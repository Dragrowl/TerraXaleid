# 文档索引

开发者文档。使用者只需要看仓库根的 [`README.md`](../README.md)（英文版 [`README_EN.md`](../README_EN.md)）。

| 文件 | 内容 |
|---|---|
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | 三层划分、L1 接口契约（C# / Kotlin 实际签名）、六条解析不变量、L2 的 Android 实现（Shizuku 主路线与写入链路）、L3 三态页面与动效、世界列表字段来源、难度派生、Banner 变长块的坑、版本号与产物约定 |
| [`CONTRIBUTING.md`](CONTRIBUTING.md) | 目录约定、改代码前的检查、验证与打包命令、版本号规则、文档分工、提交规范 |
| [`L1-STATUS.md`](L1-STATUS.md) | 三语言实现状态、黄金样本哈希、`GridDigest` 等价判定、八个踩过的坑、自检覆盖范围、遗留项 |
| [`ROADMAP-NEXT.md`](ROADMAP-NEXT.md) | 通往 `0.1.0-beta.1` 的清单：返回分层、顶栏下拉、设置页与主题卡、关于块、图标文字，带完成标记 |
| [`../AGENTS.md`](../AGENTS.md) | 给 AI 代理的代码边界与硬约定（人也可以当速查表） |
| [`assets/mark.svg`](assets/mark.svg) | README 用的项目标记 |
| [`assets/shots/`](assets/shots/) | 主题缩略图用的实机截图（`light-3x2.png`、`dark-3x2.png`、`auto-3x2.png`） |
| [`shots/`](shots/) | 截图专用工程（`io.terramap.shots`）的源码副本，仅作存档参考；工程本体在仓库外 |

## 阅读顺序建议

1. 想看懂「为什么地图长这样」→ `ARCHITECTURE.md` 的「六条不变量」。
2. 想核对某份实现的进度或某个坑 → `L1-STATUS.md`。
3. 想加一个平台 → `ARCHITECTURE.md` 的 L2/L3 与「打包计划」。
4. 想提交改动 → `CONTRIBUTING.md`，然后 `scripts\verify-all.ps1` 必须全绿。
5. 想知道下一步做什么 → `ROADMAP-NEXT.md`。

## 字节级细节放在哪

- 根 `README.md` 的「原理」折叠节：给要核对结果的人看，讲清「一格地图怎么算出来」。
- `ARCHITECTURE.md` 的「六条不变量」：给改解析器的人看，讲清「哪几条不能碰」。
- `L1-STATUS.md` 的「已验证的坑」：给调试的人看，讲清「错了会怎样」。
