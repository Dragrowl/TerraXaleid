# Changelog

本项目遵循 [SemVer](https://semver.org/lang/zh-CN/)；0.x 阶段不承诺接口稳定。
格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)。

## 版本号约定

- `0.1.0-dev.N` 是唯一开发线：N 每轮递增，`versionCode` 同步 +1，不开 `0.1.1-dev.*` 之类的平行序列。
- `0.1.0-rc.N`：功能冻结后的候选发布，只修显示与稳定性问题；`versionCode` 继续逐版 +1。
- `0.1.0`：`rc` 线稳定后转为正式版。
- `dist/` 文件名只带 `versionName`（`TerraXaleid-0.1.0-dev.N.apk`），不带 `-release`、`-shizuku` 之类定语；
  构建类型与改动内容进提交信息、git tag 和本文件。

## [0.1.0-beta.1] - 2026-11-28（Windows 端）

- Windows 版本序列自此并入 `0.1.0-beta.*`（Android 保持 `0.1.0-rc.16` 不动），安装包与 zip 文件名同步换成 `0.1.0-beta.1`。
- **版式**：删掉「选择世界」标题行；世界卡不再固定 320 宽，改按窗口宽度自动增减列数并横向填满；滚动条改成 8px 并贴到窗口边缘。
- **设置页**：卡片改成两列自由堆叠、不再等高；外观卡三个主题缩略图重排成等宽三列；OLED 开关移到主题卡正下方，且只在深色下出现。
- **观感**：字号统一成 12 / 14 / 18 / 28 四档；深浅主题切换带 200ms 颜色渐变；深色下语言下拉不再是白底；标题栏与图标按钮补齐 hover / 按下态。
- **功能**：勾选世界改成双向绑定，修掉「点哪都只勾第一个」；刷新带淡入 + 图标旋转 + 状态行时间戳；新增字体选择、检查更新、前往更新与更新日志三个出口；关于卡链接换成 GitHub / 哔哩哔哩图标按钮。
- **资源**：新增多尺寸 `Assets/app.ico`（16–256）；主题缩略图按 65° 斜切渲染。

## [0.1.0-beta.3] - 2026-11-28（Windows 端）

搜索框现在真的浮在世界卡片上：世界列表的 `ScrollViewer` 去掉了 66 px 的上边距，卡片从标题栏正下方开始，滚动时从胶囊底下穿过。设置页一行之内的卡片不再被拉齐到同一高度 —— 五张卡都改成 `VerticalAlignment="Top"`，语言卡下面那块空白随之消失。

关于卡把 GitHub / bilibili 两个小按钮并到了「发布者 / 许可证」那一行的行尾，不再另起一行；bilibili 不再借用现成图标，改成自绘的电视机轮廓（两根天线 + 圆角屏幕）。

「跟随系统」缩略图的分割线改成过图像中心、斜率 65° 的陡缝，与手机版一致（之前两版分别是自然角对角、以及 65° 但没过中心）。
## [0.1.0-beta.2] - 2026-11-28（Windows 端）

