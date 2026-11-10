"""The gate monsters' authoring kit (SPEC §60): what every theme file in this folder builds its bodies with.

A monster is a hero rig (fauna_rig.Rig: bones, whole-pixel boxes, packed UVs, keyframe clips), drawn in game by
mobs.client.anim.WildRenderer exactly as the Drip Lurker is. This file keeps the craft in one place:

  * poses as plain dicts and clips built from them, with the anticipation, overshoot and follow-through every move
    needs written once (act, keyed);
  * legs built to the motion layer's rules (SPEC §23.1: a leg bent at rest bends freely; strides the legs can reach),
    and the motion.json entry that goes with them (legs, gait);
  * paint helpers and the palette rule (a theme's monsters are painted against their room and carry one light).

Conventions (vanilla ModelPart): model pixels, y down, the ground at y = 24, the creature faces -z, +x is its left;
rotations in degrees, applied Z then Y then X. In a pose or a clip, "pos" is in posVec units: +y is UP.

Bone names matter: the life layer (mobs.client.life.LifeBones) binds by name. head, neck, jaw, eye; body, chest;
springs on ear/tuft/crest/plume/sail, antenna/feeler/whisk/palp/rod, tail/streamer/stinger/fan, and the hanging parts
mane/beard/skirt/robe/cape/tassel/chain/clapper/lamp/drip/frill/moss/leaf/tent/reed/shag; wing; arm/fore; and legs
leg/thigh/shin/foot/hoof/hock/talon/root. Never name a bone lid (an eyelid) or bell (a body) unless it is one.
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.abspath(os.path.join(HERE, "..")))
sys.path.insert(0, os.path.abspath(os.path.join(HERE, "../..")))
import fauna_rig as R  # noqa: E402
from fauna_rig import Rig, Ramp, hexc, lerpc, mul, p_fur, p_scales, p_plates, p_bands, p_strokes, p_blotch, p_stone, combine, SIDES, _h  # noqa: E402,F401
from fauna_anim import cycle, s, c  # noqa: E402,F401

DEG = math.pi / 180.0
ALL = ("top", "bottom") + SIDES


# ---------------------------------------------------------------------------------------------------------------------
# Poses: {bone: (rx, ry, rz)} or {bone: {"rot": (..), "pos": (x, up, z), "scale": (sx, sy, sz)}}; all are offsets from rest.

def _n(v):
    if isinstance(v, dict):
        return {"rot": tuple(v.get("rot", (0, 0, 0))), "pos": tuple(v.get("pos", (0, 0, 0))), "scale": tuple(v.get("scale", (1, 1, 1)))}
    return {"rot": tuple(v), "pos": (0, 0, 0), "scale": (1, 1, 1)}


def norm(p):
    return {b: _n(v) for b, v in (p or {}).items()}


def scaled(p, k):
    """The pose k times as far from rest."""
    out = {}
    for b, v in norm(p).items():
        out[b] = {"rot": tuple(a * k for a in v["rot"]), "pos": tuple(a * k for a in v["pos"]), "scale": tuple(1 + (a - 1) * k for a in v["scale"])}
    return out


def plus(*poses):
    """Poses added (scales multiplied about 1)."""
    out = {}
    for p in poses:
        for b, v in norm(p).items():
            e = out.setdefault(b, {"rot": (0, 0, 0), "pos": (0, 0, 0), "scale": (1, 1, 1)})
            out[b] = {"rot": tuple(e["rot"][i] + v["rot"][i] for i in range(3)), "pos": tuple(e["pos"][i] + v["pos"][i] for i in range(3)),
                      "scale": tuple(e["scale"][i] + v["scale"][i] - 1 for i in range(3))}
    return out


def blend(a, b, t):
    return plus(scaled(a, 1 - t), scaled(b, t))


def mirrored(p):
    """The pose with left and right swapped (bones named *_l / *_r; y and z turns and x offsets flip)."""
    out = {}
    for b, v in norm(p).items():
        nb = b[:-2] + ("_r" if b.endswith("_l") else "_l") if b.endswith(("_l", "_r")) else b
        out[nb] = {"rot": (v["rot"][0], -v["rot"][1], -v["rot"][2]), "pos": (-v["pos"][0], v["pos"][1], v["pos"][2]), "scale": v["scale"]}
    return out


def tree(rig, p=None, lift=0.0):
    """The renderer's posed box tree for a pose (previews and checks)."""
    q = {}
    for b, v in norm(p).items():
        q[b] = {"rot": tuple(a * DEG for a in v["rot"]), "pos": (v["pos"][0], -v["pos"][1], v["pos"][2]), "scale": v["scale"]}
    t = rig.tree(q)
    t["pivot"] = (0, -lift, 0)
    return t


# ---------------------------------------------------------------------------------------------------------------------
# Clips from poses

def keyed(rig, name, length, keys, loop=False, lag=None, interp="cat"):
    """A clip through key poses: keys = [(seconds, pose)]. Every bone any pose moves gets a key at every time (rest where
    a pose is silent). lag = {bone: seconds}: that bone reaches each pose that much later (follow-through down a chain,
    a hanging part trailing the body); its last key is clamped to the clip's end."""
    clip = rig.clip(name, length, loop)
    keys = sorted(((float(t), norm(p)) for t, p in keys), key=lambda k: k[0])
    lag = lag or {}
    bones = []
    for _, p in keys:
        for b in p:
            if b not in bones:
                bones.append(b)
    for b in bones:
        if b not in rig.by_name:
            raise ValueError(f"{rig.id}/{name}: no bone {b}")
        for target, ident in (("rot", (0, 0, 0)), ("pos", (0, 0, 0)), ("scale", (1, 1, 1))):
            vals = [p.get(b, {}).get(target, ident) for _, p in keys]
            if all(v == ident for v in vals):
                continue
            d = lag.get(b, 0.0)
            out = []
            for (t, _), v in zip(keys, vals):
                tt = t if t <= 0 or d <= 0 else min(length, t + d)
                if out and tt <= out[-1][0] + 1e-4:
                    out[-1] = (out[-1][0],) + tuple(v)
                    continue
                out.append((tt,) + tuple(v))
            if out[-1][0] < length - 1e-4 and not loop:
                out.append((length,) + out[-1][1:])
            getattr(clip, target)(b, out, interp)
    rig._lying = None       # a fall's laying-down belongs to the clip that held it (see fall, lying)
    return clip


