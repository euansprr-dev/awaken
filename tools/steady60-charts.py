#!/usr/bin/env python3
"""Measurement only (the look-steady-60 plan): inline SVG charts for the approval page from a results folder.

  python3 -I tools/steady60-charts.py <results dir> strip <label> [<label> ...]   one frame-time strip per window
  python3 -I tools/steady60-charts.py <results dir> slots <label> [<label> ...]   how long each frame stayed on screen

Colours are CSS variables (--ink, --muted, --grid, --good, --warn, --bad, --accent) so the page's theme decides them.
"""
import sys
from pathlib import Path


def load(root, label):
    return [int(x) / 1000.0 for x in (Path(root) / "perf-traces" / f"{label}.txt").read_text().split() if x]


def strip(root, label, title, width=720, height=150, top=40.0):
    ms = load(root, label)
    left, right, up, down = 34, 8, 20, 18
    w, h = width - left - right, height - up - down
    total = sum(ms)
    t, pts, late = 0.0, [], []
    for m in ms:
        t += m
        x = left + w * t / total
        y = up + h * (1 - min(m, top) / top)
        pts.append(f"{x:.1f},{y:.1f}")
        if m > 20.0:
            late.append((x, y))
    out = [f'<svg viewBox="0 0 {width} {height}" role="img" aria-label="{title}: frame time over the window">']
    out.append(f'<text x="{left}" y="12" class="ct">{title}</text>')
    for v in (0, 8.3, 16.7, 25, 33.3):
        y = up + h * (1 - v / top)
        cls = "cl60" if abs(v - 16.7) < 0.1 else "cg"
        out.append(f'<line x1="{left}" x2="{left + w}" y1="{y:.1f}" y2="{y:.1f}" class="{cls}"/>')
        out.append(f'<text x="{left - 4}" y="{y + 3:.1f}" class="ca" text-anchor="end">{v:g}</text>')
    out.append(f'<polyline points="{" ".join(pts)}" class="cp"/>')
    for x, y in late:
        out.append(f'<circle cx="{x:.1f}" cy="{y:.1f}" r="1.6" class="cd"/>')
    out.append(f'<text x="{left}" y="{height - 4}" class="ca">0 s</text>')
    out.append(f'<text x="{left + w}" y="{height - 4}" class="ca" text-anchor="end">{total / 1000:.0f} s</text>')
    out.append("</svg>")
    return "\n".join(out)


def slots(root, labels, titles, width=720, row=26):
    """Stacked bar per window: the share of frames that stayed on screen 1, 2, 3 or 4+ refreshes of a 120 Hz display."""
    left, right, up = 150, 46, 22
    w = width - left - right
    height = up + row * len(labels) + 24
    out = [f'<svg viewBox="0 0 {width} {height}" role="img" aria-label="How long frames stayed on screen">']
    classes = ["s1", "s2", "s3", "s4"]
    for i, (label, title) in enumerate(zip(labels, titles)):
        ms = load(root, label)
        bins = [0, 0, 0, 0]
        for m in ms:
            b = max(1, min(4, round(m / 8.333)))
            bins[b - 1] += 1
        y = up + i * row
        out.append(f'<text x="{left - 8}" y="{y + 13}" class="cb" text-anchor="end">{title}</text>')
        x = left
        for b, c in zip(bins, classes):
            bw = w * b / len(ms)
            if bw > 0:
                out.append(f'<rect x="{x:.1f}" y="{y}" width="{bw:.1f}" height="{row - 8}" class="{c}"/>')
            x += bw
        out.append(f'<text x="{left + w + 6}" y="{y + 13}" class="cb">{bins[1] * 100 / len(ms):.0f}%</text>')
    return "\n".join(out) + "\n</svg>"


def main(argv):
    root, kind, labels = argv[1], argv[2], argv[3:]
    if kind == "strip":
        for item in labels:
            label, _, title = item.partition("=")
            print(strip(root, label, title or label))
    elif kind == "slots":
        pairs = [item.partition("=") for item in labels]
        print(slots(root, [p[0] for p in pairs], [p[2] or p[0] for p in pairs]))


if __name__ == "__main__":
    main(sys.argv)
