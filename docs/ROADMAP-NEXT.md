# 通往 0.1.0-beta.1 的清单

当前版本 `0.1.0-dev.17`。这一轮的目标是「能用、看着也不像安卓 2」，做完切 `0.1.0-beta.1`。
下面按交付顺序排，每完成一项递增一个 `0.1.0-dev.N`。

状态标记：`[x]` 已完成、`[~]` 部分完成、`[ ]` 未开始。

## 1. 返回逻辑

- [x] 子页面（选角色、恢复与备份）按系统返回**逐级退回上一级**，最终回到世界列表。
  实现：`BackHandler(enabled = stage !is Stage.Worlds) { stage = Stage.Worlds }`。
- [x] 世界列表（主页）按一次返回直接退出应用（系统默认行为，不弹「再按一次」）。
- [ ] 退回桌面时是否结束进程，由设置页开关控制（默认关闭）：
  - 关闭 → `moveTaskToBack(true)`，进程留在后台
  - 开启 → `finishAndRemoveTask()`，进程结束
- [ ] （可选，另开一轮）连按两次返回退出 + 预测式返回动画。后者需要把命令式的
  `stage` 改由 `SeekableTransitionState` 持有，并升 `androidx.compose.animation` 到 1.7+。

## 2. 顶栏与设置

- [ ] 右上角改成**下拉菜单**，把「设置 / 恢复与备份 / 刷新」收进去
      （现在是两个并排图标：时钟、刷新）。
- [ ] 设置页：展开的卡片列表，每张卡带副标题，不做二级菜单、不做下拉框。
- [ ] 唯一的开关：「退回桌面时自动结束进程」。
- [ ] 设置入口从顶栏下拉进入；返回键回到世界列表。

### 主题卡片

- [x] 主题数据与配色：`ui/Theme.kt` 里浅色 / 深色 / OLED 三套配色，石墨灰（饱和度 2–4%）。
- [x] 主题选择目前由配置文件驱动：`/sdcard/Android/data/io.terramap.android/files/terramap.json`
      `{"theme":"light"|"dark"|"auto","darkVariant":"standard"|"oled"}`，`ON_RESUME` 时重读。
- [ ] 设置页里的三张主题卡片（跟随系统 / 浅色 / 深色），缩略图用位图。
- [ ] 切换**实时生效并带渐变**（约 300 ms，与页面切换同一套 emphasized 曲线）。
- [ ] 选深色后追加「标准深色 / OLED 全黑」两个文字选项（不给缩略图）。
- [ ] 强调色：本轮不做。

### 关于卡片

- [ ] 左上角软件图标；右侧两行：软件名、版本号。
- [ ] 图标下方右对齐：发布者 `Dragr0wl`，再一行 `GPL-3.0-or-later`。
- [ ] 两个并排小按钮：GitHub 项目地址（**先留空、点击无反应**）、
      B 站主页 <https://space.bilibili.com/3461573882415902>（点击跳转）。
- [x] 图标资源：`res/drawable/ic_github.xml`、`ic_bilibili.xml`（simple-icons 轮廓）。
- [ ] 图标颜色先做成跟随主题的不同明度灰。

## 3. 主题缩略图：用实机截图，不手绘

- [x] 确定性进入浅色 / 深色（配置文件 + `ON_RESUME` 重读）。
- [x] 截图专用工程（**不进本仓库**，位于 `G:\terramap-shots\`）：独立 `io.terramap.shots` 应用，
      硬编码字体、文本全部预先写死的随机串、`allWarningsAsErrors` + `lint.warningsAsErrors`、
      不读任何文件、不申请权限，主题只由 `--ei theme 0|1` 决定。
- [x] 隐藏系统栏（沉浸式），所以屏幕第一行像素就是标题栏顶部；3:2 裁切从 `y=0` 开始。
- [x] 产出：`docs/assets/shots/{light,dark}-3x2.png` 与合成图 `auto-3x2.png`。
- [x] 3:2 画幅下的切线落点：`w=1440,h=960` 时从顶边 `x≈944`、底边 `x≈496`
      （65° 的陡线在宽画幅里穿上下边，不是左右边；按正方形套 `tan` 会算出界）。
- [ ] 把三张位图接进设置页的主题卡片，并删掉占位矢量
      （`theme_thumb_{auto,light,dark}.xml` 只用于占位）。
- [ ] 需要重截时：`G:\terramap-shots\` 重新构建签名安装，`--ei theme 0` / `1` 各截一张，
      再按上面参数合成。

## 4. 图标里的萨卡兹语文本

- [x] 字体：`res/font/endfield_by_butan.ttf`，来自
      <https://github.com/lhclbt/Endfield_Font>（`EndfieldByButan.ttf`，14,496 B）。
- [ ] 应用图标里渲染的那行文字换成这个字体。
- [ ] 文本由「随机长度、随机字符」生成，**固定单行**，不换行、不超框。

## 已就绪的资产

| 文件 | 说明 | 用途 |
| --- | --- | --- |
| `res/font/endfield_by_butan.ttf` | 萨卡兹语字体 | 图标文字（待接） |
| `res/drawable/ic_github.xml` | GitHub mark，24×24 视口 | 关于页（待接） |
| `res/drawable/ic_bilibili.xml` | Bilibili mark，24×24 视口 | 关于页（待接） |
| `docs/assets/shots/light-3x2.png` | 浅色 1440×960 实拍 | 主题卡片（待接） |
| `docs/assets/shots/dark-3x2.png` | 深色 1440×960 实拍 | 主题卡片（待接） |
| `docs/assets/shots/auto-3x2.png` | 65° 斜切合成 | 跟随系统那张（待接） |
| `res/drawable/theme_thumb_*.xml` | 108×108 占位矢量 | 接线后删除 |

## 完成后的动作

1. `scripts\verify-all.ps1` 全绿。
2. `scripts\package-apk.ps1` 出 `dist\terramap-0.1.0-beta.1.apk`（`versionName` 改成 `0.1.0-beta.1`）。
3. `CHANGELOG.md` 收口，tag `v0.1.0-beta.1`。
4. 更新 README 的版本徽章与「接下来做什么」一节。
