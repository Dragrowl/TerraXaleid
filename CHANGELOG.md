# Changelog

本项目遵循 [SemVer](https://semver.org/lang/zh-CN/)；0.x 阶段不承诺接口稳定。
格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)。

## 版本号约定

- `0.1.0-dev.N` 是唯一开发线：N 每轮递增，`versionCode` 同步 +1，不开 `0.1.1-dev.*` 之类的平行序列。
- `0.1.0-beta.1`：`docs/ROADMAP-NEXT.md` 那一轮（顶栏下拉 / 设置页 / 主题卡 / 关于页 / 返回分层）全部落地后切。
- `dist/` 文件名只带 `versionName`（`terramap-0.1.0-dev.N.apk`），不带 `-release`、`-shizuku` 之类定语；
  构建类型与改动内容进提交信息、git tag 和本文件。

## [0.1.0-dev.22] - 2026-11-24

### Removed
- `res/font/endfield_by_butan.ttf`：萨卡兹语字体只服务于那三张主题缩略图，不再随 APK 分发，也不在仓库里留副本。
  将来做 Windows 版需要它时，按 <https://github.com/lhclbt/Endfield_Font> 重新取。
- `docs/shots/`：截图工程的源码副本（Kotlin 源、manifest、build 脚本）。工程本体仍在仓库外的 `G:\terramap-shots\`。
- `res/drawable/theme_thumb_{auto,light,dark}.xml`：占位矢量，主题卡已改用实机截图。

### Changed
- 仓库里带萨卡兹语字形的只剩位图：`docs/assets/shots/*.png` 与 `res/drawable-nodpi/theme_preview_*.png`。
- `docs/ROADMAP-NEXT.md` 全篇按现状打钩，剩下的只有 beta.1 的收口动作；README 双语致谢里注明该字体只用于缩略图。

### Notes
- `versionCode 24`。
## [0.1.0-dev.21] - 2026-11-24

### Added
- 关于页（单卡）：左侧软件图标，右侧「terramap / 版本 0.1.0-dev.21」；下方一行左侧是「发布者 Dragr0wl」与「GPL-3.0-or-later」，右侧是 GitHub 与哔哩哔哩两个小图标按钮——不单独成卡、没有副标题。
- 设置页新增「关于」卡作为入口，副标题显示版本与许可，右侧一个箭头。
- 哔哩哔哩按钮打开 `https://space.bilibili.com/3461573882415902`；GitHub 按钮先留空，画成 38% 透明的禁用态，点了不发任何跳转。

### Fixed
- 关于页的软件图标改为「矢量前景 + 深色圆角底」组合：直接把自适应图标 XML 喂给 `painterResource` 会抛 `IllegalArgumentException: Only VectorDrawables and rasterized asset types are supported`（第一次进关于页就崩）。

### Changed
- 返回分层变成三级：关于 → 设置 → 世界列表 → 退出；退出行为仍受「退回桌面时结束进程」开关控制。

### Notes
- `versionCode 23`。
## [0.1.0-dev.20] - 2026-11-24

### Added
- 设置页「外观」卡：三张 **实机截图缩略图**（跟随系统 / 浅色 / 深色），选中项描边高亮并带勾；点击即切换，配色按槽位做 300 ms 渐变。
- 设置页「深色变体」卡：只在选中「深色」时出现，标准深色与 OLED 全黑二选一。
- `res/drawable-nodpi/theme_preview_{auto,light,dark}.png`：由 `docs/assets/shots/*-3x2.png` 缩到 720×480，是运行中界面的真实像素，不是画出来的示意图。

### Changed
- 主题卡不再使用矢量占位图：`theme_thumb_*.xml` 保留到 dev.22 随字体一起清理。

### Notes
- `versionCode 22`。
## [0.1.0-dev.19] - 2026-11-24

### Added
- **设置页**（`ui/SettingsScreen.kt`）：卡片式，每项带副标题，先落地「行为」卡。
- **顶栏下拉**：世界列表右上角改为 `⋮` 菜单，含「设置 / 恢复与备份 / 刷新」，不再让两个图标各自占位。
- `AppConfig` 增加 `backExit`（`background` / `kill`）与 `AppConfigStore.write()`：写到 `.tmp` 再改名，失败只记日志不抛。`ui/ThemeController.kt` 持有配置状态，设置页改动即时写入。
- 返回行为可配置：`kill` 时按返回结束进程（先 `finishAndRemoveTask()` 再 `Process.killProcess()`，正在写图时降级为退回后台），`background` 时只把任务退到后台，进程与已解析的世界留在内存里。

### Changed
- 主题切换改成逐槽 `animateColorAsState`（300 ms，emphasized decelerate），不再整树 `Crossfade`——后者会重建组合、把当前页面和勾选状态一起丢掉。
- `ThemeMode.Auto` 不再使用 Android 12+ 的动态取色：跟随系统只决定明暗，配色仍用本项目自己的石墨灰，缩略图才对得上。
- 过渡动效常量搬到 `ui/Motion.kt`，页面切换与主题渐变共用同一组曲线。

### Notes
- `versionCode 21`。
## [0.1.0-dev.18] - 2026-11-24

### Changed
- **文档全量重写**，对齐当前实现（`0.1.0-dev.17` 的代码状态）：
  - 根 `README.md` / `README_EN.md`：去掉「核心是纯数据处理、没有 GUI」这类过时表述，
    改成「Android 上用图形界面、电脑上用命令行」两条路径；补备份/撤销/全黑重置、Shizuku 必要性与便携目录兜底、
    版本号约定、`package-apk.ps1` 打包命令；「原理」与「仓库结构」折进 `<details>`，不再是正文噪音。
  - `docs/ARCHITECTURE.md`：L1 契约改成实际签名（`net10.0`、`WorldFile.ParseMeta/Load`、`MapFile.Read/Write`、
    `Reveal.Build/Write`、`PlayerMaps`）；L2 改成「Shizuku 主路线 + 便携目录兜底」并写清写入链路
    （镜像 → 备份轮换 → 写回 → 属主还原 → 回读校验）；L3 改成实际三态页面与 shared axis 动效；
    新增「版本号与产物」一节，说明纯 Kotlin 无需按 ABI 拆分。
  - `docs/L1-STATUS.md`：补液体层与 `ceil(maxId/8)` 两条坑，合成自检覆盖范围逐条列出，
    遗留项改成实际状态（Android 外壳已实现）。
  - `docs/ROADMAP-NEXT.md`：改名为「通往 0.1.0-beta.1 的清单」，逐项标 `[x]` / `[ ]`，
    已完成与待做分开写，并列出已就绪但尚未接线的资产。
  - `docs/CONTRIBUTING.md`、`docs/INDEX.md`、`AGENTS.md`、`samples/README.md`：同步目录、命令、版本规则、文档分工。
- `README.md` 里残留的 NUL 字节（早前用 PowerShell 双引号写 `` `0 `` 造成）已清除，文件重新按纯文本处理。

### Notes
- 本轮只动文档与版本号，没有功能改动；`versionCode 20`。
## [0.1.0-dev.17] - 2026-11-24

### Changed
- 版本号归位：之前几轮误开成 `0.1.1-dev.1/2/3`，现在统一回到 `0.1.0-dev` 序列，续在 `0.1.0-dev.16`
  之后成为 `0.1.0-dev.17`（`versionCode 19`）。对应 git tag 也已改名为 `v0.1.0-dev.14/15/16`。
- `versionName` 保持 `0.1.0-dev.N` 直到路线那一轮完成，届时切 `0.1.0-beta.1`。

## [0.1.0-dev.16] - 2026-11-24

### Changed
- **配色回到灰阶附近**：上一版主体色是暖白 `#FBF7F2` 和偏绿的 `#14170F`/`#2A2F26`，看起来又暖又绿。
  现在浅色用 `#F6F6F4` 底 / `#E8E8E4` 卡片 / `#1A1A18` 正文，深色用 `#121212` 底 / `#232322` 卡片 /
  `#E3E3DF` 正文；主色 `#4F5A51`（深）/ `#B4C3B6`（浅）饱和度压到 2–4%，整体读起来是中性石墨而不是
  暖纸或绿黑。新增 `outline`/`outlineVariant` 两档，边框不再靠 `surfaceVariant` 硬顶。

### Added
- `docs/shots/` 里存了一份**截图用的独立工程**（Kotlin 源、manifest、build 脚本的副本，仅存档参考）：
  它不在 Gradle 构建里、不进 APK，硬编码萨卡兹字体与预生成字符串、不读任何文件、隐藏系统栏。
- `docs/assets/shots/`：实机截图 `light-3x2.png`、`dark-3x2.png` 与合成后的 `auto-3x2.png`
  （3:2，从标题栏顶部起裁）。

### Notes
- 3:2 画幅下的 65° 切线落点与正方形不同：`w=1440,h=960` 时线从顶边 `x≈944` 进、底边 `x≈496` 出，
  因为陡线在宽画幅里是穿上下边而不是左右边。

## [0.1.0-dev.15] - 2026-11-24

### Added
- 配置文件驱动的主题：`terramap.json`（`{"theme":"auto|light|dark","darkVariant":"standard|oled"}`），
  查找顺序为 `Android/data/io.terramap.android/files/` → `/sdcard/Download/.terramap/` → 应用私有目录；
  文件不存在时会自动生成一份默认的，`ON_RESUME` 时重读，所以推一份新配置不用重启应用。
- OLED 全黑配色（`background`/`surface` 纯黑，卡片 `#0A0A0A`），只在「深色 + oled」时启用。
- 强制主题时关闭动态取色，保证同一份配置在任何设备上截出来的图一致。
- 每读一次配置都打一行日志：`I terramap: config: theme=dark darkVariant=oled from=...`。

### Fixed
- 跟随系统缩略图的斜切方向反了：改为从 **+x 方向逆时针 65°** 过画布中点，即切线从顶边
  `x = 79.18` 进、底边 `x = 28.82` 出；两半各自 `clip-path` 裁剪后各画内容。

## [0.1.0-dev.14] - 2026-11-24

这一版先把素材落地，界面改造按 `docs/ROADMAP-NEXT.md` 的清单逐项推进。

### Added
- `res/font/endfield_by_butan.ttf`：萨卡兹语字体，取自 <https://github.com/lhclbt/Endfield_Font>。
- `res/drawable/ic_github.xml`、`res/drawable/ic_bilibili.xml`：GitHub / Bilibili 轮廓图标，24×24 视口，路径数据来自 simple-icons。
- 三张主题缩略图 `theme_thumb_light/dark/auto.xml`（108×108）：浅色、深色，以及「跟随系统」——按 65° 过画布中点的斜切把两半各自裁剪、各画各的内容。
- `docs/ROADMAP-NEXT.md`：0.1.0-beta.1 的完整改造清单（返回分层、顶栏下拉、设置卡片、主题与 OLED、关于卡片、图标里的随机萨卡兹文本）。

## [0.1.0-dev.13] - 2026-11-24

### Added
- **页面切换动效**：三个画面之间走 Material「shared axis X」——新页面按 1/8 宽度滑入并淡入，旧页面向反方向滑出并淡出，进向用 emphasized decelerate `CubicBezierEasing(0.05, 0.7, 0.1, 1)`、出向用 emphasized accelerate `(0.3, 0, 0.8, 0.15)`，时长 300 ms；返回时方向自动反转（由 `stageDepth` 判断层级）。
- 清单开启 `android:enableOnBackInvokedCallback="true"`，Android 13+ 的预测性返回手势可以正常接管。

### Fixed
- **系统返回键**：原来在任意子页面按返回都会直接 finish activity 退回桌面。现在子页面（选角色、恢复与备份）按返回回到世界列表，只有在世界列表按返回才退出应用。

## [0.1.0-dev.12] - 2026-11-24

### Fixed
- 覆盖前的「是否已有备份」改为向**设备**查询，而不是看 Shizuku 暂存镜像：镜像只是快照，恢复或删除之后它与磁盘不一致，按镜像判断会漏报或多报。
- 新建地图（该角色还没有 `.map`）写回后属主原本会留在 `root`、mode 770，游戏可能读不到。现在无原文件时以**角色目录的属主**兜底，并统一 `chmod 660`。

### Added
- 「删除地图（全黑）」加确认框：这是恢复页里唯一无法靠拷回文件撤销的动作。

## [0.1.0-dev.11] - 2026-11-24

### Fixed
- 恢复页底部三个动作在竖屏被裁成「删除地图（全」；改为「删除所选备份 / 删除地图（全黑）」同一行、小一号字，三个按钮都完整可见。

## [0.1.0-dev.10] - 2026-11-24

### Fixed
- 恢复页底部三个动作纵向堆叠时占掉太多高度，列表可视区被压扁；改为两个删除类动作同排。

## [0.1.0-dev.9] - 2026-11-24

### Changed
- 恢复页的删除类动作改为整行按钮，避免长文案被截断。

## [0.1.0-dev.8] - 2026-11-24

### Added
- **覆盖前备份链**：每次覆盖前都把当时的 `.map` 留成 `.map.bak`；如果上一次的备份还在，会按选择决定是替换它还是把它改名成 `.bak.1`、`.bak.2`……依次往后排，所以「保留上一次备份」永远不会静默丢掉文件。
- **再次覆盖确认框**：选中角色里只要有一个已经存在备份，就弹确认框，默认勾选「删除上一次的备份」；勾选时显示红底提示说明旧备份会被替换、无法再恢复，可以手动取消勾选改为保留。
- **顶栏「恢复与备份」入口**：列出当前根下每张地图及其备份数量、最新备份时间与大小；支持多选后「恢复所选备份」「删除所选备份」「删除地图（全黑，游戏内重建为完全未探索）」。

### Notes
- `versionCode 10`。
- 恢复走同一套 Shizuku 通道，写回时保留原文件的 `uid:gid` 与 mode。

## [0.1.0-dev.7] - 2026-11-24

### Changed
- **产物命名规则**：`dist/` 里只保留 `<name>-<versionName>.apk`（如 `terramap-0.1.0-dev.7.apk`）。构建类型、签名状态或本次改了什么，一律写在提交信息、git tag 和 CHANGELOG 里，不进文件名——同一版本出现多个只差后缀的文件，迟早会装错。历史产物同步改名（`-liquid`、`-shizuku`、`-release` 三个后缀已去掉）。

### Added
- `scripts/package-apk.ps1`：从 `app/build.gradle.kts` 读 `versionName`/`versionCode`，跑 `assembleRelease`，把产物拷成 `dist/terramap-<versionName>.apk` 并打印大小与 SHA-256。`-SkipBuild` 只做拷贝与校验。

### Notes
- `versionCode 9`。

## [0.1.0-dev.6] - 2026-11-24

### Fixed
- **写回误报「写回设备失败」**：判断依据从「写回后文件的字节数必须等于本地暂存文件的字节数」改成「`cp` 的退出码」。实测（vivo V2505A）写回其实成功、进游戏地图已全亮，但设备侧 `stat` 读回的尺寸与本地不一致，旧逻辑把成功判成失败。现在尺寸只作诊断：不一致时状态仍为成功，并在文案后面标注 `（大小校验 X≠Y）`；只有 `cp` 真的失败才报错，错误里带上退出码与 stderr。
- 写回时的属主/权限还原改为从**原文件**读取 `uid:gid`/`mode`（之前从 `.bak` 读，而 `.bak` 由 `cp` 新建、属主已变），撤销同理。

### Added
- 所有 Shizuku 调用都记 `Log.i/w/e`，tag 固定为 `terramap`：每条 shell 命令、退出码、stdout/stderr 都会进 logcat，写回成功时还会打印实际字节数。

### Notes
- `versionCode 8`。

## [0.1.0-dev.5] - 2026-11-24

### Added
- **发布签名**：`release` 变体用本地 keystore 签名（v2 方案，`CN=TerraMap`），产物 `terramap-0.1.0-dev.5-release.apk`，10.2 MB（当时的命名，现已统一为只带 versionName）。keystore 与 `keystore.properties` 都在 `android/` 下且已加入 `.gitignore`，首次构建前缺失时 release 退化为未签名、不影响 debug 构建。
- README 增加「安装与首次授权」小节（未知来源安装 → 所有文件访问权限 → Shizuku 授权）。

### Changed
- **所有对游戏存档的读写都走 Shizuku**，不再有 `su`/root 分支；adb root 只用于开发期排查。
- Shizuku 以 root 运行时写回会保留原文件的属主与权限：覆盖前先 `stat` 原文件的 `uid:gid` 与 `mode`，写入后按原值 `chown`/`chmod`（`/sdcard` 上 `chmod` 可能返回 EOPNOTSUPP，失败容忍），避免 `root:root` 归属让游戏读不到地图。
- 撤销同样保留属主/权限，并在设备侧比对 `.bak` 与恢复后的文件大小，不再依赖本地区性判断。

### Notes
- `versionCode 7`。R8 保持关闭：`ShizukuShell` 通过反射调用 `Shizuku.newProcess`，压缩会破坏它。

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