def act(rig, mv, wind, hit, *, w=0.6, length=0.55, t_hit=0.09, over=0.14, back=0.1, counter=0.16, after=None, rest=None, open_pose=None,
        stuck=None, lag=None, press=0.06, plant=()):
    """One move as the body acts it, in the three clips the renderer plays.

    <mv>_windup (stretched in game to the move's real wind-up): a small counter-move the other way, then the body
    gathers into `wind`, there by four fifths of the time and pressed a little further to the end, so it never holds
    dead still before it lets go.
    <mv>_strike: from that pose to `hit` in t_hit seconds, past it (over), back through it the other way (`after`, by
    default a tenth of the hit reversed), and settled to `rest` (or into `open_pose`, where the move leaves it open).
    <mv>_open (only with open_pose): the open pose, breathing, for the server to hold until the opening ends.
    With `stuck` instead, the strike settles to rest as usual and <mv>_open is the pose a miss leaves it in (the server
    cues it only when the lunge hit nobody).
    """
    limbs = [b for t in plant for b in ("leg" + t, "shin" + t, "foot" + t)]

    def pl(p, free=True):
        """Feet the clips own in a cue stay on their spots: a limb the pose itself moves keeps its pose (free), the rest
        are solved; in a passing key (the counter-move, the swing back) every planted limb is solved."""
        if not plant:
            return p
        q = norm(p)
        if not free:
            q = {b: v for b, v in q.items() if b not in limbs}
        return planted(rig, q, plant)
    wind_end = plus(wind, scaled(wind, press))
    # out of the counter-move and into the wind-up the body eases: slow away, quickest through the middle, slow to arrive
    # (two keys on a smoothstep between them; a straight run from one pose to the other reads as a machine)
    back_ = scaled(wind, -counter)

    def gathering(u):
        e = u * u * (3 - 2 * u)
        return plus(scaled(back_, 1 - e), scaled(wind, e))
    keyed(rig, mv + "_windup", w, [(0, {}), (0.2 * w, pl(back_, False)), (0.38 * w, pl(gathering(0.3), False)), (0.62 * w, pl(gathering(0.7))),
                                   (0.8 * w, pl(wind)), (w, pl(wind_end))], lag=lag)
    end = open_pose if open_pose is not None else (rest or {})
    aft = after if after is not None else plus(scaled(hit, -back), scaled(end, 0.5))
    t1 = t_hit + 0.07
    t2 = min(length - 0.08, t1 + max(0.12, (length - t1) * 0.45))
    skeys = [(0, pl(wind_end)), (t_hit, pl(hit)), (t1, pl(plus(hit, scaled(hit, over)))), (t2, pl(aft, False))]
    if length - t2 > 0.14:
        # it does not stop dead where it ends: a last small rock back toward the blow, then still
        skeys.append((t2 + (length - t2) * 0.55, pl(plus(end, scaled(hit, 0.035)))))
    skeys.append((length, pl(end)))
    keyed(rig, mv + "_strike", length, skeys, lag=lag)
    held = open_pose if open_pose is not None else stuck
    if held is not None:
        sway = scaled(held, 0.05)
        keyed(rig, mv + "_open", 2.6, [(0, pl(held)), (0.65, pl(plus(held, sway))), (1.3, pl(plus(held, scaled(sway, -1)))),
                                       (1.95, pl(plus(held, scaled(sway, 0.6)))), (2.6, pl(held))], lag=lag)
        if not hasattr(rig, "_spent"):
            rig._spent = (pl(held), lag)      # the body's spent pose, for its moves that were given none (fill_opens)
    names = [ab for ab, m in getattr(rig, "_abilities", {}).items() if m == mv]
    if not names and mv != "_plain":
        raise ValueError(f"{rig.id}: move {mv} answers to no ability; name it in moves()")
    for ab in names:
        rig.moves[ab] = mv


def fill_opens(rig):
    """Every move the body acts gets an <mv>_open clip. A move authored without one takes the body's own spent pose (the
    first one any of its moves was given), eased into from rest, since that move's strike ends at rest: so whichever
    move the server says left it open, the body shows it. A body with no spent pose at all is left alone."""
    spent = getattr(rig, "_spent", None)
    if spent is None:
        return
    held, lag = spent
    sway = scaled(held, 0.05)
    for mv in list(dict.fromkeys(rig.moves.values())) + [""]:   # ("": its plain blow, whose open clip is just "open")
        if (mv + "_open" if mv else "open") in rig.clips:
            continue
        keyed(rig, mv + "_open" if mv else "open", 2.6, [(0, {}), (0.14, scaled(held, 0.45)), (0.32, held), (0.9, plus(held, sway)), (1.5, plus(held, scaled(sway, -1))),
                                       (2.1, plus(held, scaled(sway, 0.6))), (2.6, held)], lag=lag)


def moves(rig, **ability_of):
    """Names the bestiary abilities this body acts, before act(): moves(r, toll="Bell Toll", rush=("Coffin Rush", "Bell
    Slam")). A move may answer to several abilities."""
    rig._abilities = {}
    for mv, abilities in ability_of.items():
        for ab in ((abilities,) if isinstance(abilities, str) else abilities):
            rig._abilities[ab] = mv


def plain(rig, wind, hit, *, heavy=None, second=None, **kw):
    """Its plain blow (the clips are windup / strike; a big body's every third is heavy_windup / heavy_strike).
    second = (wind, hit): its other swing (windup2 / strike2), which a body that strings its blows together throws as
    every second one (SPEC §60.8): the other hand, the backhand, the bite from the other side."""
    kw.setdefault("w", 0.4)
    kw.setdefault("length", 0.42)
    kw.setdefault("t_hit", 0.07)
    _plain(rig, "", "", wind, hit, **kw)
    if heavy is not None:
        hw, hh = heavy
        kw2 = dict(kw, w=0.7, length=0.6, t_hit=0.09)
        _plain(rig, "heavy_", "", hw, hh, **kw2)
    if second is not None:
        sw, sh = second
        kw3 = {k: v for k, v in kw.items() if k not in ("open_pose", "stuck")}
        _plain(rig, "", "2", sw, sh, **kw3)


