"""The wild's small life (SPEC "Wild life"): songbirds, woodpeckers, crows, owls, buzzards and kestrels, gulls, ducks,
herons, squirrels, rabbits, foxes and frogs as client-only bodies (wildlife.client), and the insects' sprite sheet
(butterflies, moths, bees, dragonflies, fireflies, gnats, water striders, far flocks, ripples). The server pays nothing.

    python3 tools/art/build_all.py wild_fauna         textures (textures/entity/wild/<id>[_<variant>].png, _blink, _glow,
                                                      insects.png) and src/main/java/dev/awaken/wildlife/client/WildFaunaData.java
    python3 tools/art/specs/wild_fauna.py preview DIR [ids]
                                                      per animal: views of every variant, a size line-up with a player,
                                                      the strip of its clips; insects.png enlarged

Same conventions as city_fauna.py (it lends the gull, the bird helpers and the preview): hero rigs (fauna_rig.py), model
pixels, y down, ground at y = 24, facing -z, rotations in degrees, clip pos y up. Small animals are designed large and
drawn small (SCALE): at the world's density a robin would be two pixels long.
Clip names are the brains' vocabulary (wildlife.client.Birds, Critters): idle, hop, peck, fly, bound, glide, flare,
sing, preen, fluff, cling, drum, hitch, walk, caw, hoot, soar, hover, stoop, call, swim, dabble, quack, stand, stalk,
strike, alert, run, climb, descend, chatter, forage, graze, groom, trot, sit, sniff, pounce, lie, croak.
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
from fauna_rig import Rig, Ramp, hexc, lerpc, mul, p_fur, p_feathers, p_blotch, combine, SIDES  # noqa: E402,F401
from fauna_anim import cycle, s, c  # noqa: E402
import city_fauna as CF  # noqa: E402
from pixelart import Canvas  # noqa: E402

JAVA_OUT = os.path.join(ROOT, "src/main/java/dev/awaken/wildlife/client/WildFaunaData.java")

SCALE = {"wild_songbird": 0.42, "wild_woodpecker": 0.5, "wild_crow": 0.62, "wild_owl": 0.62, "wild_raptor": 0.8,
         "wild_gull": 0.75, "wild_duck": 0.6, "wild_heron": 0.9, "wild_squirrel": 0.5, "wild_rabbit": 0.55,
         "wild_fox": 0.78, "wild_frog": 0.36}


def build_hook():
    pass


def sym(fn):
    for side, nm in ((1, "l"), (-1, "r")):
        fn(side, nm, side < 0, (lambda n: None) if side > 0 else (lambda n: n))


def stubs(r):
    for nm in ("hurt", "death"):
        cl = r.clip(nm, 0.3)
        cl.rot("body", [(0, 0, 0, 0), (0.3, 0, 0, 0)])


def clip_of(r, name, length, loop=False):
    """r.clip, carrying the rig's wing lift for the wing helpers."""
    cl = r.clip(name, length, loop)
    cl.lift = getattr(r, "wing_lift", 0)
    return cl


def hold(clip, bone, rot=None, pos=None, scale=None):
    """A pose held through the whole clip."""
    L = clip.length
    if rot is not None:
        clip.rot(bone, [(0,) + tuple(rot), (L,) + tuple(rot)], "lin")
    if pos is not None:
        clip.pos(bone, [(0,) + tuple(pos), (L,) + tuple(pos)], "lin")
    if scale is not None:
        clip.scale(bone, [(0,) + tuple(scale), (L,) + tuple(scale)], "lin")


# =====================================================================================================================
# Birds: one body plan with sizes (wings modelled spread and folded by their rest rotation, as city_fauna's)
# =====================================================================================================================

def bird_body(rid, tex, *, w, h, length, breast=1.5, neck_h=2, neck_w=3, head=(4, 4, 4), wing_arm=4, wing_hand=5,
              chord=(4, 3), tail=(3, 5), legs_h=2, upright=0.0, leg2=0):
    """A bird. upright: the body's rest pitch (degrees nose up; the neck and the legs take it back so the head and the
    feet stay level). leg2: a shin under the leg (herons). Returns the rig (bones: body, neck, head, beak, tail,
    leg_l/r [shin_l/r], wing_l/r, hand_l/r)."""
    r = Rig(rid, tex)
    r.walk_stride, r.run_stride, r.run_speed, r.shadow = 0.5, 1.0, (0.08, 0.16), 0.2
    legs_total = legs_h + leg2
    by = 24 - legs_total - h / 2
    body = r.bone("body", (0, by, 0), (-upright, 0, 0))
    half = length / 2
    body.cube("torso", -w / 2, -h / 2, -half, w, h, length)
    if breast:
        body.cube("breast", -w / 2 + 0.5, -h / 2 + 1, -half - breast, w - 1, h - 1, breast)
    neck = body.bone("neck", (0, -h / 2 + 1, -half - breast * 0.5), (-10 + upright, 0, 0))
    neck.cube("oneck", -neck_w / 2, -neck_h, -neck_w / 2, neck_w, neck_h + 1, neck_w)
    hw, hh, hd = head
    hd_ = neck.bone("head", (0, -neck_h, 0), (10, 0, 0))
    hd_.cube("ohead", -hw / 2, -hh + 0.5, -hd / 2 - 0.5, hw, hh, hd)
    beak = hd_.bone("beak", (0, -hh / 2 + 1, -hd / 2 - 0.5))
    tw, tl = tail
    tl_ = body.bone("tail", (0, -h / 2 + 1, half), (-8 + upright * 0.6, 0, 0))
    tl_.cube("otail", -tw / 2, -0.5, 0, tw, 1, tl)
    ca, ch = chord

    def legs(side, nm, mir, sh):
        lg = body.bone("leg_" + nm, (side * min(1.3, w / 4), h / 2 - 0.5, 0.5), (upright, 0, 0))
        lg.cube("oleg_" + nm, -0.5, 0, -0.5, 1, legs_h, 1, mirror=mir, share=sh("oleg_l"))
        if leg2:
            sn = lg.bone("shin_" + nm, (0, legs_h, 0))
            sn.cube("oshin_" + nm, -0.5, 0, -0.5, 1, leg2, 1, mirror=mir, share=sh("oshin_l"))
            sn.cube("foot_" + nm, -1.5, leg2, -3, 3, 0, 4, mirror=mir, share=sh("foot_l"))
        else:
            lg.cube("foot_" + nm, -1, legs_h, -2, 2, 0, 3, mirror=mir, share=sh("foot_l"))
        arm = body.bone("wing_" + nm, (side * w / 2, -h / 2 + 0.5 + ca, -half + 1), (FOLD, -side * 90, 0))
        if side > 0:
            arm.cube("arm_" + nm, 0, 0, 0, wing_arm, 1, ca)
        else:
            arm.cube("arm_" + nm, -wing_arm, 0, 0, wing_arm, 1, ca, mirror=True, share="arm_l")
        hand = arm.bone("hand_" + nm, (side * wing_arm, 0, 0))
        if side > 0:
            hand.cube("prim_" + nm, 0, 0, 0, wing_hand, 1, ch)
        else:
            hand.cube("prim_" + nm, -wing_hand, 0, 0, wing_hand, 1, ch, mirror=True, share="prim_l")
    sym(legs)
    r.look = [("neck", 0.35, 0.3), ("head", 0.65, 0.7)]
    r.wing_lift = ca
    return r, beak


# Folded wings lie on the flanks with their upper side out (the rest rotation's x is FOLD), the leading edge low and
# the chord rising to the back's line; opening them takes the fold back out and lifts the root to the back by the arm's
# chord (clip.lift), so a spread wing hangs from the shoulder.
FOLD = 90


def lift_hold(clip, t0=0.0, t1=None):
    t1 = clip.length if t1 is None else t1
    for nm in ("wing_l", "wing_r"):
        clip.pos(nm, [(t0, 0, clip.lift, 0), (t1, 0, clip.lift, 0)], "lin")


def flap(clip, up, down, tip_lag=0.12, n=8):
    """A flap cycle with the wings open: the arm rows from up to down, the hand follows a little later."""
    for nm, sg in (("wing_l", 1), ("wing_r", -1)):
        cycle(clip, nm, "rot", lambda p, sg=sg: (-FOLD, sg * 90, -sg * ((up - down) / 2 * c(p) + (up + down) / 2)), n)
        cycle(clip, "hand_" + nm[-1], "rot", lambda p, sg=sg: (0, sg * 6 * s(p), -sg * 22 * c(p, -tip_lag)), n)
    lift_hold(clip)


def tuck_legs(clip, angle=70):
    hold(clip, "leg_l", rot=(angle, 0, 0))
    hold(clip, "leg_r", rot=(angle, 0, 0))


def glide_wings(clip, dihedral=6, sweep=-8, droop=4, rock=3):
    for nm, sg in (("wing_l", 1), ("wing_r", -1)):
        cycle(clip, nm, "rot", lambda p, sg=sg: (-FOLD, sg * 90, -sg * (dihedral + rock * s(p))), 8)
        clip.rot("hand_" + nm[-1], [(0, 0, sg * sweep, sg * droop), (clip.length, 0, sg * sweep, sg * droop)])
    lift_hold(clip)


def flare_clip(r, length=0.5):
    fl = clip_of(r, "flare", length)
    for nm, sg in (("wing_l", 1), ("wing_r", -1)):
        fl.rot(nm, [(0, -FOLD, sg * 90, -sg * 10), (length * 0.5, -FOLD, sg * 60, sg * 40), (length, -FOLD, sg * 70, sg * 10)])
    lift_hold(fl)
    fl.rot("body", [(0, 0, 0, 0), (length * 0.5, -28, 0, 0), (length, -10, 0, 0)])
    fl.rot("leg_l", [(0, 60, 0, 0), (length * 0.6, -15, 0, 0), (length, 0, 0, 0)])
    fl.rot("leg_r", [(0, 60, 0, 0), (length * 0.6, -15, 0, 0), (length, 0, 0, 0)])
    fl.rot("tail", [(0, 0, 0, 0), (length * 0.5, -25, 0, 0), (length, 0, 0, 0)])
    return fl


def fly_clip(r, name, length, up, down, body_bob=0.6, neck=15):
    fly = clip_of(r, name, length, loop=True)
    flap(fly, up=up, down=down)
    cycle(fly, "body", "pos", lambda p: (0, body_bob * s(p), 0), 8)
    tuck_legs(fly)
    hold(fly, "neck", rot=(neck, 0, 0))
    hold(fly, "tail", rot=(6, 0, 0))
    return fly


# ---- songbirds -------------------------------------------------------------------------------------------------------

def songbird():
    r, beak = bird_body("wild_songbird", (64, 32), w=5, h=5, length=6, breast=1, neck_h=1, neck_w=3, head=(4, 4, 4),
                        wing_arm=4, wing_hand=4, chord=(4, 3), tail=(3, 5), legs_h=2)
    beak.cube("obeak", -0.5, -0.5, -1.5, 1, 1, 2)
    r.pack()
    idle = clip_of(r, "idle", 3.0, loop=True)
    # never still: head darts, a bob, the tail and the wings flicked
    idle.rot("head", [(0, 0, 0, 0), (0.25, 0, 32, 0), (0.7, 0, 32, 0), (0.85, 0, -26, 6), (1.4, 0, -26, 6), (1.55, -12, 0, 0),
                      (2.1, -12, 0, 0), (2.25, 0, 0, 0), (3.0, 0, 0, 0)])
    idle.rot("body", [(0, 0, 0, 0), (1.9, 0, 0, 0), (2.0, 14, 0, 0), (2.12, 0, 0, 0), (3.0, 0, 0, 0)])
    idle.rot("tail", [(0, 0, 0, 0), (0.9, 0, 0, 0), (0.98, -28, 0, 0), (1.15, 0, 0, 0), (2.0, 0, 0, 0), (2.07, -22, 0, 0),
                      (2.2, 0, 0, 0), (3.0, 0, 0, 0)])
    for nm, sg in (("wing_l", 1), ("wing_r", -1)):
        idle.rot(nm, [(0, 0, 0, 0), (2.0, 0, 0, 0), (2.06, 0, sg * 14, 0), (2.18, 0, 0, 0), (3.0, 0, 0, 0)])
    hop = clip_of(r, "hop", 0.32)
    # two-footed: crouch, spring, tuck, land
    hop.pos("body", [(0, 0, 0, 0), (0.06, 0, -0.8, 0), (0.16, 0, 1.6, -0.6), (0.27, 0, 0.2, 0), (0.32, 0, 0, 0)])
    hop.rot("body", [(0, 0, 0, 0), (0.06, 10, 0, 0), (0.16, -8, 0, 0), (0.27, 4, 0, 0), (0.32, 0, 0, 0)])
    for nm in ("leg_l", "leg_r"):
        hop.rot(nm, [(0, 0, 0, 0), (0.06, -20, 0, 0), (0.12, 30, 0, 0), (0.24, 25, 0, 0), (0.32, 0, 0, 0)])
    hop.rot("tail", [(0, 0, 0, 0), (0.16, -18, 0, 0), (0.32, 0, 0, 0)])
    peck = clip_of(r, "peck", 1.1, loop=True)
    peck.rot("body", [(0, 0, 0, 0), (0.15, 26, 0, 0), (0.8, 26, 0, 0), (0.95, 0, 0, 0), (1.1, 0, 0, 0)])
    peck.rot("neck", [(0, 0, 0, 0), (0.15, 35, 0, 0), (0.25, 55, 0, 0), (0.33, 35, 0, 0), (0.45, 58, 0, 0), (0.53, 35, 0, 0),
                      (0.8, 35, 0, 0), (0.95, 0, 0, 0), (1.1, 0, 0, 0)])
    peck.rot("tail", [(0, 0, 0, 0), (0.15, -22, 0, 0), (0.8, -22, 0, 0), (1.1, 0, 0, 0)])
    fly_clip(r, "fly", 0.16, up=75, down=-50, body_bob=0.4)
    bound = clip_of(r, "bound", 0.48, loop=True)
    # the songbird's bounding flight: three quick beats, then the wings shut and it falls a little
    for nm, sg in (("wing_l", 1), ("wing_r", -1)):
        keys = []
        for k in range(4):
            t = k * 0.08
            keys += [(t, -FOLD, sg * 90, -sg * -45), (t + 0.04, -FOLD, sg * 90, -sg * 70)]
        keys += [(0.32, -FOLD, sg * 90, -sg * -30), (0.36, 0, 0, 0), (0.46, 0, 0, 0), (0.48, -FOLD, sg * 90, -sg * -45)]
        bound.rot(nm, keys, "lin")
        L = bound.lift
        bound.pos(nm, [(0, 0, L, 0), (0.32, 0, L, 0), (0.36, 0, 0, 0), (0.46, 0, 0, 0), (0.48, 0, L, 0)], "lin")
    bound.pos("body", [(0, 0, 0, 0), (0.3, 0, 0.8, 0), (0.48, 0, 0, 0)])
    tuck_legs(bound, 75)
    hold(bound, "neck", rot=(12, 0, 0))
    glide = clip_of(r, "glide", 1.0, loop=True)
    glide_wings(glide, dihedral=4, sweep=-6, droop=2, rock=2)
    tuck_legs(glide)
    flare_clip(r, 0.35)
    sing = clip_of(r, "sing", 1.6, loop=True)
    # head up and back, the bill opening and shutting fast, the throat swelling, the tail down
    hold(sing, "neck", rot=(-22, 0, 0))
    hold(sing, "head", rot=(-14, 0, 0))
    sing.scale("neck", [(0, 1, 1, 1), (0.2, 1.25, 1.15, 1.25), (1.3, 1.25, 1.15, 1.25), (1.6, 1, 1, 1)])
    keys = [(0, 0, 0, 0)]
    t = 0.12
    while t < 1.4:
        keys += [(t, 30, 0, 0), (t + 0.06, 6, 0, 0)]
        t += 0.12
    keys.append((1.6, 0, 0, 0))
    sing.rot("beak", keys, "lin")
    hold(sing, "tail", rot=(18, 0, 0))
    cycle(sing, "body", "scale", lambda p: (1 + 0.04 * s(p * 6), 1 + 0.05 * s(p * 6), 1), 16)
    preen = clip_of(r, "preen", 2.0, loop=True)
    # the head round into the wing, nibbling along the feathers, a shake to finish
    preen.rot("neck", [(0, 0, 0, 0), (0.25, 20, 110, 0), (1.4, 20, 110, 0), (1.6, 0, 0, 0), (2.0, 0, 0, 0)])
    preen.rot("head", [(0, 0, 0, 0), (0.25, 30, 40, 0), (0.45, 45, 40, 0), (0.65, 30, 40, 0), (0.85, 45, 50, 0),
                       (1.05, 30, 40, 0), (1.25, 45, 40, 0), (1.4, 30, 40, 0), (1.6, 0, 0, 0), (2.0, 0, 0, 0)])
    preen.rot("wing_l", [(0, 0, 0, 0), (0.25, 0, 12, -10), (1.4, 0, 12, -10), (1.6, 0, 0, 0), (2.0, 0, 0, 0)])
    preen.scale("body", [(0, 1, 1, 1), (1.6, 1, 1, 1), (1.7, 1.18, 1.14, 1.05), (1.85, 1, 1, 1), (2.0, 1, 1, 1)])
    fluff = clip_of(r, "fluff", 2.5, loop=True)
    # puffed up against the rain or the cold: round, head sunk, now and then a shiver
    hold(fluff, "body", scale=(1.16, 1.14, 1.06))
    hold(fluff, "neck", rot=(10, 0, 0), pos=(0, -1.2, 0.5))
    fluff.rot("body", [(0, 0, 0, 0), (1.8, 0, 0, 0), (1.85, 0, 0, 6), (1.9, 0, 0, -6), (1.95, 0, 0, 0), (2.5, 0, 0, 0)])
    stubs(r)
    return r


