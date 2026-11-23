<p align="center">
  <img src="docs/assets/mark.svg" alt="terramap" width="96">
</p>

<h1 align="center">terramap</h1>

<p align="center">
  <img src="https://img.shields.io/badge/version-0.1.0--dev.1-3178C6?style=for-the-badge" alt="version">
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPLv3-blue?style=for-the-badge" alt="GPL-3.0-or-later"></a>
  <a href="#构建与验证"><img src="https://img.shields.io/badge/selftest-PASS-2E7D32?style=for-the-badge" alt="selftest"></a>
  <a href="#构建与验证"><img src="https://img.shields.io/badge/L1-python%20%7C%20csharp%20%7C%20kotlin-8957E5?style=for-the-badge" alt="L1 三语言"></a>
</p>

<p align="center">
  中文 | <a href="README_EN.md">English</a>
</p>

terramap 读 Terraria 的世界存档 `.wld`，算出每一格在地图上应该显示什么，再写成合法的地图缓存 `.map`。结果就是：**打开游戏里的地图，整张世界全部点亮，一格黑区都不剩；而世界本身——物块、物品、NPC、进度——一个字节都不动。**

核心是纯数据处理：输入字节，输出字节。没有 GUI，不碰游戏进程，三份实现（Python / C# / Kotlin）对同一份存档给出同样的结果。

## 快速开始

### 需要什么

- Python 3.10+ 与 `numpy`：`python -m pip install -r requirements.txt`
- 一份世界存档 `.wld`，以及**任意一个进过这个世界的角色的** `.map`（当模板，决定调色板基址）
- 游戏处于**关闭**状态（游戏退出时会把内存里的地图写回磁盘，覆盖你的成果）

### 三步走

```powershell
# 1. 看看这台机器上有哪些角色（只读，不写任何文件）
python reveal.py "D:\...\Terraria\Worlds\我的世界.wld" --players-dir "D:\...\Terraria\Players" --list

# 2. 先演一遍，确认世界和模板都对得上（不落盘）
python reveal.py "D:\...\我的世界.wld" --players-dir "D:\...\Terraria\Players" --dry-run

# 3. 正式写：默认给该目录下每个角色都写一份
python reveal.py "D:\...\我的世界.wld" --players-dir "D:\...\Terraria\Players"
```

看到 `written : … unexplored=0 [ok]` 就是成了。`unexplored` 不是 0 会以退出码 3 失败，不会留半成品。

### 存档在哪

| 平台 | 世界 `.wld` | 角色与地图 `.map` |
|---|---|---|
| Windows / Steam | `%USERPROFILE%\Documents\My Games\Terraria\Worlds` | `%USERPROFILE%\Documents\My Games\Terraria\Players` |
| Android 国际版 | `/sdcard/Android/data/com.and.games505.TerrariaPaid/files/` | `/sdcard/Android/data/com.and.games505.TerrariaPaid/Players` |
| MuMu 模拟器 | 同上，用 `adb` 或模拟器内的文件管理器取 | 同上；改完要 `chown u0_a72:ext_data_rw` + `chmod 660`，`deploy.py` 已处理 |

## 能做什么

| 场景 | 命令 |
|---|---|
| 本机所有角色一次性点亮 | `python reveal.py WORLD.wld --players-dir PLAYERS` |
| 只给指定角色点亮 | 追加 `--player 角色名`（可重复） |
| 只列角色，不动文件 | 追加 `--list` |
| 单文件模式（自己指定输出） | `python reveal.py WORLD.wld TEMPLATE.map -o OUT.map` |
| 保留游戏已探索格子的原始类型 | 追加 `--keep-explored-type` |
| 推到模拟器 / 手机 | `python deploy.py OUT.map --serial 127.0.0.1:7555` |

地图是**按「世界 × 角色」各存一份**的：`Players/<角色>/<世界 GUID>.map`。所以"点亮"必须逐角色写，这也是默认行为——只给你写过的角色，游戏里才看得见。

每格颜色按 `Terraria.Map.MapHelper.CreateMapTile` 的真实优先级决定：**方块 > 液体（存量 > 32）> 墙 > 深度背景**。液体四槽是水 / 岩浆 / 蜂蜜 / 微光，所以海、湖、岩浆海在点亮的图上都是有颜色的，不会留成黑区。

### 手机上直接用（Android，推荐 Shizuku）

装 Shizuku 并启动服务（MuMu/真机都行），在 App 里点「授权 Shizuku」，之后列世界、选角色、点亮、写回全在手机上完成——不需要 adb，也不需要把存档拷到别处：

```
授权 Shizuku → 世界列表（直接读 Android/data） → 勾选世界 → 勾选角色 → 点亮所选
```

