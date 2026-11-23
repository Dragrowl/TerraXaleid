# SPDX-License-Identifier: GPL-3.0-or-later

import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import worldfmt

for f in (r"..\live.wld", r"..\all.wld"):
    m = worldfmt.read_meta(f)
    hard = "肉山后" if m["hard_mode"] else "未肉山"
    moon = "月总后" if m["defeated_moonlord"] else "未月总"
    when = m["creation_time"].strftime("%Y-%m-%d %H:%M") if m["creation_time"] else "?"
    print(f'{m["name"]} | {m["difficulty_label"]} | {m["size_label"]} | {m["evil"]} | {hard} | {moon} | {when}')
