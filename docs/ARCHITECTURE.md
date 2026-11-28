# 架构

当前版本 Android `0.1.0-rc.16` / Windows `0.1.0-beta.2`。本文描述**结构与接口契约**，不含打包产物。

---

## 0. 为什么分三层

| 层 | 名称 | 允许接触 | 禁止接触 |
|---|---|---|---|
| L1 | 纯数据与字节解析（Zero UI / Zero OS） | `ByteArray` / `Span<byte>` / `InputStream`、内存缓冲 | 文件路径、权限、网络、UI、时钟、随机数 |
| L2 | 文件与存储适配（各平台各写一份） | 路径发现、权限、读写、备份、校验 | 解析逻辑、调色板算术（一律调 L1） |
| L3 | UI 壳（Android：Compose；桌面：计划中的 MaterialDesign 界面） | 展示字段、收集用户意图、调 L2 | 字节操作 |

收益：L1 可以在没有游戏、没有存档、没有设备的情况下用内存字节做单元测试；改解析只可能改到纯函数；换平台时 L1 一行不改。

---

## 1. L1 接口契约

### 1.1 数据模型（三语言同名同义）

```
WorldMeta                    // 世界列表要显示的东西，只读头部即可得到
    Name, Seed, WorldId, GuidBytes
    Width, Height
    GameMode                 // 0 普通 / 1 专家 / 2 大师 / 3 旅行；传奇由 FTW 派生
    ForTheWorthy             // getGoodWorld 布尔
    Crimson                  // 腐化 false / 猩红 true
    HardMode                 // 肉山后
    DefeatedMoonLord         // 月总后
    SpawnX, SpawnY
    WorldSurface, RockLayer  // float64，按固定偏移读
    DungeonX, DungeonY
    CreationTime
    SeedFlags                // 9 个世界种子开关

World                        // 仅 L1 与 L2 之间传递，不进 UI
    Width, Height
    short[] Tiles            // 列优先 index = x * height + y；-1 = 无方块
    short[] Walls            // 同上；-1 = 无墙
    byte[]  LiquidAmounts    // 每格液体存量
    byte[]  LiquidKinds      // 0 水 / 1 岩浆 / 2 蜂蜜 / 3 微光
    WorldSurface, RockLayer  // 决定背景渐变
    UnderworldLayer = Height - 200

MapData                      // 解出来的地图缓存
    MapHeader Header         // 含调色板基址与各 tileID 的变体数量
    ushort[] Types
    byte[]   Lights
    byte[]   Colors
```

### 1.2 C#（`TerraXaleid.Core`，`net10.0`，零 NuGet）

```csharp
WorldMeta WorldFile.ParseMeta(ReadOnlySpan<byte> worldBytes);   // 只走头部，约 2.4 KB
World     WorldFile.Load(ReadOnlySpan<byte> worldBytes);        // 全量 tile 流
MapHeader MapHeader.Parse(ReadOnlySpan<byte> mapBytes);
MapData   MapFile.Read(ReadOnlySpan<byte> mapBytes);
byte[]    MapFile.Write(MapData map, int level = 9);
MapData   Reveal.Build(MapHeader header, World world, RevealOptions? options = null);
byte[]    Reveal.Write(MapHeader header, World world, RevealOptions? options = null);

sealed record RevealOptions { byte Light = 255; bool OverwriteExplored = true; int CompressionLevel = 9; }

static class MapHeader { (int cx, int cy) ChunkGrid(int w, int h); }
static class PlayerMaps { string MapFileNameForWorld(ReadOnlySpan<byte> guid); ... }
```

`RevealOptions.OverwriteExplored = false` 会抛 `NotSupportedException`：全量覆盖是本工具的
唯一语义（做不到「只补黑区而不动已探索格」时宁可明确报错，也不静默产出半对的文件）。

### 1.3 Kotlin（`io.terraxaleid.core`，纯 Kotlin/JVM，无 `android.*` 依赖）

