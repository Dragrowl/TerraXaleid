# terramap 三层架构（C# / Kotlin 双实现）

面向 `0.1.0-dev.1` 之后的发行形态。本文只描述**结构与接口契约**，不含打包产物。

---

## 0. 为什么是三层

| 层 | 名称 | 允许接触的东西 | 禁止接触的东西 |
|---|---|---|---|
| L1 | 纯数据与字节解析（Zero UI / Zero OS） | `byte[]` / `Span<byte>` / `Stream`、内存缓冲 | 文件路径、权限、网络、UI、时钟、随机数 |
| L2 | 文件与存储适配（各平台各写一份） | 路径发现、SAF/权限、读写、备份、校验 | 解析逻辑、调色板算术（一律调 L1） |
| L3 | UI 壳（WinUI 3 / Material Design） | 展示字段、收集用户意图、调 L2 | 字节操作 |

**收益**：L1 可以在没有游戏、没有存档、没有设备的情况下用内存字节做单元测试；AI 局部改动也只能改到 L1 里的纯函数，不会碰坏权限或路径逻辑；L2 换平台时 L1 一行不改。

---

## 1. L1 接口契约

### 1.1 数据模型（两语言同名）

```
WorldMeta                         // 世界列表要显示的东西，只读头部即可得到
    Name            string
    Seed            string
    WorldId         int32
    Width, Height   int32
    SizeLabel       string        // 小/中/大世界
    GameMode        int32         // 0普通 1专家 2大师 3旅行 4传奇
    DifficultyLabel string
    Evil            enum          // Corruption / Crimson
    HardMode        bool          // 肉山后
    DefeatedMoonlord bool         // 月总后（列表金色边框）
    Spawn           (int32,int32)
    WorldSurface    float64
    RockLayer       float64
    Dungeon         (int32,int32)
    CreationTime    DateTime
    SeedFlags       SeedFlags     // 9 个世界种子开关

World                             // 仅 L1 内部与 L2 之间传递，不进 UI
    WorldMeta Meta
    int16[,] Tiles                // -1 = 无方块，[x, y] 序
    int16[,] Walls                // -1 = 无墙

MapFile
    MapHeader Header              // 含调色板基址与 option 计数表
    uint16[,] Types
    uint8[,]  Lights
    uint8[,]  Colors
```

### 1.2 函数（C#，`TerraMap.Core`，`netstandard2.1` + `net8.0`）

```csharp
public static class TerraMapCore
{
    // 全部纯函数：调用方给字节，返回结构或字节
    public static WorldMeta ReadWorldMeta(ReadOnlySpan<byte> worldBytes);
    public static World     LoadWorld(Stream worldStream);              // 大头，只有需要改地图时调
    public static MapHeader ReadMapHeader(ReadOnlySpan<byte> mapBytes);
    public static MapFile   ReadMap(ReadOnlySpan<byte> mapBytes);
    public static byte[]    BuildRevealedMap(Stream world, ReadOnlySpan<byte> templateMap,
                                             RevealOptions options);
    public static byte[]    WriteMap(MapFile map);                      // 供"只改几格"的场景
}

public sealed record RevealOptions
{
    public byte Light { get; init; } = 255;        // 每格亮度
    public bool KeepExploredType { get; init; }    // 保留已探索格原有 Type
    public bool DimUnexplored { get; init; }       // 未探索格暗置（Light=某值）而非全亮
}
```

约束：不出现 `System.IO.File`、`Directory`、`Environment`、`Registry`；不读时钟（`CreationTime` 从字节解出，不取 `DateTime.Now`）；不写任何静态可变状态。

### 1.3 函数（Kotlin，`io.terramap.core`，pure Kotlin/JVM，无 Android 依赖）

