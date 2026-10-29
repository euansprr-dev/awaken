"""The Gearwright Colossus (SPEC §35.11, docs/bosses/ROLLOUT.md): the Clockwork Foundry's Lord, a Brute. The Clock
Tower, Euan's pick from its silhouette round: a clock tower that walked out of the foundry, 8.4 blocks to its
weathervane, on long jointed iron legs.

The tower is its body: a riveted copper shaft with brass cornices and iron buttresses, a lit clock face on its chest
whose small hands go round, an open belfry for a head with a bell that swings inside it, and a stepped iron spire.
Its arms are the clock's hands, flat blades on cogs at its shoulders: a long minute hand (left) and a short hour hand
(right), splayed out at rest. Under it hangs the pendulum, a brass rod and an iron weight that swings between its legs
in time with the clock. Its light story is the face: a pale backlit dial, the brightest thing on it, then the belfry's
lamp, the inlays down the hands and the lamp in the pendulum's weight, all the white-blue of an electric arc.

It is too tall to reach you standing up, so it moves like a drinking bird: the tower stays rigid and tips at the hips,
the legs folding under it and the hips drawing back over its heels, and it rights itself after every blow. Moves
(SPEC §35.11): the minute hand's sweep across the front (LIGHT), the hour hand's chop from twelve to six, its blade
turning edge-on first (LIGHT), Piston Slam (the pendulum swung up behind it, held, then brought down through its legs
onto the floor in front, HEAVY), Colossal Stride (two great strides at you, GRAB), the pendulum kicked back at whoever
is behind it, Spark Halo (the face's hands race round and the twelve hours fly off it as sparks), Release Drones (the
spire lifts off the belfry and the bell rings), Arc Strikes (both hands to twelve, the bell tolls, the arcs fall), and
Overload Arc (both hands come down together onto you and the arc runs down them: PERILOUS, the signature). It wakes on
the stroke of its bell, and dies when the clock stops: the legs give and the tower falls on its face.
"""
import math

from kit import core, plans
from kit.core import DEG, Move, keyed, planted, stage_clips
from kit.paint import edge_glow, radial
import fauna_rig as R
from fauna_rig import Ramp, hexc, SIDES

ID = "gearwright_colossus"
SCALE = 1.8  # the hitbox on vanilla's iron golem: 2.5 x 4.9 blocks; the tower is modelled at true size

# The Clock Tower (docs/bosses/ROLLOUT.md, Euan's pick C, 2026-10-28). Proportions from its silhouette: hips 3.2 blocks
# up on long bent legs (the knee forward, the shin raked back, the foot flat), a 20-wide tower to 5.75 blocks with the
# clock face at 5 blocks, the belfry and the spire above it. Value story: the dial and the lamps brightest, then brass
# (cornices, cogs, bezel, bell, the hands' points), then green copper (the tower's walls), then dark iron.
P = plans.Biped(leg=51, thigh=24, foot=(9, 3, 16), pelvis=(24, 6, 20), spine=(20, 14, 18), chest=(20, 23, 18), neck=(18, 2, 16),
                head=(16, 12, 14), arm=(12, 12, 3), forearm=(4, 54, 2), hand=(2, 10, 2), stance=9, leg_w=(7, 6))
LEG = {"thigh": -18, "shin": 32, "foot": -14}  # the rest bend: knee forward, shin back, foot flat
REST_ARM = {"l": (-10.0, 0.0, -64.0), "r": (-6.0, 0.0, 40.0)}  # the minute hand out to its left, the hour hand to its right
LONG, SHORT = 54, 38  # the hands' lengths from the hub, pixels
BOB = (0, 6, 0)  # the pendulum weight's middle, in its bone's space


def _cog(parent, name, at, size, depth, axis="z"):
    """A cog on its own bone: two squares on one axle, the second turned 45 degrees so eight teeth stand round it, and a
    hub. axis: its axle ("z" faces the front, "x" the side)."""
    b = parent.bone(name, at)
    s, h = size, size - 2
    if axis == "z":
        b.cube(name, -s / 2, -s / 2, -depth / 2, s, s, depth)
        t = b.bone(name + "_t", (0, 0, 0), (0, 0, 45))
        t.cube(name + "_t", -h / 2, -h / 2, -depth / 2 + 0.5, h, h, depth - 1)
        b.cube(name + "_hub", -1.5, -1.5, -depth / 2 - 1, 3, 3, depth + 2)
    else:
        b.cube(name, -depth / 2, -s / 2, -s / 2, depth, s, s)
        t = b.bone(name + "_t", (0, 0, 0), (45, 0, 0))
        t.cube(name + "_t", -depth / 2 + 0.5, -h / 2, -h / 2, depth - 1, h, h)
        b.cube(name + "_hub", -depth / 2 - 1, -1.5, -1.5, depth + 2, 3, 3)
    return b


