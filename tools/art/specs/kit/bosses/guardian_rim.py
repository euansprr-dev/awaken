"""The Rootmother (SPEC §35.11, docs/bosses/ROLLOUT.md): the Rim's Guardian, a Crawler. The Long-Legs, picked from her
silhouette round 2026-10-29: "The roots under the Rim are hers."

A harvestman taller than a house. Her body is a small knot of old root, a block across, capped with moss, held high in
the air on eight long thin legs of root bark: each bows up from her body to a mossy knee four blocks off the floor and
comes down to a root-claw three blocks out from her, so her whole stance is a tent you can walk into. On top of her body
stands a little turret with an eye on each side; under its front she has two root fangs and a pair of feelers that
never stop moving. Under her body hangs her brood, a cluster of eggs in a sac of silk glowing green-gold, and three long
streamers of moss and silk sway down from her to the height of your knees.

Moves (SPEC §35.11): she rears a front leg high and stabs it down onto you, then the other (LIGHT); the Scythe (a long
leg from her side lifted high and swung round low across the floor in front of her: HEAVY); a back leg stabbing back at
whoever is behind her; Moss Web (she tips back, her front legs raised, and flings a web from her feelers); Rootfall
(she sinks right down between her legs, holds, and springs, landing body first on the floor where you are: HEAVY); her
Brood (she lowers her body until the sac touches the floor, and it splits); and Root Lances (the signature: she rears
up on her back legs with the four front ones raised over her like spears, and drives them all into the floor). She
bobs when she stands, the way a harvestman does. She dies with her legs giving one after another; her body drops to the
floor and her legs fold in over it.
"""
import math

from kit import core, ik, parts
from kit.core import DEG, Move, keyed, stage_clips
import fauna_rig as R
from fauna_rig import Ramp, hexc, SIDES

ID = "guardian_rim"
SCALE = 2.4  # the hitbox on the def's SPIDER model at size 0.85 x 1.45: 2.0 x 3.5 blocks, round her body and under it

# The Long-Legs (docs/bosses/ROLLOUT.md, B). Her body's middle 2.75 blocks up, its top at 3.3 (the eye turret to 3.6);
# the knees at about 4.2 blocks, the claws 3.1 blocks out from her middle. A player walks in under her body, which is
# where the brood hangs. Value story: the eggs and the eyes glow brightest, then the moss on her cap and her knees, then
# the root bark of her body and legs, darkest at the claws.
BODY = 44.0  # the body's pivot, its middle (pixels above the floor)
THIGH, SHIN = 38, 70
SIDE = (1, 1, 1, 1, -1, -1, -1, -1)  # legs 0-3 on her left (+x), 4-7 on her right, front to back
IDX = (0, 1, 2, 3, 0, 1, 2, 3)
ANG = (58.0, 22.0, -18.0, -55.0)  # each leg's way out, degrees toward the front from straight out to her side
HIPZ = (-5.5, -2.0, 2.0, 5.5)
REACH = 50.0  # each claw at rest, out from her middle


def _dir(k):
    a = ANG[IDX[k]] * DEG
    return (SIDE[k] * math.cos(a), -math.sin(a))  # (x, z), front -z


def _hip_yaw(k):
    """Each hip turned to face down its leg, so the thigh's turn in it stays far from the Euler lock at y = 90 that a
    leg straight out to her side would sit on (its keys would spin x against z there)."""
    a = ANG[IDX[k]] * DEG
    return math.degrees(math.atan2(-SIDE[k] * math.cos(a), math.sin(a)))


REST_FOOT = [(_dir(k)[0] * REACH, 0.5, _dir(k)[1] * REACH) for k in range(8)]
POLE = [(_dir(k)[0] * 0.45, -1.0, _dir(k)[1] * 0.45) for k in range(8)]  # the knees ride up and out


def _bone(parent, name, x, up, z, rot=(0, 0, 0)):
    return parent.bone(name, (x, -up, z), rot)


def _box(bone, name, cx, y0, cz, w, h, d, mirror=False):
    return bone.cube(name, cx - w / 2, -(y0 + h), cz - d / 2, w, h, d, mirror=mirror)


