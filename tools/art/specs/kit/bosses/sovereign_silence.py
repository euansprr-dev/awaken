"""The Sovereign of Silence (SPEC §35.11, docs/bosses/ROLLOUT.md): the Hollow Court's second Sovereign, a Brute. The
Stone Ear, picked from its silhouette round 2026-10-29: "It listened until there was nothing left to hear."

A stooped giant of pale stone, long in the arm, whose whole head is one great ear. It has no face: where a face would
be, the ear stands up off its neck, rimmed and ridged like a real one, its lobe hanging, and in the bowl of it the
canal is a hole so dark it takes the light, ringed in a thin pale glow. Small ears grow out of its shoulders and down
its back through the veil it wears, and they twitch at every sound. The veil hangs from its shoulders to its knees,
black, its hem in tatters. Its hands are long and thin, the fingers longer than the palm.

Moves (SPEC §35.11): it rakes with one long hand and then the other (LIGHT); Found You (HEAVY: it goes still and tilts
its ear toward you, then rears up with both fists over its head and brings them down where you are); No Sound At All
(it lays one long finger across its ear and the light goes out of the room); a back-kick for whoever is behind it;
Inverted Scream (HEAVY: it grips the rim of its ear and tips it back, then thrusts it at you and the canal screams
silence at you in a beam); Ears (it plucks ears off its own shoulders and throws them down, and they come for you);
Hush (it cups its hands behind its ear and leans toward you, listening so hard it pulls you in); and Deafening
Silence (the signature: it claps both hands over its ear and curls up shaking, then throws its arms wide). Broken,
it sinks onto one knee with one hand on the floor, its ear hanging; it dies on its knees with its hands over its ear,
and falls on its side.
"""
import math

from kit import core, ik, parts, plans
from kit.core import Move, keyed, stage_clips
import fauna_rig as R
from fauna_rig import Ramp, hexc, p_stone, combine, p_strokes, SIDES

ID = "sovereign_silence"
SCALE = 1.9  # the def's scale; the body is modelled at true size and the hitbox set round its body

# The Stone Ear (docs/bosses/ROLLOUT.md, A). Long legs and a short body bent forward, the neck rising out of the hunch
# and the ear standing on it; its long arms hang to its knees. Value story: the canal's dark is the darkest thing on
# it and its ring the brightest; the stone of its skin and the ear are pale, the inside of the ear warmer, the veil
# nearly black.
P = plans.Biped(leg=30, thigh=15, foot=(10, 4, 14), pelvis=(18, 8, 12), spine=(16, 12, 12), chest=(30, 18, 18), neck=(7, 14, 7),
                head=(22, 32, 8), arm=(6, 22, 6), forearm=(5, 22, 5), hand=(5, 8, 8), stance=7, hunch=36, leg_w=(8, 6))
HUNCH = (12, 24)  # the spine's and the chest's rest pitch: its body bends 36 degrees forward
NECK = -24  # the neck's rest pitch on the chest: it rises forward out of the hunch
REST_HAND = {"l": (17.0, 23.0, -12.0), "r": (-17.0, 23.0, -12.0)}  # the wrists at rest, hanging beside its knees
POLE = {"l": (0.35, 0.0, 1.0), "r": (-0.35, 0.0, 1.0)}  # elbows back and a little out
EAR_TILT = 10  # the ear leans forward over its neck
EAR_TURN, EAR_ROLL = 34, 12  # and stands turned toward its left and cocked, as you turn an ear to a sound
FACE = (0, -EAR_TURN, -EAR_ROLL)  # a head delta that turns the ear square to you
ARMS = tuple(f"{b}_{s}" for b in ("shoulder", "arm", "forearm", "hand", "fingers", "index") for s in "lr")
ON_TIME = {b: 0 for b in ARMS}  # its hands go where they are sent: no trailing arms
SMALL = ((-13, -20, 0, (8, 0, 30)), (13, -20, 0, (8, 0, -30)), (-7, -20, 7, (26, 0, 16)), (7, -19, 7, (26, 0, -16)),
         (-15, -13, 6, (34, 0, 48)), (15, -12, 5, (34, 0, -48)))  # the small ears: where on the chest, how they lean
CANAL = (-4.0, -6.5, -3.5)  # the canal's middle, in the head's space


