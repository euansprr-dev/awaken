"""The Sovereign of Machines (SPEC §35.11, docs/bosses/ROLLOUT.md): the Hollow Court's fourth Sovereign, a Brute. The
Armillary, picked from its silhouette round 2026-10-29: "It built until nothing was left to build with."

A hulking engine of pale blue enamel plates over a dark iron frame, its arms long and heavy as a great ape's, the
forearms great sleeved gauntlets on chrome rams that drive its clamp fists out a hand's length when it punches. It has
no head: on a short axle above its shoulders turns an armillary sphere, a gold core like a small sun with one dark lens
in it, and round it three brass rings, each on its own tilt, that rock to and fro like a clock's balance wheel and whirl
round in whole turns when it acts, and two small moons on long arms going round them. Three satellites ride docked on its back. Its waist is a turntable.

Moves (SPEC §35.11): it punches with one fist and then the other, the rams firing (LIGHT); Hydraulic Slam (HEAVY, its gap
closer: it crouches with both fists high, holds, and leaps at you, bringing them down together); Calculated Lance (it
plants its feet and points at you, and its three rings stop and turn to face you, lining up into a sight round the
lens; then the beam); Drones (it rolls its shoulders and the satellites lift off its back one after another); Orbital
Strike (it raises one open hand to the sky, its rings spinning up and its moons whirling, then brings it down to point
at you); Spark Lattice (it strikes its fists together twice like flints); for whoever is behind it, its waist turns
right round with its arms out; and Shutdown (the signature: everything stops, its rings slow and stand still, its core
dims, its arms drop and its sphere sags; then it jolts awake, its core blazing and its rings whipping round, and throws
its arms wide). Broken, it drops onto one knee and one fist, its rings slowing; it dies by shutting down, its rings
stopping one by one, and falls on its face.
"""
import math

from kit import core, ik, parts, plans
from kit.core import Move, keyed, stage_clips
import fauna_rig as R
from fauna_rig import Ramp, hexc, SIDES

ID = "sovereign_machines"
SCALE = 1.9  # the def's scale; the body is modelled at true size and the hitbox set round its body

# The Armillary (docs/bosses/ROLLOUT.md, A). Short heavy legs and a short waist under a great barrel of a chest bent a
# little forward, the arms hanging to its knees, the sphere on its axle above the shoulders. Value story: the gold core
# is the brightest thing on it, then the chrome of its rams; the enamel pale, the brass rings warm and middling, the
# frame between the plates nearly black.
P = plans.Biped(leg=28, thigh=14, foot=(14, 5, 15), pelvis=(24, 9, 16), spine=(16, 10, 12), chest=(36, 20, 22), neck=(8, 5, 8),
                head=(26, 26, 26), arm=(8, 16, 8), forearm=(13, 15, 13), hand=(7, 8, 12), stance=9, hunch=16, leg_w=(10, 9))
HUNCH = (6, 10)  # the waist's and the chest's rest pitch
REST_HAND = {"l": (23.0, 27.0, -5.0), "r": (-23.0, 27.0, -5.0)}  # the wrists at rest, hanging beside its knees
POLE = {"l": (0.5, 0.0, 1.0), "r": (-0.5, 0.0, 1.0)}  # elbows back and out
ORB = (0, -14, 0)  # the sphere's middle above the top of its axle (the head bone)
RINGS = (("a", 15, (62, 0, 0)), ("b", 11, (0, 56, 24)), ("c", 7, (34, -42, 10)))  # each ring: half its size, its tilt
MOONS = (("a", 21, (16, 0, -12)), ("b", 19, (-10, 0, 22)))  # each moon: how far out its arm reaches, its orbit's tilt
SATS = ((-10, -17, 11), (0, -17, 11), (10, -17, 11))  # where the satellites dock, in a row along its upper back
EXT = 6.0  # the furthest a ram drives its fist out along the forearm
CURL, REST_CURL = 80.0, 0.6  # a clamp's fingers turn this far closing (1), and stand this far closed at rest
ARMS = tuple(f"{b}_{s}" for b in ("shoulder", "arm", "forearm", "hand", "fingers", "rod") for s in "lr")
ON_TIME = {b: 0 for b in ARMS + tuple("tilt_" + k for k, _, _ in RINGS)}  # its arms and rings go where they are sent


def body():
    r = R.Rig(ID, tex=(512, 512))
    pel = r.bone("pelvis", (0, 24 - P.leg, 0))
    pel.cube("pelvis", -12, -5, -8, 24, 9, 16)
    pel.cube("hipplate", -9, -3, -9.5, 18, 6, 2)
    # short heavy legs: a plated thigh, a brass knee cap, the shin's ram in front, a wide flat foot with three toe plates
    for side, nm in ((1, "l"), (-1, "r")):
        mir = side < 0
        th = pel.bone("thigh_" + nm, (side * P.stance, 0, 0))
        th.cube("thigh_" + nm, -5, -1, -5, 10, 15, 10, mirror=mir)
        th.cube("knee_" + nm, -4, 10, -7, 8, 6, 3, mirror=mir)
        sn = th.bone("shin_" + nm, (0, P.thigh, 0))
        sn.cube("shin_" + nm, -4.5, 0, -2.5, 9, 9, 8, mirror=mir)
        sn.cube("calf_" + nm, -2, 1, -5, 4, 8, 3, mirror=mir)
        ft = sn.bone("foot_" + nm, (0, P.leg - P.thigh - P.foot[1], 0))
        ft.cube("foot_" + nm, -7, 0, -9, 14, 5, 15, mirror=mir)
        for k, x in enumerate((-7, -2, 3)):
            ft.cube(f"toe{k}_" + nm, x, 1, -13, 4, 4, 4, mirror=mir)
        ft.cube("heel_" + nm, -4, 1, 6, 8, 4, 2, mirror=mir)
    # the waist: a dark frame on a brass turntable, which can turn right round
    sp = pel.bone("spine", (0, -5, 0), (HUNCH[0], 0, 0))
    sp.cube("waist", -8, -10, -6, 16, 10, 12)
    sp.cube("turntable", -11, -3, -9, 22, 3, 18)
    # the chest: a great barrel of enamel, two plates over its front with the furnace grille glowing between them, a
    # lower plate, and on its back the rack where the satellites dock
    ch = sp.bone("chest", (0, -10, 0), (HUNCH[1], 0, 0))
    ch.cube("chest", -18, -20, -11, 36, 13, 22)
    ch.cube("ribs", -14, -7, -9, 28, 7, 18)
    ch.cube("plate_l", 2, -19, -13, 15, 11, 2)
    ch.cube("plate_r", -17, -19, -13, 15, 11, 2)
    ch.cube("grille", -2, -18, -12.5, 4, 10, 1)
    ch.cube("belly", -11, -7, -11, 22, 6, 2)
    ch.cube("rack", -14, -20, 10, 28, 6, 2)
    ch.cube("collar", -8, -22, -11, 16, 2, 16)
    # the crown it wears: brass prongs standing up round the collar, the sphere rising out of them
    for k, (x, z, h) in enumerate(((-8, -11, 5), (6, -11, 5), (-1, -12, 7), (-8, 3, 4), (6, 3, 4))):
        ch.cube(f"prong{k}", x, -22 - h, z, 2, h, 2)
    for k, (x, y, z) in enumerate(SATS):
        s = ch.bone(f"sat{k}", (x, y, z))
        s.cube(f"sat{k}", -2.5, -2.5, 0, 5, 5, 5)
        s.cube(f"panel{k}", -6, -0.5, 1.5, 12, 1, 2)
    # the arms: a great rounded pauldron with a brass cap, a dark upper arm, a brass elbow, a sleeved gauntlet of a
    # forearm with a chrome ram inside it (it shows at the wrist, and drives the fist out), and a clamp of a hand: three
    # thick fingers on one bone (they close) and a thumb
    for side, nm in ((1, "l"), (-1, "r")):
        mir = side < 0
        sh = ch.bone("shoulder_" + nm, (side * 20, -16, 0), (-sum(HUNCH), 0, 0))
        sh.cube("pauldron_" + nm, -7, -6, -7, 14, 10, 14, mirror=mir)
        sh.cube("pcap_" + nm, -5, -8, -5, 10, 2, 10, mirror=mir)
        a = sh.bone("arm_" + nm, (0, 3, 0))
        a.cube("arm_" + nm, -4, -1, -4, 8, 15, 8, mirror=mir)
        a.cube("elbow_" + nm, -5, 13, -5, 10, 5, 10, mirror=mir)
        f = a.bone("forearm_" + nm, (0, 16, 0))
        f.cube("fore_" + nm, -6.5, 1, -6.5, 13, 12, 13, mirror=mir)
        f.cube("cuff_" + nm, -7, 12, -7, 14, 4, 14, mirror=mir)
        rd = f.bone("rod_" + nm, (0, 18, 0))
        rd.cube("rod_" + nm, -2.5, -6, -2.5, 5, 9, 5, mirror=mir)
        h = f.bone("hand_" + nm, (0, 18, 0))
        h.cube("hand_" + nm, -3.5, 0, -6, 7, 8, 12, mirror=mir)
        h.cube("knuck_" + nm, -4, 5, -6.5, 8, 4, 13, mirror=mir)
        h.cube("thumb_" + nm, -5 if side > 0 else 2, 1, -8, 3, 6, 3, mirror=mir)
        g = h.bone("fingers_" + nm, (0, 8, 0), (0, 0, -side * CURL * REST_CURL))
        for k, z in enumerate((-5.5, -1.5, 2.5)):
            g.cube(f"finger{k}_" + nm, -2, 0, z, 4, 7, 3, mirror=mir)
    # the axle and on it the sphere: the gold core with its lens, three brass rings each on its own tilt (the ring's
    # bone spins it in its own plane), and two moons on long arms going round them
    nk = ch.bone("neck", (0, -20, -3), (-sum(HUNCH), 0, 0))
    nk.cube("neck", -4, -5, -4, 8, 5, 8)
    hd = nk.bone("head", (0, -5, 0))
    hd.cube("axle", -1.5, -10, -1.5, 3, 10, 3)
    hd.cube("hub", -3, -3, -3, 6, 3, 6)
    orb = hd.bone("orb", ORB)
    # the core and the lens are bones of their own so each can swell and dim (the lance, the shutdown, its death)
    orb.bone("core", (0, 0, 0)).cube("core", -4, -4, -4, 8, 8, 8)
    orb.bone("lens", (0, 0, -4)).cube("lens", -2, -2, -0.5, 4, 4, 1)
    for k, h_, tilt in RINGS:
        t = orb.bone("tilt_" + k, (0, 0, 0), tilt)
        g = t.bone("ring_" + k, (0, 0, 0))
        n, d = 2 * (h_ - 3), (2 if k == "a" else 1)
        g.cube(f"ring{k}0", -(h_ - 3), -h_, -d / 2, n, 2, d)
        g.cube(f"ring{k}1", -(h_ - 3), h_ - 2, -d / 2, n, 2, d)
        g.cube(f"ring{k}2", -h_, -(h_ - 3), -d / 2, 2, n, d)
        g.cube(f"ring{k}3", h_ - 2, -(h_ - 3), -d / 2, 2, n, d)
    for k, reach, tilt in MOONS:
        o = orb.bone("orbit_" + k, (0, 0, 0), tilt)
        m = o.bone("moon_" + k, (0, 0, 0))
        sg = 1 if k == "a" else -1
        m.cube("marm_" + k, 0 if sg > 0 else -reach, -0.5, -0.5, reach, 1, 1)
        m.cube("moon_" + k, sg * reach - 2, -2, -2, 4, 4, 4)
    r.look = [("head", 0.6, 0.4), ("neck", 0.3, 0.25)]
    r.size = P.leg / 36.0
    r.shadow = 2.4
    for side in "lr":
        ik.rest_limb(r, "arm_" + side, "forearm_" + side, "hand_" + side, REST_HAND[side], POLE[side])
    # secondary parts (SPEC §35.10): the satellites ride a little loose in their cradles
    r.springs = [(f"sat{k}", 0.4, 2.4, 0.1) for k in range(len(SATS))]
    r.guard = ["hand_l", "hand_r", "fingers_l", "fingers_r", "head"]  # bones, not cubes
    return r


