# Changelog

本项目遵循 [SemVer](https://semver.org/lang/zh-CN/)；`0.x` 阶段不承诺接口稳定。
格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)。

## [0.1.0-dev.1] - 2026-11-23

首个开发版：把「读世界 → 算地图 → 写地图」的核心逻辑固化成三语言实现，
并提供 Android 外壳。**不含**任何打包产物，只保留可复现的核心读取/计算/写入。

### Added

- **L1 核心（Python）**：`worldfmt.py`（`.wld` 头部与 tile 流解析）、`mapfmt.py`
  （`.map` 头部、1-based 调色板、64×64 区块 zlib 读写）、`players.py`（世界 GUID ⇄
  角色地图文件名）、`reveal.py`（CLI，默认全图覆盖点亮）、`deploy.py`（adb 推送）、
  `selftest.py`（合成样本 + 真实样本自检）。
- **L1 核心（C#）**：`csharp/TerraMap.Core`（`net10.0`，零 NuGet）与
  `csharp/TerraMap.SelfTest`；`--with-samples` 可对真实存档做字节级校验，
  输出与 Python 黄金样本 SHA-256 完全一致。
- **L1 核心（Kotlin）**：`kotlin/src/main/kotlin/io/terramap/core`，纯 JVM 实现，
  自检用普通 `main()`，不依赖测试框架。
- **Android 外壳**：`android/`（AGP + Compose Material 3），`core` 模块直接引用
  `kotlin/src/main/kotlin`，不复制源码；首屏显示 ABI/SDK/机型并跑一次核心自检。
- **校验脚本**：`scripts/verify-dotnet.ps1`、`scripts/verify-kotlin.ps1`、
  `scripts/verify-all.ps1`；工具链缺失时退出码 2 并打印 `toolchain: unavailable`。
- 文档：`README.md`（使用者入口）、`docs/ARCHITECTURE.md`（三层架构与 L1 契约）、
  `docs/CONTRIBUTING.md`（改动与验证流程）、`docs/L1-STATUS.md`（三语言状态与已知坑）。

### Notes

- 只识别移动版（1.4.5.8，`.map` fileVersion 33094）；PC/Steam 的 318 格式会被明确拒绝。
- 与 tModLoader 无关；不挂载、不注入游戏进程；只读写存档文件。
- 不兼容国服心动版（`con.xd.terraria`）。
