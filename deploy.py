# SPDX-License-Identifier: GPL-3.0-or-later

"""
deploy.py — push a generated .map to a MuMu/Android device and restore ownership.

The map file lives on shared storage, so a plain `adb push` leaves it owned by the
shell user and the game silently ignores it. This script does the chown/chmod and
optionally force-stops the game first (it must be stopped, otherwise the game
rewrites the file from memory when it exits).

Usage:
    python deploy.py OUT.map /sdcard/.../Players/鸡/<worldguid>.map \
        [--adb "F:\\Program Files\\Netease\\MuMu\\nx_device\\15.0\\shell\\adb.exe"] \
        [--serial 127.0.0.1:7555] [--package com.and.games505.TerrariaPaid] [--no-stop]
"""
from __future__ import annotations

import argparse
import subprocess
import sys

DEFAULT_ADB = r"F:\Program Files\Netease\MuMu\nx_device\15.0\shell\adb.exe"
DEFAULT_SERIAL = "127.0.0.1:7555"
DEFAULT_PKG = "com.and.games505.TerrariaPaid"


def sh(adb: str, serial: str, *args: str, check: bool = True) -> str:
    cmd = [adb, "-s", serial, *args]
    res = subprocess.run(cmd, capture_output=True, text=True)
    if check and res.returncode != 0:
        raise RuntimeError(f"{' '.join(cmd)} -> {res.returncode}\n{res.stderr}{res.stdout}")
    return res.stdout.strip()


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("local")
    ap.add_argument("remote")
    ap.add_argument("--adb", default=DEFAULT_ADB)
    ap.add_argument("--serial", default=DEFAULT_SERIAL)
    ap.add_argument("--package", default=DEFAULT_PKG)
    ap.add_argument("--owner", default="u0_a72:ext_data_rw")
    ap.add_argument("--no-stop", action="store_true")
    args = ap.parse_args(argv)

    try:
        sh(args.adb, args.serial, "root")
        if not args.no_stop:
            sh(args.adb, args.serial, "shell", "am", "force-stop", args.package)
        print(sh(args.adb, args.serial, "push", args.local, args.remote))
        sh(args.adb, args.serial, "shell",
           f"chown {args.owner} '{args.remote}'; chmod 660 '{args.remote}'; ls -l '{args.remote}'",
           check=False)
        print(sh(args.adb, args.serial, "shell", "ls", "-l", args.remote))
    except Exception as exc:                     # noqa: BLE001 - surfaced to the user
        print(f"deploy failed: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