# ---------------------------------------------------------------------------------------------------------------------
# Paint

ENAMEL = Ramp("#0e1420", "#152034", "#1c2b45", "#243756", "#2d4468", "#38527a", "#44618c", "#52719d", "#6483ad", "#7a97bf", "#93add0",
              "#b0c6e2")
FRAME = Ramp("#06070a", "#0d0f13", "#15181e", "#1e2229", "#282d35", "#333942", "#40474f", "#4f5660")
BRASS = Ramp("#1e1206", "#33200c", "#4b3113", "#66441a", "#835a22", "#a1722c", "#bd8c3a", "#d6a84f", "#ecc76e", "#f8e29a")
CHROME = Ramp("#22252b", "#3a3f48", "#5a606b", "#80868f", "#a8adb5", "#cfd3d8", "#eef0f2", "#ffffff")
GOLD = ("#fff6d0", "#ffd060", "#e8a838", "#a86c1c")
PLATES = ("hipplate", "plate_l", "plate_r", "belly", "chest")


def _seams(P_, c, step=8, base=None):
    """Panel lines on an enamel cube's sides: a dark seam every step pixels with a rivet either side of it."""
    for fname in SIDES + ("top",):
        f = P_.face(c, fname)
        for i, j in f.each():
            if f.w > step and i % step == step - 1:
                f.set(i, j, ENAMEL.at(1.6))
            elif f.w > 2 * step and i % step in (0, step - 2) and j % 8 == 3:
                f.set(i, j, ENAMEL.at(10.6))


