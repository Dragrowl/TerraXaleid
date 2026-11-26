<p align="center">
  <img src="docs/assets/mark.svg" alt="TerraXaleid" width="96">
</p>

<h1 align="center">TerraXaleid</h1>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPLv3-blue?style=for-the-badge" alt="GPL-3.0-or-later"></a>
  <a href="https://github.com/Dragrowl/TerraXaleid/releases"><img src="https://img.shields.io/github/v/release/Dragrowl/TerraXaleid?include_prereleases&style=for-the-badge&label=release&color=2E7D32&cacheSeconds=60" alt="release"></a>
  <a href="#build--verify"><img src="https://img.shields.io/badge/selftest-PASS-2E7D32?style=for-the-badge" alt="selftest"></a>
  <a href="#build--verify"><img src="https://img.shields.io/badge/L1-python%20%7C%20csharp%20%7C%20kotlin-8957E5?style=for-the-badge" alt="L1 three languages"></a>
</p>

<p align="center">
  <a href="README.md">中文</a> | English
</p>

TerraXaleid reads a Terraria world file (`.wld`), works out what every tile should look like on the in-game map, and writes a valid map cache (`.map`). The result: **the whole world is revealed when you open the map — no black patches left — while the world itself (tiles, items, NPCs, progress) is not touched by a single byte.**

