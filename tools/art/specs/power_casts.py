"""Full-body cast animations for the Player Animation Library (an optional client mod; docs/modules/power-fx.md 4).

    python3 tools/art/build_all.py power_casts       assets/awaken/player_animations/power_casts.json
    python3 tools/art/specs/power_casts.py build     the same, plus the clipping report
    python3 tools/art/specs/power_casts.py preview DIR [clip ...]
                                                     a frame strip per clip (every tick, three views) and a contact
                                                     sheet of the nine families at wind-up, release and follow-through
    python3 tools/art/specs/power_casts.py check     the clipping report only

The game reads the JSON through dev.awaken.powers.client.fx.PalCasts (only while player_animation_library is loaded);
dev.awaken.powers.fx.CastClips names the clip each cast pose plays. Edit the poses here, never the JSON.

Conventions (vanilla model space, exactly as the library applies them): degrees; arms and legs rotate like a ModelPart's
xRot / yRot / zRot (x < 0 swings a limb forward and up; a right arm's z > 0 lifts it out to the side; a forward right
arm's y > 0 turns it outward, y < 0 across the chest). A pose is written in figure terms and baked into bones:
  lean    whole figure about the waist, + forward          tilt   whole figure, + toward the main-hand side
  rise    px up (after the feet are put on the ground)     shift  px forward;  side  px toward the main hand
  twist   chest yaw, + turns the chest toward the main hand (the shoulders go with it, the head stays on target)
  ra, la  main / off arm (x, y, z), body-relative; an aimed arm (ra_aim / la_aim) is world-relative and follows the
          look pitch, so it points at what the player looks at whatever the lean or twist
  rl, ll  main-side / off-side leg (x, y, z), world-relative: the legs stay where they are when the body leans
  head_z  head roll, + toward the main hand (the head always keeps the player's look; a lean is taken out of it)
  ground  True: the figure is raised or lowered so the lowest foot is on the ground (then rise is added)
Clips are authored right-handed; the game mirrors them for a left main arm. A cast clip's last key is its endTick: the
library then eases every bone back to the vanilla pose over the rest of the clip.
"""
import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ART = os.path.abspath(os.path.join(HERE, ".."))
ROOT = os.path.abspath(os.path.join(HERE, "../../.."))
for p in (HERE, ART):
    if p not in sys.path:
        sys.path.insert(0, p)
import fauna_rig as R  # noqa: E402

OUT_REL = "assets/awaken/player_animations/power_casts.json"
AIM = "q.head_x_rotation"  # the look pitch in degrees (+ down), the same value vanilla puts on the head


# ---------------------------------------------------------------------------------------------------------------------
# Poses

DEFAULTS = dict(lean=0.0, tilt=0.0, rise=0.0, shift=0.0, side=0.0, twist=0.0, ground=True,
                ra=(0.0, 0.0, 3.0), la=(0.0, 0.0, -3.0), ra_aim=False, la_aim=False,
                rl=(0.0, 0.0, 0.0), ll=(0.0, 0.0, 0.0), head_z=0.0)


def pose(base=None, **kw):
    p = dict(DEFAULTS if base is None else base)
    for k, v in kw.items():
        if k not in DEFAULTS:
            raise KeyError(k)
        p[k] = tuple(float(x) for x in v) if isinstance(v, (tuple, list)) else v
    return p


def nudge(base, **kw):
    """base with small offsets added (tremble keys): numbers add, tuples add per axis."""
    p = dict(base)
    for k, v in kw.items():
        if isinstance(v, (tuple, list)):
            p[k] = tuple(a + b for a, b in zip(p[k], v))
        else:
            p[k] = p[k] + v
    return p


NEUTRAL = pose()

# Vanilla player model (wide arms): part pivots and boxes, model space (y down, front -z, the right side at -x).
PARTS = {
    "head": ((0, 0, 0), (-4, -8, -4, 8, 8, 8), (0, 0)),
    "torso": ((0, 0, 0), (-4, 0, -2, 8, 12, 4), (16, 16)),
    "right_arm": ((-5, 2, 0), (-3, -2, -2, 4, 12, 4), (40, 16)),
    "left_arm": ((5, 2, 0), (-1, -2, -2, 4, 12, 4), (32, 48)),
    "right_leg": ((-1.9, 12, 0), (-2, 0, -2, 4, 12, 4), (0, 16)),
    "left_leg": ((1.9, 12, 0), (-2, 0, -2, 4, 12, 4), (16, 48)),
}
WAIST = 12.0  # the library turns the whole figure (its "body" bone) about the waist


class V:
    """A channel value: const + k * look pitch (k = 0 or 1), or disabled (the vanilla value stays)."""
    __slots__ = ("c", "k", "off")

    def __init__(self, c=0.0, k=0, off=False):
        self.c, self.k, self.off = float(c), k, off

    def at(self, pitch=0.0):
        return self.c + self.k * pitch

    def json(self):
        if self.off:
            return "pal.disabled"
        c = round(self.c, 2)
        if self.k == 0:
            return int(c) if c == int(c) else c
        return f"{AIM} {'+' if c >= 0 else '-'} {abs(c):g}"


OFF = V(off=True)