def paint(r, P_):
    """Pale blue enamel plates, panelled and riveted, worn darker toward their edges, over a frame of nearly black
    iron; brass at the knees, elbows, caps, the turntable and the rings, its rings marked like dials; chrome rams; and
    the light is gold: the core, the furnace grille between its chest plates, a seam along each plate's inner edge, the
    satellites' eyes and the inner ring's marks."""
    of = lambda *ps: parts.cubes_of(r, *ps)
    enamel = ["pelvis", "hipplate", "chest", "ribs", "plate_l", "plate_r", "belly"] + of("thigh_", "foot_", "toe", "pauldron_", "fore_", "sat")
    for c in enamel:
        P_.paint(c, ENAMEL, 6.4, None, grad=1.2, rim=1.0, ao=1.1, noise=0.22, nscale=5)
        # the paint chipped to bare steel along its edges
        for fname in SIDES + ("top",):
            f = P_.face(c, fname)
            for i, j in f.each():
                edge = i in (0, f.w - 1) or j in (0, f.h - 1)
                if edge and j == 0 and R._h(c, fname, i, j) % 5 == 0:
                    f.set(i, j, CHROME.at(4.8))
    for c in ("chest", "pelvis") + tuple(of("thigh_", "fore_", "pauldron_")):
        _seams(P_, c)
    frame = ["waist", "rack", "neck", "axle", "hub"] + of("shin_", "heel_", "arm_", "hand_", "finger", "thumb_", "cuff_", "panel", "marm_")
    for c in frame:
        P_.paint(c, FRAME, 4.4, None, grad=1.0, rim=1.2, ao=0.9, noise=0.4, nscale=4)
    brass = ["turntable", "collar"] + of("knee_", "pcap_", "elbow_", "knuck_", "prong")
    for c in brass:
        P_.paint(c, BRASS, 6.2, None, grad=1.0, rim=1.4, ao=0.6, noise=0.3, nscale=3)
    for c in of("ringa", "ringb", "ringc"):  # older bronze than the rest, so the core shines out of them
        P_.paint(c, BRASS, 4.4, None, grad=0.8, rim=1.4, ao=0.4, noise=0.2, nscale=3)
    for c in of("calf_", "rod_"):
        P_.paint(c, CHROME, 4.6, None, grad=0.6, rim=1.6, ao=0.3)
        for fname in SIDES:
            f = P_.face(c, fname)
            for i, j in f.each():
                f.set(i, j, CHROME.at(7.0 if i == 1 else (1.2 if i in (0, f.w - 1) else 4.4 + 0.3 * (j % 2))))
    for c in of("prong"):  # the crown's points catch the light
        f = P_.face(c, "top")
        for i, j in f.each():
            f.set(i, j, BRASS.at(9.4))
    # the solar panels: dark glass in fine cells
    for c in of("panel"):
        f = P_.face(c, "top")
        for i, j in f.each():
            f.set(i, j, hexc("#1a2a44" if i % 3 else "#5a6a88", 255))
    # the rings: brass marked like dials, a dark tick every other pixel along their faces; the inner ring's ticks glow
    for k, _, _ in RINGS:
        for q in range(4):
            c = f"ring{k}{q}"
            for fname in ("north", "south"):
                f, g = P_.face(c, fname), P_.face(c, fname, glow=True)
                for i, j in f.each():
                    long_ = max(f.w, f.h)
                    u = i if f.w >= f.h else j
                    if u % 2 == 0 and 0 < u < long_ - 1:
                        if k == "c":
                            f.set(i, j, hexc(GOLD[1], 255))
                            g.set(i, j, hexc(GOLD[1], 220))
                        else:
                            f.set(i, j, BRASS.at(8.2))
    # the core: a small sun, gold all over and glowing, brighter to its middle; the lens dark glass with a white-gold
    # pupil
    for fname in SIDES + ("top", "bottom"):
        f, g = P_.face("core", fname), P_.face("core", fname, glow=True)
        for i, j in f.each():
            d = math.hypot(i - (f.w - 1) / 2, j - (f.h - 1) / 2)
            col = GOLD[0] if d < 2.2 else (GOLD[1] if d < 3.4 else GOLD[2])
            f.set(i, j, hexc(col, 255))
            g.set(i, j, hexc(col, 235))
    f, g = P_.face("lens", "north"), P_.face("lens", "north", glow=True)
    for i, j in f.each():
        mid = i in (1, 2) and j in (1, 2)
        f.set(i, j, hexc("#fffbe8" if mid else "#120c06", 255))
        if mid:
            g.set(i, j, hexc("#ffffff", 255))
    for fname in ("west", "east", "top", "bottom"):
        f = P_.face("lens", fname)
        for i, j in f.each():
            f.set(i, j, hexc("#120c06", 255))
    # the furnace grille: gold light between dark slats
    f, g = P_.face("grille", "north"), P_.face("grille", "north", glow=True)
    for i, j in f.each():
        lit = j % 3 != 2
        f.set(i, j, hexc(GOLD[1] if lit else "#1a1208", 255))
        if lit:
            g.set(i, j, hexc(GOLD[1 if j % 3 == 0 else 2], 230))
    # a gold seam along each chest plate's inner edge, and the satellites' eyes
    for c, edge in (("plate_l", 0), ("plate_r", None)):
        f, g = P_.face(c, "north"), P_.face(c, "north", glow=True)
        i = 0 if edge == 0 else f.w - 1
        for j in range(f.h):
            f.set(i, j, hexc(GOLD[2], 255))
            g.set(i, j, hexc(GOLD[2], 200))
    for k in range(len(SATS)):
        f, g = P_.face(f"sat{k}", "south"), P_.face(f"sat{k}", "south", glow=True)
        for i, j in f.each():
            if i == 2 and j == 2:
                f.set(i, j, hexc(GOLD[0], 255))
                g.set(i, j, hexc(GOLD[1], 240))
    # the moons: pale, one lit gold on its face
    for k, _, _ in MOONS:
        P_.paint("moon_" + k, ENAMEL, 9.4, None, grad=0.8, rim=0.6, ao=0.4)
        f, g = P_.face("moon_" + k, "north"), P_.face("moon_" + k, "north", glow=True)
        for i, j in f.each():
            if i in (1, 2) and j in (1, 2):
                f.set(i, j, hexc(GOLD[1], 255))
                g.set(i, j, hexc(GOLD[1], 230))
    return P_.skin, P_.glow


# ---------------------------------------------------------------------------------------------------------------------
# Posing

TILTS = {"tilt_" + k: t for k, _, t in RINGS}
DIR = {"ring_a": 1, "ring_b": -1, "ring_c": 1, "moon_a": 1, "moon_b": -1}  # which way each turns
AXIS = {"ring_a": 2, "ring_b": 2, "ring_c": 2, "moon_a": 1, "moon_b": 1}  # rings turn in their own planes, moons round
RINGS_, MOONS_ = ("ring_a", "ring_b", "ring_c"), ("moon_a", "moon_b")


def _pose(r, hips=(0, 0, 0), at=(0, 0, 0), spine=(0, 0, 0), chest=(0, 0, 0), neck=(0, 0, 0), head=(0, 0, 0), left=None, right=None, lhand=None,
          rhand=None, legs=None, feet=("l", "r"), step=None, curl=(0.6, 0.6), ext=(0.0, 0.0), sight=0.0, more=None):
    """A whole-body key. It bends at the hips (hips: turn, at: x, up, z), the waist (spine), the chest, the axle (neck)
    and the sphere (head). The feet stay planted where they stand at rest (step: {side: (dx, dy, dz)} model pixels, y
    down) unless legs pose them. left, right: where that wrist goes, (x, up, z) model pixels or ("on", bone, point) a
    point on a bone (left alone, the arm hangs as at rest); lhand, rhand: (ydir, xdir) for that hand in model space.
    curl (left, right) closes the clamp (1) or spreads it (below 0); ext (left, right) drives that fist out along its
    forearm on its ram (0..1 of EXT); sight (0..1) turns the three rings to face forward round the lens, lined up."""
    p = {"pelvis": {"rot": hips, "pos": at}, "spine": spine, "chest": chest, "neck": neck, "head": head}
    for b, t in TILTS.items():
        p[b] = tuple(-v * sight for v in t)
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
        sg = 1 if side == "l" else -1
        p["fingers_" + side] = (0, 0, -sg * CURL * (curl[k] - REST_CURL))
        e = EXT * ext[k]
        h = p.get("hand_" + side, (0, 0, 0))
        p["hand_" + side] = {"rot": h["rot"] if isinstance(h, dict) else h, "pos": (0, -e, 0)}
        p["rod_" + side] = {"rot": (0, 0, 0), "pos": (0, -e / 2, 0)}
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
    """Down on its right knee, the shin flat behind and the foot on its toes (the left foot is planted ahead, KNEE_STEP)."""
    return {"thigh_r": (8, 0, 4), "shin_r": (88, 0, 0), "foot_r": (60, 0, 0)}


KNEE_STEP = {"l": (0, 0, -8)}


def _knees():
    """Down on both knees, the shins flat behind and the feet on their toes."""
    return {"thigh_l": (-4, 0, -3), "shin_l": (88, 0, 0), "foot_l": (76, 0, 0), "thigh_r": (-4, 0, 3), "shin_r": (88, 0, 0), "foot_r": (76, 0, 0)}


def _laid(r, **kw):
    """A key lying on the floor: the body set down so its lowest part rests on the floor."""
    p = _pose(r, **kw)
    fr = core.frames(r, core.as_sample(p))
    low = core.lowest(r, fr, [b.name for b in r.bones if b.cubes and not b.name.startswith(("orbit", "moon", "tilt", "ring", "sat"))])
    at = kw.get("at", (0, 0, 0))
    return _pose(r, **{**kw, "at": (at[0], at[1] + (low - 24.0) - 0.3, at[2])})


def _ticks(r, clip, flicks):
    """In a move the rings and moons turn like clockwork: each whirls round a whole number of turns in one smooth flick
    and is set back to where it started the instant the flick ends (a whole turn is no turn), so the move layer adds
    nothing to them between flicks and ends on nothing (it plays at full weight, so the flick is never cut short).
    flicks: [(bone, or "rings", "moons" or "all", start seconds, how long, turns)]."""
    c = r.clips[clip]
    L = c.length
    by = {}
    for who, t0, dur, turns in flicks:
        names = {"rings": RINGS_, "moons": MOONS_, "all": RINGS_ + MOONS_}.get(who, (who,))
        for b in names:
            by.setdefault(b, []).append((t0, min(dur, L - t0), turns))
    for b, fl in by.items():
        keys = [(0.0, 0.0)]
        for t0, dur, turns in sorted(fl):
            keys.append((t0, 0.0))
            n = max(2, int(math.ceil(dur * 40)))
            for k in range(1, n + 1):
                u = k / n
                keys.append((t0 + dur * u, 360.0 * turns * DIR[b] * (u * u * u * (u * (6 * u - 15) + 10))))
            if t0 + dur < L - 0.002:
                keys.append((t0 + dur + 0.001, 0.0))
        keys.append((L, keys[-1][1]))
        out, last = [], -1.0
        for t, v in keys:
            if t > last + 5e-4:
                rot = [0.0, 0.0, 0.0]
                rot[AXIS[b]] = v
                out.append((t, tuple(rot), "lin"))
                last = t
        c.tracks[(b, "rot")] = out


