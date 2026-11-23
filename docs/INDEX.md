# 文档

开发者文档。使用者只需要看仓库根目录的 [`README.md`](../README.md)。

| 文件 | 内容 |
|---|---|
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | 三层架构、L1 契约（C# / Kotlin 签名草案）、六条解析不变量、L2 路径发现与权限路线、L3 页面清单、难度派生表、Banner 变长块的坑、打包计划与迁移顺序 |
| [`CONTRIBUTING.md`](CONTRIBUTING.md) | 目录约定、改代码前的检查、提交信息规范、验证命令 |
| [`L1-STATUS.md`](L1-STATUS.md) | 三语言 L1 实现状态、黄金样本哈希、`GridDigest` 等价判定、六个踩过的坑、遗留项 |
| [`../AGENTS.md`](../AGENTS.md) | AI 代理的代码边界与硬约定（人也能当速查表读） |
| [`assets/mark.svg`](assets/mark.svg) | README 用的项目标记 |

## 阅读顺序建议

1. 想看懂「为什么地图长这样」→ `ARCHITECTURE.md` 的「不变量」一节。
2. 想加一个平台 → `ARCHITECTURE.md` 的「迁移顺序」+ `L1-STATUS.md`。
3. 想提交改动 → `CONTRIBUTING.md`，然后 `scripts\verify-all.ps1` 必须全绿。

## 格式速查

字节级字段表与坑放在两层：

- 根 [`README.md`](../README.md) 的「原理」折叠节——给要核对结果的人看，讲清"一格地图怎么算出来"。
- `ARCHITECTURE.md` 的「不变量」——给改解析器的人看，讲清"哪几条不能碰"。
