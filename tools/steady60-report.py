#!/usr/bin/env python3
"""Measurement only (the look-steady-60 plan): frame-pacing tables from a tools/steady60-tour.sh results folder.

  python3 -I tools/steady60-report.py <results dir> [--json out.json]

Per perf window (perf-traces/<label>.txt, one frame time in microseconds per line, in order): frames, average fps,
p50/p95/p99/max in ms, the share of frames over 16.7, 25 and 33.3 ms, hitches (frames over twice the window's median
and over 20 ms), and the share of time spent in frames slower than 20 ms. The GPU world pass comes from the log's
"[pixels] gpu" lines; the machine's load from the perf lines.
"""
import json
import re
import sys
from pathlib import Path


def pct(sorted_values, q):
    return sorted_values[min(len(sorted_values) - 1, int(len(sorted_values) * q))]


def window(path):
    us = [int(x) for x in path.read_text().split() if x]
    if len(us) < 2:
        return None
    ms = [u / 1000.0 for u in us]
    s = sorted(ms)
    total = sum(ms)
    med = pct(s, 0.5)
    return {
        "frames": len(ms), "fps": len(ms) / (total / 1000.0), "p50": med, "p95": pct(s, 0.95), "p99": pct(s, 0.99),
        "p999": pct(s, 0.999), "max": s[-1],
        "over16": sum(1 for m in ms if m > 16.9) / len(ms), "over25": sum(1 for m in ms if m > 25.2) / len(ms),
        "over33": sum(1 for m in ms if m > 33.6) / len(ms),
        "hitches": sum(1 for m in ms if m > 2 * med and m > 20.0),
        "slow_time": sum(m for m in ms if m > 20.0) / total,
        "trace": ms,
    }


def main(argv):
    out_json = None
    if "--json" in argv:
        out_json = argv[argv.index("--json") + 1]
        argv = [a for a in argv if a not in ("--json", out_json)]
    root = Path(argv[1])
    log = (root / "summary.txt").read_text(errors="replace") if (root / "summary.txt").exists() else ""
    gpu = {m.group(1): {"gpu_p50": float(m.group(2)), "gpu_p95": float(m.group(3)), "gpu_p99": float(m.group(4))}
           for m in re.finditer(r"\[pixels\] gpu (\S+) samples=\d+ world_p50=([\d.]+)ms world_p95=([\d.]+)ms world_p99=([\d.]+)ms", log)}
    load = {m.group(1): float(m.group(2)) for m in re.finditer(r"\[autopilot\] perf (\S+) .* load=([\d.]+)", log)}
    order = [m.group(1) for m in re.finditer(r"\[autopilot\] perf (\S+) frames=", log)]
    rows = {}
    for path in sorted((root / "perf-traces").glob("*.txt")):
        w = window(path)
        if w:
            w.update(gpu.get(path.stem, {}))
            w["load"] = load.get(path.stem)
            rows[path.stem] = w
    names = [n for n in order if n in rows] + [n for n in rows if n not in order]
    head = f"{'window':24} {'fps':>6} {'p50':>6} {'p95':>6} {'p99':>6} {'max':>7} {'>16.7':>6} {'>25':>6} {'>33':>6} {'hitch':>5} {'gpu50':>6} {'gpu99':>6} {'load':>5}"
    print(head)
    for n in names:
        r = rows[n]
        print(f"{n:24} {r['fps']:6.1f} {r['p50']:6.2f} {r['p95']:6.2f} {r['p99']:6.2f} {r['max']:7.1f} {r['over16']*100:5.1f}% "
              f"{r['over25']*100:5.1f}% {r['over33']*100:5.1f}% {r['hitches']:5d} "
              f"{r.get('gpu_p50', float('nan')):6.2f} {r.get('gpu_p99', float('nan')):6.2f} {r['load'] if r['load'] is not None else float('nan'):5.1f}")
    if out_json:
        Path(out_json).write_text(json.dumps({n: rows[n] for n in names}))


if __name__ == "__main__":
    main(sys.argv)