def body():
    r = R.Rig(ID, tex=(512, 512))
    hip = 24 - (24 * math.cos(-LEG["thigh"] * DEG) + 26 * math.cos((LEG["shin"] + LEG["thigh"]) * DEG) + 3)
    pel = r.bone("pelvis", (0, hip, 0))
    pel.cube("pelvis", -12, -4, -10, 24, 6, 20)  # the tower's foot
    pel.cube("plinth", -11, 2, -9, 22, 1, 18)
    pel.cube("bearing", -3, 2, -3, 6, 2, 6)  # the pendulum's bearing
    # legs: long and jointed, iron, the hips' cogs turning with the thighs, a ram down each thigh's back
    for side, nm in ((1, "l"), (-1, "r")):
        mir = side < 0
        th = pel.bone("thigh_" + nm, (side * 9, 0, 0), (LEG["thigh"], 0, 0))
        th.cube("thigh_" + nm, -3.5, -2, -3.5, 7, 26, 7, mirror=mir)
        th.cube("hipband_" + nm, -4.5, 2, -4.5, 9, 3, 9, mirror=mir)
        th.cube("ram_" + nm, -1.5, 7, 3.5, 3, 13, 2, mirror=mir)
        th.cube("kneeguard_" + nm, -3, 17, -4.5, 6, 6, 1, mirror=mir)
        _cog(th, "hipcog_" + nm, (side * 4.5, 0, 0), 10, 2, axis="x")
        sn = th.bone("shin_" + nm, (0, 24, 0), (LEG["shin"], 0, 0))
        _cog(sn, "kneecog_" + nm, (side * 4, 0, 0), 8, 2, axis="x")
        sn.cube("shin_" + nm, -3, -1, -3, 6, 25, 6, mirror=mir)
        sn.cube("greave_" + nm, -2, 3, -4, 4, 14, 1, mirror=mir)  # green copper down its front
        sn.cube("ankle_" + nm, -3.5, 21, -3.5, 7, 4, 7, mirror=mir)
        ft = sn.bone("foot_" + nm, (0, 26, 0), (LEG["foot"], 0, 0))
        ft.cube("foot_" + nm, -4.5, 0, -11, 9, 3, 16, mirror=mir)
        ft.cube("toe_" + nm, -2.5, 1, -15, 5, 2, 4, mirror=mir)
        ft.cube("spur_" + nm, -1.5, 1, 5, 3, 2, 3, mirror=mir)
    # the tower: a lower shaft and an upper one with the clock face, cornices at the joins, buttresses on the corners;
    # it bends at the waist a little and not at all above it
    sp = pel.bone("spine", (0, -4, 0))
    sp.cube("spine", -10, -14, -9, 20, 14, 18)
    sp.cube("cornice0", -11, -1, -10, 22, 2, 20)
    for k, (x, z) in enumerate(((-12, -11), (8, -11), (-12, 7), (8, 7))):
        sp.cube(f"buttress{k}", x, -13, z, 4, 13, 4)
    for k, x in enumerate((-5, 3)):  # two lit slits in the lower shaft
        sp.cube(f"slit{k}", x, -11, -9.5, 2, 6, 1)
    ch = sp.bone("chest", (0, -14, 0))
    ch.cube("chest", -10, -23, -9, 20, 23, 18)
    ch.cube("cornice1", -11, -1, -10, 22, 2, 20)
    ch.cube("cornice2", -11, -22, -10, 22, 2, 20)
    for k, (x, z) in enumerate(((-11.5, -10.5), (8.5, -10.5), (-11.5, 7.5), (8.5, 7.5))):
        ch.cube(f"pier{k}", x, -8, z, 3, 8, 3)
    # the clock face: a brass bezel, a pale dial lit from behind, twelve dark marks, two small hands that go round
    ch.cube("bezel", -9, -21, -10, 18, 18, 1)
    ch.cube("dial", -8, -20, -11, 16, 16, 1)
    for k in range(12):
        a = k * 30 * DEG
        w, h = ((2, 3) if k % 6 == 0 else (3, 2)) if k % 3 == 0 else (1, 1)
        ch.cube(f"mark{k}", round(6 * math.sin(a) - w / 2, 1), round(-12 - 6 * math.cos(a) - h / 2, 1), -11.5, w, h, 1)
    dh = ch.bone("dial_h", (0, -12, -11))
    dh.cube("dial_h", -1, -4, -1, 2, 5, 1)
    dm = ch.bone("dial_m", (0, -12, -11))
    dm.cube("dial_m", -0.5, -7, -2, 1, 8, 1)
    ch.cube("dial_hub", -1, -13, -14, 2, 2, 1)
    # the hands: a cog on an iron axle out of each side, the blade in front of it, a tail past the hub; the blade turns
    # on its own length (the forearm bone's y) to go edge-on for a blow
    for side, nm in ((1, "l"), (-1, "r")):
        sh = ch.bone("shoulder_" + nm, (side * 10, -15, 0))
        sh.cube("axle_" + nm, 0 if side > 0 else -6, -2, -2, 6, 4, 4, mirror=side < 0)
        arm = sh.bone("arm_" + nm, (side * 6, 0, 0), REST_ARM[nm])
        _cog(arm, "cog_" + nm, (0, 0, 0), 12 if side > 0 else 10, 3)
        fa = arm.bone("forearm_" + nm, (0, 0, -2))
        if side > 0:  # the minute hand: a long blade, a Breguet ring, an arrow
            fa.cube("tail_l", -2, -9, -1.5, 4, 7, 2)
            fa.cube("weight_l", -3, -13, -1.5, 6, 4, 2)
            fa.cube("blade_l", -1.5, 2, -1.5, 3, 28, 2)
            fa.cube("ring_l0", -4, 30, -1.5, 2, 8, 2)
            fa.cube("ring_l1", 2, 30, -1.5, 2, 8, 2)
            fa.cube("ring_l2", -2, 30, -1.5, 4, 2, 2)
            fa.cube("ring_l3", -2, 36, -1.5, 4, 2, 2)
            fa.cube("neck_l", -1, 38, -1.5, 2, 6, 2)
            fa.cube("inlay_l", -0.5, 5, -2, 1, 22, 1)
            hd_ = fa.bone("hand_l", (0, 44, 0))
            hd_.cube("arrow_l0", -3, 0, -1.5, 6, 3, 2)
            hd_.cube("arrow_l1", -2, 3, -1.5, 4, 3, 2)
            hd_.cube("arrow_l2", -1, 6, -1.5, 2, 4, 2)
        else:  # the hour hand: shorter and broader, a spade, a point
            fa.cube("tail_r", -2, -7, -1.5, 4, 5, 2)
            fa.cube("blade_r", -2, 2, -1.5, 4, 18, 2)
            fa.cube("spade_r0", -4, 20, -1.5, 8, 4, 2)
            fa.cube("spade_r1", -3, 24, -1.5, 6, 3, 2)
            fa.cube("spade_r2", -2, 27, -1.5, 4, 3, 2)
            fa.cube("inlay_r", -0.5, 4, -2, 1, 14, 1)
            hd_ = fa.bone("hand_r", (0, 30, 0))
            hd_.cube("point_r", -1, 0, -1.5, 2, 8, 2)
    # the belfry for a head: a ledge, four posts, a roof, a lamp under it, and a bell that swings between the arches
    nk = ch.bone("neck", (0, -23, 0))
    nk.cube("neck", -9, -2, -8, 18, 2, 16)
    hd = nk.bone("head", (0, -2, 0))
    for k, (x, z) in enumerate(((-8, -7), (5, -7), (-8, 4), (5, 4))):
        hd.cube(f"post{k}", x, -10, z, 3, 10, 3)
    hd.cube("roof", -9, -12, -8, 18, 2, 16)
    hd.cube("lamp", -3, -10, -3, 6, 1, 6)
    bell = hd.bone("bell", (0, -9, 0))
    bell.cube("bell_crown", -2, 0, -2, 4, 2, 4)
    bell.cube("bell", -3, 2, -3, 6, 3, 6)
    bell.cube("bell_lip", -4, 5, -4, 8, 2, 8)
    cl = bell.bone("clapper", (0, 2, 0))
    cl.cube("clapper", -0.5, 0, -0.5, 1, 5, 1)
    cl.cube("clapper_ball", -1, 5, -1, 2, 2, 2)
    spire = hd.bone("spire", (0, -12, 0))
    y = 0
    for k, (w, h, d) in enumerate(((20, 3, 18), (15, 4, 13), (11, 4, 9), (7, 5, 5), (3, 6, 3))):
        spire.cube(f"spire{k}", -w / 2, y - h, -d / 2, w, h, d)
        y -= h
    spire.cube("vane_rod", -0.5, y - 6, -0.5, 1, 6, 1)
    spire.cube("vane", -1, y - 5, -0.5, 5, 1, 1)
    spire.cube("vane_tail", -3, y - 6, -0.5, 2, 3, 1)
    # the pendulum: a brass rod from the bearing and a stepped iron weight with a lamp in it, 4 px off the floor
    pd = pel.bone("pendulum", (0, 4, 0))
    pd.cube("rod", -1, 0, -1, 2, 32, 2)
    bob = pd.bone("bob", (0, 32, 0))
    bob.cube("bob", -4.5, 0, -3, 9, 8, 6)
    bob.cube("bob_band", -5.5, 2, -3.5, 11, 2, 7)
    bob.cube("bob_foot", -3.5, 8, -2.5, 7, 3, 5)
    bob.cube("bob_lamp", -2, 5, -3.5, 4, 2, 1)
    r.look = [("head", 0.55, 0.3), ("neck", 0.2, 0.1)]
    r.size = 1.0  # its moves are written for its own legs
    r.shadow = 2.2
    # secondary parts (SPEC §35.10): the bell and its clapper swing inside the belfry, the pendulum and the spire carry
    # a little weight
    r.springs = [("bell", 0.7, 1.3, 0.12), ("clapper", 0.8, 2.2, 0.2), ("pendulum", 0.3, 0.6, 0.18), ("spire", 0.08, 2.4, 0.4)]
    r.guard = ["bob", "hand_l", "hand_r"]  # the weight and the hands' points never go into the ground (core.check)
    return r


# ---------------------------------------------------------------------------------------------------------------------
# Posing helpers

def _unit(v):
    n = math.sqrt(sum(c * c for c in v))
    return tuple(c / n for c in v)


