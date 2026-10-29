"""Mother of Spores (SPEC §34, §35.11): the Crowned Host, a floating fungal matriarch. A Flier.

The fungus wears her: a pale woman hangs in the air as if held up by strings, her legs one root tail, and the crown of
glowing stalks fanned out of her back and skull is what fights. Its stalks are the weapons; she is the puppet they jerk
about. Her light story is the sea-green glow in the clubs, her eyes, the spore sacs and the tail's tip.

Moves: Crown Lash (one side of the crown rears back and whips down at you, then the other: LIGHTs), a swoop that closes
the gap (she pitches forward with the crown swept back, and it snaps forward over her as she arrives), Spore Lob (the
tallest stalk bends back and flings a spore), the Drop (she is hauled up, hangs a beat with the crown shut into a spire
and falls, the crown bursting open onto the floor round her: HEAVY, a ring that reaches behind her too), Spore Rain
(she rises, arms up, the crown open wide like a wheel and pulsing: the signature), Birth (she curls round herself,
shudders, and arches back as the sacs on her back burst), Spore Halo (her second phase: the crown shuts, she turns once
and it flares) and Bloom (her third: the crown closes over her like a bud, then everything opens at once).
"""
from kit import core, plans
from kit.core import Move, keyed
from kit.core import stage_clips
from kit.paint import cracks, edge_glow, radial
import fauna_rig as R
from fauna_rig import Ramp, hexc, SIDES, p_blotch, combine, p_strokes

ID = "mother_of_spores"
SCALE = 0.75  # the hitbox: vanilla's ghast at 0.75, 3 x 3 blocks

# The Crowned Host (docs/bosses/ROLLOUT.md, Euan's pick C, 2026-10-28): the fungus wears her. A pale woman hangs in the
# air as if held up by strings, back arched, head fallen to one side, arms limp and open, her legs fused into one long
# root tail trailing behind and down. Out of her back and skull a crown of twelve fungus stalks fans up and out wider
# than she is, each tipped with a glowing club, four of them forking. A ruff of gills rings her neck, shelf fungus grows
# on her left shoulder, a ragged frill of gills hangs where her legs became the tail, and dark growth with three spore
# sacs covers her back. Value story: the sea-green glow (the clubs, her eyes, the sacs, the tail's tip) brightest, then
# her bone-pale skin (the lightest surface: face, arms, ribs), the pale gills, the mauve flesh, the dark plum growth.
HOVER = 32  # her hips' height over the ground, in pixels: the tail's tip hangs just clear of it
STALKS = ((1, 0, 7, 0, 0, 36), (1, 2, 6, 1, 18, 32), (1, -2, 6, 1, -18, 32),  # (on the head?, x, up, z, fan degrees, length)
          (0, 4, 12, 3, 38, 38), (0, -4, 12, 3, -38, 38), (0, 5, 9, 3, 60, 32), (0, -5, 9, 3, -60, 32),
          (0, 3, 6, 3, 80, 26), (0, -3, 6, 3, -80, 26), (0, 0, 10, 3, 10, 42), (0, 1, 4, 3, 100, 18), (0, -1, 4, 3, -100, 16))
FORKS = (3, 4, 6, 9)
SIDES_ = (("l", 1), ("r", -1))
LOB_FROM = (0.7, 4.7, 1.7)  # where the spore leaves her: the tallest stalk's club as it flings (blocks: x, up, forward)


def _bone(parent, name, x, up, z, rot=(0, 0, 0)):
    """A bone placed as the silhouette round placed it: x, height up, z from its parent's pivot."""
    return parent.bone(name, (x, -up, z), rot)


def _box(bone, name, cx, y0, cz, w, h, d):
    """A cube as the round drew it: centred on (cx, cz), from y0 up to y0 + h over the bone's pivot."""
    return bone.cube(name, cx - w / 2, -(y0 + h), cz - d / 2, w, h, d)