def body():
    r = R.Rig(ID, tex=(512, 512))
    pel = r.bone("pelvis", (0, 24 - P.leg, 0))
    pel.cube("pelvis", -9, -4, -6, 18, 8, 12)
    # long legs, the knees knobbed, the feet long and flat with three long toes
    for side, nm in ((1, "l"), (-1, "r")):
        mir = side < 0
        th = pel.bone("thigh_" + nm, (side * P.stance, 0, 0))
        th.cube("thigh_" + nm, -4, -1, -4, 8, 16, 8, mirror=mir)
        th.cube("knee_" + nm, -3, 12, -5, 6, 4, 2, mirror=mir)
        sn = th.bone("shin_" + nm, (0, P.thigh, 0))
        sn.cube("shin_" + nm, -3, 0, -3, 6, 12, 6, mirror=mir)
        ft = sn.bone("foot_" + nm, (0, P.leg - P.thigh - P.foot[1], 0))
        ft.cube("foot_" + nm, -4.5, 0, -8, 9, 4, 12, mirror=mir)
        for k, x in enumerate((-4.5, -1.5, 1.5)):
            ft.cube(f"toe{k}_" + nm, x, 1, -13, 3, 3, 5, mirror=mir)
        ft.cube("heel_" + nm, -2.5, 1, 4, 5, 3, 2, mirror=mir)
    # the short body bent forward, the ribs showing under the stone
    sp = pel.bone("spine", (0, -4, 0), (HUNCH[0], 0, 0))
    sp.cube("spine", -8, -12, -6, 16, 13, 12)
    ch = sp.bone("chest", (0, -12, 0), (HUNCH[1], 0, 0))
    ch.cube("chest", -15, -18, -9, 30, 18, 18)
    ch.cube("ribs", -12, -15, -10, 24, 11, 1)
    # the veil over its shoulders and hanging plumb down its back to its knees, its hem in tatters
    ch.cube("mantle", -17, -20, -8, 34, 4, 17)
    vl = ch.bone("veil", (0, -19, 9), (-sum(HUNCH), 0, 0))
    vl.cube("veil", -16, 0, 0, 32, 20, 2)
    v2 = vl.bone("veil_lo", (0, 20, 0))
    v2.cube("veil_lo", -15, 0, 0, 30, 13, 2)
    for k, (x, h) in enumerate(((-15, 4), (-9, 6), (-3, 3), (3, 5), (9, 4))):
        v2.cube(f"tatter{k}", x, 13, 0, 6, h, 1)
    # the small ears, growing out of its shoulders and back through the veil
    for k, (x, y, z, rot) in enumerate(SMALL):
        e = ch.bone(f"ear{k}", (x, y, z), rot)
        e.cube(f"ear{k}", -1.5, -7, -2.5, 3, 7, 5)
        e.cube(f"erim{k}", -2, -8, -3, 1, 6, 6)
    # the long arms: a knobbed shoulder, thin stone arms, a ridge at the wrist, a long hand; the fingers on their own
    # bone (they curl into a fist), the index on its own (it points)
    for side, nm in ((1, "l"), (-1, "r")):
        mir = side < 0
        sh = ch.bone("shoulder_" + nm, (side * 15, -15, -1), (-sum(HUNCH), 0, 0))
        sh.cube("shoulder_" + nm, -4, -3, -4, 8, 6, 8, mirror=mir)
        a = sh.bone("arm_" + nm, (0, 1, 0))
        a.cube("arm_" + nm, -3, -1, -3, 6, 22, 6, mirror=mir)
        f = a.bone("forearm_" + nm, (0, 21, 0))
        f.cube("forearm_" + nm, -2.5, 0, -2.5, 5, 22, 5, mirror=mir)
        f.cube("wrist_" + nm, -3, 18, -3, 6, 3, 6, mirror=mir)
        h = f.bone("hand_" + nm, (0, 22, 0))
        h.cube("hand_" + nm, -2.5, 0, -4, 5, 8, 8, mirror=mir)
        h.cube("thumb_" + nm, -2 if side > 0 else 0, 2, -6, 2, 7, 2, mirror=mir)
        g = h.bone("fingers_" + nm, (0, 8, 0.5))
        for k, z in enumerate((-2, 0.5, 3)):
            g.cube(f"finger{k}_" + nm, -1, 0, z - 1, 2, 10 - k, 2, mirror=mir)
        ix = h.bone("index_" + nm, (0, 8, -3.5))
        ix.cube("index_" + nm, -1, 0, -1, 2, 11, 2, mirror=mir)
    # the neck rising out of the hunch, and on it the ear: a plate of stone shaped like an ear, the rim curling round its
    # top and down its outer side into the lobe, the ridge inside it and the flap before the canal, and the canal
    nk = ch.bone("neck", (0, -18, -6), (NECK, 0, 0))
    nk.cube("neck", -3.5, -13, -3.5, 7, 14, 7)
    nk.cube("cords", -2.5, -11, -4.5, 5, 9, 1)
    ch.cube("cowl", -7, -22, -11, 14, 5, 12)  # the veil gathered round the root of its neck
    hd = nk.bone("head", (0, -12, -1), (-(sum(HUNCH) + NECK) + EAR_TILT, EAR_TURN, EAR_ROLL))
    hd.cube("earback", -10, -21, -3, 20, 12, 3)
    hd.cube("earback_t", -7, -26, -2.5, 14, 5, 3)
    hd.cube("earback_m", -8, -9, -3, 15, 7, 3)
    hd.cube("shell", -7, -23, 0, 14, 20, 3)  # the ear's back, rounded like a shell
    hd.cube("lobe", -4, -2, -4, 9, 7, 4)
    hd.cube("lobe_lo", -2, 5, -3.5, 6, 2, 3)
    hd.cube("helix0", -6, -27, -7, 12, 3, 5)
    hd.cube("helixf", -9, -25, -7, 4, 4, 5)
    hd.cube("helix1", 6, -25, -7, 3, 5, 5)
    hd.cube("helix2", 8, -21, -7, 3, 12, 5)
    hd.cube("helix3", 7, -9, -7, 3, 7, 5)
    hd.cube("helix4", 3, -3, -6, 4, 4, 4)
    hd.cube("crus", -10, -22, -5, 3, 9, 3)
    hd.cube("tragus", -10, -11, -6, 3, 5, 4)
    hd.cube("antitragus", -1, -4, -5, 4, 3, 2)
    hd.cube("antihelix", 2, -20, -5, 3, 13, 2)
    hd.cube("crura", -5, -21, -5, 8, 2, 2)
    cn = hd.bone("canal", CANAL)
    cn.cube("canal", -2, -2.5, -0.5, 4, 5, 1)
    cn.cube("canalring", -3, -3.5, 0, 6, 7, 1)
    r.look = [("head", 0.6, 0.4), ("neck", 0.3, 0.25)]
    r.size = P.leg / 36.0
    r.shadow = 2.0
    for side in "lr":
        ik.rest_limb(r, "arm_" + side, "forearm_" + side, "hand_" + side, REST_HAND[side], POLE[side])
    # secondary parts (SPEC §35.10): the veil swings and trails, the small ears twitch, the lobe hangs heavy
    r.springs = [("veil", 0.3, 1.3, 0.08), ("veil_lo", 0.5, 1.6, 0.12)] + [(f"ear{k}", 0.6, 3.0, 0.15) for k in range(len(SMALL))]
    r.guard = ["hand_l", "hand_r", "fingers_l", "fingers_r", "index_l", "index_r", "head", "veil_lo"]  # bones, not cubes
    return r


# ---------------------------------------------------------------------------------------------------------------------
# Paint

STONE = Ramp("#1f1d26", "#2c2a35", "#3c3a47", "#4f4c5b", "#63606f", "#797685", "#908d9b", "#a8a5b1", "#c0bec8", "#d8d6de", "#ebeaef")
INNER = Ramp("#050407", "#140d12", "#25171f", "#38222d", "#4d2f3b", "#623e4a", "#784f5a", "#8d6169")
VEIL = Ramp("#07080a", "#0d0f13", "#14171d", "#1c2028", "#252a34", "#2f3541", "#3b4250")
THREAD = "#aeb4c0"
RING = ("#e6f0ff", "#b8c8e8", "#7a8cb4")
RIMS = ("helix0", "helix1", "helix2", "helix3", "helix4", "crus", "tragus", "antitragus", "antihelix", "crura", "lobe", "lobe_lo", "helixf")