```kotlin
object TerraMapCore {
    fun readWorldMeta(worldBytes: ByteArray): WorldMeta
    fun loadWorld(input: InputStream): World                    // 11.5M 格 → IntArray，避免对象爆炸
    fun readMapHeader(mapBytes: ByteArray): MapHeader
    fun readMap(mapBytes: ByteArray): MapFile
    fun buildRevealedMap(world: InputStream, template: ByteArray,
                         options: RevealOptions): ByteArray
    fun writeMap(map: MapFile): ByteArray
}
```

约束：只用 `java.io.InputStream` / `ByteArray` / `java.nio.ByteBuffer`；**不引用 `android.*`**（保证能被桌面 JVM 单测）；数组一律扁平 `IntArray`（`x * height + y`），不用二维嵌套数组。

### 1.4 两端共享的不变量（写进测试）

1. `.map` 区块数 = `ceil(W/64) * ceil(H/64)`；每块解压恒 16384 字节。
2. 块内 `x = n % 64 + chunkX*64`、`y = n / 64 + chunkY*64`（**列主序**）。
3. `Type` 编号 **1-based**：`tile = 1 + prefixTile[tileID]`、`wall = 1 + ΣtileCounts + prefixWall[wallID]`。
4. `Light <= 18` 视为未探索。
5. `.wld` 的 RLE run 在列边界截断，覆盖 `run + 1` 格；解析结束必须 `p == sections[2]` 且 `tiles == W*H`。
6. 生成后回读校验：`unexplored == 0`，`types` 逐一相等。

---

## 2. L2 适配层

### 2.1 Windows（portable 优先）

路径发现顺序（命中即停，全部可手动覆盖）：

```
1. <exe同级>\terramap.toml 里写的 saves_dir
2. %USERPROFILE%\Documents\My Games\Terraria\Worlds
3. OneDrive 文档重定向：%OneDrive%\文档\My Games\Terraria\Worlds
   （中文系统是「文档」，英文是「Documents」，两者都试）
4. 注册表 Shell Folders\Personal 指向的 Documents 真实路径
5. Steam 库：steamapps/libraryfolders.vdf → 逐库找
   steamapps\common\Terraria\  （存在即认为用户装过 PC 版，仅作提示不影响存档路径）
```