def _rock(r, clip, swings):
    """Under everything (the idle plays beneath every move and, lighter, beneath the walk) the rings rock to and fro
    like a clock's balance wheel and the moons swing round and back: [(bone, degrees either way, period seconds,
    phase 0..1)], each period dividing the clip so it loops."""
    c = r.clips[clip]
    L = c.length
    for b, amp, per, ph in swings:
        n = int(round(L / per * 8))
        keys = []
        for k in range(n + 1):
            t = L * k / n
            rot = [0.0, 0.0, 0.0]
            rot[AXIS[b]] = DIR[b] * amp * math.sin(2 * math.pi * (t / per + ph))
            keys.append((t, tuple(rot), "cat"))
        c.tracks[(b, "rot")] = keys


def _scales(r, clip, bone, pts, interp="cat", ends=True):
    """A clip's scale keys for one bone: [(seconds, scale)], 1 at both ends (ends False: only at the start)."""
    c = r.clips[clip]
    ks = dict([(0.0, 1.0)] + ([(c.length, 1.0)] if ends else []))
    ks.update((round(t, 4), v) for t, v in pts)  # a key given at an end wins over the default there
    ks = list(ks.items())
    c.scale(bone, [(t, v, v, v) for t, v in sorted(ks)], interp)


def _staged(mv, stage, f):
    W, A = mv.seconds("windup"), mv.seconds("active")
    return {"windup": 0.0, "active": W, "recover": W + A}[stage] + f * mv.seconds(stage)


def _gait(u, swing=20.0, bend=34.0):
    """A heavy flat-footed stride at phase u (0..1, the left foot planted first): thigh, knee and foot turns."""
    s, c = math.sin(2 * math.pi * u), math.cos(2 * math.pi * u)
    lk, rk = max(0.0, c) * bend + 4, max(0.0, -c) * bend + 4
    return {"thigh_l": (-swing * s - lk * 0.45, 0, 0), "shin_l": (lk, 0, 0), "foot_l": (swing * s - lk * 0.55, 0, 0),
            "thigh_r": (swing * s - rk * 0.45, 0, 0), "shin_r": (rk, 0, 0), "foot_r": (-swing * s - rk * 0.55, 0, 0)}


# ---------------------------------------------------------------------------------------------------------------------
# Clips

def idle(r):
    """It stands heavy and still, working: its chest pumps like a slow engine; its sphere turns to one side in a quick
    mechanical sweep and stops dead, holds, sweeps to the other side and stops, and comes back; one clamp closes and
    opens as if testing its grip, then the other; a ram twitches out and back with a hiss. Its rings and moons never
rest: they rock to and fro like a clock's balance wheel."""
    k0 = _pose(r)
    pump = _pose(r, at=(0, 0.6, 0), spine=(-0.8, 0, 0), chest=(-1.6, 0, 0), curl=(0.62, 0.62))
    look_r = _pose(r, hips=(0, -1, 0), chest=(0, -3, 0), head=(0, -30, 0))
    over_r = _pose(r, hips=(0, -1, 0), chest=(0, -3, 0), head=(0, -34, 0))
    look_l = _pose(r, hips=(0, 1.5, 0), chest=(0, 4, 0), head=(0, 28, -2))
    over_l = _pose(r, hips=(0, 1.5, 0), chest=(0, 4, 0), head=(0, 32, -2))
    grip_l = _pose(r, hips=(0, 1.5, 0), chest=(0, 4, 0), head=(0, 28, -2), curl=(1.1, 0.6))
    grip_r = _pose(r, chest=(-0.5, 1, 0), head=(0, 8, 0), curl=(0.5, 1.1))
    twitch = _pose(r, chest=(-0.5, 0, 0), curl=(0.6, 0.9), ext=(0.0, 0.45))
    keyed(r, "idle", 6.0, [(0, k0), (0.6, pump), (1.4, k0), (1.85, over_r), (2.0, look_r), (2.7, look_r), (2.9, _mix(look_r, k0, 0.5)), (3.2, over_l),
                           (3.35, look_l), (3.75, grip_l), (4.05, look_l), (4.35, _mix(look_l, k0, 0.6)), (4.6, grip_r), (4.9, k0), (5.15, twitch),
                           (5.35, k0), (6.0, k0)], loop=True)
    _rock(r, "idle", [("ring_c", 50, 2.0, 0.0), ("ring_b", 38, 3.0, 0.3), ("ring_a", 26, 6.0, 0.6), ("moon_a", 110, 6.0, 0.1), ("moon_b", 90, 3.0, 0.55)])


def _stomp(r):
    """The walk, rewritten as a stomp: heavy flat-footed strides, the body dropping a little onto each foot as it lands
    and the chest jolting with it; the long arms swing a little and late; the sphere stays level and square to the way
    it goes (its axle turns against the body's sway); its rings rock with the idle's beneath it (the walk keys none)."""
    L, n, keys = 1.6, 24, []
    for k in range(n + 1):
        u = k / n
        s = math.sin(2 * math.pi * u)
        land = max(0.0, math.cos(4 * math.pi * (u - 0.04))) ** 3
        p = _pose(r, hips=(1, 5 * s, -3 * s), at=(0, -1.4 * land + 0.4, 0), spine=(0, -3 * s, 0), chest=(1 + 2.2 * land, -4 * s, 2 * s),
                  neck=(-1.5 * land, 6 * s, -1.5 * s), head=(-0.8 * land, 6 * s, 0), legs=_gait(u), feet=(), curl=(0.7, 0.7))
        p.update({"arm_l": (10 * math.sin(2 * math.pi * u - 0.5) + 2, 0, -2), "arm_r": (-10 * math.sin(2 * math.pi * u - 0.5) + 2, 0, 2),
                  "forearm_l": (-6 - 6 * max(0, s), 0, 0), "forearm_r": (-6 - 6 * max(0, -s), 0, 0)})
        keys.append((L * u, p))
    keyed(r, "walk", L, keys, loop=True)

    L, keys = 0.9, []
    for k in range(n + 1):
        u = k / n
        s = math.sin(2 * math.pi * u)
        land = max(0.0, math.cos(4 * math.pi * (u - 0.05))) ** 3
        p = _pose(r, hips=(10, 7 * s, -3 * s), at=(0, -2.4 * land + 0.6, 0), spine=(4, -4 * s, 0), chest=(8 + 3 * land, -7 * s, 2 * s),
                  neck=(-10, 8 * s, 0), head=(-6 - land, 8 * s, 0), legs=_gait(u, swing=30, bend=52), feet=(), curl=(1.0, 1.0))
        p.update({"arm_l": (26 * math.sin(2 * math.pi * u - 0.4) - 12, 0, -6), "arm_r": (-26 * math.sin(2 * math.pi * u - 0.4) - 12, 0, 6),
                  "forearm_l": (-30 - 10 * max(0, s), 0, 0), "forearm_r": (-30 - 10 * max(0, -s), 0, 0)})
        keys.append((L * u, p))
    keyed(r, "run", L, keys, loop=True)

    r.walk_stride = 2.0
    r.run_stride = 3.4


