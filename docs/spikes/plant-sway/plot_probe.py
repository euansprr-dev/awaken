"""Charts for the sway probe (/awaken terrain waving probe; SPEC 15): vertex offsets over time, smooth clock vs old.

    python3 -I docs/spikes/plant-sway/plot_probe.py <out.png> <probe.csv> [title] [--old] [--dir]

One panel per probed vertex (grass tips, a tall grass top, a strand's end, a leaf): its offset along x and z over
time, from the corner texels the game wrote each frame (the same math as the shader). --old overlays the same vertex
on the old clock (raw game time, which rewinds when the server's time sync pulls it back). --dir adds a panel of the
wind heading. Prints, per vertex, the largest frame-to-frame jump against the typical step, so a rewind shows as a
number too.
"""
import csv
import sys

from PIL import Image, ImageDraw

W, PANEL_H, PAD_L, PAD_R, PAD_T = 1800, 210, 90, 30, 34
COLORS = {"x": (66, 133, 244), "z": (234, 112, 30), "old_x": (150, 185, 245), "old_z": (245, 190, 150), "dir": (60, 160, 90),
          "lag": (200, 50, 50)}


def load(path):
    with open(path) as f:
        rows = list(csv.DictReader(f))
    return rows


def series(rows, key):
    return [float(r[key]) for r in rows]


def panel(draw, y0, ts, lines, label, unit="blocks"):
    t0, t1 = ts[0], ts[-1] if ts[-1] > ts[0] else ts[0] + 1
    vals = [v for _, vs in lines for v in vs]
    lo, hi = min(vals), max(vals)
    if hi - lo < 1e-6:
        lo, hi = lo - 0.01, hi + 0.01
    pad = (hi - lo) * 0.08
    lo, hi = lo - pad, hi + pad
    x0, x1, yt, yb = PAD_L, W - PAD_R, y0 + PAD_T, y0 + PANEL_H - 10
    draw.rectangle([x0, yt, x1, yb], outline=(90, 90, 90))
    draw.text((8, y0 + 8), label, fill=(20, 20, 20))
    draw.text((8, yt), f"{hi:.3f}", fill=(110, 110, 110))
    draw.text((8, yb - 12), f"{lo:.3f} {unit}", fill=(110, 110, 110))
    for color, vs in lines:
        pts = [(x0 + (t - t0) / (t1 - t0) * (x1 - x0), yb - (v - lo) / (hi - lo) * (yb - yt)) for t, v in zip(ts, vs)]
        draw.line(pts, fill=color, width=2)
        if len(pts) <= 60:
            for p in pts:
                draw.ellipse([p[0] - 3, p[1] - 3, p[0] + 3, p[1] + 3], fill=color)


def jumps(vs):
    steps = [abs(b - a) for a, b in zip(vs, vs[1:])]
    if not steps:
        return 0.0, 0.0
    srt = sorted(steps)
    return max(steps), srt[len(srt) // 2]


def main(argv):
    out, path = argv[0], argv[1]
    title = argv[2] if len(argv) > 2 and not argv[2].startswith("--") else path
    old, show_dir = "--old" in argv, "--dir" in argv
    rows = load(path)
    ts = [float(r["ms"]) / 1000.0 for r in rows]
    names = [k[:-2] for k in rows[0].keys() if k.endswith("_x") and "_old" not in k]
    panels = len(names) + (1 if show_dir else 0) + (1 if old else 0)
    img = Image.new("RGB", (W, 40 + panels * PANEL_H + 30), (250, 250, 250))
    d = ImageDraw.Draw(img)
    d.text((8, 10), f"{title}: {len(rows)} frames over {ts[-1] - ts[0]:.2f} s (blue x, orange z" + (
        "; pale: the same vertex on the old clock" if old else "") + ")", fill=(0, 0, 0))
    y = 40
    for n in names:
        lines = [(COLORS["x"], series(rows, n + "_x")), (COLORS["z"], series(rows, n + "_z"))]
        if old:
            lines = [(COLORS["old_x"], series(rows, n + "_old_x")), (COLORS["old_z"], series(rows, n + "_old_z"))] + lines
        mx, med = jumps(series(rows, n + "_x"))
        mz, medz = jumps(series(rows, n + "_z"))
        note = f"{n}: largest frame step x {mx:.4f} (median {med:.4f}), z {mz:.4f} (median {medz:.4f})"
        if old:
            ox, _ = jumps(series(rows, n + "_old_x"))
            oz, _ = jumps(series(rows, n + "_old_z"))
            note += f"; old clock largest step x {ox:.4f}, z {oz:.4f}"
        print(note)
        panel(d, y, ts, lines, note)
        y += PANEL_H
    if old:
        lag = [float(r["raw_s"]) - float(r["wind_s"]) for r in rows]
        rewinds = sum(1 for a, b in zip(series(rows, "raw_s"), series(rows, "raw_s")[1:]) if b < a)
        print(f"raw game clock rewound {rewinds} times; raw minus smooth clock {min(lag):.3f}..{max(lag):.3f} s")
        panel(d, y, ts, [(COLORS["lag"], lag)], f"raw game time minus the smooth wind clock (raw rewound {rewinds} times)", "s")
        y += PANEL_H
    if show_dir:
        dirs = series(rows, "dir_deg")
        un, last = [], None
        for v in dirs:
            if last is not None:
                while v - last > 180:
                    v -= 360
                while v - last < -180:
                    v += 360
            un.append(v)
            last = v
        print(f"wind heading {un[0]:.1f} -> {un[-1]:.1f} degrees")
        panel(d, y, ts, [(COLORS["dir"], un)], f"wind heading ({un[0]:.0f} to {un[-1]:.0f} degrees)", "deg")
    img.save(out)
    print("chart:", out)


if __name__ == "__main__":
    main(sys.argv[1:])
