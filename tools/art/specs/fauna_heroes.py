"""The named beasts' hero bodies (and two smaller rigs: the Hollow Owl and the Bogwick): geometry, paint and clips.

Each design is a function returning a packed Rig; each painter fills the skin and glow canvases. heroes.py builds
the textures and HeroData.java from these and renders the previews. No build step here (build(out) is a no-op).

Conventions (vanilla ModelPart): model pixels, y down, ground at y = 24, the creature faces -z. Rotations in degrees.
Clip names the Java side looks for: idle, walk, run, fly (fliers), windup, strike, bite (melee), hurt, roar, warn,
death, and <move>_windup / <move>_strike for the moves in rig.moves (ability name -> move).
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from fauna_rig import Rig, Ramp, Painter, Face, hexc, lerpc, mul, p_fur, p_scales, p_plates, p_bands, p_feathers, p_stone, combine, SIDES  # noqa: E402
from fauna_rig import p_strokes, p_blotch, _h  # noqa: E402
from fauna_anim import cycle, quad_gait, body_bob, wave, s, c  # noqa: E402


def build(out):
    pass


# ---------------------------------------------------------------------------------------------------------------------
# Shared paint helpers

def ragged(face, rng, ramp, base, minlen=0.45, every=1, pattern=None, seed=0):
    """Hanging strands on a plane (moss, silk, fur fringe): each column hangs to its own length, alpha below."""
    lens = []
    for i in range(face.w):
        r = rng.random()
        lens.append(int(face.h * (minlen + (1 - minlen) * (0.35 + 0.65 * r if (i + seed) % 2 else r * 0.7))))
    for i in range(face.w):
        for j in range(face.h):
            if j >= max(1, lens[i]):
                face.set(i, j, (0, 0, 0, 0))
                continue
            v = base + 0.6 * (1 - j / max(1, face.h)) - (0.8 if j == lens[i] - 1 else 0) - (0.5 if i % 3 == 2 else 0)
            face.set(i, j, ramp.at(v))


def clear(face):
    for i, j in face.each():
        face.set(i, j, (0, 0, 0, 0))


def line(face, x0, y0, x1, y1, col):
    n = max(abs(x1 - x0), abs(y1 - y0), 1)
    for k in range(n + 1):
        face.set(int(round(x0 + (x1 - x0) * k / n)), int(round(y0 + (y1 - y0) * k / n)), col)


def blob(face, cx, cy, r, col, ry=None):
    ry = ry or r
    for j in range(int(cy - ry - 1), int(cy + ry + 2)):
        for i in range(int(cx - r - 1), int(cx + r + 2)):
            if ((i - cx) / max(0.5, r)) ** 2 + ((j - cy) / max(0.5, ry)) ** 2 <= 1.0:
                face.set(i, j, col)


def sprig(face, rng, stem, leaf, berry=None, flower=None, thorns=True, stems=None):
    """Bramble sprigs drawn on a transparent plane: curving stems with thorns, leaf pairs, berries, a rose on top."""
    clear(face)
    n = stems or (3 if face.w >= 14 else 2 if face.w >= 9 else 1)
    for k in range(n):
        _sprig_stem(face, rng, stem, leaf, berry, flower if k == n // 2 else None, thorns, (k + 0.5) / n, n)


def _sprig_stem(face, rng, stem, leaf, berry, flower, thorns, at, n):
    w, h = face.w, face.h
    x = w * at + rng.uniform(-1, 1)
    lean = (at - 0.5) * 0.5
    top = int(h * (0.0 if flower else rng.uniform(0.15, 0.35)))
    pts = []
    cx = w * at
    for j in range(h - 1, top - 1, -1):
        x += rng.uniform(-0.5, 0.5) + lean + (0.3 if x < cx - 2 else -0.3 if x > cx + 2 else 0)
        x = max(1, min(w - 2, x))
        pts.append((int(round(x)), j))
    for k, (i, j) in enumerate(pts):
        face.set(i, j, stem.at(2 if k % 3 else 3))
        if thorns and k % 3 == 1:
            d = 1 if k % 2 else -1
            face.set(i + d, j, stem.at(4))
        if k > 2 and k % 4 == 2:
            d = 1 if (k // 4) % 2 else -1
            for q in range(1, 4):
                face.set(i + d * q, j - (q // 2), leaf.at(5 - q))
                face.set(i + d * q, j - (q // 2) + 1, leaf.at(3 - q // 2))
            face.set(i + d * 2, j - 2, leaf.at(4))
        if berry and k > 4 and k % 5 == 0:
            d = -1 if (k // 5) % 2 else 1
            face.set(i + d * 2, j + 1, berry[0])
            face.set(i + d * 2 + 1, j + 1, berry[1])
            face.set(i + d * 2, j + 2, berry[1])
    if flower:
        i, j = pts[-1]
        for di, dj in ((0, 0), (-1, 0), (1, 0), (0, -1), (0, 1)):
            face.set(i + di, j + dj + 1, flower[0])
        face.set(i, j + 1, flower[1])


# ---------------------------------------------------------------------------------------------------------------------
# The Briar Sow: a hunched forest boar with a bramble hedge growing out of her hump

def briar_sow():
    r = Rig("briar_sow", (256, 128))
    r.walk_stride, r.run_stride, r.run_speed, r.shadow = 1.8, 4.2, (0.17, 0.34), 1.3
    body = r.bone("body", (0, 10, 0))
    body.cube("chest", -9, -14, -14, 18, 20, 16)
    rump = body.bone("rump", (0, -6, 2), (-9, 0, 0))  # the back slopes down from the shoulders
    rump.cube("rump", -7.5, -1, 0, 15, 14, 16)
    rump.cube("moss_rump", -6, -3, 1, 12, 2, 13)
    hump = body.bone("hump", (0, -14, -6))
    hump.cube("hump", -7.5, -5, -7, 15, 5, 14)
    hump.cube("ridge", -3.5, -7, -6, 7, 2, 12)
    mane_l = body.bone("mane_l", (9.1, -8, -7))
    mane_l.cube("mane_l", 0, 0, -6, 0, 10, 14)
    mane_r = body.bone("mane_r", (-9.1, -8, -7))
    mane_r.cube("mane_r", 0, 0, -6, 0, 10, 14, mirror=True, share="mane_l")
    head = body.bone("head", (0, 0, -13))
    head.cube("skull", -6, -6, -12, 12, 11, 12)
    head.cube("snout", -4.5, -2, -20, 9, 8, 8)
    head.cube("nose", -5, -2.5, -21, 10, 7, 1)
    head.cube("brow", -6.5, -7, -11, 13, 3, 5)
    jaw = head.bone("jaw", (0, 5, -5))
    jaw.cube("jaw", -5, 0, -14, 10, 3, 14)
    beard = jaw.bone("beard", (0, 3, -4))
    beard.cube("beard", -5, 0, 0, 10, 8, 0)
    for side, nm in ((1, "l"), (-1, "r")):
        mir = side < 0

        def sh(n, side=side):
            return None if side > 0 else n
        ear = head.bone("ear_" + nm, (side * 5, -5, -3), (-35, 0, side * -30))
        ear.cube("ear_" + nm, -1, -5, -1, 2, 5, 3, mirror=mir, share=sh("ear_l"))
        t1 = head.bone("tusk_" + nm, (side * 5, 2, -16), (-28, side * -35, 0))
        t1.cube("tusk1_" + nm, -1.5, -1.5, -6, 3, 3, 6, mirror=mir, share=sh("tusk1_l"))
        t2 = t1.bone("tusk2_" + nm, (0, 0, -5.5), (-58, 0, side * -12))
        t2.cube("tusk2_" + nm, -1, -1, -6, 2, 2, 6, mirror=mir, share=sh("tusk2_l"))
        t3 = t2.bone("tusk3_" + nm, (0, 0, -5.5), (-50, 0, 0))
        t3.cube("tusk3_" + nm, -0.5, -0.5, -5, 1, 1, 5, mirror=mir, share=sh("tusk3_l"))
        lt = jaw.bone("ltusk_" + nm, (side * 4, 0, -12), (-15, 0, side * 15))
        lt.cube("ltusk_" + nm, -0.5, -3, -0.5, 1, 3, 1, mirror=mir, share=sh("ltusk_l"))
        leg = body.bone("leg_f" + nm, (side * 6, 4, -7))
        leg.cube("legf_" + nm, -3, -1, -3, 6, 7, 6, mirror=mir, share=sh("legf_l"))
        shin = leg.bone("shin_f" + nm, (0, 6, 0))
        shin.cube("shinf_" + nm, -2.5, 0, -2.5, 5, 4, 5, mirror=mir, share=sh("shinf_l"))
        thigh = body.bone("leg_h" + nm, (side * 5.5, -1, 12))
        thigh.cube("thigh_" + nm, -3, -2, -4, 6, 10, 8, mirror=mir, share=sh("thigh_l"))
        hock = thigh.bone("hock_h" + nm, (0, 8, 1))
        hock.cube("hock_" + nm, -2, 0, -2, 4, 7, 4, mirror=mir, share=sh("hock_l"))
    tail = body.bone("tail", (0, -2, 18), (25, 0, 0))
    tail.cube("tail", -1, 0, -1, 2, 7, 2)
    tuft = tail.bone("tail_tuft", (0, 7, 0))
    tuft.cube("tuft", -2, 0, -2, 4, 4, 4)
    # the hedge: thorny canes arching back out of a bramble bush on her hump
    hedge = hump.bone("hedge", (0, -5, 0))
    hedge.cube("bush", -5, -4, -6, 10, 4, 12)
    cane(hedge, "bramble_a", (2.5, -1, -4), (-28, 8, 16), [(8, 2, 0), (7, 2, 34), (6, 1, 38)], leaf_at=0)
    cane(hedge, "bramble_b", (-2.5, -1, -1), (-12, -8, -14), [(9, 2, 0), (8, 2, 30), (6, 1, 40)], leaf_at=1)
    cane(hedge, "bramble_c", (1, -1, 4), (12, 0, 8), [(7, 2, 0), (6, 2, 42), (5, 1, 44)])
    cane(rump, "bramble_d", (0, -3, 10), (34, 0, -10), [(6, 2, 0), (5, 1, 40)])
    sp = hedge.bone("sprig_a", (0, -2, 0), (0, 45, 0))
    sp.cube("sprig_a", -9, -16, 0, 18, 16, 0)
    sp2 = hedge.bone("sprig_b", (0, -2, 0), (0, -45, 0))
    sp2.cube("sprig_b", -9, -16, 0, 18, 16, 0)
    rump.bone("sprig_d", (0, -3, 9), (10, 30, 0)).cube("sprig_d", -6, -11, 0, 12, 11, 0)
    r.look = [("head", 0.75, 0.6)]
    r.moves = {"Bramble Charge": "charge", "Root Stomp": "stomp", "Thornsnare": "stomp", "Pollen Haze": "shake",
               "Thorn Burst": "shake", "Last Stand": "roar"}
    r.pack()
    sow_clips(r)
    return r


def cane(parent, name, pivot, rot, segs, leaf_at=None):
    """A chain of thorny segments, each bent back from the last: (length, thickness, bend degrees)."""
    b = parent.bone(name, pivot, rot)
    node = b
    for k, (ln, th, bend) in enumerate(segs):
        if k > 0:
            node = node.bone(f"{name}{k + 1}", (0, -segs[k - 1][0] + 0.5, 0), (bend, 0, 0))
        o = -th / 2
        node.cube(f"{name}_s{k + 1}", o, -ln, o, th, ln, th)
        if leaf_at == k:
            node.bone(f"{name}_leaf", (0, -ln, 0)).cube(f"{name}_leaf", -2.5, -2, -2.5, 5, 3, 5)
    return b


LEGS4 = [("leg_fl", "shin_fl", 0.0), ("leg_hr", "hock_hr", 0.0), ("leg_fr", "shin_fr", 0.5), ("leg_hl", "hock_hl", 0.5)]
HEDGE = ["bramble_a", "bramble_b", "bramble_c", "bramble_d"]


def sow_clips(r):
    idle = r.clip("idle", 3.2, loop=True)
    cycle(idle, "body", "scale", lambda p: (1 + 0.012 * s(p), 1 + 0.025 * s(p), 1), 8)
    cycle(idle, "head", "rot", lambda p: (2.5 * s(p, 0.15), 3 * s(p * 1, 0.4), 0), 8)
    cycle(idle, "jaw", "rot", lambda p: (1.5 + 1.5 * s(p * 2), 0, 0), 8)
    cycle(idle, "tail", "rot", lambda p: (0, 0, 14 * s(p * 2)), 8)
    cycle(idle, "ear_l", "rot", lambda p: (0, 0, 10 * max(0, s(p * 2, 0.1)) ** 4), 16)
    cycle(idle, "ear_r", "rot", lambda p: (0, 0, -10 * max(0, s(p * 2, 0.6)) ** 4), 16)
    for k, b in enumerate(HEDGE):
        cycle(idle, b, "rot", lambda p, k=k: (2.5 * s(p, k * 0.21), 0, 3 * s(p, 0.1 + k * 0.17)), 8)
    cycle(idle, "beard", "rot", lambda p: (4 * s(p, 0.3), 0, 0), 8)

    walk = r.clip("walk", 1.2, loop=True)
    quad_gait(walk, LEGS4, swing=24, knee=26)
    body_bob(walk, "body", amp=0.6, roll=2.0, n=8)
    cycle(walk, "head", "rot", lambda p: (3 * c(p * 2, 0.1), 4 * s(p), 0), 8)
    cycle(walk, "tail", "rot", lambda p: (0, 0, 18 * s(p)), 8)
    for k, b in enumerate(HEDGE):
        cycle(walk, b, "rot", lambda p, k=k: (4 * c(p * 2, 0.15 + k * 0.05), 0, 0), 8)

    run = r.clip("run", 0.7, loop=True)
    # a bounding gallop: the forelegs reach together, the hind legs drive together
    quad_gait(run, [("leg_fl", "shin_fl", 0.0), ("leg_fr", "shin_fr", 0.08), ("leg_hl", "hock_hl", 0.5), ("leg_hr", "hock_hr", 0.58)],
              swing=42, knee=40)
    cycle(run, "body", "rot", lambda p: (7 * s(p, 0.25), 0, 0), 8)
    cycle(run, "body", "pos", lambda p: (0, 1.4 * c(p * 2), 0), 8)
    cycle(run, "head", "rot", lambda p: (14 - 6 * s(p, 0.25), 0, 0), 8)
    cycle(run, "tail", "rot", lambda p: (30 + 10 * s(p), 0, 0), 8)
    for k, b in enumerate(HEDGE):
        cycle(run, b, "rot", lambda p, k=k: (10 + 8 * c(p, 0.1 * k), 0, 0), 8)

    # generic wind-up / strike (Thornsnare and anything unmapped): head low, a snort, then a gore upward
    w = r.clip("windup", 1.0)
    w.rot("head", [(0, 0, 0, 0), (0.5, 22, 0, 0), (0.75, 18, 0, 0), (1.0, 26, 0, 0)])
    w.rot("body", [(0, 0, 0, 0), (1.0, 6, 0, 0)])
    w.pos("body", [(0, 0, 0, 0), (1.0, 0, -1, 1)])
    w.rot("jaw", [(0, 0, 0, 0), (0.6, 0, 0, 0), (0.7, 14, 0, 0), (0.85, 0, 0, 0)])
    st = r.clip("strike", 0.6)
    st.rot("head", [(0, 26, 0, 0), (0.12, -32, 0, 0), (0.3, -24, 0, 0), (0.6, 0, 0, 0)])
    st.rot("body", [(0, 6, 0, 0), (0.12, -6, 0, 0), (0.6, 0, 0, 0)])
    st.pos("body", [(0, 0, -1, 1), (0.12, 0, 1, -2), (0.6, 0, 0, 0)])
    bite = r.clip("bite", 0.5)
    bite.rot("head", [(0, 0, 0, 0), (0.1, 16, -8, 0), (0.22, -30, 6, 0), (0.5, 0, 0, 0)])
    bite.rot("jaw", [(0, 0, 0, 0), (0.1, 18, 0, 0), (0.22, 0, 0, 0)])

    # Bramble Charge: head right down, tusks forward, the hedge bristles, a hoof scrapes twice, then the rush
    cw = r.clip("charge_windup", 1.3)
    cw.rot("head", [(0, 0, 0, 0), (0.25, 30, 0, 0), (1.3, 34, 0, 0)])
    cw.rot("body", [(0, 0, 0, 0), (0.3, 9, 0, 0), (1.3, 11, 0, 0)])
    cw.pos("body", [(0, 0, 0, 0), (0.3, 0, -1.5, 2), (1.3, 0, -2, 3)])
    cw.rot("leg_fr", [(0, 0, 0, 0), (0.35, -40, 0, 0), (0.55, 25, 0, 0), (0.75, -40, 0, 0), (0.95, 25, 0, 0), (1.3, 0, 0, 0)])
    cw.rot("shin_fr", [(0, 0, 0, 0), (0.35, 40, 0, 0), (0.55, 0, 0, 0), (0.75, 40, 0, 0), (0.95, 0, 0, 0), (1.3, 0, 0, 0)])
    cw.rot("leg_hl", [(0, 0, 0, 0), (0.4, -14, 0, 0), (1.3, -16, 0, 0)])
    cw.rot("leg_hr", [(0, 0, 0, 0), (0.4, -14, 0, 0), (1.3, -16, 0, 0)])
    for k, b in enumerate(HEDGE):
        sgn = 1 if k % 2 == 0 else -1
        cw.rot(b, [(0, 0, 0, 0), (0.3 + 0.05 * k, -8, 0, 14 * sgn), (1.3, -10, 0, 18 * sgn)])
        cw.scale(b, [(0, 1, 1, 1), (0.35, 1, 1.18, 1), (1.3, 1, 1.22, 1)])
    cw.rot("tail", [(0, 0, 0, 0), (0.3, -30, 0, 0), (1.3, -40, 0, 0)])
    cw.rot("ear_l", [(0, 0, 0, 0), (0.3, 25, 0, 0), (1.3, 25, 0, 0)])
    cw.rot("ear_r", [(0, 0, 0, 0), (0.3, 25, 0, 0), (1.3, 25, 0, 0)])
    cs = r.clip("charge_strike", 0.8)
    cs.rot("head", [(0, 34, 0, 0), (0.1, 24, 0, 0), (0.55, 24, 0, 0), (0.68, -28, 0, 0), (0.8, 0, 0, 0)])
    cs.rot("body", [(0, 11, 0, 0), (0.1, -4, 0, 0), (0.6, -4, 0, 0), (0.8, 0, 0, 0)])
    cs.pos("body", [(0, 0, -2, 3), (0.1, 0, 0, -3), (0.6, 0, 0, -3), (0.8, 0, 0, 0)])
    for k, b in enumerate(HEDGE):
        sgn = 1 if k % 2 == 0 else -1
        cs.rot(b, [(0, -10, 0, 18 * sgn), (0.12, 30, 0, 4 * sgn), (0.6, 28, 0, 4 * sgn), (0.8, 0, 0, 0)])
        cs.scale(b, [(0, 1, 1.22, 1), (0.2, 1, 1.05, 1), (0.8, 1, 1, 1)])
    cs.rot("tail", [(0, -40, 0, 0), (0.2, 50, 0, 0), (0.8, 0, 0, 0)])

    # Root Stomp: rears on the hind legs, forelegs tucked, then slams the ground
    sw = r.clip("stomp_windup", 1.1)
    sw.rot("body", [(0, 0, 0, 0), (0.2, 4, 0, 0), (0.75, -30, 0, 0), (1.1, -34, 0, 0)])
    sw.pos("body", [(0, 0, 0, 0), (0.2, 0, -1, 0), (0.75, 0, 4, 2), (1.1, 0, 5, 2)])
    for nm in ("fl", "fr"):
        sw.rot("leg_" + nm, [(0, 0, 0, 0), (0.75, -50, 0, 0), (1.1, -58, 0, 0)])
        sw.rot("shin_" + nm, [(0, 0, 0, 0), (0.75, 70, 0, 0), (1.1, 76, 0, 0)])
    for nm in ("hl", "hr"):
        sw.rot("leg_" + nm, [(0, 0, 0, 0), (0.75, 30, 0, 0), (1.1, 34, 0, 0)])
    sw.rot("head", [(0, 0, 0, 0), (0.75, 18, 0, 0), (1.1, 22, 0, 0)])
    sw.rot("jaw", [(0, 0, 0, 0), (0.8, 20, 0, 0), (1.1, 26, 0, 0)])
    ss = r.clip("stomp_strike", 0.7)
    ss.rot("body", [(0, -34, 0, 0), (0.12, 8, 0, 0), (0.25, 3, 0, 0), (0.7, 0, 0, 0)])
    ss.pos("body", [(0, 0, 5, 2), (0.12, 0, -1.5, 0), (0.3, 0, 0, 0), (0.7, 0, 0, 0)])
    for nm in ("fl", "fr"):
        ss.rot("leg_" + nm, [(0, -58, 0, 0), (0.12, 10, 0, 0), (0.7, 0, 0, 0)])
        ss.rot("shin_" + nm, [(0, 76, 0, 0), (0.12, 0, 0, 0)])
    for nm in ("hl", "hr"):
        ss.rot("leg_" + nm, [(0, 34, 0, 0), (0.15, 0, 0, 0)])
    ss.rot("head", [(0, 22, 0, 0), (0.12, 30, 0, 0), (0.4, 8, 0, 0), (0.7, 0, 0, 0)])
    ss.rot("jaw", [(0, 26, 0, 0), (0.15, 4, 0, 0), (0.7, 0, 0, 0)])
    for k, b in enumerate(HEDGE):
        ss.rot(b, [(0, 0, 0, 0), (0.12, 16, 0, 0), (0.3, -10, 0, 0), (0.5, 4, 0, 0), (0.7, 0, 0, 0)])

    # Pollen Haze / Thorn Burst: she shakes the hedge like a wet dog
    hw = r.clip("shake_windup", 1.2)
    hw.rot("body", [(0, 0, 0, 0), (0.3, 0, 0, 6), (0.45, 0, 0, -8), (0.6, 0, 0, 9), (0.75, 0, 0, -9), (0.9, 0, 0, 7), (1.2, 0, 0, 0)])
    hw.rot("head", [(0, 0, 0, 0), (0.3, -12, 14, 0), (0.45, -12, -16, 0), (0.6, -12, 16, 0), (0.75, -12, -16, 0), (1.2, -10, 0, 0)])
    for k, b in enumerate(HEDGE):
        sgn = 1 if k % 2 == 0 else -1
        hw.rot(b, [(0, 0, 0, 0), (0.3, 0, 0, 14 * sgn), (0.45, 0, 0, -16 * sgn), (0.6, 0, 0, 16 * sgn), (0.75, 0, 0, -16 * sgn), (1.2, 0, 0, 20 * sgn)])
        hw.scale(b, [(0, 1, 1, 1), (1.2, 1.1, 1.25, 1.1)])
    hs = r.clip("shake_strike", 0.7)
    for k, b in enumerate(HEDGE):
        sgn = 1 if k % 2 == 0 else -1
        hs.rot(b, [(0, 0, 0, 20 * sgn), (0.1, -12, 0, 34 * sgn), (0.7, 0, 0, 0)])
        hs.scale(b, [(0, 1.1, 1.25, 1.1), (0.1, 1.2, 1.35, 1.2), (0.7, 1, 1, 1)])
    hs.rot("head", [(0, -10, 0, 0), (0.1, -26, 0, 0), (0.7, 0, 0, 0)])
    hs.rot("jaw", [(0, 0, 0, 0), (0.1, 24, 0, 0), (0.7, 0, 0, 0)])

    roar = r.clip("roar", 1.4)
    roar.rot("head", [(0, 0, 0, 0), (0.25, 12, 0, 0), (0.5, -34, 0, 0), (1.05, -30, 0, 0), (1.4, 0, 0, 0)])
    roar.rot("jaw", [(0, 0, 0, 0), (0.45, 34, 0, 0), (0.6, 28, 0, 0), (0.75, 34, 0, 0), (1.05, 30, 0, 0), (1.4, 0, 0, 0)])
    roar.rot("body", [(0, 0, 0, 0), (0.5, -9, 0, 0), (1.1, -7, 0, 0), (1.4, 0, 0, 0)])
    roar.pos("body", [(0, 0, 0, 0), (0.5, 0, 1.5, 0), (1.1, 0, 1.2, 0), (1.4, 0, 0, 0)])
    for k, b in enumerate(HEDGE):
        sgn = 1 if k % 2 == 0 else -1
        roar.rot(b, [(0, 0, 0, 0), (0.5, -14, 0, 20 * sgn), (0.6, -10, 0, 16 * sgn), (0.7, -14, 0, 20 * sgn), (1.1, -10, 0, 16 * sgn), (1.4, 0, 0, 0)])
        roar.scale(b, [(0, 1, 1, 1), (0.5, 1.1, 1.25, 1.1), (1.1, 1.1, 1.2, 1.1), (1.4, 1, 1, 1)])
    roar.rot("ear_l", [(0, 0, 0, 0), (0.5, 30, 0, 0), (1.4, 0, 0, 0)])
    roar.rot("ear_r", [(0, 0, 0, 0), (0.5, 30, 0, 0), (1.4, 0, 0, 0)])

    warn = r.clip("warn", 0.9)
    warn.rot("head", [(0, 0, 0, 0), (0.15, 14, 12, 0), (0.3, 14, -12, 0), (0.45, 10, 8, 0), (0.9, 0, 0, 0)])
    warn.rot("leg_fl", [(0, 0, 0, 0), (0.2, -28, 0, 0), (0.4, 12, 0, 0), (0.6, 0, 0, 0)])
    warn.rot("jaw", [(0, 0, 0, 0), (0.15, 12, 0, 0), (0.35, 0, 0, 0)])

    hurt = r.clip("hurt", 0.45)
    hurt.rot("body", [(0, 0, 0, 0), (0.06, -5, 0, 6), (0.2, 2, 0, -3), (0.45, 0, 0, 0)])
    hurt.rot("head", [(0, 0, 0, 0), (0.06, -20, 18, 0), (0.25, 6, -6, 0), (0.45, 0, 0, 0)])
    for k, b in enumerate(HEDGE):
        hurt.rot(b, [(0, 0, 0, 0), (0.06, 0, 0, 12 if k % 2 else -12), (0.2, 0, 0, -6 if k % 2 else 6), (0.45, 0, 0, 0)])

    death = r.clip("death", 1.8)
    death.rot("body", [(0, 0, 0, 0), (0.25, -8, 0, 0), (0.7, 18, 0, 10), (1.2, 8, 0, 78), (1.8, 6, 0, 84)])
    death.pos("body", [(0, 0, 0, 0), (0.25, 0, 1, 0), (0.7, 0, -4, 0), (1.2, 0, -6, 0), (1.8, 0, -7, 0)])
    death.rot("head", [(0, 0, 0, 0), (0.25, -30, 0, 0), (0.7, 20, 0, 0), (1.3, 30, 0, -10), (1.8, 32, 0, -12)])
    death.rot("jaw", [(0, 0, 0, 0), (0.25, 30, 0, 0), (1.0, 18, 0, 0), (1.8, 22, 0, 0)])
    for nm in ("fl", "fr"):
        death.rot("leg_" + nm, [(0, 0, 0, 0), (0.7, -60, 0, 0), (1.8, -50, 0, 0)])
        death.rot("shin_" + nm, [(0, 0, 0, 0), (0.7, 80, 0, 0), (1.8, 70, 0, 0)])
    for nm in ("hl", "hr"):
        death.rot("leg_" + nm, [(0, 0, 0, 0), (1.0, 30, 0, 0), (1.8, 40, 0, 0)])
    for k, b in enumerate(HEDGE):
        death.rot(b, [(0, 0, 0, 0), (0.7, 10, 0, 0), (1.8, 20, 0, -10 if k % 2 else 10)])
        death.scale(b, [(0, 1, 1, 1), (1.8, 1, 0.85, 1)])


def paint_briar_sow(r, P):
    hide = Ramp("#100b08", "#1c130e", "#2a1d15", "#3a291c", "#4c3725", "#624830", "#7d613f")
    moss = Ramp("#14200f", "#1e3016", "#2b431d", "#3b5a26", "#4f7531", "#6a923f", "#8bb153")
    bone = Ramp("#4e3e2a", "#7a6648", "#a58f6c", "#c9b893", "#e2d7bb", "#f4eedc")
    hoof = Ramp("#0d0b0a", "#1b1714", "#2b2520", "#3c342d", "#4e443a")
    stem = Ramp("#1a0d0a", "#2c1510", "#422016", "#5a2e1d", "#733f27", "#8e5634")
    leaf = Ramp("#0e2412", "#163819", "#1f4d22", "#2c662d", "#3f8239", "#5ba04c")
    snout = Ramp("#22191a", "#382a28", "#4f3c37", "#6a524a", "#86695d", "#a3857a")
    berry = (hexc("#b0262e"), hexc("#6a1219"))
    rose = (hexc("#f2cdd6"), hexc("#ffd84a"))
    rng = P.rng
    fur = combine(p_strokes(3, 3, 0.7, 0.5, 0.75), p_blotch("sow", 7, 0.35))
    crest = lambda f, i, j: 0.7 if f.name == "top" and abs(i - (f.w - 1) / 2) < 1.6 else 0.0  # noqa: E731
    P.paint("chest", hide, 2.7, combine(fur, crest))
    P.paint("rump", hide, 2.5, combine(p_strokes(3, 3, 0.7, 0.5, 0.75, seed=1), p_blotch("sow2", 7, 0.35), crest))
    for nm in ("hump", "ridge", "moss_rump", "bush"):
        P.paint(nm, moss, 3.3 if nm != "bush" else 2.8, combine(p_strokes(2, 2, 0.8, 0.6, 0.8, seed=7), p_blotch("m" + nm, 4, 0.4)))
    # moss creeps down the flanks from the hedge, in tongues rather than a band
    for nm in ("chest", "rump"):
        for fname in ("west", "east", "north", "south"):
            f = P.face(nm, fname)
            for i in range(f.w):
                hv = (i * 7 + (3 if fname == "east" else 0) + (5 if nm == "rump" else 0)) % 11
                depth = 1 + (hv % 4) + (2 if hv in (2, 7) else 0)
                for j in range(min(depth, f.h)):
                    f.set(i, j, moss.at(3.7 - j * 0.6 + (0.4 if j == 0 else 0) - (0.8 if j == depth - 1 else 0)))
    P.paint("skull", hide, 2.9, combine(p_strokes(3, 2, 0.6, 0.4, 0.6, seed=2), crest))
    P.paint("brow", hide, 2.2, p_strokes(2, 2, 0.6, 0.6, 0.8, seed=3))
    P.paint("snout", snout, 2.7, lambda f, i, j: -0.6 if f.name in SIDES and j % 3 == 2 and i % 4 != 0 else 0)
    P.paint("jaw", snout, 2.1, lambda f, i, j: -0.5 if f.name in SIDES and j == 2 else 0)
    f = P.face("nose", "north")
    for i, j in f.each():
        f.set(i, j, snout.at(3.4 - (0.7 if j == f.h - 1 or i in (0, f.w - 1) else 0) + (0.6 if j == 0 else 0)))
    for (i, j) in ((2, 2), (2, 3), (3, 3), (7, 2), (7, 3), (6, 3)):
        f.set(i, j, snout.at(0))
    P.paint("nose", snout, 3.0, faces=("top", "bottom", "west", "east", "south"))
    # eyes: small, deep under the brow, near the front of the skull sides, glowing green
    for fname, ex in (("west", None), ("east", None)):
        f = P.face("skull", fname)
        ex = f.w - 4 if fname == "west" else 2
        for (dx, dy, col) in ((0, 0, hexc("#0a0806")), (1, 0, hexc("#0a0806")), (0, 1, hexc("#24401a")), (1, 1, hexc("#0a0806")), (-1, 0, hide.at(1)), (2, 0, hide.at(1))):
            f.set(ex + dx, 2 + dy, col)
        lit = ex + (1 if fname == "west" else 0)
        f.set(lit, 3, hexc("#b8ff8a"))
        g = P.face("skull", fname, glow=True)
        g.set(lit, 3, hexc("#e0ffc0", 255))
        g.set(ex + (0 if fname == "west" else 1), 3, hexc("#8aff6a", 200))
        g.set(lit, 2, hexc("#6aff5a", 110))
    for fname in ("west", "east"):
        f = P.face("jaw", fname)
        for i in range(f.w):
            f.set(i, 0, snout.at(0.4))
    P.paint("tusk1_l", bone, 2.3, lambda f, i, j: -0.5 if f.name in SIDES and j == f.h - 1 else 0)
    P.paint("tusk2_l", bone, 3.3)
    P.paint("tusk3_l", bone, 4.2)
    P.paint("ltusk_l", bone, 3.5)
    for fname in ("top", "north", "west", "east", "south", "bottom"):
        g = P.face("tusk3_l", fname, glow=True)
        for i, j in g.each():
            if fname in ("top", "north") or j <= 1:
                g.set(i, j, hexc("#e8ffc0", 110))
    for nm in ("legf_l", "thigh_l"):
        P.paint(nm, hide, 2.5, p_strokes(3, 3, 0.7, 0.4, 0.7, seed=4))
    for nm in ("shinf_l", "hock_l"):
        P.paint(nm, hide, 2.0, p_strokes(2, 2, 0.6, 0.4, 0.7, seed=5))
        for fname in SIDES + ("bottom",):
            f = P.face(nm, fname)
            for i, j in f.each():
                if fname == "bottom" or j >= f.h - 2:
                    f.set(i, j, hoof.at(2.4 - (j - (f.h - 2)) * 0.9 + (0.8 if (fname != "bottom" and j == f.h - 2) else 0)))
            if fname == "north":
                for j in range(f.h - 2, f.h):
                    f.set(f.w // 2, j, hoof.at(0))
    P.paint("tail", hide, 2.4, p_strokes(2, 2, 0.6, 0.4, 0.7))
    P.paint("tuft", leaf, 3.1, p_scales(2, 2, 0.8))
    P.paint("ear_l", hide, 2.4, p_strokes(2, 2, 0.6, 0.4, 0.7))
    f = P.face("ear_l", "north")
    for j in range(1, f.h - 1):
        f.set(0, j, snout.at(3))
        f.set(1, j, snout.at(2))
    # canes: dark red-brown with pale thorn points along the sides, every other segment a little lighter
    for c in r.cubes:
        if c.name.startswith("bramble") and "_s" in c.name:
            k = int(c.name[-1])
            P.paint(c, stem, 2.6 + 0.4 * k, lambda f, i, j: (1.6 if f.name in SIDES and (j + i * 2) % 3 == 0 and i in (0, f.w - 1) else 0))
        if c.name.endswith("_leaf"):
            P.paint(c, leaf, 3.1, p_scales(3, 2, 0.9))
    for nm in [c.name for c in r.cubes if c.name.endswith("_leaf")] + ["tuft", "bush"]:
        for fname in ("top", "west", "east", "north", "south"):
            f = P.face(nm, fname)
            for k in range(max(1, f.w * f.h // 16)):
                i, j = rng.randrange(f.w), rng.randrange(f.h)
                f.set(i, j, berry[0])
                if i + 1 < f.w:
                    f.set(i + 1, j, berry[1])
            if fname == "top" and nm != "tuft":
                i, j = f.w // 2, f.h // 2
                for di, dj in ((0, 0), (-1, 0), (1, 0), (0, -1), (0, 1)):
                    f.set(i + di, j + dj, rose[0])
                f.set(i, j, rose[1])
                P.face(nm, fname, glow=True).set(i, j, hexc("#f4ff9a", 210))
    for nm in ("sprig_a", "sprig_b", "sprig_d"):
        if not P.own(nm):
            continue
        for fname in ("north", "south"):
            f = P.face(nm, fname)
            sprig(f, rng, stem, leaf, berry=berry, flower=rose)
            g = P.face(nm, fname, glow=True)
            for i, j in f.each():
                if f.get(i, j)[:3] == rose[1][:3]:
                    g.set(i, j, hexc("#f4ff9a", 230))
    for nm in ("mane_l", "beard"):
        for fname in ("west", "east", "north", "south"):
            f = P.face(nm, fname)
            if f.w and f.h:
                ragged(f, rng, moss, 3.2, minlen=0.4)
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# The Dune Wyrm: a plated sand-swimmer with a three-way jaw and a spine of sun-glass; it swims the air like water,
# undulating up and down (the Plumed Coil, by contrast, slithers side to side)

WYRM_SEGS = [(11, 10, 9), (10, 9, 9), (9, 8, 8), (8, 7, 8), (7, 6, 7), (6, 5, 7), (5, 4, 6), (4, 3, 6), (3, 3, 5)]


def dune_wyrm():
    r = Rig("dune_wyrm", (128, 128))
    r.shadow = 0.9
    head = r.bone("head", (0, 15, -6))
    head.cube("skull", -6, -5, -12, 12, 10, 12)
    head.cube("snout", -4, -3, -18, 8, 6, 6)
    head.cube("brow", -6.5, -6.5, -11, 13, 2, 9)
    jaw = head.bone("jaw", (0, 4, -2))
    jaw.cube("jaw", -5, 0, -16, 10, 2, 15)
    for side, nm in ((1, "l"), (-1, "r")):
        mir = side < 0
        sh = None if side > 0 else "mand_l"
        m = head.bone("mand_" + nm, (side * 6, 2, -7), (0, side * 14, 0))
        m.cube("mand_" + nm, -1.5, -1.5, -12, 3, 4, 12, mirror=mir, share=sh)
        tip = m.bone("mandtip_" + nm, (0, 0, -11.5), (0, side * 42, 0))
        tip.cube("mandtip_" + nm, -1, -1, -6, 2, 3, 6, mirror=mir, share=None if side > 0 else "mandtip_l")
        claw = head.bone("arm_" + nm, (side * 4, 6, 6), (40, 0, side * -20))
        claw.cube("arm_" + nm, -1, 0, -1, 2, 5, 2, mirror=mir, share=None if side > 0 else "arm_l")
        claw.bone("hand_" + nm, (0, 5, 0), (-60, 0, 0)).cube("hand_" + nm, -1.5, 0, -2, 3, 1, 3, mirror=mir, share=None if side > 0 else "hand_l")
    # a crest of glass shards along the skull
    for k, (h, z, tilt) in enumerate(((6, -9, -30), (9, -5, -35), (8, -1, -40))):
        head.bone(f"crest{k}", (0, -6, z), (tilt, 0, 0)).cube(f"crest{k}", -1.5, -h, -1, 3, h, 3)
    node = head
    z = 0
    for k, (w, hgt, ln) in enumerate(WYRM_SEGS):
        node = node.bone(f"seg{k}", (0, 0, z), (0, 0, 0))
        node.cube(f"seg{k}", -w / 2, -hgt / 2, 0, w, hgt, ln)
        if k < 6:
            fh = max(2, 6 - k)
            node.bone(f"fin{k}", (0, -hgt / 2, ln * 0.4), (-35, 0, 0)).cube(f"fin{k}", -0.5, -fh, -1, 1, fh, 3)
        z = ln - 0.5
    node.bone("fan", (0, 0, z), (0, 0, 0)).cube("fan", 0, -5, 0, 0, 10, 9)
    r.look = [("head", 0.7, 0.7), ("seg0", 0.25, 0.2)]
    r.moves = {"Surface": "surface", "Sand Dive": "dive", "Sandfall": "rattle", "Sunscorch Ring": "rattle", "Glass Shards": "rattle"}
    r.pack()
    wyrm_clips(r)
    return r


def wyrm_clips(r):
    segs = [f"seg{k}" for k in range(len(WYRM_SEGS))]
    idle = r.clip("idle", 3.0, loop=True)
    wave(idle, segs, "x", 6, lag=0.1, n=8)
    cycle(idle, "head", "rot", lambda p: (-3 * s(p, 0.2), 4 * s(p * 1, 0.5), 0), 8)
    cycle(idle, "head", "pos", lambda p: (0, 0.8 * s(p), 0), 8)
    cycle(idle, "jaw", "rot", lambda p: (4 + 3 * s(p * 2), 0, 0), 8)
    cycle(idle, "mand_l", "rot", lambda p: (0, -6 * max(0, s(p * 2)), 0), 8)
    cycle(idle, "mand_r", "rot", lambda p: (0, 6 * max(0, s(p * 2)), 0), 8)
    fly = r.clip("fly", 1.6, loop=True)
    wave(fly, segs, "x", 11, lag=0.12, n=8)
    wave(fly, segs[2:], "y", 5, lag=0.1, n=8)
    cycle(fly, "head", "rot", lambda p: (5 * s(p, 0.15), 0, 0), 8)
    cycle(fly, "fan", "rot", lambda p: (0, 18 * s(p, 0.6), 0), 8)
    for nm in ("arm_l", "arm_r"):
        fly.rot(nm, [(0, 30, 0, 0), (1.6, 30, 0, 0)])
    # generic: rear back with the jaws parting, then a snapping lunge
    w = r.clip("windup", 0.9)
    w.rot("head", [(0, 0, 0, 0), (0.9, -28, 0, 0)])
    w.rot("seg0", [(0, 0, 0, 0), (0.9, 18, 0, 0)])
    w.rot("seg1", [(0, 0, 0, 0), (0.9, 14, 0, 0)])
    w.rot("jaw", [(0, 0, 0, 0), (0.9, 26, 0, 0)])
    for nm, sg in (("mand_l", -1), ("mand_r", 1)):
        w.rot(nm, [(0, 0, 0, 0), (0.9, 0, sg * 30, 0)])
    st = r.clip("strike", 0.6)
    st.rot("head", [(0, -28, 0, 0), (0.12, 22, 0, 0), (0.3, 8, 0, 0), (0.6, 0, 0, 0)])
    st.pos("head", [(0, 0, 0, 0), (0.12, 0, -2, -6), (0.6, 0, 0, 0)])
    st.rot("seg0", [(0, 18, 0, 0), (0.12, -14, 0, 0), (0.6, 0, 0, 0)])
    st.rot("seg1", [(0, 14, 0, 0), (0.12, -8, 0, 0), (0.6, 0, 0, 0)])
    st.rot("jaw", [(0, 26, 0, 0), (0.1, 30, 0, 0), (0.16, 0, 0, 0), (0.6, 0, 0, 0)])
    for nm, sg in (("mand_l", -1), ("mand_r", 1)):
        st.rot(nm, [(0, 0, sg * 30, 0), (0.1, 0, sg * 34, 0), (0.16, 0, -sg * 8, 0), (0.6, 0, 0, 0)])
    bite = r.clip("bite", 0.5)
    bite.rot("head", [(0, 0, 0, 0), (0.08, -14, 0, 0), (0.18, 16, 0, 0), (0.5, 0, 0, 0)])
    bite.rot("jaw", [(0, 0, 0, 0), (0.08, 28, 0, 0), (0.18, 0, 0, 0)])
    for nm, sg in (("mand_l", -1), ("mand_r", 1)):
        bite.rot(nm, [(0, 0, 0, 0), (0.08, 0, sg * 30, 0), (0.18, 0, -sg * 6, 0), (0.5, 0, 0, 0)])
    # Surface (signature): it dives head-first as if into sand, coiling down out of sight...
    sw = r.clip("surface_windup", 1.0)
    sw.rot("head", [(0, 0, 0, 0), (0.3, -15, 0, 0), (1.0, 70, 0, 0)])
    sw.pos("head", [(0, 0, 0, 0), (0.3, 0, 3, 0), (1.0, 0, -14, -4)])
    for k, sg in enumerate(["seg0", "seg1", "seg2", "seg3"]):
        sw.rot(sg, [(0, 0, 0, 0), (0.3 + 0.08 * k, 8, 0, 0), (1.0, -26 + k * 4, 0, 0)])
    sw.scale("head", [(0, 1, 1, 1), (0.85, 1, 1, 1), (1.0, 0.8, 0.8, 0.8)])
    # ...and bursts up beside you, jaws flung wide, then settles
    ss = r.clip("surface_strike", 1.0)
    ss.rot("head", [(0, -60, 0, 0), (0.15, -50, 0, 0), (0.35, 10, 0, 0), (1.0, 0, 0, 0)])
    ss.pos("head", [(0, 0, -12, 2), (0.15, 0, 8, 0), (0.4, 0, 2, 0), (1.0, 0, 0, 0)])
    for k, sg in enumerate(["seg0", "seg1", "seg2", "seg3"]):
        ss.rot(sg, [(0, 40 - k * 6, 0, 0), (0.18, 26 - k * 5, 0, 0), (0.45, -6, 0, 0), (1.0, 0, 0, 0)])
    ss.rot("jaw", [(0, 34, 0, 0), (0.15, 36, 0, 0), (0.3, 0, 0, 0)])
    for nm, sg in (("mand_l", -1), ("mand_r", 1)):
        ss.rot(nm, [(0, 0, sg * 42, 0), (0.15, 0, sg * 45, 0), (0.3, 0, -sg * 8, 0), (0.6, 0, 0, 0)])
    for k in range(3):
        ss.scale(f"crest{k}", [(0, 1, 1.3, 1), (0.3, 1, 1.4, 1), (1.0, 1, 1, 1)])
    # Sand Dive: an S-coil drawn back like a spring, then the body snaps straight along the charge
    dw = r.clip("dive_windup", 0.9)
    dw.rot("head", [(0, 0, 0, 0), (0.9, 18, 0, 0)])
    for k, sg in enumerate(segs[:5]):
        dw.rot(sg, [(0, 0, 0, 0), (0.9, 0, (24 if k % 2 == 0 else -28), 0)])
    dw.rot("jaw", [(0, 0, 0, 0), (0.9, 18, 0, 0)])
    for nm, sg in (("mand_l", -1), ("mand_r", 1)):
        dw.rot(nm, [(0, 0, 0, 0), (0.9, 0, sg * 28, 0)])
    ds = r.clip("dive_strike", 0.8)
    ds.rot("head", [(0, 18, 0, 0), (0.1, 6, 0, 0), (0.8, 0, 0, 0)])
    for k, sg in enumerate(segs[:5]):
        ds.rot(sg, [(0, 0, (24 if k % 2 == 0 else -28), 0), (0.1, 0, 0, 0), (0.8, 0, 0, 0)])
    ds.rot("jaw", [(0, 18, 0, 0), (0.6, 24, 0, 0), (0.7, 0, 0, 0)])
    # Sandfall, the ring, the shards: it rears and shakes its glass spine like a rattle
    rw = r.clip("rattle_windup", 1.2)
    rw.rot("head", [(0, 0, 0, 0), (0.4, -40, 0, 0), (1.2, -44, 0, 0)])
    rw.rot("seg0", [(0, 0, 0, 0), (0.4, 30, 0, 0), (1.2, 32, 0, 0)])
    rw.rot("seg1", [(0, 0, 0, 0), (0.4, 18, 0, 0), (1.2, 20, 0, 0)])
    for k in range(6):
        rw.rot(f"fin{k}", [(0, 0, 0, 0)] + [(0.3 + 0.1 * q, 0, 0, (10 if q % 2 else -10)) for q in range(9)])
        rw.scale(f"fin{k}", [(0, 1, 1, 1), (0.5, 1, 1.35, 1), (1.2, 1, 1.4, 1)])
    rw.rot("jaw", [(0, 0, 0, 0), (0.5, 30, 0, 0), (1.2, 32, 0, 0)])
    rs = r.clip("rattle_strike", 0.6)
    rs.rot("head", [(0, -44, 0, 0), (0.15, -10, 0, 0), (0.6, 0, 0, 0)])
    rs.rot("seg0", [(0, 32, 0, 0), (0.15, 6, 0, 0), (0.6, 0, 0, 0)])
    rs.rot("seg1", [(0, 20, 0, 0), (0.6, 0, 0, 0)])
    rs.rot("jaw", [(0, 32, 0, 0), (0.6, 0, 0, 0)])
    for k in range(6):
        rs.scale(f"fin{k}", [(0, 1, 1.4, 1), (0.1, 1.2, 1.6, 1.2), (0.6, 1, 1, 1)])
    roar = r.clip("roar", 1.4)
    roar.rot("head", [(0, 0, 0, 0), (0.3, -52, 0, 0), (1.0, -48, 0, 0), (1.4, 0, 0, 0)])
    roar.rot("seg0", [(0, 0, 0, 0), (0.3, 34, 0, 0), (1.0, 30, 0, 0), (1.4, 0, 0, 0)])
    roar.rot("seg1", [(0, 0, 0, 0), (0.3, 16, 0, 0), (1.0, 14, 0, 0), (1.4, 0, 0, 0)])
    roar.rot("jaw", [(0, 0, 0, 0), (0.3, 40, 0, 0), (1.0, 38, 0, 0), (1.4, 0, 0, 0)])
    for nm, sg in (("mand_l", -1), ("mand_r", 1)):
        roar.rot(nm, [(0, 0, 0, 0), (0.3, 0, sg * 45, 0), (0.6, 0, sg * 38, 0), (0.8, 0, sg * 45, 0), (1.0, 0, sg * 40, 0), (1.4, 0, 0, 0)])
    warn = r.clip("warn", 0.9)
    for nm, sg in (("mand_l", -1), ("mand_r", 1)):
        warn.rot(nm, [(0, 0, 0, 0), (0.1, 0, sg * 30, 0), (0.2, 0, 0, 0), (0.3, 0, sg * 30, 0), (0.4, 0, 0, 0), (0.5, 0, sg * 30, 0), (0.6, 0, 0, 0)])
    warn.rot("head", [(0, 0, 0, 0), (0.3, -12, 10, 0), (0.6, -12, -10, 0), (0.9, 0, 0, 0)])
    hurt = r.clip("hurt", 0.45)
    hurt.rot("head", [(0, 0, 0, 0), (0.06, -24, 14, 0), (0.25, 6, -4, 0), (0.45, 0, 0, 0)])
    for k, sg in enumerate(segs[:4]):
        hurt.rot(sg, [(0, 0, 0, 0), (0.08 + 0.03 * k, 0, (14 if k % 2 else -14), 0), (0.45, 0, 0, 0)])
    death = r.clip("death", 1.8)
    death.pos("head", [(0, 0, 0, 0), (0.3, 0, 4, 0), (1.2, 0, -8, 0), (1.8, 0, -8.5, 0)])
    death.rot("head", [(0, 0, 0, 0), (0.3, -40, 0, 0), (1.0, 20, 0, 40), (1.8, 12, 0, 70)])
    death.rot("jaw", [(0, 0, 0, 0), (0.3, 40, 0, 0), (1.8, 26, 0, 0)])
    for k, sg in enumerate(segs):
        death.rot(sg, [(0, 0, 0, 0), (0.3, 10, 0, 0), (1.2, -6 + (k % 2) * 8, (16 if k % 2 else -16), 0), (1.8, -4, (14 if k % 2 else -14), 0)])


def paint_dune_wyrm(r, P):
    sand = Ramp("#3e2c18", "#5c4426", "#7e6034", "#a07c45", "#c29c5c", "#dcbc7c", "#f0dba6")
    plate = Ramp("#241a10", "#3a2a18", "#56402a", "#76593a", "#94744a", "#b8955e")
    belly = Ramp("#5e4a30", "#86703f", "#ad9558", "#cdb878", "#e6d59c")
    glass = Ramp("#5a3a10", "#8a5a14", "#c0841e", "#e8aa36", "#ffd06a", "#fff0b8")
    mouth = Ramp("#1a0a08", "#2e120e", "#4c1c16", "#6e2a1e")
    # the back is armour: overlapping sandstone-coloured plates, darker seams; the belly pale and banded
    def back(f, i, j):
        if f.name == "bottom":
            return 0.0
        if f.name in ("west", "east"):
            return 0.9 if j >= f.h - 2 else (-1.0 if (i + 1) % 4 == 0 else 0.4 if i % 4 == 0 else 0.0)
        return -1.0 if (j + 1) % 4 == 0 else 0.4 if j % 4 == 0 else 0.0
    for k in range(len(WYRM_SEGS)):
        P.paint(f"seg{k}", plate, 3.0 - k * 0.12, back)
        f = P.face(f"seg{k}", "bottom")
        for i, j in f.each():
            f.set(i, j, belly.at(2.6 - (0.9 if j % 3 == 2 else 0)))
        # molten light in the seams between plates: the glow layer carries the wyrm at night
        for fname in ("top", "west", "east"):
            g = P.face(f"seg{k}", fname, glow=True)
            for i, j in g.each():
                seam = ((i + 1) % 4 == 0) if fname != "top" else ((j + 1) % 4 == 0)
                if seam and (i + j * 3 + k) % 3 != 0 and not (fname != "top" and j >= g.h - 2):
                    g.set(i, j, hexc("#ff9a2a", 170))
    P.paint("skull", plate, 3.2, combine(p_plates(5, 4, 0.8), lambda f, i, j: 0.4 if f.name == "top" else 0))
    P.paint("brow", plate, 3.8, p_bands(2, 0.6, vertical=True))
    P.paint("snout", plate, 3.4, p_plates(4, 3, 0.6))
    P.paint("jaw", belly, 2.4, lambda f, i, j: -0.7 if f.name in SIDES and j == 0 else 0)
    for fname in ("top",):
        f = P.face("jaw", fname)
        for i, j in f.each():
            f.set(i, j, mouth.at(1 + (1 if i in (0, f.w - 1) else 0)))
        for i in range(0, f.w, 2):
            f.set(i, 0, hexc("#f2e6c8"))
    P.paint("mand_l", sand, 3.6, lambda f, i, j: -0.8 if f.name in SIDES and j == f.h - 1 else 0)
    P.paint("mandtip_l", glass, 4.0)
    for fname in ("top", "north", "west", "east"):
        g = P.face("mandtip_l", fname, glow=True)
        for i, j in g.each():
            g.set(i, j, hexc("#ffcf6a", 140))
    for nm in ("arm_l", "hand_l"):
        P.paint(nm, plate, 2.8)
    # the eyes: slits of molten gold under the brow, both sides
    for fname in ("west", "east"):
        f = P.face("skull", fname)
        g = P.face("skull", fname, glow=True)
        x0 = f.w - 5 if fname == "west" else 1
        for dx in range(4):
            f.set(x0 + dx, 2, hexc("#120806"))
            f.set(x0 + dx, 3, hexc("#ffb347") if 0 < dx < 3 else hexc("#120806"))
            if 0 < dx < 3:
                g.set(x0 + dx, 3, hexc("#ffe9a0", 255))
                g.set(x0 + dx, 2, hexc("#ff9a2a", 120))
    # glass: the crest, the fins and the tail fan glow amber, brightest at their edges
    for c in r.cubes:
        if c.name.startswith("crest") or c.name.startswith("fin"):
            P.paint(c, glass, 3.4, lambda f, i, j: 1.0 if j == 0 or i == 0 else -0.6 if (i + j) % 3 == 0 else 0)
            for fname in ("top", "north", "south", "west", "east"):
                g = P.face(c, fname, glow=True)
                for i, j in g.each():
                    g.set(i, j, hexc("#ffcf6a", 200 if j == 0 else 120))
    for fname in ("west", "east"):
        f = P.face("fan", fname)
        g = P.face("fan", fname, glow=True)
        clear(f)
        for i, j in f.each():
            # a fan of glass rays spreading back from the tail tip
            dy = abs(j - f.h / 2 + 0.5)
            if dy <= 1 + i * 0.55:
                ray = (j + i // 2) % 3 == 0
                f.set(i, j, glass.at(4.2 if ray else 2.8 + (0.5 if i == 0 else 0)))
                if ray or dy > i * 0.55 - 0.5:
                    g.set(i, j, hexc("#ffcf6a", 180))
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# The Plumed Coil: a slender jade serpent with a feathered headdress, a collar of plumes that flares and rattles
# before it strikes, and quetzal streamers for a tail; it slithers through the air side to side

COIL_SEGS = [(7, 7, 7), (7, 7, 7), (6, 6, 7), (6, 6, 7), (5, 5, 7), (5, 5, 6), (4, 4, 6), (3, 3, 6), (3, 3, 6), (2, 2, 6)]
RUFF = [(-80, 0), (-52, 0), (-26, 0), (0, 0), (26, 0), (52, 0), (80, 0)]


def plumed_coil():
    r = Rig("plumed_coil", (128, 128))
    r.shadow = 0.7
    head = r.bone("head", (0, 15, -6))
    head.cube("skull", -4, -3.5, -9, 8, 7, 9)
    head.cube("snout", -3, -2.5, -14, 6, 5, 5)
    head.cube("brows", -4.5, -4, -8, 9, 2, 4)
    jaw = head.bone("jaw", (0, 2.5, -2))
    jaw.cube("jaw", -3, 0, -12, 6, 2, 12)
    jaw.bone("fang_l", (1.8, 0, -11), (0, 0, 0)).cube("fang_l", -0.5, -2, -0.5, 1, 2, 1)
    jaw.bone("fang_r", (-1.8, 0, -11), (0, 0, 0)).cube("fang_r", -0.5, -2, -0.5, 1, 2, 1, mirror=True, share="fang_l")
    # the headdress: long plumes swept back from the brow, the middle one tallest
    for k, (yaw, tilt, ln) in enumerate(((-22, -18, 16), (-8, -30, 20), (8, -30, 20), (22, -18, 16))):
        head.bone(f"plume{k}", (0, -4, -5), (tilt, yaw, 0)).cube(f"plume{k}", 0, -5, 0, 0, 5, ln,
                                                                  share=None if k < 2 else ("plume0" if k == 3 else "plume1"))
    # the collar: a fan of plumes behind the head that flares open and rattles
    collar = head.bone("collar", (0, 0, 1))
    for k, (zr, _) in enumerate(RUFF):
        b = collar.bone(f"ruff{k}", (0, 0, 0), (25, 0, zr))
        b.cube(f"ruff{k}", -2.5, -12, 0, 5, 12, 0, share=None if k == 0 else "ruff0")
    node = head
    z = 0
    for k, (w, hgt, ln) in enumerate(COIL_SEGS):
        node = node.bone(f"seg{k}", (0, 0, z))
        node.cube(f"seg{k}", -w / 2, -hgt / 2, 0, w, hgt, ln)
        if k in (2, 5):
            for side, nm in ((1, "l"), (-1, "r")):
                node.bone(f"wing{k}_{nm}", (side * w / 2, -hgt / 2 + 1, 1), (0, side * 25, side * -30)).cube(
                    f"wing{k}_{nm}", 0 if side > 0 else -10, 0, 0, 10, 0, 6, mirror=side < 0, share=None if side > 0 else f"wing{k}_l")
        z = ln - 0.5
    tail = node.bone("tail", (0, 0, z))
    for k, (yaw, ln) in enumerate(((-16, 18), (0, 24), (16, 18))):
        tail.bone(f"streamer{k}", (0, 0, 0), (8, yaw, 0)).cube(f"streamer{k}", -2, 0, 0, 4, 0, ln, share=None if k != 2 else "streamer0")
    r.look = [("head", 0.7, 0.7), ("seg0", 0.3, 0.2)]
    r.moves = {"Coil Strike": "coil", "Canopy Drop": "drop", "Venom Spray": "spray", "Venom Rain": "flare", "Plume Storm": "flare",
               "Wingbeat": "flare", "Coiled Fury": "roar"}
    r.pack()
    coil_clips(r)
    return r


def coil_clips(r):
    segs = [f"seg{k}" for k in range(len(COIL_SEGS))]
    ruffs = [f"ruff{k}" for k in range(len(RUFF))]
    idle = r.clip("idle", 3.2, loop=True)
    wave(idle, segs, "y", 9, lag=0.11, n=8)
    cycle(idle, "head", "rot", lambda p: (3 * s(p, 0.3), -6 * s(p, 0.1), 0), 8)
    cycle(idle, "head", "pos", lambda p: (0, 0.8 * s(p * 2), 0), 8)
    for k in range(4):
        cycle(idle, f"plume{k}", "rot", lambda p, k=k: (4 * s(p, k * 0.2), 0, 0), 8)
    for k in range(3):
        cycle(idle, f"streamer{k}", "rot", lambda p, k=k: (6 * s(p, 0.4 + k * 0.1), 0, 0), 8)
    fly = r.clip("fly", 1.4, loop=True)
    wave(fly, segs, "y", 16, lag=0.12, n=8)
    cycle(fly, "head", "rot", lambda p: (0, -10 * s(p, 0.05), 0), 8)
    for k in range(3):
        cycle(fly, f"streamer{k}", "rot", lambda p, k=k: (10 * s(p, 0.5 + k * 0.08), 0, 0), 8)
    for wg in ("wing2_l", "wing2_r", "wing5_l", "wing5_r"):
        sg = 1 if wg.endswith("l") else -1
        cycle(fly, wg, "rot", lambda p, sg=sg: (0, 0, sg * 25 * s(p * 2)), 8)

    def flare(clip, t0, t1, amount=1.0, rattle=True):
        for k, rf in enumerate(ruffs):
            spread = (RUFF[k][0] * 0.45) * amount
            keys = [(0, 0, 0, 0), (t0, -30 * amount, 0, spread)]
            if rattle:
                q = t0
                while q + 0.08 < t1:
                    q += 0.08
                    keys.append((q, -30 * amount + (4 if int(q * 100) % 2 else -4), 0, spread + (5 if len(keys) % 2 else -5)))
            keys.append((t1, -30 * amount, 0, spread))
            clip.rot(rf, keys)
            clip.scale(rf, [(0, 1, 1, 1), (t0, 1.2, 1.35, 1), (t1, 1.2, 1.35, 1)])
    # generic: draw back into an S and strike
    w = r.clip("windup", 0.9)
    w.rot("head", [(0, 0, 0, 0), (0.9, -20, 0, 0)])
    w.pos("head", [(0, 0, 0, 0), (0.9, 0, 3, 4)])
    for k, sg in enumerate(segs[:4]):
        w.rot(sg, [(0, 0, 0, 0), (0.9, 0, (30 if k % 2 == 0 else -36), 0)])
    flare(w, 0.3, 0.9, 0.6, rattle=False)
    st = r.clip("strike", 0.6)
    st.rot("head", [(0, -20, 0, 0), (0.1, 12, 0, 0), (0.6, 0, 0, 0)])
    st.pos("head", [(0, 0, 3, 4), (0.1, 0, -1, -7), (0.6, 0, 0, 0)])
    for k, sg in enumerate(segs[:4]):
        st.rot(sg, [(0, 0, (30 if k % 2 == 0 else -36), 0), (0.1, 0, 0, 0), (0.6, 0, 0, 0)])
    st.rot("jaw", [(0, 0, 0, 0), (0.06, 40, 0, 0), (0.18, 0, 0, 0)])
    for k, rf in enumerate(ruffs):
        st.rot(rf, [(0, -18, 0, RUFF[k][0] * 0.27), (0.2, 0, 0, 0)])
        st.scale(rf, [(0, 1.12, 1.2, 1), (0.2, 1, 1, 1)])
    bite = r.clip("bite", 0.45)
    bite.rot("head", [(0, 0, 0, 0), (0.08, -12, 0, 0), (0.16, 14, 0, 0), (0.45, 0, 0, 0)])
    bite.pos("head", [(0, 0, 0, 0), (0.16, 0, 0, -4), (0.45, 0, 0, 0)])
    bite.rot("jaw", [(0, 0, 0, 0), (0.08, 40, 0, 0), (0.16, 0, 0, 0)])
    # Coil Strike (signature): it rears up into a tall S, the collar flares and rattles... then the lunge
    cw = r.clip("coil_windup", 1.0)
    cw.rot("head", [(0, 0, 0, 0), (0.35, -10, 0, 0), (1.0, 25, 0, 0)])
    cw.pos("head", [(0, 0, 0, 0), (0.35, 0, 6, 3), (1.0, 0, 8, 5)])
    cw.rot("seg0", [(0, 0, 0, 0), (0.35, 50, 0, 0), (1.0, 62, 0, 0)])
    cw.rot("seg1", [(0, 0, 0, 0), (0.35, -20, 0, 0), (1.0, -26, 0, 0)])
    cw.rot("seg2", [(0, 0, 0, 0), (0.35, -30, 18, 0), (1.0, -34, 22, 0)])
    cw.rot("seg3", [(0, 0, 0, 0), (0.35, 0, -26, 0), (1.0, 0, -30, 0)])
    cw.rot("jaw", [(0, 0, 0, 0), (0.7, 0, 0, 0), (1.0, 32, 0, 0)])
    flare(cw, 0.3, 1.0, 1.0)
    for k in range(4):
        cw.rot(f"plume{k}", [(0, 0, 0, 0), (0.3, -30, 0, 0), (1.0, -34, 0, 0)])
    for k in range(3):
        cw.rot(f"streamer{k}", [(0, 0, 0, 0)] + [(0.2 + 0.08 * q, (-14 if q % 2 else 14), 0, 0) for q in range(10)])
    cs = r.clip("coil_strike", 0.7)
    cs.rot("head", [(0, 25, 0, 0), (0.1, 30, 0, 0), (0.3, 6, 0, 0), (0.7, 0, 0, 0)])
    cs.pos("head", [(0, 0, 8, 5), (0.1, 0, -2, -10), (0.35, 0, 0, -3), (0.7, 0, 0, 0)])
    cs.rot("seg0", [(0, 62, 0, 0), (0.1, -10, 0, 0), (0.7, 0, 0, 0)])
    cs.rot("seg1", [(0, -26, 0, 0), (0.1, 6, 0, 0), (0.7, 0, 0, 0)])
    cs.rot("seg2", [(0, -34, 22, 0), (0.12, 0, 0, 0), (0.7, 0, 0, 0)])
    cs.rot("seg3", [(0, 0, -30, 0), (0.14, 0, 0, 0), (0.7, 0, 0, 0)])
    cs.rot("jaw", [(0, 32, 0, 0), (0.08, 44, 0, 0), (0.14, 0, 0, 0), (0.7, 0, 0, 0)])
    for k, rf in enumerate(ruffs):
        cs.rot(rf, [(0, -30, 0, RUFF[k][0] * 0.45), (0.1, 20, 0, RUFF[k][0] * 0.1), (0.7, 0, 0, 0)])
        cs.scale(rf, [(0, 1.2, 1.35, 1), (0.3, 1, 1, 1)])
    for k in range(4):
        cs.rot(f"plume{k}", [(0, -34, 0, 0), (0.15, 10, 0, 0), (0.7, 0, 0, 0)])
    # Canopy Drop: it coils up high and looks down, then drops on you
    dw = r.clip("drop_windup", 0.8)
    dw.rot("head", [(0, 0, 0, 0), (0.8, 50, 0, 0)])
    dw.pos("head", [(0, 0, 0, 0), (0.8, 0, 10, 4)])
    dw.rot("seg0", [(0, 0, 0, 0), (0.8, 70, 0, 0)])
    dw.rot("seg1", [(0, 0, 0, 0), (0.8, -30, 0, 0)])
    flare(dw, 0.2, 0.8, 0.5, rattle=False)
    ds = r.clip("drop_strike", 0.7)
    ds.rot("head", [(0, 50, 0, 0), (0.12, 40, 0, 0), (0.7, 0, 0, 0)])
    ds.pos("head", [(0, 0, 10, 4), (0.12, 0, -4, -2), (0.7, 0, 0, 0)])
    ds.rot("seg0", [(0, 70, 0, 0), (0.15, 0, 0, 0)])
    ds.rot("seg1", [(0, -30, 0, 0), (0.15, 0, 0, 0)])
    ds.rot("jaw", [(0, 0, 0, 0), (0.08, 40, 0, 0), (0.2, 0, 0, 0)])
    # Venom Spray: the jaws gape wide, head thrust forward, collar half open
    pw = r.clip("spray_windup", 0.9)
    pw.rot("head", [(0, 0, 0, 0), (0.9, -16, 0, 0)])
    pw.rot("jaw", [(0, 0, 0, 0), (0.9, 50, 0, 0)])
    flare(pw, 0.3, 0.9, 0.7)
    ps = r.clip("spray_strike", 0.8)
    ps.rot("head", [(0, -16, 0, 0), (0.1, 6, -12, 0), (0.4, 6, 12, 0), (0.8, 0, 0, 0)])
    ps.rot("jaw", [(0, 50, 0, 0), (0.6, 50, 0, 0), (0.8, 0, 0, 0)])
    for k, rf in enumerate(ruffs):
        ps.rot(rf, [(0, -21, 0, RUFF[k][0] * 0.31), (0.6, -21, 0, RUFF[k][0] * 0.31), (0.8, 0, 0, 0)])
    # Venom Rain, Plume Storm, Wingbeat: every feather flares and shivers
    fw = r.clip("flare_windup", 1.1)
    flare(fw, 0.25, 1.1, 1.0)
    fw.rot("head", [(0, 0, 0, 0), (0.25, -30, 0, 0), (1.1, -34, 0, 0)])
    fw.rot("seg0", [(0, 0, 0, 0), (0.25, 30, 0, 0), (1.1, 34, 0, 0)])
    for wg in ("wing2_l", "wing2_r", "wing5_l", "wing5_r"):
        sg = 1 if wg.endswith("l") else -1
        fw.rot(wg, [(0, 0, 0, 0), (0.3, 0, 0, sg * 40), (1.1, 0, 0, sg * 46)])
    for k in range(3):
        fw.rot(f"streamer{k}", [(0, 0, 0, 0), (0.3, -30, (k - 1) * 20, 0), (1.1, -34, (k - 1) * 24, 0)])
    fs = r.clip("flare_strike", 0.6)
    for k, rf in enumerate(ruffs):
        fs.rot(rf, [(0, -30, 0, RUFF[k][0] * 0.45), (0.1, -40, 0, RUFF[k][0] * 0.6), (0.6, 0, 0, 0)])
        fs.scale(rf, [(0, 1.2, 1.35, 1), (0.1, 1.3, 1.5, 1), (0.6, 1, 1, 1)])
    fs.rot("head", [(0, -34, 0, 0), (0.6, 0, 0, 0)])
    fs.rot("seg0", [(0, 34, 0, 0), (0.6, 0, 0, 0)])
    for wg in ("wing2_l", "wing2_r", "wing5_l", "wing5_r"):
        sg = 1 if wg.endswith("l") else -1
        fs.rot(wg, [(0, 0, 0, sg * 46), (0.1, 0, 0, -sg * 30), (0.6, 0, 0, 0)])
    for k in range(3):
        fs.rot(f"streamer{k}", [(0, -34, (k - 1) * 24, 0), (0.6, 0, 0, 0)])
    roar = r.clip("roar", 1.3)
    roar.rot("head", [(0, 0, 0, 0), (0.3, -40, 0, 0), (1.0, -36, 0, 0), (1.3, 0, 0, 0)])
    roar.rot("seg0", [(0, 0, 0, 0), (0.3, 40, 0, 0), (1.0, 36, 0, 0), (1.3, 0, 0, 0)])
    roar.rot("jaw", [(0, 0, 0, 0), (0.3, 48, 0, 0), (1.0, 44, 0, 0), (1.3, 0, 0, 0)])
    flare(roar, 0.3, 1.0, 1.0)
    roar.rot("ruff0", [(1.0, -30, 0, -31.5), (1.3, 0, 0, 0)])
    warn = r.clip("warn", 1.0)
    flare(warn, 0.15, 0.8, 0.7)
    for k in range(3):
        warn.rot(f"streamer{k}", [(0, 0, 0, 0)] + [(0.1 + 0.07 * q, (-12 if q % 2 else 12), 0, 0) for q in range(11)] + [(1.0, 0, 0, 0)])
    for rf in ruffs:
        warn.scale(rf, [(0.8, 1.15, 1.25, 1), (1.0, 1, 1, 1)])
    hurt = r.clip("hurt", 0.45)
    hurt.rot("head", [(0, 0, 0, 0), (0.06, -22, -16, 0), (0.25, 5, 5, 0), (0.45, 0, 0, 0)])
    for k, sg in enumerate(segs[:5]):
        hurt.rot(sg, [(0, 0, 0, 0), (0.07 + 0.03 * k, 0, (18 if k % 2 else -18), 0), (0.45, 0, 0, 0)])
    death = r.clip("death", 1.8)
    death.pos("head", [(0, 0, 0, 0), (0.3, 0, 3, 0), (1.2, 0, -9, 0), (1.8, 0, -9.5, 0)])
    death.rot("head", [(0, 0, 0, 0), (0.3, -36, 0, 0), (1.1, 14, 0, -50), (1.8, 10, 0, -80)])
    death.rot("jaw", [(0, 0, 0, 0), (0.3, 44, 0, 0), (1.8, 30, 0, 0)])
    for k, sg in enumerate(segs):
        death.rot(sg, [(0, 0, 0, 0), (0.4, 6, (12 if k % 2 else -12), 0), (1.2, 2, (34 if k % 2 else -30), 0), (1.8, 0, (30 if k % 2 else -28), 0)])
    for rf in ruffs:
        death.rot(rf, [(0, 0, 0, 0), (1.8, 30, 0, 0)])


def paint_plumed_coil(r, P):
    jade = Ramp("#06221c", "#0b3a2c", "#11523a", "#1a6e48", "#2a8c56", "#46ac68", "#74cc84")
    gold = Ramp("#4a3008", "#7a5410", "#a87c1c", "#d4a636", "#f0cc60", "#fff0b0")
    teal = Ramp("#062a36", "#0b4656", "#126a7a", "#1d92a0", "#3cbcc4", "#7ce8e8")
    red = Ramp("#3a0a0e", "#661418", "#922222", "#c43a2a", "#ec6a3c", "#ffa060")
    mouth = Ramp("#1c060a", "#36101a", "#5a1c28")
    # scales: jade, a gold belly, a dorsal row of gold diamonds
    def scales(f, i, j):
        base = p_scales(3, 2, 0.8)(f, i, j)
        if f.name == "top" and (j % 6 in (1, 2, 3, 4)) and abs(i - (f.w - 1) / 2) <= (2 - abs(j % 6 - 2.5) + 0.2):
            return gold.at(4.0 - (0.8 if j % 6 == 4 else 0))
        return base
    for k in range(len(COIL_SEGS)):
        P.paint(f"seg{k}", jade, 3.3 - k * 0.08, scales)
        f = P.face(f"seg{k}", "bottom")
        for i, j in f.each():
            f.set(i, j, gold.at(3.2 - (0.9 if j % 2 == 1 else 0)))
        for fname in ("west", "east"):
            f = P.face(f"seg{k}", fname)
            for i in range(f.w):
                f.set(i, f.h - 1, gold.at(2.6))
            # glowing turquoise spots along the flanks
            g = P.face(f"seg{k}", fname, glow=True)
            for i in range(1, f.w - 1, 3):
                jj = max(0, f.h // 2 - 1)
                f.set(i, jj, teal.at(5))
                g.set(i, jj, hexc("#7cf0f0", 220))
    P.paint("skull", jade, 3.5, p_scales(3, 2, 0.7))
    P.paint("snout", jade, 3.8, p_scales(2, 2, 0.6))
    P.paint("brows", gold, 3.4, p_bands(2, 0.6, vertical=True))
    P.paint("jaw", gold, 2.8)
    f = P.face("jaw", "top")
    for i, j in f.each():
        f.set(i, j, mouth.at(1))
    P.paint("fang_l", Ramp("#c8c0a8", "#e8e2d0", "#fffaf0"), 1.5)
    for fname in ("west", "east"):
        f = P.face("skull", fname)
        g = P.face("skull", fname, glow=True)
        ex = f.w - 4 if fname == "west" else 1
        for dx in range(3):
            f.set(ex + dx, 1, hexc("#0a1410"))
            f.set(ex + dx, 2, hexc("#5cf0ff") if dx == 1 else hexc("#0a1410"))
        g.set(ex + 1, 2, hexc("#c0ffff", 255))
        g.set(ex, 2, hexc("#5cf0ff", 160))
        g.set(ex + 2, 2, hexc("#5cf0ff", 160))

    def feather(face, colours, eye=None, glow_eye=None, vane_edge=True):
        """A long feather on a plane: a pale shaft down the middle, vanes in bands, an eye near the tip."""
        clear(face)
        w, h = face.w, face.h
        horizontal = w >= h
        L, Wd = (w, h) if horizontal else (h, w)
        for a in range(L):
            for b in range(Wd):
                # taper the vane to the tip
                t = a / max(1, L - 1)
                half = (Wd / 2) * (0.55 + 0.45 * math.sin(math.pi * min(1.0, t * 1.15)))
                db = abs(b - (Wd - 1) / 2)
                if db > half:
                    continue
                band = colours[int((t * len(colours) * 0.999))]
                col = band.at(3.6 - db * 0.6 + (0.6 if db < 0.6 else 0) - (0.7 if vane_edge and db > half - 1 else 0))
                x, y = (a, b) if horizontal else (b, a)
                face.set(x, y, col)
        if eye:
            a0 = int(L * 0.8)
            for da in (-1, 0, 1):
                for db in (-1, 0, 1):
                    x, y = (a0 + da, int((Wd - 1) / 2) + db) if horizontal else (int((Wd - 1) / 2) + db, a0 + da)
                    if 0 <= x < w and 0 <= y < h and face.get(x, y)[3]:
                        face.set(x, y, eye[0] if (da, db) != (0, 0) else eye[1])
            if glow_eye:
                x, y = (a0, int((Wd - 1) / 2)) if horizontal else (int((Wd - 1) / 2), a0)
                glow_eye.set(x, y, hexc("#7cf0f0", 255))
    for c in r.cubes:
        if not P.own(c):
            continue
        n = c.name
        if n.startswith("plume"):
            for fname in ("west", "east"):
                feather(P.face(c, fname), [jade, teal, gold, red], eye=(teal.at(5), gold.at(5)), glow_eye=P.face(c, fname, glow=True))
        elif n.startswith("ruff"):
            for fname in ("north", "south"):
                feather(P.face(c, fname), [teal, gold, red, red], eye=(gold.at(4), teal.at(5)), glow_eye=P.face(c, fname, glow=True))
        elif n.startswith("wing"):
            for fname in ("top", "bottom"):
                feather(P.face(c, fname), [jade, teal])
        elif n.startswith("streamer"):
            for fname in ("top", "bottom"):
                feather(P.face(c, fname), [jade, teal, gold, red], eye=(gold.at(4), teal.at(5)), glow_eye=P.face(c, fname, glow=True))
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# The Old Antler: an ancient elk, high-shouldered, bearded with frost-pale shag, bark grown over its shoulders, and a
# crown of antlers so wide it carries the forest's spirit-lights and hanging moss

ANT_LEGS = [("leg_fl", "shin_fl", 0.0), ("leg_hr", "shin_hr", 0.0), ("leg_fr", "shin_fr", 0.5), ("leg_hl", "shin_hl", 0.5)]


def old_antler():
    r = Rig("old_antler", (128, 128))
    r.walk_stride, r.run_stride, r.run_speed, r.shadow = 2.4, 5.0, (0.16, 0.3), 1.0
    body = r.bone("body", (0, 3, 0))
    body.cube("barrel", -5.5, -6, -10, 11, 11, 22)
    body.cube("chest", -6, -8, -14, 12, 13, 8)
    body.cube("withers", -4.5, -10, -13, 9, 3, 10)
    for side, nm in ((1, "l"), (-1, "r")):
        mir = side < 0

        def sh(n, side=side):
            return None if side > 0 else n
        body.bone("bark_" + nm, (side * 5, -8, -9), (0, 0, side * -18)).cube("bark_" + nm, -2.5, -2, -4, 5, 4, 9, mirror=mir, share=sh("bark_l"))
        body.bone("shag_" + nm, (side * 6.1, 2, -12)).cube("shag_" + nm, 0, 0, 0, 0, 7, 20, mirror=mir, share=sh("shag_l"))
        for front, z, y in ((True, -9, 3), (False, 9, 2)):
            ln = "leg_" + ("f" if front else "h") + nm
            leg = body.bone(ln, (side * 3.8, y, z))
            leg.cube(ln, -2, -1, -2, 4, 11, 4, mirror=mir, share=sh("leg_" + ("f" if front else "h") + "l"))
            sn = "shin_" + ("f" if front else "h") + nm
            shin = leg.bone(sn, (0, 9, 0 if front else 1))
            shin.cube(sn, -1.5, 0, -1.5, 3, 9 if front else 10, 3, mirror=mir, share=sh("shin_" + ("f" if front else "h") + "l"))
    ruff = body.bone("ruff", (0, 5, -13.5))
    ruff.cube("ruff", -5, 0, 0, 10, 9, 0)
    tail = body.bone("tail", (0, -4, 12), (35, 0, 0))
    tail.cube("tail", -1.5, 0, -1, 3, 5, 2)
    neck = body.bone("neck", (0, -5, -12), (32, 0, 0))
    neck.cube("neck", -3.5, -13, -3.5, 7, 14, 7)
    neck.bone("mane", (0, -12, 3.6)).cube("mane", 0, 0, 0, 0, 13, 4)
    neck.bone("beard", (0, 0, -3.6)).cube("beard", -3, 0, 0, 6, 8, 0)
    head = neck.bone("head", (0, -12.5, 0), (-32, 0, 0))
    head.cube("skull", -3.5, -4, -8, 7, 7, 8)
    head.cube("muzzle", -2, -2, -13, 4, 5, 5)
    head.cube("brow", -4, -5, -6, 8, 2, 4)
    jaw = head.bone("jaw", (0, 3, -6))
    jaw.cube("jaw", -1.5, 0, -7, 3, 2, 6)
    for side, nm in ((1, "l"), (-1, "r")):
        mir = side < 0

        def sh(n, side=side):
            return None if side > 0 else n
        head.bone("ear_" + nm, (side * 3.5, -3, -1), (0, side * -20, side * -40)).cube("ear_" + nm, 0 if side > 0 else -4, -1, -1, 4, 2, 1, mirror=mir, share=sh("ear_l"))
        # the crown: a wide royal rack, out and back, then up into a cup of points
        b1 = head.bone("antler_" + nm, (side * 2.5, -4.5, -3), (-30, side * 14, side * 58))
        b1.cube("beam1_" + nm, -1, -10, -1, 2, 10, 2, mirror=mir, share=sh("beam1_l"))
        b1.bone("brow_tine_" + nm, (0, -2, 0), (-80, 0, side * -30)).cube("tine1_" + nm, -0.5, -7, -0.5, 1, 7, 1, mirror=mir, share=sh("tine1_l"))
        b1.bone("bez_tine_" + nm, (0, -6, 0), (-60, 0, side * -40)).cube("tine2_" + nm, -0.5, -6, -0.5, 1, 6, 1, mirror=mir, share=sh("tine2_l"))
        b1.bone("moss_" + nm, (0, -7, 1), (20, 0, side * -58)).cube("moss_" + nm, 0, 0, 0, 0, 9, 4, mirror=mir, share=sh("moss_l"))
        b2 = b1.bone("antler2_" + nm, (0, -9.5, 0), (-10, 0, side * -30))
        b2.cube("beam2_" + nm, -1, -9, -1, 2, 9, 2, mirror=mir, share=sh("beam2_l"))
        tc = b2.bone("tine_c_" + nm, (0, -4, 0), (-20, 0, side * 50))
        tc.cube("tine3_" + nm, -0.5, -7, -0.5, 1, 7, 1, mirror=mir, share=sh("tine3_l"))
        b3 = b2.bone("antler3_" + nm, (0, -8.5, 0), (15, 0, side * -12))
        b3.cube("beam3_" + nm, -0.5, -7, -0.5, 1, 7, 1, mirror=mir, share=sh("beam3_l"))
        for k, (rx, rz) in enumerate(((-45, 30), (-10, -35), (40, 15))):
            b3.bone(f"cup{k}_" + nm, (0, -6.5, 0), (rx, 0, side * rz)).cube(f"cup{k}_" + nm, -0.5, -5, -0.5, 1, 5, 1, mirror=mir,
                                                                          share=sh(f"cup{k}_l"))
        # spirit-lights hang from the crown on threads of moss
        lamp = tc.bone("lamp_" + nm, (0, -6.5, 0), (-10, 0, side * -82))
        lamp.cube("lamp_" + nm, -1, 0, -1, 2, 3, 2, mirror=mir, share=sh("lamp_l"))


    r.look = [("neck", 0.3, 0.15), ("head", 0.6, 0.6)]
    r.moves = {"Antler Rush": "rush", "Hoof Quake": "rear", "Root Snare": "rear", "Rime Ring": "rear", "Bellow": "bellow",
               "Crown Light": "crown", "The Last Rut": "roar"}
    r.pack()
    antler_clips(r)
    return r


def antler_clips(r):
    idle = r.clip("idle", 4.0, loop=True)
    cycle(idle, "body", "scale", lambda p: (1 + 0.01 * s(p * 2), 1 + 0.02 * s(p * 2), 1), 8)
    cycle(idle, "neck", "rot", lambda p: (2 * s(p, 0.1), 0, 0), 8)
    cycle(idle, "head", "rot", lambda p: (-2 * s(p, 0.3), 5 * s(p, 0.6), 0), 8)
    cycle(idle, "ear_l", "rot", lambda p: (0, 0, 12 * max(0, s(p * 2, 0.2)) ** 6), 16)
    cycle(idle, "ear_r", "rot", lambda p: (0, 0, -12 * max(0, s(p * 2, 0.7)) ** 6), 16)
    cycle(idle, "tail", "rot", lambda p: (0, 0, 10 * s(p * 4)), 8)
    for nm, sg in (("lamp_l", 1), ("lamp_r", -1)):
        cycle(idle, nm, "rot", lambda p, sg=sg: (6 * s(p * 2, 0.3), 0, sg * 5 * s(p * 2)), 8)
    for nm in ("moss_l", "moss_r", "beard", "ruff", "mane"):
        cycle(idle, nm, "rot", lambda p: (4 * s(p * 2, 0.4), 0, 0), 8)
    walk = r.clip("walk", 1.4, loop=True)
    quad_gait(walk, ANT_LEGS, swing=26, knee=30)
    body_bob(walk, "body", amp=0.5, roll=1.5)
    cycle(walk, "neck", "rot", lambda p: (3 * c(p * 2, 0.15), 0, 0), 8)
    cycle(walk, "head", "rot", lambda p: (-3 * c(p * 2, 0.15), 0, 0), 8)
    for nm, sg in (("lamp_l", 1), ("lamp_r", -1)):
        cycle(walk, nm, "rot", lambda p, sg=sg: (10 * s(p * 2, 0.4), 0, 0), 8)
    run = r.clip("run", 0.75, loop=True)
    quad_gait(run, [("leg_fl", "shin_fl", 0.0), ("leg_fr", "shin_fr", 0.1), ("leg_hl", "shin_hl", 0.5), ("leg_hr", "shin_hr", 0.6)], swing=46, knee=50)
    cycle(run, "body", "rot", lambda p: (8 * s(p, 0.25), 0, 0), 8)
    cycle(run, "body", "pos", lambda p: (0, 1.8 * c(p * 2), 0), 8)
    cycle(run, "neck", "rot", lambda p: (14 + 6 * s(p, 0.5), 0, 0), 8)
    cycle(run, "head", "rot", lambda p: (-10, 0, 0), 4)
    for nm, sg in (("lamp_l", 1), ("lamp_r", -1)):
        cycle(run, nm, "rot", lambda p, sg=sg: (30 + 14 * s(p * 2), 0, 0), 8)
    # generic: head lowered, a toss of the crown
    w = r.clip("windup", 0.9)
    w.rot("neck", [(0, 0, 0, 0), (0.9, 30, 0, 0)])
    w.rot("head", [(0, 0, 0, 0), (0.9, 26, 0, 0)])
    w.rot("body", [(0, 0, 0, 0), (0.9, -4, 0, 0)])
    st = r.clip("strike", 0.6)
    st.rot("neck", [(0, 30, 0, 0), (0.12, -10, 0, 0), (0.6, 0, 0, 0)])
    st.rot("head", [(0, 26, 0, 0), (0.12, -30, 0, 0), (0.6, 0, 0, 0)])
    st.rot("body", [(0, -4, 0, 0), (0.12, 3, 0, 0), (0.6, 0, 0, 0)])
    bite = r.clip("bite", 0.5)
    bite.rot("neck", [(0, 0, 0, 0), (0.1, 26, 0, 0), (0.22, -8, 0, 0), (0.5, 0, 0, 0)])
    bite.rot("head", [(0, 0, 0, 0), (0.1, 30, -10, 0), (0.22, -24, 12, 0), (0.5, 0, 0, 0)])
    # Antler Rush (signature): the crown drops level with your chest, a hoof scrapes twice, the haunches gather
    cw = r.clip("rush_windup", 1.3)
    cw.rot("neck", [(0, 0, 0, 0), (0.35, 48, 0, 0), (1.3, 52, 0, 0)])
    cw.rot("head", [(0, 0, 0, 0), (0.35, 34, 0, 0), (1.3, 38, 0, 0)])
    cw.rot("body", [(0, 0, 0, 0), (0.35, 5, 0, 0), (1.3, 7, 0, 0)])
    cw.pos("body", [(0, 0, 0, 0), (0.35, 0, -1.5, 2), (1.3, 0, -2, 3)])
    cw.rot("leg_fr", [(0, 0, 0, 0), (0.4, -45, 0, 0), (0.6, 20, 0, 0), (0.8, -45, 0, 0), (1.0, 20, 0, 0), (1.3, 0, 0, 0)])
    cw.rot("shin_fr", [(0, 0, 0, 0), (0.4, 50, 0, 0), (0.6, 0, 0, 0), (0.8, 50, 0, 0), (1.0, 0, 0, 0)])
    cw.rot("leg_hl", [(0, 0, 0, 0), (0.4, -18, 0, 0), (1.3, -20, 0, 0)])
    cw.rot("leg_hr", [(0, 0, 0, 0), (0.4, -18, 0, 0), (1.3, -20, 0, 0)])
    cw.rot("tail", [(0, 0, 0, 0), (0.3, -40, 0, 0), (1.3, -45, 0, 0)])
    for nm, sg in (("lamp_l", 1), ("lamp_r", -1)):
        cw.rot(nm, [(0, 0, 0, 0), (0.4, -40, 0, sg * 10), (1.3, -44, 0, sg * 10)])
    cs = r.clip("rush_strike", 0.8)
    cs.rot("neck", [(0, 52, 0, 0), (0.1, 44, 0, 0), (0.55, 44, 0, 0), (0.68, -6, 0, 0), (0.8, 0, 0, 0)])
    cs.rot("head", [(0, 38, 0, 0), (0.55, 34, 0, 0), (0.68, -34, 0, 0), (0.8, 0, 0, 0)])
    cs.rot("body", [(0, 7, 0, 0), (0.1, -5, 0, 0), (0.6, -5, 0, 0), (0.8, 0, 0, 0)])
    cs.pos("body", [(0, 0, -2, 3), (0.1, 0, 0, -3), (0.6, 0, 0, -3), (0.8, 0, 0, 0)])
    cs.rot("tail", [(0, -45, 0, 0), (0.8, 0, 0, 0)])
    # Hoof Quake (and the snare, the rime ring): it rears high, forelegs churning, and comes down on the ground
    rw = r.clip("rear_windup", 1.1)
    rw.rot("body", [(0, 0, 0, 0), (0.2, 5, 0, 0), (0.8, -38, 0, 0), (1.1, -42, 0, 0)])
    rw.pos("body", [(0, 0, 0, 0), (0.8, 0, 5, 3), (1.1, 0, 6, 3)])
    for nm, ph in (("fl", 0), ("fr", 0.15)):
        rw.rot("leg_" + nm, [(0, 0, 0, 0), (0.6 + ph, -60, 0, 0), (0.8 + ph, -30, 0, 0), (1.1, -62, 0, 0)])
        rw.rot("shin_" + nm, [(0, 0, 0, 0), (0.6 + ph, 70, 0, 0), (0.8 + ph, 30, 0, 0), (1.1, 74, 0, 0)])
    for nm in ("hl", "hr"):
        rw.rot("leg_" + nm, [(0, 0, 0, 0), (0.8, 38, 0, 0), (1.1, 42, 0, 0)])
    rw.rot("neck", [(0, 0, 0, 0), (0.8, -18, 0, 0), (1.1, -20, 0, 0)])
    rw.rot("head", [(0, 0, 0, 0), (0.8, 30, 0, 0), (1.1, 34, 0, 0)])
    rw.rot("jaw", [(0, 0, 0, 0), (0.8, 24, 0, 0), (1.1, 26, 0, 0)])
    rs = r.clip("rear_strike", 0.7)
    rs.rot("body", [(0, -42, 0, 0), (0.12, 6, 0, 0), (0.3, 2, 0, 0), (0.7, 0, 0, 0)])
    rs.pos("body", [(0, 0, 6, 3), (0.12, 0, -1.5, 0), (0.3, 0, 0, 0)])
    for nm in ("fl", "fr"):
        rs.rot("leg_" + nm, [(0, -62, 0, 0), (0.12, 8, 0, 0), (0.7, 0, 0, 0)])
        rs.rot("shin_" + nm, [(0, 74, 0, 0), (0.12, 0, 0, 0)])
    for nm in ("hl", "hr"):
        rs.rot("leg_" + nm, [(0, 42, 0, 0), (0.15, 0, 0, 0)])
    rs.rot("neck", [(0, -20, 0, 0), (0.12, 20, 0, 0), (0.4, 4, 0, 0), (0.7, 0, 0, 0)])
    rs.rot("head", [(0, 34, 0, 0), (0.12, 14, 0, 0), (0.7, 0, 0, 0)])
    rs.rot("jaw", [(0, 26, 0, 0), (0.2, 0, 0, 0)])
    for nm in ("lamp_l", "lamp_r"):
        rs.rot(nm, [(0, 0, 0, 0), (0.12, 50, 0, 0), (0.35, -20, 0, 0), (0.7, 0, 0, 0)])
    # Bellow: the neck stretches out and up, the chest swells, the jaw drops
    bw = r.clip("bellow_windup", 1.2)
    bw.rot("neck", [(0, 0, 0, 0), (0.3, 20, 0, 0), (1.2, -12, 0, 0)])
    bw.rot("head", [(0, 0, 0, 0), (0.3, 10, 0, 0), (1.2, -38, 0, 0)])
    bw.scale("body", [(0, 1, 1, 1), (1.2, 1.06, 1.08, 1)])
    bw.rot("jaw", [(0, 0, 0, 0), (0.8, 10, 0, 0), (1.2, 30, 0, 0)])
    bs = r.clip("bellow_strike", 0.9)
    bs.rot("neck", [(0, -12, 0, 0), (0.15, 10, 0, 0), (0.7, 8, 0, 0), (0.9, 0, 0, 0)])
    bs.rot("head", [(0, -38, 0, 0), (0.15, -20, 0, 0), (0.7, -16, 0, 0), (0.9, 0, 0, 0)])
    bs.rot("jaw", [(0, 30, 0, 0), (0.7, 34, 0, 0), (0.9, 0, 0, 0)])
    bs.scale("body", [(0, 1.06, 1.08, 1), (0.2, 0.98, 0.97, 1), (0.9, 1, 1, 1)])
    # Crown Light: it lowers the crown slowly toward you as the lights brighten, holding still
    lw = r.clip("crown_windup", 1.9)
    lw.rot("neck", [(0, 0, 0, 0), (0.8, 30, 0, 0), (1.9, 34, 0, 0)])
    lw.rot("head", [(0, 0, 0, 0), (0.8, -6, 0, 0), (1.9, -10, 0, 0)])
    lw.rot("body", [(0, 0, 0, 0), (0.8, 4, 0, 0), (1.9, 5, 0, 0)])
    for nm, sg in (("lamp_l", 1), ("lamp_r", -1)):
        lw.scale(nm, [(0, 1, 1, 1), (1.9, 1.5, 1.5, 1.5)])
    ls = r.clip("crown_strike", 0.6)
    ls.rot("neck", [(0, 34, 0, 0), (0.08, 38, 0, 0), (0.6, 0, 0, 0)])
    ls.rot("head", [(0, -10, 0, 0), (0.08, -14, 0, 0), (0.6, 0, 0, 0)])
    ls.rot("body", [(0, 5, 0, 0), (0.08, -3, 0, 0), (0.6, 0, 0, 0)])
    for nm in ("lamp_l", "lamp_r"):
        ls.scale(nm, [(0, 1.5, 1.5, 1.5), (0.1, 1.8, 1.8, 1.8), (0.6, 1, 1, 1)])
    roar = r.clip("roar", 1.6)
    roar.rot("neck", [(0, 0, 0, 0), (0.35, -16, 0, 0), (1.2, -14, 0, 0), (1.6, 0, 0, 0)])
    roar.rot("head", [(0, 0, 0, 0), (0.35, -40, 0, 0), (1.2, -38, 0, 0), (1.6, 0, 0, 0)])
    roar.rot("jaw", [(0, 0, 0, 0), (0.35, 34, 0, 0), (1.2, 32, 0, 0), (1.6, 0, 0, 0)])
    roar.rot("body", [(0, 0, 0, 0), (0.35, -6, 0, 0), (1.2, -5, 0, 0), (1.6, 0, 0, 0)])
    roar.scale("body", [(0, 1, 1, 1), (0.4, 1.05, 1.07, 1), (1.2, 1.04, 1.06, 1), (1.6, 1, 1, 1)])
    warn = r.clip("warn", 1.0)
    warn.rot("neck", [(0, 0, 0, 0), (0.2, 22, 0, 0), (0.4, 18, 0, 0), (1.0, 0, 0, 0)])
    warn.rot("head", [(0, 0, 0, 0), (0.2, 16, 14, 0), (0.4, 16, -14, 0), (0.6, 12, 8, 0), (1.0, 0, 0, 0)])
    warn.rot("leg_fl", [(0, 0, 0, 0), (0.25, -34, 0, 0), (0.45, 10, 0, 0), (0.7, 0, 0, 0)])
    hurt = r.clip("hurt", 0.45)
    hurt.rot("body", [(0, 0, 0, 0), (0.06, -4, 0, 5), (0.22, 2, 0, -3), (0.45, 0, 0, 0)])
    hurt.rot("neck", [(0, 0, 0, 0), (0.06, -14, 12, 0), (0.25, 4, -4, 0), (0.45, 0, 0, 0)])
    hurt.rot("head", [(0, 0, 0, 0), (0.06, -12, 10, 0), (0.45, 0, 0, 0)])
    death = r.clip("death", 1.8)
    death.rot("body", [(0, 0, 0, 0), (0.3, -10, 0, 0), (0.75, 14, 0, 6), (1.25, 6, 0, 80), (1.8, 4, 0, 86)])
    death.pos("body", [(0, 0, 0, 0), (0.3, 0, 2, 0), (0.75, 0, -6, 0), (1.25, 0, -10, 0), (1.8, 0, -11, 0)])
    death.rot("neck", [(0, 0, 0, 0), (0.3, -24, 0, 0), (0.9, 40, 0, 0), (1.8, 50, 0, 10)])
    death.rot("head", [(0, 0, 0, 0), (0.3, -30, 0, 0), (1.2, 20, 0, 0), (1.8, 24, 0, 0)])
    for nm in ("fl", "fr"):
        death.rot("leg_" + nm, [(0, 0, 0, 0), (0.75, -70, 0, 0), (1.8, -40, 0, 0)])
        death.rot("shin_" + nm, [(0, 0, 0, 0), (0.75, 90, 0, 0), (1.8, 60, 0, 0)])
    for nm in ("hl", "hr"):
        death.rot("leg_" + nm, [(0, 0, 0, 0), (1.0, 30, 0, 0), (1.8, 46, 0, 0)])
    for nm in ("lamp_l", "lamp_r"):
        death.scale(nm, [(0, 1, 1, 1), (1.8, 0.4, 0.4, 0.4)])


def paint_old_antler(r, P):
    coat = Ramp("#1e1915", "#2c241e", "#3c3128", "#4e4034", "#625142", "#7a6652", "#947e66")
    shag = Ramp("#3a3e48", "#555b68", "#737a88", "#959caa", "#b6bcc8", "#d6dae2", "#eef1f5")
    antler = Ramp("#4a4436", "#6e6650", "#948a6e", "#b8ae90", "#d6cdb0", "#ece6d2")
    bark = Ramp("#1c140c", "#2c2014", "#3e2e1c", "#523d26", "#684e32")
    moss = Ramp("#1a2a1c", "#25402a", "#335a36", "#467a46", "#5f9a58", "#84bc74")
    hoof = Ramp("#0e0d0c", "#1c1a18", "#2c2926", "#3e3a35")
    muzzle = Ramp("#1a1614", "#2a2420", "#3e3630", "#544a42", "#6e625a")
    ice = hexc("#bfe6ff")
    rng = P.rng
    hide = combine(p_strokes(3, 3, 0.6, 0.4, 0.6), p_blotch("antler", 8, 0.3))
    for nm in ("barrel", "chest", "withers"):
        P.paint(nm, coat, 3.0 if nm != "withers" else 3.4, hide)
    # a pale saddle of frost-grey hair over the withers and down the neck: the old one's colouring
    P.paint("neck", coat, 3.3, p_strokes(2, 3, 0.6, 0.5, 0.7, seed=3))
    P.paint("withers", coat, 3.8, p_strokes(2, 2, 0.6, 0.5, 0.7, seed=8))
    for nm in ("leg_fl", "leg_hl"):
        P.paint(nm, coat, 2.6, p_strokes(3, 3, 0.6, 0.4, 0.6, seed=4))
    for nm in ("shin_fl", "shin_hl"):
        P.paint(nm, coat, 2.2, p_strokes(2, 2, 0.5, 0.4, 0.5, seed=5))
        for fname in SIDES + ("bottom",):
            f = P.face(nm, fname)
            for i, j in f.each():
                if fname == "bottom" or j >= f.h - 2:
                    f.set(i, j, hoof.at(2.2 - (j - (f.h - 2)) * 0.9))
                elif j < 3:  # pale "socks" of shag above the hooves... no: dark lower legs, pale knees
                    pass
    P.paint("skull", coat, 3.2, p_strokes(2, 2, 0.5, 0.4, 0.6, seed=6))
    P.paint("brow", coat, 2.6, p_bands(2, 0.5))
    P.paint("muzzle", muzzle, 2.8, lambda f, i, j: 0.6 if f.name == "top" else 0)
    P.paint("jaw", muzzle, 2.2)
    f = P.face("muzzle", "north")
    for i, j in f.each():
        f.set(i, j, muzzle.at(1.5 + (1 if j == 0 else 0)))
    f.set(1, 1, muzzle.at(0))
    f.set(f.w - 2, 1, muzzle.at(0))
    P.paint("ear_l", coat, 3.0)
    P.paint("tail", shag, 3.5)
    for fname in ("west", "east"):
        f = P.face("skull", fname)
        g = P.face("skull", fname, glow=True)
        ex = f.w - 3 if fname == "west" else 1
        f.set(ex, 2, hexc("#08090c"))
        f.set(ex + 1, 2, hexc("#08090c"))
        f.set(ex + (1 if fname == "west" else 0), 3, hexc("#7fc3ff"))
        f.set(ex + (0 if fname == "west" else 1), 3, hexc("#08090c"))
        g.set(ex + (1 if fname == "west" else 0), 3, hexc("#d6f0ff", 255))
        g.set(ex + (1 if fname == "west" else 0), 2, hexc("#7fc3ff", 120))
    # bark grown over the shoulders, old runes in it glowing faintly
    P.paint("bark_l", bark, 2.6, combine(p_bands(2, 0.8, vertical=True), lambda f, i, j: 0.6 if j == 0 else 0))
    for fname in ("top", "west", "east"):
        g = P.face("bark_l", fname, glow=True)
        f = P.face("bark_l", fname)
        for i, j in ((1, 1), (2, 2), (3, 1), (2, 3)):
            if i < f.w and j < f.h:
                f.set(i, j, hexc("#7fc3ff"))
                g.set(i, j, hexc("#9fd8ff", 200))
    # antlers: bone, darker at the base, mossed along the lower beams, tips glowing ice-blue
    for c in r.cubes:
        n = c.name
        if not P.own(c):
            continue
        if n.startswith(("beam", "tine", "cup")):
            base = 2.0 if n.startswith("beam1") else 2.8 if n.startswith("beam2") else 3.6 if n.startswith("beam") else 4.0
            P.paint(c, antler, base, lambda f, i, j: (0.8 * (1 - j / max(1, f.h)) if f.name in SIDES else 0.4) - (0.6 if f.name in SIDES and j % 4 == 3 and i == 0 else 0))
            if n.startswith(("beam1", "beam2")):
                for fname in ("west", "east", "south", "north"):
                    f = P.face(c, fname)
                    for i, j in f.each():
                        if j >= f.h - 3 and (i + j) % 3 != 0:
                            f.set(i, j, moss.at(3.5 - (j - f.h + 3) * 0.5))
            if n.startswith("tine") or n.startswith("cup"):
                for fname in ("top", "north", "south", "west", "east"):
                    f = P.face(c, fname)
                    g = P.face(c, fname, glow=True)
                    for i, j in f.each():
                        if fname == "top" or j <= (1 if n.startswith("tine") else 0):
                            f.set(i, j, ice)
                            g.set(i, j, hexc("#cfeeff", 230))
    P.paint("lamp_l", Ramp("#2e5878", "#4c88b4", "#7fc3ff", "#bfe6ff", "#ffffff"), 2.5)
    for fname in ("top", "bottom", "north", "south", "west", "east"):
        g = P.face("lamp_l", fname, glow=True)
        for i, j in g.each():
            g.set(i, j, hexc("#d6f0ff", 255 if (i + j) % 2 == 0 else 200))
    for nm, fnames in (("shag_l", ("west", "east")), ("ruff", ("north", "south")), ("beard", ("north", "south")), ("mane", ("west", "east")),
                       ("moss_l", ("west", "east"))):
        for fname in fnames:
            f = P.face(nm, fname)
            ragged(f, rng, moss if nm == "moss_l" else shag, 3.4 if nm != "moss_l" else 3.0, minlen=0.45)
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# The Mire Mother: a toad as broad as a hut and nearly all head, heavy-browed, a glowing toadstone set in a crown of
# bony knobs, a throat sac that swells before she speaks or strikes, and the bog itself growing on her back

def mire_mother():
    r = Rig("mire_mother", (128, 128))
    r.walk_stride, r.run_stride, r.run_speed, r.shadow = 2.4, 4.0, (0.2, 0.35), 1.4
    body = r.bone("body", (0, 14, 2))
    body.cube("back", -12, -9, -8, 24, 13, 20)
    body.cube("haunch", -11, -6, 10, 22, 10, 6)
    head = body.bone("head", (0, -3, -8))
    head.cube("skull", -13, -6, -12, 26, 8, 12)
    head.cube("brow_l", 4, -9, -10, 8, 3, 7)
    head.cube("brow_r", -12, -9, -10, 8, 3, 7, mirror=True)
    for side, nm in ((1, "l"), (-1, "r")):
        e = head.bone("eye_" + nm, (side * 8, -8, -8))
        e.cube("eye_" + nm, -2.5, -3, -2.5, 5, 4, 5, mirror=side < 0, share=None if side > 0 else "eye_l")
        e.bone("lid_" + nm, (0, -3, 0)).cube("lid_" + nm, -3, -0.5, -3, 6, 1, 6, mirror=side < 0, share=None if side > 0 else "lid_l")
    crown = head.bone("crown", (0, -6, -6))
    crown.cube("toadstone", -2, -4, -2, 4, 4, 4)
    for k, (x, z, rz) in enumerate(((-3.5, -1, -20), (3.5, -1, 20), (0, -4, 0), (0, 3, 0))):
        crown.bone(f"knob{k}", (x, 0, z), (0, 0, rz)).cube(f"knob{k}", -1, -4, -1, 2, 4, 2, share=None if k == 0 else "knob0")
    jaw = head.bone("jaw", (0, 2, 0))
    jaw.cube("jaw", -12.5, 0, -12.5, 25, 3, 12)
    sac = jaw.bone("sac", (0, 3, -6))
    sac.cube("sac", -7, 0, -5, 14, 4, 10)
    tongue = jaw.bone("tongue", (0, -0.5, 16))  # rests coiled back inside her
    tongue.cube("tongue", -2, -1, -24, 4, 2, 24)
    tongue.bone("tongue_tip", (0, 0, -24)).cube("tongue_tip", -3, -1.5, -3, 6, 3, 3)
    for side, nm in ((1, "l"), (-1, "r")):
        mir = side < 0

        def sh(n, side=side):
            return None if side > 0 else n
        arm = body.bone("arm_" + nm, (side * 10, 0, -6), (0, 0, side * -15))
        arm.cube("arm_" + nm, -2.5, 0, -2.5, 5, 9, 5, mirror=mir, share=sh("arm_l"))
        hand = arm.bone("hand_" + nm, (0, 8.5, 0), (0, 0, side * 15))
        hand.cube("hand_" + nm, -4, 0, -6, 8, 1, 7, mirror=mir, share=sh("hand_l"))
        thigh = body.bone("thigh_" + nm, (side * 11, -1, 10), (0, side * 20, 0))
        thigh.cube("thigh_" + nm, -3.5, -3, -9, 7, 9, 13, mirror=mir, share=sh("thigh_l"))
        foot = thigh.bone("foot_" + nm, (0, 5.5, -7), (0, side * -30, 0))
        foot.cube("foot_" + nm, -4, 0, -9, 8, 3, 10, mirror=mir, share=sh("foot_l"))
    # the bog on her back: mud mounds, lily pads, reeds with brown heads, small mushrooms, glowing pustules
    bog = body.bone("bog", (0, -9, 2))
    bog.cube("mound_a", -7, -3, -6, 9, 3, 9)
    bog.cube("mound_b", 1, -2, 0, 8, 2, 8)
    for k, (x, z, ry) in enumerate(((-9, 5, 20), (7, -5, -30), (-3, 9, 60))):
        bog.bone(f"pad{k}", (x, -0.2, z), (0, ry, 0)).cube(f"pad{k}", -3.5, 0, -3.5, 7, 0, 7, share=None if k == 0 else "pad0")
    for k, (x, z, h, rx, rz) in enumerate(((-6, 6, 13, 8, -10), (-4, 7, 11, -6, 12), (6, 3, 12, 10, 8), (8, 5, 9, -8, 18), (-8, -1, 10, -12, -16))):
        rd = bog.bone(f"reed{k}", (x, -1, z), (rx, 0, rz))
        rd.cube(f"reed{k}", -0.5, -h, -0.5, 1, h, 1, share=None if k == 0 else ("reed0" if h == 13 else None))
        if k < 3:
            rd.bone(f"cattail{k}", (0, -h + 1, 0)).cube(f"cattail{k}", -1, -3, -1, 2, 3, 2, share=None if k == 0 else "cattail0")
    for k, (x, z) in enumerate(((3, -3), (-4, -4))):
        mu = bog.bone(f"shroom{k}", (x, -2, z))
        mu.cube(f"stalk{k}", -0.5, -2, -0.5, 1, 2, 1, share=None if k == 0 else "stalk0")
        mu.cube(f"cap{k}", -1.5, -3, -1.5, 3, 1, 3, share=None if k == 0 else "cap0")
    r.look = [("head", 0.5, 0.35), ("eye_l", 0.5, 0.0), ("eye_r", 0.5, 0.0)]
    r.moves = {"Tongue Lash": "tongue", "Belly Flop": "flop", "Great Leap": "leap", "Bog Breath": "breath", "Toadstone Shards": "crown",
               "Last Croak": "roar"}
    r.pack()
    mire_clips(r)
    return r


def mire_clips(r):
    idle = r.clip("idle", 3.6, loop=True)
    # breathing is the throat: the sac swells and empties, the flanks rise a little
    idle.scale("sac", [(0, 1, 1, 1), (0.9, 1.15, 1.6, 1.1), (1.5, 1.2, 1.8, 1.15), (2.2, 1, 1, 1), (3.6, 1, 1, 1)])
    cycle(idle, "body", "scale", lambda p: (1 + 0.015 * s(p), 1 + 0.02 * s(p), 1), 8)
    cycle(idle, "head", "rot", lambda p: (-1.5 * s(p, 0.2), 0, 0), 8)
    # a slow blink
    for nm in ("lid_l", "lid_r"):
        idle.pos(nm, [(0, 0, 0, 0), (2.5, 0, 0, 0), (2.6, 0, -2.5, 0), (2.75, 0, 0, 0), (3.6, 0, 0, 0)], interp="lin")
    for k in range(5):
        cycle(idle, f"reed{k}", "rot", lambda p, k=k: (3 * s(p, k * 0.17), 0, 4 * s(p, 0.3 + k * 0.13)), 8)
    walk = r.clip("walk", 1.6, loop=True)
    # a heavy crawl: forelimbs plant alternately, the hind feet shove, the whole body rolls
    cycle(walk, "arm_l", "rot", lambda p: (30 * s(p), 0, 0), 8)
    cycle(walk, "arm_r", "rot", lambda p: (30 * s(p, 0.5), 0, 0), 8)
    cycle(walk, "thigh_l", "rot", lambda p: (22 * s(p, 0.5), 0, 0), 8)
    cycle(walk, "thigh_r", "rot", lambda p: (22 * s(p), 0, 0), 8)
    cycle(walk, "foot_l", "rot", lambda p: (-18 * max(0, c(p, 0.5)), 0, 0), 8)
    cycle(walk, "foot_r", "rot", lambda p: (-18 * max(0, c(p)), 0, 0), 8)
    cycle(walk, "body", "rot", lambda p: (0, 4 * s(p), 4 * s(p, 0.25)), 8)
    cycle(walk, "body", "pos", lambda p: (0, 1.2 * abs(s(p)), 0), 8)
    for k in range(5):
        cycle(walk, f"reed{k}", "rot", lambda p, k=k: (0, 0, 8 * s(p, 0.3 + k * 0.1)), 8)
    run = r.clip("run", 0.9, loop=True)
    # hops: crouch, launch, tuck, land
    run.pos("body", [(0, 0, 0, 0), (0.2, 0, -2, 0), (0.45, 0, 7, 0), (0.7, 0, 1, 0), (0.9, 0, 0, 0)])
    run.rot("body", [(0, 0, 0, 0), (0.2, 6, 0, 0), (0.45, -14, 0, 0), (0.7, 6, 0, 0), (0.9, 0, 0, 0)])
    for nm in ("thigh_l", "thigh_r"):
        run.rot(nm, [(0, 0, 0, 0), (0.2, -10, 0, 0), (0.45, 50, 0, 0), (0.7, 0, 0, 0), (0.9, 0, 0, 0)])
    for nm in ("foot_l", "foot_r"):
        run.rot(nm, [(0, 0, 0, 0), (0.45, 30, 0, 0), (0.7, 0, 0, 0)])
    for nm in ("arm_l", "arm_r"):
        run.rot(nm, [(0, 0, 0, 0), (0.45, -40, 0, 0), (0.7, 10, 0, 0), (0.9, 0, 0, 0)])
    # generic: the sac swells, a croak, then a head-butt
    w = r.clip("windup", 1.0)
    w.scale("sac", [(0, 1, 1, 1), (1.0, 1.3, 2.4, 1.3)])
    w.rot("head", [(0, 0, 0, 0), (1.0, -10, 0, 0)])
    w.rot("jaw", [(0, 0, 0, 0), (1.0, 6, 0, 0)])
    st = r.clip("strike", 0.6)
    st.scale("sac", [(0, 1.3, 2.4, 1.3), (0.12, 1, 1, 1)])
    st.rot("head", [(0, -10, 0, 0), (0.12, 12, 0, 0), (0.6, 0, 0, 0)])
    st.pos("body", [(0, 0, 0, 0), (0.12, 0, 0, -3), (0.6, 0, 0, 0)])
    st.rot("jaw", [(0, 6, 0, 0), (0.08, 30, 0, 0), (0.2, 0, 0, 0)])
    bite = r.clip("bite", 0.5)
    bite.rot("jaw", [(0, 0, 0, 0), (0.08, 36, 0, 0), (0.2, 0, 0, 0)])
    bite.rot("head", [(0, 0, 0, 0), (0.08, -14, 0, 0), (0.2, 8, 0, 0), (0.5, 0, 0, 0)])
    bite.pos("body", [(0, 0, 0, 0), (0.15, 0, 0, -3), (0.5, 0, 0, 0)])
    # Tongue Lash (signature): she rears her head back, the sac balloons, the mouth gapes... then the tongue
    tw = r.clip("tongue_windup", 1.3)
    tw.rot("head", [(0, 0, 0, 0), (0.4, -14, 0, 0), (1.3, -20, 0, 0)])
    tw.rot("body", [(0, 0, 0, 0), (1.3, -6, 0, 0)])
    tw.scale("sac", [(0, 1, 1, 1), (0.5, 1.4, 2.8, 1.4), (1.3, 1.5, 3.2, 1.5)])
    tw.rot("jaw", [(0, 0, 0, 0), (0.9, 6, 0, 0), (1.3, 34, 0, 0)])
    ts = r.clip("tongue_strike", 1.0)
    ts.rot("head", [(0, -20, 0, 0), (0.08, 6, 0, 0), (0.6, 4, 0, 0), (1.0, 0, 0, 0)])
    ts.rot("body", [(0, -6, 0, 0), (0.08, 4, 0, 0), (1.0, 0, 0, 0)])
    ts.rot("jaw", [(0, 34, 0, 0), (0.6, 30, 0, 0), (0.75, 0, 0, 0)])
    ts.scale("sac", [(0, 1.5, 3.2, 1.5), (0.1, 1, 1, 1)])
    ts.pos("tongue", [(0, 0, 0, 0), (0.1, 0, 0, -30), (0.35, 0, 0, -30), (0.6, 0, 0, 0), (1.0, 0, 0, 0)], interp="lin")
    ts.scale("tongue_tip", [(0, 1, 1, 1), (0.1, 1.3, 1.3, 1.3), (0.35, 1.3, 1.3, 1.3), (0.6, 1, 1, 1)], interp="lin")
    ts.rot("tongue", [(0, -20, 0, 0), (0.1, -26, 0, 0), (0.35, -24, 0, 0), (0.6, 0, 0, 0)])
    # Belly Flop: she squats, heaves up off the ground, and drops
    fw = r.clip("flop_windup", 1.5)
    fw.pos("body", [(0, 0, 0, 0), (0.6, 0, -3, 0), (1.5, 0, -3.5, 0)])
    fw.rot("body", [(0, 0, 0, 0), (0.6, 6, 0, 0), (1.5, 8, 0, 0)])
    for nm in ("thigh_l", "thigh_r"):
        fw.rot(nm, [(0, 0, 0, 0), (0.6, -12, 0, 0), (1.5, -14, 0, 0)])
    fw.scale("sac", [(0, 1, 1, 1), (1.5, 1.3, 2.2, 1.3)])
    fs = r.clip("flop_strike", 0.9)
    fs.pos("body", [(0, 0, -3.5, 0), (0.15, 0, 9, 0), (0.3, 0, 10, 0), (0.42, 0, -2, 0), (0.6, 0, 0.5, 0), (0.9, 0, 0, 0)])
    fs.rot("body", [(0, 8, 0, 0), (0.15, -12, 0, 0), (0.3, -8, 0, 0), (0.42, 6, 0, 0), (0.9, 0, 0, 0)])
    fs.scale("body", [(0, 1, 1, 1), (0.42, 1.12, 0.85, 1.1), (0.6, 0.97, 1.04, 0.98), (0.9, 1, 1, 1)])
    for nm in ("thigh_l", "thigh_r"):
        fs.rot(nm, [(0, -14, 0, 0), (0.15, 50, 0, 0), (0.42, -10, 0, 0), (0.9, 0, 0, 0)])
    for nm in ("arm_l", "arm_r"):
        fs.rot(nm, [(0, 0, 0, 0), (0.15, -50, 0, 0), (0.42, 20, 0, 0), (0.9, 0, 0, 0)])
    fs.scale("sac", [(0, 1.3, 2.2, 1.3), (0.42, 1, 1, 1)])
    # Great Leap: a deep crouch, then she launches
    lw = r.clip("leap_windup", 0.8)
    lw.pos("body", [(0, 0, 0, 0), (0.8, 0, -3, 1)])
    lw.rot("body", [(0, 0, 0, 0), (0.8, 10, 0, 0)])
    for nm in ("thigh_l", "thigh_r"):
        lw.rot(nm, [(0, 0, 0, 0), (0.8, -16, 0, 0)])
    ls = r.clip("leap_strike", 1.2)
    ls.rot("body", [(0, 10, 0, 0), (0.15, -24, 0, 0), (0.6, -10, 0, 0), (0.85, 8, 0, 0), (1.2, 0, 0, 0)])
    ls.pos("body", [(0, 0, -3, 1), (0.15, 0, 1, 0), (1.2, 0, 0, 0)])
    for nm in ("thigh_l", "thigh_r"):
        ls.rot(nm, [(0, -16, 0, 0), (0.15, 60, 0, 0), (0.7, 20, 0, 0), (0.9, -8, 0, 0), (1.2, 0, 0, 0)])
    for nm in ("foot_l", "foot_r"):
        ls.rot(nm, [(0, 0, 0, 0), (0.15, 40, 0, 0), (0.9, 0, 0, 0)])
    for nm in ("arm_l", "arm_r"):
        ls.rot(nm, [(0, 0, 0, 0), (0.15, -60, 0, 0), (0.7, -30, 0, 0), (0.9, 20, 0, 0), (1.2, 0, 0, 0)])
    # Bog Breath: the sac fills to bursting, the mouth opens on a long exhale
    bw = r.clip("breath_windup", 0.9)
    bw.scale("sac", [(0, 1, 1, 1), (0.9, 1.6, 3.4, 1.6)])
    bw.rot("head", [(0, 0, 0, 0), (0.9, -8, 0, 0)])
    bs = r.clip("breath_strike", 1.2)
    bs.scale("sac", [(0, 1.6, 3.4, 1.6), (1.0, 1, 1, 1)], interp="lin")
    bs.rot("jaw", [(0, 0, 0, 0), (0.1, 26, 0, 0), (1.0, 22, 0, 0), (1.2, 0, 0, 0)])
    bs.rot("head", [(0, -8, 0, 0), (0.1, 4, 0, 0), (1.2, 0, 0, 0)])
    # Toadstone Shards: she bows her crown toward you, the stone blazing, then flings her head up
    cw = r.clip("crown_windup", 1.0)
    cw.rot("head", [(0, 0, 0, 0), (1.0, 14, 0, 0)])
    cw.scale("crown", [(0, 1, 1, 1), (1.0, 1.4, 1.4, 1.4)])
    cs = r.clip("crown_strike", 0.6)
    cs.rot("head", [(0, 14, 0, 0), (0.1, -20, 0, 0), (0.6, 0, 0, 0)])
    cs.scale("crown", [(0, 1.4, 1.4, 1.4), (0.1, 1.7, 1.7, 1.7), (0.6, 1, 1, 1)])
    roar = r.clip("roar", 1.6)
    roar.scale("sac", [(0, 1, 1, 1), (0.4, 1.6, 3.4, 1.6), (0.8, 1.2, 1.8, 1.2), (1.1, 1.6, 3.4, 1.6), (1.6, 1, 1, 1)])
    roar.rot("head", [(0, 0, 0, 0), (0.4, -18, 0, 0), (1.2, -16, 0, 0), (1.6, 0, 0, 0)])
    roar.rot("jaw", [(0, 0, 0, 0), (0.4, 24, 0, 0), (1.2, 24, 0, 0), (1.6, 0, 0, 0)])
    roar.rot("body", [(0, 0, 0, 0), (0.4, -6, 0, 0), (1.6, 0, 0, 0)])
    warn = r.clip("warn", 1.0)
    warn.scale("sac", [(0, 1, 1, 1), (0.3, 1.5, 3.0, 1.5), (0.6, 1, 1, 1), (0.8, 1.4, 2.6, 1.4), (1.0, 1, 1, 1)])
    warn.rot("head", [(0, 0, 0, 0), (0.3, -8, 0, 0), (1.0, 0, 0, 0)])
    hurt = r.clip("hurt", 0.45)
    hurt.rot("body", [(0, 0, 0, 0), (0.06, -4, 0, 6), (0.2, 2, 0, -3), (0.45, 0, 0, 0)])
    hurt.scale("body", [(0, 1, 1, 1), (0.06, 1.05, 0.92, 1.04), (0.2, 0.98, 1.03, 0.99), (0.45, 1, 1, 1)])
    for nm in ("lid_l", "lid_r"):
        hurt.pos(nm, [(0, 0, 0, 0), (0.04, 0, -2.5, 0), (0.3, 0, -2.5, 0), (0.4, 0, 0, 0)], interp="lin")
    death = r.clip("death", 1.8)
    death.rot("body", [(0, 0, 0, 0), (0.3, -10, 0, 0), (1.0, 6, 0, 30), (1.5, 2, 0, 160), (1.8, 0, 0, 178)])
    death.pos("body", [(0, 0, 0, 0), (0.3, 0, 3, 0), (1.0, 0, 0, 0), (1.5, 0, 4, 0), (1.8, 0, 6, 0)])
    death.rot("jaw", [(0, 0, 0, 0), (0.3, 30, 0, 0), (1.8, 14, 0, 0)])
    death.scale("sac", [(0, 1, 1, 1), (0.3, 1.4, 2.6, 1.4), (1.0, 0.8, 0.6, 0.8), (1.8, 0.8, 0.6, 0.8)])
    for nm in ("arm_l", "arm_r", "thigh_l", "thigh_r"):
        death.rot(nm, [(0, 0, 0, 0), (1.5, -30, 0, 0), (1.8, -50, 0, 0)])
    for nm in ("lid_l", "lid_r"):
        death.pos(nm, [(0, 0, 0, 0), (1.0, 0, 0, 0), (1.3, 0, -2.5, 0), (1.8, 0, -2.5, 0)], interp="lin")


def paint_mire_mother(r, P):
    skin = Ramp("#141a0c", "#1f2812", "#2c3818", "#3c4a20", "#4f5f2a", "#677a36", "#839846")
    belly = Ramp("#4a4428", "#6a6238", "#8c824a", "#ada260", "#c8bf7c")
    mud = Ramp("#1a120c", "#2a1e14", "#3c2c1c", "#4e3a26", "#634a32")
    pad = Ramp("#13300f", "#1c4416", "#285c1e", "#367628", "#4a9436")
    reed = Ramp("#2a3a14", "#3c5420", "#52702c", "#6c8c3a")
    cattail = Ramp("#2a160c", "#3e2414", "#56341e", "#6e4628")
    bone = Ramp("#5e5440", "#857a5e", "#aa9f80", "#ccc2a2", "#e6dec4")
    mouth = Ramp("#2a0c10", "#46161c", "#6a2630", "#8e3844", "#b45664")
    rng = P.rng
    # warty hide: big darker warts with a lit crown, on a softly blotched olive back
    def warts(f, i, j):
        hv = _h(f.cube.name, f.name, i // 3, j // 3)
        ci, cj = i % 3, j % 3
        if hv % 5 == 0:
            if (ci, cj) == (1, 1):
                return 0.9
            if (ci, cj) in ((1, 2), (2, 1), (2, 2)):
                return -0.7
        return 0.0
    hide = combine(warts, p_blotch("mire", 6, 0.5))
    for nm in ("back", "haunch", "skull", "brow_l", "brow_r", "arm_l", "thigh_l"):
        P.paint(nm, skin, 3.0 if nm not in ("brow_l", "brow_r") else 3.5, hide)
    for nm in ("hand_l", "foot_l"):
        P.paint(nm, skin, 2.8, lambda f, i, j: -0.8 if f.name == "top" and i % 3 == 2 and j < f.h - 2 else 0)
    # pale belly and throat
    for nm in ("back", "haunch", "jaw"):
        f = P.face(nm, "bottom")
        for i, j in f.each():
            f.set(i, j, belly.at(2.6 - (0.6 if (i + j) % 5 == 0 else 0)))
    P.paint("jaw", belly, 2.8, lambda f, i, j: -0.6 if f.name in SIDES and j == 0 else 0)
    P.paint("sac", belly, 3.6, lambda f, i, j: 0.6 if f.name == "bottom" and abs(i - f.w / 2) < 3 else 0)
    f = P.face("jaw", "top")
    for i, j in f.each():
        f.set(i, j, mouth.at(1.6 + (0.8 if abs(i - f.w / 2) < 2 else 0)))
    # the mouth line: a long dark seam around the head
    for fname in ("north", "west", "east"):
        f = P.face("skull", fname)
        for i in range(f.w):
            f.set(i, f.h - 1, mouth.at(0))
    P.paint("tongue", mouth, 3.4, lambda f, i, j: 0.6 if f.name == "top" and i in (1, 2) else 0)
    P.paint("tongue_tip", mouth, 3.8)
    # eyes: big bulging amber-green domes with a black bar pupil, glowing
    P.paint("eye_l", Ramp("#3a4a10", "#6a8018", "#9cb428", "#c8dc4a", "#eaf47a"), 2.8)
    for fname in ("north", "west", "east", "south", "top"):
        f = P.face("eye_l", fname)
        g = P.face("eye_l", fname, glow=True)
        for i, j in f.each():
            g.set(i, j, hexc("#d8ff7a", 150 if fname != "top" else 90))
            if fname in ("north", "west", "east") and j in (1, 2) and 1 <= i <= f.w - 2:
                f.set(i, j, hexc("#0a0c04"))
                g.set(i, j, (0, 0, 0, 0))
    P.paint("lid_l", skin, 3.4, hide)
    # the crown: bone knobs round a toadstone that blazes
    P.paint("knob0", bone, 3.2, lambda f, i, j: 0.8 if j == 0 else 0)
    P.paint("toadstone", Ramp("#1e5a10", "#3a8c1c", "#6cc82c", "#a8f05a", "#e4ffa8"), 2.8,
            lambda f, i, j: 1.4 if (i, j) in ((1, 1),) else (-0.8 if i == f.w - 1 or j == f.h - 1 else 0))
    for fname in ("north", "south", "west", "east", "top"):
        g = P.face("toadstone", fname, glow=True)
        for i, j in g.each():
            g.set(i, j, hexc("#e4ffa8", 255))
    # the bog: mud mounds, lily pads with a notch, reeds and cattails, mushrooms; glowing pustules on the back
    for nm in ("mound_a", "mound_b"):
        P.paint(nm, mud, 2.6, combine(p_blotch("mud" + nm, 4, 0.5), lambda f, i, j: 0.6 if f.name == "top" and (i * 3 + j) % 7 == 0 else 0))
        f = P.face(nm, "top")
        for k in range(2):
            i, j = rng.randrange(f.w), rng.randrange(f.h)
            f.set(i, j, pad.at(4))
    for fname in ("top", "bottom"):
        f = P.face("pad0", fname)
        for i, j in f.each():
            d = math.hypot(i - 3, j - 3)
            notch = j < 3 and abs(i - 3) < 0.8
            if d > 3.4 or notch:
                f.set(i, j, (0, 0, 0, 0))
            else:
                f.set(i, j, pad.at(3.2 + (0.8 if d < 1 else 0) - (0.9 if d > 2.6 else 0) - (0.5 if (i + j) % 4 == 0 else 0)))
    for c in r.cubes:
        if c.name.startswith("reed"):
            P.paint(c, reed, 2.6, lambda f, i, j: 0.5 if j < 2 else 0)
        if c.name.startswith("cattail"):
            P.paint(c, cattail, 2.4)
    P.paint("stalk0", Ramp("#c8bca0", "#e6dcc4"), 1.0)
    P.paint("cap0", Ramp("#5a1a3a", "#8a2a5a", "#c03c7a", "#ff8ac8"), 2.0)
    for fname in ("top", "north", "south", "west", "east"):
        P.face("cap0", fname, glow=True).set(1, 0, hexc("#ff9ae0", 200))
    for fname in ("top", "west", "east"):
        g = P.face("back", fname, glow=True)
        f = P.face("back", fname)
        for k in range(10 if fname == "top" else 5):
            i, j = rng.randrange(f.w), rng.randrange(max(1, f.h // (1 if fname == "top" else 2)))
            f.set(i, j, hexc("#c8ff6a"))
            g.set(i, j, hexc("#d8ff8a", 230))
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# The Weaver Queen: a spider queen with knees held high (jointed legs, not vanilla's straight bars), a crowned
# cephalothorax full of eyes, hooked fangs, and a great banded abdomen trailing silk

QUEEN_LEGS = [(-5, 42), (-2, 14), (1, -14), (4, -40)]  # (z on the body, yaw spread in degrees)


def queen_leg(name, k, side):
    return f"leg{k}_{name}", f"knee{k}_{name}", f"foot{k}_{name}"


def weaver_queen():
    r = Rig("weaver_queen", (128, 128))
    r.walk_stride, r.run_stride, r.run_speed, r.shadow = 1.6, 3.2, (0.18, 0.34), 1.6
    body = r.bone("body", (0, 12, 0))
    body.cube("thorax", -5, -4, -7, 10, 7, 11)
    body.cube("face", -4, -3, -9, 8, 5, 2)
    for k, (x, rz) in enumerate(((-3, -24), (-1, -8), (1, 8), (3, 24))):
        body.bone(f"spike{k}", (x, -4, -5), (-28, 0, rz)).cube(f"spike{k}", -1, -7 if k in (1, 2) else -5, -1, 2, 7 if k in (1, 2) else 5, 2,
                                                               share=None if k < 2 else ("spike1" if k == 2 else "spike0"))
    for side, nm in ((1, "l"), (-1, "r")):
        mir = side < 0
        fang = body.bone("fang_" + nm, (side * 2, 1, -8.5), (0, 0, side * 6))
        fang.cube("fang_" + nm, -1.5, 0, -1.5, 3, 4, 3, mirror=mir, share=None if side > 0 else "fang_l")
        fang.bone("fangtip_" + nm, (0, 3.5, 0), (-30, 0, side * -40)).cube("fangtip_" + nm, -0.5, 0, -0.5, 1, 3, 1, mirror=mir,
                                                                            share=None if side > 0 else "fangtip_l")
        palp = body.bone("palp_" + nm, (side * 3.5, 0, -8), (-40, side * 20, side * -10))
        palp.cube("palp_" + nm, -0.5, -0.5, -5, 1, 1, 5, mirror=mir, share=None if side > 0 else "palp_l")
        for k, (z, yaw) in enumerate(QUEEN_LEGS):
            ln, kn, fn = queen_leg(nm, k, side)
            leg = body.bone(ln, (side * 4.5, -1, z), (0, side * yaw, side * -38))
            if side > 0:
                leg.cube(ln, 0, -1, -1, 12, 2, 2, share=None if k == 0 else "leg0_l")
            else:
                leg.cube(ln, -12, -1, -1, 12, 2, 2, mirror=True, share="leg0_l")
            knee = leg.bone(kn, (side * 11.5, 0, 0), (0, 0, side * 100))
            if side > 0:
                knee.cube(kn, 0, -1, -1, 14, 2, 2, share=None if k == 0 else "knee0_l")
            else:
                knee.cube(kn, -14, -1, -1, 14, 2, 2, mirror=True, share="knee0_l")
            foot = knee.bone(fn, (side * 13.5, 0, 0), (0, 0, side * 22))
            if side > 0:
                foot.cube(fn, 0, -0.5, -0.5, 6, 1, 1, share=None if k == 0 else "foot0_l")
            else:
                foot.cube(fn, -6, -0.5, -0.5, 6, 1, 1, mirror=True, share="foot0_l")
    ab = body.bone("abdomen", (0, -1, 3), (-18, 0, 0))
    ab.cube("abdomen", -8, -7, 0, 16, 13, 18)
    ab.cube("ab_top", -6, -9, 2, 12, 2, 13)
    ab.cube("spinner", -2, -2, 18, 4, 3, 2)
    for k, (x, rz) in enumerate(((-1, -12), (1, 12), (0, 0))):
        ab.bone(f"silk{k}", (x, -0.5, 19.5), (20, 0, rz)).cube(f"silk{k}", -1.5, 0, 0, 3, 12, 0, share=None if k == 0 else "silk0")
    r.look = [("body", 0.15, 0.1)]
    r.moves = {"Pounce": "pounce", "Web Snare": "snare", "Silk Drop": "snare", "Venom Spray": "spray", "Shroud": "shroud",
               "Web Burst": "burst", "Brood Fury": "roar"}
    r.pack()
    queen_clips(r)
    return r


def queen_clips(r):
    def legs():
        for side, nm in ((1, "l"), (-1, "r")):
            for k in range(4):
                yield side, nm, k, queen_leg(nm, k, side)
    idle = r.clip("idle", 3.0, loop=True)
    cycle(idle, "abdomen", "scale", lambda p: (1 + 0.02 * s(p), 1 + 0.035 * s(p), 1 + 0.02 * s(p)), 8)
    cycle(idle, "abdomen", "rot", lambda p: (2 * s(p, 0.2), 0, 0), 8)
    for nm, sg in (("palp_l", 1), ("palp_r", -1)):
        cycle(idle, nm, "rot", lambda p, sg=sg: (8 * max(0, s(p * 2, 0.25 if sg > 0 else 0.6)) ** 3, 0, 0), 16)
    for nm, sg in (("fang_l", 1), ("fang_r", -1)):
        cycle(idle, nm, "rot", lambda p, sg=sg: (0, 0, sg * 5 * s(p * 2)), 8)
    for k in range(3):
        cycle(idle, f"silk{k}", "rot", lambda p, k=k: (5 * s(p, k * 0.3), 0, 4 * s(p, 0.5 + k * 0.2)), 8)
    cycle(idle, "body", "pos", lambda p: (0, 0.4 * s(p), 0), 8)

    def gait(clip, swing, lift, n=8):
        # alternating tetrapods: L0 R1 L2 R3 against R0 L1 R2 L3
        for side, nm, k, (ln, kn, fn) in legs():
            ph = 0.0 if (k % 2 == 0) == (side > 0) else 0.5
            cycle(clip, ln, "rot", lambda p, ph=ph, side=side: (0, side * swing * s(p, ph), side * -lift * max(0, c(p, ph))), n)
            cycle(clip, kn, "rot", lambda p, ph=ph, side=side: (0, 0, side * -lift * 0.6 * max(0, c(p, ph))), n)
    walk = r.clip("walk", 1.0, loop=True)
    gait(walk, 16, 14)
    cycle(walk, "body", "pos", lambda p: (0, 0.4 * c(p * 2), 0), 8)
    cycle(walk, "abdomen", "rot", lambda p: (2 * c(p * 2), 3 * s(p), 0), 8)
    run = r.clip("run", 0.55, loop=True)
    gait(run, 24, 22)
    cycle(run, "body", "pos", lambda p: (0, 0.8 * c(p * 2), 0), 8)
    cycle(run, "abdomen", "rot", lambda p: (5 + 3 * c(p * 2), 0, 0), 8)

    def front_up(clip, t0, t1, raise_=60, knee=-40, extra=None):
        for side, nm, k, (ln, kn, fn) in legs():
            if k > 1:
                continue
            a = raise_ if k == 0 else raise_ * 0.6
            clip.rot(ln, [(0, 0, 0, 0), (t0, 0, side * (8 if k == 0 else 0), side * -a), (t1, 0, side * (10 if k == 0 else 2), side * -a * 1.05)])
            clip.rot(kn, [(0, 0, 0, 0), (t0, 0, 0, side * knee), (t1, 0, 0, side * knee * 1.1)])
    w = r.clip("windup", 0.8)
    front_up(w, 0.4, 0.8, 50)
    w.rot("body", [(0, 0, 0, 0), (0.8, -10, 0, 0)])
    st = r.clip("strike", 0.5)
    for side, nm, k, (ln, kn, fn) in legs():
        if k > 1:
            continue
        a = 50 if k == 0 else 30
        st.rot(ln, [(0, 0, side * 10, side * -a * 1.05), (0.1, 0, side * 4, side * 10), (0.5, 0, 0, 0)])
        st.rot(kn, [(0, 0, 0, side * -44), (0.1, 0, 0, side * 10), (0.5, 0, 0, 0)])
    st.rot("body", [(0, -10, 0, 0), (0.1, 8, 0, 0), (0.5, 0, 0, 0)])
    bite = r.clip("bite", 0.45)
    bite.rot("body", [(0, 0, 0, 0), (0.08, -8, 0, 0), (0.18, 8, 0, 0), (0.45, 0, 0, 0)])
    for nm, sg in (("fang_l", 1), ("fang_r", -1)):
        bite.rot(nm, [(0, 0, 0, 0), (0.08, 0, 0, sg * 30), (0.18, 0, 0, -sg * 10), (0.45, 0, 0, 0)])
    # Pounce (signature): she rears high on her back legs, forelegs spread wide and fangs open... then drops on you
    pw = r.clip("pounce_windup", 0.9)
    pw.rot("body", [(0, 0, 0, 0), (0.3, -14, 0, 0), (0.9, -22, 0, 0)])
    pw.pos("body", [(0, 0, 0, 0), (0.3, 0, 2, 1), (0.9, 0, 3, 2)])
    pw.rot("abdomen", [(0, 0, 0, 0), (0.9, 22, 0, 0)])
    front_up(pw, 0.3, 0.9, 70, -30)
    for side, nm, k, (ln, kn, fn) in legs():
        if k >= 2:
            pw.rot(ln, [(0, 0, 0, 0), (0.9, 0, 0, side * 14)])
    for nm, sg in (("fang_l", 1), ("fang_r", -1)):
        pw.rot(nm, [(0, 0, 0, 0), (0.6, 0, 0, sg * 28), (0.9, 0, 0, sg * 34)])
    for nm in ("palp_l", "palp_r"):
        pw.rot(nm, [(0, 0, 0, 0), (0.9, -40, 0, 0)])
    ps = r.clip("pounce_strike", 0.9)
    ps.rot("body", [(0, -22, 0, 0), (0.12, 14, 0, 0), (0.3, 6, 0, 0), (0.9, 0, 0, 0)])
    ps.pos("body", [(0, 0, 3, 2), (0.12, 0, -2, -4), (0.4, 0, 0, -2), (0.9, 0, 0, 0)])
    ps.rot("abdomen", [(0, 22, 0, 0), (0.15, -10, 0, 0), (0.9, 0, 0, 0)])
    for side, nm, k, (ln, kn, fn) in legs():
        if k > 1:
            continue
        a = 70 if k == 0 else 42
        ps.rot(ln, [(0, 0, side * 10, side * -a * 1.05), (0.12, 0, side * 6, side * 16), (0.9, 0, 0, 0)])
        ps.rot(kn, [(0, 0, 0, side * -33), (0.12, 0, 0, side * 14), (0.9, 0, 0, 0)])
    for nm, sg in (("fang_l", 1), ("fang_r", -1)):
        ps.rot(nm, [(0, 0, 0, sg * 34), (0.1, 0, 0, -sg * 12), (0.4, 0, 0, 0)])
    # Web Snare / Silk Drop: the abdomen swings down under her and the spinnerets aim; a flick
    sw = r.clip("snare_windup", 1.1)
    sw.rot("abdomen", [(0, 0, 0, 0), (0.6, 40, 0, 0), (1.1, 52, 0, 0)])
    sw.rot("body", [(0, 0, 0, 0), (1.1, -8, 0, 0)])
    sw.pos("body", [(0, 0, 0, 0), (1.1, 0, 2, 0)])
    ss = r.clip("snare_strike", 0.6)
    ss.rot("abdomen", [(0, 52, 0, 0), (0.1, 62, 0, 0), (0.6, 0, 0, 0)])
    ss.scale("abdomen", [(0, 1, 1, 1), (0.1, 0.92, 0.92, 1.06), (0.6, 1, 1, 1)])
    ss.rot("body", [(0, -8, 0, 0), (0.6, 0, 0, 0)])
    ss.pos("body", [(0, 0, 2, 0), (0.6, 0, 0, 0)])
    # Venom Spray: up on the front legs, fangs wide, a hiss
    yw = r.clip("spray_windup", 0.9)
    front_up(yw, 0.4, 0.9, 25, -20)
    yw.rot("body", [(0, 0, 0, 0), (0.9, -14, 0, 0)])
    for nm, sg in (("fang_l", 1), ("fang_r", -1)):
        yw.rot(nm, [(0, 0, 0, 0), (0.9, 0, 0, sg * 36)])
    ys = r.clip("spray_strike", 0.8)
    ys.rot("body", [(0, -14, 0, 0), (0.1, -4, -10, 0), (0.4, -4, 10, 0), (0.8, 0, 0, 0)])
    for side, nm, k, (ln, kn, fn) in legs():
        if k <= 1:
            a = 25 if k == 0 else 15
            ys.rot(ln, [(0, 0, side * 10, side * -a * 1.05), (0.8, 0, 0, 0)])
            ys.rot(kn, [(0, 0, 0, side * -22), (0.8, 0, 0, 0)])
    for nm, sg in (("fang_l", 1), ("fang_r", -1)):
        ys.rot(nm, [(0, 0, 0, sg * 36), (0.6, 0, 0, sg * 36), (0.8, 0, 0, 0)])
    # Shroud / Web Burst: legs flung wide, she rises tall, the abdomen swells
    hw = r.clip("shroud_windup", 1.2)
    for side, nm, k, (ln, kn, fn) in legs():
        hw.rot(ln, [(0, 0, 0, 0), (1.2, 0, side * QUEEN_LEGS[k][1] * 0.25, side * -24)])
        hw.rot(kn, [(0, 0, 0, 0), (1.2, 0, 0, side * -30)])
    hw.pos("body", [(0, 0, 0, 0), (1.2, 0, 4, 0)])
    hw.scale("abdomen", [(0, 1, 1, 1), (1.2, 1.12, 1.15, 1.08)])
    hs = r.clip("shroud_strike", 0.7)
    for side, nm, k, (ln, kn, fn) in legs():
        hs.rot(ln, [(0, 0, side * QUEEN_LEGS[k][1] * 0.25, side * -24), (0.12, 0, 0, side * 10), (0.7, 0, 0, 0)])
        hs.rot(kn, [(0, 0, 0, side * -30), (0.12, 0, 0, side * 8), (0.7, 0, 0, 0)])
    hs.pos("body", [(0, 0, 4, 0), (0.12, 0, -1, 0), (0.7, 0, 0, 0)])
    hs.scale("abdomen", [(0, 1.12, 1.15, 1.08), (0.1, 0.94, 0.94, 0.96), (0.7, 1, 1, 1)])
    bw = r.clip("burst_windup", 1.0)
    bw.scale("abdomen", [(0, 1, 1, 1), (0.5, 1.1, 1.12, 1.05), (0.7, 1.05, 1.06, 1.02), (1.0, 1.18, 1.2, 1.1)])
    bw.rot("abdomen", [(0, 0, 0, 0), (1.0, -10, 0, 0)])
    bs = r.clip("burst_strike", 0.5)
    bs.scale("abdomen", [(0, 1.18, 1.2, 1.1), (0.08, 0.9, 0.9, 0.95), (0.5, 1, 1, 1)])
    bs.rot("abdomen", [(0, -10, 0, 0), (0.5, 0, 0, 0)])
    roar = r.clip("roar", 1.4)
    roar.rot("body", [(0, 0, 0, 0), (0.35, -20, 0, 0), (1.0, -18, 0, 0), (1.4, 0, 0, 0)])
    roar.pos("body", [(0, 0, 0, 0), (0.35, 0, 3, 1), (1.0, 0, 3, 1), (1.4, 0, 0, 0)])
    for side, nm, k, (ln, kn, fn) in legs():
        if k <= 1:
            a = 66 if k == 0 else 40
            roar.rot(ln, [(0, 0, 0, 0), (0.35, 0, side * 12, side * -a), (0.6, 0, side * 4, side * -a * 0.9), (0.8, 0, side * 12, side * -a), (1.0, 0, side * 8, side * -a),
                          (1.4, 0, 0, 0)])
            roar.rot(kn, [(0, 0, 0, 0), (0.35, 0, 0, side * -30), (1.0, 0, 0, side * -30), (1.4, 0, 0, 0)])
    for nm, sg in (("fang_l", 1), ("fang_r", -1)):
        roar.rot(nm, [(0, 0, 0, 0), (0.35, 0, 0, sg * 36), (1.0, 0, 0, sg * 32), (1.4, 0, 0, 0)])
    warn = r.clip("warn", 1.0)
    for side, nm, k, (ln, kn, fn) in legs():
        if k == 0:
            warn.rot(ln, [(0, 0, 0, 0), (0.15, 0, 0, side * -40), (0.3, 0, 0, side * 6), (0.45, 0, 0, side * -40), (0.6, 0, 0, side * 6), (1.0, 0, 0, 0)])
    warn.rot("body", [(0, 0, 0, 0), (0.2, -6, 0, 0), (1.0, 0, 0, 0)])
    hurt = r.clip("hurt", 0.45)
    hurt.rot("body", [(0, 0, 0, 0), (0.06, -8, 6, 4), (0.22, 3, -2, -2), (0.45, 0, 0, 0)])
    hurt.rot("abdomen", [(0, 0, 0, 0), (0.06, 10, 0, 0), (0.25, -4, 0, 0), (0.45, 0, 0, 0)])
    for side, nm, k, (ln, kn, fn) in legs():
        hurt.rot(kn, [(0, 0, 0, 0), (0.06, 0, 0, side * -18), (0.3, 0, 0, 0)])
    # death: she rolls onto her back and the legs curl in, as spiders do
    death = r.clip("death", 1.8)
    death.rot("body", [(0, 0, 0, 0), (0.4, -14, 0, 0), (1.0, 0, 0, 90), (1.4, 0, 0, 180), (1.8, 0, 0, 180)])
    death.pos("body", [(0, 0, 0, 0), (0.4, 0, 2, 0), (1.0, 0, 1, 0), (1.4, 0, 6, 0), (1.8, 0, 6, 0)])
    for side, nm, k, (ln, kn, fn) in legs():
        death.rot(ln, [(0, 0, 0, 0), (0.5, 0, 0, side * -10), (1.8, 0, side * -QUEEN_LEGS[k][1] * 0.4, side * -55)])
        death.rot(kn, [(0, 0, 0, 0), (0.6, 0, 0, side * 20), (1.8, 0, 0, side * 40)])
        death.rot(fn, [(0, 0, 0, 0), (1.8, 0, 0, side * 50)])


def paint_weaver_queen(r, P):
    chitin = Ramp("#0c0a12", "#16121e", "#211a2c", "#2e243c", "#3e3250", "#524468", "#6c5c86")
    lilac = Ramp("#4a3c6a", "#6a5a92", "#9282bc", "#b8aade", "#dcd2f6", "#f4f0ff")
    silk = Ramp("#7c7a88", "#a2a0ae", "#c4c2ce", "#e0dfe8", "#f6f6fa")
    fangc = Ramp("#1a0e14", "#3a1e2a", "#6a3446", "#a05468", "#d4889a")
    rng = P.rng
    glossy = lambda f, i, j: (0.9 if f.name in SIDES and j == 1 else 0) + (0.5 if f.name == "top" and (i + j) % 7 == 0 else 0)  # noqa: E731
    for nm in ("thorax", "face", "abdomen", "ab_top", "spinner"):
        P.paint(nm, chitin, 2.8 if nm != "face" else 2.4, combine(glossy, p_blotch("q" + nm, 7, 0.3)))
    for nm in ("spike0", "spike1"):
        P.paint(nm, lilac, 2.4, lambda f, i, j: 1.2 if j == 0 else 0)
        for fname in ("top", "north", "south", "west", "east"):
            P.face(nm, fname, glow=True).set(0, 0, hexc("#e0d0ff", 200))
    # the abdomen's crown: a pale lilac hourglass-and-chevron mark on the back, and pale bands down the sides
    # the top runs back (j = 0) to front: a crowned hourglass down the middle, chevrons either side of it
    for nm in ("abdomen", "ab_top"):
        f = P.face(nm, "top")
        g = P.face(nm, "top", glow=True)
        for i, j in f.each():
            cx = (f.w - 1) / 2
            dx = abs(i - cx)
            jj = j / max(1, f.h - 1)
            hour = dx <= 0.6 + 2.6 * abs(jj - 0.5) * 2 and 0.12 < jj < 0.88
            chev = 2.5 < dx < 5.5 and int(dx + j * 0.8) % 5 in (0, 1)
            if hour:
                f.set(i, j, lilac.at(4.4 - (0.8 if dx > 0.6 + 2.6 * abs(jj - 0.5) * 2 - 1 else 0)))
                g.set(i, j, hexc("#e4d8ff", 160))
            elif chev and nm == "abdomen":
                f.set(i, j, lilac.at(2.4))
    for fname in ("west", "east", "south"):
        f = P.face("abdomen", fname)
        g = P.face("abdomen", fname, glow=True)
        for i, j in f.each():
            if fname == "south":
                d = math.hypot(i - (f.w - 1) / 2, j - (f.h - 1) / 2)
                if abs(d - 4.5) < 0.8:
                    f.set(i, j, lilac.at(3.4))
                    g.set(i, j, hexc("#d8c8ff", 110))
            elif (i + j // 2) % 6 in (0, 1) and 2 < j < f.h - 2:
                f.set(i, j, lilac.at(2.2 + (0.6 if (i + j // 2) % 6 == 0 else 0)))
    f = P.face("abdomen", "bottom")
    for i, j in f.each():
        f.set(i, j, chitin.at(1.2 + (0.6 if (i // 2 + j // 2) % 2 else 0)))
    # eyes: eight, two great ones and six small, all glowing lilac
    f = P.face("face", "north")
    g = P.face("face", "north", glow=True)
    for (i, j, big) in ((2, 1, True), (5, 1, True), (1, 3, False), (3, 3, False), (4, 3, False), (6, 3, False), (0, 1, False), (7, 1, False)):
        if big:
            for di in (0, 1):
                f.set(i + di, j, hexc("#c8b8ff"))
                f.set(i + di, j + 1, hexc("#7a5cc8"))
                g.set(i + di, j, hexc("#ffffff", 255))
                g.set(i + di, j + 1, hexc("#c8b8ff", 230))
        else:
            f.set(i, j, hexc("#a890f0"))
            g.set(i, j, hexc("#e0d4ff", 230))
    for nm in ("fang_l",):
        P.paint(nm, chitin, 2.4, glossy)
    P.paint("fangtip_l", fangc, 3.6, lambda f, i, j: 1.0 if j == f.h - 1 else 0)
    for fname in ("north", "south", "west", "east", "bottom"):
        P.face("fangtip_l", fname, glow=True).set(0, 2, hexc("#ff9ad0", 180))
    P.paint("palp_l", chitin, 2.6, glossy)
    # legs: glossy black with lilac joint bands and pale tips
    P.paint("leg0_l", chitin, 2.9, lambda f, i, j: (2.2 if i >= f.w - 2 and f.name in ("north", "south", "top", "bottom") else 0) + (0.8 if j == 0 else 0))
    P.paint("knee0_l", chitin, 2.6, lambda f, i, j: (2.0 if i < 2 and f.name in ("north", "south", "top", "bottom") else 0) + (0.8 if j == 0 else 0))
    P.paint("foot0_l", lilac, 2.0)
    for fname in ("north", "south", "top"):
        g = P.face("knee0_l", fname, glow=True)
        g.set(0, 0, hexc("#c8b8ff", 140))
    for fname in ("north", "south"):
        f = P.face("silk0", fname)
        ragged(f, rng, silk, 3.0, minlen=0.5)
        g = P.face("silk0", fname, glow=True)
        for i, j in f.each():
            if f.get(i, j)[3] and (i + j) % 4 == 0:
                g.set(i, j, hexc("#f0eaff", 90))
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# The Empress Moth: a furred, crowned body under great dusk-rose wings with gold eyespots and long trailing tails,
# feathered antennae like a diadem; she beats the air slowly and grandly

def empress_moth():
    r = Rig("empress_moth", (128, 128))
    r.shadow = 1.2
    body = r.bone("body", (0, 12, 0))
    body.cube("thorax", -3.5, -3.5, -4, 7, 7, 8)
    body.cube("collar", -4.5, -4, -5, 9, 8, 3)
    head = body.bone("head", (0, 0, -5))
    head.cube("mhead", -2.5, -2.5, -3, 5, 5, 3)
    head.bone("tongue", (0, 2, -3), (30, 0, 0)).cube("tongue", -0.5, 0, -0.5, 1, 3, 1)
    for side, nm in ((1, "l"), (-1, "r")):
        mir = side < 0

        def sh(n, side=side):
            return None if side > 0 else n
        a = head.bone("antenna_" + nm, (side * 1.5, -2.5, -2), (-35, side * -20, side * 25))
        a.cube("antenna_" + nm, 0, -10, -3, 0, 10, 7, mirror=mir, share=sh("antenna_l"))
        fw = body.bone("wing_f" + nm, (side * 3.5, -3, -1), (0, side * 8, 0))
        fw.cube("wingf_" + nm, 0 if side > 0 else -27, 0, -7, 27, 0, 19, mirror=mir, share=sh("wingf_l"))
        hw = body.bone("wing_h" + nm, (side * 3.5, -2, 3), (0, side * -12, 0))
        hw.cube("wingh_" + nm, 0 if side > 0 else -20, 0, -2, 20, 0, 16, mirror=mir, share=sh("wingh_l"))
        tail = hw.bone("tail_" + nm, (side * 12, 0, 13), (0, side * -10, 0))
        tail.cube("tailw_" + nm, -2.5, 0, 0, 5, 0, 18, mirror=mir, share=sh("tailw_l"))
        for k, z in enumerate((-2, 1, 3)):
            leg = body.bone(f"leg{k}_" + nm, (side * 2.5, 3, z), (20 - k * 15, 0, side * 25))
            leg.cube(f"mleg{k}_" + nm, -0.5, 0, -0.5, 1, 6, 1, mirror=mir, share="mleg0_l" if (k or side < 0) else None)
    ab = body.bone("abdomen", (0, 1, 4), (22, 0, 0))
    ab.cube("abdomen", -3, -3, 0, 6, 6, 9)
    ab.bone("abdomen_tip", (0, 0, 8.5), (12, 0, 0)).cube("abtip", -2, -2, 0, 4, 4, 5)
    r.look = [("head", 0.5, 0.4)]
    r.moves = {"Wingbeat": "beat", "Petal Storm": "storm", "Scale Burst": "storm", "Slumber Dust": "dust", "Dive": "dive",
               "Eyespot Glare": "glare", "Last Flight": "roar"}
    r.pack()
    moth_clips(r)
    return r


WINGS = (("wing_fl", 1, 0.0), ("wing_fr", -1, 0.0), ("wing_hl", 1, 0.08), ("wing_hr", -1, 0.08))


def moth_clips(r):
    def flap(clip, amp, bias, n=8, freq=1):
        for wb, side, lag in WINGS:
            cycle(clip, wb, "rot", lambda p, side=side, lag=lag: (0, 0, side * (bias + amp * s(p * freq, -lag))), n)
    idle = r.clip("idle", 2.4, loop=True)
    flap(idle, 26, -14)
    cycle(idle, "body", "pos", lambda p: (0, 1.2 * c(p, 0.1), 0), 8)
    cycle(idle, "body", "rot", lambda p: (3 * s(p, 0.15), 0, 0), 8)
    cycle(idle, "abdomen", "rot", lambda p: (5 * s(p, 0.3), 0, 0), 8)
    for nm, sg in (("antenna_l", 1), ("antenna_r", -1)):
        cycle(idle, nm, "rot", lambda p, sg=sg: (4 * s(p * 2, 0.2), 0, sg * 4 * s(p, 0.5)), 8)
    for nm in ("tail_l", "tail_r"):
        cycle(idle, nm, "rot", lambda p: (10 * s(p, 0.25), 0, 0), 8)
    fly = r.clip("fly", 1.2, loop=True)
    flap(fly, 36, -8)
    cycle(fly, "body", "pos", lambda p: (0, 1.6 * c(p, 0.1), 0), 8)
    for nm in ("tail_l", "tail_r"):
        cycle(fly, nm, "rot", lambda p: (16 * s(p, 0.3), 0, 0), 8)
    for k in range(3):
        for nm in ("l", "r"):
            fly.rot(f"leg{k}_{nm}", [(0, 30, 0, 0), (1.2, 30, 0, 0)])

    def wings_to(clip, t, zf, zh, keys=None):
        for wb, side, lag in WINGS:
            z = zf if wb.startswith("wing_f") else zh
            (keys or {}).setdefault(wb, []).append((t, 0, 0, side * z))
    # generic: wings lifted, a shiver, then a beat
    w = r.clip("windup", 0.9)
    for wb, side, lag in WINGS:
        w.rot(wb, [(0, 0, 0, side * -14), (0.9, 0, 0, side * -60)])
    st = r.clip("strike", 0.6)
    for wb, side, lag in WINGS:
        st.rot(wb, [(0, 0, 0, side * -60), (0.12, 0, 0, side * 30), (0.6, 0, 0, side * -14)])
    st.pos("body", [(0, 0, 0, 0), (0.12, 0, 2, 0), (0.6, 0, 0, 0)])
    bite = r.clip("bite", 0.5)
    bite.rot("body", [(0, 0, 0, 0), (0.1, 16, 0, 0), (0.22, -8, 0, 0), (0.5, 0, 0, 0)])
    bite.pos("body", [(0, 0, 0, 0), (0.12, 0, -2, -3), (0.5, 0, 0, 0)])
    # Wingbeat (signature): the wings rise all the way up and back, she draws herself tall... one huge downstroke
    bw = r.clip("beat_windup", 1.2)
    for wb, side, lag in WINGS:
        bw.rot(wb, [(0, 0, 0, side * -14), (0.5, 0, side * -10, side * -78), (1.2, 0, side * -16, side * -84)])
    bw.rot("body", [(0, 0, 0, 0), (1.2, -18, 0, 0)])
    bw.pos("body", [(0, 0, 0, 0), (1.2, 0, 2, 2)])
    bw.rot("abdomen", [(0, 0, 0, 0), (1.2, 28, 0, 0)])
    for nm in ("antenna_l", "antenna_r"):
        bw.rot(nm, [(0, 0, 0, 0), (1.2, -20, 0, 0)])
    bs = r.clip("beat_strike", 0.9)
    for wb, side, lag in WINGS:
        bs.rot(wb, [(0, 0, side * -16, side * -84), (0.12 + lag, 0, side * 6, side * 46), (0.35, 0, 0, side * 30), (0.9, 0, 0, side * -14)])
    bs.rot("body", [(0, -18, 0, 0), (0.12, 6, 0, 0), (0.9, 0, 0, 0)])
    bs.pos("body", [(0, 0, 2, 2), (0.15, 0, 5, 0), (0.5, 0, 2, 0), (0.9, 0, 0, 0)])
    bs.rot("abdomen", [(0, 28, 0, 0), (0.15, -10, 0, 0), (0.9, 0, 0, 0)])
    for nm in ("tail_l", "tail_r"):
        bs.rot(nm, [(0, 0, 0, 0), (0.15, -30, 0, 0), (0.5, 20, 0, 0), (0.9, 0, 0, 0)])
    # Petal Storm / Scale Burst: the wings shiver open and shed
    sw = r.clip("storm_windup", 1.4)
    for wb, side, lag in WINGS:
        keys = [(0, 0, 0, side * -14)] + [(0.2 + 0.1 * q, 0, 0, side * (-30 + (8 if q % 2 else -8))) for q in range(12)]
        sw.rot(wb, keys)
    sw.scale("abdomen", [(0, 1, 1, 1), (1.4, 1.1, 1.1, 1.05)])
    ss = r.clip("storm_strike", 0.7)
    for wb, side, lag in WINGS:
        ss.rot(wb, [(0, 0, 0, side * -30), (0.1, 0, 0, side * 10), (0.3, 0, 0, side * -40), (0.7, 0, 0, side * -14)])
    ss.scale("abdomen", [(0, 1.1, 1.1, 1.05), (0.7, 1, 1, 1)])
    # Slumber Dust: wings fold forward over the body and shake their scales down
    dw = r.clip("dust_windup", 0.9)
    for wb, side, lag in WINGS:
        dw.rot(wb, [(0, 0, 0, side * -14), (0.9, 0, side * 30, side * 20)])
    dw.rot("body", [(0, 0, 0, 0), (0.9, 10, 0, 0)])
    ds = r.clip("dust_strike", 0.8)
    for wb, side, lag in WINGS:
        ds.rot(wb, [(0, 0, side * 30, side * 20)] + [(0.1 + 0.08 * q, 0, side * 30, side * (20 + (10 if q % 2 else -6))) for q in range(7)] + [(0.8, 0, 0, side * -14)])
    ds.rot("body", [(0, 10, 0, 0), (0.8, 0, 0, 0)])
    # Dive: wings swept back, nose down... and the dive
    vw = r.clip("dive_windup", 0.9)
    for wb, side, lag in WINGS:
        vw.rot(wb, [(0, 0, 0, side * -14), (0.9, 0, side * -45, side * -10)])
    vw.rot("body", [(0, 0, 0, 0), (0.9, 24, 0, 0)])
    vw.pos("body", [(0, 0, 0, 0), (0.9, 0, 3, 3)])
    vs = r.clip("dive_strike", 0.8)
    for wb, side, lag in WINGS:
        vs.rot(wb, [(0, 0, side * -45, side * -10), (0.55, 0, side * -45, side * -10), (0.7, 0, 0, side * 30), (0.8, 0, 0, side * -14)])
    vs.rot("body", [(0, 24, 0, 0), (0.55, 30, 0, 0), (0.8, 0, 0, 0)])
    vs.pos("body", [(0, 0, 3, 3), (0.2, 0, -2, -6), (0.8, 0, 0, 0)])
    # Eyespot Glare: wings spread flat and still, the eyes on them staring at you
    gw = r.clip("glare_windup", 1.9)
    for wb, side, lag in WINGS:
        gw.rot(wb, [(0, 0, 0, side * -14), (0.6, 0, 0, side * 4), (1.9, 0, 0, side * 6)])
    gw.rot("body", [(0, 0, 0, 0), (0.6, -40, 0, 0), (1.9, -44, 0, 0)])
    gw.pos("body", [(0, 0, 0, 0), (0.6, 0, 3, 0), (1.9, 0, 3, 0)])
    gs = r.clip("glare_strike", 0.7)
    for wb, side, lag in WINGS:
        gs.rot(wb, [(0, 0, 0, side * 6), (0.1, 0, 0, side * 12), (0.7, 0, 0, side * -14)])
    gs.rot("body", [(0, -44, 0, 0), (0.7, 0, 0, 0)])
    gs.pos("body", [(0, 0, 3, 0), (0.7, 0, 0, 0)])
    roar = r.clip("roar", 1.4)
    for wb, side, lag in WINGS:
        roar.rot(wb, [(0, 0, 0, side * -14), (0.35, 0, 0, side * 8), (0.55, 0, 0, side * -20), (0.75, 0, 0, side * 8), (1.0, 0, 0, side * 2), (1.4, 0, 0, side * -14)])
    roar.rot("body", [(0, 0, 0, 0), (0.35, -30, 0, 0), (1.0, -26, 0, 0), (1.4, 0, 0, 0)])
    for nm in ("antenna_l", "antenna_r"):
        roar.rot(nm, [(0, 0, 0, 0), (0.35, -25, 0, 0), (1.4, 0, 0, 0)])
    warn = r.clip("warn", 1.0)
    for wb, side, lag in WINGS:
        warn.rot(wb, [(0, 0, 0, side * -14), (0.2, 0, 0, side * 8), (0.35, 0, 0, side * -40), (0.55, 0, 0, side * 8), (0.7, 0, 0, side * -40), (1.0, 0, 0, side * -14)])
    hurt = r.clip("hurt", 0.45)
    for wb, side, lag in WINGS:
        hurt.rot(wb, [(0, 0, 0, side * -14), (0.06, 0, 0, side * -50), (0.25, 0, 0, side * 0), (0.45, 0, 0, side * -14)])
    hurt.rot("body", [(0, 0, 0, 0), (0.06, -10, 8, 10), (0.25, 4, -3, -4), (0.45, 0, 0, 0)])
    death = r.clip("death", 1.8)
    for wb, side, lag in WINGS:
        death.rot(wb, [(0, 0, 0, side * -14), (0.3, 0, 0, side * -60), (0.9, 0, 0, side * 20), (1.4, 0, side * 10, side * 70), (1.8, 0, side * 10, side * 80)])
    death.rot("body", [(0, 0, 0, 0), (0.4, -20, 0, 20), (1.2, 30, 0, 70), (1.8, 20, 0, 90)])
    death.pos("body", [(0, 0, 0, 0), (0.4, 0, 2, 0), (1.4, 0, -8, 0), (1.8, 0, -9, 0)])
    death.rot("abdomen", [(0, 0, 0, 0), (1.8, 30, 0, 0)])


def paint_empress_moth(r, P):
    fur = Ramp("#5a3a4a", "#7a5466", "#9c7084", "#bc90a2", "#d8b2c0", "#eed4de", "#fbeef2")
    rose = Ramp("#3a1a34", "#5a2a4c", "#7e3e66", "#a45a84", "#c87ca2", "#e2a2c0", "#f6cade")
    gold = Ramp("#5a3a10", "#8a5e1a", "#bc8a2a", "#e8b84a", "#ffe08a", "#fff6cc")
    violet = Ramp("#1a1030", "#2c1a4a", "#44286a", "#5e3c8c")
    for nm in ("thorax", "abdomen", "abtip"):
        P.paint(nm, fur, 3.4, combine(p_strokes(2, 2, 0.6, 0.6, 0.8, seed=11), lambda f, i, j: -0.9 if nm != "thorax" and f.name in SIDES and j % 3 == 2 else 0))
    P.paint("collar", fur, 5.0, p_strokes(2, 2, 0.6, 0.5, 0.9, seed=12))
    P.paint("mhead", fur, 3.0, p_strokes(2, 2, 0.5, 0.5, 0.7, seed=13))
    P.paint("tongue", gold, 2.4)
    P.paint("mleg0_l", violet, 2.4)
    # eyes: two great dark domes with a gold shine
    for fname, xs in (("west", (0, 1, 2)), ("east", (0, 1, 2)), ("north", (0, 4))):
        f = P.face("mhead", fname)
        g = P.face("mhead", fname, glow=True)
        for x in xs:
            for y in (1, 2, 3):
                if 0 <= x < f.w and y < f.h:
                    f.set(x, y, violet.at(0.5 + (1 if y == 1 else 0)))
        if fname == "north":
            for x in (0, 4):
                g.set(x, 1, hexc("#ffe08a", 230))
    # antennae: feathered combs, gold, glowing at the barb tips
    for fname in ("west", "east"):
        f = P.face("antenna_l", fname)
        g = P.face("antenna_l", fname, glow=True)
        clear(f)
        for i, j in f.each():
            shaft = abs(i - (f.w - 1) / 2) < 0.6
            barb = (j % 2 == 0) and abs(i - (f.w - 1) / 2) <= (f.w / 2) * (1 - j / f.h * 0.5) and j > 0
            if shaft or barb:
                f.set(i, j, gold.at(4.2 if shaft else 3.2))
                if barb and abs(i - (f.w - 1) / 2) > 2:
                    g.set(i, j, hexc("#ffe8a0", 150))

    def wing(face, glowf, shape, eye, veins=6, tail=False):
        """Paint a wing on a top/bottom face: i runs out from the body (0 at the root), j runs back (0) to front."""
        clear(face)
        w, h = face.w, face.h
        for i, j in face.each():
            u = i / max(1, w - 1)
            z = 1 - j / max(1, h - 1)  # 0 at the back edge, 1 at the front edge
            if not shape(u, z):
                continue
            # margin: a cream scalloped edge
            edge = not shape(min(1, u + 1.5 / w), z) or not shape(u, min(1, z + 1.5 / h)) or not shape(u, max(0, z - 1.5 / h))
            base = 2.2 + 3.2 * u if not tail else 3.0 + 2.5 * (j / max(1, h - 1))
            v = rose.at(min(6, base))
            if edge:
                v = rose.at(6) if (i + j) % 3 else gold.at(4)
            # veins radiating from the root
            if not tail:
                ang = math.atan2(z - 0.5, u + 0.05)
                if not edge and abs((ang * veins / math.pi * 2) % 1 - 0.5) < 0.06 and u > 0.12:
                    v = rose.at(1.5)
            face.set(i, j, v)
            if edge:
                glowf.set(i, j, hexc("#ffd8ea", 90))
        if eye:
            cu, cz, rad = eye
            for i, j in face.each():
                u = i / max(1, w - 1)
                z = 1 - j / max(1, h - 1)
                d = math.hypot((u - cu) * w, (z - cz) * h)
                if d <= rad and face.get(i, j)[3]:
                    col = violet.at(1) if d > rad - 1 else gold.at(4) if d > rad - 2.2 else violet.at(0) if d > 1.0 else gold.at(5)
                    face.set(i, j, col)
                    if d <= rad - 1:
                        glowf.set(i, j, hexc("#ffe08a", 230 if d > rad - 2.2 or d <= 1.0 else 120))
    fore = lambda u, z: (0.04 + 0.72 * u ** 1.5) <= z <= (1.0 - 0.18 * u ** 2) and u <= 0.98  # noqa: E731
    hind = lambda u, z: (u / 1.0) ** 2 + ((z - 0.55) / 0.55) ** 2 <= 1.0  # noqa: E731
    tailshape = lambda u, z: abs(u - 0.5) <= 0.5 * (0.35 + 0.65 * (1 - z) ** 0.7)  # noqa: E731
    for fname in ("top", "bottom"):
        wing(P.face("wingf_l", fname), P.face("wingf_l", fname, glow=True), fore, (0.62, 0.62, 4.2), veins=7)
        wing(P.face("wingh_l", fname), P.face("wingh_l", fname, glow=True), hind, (0.45, 0.5, 3.4), veins=5)
        wing(P.face("tailw_l", fname), P.face("tailw_l", fname, glow=True), tailshape, None, tail=True)
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# The Stonehorn: a ram the size of a boulder; its back is a cairn of lichened rock, its horns are curls of stone with
# cold light in the cracks, and a matted skirt of wool hangs to its knees

ST_LEGS = [("leg_fl", "shin_fl", 0.0), ("leg_hr", "shin_hr", 0.0), ("leg_fr", "shin_fr", 0.5), ("leg_hl", "shin_hl", 0.5)]
ROCKS = ["rock_a", "rock_b", "rock_c"]


def stonehorn():
    r = Rig("stonehorn", (128, 128))
    r.walk_stride, r.run_stride, r.run_speed, r.shadow = 1.8, 3.8, (0.16, 0.32), 1.5
    body = r.bone("body", (0, 8, 0))
    body.cube("barrel", -7, -6, -10, 14, 12, 21)
    body.cube("chest", -7.5, -7, -13, 15, 13, 6)
    rocks = body.bone("rocks", (0, -6, 0))
    rocks.bone("rock_a", (0, 0, -4), (4, 0, -3)).cube("rock_a", -6.5, -7, -5, 13, 7, 11)
    rocks.bone("rock_b", (1, 0, 6), (-6, 0, 4)).cube("rock_b", -5.5, -5, -4, 11, 5, 9)
    rocks.bone("rock_c", (-1, -6, -3), (6, 20, -8)).cube("rock_c", -3.5, -4, -3, 7, 4, 7)
    for side, nm in ((1, "l"), (-1, "r")):
        mir = side < 0

        def sh(n, side=side):
            return None if side > 0 else n
        rocks.bone("crag_" + nm, (side * 6, 2, 0), (0, 0, side * 22)).cube("crag_" + nm, -2.5, -4, -4, 5, 4, 8, mirror=mir, share=sh("crag_l"))
        body.bone("skirt_" + nm, (side * 7.1, 3, -12)).cube("skirt_" + nm, 0, 0, 0, 0, 8, 22, mirror=mir, share=sh("skirt_l"))
        for front, z in ((True, -8), (False, 7.5)):
            ln = "leg_" + ("f" if front else "h") + nm
            leg = body.bone(ln, (side * 4.5, 4, z))
            leg.cube(ln, -2.5, -1, -2.5, 5, 7, 5, mirror=mir, share=sh("leg_" + ("f" if front else "h") + "l"))
            sn = "shin_" + ("f" if front else "h") + nm
            shin = leg.bone(sn, (0, 6, 0))
            shin.cube(sn, -2, 0, -2, 4, 6, 4, mirror=mir, share=sh("shin_" + ("f" if front else "h") + "l"))
    body.bone("skirt_f", (0, 4, -13.1)).cube("skirt_f", -7, 0, 0, 14, 8, 0)
    body.bone("tail", (0, -3, 11), (30, 0, 0)).cube("tail", -2, 0, -1, 4, 5, 3)
    head = body.bone("head", (0, -1, -13))
    head.cube("skull", -4, -4, -7, 8, 8, 8)
    head.cube("muzzle", -3, -2, -11, 6, 6, 4)
    head.cube("brow", -4.5, -5, -7, 9, 2, 5)
    jaw = head.bone("jaw", (0, 3.5, -6))
    jaw.cube("jaw", -2.5, 0, -5, 5, 2, 5)
    jaw.bone("beard", (0, 2, -3)).cube("beard", -2.5, 0, 0, 5, 7, 0)
    for side, nm in ((1, "l"), (-1, "r")):
        mir = side < 0

        def sh(n, side=side):
            return None if side > 0 else n
        head.bone("ear_" + nm, (side * 4, -1, -2), (0, 0, side * 70)).cube("ear_" + nm, -1, -4, -0.5, 2, 4, 1, mirror=mir, share=sh("ear_l"))
        # a ram's curl: each stone block turns further back, down and forward round the ear
        h = head.bone("horn_" + nm, (side * 3.5, -4, -2), (-25, side * 10, side * 32))
        h.cube("horn1_" + nm, -2.5, -5, -2.5, 5, 5, 5, mirror=mir, share=sh("horn1_l"))
        sizes = [(4, 5), (4, 4), (3, 4), (2, 3)]
        node = h
        for k, (w, ln) in enumerate(sizes):
            node = node.bone(f"horn{k + 2}_" + nm, (0, -4.5 if k == 0 else -sizes[k - 1][1] + 0.5, 0), (62, 0, side * 8))
            node.cube(f"horn{k + 2}_" + nm, -w / 2, -ln, -w / 2, w, ln, w, mirror=mir, share=sh(f"horn{k + 2}_l"))
    r.look = [("head", 0.7, 0.5)]
    r.moves = {"Ram Charge": "ram", "Stamp": "stamp", "Rockfall": "stamp", "Quake Ring": "stamp", "Avalanche Bellow": "bellow",
               "Last Stand": "roar"}
    r.pack()
    stonehorn_clips(r)
    return r


def stonehorn_clips(r):
    idle = r.clip("idle", 3.6, loop=True)
    cycle(idle, "body", "scale", lambda p: (1 + 0.012 * s(p), 1 + 0.02 * s(p), 1), 8)
    cycle(idle, "head", "rot", lambda p: (2 * s(p, 0.2), 4 * s(p, 0.55), 0), 8)
    cycle(idle, "jaw", "rot", lambda p: (3 * max(0, s(p * 3)), 0, 0), 12)  # chewing
    cycle(idle, "ear_l", "rot", lambda p: (0, 0, 10 * max(0, s(p * 2, 0.2)) ** 6), 16)
    cycle(idle, "ear_r", "rot", lambda p: (0, 0, -10 * max(0, s(p * 2, 0.7)) ** 6), 16)
    cycle(idle, "tail", "rot", lambda p: (0, 0, 14 * s(p * 3)), 8)
    for nm in ("skirt_l", "skirt_r", "skirt_f", "beard"):
        cycle(idle, nm, "rot", lambda p: (2 * s(p, 0.3), 0, 0), 8)
    walk = r.clip("walk", 1.3, loop=True)
    quad_gait(walk, ST_LEGS, swing=22, knee=24)
    body_bob(walk, "body", amp=0.5, roll=2.5)
    cycle(walk, "head", "rot", lambda p: (3 * c(p * 2, 0.1), 4 * s(p), 0), 8)
    for nm in ("skirt_l", "skirt_r", "skirt_f"):
        cycle(walk, nm, "rot", lambda p: (6 * s(p * 2, 0.2), 0, 0), 8)
    run = r.clip("run", 0.75, loop=True)
    quad_gait(run, [("leg_fl", "shin_fl", 0.0), ("leg_fr", "shin_fr", 0.08), ("leg_hl", "shin_hl", 0.5), ("leg_hr", "shin_hr", 0.58)], swing=40, knee=40)
    cycle(run, "body", "rot", lambda p: (6 * s(p, 0.25), 0, 0), 8)
    cycle(run, "body", "pos", lambda p: (0, 1.4 * c(p * 2), 0), 8)
    cycle(run, "head", "rot", lambda p: (16 - 5 * s(p, 0.25), 0, 0), 8)
    for nm in ("skirt_l", "skirt_r", "skirt_f"):
        cycle(run, nm, "rot", lambda p: (14 + 8 * s(p * 2), 0, 0), 8)
    w = r.clip("windup", 0.9)
    w.rot("head", [(0, 0, 0, 0), (0.9, 26, 0, 0)])
    w.rot("body", [(0, 0, 0, 0), (0.9, 5, 0, 0)])
    w.pos("body", [(0, 0, 0, 0), (0.9, 0, -1, 1.5)])
    st = r.clip("strike", 0.6)
    st.rot("head", [(0, 26, 0, 0), (0.12, -26, 0, 0), (0.6, 0, 0, 0)])
    st.rot("body", [(0, 5, 0, 0), (0.12, -4, 0, 0), (0.6, 0, 0, 0)])
    st.pos("body", [(0, 0, -1, 1.5), (0.12, 0, 0.5, -3), (0.6, 0, 0, 0)])
    bite = r.clip("bite", 0.5)
    bite.rot("head", [(0, 0, 0, 0), (0.1, 24, -6, 0), (0.22, -20, 6, 0), (0.5, 0, 0, 0)])
    bite.pos("body", [(0, 0, 0, 0), (0.22, 0, 0, -2), (0.5, 0, 0, 0)])
    # Ram Charge (signature): it backs a step, drops its horns level, scrapes the stone twice, the haunches bunch
    cw = r.clip("ram_windup", 1.3)
    cw.rot("head", [(0, 0, 0, 0), (0.3, -14, 0, 0), (0.55, 36, 0, 0), (1.3, 40, 0, 0)])
    cw.rot("body", [(0, 0, 0, 0), (0.3, -6, 0, 0), (0.55, 8, 0, 0), (1.3, 10, 0, 0)])
    cw.pos("body", [(0, 0, 0, 0), (0.3, 0, 0.5, 2), (0.55, 0, -2, 3), (1.3, 0, -2.5, 3.5)])
    cw.rot("leg_fl", [(0, 0, 0, 0), (0.6, -40, 0, 0), (0.8, 20, 0, 0), (1.0, -40, 0, 0), (1.15, 20, 0, 0), (1.3, 0, 0, 0)])
    cw.rot("shin_fl", [(0, 0, 0, 0), (0.6, 40, 0, 0), (0.8, 0, 0, 0), (1.0, 40, 0, 0), (1.15, 0, 0, 0)])
    for nm in ("leg_hl", "leg_hr"):
        cw.rot(nm, [(0, 0, 0, 0), (0.55, -18, 0, 0), (1.3, -22, 0, 0)])
    cw.rot("tail", [(0, 0, 0, 0), (0.5, -30, 0, 0), (1.3, -30, 0, 0)])
    cs = r.clip("ram_strike", 0.8)
    cs.rot("head", [(0, 40, 0, 0), (0.1, 30, 0, 0), (0.55, 30, 0, 0), (0.66, -30, 0, 0), (0.8, 0, 0, 0)])
    cs.rot("body", [(0, 10, 0, 0), (0.1, -4, 0, 0), (0.6, -4, 0, 0), (0.8, 0, 0, 0)])
    cs.pos("body", [(0, 0, -2.5, 3.5), (0.1, 0, 0, -3), (0.6, 0, 0, -3), (0.8, 0, 0, 0)])
    cs.rot("tail", [(0, -30, 0, 0), (0.8, 0, 0, 0)])
    # Stamp (and the rockfall, the quake): it rears high on its hind legs and brings the mountain down
    sw = r.clip("stamp_windup", 1.1)
    sw.rot("body", [(0, 0, 0, 0), (0.2, 4, 0, 0), (0.8, -32, 0, 0), (1.1, -36, 0, 0)])
    sw.pos("body", [(0, 0, 0, 0), (0.8, 0, 4, 2), (1.1, 0, 5, 2)])
    for nm in ("fl", "fr"):
        sw.rot("leg_" + nm, [(0, 0, 0, 0), (0.8, -55, 0, 0), (1.1, -60, 0, 0)])
        sw.rot("shin_" + nm, [(0, 0, 0, 0), (0.8, 60, 0, 0), (1.1, 66, 0, 0)])
    for nm in ("hl", "hr"):
        sw.rot("leg_" + nm, [(0, 0, 0, 0), (0.8, 32, 0, 0), (1.1, 36, 0, 0)])
    sw.rot("head", [(0, 0, 0, 0), (0.8, 22, 0, 0), (1.1, 26, 0, 0)])
    ss = r.clip("stamp_strike", 0.8)
    ss.rot("body", [(0, -36, 0, 0), (0.12, 7, 0, 0), (0.28, 2, 0, 0), (0.8, 0, 0, 0)])
    ss.pos("body", [(0, 0, 5, 2), (0.12, 0, -2, 0), (0.3, 0, 0, 0)])
    ss.scale("body", [(0, 1, 1, 1), (0.12, 1.06, 0.92, 1.04), (0.3, 0.98, 1.02, 0.99), (0.8, 1, 1, 1)])
    for nm in ("fl", "fr"):
        ss.rot("leg_" + nm, [(0, -60, 0, 0), (0.12, 10, 0, 0), (0.8, 0, 0, 0)])
        ss.rot("shin_" + nm, [(0, 66, 0, 0), (0.12, 0, 0, 0)])
    for nm in ("hl", "hr"):
        ss.rot("leg_" + nm, [(0, 36, 0, 0), (0.15, 0, 0, 0)])
    ss.rot("head", [(0, 26, 0, 0), (0.12, 34, 0, 0), (0.45, 6, 0, 0), (0.8, 0, 0, 0)])
    for k, rk in enumerate(ROCKS):
        ss.pos(rk, [(0, 0, 0, 0), (0.12, 0, 1.5 + k * 0.5, 0), (0.25, 0, -0.5, 0), (0.45, 0, 0, 0)])
    # Avalanche Bellow: the head goes up and back, the chest heaves, the jaw drops on a roar that shakes stones loose
    bw = r.clip("bellow_windup", 1.2)
    bw.rot("head", [(0, 0, 0, 0), (0.4, 14, 0, 0), (1.2, -40, 0, 0)])
    bw.scale("body", [(0, 1, 1, 1), (1.2, 1.05, 1.08, 1)])
    bw.rot("jaw", [(0, 0, 0, 0), (0.8, 10, 0, 0), (1.2, 30, 0, 0)])
    bw.rot("body", [(0, 0, 0, 0), (1.2, -6, 0, 0)])
    bs = r.clip("bellow_strike", 0.9)
    bs.rot("head", [(0, -40, 0, 0), (0.12, -10, 0, 0), (0.7, -8, 0, 0), (0.9, 0, 0, 0)])
    bs.rot("jaw", [(0, 30, 0, 0), (0.7, 36, 0, 0), (0.9, 0, 0, 0)])
    bs.scale("body", [(0, 1.05, 1.08, 1), (0.15, 0.97, 0.96, 1), (0.9, 1, 1, 1)])
    bs.rot("body", [(0, -6, 0, 0), (0.9, 0, 0, 0)])
    for k, rk in enumerate(ROCKS):
        bs.rot(rk, [(0, 0, 0, 0)] + [(0.1 + 0.07 * q, 0, 0, (3 if (q + k) % 2 else -3)) for q in range(8)] + [(0.9, 0, 0, 0)])
    roar = r.clip("roar", 1.5)
    roar.rot("head", [(0, 0, 0, 0), (0.35, -36, 0, 0), (1.1, -34, 0, 0), (1.5, 0, 0, 0)])
    roar.rot("jaw", [(0, 0, 0, 0), (0.35, 32, 0, 0), (1.1, 30, 0, 0), (1.5, 0, 0, 0)])
    roar.rot("body", [(0, 0, 0, 0), (0.35, -8, 0, 0), (1.1, -7, 0, 0), (1.5, 0, 0, 0)])
    roar.scale("body", [(0, 1, 1, 1), (0.4, 1.05, 1.07, 1), (1.1, 1.04, 1.06, 1), (1.5, 1, 1, 1)])
    warn = r.clip("warn", 1.0)
    warn.rot("head", [(0, 0, 0, 0), (0.2, 24, 12, 0), (0.4, 24, -12, 0), (0.6, 20, 0, 0), (1.0, 0, 0, 0)])
    warn.rot("leg_fl", [(0, 0, 0, 0), (0.25, -36, 0, 0), (0.45, 10, 0, 0), (0.7, 0, 0, 0)])
    hurt = r.clip("hurt", 0.45)
    hurt.rot("body", [(0, 0, 0, 0), (0.06, -4, 0, 5), (0.22, 2, 0, -3), (0.45, 0, 0, 0)])
    hurt.rot("head", [(0, 0, 0, 0), (0.06, -18, 14, 0), (0.25, 5, -4, 0), (0.45, 0, 0, 0)])
    death = r.clip("death", 1.8)
    death.rot("body", [(0, 0, 0, 0), (0.3, -8, 0, 0), (0.8, 16, 0, 8), (1.3, 6, 0, 82), (1.8, 4, 0, 86)])
    death.pos("body", [(0, 0, 0, 0), (0.3, 0, 1, 0), (0.8, 0, -4, 0), (1.3, 0, -6, 0), (1.8, 0, -7, 0)])
    death.rot("head", [(0, 0, 0, 0), (0.3, -28, 0, 0), (0.8, 24, 0, 0), (1.8, 30, 0, -14)])
    for nm in ("fl", "fr"):
        death.rot("leg_" + nm, [(0, 0, 0, 0), (0.8, -60, 0, 0), (1.8, -45, 0, 0)])
        death.rot("shin_" + nm, [(0, 0, 0, 0), (0.8, 70, 0, 0), (1.8, 60, 0, 0)])
    for nm in ("hl", "hr"):
        death.rot("leg_" + nm, [(0, 0, 0, 0), (1.0, 30, 0, 0), (1.8, 40, 0, 0)])


def paint_stonehorn(r, P):
    wool = Ramp("#3a3630", "#56504a", "#736c64", "#918a80", "#aea79c", "#c8c2b6", "#e0dbd0")
    stone = Ramp("#1e1f22", "#2e2f33", "#414247", "#56575d", "#6d6e75", "#86878e", "#a2a3aa")
    dark = Ramp("#141416", "#202124", "#2e2f33", "#3e3f44", "#505157")
    lichen = Ramp("#4a4a1a", "#6a6a24", "#8e8a30", "#b2aa44")
    hoof = Ramp("#0e0e0f", "#1c1c1e", "#2c2c2f", "#3e3e42")
    rng = P.rng
    woolp = combine(p_strokes(3, 3, 0.6, 0.6, 0.8, seed=21), p_blotch("wool", 6, 0.35))
    for nm in ("barrel", "chest", "leg_fl", "leg_hl", "tail"):
        P.paint(nm, wool, 3.0, woolp)
    for nm in ("shin_fl", "shin_hl"):
        P.paint(nm, dark, 2.4, lambda f, i, j: 0.5 if j == 0 else 0)
        for fname in SIDES + ("bottom",):
            f = P.face(nm, fname)
            for i, j in f.each():
                if fname == "bottom" or j >= f.h - 2:
                    f.set(i, j, hoof.at(2.0 - (j - (f.h - 2)) * 0.8))
    # the cairn: big stone blocks, lit edges, dark seams; lichen on the tops; icy light deep in the cracks
    crack_seed = [0]

    def crackglow(c):
        for fname in ("top", "west", "north"):
            f = P.face(c, fname)
            g = P.face(c, fname, glow=True)
            if f.w < 6 or f.h < 4:
                continue
            crack_seed[0] += 1
            rr = __import__("random").Random(crack_seed[0] * 7919)
            x, y = rr.randrange(1, f.w - 1), 0 if fname != "top" else rr.randrange(f.h)
            for k in range(rr.randint(f.h // 2 + 1, f.h + 2)):
                if not (0 <= x < f.w and 0 <= y < f.h):
                    break
                f.set(x, y, stone.at(0))
                g.set(x, y, hexc("#bfe6ff", 220 if k % 3 else 150))
                y += 1
                x += rr.choice((-1, 0, 0, 1))
    for c in ("rock_a", "rock_b", "rock_c", "crag_l"):
        P.paint(c, stone, 3.2, p_stone(rng, 1.0, 4))
        for fname in ("top",):
            f = P.face(c, fname)
            for i, j in f.each():
                if (_h(c, i // 2, j // 2) % 5) < 2:
                    f.set(i, j, lichen.at(2 + (_h(c, i, j) % 2)))
        crackglow(c)
    # horns: dark stone curls ridged across, the cold light in the ridges
    for k in range(1, 6):
        c = f"horn{k}_l"
        P.paint(c, stone, 2.2 + k * 0.35, lambda f, i, j: (-1.2 if f.name in SIDES and j % 2 == 1 else 0.5 if f.name in SIDES and j % 2 == 0 and j < 2 else 0))
        for fname in ("west", "east", "north", "south"):
            g = P.face(c, fname, glow=True)
            for i, j in g.each():
                if j % 2 == 1 and (i + k) % 4 == 0 and k >= 2:
                    g.set(i, j, hexc("#a8d8ff", 150))
    P.paint("skull", wool, 3.2, p_strokes(2, 2, 0.5, 0.5, 0.7, seed=22))
    P.paint("brow", stone, 3.0, p_stone(rng, 0.8, 3))
    P.paint("muzzle", dark, 3.2, lambda f, i, j: 0.5 if f.name == "top" else 0)
    P.paint("jaw", dark, 2.6)
    f = P.face("muzzle", "north")
    for i, j in f.each():
        f.set(i, j, dark.at(2.4 + (0.8 if j == 0 else 0)))
    f.set(1, 2, dark.at(0))
    f.set(f.w - 2, 2, dark.at(0))
    P.paint("ear_l", wool, 2.8)
    for fname in ("west", "east"):
        f = P.face("skull", fname)
        g = P.face("skull", fname, glow=True)
        ex = f.w - 3 if fname == "west" else 1
        f.set(ex, 3, hexc("#0c0c0e"))
        f.set(ex + 1, 3, hexc("#0c0c0e"))
        lit = ex + (1 if fname == "west" else 0)
        f.set(lit, 4, hexc("#a8d8ff"))
        f.set(ex + (0 if fname == "west" else 1), 4, hexc("#0c0c0e"))
        g.set(lit, 4, hexc("#e0f4ff", 255))
    for nm, fnames in (("skirt_l", ("west", "east")), ("skirt_f", ("north", "south")), ("beard", ("north", "south"))):
        for fname in fnames:
            ragged(P.face(nm, fname), rng, wool, 3.0, minlen=0.5)
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# The Mycelord: a mushroom as tall as a tree that walks on roots; a hollow old face in the stem, shelf fungi up its
# sides, root arms that hang to its knees, and a vast cap whose glowing gills breathe spores

def mycelord():
    r = Rig("mycelord", (128, 128))
    r.walk_stride, r.run_stride, r.run_speed, r.shadow = 1.8, 3.0, (0.16, 0.3), 1.5
    body = r.bone("body", (0, 14, 0))
    body.cube("base", -5.5, -8, -5, 11, 10, 10)
    trunk = body.bone("trunk", (0, -8, 0))
    trunk.cube("trunk", -4.5, -12, -4.5, 9, 12, 9)
    trunk.cube("brow", -4, -10, -5.5, 8, 2, 1)
    trunk.bone("mouth", (0, -4.5, -4.6)).cube("mouth", -2.5, 0, -1, 5, 2, 1)
    for k, (x, y, z, w, d, ry) in enumerate(((4.5, -5, -1, 4, 6, 0), (-4.5, -8, 1, 4, 5, 180), (4.5, -10, 2, 3, 4, 0), (-5, 2, -2, 3, 4, 180))):
        trunk.bone(f"shelf{k}", (x, y, z), (0, ry, -8)).cube(f"shelf{k}", 0, -1, -d / 2, w, 1, d)
    cap = trunk.bone("cap", (0, -12, 0))
    cap.cube("brim", -14, -3, -14, 28, 3, 28)
    cap.cube("dome", -11, -6, -11, 22, 3, 22)
    cap.cube("crown", -7, -8, -7, 14, 2, 14)
    for k, (x, z, h) in enumerate(((-5, 3, 3), (4, -4, 4), (6, 5, 2))):
        m = cap.bone(f"baby{k}", (x, -8 if abs(x) < 7 and abs(z) < 7 else -6, z))
        m.cube(f"bstalk{k}", -0.5, -h, -0.5, 1, h, 1, share=None if k == 0 or h != 3 else "bstalk0")
        m.cube(f"bcap{k}", -1.5, -h - 1, -1.5, 3, 1, 3, share=None if k == 0 else "bcap0")
    for side, nm in ((1, "l"), (-1, "r")):
        mir = side < 0

        def sh(n, side=side):
            return None if side > 0 else n
        arm = trunk.bone("arm_" + nm, (side * 5, -9, 0), (0, 0, side * -12))
        arm.cube("arm_" + nm, -1.5, 0, -1.5, 3, 11, 3, mirror=mir, share=sh("arm_l"))
        fore = arm.bone("fore_" + nm, (0, 10.5, 0), (-10, 0, side * 8))
        fore.cube("fore_" + nm, -1.5, 0, -1.5, 3, 10, 3, mirror=mir, share=sh("fore_l"))
        for k, (rx, rz) in enumerate(((-20, -15), (0, 0), (20, 15))):
            fore.bone(f"finger{k}_" + nm, (0, 9.5, 0), (rx, 0, side * rz)).cube(f"finger{k}_" + nm, -0.5, 0, -0.5, 1, 5, 1, mirror=mir,
                                                                                 share="finger0_l" if (k or side < 0) else None)
        leg = body.bone("leg_" + nm, (side * 3, 1, 0))
        leg.cube("leg_" + nm, -2.5, 0, -2.5, 5, 6, 5, mirror=mir, share=sh("leg_l"))
        foot = leg.bone("foot_" + nm, (0, 5.5, 0))
        foot.cube("foot_" + nm, -3, 0, -4, 6, 3.5 if False else 3, 7, mirror=mir, share=sh("foot_l"))
        for k, (ry, z) in enumerate(((-30, -3), (0, -4), (30, -3), (180, 3))):
            foot.bone(f"root{k}_" + nm, (side * (1 if k != 1 else 0), 2, z), (10, ry + side * 10, 0)).cube(f"root{k}_" + nm, -0.5, 0, -4, 1, 1, 4, mirror=mir,
                                                                                                          share="root0_l" if (k or side < 0) else None)
    r.look = [("trunk", 0.35, 0.2), ("cap", 0.2, 0.15)]
    r.moves = {"Cap Slam": "slam", "Mycel Ring": "slam", "Spore Cloud": "puff", "Spore Burst": "puff", "Spore Rain": "puff",
               "Choking Spores": "puff", "Last Bloom": "roar"}
    r.pack()
    mycelord_clips(r)
    return r


def mycelord_clips(r):
    idle = r.clip("idle", 4.0, loop=True)
    # the cap breathes: it lifts and swells a little, the gills (glow) pulse with it
    idle.scale("cap", [(0, 1, 1, 1), (1.6, 1.04, 1.1, 1.04), (2.4, 1.04, 1.1, 1.04), (4.0, 1, 1, 1)])
    cycle(idle, "trunk", "rot", lambda p: (2 * s(p, 0.2), 3 * s(p, 0.6), 1.5 * s(p)), 8)
    cycle(idle, "arm_l", "rot", lambda p: (4 * s(p, 0.1), 0, -3 * s(p)), 8)
    cycle(idle, "arm_r", "rot", lambda p: (4 * s(p, 0.4), 0, 3 * s(p)), 8)
    cycle(idle, "mouth", "pos", lambda p: (0, 0.4 * max(0, s(p * 2)), 0), 8)
    for k in range(3):
        cycle(idle, f"baby{k}", "rot", lambda p, k=k: (4 * s(p, k * 0.3), 0, 4 * s(p, 0.5 + k * 0.2)), 8)
    walk = r.clip("walk", 1.8, loop=True)
    cycle(walk, "leg_l", "rot", lambda p: (24 * s(p), 0, 0), 8)
    cycle(walk, "leg_r", "rot", lambda p: (24 * s(p, 0.5), 0, 0), 8)
    cycle(walk, "foot_l", "rot", lambda p: (-14 * max(0, c(p)), 0, 0), 8)
    cycle(walk, "foot_r", "rot", lambda p: (-14 * max(0, c(p, 0.5)), 0, 0), 8)
    cycle(walk, "body", "rot", lambda p: (0, 5 * s(p), 4 * s(p)), 8)
    cycle(walk, "body", "pos", lambda p: (0, 0.8 * abs(c(p)), 0), 8)
    cycle(walk, "trunk", "rot", lambda p: (3, 0, -5 * s(p, 0.08)), 8)
    cycle(walk, "cap", "rot", lambda p: (0, 0, -4 * s(p, 0.15)), 8)
    cycle(walk, "arm_l", "rot", lambda p: (-26 * s(p), 0, 0), 8)
    cycle(walk, "arm_r", "rot", lambda p: (-26 * s(p, 0.5), 0, 0), 8)
    cycle(walk, "fore_l", "rot", lambda p: (-12 * max(0, s(p)), 0, 0), 8)
    cycle(walk, "fore_r", "rot", lambda p: (-12 * max(0, s(p, 0.5)), 0, 0), 8)
    run = r.clip("run", 1.0, loop=True)
    cycle(run, "leg_l", "rot", lambda p: (36 * s(p), 0, 0), 8)
    cycle(run, "leg_r", "rot", lambda p: (36 * s(p, 0.5), 0, 0), 8)
    cycle(run, "body", "rot", lambda p: (8, 6 * s(p), 6 * s(p)), 8)
    cycle(run, "body", "pos", lambda p: (0, 1.4 * abs(c(p)), 0), 8)
    cycle(run, "arm_l", "rot", lambda p: (-40 * s(p), 0, 0), 8)
    cycle(run, "arm_r", "rot", lambda p: (-40 * s(p, 0.5), 0, 0), 8)
    # generic: a root-arm swipe
    w = r.clip("windup", 0.9)
    w.rot("arm_r", [(0, 0, 0, 0), (0.9, -130, 0, 20)])
    w.rot("trunk", [(0, 0, 0, 0), (0.9, -6, 18, 0)])
    st = r.clip("strike", 0.6)
    st.rot("arm_r", [(0, -130, 0, 20), (0.12, -20, 0, -10), (0.6, 0, 0, 0)])
    st.rot("trunk", [(0, -6, 18, 0), (0.12, 8, -18, 0), (0.6, 0, 0, 0)])
    bite = r.clip("bite", 0.5)
    bite.rot("arm_l", [(0, 0, 0, 0), (0.12, -90, 0, -10), (0.25, -30, 0, 0), (0.5, 0, 0, 0)])
    bite.rot("trunk", [(0, 0, 0, 0), (0.12, -4, -12, 0), (0.25, 6, 10, 0), (0.5, 0, 0, 0)])
    # Cap Slam (signature): both root arms rise overhead, it leans back on its roots... then bows and brings the cap down
    sw = r.clip("slam_windup", 1.6)
    for nm, sg in (("arm_l", 1), ("arm_r", -1)):
        sw.rot(nm, [(0, 0, 0, 0), (0.6, -150, 0, sg * -10), (1.6, -165, 0, sg * -14)])
        sw.rot(nm.replace("arm", "fore"), [(0, 0, 0, 0), (0.6, -20, 0, 0), (1.6, -26, 0, 0)])
    sw.rot("trunk", [(0, 0, 0, 0), (0.6, -14, 0, 0), (1.6, -18, 0, 0)])
    sw.rot("cap", [(0, 0, 0, 0), (0.6, -10, 0, 0), (1.6, -14, 0, 0)])
    sw.pos("body", [(0, 0, 0, 0), (0.6, 0, 1, 2), (1.6, 0, 1.5, 2.5)])
    ss = r.clip("slam_strike", 1.0)
    for nm, sg in (("arm_l", 1), ("arm_r", -1)):
        ss.rot(nm, [(0, -165, 0, sg * -14), (0.15, -40, 0, sg * -20), (0.3, -60, 0, sg * -20), (1.0, 0, 0, 0)])
        ss.rot(nm.replace("arm", "fore"), [(0, -26, 0, 0), (0.15, 0, 0, 0)])
    ss.rot("trunk", [(0, -18, 0, 0), (0.15, 36, 0, 0), (0.45, 30, 0, 0), (1.0, 0, 0, 0)])
    ss.rot("cap", [(0, -14, 0, 0), (0.15, 20, 0, 0), (0.45, 16, 0, 0), (1.0, 0, 0, 0)])
    ss.scale("cap", [(0, 1, 1, 1), (0.15, 1.1, 0.8, 1.1), (0.3, 0.97, 1.05, 0.97), (0.6, 1, 1, 1)])
    ss.pos("body", [(0, 0, 1.5, 2.5), (0.15, 0, -1.5, -2), (0.45, 0, -1, -1.5), (1.0, 0, 0, 0)])
    # the spore moves: the cap swells and lifts, the gills open; then it exhales in a shudder
    pw = r.clip("puff_windup", 1.0)
    pw.scale("cap", [(0, 1, 1, 1), (1.0, 1.15, 1.35, 1.15)])
    pw.pos("cap", [(0, 0, 0, 0), (1.0, 0, 2, 0)])
    for nm, sg in (("arm_l", 1), ("arm_r", -1)):
        pw.rot(nm, [(0, 0, 0, 0), (1.0, 0, 0, sg * -40)])
    pw.rot("mouth", [(0, 0, 0, 0), (1.0, 30, 0, 0)])
    ps = r.clip("puff_strike", 0.9)
    ps.scale("cap", [(0, 1.15, 1.35, 1.15), (0.1, 0.9, 0.75, 0.9), (0.2, 1.05, 1.1, 1.05), (0.3, 0.95, 0.9, 0.95), (0.9, 1, 1, 1)])
    ps.pos("cap", [(0, 0, 2, 0), (0.1, 0, -1, 0), (0.9, 0, 0, 0)])
    for nm, sg in (("arm_l", 1), ("arm_r", -1)):
        ps.rot(nm, [(0, 0, 0, sg * -40), (0.9, 0, 0, 0)])
    ps.rot("mouth", [(0, 30, 0, 0), (0.9, 0, 0, 0)])
    roar = r.clip("roar", 1.6)
    for nm, sg in (("arm_l", 1), ("arm_r", -1)):
        roar.rot(nm, [(0, 0, 0, 0), (0.4, -60, 0, sg * -60), (1.2, -55, 0, sg * -55), (1.6, 0, 0, 0)])
    roar.rot("trunk", [(0, 0, 0, 0), (0.4, -16, 0, 0), (1.2, -14, 0, 0), (1.6, 0, 0, 0)])
    roar.rot("cap", [(0, 0, 0, 0)] + [(0.4 + 0.08 * q, -8, 0, (4 if q % 2 else -4)) for q in range(10)] + [(1.6, 0, 0, 0)])
    roar.rot("mouth", [(0, 0, 0, 0), (0.4, 40, 0, 0), (1.2, 36, 0, 0), (1.6, 0, 0, 0)])
    warn = r.clip("warn", 1.0)
    warn.rot("arm_r", [(0, 0, 0, 0), (0.3, -90, 0, 30), (0.5, -80, 0, 20), (0.7, -90, 0, 30), (1.0, 0, 0, 0)])
    warn.rot("cap", [(0, 0, 0, 0), (0.3, 0, 0, 6), (0.6, 0, 0, -6), (1.0, 0, 0, 0)])
    hurt = r.clip("hurt", 0.45)
    hurt.rot("trunk", [(0, 0, 0, 0), (0.06, -8, 10, 5), (0.22, 3, -4, -2), (0.45, 0, 0, 0)])
    hurt.scale("cap", [(0, 1, 1, 1), (0.06, 1.06, 0.9, 1.06), (0.22, 0.98, 1.03, 0.98), (0.45, 1, 1, 1)])
    death = r.clip("death", 1.8)
    death.rot("body", [(0, 0, 0, 0), (0.4, 8, 0, 0), (1.3, -86, 0, 6), (1.8, -88, 0, 8)])
    death.pos("body", [(0, 0, 0, 0), (0.4, 0, -1, 0), (1.3, 0, -8, 0), (1.8, 0, -9, 0)])
    death.rot("cap", [(0, 0, 0, 0), (1.3, -10, 0, 0), (1.6, 6, 0, 0), (1.8, 4, 0, 0)])
    for nm, sg in (("arm_l", 1), ("arm_r", -1)):
        death.rot(nm, [(0, 0, 0, 0), (0.4, -30, 0, sg * -20), (1.3, -100, 0, sg * -50), (1.8, -100, 0, sg * -60)])
    death.scale("cap", [(0, 1, 1, 1), (1.8, 1.02, 0.85, 1.02)])


def paint_mycelord(r, P):
    capr = Ramp("#2a0a1e", "#46102e", "#6a1a44", "#8e2658", "#b2386e", "#d25488", "#ec7aa6")
    spot = Ramp("#c8b8a6", "#e2d6c6", "#f6eee4")
    stem = Ramp("#4a4036", "#6a5e50", "#8e8270", "#b0a590", "#ccc2ac", "#e2dac6")
    gill = Ramp("#7a3a5e", "#a45a80", "#cc7ea2", "#ec9ec2")
    root = Ramp("#1e1610", "#30241a", "#443426", "#5a4634", "#705a44")
    shelf = Ramp("#4a2c14", "#6e4420", "#94602e", "#b47e40", "#d4a058")
    rng = P.rng
    fibre = combine(lambda f, i, j: (-0.7 if f.name in SIDES and i % 3 == 1 and (j + i) % 5 != 0 else 0), p_blotch("stem", 6, 0.3))
    for nm in ("base", "trunk"):
        P.paint(nm, stem, 3.0, fibre)
    for nm in ("arm_l", "fore_l", "finger0_l", "leg_l", "foot_l", "root0_l"):
        P.paint(nm, root, 2.8, lambda f, i, j: (-0.8 if f.name in SIDES and (i + j // 3) % 3 == 0 else 0) + (0.5 if j == 0 else 0))
    # a hollow old face: a dark brow line over two deep sockets with a pink light, a crack of a mouth
    P.paint("brow", stem, 2.0)
    f = P.face("trunk", "north")
    g = P.face("trunk", "north", glow=True)
    for (cx, cy) in ((2, 3), (6, 3)):
        for dx in (-1, 0):
            for dy in (0, 1, 2):
                f.set(cx + dx, cy + dy, stem.at(0) if dy < 2 else hexc("#120810"))
        f.set(cx, cy + 1, hexc("#ff9ae0"))
        f.set(cx - 1, cy + 1, hexc("#ffd0ee"))
        g.set(cx, cy + 1, hexc("#ff9ae0", 255))
        g.set(cx - 1, cy + 1, hexc("#ffe0f4", 255))
        g.set(cx - 1, cy + 2, hexc("#ff9ae0", 120))
    for i in range(f.w):  # wrinkles across the old face
        if i % 3 != 1:
            f.set(i, 1, stem.at(1.4))
    P.paint("mouth", Ramp("#0a0408", "#1e0c18", "#3a1a2c"), 1.0)
    for c in r.cubes:
        if c.name.startswith("shelf") and P.own(c):
            P.paint(c, shelf, 3.0, lambda f, i, j: (0.8 if f.name == "top" and j == f.h - 1 else 0) + (-0.6 if f.name == "top" and i % 2 == 0 else 0))
    # the cap: deep magenta, darker at the rim, pale spots on the upper faces, glowing gills underneath
    spots = [(_h("myc", k) % 1000) for k in range(40)]
    for nm in ("brim", "dome", "crown"):
        P.paint(nm, capr, 3.4 if nm != "brim" else 2.8, combine(p_blotch("cap" + nm, 8, 0.35),
                                                                lambda f, i, j: (0.6 if f.name in SIDES and j == 0 else 0)))
        for fname in ("top", "north", "south", "west", "east"):
            f = P.face(nm, fname)
            n = max(1, f.w * f.h // (34 if fname == "top" else 60))
            for k in range(n):
                hv = _h(nm, fname, k)
                cx, cy = hv % max(1, f.w), (hv // 97) % max(1, f.h)
                rad = 1.7 if (hv // 13) % 3 else 2.4
                for i in range(int(cx - 2), int(cx + 3)):
                    for j in range(int(cy - 2), int(cy + 3)):
                        if 0 <= i < f.w and 0 <= j < f.h and (i - cx) ** 2 + (j - cy) ** 2 <= rad * rad:
                            f.set(i, j, spot.at(1.6 + (0.6 if j < cy else 0)))
    for nm in ("brim",):
        f = P.face(nm, "bottom")
        g = P.face(nm, "bottom", glow=True)
        cx, cy = (f.w - 1) / 2, (f.h - 1) / 2
        for i, j in f.each():
            ang = math.atan2(j - cy, i - cx)
            d = math.hypot(i - cx, j - cy)
            gillline = (ang * 18 / math.pi) % 1 < 0.45
            if d < 5:
                f.set(i, j, stem.at(2))
                continue
            f.set(i, j, gill.at(2.6 if gillline else 1.2))
            if gillline:
                g.set(i, j, hexc("#ff9ae0", int(120 + 100 * min(1.0, d / 14))))
    for k in range(3):
        if P.own(f"bstalk{k}"):
            P.paint(f"bstalk{k}", stem, 3.5)
        if P.own(f"bcap{k}"):
            P.paint(f"bcap{k}", capr, 4.0)
            for fname in ("top", "north", "west"):
                P.face(f"bcap{k}", fname, glow=True).set(1, 0, hexc("#ff9ae0", 200))
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# The Hollow Owl: a great grey owl, round and soft, with a big facial disc, ear tufts and a head that turns almost to
# its back; it glides on spread wings without a wingbeat

def hollow_owl():
    r = Rig("hollow_owl", (64, 64))
    r.walk_stride, r.run_stride, r.run_speed, r.shadow = 0.8, 1.6, (0.15, 0.3), 0.4
    body = r.bone("body", (0, 21, 0))
    body.cube("obody", -3.5, -9, -3, 7, 9, 6)
    body.cube("chest", -3, -8, -3.6, 6, 6, 1)
    head = body.bone("head", (0, -9, 0))
    head.cube("ohead", -4, -7, -3.5, 8, 7, 7)
    head.bone("disc", (0, -3.5, -3.5)).cube("disc", -4, -3, -0.5, 8, 6, 1)
    head.bone("beak", (0, -3.5, -4)).cube("beak", -0.5, 0, -1, 1, 2, 1)
    for side, nm in ((1, "l"), (-1, "r")):
        mir = side < 0

        def sh(n, side=side):
            return None if side > 0 else n
        head.bone("tuft_" + nm, (side * 2.5, -7, -1), (-15, 0, side * 20)).cube("tuft_" + nm, -1, -3, 0, 2, 3, 0, mirror=mir, share=sh("tuft_l"))
        wing = body.bone("wing_" + nm, (side * 3.5, -8.5, 0), (0, 0, side * -4))
        wing.cube("owing_" + nm, 0 if side > 0 else -1, 0, -3, 1, 7, 7, mirror=mir, share=sh("owing_l"))
        tip = wing.bone("wingtip_" + nm, (side * 0.5, 6.5, 1), (30, 0, side * -6))
        tip.cube("wingtip_" + nm, 0, 0, -3, 0, 6, 7, mirror=mir, share=sh("wingtip_l"))
        leg = body.bone("oleg_" + nm, (side * 1.5, 0, -0.5))
        leg.cube("oleg_" + nm, -1, -0.5, -1, 2, 3, 2, mirror=mir, share=sh("oleg_l"))
        leg.bone("talon_" + nm, (0, 2.5, -0.5)).cube("talon_" + nm, -1.5, 0, -1.5, 3, 0, 3, mirror=mir, share=sh("talon_l"))
    body.bone("tail", (0, -1, 3), (20, 0, 0)).cube("otail", -2, 0, 0, 4, 0, 5)
    r.look = [("head", 1.0, 0.8)]
    r.moves = {"Talon Dive": "dive"}
    r.pack()
    owl_clips(r)
    return r


def owl_clips(r):
    idle = r.clip("idle", 8.0, loop=True)
    # an owl's watch: still, then the head swivels far round, holds, snaps back; a slow blink of the whole disc
    idle.rot("head", [(0, 0, 0, 0), (1.6, 0, 0, 0), (2.0, 0, 115, 0, "lin"), (3.4, 0, 115, 0), (3.6, 0, 0, 0, "lin"), (5.2, 0, 0, 0),
                      (5.4, 8, -95, 18, "lin"), (6.6, 8, -95, 18), (6.9, 0, 0, 0, "lin"), (8.0, 0, 0, 0)], interp="cat")
    cycle(idle, "body", "scale", lambda p: (1 + 0.02 * s(p * 4), 1 + 0.025 * s(p * 4), 1), 16)
    idle.scale("disc", [(0, 1, 1, 1), (4.4, 1, 1, 1), (4.5, 1, 0.3, 1, "lin"), (4.65, 1, 1, 1, "lin"), (8.0, 1, 1, 1)])
    for nm, sg in (("tuft_l", 1), ("tuft_r", -1)):
        idle.rot(nm, [(0, 0, 0, 0), (1.9, 0, 0, sg * 12), (3.5, 0, 0, 0), (8, 0, 0, 0)])
    walk = r.clip("walk", 0.6, loop=True)
    cycle(walk, "oleg_l", "rot", lambda p: (30 * s(p), 0, 0), 8)
    cycle(walk, "oleg_r", "rot", lambda p: (30 * s(p, 0.5), 0, 0), 8)
    cycle(walk, "body", "rot", lambda p: (0, 0, 8 * s(p)), 8)
    cycle(walk, "body", "pos", lambda p: (0, 0.6 * abs(s(p)), 0), 8)
    cycle(walk, "head", "rot", lambda p: (0, 0, -8 * s(p)), 8)
    # gliding: wings straight out and still, primaries splayed, legs tucked; it rocks on the air without a beat
    air = r.clip("air", 3.0, loop=True)
    for nm, sg in (("wing_l", 1), ("wing_r", -1)):
        cycle(air, nm, "rot", lambda p, sg=sg: (0, 0, sg * (-92 + 4 * s(p, 0.25 if sg > 0 else 0.75))), 8)
        air.rot(nm.replace("wing", "wingtip"), [(0, -8, 0, sg * 6), (3.0, -8, 0, sg * 6)])
    cycle(air, "body", "rot", lambda p: (12 + 3 * s(p), 0, 5 * s(p, 0.1)), 8)
    for nm in ("oleg_l", "oleg_r"):
        air.rot(nm, [(0, 60, 0, 0), (3.0, 60, 0, 0)])
    air.rot("tail", [(0, -20, 0, 0), (3.0, -20, 0, 0)])
    air.scale("tail", [(0, 1.5, 1, 1.2), (3.0, 1.5, 1, 1.2)])
    air.rot("head", [(0, -12, 0, 0), (3.0, -12, 0, 0)])
    # Talon Dive: it hunches, wings mantling half open, then throws its talons forward
    dw = r.clip("dive_windup", 0.8)
    dw.rot("body", [(0, 0, 0, 0), (0.8, 24, 0, 0)])
    dw.pos("body", [(0, 0, 0, 0), (0.8, 0, -1.5, 0)])
    dw.rot("head", [(0, 0, 0, 0), (0.8, -20, 0, 0)])
    for nm, sg in (("wing_l", 1), ("wing_r", -1)):
        dw.rot(nm, [(0, 0, 0, sg * -4), (0.8, -20, 0, sg * -60)])
    ds = r.clip("dive_strike", 0.7)
    ds.rot("body", [(0, 24, 0, 0), (0.12, -30, 0, 0), (0.7, 0, 0, 0)])
    ds.pos("body", [(0, 0, -1.5, 0), (0.12, 0, 1, -2), (0.7, 0, 0, 0)])
    for nm, sg in (("wing_l", 1), ("wing_r", -1)):
        ds.rot(nm, [(0, -20, 0, sg * -60), (0.12, -40, 0, sg * -110), (0.4, -20, 0, sg * -60), (0.7, 0, 0, sg * -4)])
    for nm in ("oleg_l", "oleg_r"):
        ds.rot(nm, [(0, 0, 0, 0), (0.12, -70, 0, 0), (0.4, -40, 0, 0), (0.7, 0, 0, 0)])
    w = r.clip("windup", 0.6)
    w.rot("head", [(0, 0, 0, 0), (0.6, -16, 0, 0)])
    w.rot("body", [(0, 0, 0, 0), (0.6, 10, 0, 0)])
    st = r.clip("strike", 0.4)
    st.rot("head", [(0, -16, 0, 0), (0.08, 24, 0, 0), (0.4, 0, 0, 0)])
    st.rot("body", [(0, 10, 0, 0), (0.08, 18, 0, 0), (0.4, 0, 0, 0)])
    bite = r.clip("bite", 0.4)
    bite.rot("head", [(0, 0, 0, 0), (0.08, -14, 0, 0), (0.16, 26, 0, 0), (0.4, 0, 0, 0)])
    bite.rot("body", [(0, 0, 0, 0), (0.16, 14, 0, 0), (0.4, 0, 0, 0)])
    hurt = r.clip("hurt", 0.5)
    hurt.scale("body", [(0, 1, 1, 1), (0.06, 1.15, 1.08, 1.15), (0.3, 1, 1, 1)])
    hurt.rot("head", [(0, 0, 0, 0), (0.06, -18, 30, 0), (0.3, 0, 0, 0)])
    for nm, sg in (("wing_l", 1), ("wing_r", -1)):
        hurt.rot(nm, [(0, 0, 0, 0), (0.06, 0, 0, sg * -40), (0.3, 0, 0, 0)])
    death = r.clip("death", 1.0)
    death.rot("body", [(0, 0, 0, 0), (0.25, -16, 0, 0), (0.7, 0, 0, 88), (1.0, 0, 0, 90)])
    death.pos("body", [(0, 0, 0, 0), (0.25, 0, 1, 0), (0.7, 0, -2.5, 0), (1.0, 0, -2.5, 0)])
    for nm, sg in (("wing_l", 1), ("wing_r", -1)):
        death.rot(nm, [(0, 0, 0, 0), (0.5, 0, 0, sg * -70), (1.0, 0, 0, sg * -50)])
    death.rot("head", [(0, 0, 0, 0), (0.7, 20, 0, 0), (1.0, 24, 0, 0)])
    death.scale("disc", [(0, 1, 1, 1), (0.8, 1, 1, 1), (1.0, 1, 0.3, 1)])


def paint_hollow_owl(r, P):
    grey = Ramp("#2a2826", "#3e3b38", "#55514c", "#6e6963", "#8a847d", "#a7a199", "#c4beb5")
    pale = Ramp("#7e786e", "#9a9488", "#b6b0a4", "#d0cbbf", "#e6e2d8")
    dark = Ramp("#141312", "#22201e", "#33302d")
    rng = P.rng
    # soft barred plumage: a darker bar every few rows, pale streaks on the breast
    bars = lambda f, i, j: (-0.8 if f.name in SIDES and (j + (i // 3)) % 4 == 3 else 0) + (0.3 if (i * 7 + j * 3) % 11 == 0 else 0)  # noqa: E731
    P.paint("obody", grey, 3.4, bars)
    P.paint("chest", pale, 2.6, lambda f, i, j: -0.9 if f.name == "north" and (i + j) % 3 == 0 and j % 2 == 1 else 0)
    P.paint("ohead", grey, 3.6, bars)
    P.paint("owing_l", grey, 3.0, combine(p_feathers(3, 3, 0.9), lambda f, i, j: 0.4 if j < 2 else 0))
    for fname in ("west", "east"):
        f = P.face("wingtip_l", fname)
        clear(f)
        for i, j in f.each():
            # primaries: long feathers, dark-barred, ragged at the ends
            feather = i % 2 == 0 or j < f.h - 2
            if feather and j < f.h - (i % 3 == 2):
                f.set(i, j, grey.at(2.6 - (0.9 if j % 3 == 2 else 0) + (0.6 if i % 2 == 0 else 0)))
    for fname in ("top", "bottom"):
        f = P.face("otail", fname)
        for i, j in f.each():
            f.set(i, j, grey.at(2.8 - (0.9 if j % 2 == 0 else 0)))
    # the facial disc: concentric rings round two great yellow eyes
    f = P.face("disc", "north")
    g = P.face("disc", "north", glow=True)
    for i, j in f.each():
        dl = math.hypot(i - 1.5, j - 2.5)
        dr = math.hypot(i - 5.5, j - 2.5)
        d = min(dl, dr)
        ring = int(d) % 2
        f.set(i, j, pale.at(3.6 - ring * 0.9 - (0.6 if d > 3 else 0)))
    for cx in (1, 5):
        for dx in (0, 1):
            for dy in (2, 3):
                f.set(cx + dx, dy, hexc("#f2c23a") if (dx, dy) != (1 if cx == 1 else 0, 3) else hexc("#1a1208"))
                g.set(cx + dx, dy, hexc("#ffe08a", 230))
        f.set(cx + (1 if cx == 1 else 0), 3, hexc("#120c04"))
        g.set(cx + (1 if cx == 1 else 0), 3, (0, 0, 0, 0))
    for i in range(f.w):  # the disc's dark rim
        f.set(i, 0, dark.at(2))
    P.paint("disc", pale, 3.0, faces=("top", "bottom", "west", "east", "south"))
    P.paint("beak", Ramp("#3a3020", "#6a5a38", "#a08a58"), 1.6)
    for fname in ("north", "south"):
        f = P.face("tuft_l", fname)
        clear(f)
        for i, j in f.each():
            if j >= (1 - i):
                f.set(i, j, grey.at(2.0 + j * 0.5))
    P.paint("oleg_l", pale, 3.0)
    for fname in ("top", "bottom"):
        f = P.face("talon_l", fname)
        clear(f)
        for i, j in ((0, 0), (1, 0), (2, 0), (1, 1), (1, 2), (0, 2), (2, 2)):
            f.set(i, j, dark.at(1))
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# The Bogwick: a swamp firefly; its abdomen is a lantern sac that breathes light

def bogwick():
    r = Rig("bogwick", (64, 32), translucent=True)
    r.shadow = 0.2
    body = r.bone("body", (0, 16, 0))
    body.cube("thorax", -1.5, -1.5, -1.5, 3, 3, 3)
    head = body.bone("bhead", (0, 0, -1.5))
    head.cube("bhead", -1.5, -1.5, -2, 3, 2, 2)
    for side, nm in ((1, "l"), (-1, "r")):
        mir = side < 0

        def sh(n, side=side):
            return None if side > 0 else n
        head.bone("antenna_" + nm, (side * 0.8, -1.5, -1.5), (-30, side * -25, side * 20)).cube("bantenna_" + nm, 0, -4, 0, 0, 4, 2, mirror=mir, share=sh("bantenna_l"))
        el = body.bone("elytron_" + nm, (side * 0.5, -1.5, -1), (-20, 0, side * -25))
        el.cube("elytron_" + nm, 0 if side > 0 else -2, -1, 0, 2, 1, 5, mirror=mir, share=sh("elytron_l"))
        wing = body.bone("bwing_" + nm, (side * 1, -1.5, 0), (-10, 0, 0))
        wing.cube("bwing_" + nm, 0 if side > 0 else -6, 0, -1, 6, 0, 4, mirror=mir, share=sh("bwing_l"))
        for k, z in enumerate((-1, 1)):
            body.bone(f"bleg{k}_" + nm, (side * 1.4, 1.5, z), (10, 0, side * 30)).cube(f"bleg{k}_" + nm, 0, 0, -0.5, 0, 2, 1, mirror=mir, share="bleg0_l" if (k or side < 0) else None)
    sac = body.bone("sac", (0, 0, 1.5), (-12, 0, 0))
    sac.cube("sac", -2, -2, 0, 4, 4, 5)
    sac.bone("sactip", (0, 0, 5)).cube("sactip", -1.5, -1.5, 0, 3, 3, 1)
    r.look = [("bhead", 0.5, 0.5)]
    r.pack()
    bogwick_clips(r)
    return r


def bogwick_clips(r):
    idle = r.clip("idle", 2.0, loop=True)
    # the sac breathes light: it swells as the glow layer brightens (the creature's glow pulses at the same pace)
    idle.scale("sac", [(0, 1, 1, 1), (0.8, 1.14, 1.14, 1.1), (1.2, 1.14, 1.14, 1.1), (2.0, 1, 1, 1)])
    cycle(idle, "body", "pos", lambda p: (0, 0.8 * s(p), 0), 8)
    cycle(idle, "body", "rot", lambda p: (4 * s(p, 0.2), 0, 3 * s(p, 0.5)), 8)
    cycle(idle, "sac", "rot", lambda p: (6 * s(p, 0.3), 0, 0), 8)
    for nm, sg in (("antenna_l", 1), ("antenna_r", -1)):
        cycle(idle, nm, "rot", lambda p, sg=sg: (6 * s(p * 2, 0.1 if sg > 0 else 0.6), 0, 0), 8)
    fly = r.clip("fly", 0.15, loop=True)
    for nm, sg in (("bwing_l", 1), ("bwing_r", -1)):
        fly.rot(nm, [(0, 0, 0, sg * -40), (0.075, 0, 0, sg * 30), (0.15, 0, 0, sg * -40)], interp="lin")
    for nm, sg in (("elytron_l", 1), ("elytron_r", -1)):
        fly.rot(nm, [(0, 0, 0, sg * -4), (0.075, 0, 0, sg * 4), (0.15, 0, 0, sg * -4)], interp="lin")
    hurt = r.clip("hurt", 0.4)
    hurt.rot("body", [(0, 0, 0, 0), (0.05, -20, 0, 30), (0.25, 5, 0, -8), (0.4, 0, 0, 0)])
    hurt.scale("sac", [(0, 1, 1, 1), (0.05, 0.8, 0.8, 0.8), (0.25, 1.1, 1.1, 1.1), (0.4, 1, 1, 1)])
    death = r.clip("death", 1.0)
    death.rot("body", [(0, 0, 0, 0), (0.6, 30, 0, 160), (1.0, 20, 0, 180)])
    death.pos("body", [(0, 0, 0, 0), (0.8, 0, -6, 0), (1.0, 0, -6.5, 0)])
    death.scale("sac", [(0, 1, 1, 1), (1.0, 0.85, 0.85, 0.85)])


def paint_bogwick(r, P):
    shell = Ramp("#0e120a", "#1a2010", "#283018", "#384222", "#4c5830")
    sac = Ramp("#4a6a10", "#6e9418", "#98c028", "#c4e84a", "#e8ff8a", "#faffd0")
    P.paint("thorax", shell, 2.4, lambda f, i, j: 0.8 if f.name == "top" and i == 1 else 0)
    P.paint("bhead", shell, 2.0)
    for fname in ("west", "east", "north"):
        f = P.face("bhead", fname)
        g = P.face("bhead", fname, glow=True)
        f.set(0 if fname != "east" else f.w - 1, 0, hexc("#e6ff9a"))
        g.set(0 if fname != "east" else f.w - 1, 0, hexc("#e6ff9a", 200))
    P.paint("elytron_l", Ramp("#2a1e10", "#44301a", "#5e4424", "#7a5a30"), 2.2, lambda f, i, j: 1.0 if f.name == "top" and i == 0 else 0)
    for fname in ("west", "east"):
        f = P.face("bantenna_l", fname)
        clear(f)
        for j in range(f.h):
            f.set(0 if j > 1 else 1, j, shell.at(3))
    for fname in ("top", "bottom"):
        f = P.face("bwing_l", fname)
        for i, j in f.each():
            edge = i == f.w - 1 or j in (0, f.h - 1)
            f.set(i, j, hexc("#d8e8d0", 150 if edge else 90))
    for fname in ("west", "east"):
        f = P.face("bleg0_l", fname)
        for i, j in f.each():
            f.set(i, j, shell.at(1))
    # the sac: banded like a lantern's glass, lit from inside; the glow layer is the light
    for nm in ("sac", "sactip"):
        P.paint(nm, sac, 3.4, lambda f, i, j: (-1.6 if f.name in SIDES and j == 0 and nm == "sac" else 0) + (-1.0 if f.name in ("top", "west", "east") and (i if f.name == "top" else i) % 2 == 1 else 0))
        for fname in ("top", "bottom", "north", "south", "west", "east"):
            f = P.face(nm, fname)
            g = P.face(nm, fname, glow=True)
            for i, j in f.each():
                band = (i if fname in ("top", "bottom") else i) % 2 == 1
                g.set(i, j, hexc("#eaff9a", 180 if band else 255))
    return P.skin, P.glow


HEROES = {
    "briar_sow": (briar_sow, paint_briar_sow),
    "dune_wyrm": (dune_wyrm, paint_dune_wyrm),
    "plumed_coil": (plumed_coil, paint_plumed_coil),
    "old_antler": (old_antler, paint_old_antler),
    "mire_mother": (mire_mother, paint_mire_mother),
    "weaver_queen": (weaver_queen, paint_weaver_queen),
    "empress_moth": (empress_moth, paint_empress_moth),
    "stonehorn": (stonehorn, paint_stonehorn),
    "mycelord": (mycelord, paint_mycelord),
    "hollow_owl": (hollow_owl, paint_hollow_owl),
    "bogwick": (bogwick, paint_bogwick),
}
