# terramap — Terraria 世界地图读写核心

版本 **0.1.0-dev.1**（SemVer 风格预发布号，见 `VERSION`）。目标只有一个：把 `.wld` 读出来，算出每格应该在地图上显示什么，再写成合法的 `.map` 文件。**不改动世界本身**。

当前只含核心「读取 / 计算 / 写入」三部分，不含打包、GUI、网络。日后如果要发行为 APK / EXE / 本地控制台，直接在 `mapfmt.py` + `worldfmt.py` 外面套壳即可（见文末路线）。

---

## 0. 定位与边界（务必先读）

**本项目做什么**：读取 Terraria 的存档文件（`.wld` 世界文件、`.map` 地图缓存文件），计算每个格子在地图上应显示的类型，然后把结果写回 `.map`。就这些。

**本项目不做什么**（这些边界是有意的设计约束，不是暂时缺失）：

- **与 tModLoader 无关**：不加载、不依赖、不生成、不解析任何 tModLoader 的模组（`.tmod` / `.efmod`）内容，也不需要玩家安装 tModLoader。功能不通过模组实现，等价效果由直接改写存档数据得到。
- **与游戏官方无关**：不是 Re-Logic、不是 Terraria 官方、也不是任何发行方的产品，未获其背书或授权，不包含任何游戏资源文件。
- **不挂载 / 不注入游戏进程**：不注入 DLL、不 hook、不打内存补丁、不做运行时脚本、不修改 APK。工具在游戏**关闭**的状态下离线处理文件；游戏运行时只是读取被处理后的存档。
- **只修改存档文件**：写入对象仅有 `.map`（地图缓存）。世界数据 `.wld` 全程只读，物块、物品、NPC、进度一律不动。

**不兼容国服心动版**：本项目的格式实现针对国际版（Google Play / App Store / Steam）的 Terraria 1.4.5.x 存档。国服心动版（包名 `con.xd.terraria`）使用不同的存档布局与版本号体系，**不受支持，也不计划支持**。用国服存档运行本工具会得到错误结果，请勿尝试。

**声明（固定，随仓库发布）**：

1. **本项目为独立第三方开源工具，与 Re-Logic 及 Terraria 官方无关。**
2. **本项目主要由 AI 辅助编写与迭代（开发过程中使用了 DeepSeek-V4.1-Flash 辅助生成代码），核心功能均经过本地存档测试验证。因缺乏完整传统工程经验，代码结构可能存在不足，欢迎 Issue 反馈。**
3. 许可证：**GPL-3.0-or-later**，全文见 `LICENSE`，各源码文件顶部带 `SPDX-License-Identifier: GPL-3.0-or-later`。

---

## 1. 文件说明

| 文件 | 作用 |
|---|---|
| `worldfmt.py` | 读 `.wld`：世界名、worldID、宽高、surface / rockLayer / underworldLayer、tile 与 wall 索引数组（`np.ndarray`，形状 `(width, height)`，**`-1` 表示无**） |
| `mapfmt.py` | `.map` 头解析、调色板基址推导、区块读写、数组生成（`build_arrays`） |
| `reveal.py` | CLI：`.wld` + 模板 `.map` → 全点亮 `.map`，自带回读校验 |
| `deploy.py` | 推回设备 + `chown`/`chmod` + 必要时 `force-stop` |
| `selftest.py` | 自检：调色板算术、合成文件往返、真实文件不变量 |
| `ARCHITECTURE.md` | 三层架构（L1 纯字节 / L2 存储适配 / L3 UI）与 C#、Kotlin 双实现接口契约 |
| `scripts\verify-all.ps1` | 一次跑 Python / C# / Kotlin 三份自检，汇总 PASS/FAIL |
| `CHANGELOG.md` | 按 Keep a Changelog 记录版本变更 |
| `CONTRIBUTING.md` | 目录约定、改代码前的检查、提交与验证方式 |
| `samples\` | 真实样本说明（**不放存档**，路径由参数传入） |
| `requirements.txt` | 依赖（仅 numpy） |

---

## 2. 一次性用法

**先记住一件事：地图是「世界 × 角色」各存一份的。**

```
Players/<角色>.plr            角色本身
Players/<角色>/<世界GUID>.map 这个角色在这个世界探索到的东西
```

同一个世界，有几个角色去过就有几个 `.map`。只写你那个角色的文件，别的角色打开还是黑的；所以默认行为是**本机所有角色一起点亮**。

```bash
P="$USERPROFILE/Documents/My Games/Terraria/Players"   # 或设备上的 Players 目录

