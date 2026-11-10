"""Disposable: reads the takes of the stages 1 to 5 look tour (tools/autopilot/arms-weight-proof.txt; ArmsFilm's track.tsv
and events.tsv) and prints what each layer did, frame by frame, then tiles each take's pictures into one sheet to look at.

    python3 docs/weapon-feel/harness/proof_read.py <clips dir> [out dir]
"""
import csv
import math
import os
import sys


def load(d):
    rows = list(csv.DictReader(open(os.path.join(d, "track.tsv")), delimiter="\t"))
    for r in rows:
        for k, v in r.items():
            if k != "move":
                try:
                    r[k] = float(v)
                except (TypeError, ValueError):
                    pass
    return rows


def events(d):
    p = os.path.join(d, "events.tsv")
    return list(csv.DictReader(open(p), delimiter="\t")) if os.path.exists(p) else []


def tip(r):
    """The main blade's tip on the screen in pixels of a 1280 x 720 frame (the hands' 70 degrees), None when it isn't drawn."""
    z = r["m_tz"]  # the film's space looks along +z with +x to the left
    if not r["m_has"] or z <= 1e-3:
        return None
    f = 1 / math.tan(math.radians(35))
    return (640 - r["m_tx"] / z * f / (16 / 9) * 640, 360 - r["m_ty"] / z * f * 360)


def base(r):
    z = r["m_bz"]
    if not r["m_has"] or z <= 1e-3:
        return None
    f = 1 / math.tan(math.radians(35))
    return (640 - r["m_bx"] / z * f / (16 / 9) * 640, 360 - r["m_by"] / z * f * 360)


def fmt(p):
    return "   off   " if p is None else f"{p[0]:6.0f},{p[1]:5.0f}"


def take(d, name):
    rows = load(d)
    print(f"\n== {name}: {len(rows)} frames, {sum(1 for r in rows if r['pic'] >= 0)} pictures, "
          f"{rows[-1]['real_ms'] - rows[0]['real_ms']:.0f} ms real, game ticks {rows[0]['gt']:.1f} to {rows[-1]['gt']:.1f}")
    for e in events(d):
        w = e["what"]
        if any(k in w for k in ("impact", "ghost", "dull", "glance", "draw", "hip", "begin", "server hit", "confirm", "guard", "DULL")):
            print(f"   event {e['side']} gt {e['gt'] or '-':>9} {w}")
    return rows


def freeze(rows):
    """The frames a blow is held: where the blade's tip is on each (the shake) and what it does in the ticks after (the settle)."""
    held = [i for i, r in enumerate(rows) if r["hold"]]
    if not held:
        print("   no frame was frozen")
        return
    runs, cur = [], [held[0]]
    for i in held[1:]:
        if i == cur[-1] + 1:
            cur.append(i)
        else:
            runs.append(cur)
            cur = [i]
    runs.append(cur)
    for run in runs:
        tips = [tip(rows[i]) for i in run]
        seen = [t for t in tips if t]
        spread = max((math.dist(a, b) for a in seen for b in seen), default=0)
        print(f"   freeze: frames {run[0]}..{run[-1]} ({len(run)}), {rows[run[-1]]['real_ms'] - rows[run[0]]['real_ms']:.0f} ms, "
              f"move {rows[run[0]]['move']}; the tip moves {spread:.1f} px over it: " + " ".join(fmt(t) for t in tips[:8]))
        after = rows[run[-1] + 1:run[-1] + 60]
        still = [tip(r) for r in after]
        line = " ".join(f"{r['raw']:.1f}:{fmt(t)}" for r, t in list(zip(after, still))[::4][:12])
        print(f"   after it (move clock: tip): {line}")


def column(rows, key, every=3, n=40):
    print(f"   {key} by frame (gt: value): " + " ".join(f"{r['gt'] - rows[0]['gt']:.1f}:{r[key]:.2f}" for r in rows[::every][:n]))


def path(rows, every=3, n=40):
    print("   tip by frame (gt: x,y): " + " ".join(f"{r['gt'] - rows[0]['gt']:.1f}:{fmt(tip(r))}" for r in rows[::every][:n]))


def sheet(d, name, out, cols=8, most=32):
    from PIL import Image
    pics = sorted(f for f in os.listdir(d) if f.endswith(".png"))
    if not pics:
        return
    step = max(1, math.ceil(len(pics) / most))
    pick = pics[::step]
    ims = [Image.open(os.path.join(d, f)).convert("RGB").resize((320, 180)) for f in pick]
    rows_n = math.ceil(len(ims) / cols)
    im = Image.new("RGB", (cols * 320, rows_n * 180))
    for i, x in enumerate(ims):
        im.paste(x, (i % cols * 320, i // cols * 180))
    os.makedirs(out, exist_ok=True)
    im.save(os.path.join(out, name + ".jpg"), quality=82)
    print(f"   sheet {os.path.join(out, name + '.jpg')}: every {step} of {len(pics)} pictures")


def main(argv):
    root = argv[0]
    out = argv[1] if len(argv) > 1 else os.path.join(root, "sheets")
    for name in ("hit", "dull", "glance", "draw", "hip", "hip_lower", "guard"):
        d = os.path.join(root, name)
        if not os.path.isdir(d):
            print(f"\n== {name}: no take")
            continue
        rows = take(d, name)
        if name in ("hit", "dull", "glance", "hip"):
            freeze(rows)
        if name in ("draw",):
            column(rows, "equip")
            column(rows, "m_has")
        if name in ("guard", "hip_lower", "glance", "draw"):
            path(rows)
        if name in ("hit", "dull", "glance"):
            column(rows, "yaw", every=2, n=60)
            column(rows, "pitch", every=2, n=60)
        sheet(d, name, out)


if __name__ == "__main__":
    main(sys.argv[1:])