# ---- woodpecker -------------------------------------------------------------------------------------------------------

def woodpecker():
    r, beak = bird_body("wild_woodpecker", (64, 32), w=4, h=5, length=7, breast=1, neck_h=2, neck_w=3, head=(4, 4, 5),
                        wing_arm=4, wing_hand=5, chord=(4, 3), tail=(3, 5), legs_h=2)
    beak.cube("obeak", -0.5, -0.5, -3, 1, 1, 3)
    r.pack()
    # on a trunk: the body upright against the bark (the entity faces the trunk), the stiff tail braced, the head level
    cling_pose = {"body": (-72, 0, 0), "neck": (50, 0, 0), "head": (20, 0, 0), "tail": (22, 0, 0),
                  "leg_l": (40, 0, 0), "leg_r": (40, 0, 0)}

    def clinging(cl, skip=()):
        for b, rot in cling_pose.items():
            if b not in skip:
                hold(cl, b, rot=rot)

    idle = clip_of(r, "idle", 3.0, loop=True)
    # perched like any bird (a branch, the line-up): glances, a tail flick
    idle.rot("head", [(0, 0, 0, 0), (0.4, 0, 30, 0), (1.1, 0, 30, 0), (1.3, 0, -28, 0), (2.1, 0, -28, 0), (2.3, 0, 0, 0), (3.0, 0, 0, 0)])
    idle.rot("tail", [(0, 0, 0, 0), (2.5, 0, 0, 0), (2.6, -14, 0, 0), (2.8, 0, 0, 0), (3.0, 0, 0, 0)])
    cling = clip_of(r, "cling", 3.0, loop=True)
    clinging(cling, ("head",))
    cling.rot("head", [(0, 20, 0, 0), (0.4, 20, 35, 0), (1.0, 20, 35, 0), (1.2, 20, -30, 0), (1.9, 20, -30, 0), (2.1, 20, 0, 0),
                       (3.0, 20, 0, 0)])
    peck = clip_of(r, "peck", 0.9, loop=True)
    clinging(peck, ("neck", "head"))
    peck.rot("neck", [(0, 50, 0, 0), (0.1, 30, 0, 0), (0.16, 64, 0, 0), (0.3, 30, 0, 0), (0.36, 64, 0, 0), (0.6, 40, 0, 0),
                      (0.66, 64, 0, 0), (0.9, 50, 0, 0)])
    hold(peck, "head", rot=(20, 0, 0))
    drum = clip_of(r, "drum", 1.0, loop=True)
    clinging(drum, ("neck", "head"))
    keys = [(0, 50, 0, 0)]
    for k in range(16):
        t = 0.05 + k * 0.055
        keys += [(t, 34, 0, 0), (t + 0.025, 64, 0, 0)]
    keys.append((1.0, 50, 0, 0))
    drum.rot("neck", keys, "lin")
    hold(drum, "head", rot=(20, 0, 0))
    hitch = clip_of(r, "hitch", 0.36)
    clinging(hitch)
    hitch.pos("body", [(0, 0, 0, 0), (0.08, 0, -0.5, 0), (0.18, 0, 3.2, 0), (0.3, 0, 2.8, 0), (0.36, 0, 3, 0)])
    for nm, sg in (("wing_l", 1), ("wing_r", -1)):
        hitch.rot(nm, [(0, 0, 0, 0), (0.12, -30, sg * 20, -sg * 20), (0.24, 0, 0, 0), (0.36, 0, 0, 0)])
    fly_clip(r, "fly", 0.2, up=70, down=-50)
    bound = clip_of(r, "bound", 0.6, loop=True)
    for nm, sg in (("wing_l", 1), ("wing_r", -1)):
        bound.rot(nm, [(0, -FOLD, sg * 90, sg * 45), (0.06, -FOLD, sg * 90, -sg * 65), (0.12, -FOLD, sg * 90, sg * 45),
                       (0.18, -FOLD, sg * 90, -sg * 65), (0.24, -FOLD, sg * 90, sg * 30), (0.3, 0, 0, 0), (0.54, 0, 0, 0),
                       (0.6, -FOLD, sg * 90, sg * 45)], "lin")
        L = bound.lift
        bound.pos(nm, [(0, 0, L, 0), (0.24, 0, L, 0), (0.3, 0, 0, 0), (0.54, 0, 0, 0), (0.6, 0, L, 0)], "lin")
    tuck_legs(bound)
    flare = flare_clip(r, 0.45)
    flare.rot("body", [(0, 0, 0, 0), (0.25, -55, 0, 0), (0.45, -72, 0, 0)])
    stubs(r)
    return r


# ---- crow --------------------------------------------------------------------------------------------------------------

def crow():
    r, beak = bird_body("wild_crow", (64, 64), w=6, h=6, length=10, breast=2, neck_h=3, neck_w=4, head=(5, 5, 5),
                        wing_arm=8, wing_hand=9, chord=(5, 4), tail=(5, 7), legs_h=4)
    beak.cube("obeak", -1, -1, -3, 2, 2, 3)
    r.pack()
    idle = clip_of(r, "idle", 3.6, loop=True)
    idle.rot("head", [(0, 0, 0, 0), (0.6, 0, 30, 6), (1.4, 0, 30, 6), (1.7, 0, -24, -8), (2.6, 0, -24, -8), (2.9, 0, 0, 0), (3.6, 0, 0, 0)])
    idle.rot("tail", [(0, 0, 0, 0), (2.9, 0, 0, 0), (3.0, -12, 0, 0), (3.15, 0, 0, 0), (3.6, 0, 0, 0)])
    walk = clip_of(r, "walk", 0.7, loop=True)
    # the crow's strut: a deliberate stride, the head nodding with each step
    walk.rot("leg_l", [(0, -32, 0, 0), (0.35, 32, 0, 0), (0.7, -32, 0, 0)])
    walk.rot("leg_r", [(0, 32, 0, 0), (0.35, -32, 0, 0), (0.7, 32, 0, 0)])
    walk.pos("neck", [(0, 0, 0, 1.2), (0.25, 0, 0, -0.6), (0.35, 0, 0, 1.2), (0.6, 0, 0, -0.6), (0.7, 0, 0, 1.2)])
    walk.rot("body", [(0, 0, 0, -3), (0.35, 0, 0, 3), (0.7, 0, 0, -3)])
    hop = clip_of(r, "hop", 0.4)
    hop.pos("body", [(0, 0, 0, 0), (0.08, 0, -1, 0), (0.2, 0, 2.5, -1), (0.34, 0, 0.2, 0), (0.4, 0, 0, 0)])
    for nm, sg in (("wing_l", 1), ("wing_r", -1)):
        hop.rot(nm, [(0, 0, 0, 0), (0.12, -40, sg * 40, -sg * 20), (0.3, 0, 0, 0), (0.4, 0, 0, 0)])
    peck = clip_of(r, "peck", 1.4, loop=True)
    peck.rot("body", [(0, 0, 0, 0), (0.2, 24, 0, 0), (1.1, 24, 0, 0), (1.4, 0, 0, 0)])
    peck.rot("neck", [(0, 0, 0, 0), (0.2, 40, 0, 0), (0.35, 62, 0, 0), (0.5, 40, 0, 0), (0.7, 62, 10, 0), (0.85, 40, 0, 0),
                      (1.1, 40, 0, 0), (1.4, 0, 0, 0)])
    caw = clip_of(r, "caw", 1.6)
    # each caw a bow forward with the tail flicked and the wings half-shrugged
    keys_n, keys_b, keys_t = [(0, 0, 0, 0)], [(0, 0, 0, 0)], [(0, 0, 0, 0)]
    for k in range(3):
        t = 0.1 + k * 0.45
        keys_n += [(t, -10, 0, 0), (t + 0.12, 30, 0, 0), (t + 0.3, 5, 0, 0)]
        keys_b += [(t + 0.08, 0, 0, 0), (t + 0.12, 32, 0, 0), (t + 0.26, 0, 0, 0)]
        keys_t += [(t + 0.12, -20, 0, 0), (t + 0.3, 0, 0, 0)]
    caw.rot("neck", keys_n + [(1.6, 0, 0, 0)])
    caw.rot("beak", keys_b + [(1.6, 0, 0, 0)], "lin")
    caw.rot("tail", keys_t + [(1.6, 0, 0, 0)])
    fly_clip(r, "fly", 0.5, up=55, down=-40, body_bob=0.8)
    glide = clip_of(r, "glide", 2.0, loop=True)
    glide_wings(glide, dihedral=8, sweep=-10, droop=6, rock=3)
    tuck_legs(glide)
    flare_clip(r, 0.55)
    stubs(r)
    return r


# ---- owl ---------------------------------------------------------------------------------------------------------------

def owl():
    r = Rig("wild_owl", (64, 64))
    r.walk_stride, r.run_stride, r.run_speed, r.shadow = 0.5, 1.0, (0.08, 0.16), 0.25
    # upright and round: the body stands nearly vertical on short feathered legs, a big round head on no neck at all
    body = r.bone("body", (0, 17.5, 0), (-72, 0, 0))
    body.cube("torso", -3.5, -3.5, -4.5, 7, 7, 9)
    body.cube("breast", -3, -3, -5.5, 6, 6, 1)
    neck = body.bone("neck", (0, -3, -4.5), (72, 0, 0))
    neck.cube("oneck", -2.5, -1, -2.5, 5, 1, 5)
    head = neck.bone("head", (0, -1, 0))
    head.cube("ohead", -3.5, -5.5, -3.5, 7, 6, 6)
    head.cube("disc", -3.5, -5, -4.5, 7, 5, 1)
    beak = head.bone("beak", (0, -2, -4))
    beak.cube("obeak", -0.5, -0.5, -0.8, 1, 2, 1)
    tail = body.bone("tail", (0, -2.5, 4.5), (-10, 0, 0))
    tail.cube("otail", -2.5, -0.5, 0, 5, 1, 4)

    def side(sd, nm, mir, sh):
        lg = body.bone("leg_" + nm, (sd * 1.8, 3.5, 3), (72, 0, 0))
        lg.cube("oleg_" + nm, -1, 0, -1, 2, 2, 2, mirror=mir, share=sh("oleg_l"))
        lg.cube("foot_" + nm, -1.5, 2, -2.5, 3, 0, 3, mirror=mir, share=sh("foot_l"))
        arm = body.bone("wing_" + nm, (sd * 3.5, -3 + 7, -3.5), (FOLD, -sd * 90, 0))
        if sd > 0:
            arm.cube("arm_" + nm, 0, 0, 0, 6, 1, 7)
        else:
            arm.cube("arm_" + nm, -6, 0, 0, 6, 1, 7, mirror=True, share="arm_l")
        hand = arm.bone("hand_" + nm, (sd * 6, 0, 0))
        if sd > 0:
            hand.cube("prim_" + nm, 0, 0, 0, 6, 1, 6)
        else:
            hand.cube("prim_" + nm, -6, 0, 0, 6, 1, 6, mirror=True, share="prim_l")
        ear = head.bone("tuft_" + nm, (sd * 2.5, -5.5, -1))
        ear.cube("otuft_" + nm, -0.5, -1, -0.5, 1, 1, 1, mirror=mir, share=sh("otuft_l"))
    sym(side)
    r.look = [("head", 1.0, 0.8)]
    r.wing_lift = 7
    r.pack()
    idle = clip_of(r, "idle", 6.0, loop=True)
    # still as wood: a slow feather-settle, a tilt of the head as it listens
    idle.rot("head", [(0, 0, 0, 0), (2.0, 0, 0, 0), (2.4, 0, 0, 14), (3.4, 0, 0, 14), (3.8, 0, 0, 0), (6.0, 0, 0, 0)])
    idle.scale("body", [(0, 1, 1, 1), (4.8, 1, 1, 1), (5.0, 1.08, 1.06, 1.04), (5.3, 1, 1, 1), (6.0, 1, 1, 1)])
    hoot = clip_of(r, "hoot", 2.4)
    # the throat swells under the disc, the head dips with each note, the tail lifts
    hoot.scale("neck", [(0, 1, 1, 1), (0.2, 1.3, 1.6, 1.3), (0.9, 1.3, 1.6, 1.3), (1.1, 1, 1, 1), (1.4, 1.3, 1.6, 1.3),
                        (2.1, 1.3, 1.6, 1.3), (2.4, 1, 1, 1)])
    hoot.rot("head", [(0, 0, 0, 0), (0.2, 12, 0, 0), (0.9, 8, 0, 0), (1.1, 0, 0, 0), (1.4, 12, 0, 0), (2.1, 8, 0, 0), (2.4, 0, 0, 0)])
    hoot.rot("tail", [(0, 0, 0, 0), (0.3, -18, 0, 0), (2.0, -18, 0, 0), (2.4, 0, 0, 0)])
    fly = clip_of(r, "fly", 0.7, loop=True)
    flap(fly, up=55, down=-45)
    hold(fly, "body", rot=(64, 0, 0))
    hold(fly, "neck", rot=(-64, 0, 0))
    tuck_legs(fly, 10)
    glide = clip_of(r, "glide", 2.0, loop=True)
    glide_wings(glide, dihedral=2, sweep=-4, droop=2, rock=2)
    hold(glide, "body", rot=(64, 0, 0))
    hold(glide, "neck", rot=(-64, 0, 0))
    tuck_legs(glide, 10)
    fl = clip_of(r, "flare", 0.6)
    for nm, sg in (("wing_l", 1), ("wing_r", -1)):
        fl.rot(nm, [(0, -FOLD, sg * 90, 0), (0.3, -FOLD, sg * 60, sg * 40), (0.6, 0, 0, 0)])
        fl.pos(nm, [(0, 0, fl.lift, 0), (0.3, 0, fl.lift, 0), (0.6, 0, 0, 0)])
    fl.rot("body", [(0, 64, 0, 0), (0.3, 10, 0, 0), (0.6, 0, 0, 0)])
    fl.rot("neck", [(0, -64, 0, 0), (0.3, -10, 0, 0), (0.6, 0, 0, 0)])
    fl.rot("leg_l", [(0, -40, 0, 0), (0.6, 0, 0, 0)])
    fl.rot("leg_r", [(0, -40, 0, 0), (0.6, 0, 0, 0)])
    fluff = clip_of(r, "fluff", 3.0, loop=True)
    hold(fluff, "body", scale=(1.12, 1.1, 1.08))
    stubs(r)
    return r