def body():
    r = R.Rig(ID, tex=(512, 512))
    core = r.bone("core", (0, 24 - HOVER, 2), (-6, 0, 0))
    _box(core, "pelvis", 0, -2, 0, 8, 5, 5)
    # a ragged frill of gills hangs where the legs became the tail
    for k, (x, z, rz, rx, w, d) in enumerate(((0, -3, 0, 10, 8, 1), (0, 3, 0, -10, 8, 1), (4.5, 0, -14, 0, 1, 5), (-4.5, 0, 14, 0, 1, 5))):
        fr = _bone(core, f"frill{k}", x, -1, z, (rx, 0, rz))
        _box(fr, f"frill{k}", 0, -8, 0, w, 8, d)
    # the root tail: five segments trailing back and down, each turning on from the last, a glowing tip
    seg, prev = core, 2
    for k, (w, ln) in enumerate(((6, 8), (5, 7), (4, 6), (3, 6), (2, 5))):
        seg = _bone(seg, f"tail{k}", 0, -prev, 0, (12, 0, 4) if k == 0 else (14, 0, (k % 2) * 8 - 4))
        _box(seg, f"tail{k}", 0, -ln, 0, w, ln, w)
        prev = ln
    _box(seg, "tail_tip", 0, -7, 0, 2, 2, 2)
    # the body, its back arched: waist, chest, the ribs showing, the dark growth and its spore sacs on her back
    tor = _bone(core, "body", 0, 3, 0, (-10, 0, 4))
    _box(tor, "waist", 0, 0, 0, 6, 6, 4)
    _box(tor, "chest", 0, 6, 0, 10, 8, 6)
    for k in range(3):
        _box(tor, f"rib{k}", 0, 7 + k * 2, -3.2, 8 - k * 2, 1, 1)
    _box(tor, "growth", 2, 3, 3.4, 6, 9, 1)
    _box(tor, "growth2", 3, 6, 4.4, 4, 5, 1)
    for k, (x, y) in enumerate(((1, 9), (4, 6), (2, 4))):
        _box(tor, f"sac{k}", x, y, 4.9, 2, 2, 2)
    for k, w in enumerate((14, 11, 8)):  # the ruff of gills round her neck, stepped
        _box(tor, f"ruff{k}", 0, 13 + k * 1.5, 0, w, 2, w - 4)
    sh = _bone(tor, "shelf", 6, 12, 0, (0, 0, -24))  # shelf fungus on the left shoulder
    _box(sh, "shelf0", 3, 0, 0, 9, 2, 9)
    _box(sh, "shelf1", 2, 2, 0, 6, 2, 6)
    _box(sh, "shelf_gill", 3, -1, 0, 8, 1, 8)
    # the head, fallen forward to one side; the jaw slack; hair of dark gill strands hanging from it
    hd = _bone(tor, "head", 0, 15, 0, (34, 0, -20))
    _box(hd, "head", 0, 0, 0, 7, 8, 7)
    _box(hd, "brow", 0, 5, -3.3, 7, 2, 1)
    _box(hd, "eyes", 0, 4, -3.6, 5, 1, 1)
    jaw = _bone(hd, "jaw", 0, 1, -1)
    _box(jaw, "jaw", 0, -2, -1, 5, 2, 4)
    for k, (x, z, ln) in enumerate(((3.5, 1, 10), (-3.5, 1, 8), (2, 3.5, 12), (-2, 3.5, 9), (0, 3.5, 11))):
        hr = _bone(hd, f"hair{k}", x, 6, z, (-10 if z > 2 else 0, 0, 0))
        _box(hr, f"hair{k}", 0, -ln, 0, 1, ln, 1)
    # the arms, limp and open, long fingers trailing, threads of mycelium hanging from the wrists
    for nm, sg in SIDES_:
        arm = _bone(tor, "arm_" + nm, sg * 5.5, 12, 0, (-8, 0, -sg * 36))  # open: the hands hang out from her sides
        _box(arm, "arm_" + nm, 0, -13, 0, 2, 13, 2)
        el = _bone(arm, "forearm_" + nm, 0, -13, 0, (-12, 0, sg * 14))
        _box(el, "forearm_" + nm, 0, -12, 0, 2, 12, 2)
        _box(el, "wrist_" + nm, 0, -12, 0, 3, 2, 3)
        hn = _bone(el, "hand_" + nm, 0, -12, 0)
        for k in range(3):
            f = _bone(hn, f"finger_{nm}{k}", (k - 1) * 0.7, 0, 0, (8, 0, (k - 1) * 10))
            _box(f, f"finger_{nm}{k}", 0, -6, 0, 1, 6, 1)
        th = _bone(el, "thread_" + nm, sg * 1, -10, 1, (6, 0, 0))
        _box(th, "thread_" + nm, 0, -9, 0, 1, 9, 1)
    # the crown: twelve stalks fanning up and out of her skull and back, each with a stepped glowing club, four forking
    for k, (on_head, x, y, z, fan, ln) in enumerate(STALKS):
        bn = hd if on_head else tor
        st = _bone(bn, f"stalk{k}", x, y, z, ((-34 if on_head else 14) - (k % 3) * 6, 0, fan))  # out over its own side
        lo = round(ln * 0.55)
        _box(st, f"stalk{k}", 0, 0, 0, 2, lo, 2)
        up = _bone(st, f"stalk{k}b", 0, lo, 0, (-6, 0, ((k % 2) * 2 - 1) * 8))
        _box(up, f"stalk{k}b", 0, 0, 0, 1, ln - lo, 1)
        _box(up, f"club{k}", 0, ln - lo, 0, 3, 4, 3)
        _box(up, f"club{k}b", 0, ln - lo + 4, 0, 2, 1, 2)
        if k in FORKS:
            fk = _bone(st, f"fork{k}", 0, lo - 2, 0, (0, 0, -22 if fan >= 0 else 22))
            _box(fk, f"fork{k}", 0, 0, 0, 1, 10, 1)
            _box(fk, f"forkclub{k}", 0, 10, 0, 2, 3, 2)
    r.look = [("head", 0.5, 0.4), ("body", 0.15, 0.1)]
    r.shadow = 1.4
    # secondary parts (SPEC §35.10): the tail and the frill trail her, the hair and threads swing, the stalks sway
    r.springs = [(f"tail{k}", g, hz, 0.25) for k, (g, hz) in enumerate(((0.3, 2.4), (0.45, 2.2), (0.55, 2.0), (0.6, 1.8), (0.6, 1.6)))] \
        + [(f"frill{k}", 0.5, 2.6, 0.3) for k in range(4)] + [(f"hair{k}", 0.7, 2.0, 0.25) for k in range(5)] \
        + [(f"thread_{s}", 0.8, 2.2, 0.25) for s in "lr"] + [(f"finger_{s}{k}", 0.3, 3.0, 0.3) for s in "lr" for k in range(3)] \
        + [(f"stalk{k}b", 0.12, 3.5, 0.35) for k in range(len(STALKS))]
    r.guard = ["tail4"]  # the tail's tip never goes into the ground, in any clip (core.check)
    return r