def _plain(rig, prefix, suffix, wind, hit, **kw):
    saved = dict(rig.moves)
    act(rig, "_plain", wind, hit, **kw)
    rig.moves.clear()
    rig.moves.update(saved)
    for stage in ("windup", "strike", "open"):
        clip = rig.clips.pop("_plain_" + stage, None)
        if clip is None:
            continue    # (a plain blow has an open clip only if it was given an open pose)
        if suffix and stage == "open":
            continue    # (its other swing leaves it as its first does)
        clip.name = prefix + stage + suffix
        rig.clips[clip.name] = clip


def other_side(wind, hit, turn=("head",), roll=16.0, more=1.12):
    """A body's other swing made from its first, for a blow thrown with the head (a bite, a butt, a peck): the same
    gather and snap with the head rolled over the other way and driven a little further, so a second bite comes in from
    the other side of the jaw instead of repeating the first. Returns (wind, hit) for plain(second=...)."""
    w2 = plus(wind, {b: (0, 0, -roll) for b in turn})
    h2 = plus(scaled(hit, more), {b: (0, 0, roll * 0.9) for b in turn})
    return w2, h2


def flinch(rig, p, length=0.4, plant=()):
    """Struck: thrown into the pose in a twentieth of a second, back past rest, settled."""
    f = (lambda q: planted(rig, q, plant)) if plant else (lambda q: q)
    return keyed(rig, "hurt", length, [(0, {}), (0.05, f(p)), (0.19, f(scaled(p, -0.28))), (0.3, f(scaled(p, 0.09))), (length, {})])


def bump(p, at, width=0.06):
    """A smooth pulse 0..1 round phase `at` of a loop (for a gesture inside an idle's cycle)."""
    d = abs(((p - at + 0.5) % 1.0) - 0.5)
    return math.exp(-(d / width) ** 2)


def loop(rig, name, length, fn, n=12, plant=()):
    """A looping clip from fn(p) -> pose, p in 0..1, sampled at n even keys (the last equal to the first). plant: limbs
    whose feet stay on their spots under the body (a stance the clip owns, as an angry one that rears)."""
    f = (lambda q: planted(rig, q, plant)) if plant else (lambda q: q)
    if not plant:
        n = enough_keys(fn, length, n)
    keys = [(length * k / n, f(fn((k / n) % 1.0))) for k in range(n)] + [(length, f(fn(0.0)))]
    return keyed(rig, name, length, keys, loop=True)


def enough_keys(fn, length, n, most=64):
    """How many even keys a loop needs so that nothing in it is lost between them. A long idle sampled at twelve keys
    has one every half second or more, and a glance or a twitch written into it (a bump a quarter of a second wide) falls
    between two keys and never plays, or plays as a slow ghost of itself. The loop's function is read finely, and the
    keys are doubled (evenly, so the game's curve through them stays well behaved) until a straight line between
    neighbouring keys stays within 0.8 degrees, a tenth of a pixel and 1.5% of scale of it, or there are `most`."""
    dense = 96 if length > 2.0 else 48
    truth = [norm(fn(k / dense)) for k in range(dense)]

    def worst(m):
        ks = [norm(fn((k / m) % 1.0)) for k in range(m + 1)]
        bad = 0.0
        for i, t in enumerate(truth):
            x = i / dense * m
            a = min(m - 1, int(x))
            u = x - a
            for bone, tv in t.items():
                ka, kb = ks[a].get(bone), ks[a + 1].get(bone)
                for ch, tol, ident in (("rot", 0.8, 0.0), ("pos", 0.1, 0.0), ("scale", 0.015, 1.0)):
                    for c_ in range(3):
                        va = ka[ch][c_] if ka else ident
                        vb = kb[ch][c_] if kb else ident
                        bad = max(bad, abs(va + (vb - va) * u - tv[ch][c_]) / tol)
        return bad
    while n < most and worst(n) > 1.0:
        n *= 2
    return min(n, most)


def spin_loop(rig, name, length, fn, bone, axis, turn, n=12):
    """A looping clip from fn(p) -> pose in which `bone` also turns steadily through `turn` degrees about one axis
    (0 x, 1 y, 2 z). A wheel whose shape repeats every `turn` degrees comes round to where it began, so the loop's seam
    never shows; turn it by its symmetry (a gear of eight teeth: 45, a two-armed governor: 180)."""
    keys = []
    for k in range(n + 1):
        rot = [0.0, 0.0, 0.0]
        rot[axis] = turn * k / n
        keys.append((length * k / n, plus(fn((k / n) % 1.0), {bone: tuple(rot)})))
    return keyed(rig, name, length, keys, loop=True)


def turning(rig, name, length, fn, spins, n=12):
    """spin_loop for several wheels at once: spins = {bone: (axis, degrees a loop)}."""
    keys = []
    for k in range(n + 1):
        add = {}
        for bone, (axis, turn) in spins.items():
            rot = [0.0, 0.0, 0.0]
            rot[axis] = turn * k / n
            add[bone] = tuple(rot)
        keys.append((length * k / n, plus(fn((k / n) % 1.0), add)))
    return keyed(rig, name, length, keys, loop=True)


# ---------------------------------------------------------------------------------------------------------------------
# Legs the motion layer can plant (SPEC §23.1). A leg bent more than ten degrees at rest bends freely on its IK.

def reach_ground(hip_y, upper, lower, rise):
    """The knee angle (degrees, down from the thigh's line) that puts the foot of a leg on the ground: hip_y model px,
    thigh `upper` px rising `rise` degrees above level (negative: sloping down), shin `lower` px."""
    need = (24.0 - hip_y + upper * math.sin(rise * DEG)) / lower
    need = max(-1.0, min(1.0, need))
    return math.degrees(math.asin(need)) + rise