# ---- buzzard / kestrel -------------------------------------------------------------------------------------------------

def raptor():
    r, beak = bird_body("wild_raptor", (64, 64), w=6, h=6, length=10, breast=2, neck_h=2, neck_w=4, head=(5, 5, 5),
                        wing_arm=10, wing_hand=8, chord=(6, 5), tail=(6, 8), legs_h=4, upright=30)
    beak.cube("obeak", -0.5, -0.5, -2, 1, 1, 2)
    beak.cube("hook", -0.5, 0.5, -2, 1, 1, 1)
    r.pack()
    idle = clip_of(r, "idle", 4.0, loop=True)
    idle.rot("head", [(0, 0, 0, 0), (1.0, 0, 40, 0), (1.8, 0, 40, 0), (2.2, 0, -35, 0), (3.2, 0, -35, 0), (3.6, 0, 0, 0), (4.0, 0, 0, 0)])
    level = (30, 0, 0)  # flight takes the perch's lean back out
    soar = clip_of(r, "soar", 3.0, loop=True)
    # wings at full stretch with a lifted dihedral, the hands' primaries upturned, the tail fanned
    for nm, sg in (("wing_l", 1), ("wing_r", -1)):
        cycle(soar, nm, "rot", lambda p, sg=sg: (-FOLD, sg * 90, -sg * (10 + 2.5 * s(p))), 8)
        soar.rot("hand_" + nm[-1], [(0, 0, sg * -4, -sg * 10), (3.0, 0, sg * -4, -sg * 10)])
    lift_hold(soar)
    hold(soar, "body", rot=level)
    hold(soar, "neck", rot=(-20, 0, 0))
    hold(soar, "tail", scale=(1.5, 1, 1), rot=(-24, 0, 0))
    tuck_legs(soar, 60)
    fl_ = clip_of(r, "fly", 0.62, loop=True)
    flap(fl_, up=48, down=-38)
    hold(fl_, "body", rot=level)
    hold(fl_, "neck", rot=(-20, 0, 0))
    hold(fl_, "tail", rot=(-24, 0, 0))
    tuck_legs(fl_, 60)
    hover = clip_of(r, "hover", 0.24, loop=True)
    # a kestrel holding still in the wind: the body tilted up, quick shallow beats, the tail fanned and dipped
    flap(hover, up=40, down=-30, tip_lag=0.2)
    hold(hover, "body", rot=(0, 0, 0))
    hold(hover, "neck", rot=(10, 0, 0))
    hold(hover, "head", rot=(30, 0, 0))
    hold(hover, "tail", scale=(1.6, 1, 1), rot=(10, 0, 0))
    tuck_legs(hover, 30)
    stoop = clip_of(r, "stoop", 1.0, loop=True)
    for nm, sg in (("wing_l", 1), ("wing_r", -1)):
        hold(stoop, nm, rot=(-FOLD, sg * 50, -sg * 5))
        hold(stoop, "hand_" + nm[-1], rot=(0, sg * -60, 0))
    lift_hold(stoop)
    hold(stoop, "body", rot=level)
    tuck_legs(stoop, 75)
    call = clip_of(r, "call", 1.4)
    call.rot("neck", [(0, 0, 0, 0), (0.2, -25, 0, 0), (1.1, -25, 0, 0), (1.4, 0, 0, 0)])
    call.rot("beak", [(0, 0, 0, 0), (0.25, 22, 0, 0), (1.0, 22, 0, 0), (1.2, 0, 0, 0), (1.4, 0, 0, 0)])
    flare_clip(r, 0.6)
    stubs(r)
    return r


# ---- gull (Carillon's) -------------------------------------------------------------------------------------------------

def gull():
    r = CF.gull()
    r.id = "wild_gull"
    return r


# ---- duck --------------------------------------------------------------------------------------------------------------

def duck():
    r, beak = bird_body("wild_duck", (64, 64), w=7, h=5, length=10, breast=2, neck_h=4, neck_w=3, head=(4, 4, 5),
                        wing_arm=6, wing_hand=7, chord=(5, 4), tail=(4, 3), legs_h=2)
    beak.cube("obeak", -1, 0, -3, 2, 1, 3)
    r.by_name["tail"].cube("curl", -0.5, -2, 2, 1, 2, 1)
    r.pack()
    idle = clip_of(r, "idle", 3.4, loop=True)
    idle.rot("head", [(0, 0, 0, 0), (0.8, 0, 28, 0), (1.6, 0, 28, 0), (1.9, 0, -20, 0), (2.8, 0, -20, 0), (3.1, 0, 0, 0), (3.4, 0, 0, 0)])
    idle.rot("tail", [(0, 0, 0, 0), (2.0, 0, 0, 0), (2.08, 0, 22, 0), (2.16, 0, -22, 0), (2.24, 0, 14, 0), (2.32, 0, 0, 0), (3.4, 0, 0, 0)])
    swim = clip_of(r, "swim", 1.8, loop=True)
    # sitting in the water: a gentle rock and the feet paddling unseen; the head bobs with the strokes
    cycle(swim, "body", "rot", lambda p: (2 * s(p), 0, 1.5 * s(p, 0.25)), 8)
    cycle(swim, "neck", "rot", lambda p: (5 * s(p * 2), 0, 0), 8)
    cycle(swim, "leg_l", "rot", lambda p: (40 + 30 * s(p * 2), 0, 0), 8)
    cycle(swim, "leg_r", "rot", lambda p: (40 + 30 * s(p * 2, 0.5), 0, 0), 8)
    walk = clip_of(r, "walk", 0.6, loop=True)
    # the waddle: the body rolls from foot to foot
    walk.rot("leg_l", [(0, -30, 0, 0), (0.3, 30, 0, 0), (0.6, -30, 0, 0)])
    walk.rot("leg_r", [(0, 30, 0, 0), (0.3, -30, 0, 0), (0.6, 30, 0, 0)])
    walk.rot("body", [(0, 0, 0, -9), (0.3, 0, 0, 9), (0.6, 0, 0, -9)])
    cycle(walk, "neck", "rot", lambda p: (6 * s(p * 2), 0, 0), 8)
    dabble = clip_of(r, "dabble", 3.0)
    # bottoms up: the head goes under, tail to the sky, the feet kicking to hold it there
    dabble.rot("body", [(0, 0, 0, 0), (0.35, 95, 0, 0), (2.5, 95, 0, 0), (2.9, 0, 0, 0), (3.0, 0, 0, 0)])
    dabble.pos("body", [(0, 0, 0, 0), (0.35, 0, -1.5, -1), (2.5, 0, -1.5, -1), (2.9, 0, 0, 0), (3.0, 0, 0, 0)])
    dabble.rot("neck", [(0, 0, 0, 0), (0.35, 30, 0, 0), (2.5, 30, 0, 0), (2.9, 0, 0, 0)])
    for nm, ph in (("leg_l", 0), ("leg_r", 0.5)):
        cycle(dabble, nm, "rot", lambda p, ph=ph: (-40 + 35 * s(p * 6, ph), 0, 0), 24)
    dabble.rot("tail", [(0, 0, 0, 0), (0.6, -20, 0, 0), (0.8, -20, 15, 0), (1.0, -20, -15, 0), (1.2, -20, 0, 0), (2.5, -20, 0, 0), (3.0, 0, 0, 0)])
    preen = clip_of(r, "preen", 2.4, loop=True)
    preen.rot("neck", [(0, 0, 0, 0), (0.3, 30, 120, 0), (1.9, 30, 120, 0), (2.2, 0, 0, 0), (2.4, 0, 0, 0)])
    preen.rot("head", [(0, 0, 0, 0), (0.3, 40, 30, 0), (0.6, 55, 30, 0), (0.9, 40, 30, 0), (1.2, 55, 40, 0), (1.5, 40, 30, 0),
                       (1.9, 40, 30, 0), (2.2, 0, 0, 0), (2.4, 0, 0, 0)])
    preen.rot("wing_l", [(0, 0, 0, 0), (0.3, 0, 14, -14), (1.9, 0, 14, -14), (2.2, 0, 0, 0)])
    quack = clip_of(r, "quack", 1.4)
    keys_n, keys_b = [(0, 0, 0, 0)], [(0, 0, 0, 0)]
    for k in range(3):
        t = 0.08 + k * 0.38
        keys_n += [(t, 0, 0, 0), (t + 0.1, 22, 0, 0), (t + 0.26, 4, 0, 0)]
        keys_b += [(t + 0.06, 0, 0, 0), (t + 0.1, 26, 0, 0), (t + 0.22, 0, 0, 0)]
    quack.rot("neck", keys_n + [(1.4, 0, 0, 0)])
    quack.rot("beak", keys_b + [(1.4, 0, 0, 0)], "lin")
    fly_clip(r, "fly", 0.26, up=60, down=-45, neck=-5)
    glide = clip_of(r, "glide", 1.6, loop=True)
    glide_wings(glide, dihedral=4, sweep=-12, droop=3, rock=2)
    tuck_legs(glide)
    fl = flare_clip(r, 0.5)
    fl.rot("leg_l", [(0, -50, 0, 0), (0.5, -50, 0, 0)])
    fl.rot("leg_r", [(0, -50, 0, 0), (0.5, -50, 0, 0)])
    stubs(r)
    return r


# ---- heron -------------------------------------------------------------------------------------------------------------

def heron():
    r = Rig("wild_heron", (64, 64))
    r.walk_stride, r.run_stride, r.run_speed, r.shadow = 0.9, 1.6, (0.08, 0.16), 0.35
    # tall, grey and still: long legs in two parts, an S of a neck in two, a dagger of a bill and a trailing crest
    body = r.bone("body", (0, 6, 0), (-24, 0, 0))
    body.cube("torso", -2.5, -3, -4.5, 5, 6, 10)
    body.cube("breast", -2, -1, -5.5, 4, 5, 1)
    n1 = body.bone("neck", (0, -2, -5), (-40, 0, 0))
    n1.cube("oneck1", -1, -5, -1, 2, 5, 2)
    n2 = n1.bone("neck2", (0, -5, 0), (70, 0, 0))
    n2.cube("oneck2", -1, -5, -1, 2, 5, 2)
    head = n2.bone("head", (0, -5, 0), (-6, 0, 0))
    head.cube("ohead", -1.5, -2.5, -2.5, 3, 3, 4)
    beak = head.bone("beak", (0, -1.2, -2.5))
    beak.cube("obeak", -0.5, -0.5, -6, 1, 1, 6)
    plume = head.bone("plume", (0, -2, 1.5), (30, 0, 0))
    plume.cube("oplume", -0.5, -0.5, 0, 1, 0, 4)
    tail = body.bone("tail", (0, -1, 5.5), (10, 0, 0))
    tail.cube("otail", -2, -0.5, 0, 4, 1, 3)

    def side(sd, nm, mir, sh):
        lg = body.bone("leg_" + nm, (sd * 1.2, 3, 1), (24, 0, 0))
        lg.cube("oleg_" + nm, -0.5, 0, -0.5, 1, 7, 1, mirror=mir, share=sh("oleg_l"))
        sn = lg.bone("shin_" + nm, (0, 7, 0))
        sn.cube("oshin_" + nm, -0.5, 0, -0.5, 1, 8, 1, mirror=mir, share=sh("oshin_l"))
        sn.cube("foot_" + nm, -2, 8, -3.5, 4, 0, 5, mirror=mir, share=sh("foot_l"))
        arm = body.bone("wing_" + nm, (sd * 2.5, -2.5 + 6, -3.5), (FOLD, -sd * 90, 0))
        if sd > 0:
            arm.cube("arm_" + nm, 0, 0, 0, 8, 1, 6)
        else:
            arm.cube("arm_" + nm, -8, 0, 0, 8, 1, 6, mirror=True, share="arm_l")
        hand = arm.bone("hand_" + nm, (sd * 8, 0, 0))
        if sd > 0:
            hand.cube("prim_" + nm, 0, 0, 0, 8, 1, 5)
        else:
            hand.cube("prim_" + nm, -8, 0, 0, 8, 1, 5, mirror=True, share="prim_l")
    sym(side)
    r.look = [("neck", 0.25, 0.2), ("neck2", 0.25, 0.3), ("head", 0.5, 0.5)]
    r.wing_lift = 6
    r.pack()
    idle = clip_of(r, "stand", 6.0, loop=True)
    # the statue: neck drawn down into the shoulders, now and then a slow look along the water
    hold(idle, "neck", rot=(25, 0, 0))
    idle.rot("neck2", [(0, -25, 0, 0), (3.0, -25, 0, 0), (3.6, -25, 20, 0), (4.8, -25, 20, 0), (5.4, -25, 0, 0), (6.0, -25, 0, 0)])
    idle2 = clip_of(r, "idle", 6.0, loop=True)  # the generic name the brains fall back on
    for (bn, tg), keys in idle.tracks.items():
        idle2.tracks[(bn, tg)] = list(keys)
    alert = clip_of(r, "alert", 2.0, loop=True)
    # neck straight up, the bill level, very still
    hold(alert, "neck", rot=(-10, 0, 0))
    hold(alert, "neck2", rot=(-60, 0, 0))
    hold(alert, "head", rot=(6, 0, 0))
    stalk = clip_of(r, "stalk", 2.0, loop=True)
    # slow, high steps, the neck reaching forward and low over the water
    for nm, ph in (("leg_l", 0), ("leg_r", 0.5)):
        cycle(stalk, nm, "rot", lambda p, ph=ph: (24 + 22 * s(p, ph), 0, 0), 8)
        cycle(stalk, "shin_" + nm[-1], "rot", lambda p, ph=ph: (-40 * max(0, s(p, ph)), 0, 0), 8)
    hold(stalk, "neck", rot=(30, 0, 0))
    hold(stalk, "neck2", rot=(-30, 0, 0))
    strike = clip_of(r, "strike", 1.2)
    strike.rot("neck", [(0, 30, 0, 0), (0.3, 30, 0, 0), (0.38, 85, 0, 0), (0.7, 85, 0, 0), (1.0, 25, 0, 0), (1.2, 25, 0, 0)])
    strike.rot("neck2", [(0, -30, 0, 0), (0.3, -60, 0, 0), (0.38, -40, 0, 0), (0.7, -40, 0, 0), (1.0, -25, 0, 0), (1.2, -25, 0, 0)])
    strike.rot("beak", [(0, 0, 0, 0), (0.6, 0, 0, 0), (0.66, 10, 0, 0), (0.75, 0, 0, 0), (1.2, 0, 0, 0)])
    fly = clip_of(r, "fly", 0.9, loop=True)
    # slow, deep, bowed wingbeats; the neck folded back into the shoulders, legs trailing
    flap(fly, up=45, down=-50, tip_lag=0.18)
    hold(fly, "body", rot=(24, 0, 0))
    hold(fly, "neck", rot=(60, 0, 0))
    hold(fly, "neck2", rot=(-110, 0, 0))
    hold(fly, "head", rot=(50, 0, 0))
    for nm in ("leg_l", "leg_r"):
        hold(fly, nm, rot=(66, 0, 0))
    glide = clip_of(r, "glide", 2.0, loop=True)
    glide_wings(glide, dihedral=-4, sweep=-6, droop=8, rock=2)
    for bn, rot in (("body", (24, 0, 0)), ("neck", (60, 0, 0)), ("neck2", (-110, 0, 0)), ("head", (50, 0, 0)),
                    ("leg_l", (66, 0, 0)), ("leg_r", (66, 0, 0))):
        hold(glide, bn, rot=rot)
    fl = clip_of(r, "flare", 0.7)
    for nm, sg in (("wing_l", 1), ("wing_r", -1)):
        fl.rot(nm, [(0, -FOLD, sg * 90, 0), (0.35, -FOLD, sg * 60, sg * 45), (0.7, 0, 0, 0)])
        fl.pos(nm, [(0, 0, fl.lift, 0), (0.35, 0, fl.lift, 0), (0.7, 0, 0, 0)])
    fl.rot("leg_l", [(0, 40, 0, 0), (0.5, -10, 0, 0), (0.7, 0, 0, 0)])
    fl.rot("leg_r", [(0, 40, 0, 0), (0.5, -10, 0, 0), (0.7, 0, 0, 0)])
    stubs(r)
    return r