def bake(p):
    """Pose -> {bone: {"rotation": (V, V, V), "position": (V, V, V)}} in library terms (degrees, px, y up)."""
    lean, tilt, twist = p["lean"], p["tilt"], p["twist"]
    brz = -tilt
    tw = math.radians(twist)
    b = {}
    b["torso"] = {"rotation": (OFF, V(twist), OFF)}
    # arms: the shoulders ride the chest's yaw (vanilla's attack swing does the same)
    sx, sz = 5 - 5 * math.cos(tw), 5 * math.sin(tw)
    for name, key, aim, sgn in (("right_arm", "ra", "ra_aim", 1), ("left_arm", "la", "la_aim", -1)):
        x, y, z = p[key]
        if p[aim]:
            rot = (V(x - lean, 1), V(y), V(z - brz))
        else:
            rot = (V(x), V(y + twist), V(z))
        b[name] = {"rotation": rot, "position": (V(sgn * sx), OFF, V(sgn * sz))}
    for name, key in (("right_leg", "rl"), ("left_leg", "ll")):
        x, y, z = p[key]
        b[name] = {"rotation": (V(x - lean), V(y), V(z - brz))}
    b["head"] = {"rotation": (V(-lean, 1), OFF, V(-p["head_z"]))}
    by = p["rise"]
    if p["ground"]:
        by += ground_offset(p, b)
    b["body"] = {"rotation": (V(lean), V(0), V(brz)), "position": (V(-p["side"]), V(by), V(-p["shift"]))}
    return b


# ---------------------------------------------------------------------------------------------------------------------
# Forward kinematics in model space (the preview and the checks use the same transforms the library does)

def _deg(v):
    return tuple(math.radians(a) for a in v)


def part_frames(b, pitch=0.0):
    """{part: (M, T)} world transforms of every part for baked bones b (look pitch in degrees)."""
    def val(ch, i, vanilla):
        v = ch[i]
        return vanilla if v.off else v.at(pitch)

    bp = b["body"]["position"]
    br = b["body"]["rotation"]
    root_t = (val(bp, 0, 0), WAIST - val(bp, 1, 0), val(bp, 2, 0))
    root_m = R._rot(*_deg((val(br, 0, 0), val(br, 1, 0), val(br, 2, 0))))
    out = {}
    for name, (pivot, _, _) in PARTS.items():
        ch = b.get(name, {})
        rot = ch.get("rotation", (OFF, OFF, OFF))
        vanilla_rot = {"head": (pitch, 0, 0), "right_arm": (0, 0, 3), "left_arm": (0, 0, -3)}.get(name, (0, 0, 0))
        r = tuple(val(rot, i, vanilla_rot[i]) for i in range(3))
        pos = ch.get("position", (OFF, OFF, OFF))
        # library position: x and z add to the pivot, y is up (model y is down)
        pv = (pivot[0] + val(pos, 0, 0), pivot[1] - val(pos, 1, 0), pivot[2] + val(pos, 2, 0))
        local = (pv[0], pv[1] - WAIST, pv[2])
        T = tuple(root_t[i] + R._mv(root_m, local)[i] for i in range(3))
        M = R._mm(root_m, R._rot(*_deg(r)))
        out[name] = (M, T)
    return out


def ground_offset(p, b):
    """px to raise the figure (y up) so its lowest foot corner sits on the ground (model y = 24)."""
    probe = dict(b)
    probe["body"] = {"rotation": (V(p["lean"]), V(0), V(-p["tilt"])), "position": (V(0), V(0), V(0))}
    frames = part_frames(probe)
    low = -1e9
    for leg in ("right_leg", "left_leg"):
        M, T = frames[leg]
        _, (x0, y0, z0, w, h, d), _ = PARTS[leg]
        for cx in (x0, x0 + w):
            for cz in (z0, z0 + d):
                q = R._mv(M, (cx, y0 + h, cz))
                low = max(low, q[1] + T[1])
    return low - 24.0


def tree(b, pitch=0.0):
    """A fauna_rig render tree (the vanilla player with the Steve skin) posed by baked bones b."""
    frames = part_frames(b, pitch)
    kids = []
    for name, (pivot, (x, y, z, w, h, d), (u, v)) in PARTS.items():
        M, T = frames[name]
        kids.append({"pivot": T, "rot": (0, 0, 0), "mat": M, "scale": (1, 1, 1), "children": [],
                     "boxes": [{"u": u, "v": v, "x": x, "y": y, "z": z, "w": w, "h": h, "d": d, "mirror": False, "grow": 0.0}]})
    return kids


# ---------------------------------------------------------------------------------------------------------------------
# Clips

EASES = {"linear", "constant", "step", "easeinsine", "easeoutsine", "easeinoutsine", "easeinquad", "easeoutquad",
         "easeinoutquad", "easeincubic", "easeoutcubic", "easeinoutcubic", "easeinquart", "easeoutquart", "easeinoutquart",
         "easeinexpo", "easeoutexpo", "easeinoutexpo", "easeoutback", "easeinback", "easeinoutback", "easeoutcirc"}


