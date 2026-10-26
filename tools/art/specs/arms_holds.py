"""Each fighting style's first-person hold (SPEC §25 "First person, the hold"; Euan 2026-10-26: "a spear should still feel
like you're holding a spear. Right now in first person, it just looks like a sword").

A hold is written as the weapon is really held, in view terms, and solved for each weapon from its own mesh (so every
weapon of a style, a Lord-forged one too, sits the same way in the hand whatever its outline):
  yaw, pitch   where the business end points at rest: yaw degrees to the left of straight ahead, pitch degrees up
  roll         the weapon turned about its own length (0: its flat toward the eye as far as the pointing allows)
  grip         where the hand closes, from vanilla's resting hand (view space, blocks): every weapon is seated in it
  scale        one size for every style (SCALE), so a spear is as much longer than a longsword in hand as in F5, a
               dagger as much shorter; a style may carry a little more or less (two-handed weapons nearer the eye)
  cut          for a style with a working side (an axe's bit, a scythe's blade, a glaive's edge, a cleaver's edge): the
               side it faces at rest, as a view direction; the solver turns each weapon so its own working side (EDGES,
               read from its design) faces there, so every weapon of the style cuts with its edge on the same swing

Every design puts its grip at arms_weapons.HAND along the item's diagonal, tip to the upper right, its flat across z.
    python3 tools/art/specs/arms_holds.py report        each style's current hold, in these terms
"""
import json
import math
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "../../.."))
ART = os.path.abspath(os.path.join(HERE, ".."))
for p in (HERE, ART):
    if p not in sys.path:
        sys.path.insert(0, p)
import mesh_render as MR  # noqa: E402

REST = np.array([0.56, -0.52, -0.72])  # vanilla's resting hand (view space)
GRIP_ITEM = np.array([3.0 / 16, 3.0 / 16, 0.5])  # arms_weapons.HAND, item space
AXIS_ITEM = np.array([1.0, 1.0, 0.0]) / math.sqrt(2)
FACE_ITEM = np.array([0.0, 0.0, 1.0])
SCALE = 0.5  # the Longsword's tuned size; every style shares it (relative size as in the world)


def direction(yaw, pitch):
    y, p = math.radians(yaw), math.radians(pitch)
    return np.array([-math.sin(y) * math.cos(p), math.sin(p), -math.cos(y) * math.cos(p)])


def frame(yaw, pitch, roll):
    """The weapon's frame in view space: (axis, edge, face) columns."""
    a = direction(yaw, pitch)
    ref = np.array([0.0, 0.0, 1.0]) - a * a[2]  # toward the eye, across the axis
    if np.linalg.norm(ref) < 1e-3:
        ref = np.array([0.0, 1.0, 0.0]) - a * a[1]
    ref /= np.linalg.norm(ref)
    r = math.radians(roll)
    face = ref * math.cos(r) + np.cross(a, ref) * math.sin(r)
    edge = np.cross(a, face)
    return np.stack([a, edge, face], axis=1)


def euler_xyz(R):
    """Angles (degrees) with R = Rx . Ry . Rz (vanilla's ItemTransform order, mesh_render.euler)."""
    sy = max(-1.0, min(1.0, R[0, 2]))
    y = math.asin(sy)
    if abs(sy) < 0.9999:
        x = math.atan2(-R[1, 2], R[2, 2])
        z = math.atan2(-R[0, 1], R[0, 0])
    else:
        x = math.atan2(R[2, 1], R[1, 1])
        z = 0.0
    return math.degrees(x), math.degrees(y), math.degrees(z)


# Which side of its length each weapon works with, in its design's terms (+1: arms_weapons' +x, the item's lower right;
# -1: its upper left; 0: both, or a point, a face): read from each design, never guessed from the outline (a katana's
# tip curves back toward its spine and a glaive's fluke weighs down its back, so their mass leans away from the edge).
# Euan 2026-10-26: "when it's a blade, it's the sharp end that's cutting". A style's own entry covers its every weapon;
# a (style, tier) entry one forged tier (a Lord-forged weapon takes its style's).
EDGES = {
    "katana": 1,      # the edge on the outside of the curve, the tip curving back toward the spine
    "glaive": 1,      # the hardened edge on the outside of the curve, the fluke on the back
    "axe": -1,        # the bit
    "scythe": -1,     # the blade, off the top at right angles; it cuts along its inside, drawn back (the reap's pull)
    "twinblades": 1,  # each blade's leading side: a tusk or fin curving back from it, so the pair spins tips trailing
    ("longsword", "D"): 1,   # the tusk cleaver, its bronze-capped back to -x
    ("greatblade", "D"): 1,  # the tusk blade, its back and hook to -x
    ("daggers", "E"): 1,     # the skinning knife, its straight back to -x
    ("daggers", "D"): -1,    # the boar's tusk, honed along its inside (it curves to -x)
    ("daggers", "B"): 1,     # the talon, curving forward to +x, cutting on its inside
}


