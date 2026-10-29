"""The kin (SPEC §43.5): the land's own beasts that can live with you. Stage 2 brings four, each with its own look:

  kin_lampwing   a moth the size of a cat, a fluffy ruff, feathered antennae, a round belly that glows like a lamp
  kin_burrowbun  a round, sandy digger with long laid-back ears, big pale front claws and a pink nose
  kin_mossback   a slow tortoise whose shell carries a little garden: grass, a fern and two flowers
  kin_pebblepup  a stone-skinned pup with floppy slate ears and shiny crystals set along its back

    python3 tools/art/build_all.py kin     textures (textures/entity/kin/<id>.png, _blink, _glow) and
                                           src/main/java/dev/awaken/kin/client/KinData.java
    python3 tools/art/specs/kin.py preview DIR [ids]

Same conventions as wild_fauna.py (hero rigs, model pixels, y down, ground at y = 24, facing -z, clip pos y up).
Clip names are the acts' vocabulary (kin.client.KinModel): idle, move, run, want, tell, nudge, work, sleep, sniff,
carry, hide. Each want is something you can see it doing (JOURNEY part 2): the Lampwing drifts toward a glow and back;
the Burrowbun scratches the ground and gives up; the Mossback's garden droops while it turns to the sun; the Pebblepup
noses at its pile.
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ART = os.path.abspath(os.path.join(HERE, ".."))
ROOT = os.path.abspath(os.path.join(HERE, "../../.."))
sys.path.insert(0, HERE)
sys.path.insert(0, ART)
import fauna_rig as R  # noqa: E402
from fauna_rig import Rig, Ramp, hexc, lerpc, p_fur, p_blotch, p_stone, combine  # noqa: E402
from fauna_anim import cycle, s, c  # noqa: E402
from wild_fauna import sym, stubs, clip_of, hold, face_fill, front_i  # noqa: E402

JAVA_OUT = os.path.join(ROOT, "src/main/java/dev/awaken/kin/client/KinData.java")

SCALE = {"kin_lampwing": 0.55, "kin_burrowbun": 0.62, "kin_mossback": 0.85, "kin_pebblepup": 0.7}
TEMPER = {"kin_lampwing": "cute", "kin_burrowbun": "cute", "kin_mossback": "grazer", "kin_pebblepup": "cute"}


def build_hook():
    pass


def keys(*pts):
    return [tuple(p) for p in pts]


# =====================================================================================================================
# Lampwing
# =====================================================================================================================

def lampwing():
    r = Rig("kin_lampwing", (64, 64))
    r.walk_stride, r.run_stride, r.run_speed, r.shadow = 1.0, 2.0, (0.12, 0.3), 0.0
    body = r.bone("body", (0, 13, 0))
    body.cube("thorax", -3, -3, -3, 6, 6, 6)
    body.cube("ruff", -4, -3.5, -3.8, 8, 7, 2)
    belly = body.bone("abdomen", (0, 0.5, 2.5), (-22, 0, 0))
    belly.cube("belly", -3.5, -3, 0, 7, 7, 8)
    tip = belly.bone("tip", (0, 0.5, 8), (-12, 0, 0))
    tip.cube("otip", -2, -2, 0, 4, 4, 3)
    head = body.bone("head", (0, -0.5, -3.5), (8, 0, 0))
    head.cube("ohead", -2.5, -2.5, -4, 5, 5, 4)

    def side(sd, nm, mir, sh):
        ant = head.bone("ant_" + nm, (sd * 1.2, -2.5, -3.2), (-35, 0, sd * 25))
        ant.cube("frond_" + nm, -1.5, -6, 0, 3, 6, 0, mirror=mir, share=sh("frond_l"))
        fw = body.bone("fore_" + nm, (sd * 3, -2.5, -1.5), (0, sd * -10, -sd * 12))
        if sd > 0:
            fw.cube("forew_l", 0, 0, -3, 12, 0, 9)
        else:
            fw.cube("forew_r", -12, 0, -3, 12, 0, 9, mirror=True, share="forew_l")
        hw = body.bone("hind_" + nm, (sd * 3, -2, 2), (0, sd * 15, -sd * 6))
        if sd > 0:
            hw.cube("hindw_l", 0, 0, 0, 8, 0, 7)
        else:
            hw.cube("hindw_r", -8, 0, 0, 8, 0, 7, mirror=True, share="hindw_l")
        leg = body.bone("legs_" + nm, (sd * 1.5, 3, -1), (20, 0, sd * 20))
        leg.cube("oleg_" + nm, -0.5, 0, -1.5, 1, 3, 3, mirror=mir, share=sh("oleg_l"))
    sym(side)
    r.look = [("head", 1.0, 0.8)]
    r.pack()

    def flapping(cl, up, down, speed_bob=0.8, lag=0.12):
        for nm, sg in (("l", 1), ("r", -1)):
            cycle(cl, "fore_" + nm, "rot", lambda p, sg=sg: (0, sg * -10, -sg * ((up - down) / 2 * c(p) + (up + down) / 2)), 8)
            cycle(cl, "hind_" + nm, "rot", lambda p, sg=sg: (0, sg * 15, -sg * ((up - down) / 2.4 * c(p, -lag) + (up + down) / 2 - 4)), 8)
        cycle(cl, "body", "pos", lambda p: (0, speed_bob * s(p, 0.25), 0), 8)

    idle = clip_of(r, "idle", 0.9, loop=True)
    # hovering: an easy flap, the belly swinging a little behind
    flapping(idle, 45, -10, 0.9)
    cycle(idle, "abdomen", "rot", lambda p: (-22 + 4 * s(p, 0.4), 0, 0), 8)
    cycle(idle, "ant_l", "rot", lambda p: (-35 + 6 * s(p, 0.2), 0, 25), 8)
    cycle(idle, "ant_r", "rot", lambda p: (-35 + 6 * s(p, 0.3), 0, -25), 8)
    move = clip_of(r, "move", 0.5, loop=True)
    flapping(move, 55, -20, 0.6)
    hold(move, "body", rot=(10, 0, 0))
    hold(move, "abdomen", rot=(-10, 0, 0))
    hold(move, "ant_l", rot=(-60, 0, 20))
    hold(move, "ant_r", rot=(-60, 0, -20))
    run = clip_of(r, "run", 0.3, loop=True)
    flapping(run, 60, -30, 0.5)
    hold(run, "body", rot=(18, 0, 0))
    hold(run, "abdomen", rot=(-4, 0, 0))
    hold(run, "ant_l", rot=(-80, 0, 10))
    hold(run, "ant_r", rot=(-80, 0, -10))
    want = clip_of(r, "want", 3.2, loop=True)
    # drifts toward a glow it can see, then back to the trees: a lean out, a hang, a turn away
    flapping(want, 40, -5, 0.7)
    want.pos("body", keys((0, 0, 0, 0), (1.0, 0, 1, -4), (1.8, 0, 1.2, -5), (2.6, 0, 0.5, -1), (3.2, 0, 0, 0)))
    want.rot("body", keys((0, 0, 0, 0), (1.0, 12, 0, 0), (1.8, 6, 0, 0), (2.4, -4, 30, 0), (3.2, 0, 0, 0)))
    want.rot("head", keys((0, 8, 0, 0), (1.0, -10, 0, 0), (1.8, -14, 0, 0), (2.4, 8, 20, 0), (3.2, 8, 0, 0)))
    tell = clip_of(r, "tell", 1.4)
    # a happy loop-the-loop: up, over, the belly flaring
    for nm, sg in (("l", 1), ("r", -1)):
        tell.rot("fore_" + nm, keys((0, 0, sg * -10, -sg * 20), (0.2, 0, sg * -10, -sg * 70), (0.4, 0, sg * -10, sg * 25),
                                    (0.6, 0, sg * -10, -sg * 70), (0.8, 0, sg * -10, sg * 25), (1.1, 0, sg * -10, -sg * 50), (1.4, 0, sg * -10, -sg * 20)))
        tell.rot("hind_" + nm, keys((0, 0, sg * 15, -sg * 10), (0.3, 0, sg * 15, -sg * 55), (0.5, 0, sg * 15, sg * 20),
                                    (0.7, 0, sg * 15, -sg * 55), (0.9, 0, sg * 15, sg * 20), (1.4, 0, sg * 15, -sg * 10)))
    tell.pos("body", keys((0, 0, 0, 0), (0.4, 0, 4, 0), (0.8, 0, 5, 0), (1.1, 0, 2, 0), (1.4, 0, 0, 0)))
    tell.rot("body", keys((0, 0, 0, 0), (0.3, -40, 0, 0), (0.6, -180, 0, 0), (0.9, -320, 0, 0), (1.1, -360, 0, 0), (1.4, -360, 0, 0)))
    tell.scale("abdomen", keys((0, 1, 1, 1), (0.5, 1.15, 1.15, 1.1), (1.0, 1.05, 1.05, 1.0), (1.4, 1, 1, 1)))
    nudge = clip_of(r, "nudge", 1.0)
    flapping(nudge, 30, -5, 0.4)
    nudge.rot("head", keys((0, 8, 0, 0), (0.25, 8, -25, 0), (0.5, 8, 25, 0), (0.75, 8, -15, 0), (1.0, 8, 0, 0)))
    nudge.rot("ant_l", keys((0, -35, 0, 25), (0.3, -10, 0, 50), (0.7, -10, 0, 50), (1.0, -35, 0, 25)))
    nudge.rot("ant_r", keys((0, -35, 0, -25), (0.3, -10, 0, -50), (0.7, -10, 0, -50), (1.0, -35, 0, -25)))
    work = clip_of(r, "work", 2.4, loop=True)
    # roosting over its light: a slow circle with the belly lit and swinging
    flapping(work, 40, -12, 0.6)
    cycle(work, "body", "rot", lambda p: (4 * s(p), 0, 10 * s(p, 0.25)), 8)
    cycle(work, "abdomen", "rot", lambda p: (-26 + 6 * s(p, 0.4), 0, 0), 8)
    sleep = clip_of(r, "sleep", 4.0, loop=True)
    # settled on the ground, wings folded down over the body like a little tent, antennae laid back
    hold(sleep, "body", pos=(0, -8, 0), rot=(-6, 0, 0))
    for nm, sg in (("l", 1), ("r", -1)):
        hold(sleep, "fore_" + nm, rot=(0, sg * -38, sg * 58))
        hold(sleep, "hind_" + nm, rot=(0, sg * -10, sg * 62))
        hold(sleep, "ant_" + nm, rot=(-75, 0, sg * 8))
        hold(sleep, "legs_" + nm, rot=(-10, 0, sg * 40))
    cycle(sleep, "abdomen", "scale", lambda p: (1 + 0.03 * s(p), 1 + 0.03 * s(p), 1), 8)
    sniff = clip_of(r, "sniff", 1.2)
    flapping(sniff, 35, -5, 0.4)
    for nm, sg in (("l", 1), ("r", -1)):
        sniff.rot("ant_" + nm, keys((0, -35, 0, sg * 25), (0.2, -5, 0, sg * 15), (0.4, -30, 0, sg * 30), (0.6, -5, 0, sg * 15), (1.2, -35, 0, sg * 25)))
    carry = clip_of(r, "carry", 0.5, loop=True)
    flapping(carry, 55, -20, 0.6)
    hide = clip_of(r, "hide", 1.0, loop=True)
    flapping(hide, 20, -5, 0.2)
    hold(hide, "body", pos=(0, -4, 0), rot=(-15, 0, 0))
    hold(hide, "ant_l", rot=(-80, 0, 5))
    hold(hide, "ant_r", rot=(-80, 0, -5))
    stubs(r)
    return r


def paint_lampwing(r, P):
    fur = Ramp("#5a4632", "#7a6046", "#9a7c5a", "#b89a72", "#d2b88e", "#e8d6b0", "#f6ecd6")
    cream = Ramp("#a89a84", "#c4b69e", "#ddd0b8", "#efe6d2", "#fbf6ea")
    for cb in ("thorax", "ohead", "otip"):
        P.paint(cb, fur, 3.4, combine(p_fur(0.7, 2, 1, 2), p_blotch("lampwing", 4, 0.3)))
    P.paint("ruff", cream, 3.0, p_fur(0.9, 2, 3, 2))
    # the belly: warm bands between darker rings, its underside a lamp
    lamp = Ramp("#6a3c12", "#9a5c1c", "#c88428", "#e6a83c", "#f6cc6a", "#ffe8a8")
    P.paint("belly", lamp, 3.0, lambda f, i, j: -1.6 if (f.name in ("west", "east") and i % 3 == 0) or (f.name == "top" and j % 3 == 0) else 0.0)
    for fname in ("bottom", "west", "east", "south"):
        def g(f, i, j, fname=fname):
            ring = (f.name in ("west", "east") and i % 3 == 0) or (f.name in ("bottom",) and j % 3 == 0)
            if f.name in ("west", "east") and j < f.h // 3:
                return None
            return None if ring else lerpc(hexc("#ffcf6a"), hexc("#fff2c0"), 0.5 if f.name == "bottom" else 0.2)
        P.glow_face("belly", fname, g)
    P.glow_face("otip", "south", lambda f, i, j: hexc("#ffd88a") if 0 < i < f.w - 1 and 0 < j < f.h - 1 else None)
    # wings: dusky lilac-brown, a pale edge, an eye-spot on each forewing, soft vein lines
    wing = Ramp("#3e3040", "#56445a", "#6e5a6e", "#8a7484", "#a8909a")
    for cb, spot in (("forew_l", True), ("hindw_l", False)):
        for fname in ("top", "bottom"):
            def wf(f, i, j, spot=spot):
                cx, cz = f.w * 0.62, f.h * 0.45
                # rounded wing outline: corners left clear
                if (i == f.w - 1 and (j == 0 or j == f.h - 1)) or (i >= f.w - 2 and j in (0, f.h - 1)):
                    return None
                d = ((i - cx) ** 2 + (j - cz) ** 2) ** 0.5
                if spot and d < 1.3:
                    return hexc("#2a1a10")
                if spot and d < 2.4:
                    return hexc("#e8b860")
                if i == f.w - 1 or j == f.h - 1 or j == 0:
                    return hexc("#cdb8b0")
                if (i + j) % 5 == 0:
                    return wing.at(1.4)
                return wing.at(2.6 + 0.6 * (i / f.w) + (0.4 if fname == "bottom" else 0))
            face_fill(P, cb, fname, wf)
        for fname in ("north", "south", "west", "east"):
            face_fill(P, cb, fname, lambda f, i, j: hexc("#8a7484"))
    face_fill(P, "frond_l", "north", lambda f, i, j: hexc("#c8a878") if i == 1 or (j % 2 == 0) else None)
    face_fill(P, "frond_l", "south", lambda f, i, j: hexc("#a88a60") if i == 1 or (j % 2 == 0) else None)
    P.glow_face("frond_l", "north", lambda f, i, j: hexc("#ffe0a0") if j == 0 and i == 1 else None)
    P.paint("oleg_l", Ramp("#2a2018", "#3a2c20", "#4a3a2a"), 1.5)
    # big dark eyes on the head's sides, a glint in each; a soft fluffy face
    with P.eyes():
        for fname in ("west", "east"):
            f = P.face("ohead", fname)
            ex = front_i(f)
            step = 1 if fname == "west" else -1
            for di in (0, 1):
                for j in (1, 2, 3):
                    f.set(ex + step * di, j, hexc("#120c10"))
            f.set(ex, 1, hexc("#b8a8c0"))
    face_fill(P, "ohead", "north", lambda f, i, j: hexc("#f0e2c4") if j >= 3 and 0 < i < f.w - 1 else None)
    return P.skin, P.glow


# =====================================================================================================================
# Burrowbun
# =====================================================================================================================

def burrowbun():
    r = Rig("kin_burrowbun", (64, 64))
    r.walk_stride, r.run_stride, r.run_speed, r.shadow = 0.7, 1.6, (0.1, 0.24), 0.3
    body = r.bone("body", (0, 19, 0))
    body.cube("torso", -4, -4, -4, 8, 7, 8)
    body.cube("rump", -3.5, -3.5, 3, 7, 6, 2)
    head = body.bone("head", (0, -2, -4), (4, 0, 0))
    head.cube("ohead", -3, -3.5, -4, 6, 5, 4)
    head.cube("snout", -1.5, -1, -5, 3, 2, 1)
    head.cube("nose", -0.5, -1.2, -5.6, 1, 1, 1)

    def side(sd, nm, mir, sh):
        ear = head.bone("ear_" + nm, (sd * 1.8, -3.5, -1), (-62, sd * 12, sd * 18))
        ear.cube("oear_" + nm, -1, -7, -0.5, 2, 7, 1, mirror=mir, share=sh("oear_l"))
        paw = body.bone("paw_" + nm, (sd * 2.2, 2, -3), (10, 0, 0))
        paw.cube("arm_" + nm, -1, 0, -1, 2, 2, 2, mirror=mir, share=sh("arm_l"))
        paw.cube("claw_" + nm, -1.5, 2, -2.5, 3, 1, 3, mirror=mir, share=sh("claw_l"))
        hl = body.bone("hleg_" + nm, (sd * 2.6, 2, 2.5))
        hl.cube("thigh_" + nm, -1.5, -1, -1.5, 3, 3, 3, mirror=mir, share=sh("thigh_l"))
        hl.cube("hfoot_" + nm, -1, 2, -3, 2, 1, 4, mirror=mir, share=sh("hfoot_l"))
    sym(side)
    tail = body.bone("tail", (0, -2, 5))
    tail.cube("otail", -1.5, -1.5, 0, 3, 3, 2)
    r.look = [("head", 1.0, 0.8)]
    r.pack()

    def hops(cl, height, tilt, legs):
        cycle(cl, "body", "pos", lambda p: (0, height * max(0, s(p)), 0), 8)
        cycle(cl, "body", "rot", lambda p: (-tilt * s(p), 0, 0), 8)
        for nm in ("hleg_l", "hleg_r"):
            cycle(cl, nm, "rot", lambda p: (-legs * s(p, 0.05), 0, 0), 8)
        for nm, ph in (("paw_l", 0.25), ("paw_r", 0.3)):
            cycle(cl, nm, "rot", lambda p, ph=ph: (10 + legs * 0.8 * s(p, ph), 0, 0), 8)
        for nm, sd in (("ear_l", 1), ("ear_r", -1)):
            cycle(cl, nm, "rot", lambda p, sd=sd: (-62 - 14 * s(p, 0.1), sd * 12, sd * 18), 8)

    idle = clip_of(r, "idle", 3.0, loop=True)
    kk = [(0, 4, 0, 0)]
    for k in range(9):
        kk += [(0.15 + k * 0.3, 7, 0, 0), (0.3 + k * 0.3, 4, 0, 0)]
    kk.append((3.0, 4, 0, 0))
    idle.rot("head", kk, "lin")
    idle.rot("ear_r", keys((0, -62, -12, -18), (1.6, -62, -12, -18), (1.75, -40, -12, -30), (2.3, -40, -12, -30), (2.5, -62, -12, -18), (3.0, -62, -12, -18)))
    cycle(idle, "body", "scale", lambda p: (1 + 0.02 * s(p * 2), 1 + 0.02 * s(p * 2), 1), 8)
    move = clip_of(r, "move", 0.5, loop=True)
    hops(move, 1.8, 10, 45)
    run = clip_of(r, "run", 0.3, loop=True)
    hops(run, 2.6, 16, 70)
    want = clip_of(r, "want", 3.0, loop=True)
    # scratches at the ground, finds it hard, sits back and gives up
    want.rot("body", keys((0, 0, 0, 0), (0.3, 18, 0, 0), (1.6, 18, 0, 0), (2.0, -14, 0, 0), (2.7, -14, 0, 0), (3.0, 0, 0, 0)))
    want.rot("head", keys((0, 4, 0, 0), (0.3, 20, 0, 0), (1.6, 20, 0, 0), (2.0, -6, 0, 0), (2.3, 4, -20, 0), (2.7, 4, 20, 0), (3.0, 4, 0, 0)))
    scr = [(0, 10, 0, 0)]
    for k in range(6):
        scr += [(0.35 + k * 0.2, -40, 0, 0), (0.45 + k * 0.2, 30, 0, 0)]
    scr += [(1.8, 10, 0, 0), (3.0, 10, 0, 0)]
    want.rot("paw_l", scr, "lin")
    want.rot("paw_r", [(t + 0.1 if 0 < t < 1.8 else t, x, y, z) for t, x, y, z in scr], "lin")
    want.rot("ear_l", keys((0, -62, 12, 18), (2.0, -62, 12, 18), (2.2, -30, 12, 40), (2.8, -30, 12, 40), (3.0, -62, 12, 18)))
    want.rot("ear_r", keys((0, -62, -12, -18), (2.0, -62, -12, -18), (2.2, -30, -12, -40), (2.8, -30, -12, -40), (3.0, -62, -12, -18)))
    tell = clip_of(r, "tell", 1.3)
    # two happy hops and a twist in the air, ears flung up
    tell.pos("body", keys((0, 0, 0, 0), (0.2, 0, 4, 0), (0.4, 0, 0, 0), (0.6, 0, 5, 0), (0.85, 0, 0, 0), (1.3, 0, 0, 0)))
    tell.rot("body", keys((0, 0, 0, 0), (0.2, -10, 0, 0), (0.4, 0, 0, 0), (0.6, -10, 180, 0), (0.85, 0, 360, 0), (1.3, 0, 360, 0)))
    for nm, sd in (("ear_l", 1), ("ear_r", -1)):
        tell.rot(nm, keys((0, -62, sd * 12, sd * 18), (0.2, -5, sd * 12, sd * 25), (0.5, -40, sd * 12, sd * 18), (0.7, 0, sd * 12, sd * 30), (1.3, -62, sd * 12, sd * 18)))
    nudge = clip_of(r, "nudge", 0.9)
    nudge.rot("head", keys((0, 4, 0, 0), (0.15, 4, -22, 0), (0.35, 4, 22, 0), (0.55, 4, -16, 0), (0.75, 4, 10, 0), (0.9, 4, 0, 0)))
    for nm, sd in (("ear_l", 1), ("ear_r", -1)):
        nudge.rot(nm, keys((0, -62, sd * 12, sd * 18), (0.2, -62, sd * 12, sd * 50), (0.45, -62, sd * 12, sd * 5), (0.9, -62, sd * 12, sd * 18)))
    work = clip_of(r, "work", 0.8, loop=True)
    # digging hard: nose down, claws going like a little engine, rump up
    hold(work, "body", rot=(22, 0, 0), pos=(0, 0.6, 0))
    hold(work, "head", rot=(24, 0, 0))
    cycle(work, "paw_l", "rot", lambda p: (-20 + 55 * s(p * 2), 0, 0), 8)
    cycle(work, "paw_r", "rot", lambda p: (-20 + 55 * s(p * 2, 0.5), 0, 0), 8)
    cycle(work, "tail", "rot", lambda p: (0, 18 * s(p * 2), 0), 8)
    hold(work, "ear_l", rot=(-80, 12, 30))
    hold(work, "ear_r", rot=(-80, -12, -30))
    sleep = clip_of(r, "sleep", 4.0, loop=True)
    # a round ball: tucked down, ears flat along the back, breathing
    hold(sleep, "body", pos=(0, -2.4, 0))
    hold(sleep, "head", rot=(30, 0, 0), pos=(0, -0.5, 1))
    hold(sleep, "ear_l", rot=(-90, 6, 6))
    hold(sleep, "ear_r", rot=(-90, -6, -6))
    for nm in ("paw_l", "paw_r"):
        hold(sleep, nm, rot=(-70, 0, 0))
    for nm in ("hleg_l", "hleg_r"):
        hold(sleep, nm, rot=(-20, 0, 0))
    cycle(sleep, "body", "scale", lambda p: (1 + 0.04 * s(p), 1 + 0.05 * s(p), 1 + 0.03 * s(p)), 8)
    sniff = clip_of(r, "sniff", 1.2)
    sniff.rot("body", keys((0, 0, 0, 0), (0.3, 12, 0, 0), (0.9, 12, 0, 0), (1.2, 0, 0, 0)))
    sk = [(0, 4, 0, 0)]
    for k in range(5):
        sk += [(0.25 + k * 0.14, 22, 0, 0), (0.32 + k * 0.14, 16, 0, 0)]
    sk.append((1.2, 4, 0, 0))
    sniff.rot("head", sk, "lin")
    carry = clip_of(r, "carry", 0.5, loop=True)
    hops(carry, 1.4, 6, 40)
    hold(carry, "head", rot=(-10, 0, 0))
    hide = clip_of(r, "hide", 1.0, loop=True)
    hold(hide, "body", pos=(0, -1.8, 0), rot=(6, 0, 0))
    hold(hide, "ear_l", rot=(-92, 4, 4))
    hold(hide, "ear_r", rot=(-92, -4, -4))
    cycle(hide, "body", "pos", lambda p: (0.15 * s(p * 4), -1.8, 0), 8)
    stubs(r)
    return r


def paint_burrowbun(r, P):
    sand = Ramp("#4a3220", "#664630", "#845c3e", "#a4784f", "#bf9466", "#d6ae80", "#e8c89c")
    belly = Ramp("#c8b498", "#ded0b8", "#efe6d4", "#fbf6ec")
    fur = combine(p_fur(0.6, 2, 2, 3), p_blotch("burrowbun", 5, 0.25))
    for cb in ("torso", "rump", "ohead", "thigh_l", "arm_l"):
        P.paint(cb, sand, 3.6, fur)
    # a darker stripe down the back, a cream belly and chin
    face_fill(P, "torso", "top", lambda f, i, j: sand.at(2.2) if abs(i - f.w / 2 + 0.5) < 1.2 else None)
    face_fill(P, "rump", "top", lambda f, i, j: sand.at(2.2) if abs(i - f.w / 2 + 0.5) < 1.2 else None)
    for cb in ("torso", "rump"):
        face_fill(P, cb, "bottom", lambda f, i, j: belly.at(2.0 + 0.4 * ((i + j) % 2)))
    face_fill(P, "torso", "north", lambda f, i, j: belly.at(2.3) if j >= 2 and 1 <= i < f.w - 1 else None)
    P.paint("snout", belly, 2.4)
    face_fill(P, "snout", "north", lambda f, i, j: hexc("#efe2cc"))
    P.paint("nose", Ramp("#b05868", "#d07a88", "#e89aa6"), 2.0)
    P.paint("oear_l", sand, 3.2, p_fur(0.4, 3, 1, 4))
    face_fill(P, "oear_l", "north", lambda f, i, j: hexc("#d8a098") if 0 < j < f.h - 1 else None)
    face_fill(P, "oear_l", "south", lambda f, i, j: sand.at(1.8) if j < 2 else None)
    P.paint("claw_l", Ramp("#9a8c78", "#bcae98", "#ddd2be", "#f2ead8"), 2.6, lambda f, i, j: -1.2 if f.name == "north" and i % 1 == 0 and j == f.h - 1 else 0.0)
    face_fill(P, "claw_l", "north", lambda f, i, j: hexc("#f0e8d6") if i % 2 == 0 else hexc("#a89c88"))
    P.paint("hfoot_l", Ramp("#5a4230", "#6e543e", "#8a6c52"), 2.0)
    P.paint("otail", Ramp("#d8ccb6", "#ece4d4", "#f8f4ea", "#ffffff"), 2.4, p_fur(0.5, 2, 0, 2))
    with P.eyes():
        for fname in ("west", "east"):
            f = P.face("ohead", fname)
            ex = front_i(f) + (1 if fname == "west" else -1)
            f.set(ex, 1, hexc("#1a1210"))
            f.set(ex, 2, hexc("#0c0806"))
            f.set(ex + (1 if fname == "west" else -1), 1, hexc("#2a1c16"))
            f.set(ex, 1, hexc("#6a5a50"))
    return P.skin, P.glow


# =====================================================================================================================
# Mossback
# =====================================================================================================================

def mossback():
    r = Rig("kin_mossback", (64, 64))
    r.walk_stride, r.run_stride, r.run_speed, r.shadow = 0.4, 0.7, (0.05, 0.1), 0.45
    body = r.bone("body", (0, 19, 0))
    body.cube("plastron", -5.5, 0, -6, 11, 2, 12)
    shell = body.bone("shell", (0, 0, 0))
    shell.cube("dome", -6, -5, -7, 12, 5, 14)
    shell.cube("crown", -4.5, -7, -5, 9, 2, 10)
    garden = shell.bone("garden", (0, -7, 0))
    garden.cube("sod", -4, -1, -4, 8, 1, 8)
    garden.cube("grass_x", -4, -4, -1, 8, 3, 0)
    garden.cube("grass_z", 1, -4, -4, 0, 3, 8)
    fern = garden.bone("fern", (-2, -1, 2), (0, 40, 0))
    fern.cube("frond_a", -3, -5, 0, 6, 5, 0)
    fern.cube("frond_b", 0, -5, -3, 0, 5, 6)
    for k, (x, z, h) in enumerate(((2.5, -2, 4), (-1.5, -2.5, 3))):
        fl = garden.bone(f"flower{k}", (x, -1, z))
        fl.cube(f"stem{k}", 0, -h, 0, 0, h, 1)
        fl.cube(f"bloom{k}", -1, -h - 2, -1, 2, 2, 2)
    neck = body.bone("neck", (0, -0.5, -6), (8, 0, 0))
    neck.cube("oneck", -1.5, -1.5, -3, 3, 3, 3)
    head = neck.bone("head", (0, -0.5, -3), (-6, 0, 0))
    head.cube("ohead", -2, -3, -4, 4, 4, 4)
    head.cube("beak", -1, -1, -4.6, 2, 2, 1)

    def side(sd, nm, mir, sh):
        fl = body.bone("fleg_" + nm, (sd * 4.5, 1, -4))
        fl.cube("ofleg_" + nm, -1.5, 0, -1.5, 3, 4, 3, mirror=mir, share=sh("ofleg_l"))
        hl = body.bone("hleg_" + nm, (sd * 4.5, 1, 4))
        hl.cube("ohleg_" + nm, -1.5, 0, -1.5, 3, 4, 3, mirror=mir, share=sh("ofleg_l"))
    sym(side)
    tail = body.bone("tail", (0, 0.5, 7), (20, 0, 0))
    tail.cube("otail", -0.5, 0, 0, 1, 1, 2)
    r.look = [("neck", 0.4, 0.4), ("head", 0.6, 0.6)]
    r.pack()

    def walk(cl, swing, sway):
        for nm, ph in (("fleg_l", 0), ("hleg_r", 0), ("fleg_r", 0.5), ("hleg_l", 0.5)):
            cycle(cl, nm, "rot", lambda p, ph=ph: (swing * s(p, ph), 0, 0), 8)
            cycle(cl, nm, "pos", lambda p, ph=ph: (0, 0.6 * max(0, c(p, ph)), 0), 8)
        cycle(cl, "body", "rot", lambda p: (0, 0, sway * s(p)), 8)
        cycle(cl, "neck", "rot", lambda p: (8 + 3 * s(p * 2), 0, 0), 8)
        cycle(cl, "garden", "rot", lambda p: (0, 0, -sway * 1.5 * s(p, 0.1)), 8)

    idle = clip_of(r, "idle", 4.0, loop=True)
    idle.rot("neck", keys((0, 8, 0, 0), (1.4, 4, 0, 0), (1.8, 4, 25, 0), (2.8, 4, 25, 0), (3.2, 8, 0, 0), (4.0, 8, 0, 0)))
    idle.rot("head", keys((0, -6, 0, 0), (1.0, 0, 0, 0), (2.0, -10, 0, 0), (4.0, -6, 0, 0)))
    cycle(idle, "garden", "rot", lambda p: (0, 0, 2.5 * s(p)), 8)
    cycle(idle, "fern", "rot", lambda p: (3 * s(p, 0.3), 40, 3 * s(p, 0.1)), 8)
    move = clip_of(r, "move", 1.6, loop=True)
    walk(move, 25, 3)
    run = clip_of(r, "run", 0.9, loop=True)
    walk(run, 35, 5)
    want = clip_of(r, "want", 4.0, loop=True)
    # very still, the garden drooping; it turns its shell toward the sun, then toward the water
    hold(want, "garden", scale=(0.92, 0.85, 0.92))
    hold(want, "fern", rot=(35, 40, 20))
    for k in (0, 1):
        hold(want, f"flower{k}", rot=(50 if k == 0 else 40, 0, -30 if k == 0 else 25))
    want.rot("body", keys((0, 0, 0, 0), (1.4, 0, 0, 0), (2.0, 0, 35, 0), (3.0, 0, 35, 0), (3.6, 0, 0, 0), (4.0, 0, 0, 0)))
    want.rot("neck", keys((0, 8, 0, 0), (0.8, -25, 0, 0), (1.4, -25, 0, 0), (2.0, 12, 0, 0), (3.0, 20, 0, 0), (4.0, 8, 0, 0)))
    want.rot("head", keys((0, -6, 0, 0), (0.8, -25, 0, 0), (1.4, -25, 0, 0), (2.4, 10, 0, 0), (4.0, -6, 0, 0)))
    tell = clip_of(r, "tell", 1.8)
    # the garden blooms: it lifts up tall, the flowers open, the shell gives a happy wiggle
    tell.scale("garden", keys((0, 1, 1, 1), (0.5, 1.15, 1.3, 1.15), (1.2, 1.1, 1.2, 1.1), (1.8, 1, 1, 1)))
    for k in (0, 1):
        tell.scale(f"flower{k}", keys((0, 1, 1, 1), (0.4, 1.8, 1.4, 1.8), (1.2, 1.5, 1.2, 1.5), (1.8, 1, 1, 1)))
    tell.pos("body", keys((0, 0, 0, 0), (0.4, 0, 1.6, 0), (1.2, 0, 1.6, 0), (1.8, 0, 0, 0)))
    tell.rot("shell", keys((0, 0, 0, 0), (0.6, 0, 0, 6), (0.8, 0, 0, -6), (1.0, 0, 0, 6), (1.2, 0, 0, -4), (1.5, 0, 0, 0), (1.8, 0, 0, 0)))
    tell.rot("neck", keys((0, 8, 0, 0), (0.4, -30, 0, 0), (1.3, -30, 0, 0), (1.8, 8, 0, 0)))
    nudge = clip_of(r, "nudge", 1.4)
    nudge.rot("neck", keys((0, 8, 0, 0), (0.3, 8, -30, 0), (0.7, 8, 30, 0), (1.0, 8, -15, 0), (1.4, 8, 0, 0)))
    nudge.rot("head", keys((0, -6, 0, 0), (0.5, 4, 0, 0), (1.4, -6, 0, 0)))
    work = clip_of(r, "work", 4.0, loop=True)
    # basking in the wet and the sun, its garden swaying full, slow breaths
    hold(work, "neck", rot=(-18, 0, 0))
    hold(work, "head", rot=(-14, 0, 0))
    cycle(work, "garden", "scale", lambda p: (1.05 + 0.03 * s(p), 1.1 + 0.05 * s(p), 1.05 + 0.03 * s(p)), 8)
    cycle(work, "fern", "rot", lambda p: (4 * s(p), 40 + 8 * s(p, 0.2), 4 * s(p, 0.4)), 8)
    cycle(work, "body", "pos", lambda p: (0, 0.2 * s(p), 0), 8)
    sleep = clip_of(r, "sleep", 5.0, loop=True)
    # head and legs pulled into the shell, the shell resting on the ground
    hold(sleep, "body", pos=(0, -3.2, 0))
    hold(sleep, "neck", pos=(0, 0, 3.5), rot=(20, 0, 0))
    hold(sleep, "head", rot=(10, 0, 0))
    for nm in ("fleg_l", "fleg_r", "hleg_l", "hleg_r"):
        hold(sleep, nm, pos=(0, 3, 0))
    cycle(sleep, "shell", "scale", lambda p: (1, 1 + 0.015 * s(p), 1), 8)
    sniff = clip_of(r, "sniff", 1.6)
    sniff.rot("neck", keys((0, 8, 0, 0), (0.4, 30, 0, 0), (1.2, 30, 0, 0), (1.6, 8, 0, 0)))
    sniff.rot("head", keys((0, -6, 0, 0), (0.5, 10, 0, 0), (0.7, 14, 0, 0), (0.9, 10, 0, 0), (1.1, 14, 0, 0), (1.6, -6, 0, 0)))
    carry = clip_of(r, "carry", 1.6, loop=True)
    walk(carry, 25, 3)
    hide = clip_of(r, "hide", 1.0, loop=True)
    hold(hide, "body", pos=(0, -3.2, 0))
    hold(hide, "neck", pos=(0, 0, 3.5), rot=(20, 0, 0))
    for nm in ("fleg_l", "fleg_r", "hleg_l", "hleg_r"):
        hold(hide, nm, pos=(0, 3, 0))
    stubs(r)
    return r


def paint_mossback(r, P):
    shellr = Ramp("#2a2e1c", "#3c4426", "#525c30", "#6a743c", "#84904c", "#a0aa62")
    moss = Ramp("#2c4a1c", "#3a6224", "#4c7a2c", "#609436", "#78ac44", "#94c25a")
    # scutes: plates edged darker, moss creeping over the top
    plates = R.p_plates(4, 4, 1.2) if hasattr(R, "p_plates") else None
    P.paint("dome", shellr, 3.2, combine(plates, p_blotch("mossback", 4, 0.5)) if plates else p_blotch("mossback", 4, 0.5))
    face_fill(P, "dome", "top", lambda f, i, j: moss.at(2.4 + ((i * 7 + j * 3) % 5) * 0.3))
    for fname in ("north", "south", "west", "east"):
        face_fill(P, "dome", fname, lambda f, i, j: moss.at(2.2 + (i % 3) * 0.4) if j == 0 or (j == 1 and (i * 5) % 3 == 0) else None)
    P.paint("crown", moss, 3.0, p_blotch("mossback-crown", 3, 0.6))
    P.paint("plastron", Ramp("#6a5a3a", "#867248", "#a28c5a", "#bca46c"), 2.6, R.p_bands(2, 0.8))
    P.paint("sod", Ramp("#2a1e14", "#3a2a1c", "#4c3826"), 1.6)
    face_fill(P, "sod", "top", lambda f, i, j: moss.at(2.0 + ((i + j * 3) % 4) * 0.5))
    for cb, fnames in (("grass_x", ("north", "south")), ("grass_z", ("west", "east"))):
        for fname in fnames:
            face_fill(P, cb, fname, lambda f, i, j: moss.at(2.5 + 0.8 * (1 - j / max(1, f.h - 1))) if (i * 3 + 1) % 4 != 0 and (j > 0 or i % 2 == 0) else None)
    fernr = Ramp("#1e4a24", "#2a6230", "#38783a", "#4a9248")
    for cb, fnames in (("frond_a", ("north", "south")), ("frond_b", ("west", "east"))):
        for fname in fnames:
            def fr(f, i, j):
                mid = f.w / 2 - 0.5
                half = (j + 1) / f.h * f.w / 2
                if abs(i - mid) > half:
                    return None
                return fernr.at(1.5 + (0.8 if abs(i - mid) < 0.6 else 0.0) + j * 0.15)
            face_fill(P, cb, fname, fr)
    for k, col in ((0, ("#c84a5a", "#e46a7a", "#f69aa4")), (1, ("#d8b42a", "#f0d04a", "#fae68a"))):
        face_fill(P, f"stem{k}", "west", lambda f, i, j: hexc("#3a6a2a"))
        face_fill(P, f"stem{k}", "east", lambda f, i, j: hexc("#3a6a2a"))
        P.paint(f"bloom{k}", Ramp(*col), 1.6)
        face_fill(P, f"bloom{k}", "top", lambda f, i, j: hexc("#fff4c8") if (i, j) == (0, 0) else Ramp(*col).at(2.0))
    skin = Ramp("#3a3a2a", "#525038", "#6c684a", "#86805c", "#a09a72")
    for cb in ("oneck", "ohead", "ofleg_l", "otail"):
        P.paint(cb, skin, 3.0, R.p_scales(2, 2, 0.6))
    P.paint("beak", Ramp("#4a4232", "#6a5e46", "#8a7c5c"), 2.0)
    with P.eyes():
        for fname in ("west", "east"):
            f = P.face("ohead", fname)
            ex = front_i(f) + (1 if fname == "west" else -1)
            f.set(ex, 1, hexc("#100c08"))
            f.set(ex, 0, hexc("#c8b070"))
    return P.skin, P.glow


# =====================================================================================================================
# Pebblepup
# =====================================================================================================================

def pebblepup():
    r = Rig("kin_pebblepup", (64, 64))
    r.walk_stride, r.run_stride, r.run_speed, r.shadow = 0.8, 1.8, (0.1, 0.24), 0.3
    body = r.bone("body", (0, 17, 0))
    body.cube("torso", -3, -3, -4, 6, 6, 8)
    body.cube("chest", -3.5, -3.5, -5, 7, 6, 2)
    gems = body.bone("gems", (0, -3, 0))
    gems.cube("gem_a", -1, -2, -3, 2, 2, 2)
    gems.cube("gem_b", -0.5, -1, 0, 1, 1, 1)
    gems.cube("gem_c", 0, -3, 2, 1, 3, 1)
    neck = body.bone("neck", (0, -2.5, -4.5), (-10, 0, 0))
    neck.cube("oneck", -2, -2, -2, 4, 3, 3)
    head = neck.bone("head", (0, -2, -1.5), (10, 0, 0))
    head.cube("ohead", -3, -4, -5, 6, 5, 5)
    head.cube("muzzle", -1.5, -1.5, -7, 3, 2, 2)
    head.cube("nose", -0.5, -1.8, -7.6, 1, 1, 1)

    def side(sd, nm, mir, sh):
        ear = head.bone("ear_" + nm, (sd * 2.8, -3.5, -2.5), (0, 0, sd * 35))
        ear.cube("oear_" + nm, -0.5 if sd > 0 else -1.5, 0, -1.5, 2, 4, 3, mirror=mir, share=sh("oear_l"))
        fl = body.bone("fleg_" + nm, (sd * 2, 2.5, -3))
        fl.cube("ofleg_" + nm, -1, 0, -1, 2, 4, 2, mirror=mir, share=sh("ofleg_l"))
        fl.cube("fpaw_" + nm, -1, 3.5, -2, 2, 1, 3, mirror=mir, share=sh("fpaw_l"))
        hl = body.bone("hleg_" + nm, (sd * 2, 2.5, 3))
        hl.cube("ohleg_" + nm, -1, 0, -1, 2, 4, 2, mirror=mir, share=sh("ofleg_l"))
        hl.cube("hpaw_" + nm, -1, 3.5, -2, 2, 1, 3, mirror=mir, share=sh("fpaw_l"))
    sym(side)
    tail = body.bone("tail", (0, -2, 4), (-35, 0, 0))
    tail.cube("otail", -1, -1, 0, 2, 2, 4)
    r.look = [("neck", 0.35, 0.35), ("head", 0.65, 0.65)]
    r.pack()

    def trot(cl, swing, bob, wag):
        for nm, ph in (("fleg_l", 0), ("hleg_r", 0.05), ("fleg_r", 0.5), ("hleg_l", 0.55)):
            cycle(cl, nm, "rot", lambda p, ph=ph: (swing * s(p, ph), 0, 0), 8)
        cycle(cl, "body", "pos", lambda p: (0, bob * abs(s(p)), 0), 8)
        cycle(cl, "tail", "rot", lambda p: (-35, wag * s(p * 2), 0), 8)
        for nm, sd in (("ear_l", 1), ("ear_r", -1)):
            cycle(cl, nm, "rot", lambda p, sd=sd: (-10 * abs(s(p)), 0, sd * (35 + 12 * abs(s(p, 0.1)))), 8)

    idle = clip_of(r, "idle", 3.0, loop=True)
    cycle(idle, "tail", "rot", lambda p: (-35, 25 * s(p * 3), 0), 12)
    idle.rot("neck", keys((0, -10, 0, 0), (1.0, -10, 0, 0), (1.3, -10, 30, 0), (2.0, -10, 30, 0), (2.3, -10, -20, 0), (2.7, -10, -20, 0), (3.0, -10, 0, 0)))
    idle.rot("head", keys((0, 10, 0, 0), (1.3, 10, 0, 18), (2.0, 10, 0, 18), (2.3, 10, 0, 0), (3.0, 10, 0, 0)))
    cycle(idle, "body", "scale", lambda p: (1 + 0.02 * s(p * 2), 1 + 0.025 * s(p * 2), 1), 8)
    move = clip_of(r, "move", 0.6, loop=True)
    trot(move, 35, 0.6, 30)
    run = clip_of(r, "run", 0.36, loop=True)
    for nm, ph in (("fleg_l", 0), ("fleg_r", 0.08), ("hleg_l", 0.5), ("hleg_r", 0.58)):
        cycle(run, nm, "rot", lambda p, ph=ph: (60 * s(p, ph), 0, 0), 8)
    cycle(run, "body", "rot", lambda p: (12 * s(p, 0.25), 0, 0), 8)
    cycle(run, "body", "pos", lambda p: (0, 1.4 * max(0, s(p)), 0), 8)
    hold(run, "tail", rot=(-10, 0, 0))
    hold(run, "ear_l", rot=(-50, 0, 20))
    hold(run, "ear_r", rot=(-50, 0, -20))
    want = clip_of(r, "want", 3.4, loop=True)
    # back to its pile: noses the stones over, turns one with a paw, sits and looks at it
    want.rot("neck", keys((0, -10, 0, 0), (0.4, 35, 0, 0), (1.6, 35, -15, 0), (2.2, 35, 15, 0), (2.6, -15, 0, 0), (3.4, -10, 0, 0)))
    want.rot("head", keys((0, 10, 0, 0), (0.4, 25, 0, 0), (2.2, 25, 0, 0), (2.6, 0, 0, -20), (3.1, 0, 0, -20), (3.4, 10, 0, 0)))
    want.rot("fleg_l", keys((0, 0, 0, 0), (1.0, 0, 0, 0), (1.2, -50, 0, 0), (1.4, -10, 0, 0), (1.6, -50, 0, 0), (1.8, 0, 0, 0), (3.4, 0, 0, 0)))
    want.rot("body", keys((0, 0, 0, 0), (2.4, 0, 0, 0), (2.6, -16, 0, 0), (3.1, -16, 0, 0), (3.4, 0, 0, 0)))
    for nm in ("hleg_l", "hleg_r"):
        want.rot(nm, keys((0, 0, 0, 0), (2.4, 0, 0, 0), (2.6, 40, 0, 0), (3.1, 40, 0, 0), (3.4, 0, 0, 0)))
    tell = clip_of(r, "tell", 1.4)
    # a play bow, a bounce, the tail a blur
    tell.rot("body", keys((0, 0, 0, 0), (0.25, 18, 0, 0), (0.6, 18, 0, 0), (0.8, -10, 0, 0), (1.0, 0, 0, 0), (1.4, 0, 0, 0)))
    tell.pos("body", keys((0, 0, 0, 0), (0.25, 0, -1, -0.5), (0.6, 0, -1, -0.5), (0.85, 0, 3.5, 0), (1.05, 0, 0, 0), (1.4, 0, 0, 0)))
    for nm in ("fleg_l", "fleg_r"):
        tell.rot(nm, keys((0, 0, 0, 0), (0.25, -60, 0, 0), (0.6, -60, 0, 0), (0.85, -30, 0, 0), (1.05, 0, 0, 0), (1.4, 0, 0, 0)))
    tw = [(0, -35, 0, 0)]
    for k in range(10):
        tw.append((0.1 + k * 0.12, -45, 40 if k % 2 == 0 else -40, 0))
    tw.append((1.4, -35, 0, 0))
    tell.rot("tail", tw, "lin")
    nudge = clip_of(r, "nudge", 1.0)
    # pushes the dull one off: head down, a shove forward with the nose
    nudge.rot("neck", keys((0, -10, 0, 0), (0.25, 40, 0, 0), (0.45, 40, 0, 0), (0.6, 30, 0, 0), (1.0, -10, 0, 0)))
    nudge.pos("body", keys((0, 0, 0, 0), (0.45, 0, 0, 0), (0.55, 0, 0, -1.5), (0.75, 0, 0, 0), (1.0, 0, 0, 0)))
    nudge.rot("head", keys((0, 10, 0, 0), (0.6, 30, 0, 0), (0.8, 10, -15, 0), (1.0, 10, 0, 0)))
    work = clip_of(r, "work", 1.2, loop=True)
    # sorting: head down over the barrel's spill, paws turning stones, a toss of the head now and then
    hold(work, "body", rot=(12, 0, 0))
    cycle(work, "neck", "rot", lambda p: (30 + 10 * s(p * 2), 15 * s(p), 0), 8)
    cycle(work, "fleg_l", "rot", lambda p: (-35 * max(0, s(p * 2)), 0, 0), 8)
    cycle(work, "fleg_r", "rot", lambda p: (-35 * max(0, s(p * 2, 0.5)), 0, 0), 8)
    cycle(work, "tail", "rot", lambda p: (-30, 15 * s(p * 2), 0), 8)
    sleep = clip_of(r, "sleep", 4.0, loop=True)
    # lying down, chin on the paws, tail curled round
    hold(sleep, "body", pos=(0, -4.2, 0))
    for nm in ("fleg_l", "fleg_r"):
        hold(sleep, nm, rot=(-85, 0, 0))
    for nm in ("hleg_l", "hleg_r"):
        hold(sleep, nm, rot=(80, 0, 0))
    hold(sleep, "neck", rot=(30, 0, 0))
    hold(sleep, "head", rot=(0, 0, 0))
    hold(sleep, "tail", rot=(10, 60, 0))
    hold(sleep, "ear_l", rot=(0, 0, 60))
    hold(sleep, "ear_r", rot=(0, 0, -60))
    cycle(sleep, "body", "scale", lambda p: (1 + 0.03 * s(p), 1 + 0.04 * s(p), 1), 8)
    sniff = clip_of(r, "sniff", 1.2)
    sniff.rot("neck", keys((0, -10, 0, 0), (0.3, 40, 0, 0), (0.9, 40, 0, 0), (1.2, -10, 0, 0)))
    sk = [(0, 10, 0, 0)]
    for k in range(5):
        sk += [(0.3 + k * 0.12, 22, 0, 0), (0.36 + k * 0.12, 16, 0, 0)]
    sk.append((1.2, 10, 0, 0))
    sniff.rot("head", sk, "lin")
    carry = clip_of(r, "carry", 0.6, loop=True)
    trot(carry, 30, 0.5, 20)
    hold(carry, "neck", rot=(-25, 0, 0))
    hide = clip_of(r, "hide", 1.0, loop=True)
    hold(hide, "body", pos=(0, -2.5, 0))
    for nm in ("fleg_l", "fleg_r"):
        hold(hide, nm, rot=(-40, 0, 0))
    for nm in ("hleg_l", "hleg_r"):
        hold(hide, nm, rot=(45, 0, 0))
    hold(hide, "tail", rot=(20, 0, 0))
    hold(hide, "ear_l", rot=(30, 0, 70))
    hold(hide, "ear_r", rot=(30, 0, -70))
    stubs(r)
    return r


def paint_pebblepup(r, P):
    import random
    stone = Ramp("#2e3036", "#43464e", "#5a5e66", "#72767e", "#8c9098", "#a8acb2", "#c2c6ca")
    slate = Ramp("#22242c", "#30343e", "#40444e", "#525662")
    rng = random.Random("pebblepup")
    pat = combine(p_stone(rng, 1.0, 3), p_blotch("pebblepup", 4, 0.4))
    for cb in ("torso", "chest", "oneck", "ohead", "ofleg_l", "otail"):
        P.paint(cb, stone, 3.6, pat)
    face_fill(P, "chest", "north", lambda f, i, j: stone.at(4.6 + (0.4 if (i + j) % 3 == 0 else 0)))
    P.paint("muzzle", Ramp("#6a6e74", "#868a90", "#a2a6aa", "#bcc0c2"), 2.6, p_stone(rng, 0.6, 2))
    P.paint("nose", Ramp("#101014", "#1c1c22", "#2a2a32"), 1.6)
    P.paint("oear_l", slate, 2.6, p_stone(rng, 0.5, 2))
    P.paint("fpaw_l", slate, 2.4)
    face_fill(P, "fpaw_l", "north", lambda f, i, j: slate.at(1.0) if i % 2 == 1 else None)
    # its hoard worn on its back: amethyst, a quartz point, a copper-green chip
    for cb, col in (("gem_a", ("#4a2a6a", "#6e42a0", "#9a6ad0", "#c8a2f0")), ("gem_b", ("#8a8a90", "#c2c2c8", "#ececf0", "#ffffff")),
                    ("gem_c", ("#2a6a5a", "#3a9078", "#5ab89a", "#9ae0c8"))):
        P.paint(cb, Ramp(*col), 2.4, lambda f, i, j: 0.9 if (i + j) % 3 == 0 else 0.0)
        P.glow_face(cb, "top", lambda f, i, j, col=col: hexc(col[3]) if (i + j) % 2 == 0 else None)
    # flecks in the stone that catch the light
    for cb in ("torso", "ohead"):
        for fname in ("west", "east", "top"):
            f = P.face(cb, fname)
            for k in range(3):
                i, j = rng.randrange(f.w), rng.randrange(f.h)
                f.set(i, j, hexc("#d8dce8"))
    with P.eyes():
        for fname in ("west", "east"):
            f = P.face("ohead", fname)
            ex = front_i(f) + (1 if fname == "west" else -1)
            for j in (1, 2):
                f.set(ex, j, hexc("#0c0c10"))
                f.set(ex + (1 if fname == "west" else -1), j, hexc("#0c0c10"))
            f.set(ex, 1, hexc("#e8c860"))
    return P.skin, P.glow


# =====================================================================================================================

DESIGNS = {"kin_lampwing": (lampwing, paint_lampwing), "kin_burrowbun": (burrowbun, paint_burrowbun),
           "kin_mossback": (mossback, paint_mossback), "kin_pebblepup": (pebblepup, paint_pebblepup)}


def rigs(only=None):
    return [(rid, d(), p) for rid, (d, p) in DESIGNS.items() if not only or rid in only]


def textures(rig, painter):
    P = R.Painter(rig, "kin|" + rig.id)
    skin, glow = painter(rig, P)
    return skin, glow, P.blink()


def java(all_rigs):
    chunks = []
    for rid, rig, _ in all_rigs:
        for cl in rig.clips.values():
            for (bone, _), _keys in cl.tracks.items():
                if bone not in rig.by_name:
                    raise SystemExit(f"{rid}/{cl.name}: no bone {bone}")
        chunks.append((rid, rig.java_lines()))
    src = ["package dev.awaken.kin.client;", "", "import java.util.List;", "", "/**",
           " * GENERATED by tools/art/specs/kin.py: do not edit by hand. The kin's bodies as plain data, in the hero rigs'",
           " * format ({@link dev.awaken.mobs.client.anim.HeroRig#parse}); textures in textures/entity/kin/.",
           " */", "public final class KinData {", "    private KinData() {}", "",
           "    /** Render scale per kind (designed larger than it stands). */",
           "    public static final java.util.Map<String, Float> SCALE = java.util.Map.ofEntries("]
    src.append(",\n".join(f'            java.util.Map.entry("{rid}", {SCALE[rid]}F)' for rid, _, _ in all_rigs))
    src.append("    );")
    src.append("")
    src.append("    /** The life layer's temperament per kind. */")
    src.append("    public static final java.util.Map<String, String> TEMPER = java.util.Map.ofEntries(")
    src.append(",\n".join(f'            java.util.Map.entry("{rid}", "{TEMPER[rid]}")' for rid, _, _ in all_rigs))
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
    for rid, rig, painter in all_rigs:
        skin, glow, blink = textures(rig, painter)
        name = rid[len("kin_"):]
        out.entity("kin/" + name, skin)
        if any(px[3] for row in glow.px for px in row):
            out.entity("kin/" + name + "_glow", glow)
        if blink is not None:
            out.entity("kin/" + name + "_blink", blink[0])
    path = java(all_rigs)
    print(f"kin: {len(all_rigs)} rigs -> {os.path.relpath(path, ROOT)}")


def preview(outdir, only=None):
    """One sheet: each kind from three sides, then a strip of its clips."""
    from PIL import Image, ImageDraw
    os.makedirs(outdir, exist_ok=True)
    rows = []
    for rid, rig, painter in rigs(only):
        skin, glow, _ = textures(rig, painter)
        lit = Image.alpha_composite(skin.image(), glow.image())
        poses = [(nm, [(nm, cl.length * 0.35, 1.0)]) for nm, cl in rig.clips.items() if nm not in ("hurt", "death")]
        trees = [rig.tree(rig.sample(layers)) for _, layers in poses]
        yaw, pitch = 40, 16
        b = R.bounds_of(trees, yaw, pitch)
        pc = (150, 140)
        px = min((pc[0] - 16) / max(1e-6, b[1] - b[0]), (pc[1] - 24) / max(1e-6, b[3] - b[2]))
        per = 6
        nrow = (len(poses) + per - 1) // per
        strip = Image.new("RGBA", (pc[0] * per, (pc[1] + 14) * nrow), (40, 44, 54, 255))
        d = ImageDraw.Draw(strip)
        for k, ((label, _), t) in enumerate(zip(poses, trees)):
            im = R.render(t, lit, yaw=yaw, pitch=pitch, px=px, bounds=b,
                          origin=(pc[0], pc[1], (pc[0] - (b[1] - b[0]) * px) / 2 - b[0] * px, pc[1] - 8 - b[3] * px))
            bg = Image.new("RGBA", pc, (110, 128, 140, 255))
            bg.alpha_composite(im)
            x0, y0 = (k % per) * pc[0], (k // per) * (pc[1] + 14)
            strip.paste(bg, (x0, y0))
            d.text((x0 + 3, y0 + pc[1] + 1), label, fill=(220, 225, 235, 255))
        rows.append(strip)
    sheet = Image.new("RGBA", (max(r_.width for r_ in rows), sum(r_.height for r_ in rows)), (24, 26, 34, 255))
    y = 0
    for r_ in rows:
        sheet.paste(r_, (0, y))
        y += r_.height
    path = os.path.join(outdir, "kin.png")
    sheet.save(path)
    return [path]


if __name__ == "__main__":
    if len(sys.argv) > 2 and sys.argv[1] == "preview":
        for p in preview(sys.argv[2], sys.argv[3:] or None):
            print(p)