def reactions(r):
    """Struck, it jolts back, its sphere jerking and its rings jarring, its arms flinching up. Broken, it drops onto its
    right knee and its right fist, its sphere sagging and its rings slowing; it rises with a push off the fist. Its
    death is a shutdown: its rings slow and stop one after another and its core dims, its arms drop, its legs give way
    under it onto both knees, it sags and tips forward and falls on its face, and its rings close round the lens and lie
    flat on the floor."""
    hit = _pose(r, hips=(-4, 0, 0), at=(0, 0, 1.5), spine=(-3, 0, 0), chest=(-9, 0, 0), neck=(-8, 0, 0), head=(-14, 0, 8), curl=(1, 1),
                left=(26, 40, -14), right=(-26, 40, -14))
    back = _pose(r, chest=(3, 0, 0), head=(4, 0, -3))
    keyed(r, "hurt", 0.5, [(0, _pose(r)), (0.07, hit), (0.28, back), (0.5, _pose(r))])
    _ticks(r, "hurt", [("ring_c", 0.0, 0.46, 1)])
    for nm, sgn in (("hurt_l", 1), ("hurt_r", -1)):
        h = _pose(r, hips=(0, -8 * sgn, -3 * sgn), at=(-sgn * 1.0, 0, 0), chest=(-4, -12 * sgn, -8 * sgn), neck=(0, -8 * sgn, 0), head=(-6, -18 * sgn, -14 * sgn),
                  curl=(1, 1))
        b = _pose(r, chest=(1, 4 * sgn, 2 * sgn), head=(2, 6 * sgn, 4 * sgn))
        keyed(r, nm, 0.55, [(0, _pose(r)), (0.07, h), (0.3, b), (0.55, _pose(r))])
        _ticks(r, nm, [("ring_b", 0.02, 0.5, 1)])
    kneel = _pose(r, hips=(10, 0, 0), at=(0, -10, 1), legs=_knee(), feet=("l",), step=KNEE_STEP, spine=(6, 0, 0), chest=(12, 6, -4), neck=(14, 0, 0),
                  head=(16, -6, -10), right=(-20, 6, -20), rhand=((0, 1, -0.1), (1, 0, 0)), curl=(0.7, 1.0))
    sag = _pose(r, hips=(4, 0, 0), at=(0, -4, 0), chest=(6, 0, 0), neck=(6, 0, 0), head=(8, 0, 0))
    # the right foot lifts back off the floor before the knee goes down, so it never sweeps through the floor
    lift = _pose(r, hips=(7, 0, 0), at=(0, -7, 0.5), step={"l": (0, 0, -5), "r": (0, -12, 12)}, chest=(9, 3, -2), neck=(10, 0, 0), head=(12, -3, -5),
                 right=(-22, 16, -16), curl=(0.7, 0.9))
    keyed(r, "kneel", 0.65, [(0, _pose(r)), (0.2, sag), (0.4, lift), (0.65, kneel)])

    push = _pose(r, hips=(8, 0, 0), at=(0, -6, 0.5), chest=(9, 3, -2), neck=(6, 0, 0), head=(6, 0, 0), right=(-20, 10, -18), rhand=((0, 1, -0.1), (1, 0, 0)),
                 curl=(0.7, 1.0))
    keyed(r, "rise", 0.9, [(0, kneel), (0.45, push), (0.9, _pose(r))])
    _ticks(r, "rise", [("ring_c", 0.3, 0.5, 1)])
    dead = dict(curl=(0.1, 0.1), left=(24, 18, -2), right=(-24, 18, -2))
    stop = _pose(r, at=(0, -0.5, 0), chest=(2, 0, 0), neck=(4, 0, 0), head=(6, 0, 0), curl=(0.4, 0.4))
    slump = _pose(r, hips=(4, 0, 0), at=(0, -3, 0.5), spine=(4, 0, 0), chest=(10, 0, 0), neck=(14, 0, 0), head=(18, 0, 6), **dead)
    knees = _pose(r, hips=(-2, 0, 0), at=(0, -8, 2), legs=_knees(), feet=(), spine=(4, 0, 0), chest=(12, 0, 0), neck=(16, 0, 0), head=(20, 0, 8), **dead)
    # its legs give way: both feet fold up off the floor as it drops, so the shins swing back clear of it onto the knees
    buckle = _pose(r, hips=(1, 0, 0), at=(0, -4, 1), step={"l": (0, -15, 8), "r": (0, -15, 8)}, spine=(4, 0, 0), chest=(11, 0, 0), neck=(15, 0, 0),
                   head=(19, 0, 7), **dead)
    tip = _pose(r, hips=(26, 0, 2), at=(0, -9, -4), legs={**_knees(), "thigh_l": (-24, 0, -3), "thigh_r": (-24, 0, 3)}, feet=(), spine=(8, 0, 0),
                chest=(14, 0, 2), neck=(16, 0, 0), head=(20, 0, 10), curl=(0.1, 0.1), left=(28, 18, -2), right=(-28, 18, -2))
    # face down: the chest on the floor, the sphere settled just off it and its three rings closed into the lens's plane,
    # which lies flat when the sphere faces the floor (the big ring is wider than the sphere and would dig in otherwise)
    down = _laid(r, hips=(84, 0, 4), at=(0, 0, -18), legs={"thigh_l": (-6, 0, -6), "shin_l": (4, 0, 0), "foot_l": (30, 0, 0), "thigh_r": (-2, 0, 8),
                                                           "shin_r": (0, 0, 0), "foot_r": (30, 0, 0)}, feet=(),
                 spine=(2, 0, 2), chest=(2, 0, 0), neck=(30, 0, 0), head=(-20, 0, 14), curl=(0.1, 0.1), sight=1.0,
                 more={"arm_l": (0, 0, -16), "forearm_l": (0, 0, 0), "arm_r": (-2, 0, 20), "forearm_r": (-4, 0, 0)})
    # it lands with all its weight still going, so the curve carries it a little past the landing key: key that high
    hp = down["pelvis"]["pos"]
    land = {**down, "pelvis": {"rot": down["pelvis"]["rot"], "pos": (hp[0], hp[1] + 1.5, hp[2])}}
    keyed(r, "death", 4.6, [(0, _pose(r)), (0.5, stop), (1.3, slump), (1.7, buckle), (2.0, knees), (2.6, _mix(knees, tip, 0.35)), (3.2, tip), (3.75, land),
                            (3.95, _mix(down, tip, 0.06)), (4.6, down)])
    _ticks(r, "death", [("ring_c", 0.2, 0.9, 1), ("moon_a", 0.3, 2.4, 1), ("ring_b", 0.6, 1.3, 1), ("moon_b", 0.8, 2.2, 1), ("ring_a", 1.1, 1.6, 1)])
    _scales(r, "death", "core", [(0.4, 1.0), (1.2, 1.12), (1.4, 0.8), (3.0, 0.55), (4.6, 0.55)], ends=False)


# ---------------------------------------------------------------------------------------------------------------------
# Moves

def punch(r, side="r", mid="punch"):
    """The Piston Punch: it twists away and draws one clamp back beside its chest, the ram sucking the fist in; then it
    twists through and punches straight out, the ram firing the fist out a hand's length past the cuff (LIGHT)."""
    s = 1 if side == "l" else -1
    hand, off = ("left", "right") if side == "l" else ("right", "left")
    e = (lambda v: (v, 0.0)) if side == "l" else (lambda v: (0.0, v))
    cock = _pose(r, hips=(-2, 12 * s, 0), at=(0, -0.5, 1), spine=(-2, 6 * s, 0), chest=(-6, 22 * s, -3 * s), head=(-2, -24 * s, 0),
                 **{hand: (s * 16, 50, 6), off: (-s * 14, 40, -26)}, curl=(1, 1))
    gather = _mix(_pose(r), cock, 0.4)
    drive = _pose(r, hips=(3, -4 * s, 0), at=(0, -1, -1), spine=(2, -4 * s, 0), chest=(4, -8 * s, 1 * s), head=(0, 6 * s, 0),
                  **{hand: (s * 6, 52, -24), off: (-s * 18, 37, -14)}, curl=(1, 1), ext=e(0.4))
    hit = _pose(r, hips=(5, -10 * s, 0), at=(0, -1.5, -1.5), spine=(3, -8 * s, 0), chest=(7, -18 * s, 3 * s), head=(0, 16 * s, 0),
                **{hand: (s * 1, 52, -32), off: (-s * 22, 32, 0)}, curl=(1, 1), ext=e(1.0))
    held = _pose(r, hips=(6, -11 * s, 0), at=(0, -1.8, -1.6), spine=(3, -9 * s, 0), chest=(8, -20 * s, 3 * s), head=(0, 18 * s, 0),
                 **{hand: (s * 0, 50, -32), off: (-s * 22, 31, 1)}, curl=(1, 1), ext=e(0.8))
    return Move(mid, "LIGHT", 10, 4, 14, title="Piston Punch", track=8.0, rng=(0, 4.6), kinds=("combo", "near"), damage=1.0, knock=0.8, plant=(),
                hit=[("hand_" + side, (0, 8, 0), 4.0), ("fingers_" + side, (0, 6, 0), 3.5)], lag=ON_TIME, through=_on(r, held, 0.04),
                stages={"windup": [(0.3, gather), (1.0, cock)], "active": [(0.3, drive), (0.55, hit), (1.0, held)], "recover": _back(r, held)})


