"""Each fighting style's first-person hold (SPEC §25 "First person, the hold"; Euan 2026-10-26: "a spear should still feel
like you're holding a spear. Right now in first person, it just looks like a sword").

A hold is written as the weapon is really held, in view terms, and solved for each weapon from its own mesh (so every
weapon of a style, a Lord-forged one too, sits the same way in the hand whatever its outline):
  yaw, pitch   where the business end points at rest: yaw degrees to the left of straight ahead, pitch degrees up
  roll         the weapon turned about its own length (0: its flat toward the eye as far as the pointing allows)
  grip         where the hand closes, from vanilla's resting hand (view space, blocks): every weapon is seated in it
  scale        one size for every style (SCALE), so a spear is as much longer than a longsword in hand as in F5, a
               dagger as much shorter; a style may carry a little more or less (two-handed weapons nearer the eye)
  cut          for a weapon with a working side (an axe's bit, a scythe's blade, a glaive's edge): the side it faces at
               rest, as a view direction; the solver puts the mesh's heavier side there

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


def side_of(pos):
    """Which side of the item's axis the mesh's head reaches (+1 toward the edge direction axis x face, -1 away,
    0 even): an axe's bit, a scythe's blade."""
    edge = np.cross(AXIS_ITEM, FACE_ITEM)
    rel = pos - GRIP_ITEM
    along = rel @ AXIS_ITEM
    top = rel[along > 0.6 * along.max()]
    if not len(top):
        return 0
    bias = float((top @ edge).mean())
    return 0 if abs(bias) < 0.04 else (1 if bias > 0 else -1)


def solve(pos, hold):
    """(rotation, translation, scale) of the display transform for a mesh (item space, 0..1) held as {@code hold}."""
    T = frame(hold["yaw"], hold["pitch"], hold.get("roll", 0.0))
    side = side_of(pos)
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


def mirror_display(display):
    """The left hand's display transform for a right hand's, so the weapon in the left hand is that hand's true mirror:
    its length, its flat and where the hand closes mirrored across the view (only a detail on one side of the blade
    stays on its side). 26.3 mirrors every left-hand transform itself as it applies it (ItemTransform.apply: x
    translation, y and z rotation negated), so what is written here is the transform before that."""
    (rx, ry, rz), t, sc = display
    R = MR.euler(rx, ry, rz)
    a = R @ AXIS_ITEM
    f = R @ FACE_ITEM
    a2, f2 = M_X @ a, M_X @ f
    T = np.stack([a2, np.cross(a2, f2), f2], axis=1)
    F = np.stack([AXIS_ITEM, np.cross(AXIS_ITEM, FACE_ITEM), FACE_ITEM], axis=1)
    R_left = T @ F.T
    grip_r = sc * (R @ (GRIP_ITEM - 0.5)) + np.asarray(t) / 16.0
    t_left = M_X @ grip_r - sc * (R_left @ (GRIP_ITEM - 0.5))
    jx, jy, jz = euler_xyz(M_X @ R_left @ M_X)  # applied, the engine's own mirror turns it back into R_left
    return ([round(jx, 2), round(jy, 2), round(jz, 2)], [round(float(-t_left[0] * 16), 3), round(float(t_left[1] * 16), 3),
                                                         round(float(t_left[2] * 16), 3)], sc)


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

HOLDS = {
    # one hand, the blade up and forward to the left, its flat to the eye
    "longsword": dict(yaw=18, pitch=52, grip=(0.02, 0.0, 0.02)),
    # en garde: the point forward at the target's height, the hand a little in
    "rapier": dict(yaw=20, pitch=18, grip=(-0.04, 0.03, -0.02)),
    # two hands at the middle, the edge up, the point toward the target (chudan)
    "katana": dict(yaw=10, pitch=36, roll=90, grip=(-0.14, 0.0, 0.04)),
    # the shaft across the lower view, gripped part way, the head forward and up, the butt back past the hand; held
    # larger (nearer its size in F5) and turned so its head sits left of and under the aim point, not on it (the game
    # draws the resting hand about an eighth of the frame higher than mesh_render does: judged in the game, 2026-10-26)
    "spear": dict(yaw=46, pitch=13, grip=(0.02, -0.04, 0.06), size=1.3),
    # as the spear, the blade higher, its edge in toward the middle
    "glaive": dict(yaw=54, pitch=22, grip=(0.02, -0.02, 0.05), size=1.2, cut=LEFT),
    # the snath up, the blade at the top reaching in across the view
    "scythe": dict(yaw=8, pitch=64, grip=(0.02, -0.04, 0.03), cut=LEFT),
    # held at its middle across the body, one end low right, the other up to the left
    "warstaff": dict(yaw=58, pitch=28, grip=(-0.08, -0.06, 0.0)),
    # two hands, raised, big and near the middle
    "greatblade": dict(yaw=20, pitch=60, grip=(-0.10, -0.08, 0.04)),
    # two hands low on the haft, the head up at the right
    "maul": dict(yaw=6, pitch=58, roll=90, grip=(-0.04, -0.10, 0.04)),
    # one hand, the head up, its bit in toward the middle
    "axe": dict(yaw=10, pitch=58, grip=(0.02, -0.04, 0.02), cut=LEFT),
    # short, the point forward, a little larger than true so its blade reads; one in each hand (the off hand's twin,
    # ArmsOffHand), held up enough that both blades show whole
    "daggers": dict(yaw=14, pitch=42, grip=(-0.02, 0.07, -0.02), size=1.15),
    # held at the middle, the blades either way across the view
    "twinblades": dict(yaw=62, pitch=34, grip=(-0.04, 0.02, 0.0)),
    # the head forward on its coiled chain
    "chainblade": dict(yaw=18, pitch=38, grip=(0.02, 0.0, 0.02)),
    # the shield to the front on the forearm, the blade above it
    "shieldblade": dict(yaw=30, pitch=62, grip=(-0.02, -0.02, 0.02)),
    # the energy blade forward like a drawn arm
    "precursor": dict(yaw=14, pitch=24, grip=(0.0, 0.0, 0.0)),
    # gauntlets keep their worn hold (arms_weapons.HOLDS["gauntlets"]): they were never a blade in the hand
}


def hold_for(style, pos):
    """The display transform for a weapon of {@code style} with mesh corners {@code pos} (item space), or None to keep
    the style's own (the gauntlets)."""
    h = HOLDS.get(style)
    return None if h is None else solve(pos, h)


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