LEFT = tuple(k for k, s in enumerate(STALKS) if s[4] > 12)  # the stalks fanned out over her left (+x) and her right
RIGHT = tuple(k for k, s in enumerate(STALKS) if s[4] < -12)
# the ground (SPEC §35.10): the tail's tip curls up off the floor; a club that would go in lifts on its stalk
TAIL = [(["tail4"], [("tail0", 0, 60), ("tail1", 0, 40), ("tail2", 0, 40)])]
CROWN = [([f"stalk{k}b"] + ([f"fork{k}"] if k in FORKS else []), [(f"stalk{k}", 0, 70), (f"stalk{k}b", 0, 50)]) for k in range(len(STALKS))]
# lying on the floor at the end of the fall: the arms, the threads and the frill lift to lie along it, the tail curls more
FLOOR = [([f"forearm_{s}", f"thread_{s}"] + [f"finger_{s}{k}" for k in range(3)], [(f"arm_{s}", 0, 90), (f"arm_{s}", 2, 60), (f"forearm_{s}", 0, 90)])
         for s in "lr"] + [([f"frill{k}"], [(f"frill{k}", 0 if k < 2 else 2, 90)]) for k in range(4)] \
    + [(["tail4"], [("tail0", 0, 90), ("tail1", 0, 60), ("tail2", 0, 60)])]


def _tip(k):
    """The far end of stalk k's club in its upper segment's space: where a lash lands."""
    ln = STALKS[k][5]
    return (0, -(ln - round(ln * 0.55) + 4), 0)


def _add(pose, b, rot=(0, 0, 0)):
    """Adds a turn to a bone in a key pose, on top of whatever the pose already turns it by."""
    v = pose.get(b, (0, 0, 0))
    if isinstance(v, dict):
        pose[b] = {"rot": tuple(a + c for a, c in zip(v.get("rot", (0, 0, 0)), rot)), "pos": v.get("pos", (0, 0, 0))}
    else:
        pose[b] = tuple(a + c for a, c in zip(v, rot))
    return pose


def _crown(pose, f=0.0, bow=0.0, only=None):
    """The crown moving as one: f opens it (+: each stalk fans further out by that share of its own fan, the middle ones
    tipping back) or shuts it (-1: every stalk straight up, a closed spire); bow tips the stalks forward over her (+) or
    back (-), degrees. The upper segments carry a share more, so the stalks curve instead of turning stiff."""
    for k, s in enumerate(STALKS):
        if only is not None and k not in only:
            continue
        fan = s[4] if abs(s[4]) > 12 else 0
        x = bow - (10 * f if fan == 0 else 0)
        _add(pose, f"stalk{k}", (x, 0, fan * f))
        _add(pose, f"stalk{k}b", (x * 0.35, 0, fan * f * 0.3))
    return pose


def _arms(pose, swing=0.0, out=0.0, bend=0.0, side=None):
    """Her limp arms: swung back (+) or forward (-), raised out from her sides (+) or in, the elbows bent (+) or opened."""
    for nm, sg in SIDES_:
        if side is None or nm == side:
            _add(pose, "arm_" + nm, (swing, 0, -sg * out))
            _add(pose, "forearm_" + nm, (-bend, 0, 0))
    return pose


def _tail(pose, curl=0.0, sway=0.0):
    """The root tail curls up behind her (+) or hangs straighter (-), each segment a little more than the last; sway
    bends it to one side."""
    for k in range(5):
        _add(pose, f"tail{k}", (curl * (0.6 + 0.1 * k), 0, sway * (0.5 + 0.15 * k)))
    return pose


def _pose(core=(0, 0, 0), at=(0, 0, 0), body=(0, 0, 0), head=(0, 0, 0), crown=(0.0, 0.0), arms=(0.0, 0.0, 0.0), tail=(0.0, 0.0)):
    """A whole-body key: the core's turn and shift, the arch of her back, her head, the crown (open, bow), the arms
    (swing, out, bend) and the tail (curl, sway)."""
    p = {"core": {"rot": core, "pos": at}, "body": body, "head": head}
    _crown(p, *crown)
    _arms(p, *arms)
    _tail(p, *tail)
    return p


def _spin(r, mv, turn):
    """A whole turn added to her core after the move's clip is staged: it runs one way through the strike and ends on a
    whole turn (no turn at all, and the hand-over to the next clip takes angles the short way round)."""
    c = r.clips[core.clip_name(mv)]
    W, A = mv.seconds("windup"), mv.seconds("active")
    out = []
    for t, v, it in c.tracks[("core", "rot")]:
        s = 0.0 if t <= W else turn if t >= W + A else turn * core.ease_io((t - W) / A)
        out.append((t, (v[0], v[1] + s, v[2]), it))
    c.tracks[("core", "rot")] = out