def slam(r):
    """Hydraulic Slam (HEAVY, its gap closer): it crouches with its fists swung back, its rams sucking in; it rises with
    both fists high over its sphere and holds; then it leaps at you and brings them down together where you stand, the
    rams firing into the floor, its knees giving."""
    crouch = _pose(r, hips=(12, 0, 0), at=(0, -6, 1), spine=(6, 0, 0), chest=(12, 0, 0), neck=(-6, 0, 0), head=(-8, 0, 0), left=(22, 26, 14), right=(-22, 26, 14),
                   curl=(1, 1))
    up = _pose(r, hips=(-6, 0, 0), at=(0, 2, 1), spine=(-6, 0, 0), chest=(-16, 0, 0), neck=(-4, 0, 0), head=(-8, 0, 0), left=(9, 86, 0), right=(-9, 86, 0),
               lhand=((0, -0.4, -0.9), (1, 0, 0)), rhand=((0, -0.4, -0.9), (1, 0, 0)), curl=(1, 1))
    held = _pose(r, hips=(-7, 0, 0), at=(0, 2.4, 1.2), spine=(-7, 0, 0), chest=(-18, 0, 0), neck=(-5, 0, 0), head=(-10, 0, 0), left=(9, 88, 1), right=(-9, 88, 1),
                 lhand=((0, -0.4, -0.9), (1, 0, 0)), rhand=((0, -0.4, -0.9), (1, 0, 0)), curl=(1, 1))
    lunge = {"l": (0, 0, -10), "r": (0, 0, 4)}
    smash = _pose(r, hips=(18, 0, 0), at=(0, -9, -3), spine=(10, 0, 0), chest=(18, 0, 0), neck=(-14, 0, 0), head=(-10, 0, 0), step=lunge,
                  left=(8, 12, -32), right=(-8, 12, -32), lhand=((0, 0.9, -0.4), (1, 0, 0)), rhand=((0, 0.9, -0.4), (1, 0, 0)), curl=(1, 1), ext=(1, 1))
    after = _pose(r, hips=(19, 0, 0), at=(0, -9.6, -3.2), spine=(11, 0, 0), chest=(19, 0, 0), neck=(-15, 0, 0), head=(-10, 0, 0), step=lunge,
                  left=(8.5, 11, -33), right=(-8.5, 11, -33), lhand=((0, 0.95, -0.3), (1, 0, 0)), rhand=((0, 0.95, -0.3), (1, 0, 0)), curl=(1, 1), ext=(0.7, 0.7))
    lift = _pose(r, hips=(10, 0, 0), at=(0, -5, -1.5), spine=(6, 0, 0), chest=(10, 0, 0), neck=(-6, 0, 0), head=(-4, 0, 0), step={"l": (0, 0, -5), "r": (0, 0, 2)},
                 left=(14, 22, -26), right=(-14, 22, -26), curl=(0.9, 0.9), ext=(0.2, 0.2))
    mv = Move("slam", "HEAVY", 24, 6, 24, title="Hydraulic Slam", hold=10, track=5.0, advance=6.0, ability="Hydraulic Slam", rng=(3, 10), kinds=("gap", "area"),
              damage=1.5, knock=1.4, plant=(), disc=4.5, disc_at="hit", hit=[("hand_l", (0, 6, 0), 5.0), ("hand_r", (0, 6, 0), 5.0)], lag=ON_TIME,
              through=after, stages={"windup": [(0.3, crouch), (0.8, up), (1.0, held)], "active": [(0.5, smash), (1.0, after)],
                                     "recover": [(0.4, lift), (0.7, _mix(lift, _pose(r), 0.75))]})
    W = mv.seconds("windup")
    mv._ticks = [("ring_c", 0.1, 0.5, 1), ("ring_b", 0.45, 0.6, 1), ("ring_a", W - 0.6, 0.6, 1), ("moon_a", W, 0.9, 1), ("moon_b", W + 0.05, 0.9, 1)]
    return mv


LANCE_FROM = (0.0, 4.9, 0.7)  # where the beam leaves it: the lens (blocks: x, up, forward)


def lance(r):
    """Calculated Lance (HEAVY, a beam): it plants its feet wide and turns side-on, its right arm coming up to point at
    you with the clamp open; its three rings slow and stop and turn to face you, lining up one inside another round the
    lens like a gunsight; the lens opens; then the beam, the arm braced and shuddering, the core blazing."""
    wide = {"l": (3, 0, -5), "r": (-3, 0, 4)}
    aim = lambda a: _pose(r, hips=(1, -16, 0), at=(0, -2.5, 0), spine=(0, -8, 0), chest=(-2, -6, 0), neck=(0, 14, 0), head=(-2, 16, 0.6 * a), step=wide,
                          right=(-6 + 0.6 * a, 62 + 0.6 * a, -36), rhand=((0, 0, -1), (0, 1, 0)), left=(16, 40, -4), curl=(1, -0.3), sight=1.0)
    raise_ = _pose(r, hips=(0, -10, 0), at=(0, -1.5, 0), spine=(0, -5, 0), chest=(-1, -4, 0), neck=(0, 8, 0), head=(-1, 10, 0), step=wide,
                   right=(-14, 52, -24), rhand=((0, 0.3, -1), (0, 1, 0)), left=(18, 36, -4), curl=(1, 0.2), sight=0.4)
    act = [(0.1, aim(0)), (0.25, aim(1)), (0.4, aim(-1)), (0.55, aim(1)), (0.7, aim(-1)), (0.85, aim(1)), (1.0, aim(0))]
    mv = Move("lance", "HEAVY", 22, 24, 18, title="Calculated Lance", hold=8, track=3.0, ability="Calculated Lance", origin=LANCE_FROM, rng=(4, 22),
              kinds=("ranged",), damage=0, knock=0, plant=(), lag=ON_TIME, through=_mix(aim(0), _pose(r), 0.3),
              stages={"windup": [(0.3, _mix(_pose(r), raise_, 0.6)), (0.6, raise_), (1.0, aim(0))], "active": act,
                      "recover": _back(r, _mix(aim(0), _pose(r), 0.3))})
    W, A = mv.seconds("windup"), mv.seconds("active")
    mv._sight = W  # the rings come to rest lined up here (the hold freezes it so), and stay so through the beam
    mv._ticks = [("ring_c", 0.05, 0.5, 1), ("ring_b", 0.25, 0.6, 1), ("ring_a", W - 0.65, 0.65, 1), ("rings", W + A + 0.1, 0.6, 1)]
    return mv


def drones(r):
    """Drones (summoning): it rolls its great shoulders back and spreads its arms, and the satellites lift off the rack
    on its back one after another, its body jolting as each goes, its sphere tipping back to watch them rise."""
    open_ = lambda j: _pose(r, hips=(-3, 0, 0), at=(0, 0.5 - 0.8 * j, 0.5), spine=(-3 + 2 * j, 0, 0), chest=(-12 + 4 * j, 0, 0), neck=(-6, 0, 0), head=(-22, 0, 0),
                            left=(36, 50, 4), right=(-36, 50, 4), curl=(-0.2, -0.2))
    act = [(0.1, open_(0)), (0.18, open_(1)), (0.3, open_(0)), (0.43, open_(1)), (0.55, open_(0)), (0.68, open_(1)), (0.8, open_(0)), (1.0, open_(0))]
    mv = Move("drones", "NONE", 12, 28, 14, title="Drones", track=0.0, ability="Drones", rng=(0, 99), kinds=("summon",), damage=0, knock=0, plant=(),
              lag=ON_TIME, through=_mix(open_(0), _pose(r), 0.35),
              stages={"windup": [(0.4, _mix(_pose(r), open_(0), 0.5)), (1.0, open_(0))], "active": act, "recover": _back(r, _mix(open_(0), _pose(r), 0.35))})
    mv._launch = [0.16, 0.41, 0.66]  # each satellite leaves at this much of the active stage
    mv._ticks = [(b, _staged(mv, "active", f), 0.5, 1) for b, f in zip(("ring_c", "ring_b", "ring_a"), mv._launch)]
    mv._ticks += [("moon_a", _staged(mv, "active", 0.0), 1.4, 1), ("moon_b", _staged(mv, "active", 0.1), 1.4, 1)]
    return mv