def body():
    r = R.Rig(ID, (512, 512))
    base = r.bone("base", (0, 24, 0))
    bd = _bone(base, "body", 0, BODY, 0)
    # the knot of root: a core, its mossy cap and a bark ridge, knobs at its sides, a rump behind, a pale underside
    _box(bd, "core", 0, -5, 0, 16, 10, 18)
    _box(bd, "cap", 0, 5, 0.5, 14, 3, 15)
    _box(bd, "hump", 0, 8, 3, 10, 2, 9)
    _box(bd, "ridge", 0, 10, 4, 4, 2, 6)
    _box(bd, "rump", 0, -4, 11.5, 12, 8, 7)
    _box(bd, "rumpcap", 0, 4, 11.5, 10, 2, 6)
    for s, nm in ((1, "l"), (-1, "r")):
        _box(bd, "knob_" + nm, s * 8.5, -2.5, 1, 3, 5, 7)
    _box(bd, "under", 0, -7, 1, 12, 2, 14)
    # the eye turret on top, an eye on each side and two small ones to the front
    _box(bd, "turret", 0, 8, -4.5, 5, 5, 5)
    _box(bd, "brow", 0, 13, -4.5, 6, 1, 6)
    # two root fangs under her front, and her feelers either side of them
    for s, nm in ((1, "l"), (-1, "r")):
        fg = _bone(bd, "fang_" + nm, s * 2.0, -4.5, -8.5)
        fg.cube("fang_" + nm, -1, 0, -1, 2, 4, 2)
        fg.cube("fangt_" + nm, -0.5, 4, -1.5, 1, 2, 1)
        pp = _bone(bd, "palp_" + nm, s * 4.2, -3, -8.5, (-55, 0, -10 * s))
        pp.cube("palp_" + nm, -1, 0, -1, 2, 6, 2)
        pb = pp.bone("palpb_" + nm, (0, 6, 0), (75, 0, 0))
        pb.cube("palpb_" + nm, -1, 0, -1, 2, 4, 2)
        pb.cube("palpt_" + nm, -0.5, 4, -0.5, 1, 2, 1)
    # her brood: a sac of silk under her body and the eggs in it, hanging on its own bone
    br = _bone(bd, "brood", 0, -7, 1.5)
    br.cube("sac", -4, 0, -4, 8, 3, 8)
    for k, (x, y, z, e) in enumerate(((-2.5, 2, -2, 5), (2, 2.5, -1.5, 4), (-0.5, 5, 2, 5), (2.5, 6, 2.5, 4), (-2.5, 7, 0, 4), (0.5, 9.5, -0.5, 3))):
        br.cube(f"egg{k}", x - e / 2, y, z - e / 2, e, e, e)
    # three streamers of moss and silk, swaying down from her underside
    for nm, x, z in (("sa", -4, -3), ("sb", 4, 3), ("sc", 1, 6)):
        parts.chain(bd, nm, (x, 7, z), 3, link=(1, 7, 1))
    # eight legs of root bark: the coxa at the hip, the long femur up to a mossy knee, the long shin down to a root-claw
    for k in range(8):
        s, i = SIDE[k], IDX[k]
        hp = _bone(bd, f"hip{k}", s * 7.5, -0.5, HIPZ[i] * 1.15, (0, _hip_yaw(k), 0))
        th = hp.bone(f"thigh{k}", (0, 0, 0))
        th.cube(f"coxa{k}", -2, -1, -2, 4, 6, 4)
        th.cube(f"thigh{k}", -1.5, 5, -1.5, 3, THIGH - 7, 3)
        th.cube(f"thighk{k}", -2, THIGH - 4, -2, 4, 4, 4)
        sh = th.bone(f"shin{k}", (0, THIGH, 0))
        sh.cube(f"knee{k}", -2.5, -2.5, -2.5, 5, 5, 5)
        sh.cube(f"shin{k}", -1.5, 2.5, -1.5, 3, 22, 3)
        sh.cube(f"shinb{k}", -1, 24.5, -1, 2, 26, 2)
        sh.cube(f"shinc{k}", -1, 50.5, -1, 2, SHIN - 51, 2)
        sh.cube(f"twig{k}", 0.5 * s, 34, -0.5, 2, 1, 1)
        ft = sh.bone(f"foot{k}", (0, SHIN, 0))
        ft.cube(f"claw{k}", -0.5, -1, -0.5, 1, 2, 1)
    for k in range(8):
        ik.rest_limb(r, f"thigh{k}", f"shin{k}", f"foot{k}", REST_FOOT[k], POLE[k])
    r.look = [("body", 0.15, 0.2), ("palp_l", 0.3, 0.3), ("palp_r", 0.3, 0.3)]
    r.shadow = 2.4
    r.size = 1.0
    # secondary parts (SPEC §35.10): the brood swings under her, the streamers sway, the feelers tremble
    r.springs = [("brood", 0.5, 1.4, 0.15)] + [(f"{nm}{j}", 0.5, 1.5 + 0.4 * j, 0.22) for nm in ("sa", "sb", "sc") for j in range(3)] + \
                [("palpb_l", 0.6, 2.2, 0.1), ("palpb_r", 0.6, 2.2, 0.1)]
    r.guard = [f"claw{k}" for k in range(8)] + [f"egg{k}" for k in range(6)]
    return r


# ---------------------------------------------------------------------------------------------------------------------
# Paint

BARK = Ramp("#0e0906", "#160e09", "#1f150d", "#2a1c11", "#362416", "#432d1b", "#523821", "#624428", "#745230")
MOSS = Ramp("#0f1a08", "#16240c", "#1e3010", "#283e15", "#334d1a", "#405e20", "#4f7028", "#608232", "#74963e")
SILK = Ramp("#5c6650", "#76806a", "#909a82", "#aab49a", "#c2cab2", "#d8dec8")
EGG = Ramp("#4a5a1e", "#64782a", "#80963a", "#9cb24c", "#b8cc62", "#d2e27e")
GLOW = ("#5a8a14", "#8ac22a", "#b4e450", "#dcff8a", "#f4ffd0")
STONE = Ramp("#2a2a24", "#36362e", "#44443a", "#525246", "#626254")


def paint(r, P_):
    """Root bark with a long grain down every leg, darker toward the claws; moss on her cap, her rump and her knees,
    lighter where the light falls; her underside pale and dusty; the eggs pale green, lit from inside; the eyes bright."""
    of = lambda *ps: parts.cubes_of(r, *ps)
    grain = R.p_strokes(cell=2, length=4, dark=0.8, light=0.4, density=0.6, seed=3, tops=False)
    for c in ["core", "rump", "ridge", "turret"] + of("knob_"):
        P_.paint(c, BARK, 5.4, grain, grad=1.0, rim=0.8, ao=0.9, noise=0.5, nscale=4)
    for c in ("cap", "rumpcap", "brow", "hump"):
        P_.paint(c, MOSS, 5.0, R.p_blotch(c, scale=3, amount=1.0), grad=0.8, rim=1.0, ao=0.6, noise=0.7, nscale=3)
    # moss creeping down the core's sides from the cap
    for c in ("core", "rump"):
        for fname in SIDES:
            f = P_.face(c, fname)
            for i in range(f.w):
                h = R._h(c, fname, i)
                for j in range(min(f.h, 1 + h % 4)):
                    f.set(i, j, MOSS.at(4.4 - 0.6 * j + (h % 3) * 0.3))
    P_.paint("under", STONE, 3.0, R.p_blotch("under", scale=3, amount=0.6), grad=0.4, rim=0.2, ao=0.4, noise=0.4)
    # the legs: bark grain, the thigh lighter toward its knee, the shin darkening down to the claw
    for c in of("coxa", "thigh"):
        P_.paint(c, BARK, 5.2 if not c.startswith("thighk") else 5.8, grain, grad=1.0, rim=0.7, ao=0.8, noise=0.4, nscale=4)
    for c in of("shin", "twig"):
        base = 5.6 if c.startswith("shin") and not c.startswith(("shinb", "shinc")) else 4.6 if c.startswith("shinb") else 3.6
        if c.startswith("twig"):
            base = 4.6
        P_.paint(c, BARK, base, grain, grad=1.4, rim=0.6, ao=0.6, noise=0.4, nscale=4)
    for c in of("claw"):
        P_.paint(c, BARK, 2.0, None, rim=0.4)
    for c in of("knee"):
        P_.paint(c, MOSS, 5.2, R.p_blotch(c, scale=2, amount=1.1), grad=0.6, rim=1.2, ao=0.5, noise=0.8, nscale=3)
    for c in of("thighk"):
        for fname in SIDES:
            f = P_.face(c, fname)
            for i, j in f.each():
                if j >= f.h - 2 and R._h(c, fname, i) % 2:
                    f.set(i, j, MOSS.at(4 + R._h(c, i, j) % 2))
    # the fangs and feelers: darker root, the tips black
    for c in of("fang", "palp"):
        P_.paint(c, BARK, 3.0 if not c.startswith(("fangt", "palpt")) else 1.2, None, grad=0.6, rim=0.6, ao=0.5, noise=0.3)
    # the streamers: moss and silk by turns
    for c in of("sa", "sc"):
        P_.paint(c, MOSS, 3.8, None, grad=0.6, rim=0.4, noise=0.5)
    for c in of("sb"):
        P_.paint(c, SILK, 3.4, None, grad=0.6, rim=0.4, noise=0.4)
    # the brood: a silk sac, the eggs pale green and lit from inside, brightest where the silk is thinnest
    P_.paint("sac", SILK, 3.6, R.p_blotch("sac", scale=2, amount=0.6), grad=0.6, rim=0.6, ao=0.4, noise=0.4)
    for c in of("egg"):
        P_.paint(c, EGG, 3.6, None, grad=0.8, rim=0.8, ao=0.2, noise=0.3)
        for fname in ("north", "south", "west", "east", "bottom"):
            f, g = P_.face(c, fname), P_.face(c, fname, glow=True)
            for i, j in g.each():
                core_ = 0 < i < f.w - 1 and 0 < j < f.h - 1
                g.set(i, j, hexc(GLOW[2] if core_ else GLOW[1], 150 if core_ else 90))
                if core_ and (i + j) % 3 == 0:
                    f.set(i, j, EGG.at(5))
    # the eyes: one on each side of the turret and two small ones to the front, green-gold
    with P_.eyes():
        for fname in ("west", "east"):
            f, g = P_.face("turret", fname), P_.face("turret", fname, glow=True)
            for i in (1, 2, 3):
                for j in (1, 2):
                    f.set(i, j, BARK.at(0))
                    g.set(i, j, hexc(GLOW[4] if (j == 1 and i == 2) else GLOW[3] if j == 1 else GLOW[2], 255))
        f, g = P_.face("turret", "north"), P_.face("turret", "north", glow=True)
        for i in (0, 4):
            for j in (2, 3):
                f.set(i, j, BARK.at(0))
                g.set(i, j, hexc(GLOW[3], 230))
    return P_.skin, P_.glow


