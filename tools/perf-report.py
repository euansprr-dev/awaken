#!/usr/bin/env python3
"""Frame-time tables from autopilot logs ('[autopilot] perf ...' and '[autopilot] info ...' lines).

  python3 -I tools/perf-report.py <log>                  one run: every scene
  python3 -I tools/perf-report.py <base log> <new log>   two runs side by side, with the change in p50 and p99
  python3 -I tools/perf-report.py --md ...               the same as a Markdown table (for docs/perf/)

Two runs of the same setup should agree within 5% on p50 (the plan's repeatability gate); bigger gaps mean the
machine was busy, so don't trust that pair.
"""
import re
import sys

PERF = re.compile(r"\[autopilot\] perf (\S+) (.*)$")
INFO = re.compile(r"\[autopilot\] info (.*)$")
KV = re.compile(r"(\w+)=(\S+)")


def parse(path):
    scenes, info = {}, {}
    with open(path, errors="replace") as f:
        for line in f:
            m = PERF.search(line)
            if m:
                kv = dict(KV.findall(m.group(2)))
                scenes[m.group(1)] = {k: float(v.rstrip("ms")) if k in ("p50", "p95", "p99", "max", "avg_fps") else v
                                      for k, v in kv.items()}
                continue
            m = INFO.search(line)
            if m:
                info = dict(KV.findall(m.group(1)))
    return scenes, info


def describe(info):
    if not info:
        return "(no info line)"
    mods = info.get("mods", "").split(",")
    extra = [m for m in mods if m not in ("minecraft", "neoforge", "awaken")]
    return (f"{info.get('backend')} on {info.get('device')} · framebuffer {info.get('fb')} · render distance {info.get('rd')}"
            f" · heap {info.get('heapMax')} MB · GC {info.get('gc', '').split(',')[0]} · mods: {', '.join(extra) or 'Awaken only'}")


def main(argv):
    md = "--md" in argv
    paths = [a for a in argv[1:] if a != "--md"]
    if not paths or len(paths) > 2:
        raise SystemExit(__doc__)
    runs = [parse(p) for p in paths]
    for i, (p, (_, info)) in enumerate(zip(paths, runs)):
        print(f"{'AB'[i] if len(runs) > 1 else '-'} {p}\n  {describe(info)}")
    names = list(dict.fromkeys(n for scenes, _ in runs for n in scenes))
    if len(runs) == 1:
        head = ["scene", "fps", "p50 ms", "p95 ms", "p99 ms", "max ms", "tick p95", "heap MB"]
        rows = [[n, f"{s['avg_fps']:.0f}", f"{s['p50']:.2f}", f"{s['p95']:.2f}", f"{s['p99']:.2f}", f"{s['max']:.1f}",
                 s.get("tick_p95", "-"), s.get("heap", "-")] for n, s in ((n, runs[0][0][n]) for n in names)]
    else:
        head = ["scene", "A p50", "B p50", "Δ p50", "A p99", "B p99", "Δ p99"]
        rows = []
        for n in names:
            a, b = runs[0][0].get(n), runs[1][0].get(n)
            if not a or not b:
                rows.append([n, "-" if not a else f"{a['p50']:.2f}", "-" if not b else f"{b['p50']:.2f}", "", "", "", ""])
                continue
            rows.append([n, f"{a['p50']:.2f}", f"{b['p50']:.2f}", f"{(b['p50'] / a['p50'] - 1) * 100:+.0f}%",
                         f"{a['p99']:.2f}", f"{b['p99']:.2f}", f"{(b['p99'] / a['p99'] - 1) * 100:+.0f}%"])
    if md:
        print("| " + " | ".join(head) + " |\n|" + "---|" * len(head))
        for r in rows:
            print("| " + " | ".join(r) + " |")
    else:
        w = [max(len(str(x)) for x in col) for col in zip(head, *rows)]
        for r in [head] + rows:
            print("  ".join(str(x).ljust(w[i]) if i == 0 else str(x).rjust(w[i]) for i, x in enumerate(r)))


if __name__ == "__main__":
    main(sys.argv)
