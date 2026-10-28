"""Form bodies of the animals (SPEC 21.13): the pig (a boar), the cow and mooshroom (an aurochs), the sheep (a ram), the chicken (a
fighting cock, then a cockatrice's stance). They truly grow (FormShape's sizes): each rung is modelled at its size, never scaled.
Coats the game decides draw from their own texture over the body (FormRenderer's coat layer): the sheep's wool (tinted to its colour,
gone when sheared) and the pig's saddle. Shared helpers and the registry live in forms_bodies.py; axes are the game's.
"""
import math
import random

import fauna_rig as R
from fauna_anim import cycle, s, c
import forms_bodies as B
import _forms_hostiles as H

# the coats painted apart from the skin: {(rig id, species, family): {"wool": canvas, ...}}, read by forms_bodies.build
COATS = {}


# ---- the four-legged frame -----------------------------------------------------------------------------------------

def quad(r, p):
    """Body on four two-part legs, a neck and a head; returns (torso, head, neck). p: body (w,h,len), legs (height, thickness),
    head (w,h,d), neck (w,h,len, pitch)."""
    bw, bh, bl = p["body"]
    hgt, t = p["legs"]
    base = r.bone("base", (0, 24, 0))
    torso = base.bone("torso", (0, -hgt, 0))
    torso.cube("torso", -bw / 2, -bh, -bl / 2, bw, bh, bl)
    up = round(hgt * 0.5)
    for nm, x, z in (("fr", -1, -1), ("fl", 1, -1), ("br", -1, 1), ("bl", 1, 1)):
        px, pz = x * (bw / 2 - t / 2 - 0.5), z * (bl / 2 - t / 2 - 1)
        leg = torso.bone("leg_" + nm, (px, -1, pz))
        leg.cube("leg_" + nm, -t / 2, 0, -t / 2, t, up + 1, t, mirror=x > 0)
        shin = leg.bone("shin_" + nm, (0, up + 1, 0))
        shin.cube("shin_" + nm, -t / 2 + 0.5, 0, -t / 2 + 0.5, t - 1, hgt - up, t - 1, mirror=x > 0)
    nw, nh, nl, npitch = p["neck"]
    neck = torso.bone("neck", (0, -bh + nh / 2 + 1, -bl / 2 + 1), (npitch, 0, 0))
    neck.cube("neck", -nw / 2, -nh / 2, -nl, nw, nh, nl)
    hw, hh, hd = p["head"]
    head = neck.bone("head", (0, 0, -nl + 1), (-npitch, 0, 0))
    head.cube("head", -hw / 2, -hh / 2 - 1, -hd, hw, hh, hd)
    return torso, head, neck


QUAD_LEGS = (("fr", 0.0), ("bl", 0.0), ("fl", 0.5), ("br", 0.5))