# ---------------------------------------------------------------------------------------------------------------------
# Posing: the body set, each leg reaching its claw to where it stands, the brood hung plumb, the feelers set

def _swing_dir(fwd, side):
    """A hanging part's way down, swung fwd degrees toward the front and side degrees toward her left."""
    f, s = fwd * DEG, side * DEG
    return (math.cos(f) * math.sin(s), math.cos(f) * math.cos(s), -math.sin(f))


def _pose(r, lift=0.0, pitch=0.0, yaw=0.0, roll=0.0, shift=(0.0, 0.0), feet=None, poles=None, palps=(0.0, 0.0), spread=0.0, fangs=0.0,
          brood=(0.0, 0.0), tuck=0.0, extra=None):
    """lift (+ up), pitch (+ her front dipping), yaw (+ to her left), roll (+ her left side down) and shift (x, z) move
    her body; feet {leg: (x, height, z)} are where the claws stand (the rest stay where they stand at rest); palps (left,
    right) lift the feelers (+ up and forward), spread opens them; fangs opens the fangs; brood (fwd, left) swings the
    sac from plumb and tuck draws it up under her (pixels)."""
    p = {"body": {"rot": (pitch, yaw, roll), "pos": (shift[0], lift, shift[1])}}
    fr = core.frames(r, core.as_sample(p))
    feet = feet or {}
    poles = poles or {}
    for k in range(8):
        tgt = ik.model(feet.get(k, REST_FOOT[k]))
        p.update(ik.limb(r, fr, f"thigh{k}", f"shin{k}", f"foot{k}", tgt, poles.get(k, POLE[k])))
    p["brood"] = {"rot": ik.orient(r, fr, "brood", _swing_dir(*brood), (1, 0, 0)), "pos": (0, tuck, 0)}
    for s, nm, a in ((1, "l", palps[0]), (-1, "r", palps[1])):
        p["palp_" + nm] = (-a, 0, -s * spread)
        p["palpb_" + nm] = (-0.5 * a, 0, 0)
        p["fang_" + nm] = (-fangs, 0, -s * fangs * 0.4)
    if extra:
        p.update(extra)
    return p


def _foot(k, out=0.0, side=0.0, up=0.0, dz=0.0, dx=0.0):
    """Leg k's claw moved from where it stands at rest: out along its way out, side across it (toward her front for
    the left legs), up, and by (dx, dz)."""
    ox, oz = _dir(k)
    x, h, z = REST_FOOT[k]
    sx, sz = (oz, -ox) if SIDE[k] > 0 else (-oz, ox)  # across, toward the front
    return (x + ox * out + sx * side + dx, h + up, z + oz * out + sz * side + dz)


def _plant(r, clip, legs, where=None):
    """The legs that stand through a move keep their claws on the floor where they stand, every tick."""
    where = where or {}

    def fn(t, s, fr):
        out = {}
        for k in legs:
            tgt = ik.model(where.get(k, REST_FOOT[k]))
            out.update(ik.limb(r, fr, f"thigh{k}", f"shin{k}", f"foot{k}", tgt, POLE[k]))
        return out
    ik.retrack(r, clip, fn)


def _all(fn):
    return {k: fn(k) for k in range(8)}


def _with(a, b):
    return {**a, **b}


def _unwrap(keys):
    """Each key's turns written within half a turn of the key before (each axis by whole turns, the same pose), so a
    leg folded nearly about-face from its rest goes the short way between keys and never spins round."""
    out, last = [], {}
    for t, p in keys:
        q = {}
        for b, v in p.items():
            rot = v["rot"] if isinstance(v, dict) else v
            if b in last:
                rot = tuple(c + 360 * round((last[b][i] - c) / 360) for i, c in enumerate(rot))
            last[b] = rot
            q[b] = {**v, "rot": rot} if isinstance(v, dict) else rot
        out.append((t, q))
    return out


# ---------------------------------------------------------------------------------------------------------------------
# Clips

GROUPS = {0: 0.0, 5: 0.0, 2: 0.0, 7: 0.0, 4: 0.5, 1: 0.5, 6: 0.5, 3: 0.5}  # the alternating fours of a harvestman's walk