def clips(r):
    import math
    two = 2 * math.pi
    # idle: she hangs as if from strings, drifting up and down; her head lolls, the arms swing a beat behind the body,
    # every stalk of the crown sways on its own phase and a slow wave runs down the tail
    keys, n, L = [], 8, 4.0
    for k in range(n + 1):
        p = k / n
        s, c = math.sin(p * two), math.cos(p * two)
        pose = {"core": {"rot": (2.5 * s, 2 * c, 1.5 * c), "pos": (0, 1.5 * s, 0)}, "body": (-2 * s, 0, c),
                "head": (3 * math.sin(p * two - 0.6), 2 * c, 2 * s)}
        for nm, sg in SIDES_:
            pose["arm_" + nm] = (5 * math.sin(p * two - 0.9), 0, sg * 2 * c)
            pose["forearm_" + nm] = (-4 * math.sin(p * two - 1.5), 0, 0)
        for i in range(len(STALKS)):
            pose[f"stalk{i}"] = (2.5 * math.sin(p * two + i * 0.7), 0, 3 * math.cos(p * two + i * 0.5))
            pose[f"stalk{i}b"] = (3 * math.sin(p * two + i * 0.7 - 0.8), 0, 3 * math.cos(p * two + i * 0.5 - 0.8))
        for i in range(5):
            pose[f"tail{i}"] = (5 * math.sin(p * two - 0.7 * i), 0, 4 * math.cos(p * two - 0.6 * i))
        keys.append((L * p, pose))
    keyed(r, "idle", L, keys, loop=True)
    # fly: she leans into it, the crown swept back and the arms and tail streaming behind (on top of the idle)
    lean = lambda k: _pose(core=(8 + 2 * k, 0, 0), crown=(-0.05, -8 - 4 * k), arms=(16 + 6 * k, -4, 0), tail=(10 + 4 * k, 0))
    keyed(r, "fly", 1.6, [(0, lean(0)), (0.8, lean(1)), (1.6, lean(0))], loop=True)
    recoil = _pose(core=(-12, 0, 0), at=(0, 1, 2), body=(-10, 0, 0), head=(-24, 0, 14), crown=(-0.15, -14), arms=(-22, 10, 0), tail=(-8, 0))
    keyed(r, "hurt", 0.5, [(0, {}), (0.07, recoil), (0.3, _pose(core=(3, 0, 0), head=(4, 0, 0), crown=(0.04, 4))), (0.5, {})])
    for nm, sg in (("hurt_l", 1), ("hurt_r", -1)):
        side = _pose(core=(-6, -14 * sg, -12 * sg), at=(-sg * 1.5, 0.5, 0), body=(-4, 0, -6 * sg), head=(-8, -18 * sg, 10 * sg),
                     crown=(-0.05, -6), arms=(-10, 8, 0), tail=(0, 10 * sg))
        _crown(side, -0.15, -10, only=LEFT if sg > 0 else RIGHT)  # the side that was struck bends away from it
        keyed(r, nm, 0.55, [(0, {}), (0.08, side), (0.3, _pose(core=(0, 4 * sg, 3 * sg), crown=(0.03, 3))), (0.55, {})])
    # broken: the strings go slack and she sags, her head hanging, the crown wilting forward (held while she's broken)
    sag = _pose(core=(16, 0, 6), at=(0, -12, 0), body=(18, 0, 0), head=(24, 0, 10), crown=(0.25, 30), arms=(8, -28, -10), tail=(16, 0))
    keyed(r, "kneel", 0.6, [(0, {}), (0.25, _pose(core=(8, 0, 2), at=(0, -5, 0), body=(8, 0, 0), head=(12, 0, 4), crown=(0.1, 12),
                                                  arms=(4, -12, 0), tail=(6, 0))), (0.6, sag)])
    keyed(r, "rise", 0.8, [(0, sag), (0.4, _pose(core=(-6, 0, 0), at=(0, 2, 0), body=(-4, 0, 0), crown=(-0.05, -6), arms=(-6, 4, 0))), (0.8, {})])
    # death: a last jerk on the strings, the crown flaring; then she sinks, and the crown folds forward over her as she falls
    jerk = _pose(core=(-18, 0, 0), at=(0, 3, 0), body=(-14, 0, 0), head=(-40, 0, 20), crown=(0.4, -20), arms=(-30, 24, -6), tail=(-10, 0))
    slump = _pose(core=(20, 0, 8), at=(0, -10, 0), body=(14, 0, 4), head=(20, 0, 10), crown=(0.3, 30), arms=(10, -20, -10), tail=(20, 0))
    fall = _pose(core=(40, 0, 18), at=(0, -26, 0), body=(20, 0, 6), head=(34, 0, 20), crown=(0.5, 50), arms=(-40, 40, -10), tail=(60, 6))
    keyed(r, "death", 2.6, [(0, {}), (0.3, jerk), (1.2, slump), (1.9, fall), (2.6, fall)])
    mv = [lash("lash", "r"), lash("lash_b", "l", windup=9, recover=12), swoop(), lob(), drop(), rain(), birth(), halo(), bloom(), unfurl()]
    for m in mv:
        stage_clips(r, m)
    _spin(r, mv[7], 360.0)
    core.ground_pass(r, TAIL, clips=list(r.clips), skip=())
    core.ground_pass(r, CROWN, clips=list(r.clips), skip=())
    core.ground_pass(r, FLOOR, clips=["death"], skip=())
    return mv