def _aim(r, pose, side, d, near=(0.0, 0.0, 0.0)):
    """The arm's turn (degrees, on top of its rest) that points a hand along d, a direction in model space (x to its
    left, y down, z back), with the tower where the pose puts it; of the two ways to get there, the one nearest near."""
    fr = core.frames(r, core.as_sample(pose))
    x, y, z = R._mv(core._t(fr["shoulder_" + side][0]), _unit(d))
    a = math.asin(max(-1.0, min(1.0, z)))
    rx0, _, rz0 = REST_ARM[side]
    c = math.atan2(-x, y) if math.hypot(x, y) > 1e-4 else (near[2] + rz0) * DEG
    best = None
    for A, C in ((a, c), (math.pi - a, c + math.pi)):
        for ka in (-360, 0, 360):
            for kc in (-360, 0, 360):
                dx, dz = A / DEG + ka - rx0, C / DEG + kc - rz0
                cost = abs(dx - near[0]) + abs(dz - near[2])
                if best is None or cost < best[0]:
                    best = (cost, (round(dx, 2), 0.0, round(dz, 2)))
    return best[1]


def _sag(side, x):
    """A near hint for _aim: the hand swung in the plane through its shoulder from front to back, x degrees from hanging
    (-90 forward, -180 straight up), rather than out round its side."""
    return (x - REST_ARM[side][0], 0.0, -REST_ARM[side][2])


def _bow(f, down=0.0, yaw=0.0, roll=0.0, back=None):
    """The tower's bow: it tips at the hips, a little give at the waist and none above, the hips sinking and drawing
    back over its heels so it stays balanced."""
    back = 0.1 * max(0.0, f) if back is None else back
    return {"pelvis": {"rot": (f, yaw, roll), "pos": (0, -down, back)}, "spine": (0.14 * f, 0.3 * yaw, 0), "chest": (0.04 * f, 0.2 * yaw, 0)}


def _hang(pose, swing=0.0, sway=0.0):
    """The pendulum hanging plumb under a tipped tower, plus a swing (degrees from plumb, + back) and a sway."""
    p = pose.get("pelvis", {})
    rx, _, rz = p.get("rot", (0, 0, 0)) if isinstance(p, dict) else p
    pose["pendulum"] = (-rx + swing, 0.0, -rz + sway)
    return pose


def _pose(r, bow=(0.0, 0.0), yaw=0.0, roll=0.0, long=None, short=None, twist=(0.0, 0.0), swing=0.0, head=(0, 0, 0), bell=0.0,
          near=None, at=None, **extra):
    """A key pose: the bow (degrees, how far the hips sink), the hands aimed along directions (None: at rest), the blades'
    turn on their own length, the pendulum's swing from plumb, the belfry's turn and the bell's swing; its feet planted
    (at: offsets from where they stand, as core.plant), since a move that carries it forward isn't planted for it."""
    pose = _bow(bow[0], bow[1], yaw, roll)
    near = near or {}
    for side, d in (("l", long), ("r", short)):
        if d is not None:
            pose["arm_" + side] = _aim(r, pose, side, d, near.get(side, (0.0, 0.0, 0.0)))
    if twist[0]:
        pose["forearm_l"] = (0, twist[0], 0)
    if twist[1]:
        pose["forearm_r"] = (0, twist[1], 0)
    _hang(pose, swing)
    if any(head):
        pose["head"] = head
    if bell:
        pose["bell"] = (bell, 0, 0)
    pose.update(extra)
    return core.plant(r, pose, at=at)


def _turn(r, mv, bone, axis, turns, t0, t1):
    """Whole turns on a bone in a move's clip, after staging: from none at t0 to all of them at t1, eased (a whole turn is
    no turn, so it hands back to the idle as it was)."""
    c = r.clips[core.clip_name(mv)]
    old = c.tracks.get((bone, "rot"))
    keys = []
    for k in range(int(round(c.length / core.TICK)) + 1):
        t = min(c.length, k * core.TICK)
        base = list(R._interp(old, t)) if old else [0.0, 0.0, 0.0]
        f = 0.0 if t <= t0 else 1.0 if t >= t1 else core.ease_io((t - t0) / (t1 - t0))
        base[axis] += 360.0 * turns * f
        keys.append((t, tuple(base), "cat"))  # a key a tick on a smooth curve: its speed changes smoothly too
    c.tracks[(bone, "rot")] = keys


def _leg(side):
    """A leg's parts below the hip: the guards of a fall."""
    return [f"shin_{side}", f"kneecog_{side}", f"kneecog_{side}_t", f"foot_{side}"]


def _strip(r, clip, bones):
    for b in bones:
        for tgt in ("rot", "pos"):
            r.clips[clip].tracks.pop((b, tgt), None)


# ---------------------------------------------------------------------------------------------------------------------
# Clips

def _idle(r):
    """Standing: the pendulum swings in time with the clock (one beat each way a loop), the tower rocks with it by a
    hair, the bell sways, the belfry looks about, and the face's minute hand sweeps round once."""
    L = 3.6
    n = 72
    keys = []
    for k in range(n + 1):
        t = L * k / n
        s, c = math.sin(2 * math.pi * k / n), math.cos(2 * math.pi * k / n)
        keys.append((t, {"pelvis": {"rot": (-0.5 * s, 0, 0.4 * c), "pos": (0, 0, 0)}, "pendulum": (6 * s, 0, -0.4 * c),
                         "bell": (2.5 * math.sin(2 * math.pi * k / n - 0.8), 0, 0), "head": (0, 3 * math.sin(math.pi * k / n * 2 + 1.2), 0)}))
    keyed(r, "idle", L, planted(r, keys), loop=True, interp="lin")
    c = r.clips["idle"]
    # the face's minute hand sweeps round once a loop (a ticking hand caught mid-tick by a move's blend would stutter)
    m = [(L * k / 36, (0, 0, 10 * k), "lin") for k in range(37)]
    c.tracks[("dial_m", "rot")] = m


def _locomotion(r):
    """A rocking gait: each long leg swings through with the knee lifting, the tower rolls onto the planted foot and
    yaws a little, the pendulum swings across the stride and the bell rocks; the hands swing a little like a clock's."""
    for name, L, sw, roll in (("walk", 1.6, 18.0, 3.5), ("run", 1.0, 26.0, 4.5)):
        keys = []
        n = 8
        for k in range(n + 1):
            p = k / n
            s, c = math.sin(p * 2 * math.pi), math.cos(p * 2 * math.pi)
            lean = 0 if name == "walk" else 8
            pose = {
                "thigh_l": (-sw * s - lean, 0, 0), "thigh_r": (sw * s - lean, 0, 0),
                "shin_l": (max(0, c) * sw * 1.5 + 3 + lean, 0, 0), "shin_r": (max(0, -c) * sw * 1.5 + 3 + lean, 0, 0),
                "foot_l": (sw * s * 0.35, 0, 0), "foot_r": (-sw * s * 0.35, 0, 0),
                "pelvis": {"rot": (lean, 3 * s, roll * s), "pos": (0, 1.0 * abs(c) + (0.6 if lean else 0), 0)},
                "spine": (1, -1.5 * s, -0.8 * s), "chest": (0.5, -1 * s, 0),
                "arm_l": (5 * s, 0, 2 * abs(s)), "arm_r": (-5 * s, 0, -2 * abs(s)),
                "head": (-1.5 * abs(c), 2 * s, 0), "bell": (7 * c, 0, 0),
            }
            _hang(pose, -9 * s, -roll * s * 0.6)
            keys.append((L * p, pose))
        keyed(r, name, L, keys, loop=True)
    r.walk_stride = 3.9  # blocks a cycle: two steps of about 2 blocks on legs this long
    r.run_stride = 5.6
    r.run_speed = (0.14, 0.3)
    for nm, sgn in (("turn_l", 1), ("turn_r", -1)):
        keyed(r, nm, 0.6, [
            (0, {}),
            (0.15, {"thigh_l": (-16, 0, 0), "shin_l": (24, 0, 0), "pelvis": {"rot": (0, 5 * sgn, -2), "pos": (0, -0.6, 0)}, "chest": (0, 3 * sgn, 0),
                    "pendulum": (0, 0, 4)}),
            (0.3, {"pelvis": {"rot": (0, 3 * sgn, 0), "pos": (0, 0, 0)}}),
            (0.45, {"thigh_r": (-16, 0, 0), "shin_r": (24, 0, 0), "pelvis": {"rot": (0, 5 * sgn, 2), "pos": (0, -0.6, 0)}, "chest": (0, 3 * sgn, 0),
                    "pendulum": (0, 0, -4)}),
            (0.6, {}),
        ], loop=True)