def _gait(u, stride_px, beta, lift):
    """The claws through a stride at u: down, each moves back as far as she travels, so it stays put on the floor; up,
    it lifts high and reaches forward again, each four by turns."""
    feet = {}
    st = stride_px * beta
    for k, ph in GROUPS.items():
        q = (u - ph + 0.02 * IDX[k]) % 1.0  # the fours not quite together: front to back, a ripple
        if q < beta:
            feet[k] = _foot(k, dz=-st / 2 + st * q / beta)
        else:
            f = (q - beta) / (1 - beta)
            feet[k] = _foot(k, up=lift * math.sin(math.pi * f) ** 0.8, dz=st / 2 - st * core.ease_io(f), out=2.0 * math.sin(math.pi * f))
    return feet


def _idle(r):
    """Standing in her tent of legs she bobs, the way a harvestman does, her body rocking a little on its legs; her
    feelers never stop; one of her long second legs lifts and feels the air, then the other; the brood sways."""
    L, n = 4.8, 48
    keys = []
    for k in range(n + 1):
        u = k / n
        w = 2 * math.pi * u
        feet = {}
        for leg, (a, b) in ((1, (0.12, 0.42)), (5, (0.58, 0.88))):  # a second leg lifts, feels, and sets down
            f = (u - a) / (b - a)
            if 0 < f < 1:
                e = math.sin(math.pi * f)
                feet[leg] = _foot(leg, up=22 * e, out=-8 * e, side=6 * math.sin(3 * math.pi * f) * e)
        pose = _pose(r, lift=1.2 * math.sin(2 * w) + 0.5 * math.sin(w), pitch=1.4 * math.sin(w + 0.6), roll=1.6 * math.sin(w + 1.7),
                     yaw=2.0 * math.sin(w), feet=feet, palps=(14 + 12 * math.sin(3 * w), 14 + 12 * math.sin(3 * w + 2.1)),
                     spread=6 + 4 * math.sin(2 * w + 1.0), fangs=3 + 3 * math.sin(4 * w), brood=(3 * math.sin(w - 1.2), 4 * math.sin(w + 0.4)))
        keys.append((L * u, pose))
    keyed(r, "idle", L, keys, loop=True)


def _locomotion(r):
    """Her walk: the alternating fours, each leg lifting high and reaching on as the others carry her; her body rides
    level and rocks toward the four that holds it; her feelers held forward. Running, she goes lower and faster."""
    for name, L, stride, beta, lift, lean, drop, bob in (("walk", 1.3, 1.6, 0.6, 12.0, 2.0, 0.0, 1.0), ("run", 0.74, 2.8, 0.5, 16.0, 6.0, 5.0, 1.8)):
        sp = stride * 16
        keys = []
        n = 26
        for k in range(n + 1):
            u = k / n
            s2 = math.sin(4 * math.pi * u)
            s = math.sin(2 * math.pi * u)
            pose = _pose(r, lift=-drop - bob * 0.5 * (1 + math.cos(4 * math.pi * u)), pitch=lean + 1.0 * s2, roll=2.2 * s, yaw=1.6 * s,
                         feet=_gait(u, sp, beta, lift), palps=(26 + 6 * s2, 26 - 6 * s2), spread=4, fangs=2,
                         brood=(-lean * 2 - 6 * math.sin(4 * math.pi * u - 0.9), 5 * math.sin(2 * math.pi * u - 0.7)))
            keys.append((L * u, pose))
        keyed(r, name, L, keys, loop=True)
    r.walk_stride, r.run_stride = 1.6, 2.8
    r.run_speed = (0.14, 0.3)
    for nm, sgn in (("turn_l", 1), ("turn_r", -1)):
        keys = []
        n = 18
        for k in range(n + 1):
            u = k / n
            feet = {}
            for j, ph in GROUPS.items():  # each four steps round the way she turns while the other holds
                q = (u - ph) % 1.0
                if q < 0.5:
                    f = q / 0.5
                    feet[j] = _foot(j, side=sgn * SIDE[j] * 8 * (core.ease_io(f) - 0.5), up=10 * math.sin(math.pi * f))
                else:
                    feet[j] = _foot(j, side=sgn * SIDE[j] * 8 * (0.5 - (q - 0.5) / 0.5))
            keys.append((0.9 * u, _pose(r, yaw=sgn * 2.0, roll=1.5 * math.sin(4 * math.pi * u), feet=feet, palps=(20, 20),
                                        brood=(0, -sgn * 6 * math.sin(2 * math.pi * u)))))
        keyed(r, nm, 0.9, keys, loop=True)


def _floor_lift(extra=0.0):
    """The lift that sets her underside on the floor."""
    return -(BODY - 7.5) + extra


def _sat(r, k_out=12.0):
    """Down on the floor between her legs, the knees standing high round her like a tent, the brood drawn up under her."""
    return _pose(r, lift=_floor_lift(), feet=_all(lambda k: _foot(k, out=k_out)), palps=(-10, -10), spread=2, tuck=4, brood=(-30, 0))


def _reactions(r):
    """Struck, her body jolts up on its legs and rocks back, the feelers flung; from the side it sways away. Her stance
    broken, her legs slide out and her body drops to the floor between them; she rises out of it by pulling them in."""
    hit = _pose(r, lift=3.5, pitch=-8, palps=(-20, -20), spread=14, fangs=10, brood=(14, 0))
    back = _pose(r, lift=-1.5, pitch=4, palps=(30, 30), spread=4, fangs=2, brood=(-8, 0))
    keyed(r, "hurt", 0.55, [(0, _pose(r)), (0.08, hit), (0.3, back), (0.55, _pose(r))])
    for nm, sgn in (("hurt_l", 1), ("hurt_r", -1)):  # struck from her left she sways away to her right
        h = _pose(r, lift=1.5, roll=-9 * sgn, yaw=-7 * sgn, shift=(-sgn * 3, 0), palps=(-10, -10), spread=12, fangs=8, brood=(0, 14 * sgn))
        b = _pose(r, lift=-0.5, roll=3 * sgn, yaw=2 * sgn, shift=(sgn * 0.8, 0), palps=(24, 24), brood=(0, -6 * sgn))
        keyed(r, nm, 0.6, [(0, _pose(r)), (0.08, h), (0.32, b), (0.6, _pose(r))])
    half = _pose(r, lift=-16, feet=_all(lambda k: _foot(k, out=6)), palps=(0, 0), spread=6, tuck=2, brood=(-10, 0))
    keyed(r, "kneel", 0.9, [(0, _pose(r)), (0.3, half), (0.62, _sat(r, 14)), (0.9, _sat(r))])
    push = _pose(r, lift=-20, feet=_all(lambda k: _foot(k, out=4)), palps=(10, 10), tuck=2, brood=(-12, 0))
    keyed(r, "rise", 1.1, [(0, _sat(r)), (0.4, push), (0.8, _pose(r, lift=2, palps=(20, 20))), (1.1, _pose(r))])
    _death(r)


