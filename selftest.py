# SPDX-License-Identifier: GPL-3.0-or-later

"""
selftest.py — sanity checks for the core reader/writer.

Runs without any game file: it parses a synthetic world/map pair, exercises the
palette maths, and (if a real pair is given on the command line) validates the
known invariants:

    python selftest.py
    python selftest.py ..\\live.wld ..\\map1_orig_fromdevice.bin

Exit code 0 = all checks passed.
"""
from __future__ import annotations

import struct
import sys
import tempfile
import os
import zlib

import numpy as np

import mapfmt
import worldfmt


def check(cond: bool, msg: str) -> bool:
    print(("ok   " if cond else "FAIL ") + msg)
    return cond


def palette_math() -> bool:
    hdr = mapfmt.MapHeader(
        raw=b"", world_name="t", world_id=1, width=128, height=128,
        max_tile_id=4, max_wall_id=3,
        tile_counts=[10, 20, 30, 40], wall_counts=[7, 8, 9],
    )
    good = check(hdr.tile_base == 1, f"tile base is 1 (got {hdr.tile_base})")
    good &= check(hdr.tile_type(0) == 1 and hdr.tile_type(1) == 11,
                  f"tile prefix 1-based ({hdr.tile_type(0)}, {hdr.tile_type(1)})")
    good &= check(hdr.wall_base == 1 + 100, f"wall base ({hdr.wall_base})")
    good &= check(hdr.liquid_base == hdr.wall_base + 24, f"liquid base ({hdr.liquid_base})")
    good &= check(hdr.sky_base == hdr.liquid_base + 4, f"sky base ({hdr.sky_base})")
    good &= check(hdr.dirt_base == hdr.sky_base + 256, f"dirt base ({hdr.dirt_base})")
    good &= check(hdr.rock_base == hdr.dirt_base + 256, f"rock base ({hdr.rock_base})")
    good &= check(hdr.hell_base == hdr.rock_base + 256, f"hell base ({hdr.hell_base})")
    good &= check(hdr.sky_type(0, 500.0) == hdr.sky_base, "sky index at y=0")
    good &= check(hdr.sky_type(250, 500.0) == hdr.sky_base + 127, "sky index at y=surface/2")
    return good


def roundtrip_synthetic() -> bool:
    """Build a tiny map from scratch, write it, read it back, compare."""
    w = h = 70                                    # deliberately not a multiple of 64
    hdr = mapfmt.MapHeader(raw=b"", world_name="synthetic", world_id=7, width=w, height=h,
                           max_tile_id=8, max_wall_id=4,
                           tile_counts=[1] * 8, wall_counts=[1] * 4)
    hdr.raw = (struct.pack("<i", mapfmt.FILE_VERSION) + b"relogic" + bytes([1]) +
               struct.pack("<I", mapfmt.REVISION) + b"\0" * 8 +
               bytes([len("synthetic")]) + b"synthetic" +
               struct.pack("<iii", 7, h, w) +
               struct.pack("<hh", 8, 4) + struct.pack("<hhhh", 4, 256, 256, 256) +
               b"\0\0")                        # 8 tile bits = 1 byte, 4 wall bits = 1 byte;
                                               # all flags false -> counts implicit 1
    types = (np.arange(w)[:, None] + np.arange(h)[None, :]).astype(np.uint16) % 12
    lights = np.full((w, h), 255, dtype=np.uint8)
    colors = np.full((w, h), 2, dtype=np.uint8)

    with tempfile.TemporaryDirectory() as td:
        path = os.path.join(td, "x.map")
        size = mapfmt.write_map(path, hdr, types, lights, colors)
        hdr2, t2, l2, c2 = mapfmt.read_map(path)
        good = check(size > 0, f"wrote {size} bytes")
        good &= check((hdr2.width, hdr2.height) == (w, h), "header round-trips size")
        good &= check(np.array_equal(t2, types), "types round-trip")
        good &= check(np.array_equal(l2, lights), "lights round-trip")
        good &= check(np.array_equal(c2, colors), "colors round-trip")
        good &= check(mapfmt.chunk_grid(w, h) == (2, 2), "chunk grid for 70x70 is 2x2")
    return good


def real_files(world_path: str, map_path: str) -> bool:
    world = worldfmt.load(world_path)
    hdr, types, lights, _ = mapfmt.read_map(map_path)
    good = check(world.world_id == hdr.world_id, f"worldID matches ({world.world_id})")
    good &= check((world.width, world.height) == (hdr.width, hdr.height),
                  f"size matches ({world.width}x{world.height})")
    solid = int((world.tiles >= 0).sum())
    print(f"info solid tiles = {solid}, walls = {int((world.walls >= 0).sum())}")
    good &= check(0 < solid < world.width * world.height, "world tile array populated")

    with tempfile.TemporaryDirectory() as td:
        out = os.path.join(td, "reveal.map")
        t, l, c = mapfmt.build_arrays(hdr, world, light=255)
        mapfmt.write_map(out, hdr, t, l, c)
        h2, t2, l2, _ = mapfmt.read_map(out)
        good &= check(int((l2 > 18).sum()) == world.width * world.height, "every cell explored")
        good &= check(np.array_equal(t2, t), "generated types round-trip")
    return good


def main(argv: list[str]) -> int:
    ok = palette_math() and roundtrip_synthetic()
    if len(argv) >= 3:
        ok = real_files(argv[1], argv[2]) and ok
    print("\nRESULT:", "PASS" if ok else "FAIL")
    return 0 if ok else 1


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