# =====================================================================================================================
# Small mammals and a frog
# =====================================================================================================================

def squirrel():
    r = Rig("wild_squirrel", (64, 32))
    r.walk_stride, r.run_stride, r.run_speed, r.shadow = 0.6, 1.4, (0.1, 0.2), 0.2
    body = r.bone("body", (0, 19.5, 0))
    body.cube("torso", -2, -2, -3.5, 4, 4, 7)
    neck = body.bone("neck", (0, -1, -3.5), (-10, 0, 0))
    neck.cube("oneck", -1.5, -1.5, -1.5, 3, 3, 2)
    head = neck.bone("head", (0, -1, -1.5), (10, 0, 0))
    head.cube("ohead", -2, -2.5, -3.5, 4, 4, 4)
    head.cube("nose", -1, -1, -4.2, 2, 2, 1)

    def side(sd, nm, mir, sh):
        ear = head.bone("ear_" + nm, (sd * 1.3, -2.5, -1.5), (0, 0, sd * 8))
        ear.cube("oear_" + nm, -0.5, -2, -0.5, 1, 2, 1, mirror=mir, share=sh("oear_l"))
        ear.cube("tuft_" + nm, -0.5, -3, -0.5, 1, 1, 1, inflate=-0.1, mirror=mir, share=sh("tuft_l"))
        fl = body.bone("fleg_" + nm, (sd * 1.3, 1.5, -2.5))
        fl.cube("ofleg_" + nm, -0.5, 0, -0.5, 1, 3, 1, mirror=mir, share=sh("ofleg_l"))
        hl = body.bone("hleg_" + nm, (sd * 1.5, 0.5, 2))
        hl.cube("othigh_" + nm, -1, -1, -1.5, 2, 3, 3, mirror=mir, share=sh("othigh_l"))
        hl.cube("hfoot_" + nm, -0.5, 2, -2.5, 1, 1, 3, mirror=mir, share=sh("hfoot_l"))
    sym(side)
    # the tail: three bushy links curling up over the back
    t1 = body.bone("tail1", (0, -1, 3.5), (-55, 0, 0))
    t1.cube("otail1", -1.5, -1.5, 0, 3, 3, 4)
    t2 = t1.bone("tail2", (0, 0, 4), (-35, 0, 0))
    t2.cube("otail2", -2, -2, 0, 4, 4, 4)
    t3 = t2.bone("tail3", (0, 0, 4), (-30, 0, 0))
    t3.cube("otail3", -2, -2, 0, 4, 4, 3, inflate=-0.2)
    r.look = [("neck", 0.3, 0.3), ("head", 0.7, 0.7)]
    r.pack()
    sitpose = {"body": (-55, 0, 0), "neck": (40, 0, 0), "head": (10, 0, 0), "hleg_l": (55, 0, 0), "hleg_r": (55, 0, 0),
               "fleg_l": (-30, 0, 0), "fleg_r": (-30, 0, 0), "tail1": (30, 0, 0)}
    idle = clip_of(r, "idle", 2.4, loop=True)
    # up on its haunches nibbling something held in the forepaws
    for b, rot in sitpose.items():
        if b not in ("fleg_l", "fleg_r", "head"):
            hold(idle, b, rot=rot)
    hold(idle, "body", pos=(0, -1.5, 1))
    cycle(idle, "fleg_l", "rot", lambda p: (-70 + 8 * s(p * 4), 0, 10), 16)
    cycle(idle, "fleg_r", "rot", lambda p: (-70 + 8 * s(p * 4, 0.2), 0, -10), 16)
    cycle(idle, "head", "rot", lambda p: (24 + 6 * s(p * 4), 0, 0), 16)
    cycle(idle, "tail3", "rot", lambda p: (0, 8 * s(p), 0), 8)
    run = clip_of(r, "run", 0.3, loop=True)
    # bounding: the hind feet land ahead of the front, the back arching and stretching
    cycle(run, "body", "rot", lambda p: (-14 * s(p), 0, 0), 8)
    cycle(run, "body", "pos", lambda p: (0, 1.4 * max(0, s(p)), 0), 8)
    cycle(run, "fleg_l", "rot", lambda p: (60 * s(p, 0.1), 0, 0), 8)
    cycle(run, "fleg_r", "rot", lambda p: (60 * s(p, 0.15), 0, 0), 8)
    cycle(run, "hleg_l", "rot", lambda p: (-60 * s(p, 0.1), 0, 0), 8)
    cycle(run, "hleg_r", "rot", lambda p: (-60 * s(p, 0.15), 0, 0), 8)
    hold(run, "tail1", rot=(40, 0, 0))
    cycle(run, "tail2", "rot", lambda p: (20 + 10 * s(p, -0.2), 0, 0), 8)
    climb = clip_of(r, "climb", 0.32, loop=True)
    # up a trunk, nose to the sky: claws scrabbling in pairs, the tail streaming down behind
    hold(climb, "body", rot=(-90, 0, 0))
    hold(climb, "neck", rot=(60, 0, 0))
    for nm, ph in (("fleg_l", 0), ("fleg_r", 0.08), ("hleg_l", 0.5), ("hleg_r", 0.58)):
        cycle(climb, nm, "rot", lambda p, ph=ph: (-50 * s(p, ph), 0, 0), 8)
    hold(climb, "tail1", rot=(55, 0, 0))
    cycle(climb, "tail2", "rot", lambda p: (35 + 10 * s(p), 0, 0), 8)
    hold(climb, "tail3", rot=(20, 0, 0))
    descend = clip_of(r, "descend", 0.36, loop=True)
    # head first down the bark, hind feet turned back to grip
    hold(descend, "body", rot=(90, 0, 0))
    hold(descend, "neck", rot=(-70, 0, 0))
    for nm, ph in (("fleg_l", 0), ("fleg_r", 0.08)):
        cycle(descend, nm, "rot", lambda p, ph=ph: (-40 * s(p, ph), 0, 0), 8)
    for nm in ("hleg_l", "hleg_r"):
        hold(descend, nm, rot=(-150, 0, 0))
    hold(descend, "tail1", rot=(-30, 0, 0))
    cling = clip_of(r, "cling", 2.0, loop=True)
    hold(cling, "body", rot=(-90, 0, 0))
    hold(cling, "neck", rot=(60, 0, 0))
    for nm in ("fleg_l", "fleg_r"):
        hold(cling, nm, rot=(-30, 0, 0))
    for nm in ("hleg_l", "hleg_r"):
        hold(cling, nm, rot=(20, 0, 0))
    hold(cling, "tail1", rot=(55, 0, 0))
    cycle(cling, "tail2", "rot", lambda p: (35, 12 * s(p), 0), 8)
    chatter = clip_of(r, "chatter", 1.2)
    for b, rot in sitpose.items():
        hold(chatter, b, rot=rot)
    chatter.rot("tail2", [(0, 0, 0, 0)] + [(0.08 + k * 0.14, -40 if k % 2 == 0 else 10, 0, 0) for k in range(7)] + [(1.2, 0, 0, 0)])
    chatter.pos("body", [(0, 0, 0, 0)] + [(0.08 + k * 0.14, 0, -0.6 if k % 2 == 0 else 0.4, 0) for k in range(7)] + [(1.2, 0, 0, 0)])
    forage = clip_of(r, "forage", 1.6, loop=True)
    hold(forage, "body", rot=(18, 0, 0))
    hold(forage, "neck", rot=(30, 0, 0))
    cycle(forage, "fleg_l", "rot", lambda p: (-30 + 30 * s(p * 4), 0, 0), 16)
    cycle(forage, "fleg_r", "rot", lambda p: (-30 + 30 * s(p * 4, 0.5), 0, 0), 16)
    hold(forage, "tail1", rot=(10, 0, 0))
    stubs(r)
    return r


def rabbit():
    r = Rig("wild_rabbit", (64, 32))
    r.walk_stride, r.run_stride, r.run_speed, r.shadow = 0.6, 1.6, (0.1, 0.22), 0.25
    body = r.bone("body", (0, 19, 0), (-12, 0, 0))
    body.cube("torso", -2.5, -2.5, -3.5, 5, 5, 7)
    body.cube("haunch", -3, -2, 1, 6, 5, 3)
    neck = body.bone("neck", (0, -1.5, -3.5), (5, 0, 0))
    neck.cube("oneck", -1.5, -1.5, -1, 3, 3, 2)
    head = neck.bone("head", (0, -1.5, -1), (7, 0, 0))
    head.cube("ohead", -2, -2.5, -4, 4, 4, 4)
    head.cube("muzzle", -1, -0.5, -4.6, 2, 2, 1)

    def side(sd, nm, mir, sh):
        ear = head.bone("ear_" + nm, (sd * 1.0, -2.5, -1), (-12, 0, sd * 10))
        ear.cube("oear_" + nm, -0.5, -4, -1, 1, 4, 2, mirror=mir, share=sh("oear_l"))
        tip = ear.bone("eartip_" + nm, (0, -4, 0), (-5, 0, 0))
        tip.cube("oeartip_" + nm, -0.5, -1, -1, 1, 1, 2, inflate=-0.1, mirror=mir, share=sh("oeartip_l"))
        fl = body.bone("fleg_" + nm, (sd * 1.4, 2, -2.5), (12, 0, 0))
        fl.cube("ofleg_" + nm, -0.5, 0, -0.5, 1, 4, 1, mirror=mir, share=sh("ofleg_l"))
        hl = body.bone("hleg_" + nm, (sd * 2, 1, 2.5), (12, 0, 0))
        hl.cube("othigh_" + nm, -1, -1, -2, 2, 3, 4, mirror=mir, share=sh("othigh_l"))
        hl.cube("hfoot_" + nm, -0.5, 2.5, -3.5, 1, 1, 4, mirror=mir, share=sh("hfoot_l"))
    sym(side)
    tail = body.bone("tail", (0, -1, 4.5))
    tail.cube("otail", -1, -1, 0, 2, 2, 2)
    r.look = [("neck", 0.3, 0.3), ("head", 0.7, 0.7)]
    r.pack()
    idle = clip_of(r, "idle", 3.0, loop=True)
    # hunched and watchful: the nose working, an ear swivelling
    keys = [(0, 0, 0, 0)]
    for k in range(10):
        keys += [(0.1 + k * 0.28, 4, 0, 0), (0.24 + k * 0.28, 0, 0, 0)]
    keys.append((3.0, 0, 0, 0))
    idle.rot("head", keys, "lin")
    idle.rot("ear_l", [(0, 0, 0, 0), (1.2, 0, 0, 0), (1.4, 0, -40, 0), (2.2, 0, -40, 0), (2.4, 0, 0, 0), (3.0, 0, 0, 0)])
    graze = clip_of(r, "graze", 2.0, loop=True)
    hold(graze, "body", rot=(10, 0, 0))
    hold(graze, "neck", rot=(30, 0, 0))
    cycle(graze, "head", "rot", lambda p: (25 + 6 * s(p * 5), 0, 0), 20)
    hold(graze, "ear_l", rot=(30, 0, 0))
    hold(graze, "ear_r", rot=(30, 0, 0))
    hop = clip_of(r, "hop", 0.45, loop=True)
    cycle(hop, "body", "pos", lambda p: (0, 1.6 * max(0, s(p)), 0), 8)
    cycle(hop, "body", "rot", lambda p: (-10 * s(p), 0, 0), 8)
    cycle(hop, "hleg_l", "rot", lambda p: (-50 * s(p, 0.05), 0, 0), 8)
    cycle(hop, "hleg_r", "rot", lambda p: (-50 * s(p, 0.05), 0, 0), 8)
    cycle(hop, "fleg_l", "rot", lambda p: (40 * s(p, 0.25), 0, 0), 8)
    cycle(hop, "fleg_r", "rot", lambda p: (40 * s(p, 0.3), 0, 0), 8)
    run = clip_of(r, "run", 0.28, loop=True)
    cycle(run, "body", "pos", lambda p: (0, 2.2 * max(0, s(p)), 0), 8)
    cycle(run, "body", "rot", lambda p: (-16 * s(p), 0, 0), 8)
    for nm, ph, a in (("hleg_l", 0.05, -75), ("hleg_r", 0.08, -75), ("fleg_l", 0.3, 60), ("fleg_r", 0.35, 60)):
        cycle(run, nm, "rot", lambda p, ph=ph, a=a: (a * s(p, ph), 0, 0), 8)
    hold(run, "ear_l", rot=(55, 0, 0))
    hold(run, "ear_r", rot=(55, 0, 0))
    alert = clip_of(r, "alert", 2.0, loop=True)
    # up on the haunches, tall and frozen, ears straight up
    hold(alert, "body", rot=(-50, 0, 0), pos=(0, 1, 0.5))
    hold(alert, "neck", rot=(35, 0, 0))
    hold(alert, "head", rot=(10, 0, 0))
    for nm in ("fleg_l", "fleg_r"):
        hold(alert, nm, rot=(20, 0, 0))
    for nm in ("hleg_l", "hleg_r"):
        hold(alert, nm, rot=(45, 0, 0))
    hold(alert, "ear_l", rot=(-15, 0, 0))
    hold(alert, "ear_r", rot=(-15, 0, 0))
    groom = clip_of(r, "groom", 2.2, loop=True)
    hold(groom, "body", rot=(-30, 0, 0))
    hold(groom, "neck", rot=(25, 0, 0))
    cycle(groom, "fleg_l", "rot", lambda p: (-80 + 25 * s(p * 3), 0, 12), 12)
    cycle(groom, "fleg_r", "rot", lambda p: (-80 + 25 * s(p * 3, 0.1), 0, -12), 12)
    cycle(groom, "head", "rot", lambda p: (20 + 10 * s(p * 3), 0, 0), 12)
    stubs(r)
    return r