def _reactions(r):
    """It rocks back on its hips when struck (the bell swinging, the pendulum kicking), breaks to a crouch when its
    stance goes, and dies when the clock stops."""
    keyed(r, "hurt", 0.55, planted(r, [(0, {}), (0.07, _hang({"pelvis": {"rot": (-5, 0, 0), "pos": (0, 0, 1)}, "spine": (-1.5, 0, 0), "head": (-5, 0, 0),
                                                                "arm_l": (-6, 0, 6), "arm_r": (-6, 0, -6), "bell": (22, 0, 0)}, -10)),
                                       (0.3, _hang({"pelvis": {"rot": (1.5, 0, 0), "pos": (0, 0, 0)}, "head": (2, 0, 0), "bell": (-12, 0, 0)}, 6)), (0.55, {})]))
    for nm, sgn in (("hurt_l", 1), ("hurt_r", -1)):  # struck from its left it turns away to its right and tips away
        keyed(r, nm, 0.6, planted(r, [(0, {}), (0.08, _hang({"pelvis": {"rot": (-2, -7 * sgn, -4 * sgn), "pos": (-0.6 * sgn, 0, 0)}, "chest": (0, -3 * sgn, -1 * sgn),
                                                              "head": (-3, -10 * sgn, 0), "arm_" + ("l" if sgn > 0 else "r"): (0, 0, 8 * sgn), "bell": (14, 0, 10 * sgn)}, 0, 6 * sgn)),
                                           (0.32, _hang({"pelvis": {"rot": (1, 2 * sgn, 1.5 * sgn), "pos": (0, 0, 0)}, "head": (1, 3 * sgn, 0)}, 0, -3 * sgn)), (0.6, {})]))
    # the kneel: the gears slip, the knees fold, the tower bows and the hands droop toward six (held while it's broken)
    kneel = _hang({"pelvis": {"rot": (16, 0, 3), "pos": (0, -15, 3)}, "spine": (4, 0, 0), "chest": (2, 0, 0), "head": (14, 0, 6),
                   "arm_l": (6, 0, 34), "arm_r": (6, 0, -22), "bell": (16, 0, 0)}, 30)
    keyed(r, "kneel", 0.6, planted(r, [(0, {}), (0.2, _hang({"pelvis": {"rot": (6, 0, 1), "pos": (0, -9, 1)}, "head": (6, 0, 0), "arm_l": (0, 0, 14), "bell": (-14, 0, 0)}, 10)),
                                       (0.6, kneel)]))
    keyed(r, "rise", 0.8, planted(r, [(0, kneel), (0.4, _hang({"pelvis": {"rot": (4, 0, 1), "pos": (0, -5, 1)}, "head": (-4, 0, 0), "arm_l": (0, 0, 10),
                                                               "arm_r": (0, 0, -6), "bell": (-10, 0, 0)}, -6)), (0.8, {})]))
    # the death: the clock stops with a jolt, the legs give, and the tower falls on its face (its hips over its knees)
    stop = core.plant(r, _hang({"pelvis": {"rot": (-6, 0, 0), "pos": (0, 0.5, 1)}, "head": (-8, 0, 0), "arm_l": (-6, 0, -6), "arm_r": (-6, 0, 6),
                                "bell": (30, 0, 0)}, -14))
    give = core.plant(r, {"pelvis": {"rot": (14, 0, 3), "pos": (0, -16, 2)}, "spine": (3, 0, 0), "head": (10, 0, 4),
                          "arm_l": (8, 0, 50), "arm_r": (8, 0, -30), "bell": (-24, 0, 0), "pendulum": (-10, 0, 0)})  # the knees give, the feet stay
    # up on its knees, the thighs straight down, the shins and feet back along the floor, the tower tipping
    tip = {"pelvis": {"rot": (46, 0, 0), "pos": (0, -22.5, -2)}, "thigh_l": (-28, 0, 0), "shin_l": (58, 0, 0), "foot_l": (84, 0, 0),
           "thigh_r": (-28, 0, 0), "shin_r": (58, 0, 0), "foot_r": (84, 0, 0), "spine": (4, 0, 0), "head": (6, 0, 6),
           "arm_l": (-30, 0, 60), "arm_r": (-30, 0, -40), "bell": (40, 0, 0), "pendulum": (-60, 0, 0), "spire": (6, 0, 0)}
    down = dict(tip)
    # down flat on its face like a toppled tower, hinged over its knees, the legs straight out behind it along the floor
    down.update({"pelvis": {"rot": (84, 0, 8), "pos": (0, -36, -26)}, "thigh_l": (5, 0, 0), "shin_l": (9, 0, 0), "foot_l": (-19, 0, 0),
                 "thigh_r": (-4, 0, -4), "shin_r": (17, 0, 0), "foot_r": (16, 0, 0), "head": (2, 0, 8), "arm_l": (-70, 0, 70), "arm_r": (-70, 0, -60), "bell": (-30, 0, 0),
                 "pendulum": (-90, 0, 0), "spire": (12, 0, 4)})
    legs = ("thigh", "shin", "foot")
    # sinking, the feet still under it; then halfway down on its face, the knees lifting off the floor as the legs swing back
    sink = core.plant(r, {b: v for b, v in core._lerp_pose(give, tip, 0.5).items() if not b.startswith(legs)})
    fall = core._lerp_pose(tip, down, 0.5)
    fall.update({"thigh_l": (10, 0, 0), "shin_l": (4, 0, 0), "foot_l": (75, 0, 0), "thigh_r": (2, 0, 0), "shin_r": (20, 0, 0), "foot_r": (53, 0, 0)})
    # a key between each pair, so the legs never pass through the floor on the way: planted while the feet are under it,
    # then the least turn of the thigh, shin and foot that keeps the knee, shin and foot above the floor
    main = [(0.35, stop), (1.0, give), (1.4, sink), (1.8, tip), (2.1, fall), (2.4, down)]
    keys = [(0, {})]
    for (t0, a), (t1, b) in zip(main, main[1:]):
        mid = core._lerp_pose(a, b, 0.5)
        if t1 <= 1.4:
            mid = core.plant(r, mid)
        else:
            for sd in ("l", "r"):
                mid = core.keep_above(r, mid, _leg(sd),
                                      [(f"thigh_{sd}", 0, 40), (f"shin_{sd}", 0, 40), (f"foot_{sd}", 0, 60)])
        keys += [(t0, a), ((t0 + t1) / 2, mid)]
    keyed(r, "death", 3.0, keys + [(2.4, down), (3.0, dict(down, bell=(8, 0, 0), spire=(9, 0, 3)))])


