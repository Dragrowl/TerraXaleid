<p align="center">
  <img src="docs/assets/mark.svg" alt="terramap" width="96">
</p>

<h1 align="center">terramap</h1>

<p align="center">
  <img src="https://img.shields.io/badge/version-0.1.0--dev.1-3178C6?style=for-the-badge" alt="version">
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPLv3-blue?style=for-the-badge" alt="GPL-3.0-or-later"></a>
  <a href="#构建与验证"><img src="https://img.shields.io/badge/selftest-PASS-2E7D32?style=for-the-badge" alt="selftest"></a>
  <a href="#构建与验证"><img src="https://img.shields.io/badge/L1-python%20%7C%20csharp%20%7C%20kotlin-8957E5?style=for-the-badge" alt="L1 三语言"></a>
</p>

<p align="center">
  中文 | <a href="README_EN.md">English</a>
</p>

terramap 读 Terraria 的世界存档 `.wld`，算出每一格在地图上应该显示什么，再写成合法的地图缓存 `.map`。结果就是：**打开游戏里的地图，整张世界全部点亮，一格黑区都不剩；而世界本身——物块、物品、NPC、进度——一个字节都不动。**

核心是纯数据处理：输入字节，输出字节。没有 GUI，不碰游戏进程，三份实现（Python / C# / Kotlin）对同一份存档给出同样的结果。

## 快速开始

### 需要什么

- Python 3.10+ 与 `numpy`：`python -m pip install -r requirements.txt`
- 一份世界存档 `.wld`，以及**任意一个进过这个世界的角色的** `.map`（当模板，决定调色板基址）
- 游戏处于**关闭**状态（游戏退出时会把内存里的地图写回磁盘，覆盖你的成果）

### 三步走

```powershell
# 1. 看看这台机器上有哪些角色（只读，不写任何文件）
python reveal.py "D:\...\Terraria\Worlds\我的世界.wld" --players-dir "D:\...\Terraria\Players" --list

# 2. 先演一遍，确认世界和模板都对得上（不落盘）
python reveal.py "D:\...\我的世界.wld" --players-dir "D:\...\Terraria\Players" --dry-run

# 3. 正式写：默认给该目录下每个角色都写一份
python reveal.py "D:\...\我的世界.wld" --players-dir "D:\...\Terraria\Players"
```

看到 `written : … unexplored=0 [ok]` 就是成了。`unexplored` 不是 0 会以退出码 3 失败，不会留半成品。

### 存档在哪

| 平台 | 世界 `.wld` | 角色与地图 `.map` |
|---|---|---|
| Windows / Steam | `%USERPROFILE%\Documents\My Games\Terraria\Worlds` | `%USERPROFILE%\Documents\My Games\Terraria\Players` |
| Android 国际版 | `/sdcard/Android/data/com.and.games505.TerrariaPaid/files/` | `/sdcard/Android/data/com.and.games505.TerrariaPaid/Players` |
| MuMu 模拟器 | 同上，用 `adb` 或模拟器内的文件管理器取 | 同上；改完要 `chown u0_a72:ext_data_rw` + `chmod 660`，`deploy.py` 已处理 |

## 能做什么

| 场景 | 命令 |
|---|---|
| 本机所有角色一次性点亮 | `python reveal.py WORLD.wld --players-dir PLAYERS` |
| 只给指定角色点亮 | 追加 `--player 角色名`（可重复） |
| 只列角色，不动文件 | 追加 `--list` |
| 单文件模式（自己指定输出） | `python reveal.py WORLD.wld TEMPLATE.map -o OUT.map` |
| 保留游戏已探索格子的原始类型 | 追加 `--keep-explored-type` |
| 推到模拟器 / 手机 | `python deploy.py OUT.map --serial 127.0.0.1:7555` |

地图是**按「世界 × 角色」各存一份**的：`Players/<角色>/<世界 GUID>.map`。所以"点亮"必须逐角色写，这也是默认行为——只给你写过的角色，游戏里才看得见。

每格颜色按 `Terraria.Map.MapHelper.CreateMapTile` 的真实优先级决定：**方块 > 液体（存量 > 32）> 墙 > 深度背景**。液体四槽是水 / 岩浆 / 蜂蜜 / 微光，所以海、湖、岩浆海在点亮的图上都是有颜色的，不会留成黑区。

### 手机上直接用（Android，推荐 Shizuku）

装 Shizuku 并启动服务（MuMu/真机都行），在 App 里点「授权 Shizuku」，之后列世界、选角色、点亮、写回全在手机上完成——不需要 adb，也不需要把存档拷到别处：

```
授权 Shizuku → 世界列表（直接读 Android/data） → 勾选世界 → 勾选角色 → 点亮所选
```

点亮的 `.map` 会写回游戏目录，原文件保留为 `.map.bak`；结果弹窗里可以「撤销本次」。
### 安装（release APK）

```
1) 把 dist\terramap-<版本>-release.apk 传到手机，允许「未知来源」后安装
2) 首次启动：按提示授予「所有文件访问权限」（Android 11+ 需要手动去设置里打开）
3) 安装并启动 Shizuku（root 或无线调试均可），回到 App 点「授权 Shizuku」→ 选「始终允许」
4) 世界列表出现「游戏目录 (Shizuku)」→ 勾选世界 → 勾选角色 → 点亮所选
```

打包（本机）：

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
cd G:\terramap\android
F:\Android\gradle\gradle-8.9\bin\gradle.bat :app:assembleRelease --no-daemon
# 产物：app\build\outputs\apk\release\app-release.apk
```

**签名与升级**：`release` 用 `android/keystore/terramap-release.jks` 签名（alias `terramap`，密码放在 `android/keystore.properties`）。这两个文件都在 `.gitignore` 里，不随仓库分发；覆盖安装新版本必须用同一把 key，否则 Android 会拒绝升级。

### 边界

- 只改存档文件；不改游戏进程、不注入、不挂载。
- Android 侧一切读写都走 Shizuku shell（`sh -c`），App 自身只用普通 `File` 读暂存目录；不依赖 `su`，root 设备也一样。
- 写回时保留原 `.map` 的属主与权限，原文件备份为 `.map.bak`，结果页可「撤销本次」。