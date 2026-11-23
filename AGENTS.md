# terramap — 给 AI 代理的仓库约定

Terraria 存档地图读写工具。输入 `.wld` 字节、输出 `.map` 字节，没有 GUI，不碰游戏进程。

使用者看 [`README.md`](README.md)；人类开发者先看 [`docs/INDEX.md`](docs/INDEX.md)。

## 代码边界

- 根 `*.py`：L1 参考实现，也是三语言的「基准答案」。
  - `worldfmt.py` 只负责读 `.wld`（`load` 解析全量 tile，`read_meta` 只走头部，`read_layers` 按固定偏移取 surface/rock 两个 float64）。
  - `mapfmt.py` 只负责 `.map`（头部解析、调色板基址、区块读写）。
  - `players.py` 只负责「世界 GUID ⇄ 角色地图文件名」与角色选择。
  - `reveal.py` 是 CLI，`deploy.py` 是推设备；两者不做解析。
- `csharp/TerraMap.Core`：L1 的 C# 实现，`net10.0`，**零 NuGet**；`TerraMap.SelfTest` 是自检（普通 `main`，不引测试框架）。
- `kotlin/src/main/kotlin/io/terramap/core`：L1 的 Kotlin 实现；`android/core` 通过 `srcDir("../../kotlin/src/main/kotlin")` **直接引用**，不复制源码。
- `android/app`：Compose 外壳，只做展示与调用，不实现解析。
- L1 里**禁止**出现 `File` / `Directory` / `Environment` / 时钟读取：L1 只吃字节、吐字节；路径与权限属于 L2。

## 改动的硬约定

改动任何一个解析规则，必须同步三份实现（Python / C# / Kotlin），改完跑 `scripts\verify-all.ps1` 必须全绿。以下是不变量，写错就会静默出错图：

1. `Type` 是 **1-based** 调色板索引，`tile` 段起点是 `1`。
2. 天空渐变 = `(int)(255.0 * y / worldSurface) & 0xFF`，不是 `min(255, y - rockLayer)`。
3. 区块 64×64，块内**列主序**：`x = cx*64 + n%64`、`y = cy*64 + n/64`。
4. 块前缀 `int32 length <= 0` 表示整块空，是语义的一部分，不能省。
5. `.wld` tile 流 RLE **不跨列**，每列严格 `H` 格，覆盖格数 = `run + 1`。
6. `worldSurface` / `rockLayer` 按固定偏移读，不要用「扫描猜两个浮点」的启发式（两份实现会猜出微小差异）。

## 工作方式

- 先定位真实数据：拿一份存档跑一遍，看输出，再动代码。不要靠猜格式写解析器。
- 判断「对不对」的判据是**字节级**：`unexplored=0`、`p == sections[2]`、`tiles == W*H`、黄金样本 SHA-256 一致、`GridDigest` 一致。
- 真实存档（`*.wld` / `*.plr` / `*.map`）、`kotlin/.tools`、`local.properties`、构建产物一律不进仓库。
- 只在最小范围验证，通过即停；不要顺手改与任务无关的文件。

## Git

- 分支 `main`；提交信息 `type(scope): summary`（`feat` / `fix` / `docs` / `chore` / `refactor`）。
- 一个提交表达一个完整改动；提交前 `git status` 与 `git diff --cached` 都要看过。
- 不建 remote、不 push、不经确认不改写历史。
- 不加代理工具署名（`Generated with …` 之类）。

## 入口

- [文档索引](docs/INDEX.md)
- [架构与不变量](docs/ARCHITECTURE.md)
- [贡献流程](docs/CONTRIBUTING.md)
- [L1 三语言状态](docs/L1-STATUS.md)