def splay_leg(parent, name, side, hip, upper, lower, rise, yaw, thick=1, share=None, foot=None):
    """A crawler's leg, out to the side: the thigh rises `rise` degrees from the hip (yawed `yaw` forward), the shin
    comes down to the ground. hip = (x, y, z) in the parent's frame, y the hip's height in model px when the parent sits
    at the origin of heights (pass world y for a parent whose pivot is the body's)."""
    x, y, z = hip
    world_y = parent._world_y + y if hasattr(parent, "_world_y") else y
    knee = reach_ground(world_y, upper, lower, rise)
    mir = side < 0
    t = thick
    leg = parent.bone(f"leg{name}", (side * x, y, z), (0, side * yaw, side * -rise))
    leg.cube(f"thigh{name}", 0 if side > 0 else -upper, -t / 2, -t / 2, upper, t, t, mirror=mir, share=share and share[0])
    shin = leg.bone(f"shin{name}", (side * upper, 0, 0), (0, 0, side * knee))
    shin.cube(f"shinb{name}", 0 if side > 0 else -lower, -t / 2, -t / 2, lower, t, t, mirror=mir, share=share and share[1])
    if foot:
        fw, fh, fd = foot
        f = shin.bone(f"foot{name}", (side * lower, 0, 0), (0, 0, side * -(knee - rise)))
        f.cube(f"footb{name}", -fw / 2, -fh, -fd / 2, fw, fh, fd, mirror=mir, share=share and len(share) > 2 and share[2])
    return leg


def bent_leg(parent, name, hip, upper, lower, fwd, thick=(3, 2), share=None, foot=None, side=1, world_y=None, tilt=0.0, splay=0.0):
    """A walker's limb under the body: the thigh slopes `fwd` degrees forward of straight down (negative: back, as a
    hock or an ape's upper arm does), the shin comes back under it to the ground. hip is in the parent's frame; world_y
    is the hip's height in model px; tilt is how far the parent itself leans forward (degrees about x), which the limb
    cancels so it still hangs true. The thigh's block runs a pixel past the knee over the thinner shin, so the joint
    never opens. Bones: leg<name>, shin<name>, foot<name> (name like "0_l")."""
    x, y, z = hip
    wy = world_y if world_y is not None else (parent._world_y + y if hasattr(parent, "_world_y") else y)
    drop = 24.0 - wy
    ty = upper * math.cos(fwd * DEG)
    rest = drop - ty
    if rest <= 0 or rest > lower:
        raise ValueError(f"leg{name}: {upper}+{lower} px can't stand a hip {drop:.1f} px up at {fwd} degrees (the shin must cover {rest:.1f})")
    back = math.degrees(math.acos(rest / lower))
    knee = (back + abs(fwd)) * (1 if fwd >= 0 else -1)
    if abs(knee) <= 10.5:
        raise ValueError(f"leg{name}: bent only {knee:.1f} degrees at rest; the motion layer would call it straight")
    mir = side < 0
    tu, tl = thick
    leg = parent.bone(f"leg{name}", (side * x, y, z), (-fwd - tilt, 0, side * -splay))
    leg.cube(f"thigh{name}", -tu / 2, 0, -tu / 2, tu, upper + (1 if tu > tl else 0), tu, mirror=mir, share=share and share[0])
    shin = leg.bone(f"shin{name}", (0, upper, 0), (knee, 0, 0))
    fh = foot[1] if foot else 0
    shin.cube(f"shinb{name}", -tl / 2, 0, -tl / 2, tl, lower - fh, tl, mirror=mir, share=share and share[1])
    if foot:
        # the foot's block hangs below its ankle (the motion layer takes a limb's far end as where it stands, LegRig.tip),
        # and is at least as tall as it reaches fore or aft, so that far end is its sole under the ankle, not its toe
        fw, fh, fd, fz = foot
        if fh < max(abs(fz), abs(fz + fd)):
            raise ValueError(f"foot{name}: {fh} px tall but it reaches {max(abs(fz), abs(fz + fd))} px from its ankle; the game would stand it on its toe")
        f = shin.bone(f"foot{name}", (0, lower - fh, 0), (fwd - knee, 0, side * splay))
        f.cube(f"footb{name}", -fw / 2, 0, fz, fw, fh, fd, mirror=mir, share=share and len(share) > 2 and share[2])
    return leg


SIDES2 = ((1, "l"), (-1, "r"))


def legs2(parent, tag, hip, upper, lower, fwd, **kw):
    """A left and right bent_leg (leg<tag>_l, leg<tag>_r) sharing one side's textures."""
    for side, nm in SIDES2:
        share = None if side > 0 else (f"thigh{tag}_l", f"shinb{tag}_l") + ((f"footb{tag}_l",) if kw.get("foot") else ())
        bent_leg(parent, f"{tag}_{nm}", hip, upper, lower, fwd, side=side, share=share, **kw)


def splay_pairs(body, hip_y, xs, zs, upper, lower, rise=32, yaws=None, thick=1, mounts=False):
    """Pairs of crawler's legs off a body bone (marked with at()), front to back, sharing one pair of textures.
    mounts: each leg hangs from a small bone turned its way (mount<k>_<side>), so the leg itself only rises: its knee
    then stands exactly as high as `rise` says however far round it is turned (a leg that carries its own yaw loses a
    little of its rise to it, more the further it is turned)."""
    n = len(zs)
    yaws = yaws or [36 - 72 * k / max(1, n - 1) for k in range(n)]
    for side, nm in SIDES2:
        for k in range(n):
            first = side > 0 and k == 0
            share = None if first else ("thigh0_l", "shinb0_l")
            if mounts:
                m = at(body.bone(f"mount{k}_{nm}", (side * xs[k], hip_y, zs[k]), (0, side * yaws[k], 0)), body._world_y + hip_y)
                splay_leg(m, f"{k}_{nm}", side, (0, 0, 0), upper, lower, rise, 0, thick=thick, share=share)
            else:
                splay_leg(body, f"{k}_{nm}", side, (xs[k], hip_y, zs[k]), upper, lower, rise, yaws[k], thick=thick, share=share)


def tags(n):
    """The limb tags of n pairs built by splay_pairs or legs2 with numbered tags."""
    return tuple(f"{k}_{nm}" for k in range(n) for nm in "lr")


def alternating(n):
    """fallback_walk's list for n pairs stepping in alternating sets (a tripod, a tetrapod)."""
    return [(f"leg{k}_{nm}", (0.5 * ((k + (0 if sd > 0 else 1)) % 2)), sd) for k in range(n) for sd, nm in SIDES2]