def edge_of(style, tier=None):
    """A weapon's working side (EDGES): its forged tier's own, else its style's; None for the tier: a Lord-forged one."""
    e = EDGES.get((style, tier)) if tier else None
    return EDGES.get(style, 0) if e is None else e


def cut_side(style, tier=None):
    """The side every cut of a style leads with, for its weapon of {@code tier}, in that weapon's design terms: its own
    working side; a double-edged one of a style with single-edged tiers (a hold with a cut) leads with the side those
    tiers' edges are turned to, so one baked swing cuts with every tier's edge."""
    e = edge_of(style, tier)
    h = HOLDS.get(style, {})
    if e or "cut" not in h:
        return e
    T = frame(h["yaw"], h["pitch"], h.get("roll", 0.0))
    return 1 if T[:, 1] @ np.asarray(h["cut"], dtype=float) > 0 else -1


def side_of(pos):
    """Which side of the item's axis the mesh's head reaches (+1 toward the edge direction axis x face, -1 away,
    0 even): a fallback for a weapon with no entry in EDGES."""
    edge = np.cross(AXIS_ITEM, FACE_ITEM)
    rel = pos - GRIP_ITEM
    along = rel @ AXIS_ITEM
    top = rel[along > 0.6 * along.max()]
    if not len(top):
        return 0
    bias = float((top @ edge).mean())
    return 0 if abs(bias) < 0.04 else (1 if bias > 0 else -1)


def solve(pos, hold, edge=None):
    """(rotation, translation, scale) of the display transform for a mesh (item space, 0..1) held as {@code hold}, its
    working side {@code edge} (EDGES; None: guessed from the mesh)."""
    T = frame(hold["yaw"], hold["pitch"], hold.get("roll", 0.0))
    side = side_of(pos) if edge is None else edge
    if hold.get("cut") is not None and side != 0:
        want = np.asarray(hold["cut"], dtype=float)
        if (T[:, 1] @ want) * side < 0:  # turn the weapon about its length so its working side faces the cut
            T = np.stack([T[:, 0], -T[:, 1], -T[:, 2]], axis=1)
    F = np.stack([AXIS_ITEM, np.cross(AXIS_ITEM, FACE_ITEM), FACE_ITEM], axis=1)
    R = T @ F.T
    sc = SCALE * hold.get("size", 1.0)
    grip = np.asarray(hold.get("grip", (0.07, -0.05, -0.02)))
    t16 = (grip - sc * (R @ (GRIP_ITEM - 0.5))) * 16.0
    rx, ry, rz = euler_xyz(R)
    return ([round(rx, 2), round(ry, 2), round(rz, 2)], [round(float(v), 3) for v in t16], round(sc, 3))


M_X = np.diag([-1.0, 1.0, 1.0])


def mirror_display(display, keep_edge=False):
    """The left hand's display transform for a right hand's, so the weapon in the left hand is that hand's true mirror:
    its length, its flat and where the hand closes mirrored across the view (only a detail on one side of the blade
    stays on its side). A turn can mirror only two of a weapon's three ways: a single-edged one ({@code keep_edge})
    keeps its length and its edge mirrored and shows its other flat, so the off hand's mirrored cuts lead with the edge
    too, never the back. 26.3 mirrors every left-hand transform itself as it applies it (ItemTransform.apply: x
    translation, y and z rotation negated), so what is written here is the transform before that."""
    (rx, ry, rz), t, sc = display
    R = MR.euler(rx, ry, rz)
    a = R @ AXIS_ITEM
    f = R @ FACE_ITEM
    a2, f2 = M_X @ a, M_X @ f
    if keep_edge:
        e2 = M_X @ (R @ np.cross(AXIS_ITEM, FACE_ITEM))
        T = np.stack([a2, e2, np.cross(e2, a2)], axis=1)
    else:
        T = np.stack([a2, np.cross(a2, f2), f2], axis=1)
    F = np.stack([AXIS_ITEM, np.cross(AXIS_ITEM, FACE_ITEM), FACE_ITEM], axis=1)
    R_left = T @ F.T
    grip_r = sc * (R @ (GRIP_ITEM - 0.5)) + np.asarray(t) / 16.0
    t_left = M_X @ grip_r - sc * (R_left @ (GRIP_ITEM - 0.5))
    jx, jy, jz = euler_xyz(M_X @ R_left @ M_X)  # applied, the engine's own mirror turns it back into R_left
    return ([round(jx, 2), round(jy, 2), round(jz, 2)], [round(float(-t_left[0] * 16), 3), round(float(t_left[1] * 16), 3),
                                                         round(float(t_left[2] * 16), 3)], sc)