class Clip:
    def __init__(self, name, keys, length, loop=False, end=None, note=""):
        """keys: [(tick, pose, easing into this key)]; end: endTick (the last key) for one-shot clips."""
        self.name, self.length, self.loop, self.note = name, float(length), loop, note
        self.keys = sorted(((float(t), p, e) for t, p, e in keys), key=lambda k: k[0])
        self.end = None if loop else float(self.keys[-1][0] if end is None else end)
        for t, _, e in self.keys:
            if e not in EASES:
                raise ValueError(f"{name}: unknown easing {e}")
        if self.keys[0][0] != 0:
            raise ValueError(f"{name}: the first key must be at tick 0")
        if not loop and abs(self.keys[-1][0] - self.end) > 1e-6:
            raise ValueError(f"{name}: the last key must be the endTick")
        if self.keys[-1][0] > self.length:
            raise ValueError(f"{name}: keys past the clip length")

    def baked(self):
        return [(t, bake(p), e) for t, p, e in self.keys]

    def channels(self):
        """{bone: {target: [(tick, (V, V, V), ease)]}}: every channel any key moves; static channels are dropped."""
        baked = self.baked()
        out = {}
        for bone in ("body", "torso", "head", "right_arm", "left_arm", "right_leg", "left_leg"):
            for target in ("rotation", "position"):
                rows = [(t, bb[bone][target], e) for t, bb, e in baked if target in bb.get(bone, {})]
                if not rows:
                    continue
                axes = [i for i in range(3) if not rows[0][1][i].off]
                if not axes:
                    continue
                neutral = bake(NEUTRAL)[bone][target]
                moving = any(abs(r[1][i].c - neutral[i].c) > 1e-3 or r[1][i].k != neutral[i].k for r in rows for i in axes)
                if not moving:
                    continue
                out.setdefault(bone, {})[target] = rows
        return out

    def json(self):
        bones = {}
        for bone, targets in self.channels().items():
            jb = {}
            for target, rows in targets.items():
                frames = {}
                for i, (t, vec, ease) in enumerate(rows):
                    entry = {"vector": [v.json() for v in vec]}
                    if i > 0 and ease != "linear":
                        entry["easing"] = ease
                    frames[f"{t / 20:.4g}"] = entry
                jb[target] = frames
            bones[bone] = jb
        a = {"animation_length": round(self.length / 20, 4), "bones": bones}
        if self.loop:
            a["loop"] = True
        else:
            a["player_animation_library"] = {"endTick": self.end}
        return a

    def sample(self, tick, pitch=0.0):
        """Baked bones at a tick, interpolated as the library does (easing on the key a segment ends at)."""
        baked = self.baked()
        if tick <= baked[0][0]:
            return baked[0][1]
        for (t0, b0, _), (t1, b1, e) in zip(baked, baked[1:]):
            if tick <= t1:
                u = ease(e, (tick - t0) / (t1 - t0))
                return lerp_bones(b0, b1, u)
        last = baked[-1][1]
        if self.end is not None and tick > self.end:
            # the library's end fade: ease-in-out back to vanilla over the clip's tail
            u = min(1.0, (tick - self.end) / max(1e-6, self.length - self.end))
            return lerp_bones(last, bake(NEUTRAL), 0.5 - 0.5 * math.cos(math.pi * u))
        return last


def ease(name, t):
    t = max(0.0, min(1.0, t))
    inv = lambda f: 1 - f(1 - t)  # noqa: E731
    fs = {"sine": lambda x: 1 - math.cos(x * math.pi / 2), "quad": lambda x: x * x, "cubic": lambda x: x ** 3,
          "quart": lambda x: x ** 4, "expo": lambda x: 2 ** (10 * (x - 1)) if x > 0 else 0.0,
          "back": lambda x: x * x * (2.70158 * x - 1.70158), "circ": lambda x: 1 - math.sqrt(max(0.0, 1 - x * x))}
    if name in ("linear", "step"):
        return t
    if name == "constant":
        return 0.0
    for k, f in fs.items():
        if name == "easein" + k:
            return f(t)
        if name == "easeout" + k:
            return inv(f)
        if name == "easeinout" + k:
            return f(t * 2) / 2 if t < 0.5 else 1 - f((1 - t) * 2) / 2
    raise ValueError(name)


def lerp_bones(a, b, u):
    out = {}
    for bone in a:
        out[bone] = {}
        for target in a[bone]:
            va, vb = a[bone][target], b[bone][target]
            out[bone][target] = tuple(OFF if x.off else V(x.c + (y.c - x.c) * u, x.k + (y.k - x.k) * u) for x, y in zip(va, vb))
    return out


CLIPS = {}


def clip(name, keys, length, loop=False, end=None, note=""):
    c = Clip(name, keys, length, loop, end, note)
    if name in CLIPS:
        raise ValueError(f"duplicate clip {name}")
    CLIPS[name] = c
    return c


def cast(family, keys, length, release, note):
    """A family's cast clip plus its release half (played after a held wind-up: from the wind-up key on)."""
    c = clip(f"cast_{family}", keys, length, note=note)
    rel = [(t - release, p, e) for t, p, e in keys if t >= release]
    if not rel or rel[0][0] != 0:
        raise ValueError(f"cast_{family}: release tick {release} must be a key")
    clip(f"cast_{family}_release", rel, length - release, note=note + " (release half)")
    return c


def hold(name, base, jitters, period, note):
    """A held wind-up: a loop of the base pose with a tremble (jitter poses spaced evenly), plus its fade-out clip."""
    n = len(jitters) + 1
    keys = [(0, base, "linear")]
    for i, j in enumerate(jitters):
        keys.append((period * (i + 1) / n, j, "easeinoutsine"))
    keys.append((period, base, "easeinoutsine"))
    clip(name, keys, period, loop=True, note=note)
    clip(name + "_end", [(0, base, "linear")], 5, end=0, note="lets go of " + name)


# ---------------------------------------------------------------------------------------------------------------------
# The nine families. Each cast is short: the wind-up is two or three ticks because the effect leaves at the start of
# the clip (the server sends the effect and the pose together); the release lands by tick 3-5; the rest is
# follow-through and the library's fade back.

