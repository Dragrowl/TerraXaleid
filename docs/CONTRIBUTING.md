# 参与开发

这个仓库对外接受改动：**你可以随时 fork、开分支、提 Pull Request**；只有维护者能直接往 `main` push。
PR 不要求完美，说清楚你想解决什么就够了，剩下的可以一起改。

## 从哪里开始

| 你想做什么 | 先看 |
|---|---|
| 只是想用这个工具 | 仓库根 [`README.md`](../README.md) |
| 想搞懂 `.map` / `.wld` 为什么这么算 | [`ARCHITECTURE.md`](ARCHITECTURE.md) |
| 想确认三份核心实现的进度 | [`L1-STATUS.md`](L1-STATUS.md) |

手上有活想认领，直接开 issue 说一声；没有明确任务也可以先提一个小改动试试水。

## 仓库结构

| 路径 | 内容 |
|---|---|
| `*.py`（仓库根） | 核心格式的 Python 实现与自检，读起来最直接，适合先看这里 |
| `csharp/` | 同一套逻辑的 C# 实现（`net10.0`，零第三方依赖），Windows 端在用 |
| `kotlin/` | 同一套逻辑的 Kotlin/JVM 实现，Android 模块直接引用 |
| `android/` | Compose 界面；`app` 是界面与文件读写，`core` 只把 Kotlin 核心包一层 |
| `windows/` | 命令行工具（`terraxaleid-cli`）与存档定位 |
| `scripts/` | 校验、打包、发布辅助脚本 |
| `docs/` | 开发者文档；`docs/assets/` 放项目标记与主题缩略图源图 |
| `samples/` | 样本说明（**不放真实存档**） |

## 动手之前

```powershell
# 先确认基线是绿的，省得后面分不清是自己改坏的还是本来就坏
powershell -ExecutionPolicy Bypass -File .\scripts\verify-all.ps1
```

建议先看这三件事，能避开大部分返工：

1. **格式逻辑有三份实现（Python / C# / Kotlin）**，用同一批样本互相校验。
   改解析规则时尽量一起改；如果只来得及改一份，在 PR 里说一声，维护者会补另外两份，
   不会因此拒掉你的贡献。
2. **调色板与坐标约定**（Type 从 1 开始、天空渐变按 `255*y/surface`、区块 64×64 列主序、
   液体优先于墙）是和游戏本身对齐的，不是我们的偏好。改之前建议先读 `ARCHITECTURE.md`，
   或者直接问——这些地方一旦算偏，症状是「进游戏一片黑」，很难从截图反推。
3. **Android 侧只做展示与读写**：解析逻辑放在 `kotlin/`，界面层不要自己算。
   设备读写统一走 `ShizukuShell`，这样非 root 设备也能用。

## 验证

```powershell
# 三份实现全量自检
powershell -ExecutionPolicy Bypass -File .\scripts\verify-all.ps1

# 也可以单独跑
python .\selftest.py
powershell -File .\scripts\verify-dotnet.ps1
powershell -File .\scripts\verify-kotlin.ps1
```

手上有真实存档想顺手验一下（不必提交进仓库）：

```powershell
powershell -File .\scripts\verify-dotnet.ps1 -WithSamples `
  -WorldPath "C:\path\world.wld" -MapPath "C:\path\template.map"
```

Android：

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
powershell -ExecutionPolicy Bypass -File .\scripts\package-apk.ps1   # → dist\TerraXaleid-<versionName>.apk
```

需要 `local.properties` 指向 SDK（模板见 `android/local.properties.example`），这个文件不入库。

## 提 PR

- 从 `main` 拉分支即可，不需要遵守什么命名规范。
- 提交信息建议 `type(scope): summary`（`feat` / `fix` / `docs` / `chore` / `refactor` / `test`），
  但更重要的是让人看懂改了什么。
- PR 描述里写：改了什么、怎么验证的（贴 `verify-all` 的输出就行）、有没有碰格式解析。
- 下面这些文件请不要进仓库：真实存档（`*.wld` / `*.plr` / `*.map`）、`kotlin/.tools`、
  `local.properties`、签名文件与 `keystore.properties`、构建产物。`.gitignore` 已覆盖，
  如果确实被忽略了又非提不可，在 PR 里说明原因。
- 发布用的签名配置与版本号由维护者统一处理，你不需要动它们。

## 文档

- 面向使用者的只有根目录的 `README.md` 与 `README_EN.md`，两份内容保持一致。
- 其余都在 `docs/`，入口是 `docs/INDEX.md`。
- 写功能说明时以「现在能做什么」为准；还没落地的想法写在 issue 里就好。

## 许可

提交即表示同意以 GPL-3.0-or-later 授权（见 `LICENSE`）。源文件头部的
`SPDX-License-Identifier` 保留即可。
