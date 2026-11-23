# Changelog

本项目遵循 [SemVer](https://semver.org/lang/zh-CN/)；`0.x` 阶段不承诺接口稳定。
格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)。

## [0.1.0-dev.4] - 2026-11-24

### Changed
- 世界卡片的三态进度标签改为**互斥**，只显示最远状态：`困难模式之前` / `困难模式` / `已击败月亮领主`。困难模式是击败月亮领主的必要不充分条件，两个标签不再同时出现；FTW 仍作为独立芯片。
- 自适应图标前景网格居中到 72dp 安全区内（`28..80`，52 格），旧版 mipmap 矢量图同步重画，启动器不再裁掉右/下边。
- 竖屏：根目录标签行横向滚动、信息卡与 Shizuku 提示卡移入列表首项、列表 `weight(1f)` 独立滚动、底栏改为两行（计数 + 全选/全不选，整宽「点亮所选」），卡片内图标与复选框顶部对齐。

### Notes
- `versionCode 6`。本轮起每次改动都递增 dev 版本号，不再停在同一个 dev 号上。
## [0.1.0-dev.3] - 2026-11-24

### Added
- **Shizuku 直读**：装了 Shizuku 并授权后，App 直接用 shell 用户把 `Android/data/com.and.games505.TerrariaPaid` 的存档镜像到 `/sdcard/Download/.terramap`，无需 adb、无需手工拷贝；点亮后写回原目录，原文件保留为 `.map.bak`（可「撤销本次」）。
- 权限页与列表页都提供「授权 Shizuku」入口；服务未运行时按钮改为打开 Shizuku 本体。

### Changed
- 图标改为上下轴对称版本（`docs/assets/mark.svg` 与 Android 自适应图标同一几何）。
- 世界列表在根目录路径变化时自动重扫（之前镜像生成完成后需要手动刷新）。

### Fixed
- 竖屏布局完全无法使用：根目录标签挤成一个巨块、世界列表被压成 0 高、底部「点亮所选」被挤成两行。现在标签行横向滚动且单行省略，信息卡与 Shizuku 提示卡改为列表首项（不再和列表抢高度），列表用 `weight(1f)` 独立滚动，底栏改成「计数 + 全选/全不选」一行加整宽按钮，卡片内图标与复选框顶部对齐。
- Shizuku 命令执行：并发读 stdout/stderr 并主动关闭 stdin，命令不再卡住；每次调用后释放远端进程。
- 镜像暂存不再 `rm -rf` 目录（该设备上会 `Text file busy` 长时间阻塞），改为就地覆盖。
- 写回校验改看远端文件大小，`chmod` 在 `/sdcard` 上返回 EOPNOTSUPP 曾把成功误报为失败。
## [0.1.0-dev.2] - 2026-11-23

Android 第一版可用形态：申请「所有文件访问权限」→ 列出存档里的世界 → 选世界选角色 → 一键点亮。
仍是 debug 签名，仅供本地安装测试。

### Added

- **液体层**：`World` 现在保留每格的液体量与液体种类（水 / 岩浆 / 蜂蜜 / 微光），
  点亮时按 `MapHelper.CreateMapTile` 的真实优先级着色 —— 方块 > 液体（量 > 32）> 墙 > 深度背景。
  此前液体被解析时丢弃，导致水面与岩浆海在地图上全黑，只有走近才被游戏自己点亮。
- **权限门**：API 30+ 走 `MANAGE_EXTERNAL_STORAGE`（`Environment.isExternalStorageManager()`），
  API 24–29 走 `READ/WRITE_EXTERNAL_STORAGE` 运行时申请 + `requestLegacyExternalStorage`。
- **存档发现**：`data/TerraPaths.kt` 按序探测 `Android/data/com.and.games505.TerrariaPaid/{Worlds,Players}`、
  `.../files/{Worlds,Players}`、`Documents/My Games/Terraria/*`、`Terraria/*`；多根时用筛选片切换。
- **世界列表**：只读每个 `.wld` 前 64 KiB 走 `WorldFile.parseMeta`，显示名字 / 难度 / 尺寸 / 邪恶 /
  肉山后 / 月总后 / FTW；读不出来的文件单独列原因，不拖垮整页。
- **角色选择**：默认全选，已有地图与「将新建」分开标注；地图文件名由世界 GUID 推导。
- **点亮**：每个世界只解析一次 `.wld`、只生成一份载荷，随后逐角色写入；写入前备份为
  `<文件名>.map.bak`，先写 `.tmp` 再原子改名，并用 `MapFile.read` + `exploredCount()` 回读校验。