def orbit(r):
    """Orbital Strike: it raises its right hand straight up to the sky, the clamp open, its sphere tipping back, its
    rings spinning up and its moons whirling; then it brings the hand down to point at you, and holds it there, chopping
    down three times as the strikes fall."""
    sky = _pose(r, hips=(-3, -6, 0), at=(0, 1, 0.5), spine=(-4, -4, 0), chest=(-10, -6, 4), neck=(-8, 0, 0), head=(-22, 4, 0),
                right=(-14, 90, -4), rhand=((0, -1, -0.1), (1, 0, 0)), left=(20, 34, -2), curl=(1, -0.4))
    point = lambda d: _pose(r, hips=(2, -12, 0), at=(0, -1.5, -0.5), spine=(1, -6, 0), chest=(4, -6, 0), neck=(2, 10, 0), head=(4, 12, 0),
                            right=(-8, 60 - 6 * d, -36), rhand=((0, 0.15 + 0.35 * d, -1), (1, 0, 0)), left=(20, 36, -4), curl=(1, -0.3))
    act = [(0.2, point(1)), (0.32, point(0)), (0.46, point(1)), (0.58, point(0)), (0.72, point(1)), (0.84, point(0)), (1.0, point(0))]
    mv = Move("orbit", "NONE", 16, 26, 16, title="Orbital Strike", track=3.0, ability="Orbital Strike", rng=(4, 20), kinds=("ranged", "area"), damage=0,
              knock=0, plant=(), lag=ON_TIME, through=_mix(point(0), _pose(r), 0.3),
              stages={"windup": [(0.35, _mix(_pose(r), sky, 0.55)), (0.75, sky), (1.0, _mix(sky, point(0), 0.15))], "active": act,
                      "recover": _back(r, _mix(point(0), _pose(r), 0.3))})
    mv._ticks = [("ring_c", 0.1, 1.9, 4), ("ring_b", 0.15, 1.85, 3), ("ring_a", 0.2, 1.85, 3), ("moons", 0.1, 2.0, 2)]
    return mv


def lattice(r):
    """Spark Lattice: it draws its fists far apart at the height of its chest and strikes them together in front of it
    like flints, pulls them apart, and strikes them again harder, its rings jolting faster at each blow."""
    apart = lambda w, a: _pose(r, hips=(-2, 0, 0), at=(0, -0.5 * a, 0.5), spine=(-2, 0, 0), chest=(-8 + 4 * a, 0, 0), neck=(-4, 0, 0), head=(-8 + 4 * a, 0, 0),
                               left=(w, 48, -12 + 4 * a), right=(-w, 48, -12 + 4 * a), curl=(1, 1))
    strike = lambda a: _pose(r, hips=(6, 0, 0), at=(0, -2 - a, -1), spine=(4, 0, 0), chest=(10 + 2 * a, 0, 0), neck=(-6, 0, 0), head=(-4, 0, 0),
                             left=(6.5, 48, -30), right=(-6.5, 48, -30), lhand=((-1, 0, -0.2), (0, 1, 0)), rhand=((1, 0, -0.2), (0, 1, 0)), curl=(1, 1))
    act = [(0.18, strike(0)), (0.3, _mix(strike(0), apart(30, 0), 0.25)), (0.48, apart(32, 0)), (0.68, strike(1)), (0.78, _mix(strike(1), apart(30, 1), 0.1)),
           (1.0, strike(1))]
    mv = Move("lattice", "NONE", 14, 20, 16, title="Spark Lattice", track=0.0, ability="Spark Lattice", rng=(0, 14), kinds=("area",), damage=0, knock=0,
              plant=(), lag=ON_TIME, through=_mix(strike(1), _pose(r), 0.3),
              stages={"windup": [(0.4, _mix(_pose(r), apart(34, 0), 0.5)), (1.0, apart(36, 0))], "active": act,
                      "recover": _back(r, _mix(strike(1), _pose(r), 0.3))})
    t1, t2 = _staged(mv, "active", 0.18), _staged(mv, "active", 0.68)
    mv._ticks = [("ring_c", t1, 0.48, 1), ("ring_b", t1 + 0.02, 0.55, 1), ("ring_a", t2, 0.6, 1), ("ring_c", t2 + 0.02, 0.5, 1), ("moons", t2, 0.9, 1)]
    return mv


def turntable(r):
    """For whoever is behind it: it braces with its arms out wide and its fists clenched, and its waist turns right
    round on its turntable, a whole turn, the fists sweeping everything round it; its legs never move."""
    arms = lambda a: _pose(r, hips=(2, 0, 0), at=(0, -2.5, 0), spine=(0, 0, 0), chest=(-2 + 2 * a, 0, 0), neck=(0, 0, 0), head=(0, 0, 0),
                           left=(48, 52, -4), right=(-48, 52, -4), curl=(1, 1))
    mv = Move("turntable", "LIGHT", 12, 8, 14, title="Turntable", track=0.0, rng=(0, 4.8), kinds=("behind",), damage=1.0, knock=1.3, plant=(),
              hit=[("hand_l", (0, 6, 0), 4.5), ("hand_r", (0, 6, 0), 4.5), ("forearm_l", (0, 12, 0), 4.0), ("forearm_r", (0, 12, 0), 4.0)],
              lag=ON_TIME, through=arms(1),
              stages={"windup": [(0.3, _mix(_pose(r), arms(0), 0.35)), (1.0, arms(0))], "active": [(0.5, arms(1)), (1.0, arms(1))],
                      "recover": [(0.4, _mix(arms(1), _pose(r), 0.4)), (0.75, _mix(arms(1), _pose(r), 0.85))]})
    return mv


def shutdown(r):
    """Shutdown (PERILOUS, the signature): everything stops. Its rings slow and stand still, its core dims, its arms drop
    dead at its sides, its sphere sags forward on its axle and its knees lock; it stands there, still; then it jolts
    awake all at once, its core blazing and its rings whipping round, and throws its arms wide and its chest open."""
    off = lambda a: _pose(r, hips=(3, 0, 0), at=(0, -2.5, 0.5), spine=(3, 0, 0), chest=(9, 0, 0), neck=(16, 0, 0), head=(20 + 0.6 * a, 0, 6),
                          left=(24, 18, -1), right=(-24, 18, -1), curl=(0.05, 0.05))
    jolt = _pose(r, hips=(-2, 0, 0), at=(0, -1, 0.5), spine=(-2, 0, 0), chest=(-4, 0, 0), neck=(-6, 0, 0), head=(-14, 0, -4),
                 left=(26, 30, -6), right=(-26, 30, -6), curl=(1.1, 1.1))
    wide = lambda a: _pose(r, hips=(-6, 0, 0), at=(0, 1.5, 1), spine=(-6, 0, 0), chest=(-18, 0, 0), neck=(-10, 0, 0), head=(-20, 0, 2 * a),
                           left=(46, 68, 6), right=(-46, 68, 6), curl=(-0.4, -0.4))
    wind = [(0.25, _mix(_pose(r), off(0), 0.55)), (0.45, off(0)), (0.62, off(1)), (0.8, off(0)), (0.9, off(0)), (1.0, jolt)]
    act = [(0.3, wide(0)), (0.65, wide(1)), (1.0, wide(0))]
    mv = Move("shutdown", "PERILOUS", 36, 12, 28, title="Shutdown", signature=True, track=0.0, ability="Shutdown", rng=(0, 15), kinds=("area",), damage=0,
              knock=0, plant=(), lag=ON_TIME, through=_mix(wide(0), _pose(r), 0.2),
              stages={"windup": wind, "active": act, "recover": [(0.35, _mix(wide(0), _pose(r), 0.45)), (0.7, _mix(wide(0), _pose(r), 0.85))]})
    W = mv.seconds("windup")
    mv._ticks = [("ring_c", W, 0.8, 3), ("ring_b", W, 0.85, 3), ("ring_a", W + 0.02, 0.9, 3), ("moons", W, 1.0, 2)]
    mv._core = [(0.2 * W, 1.0), (0.45 * W, 0.6), (0.97 * W, 0.6), (W + 0.06, 1.55), (_staged(mv, "active", 1.0), 1.35), (_staged(mv, "recover", 0.6), 1.0)]
    return mv