# Kinetic: a grounded punch. Wide stance, the chest coils away from the target, then the hips and chest snap round,
# the fist drives straight at the target and the off fist chambers at the hip.
K_LOAD = pose(lean=-4, twist=30, ra=(34, 0, 14), la=(-74, -24, 0), rl=(18, 0, 7), ll=(-14, 0, -7))
K_STRIKE = pose(lean=14, shift=1.5, twist=-26, ra=(-94, -6, 0), ra_aim=True, la=(32, 0, -12), rl=(30, 0, 8), ll=(-24, 0, -7))
K_FOLLOW = pose(K_STRIKE, lean=11, twist=-20, ra=(-90, -6, 0))
K_SETTLE = pose(K_STRIKE, lean=6, shift=0.5, twist=-10, ra=(-62, -4, 0), ra_aim=False, la=(14, 0, -8), rl=(16, 0, 6), ll=(-12, 0, -6))
cast("kinetic", [(0, NEUTRAL, "linear"), (2, K_LOAD, "easeoutquad"), (4, K_STRIKE, "easeoutexpo"),
                 (6, K_FOLLOW, "easeinoutsine"), (7, K_SETTLE, "easeinoutsine")], 10, 2,
     "grounded punch: coil, hips and chest snap round, straight fist, off fist chambered")
K_WIND = pose(K_LOAD, lean=-7, twist=38, ra=(44, 0, 16), la=(-70, -30, 0), rl=(22, 0, 9), ll=(-16, 0, -9))
hold("hold_kinetic", K_WIND, [nudge(K_WIND, twist=2, ra=(3, 0, 0)), nudge(K_WIND, twist=-1.5, ra=(-2, 0, 1))], 6,
     "a punch loaded and trembling")

# Velocity (also every dash): a sprinter's burst. Load low, then explode into a hard forward lean with both arms
# swept back like wings and a long stride; the body stays low and fast.
V_LOAD = pose(lean=18, ra=(-18, 0, 6), la=(-18, 0, -6), rl=(10, 0, 3), ll=(-8, 0, -3))
V_BURST = pose(lean=40, shift=2, ra=(58, 0, 20), la=(58, 0, -20), rl=(34, 0, 3), ll=(-30, 0, -3))
V_CARRY = pose(V_BURST, lean=34, ra=(50, 0, 18), la=(50, 0, -18), rl=(26, 0, 3), ll=(-22, 0, -3))
V_SETTLE = pose(lean=16, shift=0.5, ra=(24, 0, 10), la=(24, 0, -10), rl=(10, 0, 2), ll=(-8, 0, -2))
cast("velocity", [(0, NEUTRAL, "linear"), (1, V_LOAD, "easeoutquad"), (3, V_BURST, "easeoutexpo"),
                  (6, V_CARRY, "easeinoutsine"), (7, V_SETTLE, "easeinoutsine")], 10, 1,
     "sprinter's burst: hard forward lean, arms swept back, long stride")
hold("hold_velocity", V_LOAD, [nudge(V_LOAD, lean=2, rise=0.3), nudge(V_LOAD, lean=-1)], 6, "crouched to go")

# Flight & Gravity: the launch. Both hands gather low at the off hip, then sweep diagonally up across the body to
# above the main shoulder as the figure lifts and arches; the off arm trails down: one long diagonal line, rising.
F_GATHER = pose(lean=10, twist=-22, ra=(-30, -38, -6), la=(-12, 0, -28), rl=(4, 0, 8), ll=(-4, 0, -8))
F_LAUNCH = pose(lean=-14, rise=2.5, twist=16, ra=(-160, 0, -24), la=(28, 0, -40), rl=(24, 0, 4), ll=(2, 0, -6))
F_FLOAT = pose(F_LAUNCH, rise=3.2, twist=12, ra=(-168, 0, -20), la=(30, 0, -46), rl=(28, 0, 4))
F_SETTLE = pose(F_LAUNCH, lean=-4, rise=1.5, twist=6, ra=(-120, 0, -16), la=(10, 0, -28))
cast("flight", [(0, NEUTRAL, "linear"), (3, F_GATHER, "easeoutquad"), (6, F_LAUNCH, "easeoutcubic"),
                (10, F_FLOAT, "easeoutsine"), (11, F_SETTLE, "easeinoutsine")], 15, 3,
     "the launch: gather low at the off hip, sweep diagonally up across the body, lift off")
F_GRIP = pose(lean=-4, rise=0.6, ra=(-112, 6, -6), ra_aim=True, la=(-6, 0, -34), rl=(6, 0, 4), ll=(-2, 0, -4))
hold("hold_flight", F_GRIP, [nudge(F_GRIP, ra=(-3, 2, 0), rise=0.4), nudge(F_GRIP, ra=(2, -2, 0), rise=-0.2)], 8,
     "holding something aloft: the hand up and out, the body light")

# Energy: the hands gather the charge cupped at the main hip with the chest coiled away, then both palms thrust out
# along the aim from a wide braced stance.
E_GATHER = pose(lean=-4, twist=30, ra=(26, 0, 10), la=(-34, 52, 0), rl=(14, 0, 8), ll=(-6, 0, -8))
E_THRUST = pose(lean=14, shift=1.5, ra=(-92, -12, 0), la=(-92, 12, 0), ra_aim=True, la_aim=True, rl=(24, 0, 9), ll=(-16, 0, -9))
E_HOLD = pose(E_THRUST, lean=11, ra=(-90, -12, 0), la=(-90, 12, 0))
E_SETTLE = pose(E_THRUST, lean=5, shift=0.5, ra=(-62, -14, 0), la=(-62, 14, 0), ra_aim=False, la_aim=False, rl=(12, 0, 6), ll=(-8, 0, -6))
cast("energy", [(0, NEUTRAL, "linear"), (3, E_GATHER, "easeoutquad"), (5, E_THRUST, "easeoutexpo"),
                (8, E_HOLD, "easeinoutsine"), (9, E_SETTLE, "easeinoutsine")], 12, 3,
     "gather cupped at the hip, both palms thrust out along the aim")