HANDHELD_3P = ((0.0, -90.0, 55.0), (0.0, 4.0, 0.5), 0.85)  # vanilla item/handheld's thirdperson_righthand


def third_person(style, edge):
    """The third-person holds (right, left, as written in the JSON) for a weapon whose single edge is on the other side
    from the one its style's swings lead with (cut_side of its forged weapon): vanilla's handheld turned half round
    about the weapon's length (the line through the item's centre), so the wrist's baked turn (anim_lab.py wrist_keys)
    lays its edge into every blow; None keeps vanilla's."""
    lead = cut_side(style, "C")
    if not edge or not lead or edge == lead:
        return None
    (rx, ry, rz), t, sc = HANDHELD_3P
    a = math.radians(180)
    K = np.array([[0, -AXIS_ITEM[2], AXIS_ITEM[1]], [AXIS_ITEM[2], 0, -AXIS_ITEM[0]], [-AXIS_ITEM[1], AXIS_ITEM[0], 0]])
    flip = np.eye(3) + math.sin(a) * K + (1 - math.cos(a)) * (K @ K)
    ex, ey, ez = euler_xyz(MR.euler(rx, ry, rz) @ flip)
    rot = [round(ex, 2), round(ey, 2), round(ez, 2)]
    # a left hand's is applied mirrored (y and z turns, x move negated): written so it applies as the right's does
    return ({"rotation": rot, "translation": list(t), "scale": [sc] * 3},
            {"rotation": [rot[0], -rot[1], -rot[2]], "translation": [-t[0], t[1], t[2]], "scale": [sc] * 3})


def worn_third_person(sc=0.7, fist=2.5):
    """The Gauntlets' third-person holds (right, left, as written in the JSON): worn, not held (Euan 2026-10-26, "the
    gauntlet should be on the hand"; in F5 vanilla's handheld hold set it out in front of the fist like a held block,
    where it went into the legs, the body and the head). The knuckles point down the forearm and out past the fist, the
    back of the hand outward, the cuff round the forearm, the fist ({@code fist} texels up from the wrist) on the hand.
    ItemInHandLayer's frame: the arm's, X -90, Y 180, (+-1, 2, -10)/16; the arm hangs along +y, its hand near y 10."""
    hold = MR.euler(-90, 0, 0) @ MR.euler(0, 180, 0)
    F = np.stack([AXIS_ITEM, np.cross(AXIS_ITEM, FACE_ITEM), FACE_ITEM], axis=1)
    c = GRIP_ITEM + AXIS_ITEM * fist / 16
    out = []
    for out_x, o in ((-1.0, np.array([1.0, 2.0, -10.0]) / 16), (1.0, np.array([-1.0, 2.0, -10.0]) / 16)):
        a, z = np.array([0.0, 1.0, 0.0]), np.array([out_x, 0.0, 0.0])
        T = np.stack([a, np.cross(a, z), z], axis=1)  # the arm frame's knuckles, edge, back of the hand
        R = hold.T @ T @ F.T
        centre = np.array([out_x, 9.0, 0.0]) / 16  # on the arm's middle, the fist round the hand
        t = (hold.T @ centre - o - sc * R @ (c - 0.5)) * 16
        ex, ey, ez = euler_xyz(R)
        out.append((ex, ey, ez, t))
    (rx, ry, rz, tr), (lx, ly, lz, tl) = out
    # a left hand's is applied mirrored (y and z turns, x move negated): written so it applies as solved
    return ({"rotation": [round(rx, 2), round(ry, 2), round(rz, 2)], "translation": [round(float(v), 3) for v in tr], "scale": [sc] * 3},
            {"rotation": [round(lx, 2), round(-ly, 2), round(-lz, 2)], "translation": [round(float(-tl[0]), 3), round(float(tl[1]), 3),
                                                                                      round(float(tl[2]), 3)], "scale": [sc] * 3})


def describe(display):
    """A display transform back in these terms (for the report)."""
    (rx, ry, rz), t, sc = display
    R = MR.euler(rx, ry, rz)
    a = R @ AXIS_ITEM
    yaw = math.degrees(math.atan2(-a[0], -a[2]))
    pitch = math.degrees(math.asin(max(-1, min(1, a[1]))))
    grip = sc * (R @ (GRIP_ITEM - 0.5)) + np.asarray(t) / 16.0
    return round(yaw), round(pitch), [round(float(v), 2) for v in grip], sc


