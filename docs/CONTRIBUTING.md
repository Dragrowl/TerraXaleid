# 贡献指南

## 目录约定

| 路径 | 内容 |
|---|---|
| `*.py`（仓库根） | L1 核心的参考实现与其自检 |
| `csharp/` | L1 的 C# 实现（`net10.0`，零第三方依赖） |
| `kotlin/` | L1 的 Kotlin/JVM 实现（同时被 Android 模块直接引用） |
| `android/` | Compose 外壳工程 |
| `scripts/` | 三语言校验脚本 |
| `docs/` | 开发者文档（架构、贡献流程、L1 状态）；使用者只看根 `README.md` |
| `samples/` | 合成样本说明（**不放真实存档**） |

## 改代码前

1. 先跑 `scripts\verify-all.ps1`，确认基线是绿的。
2. 任何对 `.map` / `.wld` 解析规则的改动，必须同时改 Python、C#、Kotlin 三份实现。
3. 调色板（1-based Type、`255*y/surface` 渐变、区块 64×64 列主序）属于硬约定，
   改动前先读 `ARCHITECTURE.md` 的「不变量」一节。

## 提交

- 提交信息用 `type: summary` 形式（`feat:` / `fix:` / `docs:` / `chore:` / `refactor:`）。
- 不要把真实存档（`*.wld` / `*.plr` / `*.map`）、编译器 jar、`local.properties` 提交进仓库。
- 不要在仓库里放构建产物；`csharp/**/bin|obj`、`kotlin/.verify`、`android/**/build` 已被忽略。

## 验证

```powershell
# 全量
powershell -ExecutionPolicy Bypass -File .\scripts\verify-all.ps1

# 只跑某一语言
powershell -ExecutionPolicy Bypass -File .\scripts\verify-dotnet.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\verify-kotlin.ps1
python .\selftest.py
```

真实存档校验（可选，需要自备存档）：

```powershell
powershell -File .\scripts\verify-dotnet.ps1 -WithSamples `
  -WorldPath "C:\path\world.wld" -MapPath "C:\path\template.map"
```

## 许可

提交即表示同意以 GPL-3.0-or-later 授权（见 `LICENSE`）。
