# SPDX-License-Identifier: GPL-3.0-or-later

"""
mapfmt.py — Terraria map file (.map) reader / writer, and the reveal generator.

File layout (mobile & PC 1.4.5.x, revision 68, fileType 1):

    header (variable length, 293 bytes for the sample world):
        int32  33094
        char[7] "relogic"
        byte   fileType = 1
        uint32 revision
        uint64 favorite
        string worldName        (7-bit length prefix + UTF-8)
        int32  worldID
        int32  height
        int32  width
        int16  maxTileID / maxWallID / liquidTypes / skyGradients
               / dirtGradients / rockGradients        (6 int16)
        bool[] tileOptionFlags (maxTileID bits, MSB first)
        bool[] wallOptionFlags (maxWallID bits, MSB first)
        byte[] tileOptionCounts   (1 byte per flagged tile id, else implicit 1)
        byte[] wallOptionCounts

    then ceil(width/64) * ceil(height/64) chunks, row-major over the chunk grid:
        int32 length            0 => the whole chunk is empty (all Type 0)
        byte[length]            zlib stream that inflates to 16384 bytes
                                = 4096 * MapTile{ ushort Type; byte Light; byte Color; }

    inside a chunk: index n -> x = chunkX*64 + (n % 64), y = chunkY*64 + (n / 64)
    i.e. column-major inside the 64x64 block.

    A tile counts as explored when Light > 18; untouched tiles are Type 0 / Light 0.

Type numbering (the map palette index) is 1-based and derived from the option counts:

    tile   : 1    + prefixTile[tileID]
    wall   : 1 + sum(tile counts) + prefixWall[wallID]
    liquid : + 4 entries (water, lava, honey, shimmer)
    sky    : + 256 gradient entries
    dirt   : + 256 (index 0 normal, 255 snow)
    rock   : + 256 (index 0 normal, 255 snow)
    hell   : single entry

    background for an empty cell (MapHelper.GetBackgroundType):
        y <  worldSurface                    -> sky gradient, index = byte(255*y/worldSurface)
        y <  rockLayer                       -> dirt + (255 if snowy else 0)
        y <  underworldLayer (= height - 200)-> rock + (255 if snowy else 0)
        else                                 -> hell
"""
from __future__ import annotations

import struct
import zlib
from dataclasses import dataclass, field

import numpy as np

REVISION = 68
FILE_VERSION = 33094


@dataclass
class MapHeader:
    raw: bytes
    world_name: str
    world_id: int
    width: int
    height: int
    max_tile_id: int
    max_wall_id: int
    tile_counts: list[int] = field(default_factory=list)
    wall_counts: list[int] = field(default_factory=list)

    # ---- palette bases, derived from the option counts ----
    _tile_prefix: list[int] = field(default_factory=list)
    _wall_prefix: list[int] = field(default_factory=list)

    def __post_init__(self) -> None:
        self._tile_prefix = [0] * (self.max_tile_id + 1)
        for i, c in enumerate(self.tile_counts):
            if i + 1 < len(self._tile_prefix):
                self._tile_prefix[i + 1] = self._tile_prefix[i] + c
        self._wall_prefix = [0] * (self.max_wall_id + 1)
        for i, c in enumerate(self.wall_counts):
            if i + 1 < len(self._wall_prefix):
                self._wall_prefix[i + 1] = self._wall_prefix[i] + c

    # tile/wall/liquid/sky/dirt/rock/hell bases
    @property
    def tile_base(self) -> int:
        return 1

    @property
    def wall_base(self) -> int:
        return self.tile_base + sum(self.tile_counts)

    @property
    def liquid_base(self) -> int:
        return self.wall_base + sum(self.wall_counts)

    @property
    def sky_base(self) -> int:
        return self.liquid_base + 4

    @property
    def dirt_base(self) -> int:
        return self.sky_base + 256

    @property
    def rock_base(self) -> int:
        return self.dirt_base + 256

    @property
    def hell_base(self) -> int:
        return self.rock_base + 256

    def tile_type(self, tile_id: int) -> int:
        if 0 <= tile_id < len(self._tile_prefix):
            return self.tile_base + self._tile_prefix[tile_id]
        return self.tile_base

    def wall_type(self, wall_id: int) -> int:
        if 0 <= wall_id < len(self._wall_prefix):
            return self.wall_base + self._wall_prefix[wall_id]
        return self.wall_base

    def sky_type(self, y: int, surface: float) -> int:
        if surface <= 0:
            return self.sky_base
        return self.sky_base + (int(255.0 * y / surface) & 0xFF)


