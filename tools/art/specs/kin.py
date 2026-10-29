"""The kin (SPEC §43.5): the land's own beasts that can live with you. Stage 2 brings four, each with its own look:

  kin_lampwing   a moth the size of a cat, a fluffy ruff, feathered antennae, a round belly that glows like a lamp
  kin_burrowbun  a round, sandy digger with long laid-back ears, big pale front claws and a pink nose
  kin_mossback   a slow tortoise whose shell carries a little garden: grass, a fern and two flowers
  kin_pebblepup  a stone-skinned pup with floppy slate ears and shiny crystals set along its back

Stage 5 brings the working eight (SPEC §43.12):

  kin_totter     a round blue beetle with a row of pale spots down each wing case; it pops them open to buzz
  kin_thatchback a beaver-badger: a pale stripe from nose to back, orange teeth, a leathery paddle of a tail
  kin_emberkit   a salamander kitten, ears and all, with embers glowing down its back and at its tail's tip
  kin_puffwool   a cloud of a sheep, all curls, on four tiny dark legs
  kin_dumplin    a round butter-yellow bird with a russet plume, too round to fly
  kin_pondling   a frog-axolotl: a wide smile, gold eyes, coral gills that glow at the tips
  kin_snufflet   a striped wild piglet with a long, never-still snout
  kin_chimewren  a small blue songbird with a cocked, barred tail and a brass crest that catches the light

Stage 6 brings the last four (SPEC §43.13):

  kin_bumblepuff  a fuzzy gold-and-black bee as big as a loaf, on wings too small for it, so it bobs as it floats
  kin_loomworm    a fat leaf-green caterpillar with cream tufts and a round face; it rears for leaves and weaves
  kin_cloudcalf   a sky-blue whale calf with a pale throat, star freckles that glow at dusk, a little cloud on its back
  kin_boulderback a mossy, tusked hill of a beast with stones grown into its back and small eyes that shine green

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

SCALE = {"kin_lampwing": 0.55, "kin_burrowbun": 0.62, "kin_mossback": 0.85, "kin_pebblepup": 0.7,
         "kin_totter": 0.85, "kin_thatchback": 0.9, "kin_emberkit": 0.75, "kin_puffwool": 0.8,
         "kin_dumplin": 0.5, "kin_pondling": 0.85, "kin_snufflet": 0.9, "kin_chimewren": 0.45,
         "kin_bumblepuff": 0.6, "kin_loomworm": 0.7, "kin_cloudcalf": 1.0, "kin_boulderback": 1.0}
TEMPER = {"kin_lampwing": "cute", "kin_burrowbun": "cute", "kin_mossback": "grazer", "kin_pebblepup": "cute",
          "kin_totter": "cute", "kin_thatchback": "grazer", "kin_emberkit": "cute", "kin_puffwool": "grazer",
          "kin_dumplin": "cute", "kin_pondling": "cute", "kin_snufflet": "cute", "kin_chimewren": "cute",
          "kin_bumblepuff": "cute", "kin_loomworm": "cute", "kin_cloudcalf": "grazer", "kin_boulderback": "grazer"}


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
# The working kin (stage 5, SPEC §43.12): eight more, each with its own body and its own way of moving
# =====================================================================================================================

def gait4(cl, swing, phases, rest=None):
    """Legs swinging fore and aft from their rest pose; phases is [(bone, phase)]."""
    rest = rest or {}
    for nm, ph in phases:
        rx, ry, rz = rest.get(nm, (0, 0, 0))
        cycle(cl, nm, "rot", lambda p, ph=ph, rx=rx, ry=ry, rz=rz: (rx + swing * s(p, ph), ry, rz), 8)


def spots_row(f, i, j):
    """A neat row of pale spots down the middle of a top face (the Totter's wing cases)."""
    return hexc("#e8eef6") if f.name == "top" and i == f.w // 2 and j % 2 == 1 and 0 < j < f.h - 1 else None


def side_eye(P, cube, glint, dark, row=1, tall=2):
    """An eye at the front of each side face: a glint over the dark."""
    with P.eyes():
        for fname in ("west", "east"):
            f = P.face(cube, fname)
            ex = front_i(f) + (1 if fname == "west" else -1)
            for j in range(row, row + tall):
                f.set(ex, j, hexc(dark))
            f.set(ex, row, hexc(glint))


# --- Totter: a round beetle that carries things in neat rows ---------------------------------------------------------

def totter():
    r = Rig("kin_totter", (64, 64))
    r.walk_stride, r.run_stride, r.run_speed, r.shadow = 0.5, 1.1, (0.1, 0.22), 0.35
    body = r.bone("body", (0, 20.4, 0))
    body.cube("belly", -3.5, -1, -4, 7, 3, 8)
    body.cube("pronotum", -3, -3, -5.5, 6, 3, 3)
    head = body.bone("head", (0, -1, -5.5))
    head.cube("ohead", -2, -1.5, -3, 4, 3, 3)
    head.cube("mandible", -1.5, 1, -3.6, 3, 1, 1)

    def side(sd, nm, mir, sh):
        el = body.bone("ely_" + nm, (sd * 0.05, -1.2, -3))
        if sd > 0:
            el.cube("shell_l", 0, -4, 0, 4, 4, 8)
        else:
            el.cube("shell_r", -4, -4, 0, 4, 4, 8, mirror=True, share="shell_l")
        wg = body.bone("wing_" + nm, (sd * 0.5, -2.6, -2))
        if sd > 0:
            wg.cube("hwing_l", 0, 0, 0, 3, 0, 7)
        else:
            wg.cube("hwing_r", -3, 0, 0, 3, 0, 7, mirror=True, share="hwing_l")
        ant = head.bone("ant_" + nm, (sd * 1.1, -1.4, -2.2), (-30, 0, sd * 22))
        ant.cube("stalk_" + nm, -0.5, -4, 0, 1, 4, 0, mirror=mir, share=sh("stalk_l"))
        ant.cube("club_" + nm, -1, -6, -0.5, 2, 2, 1, mirror=mir, share=sh("club_l"))
        for k, z in enumerate((-2.6, 0.2, 3)):
            lg = body.bone(f"leg{k}_" + nm, (sd * 3.2, 0.5, z), (0, sd * (20, 0, -20)[k], -sd * 40))
            lg.cube(f"oleg{k}_" + nm, -0.5, 0, -0.5, 1, 4, 1, mirror=mir, share=None if (k == 0 and sd > 0) else "oleg0_l")
    sym(side)
    r.look = [("head", 1.0, 0.8)]
    r.pack()

    legs = [(f"leg{k}_{nm}", sd) for k in range(3) for nm, sd in (("l", 1), ("r", -1))]
    rest = {nm: (0, sd * (20, 0, -20)[int(nm[3])], -sd * 40) for nm, sd in legs}
    tripod = [(nm, 0.0 if (int(nm[3]) + (0 if sd > 0 else 1)) % 2 == 0 else 0.5) for nm, sd in legs]

    def scuttle(cl, swing, bob):
        gait4(cl, swing, tripod, rest)
        cycle(cl, "body", "pos", lambda p: (0, bob * abs(s(p * 2)), 0), 8)
        for nm, sd in (("l", 1), ("r", -1)):
            cycle(cl, "ant_" + nm, "rot", lambda p, sd=sd: (-40 + 6 * s(p * 2, 0.2), 0, sd * 22), 8)

    idle = clip_of(r, "idle", 3.0, loop=True)
    # feelers twitching in turn, a look to one side
    for nm, sd, ph in (("l", 1, 0.0), ("r", -1, 0.35)):
        idle.rot("ant_" + nm, keys((0, -30, 0, sd * 22), (0.4 + ph, -30, 0, sd * 22), (0.55 + ph, -12, 0, sd * 30), (0.75 + ph, -30, 0, sd * 22),
                                   (1.9 + ph, -30, 0, sd * 22), (2.05 + ph, -40, sd * 15, sd * 22), (2.3 + ph, -30, 0, sd * 22), (3.0, -30, 0, sd * 22)))
    idle.rot("head", keys((0, 0, 0, 0), (1.2, 0, 0, 0), (1.4, 6, 18, 0), (2.2, 6, 18, 0), (2.4, 0, 0, 0), (3.0, 0, 0, 0)))
    cycle(idle, "body", "scale", lambda p: (1 + 0.015 * s(p * 2), 1 + 0.02 * s(p * 2), 1), 8)
    move = clip_of(r, "move", 0.5, loop=True)
    scuttle(move, 22, 0.25)
    run = clip_of(r, "run", 0.3, loop=True)
    scuttle(run, 32, 0.4)
    want = clip_of(r, "want", 3.4, loop=True)
    # carries a thing to the next heap, sets it down, frets at it, turns this way and that, and goes back
    gait4(want, 12, tripod, rest)
    want.pos("body", keys((0, 0, 0, 0), (1.0, 0, 0, -2.5), (2.4, 0, 0, -2.5), (3.4, 0, 0, 0)))
    want.rot("body", keys((0, 0, 0, 0), (1.0, 0, 0, 0), (1.2, 8, 0, 0), (1.5, 0, 0, 0), (1.8, 0, 25, 0), (2.1, 0, -25, 0), (2.4, 0, 0, 0), (3.4, 0, 0, 0)))
    want.rot("head", keys((0, 0, 0, 0), (1.0, 0, 0, 0), (1.25, 25, 0, 0), (1.5, 0, 0, 0), (3.4, 0, 0, 0)))
    tell = clip_of(r, "tell", 1.4)
    # pops its wing cases, buzzes its wings and hops, then snaps shut
    for nm, sd in (("l", 1), ("r", -1)):
        tell.rot("ely_" + nm, keys((0, 0, 0, 0), (0.15, 15, sd * 20, -sd * 40), (1.0, 15, sd * 20, -sd * 40), (1.2, 0, 0, 0), (1.4, 0, 0, 0)))
        wk = [(0, 0, 0, 0), (0.15, 0, sd * 55, 0)]
        for k in range(8):
            wk.append((0.2 + k * 0.1, 0, sd * 55, -sd * (30 if k % 2 == 0 else -10)))
        wk += [(1.15, 0, 0, 0), (1.4, 0, 0, 0)]
        tell.rot("wing_" + nm, wk, "lin")
    tell.pos("body", keys((0, 0, 0, 0), (0.2, 0, -0.5, 0), (0.45, 0, 3, 0), (0.8, 0, 3.5, 0), (1.05, 0, 0, 0), (1.4, 0, 0, 0)))
    tell.rot("head", keys((0, 0, 0, 0), (0.3, -15, 0, 0), (1.0, -15, 0, 0), (1.4, 0, 0, 0)))
    nudge = clip_of(r, "nudge", 1.0)
    # bumps you with its head, twice
    nudge.pos("body", keys((0, 0, 0, 0), (0.25, 0, 0, -1.2), (0.4, 0, 0, 0), (0.6, 0, 0, -1.2), (0.8, 0, 0, 0), (1.0, 0, 0, 0)))
    nudge.rot("head", keys((0, 0, 0, 0), (0.25, 15, 0, 0), (0.4, 0, 0, 0), (0.6, 15, 0, 0), (1.0, 0, 0, 0)))
    work = clip_of(r, "work", 1.6, loop=True)
    # setting things in rows: front legs patting in turn, the head bobbing over each, a shuffle along
    hold(work, "body", rot=(6, 0, 0))
    cycle(work, "leg0_l", "rot", lambda p: (-30 * max(0, s(p * 2)), 20, -40), 8)
    cycle(work, "leg0_r", "rot", lambda p: (-30 * max(0, s(p * 2, 0.5)), -20, 40), 8)
    cycle(work, "head", "rot", lambda p: (12 + 10 * s(p * 2), 0, 0), 8)
    cycle(work, "body", "pos", lambda p: (0.6 * s(p), 0, 0), 8)
    sleep = clip_of(r, "sleep", 4.0, loop=True)
    # down on its belly, legs drawn in, feelers laid back along the shell
    hold(sleep, "body", pos=(0, -2.0, 0))
    for nm, sd in legs:
        hold(sleep, nm, rot=(0, rest[nm][1], -sd * 85))
    for nm, sd in (("l", 1), ("r", -1)):
        hold(sleep, "ant_" + nm, rot=(-85, 0, sd * 10))
    cycle(sleep, "body", "scale", lambda p: (1 + 0.02 * s(p), 1 + 0.03 * s(p), 1), 8)
    sniff = clip_of(r, "sniff", 1.2)
    sniff.rot("head", keys((0, 0, 0, 0), (0.25, 14, 0, 0), (0.9, 14, 0, 0), (1.2, 0, 0, 0)))
    for nm, sd in (("l", 1), ("r", -1)):
        ak = [(0, -30, 0, sd * 22)]
        for k in range(4):
            ak += [(0.2 + k * 0.2, 10, 0, sd * (12 if k % 2 else 30)), (0.3 + k * 0.2, -5, 0, sd * 22)]
        ak.append((1.2, -30, 0, sd * 22))
        sniff.rot("ant_" + nm, ak)
    carry = clip_of(r, "carry", 0.5, loop=True)
    scuttle(carry, 20, 0.2)
    hold(carry, "head", rot=(-6, 0, 0))
    hide = clip_of(r, "hide", 1.0, loop=True)
    # shut up tight like a pebble, legs in, trembling
    for nm, sd in legs:
        hold(hide, nm, rot=(0, rest[nm][1], -sd * 80))
    for nm, sd in (("l", 1), ("r", -1)):
        hold(hide, "ant_" + nm, rot=(-90, 0, sd * 5))
    hold(hide, "head", pos=(0, 0, 1))
    cycle(hide, "body", "pos", lambda p: (0.12 * s(p * 5), -2, 0), 24)
    stubs(r)
    return r


def paint_totter(r, P):
    shell = Ramp("#16223a", "#223458", "#304878", "#42629a", "#5e86b4", "#86a8d0", "#c0d6ee")
    dark = Ramp("#0c0e16", "#161a26", "#222838", "#30384c")
    P.paint("shell_l", shell, 3.4, p_blotch("totter", 3, 0.2))
    face_fill(P, "shell_l", "top", spots_row)
    for fname in ("west", "east"):
        face_fill(P, "shell_l", fname, lambda f, i, j: shell.at(5.6) if j == 0 else (hexc("#e8eef6") if j == 1 and i % 3 == 1 else None))
    P.paint("pronotum", dark, 2.6)
    face_fill(P, "pronotum", "top", lambda f, i, j: hexc("#e8eef6") if (i, j) in ((1, 1), (f.w - 2, 1)) else None)
    P.paint("belly", dark, 2.2, R.p_bands(2, 0.6))
    P.paint("ohead", dark, 2.4)
    P.paint("mandible", Ramp("#3a2a1a", "#5a4428", "#7a6038"), 2.0)
    P.paint("oleg0_l", dark, 2.0, R.p_bands(2, 0.5))
    for fname in ("north", "south"):
        face_fill(P, "stalk_l", fname, lambda f, i, j: dark.at(2.6))
    P.paint("club_l", shell, 4.4)
    face_fill(P, "hwing_l", "top", lambda f, i, j: hexc("#b8c8dc") if (i + j) % 4 else hexc("#8a9cb4"))
    face_fill(P, "hwing_l", "bottom", lambda f, i, j: hexc("#a8b8cc") if (i + j) % 4 else hexc("#7a8ca4"))
    side_eye(P, "ohead", "#e8f0f8", "#101420", row=0)
    return P.skin, P.glow


# --- Thatchback: a beaver-badger that carries planks on its tail ------------------------------------------------------

def thatchback():
    r = Rig("kin_thatchback", (64, 64))
    r.walk_stride, r.run_stride, r.run_speed, r.shadow = 0.6, 1.4, (0.08, 0.2), 0.45
    body = r.bone("body", (0, 18, 0))
    body.cube("torso", -4, -3.5, -5, 8, 7, 10)
    body.cube("hump", -3, -4.5, -3, 6, 1, 6)
    head = body.bone("head", (0, -0.5, -5), (6, 0, 0))
    head.cube("ohead", -3, -3, -4, 6, 5, 4)
    head.cube("snout", -2, -0.5, -6, 4, 3, 2)
    head.cube("nose", -1, -1, -6.6, 2, 1, 1)
    head.cube("teeth", -1, 2.5, -5.8, 2, 1, 0)

    def side(sd, nm, mir, sh):
        ear = head.bone("ear_" + nm, (sd * 2.4, -3, -1.5), (0, 0, sd * 10))
        ear.cube("oear_" + nm, -1, -2, -0.5, 2, 2, 1, mirror=mir, share=sh("oear_l"))
        fl = body.bone("fleg_" + nm, (sd * 2.8, 3, -3))
        fl.cube("ofleg_" + nm, -1.5, 0, -1.5, 3, 3, 3, mirror=mir, share=sh("ofleg_l"))
        hl = body.bone("hleg_" + nm, (sd * 2.8, 3, 3))
        hl.cube("ohleg_" + nm, -1.5, 0, -1.5, 3, 3, 3, mirror=mir, share="ofleg_l")
    sym(side)
    tail = body.bone("tail", (0, 0.5, 5), (-35, 0, 0))
    tail.cube("paddle", -2.5, -0.5, 0, 5, 1, 7)
    r.look = [("head", 1.0, 0.8)]
    r.pack()

    legs4 = [("fleg_l", 0), ("hleg_r", 0.03), ("fleg_r", 0.5), ("hleg_l", 0.53)]

    def waddle(cl, swing, roll, wag):
        gait4(cl, swing, legs4)
        cycle(cl, "body", "rot", lambda p: (0, 0, roll * s(p)), 8)
        cycle(cl, "body", "pos", lambda p: (0, 0.3 * abs(s(p)), 0), 8)
        cycle(cl, "tail", "rot", lambda p: (-35 + 4 * s(p * 2), wag * s(p, 0.15), 0), 8)
        cycle(cl, "head", "rot", lambda p: (6 + 3 * s(p * 2, 0.1), 0, -roll * 0.6 * s(p)), 8)

    idle = clip_of(r, "idle", 3.6, loop=True)
    cycle(idle, "tail", "rot", lambda p: (-35, 14 * s(p), 0), 8)
    idle.rot("head", keys((0, 6, 0, 0), (1.0, 6, 0, 0), (1.3, -4, 25, 0), (2.2, -4, 25, 0), (2.5, 6, 0, 0), (3.6, 6, 0, 0)))
    cycle(idle, "body", "scale", lambda p: (1 + 0.02 * s(p * 2), 1 + 0.02 * s(p * 2), 1), 8)
    move = clip_of(r, "move", 0.7, loop=True)
    waddle(move, 28, 4, 12)
    run = clip_of(r, "run", 0.42, loop=True)
    gait4(run, 45, [("fleg_l", 0), ("fleg_r", 0.08), ("hleg_l", 0.5), ("hleg_r", 0.58)])
    cycle(run, "body", "rot", lambda p: (8 * s(p, 0.25), 0, 0), 8)
    cycle(run, "body", "pos", lambda p: (0, 1.0 * max(0, s(p)), 0), 8)
    cycle(run, "tail", "rot", lambda p: (-20 + 10 * s(p, 0.4), 0, 0), 8)
    want = clip_of(r, "want", 3.6, loop=True)
    # drags a stick backwards to the water, head low, then looks over its shoulder at its dam and sighs
    gait4(want, 14, legs4)
    want.pos("body", keys((0, 0, 0, 0), (1.4, 0, 0, 2.5), (2.8, 0, 0, 2.5), (3.6, 0, 0, 0)))
    want.rot("head", keys((0, 6, 0, 0), (0.3, 28, 0, 0), (1.4, 28, 0, 0), (1.8, -8, 60, 0), (2.6, -8, 60, 0), (3.0, 10, 0, 0), (3.6, 6, 0, 0)))
    want.scale("body", keys((0, 1, 1, 1), (2.6, 1, 1, 1), (2.85, 1.04, 0.96, 1.02), (3.2, 1, 1, 1), (3.6, 1, 1, 1)))
    tell = clip_of(r, "tell", 1.6)
    # a proud tail-slap: up, smack, up, smack, with a hop on each
    tell.rot("tail", keys((0, -35, 0, 0), (0.25, 40, 0, 0), (0.4, -45, 0, 0), (0.7, 40, 0, 0), (0.85, -45, 0, 0), (1.2, -35, 0, 0), (1.6, -35, 0, 0)), "lin")
    tell.pos("body", keys((0, 0, 0, 0), (0.4, 0, 0, 0), (0.55, 0, 2, 0), (0.85, 0, 0, 0), (1.0, 0, 2.5, 0), (1.25, 0, 0, 0), (1.6, 0, 0, 0)))
    tell.rot("head", keys((0, 6, 0, 0), (0.3, -15, 0, 0), (1.2, -15, 0, 0), (1.6, 6, 0, 0)))
    nudge = clip_of(r, "nudge", 1.0)
    nudge.rot("head", keys((0, 6, 0, 0), (0.2, 6, -25, 0), (0.45, 6, 25, 0), (0.7, 6, -15, 0), (1.0, 6, 0, 0)))
    nudge.rot("tail", keys((0, -35, 0, 0), (0.3, -20, 0, 0), (0.6, -35, 0, 0), (1.0, -35, 0, 0)))
    work = clip_of(r, "work", 1.0, loop=True)
    # gnawing and fitting: head down chewing hard, front paws patting the joint, the tail thumping
    hold(work, "body", rot=(10, 0, 0))
    cycle(work, "head", "rot", lambda p: (26 + 8 * s(p * 4), 6 * s(p), 0), 16)
    cycle(work, "fleg_l", "rot", lambda p: (-35 * max(0, s(p * 2)), 0, 0), 8)
    cycle(work, "fleg_r", "rot", lambda p: (-35 * max(0, s(p * 2, 0.5)), 0, 0), 8)
    cycle(work, "tail", "rot", lambda p: (-35 + 18 * max(0, s(p * 2, 0.2)), 0, 0), 8)
    sleep = clip_of(r, "sleep", 4.5, loop=True)
    # flat on its belly, chin on its paws, the tail laid round to one side
    hold(sleep, "body", pos=(0, -2.6, 0))
    for nm in ("fleg_l", "fleg_r"):
        hold(sleep, nm, rot=(-80, 0, 0))
    for nm in ("hleg_l", "hleg_r"):
        hold(sleep, nm, rot=(80, 0, 0))
    hold(sleep, "head", rot=(14, 0, 0))
    hold(sleep, "tail", rot=(-10, 55, 0))
    cycle(sleep, "body", "scale", lambda p: (1 + 0.03 * s(p), 1 + 0.035 * s(p), 1), 8)
    sniff = clip_of(r, "sniff", 1.2)
    sk = [(0, 6, 0, 0)]
    for k in range(5):
        sk += [(0.2 + k * 0.16, -14, 0, 0), (0.28 + k * 0.16, -8, 0, 0)]
    sk.append((1.2, 6, 0, 0))
    sniff.rot("head", sk, "lin")
    sniff.pos("body", keys((0, 0, 0, 0), (0.3, 0, 1, 0), (0.9, 0, 1, 0), (1.2, 0, 0, 0)))
    carry = clip_of(r, "carry", 0.75, loop=True)
    waddle(carry, 24, 3, 6)
    hide = clip_of(r, "hide", 1.0, loop=True)
    hold(hide, "body", pos=(0, -2, 0))
    for nm in ("fleg_l", "fleg_r"):
        hold(hide, nm, rot=(-45, 0, 0))
    for nm in ("hleg_l", "hleg_r"):
        hold(hide, nm, rot=(45, 0, 0))
    hold(hide, "head", rot=(20, 0, 0), pos=(0, 0, 0.8))
    hold(hide, "tail", rot=(-40, 0, 0))
    hold(hide, "ear_l", rot=(0, 0, 50))
    hold(hide, "ear_r", rot=(0, 0, -50))
    stubs(r)
    return r


def paint_thatchback(r, P):
    fur = Ramp("#24180e", "#36261a", "#4c3624", "#644830", "#7e5c3e", "#9a7450", "#b89068")
    pale = Ramp("#8a7458", "#a88e6c", "#c2a884", "#dcc4a0")
    fpat = combine(p_fur(0.7, 2, 4, 3), p_blotch("thatchback", 4, 0.3))
    for cb in ("torso", "hump", "ohead", "ofleg_l", "oear_l"):
        P.paint(cb, fur, 3.4, fpat)
    # the badger's mark: a pale stripe from the nose over the crown and down the back, dark through the eyes
    stripe = lambda f, i, j: hexc("#e8e0d0") if abs(i - (f.w - 1) / 2) < 0.9 else None
    face_fill(P, "ohead", "top", stripe)
    face_fill(P, "ohead", "north", lambda f, i, j: hexc("#e8e0d0") if abs(i - (f.w - 1) / 2) < 0.9 else fur.at(1.4))
    face_fill(P, "hump", "top", stripe)
    face_fill(P, "torso", "top", lambda f, i, j: hexc("#d8ccb4") if abs(i - (f.w - 1) / 2) < 0.9 and j < f.h // 2 else None)
    for fname in ("west", "east"):
        face_fill(P, "ohead", fname, lambda f, i, j: fur.at(1.2) if j <= 2 else None)
    face_fill(P, "torso", "bottom", lambda f, i, j: pale.at(1.5 + 0.5 * ((i + j) % 2)))
    P.paint("snout", pale, 2.6)
    P.paint("nose", Ramp("#0c0806", "#1a120e", "#2a1e18"), 1.6)
    face_fill(P, "teeth", "north", lambda f, i, j: hexc("#b06a20") if i == f.w // 2 else hexc("#e8a040"))
    face_fill(P, "teeth", "south", lambda f, i, j: hexc("#c08030"))
    # the tail: a leathery paddle, cross-hatched, worn pale across the middle where planks ride
    P.paint("paddle", Ramp("#1a1612", "#2a241e", "#3c342c", "#504638", "#665a48"), 2.8, R.p_scales(2, 2, 0.8))
    face_fill(P, "paddle", "top", lambda f, i, j: hexc("#7a6c58") if j in (2, 3) and 0 < i < f.w - 1 else None)
    face_fill(P, "ofleg_l", "bottom", lambda f, i, j: hexc("#1a120c"))
    side_eye(P, "ohead", "#d8d0c0", "#0a0806")
    return P.skin, P.glow


# --- Emberkit: a salamander kitten that glows from inside -------------------------------------------------------------

def emberkit():
    r = Rig("kin_emberkit", (64, 64))
    r.walk_stride, r.run_stride, r.run_speed, r.shadow = 0.7, 1.5, (0.1, 0.24), 0.3
    body = r.bone("body", (0, 20.5, 0))
    body.cube("torso", -2.5, -2, -4, 5, 4, 8)
    head = body.bone("head", (0, -0.5, -4))
    head.cube("ohead", -3, -2.5, -4, 6, 4, 4)
    head.cube("chin", -2.5, 1.5, -3.5, 5, 1, 3)

    def side(sd, nm, mir, sh):
        ear = head.bone("ear_" + nm, (sd * 1.8, -2.5, -1.2), (-10, 0, sd * 18))
        ear.cube("oear_" + nm, -1, -2, -0.5, 2, 2, 1, mirror=mir, share=sh("oear_l"))
        for part, z in (("fleg", -2.5), ("hleg", 2.5)):
            lg = body.bone(part + "_" + nm, (sd * 2.3, 1, z), (0, 0, -sd * 40))
            lg.cube("o" + part + "_" + nm, -1, 0, -1, 2, 3, 2, mirror=mir, share=sh("ofleg_l") if part == "fleg" else "ofleg_l")
    sym(side)
    tail = body.bone("tail", (0, -0.5, 4), (-6, 0, 0))
    tail.cube("tail1", -1.5, -1.5, 0, 3, 3, 5)
    tail2 = tail.bone("tail2", (0, 0, 5), (-6, 0, 0))
    tail2.cube("otail2", -1, -1, 0, 2, 2, 4)
    tail2.cube("ttip", -0.5, -0.5, 4, 1, 1, 2)
    r.look = [("head", 1.0, 0.8)]
    r.pack()

    rest = {f"{part}_{nm}": (0, 0, -sd * 40) for part in ("fleg", "hleg") for nm, sd in (("l", 1), ("r", -1))}
    diag = [("fleg_l", 0), ("hleg_r", 0), ("fleg_r", 0.5), ("hleg_l", 0.5)]

    def slink(cl, swing, wiggle):
        gait4(cl, swing, diag, rest)
        cycle(cl, "body", "rot", lambda p: (0, wiggle * s(p), 0), 8)
        cycle(cl, "head", "rot", lambda p: (0, -wiggle * 0.8 * s(p), 0), 8)
        cycle(cl, "tail", "rot", lambda p: (-6, -wiggle * 1.4 * s(p, 0.1), 0), 8)
        cycle(cl, "tail2", "rot", lambda p: (-6, -wiggle * 1.6 * s(p, 0.2), 0), 8)

    def curled(cl):
        for nm, sd in (("l", 1), ("r", -1)):
            hold(cl, "fleg_" + nm, rot=(-30, 0, -sd * 80))
            hold(cl, "hleg_" + nm, rot=(30, 0, -sd * 80))
        hold(cl, "tail", rot=(0, 65, 0))
        hold(cl, "tail2", rot=(0, 75, 0))

    idle = clip_of(r, "idle", 3.0, loop=True)
    # the tail curling lazily, slow warm breaths, a kitten's head-tilt
    cycle(idle, "tail", "rot", lambda p: (-6, 18 * s(p), 0), 8)
    cycle(idle, "tail2", "rot", lambda p: (-6, 26 * s(p, 0.12), 0), 8)
    cycle(idle, "body", "scale", lambda p: (1 + 0.03 * s(p * 2), 1 + 0.03 * s(p * 2), 1), 8)
    idle.rot("head", keys((0, 0, 0, 0), (1.1, 0, 0, 0), (1.3, 0, 10, 16), (2.1, 0, 10, 16), (2.3, 0, 0, 0), (3.0, 0, 0, 0)))
    move = clip_of(r, "move", 0.6, loop=True)
    slink(move, 30, 10)
    run = clip_of(r, "run", 0.36, loop=True)
    slink(run, 45, 14)
    want = clip_of(r, "want", 1.6, loop=True)
    # curled up tight and shivering, even here
    curled(want)
    hold(want, "head", rot=(10, 40, 0))
    hold(want, "ear_l", rot=(-40, 0, 40))
    hold(want, "ear_r", rot=(-40, 0, -40))
    cycle(want, "body", "pos", lambda p: (0.18 * s(p * 8), -1.4, 0), 32)
    tell = clip_of(r, "tell", 1.5)
    # a kitten's pounce: a wiggle down, a spring, a landing with the tail straight up
    tell.pos("body", keys((0, 0, 0, 0), (0.35, 0, -0.8, 0.5), (0.55, 0, -0.8, 0.5), (0.8, 0, 3, -2), (1.05, 0, 0, -2.5), (1.5, 0, 0, 0)))
    tell.rot("body", keys((0, 0, 0, 0), (0.15, 0, 6, 0), (0.25, 0, -6, 0), (0.35, 0, 6, 0), (0.45, 0, -6, 0), (0.55, 0, 0, 0), (0.8, -15, 0, 0), (1.05, 6, 0, 0), (1.5, 0, 0, 0)))
    tell.rot("tail", keys((0, -6, 0, 0), (0.6, 10, 0, 0), (1.05, 55, 0, 0), (1.4, 55, 0, 0), (1.5, -6, 0, 0)))
    tell.rot("tail2", keys((0, -6, 0, 0), (1.05, 20, 0, 0), (1.4, 20, 0, 0), (1.5, -6, 0, 0)))
    nudge = clip_of(r, "nudge", 1.0)
    # rubs its head against you like a cat
    nudge.rot("head", keys((0, 0, 0, 0), (0.25, -5, 20, 20), (0.5, -5, -15, -15), (0.75, -5, 20, 20), (1.0, 0, 0, 0)))
    nudge.rot("body", keys((0, 0, 0, 0), (0.25, 0, 8, 0), (0.5, 0, -8, 0), (0.75, 0, 8, 0), (1.0, 0, 0, 0)))
    work = clip_of(r, "work", 3.0, loop=True)
    # stretched against the warm stone, legs splayed, the tail's tip flicking
    hold(work, "body", pos=(0, -1.6, 0))
    for nm, sd in (("l", 1), ("r", -1)):
        hold(work, "fleg_" + nm, rot=(-60, 0, -sd * 75))
        hold(work, "hleg_" + nm, rot=(60, 0, -sd * 75))
    hold(work, "head", rot=(8, 0, 0))
    cycle(work, "tail2", "rot", lambda p: (-6, 20 * max(0, s(p * 2)), 0), 8)
    cycle(work, "body", "scale", lambda p: (1 + 0.04 * s(p), 1 + 0.04 * s(p), 1), 8)
    sleep = clip_of(r, "sleep", 4.0, loop=True)
    # curled round its own tail
    curled(sleep)
    hold(sleep, "body", pos=(0, -1.4, 0))
    hold(sleep, "head", rot=(14, 50, 0))
    cycle(sleep, "body", "scale", lambda p: (1 + 0.035 * s(p), 1 + 0.04 * s(p), 1), 8)
    sniff = clip_of(r, "sniff", 1.2)
    sniff.rot("head", keys((0, 0, 0, 0), (0.3, 18, -15, 0), (0.6, 18, 15, 0), (0.9, 18, -8, 0), (1.2, 0, 0, 0)))
    carry = clip_of(r, "carry", 0.66, loop=True)
    slink(carry, 26, 8)
    hide = clip_of(r, "hide", 1.0, loop=True)
    for nm, sd in (("l", 1), ("r", -1)):
        hold(hide, "fleg_" + nm, rot=(0, 0, -sd * 80))
        hold(hide, "hleg_" + nm, rot=(0, 0, -sd * 80))
        hold(hide, "ear_" + nm, rot=(-45, 0, sd * 45))
    hold(hide, "tail", rot=(-10, 40, 0))
    cycle(hide, "body", "pos", lambda p: (0.12 * s(p * 5), -1.4, 0), 24)
    stubs(r)
    return r


def paint_emberkit(r, P):
    skin = Ramp("#2c0a06", "#4a1408", "#74220c", "#a03414", "#c84c1e", "#e46a2c", "#f69048")
    belly = Ramp("#a0501a", "#c8702a", "#e4943a", "#f6b85a")
    pat = combine(R.p_scales(2, 2, 0.4), p_blotch("emberkit", 3, 0.5))
    for cb in ("torso", "ohead", "tail1", "otail2", "ofleg_l", "oear_l", "ttip"):
        P.paint(cb, skin, 3.4, pat)
    for cb in ("torso", "tail1"):
        face_fill(P, cb, "bottom", lambda f, i, j: belly.at(2.0 + 0.5 * ((i + j) % 2)))
    P.paint("chin", belly, 2.6)

    # the fire inside shows through: embers down the back and tail, the ears' insides, the tail's tip
    def embers(f, i, j):
        if i in (1, f.w - 2) and j % 2 == 1:
            return hexc("#ffc45a")
        if i == f.w // 2 and j % 3 == 0:
            return hexc("#ff8a30")
        return None
    for cb in ("torso", "tail1", "otail2"):
        face_fill(P, cb, "top", embers)
        P.glow_face(cb, "top", embers)
    for fname in ("north", "south", "west", "east", "top", "bottom"):
        P.glow_face("ttip", fname, lambda f, i, j: hexc("#ffd070"))
    P.glow_face("oear_l", "north", lambda f, i, j: hexc("#ff9a40") if j == f.h - 1 else None)
    with P.eyes():
        for fname in ("west", "east"):
            f = P.face("ohead", fname)
            step = 1 if fname == "west" else -1
            ex = front_i(f) + step
            for di in (0, 1):
                for j in (1, 2):
                    f.set(ex + step * di, j, hexc("#ffd040"))
            f.set(ex, 2, hexc("#1a0804"))
    return P.skin, P.glow


# --- Puffwool: a cloud of a sheep on tiny legs ------------------------------------------------------------------------

def puffwool():
    r = Rig("kin_puffwool", (64, 64))
    r.walk_stride, r.run_stride, r.run_speed, r.shadow = 0.45, 1.1, (0.07, 0.18), 0.5
    body = r.bone("body", (0, 15, 0))
    wool = body.bone("wool", (0, 0, 0))
    wool.cube("fleece", -5, -5, -5.5, 10, 9, 11)
    wool.cube("puff_top", -3.5, -7, -3.5, 7, 2, 7)
    wool.cube("puff_side", -6, -3, -3.5, 12, 5, 6)
    wool.cube("puff_rump", -3.5, -3.5, 5.5, 7, 6, 2)
    head = body.bone("head", (0, 0.5, -5.5), (12, 0, 0))
    head.cube("face", -2, -2.5, -3, 4, 5, 3)
    head.cube("tuft", -2.5, -4, -2.5, 5, 2, 3)

    def side(sd, nm, mir, sh):
        ear = head.bone("ear_" + nm, (sd * 2, -1.2, -1.5), (0, 0, sd * 25))
        if sd > 0:
            ear.cube("oear_l", 0, -0.5, -1, 3, 1, 2)
        else:
            ear.cube("oear_r", -3, -0.5, -1, 3, 1, 2, mirror=True, share="oear_l")
        for part, z in (("fleg", -3.2), ("hleg", 3.2)):
            lg = body.bone(part + "_" + nm, (sd * 2.8, 4, z))
            lg.cube("o" + part + "_" + nm, -1, 0, -1, 2, 5, 2, mirror=mir, share=sh("ofleg_l") if part == "fleg" else "ofleg_l")
    sym(side)
    r.look = [("head", 1.0, 0.8)]
    r.pack()

    fourl = [("fleg_l", 0), ("hleg_r", 0.02), ("fleg_r", 0.5), ("hleg_l", 0.52)]

    def bounce(cl, swing, hop):
        gait4(cl, swing, fourl)
        cycle(cl, "body", "pos", lambda p: (0, hop * abs(s(p)), 0), 8)
        cycle(cl, "wool", "scale", lambda p: (1 + 0.04 * (1 - abs(s(p))), 1 - 0.05 * (1 - abs(s(p))), 1 + 0.03 * (1 - abs(s(p)))), 8)
        for nm, sd in (("ear_l", 1), ("ear_r", -1)):
            cycle(cl, nm, "rot", lambda p, sd=sd: (0, 0, sd * (25 + 15 * abs(s(p, 0.15)))), 8)

    idle = clip_of(r, "idle", 3.2, loop=True)
    cycle(idle, "wool", "scale", lambda p: (1 + 0.025 * s(p * 2), 1 + 0.03 * s(p * 2), 1 + 0.02 * s(p * 2)), 8)
    idle.rot("head", keys((0, 12, 0, 0), (1.0, 12, 0, 0), (1.3, 6, -20, 0), (2.1, 6, -20, 0), (2.4, 12, 0, 0), (3.2, 12, 0, 0)))
    idle.rot("ear_l", keys((0, 0, 0, 25), (0.6, 0, 0, 25), (0.7, 0, 0, 5), (0.85, 0, 0, 25), (3.2, 0, 0, 25)))
    move = clip_of(r, "move", 0.5, loop=True)
    bounce(move, 30, 1.0)
    run = clip_of(r, "run", 0.34, loop=True)
    gait4(run, 45, [("fleg_l", 0), ("fleg_r", 0.05), ("hleg_l", 0.5), ("hleg_r", 0.55)])
    cycle(run, "body", "pos", lambda p: (0, 2.2 * max(0, s(p)), 0), 8)
    cycle(run, "wool", "scale", lambda p: (1 - 0.04 * s(p), 1 + 0.06 * s(p), 1 - 0.03 * s(p)), 8)
    want = clip_of(r, "want", 3.2, loop=True)
    # rubbing itself on the rocks: a lean and a scrub on one side, then the other
    want.rot("body", keys((0, 0, 0, 0), (0.4, 0, 0, 12), (1.4, 0, 0, 12), (1.8, 0, 0, -12), (2.8, 0, 0, -12), (3.2, 0, 0, 0)))
    want.pos("body", keys((0, 0, 0, 0), (0.4, 1.2, 0, 0), (0.65, 1.2, 0, -0.8), (0.9, 1.2, 0, 0.8), (1.15, 1.2, 0, -0.8), (1.4, 1.2, 0, 0),
                          (1.8, -1.2, 0, 0), (2.05, -1.2, 0, -0.8), (2.3, -1.2, 0, 0.8), (2.55, -1.2, 0, -0.8), (2.8, -1.2, 0, 0), (3.2, 0, 0, 0)))
    want.rot("head", keys((0, 12, 0, 0), (0.4, 18, 0, -10), (1.4, 18, 0, -10), (1.8, 18, 0, 10), (2.8, 18, 0, 10), (3.2, 12, 0, 0)))
    tell = clip_of(r, "tell", 1.6)
    # a big springy boing, the wool puffing out all over
    tell.pos("body", keys((0, 0, 0, 0), (0.3, 0, -1.2, 0), (0.6, 0, 5, 0), (0.85, 0, 5.5, 0), (1.1, 0, 0, 0), (1.25, 0, -0.8, 0), (1.6, 0, 0, 0)))
    tell.scale("wool", keys((0, 1, 1, 1), (0.3, 1.1, 0.85, 1.1), (0.6, 0.95, 1.12, 0.95), (0.85, 1.15, 1.1, 1.15), (1.1, 1.12, 0.88, 1.12),
                            (1.35, 1.03, 1.02, 1.03), (1.6, 1, 1, 1)))
    for nm, a in (("fleg_l", -30), ("fleg_r", -30), ("hleg_l", 30), ("hleg_r", 30)):
        tell.rot(nm, keys((0, 0, 0, 0), (0.5, 0, 0, 0), (0.7, a, 0, 0), (0.9, a, 0, 0), (1.1, 0, 0, 0), (1.6, 0, 0, 0)))
    nudge = clip_of(r, "nudge", 1.0)
    nudge.rot("head", keys((0, 12, 0, 0), (0.25, 35, 0, 0), (0.45, 0, 0, 0), (0.7, 30, 0, 0), (1.0, 12, 0, 0)))
    nudge.pos("body", keys((0, 0, 0, 0), (0.3, 0, 0, -1), (0.5, 0, 0, 0), (1.0, 0, 0, 0)))
    work = clip_of(r, "work", 2.4, loop=True)
    # grazing by its bale: head down, chewing, a contented sway
    cycle(work, "head", "rot", lambda p: (40 + 5 * s(p * 4), 6 * s(p), 0), 16)
    cycle(work, "wool", "rot", lambda p: (0, 0, 2.5 * s(p)), 8)
    sleep = clip_of(r, "sleep", 4.4, loop=True)
    # sunk down into its own wool, legs folded away, head resting on the fleece
    hold(sleep, "body", pos=(0, -4.6, 0))
    for nm in ("fleg_l", "fleg_r"):
        hold(sleep, nm, rot=(85, 0, 0))
    for nm in ("hleg_l", "hleg_r"):
        hold(sleep, nm, rot=(-85, 0, 0))
    hold(sleep, "head", rot=(30, 0, 0), pos=(0, 0, 0.5))
    hold(sleep, "ear_l", rot=(0, 0, 50))
    hold(sleep, "ear_r", rot=(0, 0, -50))
    cycle(sleep, "wool", "scale", lambda p: (1 + 0.035 * s(p), 1 + 0.04 * s(p), 1 + 0.03 * s(p)), 8)
    sniff = clip_of(r, "sniff", 1.2)
    sk = [(0, 12, 0, 0)]
    for k in range(5):
        sk += [(0.25 + k * 0.14, 34, 0, 0), (0.32 + k * 0.14, 28, 0, 0)]
    sk.append((1.2, 12, 0, 0))
    sniff.rot("head", sk, "lin")
    carry = clip_of(r, "carry", 0.55, loop=True)
    bounce(carry, 26, 0.8)
    hide = clip_of(r, "hide", 1.0, loop=True)
    hold(hide, "body", pos=(0, -4.0, 0))
    for nm in ("fleg_l", "fleg_r"):
        hold(hide, nm, rot=(85, 0, 0))
    for nm in ("hleg_l", "hleg_r"):
        hold(hide, nm, rot=(-85, 0, 0))
    hold(hide, "head", rot=(20, 0, 0), pos=(0, 0, 1.6))
    cycle(hide, "wool", "pos", lambda p: (0.15 * s(p * 5), 0, 0), 24)
    stubs(r)
    return r


def paint_puffwool(r, P):
    wool = Ramp("#8a849a", "#a49eb4", "#bdb8cc", "#d4d0e0", "#e6e2ee", "#f3f1f8", "#ffffff")
    curls = combine(p_fur(0.9, 2, 5, 2), p_blotch("puffwool", 3, 0.5))
    for cb in ("fleece", "puff_top", "puff_side", "puff_rump", "tuft"):
        P.paint(cb, wool, 4.4, curls)
    # little curls: a pale fleck and a soft shadow in a loose pattern over every face
    for cb in ("fleece", "puff_top", "puff_side", "puff_rump", "tuft"):
        for fname in ("north", "south", "west", "east", "top"):
            face_fill(P, cb, fname, lambda f, i, j: wool.at(6.0) if (i * 3 + j * 5) % 7 == 0 else (wool.at(2.6) if (i * 3 + j * 5) % 7 == 3 else None))
    face = Ramp("#14141a", "#202028", "#2e2e38", "#3e3e4a", "#525262")
    P.paint("face", face, 2.6)
    face_fill(P, "face", "north", lambda f, i, j: hexc("#4a4656") if j >= f.h - 2 and 0 < i < f.w - 1 else None)
    P.paint("oear_l", face, 2.4)
    face_fill(P, "oear_l", "north", lambda f, i, j: hexc("#c88a96"))
    P.paint("ofleg_l", face, 2.2)
    face_fill(P, "ofleg_l", "bottom", lambda f, i, j: hexc("#0a0a0e"))
    side_eye(P, "face", "#efe6cc", "#0a0806")
    return P.skin, P.glow


# --- Dumplin: a round bird that can't fly -----------------------------------------------------------------------------

def dumplin():
    r = Rig("kin_dumplin", (64, 64))
    r.walk_stride, r.run_stride, r.run_speed, r.shadow = 0.5, 1.1, (0.08, 0.2), 0.35
    body = r.bone("body", (0, 16.5, 0))
    body.cube("obody", -4, -4, -4.5, 8, 8, 9)
    body.cube("breast", -3.5, -2.5, -5.5, 7, 6, 1)
    head = body.bone("head", (0, -3.5, -3.5))
    head.cube("ohead", -2.5, -4.5, -3, 5, 5, 5)
    head.cube("beak", -1, -2.5, -4.5, 2, 2, 2)
    plume = head.bone("plume", (0, -4.5, -1), (-20, 0, 0))
    plume.cube("oplume", 0, -3, -1.5, 0, 3, 4)

    def side(sd, nm, mir, sh):
        wg = body.bone("wing_" + nm, (sd * 4, -2.5, -2))
        if sd > 0:
            wg.cube("owing_l", 0, 0, 0, 1, 5, 6)
        else:
            wg.cube("owing_r", -1, 0, 0, 1, 5, 6, mirror=True, share="owing_l")
        lg = body.bone("leg_" + nm, (sd * 1.8, 3.5, 0.5))
        lg.cube("oleg_" + nm, -0.5, 0, -0.5, 1, 4, 1, mirror=mir, share=sh("oleg_l"))
        lg.cube("foot_" + nm, -1, 4, -2, 2, 0, 3, mirror=mir, share=sh("foot_l"))
    sym(side)
    tail = body.bone("tail", (0, -2.5, 4.5), (35, 0, 0))
    tail.cube("otail", -2, -1, 0, 4, 2, 3)
    r.look = [("head", 1.0, 0.8)]
    r.pack()

    def waddle(cl, swing, roll, bob):
        cycle(cl, "leg_l", "rot", lambda p: (swing * s(p), 0, 0), 8)
        cycle(cl, "leg_r", "rot", lambda p: (-swing * s(p), 0, 0), 8)
        cycle(cl, "body", "rot", lambda p: (0, 0, roll * s(p)), 8)
        cycle(cl, "body", "pos", lambda p: (0, bob * abs(s(p)), 0), 8)
        cycle(cl, "head", "rot", lambda p: (0, 0, -roll * 0.7 * s(p)), 8)
        cycle(cl, "tail", "rot", lambda p: (35, 12 * s(p, 0.1), 0), 8)

    idle = clip_of(r, "idle", 2.6, loop=True)
    # a bird's quick looks, the plume bobbing, a flick of the tail
    idle.rot("head", keys((0, 0, 0, 0), (0.6, 0, 0, 0), (0.7, 0, 25, 8), (1.2, 0, 25, 8), (1.3, 0, -20, -6), (1.9, 0, -20, -6), (2.0, 0, 0, 0), (2.6, 0, 0, 0)), "lin")
    cycle(idle, "plume", "rot", lambda p: (-20 + 6 * s(p * 2), 0, 0), 8)
    idle.rot("tail", keys((0, 35, 0, 0), (1.5, 35, 0, 0), (1.6, 45, 15, 0), (1.7, 35, -15, 0), (1.8, 35, 0, 0), (2.6, 35, 0, 0)))
    cycle(idle, "body", "scale", lambda p: (1 + 0.02 * s(p * 2), 1 + 0.025 * s(p * 2), 1), 8)
    move = clip_of(r, "move", 0.45, loop=True)
    waddle(move, 32, 7, 0.5)
    run = clip_of(r, "run", 0.28, loop=True)
    waddle(run, 45, 9, 0.9)
    for nm, sd in (("l", 1), ("r", -1)):
        cycle(run, "wing_" + nm, "rot", lambda p, sd=sd: (0, 0, -sd * (25 + 25 * s(p * 2))), 8)
    want = clip_of(r, "want", 3.6, loop=True)
    # wandering round in a circle, looking up for something tall to follow
    want.rot("body", [(0, 0, 0, 0), (3.6, 0, 360, 0)], "lin")
    cycle(want, "leg_l", "rot", lambda p: (20 * s(p * 6), 0, 0), 24)
    cycle(want, "leg_r", "rot", lambda p: (-20 * s(p * 6), 0, 0), 24)
    want.rot("head", keys((0, 0, 0, 0), (0.6, -30, 0, 0), (1.4, -30, 20, 0), (2.0, -10, 0, 0), (2.6, -35, -20, 0), (3.2, -30, 0, 0), (3.6, 0, 0, 0)))
    tell = clip_of(r, "tell", 1.3)
    # a happy flap and a hop, the plume fanned
    for nm, sd in (("l", 1), ("r", -1)):
        wk = [(0, 0, 0, 0)]
        for k in range(6):
            wk.append((0.1 + k * 0.15, 0, 0, -sd * (70 if k % 2 == 0 else 20)))
        wk += [(1.1, 0, 0, 0), (1.3, 0, 0, 0)]
        tell.rot("wing_" + nm, wk)
    tell.pos("body", keys((0, 0, 0, 0), (0.2, 0, -0.6, 0), (0.45, 0, 3, 0), (0.7, 0, 0, 0), (0.85, 0, 2, 0), (1.05, 0, 0, 0), (1.3, 0, 0, 0)))
    tell.scale("plume", keys((0, 1, 1, 1), (0.3, 1, 1.4, 1.3), (1.0, 1, 1.3, 1.2), (1.3, 1, 1, 1)))
    tell.rot("head", keys((0, 0, 0, 0), (0.3, -20, 0, 0), (1.0, -20, 0, 0), (1.3, 0, 0, 0)))
    nudge = clip_of(r, "nudge", 0.9)
    # pecks at your boots
    nudge.rot("head", keys((0, 0, 0, 0), (0.15, 45, 0, 0), (0.25, 0, 0, 0), (0.4, 45, 0, 0), (0.5, 0, 0, 0), (0.9, 0, 0, 0)), "lin")
    nudge.rot("body", keys((0, 0, 0, 0), (0.15, 15, 0, 0), (0.25, 0, 0, 0), (0.4, 15, 0, 0), (0.5, 0, 0, 0), (0.9, 0, 0, 0)), "lin")
    work = clip_of(r, "work", 2.4, loop=True)
    # sitting on the nest: settled down, a fluff and a wiggle, looking about
    hold(work, "body", pos=(0, -3.4, 0))
    for nm in ("leg_l", "leg_r"):
        hold(work, nm, rot=(-80, 0, 0))
    cycle(work, "body", "rot", lambda p: (0, 0, 4 * s(p * 2)), 8)
    cycle(work, "body", "scale", lambda p: (1.03 + 0.02 * s(p), 1.03 + 0.02 * s(p), 1.03), 8)
    work.rot("head", keys((0, 0, 0, 0), (0.8, 0, 0, 0), (0.9, 0, 30, 0), (1.5, 0, 30, 0), (1.6, 0, 0, 0), (2.4, 0, 0, 0)), "lin")
    sleep = clip_of(r, "sleep", 4.0, loop=True)
    # a feather ball: settled, head sunk into its shoulders, plume laid flat
    hold(sleep, "body", pos=(0, -3.4, 0))
    for nm in ("leg_l", "leg_r"):
        hold(sleep, nm, rot=(-80, 0, 0))
    hold(sleep, "head", rot=(30, 0, 0), pos=(0, -1.2, 1))
    hold(sleep, "plume", rot=(-80, 0, 0))
    cycle(sleep, "body", "scale", lambda p: (1.05 + 0.03 * s(p), 1.05 + 0.04 * s(p), 1.05), 8)
    sniff = clip_of(r, "sniff", 1.2)
    pk = [(0, 0, 0, 0)]
    for k in range(3):
        pk += [(0.15 + k * 0.32, 50, 0, 0), (0.3 + k * 0.32, 10, 0, 0)]
    pk.append((1.2, 0, 0, 0))
    sniff.rot("head", pk, "lin")
    sniff.rot("body", keys((0, 0, 0, 0), (0.15, 18, 0, 0), (1.0, 18, 0, 0), (1.2, 0, 0, 0)))
    carry = clip_of(r, "carry", 0.5, loop=True)
    waddle(carry, 28, 6, 0.4)
    hide = clip_of(r, "hide", 1.0, loop=True)
    for nm in ("leg_l", "leg_r"):
        hold(hide, nm, rot=(-70, 0, 0))
    hold(hide, "head", pos=(0, -1.5, 0.8))
    hold(hide, "plume", rot=(-70, 0, 0))
    cycle(hide, "body", "pos", lambda p: (0.12 * s(p * 5), -3.0, 0), 24)
    cycle(hide, "body", "scale", lambda p: (1.06, 1.06, 1.06), 8)
    stubs(r)
    return r


def paint_dumplin(r, P):
    plum = Ramp("#5a3c14", "#7e5620", "#a2722e", "#c08e42", "#dcac5c", "#ecc67e", "#f8e0aa")
    cream = Ramp("#c8b48a", "#dccaa2", "#ecdec0", "#f8f0dc")
    for cb in ("obody", "ohead", "owing_l", "otail"):
        P.paint(cb, plum, 4.0, R.p_feathers(3, 2, 0.8))
    P.paint("breast", cream, 2.4, R.p_feathers(2, 2, 0.5))
    face_fill(P, "obody", "bottom", lambda f, i, j: cream.at(1.8 + 0.4 * ((i + j) % 2)))
    face_fill(P, "obody", "north", lambda f, i, j: cream.at(2.0) if j > 1 else None)
    for fname in ("west", "east"):
        face_fill(P, "owing_l", fname, lambda f, i, j: plum.at(2.2) if j == f.h - 2 else (cream.at(2.5) if j == f.h - 1 else None))
    face_fill(P, "otail", "top", lambda f, i, j: plum.at(1.6) if j == f.h - 1 else None)
    plume = Ramp("#5a2a14", "#7e3c1c", "#a85428", "#cc7038")
    for fname in ("west", "east"):
        face_fill(P, "oplume", fname, lambda f, i, j: plume.at(1.5 + j * 0.5) if (i + j) % 3 != 0 or j == f.h - 1 else None)
    orange = Ramp("#a04a12", "#c8621c", "#e47e28", "#f49a3e")
    P.paint("beak", orange, 2.4)
    P.paint("oleg_l", orange, 1.8)
    face_fill(P, "foot_l", "top", lambda f, i, j: orange.at(2.2))
    face_fill(P, "foot_l", "bottom", lambda f, i, j: orange.at(1.6))
    # rosy cheeks under the eyes
    for fname in ("west", "east"):
        face_fill(P, "ohead", fname, lambda f, i, j: hexc("#e8907a") if j == f.h - 2 and i == (1 if f.name == "west" else f.w - 2) else None)
    side_eye(P, "ohead", "#f4f0e8", "#100a06")
    return P.skin, P.glow


# --- Pondling: a frog-axolotl that fishes -----------------------------------------------------------------------------

def pondling():
    r = Rig("kin_pondling", (64, 64))
    r.walk_stride, r.run_stride, r.run_speed, r.shadow = 0.8, 1.8, (0.1, 0.24), 0.35
    body = r.bone("body", (0, 21, 0))
    body.cube("torso", -3, -2.5, -3.5, 6, 4, 7)
    head = body.bone("head", (0, -0.5, -3.5), (-4, 0, 0))
    head.cube("ohead", -4, -3, -5, 8, 4, 5)
    throat = head.bone("throat", (0, 1, -2.5))
    throat.cube("othroat", -2.5, 0, -1.5, 5, 1, 3)

    def side(sd, nm, mir, sh):
        gl = head.bone("gill_" + nm, (sd * 3.6, -2.4, -1.2), (0, -sd * 35, -sd * 25))
        if sd > 0:
            gl.cube("fronds_l", 0, -3, 0, 4, 4, 0)
        else:
            gl.cube("fronds_r", -4, -3, 0, 4, 4, 0, mirror=True, share="fronds_l")
        fl = body.bone("fleg_" + nm, (sd * 2.6, 1, -2), (0, 0, -sd * 18))
        fl.cube("ofleg_" + nm, -0.5, 0, -0.5, 1, 2, 1, mirror=mir, share=sh("ofleg_l"))
        fl.cube("ffoot_" + nm, -1, 2, -1.5, 2, 0, 2, mirror=mir, share=sh("ffoot_l"))
        hl = body.bone("hleg_" + nm, (sd * 3, 0.5, 2))
        hl.cube("thigh_" + nm, -1, 0, -2.5, 2, 2, 4, mirror=mir, share=sh("thigh_l"))
        hl.cube("hfoot_" + nm, -1.5, 2, -3.5, 3, 0, 3, mirror=mir, share=sh("hfoot_l"))
    sym(side)
    tail = body.bone("tail", (0, -0.5, 3.5), (-8, 0, 0))
    tail.cube("otail", -1, -1, 0, 2, 2, 4)
    tail.cube("fin", 0, -2.5, 0, 0, 4, 5)
    r.look = [("head", 1.0, 0.6)]
    r.pack()

    def gills(cl, fn, n=8):
        for nm, sd in (("gill_l", 1), ("gill_r", -1)):
            cycle(cl, nm, "rot", lambda p, sd=sd: fn(p, sd), n)

    def hop(cl, height, reach):
        cycle(cl, "body", "pos", lambda p: (0, height * max(0, s(p)), 0), 8)
        cycle(cl, "body", "rot", lambda p: (-12 * max(0, s(p, 0.1)), 0, 0), 8)
        for nm in ("hleg_l", "hleg_r"):
            cycle(cl, nm, "rot", lambda p: (reach * max(0, s(p, 0.05)), 0, 0), 8)
        for nm, sd in (("fleg_l", 1), ("fleg_r", -1)):
            cycle(cl, nm, "rot", lambda p, sd=sd: (-reach * 0.6 * max(0, s(p, 0.2)), 0, -sd * 18), 8)
        gills(cl, lambda p, sd: (0, -sd * (35 + 15 * max(0, s(p, 0.1))), -sd * 25))
        cycle(cl, "tail", "rot", lambda p: (-8, 15 * s(p, 0.2), 0), 8)

    idle = clip_of(r, "idle", 3.0, loop=True)
    # the throat pulsing, the gills waving slowly like weed in a current
    cycle(idle, "throat", "scale", lambda p: (1, 1 + 0.6 * max(0, s(p * 4)), 1 + 0.1 * max(0, s(p * 4))), 16)
    gills(idle, lambda p, sd: (6 * s(p, 0.3 if sd < 0 else 0), -sd * (35 + 8 * s(p, 0.2 + (0.3 if sd < 0 else 0))), -sd * 25))
    cycle(idle, "tail", "rot", lambda p: (-8, 10 * s(p), 0), 8)
    move = clip_of(r, "move", 0.6, loop=True)
    hop(move, 2.2, 55)
    run = clip_of(r, "run", 0.42, loop=True)
    hop(run, 3.4, 75)
    want = clip_of(r, "want", 3.2, loop=True)
    # at the water's edge: leans to look in, flinches back, droops
    want.rot("body", keys((0, 0, 0, 0), (0.6, 18, 0, 0), (1.2, 18, 0, 0), (1.4, -10, 0, 0), (1.8, -4, 0, 0), (3.2, 0, 0, 0)))
    want.pos("body", keys((0, 0, 0, 0), (0.6, 0, 0, -1.2), (1.2, 0, 0, -1.2), (1.4, 0, 0.6, 1), (1.8, 0, 0, 0.6), (3.2, 0, 0, 0)))
    for nm, sd in (("gill_l", 1), ("gill_r", -1)):
        want.rot(nm, keys((0, 0, -sd * 35, -sd * 25), (1.2, 0, -sd * 35, -sd * 25), (1.4, 0, -sd * 70, sd * 10), (2.0, 0, -sd * 55, sd * 20), (3.2, 0, -sd * 35, -sd * 25)))
    want.rot("head", keys((0, -4, 0, 0), (2.0, -4, 0, 0), (2.3, 6, -15, 0), (2.6, 6, 15, 0), (2.9, 6, 0, 0), (3.2, -4, 0, 0)))
    tell = clip_of(r, "tell", 1.4)
    # a frog-leap with a twist, gills flared wide, and a croak on landing
    tell.pos("body", keys((0, 0, 0, 0), (0.2, 0, -0.5, 0), (0.55, 0, 5, 0), (0.9, 0, 0, 0), (1.4, 0, 0, 0)))
    tell.rot("body", keys((0, 0, 0, 0), (0.2, 6, 0, 0), (0.55, -20, 180, 0), (0.9, 0, 360, 0), (1.4, 0, 360, 0)))
    for nm in ("hleg_l", "hleg_r"):
        tell.rot(nm, keys((0, 0, 0, 0), (0.2, -10, 0, 0), (0.35, 75, 0, 0), (0.8, 30, 0, 0), (0.95, 0, 0, 0), (1.4, 0, 0, 0)))
    for nm in ("gill_l", "gill_r"):
        tell.scale(nm, keys((0, 1, 1, 1), (0.4, 1.3, 1.4, 1.3), (1.1, 1.2, 1.3, 1.2), (1.4, 1, 1, 1)))
    tell.scale("throat", keys((0, 1, 1, 1), (0.9, 1, 1, 1), (1.05, 1, 2, 1.2), (1.25, 1, 1, 1), (1.4, 1, 1, 1)))
    nudge = clip_of(r, "nudge", 1.0)
    nudge.rot("head", keys((0, -4, 0, 0), (0.25, 10, -18, 0), (0.5, 10, 18, 0), (0.75, 4, 0, 0), (1.0, -4, 0, 0)))
    for nm, sd in (("gill_l", 1), ("gill_r", -1)):
        nudge.rot(nm, keys((0, 0, -sd * 35, -sd * 25), (0.3, 0, -sd * 10, -sd * 40), (0.6, 0, -sd * 50, -sd * 20), (1.0, 0, -sd * 35, -sd * 25)))
    work = clip_of(r, "work", 2.4, loop=True)
    # fishing: still as a stone at the edge, then a snap down into the water and a gulp
    work.rot("body", keys((0, 10, 0, 0), (1.5, 10, 0, 0), (1.62, 34, 0, 0), (1.8, 34, 0, 0), (2.0, 10, 0, 0), (2.4, 10, 0, 0)), "lin")
    work.pos("body", keys((0, 0, 0, -0.5), (1.5, 0, 0, -0.5), (1.62, 0, -0.6, -2.4), (1.8, 0, -0.6, -2.4), (2.0, 0, 0, -0.5), (2.4, 0, 0, -0.5)), "lin")
    work.scale("throat", keys((0, 1, 1, 1), (1.9, 1, 1, 1), (2.05, 1, 2.2, 1.2), (2.2, 1, 1, 1), (2.4, 1, 1, 1)))
    sleep = clip_of(r, "sleep", 4.0, loop=True)
    hold(sleep, "body", pos=(0, -1.4, 0))
    for nm, sd in (("gill_l", 1), ("gill_r", -1)):
        hold(sleep, nm, rot=(0, -sd * 80, sd * 5))
    for nm, sd in (("fleg_l", 1), ("fleg_r", -1)):
        hold(sleep, nm, rot=(-60, 0, -sd * 50))
    hold(sleep, "tail", rot=(-14, 30, 0))
    cycle(sleep, "throat", "scale", lambda p: (1, 1 + 0.4 * max(0, s(p)), 1), 8)
    sniff = clip_of(r, "sniff", 1.2)
    sniff.rot("head", keys((0, -4, 0, 0), (0.3, -16, 0, 0), (0.9, -16, 0, 0), (1.2, -4, 0, 0)))
    for nm, sd in (("gill_l", 1), ("gill_r", -1)):
        sniff.rot(nm, keys((0, 0, -sd * 35, -sd * 25), (0.3, 0, sd * 5, -sd * 35), (0.5, 0, -sd * 20, -sd * 30), (0.7, 0, sd * 5, -sd * 35), (1.2, 0, -sd * 35, -sd * 25)))
    carry = clip_of(r, "carry", 0.6, loop=True)
    hop(carry, 1.8, 50)
    hide = clip_of(r, "hide", 1.0, loop=True)
    for nm, sd in (("gill_l", 1), ("gill_r", -1)):
        hold(hide, nm, rot=(0, -sd * 85, sd * 10))
    for nm, sd in (("fleg_l", 1), ("fleg_r", -1)):
        hold(hide, nm, rot=(-40, 0, -sd * 60))
    cycle(hide, "body", "pos", lambda p: (0.1 * s(p * 5), -1.4, 0), 24)
    stubs(r)
    return r


def paint_pondling(r, P):
    skin = Ramp("#0e2e2c", "#164440", "#205c56", "#2c766e", "#3c9286", "#5aaea0", "#86cabe")
    belly = Ramp("#b4d8c4", "#cce6d6", "#e0f0e4", "#f2faf4")
    pat = combine(p_blotch("pondling", 3, 0.6), R.p_scales(2, 2, 0.25))
    for cb in ("torso", "ohead", "thigh_l", "otail", "ofleg_l"):
        P.paint(cb, skin, 3.6, pat)
    # darker spots down the back, like a frog's
    for cb in ("torso", "ohead", "thigh_l"):
        face_fill(P, cb, "top", lambda f, i, j: skin.at(1.4) if (i * 5 + j * 3) % 7 == 0 else None)
    for cb in ("torso", "thigh_l"):
        face_fill(P, cb, "bottom", lambda f, i, j: belly.at(1.8 + 0.4 * ((i + j) % 2)))
    P.paint("othroat", belly, 2.2)
    # the wide smile
    face_fill(P, "ohead", "north", lambda f, i, j: hexc("#0a201e") if (j == f.h - 1 and 1 < i < f.w - 2) or (j == f.h - 2 and i in (1, f.w - 2)) else None)
    gill = Ramp("#7a2438", "#a63a52", "#cc5a70", "#e8808e", "#f8aab2")
    for fname in ("north", "south"):
        face_fill(P, "fronds_l", fname, lambda f, i, j: gill.at(2.0 + 1.5 * i / max(1, f.w - 1)) if j % 2 == 0 or i % 2 == 1 else None)
        P.glow_face("fronds_l", fname, lambda f, i, j: hexc("#ff9cb0") if j % 2 == 0 and i == f.w - 1 else None)
    for fname in ("west", "east"):
        face_fill(P, "fin", fname, lambda f, i, j: hexc("#7ac8b8") if 1 <= j <= f.h - 1 - i // 2 else None)
    for cb in ("hfoot_l", "ffoot_l"):
        face_fill(P, cb, "top", lambda f, i, j: skin.at(4.0) if i % 2 == 0 or j == f.h - 1 else None)
        face_fill(P, cb, "bottom", lambda f, i, j: belly.at(1.4) if i % 2 == 0 or j == f.h - 1 else None)
    # big round eyes high on the head, gold with a dark middle
    with P.eyes():
        for fname in ("west", "east"):
            f = P.face("ohead", fname)
            step = 1 if fname == "west" else -1
            ex = front_i(f) + step
            for di in (0, 1):
                for j in (0, 1):
                    f.set(ex + step * di, j, hexc("#e0b840"))
            f.set(ex, 1, hexc("#0a0a08"))
    return P.skin, P.glow


# --- Snufflet: a wild piglet with a long snout ------------------------------------------------------------------------

def snufflet():
    r = Rig("kin_snufflet", (64, 64))
    r.walk_stride, r.run_stride, r.run_speed, r.shadow = 0.6, 1.4, (0.1, 0.24), 0.35
    body = r.bone("body", (0, 18.5, 0))
    body.cube("torso", -3.5, -3, -4.5, 7, 6, 9)
    head = body.bone("head", (0, -0.5, -4.5), (0, 0, 0))
    head.cube("ohead", -3, -3, -3.5, 6, 5, 4)
    snout = head.bone("snout", (0, 0.5, -3.5), (4, 0, 0))
    snout.cube("s1", -1.5, -1, -3, 3, 2, 3)
    tip = snout.bone("stip", (0, 0.2, -3), (8, 0, 0))
    tip.cube("s2", -1, -1, -2.5, 2, 2, 3)
    tip.cube("disc", -1.5, -1.2, -3, 3, 2, 1)

    def side(sd, nm, mir, sh):
        ear = head.bone("ear_" + nm, (sd * 2.2, -3, -1), (40, 0, sd * 15))
        ear.cube("oear_" + nm, -1, -0.5, -2.5, 2, 1, 3, mirror=mir, share=sh("oear_l"))
        for part, z in (("fleg", -2.8), ("hleg", 2.8)):
            lg = body.bone(part + "_" + nm, (sd * 2.1, 2.5, z))
            lg.cube("o" + part + "_" + nm, -1, 0, -1, 2, 3, 2, mirror=mir, share=sh("ofleg_l") if part == "fleg" else "ofleg_l")
    sym(side)
    tail = body.bone("tail", (0, -2.2, 4.5), (50, 0, 0))
    tail.cube("otail", -0.5, -0.5, 0, 1, 1, 2)
    curl = tail.bone("curl", (0, 0, 2), (0, 100, 0))
    curl.cube("ocurl", -0.5, -0.5, 0, 1, 1, 2)
    r.look = [("head", 1.0, 0.8)]
    r.pack()

    legs4 = [("fleg_l", 0), ("hleg_r", 0.02), ("fleg_r", 0.5), ("hleg_l", 0.52)]

    def trot(cl, swing, bob):
        gait4(cl, swing, legs4)
        cycle(cl, "body", "pos", lambda p: (0, bob * abs(s(p)), 0), 8)
        for nm, sd in (("ear_l", 1), ("ear_r", -1)):
            cycle(cl, nm, "rot", lambda p, sd=sd: (40 - 14 * abs(s(p, 0.12)), 0, sd * 15), 8)
        cycle(cl, "tail", "rot", lambda p: (50, 20 * s(p * 2), 0), 8)
        cycle(cl, "stip", "rot", lambda p: (8 + 5 * s(p * 2), 0, 0), 8)

    idle = clip_of(r, "idle", 2.8, loop=True)
    # the snout never stops: little wiggles side to side, a lift to sniff the air
    sw = [(0, 8, 0, 0)]
    for k in range(6):
        sw += [(0.15 + k * 0.4, 8, 12, 0), (0.3 + k * 0.4, 8, -12, 0)]
    sw.append((2.8, 8, 0, 0))
    idle.rot("stip", sw)
    idle.rot("snout", keys((0, 4, 0, 0), (1.4, 4, 0, 0), (1.6, -10, 0, 0), (2.0, -10, 0, 0), (2.2, 4, 0, 0), (2.8, 4, 0, 0)))
    cycle(idle, "body", "scale", lambda p: (1 + 0.02 * s(p * 2), 1 + 0.025 * s(p * 2), 1), 8)
    move = clip_of(r, "move", 0.5, loop=True)
    trot(move, 35, 0.5)
    run = clip_of(r, "run", 0.3, loop=True)
    gait4(run, 55, [("fleg_l", 0), ("fleg_r", 0.08), ("hleg_l", 0.5), ("hleg_r", 0.58)])
    cycle(run, "body", "pos", lambda p: (0, 1.4 * max(0, s(p)), 0), 8)
    cycle(run, "body", "rot", lambda p: (10 * s(p, 0.25), 0, 0), 8)
    hold(run, "ear_l", rot=(-10, 0, 30))
    hold(run, "ear_r", rot=(-10, 0, -30))
    want = clip_of(r, "want", 2.6, loop=True)
    # snuffling hard at one spot, then a squealing little hop
    want.rot("head", keys((0, 0, 0, 0), (0.3, 28, 0, 0), (1.8, 28, 0, 0), (2.0, -14, 0, 0), (2.3, -14, 0, 0), (2.6, 0, 0, 0)))
    snk = [(0, 4, 0, 0)]
    for k in range(7):
        snk += [(0.3 + k * 0.2, 30, 10 if k % 2 else -10, 0), (0.4 + k * 0.2, 18, 0, 0)]
    snk += [(2.0, -20, 0, 0), (2.3, -20, 0, 0), (2.6, 4, 0, 0)]
    want.rot("snout", snk, "lin")
    want.pos("body", keys((0, 0, 0, 0), (1.8, 0, 0, 0), (2.0, 0, 2.2, 0), (2.25, 0, 0, 0), (2.6, 0, 0, 0)))
    tell = clip_of(r, "tell", 1.4)
    # zoomies: a hop, a spin, a hop, ears flapping
    tell.pos("body", keys((0, 0, 0, 0), (0.2, 0, 2.2, 0), (0.4, 0, 0, 0), (0.75, 0, 2.6, 0), (1.0, 0, 0, 0), (1.4, 0, 0, 0)))
    tell.rot("body", keys((0, 0, 0, 0), (0.4, 0, 0, 0), (0.75, 0, 180, 0), (1.0, 0, 360, 0), (1.4, 0, 360, 0)))
    for nm, sd in (("ear_l", 1), ("ear_r", -1)):
        tell.rot(nm, keys((0, 40, 0, sd * 15), (0.2, -20, 0, sd * 35), (0.5, 30, 0, sd * 15), (0.75, -25, 0, sd * 40), (1.1, 40, 0, sd * 15), (1.4, 40, 0, sd * 15)))
    tell.rot("snout", keys((0, 4, 0, 0), (0.2, -25, 0, 0), (1.0, -25, 0, 0), (1.4, 4, 0, 0)))
    nudge = clip_of(r, "nudge", 0.9)
    # a push with the snout, up under your hand
    nudge.rot("snout", keys((0, 4, 0, 0), (0.25, -30, 0, 0), (0.4, 0, 0, 0), (0.6, -30, 0, 0), (0.9, 4, 0, 0)))
    nudge.pos("body", keys((0, 0, 0, 0), (0.25, 0, 0, -1), (0.45, 0, 0, 0), (0.6, 0, 0, -1), (0.9, 0, 0, 0)))
    work = clip_of(r, "work", 1.2, loop=True)
    # rooting: snout down in the earth, shoving forward, the little tail going
    hold(work, "head", rot=(30, 0, 0))
    cycle(work, "snout", "rot", lambda p: (24 + 10 * s(p * 2), 8 * s(p), 0), 8)
    cycle(work, "body", "pos", lambda p: (0, 0, -0.6 * max(0, s(p * 2))), 8)
    cycle(work, "tail", "rot", lambda p: (50, 30 * s(p * 3), 0), 12)
    sleep = clip_of(r, "sleep", 4.0, loop=True)
    hold(sleep, "body", pos=(0, -2.6, 0))
    for nm in ("fleg_l", "fleg_r"):
        hold(sleep, nm, rot=(-80, 0, 0))
    for nm in ("hleg_l", "hleg_r"):
        hold(sleep, nm, rot=(80, 0, 0))
    hold(sleep, "head", rot=(20, 0, 0))
    hold(sleep, "snout", rot=(20, 0, 0))
    cycle(sleep, "body", "scale", lambda p: (1 + 0.03 * s(p), 1 + 0.04 * s(p), 1), 8)
    sniff = clip_of(r, "sniff", 1.2)
    sniff.rot("head", keys((0, 0, 0, 0), (0.3, -14, 0, 0), (0.9, -14, 0, 0), (1.2, 0, 0, 0)))
    sk = [(0, 8, 0, 0)]
    for k in range(6):
        sk += [(0.2 + k * 0.14, 8, 14, 0), (0.27 + k * 0.14, 8, -14, 0)]
    sk.append((1.2, 8, 0, 0))
    sniff.rot("stip", sk, "lin")
    carry = clip_of(r, "carry", 0.55, loop=True)
    trot(carry, 30, 0.4)
    hide = clip_of(r, "hide", 1.0, loop=True)
    for nm in ("fleg_l", "fleg_r"):
        hold(hide, nm, rot=(-40, 0, 0))
    for nm in ("hleg_l", "hleg_r"):
        hold(hide, nm, rot=(40, 0, 0))
    hold(hide, "ear_l", rot=(60, 0, 30))
    hold(hide, "ear_r", rot=(60, 0, -30))
    hold(hide, "tail", rot=(0, 0, 0))
    cycle(hide, "body", "pos", lambda p: (0.12 * s(p * 5), -1.8, 0), 24)
    stubs(r)
    return r


def paint_snufflet(r, P):
    hide = Ramp("#4a2420", "#66342e", "#84483e", "#a26056", "#be7c72", "#d89a92", "#ecc0b6")
    cream = hexc("#f2dcc0")
    pat = combine(p_fur(0.4, 2, 6, 2), p_blotch("snufflet", 4, 0.3))
    for cb in ("torso", "ohead", "ofleg_l", "s1", "oear_l", "otail", "ocurl"):
        P.paint(cb, hide, 3.6, pat)
    # a wild piglet's stripes: cream bands along the back and sides
    for fname in ("west", "east"):
        face_fill(P, "torso", fname, lambda f, i, j: cream if j in (1, 3) else None)
    face_fill(P, "torso", "top", lambda f, i, j: cream if i in (1, f.w - 2) else (hide.at(2.4) if i == f.w // 2 else None))
    face_fill(P, "ohead", "top", lambda f, i, j: cream if i in (1, f.w - 2) else None)
    face_fill(P, "torso", "bottom", lambda f, i, j: hexc("#ecc8bc"))
    pink = Ramp("#a0505a", "#c06a72", "#d88a90", "#eeaab0")
    P.paint("s2", pink, 2.6)
    P.paint("disc", pink, 3.0)
    face_fill(P, "disc", "north", lambda f, i, j: hexc("#4a181e") if i in (0, f.w - 1) and j == f.h // 2 else pink.at(3.4))
    face_fill(P, "oear_l", "bottom", lambda f, i, j: hexc("#e8a8ac"))
    for fname in ("north", "south", "west", "east"):
        face_fill(P, "ofleg_l", fname, lambda f, i, j: hexc("#2a1a16") if j == f.h - 1 else None)
    face_fill(P, "ofleg_l", "bottom", lambda f, i, j: hexc("#2a1a16"))
    side_eye(P, "ohead", "#d8c0b0", "#100808")
    return P.skin, P.glow


# --- Chimewren: a round songbird that copies sounds -------------------------------------------------------------------

def chimewren():
    r = Rig("kin_chimewren", (64, 64))
    r.walk_stride, r.run_stride, r.run_speed, r.shadow = 0.5, 1.2, (0.1, 0.25), 0.25
    body = r.bone("body", (0, 18.5, 0))
    body.cube("obody", -2.5, -2.5, -3, 5, 5, 6)
    throat = body.bone("throat", (0, -0.5, -3))
    throat.cube("othroat", -1.5, -1.5, -0.8, 3, 3, 1)
    head = body.bone("head", (0, -2, -2.2))
    head.cube("ohead", -2, -4, -3, 4, 4, 4)
    beak = head.bone("beak", (0, -2, -3))
    beak.cube("upper", -0.5, -0.5, -2, 1, 1, 2)
    jaw = beak.bone("jaw", (0, 0.5, 0))
    jaw.cube("lower", -0.5, 0, -1.5, 1, 1, 2)
    crest = head.bone("crest", (0, -4, -0.5), (-25, 0, 0))
    crest.cube("ocrest", 0, -2, -1, 0, 2, 3)

    def side(sd, nm, mir, sh):
        wg = body.bone("wing_" + nm, (sd * 2.5, -1.6, -1.6))
        if sd > 0:
            wg.cube("owing_l", 0, 0, 0, 1, 3, 5)
        else:
            wg.cube("owing_r", -1, 0, 0, 1, 3, 5, mirror=True, share="owing_l")
        lg = body.bone("leg_" + nm, (sd * 1, 2.5, 0.3))
        lg.cube("oleg_" + nm, -0.5, 0, -0.5, 1, 3, 1, mirror=mir, share=sh("oleg_l"))
        lg.cube("foot_" + nm, -1, 3, -1.5, 2, 0, 2, mirror=mir, share=sh("foot_l"))
    sym(side)
    tail = body.bone("tail", (0, -1.8, 2.8), (55, 0, 0))
    tail.cube("otail", -1, -0.5, 0, 2, 1, 5)
    r.look = [("head", 1.0, 0.8)]
    r.pack()

    def hops(cl, height, flick):
        cycle(cl, "body", "pos", lambda p: (0, height * max(0, s(p)), 0), 8)
        cycle(cl, "body", "rot", lambda p: (-6 * max(0, s(p)), 0, 0), 8)
        for nm in ("leg_l", "leg_r"):
            cycle(cl, nm, "rot", lambda p: (30 * max(0, s(p, 0.05)), 0, 0), 8)
        cycle(cl, "tail", "rot", lambda p: (55 + flick * s(p, 0.15), 0, 0), 8)

    idle = clip_of(r, "idle", 2.4, loop=True)
    # quick bird looks: a jerk of the head this way and that, the tail flicked up
    idle.rot("head", keys((0, 0, 0, 0), (0.5, 0, 0, 0), (0.55, 0, 30, 10), (1.0, 0, 30, 10), (1.05, -10, -25, -10), (1.6, -10, -25, -10),
                          (1.65, 0, 0, 0), (2.4, 0, 0, 0)), "lin")
    idle.rot("tail", keys((0, 55, 0, 0), (1.8, 55, 0, 0), (1.9, 75, 0, 0), (2.05, 55, 0, 0), (2.4, 55, 0, 0)))
    cycle(idle, "body", "scale", lambda p: (1 + 0.02 * s(p * 2), 1 + 0.02 * s(p * 2), 1), 8)
    move = clip_of(r, "move", 0.4, loop=True)
    hops(move, 1.6, 12)
    run = clip_of(r, "run", 0.26, loop=True)
    hops(run, 2.4, 18)
    for nm, sd in (("l", 1), ("r", -1)):
        cycle(run, "wing_" + nm, "rot", lambda p, sd=sd: (0, 0, -sd * 30 * max(0, s(p * 2))), 8)
    want = clip_of(r, "want", 3.0, loop=True)
    # the same three notes, over and over, and a tired droop after each round
    jk, tk = [(0, 0, 0, 0)], [(0, 1, 1, 1)]
    for k in range(3):
        t = 0.2 + k * 0.35
        jk += [(t, 35, 0, 0), (t + 0.2, 0, 0, 0)]
        tk += [(t, 1, 1.4, 1.3), (t + 0.2, 1, 1, 1)]
    jk.append((3.0, 0, 0, 0))
    tk.append((3.0, 1, 1, 1))
    want.rot("jaw", jk, "lin")
    want.scale("throat", tk)
    want.rot("head", keys((0, -15, 0, 0), (1.2, -15, 0, 0), (1.6, 20, 0, 0), (2.6, 20, 0, 0), (3.0, -15, 0, 0)))
    want.rot("tail", keys((0, 55, 0, 0), (1.2, 55, 0, 0), (1.6, 15, 0, 0), (2.6, 15, 0, 0), (3.0, 55, 0, 0)))
    tell = clip_of(r, "tell", 1.6)
    # a bright run of notes: head thrown back, throat swelling, tail fanned, wings shivering
    tell.rot("head", keys((0, 0, 0, 0), (0.2, -35, 0, 0), (1.3, -35, 0, 0), (1.6, 0, 0, 0)))
    jk = [(0, 0, 0, 0)]
    for k in range(6):
        jk += [(0.2 + k * 0.18, 45, 0, 0), (0.29 + k * 0.18, 15, 0, 0)]
    jk.append((1.6, 0, 0, 0))
    tell.rot("jaw", jk, "lin")
    tell.scale("throat", keys((0, 1, 1, 1), (0.25, 1.2, 1.6, 1.5), (1.25, 1.2, 1.6, 1.5), (1.6, 1, 1, 1)))
    tell.scale("tail", keys((0, 1, 1, 1), (0.25, 1.8, 1, 1), (1.3, 1.8, 1, 1), (1.6, 1, 1, 1)))
    tell.rot("tail", keys((0, 55, 0, 0), (0.25, 80, 0, 0), (1.3, 80, 0, 0), (1.6, 55, 0, 0)))
    for nm, sd in (("l", 1), ("r", -1)):
        wk = [(0, 0, 0, 0)]
        for k in range(8):
            wk.append((0.2 + k * 0.13, 0, 0, -sd * (20 if k % 2 == 0 else 5)))
        wk += [(1.4, 0, 0, 0), (1.6, 0, 0, 0)]
        tell.rot("wing_" + nm, wk, "lin")
    tell.rot("crest", keys((0, -25, 0, 0), (0.25, 10, 0, 0), (1.3, 10, 0, 0), (1.6, -25, 0, 0)))
    nudge = clip_of(r, "nudge", 0.9)
    # cocks its head at you, one way then the other
    nudge.rot("head", keys((0, 0, 0, 0), (0.15, 0, 0, 30), (0.45, 0, 0, 30), (0.55, 0, 0, -30), (0.8, 0, 0, -30), (0.9, 0, 0, 0)), "lin")
    work = clip_of(r, "work", 2.0, loop=True)
    # singing from its perch: throat swelling on every note, head up, the tail keeping time
    cycle(work, "throat", "scale", lambda p: (1 + 0.15 * max(0, s(p * 4)), 1 + 0.45 * max(0, s(p * 4)), 1 + 0.4 * max(0, s(p * 4))), 16)
    cycle(work, "jaw", "rot", lambda p: (30 * max(0, s(p * 4)), 0, 0), 16)
    cycle(work, "head", "rot", lambda p: (-20 + 6 * s(p * 2), 8 * s(p), 0), 8)
    cycle(work, "tail", "rot", lambda p: (60 + 10 * s(p * 2, 0.2), 0, 0), 8)
    sleep = clip_of(r, "sleep", 4.0, loop=True)
    # a fluffed ball, head sunk in, crest laid flat
    hold(sleep, "body", pos=(0, -2.2, 0))
    for nm in ("leg_l", "leg_r"):
        hold(sleep, nm, rot=(-80, 0, 0))
    hold(sleep, "head", rot=(25, 0, 0), pos=(0, -0.8, 0.8))
    hold(sleep, "crest", rot=(-70, 0, 0))
    hold(sleep, "tail", rot=(30, 0, 0))
    cycle(sleep, "body", "scale", lambda p: (1.08 + 0.03 * s(p), 1.08 + 0.04 * s(p), 1.08), 8)
    sniff = clip_of(r, "sniff", 1.2)
    # listening: head cocked hard to one side, crest up, very still
    sniff.rot("head", keys((0, 0, 0, 0), (0.15, -5, 0, 35), (1.0, -5, 0, 35), (1.2, 0, 0, 0)), "lin")
    sniff.rot("crest", keys((0, -25, 0, 0), (0.2, 5, 0, 0), (1.0, 5, 0, 0), (1.2, -25, 0, 0)))
    carry = clip_of(r, "carry", 0.44, loop=True)
    hops(carry, 1.2, 8)
    hide = clip_of(r, "hide", 1.0, loop=True)
    for nm in ("leg_l", "leg_r"):
        hold(hide, nm, rot=(-60, 0, 0))
    hold(hide, "crest", rot=(-80, 0, 0))
    hold(hide, "tail", rot=(10, 0, 0))
    hold(hide, "head", pos=(0, -0.8, 0.6))
    cycle(hide, "body", "pos", lambda p: (0.1 * s(p * 5), -1.6, 0), 24)
    stubs(r)
    return r


def paint_chimewren(r, P):
    blue = Ramp("#18223a", "#243458", "#344a7a", "#4a6698", "#6684b8", "#8fb0dc", "#c0d4f0")
    cream = Ramp("#c8c0b0", "#ded8cc", "#eee8e0", "#faf8f4")
    for cb in ("obody", "ohead", "owing_l"):
        P.paint(cb, blue, 4.0, R.p_feathers(2, 2, 0.7))
    face_fill(P, "obody", "bottom", lambda f, i, j: cream.at(1.8 + 0.4 * ((i + j) % 2)))
    face_fill(P, "obody", "north", lambda f, i, j: cream.at(2.2) if j >= f.h - 2 else None)
    P.paint("othroat", cream, 2.6)
    # the wren's barred tail and wing bars
    P.paint("otail", blue, 3.0, R.p_bands(2, 1.4))
    for fname in ("west", "east"):
        face_fill(P, "owing_l", fname, lambda f, i, j: cream.at(2.6) if j == 1 and i % 2 == 0 else (blue.at(2.0) if j == f.h - 1 else None))
    # the pale brow over each eye
    for fname in ("west", "east"):
        face_fill(P, "ohead", fname, lambda f, i, j: cream.at(3.0) if j == 0 and i != (0 if f.name == "west" else f.w - 1) else None)
    # the brass crest that gives them their name: its tips catch the light
    brass = Ramp("#6a4a18", "#94702a", "#c09a40", "#e6c462", "#f8e49a")
    for fname in ("west", "east"):
        face_fill(P, "ocrest", fname, lambda f, i, j: brass.at(1.8 + 0.8 * (f.h - 1 - j)) if (i + j) % 3 != 2 or j == f.h - 1 else None)
        P.glow_face("ocrest", fname, lambda f, i, j: hexc("#ffe8a0") if j == 0 and (i + j) % 3 != 2 else None)
    P.paint("upper", Ramp("#2a2420", "#3e362e", "#544a40"), 2.0)
    P.paint("lower", Ramp("#3a322a", "#544a40", "#6e6254"), 2.0)
    P.paint("oleg_l", Ramp("#6a5a54", "#8a7a72", "#a89890"), 1.8)
    face_fill(P, "foot_l", "top", lambda f, i, j: hexc("#8a7a72"))
    face_fill(P, "foot_l", "bottom", lambda f, i, j: hexc("#6a5a54"))
    side_eye(P, "ohead", "#3a4a6a", "#0a0a10")
    return P.skin, P.glow


# =====================================================================================================================

# =====================================================================================================================
# Stage 6 (SPEC §43.13): the Bumblepuff, the Loomworm, the Cloudcalf and the Boulderback
# =====================================================================================================================

# --- Bumblepuff: a fuzzy bee as big as a loaf that floats more than it flies ------------------------------------------

def bumblepuff():
    r = Rig("kin_bumblepuff", (64, 64))
    r.walk_stride, r.run_stride, r.run_speed, r.shadow = 1.0, 2.0, (0.1, 0.25), 0.0
    body = r.bone("body", (0, 12, 0))
    body.cube("fuzz", -5, -5, -5, 10, 10, 10)
    body.cube("collar", -5.5, -4.5, -5.5, 11, 9, 2)
    rump = body.bone("rump", (0, 0.5, 4.5), (-8, 0, 0))
    rump.cube("orump", -4, -4, 0, 8, 8, 4)
    rump.cube("tuft", -1, -1, 4, 2, 2, 1)
    head = body.bone("head", (0, 0, -5.5), (6, 0, 0))
    head.cube("ohead", -3, -3, -4, 6, 6, 4)
    head.cube("snout", -1.5, 0.5, -5, 3, 2, 1)

    def side(sd, nm, mir, sh):
        ant = head.bone("ant_" + nm, (sd * 1.5, -3, -3), (-25, 0, sd * 22))
        ant.cube("stalk_" + nm, -0.5, -4, -0.5, 1, 4, 1, mirror=mir, share=sh("stalk_l"))
        ant.cube("knob_" + nm, -1, -6, -1, 2, 2, 2, mirror=mir, share=sh("knob_l"))
        w = body.bone("wing_" + nm, (sd * 2, -5, -1), (0, sd * -12, -sd * 18))
        if sd > 0:
            w.cube("wingf_l", 0, 0, -2, 7, 0, 5)
        else:
            w.cube("wingf_r", -7, 0, -2, 7, 0, 5, mirror=True, share="wingf_l")
        hw = body.bone("hwing_" + nm, (sd * 2, -5, 2), (0, sd * 18, -sd * 12))
        if sd > 0:
            hw.cube("wingh_l", 0, 0, 0, 5, 0, 4)
        else:
            hw.cube("wingh_r", -5, 0, 0, 5, 0, 4, mirror=True, share="wingh_l")
        leg = body.bone("legs_" + nm, (sd * 2.5, 4.5, -1), (15, 0, sd * 15))
        leg.cube("oleg_" + nm, -0.5, 0, -2.5, 1, 2, 5, mirror=mir, share=sh("oleg_l"))
    sym(side)
    r.look = [("head", 1.0, 0.8)]
    r.pack()

    def buzz(cl, amp, beats, bob=0.6):
        # little wings going nineteen to the dozen and hardly holding it up: it bobs on every beat
        for nm, sg in (("l", 1), ("r", -1)):
            cycle(cl, "wing_" + nm, "rot", lambda p, sg=sg: (0, sg * -12, -sg * (18 + amp * s(p * beats))), beats * 4)
            cycle(cl, "hwing_" + nm, "rot", lambda p, sg=sg: (0, sg * 18, -sg * (12 + amp * 0.8 * s(p * beats, -0.1))), beats * 4)
        cycle(cl, "body", "pos", lambda p: (0, bob * s(p), 0), 8)

    idle = clip_of(r, "idle", 1.6, loop=True)
    buzz(idle, 35, 16, 0.8)
    cycle(idle, "rump", "rot", lambda p: (-8 + 4 * s(p, 0.3), 0, 0), 8)
    for nm, sg in (("l", 1), ("r", -1)):
        cycle(idle, "legs_" + nm, "rot", lambda p, sg=sg: (15 + 10 * s(p, 0.2), 0, sg * 15), 8)
        cycle(idle, "ant_" + nm, "rot", lambda p, sg=sg: (-25 + 8 * s(p, 0.1 * sg), 0, sg * 22), 8)
    move = clip_of(r, "move", 0.8, loop=True)
    buzz(move, 40, 10, 0.5)
    hold(move, "body", rot=(12, 0, 0))
    hold(move, "rump", rot=(-2, 0, 0))
    run = clip_of(r, "run", 0.5, loop=True)
    buzz(run, 45, 8, 0.4)
    hold(run, "body", rot=(20, 0, 0))
    hold(run, "ant_l", rot=(-60, 0, 15))
    hold(run, "ant_r", rot=(-60, 0, -15))
    want = clip_of(r, "want", 3.2, loop=True)
    # sinks to the bare ground by its nest, pats about for a flower that isn't there, and floats up again, drooping
    buzz(want, 25, 32, 0.3)
    want.pos("body", keys((0, 0, 0, 0), (0.8, 0, -5, 0), (2.0, 0, -5, 0), (2.7, 0, -1, 0), (3.2, 0, 0, 0)))
    want.rot("head", keys((0, 6, 0, 0), (0.8, 30, 0, 0), (1.2, 30, -20, 0), (1.6, 30, 20, 0), (2.0, 30, 0, 0), (2.7, 14, 0, 0), (3.2, 6, 0, 0)))
    for nm, sg in (("l", 1), ("r", -1)):
        want.rot("legs_" + nm, keys((0, 15, 0, sg * 15), (0.9, -20, 0, sg * 10), (1.2, 10, 0, sg * 10), (1.5, -20, 0, sg * 10),
                                    (1.8, 10, 0, sg * 10), (2.2, 15, 0, sg * 15), (3.2, 15, 0, sg * 15)))
        want.rot("ant_" + nm, keys((0, -25, 0, sg * 22), (2.0, -25, 0, sg * 22), (2.4, 20, 0, sg * 35), (3.0, 20, 0, sg * 35), (3.2, -25, 0, sg * 22)))
    tell = clip_of(r, "tell", 1.4)
    # a happy roll in the air, fuzz fluffing out
    buzz(tell, 45, 14, 0.4)
    tell.rot("body", keys((0, 0, 0, 0), (0.2, 0, 0, -20), (0.7, 0, 0, 200), (1.0, 0, 0, 360), (1.4, 0, 0, 360)))
    tell.pos("body", keys((0, 0, 0, 0), (0.5, 0, 3, 0), (1.0, 0, 2, 0), (1.4, 0, 0, 0)))
    tell.scale("body", keys((0, 1, 1, 1), (0.4, 1.12, 1.12, 1.12), (1.0, 1.05, 1.05, 1.05), (1.4, 1, 1, 1)))
    nudge = clip_of(r, "nudge", 1.0)
    buzz(nudge, 30, 10, 0.3)
    nudge.rot("head", keys((0, 6, 0, 0), (0.2, 6, -25, 0), (0.4, 6, 25, 0), (0.6, 6, -15, 0), (1.0, 6, 0, 0)))
    nudge.pos("body", keys((0, 0, 0, 0), (0.3, 0, 0, 1.5), (1.0, 0, 0, 0)))
    work = clip_of(r, "work", 2.4, loop=True)
    # drifting round its hive, the rump pumping as it makes comb
    buzz(work, 35, 24, 0.6)
    cycle(work, "body", "rot", lambda p: (4 * s(p), 0, 8 * s(p, 0.25)), 8)
    cycle(work, "rump", "scale", lambda p: (1 + 0.06 * s(p * 3), 1 + 0.08 * s(p * 3), 1 - 0.05 * s(p * 3)), 12)
    sleep = clip_of(r, "sleep", 4.0, loop=True)
    # down on the ground, wings laid back, a fuzzy heap that breathes
    hold(sleep, "body", pos=(0, -6.5, 0), rot=(-4, 0, 0))
    for nm, sg in (("l", 1), ("r", -1)):
        hold(sleep, "wing_" + nm, rot=(0, sg * -70, sg * 4))
        hold(sleep, "hwing_" + nm, rot=(0, sg * -40, sg * 4))
        hold(sleep, "ant_" + nm, rot=(25, 0, sg * 35))
        hold(sleep, "legs_" + nm, rot=(-30, 0, sg * 50))
    hold(sleep, "head", rot=(20, 0, 0))
    cycle(sleep, "body", "scale", lambda p: (1 + 0.04 * s(p), 1 + 0.05 * s(p), 1 + 0.03 * s(p)), 8)
    sniff = clip_of(r, "sniff", 1.2)
    buzz(sniff, 30, 12, 0.3)
    for nm, sg in (("l", 1), ("r", -1)):
        sniff.rot("ant_" + nm, keys((0, -25, 0, sg * 22), (0.2, 0, 0, sg * 10), (0.4, -30, 0, sg * 30), (0.6, 0, 0, sg * 10), (1.2, -25, 0, sg * 22)))
    sniff.rot("head", keys((0, 6, 0, 0), (0.3, 25, 0, 0), (0.9, 25, 0, 0), (1.2, 6, 0, 0)))
    carry = clip_of(r, "carry", 0.6, loop=True)
    buzz(carry, 45, 8, 0.4)
    hold(carry, "legs_l", rot=(-40, 0, 10))
    hold(carry, "legs_r", rot=(-40, 0, -10))
    hide = clip_of(r, "hide", 1.0, loop=True)
    buzz(hide, 15, 16, 0.15)
    hold(hide, "body", pos=(0, -4, 0), rot=(-15, 0, 0))
    hold(hide, "ant_l", rot=(30, 0, 10))
    hold(hide, "ant_r", rot=(30, 0, -10))
    stubs(r)
    return r


def paint_bumblepuff(r, P):
    gold = Ramp("#6a4810", "#94661a", "#c08a24", "#e0ac36", "#f0c040", "#f8d870", "#fff0b0")
    soot = Ramp("#0e0a08", "#1a1410", "#2a221a", "#3a3026", "#4a3e32")
    fuzz = combine(p_fur(0.9, 2, 4, 2), p_blotch("bumblepuff", 3, 0.3))
    P.paint("fuzz", gold, 4.2, fuzz)
    P.paint("orump", gold, 4.2, fuzz)
    # two soft black bands round the middle, three round the rump; the tuft at the end gold
    for cb, bands in (("fuzz", (6, 7)), ("orump", (0, 1, 4, 5))):
        for fname in ("top", "west", "east", "bottom"):
            def band(f, i, j, bands=bands, fname=fname):
                z = j if fname in ("top", "bottom") else (i if fname == "west" else f.w - 1 - i)
                if fname == "west":
                    z = f.w - 1 - i
                return soot.at(2.6 + 0.6 * ((i + j) % 2)) if z in bands else None
            face_fill(P, cb, fname, band)
    P.paint("collar", Ramp("#c8b48a", "#e0d0a8", "#f2e6c8", "#fff8e8"), 2.6, p_fur(0.9, 2, 3, 2))
    P.paint("tuft", gold, 5.0)
    P.paint("ohead", soot, 2.6, p_fur(0.5, 2, 1, 2))
    P.paint("snout", gold, 3.4)
    P.paint("stalk_l", soot, 2.0)
    P.paint("knob_l", gold, 4.0)
    P.paint("oleg_l", soot, 1.8)
    # little glassy wings: pale, a vein or two, a bright edge
    for cb in ("wingf_l", "wingh_l"):
        for fname in ("top", "bottom"):
            face_fill(P, cb, fname, lambda f, i, j: None if (i == f.w - 1 and j in (0, f.h - 1)) else
                      (hexc("#f4f8ff") if i == f.w - 1 or j == 0 else (hexc("#b8c8dc") if (i + 2 * j) % 5 == 0 else hexc("#dfe8f4"))))
        for fname in ("north", "south", "west", "east"):
            face_fill(P, cb, fname, lambda f, i, j: hexc("#dfe8f4"))
    # big glossy eyes on the head's sides, a soft smile under the snout
    with P.eyes():
        for fname in ("west", "east"):
            f = P.face("ohead", fname)
            ex = front_i(f)
            step = 1 if fname == "west" else -1
            for di in (0, 1, 2):
                for j in (1, 2, 3, 4):
                    f.set(ex + step * di, j, hexc("#0a0608"))
            f.set(ex + step, 1, hexc("#c8d4e8"))
            f.set(ex, 2, hexc("#6a5a70"))
    face_fill(P, "ohead", "north", lambda f, i, j: hexc("#f0c040") if j == f.h - 1 and 0 < i < f.w - 1 else None)
    return P.skin, P.glow


# --- Loomworm: a fat, fuzzy caterpillar that spins ---------------------------------------------------------------------

def loomworm():
    r = Rig("kin_loomworm", (64, 64))
    r.walk_stride, r.run_stride, r.run_speed, r.shadow = 0.3, 0.6, (0.03, 0.1), 0.45
    body = r.bone("body", (0, 20.5, 0))
    body.cube("seg_c", -3.5, -3.5, -2.5, 7, 7, 5)
    body.cube("tuft_c", -1, -5.5, -1, 2, 2, 2)
    s1 = body.bone("seg1", (0, 0.5, -2.5))
    s1.cube("oseg1", -3, -3, -4, 6, 6, 4)
    head = s1.bone("head", (0, -0.5, -4), (-6, 0, 0))
    head.cube("ohead", -3, -3.5, -4, 6, 6, 4)
    s3 = body.bone("seg3", (0, 0.5, 2.5))
    s3.cube("oseg3", -3, -3, 0, 6, 6, 4)
    s3.cube("tuft_3", -1, -5, 1, 2, 2, 2)
    s4 = s3.bone("seg4", (0, 0.5, 4))
    s4.cube("oseg4", -2.5, -2.5, 0, 5, 5, 3)
    tail = s4.bone("tail", (0, 0.5, 3), (-15, 0, 0))
    tail.cube("otail", -2, -2, 0, 4, 4, 2)
    tail.cube("spinneret", -0.5, -3, 1, 1, 1, 1)

    def side(sd, nm, mir, sh):
        fl = head.bone("feel_" + nm, (sd * 1.5, -3.5, -3), (-20, 0, sd * 25))
        fl.cube("ofeel_" + nm, -0.5, -3, -0.5, 1, 3, 1, mirror=mir, share=sh("ofeel_l"))
        x0 = 2.5 if sd > 0 else -3.5
        body.cube("pro_" + nm, x0, 3, -1.5, 1, 1, 3, mirror=mir, share=sh("pro_l"))
        s3.cube("prob_" + nm, x0 + (-0.5 if sd > 0 else 0.5), 2.5, 1, 1, 1, 2, mirror=mir, share=sh("prob_l"))
    sym(side)
    r.look = [("head", 1.0, 0.8)]
    r.pack()

    segs = [("seg1", 0.0), ("body", 0.12), ("seg3", 0.24), ("seg4", 0.36), ("tail", 0.48)]

    def crawl(cl, lift, beats=1):
        # a hump runs from tail to head, each segment lifting and pulling forward in turn
        for nm, ph in segs:
            if nm == "body":
                cycle(cl, nm, "pos", lambda p, ph=ph: (0, lift * max(0, s(p * beats, -ph)), 0.3 * s(p * beats, -ph)), 8 * beats)
            else:
                cycle(cl, nm, "rot", lambda p, ph=ph: (-14 * lift * max(0, s(p * beats, -ph)) / 1.5, 0, 0), 8 * beats)
        cycle(cl, "head", "rot", lambda p: (-6 + 5 * s(p * beats, 0.1), 0, 0), 8 * beats)
        for nm, sd in (("feel_l", 1), ("feel_r", -1)):
            cycle(cl, nm, "rot", lambda p, sd=sd: (-20 + 10 * s(p * beats * 2), 0, sd * 25), 8 * beats)

    idle = clip_of(r, "idle", 3.0, loop=True)
    cycle(idle, "body", "scale", lambda p: (1 + 0.03 * s(p), 1 + 0.04 * s(p), 1), 8)
    idle.rot("head", keys((0, -6, 0, 0), (0.8, -6, 0, 0), (1.1, -12, 25, 0), (1.8, -12, 25, 0), (2.1, -6, -15, 0), (2.6, -6, -15, 0), (3.0, -6, 0, 0)))
    idle.rot("feel_l", keys((0, -20, 0, 25), (0.4, -5, 0, 35), (0.6, -20, 0, 25), (3.0, -20, 0, 25)))
    idle.rot("feel_r", keys((0, -20, 0, -25), (1.5, -20, 0, -25), (1.7, -5, 0, -35), (1.9, -20, 0, -25), (3.0, -20, 0, -25)))
    move = clip_of(r, "move", 1.0, loop=True)
    crawl(move, 1.5)
    run = clip_of(r, "run", 0.6, loop=True)
    crawl(run, 2.2)
    want = clip_of(r, "want", 3.6, loop=True)
    # rears up and stretches toward the leaves over it, sways, can't reach, and flops back down
    want.rot("seg1", keys((0, 0, 0, 0), (0.7, -55, 0, 0), (2.2, -55, 0, 0), (2.6, 0, 0, 0), (3.6, 0, 0, 0)))
    want.rot("head", keys((0, -6, 0, 0), (0.7, -30, 0, 0), (1.2, -35, 12, 0), (1.7, -35, -12, 0), (2.2, -30, 0, 0), (2.6, 8, 0, 0), (3.0, 8, 0, 0), (3.6, -6, 0, 0)))
    want.pos("body", keys((0, 0, 0, 0), (0.7, 0, 0.5, 0.6), (2.2, 0, 0.5, 0.6), (2.6, 0, -0.3, 0), (2.9, 0, 0, 0), (3.6, 0, 0, 0)))
    for nm, sd in (("feel_l", 1), ("feel_r", -1)):
        want.rot(nm, keys((0, -20, 0, sd * 25), (0.7, -45, 0, sd * 15), (2.2, -45, 0, sd * 15), (2.6, 10, 0, sd * 40), (3.4, 10, 0, sd * 40), (3.6, -20, 0, sd * 25)))
    tell = clip_of(r, "tell", 1.4)
    # a happy wiggle, side to side all down its length, and a bounce
    for k, (nm, ph) in enumerate(segs):
        if nm == "body":
            tell.pos(nm, keys((0, 0, 0, 0), (0.25, 0, 1.5, 0), (0.5, 0, 0, 0), (1.4, 0, 0, 0)))
        else:
            sg = 1 if k % 2 == 0 else -1
            tell.rot(nm, keys((0, 0, 0, 0), (0.3, 0, sg * 18, 0), (0.6, 0, -sg * 18, 0), (0.9, 0, sg * 14, 0), (1.15, 0, -sg * 8, 0), (1.4, 0, 0, 0)))
    tell.rot("head", keys((0, -6, 0, 0), (0.25, -25, 0, 0), (1.0, -20, 0, 0), (1.4, -6, 0, 0)))
    nudge = clip_of(r, "nudge", 1.0)
    nudge.rot("head", keys((0, -6, 0, 0), (0.2, -6, -25, 0), (0.4, -6, 25, 0), (0.6, -6, -15, 0), (1.0, -6, 0, 0)))
    nudge.rot("seg1", keys((0, 0, 0, 0), (0.3, 15, 0, 0), (0.8, 15, 0, 0), (1.0, 0, 0, 0)))
    work = clip_of(r, "work", 2.4, loop=True)
    # spinning: the head weaving back and forth over its work, the tail end pumping thread
    cycle(work, "head", "rot", lambda p: (10 + 6 * s(p * 4), 28 * s(p * 2), 0), 16)
    cycle(work, "seg1", "rot", lambda p: (-20, 10 * s(p * 2, 0.1), 0), 16)
    cycle(work, "tail", "rot", lambda p: (-15 - 12 * max(0, s(p * 4)), 0, 0), 16)
    sleep = clip_of(r, "sleep", 4.0, loop=True)
    # curled into a fat C, head tucked to its tail
    hold(sleep, "seg1", rot=(0, 40, 0))
    hold(sleep, "head", rot=(15, 40, 0))
    hold(sleep, "seg3", rot=(0, -35, 0))
    hold(sleep, "seg4", rot=(0, -40, 0))
    hold(sleep, "tail", rot=(-5, -35, 0))
    hold(sleep, "feel_l", rot=(30, 0, 40))
    hold(sleep, "feel_r", rot=(30, 0, -40))
    cycle(sleep, "body", "scale", lambda p: (1 + 0.04 * s(p), 1 + 0.05 * s(p), 1), 8)
    sniff = clip_of(r, "sniff", 1.2)
    sniff.rot("head", keys((0, -6, 0, 0), (0.25, 20, 0, 0), (0.95, 20, 0, 0), (1.2, -6, 0, 0)))
    for nm, sd in (("feel_l", 1), ("feel_r", -1)):
        sniff.rot(nm, keys((0, -20, 0, sd * 25), (0.2, 10, 0, sd * 10), (0.4, -20, 0, sd * 30), (0.6, 10, 0, sd * 10), (0.8, -20, 0, sd * 30), (1.2, -20, 0, sd * 25)))
    carry = clip_of(r, "carry", 1.0, loop=True)
    crawl(carry, 1.2)
    hide = clip_of(r, "hide", 1.0, loop=True)
    # curled tight, trembling
    hold(hide, "seg1", rot=(20, 55, 0))
    hold(hide, "head", rot=(40, 45, 0))
    hold(hide, "seg3", rot=(0, -50, 0))
    hold(hide, "seg4", rot=(0, -55, 0))
    hold(hide, "tail", rot=(0, -50, 0))
    hold(hide, "feel_l", rot=(50, 0, 10))
    hold(hide, "feel_r", rot=(50, 0, -10))
    cycle(hide, "body", "pos", lambda p: (0.12 * s(p * 5), 0, 0), 24)
    stubs(r)
    return r


def paint_loomworm(r, P):
    leaf = Ramp("#3a5a26", "#527a34", "#6c9642", "#88b056", "#a4c870", "#bcd88a", "#d8ecb0")
    cream = Ramp("#b8ac88", "#d0c6a4", "#e6dec4", "#f6f2e2", "#ffffff")
    fuzz = combine(p_fur(0.8, 2, 3, 2), p_blotch("loomworm", 4, 0.25))
    for cb in ("seg_c", "oseg1", "oseg3", "oseg4", "otail"):
        P.paint(cb, leaf, 4.0, fuzz)
        # a pale line where one segment meets the next, and a row of soft yellow spots down each side
        face_fill(P, cb, "top", lambda f, i, j: leaf.at(2.4) if j in (0, f.h - 1) else None)
        for fname in ("west", "east"):
            face_fill(P, cb, fname, lambda f, i, j: hexc("#f0e070") if j == f.h // 2 and i == f.w // 2 else
                      (cream.at(2.6) if j >= f.h - 1 else None))
        face_fill(P, cb, "bottom", lambda f, i, j: cream.at(1.8 + 0.5 * ((i + j) % 2)))
    for cb in ("tuft_c", "tuft_3"):
        P.paint(cb, cream, 3.4, p_fur(0.9, 2, 2, 2))
    P.paint("ohead", Ramp("#4a6a30", "#5e823c", "#729a4a", "#88b05a", "#9cc46c"), 3.2, p_fur(0.4, 2, 1, 2))
    P.paint("ofeel_l", Ramp("#3a4a2a", "#4a5e36", "#5e7444"), 2.0)
    P.paint("spinneret", cream, 3.6)
    for cb in ("pro_l", "prob_l"):
        P.paint(cb, Ramp("#2e3e22", "#3e522c", "#4e6638"), 1.8)
    # a round friendly face: big dark eyes set wide on its front, a pale little mouth
    with P.eyes():
        f = P.face("ohead", "north")
        for x in (0, f.w - 2):
            for dx in (0, 1):
                for j in (1, 2, 3):
                    f.set(x + dx, j, hexc("#0c100a"))
            f.set(x + (1 if x == 0 else 0), 1, hexc("#e0ecd0"))
    face_fill(P, "ohead", "north", lambda f, i, j: cream.at(3.0) if j == f.h - 2 and 1 < i < f.w - 2 else None)
    return P.skin, P.glow


# --- Cloudcalf: a sky-whale calf as big as a cow, with a little cloud riding on its back -----------------------------

def cloudcalf():
    r = Rig("kin_cloudcalf", (128, 64))
    r.walk_stride, r.run_stride, r.run_speed, r.shadow = 1.4, 2.6, (0.08, 0.2), 0.0
    body = r.bone("body", (0, 12, 0))
    body.cube("hull", -7, -6, -6, 14, 12, 12)
    body.cube("belly", -6, 4, -5, 12, 3, 10)
    head = body.bone("head", (0, 0.5, -6))
    head.cube("ohead", -6, -5.5, -7, 12, 10, 7)
    head.cube("melon", -5, -6.5, -6, 10, 1, 5)
    jaw = head.bone("jaw", (0, 4.5, -0.5))
    jaw.cube("ojaw", -5.5, 0, -6.5, 11, 2, 7)
    tail = body.bone("tail", (0, -0.5, 6))
    tail.cube("otail", -4.5, -4, 0, 9, 8, 7)
    flukes = tail.bone("flukes", (0, -0.5, 7))
    flukes.cube("stock", -2.5, -2, 0, 5, 4, 2)
    flukes.cube("ofluke", -7, -0.5, 1, 14, 1, 5)
    cloud = body.bone("cloud", (0, -6, -1))
    cloud.cube("puff_a", -4, -3, -3, 8, 3, 7)
    cloud.cube("puff_b", -2.5, -5, -1.5, 5, 2, 4)

    def side(sd, nm, mir, sh):
        fin = body.bone("fin_" + nm, (sd * 7, 2, -3), (0, sd * -20, sd * 12))
        if sd > 0:
            fin.cube("pfin_l", 0, -0.5, -2, 7, 1, 5)
        else:
            fin.cube("pfin_r", -7, -0.5, -2, 7, 1, 5, mirror=True, share="pfin_l")
    sym(side)
    r.look = [("head", 0.6, 0.5)]
    r.pack()

    def swim(cl, amp, bob=1.0, beats=1):
        # the sky as water: the flukes beat slow and deep, the body rides each beat, the fins steer
        n = 8 * beats
        cycle(cl, "tail", "rot", lambda p: (amp * 0.5 * s(p * beats), 0, 0), n)
        cycle(cl, "flukes", "rot", lambda p: (amp * s(p * beats, -0.15), 0, 0), n)
        cycle(cl, "body", "pos", lambda p: (0, bob * s(p * beats, 0.25), 0), n)
        for nm, sg in (("l", 1), ("r", -1)):
            cycle(cl, "fin_" + nm, "rot", lambda p, sg=sg: (0, sg * -20, sg * (12 + 10 * s(p * beats, 0.3))), n)
        cycle(cl, "cloud", "pos", lambda p: (0.3 * s(p * beats, 0.4), 0.4 * s(p * beats, 0.5), 0), n)

    idle = clip_of(r, "idle", 4.0, loop=True)
    swim(idle, 12, 1.2)
    cycle(idle, "cloud", "scale", lambda p: (1 + 0.05 * s(p * 2), 1 + 0.08 * s(p * 2), 1 + 0.05 * s(p * 2)), 8)
    idle.rot("head", keys((0, 0, 0, 0), (1.4, 0, 0, 0), (2.0, -4, 12, 0), (2.8, -4, 12, 0), (3.4, 0, 0, 0), (4.0, 0, 0, 0)))
    move = clip_of(r, "move", 2.0, loop=True)
    swim(move, 20, 1.0)
    hold(move, "body", rot=(4, 0, 0))
    run = clip_of(r, "run", 1.0, loop=True)
    swim(run, 28, 0.8)
    hold(run, "body", rot=(9, 0, 0))
    want = clip_of(r, "want", 4.0, loop=True)
    # calling for its pod: head up, jaw open, a long call this way and then that, listening between
    swim(want, 10, 0.8)
    want.rot("head", keys((0, 0, 0, 0), (0.5, -18, -20, 0), (1.5, -18, -20, 0), (2.0, -8, 0, 0), (2.5, -18, 20, 0), (3.5, -18, 20, 0), (4.0, 0, 0, 0)))
    want.rot("jaw", keys((0, 0, 0, 0), (0.6, 18, 0, 0), (1.3, 18, 0, 0), (1.6, 0, 0, 0), (2.6, 18, 0, 0), (3.3, 18, 0, 0), (3.6, 0, 0, 0), (4.0, 0, 0, 0)))
    want.rot("body", keys((0, 0, 0, 0), (0.5, -6, -10, 0), (1.5, -6, -10, 0), (2.0, 0, 0, 0), (2.5, -6, 10, 0), (3.5, -6, 10, 0), (4.0, 0, 0, 0)))
    tell = clip_of(r, "tell", 2.0)
    # a joyful roll, flukes slapping the air, the little cloud puffing up big
    swim(tell, 30, 0.6, 2)
    tell.rot("body", keys((0, 0, 0, 0), (0.3, -8, 0, -20), (1.0, -8, 0, 200), (1.5, 0, 0, 360), (2.0, 0, 0, 360)))
    tell.scale("cloud", keys((0, 1, 1, 1), (0.4, 1.4, 1.5, 1.4), (1.4, 1.3, 1.4, 1.3), (2.0, 1, 1, 1)))
    tell.rot("jaw", keys((0, 0, 0, 0), (0.3, 15, 0, 0), (1.6, 15, 0, 0), (2.0, 0, 0, 0)))
    nudge = clip_of(r, "nudge", 1.2)
    swim(nudge, 10, 0.4)
    nudge.rot("head", keys((0, 0, 0, 0), (0.25, 0, -15, 0), (0.55, 0, 15, 0), (0.85, 0, -8, 0), (1.2, 0, 0, 0)))
    nudge.rot("jaw", keys((0, 0, 0, 0), (0.2, 8, 0, 0), (0.6, 0, 0, 0), (1.2, 0, 0, 0)))
    work = clip_of(r, "work", 3.0, loop=True)
    # raining on the field: it hangs still, the cloud on its back swelling and squeezing
    swim(work, 8, 0.5)
    cycle(work, "cloud", "scale", lambda p: (1.25 + 0.1 * s(p * 2), 1.35 + 0.15 * s(p * 2), 1.25 + 0.1 * s(p * 2)), 12)
    hold(work, "head", rot=(10, 0, 0))
    sleep = clip_of(r, "sleep", 5.0, loop=True)
    # come down to rest on the ground, fins flat out, flukes laid down, the cloud settled over it like a blanket
    hold(sleep, "body", pos=(0, -5, 0))
    hold(sleep, "fin_l", rot=(0, 0, 75))
    hold(sleep, "fin_r", rot=(0, 0, -75))
    hold(sleep, "tail", rot=(-8, 0, 0))
    hold(sleep, "flukes", rot=(-10, 0, 0))
    hold(sleep, "head", rot=(4, 0, 0))
    cycle(sleep, "body", "scale", lambda p: (1 + 0.025 * s(p), 1 + 0.03 * s(p), 1), 8)
    cycle(sleep, "cloud", "scale", lambda p: (1.2 + 0.05 * s(p, 0.3), 0.8 + 0.05 * s(p, 0.3), 1.2), 8)
    sniff = clip_of(r, "sniff", 1.4)
    swim(sniff, 10, 0.4)
    sniff.rot("head", keys((0, 0, 0, 0), (0.3, 6, -18, 0), (0.7, 6, 18, 0), (1.1, 6, 0, 0), (1.4, 0, 0, 0)))
    carry = clip_of(r, "carry", 2.0, loop=True)
    swim(carry, 18, 1.0)
    hide = clip_of(r, "hide", 1.2, loop=True)
    hold(hide, "fin_l", rot=(0, -20, 70))
    hold(hide, "fin_r", rot=(0, 20, -70))
    hold(hide, "tail", rot=(25, 0, 0))
    hold(hide, "flukes", rot=(30, 0, 0))
    hold(hide, "head", rot=(14, 0, 0))
    cycle(hide, "body", "pos", lambda p: (0.15 * s(p * 4), -3, 0), 24)
    stubs(r)
    return r


def paint_cloudcalf(r, P):
    sky = Ramp("#2e4266", "#3e5684", "#506ca0", "#6684b8", "#7e9ccc", "#98b4de", "#b8cef0")
    white = Ramp("#a8b4c8", "#c4cede", "#dce4ee", "#eef2f8", "#ffffff")
    soft = combine(p_blotch("cloudcalf", 6, 0.35), p_fur(0.3, 3, 2, 3))
    for cb in ("hull", "ohead", "otail", "stock", "melon"):
        P.paint(cb, sky, 4.2, soft)
    # pale underneath: belly and jaw, with the long grooves of a whale's throat
    P.paint("belly", white, 3.0)
    face_fill(P, "belly", "bottom", lambda f, i, j: white.at(1.6) if i % 2 == 0 else None)
    P.paint("ojaw", white, 3.0)
    face_fill(P, "ojaw", "bottom", lambda f, i, j: white.at(1.6) if i % 2 == 0 else None)
    for cb in ("hull", "ohead", "otail"):
        for fname in ("west", "east", "north", "south"):
            face_fill(P, cb, fname, lambda f, i, j: white.at(2.6 + 0.4 * (j - f.h + 3)) if j >= f.h - 3 else None)
    # freckles across its back like early stars; they glow faintly when the light goes
    for cb in ("hull", "ohead"):
        face_fill(P, cb, "top", lambda f, i, j: white.at(3.6) if (i * 7 + j * 13) % 17 == 0 else None)
        P.glow_face(cb, "top", lambda f, i, j: hexc("#d8e6ff") if (i * 7 + j * 13) % 17 == 0 else None)
    for cb in ("pfin_l", "ofluke"):
        P.paint(cb, sky, 3.2)
        face_fill(P, cb, "top", lambda f, i, j: white.at(3.4) if j == f.h - 1 else None)
    # the cloud it carries: soft and white, a little grey underneath
    for cb in ("puff_a", "puff_b"):
        P.paint(cb, white, 3.8, p_fur(0.9, 3, 3, 3))
        face_fill(P, cb, "bottom", lambda f, i, j: white.at(1.6))
    # a small gentle eye low on each side, toward the back of the head
    with P.eyes():
        for fname in ("west", "east"):
            f = P.face("ohead", fname)
            ex = f.w - 3 if fname == "west" else 2
            for j in (5, 6):
                f.set(ex, j, hexc("#0e1220"))
                f.set(ex + (1 if fname == "west" else -1), j, hexc("#0e1220"))
            f.set(ex, 5, hexc("#cfe0ff"))
    return P.skin, P.glow


# --- Boulderback: big, tusked and mossy, with eyes that glow in the dark. Looks like a monster; shy as anything --------

def boulderback():
    r = Rig("kin_boulderback", (128, 128))
    r.walk_stride, r.run_stride, r.run_speed, r.shadow = 0.9, 1.6, (0.06, 0.16), 1.0
    body = r.bone("body", (0, 11, 0))
    body.cube("hide", -8, -7, -10, 16, 14, 20)
    body.cube("hump", -6, -10, -7, 12, 3, 13)
    body.cube("moss", -6.5, -10.5, -5, 13, 1, 9)
    body.cube("rocks", -5, -11, -1, 5, 1, 4)
    head = body.bone("head", (0, 3, -10), (15, 0, 0))
    head.cube("ohead", -5, -5, -7, 10, 9, 7)
    head.cube("snout", -3, 0, -9, 6, 4, 2)
    tail = body.bone("tail", (0, -4, 10), (25, 0, 0))
    tail.cube("otail", -1, 0, -0.5, 2, 5, 1)
    tail.cube("tailtuft", -1.5, 5, -1, 3, 2, 2)

    def side(sd, nm, mir, sh):
        tk = head.bone("tusk_" + nm, (sd * 3.5, 2, -7), (-30, sd * -12, 0))
        tk.cube("tbase_" + nm, -1, -1, -5, 2, 2, 5, mirror=mir, share=sh("tbase_l"))
        tk.cube("ttip_" + nm, -1, -4, -6, 2, 3, 2, mirror=mir, share=sh("ttip_l"))
        ear = head.bone("ear_" + nm, (sd * 5, -4, -2), (0, 0, sd * 30))
        ear.cube("oear_" + nm, -0.5, 0, -1, 1, 3, 2, mirror=mir, share=sh("oear_l"))
        for part, z in (("fleg", -6), ("hleg", 6)):
            lg = body.bone(part + "_" + nm, (sd * 5, 7, z))
            lg.cube("o" + part + "_" + nm, -2.5, 0, -2.5, 5, 6, 5, mirror=mir, share=sh("ofleg_l") if part == "fleg" else "ofleg_l")
    sym(side)
    r.look = [("head", 0.7, 0.5)]
    r.pack()

    legs = [("fleg_l", 0), ("hleg_r", 0.05), ("fleg_r", 0.5), ("hleg_l", 0.55)]

    def lumber(cl, swing, roll, bob):
        gait4(cl, swing, legs)
        cycle(cl, "body", "rot", lambda p: (0, 0, roll * s(p)), 8)
        cycle(cl, "body", "pos", lambda p: (0, bob * abs(s(p)), 0), 8)
        cycle(cl, "head", "rot", lambda p: (15 + 3 * s(p * 2, 0.1), 4 * s(p), 0), 8)
        cycle(cl, "tail", "rot", lambda p: (25, 0, 12 * s(p, 0.2)), 8)

    idle = clip_of(r, "idle", 4.0, loop=True)
    cycle(idle, "body", "scale", lambda p: (1 + 0.015 * s(p), 1 + 0.02 * s(p), 1), 8)
    idle.rot("head", keys((0, 15, 0, 0), (1.2, 15, 0, 0), (1.8, 10, 18, 0), (2.6, 10, 18, 0), (3.2, 15, 0, 0), (4.0, 15, 0, 0)))
    idle.rot("ear_l", keys((0, 0, 0, 30), (2.9, 0, 0, 30), (3.0, 0, 0, 10), (3.15, 0, 0, 30), (4.0, 0, 0, 30)))
    move = clip_of(r, "move", 1.2, loop=True)
    lumber(move, 22, 3, 0.6)
    run = clip_of(r, "run", 0.7, loop=True)
    lumber(run, 35, 4, 1.4)
    want = clip_of(r, "want", 4.0, loop=True)
    # keeps very still and backs off from your light, head down and tusks low; peeks; hides its face again
    want.pos("body", keys((0, 0, 0, 0), (1.0, 0, 0, 2.5), (2.8, 0, 0, 2.5), (4.0, 0, 0, 0)))
    want.rot("head", keys((0, 15, 0, 0), (1.0, 38, 0, 0), (1.8, 30, -14, 0), (2.4, 30, -14, 0), (2.8, 38, 0, 0), (4.0, 15, 0, 0)))
    for nm, sd in (("ear_l", 1), ("ear_r", -1)):
        want.rot(nm, keys((0, 0, 0, sd * 30), (1.0, 0, 0, sd * 70), (2.8, 0, 0, sd * 70), (4.0, 0, 0, sd * 30)))
    for nm, a in (("fleg_l", -14), ("fleg_r", -8), ("hleg_l", 10), ("hleg_r", 14)):
        want.rot(nm, keys((0, 0, 0, 0), (0.4, a, 0, 0), (0.8, 0, 0, 0), (2.8, 0, 0, 0), (3.4, -a, 0, 0), (4.0, 0, 0, 0)))
    tell = clip_of(r, "tell", 2.0)
    # a slow, happy rumble: it rocks, lifts its head to you and gives a little stamp
    tell.rot("head", keys((0, 15, 0, 0), (0.4, -10, 0, 0), (1.4, -10, 0, 0), (2.0, 15, 0, 0)))
    tell.rot("body", keys((0, 0, 0, 0), (0.5, 0, 0, 4), (1.0, 0, 0, -4), (1.5, 0, 0, 3), (2.0, 0, 0, 0)))
    tell.rot("fleg_l", keys((0, 0, 0, 0), (1.2, 0, 0, 0), (1.4, -25, 0, 0), (1.6, 0, 0, 0), (2.0, 0, 0, 0)))
    tell.scale("body", keys((0, 1, 1, 1), (0.6, 1.04, 1.05, 1.02), (1.4, 1.04, 1.05, 1.02), (2.0, 1, 1, 1)))
    for nm, sd in (("ear_l", 1), ("ear_r", -1)):
        tell.rot(nm, keys((0, 0, 0, sd * 30), (0.4, 0, 0, sd * 5), (1.6, 0, 0, sd * 5), (2.0, 0, 0, sd * 30)))
    nudge = clip_of(r, "nudge", 1.2)
    # a snort and a step back
    nudge.rot("head", keys((0, 15, 0, 0), (0.2, 5, 0, 0), (0.35, 25, -10, 0), (0.6, 25, 10, 0), (1.2, 15, 0, 0)))
    nudge.pos("body", keys((0, 0, 0, 0), (0.4, 0, 0, 1.5), (1.2, 0, 0, 0)))
    work = clip_of(r, "work", 3.0, loop=True)
    # carrying stone: head down to its load, a heave forward, set down, back for more
    cycle(work, "head", "rot", lambda p: (30 + 10 * s(p * 2), 0, 0), 8)
    cycle(work, "body", "pos", lambda p: (0, 0.6 * max(0, s(p * 2)), -1.2 * s(p * 2)), 8)
    gait4(work, 10, legs)
    sleep = clip_of(r, "sleep", 5.0, loop=True)
    # lies down like a hill, head on its front feet, and breathes very slowly
    hold(sleep, "body", pos=(0, -5.5, 0))
    for nm in ("fleg_l", "fleg_r"):
        hold(sleep, nm, rot=(-80, 0, 0))
    for nm in ("hleg_l", "hleg_r"):
        hold(sleep, nm, rot=(80, 0, 0))
    hold(sleep, "head", rot=(28, 0, 0), pos=(0, -0.5, 0))
    hold(sleep, "ear_l", rot=(0, 0, 80))
    hold(sleep, "ear_r", rot=(0, 0, -80))
    cycle(sleep, "body", "scale", lambda p: (1 + 0.02 * s(p), 1 + 0.03 * s(p), 1), 8)
    sniff = clip_of(r, "sniff", 1.4)
    sk = [(0, 15, 0, 0)]
    for k in range(5):
        sk += [(0.25 + k * 0.16, 36, 0, 0), (0.33 + k * 0.16, 30, 0, 0)]
    sk.append((1.4, 15, 0, 0))
    sniff.rot("head", sk, "lin")
    carry = clip_of(r, "carry", 1.3, loop=True)
    lumber(carry, 18, 2, 0.5)
    hold(carry, "head", rot=(28, 0, 0))
    hide = clip_of(r, "hide", 1.2, loop=True)
    # crouched as small as something that big can make itself, face tucked down between its feet
    hold(hide, "body", pos=(0, -3.5, 1))
    for nm in ("fleg_l", "fleg_r"):
        hold(hide, nm, rot=(-55, 0, 0))
    for nm in ("hleg_l", "hleg_r"):
        hold(hide, nm, rot=(55, 0, 0))
    hold(hide, "head", rot=(45, 0, 0), pos=(0, 0.5, 1))
    hold(hide, "ear_l", rot=(0, 0, 85))
    hold(hide, "ear_r", rot=(0, 0, -85))
    cycle(hide, "body", "rot", lambda p: (0, 0, 1.2 * s(p * 5)), 24)
    stubs(r)
    return r


def paint_boulderback(r, P):
    slate = Ramp("#1e2420", "#2a322c", "#38423a", "#48544a", "#5a685c", "#6e7e70", "#86968a")
    moss = Ramp("#24401a", "#345a22", "#46762c", "#5a8e36", "#72a844", "#8cc058")
    stone = Ramp("#3a3c40", "#4c5056", "#62666c", "#7a7e84", "#94989e")
    P.paint("hide", slate, 3.6, combine(p_stone(P.rng, 0.6, 3), p_fur(0.3, 2, 2, 2)))
    # the plates on its back: big stones grown into the hide, with moss in the cracks
    P.paint("hump", stone, 3.0, p_stone(P.rng, 0.9, 4))
    P.paint("rocks", stone, 3.4, p_stone(P.rng, 1.0, 2))
    P.paint("moss", moss, 3.6, p_fur(0.9, 2, 2, 2))
    for fname in ("west", "east", "north", "south"):
        face_fill(P, "hump", fname, lambda f, i, j: moss.at(2.4 + (i % 2)) if j == 0 and (i * 3) % 4 != 1 else None)
        face_fill(P, "hide", fname, lambda f, i, j: moss.at(2.0 + 0.6 * ((i + j) % 2)) if j < 2 and (i * 5 + j) % 3 != 0 else None)
    face_fill(P, "hide", "top", lambda f, i, j: moss.at(2.4 + 0.8 * ((i * 3 + j) % 3 == 0)) if (i * 7 + j * 3) % 5 < 3 else None)
    face_fill(P, "hide", "bottom", lambda f, i, j: slate.at(1.6))
    P.paint("ohead", slate, 3.4, p_stone(P.rng, 0.4, 2))
    P.paint("snout", Ramp("#2a2a2c", "#3a3a3e", "#4e4c52", "#625e64"), 2.6)
    face_fill(P, "snout", "north", lambda f, i, j: hexc("#141416") if j == 1 and i in (1, f.w - 2) else None)
    ivory = Ramp("#8a7e66", "#aa9e84", "#c8bea2", "#e2dac2", "#f4eee0")
    for cb in ("tbase_l", "ttip_l"):
        P.paint(cb, ivory, 3.4, R.p_bands(2, 0.5))
    P.paint("oear_l", slate, 2.8)
    P.paint("ofleg_l", slate, 3.0, p_stone(P.rng, 0.4, 2))
    face_fill(P, "ofleg_l", "bottom", lambda f, i, j: hexc("#121614"))
    face_fill(P, "ofleg_l", "north", lambda f, i, j: hexc("#1a1e1a") if j == f.h - 1 and i % 2 == 0 else None)
    P.paint("otail", slate, 2.6)
    P.paint("tailtuft", moss, 3.0, p_fur(0.8, 2, 2, 2))
    # small deep-set eyes that shine pale green in the dark, under a heavy brow
    with P.eyes():
        for fname in ("west", "east"):
            f = P.face("ohead", fname)
            ex = front_i(f) + (1 if fname == "west" else -1)
            for j in (3, 4):
                f.set(ex, j, hexc("#0c120e"))
                f.set(ex + (1 if fname == "west" else -1), j, hexc("#0c120e"))
            f.set(ex, 3, hexc("#b8f0b0"))
        P.glow_face("ohead", fname, lambda f, i, j: hexc("#b8f0b0") if j == 3 and i == front_i(f) + (1 if f.name == "west" else -1) else None)
    for fname in ("west", "east"):
        P.glow_face("ohead", fname, lambda f, i, j: hexc("#a8f0a0") if j in (3, 4) and i == front_i(f) + (1 if f.name == "west" else -1) else None)
    face_fill(P, "ohead", "north", lambda f, i, j: slate.at(1.4) if j == 2 else None)
    return P.skin, P.glow


DESIGNS = {"kin_lampwing": (lampwing, paint_lampwing), "kin_burrowbun": (burrowbun, paint_burrowbun),
           "kin_mossback": (mossback, paint_mossback), "kin_pebblepup": (pebblepup, paint_pebblepup),
           "kin_totter": (totter, paint_totter), "kin_thatchback": (thatchback, paint_thatchback),
           "kin_emberkit": (emberkit, paint_emberkit), "kin_puffwool": (puffwool, paint_puffwool),
           "kin_dumplin": (dumplin, paint_dumplin), "kin_pondling": (pondling, paint_pondling),
           "kin_snufflet": (snufflet, paint_snufflet), "kin_chimewren": (chimewren, paint_chimewren),
           "kin_bumblepuff": (bumblepuff, paint_bumblepuff), "kin_loomworm": (loomworm, paint_loomworm),
           "kin_cloudcalf": (cloudcalf, paint_cloudcalf), "kin_boulderback": (boulderback, paint_boulderback)}


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