def lash(mid, side, windup=11, active=4, recover=12):
    """Crown Lash: one side of the crown rears back over her shoulder, her body twisting away with it, then whips down
    and across in front of her, the clubs landing at a standing player's chest; the lash jerks her forward after it."""
    sg = 1 if side == "l" else -1
    other = "r" if side == "l" else "l"
    hit, near = ((3, 5), 7) if side == "l" else ((4, 6), 8)

    def whip(p, x, z, xb):
        for k in hit:
            _add(p, f"stalk{k}", (x, 0, z))
            _add(p, f"stalk{k}b", (xb, 0, 0))
        _add(p, f"stalk{near}", (x * 0.4, 0, z * 0.4))
        return p
    pre = whip(_pose(core=(-3, -sg * 6, 0), crown=(-0.05, -3), arms=(4, 0, 0)), -14, sg * 4, -8)
    coil = whip(_pose(core=(-10, -sg * 20, sg * 6), at=(0, 2, 2), body=(-8, -sg * 8, 0), head=(-8, sg * 12, 6 * sg), crown=(-0.25, -14),
                      tail=(14, sg * 8)), -45, sg * 14, -30)
    _arms(coil, 24, 6, 0, side)
    _arms(coil, -10, 0, 0, other)
    hitk = whip(_pose(core=(18, sg * 18, -sg * 6), at=(0, -3, -8), body=(10, sg * 8, 0), head=(16, -sg * 8, 0), crown=(0.0, 20),
                      tail=(-6, -sg * 8)), 66, -sg * 30, 24)
    _arms(hitk, -35, 10, 0, side)
    _arms(hitk, 15, 0, 0, other)
    after = whip(_pose(core=(21, sg * 22, -sg * 8), at=(0, -4, -9), body=(12, sg * 10, 0), head=(20, -sg * 10, 0), crown=(0.0, 24),
                       tail=(-8, -sg * 10)), 80, -sg * 36, 36)
    _arms(after, -40, 12, 0, side)
    _arms(after, 18, 0, 0, other)
    back = whip(_pose(core=(8, sg * 6, 0), at=(0, -1, -3), body=(4, 0, 0), crown=(0.0, 8)), 24, -sg * 10, 8)
    return Move(mid, "LIGHT", windup, active, recover, title="Crown Lash" if mid == "lash" else "", track=10.0, rng=(0, 3.4),
                kinds=("near",), damage=0.8, knock=0.7, plant=(), hit=[(f"stalk{k}b", _tip(k), 3) for k in hit],
                stages={"windup": [(0.3, pre), (1.0, coil)], "active": [(0.5, hitk), (1.0, after)], "recover": [(0.5, back)]},
                lag={f"stalk{k}b": 0.03 for k in hit})


def swoop():
    """She pitches forward and glides at you, the crown swept back like folded wings and her arms and tail streaming
    behind; as she arrives the whole crown snaps forward over her head and rakes down in front (closes the gap)."""
    gather = _pose(core=(-8, 0, 0), at=(0, 3, 2), body=(-6, 0, 0), head=(-8, 0, 6), crown=(-0.1, -8), arms=(8, 4, 0), tail=(-4, 0))
    lean = _pose(core=(30, 0, 0), at=(0, -4, -2), body=(6, 0, 0), head=(-24, 0, 14), crown=(-0.35, -40), arms=(45, -10, 0), tail=(24, 0))
    rake = _pose(core=(18, 0, 0), at=(0, -2, -6), body=(10, 0, 0), head=(6, 0, 6), crown=(0.15, 84), arms=(-30, 20, 0), tail=(4, 0))
    for k in range(len(STALKS)):
        _add(rake, f"stalk{k}b", (30, 0, 0))
    return Move("swoop", "LIGHT", 12, 10, 14, track=8.0, advance=8.0, rng=(5, 12), kinds=("gap",), damage=0.9, knock=0.8, plant=(),
                hit=[(f"stalk{k}b", _tip(k), 3) for k in (9, 3, 4, 5, 6)],
                stages={"windup": [(0.3, gather), (1.0, lean)], "active": [(0.6, lean), (1.0, rake)],
                        "recover": [(0.5, _pose(core=(6, 0, 0), crown=(0.05, 14), arms=(-6, 4, 0)))]})


def lob():
    """Spore Lob: the tallest stalk bends far back over her like a catapult's arm, her head lifting, then it flings
    forward and throws a spore from its club."""
    def sling(p, x, xb, z=0):
        _add(p, "stalk9", (x, 0, z))
        _add(p, "stalk9b", (xb, 0, z * 0.35))
        return p
    # it winds back round the side, her body rolling with it, so lobs in a row loop on without stopping upright
    pre = sling(_pose(core=(-4, 0, 20), body=(-2, 0, 12), head=(-6, 0, 22), crown=(-0.15, -10), arms=(2, 12, 0)), -14, -6, 75)
    for k in range(len(STALKS)):
        _add(pre, f"stalk{k}", (0, 0, 22))  # the whole crown leans over with her
    back = sling(_pose(core=(-12, 0, 0), at=(0, 2, 2), body=(-10, 0, 0), head=(-20, 0, 12), crown=(-0.25, -24), arms=(-10, 14, 0),
                       tail=(-6, 0)), -50, -34)
    # it lets go reaching up and out at you (a lob climbs), then carries on over and down
    flick = sling(_pose(core=(8, 0, 0), at=(0, -1, -1), body=(6, 0, 0), head=(6, 0, 4), crown=(0.1, 10), arms=(8, 4, 0)), 22, 14)
    after = sling(_pose(core=(12, 0, 0), at=(0, -1, -2), body=(10, 0, 0), head=(10, 0, 4), crown=(0.1, 22), arms=(10, 4, 0)), 50, 36)
    return Move("lob", "LIGHT", 12, 4, 12, title="Spore Lob", track=9.0, ability="Spore Lob", origin=LOB_FROM, rng=(4, 20),
                kinds=("ranged",), damage=0, knock=0, plant=(),
                stages={"windup": [(0.3, pre), (1.0, back)], "active": [(0.5, flick), (1.0, after)],
                        "recover": [(0.5, sling(_pose(crown=(0.03, 4)), 14, 8))]})


