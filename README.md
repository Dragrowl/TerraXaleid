<p align="center">
  <img src="docs/assets/mark.svg" alt="TerraXaleid" width="96">
</p>

<h1 align="center">TerraXaleid</h1>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPLv3-blue?style=for-the-badge" alt="GPL-3.0-or-later"></a>
  <a href="https://github.com/Dragrowl/TerraXaleid/releases"><img src="https://img.shields.io/github/v/release/Dragrowl/TerraXaleid?include_prereleases&style=for-the-badge&label=release&color=2E7D32&cacheSeconds=60" alt="release"></a>
  <a href="#构建与验证"><img src="https://img.shields.io/badge/selftest-PASS-2E7D32?style=for-the-badge" alt="selftest"></a>
  <a href="#构建与验证"><img src="https://img.shields.io/badge/L1-python%20%7C%20csharp%20%7C%20kotlin-8957E5?style=for-the-badge" alt="L1 三语言"></a>
</p>

<p align="center">
  中文 | <a href="README_EN.md">English</a>
</p>

TerraXaleid 读 Terraria 的世界存档 `.wld`，算出每一格在地图上应该显示什么，再写成合法的地图缓存 `.map`。结果就是：**打开游戏里的地图，整张世界全部点亮，一格黑区都不剩；而世界本身——物块、物品、NPC、进度——一个字节都不动。**

核心是纯数据处理：输入字节，输出字节。不碰游戏进程，三份实现（Python / C# / Kotlin）对同一份存档给出同样的结果，外层界面只是把这件事搬到不同平台上。

## 平台状态

| 平台 | 状态 | 说明 |
|---|---|---|
| **Android** | ✅ 可用（`0.1.0-rc` 线） | Compose（Material 3）界面，读写走 Shizuku，装 Releases 里的 APK 即用 |
| **Windows** | 🚧 命令行可用 | `terraxaleid-cli` 能读 Steam 存档、写 PC 版 `.map`（`--reveal-pc`），也可用 `--auto-test` 做端到端自检；图形界面还没提供 |
| L1 核心 | ✅ 三语言完成 | Python / C# / Kotlin 逐格结果一致（`decoded-grid sha256` 相同），是各平台界面的公共底座 |


## 使用之前