- 版本号沿 `0.1.0-beta.*` 递增，安装包与 zip 文件名换成 `0.1.0-beta.2`。
- **窗口**：`WindowStyle=None` 下用 DWM（`DWMWA_WINDOW_CORNER_PREFERENCE`）把窗口做成圆角，根内容用 `Border CornerRadius=12` 裁切；标题栏仍是自绘 44px，右侧三键保留 hover / 按下态。
- **世界页**：删掉「选择世界」标题行；搜索框改成右上角悬浮 pill（`Panel.ZIndex=3`），卡片从标题栏正下方开始滚动、直接从 pill 底下穿过，不再单独占一行；世界卡高度固定 104px，标题单行省略，勾选框只在卡片右上角。
- **设置页**：改成 `WrapPanel` 流式换行，卡片各自定宽（外观 470 / 语言 300 / 字体 300 / 关于 320 / 更新 320），窗口变宽时一行放更多卡片；语言与字体字段加高到 36px。
- **观感**：字号标尺整体上调为 13 / 15 / 20 / 32；外观卡三张缩略图固定 132×88（3:2）居中显示，「跟随系统」那张改成过图像中心的对角拼接（左上亮、右下暗）。
- **关于卡**：应用图标放大到 40×40，正文收敛成「版本 / 发布者 / 许可证」三行，与手机端写法一致；**删除独立的「键盘快捷键」卡**。
- **功能**：勾选世界双向绑定，修掉「点哪都只勾第一个」；刷新有淡入 + 图标旋转 + 状态行时间戳；新增字体选择（只读系统已安装字体）、检查更新（比对 GitHub 最新 release，无 Release 时给友好提示）、前往更新与更新日志三个出口；关于卡的链接换成 GitHub / 哔哩哔哩图标按钮。
- **资源**：新增多尺寸 `Assets/app.ico`（16–256），窗口与关于卡都带应用图标；主题缩略图由程序自身渲染（`--render-previews`），不是截图拼贴。
### Added

- Windows 安装包：Inno Setup 定义在 `windows/installer/TerraXaleid.iss`（逐用户安装，含简中/英/日三语向导），产物 `dist/TerraXaleid-0.1.0-beta.1-win-x64-setup.exe`（约 4.1 MB），带开始菜单项、可选桌面图标与卸载入口。
- Windows 图形界面（WPF，`windows/TerraXaleid.App`）：世界列表 → 角色列表 → 点亮，直接读写
  `%USERPROFILE%\Documents\My Games\Terraria`，不需要 adb 或 Shizuku。
- 批量点亮：卡片右上角勾号把世界加入批量，`Ctrl+B` 执行；只重写已经存在该世界地图的角色，
  不会为空角色新建地图。世界卡右键菜单提供 打开 / 加入批量 / 在资源管理器中显示。
- 撤销本次：点亮后底栏出现「撤销本次」，删除刚写的地图并把 `.bak` 链逐级前移。
- 恢复与备份页：按「世界 · 角色」列出全部 `.bak`，可恢复、删备份，或删除地图（全黑）让游戏重新生成。
- 深色变体：标准深色 / OLED 全黑（`App.Background #000000`），随配置持久化。
- 键盘操作：`Ctrl+1/2/3` 切页、`Ctrl+B` 批量、`空格` 勾选、`回车` 进角色页、`F5` 重扫，
  所有按钮与卡片都有可见焦点环。

### Changed

- 主题缩略图改由程序自身离屏渲染（`--render-previews <字体> <输出目录>`），不再使用手机截图拼接。
- 浅色主题对比度：卡片描边加深到 `#DCDCD6`，卡片加 7% 柔和阴影，未选中勾号透明度 0.25 → 0.45。

### Notes

- 版本已切到 `0.1.0-beta.1`，安装包为 `dist/TerraXaleid-0.1.0-beta.1-win-x64.zip`（需 .NET 10 桌面运行时）
  与 `dist/TerraXaleid-0.1.0-beta.1-win-x64-standalone.zip`（单文件，免运行时）。

## [0.1.0-rc.16] - 2026-11-28

### Added

- 支持 Terraria 1.4.6：世界存档格式 `326 → 329`，移动版地图 `33094 → 33097`，PC 版地图 `326 → 329`。
  三份实现（Python / C# / Kotlin）都能读写两种版本，旧版本存档照常可用。
- 1.4.6 的世界头部只多了一个 `noSpidersSeed` 标志，`worldSurface` / `rockLayer` 等字段偏移没有变化，
  所以 1.4.5 与 1.4.6 的存档都能直接点亮。

### Notes

- 实测：1.4.6 客户端能正常加载本工具写出的 `33097` 地图（`Map load time = 0.034145`，无异常、不生成 `.bad`）。
- 三语言自检全部通过，1.4.6 与 1.4.5 存档的输出字节数一致。