def drop():
    """The Drop: the strings haul her up, arms out, head thrown back and the crown shut tight into a spire; she hangs
    there a beat (the hold) and falls, the crown bursting open and slamming down onto the floor all round her."""
    dip = _pose(core=(6, 0, 0), at=(0, -3, 0), body=(6, 0, 0), head=(8, 0, 0), crown=(-0.2, 10), arms=(6, -10, 0), tail=(4, 0))
    up = _pose(core=(-6, 0, 0), at=(0, 14, 0), body=(-14, 0, 0), head=(-30, 0, 18), crown=(-0.75, -10), arms=(-20, 60, -6), tail=(-10, 0))
    # the body really falls (rise below: she drops to the floor), so the pose only adds the crown's burst and her fold
    slam = _pose(core=(14, 0, 0), at=(0, -8, 0), body=(16, 0, 0), head=(20, 0, -6), crown=(0.55, 45), arms=(-10, -20, 0), tail=(10, 0))
    for k in range(len(STALKS)):
        _add(slam, f"stalk{k}b", (20, 0, 0))
    settle = _pose(core=(6, 0, 0), at=(0, -6, 0), body=(8, 0, 0), head=(12, 0, 0), crown=(0.3, 30), arms=(-4, -10, 0), tail=(8, 0))
    calm = _pose(core=(-2, 0, 0), at=(0, -3, 0), crown=(0.05, 6))
    return Move("drop", "HEAVY", 16, 4, 22, title="The Drop", hold=10, track=4.0, rng=(0, 2.4), kinds=("area", "behind"), damage=1.4,
                knock=1.2, plant=(), rise=-3.0, disc=3.0,
                hit=[("stalk9b", _tip(9), 3), ("stalk5b", _tip(5), 3), ("stalk6b", _tip(6), 3), ("body", (0, -14, 0), 7)],
                stages={"windup": [(0.3, dip), (1.0, up)], "active": [(0.5, slam), (1.0, slam)], "recover": [(0.4, settle), (0.8, calm)]})


def rain():
    """Spore Rain (the signature): she sinks, then rises with her arms going up and her face to the sky, the crown
    opening out wide like a wheel; it pulses twice and the spores fall in circles."""
    sink = _pose(core=(6, 0, 0), at=(0, -3, 0), body=(6, 0, 0), head=(10, 0, 0), crown=(-0.3, 10), arms=(-6, -10, 0), tail=(4, 0))
    rise = _pose(core=(-8, 0, 0), at=(0, 10, 0), body=(-12, 0, 0), head=(-26, 0, 14), crown=(0.25, -10), arms=(-14, 50, -4), tail=(-6, 0))
    wide = _pose(core=(-4, 0, 0), at=(0, 12, 0), body=(-16, 0, 0), head=(-34, 0, 18), crown=(0.6, -20), arms=(-20, 75, -10), tail=(-8, 0))
    beat = _pose(core=(-6, 0, 0), at=(0, 11, 0), body=(-14, 0, 0), head=(-30, 0, 16), crown=(0.35, -10), arms=(-18, 66, -8), tail=(-6, 0))
    return Move("rain", "PERILOUS", 24, 12, 20, title="Spore Rain", track=0.0, ability="Spore Rain", rng=(0, 24), kinds=("area", "ranged"),
                signature=True, damage=0, knock=0, plant=(),
                stages={"windup": [(0.3, sink), (0.7, rise), (1.0, wide)], "active": [(0.3, beat), (0.6, wide), (1.0, beat)],
                        "recover": [(0.5, _pose(at=(0, 4, 0), crown=(0.15, -4), arms=(-6, 20, 0)))]})


def birth():
    """Birth: she curls round herself, the crown closing over her, and shudders; then she arches back hard, arms flung
    open, as the sacs on her back burst and the colony drops out of her."""
    clench = _pose(core=(12, 0, 0), at=(0, -2, 0), body=(20, 0, 0), head=(18, 0, -6), crown=(-0.4, 20), arms=(-30, -26, 40), tail=(20, 0))

    def shake(sg):
        p = dict(clench)
        p["core"] = {"rot": (12, 4 * sg, 3.5 * sg), "pos": (0.6 * sg, -2, 0)}
        return p
    burst = _pose(core=(-14, 0, 0), at=(0, 3, 0), body=(-20, 0, 0), head=(-36, 0, 16), crown=(0.5, -24), arms=(-20, 50, -10), tail=(2, 0))
    return Move("birth", "NONE", 14, 12, 14, title="Birth", track=0.0, ability="Birth", rng=(0, 99), kinds=("summon",), damage=0, knock=0,
                plant=(), stages={"windup": [(0.4, clench), (0.6, shake(1)), (0.8, shake(-1)), (1.0, shake(1))],
                                  "active": [(0.3, burst), (1.0, burst)], "recover": [(0.5, _pose(core=(2, 0, 0), crown=(0.1, 0)))]})


