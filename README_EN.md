<p align="center">
  <img src="docs/assets/mark.svg" alt="terramap" width="96">
</p>

<h1 align="center">terramap</h1>

<p align="center">
  <img src="https://img.shields.io/badge/version-0.1.0--beta.1-2E7D32?style=for-the-badge" alt="version">
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPLv3-blue?style=for-the-badge" alt="GPL-3.0-or-later"></a>
  <a href="#build-and-verify"><img src="https://img.shields.io/badge/selftest-PASS-2E7D32?style=for-the-badge" alt="selftest"></a>
  <a href="#build-and-verify"><img src="https://img.shields.io/badge/L1-python%20%7C%20csharp%20%7C%20kotlin-8957E5?style=for-the-badge" alt="L1 three languages"></a>
</p>

<p align="center">
  <a href="README.md">中文</a> | English
</p>

terramap reads a Terraria world file (`.wld`), decides what every tile should look like on the map, and writes a valid map cache (`.map`). The result: **open the in-game map and the whole world is revealed — no black areas left — while the world itself (tiles, items, NPCs, progress) is not touched by a single byte.**

It is pure data processing: bytes in, bytes out. No game process is involved, and three implementations (Python / C# / Kotlin) produce the same result for the same save. The Android app is just a UI that runs the same thing on the phone.

> **Versioning**: `0.1.0-dev.N` during development (`versionCode` +1 per round), then `0.1.0-beta.1` once the round in `docs/ROADMAP-NEXT.md` is done — that round is done, so the current tag is `0.1.0-beta.1`. Files in `dist/` carry only the `versionName`.

## What it does

| Goal | How |
|---|---|
| Reveal the map on a phone | Install the APK, grant Shizuku, pick world → pick characters → reveal. No PC, no adb |
| Reveal for one character only | Same, uncheck the others in the character list |
| Undo a reveal | "撤销本次" on the result page, or restore `.map.bak` from "恢复与备份" |
| Reset a map to fully unexplored | "恢复与备份" → select → delete map (all black) |
| Batch processing on a PC | `python reveal.py` with `--players-dir` to write every character |
| Just list characters | `python reveal.py ... --list` (read-only) |

Map caches are stored **per world × per character** (`Players/<character>/<worldGUID>.map`), so a reveal has to be written for each character; by default every local character gets a copy, because a character without one still sees a black map.

Per-tile colour follows the real priority in `Terraria.Map.MapHelper.CreateMapTile`: **tile > liquid (amount > 32) > wall > depth background**. The four liquid slots are water / lava / honey / shimmer, so oceans, lakes and the lava sea are coloured instead of showing as black.

## On Android

```
1) Copy dist\terramap-0.1.0-dev.17.apk to the phone and install it (allow unknown sources)
2) First launch: grant "All files access" when prompted (Android 11+ needs the system settings page)
3) Install and start Shizuku (root or wireless debugging), return to the app, tap
   "授权 Shizuku" → "Always allow"
4) The world list shows "游戏目录 (Shizuku)" → select worlds → select characters → reveal
```

- Leave the world and close the game before writing: on exit the game writes its in-memory map back over yours.
- The original `.map` is kept as `.map.bak` (further overwrites rotate old backups to `.bak.1`, `.bak.2`); the result page can undo the operation.
- The clock icon in the top bar opens "恢复与备份": restore a backup, delete backups only, or reset maps to fully unexplored.

Why Shizuku is required: since Android 11 a normal app cannot list **another app's** `Android/data`, not even with "All files access". Without Shizuku, use the portable folders `Download/TerrariaSaves/` or `Documents/TerrariaSaves/`: put `Worlds/` and `Players/` there, let the app work on them, then move them back with a file manager.

## On a PC (CLI)

Requirements: Python 3.10+ with `numpy` (`python -m pip install -r requirements.txt`), a `.wld`, and any `.map` of a character that has visited that world (used as the palette template). The game must be closed.

```powershell
# 1. list characters (read-only)
python reveal.py "D:\...\Terraria\Worlds\MyWorld.wld" --players-dir "D:\...\Terraria\Players" --list

# 2. dry run, no files written
python reveal.py "D:\...\MyWorld.wld" --players-dir "D:\...\Terraria\Players" --dry-run

# 3. write one map per character
python reveal.py "D:\...\MyWorld.wld" --players-dir "D:\...\Terraria\Players"
```

`written : … unexplored=0 [ok]` means success; anything other than `unexplored=0` exits with code 3 and leaves nothing half-written.

| Platform | World `.wld` | Characters and `.map` |
|---|---|---|
| Windows / Steam | `%USERPROFILE%\Documents\My Games\Terraria\Worlds` | `%USERPROFILE%\Documents\My Games\Terraria\Players` |
| Android (international) | `/sdcard/Android/data/com.and.games505.TerrariaPaid/files/` | `/sdcard/Android/data/com.and.games505.TerrariaPaid/Players` |
| MuMu emulator | same, via `adb` or the emulator's file manager | same; `deploy.py` fixes owner (`u0_a72:ext_data_rw`) and mode `660` |

<details>
<summary>More CLI flags</summary>

| Case | Command |
|---|---|
| Only specific characters | append `--player NAME` (repeatable) |
| Single-file mode | `python reveal.py WORLD.wld TEMPLATE.map -o OUT.map` |
| Keep the type of already explored cells | append `--keep-explored-type` |
| Push to emulator / phone | `python deploy.py OUT.map --serial 127.0.0.1:7555` |

</details>

## Build and verify

All three L1 implementations must agree on the same bytes:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\verify-all.ps1
```

Android (`JAVA_HOME` must point at JDK 21; Gradle 8.9 and SDK 34 are configured in `local.properties`):

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
powershell -ExecutionPolicy Bypass -File .\scripts\package-apk.ps1
# output: dist\terramap-0.1.0-dev.17.apk
```

The release build is signed with `android/keystore/terramap-release.jks` (alias `terramap`, password in `android/keystore.properties`). Both files are git-ignored; upgrades require the same key.

## How it works

<details>
<summary>What every map cell comes from</summary>

1. Read `.wld`: section offsets → world info (name / size / difficulty / spawn / surface / rock layer) → tile stream (column-major, RLE does not cross columns) → per-cell `tileID` and `wallID`.
2. Read the template `.map`: the header gives palette bases (tile section starts at 1, wall section, four liquid slots, sky/dirt/rock gradients, hell) and the variation count per tile id.
3. Per cell: tile → tile palette; else liquid > 32 → liquid palette; else wall → wall palette; else a depth-based gradient background.
4. Light is always 255 (explored and fully lit), color 0.
5. Repack into 64×64 chunks: column-major inside a chunk, 4 bytes per cell × 4096 cells, zlib-compressed with an `int32` length prefix; `length <= 0` means an all-empty chunk.
6. Read back: `unexplored == 0` and chunk count == `ceil(W/64)*ceil(H/64)`.

Details and pitfalls: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) and [`docs/L1-STATUS.md`](docs/L1-STATUS.md).