def crawl_gait(rm, **over):
    """A crawler's gait from its room: a ripple walking, alternating sets once it runs, short quick steps."""
    g = dict(duty=(0.74, 0.54), lift=(2.2, 3.4), crouch=0.6, swing=0.22, bob=0.3, roll=0.03, rear=1.0, speed=0.55)
    g.update(over)
    return gait("ripple-alternate", rm, **g)


def at(bone, world_y):
    """Marks a bone with the model-space height of its pivot, so the leg builders know how far the ground is."""
    bone._world_y = world_y
    return bone


def fk(rig, pose=None):
    """{bone: (M, T)}: every bone's frame in model space (M its axes, T its pivot), at rest or in a pose."""
    out = {}
    pose = norm(pose)
    for b in rig.bones:
        M, T = out[b.parent.name] if b.parent is not None else (((1, 0, 0), (0, 1, 0), (0, 0, 1)), (0.0, 0.0, 0.0))
        v = pose.get(b.name)
        piv, rot, sc = b.pivot, b.rot, (1, 1, 1)
        if v is not None:
            piv = (piv[0] + v["pos"][0], piv[1] - v["pos"][1], piv[2] + v["pos"][2])
            rot = (rot[0] + v["rot"][0], rot[1] + v["rot"][1], rot[2] + v["rot"][2])
            sc = v["scale"]
        T2 = tuple(T[i] + R._mv(M, piv)[i] for i in range(3))
        M2 = R._mm(M, R._rot(rot[0] * DEG, rot[1] * DEG, rot[2] * DEG))
        if sc != (1, 1, 1):
            M2 = R._mm(M2, ((sc[0], 0, 0), (0, sc[1], 0), (0, 0, sc[2])))
        out[b.name] = (M2, T2)
    return out


def frame(rig, bone, pose=None):
    """(M, T) of one bone, through its own chain of parents only (fk() for a whole body; this for the solver)."""
    chain = []
    b = rig.by_name[bone]
    while b is not None:
        chain.append(b)
        b = b.parent
    M, T = ((1, 0, 0), (0, 1, 0), (0, 0, 1)), (0.0, 0.0, 0.0)
    for b in reversed(chain):
        v = pose.get(b.name) if pose else None
        piv, rot, sc = b.pivot, b.rot, (1, 1, 1)
        if v is not None:
            piv = (piv[0] + v["pos"][0], piv[1] - v["pos"][1], piv[2] + v["pos"][2])
            rot = (rot[0] + v["rot"][0], rot[1] + v["rot"][1], rot[2] + v["rot"][2])
            sc = v["scale"]
        T = tuple(T[i] + R._mv(M, piv)[i] for i in range(3))
        M = R._mm(M, R._rot(rot[0] * DEG, rot[1] * DEG, rot[2] * DEG))
        if sc != (1, 1, 1):
            M = R._mm(M, ((sc[0], 0, 0), (0, sc[1], 0), (0, 0, sc[2])))
    return M, T


def point(rig, bone, local=(0, 0, 0), frames=None, pose=None):
    """A point of a bone's own frame, in model space (at rest, or in a pose: a normalised one, see norm())."""
    M, T = frames[bone] if frames is not None else frame(rig, bone, norm(pose) if pose else None)
    q = R._mv(M, local)
    return (q[0] + T[0], q[1] + T[1], q[2] + T[2])


def lowest(rig, pose=None, skip=()):
    """(y, cube name) of the lowest box corner of a posed rig (y down: the largest y), parts scaled away left out."""
    fr = fk(rig, pose)
    low, name = -1e9, ""
    for b in rig.bones:
        M, T = fr[b.name]
        if min(abs(M[0][0]) + abs(M[1][0]) + abs(M[2][0]), abs(M[0][1]) + abs(M[1][1]) + abs(M[2][1])) < 0.06:
            continue
        for cb in b.cubes:
            if cb.name in skip:
                continue
            for x in (cb.x, cb.x + cb.w):
                for y in (cb.y, cb.y + cb.h):
                    for z in (cb.z, cb.z + cb.d):
                        q = R._mv(M, (x, y, z))
                        if q[1] + T[1] > low:
                            low, name = q[1] + T[1], cb.name
    return low, name


def on_floor(rig, pose, root="body", skip=(), sink=0.0):
    """The pose with its root raised or lowered so the body rests on the ground: nothing under the floor, nothing
    hovering (a fall's last poses, a body lying flat). sink: px it may settle into the floor."""
    pose = _relaid(rig, pose)
    low, _ = lowest(rig, pose, skip)
    p = norm(pose)
    e = p.setdefault(root, {"rot": (0, 0, 0), "pos": (0, 0, 0), "scale": (1, 1, 1)})
    e["pos"] = (e["pos"][0], e["pos"][1] + (low - 24.0 - sink), e["pos"][2])
    return p


def _parent(bone):
    return bone.parent if not isinstance(bone.parent, str) else bone.rig.by_name[bone.parent]


def _trunk(rig):
    """A body's biggest box and the bone it is on: what should be lying on the floor when the body has fallen."""
    best = None
    for b in rig.bones:
        for cb in b.cubes:
            if best is None or cb.w * cb.h * cb.d > best[0]:
                best = (cb.w * cb.h * cb.d, cb, b)
    return best[1], best[2]