def fox():
    r = Rig("wild_fox", (64, 64))
    r.walk_stride, r.run_stride, r.run_speed, r.shadow = 1.0, 2.6, (0.12, 0.26), 0.4
    body = r.bone("body", (0, 14.5, 0))
    body.cube("torso", -2.5, -2.5, -5.5, 5, 5, 11)
    body.cube("ruff", -3, -3, -6.5, 6, 6, 3)
    neck = body.bone("neck", (0, -1.5, -6), (18, 0, 0))
    neck.cube("oneck", -1.5, -3.5, -1.5, 3, 4, 3)
    head = neck.bone("head", (0, -3, -0.5), (-18, 0, 0))
    head.cube("ohead", -2.5, -2.5, -3.5, 5, 4, 4)
    head.cube("snout", -1, -0.5, -6.5, 2, 2, 3)
    head.cube("nose", -0.5, -0.7, -7.2, 1, 1, 1)
    jaw = head.bone("jaw", (0, 1.5, -3.5))
    jaw.cube("ojaw", -1, 0, -3, 2, 1, 3)

    def side(sd, nm, mir, sh):
        ear = head.bone("ear_" + nm, (sd * 1.6, -2.5, -1), (-8, 0, sd * 12))
        ear.cube("oear_" + nm, -1, -3, -0.5, 2, 3, 1, mirror=mir, share=sh("oear_l"))
        tip = ear.bone("eartip_" + nm, (0, -3, 0))
        tip.cube("oeartip_" + nm, -0.5, -1, -0.5, 1, 1, 1, mirror=mir, share=sh("oeartip_l"))
        fl = body.bone("fleg_" + nm, (sd * 1.5, 2, -4.5))
        fl.cube("ofleg_" + nm, -1, 0, -1, 2, 8, 2, inflate=-0.25, mirror=mir, share=sh("ofleg_l"))
        hl = body.bone("hleg_" + nm, (sd * 1.5, 1.5, 4))
        hl.cube("othigh_" + nm, -1, -1, -1.5, 2, 4, 3, mirror=mir, share=sh("othigh_l"))
        hk = hl.bone("hock_" + nm, (0, 3, 1))
        hk.cube("ohock_" + nm, -1, 0, -1, 2, 5, 2, inflate=-0.25, mirror=mir, share=sh("ohock_l"))
    sym(side)
    t1 = body.bone("tail1", (0, -1.5, 5.5), (-25, 0, 0))
    t1.cube("otail1", -1.5, -1.5, 0, 3, 3, 4)
    t2 = t1.bone("tail2", (0, 0, 4), (12, 0, 0))
    t2.cube("otail2", -2, -2, 0, 4, 4, 4)
    t3 = t2.bone("tail3", (0, 0, 4), (10, 0, 0))
    t3.cube("otail3", -1.5, -1.5, 0, 3, 3, 3, inflate=-0.1)
    r.look = [("neck", 0.35, 0.3), ("head", 0.65, 0.7)]
    r.pack()
    idle = clip_of(r, "idle", 3.5, loop=True)
    cycle(idle, "tail2", "rot", lambda p: (0, 8 * s(p), 0), 8)
    cycle(idle, "tail3", "rot", lambda p: (0, 12 * s(p, -0.15), 0), 8)
    idle.rot("ear_l", [(0, 0, 0, 0), (1.5, 0, 0, 0), (1.6, 0, -25, 0), (2.4, 0, -25, 0), (2.5, 0, 0, 0), (3.5, 0, 0, 0)])
    walk = clip_of(r, "walk", 0.9, loop=True)
    for nm, ph in (("fleg_l", 0), ("hleg_r", 0.08), ("fleg_r", 0.5), ("hleg_l", 0.58)):
        cycle(walk, nm, "rot", lambda p, ph=ph: (26 * s(p, ph), 0, 0), 8)
    for nm, ph in (("hock_l", 0.58), ("hock_r", 0.08)):
        cycle(walk, nm, "rot", lambda p, ph=ph: (-18 * max(0, c(p, ph)), 0, 0), 8)
    cycle(walk, "body", "pos", lambda p: (0, 0.3 * c(p * 2), 0), 8)
    hold(walk, "neck", rot=(10, 0, 0))
    trot = clip_of(r, "trot", 0.5, loop=True)
    # the fox's trot: light and level, the head low and still, the brush held straight out behind
    for nm, ph in (("fleg_l", 0), ("hleg_r", 0), ("fleg_r", 0.5), ("hleg_l", 0.5)):
        cycle(trot, nm, "rot", lambda p, ph=ph: (40 * s(p, ph), 0, 0), 8)
    for nm, ph in (("hock_l", 0.5), ("hock_r", 0)):
        cycle(trot, nm, "rot", lambda p, ph=ph: (-28 * max(0, c(p, ph)), 0, 0), 8)
    cycle(trot, "body", "pos", lambda p: (0, 0.5 * abs(s(p)), 0), 8)
    hold(trot, "neck", rot=(22, 0, 0))
    hold(trot, "tail1", rot=(15, 0, 0))
    run = clip_of(r, "run", 0.38, loop=True)
    cycle(run, "body", "rot", lambda p: (-10 * s(p), 0, 0), 8)
    cycle(run, "body", "pos", lambda p: (0, 1.2 * max(0, s(p)), 0), 8)
    for nm, ph, a in (("fleg_l", 0.0, 60), ("fleg_r", 0.08, 60), ("hleg_l", 0.5, -60), ("hleg_r", 0.58, -60)):
        cycle(run, nm, "rot", lambda p, ph=ph, a=a: (a * s(p, ph), 0, 0), 8)
    hold(run, "tail1", rot=(20, 0, 0))
    hold(run, "ear_l", rot=(35, 0, 0))
    hold(run, "ear_r", rot=(35, 0, 0))
    sit = clip_of(r, "sit", 3.0, loop=True)
    hold(sit, "body", rot=(-40, 0, 0), pos=(0, -3.5, 2))
    hold(sit, "neck", rot=(25, 0, 0))
    hold(sit, "head", rot=(15, 0, 0))
    for nm in ("fleg_l", "fleg_r"):
        hold(sit, nm, rot=(40, 0, 0))
    for nm in ("hleg_l", "hleg_r"):
        hold(sit, nm, rot=(-50, 0, 0))
    for nm in ("hock_l", "hock_r"):
        hold(sit, nm, rot=(95, 0, 0))
    hold(sit, "tail1", rot=(35, 60, 0))
    hold(sit, "tail2", rot=(0, 35, 0))
    sniff = clip_of(r, "sniff", 1.8, loop=True)
    sniff.rot("neck", [(0, 0, 0, 0), (0.3, 50, 0, 0), (1.5, 50, 0, 0), (1.8, 0, 0, 0)])
    sniff.rot("head", [(0, 0, 0, 0), (0.3, 10, 0, 0), (0.6, 10, 20, 0), (0.9, 10, -20, 0), (1.2, 10, 10, 0), (1.5, 10, 0, 0), (1.8, 0, 0, 0)])
    listen = clip_of(r, "listen", 2.4, loop=True)
    # still, head cocked at a sound in the grass: the moment before the pounce
    hold(listen, "neck", rot=(30, 0, 0))
    listen.rot("head", [(0, 0, 0, 0), (0.4, 25, 0, 24), (1.2, 25, 0, 24), (1.5, 25, 0, -18), (2.1, 25, 0, -18), (2.4, 0, 0, 0)])
    hold(listen, "ear_l", rot=(-10, 0, 0))
    hold(listen, "ear_r", rot=(-10, 0, 0))
    pounce = clip_of(r, "pounce", 1.5)
    # the mousing leap: crouch, spring high, arc, and come down nose and forepaws first
    pounce.pos("body", [(0, 0, 0, 0), (0.25, 0, -2.5, 0), (0.45, 0, 4, -2), (0.7, 0, 9, -6), (0.95, 0, 4, -10), (1.15, 0, -1.5, -12),
                        (1.5, 0, 0, -12)])
    pounce.rot("body", [(0, 0, 0, 0), (0.25, 5, 0, 0), (0.45, -30, 0, 0), (0.7, 0, 0, 0), (0.95, 45, 0, 0), (1.15, 30, 0, 0), (1.5, 0, 0, 0)])
    for nm in ("fleg_l", "fleg_r"):
        pounce.rot(nm, [(0, 0, 0, 0), (0.25, 30, 0, 0), (0.45, -50, 0, 0), (0.95, -20, 0, 0), (1.15, 10, 0, 0), (1.5, 0, 0, 0)])
    for nm in ("hleg_l", "hleg_r"):
        pounce.rot(nm, [(0, 0, 0, 0), (0.25, -30, 0, 0), (0.45, 50, 0, 0), (0.95, 40, 0, 0), (1.5, 0, 0, 0)])
    pounce.rot("tail1", [(0, 0, 0, 0), (0.45, -40, 0, 0), (0.95, 30, 0, 0), (1.5, 0, 0, 0)])
    lie = clip_of(r, "lie", 4.0, loop=True)
    # curled nose to tail
    hold(lie, "body", pos=(0, -5.5, 0))
    for nm in ("fleg_l", "fleg_r"):
        hold(lie, nm, rot=(-85, 0, 0), pos=(0, 3.5, 1))
    for nm in ("hleg_l", "hleg_r"):
        hold(lie, nm, rot=(70, 0, 0))
    for nm in ("hock_l", "hock_r"):
        hold(lie, nm, rot=(-140, 0, 0))
    hold(lie, "neck", rot=(40, -70, 0))
    hold(lie, "head", rot=(20, -30, -20))
    hold(lie, "tail1", rot=(40, -70, 0))
    hold(lie, "tail2", rot=(0, -50, 0))
    hold(lie, "tail3", rot=(0, -40, 0))
    stubs(r)
    return r


def frog():
    r = Rig("wild_frog", (32, 32))
    r.walk_stride, r.run_stride, r.run_speed, r.shadow = 0.8, 1.6, (0.1, 0.2), 0.15
    body = r.bone("body", (0, 21, 0), (-18, 0, 0))
    body.cube("torso", -2.5, -1.5, -2.5, 5, 3, 6)
    head = body.bone("head", (0, -0.5, -2.5), (14, 0, 0))
    head.cube("ohead", -2.5, -2, -3.5, 5, 3, 4)
    throat = head.bone("throat", (0, 1, -2))
    throat.cube("othroat", -1.5, -0.6, -1, 3, 1, 2, inflate=-0.1)

    def side(sd, nm, mir, sh):
        eye = head.bone("eye_" + nm, (sd * 1.6, -1.5, -2))
        eye.cube("oeye_" + nm, -1, -1, -1, 2, 1, 2, inflate=-0.15, mirror=mir, share=sh("oeye_l"))
        fl = body.bone("fleg_" + nm, (sd * 2, 1, -1.5), (-20, 0, sd * -10))
        fl.cube("ofleg_" + nm, -0.5, 0, -0.5, 1, 3, 1, mirror=mir, share=sh("ofleg_l"))
        # folded in a Z at the flank: the thigh forward and out from the hip, the shin back along it, the webbed foot
        # trailing behind
        hl = body.bone("hleg_" + nm, (sd * 2.2, 1, 2.5), (10, -sd * 28, 0))
        hl.cube("othigh_" + nm, -1, -1, -4, 2, 2, 4, mirror=mir, share=sh("othigh_l"))
        sn = hl.bone("shin_" + nm, (sd * 0.5, 0.5, -4), (0, -sd * 12, 0))
        sn.cube("oshin_" + nm, -0.5, -0.5, 0, 1, 1, 4, mirror=mir, share=sh("oshin_l"))
        sn.cube("hfoot_" + nm, -1, 0.5, 3, 2, 0, 3, mirror=mir, share=sh("hfoot_l"))
    sym(side)
    r.look = [("head", 1.0, 0.6)]
    r.pack()
    idle = clip_of(r, "idle", 4.0, loop=True)
    # the throat fluttering as it breathes
    cycle(idle, "throat", "scale", lambda p: (1, 1 + 0.6 * max(0, s(p * 8)), 1), 32)
    croak = clip_of(r, "croak", 1.6)
    croak.scale("throat", [(0, 1, 1, 1), (0.15, 1.5, 4.5, 1.6), (0.5, 1.5, 4.5, 1.6), (0.6, 1, 1, 1), (0.75, 1.5, 4.5, 1.6),
                           (1.1, 1.5, 4.5, 1.6), (1.2, 1, 1, 1), (1.6, 1, 1, 1)])
    croak.scale("body", [(0, 1, 1, 1), (0.15, 0.94, 0.9, 1), (0.5, 0.94, 0.9, 1), (0.6, 1, 1, 1), (0.75, 0.94, 0.9, 1),
                         (1.1, 0.94, 0.9, 1), (1.2, 1, 1, 1), (1.6, 1, 1, 1)])
    hop = clip_of(r, "hop", 0.5)
    hop.pos("body", [(0, 0, 0, 0), (0.1, 0, 2.5, -2), (0.25, 0, 4, -5), (0.4, 0, 1, -8), (0.5, 0, 0, -9)])
    hop.rot("body", [(0, 0, 0, 0), (0.1, -20, 0, 0), (0.3, 10, 0, 0), (0.5, 0, 0, 0)])
    # the kick: thighs swing back and the shins straighten out behind
    for nm, sg in (("hleg_l", 1), ("hleg_r", -1)):
        hop.rot(nm, [(0, 0, 0, 0), (0.08, 0, sg * 150, 0), (0.3, 0, sg * 140, 0), (0.5, 0, 0, 0)])
    for nm, sg in (("shin_l", 1), ("shin_r", -1)):
        hop.rot(nm, [(0, 0, 0, 0), (0.08, 0, -sg * 150, 0), (0.3, 0, -sg * 120, 0), (0.5, 0, 0, 0)])
    for nm in ("fleg_l", "fleg_r"):
        hop.rot(nm, [(0, 0, 0, 0), (0.1, -60, 0, 0), (0.35, 20, 0, 0), (0.5, 0, 0, 0)])
    stubs(r)
    return r