def halo():
    """Spore Halo (her second phase): the crown shuts tight round her and her arms fold in, then she turns once (the
    turn is added after, _spin) and the crown flares out in a ring of spores."""
    pre = _pose(core=(3, 0, 0), crown=(-0.3, 2), arms=(-4, -8, 10))
    fold = _pose(core=(6, 0, 0), at=(0, -2, 0), body=(8, 0, 0), head=(14, 0, 0), crown=(-0.85, 6), arms=(-14, -24, 30), tail=(6, 0))
    out = _pose(core=(-4, 0, 0), at=(0, 2, 0), body=(-6, 0, 0), head=(-14, 0, 10), crown=(0.6, -14), arms=(-10, 40, -6), tail=(-4, 0))
    out2 = _pose(core=(-3, 0, 0), at=(0, 2, 0), body=(-5, 0, 0), head=(-12, 0, 8), crown=(0.45, -10), arms=(-8, 32, -4), tail=(-4, 0))
    return Move("halo", "LIGHT", 16, 10, 16, title="Spore Halo", track=0.0, ability="Spore Halo", rng=(0, 8), kinds=("area",), damage=0,
                knock=0, plant=(), stages={"windup": [(0.3, pre), (1.0, fold)], "active": [(0.5, out), (1.0, out2)],
                                           "recover": [(0.5, _pose(at=(0, 1, 0), crown=(0.15, -2)))]})


def bloom():
    """Bloom (her third phase): she sinks and the crown closes over her like a bud, then everything opens at once."""
    bud = _pose(core=(16, 0, 0), at=(0, -8, 0), body=(22, 0, 0), head=(26, 0, 0), crown=(-0.7, 50), arms=(-30, -26, 46), tail=(24, 0))
    for k in range(len(STALKS)):
        _add(bud, f"stalk{k}b", (30, 0, 0))  # the stalks curl over her
    opn = _pose(core=(-16, 0, 0), at=(0, 6, 0), body=(-18, 0, 0), head=(-34, 0, 18), crown=(0.7, -24), arms=(-20, 80, -14), tail=(-14, 0))
    return Move("bloom", "PERILOUS", 22, 10, 18, title="Bloom", track=0.0, ability="Bloom", rng=(0, 10), kinds=("area",), damage=0,
                knock=0, plant=(), stages={"windup": [(0.45, core._scaled(bud, 0.8)), (1.0, bud)], "active": [(0.25, opn), (1.0, opn)],
                                           "recover": [(0.5, _pose(core=(-4, 0, 0), crown=(0.2, -6), arms=(-6, 20, 0)))]})


def unfurl():
    """Her intro: she hangs curled up under the crown like a bud, then it opens and she lifts her face to you."""
    curled = _pose(core=(14, 0, 0), at=(0, -4, 0), body=(18, 0, 0), head=(30, 0, 0), crown=(-0.7, 40), arms=(-20, -26, 40), tail=(20, 0))
    opn = _pose(core=(-8, 0, 0), at=(0, 3, 0), body=(-8, 0, 0), head=(-22, 0, 16), crown=(0.3, -10), arms=(-10, 30, -6), tail=(-4, 0))
    return Move("unfurl", "NONE", 14, 24, 14, track=0.0, rng=(0, 99), kinds=("intro",), damage=0, knock=0, plant=(),
                stages={"windup": [(1.0, curled)], "active": [(0.4, opn), (1.0, opn)], "recover": [(0.5, _pose(core=(2, 0, 0), crown=(0.05, 2)))]})


# Her sounds (SPEC §35.8): sculk, wings and breath; she flies, so no steps. Chosen by ear on paper, not yet heard.
SOUNDS = {
    "tell_light": [("entity.sniffer.scenting", 1.4, 0.6)],
    "tell_heavy": [("block.sculk.charge", 1.8, 0.6), ("entity.phantom.flap", 1.4, 0.5)],
    "tell_perilous": [("block.sculk_shrieker.shriek", 2.0, 0.7)],
    "swing_light": [("entity.player.attack.sweep", 1.2, 0.8), ("entity.creaking.sway", 1.2, 0.7)],
    "swing_heavy": [("entity.ender_dragon.flap", 2.0, 0.8), ("entity.player.attack.sweep", 1.6, 0.6)],
    "cast": [("block.sculk_catalyst.bloom", 1.8, 0.7)],
    "impact": [("item.mace.smash_ground_heavy", 2.2, 0.6), ("block.sculk.break", 2.0, 0.5)],
    "roar": [("block.sculk_shrieker.shriek", 2.5, 0.6), ("entity.warden.agitated", 2.0, 0.6)],
}
MOVE_SOUNDS = {"birth": {"swing": "cast"}, "rain": {"impact": ""}, "halo": {"impact": ""}}

FIGHT = {
    "intro": {"pose": "idle", "move": "unfurl"},
    "phases": [
        {"pool": [
            {"move": "lash", "weight": 3, "when": "near", "then": [["lash_b"]]},
            {"move": "lob", "weight": 3},
            {"move": "swoop", "weight": 2, "then": [["lash_b"]]},
            {"move": "drop", "weight": 1},
            {"move": "drop", "weight": 3, "when": "behind"},
            {"move": "rain", "weight": 1},
        ]},
        # the colony comes with the second phase, as in GateBestiary
        {"transition": "halo", "pool": [
            {"move": "lash", "weight": 3, "when": "near", "then": [["lash_b"], ["lash_b", "drop"]]},
            {"move": "lob", "weight": 3, "then": [["lob"]]},
            {"move": "swoop", "weight": 2, "then": [["lash_b"]]},
            {"move": "drop", "weight": 3, "when": "behind"},
            {"move": "rain", "weight": 1},
            {"move": "birth", "weight": 1},
            {"move": "halo", "weight": 1},
        ]},
        {"transition": "bloom", "pool": [
            {"move": "lash", "weight": 3, "when": "near", "then": [["lash_b", "drop"]]},
            {"move": "lob", "weight": 2, "then": [["lob", "lob"]]},
            {"move": "swoop", "weight": 2, "then": [["lash_b"]]},
            {"move": "drop", "weight": 2},
            {"move": "drop", "weight": 3, "when": "behind"},
            {"move": "rain", "weight": 1},
            {"move": "halo", "weight": 1},
        ]},
    ],
    "enrage": {"below": 0.15, "speed": 1.2},
}