def quad_clips(r, rung, p, heavy=1.0, graze=True, tail=True):
    """The shared motion of the big four-legged forms; the moves (stomp, charge) and roar on top."""
    hgt = p["legs"][0]
    # ---- idle: a deep slow breath through the barrel, the head lifting to look and dropping, the tail flicking, weight shifting
    idle = r.clip("idle", 5.0, loop=True)
    cycle(idle, "torso", "scale", lambda q: (1 + 0.012 * s(q * 2), 1 + 0.018 * s(q * 2), 1.0), n=10)
    cycle(idle, "torso", "rot", lambda q: (0.6 * s(q), 0, 0.8 * s(q, 0.3)), n=10)
    cycle(idle, "neck", "rot", lambda q: ((10 * max(0.0, s(q, 0.6)) ** 3 if graze else 0) - 2 * s(q, 0.1), 4 * s(q, 0.2), 0), n=12)
    cycle(idle, "head", "rot", lambda q: (2 * s(q, 0.35), 6 * s(q, 0.05), 2 * s(q, 0.4)), n=12)
    if tail:
        cycle(idle, "tail", "rot", lambda q: (4 * s(q * 2), 14 * s(q * 3, 0.1) * max(0.0, s(q)), 0), n=20)
    # ---- walk: the diagonal pairs, each knee folding as the foot comes through; the barrel rolls, the head nods with the step
    walk = r.clip("walk", 1.2, loop=True)
    sw = 24
    for nm, ph in QUAD_LEGS:
        cycle(walk, "leg_" + nm, "rot", lambda q, ph=ph: (sw * s(q, ph), 0, 0), n=12)
        cycle(walk, "shin_" + nm, "rot", lambda q, ph=ph, f=nm[0] == "f": ((-1 if f else 1) * -26 * max(0.0, c(q, ph)), 0, 0), n=12)
    cycle(walk, "torso", "rot", lambda q: (1.2 * s(2 * q), 2 * s(q), 2.5 * heavy * s(q, 0.25)), n=12)
    cycle(walk, "torso", "pos", lambda q: (0, -0.5 * heavy * c(2 * q), 0), n=12)
    cycle(walk, "neck", "rot", lambda q: (3 * s(2 * q, 0.1), -3 * s(q), 0), n=12)
    cycle(walk, "head", "rot", lambda q: (-2 * s(2 * q, 0.15), 0, 0), n=12)
    if tail:
        cycle(walk, "tail", "rot", lambda q: (6 * s(2 * q), 8 * s(q), 0), n=12)
    # ---- run: a gallop (front pair, then back pair), the body pitching over it
    run = r.clip("run", 0.6, loop=True)
    for nm, ph in (("fr", 0.0), ("fl", 0.08), ("br", 0.5), ("bl", 0.58)):
        cycle(run, "leg_" + nm, "rot", lambda q, ph=ph: (40 * s(q, ph), 0, 0), n=12)
        cycle(run, "shin_" + nm, "rot", lambda q, ph=ph, f=nm[0] == "f": ((-1 if f else 1) * -40 * max(0.0, c(q, ph)), 0, 0), n=12)
    cycle(run, "torso", "rot", lambda q: (6 * s(q, 0.25), 0, 0), n=12)
    cycle(run, "torso", "pos", lambda q: (0, -1.2 * max(0.0, s(q, 0.1)) - 0.3, 0), n=12)
    cycle(run, "neck", "rot", lambda q: (8 - 6 * s(q, 0.3), 0, 0), n=12)
    if tail:
        cycle(run, "tail", "rot", lambda q: (-30 + 8 * s(2 * q), 0, 0), n=12)
    # ---- angry: the head goes down, horns or tusks forward; a front hoof paws the ground; the breath comes hard
    angry = r.clip("angry", 2.0, loop=True)
    cycle(angry, "neck", "rot", lambda q: (18 + 2 * s(2 * q), 0, 0), n=8)
    cycle(angry, "head", "rot", lambda q: (8, 0, 0), n=4)
    cycle(angry, "torso", "rot", lambda q: (3, 0, 0), n=4)
    cycle(angry, "torso", "scale", lambda q: (1 + 0.025 * s(4 * q), 1 + 0.03 * s(4 * q), 1.0), n=12)
    cycle(angry, "leg_fr", "rot", lambda q: (-28 * max(0.0, s(q)) + 14 * max(0.0, -s(q)) ** 2, 0, 0), n=12)
    cycle(angry, "shin_fr", "rot", lambda q: (30 * max(0.0, s(q)), 0, 0), n=12)
    if tail:
        cycle(angry, "tail", "rot", lambda q: (-20, 10 * s(3 * q), 0), n=12)
    # ---- the blow: a toss of the head (gore, butt); from B a second toss the other way
    bite = r.clip("bite", 1.0 if rung >= 3 else 0.6)
    keys = [(0.0, 0, 0, 0), (0.06, 18, 0, 0), (0.16, -28, -10, 0), (0.42, 0, 0, 0)]
    if rung >= 3:
        keys += [(0.5, 18, 0, 0), (0.6, -28, 10, 0), (1.0, 0, 0, 0)]
    bite.rot("neck", keys)
    bite.pos("torso", [(0.0, 0, 0, 0), (0.16, 0, 0, -1.5), (0.42, 0, 0, 0)] + ([(0.6, 0, 0, -1.5), (1.0, 0, 0, 0)] if rung >= 3 else []))
    # ---- hurt: it shies, the head thrown up and the haunches tucking
    hurt = r.clip("hurt", 0.45)
    hurt.rot("neck", [(0.0, 0, 0, 0), (0.07, -16, 8, 0), (0.45, 0, 0, 0)])
    hurt.rot("torso", [(0.0, 0, 0, 0), (0.07, -4, 4, 4), (0.45, 0, 0, 0)])
    hurt.pos("torso", [(0.0, 0, 0, 0), (0.07, 0, 0, 1.0), (0.45, 0, 0, 0)])
    # ---- death: the legs give, it goes down onto its side (the game keeps a fallen body one second)
    death = r.clip("death", 0.95)
    drop = hgt - 1
    death.pos("torso", [(0.0, 0, 0, 0), (0.3, 0, drop * 0.4, 0), (0.6, 0, drop * 0.7, 0), (0.95, 0, drop * 0.7, 0)])
    death.rot("torso", [(0.0, 0, 0, 0), (0.3, 4, 0, 4), (0.62, 2, 0, 88, "lin"), (0.72, 2, 0, 84), (0.95, 2, 0, 86)])
    for nm, _ in QUAD_LEGS:
        f = nm[0] == "f"
        death.rot("leg_" + nm, [(0.0, 0, 0, 0), (0.3, (-30 if f else 30), 0, 0), (0.95, (-20 if f else 26), 0, 0)])
        death.rot("shin_" + nm, [(0.0, 0, 0, 0), (0.3, (40 if f else -40), 0, 0), (0.95, (20 if f else -20), 0, 0)])
    death.rot("neck", [(0.0, 0, 0, 0), (0.6, -10, 0, 0), (0.95, -14, 0, -20)])
    # ---- roar (an alpha's, and the S's bellow): it rears up on the hind legs, the head thrown up
    roar = r.clip("roar", 1.6)
    rear = [(0.0, 0, 0, 0), (0.35, -24, 0, 0), (1.1, -22, 0, 0), (1.6, 0, 0, 0)]
    roar.rot("torso", rear)
    roar.pos("torso", [(0.0, 0, 0, 0), (0.35, 0, -p["body"][2] * 0.18, p["body"][2] * 0.08), (1.1, 0, -p["body"][2] * 0.17, p["body"][2] * 0.08), (1.6, 0, 0, 0)])
    roar.rot("neck", [(0.0, 0, 0, 0), (0.35, -20, 0, 0), (0.7, -26, 6, 0), (1.0, -22, -6, 0), (1.6, 0, 0, 0)])
    for nm in ("fr", "fl"):
        roar.rot("leg_" + nm, [(0.0, 0, 0, 0), (0.35, -40, 0, 0), (0.6, -20, 0, 0), (0.85, -44, 0, 0), (1.6, 0, 0, 0)])
        roar.rot("shin_" + nm, [(0.0, 0, 0, 0), (0.35, 50, 0, 0), (1.1, 40, 0, 0), (1.6, 0, 0, 0)])
    for nm in ("br", "bl"):
        roar.rot("leg_" + nm, [(0.0, 0, 0, 0), (0.35, 24, 0, 0), (1.1, 22, 0, 0), (1.6, 0, 0, 0)])
    # ---- stomp: it rears and brings both forefeet down on the ground
    w = r.clip("stomp_windup", 1.0)
    w.rot("torso", [(0.0, 0, 0, 0), (0.7, -22, 0, 0), (1.0, -26, 0, 0)])
    w.pos("torso", [(0.0, 0, 0, 0), (1.0, 0, -p["body"][2] * 0.2, p["body"][2] * 0.08)])
    for nm in ("fr", "fl"):
        w.rot("leg_" + nm, [(0.0, 0, 0, 0), (1.0, -44, 0, 0)])
        w.rot("shin_" + nm, [(0.0, 0, 0, 0), (1.0, 56, 0, 0)])
    for nm in ("br", "bl"):
        w.rot("leg_" + nm, [(0.0, 0, 0, 0), (1.0, 26, 0, 0)])
    w.rot("neck", [(0.0, 0, 0, 0), (1.0, -16, 0, 0)])
    st = r.clip("stomp_strike", 0.6)
    st.rot("torso", [(0.0, -26, 0, 0), (0.08, 6, 0, 0), (0.2, 2, 0, 0), (0.6, 0, 0, 0)])
    st.pos("torso", [(0.0, 0, -p["body"][2] * 0.2, p["body"][2] * 0.08), (0.08, 0, 0.8, 0), (0.6, 0, 0, 0)])
    for nm in ("fr", "fl"):
        st.rot("leg_" + nm, [(0.0, -44, 0, 0), (0.08, 8, 0, 0), (0.6, 0, 0, 0)])
        st.rot("shin_" + nm, [(0.0, 56, 0, 0), (0.08, 0, 0, 0), (0.6, 0, 0, 0)])
    for nm in ("br", "bl"):
        st.rot("leg_" + nm, [(0.0, 26, 0, 0), (0.08, 0, 0, 0), (0.6, 0, 0, 0)])
    st.rot("neck", [(0.0, -16, 0, 0), (0.08, 20, 0, 0), (0.6, 0, 0, 0)])
    # ---- charge: it paws twice and lowers its head, then drives forward
    w = r.clip("charge_windup", 1.0)
    w.rot("neck", [(0.0, 0, 0, 0), (0.4, 22, 0, 0), (1.0, 26, 0, 0)])
    w.rot("leg_fr", [(0.0, 0, 0, 0), (0.15, -30, 0, 0), (0.3, 16, 0, 0), (0.45, 0, 0, 0), (0.6, -30, 0, 0), (0.75, 16, 0, 0), (0.9, 0, 0, 0)])
    w.rot("shin_fr", [(0.0, 0, 0, 0), (0.15, 34, 0, 0), (0.3, 0, 0, 0), (0.6, 34, 0, 0), (0.75, 0, 0, 0)])
    w.pos("torso", [(0.0, 0, 0, 0), (1.0, 0, 0.8, 1.2)])
    w.rot("torso", [(0.0, 0, 0, 0), (1.0, 5, 0, 0)])
    st = r.clip("charge_strike", 0.8)
    st.rot("neck", [(0.0, 26, 0, 0), (0.6, 24, 0, 0), (0.7, -14, 0, 0), (0.8, 0, 0, 0)])
    st.pos("torso", [(0.0, 0, 0.8, 1.2), (0.1, 0, -0.4, -2), (0.6, 0, -0.2, -1), (0.8, 0, 0, 0)])
    st.rot("torso", [(0.0, 5, 0, 0), (0.6, 4, 0, 0), (0.8, 0, 0, 0)])


def horn(parent, name, pivot, rot, segs):
    return B.shard(parent, name, pivot, rot, segs)


# ---- the pig: a boar ---------------------------------------------------------------------------------------------------

PIG = {
    2: dict(body=(11, 9, 17), legs=(7, 4), neck=(7, 7, 3, 4), head=(8, 8, 7), tusk=2, hump=False, spines=0, plates=0, crystal=0, crown=False),
    3: dict(body=(13, 11, 20), legs=(8, 4), neck=(8, 8, 3, 8), head=(9, 9, 8), tusk=3, hump=True, spines=4, plates=0, crystal=0, crown=False),
    4: dict(body=(15, 13, 23), legs=(9, 5), neck=(10, 9, 3, 10), head=(10, 10, 9), tusk=4, hump=True, spines=5, plates=3, crystal=2, crown=False),
    5: dict(body=(17, 14, 26), legs=(10, 5), neck=(11, 10, 4, 8), head=(11, 10, 10), tusk=5, hump=True, spines=5, plates=3, crystal=3, crown=True),
}


