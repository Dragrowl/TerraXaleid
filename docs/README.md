# docs/

开发者文档。使用者只需要看仓库根目录的 `README.md`。

| 文件 | 内容 |
|---|---|
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | 三层架构、L1 契约（C# / Kotlin 签名草案）、六条解析不变量、L2 路径发现与权限路线、L3 页面清单、难度派生表、Banner 变长块的坑、打包计划与迁移顺序 |
| [`CONTRIBUTING.md`](CONTRIBUTING.md) | 目录约定、改代码前的检查、提交信息规范、验证命令、许可声明 |
| [`L1-STATUS.md`](L1-STATUS.md) | 三语言 L1 实现状态：各自通过了什么、跨语言一致性怎么验证、已知差异与遗留项 |

## 阅读顺序建议

1. 想看懂「为什么地图长这样」→ `ARCHITECTURE.md` 的「不变量」一节。
2. 想加一个平台 → `ARCHITECTURE.md` 的「迁移顺序」+ `L1-STATUS.md`。
3. 想提交改动 → `CONTRIBUTING.md`，然后 `scripts\verify-all.ps1` 必须全绿。

## 格式速查

`.wld` 与 `.map` 的字节级细节写在根 `README.md` 第 3 章（使用者也要能核对结果，所以留在那里），
本目录只讲**实现约束**，不重复字段表。