def _curl(k, f):
    """A dead harvestman's leg: drawn in toward her body and up, f of the way."""
    x, h, z = REST_FOOT[k]
    tx, th, tz = x * 0.3, 16.0, z * 0.3
    return (x + (tx - x) * f, h + (th - h) * f, z + (tz - z) * f)


def _death(r):
    """Her last: she shudders up on her legs, her feelers flung up; her front legs give and she lurches down that way,
    the rest giving one after another, her body dropping to the floor; then her legs fold in over her, the knees high,
    one twitches, and she is still."""
    shake = _pose(r, lift=3, pitch=-6, roll=3, palps=(-30, -30), spread=18, fangs=12, brood=(10, -6))
    give0 = _pose(r, lift=-8, pitch=12, roll=6, feet={0: _foot(0, out=12, up=0), 4: _foot(4, out=8)}, palps=(-10, 0), spread=10, fangs=10, brood=(20, 6))
    give1 = _pose(r, lift=-22, pitch=8, roll=-4, feet=_all(lambda k: _foot(k, out=10 if IDX[k] < 2 else 5)), palps=(0, 10), spread=8, fangs=6, tuck=1,
                  brood=(12, 0))
    floor = _pose(r, lift=_floor_lift(0.5), pitch=3, roll=2, feet=_all(lambda k: _foot(k, out=16)), palps=(-6, -6), spread=4, fangs=4, tuck=4, brood=(-30, 4))
    bounce = _pose(r, lift=_floor_lift(2.0), pitch=1, roll=-1, feet=_all(lambda k: _foot(k, out=17)), palps=(4, 0), spread=6, tuck=4, brood=(-30, 0))
    hi = {k: (_dir(k)[0] * 0.2, -1.0, _dir(k)[1] * 0.2) for k in range(8)}
    fold = lambda f, tw=None: _pose(r, lift=_floor_lift(0.3), pitch=2, roll=1.5, feet={k: (_foot(k, out=18 * (1 - f)) if f < 0.01 else
                                                                                           _curl(k, f * (1.15 if tw == k else 1.0))) for k in range(8)},
                                    poles=hi, palps=(-30 * f, -30 * f), spread=0, fangs=0, tuck=4, brood=(-30, 0))
    keys = [(0, _pose(r)), (0.3, shake), (0.75, give0), (1.15, give1), (1.45, floor), (1.65, bounce), (1.9, floor), (2.6, fold(0.55)),
            (3.2, fold(0.85)), (3.55, fold(0.88, 2)), (3.8, fold(0.86)), (4.6, fold(0.9))]
    keyed(r, "death", 4.6, _unwrap(keys))


# ---------------------------------------------------------------------------------------------------------------------
# Moves

NO_LAG = {b + str(k): 0 for b in ("thigh", "shin", "foot") for k in range(8)}


def _lag(**kw):
    out = dict(NO_LAG)
    out.update(kw)
    return out


def _leg_hit(k):
    return [(f"shin{k}", (0, SHIN - 16, 0), 2.5), (f"foot{k}", (0, 0, 0), 2.5)]


def stab(r, k=0, mid="stab"):
    """A front leg rears up high over her, its claw drawn up and back as her body leans away from it; then it stabs
    down onto you two blocks before her and her body drops after it (LIGHT)."""
    s = SIDE[k]
    gather = _pose(r, lift=1, pitch=-3, roll=2.5 * s, yaw=2 * s, feet={k: _foot(k, up=18, out=-6)}, palps=(30, 30), spread=10, fangs=6)
    rear = _pose(r, lift=2.5, pitch=-7, roll=5 * s, yaw=5 * s, feet={k: (s * 30, 100, -42)}, poles={k: (s * 0.5, -1, 0.6)}, palps=(40, 40),
                 spread=16, fangs=12, brood=(-8, -4 * s))
    hit = _pose(r, lift=-4, pitch=8, roll=-3 * s, yaw=-4 * s, feet={k: (s * 7, 1.0, -38)}, poles={k: (s * 0.6, -1, -0.4)}, palps=(10, 10),
                spread=6, fangs=14, brood=(12, 3 * s))
    held = _pose(r, lift=-5, pitch=9, roll=-3 * s, yaw=-4 * s, feet={k: (s * 7, 0.5, -39)}, poles={k: (s * 0.6, -1, -0.4)}, palps=(6, 6),
                 fangs=10, brood=(6, 2 * s))
    sink = _pose(r, lift=-6, pitch=10, roll=-3.5 * s, yaw=-5 * s, feet={k: (s * 7, 0.5, -39.5)}, poles={k: (s * 0.6, -1, -0.4)}, palps=(2, 2),
                 fangs=6, brood=(0, 1 * s))  # her weight comes on down onto the claw
    back = _pose(r, lift=-1, pitch=3, feet={k: _foot(k, up=8, dz=-4)}, palps=(20, 20), brood=(-4, 0))
    return Move(mid, "LIGHT", 11, 4, 14, through=sink, track=8.0, rng=(0, 3.0), kinds=("combo", "near"), damage=1.0, knock=0.5, hit=_leg_hit(k),
                plant=(), stages={"windup": [(0.3, gather), (1.0, rear)], "active": [(0.5, hit), (1.0, held)], "recover": [(0.5, back)]}, lag=_lag())


def scythe(r):
    """The Scythe (HEAVY): her body swings away to her right and a long second leg lifts high and far out to her left,
    drawn back behind her; she holds; then it sweeps round low across the floor in front of her, knee high, her body
    swinging after it, and on out past her right."""
    k, s = 1, 1
    gather = _pose(r, lift=1.5, roll=-4, yaw=-9, feet={k: _foot(k, up=20, out=-4, side=-6)}, palps=(20, 30), spread=10, fangs=4, brood=(0, -7))
    up = _pose(r, lift=3, roll=-6, yaw=-16, pitch=-3, feet={k: (44, 34, 30)}, poles={k: (0.6, -1, 0.5)}, palps=(10, 36), spread=14, fangs=8,
               brood=(-4, -10))
    sweep_ = _pose(r, lift=-3, roll=4, yaw=10, pitch=4, feet={k: (6, 9, -50)}, poles={k: (0.3, -1, -0.6)}, palps=(30, 10), spread=6, fangs=12,
                   brood=(10, 10))
    out = _pose(r, lift=-4, roll=6, yaw=22, pitch=3, feet={k: (-34, 10, -38)}, poles={k: (-0.2, -1, -0.7)}, palps=(34, 6), fangs=10, brood=(4, 16))
    on = _pose(r, lift=-4.5, roll=7, yaw=26, pitch=2, feet={k: (-42, 12, -28)}, poles={k: (-0.4, -1, -0.6)}, palps=(30, 4), fangs=6, brood=(0, 18))
    home = _pose(r, lift=-1, roll=2, yaw=8, feet={k: _foot(k, up=10, dz=-8)}, palps=(24, 20), brood=(0, 6))
    return Move("scythe", "HEAVY", 16, 6, 20, through=on, title="The Scythe", hold=6, track=4.0, rng=(0, 4.0), kinds=("area", "near"), damage=1.2,
                knock=1.4, hit=_leg_hit(k), plant=(), stages={"windup": [(0.25, gather), (1.0, up)], "active": [(0.5, sweep_), (1.0, out)],
                                                             "recover": [(0.5, home)]}, lag=_lag())