def lying(rig, pose, root="body", skip=(), tries=80, step=12.0, rest=2.5):
    """A fallen body laid down properly. on_floor() lifts a body until nothing is under the floor, so an arm or a leg
    pointing down holds the whole body up in the air like a prop. Here, while something that is not the trunk (its
    biggest box, and the bones that carry it) holds the trunk more than `rest` px off the floor, the limb that reaches
    lowest is turned at its shoulder or hip (or at the joint that touches), whichever way brings it up toward the
    trunk's own level fastest; then the next lowest, until the trunk rests. Returns the pose with those limbs turned
    (its root where it was: floor it with on_floor)."""
    p = norm(pose)
    cube, tb = _trunk(rig)
    chain = set()
    b = tb
    while b is not None:
        chain.add(b.name)
        b = _parent(b)
    owner = {cb.name: bn for bn in rig.bones for cb in bn.cubes}
    kids = {}
    for bn in rig.bones:
        if _parent(bn) is not None:
            kids.setdefault(_parent(bn).name, []).append(bn)

    def subtree(bone):
        out, todo = [], [bone]
        while todo:
            x = todo.pop()
            out.append(x)
            todo += kids.get(x.name, [])
        return out

    def low_of(fr, bones):
        low = -1e9
        for bn in bones:
            M, T = fr[bn.name]
            if min(abs(M[0][0]) + abs(M[1][0]) + abs(M[2][0]), abs(M[0][1]) + abs(M[1][1]) + abs(M[2][1])) < 0.06:
                continue    # scaled away
            for cb in bn.cubes:
                if cb.name in skip:
                    continue
                for x in (cb.x, cb.x + cb.w):
                    for y in (cb.y, cb.y + cb.h):
                        for z in (cb.z, cb.z + cb.d):
                            low = max(low, R._mv(M, (x, y, z))[1] + T[1])
        return low

    def trunk_low(fr):
        M, T = fr[tb.name]
        return max(R._mv(M, (x, y, z))[1] + T[1] for x in (cube.x, cube.x + cube.w) for y in (cube.y, cube.y + cube.h) for z in (cube.z, cube.z + cube.d))

    def turned(q, bone, axis, d):
        e = dict(q.get(bone, {"rot": (0, 0, 0), "pos": (0, 0, 0), "scale": (1, 1, 1)}))
        r_ = list(e["rot"])
        r_[axis] += d
        e["rot"] = tuple(r_)
        q2 = dict(q)
        q2[bone] = e
        return q2
    for _ in range(tries):
        fr = fk(rig, p)
        low, nm = lowest(rig, p, skip)
        if low - trunk_low(fr) <= rest or nm not in owner:
            break
        low_bone = owner[nm]
        if low_bone.name in chain:
            break       # the trunk's own bones hold it: that is how it lies
        base = low_bone
        while _parent(base) is not None and _parent(base).name not in chain:
            base = _parent(base)
        limb = subtree(base)
        depth = low_of(fr, limb) - trunk_low(fr)
        best = None
        for target in dict.fromkeys((base.name, low_bone.name)):
            for axis in (0, 2):
                for d in (step, -step):
                    q = turned(p, target, axis, d)
                    f2 = fk(rig, q)
                    d2 = low_of(f2, limb) - trunk_low(f2)
                    if best is None or d2 < best[0]:
                        best = (d2, q)
        if best is None or best[0] > depth - 0.05:
            break
        p = best[1]
    return p


def _relaid(rig, pose):
    """After a fall laid a body down (lying), the poses that follow it in the same death (its bounce, its last rest) are
    written from the pose as it was authored: they take the same turns, so the limbs stay where the fall left them."""
    lay = getattr(rig, "_lying", None)
    if lay is None:
        return pose
    was, now = lay
    p = norm(pose)
    for bone, e in now.items():
        w = was.get(bone, {"rot": (0, 0, 0)})["rot"]
        if e["rot"] != w and bone in p and all(abs(a - b) < 1e-6 for a, b in zip(p[bone]["rot"], w)):
            p[bone] = dict(p[bone], rot=e["rot"])
        elif e["rot"] != w and bone not in p and all(abs(a) < 1e-6 for a in w):
            p[bone] = {"rot": e["rot"], "pos": (0, 0, 0), "scale": (1, 1, 1)}
    return p


def fall(rig, t0, a, t1, b, steps=3, root="body", skip=(), sink=0.0, ease=1.6):
    """Keys for a fall from pose a (at t0) to pose b (at t1), the body resting on the floor all the way down and
    gathering speed as it goes. It ends lying down properly (lying): no limb left holding it up; the keys written after
    it in the same death follow suit."""
    rig._lying = None
    laid = lying(rig, b, root, skip)
    was = norm(b)
    b = laid
    out = []
    for k in range(1, steps + 1):
        u = k / steps
        out.append((t0 + (t1 - t0) * u ** (1.0 / ease), on_floor(rig, blend(a, b, u), root, skip, sink)))
    rig._lying = (was, laid)
    return out


def solve(rig, pose, unknowns, tip, target, mask=(1, 1, 1), iters=14):
    """Turns the bones in `unknowns` [(bone, axis 0..2)] until the point `tip` (bone, local point) is at `target` (model
    space), as nearly as it can be; mask picks which of x, y, z count. Returns the pose with those turns added. A small
    damped Gauss-Newton on finite differences: enough for a limb's two or three joints."""
    p = norm(pose)

    def err(q):
        M, T = frame(rig, tip[0], q)
        t = R._mv(M, tip[1])
        return [(t[i] + T[i] - target[i]) * mask[i] for i in range(3)]

    def turned(q, k, d):
        bone, axis = unknowns[k]
        e = dict(q.get(bone, {"rot": (0, 0, 0), "pos": (0, 0, 0), "scale": (1, 1, 1)}))
        r_ = list(e["rot"])
        r_[axis] += d
        e["rot"] = tuple(r_)
        q2 = dict(q)
        q2[bone] = e
        return q2
    n = len(unknowns)
    for _ in range(iters):
        f = err(p)
        if sum(v * v for v in f) < 1e-4:
            break
        J = [[(err(turned(p, k, 1.0))[i] - f[i]) for k in range(n)] for i in range(3)]
        A = [[sum(J[i][a] * J[i][b] for i in range(3)) + (0.02 if a == b else 0.0) for b in range(n)] for a in range(n)]
        g = [-sum(J[i][a] * f[i] for i in range(3)) for a in range(n)]
        for a in range(n):  # Gaussian elimination, n <= 3
            piv = A[a][a] if abs(A[a][a]) > 1e-9 else 1e-9
            for b in range(a + 1, n):
                m = A[b][a] / piv
                for c2 in range(a, n):
                    A[b][c2] -= m * A[a][c2]
                g[b] -= m * g[a]
        dq = [0.0] * n
        for a in range(n - 1, -1, -1):
            piv = A[a][a] if abs(A[a][a]) > 1e-9 else 1e-9
            dq[a] = (g[a] - sum(A[a][b] * dq[b] for b in range(a + 1, n))) / piv
        for k in range(n):
            p = turned(p, k, max(-25.0, min(25.0, dq[k])))
    return p