def pig(rung):
    p = PIG[rung]
    r = R.Rig(f"form_pig_{rung}", (128, 128))
    r.walk_stride, r.run_stride = 1.2 + 0.15 * (rung - 2), 2.6 + 0.3 * (rung - 2)
    r.run_speed = (0.2, 0.32)
    r.shadow = 0.6 + 0.15 * (rung - 2)
    torso, head, neck = quad(r, p)
    bw, bh, bl = p["body"]
    hw, hh, hd = p["head"]
    g = torso.bone("geart", (0, 0, 0))
    if p["hump"]:   # the boar's shoulders, heavy over the forelegs
        g.cube("hump", -(bw - 2) / 2, -bh - 2, -bl / 2 + 1, bw - 2, 3, bl // 3)
    for k in range(p["spines"]):   # the bristle mane down the back, standing up
        B.shard(g, f"spine{k}", (0, -bh - (2 if p["hump"] and k < 2 else 0), -bl / 2 + 2 + k * 2.5), (-30, 0, 0), [(2 + (k < 3), 1, 0)])
    for k in range(p["plates"]):   # hide grown into plates over the haunches
        w = bw - 4 - k
        g.cube(f"plate{k}", -w / 2, -bh - 1, bl * 0.05 + k * 3.5, w, 1, 4)
    gh = head.bone("gearh", (0, 0, 0))
    gh.cube("snout", -2.5, -1, -hd - 2, 5, 4, 2)   # the snout, broad and wet
    for side, nm in ((-1, "r"), (1, "l")):
        horn(gh, "tusk_" + nm, (side * 2.6, 1.5, -hd - 1), (-150, 0, -side * 26), [(p["tusk"], 1 + (rung >= 4), 0), (2, 1, -30)])
        ear = head.bone("ear_" + nm, (side * (hw / 2 - 1), -hh / 2 - 1, -hd + 3), (20, 0, side * -30))
        ear.cube("ear_" + nm, -1.5, -3, -0.5, 3, 3, 1, mirror=side > 0)
    B.eye_bones(head, (2.2, -2.2), -1.5, -hd - 0.15)
    if p["crown"]:
        for k, x in enumerate((-2.5, 0, 2.5)):
            horn(gh, f"crown{k}", (x, -hh / 2 - 1, -hd + 2.5), (-10, 0, x * 6), [(3 + (k == 1), 2, 0), (2, 1, -16)])
    if p["crystal"]:
        for k, (x, z) in enumerate(((-2.5, -bl / 2 + 3), (2.5, -bl / 2 + 5), (0, -bl / 2 + 8))[:p["crystal"]]):
            B.shard(g, f"xtal{k}", (x, -bh - 2, z), (-20, 0, x * 8), [(4 + (k == 0), 2, 0), (2, 1, -14)])
    tail = torso.bone("tail", (0, -bh + 2, bl / 2), (30, 0, 0))
    tail.cube("tail", -0.5, -0.5, 0, 1, 1, 3)
    # the saddle: drawn only from the coat layer, when the game says it is saddled
    torso.bone("gears", (0, 0, 0)).cube("saddle", -bw / 2 - 0.5, -bh - 0.5, -2, bw + 1, 4, 8)
    r.look = [("head", 0.6, 0.6), ("neck", 0.3, 0.2)]
    r.moves = {}
    quad_clips(r, rung, p, heavy=1.2)
    r.pack()
    return r


HIDE = {
    "home": ["#7a3330", "#b04e4b", "#d0625f", "#e2807b", "#eb968f", "#f4b4ae"],   # the vanilla pig's pink
    "pig": ["#3b1f1f", "#6b3838", "#a5605a", "#d58b80", "#eeb0a2", "#f8d0c4"],
    "boar": ["#1d1412", "#33241f", "#4d3a31", "#6a5145", "#8a6b5b", "#ab8a76"],
}


def paint_pig(r, rung, species, fam):
    p = PIG[rung]
    P = R.Painter(r, f"forms|{r.id}|{species}|{fam}")
    coat = R.Painter(r, f"forms|{r.id}|{species}|{fam}|coat")
    rng = random.Random(R._h(r.id, species, fam))
    # the hide darkens toward a boar's as it climbs: C still pink under the bristle, A and S dark
    k = B.drift(rung)
    hide = B.tint_ramp(B.toward(HIDE["home"], HIDE["boar"], rung), fam)
    bristle = R.combine(R.p_strokes(cell=3, length=3, dark=0.3 + 0.6 * k, light=0.3, density=0.25 + 0.4 * k, seed=rung),
                        R.p_blotch(rung * 7 + 4, scale=4, amount=0.15 + 0.25 * k, levels=2))
    tusk = B.tint_ramp(H.BONES["skeleton"], fam)
    for cu in r.cubes:
        n = cu.name
        if n == "saddle":
            coat.paint(cu, R.Ramp("#2b160c", "#4a2814", "#6e3d1f", "#91552c", "#b06e3a"), 2.4,
                       lambda f, i, j: 1.2 if f.name == "top" and (i in (0, f.w - 1)) else (-0.8 if j == f.h - 1 else 0.0), rim=0.8)
        elif n.startswith(("tusk", "crown")):
            P.paint(cu, tusk, 2.8, None, grad=1.4, rim=0.8)
        elif n.startswith("spine"):
            P.paint(cu, hide, 1.4, None, grad=1.6)
        elif n.startswith("plate"):
            P.paint(cu, hide, 1.8, lambda f, i, j: 1.0 if j == 0 else -0.6 if j == f.h - 1 else 0.0, rim=1.0)
        elif n.startswith("xtal"):
            P.paint(cu, B.ramp_to(B.RANK[rung]), 2.2, None, grad=1.6, rim=1.2, ao=1.0)
        elif n == "snout":
            P.paint(cu, B.tint_ramp(B.toward(HIDE["home"], HIDE["pig"], rung), fam), 3.2 - k, None)
            f = P.face("snout", "north")
            f.set(1, 1, R.hexc("#1a0c0b"))
            f.set(f.w - 2, 1, R.hexc("#1a0c0b"))
        else:
            P.paint(cu, hide, 2.6, bristle, noise=0.5)
    # small deep eyes under a heavy brow
    head = P.face("head", "north")
    w = head.w
    sock = R.hexc("#060404")
    rank = R.hexc(B.RANK[rung])
    core = R.lerpc(rank, R.hexc("#ffffff"), 0.6)
    ey = 2
    with P.eyes():
        for i in (1, w - 2):
            head.set(i, ey, sock)
    g = P.face("head", "north", glow=True)
    for i in (1, w - 2):
        g.set(i, ey, core[:3] + (255,))
    for i in range(w):
        head.set(i, ey - 1, hide.at(1.6))   # the brow's shadow
    B.weathering(P, r, fam, rng, ["torso", "head"], 1 + (fam > 0))
    H.crystal_glow(P, r, rung)
    COATS[(r.id, species, fam)] = {"saddle": coat.skin}
    return P.skin, P.glow, P.blink()


# ---- the cow and the mooshroom: an aurochs ------------------------------------------------------------------------------

COW = {
    2: dict(body=(13, 11, 20), legs=(13, 4), neck=(7, 7, 4, 10), head=(8, 8, 7), horn=(4, 2), hump=False, plate=False, crystal=0, crown=False),
    3: dict(body=(15, 12, 23), legs=(15, 5), neck=(8, 8, 4, 14), head=(9, 9, 8), horn=(5, 3), hump=True, plate=False, crystal=0, crown=False),
    4: dict(body=(17, 13, 26), legs=(17, 5), neck=(9, 9, 5, 16), head=(10, 10, 9), horn=(6, 3), hump=True, plate=True, crystal=2, crown=False),
    5: dict(body=(19, 14, 29), legs=(19, 6), neck=(10, 10, 5, 12), head=(10, 10, 10), horn=(7, 3), hump=True, plate=True, crystal=2, crown=True),
}


def cow(rung):
    p = COW[rung]
    r = R.Rig(f"form_cow_{rung}", (128, 128))
    r.walk_stride, r.run_stride = 1.6 + 0.2 * (rung - 2), 3.2 + 0.3 * (rung - 2)
    r.run_speed = (0.2, 0.32)
    r.shadow = 0.7 + 0.15 * (rung - 2)
    torso, head, neck = quad(r, p)
    bw, bh, bl = p["body"]
    hw, hh, hd = p["head"]
    g = torso.bone("geart", (0, 0, 0))
    if p["hump"]:
        g.cube("hump", -(bw - 4) / 2, -bh - 3, -bl / 2 + 1, bw - 4, 4, bl // 3)
    gh = head.bone("gearh", (0, 0, 0))
    gh.cube("muzzle", -3, 0, -hd - 1, 6, 3, 1)
    hl, ht = p["horn"]
    for side, nm in ((-1, "r"), (1, "l")):
        # out from the poll, sweeping out and forward, the tips turning up: an aurochs' lyre
        h1 = gh.bone("horn_" + nm, (side * (hw / 2 - 0.5), -hh / 2, -hd + 2.5), (0, 0, side * 84))
        h1.cube(f"horn_{nm}_s1", -ht / 2, -hl, -ht / 2, ht, hl, ht)
        h2 = h1.bone(f"horn_{nm}_n2", (0, -hl + 0.5, 0), (30, 0, -side * 62))   # turning up, and a little forward
        h2.cube(f"horn_{nm}_s2", -(ht - 1) / 2, -(hl - 1), -(ht - 1) / 2, max(1, ht - 1), hl - 1, max(1, ht - 1))
        h3 = h2.bone(f"horn_{nm}_n3", (0, -(hl - 1) + 0.5, 0), (14, 0, side * 24))   # the tip flicks out
        h3.cube(f"horn_{nm}_s3", -0.5, -2, -0.5, 1, 2, 1)
        ear = head.bone("ear_" + nm, (side * (hw / 2), -hh / 2 + 1, -hd + 3), (0, 0, side * -70))
        ear.cube("ear_" + nm, -1, -3, -0.5, 2, 3, 1, mirror=side > 0)
    if p["plate"]:   # a boss of horn grown over the brow
        gh.cube("boss", -hw / 2 + 1, -hh / 2 - 2, -hd, hw - 2, 2, 4)
    if p["crown"]:
        for side, nm in ((-1, "r"), (1, "l")):
            horn(gh, "crown_" + nm, (side * 1.5, -hh / 2 - 2, -hd + 2), (-20, 0, side * 20), [(3, 2, 0), (2, 1, -20)])
    B.eye_bones(head, (hw / 2 - 1.5, -hw / 2 + 1.5), -1.5, -hd - 0.15)
    if p["crystal"]:
        for k, (x, z) in enumerate(((-2, -bl / 2 + 3), (2, -bl / 2 + 6))[:p["crystal"]]):
            B.shard(g, f"xtal{k}", (x, -bh - 3, z), (-24, 0, x * 10), [(5, 2, 0), (2, 1, -14)])
    # the mooshroom's mushrooms (left bare on a cow)
    for k, (x, z) in enumerate(((-3, -2), (3, 4), (-1, bl / 2 - 4))):
        sh = g.bone(f"shroom{k}", (x, -bh, z))
        sh.cube(f"shroom{k}_st", -0.5, -2, -0.5, 1, 2, 1)
        sh.cube(f"shroom{k}_cap", -1.5, -4, -1.5, 3, 2, 3)
    tail = torso.bone("tail", (0, -bh + 1, bl / 2), (12, 0, 0))
    tail.cube("tail", -0.5, 0, -0.5, 1, p["legs"][0] - 2, 1)
    tail.cube("tuft", -1, p["legs"][0] - 3, -1, 2, 3, 2)
    r.look = [("head", 0.55, 0.55), ("neck", 0.35, 0.25)]
    r.moves = {}
    quad_clips(r, rung, p, heavy=1.4)
    r.pack()
    return r


COWHIDE = {
    "cow": ["#120c09", "#261a13", "#3d2a1e", "#57402e", "#6e5442", "#8a6d58"],
    "mooshroom_home": ["#3a0507", "#6a0c10", "#8e0f12", "#a31012", "#b8282a", "#cc4446"],
    "mooshroom": ["#2a0606", "#4f0d0c", "#7c1714", "#a5231d", "#c8382e", "#e05a4d"],
}


def paint_cow(r, rung, species, fam):
    p = COW[rung]
    P = R.Painter(r, f"forms|{r.id}|{species}|{fam}")
    rng = random.Random(R._h(r.id, species, fam))
    hide = B.tint_ramp(B.toward(COWHIDE.get(species + "_home", COWHIDE[species]), COWHIDE[species], rung), fam)
    pale = B.tint_ramp(["#5c5650", "#8e877e", "#bdb5a9", "#ddd6ca", "#f1ece2"] if species == "cow"
                       else ["#5a4a4a", "#857171", "#ab9696", "#c4b0b0", "#d8c8c8"], fam)
    shag = R.p_strokes(cell=3, length=4, dark=0.7, light=0.35, density=0.7, seed=rung + 3)
    patches = R.p_blotch(rung * 3 + 11, scale=5, amount=1.0, levels=2)
    horn_ramp = B.tint_ramp(["#17120d", "#3b3127", "#6e604f", "#a59781", "#d6cbb6"], fam)
    for cu in r.cubes:
        n = cu.name
        if n.startswith(("horn", "crown", "boss")):
            P.paint(cu, horn_ramp, 2.6, lambda f, i, j: -0.6 if j % 3 == 2 else 0.0, grad=1.6, rim=0.8)
        elif n.startswith("xtal"):
            P.paint(cu, B.ramp_to(B.RANK[rung]), 2.2, None, grad=1.6, rim=1.2, ao=1.0)
        elif n.startswith("shroom"):
            if species != "mooshroom":
                continue
            if n.endswith("_cap"):
                P.paint(cu, R.Ramp("#4a0907", "#8e1612", "#c8261f", "#e8443a"), 2.6,
                        lambda f, i, j: (R.hexc("#efe6d6") if f.name == "top" and (i + j) % 2 == 0 and i not in (0, f.w - 1) else 0.0))
            else:
                P.paint(cu, pale, 3.0, None)
        elif n in ("muzzle",):
            P.paint(cu, pale, 2.4, None)
            f = P.face("muzzle", "north")
            f.set(1, 1, R.hexc("#100b08"))
            f.set(f.w - 2, 1, R.hexc("#100b08"))
        elif n == "tuft":
            P.paint(cu, hide, 1.6, shag)
        else:
            # vanilla's pale patches on the hide (a cow's white, a mooshroom's grey), the shaggy shoulders heavier from B
            def pat(f, i, j, n=n):
                v = patches(f, i, j)
                if n in ("torso", "head", "neck", "hump") and v is not None and v > 0.3:
                    return tuple(pale.at(2.6 + (0.3 if j == 0 else 0)))
                return shag(f, i, j) if n in ("hump", "neck") else 0.0
            P.paint(cu, hide, 2.4, pat, noise=0.5)
    head = P.face("head", "north")
    w = head.w
    rank = R.hexc(B.RANK[rung])
    core = R.lerpc(rank, R.hexc("#ffffff"), 0.6)
    with P.eyes():
        for i in (0, w - 1):
            head.set(i, 3, R.hexc("#060404"))
    g = P.face("head", "north", glow=True)
    for i in (0, w - 1):
        g.set(i, 3, core[:3] + (255,))
    B.weathering(P, r, fam, rng, ["torso", "head"], 1 + (fam > 0))
    H.crystal_glow(P, r, rung)
    return P.skin, P.glow, P.blink()


# ---- the sheep: a ram ----------------------------------------------------------------------------------------------------

SHEEP = {
    2: dict(body=(9, 8, 15), legs=(10, 3), neck=(5, 5, 3, 10), head=(6, 7, 7), curl=2, crystal=0, crown=False),
    3: dict(body=(10, 9, 17), legs=(11, 4), neck=(6, 6, 3, 12), head=(7, 7, 8), curl=3, crystal=0, crown=False),
    4: dict(body=(12, 10, 19), legs=(12, 4), neck=(7, 7, 3, 14), head=(8, 8, 8), curl=4, crystal=2, crown=False),
    5: dict(body=(13, 11, 21), legs=(13, 4), neck=(7, 7, 4, 12), head=(8, 8, 9), curl=4, crystal=2, crown=True),
}


def sheep(rung):
    p = SHEEP[rung]
    r = R.Rig(f"form_sheep_{rung}", (128, 128))
    r.walk_stride, r.run_stride = 1.3 + 0.15 * (rung - 2), 2.8 + 0.3 * (rung - 2)
    r.run_speed = (0.2, 0.32)
    r.shadow = 0.6 + 0.12 * (rung - 2)
    torso, head, neck = quad(r, p)
    bw, bh, bl = p["body"]
    hw, hh, hd = p["head"]
    # the fleece: its own cubes round the body, the neck and the top of the legs (the coat layer draws them, tinted; shorn, they go)
    gw = torso.bone("gearw", (0, 0, 0))
    gw.cube("wool", -bw / 2 - 2, -bh - 2, -bl / 2 - 1.5, bw + 4, bh + 3, bl + 3)
    neck.bone("gearn", (0, 0, 0)).cube("wool_neck", -p["neck"][0] / 2 - 1.5, -p["neck"][1] / 2 - 1.5, -p["neck"][2] - 0.5, p["neck"][0] + 3, p["neck"][1] + 3, p["neck"][2] + 1)
    head.bone("gearp", (0, 0, 0)).cube("wool_poll", -hw / 2 - 0.5, -hh / 2 - 2, -hd + 2, hw + 1, 2, hd - 2)
    for nm in ("fr", "fl", "br", "bl"):
        leg = r.by_name["leg_" + nm]
        t = p["legs"][1]
        leg.bone("gearl" + nm, (0, 0, 0)).cube("wool_leg_" + nm, -t / 2 - 1, -1, -t / 2 - 1, t + 2, 4, t + 2)
    gh = head.bone("gearh", (0, 0, 0))
    for side, nm in ((-1, "r"), (1, "l")):
        # the ram's horns: out from the poll, back, down and round past the cheek, a segment more at every rank
        segs = [(3, 3, 0), (3, 3, -50), (3, 2, -55), (2, 2, -55), (2, 1, -50)][:p["curl"] + 1]
        horn(gh, "horn_" + nm, (side * (hw / 2 - 1), -hh / 2 - 1, -hd + 4), (-30, side * -20, side * 70), segs)
        ear = head.bone("ear_" + nm, (side * (hw / 2), -hh / 2 + 2, -hd + 5), (0, 0, side * -60))
        ear.cube("ear_" + nm, -1, -3, -0.5, 2, 3, 1, mirror=side > 0)
    if p["crown"]:
        for k, x in enumerate((-1.5, 1.5)):
            horn(gh, f"crown{k}", (x, -hh / 2 - 2, -hd + 2), (-6, 0, x * 10), [(3, 1, 0), (2, 1, -12)])
    B.eye_bones(head, (hw / 2 - 1.0, -hw / 2 + 1.0), -1.5, -hd - 0.15)
    if p["crystal"]:
        for k, (x, z) in enumerate(((-2, -1), (2, 3))[:p["crystal"]]):
            B.shard(gw, f"xtal{k}", (x, -bh - 2, z), (-20, 0, x * 10), [(4, 2, 0), (2, 1, -14)])
    tail = torso.bone("tail", (0, -bh + 2, bl / 2 + 1.5), (20, 0, 0))
    tail.cube("tail", -1, 0, -1, 2, 3, 2)
    r.look = [("head", 0.6, 0.55), ("neck", 0.3, 0.25)]
    r.moves = {}
    quad_clips(r, rung, p, heavy=1.0)
    r.pack()
    return r


def paint_sheep(r, rung, species, fam):
    p = SHEEP[rung]
    P = R.Painter(r, f"forms|{r.id}|{species}|{fam}")
    coat = R.Painter(r, f"forms|{r.id}|{species}|{fam}|coat")
    rng = random.Random(R._h(r.id, species, fam))
    # the vanilla sheep's pale face and legs at C; a ram's dark face by S
    skin = B.tint_ramp(B.toward(["#6b574b", "#93796a", "#b59a8a", "#cdb4a4", "#e0cdbf", "#efe2d8"],
                                ["#3a2f29", "#5e4c41", "#86705f", "#ab947f", "#cbb7a1", "#e2d2be"], rung), fam)
    face = B.tint_ramp(B.toward(["#5e4a3f", "#8d7262", "#b9998a", "#d2b8a8", "#e6d4c6"],
                                ["#100c0b", "#211a17", "#352b26", "#4b3e36", "#62524a"], rung), fam)
    # the wool in greys (the game tints it to the sheep's colour): near white and soft at C, curled and matted by S
    k = B.drift(rung)
    wool = R.Ramp(*B.toward(["#a9a6a1", "#c4c1bc", "#dad7d2", "#e9e7e3", "#f4f2ef", "#fdfcfa"],
                            ["#5a5754", "#85827d", "#aca8a2", "#cdc9c2", "#e6e2db", "#f7f4ef"], rung))
    curls = R.p_strokes(cell=2, length=2, dark=0.25 + 0.35 * k, light=0.5, density=0.5 + 0.3 * k, seed=rung + 9)   # tight curls
    clumps = R.p_blotch(rung * 13 + 2, scale=3, amount=0.2 + 0.4 * k, levels=3)
    horn_ramp = B.tint_ramp(["#231c14", "#4b3e2f", "#7c6b55", "#ab9a7f", "#d4c6ab"], fam)
    for cu in r.cubes:
        n = cu.name
        if n.startswith("wool"):
            coat.paint(cu, wool, 3.0, R.combine(curls, clumps), noise=0.6, rim=0.9)
        elif n.startswith(("horn", "crown")):
            P.paint(cu, horn_ramp, 2.6, lambda f, i, j: -0.9 if j % 2 == 1 else 0.0, grad=1.2, rim=0.6)   # ridged
        elif n.startswith("xtal"):
            P.paint(cu, B.ramp_to(B.RANK[rung]), 2.2, None, grad=1.6, rim=1.2, ao=1.0)
        elif n in ("head", "neck") or n.startswith(("ear", "shin")):
            P.paint(cu, face, 2.4, None, noise=0.4)
        else:   # the shorn body under the fleece, and the legs
            P.paint(cu, skin, 2.4, R.p_strokes(cell=3, length=2, dark=0.4, light=0.2, seed=4), noise=0.3)
    head = P.face("head", "north")
    w = head.w
    rank = R.hexc(B.RANK[rung])
    core = R.lerpc(rank, R.hexc("#ffffff"), 0.6)
    with P.eyes():
        for i in (0, w - 1):
            head.set(i, 2, R.hexc("#050404"))
    g = P.face("head", "north", glow=True)
    for i in (0, w - 1):
        g.set(i, 2, core[:3] + (255,))
    for i in range(1, w - 1):
        head.set(i, head.h - 2, face.at(1.2) if i not in (w // 2 - 1, w // 2) else face.at(0.3))   # the mouth line
    B.weathering(P, r, fam, rng, ["head"], 1)
    H.crystal_glow(P, r, rung)
    COATS[(r.id, species, fam)] = {"wool": coat.skin}
    return P.skin, P.glow, P.blink()


# ---- the chicken: a fighting cock, then a cockatrice's stance -------------------------------------------------------------

CH = {
    2: dict(body=(6, 6, 8), hgt=6, neck=3, head=(4, 5, 4), comb=2, sickles=2, spur=1, upright=12, crystal=0, crest=False),
    3: dict(body=(7, 7, 9), hgt=7, neck=4, head=(4, 5, 4), comb=3, sickles=3, spur=2, upright=18, crystal=0, crest=False),
    4: dict(body=(8, 8, 11), hgt=10, neck=6, head=(5, 6, 5), comb=3, sickles=3, spur=2, upright=30, crystal=2, crest=False),
    5: dict(body=(9, 9, 12), hgt=12, neck=7, head=(5, 6, 5), comb=3, sickles=4, spur=3, upright=34, crystal=2, crest=True),
}


def chicken(rung):
    p = CH[rung]
    r = R.Rig(f"form_chicken_{rung}", (128, 128))
    r.walk_stride, r.run_stride = 0.7 + 0.15 * (rung - 2), 1.6 + 0.25 * (rung - 2)
    r.run_speed = (0.2, 0.3)
    r.shadow = 0.35 + 0.1 * (rung - 2)
    bw, bh, bl = p["body"]
    hgt = p["hgt"]
    base = r.bone("base", (0, 24, 0))
    torso = base.bone("torso", (0, -hgt, 0), (-p["upright"], 0, 0))   # the breast up: more upright at every rank
    torso.cube("torso", -bw / 2, -bh, -bl / 2, bw, bh, bl)
    g = torso.bone("geart", (0, 0, 0))
    for side, nm in ((-1, "r"), (1, "l")):
        leg = base.bone("leg_" + nm, (side * 1.5, -hgt, 0.5))
        up = hgt // 2
        leg.cube("leg_" + nm, -1, 0, -1, 2, up, 2, mirror=side > 0)
        shin = leg.bone("shin_" + nm, (0, up, 0))
        shin.cube("shin_" + nm, -0.5, 0, -0.5, 1, hgt - up, 1, mirror=side > 0)
        shin.cube("foot_" + nm, -1.5, hgt - up - 1, -2.5, 3, 1, 3, mirror=side > 0)
        B.shard(shin, "spur_" + nm, (0, hgt - up - 2.5, 0.5), (-100, 0, 0), [(p["spur"], 1, 0)])
        wing = torso.bone("wing_" + nm, (side * bw / 2, -bh + 1, -bl / 2 + 1))
        wing.cube("wing_" + nm, -0.5 if side < 0 else -0.5, 0, 0, 1, bh - 2, bl - 1, mirror=side > 0)
    neck = torso.bone("neck", (0, -bh + 1, -bl / 2 + 1.5), (p["upright"] - 6, 0, 0))
    neck.cube("neck", -1.5, -p["neck"], -1.5, 3, p["neck"], 3)
    hw, hh, hd = p["head"]
    head = neck.bone("head", (0, -p["neck"], 0), (6, 0, 0))
    head.cube("head", -hw / 2, -hh, -hd / 2 - 0.5, hw, hh, hd)
    gh = head.bone("gearh", (0, 0, 0))
    gh.cube("beak", -1, -hh + 2, -hd / 2 - 2.5, 2, 2, 2)
    gh.cube("wattle", -1, -hh + 4, -hd / 2 - 1.5, 2, 2 + (rung >= 3), 1)
    for k in range(p["comb"]):   # the comb: blades in a row over the crown
        gh.cube(f"comb{k}", -0.5, -hh - 1 - (k % 2), -hd / 2 + k * 1.3 - 0.5, 1, 1 + (k % 2), 1)
    B.eye_bones(head, (hw / 2, -hw / 2), -hh + 1.5, -0.5)
    if p["crest"]:
        for k in range(3):
            B.shard(gh, f"crown{k}", (-1 + k, -hh - 1, 0.5), (-30, 0, (k - 1) * 18), [(3, 1, 0), (2, 1, -20)])
    tail = torso.bone("tail", (0, -bh + 1, bl / 2 - 1), (p["upright"] - 30, 0, 0))
    for k in range(p["sickles"]):   # the sickle feathers, long and arched back over
        x = (k - (p["sickles"] - 1) / 2) * 1.5
        B.shard(tail, f"sickle{k}", (x, 0, 0), (-10 - k * 10, 0, x * 10), [(4 + rung, 2, 0), (3 + rung // 2, 1, -36)])
    if p["crystal"]:
        for k, (x, z) in enumerate(((-1.5, 0), (1.5, 2))[:p["crystal"]]):
            B.shard(g, f"xtal{k}", (x, -bh, z), (-30, 0, x * 12), [(3, 2, 0), (2, 1, -14)])
    r.look = [("head", 0.8, 0.6), ("neck", 0.2, 0.2)]
    r.moves = {}
    chicken_clips(r, rung, p)
    r.pack()
    return r


def chicken_clips(r, rung, p):
    # ---- idle: the head moves in jerks and holds (a bird's head stays still while its body shifts), a scratch now and then
    idle = r.clip("idle", 4.0, loop=True)
    # each look: a quick turn (0.08 s), then held
    looks = [(0.5, 0, 20), (1.2, -10, 20), (1.7, 0, -14), (2.5, 14, -14), (3.0, 0, 8), (3.7, -6, 0)]
    keys, last = [(0.0, 0, 0, 0, "lin")], (0, 0)
    for t, px, yw in looks:
        keys += [(t, last[0], last[1], 0, "lin"), (t + 0.08, px, yw, 0, "lin")]
        last = (px, yw)
    keys += [(3.92, last[0], last[1], 0, "lin"), (4.0, 0, 0, 0, "lin")]
    idle.rot("head", keys)
    cycle(idle, "torso", "rot", lambda q: (1.5 * s(q), 0, 1 * s(q, 0.3)), n=8)
    cycle(idle, "tail", "rot", lambda q: (3 * s(q, 0.2), 4 * s(2 * q), 0), n=8)
    idle.rot("leg_r", [(0.0, 0, 0, 0), (2.8, 0, 0, 0), (2.95, -30, 0, 0), (3.1, 6, 0, 0), (3.25, -24, 0, 0), (3.45, 0, 0, 0), (4.0, 0, 0, 0)])
    # ---- walk: the strut, the head thrust forward and back with each step
    walk = r.clip("walk", 0.7, loop=True)
    cycle(walk, "leg_r", "rot", lambda q: (34 * s(q), 0, 0), n=12)
    cycle(walk, "leg_l", "rot", lambda q: (-34 * s(q), 0, 0), n=12)
    cycle(walk, "shin_r", "rot", lambda q: (30 * max(0.0, -c(q)), 0, 0), n=12)
    cycle(walk, "shin_l", "rot", lambda q: (30 * max(0.0, c(q)), 0, 0), n=12)
    cycle(walk, "torso", "rot", lambda q: (2 * s(2 * q), 0, 4 * s(q)), n=12)
    cycle(walk, "torso", "pos", lambda q: (0.3 * s(q), -0.5 * abs(s(q)), 0), n=12)
    cycle(walk, "neck", "pos", lambda q: (0, 0, -1.2 * s(2 * q, 0.1)), n=12)
    cycle(walk, "tail", "rot", lambda q: (4 * s(2 * q), 0, 4 * s(q)), n=12)
    run = r.clip("run", 0.45, loop=True)
    cycle(run, "leg_r", "rot", lambda q: (50 * s(q), 0, 0), n=12)
    cycle(run, "leg_l", "rot", lambda q: (-50 * s(q), 0, 0), n=12)
    cycle(run, "shin_r", "rot", lambda q: (40 * max(0.0, -c(q)), 0, 0), n=12)
    cycle(run, "shin_l", "rot", lambda q: (40 * max(0.0, c(q)), 0, 0), n=12)
    cycle(run, "torso", "rot", lambda q: (14, 0, 3 * s(q)), n=12)
    cycle(run, "wing_r", "rot", lambda q: (0, 0, 30 + 20 * s(2 * q)), n=12)
    cycle(run, "wing_l", "rot", lambda q: (0, 0, -30 - 20 * s(2 * q)), n=12)
    cycle(run, "neck", "rot", lambda q: (20, 0, 0), n=4)
    # ---- air: it beats its wings while it falls (vanilla's flap)
    air = r.clip("air", 0.3, loop=True)
    cycle(air, "wing_r", "rot", lambda q: (0, 0, 50 + 40 * s(q)), n=8)
    cycle(air, "wing_l", "rot", lambda q: (0, 0, -50 - 40 * s(q)), n=8)
    cycle(air, "leg_r", "rot", lambda q: (20, 0, 0), n=4)
    cycle(air, "leg_l", "rot", lambda q: (20, 0, 0), n=4)
    # ---- angry: the hackles: neck feathers up, wings half out and down, low and forward, ready to fly at you
    angry = r.clip("angry", 1.5, loop=True)
    cycle(angry, "torso", "rot", lambda q: (14 + 2 * s(2 * q), 0, 0), n=8)
    cycle(angry, "neck", "rot", lambda q: (24, 0, 0), n=4)
    cycle(angry, "head", "rot", lambda q: (-24 + 3 * s(3 * q), 6 * s(q), 0), n=8)
    cycle(angry, "neck", "scale", lambda q: (1.25, 1.0, 1.25), n=4)
    cycle(angry, "wing_r", "rot", lambda q: (10, 0, 24 + 4 * s(2 * q)), n=8)
    cycle(angry, "wing_l", "rot", lambda q: (10, 0, -24 - 4 * s(2 * q)), n=8)
    cycle(angry, "tail", "rot", lambda q: (-14, 0, 0), n=4)
    # ---- the blow: a peck; from B a leap and a raking kick with the spurs
    bite = r.clip("bite", 1.0 if rung >= 3 else 0.5)
    bite.rot("neck", [(0.0, 0, 0, 0), (0.05, -14, 0, 0), (0.12, 30, 0, 0), (0.3, 0, 0, 0)])
    bite.rot("head", [(0.0, 0, 0, 0), (0.12, 16, 0, 0), (0.3, 0, 0, 0)])
    if rung >= 3:
        bite.pos("base", [(0.0, 0, 0, 0), (0.45, 0, 0, 0), (0.55, 0, -4, -1), (0.75, 0, -2, -1.5), (0.9, 0, 0, 0), (1.0, 0, 0, 0)])
        bite.rot("leg_r", [(0.0, 0, 0, 0), (0.5, 20, 0, 0), (0.62, -70, 0, 0), (0.8, -20, 0, 0), (1.0, 0, 0, 0)])
        bite.rot("leg_l", [(0.0, 0, 0, 0), (0.5, 20, 0, 0), (0.64, -64, 0, 0), (0.82, -20, 0, 0), (1.0, 0, 0, 0)])
        bite.rot("wing_r", [(0.0, 0, 0, 0), (0.5, 0, 0, 70), (0.7, 0, 0, 20), (1.0, 0, 0, 0)])
        bite.rot("wing_l", [(0.0, 0, 0, 0), (0.5, 0, 0, -70), (0.7, 0, 0, -20), (1.0, 0, 0, 0)])
    hurt = r.clip("hurt", 0.4)
    hurt.rot("torso", [(0.0, 0, 0, 0), (0.06, -12, 0, 8), (0.4, 0, 0, 0)])
    hurt.rot("wing_r", [(0.0, 0, 0, 0), (0.06, 0, 0, 60), (0.4, 0, 0, 0)])
    hurt.rot("wing_l", [(0.0, 0, 0, 0), (0.06, 0, 0, -60), (0.4, 0, 0, 0)])
    hurt.rot("neck", [(0.0, 0, 0, 0), (0.06, -20, 0, 0), (0.4, 0, 0, 0)])
    # ---- death: a last flap, then it keels over, the legs out stiff
    death = r.clip("death", 0.95)
    death.rot("wing_r", [(0.0, 0, 0, 0), (0.15, 0, 0, 70), (0.3, 0, 0, 20), (0.95, 0, 0, 10)])
    death.rot("wing_l", [(0.0, 0, 0, 0), (0.15, 0, 0, -70), (0.3, 0, 0, -20), (0.95, 0, 0, -10)])
    death.rot("base", [(0.0, 0, 0, 0), (0.35, 0, 0, 10), (0.65, 0, 0, 90, "lin"), (0.75, 0, 0, 84), (0.95, 0, 0, 86)])
    death.pos("base", [(0.0, 0, 0, 0), (0.65, 0, -p["body"][0] / 2, 0), (0.95, 0, -p["body"][0] / 2, 0)])
    death.rot("leg_r", [(0.0, 0, 0, 0), (0.6, -20, 0, 0), (0.95, -30, 0, 0)])
    death.rot("leg_l", [(0.0, 0, 0, 0), (0.6, -10, 0, 0), (0.95, -24, 0, 0)])
    death.rot("neck", [(0.0, 0, 0, 0), (0.6, 30, 0, 0), (0.95, 40, 0, 10)])
    # ---- roar (the screech, an alpha's call): it stands tall, wings spread wide, the neck stretched up
    roar = r.clip("roar", 1.6)
    roar.rot("torso", [(0.0, 0, 0, 0), (0.3, -14, 0, 0), (1.2, -12, 0, 0), (1.6, 0, 0, 0)])
    roar.rot("neck", [(0.0, 0, 0, 0), (0.3, -24, 0, 0), (1.2, -20, 0, 0), (1.6, 0, 0, 0)])
    roar.rot("head", [(0.0, 0, 0, 0), (0.3, -20, 0, 0), (0.7, -26, 0, 0), (1.6, 0, 0, 0)])
    for nm, sd in (("r", 1), ("l", -1)):
        roar.rot("wing_" + nm, [(0.0, 0, 0, 0), (0.3, 0, 0, sd * 80), (0.5, 0, 0, sd * 60), (0.7, 0, 0, sd * 84), (1.2, 0, 0, sd * 76), (1.6, 0, 0, 0)])
    # crow (the rally, C): chest out, neck up, beak to the sky
    w = r.clip("rally_windup", 1.0)
    w.rot("torso", [(0.0, 0, 0, 0), (1.0, -10, 0, 0)])
    w.rot("neck", [(0.0, 0, 0, 0), (1.0, -20, 0, 0)])
    w.rot("wing_r", [(0.0, 0, 0, 0), (1.0, 0, 0, 20)])
    w.rot("wing_l", [(0.0, 0, 0, 0), (1.0, 0, 0, -20)])
    st = r.clip("rally_strike", 1.0)
    st.rot("torso", [(0.0, -10, 0, 0), (0.8, -10, 0, 0), (1.0, 0, 0, 0)])
    st.rot("neck", [(0.0, -20, 0, 0), (0.1, -34, 0, 0), (0.8, -30, 0, 0), (1.0, 0, 0, 0)])
    st.rot("head", [(0.0, 0, 0, 0), (0.1, -30, 0, 0), (0.8, -26, 0, 0), (1.0, 0, 0, 0)])
    st.rot("wing_r", [(0.0, 0, 0, 20), (0.1, 0, 0, 50), (0.3, 0, 0, 20), (0.5, 0, 0, 50), (1.0, 0, 0, 0)])
    st.rot("wing_l", [(0.0, 0, 0, -20), (0.1, 0, 0, -50), (0.3, 0, 0, -20), (0.5, 0, 0, -50), (1.0, 0, 0, 0)])
    # stomp (B's burst): it leaps with a beat of the wings and comes down hard
    w = r.clip("stomp_windup", 1.0)
    w.pos("base", [(0.0, 0, 0, 0), (1.0, 0, 1.0, 0)])
    w.rot("leg_r", [(0.0, 0, 0, 0), (1.0, -20, 0, 0)])
    w.rot("leg_l", [(0.0, 0, 0, 0), (1.0, -20, 0, 0)])
    w.rot("shin_r", [(0.0, 0, 0, 0), (1.0, 34, 0, 0)])
    w.rot("shin_l", [(0.0, 0, 0, 0), (1.0, 34, 0, 0)])
    w.rot("wing_r", [(0.0, 0, 0, 0), (1.0, 0, 0, 60)])
    w.rot("wing_l", [(0.0, 0, 0, 0), (1.0, 0, 0, -60)])
    st = r.clip("stomp_strike", 0.6)
    st.pos("base", [(0.0, 0, 1.0, 0), (0.12, 0, -5, 0), (0.3, 0, 0.6, 0), (0.6, 0, 0, 0)])
    st.rot("wing_r", [(0.0, 0, 0, 60), (0.12, 0, 0, 90), (0.3, 0, 0, 10), (0.6, 0, 0, 0)])
    st.rot("wing_l", [(0.0, 0, 0, -60), (0.12, 0, 0, -90), (0.3, 0, 0, -10), (0.6, 0, 0, 0)])
    st.rot("leg_r", [(0.0, -20, 0, 0), (0.12, 10, 0, 0), (0.6, 0, 0, 0)])
    st.rot("leg_l", [(0.0, -20, 0, 0), (0.12, 10, 0, 0), (0.6, 0, 0, 0)])
    st.rot("shin_r", [(0.0, 34, 0, 0), (0.3, 20, 0, 0), (0.6, 0, 0, 0)])
    st.rot("shin_l", [(0.0, 34, 0, 0), (0.3, 20, 0, 0), (0.6, 0, 0, 0)])
    # lunge (A's flap-jump): it crouches with wings raised, then springs feet first, spurs out
    w = r.clip("lunge_windup", 1.0)
    w.pos("base", [(0.0, 0, 0, 0), (1.0, 0, 1.5, 0.5)])
    w.rot("torso", [(0.0, 0, 0, 0), (1.0, 10, 0, 0)])
    w.rot("wing_r", [(0.0, 0, 0, 0), (1.0, -20, 0, 80)])
    w.rot("wing_l", [(0.0, 0, 0, 0), (1.0, -20, 0, -80)])
    w.rot("shin_r", [(0.0, 0, 0, 0), (1.0, 40, 0, 0)])
    w.rot("shin_l", [(0.0, 0, 0, 0), (1.0, 40, 0, 0)])
    st = r.clip("lunge_strike", 0.7)
    st.rot("torso", [(0.0, 10, 0, 0), (0.08, -30, 0, 0), (0.45, -20, 0, 0), (0.7, 0, 0, 0)])
    st.rot("leg_r", [(0.0, 0, 0, 0), (0.08, -70, 0, 0), (0.45, -50, 0, 0), (0.7, 0, 0, 0)])
    st.rot("leg_l", [(0.0, 0, 0, 0), (0.1, -64, 0, 0), (0.45, -46, 0, 0), (0.7, 0, 0, 0)])
    st.rot("wing_r", [(0.0, -20, 0, 80), (0.1, 0, 0, 30), (0.2, 0, 0, 80), (0.35, 0, 0, 30), (0.7, 0, 0, 0)])
    st.rot("wing_l", [(0.0, -20, 0, -80), (0.1, 0, 0, -30), (0.2, 0, 0, -80), (0.35, 0, 0, -30), (0.7, 0, 0, 0)])
    st.pos("base", [(0.0, 0, 1.5, 0.5), (0.08, 0, -3, -2), (0.45, 0, -1, -1), (0.7, 0, 0, 0)])


FEATHER = {
    "chicken": ["#6d675c", "#a29b8e", "#c9c2b5", "#e3ddd1", "#f4efe6", "#fffcf6"],
}
HACKLE = ["#5c1f0c", "#8e3a15", "#c1611f", "#e08a2c", "#f0b14a"]   # a cock's red-gold hackles and saddle
SICKLE = ["#0b0f12", "#132026", "#1b3640", "#25505c", "#33707c"]   # the dark green sheen of the sickles


def paint_chicken(r, rung, species, fam):
    p = CH[rung]
    P = R.Painter(r, f"forms|{r.id}|{species}|{fam}")
    rng = random.Random(R._h(r.id, species, fam))
    k = (rung - 2) / 3.0
    white = B.tint_ramp(FEATHER["chicken"], fam)
    # a white hen at C: the cock's red-gold hackles and dark sickles come in a rank at a time (the wing bows two ranks later)
    hackle = B.tint_ramp(B.toward(FEATHER["chicken"], HACKLE, rung), fam)
    bows = B.tint_ramp(B.toward(FEATHER["chicken"], HACKLE, rung, lag=2), fam)
    sickle = B.tint_ramp(B.toward(FEATHER["chicken"], SICKLE, rung), fam)
    feathers = R.p_feathers(fw=3, fh=2, depth=0.8)
    comb = R.Ramp("#3d0806", "#7a120d", "#b31d16", "#de3326", "#f2604e")
    leg = B.tint_ramp(["#4a3010", "#7d5419", "#b07c24", "#d9a73a", "#f0cb63"], fam)
    for cu in r.cubes:
        n = cu.name
        if n.startswith(("comb", "wattle")):
            P.paint(cu, comb, 2.8, None, rim=0.8)
        elif n == "beak":
            P.paint(cu, leg, 3.0, None, grad=1.4)
        elif n.startswith(("leg", "shin", "foot")):
            # bare scaled legs (more so in the cockatrice stance)
            P.paint(cu, leg, 2.4 - k, R.p_scales(w=2, h=1, depth=0.6 + 0.4 * k))
        elif n.startswith("spur"):
            P.paint(cu, B.tint_ramp(H.BONES["skeleton"], fam), 2.4, None, grad=1.4)
        elif n.startswith("sickle"):
            P.paint(cu, sickle, 2.4, lambda f, i, j: 0.9 if j == 0 or i == 0 else 0.0, grad=1.4)
        elif n.startswith(("xtal", "crown")):
            P.paint(cu, B.ramp_to(B.RANK[rung]), 2.2, None, grad=1.6, rim=1.2, ao=1.0)
        elif n in ("neck", "head") or n.startswith("wing"):
            # the cock's hackles and wing bows go red-gold from C, more at each rank
            P.paint(cu, hackle if n == "neck" else bows if n.startswith("wing") else white, 2.7, feathers, noise=0.4)
        else:
            P.paint(cu, white, 2.8 - 0.5 * k, feathers, noise=0.4)
    head = P.face("head", "north")
    rank = R.hexc(B.RANK[rung])
    core = R.lerpc(rank, R.hexc("#ffffff"), 0.6)
    for side in ("east", "west"):   # a bird's eyes are on the sides of its head
        f = P.face("head", side)
        with P.eyes():
            f.set(f.w // 2, 1, R.hexc("#050403"))
        P.face("head", side, glow=True).set(f.w // 2, 1, core[:3] + (255,))
    with P.eyes():
        head.set(0, 1, R.hexc("#050403"))
        head.set(head.w - 1, 1, R.hexc("#050403"))
    g = P.face("head", "north", glow=True)
    g.set(0, 1, rank[:3] + (200,))
    g.set(head.w - 1, 1, rank[:3] + (200,))
    B.weathering(P, r, fam, rng, ["torso"], 1)
    H.crystal_glow(P, r, rung)
    return P.skin, P.glow, P.blink()