def paint(r, P_):
    """Pale stone with a faint violet cast, cracked and weathered, darker in its creases. The ear's rim and ridges are
    the palest stone on it, lit along their tops; inside them the ear is dusky flesh, darkening down into the bowl and
    to black at the canal, whose lip holds a thin cold light. The veil is nearly black, a pale thread along its hem."""
    rng = P_.rng
    of = lambda *ps: parts.cubes_of(r, *ps)
    seams = combine(p_stone(rng, 0.6, 9), p_strokes(4, 3, 0.3, 0.2, 0.3, seed=5))
    skin = ["pelvis", "spine", "chest", "ribs", "neck", "cords"] + of("thigh_", "knee_", "shin_", "foot_", "toe", "heel_", "shoulder_", "arm_",
                                                                  "forearm_", "wrist_", "hand_", "thumb_", "finger", "index_")
    for c in skin:
        P_.paint(c, STONE, 4.7, seams, grad=1.4, rim=1.0, ao=1.2, noise=0.7, nscale=7)
    # the ribs: low ridges under the stone either side of the breastbone
    f = P_.face("ribs", "north")
    for i, j in f.each():
        mid = abs(i - (f.w - 1) / 2) < 1.5
        f.set(i, j, STONE.at(5.9 if mid else (6.3 if j % 3 == 0 else 4.9 - 0.1 * abs(i - f.w / 2))))
    # the ear's back: weathered stone, like the rest of it
    for c in ("earback", "earback_t", "earback_m", "shell") + RIMS:
        P_.paint(c, STONE, 5.8, seams, grad=1.2, rim=1.2, ao=0.9, noise=0.5, nscale=6)
    # the rim and the ridges: the palest stone, lit on top, shadowed under
    for c in RIMS:
        for fname in ("north", "top"):
            f = P_.face(c, fname)
            for i, j in f.each():
                f.set(i, j, STONE.at((8.6 if fname == "top" else 7.6 - 0.5 * j / max(1, f.h - 1)) + 0.3 * (R._h(c, i, j) % 3 - 1)))
        for fname in ("west", "east", "bottom"):
            f = P_.face(c, fname)
            for i, j in f.each():
                f.set(i, j, INNER.at(4.6 - 1.2 * (fname == "bottom")))
    # inside the ear: flesh, darker down in the bowl (cubes, (canal x, y) on their north face) and black at the canal
    for c, cx, cy in (("earback_t", 3.0, 19.5), ("earback", 6.0, 14.5), ("earback_m", 4.0, 2.5)):
        f = P_.face(c, "north")
        for i, j in f.each():
            d = math.hypot(i + 0.5 - cx, (j + 0.5 - cy) * 1.15)
            f.set(i, j, INNER.at(min(6.6, 0.3 + d * 0.42) + 0.25 * (R._h(c, i, j) % 3 - 1)))
    # the canal: black, the lip of it a thin cold light on its upper and back edges (a crescent, not a ring)
    for fname in SIDES + ("top", "bottom"):
        f = P_.face("canal", fname)
        for i, j in f.each():
            f.set(i, j, hexc("#020203", 255))
    f, g = P_.face("canalring", "north"), P_.face("canalring", "north", glow=True)
    for i, j in f.each():
        lip = (j == 0 and i > 0) or (i == f.w - 1 and j < f.h - 2)
        f.set(i, j, hexc(RING[2] if lip else "#030305", 255))
        if lip:
            g.set(i, j, hexc(RING[0] if j == 0 else RING[1], 200))
    # the small ears: pale stone rims, flesh inside, dark at the root
    for c in of("ear", "erim"):
        if c.startswith("earback"):
            continue
        P_.paint(c, STONE, 6.4 if c.startswith("erim") else 5.6, None, grad=1.0, rim=1.2, ao=0.6, noise=0.4)
        if c.startswith("erim"):
            continue
        for fname in ("north", "west"):
            f = P_.face(c, fname)
            for i, j in f.each():
                f.set(i, j, INNER.at(1.4 + 3.4 * (1 - j / max(1, f.h - 1))))
    # the veil: nearly black wool, folds down it, a pale thread along its hem and the tatters
    cloth = R.p_blotch("veil", scale=4, amount=0.5)
    for c in ("mantle", "cowl", "veil", "veil_lo") + tuple(of("tatter")):
        P_.paint(c, VEIL, 3.4, cloth, grad=1.0, rim=0.6, ao=0.8, noise=0.5, nscale=3)
        for fname in SIDES:
            f = P_.face(c, fname)
            for i, j in f.each():
                if (i + R._h(c, fname) % 4) % 4 == 0:
                    f.set(i, j, VEIL.at(1.2))
    for c in ("veil_lo", "mantle", "cowl"):
        for fname in (("south", "west", "east") if c == "veil_lo" else SIDES):
            f = P_.face(c, fname)
            for i in range(f.w):
                if i % 2 == 0:
                    f.set(i, f.h - 1, hexc(THREAD, 255))
    return P_.skin, P_.glow


# ---------------------------------------------------------------------------------------------------------------------
# Posing

def _pose(r, hips=(0, 0, 0), at=(0, 0, 0), spine=(0, 0, 0), chest=(0, 0, 0), neck=(0, 0, 0), head=(0, 0, 0), left=None, right=None, lhand=None,
          rhand=None, legs=None, feet=("l", "r"), step=None, curl=(0.0, 0.0), point=(False, False), ears=0.0, veil=(0, 0, 0), face=0.0, more=None):
    """A whole-body key. It bends at the hips (hips: turn, at: x, up, z), the spine, the chest, the neck and the ear
    (head). The feet stay planted where they stand at rest (step: {side: (dx, dy, dz)} model pixels, y down) unless legs
    pose them. left, right: where that wrist goes, (x, up, z) model pixels or ("on", bone, point) a point on a bone
    (left alone, the arm hangs as at rest); lhand, rhand: (ydir, xdir) for that hand. curl (left, right) closes the
    fingers into a fist (1); point keeps that index straight; ears lifts (+) or flattens (-) the small ears; face turns the
    ear (which stands turned to its left at rest) square to you (1)."""
    head = tuple(h + face * f for h, f in zip(head, FACE))
    p = {"pelvis": {"rot": hips, "pos": at}, "spine": spine, "chest": chest, "neck": neck, "head": head, "veil": veil}
    if legs:
        p.update(legs)
    if more:
        p.update(more)
    if feet:
        p = core.plant(r, p, feet, at=step)
    fr = core.frames(r, core.as_sample(p))
    for side, tgt in (("l", left), ("r", right)):
        if tgt is None:
            continue
        q = core.point(fr, tgt[1], tgt[2]) if tgt[0] == "on" else ik.model(tgt)
        p.update(ik.limb(r, fr, "arm_" + side, "forearm_" + side, "hand_" + side, q, POLE[side]))
    for side, o in (("l", lhand), ("r", rhand)):
        if o:
            fr = core.frames(r, core.as_sample(p))
            p["hand_" + side] = ik.orient(r, fr, "hand_" + side, *o)
    for k, side in enumerate("lr"):
        s = 1 if side == "l" else -1
        c = curl[k]
        p["fingers_" + side] = (0, 0, -s * 100 * c)
        p["index_" + side] = (0, 0, 0) if point[k] else (0, 0, -s * 95 * c)
    for k in range(len(SMALL)):
        p[f"ear{k}"] = (-24 * ears, 0, 0)
    return p


def _mix(a, b, f):
    """Between two keys (the bones of either), f of the way from a to b."""
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


def _back(r, end):
    """A recovery in two steps to rest, so nothing snaps home."""
    home = _pose(r)
    return [(0.45, _mix(end, home, 0.45)), (0.7, _mix(end, home, 0.8))]


def _on(r, end, f=0.08):
    """The follow-through: the end carried on a little past where it was going (away from rest)."""
    return _mix(end, _pose(r), -f)


def _knee():
    """Down on its right knee, the shin flat behind and the foot on its instep (the left foot is planted ahead, KNEE_STEP)."""
    return {"thigh_r": (10, 0, 6), "shin_r": (92, 0, 0), "foot_r": (78, 0, 0)}


KNEE_STEP = {"l": (0, 0, -9)}


def _laid(r, lie=("thigh_l", "shin_l", "foot_l"), **kw):
    """A key lying on the floor: the body set down so its lowest solid part rests on the floor (the veil drapes), and the
    bottom leg's thigh turned in or out at the hip (the least it can) so the leg lies on the floor too, not through it."""
    best = None
    for z in range(-40, 41, 4):
        k = dict(kw)
        k["legs"] = {**kw["legs"], lie[0]: (kw["legs"][lie[0]][0], 0, z)}
        p = _pose(r, **k)
        fr = core.frames(r, core.as_sample(p))
        body = core.lowest(r, fr, [b.name for b in r.bones if b.cubes and b.name not in lie and not b.name.startswith("veil")])
        leg = core.lowest(r, fr, list(lie))
        cost = abs(leg - body) + 0.02 * abs(z)
        if best is None or cost < best[0]:
            best = (cost, k, max(body, leg))
    _, k, low = best
    at = k.get("at", (0, 0, 0))
    return _pose(r, **{**k, "at": (at[0], at[1] + (low - 24.0) - 0.3, at[2])})


def _knees():
    """Down on both knees, the shins flat behind and the feet on their insteps, soles up."""
    return {"thigh_l": (-6, 0, -4), "shin_l": (90, 0, 0), "foot_l": (86, 0, 0), "thigh_r": (-6, 0, 4), "shin_r": (90, 0, 0), "foot_r": (86, 0, 0)}


def _track(r, mv, bone, pts):
    """A move's scale keys for one bone: [(stage, fraction, scale)] in its stages' time."""
    W, A, Rr = mv.seconds("windup"), mv.seconds("active"), mv.seconds("recover")
    t0 = {"windup": 0.0, "active": W, "recover": W + A}
    ln = {"windup": W, "active": A, "recover": Rr}
    c = r.clips[core.clip_name(mv)]
    ks = [(0.0, 1.0)] + [(t0[s] + f * ln[s], v) for s, f, v in pts] + [(c.length, 1.0)]
    c.scale(bone, [(t, v, v, v) for t, v in sorted(ks)], "cat")