## [0.1.0-rc.15] - 2026-11-27

### Changed

- 应用显示名统一为 **TerraXaleid**（桌面图标与系统设置里的应用名），五套语言资源一致。
- 桌面端工程与命名空间同步改名：`TerraXaleid.Core` / `TerraXaleid.SelfTest` / `TerraXaleid.Core.Win` /
  `TerraXaleid.Cli`，命令行产物 `terraxaleid-cli`。三语言自检全部通过，输出与改名前一字节不差。
- 文档全面对齐现状：安装包文件名、日志 tag、Android 存档路径（`Worlds` 与 `Players` 分开写清）、
  界面语言与世界搜索的用法、横屏多列规则、三语言实现的分工与遗留项。

### Notes

- `versionCode 44`；功能与上一版一致，仅命名与文档变化。

## [0.1.0-rc.14] - 2026-11-27

### Changed

- 应用标识全部迁到新名字：包名 `io.terramap.android` → `io.terraxaleid.android`，核心库命名空间
  `io.terramap.core` → `io.terraxaleid.core`，配置文件 `terramap.json` → `terraxaleid.json`，
  日志 tag `terramap` → `terraxaleid`，安装包文件名 `TerraXaleid-<版本>.apk`。
- 暂存目录从 `/sdcard/Download/.terramap` 换到应用自己的外部目录；旧目录里上一次留下的
  暂存树仍会被接管，不会丢配置或存档。
- 旧配置文件名（`terramap.json`）在三个查找位置都保留为后备，升级后设置不会重置。

### Notes

- **包名变了等于换了一个应用**：新版本会与旧版并存，请先卸载旧版再安装；卸载会清掉
  Shizuku 对本应用的授权，装好后需要在应用内重新授权一次。
- `versionCode 43`。

## [0.1.0-rc.13] - 2026-11-27

- 项目更名 **TerraXaleid**（原名 terramap，与在线地图服务撞名）：仓库改名并保留旧地址重定向；应用内标识（包名、配置文件、日志 tag）在下一版随包名迁移，届时需卸载重装。

- 镜像里删掉的存档会跟着消失：生成镜像时顺手清掉游戏里已经没有的世界与角色目录，不再拿上一次的残留当现状。
- Shizuku 可用时不再采用上一次留下的暂存目录（那里面可能装着早已删除的存档）。

## [0.1.0-rc.12] - 2026-11-27

- 游戏里把所有角色删光后，软件不再显示残留的角色目录与地图（游戏目录按 `<角色>.plr` 判定，便携目录仍全部保留）。
- 「打开 Shizuku」在 Shizuku 未启动/未安装时点不动的问题：改为按包名取启动入口，失败再退到「应用信息」，都失败时提示「没有找到 Shizuku」。

## [0.1.0-rc.11] - 2026-11-27

### Fixed
- 存档根标签在部分机型上显示为乱码（根因是一处标签被按 GBK 重新解码过）；顺带让未映射的标签
  统一回落到通用名称并打日志，不再把原始串直接显示出来。
- 英文界面里「搜索世界名称」「打开 Shizuku」两句仍显示中文（英文词条被误写成中文）。
- 游戏里已删除的角色仍会出现在角色列表里，还能被写入地图——写入的位置游戏根本不会读。现在凡
  是没有同名 `.plr` 的角色目录一律跳过（便携目录里没有 `.plr` 时保持旧行为）；恢复与备份页同
  样不再列出这些已删除角色的地图。

### Notes
- `versionCode 40`。

## [0.1.0-rc.10] - 2026-11-25

### Fixed
- 部分机型（尤其 Android 16）点「授权 Shizuku」没反应：Shizuku 的授权弹窗属于后台启动 Activity，
  有些系统直接拦掉。现在发起请求后 1.8 秒仍未拿到结果，就自动把 Shizuku 应用切到前台，
  在它的授权列表里打开开关，返回时自动重新检测。