def kick(r):
    """For whoever is behind her (LIGHT): a back leg lifts high behind her, her body tipping forward to look under
    itself, and stabs back down onto them."""
    k, s = 3, 1
    gather = _pose(r, lift=1, pitch=3, roll=2, feet={k: _foot(k, up=14, out=-4)}, palps=(10, 10), brood=(6, 0))
    up = _pose(r, lift=4, pitch=14, roll=7, yaw=-9, feet={k: (24, 96, 50)}, poles={k: (0.5, -1, -0.6)}, palps=(-24, -24), spread=18, fangs=10,
               brood=(22, 4))
    hit = _pose(r, lift=-3, pitch=-6, roll=-2, yaw=3, feet={k: (6, 1.0, 40)}, poles={k: (0.6, -1, 0.4)}, palps=(16, 16), fangs=8, brood=(-12, 0))
    held = _pose(r, lift=-3.5, pitch=-7, roll=-2, yaw=3, feet={k: (6, 0.5, 41)}, poles={k: (0.6, -1, 0.4)}, palps=(18, 18), fangs=6, brood=(-8, 0))
    back = _pose(r, lift=-0.5, pitch=-2, feet={k: _foot(k, up=8, dz=4)}, palps=(20, 20))
    return Move("kick", "LIGHT", 10, 4, 14, through=held, track=0.0, rng=(0, 3.0), kinds=("behind",), damage=0.9, knock=0.9, hit=_leg_hit(k),
                plant=(), stages={"windup": [(0.3, gather), (1.0, up)], "active": [(0.5, hit), (1.0, held)], "recover": [(0.5, back)]}, lag=_lag())


def web(r):
    """Moss Web: she tips back on her legs, her two front legs raised and spread and her feelers drawn back; then she
    pitches forward and flings her feelers at you, and the web flies from them."""
    fl = {0: (32, 92, -44), 4: (-32, 92, -44)}
    tip = _pose(r, lift=3, pitch=-14, feet=fl, poles={0: (0.6, -1, 0.5), 4: (-0.6, -1, 0.5)}, palps=(-40, -40), spread=22, fangs=14, brood=(-14, 0))
    gather = _pose(r, lift=1, pitch=-6, feet={0: _foot(0, up=20, out=-6), 4: _foot(4, up=20, out=-6)}, palps=(-10, -10), spread=12, fangs=6)
    fling = _pose(r, lift=-3, pitch=10, feet={0: (14, 26, -40), 4: (-14, 26, -40)}, poles={0: (0.6, -1, -0.3), 4: (-0.6, -1, -0.3)}, palps=(70, 70),
                  spread=4, fangs=16, brood=(16, 0))
    held = _pose(r, lift=-3.5, pitch=11, feet={0: (13, 20, -41), 4: (-13, 20, -41)}, poles={0: (0.6, -1, -0.3), 4: (-0.6, -1, -0.3)}, palps=(62, 62),
                 spread=2, fangs=12, brood=(10, 0))
    shake = _pose(r, lift=-2.5, pitch=8, feet={0: (15, 22, -40), 4: (-15, 22, -40)}, poles={0: (0.6, -1, -0.3), 4: (-0.6, -1, -0.3)}, palps=(50, 56),
                  spread=8, fangs=8, brood=(4, 3))
    return Move("web", "NONE", 12, 14, 16, through=shake, title="Moss Web", track=9.0, ability="Moss Web", rng=(4, 18), kinds=("ranged",), damage=0,
                knock=0, plant=(), stages={"windup": [(0.3, gather), (1.0, tip)], "active": [(0.35, fling), (0.7, held), (1.0, shake)],
                                           "recover": _back(r, shake)}, lag=_lag())


def _back(r, end):
    home = _pose(r)
    return [(0.45, _mix(end, home, 0.45)), (0.7, _mix(end, home, 0.82))]


def _mix(a, b, f):
    out = {}
    for k in set(a) | set(b):
        va, vb = a.get(k, (0, 0, 0)), b.get(k, (0, 0, 0))
        ra = va["rot"] if isinstance(va, dict) else va
        rb = vb["rot"] if isinstance(vb, dict) else vb
        rot = tuple(x + (y - x) * f for x, y in zip(ra, rb))
        if isinstance(va, dict) or isinstance(vb, dict):
            pa = va.get("pos", (0, 0, 0)) if isinstance(va, dict) else (0, 0, 0)
            pb = vb.get("pos", (0, 0, 0)) if isinstance(vb, dict) else (0, 0, 0)
            out[k] = {"rot": rot, "pos": tuple(x + (y - x) * f for x, y in zip(pa, pb))}
        else:
            out[k] = rot
    return out