The core is pure data processing: bytes in, bytes out, never touching the game process. Three implementations (Python / C# / Kotlin) produce the same result for the same save. The UI layers only move that work onto different platforms.

## Platform status

| Platform | State | Notes |
|---|---|---|
| **Android** | ✅ available | Compose (Material 3) UI, reads and writes through Shizuku; install the APK from Releases |
| **Windows** | 🚧 command line available | `terraxaleid-cli` reads Steam saves and writes PC `.map` files (`--reveal-pc`), with `--auto-test` for an end-to-end check. No GUI yet |
| L1 core | ✅ done in three languages | Python / C# / Kotlin agree tile-for-tile (identical `decoded-grid sha256`); both UIs sit on it |


## Before you start

- **Android**: 11 or newer. Install and start [Shizuku](https://github.com/RikkaApps/Shizuku/releases/latest) first (latest build on its Releases page) — all cross-app file access goes through it.
- **Desktop (command line)**: Python 3.10+ with `numpy`.
- **Both**: quit the game completely first — it writes the in-memory map back to disk on exit and would overwrite the result.
- Only the map cache is touched; the world file stays read-only, and the original map is backed up as `.map.bak`, so every run can be undone.

## What it can do

| Goal | How |
|---|---|
| Reveal the map on a phone | Install the APK, grant Shizuku, pick world → pick characters → reveal (no PC, no adb) |
| Reveal for a single character | Same, untick everybody else in the character list |
| Undo | "撤销本次" on the result sheet, or restore `.map.bak` from "恢复与备份" |
| Wipe the map back to fully unexplored | "恢复与备份" → select → delete map (all black) |
| Change theme | Settings → Appearance: follow system / light / dark, plus standard dark or OLED black |
| Change UI language | Settings → Language: follow system / 简体中文 / 繁體中文 / 日本語 / English, applied immediately, no restart |
| Too many worlds to scan | Search box at the top of the world list filters by name fragment and highlights the match |
| Batch on a PC (interim) | `python reveal.py … --players-dir` writes every character in one go |

Maps are stored **per world × per character** (`Players/<character>/<world GUID>.map`), so a reveal has to be written for each character; the default writes one for every character on the machine.

Tile colours follow the real priority of `Terraria.Map.MapHelper.CreateMapTile`: **tile > liquid (amount > 32) > wall > depth background**. The four liquid slots are water / lava / honey / shimmer, so oceans, lakes and the underworld sea keep their colours instead of staying black.

## On Android

```
1) Copy the APK from Releases (or `TerraXaleid-<version>.apk` under dist\) to the phone and install it (allow unknown sources)
2) First launch: grant "All files access" (Android 11+ sends you to system settings)
3) Install and start [Shizuku](https://github.com/RikkaApps/Shizuku/releases/latest) (root or wireless debugging), then tap "授权 Shizuku" → "始终允许"
4) A "游戏目录 (Shizuku)" root appears → tick worlds → tick characters → reveal
```

- **Quit the world and close the game first**: the game writes the in-memory map back to disk on exit and would overwrite the result.
- The original `.map` is copied to `.map.bak` before overwriting (older backups shift to `.bak.1`, `.bak.2`), and the result sheet can undo the run.
- The ⋮ menu holds Settings / Backup & restore / Refresh; Back walks up one level, and on the world list it leaves the app (configurable to also kill the process).
- On landscape or large screens the world and character cards lay out in 2–4 columns, chosen from the available width rather than hard-coded per device.

Why Shizuku is required: since Android 11 an app cannot list another app's `Android/data` — not even with "All files access". Without Shizuku you can use the portable folders `Download/TerrariaSaves/` or `Documents/TerrariaSaves/`: drop `Worlds/` and `Players/` there, let the app work on them, then move them back with a file manager.

## On desktop

The Windows side is a C# command line, `terraxaleid-cli`: `--inspect` to look at a save, `--reveal-pc` to write a PC `.map`, `--auto-test` for an end-to-end check (read world → resolve the character → write the map → read it back).

```powershell
dotnet run --project windows\TerraXaleid.Cli -- --auto-test --world 111test --player koboshi
dotnet run --project windows\TerraXaleid.Cli -- --reveal-pc --world WORLD.wld --template TEMPLATE.map --out OUT.map
```

PC maps are `fileVersion 326`, mobile maps are `33094`. The two are not interchangeable: write the format the target machine expects.

### Requirements

- Python 3.10+ with `numpy`: `python -m pip install -r requirements.txt`
- A world file (`​.wld`) and a `.map` belonging to **any character that has entered that world** (it supplies the palette base)
- The game **closed**

### Three steps

```powershell
# 1. List the characters this machine knows (read-only)
python reveal.py "D:\...\Terraria\Worlds\MyWorld.wld" --players-dir "D:\...\Terraria\Players" --list

# 2. Dry run: check that world and template line up (nothing written)
python reveal.py "D:\...\MyWorld.wld" --players-dir "D:\...\Terraria\Players" --dry-run

# 3. Write: one map per character by default
python reveal.py "D:\...\MyWorld.wld" --players-dir "D:\...\Terraria\Players"
```

`written : … unexplored=0 [ok]` means it worked; a non-zero `unexplored` exits with code 3 and leaves no half-written file.

| Platform | World `.wld` | Characters and `.map` |
|---|---|---|
| Windows / Steam | `%USERPROFILE%\Documents\My Games\Terraria\Worlds` | `%USERPROFILE%\Documents\My Games\Terraria\Players` |
| Android (international) | `/sdcard/Android/data/com.and.games505.TerrariaPaid/Worlds` | `/sdcard/Android/data/com.and.games505.TerrariaPaid/Players` |
| MuMu emulator | same as above, via `adb` or a file manager | same; `deploy.py` fixes owner (`u0_a72:ext_data_rw`) and mode `660` |

<details>
<summary>More command line flags</summary>

| Case | Flag |
|---|---|
| Reveal only for named characters | `--player NAME` (repeatable) |
| Single file mode | `python reveal.py WORLD.wld TEMPLATE.map -o OUT.map` |
| Keep the tile type the game already explored | `--keep-explored-type` |
| Push to emulator / phone | `python deploy.py OUT.map --serial 127.0.0.1:16416` |

</details>

## Build & verify

The three L1 implementations must agree on the same bytes. Full check:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\verify-all.ps1
```

Individually:

```powershell
python .\selftest.py                                                     # Python
powershell -File .\scripts\verify-dotnet.ps1                             # C# (net10.0, no NuGet)
powershell -File .\scripts\verify-kotlin.ps1                             # Kotlin (offline compiler or gradle)
```

Android (needs `JAVA_HOME` pointing at JDK 21; Gradle 8.9 and SDK 34, see `local.properties`):

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
powershell -ExecutionPolicy Bypass -File .\scripts\package-apk.ps1
# output: dist\TerraXaleid-<versionName>.apk
```

`package-apk.ps1` reads `versionName` / `versionCode` from `android/app/build.gradle.kts`, runs `:app:assembleRelease`, names the artefact after the version and prints size plus SHA-256.

**Signing**: release builds use a local keystore (alias `TerraXaleid`, password in `android/keystore.properties`). Neither the keystore nor the password file is in the repository; upgrades must be signed with the same key.

## How it works

<details>
<summary>How one map cell is decided</summary>

1. Read the `.wld`: section table → world info (name, size, difficulty, spawn, surface, rock layer) → tile stream (column-major, RLE never crosses a column) → per-cell `tileID`, `wallID` and liquid amount.
2. Read the template `.map`: its header gives the palette bases (`tile` starts at 1, then `wall`, four liquid slots, sky/dirt/rock gradients, hell) and per-tile variant counts.
3. Decide the type per cell: tile → tile palette; else liquid amount > 32 → liquid palette; else wall → wall palette; else the depth gradient.
4. `Light` is always 255 (explored and fully lit), `Color` is 0.
5. Repack into 64×64 chunks: column-major inside a chunk, 4 bytes per cell × 4096 cells, zlib-compressed, prefixed with an `int32` length; `length <= 0` means the whole chunk is empty.
6. Verify after writing: `unexplored == 0` and chunk count `ceil(W/64)*ceil(H/64)`.

Details and traps: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md), [`docs/L1-STATUS.md`](docs/L1-STATUS.md).

</details>

<details>
<summary>Repository layout</summary>

| Path | Contents |
|---|---|
| `*.py` | L1 reference implementation (`worldfmt` / `mapfmt` / `players` / `reveal` / `deploy`) plus self-test |
| `csharp/` | C# L1 (`net10.0`, no NuGet) and its self-test |
| `windows/` | Windows side: `TerraXaleid.Core.Win` (save discovery) and `TerraXaleid.Cli` (command line) |
| `kotlin/` | Kotlin/JVM L1 and self-test (referenced by the Android module, not copied) |
| `android/` | Compose shell (Material 3), `app` + `core` modules |
| `scripts/` | three-language verification and packaging |
| `docs/` | developer docs; `docs/assets/` holds the mark and screenshots |
| `samples/` | sample notes (**no real saves**) |
| `dist/` | build artefacts (git-ignored) |

</details>

<details>
<summary>Known limits</summary>

- Steam/PC `.map` files (`fileVersion 326`) and mobile ones (`33094`) are not interchangeable: a PC map dropped into a phone (or the reverse) will not show up, and nothing converts between them.
- The Chinese "心动" (XD) release is not supported: different package name and save layout.
- Only the map cache is touched. World files stay read-only; changing difficulty is out of scope.
- On Android every read and write goes through a Shizuku shell; without it only the portable folders work.

</details>

## Three statements

1. This is an independent third-party open-source tool, unrelated to Re-Logic or the official Terraria team.
2. The project is mainly written and iterated with AI assistance (DeepSeek-V4.1-Flash during development); the core features are verified against local saves. Lacking a full traditional engineering background, the structure may have rough edges — issues are welcome.
3. It only modifies save files: no injection, no hooking, no touching the game process.

## Feedback

Issues are the place. These help a lot:

- world size and difficulty (a screenshot of the world list is enough)
- `adb logcat -s TerraXaleid:*` output (every Shizuku command, exit code and stdio is logged)
- the `.map` byte size when it fails, and whether it is reproducible

## License

GPL-3.0-or-later, see [LICENSE](LICENSE). Every source file carries an `SPDX-License-Identifier` header.

## Credits

- [Shizuku](https://github.com/RikkaApps/Shizuku) — cross-app file access without root
- [Endfield Font](https://github.com/lhclbt/Endfield_Font) — used only to render the three theme thumbnails; the font file is not in the repository
- [simple-icons](https://github.com/simple-icons/simple-icons) — GitHub / Bilibili icon outlines
- The public write-ups of the Terraria save formats (wiki.gg World File Format and map format pages)