点亮的 `.map` 会写回游戏目录，原文件保留为 `.map.bak`；结果弹窗里可以「撤销本次」。
### 手机上直接用（Android）

`android/` 是一个独立 App（Material 3）：授予「所有文件访问权限」后，它会列出存档里的世界，选中世界 → 选角色（默认全部）→ 点亮。写入前自动备份为 `.map.bak`，结果页有「撤销本次」。

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
cd android
F:\Android\gradle\gradle-8.9\bin\gradle.bat :app:assembleDebug --no-daemon
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

**关于 Android 11+ 的系统限制**：`MANAGE_EXTERNAL_STORAGE` 并不能让应用读取别的 App 的 `Android/data`，这是平台的硬限制（授权到位也一样，`ls` 直接 `Permission denied`）。App 因此优先读两个便携目录：

| 位置 | 用途 |
|---|---|
| `/sdcard/Download/TerrariaSaves/{Worlds,Players}` | 把存档复制进来，App 直接列出并点亮 |
| `/sdcard/Documents/TerrariaSaves/{Worlds,Players}` | 同上，备份盘 |

想直接写回游戏目录，用 PC 上的 `reveal.py` + `deploy.py`（走 adb，shell 用户有 `ext_data_rw` 权限）；root 设备后续会加 `su` 桥接。

## 声明

1. **本项目为独立第三方开源工具，与 Re-Logic 及 Terraria 官方无关。**
2. **本项目主要由 AI 辅助编写与迭代（开发过程中使用了 DeepSeek-V4.1-Flash 辅助生成代码），核心功能均经过本地存档测试验证。因缺乏完整传统工程经验，代码结构可能存在不足，欢迎 Issue 反馈。**
3. 许可证 **GPL-3.0-or-later**，全文见 [`LICENSE`](LICENSE)，各源码文件顶部带 `SPDX-License-Identifier: GPL-3.0-or-later`。

<details>
<summary><h2 id="边界">边界（为什么这样设计）</h2></summary>

这些是**有意的设计约束**，不是暂时缺失：

- **与 tModLoader 无关**：不加载、不依赖、不生成、不解析任何 `.tmod` / `.efmod`，玩家不需要装 tModLoader。功能不通过模组实现，等价效果由直接改写存档数据得到。
- **不挂载 / 不注入游戏进程**：不注入 DLL、不 hook、不打内存补丁、不做运行时脚本、不修改 APK。工具在游戏关闭时离线处理文件。
- **只修改地图缓存**：写入对象只有 `.map`；`.wld` 全程只读。
- **不兼容国服心动版**（`con.xd.terraria`）：存档布局与版本体系不同，用国服存档运行会得到错误结果，不计划支持。

</details>

<details>
<summary><h2 id="原理">原理（一格地图是怎么算出来的）</h2></summary>

`.map` 不是图片，是 `4096` 格一块的稠密数组：每格 4 字节 `uint16 Type + uint8 Light + uint8 Color`，块内**列主序**，整块 zlib 压缩，头部有 `int32 长度` 前缀（长度为 0 表示整块未探索）。所以"点亮"= 把每格的 `Light` 写成 255、`Type` 按世界数据算出来。

`Type` 是**调色板索引**，不是 tileID：`tile = 1 + prefixTile[tileID]`、`wall = 1 + ΣtileOptionCounts + prefixWall[wallID]`，之后依次是液体、天空渐变、泥土、岩石、地狱各段。1-based 这一点错过一次——写 0-based 会让整张图颜色错位一格（泥土变黑）。

细节、字段表与踩过的坑见 [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) 与 [`docs/L1-STATUS.md`](docs/L1-STATUS.md)。

</details>

<details>
<summary><h2 id="构建与验证">构建与验证</h2></summary>

三份实现互相当对照，任何一份改动都要三份一起过。

```powershell
# 全量：Python + C# + Kotlin 依次自检，汇总 PASS/FAIL
.\scripts\verify-all.ps1

# 单跑
python selftest.py
dotnet run --project csharp\TerraMap.SelfTest            # 需要 .NET 10 SDK
.\scripts\verify-kotlin.ps1                              # 需要 kotlinc 或 kotlin/.tools
```

真实存档级校验（可选，用来对黄金哈希）：

```powershell
dotnet run --project csharp\TerraMap.SelfTest -- --with-samples WORLD.wld TEMPLATE.map
```

期望输出 `reveal size 2158030 bytes, sha256 272C8A80…5FCF8`，与 Python 结果逐字节一致。

Android 侧：

```powershell
cd android
Copy-Item .\local.properties.example .\local.properties   # 把 sdk.dir 指到本机 SDK
F:\Android\gradle\gradle-8.9\bin\gradle.bat :app:assembleDebug --no-daemon
```

