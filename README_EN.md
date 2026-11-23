<p align="center">
  <img src="docs/assets/mark.svg" alt="terramap" width="96">
</p>

<h1 align="center">terramap</h1>

<p align="center">
  <img src="https://img.shields.io/badge/version-0.1.0--dev.1-3178C6?style=for-the-badge" alt="version">
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPLv3-blue?style=for-the-badge" alt="GPL-3.0-or-later"></a>
  <a href="#build--verify"><img src="https://img.shields.io/badge/selftest-PASS-2E7D32?style=for-the-badge" alt="selftest"></a>
  <a href="#build--verify"><img src="https://img.shields.io/badge/L1-python%20%7C%20csharp%20%7C%20kotlin-8957E5?style=for-the-badge" alt="L1 implementations"></a>
</p>

<p align="center">
  <a href="README.md">中文</a> | English
</p>

terramap reads a Terraria world file (`.wld`), computes what every tile should look like on the map, and writes a valid map cache (`.map`). The result: **the in-game map shows the whole world revealed — no unexplored black areas — while the world itself is untouched** (terrain, items, NPCs, progress: not a single byte).

It is pure data processing: bytes in, bytes out. No GUI, no process injection. Three implementations (Python / C# / Kotlin) produce identical output for the same save.

## Quick start

### Requirements

- Python 3.10+ with `numpy`: `python -m pip install -r requirements.txt`
- A world `.wld`, plus **any** `.map` of the same world (used as a template: it carries the palette base offsets)
- The game **closed** (it writes its in-memory map back on exit and would overwrite the result)

### Three steps

```powershell
# 1. list characters on this machine (read-only, writes nothing)
python reveal.py "D:\...\Terraria\Worlds\MyWorld.wld" --players-dir "D:\...\Terraria\Players" --list

# 2. dry run: verify the world and the template match (nothing is written)
python reveal.py "D:\...\MyWorld.wld" --players-dir "D:\...\Terraria\Players" --dry-run

# 3. write, for every character in that directory by default
python reveal.py "D:\...\MyWorld.wld" --players-dir "D:\...\Terraria\Players"
```

`written : … unexplored=0 [ok]` means success. A non-zero `unexplored` exits with code 3 instead of leaving a half-written file.

### Where the saves live

| Platform | world `.wld` | characters and `.map` |
|---|---|---|
| Windows / Steam | `%USERPROFILE%\Documents\My Games\Terraria\Worlds` | `%USERPROFILE%\Documents\My Games\Terraria\Players` |
| Android (international) | `/sdcard/Android/data/com.and.games505.TerrariaPaid/files/` | `/sdcard/Android/data/com.and.games505.TerrariaPaid/Players` |
| MuMu emulator | as above, via `adb` | as above; then `chown u0_a72:ext_data_rw` + `chmod 660` (`deploy.py` does this) |

## What it does

| Task | Command |
|---|---|
| reveal for every character | `python reveal.py WORLD.wld --players-dir PLAYERS` |
| reveal for specific characters | add `--player NAME` (repeatable) |
| list characters only | add `--list` |
| single file mode | `python reveal.py WORLD.wld TEMPLATE.map -o OUT.map` |
| keep the game's Type for explored cells | add `--keep-explored-type` |
| push to emulator / phone | `python deploy.py OUT.map --serial 127.0.0.1:7555` |

Exploration is stored **per (world, character)** as `Players/<character>/<world GUID>.map`, which is why the default is "every character".

## Notices

1. **This is an independent third-party open-source tool, unaffiliated with Re-Logic or Terraria.**
2. **This project is largely written and iterated with AI assistance (DeepSeek-V4.1-Flash generated a substantial part of the code); core functionality is verified against local save files. It may fall short of conventional engineering standards — issues are welcome.**
3. Licensed **GPL-3.0-or-later**; see [`LICENSE`](LICENSE). Every source file carries an `SPDX-License-Identifier` header.

<details>
<summary><h2 id="scope">Scope</h2></summary>

Deliberate constraints, not missing features:

- **No tModLoader involvement**: no `.tmod` / `.efmod`, no in-game mod required.
- **No mounting, no injection**: no DLL injection, no hooks, no memory patches, no APK modification. Files are processed offline while the game is closed.
- **Only the map cache is written**: `.wld` is read-only.
- **Chinese mainland release (`con.xd.terraria`) is not supported**: different save layout.

</details>

<details>
<summary><h2 id="how">How a tile becomes a map cell</h2></summary>

A `.map` is not an image: it is dense 64×64 chunks of 4096 cells, each cell 4 bytes (`uint16 Type`, `uint8 Light`, `uint8 Color`), **column-major inside a chunk**, zlib-compressed, prefixed by an `int32` length (length 0 means the chunk is unexplored). Revealing means writing `Light = 255` everywhere and computing `Type` from world data.

`Type` is a **palette index**, not a tile id: `tile = 1 + prefixTile[tileID]`, `wall = 1 + ΣtileOptionCounts + prefixWall[wallID]`, then liquid, sky gradient, dirt, rock, hell. It is **1-based** — getting that wrong shifts the entire palette by one (dirt turns black).

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) and [`docs/L1-STATUS.md`](docs/L1-STATUS.md) for field tables and the pitfalls behind them.