- **撤销**：从 `.map.bak` 逐字节还原。
- **模板回退**：同世界任一角色的 `.map` → 同尺寸 donor 地图头（重写 name/worldId/宽高）→ 都没有则
  禁用按钮并说明「先让任一角色进一次世界」。
- **UI**：Material 3（动态取色 / 亮暗两套品牌色）、`Scaffold` + `TopAppBar` + `Card` 列表 + 底部汇总条 +
  进度与结果对话框；自适应图标与矢量前景。
- **单测**：`HeaderRebuildTest`（donor 头部重序列化 + 70×70 地图 round-trip）。

### Notes

- `versionCode 3`；`android:largeHeap="true"`（8400×2400 世界峰值约 200 MB）。
- 液体着色的阈值与槽序取自反编译的 `Terraria.Map.MapHelper.CreateMapTile`：
  `liquid > 32` 才画，槽序 water / lava / honey / shimmer（`maxLiquidTypes = 4`）。
  实测本地中世界 `live.wld` 共 580,964 格达标（水 255,885 / 岩浆 321,508 / 蜂蜜 2,571 / 微光 1,000）。
- 纯 Kotlin 无 `.so`，单个 APK 同时适配 arm64-v8a 与 x86_64 模拟器。
- **平台限制**：Android 11+ 下 `MANAGE_EXTERNAL_STORAGE` 不授予对其它应用 `Android/data`
  的读取（实测授权后 `ls` 仍 `Permission denied`）。App 因此优先扫描两个便携目录
  `/sdcard/Download/TerrariaSaves/` 与 `/sdcard/Documents/TerrariaSaves/`，
  空状态页给出三条可行路径（便携目录 / PC 端 reveal+deploy / root 桥接）。

## [0.1.0-dev.1] - 2026-11-23

首个开发版：把「读世界 → 算地图 → 写地图」的核心逻辑固化成三语言实现，
并提供 Android 外壳。**不含**任何打包产物，只保留可复现的核心读取/计算/写入。

### Added

- **L1 核心（Python）**：`worldfmt.py`（`.wld` 头部与 tile 流解析）、`mapfmt.py`
  （`.map` 头部、1-based 调色板、64×64 区块 zlib 读写）、`players.py`（世界 GUID ⇄
  角色地图文件名）、`reveal.py`（CLI，默认全图覆盖点亮）、`deploy.py`（adb 推送）、
  `selftest.py`（合成样本 + 真实样本自检）。
- **L1 核心（C#）**：`csharp/TerraMap.Core`（`net10.0`，零 NuGet）与
  `csharp/TerraMap.SelfTest`；`--with-samples` 可对真实存档做字节级校验，
  输出与 Python 黄金样本 SHA-256 完全一致。
- **L1 核心（Kotlin）**：`kotlin/src/main/kotlin/io/terramap/core`，纯 JVM 实现，
  自检用普通 `main()`，不依赖测试框架。
- **Android 外壳**：`android/`（AGP + Compose Material 3），`core` 模块直接引用
  `kotlin/src/main/kotlin`，不复制源码；首屏显示 ABI/SDK/机型并跑一次核心自检。
- **校验脚本**：`scripts/verify-dotnet.ps1`、`scripts/verify-kotlin.ps1`、
  `scripts/verify-all.ps1`；工具链缺失时退出码 2 并打印 `toolchain: unavailable`。
- 文档：`README.md`（使用者入口）、`docs/ARCHITECTURE.md`（三层架构与 L1 契约）、
  `docs/CONTRIBUTING.md`（改动与验证流程）、`docs/L1-STATUS.md`（三语言状态与已知坑）。
- **README 重排**（参照 MiaCode 的写法）：首屏只留 logo / badge / 一句话定位 / 快速开始，
  构建、原理、仓库结构、限制、路线、许可证全部折叠进 `<details>`；新增 `README_EN.md`
  双语入口、`AGENTS.md`（代理代码边界与六条不变量）、`docs/INDEX.md`（原 `docs/README.md`
  改名）、`docs/assets/mark.svg`（项目标记）。

### Notes

- 只识别移动版（1.4.5.8，`.map` fileVersion 33094）；PC/Steam 的 318 格式会被明确拒绝。
- 与 tModLoader 无关；不挂载、不注入游戏进程；只读写存档文件。
- 不兼容国服心动版（`con.xd.terraria`）。
