# GitHub 发布信息

本文件只是**给人复制粘贴用的仓库元信息**，不参与构建。

## 仓库名与描述

- Repository name：`terramap`
- Description（GitHub 仓库描述，一句话，建议 ≤ 120 字符）：

```
Terraria world map revealer: reads .wld, writes a valid .map so the in-game map is fully revealed — without touching a single tile.
```

- 中文描述（用于中文社区页面或 Issue 模板）：

```
Terraria 地图全点亮工具：读 .wld、算出每格该显示什么、写成合法的 .map —— 打开游戏地图整张亮起，不动一个物块。
```

## Topics（GitHub 仓库标签）

```
terraria
terraria-modding
terraria-map
save-editor
world-map
kotlin
android
jetpack-compose
material-design
csharp
dotnet
python
reverse-engineering
binary-format
open-source
gplv3
```

## 首次推送

远程仓库还没有配置（本机无 `origin`）。在 GitHub 上建好空仓库后：

```powershell
cd G:\terramap
git remote add origin https://github.com/<你的账号>/terramap.git
git push -u origin main
git push origin --tags
```

## 发布（Release）正文模板

标题：`<versionName>`（例如 `0.1.0`）

```markdown
**Android 预发布版**：`terramap-<versionName>.apk`

- 世界列表直接读游戏目录（Shizuku），选世界 → 选角色 → 点亮
- 写回前自动备份为 `.map.bak`，结果页可一键撤销
- 恢复与备份：恢复 / 只删备份 / 删成完全未探索的全黑
- Material 3 界面，主题（跟随系统 / 浅色 / 深色 / OLED 全黑）切换带渐变
- 液体（水 / 岩浆 / 蜂蜜 / 微光）按真实优先级上色，不再留黑区

> Windows 桌面版（WinUI 3）绝赞开发中，本轮不提供。
> 功能冻结，只修显示与稳定性问题。

校验：`sha256 <构建产物算出来的值>`
```

## 推送前自检

- [x] 无历史存档：`.gitignore` 覆盖 `*.wld` / `*.plr` / `*.map` / `*.map.bak` / `*.map.bad`
- [x] 无签名材料：`android/keystore/`、`android/keystore.properties`、`*.jks`、`*.keystore`
- [x] 无本机路径：`android/local.properties` 已忽略（模板见 `android/local.properties.example`）
- [x] 无构建产物：`dist/`、`**/build/`、`**/obj/`、`kotlin/.tools/`、`kotlin/.verify/`
- [x] 无截图噪声：根目录与 `android/` 下的调试 `*.png` 已忽略（`docs/assets/` 与 `res/` 里的例外保留）
- [x] 许可证与声明：`LICENSE`（GPL-3.0-or-later）、README 里三条声明、每个源文件带 SPDX 头
- [x] 提交身份：`Dragr0wl <dragr0wl67@gmail.com>`