# =====================================================================================================================
# Paint
# =====================================================================================================================

def ramp(*h):
    return Ramp(*h)


def face_fill(P, cube, fname, fn):
    if not P.own(cube):
        return
    f = P.face(cube, fname)
    for i, j in f.each():
        col = fn(f, i, j)
        if col is not None:
            f.set(i, j, col)


def front_i(f):
    """On a side face, the pixel column nearest the bird's front (-z)."""
    return 0 if f.name == "west" else f.w - 1


def legs_feet(P, col, cubes=("oleg_l", "foot_l")):
    shade = Ramp(*["#%02x%02x%02x" % tuple(int(x * k) for x in col[:3]) for k in (0.5, 0.72, 0.9, 1.0)])
    for cb in cubes:
        if cb in P.cubes:
            P.paint(cb, shade, 2.0)


def eyes_on(P, cube, rows, col, fronts=1, glint=True):
    """Dark round eyes near the head's front, with a catch-light."""
    with P.eyes():
        for fname in ("west", "east"):
            f = P.face(cube, fname)
            ex = front_i(f) + (1 if fname == "west" else -1) * fronts
            for j in rows:
                f.set(ex, j, col)
            if glint:
                f.set(ex, rows[0], lerpc(col, hexc("#ffffff"), 0.7))


def wing_paint(P, mantle, prim, bars=None, tips=None, chord_dark=0.82):
    for cube, rp, base in (("arm_l", mantle, 3.0), ("prim_l", prim, 2.6)):
        P.paint(cube, rp, base, p_feathers(3, 3, 0.6))
    for fname in ("top", "bottom"):
        f = P.face("arm_l", fname)
        for i, j in f.each():
            if bars and j in bars[0]:
                f.set(i, j, bars[1])
            if fname == "bottom":
                f.set(i, j, mul(f.get(i, j), 1.1))
        f = P.face("prim_l", fname)
        for i, j in f.each():
            t = i / max(1, f.w - 1)
            col = f.get(i, j)
            if tips is not None and t > tips[0]:
                col = tips[1]
            if j == f.h - 1 or (i % 3 == 2 and j > 0):
                col = mul(col, chord_dark)
            f.set(i, j, col)


SONG = {
    "robin": dict(back=("#3b2d20", "#54402c", "#6d5639", "#86704b", "#9e8a62"), breast=("#9a3a14", "#c4521e", "#de6f2f", "#ec8a46"),
                  belly=("#bdb4a6", "#d8d1c4", "#ece7de"), cap=None, beak="#2a2422", leg="#8a6d5a"),
    "blue_tit": dict(back=("#4a5a2e", "#627340", "#7a8c4f", "#95a463"), breast=("#b89a1e", "#d8bc2c", "#ecd447", "#f6e47a"),
                     belly=("#c8b437", "#e0cc4c", "#efdc6a"), cap=("#1f4f8e", "#2f6db4", "#4a8ad0"), beak="#26282c", leg="#5e6a80"),
    "chaffinch": dict(back=("#4e3528", "#6a4734", "#865c44", "#a07454"), breast=("#a2655a", "#c07c6c", "#d69482", "#e6ad9a"),
                      belly=("#c9b8ac", "#ddd0c4", "#ece2d8"), cap=("#3e5266", "#56708a", "#7290aa"), beak="#8a8f9a", leg="#7a6656"),
    "wren": dict(back=("#3a2618", "#523624", "#6c4932", "#865e42", "#9e7552"), breast=("#8a6a4a", "#a8835e", "#c09c74", "#d4b28a"),
                 belly=("#a68866", "#bfa07c", "#d2b690"), cap=None, beak="#3a3028", leg="#9a7a62"),
    "blackbird": dict(back=("#0c0c0e", "#16161a", "#202026", "#2c2c34", "#3a3a44"), breast=("#101014", "#1a1a20", "#26262e", "#32323c"),
                      belly=("#14141a", "#1e1e26", "#2a2a32"), cap=None, beak="#e8a020", leg="#3a3026", eye_ring="#e0a428"),
}


def paint_songbird(r, P, v):
    d = SONG[v]
    back, breast, belly = Ramp(*d["back"]), Ramp(*d["breast"]), Ramp(*d["belly"])
    soft = combine(p_feathers(2, 2, 0.4), p_blotch(r.id + v, 4, 0.25))
    P.paint("torso", back, 2.8, soft)
    # the breast and belly: the front face and the underside (and the lower flanks)
    P.paint("breast", breast, 3.0, p_feathers(2, 2, 0.3))
    for fname in ("west", "east"):
        f = P.face("torso", fname)
        for i, j in f.each():
            fi = front_i(f)
            near = abs(i - fi)
            if j >= f.h - 2 or (near <= 1 and j >= 1):
                f.set(i, j, breast.at(2.6 - 0.2 * j) if near <= 1 else belly.at(2.2))
    face_fill(P, "torso", "bottom", lambda f, i, j: belly.at(2.0 + 0.3 * ((i + j) % 2)))
    P.paint("oneck", back, 3.0)
    for fname in ("north",):
        face_fill(P, "oneck", fname, lambda f, i, j: breast.at(3.0))
    P.paint("ohead", back, 3.2, p_feathers(2, 2, 0.3))
    # the face: robins wear their orange up over the forehead, tits a white cheek and a dark eye-stripe
    face_fill(P, "ohead", "north", lambda f, i, j: breast.at(3.0) if v in ("robin", "chaffinch") and j >= 1 else
              (hexc("#f2f2ee") if v == "blue_tit" and j >= 1 else None))
    for fname in ("west", "east"):
        f = P.face("ohead", fname)
        fi = front_i(f)
        for i, j in f.each():
            near = abs(i - fi)
            if v == "robin" and near <= 1 and j >= 1:
                f.set(i, j, breast.at(2.8))
            elif v == "blue_tit":
                if j == 0:
                    f.set(i, j, Ramp(*d["cap"]).at(2.4))
                elif j == 1:
                    f.set(i, j, hexc("#1c1e30"))
                elif j >= 2:
                    f.set(i, j, hexc("#eeeeea") if near <= 2 else hexc("#1c1e30") if j == f.h - 1 else hexc("#e6e6e0"))
            elif v == "chaffinch" and j <= 1:
                f.set(i, j, Ramp(*d["cap"]).at(2.6))
            elif v == "wren" and j == 1 and near >= 1:
                f.set(i, j, hexc("#d8c4a0"))  # the pale eyebrow
    if d["cap"]:
        face_fill(P, "ohead", "top", lambda f, i, j: Ramp(*d["cap"]).at(2.8 + 0.2 * (i % 2)))
    # the tail and wings
    P.paint("otail", back, 2.2, p_bands(3, 0.5) if v == "wren" else None)
    if v == "wren":
        face_fill(P, "torso", "top", lambda f, i, j: back.at(2.6 - 0.8 * (j % 3 == 0)))
    mantle = back if v != "blue_tit" else Ramp("#1f4f8e", "#2f6db4", "#4a8ad0", "#6aa2dc")
    bars = None
    if v == "chaffinch":
        bars = ({1, 3}, hexc("#f2efe6"))
    elif v == "blue_tit":
        bars = ({2}, hexc("#e8eef4"))
    wing_paint(P, mantle, back if v != "blue_tit" else Ramp("#183a6a", "#24548e", "#3a6ca8"), bars=bars)
    for fname in SIDES + ("top", "bottom"):
        face_fill(P, "obeak", fname, lambda f, i, j: hexc(d["beak"]))
    legs_feet(P, hexc(d["leg"]))
    with P.eyes():
        for fname in ("west", "east"):
            f = P.face("ohead", fname)
            ex = front_i(f) + (1 if fname == "west" else -1)
            f.set(ex, 1, hexc("#0a0a0c"))
            if v == "blackbird":
                f.set(ex, 0, hexc(d["eye_ring"]))
    return P.skin, P.glow


def p_bands(every, depth):
    def f(face, i, j):
        return -0.6 * depth if j % every == every - 1 else 0.0
    return f


def paint_woodpecker(r, P, v):
    green = v == "green"
    if green:
        back = Ramp("#2f4a18", "#44652a", "#5a823a", "#73a04c", "#8eba62")
        under = Ramp("#7c8a5a", "#98a670", "#b2bf88")
    else:
        back = Ramp("#0c0c10", "#16161c", "#222228", "#2e2e36")
        under = Ramp("#b8b2a6", "#d2ccc0", "#e6e2d8", "#f2f0ea")
    P.paint("torso", back, 2.6, p_feathers(2, 2, 0.3))
    for fname in ("west", "east"):
        f = P.face("torso", fname)
        fi = front_i(f)
        for i, j in f.each():
            if j >= f.h - 2:
                f.set(i, j, under.at(2.4))
            if not green and 2 <= abs(i - fi) <= 3 and 1 <= j <= 2:
                f.set(i, j, hexc("#f4f2ec"))  # the white shoulder patch
    face_fill(P, "torso", "bottom", lambda f, i, j: (hexc("#c8202a") if j >= f.h - 2 and not green else under.at(2.2)))
    P.paint("breast", under, 2.8)
    P.paint("oneck", under if not green else back, 2.8)
    P.paint("ohead", back if green else under, 3.0)
    face_fill(P, "ohead", "top", lambda f, i, j: hexc("#c41e26") if (green or j <= 1) else hexc("#14141a"))
    for fname in ("west", "east"):
        f = P.face("ohead", fname)
        fi = front_i(f)
        for i, j in f.each():
            near = abs(i - fi)
            if green:
                if j == 0:
                    f.set(i, j, hexc("#c41e26"))
                elif j == 2 and near <= 2:
                    f.set(i, j, hexc("#141414"))
            else:
                if j == 0:
                    f.set(i, j, hexc("#14141a"))
                elif j == 2 and near >= 1:
                    f.set(i, j, hexc("#14141a"))  # the moustache stripe
    P.paint("otail", back, 2.0)
    wing_paint(P, back, back, bars=None if green else ({1, 3}, hexc("#efece4")))
    for fname in SIDES + ("top", "bottom"):
        face_fill(P, "obeak", fname, lambda f, i, j: hexc("#4a4a50") if i < f.w - 1 else hexc("#2a2a30"))
    legs_feet(P, hexc("#6a6a72"))
    with P.eyes():
        for fname in ("west", "east"):
            f = P.face("ohead", fname)
            ex = front_i(f) + (1 if fname == "west" else -1)
            f.set(ex, 1, hexc("#1a0e0e") if not green else hexc("#e8e6e0"))
    return P.skin, P.glow


def paint_crow(r, P, v):
    black = Ramp("#07070a", "#101016", "#1a1a22", "#262630", "#353542", "#46465a")
    sheen = combine(p_feathers(3, 3, 0.6), p_blotch(r.id, 6, 0.3))
    for cb in ("torso", "breast", "oneck", "ohead"):
        P.paint(cb, black, 2.4, sheen)
    # a faint blue-violet gloss along the back
    face_fill(P, "torso", "top", lambda f, i, j: lerpc(f.get(i, j), hexc("#3a3e66"), 0.25) if (i + j) % 3 == 0 else None)
    P.paint("otail", black, 2.0, p_bands(3, 0.4))
    wing_paint(P, black, black)
    for fname in SIDES + ("top", "bottom"):
        face_fill(P, "obeak", fname, lambda f, i, j: hexc("#121216") if j > 0 else hexc("#24242c"))
    legs_feet(P, hexc("#1c1c22"))
    eyes_on(P, "ohead", (1,), hexc("#2c1e14"))
    return P.skin, P.glow


OWL = {"tawny": dict(base=("#3e2414", "#5a361e", "#7a4c2a", "#98653a", "#b4804e", "#ccA06e"), disc=("#8a6440", "#a87e54", "#c49a6c"),
                     eye="#0e0a08", shine="#ffb050"),
       "barn": dict(base=("#6a4a2a", "#8c6a40", "#ae8a56", "#c6a46c", "#d8bc86", "#e8d2a6"), disc=("#e4dccc", "#f2ece0", "#faf8f2"),
                    eye="#0c0a0a", shine="#ffb870")}


def paint_owl(r, P, v):
    d = OWL[v]
    base = Ramp(*d["base"])
    under = Ramp("#d8cab0", "#ece2cc", "#f8f4ea") if v == "barn" else Ramp("#9a7652", "#b48e66", "#caa67e")
    streak = combine(p_feathers(2, 3, 0.5), p_blotch(r.id + v, 4, 0.5))
    P.paint("torso", base, 3.0, streak)
    P.paint("breast", under, 2.6, p_bands(2, 0.6))
    for fname in ("west", "east"):
        f = P.face("torso", fname)
        fi = front_i(f)
        for i, j in f.each():
            if abs(i - fi) <= 2:
                f.set(i, j, under.at(2.4 - 0.5 * ((i + j) % 3 == 0)))
    P.paint("oneck", base, 3.0)
    P.paint("ohead", base, 3.2, streak)
    disc = Ramp(*d["disc"])
    # the facial disc: a pale heart with a dark rim, the eyes deep in it
    for fname in ("north", "south", "top", "bottom", "west", "east"):
        face_fill(P, "disc", fname, lambda f, i, j: disc.at(2.0 + 0.4 * (abs(i - f.w / 2 + 0.5) < 1)) if 0 < i < f.w - 1 and j < f.h - 1
                  else mul(base.at(1.6), 0.9))
    face_fill(P, "ohead", "north", lambda f, i, j: disc.at(2.0))
    P.paint("otail", base, 2.6, p_bands(2, 0.6))
    wing_paint(P, base, base, bars=({1, 4}, base.at(4.4)))
    for fname in SIDES + ("top", "bottom"):
        face_fill(P, "obeak", fname, lambda f, i, j: hexc("#c8b48a") if v == "barn" else hexc("#d0c090"))
    P.paint("oleg_l", under, 2.4)
    for fname in ("top", "bottom"):
        face_fill(P, "foot_l", fname, lambda f, i, j: hexc("#5a5048"))
    P.paint("otuft_l", base, 2.6)
    with P.eyes():
        f = P.face("disc", "north")
        for ex in (1, 2, f.w - 3, f.w - 2):
            for ey in (1, 2):
                f.set(ex, ey, hexc(d["eye"]))
        for ex in (1, f.w - 3):
            f.set(ex, 1, lerpc(hexc(d["eye"]), hexc("#ffffff"), 0.45))
    # the eyes catch light in the dark (the glow layer, by darkness)
    f = P.face("disc", "north", glow=True)
    for ex in (2, f.w - 2):
        f.set(ex, 2, hexc(d["shine"], 190))
    return P.skin, P.glow