- 世界列表的 Shizuku 卡片新增「打开 Shizuku」按钮，随时可以手动去开关权限。

### Notes
- `versionCode 39`。

## [0.1.0-rc.9] - 2026-11-25

### Fixed
- Android 16 上未给 Shizuku 授权时应用进不去：启动时不再用「所有文件访问 / Shizuku 授权」做硬门槛，
  缺权限时照常进入世界列表，权限说明改成列表里的卡片（带授权、重新检测按钮）。
- 在 Shizuku 应用里授权后读不到 `.wld`：暂存目录从 `/sdcard/Download/.terramap` 改到应用自己的
  `Android/data/io.terraxaleid.android/files/mirror`。旧位置属于 Download，读取它需要「所有文件访问」，
  于是有 Shizuku 却读不到；新位置无需任何存储权限，`shell` 用户（`ext_data_rw` 组）仍可写入。
  旧位置的暂存树仍可被沿用。

### Notes
- `versionCode 38`。

## [0.1.0-rc.8] - 2026-11-24

### Fixed
- 关于卡片里点 GitHub / 哔哩哔哩小按钮会闪退：`LocalContext` 给到的是包装过的配置上下文而不是
  Activity 实例，`startActivity` 必须带 `FLAG_ACTIVITY_NEW_TASK`；同时补上 `SecurityException`
  与 `RuntimeException` 的兜底，失败只弹提示不再崩溃。
- 哔哩哔哩按钮改为 App 内打开：装了就用 `bilibili://space/…` 直达，没装或打不开才回落到浏览器。
  旧代码先用 `getLaunchIntentForPackage` 判断，而 Android 11 起查不到未在 `<queries>` 声明的包，
  所以永远判为「没装」——现在直接尝试显式 Intent，并在清单里声明该包。

### Notes
- `versionCode 36`。

## [0.1.0-rc.7] - 2026-11-24

### Fixed
- 角色选择页标题与说明文字重叠（懒加载列表项的插槽是单框不是列，标题和提示画在了一起）。
- 搜索框圆角太小：改成两端半圆，与卡片、按钮的圆角语言一致。

### Changed
- 卡片栅格的列数判定放宽：宽屏横屏（约 1000dp，如 3168×1440 / 508ppi）排 3 列，
  更宽（≥1240dp）才 4 列，避免把宽屏压成两列。

### Notes
- `versionCode 35`。

## [0.1.0-rc.6] - 2026-11-24

### Added
- 选择世界页顶部加了搜索横幅：按名称包含的字符串过滤，大小写不敏感，命中的片段高亮；
  没有匹配时给出空状态，输入框右侧可一键清空。

### Changed
- 世界卡片与角色卡片改成自适应栅格：竖屏单列，横屏/平板上按可用宽度排 2–4 个，
  不再把一整排卡片拉成超宽的一条。

### Notes
- `versionCode 34`。

## [0.1.0-rc.5] - 2026-11-24

### Changed
- 繁體中文只保留一种：系统语言是 zh-TW、zh-HK 或其他中文变体时都显示同一套繁體中文，
  语言列表里去掉了「繁體中文（香港）」。

### Fixed
- 顶部 ⋮ 菜单与语言列表不再固定在启动时的语言：文案改为在页面自身的组合里先取好再交给弹窗，
  切换语言后立即跟着变。

### Notes
- `versionCode 33`。旧配置里的 `hktc` 会自动当作 `tc` 读取。

## [0.1.0-rc.4-patch.1] - 2026-11-24

### Fixed
- 语言下拉菜单靠右展开、优先向下；下方放不下时才向上翻（rc.4 误改成靠左）。
- 选「跟随系统」不再重建页面：立即按当前系统语言刷新文本，`terramap.json` 仍记 `system`，
  下次启动重新读取系统语言，期间系统语言变过就会跟着变。
