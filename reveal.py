# SPDX-License-Identifier: GPL-3.0-or-later

"""
reveal.py — CLI: fully reveal a Terraria world map by rewriting .map files.

Terraria stores exploration per (world, character): `Players/<character>/<world>.map`. So a
reveal is per character, and by default this tool does every character found on the machine
(`--players-dir`), because only the characters you write for will see the revealed map.

The world file is only read; nothing in the world changes.

Usage — single file:
    python reveal.py WORLD.wld TEMPLATE.map -o OUT.map [--light 255] [--dry-run]

Usage — every character under a Players directory (the normal case):
    python reveal.py WORLD.wld --players-dir "…\\Terraria\\Players"
    python reveal.py WORLD.wld --players-dir "…\\Players" --player classic --player Marina
    python reveal.py WORLD.wld --players-dir "…\\Players" --list
    python reveal.py WORLD.wld --players-dir "…\\Players" --dry-run

Exit codes: 0 ok, 2 bad arguments, 3 validation failure.
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

import mapfmt
import players as players_mod
import worldfmt


def _print_world(world: worldfmt.World) -> None:
    print(f"world   : {world.name!r} id={world.world_id} {world.width}x{world.height} "
          f"surface={world.surface:g} rock={world.rock_layer:g} underworld={world.underworld_layer}")


def _print_palette(hdr: mapfmt.MapHeader) -> None:
    print(f"palette : tile={hdr.tile_base} wall={hdr.wall_base} liquid={hdr.liquid_base} "
          f"sky={hdr.sky_base} dirt={hdr.dirt_base} rock={hdr.rock_base} hell={hdr.hell_base}")


def _reveal_one(world: worldfmt.World, template: Path, out: Path, args: argparse.Namespace) -> int:
    hdr, old_types, old_lights, _old_colors = mapfmt.read_map(str(template))

    if (hdr.width, hdr.height) != (world.width, world.height):
        print(f"error: template {template.name} is {hdr.width}x{hdr.height}, world is "
              f"{world.width}x{world.height}", file=sys.stderr)
        return 3
    if not args.no_template_check and hdr.world_id != world.world_id:
        print(f"error: template {template.name} worldID {hdr.world_id} != world id {world.world_id}",
              file=sys.stderr)
        return 3

    types, lights, colors = mapfmt.build_arrays(hdr, world, light=args.light)
    if args.keep_explored_type:
        seen = old_lights > 18
        types = old_types.copy()
        types[~seen] = mapfmt.build_arrays(hdr, world, light=args.light)[0][~seen]

    if args.dry_run:
        print(f"dry run : would write {out} ({world.width * world.height} cells)")
        return 0

    out.parent.mkdir(parents=True, exist_ok=True)
    size = mapfmt.write_map(str(out), hdr, types, lights, colors)

    hdr2, _t2, l2, _ = mapfmt.read_map(str(out))
    if (hdr2.width, hdr2.height) != (world.width, world.height):
        print(f"error: round-trip size mismatch in {out}", file=sys.stderr)
        return 3
    unseen = int((l2 <= 18).sum())
    flag = "ok" if unseen == 0 else "FAIL"
    print(f"written : {out} ({size} bytes) unexplored={unseen} [{flag}]")
    return 0 if unseen == 0 else 3


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description="Reveal every tile of a Terraria world map.")
    ap.add_argument("world", help="path to the .wld file (read only)")
    ap.add_argument("template", nargs="?", default=None,
                    help="existing .map of the same world; optional when --players-dir can supply one")
    ap.add_argument("-o", "--out", default=None, help="output .map (single-file mode)")
    ap.add_argument("--players-dir", default=None,
                    help="a Terraria Players directory; reveals for every character under it")
    ap.add_argument("--player", action="append", default=None, metavar="NAME",
                    help="restrict to this character (repeatable); default is every character")
    ap.add_argument("--list", action="store_true", help="print the character names and exit")
    ap.add_argument("--light", type=int, default=255, help="light value for every tile (default 255)")
    ap.add_argument("--keep-explored-type", action="store_true",
                    help="keep the Type already stored for tiles the game had explored")
    ap.add_argument("--no-template-check", action="store_true",
                    help="do not verify that the template belongs to this world")
    ap.add_argument("--dry-run", action="store_true", help="build and verify, do not write")
    args = ap.parse_args(argv)

    # --list prints the character names and nothing else, so it runs before the world is touched.
    if args.list:
        if not args.players_dir:
            print("error: --list needs --players-dir", file=sys.stderr)
            return 2
        for p in players_mod.list_players(args.players_dir):
            print(p.name)
        return 0

    world = worldfmt.load(args.world)
    _print_world(world)

    if args.players_dir:
        all_players = players_mod.list_players(args.players_dir)
        if args.list:
            for p in all_players:
                print(p.name)
            return 0

        map_name = players_mod.guid_to_map_name(world.guid_bytes) + ".map"
        template = players_mod.find_template(all_players, world.guid_bytes)
        if template is None:
            template = Path(args.template) if args.template else None
        if template is None:
            print("error: no existing .map for this world and no template given; pass one to create "
                  "the first map", file=sys.stderr)
            return 2

        try:
            chosen = players_mod.select(all_players, args.player)
        except KeyError as exc:
            print(f"error: {exc.args[0]}", file=sys.stderr)
            return 2
        if not chosen:
            print("error: no characters found", file=sys.stderr)
            return 2

        hdr, *_ = mapfmt.read_map(str(template))
        print(f"template: {template}")
        print(f"map file: {map_name}")
        _print_palette(hdr)
        print(f"players : {len(chosen)} of {len(all_players)} "
              f"({', '.join(p.name for p in chosen)})")

        worst = 0
        for p in chosen:
            out = p.directory / map_name
            existed = out.exists()
            rc = _reveal_one(world, template, out, args)
            worst = max(worst, rc)
            if rc == 0 and not existed:
                print(f"          (created a new map for {p.name!r})")
        return worst

    if not args.template or not args.out:
        print("error: single-file mode needs TEMPLATE and -o, or use --players-dir", file=sys.stderr)
        return 2

    hdr, *_ = mapfmt.read_map(args.template)
    print(f"template: {args.template}")
    _print_palette(hdr)
    total = world.width * world.height
    solid = int((world.tiles >= 0).sum())
    walled = int(((world.tiles < 0) & (world.walls >= 0)).sum())
    print(f"cells   : {total} (solid {solid}, wall-only {walled}, background {total - solid - walled})")
    return _reveal_one(world, Path(args.template), Path(args.out), args)


if __name__ == "__main__":
    raise SystemExit(main())