def paint(r, P_):
    """Five materials in a value order: the sea-green glow brightest (the clubs, her eyes, the spore sacs, the tail's tip),
    then her bone-pale skin (the lightest surface, so the woman reads first: face, arms, fingers, ribs), the pale gills
    (the ruff, the frill, the stalks), the mauve flesh fading to the dark plum growth that covers her back and tail."""
    from kit import parts
    cap = Ramp("#140a11", "#1c0f18", "#2a1522", "#3a1c2e", "#4c243a", "#602e46", "#763a52")
    gill = Ramp("#4a3e36", "#665848", "#86765e", "#a69478", "#c4b294", "#ddd0b2")
    flesh = Ramp("#2e1a28", "#3a2232", "#4e2e42", "#663c54", "#7e4c66", "#985e7a", "#b2748e")
    skin = Ramp("#5e5650", "#7c7268", "#9a9086", "#b8aea2", "#d4cabc", "#ebe3d6", "#f6f0e6")
    green = ("#16a07e", "#2fbf8a", "#5fe0b0", "#9cf4d2", "#d8fff0")
    of = lambda *ps: parts.cubes_of(r, *ps)
    veins = lambda f, i, j: (-0.8 if (i * 5 + j * 3) % 11 == 0 else 0)
    for c in ["head", "jaw"] + of("arm_", "forearm_", "finger_", "rib"):
        P_.paint(c, skin, 4.4 if c in ("head", "jaw") else 4.0, veins, grad=0.8, rim=0.6, ao=1.0, noise=0.25)
    P_.paint("brow", skin, 3.4)
    # the flesh darkens down her body into the tail, the tail's last segments the dark plum of the growth
    for c, base in (("chest", 3.6), ("waist", 3.4), ("pelvis", 3.2), ("tail0", 3.1), ("tail1", 2.9), ("tail2", 2.7)):
        P_.paint(c, flesh, base, lambda f, i, j: (-0.9 if j % 3 == 0 and f.name in SIDES else 0.2), grad=1.2, noise=0.4)
    for c in ["tail3", "tail4", "growth", "growth2"] + of("hair", "shelf0", "shelf1"):
        P_.paint(c, cap, 3.2, combine(p_blotch("mos_cap", 5, 0.5, 2), lambda f, i, j: 0), grad=1.0, noise=0.5)
    # the gills: pale, ribbed, a faint glow down every few ribs; the frill's hem ragged
    for c in of("ruff", "frill", "wrist_") + ["shelf_gill"]:
        P_.paint(c, gill, 3.4, lambda f, i, j: (0.8 if i % 2 == 0 else -0.5) if f.name in SIDES + ("bottom",) else 0)
        for fname in SIDES + ("bottom",):
            g = P_.face(c, fname, glow=True)
            for i, j in g.each():
                if i % 5 == 0 and j > 0:
                    g.set(i, j, hexc(green[0], 90 + 12 * j))
    for k in range(4):
        c = f"frill{k}"
        for fname in SIDES:
            f, g = P_.face(c, fname), P_.face(c, fname, glow=True)
            for i, j in f.each():
                if j >= f.h - 1 - (R._h(c, fname, i) % 3) and j > 3:
                    f.set(i, j, (0, 0, 0, 0))
                    g.set(i, j, (0, 0, 0, 0))
    # the stalks: pale, banded where they grew in spurts, the glow climbing toward the clubs
    for c in of("stalk", "fork"):
        P_.paint(c, gill, 3.6, lambda f, i, j: (-1.0 if j % 4 == 3 else 0.3) if f.name in SIDES else 0, noise=0.3)
        if c.endswith("b") or c.startswith("fork"):
            for fname in SIDES:
                edge_glow(P_, c, fname, green[1], rows=3, alpha=150, side="top")
    for c in of("club", "forkclub", "sac") + ["tail_tip"]:
        P_.paint(c, Ramp("#0c3a30", "#14584a", "#1e7a64"), 2.0)
        for fname in ("top", "bottom") + SIDES:
            radial(P_, c, fname, [green[4], green[3], green[2], green[1]], alpha=240)
    cracks(P_, "chest", ("north", "west", "east"), flesh.at(1), (green[0], green[1]), n=1, seed="mos_chest", alpha=170)
    cracks(P_, "growth", ("south",), cap.at(0), (green[0], green[2]), n=2, seed="mos_growth", alpha=200)
    # the face: hollow eyes lit sea-green under a heavy brow, the slack mouth dark
    P_.paint("eyes", cap, 1.0)
    with P_.eyes():
        f, g = P_.face("eyes", "north"), P_.face("eyes", "north", glow=True)
        for i, j in f.each():
            f.set(i, j, hexc("#0c1a14"))
            if i in (0, 1, 3, 4):
                g.set(i, j, hexc(green[4] if i in (1, 3) else green[2], 255))
    fj = P_.face("jaw", "north")
    for i in range(1, fj.w - 1):
        fj.set(i, 0, cap.at(0))
    return P_.skin, P_.glow


def design():
    r = body()
    r.pack()
    mv = clips(r)
    return {"rig": r, "plan": None, "moves": mv, "fight": FIGHT, "paint": paint, "scale": SCALE, "half": 1.5, "sounds": SOUNDS, "move_sounds": MOVE_SOUNDS}
