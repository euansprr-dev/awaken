#!/usr/bin/env python3
"""The fight recorder's table (SPEC §48.7). Reads a GameTest log, local or CI, and prints what each boss did against each
kind of player. Each row is an average of the fights it covers.

  python3 tools/fights/record_table.py logs/gradle-<stamp>-runGameTestServer.log
  gh run view <id> --repo euansprr-dev/awaken --log | python3 tools/fights/record_table.py -
  ... --by boss      one row per boss rather than per kind of player
  ... --gates        also the plan's gates (docs/fights/read-you.html), for the fights given with --boss
"""
import argparse
import re
import sys
from collections import defaultdict

LINE = re.compile(r"\[record\] boss=(\S+) player=(\S+) ticks=(\d+) begun=(\d+) repeats=(\d+) playing=(\d+) opening=(\d+) idle=(\d+) walking=(\d+) sideways=(\d+) tags=\{(.*?)\}")
PACK = re.compile(r"\[record-pack\] (.*)$")
TAGS = ["gap", "near", "ranged", "area", "combo", "behind", "summon"]
CLASSES = ["LIGHT", "HEAVY", "PERILOUS", "GRAB", "NONE"]


def read(path):
    text = sys.stdin.read() if path == "-" else open(path, errors="replace").read()
    rows, packs = {}, []
    for line in text.splitlines():
        m = LINE.search(line)
        if m:
            boss, kind, *nums, tags = m.groups()
            r = dict(zip(("ticks", "begun", "repeats", "playing", "opening", "idle", "walking", "sideways"), map(int, nums)))
            r["tags"] = {k: int(v) for k, v in (t.split("=") for t in tags.split(", ") if t)}
            rows[(boss, kind)] = r
            continue
        m = PACK.search(line)
        if m:
            packs.append(m.group(1))
    return rows, packs


def pct(a, b):
    return 100.0 * a / b if b else 0.0


def table(rows, key):
    groups = defaultdict(list)
    for (boss, kind), r in rows.items():
        groups[boss if key == "boss" else kind].append(r)
    print(f"{key:20s} fights begun rep% | play open idle walk side | " + " ".join(f"{t[:6]:>6s}" for t in TAGS) + " | " + " ".join(f"{c[:6]:>6s}" for c in CLASSES))
    def row(name, rs):
        ticks, begun = sum(r["ticks"] for r in rs), sum(r["begun"] for r in rs)
        tag = lambda t: pct(sum(r["tags"].get(t, 0) for r in rs), begun)
        print(f"{name:20s} {len(rs):6d} {begun:5d} {pct(sum(r['repeats'] for r in rs), begun):4.0f} | "
              + " ".join(f"{pct(sum(r[k] for r in rs), ticks):4.0f}" for k in ("playing", "opening", "idle", "walking", "sideways"))
              + " | " + " ".join(f"{tag(t):6.0f}" for t in TAGS) + " | " + " ".join(f"{tag('c_' + c):6.0f}" for c in CLASSES))
    for name in sorted(groups):
        row(name, groups[name])
    row("ALL", list(rows.values()))


def gates(rows, boss):
    get = lambda kind: rows.get((boss, kind))
    every = [r for (b, _), r in rows.items() if b == boss]
    if not every:
        print(f"{boss}: no fights")
        return
    worst = max(pct(r["repeats"], r["begun"]) for r in every)
    moving = pct(sum(r["walking"] + r["sideways"] for r in every), sum(r["ticks"] for r in every))
    stand, guard = get("stand"), get("guard")
    breakers = lambda r: pct(r["tags"].get("c_PERILOUS", 0) + r["tags"].get("c_GRAB", 0), r["begun"])
    print(f"{boss}:")
    print(f"  same move twice in a row, worst fight   {worst:5.1f}%   gate 10%")
    if stand:
        print(f"  mid-move against someone standing still {pct(stand['playing'], stand['ticks']):5.1f}%   gate 50-60%")
    print(f"  walking or stepping                     {moving:5.1f}%   gate 20%")
    if stand and guard:
        print(f"  guard-beaters: turtling / standing      {breakers(guard):5.1f}% / {breakers(stand):.1f}%   gate double")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("log")
    ap.add_argument("--by", choices=("player", "boss"), default="player")
    ap.add_argument("--boss", default="hive_regent,gearwright_colossus")
    ap.add_argument("--gates", action="store_true")
    a = ap.parse_args()
    rows, packs = read(a.log)
    if not rows and not packs:
        sys.exit("no [record] lines in that log")
    if rows:
        table(rows, a.by)
    for p in packs:
        print("pack:", p)
    if a.gates:
        for b in a.boss.split(","):
            gates(rows, b)


if __name__ == "__main__":
    main()
