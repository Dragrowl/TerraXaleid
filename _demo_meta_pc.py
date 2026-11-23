# SPDX-License-Identifier: GPL-3.0-or-later

import glob
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import worldfmt

D = os.path.expanduser(r"~\Documents\My Games\Terraria\Worlds")
rows = []
for f in sorted(glob.glob(os.path.join(D, "*.wld"))):
    try:
        m = worldfmt.read_meta(f)
    except Exception as exc:                      # noqa: BLE001
        rows.append((os.path.basename(f), f"ERROR {exc}", "", "", "", "", ""))
        continue
    when = m["creation_time"].strftime("%Y-%m-%d") if m["creation_time"] else "?"
    rows.append((
        m["name"][:22],
        m["difficulty_label"],
        m["size_label"],
        m["evil"],
        "肉山后" if m["hard_mode"] else "未肉山",
        "月总后" if m["defeated_moonlord"] else "未月总",
        f"GM={m['game_mode']} FTW={int(m['for_the_worthy'])} {when}",
    ))

for r in rows:
    print(" | ".join(f"{c:<12}" for c in r))
