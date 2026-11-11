#!/usr/bin/env python3
"""Reads one fight out of a play log (planning harness; give it a COPY of the log, never commit a log).

The hands log every strike they played with the ticks since the last ("[arms] strikes played: [longsword] 1+12 2+6 ...",
"." a press that counted for nothing). This turns the last such line before --until into a timeline: strike moves a
minute, ripostes, Arts, Executions, and the long gaps (a pause, a weapon swapped).

Run:  python3 docs/boss-experience/harness/fight_read.py /path/to/copy/latest.log [--line N]
"""
import collections, sys

STRIKES = ("1", "2", "3", "4", "riposte", "art", "execution", "heavy", "slipcut", "undercut", "plunge")


def main():
    path = sys.argv[1]
    lines = [l for l in open(path, encoding="utf-8", errors="replace") if "strikes played:" in l]
    if not lines:
        sys.exit("no '[arms] strikes played' line in " + path)
    pick = int(sys.argv[sys.argv.index("--line") + 1]) if "--line" in sys.argv else max(range(len(lines)), key=lambda i: len(lines[i]))
    seq = lines[pick].split("strikes played: ")[1].split()
    t, ev, style = 0, [], "?"
    for tok in seq:
        if tok.startswith("["):
            style = tok.strip("[]")
            continue
        what, dt = tok.rsplit("+", 1)
        t += int(dt)
        ev.append((t, what))
    t0, t1 = ev[0][0], ev[-1][0]
    moves = [e for e in ev if e[1] in STRIKES]
    print(f"{style}: {len(moves)} strike moves over {(t1 - t0) / 20:.0f} s ({len(moves) / max(1, (t1 - t0) / 1200):.0f} a minute)")
    print("by kind:", dict(collections.Counter(w for _, w in ev)))
    prev = t0
    for tt, w in ev:
        if tt - prev >= 60:
            print(f"  a gap of {(tt - prev) / 20:.1f} s before the strike at {(tt - t0) / 20:.1f} s")
        prev = tt
    print("moments:", ", ".join(f"{w} at {(tt - t0) / 20:.0f} s" for tt, w in ev if w in ("riposte", "art", "execution", "heavy")))


if __name__ == "__main__":
    main()