def _gait(u, swing=24.0, bend=40.0):
    """A long stalking stride at phase u (0..1, the left foot planted first): thigh, knee and foot turns."""
    s, c = math.sin(2 * math.pi * u), math.cos(2 * math.pi * u)
    lk, rk = max(0.0, c) * bend + 6, max(0.0, -c) * bend + 6
    return {"thigh_l": (-swing * s - lk * 0.45, 0, 0), "shin_l": (lk, 0, 0), "foot_l": (swing * s * 0.6 - lk * 0.55, 0, 0),
            "thigh_r": (swing * s - rk * 0.45, 0, 0), "shin_r": (rk, 0, 0), "foot_r": (-swing * s * 0.6 - rk * 0.55, 0, 0)}


# ---------------------------------------------------------------------------------------------------------------------
# Clips

def idle(r):
    """It stands stooped and still, listening: the ear turns slowly to one side and stops there, then gives a small
    sharp tilt, as a dog's head tilts; it holds; it turns slowly back the other way; the small ears twitch round on
    its shoulders now and then; its long fingers flex once; it breathes slowly."""
    k0 = _pose(r)
    breathe = _pose(r, at=(0, 0.4, 0), spine=(-1, 0, 0), chest=(-2, 0, 0), head=(1, 0, 0), ears=0.2)
    turn_r = _pose(r, hips=(0, -2, 0), chest=(-1, -5, 0), neck=(0, -12, 0), head=(-2, -24, 0), ears=0.3)
    tilt_r = _pose(r, hips=(0, -2, 0), chest=(-1, -5, 0), neck=(2, -12, -4), head=(0, -26, -20), ears=0.6)
    hold_r = _pose(r, hips=(0, -2, 0), at=(0, 0.3, 0), chest=(-1.5, -5, 0), neck=(2, -12, -4), head=(0, -27, -18), ears=0.5)
    turn_l = _pose(r, hips=(0, 3, 0), chest=(-1, 6, 0), neck=(0, 12, 0), head=(-3, 22, 4), ears=0.1)
    flex = _pose(r, hips=(0, 3, 0), chest=(-1, 6, 0), neck=(0, 12, 0), head=(-3, 22, 8), curl=(0.45, 0.3), ears=-0.2)
    keyed(r, "idle", 7.0, [(0, k0), (0.9, breathe), (1.8, turn_r), (2.05, tilt_r), (3.0, hold_r), (3.6, _mix(hold_r, k0, 0.5)), (4.4, turn_l),
                           (4.9, flex), (5.5, _mix(flex, turn_l, 0.8)), (6.2, _mix(turn_l, k0, 0.7)), (7.0, k0)], loop=True)


def _stalk(r):
    """The walk, rewritten as a stalk: long slow strides, the knees lifted high, its body low and leaning into them; its
    long arms swing a little and its fingers trail; the ear turns slowly from side to side as it goes, listening."""
    L = 1.6
    keys = []
    n = 24
    for k in range(n + 1):
        u = k / n
        s, s2 = math.sin(2 * math.pi * u), math.sin(4 * math.pi * u)
        legs = _gait(u)
        p = _pose(r, hips=(2, 4 * s, -2 * s), at=(0, -1.2 + 1.2 * abs(math.cos(2 * math.pi * u)), 0), spine=(1, -3 * s, 0), chest=(1 + 1.5 * s2, -4 * s, 2 * s),
                  neck=(-1, 6 * math.sin(math.pi * u + 0.4), 0), head=(-2 - 1.5 * s2, 14 * math.sin(math.pi * u + 0.4), 4 * math.sin(math.pi * u)),
                  legs=legs, feet=(), curl=(0.15, 0.15), ears=0.2 + 0.2 * s2)
        p.update({"arm_l": (8 * s + 4, 0, -2), "arm_r": (-8 * s + 4, 0, 2), "forearm_l": (-6 - 4 * max(0, s), 0, 0), "forearm_r": (-6 - 4 * max(0, -s), 0, 0)})
        keys.append((L * u, p))
    keyed(r, "walk", L, keys, loop=True)
    r.walk_stride = 2.0


def reactions(r):
    """Struck, it flinches with its whole body and its ear jerks back and away, its hands coming up toward it. Its stance
    broken, it sinks onto its right knee with its left hand flat on the floor, its ear hanging forward; it rises with a
    push off the hand. Its death: it staggers, claps its hands over its ear, sinks onto both knees rocking, and falls
    onto its side, its ear on the floor and one long arm reaching out along it."""
    hit = _pose(r, hips=(-4, 0, 0), at=(0, 0, 1.5), spine=(-4, 0, 0), chest=(-10, 0, 0), neck=(-6, 0, 0), head=(-18, 0, 6), curl=(0.4, 0.4), ears=-0.8,
                left=(14, 36, -18), right=(-14, 36, -18))
    back = _pose(r, chest=(3, 0, 0), head=(4, 0, -2), ears=0.3)
    keyed(r, "hurt", 0.5, [(0, _pose(r)), (0.07, hit), (0.28, back), (0.5, _pose(r))])
    for nm, sgn in (("hurt_l", 1), ("hurt_r", -1)):
        h = _pose(r, hips=(0, -8 * sgn, -3 * sgn), at=(-sgn * 1.0, 0, 0), chest=(-4, -14 * sgn, -8 * sgn), neck=(0, -10 * sgn, 0), head=(-6, -20 * sgn, -16 * sgn),
                  ears=-0.6, curl=(0.3, 0.3))
        b = _pose(r, chest=(1, 4 * sgn, 2 * sgn), head=(2, 6 * sgn, 4 * sgn), ears=0.2)
        keyed(r, nm, 0.55, [(0, _pose(r)), (0.07, h), (0.3, b), (0.55, _pose(r))])
    kneel = _pose(r, hips=(8, 0, 0), at=(0, -13, 1), legs=_knee(), feet=("l",), step=KNEE_STEP, spine=(6, 0, 0), chest=(12, -6, 4), neck=(10, 0, 0), head=(18, 6, 10),
                  left=(14, 1, -20), lhand=((0, 1, -0.2), (1, 0, 0)), curl=(0.0, 0.3), ears=-0.8)
    sag = _pose(r, hips=(4, 0, 0), at=(0, -5, 0), chest=(6, 0, 0), head=(10, 0, 0), ears=-0.4)
    keyed(r, "kneel", 0.65, [(0, _pose(r)), (0.25, sag), (0.65, kneel)])
    push = _pose(r, hips=(6, 0, 0), at=(0, -6, 0.5), chest=(8, -3, 1), head=(6, 0, 0), left=(14, 6, -18), lhand=((0, 1, -0.2), (1, 0, 0)), ears=0.0)
    keyed(r, "rise", 0.9, [(0, kneel), (0.45, push), (0.9, _pose(r))])
    cover = dict(left=("on", "head", (8, -10, -10)), right=("on", "head", (-9, -10, -10)), lhand=((-0.3, -0.9, 0.3), (0, 0, -1)), rhand=((0.3, -0.9, 0.3), (0, 0, -1)))
    stag = _pose(r, hips=(-4, 6, 0), at=(0, 0, 1.5), chest=(-10, 8, 0), head=(-20, 10, 10), ears=-0.6, curl=(0.3, 0.3))
    clutch = _pose(r, hips=(2, 0, 0), at=(0, -3, 1), chest=(4, 0, 0), neck=(6, 0, 0), head=(10, 0, 0), ears=-1.0, **cover)
    knees = _pose(r, hips=(-4, 0, 0), at=(0, -10, 2), legs=_knees(), feet=(), spine=(2, 0, 0), chest=(6, 0, 0), neck=(8, 0, 0), head=(14, 0, 0), ears=-1.0, **cover)
    rock = _pose(r, hips=(0, 0, 4), at=(0, -10, 2), legs=_knees(), feet=(), spine=(4, 0, 2), chest=(10, 0, 4), neck=(10, 0, 0), head=(18, 0, 6), ears=-1.0, **cover)
    tip = _pose(r, hips=(6, 0, 26), at=(5, -11, 2), legs={**_knees(), "thigh_l": (-6, 0, 20), "thigh_r": (-6, 0, 26)}, feet=(), spine=(4, 0, 6),
                chest=(8, 0, 10), neck=(8, 0, 0), head=(12, 0, 10), ears=-1.0, left=(30, 6, -14), lhand=((0, 1, -0.2), (1, 0, 0)),
                right=("on", "head", (-9, -10, -10)), rhand=((0.3, -0.9, 0.3), (0, 0, -1)))
    down = _laid(r, hips=(8, 0, 84), at=(18, 0, 2), legs={**_knees(), "thigh_r": (-22, 0, -6), "shin_r": (80, 0, 0)}, feet=(),
                 spine=(4, 0, 4), chest=(6, 0, 4), neck=(4, 0, 6), head=(6, 0, 10), ears=-1.0, veil=(0, -64, 0),
                 left=("on", "pelvis", (11, -44, -24)), lhand=((0, -0.3, -1), (0, 1, 0)), curl=(0.1, 0.5),
                 right=("on", "head", (-9, -10, -10)), rhand=((0.3, -0.9, 0.3), (0, 0, -1)))
    keyed(r, "death", 4.6, [(0, _pose(r)), (0.3, stag), (0.8, clutch), (1.4, knees), (1.9, rock), (2.4, knees), (3.0, tip), (3.7, down), (4.6, down)])