- **手机端**：Android 11 以上；需要先装并启动 [Shizuku](https://github.com/RikkaApps/Shizuku/releases/latest)（最新版在 Releases 页），本工具的跨应用读写全靠它。
- **桌面端（命令行）**：Python 3.10+ 与 `numpy`；或者直接用 C# 的 `terraxaleid-cli`（`dotnet` 10 运行时，无需 Python）。
- **通用**：动手前先把游戏完全退出——游戏退出时会把内存里的地图写回磁盘，覆盖本工具的成果。
- 只处理地图缓存，世界文件本身只读；改之前会自动备份成 `.map.bak`，随时可撤销。

## 这工具能干什么

| 想做的事 | 怎么做 |
|---|---|
| 手机上直接把地图全点亮 | 装 APK，授权 Shizuku，选世界 → 选角色 → 点亮（不需要电脑、不需要 adb） |
| 只点亮某个角色 | 同上，角色列表里取消勾选其它人 |
| 点亮完反悔 | 结果页「撤销本次」，或到「恢复与备份」里恢复 `.map.bak` |
| 把地图清空回全黑（重新探索） | 「恢复与备份」→ 勾选 → 删除地图（全黑） |
| 换主题 | 设置 → 外观：跟随系统 / 浅色 / 深色；深色下还有「标准深色 / OLED 全黑」 |
| 换界面语言 | 设置 → 语言：跟随系统 / 简体中文 / 繁體中文 / 日本語 / English，选完立即生效，不用重启 |
| 世界很多找不过来 | 世界列表顶部的搜索框，输入名字片段即可过滤，命中的字会高亮 |
| 电脑上批量处理（临时手段） | 命令行 `python reveal.py`，配合 `--players-dir` 一次写所有角色 |

地图是**按「世界 × 角色」各存一份**的（`Players/<角色>/<世界GUID>.map`），所以"点亮"必须逐角色写；默认给本机所有角色都写一份，只给一个角色写的话，其他人进游戏还是黑图。

每格颜色按 `Terraria.Map.MapHelper.CreateMapTile` 的真实优先级决定：**方块 > 液体（存量 > 32）> 墙 > 深度背景**。液体四槽是水 / 岩浆 / 蜂蜜 / 微光，所以海、湖、岩浆海在点亮的图上都有颜色，不会留成黑区。

## 手机上用（Android）

```
1) 把 Releases 里的 APK 传到手机，允许「未知来源」后安装
2) 首次启动：按提示授予「所有文件访问权限」（Android 11+ 要手动去系统设置里打开）
3) 安装并启动 [Shizuku](https://github.com/RikkaApps/Shizuku/releases/latest)（root 或无线调试均可），回到 App 点「授权 Shizuku」→ 选「始终允许」
4) 世界列表出现「游戏目录 (Shizuku)」→ 勾选世界 → 勾选角色 → 点亮所选
```

- 改之前请**先退出世界并关闭游戏**：游戏退出时会把内存里的地图写回磁盘，覆盖本工具的成果。
- 写回时把原 `.map` 备份成 `.map.bak`（再覆盖时旧备份依次让位成 `.bak.1`、`.bak.2`），结果页可以一键撤销。
- 右上角 ⋮ 里有「设置 / 恢复与备份 / 刷新」；返回键逐级退回，主页再按一次退出（可在设置里改成直接结束进程）。
- 横屏或大屏上世界与角色卡片会自动排成 2–4 列（按屏幕宽度，不按机型硬编码）。

为什么必须要 Shizuku：Android 11 起系统禁止普通应用读取**别的应用**的 `Android/data`，连「所有文件访问」也拿不到那个目录。没有 Shizuku 时，可以用 `Download/TerrariaSaves/` 或 `Documents/TerrariaSaves/` 这两个便携目录：把 `Worlds/` 与 `Players/` 拷进去，App 直接读写它们，再用文件管理器搬回游戏目录。

## 桌面端

Windows 端目前提供 C# 命令行 `terraxaleid-cli`：`--inspect` 看存档、`--reveal-pc` 写 PC 版 `.map`、`--auto-test` 做端到端自检（读世界 → 认角色 → 写图 → 回读比对）。

```powershell
# 端到端跑一遍：世界 + 角色，写完立刻回读比对
dotnet run --project windows\TerraXaleid.Cli -- --auto-test --world 111test --player koboshi

# 自己指定输出文件（不落盘就加 --dry-run）
dotnet run --project windows\TerraXaleid.Cli -- --reveal-pc --world WORLD.wld --template TEMPLATE.map --out OUT.map
```

PC 版地图是 `fileVersion 329`（1.4.5 及更早为 `326`），移动版是 `33097`（1.4.5.8 及更早为 `33094`），两种格式不能混用：给哪台机器写图，就得用那台机器的编码器。

Python 的参考实现继续用来做三语言交叉验证：

### 需要什么

- Python 3.10+ 与 `numpy`：`python -m pip install -r requirements.txt`
- 一份世界存档 `.wld`，以及**任意一个进过这个世界的角色的** `.map`（当模板，决定调色板基址）
- 游戏处于**关闭**状态

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

| 平台 | 世界 `.wld` | 角色与地图 `.map` |
|---|---|---|
| Windows / Steam | `%USERPROFILE%\Documents\My Games\Terraria\Worlds` | `%USERPROFILE%\Documents\My Games\Terraria\Players` |
| Android 国际版 | `/sdcard/Android/data/com.and.games505.TerrariaPaid/Worlds` | `/sdcard/Android/data/com.and.games505.TerrariaPaid/Players` |
| MuMu 模拟器 | 同上，用 `adb` 或模拟器内文件管理器取 | 同上；`deploy.py` 会处理属主（`u0_a72:ext_data_rw`）与 `660` |

<details>
<summary>其它命令行参数</summary>

| 场景 | 命令 |
|---|---|
| 只给指定角色点亮 | 追加 `--player 角色名`（可重复） |
| 单文件模式（自己指定输出） | `python reveal.py WORLD.wld TEMPLATE.map -o OUT.map` |
| 保留游戏已探索格子的原始类型 | 追加 `--keep-explored-type` |
| 推到模拟器 / 手机 | `python deploy.py OUT.map --serial 127.0.0.1:16416` |

</details>

## 构建与验证

三份 L1 实现处理同一份字节，必须给出同一个结果。全量校验：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\verify-all.ps1
```

单独跑：

```powershell
python .\selftest.py                                                     # Python
powershell -File .\scripts\verify-dotnet.ps1                             # C#（net10.0，零 NuGet）
powershell -File .\scripts\verify-kotlin.ps1                             # Kotlin（离线编译器或 gradle）
```

Android（需要 `JAVA_HOME` 指向 JDK 21；Gradle 8.9 与 SDK 34 见 `local.properties`）：

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
powershell -ExecutionPolicy Bypass -File .\scripts\package-apk.ps1
# 产物：dist\TerraXaleid-<versionName>.apk
```

`package-apk.ps1` 从 `android/app/build.gradle.kts` 读 `versionName` / `versionCode`，跑 `:app:assembleRelease`，按版本号命名并打印 size 与 SHA-256。

**签名与升级**：`release` 用本地 keystore 签名（密码在 `android/keystore.properties`，两个文件都不进仓库）。覆盖安装必须用同一把 key。

## 原理（折叠）

<details>
<summary>一格地图是怎么算出来的</summary>

1. 读 `.wld`：段偏移表 → 世界信息（名字/尺寸/难度/出生点/地表线/岩石层）→ tile 流（列优先、RLE 不跨列）→ 得到每格的 `tileID`、`wallID` 与液体存量。
2. 读模板 `.map`：头部给出调色板基址（`tile` 段起点 1、`wall` 段、液体 4 槽、天空/泥土/岩石渐变、地狱）与各 tileID 的变体数量。
3. 逐格决定 Type：有方块 → 方块调色板；否则液体存量 > 32 → 液体调色板；否则有墙 → 墙调色板；否则按深度取渐变背景。
4. Light 一律 255（= 已探索、完全照亮），Color 0。
5. 按 64×64 区块重新打包：块内**列主序**，每块 4 字节/格 × 4096 格，zlib 压成流，前面写 `int32` 长度；`length <= 0` 的块表示整块空。
6. 写回后回读校验：`unexplored == 0`、区块数 = `ceil(W/64)*ceil(H/64)`。

细节与坑见 [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) 与 [`docs/L1-STATUS.md`](docs/L1-STATUS.md)。

</details>

<details>
<summary>仓库结构</summary>

| 路径 | 内容 |
|---|---|
| `*.py` | L1 参考实现（`worldfmt` / `mapfmt` / `players` / `reveal` / `deploy`）与自检 |
| `csharp/` | L1 的 C# 实现（`net10.0`，零 NuGet）+ 自检 |
| `windows/` | Windows 端：`TerraXaleid.Core.Win`（存档发现）与 `TerraXaleid.Cli`（命令行入口） |
| `TerraXaleid-<versionName>.apk` | Android 安装包产物，见 `dist/` |
| `kotlin/` | L1 的 Kotlin/JVM 实现 + 自检（被 Android 模块直接引用，不复制源码） |
| `android/` | Compose 外壳（Material 3），`app` + `core` 两个模块 |
| `scripts/` | 三语言校验、打包脚本 |
| `docs/` | 开发者文档；`docs/assets/` 放项目标记与截图 |
| `samples/` | 样本说明（**不放真实存档**） |
| `dist/` | 构建产物（被 git 忽略） |

</details>

<details>
<summary>已知限制</summary>

- PC/Steam 的 `.map`（`fileVersion 329`，旧版 `326`）与移动端（`33097`，旧版 `33094`）格式不同，**不能互相通用**：把 PC 的图放进手机（或反过来），游戏不会显示，也没有自动转换。
- 不兼容心动代理的国服 Terraria（包名与存档布局都不同）。
- 只处理地图缓存。世界文件本身只读不改；改难度那种事不在本工具范围内。
- Android 端一切读写走 Shizuku shell；没有 Shizuku 就只能用便携目录。

</details>

## 三条声明

1. 本项目为独立第三方开源工具，与 Re-Logic 及 Terraria 官方无关。
2. 本项目主要由 AI 辅助编写与迭代（开发过程中使用了 DeepSeek-V4.1-Flash 辅助生成代码），核心功能均经过本地存档测试验证。因缺乏完整传统工程经验，代码结构可能存在不足，欢迎 Issue 反馈。
3. 只修改存档文件，不注入、不挂载、不改游戏进程。

## 反馈

问题与建议走 Issue。附上这几样能省很多来回：

- 世界尺寸与难度（截图世界列表即可）
- `adb logcat -s terraxaleid:*` 的输出（每条 Shizuku 命令、退出码、stdout/stderr 都在里面）
- 出错时 `.map` 的字节数，以及是否能复现

## 许可

GPL-3.0-or-later，见 [LICENSE](LICENSE)。每个源文件头部有 `SPDX-License-Identifier`。

## 致谢

- [Shizuku](https://github.com/RikkaApps/Shizuku) — 让非 root 设备也能安全地做跨应用文件操作
- [Endfield Font](https://github.com/lhclbt/Endfield_Font) — 只用来渲染那三张主题缩略图，字体文件不进仓库
- [simple-icons](https://github.com/simple-icons/simple-icons) — GitHub / Bilibili 图标轮廓
- Terraria 存档格式的公开整理（wiki.gg 的 World File Format 与 map 格式说明）