# ---- the holds ------------------------------------------------------------------------------------------------------

LEFT = (-1.0, 0.0, -0.3)  # a working side toward the middle of the view
# a working side down and toward the foe: how a blade, an axe's bit, a glaive's edge is really held at rest (Euan
# 2026-10-26: "You're never going to hold an axe on its side like that. You're going to hold the sharp ends downwards,
# similar to a sword"): the weapon turned a quarter about its length (roll 90), its flat to the side, never to the eye
DOWN = (0.0, -0.8, -0.6)

HOLDS = {
    # one hand, the blade up and forward to the left, an edge down toward the foe (a cleaver's own), its flat to the side
    "longsword": dict(yaw=18, pitch=52, roll=90, grip=(0.02, 0.0, 0.02), cut=DOWN),
    # en garde: the point forward at the target's height, the hand a little in
    "rapier": dict(yaw=20, pitch=18, roll=90, grip=(-0.04, 0.03, -0.02)),
    # two hands at the middle, the edge down toward the target, the point at it (chudan)
    "katana": dict(yaw=10, pitch=36, roll=90, grip=(-0.14, 0.0, 0.04)),
    # the shaft across the lower view, gripped part way, the head forward and up, the butt back past the hand; held
    # larger (nearer its size in F5) and turned so its head sits left of and under the aim point, not on it (the game
    # draws the resting hand about an eighth of the frame higher than mesh_render does: judged in the game, 2026-10-26)
    "spear": dict(yaw=46, pitch=13, grip=(0.02, -0.04, 0.06), size=1.3),
    # as the spear, the blade higher, its edge down toward the foe
    "glaive": dict(yaw=54, pitch=22, roll=90, grip=(0.02, -0.02, 0.05), size=1.2, cut=DOWN),
    # the snath up, the blade at the top reaching in across the view
    "scythe": dict(yaw=8, pitch=64, grip=(0.02, -0.04, 0.03), cut=LEFT),
    # held at its middle across the body, one end low right, the other up to the left
    "warstaff": dict(yaw=58, pitch=28, grip=(-0.08, -0.06, 0.0)),
    # two hands, raised, big and near the middle; an edge down toward the foe, its broad flat to the side
    "greatblade": dict(yaw=20, pitch=60, roll=90, grip=(-0.10, -0.08, 0.04), cut=DOWN),
    # two hands low on the haft, the head up at the right
    "maul": dict(yaw=6, pitch=58, roll=90, grip=(-0.04, -0.10, 0.04)),
    # one hand, the head up, its bit down toward the foe (never its flat to the eye)
    "axe": dict(yaw=10, pitch=58, roll=90, grip=(0.02, -0.04, 0.02), cut=DOWN),
    # short, the point forward, a little larger than true so its blade reads; one in each hand (the off hand's twin,
    # ArmsOffHand), held up enough that both blades show whole
    "daggers": dict(yaw=14, pitch=42, roll=90, grip=(-0.02, 0.07, -0.02), size=1.15, cut=DOWN),
    # held at the middle, the blades either way across the view
    "twinblades": dict(yaw=62, pitch=34, grip=(-0.04, 0.02, 0.0)),
    # the head forward on its coiled chain
    "chainblade": dict(yaw=18, pitch=38, roll=90, grip=(0.02, 0.0, 0.02)),
    # the shield to the front on the forearm, the blade above it
    "shieldblade": dict(yaw=30, pitch=62, grip=(-0.02, -0.02, 0.02)),
    # the energy blade forward like a drawn arm
    "precursor": dict(yaw=14, pitch=24, roll=90, grip=(0.0, 0.0, 0.0)),
    # gauntlets keep their worn hold (arms_weapons.HOLDS["gauntlets"]): they were never a blade in the hand
}


def hold_for(style, pos, edge=None):
    """The display transform for a weapon of {@code style} with mesh corners {@code pos} (item space) and working side
    {@code edge} (EDGES), or None to keep the style's own (the gauntlets)."""
    h = HOLDS.get(style)
    return None if h is None else solve(pos, h, edge)


def report():
    res = os.path.join(ROOT, "src/main/resources/assets/awaken/models/item")
    for st in ["longsword", "axe", "maul", "spear", "greatblade", "rapier", "daggers", "katana", "twinblades", "glaive",
               "scythe", "gauntlets", "chainblade", "shieldblade", "warstaff", "precursor"]:
        d = json.load(open(os.path.join(res, f"gatewrought_{st}_in_hand.json")))["display"]["firstperson_righthand"]
        print(st, describe((d["rotation"], d["translation"], d["scale"][0])))


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "report":
        report()
    else:
        raise SystemExit(__doc__)