def planted(rig, pose, tags, lift=None):
    """The pose with the limbs leg<tag> turned (hip fore-aft and sideways, knee) so each foot stays where it stands at
    rest while the body above it moves, as the game's own planted feet do. A limb the pose already turns is left alone.
    lift = {tag: px}: that foot is held that far above its spot instead."""
    p = norm(pose)
    rest = fk(rig)
    for tag in tags:
        if "leg" + tag in p or "shin" + tag in p:
            continue
        tipb, local = tip_of(rig, tag)
        want = point(rig, tipb, local, frames=rest)
        if lift and tag in lift:
            want = (want[0], want[1] - lift[tag], want[2])
        # a walker's leg swings fore and aft at the hip and bends its knee about x; a crawler's leg, out to the side,
        # sweeps round its hip and bends its knee about z (the way it was built), so those are the joints to turn
        shin = rig.by_name["shin" + tag]
        splay = abs(shin.rot[2]) > abs(shin.rot[0])
        joints = [("leg" + tag, 1), ("leg" + tag, 2), ("shin" + tag, 2)] if splay else [("leg" + tag, 0), ("leg" + tag, 2), ("shin" + tag, 0)]
        p = solve(rig, p, joints, (tipb, local), want)
        if "foot" + tag in rig.by_name:  # the foot stays level: it takes back what hip and knee turned
            p = level(rig, p, "foot" + tag)
    return p


def level(rig, pose, bone):
    """The pose with `bone` turned about its own x axis until its y axis points straight down again (a foot or a hand
    flat on the floor whatever the limb above it does)."""
    p = norm(pose)
    M = frame(rig, bone, p)[0]
    a = math.degrees(math.atan2(-M[2][1], M[2][2])) if abs(M[2][2]) + abs(M[2][1]) > 1e-6 else 0.0
    # of the two turns that level it, the one that leaves its y axis pointing down, not up
    c2, s2 = math.cos(a * DEG), math.sin(a * DEG)
    if c2 * M[1][1] + s2 * M[1][2] < 0:
        a += 180.0
    e = p.setdefault(bone, {"rot": (0, 0, 0), "pos": (0, 0, 0), "scale": (1, 1, 1)})
    e["rot"] = (e["rot"][0] + a, e["rot"][1], e["rot"][2])
    return p


def limb_room(rig, hip, knee, tip, crouch=0.0, slack=0.97):
    """How far (px) a planted foot can trail or lead before this limb is at full stretch. hip and knee are bone names;
    tip is (bone, point in its frame): where the limb touches the ground at rest."""
    fr = fk(rig)
    h, k, t = point(rig, hip, frames=fr), point(rig, knee, frames=fr), point(rig, tip[0], tip[1], frames=fr)
    full = (math.dist(h, k) + math.dist(k, t)) * slack
    dx, dy, dz = t[0] - h[0], 24.0 - crouch - h[1], t[2] - h[2]
    q = full * full - dx * dx - dy * dy
    return max(0.0, math.sqrt(q) - abs(dz)) if q > 0 else 0.0


def room(rig, limbs, crouch=0.0):
    """The least limb_room of a body's limbs: limbs = [(hip, knee, (tip bone, tip point))]."""
    return min(limb_room(rig, h, k, t, crouch) for h, k, t in limbs)


def engine_tip(bone):
    """Where the motion layer takes a limb's last bone to touch the ground (mobs.client.life.LegRig.tip, mirrored): the
    middle of its boxes' far end along the axis they reach furthest; a flat pad stands on its sole's far edge."""
    lo = [min(getattr(cb, a) for cb in bone.cubes) for a in ("x", "y", "z")]
    hi = [max(getattr(cb, a) + getattr(cb, d) for cb in bone.cubes) for a, d in (("x", "w"), ("y", "h"), ("z", "d"))]
    cc = [(lo[i] + hi[i]) / 2 for i in range(3)]
    axis, best = 0, -1.0
    for a in range(3):
        far = max(abs(lo[a]), abs(hi[a]))
        if far > best:
            best, axis = far, a
    cc[axis] = hi[axis] if abs(hi[axis]) >= abs(lo[axis]) else lo[axis]
    h = hi[1] - lo[1]
    if axis != 1 and h < hi[0] - lo[0] and h < hi[2] - lo[2]:
        cc[1] = hi[1]
    return tuple(cc)


def tip_of(rig, tag):
    """(bone, point): where the limb leg<tag> stands, as the game will take it."""
    foot = rig.by_name.get("foot" + tag)
    last = foot if foot is not None else rig.by_name["shin" + tag]
    return (last.name, engine_tip(last))


def legs_of(rig):
    """The limbs bent_leg and splay_leg built on a rig, as room() wants them."""
    return [(b.name, "shin" + b.name[3:], tip_of(rig, b.name[3:])) for b in rig.bones if b.name.startswith("leg") and "shin" + b.name[3:] in rig.by_name]


def stride_room(drop, side_off, upper, lower, slack=0.97):
    """How far (px) a planted foot can trail or lead before the leg is at full stretch, for a hip `drop` px over the
    ground whose foot rests `side_off` px out to the side."""
    full = (upper + lower) * slack
    room = full * full - drop * drop - side_off * side_off
    return math.sqrt(room) if room > 0 else 0.0


def gait(pattern, room_px, *, duty=(0.62, 0.42), lift=(2.0, 3.4), speed=1.0, lead=0.0, **over):
    """A motion.json gait whose strides its legs can reach: room_px is stride_room() for its shortest-reaching leg.
    A planted foot sweeps duty x stride x 16 x (0.5 + lead) px each way, held under 0.82 of the room (walking) and of
    1.08 x the room running (the hips may drop a little: the vault)."""
    reach = round(room_px * 0.9 / 16.0, 3)
    half = 0.5 + abs(lead)
    walk = round(min(room_px * 0.82, reach * 16) / (duty[0] * 16.0 * half) * speed, 2)
    run = round(min(room_px * 1.02, reach * 16) / (duty[1] * 16.0 * half) * speed, 2)
    g = {"pattern": pattern, "duty": list(duty), "stride": [walk, max(walk, run)], "lift": list(lift), "reach": reach, "settle": 0.08,
         "regrip": [4.0, 10.0], "swing": 0.3, "bob": 0.4, "roll": 0.04}
    if lead:
        g["lead"] = lead
    g.update(over)
    return g