# --------------------------------------------------------------------------- read

def _read_string7(buf: bytes, pos: int) -> tuple[str, int]:
    length = 0
    shift = 0
    while True:
        b = buf[pos]; pos += 1
        length |= (b & 0x7F) << shift
        if not b & 0x80:
            break
        shift += 7
    return buf[pos:pos + length].decode("utf-8"), pos + length


def read_header(data: bytes) -> tuple[MapHeader, int]:
    pos = 0
    version = struct.unpack_from("<i", data, pos)[0]; pos += 4
    if version != FILE_VERSION:
        raise ValueError(f"unsupported map file version {version} (expected {FILE_VERSION})")
    if data[pos:pos + 7] != b"relogic":
        raise ValueError("not a Terraria map file")
    pos += 7
    file_type = data[pos]; pos += 1
    if file_type != 1:
        raise ValueError(f"unexpected fileType {file_type}")
    revision = struct.unpack_from("<I", data, pos)[0]; pos += 4
    pos += 8                                  # favorite (uint64)
    world_name, pos = _read_string7(data, pos)
    world_id = struct.unpack_from("<i", data, pos)[0]; pos += 4
    height = struct.unpack_from("<i", data, pos)[0]; pos += 4
    width = struct.unpack_from("<i", data, pos)[0]; pos += 4
    max_tile_id, max_wall_id = struct.unpack_from("<hh", data, pos); pos += 4
    pos += 8                                  # liquid / sky / dirt / rock gradient counts

    def bits(n: int) -> list[bool]:
        nonlocal pos
        out = [False] * n
        cur = 0
        mask = 128
        for i in range(n):
            if mask == 128:
                cur = data[pos]; pos += 1; mask = 1
            else:
                mask <<= 1
            if cur & mask == mask:
                out[i] = True
        return out

    tflags = bits(max_tile_id)
    wflags = bits(max_wall_id)
    tcounts = []
    for i in range(max_tile_id):
        if tflags[i]:
            tcounts.append(data[pos]); pos += 1
        else:
            tcounts.append(1)
    wcounts = []
    for i in range(max_wall_id):
        if wflags[i]:
            wcounts.append(data[pos]); pos += 1
        else:
            wcounts.append(1)

    hdr = MapHeader(raw=data[:pos], world_name=world_name, world_id=world_id,
                    width=width, height=height, max_tile_id=max_tile_id,
                    max_wall_id=max_wall_id, tile_counts=tcounts, wall_counts=wcounts)
    return hdr, pos


def chunk_grid(width: int, height: int) -> tuple[int, int]:
    return (width + 63) // 64, (height + 63) // 64