E_CHARGE = pose(E_GATHER, lean=-6, twist=34, ra=(30, 0, 10), la=(-30, 54, 0), rl=(16, 0, 10), ll=(-8, 0, -10))
hold("hold_energy", E_CHARGE, [nudge(E_CHARGE, ra=(3, 2, 0), la=(-3, -2, 0), rise=0.3), nudge(E_CHARGE, ra=(-2, -1, 0), la=(2, 1, 0))], 4,
     "power gathered between the palms at the hip, trembling")

# Shadow: drop into a low crouch with the hand drawn across to the off shoulder, then sweep it out wide at waist
# height as the chest uncoils; stay low.
S_DRAW = pose(lean=24, twist=-26, ra=(-96, -58, 0), la=(36, 0, -22), rl=(16, 0, 30), ll=(-10, 0, -30))
S_SWEEP = pose(lean=28, twist=30, ra=(-72, 72, 22), la=(42, 0, -30), rl=(18, 0, 32), ll=(-12, 0, -32))
S_LOW = pose(S_SWEEP, lean=26, twist=34, ra=(-60, 86, 26))
S_SETTLE = pose(lean=14, twist=14, ra=(-30, 40, 16), la=(20, 0, -16), rl=(8, 0, 16), ll=(-6, 0, -16))
cast("shadow", [(0, NEUTRAL, "linear"), (3, S_DRAW, "easeoutquad"), (6, S_SWEEP, "easeoutexpo"),
                (9, S_LOW, "easeoutsine"), (10, S_SETTLE, "easeinoutsine")], 13, 3,
     "low crouch, hand drawn across, a wide sweep at waist height")
hold("hold_shadow", S_DRAW, [nudge(S_DRAW, lean=1.5, ra=(2, 2, 0)), nudge(S_DRAW, lean=-1, ra=(-2, -1, 0))], 8, "coiled low")

# Mind: two fingers to the temple (head tipped to the hand, the body still), then a straight point at the target.
M_TEMPLE = pose(lean=-3, twist=6, ra=(-152, -24, -14), la=(6, 0, -6), head_z=8)
M_FOCUS = pose(M_TEMPLE, ra=(-156, -26, -14), head_z=10)
M_POINT = pose(lean=6, twist=-10, ra=(-92, -4, 0), ra_aim=True, la=(14, 0, -8))
M_HOLD = pose(M_POINT, lean=5, ra=(-90, -4, 0))
cast("mind", [(0, NEUTRAL, "linear"), (3, M_TEMPLE, "easeoutquad"), (7, M_FOCUS, "easeinoutsine"),
              (9, M_POINT, "easeoutexpo"), (12, M_HOLD, "easeoutsine")], 15, 3,
     "two fingers to the temple, then a forward point")
hold("hold_mind", M_FOCUS, [nudge(M_FOCUS, head_z=2, ra=(-2, 0, 0)), nudge(M_FOCUS, head_z=-1, ra=(1, 0, 0))], 10, "concentrating")

# Architect: the hands draw a frame in the air (together at the top, apart, down the sides), then set it with both
# palms pressed forward and down. Ruler-straight moves (linear), a firm set.
A_TOP = pose(ra=(-102, -14, 0), la=(-102, 14, 0), rl=(0, 0, 7), ll=(0, 0, -7), head_z=5)
A_SPREAD = pose(A_TOP, ra=(-100, 42, 0), la=(-100, -42, 0), head_z=7)
A_SIDES = pose(A_TOP, ra=(-60, 42, 0), la=(-60, -42, 0), head_z=3)
A_SET = pose(lean=12, shift=1, ra=(-58, -6, 0), la=(-58, 6, 0), ra_aim=True, la_aim=True, rl=(12, 0, 8), ll=(-8, 0, -8))
cast("architect", [(0, NEUTRAL, "linear"), (2, A_TOP, "easeoutquad"), (5, A_SPREAD, "linear"), (8, A_SIDES, "linear"),
                   (10, A_SET, "easeoutback")], 14, 2,
     "hands draw a frame in the air, then set it")
hold("hold_architect", A_SPREAD, [nudge(A_SPREAD, ra=(0, 3, 0), la=(0, -3, 0)), nudge(A_SPREAD, ra=(0, -2, 0), la=(0, 2, 0))], 10,
     "the frame held open")

# Tech: the off hand taps the device on the main wrist, the arm locks straight onto the target (side-on stance), a
# recoil kicks it up and rocks the body back, then it settles: mechanical, no wasted motion.
T_TAP = pose(twist=8, ra=(-94, -48, 0), la=(-84, 54, 0), head_z=-6)
T_LOCK = pose(twist=-36, ra=(-92, 0, 0), ra_aim=True, la=(-80, 40, 0), rl=(12, 0, 5), ll=(-10, 0, -6))
T_KICK = pose(T_LOCK, lean=-8, shift=-1, ra=(-114, 0, 0))
T_SETTLE = pose(T_LOCK, lean=-1, twist=-32, ra=(-94, 0, 0))
cast("tech", [(0, NEUTRAL, "linear"), (2, T_TAP, "easeoutquad"), (4, T_LOCK, "easeoutexpo"), (5, T_KICK, "easeoutexpo"),
              (8, T_SETTLE, "easeoutcubic"), (9, T_SETTLE, "linear")], 12, 2,
     "tap the wrist device, lock on, recoil")
hold("hold_tech", T_LOCK, [nudge(T_LOCK, ra=(-1, 0.6, 0)), nudge(T_LOCK, ra=(0.8, -0.6, 0))], 4, "locked on, servo jitter")