# ---------------------------------------------------------------------------------------------------------------------
# Moves

def rake(r, side="r", mid="rake"):
    """The Rake: one long arm reaches up and back past its shoulder, the fingers spread, its body turning away and its
    ear turned toward you; then the hand comes down across in front of it, the long fingers raking, the body turning
    after it (LIGHT)."""
    s = 1 if side == "l" else -1
    hand = "left" if side == "l" else "right"
    o = "lhand" if side == "l" else "rhand"
    k = 0 if side == "l" else 1
    off = "right" if side == "l" else "left"
    cu = lambda v: (v, 0.0) if side == "l" else (0.0, v)
    cock = _pose(r, hips=(-4, 14 * s, 0), at=(0, 0.5, 0.5), spine=(-4, 6 * s, 0), chest=(-12, 20 * s, -8 * s), neck=(-2, -12 * s, 0), head=(-6, -18 * s, -8 * s),
                 ears=0.8, **{hand: (s * 22, 84, 10), off: (-s * 14, 32, -24)}, curl=cu(-0.3), face=0.5)
    gather = _mix(_pose(r), cock, 0.4)
    swing = _pose(r, hips=(4, 2 * s, 0), at=(0, -1.5, -0.5), chest=(6, 4 * s, 0), neck=(-2, -4 * s, 0), head=(-4, -8 * s, 0), ears=0.4,
                  **{hand: (s * 12, 40, -32), off: (-s * 18, 30, -14)}, curl=cu(-0.1), face=0.5)
    hit = _pose(r, hips=(8, -10 * s, 0), at=(0, -3, -1), chest=(10, -16 * s, 6 * s), neck=(0, 6 * s, 0), head=(-2, 10 * s, 6 * s), ears=0.2,
                **{hand: (-s * 10, 14, -30)}, curl=cu(0.25), face=0.5)
    held = _pose(r, hips=(9, -12 * s, 0), at=(0, -3.5, -1), chest=(11, -19 * s, 7 * s), neck=(0, 7 * s, 0), head=(-1, 12 * s, 6 * s), ears=0.1,
                 **{hand: (-s * 18, 12, -22)}, curl=cu(0.4), face=0.5)
    return Move(mid, "LIGHT", 10, 4, 14, title="Rake", track=8.0, rng=(0, 4.4), kinds=("combo", "near"), damage=1.0, knock=0.6, plant=(),
                hit=[("fingers_" + side, (0, 8, 0), 3.0), ("hand_" + side, (0, 4, 0), 3.5)], lag=ON_TIME, through=_on(r, held, 0.04),
                stages={"windup": [(0.3, gather), (1.0, cock)], "active": [(0.25, swing), (0.5, hit), (1.0, held)], "recover": _back(r, held)})


def found(r):
    """Found You (HEAVY, a gap closer): it goes still and turns its ear square to you and tilts it, leaning in; then it
    rears up straight with both fists over its head, holds, and lunges at you in one long stride, bringing them down
    together where you stand, its knees giving under the blow."""
    lean = _pose(r, hips=(4, 0, 0), at=(0, -1, -1), chest=(6, 0, 0), neck=(4, 0, -6), head=(4, 0, -16), ears=1.0, face=1, left=(19, 28, -4), right=(-19, 28, -4), curl=(0.3, 0.3))
    still = _pose(r, hips=(5, 0, 0), at=(0, -1.2, -1.2), chest=(7, 0, 0), neck=(5, 0, -6), head=(5, 0, -20), ears=1.0, face=1, left=(19, 30, -2), right=(-19, 30, -2), curl=(0.5, 0.5))
    up = _pose(r, hips=(-6, 0, 0), at=(0, 1.5, 1), spine=(-8, 0, 0), chest=(-22, 0, 0), neck=(-6, 0, 0), head=(-10, 0, 0), ears=0.6, face=1,
               left=(7, 98, 5), right=(-7, 98, 5), lhand=((0, -0.6, -0.8), (1, 0, 0)), rhand=((0, -0.6, -0.8), (1, 0, 0)), curl=(1, 1))
    lunge = {"l": (0, 0, -12), "r": (0, 0, 6)}  # the long stride it takes at you as it comes down
    smash = _pose(r, hips=(14, 0, 0), at=(0, -8, -2), spine=(10, 0, 0), chest=(18, 0, 0), neck=(4, 0, 0), head=(8, 0, 0), ears=-0.4, face=1, step=lunge,
                  left=(6, 5, -34), right=(-6, 5, -34), lhand=((0, 0.8, -0.6), (1, 0, 0)), rhand=((0, 0.8, -0.6), (1, 0, 0)), curl=(1, 1))
    after = _pose(r, hips=(15, 0, 0), at=(0, -8.5, -2.2), spine=(11, 0, 0), chest=(19, 0, 0), neck=(5, 0, 0), head=(10, 0, 0), ears=-0.6, face=1, step=lunge,
                  left=(6.5, 4, -35), right=(-6.5, 4, -35), lhand=((0, 0.9, -0.4), (1, 0, 0)), rhand=((0, 0.9, -0.4), (1, 0, 0)), curl=(1, 1))
    lift = _pose(r, hips=(8, 0, 0), at=(0, -4, -1), spine=(5, 0, 0), chest=(9, 0, 0), head=(4, 0, -6), ears=0.4, face=0.6, step={"l": (0, 0, -6), "r": (0, 0, 3)},
                 left=(10, 18, -28), right=(-10, 18, -28), lhand=((0, 0.9, -0.4), (1, 0, 0)), rhand=((0, 0.9, -0.4), (1, 0, 0)), curl=(0.5, 0.5))
    return Move("found", "HEAVY", 24, 5, 22, title="Found You", hold=10, track=5.0, advance=5.0, ability="Found You", rng=(2.5, 9.5), kinds=("gap", "area"),
                damage=1.5, knock=1.3, plant=(), disc=4.0, disc_at="hit", hit=[("hand_l", (0, 4, 0), 4.5), ("hand_r", (0, 4, 0), 4.5)], lag=ON_TIME,
                through=after, stages={"windup": [(0.3, lean), (0.55, still), (1.0, up)], "active": [(0.5, smash), (1.0, after)],
                                       "recover": [(0.4, lift), (0.7, _mix(lift, _pose(r), 0.75))]})


