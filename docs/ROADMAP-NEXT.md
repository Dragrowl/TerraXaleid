# 通往 0.1.0-beta.1 的清单

当前版本 `0.1.0-dev.22`。这一轮的目标是「能用、看着也不像安卓 2」，下面这些已完成，剩下只有收口动作。
每完成一项递增一个 `0.1.0-dev.N`；版本号的规矩见 `CHANGELOG.md` 顶部的「版本号约定」。

状态标记：`[x]` 已完成、`[~]` 部分完成、`[ ]` 未开始。

## 1. 返回逻辑

- [x] 子页面按系统返回**逐级退回上一级**，最终回到世界列表。
  实现：`BackHandler(enabled = stage !is Stage.Worlds)`，关于 → 设置 → 世界列表。
- [x] 世界列表（主页）按返回离开应用，行为由设置页开关决定（默认「留在后台」）。
- [x] 开关「退回桌面时结束进程」：
  - 关闭 → `moveTaskToBack(true)`，进程留在后台，再进来是原任务
  - 开启 → `finishAndRemoveTask()` + `Process.killProcess()`；正在写图时不允许杀，退回后台
- [ ] （可选，另开一轮）连按两次返回退出 + 预测式返回动画。后者要把命令式 `stage` 改由
      `SeekableTransitionState` 持有，并把 `androidx.compose.animation` 升到 1.7+。

## 2. 顶栏与设置

- [x] 右上角是**下拉菜单**：设置 / 恢复与备份 / 刷新。
- [x] 设置页：展开的卡片列表，每张卡带副标题，没有二级菜单、没有下拉框。
- [x] 唯一的开关：「退回桌面时结束进程」。

### 主题卡片

- [x] 主题数据与配色：`ui/Theme.kt` 里浅色 / 深色 / OLED 三套，石墨灰（色相与饱和度 2–4%）。
- [x] 三张主题卡片（跟随系统 / 浅色 / 深色），缩略图是**实机截图**（`res/drawable-nodpi/theme_preview_*.png`）。
- [x] 切换实时生效并带渐变：20 个颜色槽各做 300 ms `animateColorAsState`，与页面切换同一套 emphasized 曲线。
- [x] 选中「深色」后追加「标准深色 / OLED 全黑」两个文字选项（不给缩略图）。
- [x] `ThemeMode.Auto` 只决定明暗，不再取系统动态色，否则缩略图与结果对不上。
- [ ] 强调色：本轮不做。

### 关于块（设置页底部，不单独成页）

- [x] 设置页最后一张卡：左上软件图标，右侧「terramap / 版本」两行。
- [x] 下一行左侧「发布者 Dragr0wl」「GPL-3.0-or-later」，右侧两个小图标按钮（不单独成卡、无副标题）。
- [x] GitHub 按钮先留空：画成 38% 透明禁用态，点击无反应。
- [x] 哔哩哔哩按钮打开 <https://space.bilibili.com/3461573882415902>；没有浏览器时弹 Snackbar。
- [x] 图标颜色跟随主题的不同明度灰（`onSurfaceVariant`）。

## 3. 主题缩略图：用实机截图，不手绘

- [x] 确定性进入浅色 / 深色（配置文件 + `ON_RESUME` 重读）。
- [x] 截图专用工程（**不进本仓库**，位于 `G:\terramap-shots\`）：独立 `io.terramap.shots` 应用，
      硬编码字体、文本全部预先写死的随机串、`allWarningsAsErrors` + `lint.warningsAsErrors`、
      不读任何文件、不申请权限，主题只由 `--ei theme 0|1` 决定。
- [x] 隐藏系统栏（沉浸式），所以屏幕第一行像素就是标题栏顶部；3:2 裁切从 `y=0` 开始。
- [x] 产出并入库：`docs/assets/shots/{light,dark}-3x2.png` 与合成图 `auto-3x2.png`；
      缩到 720×480 后进 `res/drawable-nodpi/` 供设置页使用。
- [x] 3:2 画幅下的切线落点：`w=1440,h=960` 时顶边 `x≈944`、底边 `x≈496`
      （65° 的陡线在宽画幅里穿上下边；按正方形套 `tan` 会算出界）。
- [x] 需要重截时：重建 `G:\terramap-shots\`，`--ei theme 0` / `1` 各截一张，再按上面参数合成。

## 4. 萨卡兹语字体（仅用于缩略图）

- [x] 字体 `EndfieldByButan.ttf`（<https://github.com/lhclbt/Endfield_Font>，14,496 B）只用来渲染那三张缩略图。
- [x] 字体文件、截图工程源码、占位矢量 `theme_thumb_*.xml` 全部从仓库移除（dev.22）。
      现在仓库里只有 `docs/assets/shots/*.png` 与 `res/drawable-nodpi/theme_preview_*.png` 这几张位图带有该字形。
- [ ] 应用内不再有任何地方渲染该字体；将来做 Windows 版需要重新取字体时再按上面链接下载。

## 资产现状

| 文件 | 说明 | 用途 |
| --- | --- | --- |
| `res/drawable/ic_github.xml` | GitHub mark，24×24 视口 | 关于页按钮（已接） |
| `res/drawable/ic_bilibili.xml` | Bilibili mark，24×24 视口 | 关于页按钮（已接） |
| `docs/assets/shots/light-3x2.png` | 浅色 1440×960 实拍 | 缩略图源 |
| `docs/assets/shots/dark-3x2.png` | 深色 1440×960 实拍 | 缩略图源 |
| `docs/assets/shots/auto-3x2.png` | 65° 斜切合成 | 跟随系统那张的源 |
| `res/drawable-nodpi/theme_preview_*.png` | 720×480 缩略图 | 设置页主题卡片（已接） |

## 完成后的动作

1. `scripts\verify-all.ps1` 全绿。
2. `scripts\package-apk.ps1` 出 `dist\terramap-0.1.0-beta.1.apk`（`versionName` 改成 `0.1.0-beta.1`，`versionCode` 加一）。
3. `CHANGELOG.md` 收口，tag `v0.1.0-beta.1`。
4. 更新 README 的版本徽章与「接下来做什么」一节。
