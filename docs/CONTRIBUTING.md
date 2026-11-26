# 贡献指南

这是一个外部协作的仓库：**任何人可以 fork、可以提 Pull Request，但只有维护者能直接 push**。
你的改动请走 fork → 分支 → PR 的流程。

## 先读哪个

| 你想干的事 | 看这个 |
|---|---|
| 只是想用 | 仓库根 [`README.md`](../README.md) |
| 搞懂 `.map` / `.wld` 为什么这么算 | [`ARCHITECTURE.md`](ARCHITECTURE.md) 的不变量一节 |
| 核对三份 L1 实现的状态与踩过的坑 | [`L1-STATUS.md`](L1-STATUS.md) |
| 想找活干 | 仓库的 Issues 列表；维护者不在这里公布后续计划 |

## 目录约定

| 路径 | 内容 |
|---|---|
| `*.py`（仓库根） | L1 参考实现（`worldfmt` / `mapfmt` / `players` / `reveal` / `deploy`）与自检 |
| `csharp/` | L1 的 C# 实现（`net10.0`，零第三方依赖） |
| `kotlin/` | L1 的 Kotlin/JVM 实现（被 Android 模块直接引用） |
| `android/` | Compose 外壳；`app` 是界面，`core` 只把 Kotlin L1 包一层 |
| `scripts/` | 三语言校验、打包、推送辅助 |
| `docs/` | 开发者文档；`docs/assets/` 放项目标记与主题缩略图源图 |
| `samples/` | 样本说明（**不放真实存档**） |

## 改代码前

1. 先跑 `scripts\verify-all.ps1`，确认基线是绿的。
2. **任何对 `.map` / `.wld` 解析规则的改动，必须同时改 Python、C#、Kotlin 三份实现**，
   并且三份自检都要过。只改一份的 PR 不会被合。
3. 调色板（1-based Type、`255*y/surface` 渐变、区块 64×64 列主序、液体在墙之前）属于硬约定，
   改动前先读 `ARCHITECTURE.md` 的「六条不变量」。
4. Android 侧只做展示与调用：解析逻辑不进 `android/`，一律走 `io.terraxaleid.core`。
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
powershell -ExecutionPolicy Bypass -File .\scripts\package-apk.ps1     # → dist\TerraXaleid-<versionName>.apk
```

需要 `local.properties` 指向 SDK（模板见 `android/local.properties.example`），该文件不进仓库。

## 提交 PR

- 从 `main` 拉一个短分支，一个 PR 只做一件事。
- 提交信息 `type(scope): summary`（`feat` / `fix` / `docs` / `chore` / `refactor` / `test`）。
- PR 描述里写清：改了什么、怎么验证的（`verify-all` 的输出）、有没有碰解析规则。
- 不要提交：真实存档（`*.wld` / `*.plr` / `*.map`）、`kotlin/.tools`、`local.properties`、
  签名文件与 `keystore.properties`、任何构建产物。`.gitignore` 已经覆盖这些路径，请别用 `-f` 强加。
- 不要改动发布用的签名配置与版本号段；版本由维护者在发版时统一决定。

## 文档

- **给使用者看的**：只有仓库根的 `README.md` 与 `README_EN.md`（两份内容对齐，改一份就改另一份）。
- **给开发者看的**：`docs/` 下所有内容，入口是 `docs/INDEX.md`。
- 写文档时不要写「未来会」「计划支持」这类话；功能只写已经能用的。

## 许可

提交即表示同意以 GPL-3.0-or-later 授权（见 `LICENSE`）。每个源文件头部保留 `SPDX-License-Identifier`。
