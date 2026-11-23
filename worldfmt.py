# SPDX-License-Identifier: GPL-3.0-or-later

"""
worldfmt.py — Terraria world file (.wld, format 326 / 1.4.5.x) reader.

Only what the map generator needs:
    world_id, name, width, height, surface, rock_layer, tiles[x][y], walls[x][y]

tiles / walls are numpy int16 arrays shaped (width, height); -1 means "none".
Reading is strictly read-only: the file is loaded into memory and never written back.

Tile stream encoding (per column, exactly `height` tiles):
    flags byte; bit0 of the byte just read => one more flags byte follows (max 4)
    bit1 = has tile, bit2 = has wall, bits3-4 = liquid, bit5 = tile id is uint16,
    bits6-7 = RLE mode (0 none, 1 uint8 run follows, 2 uint16 run follows)
    field order: tile id -> (frame u,v int16 each if the tile id is "important")
                 -> tile paint -> wall id (-- if >255 a high byte is appended after liquid)
                 -> wall paint -> liquid amount -> run
    RLE covers run+1 tiles and never crosses a column boundary.
"""
from __future__ import annotations

import struct
from dataclasses import dataclass

import numpy as np


@dataclass
class World:
    name: str
    world_id: int
    width: int
    height: int
    surface: float
    rock_layer: float
    tiles: np.ndarray   # (width, height) int16, -1 = no tile
    walls: np.ndarray   # (width, height) int16, -1 = no wall
    guid_bytes: bytes = b""   # the 16 bytes as stored; .map files are named after this GUID
    liquids: np.ndarray | None = None       # (width, height) uint8 fill level 0..255
    liquid_kinds: np.ndarray | None = None  # (width, height) uint8 0 water 1 lava 2 honey 3 shimmer

    # MapHelper.GetWallType only draws liquid above this fill level.
    LIQUID_PAINT_THRESHOLD = 32

    @property
    def underworld_layer(self) -> int:
        return self.height - 200


def _read_string7(buf: bytes, pos: int) -> tuple[str, int]:
    length = 0
    shift = 0
    while True:
        b = buf[pos]
        pos += 1
        length |= (b & 0x7F) << shift
        if not b & 0x80:
            break
        shift += 7
    return buf[pos:pos + length].decode("utf-8"), pos + length


def _find_layer_floats(section1: bytes, height: int) -> tuple[float, float]:
    """Locate the (worldSurface, rockLayer) float64 pair inside section 1.

    The exact byte offset depends on a long run of booleans/flags that has changed
    between releases, so instead of hard-coding it we look for the only two
    consecutive doubles that both look like layer depths.
    """
    best = None
    for off in range(0, len(section1) - 16):
        a, b = struct.unpack_from("<dd", section1, off)
        if not (50.0 < a < height - 100):
            continue
        if not (a + 20 < b < height - 50):
            continue
        score = abs((b - a) - 264.0)
        if score > 90:
            continue
        if best is None or score < best[0]:
            best = (score, a, b)
    if best is not None:
        return best[1], best[2]
    # fallback: rough guesses; only affects the sky gradient above ground
    return height * 0.274, height * 0.42