def wake(r):
    """Its intro: it stands shut down, slumped, its arms hanging dead and its sphere sagging, its rings still; its core
    flickers and lights; the inner ring starts to turn, then the middle, then the outer, then the moons; it lifts its
    sphere and straightens; it closes one clamp and then the other, its rams hissing out and back; and it settles."""
    dead = _pose(r, hips=(3, 0, 0), at=(0, -2.5, 0.5), spine=(3, 0, 0), chest=(9, 0, 0), neck=(16, 0, 0), head=(20, 0, 6), left=(24, 18, -1), right=(-24, 18, -1),
                 curl=(0.05, 0.05))
    lift_ = _pose(r, hips=(1, 0, 0), at=(0, -1, 0.2), spine=(1, 0, 0), chest=(2, 0, 0), neck=(2, 0, 0), head=(-4, 0, 0), curl=(0.2, 0.2))
    grip_l = _pose(r, head=(-2, 12, 0), curl=(1.1, 0.2), ext=(0.5, 0.0))
    grip_r = _pose(r, head=(-2, -12, 0), curl=(0.8, 1.1), ext=(0.0, 0.5))
    settle = _pose(r, hips=(2, 0, 0), at=(0, -1.2, -0.3), chest=(3, 0, 0), head=(2, 0, 0), curl=(0.8, 0.8))
    mv = Move("wake", "NONE", 16, 40, 16, track=0.0, rng=(0, 99), kinds=("intro",), damage=0, knock=0, plant=(), lag=ON_TIME,
              through=_mix(settle, _pose(r), 0.3),
              stages={"windup": [(0.5, _mix(_pose(r), dead, 0.7)), (1.0, dead)],
                      "active": [(0.3, dead), (0.5, lift_), (0.62, grip_l), (0.7, _mix(grip_l, grip_r, 0.5)), (0.8, grip_r), (1.0, settle)],
                      "recover": _back(r, _mix(settle, _pose(r), 0.3))})
    W, A = mv.seconds("windup"), mv.seconds("active")
    mv._ticks = [("ring_c", W + 0.25 * A, 0.55, 1), ("ring_b", W + 0.375 * A, 0.6, 1), ("ring_a", W + 0.5 * A, 0.7, 1), ("moon_a", W + 0.6 * A, 1.4, 1),
                 ("moon_b", W + 0.7 * A, 1.4, 1)]
    mv._core = [(0.0, 0.6), (W + 0.05 * A, 0.6), (W + 0.1 * A, 0.9), (W + 0.13 * A, 0.65), (W + 0.18 * A, 1.1), (W + 0.3 * A, 1.0)]
    return mv


def moves(r):
    return [punch(r, "r", "punch"), punch(r, "l", "punch_b"), slam(r), lance(r), drones(r), orbit(r), lattice(r), turntable(r), shutdown(r), wake(r)]


def clips(r):
    plans.biped_locomotion(r, P, weight=1.6, temper="regal")
    idle(r)
    _stomp(r)
    reactions(r)
    mv = moves(r)
    by = {m.id: m for m in mv}
    for m in mv:
        stage_clips(r, m)
    for m in mv:
        nm = core.clip_name(m)
        _ticks(r, nm, getattr(m, "_ticks", []) or [("ring_c", 0.0, 0.48, 1)])
        if getattr(m, "_core", None):
            _scales(r, nm, "core", m._core)
    # the lance: the rings come round to whole turns and turn to face you (sight) by the wind-up's end, so they stand
    # square in one another round the lens, and stay so through the beam; the lens opens as it fires
    m = by["lance"]
    nm = core.clip_name(m)
    W, A = m._sight, m.seconds("active")
    _scales(r, nm, "lens", [(W - 0.2, 1.0), (W, 1.5), (W + A, 1.5), (W + A + 0.3, 1.0)])
    _scales(r, nm, "core", [(W - 0.1, 1.0), (W + 0.1, 1.25), (W + A, 1.2), (W + A + 0.4, 1.0)])
    # the turntable: its waist turns a whole turn on its own track (it ends a turn round, which is where it started)
    m = by["turntable"]
    c = r.clips[core.clip_name(m)]
    c.tracks[("spine", "rot")] = []
    W, A = m.seconds("windup"), m.seconds("active")
    c.rot("spine", [(0.0, 0, 0, 0), (W * 0.6, 0, -14, 0), (W, 0, -22, 0), (W + 0.25 * A, 0, 60, 0), (W + 0.6 * A, 0, 230, 0), (W + A, 0, 352, 0),
                    (W + A + 0.25, 0, 368, 0), (W + A + 0.5, 0, 362, 0), (c.length, 0, 360, 0)])
    # the satellites lift off the rack one after another and are gone, and fold out of it again as it recovers
    m = by["drones"]
    nm = core.clip_name(m)
    c = r.clips[nm]
    for k, f in enumerate(m._launch):
        t = _staged(m, "active", f)
        back = _staged(m, "recover", 0.55 + 0.15 * k)
        c.pos(f"sat{k}", [(0.0, 0, 0, 0), (t - 0.05, 0, 0, 0), (t + 0.15, 0, 9, 2), (t + 0.2, 0, 10, 2), (back - 0.01, 0, 0, 0), (c.length, 0, 0, 0)], "lin")
        _scales(r, nm, f"sat{k}", [(t + 0.1, 1.0), (t + 0.2, 0.0), (back, 0.0), (back + 0.25, 1.0)], interp="lin")
    # its hands, fingers and sphere stay out of the floor between the keys; kneeling and dying, its feet too
    hands = [([f"hand_{s}", f"fingers_{s}"], [(f"hand_{s}", 0, 50), (f"forearm_{s}", 0, 30)]) for s in "lr"]
    core.ground_pass(r, hands, clips=[n for n in r.clips if n != "death"])
    feet = [(["foot_" + s], [("foot_" + s, 0, 50), ("shin_" + s, 0, 30)]) for s in "lr"]
    core.ground_pass(r, feet, clips=["kneel", "rise", "run", "walk"])
    sphere = [(["core", "lens", "ring_a", "ring_b", "ring_c", "moon_a", "moon_b"], [("head", 0, 40, -1), ("neck", 0, 30, -1)])]
    core.ground_pass(r, hands + feet + sphere, clips=["death"], skip=())
    return mv


# Its sounds (SPEC §35.8): an iron golem's weight, pistons, a beacon's hum and a respawn anchor's charge; chosen on
# paper, not heard in game.
SOUNDS = {
    "tell_light": [("block.piston.contract", 1.3, 0.6), ("entity.iron_golem.step", 1.2, 0.6)],
    "tell_heavy": [("block.piston.contract", 0.8, 0.8), ("block.beacon.power_select", 1.4, 0.7)],
    "tell_perilous": [("block.beacon.deactivate", 2.0, 0.6), ("block.respawn_anchor.deplete", 1.4, 0.6)],
    "swing_light": [("block.piston.extend", 1.2, 0.8), ("entity.iron_golem.attack", 1.0, 0.8)],
    "swing_heavy": [("block.piston.extend", 1.6, 0.6), ("entity.iron_golem.attack", 1.2, 0.6)],
    "cast": [("block.beacon.activate", 1.4, 0.9), ("block.respawn_anchor.charge", 1.0, 0.8)],
    "impact": [("item.mace.smash_ground_heavy", 1.6, 0.6), ("block.anvil.land", 0.8, 0.7)],
    "roar": [("block.beacon.activate", 2.0, 0.6), ("block.respawn_anchor.set_spawn", 1.4, 0.7)],
    "step": [("entity.iron_golem.step", 1.0, 0.7), ("block.piston.contract", 0.4, 1.4)],
}
MOVE_SOUNDS = {"lance": {"swing": "cast"}, "drones": {"swing": "cast", "impact": ""}, "orbit": {"swing": "cast"}, "lattice": {"swing": "cast"},
               "shutdown": {"swing": "roar"}, "wake": {"swing": "cast", "impact": ""}}

FIGHT = {
    "intro": {"pose": "idle", "move": "wake"},  # it stands shut down, and starts up
    "phases": [
        {"pool": [
            {"move": "punch", "weight": 3, "when": "near", "then": [["punch_b"], ["punch_b", "slam"]]},
            {"move": "slam", "weight": 3, "then": [["punch"]]},
            {"move": "lance", "weight": 2, "when": "far"},
            {"move": "turntable", "weight": 3, "when": "behind"},
        ]},
        # subroutines, as in DeepBestiary: the drones, the strikes from orbit
        {"transition": "drones", "pool": [
            {"move": "punch", "weight": 3, "when": "near", "then": [["punch_b"], ["punch_b", "slam"]]},
            {"move": "slam", "weight": 3, "then": [["punch"]]},
            {"move": "lance", "weight": 2, "when": "far"},
            {"move": "orbit", "weight": 2},
            {"move": "turntable", "weight": 3, "when": "behind"},
        ]},
        # shutdown
        {"transition": "shutdown", "pool": [
            {"move": "shutdown", "weight": 2},
            {"move": "lattice", "weight": 2, "when": "near"},
            {"move": "punch", "weight": 3, "when": "near", "then": [["punch_b", "slam"]]},
            {"move": "slam", "weight": 3, "then": [["punch", "punch_b"]]},
            {"move": "turntable", "weight": 3, "when": "behind"},
        ]},
    ],
    "enrage": {"below": 0.15, "speed": 1.2},
}


def design():
    r = body()
    r.pack()
    mv = clips(r)
    return {"rig": r, "plan": P, "moves": mv, "fight": FIGHT, "paint": paint, "scale": SCALE, "half": 1.2, "sounds": SOUNDS,
            "move_sounds": MOVE_SOUNDS}
