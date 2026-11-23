# 贡献指南

## 先读哪个

| 你想干的事 | 看这个 |
|---|---|
| 只是想用 | 仓库根 [`README.md`](../README.md) |
| 搞懂 `.map` / `.wld` 为什么这么算 | [`ARCHITECTURE.md`](ARCHITECTURE.md) 的不变量一节 |
| 核对三份 L1 实现的状态与踩过的坑 | [`L1-STATUS.md`](L1-STATUS.md) |
| 知道接下来要做什么 | [`ROADMAP-NEXT.md`](ROADMAP-NEXT.md) |

## 目录约定

| 路径 | 内容 |
|---|---|
| `*.py`（仓库根） | L1 参考实现（`worldfmt` / `mapfmt` / `players` / `reveal` / `deploy`）与自检 |
| `csharp/` | L1 的 C# 实现（`net10.0`，零第三方依赖） |
| `kotlin/` | L1 的 Kotlin/JVM 实现（被 Android 模块直接引用） |
| `android/` | Compose 外壳；`app` 是界面，`core` 只把 Kotlin L1 包一层 |
| `scripts/` | 三语言校验、打包 |
| `docs/` | 开发者文档；`docs/assets/` 放项目标记与截图；`docs/shots/` 是截图工程源码副本 |
| `samples/` | 样本说明（**不放真实存档**） |
| `dist/` | 构建产物（git 忽略） |

## 改代码前

1. 先跑 `scripts\verify-all.ps1`，确认基线是绿的。
2. **任何对 `.map` / `.wld` 解析规则的改动，必须同时改 Python、C#、Kotlin 三份实现**，
   并且三份自检都要过。只改一份的提交不接受。
3. 调色板（1-based Type、`255*y/surface` 渐变、区块 64×64 列主序、液体在墙之前）属于硬约定，
   改动前先读 `ARCHITECTURE.md` 的「六条不变量」。
4. Android 侧只做展示与调用：解析逻辑不进 `android/`，一律走 `io.terramap.core`。
5. 设备上的读写只走 `ShizukuShell.exec()`（`sh -c`）。不要引入 `su`、`Runtime.exec`、
   `ProcessBuilder`——这样非 root 设备也能用，root 设备也不会多一条路径。

## 验证

```powershell
# 三语言全量（Python / C# / Kotlin）
powershell -ExecutionPolicy Bypass -File .\scripts\verify-all.ps1

# 单独跑
python .\selftest.py
powershell -File .\scripts\verify-dotnet.ps1
powershell -File .\scripts\verify-kotlin.ps1

# 真实存档校验（可选，需要自备存档）
powershell -File .\scripts\verify-dotnet.ps1 -WithSamples `
  -WorldPath "C:\path\world.wld" -MapPath "C:\path\template.map"
```

Android 构建与打包：

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
powershell -ExecutionPolicy Bypass -File .\scripts\package-apk.ps1     # → dist\terramap-<versionName>.apk
```

需要 `local.properties` 指向 SDK（`sdk.dir=F:/Android/Sdk`），该文件不进仓库。

## 版本号

- 开发期 `versionName` 一律 `0.1.0-dev.N`：每轮改动 +1，`versionCode` 同步 +1。
  不开 `0.1.1-dev.*` 之类的平行序列，也不在文件名上加 `-release` / `-fix` 定语。
- `docs/ROADMAP-NEXT.md` 那一轮做完全部切 `0.1.0-beta.1`。
- `dist/` 的文件名由 `package-apk.ps1` 按 `versionName` 生成，不要手改。

## 文档

- **给使用者看的**：只有仓库根 `README.md` 与 `README_EN.md`（两份内容对齐，改一份就改另一份）。
- **给开发者看的**：`docs/` 下所有东西，入口是 `docs/INDEX.md`。
- 写文档时不要写「未来会」「计划支持」这类话进 README 的功能段；未实现的一律进 `ROADMAP-NEXT.md`。
- 版本徽章、示例路径、命令参数会随版本变化，改代码时顺手核对。

## 提交

- 提交信息 `type(scope): summary`（`feat` / `fix` / `docs` / `chore` / `refactor` / `test`）。
- 一个提交表达一个完整改动；提交前看过 `git status` 与 `git diff --cached`。
- 不要提交：真实存档（`*.wld` / `*.plr` / `*.map`）、`kotlin/.tools`、`local.properties`、
  签名文件与 `keystore.properties`、任何构建产物。
- 不加代理工具署名。

## 许可

提交即表示同意以 GPL-3.0-or-later 授权（见 `LICENSE`）。每个源文件头部保留 `SPDX-License-Identifier`。