def rootfall(r):
    """Rootfall (HEAVY, a gap closer): she sinks right down between her legs until her body nearly touches the floor,
    her knees standing high; holds; then she springs: up and over toward you with her legs drawn in under her, and
    comes down body first on the floor where you are, her legs slapping down out round her."""
    crouch = _pose(r, lift=-22, pitch=3, feet=_all(lambda k: _foot(k, out=3)), palps=(-20, -20), spread=4, fangs=4, tuck=2, brood=(-20, 0))
    gather = _pose(r, lift=-8, pitch=1, feet=_all(lambda k: _foot(k, out=1)), palps=(0, 0), spread=6, fangs=2, tuck=1, brood=(-8, 0))
    tucked = lambda up, zf: {k: (REST_FOOT[k][0] * 0.55, BODY + up - 26, REST_FOOT[k][2] * 0.45 + zf) for k in range(8)}
    hi = {k: (_dir(k)[0] * 0.3, -1.0, _dir(k)[1] * 0.3) for k in range(8)}
    leap = _pose(r, lift=18, pitch=-6, feet=tucked(18, 0), poles=hi, palps=(40, 40), spread=20, fangs=16, tuck=3, brood=(-24, 0))
    top = _pose(r, lift=22, pitch=4, feet=tucked(22, -4), poles=hi, palps=(50, 50), spread=24, fangs=18, tuck=3, brood=(-10, 0))
    land = _pose(r, lift=_floor_lift(1.0), pitch=2, feet=_all(lambda k: _foot(k, out=14)), palps=(-20, -20), spread=10, fangs=10, tuck=4, brood=(-30, 0))
    settle = _pose(r, lift=_floor_lift(2.5), pitch=0, feet=_all(lambda k: _foot(k, out=15)), palps=(-6, -6), spread=6, fangs=6, tuck=4, brood=(-28, 0))
    push = _pose(r, lift=-18, feet=_all(lambda k: _foot(k, out=6)), palps=(10, 10), tuck=2, brood=(-10, 0))
    return Move("rootfall", "HEAVY", 18, 10, 22, through=settle, title="Rootfall", hold=8, track=6.0, lock=12, advance=7.0, ability="Rootfall", rng=(5, 14),
                kinds=("gap", "area"), damage=1.3, knock=1.2, plant=(), hit=[("body", (0, 4, 0), 7.0)],
                stages={"windup": [(0.3, gather), (1.0, crouch)], "active": [(0.3, leap), (0.6, top), (1.0, land)], "recover": [(0.5, push)]}, lag=_lag())


def lances(r):
    """Root Lances (PERILOUS, the signature): she rears up on her back legs, her body tipped up and her four front legs
    raised high over her like spears, spread; then she drives them all down into the floor before her, her body coming
    down after them, and the roots burst up out of the floor toward you; she wrenches them out."""
    front = (0, 1, 4, 5)
    brace = {3: _foot(3, out=-4), 7: _foot(7, out=-4), 2: _foot(2, out=-2), 6: _foot(6, out=-2)}
    spears = {0: (24, 110, -20), 4: (-24, 110, -20), 1: (48, 96, 4), 5: (-48, 96, 4)}
    hi = {0: (0.5, -0.2, 1), 4: (-0.5, -0.2, 1), 1: (1, -0.2, 0.6), 5: (-1, -0.2, 0.6)}
    gather = _pose(r, lift=3, pitch=-8, feet=_with(brace, {k: _foot(k, up=24, out=-8) for k in front}), palps=(0, 0), spread=12, fangs=6, brood=(-10, 0))
    rear = _pose(r, lift=10, pitch=-22, feet=_with(brace, spears), poles=hi, palps=(-40, -40), spread=26, fangs=18, brood=(-24, 0))
    strain = _pose(r, lift=11, pitch=-24, roll=1.5, feet=_with(brace, {k: (v[0] * 1.06, v[1] + 3, v[2] + 3) for k, v in spears.items()}), poles=hi,
                   palps=(-46, -44), spread=30, fangs=20, brood=(-26, 2))
    drive = {0: (10, 0.5, -34), 4: (-10, 0.5, -34), 1: (26, 0.5, -28), 5: (-26, 0.5, -28)}
    down = {k: (SIDE[k] * 0.6, -1, -0.3) for k in front}
    hit = _pose(r, lift=-8, pitch=12, feet=_with(brace, drive), poles=down, palps=(30, 30), spread=6, fangs=20, brood=(18, 0))
    deep = _pose(r, lift=-10, pitch=14, feet=_with(brace, {k: (v[0], v[1], v[2]) for k, v in drive.items()}), poles=down, palps=(20, 24), fangs=14,
                 brood=(12, 0))
    wrench = _pose(r, lift=-6, pitch=6, roll=-2, feet=_with(brace, {k: (v[0] * 1.1, 10, v[2] + 6) for k, v in drive.items()}), poles=down,
                   palps=(16, 10), fangs=10, brood=(-6, 4))
    return Move("lances", "PERILOUS", 28, 10, 24, through=wrench, title="Root Lances", signature=True, track=6.0, ability="Root Lances", rng=(0, 14),
                kinds=("ranged", "area"), damage=0, knock=0, plant=(), stages={"windup": [(0.3, gather), (0.75, rear), (1.0, strain)],
                                                                             "active": [(0.3, hit), (1.0, deep)], "recover": [(0.5, _mix(wrench, _pose(r), 0.4))]},
                lag=_lag())


def brood(r):
    """Her Brood: she lowers her body until the sac touches the floor between her legs, her feelers tearing at it; it
    shakes three times and splits, and her young spill out of it; she lifts herself off them."""
    low = lambda j: _pose(r, lift=-30 + 1.5 * abs(j), pitch=2 * j, roll=2.5 * j, feet=_all(lambda k: _foot(k, out=8)), palps=(-50 + 10 * abs(j), -50 - 10 * abs(j)),
                          spread=14 + 8 * abs(j), fangs=10 + 6 * abs(j), brood=(-4 * j, 10 * j))
    gather = _pose(r, lift=-12, feet=_all(lambda k: _foot(k, out=4)), palps=(-10, -10), spread=8)
    act = [(0.16, low(1)), (0.33, low(-0.3)), (0.5, low(-1)), (0.66, low(0.3)), (0.83, low(1)), (1.0, low(0))]
    return Move("brood", "NONE", 14, 24, 16, through=low(-0.4), title="Brood", track=0.0, ability="Brood", rng=(0, 99), kinds=("summon",), damage=0, knock=0,
                plant=(), stages={"windup": [(0.3, gather), (1.0, low(0))], "active": act, "recover": _back(r, low(-0.4))}, lag=_lag())