- 顶部 ⋮ 菜单与跟随系统相关的文案随之实时同步，不再停留在上一个语言。

### Notes
- `versionCode 32`。

## [0.1.0-rc.4] - 2026-11-24

### Changed
- 切换语言不再重建 Activity：文本就地刷新，停留在当前页面，不再退回世界列表。
- 语言下拉菜单固定靠左展开，横屏竖屏都起始于该行的左边缘，不再贴右边缘或被挤出屏幕。

### Notes
- `versionCode 31`。只修体验问题，语言清单与上一版一致。

## [0.1.0-rc.3] - 2026-11-24

### Added
- 五种显示语言：简体中文、繁體中文（台灣）、繁體中文（香港）、日本語、English。设置页「行为」卡片下面新增
  「语言」卡片，下拉就地展开、当前值右对齐，每种语言用自己的文字显示自己的名字（`language_*`）。
- `tools/gen_strings.py`：以 `values/strings.xml` 为唯一来源生成 `values-en` / `values-ja` /
  `values-zh-rTW` / `values-zh-rHK`，简→繁按词组 + 逐字映射，缺键回退简体并打印键名。
- 世界卡片的难度 / 尺寸 / 邪恶度、存档根名称改为资源化；L1 的 `WorldMeta` 增加
  `difficultyKind` / `sizeKind` / `evilKind` 三个枚举，UI 不再直接读中文串。

### Changed
- `MainActivity` 用 `attachBaseContext` 把选定语言装进 Activity 上下文，弹层菜单、对话框、Snackbar
  与 Compose 树共用同一 locale；语言切换后 `recreate()`，立即整屏生效。
- 进度文案、模板说明、写回失败提示统一走 `Resources.getString`，`RevealJob.reveal` 与
  `Templates.resolve` 因此多了一个 `res` 参数。

### Notes
- `versionCode 30`。功能与 rc.2 一致，只加多语言。
- 搜索框、Windows 改 MaterialDesign、Windows 字体切换都不在本版范围。

## [0.1.0-rc.2] - 2026-11-24

### Added
- 设置页「关于」卡片里的 GitHub 按钮接通仓库地址（`https://github.com/Dragrowl/terramap`），
  与哔哩哔哩按钮共用同一套「没有浏览器就弹 Snackbar」的处理。
- 首个 GitHub Release 随本版发布，仓库 README 顶部加了 release 徽章、新增「使用之前」一节，
  指向 Shizuku 最新 Release。

### Notes
- `versionCode 29`。功能与 rc.1 一致，只是把发布渠道接通。

## [0.1.0-rc.1] - 2026-11-24

### Changed
- 「深色变体」卡片去掉标题，只留两个选项卡。
- 配色整体回到严格的中性灰：所有底色 R=G=B（此前 background / surfaceVariant 带 2–4 点暖色偏移，
  大面积铺开时偏黄）。强调色只保留极淡的灰绿。
- 选项卡选中态改用「卡片底色 + 10% onSurface」合成，不再借用带暖调的 `secondaryContainer`。
  实测 OLED：页面 `(0,0,0)`、卡片 `(11,11,11)`、未选中选项卡底 `(33,33,33)`，色偏全为 0。

### Notes
- `versionCode 28`。预发布版：功能冻结，只修显示问题。

## [0.1.0-beta.3] - 2026-11-24

### Changed
- 「深色变体」改成两个并排、一行高的选项卡（选中实底、未选中描边），副标题与说明文字全部去掉。
- 主题切换的渐变时间从 300 ms 提到 450 ms（`ThemeTransitionMs`），页面切换仍是 300 ms；
  `Scaffold` 显式铺 `background`，页面底色与顶栏同一时刻变。
- 顶栏改成「透明容器 + `terramapTopBarBackground()` 动画底色层」：Material3 的 `TopAppBar`
  会在动画开始前就把 `containerColor` 定死，改前它要等切换结束才跳色。