```kotlin
fun WorldFile.parseMeta(data: ByteArray): WorldMeta
fun WorldFile.load(data: ByteArray): World
fun MapHeader.parse(data: ByteArray): MapHeader
fun MapFile.read(data: ByteArray): MapData
fun MapFile.write(header: MapHeader, data: MapData, level: Int = 9): ByteArray
fun Reveal.build(header: MapHeader, world: World, options: RevealOptions = RevealOptions()): MapData
fun Reveal.write(header: MapHeader, world: World, options: RevealOptions = RevealOptions()): ByteArray
object PlayerMaps { fun mapFileNameForWorld(guid: ByteArray): String; ... }
```

数组一律扁平（`x * height + y`），不用二维嵌套；`MapData.exploredCount()` = `lights.count { it > 18 }`。

### 1.4 六条不变量（写进三份自检）

1. `.map` 区块数 = `ceil(W/64) * ceil(H/64)`；每块解压恒 **16384** 字节。
2. 块内 `x = cx*64 + n % 64`、`y = cy*64 + n / 64`（**列主序**）。
3. `Type` 编号 **1-based**：`tile = 1 + prefixTile[tileID]`、`wall = 1 + ΣtileCounts + prefixWall[wallID]`；
   液体 4 槽、天空/泥土/岩石各 256、地狱在末尾。
4. `Light <= 18` 视为未探索；本工具一律写 255。
5. `.wld` 的 RLE run **不跨列**，每列严格 `H` 格，覆盖 `run + 1` 格；解析结束必须
   `pos == sections[2]` 且 `tiles == W*H`。
6. 生成后回读校验：`unexplored == 0`，且区块前缀与块内偏移自洽。

---

## 2. L2 适配层

### 2.1 Android（已实现，Shizuku 优先）

| 路线 | 能读到什么 | 现状 |
|---|---|---|
| Shizuku（shell uid，天然带 `ext_data_rw`） | 游戏的 `Android/data/.../Worlds`、`Players` 直接读写 | **主路线**：列世界、列角色、写回全在手机上完成 |
| `MANAGE_EXTERNAL_STORAGE` | 自己的 `Download` / `Documents` | 便携目录 `Download/TerrariaSaves/`、`Documents/TerrariaSaves/` 走这条 |
| 手动拷贝 | 用户自己搬 | 兜底 |

**为什么必须 Shizuku**：Android 11 起每个应用有一份 FUSE 视图，别的应用的 `Android/data`
不可列举，「所有文件访问」也拿不到（实测 `run-as` 读该目录直接 `Permission denied`）。
root 不是必要条件——Shizuku 用 shell 身份即可，因为 shell 属于 `ext_data_rw` 组。

写入链路（`data/TerraMirror.kt` + `data/RevealJob.kt`）：

```
prepare()   一条 shell：mkdir -p + cp Worlds/*.wld + cp -rf Players/.  → 镜像到
            /sdcard/Android/data/io.terraxaleid.android/files/mirror/{Worlds,Players}
            （不能先 rm -rf：这台机器上会 Text file busy 并把整条 shell 卡住）
Reveal      在镜像上跑 L1，写出新的 .map（本地路径）
rotate      .map.bak → .bak.1 → .bak.2 依次让位（勾选「删除上一次的备份」时跳过）
pushBack    从原文件读 uid:gid 与 mode；无原文件时回退到父目录属主；cp 后 chown/chmod || true
            以 cp 退出码为结论，字节数只作诊断（部分设备 stat 读回值不一致）
verify      回读 MapData.exploredCount() == W*H
```

所有 shell 调用都打 `Log`，tag 固定 `terraxaleid`，形如
`exec[cp -f ... && stat -c %s ...] -> 0 out=930790 err=`。

### 2.2 Windows（命令行已实现，图形界面未开始）

`windows/TerraXaleid.Core.Win` 的 `SaveDiscovery` 依次尝试
`%USERPROFILE%\Documents\My Games\Terraria` → `SpecialFolder.MyDocuments` → OneDrive 的 `Documents` / `文档`，
命中即停；刻意不读注册表，换机器、换 OneDrive 配置都能自己找。

图形界面那层（MaterialDesign）还没开始，计划把同一套 `SaveDiscovery` 与 `TerraXaleid.Core` 直接接过去。