# 0. 只看角色名字（其他属性不打印）
python reveal.py live.wld --players-dir "$P" --list

# 1. 所有角色一次点亮（默认）
python reveal.py live.wld --players-dir "$P"

# 2. 只点亮指定角色（可重复，大小写不敏感；名字打错会报错并列出可用名字）
python reveal.py live.wld --players-dir "$P" --player 鸡 --player Marina

# 3. 想先看数字不写文件
python reveal.py live.wld --players-dir "$P" --dry-run

# 4. 单个文件（老用法，自己指定模板和输出）
python reveal.py live.wld map1_orig_fromdevice.bin -o map1_reveal.bin

# 5. 推回设备（必须先停游戏，否则退出时会把旧地图写回去覆盖）
python deploy.py map1_reveal.bin "/sdcard/Android/data/com.and.games505.TerrariaPaid/Players/鸡/c3d557e9-d397-4279-a7b4-6e8dc32adff1.map"

# 6. 自检
python selftest.py
python selftest.py ..\live.wld ..\map1_orig_fromdevice.bin
```

`--players-dir` 模式下会自己找模板：**任何一个角色**在该世界已有的 `.map` 都能当模板（option 计数表由世界内容决定，同世界的各角色完全一致）。某个角色从没进过这个世界、目录里没有这个 `.map` 时，就用模板新建一份，输出里会标 `(created a new map for '…')`。

`reveal.py` 正常输出：

```
world   : '柔弱的颤抖几把' id=750061081 6400x1800 surface=493 rock=757 underworld=1600
template: F:\…\Players\鸡\c3d557e9-d397-4279-a7b4-6e8dc32adff1.map
map file: c3d557e9-d397-4279-a7b4-6e8dc32adff1.map
palette : tile=1 wall=1065 liquid=1422 sky=1426 dirt=1682 rock=1938 hell=2194
players : 3 of 3 (小三, 阿二, 鸡)
written : …\小三\c3d557e9-….map (2158030 bytes) unexplored=0 [ok]
          (created a new map for '小三')
```

---

## 3. `.map` 格式（mobile & PC 1.4.5.x，revision 68，fileType 1）

### 3.1 头部

```
int32   33094
char[7] "relogic"
byte    fileType = 1
uint32  revision
uint64  favorite
string  worldName           7-bit 长度前缀 + UTF-8
int32   worldID
int32   height
int32   width
int16   maxTileID, maxWallID, liquidTypes, skyGradients, dirtGradients, rockGradients
bool[]  tileOptionFlags     maxTileID 位，MSB first，逐字节
bool[]  wallOptionFlags     maxWallID 位
byte[]  tileOptionCounts    flag=1 的 tileID 各 1 字节，否则隐式 1
byte[]  wallOptionCounts
```

头部长度随世界变化（样例 293 B；worldName 长度、以及有多少 flag 被置位都会影响）。

### 3.2 区块

头部之后是 `ceil(width/64) * ceil(height/64)` 个区块，**行主序**遍历区块网格：

```
int32 length        0 => 整块视为空（4096 格全 Type 0）
byte[length]        zlib 流，解压恒为 16384 字节
                    = 4096 × MapTile{ uint16 Type; uint8 Light; uint8 Color; }