- 状态栏与导航栏跟着主题走：`MainActivity` 里按当前动画帧的 `background` 同步 window、
  statusBar、navigationBar 底色，并用 `isAppearanceLightStatusBars` 同步图标明暗。
  改前状态栏那条一直是主题 XML 里的静态色，看起来正是「标题栏慢半拍」。
  同一帧实测：状态栏 `(215,215,214)`、顶栏 `(215,215,214)`、页面 `(215,215,214)`、卡片 `(207,207,204)`。
- 右上角下拉菜单三项补上图标（齿轮 / 时钟 / 刷新）。

### Fixed
- OLED 变体的层次：页面背景与顶栏是纯 `#000000`，卡片仍保留 `#0B0B0B` 的微亮填充——
  之前把卡片也压成纯黑，卡片边界消失，整屏看起来像一片低明度灰。
  实测像素：页面留白 `(0,0,0)`、顶栏 `(0,0,0)`、卡片 `(11,11,11)`。

### Notes
- `versionCode 27`。

## [0.1.0-beta.2] - 2026-11-24

### Changed
- 关于不再是二级页面：内容作为设置页最后一张卡直接铺开，`Stage.About`、`AboutScreen`、入口卡与返回分层里的那一级一起删掉。
- 「深色变体」的显示条件从「明确选了深色」改成「**解析后**是深色」——跟随系统且系统当前是深色时同样出现。
- 设置页去掉描述性副标题：「行为」卡只留标题与开关，「深色变体」卡只留选项名（标准深色 / OLED 全黑），两项的说明文字一并移除。
- 清理随之无用的字符串资源 6 条；关于块改用 `ui/AboutBlock.kt`，链接失败时由设置页的 Snackbar 提示。

### Notes
- `versionCode 26`。

## [0.1.0-beta.1] - 2026-11-24

第一个 beta。这一轮的目标是「能用、看着也不像安卓 2」，从 dev.14 到 dev.22 逐轮积累，现在收口。

### 这一轮带来了什么

- **设置页**：外观（三张实机截图缩略图，切换带 300 ms 渐变）、深色变体（标准 / OLED 全黑）、行为（退回桌面时是否结束进程）、关于入口。全部是展开的卡片加副标题，没有二级菜单。
- **关于页**：单卡；软件图标与版本、发布者与许可左对齐，GitHub（暂空、禁用态）与哔哩哔哩两个小图标按钮右对齐。
- **顶栏**：一个下拉菜单装下设置 / 恢复与备份 / 刷新。
- **返回分层**：关于 → 设置 → 角色/恢复 → 世界列表 → 离开应用；离开方式由开关决定，且写图时不会强杀进程。
- **备份与恢复**：覆盖前留 `.map.bak`，可轮换成 `.bak.1/.bak.2`；恢复页支持多选恢复、只删备份、删地图回到全黑。
- **存档层**：Shizuku 直读 `Android/data`、液体层、三语言 L1（python / csharp / kotlin，逐格哈希一致）。

### 已知限制

- 只认移动版 `.map`（fileVersion 33094）；PC 的 318 会明确报不支持。
- 没有 Shizuku 时只能用便携目录；Android 11+ 的 `Android/data` 对第三方应用不可列，这是平台限制。
- 强调色、连按两次返回退出、预测式返回动画都不在这一轮。

### Notes
- `versionCode 25`；`dist/terramap-0.1.0-beta.1.apk`。
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
  查找顺序为 `Android/data/io.terraxaleid.android/files/` → `/sdcard/Download/.terramap/` → 应用私有目录；
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
- **L1 核心（C#）**：`csharp/TerraXaleid.Core`（`net10.0`，零 NuGet）与
  `csharp/TerraXaleid.SelfTest`；`--with-samples` 可对真实存档做字节级校验，
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