# Spirit: palms meet at the chest with a small bow, then the arms open wide and high as the body rises and the chest
# lifts; a slow float down.
P_PRAY = pose(lean=8, ra=(-56, -40, 0), la=(-56, 40, 0), rl=(0, 0, 4), ll=(0, 0, -4))
P_OPEN = pose(lean=-10, rise=2.0, ra=(-24, 0, 118), la=(-24, 0, -118), rl=(6, 0, 3), ll=(6, 0, -3))
P_FLOAT = pose(P_OPEN, rise=2.6, ra=(-26, 0, 128), la=(-26, 0, -128))
P_SETTLE = pose(P_OPEN, lean=-4, rise=1.2, ra=(-16, 0, 84), la=(-16, 0, -84))
cast("spirit", [(0, NEUTRAL, "linear"), (3, P_PRAY, "easeoutquad"), (7, P_OPEN, "easeoutcubic"), (11, P_FLOAT, "easeoutsine"),
                (12, P_SETTLE, "easeinoutsine")], 16, 3,
     "palms together at the chest, then an open-armed rise")
hold("hold_spirit", P_PRAY, [nudge(P_PRAY, lean=1.5, rise=0.3), nudge(P_PRAY, lean=-1)], 12, "palms together, breathing")

# ---------------------------------------------------------------------------------------------------------------------
# Moves the body makes because of what the power does to it (any family)

# Slam: both arms high, hands together, arched back (held); then a deep crouch with both fists driven into the ground.
SL_UP = pose(lean=-12, ra=(-172, -12, 0), la=(-172, 12, 0), rl=(6, 0, 8), ll=(-4, 0, -8))
SL_DOWN = pose(lean=36, shift=1.5, ra=(-48, -14, 0), la=(-48, 14, 0), rl=(20, 0, 26), ll=(-14, 0, -26))
SL_REBOUND = pose(SL_DOWN, lean=30, ra=(-52, -14, 0), la=(-52, 14, 0))
SL_SETTLE = pose(lean=14, ra=(-20, -8, 6), la=(-20, 8, -6), rl=(8, 0, 12), ll=(-6, 0, -12))
hold("slam_wind", SL_UP, [nudge(SL_UP, lean=-1.5, ra=(-2, 0, 0), la=(-2, 0, 0)), nudge(SL_UP, lean=1)], 6, "both arms up, about to come down")
clip("slam", [(0, SL_UP, "linear"), (2, SL_DOWN, "easeinexpo"), (5, SL_REBOUND, "easeoutsine"), (6, SL_SETTLE, "easeinoutsine")], 10,
     note="both fists into the ground from overhead")

# Flurry: palms strike in turn every two ticks from a wide stance, the chest turning into each one.
FL_BASE = dict(lean=12, shift=1, rl=(22, 0, 10), ll=(-16, 0, -10))
FL_R = pose(**FL_BASE, twist=-16, ra=(-92, -8, 0), ra_aim=True, la=(-40, 34, 0))
FL_L = pose(**FL_BASE, twist=16, ra=(-40, -34, 0), la=(-92, 8, 0), la_aim=True)
fl_keys = [(0, NEUTRAL, "linear")]
for i in range(18):
    fl_keys.append((2 + 2 * i, FL_R if i % 2 == 0 else FL_L, "easeoutquad"))
fl_keys.append((38, pose(**FL_BASE, ra=(-50, -20, 0), la=(-50, 20, 0)), "easeinoutsine"))
clip("flurry", fl_keys, 42, note="palms in turn, too fast to see")

# Hover: weightless; legs hang a little back, arms float out low with the palms down, a slow bob.
H_BASE = pose(ground=False, lean=4, ra=(-10, 0, 34), la=(-10, 0, -34), rl=(14, 0, 5), ll=(8, 0, -5))
hold("hover", H_BASE, [nudge(H_BASE, rise=0.7, ra=(0, 0, 4), la=(0, 0, -4), rl=(-3, 0, 0)),
                       nudge(H_BASE, rise=-0.5, ra=(0, 0, -2), la=(0, 0, 2), ll=(-3, 0, 0))], 40, "weightless, a slow bob")


# ---------------------------------------------------------------------------------------------------------------------
# Output

def document():
    return {"format_version": "1.8.0",
            "__comment": "GENERATED by tools/art/specs/power_casts.py: edit the poses there, not this file.",
            "animations": {name: c.json() for name, c in CLIPS.items()}}


def write(root=None):
    path = os.path.join(root or os.path.join(ROOT, "src/main/resources"), OUT_REL)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as fh:
        json.dump(document(), fh, indent=1, sort_keys=False)
        fh.write("\n")
    return path


def build(out):
    """build_all.py entry: writes the clips JSON (no textures)."""
    write(out.root)


# ---------------------------------------------------------------------------------------------------------------------
# Clipping check: points inside each arm (below the shoulder) must stay out of the head, torso and legs

def _inv_apply(M, T, q):
    d = tuple(q[i] - T[i] for i in range(3))
    # M is orthonormal: inverse = transpose
    return tuple(sum(M[j][i] * d[j] for j in range(3)) for i in range(3))