# ---------------------------------------------------------------------------------------------------------------------
# Moves

FWD = (0.0, 0.0, -1.0)


def sweep(r):
    """The minute hand's sweep (LIGHT): the tower rocks back and coils to its left, the long hand swung out wide behind
    it; then it tips forward and the hand scythes across the front at a man's waist, out past its right side."""
    sg = 1
    gather = _pose(r, (-4, 0), yaw=-10, long=(0.9, 0.1, 0.4), short=(-0.7, 0.6, -0.3), swing=-6, head=(0, 10, 0))
    peak = _pose(r, (-8, 0), yaw=-22, long=(0.75, -0.05, 0.66), short=(-0.6, 0.5, -0.6), swing=-10, head=(-2, 18, 0),
                 near={"l": gather["arm_l"], "r": gather["arm_r"]})
    hit = _pose(r, (30, 12), yaw=8, long=(0.55, 0.62, -0.56), short=(-0.5, 0.86, 0.1), swing=4, head=(6, -8, 0),
                near={"l": peak["arm_l"], "r": peak["arm_r"]})
    out = _pose(r, (34, 13), yaw=24, long=(-0.5, 0.62, -0.6), short=(-0.4, 0.9, 0.2), swing=8, head=(6, -16, 0),
                near={"l": hit["arm_l"], "r": hit["arm_r"]})
    back = _pose(r, (14, 5), yaw=12, long=(-0.2, 0.95, -0.2), swing=-4, near={"l": out["arm_l"]})
    return Move("sweep", "LIGHT", 14, 5, 15, title="", track=8.0, rng=(0, 4.5), kinds=("combo",), damage=1.0, knock=0.6, advance=0.5,
                hit=[("forearm_l", (0, LONG, 0), 3)],
                stages={"windup": [(0.34, gather), (1.0, peak)], "active": [(0.45, hit), (1.0, out)], "recover": [(0.5, back)]},
                lag={"head": 0.05, "arm_l": 0.0, "forearm_l": 0, "hand_l": 0})


def chop(r):
    """The hour hand's chop (LIGHT): the short hand climbs to twelve over its right shoulder and its blade clicks round
    edge-on; the tower leans back on its heels, then tips forward and the hand comes down through three to six in
    front of it."""
    up1 = _pose(r, (-4, 0), yaw=6, short=(-0.3, -0.5, -0.8), long=(0.8, 0.55, -0.2), twist=(0, 90), swing=-6, head=(0, -8, 0),
                near={"r": _sag("r", -120)})
    peak = _pose(r, (-9, 0), yaw=12, short=(-0.15, -0.97, 0.2), long=(0.7, 0.55, -0.45), twist=(0, 90), swing=-12, head=(-4, -12, 0),
                 near={"r": _sag("r", -185), "l": up1["arm_l"]})
    hit = _pose(r, (38, 14), yaw=-6, short=(-0.12, 0.55, -0.83), long=(0.75, 0.6, 0.2), twist=(0, 90), swing=6, head=(8, 6, 0),
                near={"r": _sag("r", -70), "l": peak["arm_l"]})
    low = _pose(r, (42, 15), yaw=-8, short=(-0.1, 0.85, -0.52), long=(0.72, 0.66, 0.2), twist=(0, 90), swing=10, head=(8, 8, 0),
                near={"r": _sag("r", -45), "l": hit["arm_l"]})
    back = _pose(r, (14, 5), yaw=-3, short=(-0.4, 0.9, -0.1), twist=(0, 30), swing=-6, near={"r": low["arm_r"]})
    return Move("chop", "LIGHT", 12, 4, 15, track=8.0, rng=(0, 3.6), kinds=("combo",), damage=1.1, knock=0.5, advance=0.4,
                hit=[("forearm_r", (0, SHORT, 0), 3)],
                stages={"windup": [(0.34, up1), (1.0, peak)], "active": [(0.5, hit), (1.0, low)], "recover": [(0.5, back)]},
                lag={"head": 0.05, "arm_r": 0.0, "forearm_r": 0, "hand_r": 0})


def slam(r):
    """Piston Slam (HEAVY): the pendulum swings forward, then far up behind it while the tower rears back on its heels
    with both hands raised to ten and two; it holds; then the tower drops on folding knees and the weight comes down
    through its legs onto the floor in front of its feet."""
    dip = _pose(r, (8, 4), long=(0.75, 0.6, 0), short=(-0.75, 0.6, 0), swing=-34, head=(6, 0, 0))
    peak = _pose(r, (-10, -0.5), long=(0.7, -0.68, 0.15), short=(-0.7, -0.68, 0.15), swing=104, head=(-8, 0, 0), bell=12,
                 near={"l": dip["arm_l"], "r": dip["arm_r"]})
    hit = _pose(r, (20, 14), long=(0.6, 0.75, -0.3), short=(-0.6, 0.75, -0.3), swing=-26, head=(10, 0, 0), bell=-20,
                near={"l": peak["arm_l"], "r": peak["arm_r"]})
    settle = _pose(r, (16, 12), long=(0.62, 0.74, -0.25), short=(-0.62, 0.74, -0.25), swing=-24, head=(8, 0, 0), bell=10,
                   near={"l": hit["arm_l"], "r": hit["arm_r"]})
    bounce = _pose(r, (6, 4), long=(0.7, 0.68, 0), short=(-0.7, 0.68, 0), swing=22, head=(2, 0, 0), bell=-8,
                   near={"l": settle["arm_l"], "r": settle["arm_r"]})
    swing = _pose(r, (2, 1), swing=-12, bell=4)
    return Move("slam", "HEAVY", 20, 6, 22, title="Piston Slam", hold=10, track=4.0, ability="Piston Slam", origin=(0, 0, 1.4),
                rng=(0, 4.5), kinds=("area", "near"), damage=1.3, knock=1.0, disc=3.0, disc_at="hit",
                hit=[("bob", BOB, 7)],
                stages={"windup": [(0.3, dip), (1.0, peak)], "active": [(0.45, hit), (1.0, settle)], "recover": [(0.4, bounce), (0.75, swing)]},
                lag={"head": 0.05, "pendulum": 0, "bob": 0}, ground=[(["bob"], [("pendulum", 0, 80)])], bite=0.45)


def stride(r):
    """Colossal Stride (GRAB): it dips and tips forward, the hands swept back, then takes two great strides at you, the
    pendulum swinging under it; it brakes back on its heels at the end."""
    # root motion carries it, so its feet are placed here: planted under it in the tell, then striding
    set_ = _pose(r, (10, 6), long=(0.75, 0.45, 0.5), short=(-0.75, 0.45, 0.5), swing=-8, head=(-6, 0, 0))
    lean = _pose(r, (22, 8), long=(0.6, 0.35, 0.72), short=(-0.6, 0.35, 0.72), swing=-14, head=(-14, 0, 0),
                 near={"l": set_["arm_l"], "r": set_["arm_r"]})

    def step(lead, pend):
        o = "r" if lead == "l" else "l"
        return _pose(r, (24, 4), long=(0.6, 0.4, 0.7), short=(-0.6, 0.4, 0.7), swing=pend, head=(-14, 0, 0), bell=-pend * 0.5,
                     near={"l": lean["arm_l"], "r": lean["arm_r"]}, at={lead: (0, -12, -20), o: (0, 0, 16)})
    a, b = step("l", 18), step("r", -18)
    brake = _pose(r, (-8, 4), long=(0.8, 0.55, -0.2), short=(-0.8, 0.55, -0.2), swing=-24, head=(6, 0, 0), bell=14,
                  near={"l": lean["arm_l"], "r": lean["arm_r"]}, at={"l": (0, 0, -10), "r": (0, 0, 6)})
    return Move("stride", "GRAB", 16, 14, 18, title="Colossal Stride", track=4.0, lock=12, advance=7.0, rng=(5, 14), kinds=("gap",),
                damage=1.2, knock=1.2, plant=(),
                hit=[("pelvis", (0, 0, -14), 12), ("bob", BOB, 7), ("shin_l", (0, 24, -3), 5), ("shin_r", (0, 24, -3), 5)],
                stages={"windup": [(0.34, set_), (1.0, lean)], "active": [(0.25, a), (0.5, b), (0.75, a), (1.0, b)],
                        "recover": [(0.5, brake), (0.72, _pose(r, (3, 1), swing=10))]},
                lag={"head": 0.05})