RAPTOR = {"buzzard": dict(back=("#2e1e12", "#46301c", "#604428", "#7a5a38", "#94724a"), under=("#8a6a48", "#b49470", "#d6be9a", "#ece0c8"),
                          tip="#1a120c"),
          "kestrel": dict(back=("#6a2c12", "#8c3e1a", "#ac5526", "#c46c34", "#d88446"), under=("#b48a5a", "#d4aa76", "#e8c896", "#f4e0b8"),
                          tip="#1c140e")}


def paint_raptor(r, P, v):
    d = RAPTOR[v]
    back, under = Ramp(*d["back"]), Ramp(*d["under"])
    spots = combine(p_feathers(3, 3, 0.6), p_blotch(r.id + v, 5, 0.4))
    P.paint("torso", back, 2.8, spots)
    face_fill(P, "torso", "bottom", lambda f, i, j: under.at(2.4 - 0.9 * ((i * 3 + j * 5) % 7 == 0)))
    P.paint("breast", under, 2.6, p_bands(2, 0.7) if v == "buzzard" else None)
    P.paint("oneck", under if v == "buzzard" else back, 2.8)
    head = back if v == "buzzard" else Ramp("#4a5462", "#5e6a7a", "#76828f", "#8e9aa6")
    P.paint("ohead", head, 3.0)
    tail = back if v == "buzzard" else Ramp("#4a5462", "#5e6a7a", "#76828f", "#8e9aa6")
    P.paint("otail", tail, 2.6, p_bands(2, 0.7))
    face_fill(P, "otail", "top", lambda f, i, j: hexc("#141414") if j == f.h - 2 else None)
    # the underwing: pale with dark fingertips and the buzzard's dark wrist patch; the fingers are gaps
    for cube in ("arm_l", "prim_l"):
        P.paint(cube, back, 2.6, p_feathers(3, 3, 0.6))
    f = P.face("arm_l", "bottom")
    for i, j in f.each():
        f.set(i, j, under.at(2.6 - 0.8 * (i == f.w - 1 and v == "buzzard")))
    for fname in ("top", "bottom"):
        f = P.face("prim_l", fname)
        for i, j in f.each():
            t = i / max(1, f.w - 1)
            if fname == "bottom":
                col = under.at(2.2) if t < 0.55 else hexc(d["tip"])
                if t < 0.2 and v == "buzzard":
                    col = back.at(1.4)
            else:
                col = back.at(2.4) if t < 0.55 else hexc(d["tip"])
            if t > 0.7 and j % 2 == 1 and j < f.h - 0:
                col = (0, 0, 0, 0)  # the fingered primaries: light between the feathers
            f.set(i, j, col)
    for fname in SIDES + ("top", "bottom"):
        face_fill(P, "obeak", fname, lambda f, i, j: hexc("#e0b030") if i == 0 and fname != "north" else hexc("#2a2622"))
    face_fill(P, "hook", "north", lambda f, i, j: hexc("#1e1a18"))
    legs_feet(P, hexc("#e2b032"))
    eyes_on(P, "ohead", (1,), hexc("#3a1e0c") if v == "buzzard" else hexc("#160e0a"))
    return P.skin, P.glow


def paint_gull(r, P, v):
    rid = r.id
    r.id = "city_gull"
    try:
        return CF.paint_bird(r, P, "gull")
    finally:
        r.id = rid


DUCK = {"drake": dict(head=("#0e3a22", "#14512e", "#1e6a3c", "#2e8650"), breast=("#4a2618", "#5e3220", "#74402a"),
                      body=("#8a8a86", "#a6a6a2", "#c0bfba", "#d8d6d0"), back=("#5a5248", "#6e665a", "#867c6e"), bill="#d8c440"),
        "hen": dict(head=("#4a3424", "#64462e", "#7e5a3c", "#987050"), breast=("#5a3e26", "#74522f", "#8e683e"),
                    body=("#4c3422", "#68482e", "#86603c", "#a27a50"), back=("#3e2a1a", "#5a3e26", "#745234"), bill="#b8742a")}


def paint_duck(r, P, v):
    d = DUCK[v]
    head, breast, body, back = Ramp(*d["head"]), Ramp(*d["breast"]), Ramp(*d["body"]), Ramp(*d["back"])
    scal = combine(p_feathers(3, 2, 0.5), p_blotch(r.id + v, 5, 0.35))
    P.paint("torso", body, 2.6, scal if v == "hen" else p_bands(2, 0.25))
    face_fill(P, "torso", "top", lambda f, i, j: back.at(2.4 + 0.3 * ((i + j) % 2)))
    P.paint("breast", breast, 2.6, scal if v == "hen" else None)
    P.paint("oneck", head, 3.0)
    P.paint("ohead", head, 3.2)
    if v == "drake":
        # the white collar and the iridescence on the head
        face_fill(P, "oneck", "north", lambda f, i, j: hexc("#f2f0ea") if j == f.h - 1 else None)
        for fname in ("west", "east", "south"):
            face_fill(P, "oneck", fname, lambda f, i, j: hexc("#f2f0ea") if j == f.h - 1 else None)
        face_fill(P, "ohead", "top", lambda f, i, j: lerpc(f.get(i, j), hexc("#3a2a6a"), 0.3) if (i + j) % 3 == 0 else None)
    else:
        for fname in ("west", "east"):
            f = P.face("ohead", fname)
            for i, j in f.each():
                if j == 1:
                    f.set(i, j, hexc("#2e1e12"))  # the dark eye-stripe
    P.paint("otail", back if v == "hen" else Ramp("#d8d6d0", "#eceae4", "#f8f6f2"), 2.4)
    P.paint("curl", Ramp("#0c0c10", "#16161c", "#202028"), 2.0)
    wing_paint(P, back, back)
    # the speculum: a band of violet-blue on the folded wing, edged white
    for fname in ("top",):
        f = P.face("arm_l", fname)
        for i, j in f.each():
            if i >= f.w - 2:
                f.set(i, j, hexc("#2e3ea0") if j < f.h - 1 else hexc("#f2f2f2"))
    for fname in SIDES + ("top", "bottom"):
        face_fill(P, "obeak", fname, lambda f, i, j: hexc(d["bill"]) if i < f.w - 1 or fname in ("north", "south") else mul(hexc(d["bill"]), 0.7))
    legs_feet(P, hexc("#e88a28"))
    eyes_on(P, "ohead", (1,), hexc("#140c08"))
    return P.skin, P.glow


def paint_heron(r, P, v):
    grey = Ramp("#4a525c", "#5e6772", "#76808a", "#8e98a2", "#a8b0b8", "#c4cad0")
    white = Ramp("#b8bcc0", "#d2d6d8", "#e6e8ea", "#f4f5f6")
    P.paint("torso", grey, 3.0, p_feathers(3, 3, 0.4))
    P.paint("breast", white, 2.4, p_bands(2, 0.4))
    for cb in ("oneck1", "oneck2"):
        P.paint(cb, white, 2.6)
        face_fill(P, cb, "north", lambda f, i, j: hexc("#3a3c44") if j % 3 == 0 and i == 0 else None)  # the dark streaks down the throat
    P.paint("ohead", white, 2.8)
    for fname in ("west", "east"):
        f = P.face("ohead", fname)
        for i, j in f.each():
            if j == 0:
                f.set(i, j, hexc("#14161a"))  # the black brow running back into the crest
    P.paint("oplume", Ramp("#0e1014", "#16181e", "#20232a"), 2.0)
    P.paint("otail", grey, 2.4)
    wing_paint(P, grey, Ramp("#16181e", "#22252c", "#30343c", "#3e434c"))
    for fname in SIDES + ("top", "bottom"):
        face_fill(P, "obeak", fname, lambda f, i, j: hexc("#d8b03a") if i > 1 else hexc("#b88a2a"))
    legs_feet(P, hexc("#9a8a5a"), ("oleg_l", "oshin_l", "foot_l"))
    eyes_on(P, "ohead", (1,), hexc("#e8d040"), glint=False)
    return P.skin, P.glow


SQUIRREL = {"red": dict(base=("#5a2410", "#7a3416", "#9e481e", "#bc5e28", "#d27838", "#e4964e"), belly=("#d8ccb4", "#ece2cc", "#f8f2e6")),
            "grey": dict(base=("#3c3a38", "#56524e", "#726c66", "#8e8780", "#a8a098", "#c2bab0"), belly=("#d8d4cc", "#ece8e0", "#f8f6f2"))}


def paint_squirrel(r, P, v):
    d = SQUIRREL[v]
    base, belly = Ramp(*d["base"]), Ramp(*d["belly"])
    fur = combine(p_fur(0.3, 2, 3, 2), p_blotch(r.id + v, 4, 0.25))
    for cb in ("torso", "oneck", "ohead", "nose", "othigh_l", "ofleg_l", "hfoot_l", "oear_l"):
        P.paint(cb, base, 3.0, fur)
    face_fill(P, "torso", "bottom", lambda f, i, j: belly.at(2.2))
    for fname in ("west", "east"):
        face_fill(P, "torso", fname, lambda f, i, j: belly.at(2.0) if j >= f.h - 1 else None)
    face_fill(P, "oneck", "north", lambda f, i, j: belly.at(2.4))
    face_fill(P, "nose", "north", lambda f, i, j: hexc("#2a1a14") if j == 0 and i in (0, f.w - 1) else None)
    tail = Ramp(*d["base"])
    for cb in ("otail1", "otail2", "otail3"):
        P.paint(cb, tail, 3.2, combine(p_fur(1.0, 2, 5, 2), p_blotch(cb + v, 3, 0.4)))
    P.paint("tuft_l", base, 2.0 if v == "red" else 3.5)
    with P.eyes():
        for fname in ("west", "east"):
            f = P.face("ohead", fname)
            ex = front_i(f) + (1 if fname == "west" else -1)
            f.set(ex, 1, hexc("#0a0806"))
            f.set(ex, 2, hexc("#140e0a"))
            f.set(ex + (1 if fname == "west" else -1), 1, belly.at(2.6))  # the pale eye-ring
    return P.skin, P.glow


RABBIT = {"brown": dict(base=("#3e2c1c", "#58402a", "#74563a", "#8e6e4c", "#a8885e", "#c0a074")),
          "grey": dict(base=("#3a3632", "#524c46", "#6c655c", "#867e74", "#a0968a", "#b8aea0"))}