### 2.3 角色与世界（三端一致）

```
地图缓存路径 = Players/<角色名>/<世界GUID 前三段字节倒序>.map
例：世界 GUID e957d5c3-97d3-7942-a7b4-6e8dc32adff1
    → c3d557e9-d397-4279-a7b4-6e8dc32adff1.map
```

| 场景 | 行为 |
|---|---|
| 默认 | 本机**所有角色**都写一份（每个角色各有一份地图，少写一个那人还是黑） |
| 用户指定 | 只写指定的那些；名字大小写不敏感 |
| 名字不存在 | 报错并列出可用名字，避免「打错字看起来像成功」 |
| 角色没有这个世界的 `.map` | 用**任意同世界角色**的 `.map` 当头部模板新建一份 |
| 列表展示 | 只给角色名字 |
| Android 识别 | 列 `Players/*` 目录，再用同目录的 `<名字>.plr` 过滤：游戏删角色只删 `.plr`、目录与地图留着，不过滤就会列出已删除的角色；游戏目录下「一个 `.plr` 都没有」视为空，便携目录则保持旧行为（没有 `.plr` 就全列） |

C# / Kotlin 各自有 `PlayerMaps`，两侧自测都用真实 GUID 对互验，避免两端各写一套格式。

---

## 3. L3 壳层

### 3.1 Android（Jetpack Compose，Material 3，已实现）

```
AppRoot                     语言与主题在这里注入（LocalContext / LocalConfiguration + 配色）
 └ MainFlow                 三态：Stage.Worlds / Stage.Characters(entry) / Stage.Restore
     ├ WorldsScreen         FilterChip 选根 + 搜索横幅 + 卡片栅格 + 底部汇总条
     │   ├ WorldCard        标题（命中搜索时高亮）、状态图标、横向 Chip 行、复选框
     │   └ 授权卡片          没有 Shizuku / 没有全部文件权限时作为列表首项出现，
     │                      不再把权限当成「进不去应用」的门槛
     ├ CharactersScreen     角色多选（默认全选）、模板来源、点亮按钮
     └ RestoreScreen        每张地图一行：备份份数 / 当前大小 / 最新备份时间
                            动作：恢复所选备份 / 删除所选备份 / 删除地图（全黑）
```

栅格：`<560dp` 一列、`≥560dp` 两列、`≥820dp` 三列、`≥1240dp` 四列。

- 卡片里的 Chip 行：难度、尺寸、腐化|猩红、FTW、进度（三选一：困难模式之前 / 困难模式 / 已击败月亮领主，
  因为肉山后是月总的必要不充分条件，只显示一个）。
- 页面切换：Material **shared axis X**，300 ms，进 `slideInHorizontally{w/8}` + `fadeIn`
  （emphasized decelerate `CubicBezierEasing(0.05, 0.7, 0.1, 1)`），出反向（emphasized accelerate
  `(0.3, 0, 0.8, 0.15)`）；方向由 `stageDepth` 决定。
- 主题：`ui/Theme.kt` 三套配色（浅色 / 深色 / OLED），石墨灰；设置页的三张缩略图来自实机截图，切换带颜色渐变。
- 语言：五套资源（`values` / `values-zh-rTW` / `values-ja` / `values-en`），切换时重建 Activity 的 locale，界面原地刷新、不跳回主页。
- 返回：子页面 `BackHandler` 回世界列表；世界列表按返回退出应用。

### 3.2 桌面端（未开始）

计划用 MaterialDesign 系控件，与 Android 保持同一套视觉语言（前期评估过 WinUI 3，已放弃）。
页面结构与 Android 对齐：世界列表（名称 / 尺寸 / 腐化猩红 / 难度 / 困难模式 / 金框）→ 角色多选 → 点亮与结果。

失败原因一律显示 L1/L2 抛出的原始消息（如「区块 512 解压为 16383 字节，期望 16384」）。

---

## 4. 世界列表字段 → 数据来源（已实测对齐）

`WorldMeta` 全部来自 `.wld` 头部，不读 1152 万格。读取顺序取自 1.4.5.8 的 `WorldFile.SaveWorld` 写入序列。