def kick(r):
    """For whoever is behind it (LIGHT): the belfry turns to look, the tower rocks back and the pendulum swings forward,
    then the tower tips sharply forward and the weight whips back between its legs into them."""
    cock = _pose(r, (-6, 0), swing=-40, head=(0, 40, 0), long=(0.8, 0.6, 0.1), short=(-0.8, 0.6, 0.1))
    peak = _pose(r, (-10, 0), swing=-62, head=(0, 55, 0), long=(0.85, 0.5, -0.1), short=(-0.85, 0.5, -0.1),
                 near={"l": cock["arm_l"], "r": cock["arm_r"]})
    hit = _pose(r, (24, 6), swing=70, head=(4, 40, 0), long=(0.75, 0.6, -0.3), short=(-0.75, 0.6, -0.3),
                near={"l": peak["arm_l"], "r": peak["arm_r"]})
    high = _pose(r, (28, 7), swing=92, head=(4, 30, 0), long=(0.72, 0.62, -0.32), short=(-0.72, 0.62, -0.32),
                 near={"l": hit["arm_l"], "r": hit["arm_r"]})
    back = _pose(r, (6, 2), swing=14, head=(0, 12, 0))
    return Move("kick", "LIGHT", 10, 4, 14, track=0.0, rng=(0, 3.5), kinds=("behind",), damage=1.0, knock=1.0,
                hit=[("bob", BOB, 7), ("pendulum", (0, 30, 0), 2)],
                stages={"windup": [(0.34, cock), (1.0, peak)], "active": [(0.5, hit), (1.0, high)], "recover": [(0.5, back)]},
                lag={"head": 0.04, "pendulum": 0, "bob": 0})


def halo(r):
    """Spark Halo (HEAVY): the tower straightens and both hands rise to ten and two; the face's hands race round faster
    and faster until the twelve hours fly off the dial as sparks, and the tower rocks back from it."""
    rise = _pose(r, (-3, -0.5), long=(0.8, -0.3, 0.1), short=(-0.8, -0.3, 0.1), swing=-6, head=(-4, 0, 0))
    peak = _pose(r, (-5, -1), long=(0.72, -0.68, 0.1), short=(-0.72, -0.68, 0.1), swing=-10, head=(-8, 0, 0), bell=10,
                 near={"l": rise["arm_l"], "r": rise["arm_r"]})
    burst = _pose(r, (-12, -1), long=(0.82, -0.56, 0.12), short=(-0.82, -0.56, 0.12), swing=22, head=(-14, 0, 0), bell=-26,
                  near={"l": peak["arm_l"], "r": peak["arm_r"]})
    rock = _pose(r, (-2, 0), long=(0.7, -0.7, 0.05), short=(-0.7, -0.7, 0.05), swing=-14, head=(-6, 0, 0), bell=18,
                 near={"l": burst["arm_l"], "r": burst["arm_r"]})
    after = _pose(r, (4, 2), long=(0.8, 0.4, 0), short=(-0.8, 0.4, 0), swing=-10, bell=12, near={"l": burst["arm_l"], "r": burst["arm_r"]})
    return Move("halo", "HEAVY", 16, 12, 16, title="Spark Halo", track=0.0, ability="Spark Halo", origin=(0, 5.0, 0.7), rng=(0, 14),
                kinds=("area", "ranged"), damage=0, knock=0,
                stages={"windup": [(0.34, rise), (1.0, peak)], "active": [(0.3, burst), (0.7, rock), (1.0, burst)], "recover": [(0.5, after)]},
                lag={"head": 0.05})


def overload(r):
    """Overload Arc (PERILOUS, the signature): both hands climb together to twelve, then come down as one and level on
    you, the minute hand over the hour hand; the face blazes, its hands spinning, and the arc runs down them."""
    up = _pose(r, (-6, -0.5), long=(0.25, -0.97, 0.05), short=(-0.25, -0.97, 0.05), swing=-8, head=(-6, 0, 0), bell=14,
               near={"l": _sag("l", -175), "r": _sag("r", -175)})
    level = _pose(r, (12, 4), long=(-0.22, 0.42, -0.88), short=(0.2, 0.44, -0.88), swing=6, head=(4, 0, 0), bell=-10,
                  near={"l": _sag("l", -110), "r": _sag("r", -110)})
    kick_ = _pose(r, (6, 3), long=(-0.22, 0.36, -0.9), short=(0.2, 0.38, -0.9), swing=-8, head=(-2, 0, 0), bell=18,
                  near={"l": level["arm_l"], "r": level["arm_r"]})
    press = _pose(r, (14, 5), long=(-0.2, 0.44, -0.87), short=(0.18, 0.46, -0.87), swing=8, head=(6, 0, 0), bell=-14,
                  near={"l": level["arm_l"], "r": level["arm_r"]})
    after = _pose(r, (6, 2), long=(0.5, 0.86, 0), short=(-0.5, 0.86, 0), swing=8, near={"l": kick_["arm_l"], "r": kick_["arm_r"]})
    return Move("overload", "PERILOUS", 28, 14, 22, title="Overload Arc", track=9.0, lock=18, ability="Overload Arc", rng=(4, 24),
                kinds=("ranged",), signature=True, damage=0, knock=0,
                stages={"windup": [(0.34, up), (1.0, level)], "active": [(0.2, kick_), (0.5, press), (0.75, kick_), (1.0, press)],
                        "recover": [(0.5, after)]},
                lag={"head": 0.05})


def strikes(r):
    """Arc Strikes (PERILOUS): the hands sink and cross before it, then climb to twelve together; the belfry throws back
    and the bell tolls while the arcs fall round it."""
    gather = _pose(r, (10, 6), long=(-0.25, 0.9, -0.4), short=(0.25, 0.9, -0.4), swing=-10, head=(8, 0, 0))
    up = _pose(r, (-8, -1), long=(0.08, -1, 0.02), short=(-0.08, -1, 0.02), swing=12, head=(-12, 0, 0), bell=30,
               near={"l": _sag("l", -178), "r": _sag("r", -178)})
    toll = dict(_pose(r, (-11, -1), long=(0.14, -1, 0.06), short=(-0.03, -1, 0.0), swing=-6, near={"l": up["arm_l"], "r": up["arm_r"]}),
                head=(-14, 8, 0), bell=(-34, 0, 0))
    toll2 = dict(_pose(r, (-6, -0.5), long=(0.03, -1, 0.0), short=(-0.14, -1, 0.06), swing=10, near={"l": up["arm_l"], "r": up["arm_r"]}),
                 head=(-10, -8, 0), bell=(30, 0, 0))
    return Move("strikes", "PERILOUS", 18, 12, 16, title="Arc Strikes", track=0.0, ability="Arc Strikes", rng=(0, 30), kinds=("area", "ranged"),
                damage=0, knock=0,
                stages={"windup": [(0.34, gather), (1.0, up)], "active": [(0.3, toll), (0.65, toll2), (1.0, dict(up, bell=(-20, 0, 0)))],
                        "recover": [(0.5, _pose(r, (4, 1), long=(0.25, -0.15, -0.95), short=(-0.25, -0.15, -0.95), swing=-6,
                                                near={"l": _sag("l", -100), "r": _sag("r", -100)}))]},
                lag={"head": 0.05})