def penetration(b, pitch=0.0):
    frames = part_frames(b, pitch)
    worst, where = 0.0, ""
    for arm in ("right_arm", "left_arm"):
        M, T = frames[arm]
        _, (x0, y0, z0, w, h, d), _ = PARTS[arm]
        pts = [(x0 + w * fx, y0 + h * fy, z0 + d * fz) for fx in (0.1, 0.5, 0.9) for fy in (0.4, 0.65, 0.95) for fz in (0.1, 0.5, 0.9)]
        for other in ("head", "torso", "right_leg", "left_leg"):
            Mo, To = frames[other]
            _, (ox, oy, oz, ow, oh, od), _ = PARTS[other]
            for pt in pts:
                q = R._mv(M, pt)
                q = (q[0] + T[0], q[1] + T[1], q[2] + T[2])
                lx, ly, lz = _inv_apply(Mo, To, q)
                depth = min(lx - ox, ox + ow - lx, ly - oy, oy + oh - ly, lz - oz, oz + od - lz)
                if depth > worst:
                    worst, where = depth, f"{arm} in {other}"
    return worst, where


def check(limit=1.25, quiet=False):
    bad = []
    for name, c in CLIPS.items():
        worst, at, where = 0.0, 0.0, ""
        t = 0.0
        while t <= c.length + 1e-6:
            for pitch in (0.0, 30.0):
                d, w = penetration(c.sample(t, pitch), pitch)
                if d > worst:
                    worst, at, where = d, t, w + (f" (look {pitch:g})" if pitch else "")
            t += 0.5
        if worst > limit:
            bad.append(f"{name}: {where} by {worst:.2f} px at tick {at:g}")
        elif not quiet and worst > 0.3:
            print(f"  {name}: {where} {worst:.2f} px at tick {at:g} (allowed)")
    return bad


# ---------------------------------------------------------------------------------------------------------------------
# Preview

VIEWS = (("front", -30.0, 12.0), ("side", -90.0, 6.0), ("behind", 160.0, 16.0))
BG = (34, 37, 46, 255)
FLOOR = (64, 70, 84, 255)