| 列表显示 | 数据来源 | 说明 |
|---|---|---|
| 名字 | 段1开头 `string`（7-bit 长度前缀 + UTF-8） | |
| 小/中/大世界 | `maxTilesX`：4200 / 6400 / 8400 | |
| 腐化 / 猩红 | `WorldGen.crimson` 布尔（`dungeonY` 之后 1 字节） | |
| 普通/专家/大师/旅行/传奇 | `GameMode` int32 + `getGoodWorld`（FTW）布尔 | 传奇不是独立挡位，见 4.1 |
| 困难模式（肉山后） | `hardMode` 布尔（`altarCount` 之后） | |
| 金色边框（月总后） | `downedMoonlord` 布尔 | 读取前必须先跳过 `BannerSystem` 变长块，否则整体错位 |
| 创建时间 | `CreationTime.ToBinary()` int64 | 高 2 位 Kind、低 62 位 ticks；按 UTC 转本地 |

### 4.1 难度派生

```
显示难度 = GameMode == 3            ? "旅行"
         : FTW                      ? ["普通","专家","大师","传奇"][min(3, GameMode + 1)]
         :                            ["普通","专家","大师","旅行"][GameMode]
```

即 FTW 让难度整体上移一档：普通→专家、专家→大师、**大师→传奇**。读档时不要期待 `GameMode == 4`。

### 4.2 一个真实踩过的坑

布尔序列末尾、月总标志之前夹着 `BannerSystem.Load(reader, version)` 的变长块：

```
int16 n1 ; n1 × int32      // banner 击杀数
int16 n2 ; n2 × uint16     // banner 可领取数（version >= 289）
```

漏掉它会让后面的 `downedMoonlord` 全部错位——实测表现为已通关的世界读成「未月总」。

实测校验（14 个真实 Steam 存档 + 2 个移动端存档，全部与游戏内显示一致）：

```
简约全物品 v1.4.5        → 传奇 / 中世界 / 猩红 / 肉山后 / 月总后    GM=2 FTW=1
硬核的神童舌             → 传奇 / 小世界 / 猩红 / 未肉山 / 未月总    GM=2 FTW=1
肥沃的大王通道           → 专家 / 小世界 / 腐化 / 未肉山 / 未月总    GM=0 FTW=1
100% Бестиарий + ачивк   → 旅行 / 大世界 / 猩红 / 肉山后 / 月总后    GM=3 FTW=1
1.4.5.8 言寺全物品（大师）→ 大师 / 大世界 / 猩红 / 肉山后 / 月总后   GM=2 FTW=0
柔弱的颤抖几把（移动端）  → 专家 / 中世界 / 腐化 / 未肉山 / 未月总    GM=1 FTW=0
```

---

## 5. 版本号与产物

- `versionName` 走 `0.1.0-dev.N` → `0.1.0-rc.N` → `0.1.0`，每轮 `versionCode` +1，不开平行序列。Windows 端自 `0.1.0-beta.1` 起并入同一序列。
- `dist/` 文件名只带 `versionName`（`TerraXaleid-<versionName>.apk`），不带 `-release` / `-shizuku` 之类的定语。
  构建类型、签名状态、改动内容进提交信息、git tag 与 CHANGELOG。
- Android 是**纯 Kotlin + Compose，没有 `lib/` 下的原生 `.so`**，所以一个通用 APK 同时适配
  `arm64-v8a` 与 `x86_64`，不需要 `abiFilters` 或 ABI 拆分。只有将来加入原生库才需要。

## 6. 打包计划（Windows 尚未开始）

| 目标 | 形态 | 说明 |
|---|---|---|
| Android | 通用 APK | arm64-v8a / x86_64 同一个包（无原生库） |
| Windows | **单文件 exe 便携版**（优先且大概率唯一） | 依赖能内联就内联；只有必须带 DLL 或要求安装 .NET 运行时，才退成文件夹 portable，再退 setup |
| 其他 | 本地 `127.0.0.1` 控制台 | 与桌面端二选一 |

Node 方案不进矩阵：与 .NET / Kotlin 两套共存没有收益。