def drones(r):
    """Release Drones: the hands sink to four and eight, the spire lifts off the belfry on its own like a lid and tips
    back, and the bell swings while the drones come out."""
    low = _pose(r, (6, 3), long=(0.7, 0.7, 0), short=(-0.7, 0.7, 0), swing=-6, head=(6, 0, 0))
    lift = dict(_pose(r, (-4, 0), long=(0.75, 0.62, 0.1), short=(-0.75, 0.62, 0.1), swing=8, head=(-6, 0, 0), bell=24,
                      near={"l": low["arm_l"], "r": low["arm_r"]}), spire={"rot": (-16, 0, 0), "pos": (0, 8, 2)})
    ring = dict(_pose(r, (-6, 0), long=(0.85, 0.5, 0.15), short=(-0.85, 0.5, 0.15), swing=-10, head=(-8, 10, 0), near={"l": lift["arm_l"], "r": lift["arm_r"]}),
                bell=(-26, 0, 0), spire={"rot": (-14, 24, 2), "pos": (0, 9, 2)})
    ring2 = dict(_pose(r, (-2, 0), long=(0.7, 0.68, 0.05), short=(-0.7, 0.68, 0.05), swing=10, head=(-4, -10, 0), near={"l": lift["arm_l"], "r": lift["arm_r"]}),
                 bell=(24, 0, 0), spire={"rot": (-18, -16, -2), "pos": (0, 10, 2)})
    return Move("drones", "NONE", 12, 22, 14, title="Release Drones", track=0.0, ability="Release Drones", rng=(0, 99), kinds=("summon",),
                damage=0, knock=0,
                stages={"windup": [(0.5, low), (1.0, lift)], "active": [(0.35, ring), (0.7, ring2), (1.0, lift)], "recover": [(0.5, low)]},
                lag={"head": 0.05, "spire": 0.06})


def chime(r):
    """Its intro: the clock strikes. It settles, the hands hanging at six; the bell tolls three times, the tower jolting
    back on each stroke, and on each the hands click a step out to where they stand."""
    hang = {"l": (0, 0, 60), "r": (0, 0, -36)}
    settle = _pose(r, (4, 3), arm_l=hang["l"], arm_r=hang["r"], head=(6, 0, 0))
    strokes = []
    for k, f in enumerate((0.66, 0.33, 0.0)):
        strokes.append((0.12 + 0.3 * k, _pose(r, (-4, 0), arm_l=(0, 0, 60 * f - 3), arm_r=(0, 0, -36 * f + 2), head=(-6, 4 * (k - 1), 0),
                                              bell=34 if k % 2 == 0 else -34, swing=-8)))
        strokes.append((0.27 + 0.3 * k, _pose(r, (1, 1), arm_l=(0, 0, 60 * f), arm_r=(0, 0, -36 * f), head=(-2, 4 * (k - 1), 0),
                                              bell=-12 if k % 2 == 0 else 12, swing=6)))
    return Move("chime", "NONE", 10, 30, 14, track=0.0, rng=(0, 99), kinds=("intro",), damage=0, knock=0,
                stages={"windup": [(1.0, settle)], "active": strokes + [(1.0, _pose(r, (-2, 0), head=(-4, 0, 0), bell=8))], "recover": []},
                lag={"head": 0.05})


HANDS = [(["hand_l"], [("arm_l", 0, 60), ("arm_l", 2, 40)]), (["hand_r"], [("arm_r", 0, 60), ("arm_r", 2, 40)])]
# the legs as the tower falls: the hip lifts the knee, then the knee the shin, then the ankle the foot, clear of the floor
LEGS = [(_leg(s), [(f"thigh_{s}", 0, 30), (f"shin_{s}", 0, 30), (f"foot_{s}", 0, 40)]) for s in ("l", "r")]
WEIGHT = [(["bob"], [("pendulum", 0, 90)])]


def clips(r):
    _idle(r)
    _locomotion(r)
    _reactions(r)
    mv = [sweep(r), chop(r), slam(r), stride(r), kick(r), halo(r), overload(r), strikes(r), drones(r), chime(r)]
    by = {m.id: m for m in mv}
    for m in mv:
        stage_clips(r, m)
    # the face's hands race through the halo (the minute hand four turns, the hour hand one) and the overload's tell
    W, A = by["halo"].seconds("windup"), by["halo"].seconds("active")
    _turn(r, by["halo"], "dial_m", 2, 3, 0.1, W + 0.15)
    _turn(r, by["halo"], "dial_h", 2, 1, 0.1, W + 0.1)
    W = by["overload"].seconds("windup")
    _turn(r, by["overload"], "dial_m", 2, 6, 0.2, W + 0.3)
    _turn(r, by["overload"], "dial_h", 2, 2, 0.2, W + 0.3)
    # the weight and the hands' points stay out of the ground everywhere but the fall (the slam meets it on purpose)
    own = {core.clip_name(m) for m in mv if m.ground}
    core.ground_pass(r, WEIGHT, clips=[n for n in r.clips if n not in own and n != "death"])
    core.ground_pass(r, HANDS, clips=[n for n in r.clips if n != "death"])
    core.ground_pass(r, WEIGHT + HANDS + LEGS, clips=["death"], skip=())
    return mv


# Its sounds (SPEC §35.8): the bell is its voice; iron, pistons and the crack of an arc under it. Chosen by ear on
# paper, not yet heard in game.
SOUNDS = {
    "tell_light": [("block.vault.close_shutter", 1.4, 0.6), ("block.chain.place", 1.0, 0.6)],
    "tell_heavy": [("block.piston.contract", 1.8, 0.5), ("block.bell.resonate", 1.2, 0.6)],
    "tell_perilous": [("block.bell.use", 2.5, 0.5), ("block.respawn_anchor.charge", 1.8, 0.6), ("entity.warden.sonic_charge", 1.2, 0.9)],
    "tell_grab": [("block.piston.extend", 2.0, 0.5), ("entity.iron_golem.repair", 1.5, 0.5)],
    "swing_light": [("entity.player.attack.sweep", 1.6, 0.55), ("block.grindstone.use", 1.0, 0.6)],
    "swing_heavy": [("entity.wind_charge.wind_burst", 1.6, 0.5), ("block.piston.extend", 1.8, 0.45)],
    "cast": [("item.trident.thunder", 1.4, 1.2), ("block.copper_bulb.turn_on", 1.5, 0.7)],
    "impact": [("block.anvil.land", 2.2, 0.5), ("item.mace.smash_ground_heavy", 2.0, 0.6), ("block.heavy_core.fall", 2.0, 0.6)],
    "roar": [("block.bell.use", 3.0, 0.5), ("block.bell.resonate", 2.5, 0.5)],
    "step": [("entity.iron_golem.step", 1.6, 0.55), ("block.chain.step", 1.0, 0.6)],
}
MOVE_SOUNDS = {"chime": {"swing": "roar"}, "strikes": {"swing": "roar"}, "drones": {"swing": "cast"}}

