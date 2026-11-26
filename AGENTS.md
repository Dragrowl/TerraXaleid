# TerraXaleid — 给 AI 代理的仓库约定

> 产品名是 **TerraXaleid**（原名 terramap，与在线地图服务撞名后改名）。代码标识已全部迁移：包名 `io.terraxaleid.*`、配置文件 `terraxaleid.json`、日志 tag `terraxaleid`；旧的 `terramap.*` 只在 CHANGELOG 的历史条目里出现。

Terraria 存档地图读写工具：输入 `.wld` 字节、输出 `.map` 字节，不碰游戏进程。
当前版本 `0.1.0-rc.15`，版本号规则见文末。

使用者看 [`README.md`](README.md)；人类开发者先看 [`docs/INDEX.md`](docs/INDEX.md)。

## 代码边界

- 根 `*.py`：L1 参考实现，也是三语言的「基准答案」。
  - `worldfmt.py` 只读 `.wld`（`load` 解析全量 tile 与液体，`read_meta` 只走头部，`read_layers` 按固定偏移取 surface/rock）。
  - `mapfmt.py` 只碰 `.map`（头部、调色板基址、区块读写）。
  - `players.py` 只负责「世界 GUID ⇄ 角色地图文件名」与角色选择。
  - `reveal.py` 是 CLI，`deploy.py` 推设备；两者不做解析。
- `csharp/TerraXaleid.Core`：L1 的 C# 实现（`net10.0`，**零 NuGet**）；`TerraXaleid.SelfTest` 是自检（普通 `main`，不引测试框架）。
- `kotlin/src/main/kotlin/io/terraxaleid/core`：L1 的 Kotlin 实现；`android/core` 用
  `srcDir("../../kotlin/src/main/kotlin")` **直接引用**，不复制源码。
- `android/app`：Compose 外壳，只做展示与调用。
  - `data/ShizukuShell.kt` 是**唯一**的 shell 出口（`Shizuku.newProcess` 反射调用，命令走 `sh -c`）。
  - `data/TerraMirror.kt` 负责镜像、写回、属主/权限还原；`data/RevealJob.kt` 编排点亮流程；
    `data/Backups.kt` 负责备份的扫描/恢复/删除；`ui/` 只放界面。
- L1 里**禁止**出现 `File` / `Directory` / `Environment` / 时钟读取：L1 只吃字节、吐字节；路径与权限属于 L2。
- Android 里**禁止**出现 `su` / `Runtime.exec` / `ProcessBuilder`：非 root 设备也要能用。

## 改动的硬约定

改任何解析规则，必须同步三份实现（Python / C# / Kotlin），改完 `scripts\verify-all.ps1` 必须全绿。
以下不变量写错会静默出错图：

1. `Type` 是 **1-based** 调色板索引，`tile` 段起点是 `1`。
2. 天空渐变 = `(int)(255.0 * y / worldSurface) & 0xFF`，不是 `min(255, y - rockLayer)`。
3. 区块 64×64，块内**列主序**：`x = cx*64 + n%64`、`y = cy*64 + n/64`。
4. 块前缀 `int32 length <= 0` 表示整块空，是语义的一部分，不能省。
5. `.wld` tile 流 RLE **不跨列**，每列严格 `H` 格，覆盖格数 = `run + 1`。
6. `worldSurface` / `rockLayer` 按固定偏移读，不要用「扫描猜两个浮点」的启发式。
7. 每格优先级：方块 > 液体（存量 > 32）> 墙 > 深度背景；液体四槽 water / lava / honey / shimmer。
8. `.map` 的 option 位图字节数 = `ceil(maxId / 8)`，按位读取时每 8 位换新字节。

## 设备写入

- 写回前备份原文件为 `.map.bak`，再覆盖时旧备份让位成 `.bak.1`、`.bak.2`（上限 2 份）。
- 写回后按**原文件**的 `uid:gid` 与 `mode` 还原；原文件不存在时用父目录属主，`chmod` 默认 `660`。
- 判断成功看 `cp` 退出码；字节数只作诊断（部分设备 `stat` 读回值不一致，曾因此误报失败）。
- 所有 shell 调用打 `Log`，tag 固定 `terraxaleid`：命令、退出码、stdout/stderr 都要有。

## 工作方式

- 先定位真实数据：拿一份存档跑一遍，看输出，再动代码。不要靠猜格式写解析器。
- 「对不对」的判据是**字节级**：`unexplored=0`、`p == sections[2]`、`tiles == W*H`、
  黄金样本 SHA-256 一致、`GridDigest` 一致。
- 真实存档（`*.wld` / `*.plr` / `*.map`）、`kotlin/.tools`、`local.properties`、签名文件、
  构建产物一律不进仓库。
- 只在最小范围验证，通过即停；不要顺手改与任务无关的文件。

## 版本号

- `versionName` 走 `0.1.0-dev.N` → `0.1.0-rc.N` → `0.1.0`：每轮 +1，`versionCode` 同步 +1，不开平行序列。
- 进入 `rc` 线后只修显示与稳定性问题，不再加功能；功能类改动留到下一个次版本。
- 版本号由维护者在发版时统一决定，PR 不要自己改。
- `dist/` 文件名只带 `versionName`；改动内容进 `CHANGELOG.md` 与 git tag。

## Git

- 分支 `main`；提交信息 `type(scope): summary`。
- 一个提交表达一个完整改动；提交前 `git status` 与 `git diff --cached` 都要看过。
- 维护者负责推 `origin`（`https://github.com/Dragrowl/TerraXaleid.git`）；代理不自行 push，不改写历史。
- 不加代理工具署名（`Generated with …` 之类）。

## 入口

- [文档索引](docs/INDEX.md)
- [架构与不变量](docs/ARCHITECTURE.md)
- [贡献流程](docs/CONTRIBUTING.md)
- [L1 三语言状态](docs/L1-STATUS.md)
- [GitHub 与发布](docs/GITHUB.md)
