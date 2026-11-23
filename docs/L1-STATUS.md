# L1 实现状态

三份实现处理同一份字节，必须给出同一个结果。本文件记录「谁跑到什么程度」以及怎么证明它们等价。

## 状态表

| 语言 | 位置 | 编译/运行 | 自检结果 | 真实存档校验 |
|---|---|---|---|---|
| Python | 仓库根 `*.py` | `python selftest.py` | PASS | 是（黄金样本来源） |
| C# | `csharp/TerraMap.Core`、`csharp/TerraMap.SelfTest` | `dotnet build` + `dotnet run`（`net10.0`，零 NuGet） | PASS，0 警告 0 错误 | 是，与 Python 输出**逐字节一致** |
| Kotlin | `kotlin/src/main/kotlin/io.terramap.core` | `kotlinc` / `kotlin-compiler-embeddable`（离线 `.tools`） | PASS | 是，靠 `GridDigest` 判等价 |

## 黄金样本

同一对输入（世界 `live.wld` + 模板 `map1_orig_fromdevice.bin`）下，Python 与 C# 产出同样大小、同样哈希：

```
reveal size 2158030 bytes
sha256 272C8A8074620DE36EE3E30B5B3AE428FE0A3047F73F46CEE2F333D329F5FCF8
```

Kotlin 输出长度略有不同（`java.util.zip.Deflater` 与 zlib 的压缩流不保证逐字节相同），
因此跨语言比较不看文件哈希，看**解码后的网格摘要**：

- `GridDigest` = 对每格 `ushort type + byte light + byte color` 顺序做 SHA-256。
- C#：`--with-samples <world> <map> <dumpPath>`，Kotlin：`--with-samples <world> <map> <dumpPath>`。
- 三者 `GridDigest` 相同 = 语义等价。

## 已验证的坑（别重犯）

1. **`Type` 是 1-based**。写成 0-based 会让整张调色板错一格：泥土变黑、草变绿块。
2. **天空渐变 = `(int)(255.0 * y / surface) & 0xFF`**，不是 `min(255, y - rockLayer)`；`y` 超过表面线时按 255 回绕。
3. **区块是 64×64，块内列主序**：`x = cx*64 + n%64`、`y = cy*64 + n/64`。按行主序解出来的
   图形与真实地形相关性只有 0.04（等于随机），这是当初误判「块内不是行主序」的来由。
4. **空块（`int32 length <= 0`）是语义的一部分**：不是可省略的填充，跳过后必然 `EndOfStreamException`。
5. **`.wld` 的 tile 流 RLE 不跨列**：每列严格 `H` 格，`run + 1` 覆盖格数，超出列边界要截断。
6. **`worldSurface` / `rockLayer` 必须按固定偏移读 float64**，不要用「扫描找两个浮点」的启发式：
   启发式在两个实现里会猜出微小差异，导致 137,899 格天空渐变差 1。

## 遗留项

- PC/Steam 的 `.map` 是 `fileVersion 318`（带 RLE），当前只认移动版 `33094`；读到 318 会明确报
  `unsupported map file version 318` 并且不写文件。
- Android 外壳目前只做到「显示 ABI + 跑一次核心自检」，文件读取仍走 L2 未实现。
- `kotlin/.tools`（编译器 jar）只在本机存在，不进版本库；换机器由 `verify-kotlin.ps1` 如实报
  `toolchain: unavailable`。