def body(**over):
    b = {"headLevel": 0.45, "twist": 0.25, "slope": 0.7, "land": 1.0, "shift": 0.4}
    b.update(over)
    return b


def flight(**over):
    f = {"always": True, "fast": 4, "bank": 0.35, "climb": 0.45, "tilt": 0.3, "level": 0.35}
    f.update(over)
    return f


def fallback_walk(rig, legs, swing=(20, 30), lift=(10, 16), lengths=(0.9, 0.55), axis="x"):
    """Walk and run leg cycles for when nothing plants the feet (a body drawn without its life). legs: [(bone, phase,
    side)]; a walker's leg swings fore and aft (axis x), a crawler's sweeps round its hip (axis y) and lifts (z)."""
    for name, length, sw, lf in (("walk", lengths[0], swing[0], lift[0]), ("run", lengths[1], swing[1], lift[1])):
        clip = rig.clips.get(name) or rig.clip(name, length, loop=True)
        for bone, ph, side in legs:
            if axis == "x":
                cycle(clip, bone, "rot", lambda p, ph=ph, a=sw: (a * s(p, ph), 0, 0), 8)
            else:
                cycle(clip, bone, "rot", lambda p, ph=ph, side=side, a=sw, b=lf: (0, side * a * s(p, ph), side * -b * max(0.0, c(p, ph))), 8)


# ---------------------------------------------------------------------------------------------------------------------
# Paint

def fill(P, cube, fname, col):
    f = P.face(cube, fname)
    col = hexc(col) if isinstance(col, str) else col
    for i, j in f.each():
        f.set(i, j, col)


def lit(P, cube, fname, x0, y0, w, h, col, core=None, a=235):
    """A rectangle of the monster's one light on a face: skin and glow layer both, hotter in the middle."""
    f, g = P.face(cube, fname), P.face(cube, fname, glow=True)
    for j in range(y0, y0 + h):
        for i in range(x0, x0 + w):
            edge = (w > 2 and i in (x0, x0 + w - 1)) or (h > 2 and j in (y0, y0 + h - 1))
            cc = hexc(col if edge or core is None else core)
            f.set(i, j, cc)
            g.set(i, j, cc[:3] + (a,))


def lit_cube(P, cube, col, core=None, a=235, hot=lambda fname, i, j, f: False):
    """A whole box of light (a flame, a core)."""
    for fname in ALL:
        f, g = P.face(cube, fname), P.face(cube, fname, glow=True)
        for i, j in f.each():
            cc = hexc(core if core is not None and hot(fname, i, j, f) else col)
            f.set(i, j, cc)
            g.set(i, j, cc[:3] + (a,))


def void(P, cube, fname, rim=None, margin=1, top=1):
    """A dark opening in a face (a veil, a mouth, a hollow), inside a rim of the surrounding colour."""
    f = P.face(cube, fname)
    for i, j in f.each():
        inside = margin <= i < f.w - margin and j >= top and j < f.h - (margin if fname in ("top", "bottom") else 0)
        if inside:
            f.set(i, j, hexc("#08070c"))
        elif rim is not None:
            f.set(i, j, rim)


def rows(P, cube, fname, ys, col, x0=0, x1=None):
    f = P.face(cube, fname)
    col = hexc(col) if isinstance(col, str) else col
    for j in ys:
        for i in range(x0, f.w if x1 is None else x1):
            f.set(i, j, col)


def tatter(P, cube, ramp, base, seed=0, faces=("north", "south"), fall=0.12):
    """A hanging plane of cloth with a ragged lower edge (paint both faces of a zero-thick box)."""
    for fname in faces:
        f = P.face(cube, fname)
        for i, j in f.each():
            ii = i if fname in ("north", "west") else f.w - 1 - i
            if j < f.h - 1 - (ii * 3 + seed) % 4:
                f.set(i, j, ramp.at(base - j * fall + (0.5 if ii in (0, f.w - 1) else 0)))


# ---------------------------------------------------------------------------------------------------------------------
# A monster, as its theme file declares it

class Monster:
    """One gate monster: its bestiary id (the rig takes it), the name players see, how to build, paint and move it."""

    def __init__(self, mid, name, make, paint, *, kind, motion=None, life=None, voice=None, box=None, scale=1.0, lift=0.0, poses=None,
                 journal="", tex=None, sunk=(), grounds=True, arms=False):
        self.id, self.name, self.make, self.paint = mid, name, make, paint
        self.kind = kind            # strider, runner, crawler, floater, slider, coiler
        self.motion = motion or {}  # the motion.json entry (gait/body, or fly)
        self.life = life or {}      # the creatures.json entry
        self.voice = voice or {}    # {"ambient": [(vanilla sound name, volume, pitch)], "hurt": .., "death": .., "step": ..}
        self.box = box              # (width, height) of its hitbox in blocks, before scale
        self.scale = scale
        self.lift = lift            # px a floater hangs above its feet in previews
        self.poses = poses or []    # [(label, pose)] for the preview's fight strip
        self.journal = journal
        self.grounds = grounds      # a floater a miss brings down to the floor (its _open clip lies on it); False: it stays aloft
        self.arms = arms            # a knuckle-walker: its front limbs are arms (elbows back) and its knees point forward
        self.sunk = tuple(sunk)     # boxes meant to go into the floor (a spade's blade): the floor check leaves them out

    def build(self):
        """(rig, motion): the rig built once, its motion.json entry (a dict, or a function of the rig, so strides can be
        measured from the legs it really has); the rig's own strides follow its gait's."""
        if getattr(self, "_built", None) is None:
            r = self.make()
            if r.id != self.id:
                raise ValueError(f"{self.id}: its rig is called {r.id}")
            fill_opens(r)
            mo = self.motion(r) if callable(self.motion) else dict(self.motion)
            g = mo.get("gait")
            if g and "stride" in g:
                r.walk_stride, r.run_stride = float(g["stride"][0]), float(g["stride"][1])
            self._built = (r, mo)
        return self._built

    def rig(self):
        return self.build()[0]
