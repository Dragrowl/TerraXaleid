# L1 实现状态

三份实现处理同一份字节，必须给出同一个结果。本文件记录「谁跑到什么程度」以及怎么证明它们等价。

## 状态表

| 语言 | 位置 | 怎么跑 | 结果 | 真实存档校验 |
|---|---|---|---|---|
| Python | 仓库根 `*.py` | `python selftest.py` | PASS | 是，黄金样本来源 |
| C# | `csharp/TerraMap.Core`、`csharp/TerraMap.SelfTest` | `dotnet build` + `dotnet run`（`net10.0`，零 NuGet） | PASS，0 警告 0 错误 | 是，与 Python **逐字节一致** |
| Kotlin | `kotlin/src/main/kotlin/io/terramap/core` | `scripts\verify-kotlin.ps1`（离线 `kotlin-compiler-embeddable` 或 gradle） | PASS | 是，靠 `GridDigest` 判等价 |

Android 的 `core` 模块通过 `sourceSets.main.kotlin.srcDir("../../kotlin/src/main/kotlin")`
**直接引用** Kotlin 版，不复制源码——所以改 L1 只需要改一处，App 自动跟着变。

## 黄金样本

同一对输入（世界 `live.wld` + 模板 `map1_orig_fromdevice.bin`）下，Python 与 C# 产出同样大小、同样哈希：

```
reveal size 2158030 bytes
sha256 272C8A8074620DE36EE3E30B5B3AE428FE0A3047F73F46CEE2F333D329F5FCF8
```

Kotlin 输出长度略有不同（`java.util.zip.Deflater` 与 zlib 的压缩流不保证逐字节相同），
所以跨语言比较不看文件哈希，看**解码后的网格摘要**：

- `GridDigest` = 对每格 `ushort type + byte light + byte color` 顺序做 SHA-256。
- 三份自检的 `--with-samples <world> <map> [dumpPath]` 都会打印它。
- `GridDigest` 相同 = 语义等价。

含液体的那版（当前版本 起）三方 `GridDigest` = `D4FEC839110830DEB2AAB373D9259F4807B23CA22A0346831D05AD29C01FB5DE`；
C# 与 Python 的整文件 sha256 也相同（`ABEBD4AB…1290150`，2,179,420 B）。

## 已验证的坑（别重犯）

1. **`Type` 是 1-based**。写成 0-based 会让整张调色板错一格：泥土变黑、草变绿块。
2. **天空渐变 = `(int)(255.0 * y / surface) & 0xFF`**，不是 `min(255, y - rockLayer)`；超过表面线按 255 回绕。
3. **区块 64×64，块内列主序**：`x = cx*64 + n%64`、`y = cy*64 + n/64`。按行主序解出来的图形
   与真实地形相关性只有 0.04（等于随机），这是当初误判「块内不是行主序」的来由。
4. **空块（`int32 length <= 0`）是语义的一部分**：不是可省略的填充，跳过后必然 `EndOfStreamException`。
5. **`.wld` tile 流 RLE 不跨列**：每列严格 `H` 格，`run + 1` 覆盖格数，超列边界要截断。
6. **`worldSurface` / `rockLayer` 按固定偏移读 float64**，不要用「扫描找两个浮点」的启发式：
   两份实现会猜出微小差异，导致 137,899 格天空渐变差 1。
7. **液体在方块与墙之间**：`CreateMapTile` 的优先级是 方块 > 液体（存量 > 32）> 墙 > 背景。
   液体四槽顺序 water / lava / honey / shimmer（shimmer 走 `f3` 的 bit7，不是 `f1` 的液体位）。
8. **`.map` 的 option 位图字节数 = `ceil(maxId / 8)`**，读取时每 8 位换一个新字节
   （`mask == 128` 时取新字节并置 1），不是 `maxId/8 + 1`。

## 合成自检覆盖了什么

不依赖任何外部文件即可跑：

- 调色板算术（`tile(0)=1`、`wall` 基址、液体 4 槽、天空/泥土/岩石/地狱基址、`skyType` 回绕）
- 区块网格（70×70→2×2、6400×1800→100×29、8400×2400→132×38、4200×1200→66×19）
- `String7` 单/双字节长度
- 难度派生 8 组（GM0–3 × FTW 0/1）
- 70×70 合成头 round-trip（`MapFile.write` → `read` → `exploredCount() == 4900`）
- 全点亮覆盖（每格 light = 255，按行预算的背景渐变正确）
- 合成 3×3 `.wld`（important tileID 走 4 字节 frame）
- 大世界构建（6400×1800 = 1152 万格，托管/堆增量约 43–50 MB）
- `PlayerMaps` 真实 GUID ↔ 文件名互验

## 遗留项

- PC/Steam 的 `.map` 是 `fileVersion 318`（带 RLE），当前只认移动版 `33094`；读到 318 会明确报
  `unsupported map file version 318` 并且不写文件。
- `kotlin/.tools`（编译器 jar，约 120 MB）只在本机存在，不进版本库；换机器由
  `verify-kotlin.ps1` 如实报 `toolchain: unavailable`（退出码 2）。
- L1 只做「全量覆盖」：`RevealOptions.OverwriteExplored = false` 会抛 `NotSupportedException`，
  因为部分点亮需要旧 payload，属于 L2 的活。