</details>

<details>
<summary><h2 id="build--verify">Build and verify</h2></summary>

```powershell
.\scripts\verify-all.ps1                                  # python + csharp + kotlin
python selftest.py
dotnet run --project csharp\TerraMap.SelfTest             # needs .NET 10 SDK
.\scripts\verify-kotlin.ps1                               # needs kotlinc or kotlin/.tools
```

Real-save check (optional, compares against the golden hash):

```powershell
dotnet run --project csharp\TerraMap.SelfTest -- --with-samples WORLD.wld TEMPLATE.map
```

Expected: `reveal size 2158030 bytes, sha256 272C8A80…5FCF8`, byte-identical to the Python output.

A missing toolchain exits with code 2 and prints `toolchain: unavailable` — that is not a pass.

</details>

<details>
<summary><h2 id="layout">Repository layout</h2></summary>

| Path | Contents |
|---|---|
| `*.py` (root) | reference L1: `worldfmt.py`, `mapfmt.py`, `players.py`, `reveal.py`, `deploy.py` |
| `csharp/` | L1 in C# (`net10.0`, no NuGet) + self test |
| `kotlin/` | L1 in Kotlin/JVM (also referenced by the Android module) |
| `android/` | Compose shell (Material 3) |
| `scripts/` | verification scripts |
| `docs/` | developer docs: [architecture](docs/ARCHITECTURE.md), [contributing](docs/CONTRIBUTING.md), [L1 status](docs/L1-STATUS.md), [index](docs/INDEX.md) |
| `samples/` | sample notes (no real saves in the repo) |

</details>

<details>
<summary><h2 id="limits">Known limits</h2></summary>

- Quit the game before writing `.map`, or the game overwrites it on exit.
- On `/sdcard/...`, `chown u0_a72:ext_data_rw` + `chmod 660` is required (`restorecon` fails on FUSE, harmless).
- Chests and other map sprites live in world data, not in `.map`.
- **PC (Steam) `.map` files are the old `fileVersion 318` (with RLE); only the mobile 1.4.5.8 `33094` format is supported.** A 318 template is rejected with `unsupported map file version 318` rather than written incorrectly.
- A character that never entered a world gets a newly created `.map` — expected for "reveal for every character"; narrow it with `--player`.

</details>

<details>
<summary><h2 id="roadmap">Roadmap</h2></summary>

- [x] L1 in three languages, byte-identical C# vs Python output
- [x] per-character reveal, `--list` / `--player` / `--dry-run`
- [x] Android Compose skeleton (`arm64-v8a` first)
- [ ] Android APK: `arm64-v8a`, `armeabi-v7a`, `x86_64`
- [ ] Windows: single-file exe or portable (including win-arm64)
- [ ] Optional `127.0.0.1` console form

</details>

<details>
<summary><h2 id="license">License</h2></summary>

[GPL-3.0-or-later](LICENSE). The project rewrites files players own; they should be able to audit exactly which bytes change and to redistribute their own modifications, and GPL-3.0 keeps derived versions open as well.

</details>

## Feedback

Include in an issue: world name, size, `GameMode` (printed by `reveal.py`), the template `.map` `fileVersion`, the full `--dry-run` output, `scripts/verify-all.ps1` result, and your platform plus the full game version string.

## Credits

- [Re-Logic](https://terraria.org/) for Terraria, and the wiki community's [world file format docs](https://terraria.wiki.gg/wiki/User:Minimiscience/World_File_Format).
- [lihzahrd](https://github.com/Stealthmate/lihzahrd) — its tile decoding order served as cross-validation.
- [ilspycmd](https://github.com/icsharpcode/ILSpy) — decompiling the local PC `Terraria.exe` (`Terraria.Map.MapHelper`) produced the authoritative `.map` algorithm.