def wake(r):
    """Her intro, "The Rootmother descends.": she rises up tall on her legs, her two front legs lifting to feel the air
    before her; then she lets her body down between them toward you, low, her feelers working, and settles."""
    tall = _pose(r, lift=12, pitch=-6, roll=-1.5, yaw=-2.5, feet=_with(_all(lambda k: _foot(k, out=-6)), {0: (30, 62, -46), 4: (-30, 58, -46)}),
                 poles={0: (0.5, -1, 0.5), 4: (-0.5, -1, 0.5)}, palps=(30, 30), spread=10)
    # the feelers and her body sway out of step: when the feelers turn, her body is swinging through, and back
    feel = lambda a, b: _pose(r, lift=13 + 1.2 * b, pitch=-8, roll=3 * b, yaw=5 * b,
                              feet=_with(_all(lambda k: _foot(k, out=-6)), {0: (28 + 4 * a, 90 + 6 * a, -44 + 4 * a), 4: (-28 + 4 * a, 90 - 6 * a, -44 - 4 * a)}),
                              poles={0: (0.5, -1, 0.5), 4: (-0.5, -1, 0.5)}, palps=(40 + 10 * a, 40 - 10 * a), spread=16, fangs=8, brood=(-10 - 4 * b, 4 * a))
    low = _pose(r, lift=-14, pitch=8, roll=-2, yaw=3, feet=_all(lambda k: _foot(k, out=4)), palps=(50, 50), spread=12, fangs=14, brood=(14, 0))
    end = _mix(low, _pose(r), 0.4)
    return Move("wake", "NONE", 16, 32, 14, through=_mix(low, _pose(r), 0.6), track=0.0, rng=(0, 99), kinds=("intro",), damage=0, knock=0, plant=(),
                stages={"windup": [(0.3, _mix(_pose(r), tall, 0.4)), (1.0, tall)],
                        "active": [(0.1, feel(0.3, -1)), (0.2, feel(1, 0)), (0.3, feel(0, 1)), (0.4, feel(-1, 0)), (0.5, feel(0, -1)),
                                   (0.58, feel(0.6, -0.4)), (0.8, low), (1.0, end)],
                        "recover": _back(r, end)}, lag=_lag())


# the ground (SPEC §35.10): the claws, the eggs and the streamers' ends stay out of the floor
LEG_GROUND = [([f"claw{k}", f"shinc{k}"], [(f"thigh{k}", 0, 30), (f"shin{k}", 0, 40)]) for k in range(8)]
HANG_GROUND = [([f"{nm}2"], [(f"{nm}0", 0, 80, -1), (f"{nm}1", 0, 80, -1)]) for nm in ("sa", "sb", "sc")] + \
              [([f"egg{k}" for k in range(6)], [("brood", 0, 80, -1)])]
# as she falls her body pitches forward onto the floor, so the streamers swing forward under her from the start (swung
# back they run out of room once she is down and would flip over to the front in a tick)
HANG_DEATH = [([f"{nm}2"], [(f"{nm}0", 0, 88, 1), (f"{nm}1", 0, 80, 1)]) for nm in ("sa", "sb", "sc")] + \
             [([f"egg{k}" for k in range(6)], [("brood", 0, 80, -1)])]
STANDS = {"stab": (1, 2, 3, 5, 6, 7), "stab_b": (0, 1, 2, 3, 5, 6, 7), "scythe": (0, 2, 3, 4, 5, 6, 7), "kick": (0, 1, 2, 4, 5, 6, 7),
          "web": (1, 2, 3, 5, 6, 7), "rootfall": (), "lances": (), "brood": (), "wake": ()}


def clips(r):
    _idle(r)
    _locomotion(r)
    _reactions(r)
    mv = [stab(r), stab(r, 4, "stab_b"), scythe(r), kick(r), web(r), rootfall(r), lances(r), brood(r), wake(r)]
    for m in mv:
        stage_clips(r, m)
        if STANDS[m.id]:
            _plant(r, core.clip_name(m), STANDS[m.id])
    everyday = [n for n in r.clips if n != "death"]
    core.ground_pass(r, LEG_GROUND, clips=everyday)
    core.ground_pass(r, HANG_GROUND, clips=everyday)
    core.ground_pass(r, HANG_DEATH, clips=["death"], skip=())
    return mv


# Her sounds (SPEC §35.8): the spider's own voice, roots and moss, the creaking's wood; chosen on paper, not heard in game.
SOUNDS = {
    "tell_light": [("entity.spider.ambient", 0.8, 0.6), ("block.mangrove_roots.step", 0.8, 0.7)],
    "tell_heavy": [("entity.creaking.activate", 1.0, 0.6), ("block.rooted_dirt.break", 1.0, 0.6)],
    "tell_perilous": [("block.creaking_heart.spawn", 1.4, 0.5), ("entity.creaking.twitch", 1.0, 0.5), ("entity.spider.ambient", 1.2, 0.4)],
    "swing_light": [("entity.player.attack.sweep", 0.8, 0.7), ("block.hanging_roots.hit", 1.0, 0.8)],
    "swing_heavy": [("entity.player.attack.knockback", 0.8, 0.6), ("block.mangrove_roots.break", 1.2, 0.6)],
    "cast": [("block.moss.place", 1.2, 0.6), ("block.sculk_vein.place", 1.0, 0.7)],
    "impact": [("block.rooted_dirt.break", 1.6, 0.5), ("entity.evoker_fangs.attack", 1.2, 0.6), ("block.wood.fall", 1.2, 0.5)],
    "roar": [("entity.spider.ambient", 1.6, 0.4), ("entity.creaking.ambient", 1.2, 0.5)],
    "step": [("block.mangrove_roots.step", 0.5, 0.8), ("block.moss.step", 0.4, 0.8)],
}
MOVE_SOUNDS = {"web": {"swing": "cast"}, "brood": {"swing": "roar", "impact": ""}, "lances": {"swing": "roar"}, "wake": {"swing": "roar"}}

FIGHT = {
    "intro": {"pose": "idle", "move": "wake"},
    "phases": [
        {"pool": [
            {"move": "stab", "weight": 3, "when": "near", "then": [["stab_b"], ["stab_b", "scythe"]]},
            {"move": "scythe", "weight": 2, "when": "near"},
            {"move": "web", "weight": 3, "when": "far"},
            {"move": "rootfall", "weight": 2, "when": "far", "then": [["stab"]]},
            {"move": "kick", "weight": 3, "when": "behind"},
        ]},
        # the Rim wakes with the second phase, as in DeepBestiary: her brood, and the lances
        {"transition": "brood", "pool": [
            {"move": "stab", "weight": 3, "when": "near", "then": [["stab_b"], ["stab_b", "scythe"]]},
            {"move": "scythe", "weight": 2, "when": "near"},
            {"move": "web", "weight": 2, "when": "far"},
            {"move": "rootfall", "weight": 2, "when": "far", "then": [["stab", "stab_b"]]},
            {"move": "lances", "weight": 2},
            {"move": "brood", "weight": 1},
            {"move": "kick", "weight": 3, "when": "behind"},
        ]},
    ],
    "enrage": {"below": 0.15, "speed": 1.2},
}


def design():
    r = body()
    r.pack()
    mv = clips(r)
    return {"rig": r, "plan": None, "moves": mv, "fight": FIGHT, "paint": paint, "scale": SCALE, "half": 1.0, "sounds": SOUNDS,
            "move_sounds": MOVE_SOUNDS}