def nosound(r):
    """No Sound At All: it straightens a little and lifts one long hand to its ear, and lays its finger across the canal
    as you would lay one across your lips; it holds it there while the light goes out of the room, the ear bowing over
    the finger and the small ears lying flat; its other hand spreads low at its side, palm down."""
    def shh(**kw):
        """A key with the right index laid up across the canal: the wrist below the ear, the hand turned to the ear's
        own up and across (it stands turned), so the finger lies on the canal as on lips."""
        k = dict(kw, right=("on", "head", (-4, 6.5, -11.5)), curl=(kw.get("curl", (0.0, 0.0))[0], 1.0), point=(False, True))
        M, _ = core.frames(r, core.as_sample(_pose(r, **k)))["head"]
        return _pose(r, **k, rhand=(R._mv(M, (0, -1, 0)), R._mv(M, (1, 0, 0))))
    rise = _pose(r, hips=(-2, 0, 0), at=(0, 0.6, 0.5), chest=(-6, 0, 0), neck=(-2, 0, 0), head=(-4, 0, 0), ears=0.4,
                 right=(-14, 46, -24), rhand=((0, -1, -0.2), (1, 0, 0)), curl=(0.0, 0.6), point=(False, True))
    lay = shh(hips=(-2, 0, 0), at=(0, 0.6, 0.5), chest=(-6, 0, 0), neck=(-2, 0, 0), head=(-2, 0, 0), ears=-0.6, left=(24, 24, -10),
              lhand=((0.2, 1, 0), (0, 0, -1)))
    bow = lambda a: shh(hips=(1, 0, 0), at=(0, -0.5, 0), chest=(-2, 0, 0), neck=(4, 0, 0), head=(8 + 2 * a, 0, 2 * a), ears=-1.0,
                        left=(25, 23 + 1.5 * a, -9), lhand=((0.2, 1, 0), (0, 0, -1)))
    act = [(0.15, lay), (0.4, bow(1)), (0.65, bow(-1)), (0.85, bow(1)), (1.0, bow(0))]
    return Move("nosound", "NONE", 16, 26, 16, title="No Sound At All", track=0.0, ability="No Sound At All", rng=(0, 20), kinds=("area",), damage=0,
                knock=0, plant=(), lag=ON_TIME, through=_mix(bow(0), _pose(r), 0.25),
                stages={"windup": [(0.4, _mix(_pose(r), rise, 0.5)), (1.0, rise)], "active": act, "recover": _back(r, _mix(bow(0), _pose(r), 0.25))})


SCREAM_FROM = (-0.25, 3.6, 2.5)  # where the beam leaves it: the canal as it pours (blocks: x, up, forward)


def scream(r):
    """Inverted Scream (HEAVY): it grips the rim of its ear in both hands and tips it right back, shuddering, the canal
    swelling; then it lets go, braces its feet, and thrusts the ear at you, and the silence comes out of it in a beam,
    the ear juddering as it pours."""
    grip = dict(left=("on", "head", (12, -14, -2)), right=("on", "head", (-12, -14, -2)), lhand=((-0.8, 0.2, -0.4), (0, 0, -1)),
                rhand=((0.8, 0.2, -0.4), (0, 0, -1)), curl=(0.6, 0.6))
    tip = lambda a: _pose(r, hips=(-6, 0, 0), at=(0, 0.5, 1.5), spine=(-6, 0, 0), chest=(-16, 0, 0), neck=(-14, 0, 2 * a), head=(-30, 0, 3 * a), ears=1.0, face=1, **grip)
    brace = {"l": (0, 0, 2), "r": (0, 0, 4)}
    pour = lambda a: _pose(r, hips=(6, 0, 0), at=(0, -4, -1), spine=(4, 0, 0), chest=(6, 0, 0), neck=(10, 0, 1.5 * a), head=(16, 0, 2.5 * a), ears=-1.0, face=1,
                           step=brace, left=(30, 30, 8), right=(-30, 30, 8), lhand=((0.6, 0.6, 0.4), (0, 0, -1)), rhand=((-0.6, 0.6, 0.4), (0, 0, -1)),
                           curl=(-0.2, -0.2))
    wind = [(0.3, _mix(_pose(r), tip(0), 0.4)), (0.6, tip(1)), (0.8, tip(-1)), (1.0, tip(0))]
    act = [(0.15, pour(0)), (0.3, pour(1)), (0.45, pour(-1)), (0.6, pour(1)), (0.75, pour(-1)), (0.9, pour(1)), (1.0, pour(0))]
    mv = Move("scream", "HEAVY", 22, 20, 18, title="Inverted Scream", hold=8, track=3.0, ability="Inverted Scream", origin=SCREAM_FROM, rng=(4, 20),
              kinds=("ranged",), damage=0, knock=0, plant=(), lag=ON_TIME, through=_mix(pour(0), _pose(r), 0.3),
              stages={"windup": wind, "active": act, "recover": _back(r, _mix(pour(0), _pose(r), 0.3))})
    mv._canal = [("windup", 0.6, 1.3), ("windup", 1.0, 1.5), ("active", 0.15, 1.8), ("active", 1.0, 1.6), ("recover", 0.5, 1.1)]
    return mv


def ears(r):
    """Ears (summoning): it reaches over its right shoulder with its left hand and plucks an ear off it, and throws it
    down in front of it; then over its left shoulder with the right hand, and throws that one too; they grow back."""
    reach_l = _pose(r, hips=(0, -6, 0), chest=(-4, -10, 0), head=(-4, 16, 0), ears=0.8, left=("on", "chest", (-12, -24, 0)),
                    lhand=((0, 1, 0.3), (1, 0, 0)), curl=(0.3, 0.0))
    pluck_l = _pose(r, hips=(0, -6, 0), chest=(-6, -12, 0), head=(-6, 18, 0), ears=0.8, left=("on", "chest", (-12, -28, 0)),
                    lhand=((0, 1, 0.3), (1, 0, 0)), curl=(0.8, 0.0))
    throw_l = _pose(r, hips=(6, 8, 0), at=(0, -2, -1), chest=(8, 12, 0), head=(2, -6, 0), ears=0.4, left=(4, 12, -34), lhand=((0, 0.6, -0.8), (1, 0, 0)),
                    curl=(-0.2, 0.0))
    reach_r = _pose(r, hips=(0, 6, 0), chest=(-4, 10, 0), head=(-4, -16, 0), ears=0.8, right=("on", "chest", (12, -24, 0)),
                    rhand=((0, 1, 0.3), (1, 0, 0)), curl=(0.0, 0.3))
    pluck_r = _pose(r, hips=(0, 6, 0), chest=(-6, 12, 0), head=(-6, -18, 0), ears=0.8, right=("on", "chest", (12, -28, 0)),
                    rhand=((0, 1, 0.3), (1, 0, 0)), curl=(0.0, 0.8))
    throw_r = _pose(r, hips=(6, -8, 0), at=(0, -2, -1), chest=(8, -12, 0), head=(2, 6, 0), ears=0.4, right=(-4, 12, -34), rhand=((0, 0.6, -0.8), (1, 0, 0)),
                    curl=(0.0, -0.2))
    act = [(0.12, pluck_l), (0.32, throw_l), (0.48, reach_r), (0.6, pluck_r), (0.8, throw_r), (1.0, _mix(throw_r, _pose(r), 0.3))]
    mv = Move("ears", "NONE", 12, 26, 14, title="Ears", track=0.0, ability="Ears", rng=(0, 99), kinds=("summon",), damage=0, knock=0, plant=(), lag=ON_TIME,
              through=_mix(throw_r, _pose(r), 0.45), stages={"windup": [(0.4, _mix(_pose(r), reach_l, 0.5)), (1.0, reach_l)], "active": act,
                                                            "recover": _back(r, _mix(throw_r, _pose(r), 0.45))})
    mv._plucked = {1: [("active", 0.1, 1.0), ("active", 0.14, 0.0), ("recover", 0.4, 0.0), ("recover", 1.0, 1.0)],
                   0: [("active", 0.58, 1.0), ("active", 0.62, 0.0), ("recover", 0.5, 0.0), ("recover", 1.0, 1.0)]}
    return mv