工具链缺失时校验脚本以退出码 2 结束并打印 `toolchain: unavailable`，不是"通过"。

</details>

<details>
<summary><h2 id="仓库结构">仓库结构</h2></summary>

| 路径 | 内容 |
|---|---|
| `*.py`（根） | L1 参考实现：`worldfmt.py` 读 `.wld`、`mapfmt.py` 读写 `.map`、`players.py` 角色与文件名、`reveal.py` CLI、`deploy.py` 推设备 |
| `csharp/` | L1 的 C# 实现（`net10.0`，零 NuGet）+ 自检 |
| `kotlin/` | L1 的 Kotlin/JVM 实现（同时被 Android 模块直接引用） |
| `android/` | Compose 外壳工程（Material 3） |
| `scripts/` | 三语言校验脚本 |
| `docs/` | 开发者文档：[架构](docs/ARCHITECTURE.md)、[贡献](docs/CONTRIBUTING.md)、[L1 状态](docs/L1-STATUS.md)、[索引](docs/INDEX.md) |
| `samples/` | 样本说明（**不放真实存档**，路径由参数传入） |

</details>

<details>
<summary><h2 id="限制">已知限制</h2></summary>

- 写 `.map` 前**必须先退出游戏**，否则游戏退出时会用内存里的旧地图覆盖。
- 推到 `/sdcard/...` 后需要 `chown u0_a72:ext_data_rw` + `chmod 660`；FUSE 上 `restorecon` 会报 `Operation not supported`，可忽略。
- 图标、箱子等"地图上的小对象"不在 `.map` 里，属世界数据，本工具不涉及。
- 世界被游戏升级重构过（tileID 段变了）后，旧模板的 option 计数表可能不再匹配，需要重新取模板。
- **PC（Steam）的 `.map` 是旧格式 `fileVersion 318`（带 RLE），当前只认移动版 1.4.5.8 的 `33094`**。拿 PC 地图当模板会被明确拒绝（`unsupported map file version 318`），不会写坏文件。
- 角色从没进过某个世界时，工具会为该角色新建一份 `.map`——这是"所有角色点亮"的预期行为，`--player` 可收窄范围。

</details>

<details>
<summary><h2 id="路线">路线</h2></summary>

版本线自 `0.1.0-dev.1` 起（SemVer，`0.x` 不承诺接口稳定）。

- [x] L1 三语言实现（Python / C# / Kotlin）通过自检；C# 与 Python 输出逐字节一致
- [x] 「世界 × 角色」逐角色写入、`--list` / `--player` / `--dry-run`
- [x] Android Compose 骨架（`arm64-v8a` 优先）
- [ ] Android APK：`arm64-v8a`、`armeabi-v7a`、`x86_64`
- [ ] Windows：单文件 exe 或 portable（含 win-arm64）
- [ ] 可选：`127.0.0.1` 本地控制台形态

</details>

<details>
<summary><h2 id="许可">许可证</h2></summary>

[GPL-3.0-or-later](LICENSE)（全文 35,149 B，取自 gnu.org）。选它的理由与项目形态一致：本项目读写他人存档格式，玩家需要能自行审计"到底改了哪几个字节"，并有权分发自己修改的版本；GPL-3.0 保证衍生版本同样开源。

</details>

## 反馈

提 Issue 时带上这些，能省掉一轮来回：

- 世界名、宽高、`GameMode`（`reveal.py` 会直接打印 `world : … id=… 6400x1800 surface=…`）
- 模板 `.map` 的 `fileVersion`（318 是 PC 旧格式，见[已知限制](#限制)）
- `--dry-run` 的完整输出，以及 `.\scripts\verify-all.ps1` 的结果
- 平台与游戏版本（`v1.4.5.8.1 Build 1729` 这类完整串）

较大的功能或架构调整，先在 Issue 里聊一下再动 PR。

## 致谢

- [Re-Logic](https://terraria.org/) 的 Terraria，以及 wiki 社区整理的[世界文件格式文档](https://terraria.wiki.gg/wiki/User:Minimiscience/World_File_Format)，`.wld` 的字段表有相当一部分靠它校准。
- [lihzahrd](https://github.com/Stealthmate/lihzahrd)：其 `world.py` 的 tile 解码顺序被用作交叉验证（它只支持到 1.4.4.9，本项目在此基础上补了 1.4.5.8 的差异）。
- [ilspycmd](https://github.com/icsharpcode/ILSpy)：反编译本机 PC 版 `Terraria.exe` 的 `Terraria.Map.MapHelper`，才拿到 `.map` 写入/读取的权威算法（区块 64×64、`MapTile` 4 字节、`Light<=18` 视为未探索）。