def read_map(path: str) -> tuple[MapHeader, np.ndarray, np.ndarray, np.ndarray]:
    """Returns (header, types, lights, colors) with arrays shaped (width, height)."""
    with open(path, "rb") as fh:
        data = fh.read()
    hdr, pos = read_header(data)
    w, h = hdr.width, hdr.height
    types = np.zeros((w, h), dtype=np.uint16)
    lights = np.zeros((w, h), dtype=np.uint8)
    colors = np.zeros((w, h), dtype=np.uint8)
    ncx, ncy = chunk_grid(w, h)
    idx = np.arange(4096)
    for c in range(ncx * ncy):
        n = struct.unpack_from("<i", data, pos)[0]; pos += 4
        if n <= 0:
            continue
        raw = zlib.decompress(data[pos:pos + n]); pos += n
        if len(raw) != 16384:
            raise ValueError(f"chunk {c} inflated to {len(raw)} bytes, expected 16384")
        a = np.frombuffer(raw, dtype=np.uint8).reshape(4096, 4)
        cx, cy = c % ncx, c // ncx
        x = cx * 64 + (idx % 64)
        y = cy * 64 + (idx // 64)
        ok = (x < w) & (y < h)
        types[x[ok], y[ok]] = a[ok, 0].astype(np.uint16) | (a[ok, 1].astype(np.uint16) << 8)
        lights[x[ok], y[ok]] = a[ok, 2]
        colors[x[ok], y[ok]] = a[ok, 3]
    return hdr, types, lights, colors


# -------------------------------------------------------------------------- write

def build_arrays(hdr: MapHeader, world, light: int = 255) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    """Turn a World into map Type/Light/Color arrays (does not modify the world)."""
    w, h = world.width, world.height
    if (w, h) != (hdr.width, hdr.height):
        raise ValueError(f"world is {w}x{h} but map header says {hdr.width}x{hdr.height}")

    tiles, walls = world.tiles, world.walls
    types = np.zeros((w, h), dtype=np.uint16)

    # background table, one entry per row
    bg = np.zeros(h, dtype=np.uint16)
    for y in range(h):
        if y < world.surface:
            bg[y] = hdr.sky_type(y, world.surface)
        elif y < world.rock_layer:
            bg[y] = hdr.dirt_base
        elif y < world.underworld_layer:
            bg[y] = hdr.rock_base
        else:
            bg[y] = hdr.hell_base

    tile_lut = np.zeros(max(1, hdr.max_tile_id), dtype=np.uint16)
    for tid in range(hdr.max_tile_id):
        tile_lut[tid] = hdr.tile_type(tid)
    wall_lut = np.zeros(max(1, hdr.max_wall_id), dtype=np.uint16)
    for wid in range(hdr.max_wall_id):
        wall_lut[wid] = hdr.wall_type(wid)

    t = tiles.astype(np.int32)
    wl = walls.astype(np.int32)
    safe_t = np.where((t >= 0) & (t < hdr.max_tile_id), t, 0)
    safe_w = np.where((wl >= 0) & (wl < hdr.max_wall_id), wl, 0)

    types = np.where(t >= 0, tile_lut[safe_t],
                     np.where(wl >= 0, wall_lut[safe_w], bg[None, :]))
    lights = np.full((w, h), light, dtype=np.uint8)
    colors = np.zeros((w, h), dtype=np.uint8)
    return types.astype(np.uint16), lights, colors


def write_map(path: str, hdr: MapHeader, types: np.ndarray, lights: np.ndarray,
              colors: np.ndarray, level: int = 9) -> int:
    """Serialise arrays into a .map file. Returns the number of bytes written."""
    w, h = hdr.width, hdr.height
    ncx, ncy = chunk_grid(w, h)
    idx = np.arange(4096)
    buf = bytearray(hdr.raw)
    for c in range(ncx * ncy):
        cx, cy = c % ncx, c // ncx
        x = cx * 64 + (idx % 64)
        y = cy * 64 + (idx // 64)
        ok = (x < w) & (y < h)
        block = np.zeros((4096, 4), dtype=np.uint8)
        t = types[x[ok], y[ok]]
        block[ok, 0] = t & 0xFF
        block[ok, 1] = (t >> 8) & 0xFF
        block[ok, 2] = lights[x[ok], y[ok]]
        block[ok, 3] = colors[x[ok], y[ok]]
        z = zlib.compress(block.tobytes(), level)
        buf += struct.pack("<i", len(z)) + z
    with open(path, "wb") as fh:
        fh.write(buf)
    return len(buf)