def hush(r):
    """Hush: it cups both long hands behind the rim of its ear and leans far toward you, listening so hard it pulls you
    in, the ear turning a little one way and the other to find you; the small ears all stand up."""
    cup = dict(left=("on", "head", (13, -12, 4)), right=("on", "head", (-13, -12, 4)), lhand=((-0.9, 0.1, 0.3), (0, 0, -1)), rhand=((0.9, 0.1, 0.3), (0, 0, -1)),
               curl=(0.3, 0.3))
    lean = lambda a: _pose(r, hips=(12, 3 * a, 0), at=(0, -5, -3), spine=(8, 0, 0), chest=(10, 2 * a, 0), neck=(6, 4 * a, 0), head=(-4, 8 * a, -6 * a), ears=1.0, face=1,
                           step={"l": (0, 0, -3), "r": (0, 0, 2)}, **cup)
    act = [(0.2, lean(0)), (0.4, lean(1)), (0.6, lean(-1)), (0.8, lean(0.6)), (1.0, lean(0))]
    return Move("hush", "NONE", 14, 26, 16, title="Hush", track=4.0, ability="Hush", rng=(3, 14), kinds=("ranged",), damage=0, knock=0, plant=(), lag=ON_TIME,
                through=_mix(lean(0), _pose(r), 0.3), stages={"windup": [(0.4, _mix(_pose(r), lean(0), 0.4)), (1.0, _mix(_pose(r), lean(0), 0.85))],
                                                             "active": act, "recover": _back(r, _mix(lean(0), _pose(r), 0.3))})


def deafening(r):
    """Deafening Silence (PERILOUS, the signature): it claps both hands over its ear and curls over them, sinking on its
    knees, and shakes, harder and harder, the small ears lying flat; then it straightens all at once and throws its arms
    wide and its ear back and open."""
    cover = dict(left=("on", "head", (8, -12, -10)), right=("on", "head", (-9, -12, -10)), lhand=((-0.3, -0.9, 0.3), (0, 0, -1)),
                 rhand=((0.3, -0.9, 0.3), (0, 0, -1)), curl=(0.2, 0.2))
    curl_ = lambda a, d: _pose(r, hips=(10, 0, 2 * a), at=(0, -6 - 2 * d, -1), spine=(8 + 2 * d, 0, 0), chest=(14 + 4 * d, 0, 2 * a), neck=(10, 0, 0),
                               head=(16, 2 * a, 4 * a), ears=-1.0, face=0.6, **cover)
    wind = [(0.2, curl_(0, 0)), (0.35, curl_(1, 0.2)), (0.48, curl_(-1, 0.4)), (0.6, curl_(1, 0.6)), (0.7, curl_(-1, 0.8)), (0.8, curl_(1, 1)),
            (0.88, curl_(-1, 1)), (0.95, curl_(1, 1)), (1.0, curl_(0, 1))]
    wide = lambda a: _pose(r, hips=(-8, 0, 0), at=(0, 2, 1.5), spine=(-10, 0, 0), chest=(-22, 0, 0), neck=(-12, 0, 0), head=(-26, 0, 2 * a), ears=1.0, face=1,
                           left=(40, 54, 6), right=(-40, 54, 6), lhand=((0.9, -0.2, 0.3), (0, 0, -1)), rhand=((-0.9, -0.2, 0.3), (0, 0, -1)), curl=(-0.3, -0.3))
    act = [(0.25, wide(0)), (0.6, wide(1)), (1.0, wide(0))]
    mv = Move("deafening", "PERILOUS", 30, 12, 28, title="Deafening Silence", signature=True, track=0.0, ability="Deafening Silence", rng=(0, 15),
              kinds=("area",), damage=0, knock=0, plant=(), lag=ON_TIME, through=_mix(wide(0), _pose(r), 0.2),
              stages={"windup": wind, "active": act, "recover": [(0.35, _mix(wide(0), _pose(r), 0.45)), (0.7, _mix(wide(0), _pose(r), 0.85))]})
    mv._canal = [("windup", 1.0, 0.6), ("active", 0.25, 1.9), ("active", 1.0, 1.6), ("recover", 0.6, 1.0)]
    return mv


def kick(r):
    """For whoever is behind it: it pitches forward onto its long arms, its hands reaching for the floor, and its right
    leg folds up and kicks straight back like a mule's."""
    down = dict(left=(12, 12, -24), right=(-12, 14, -24), lhand=((0, 1, -0.3), (1, 0, 0)), rhand=((0, 1, -0.3), (1, 0, 0)))
    fold = _pose(r, hips=(18, 0, 0), at=(0, -2, -2), spine=(6, 0, 0), chest=(4, 0, 0), neck=(-8, 0, 0), head=(-10, -20, 0), ears=0.8, feet=("l",),
                 legs={"thigh_r": (-34, 0, 0), "shin_r": (96, 0, 0), "foot_r": (-10, 0, 0)}, **down)
    kick_ = _pose(r, hips=(26, 0, 0), at=(0, -3, -3), spine=(8, 0, 0), chest=(6, 0, 0), neck=(-10, 0, 0), head=(-12, -28, 0), ears=1.0, feet=("l",),
                  legs={"thigh_r": (62, 0, 0), "shin_r": (8, 0, 0), "foot_r": (30, 0, 0)}, **down)
    held = _pose(r, hips=(27, 0, 0), at=(0, -3, -3), spine=(8, 0, 0), chest=(6, 0, 0), neck=(-10, 0, 0), head=(-12, -30, 0), ears=1.0, feet=("l",),
                 legs={"thigh_r": (66, 0, 0), "shin_r": (12, 0, 0), "foot_r": (34, 0, 0)}, **down)
    back = _pose(r, hips=(10, 0, 0), at=(0, -1, -1), chest=(2, 0, 0), head=(-4, -10, 0), ears=0.4, feet=("l",),
                 legs={"thigh_r": (10, 0, 0), "shin_r": (30, 0, 0), "foot_r": (-6, 0, 0)})
    return Move("kick", "LIGHT", 12, 4, 14, title="Back-kick", track=0.0, rng=(0, 3.6), kinds=("behind",), damage=1.0, knock=1.2, plant=(),
                hit=[("foot_r", (0, 2, 0), 3.5), ("shin_r", (0, 8, 0), 3.0)], lag=ON_TIME, through=_on(r, held, 0.04),
                stages={"windup": [(0.35, _mix(_pose(r), fold, 0.5)), (1.0, fold)], "active": [(0.5, kick_), (1.0, held)],
                        "recover": [(0.45, back), (0.75, _mix(back, _pose(r), 0.7))]})


