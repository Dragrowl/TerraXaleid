# samples/

这个目录**不放真实游戏存档**。

`--with-samples` 那类校验需要一份 `.wld`（世界）和一份同世界的 `.map`（地图模板）。
它们属于玩家自己的存档，体积大且含个人信息，因此不进版本库。

需要跑真实样本校验时，把路径直接传给脚本：

```powershell
# Python
python .\reveal.py "C:\path\world.wld" "C:\path\template.map" -o out.map

# C#（会同时打印文件 SHA-256 与 GridDigest）
powershell -File .\scripts\verify-dotnet.ps1 -WithSamples `
  -WorldPath "C:\path\world.wld" -MapPath "C:\path\template.map"

# Kotlin
powershell -File .\scripts\verify-kotlin.ps1 -WithSamples `
  -WorldPath "C:\path\world.wld" -MapPath "C:\path\template.map"
```

不需要真实存档的部分（调色板算术、区块网格、难度派生、合成 70×70 round-trip、
合成 3×3 重要帧跳过、大世界内存构建、角色名映射）都写在 `selftest.py` /
`csharp/TerraMap.SelfTest/Program.cs` / `kotlin/.../SelfTest.kt` 里，
目标是**不依赖任何外部文件**也能全绿。

黄金样本（同一对输入下必须一致）见 [`../docs/L1-STATUS.md`](../docs/L1-STATUS.md)。