```

块内索引 → 坐标（**列主序，最容易踩的坑**）：

```
x = chunkX*64 + (n % 64)
y = chunkY*64 + (n / 64)
```

`Light > 18` 才算"已探索"，未探索格是 `Type=0 / Light=0`。

区块数核对：`6400×1800 → 100×29 = 2900`；`8400×2400 → 132×38 = 5016`。

### 3.3 Type（调色板索引，**1-based**）

```
tile    = 1                              + prefixTile[tileID]
wall    = 1 + Σ tileOptionCounts         + prefixWall[wallID]
liquid  = wall 之后 +4                    （水 / 岩浆 / 蜂蜜 / 微光）
sky     = liquid 之后 +256                index = byte(255 * y / worldSurface)
dirt    = sky 之后 +256                   0 = 普通，255 = 雪地
rock    = dirt 之后 +256                  同上
hell    = rock 之后 +1
```

空格子的背景（`MapHelper.GetBackgroundType`）：

```
y <  worldSurface                 -> sky（渐变索引 byte(255*y/surface)）
y <  rockLayer                    -> dirt + (雪地 ? 255 : 0)
y <  underworldLayer (H-200)      -> rock + (雪地 ? 255 : 0)
否则                               -> hell
```

样例世界基址：`tile=1 wall=1065 liquid=1422 sky=1426 dirt=1682 rock=1938 hell=2194`。

> 这两条是当年整张图颜色错位的原因：Type 写成 0-based（整体错一格，泥土变黑）、天空渐变索引写成 `min(255, y-757)`（游戏只把 0 和 255 当合法值）。

### 3.4 `.wld` 关键点

`int32 326 / "relogic" / ...`，`int32[11]` 段偏移，段 1 = 世界信息（名称、seed、GUID、worldID、LRTB、宽高、gameMode、spawn、两个 float64 分层线），段 2 = tile 流：每列严格 1800 格，**RLE 的 run 必须在列边界截断**；run 覆盖格数 = `run + 1`。段尾偏移与总格数是硬校验（`p == sections[2]` 且 `tiles == W*H`）。

`worldfmt.py` 的 `_find_layer_floats` 用启发式定位 surface / rock 两个 float64（值在 `0 < v < height`，且 `surface < rock`），比按固定偏移可靠。

---

## 4. 已知限制

- 只写地图图层，不动的确是世界数据；但**必须先 `am force-stop` 游戏**再替换 `.map`，否则退出时被覆盖。
- 推到 `/sdcard/...` 后必须 `chown u0_a72:ext_data_rw` + `chmod 660`（FUSE 上 `restorecon` 会报 `Operation not supported`，可忽略）。`deploy.py` 已处理。
- 图标/箱子等"地图上的小对象"不在 `.map` 里，属世界数据，本工具不涉及。
- 世界被游戏重构过（版本升级改了 tileID 段）后，模板 `.map` 的 option 计数表可能不再匹配，需要重新取模板。
- **PC（Steam）存档里的 `.map` 是旧格式（fileVersion 318，带 RLE），当前解析器只认移动版 1.4.5.8 的 33094**。Windows 版要做到「一键处理 PC 存档」，得先补 318 的读写；现在拿 PC 的 `.map` 当模板会被明确拒绝（`unsupported map file version 318`），不会写坏文件。
- 角色从没进过某个世界时，本工具会新建该世界的 `.map`，游戏下次进去直接显示全区已探索。这是「所有角色点亮」的预期行为，但确实是在替角色创建文件；`--player` 可以收窄范围。

---

## 5. 路线与许可

版本线 **0.1.0-dev.1** 起步，日后再考虑：Android（arm64-v8a / armeabi-v7a / x86_64）APK、Windows x86_64（含 win-arm64）可执行或便携包、或本地 `127.0.0.1` 控制台。

当前进度：

- L1 三语言实现（Python / C# / Kotlin）均已通过自检，C# 与 Python 对同一真实存档输出**逐字节一致**（SHA-256 `272C8A80…5FCF8`）。
- Android 侧已搭好 Compose 骨架（`android/`，Material 3），`core` 模块直接引用 `kotlin/src/main/kotlin`；**arm64-v8a 优先**，Windows 版本在其后追加。

构建 Android 之前先把 `android\local.properties.example` 复制成 `android\local.properties`，把 `sdk.dir` 指到本机 Android SDK（该文件含机器专属路径，已被 `.gitignore` 排除）。

**许可证：GPL-3.0-or-later**（`LICENSE` 全文 35,149 B，取自 gnu.org）。选它的原因与项目形态一致：本项目读写他人存档格式、修改结果需要可被玩家自行审计与再分发修改版，GPL-3.0 能保证衍生版本同样开源。

### 发行前待办（checklist）

- [x] 补 `LICENSE`（GPL-3.0 全文）与各文件 SPDX 头
- [x] README 首屏固定加入「与 Re-Logic 及 Terraria 官方无关」声明
- [x] README 首屏固定加入 AI 辅助开发的说明段（DeepSeek-V4.1-Flash 辅助生成代码，核心功能经本地存档测试验证）
- [x] 明确标注「不兼容国服心动版（`con.xd.terraria`）」
- [x] 明确标注「与 tModLoader 无关、不挂载/注入游戏、仅修改存档文件」
- [ ] 打包矩阵：Android arm64-v8a / armeabi-v7a / x86_64，Windows x86_64（含 win-arm64）exe 或 portable，本地控制台形态
- [x] 版本号自 `0.1.0-dev.1` 起，遵循 SemVer（`0.x` 阶段不承诺接口稳定）
