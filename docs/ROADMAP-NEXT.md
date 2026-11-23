# 下一步（0.1.1-dev.x）计划

这一版的目标是「能用起来看着也不像安卓 2」。下面按交付顺序排，每完成一项递增一个 dev 号。

## 1. 返回逻辑（基础）

- 所有子页面按返回都**逐级退回上一级**，不是回到「上次停留的页面位置」。
- 最终汇合到世界列表（主页）。
- 主页按一次返回**直接退回桌面**（不弹「再按一次」）。
- 是否在退回桌面时结束进程，由设置页的开关决定，默认关闭。
  - 关闭：`moveTaskToBack(true)`，进程留在后台。
  - 开启：`finish()` / `finishAndRemoveTask()`，进程结束。

## 2. 顶栏与设置

- 右上角改成一个**下拉菜单**，把「设置 / 备份（恢复与备份）/ 刷新」收进去。
- 设置页、关于页都做成**展开的卡片列表**（每张卡片带副标题），不做二级菜单、不做下拉框。
- 唯一的开关是「退回桌面时自动结束进程」。

### 主题卡片

- 三张卡片：跟随系统 / 浅色 / 深色，缩略图用 `res/drawable/theme_thumb_{auto,light,dark}.xml`
  （已有；跟随系统那张是 65° 过中点的斜切）。
- 切换**实时生效并带渐变**（约 300 ms，与页面切换同一套 emphasized 曲线）。
- 强调色暂不做。
- 选深色后追加两个选项：**标准深色 / OLED 全黑**，不提供缩略图，纯文字卡片。

### 关于卡片

- 左上角软件图标；图标右侧两行：软件名、版本号。
- 图标下方右对齐一行：发布者 `Dragr0wl`。
- 再一行：`GPL-3.0-or-later`。
- 两个并排的小按钮：GitHub 项目地址（**暂时留空、点击无反应**）、B 站主页
  <https://space.bilibili.com/3461573882415902>（点击跳转）。
- 图标用 `res/drawable/ic_github.xml`、`ic_bilibili.xml`（simple-icons 轮廓，已有），
  颜色先做成跟随主题的不同明度灰，后续再接强调色。

## 3. 主题缩略图要用实机截图生成（不是手画矢量）

正确的做法：不手绘缩略图，改用**实机渲染 + 截图**，因为缩略图要看起来就像应用本身。

1. 先能确定性地进入浅色/深色 —— 已实现：`/sdcard/Android/data/io.terramap.android/files/terramap.json`
   里 `{"theme":"light"|"dark"|"auto","darkVariant":"standard"|"oled"}`，`ON_RESUME` 时重读。
2. 做一个**只用于截图**的模式：选择世界页的**所有文本（含标题栏）**都换成同一批随机字符串，
   字体换成萨卡兹语；随机串的长度随机、字符随机，但**深色与浅色两次必须用完全相同的那一批**，
   这样两张图只差配色。
3. 分别截浅色、深色两张同尺寸图。
4. 合成缩略图：以画布中点为基点，切线从 **+x 方向逆时针转 65°**（方向 `(cos65, -sin65)`），
   左半取浅色截图、右半取深色截图。
5. 现有的 `theme_thumb_*.xml` 只是占位，最终要换成上面生成的位图（`drawable-nodpi` 或按密度切图）。

## 4. 图标里的萨卡兹语文本

- 字体：`res/font/endfield_by_butan.ttf`，来自
  <https://github.com/lhclbt/Endfield_Font>（`EndfieldByButan.ttf`）。
- 用途：mipmap 里渲染的那行文字改用这个字体。
- 文本由「随机长度、随机字符」的生成器产出，**固定单行**，不换行、不超框。

## 已就绪的资产

| 文件 | 说明 |
| --- | --- |
| `res/font/endfield_by_butan.ttf` | 萨卡兹语字体 14,496 B |
| `res/drawable/ic_github.xml` | GitHub mark，24×24 视口 |
| `res/drawable/ic_bilibili.xml` | Bilibili mark，24×24 视口 |
| `res/drawable/theme_thumb_light.xml` | 浅色缩略图 108×108 |
| `res/drawable/theme_thumb_dark.xml` | 深色缩略图 108×108 |
| `res/drawable/theme_thumb_auto.xml` | 跟随系统缩略图，65° 过中点斜切，两半各自裁剪内容 |
