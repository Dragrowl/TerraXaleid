# 通往 0.1.0 的清单

当前版本 `0.1.0-rc.1`（预发布，功能冻结、只修显示与稳定性）。Android 侧这一轮的工作已经收口，
接下来的主线是 **Windows 桌面版（WinUI 3）——绝赞开发中**。

状态标记：`[x]` 已完成、`[~]` 进行中、`[ ]` 未开始。

## 1. Windows 桌面版（主线）

目标形态：单文件便携 exe，双击就开，不需要装 .NET 运行时之外的东西（若必须带 dll 才退回 portable 目录再退 setup）。
界面用 WinUI 3，配色与 Android 版共享同一套中性灰调色板。

- [~] **L2 存档发现**：按顺序探测 Steam / GOG / 便携目录 / 用户手动指定；列表字段与 Android 一致
      （名字、小/中/大/巨大、腐化或猩红、普通/专家/大师/传奇、困难模式、月总后）。
- [ ] **L3 界面**：世界列表 → 角色多选 → 点亮 → 结果与撤销，与 Android 同构。
- [ ] **预览**：点亮前给一张缩略图（直接用 L1 算出的 grid 渲染，不写真文件）。
- [ ] **备份与恢复**：沿用 `.map.bak` / `.bak.1` 轮换，界面里可一键撤销。
- [ ] **配置文件**：`terramap.json` 放在程序同级目录（便携原则），字段与 Android 版共用。
- [ ] 打包与签名：单文件发布 + 版本信息写入 exe 属性。

依赖：C# L1（`csharp/`，已完成并通过 `verify-dotnet.ps1`）→ WinUI 3 外壳。

## 2. PC 存档格式（fileVersion 318）

Windows 版要直接吃 Steam 存档，必须先解决这一条。

- [ ] 读 `fileVersion 318` 的 `.map`（带 RLE 的条目编码）。
- [ ] 写回同一版本，保证游戏读得进去。
- [ ] 用本机 `Documents\My Games\Terraria` 下的真实存档做回归，加入 `--with-samples` 用例。

## 3. Android 细节（不阻塞发布）

- [ ] 连按两次返回退出（现为一次返回离开应用）。
- [ ] 预测式返回动画：需要 `androidx.compose.animation` 1.7+ 与 `SeekableTransitionState`
      持有当前页面，工作量约 80–120 行。
- [ ] 强调色（Material You 风格的自选主色）：本轮不做。
- [ ] 多语言：目前界面文案只有中文。

## 4. 已完成（`0.1.0-rc.1` 之前）

- [x] 三语言 L1（Python / C# / Kotlin），逐格 `decoded-grid sha256` 一致。
- [x] `.wld` 解析：段表、世界信息、tile 流列优先 + RLE 不跨列、液体存量。
- [x] `.map` 读写：64×64 区块、块内列主序、`int32` 前缀 + zlib、`length <= 0` 为空块。
- [x] 调色板：方块 / 液体（> 32）/ 墙 / 深度背景，优先级与 `CreateMapTile` 一致。
- [x] 「世界 × 角色」地图：默认写全部角色，`--player` 可指定、`--list` 只读列名。
- [x] Android：Shizuku 直读 `Android/data`、便携目录回退、备份轮换、恢复与删除全黑。
- [x] Android 界面：Material 3、共享轴页面切换、顶栏下拉、设置页（外观/行为/关于）、
      主题渐变 450 ms 且顶栏与系统栏同步。
- [x] 截图工程：独立于仓库的 Compose 工程产出主题缩略图（字体只用于这一步，不进仓库）。
- [x] 打包：`scripts/package-apk.ps1` 从 `build.gradle.kts` 读版本号、签名、打印 size 与 SHA-256。