def wake(r):
    """Its intro: it stands with its ear turned away from you, listening to something far off; the small ears on its
    shoulders turn one by one toward you; the ear comes round, slowly, and stops on you; it tilts; then it lowers itself
    into its stoop and spreads its long fingers."""
    away = _pose(r, hips=(0, 10, 0), chest=(-2, 12, 0), neck=(0, 20, 0), head=(-4, 50, 4), ears=-0.4)
    perk = _pose(r, hips=(0, 10, 0), chest=(-2, 12, 0), neck=(0, 20, 0), head=(-4, 48, 2), ears=1.0)
    come = _pose(r, hips=(0, 4, 0), chest=(-1, 4, 0), neck=(0, 8, 0), head=(-4, 16, 0), ears=1.0)
    on = _pose(r, hips=(2, 0, 0), chest=(2, 0, 0), neck=(2, 0, 0), head=(0, 0, 0), ears=1.0, face=1)
    tilt = _pose(r, hips=(2, 0, 0), chest=(2, 0, 0), neck=(3, 0, -4), head=(2, 0, -14), ears=1.0, face=1)
    low = _pose(r, hips=(6, 0, 0), at=(0, -2.5, -1), chest=(6, 0, 0), neck=(-2, 0, 0), head=(-6, 0, -4), ears=0.6, curl=(-0.3, -0.3), face=0.5,
                left=(24, 18, -18), right=(-24, 18, -18))
    mv = Move("wake", "NONE", 16, 32, 16, track=0.0, rng=(0, 99), kinds=("intro",), damage=0, knock=0, plant=(), lag=ON_TIME,
              through=_mix(low, _pose(r), 0.3),
              stages={"windup": [(0.5, _mix(_pose(r), away, 0.6)), (1.0, away)],
                      "active": [(0.2, perk), (0.45, come), (0.6, on), (0.68, tilt), (0.82, tilt), (1.0, low)],
                      "recover": _back(r, _mix(low, _pose(r), 0.3))})
    return mv


def moves(r):
    return [rake(r, "r", "rake"), rake(r, "l", "rake_b"), found(r), nosound(r), scream(r), ears(r), hush(r), deafening(r), kick(r), wake(r)]


def clips(r):
    plans.biped_locomotion(r, P, weight=1.4, temper="feral")
    idle(r)
    _stalk(r)
    reactions(r)
    mv = moves(r)
    by = {m.id: m for m in mv}
    for m in mv:
        stage_clips(r, m)
    for m in mv:
        if getattr(m, "_canal", None):
            _track(r, m, "canal", m._canal)
    for k, pts in by["ears"]._plucked.items():
        _track(r, by["ears"], f"ear{k}", pts)
    # its hands, fingers and ear stay out of the floor between the keys (the wrist turns first); broken and dying, its
    # feet tip at the ankle to lie on the floor, then the knee
    hands = [([f"hand_{s}", f"fingers_{s}", f"index_{s}"], [(f"hand_{s}", 0, 50), (f"forearm_{s}", 0, 30)]) for s in "lr"]
    ear = [(["head"], [("neck", 0, 30), ("head", 0, 30)]), (["veil_lo"], [("veil_lo", 0, 40, -1), ("veil", 0, 30, -1)])]
    core.ground_pass(r, hands + ear, clips=[n for n in r.clips if n != "death"])
    feet = [(["foot_" + s], [("foot_" + s, 0, 50), ("shin_" + s, 0, 30)]) for s in "lr"]
    core.ground_pass(r, feet, clips=["kneel", "rise", "run"])
    core.ground_pass(r, hands + ear + feet, clips=["death"], skip=())
    return mv


# Its sounds (SPEC §35.8): the warden's listening and its clicks, sculk, stone and cloth; chosen on paper, not heard in
# game.
SOUNDS = {
    "tell_light": [("entity.warden.tendril_clicks", 1.2, 0.8), ("block.sculk_sensor.clicking", 1.0, 0.9)],
    "tell_heavy": [("entity.warden.listening", 1.6, 0.7), ("entity.warden.tendril_clicks", 1.4, 0.6)],
    "tell_perilous": [("entity.warden.listening_angry", 2.0, 0.6), ("block.sculk_shrieker.shriek", 1.4, 0.5)],
    "swing_light": [("entity.player.attack.sweep", 1.0, 0.7), ("block.deepslate.step", 0.8, 0.8)],
    "swing_heavy": [("entity.player.attack.sweep", 1.4, 0.5), ("entity.warden.attack_impact", 1.0, 0.6)],
    "cast": [("entity.warden.sonic_charge", 1.6, 0.7), ("block.sculk_sensor.clicking", 1.0, 0.6)],
    "impact": [("item.mace.smash_ground_heavy", 1.6, 0.6), ("block.deepslate.break", 1.4, 0.6)],
    "roar": [("block.sculk_shrieker.shriek", 2.0, 0.5), ("entity.warden.sonic_boom", 1.2, 0.6)],
    "step": [("block.deepslate.step", 0.8, 0.6), ("entity.warden.step", 0.5, 0.8)],
}
MOVE_SOUNDS = {"nosound": {"swing": "cast", "impact": ""}, "scream": {"swing": "roar"}, "ears": {"swing": "cast", "impact": ""},
               "hush": {"swing": "cast", "impact": ""}, "deafening": {"swing": "roar"}, "wake": {"swing": "cast", "impact": ""}}

FIGHT = {
    "intro": {"pose": "idle", "move": "wake"},  # it stands listening to something far off, and hears you
    "phases": [
        {"pool": [
            {"move": "rake", "weight": 3, "when": "near", "then": [["rake_b"], ["rake_b", "found"]]},
            {"move": "found", "weight": 3, "then": [["rake"]]},
            {"move": "nosound", "weight": 1},
            {"move": "kick", "weight": 3, "when": "behind"},
        ]},
        # it hears your heartbeat, as in DeepBestiary: it screams and calls its ears
        {"transition": "ears", "pool": [
            {"move": "rake", "weight": 3, "when": "near", "then": [["rake_b"], ["rake_b", "found"]]},
            {"move": "found", "weight": 3, "then": [["rake"]]},
            {"move": "scream", "weight": 3, "when": "far"},
            {"move": "ears", "weight": 1},
            {"move": "nosound", "weight": 1},
            {"move": "kick", "weight": 3, "when": "behind"},
        ]},
        # silence: the hush, the deafening silence
        {"transition": "hush", "pool": [
            {"move": "deafening", "weight": 2},
            {"move": "hush", "weight": 2, "when": "far", "then": [["found"]]},
            {"move": "rake", "weight": 3, "when": "near", "then": [["rake_b", "found"]]},
            {"move": "found", "weight": 3},
            {"move": "scream", "weight": 2, "when": "far"},
            {"move": "kick", "weight": 3, "when": "behind"},
        ]},
    ],
    "enrage": {"below": 0.15, "speed": 1.25},
}


def design():
    r = body()
    r.pack()
    mv = clips(r)
    return {"rig": r, "plan": P, "moves": mv, "fight": FIGHT, "paint": paint, "scale": SCALE, "half": 0.9, "sounds": SOUNDS,
            "move_sounds": MOVE_SOUNDS}