FIGHT = {
    "intro": {"pose": "idle", "move": "chime"},
    "phases": [
        {"pool": [
            {"move": "sweep", "weight": 3, "then": [["chop"], ["chop", "slam"]]},
            {"move": "chop", "weight": 2, "when": "near"},
            {"move": "slam", "weight": 3},
            {"move": "stride", "weight": 2, "when": "far"},
            {"move": "kick", "weight": 3, "when": "behind"},
            {"move": "halo", "weight": 2},
        ]},
        {"transition": "drones", "pool": [
            {"move": "sweep", "weight": 3, "then": [["chop"], ["chop", "slam"]]},
            {"move": "chop", "weight": 2, "when": "near", "then": [["sweep"]]},
            {"move": "slam", "weight": 3},
            {"move": "stride", "weight": 3, "when": "far", "then": [["slam"]]},
            {"move": "kick", "weight": 3, "when": "behind"},
            {"move": "halo", "weight": 2},
            {"move": "drones", "weight": 1},
        ]},
        {"transition": "strikes", "pool": [
            {"move": "sweep", "weight": 3, "then": [["chop", "slam"]]},
            {"move": "chop", "weight": 2, "when": "near", "then": [["sweep"]]},
            {"move": "slam", "weight": 3, "then": [["kick"]]},
            {"move": "stride", "weight": 2, "when": "far", "then": [["overload"]]},
            {"move": "kick", "weight": 3, "when": "behind"},
            {"move": "halo", "weight": 2},
            {"move": "overload", "weight": 2},
            {"move": "strikes", "weight": 1},
        ]},
    ],
    "enrage": {"below": 0.15, "speed": 1.25},
}


def paint(r, P_):
    """Four materials in a value order: the arc-light brightest (the dial, the belfry's lamp, the hands' inlays, the
    weight's lamp, the slits), then brass (cornices, bezel, cogs, bell, the hands' points, the rod), then the tower's
    green copper (riveted plates streaked with verdigris), then dark iron (legs, buttresses, hands, spire)."""
    brass = Ramp("#3a280c", "#563c14", "#74521e", "#94702c", "#b48e3c", "#d2ae56", "#ead07c")
    copper = Ramp("#14241f", "#1c3029", "#253e35", "#2f4e43", "#3c6152", "#4b7664", "#5d8c78", "#74a48e")
    iron = Ramp("#0c0d10", "#141519", "#1c1e23", "#25282f", "#30343c", "#3d424b", "#4d535e")
    rust = Ramp("#2a160c", "#3e2010", "#562c16")
    arc = ("#2a6cff", "#56a0ff", "#8cc8ff", "#c4e6ff", "#f2fbff")
    of = lambda *ps: [c for c in P_.cubes if any(c.startswith(p) for p in ps)]
    # the copper walls: plates five rows high, staggered seams, rivets where they cross, verdigris streaking down
    def plates(f, i, j):
        if f.name not in SIDES:
            return 0.3 if (i + j) % 5 == 0 else 0
        row = j // 5
        if j % 5 == 0:
            return -1.1
        if (i + (3 if row % 2 else 0)) % 6 == 0:
            return -0.8
        if j % 5 == 1 and (i + (3 if row % 2 else 0)) % 6 in (1, 5):
            return 1.3
        return 0.6 if (i * 7 + row * 3) % 11 == 0 and j % 5 > 2 else 0
    for c in ("chest", "spine"):
        P_.paint(c, copper, 4.0, plates, grad=1.4, rim=0.5, ao=1.0, noise=0.6, nscale=7)
    # brass: banded with a bright top row, dark seams along the cornices
    bandp = lambda f, i, j: (1.0 if j == 0 else -0.6 if (i + j) % 9 == 0 else 0)
    for c in of("cornice", "bezel", "hipband_", "kneeguard_", "axle_", "bell", "clapper", "rod", "bob_band", "neck", "roof", "vane", "plinth",
                "spade_r", "arrow_l", "point_r", "ring_l", "dial_hub"):
        P_.paint(c, brass, 4.2, bandp, grad=1.0, rim=0.8, ao=0.8, noise=0.3)
    for c in of("cog_", "hipcog_", "kneecog_"):
        P_.paint(c, brass, 4.6 if c.startswith("cog_") else 3.8, lambda f, i, j: (0.9 if (i + j) % 3 == 0 else 0), rim=1.0, noise=0.2)
    # iron: a dull sheen on the top edges, rust weeping from the bottom of the legs and the buttresses
    ironp = lambda f, i, j: (0.8 if j == 0 else -0.6 if (i * 3 + j) % 7 == 0 else 0)
    for c in ["pelvis", "bearing"] + of("thigh_", "shin_", "foot_", "toe_", "spur_", "ankle_", "ram_", "buttress", "pier", "post", "spire",
                                        "tail_", "weight_l", "blade_", "neck_l", "bob", "dial_h", "dial_m", "mark"):
        if c in P_.cubes and not c.startswith(("bob_band", "bob_lamp")):
            P_.paint(c, iron, 3.2 if not c.startswith(("blade_", "tail_", "weight_l", "neck_l")) else 3.8, ironp, grad=1.0, rim=1.0, ao=1.0, noise=0.4)
    for c in of("shin_", "buttress", "foot_"):
        for fname in SIDES:
            fc = P_.face(c, fname)
            for i, j in fc.each():
                if j >= fc.h - 3 and R._h(c, fname, i) % 3 == 0:
                    fc.set(i, j, rust.at(1 + (j - fc.h + 3) % 2))
    for c in of("greave_"):
        P_.paint(c, copper, 4.6, lambda f, i, j: (1.0 if j % 4 == 0 else 0), noise=0.3)
    # the dial: pale glass lit from behind, brightest in the middle, the hour marks and the small hands dark on it
    P_.paint("dial", Ramp("#6a8ca8", "#8eaec6", "#b4cedf", "#d8e8f2"), 2.6)
    radial(P_, "dial", "north", ["#f2fbff", "#e2f2ff", "#c4e6ff", "#a6d4ff", "#8cc8ff"], alpha=235)
    for c in of("mark", "dial_h", "dial_m"):
        P_.paint(c, iron, 1.4)
    # the lamps: the belfry's lamp, the weight's, the slits in the lower shaft; the hands' inlays run with light
    for c, faces in (("lamp", ("bottom", "top") + SIDES), ("bob_lamp", ("north",)), ("slit0", ("north",)), ("slit1", ("north",))):
        P_.paint(c, Ramp("#14306a", "#2050a8"), 1.0)
        for fname in faces:
            radial(P_, c, fname, [arc[4], arc[3], arc[2]])
    for c in of("inlay_"):
        P_.paint(c, iron, 1.0)
        for fname in ("north", "top", "bottom", "west", "east"):
            edge_glow(P_, c, fname, arc[3], rows=99, alpha=230)
    # the bell's lip catches the lamp's light from above
    for fname in SIDES:
        edge_glow(P_, "bell_lip", fname, arc[2], rows=1, alpha=120, side="top")
        edge_glow(P_, "bell", fname, arc[1], rows=1, alpha=90, side="top")
    # the hands' points carry a lit edge, so the blades read against the dark
    for c in ("arrow_l2", "point_r"):
        for fname in ("west", "east"):
            edge_glow(P_, c, fname, arc[2], rows=1, alpha=200, side="right" if fname == "west" else "left")
    return P_.skin, P_.glow


def design():
    r = body()
    r.pack()
    mv = clips(r)
    return {"rig": r, "plan": P, "moves": mv, "fight": FIGHT, "paint": paint, "scale": SCALE, "half": 1.26, "sounds": SOUNDS, "move_sounds": MOVE_SOUNDS}
