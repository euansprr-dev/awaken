"""Carillon's animals (M4 B3b): pigeons, gulls, cats, dogs and the carters' horses with their carts. Client-only bodies
(citizens.client.CityCritters), so the server pays nothing for them.

    python3 tools/art/build_all.py city_fauna        textures (textures/entity/city/<id>[_<variant>].png and _blink copies)
                                                     and src/main/java/dev/awaken/citizens/client/CityFaunaData.java
    python3 tools/art/specs/city_fauna.py preview DIR [ids]
                                                     per animal: views of every variant by day, a size line-up with a
                                                     player, and the strip of its clips

Every body is a hero rig (fauna_rig.py: bones, packed UVs, value masses, keyframe clips sampled like vanilla), parsed on
the client by mobs.client.anim.HeroRig and posed by citizens.client.CritterModel with the life layer (SPEC §23) on top.
Small animals are drawn finer than the world (designed large and scaled down: pigeons and gulls 0.5, cats 0.6, dogs
0.7): at vanilla's density a pigeon is four pixels long and reads as a placeholder.

Conventions (vanilla ModelPart): model pixels, y down, ground at y = 24, the animal faces -z, rotations in degrees.
Clip names are the brain's vocabulary (CityCritters): idle, walk, trot, fly, glide, peck, call, sit, loaf, sleep, groom,
sniff, pant, lie, toss, flare.
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ART = os.path.abspath(os.path.join(HERE, ".."))
ROOT = os.path.abspath(os.path.join(HERE, "../../.."))
sys.path.insert(0, HERE)
sys.path.insert(0, ART)
import fauna_rig as R  # noqa: E402
from fauna_rig import Rig, Ramp, hexc, lerpc, mul, p_fur, p_feathers, p_strokes, p_blotch, combine, SIDES  # noqa: E402,F401
from fauna_anim import cycle, wave, s, c  # noqa: E402

JAVA_OUT = os.path.join(ROOT, "src/main/java/dev/awaken/citizens/client/CityFaunaData.java")

# render scale per animal (the body is designed this much larger than it stands in the world)
SCALE = {"city_pigeon": 0.5, "city_gull": 0.6, "city_cat": 0.6, "city_dog": 0.7, "city_horse": 1.0, "city_cart": 1.0}


def build_hook():
    pass


def sym(fn):
    """Calls fn(side, name, mirror, share) for the left (+x) then the right (-x) side."""
    for side, nm in ((1, "l"), (-1, "r")):
        fn(side, nm, side < 0, (lambda n: None) if side > 0 else (lambda n: n))


# =====================================================================================================================
# Birds. Wings are modelled spread (span along x, chord along z, a pixel thick) and folded by their bone's rest
# rotation (x -90 lays the chord down the flank, y -/+90 swings the span back along the body); flight clips add the
# opposite to open them, then flap about z (the left wing's tip rises with negative z, the right's with positive).
# =====================================================================================================================

def bird(rid, tex, body_len, wing_arm, wing_hand, tail_len, legs_h, neck_h):
    r = Rig(rid, tex)
    r.walk_stride, r.run_stride, r.run_speed, r.shadow = 0.6, 1.2, (0.08, 0.16), 0.25
    by = 24 - legs_h - 3
    body = r.bone("body", (0, by, 0))
    half = body_len / 2
    body.cube("torso", -3, -3, -half, 6, 6, body_len)
    body.cube("breast", -2.5, -2.5, -half - 2, 5, 5, 2)
    neck = body.bone("neck", (0, -2, -half - 1), (-12, 0, 0))
    neck.cube("oneck", -2, -neck_h, -1.5, 4, neck_h + 1, 3)
    head = neck.bone("head", (0, -neck_h, 0), (12, 0, 0))
    head.cube("ohead", -2, -3.5, -2.5, 4, 4, 4)
    beak = head.bone("beak", (0, -1.5, -2.5))
    tail = body.bone("tail", (0, -1.5, half), (-6, 0, 0))
    tail.cube("otail", -2.5, -0.5, 0, 5, 1, tail_len)

    def legs(side, nm, mir, sh):
        lg = body.bone("leg_" + nm, (side * 1.3, 3, 0.5))
        lg.cube("oleg_" + nm, -0.5, 0, -0.5, 1, legs_h, 1, mirror=mir, share=sh("oleg_l"))
        lg.cube("foot_" + nm, -1, legs_h, -2.5, 2, 0, 3, mirror=mir, share=sh("foot_l"))

        arm = body.bone("wing_" + nm, (side * 3, -2.5, -half + 1), (-90, -side * 90, 0))
        if side > 0:
            arm.cube("arm_" + nm, 0, 0, 0, wing_arm, 1, 5)
        else:
            arm.cube("arm_" + nm, -wing_arm, 0, 0, wing_arm, 1, 5, mirror=True, share="arm_l")
        hand = arm.bone("hand_" + nm, (side * wing_arm, 0, 0))
        if side > 0:
            hand.cube("prim_" + nm, 0, 0, 0, wing_hand, 1, 4)
        else:
            hand.cube("prim_" + nm, -wing_hand, 0, 0, wing_hand, 1, 4, mirror=True, share="prim_l")
    sym(legs)
    r.look = [("neck", 0.35, 0.3), ("head", 0.65, 0.7)]
    return r, beak


def wing_open(clip, t0, t1=None, spread=1.0):
    """Keys that open the folded wings (added to the rest pose) and hold them open from t0 to t1."""
    t1 = clip.length if t1 is None else t1
    for nm, sg in (("wing_l", 1), ("wing_r", -1)):
        clip.rot(nm, [(t0, 90 * spread, sg * 90 * spread, 0), (t1, 90 * spread, sg * 90 * spread, 0)], "lin")


def flap(clip, up, down, tip_lag=0.12, n=8, base=0.0, open_=1.0):
    """A flap cycle on top of the open wings: the arm rows from up to down, the hand follows a little later."""
    for nm, sg in (("wing_l", 1), ("wing_r", -1)):
        cycle(clip, nm, "rot", lambda p, sg=sg: (90 * open_, sg * 90 * open_, -sg * (base + (up - down) / 2 * c(p) + (up + down) / 2)), n)
        hn = "hand_" + nm[-1]
        cycle(clip, hn, "rot", lambda p, sg=sg: (0, sg * 6 * s(p), -sg * 22 * c(p, -tip_lag)), n)


def pigeon():
    r, beak = bird("city_pigeon", (64, 32), body_len=9, wing_arm=6, wing_hand=7, tail_len=6, legs_h=3, neck_h=4)
    beak.cube("obeak", -0.5, -0.5, -2, 1, 1, 2)
    r.by_name["head"].cube("cere", -1, -2.5, -3, 2, 1, 1)
    r.pack()
    pigeon_clips(r)
    return r


def gull():
    r, beak = bird("city_gull", (64, 64), body_len=11, wing_arm=9, wing_hand=11, tail_len=6, legs_h=4, neck_h=4)
    beak.cube("obeak", -0.5, -0.5, -3, 1, 1, 3)
    beak.cube("hook", -0.5, 0.5, -3, 1, 1, 1)
    r.pack()
    pigeon_clips(r, gull=True)
    return r


def pigeon_clips(r, gull=False):
    idle = r.clip("idle", 3.2, loop=True)
    # restless: a glance, a settle of the feathers, the tail flicks
    idle.rot("head", [(0, 0, 0, 0), (0.5, 0, 28, 0), (0.9, 0, 28, 0), (1.1, 0, -20, 4), (1.7, 0, -20, 4), (2.0, 0, 0, 0), (3.2, 0, 0, 0)])
    idle.rot("tail", [(0, 0, 0, 0), (2.4, 0, 0, 0), (2.5, -10, 0, 0), (2.7, 0, 0, 0), (3.2, 0, 0, 0)])
    idle.scale("body", [(0, 1, 1, 1), (2.6, 1, 1, 1), (2.75, 1.08, 1.06, 1.02), (3.0, 1, 1, 1), (3.2, 1, 1, 1)])
    walk = r.clip("walk", 0.5, loop=True)
    # the pigeon's walk: the head holds still in space, then thrusts forward with each step
    walk.pos("neck", [(0, 0, 0, 1.6), (0.18, 0, 0, -0.8), (0.25, 0, 0, 1.6), (0.43, 0, 0, -0.8), (0.5, 0, 0, 1.6)], "lin")
    walk.rot("leg_l", [(0, -30, 0, 0), (0.25, 30, 0, 0), (0.5, -30, 0, 0)])
    walk.rot("leg_r", [(0, 30, 0, 0), (0.25, -30, 0, 0), (0.5, 30, 0, 0)])
    walk.rot("body", [(0, 0, 0, -4), (0.25, 0, 0, 4), (0.5, 0, 0, -4)])
    walk.rot("tail", [(0, 4, 0, 0), (0.125, -4, 0, 0), (0.25, 4, 0, 0), (0.375, -4, 0, 0), (0.5, 4, 0, 0)])
    peck = r.clip("peck", 1.6, loop=True)
    # head down, two quick pecks, up and look
    peck.rot("body", [(0, 0, 0, 0), (0.25, 22, 0, 0), (1.1, 22, 0, 0), (1.35, 0, 0, 0), (1.6, 0, 0, 0)])
    peck.rot("neck", [(0, 0, 0, 0), (0.25, 40, 0, 0), (0.4, 62, 0, 0), (0.5, 40, 0, 0), (0.65, 62, 0, 0), (0.75, 40, 0, 0),
                      (1.1, 40, 0, 0), (1.35, 0, 0, 0), (1.6, 0, 0, 0)])
    peck.rot("tail", [(0, 0, 0, 0), (0.25, -20, 0, 0), (1.1, -20, 0, 0), (1.35, 0, 0, 0), (1.6, 0, 0, 0)])
    fly = r.clip("fly", 0.36 if not gull else 0.62, loop=True)
    flap(fly, up=70 if not gull else 46, down=-40 if not gull else -30)
    cycle(fly, "body", "pos", lambda p: (0, 0.6 * s(p), 0), 8)
    fly.rot("leg_l", [(0, 70, 0, 0), (fly.length, 70, 0, 0)])
    fly.rot("leg_r", [(0, 70, 0, 0), (fly.length, 70, 0, 0)])
    fly.rot("neck", [(0, 20, 0, 0), (fly.length, 20, 0, 0)])
    fly.rot("tail", [(0, 6, 0, 0), (fly.length, 6, 0, 0)])
    glide = r.clip("glide", 2.0, loop=True)
    # wings held out (a gull's crooked M: the hands sweep back and droop), a slow rock
    for nm, sg in (("wing_l", 1), ("wing_r", -1)):
        cycle(glide, nm, "rot", lambda p, sg=sg: (90, sg * 90, -sg * ((12 if gull else 6) + 3 * s(p))), 8)
        glide.rot("hand_" + nm[-1], [(0, 0, sg * (-24 if gull else -8), sg * (16 if gull else 4)), (2.0, 0, sg * (-24 if gull else -8), sg * (16 if gull else 4))])
    cycle(glide, "body", "rot", lambda p: (0, 0, 4 * s(p)), 8)
    glide.rot("leg_l", [(0, 70, 0, 0), (2.0, 70, 0, 0)])
    glide.rot("leg_r", [(0, 70, 0, 0), (2.0, 70, 0, 0)])
    glide.rot("neck", [(0, 20, 0, 0), (2.0, 20, 0, 0)])
    flare = r.clip("flare", 0.5)
    # landing: the body tips up, wings cup forward, legs reach
    for nm, sg in (("wing_l", 1), ("wing_r", -1)):
        flare.rot(nm, [(0, 90, sg * 90, -sg * 10), (0.25, 90, sg * 60, sg * 40), (0.5, 90, sg * 70, sg * 10)])
    flare.rot("body", [(0, 0, 0, 0), (0.25, -28, 0, 0), (0.5, -10, 0, 0)])
    flare.rot("leg_l", [(0, 60, 0, 0), (0.3, -10, 0, 0), (0.5, 0, 0, 0)])
    flare.rot("leg_r", [(0, 60, 0, 0), (0.3, -10, 0, 0), (0.5, 0, 0, 0)])
    flare.rot("tail", [(0, 0, 0, 0), (0.25, -25, 0, 0), (0.5, 0, 0, 0)])
    call = r.clip("call", 1.2)
    if gull:
        # the long call: head thrown back, beak wide, then forward and down with each yelp
        call.rot("neck", [(0, 0, 0, 0), (0.2, -40, 0, 0), (0.5, 30, 0, 0), (0.7, 10, 0, 0), (0.85, 30, 0, 0), (1.2, 0, 0, 0)])
        call.rot("beak", [(0, 0, 0, 0), (0.15, 0, 0, 0), (0.25, 28, 0, 0), (0.9, 28, 0, 0), (1.1, 0, 0, 0), (1.2, 0, 0, 0)])
    else:
        # the coo: the throat swells, the head bows, the tail fans
        call.scale("neck", [(0, 1, 1, 1), (0.3, 1.35, 1.2, 1.35), (0.8, 1.35, 1.2, 1.35), (1.2, 1, 1, 1)])
        call.rot("neck", [(0, 0, 0, 0), (0.3, 30, 0, 0), (0.6, 10, 0, 0), (0.9, 30, 0, 0), (1.2, 0, 0, 0)])
        call.scale("tail", [(0, 1, 1, 1), (0.3, 1.4, 1, 1), (0.9, 1.4, 1, 1), (1.2, 1, 1, 1)])
    for nm in ("hurt", "death"):
        cl = r.clip(nm, 0.3)
        cl.rot("body", [(0, 0, 0, 0), (0.3, 0, 0, 0)])


# =====================================================================================================================
# Quadrupeds
# =====================================================================================================================

def cat():
    r = Rig("city_cat", (64, 64))
    r.walk_stride, r.run_stride, r.run_speed, r.shadow = 0.9, 1.8, (0.12, 0.22), 0.3
    body = r.bone("body", (0, 15, 0))
    body.cube("torso", -2.5, -2.5, -7, 5, 5, 13)
    body.cube("chest", -2, -2, -8, 4, 5, 1)
    neck = body.bone("neck", (0, -1.5, -7.5), (15, 0, 0))
    neck.cube("oneck", -1.5, -2.5, -2, 3, 3, 2)
    head = neck.bone("head", (0, -2, -1.5), (-12, 0, 0))
    head.cube("ohead", -3, -3, -4, 6, 5, 5)
    head.cube("muzzle", -1.5, -0.5, -5, 3, 2, 1)
    head.cube("nose", -0.5, -0.5, -5.5, 1, 1, 1, inflate=-0.1)
    jaw = head.bone("jaw", (0, 1.5, -3.5))
    jaw.cube("ojaw", -1, 0, -1.5, 2, 1, 1)

    def side(sd, nm, mir, sh):
        ear = head.bone("ear_" + nm, (sd * 1.8, -3, -1.5), (0, 0, sd * 10))
        ear.cube("oear_" + nm, -1, -2, -0.5, 2, 2, 1, mirror=mir, share=sh("oear_l"))
        ear.cube("tip_" + nm, -0.5, -3, -0.5, 1, 1, 1, mirror=mir, share=sh("tip_l"))
        ws = head.bone("whisk_" + nm, (sd * 1.5, 0.5, -4.8), (0, sd * -20, 0))
        ws.cube("whisk_" + nm, 0 if sd > 0 else -3, -1, 0, 3, 2, 0, mirror=mir, share=sh("whisk_l"))
        fl = body.bone("fleg_" + nm, (sd * 1.5, 1.5, -5.5))
        fl.cube("ofleg_" + nm, -1, 0, -1, 2, 7.5 if False else 7, 2, mirror=mir, share=sh("ofleg_l"))
        fl.cube("fpaw_" + nm, -1, 7, -1.5, 2, 0, 2, mirror=mir, share=sh("fpaw_l"))
        hl = body.bone("hleg_" + nm, (sd * 1.5, 1, 4.5))
        hl.cube("othigh_" + nm, -1, -1, -1.5, 2, 4, 3, mirror=mir, share=sh("othigh_l"))
        hk = hl.bone("hock_" + nm, (0, 3, 1))
        hk.cube("ohock_" + nm, -1, 0, -1, 2, 4, 2, mirror=mir, share=sh("ohock_l"))
    sym(side)
    t1 = body.bone("tail1", (0, -2, 6), (-45, 0, 0))
    t1.cube("otail1", -1, -1, 0, 2, 2, 5)
    t2 = t1.bone("tail2", (0, 0, 5), (20, 0, 0))
    t2.cube("otail2", -1, -1, 0, 2, 2, 5)
    t3 = t2.bone("tail3", (0, 0, 5), (20, 0, 0))
    t3.cube("otail3", -1, -1, 0, 2, 2, 3, inflate=-0.15)
    r.look = [("neck", 0.3, 0.3), ("head", 0.7, 0.7)]
    r.pack()
    cat_clips(r)
    return r


def cat_clips(r):
    idle = r.clip("idle", 4.0, loop=True)
    idle.rot("ear_l", [(0, 0, 0, 0), (1.3, 0, 0, 0), (1.4, -20, 0, 14), (1.55, 0, 0, 0), (4.0, 0, 0, 0)])
    cycle(idle, "tail1", "rot", lambda p: (4 * s(p), 10 * s(p), 0), 8)
    cycle(idle, "tail2", "rot", lambda p: (0, 14 * s(p, -0.12), 0), 8)
    cycle(idle, "tail3", "rot", lambda p: (0, 18 * s(p, -0.24), 0), 8)
    walk = r.clip("walk", 0.9, loop=True)
    # the cat's walk: lateral-ish, the shoulders rolling, head low and level, tail up in a hook
    for nm, ph in (("fleg_l", 0), ("hleg_r", 0.08), ("fleg_r", 0.5), ("hleg_l", 0.58)):
        cycle(walk, nm, "rot", lambda p, ph=ph: (30 * s(p, ph), 0, 0), 8)
    for nm, ph in (("hock_l", 0.58), ("hock_r", 0.08)):
        cycle(walk, nm, "rot", lambda p, ph=ph: (-20 * max(0, c(p, ph)), 0, 0), 8)
    cycle(walk, "body", "rot", lambda p: (0, 3 * s(p), 2 * s(p, 0.25)), 8)
    cycle(walk, "body", "pos", lambda p: (0, 0.3 * c(p * 2), 0), 8)
    walk.rot("tail1", [(0, 100, 0, 0), (0.9, 100, 0, 0)])
    cycle(walk, "tail3", "rot", lambda p: (-35 + 6 * s(p), 0, 0), 8)
    trot = r.clip("trot", 0.5, loop=True)
    for nm, ph in (("fleg_l", 0), ("hleg_r", 0), ("fleg_r", 0.5), ("hleg_l", 0.5)):
        cycle(trot, nm, "rot", lambda p, ph=ph: (45 * s(p, ph), 0, 0), 8)
    cycle(trot, "body", "pos", lambda p: (0, 0.8 * abs(s(p)), 0), 8)
    trot.rot("tail1", [(0, 45, 0, 0), (0.5, 45, 0, 0)])
    sit = r.clip("sit", 3.0, loop=True)
    # sitting tall: hindquarters down, forelegs straight, tail wrapped round the paws
    sit.rot("body", [(0, -42, 0, 0), (3.0, -42, 0, 0)], "lin")
    sit.pos("body", [(0, 0, -4.5, 2), (3.0, 0, -4.5, 2)], "lin")
    sit.rot("neck", [(0, 30, 0, 0), (3.0, 30, 0, 0)], "lin")
    sit.rot("head", [(0, 12, 0, 0), (3.0, 12, 0, 0)], "lin")
    for nm in ("fleg_l", "fleg_r"):
        sit.rot(nm, [(0, 42, 0, 0), (3.0, 42, 0, 0)], "lin")
    for nm in ("hleg_l", "hleg_r"):
        sit.rot(nm, [(0, -48, 0, 0), (3.0, -48, 0, 0)], "lin")
    for nm in ("hock_l", "hock_r"):
        sit.rot(nm, [(0, 90, 0, 0), (3.0, 90, 0, 0)], "lin")
    sit.rot("tail1", [(0, 30, 70, 0), (3.0, 30, 70, 0)], "lin")
    sit.rot("tail2", [(0, 0, 40, 0), (3.0, 0, 40, 0)], "lin")
    cycle(sit, "tail3", "rot", lambda p: (0, 30 + 14 * s(p), 0), 8)
    loaf = r.clip("loaf", 3.0, loop=True)
    # the loaf: legs folded under, body down on the tiles, eyes half shut (the life layer blinks slower)
    loaf.pos("body", [(0, 0, -5.5, 0), (3.0, 0, -5.5, 0)], "lin")
    for nm in ("fleg_l", "fleg_r"):
        loaf.rot(nm, [(0, -90, 0, 0), (3.0, -90, 0, 0)], "lin")
        loaf.pos(nm, [(0, 0, 4.5, 2), (3.0, 0, 4.5, 2)], "lin")
    for nm in ("hleg_l", "hleg_r"):
        loaf.rot(nm, [(0, 80, 0, 0), (3.0, 80, 0, 0)], "lin")
        loaf.pos(nm, [(0, 0, 3, 0), (3.0, 0, 3, 0)], "lin")
    for nm in ("hock_l", "hock_r"):
        loaf.rot(nm, [(0, -150, 0, 0), (3.0, -150, 0, 0)], "lin")
    loaf.rot("neck", [(0, 20, 0, 0), (3.0, 20, 0, 0)], "lin")
    loaf.rot("tail1", [(0, 45, 40, 0), (3.0, 45, 40, 0)], "lin")
    loaf.rot("tail2", [(0, 0, 40, 0), (3.0, 0, 40, 0)], "lin")
    loaf.rot("tail3", [(0, 0, 30, 0), (3.0, 0, 30, 0)], "lin")
    sleep = r.clip("sleep", 4.0, loop=True)
    # curled up: down on the tiles like the loaf, head turned back along the flank, tail wrapped round to the nose
    for (bn, tg), keys in list(loaf.tracks.items()):
        if bn not in ("neck", "tail1", "tail2", "tail3"):
            sleep._add(bn, tg, [(0,) + keys[0][1], (4.0,) + keys[0][1]], "lin")
    sleep.rot("neck", [(0, 50, -75, 0), (4.0, 50, -75, 0)], "lin")
    sleep.rot("head", [(0, 25, -30, -25), (4.0, 25, -30, -25)], "lin")
    sleep.rot("tail1", [(0, 40, -70, 0), (4.0, 40, -70, 0)], "lin")
    sleep.rot("tail2", [(0, 0, -55, 0), (4.0, 0, -55, 0)], "lin")
    sleep.rot("tail3", [(0, 0, -45, 0), (4.0, 0, -45, 0)], "lin")
    groom = r.clip("groom", 2.4, loop=True)
    # sitting, a forepaw raised to the mouth, licked, then wiped over the ear
    for track in ("body", "neck", "fleg_r", "hleg_l", "hleg_r", "hock_l", "hock_r", "tail1", "tail2"):
        for (bn, tg), keys in list(sit.tracks.items()):
            if bn == track:
                groom._add(bn, tg, [(0,) + keys[0][1]] + [(2.4,) + keys[0][1]], "lin")
    groom.rot("fleg_l", [(0, 42, 0, 0), (0.3, -60, 0, -10), (1.4, -70, 0, -10), (1.7, -100, 0, 0), (2.1, 42, 0, 0), (2.4, 42, 0, 0)])
    groom.rot("head", [(0, 12, 0, 0), (0.3, 30, 20, 0), (0.5, 40, 20, 0), (0.7, 30, 20, 0), (0.9, 40, 20, 0), (1.1, 30, 20, 0),
                       (1.4, 30, 20, 0), (1.8, -10, 10, 20), (2.1, 12, 0, 0), (2.4, 12, 0, 0)])
    groom.rot("jaw", [(0, 0, 0, 0), (0.4, 18, 0, 0), (0.5, 0, 0, 0), (0.8, 18, 0, 0), (0.9, 0, 0, 0), (2.4, 0, 0, 0)])
    for nm in ("hurt", "death"):
        cl = r.clip(nm, 0.3)
        cl.rot("body", [(0, 0, 0, 0), (0.3, 0, 0, 0)])


def dog():
    r = Rig("city_dog", (64, 64))
    r.walk_stride, r.run_stride, r.run_speed, r.shadow = 1.1, 2.4, (0.14, 0.26), 0.4
    body = r.bone("body", (0, 12.5, 0))
    body.cube("torso", -3, -3, -6, 6, 6, 12)
    body.cube("ruff", -3.5, -3.5, -7, 7, 7, 4, inflate=0.0)
    neck = body.bone("neck", (0, -2.5, -6.5), (22, 0, 0))
    neck.cube("oneck", -2, -4, -2, 4, 5, 4)
    neck.cube("kerchief", -2.5, -1, -2.5, 5, 2, 5, inflate=0.1)
    head = neck.bone("head", (0, -3.5, -1), (-14, 0, 0))
    head.cube("ohead", -3, -3.5, -4, 6, 5, 5)
    head.cube("snout", -1.5, -1, -7, 3, 3, 3)
    head.cube("nose", -1, -1.3, -7.5, 2, 1, 1)
    jaw = head.bone("jaw", (0, 1.5, -4))
    jaw.cube("ojaw", -1.5, 0, -3, 3, 1, 3)
    tongue = jaw.bone("tongue", (0, 0.5, -2))
    tongue.cube("otongue", -0.5, 0, -1.5, 1, 0, 2)

    def side(sd, nm, mir, sh):
        ear = head.bone("ear_" + nm, (sd * 2.5, -3, -1), (0, 0, sd * 70))
        ear.cube("oear_" + nm, -1.5 if sd > 0 else -1.5, -0.5, -1, 3, 1, 2, mirror=mir, share=sh("oear_l"))
        tip = ear.bone("eartip_" + nm, (sd * 1.5, 0, 0), (0, 0, sd * 40))
        tip.cube("oeartip_" + nm, 0 if sd > 0 else -2, -0.5, -1, 2, 1, 2, mirror=mir, share=sh("oeartip_l"))
        fl = body.bone("fleg_" + nm, (sd * 2, 2.5, -4.5))
        fl.cube("ofleg_" + nm, -1, 0, -1, 2, 9, 2, mirror=mir, share=sh("ofleg_l"))
        hl = body.bone("hleg_" + nm, (sd * 2, 2, 4.5))
        hl.cube("othigh_" + nm, -1.5, -1, -1.5, 3, 5, 3, mirror=mir, share=sh("othigh_l"))
        hk = hl.bone("hock_" + nm, (0, 4, 1))
        hk.cube("ohock_" + nm, -1, 0, -1, 2, 5.5 if False else 5, 2, mirror=mir, share=sh("ohock_l"))
    sym(side)
    t1 = body.bone("tail1", (0, -2.5, 6), (-50, 0, 0))
    t1.cube("otail1", -1, -1, 0, 2, 2, 4)
    t2 = t1.bone("tail2", (0, 0, 4), (15, 0, 0))
    t2.cube("otail2", -1, -1, 0, 2, 2, 4, inflate=-0.1)
    r.look = [("neck", 0.35, 0.3), ("head", 0.65, 0.7)]
    r.pack()
    dog_clips(r)
    return r


def dog_clips(r):
    idle = r.clip("idle", 3.0, loop=True)
    cycle(idle, "tail1", "rot", lambda p: (0, 8 * s(p * 2), 0), 8)
    idle.rot("ear_l", [(0, 0, 0, 0), (2.0, 0, 0, 0), (2.1, -15, 0, -15), (2.3, 0, 0, 0), (3.0, 0, 0, 0)])
    walk = r.clip("walk", 0.8, loop=True)
    for nm, ph in (("fleg_l", 0), ("hleg_r", 0.1), ("fleg_r", 0.5), ("hleg_l", 0.6)):
        cycle(walk, nm, "rot", lambda p, ph=ph: (28 * s(p, ph), 0, 0), 8)
    for nm, ph in (("hock_l", 0.6), ("hock_r", 0.1)):
        cycle(walk, nm, "rot", lambda p, ph=ph: (-18 * max(0, c(p, ph)), 0, 0), 8)
    cycle(walk, "body", "pos", lambda p: (0, 0.4 * c(p * 2), 0), 8)
    cycle(walk, "neck", "rot", lambda p: (3 * c(p * 2), 0, 0), 8)
    cycle(walk, "tail1", "rot", lambda p: (0, 14 * s(p * 2), 0), 8)
    trot = r.clip("trot", 0.45, loop=True)
    for nm, ph in (("fleg_l", 0), ("hleg_r", 0), ("fleg_r", 0.5), ("hleg_l", 0.5)):
        cycle(trot, nm, "rot", lambda p, ph=ph: (48 * s(p, ph), 0, 0), 8)
    for nm, ph in (("hock_l", 0.5), ("hock_r", 0)):
        cycle(trot, nm, "rot", lambda p, ph=ph: (-30 * max(0, c(p, ph)), 0, 0), 8)
    cycle(trot, "body", "pos", lambda p: (0, 1.0 * abs(s(p)), 0), 8)
    cycle(trot, "body", "rot", lambda p: (3 * s(p * 2), 0, 0), 8)
    cycle(trot, "tail1", "rot", lambda p: (-10, 22 * s(p * 2), 0), 8)
    cycle(trot, "jaw", "rot", lambda p: (14 + 4 * s(p * 2), 0, 0), 8)
    sit = r.clip("sit", 3.0, loop=True)
    sit.rot("body", [(0, -35, 0, 0), (3.0, -35, 0, 0)], "lin")
    sit.pos("body", [(0, 0, -4, 2.5), (3.0, 0, -4, 2.5)], "lin")
    sit.rot("neck", [(0, 25, 0, 0), (3.0, 25, 0, 0)], "lin")
    sit.rot("head", [(0, 10, 0, 0), (3.0, 10, 0, 0)], "lin")
    for nm in ("fleg_l", "fleg_r"):
        sit.rot(nm, [(0, 35, 0, 0), (3.0, 35, 0, 0)], "lin")
    for nm in ("hleg_l", "hleg_r"):
        sit.rot(nm, [(0, -50, 0, 0), (3.0, -50, 0, 0)], "lin")
    for nm in ("hock_l", "hock_r"):
        sit.rot(nm, [(0, 95, 0, 0), (3.0, 95, 0, 0)], "lin")
    sit.rot("tail1", [(0, 70, 0, 0), (3.0, 70, 0, 0)], "lin")
    cycle(sit, "tail1", "rot", lambda p: (0, 10 * s(p * 3), 0), 8)
    pant = r.clip("pant", 0.5, loop=True)
    # mouth open, tongue out, the chest heaving quick
    cycle(pant, "jaw", "rot", lambda p: (24 + 6 * s(p), 0, 0), 8)
    pant.scale("tongue", [(0, 1, 1, 1.6), (0.5, 1, 1, 1.6)], "lin")
    cycle(pant, "body", "scale", lambda p: (1 + 0.03 * s(p), 1 + 0.04 * s(p), 1), 8)
    sniff = r.clip("sniff", 1.6, loop=True)
    sniff.rot("neck", [(0, 0, 0, 0), (0.3, 55, 0, 0), (1.3, 55, 0, 0), (1.6, 0, 0, 0)])
    sniff.rot("head", [(0, 0, 0, 0), (0.3, 10, 0, 0), (0.5, 10, 18, 0), (0.8, 10, -18, 0), (1.1, 10, 10, 0), (1.3, 10, 0, 0), (1.6, 0, 0, 0)])
    cycle(sniff, "tail1", "rot", lambda p: (-20, 20 * s(p * 4), 0), 8)
    lie = r.clip("lie", 3.0, loop=True)
    lie.pos("body", [(0, 0, -7, 0), (3.0, 0, -7, 0)], "lin")
    for nm in ("fleg_l", "fleg_r"):
        lie.rot(nm, [(0, -85, 0, 0), (3.0, -85, 0, 0)], "lin")
    for nm in ("hleg_l", "hleg_r"):
        lie.rot(nm, [(0, -70, 0, 20 if nm.endswith("l") else -20), (3.0, -70, 0, 20 if nm.endswith("l") else -20)], "lin")
    lie.rot("neck", [(0, 30, 0, 0), (3.0, 30, 0, 0)], "lin")
    lie.rot("head", [(0, 20, 0, 0), (3.0, 20, 0, 0)], "lin")
    lie.rot("tail1", [(0, 60, 25, 0), (3.0, 60, 25, 0)], "lin")
    for nm in ("hurt", "death"):
        cl = r.clip(nm, 0.3)
        cl.rot("body", [(0, 0, 0, 0), (0.3, 0, 0, 0)])


def horse():
    """A draught horse: heavy, feathered white over the hooves, a braided mane, a padded collar hung with brass bells."""
    r = Rig("city_horse", (128, 128))
    r.walk_stride, r.run_stride, r.run_speed, r.shadow = 2.0, 3.6, (0.12, 0.25), 1.0
    body = r.bone("body", (0, 3, 0))
    body.cube("barrel", -5, -5, -11, 10, 10, 22)
    body.cube("rump", -5.5, -5.5, 6, 11, 9, 6, inflate=0.0)
    body.cube("chestb", -4.5, -4, -13, 9, 9, 2)
    body.cube("girth", -5, -5, -6, 10, 10, 2, inflate=0.35)
    neck = body.bone("neck", (0, -3, -11), (30, 0, 0))
    neck.cube("oneck", -3, -13, -4, 6, 14, 8)
    neck.cube("mane", -1, -14, 3, 2, 14, 2)
    neck.cube("collar", -3.5, -3, -5, 7, 4, 10, inflate=0.6)
    for k, (bx, by, bz) in enumerate(((2.6, -1.0, -5.8), (-3.6, -1.0, -5.8), (3.8, 0.6, -2.0), (-4.8, 0.6, -2.0))):
        neck.cube(f"bell{k}", bx, by, bz, 1, 1, 1, inflate=0.15, share=None if k == 0 else "bell0")
    head = neck.bone("head", (0, -13, -1), (8, 0, 0))
    head.cube("skull", -3, -3, -6, 6, 5, 7)
    head.cube("muzzleh", -2.5, -1.5, -11, 5, 4, 5)
    head.cube("bridle", -3, -2, -8, 6, 1, 2, inflate=0.2)
    head.cube("blinker_l", 3, -3, -6, 1, 2, 3, inflate=0.1)
    head.cube("blinker_r", -4, -3, -6, 1, 2, 3, mirror=True, inflate=0.1, share="blinker_l")
    jaw = head.bone("jaw", (0, 1.5, -6))
    jaw.cube("ojaw", -2, 0, -5, 4, 2, 5)

    def side(sd, nm, mir, sh):
        ear = head.bone("ear_" + nm, (sd * 2, -3, -1), (10, 0, sd * 12))
        ear.cube("oear_" + nm, -1, -3, -0.5, 2, 3, 1, mirror=mir, share=sh("oear_l"))
        fl = body.bone("fleg_" + nm, (sd * 3.2, 4, -8))
        fl.cube("ofleg_" + nm, -2, -1, -2, 4, 8, 4, mirror=mir, share=sh("ofleg_l"))
        fk = fl.bone("fknee_" + nm, (0, 7, 0))
        fk.cube("ofknee_" + nm, -1.5, 0, -1.5, 3, 7, 3, mirror=mir, share=sh("ofknee_l"))
        fk.cube("ffeath_" + nm, -2, 6, -2, 4, 3, 4, inflate=0.3, mirror=mir, share=sh("ffeath_l"))
        fk.cube("fhoof_" + nm, -2, 9, -2, 4, 1, 4, inflate=0.1, mirror=mir, share=sh("fhoof_l"))
        hl = body.bone("hleg_" + nm, (sd * 3.2, 3, 9))
        hl.cube("othigh_" + nm, -2.5, -2, -2.5, 5, 9, 5, mirror=mir, share=sh("othigh_l"))
        hk = hl.bone("hhock_" + nm, (0, 7, 1))
        hk.cube("ohhock_" + nm, -1.5, 0, -1.5, 3, 7, 3, mirror=mir, share=sh("ohhock_l"))
        hk.cube("hfeath_" + nm, -2, 7, -2, 4, 3, 4, inflate=0.3, mirror=mir, share=sh("hfeath_l"))
        hk.cube("hhoof_" + nm, -2, 10, -2, 4, 1, 4, inflate=0.1, mirror=mir, share=sh("hhoof_l"))
        tr = body.bone("trace_" + nm, (sd * 5.6, -1.5, -6))
        tr.cube("otrace_" + nm, -0.5, -0.5, 0, 1, 1, 18, mirror=mir, share=sh("otrace_l"))
    sym(side)
    tail = body.bone("tail", (0, -4, 11.5), (-55, 0, 0))
    tail.cube("dock", -1.5, -1, 0, 3, 3, 3)
    t2 = tail.bone("tail2", (0, 0.5, 3), (-25, 0, 0))
    t2.cube("otail2", -2, -1.5, 0, 4, 4, 8)
    t3 = t2.bone("tail3", (0, 0, 8), (-5, 0, 0))
    t3.cube("otail3", -1.5, -1, 0, 3, 3, 5)
    r.look = [("neck", 0.35, 0.25), ("head", 0.65, 0.6)]
    r.pack()
    horse_clips(r)
    return r


def horse_clips(r):
    idle = r.clip("idle", 6.0, loop=True)
    # patient: a hind leg resting, ears turning to sounds, the tail swishing a fly away
    idle.rot("hhock_r", [(0, 0, 0, 0), (6.0, 0, 0, 0)])
    idle.rot("hleg_r", [(0, -6, 0, 0), (6.0, -6, 0, 0)])
    idle.rot("hhock_r", [(0, 18, 0, 0), (6.0, 18, 0, 0)])
    idle.rot("ear_l", [(0, 0, 0, 0), (1.0, 0, 0, 0), (1.3, -20, -30, 0), (2.6, -20, -30, 0), (3.0, 0, 0, 0), (6.0, 0, 0, 0)])
    idle.rot("ear_r", [(0, 0, 0, 0), (3.5, 0, 0, 0), (3.8, -15, 25, 0), (4.6, -15, 25, 0), (5.0, 0, 0, 0), (6.0, 0, 0, 0)])
    idle.rot("tail2", [(0, 0, 0, 0), (4.2, 0, 0, 0), (4.5, 10, 30, 0), (4.8, 0, -25, 0), (5.2, 0, 0, 0), (6.0, 0, 0, 0)])
    walk = r.clip("walk", 1.4, loop=True)
    # the four-beat walk of a heavy horse: left hind, left fore, right hind, right fore; the head nods twice a stride
    for nm, ph in (("hleg_l", 0.0), ("fleg_l", 0.25), ("hleg_r", 0.5), ("fleg_r", 0.75)):
        cycle(walk, nm, "rot", lambda p, ph=ph: (22 * s(p, ph), 0, 0), 8)
    for nm, ph in (("fknee_l", 0.25), ("fknee_r", 0.75)):
        cycle(walk, nm, "rot", lambda p, ph=ph: (32 * max(0, c(p, ph + 0.05)), 0, 0), 8)
    for nm, ph in (("hhock_l", 0.0), ("hhock_r", 0.5)):
        cycle(walk, nm, "rot", lambda p, ph=ph: (-26 * max(0, c(p, ph + 0.05)), 0, 0), 8)
    cycle(walk, "neck", "rot", lambda p: (4 * s(p * 2), 0, 0), 8)
    cycle(walk, "body", "pos", lambda p: (0, 0.5 * c(p * 2), 0), 8)
    cycle(walk, "body", "rot", lambda p: (0, 0, 1.5 * s(p)), 8)
    cycle(walk, "tail2", "rot", lambda p: (0, 6 * s(p), 0), 8)
    toss = r.clip("toss", 1.2)
    # a toss of the head (the bells ring), a snort
    toss.rot("neck", [(0, 0, 0, 0), (0.25, -14, 0, 0), (0.45, 8, 0, 6), (0.7, -4, 0, -4), (1.2, 0, 0, 0)])
    toss.rot("head", [(0, 0, 0, 0), (0.25, -20, 0, 0), (0.45, 10, 0, 0), (1.2, 0, 0, 0)])
    toss.rot("jaw", [(0, 0, 0, 0), (0.3, 10, 0, 0), (0.6, 0, 0, 0), (1.2, 0, 0, 0)])
    for nm in ("hurt", "death"):
        cl = r.clip(nm, 0.3)
        cl.rot("body", [(0, 0, 0, 0), (0.3, 0, 0, 0)])


def cart():
    """A two-wheeled market cart drawn by the horse (its traces end at the horse's collar): spoked wheels the renderer
    turns, a bed with three loads (bones shown one at a time), and the carter on the seat, hooded and wrapped."""
    r = Rig("city_cart", (128, 128))
    r.walk_stride, r.run_stride, r.run_speed, r.shadow = 2.0, 3.6, (0.12, 0.25), 1.2
    bed = r.bone("bed", (0, 13, 0))
    bed.cube("floor", -9, 0, -10, 18, 2, 22)
    bed.cube("side_l", 8, -5, -10, 1, 5, 22)
    bed.cube("side_r", -9, -5, -10, 1, 5, 22, mirror=True, share="side_l")
    bed.cube("front", -8, -6, -11, 16, 6, 1)
    bed.cube("tailb", -8, -4, 11, 16, 4, 1)
    bed.cube("axle", -11, 3, -1, 22, 2, 2)
    bed.cube("shaft_l", 6, 0, -40, 1, 1, 30)
    bed.cube("shaft_r", -7, 0, -40, 1, 1, 30, mirror=True, share="shaft_l")
    bed.cube("seat", -7, -9, -10, 14, 2, 5)
    for nm, x in (("wheel_l", 10.5), ("wheel_r", -10.5)):
        w = bed.bone(nm, (x, 4, 0))
        w.cube("hub" + nm[-2:], -1.5, -1.5, -1.5, 3, 3, 3)
        w.cube("rimv" + nm[-2:], -1, -8, -2, 2, 16, 4, share=None if nm == "wheel_l" else "rimv_l")
        w.cube("rimh" + nm[-2:], -1, -2, -8, 2, 4, 16, share=None if nm == "wheel_l" else "rimh_l")
        w.cube("spokev" + nm[-2:], -0.5, -7, -0.5, 1, 14, 1, share=None if nm == "wheel_l" else "spokev_l")
        w.cube("spokeh" + nm[-2:], -0.5, -0.5, -7, 1, 1, 14, share=None if nm == "wheel_l" else "spokeh_l")
        d = w.bone("diag" + nm[-2:], (0, 0, 0), (45, 0, 0))
        d.cube("rimd" + nm[-2:], -0.9, -7.2, -2.6, 2, 15, 5, inflate=-0.1, share=None if nm == "wheel_l" else "rimd_l")
        d.cube("rime" + nm[-2:], -0.9, -2.6, -7.2, 2, 5, 15, inflate=-0.1, share=None if nm == "wheel_l" else "rime_l")
    hay = bed.bone("load_hay", (0, -1, 0))
    hay.cube("hay1", -8, -7, -5, 16, 7, 16)
    hay.cube("hay2", -6, -11, -3, 12, 4, 12)
    crates = bed.bone("load_crates", (0, -1, 0))
    crates.cube("crate1", -7.5, -7, -4, 7, 7, 7)
    crates.cube("crate2", 0.5, -7, -2, 7, 7, 7, share="crate1")
    crates.cube("crate3", -4, -14, 0, 7, 7, 7, share="crate1")
    crates.cube("sack1", 1, -5, 6, 6, 5, 4)
    casks = bed.bone("load_casks", (0, -1, 0))
    for k, (x, z) in enumerate(((-7, -4), (0.5, -4), (-7, 4), (0.5, 4))):
        casks.cube(f"cask{k}", x, -8, z, 6, 8, 6, share=None if k == 0 else "cask0")
    driver = bed.bone("driver", (0, -9, -7.5))
    driver.cube("cloak", -4, -11, -2.5, 8, 11, 5)
    driver.cube("lap", -3.5, -2, -6.5, 7, 2, 5)
    driver.cube("shins", -3.5, 0, -7.5, 7, 7, 2)
    dh = driver.bone("dhead", (0, -11, -0.5))
    dh.cube("hood", -3, -6, -3, 6, 6, 6)
    dh.cube("face", -2, -4, -3.2, 4, 3, 1, inflate=-0.1)
    for nm, sd in (("darm_l", 1), ("darm_r", -1)):
        a = driver.bone(nm, (sd * 3.5, -9, -0.5), (-60, 0, -sd * 8))
        a.cube("o" + nm, -1.5 if sd > 0 else -1.5, 0, -1.5, 3, 8, 3, share=None if sd > 0 else "odarm_l")
    r.look = [("dhead", 0.8, 0.6)]
    r.pack()
    idle = r.clip("idle", 3.0, loop=True)
    # the carter: reins in both hands, a shake of them now and then, the head turning to the street
    idle.rot("darm_l", [(0, 0, 0, 0), (2.2, 0, 0, 0), (2.35, -14, 0, 0), (2.5, 0, 0, 0), (3.0, 0, 0, 0)])
    idle.rot("darm_r", [(0, 0, 0, 0), (2.2, 0, 0, 0), (2.35, -14, 0, 0), (2.5, 0, 0, 0), (3.0, 0, 0, 0)])
    walk = r.clip("walk", 1.4, loop=True)
    cycle(walk, "driver", "pos", lambda p: (0, 0.3 * abs(s(p * 2)), 0), 8)
    cycle(walk, "bed", "rot", lambda p: (0.6 * s(p * 2), 0, 0.5 * s(p)), 8)
    for nm in ("hurt", "death"):
        cl = r.clip(nm, 0.3)
        cl.rot("bed", [(0, 0, 0, 0), (0.3, 0, 0, 0)])
    return r


# =====================================================================================================================
# Paint
# =====================================================================================================================

def paint_bird(r, P, v):
    gull = r.id == "city_gull"
    if gull:
        body = Ramp("#8f97a3", "#b9c0c9", "#d9dde2", "#eef0f2", "#fafbfc")
        mantle = Ramp("#5d6673", "#78828f", "#939daa", "#adb6c1", "#c4ccd5")
        tipc = Ramp("#14161a", "#22252b", "#33373f")
        bill, legc = hexc("#e9c342"), hexc("#d99a8a")
    else:
        body = {"slate": Ramp("#2f3440", "#454b59", "#5d6474", "#788092", "#9aa2b2", "#bcc3d0"),
                "white": Ramp("#8e8a86", "#b3afaa", "#cfcbc6", "#e4e1dc", "#f2f0ec", "#fbfaf8"),
                "brown": Ramp("#3a2a22", "#54392c", "#6f4c38", "#8d6448", "#a9805e", "#c6a07c")}[v]
        mantle = body
        tipc = {"slate": Ramp("#16181e", "#22252e", "#2e323c"), "white": Ramp("#9c9690", "#b8b2ac", "#cdc8c2"),
                "brown": Ramp("#241812", "#33221a", "#432d22")}[v]
        bill, legc = hexc("#3a3436"), hexc("#c45a5a")
    soft = combine(p_feathers(3, 3, 0.5), p_blotch(r.id + v, 5, 0.25))
    P.paint("torso", body, 3.0, soft)
    P.paint("breast", body, 3.4, p_feathers(3, 2, 0.4))
    P.paint("ohead", body, 3.4 if not gull else 4.0)
    P.paint("oneck", body, 3.2)
    if not gull and v != "white":
        # the neck's sheen: green over purple, catching the light in bands
        sheen = [hexc("#3f7d63"), hexc("#5e9a6e"), hexc("#7a5a9a"), hexc("#5a3f78")]
        for fname in SIDES:
            f = P.face("oneck", fname)
            for i, j in f.each():
                if j >= 1:
                    f.set(i, j, lerpc(sheen[(j + i // 2) % 4], body.at(3.0), 0.35 if v == "brown" else 0.15))
    P.paint("otail", mantle if gull else body, 2.6, p_bands(5, 0.6))
    if not gull:
        f = P.face("otail", "top")
        for i, j in f.each():
            if j >= f.h - 2:
                f.set(i, j, tipc.at(1.4))
    else:
        f = P.face("otail", "top")
        for i, j in f.each():
            f.set(i, j, body.at(4.0 if j < f.h - 1 else 2.0))
    # wings: coverts (with the pigeon's two dark bars), primaries darker toward the tips
    for cube, base in (("arm_l", 3.0), ("prim_l", 2.4)):
        P.paint(cube, mantle, base, p_feathers(3, 3, 0.7))
    for fname in ("top", "bottom"):
        f = P.face("arm_l", fname)
        for i, j in f.each():
            if not gull and v != "white" and j in (2, 4) and i >= 1:
                f.set(i, j, tipc.at(1.2))
            if fname == "bottom":
                f.set(i, j, mul(f.get(i, j), 1.12))
        f = P.face("prim_l", fname)
        for i, j in f.each():
            t = i / max(1, f.w - 1)
            if gull:
                col = tipc.at(1.5) if t > 0.55 else mantle.at(2.6 + 0.3 * (j % 2))
                if t > 0.55 and i == f.w - 2 and j == 1:
                    col = hexc("#f4f5f6")  # the mirror spot
            else:
                col = lerpc(mantle.at(2.6), tipc.at(1.5), max(0.0, (t - 0.35) / 0.65))
            if j == f.h - 1 or (i % 3 == 2 and j > 0):
                col = mul(col, 0.82)
            f.set(i, j, col)
    for fname in ("west", "north", "east", "south", "top", "bottom"):
        f = P.face("obeak", fname)
        for i, j in f.each():
            f.set(i, j, bill)
    if not gull:
        P.paint("cere", Ramp("#bdb8b4", "#e0dcd8", "#f4f1ee"), 1.6)
    else:
        P.paint("hook", Ramp("#8a2018", "#c8382a", "#e0503a"), 1.2)  # the red spot on the lower bill
    P.paint("oleg_l", Ramp(*["#%02x%02x%02x" % tuple(int(x * k) for x in legc[:3]) for k in (0.55, 0.75, 1.0)]), 1.5)
    for fname in ("top", "bottom"):
        f = P.face("foot_l", fname)
        for i, j in f.each():
            f.set(i, j, mul(legc, 0.8) if (i + j) % 2 == 0 else legc)
    with P.eyes():
        for fname in ("west", "east"):
            f = P.face("ohead", fname)
            ex = 0 if fname == "west" else f.w - 1
            f.set(ex, 1, hexc("#e26b2a") if not gull else hexc("#f0e2a0"))
            if gull:
                f.set(ex, 2, hexc("#1a1612"))
    return P.skin, P.glow


def p_bands(every, depth):
    def f(face, i, j):
        return -0.6 * depth if j % every == every - 1 else 0.0
    return f


CAT = {
    "ginger": dict(base=("#5a2c10", "#7e3f16", "#a5581f", "#c7752e", "#de9548", "#efb877"), stripe=-1.3, white=("belly", "socks")),
    "grey": dict(base=("#2c2d31", "#43454b", "#5d6068", "#7a7e87", "#989ca5", "#b9bdc5"), stripe=-1.4, white=("belly",)),
    "black": dict(base=("#0b0b0e", "#141419", "#1d1d24", "#282832", "#353543", "#454556"), stripe=0, white=("chest", "socks")),
    "calico": dict(base=("#3a2a20", "#5a3c26", "#86552e", "#b27a40", "#d9a765", "#ecd2a2"), stripe=0, white=("belly", "socks", "patch")),
}


def paint_cat(r, P, v):
    d = CAT[v]
    fur = Ramp(*d["base"])
    white = Ramp("#a59c92", "#c8c0b6", "#e2dbd2", "#f2ede6", "#fbf8f4")
    rng = R._h if False else None  # noqa: F841

    def tabby(face, i, j):
        if not d["stripe"]:
            return 0.0
        if face.name in SIDES and (i + j // 3) % 4 == 0:
            return d["stripe"]
        if face.name == "top" and j % 3 == 0:
            return d["stripe"] * 0.8
        return 0.0
    coat = combine(p_strokes(cell=3, length=2, dark=0.35, light=0.25, density=0.5, seed=7), tabby)
    for nm in ("torso", "chest", "oneck", "ohead", "otail1", "otail2", "ofleg_l", "othigh_l", "ohock_l", "oear_l", "tip_l"):
        P.paint(nm, fur, 3.0, coat)
    P.paint("otail3", fur if v != "calico" else white, 3.0 if v != "black" else 2.2, coat)
    P.paint("muzzle", white if v != "black" else fur, 3.2)
    P.paint("ojaw", white if v != "black" else fur, 2.8)
    P.paint("nose", Ramp("#5a2a2a", "#a65a5a", "#d48a86"), 1.4)
    if "belly" in d["white"] or "chest" in d["white"]:
        for nm in ("torso", "chest", "oneck"):
            f = P.face(nm, "bottom")
            for i, j in f.each():
                f.set(i, j, white.at(2.6))
        f = P.face("chest", "north")
        for i, j in f.each():
            f.set(i, j, white.at(3.0))
        f = P.face("oneck", "north")
        for i, j in f.each():
            f.set(i, j, white.at(2.8))
    if "socks" in d["white"]:
        for nm in ("ofleg_l", "ohock_l"):
            for fname in SIDES:
                f = P.face(nm, fname)
                for i, j in f.each():
                    if j >= f.h - 2:
                        f.set(i, j, white.at(3.0 - 0.3 * (fname == "east")))
    if "patch" in d["white"]:
        black = Ramp("#0e0e12", "#1b1b22", "#2a2a33")
        rr = __import__("random").Random(v)
        for nm in ("torso", "ohead", "othigh_l"):
            for fname in ("top", "west", "east"):
                f = P.face(nm, fname)
                cx, cy = rr.randrange(f.w), rr.randrange(f.h)
                for i, j in f.each():
                    dd = (i - cx) ** 2 + ((j - cy) * 1.4) ** 2
                    if dd < 6:
                        f.set(i, j, black.at(1.4))
                    elif dd < 14 and (i + j) % 2:
                        f.set(i, j, white.at(3.0))
    for fname in ("top", "bottom"):
        for nm in ("fpaw_l",):
            f = P.face(nm, fname)
            for i, j in f.each():
                f.set(i, j, white.at(2.4) if "socks" in d["white"] else fur.at(2.0))
    f = P.face("oear_l", "north")
    for i, j in f.each():
        f.set(i, j, hexc("#c98a86") if i % 2 == 0 and j > 0 else fur.at(2.2))
    for fname in ("north", "south"):
        f = P.face("whisk_l", fname)
        R.SIDES  # noqa: B018
        for i in range(f.w):
            for j in range(f.h):
                f.set(i, j, (0, 0, 0, 0))
            f.set(i, 0 if i % 2 == 0 else 1, hexc("#f4efe6", 210))
    # eyes: big and green-gold with a slit, and the night shine (the glow layer: a cat's eyes catch the lamps)
    iris = {"ginger": "#9ec23a", "grey": "#d7b23a", "black": "#e8c43a", "calico": "#7fb04a"}[v]
    with P.eyes():
        f = P.face("ohead", "north")
        g = P.face("ohead", "north", glow=True)
        for e0, pupil in ((0, 1), (4, 4)):
            for i in (e0, e0 + 1):
                f.set(i, 1, hexc(iris))
                f.set(i, 2, mul(hexc(iris), 0.8))
                g.set(i, 1, hexc("#c8ff9a", 120))
            f.set(pupil, 1, hexc("#141410"))
            f.set(pupil, 2, hexc("#141410"))
    return P.skin, P.glow


DOG = {
    "terrier": dict(base=("#2e2016", "#463020", "#62442c", "#7f5a3a", "#9d744c", "#bb9264"), white=False, kerchief=None, scruff=True),
    "collie": dict(base=("#0e0d0f", "#19181b", "#252328", "#323036", "#423f47", "#57535d"), white=True, kerchief="#b0302a", scruff=False),
    "hound": dict(base=("#5a3a16", "#7a5020", "#9e6c2e", "#bf8a42", "#d6a65c", "#e8c486"), white=True, kerchief="#2f5d8a", scruff=False),
}


def paint_dog(r, P, v):
    d = DOG[v]
    fur = Ramp(*d["base"])
    white = Ramp("#a8a094", "#c9c1b5", "#e1dbd0", "#f0ebe2", "#faf7f1")
    coat = p_fur(0.8 if d["scruff"] else 0.4, 3, seed=3, length=3) if d["scruff"] else p_strokes(cell=3, length=3, dark=0.35, light=0.3, density=0.5, seed=5)
    for nm in ("torso", "ruff", "oneck", "ohead", "snout", "otail1", "otail2", "ofleg_l", "othigh_l", "ohock_l", "oear_l", "oeartip_l"):
        P.paint(nm, fur, 3.0, coat)
    P.paint("ojaw", fur, 2.4)
    P.paint("nose", Ramp("#08080a", "#16161a", "#2a2a30"), 1.2)
    P.paint("otongue", Ramp("#9a3a44", "#c85a64", "#e48890"), 1.2)
    if d["white"]:
        # a white blaze, chest and socks, a white tail tip
        for nm, fname in (("ruff", "north"), ("ruff", "bottom"), ("oneck", "north"), ("torso", "bottom"), ("snout", "top"), ("snout", "north")):
            f = P.face(nm, fname)
            for i, j in f.each():
                if fname != "top" or abs(i - f.w // 2) <= 0:
                    f.set(i, j, white.at(3.0 - 0.4 * ((i + j) % 3 == 0)))
        f = P.face("ohead", "north")
        for i, j in f.each():
            if abs(i - (f.w - 1) / 2) < 1:
                f.set(i, j, white.at(3.2))
        for nm in ("ofleg_l", "ohock_l"):
            for fname in SIDES:
                f = P.face(nm, fname)
                for i, j in f.each():
                    if j >= f.h - 3:
                        f.set(i, j, white.at(3.0))
        for fname in SIDES + ("top", "bottom"):
            f = P.face("otail2", fname)
            for i, j in f.each():
                if fname in ("top", "bottom") or j >= 0:
                    if fname == "south" or (fname in SIDES and i >= f.w - 2) or fname in ("top", "bottom") and j >= f.h - 2:
                        f.set(i, j, white.at(3.2))
    if d["kerchief"]:
        k = hexc(d["kerchief"])
        for fname in ("top", "bottom") + SIDES:
            f = P.face("kerchief", fname)
            for i, j in f.each():
                f.set(i, j, mul(k, 1.12) if (i + j) % 4 == 0 else (k if fname != "bottom" else mul(k, 0.8)))
    else:
        for fname in ("top", "bottom") + SIDES:
            f = P.face("kerchief", fname)
            for i, j in f.each():
                f.set(i, j, (0, 0, 0, 0) if j < f.h - 1 or fname in ("top", "bottom") else hexc("#5a3a22"))  # a leather collar
    with P.eyes():
        f = P.face("ohead", "north")
        for ex in (1, 4):
            f.set(ex, 1, hexc("#2a1a0e"))
            f.set(ex, 0, mul(fur.at(3.4), 1.0))
    return P.skin, P.glow


HORSE = {
    "bay": dict(coat=("#2a140a", "#3d1d0e", "#562a14", "#70391c", "#8b4a26", "#a55d33"), mane=("#0c0907", "#17110c", "#231a12"), blaze=True),
    "grey": dict(coat=("#5a5c60", "#77797e", "#95979c", "#b2b4b8", "#cbcdd0", "#e1e2e4"), mane=("#4a4a4e", "#66666b", "#86868b"), blaze=False),
    "chestnut": dict(coat=("#3e1a08", "#5a260c", "#7a3612", "#99481a", "#b65c26", "#cf7636"), mane=("#5e2e10", "#7c4018", "#a05a24"), blaze=True),
    "black": dict(coat=("#0b0a0c", "#151317", "#1f1c22", "#2a262e", "#38333d", "#48424e"), mane=("#060507", "#0e0c10", "#18151b"), blaze=False),
}


def paint_horse(r, P, v):
    d = HORSE[v]
    coat = Ramp(*d["coat"])
    mane = Ramp(*d["mane"])
    feather = Ramp("#9e968a", "#c3bbae", "#ddd6ca", "#eee8de", "#f8f4ee")
    leather = Ramp("#1c120c", "#2c1c12", "#3e281a", "#523624", "#684630")
    brass = Ramp("#5a3e12", "#8a6420", "#b88a2e", "#ddb24a", "#f4d777")
    sleek = combine(p_strokes(cell=4, length=4, dark=0.3, light=0.3, density=0.45, seed=11), p_blotch("horse" + v, 7, 0.35))
    dapple = None
    if v == "grey":
        rr = __import__("random").Random(5)

        def dapple(face, i, j):
            return 0.6 if (i * 7 + j * 13 + rr.random() * 3) % 5 < 1.2 else -0.15
    for nm in ("barrel", "rump", "chestb", "oneck", "skull", "muzzleh", "ojaw", "ofleg_l", "ofknee_l", "othigh_l", "ohhock_l", "oear_l", "dock"):
        P.paint(nm, coat, 3.0, combine(sleek, dapple) if dapple else sleek)
    P.paint("muzzleh", coat, 2.6, sleek)
    f = P.face("muzzleh", "north")
    for i, j in f.each():
        f.set(i, j, mul(coat.at(1.6), 1.0) if j >= f.h - 2 else coat.at(2.6))
    if d["blaze"]:
        for nm, fname in (("skull", "north"), ("skull", "top"), ("muzzleh", "top"), ("muzzleh", "north")):
            f = P.face(nm, fname)
            for i, j in f.each():
                if abs(i - (f.w - 1) / 2) < 1.1 and not (nm == "muzzleh" and fname == "north" and j >= f.h - 2):
                    f.set(i, j, feather.at(3.4))
    P.paint("mane", mane, 2.0, p_bands(3, 1.0))
    for nm in ("otail2", "otail3"):
        P.paint(nm, mane, 2.2, p_strokes(cell=2, length=4, dark=0.6, light=0.4, density=0.7, seed=4))
    # the mane's braids: a ribbon knot every few pixels down the crest
    ribbon = hexc({"bay": "#c23a2e", "grey": "#2f5d8a", "chestnut": "#e0b44a", "black": "#c23a2e"}[v])
    f = P.face("mane", "top")
    for fname in ("west", "east", "south"):
        f = P.face("mane", fname)
        for i, j in f.each():
            if j % 4 == 1:
                f.set(i, j, ribbon)
    for nm in ("ffeath_l", "hfeath_l"):
        P.paint(nm, feather, 3.0, p_strokes(cell=2, length=3, dark=0.5, light=0.4, density=0.6, seed=2))
    for nm in ("fhoof_l", "hhoof_l"):
        P.paint(nm, Ramp("#2a241e", "#3e362c", "#544a3c"), 1.4)
    for nm in ("girth", "bridle", "otrace_l", "blinker_l"):
        P.paint(nm, leather, 2.4, p_bands(4, 0.4))
    P.paint("collar", leather, 2.6, lambda f, i, j: 0.8 if j == 0 else (-0.6 if j == f.h - 1 else 0))
    for fname in ("top", "bottom") + SIDES:
        f = P.face("collar", fname)
        for i, j in f.each():
            if (i % 3 == 1) and j == 1:
                f.set(i, j, brass.at(3.4))  # studs
    P.paint("bell0", brass, 3.0, lambda f, i, j: 0.8 if f.name == "top" else 0)
    f = P.face("bridle", "north")
    for i, j in f.each():
        if i in (0, f.w - 1):
            f.set(i, j, brass.at(3.6))
    with P.eyes():
        for fname, ex in (("west", 4), ("east", 2)):
            f = P.face("skull", fname)
            f.set(ex, 1, hexc("#120a06"))
            f.set(ex, 2, hexc("#2a160c"))
    return P.skin, P.glow


CART = {"hay": "load_hay", "crates": "load_crates", "casks": "load_casks"}


def paint_cart(r, P, v):
    wood = Ramp("#2a1c12", "#3d2a1a", "#523824", "#6a4930", "#7f5b3c", "#967150")
    iron = Ramp("#1a1a1e", "#2a2a30", "#3c3c44", "#52525c")
    plank = lambda f, i, j: (-0.8 if (j if f.name in ("top", "bottom") else i) % 4 == 3 else 0) + (0.3 if (i * 3 + j) % 11 == 0 else 0)  # noqa: E731
    for nm in ("floor", "side_l", "front", "tailb", "seat"):
        P.paint(nm, wood, 2.8, plank)
    for nm in ("axle", "shaft_l", "spokev_l", "spokeh_l"):
        P.paint(nm, wood, 2.2)
    for nm in ("hub_l", "hub_r"):
        if nm in P.cubes:
            P.paint(nm, iron, 2.0)
    for nm in ("rimv_l", "rimh_l", "rimd_l", "rime_l"):
        P.paint(nm, wood, 2.4, lambda f, i, j: -1.2 if f.name in ("north", "south", "top", "bottom") and (j == 0 or j == f.h - 1) else 0)
        for fname in ("top", "bottom", "north", "south"):
            f = P.face(nm, fname)
            for i, j in f.each():
                if f.name in ("top", "bottom"):
                    f.set(i, j, iron.at(2.0))
    hay = Ramp("#7a5a1a", "#9e7a26", "#c29c38", "#d9b850", "#ead070")
    P.paint("hay1", hay, 2.6, p_strokes(cell=2, length=4, dark=0.7, light=0.5, density=0.8, seed=1))
    P.paint("hay2", hay, 3.0, p_strokes(cell=2, length=4, dark=0.7, light=0.5, density=0.8, seed=2))
    crate = Ramp("#4a3220", "#644530", "#7e5a3e", "#97704e", "#b08a62")
    P.paint("crate1", crate, 2.6, lambda f, i, j: -1.0 if i in (0, f.w - 1) or j in (0, f.h - 1) else (-0.5 if i == j or i == f.w - 1 - j else 0))
    P.paint("sack1", Ramp("#6a5a40", "#8a7856", "#a6946e", "#c0ae86"), 2.4, p_strokes(cell=2, length=2, dark=0.4, light=0.3, density=0.5))
    P.paint("cask0", wood, 3.0, lambda f, i, j: (-1.2 if f.name in SIDES and j in (1, f.h - 2) else 0) + (-0.4 if f.name in SIDES and i % 2 else 0))
    for fname in SIDES:
        f = P.face("cask0", fname)
        for i, j in f.each():
            if j in (1, f.h - 2):
                f.set(i, j, iron.at(2.0))
    cloak = Ramp("#1c2230", "#283044", "#364058", "#46526c", "#58647e")
    for nm in ("cloak", "lap", "shins", "hood", "odarm_l"):
        P.paint(nm, cloak, 2.8, p_strokes(cell=3, length=4, dark=0.4, light=0.3, density=0.5, seed=9))
    skin = Ramp("#5a3a2a", "#7e5440", "#a07058", "#bd8c70")
    P.paint("face", skin, 2.4)
    f = P.face("face", "north")
    for i, j in f.each():
        if j == 0:
            f.set(i, j, cloak.at(1.2))  # the hood's shadow over the brow
        if j == 1 and i in (0, f.w - 1):
            f.set(i, j, hexc("#1a120c"))
    f = P.face("hood", "north")
    for i, j in f.each():
        if 1 <= i <= 4 and 1 <= j <= 4:
            f.set(i, j, cloak.at(0.6))
    return P.skin, P.glow


# =====================================================================================================================

VARIANTS = {"city_pigeon": ["slate", "slate", "white", "brown"], "city_gull": ["gull"], "city_cat": list(CAT),
            "city_dog": list(DOG), "city_horse": list(HORSE), "city_cart": ["cart"]}
DESIGNS = {"city_pigeon": (pigeon, paint_bird), "city_gull": (gull, paint_bird), "city_cat": (cat, paint_cat),
           "city_dog": (dog, paint_dog), "city_horse": (horse, paint_horse), "city_cart": (cart, paint_cart)}


def rigs(only=None):
    out = []
    for rid, (design, painter) in DESIGNS.items():
        if only and rid not in only:
            continue
        out.append((rid, design(), painter))
    return out


def textures(rig, painter):
    """[(texture name, skin, blink skin or None)] for every variant (the first is the plain id)."""
    out, seen = [], set()
    for v in VARIANTS[rig.id]:
        if v in seen:
            continue
        seen.add(v)
        P = R.Painter(rig, "city_fauna|" + rig.id + "|" + v)
        skin, glow = painter(rig, P, v)
        blink = P.blink()
        name = rig.id if len(set(VARIANTS[rig.id])) == 1 else rig.id + "_" + v
        out.append((name, skin, glow, blink))
    return out


def java(all_rigs):
    chunks = []
    for rid, rig, _ in all_rigs:
        for c in rig.clips.values():
            for (bone, _), keys in c.tracks.items():
                if bone not in rig.by_name:
                    raise SystemExit(f"{rid}/{c.name}: no bone {bone}")
        chunks.append((rid, rig.java_lines()))
    src = ["package dev.awaken.citizens.client;", "", "import java.util.List;", "", "/**",
           " * GENERATED by tools/art/specs/city_fauna.py: do not edit by hand. Carillon's animals as plain data, in the hero",
           " * rigs' format ({@link dev.awaken.mobs.client.anim.HeroRig#parse}); textures in textures/entity/city/.",
           " */", "public final class CityFaunaData {", "    private CityFaunaData() {}", "",
           "    /** Each animal's variants (texture suffixes, weighted by repetition). */",
           "    public static final java.util.Map<String, List<String>> VARIANTS = java.util.Map.of("]
    vs = [f'            "{rid}", List.of({", ".join(chr(34) + (v if len(set(VARIANTS[rid])) > 1 else "") + chr(34) for v in VARIANTS[rid])})'
          for rid, _, _ in all_rigs]
    src.append(",\n".join(vs))
    src.append("    );")
    src.append("")
    src.append("    /** Render scale per animal (designed larger than it stands). */")
    src.append("    static final java.util.Map<String, Float> SCALE = java.util.Map.of(")
    src.append(",\n".join(f'            "{rid}", {SCALE[rid]}F' for rid, _, _ in all_rigs))
    src.append("    );")
    src.append("")
    src.append("    /** Every rig's source text, in order. */")
    src.append("    public static final List<String[]> SOURCES = List.<String[]>of(")
    for k, (rid, lines) in enumerate(chunks):
        src.append(f"            // {rid}")
        src.append("            new String[] {")
        src.append('                """')
        for ln in lines:
            src.append("                " + ln)
        src.append('                """')
        src.append("            }" + ("," if k < len(chunks) - 1 else ""))
    src.append("    );")
    src.append("}")
    with open(JAVA_OUT, "w") as f:
        f.write("\n".join(src) + "\n")
    return JAVA_OUT


def build(out):
    all_rigs = rigs()
    n = 0
    for rid, rig, painter in all_rigs:
        for name, skin, glow, blink in textures(rig, painter):
            out.entity("city/" + name, skin)
            if any(px[3] for row in glow.px for px in row):
                out.entity("city/" + name + "_glow", glow)
            if blink is not None:
                out.entity("city/" + name + "_blink", blink[0])
            n += 1
    path = java(all_rigs)
    print(f"city_fauna: {len(all_rigs)} rigs, {n} textures -> {os.path.relpath(path, ROOT)}")


# =====================================================================================================================
# Preview

BG_DAY = (110, 128, 140, 255)


def preview(outdir, only=None):
    from PIL import Image, ImageDraw
    os.makedirs(outdir, exist_ok=True)
    made = []
    for rid, rig, painter in rigs(only):
        texs = textures(rig, painter)
        cell = (280, 240)
        rest = rig.tree(rig.sample([("idle", 0.4, 1.0)]))
        rows = []
        for name, skin, glow, _ in texs:
            lit = Image.alpha_composite(skin.image(), glow.image())
            views = [R.render(rest, lit, yaw=35, pitch=18, size=cell), R.render(rest, lit, yaw=100, pitch=8, size=cell),
                     R.render(rest, lit, yaw=-145, pitch=30, size=cell), R.render(rest, lit, yaw=0, pitch=5, size=cell)]
            row = Image.new("RGBA", (cell[0] * 4, cell[1]), BG_DAY)
            for i, im in enumerate(views):
                row.alpha_composite(im, (i * cell[0], 0))
            ImageDraw.Draw(row).text((4, 4), name, fill=(20, 24, 30, 255))
            rows.append(row)
        lit = Image.alpha_composite(texs[0][1].image(), texs[0][2].image())
        poses = [(nm, [(nm, cl.length * 0.3, 1.0)]) for nm, cl in rig.clips.items() if nm not in ("hurt", "death")]
        trees = [rig.tree(rig.sample(layers)) for _, layers in poses]
        yaw, pitch = 55, 12
        b = R.bounds_of(trees, yaw, pitch)
        pc = (200, 180)
        px = min((pc[0] - 16) / max(1e-6, b[1] - b[0]), (pc[1] - 24) / max(1e-6, b[3] - b[2]))
        strip = Image.new("RGBA", (max(pc[0] * len(poses), cell[0] * 4), pc[1] + 16), (40, 44, 54, 255))
        d3 = ImageDraw.Draw(strip)
        for k, ((label, _), t) in enumerate(zip(poses, trees)):
            im = R.render(t, lit, yaw=yaw, pitch=pitch, px=px, bounds=b,
                          origin=(pc[0], pc[1], (pc[0] - (b[1] - b[0]) * px) / 2 - b[0] * px, pc[1] - 8 - b[3] * px))
            bg = Image.new("RGBA", pc, BG_DAY)
            bg.alpha_composite(im)
            strip.paste(bg, (k * pc[0], 0))
            d3.text((k * pc[0] + 4, pc[1] + 2), label, fill=(220, 225, 235, 255))
        # size line-up against a player, at the in-game scale
        unit = 3.0
        t = rig.tree(rig.sample([("idle", 0.4, 1.0)]))
        sc = SCALE[rid]
        t["scale"] = (sc, sc, sc)
        t["pivot"] = (0, 24 - 24 * sc, 0)
        a = R.render(t, lit, yaw=60, pitch=8, px=unit)
        pl = R.render(R.player_tree(), R.steve(), yaw=60, pitch=8, px=unit)
        lw = pl.width + a.width + 80
        lh = max(pl.height, a.height) + 20
        line = Image.new("RGBA", (max(lw, cell[0] * 4), lh), (150, 170, 160, 255))
        line.alpha_composite(pl, (20, lh - 10 - pl.height))
        line.alpha_composite(a, (40 + pl.width, lh - 10 - a.height))
        Wt = max(r_.width for r_ in rows + [strip, line])
        H = sum(r_.height for r_ in rows) + strip.height + line.height
        sheet = Image.new("RGBA", (Wt, H), (24, 26, 34, 255))
        y = 0
        for r_ in rows + [line, strip]:
            sheet.paste(r_, (0, y))
            y += r_.height
        path = os.path.join(outdir, f"{rid}.png")
        sheet.save(path)
        made.append(path)
    return made


if __name__ == "__main__":
    if len(sys.argv) > 2 and sys.argv[1] == "preview":
        for p in preview(sys.argv[2], sys.argv[3:] or None):
            print(p)
