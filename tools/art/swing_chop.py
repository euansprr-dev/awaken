"""The weapon's own cutting motion (SPEC §40 "One press is one press"): while vanilla mines a block with a fighting
weapon in hand, the hands chop it in first person, in a loop.

Euan 2026-10-29: "Even without the timer, when it does cut, there is no animation". The hands are posed only from Arms'
own moves, so vanilla's mining wave never showed and the blade sat still while the log cracked. Two loops per style, on
Swing Studio's lever (`swing_choreo.arc`: the blade's middle swept round the eye through a target, out from a shoulder):

- the chop (an axe on a log or planks): raised high on the hand's side, driven down through a mark just under the
  crosshair, stopped dead in the wood with a small kick back, raised again;
- the hack (a blade on a web, bamboo or leaves): a quicker, shorter diagonal from low on the hand's side, on the
  daggers' slash lever (round 14), that carries a little through what it cuts.

Each loop starts and ends on its impact (0 degrees on the lever), so a loop repeats with no seam; the game enters it
part way up (`ENTER`) from wherever the hands are and blends out to rest when the mining stops (ArmsChop).

    python3 tools/art/swing_chop.py strip OUT.png [style]     a loop frame by frame, the chop and the hack
"""
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import anim_lab as L  # noqa: E402
import swing_choreo as SC  # noqa: E402

# the levers: way on the screen, the mark the blade's middle crosses at the impact (view space), the shoulder
LEVERS = {
    "chop": SC.arc((-0.22, -0.97), (0.05, -0.18, -1.15), (0.62, -0.58, -0.30), eye=0.55),
    "hack": SC.arc((-0.9, -0.44), (0.05, -0.20, -1.0), SC.LOW, eye=0.5),
}
# a loop's keys: (tick, degrees on the lever, the wrist's lag, the ease into it); the first and last are the impact
LOOPS = {
    # 8 ticks: the kick back off the wood, up to the set over the shoulder, a beat cocked, two ticks' drive into it
    "chop": dict(period=8.0, keys=[(0.0, 0.0, 0.0, "lin"), (1.0, -7.0, 0.0, "out"), (5.0, -38.0, 8.0, "io"),
                                   (6.0, -42.0, 10.0, "io"), (8.0, 0.0, 0.0, "in")]),
    # 6 ticks: on through what it cut, back up its side, a short drive
    "hack": dict(period=6.0, keys=[(0.0, 0.0, 0.0, "lin"), (1.0, 10.0, -4.0, "out"), (4.0, -38.0, 6.0, "io"),
                                   (6.0, 0.0, 0.0, "in")]),
}
# where the game starts a loop when the mining begins: on the way up to its set, so the first blow lands soon
ENTER = {"chop": 3.0, "hack": 2.5}


def angle(kind, t):
    """(degrees on the lever, the wrist's lag) at {@code t} ticks into a loop."""
    keys = LOOPS[kind]["keys"]
    t = t % LOOPS[kind]["period"]
    for (t0, a0, l0, _), (t1, a1, l1, ease) in zip(keys, keys[1:]):
        if t0 <= t <= t1:
            u = SC.EASE[ease]((t - t0) / max(1e-6, t1 - t0))
            return a0 + (a1 - a0) * u, l0 + (l1 - l0) * u
    return keys[-1][1], keys[-1][2]


def pose(held, kind, t):
    """(d, R): the hand's move from rest and its turn, {@code t} ticks into a loop."""
    a, lag = angle(kind, t)
    return SC._on_arc(held, LEVERS[kind], a, lag)


def bake(held):
    """A style's loops for the game's file: per kind its period, where the game enters it and its keys
    [t, dx, dy, dz, qx, qy, qz, qw] a key step apart (the impact at 0 and at the period)."""
    out = {}
    for kind, loop in LOOPS.items():
        keys = []
        for t in np.arange(0, loop["period"] + 1e-6, L.KEY_DT):
            d, R = pose(held, kind, float(t))
            keys.append([round(float(t), 3)] + [round(float(v), 4) for v in d] + [round(float(v), 5) for v in L.mat_quat(R)])
        out[kind] = {"period": loop["period"], "enter": ENTER[kind], "keys": keys}
    return out


def strip(out, style="axe"):
    """Both loops frame by frame on the style's forged weapon, the impact marked."""
    from PIL import Image, ImageDraw
    held = L.Held("gatewrought_" + style)
    rows = []
    for kind, loop in LOOPS.items():
        cells = []
        for t in np.arange(0, loop["period"] + 1e-6, 0.5 if kind == "chop" else 0.5):
            d, R = pose(held, kind, float(t))
            im = L.frame(held, d, R).resize((320, 180), Image.LANCZOS)
            dr = ImageDraw.Draw(im)
            dr.line([(154, 90), (166, 90)], fill=(255, 255, 255))
            dr.line([(160, 84), (160, 96)], fill=(255, 255, 255))
            hit = abs(t % loop["period"]) < 1e-6
            dr.text((6, 4), f"{kind} t {t:.1f}" + ("  IMPACT" if hit else ""), fill=(255, 120, 120) if hit else (200, 205, 215))
            cells.append(im)
        rows.append(cells)
    cols = max(len(r) for r in rows)
    per = 9
    lines = sum((len(r) + per - 1) // per for r in rows)
    sheet = Image.new("RGB", (320 * min(per, cols), lines * 180 + 18), (14, 15, 20))
    ImageDraw.Draw(sheet).text((6, 3), f"{style} · the chop and the hack, frame by frame", fill=(230, 232, 240))
    y = 18
    for r in rows:
        for i, c in enumerate(r):
            sheet.paste(c, ((i % per) * 320, y + (i // per) * 180))
        y += ((len(r) + per - 1) // per) * 180
    sheet.save(out)
    return out


def main(argv):
    if len(argv) >= 2 and argv[0] == "strip":
        print(strip(argv[1], argv[2] if len(argv) > 2 else "axe"))
        return 0
    raise SystemExit(__doc__)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