</details>

<details>
<summary>Repository layout</summary>

| Path | Contents |
|---|---|
| `*.py` | L1 reference implementation (`worldfmt` / `mapfmt` / `players` / `reveal` / `deploy`) and its self-test |
| `csharp/` | L1 in C# (`net10.0`, no NuGet) + self-test |
| `kotlin/` | L1 in Kotlin/JVM + self-test (referenced directly by the Android module) |
| `android/` | Compose shell (Material 3), `app` + `core` modules |
| `scripts/` | Verification and packaging scripts |
| `docs/` | Developer docs; `docs/assets/` holds the mark and screenshots |
| `samples/` | Sample notes (no real saves) |
| `dist/` | Build output (git-ignored) |

</details>

<details>
<summary>Known limitations</summary>

- PC/Steam `.map` files are `fileVersion 318` (with RLE); only the mobile `33094` format is supported. A 318 file reports `unsupported map file version 318` and nothing is written.
- The Chinese mainland release by X.D. Inc. is not supported (different package and save layout).
- Only the map cache is touched; the world file is read-only.
- Everything on Android goes through the Shizuku shell; without it only the portable folders work.

</details>

## Three statements

1. This is an independent third-party open-source tool, unrelated to Re-Logic and the official Terraria project.
2. It is mostly written and iterated with AI assistance (DeepSeek-V4.1-Flash was used during development); the core functionality is verified against local saves. The structure may not follow every conventional practice — issues are welcome.
3. Only save files are modified: nothing is injected, hooked or mounted.

## License

GPL-3.0-or-later, see [LICENSE](LICENSE). Every source file carries an `SPDX-License-Identifier`.

## Credits

- [Shizuku](https://github.com/RikkaApps/Shizuku) — safe cross-app file access without root
- [Endfield Font](https://github.com/lhclbt/Endfield_Font) — used only to render the three theme thumbnails; the font itself is not shipped
- [simple-icons](https://github.com/simple-icons/simple-icons) — GitHub / Bilibili marks
- The public documentation of the Terraria save formats (wiki.gg World File Format and map notes)