def _quads(kids):
    out = []
    for k in kids:
        M, T = k["mat"], k["pivot"]
        for bx in k["boxes"]:
            g = bx["grow"]
            x0, y0, z0 = bx["x"] - g, bx["y"] - g, bx["z"] - g
            x1, y1, z1 = bx["x"] + bx["w"] + g, bx["y"] + bx["h"] + g, bx["z"] + bx["d"] + g

            def tf(pt):
                q = R._mv(M, pt)
                return (q[0] + T[0], q[1] + T[1], q[2] + T[2])
            t0, t1, t2, t3 = (x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0)
            l0, l1, l2, l3 = (x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)
            u, v, w, h, d = bx["u"], bx["v"], bx["w"], bx["h"], bx["d"]
            U0, U1, U2, U22, U3, U4 = u, u + d, u + d + w, u + d + w + w, u + d + w + d, u + d + w + d + w
            V0, V1, V2 = v, v + d, v + d + h
            centre = tf(((x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2))
            for verts, rect in (((l1, l0, t0, t1), (U1, V0, U2, V1)), ((t2, t3, l3, l2), (U2, V1, U22, V0)),
                                ((t0, l0, l3, t3), (U0, V1, U1, V2)), ((t1, t0, t3, t2), (U1, V1, U2, V2)),
                                ((l1, t1, t2, l2), (U2, V1, U3, V2)), ((l0, l1, l2, l3), (U3, V1, U4, V2))):
                vs = [tf(q) for q in verts]
                fc = tuple(sum(q[i] for q in vs) / 4 for i in range(3))
                out.append((vs, rect, tuple(fc[i] - centre[i] for i in range(3)), False))
    return out


def _render_posed(kids, yaw, pitch, px, bounds):
    # reuse fauna_rig.render with our own quads (posed parts have arbitrary matrices)
    saved = R.quads
    R.quads = lambda tree: _quads(kids)
    try:
        return R.render({}, R.steve(), yaw=yaw, pitch=pitch, px=px, bounds=bounds)
    finally:
        R.quads = saved


def _cam(yaw, pitch):
    return R._mm(R._rot(math.radians(pitch), 0, 0), R._rot(0, math.radians(yaw), 0))


def _project(pts, yaw, pitch):
    cam = _cam(yaw, pitch)
    return [R._mv(cam, p) for p in pts]


def frame_image(c, tick, yaw, pitch, px, bounds, look=8.0):
    from PIL import Image, ImageDraw
    kids = tree(c.sample(tick, look), look)
    fig = _render_posed(kids, yaw, pitch, px, bounds)
    img = Image.new("RGBA", fig.size, BG)
    dr = ImageDraw.Draw(img)
    minx, maxx, miny, maxy = bounds
    offx, offy = 8 - minx * px, 8 - miny * px
    floor = _project([(-14, 24, -14), (14, 24, -14), (14, 24, 14), (-14, 24, 14)], yaw, pitch)
    dr.polygon([(q[0] * px + offx, q[1] * px + offy) for q in floor], fill=FLOOR)
    # the look direction (the target is ahead, -z)
    aim = _project([(0, 23.9, 0), (0, 23.9, -14)], yaw, pitch)
    dr.line([(q[0] * px + offx, q[1] * px + offy) for q in aim], fill=(220, 120, 90, 255), width=2)
    img.alpha_composite(fig)
    return img


def strip_bounds(clips, yaw, pitch):
    trees = []
    for c in clips:
        t = 0.0
        while t <= c.length:
            trees.append(c.sample(t, 8.0))
            t += 1.0
    xs, ys = [], []
    cam = _cam(yaw, pitch)
    for b in trees:
        fr = part_frames(b, 8.0)
        for name, (M, T) in fr.items():
            _, (x0, y0, z0, w, h, d), _ = PARTS[name]
            for cx in (x0, x0 + w):
                for cy in (y0, y0 + h):
                    for cz in (z0, z0 + d):
                        q = R._mv(M, (cx, cy, cz))
                        q = R._mv(cam, (q[0] + T[0], q[1] + T[1], q[2] + T[2]))
                        xs.append(q[0])
                        ys.append(q[1])
    for p in _project([(-14, 24, -14), (14, 24, -14), (14, 24, 14), (-14, 24, 14)], yaw, pitch):
        xs.append(p[0])
        ys.append(p[1])
    return min(xs), max(xs), min(ys), max(ys)


def strip(c, out_dir, px=5.0):
    from PIL import Image, ImageDraw
    ticks = list(range(0, int(c.length) + 1))
    rows = []
    for vname, yaw, pitch in VIEWS:
        bounds = strip_bounds([c], yaw, pitch)
        rows.append([frame_image(c, t, yaw, pitch, px, bounds) for t in ticks])
    w = max(im.width for r in rows for im in r)
    h = max(im.height for r in rows for im in r)
    sheet = Image.new("RGBA", (len(ticks) * w, len(rows) * h + 34), (20, 22, 28, 255))
    dr = ImageDraw.Draw(sheet)
    dr.text((6, 4), f"{c.name}  {int(c.length)} ticks" + (" loop" if c.loop else f"  endTick {c.end:g}") + f"   {c.note}",
            fill=(230, 232, 240, 255))
    keyset = {int(round(t)) for t, _, _ in c.keys}
    for j, row in enumerate(rows):
        for i, im in enumerate(row):
            sheet.paste(im, (i * w, 34 + j * h))
            if j == 0:
                dr.text((i * w + 4, 20), f"t{ticks[i]}" + (" *" if ticks[i] in keyset else ""), fill=(200, 205, 215, 255))
    path = os.path.join(out_dir, f"{c.name}.png")
    sheet.save(path)
    return path


def families_sheet(out_dir, px=5.0):
    """The nine families, one per row: wind-up, release and follow-through from the front, the side and behind
    (the third-person camera), then the release as a flat silhouette at about ten blocks, seen three-quarters on."""
    from PIL import Image, ImageDraw
    fams = ["kinetic", "velocity", "flight", "energy", "shadow", "mind", "architect", "tech", "spirit"]
    beats = {}
    for f in fams:
        c = CLIPS[f"cast_{f}"]
        ks = [t for t, _, _ in c.keys]
        beats[f] = (c, [ks[1], ks[2], ks[-2]])
    views = (("front", -30.0, 12.0), ("side", -90.0, 6.0), ("behind", 160.0, 16.0))
    bounds = {v: strip_bounds([CLIPS[f"cast_{f}"] for f in fams], v[1], v[2]) for v in views}
    rows = []
    for f in fams:
        c, ts = beats[f]
        cells = []
        for v in views:
            cells += [frame_image(c, t, v[1], v[2], px, bounds[v]) for t in ts]
        for yaw in (-20.0, -75.0):
            cells.append(silhouette(frame_image(c, ts[1], yaw, 4.0, px, strip_bounds([c], yaw, 4.0))))
        rows.append(cells)
    w = max(im.width for r in rows for im in r)
    h = max(im.height for r in rows for im in r)
    gap = 8
    cols = len(rows[0])
    sheet = Image.new("RGBA", (90 + cols * w + 3 * gap, len(rows) * h + 20), (20, 22, 28, 255))
    dr = ImageDraw.Draw(sheet)
    heads = ["front: wind-up", "release", "follow", "side: wind-up", "release", "follow", "behind: wind-up", "release", "follow", "10 blocks", "10 blocks"]
    for i, t in enumerate(heads):
        dr.text((90 + i * w + (i // 3) * gap + 4, 4), t, fill=(200, 205, 215, 255))
    for j, (f, cells) in enumerate(zip(fams, rows)):
        dr.text((6, 20 + j * h + h // 2), f, fill=(230, 232, 240, 255))
        for i, im in enumerate(cells):
            sheet.paste(im, (90 + i * w + (i // 3) * gap, 20 + j * h))
    path = os.path.join(out_dir, "families.png")
    sheet.save(path)
    return path


def silhouette(img, scale=0.22):
    """The figure as a dark shape on a pale sky, shrunk to roughly its size on screen ten blocks away."""
    from PIL import Image
    w, h = img.size
    px = img.load()
    out = Image.new("RGBA", (w, h), (196, 214, 232, 255))
    op = out.load()
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if (r, g, b, a) not in (BG, FLOOR) and not (r > 200 and g < 140):
                op[x, y] = (30, 32, 40, 255)
    small = out.resize((max(1, int(w * scale)), max(1, int(h * scale))), Image.BOX)
    return small.resize((w, h), Image.NEAREST)


def main(argv):
    cmd = argv[0] if argv else "build"
    if cmd == "build":
        path = write()
        print(f"{len(CLIPS)} clips -> {os.path.relpath(path, ROOT)}")
        bad = check(quiet=True)
        for b in bad:
            print("CLIPPING", b)
        return 1 if bad else 0
    if cmd == "check":
        bad = check()
        for b in bad:
            print("CLIPPING", b)
        print("ok" if not bad else f"{len(bad)} clips clip")
        return 1 if bad else 0
    if cmd == "preview":
        out_dir = argv[1]
        os.makedirs(out_dir, exist_ok=True)
        names = argv[2:] or [n for n in CLIPS if not n.endswith("_end") and not n.endswith("_release")]
        for n in names:
            if n == "families":
                print(families_sheet(out_dir))
                continue
            print(strip(CLIPS[n], out_dir))
        if not argv[2:]:
            print(families_sheet(out_dir))
        return 0
    raise SystemExit(__doc__)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