def load(path: str) -> World:
    with open(path, "rb") as fh:
        d = fh.read()

    fmt = struct.unpack_from("<i", d, 0)[0]
    if fmt < 200 or d[4:11] != b"relogic":
        raise ValueError(f"not a Terraria world file (format={fmt})")

    nsec = struct.unpack_from("<h", d, 24)[0]
    offsets = struct.unpack_from(f"<{nsec}i", d, 26)
    max_imp = struct.unpack_from("<h", d, 70)[0]
    imp_bits = d[72:72 + (max_imp // 8 + 1)]
    important = [bool(imp_bits[i // 8] >> (i % 8) & 1) for i in range(max_imp + 1)]

    s1 = offsets[0]
    name, p = _read_string7(d, s1)
    _seed, p = _read_string7(d, p)
    p += 8                          # worldGenVersion
    guid_bytes = d[p:p + 16]; p += 16   # guid
    world_id = struct.unpack_from("<i", d, p)[0]; p += 4
    p += 16                         # left / right / top / bottom (pixel bounds)
    height = struct.unpack_from("<i", d, p)[0]
    width = struct.unpack_from("<i", d, p + 4)[0]
    p += 8
    game_mode = struct.unpack_from("<i", d, p)[0]   # 0 classic 1 expert 2 master 3 journey

    section1 = d[s1:offsets[1]]
    surface, rock = _find_layer_floats(section1, height)

    tiles = np.full((width, height), -1, dtype=np.int16)
    walls = np.full((width, height), -1, dtype=np.int16)
    liquids = np.zeros((width, height), dtype=np.uint8)        # 0..255 fill level
    liquid_kinds = np.zeros((width, height), dtype=np.uint8)   # 0 water 1 lava 2 honey 3 shimmer

    start, end = offsets[1], offsets[2]
    pos = start
    for x in range(width):
        col = 0
        while col < height:
            f1 = d[pos]; pos += 1
            f2 = f3 = 0
            if f1 & 1:
                f2 = d[pos]; pos += 1
                if f2 & 1:
                    f3 = d[pos]; pos += 1
                    if f3 & 1:
                        pos += 1
            has_tile = (f1 >> 1) & 1
            has_wall = (f1 >> 2) & 1
            liquid = (f1 >> 3) & 3
            wide_id = (f1 >> 5) & 1
            rle = ((f1 >> 7) & 1) * 2 + ((f1 >> 6) & 1)

            tid = -1
            wid = -1
            if has_tile:
                if wide_id:
                    tid = d[pos] | (d[pos + 1] << 8); pos += 2
                else:
                    tid = d[pos]; pos += 1
                if tid <= max_imp and important[tid]:
                    pos += 4                     # frame u, v
                if (f3 >> 3) & 1:
                    pos += 1                     # tile paint
            if has_wall:
                wid = d[pos]; pos += 1
                if (f3 >> 4) & 1:
                    pos += 1                     # wall paint
            # 1..3 = water / lava / honey, and bit 23 upgrades honey to shimmer. The .map liquid
            # palette keeps the same four slots in that order, so `lkind` is the palette offset.
            shimmer = bool((f3 >> 7) & 1)
            lkind = 3 if shimmer else (liquid - 1 if liquid else 0)
            lamount = 0
            if liquid or shimmer:
                lamount = d[pos]; pos += 1       # amount
            if (f3 >> 6) & 1:
                wid |= d[pos] << 8; pos += 1     # wall id high byte

            mult = 1
            if rle == 2:
                mult = (d[pos] | (d[pos + 1] << 8)) + 1; pos += 2
            elif rle == 1:
                mult = d[pos] + 1; pos += 1
            if mult > height - col:
                mult = height - col

            tiles[x, col:col + mult] = tid
            walls[x, col:col + mult] = wid
            if lamount:
                liquids[x, col:col + mult] = lamount
                liquid_kinds[x, col:col + mult] = lkind
            col += mult

    if pos != end:
        raise ValueError(f"tile stream ended at {pos}, expected {end}")

    return World(name=name, world_id=world_id, width=width, height=height,
                 surface=float(surface), rock_layer=float(rock),
                 tiles=tiles, walls=walls, guid_bytes=guid_bytes,
                 liquids=liquids, liquid_kinds=liquid_kinds)


# --------------------------------------------------------------------------- metadata
#
# Field order below is taken verbatim from the 1.4.5.8 save routine
# (Terraria.IO.WorldFile.SaveWorld, writer.Write sequence). It exists so the UI can
# show the same rows the in-game world list shows without loading 11.5M tiles.

DIFFICULTY_LABELS = {0: "普通", 1: "专家", 2: "大师", 3: "旅行"}
# For the Worthy (FTW) bumps the displayed difficulty one tier: classic->expert,
# expert->master, master->legendary. The GameMode field itself is NOT changed by FTW.
FTW_TIER_LABELS = {0: "普通", 1: "专家", 2: "大师", 3: "传奇"}
SIZE_LABELS = {4200: "小世界", 6400: "中世界", 8400: "大世界", 17600: "大世界"}


def difficulty_label(game_mode: int, for_the_worthy: bool) -> str:
    if game_mode == 3:                     # journey is its own axis, FTW does not shift it
        return "旅行"
    if for_the_worthy:
        return FTW_TIER_LABELS.get(min(3, max(0, game_mode) + 1), f"未知({game_mode}+FTW)")
    return DIFFICULTY_LABELS.get(game_mode, f"未知({game_mode})")


def read_meta(path: str) -> dict:
    """Parse the world header only -> the fields the in-game world list displays."""
    with open(path, "rb") as fh:
        d = fh.read()

    fmt = struct.unpack_from("<i", d, 0)[0]
    if d[4:11] != b"relogic":
        raise ValueError("not a Terraria world file")
    nsec = struct.unpack_from("<h", d, 24)[0]
    offsets = struct.unpack_from(f"<{nsec}i", d, 26)

    s1 = offsets[0]
    name, p = _read_string7(d, s1)
    seed, p = _read_string7(d, p)
    wgen_version = struct.unpack_from("<q", d, p)[0]; p += 8
    guid = d[p:p + 16].hex(); p += 16
    world_id = struct.unpack_from("<i", d, p)[0]; p += 4
    left, right, top, bottom = struct.unpack_from("<iiii", d, p); p += 16
    height = struct.unpack_from("<i", d, p)[0]
    width = struct.unpack_from("<i", d, p + 4)[0]
    p += 8
    game_mode = struct.unpack_from("<i", d, p)[0]; p += 4

    def booleans(n: int) -> list[bool]:
        nonlocal p
        out = [bool(d[p + i]) for i in range(n)]
        p += n
        return out

    seed_flags = booleans(9)      # drunk, getGood, tenthAnniv, dontStarve, notTheBees,
                                  # remix, noTraps, zenith, skyblock
    creation = struct.unpack_from("<q", d, p)[0]; p += 8
    p += 8                        # saved-at timestamp
    moon_type = d[p]; p += 1
    p += 12                       # treeX[3]
    p += 16                       # treeStyle[4]
    p += 12                       # caveBackX[3]
    p += 16                       # caveBackStyle[4]
    p += 12                       # ice / jungle / hell back style
    spawn_x = struct.unpack_from("<i", d, p)[0]
    spawn_y = struct.unpack_from("<i", d, p + 4)[0]
    p += 8
    world_surface, rock_layer = struct.unpack_from("<dd", d, p); p += 16
    p += 8                        # time of day (double)
    daytime = bool(d[p]); p += 1
    moon_phase = struct.unpack_from("<i", d, p)[0]; p += 4
    blood_moon, eclipse = booleans(2)
    dungeon_x, dungeon_y = struct.unpack_from("<ii", d, p); p += 8
    crimson = bool(d[p]); p += 1

    # boss / progression flags, in save order
    boss_names = ["downed_king_slime", "downed_eye", "downed_eater", "downed_queen_bee",
                  "downed_skeletron", "downed_mech_1", "downed_mech_2", "downed_mech_any",
                  "downed_plant", "downed_golem", "downed_slime_king", "saved_goblin",
                  "saved_wizard", "saved_mech", "downed_goblins", "downed_clown",
                  "downed_frost", "downed_pirates"]
    # NOTE: exact order matches MapHelper-era save code; values only used for display.
    flags = booleans(4)           # downedBoss1..3 + queenBee
    flags += booleans(5)          # mech1..3 + mechAny + plant
    flags += booleans(9)          # golem, slimeKing, savedGoblin, savedWizard, savedMech,
                                  # goblins, clown, frost, pirates
    shadow_orb_smashed, spawn_meteor = booleans(2)
    shadow_orb_count = d[p]; p += 1
    altar_count = struct.unpack_from("<i", d, p)[0]; p += 4
    hard_mode = bool(d[p]); p += 1
    after_party = bool(d[p]); p += 1
    invasion_delay, invasion_size, invasion_type = struct.unpack_from("<iii", d, p); p += 12
    invasion_x = struct.unpack_from("<d", d, p)[0]; p += 8
    slime_rain = struct.unpack_from("<d", d, p)[0]; p += 8
    sundial = d[p]; p += 1
    raining = bool(d[p]); p += 1
    rain_time = struct.unpack_from("<i", d, p)[0]; p += 4
    max_rain = struct.unpack_from("<f", d, p)[0]; p += 4
    ore_c, ore_m, ore_a = struct.unpack_from("<iii", d, p); p += 12
    p += 8                        # 8 background style bytes
    p += 4                        # cloudBGActive int
    p += 2                        # numClouds int16
    p += 4                        # windSpeedTarget float
    n_angler = struct.unpack_from("<i", d, p)[0]; p += 4
    for _ in range(max(0, n_angler)):
        _s, p = _read_string7(d, p)
    saved_angler = bool(d[p]); p += 1
    angler_quest = struct.unpack_from("<i", d, p)[0]; p += 4
    saved_stylist, saved_tax, saved_golfer = booleans(3)
    p += 4                        # invasionSizeStart
    p += 4                        # cultist delay
    n_banners = struct.unpack_from("<h", d, p)[0]; p += 2
    p += 4 * max(0, n_banners)     # BannerSystem kill counts
    n_claims = struct.unpack_from("<h", d, p)[0]; p += 2
    p += 2 * max(0, n_claims)      # BannerSystem claimable banners (version >= 289)
    fast_forward = bool(d[p]); p += 1
    downed_fishron, downed_martians, downed_cultist, downed_moonlord = booleans(4)

    try:
        from datetime import datetime, timedelta, timezone
        kind = (creation >> 62) & 3          # DateTime.ToBinary: top 2 bits = Kind
        ticks = creation & 0x3FFFFFFFFFFFFFFF  # low 62 bits = ticks
        created = datetime(1, 1, 1) + timedelta(microseconds=ticks / 10)
        if kind in (1, 2):                   # Utc / Local both stored as UTC ticks here
            created = created.replace(tzinfo=timezone.utc).astimezone().replace(tzinfo=None)
    except Exception:             # noqa: BLE001
        created = None

    return {
        "name": name,
        "seed": seed,
        "world_id": world_id,
        "guid": guid,
        "width": width,
        "height": height,
        "size_label": SIZE_LABELS.get(width, f"{width} 宽"),
        "game_mode": game_mode,
        "difficulty_label": difficulty_label(game_mode, seed_flags[1]),
        "for_the_worthy": seed_flags[1],
        "evil": "猩红" if crimson else "腐化",
        "hard_mode": hard_mode,
        "defeated_moonlord": downed_moonlord,
        "spawn": (spawn_x, spawn_y),
        "world_surface": world_surface,
        "rock_layer": rock_layer,
        "dungeon": (dungeon_x, dungeon_y),
        "moon_phase": moon_phase,
        "day_time": daytime,
        "creation_time": created,
        "seed_flags": dict(zip(
            ["drunk", "get_good", "tenth_anniversary", "dont_starve", "not_the_bees",
             "remix", "no_traps", "zenith", "skyblock"], seed_flags)),
        "progression": dict(zip(boss_names + ["shadow_orb", "meteor"], flags)),
    }
