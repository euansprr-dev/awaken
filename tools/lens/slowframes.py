#!/usr/bin/env python3
"""Measurement only (Steady 60, 12 Nov; docs/perf/steady60/perf-2026-11-12): what the slow frames had in them. Reads the
per-frame GPU traces a tour writes (perf-traces/gpu_<window>.txt: world ms, upscale ms, marks; PixelsTiming) and, for
each window, says how slow its slow frames are and whether any of Lens's periodic work comes with them: for each mark,
the share of all frames that carry it against the share of the slow frames (the slowest 5%) that do, and the mean GPU
time of frames with it against frames without. A mark that rides the slow frames shows as a slow share well over its
all-frames share and a mean well over the others'.
Marks (LensClient, LensLit, LensSunMap, LensHeightfield, LensBlocks): 1 the lit air's upload, 2 its sweep started,
4 the sun map's rows, 8 its sweep started, 16 a heightfield upload, 32 a block map upload, 64 the governor's short
budget, 128 a tick ran, 256 block map sections read.
Usage: tools/lens/slowframes.py <tour results dir> [window prefix ...]"""
import os, sys

MARKS = {1: "lit air upload", 2: "lit air sweep", 4: "sun map rows", 8: "sun map sweep", 16: "heightfield upload",
         32: "block map upload", 64: "short budget", 128: "a tick", 256: "block map reads"}


def pct(xs, q):
    s = sorted(xs)
    return s[min(len(s) - 1, int(len(s) * q))]


def window(path):
    rows = []
    for line in open(path):
        p = line.split()
        if len(p) == 3:
            rows.append((float(p[0]), float(p[1]), int(p[2])))
    return rows


def main():
    root = sys.argv[1]
    want = sys.argv[2:]
    d = os.path.join(root, "perf-traces")
    for f in sorted(os.listdir(d)):
        if not f.startswith("gpu_"):
            continue
        name = f[4:-4]
        if want and not any(name.startswith(w) for w in want):
            continue
        rows = window(os.path.join(d, f))
        if len(rows) < 50:
            continue
        w = [r[0] for r in rows]
        p50, p95, p99 = pct(w, 0.5), pct(w, 0.95), pct(w, 0.99)
        cut = p95
        slow = [r for r in rows if r[0] >= cut]
        print("%-18s frames %5d  GPU p50 %5.2f  p95 %5.2f  p99 %5.2f  max %5.2f  over p50+2 ms %5.1f%%  over 15 ms %5.1f%%" % (
            name, len(rows), p50, p95, p99, max(w), 100 * sum(x > p50 + 2 for x in w) / len(w), 100 * sum(x > 15 for x in w) / len(w)))
        # the slow frames' neighbours: a spike that comes in runs, or alone
        runs = 0
        for i in range(1, len(rows)):
            if rows[i][0] >= cut and rows[i - 1][0] >= cut:
                runs += 1
        print("  slowest 5%% (>= %.2f ms): %d frames, %d of them right after another slow one" % (cut, len(slow), runs))
        for bit, label in MARKS.items():
            have = [r for r in rows if r[2] & bit]
            if not have:
                continue
            not_have = [r for r in rows if not r[2] & bit]
            share_all = 100 * len(have) / len(rows)
            share_slow = 100 * sum(1 for r in slow if r[2] & bit) / max(1, len(slow))
            m_have = sum(r[0] for r in have) / len(have)
            m_not = sum(r[0] for r in not_have) / max(1, len(not_have))
            print("  %-20s in %5.1f%% of frames, %5.1f%% of the slow ones;  mean %5.2f ms with, %5.2f without" % (
                label, share_all, share_slow, m_have, m_not))


if __name__ == "__main__":
    main()
