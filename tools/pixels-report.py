#!/usr/bin/env python3
"""Pixels' measurement table from one tools/autopilot/pixels.txt run (docs/perf/pixels/REPORT.md).

  python3 -I tools/pixels-report.py <autopilot log> [--md]

The tour measures every scaled setting between two native windows (<scene>_<tag>_<n>, native = tag 100). For each
scaled window this compares it with the mean of its two native neighbours, so drift (a world still loading, a busy
machine) cancels: frame time p50 and p99 ('[autopilot] perf'), and the GPU time of the world pass and of the upscale
('[pixels] gpu', p50). 'GPU total' is world + upscale, the GPU work Pixels changes.
"""
import re
import sys

PERF = re.compile(r"\[autopilot\] perf (\S+) frames=\d+ .*?p50=([\d.]+)ms .*?p99=([\d.]+)ms .*?load=([\d.]+)")
GPU = re.compile(r"\[pixels\] gpu (\S+) samples=(\d+) world_p50=([\d.]+)ms .*?world_p99=([\d.]+)ms upscale_p50=([\d.]+)ms")
NAME = re.compile(r"^(\w+?)_(\w+)_(\d+)$")
LABELS = {"075s": "0.75 sharp", "067s": "0.67 sharp", "067f": "0.67 FSR", "067b": "0.67 bilinear", "050s": "0.5 sharp",
          "050f": "0.5 FSR"}


def parse(path):
    windows = {}
    with open(path, errors="replace") as f:
        for line in f:
            m = PERF.search(line)
            if m:
                windows.setdefault(m.group(1), {}).update(p50=float(m.group(2)), p99=float(m.group(3)), load=float(m.group(4)))
            m = GPU.search(line)
            if m:
                windows.setdefault(m.group(1), {}).update(samples=int(m.group(2)), world=float(m.group(3)),
                                                          world99=float(m.group(4)), up=float(m.group(5)))
    return windows


def main(argv):
    md = "--md" in argv
    args = [a for a in argv if a != "--md"]
    if not args:
        print(__doc__)
        return 1
    windows = parse(args[0])
    scenes = {}
    for name, w in windows.items():
        m = NAME.match(name)
        if m and "p50" in w and "world" in w:
            scenes.setdefault(m.group(1), {})[int(m.group(3))] = (m.group(2), w)
    rows = []
    for scene, byIndex in scenes.items():
        for i, (tag, w) in sorted(byIndex.items()):
            if tag == "100" or i - 1 not in byIndex or i + 1 not in byIndex:
                continue
            a, b = byIndex[i - 1][1], byIndex[i + 1][1]
            base = {k: (a[k] + b[k]) / 2 for k in ("p50", "p99", "world", "up")}
            rows.append((scene, LABELS.get(tag, tag), base, w))
    head = ["scene", "setting", "frame p50", "frame p99", "GPU world p50", "upscale p50", "GPU total", "saved"]
    out = []
    for scene, label, base, w in rows:
        total, baseTotal = w["world"] + w["up"], base["world"] + base["up"]
        out.append([scene, label,
                    f"{w['p50']:.2f} ({w['p50'] - base['p50']:+.2f})",
                    f"{w['p99']:.2f} ({w['p99'] - base['p99']:+.2f})",
                    f"{w['world']:.2f} (native {base['world']:.2f})",
                    f"{w['up']:.2f}",
                    f"{total:.2f}",
                    f"{baseTotal - total:+.2f} ms ({(baseTotal - total) / baseTotal * 100:+.0f}%)"])
    if md:
        print("| " + " | ".join(head) + " |")
        print("|" + "---|" * len(head))
        for r in out:
            print("| " + " | ".join(r) + " |")
    else:
        widths = [max(len(str(r[i])) for r in out + [head]) for i in range(len(head))]
        for r in [head] + out:
            print("  ".join(str(v).ljust(widths[i]) for i, v in enumerate(r)))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