- 世界列表 = `Worlds\*.wld` + `Worlds\*.wld.bak`（`.bak` 折叠到同名条目作为"上一版"）。
- 写入前备份：`<name>.map.bak-YYYYMMDD-HHMMSS`，与 `<name>.wld.bak-...` 同目录；失败自动回滚。
- 配置与日志放 exe 同级 `data\`（portable 语义：整个文件夹可搬走）。

### 2.2 Android（Material Design 壳）

存储路线优先级：

```
1. MANAGE_EXTERNAL_STORAGE（"所有文件访问"，用户一次性在系统设置里授权）
   路径：/storage/emulated/0/Android/data/com.and.games505.TerrariaPaid/Players/<角色>/*.map
   实测可直接列举世界与角色目录 → 自动识别世界列表，一键处理
2. Shizuku + adb 权限（可选，非必要不启用）
   用 Shizuku 拿到 shell 身份的 read/write，能绕开部分 OEM 对 Android/data 的限制；
   仅在路线 1 被系统或厂商拒绝时作为兜底，不默认请求
3. SAF（ACTION_OPEN_DOCUMENT / OPEN_DOCUMENT_TREE）
   只拿到 Uri，用 ContentResolver 读成字节交给 L1；适用于不想开"所有文件访问"的用户
4. 手动导入：用户把 .wld/.map 拷到 App 私有目录（getExternalFilesDir）后处理
```

- 首启引导页按 1 → 3 → 4 顺序询问，Shizuku 只在检测到路线 1 不可用时提示。
- 无论哪条路线，**L2 只产出 `byte[]` 和写回动作**，所有解析与生成仍在 L1。
- 写回同样先备份原文件到 `getExternalFilesDir/backup/<timestamp>/`，再写，再回读校验。
- 与游戏互斥：处理前提示用户完全退出游戏（否则退出时会被游戏覆盖），需检测进程/前台状态。

### 2.3 角色与世界的对应关系（必须由 L2 提供）

```
地图缓存路径 = Players/<角色名>/<世界GUID前三段字节倒序>.map
例：世界 GUID e957d5c3-97d3-7942-a7b4-6e8dc32adff1
    → c3d557e9-d397-4279-a7b4-6e8dc32adff1.map
```

L2 负责枚举 `Players/*/`，把每个 `.map` 与世界 GUID 关联；L1 不做路径推断。

**角色选择策略（已定，三端一致）：**

| 场景 | 行为 |
|---|---|
| 默认 | 本机**所有角色**都写一份，因为这个世界的每个角色各有一份独立地图，少写一个那个角色进去还是黑 |
| 用户指定 | 只写指定的那些；名字大小写不敏感 |
| 名字不存在 | 直接报错并列出可用名字，避免「打错字看起来像成功」 |
| 角色没有这个世界的 `.map` | 用**任意同世界角色**的 `.map` 当头部模板新建一份（option 计数表由世界内容决定，同世界各角色一致） |
| 列表展示 | 只给角色名字，其他属性一律不显示 |
| Android 识别 | 优先读 `Players/*` 目录名（目录名即角色名，无需解析 `.plr`）；无权限时退化为「所有角色」 |

C# 对应 `TerraMap.Core.PlayerMaps`（`MapFileNameForWorld` / `GuidFromMapFileName` / `PlayerNameFromDirectory`），Kotlin 对应 `io.terramap.core.PlayerMaps`，两侧自测都用真实样本 `e957d5c3…` ↔ `c3d557e9-…map` 互验，避免两端各写一套格式。

---

## 3. L3 壳层

### 3.1 WinUI 3（`terramap.desktop`，unpackaged + WindowsAppSDK self-contained）

```
MainWindow
 ├ WorldListView        列表：名称 / 尺寸 / 腐化猩红 / 难度 / 困难模式 / 金框
 │   └ WorldCard        单选 + 副标题（创建时间、seed）
 └ ActionPanel          「点亮地图」开关组：亮度、是否保留已探索格
      └ RunButton       进度条 + 结果区（成功/失败原因原文）
```

- 只绑定 `WorldMeta` 与 `RevealOptions`，不接触 `World`/`MapFile`。
- 失败原因一律显示 L1/L2 抛出的原始消息（如"区块 512 解压为 16383 字节，期望 16384"），便于反馈。

### 3.2 Android（Jetpack Compose，Material 3）

```
WorldListScreen           LazyColumn of WorldCard
 ├ WorldCard              Card + 三个 Chip：难度 / 尺寸 / 腐化|猩红；角标：肉山后、月总后
 └ WorldDetailSheet       ModalBottomSheet：字段明细 + 操作按钮
ActionScreen             进度、日志尾部、结果
PermissionScreen         "所有文件访问" 引导 + SAF 兜底入口
```

---

## 4. 世界列表字段 → 数据来源（已实测对齐）

`WorldMeta` 全部来自 `.wld` 头部，不需要读 11.5M 格。字段读取顺序取自 1.4.5.8 的 `WorldFile.SaveWorld` 写入序列：

| 列表显示 | 数据来源 | 说明 |
|---|---|---|
| 名字 | 段1开头 `string`（7-bit 长度前缀 + UTF-8） | |
| 小/中/大世界 | `maxTilesX`：4200 / 6400 / 8400 | |
| 腐化 / 猩红 | `WorldGen.crimson` 布尔（`dungeonY` 之后 1 字节） | |
| 普通/专家/大师/旅行/传奇 | `GameMode` int32 + `getGoodWorld`（FTW）布尔 | **传奇不是独立挡位**：FTW 在原有挡位上加一档，`GameMode` 字段仍是 0/1/2；见 4.1 |
| 困难模式（肉山后） | `Main.hardMode` 布尔（`altarCount` 之后） | |
| 金色边框（月总后） | `NPC.downedMoonlord` 布尔 | 对应 `WorldFileData.DefeatedMoonlord`；读取时必须先跳过 `BannerSystem` 变长块，否则整体错位 |
| 创建时间 | `CreationTime.ToBinary()` int64 | 高 2 位是 Kind，低 62 位是 ticks；按 UTC 解读后转本地时区 |

### 4.1 难度标签的派生规则

```
基础挡位 = GameMode          # 0 普通 / 1 专家 / 2 大师 / 3 旅行
显示难度 = GameMode == 3 ? "旅行"
         : getGoodWorld(FTW) ? ["普通","专家","大师","传奇"][min(3, GameMode + 1)]
         : ["普通","专家","大师","旅行"][GameMode]
```

即 FTW 让难度整体上移一档：普通→专家、专家→大师、**大师→传奇**。读档时不能期待 `GameMode == 4`。

### 4.2 一个真实踩过的坑

世界信息的布尔序列末尾、月总标志之前，夹着 `BannerSystem.Load(reader, version)` 的变长块：

```
int16  n1 ; n1 × int32        // banner 击杀数
int16  n2 ; n2 × uint16       // banner 可领取数（version >= 289）
```

漏掉这个块会让后面的 `downedMoonlord` 全部错位——实测表现为一个已通关月总的世界被读成"未月总"。

实测校验（同一份代码跑过 14 个真实 Steam 存档 + 2 个移动端存档，全部与游戏内显示一致）：

```
简约全物品 v1.4.5        → 传奇 / 中世界 / 猩红 / 肉山后 / 月总后    GM=2 FTW=1
硬核的神童舌             → 传奇 / 小世界 / 猩红 / 未肉山 / 未月总    GM=2 FTW=1
肥沃的大王通道           → 专家 / 小世界 / 腐化 / 未肉山 / 未月总    GM=0 FTW=1
100% Бестиарий + ачивк   → 旅行 / 大世界 / 猩红 / 肉山后 / 月总后    GM=3 FTW=1
1.4.5.8 言寺全物品（大师）→ 大师 / 大世界 / 猩红 / 肉山后 / 月总后   GM=2 FTW=0
柔弱的颤抖几把（移动端）  → 专家 / 中世界 / 腐化 / 未肉山 / 未月总    GM=1 FTW=0
```

其中 `GM=2 FTW=1 → 传奇`、`GM=0 FTW=1 → 专家`、`GM=3 FTW=1 → 旅行`（旅行不受 FTW 影响）三类都已用真实存档覆盖。

---

## 5. 打包计划（未来，非现在）

| 目标 | 形态 | 说明 |
|---|---|---|
| Windows | **单文件 exe 便携版**（优先且大概率是唯一形态） | 依赖能内联就内联；**只有当必须带 DLL 或要求安装 .NET 运行时**，才退化为单文件夹 portable，或再退一步做 setup |
| Android | APK，arm64-v8a / armeabi-v7a / x86_64 | 三 ABI 分包或 universal；arm64 优先级最高 |
| 其他 | 本地 `127.0.0.1` 控制台 | 与桌面端二选一，不做并存 |

Node 方案不进入矩阵：与 .NET / Kotlin 两套共存没有收益。

---

## 6. 迁移顺序（建议）

1. L1 用 C# 先写（有 `Span<byte>`、有现成的 `BinaryReader` 语义对照，最容易和 PC 源码逐字段对齐）。
2. 用现有 Python 实现做**黄金样本**：同一 `.wld` 生成的 `.map` 必须逐字节相同（SHA256 相等），作为 C# 的验收标准。
3. Kotlin 版照 C# 翻译，同样用黄金样本过测试。
4. L2/L3 再各自接。