def paint_rabbit(r, P, v):
    base = Ramp(*RABBIT[v]["base"])
    belly = Ramp("#c8bca8", "#ded4c2", "#eee8dc")
    fur = combine(p_fur(0.5, 2, 3, 2), p_blotch(r.id + v, 4, 0.3))
    for cb in ("torso", "haunch", "oneck", "ohead", "muzzle", "othigh_l", "ofleg_l", "hfoot_l"):
        P.paint(cb, base, 3.0, fur)
    face_fill(P, "torso", "bottom", lambda f, i, j: belly.at(2.2))
    face_fill(P, "haunch", "bottom", lambda f, i, j: belly.at(2.2))
    P.paint("oear_l", base, 3.0)
    P.paint("oeartip_l", Ramp("#16120e", "#221a14", "#2e241c"), 2.0)
    face_fill(P, "oear_l", "north", lambda f, i, j: hexc("#c89a8a") if 0 < j < f.h - 1 else None)  # the ear's inside
    P.paint("otail", Ramp("#e0dcd4", "#f0ece6", "#fbfaf8"), 2.4)
    face_fill(P, "muzzle", "north", lambda f, i, j: hexc("#e0d6c8") if j == f.h - 1 else (hexc("#5a3a30") if j == 0 and i == f.w // 2 else None))
    with P.eyes():
        for fname in ("west", "east"):
            f = P.face("ohead", fname)
            ex = front_i(f) + (1 if fname == "west" else -1) * 2
            f.set(ex, 1, hexc("#0c0806"))
            f.set(ex, 2, hexc("#1a120c"))
    return P.skin, P.glow


def paint_fox(r, P, v):
    base = Ramp("#5a200a", "#7e2e10", "#a23e16", "#c25220", "#d8682c", "#e8843e")
    white = Ramp("#c8c0b4", "#e0dad0", "#f2eee8", "#fbfaf8")
    dark = Ramp("#120c0a", "#1c1410", "#2a1e18")
    fur = combine(p_fur(0.22, 2, 4, 3), p_blotch(r.id, 5, 0.3))
    for cb in ("torso", "ruff", "oneck", "ohead", "snout", "othigh_l", "oear_l"):
        P.paint(cb, base, 3.0, fur)
    face_fill(P, "torso", "bottom", lambda f, i, j: white.at(2.0))
    face_fill(P, "ruff", "north", lambda f, i, j: white.at(2.6))
    face_fill(P, "oneck", "north", lambda f, i, j: white.at(2.6))
    face_fill(P, "snout", "bottom", lambda f, i, j: white.at(2.4))
    for fname in ("west", "east"):
        face_fill(P, "snout", fname, lambda f, i, j: white.at(2.4) if j >= f.h - 1 else None)
        face_fill(P, "ohead", fname, lambda f, i, j: white.at(2.4) if j >= f.h - 1 and abs(i - front_i(f)) <= 1 else None)
    P.paint("nose", dark, 1.6)
    P.paint("ojaw", white, 2.0)
    for cb in ("ofleg_l", "ohock_l", "oeartip_l"):
        P.paint(cb, dark, 2.0)
    face_fill(P, "oear_l", "north", lambda f, i, j: white.at(2.2) if 0 < i < f.w - 1 and j > 0 else None)
    tailc = combine(p_fur(0.45, 2, 4, 3), p_blotch("foxtail", 4, 0.3))
    P.paint("otail1", base, 3.0, tailc)
    P.paint("otail2", base, 3.1, tailc)
    P.paint("otail3", white, 2.8)
    with P.eyes():
        for fname in ("west", "east"):
            f = P.face("ohead", fname)
            ex = front_i(f) + (1 if fname == "west" else -1)
            f.set(ex, 1, hexc("#c89a1e"))
            f.set(ex + (1 if fname == "west" else -1), 1, hexc("#0e0806"))
    # a fox's eyes shine green-gold at night
    for fname in ("west", "east"):
        f = P.face("ohead", fname, glow=True)
        f.set(front_i(f) + (1 if fname == "west" else -1), 1, hexc("#d8f070", 210))
    return P.skin, P.glow


FROG = {"green": dict(base=("#24381a", "#344e22", "#48662c", "#5e7e38", "#7a9a4a"), belly=("#c8c09a", "#ddd6b2", "#ece6c8")),
        "brown": dict(base=("#3a2a18", "#523c22", "#6a502e", "#82663c", "#9a7e4e"), belly=("#d0bc96", "#e2d2b0", "#efe4cc"))}


def paint_frog(r, P, v):
    d = FROG[v]
    base, belly = Ramp(*d["base"]), Ramp(*d["belly"])
    skin = combine(p_blotch(r.id + v, 3, 0.9, 3), p_feathers(2, 2, 0.2))
    for cb in ("torso", "ohead", "othigh_l", "oshin_l", "ofleg_l", "oeye_l"):
        P.paint(cb, base, 3.0, skin)
    for cb in ("torso", "ohead"):
        face_fill(P, cb, "bottom", lambda f, i, j: belly.at(2.4))
    P.paint("othroat", belly, 2.8)
    face_fill(P, "hfoot_l", "top", lambda f, i, j: base.at(2.2))
    face_fill(P, "hfoot_l", "bottom", lambda f, i, j: base.at(1.6))
    for fname in ("west", "east"):
        face_fill(P, "ohead", fname, lambda f, i, j: hexc("#20160e") if j == 1 and abs(i - front_i(f)) >= 1 else None)  # the mask
    with P.eyes():
        for fname in ("west", "east", "north"):
            f = P.face("oeye_l", fname)
            f.set(f.w // 2, 0, hexc("#c8a038"))
            f.set(max(0, f.w // 2 - 1), 0, hexc("#0c0a06"))
    return P.skin, P.glow


# =====================================================================================================================
# The insects' sheet (insects.png, 64 x 64): 16 x 16 cells, hand-placed pixels. The client draws each insect from
# a few quads cut from it (wildlife.client.InsectRender keeps the cells' coordinates).
#   row 0: butterfly wings (right pair, the hinge on the cell's left edge, the head toward the top): white, brimstone,
#          peacock, common blue
#   row 1: moth wing, dragonfly wings (fore and hind), bee (body seen from above, left half; wing, right half), firefly
#   row 2: soft glow disc, ripple ring, water strider, gnat speck (a 4 x 4 in the corner)
#   row 3: dragonfly body (blue, green), butterfly body, far bird (a wing, the body)
# =====================================================================================================================

def _wing(cv, ox, oy, fore, hind, vein, edge, spots=(), fw=11, hw=8):
    """A butterfly's right fore- and hindwing: the hinge at x = ox, the forewing above, the hindwing below."""
    for y in range(16):
        for x in range(16):
            u, v = x, y
            # forewing: a rounded triangle from the hinge out to the apex, rows 1..8
            if 1 <= v <= 8:
                reach = fw * (0.35 + 0.65 * math.sin(math.pi * (v - 0.5) / 9.0) ** 0.6) - (1 if v == 1 else 0)
                if u < reach:
                    col = fore
                    if u >= reach - 1.2:
                        col = edge
                    elif u % 3 == 2 and v > 2:
                        col = vein
                    cv.set(ox + u, oy + v, col)
            # hindwing: smaller and round, rows 8..14
            if 8 <= v <= 14:
                reach = hw * math.sin(math.pi * (v - 7.5) / 7.5) ** 0.7
                if u < reach:
                    col = hind
                    if u >= reach - 1.1:
                        col = edge
                    elif u % 3 == 1 and v > 9:
                        col = vein
                    cv.set(ox + u, oy + v, col)
    for (sx, sy, col) in spots:
        cv.set(ox + sx, oy + sy, col)


def insects():
    cv = Canvas(64, 64)
    H = hexc
    # row 0: butterflies
    _wing(cv, 0, 0, H("#f2f0e6"), H("#ece8d8"), H("#d8d4c4"), H("#5a5a5a"), spots=[(6, 4, H("#2a2a2a")), (5, 9, H("#2a2a2a")), (7, 1, H("#3a3a3a")), (8, 2, H("#3a3a3a"))])
    _wing(cv, 16, 0, H("#f4e04a"), H("#ecd640"), H("#d8c034"), H("#c8a830"), spots=[(5, 5, H("#e07a1a")), (4, 11, H("#e07a1a"))])
    _wing(cv, 32, 0, H("#9a2a1a"), H("#7a2216"), H("#6a1c12"), H("#2a1a14"),
          spots=[(7, 2, H("#1a3a8a")), (8, 2, H("#e8e0a0")), (8, 3, H("#1a3a8a")), (4, 11, H("#1a1a3a")), (5, 11, H("#4a7ac8")),
                 (5, 12, H("#e8e8f0")), (3, 5, H("#e8d8a0")), (2, 6, H("#e8d8a0"))])
    _wing(cv, 48, 0, H("#6a8ae0"), H("#5a7ad0"), H("#4a68b8"), H("#e8ecf4"), spots=[(2, 3, H("#8aa8f0")), (3, 10, H("#e8a040"))])
    # row 1: moth, dragonfly wings, bee, firefly
    _wing(cv, 0, 16, H("#8a7a62"), H("#a8987e"), H("#6a5c48"), H("#5a4c3a"), spots=[(4, 4, H("#4a3e30")), (6, 6, H("#4a3e30"))], fw=12, hw=6)
    for y in range(16):
        for x in range(16):
            # two long narrow clear wings with dark veins and a pterostigma near the tip
            fore = 2 <= y <= 5 and x < 15 - abs(y - 3.5) * 1.2
            hind = 8 <= y <= 12 and x < 14 - abs(y - 10) * 1.0
            if fore or hind:
                col = (200, 220, 230, 110)
                if x % 4 == 3 or y in (2, 8):
                    col = (60, 70, 80, 170)
                if x in (12, 13) and y in (3, 9):
                    col = (40, 40, 50, 230)
                cv.set(16 + x, 16 + y, col)
    for y in range(4, 13):  # the bee's body from above: a fuzzy amber and black barrel
        for x in range(2, 7):
            stripe = (y - 4) % 3 == 2
            col = H("#1a1410") if stripe and y > 6 else H("#d8a020") if y > 6 else H("#2a2016")
            if x in (2, 6):
                col = mul(col, 0.75)
            cv.set(32 + x, 16 + y, col)
    for y in range(3, 10):  # the bee's wing
        for x in range(9, 15):
            if (x - 12) ** 2 / 9 + (y - 6) ** 2 / 12 <= 1:
                cv.set(32 + x, 16 + y, (230, 236, 240, 120) if (x + y) % 3 else (120, 130, 140, 170))
    for y in range(5, 12):  # firefly body
        for x in range(6, 10):
            col = H("#1a1612") if y < 9 else H("#e8f080")
            if y == 5:
                col = H("#c0402a")
            cv.set(48 + x, 16 + y, col)
    # row 2: glow disc, ripple ring, strider, gnat
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5) / 7.5
            if d < 1:
                a = int(255 * (1 - d) ** 2.2)
                cv.set(x, 32 + y, (255, 255, 255, a))
            if 0.78 < d < 1.0:
                cv.set(16 + x, 32 + y, (255, 255, 255, int(200 * (1 - abs(d - 0.89) / 0.11))))
    for (x, y) in [(7, 4), (7, 5), (7, 6), (7, 7), (7, 8), (8, 5), (8, 6), (8, 7)]:
        cv.set(32 + x, 32 + y, H("#2a241e"))
    for leg in [((7, 5), (2, 1)), ((8, 5), (13, 1)), ((7, 7), (1, 9)), ((8, 7), (14, 9)), ((7, 8), (4, 14)), ((8, 8), (11, 14))]:
        (x0, y0), (x1, y1) = leg
        n = max(abs(x1 - x0), abs(y1 - y0))
        for k in range(n + 1):
            cv.set(32 + round(x0 + (x1 - x0) * k / n), 32 + round(y0 + (y1 - y0) * k / n), H("#3a3028"))
    for (x, y) in [(1, 1), (2, 1), (1, 2), (2, 2)]:
        cv.set(48 + x, 32 + y, H("#1a1a16"))
    # row 3: dragonfly bodies, butterfly body, far bird
    for k, (a, b) in enumerate(((H("#1e5ac8"), H("#0e2a5a")), (H("#3aa060"), H("#164a28")))):
        for y in range(1, 16):
            for x in range(6 + 0, 10):
                w_ = 2 if y > 5 else 4 if y > 1 else 3
                if abs(x - 7.5) < w_ / 2 + 0.01:
                    col = a if (y % 3 != 0 or y < 5) else b
                    if y <= 2:
                        col = H("#2a6a50") if k else H("#3a5a9a")
                    cv.set(k * 8 + x - 4, 48 + y, col)
    for y in range(2, 14):
        for x in range(7, 9):
            cv.set(16 + x, 48 + y, H("#2a2420") if y > 2 else H("#1a1612"))
    for y in range(16):
        for x in range(16):
            if x < 12 and 5 <= y <= 9 and y - 5 <= 4 - x * 0.25 + 2:
                cv.set(32 + x, 48 + y, H("#22201e") if x < 10 else H("#16141a"))
    for y in range(4, 12):
        for x in range(6, 10):
            cv.set(48 + x, 48 + y, H("#1e1c1c"))
    return cv


# =====================================================================================================================

VARIANTS = {"wild_songbird": ["robin", "robin", "blue_tit", "blue_tit", "chaffinch", "chaffinch", "wren", "blackbird"],
            "wild_woodpecker": ["great_spotted", "great_spotted", "green"],
            "wild_crow": ["crow"], "wild_owl": ["tawny", "tawny", "barn"], "wild_raptor": ["buzzard", "kestrel"],
            "wild_gull": ["gull"], "wild_duck": ["drake", "hen"], "wild_heron": ["grey"],
            "wild_squirrel": ["red", "grey"], "wild_rabbit": ["brown", "brown", "grey"], "wild_fox": ["red"],
            "wild_frog": ["green", "brown"]}
DESIGNS = {"wild_songbird": (songbird, paint_songbird), "wild_woodpecker": (woodpecker, paint_woodpecker),
           "wild_crow": (crow, paint_crow), "wild_owl": (owl, paint_owl), "wild_raptor": (raptor, paint_raptor),
           "wild_gull": (gull, paint_gull), "wild_duck": (duck, paint_duck), "wild_heron": (heron, paint_heron),
           "wild_squirrel": (squirrel, paint_squirrel), "wild_rabbit": (rabbit, paint_rabbit), "wild_fox": (fox, paint_fox),
           "wild_frog": (frog, paint_frog)}


def rigs(only=None):
    out = []
    for rid, (design, painter) in DESIGNS.items():
        if only and rid not in only:
            continue
        out.append((rid, design(), painter))
    return out


def textures(rig, painter):
    out, seen = [], set()
    for v in VARIANTS[rig.id]:
        if v in seen:
            continue
        seen.add(v)
        P = R.Painter(rig, "wild_fauna|" + rig.id + "|" + v)
        skin, glow = painter(rig, P, v)
        blink = P.blink()
        name = rig.id if len(set(VARIANTS[rig.id])) == 1 else rig.id + "_" + v
        out.append((name, skin, glow, blink))
    return out


def java(all_rigs):
    chunks = []
    for rid, rig, _ in all_rigs:
        for cl in rig.clips.values():
            for (bone, _), keys in cl.tracks.items():
                if bone not in rig.by_name:
                    raise SystemExit(f"{rid}/{cl.name}: no bone {bone}")
        chunks.append((rid, rig.java_lines()))
    src = ["package dev.awaken.wildlife.client;", "", "import java.util.List;", "", "/**",
           " * GENERATED by tools/art/specs/wild_fauna.py: do not edit by hand. The wild's small life as plain data, in the",
           " * hero rigs' format ({@link dev.awaken.mobs.client.anim.HeroRig#parse}); textures in textures/entity/wild/.",
           " */", "public final class WildFaunaData {", "    private WildFaunaData() {}", "",
           "    /** Each animal's variants (texture suffixes, weighted by repetition). */",
           "    public static final java.util.Map<String, List<String>> VARIANTS = java.util.Map.ofEntries("]
    vs = [f'            java.util.Map.entry("{rid}", List.of({", ".join(chr(34) + (v if len(set(VARIANTS[rid])) > 1 else "") + chr(34) for v in VARIANTS[rid])}))'
          for rid, _, _ in all_rigs]
    src.append(",\n".join(vs))
    src.append("    );")
    src.append("")
    src.append("    /** Render scale per animal (designed larger than it stands). */")
    src.append("    public static final java.util.Map<String, Float> SCALE = java.util.Map.ofEntries(")
    src.append(",\n".join(f'            java.util.Map.entry("{rid}", {SCALE[rid]}F)' for rid, _, _ in all_rigs))
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
    os.makedirs(os.path.dirname(JAVA_OUT), exist_ok=True)
    with open(JAVA_OUT, "w") as f:
        f.write("\n".join(src) + "\n")
    return JAVA_OUT


def build(out):
    all_rigs = rigs()
    n = 0
    for rid, rig, painter in all_rigs:
        for name, skin, glow, blink in textures(rig, painter):
            out.entity("wild/" + name, skin)
            if any(px[3] for row in glow.px for px in row):
                out.entity("wild/" + name + "_glow", glow)
            if blink is not None:
                out.entity("wild/" + name + "_blink", blink[0])
            n += 1
    out.entity("wild/insects", insects())
    path = java(all_rigs)
    print(f"wild_fauna: {len(all_rigs)} rigs, {n} textures + insects -> {os.path.relpath(path, ROOT)}")


# =====================================================================================================================
# Preview

BG_DAY = (110, 128, 140, 255)


def preview(outdir, only=None):
    from PIL import Image, ImageDraw
    os.makedirs(outdir, exist_ok=True)
    made = []
    saved = dict(CF.SCALE)
    CF.SCALE.update(SCALE)
    try:
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
            pc = (170, 160)
            px = min((pc[0] - 16) / max(1e-6, b[1] - b[0]), (pc[1] - 24) / max(1e-6, b[3] - b[2]))
            per = 8
            nrow = (len(poses) + per - 1) // per
            strip = Image.new("RGBA", (max(pc[0] * per, cell[0] * 4), (pc[1] + 16) * nrow), (40, 44, 54, 255))
            d3 = ImageDraw.Draw(strip)
            for k, ((label, _), t) in enumerate(zip(poses, trees)):
                im = R.render(t, lit, yaw=yaw, pitch=pitch, px=px, bounds=b,
                              origin=(pc[0], pc[1], (pc[0] - (b[1] - b[0]) * px) / 2 - b[0] * px, pc[1] - 8 - b[3] * px))
                bg = Image.new("RGBA", pc, BG_DAY)
                bg.alpha_composite(im)
                x0, y0 = (k % per) * pc[0], (k // per) * (pc[1] + 16)
                strip.paste(bg, (x0, y0))
                d3.text((x0 + 4, y0 + pc[1] + 2), label, fill=(220, 225, 235, 255))
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
            Hh = sum(r_.height for r_ in rows) + strip.height + line.height
            sheet = Image.new("RGBA", (Wt, Hh), (24, 26, 34, 255))
            y = 0
            for r_ in rows + [line, strip]:
                sheet.paste(r_, (0, y))
                y += r_.height
            path = os.path.join(outdir, f"{rid}.png")
            sheet.save(path)
            made.append(path)
        if not only or "insects" in only:
            im = insects().image().resize((512, 512), Image.NEAREST)
            bg = Image.new("RGBA", im.size, (90, 120, 80, 255))
            bg.alpha_composite(im)
            path = os.path.join(outdir, "insects.png")
            bg.save(path)
            made.append(path)
    finally:
        CF.SCALE.clear()
        CF.SCALE.update(saved)
    return made


if __name__ == "__main__":
    if len(sys.argv) > 2 and sys.argv[1] == "preview":
        for p in preview(sys.argv[2], sys.argv[3:] or None):
            print(p)
