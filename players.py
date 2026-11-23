# SPDX-License-Identifier: GPL-3.0-or-later

"""
players.py — per-character map bookkeeping.

Terraria keeps map exploration per (world, character) pair, not per world:

    Players/<character>.plr          the character file itself
    Players/<character>/<world>.map  everything that character has explored in that world

so the same world fills up with one .map per character who has ever visited it. Revealing
"the map" therefore means revealing it for one character or for all of them.

The <world> part of the file name is the world GUID printed the way .NET prints a Guid:
the first three groups of the 16 stored bytes are byte-swapped, the last two are not.

    stored bytes  e9 57 d5 c3 97 d3 79 42 a7 b4 6e 8d c3 2a df f1
    file name     c3d557e9-d397-4279-a7b4-6e8dc32adff1

This module is storage-agnostic: hand it a directory that contains `*.plr` plus one
sub-directory per character and it lists what is there.
"""
from __future__ import annotations

import os
import re
from dataclasses import dataclass, field
from pathlib import Path

_GUID_RE = re.compile(r"^[0-9a-f]{32}$")


def guid_to_map_name(raw_guid: bytes) -> str:
    """Format 16 raw GUID bytes the way Terraria names .map files (i.e. as .NET prints a Guid)."""
    if len(raw_guid) != 16:
        raise ValueError(f"a GUID is 16 bytes, got {len(raw_guid)}")
    b = raw_guid
    return (
        f"{b[3]:02x}{b[2]:02x}{b[1]:02x}{b[0]:02x}-"
        f"{b[5]:02x}{b[4]:02x}-"
        f"{b[7]:02x}{b[6]:02x}-"
        f"{b[8]:02x}{b[9]:02x}-"
        f"{b[10]:02x}{b[11]:02x}{b[12]:02x}{b[13]:02x}{b[14]:02x}{b[15]:02x}"
    )


def map_name_to_guid(name: str) -> bytes:
    """Inverse of [guid_to_map_name]: the .map file name back to the 16 stored bytes."""
    hexed = name.replace("-", "").lower()
    if not _GUID_RE.match(hexed):
        raise ValueError(f"not a GUID file name: {name!r}")
    b = bytes.fromhex(hexed)
    return bytes([b[3], b[2], b[1], b[0], b[5], b[4], b[7], b[6]]) + b[8:]


@dataclass(frozen=True)
class Player:
    """One character and the worlds it has a map for."""

    name: str
    directory: Path
    maps: dict[str, Path] = field(default_factory=dict)  # map file name -> path

    def map_for(self, world_guid: bytes) -> Path | None:
        return self.maps.get(guid_to_map_name(world_guid) + ".map")


def list_players(players_dir: str | os.PathLike[str]) -> list[Player]:
    """
    Every character directory under `players_dir`, sorted by name.

    A character is a sub-directory that carries at least one `.map`; the `.plr` next to it is
    what the game uses to name the character, but the directory name is the same string.
    """
    root = Path(players_dir)
    if not root.is_dir():
        raise FileNotFoundError(f"no such players directory: {root}")
    players: list[Player] = []
    for entry in sorted(root.iterdir(), key=lambda p: p.name.lower()):
        if not entry.is_dir():
            continue
        maps = {p.name: p for p in entry.glob("*.map")}
        if not maps and not entry.with_suffix(".plr").exists():
            continue
        players.append(Player(name=entry.name, directory=entry, maps=maps))
    return players


def find_template(players: list[Player], world_guid: bytes) -> Path | None:
    """
    Any existing `.map` of this world, from any character.

    All characters share the same option tables for a given world (they depend on which tile and
    wall types the world contains), so one character's map is a valid header template for
    creating another character's map from scratch.
    """
    wanted = guid_to_map_name(world_guid) + ".map"
    for player in players:
        path = player.maps.get(wanted)
        if path is not None:
            return path
    return None


def select(players: list[Player], wanted: list[str] | None) -> list[Player]:
    """
    `None` (or an empty list) means every character, which is the default for a reveal: the
    game keeps one map per character, so revealing "the map" only helps the characters you
    actually write for.

    Names are matched case-insensitively; an unknown name is an error rather than a silent
    no-op, because a typo would otherwise look like a successful reveal.
    """
    if not wanted:
        return list(players)
    by_lower = {p.name.lower(): p for p in players}
    missing = [w for w in wanted if w.lower() not in by_lower]
    if missing:
        known = ", ".join(p.name for p in players) or "<none>"
        raise KeyError(f"unknown character(s): {', '.join(missing)}; known: {known}")
    return [by_lower[w.lower()] for w in wanted]


def ensure_map_dir(player: Player) -> Path:
    """Create the character's map directory when the reveal is about to write the first map."""
    player.directory.mkdir(parents=True, exist_ok=True)
    return player.directory
