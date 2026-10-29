"""The Awakened layer, part three (P3): the Tidewater (rivers, lakes, seas) and the Hollows (caves), and the mounts.

    python3 tools/art/build_all.py fauna3          textures (rig skins and glows, eggs, materials, parts, bridle,
                                                   whistle, the bridle overlays of the Frost Stag and the Dune Ray) and
                                                   src/main/java/dev/awaken/mobs/client/anim/Fauna3Data.java
    python3 tools/art/specs/fauna3.py content      biome modifiers, loot tables, item models, lang, lairs (structures,
                                                   sets), recipes, requests, level-road steps, gear tags (biomes:
                                                   tools/terrain/biomes.py)
    python3 tools/art/specs/fauna3.py sounds       every voice (synthesised with fauna_sounds.py's DSP) merged into
                                                   src/main/merge/fauna/assets/awaken/sounds.json, subtitles in lang
    python3 tools/art/specs/fauna3.py preview DIR [ids]
                                                   per creature: three day views and a night view, a size line-up
                                                   with a player, and the key poses (idle, gait or swim, the held
                                                   poses, the signature wind-up and strike, roar, hurt, death)

Every body is a hero rig (fauna_rig.py: bones, packed UVs, clean value masses, keyframe clips sampled like vanilla);
the Java side parses Fauna3Data.java through HeroRig and draws it with mobs.client.anim.WildRenderer (swimmers pitch
with their swim; held poses: hang, chirp, ripen, listen, deflate, carry, rest, beached, ridden; bones named bridle*
show only on a bridled mount). The creature list is read from mobs/def/WaterBestiary.java and CaveBestiary.java.
W5 adds the seep heart (a rooted beast placed at a seep's socket: its rig, a sleep hold and a beat clip, its voice
synthesised with audiokit, its particle sprites) and "seep_root", the burst of roots its Rootfall throws up (a rig with
no creature). `fauna3.py sounds seep_heart` rebuilds only its voice.

Conventions (vanilla ModelPart): model pixels, y down, ground at y = 24, the creature faces -z, rotations in degrees.
"""
import json
import math
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ART = os.path.abspath(os.path.join(HERE, ".."))
ROOT = os.path.abspath(os.path.join(HERE, "../../.."))
sys.path.insert(0, HERE)
sys.path.insert(0, ART)
sys.path.insert(0, os.path.abspath(os.path.join(ROOT, "tools/content")))
import fauna_rig as R  # noqa: E402
from fauna_rig import Rig, Ramp, hexc, lerpc, mul, p_fur, p_scales, p_plates, p_bands, p_strokes, p_blotch, p_stone, combine, SIDES, _h  # noqa: E402,F401
from fauna_anim import cycle, quad_gait, body_bob, wave, s, c  # noqa: E402,F401
from fauna_heroes import clear, line, blob  # noqa: E402

JAVA_OUT = os.path.join(ROOT, "src/main/java/dev/awaken/mobs/client/anim/Fauna3Data.java")
DEFS = [os.path.join(ROOT, "src/main/java/dev/awaken/mobs/def/WaterBestiary.java"), os.path.join(ROOT, "src/main/java/dev/awaken/mobs/def/CaveBestiary.java")]
RES = os.path.join(ROOT, "src/main/resources")
MERGE = os.path.join(ROOT, "src/main/merge/fauna")
TEX_DIR = os.path.join(RES, "assets/awaken/textures/entity/mobs")


def build_hook():
    pass


# =====================================================================================================================
# The Tidewater
# =====================================================================================================================

# ---------------------------------------------------------------------------------------------------------------------
# Glimmerfin: a slim river fish, silver-blue with an iridescent stripe and a row of glowing scales along its flank

def glimmerfin():
    r = Rig("glimmerfin", (32, 32))
    r.walk_stride, r.run_stride, r.run_speed, r.shadow = 1.0, 2.0, (0.1, 0.2), 0.2
    body = r.bone("body", (0, 21, 0))
    body.cube("gbody", -1.5, -2, -2, 3, 4, 5)
    head = body.bone("head", (0, 0, -2))
    head.cube("ghead", -1.5, -1.5, -3, 3, 3, 3)
    head.cube("gsnout", -1, -0.5, -4, 2, 2, 1)
    body.bone("dorsal", (0, -2, -1), (-10, 0, 0)).cube("dorsal", 0, -2, 0, 0, 2, 4)
    for side, nm in ((1, "l"), (-1, "r")):
        body.bone("pec_" + nm, (side * 1.5, 1, -1), (0, side * -30, side * -40)).cube(
            "pec_" + nm, 0 if side > 0 else -2, 0, 0, 2, 0, 2, mirror=side < 0, share=None if side > 0 else "pec_l")
    t1 = body.bone("tail1", (0, 0, 3))
    t1.cube("gtail1", -1, -1.5, 0, 2, 3, 2)
    t2 = t1.bone("tail2", (0, 0, 2))
    t2.cube("gtail2", -0.5, -1, 0, 1, 2, 2)
    t2.bone("fin", (0, 0, 2)).cube("gfin", 0, -2.5, 0, 0, 5, 3)
    body.bone("anal", (0, 2, 1), (20, 0, 0)).cube("anal", 0, 0, 0, 0, 1, 3)
    r.look = [("head", 0.4, 0.3)]
    r.pack()
    glimmerfin_clips(r)
    return r


def glimmerfin_clips(r):
    idle = r.clip("idle", 2.0, loop=True)
    cycle(idle, "tail1", "rot", lambda p: (0, 8 * s(p), 0), 8)
    cycle(idle, "fin", "rot", lambda p: (0, 12 * s(p, -0.15), 0), 8)
    for nm, sg in (("pec_l", 1), ("pec_r", -1)):
        cycle(idle, nm, "rot", lambda p, sg=sg: (0, 0, sg * 18 * s(p * 2)), 8)
    cycle(idle, "body", "pos", lambda p: (0, 0.3 * s(p), 0), 8)
    fly = r.clip("fly", 0.5, loop=True)
    # the swim: a wave down the tail, the head yawing a little against it
    wave(fly, ["body", "tail1", "tail2", "fin"], "y", 0, lag=0.16, amps=[5, 20, 28, 34])
    cycle(fly, "head", "rot", lambda p: (0, -6 * s(p), 0), 8)
    for nm, sg in (("pec_l", 1), ("pec_r", -1)):
        cycle(fly, nm, "rot", lambda p, sg=sg: (0, sg * 20, sg * 30 * s(p * 2)), 8)
    beached = r.clip("beached", 0.6, loop=True)
    # stranded: on its side, flapping
    beached.rot("body", [(0, 0, 0, 88), (0.6, 0, 0, 88)])
    cycle(beached, "tail1", "rot", lambda p: (0, 30 * s(p), 0), 6)
    cycle(beached, "fin", "rot", lambda p: (0, 30 * s(p, -0.2), 0), 6)
    cycle(beached, "head", "rot", lambda p: (0, -14 * s(p), 0), 6)
    hurt = r.clip("hurt", 0.35)
    hurt.rot("body", [(0, 0, 0, 0), (0.05, 0, 30, 0), (0.2, 0, -10, 0), (0.35, 0, 0, 0)])
    death = r.clip("death", 1.0)
    death.rot("body", [(0, 0, 0, 0), (0.6, 0, 0, 170), (1.0, 0, 0, 180)])
    death.pos("body", [(0, 0, 0, 0), (1.0, 0, 3, 0)])


def paint_glimmerfin(r, P):
    back = Ramp("#13283c", "#1d3c56", "#2a5474", "#3a6e92")
    silver = Ramp("#5a7486", "#7c98aa", "#a2bccc", "#c8dce6", "#eaf6fa")
    fin = Ramp("#2a6a8a", "#3a8eb0", "#5cb8d4", "#9ae4f2")
    # dark back on top, silver flanks, a bright belly; one iridescent stripe along the side
    def flank(f, i, j):
        if f.name == "top":
            return back.at(2.4 - (0.6 if (i + j) % 3 == 0 else 0))
        if f.name == "bottom":
            return silver.at(4)
        if f.name in ("west", "east", "north", "south"):
            if j == 0:
                return back.at(2.6)
            if j == 1:
                return lerpc(hexc("#4ad8e8"), hexc("#b47cff"), (i % 4) / 3)
            return silver.at(3.6 - j * 0.4 - (0.6 if (i + j) % 4 == 3 else 0))
        return None
    for nm in ("gbody", "ghead", "gtail1", "gtail2"):
        for fname in ("top", "bottom") + SIDES:
            f = P.face(nm, fname)
            for i, j in f.each():
                col = flank(f, i, j)
                if col is not None:
                    f.set(i, j, col)
    P.paint("gsnout", silver, 2.8)
    # night: the iridescent stripe shines down both flanks and the scale row under it glimmers (the glow is the
    # stripe's own colour, so by day it is just the stripe)
    for nm in ("gbody", "ghead", "gtail1", "gtail2"):
        for fname in SIDES:
            f = P.face(nm, fname)
            g = P.face(nm, fname, glow=True)
            if f.h < 2:
                continue
            for i in range(f.w):
                t = i / max(1, f.w - 1)
                g.set(i, 1, f.get(i, 1)[:3] + (int(190 + 65 * t),))
                if nm in ("gbody", "gtail1") and f.h > 2:
                    g.set(i, 2, hexc("#b4f4ff", 210 if i % 2 == 0 else 160))
    # the eye: dark with a bright ring, glowing
    for fname in ("west", "east"):
        f = P.face("ghead", fname)
        g = P.face("ghead", fname, glow=True)
        ex = 0 if fname == "west" else f.w - 1
        f.set(ex, 1, hexc("#06121c"))
        g.set(ex, 1, hexc("#d8fcff", 245))
    # fins: shaped membranes with rays, translucent at the edges; the tail is forked
    fin_c = lambda i, j, edge: hexc("#9ae4f2", 230) if edge else (hexc("#5cb8d4", 210) if i % 2 == 0 else hexc("#3a8eb0", 170))  # noqa: E731
    def membrane(nm, keep):
        for fname in ("west", "east", "north", "south", "top", "bottom"):
            f = P.face(nm, fname)
            if f.w <= 0 or f.h <= 0:
                continue
            g = P.face(nm, fname, glow=True)
            for i, j in f.each():
                k = keep(f, i, j)
                f.set(i, j, (0, 0, 0, 0) if k is None else fin_c(i, j, k))
                if k is not None:
                    # both faces of every fin glimmer, the edges most
                    g.set(i, j, hexc("#b4f4ff", 230) if k else hexc("#8ee4f6", 150))
    # tail (west/east faces are 3 wide, 5 tall): a fork, deepest at the middle row
    membrane("gfin", lambda f, i, j: None if (j == 2 and i >= 1) or (j in (1, 3) and i == 2) else (i == f.w - 1 or j in (0, f.h - 1)))
    # dorsal (4 long, 2 tall): a swept sail, tallest at the front
    membrane("dorsal", lambda f, i, j: None if (j == 0 and (i >= 2 if f.name == "west" else i <= 1)) else (j == 0))
    membrane("pec_l", lambda f, i, j: None if (i, j) == (1, 1) else (j == 1))
    membrane("anal", lambda f, i, j: None if i == (2 if f.name == "west" else 0) else False)
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# Breathbell Jelly: a translucent bell with a bubble of air inside it, a frilled rim, trailing tentacles and two
# ribbon arms; the bell pulses as it swims and empties when it gives its air away

def breathbell():
    r = Rig("breathbell", (64, 64), translucent=True)
    r.walk_stride, r.run_stride, r.run_speed, r.shadow = 1.0, 2.0, (0.1, 0.2), 0.3
    bell = r.bone("bell", (0, 13, 0))
    # a rounded dome in four tiers: brim, dome, shoulder, cap
    bell.cube("brim", -5.5, 0, -5.5, 11, 1, 11)
    bell.cube("dome", -5, -3, -5, 10, 3, 10)
    bell.cube("shoulder", -4, -5, -4, 8, 2, 8)
    bell.cube("cap", -2.5, -6, -2.5, 5, 1, 5)
    bell.bone("air", (0, -2, 0)).cube("airbell", -3, -2, -3, 6, 4, 6)
    for k, (x, z, ry) in enumerate(((0, -5.5, 0), (5.5, 0, 90), (0, 5.5, 180), (-5.5, 0, 270))):
        bell.bone(f"skirt{k}", (x, 1, z), (0, ry, 0)).cube(f"skirt{k}", -5, 0, 0, 10, 2, 0, share=None if k == 0 else "skirt0")
    # eight tentacles: four long (three joints), four short, hanging from just inside the brim
    for k in range(8):
        a = k * math.tau / 8 + 0.2
        rr = 4.2 if k % 2 == 0 else 3.0
        x, z = round(rr * math.cos(a), 1), round(rr * math.sin(a), 1)
        t = bell.bone(f"tent{k}", (x, 1, z))
        t.cube(f"tent{k}a", -0.5, 0, -0.5, 1, 4, 1, share=None if k == 0 else "tent0a")
        t2 = t.bone(f"tent{k}b", (0, 4, 0))
        t2.cube(f"tent{k}b", -0.5, 0, -0.5, 1, 4, 1, share=None if k == 0 else "tent0b")
        if k % 2 == 0:
            t2.bone(f"tent{k}c", (0, 4, 0)).cube(f"tent{k}c", -0.5, 0, -0.5, 1, 4, 1, share=None if k == 0 else "tent0c")
    arms = bell.bone("arms", (0, 1, 0))
    arms.bone("arm0", (0, 0, 0), (0, 45, 0)).cube("arm0", -1.5, 0, 0, 3, 9, 0)
    arms.bone("arm1", (0, 0, 0), (0, -45, 0)).cube("arm1", -1.5, 0, 0, 3, 9, 0, share="arm0")
    r.look = []
    r.pack()
    breathbell_clips(r)
    return r


def breathbell_clips(r):
    idle = r.clip("idle", 2.4, loop=True)
    # the pulse: a sharp contraction, then the bell relaxes; the skirt flares, the tentacles trail and drift
    idle.scale("bell", [(0, 1, 1, 1), (0.35, 0.86, 1.14, 0.86), (0.9, 1.06, 0.94, 1.06), (2.4, 1, 1, 1)])
    idle.pos("bell", [(0, 0, 0, 0), (0.35, 0, 0.6, 0), (1.2, 0, 0.2, 0), (2.4, 0, 0, 0)])
    for k in range(4):
        idle.rot(f"skirt{k}", [(0, 0, 0, 0), (0.35, 28, 0, 0), (1.0, -4, 0, 0), (2.4, 0, 0, 0)])
    for k in range(8):
        cycle(idle, f"tent{k}", "rot", lambda p, k=k: (6 * s(p, k / 8), 0, 6 * c(p, k / 8)), 8)
        cycle(idle, f"tent{k}b", "rot", lambda p, k=k: (12 * s(p, k / 8 - 0.15), 0, 9 * c(p, k / 8 - 0.15)), 8)
        if k % 2 == 0:
            cycle(idle, f"tent{k}c", "rot", lambda p, k=k: (16 * s(p, k / 8 - 0.3), 0, 12 * c(p, k / 8 - 0.3)), 8)
    cycle(idle, "arms", "rot", lambda p: (0, 30 * s(p), 0), 8)
    fly = r.clip("fly", 1.2, loop=True)
    fly.scale("bell", [(0, 1, 1, 1), (0.2, 0.82, 1.18, 0.82), (0.6, 1.08, 0.92, 1.08), (1.2, 1, 1, 1)])
    for k in range(4):
        fly.rot(f"skirt{k}", [(0, 0, 0, 0), (0.2, 36, 0, 0), (0.6, -6, 0, 0), (1.2, 0, 0, 0)])
    for k in range(8):
        cycle(fly, f"tent{k}b", "rot", lambda p, k=k: (18 * s(p, k / 8), 0, 0), 8)
    deflate = r.clip("deflate", 1.0, loop=True)
    # the bell gave its air away: the bubble is a sliver, the dome sags a little
    deflate.scale("air", [(0, 0.25, 0.2, 0.25), (1.0, 0.25, 0.2, 0.25)])
    deflate.scale("bell", [(0, 1.04, 0.9, 1.04), (1.0, 1.04, 0.9, 1.04)])
    beached = r.clip("beached", 1.0, loop=True)
    beached.scale("bell", [(0, 1.3, 0.55, 1.3), (0.5, 1.34, 0.5, 1.34), (1.0, 1.3, 0.55, 1.3)])
    beached.pos("bell", [(0, 0, -8, 0), (1.0, 0, -8, 0)])
    for k in range(8):
        beached.rot(f"tent{k}", [(0, 80, k * 45, 0), (1.0, 80, k * 45, 0)])
    hurt = r.clip("hurt", 0.4)
    hurt.scale("bell", [(0, 1, 1, 1), (0.06, 0.75, 1.2, 0.75), (0.3, 1.05, 0.96, 1.05), (0.4, 1, 1, 1)])
    death = r.clip("death", 1.2)
    death.scale("bell", [(0, 1, 1, 1), (0.6, 1.2, 0.6, 1.2), (1.2, 1.25, 0.5, 1.25)])
    death.scale("air", [(0, 1, 1, 1), (0.4, 0.1, 0.1, 0.1)])
    death.pos("bell", [(0, 0, 0, 0), (1.2, 0, -4, 0)])
    for k in range(8):
        death.rot(f"tent{k}", [(0, 0, 0, 0), (1.2, 40, k * 45, 0)])


def paint_breathbell(r, P):
    gel = Ramp("#4a6a8a", "#6a92b4", "#8cb8d8", "#b4dcf0", "#dcf4ff")
    # the bell: milky glass, radial canals, brighter toward the brim, mostly see-through. At night the whole bell
    # glows softly, the canals and the frilled rim brightest (glow on both faces of the skirt, every face of the bell)
    for nm in ("brim", "dome", "shoulder", "cap"):
        for fname in ("top", "bottom") + SIDES:
            f = P.face(nm, fname)
            g = P.face(nm, fname, glow=True)
            for i, j in f.each():
                if fname == "top":
                    canal = (i == f.w // 2) or (j == f.h // 2) or (i == j) or (i == f.w - 1 - j)
                else:
                    canal = i % 3 == 1
                rim = nm == "brim"
                a = 200 if rim else (150 if canal else 90)
                f.set(i, j, gel.at((3.8 if canal or rim else 2.8) + (0.3 if nm == "cap" else 0))[:3] + (a,))
                if rim:
                    g.set(i, j, hexc("#c8f6ff", 245 if (fname in SIDES and i % 3 == 1) else 215))
                elif canal:
                    g.set(i, j, hexc("#a8ecff", 205))
                else:
                    g.set(i, j, hexc("#8cd4f4", 120))
    # the bubble of air: bright silver-blue with a highlight
    for fname in ("top", "bottom") + SIDES:
        f = P.face("airbell", fname)
        g = P.face("airbell", fname, glow=True)
        for i, j in f.each():
            hi = (i, j) in ((1, 1), (2, 1), (1, 2))
            f.set(i, j, hexc("#f4feff", 235) if hi else hexc("#c4ecff", 175))
            g.set(i, j, hexc("#ecfdff", 200 if not hi else 255))
    for fname in ("north", "south"):
        f = P.face("skirt0", fname)
        g = P.face("skirt0", fname, glow=True)
        for i, j in f.each():
            if j == f.h - 1 and i % 2 == 1:
                f.set(i, j, (0, 0, 0, 0))
                continue
            f.set(i, j, gel.at(3.6 - j * 0.6)[:3] + (150,))
            t = j / max(1, f.h - 1)
            g.set(i, j, hexc("#b8f0ff", int(190 + 65 * t)))
    # tentacles: faint at the root, bright beads toward the tips
    for nm in ("tent0a", "tent0b", "tent0c"):
        for fname in ("top", "bottom") + SIDES:
            f = P.face(nm, fname)
            g = P.face(nm, fname, glow=True)
            for i, j in f.each():
                fade = 130 if nm == "tent0a" else 105 if nm == "tent0b" else 80
                f.set(i, j, gel.at(3.4)[:3] + (fade,))
                base = 150 if nm == "tent0a" else 185 if nm == "tent0b" else 215
                g.set(i, j, hexc("#d8ffff", 255 if (j == f.h - 1 or j % 2 == 0) and nm != "tent0a" else base))
    # the ribbon arms: violet, frilled, glowing on both faces
    for fname in ("north", "south"):
        f = P.face("arm0", fname)
        g = P.face("arm0", fname, glow=True)
        for i, j in f.each():
            edge = i in (0, f.w - 1)
            if edge and (j % 2 == 1 or j > f.h - 3):
                f.set(i, j, (0, 0, 0, 0))
                continue
            f.set(i, j, hexc("#e6b8ff", 170) if edge else hexc("#b48cf0", 135))
            g.set(i, j, hexc("#ecd4ff", 235) if edge or j % 3 == 0 else hexc("#d0b4ff", 175))
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# Brook Otter: a long, low river otter with a thick tapering tail, webbed hind feet, a whiskered blunt muzzle; it
# bounds on land and swims with a long up-and-down wave of the whole body

def brook_otter():
    r = Rig("brook_otter", (64, 64))
    r.walk_stride, r.run_stride, r.run_speed, r.shadow = 1.0, 2.2, (0.14, 0.26), 0.45
    body = r.bone("body", (0, 18.5, 0))
    body.cube("torso", -2.5, -2.5, -5, 5, 5, 9)
    body.cube("belly", -2, 2.5, -4, 4, 0, 7)
    neck = body.bone("neck", (0, -0.5, -5), (-8, 0, 0))
    neck.cube("oneck", -2, -2, -2, 4, 4, 2)
    head = neck.bone("head", (0, -0.5, -2))
    head.cube("ohead", -2, -2.5, -3.5, 4, 4, 4)
    head.cube("muzzle", -1.5, -0.5, -5.5, 3, 2.0, 2)
    head.cube("nose", -0.5, -0.5, -6, 1, 1, 1)
    for side, nm in ((1, "l"), (-1, "r")):
        mir = side < 0

        def sh(n, side=side):
            return None if side > 0 else n
        head.bone("ear_" + nm, (side * 1.6, -2.5, -1)).cube("oear_" + nm, -0.5, -1, -0.5, 1, 1, 1, mirror=mir, share=sh("oear_l"))
        head.bone("whisk_" + nm, (side * 1.5, 0.5, -4.5), (0, side * -25, side * 8)).cube(
            "whisk_" + nm, 0 if side > 0 else -3, -1, 0, 3, 2, 0, mirror=mir, share=sh("whisk_l"))
        fl = body.bone("fleg_" + nm, (side * 1.8, 2, -3.5))
        fl.cube("ofleg_" + nm, -1, -0.5, -1, 2, 4, 2, mirror=mir, share=sh("ofleg_l"))
        hl = body.bone("hleg_" + nm, (side * 1.8, 2, 3))
        hl.cube("ohleg_" + nm, -1, -0.5, -1.5, 2, 4, 3, mirror=mir, share=sh("ohleg_l"))
        hl.bone("hfoot_" + nm, (0, 3.5, -1.5)).cube("hfoot_" + nm, -1.5, 0, -2, 3, 0, 3, mirror=mir, share=sh("hfoot_l"))
    jaw = head.bone("jaw", (0, 1.5, -3))
    jaw.cube("ojaw", -1.5, 0, -2.5, 3, 1, 2)
    t1 = body.bone("otail1", (0, -0.5, 4))
    t1.cube("otail1", -1.5, -1.5, 0, 3, 3, 4)
    t2 = t1.bone("otail2", (0, 0.5, 4))
    t2.cube("otail2", -1, -1, 0, 2, 2, 4)
    t3 = t2.bone("otail3", (0, 0, 4))
    t3.cube("otail3", -1, -0.5, 0, 2, 1, 3)
    r.look = [("neck", 0.4, 0.3), ("head", 0.6, 0.6)]
    r.moves = {"Pounce": "pounce"}
    r.pack()
    otter_clips(r)
    return r


def otter_clips(r):
    idle = r.clip("idle", 4.0, loop=True)
    # alert and busy: sniffs (nose up, whiskers), glances about, the tail sways
    idle.rot("head", [(0, 0, 0, 0), (0.6, -14, 0, 0), (0.9, -10, 0, 0), (1.2, -14, 0, 0), (1.6, 0, 0, 0), (2.4, 0, 30, 0), (3.0, 0, 30, 0),
                      (3.4, 0, 0, 0), (4.0, 0, 0, 0)])
    for nm, sg in (("whisk_l", 1), ("whisk_r", -1)):
        idle.rot(nm, [(0, 0, sg * -25, sg * 8), (0.7, 0, sg * -35, sg * 14), (0.85, 0, sg * -25, sg * 8), (1.0, 0, sg * -35, sg * 14),
                      (1.2, 0, sg * -25, sg * 8), (4.0, 0, sg * -25, sg * 8)])
    cycle(idle, "otail1", "rot", lambda p: (0, 8 * s(p), 0), 8)
    cycle(idle, "otail2", "rot", lambda p: (0, 10 * s(p, -0.1), 0), 8)
    cycle(idle, "body", "scale", lambda p: (1 + 0.02 * s(p * 2), 1 + 0.03 * s(p * 2), 1), 8)
    walk = r.clip("walk", 0.7, loop=True)
    # the bound: forelegs together, hind legs together, the back arching between
    for nm in ("fleg_l", "fleg_r"):
        cycle(walk, nm, "rot", lambda p: (40 * s(p), 0, 0), 8)
    for nm in ("hleg_l", "hleg_r"):
        cycle(walk, nm, "rot", lambda p: (40 * s(p, 0.5), 0, 0), 8)
    cycle(walk, "body", "rot", lambda p: (6 * s(p, 0.25), 0, 0), 8)
    cycle(walk, "body", "pos", lambda p: (0, 1.0 * abs(s(p)), 0), 8)
    cycle(walk, "neck", "rot", lambda p: (-6 * s(p, 0.25), 0, 0), 8)
    cycle(walk, "otail1", "rot", lambda p: (-10 * s(p, 0.4), 0, 0), 8)
    cycle(walk, "otail2", "rot", lambda p: (-12 * s(p, 0.3), 0, 0), 8)
    run = r.clip("run", 0.5, loop=True)
    for nm in ("fleg_l", "fleg_r"):
        cycle(run, nm, "rot", lambda p: (60 * s(p), 0, 0), 8)
    for nm in ("hleg_l", "hleg_r"):
        cycle(run, nm, "rot", lambda p: (60 * s(p, 0.5), 0, 0), 8)
    cycle(run, "body", "rot", lambda p: (12 * s(p, 0.25), 0, 0), 8)
    cycle(run, "body", "pos", lambda p: (0, 2.0 * abs(s(p)), 0), 8)
    cycle(run, "otail1", "rot", lambda p: (-16 * s(p, 0.4), 0, 0), 8)
    fly = r.clip("fly", 0.9, loop=True)
    # the swim: legs tucked back, the whole body a wave from nose to tail tip, up and down
    for nm in ("fleg_l", "fleg_r"):
        fly.rot(nm, [(0, 70, 0, 0), (0.9, 70, 0, 0)])
    for nm in ("hleg_l", "hleg_r"):
        cycle(fly, nm, "rot", lambda p: (70 + 20 * s(p), 0, 0), 8)
    wave(fly, ["neck", "body", "otail1", "otail2", "otail3"], "x", 0, lag=0.12, amps=[6, 4, 14, 18, 24])
    carry = r.clip("carry", 1.0, loop=True)
    # bringing a find: head held high and proud, jaw clamped
    carry.rot("neck", [(0, -24, 0, 0), (1.0, -24, 0, 0)])
    carry.rot("head", [(0, 12, 0, 0), (1.0, 12, 0, 0)])
    pw = r.clip("pounce_windup", 0.5)
    pw.rot("body", [(0, 0, 0, 0), (0.5, 8, 0, 0)])
    pw.pos("body", [(0, 0, 0, 0), (0.5, 0, -1.5, 1)])
    for nm in ("hleg_l", "hleg_r"):
        pw.rot(nm, [(0, 0, 0, 0), (0.5, -30, 0, 0)])
    pw.rot("jaw", [(0, 0, 0, 0), (0.5, 20, 0, 0)])
    ps = r.clip("pounce_strike", 0.6)
    ps.rot("body", [(0, 8, 0, 0), (0.1, -12, 0, 0), (0.6, 0, 0, 0)])
    ps.pos("body", [(0, 0, -1.5, 1), (0.1, 0, 1, -2), (0.6, 0, 0, 0)])
    for nm in ("fleg_l", "fleg_r"):
        ps.rot(nm, [(0, 0, 0, 0), (0.1, -60, 0, 0), (0.6, 0, 0, 0)])
    ps.rot("jaw", [(0, 20, 0, 0), (0.12, 0, 0, 0)])
    w = r.clip("windup", 0.5)
    w.rot("head", [(0, 0, 0, 0), (0.5, -10, 0, 0)])
    w.rot("jaw", [(0, 0, 0, 0), (0.5, 22, 0, 0)])
    st = r.clip("strike", 0.4)
    st.rot("head", [(0, -10, 0, 0), (0.08, 14, 0, 0), (0.4, 0, 0, 0)])
    st.rot("jaw", [(0, 22, 0, 0), (0.1, 0, 0, 0)])
    bite = r.clip("bite", 0.4)
    bite.rot("jaw", [(0, 0, 0, 0), (0.08, 28, 0, 0), (0.2, 0, 0, 0)])
    bite.rot("neck", [(0, -8, 0, 0), (0.1, 6, 0, 0), (0.4, -8, 0, 0)])
    hurt = r.clip("hurt", 0.4)
    hurt.rot("body", [(0, 0, 0, 0), (0.06, -6, 14, 0), (0.25, 2, -4, 0), (0.4, 0, 0, 0)])
    hurt.rot("head", [(0, 0, 0, 0), (0.06, -20, 0, 0), (0.4, 0, 0, 0)])
    death = r.clip("death", 1.2)
    death.rot("body", [(0, 0, 0, 0), (0.4, 0, 0, 30), (1.0, 0, 0, 175), (1.2, 0, 0, 180)])
    death.pos("body", [(0, 0, 0, 0), (1.2, 0, -3, 0)])
    for nm in ("fleg_l", "fleg_r", "hleg_l", "hleg_r"):
        death.rot(nm, [(0, 0, 0, 0), (1.2, -40, 0, 0)])


def paint_brook_otter(r, P):
    fur = Ramp("#2a1a10", "#3c2616", "#52341e", "#6a4628", "#845a34", "#9e7044")
    cream = Ramp("#8a7458", "#a88e6c", "#c6ab86", "#dcc6a0", "#efe0c0")
    dark = Ramp("#120c08", "#1e140e", "#2c1e14")
    sleek = combine(p_strokes(cell=3, length=3, dark=0.5, light=0.35, density=0.55, seed=3), p_blotch("otter", 5, 0.3))
    for nm in ("torso", "oneck", "ohead", "otail1", "otail2", "otail3", "ofleg_l", "ohleg_l"):
        P.paint(nm, fur, 3.4 if nm not in ("otail3",) else 2.8, sleek)
    # the cream throat and chest, wet-dark belly
    for nm in ("oneck", "ohead"):
        f = P.face(nm, "bottom")
        for i, j in f.each():
            f.set(i, j, cream.at(3.0 - (0.5 if (i + j) % 4 == 0 else 0)))
        if nm == "oneck":
            f = P.face(nm, "north")
            for i, j in f.each():
                f.set(i, j, cream.at(2.8 - (0.5 if (i + j) % 4 == 0 else 0)))
    # a brown face, the muzzle a shade lighter, a pale chin
    P.paint("muzzle", fur, 4.4, lambda f, i, j: (-0.6 if f.name == "north" and j == f.h - 1 else 0) + (0.8 if f.name == "bottom" else 0))
    P.paint("ojaw", cream, 2.6)
    P.paint("nose", dark, 1.6, lambda f, i, j: 0.8 if f.name == "top" else 0)
    for fname in ("top", "bottom"):
        f = P.face("belly", fname)
        for i, j in f.each():
            f.set(i, j, fur.at(2.0 - (0.4 if (i + j) % 3 == 0 else 0)))
    P.paint("oear_l", fur, 2.4)
    for fname in ("top", "bottom"):
        f = P.face("hfoot_l", fname)
        for i, j in f.each():
            webbing = j > 0 and i % 2 == 1
            f.set(i, j, hexc("#4a3424", 200) if webbing else dark.at(1.6))
    for fname in ("north", "south"):
        f = P.face("whisk_l", fname)
        clear(f)
        for i in range(f.w):
            f.set(i, 0 if i % 2 == 0 else 1, hexc("#f4ecdc", 220))
    # eyes: bright, dark, alert; a faint amber shine in the glow layer
    with P.eyes():
        for fname in ("west", "east"):
            f = P.face("ohead", fname)
            g = P.face("ohead", fname, glow=True)
            ex = 0 if fname == "west" else f.w - 1
            f.set(ex, 1, hexc("#0a0604"))
            g.set(ex, 1, hexc("#ffd27a", 150))
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# Tidesteed: a sea horse in the old sense: a horse's head and forequarters on a dolphin's tail, a mane of kelp, fins
# for hooves, glowing spots along its flanks. It swims with an up-and-down beat of its fluke; bridled, it wears a
# headstall and a saddle pad of fauna leather (bones named bridle*).

def tidesteed():
    r = Rig("tidesteed", (128, 64))
    r.walk_stride, r.run_stride, r.run_speed, r.shadow = 2.2, 4.0, (0.15, 0.3), 0.9
    body = r.bone("body", (0, 12, 0))
    body.cube("chest", -4.5, -4, -7, 9, 8, 9)
    body.cube("barrel", -4, -3.5, 2, 8, 7, 6)
    body.bone("dorsal", (0, -4.5, -1), (8, 0, 0)).cube("tdorsal", 0, -4, 0, 0, 4, 8)
    neck = body.bone("neck", (0, -3, -6), (18, 0, 0))
    neck.cube("tneck", -2.5, -11, -3, 5, 11, 6)
    for k in range(3):
        neck.bone(f"mane{k}", (0, -10 + k * 3.5, 3), (20, 0, 0)).cube(f"mane{k}", 0, 0, 0, 0, 6, 5, share=None if k == 0 else "mane0")
    head = neck.bone("head", (0, -11, 0), (40, 0, 0))
    head.cube("skull", -3, -4, -4, 6, 6, 7)
    head.cube("tmuzzle", -2, -2, -10, 4, 4, 6)
    head.bone("forelock", (0, -4, -2), (-20, 0, 0)).cube("forelock", 0, -5, 0, 0, 5, 4)
    for side, nm in ((1, "l"), (-1, "r")):
        head.bone("fin_ear_" + nm, (side * 2.5, -4, 0), (-20, side * -20, side * 30)).cube(
            "fin_ear_" + nm, 0, -4, 0, 0, 4, 3, mirror=side < 0, share=None if side > 0 else "fin_ear_l")
    jaw = head.bone("tjaw", (0, 2, -5))
    jaw.cube("tjaw", -1.5, 0, -5, 3, 1, 5)
    # the bridle: a headstall round the muzzle and the brow, and a saddle pad on the back
    head.bone("bridle_nose", (0, 0, -8)).cube("bridle_nose", -2, -2, 0, 4, 4, 1, inflate=0.3)
    head.bone("bridle_brow", (0, -1, -1)).cube("bridle_brow", -3, -3, 0, 6, 6, 1, inflate=0.3)
    body.bone("bridle_saddle", (0, -5, -3)).cube("bridle_saddle", -4, -0.5, -3, 8, 1, 7, inflate=0.2)
    for side, nm in ((1, "l"), (-1, "r")):
        mir = side < 0

        def sh(n, side=side):
            return None if side > 0 else n
        leg = body.bone("tleg_" + nm, (side * 3.2, 3, -5), (-30, 0, 0))
        leg.cube("tleg_" + nm, -1.5, 0, -1.5, 3, 6, 3, mirror=mir, share=sh("tleg_l"))
        shin = leg.bone("tshin_" + nm, (0, 5.5, 0), (55, 0, 0))
        shin.cube("tshin_" + nm, -1, 0, -1, 2, 4, 2, mirror=mir, share=sh("tshin_l"))
        shin.bone("thoof_" + nm, (0, 4, 0), (20, 0, 0)).cube("thoof_" + nm, -2, 0, -3, 4, 0, 4, mirror=mir, share=sh("thoof_l"))
        body.bone("pec_" + nm, (side * 4.5, 3, 1), (0, 0, side * -30)).cube("tpec_" + nm, 0 if side > 0 else -6, 0, -2, 6, 0, 4, mirror=mir, share=sh("tpec_l"))
    t1 = body.bone("ttail1", (0, 0, 8))
    t1.cube("ttail1", -3.5, -3.5, 0, 7, 7, 7)
    t2 = t1.bone("ttail2", (0, 0.5, 7))
    t2.cube("ttail2", -2.5, -2.5, 0, 5, 5, 6)
    t3 = t2.bone("ttail3", (0, 0.5, 6))
    t3.cube("ttail3", -1.5, -1.5, 0, 3, 3, 6)
    t4 = t3.bone("ttail4", (0, 0, 6))
    t4.cube("ttail4", -1, -1, 0, 2, 2, 4)
    t4.bone("fluke", (0, 0, 4)).cube("fluke", -6, 0, -1, 12, 0, 6)
    r.look = [("neck", 0.3, 0.2), ("head", 0.5, 0.5)]
    r.moves = {"Tail Lash": "lash", "Wave Surge": "surge"}
    r.pack()
    tidesteed_clips(r)
    return r


def tidesteed_clips(r):
    idle = r.clip("idle", 3.0, loop=True)
    # treading water: a slow fluke beat, the head bobs, the kelp mane drifts
    wave(idle, ["ttail1", "ttail2", "ttail3", "ttail4", "fluke"], "x", 0, lag=0.12, amps=[3, 5, 7, 10, 14])
    cycle(idle, "neck", "rot", lambda p: (3 * s(p), 0, 0), 8)
    cycle(idle, "head", "rot", lambda p: (-3 * s(p, 0.1), 0, 0), 8)
    for k in range(3):
        cycle(idle, f"mane{k}", "rot", lambda p, k=k: (10 * s(p, k * 0.2), 0, 8 * s(p, 0.3 + k * 0.2)), 8)
    for nm, sg in (("tleg_l", 1), ("tleg_r", -1)):
        cycle(idle, nm, "rot", lambda p, sg=sg: (12 * s(p, 0.25 if sg > 0 else 0.75), 0, 0), 8)
    for nm, sg in (("pec_l", 1), ("pec_r", -1)):
        cycle(idle, nm, "rot", lambda p, sg=sg: (0, 0, sg * (-30 + 10 * s(p))), 8)
    fly = r.clip("fly", 1.0, loop=True)
    # the swim: the fluke drives, the wave runs from the barrel to the fluke, the forelegs paddle, the mane streams
    wave(fly, ["body", "ttail1", "ttail2", "ttail3", "ttail4", "fluke"], "x", 0, lag=0.11, amps=[3, 8, 12, 16, 20, 26])
    for nm, ph in (("tleg_l", 0.0), ("tleg_r", 0.5)):
        cycle(fly, nm, "rot", lambda p, ph=ph: (-10 + 30 * s(p, ph), 0, 0), 8)
        cycle(fly, nm.replace("tleg", "tshin"), "rot", lambda p, ph=ph: (30 * max(0.0, c(p, ph)), 0, 0), 8)
    cycle(fly, "neck", "rot", lambda p: (-8 + 4 * s(p, 0.1), 0, 0), 8)
    for k in range(3):
        cycle(fly, f"mane{k}", "rot", lambda p, k=k: (30 + 8 * s(p * 2, k * 0.2), 0, 0), 8)
    walk = r.clip("walk", 1.2, loop=True)
    # ashore: the forelegs haul, the tail drags with a side sway
    for nm, ph in (("tleg_l", 0.0), ("tleg_r", 0.5)):
        cycle(walk, nm, "rot", lambda p, ph=ph: (30 + 30 * s(p, ph), 0, 0), 8)
        cycle(walk, nm.replace("tleg", "tshin"), "rot", lambda p, ph=ph: (-50 + 25 * max(0.0, c(p, ph)), 0, 0), 8)
    wave(walk, ["ttail1", "ttail2", "ttail3", "ttail4"], "y", 0, lag=0.15, amps=[6, 10, 14, 18])
    cycle(walk, "body", "pos", lambda p: (0, 0.8 * abs(s(p)), 0), 8)
    cycle(walk, "neck", "rot", lambda p: (6 * s(p * 2), 0, 0), 8)
    ridden = r.clip("ridden", 1.0, loop=True)
    ridden.rot("neck", [(0, -10, 0, 0), (1.0, -10, 0, 0)])
    ridden.rot("head", [(0, 8, 0, 0), (1.0, 8, 0, 0)])
    rest = r.clip("rest", 4.0, loop=True)
    # resting: settled low, the head lowered
    rest.pos("body", [(0, 0, -3, 0), (4.0, 0, -3, 0)])
    rest.rot("neck", [(0, 14, 0, 0), (2.0, 16, 0, 0), (4.0, 14, 0, 0)])
    rest.rot("head", [(0, 14, 0, 0), (4.0, 14, 0, 0)])
    # Tail Lash: it curls, the fluke cocked high over the back, then whips through
    lw = r.clip("lash_windup", 0.8)
    for nm, a in (("ttail1", -14), ("ttail2", -22), ("ttail3", -30), ("ttail4", -34), ("fluke", -30)):
        lw.rot(nm, [(0, 0, 0, 0), (0.8, a, 18, 0)])
    lw.rot("body", [(0, 0, 0, 0), (0.8, 0, -18, 0)])
    ls = r.clip("lash_strike", 0.7)
    for nm, a in (("ttail1", 14), ("ttail2", 22), ("ttail3", 30), ("ttail4", 34), ("fluke", 30)):
        ls.rot(nm, [(0, -a, 18, 0), (0.12, a * 0.6, -40, 0), (0.7, 0, 0, 0)])
    ls.rot("body", [(0, 0, -18, 0), (0.12, 0, 26, 0), (0.7, 0, 0, 0)])
    # Wave Surge: it rears, forelegs up, then crashes forward
    sw = r.clip("surge_windup", 1.0)
    sw.rot("body", [(0, 0, 0, 0), (1.0, -32, 0, 0)])
    sw.rot("neck", [(0, 0, 0, 0), (1.0, -20, 0, 0)])
    for nm in ("tleg_l", "tleg_r"):
        sw.rot(nm, [(0, 0, 0, 0), (1.0, -70, 0, 0)])
    ss = r.clip("surge_strike", 0.8)
    ss.rot("body", [(0, -32, 0, 0), (0.15, 18, 0, 0), (0.8, 0, 0, 0)])
    ss.pos("body", [(0, 0, 0, 0), (0.15, 0, -2, -2), (0.8, 0, 0, 0)])
    for nm in ("tleg_l", "tleg_r"):
        ss.rot(nm, [(0, -70, 0, 0), (0.15, 20, 0, 0), (0.8, 0, 0, 0)])
    w = r.clip("windup", 0.6)
    w.rot("neck", [(0, 0, 0, 0), (0.6, -12, 0, 0)])
    w.rot("tjaw", [(0, 0, 0, 0), (0.6, 20, 0, 0)])
    st = r.clip("strike", 0.4)
    st.rot("neck", [(0, -12, 0, 0), (0.08, 16, 0, 0), (0.4, 0, 0, 0)])
    st.rot("tjaw", [(0, 20, 0, 0), (0.1, 0, 0, 0)])
    bite = r.clip("bite", 0.4)
    bite.rot("tjaw", [(0, 0, 0, 0), (0.08, 26, 0, 0), (0.2, 0, 0, 0)])
    bite.rot("neck", [(0, 0, 0, 0), (0.1, 14, 0, 0), (0.4, 0, 0, 0)])
    hurt = r.clip("hurt", 0.45)
    hurt.rot("body", [(0, 0, 0, 0), (0.06, -8, 0, 10), (0.25, 3, 0, -4), (0.45, 0, 0, 0)])
    hurt.rot("neck", [(0, 0, 0, 0), (0.06, -18, 0, 0), (0.45, 0, 0, 0)])
    death = r.clip("death", 1.6)
    death.rot("body", [(0, 0, 0, 0), (0.4, -10, 0, 20), (1.2, 0, 0, 90), (1.6, 0, 0, 92)])
    death.pos("body", [(0, 0, 0, 0), (1.6, 0, -6, 0)])
    death.rot("neck", [(0, 0, 0, 0), (1.6, 30, 0, 0)])


def paint_tidesteed(r, P):
    hide = Ramp("#0c2a30", "#123a42", "#1a4e58", "#24646e", "#307c86", "#3e949c", "#56acb0")
    pale = Ramp("#5c8c8c", "#78a8a4", "#96c4bc", "#b4dcd2", "#d2f0e6")
    kelp = Ramp("#1c2a0c", "#2a3c12", "#3a5218", "#4c6a20", "#62862a", "#7c9e36")
    leather = Ramp("#2a160c", "#3e2414", "#5a361e", "#764a2a", "#94603a")
    gold = Ramp("#5a4214", "#8a6a20", "#c09a34", "#e8c860")
    sleek = combine(p_blotch("steed", 6, 0.35), lambda f, i, j: 0.35 if f.name in SIDES and j == 1 else 0)
    for nm in ("chest", "barrel", "tneck", "skull", "tmuzzle", "tleg_l", "tshin_l"):
        P.paint(nm, hide, 3.6 if nm in ("skull", "tmuzzle") else 3.3, sleek)
    for nm in ("ttail1", "ttail2", "ttail3", "ttail4"):
        P.paint(nm, hide, 3.2, combine(p_scales(3, 2, 0.7), p_blotch("tail" + nm, 5, 0.3)))
    # a pale belly and throat running down to the tail
    for nm in ("chest", "barrel", "ttail1", "ttail2", "ttail3", "tneck"):
        f = P.face(nm, "bottom" if nm != "tneck" else "north")
        for i, j in f.each():
            f.set(i, j, pale.at(2.8 - (0.5 if (i + j) % 4 == 0 else 0)))
    P.paint("tjaw", pale, 2.6)
    # glowing spots along the flanks and the tail, like plankton in a wake
    for nm, n in (("chest", 6), ("barrel", 5), ("ttail1", 4), ("ttail2", 3), ("ttail3", 2)):
        for fname in ("west", "east"):
            f = P.face(nm, fname)
            g = P.face(nm, fname, glow=True)
            for k in range(n):
                i = int((k + 0.5) * f.w / n)
                j = 2 + (k % 2)
                f.set(i, j, hexc("#9ff6ff"))
                g.set(i, j, hexc("#9ff6ff", 230))
    # eyes: dark and large, a sea-green shine
    with P.eyes():
        for fname in ("west", "east"):
            f = P.face("skull", fname)
            g = P.face("skull", fname, glow=True)
            for ex in (2, 3):
                f.set(ex if fname == "west" else f.w - 1 - ex, 2, hexc("#04100c"))
            g.set(2 if fname == "west" else f.w - 3, 2, hexc("#7fffe0", 200))
    # the kelp mane and forelock: ragged olive fronds with bladders
    for nm in ("mane0", "forelock"):
        for fname in ("west", "east"):
            f = P.face(nm, fname)
            for i, j in f.each():
                ragged = (i + j * 2) % 5 == 4 or (j == f.h - 1 and i % 2 == 0)
                if ragged:
                    f.set(i, j, (0, 0, 0, 0))
                else:
                    f.set(i, j, kelp.at(3.4 - j * 0.3 + (0.8 if (i * 3 + j) % 7 == 0 else 0)))
    # fins: membranes with rays, glowing tips
    for nm in ("tdorsal", "fin_ear_l", "fluke", "tpec_l", "thoof_l"):
        for fname in ("west", "east", "top", "bottom"):
            f = P.face(nm, fname)
            if f.w <= 0 or f.h <= 0:
                continue
            g = P.face(nm, fname, glow=True)
            for i, j in f.each():
                edge = (j == 0 and fname in ("west", "east")) or (j == f.h - 1 and fname in ("top", "bottom"))
                notch = nm == "fluke" and fname in ("top", "bottom") and j >= f.h - 2 and abs(i - f.w / 2 + 0.5) < 1.2
                if notch:
                    f.set(i, j, (0, 0, 0, 0))
                    continue
                f.set(i, j, hide.at(4.6 if i % 2 == 0 else 3.8)[:3] + (220,))
                if edge or (nm == "fluke" and (i in (0, f.w - 1))):
                    g.set(i, j, hexc("#7fe8ff", 190))
    # the bridle: dark fauna leather with gold fittings
    for nm in ("bridle_nose", "bridle_brow", "bridle_saddle"):
        P.paint(nm, leather, 2.8, lambda f, i, j: 1.2 if (i + j) % 4 == 0 and f.name != "bottom" else 0)
        for fname in ("west", "east"):
            f = P.face(nm, fname)
            f.set(f.w // 2, f.h // 2, gold.at(3))
    f = P.face("bridle_saddle", "top")
    for i, j in f.each():
        if i in (0, f.w - 1) or j in (0, f.h - 1):
            f.set(i, j, gold.at(2.4))
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# The Lantern Maw: an anglerfish as big as a boat: a vast head that is mostly jaw (an underbite of glass teeth), small
# pale eyes, a lure on a jointed rod over its brow, ragged fins, a short body and a tail. In the black water you see
# only the lure; when it lunges, the jaws open behind it. (Clip values are offsets from the rest pose.)

def lantern_maw():
    r = Rig("lantern_maw", (256, 128))
    r.walk_stride, r.run_stride, r.run_speed, r.shadow = 3.0, 6.0, (0.2, 0.4), 1.6
    # a short body behind a vast head: the head is the animal
    body = r.bone("body", (0, 12, 6))
    body.cube("trunk", -9, -10, -1, 18, 15, 13)
    head = body.bone("mhead", (0, -1, -1))
    head.cube("mskull", -12, -11, -20, 24, 11, 20)
    head.cube("crown", -9, -14, -17, 18, 3, 14)
    head.cube("lip", -11, -3, -21, 22, 3, 1)
    for side, nm in ((1, "l"), (-1, "r")):
        # small pale eyes high on the head, bulging out of the skull
        head.bone("meye_" + nm, (side * 12, -8, -15)).cube("meye_" + nm, -1, -1.5, -1.5, 2, 3, 3, mirror=side < 0, share=None if side > 0 else "meye_l")
        head.bone("gill_" + nm, (side * 12, -4, -3), (0, side * -20, 0)).cube(
            "gill_" + nm, 0, -4, 0, 0, 10, 7, mirror=side < 0, share=None if side > 0 else "gill_l")
    # the upper teeth: a row of glass needles hanging under the lip, more along the sides, two long fangs in front
    head.bone("teeth_up", (0, 0, -19.5)).cube("teeth_up", -11, 0, 0, 22, 4, 0)
    for side, nm in ((1, "l"), (-1, "r")):
        head.bone("teeth_upside_" + nm, (side * 11.5, 0, -10)).cube("teeth_upside_" + nm, 0, 0, -9, 0, 3, 10, mirror=side < 0,
                                                                     share=None if side > 0 else "teeth_upside_l")
        head.bone("fang_up_" + nm, (side * 6, 0, -19.5), (14, 0, 0)).cube("fang_up_" + nm, -0.5, 0, -0.5, 1, 7, 1, share=None if side > 0 else "fang_up_l")
    # the lower jaw: deep, wider and longer than the head (an underbite), hanging a little open at rest
    jaw = head.bone("mjaw", (0, 0, -1), (12, 0, 0))
    jaw.cube("mjaw", -13, 0, -23, 26, 9, 23)
    jaw.cube("chin", -10, 9, -22, 20, 3, 13)
    jaw.bone("teeth_lo", (0, 0, -22.5)).cube("teeth_lo", -12, -6, 0, 24, 6, 0)
    for side, nm in ((1, "l"), (-1, "r")):
        jaw.bone("teeth_loside_" + nm, (side * 12.5, 0, -11)).cube("teeth_loside_" + nm, 0, -4, -11, 0, 4, 12, mirror=side < 0,
                                                                   share=None if side > 0 else "teeth_loside_l")
    for k, (x, sp) in enumerate(((-9, -10), (-4, -4), (4, 4), (9, 10))):
        jaw.bone(f"fang_lo{k}", (x, 0, -22), (-14, 0, sp)).cube(f"fang_lo{k}", -0.5, -8, -0.5, 1, 8, 1, share=None if k == 0 else "fang_lo0")
    # the lure (the illicium): four joints rising from the crown and arching forward over the mouth, a blazing bulb
    # at the end with two filaments, and a halo of light round it (three crossed planes, glow only)
    rod = head.bone("rod1", (0, -14, -13), (10, 0, 0))
    rod.cube("rod1", -0.5, -7, -0.5, 1, 7, 1)
    rod2 = rod.bone("rod2", (0, -7, 0), (35, 0, 0))
    rod2.cube("rod2", -0.5, -7, -0.5, 1, 7, 1, share="rod1")
    rod3 = rod2.bone("rod3", (0, -7, 0), (40, 0, 0))
    rod3.cube("rod3", -0.5, -7, -0.5, 1, 7, 1, share="rod1")
    rod4 = rod3.bone("rod4", (0, -7, 0), (45, 0, 0))
    rod4.cube("rod4", -0.5, -6, -0.5, 1, 6, 1)
    esca = rod4.bone("esca", (0, -8, 0), (-130, 0, 0))
    esca.cube("esca", -2.5, -2.5, -2.5, 5, 5, 5)
    esca.cube("escacap", -1.5, -3.5, -1.5, 3, 1, 3)
    esca.bone("filament0", (1.5, 2.5, 0), (0, 0, -15)).cube("filament0", 0, 0, -0.5, 0, 6, 1)
    esca.bone("filament1", (-1.5, 2.5, 0), (0, 0, 15)).cube("filament1", 0, 0, -0.5, 0, 6, 1, share="filament0")
    halo = esca.bone("halo", (0, 0, 0))
    halo.cube("halo_xy", -7, -7, 0, 14, 14, 0)
    halo.cube("halo_zy", 0, -7, -7, 0, 14, 14)
    halo.cube("halo_xz", -7, 0, -7, 14, 0, 14)
    # ragged pectoral fans, a torn dorsal, a fan of a tail in three lobes
    for side, nm in ((1, "l"), (-1, "r")):
        body.bone("mpec_" + nm, (side * 9, 1, 1), (0, side * -35, side * -20)).cube(
            "mpec_" + nm, 0, -3, 0, 0, 11, 13, mirror=side < 0, share=None if side > 0 else "mpec_l")
    body.bone("mdorsal", (0, -10, 1), (-12, 0, 0)).cube("mdorsal", 0, -7, 0, 0, 7, 11)
    t1 = body.bone("mtail1", (0, -2, 12))
    t1.cube("mtail1", -6, -6, 0, 12, 11, 8)
    t1.bone("manal", (0, 5, 2), (25, 0, 0)).cube("manal", 0, 0, 0, 0, 5, 8)
    t2 = t1.bone("mtail2", (0, 0, 8))
    t2.cube("mtail2", -3.5, -4, 0, 7, 7, 6)
    caudal = t2.bone("caudal", (0, -0.5, 6))
    caudal.bone("caudal_u", (0, -1, 0), (-30, 0, 0)).cube("caudal_u", 0, -4, 0, 0, 7, 13)
    caudal.bone("caudal_m", (0, 0, 0)).cube("caudal_m", 0, -3, 0, 0, 6, 11)
    caudal.bone("caudal_d", (0, 1, 0), (30, 0, 0)).cube("caudal_d", 0, -3, 0, 0, 7, 13)
    r.look = [("mhead", 0.25, 0.25), ("rod1", 0.3, 0.3)]
    r.moves = {"Maw Lunge": "lunge", "Lure": "lure", "Black Water": "ink", "Abyss Glare": "glare", "Deep Hunger": "roar"}
    r.pack()
    maw_clips(r)
    return r


def maw_clips(r):
    idle = r.clip("idle", 4.0, loop=True)
    # it hangs in the dark: the lure sways and bobs (the joints out of step), the bulb breathes, the jaw opens and
    # closes slowly like a sleeper's breath, the gill flaps pulse, the fins scull, the tail sweeps
    cycle(idle, "rod1", "rot", lambda p: (5 * s(p), 0, 6 * s(p, 0.25)), 8)
    cycle(idle, "rod2", "rot", lambda p: (7 * s(p, 0.15), 0, 7 * s(p, 0.4)), 8)
    cycle(idle, "rod3", "rot", lambda p: (8 * s(p, 0.3), 0, 6 * s(p, 0.55)), 8)
    cycle(idle, "rod4", "rot", lambda p: (10 * s(p, 0.45), 0, 4 * s(p, 0.7)), 8)
    cycle(idle, "esca", "scale", lambda p: (1 + 0.12 * s(p * 2), 1 + 0.12 * s(p * 2), 1 + 0.12 * s(p * 2)), 8)
    cycle(idle, "halo", "scale", lambda p: (1 + 0.2 * s(p * 2, 0.1), 1 + 0.2 * s(p * 2, 0.1), 1 + 0.2 * s(p * 2, 0.1)), 8)
    cycle(idle, "filament0", "rot", lambda p: (12 * s(p, 0.2), 0, 8 * s(p * 2)), 8)
    cycle(idle, "filament1", "rot", lambda p: (12 * s(p, 0.5), 0, -8 * s(p * 2, 0.3)), 8)
    cycle(idle, "mjaw", "rot", lambda p: (5 * s(p), 0, 0), 8)
    for nm, sg in (("mpec_l", 1), ("mpec_r", -1)):
        cycle(idle, nm, "rot", lambda p, sg=sg: (0, sg * 14 * s(p * 2), sg * 6 * s(p * 2, 0.25)), 8)
    for nm, sg in (("gill_l", 1), ("gill_r", -1)):
        cycle(idle, nm, "rot", lambda p, sg=sg: (0, sg * -14 * max(0.0, s(p)), 0), 8)
    cycle(idle, "mdorsal", "rot", lambda p: (4 * s(p, 0.3), 0, 0), 8)
    wave(idle, ["mtail1", "mtail2", "caudal"], "y", 0, lag=0.15, amps=[4, 8, 14])
    fly = r.clip("fly", 2.0, loop=True)
    wave(fly, ["body", "mtail1", "mtail2", "caudal"], "y", 0, lag=0.14, amps=[3, 10, 18, 26])
    cycle(fly, "mhead", "rot", lambda p: (0, -3 * s(p), 0), 8)
    cycle(fly, "rod1", "rot", lambda p: (-6 + 4 * s(p * 2), 0, 0), 8)
    cycle(fly, "rod3", "rot", lambda p: (6 * s(p * 2, 0.3), 0, 0), 8)
    for nm, sg in (("mpec_l", 1), ("mpec_r", -1)):
        cycle(fly, nm, "rot", lambda p, sg=sg: (0, sg * (-10 + 22 * s(p * 2)), 0), 8)
    beached = r.clip("beached", 1.4, loop=True)
    beached.rot("body", [(0, 0, 0, 70), (1.4, 0, 0, 70)])
    cycle(beached, "mtail1", "rot", lambda p: (0, 24 * s(p), 0), 6)
    cycle(beached, "mjaw", "rot", lambda p: (8 + 14 * max(0.0, s(p)), 0, 0), 6)
    beached.scale("halo", [(0, 0.5, 0.5, 0.5), (1.4, 0.5, 0.5, 0.5)])
    # Maw Lunge (signature): it draws back, the lure lifts out of the way and flares, the jaws gape... then it lunges
    lw = r.clip("lunge_windup", 1.2)
    lw.pos("body", [(0, 0, 0, 0), (1.2, 0, 0, 4)])
    lw.rot("body", [(0, 0, 0, 0), (1.2, -8, 0, 0)])
    lw.rot("mjaw", [(0, 0, 0, 0), (0.5, 16, 0, 0), (1.2, 40, 0, 0)])
    lw.rot("mhead", [(0, 0, 0, 0), (1.2, -14, 0, 0)])
    lw.rot("rod1", [(0, 0, 0, 0), (1.2, -25, 0, 0)])
    lw.rot("rod3", [(0, 0, 0, 0), (1.2, -20, 0, 0)])
    lw.scale("esca", [(0, 1, 1, 1), (1.2, 1.5, 1.5, 1.5)])
    lw.scale("halo", [(0, 1, 1, 1), (1.2, 1.7, 1.7, 1.7)])
    for nm, sg in (("gill_l", 1), ("gill_r", -1)):
        lw.rot(nm, [(0, 0, 0, 0), (1.2, 0, sg * -40, 0)])
    ls = r.clip("lunge_strike", 1.0)
    ls.pos("body", [(0, 0, 0, 4), (0.12, 0, 0, -10), (0.4, 0, 0, -8), (1.0, 0, 0, 0)])
    ls.rot("mjaw", [(0, 40, 0, 0), (0.14, 42, 0, 0), (0.22, -10, 0, 0), (1.0, 0, 0, 0)])
    ls.rot("mhead", [(0, -14, 0, 0), (0.2, 6, 0, 0), (1.0, 0, 0, 0)])
    ls.rot("rod1", [(0, -25, 0, 0), (1.0, 0, 0, 0)])
    ls.rot("rod3", [(0, -20, 0, 0), (1.0, 0, 0, 0)])
    ls.scale("esca", [(0, 1.5, 1.5, 1.5), (1.0, 1, 1, 1)])
    ls.scale("halo", [(0, 1.7, 1.7, 1.7), (1.0, 1, 1, 1)])
    wave(ls, ["mtail1", "mtail2", "caudal"], "y", 0, lag=0.1, amps=[20, 30, 40])
    # Lure: the bulb dances out in front of you, closer and closer, then jerks back toward the open mouth
    uw = r.clip("lure_windup", 1.5)
    uw.rot("rod1", [(0, 0, 0, 0), (0.5, 15, 0, 10), (1.0, 8, 0, -10), (1.5, 22, 0, 0)])
    uw.rot("rod2", [(0, 0, 0, 0), (0.5, -10, 0, 0), (1.5, -20, 0, 0)])
    uw.scale("esca", [(0, 1, 1, 1), (0.3, 1.4, 1.4, 1.4), (0.6, 1.1, 1.1, 1.1), (0.9, 1.5, 1.5, 1.5), (1.2, 1.2, 1.2, 1.2), (1.5, 1.7, 1.7, 1.7)])
    uw.scale("halo", [(0, 1, 1, 1), (0.3, 1.5, 1.5, 1.5), (0.6, 1.1, 1.1, 1.1), (0.9, 1.7, 1.7, 1.7), (1.2, 1.2, 1.2, 1.2), (1.5, 1.9, 1.9, 1.9)])
    uw.rot("mjaw", [(0, 0, 0, 0), (1.5, 22, 0, 0)])
    us = r.clip("lure_strike", 0.8)
    us.rot("rod1", [(0, 22, 0, 0), (0.15, -25, 0, 0), (0.8, 0, 0, 0)])
    us.rot("rod2", [(0, -20, 0, 0), (0.15, 15, 0, 0), (0.8, 0, 0, 0)])
    us.scale("esca", [(0, 1.7, 1.7, 1.7), (0.8, 1, 1, 1)])
    us.scale("halo", [(0, 1.9, 1.9, 1.9), (0.8, 1, 1, 1)])
    us.rot("mjaw", [(0, 22, 0, 0), (0.3, 30, 0, 0), (0.8, 0, 0, 0)])
    # Black Water: the light goes out, the body hunches, fins flare (the ink)
    iw = r.clip("ink_windup", 1.0)
    iw.scale("esca", [(0, 1, 1, 1), (1.0, 0.4, 0.4, 0.4)])
    iw.scale("halo", [(0, 1, 1, 1), (1.0, 0.1, 0.1, 0.1)])
    iw.rot("body", [(0, 0, 0, 0), (1.0, 10, 0, 0)])
    for nm, sg in (("mpec_l", 1), ("mpec_r", -1)):
        iw.rot(nm, [(0, 0, 0, 0), (1.0, 0, sg * -40, sg * -20)])
    ist = r.clip("ink_strike", 0.8)
    ist.rot("body", [(0, 10, 0, 0), (0.15, -6, 0, 0), (0.8, 0, 0, 0)])
    ist.scale("esca", [(0, 0.4, 0.4, 0.4), (0.8, 0.6, 0.6, 0.6)])
    ist.scale("halo", [(0, 0.1, 0.1, 0.1), (0.8, 0.4, 0.4, 0.4)])
    # Abyss Glare: the lure held out straight and still, blazing; then a flash
    gw = r.clip("glare_windup", 1.4)
    gw.rot("rod1", [(0, 0, 0, 0), (1.4, 20, 0, 0)])
    gw.rot("rod2", [(0, 0, 0, 0), (1.4, -15, 0, 0)])
    gw.rot("rod3", [(0, 0, 0, 0), (1.4, -25, 0, 0)])
    gw.scale("esca", [(0, 1, 1, 1), (1.4, 2.0, 2.0, 2.0)])
    gw.scale("halo", [(0, 1, 1, 1), (1.4, 2.2, 2.2, 2.2)])
    gs = r.clip("glare_strike", 0.8)
    gs.scale("esca", [(0, 2.0, 2.0, 2.0), (0.1, 2.6, 2.6, 2.6), (0.8, 1, 1, 1)])
    gs.scale("halo", [(0, 2.2, 2.2, 2.2), (0.1, 3.0, 3.0, 3.0), (0.8, 1, 1, 1)])
    gs.rot("rod1", [(0, 20, 0, 0), (0.8, 0, 0, 0)])
    gs.rot("rod2", [(0, -15, 0, 0), (0.8, 0, 0, 0)])
    gs.rot("rod3", [(0, -25, 0, 0), (0.8, 0, 0, 0)])
    w = r.clip("windup", 0.8)
    w.rot("mjaw", [(0, 0, 0, 0), (0.8, 28, 0, 0)])
    w.rot("mhead", [(0, 0, 0, 0), (0.8, -8, 0, 0)])
    st = r.clip("strike", 0.5)
    st.rot("mjaw", [(0, 28, 0, 0), (0.1, -10, 0, 0), (0.5, 0, 0, 0)])
    st.pos("body", [(0, 0, 0, 0), (0.1, 0, 0, -4), (0.5, 0, 0, 0)])
    bite = r.clip("bite", 0.6)
    bite.rot("mjaw", [(0, 0, 0, 0), (0.12, 34, 0, 0), (0.25, -10, 0, 0), (0.6, 0, 0, 0)])
    bite.pos("body", [(0, 0, 0, 0), (0.2, 0, 0, -4), (0.6, 0, 0, 0)])
    roar = r.clip("roar", 1.8)
    roar.rot("mjaw", [(0, 0, 0, 0), (0.3, 45, 0, 0), (1.4, 45, 0, 0), (1.8, 0, 0, 0)])
    roar.rot("mhead", [(0, 0, 0, 0), (0.3, -16, 0, 0), (0.5, -16, 6, 0), (0.7, -16, -6, 0), (0.9, -16, 6, 0), (1.1, -16, -6, 0), (1.4, -14, 0, 0), (1.8, 0, 0, 0)])
    roar.scale("esca", [(0, 1, 1, 1), (0.3, 1.8, 1.8, 1.8), (1.4, 1.6, 1.6, 1.6), (1.8, 1, 1, 1)])
    roar.scale("halo", [(0, 1, 1, 1), (0.3, 2.0, 2.0, 2.0), (1.4, 1.8, 1.8, 1.8), (1.8, 1, 1, 1)])
    for nm, sg in (("mpec_l", 1), ("mpec_r", -1)):
        roar.rot(nm, [(0, 0, 0, 0), (0.3, 0, sg * -50, sg * -30), (1.4, 0, sg * -50, sg * -30), (1.8, 0, 0, 0)])
    for nm, sg in (("gill_l", 1), ("gill_r", -1)):
        roar.rot(nm, [(0, 0, 0, 0), (0.3, 0, sg * -50, 0), (1.4, 0, sg * -50, 0), (1.8, 0, 0, 0)])
    warn = r.clip("warn", 1.2)
    # the warning: the lure flicks bright twice, the jaw snaps shut on nothing
    warn.scale("esca", [(0, 1, 1, 1), (0.15, 1.8, 1.8, 1.8), (0.3, 1, 1, 1), (0.45, 1.8, 1.8, 1.8), (0.6, 1, 1, 1)])
    warn.scale("halo", [(0, 1, 1, 1), (0.15, 2.2, 2.2, 2.2), (0.3, 1, 1, 1), (0.45, 2.2, 2.2, 2.2), (0.6, 1, 1, 1)])
    warn.rot("mjaw", [(0, 0, 0, 0), (0.7, 32, 0, 0), (0.85, -10, 0, 0), (1.2, 0, 0, 0)])
    hurt = r.clip("hurt", 0.5)
    hurt.rot("body", [(0, 0, 0, 0), (0.06, -4, 8, 6), (0.25, 2, -3, -2), (0.5, 0, 0, 0)])
    hurt.scale("esca", [(0, 1, 1, 1), (0.06, 0.5, 0.5, 0.5), (0.4, 1, 1, 1)])
    hurt.scale("halo", [(0, 1, 1, 1), (0.06, 0.3, 0.3, 0.3), (0.4, 1, 1, 1)])
    death = r.clip("death", 1.8)
    # it rolls belly up, the jaw goes slack, the light dies, and it sinks
    death.rot("body", [(0, 0, 0, 0), (0.4, -8, 0, 20), (1.4, 0, 0, 170), (1.8, 0, 0, 178)])
    death.pos("body", [(0, 0, 0, 0), (1.8, 0, -8, 0)])
    death.rot("mjaw", [(0, 0, 0, 0), (0.4, 36, 0, 0), (1.8, 24, 0, 0)])
    death.scale("esca", [(0, 1, 1, 1), (1.0, 0.5, 0.5, 0.5), (1.8, 0.3, 0.3, 0.3)])
    death.scale("halo", [(0, 1, 1, 1), (1.0, 0.3, 0.3, 0.3), (1.8, 0.01, 0.01, 0.01)])


def paint_lantern_maw(r, P):
    hide = Ramp("#07090f", "#0d121b", "#151c27", "#1f2834", "#2b3645", "#3a4658", "#4c5a6e")
    belly = Ramp("#2a2e38", "#3c424e", "#525866", "#6a7080")
    mouth = Ramp("#1e0408", "#34080e", "#521018", "#741c24", "#963038")
    glassy = Ramp("#5a6a70", "#8aa4a8", "#bcd6d6", "#e8fffa")
    teal = hexc("#7fffe0")
    # the hide: dark, warty, lit along the top edges so the shape reads in daylight (a cold rim of light)
    warts = combine(p_blotch("maw", 7, 0.5, levels=3), lambda f, i, j: (0.9 if (_h(f.cube.name, f.name, i // 2, j // 2) % 9 == 0) else 0))
    for nm in ("trunk", "mskull", "crown", "mjaw", "chin", "mtail1", "mtail2", "lip"):
        P.paint(nm, hide, 2.9 if nm not in ("crown",) else 3.3, warts, rim=0.9)
    # the pale, ghostly underside
    for nm in ("trunk", "mjaw", "chin", "mtail1"):
        f = P.face(nm, "bottom")
        for i, j in f.each():
            f.set(i, j, belly.at(2.0 - (0.5 if (i * 3 + j) % 5 == 0 else 0)))
    # inside the mouth: the jaw's top face and the skull's underside are a red gullet with a pale tongue ridge
    for nm, fname in (("mjaw", "top"), ("mskull", "bottom")):
        f = P.face(nm, fname)
        for i, j in f.each():
            ridge = abs(i - f.w / 2 + 0.5) < 2.5 and nm == "mjaw"
            f.set(i, j, mouth.at(2.0 + (1.6 if ridge else 0) - (0.8 if (j < 2 or i < 2 or i >= f.w - 2) else 0) + (0.4 if (i + j) % 4 == 0 else 0)))
    # the back of the throat seen through the gape: the trunk's front face is black-red
    f = P.face("trunk", "north")
    for i, j in f.each():
        f.set(i, j, mouth.at(0.6 + (0.6 if abs(i - f.w / 2 + 0.5) < 4 and 4 < j < 12 else 0)))
    # teeth: needles of glass, uneven, transparent between; they catch the light a little
    for nm in ("teeth_up", "teeth_lo", "teeth_upside_l", "teeth_loside_l"):
        for fname in ("north", "south", "west", "east"):
            f = P.face(nm, fname)
            if f.w <= 0 or f.h <= 0:
                continue
            g = P.face(nm, fname, glow=True)
            up = nm in ("teeth_lo", "teeth_loside_l")
            for i, j in f.each():
                length = 1 + (_h(nm, i) % f.h)
                inside = (f.h - 1 - j) < length if up else j < length
                if i % 2 == 1 or not inside:
                    f.set(i, j, (0, 0, 0, 0))
                    continue
                tip = (j == f.h - length) if up else (j == length - 1)
                f.set(i, j, glassy.at(3.4 if tip else 2.4)[:3] + (240,))
                g.set(i, j, hexc("#c8fff0", 120 if tip else 70))
    for nm in ("fang_up_l", "fang_lo0"):
        P.paint(nm, glassy, 2.6, lambda f, i, j: 1.0 if (j == 0 if nm == "fang_lo0" else j == f.h - 1) else 0)
        for fname in SIDES:
            g = P.face(nm, fname, glow=True)
            for j in range(g.h):
                g.set(0, j, hexc("#d8fff4", 90 + (80 if (j == 0 if nm == "fang_lo0" else j == g.h - 1) else 0)))
    # the lure: a blazing white-teal bulb (glow on every face, nearly white at its heart), the rod dark with
    # luminous rings so its arc shows in the black water, two glowing filaments
    P.paint("esca", Ramp("#4ad8c0", "#7cf0dc", "#b4fff0", "#eafffb"), 3.0, lambda f, i, j: 1.0 if 1 <= i <= 3 and 1 <= j <= 3 else 0)
    for nm in ("esca", "escacap"):
        for fname in ("top", "bottom") + SIDES:
            g = P.face(nm, fname, glow=True)
            for i, j in g.each():
                core = 1 <= i <= g.w - 2 and 1 <= j <= g.h - 2
                g.set(i, j, hexc("#f0fffc" if core else "#a8fff0", 255))
    P.paint("escacap", Ramp("#4ad8c0", "#7cf0dc", "#b4fff0"), 2.0)
    for nm in ("rod1", "rod4"):
        P.paint(nm, hide, 3.4, lambda f, i, j: 1.8 if j % 3 == 0 else 0)
        for fname in SIDES + ("top", "bottom"):
            g = P.face(nm, fname, glow=True)
            for i, j in g.each():
                if j % 3 == 0:
                    g.set(i, j, hexc("#7fffe0", 190))
    for fname in ("west", "east"):
        f = P.face("filament0", fname)
        g = P.face("filament0", fname, glow=True)
        for i, j in f.each():
            f.set(i, j, hexc("#9ffff0", 220))
            g.set(i, j, hexc("#c8fff6", 255 if j % 2 == 0 or j == f.h - 1 else 190))
    # the halo: no skin at all, only light, brightest at the bulb and fading to nothing (crossed planes read as a
    # soft orb from any side)
    for nm, fnames in (("halo_xy", ("north", "south")), ("halo_zy", ("west", "east")), ("halo_xz", ("top", "bottom"))):
        for fname in fnames:
            f = P.face(nm, fname)
            g = P.face(nm, fname, glow=True)
            for i, j in f.each():
                f.set(i, j, (0, 0, 0, 0))
                dx, dy = (i + 0.5 - f.w / 2) / (f.w / 2), (j + 0.5 - f.h / 2) / (f.h / 2)
                d = math.sqrt(dx * dx + dy * dy)
                if d >= 1.0:
                    continue
                a = int(150 * (1.0 - d) ** 1.6)
                if a >= 8:
                    g.set(i, j, lerpc(hexc("#5ff2d8"), hexc("#d8fff8"), max(0.0, 1.0 - d * 1.6))[:3] + (a,))
    # small pale eyes that catch the light
    P.paint("meye_l", Ramp("#2a3a3a", "#6a8a86", "#b8e0d8", "#e8fff8"), 2.4, lambda f, i, j: -2.2 if (i, j) == (1, 1) else 0)
    for fname in SIDES:
        g = P.face("meye_l", fname, glow=True)
        for i, j in g.each():
            g.set(i, j, hexc("#c8fff0", 230 if (i, j) != (1, 1) else 90))
    # ragged fins: dark membranes stretched between pale rays that run past the torn edge; glowing ray tips.
    # ab(fname, i, j, w, h) -> (across, along, length): along = 0 at the fin's root.
    along_z = lambda fname, i, j, w, h: (j, i if fname == "west" else w - 1 - i, w)  # noqa: E731  (east faces run back to front)
    up_from_root = lambda fname, i, j, w, h: (i, h - 1 - j, h)  # noqa: E731
    down_from_root = lambda fname, i, j, w, h: (i, j, h)  # noqa: E731

    def ragged(nm, ab, every, seed, fan=0.0):
        for fname in ("west", "east"):
            f = P.face(nm, fname)
            g = P.face(nm, fname, glow=True)
            if f.w <= 0 or f.h <= 0:
                continue
            for i, j in f.each():
                a, b, length = ab(fname, i, j, f.w, f.h)
                across = f.h if ab is along_z else f.w
                # a fan: narrow at the root, opening toward the edge
                if fan and abs(a + 0.5 - across / 2) > (across / 2) * (1.0 - fan + fan * (b + 1) / length):
                    f.set(i, j, (0, 0, 0, 0))
                    continue
                ray = a % every == every // 2
                # the torn edge: each gap between rays is torn back by a different amount; a few holes
                torn = 1 + (_h(nm, seed, a // every) % (4 if every == 2 else 3))
                if not ray and (b >= length - torn or _h(nm, seed, a, b) % 23 == 0):
                    f.set(i, j, (0, 0, 0, 0))
                    continue
                f.set(i, j, hide.at(4.2 if ray else 2.6 + 0.8 * (b / max(1, length - 1)))[:3] + (245 if ray else 210,))
                tips = 1 if (every == 3 or ab is down_from_root) else 2
                if ray and b >= length - tips:
                    g.set(i, j, teal[:3] + (150 if every == 3 else 230,))
    ragged("mpec_l", along_z, 2, 1, fan=0.6)
    ragged("gill_l", along_z, 2, 2, fan=0.4)
    ragged("mdorsal", up_from_root, 3, 3)
    ragged("manal", down_from_root, 2, 4)
    for k, nm in enumerate(("caudal_u", "caudal_m", "caudal_d")):
        ragged(nm, along_z, 2, 5 + k, fan=0.3)
    # photophores: two rows of cold lights down each flank, a row along the jaw's edge and spots on the chin, so its
    # length and its gape show in black water
    def spots(nm, fname, row, n, start=0.0, size=1):
        f = P.face(nm, fname)
        g = P.face(nm, fname, glow=True)
        for k in range(n):
            i = int(start * f.w + (k + 0.5) * f.w * (1 - start) / n)
            for di in range(size):
                for dj in range(size):
                    f.set(i + di, row + dj, hexc("#9ffff0"))
                    g.set(i + di, row + dj, hexc("#c0fff6", 255))
    for fname in ("west", "east"):
        spots("trunk", fname, 6, 4, size=2)
        spots("trunk", fname, 11, 3, start=0.2)
        spots("mtail1", fname, 4, 2, size=2)
        spots("mtail2", fname, 3, 1)
        spots("mskull", fname, 8, 4, start=0.15)
        spots("mjaw", fname, 2, 6)
    spots("chin", "bottom", 4, 4)
    spots("chin", "north", 1, 3)
    # a faint lateral glow along the crown's ridge
    f = P.face("crown", "top")
    g = P.face("crown", "top", glow=True)
    for j in range(1, f.h - 1, 2):
        g.set(f.w // 2, j, teal[:3] + (130,))
    return P.skin, P.glow


# =====================================================================================================================
# The Hollows
# =====================================================================================================================

# ---------------------------------------------------------------------------------------------------------------------
# Chime Cricket: a hump-backed cave cricket with amethyst growing out of its back, long sweeping antennae and big
# jumping legs; beside buried ore it lifts its wings and sings (the crystals ring with it)

def chime_cricket():
    r = Rig("chime_cricket", (64, 32))
    r.walk_stride, r.run_stride, r.run_speed, r.shadow = 0.6, 1.2, (0.12, 0.24), 0.3
    body = r.bone("body", (0, 20.5, 0))
    body.cube("thorax", -1.5, -1.5, -2, 3, 3, 3)
    abd = body.bone("abdomen", (0, -0.5, 1), (8, 0, 0))
    abd.cube("abdomen", -2, -1.5, 0, 4, 3, 5)
    for k, (x, z, h, w, rx, rz) in enumerate(((0, 1, 4, 2, -20, 0), (-1.2, 2.5, 3, 1, -30, -25), (1.2, 3, 3, 1, -35, 25), (0, 4, 3, 2, -45, 0))):
        abd.bone(f"crystal{k}", (x, -1.5, z), (rx, 0, rz)).cube(f"crystal{k}", -w / 2, -h, -w / 2, w, h, w)
    for side, nm in ((1, "l"), (-1, "r")):
        mir = side < 0

        def sh(n, side=side):
            return None if side > 0 else n
        abd.bone("cwing_" + nm, (side * 1, -1.6, 0), (-6, 0, side * -8)).cube("cwing_" + nm, 0 if side > 0 else -2, 0, 0, 2, 0, 5, mirror=mir, share=sh("cwing_l"))
        for k, (z, ry) in enumerate(((-1.5, -30), (-0.5, 10))):
            body.bone(f"cleg{k}_" + nm, (side * 1.5, 1, z), (0, side * ry, side * 55)).cube(
                f"cleg{k}_" + nm, 0 if side > 0 else -3, 0, -0.5, 3, 1, 1, mirror=mir, share="cleg0_l" if (k or side < 0) else None)
        hind = body.bone("hind_" + nm, (side * 1.8, 0.5, 0.5), (50, side * -8, 0))
        hind.cube("hthigh_" + nm, -0.5, -0.5, 0, 1, 2, 5, mirror=mir, share=sh("hthigh_l"))
        hind.bone("hshin_" + nm, (0, 0.5, 5), (-35, 0, 0)).cube("hshin_" + nm, -0.5, 0, -0.5, 1, 6, 1, mirror=mir, share=sh("hshin_l"))
    head = body.bone("chead", (0, 0, -2))
    head.cube("chead", -1.5, -1.5, -2.5, 3, 3, 3)
    for side, nm in ((1, "l"), (-1, "r")):
        ant = head.bone("cant_" + nm, (side * 0.7, -1.5, -2), (55, side * -20, 0))
        ant.cube("cant_" + nm, 0, -6, 0, 0, 6, 1, mirror=side < 0, share=None if side > 0 else "cant_l")
        ant.bone("cant2_" + nm, (0, -6, 0), (35, 0, 0)).cube("cant2_" + nm, 0, -5, 0, 0, 5, 1, mirror=side < 0, share=None if side > 0 else "cant2_l")
    r.look = [("chead", 0.5, 0.4)]
    r.pack()
    cricket_clips(r)
    return r


def cricket_clips(r):
    idle = r.clip("idle", 3.0, loop=True)
    for nm, sg in (("cant_l", 1), ("cant_r", -1)):
        cycle(idle, nm, "rot", lambda p, sg=sg: (10 * s(p, 0.1 if sg > 0 else 0.4), sg * 12 * s(p, 0.25), 0), 8)
        cycle(idle, nm.replace("cant", "cant2"), "rot", lambda p, sg=sg: (14 * s(p, 0.3), 0, 0), 8)
    cycle(idle, "abdomen", "scale", lambda p: (1 + 0.04 * s(p * 2), 1 + 0.05 * s(p * 2), 1), 8)
    walk = r.clip("walk", 0.5, loop=True)
    # a tripod gait: front-left, middle-right and hind-left together, then the other three
    for nm, ph in (("cleg0_l", 0.0), ("cleg1_r", 0.0), ("cleg0_r", 0.5), ("cleg1_l", 0.5)):
        sg = 1 if nm.endswith("_l") else -1
        cycle(walk, nm, "rot", lambda p, ph=ph, sg=sg: (0, sg * 25 * s(p, ph), sg * -15 * max(0.0, c(p, ph))), 8)
    for nm, ph in (("hind_l", 0.0), ("hind_r", 0.5)):
        cycle(walk, nm, "rot", lambda p, ph=ph: (12 * s(p, ph), 0, 0), 8)
    cycle(walk, "body", "pos", lambda p: (0, 0.3 * abs(s(p * 2)), 0), 8)
    chirp = r.clip("chirp", 0.2, loop=True)
    # the song: wings raised and buzzing, antennae up, the crystals trembling
    for nm, sg in (("cwing_l", 1), ("cwing_r", -1)):
        chirp.rot(nm, [(0, -40, 0, sg * -20), (0.05, -46, 0, sg * -28), (0.1, -40, 0, sg * -20), (0.15, -46, 0, sg * -28), (0.2, -40, 0, sg * -20)], interp="lin")
    for k in range(4):
        chirp.scale(f"crystal{k}", [(0, 1, 1, 1), (0.1, 1.15, 1.1, 1.15), (0.2, 1, 1, 1)], interp="lin")
    for nm in ("cant_l", "cant_r"):
        chirp.rot(nm, [(0, -20, 0, 0), (0.2, -20, 0, 0)])
    chirp.rot("abdomen", [(0, -8, 0, 0), (0.2, -8, 0, 0)])
    hurt = r.clip("hurt", 0.35)
    hurt.pos("body", [(0, 0, 0, 0), (0.08, 0, 2, 0), (0.35, 0, 0, 0)])
    for nm in ("cant_l", "cant_r"):
        hurt.rot(nm, [(0, 0, 0, 0), (0.06, 30, 0, 0), (0.35, 0, 0, 0)])
    death = r.clip("death", 1.0)
    death.rot("body", [(0, 0, 0, 0), (0.5, 0, 0, 170), (1.0, 0, 0, 180)])
    death.pos("body", [(0, 0, 0, 0), (1.0, 0, -2, 0)])
    for nm in ("cleg0_l", "cleg0_r", "cleg1_l", "cleg1_r"):
        death.rot(nm, [(0, 0, 0, 0), (1.0, 0, 0, -40 if nm.endswith("_l") else 40)])


def paint_chime_cricket(r, P):
    shell = Ramp("#14101c", "#201a2c", "#2e263e", "#3e3452", "#504468")
    leg = Ramp("#1a1410", "#2a2018", "#3c3024", "#524232")
    amethyst = Ramp("#3a1e5a", "#5a2e8a", "#7c48b8", "#a070e0", "#c8a4ff", "#ecdcff")
    P.paint("thorax", shell, 3.0, lambda f, i, j: 0.8 if f.name == "top" and i == 1 else 0)
    P.paint("abdomen", shell, 3.0, combine(p_bands(2, 0.6), lambda f, i, j: 0.5 if f.name == "top" and i in (1, 2) else 0))
    P.paint("chead", shell, 2.8)
    for fname in ("west", "east", "north"):
        f = P.face("chead", fname)
        g = P.face("chead", fname, glow=True)
        e = 0 if fname != "east" else f.w - 1
        f.set(e, 1, hexc("#d8c8ff"))
        g.set(e, 1, hexc("#ece0ff", 240))
    for k in range(4):
        P.paint(f"crystal{k}", amethyst, 3.6, lambda f, i, j: (1.4 if j == 0 else 0) - (0.8 if f.name in ("east", "south") else 0))
        # night: every face of every crystal shines, brightest at the point (190 at the root to 255 at the tip)
        for fname in ("top", "bottom", "north", "west", "east", "south"):
            g = P.face(f"crystal{k}", fname, glow=True)
            for i, j in g.each():
                t = 1.0 if fname == "top" else 0.0 if fname == "bottom" else 1.0 - j / max(1, g.h - 1)
                g.set(i, j, lerpc(hexc("#b48cf0"), hexc("#f0e4ff"), t)[:3] + (int(190 + 65 * t),))
    # a vein of crystal light down the middle of the back, and glowing antenna tips
    f = P.face("abdomen", "top")
    g = P.face("abdomen", "top", glow=True)
    for j in range(f.h):
        for i in (1, 2):
            g.set(i, j, hexc("#c8a4ff", 170 if j % 2 == 0 else 120))
    for nm in ("cant2_l",):
        for fname in ("west", "east"):
            g = P.face(nm, fname, glow=True)
            g.set(0, 0, hexc("#e0ccff", 240))
            g.set(0, 1, hexc("#e0ccff", 170))
    for nm in ("cleg0_l", "hthigh_l", "hshin_l"):
        P.paint(nm, leg, 2.6, lambda f, i, j: 0.8 if j == 0 and f.name in SIDES else 0)
    for nm in ("cant_l", "cant2_l"):
        for fname in ("west", "east"):
            f = P.face(nm, fname)
            for i, j in f.each():
                f.set(i, j, leg.at(2.4 + (0.6 if j % 2 == 0 else 0)))
    for fname in ("top", "bottom"):
        f = P.face("cwing_l", fname)
        for i, j in f.each():
            vein = i == 0 or j % 2 == 0
            f.set(i, j, hexc("#8a7aa8", 220) if vein else hexc("#bcb0d8", 140))
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# Wickling: a small dark cave salamander with a candle-flame crest of frills and a glowing tail tip; it walks with a
# side-to-side sway and rears up to touch the vines it ripens

def wickling():
    r = Rig("wickling", (64, 32))
    r.walk_stride, r.run_stride, r.run_speed, r.shadow = 0.7, 1.4, (0.12, 0.24), 0.3
    body = r.bone("body", (0, 21.5, 0))
    body.cube("wbody", -1.5, -1.5, -3, 3, 2, 6)
    head = body.bone("whead", (0, -0.5, -3))
    head.cube("whead", -2, -1, -3, 4, 2, 3)
    for k, (x, z, rz, h) in enumerate(((0, -1, 0, 4), (1.2, -0.5, 30, 3), (-1.2, -0.5, -30, 3))):
        head.bone(f"crest{k}", (x, -1, z), (-15, 0, rz)).cube(f"crest{k}", 0, -h, -0.5, 0, h, 3, share=None if k == 0 else ("crest1" if k == 2 else None))
    for k, z in enumerate((-1, 1.5)):
        body.bone(f"wfrill{k}", (0, -1.5, z), (-10, 0, 0)).cube(f"wfrill{k}", 0, -2, 0, 0, 2, 2, share=None if k == 0 else "wfrill0")
    for side, nm in ((1, "l"), (-1, "r")):
        mir = side < 0
        for k, z in enumerate((-2, 2)):
            leg = body.bone(f"wleg{k}_" + nm, (side * 1.5, 0, z), (0, side * (20 if k == 0 else -20), 0))
            leg.cube(f"wleg{k}_" + nm, 0 if side > 0 else -2, -0.5, -0.5, 2, 1, 1, mirror=mir, share="wleg0_l" if (k or side < 0) else None)
            leg.bone(f"wfoot{k}_" + nm, (side * 2, 0, 0)).cube(f"wfoot{k}_" + nm, -0.5, 0, -0.5, 1, 2, 1, mirror=mir, share="wfoot0_l" if (k or side < 0) else None)
    t1 = body.bone("wtail1", (0, -0.5, 3))
    t1.cube("wtail1", -1, -0.5, 0, 2, 1, 4)
    t2 = t1.bone("wtail2", (0, 0, 4))
    t2.cube("wtail2", -0.5, -0.5, 0, 1, 1, 3)
    tip = t2.bone("wtip", (0, 0, 3))
    tip.cube("wtip", -0.5, -0.5, 0, 1, 1, 1)
    tip.bone("flame", (0, -0.5, 0.5)).cube("flame", 0, -5, -2, 0, 5, 4)
    r.look = [("whead", 0.6, 0.5)]
    r.pack()
    wickling_clips(r)
    return r


def wickling_clips(r):
    idle = r.clip("idle", 2.0, loop=True)
    # the flame flickers: the crest and the tail's flame stretch and shrink, never in step
    for k in range(3):
        idle.scale(f"crest{k}", [(0, 1, 1, 1), (0.3 + k * 0.1, 1, 1.25, 1), (0.7 + k * 0.1, 1, 0.9, 1), (1.3, 1, 1.15, 1), (2.0, 1, 1, 1)])
    idle.scale("flame", [(0, 1, 1, 1), (0.25, 1, 1.3, 1), (0.6, 1, 0.85, 1), (1.1, 1, 1.2, 1), (1.5, 1, 0.9, 1), (2.0, 1, 1, 1)])
    cycle(idle, "flame", "rot", lambda p: (0, 0, 10 * s(p * 2)), 8)
    cycle(idle, "whead", "rot", lambda p: (0, 0, 8 * s(p, 0.3)), 8)
    cycle(idle, "wtail1", "rot", lambda p: (0, 10 * s(p), 0), 8)
    walk = r.clip("walk", 0.6, loop=True)
    # a salamander's walk: the spine bends side to side, diagonal legs step together
    wave(walk, ["whead", "body", "wtail1", "wtail2"], "y", 0, lag=0.18, amps=[-10, 8, 16, 20])
    for nm, ph in (("wleg0_l", 0.0), ("wleg1_r", 0.0), ("wleg0_r", 0.5), ("wleg1_l", 0.5)):
        sg = 1 if nm.endswith("_l") else -1
        cycle(walk, nm, "rot", lambda p, ph=ph, sg=sg: (0, sg * 30 * s(p, ph), sg * -20 * max(0.0, c(p, ph))), 8)
    ripen = r.clip("ripen", 1.5, loop=True)
    # it rears up on its hind legs toward the vine, the crest blazing
    ripen.rot("body", [(0, -35, 0, 0), (1.5, -35, 0, 0)])
    ripen.pos("body", [(0, 0, 1.5, 0), (1.5, 0, 1.5, 0)])
    ripen.rot("whead", [(0, -15, 0, 0), (0.75, -25, 0, 0), (1.5, -15, 0, 0)])
    for k in range(3):
        ripen.scale(f"crest{k}", [(0, 1.3, 1.5, 1.3), (0.75, 1.3, 1.8, 1.3), (1.5, 1.3, 1.5, 1.3)])
    hurt = r.clip("hurt", 0.35)
    hurt.rot("body", [(0, 0, 0, 0), (0.06, 0, 20, 0), (0.35, 0, 0, 0)])
    for k in range(3):
        hurt.scale(f"crest{k}", [(0, 1, 1, 1), (0.06, 1, 0.4, 1), (0.35, 1, 1, 1)])
    death = r.clip("death", 1.0)
    death.rot("body", [(0, 0, 0, 0), (0.6, 0, 0, 170), (1.0, 0, 0, 180)])
    death.pos("body", [(0, 0, 0, 0), (1.0, 0, -1.5, 0)])
    death.scale("flame", [(0, 1, 1, 1), (1.0, 0.2, 0.2, 0.2)])
    for k in range(3):
        death.scale(f"crest{k}", [(0, 1, 1, 1), (1.0, 0.4, 0.3, 0.4)])


def paint_wickling(r, P):
    skin = Ramp("#120c0a", "#1e1410", "#2c1e16", "#3e2a1c", "#523824")
    belly = Ramp("#5a2a10", "#7a3c16", "#9c521e", "#c06c28")
    flame = Ramp("#a8300c", "#e05a14", "#ff8a2a", "#ffbe4a", "#ffe89a", "#fffbe0")
    speck = lambda f, i, j: (hexc("#ff8a2a") if (_h(f.cube.name, f.name, i, j) % 5 == 0 and f.name == "top") else 0)  # noqa: E731
    for nm in ("wbody", "whead", "wtail1", "wtail2", "wleg0_l", "wfoot0_l"):
        P.paint(nm, skin, 2.8, speck)
        f = P.face(nm, "bottom")
        for i, j in f.each():
            f.set(i, j, belly.at(2.4))
        for i, j in f.each():
            g = P.face(nm, "top", glow=True)
            if P.face(nm, "top").get(i, j)[:3] == hexc("#ff8a2a")[:3]:
                g.set(i, j, hexc("#ffb45a", 225))
    with P.eyes():
        for fname in ("west", "east", "north"):
            f = P.face("whead", fname)
            g = P.face("whead", fname, glow=True)
            e = 0 if fname != "east" else f.w - 1
            f.set(e, 0, hexc("#ffd27a"))
            g.set(e, 0, hexc("#ffe08a", 250))
    # the crest, the frills and the tail's flame: candle-flame planes, hot at the root, pale at the tip
    for nm in ("crest0", "crest1", "wfrill0", "flame"):
        for fname in ("west", "east"):
            f = P.face(nm, fname)
            g = P.face(nm, fname, glow=True)
            for i, j in f.each():
                t = j / max(1, f.h - 1)  # 0 at the tip (top), 1 at the root
                narrow = (j == 0 and i != (f.w // 2)) and f.w > 1
                if narrow:
                    f.set(i, j, (0, 0, 0, 0))
                    continue
                col = flame.at(5 - t * 3.5)
                f.set(i, j, col[:3] + (235,))
                # both faces burn: 255 at the tip down to 190 at the root
                g.set(i, j, col[:3] + (int(255 - 65 * t),))
    P.paint("wtip", flame, 3.6)
    for fname in ("top", "bottom") + SIDES:
        P.face("wtip", fname, glow=True).set(0, 0, hexc("#ffd27a", 255))
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# Drip Lurker: a harvestman-like hunter wearing a cone of dripstone on its back. Hanging from a cave roof (the renderer
# turns it over) its cone is a stalactite among stalactites; on the ground it stalks on six high-kneed legs, cone up.

def drip_lurker():
    r = Rig("drip_lurker", (64, 64))
    r.walk_stride, r.run_stride, r.run_speed, r.shadow = 1.0, 1.8, (0.16, 0.32), 0.6
    body = r.bone("body", (0, 16, 0))
    body.cube("dbody", -4, -1, -4, 8, 3, 8)
    cone = body.bone("cone", (0, -1, 0))
    cone.cube("cone0", -4.5, -3, -4.5, 9, 3, 9)
    cone.cube("cone1", -3.5, -6, -3.5, 7, 3, 7)
    cone.cube("cone2", -2.5, -9, -2.5, 5, 3, 5)
    cone.cube("cone3", -1.5, -12, -1.5, 3, 3, 3)
    cone.bone("conetip", (0, -12, 0)).cube("cone4", -0.5, -3, -0.5, 1, 3, 1)
    for k, (x, z, rx, rz) in enumerate(((3, 2, 15, 20), (-3, -2, -15, -20))):
        cone.bone(f"drip{k}", (x, -3, z), (rx, 0, rz)).cube(f"drip{k}", -1, -4, -1, 2, 4, 2, share=None if k == 0 else "drip0")
    head = body.bone("dhead", (0, 1, -4))
    head.cube("dhead", -2, -1, -2, 4, 3, 2)
    for side, nm in ((1, "l"), (-1, "r")):
        head.bone("fang_" + nm, (side * 1, 2, -2), (-20, 0, side * -10)).cube("fang_" + nm, -0.5, 0, -0.5, 1, 3, 1, mirror=side < 0, share=None if side > 0 else "fang_l")
    for side, nm in ((1, "l"), (-1, "r")):
        mir = side < 0
        for k, (z, ry) in enumerate(((-3, 35), (0, 0), (3, -35))):   # the front pair reaches forward, the back pair back
            leg = body.bone(f"dleg{k}_" + nm, (side * 4, 1, z), (0, side * ry, side * -32))
            leg.cube(f"dthigh{k}_" + nm, 0 if side > 0 else -6, -0.5, -0.5, 6, 1, 1, mirror=mir, share="dthigh0_l" if (k or side < 0) else None)
            leg.bone(f"dshin{k}_" + nm, (side * 6, 0, 0), (0, 0, side * 112)).cube(
                f"dshin{k}_" + nm, 0 if side > 0 else -10, -0.5, -0.5, 10, 1, 1, mirror=mir, share="dshin0_l" if (k or side < 0) else None)
    r.look = [("dhead", 0.4, 0.3)]
    r.moves = {"Spike Drop": "slam", "Lunge": "lunge"}
    r.pack()
    lurker_clips(r)
    return r


def lurker_clips(r):
    """On the ground its feet are planted by the motion layer (SPEC 23, Motion: assets/awaken/life/motion.json); the clips
    move the body over them, the cone, the head and the fangs, and own the legs only when it hangs, leaps or dies. A
    wind-up lets the front pair go (the profile's rear) so it rears them before it strikes."""
    L = (("dleg0_l", "dshin0_l", 1), ("dleg1_l", "dshin1_l", 1), ("dleg2_l", "dshin2_l", 1),
         ("dleg0_r", "dshin0_r", -1), ("dleg1_r", "dshin1_r", -1), ("dleg2_r", "dshin2_r", -1))
    fangs = (("fang_l", 1), ("fang_r", -1))
    idle = r.clip("idle", 6.0, loop=True)
    # it waits: the cone sways like a stalactite in a draught, the fangs taste the air now and then (never together)
    cycle(idle, "cone", "rot", lambda p: (1.2 * s(p), 0, 1.6 * s(p, 0.3)), 8)
    for nm, sg in fangs:
        lead = 1.1 if sg > 0 else 4.2
        idle.rot(nm, [(0, 0, 0, 0), (lead, 0, 0, 0), (lead + 0.08, 0, 0, sg * 14), (lead + 0.16, 0, 0, sg * 2),
                      (lead + 0.24, 0, 0, sg * 10), (lead + 0.4, 0, 0, 0), (6.0, 0, 0, 0)])
    idle.rot("dhead", [(0, 0, 0, 0), (2.4, 0, 0, 0), (2.6, 4, 0, 3), (3.4, 4, 0, 3), (3.7, 0, 0, 0), (6.0, 0, 0, 0)])
    # walking and running: the legs here are only for when nothing plants them; the body glides level over its feet
    # (they carry it), the cone lags the gait like a weight on a stalk
    walk = r.clip("walk", 0.9, loop=True)
    run = r.clip("run", 0.55, loop=True)
    for clip, swing, lift in ((walk, 22, 14), (run, 32, 20)):
        for k in range(3):
            for nm, side in (("dleg%d_l" % k, 1), ("dleg%d_r" % k, -1)):
                ph = (k % 2) * 0.5 + (0.5 if side < 0 else 0)
                cycle(clip, nm, "rot", lambda p, ph=ph, side=side, a=swing, b=lift: (0, side * a * s(p, ph), side * -b * max(0.0, c(p, ph))), 8)
    cycle(walk, "cone", "rot", lambda p: (1.5 + 1.5 * s(p * 2, 0.1), 0, 2.5 * s(p, 0.15)), 8)
    cycle(run, "cone", "rot", lambda p: (4 + 2 * s(p * 2, 0.1), 0, 3.5 * s(p, 0.15)), 8)
    run.rot("body", [(0, 5, 0, 0), (0.55, 5, 0, 0)])
    run.rot("dhead", [(0, -4, 0, 0), (0.55, -4, 0, 0)])
    # angry and still: low, the head down at its prey, fangs spread and quivering, the cone tipped forward
    angry = r.clip("angry", 2.0, loop=True)
    angry.pos("body", [(0, 0, -1, 0), (2.0, 0, -1, 0)])
    angry.rot("body", [(0, 5, 0, 0), (2.0, 5, 0, 0)])
    angry.rot("dhead", [(0, 8, 0, 0), (2.0, 8, 0, 0)])
    for nm, sg in fangs:
        cycle(angry, nm, "rot", lambda p, sg=sg: (0, 0, sg * (22 + 5 * s(p * 8, 0.25 if sg < 0 else 0))), 16)
    cycle(angry, "cone", "rot", lambda p: (6 + 1.0 * s(p), 0, 1.2 * s(p, 0.3)), 8)
    hang = r.clip("hang", 4.0, loop=True)
    # hanging (the renderer turns it over): legs drawn in round the cone's base, each flexing on its own breath; it sways
    # like a drip about to fall and the head hangs, watching the floor
    for k, (leg, shin, sg) in enumerate(L):
        ph = k / 6 + (0.5 if sg < 0 else 0)
        cycle(hang, leg, "rot", lambda p, sg=sg, ph=ph: (0, 0, sg * (40 + 3 * s(p, ph))), 8)
        cycle(hang, shin, "rot", lambda p, sg=sg, ph=ph: (0, 0, sg * (30 + 5 * s(p, ph + 0.15))), 8)
    cycle(hang, "cone", "rot", lambda p: (3 * s(p), 0, 3 * s(p, 0.25)), 8)
    hang.rot("dhead", [(0, 30, 0, 0), (4.0, 30, 0, 0)])
    front = (("dleg0_l", "dshin0_l", 1), ("dleg0_r", "dshin0_r", -1))

    def rear(clip, t0, t1, quiver=True):
        """The front pair lifts off the ground and spreads, poised (the profile lets them go during a wind-up)."""
        for leg, shin, sg in front:
            keys = [(0, 0, 0, 0), (t0, 0, sg * 15, sg * -38)]
            sk = [(0, 0, 0, 0), (t0, 0, 0, sg * -50)]
            if quiver:
                q = t0
                while q + 0.1 < t1:
                    keys.append((q + 0.05, 0, sg * 17, sg * -41))
                    keys.append((q + 0.1, 0, sg * 15, sg * -38))
                    q += 0.1
            keys.append((t1, 0, sg * 15, sg * -38))
            sk.append((t1, 0, 0, sg * -50))
            clip.rot(leg, keys)
            clip.rot(shin, sk)
    # Spike Drop: it rears high on its legs, front pair up, cone back; then crashes down cone-first and the cone whips
    sw = r.clip("slam_windup", 1.0)
    sw.pos("body", [(0, 0, 0, 0), (0.25, 0, -1, 0.5), (1.0, 0, 4, 1)])
    sw.rot("body", [(0, 0, 0, 0), (0.25, 3, 0, 0), (1.0, -20, 0, 0)])
    sw.rot("cone", [(0, 0, 0, 0), (1.0, -6, 0, 0)])
    sw.rot("dhead", [(0, 0, 0, 0), (1.0, 12, 0, 0)])
    rear(sw, 0.5, 1.0)
    ss = r.clip("slam_strike", 0.6)
    ss.pos("body", [(0, 0, 4, 1), (0.1, 0, -3, -1), (0.24, 0, 0.6, 0), (0.6, 0, 0, 0)])
    ss.rot("body", [(0, -20, 0, 0), (0.1, 15, 0, 0), (0.24, -3, 0, 0), (0.6, 0, 0, 0)])
    ss.scale("body", [(0, 1, 1, 1), (0.1, 1.12, 0.82, 1.12), (0.22, 0.96, 1.05, 0.96), (0.6, 1, 1, 1)])
    ss.rot("cone", [(0, -6, 0, 0), (0.13, 12, 0, 0), (0.3, -4, 0, 0), (0.6, 0, 0, 0)])
    for leg, shin, sg in front:
        ss.rot(leg, [(0, 0, sg * 15, sg * -38), (0.1, 0, sg * -5, sg * 12), (0.6, 0, 0, 0)])
        ss.rot(shin, [(0, 0, 0, sg * -50), (0.1, 0, 0, sg * -10), (0.6, 0, 0, 0)])
    # Lunge: it gathers back and low, the front pair rising and spreading, fangs wide; then throws itself forward and
    # the fangs snap shut, the body overshoots and settles
    lw = r.clip("lunge_windup", 0.6)
    lw.pos("body", [(0, 0, 0, 0), (0.4, 0, -2.5, 2), (0.6, 0, -2.5, 2.2)])
    lw.rot("body", [(0, 0, 0, 0), (0.4, -10, 0, 0), (0.6, -11, 0, 0)])
    lw.rot("cone", [(0, 0, 0, 0), (0.45, -5, 0, 0), (0.6, -5, 0, 0)])
    lw.rot("dhead", [(0, 0, 0, 0), (0.4, -8, 0, 0), (0.6, -8, 0, 0)])
    for nm, sg in fangs:
        lw.rot(nm, [(0, 0, 0, 0), (0.35, 0, 0, sg * 36), (0.6, 0, 0, sg * 38)])
    rear(lw, 0.35, 0.6)
    lsk = r.clip("lunge_strike", 0.5)
    lsk.pos("body", [(0, 0, -2.5, 2.2), (0.08, 0, 1, -4), (0.2, 0, -0.5, -1), (0.5, 0, 0, 0)])
    lsk.rot("body", [(0, -11, 0, 0), (0.08, 12, 0, 0), (0.22, -2, 0, 0), (0.5, 0, 0, 0)])
    lsk.rot("cone", [(0, -5, 0, 0), (0.1, 9, 0, 0), (0.26, -3, 0, 0), (0.5, 0, 0, 0)])
    lsk.rot("dhead", [(0, -8, 0, 0), (0.08, 10, 0, 0), (0.5, 0, 0, 0)])
    for nm, sg in fangs:
        lsk.rot(nm, [(0, 0, 0, sg * 38), (0.08, 0, 0, sg * -12), (0.16, 0, 0, sg * -8), (0.5, 0, 0, 0)])
    for leg, shin, sg in front:
        lsk.rot(leg, [(0, 0, sg * 15, sg * -38), (0.08, 0, sg * 25, sg * 5), (0.5, 0, 0, 0)])
        lsk.rot(shin, [(0, 0, 0, sg * -50), (0.08, 0, 0, sg * -30), (0.5, 0, 0, 0)])
    w = r.clip("windup", 0.6)
    w.pos("body", [(0, 0, 0, 0), (0.45, 0, -1.5, 1.5), (0.6, 0, -1.5, 1.6)])
    w.rot("body", [(0, 0, 0, 0), (0.45, -6, 0, 0), (0.6, -6, 0, 0)])
    for nm, sg in fangs:
        w.rot(nm, [(0, 0, 0, 0), (0.4, 0, 0, sg * 28), (0.6, 0, 0, sg * 30)])
    rear(w, 0.35, 0.6, quiver=False)
    st = r.clip("strike", 0.4)
    st.pos("body", [(0, 0, -1.5, 1.6), (0.08, 0, 0.5, -2.5), (0.2, 0, 0, -0.5), (0.4, 0, 0, 0)])
    st.rot("body", [(0, -6, 0, 0), (0.08, 9, 0, 0), (0.4, 0, 0, 0)])
    for nm, sg in fangs:
        st.rot(nm, [(0, 0, 0, sg * 30), (0.08, 0, 0, sg * -10), (0.4, 0, 0, 0)])
    # the bite: a flick back, a drive in head-down with the fangs snapping shut, a recoil
    bite = r.clip("bite", 0.4)
    for nm, sg in fangs:
        bite.rot(nm, [(0, 0, 0, 0), (0.07, 0, 0, sg * 32), (0.14, 0, 0, sg * -12), (0.22, 0, 0, sg * -6), (0.4, 0, 0, 0)])
    bite.pos("body", [(0, 0, 0, 0), (0.07, 0, 0.3, 0.8), (0.14, 0, -0.5, -2.5), (0.26, 0, 0, -0.6), (0.4, 0, 0, 0)])
    bite.rot("body", [(0, 0, 0, 0), (0.07, -5, 0, 0), (0.14, 8, 0, 0), (0.4, 0, 0, 0)])
    bite.rot("dhead", [(0, 0, 0, 0), (0.07, -10, 0, 0), (0.14, 14, 0, 0), (0.4, 0, 0, 0)])
    bite.rot("cone", [(0, 0, 0, 0), (0.12, -3, 0, 0), (0.22, 5, 0, 0), (0.4, 0, 0, 0)])
    # a blow: the body jerks up and back over its planted feet, fangs flare, the cone lags and swings back through
    hurt = r.clip("hurt", 0.4)
    hurt.pos("body", [(0, 0, 0, 0), (0.05, 0, 1.5, 1.5), (0.15, 0, -1, 0.5), (0.4, 0, 0, 0)])
    hurt.rot("body", [(0, 0, 0, 0), (0.05, -8, 0, 0), (0.18, 3, 0, 0), (0.4, 0, 0, 0)])
    hurt.rot("cone", [(0, 0, 0, 0), (0.1, 6, 0, 0), (0.22, -2, 0, 0), (0.4, 0, 0, 0)])
    for nm, sg in fangs:
        hurt.rot(nm, [(0, 0, 0, 0), (0.05, 0, 0, sg * 24), (0.4, 0, 0, 0)])
    for leg, shin, sg in L:
        hurt.rot(leg, [(0, 0, 0, 0), (0.06, 0, 0, sg * 15), (0.4, 0, 0, 0)])
    # the death: it rears with its legs flung out, drops onto its side, the legs curl up over it the way a harvestman's
    # do, and twitch one by one before they still
    death = r.clip("death", 1.0)
    death.pos("body", [(0, 0, 0, 0), (0.1, 0, 2, 0), (0.42, 0, -6, 0), (0.5, 0, -5.4, 0), (1.0, 0, -6, 0)])
    death.rot("body", [(0, 0, 0, 0), (0.1, -12, 0, 0), (0.45, 6, 0, 18), (1.0, 5, 0, 20)])
    death.rot("cone", [(0, 0, 0, 0), (0.12, -6, 0, 0), (0.5, 8, 0, 4), (0.65, 3, 0, 2), (1.0, 4, 0, 3)])
    death.rot("dhead", [(0, 0, 0, 0), (0.1, -15, 0, 0), (0.5, 20, 0, 0), (1.0, 18, 0, 0)])
    for nm, sg in fangs:
        death.rot(nm, [(0, 0, 0, 0), (0.1, 0, 0, sg * 30), (0.5, 0, 0, sg * 5), (0.72, 0, 0, sg * 14), (0.8, 0, 0, sg * 4), (1.0, 0, 0, sg * 4)])
    for k, (leg, shin, sg) in enumerate(L):
        tw = 0.55 + 0.07 * ((k * 5) % 6)  # each leg's last twitch at its own moment
        death.rot(leg, [(0, 0, 0, 0), (0.1, 0, 0, sg * -22), (0.45, 0, 0, sg * -50), (tw, 0, 0, sg * -54),
                        (tw + 0.05, 0, 0, sg * -44), (tw + 0.1, 0, 0, sg * -52), (1.0, 0, 0, sg * -54)])
        death.rot(shin, [(0, 0, 0, 0), (0.1, 0, 0, sg * -30), (0.45, 0, 0, sg * 55), (tw, 0, 0, sg * 62),
                         (tw + 0.05, 0, 0, sg * 40), (tw + 0.1, 0, 0, sg * 58), (1.0, 0, 0, sg * 64)])


def paint_drip_lurker(r, P):
    stone = Ramp("#2a1e16", "#3e2c1e", "#584028", "#725634", "#8e6e44", "#aa8a5a", "#c4a676")
    body = Ramp("#120e0c", "#1e1814", "#2c241c", "#3a3024")
    leg = Ramp("#14100c", "#221a14", "#32261c", "#463626")
    # the cone: dripstone with horizontal drip rings, lit on top, cracks of ember light
    rings = lambda f, i, j: (0.7 if j % 3 == 0 else -0.3 if j % 3 == 2 else 0) + (0.4 if (_h(f.cube.name, i // 2) % 5 == 0) else 0)  # noqa: E731
    for k, nm in enumerate(("cone0", "cone1", "cone2", "cone3", "cone4", "drip0")):
        P.paint(nm, stone, 4.0 - k * 0.3, rings, rim=0.4)
    for nm in ("cone0", "cone1", "cone2"):
        for fname in SIDES:
            f = P.face(nm, fname)
            g = P.face(nm, fname, glow=True)
            x = _h(nm, fname) % max(1, f.w - 2) + 1
            for j in range(f.h):
                f.set(x + (j % 2), j, hexc("#ff7a3a"))
                g.set(x + (j % 2), j, hexc("#ff8a4a", 150))
    # a wet glint on the cone's point (and the drips' points): hanging from a dark roof it is the one pale fleck a
    # player looking up has a fair chance to spot
    for fname in ("top",) + SIDES:
        g = P.face("cone4", fname, glow=True)
        for i, j in g.each():
            if fname == "top" or j == 0:
                g.set(i, j, hexc("#e4f0f4", 170 if fname == "top" else 110))
    for fname in ("top",) + SIDES:
        g = P.face("drip0", fname, glow=True)
        for i, j in g.each():
            if (fname == "top" and (i + j) % 2 == 0) or (fname != "top" and j == 0 and i % 2 == 0):
                g.set(i, j, hexc("#d8e8ee", 80))
    P.paint("dbody", body, 2.6, p_blotch("lurk", 4, 0.4))
    P.paint("dhead", body, 2.8)
    for fname in ("north", "west", "east"):
        f = P.face("dhead", fname)
        g = P.face("dhead", fname, glow=True)
        for e in ((0, 0), (f.w - 1, 0), (1, 0), (f.w - 2, 0)) if fname == "north" else ((0, 0),):
            f.set(e[0], e[1], hexc("#ffb05a"))
            g.set(e[0], e[1], hexc("#ffb05a", 220))
    P.paint("fang_l", Ramp("#6a5a46", "#9c8a6c", "#c8b896"), 1.6, lambda f, i, j: 1.0 if j == f.h - 1 else 0)
    for nm in ("dthigh0_l", "dshin0_l"):
        P.paint(nm, leg, 2.4, lambda f, i, j: (1.0 if f.name == "top" else 0) + (0.8 if (i % 4 == 0 and nm == "dshin0_l") else 0))
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# Hushling: a pale, eyeless, hunched hunter with ear fans as big as its head, long arms and a wide mouth; dark sculk
# ribs glow faintly down its back. It lopes on all fours and freezes, ears flared, at every sound.

def hushling():
    r = Rig("hushling", (64, 64))
    r.walk_stride, r.run_stride, r.run_speed, r.shadow = 1.2, 2.4, (0.16, 0.3), 0.45
    body = r.bone("body", (0, 15, 1), (25, 0, 0))
    body.cube("hbody", -3, -3, -4, 6, 5, 7)
    body.cube("hribs", -2.5, -3.5, -3, 5, 1, 6)
    head = body.bone("hhead", (0, -2, -4), (-25, 0, 0))
    head.cube("hskull", -3, -3, -4, 6, 5, 5)
    jaw = head.bone("hjaw", (0, 1.5, -1))
    jaw.cube("hjaw", -2.5, 0, -3, 5, 1, 3)
    for side, nm in ((1, "l"), (-1, "r")):
        ear = head.bone("hear_" + nm, (side * 3, -2, -1), (0, side * -60, side * -35))
        ear.cube("hear_" + nm, 0, -6, -1, 0, 8, 7, mirror=side < 0, share=None if side > 0 else "hear_l")
    for side, nm in ((1, "l"), (-1, "r")):
        mir = side < 0

        def sh(n, side=side):
            return None if side > 0 else n
        arm = body.bone("harm_" + nm, (side * 3, 0, -3), (-25, 0, side * -8))
        arm.cube("harm_" + nm, -1, 0, -1, 2, 6, 2, mirror=mir, share=sh("harm_l"))
        fore = arm.bone("hfore_" + nm, (0, 6, 0), (20, 0, 0))
        fore.cube("hfore_" + nm, -0.5, 0, -0.5, 1, 6, 1, mirror=mir, share=sh("hfore_l"))
        fore.bone("hhand_" + nm, (0, 6, 0), (-30, 0, 0)).cube("hhand_" + nm, -1.5, 0, -2, 3, 0, 3, mirror=mir, share=sh("hhand_l"))
        thigh = body.bone("hthigh_" + nm, (side * 2.5, 2, 2), (-60, 0, 0))
        thigh.cube("hthigh2_" + nm, -1, -0.5, -1, 2, 5, 2, mirror=mir, share=sh("hthigh2_l"))
        thigh.bone("hshin_" + nm, (0, 4.5, 0), (90, 0, 0)).cube("hshin2_" + nm, -0.5, 0, -0.5, 1, 4, 1, mirror=mir, share=sh("hshin2_l"))
    r.look = [("hhead", 0.7, 0.5)]
    r.moves = {"Lunge": "lunge"}
    r.pack()
    hushling_clips(r)
    return r


def hushling_clips(r):
    idle = r.clip("idle", 3.2, loop=True)
    # listening for you: the ears swivel separately, the head tilts, the ribs breathe
    for nm, sg in (("hear_l", 1), ("hear_r", -1)):
        idle.rot(nm, [(0, 0, 0, 0), (0.8, 0, sg * 25, 0), (1.4, 0, sg * 25, 0), (1.8, 0, sg * -10, 0), (2.6, 0, sg * -10, 0), (3.2, 0, 0, 0)])
    idle.rot("hhead", [(0, 0, 0, 0), (1.0, 0, 0, 14), (2.0, 0, 0, -10), (3.2, 0, 0, 0)])
    cycle(idle, "body", "scale", lambda p: (1 + 0.03 * s(p * 2), 1 + 0.04 * s(p * 2), 1), 8)
    walk = r.clip("walk", 0.9, loop=True)
    # a lope: the long arms reach, the haunches push, the head stays low and level
    for nm, ph in (("harm_l", 0.0), ("harm_r", 0.5)):
        cycle(walk, nm, "rot", lambda p, ph=ph: (35 * s(p, ph), 0, 0), 8)
        cycle(walk, nm.replace("harm", "hfore"), "rot", lambda p, ph=ph: (-25 * max(0.0, c(p, ph)), 0, 0), 8)
    for nm, ph in (("hthigh_l", 0.25), ("hthigh_r", 0.75)):
        cycle(walk, nm, "rot", lambda p, ph=ph: (30 * s(p, ph), 0, 0), 8)
    cycle(walk, "body", "pos", lambda p: (0, 0.6 * abs(s(p * 2)), 0), 8)
    cycle(walk, "hhead", "rot", lambda p: (-4 * s(p * 2), 0, 0), 8)
    run = r.clip("run", 0.6, loop=True)
    for nm, ph in (("harm_l", 0.0), ("harm_r", 0.1)):
        cycle(run, nm, "rot", lambda p, ph=ph: (50 * s(p, ph), 0, 0), 8)
    for nm, ph in (("hthigh_l", 0.5), ("hthigh_r", 0.6)):
        cycle(run, nm, "rot", lambda p, ph=ph: (45 * s(p, ph), 0, 0), 8)
    cycle(run, "body", "rot", lambda p: (8 * s(p, 0.25), 0, 0), 8)
    cycle(run, "body", "pos", lambda p: (0, 1.2 * abs(s(p)), 0), 8)
    listen = r.clip("listen", 1.0, loop=True)
    # a sound: it freezes, ears flared wide and forward, mouth a little open, head cocked
    for nm, sg in (("hear_l", 1), ("hear_r", -1)):
        listen.rot(nm, [(0, -10, sg * 40, sg * -20), (1.0, -10, sg * 40, sg * -20)])
        listen.scale(nm, [(0, 1.15, 1.15, 1.15), (1.0, 1.15, 1.15, 1.15)])
    listen.rot("hhead", [(0, -10, 0, 12), (1.0, -10, 0, 12)])
    listen.rot("hjaw", [(0, 12, 0, 0), (1.0, 12, 0, 0)])
    lw = r.clip("lunge_windup", 0.6)
    lw.pos("body", [(0, 0, 0, 0), (0.6, 0, -2, 1)])
    lw.rot("hjaw", [(0, 0, 0, 0), (0.6, 35, 0, 0)])
    for nm, sg in (("hear_l", 1), ("hear_r", -1)):
        lw.rot(nm, [(0, 0, 0, 0), (0.6, 30, sg * -20, 0)])
    lsk = r.clip("lunge_strike", 0.6)
    lsk.pos("body", [(0, 0, -2, 1), (0.1, 0, 1, -3), (0.6, 0, 0, 0)])
    for nm in ("harm_l", "harm_r"):
        lsk.rot(nm, [(0, 0, 0, 0), (0.1, -70, 0, 0), (0.6, 0, 0, 0)])
    lsk.rot("hjaw", [(0, 35, 0, 0), (0.15, 0, 0, 0)])
    w = r.clip("windup", 0.5)
    w.rot("hjaw", [(0, 0, 0, 0), (0.5, 30, 0, 0)])
    st = r.clip("strike", 0.4)
    st.rot("hjaw", [(0, 30, 0, 0), (0.1, 0, 0, 0)])
    st.rot("hhead", [(0, 0, 0, 0), (0.1, 15, 0, 0), (0.4, 0, 0, 0)])
    bite = r.clip("bite", 0.4)
    bite.rot("hjaw", [(0, 0, 0, 0), (0.1, 35, 0, 0), (0.2, 0, 0, 0)])
    bite.rot("hhead", [(0, 0, 0, 0), (0.1, -10, 0, 0), (0.2, 12, 0, 0), (0.4, 0, 0, 0)])
    hurt = r.clip("hurt", 0.4)
    for nm, sg in (("hear_l", 1), ("hear_r", -1)):
        hurt.rot(nm, [(0, 0, 0, 0), (0.06, 40, sg * 30, 0), (0.4, 0, 0, 0)])
    hurt.rot("body", [(0, 0, 0, 0), (0.06, -8, 0, 8), (0.4, 0, 0, 0)])
    death = r.clip("death", 1.2)
    death.rot("body", [(0, 0, 0, 0), (0.5, -20, 0, 10), (1.2, 0, 0, 90)])
    death.pos("body", [(0, 0, 0, 0), (1.2, 0, -5, 0)])
    for nm, sg in (("hear_l", 1), ("hear_r", -1)):
        death.rot(nm, [(0, 0, 0, 0), (1.2, 50, sg * 20, 0)])


def paint_hushling(r, P):
    pale = Ramp("#7a7470", "#948e88", "#aea8a0", "#c8c2b8", "#e0dbd0", "#f2eee4")
    pink = Ramp("#7a4a50", "#9a6068", "#b87a82", "#d49aa0")
    sculk = Ramp("#04161a", "#082228", "#0c3036", "#124048")
    waxy = combine(p_blotch("hush", 5, 0.35), lambda f, i, j: -0.4 if f.name in SIDES and j == f.h - 1 else 0)
    for nm in ("hbody", "hskull", "harm_l", "hfore_l", "hthigh2_l", "hshin2_l"):
        P.paint(nm, pale, 3.6, waxy)
    # no eyes: two faint pits where eyes would be; a wide dark mouth with a few pale teeth
    f = P.face("hskull", "north")
    for x in (1, f.w - 2):
        f.set(x, 1, pale.at(1.8))
    for i in range(f.w):
        f.set(i, f.h - 1, hexc("#2a0e10"))
    P.paint("hjaw", pale, 3.2)
    f = P.face("hjaw", "top")
    for i, j in f.each():
        f.set(i, j, hexc("#4a1418") if j > 0 else (hexc("#e8e2d4") if i % 2 == 0 else hexc("#4a1418")))
    # the sculk ribs: dark teal plates down the back with a cold glow in the seams
    P.paint("hribs", sculk, 2.4)
    for fname in ("top", "west", "east"):
        f = P.face("hribs", fname)
        g = P.face("hribs", fname, glow=True)
        for i, j in f.each():
            seam = (i % 2 == 1) if fname == "top" else (i % 2 == 0)
            if seam:
                f.set(i, j, hexc("#1ad0c0"))
                g.set(i, j, hexc("#3ae8d8", 200))
    for fname in ("west", "east"):
        f = P.face("hear_l", fname)
        g = P.face("hear_l", fname, glow=True)
        for i, j in f.each():
            edge = i in (0, f.w - 1) or j in (0, f.h - 1)
            cut = (j == 0 and i in (0, f.w - 1)) or (j == f.h - 1 and i == f.w - 1)
            if cut:
                f.set(i, j, (0, 0, 0, 0))
                continue
            vein = (i == f.w // 2) or (j == i + 1)
            f.set(i, j, pale.at(3.4) if edge else pink.at(2.6 if vein else 1.8)[:3] + (235,))
            if vein and not edge:
                g.set(i, j, hexc("#3ae8d8", 60))
    for fname in ("top", "bottom"):
        f = P.face("hhand_l", fname)
        for i, j in f.each():
            f.set(i, j, pale.at(2.6) if i != 1 or j == 0 else pale.at(1.6))
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# The Thousandfoot: a centipede as long as a cave, plated in dark chitin with amethyst spikes growing from its
# plates, amber legs in a ripple down its sides, hooked mandibles and long antennae. The middle segment is the root so
# the front can rear into an S and coil (clip values are offsets from the rest pose).

SEGMENTS = 7


def thousandfoot():
    r = Rig("thousandfoot", (256, 128))
    r.walk_stride, r.run_stride, r.run_speed, r.shadow = 2.6, 5.0, (0.16, 0.34), 1.4
    mid = r.bone("seg3", (0, 15.5, 2))
    mid.cube("seg3", -6, -4, -3.5, 12, 7, 7)
    chain_f, chain_b = ["seg3"], ["seg3"]
    prev = mid
    for k in (2, 1, 0):
        b = prev.bone(f"seg{k}", (0, 0, -7))
        b.cube(f"seg{k}", -6, -4, -3.5, 12, 7, 7, share="seg3")
        chain_f.append(f"seg{k}")
        prev = b
    head = prev.bone("thead", (0, 0, -3.5))
    head.cube("thead", -5, -4, -7, 10, 7, 7)
    prev = mid
    for k in (4, 5, 6):
        w = 12 - (k - 3) * 2
        b = prev.bone(f"seg{k}", (0, 0, 7))
        b.cube(f"seg{k}", -w / 2, -4 + (k - 3) * 0.5, -3.5, w, 7 - (k - 3), 7)
        chain_b.append(f"seg{k}")
        prev = b
    tail = prev.bone("ttail", (0, 0, 3.5))
    tail.cube("ttail", -2.5, -1.5, 0, 5, 3, 4)
    for side, nm in ((1, "l"), (-1, "r")):
        tail.bone("prong_" + nm, (side * 2, 0, 4), (0, side * -25, 0)).cube("prong_" + nm, -0.5, -0.5, 0, 1, 1, 6, mirror=side < 0, share=None if side > 0 else "prong_l")
        mand = head.bone("mand_" + nm, (side * 3, 2, -7), (0, side * 20, 0))
        mand.cube("mand_" + nm, -1, -1, -6, 2, 2, 6, mirror=side < 0, share=None if side > 0 else "mand_l")
        mand.bone("mandtip_" + nm, (0, 0, -6), (0, side * 60, 0)).cube("mandtip_" + nm, -0.5, -0.5, -4, 1, 1, 4, mirror=side < 0, share=None if side > 0 else "mandtip_l")
        ant = head.bone("tant_" + nm, (side * 2.5, -4, -6), (-35, side * -30, 0))
        ant.cube("tant_" + nm, -0.5, -0.5, -8, 1, 1, 8, mirror=side < 0, share=None if side > 0 else "tant_l")
        ant.bone("tant2_" + nm, (0, 0, -8), (20, 0, 0)).cube("tant2_" + nm, -0.5, -0.5, -7, 1, 1, 7, mirror=side < 0, share=None if side > 0 else "tant2_l")
    # a pair of legs on every segment, and crystal spikes on the plates
    for k in range(SEGMENTS):
        seg = r.by_name[f"seg{k}"]
        w = 12 - max(0, k - 3) * 2
        for side, nm in ((1, "l"), (-1, "r")):
            leg = seg.bone(f"tleg{k}_" + nm, (side * w / 2, 2, 0), (0, side * (k - 3) * -6, side * -25))
            leg.cube(f"tleg{k}_" + nm, 0 if side > 0 else -7, -0.5, -0.5, 7, 1, 1, mirror=side < 0, share=None if (k == 0 and side > 0) else "tleg0_l")
            leg.bone(f"tfoot{k}_" + nm, (side * 7, 0, 0), (0, 0, side * 95)).cube(
                f"tfoot{k}_" + nm, 0 if side > 0 else -7, -0.5, -0.5, 7, 1, 1, mirror=side < 0, share=None if (k == 0 and side > 0) else "tfoot0_l")
        if k % 2 == 1 or k == 0:
            for side in (1, -1):
                seg.bone(f"spike{k}_{'l' if side > 0 else 'r'}", (side * 3, -4, 0), (-10, 0, side * -15)).cube(
                    f"spike{k}_{'l' if side > 0 else 'r'}", -1, -5, -1, 2, 5, 2, share=None if (k == 0 and side > 0) else "spike0_l")
    r.look = [("thead", 0.6, 0.5)]
    r.moves = {"Coil Strike": "coil", "Mandible Snap": "snap", "Burrow": "burrow", "Venom Spit": "spit", "Thousand Legs": "roar"}
    r.pack()
    thousandfoot_clips(r)
    return r


def _legs_ripple(clip, swing, lift, speed=1.0):
    # the leg wave runs from the head back: each pair a fifth of a cycle behind the one in front
    for k in range(SEGMENTS):
        for side in (1, -1):
            nm = f"tleg{k}_" + ("l" if side > 0 else "r")
            ph = k * 0.2 + (0.5 if side < 0 else 0)
            cycle(clip, nm, "rot", lambda p, ph=ph, side=side: (0, side * swing * s(p * speed, ph), side * -lift * max(0.0, c(p * speed, ph))), 8)


def thousandfoot_clips(r):
    idle = r.clip("idle", 4.0, loop=True)
    for nm, sg in (("tant_l", 1), ("tant_r", -1)):
        cycle(idle, nm, "rot", lambda p, sg=sg: (10 * s(p, 0.1 if sg > 0 else 0.45), sg * 14 * s(p, 0.25), 0), 8)
        cycle(idle, nm.replace("tant", "tant2"), "rot", lambda p, sg=sg: (16 * s(p, 0.3), 0, 0), 8)
    cycle(idle, "seg3", "rot", lambda p: (0, 3 * s(p), 0), 8)
    for k in range(SEGMENTS):
        if k != 3:
            cycle(idle, f"seg{k}", "rot", lambda p, k=k: (0, 4 * s(p, k * 0.1), 0), 8)
    _legs_ripple(idle, 4, 3, 1.0)
    for nm, sg in (("mand_l", 1), ("mand_r", -1)):
        idle.rot(nm, [(0, 0, 0, 0), (2.0, 0, 0, 0), (2.1, 0, sg * -15, 0), (2.2, 0, 0, 0), (2.35, 0, sg * -15, 0), (2.5, 0, 0, 0), (4.0, 0, 0, 0)])
    walk = r.clip("walk", 1.0, loop=True)
    _legs_ripple(walk, 22, 14, 1.0)
    wave(walk, ["thead", "seg0", "seg1", "seg2", "seg3", "seg4", "seg5", "seg6", "ttail"], "y", 0, lag=0.11, amps=[-6, 6, 7, 7, 4, 7, 8, 9, 10])
    run = r.clip("run", 0.6, loop=True)
    _legs_ripple(run, 30, 18, 1.0)
    wave(run, ["thead", "seg0", "seg1", "seg2", "seg3", "seg4", "seg5", "seg6", "ttail"], "y", 0, lag=0.11, amps=[-8, 9, 10, 10, 6, 10, 11, 12, 14])
    # Coil Strike (signature): the front third rears into a tall S, mandibles spread wide... then it strikes down
    cw = r.clip("coil_windup", 1.4)
    for nm, a in (("seg2", -40), ("seg1", -35), ("seg0", 10), ("thead", 45)):
        cw.rot(nm, [(0, 0, 0, 0), (1.4, a, 0, 0)])
    for nm, sg in (("mand_l", 1), ("mand_r", -1)):
        cw.rot(nm, [(0, 0, 0, 0), (1.4, 0, sg * 35, 0)])
    for k in range(3):
        for side, s_ in (("l", 1), ("r", -1)):
            cw.rot(f"tleg{k}_{side}", [(0, 0, 0, 0), (1.4, 0, s_ * -30, s_ * 40)])
    cs = r.clip("coil_strike", 0.9)
    for nm, a, b in (("seg2", -40, 25), ("seg1", -35, 20), ("seg0", 10, 10), ("thead", 45, -15)):
        cs.rot(nm, [(0, a, 0, 0), (0.15, b, 0, 0), (0.9, 0, 0, 0)])
    cs.pos("seg3", [(0, 0, 0, 0), (0.15, 0, 0, -6), (0.9, 0, 0, 0)])
    for nm, sg in (("mand_l", 1), ("mand_r", -1)):
        cs.rot(nm, [(0, 0, sg * 35, 0), (0.15, 0, sg * -10, 0), (0.9, 0, 0, 0)])
    # Mandible Snap: the head lifts a little, the mandibles open wide, then snap shut
    sw = r.clip("snap_windup", 0.7)
    sw.rot("thead", [(0, 0, 0, 0), (0.7, -15, 0, 0)])
    for nm, sg in (("mand_l", 1), ("mand_r", -1)):
        sw.rot(nm, [(0, 0, 0, 0), (0.7, 0, sg * 45, 0)])
    ss = r.clip("snap_strike", 0.5)
    ss.rot("thead", [(0, -15, 0, 0), (0.08, 10, 0, 0), (0.5, 0, 0, 0)])
    for nm, sg in (("mand_l", 1), ("mand_r", -1)):
        ss.rot(nm, [(0, 0, sg * 45, 0), (0.08, 0, sg * -15, 0), (0.5, 0, 0, 0)])
    # Burrow: it noses down into the rock and the body follows; it bursts up beside you
    bw = r.clip("burrow_windup", 1.0)
    for nm, a in (("thead", 40), ("seg0", 20), ("seg1", 10)):
        bw.rot(nm, [(0, 0, 0, 0), (1.0, a, 0, 0)])
    bw.pos("seg3", [(0, 0, 0, 0), (1.0, 0, -4, 0)])
    bs = r.clip("burrow_strike", 0.9)
    for nm, a in (("thead", -50), ("seg0", -30), ("seg1", -20)):
        bs.rot(nm, [(0, 0, 0, 0), (0.15, a, 0, 0), (0.9, 0, 0, 0)])
    bs.pos("seg3", [(0, 0, -4, 0), (0.15, 0, 4, 0), (0.9, 0, 0, 0)])
    # Venom Spit: the head draws back, the mandibles part, a jerk forward
    pw = r.clip("spit_windup", 0.8)
    pw.rot("thead", [(0, 0, 0, 0), (0.8, -25, 0, 0)])
    pw.rot("seg0", [(0, 0, 0, 0), (0.8, -15, 0, 0)])
    for nm, sg in (("mand_l", 1), ("mand_r", -1)):
        pw.rot(nm, [(0, 0, 0, 0), (0.8, 0, sg * 25, 0)])
    ps = r.clip("spit_strike", 0.5)
    ps.rot("thead", [(0, -25, 0, 0), (0.1, 15, 0, 0), (0.5, 0, 0, 0)])
    ps.rot("seg0", [(0, -15, 0, 0), (0.1, 5, 0, 0), (0.5, 0, 0, 0)])
    w = r.clip("windup", 0.7)
    w.rot("thead", [(0, 0, 0, 0), (0.7, -12, 0, 0)])
    w.rot("seg0", [(0, 0, 0, 0), (0.7, -10, 0, 0)])
    st = r.clip("strike", 0.5)
    st.rot("thead", [(0, -12, 0, 0), (0.1, 14, 0, 0), (0.5, 0, 0, 0)])
    st.rot("seg0", [(0, -10, 0, 0), (0.1, 8, 0, 0), (0.5, 0, 0, 0)])
    bite = r.clip("bite", 0.45)
    for nm, sg in (("mand_l", 1), ("mand_r", -1)):
        bite.rot(nm, [(0, 0, 0, 0), (0.1, 0, sg * 40, 0), (0.2, 0, sg * -10, 0), (0.45, 0, 0, 0)])
    bite.rot("thead", [(0, 0, 0, 0), (0.15, 10, 0, 0), (0.45, 0, 0, 0)])
    roar = r.clip("roar", 1.8)
    # it rears its whole front, mandibles spread, legs flailing
    for nm, a in (("seg2", -30), ("seg1", -30), ("seg0", -15), ("thead", 20)):
        roar.rot(nm, [(0, 0, 0, 0), (0.4, a, 0, 0), (1.4, a, 0, 0), (1.8, 0, 0, 0)])
    for nm, sg in (("mand_l", 1), ("mand_r", -1)):
        roar.rot(nm, [(0, 0, 0, 0), (0.4, 0, sg * 50, 0), (1.4, 0, sg * 50, 0), (1.8, 0, 0, 0)])
    for k in range(4):
        for side, s_ in (("l", 1), ("r", -1)):
            roar.rot(f"tleg{k}_{side}", [(0, 0, 0, 0), (0.5, 0, s_ * 20, s_ * 30), (0.7, 0, s_ * -20, s_ * 10), (0.9, 0, s_ * 20, s_ * 30),
                                         (1.1, 0, s_ * -20, s_ * 10), (1.3, 0, s_ * 20, s_ * 30), (1.8, 0, 0, 0)])
    warn = r.clip("warn", 1.2)
    for nm, a in (("seg1", -15), ("seg0", -15), ("thead", 10)):
        warn.rot(nm, [(0, 0, 0, 0), (0.3, a, 0, 0), (0.9, a, 0, 0), (1.2, 0, 0, 0)])
    for nm, sg in (("mand_l", 1), ("mand_r", -1)):
        warn.rot(nm, [(0, 0, 0, 0), (0.3, 0, sg * 35, 0), (0.45, 0, 0, 0), (0.6, 0, sg * 35, 0), (0.75, 0, 0, 0), (1.2, 0, 0, 0)])
    hurt = r.clip("hurt", 0.45)
    hurt.rot("seg3", [(0, 0, 0, 0), (0.06, 0, 10, 6), (0.25, 0, -4, -2), (0.45, 0, 0, 0)])
    hurt.rot("thead", [(0, 0, 0, 0), (0.06, -15, 0, 0), (0.45, 0, 0, 0)])
    death = r.clip("death", 1.8)
    # it rolls onto its side and curls up, legs folding in
    death.rot("seg3", [(0, 0, 0, 0), (0.6, 0, 0, 40), (1.8, 0, 0, 90)])
    death.pos("seg3", [(0, 0, 0, 0), (1.8, 0, -2, 0)])
    for k, a in ((2, 25), (1, 30), (0, 35), (4, -25), (5, -30), (6, -35)):
        death.rot(f"seg{k}", [(0, 0, 0, 0), (1.8, 0, a, 0)])
    for k in range(SEGMENTS):
        for side, s_ in (("l", 1), ("r", -1)):
            death.rot(f"tfoot{k}_{side}", [(0, 0, 0, 0), (1.8, 0, 0, s_ * 40)])


def paint_thousandfoot(r, P):
    chitin = Ramp("#0c0812", "#16101e", "#221a2c", "#30263e", "#403452", "#544468")
    edge = Ramp("#3a2a4a", "#56406c", "#74588e")
    amber = Ramp("#4a2408", "#6e3810", "#94501a", "#bc6c26", "#e08e3a")
    amethyst = Ramp("#3a1e5a", "#5a2e8a", "#7c48b8", "#a070e0", "#c8a4ff", "#ecdcff")
    plates = combine(p_plates(5, 5, 0.8, stagger=False), p_blotch("thousand", 6, 0.3))
    for nm in ("seg3", "seg4", "seg5", "seg6", "thead", "ttail"):
        P.paint(nm, chitin, 2.8, plates, rim=0.5)
    # each segment's front and back edge a lighter band (the plates overlap)
    for nm in ("seg3", "seg4", "seg5", "seg6"):
        for fname in ("top", "west", "east"):
            f = P.face(nm, fname)
            for i, j in f.each():
                if (fname == "top" and j in (0, f.h - 1)) or (fname != "top" and i in (0, f.w - 1)):
                    f.set(i, j, edge.at(1.6))
    # the head: a cluster of small glowing eyes, darker plates
    f = P.face("thead", "north")
    g = P.face("thead", "north", glow=True)
    for x, y in ((1, 1), (2, 1), (f.w - 2, 1), (f.w - 3, 1), (1, 2), (f.w - 2, 2)):
        f.set(x, y, hexc("#e0c8ff"))
        g.set(x, y, hexc("#efe2ff", 255))
    for nm in ("mand_l", "mandtip_l", "prong_l"):
        P.paint(nm, amber, 3.0, lambda f, i, j: 1.0 if f.name == "top" else 0)
    for nm in ("tleg0_l", "tfoot0_l", "tant_l", "tant2_l"):
        P.paint(nm, amber, 2.6, lambda f, i, j: -1.2 if (i // 2) % 2 == 1 and f.name in ("north", "south", "top", "bottom") else 0)
    P.paint("spike0_l", amethyst, 3.6, lambda f, i, j: (1.4 if j == 0 else 0) - (0.8 if f.name in ("east", "south") else 0))
    # night: the crystal back shines (every face of every spike, 190 at the root to 255 at the point) and a seam of
    # crystal light runs along the top of every plate
    for fname in ("top", "bottom", "north", "west", "east", "south"):
        gg = P.face("spike0_l", fname, glow=True)
        for i, j in gg.each():
            t = 1.0 if fname == "top" else 0.0 if fname == "bottom" else 1.0 - j / max(1, gg.h - 1)
            gg.set(i, j, lerpc(hexc("#b48cf0"), hexc("#f0e4ff"), t)[:3] + (int(190 + 65 * t),))
    for nm in ("seg3", "seg4", "seg5", "seg6", "ttail"):
        f = P.face(nm, "top")
        g = P.face(nm, "top", glow=True)
        mid = f.w // 2
        for j in range(f.h):
            for i in (mid - 1, mid):
                f.set(i, j, amethyst.at(2.6 + (0.8 if j % 2 == 0 else 0)))
                g.set(i, j, hexc("#c8a4ff", 190 if j % 2 == 0 else 140))
    return P.skin, P.glow


# =====================================================================================================================
# The seep heart (W5, Between the Gates): the Deep grown up through the rock at a seep's innermost chamber. A mound of
# deepslate fused with black-violet growth, roots running out over the floor towards the walls, Deepglass spires, two
# hooked lashing arms, and on a twisted trunk the core: a faceted crystal that beats with violet light inside five
# Deepglass plates. Asleep the plates are shut round the core like a bud (the "sleep" hold); awake they stand open.
# It never walks: the crown turns to face you (the look bone), the body rises a little, the arms and roots lash.
# =====================================================================================================================

SEEP_PLATES, SEEP_ROOTS = 5, 6
DEEPGLASS = ("#07050c", "#120a20", "#24133f", "#3e2470", "#6a45b0", "#b48cff", "#eadcff")  # delve.py's Deepglass ramp
# spires of Deepglass out of the mound: (angle round from the front, radius, lean outward, height, width)
SEEP_SPIRES = [(24, 9, 22, 16, 3), (100, 10, 32, 12, 3), (158, 9, 30, 15, 4), (204, 11, 40, 9, 2), (232, 10, 28, 14, 3), (300, 9, 26, 10, 2)]
# rock lumps on the mound: (x, z, yaw, w, h, d)
SEEP_LUMPS = [(-8, 9, 25, 7, 3, 5), (10, -6, -40, 6, 4, 6), (-11, -4, 70, 5, 3, 7), (6, 11, 10, 6, 2, 4)]
SEEP_ROOT_ANGLES = [4, 58, 124, 177, 243, 296]
SEEP_ROOT_BEND = (-14, 9, -6, 15, -11, 8)
SEEP_MOVES = {"Deep Shards": "shards", "Rootfall": "roots", "Root Lash": "lash", "Deep Pulse": "pulse", "Call of the Deep": "call",
              "Shard Storm": "storm", "Heartburst": "burst"}


def _out(a, r):
    """Model-space x, z of a point `r` px out from the middle at `a` degrees round from the front (-z)."""
    rad = math.radians(a)
    return r * math.sin(rad), -r * math.cos(rad)


def seep_heart():
    r = Rig("seep_heart", (256, 128))
    r.walk_stride, r.run_stride, r.run_speed, r.shadow = 2.0, 4.0, (0.4, 0.8), 1.3
    base = r.bone("base", (0, 24, 0))
    base.cube("mound", -13, -4, -13, 26, 4, 26)
    base.bone("mound_top", (0, -4, 0), (0, 45, 0)).cube("mound_top", -9, -3, -9, 18, 3, 18)
    for k, (x, z, yaw, w, h, d) in enumerate(SEEP_LUMPS):
        base.bone(f"lump{k}", (x, -4, z), (0, yaw, 0)).cube(f"lump{k}", -w / 2, -h, -d / 2, w, h, d)
    # the roots, along the floor and out towards the walls (pivots on the floor line, so a lift raises the far end)
    for i, a in enumerate(SEEP_ROOT_ANGLES):
        x, z = _out(a, 10)
        bend = SEEP_ROOT_BEND[i]
        ra = base.bone(f"rt{i}a", (x, 0, z), (0, -a, 0))
        ra.cube(f"rt{i}a", -3, -3, -11, 6, 3, 11, share=None if i == 0 else "rt0a")
        rb = ra.bone(f"rt{i}b", (0, 0, -11), (0, bend, 0))
        rb.cube(f"rt{i}b", -2.5, -2, -10, 5, 2, 10, share=None if i == 0 else "rt0b")
        rc = rb.bone(f"rt{i}c", (0, 0, -10), (0, -bend * 0.9, 0))
        rc.cube(f"rt{i}c", -1.5, -2, -10, 3, 2, 10, share=None if i == 0 else "rt0c")
        # a rootlet forking off the middle link, out to the side it bends away from
        side = 1 if bend < 0 else -1
        rb.bone(f"rtf{i}", (side * 2, 0, -5), (0, side * -38, 0)).cube(f"rtf{i}", -1, -2, -8, 2, 2, 8, share=None if i == 0 else "rtf0")
    # Deepglass spires, leaning out of the mound (each turned 45 degrees on its own axis: a diamond in section)
    for k, (a, rad, lean, h, w) in enumerate(SEEP_SPIRES):
        x, z = _out(a, rad)
        sx = base.bone(f"spire{k}", (x, -3, z), (lean, -a, 0)).bone(f"spirex{k}", (0, 0, 0), (0, 45, 0))
        sx.cube(f"spire{k}", -w / 2, -h, -w / 2, w, h, w)
        tw, th = max(1, w - 2), max(2, h // 3)
        sx.bone(f"stip{k}", (0, -h, 0)).cube(f"stip{k}", -tw / 2, -th, -tw / 2, tw, th, tw)
    # two hooked arms of growth either side of the front: they lash, plunge into the floor, rise to the walls
    for side, nm in ((1, "l"), (-1, "r")):
        x, z = _out(side * 34, 8)
        l1 = base.bone("lash_" + nm, (x, -4, z), (16, side * -34, side * 16))
        l1.cube("lash_" + nm, -2, -10, -2, 4, 10, 4, mirror=side < 0, share=None if side > 0 else "lash_l")
        l2 = l1.bone("lash2_" + nm, (0, -10, 0), (30, 0, 0))
        l2.cube("lash2_" + nm, -1.5, -9, -1.5, 3, 9, 3, mirror=side < 0, share=None if side > 0 else "lash2_l")
        l3 = l2.bone("lash3_" + nm, (0, -9, 0), (38, 0, 0))
        l3.cube("lash3_" + nm, -1, -8, -1, 2, 8, 2, mirror=side < 0, share=None if side > 0 else "lash3_l")
        l3.bone("barb_" + nm, (0, -8, 0), (55, 0, 0)).cube("barb_" + nm, -0.5, -4, -0.5, 1, 4, 1, mirror=side < 0, share=None if side > 0 else "barb_l")
    # the trunk: a core of growth sunk deep in the mound (so the body can rise) wound with four strands of glass
    body = base.bone("body", (0, -6, 0))
    body.cube("trunk", -4, -14, -4, 8, 18, 8)
    for k in range(4):
        a = 45 + 90 * k
        x, z = _out(a, 3.6)
        st = body.bone(f"strand{k}", (x, 2, z), (-7, -a + (14 if k % 2 else -14), 0))
        st.cube(f"strand{k}", -2, -16, -2, 4, 16, 4, share=None if k == 0 else "strand0")
    # the crown turns to face you; the plates grow from its collar round the core
    crown = body.bone("crown", (0, -12, 0))
    crown.cube("collar", -6, -3, -6, 12, 4, 12)
    core = crown.bone("core", (0, -8, 0), (0, 45, 0))
    core.cube("core", -4, -5, -4, 8, 10, 8)
    core.bone("facet", (0, 0, 0), (45, 0, 45)).cube("facet", -3.5, -3.5, -3.5, 7, 7, 7)
    for i in range(SEEP_PLATES):
        a = i * 360 / SEEP_PLATES
        x, z = _out(a, 5.5)
        p = crown.bone(f"plate{i}", (x, -2, z), (38, -a, 0))
        p.cube(f"plate{i}", -4, -14, -1, 8, 14, 2, share=None if i == 0 else "plate0")
        tip = p.bone(f"ptip{i}", (0, -14, 0), (6, 0, 0))
        tip.cube(f"ptip{i}", -2, -5, -1, 4, 5, 2, share=None if i == 0 else "ptip0")
        tip.bone(f"ppt{i}", (0, -5, 0), (8, 0, 0)).cube(f"ppt{i}", -1, -3, -0.5, 2, 3, 1, share=None if i == 0 else "ppt0")
    r.look = [("crown", 1.0, 0.35)]
    r.moves = dict(SEEP_MOVES)
    r.pack()
    seep_clips(r)
    return r


def seep_clips(r):
    P = [f"plate{i}" for i in range(SEEP_PLATES)]
    T = [f"ptip{i}" for i in range(SEEP_PLATES)]
    RB = [f"rt{i}b" for i in range(SEEP_ROOTS)]
    RC = [f"rt{i}c" for i in range(SEEP_ROOTS)]
    L = (("l", 1), ("r", -1))

    def plates(clip, keys, tips=None, stagger=0.0):
        """keys [(t, degrees outward)] for every plate (tips: the same for the tips); each plate `stagger` s behind."""
        for i in range(SEEP_PLATES):
            def sh(ks):
                return [(t + (i * stagger if 0 < t < clip.length - 1e-6 else 0), x, 0, 0) for t, x in ks]
            clip.rot(P[i], sh(keys))
            if tips:
                clip.rot(T[i], sh(tips))

    def arms(clip, k1, k2=None, k3=None):
        """[(t, x, y, z)] for the left arm's three links; the right arm mirrors y and z."""
        for nm, sg in L:
            for bone, ks in (("lash_", k1), ("lash2_", k2), ("lash3_", k3)):
                if ks:
                    clip.rot(bone + nm, [(t, x, sg * y, sg * z) for t, x, y, z in ks])

    def core(clip, keys):
        clip.scale("core", [(t, v, v, v) for t, v in keys])

    def roots(clip, kb, kc=None, stagger=0.0):
        for i in range(SEEP_ROOTS):
            clip.rot(RB[i], [(t + (i * stagger if 0 < t < clip.length - 1e-6 else 0), x, 0, 0) for t, x in kb])
            if kc:
                clip.rot(RC[i], [(t + (i * stagger if 0 < t < clip.length - 1e-6 else 0), x, 0, 0) for t, x in kc])

    # idle: the plates breathe open and shut a few degrees, a slow ripple round the bud; the core turns; the roots
    # swell one after another along the floor; the arms sway
    idle = r.clip("idle", 4.0, loop=True)
    for i in range(SEEP_PLATES):
        cycle(idle, P[i], "rot", lambda q, i=i: (4 * s(q, -i / SEEP_PLATES), 0, 0), 8)
        cycle(idle, T[i], "rot", lambda q, i=i: (3 * s(q, -i / SEEP_PLATES - 0.08), 0, 0), 8)
    idle.rot("core", [(0, 0, 0, 0, "lin"), (4.0, 0, 360, 0, "lin")])
    cycle(idle, "crown", "rot", lambda q: (1.5 * s(q), 0, 1.2 * c(q)), 8)
    cycle(idle, "crown", "pos", lambda q: (0, 0.5 * s(q, 0.25), 0), 8)
    for i in range(SEEP_ROOTS):
        cycle(idle, RB[i], "rot", lambda q, i=i: (-1.2 * max(0.0, s(q, -i / SEEP_ROOTS)), 0, 0), 8)
    for nm, sg in L:
        ph = 0.0 if sg > 0 else 0.5
        cycle(idle, "lash_" + nm, "rot", lambda q, sg=sg, ph=ph: (3 * s(q, ph), 0, sg * 2 * c(q, ph)), 8)
        cycle(idle, "lash2_" + nm, "rot", lambda q, ph=ph: (4 * s(q, ph + 0.12), 0, 0), 8)
        cycle(idle, "lash3_" + nm, "rot", lambda q, ph=ph: (6 * s(q, ph + 0.24), 0, 0), 8)
    # it never walks (rooted at its socket): the walk is only the roots stirring
    walk = r.clip("walk", 1.0, loop=True)
    for i in range(SEEP_ROOTS):
        cycle(walk, RB[i], "rot", lambda q, i=i: (-3 * max(0.0, s(q, -i / SEEP_ROOTS)), 0, 0), 8)
    # asleep (held): the plates shut round the core like a bud, the crown sunk, the arms curled in
    sl = r.clip("sleep", 4.0, loop=True)
    plates(sl, [(0, -50), (4.0, -50)], [(0, -26), (4.0, -26)])
    sl.pos("crown", [(0, 0, -2.5, 0), (4.0, 0, -2.5, 0)])
    sl.pos("body", [(0, 0, -1, 0), (4.0, 0, -1, 0)])
    core(sl, [(0, 0.85), (4.0, 0.85)])
    arms(sl, [(0, -14, 0, -8), (4.0, -14, 0, -8)], [(0, 10, 0, 0), (4.0, 10, 0, 0)], [(0, 20, 0, 0), (4.0, 20, 0, 0)])
    # one beat of the heart (the renderer plays it on every beat): lub, dub
    be = r.clip("beat", 0.5)
    core(be, [(0, 1.0), (0.05, 1.16), (0.14, 1.0), (0.22, 1.09), (0.5, 1.0)])
    plates(be, [(0, 0), (0.06, 2.5), (0.16, 0), (0.24, 1.5), (0.5, 0)])
    be.pos("crown", [(0, 0, 0, 0), (0.06, 0, 0.3, 0), (0.5, 0, 0, 0)])
    # the wake (and every new phase): it rises, the plates burst open past their rest, the arms are flung up, the roots
    # heave out of the floor and slap down
    ro = r.clip("roar", 2.2)
    ro.pos("crown", [(0, 0, 0, 0), (0.35, 0, 3.5, 0), (0.9, 0, 2.2, 0), (2.2, 0, 0, 0)])
    ro.pos("body", [(0, 0, 0, 0), (0.4, 0, 1.5, 0), (2.2, 0, 0, 0)])
    core(ro, [(0, 1.0), (0.25, 1.35), (0.6, 1.08), (1.0, 1.2), (2.2, 1.0)])
    plates(ro, [(0, 0), (0.25, 24), (0.8, 10), (2.2, 0)], [(0, 0), (0.3, 20), (0.9, 6), (2.2, 0)], stagger=0.03)
    arms(ro, [(0, 0, 0, 0), (0.35, -38, 0, 24), (1.2, -20, 0, 12), (2.2, 0, 0, 0)], [(0, 0, 0, 0), (0.4, -30, 0, 0), (2.2, 0, 0, 0)],
         [(0, 0, 0, 0), (0.45, -40, 0, 0), (2.2, 0, 0, 0)])
    roots(ro, [(0, 0), (0.25, -12), (0.6, 2), (1.0, 0), (2.2, 0)], [(0, 0), (0.3, -16), (0.7, 3), (1.1, 0), (2.2, 0)], stagger=0.02)
    wa = r.clip("warn", 1.2)
    plates(wa, [(0, 0), (0.3, 12), (0.5, 4), (0.7, 12), (1.2, 0)])
    core(wa, [(0, 1), (0.3, 1.15), (0.5, 1.0), (0.7, 1.15), (1.2, 1)])
    arms(wa, [(0, 0, 0, 0), (0.35, -18, 0, 10), (1.2, 0, 0, 0)])
    # a melee lash of the arms
    bi = r.clip("bite", 0.6)
    arms(bi, [(0, 0, 0, 0), (0.12, -30, 0, 10), (0.24, 40, 0, -10), (0.6, 0, 0, 0)], [(0, 0, 0, 0), (0.12, -20, 0, 0), (0.24, 30, 0, 0), (0.6, 0, 0, 0)],
         [(0, 0, 0, 0), (0.14, -25, 0, 0), (0.26, 35, 0, 0), (0.6, 0, 0, 0)])
    bi.rot("crown", [(0, 0, 0, 0), (0.12, -6, 0, 0), (0.24, 8, 0, 0), (0.6, 0, 0, 0)])
    w = r.clip("windup", 0.8)
    w.rot("crown", [(0, 0, 0, 0), (0.8, -8, 0, 0)])
    plates(w, [(0, 0), (0.8, 10)])
    st = r.clip("strike", 0.5)
    st.rot("crown", [(0, -8, 0, 0), (0.08, 10, 0, 0), (0.5, 0, 0, 0)])
    plates(st, [(0, 10), (0.08, -6), (0.5, 0)])
    # Deep Shards: the plates flare wide and the crown draws back... then they snap forward and fling the shards
    sw = r.clip("shards_windup", 1.0)
    plates(sw, [(0, 0), (1.0, 26)], [(0, 0), (1.0, 22)])
    sw.rot("crown", [(0, 0, 0, 0), (1.0, -12, 0, 0)])
    core(sw, [(0, 1), (1.0, 1.18)])
    arms(sw, [(0, 0, 0, 0), (1.0, -16, 0, 14)])
    ss = r.clip("shards_strike", 0.55)
    plates(ss, [(0, 26), (0.07, -12), (0.25, 4), (0.55, 0)], [(0, 22), (0.07, -10), (0.55, 0)])
    ss.rot("crown", [(0, -12, 0, 0), (0.07, 14, 0, 0), (0.55, 0, 0, 0)])
    core(ss, [(0, 1.18), (0.06, 0.92), (0.55, 1.0)])
    arms(ss, [(0, -16, 0, 14), (0.1, 10, 0, 0), (0.55, 0, 0, 0)])
    # Rootfall: it hunches, drives its arms into the floor and the roots tremble as they push through the rock...
    # then it heaves up and they burst out where you stood
    rw = r.clip("roots_windup", 1.6)
    rw.pos("crown", [(0, 0, 0, 0), (1.6, 0, -2.5, 0)])
    rw.rot("crown", [(0, 0, 0, 0), (1.6, 7, 0, 0)])
    arms(rw, [(0, 0, 0, 0), (1.0, 28, 0, -6), (1.6, 34, 0, -8)], [(0, 0, 0, 0), (1.6, 26, 0, 0)], [(0, 0, 0, 0), (1.6, 20, 0, 0)])
    for i in range(SEEP_ROOTS):
        rw.rot(f"rt{i}a", [(k * 0.1, 0, (1.5 if (k + i) % 2 else -1.5) * (k / 16), 0) for k in range(17)])
    roots(rw, [(0, 0), (1.6, 3)])
    plates(rw, [(0, 0), (1.6, -10)])
    rs = r.clip("roots_strike", 0.6)
    rs.pos("crown", [(0, 0, -2.5, 0), (0.08, 0, 2, 0), (0.6, 0, 0, 0)])
    rs.rot("crown", [(0, 7, 0, 0), (0.08, -10, 0, 0), (0.6, 0, 0, 0)])
    arms(rs, [(0, 34, 0, -8), (0.1, -30, 0, 16), (0.6, 0, 0, 0)], [(0, 26, 0, 0), (0.1, -20, 0, 0), (0.6, 0, 0, 0)], [(0, 20, 0, 0), (0.12, -30, 0, 0), (0.6, 0, 0, 0)])
    plates(rs, [(0, -10), (0.08, 18), (0.6, 0)])
    # Root Lash: the arms coil back, the crown twists away, the roots lift... then everything sweeps round
    lw = r.clip("lash_windup", 1.0)
    lw.rot("crown", [(0, 0, 0, 0), (1.0, -4, -22, 0)])
    arms(lw, [(0, 0, 0, 0), (1.0, -42, 0, 20)], [(0, 0, 0, 0), (1.0, -26, 0, 0)], [(0, 0, 0, 0), (1.0, -20, 0, 0)])
    roots(lw, [(0, 0), (1.0, -7)], [(0, 0), (1.0, -9)])
    ls = r.clip("lash_strike", 0.65)
    ls.rot("crown", [(0, -4, -22, 0), (0.14, 6, 38, 0), (0.65, 0, 0, 0)])
    arms(ls, [(0, -42, 0, 20), (0.12, 46, 30, -20), (0.65, 0, 0, 0)], [(0, -26, 0, 0), (0.12, 36, 0, 0), (0.65, 0, 0, 0)],
         [(0, -20, 0, 0), (0.14, 40, 0, 0), (0.65, 0, 0, 0)])
    roots(ls, [(0, -7), (0.06, 6), (0.3, -2), (0.65, 0)], [(0, -9), (0.07, 8), (0.3, -2), (0.65, 0)])
    # Deep Pulse (its signature): the plates close tight and the crown sinks while the hidden core swells... then the
    # plates blast open with the pulse
    pw = r.clip("pulse_windup", 1.8)
    plates(pw, [(0, 0), (1.4, -52), (1.8, -56)], [(0, 0), (1.4, -24), (1.8, -26)])
    pw.pos("crown", [(0, 0, 0, 0), (1.8, 0, -3, 0)])
    core(pw, [(0, 1), (1.8, 1.3)])
    arms(pw, [(0, 0, 0, 0), (1.8, -18, 0, -10)], [(0, 0, 0, 0), (1.8, 16, 0, 0)], [(0, 0, 0, 0), (1.8, 26, 0, 0)])
    ps = r.clip("pulse_strike", 0.9)
    plates(ps, [(0, -56), (0.07, 30), (0.3, 12), (0.9, 0)], [(0, -26), (0.08, 24), (0.9, 0)])
    ps.pos("crown", [(0, 0, -3, 0), (0.08, 0, 3.5, 0), (0.9, 0, 0, 0)])
    core(ps, [(0, 1.3), (0.05, 1.55), (0.3, 1.1), (0.9, 1.0)])
    arms(ps, [(0, -18, 0, -10), (0.1, -40, 0, 30), (0.9, 0, 0, 0)], [(0, 16, 0, 0), (0.1, -24, 0, 0), (0.9, 0, 0, 0)], [(0, 26, 0, 0), (0.1, -30, 0, 0), (0.9, 0, 0, 0)])
    roots(ps, [(0, 0), (0.06, -10), (0.4, 0), (0.9, 0)])
    # Call of the Deep: it rises tall, opens wide and lifts its arms to the walls, shaking... then thrusts them out
    cw = r.clip("call_windup", 1.8)
    cw.pos("crown", [(0, 0, 0, 0), (1.0, 0, 4, 0), (1.8, 0, 4.5, 0)])
    cw.pos("body", [(0, 0, 0, 0), (1.0, 0, 2, 0), (1.8, 0, 2, 0)])
    plates(cw, [(0, 0), (1.0, 28), (1.8, 30)], [(0, 0), (1.0, 20), (1.8, 22)])
    arms(cw, [(0, 0, 0, 0), (0.8, -36, 0, 46)] + [(0.9 + k * 0.1, -36 + (3 if k % 2 else -3), 0, 46) for k in range(9)],
         [(0, 0, 0, 0), (0.8, -30, 0, 0), (1.8, -30, 0, 0)], [(0, 0, 0, 0), (0.8, -26, 0, 0), (1.8, -26, 0, 0)])
    core(cw, [(0, 1), (1.8, 1.2)])
    cs = r.clip("call_strike", 0.9)
    cs.pos("crown", [(0, 0, 4.5, 0), (0.1, 0, 5, 0), (0.9, 0, 0, 0)])
    cs.pos("body", [(0, 0, 2, 0), (0.9, 0, 0, 0)])
    plates(cs, [(0, 30), (0.1, 34), (0.9, 0)], [(0, 22), (0.9, 0)])
    arms(cs, [(0, -36, 0, 46), (0.1, -10, 0, 70), (0.9, 0, 0, 0)], [(0, -30, 0, 0), (0.1, -10, 0, 0), (0.9, 0, 0, 0)], [(0, -26, 0, 0), (0.9, 0, 0, 0)])
    core(cs, [(0, 1.2), (0.08, 1.45), (0.9, 1.0)])
    # Shard Storm: it winds the crown back and opens every plate... then whips round and the ring of shards flies
    tw = r.clip("storm_windup", 1.2)
    tw.rot("crown", [(0, 0, 0, 0), (1.2, -6, -30, 0)])
    plates(tw, [(0, 0), (1.2, 30)], [(0, 0), (1.2, 24)])
    core(tw, [(0, 1), (1.2, 1.25)])
    arms(tw, [(0, 0, 0, 0), (1.2, -24, 0, 30)])
    ts = r.clip("storm_strike", 0.7)
    ts.rot("crown", [(0, -6, -30, 0), (0.15, 4, 50, 0), (0.7, 0, 0, 0)])
    plates(ts, [(0, 30), (0.06, -8), (0.2, 14), (0.7, 0)], [(0, 24), (0.06, -10), (0.7, 0)], stagger=0.015)
    core(ts, [(0, 1.25), (0.05, 0.9), (0.7, 1.0)])
    arms(ts, [(0, -24, 0, 30), (0.15, 6, 0, 10), (0.7, 0, 0, 0)])
    # Heartburst: the plates close slowly over a core that swells and shakes the crown... then burst open (step in!)
    bw = r.clip("burst_windup", 2.4)
    plates(bw, [(0, 0), (2.0, -50), (2.4, -54)], [(0, 0), (2.0, -22), (2.4, -24)])
    bw.rot("crown", [(k * 0.1, (1.2 if k % 2 else -1.2) * (k / 24), 0, (1.0 if k % 3 else -1.0) * (k / 24)) for k in range(25)])
    core(bw, [(0, 1), (2.4, 1.5)])
    arms(bw, [(0, 0, 0, 0), (2.4, -10, 0, -14)], [(0, 0, 0, 0), (2.4, 18, 0, 0)], [(0, 0, 0, 0), (2.4, 30, 0, 0)])
    bs = r.clip("burst_strike", 1.0)
    plates(bs, [(0, -54), (0.06, 36), (0.35, 14), (1.0, 0)], [(0, -24), (0.07, 30), (1.0, 0)])
    bs.pos("crown", [(0, 0, 0, 0), (0.07, 0, 4, 0), (1.0, 0, 0, 0)])
    core(bs, [(0, 1.5), (0.05, 1.7), (0.4, 1.1), (1.0, 1.0)])
    arms(bs, [(0, -10, 0, -14), (0.1, -46, 0, 40), (1.0, 0, 0, 0)], [(0, 18, 0, 0), (0.1, -30, 0, 0), (1.0, 0, 0, 0)], [(0, 30, 0, 0), (0.12, -36, 0, 0), (1.0, 0, 0, 0)])
    roots(bs, [(0, 0), (0.06, -12), (0.5, 0), (1.0, 0)], [(0, 0), (0.08, -16), (0.6, 0), (1.0, 0)])
    hu = r.clip("hurt", 0.45)
    hu.rot("crown", [(0, 0, 0, 0), (0.05, -6, 0, 4), (0.2, 2, 0, -1), (0.45, 0, 0, 0)])
    plates(hu, [(0, 0), (0.05, 8), (0.18, -3), (0.45, 0)])
    core(hu, [(0, 1), (0.05, 0.9), (0.45, 1)])
    # death: the violet drains into the core (it swells, the plates flare), then the plates shut over it and the crown
    # sinks and droops; the arms collapse to the floor
    de = r.clip("death", 3.2)
    plates(de, [(0, 0), (0.9, 10), (1.3, 6), (2.4, -54), (3.2, -58)], [(0, 0), (0.9, 8), (2.4, -26), (3.2, -28)], stagger=0.05)
    de.pos("crown", [(0, 0, 0, 0), (0.9, 0, 1.5, 0), (2.0, 0, 0, 0), (3.2, 0, -4.5, 0)])
    de.rot("crown", [(0, 0, 0, 0), (1.2, -3, 0, 0), (3.2, 12, 0, 6)])
    de.pos("body", [(0, 0, 0, 0), (3.2, 0, -2, 0)])
    core(de, [(0, 1.0), (1.1, 1.3), (2.0, 1.05), (3.2, 0.7)])
    arms(de, [(0, 0, 0, 0), (0.9, -16, 0, 14), (2.0, 40, 0, 20), (3.2, 62, 0, 26)], [(0, 0, 0, 0), (2.0, 20, 0, 0), (3.2, 30, 0, 0)],
         [(0, 0, 0, 0), (2.2, 10, 0, 0), (3.2, 22, 0, 0)])
    roots(de, [(0, 0), (0.6, -6), (1.4, 0), (3.2, 1)], stagger=0.05)


def _cracks(P, cube, fname, n, seed, r0=0.2, r1=1.0, a0=230, a1=70, branch=0.25):
    """Glowing cracks running out from the middle of a face (Deepglass light in the rock)."""
    import random
    rnd = random.Random(_h("seep_cracks", cube, fname, seed))
    glass = Ramp(*DEEPGLASS)
    f, g = P.face(cube, fname), P.face(cube, fname, glow=True)
    cx, cy = (f.w - 1) / 2, (f.h - 1) / 2
    big = min(f.w, f.h) / 2

    def run(ang, ra, rb, aa, ab, depth=0):
        steps = int(big * (rb - ra) * 1.6) + 2
        wob = 0.0
        for k in range(steps):
            t = k / (steps - 1)
            rr = big * (ra + (rb - ra) * t)
            wob += rnd.uniform(-0.2, 0.2)
            a = ang + wob * 0.3
            x, y = int(round(cx + math.cos(a) * rr)), int(round(cy + math.sin(a) * rr))
            if 0 <= x < f.w and 0 <= y < f.h:
                f.set(x, y, glass.at(5 if t < 0.4 else 4))
                g.set(x, y, hexc("#b48cff")[:3] + (int(aa + (ab - aa) * t),))
            if depth == 0 and rnd.random() < branch * 0.08 and rb - ra > 0.2:
                here = ra + (rb - ra) * t
                run(a + rnd.choice((-0.7, 0.7)), here, min(rb, here + 0.3), int(aa * 0.8), ab, 1)

    for k in range(n):
        run((k + rnd.uniform(-0.3, 0.3)) / n * math.tau, r0, r1, a0, a1)


def paint_seep_heart(r, P):
    stone = Ramp("#0c0b10", "#16141c", "#211e28", "#2d2936", "#3b3646", "#4d4759")
    growth = Ramp("#050309", "#0c0716", "#160d26", "#211338", "#30194f", "#45246e")
    glass = Ramp(*DEEPGLASS)
    light = Ramp("#5a38a0", "#8a62dc", "#b48cff", "#d4baff", "#ecdfff", "#fbf7ff")
    VIOLET, PALE, WHITE = hexc("#b48cff"), hexc("#dcc8ff"), hexc("#f6efff")
    # the mound: deepslate, stained black-violet towards the middle where the growth comes up, cracked with light
    P.paint("mound", stone, 2.2, combine(p_stone(P.rng, 0.8, cell=5), p_blotch("seep_mound", 6, 0.25)), rim=0.4, ao=0.5)
    f = P.face("mound", "top")
    cx, cy = (f.w - 1) / 2, (f.h - 1) / 2
    for i, j in f.each():
        d = math.hypot(i - cx, j - cy) / (f.w / 2)
        if d < 0.66:
            f.set(i, j, growth.at(1.4 + 2.4 * d + (0.7 if (i * 7 + j * 3) % 11 == 0 else 0)))
    for fname in SIDES:
        f, g = P.face("mound", fname), P.face("mound", fname, glow=True)
        for i in range(f.w):
            k = (i * 5 + 3) % 7
            if k < 2:  # the growth reaching over the rock's edge, a faint vein in it now and then
                f.set(i, 0, growth.at(2.2))
                f.set(i, 1, growth.at(1.6))
                if k == 0:
                    g.set(i, 0, VIOLET[:3] + (110,))
    _cracks(P, "mound", "top", 8, "m", r0=0.55, r1=1.0, a0=170, a1=60)
    P.paint("mound_top", growth, 2.2, combine(p_stone(P.rng, 0.5, cell=5), p_blotch("seep_mtop", 5, 0.3)), rim=0.5)
    _cracks(P, "mound_top", "top", 6, "mt", r0=0.15, r1=1.0, a0=225, a1=120)
    for k in range(len(SEEP_LUMPS)):
        P.paint(f"lump{k}", stone, 2.4, p_stone(P.rng, 0.7, cell=3), rim=0.5)
    # the roots: black-violet growth with a vein of light down their backs, brightest near the heart
    for nm, a0 in (("rt0a", 215), ("rt0b", 180), ("rt0c", 140), ("rtf0", 120)):
        P.paint(nm, growth, 2.0, combine(p_bands(3, 0.5), p_blotch("seep_" + nm, 3, 0.3)), rim=0.5, ao=0.6)
        f, g = P.face(nm, "top"), P.face(nm, "top", glow=True)
        mid = (f.w - 1) // 2
        for j in range(f.h):
            x = mid + ((j // 3) % 2 if f.w > 3 else 0)
            f.set(x, j, glass.at(4.6))
            g.set(x, j, VIOLET[:3] + (int(a0 - 30 * j / max(1, f.h - 1)),))
        fn, gn = P.face(nm, "north"), P.face(nm, "north", glow=True)
        fn.set((fn.w - 1) // 2, 0, glass.at(5))
        gn.set((fn.w - 1) // 2, 0, PALE[:3] + (int(a0 * 0.8),))
    # the arms: banded growth, a seam of light up their fronts, a glass barb
    for nm in ("lash_l", "lash2_l", "lash3_l"):
        P.paint(nm, growth, 2.3, combine(p_bands(3, 0.7), p_blotch("seep_" + nm, 3, 0.25)), rim=0.5)
        for fname in ("north", "top"):
            f, g = P.face(nm, fname), P.face(nm, fname, glow=True)
            mid = (g.w - 1) // 2
            for j in range(g.h):
                f.set(mid, j, glass.at(4.2))
                g.set(mid, j, VIOLET[:3] + (150,))
    P.paint("barb_l", glass, 4.8, None, rim=0.4)
    for fname in ("top", "north", "south", "west", "east"):
        g = P.face("barb_l", fname, glow=True)
        for i, j in g.each():
            g.set(i, j, PALE[:3] + (220,))
    # the trunk: growth twisted round itself, two seams of light climbing each side, brighter towards the core
    P.paint("trunk", growth, 2.4, combine(p_strokes(cell=2, length=5, dark=0.8, light=0.5, density=0.8, seed=3), p_blotch("seep_trunk", 4, 0.3)), rim=0.3)
    for fname in SIDES:
        f, g = P.face("trunk", fname), P.face("trunk", fname, glow=True)
        for x0 in (1, 5):
            for j in range(f.h):
                x = x0 + (1 if (j // 4 + x0) % 2 else 0)
                if 0 <= x < f.w:
                    f.set(x, j, glass.at(4))
                    g.set(x, j, VIOLET[:3] + (int(60 + 130 * (1 - j / f.h) ** 1.5),))
    P.paint("strand0", glass, 1.6, lambda f, i, j: (2.4 if i == 0 else 1.2 if i == f.w - 1 else 0) if f.name in SIDES else 0.8, rim=0.5)
    for fname in SIDES:
        g = P.face("strand0", fname, glow=True)
        for j in range(g.h):
            g.set(0, j, PALE[:3] + (int(50 + 140 * (1 - j / g.h) ** 1.4),))
    # the collar: a ring of growth, its rim lit, glowing white-violet inside under the core
    P.paint("collar", growth, 2.6, p_bands(2, 0.5, vertical=True), rim=0.6)
    f, g = P.face("collar", "top"), P.face("collar", "top", glow=True)
    for i, j in f.each():
        edge = min(i, j, f.w - 1 - i, f.h - 1 - j)
        if edge <= 1:
            f.set(i, j, glass.at(4 + (1 - edge) * 0.6))
            g.set(i, j, VIOLET[:3] + (200 - edge * 60,))
        else:
            f.set(i, j, light.at(1.5))
            g.set(i, j, PALE[:3] + (170,))
    # the core: a faceted crystal of light, white at its heart
    for cname, base in (("core", 2.6), ("facet", 3.6)):
        P.paint(cname, light, base, lambda f, i, j: 0.9 * (1 - abs(i - (f.w - 1) / 2) / max(1, f.w / 2)) - 0.6 * abs(j - (f.h - 1) / 2) / max(1, f.h / 2),
                rim=0.4, ao=0.3, light=0.6)
        for fname in ("top", "bottom") + SIDES:
            gf = P.face(cname, fname, glow=True)
            for i, j in gf.each():
                d = max(abs(i - (gf.w - 1) / 2) / max(1, gf.w / 2), abs(j - (gf.h - 1) / 2) / max(1, gf.h / 2))
                col = lerpc(WHITE, VIOLET if cname == "core" else PALE, d)
                gf.set(i, j, col[:3] + (int(255 - 45 * d),))
    # the plates: black Deepglass, dark inside the glass and lit violet only at the edges; their inner faces catch the
    # core's light; the tips shine
    for cname, tip in (("plate0", False), ("ptip0", True)):
        def pat(f, i, j, tip=tip):
            edge = i in (0, f.w - 1)
            if f.name == "north":
                if edge:
                    return 2.5
                return -0.5 + (0.9 if i == f.w // 2 and not tip else 0) + 0.3 * j / f.h
            if f.name == "south":
                return 1.2 + (1.3 * j / max(1, f.h - 1) if not tip else 0.6) + (0.7 if edge else 0)
            return 2.3
        P.paint(cname, glass, 1.6, pat, rim=0.3, ao=0.2, grad=0.3)
        for fname in ("south", "west", "east", "top"):
            g = P.face(cname, fname, glow=True)
            for i, j in g.each():
                if fname == "south":
                    a = 60 + 100 * j / max(1, g.h - 1) if not tip else 100 + 60 * (1 - j / max(1, g.h - 1))
                    g.set(i, j, VIOLET[:3] + (int(a),))
                else:
                    g.set(i, j, PALE[:3] + (215 if tip else 165,))
        g = P.face(cname, "north", glow=True)
        for i in (0, g.w - 1):
            for j in range(g.h):
                g.set(i, j, VIOLET[:3] + (int(120 if not tip else 170),))
        if tip:
            for i in range(g.w):
                g.set(i, 0, PALE[:3] + (225,))
    P.paint("ppt0", glass, 5.0, None, rim=0.4, ao=0.0)
    for fname in ("top", "north", "south", "west", "east"):
        g = P.face("ppt0", fname, glow=True)
        for i, j in g.each():
            g.set(i, j, lerpc(WHITE, PALE, j / max(1, g.h - 1))[:3] + (240,))
    # the spires: Deepglass, lit along one edge, the light gathering to a shining tip
    for k in range(len(SEEP_SPIRES)):
        sp, tp = f"spire{k}", f"stip{k}"
        P.paint(sp, glass, 1.4, lambda f, i, j: (2.6 if i == 0 else 1.5 if i == f.w - 1 else 0.3 * (1 - j / max(1, f.h))) if f.name in SIDES else 1.4,
                rim=0.4, ao=0.4)
        P.paint(tp, glass, 4.6, None, rim=0.5, ao=0.0, grad=0.3)
        for fname in SIDES:
            g = P.face(sp, fname, glow=True)
            for j in range(g.h):
                t = 1 - j / max(1, g.h - 1)
                g.set(0, j, PALE[:3] + (int(40 + 160 * t * t),))
            gt = P.face(tp, fname, glow=True)
            for i, j in gt.each():
                u = j / max(1, gt.h - 1)
                gt.set(i, j, lerpc(WHITE, PALE, u)[:3] + (int(245 - 35 * u),))
        gt = P.face(tp, "top", glow=True)
        for i, j in gt.each():
            gt.set(i, j, WHITE[:3] + (255,))
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# A burst of roots (the seep heart's Rootfall and Root Lash): thorned roots of growth punch up through the floor with
# the rock they broke, hold, and sink back. Drawn by mobs.client.SeepRootRenderer on awaken:seep_root (no creature).

SEEP_ROOT_SPIKES = [(0, 0, 6, 0, 22), (-3, 2, -34, 40, 15), (3, -2, -30, 210, 13), (2, 3, -38, 300, 10), (-2, -3, -28, 130, 9)]


def seep_root():
    r = Rig("seep_root", (64, 64))
    r.shadow = 0.0
    base = r.bone("rbase", (0, 24, 0))
    for k, (x, z, yaw, w, h, d) in enumerate([(-4, 2, 20, 4, 2, 3), (4, 1, -30, 3, 2, 4), (0, -4, 60, 4, 1, 3), (2, 5, 10, 3, 1, 3)]):
        base.bone(f"rubble{k}", (x, 0, z), (0, yaw, 0)).cube(f"rubble{k}", -w / 2, -h, -d / 2, w, h, d)
    for k, (x, z, lean, yaw, h) in enumerate(SEEP_ROOT_SPIKES):
        w = 4 if k == 0 else 3
        h1 = int(h * 0.55)
        h2 = h - h1 - 3
        sp = base.bone(f"spike{k}", (x, 0, z), (lean, yaw, 0))
        sp.cube(f"spike{k}", -w / 2, -h1, -w / 2, w, h1, w)
        sb = sp.bone(f"spikeb{k}", (0, -h1, 0), (14, 0, 0))
        sb.cube(f"spikeb{k}", -(w - 1) / 2, -h2, -(w - 1) / 2, w - 1, h2, w - 1)
        sb.bone(f"spikec{k}", (0, -h2, 0), (10, 0, 0)).cube(f"spikec{k}", -0.5, -3, -0.5, 1, 3, 1)
    r.pack()
    for nm in ("idle", "walk"):  # what every rig must have (it is no creature: these hold still)
        r.clip(nm, 1.0, loop=True).rot("rbase", [(0, 0, 0, 0), (1.0, 0, 0, 0)])
    for nm in ("hurt", "death"):
        r.clip(nm, 0.2).rot("rbase", [(0, 0, 0, 0), (0.2, 0, 0, 0)])
    er = r.clip("erupt", 1.4)
    for k, (_, _, _, _, h) in enumerate(SEEP_ROOT_SPIKES):
        delay = k * 0.035
        keys = [(0, 0, -(h + 2), 0, "lin")]
        if delay > 0:
            keys.append((delay, 0, -(h + 2), 0, "lin"))
        keys += [(delay + 0.08, 0, 1.5, 0), (delay + 0.2, 0, 0, 0), (0.75, 0, 0, 0), (1.35, 0, -(h + 2), 0), (1.4, 0, -(h + 2), 0)]
        er.pos(f"spike{k}", keys)
        er.rot(f"spikeb{k}", [(0, 0, 0, 0), (delay + 0.1, -12, 0, 0), (delay + 0.3, 4, 0, 0), (0.6, 0, 0, 0), (1.4, 0, 0, 0)])
    for k in range(4):
        er.pos(f"rubble{k}", [(0, 0, 0, 0), (0.08, 0, 2.0, 0), (0.25, 0, 0.5, 0), (1.0, 0, 0.5, 0), (1.4, 0, 0, 0)])
    return r


def paint_seep_root(r, P):
    stone = Ramp("#0c0b10", "#16141c", "#211e28", "#2d2936", "#3b3646", "#4d4759")
    growth = Ramp("#050309", "#0c0716", "#160d26", "#211338", "#30194f", "#45246e")
    glass = Ramp(*DEEPGLASS)
    VIOLET, PALE = hexc("#b48cff"), hexc("#dcc8ff")
    for k in range(4):
        P.paint(f"rubble{k}", stone, 2.2, p_stone(P.rng, 0.6, cell=2), rim=0.5)
    for k in range(len(SEEP_ROOT_SPIKES)):
        P.paint(f"spike{k}", growth, 2.2, combine(p_bands(3, 0.6), p_blotch(f"seep_spike{k}", 3, 0.3)), rim=0.5)
        P.paint(f"spikeb{k}", growth, 2.6, p_bands(3, 0.6), rim=0.5)
        P.paint(f"spikec{k}", glass, 4.8, None, rim=0.4)
        for nm, a in ((f"spike{k}", 150), (f"spikeb{k}", 190)):
            for fname in ("north", "south"):
                f, g = P.face(nm, fname), P.face(nm, fname, glow=True)
                mid = (f.w - 1) // 2
                for j in range(f.h):
                    f.set(mid, j, glass.at(4.4))
                    g.set(mid, j, VIOLET[:3] + (a,))
        for fname in ("top", "north", "south", "west", "east"):
            g = P.face(f"spikec{k}", fname, glow=True)
            for i, j in g.each():
                g.set(i, j, PALE[:3] + (230,))
    return P.skin, P.glow


# =====================================================================================================================
# Registry, Java data, previews (kept at the end: the designs above fill RIGS)
# =====================================================================================================================

RIGS = {}


def _register():
    RIGS.clear()
    for name in ("glimmerfin", "breathbell", "brook_otter", "tidesteed", "lantern_maw", "chime_cricket", "wickling", "drip_lurker", "hushling", "thousandfoot",
                 "seep_heart", "seep_root"):
        design = globals().get(name)
        painter = globals().get("paint_" + name)
        if design and painter:
            RIGS[name] = (design, painter)


# What each body is scaled to in game (the bestiary's .scale) and its box (Model width, height) for the size check.
SCALE = {"lantern_maw": 1.3, "thousandfoot": 1.3}
BOX = {"glimmerfin": (0.5, 0.35), "breathbell": (0.7, 0.8), "brook_otter": (0.6, 0.55), "tidesteed": (1.2, 1.3), "lantern_maw": (2.0, 1.6),
       "chime_cricket": (0.5, 0.4), "wickling": (0.5, 0.35), "drip_lurker": (0.8, 1.1), "hushling": (0.6, 0.75), "thousandfoot": (2.0, 1.1),
       "seep_heart": (2.0, 2.6), "seep_root": (0.6, 1.3)}


def rigs(only=None):
    _register()
    out = []
    for rid, (design, painter) in RIGS.items():
        if only and rid not in only:
            continue
        out.append((rid, design(), painter))
    return out


def paint(rig, painter):
    return paint_full(rig, painter)[:2]


def paint_full(rig, painter):
    """(skin, glow, blink): blink is (skin, glow) with the eyes shut, or None (fauna_rig.Painter.eyes)."""
    P = R.Painter(rig, "fauna3|" + rig.id)
    skin, glow = painter(rig, P)
    return skin, glow, P.blink()


def java(all_rigs):
    chunks = []
    for rid, rig, _ in all_rigs:
        problems = rig.check()
        if problems:
            raise SystemExit("\n".join(problems))
        block, size, parts = [], 0, []
        for ln in rig.java_lines():
            if size + len(ln) > 24000 and block:
                parts.append(block)
                block, size = [], 0
            block.append(ln)
            size += len(ln) + 1
        if block:
            parts.append(block)
        chunks.append((rid, parts))
    src = ["package dev.awaken.mobs.client.anim;", "", "import java.util.List;", "", "/**",
           " * GENERATED by tools/art/specs/fauna3.py: do not edit by hand. The P3 bodies (the Tidewater and the Hollows) as",
           " * plain data, in the hero rigs' format ({@link HeroRig#parse}); textures in textures/entity/mobs/{id}.png.",
           " */", "public final class Fauna3Data {", "    private Fauna3Data() {}", "",
           "    /** Every rig's source text, in order. */", "    public static final List<String[]> SOURCES = List.<String[]>of("]
    for k, (rid, parts) in enumerate(chunks):
        src.append(f"            // {rid}")
        src.append("            new String[] {")
        for pi, block in enumerate(parts):
            src.append('                """')
            for ln in block:
                src.append("                " + ln)
            src.append('                """' + ("," if pi < len(parts) - 1 else ""))
        src.append("            }" + ("," if k < len(chunks) - 1 else ""))
    src.append("    );")
    src.append("}")
    with open(JAVA_OUT, "w") as f:
        f.write("\n".join(src) + "\n")
    return JAVA_OUT


# ---------------------------------------------------------------------------------------------------------------------
# The bestiary lines (the same parser as fauna2.py)

def creatures():
    import fauna as F
    import mobs_bestiary as MB
    out = []
    for path in DEFS:
        src = open(path).read()
        for st in [m.start() for m in MB.M_RE.finditer(src)]:
            block = src[st:src.find(".build());", st)]
            mid, name, theme, tier, model, sig = MB.M_RE.match(block).groups()
            sc = MB.SCALE_RE.search(block)
            glow = MB.GLOW_RE.search(block)
            out.append({"id": mid, "name": MB.unescape(name), "theme": theme, "tier": tier, "model": model, "sig": MB.unescape(sig),
                        "role": F.ROLE_RE.search(block).group(1), "talent": F.TALENT_RE.search(block).group(1),
                        "parts": [{"id": p, "name": MB.unescape(n), "ramp": r} for p, n, r in MB.P_RE.findall(block)],
                        "glow": ("#" + glow.group(1).lower()) if glow else "#ffffff", "scale": float(sc.group(1)) if sc else 1.0})
    return out


# ---------------------------------------------------------------------------------------------------------------------
# Items: eggs, the family materials, the beasts' parts, the bridle and the whistle; the bridle overlays

EGG = {
    "glimmerfin": ("#2a5474", "#9ae4f2"), "breathbell": ("#8cb8d8", "#e6b8ff"), "brook_otter": ("#52341e", "#dcc6a0"),
    "tidesteed": ("#1a4e58", "#9ff6ff"), "lantern_maw": ("#0c1018", "#7fffe0"), "chime_cricket": ("#2e263e", "#c8a4ff"),
    "wickling": ("#1e1410", "#ff8a2a"), "drip_lurker": ("#725634", "#ff7a3a"), "hushling": ("#c8c2b8", "#1ad0c0"),
    "thousandfoot": ("#221a2c", "#e08e3a"), "seep_heart": ("#120a20", "#b48cff"),
}

MATERIALS = ["tide_scale", "tide_pelt", "tide_gel", "hollow_crystal", "hollow_chitin", "hollow_wick"]
MATERIAL_NAMES = {"tide_scale": "Tide Scale", "tide_pelt": "Otter Pelt", "tide_gel": "Bell Jelly", "hollow_crystal": "Chime Crystal",
                  "hollow_chitin": "Hollow Chitin", "hollow_wick": "Wickling Ember"}
TOOLS = {"hunters_bridle": "Hunter's Bridle", "hunters_whistle": "Hunter's Whistle"}


def egg(mid, beast):
    import fauna2 as F2
    from pixelart import rng_for
    saved = dict(F2.EGG)
    F2.EGG.clear()
    F2.EGG.update(EGG)
    try:
        return F2.egg(mid, beast, rng_for("fauna3_egg", mid))
    finally:
        F2.EGG.clear()
        F2.EGG.update(saved)


def material_icon(mid):
    import fauna2 as F2
    from pixelart import Canvas
    P = F2.P
    if mid == "tide_scale":
        c = F2._icon([(8, 1), (14, 6), (12, 13), (8, 15), (4, 13), (2, 6)], P("#c8dce6", "#7c98aa", "#3a6e92", "#1d3c56"))
        for x in range(4, 13):
            for y in range(5, 12):
                if c.get(x, y)[3] and (x + y) % 3 == 0:
                    c.set(x, y, R.lerpc(R.hexc("#4ad8e8"), R.hexc("#b47cff"), (x - 4) / 8))
        return c
    if mid == "tide_pelt":
        c = F2._icon([(2, 4), (7, 2), (13, 3), (14, 9), (11, 14), (4, 13), (1, 9)], P("#9e7044", "#6a4628", "#52341e", "#3c2616"))
        for x, y in ((5, 6), (9, 5), (11, 9), (6, 10), (8, 8), (4, 9)):
            c.set(x, y, R.hexc("#dcc6a0"))
        return c
    if mid == "tide_gel":
        return F2._blob(["#dcf4ff", "#b4dcf0", "#8cb8d8", "#4a6a8a"], "#ffffff")
    if mid == "hollow_crystal":
        return F2._icon([(7, 1), (10, 4), (11, 11), (8, 15), (5, 12), (4, 5)], P("#ecdcff", "#c8a4ff", "#7c48b8", "#3a1e5a"))
    if mid == "hollow_chitin":
        c = F2._icon([(2, 5), (8, 2), (14, 5), (13, 12), (8, 14), (3, 12)], P("#544468", "#403452", "#2e263e", "#16101e"))
        for x in range(3, 13):
            if c.get(x, 8)[3]:
                c.set(x, 8, R.hexc("#74588e"))
        return c
    if mid == "hollow_wick":
        c = Canvas()
        for y in range(16):
            for x in range(16):
                d = math.hypot((x - 7.5) / 4.0, (y - 10.0) / 5.0)
                if d <= 1.0:
                    c.set(x, y, R.lerpc(R.hexc("#ffe89a"), R.hexc("#a8300c"), d))
        for y in range(2, 6):
            c.set(7 + (y % 2), y, R.hexc("#ffbe4a"))
        from pixelart import _outline
        return _outline(c)
    return None


def part_icon(pid):
    import fauna2 as F2
    from pixelart import Canvas, _outline
    if pid == "maw_lantern":
        c = Canvas()
        for y in range(16):
            for x in range(16):
                d = math.hypot((x - 8.0) / 5.0, (y - 9.0) / 5.0)
                if d <= 1.0:
                    c.set(x, y, R.lerpc(R.hexc("#e0fffa"), R.hexc("#2ac8b0"), d))
        for y in range(0, 5):
            c.set(8, y, R.hexc("#2a3442"))
        c.set(6, 7, (255, 255, 255, 255))
        return _outline(c)
    if pid == "seep_heartglass":
        # a heart of Deepglass: two lobes over a point, black glass lit violet at its facets, a white spark inside
        g = [R.hexc(h) for h in DEEPGLASS] + [R.hexc("#fbf7ff")]
        c = Canvas()
        for y in range(16):
            for x in range(16):
                dx, dy = x - 7.5, y - 7.5
                lobe = min(math.hypot(dx + 2.6, dy + 2.2), math.hypot(dx - 2.6, dy + 2.2)) <= 3.4
                point = dy >= -2.2 and abs(dx) <= 6.0 - (dy + 2.2) * 0.72
                if not (lobe or point):
                    continue
                d = math.hypot(dx, dy - 0.3) / 7.0
                facet = (x + 2 * y) % 7 == 0 or (2 * x - y) % 9 == 0
                v = 5.6 - 4.6 * d + (0.9 if facet else 0) - (0.9 if dx > 1.5 and dy > 0 else 0)
                c.set(x, y, g[max(0, min(7, int(round(v))))])
        c.set(7, 6, g[7])
        c.set(8, 7, g[6])
        c.set(6, 7, g[6])
        return _outline(c)
    if pid == "thousandfoot_mandible":
        return F2._icon([(3, 14), (2, 9), (4, 4), (8, 1), (13, 2), (14, 5), (10, 4), (7, 6), (6, 10), (6, 14)],
                        F2.P("#e08e3a", "#bc6c26", "#6e3810", "#4a2408"))
    return None


def tool_icon(tid):
    from pixelart import Canvas, _outline
    c = Canvas()
    if tid == "hunters_bridle":
        # a leather headstall: a loop of strap with two gold rings and a short rein
        for k in range(40):
            a = k / 40 * math.tau
            x, y = 7.5 + 5.2 * math.cos(a), 7.0 + 4.2 * math.sin(a)
            c.set(int(round(x)), int(round(y)), R.hexc("#5a361e" if k % 6 else "#3e2414"))
        for x, y in ((3, 7), (12, 7)):
            for dx, dy in ((0, -1), (1, 0), (0, 1), (-1, 0)):
                c.set(x + dx, y + dy, R.hexc("#e8c860"))
        for k in range(5):
            c.set(12 + (k % 2), 8 + k, R.hexc("#764a2a"))
        return _outline(c)
    if tid == "hunters_whistle":
        # a carved bone whistle on a cord, a Tide Scale set in it
        for x in range(3, 13):
            for y in range(6, 10):
                c.set(x, y, R.hexc("#e6dec4" if y == 6 else "#ccc2a2" if y < 9 else "#aa9f80"))
        for y in range(7, 9):
            c.set(3, y, R.hexc("#5e5440"))
        c.set(8, 7, R.hexc("#3a3020"))
        c.set(10, 7, R.hexc("#4ad8e8"))
        for k in range(6):
            c.set(12 + (k % 2), 10 + k // 2, R.hexc("#3e2414"))
        return _outline(c)
    return c


# ---------------------------------------------------------------------------------------------------------------------
# The seep heart's particle sprites (white with alpha: the game colours them violet; mobs.client.SeepParticles)

def _soft(size, r, hard=0.0, a_max=255):
    from pixelart import Canvas
    c = Canvas(size, size)
    m = (size - 1) / 2
    for y in range(size):
        for x in range(size):
            d = math.hypot(x - m, y - m) / r
            if d < 1:
                c.set(x, y, (255, 255, 255, int(a_max * (1 - d) ** (1.4 - hard))))
    return c


def seep_particles():
    import random
    from pixelart import Canvas
    out = {}
    out["seep_mote"] = [_soft(8, r, hard=0.35) for r in (2.0, 2.8, 3.4, 2.4)]
    shards = []
    for k, ang in enumerate((0.35, 1.1, 1.9, 2.6)):
        c = Canvas(8, 8)
        dx, dy = math.cos(ang), math.sin(ang)
        for s in range(-6, 7):
            t = s / 2.0
            x, y = 3.5 + dx * t, 3.5 + dy * t
            a = int(255 * (1 - abs(t) / 3.2))
            if a > 0:
                c.set(int(round(x)), int(round(y)), (255, 255, 255, max(a, c.get(int(round(x)), int(round(y)))[3])))
        shards.append(c)
    out["seep_shard"] = shards
    cracks = []
    for k in range(4):
        rnd = random.Random(_h("seep_crack_sprite", k))
        c = Canvas(32, 32)
        reach = 0.42 + 0.19 * k
        for y in range(32):
            for x in range(32):
                d = math.hypot(x - 15.5, y - 15.5) / 16
                if d < 0.32:
                    c.set(x, y, (255, 255, 255, int(70 * (1 - d / 0.32))))
        arms = 6 + k
        for a_ in range(arms):
            ang = (a_ + rnd.uniform(-0.3, 0.3)) / arms * math.tau
            wob = 0.0
            for s in range(60):
                t = s / 59
                if t > reach:
                    break
                wob += rnd.uniform(-0.25, 0.25)
                aa = ang + wob * 0.25
                x, y = int(round(15.5 + math.cos(aa) * t * 15.5)), int(round(15.5 + math.sin(aa) * t * 15.5))
                if 0 <= x < 32 and 0 <= y < 32:
                    c.set(x, y, (255, 255, 255, int(255 - 120 * t / max(0.01, reach))))
                if s % 12 == 6 and rnd.random() < 0.5:  # a short branch
                    ba = aa + rnd.choice((-0.6, 0.6))
                    for b in range(1, 4):
                        bx, by = int(round(x + math.cos(ba) * b)), int(round(y + math.sin(ba) * b))
                        if 0 <= bx < 32 and 0 <= by < 32:
                            c.set(bx, by, (255, 255, 255, 150))
        cracks.append(c)
    out["seep_crack"] = cracks
    waves = []
    for runed in (False, True):
        c = Canvas(32, 32)
        for y in range(32):
            for x in range(32):
                d = math.hypot(x - 15.5, y - 15.5)
                a = math.exp(-((d - 14.8) / (0.75 if runed else 1.0)) ** 2)
                if not runed and d < 14.8:
                    a = max(a, 0.12 * (d / 14.8) ** 3)
                if runed:
                    ang = (math.atan2(y - 15.5, x - 15.5) / math.tau) % 1.0
                    if abs(d - 12.8) < 0.6 and int(ang * 24) % 2 == 0:
                        a = max(a, 0.55)  # a dashed inner ring
                    if 12.5 < d < 15.5 and min(ang * 12 % 1.0, 1 - ang * 12 % 1.0) < 0.035:
                        a = max(a, 0.8)  # ticks every 30 degrees
                if a > 0.02:
                    c.set(x, y, (255, 255, 255, int(255 * min(1.0, a))))
        waves.append(c)
    out["seep_wave"] = waves
    return out


SEEP_PARTICLE_JSON = {"seep_mote": "seep_mote", "seep_drain": "seep_mote", "seep_shard": "seep_shard", "seep_crack": "seep_crack",
                      "seep_wave": "seep_wave"}


def bridle_overlay(model):
    """The Frost Stag's and the Dune Ray's bridle, painted on their own UV layouts (drawn over the body when bridled)."""
    from pixelart import Canvas
    leather, dark, gold = R.hexc("#764a2a"), R.hexc("#3e2414"), R.hexc("#e8c860")
    if model == "STAG":
        c = Canvas(64, 64)
        # the head box (u 0, v 24, 5x5x6) and the muzzle (u 22, v 24, 3x3x3): a noseband, a cheek strap, a brow band
        for y in range(27, 30):
            c.set(23, y, leather)  # muzzle west, middle column
            c.set(29, y, leather)  # muzzle east
        for x in range(25, 28):
            c.set(x, 25, leather)  # muzzle top
        for y in range(30, 35):
            c.set(3, y, leather)   # head west
            c.set(13, y, leather)  # head east
        for x in range(6, 11):
            c.set(x, 26, leather)  # head top, across the brow
        c.set(3, 32, gold)
        c.set(13, 32, gold)
        # the saddle pad on the body's top face (u 16, v 0, 8x16), draping over the sides (west u 0, east u 24, v 16)
        for y in range(4, 11):
            for x in range(16, 24):
                edge = y in (4, 10) or x in (16, 23)
                c.set(x, y, gold if edge and (x + y) % 2 == 0 else (dark if edge else leather))
        for x in list(range(4, 11)) + list(range(28, 35)):
            for y in range(16, 19):
                c.set(x, y, dark if y == 18 else leather)
        return c
    if model == "RAY":
        c = Canvas(64, 32)
        # the ray's body (u 0, v 0, 8x2x11): a pad on its back between two straps that run round under the wings
        for y in range(3, 9):
            for x in range(13, 17):
                c.set(x, y, gold if (x in (13, 16) and y in (3, 8)) else leather)
        for x in range(11, 19):
            c.set(x, 3, dark)
            c.set(x, 8, dark)
        for x in (3, 8):
            for y in range(11, 13):
                c.set(x, y, dark)            # west face
                c.set(x + 19, y, dark)       # east face
        return c
    return None


def build(out):
    all_rigs = rigs()
    for rid, rig, painter in all_rigs:
        skin, glow, blink = paint_full(rig, painter)
        out.entity("mobs/" + rid, skin)
        out.entity("mobs/" + rid + "_glow", glow)
        if blink is not None:  # the eyes shut, for the life layer's blink (SPEC §23)
            out.entity("mobs/" + rid + "_blink", blink[0])
            out.entity("mobs/" + rid + "_glow_blink", blink[1])
    for m in creatures():
        out.item(m["id"] + "_spawn_egg", egg(m["id"], m["role"] == "BEAST"))
        for p in m["parts"]:
            out.item(p["id"], part_icon(p["id"]))
    for mid in MATERIALS:
        out.item(mid, material_icon(mid))
    for tid in TOOLS:
        out.item(tid, tool_icon(tid))
    for name, frames in seep_particles().items():
        for k, c in enumerate(frames):
            out.particle(f"{name}_{k}", c)
    out.entity("mobs/frost_stag_bridle", bridle_overlay("STAG"))
    out.entity("mobs/dune_ray_bridle", bridle_overlay("RAY"))
    path = java(all_rigs)
    print(f"fauna3: {len(all_rigs)} rigs -> {os.path.relpath(path, ROOT)}")


# ---------------------------------------------------------------------------------------------------------------------
# Content: spawns, loot, items, lang, lairs, recipes, requests, the level road, gear tags

# Spawners per modifier (biomes: the tag #awaken:fauna/<group> of the biome table, tools/terrain/biomes.py)
SPAWNS = {
    "fauna_tidewater_rivers": [("glimmerfin", 30, 4, 6), ("brook_otter", 14, 1, 2)],
    "fauna_tidewater_lakes": [("glimmerfin", 8, 3, 5)],
    "fauna_tidewater_ocean": [("breathbell", 20, 2, 3), ("tidesteed", 12, 1, 2), ("glimmerfin", 6, 3, 5)],
    "fauna_tidewater_deep": [("lantern_maw", 1, 1, 1)],
    "fauna_hollows_dripstone": [("chime_cricket", 26, 1, 3), ("drip_lurker", 22, 1, 2), ("thousandfoot", 1, 1, 1)],
    "fauna_hollows_lush": [("wickling", 26, 2, 3), ("chime_cricket", 12, 1, 2)],
    "fauna_hollows_deep_dark": [("hushling", 24, 2, 3), ("chime_cricket", 6, 1, 1)],
}

DROPS = {
    "glimmerfin": [("awaken:tide_scale", 1, 1, 0.5, True), ("minecraft:cod", 0, 1, 1.0, False)],
    "breathbell": [("awaken:tide_gel", 1, 1, 0.6, True), ("minecraft:glow_ink_sac", 0, 1, 1.0, False)],
    "brook_otter": [("awaken:tide_pelt", 1, 1, 0.5, True), ("minecraft:salmon", 0, 1, 1.0, True)],
    "tidesteed": [("awaken:tide_scale", 1, 2, 0.4, True), ("minecraft:kelp", 1, 3, 1.0, False)],
    "lantern_maw": [("awaken:tide_scale", 3, 5, 1.0, True), ("awaken:tide_gel", 2, 3, 1.0, True), ("minecraft:prismarine_crystals", 2, 4, 1.0, True)],
    "chime_cricket": [("awaken:hollow_crystal", 1, 1, 0.5, True), ("minecraft:amethyst_shard", 0, 1, 1.0, False)],
    "wickling": [("awaken:hollow_wick", 1, 1, 0.5, True), ("minecraft:glow_berries", 0, 1, 1.0, False)],
    "drip_lurker": [("awaken:hollow_chitin", 1, 1, 0.45, True), ("minecraft:pointed_dripstone", 0, 1, 1.0, False)],
    "hushling": [("minecraft:echo_shard", 0, 1, 0.05, False), ("minecraft:bone", 0, 2, 1.0, True), ("minecraft:sculk", 0, 1, 1.0, False)],
    "thousandfoot": [("awaken:hollow_chitin", 3, 5, 1.0, True), ("awaken:hollow_crystal", 2, 3, 1.0, True), ("minecraft:amethyst_shard", 2, 4, 1.0, False)],
    "seep_heart": [("awaken:deepglass_shard", 3, 6, 1.0, True), ("awaken:hollow_crystal", 1, 3, 1.0, True), ("minecraft:amethyst_shard", 2, 5, 1.0, False)],
}

# Lairs: beast -> (style, spacing, separation, salt, step, terrain adaptation); biomes: the biome table's tag
# #awaken:has_lair/<beast> (tools/terrain/biomes.py)
LAIRS = {
    "lantern_maw": ("sunken_shrine", 96, 40, 318274911, "surface_structures", "beard_thin"),
    "thousandfoot": ("crystal_nest", 96, 40, 742019385, "underground_structures", "none"),
}

# What the System may ask you to bring to the Anchor (data/awaken/awaken/request/<item>.json, the requests module's format)
REQUESTS = {
    "tide_scale": ({"E": 6, "D": 8, "C": 10}, 12, 1.1, ["seen:glimmerfin", "seen:tidesteed", "seen:lantern_maw"], "Glimmerfin schools in rivers drop it", "tidewater"),
    "tide_pelt": ({"E": 4, "D": 6}, 9, 1.4, ["seen:brook_otter"], "Brook Otters on river banks drop it", "tidewater"),
    "tide_gel": ({"D": 6, "C": 8}, 9, 1.3, ["seen:breathbell"], "Breathbell Jellies drift in the sea", "tidewater"),
    "hollow_crystal": ({"E": 5, "D": 7, "C": 9}, 11, 1.3, ["seen:chime_cricket", "seen:thousandfoot"], "Chime Crickets sing beside ore in caves", "hollows"),
    "hollow_chitin": ({"D": 6, "C": 8, "B": 10}, 10, 1.5, ["seen:drip_lurker", "seen:thousandfoot"], "Drip Lurkers hang in dripstone caves", "hollows"),
    "hollow_wick": ({"E": 4, "D": 6}, 9, 1.2, ["seen:wickling"], "Wicklings walk the lush caves", "hollows"),
    "maw_lantern": ({"D": 2, "C": 2, "B": 3}, 5, 8.0, ["killed:awaken:lantern_maw"], "The Lantern Maw keeps a sunken shrine", "beast"),
    "thousandfoot_mandible": ({"D": 2, "C": 2, "B": 3}, 5, 8.0, ["killed:awaken:thousandfoot"], "The Thousandfoot nests in a crystal cave", "beast"),
}

# Hunter gear (merge/fauna tags): hides, cores and bindings
GEAR_TAGS = {
    "hunter_hide_e": ["awaken:tide_scale", "awaken:tide_pelt", "awaken:hollow_chitin"],
    "hunter_hide_d": ["awaken:tide_scale", "awaken:tide_pelt", "awaken:hollow_chitin"],
    "hunter_core_e": ["awaken:hollow_crystal"],
    "hunter_core_d": ["awaken:maw_lantern", "awaken:thousandfoot_mandible", "awaken:seep_heartglass"],
    "hunter_bind_e": ["awaken:tide_gel", "awaken:hollow_wick"],
}

# Every fauna family material (P2's and P3's): a bridle needs one of them
FAUNA_MATERIALS = ["mire_hide", "mire_glowsac", "canopy_venom", "canopy_scale", "dusk_shell", "dusk_feather", "blossom_dust", "blossom_fur",
                   "crag_horn", "crag_feather", "spore_dust", "spore_gel"] + MATERIALS

LEVEL_ROAD = {
    "wild_mounts": {"schema": 2, "level": 11, "title": "Wild mounts", "text": "A bridle recipe. Sneak and feed a Frost Stag, Dune Ray or Tidesteed its favourite food; when it trusts you, bridle it and ride.",
                    "grants": [{"type": "recipe", "ids": ["awaken:hunters_bridle"]}], "quiet": False,
                    "goal": {"now": "Tame a mount: sneak, feed it", "done": {"type": "counter", "counter": "awaken:mounts_tamed", "value": 1},
                             "requires": ["counter:awaken:mount_met"], "toast": "Now bridle it and ride", "priority": 45}},
    "mount_whistle": {"schema": 2, "level": 13, "title": "The whistle", "text": "A whistle recipe. Blow it and your mount comes; sneak and use it to send it home to rest.",
                      "grants": [{"type": "recipe", "ids": ["awaken:hunters_whistle"]}], "quiet": False,
                      "goal": {"now": "Ride your bridled mount", "done": {"type": "counter", "counter": "awaken:mount_rides", "value": 1},
                               "requires": ["counter:awaken:mounts_bridled"], "toast": "Whistle, and it comes", "priority": 42}},
}

RECIPES = {
    "hunters_bridle": {"type": "minecraft:crafting_shaped", "category": "equipment", "key": {"L": "minecraft:leather", "S": "minecraft:string", "M": "#awaken:fauna_materials", "I": "minecraft:iron_nugget"},
                       "pattern": [" L ", "SMS", "LIL"], "result": {"id": "awaken:hunters_bridle"}},
    "hunters_whistle": {"type": "minecraft:crafting_shapeless", "category": "equipment", "ingredients": ["minecraft:bone", "minecraft:iron_nugget", "#awaken:fauna_materials"],
                        "result": {"id": "awaken:hunters_whistle"}},
}


def merge_lang(entries):
    import fauna as F
    path = os.path.join(MERGE, "assets/awaken/lang/en_us.json")
    lang = json.load(open(path)) if os.path.exists(path) else {}
    lang.update(entries)
    F.write_json(path, dict(sorted(lang.items())))


def merge_tag(path, values):
    import fauna as F
    data = json.load(open(path)) if os.path.exists(path) else {"values": []}
    have = [v["id"] if isinstance(v, dict) else v for v in data["values"]]
    for v in values:
        if v not in have:
            data["values"].append({"id": v, "required": False})
    F.write_json(path, data)


def content():
    import fauna as F
    ms = creatures()
    for name, spawners in SPAWNS.items():
        F.spawn_modifier(name, spawners)
    F.DROPS.update(DROPS)
    lang = {}
    for m in ms:
        F.write_json(os.path.join(RES, "data/awaken/loot_table/entities", m["id"] + ".json"), F.loot_table(m))
        egg_id = m["id"] + "_spawn_egg"
        F.item_model(egg_id)
        lang["entity.awaken." + m["id"]] = m["name"]
        lang["item.awaken." + egg_id] = m["name"] + " Spawn Egg"
        for p in m["parts"]:
            F.item_model(p["id"])
            lang["item.awaken." + p["id"]] = p["name"]
    for mid in MATERIALS:
        F.item_model(mid)
        lang["item.awaken." + mid] = MATERIAL_NAMES[mid]
    for tid, name in TOOLS.items():
        F.item_model(tid)
        lang["item.awaken." + tid] = name
    lang["entity.awaken.seep_root"] = "Seep Roots"
    for name, sprite in SEEP_PARTICLE_JSON.items():
        F.write_json(os.path.join(RES, "assets/awaken/particles", name + ".json"),
                     {"textures": [f"awaken:{sprite}_{k}" for k in range(len(seep_particles()[sprite]))]})
    lang["awaken.fauna.whistle.sound"] = "Hunter's whistle"
    lang["subtitles.awaken.fauna.mount_whistle"] = "Hunter's whistle blows"
    merge_lang(lang)
    for beast, (style, spacing, separation, salt, step, adapt) in LAIRS.items():
        F.write_json(os.path.join(RES, "data/awaken/worldgen/structure/lair", beast + ".json"), {
            "type": "awaken:fauna_lair", "lair": style, "biomes": "#awaken:has_lair/" + beast, "step": step,
            "spawn_overrides": {}, "terrain_adaptation": adapt})
        F.write_json(os.path.join(RES, "data/awaken/worldgen/structure_set/lair", beast + ".json"), {
            "structures": [{"structure": "awaken:lair/" + beast, "weight": 1}],
            "placement": {"type": "minecraft:random_spread", "spacing": spacing, "separation": separation, "salt": salt}})
    tag_path = os.path.join(RES, "data/awaken/tags/worldgen/structure/fauna_lairs.json")
    tags = json.load(open(tag_path))
    for beast in LAIRS:
        if "awaken:lair/" + beast not in tags["values"]:
            tags["values"].append("awaken:lair/" + beast)
    F.write_json(tag_path, tags)
    for rid, data in RECIPES.items():
        F.write_json(os.path.join(RES, "data/awaken/recipe", rid + ".json"), data)
    F.write_json(os.path.join(RES, "data/awaken/tags/item/fauna_materials.json"), {"values": [{"id": "awaken:" + m, "required": False} for m in FAUNA_MATERIALS]})
    for item, (count, weight, value, any_of, hint, group) in REQUESTS.items():
        F.write_json(os.path.join(RES, "data/awaken/awaken/request", item + ".json"), {
            "schema": 1, "item": "awaken:" + item, "count": count, "weight": weight, "value": value, "any_of": any_of, "hint": hint, "group": group})
    for rid, data in LEVEL_ROAD.items():
        F.write_json(os.path.join(RES, "data/awaken/awaken/level_road", rid + ".json"), data)
    for tag, values in GEAR_TAGS.items():
        merge_tag(os.path.join(MERGE, "data/awaken/tags/item", tag + ".json"), values)
    print(f"fauna3: {len(ms)} creatures, {len(SPAWNS)} biome modifiers, {len(MATERIALS)} materials, {len(LAIRS)} lairs, "
          f"{len(REQUESTS)} requests, {len(LEVEL_ROAD)} level-road steps")


# ---------------------------------------------------------------------------------------------------------------------
# Voices (fauna_sounds.py's DSP), merged into the fauna sounds.json

def voices():
    import fauna_sounds as FS
    S, mixdown, at, voice, chirp, chime, reverb = FS.S, FS.mixdown, FS.at, FS.voice, FS.chirp, FS.chime, FS.reverb
    nb, thud, pad_step, squelch, wingbeat, hiss, rattle, chitter = FS.noise_burst, FS.thud, FS.pad_step, FS.squelch, FS.wingbeat, FS.hiss, FS.rattle, FS.chitter
    rumble, roar_layer, puff, growl, tumble, squeal = FS.rumble, FS.roar_layer, FS.puff, FS.growl, FS.tumble, FS.squeal
    OO, OH, EE, AH, NG, EH = FS.OO, FS.OH, FS.EE, FS.AH, FS.NG, FS.EH

    def bubbles(r, dur, n=5, lo=500, hi=1400):
        return mixdown(*[at(chirp(r, 0.035, f, f * 1.9, 0.5), r.uniform(0, max(0.01, dur - 0.05))) for f in [r.uniform(lo, hi) for _ in range(n)]])

    def swish(r, dur=0.3, fc=700):
        return nb(r, dur, fc, fc * 0.7, [(0, 0), (dur * 0.35, 1), (dur, 0)], lp=fc * 2)

    def crackle(r, dur=0.5, n=9):
        return mixdown(*[at(nb(r, 0.012, r.uniform(2500, 5000), 1500, [(0, 1), (0.012, 0)]) * r.uniform(0.4, 1.0), r.uniform(0, dur - 0.02)) for _ in range(n)])

    def drip(r, f=1700):
        return mixdown(chirp(r, 0.05, f * 0.8, f * 1.6, 0.6), at(chime(r, 0.35, f * 1.1, decay=14) * 0.4, 0.03))

    return {
        "glimmerfin": dict(
            ambient=[S(lambda r: reverb(mixdown(bubbles(r, 0.5, 4, 800, 1600), at(chime(r, 0.6, 2600, decay=9) * 0.35, 0.1)), 0.4, 0.3)),
                     S(lambda r: reverb(mixdown(chime(r, 0.5, 2900, decay=10) * 0.3, at(chime(r, 0.5, 3500, decay=11) * 0.25, 0.12), bubbles(r, 0.4, 3)), 0.4, 0.3))],
            hurt=[S(lambda r: chirp(r, 0.1, 2600, 3600))],
            death=[S(lambda r: mixdown(chime(r, 0.8, 2200, decay=5) * 0.6, at(chime(r, 0.7, 1600, decay=6) * 0.5, 0.2), bubbles(r, 0.6, 5)))],
            step=[S(swish, 0.25, 900), S(swish, 0.22, 1100)]),
        "breathbell": dict(
            ambient=[S(lambda r: reverb(mixdown(chime(r, 1.4, 1180, decay=2.2) * 0.6, puff(r, 0.6, 600) * 0.4), 0.8, 0.4)),
                     S(lambda r: reverb(mixdown(chime(r, 1.2, 1400, decay=2.6) * 0.5, bubbles(r, 0.8, 6, 400, 900)), 0.8, 0.4))],
            hurt=[S(lambda r: squelch(r, 0.25, 1400, 500, 3))],
            death=[S(lambda r: reverb(mixdown(chime(r, 1.6, 1000, decay=2.0) * 0.5, at(chime(r, 1.2, 700, decay=2.5) * 0.4, 0.3), bubbles(r, 1.2, 10, 300, 900)), 0.9, 0.4))],
            step=[S(lambda r: puff(r, 0.35, 380) * 0.8)]),
        "brook_otter": dict(
            ambient=[S(lambda r: mixdown(chirp(r, 0.07, 2400, 3300), at(chirp(r, 0.06, 2600, 3500), 0.1), at(chirp(r, 0.09, 2300, 3100), 0.2))),
                     S(lambda r: mixdown(voice(r, 0.25, [(0, 1600), (0.1, 2200), (0.25, 1500)], [(0, 0), (0.03, 1), (0.25, 0)], EE, tilt=1.8), at(chirp(r, 0.06, 2800, 3600), 0.3)))],
            hurt=[S(squeal, 1100, 0.25, 1.5)],
            death=[S(lambda r: voice(r, 0.7, [(0, 1500), (0.7, 700)], [(0, 0), (0.05, 1), (0.7, 0)], EE, tilt=1.6, breath=0.3))],
            step=[S(pad_step, 0.08, 500), S(pad_step, 0.07, 600)]),
        "tidesteed": dict(
            ambient=[S(lambda r: reverb(mixdown(voice(r, 1.1, [(0, 520), (0.25, 820), (0.6, 640), (1.1, 480)], [(0, 0), (0.1, 1), (1.1, 0)], OH, tilt=1.7, breath=0.35, vib=(9, 0.04)),
                                                bubbles(r, 1.0, 6, 300, 900) * 0.6), 0.7, 0.35)),
                     S(lambda r: reverb(mixdown(voice(r, 0.8, [(0, 700), (0.3, 950), (0.8, 600)], [(0, 0), (0.08, 1), (0.8, 0)], OO, tilt=1.8, breath=0.4, vib=(11, 0.05))), 0.6, 0.3))],
            hurt=[S(lambda r: voice(r, 0.3, [(0, 900), (0.06, 1300), (0.3, 800)], [(0, 0), (0.02, 1), (0.3, 0)], EH, tilt=1.5, nasal=0.3))],
            death=[S(lambda r: reverb(mixdown(voice(r, 1.4, [(0, 800), (1.4, 300)], [(0, 0), (0.08, 1), (1.4, 0)], OH, tilt=1.6, breath=0.5, vib=(7, 0.05)), bubbles(r, 1.3, 12, 200, 700)), 0.9, 0.4))],
            step=[S(wingbeat, 0.4, 420, 0.8), S(wingbeat, 0.38, 380, 0.9)]),
        "lantern_maw": dict(
            ambient=[S(lambda r: reverb(mixdown(rumble(r, 1.8, 70), at(chime(r, 1.4, 1900, decay=2.5) * 0.25, 0.3), bubbles(r, 1.6, 6, 200, 600) * 0.5), 1.2, 0.45)),
                     S(lambda r: reverb(mixdown(rumble(r, 1.4, 90), creak_(FS, r, 1.2) * 0.4), 1.2, 0.45))],
            hurt=[S(lambda r: reverb(growl(r, 90, 0.6, OH, 0.9, 2.2), 0.6, 0.35))],
            death=[S(lambda r: reverb(mixdown(rumble(r, 2.6, 60, [(0, 0), (0.1, 1), (2.6, 0)]), roar_layer(r, 70, 1.8, OO, 18, 1.8) * 0.6,
                                              *[at(chime(r, 0.8, r.uniform(1400, 2600), decay=5) * 0.25, r.uniform(0.3, 2.0)) for _ in range(5)],
                                              bubbles(r, 2.4, 14, 150, 500)), 1.4, 0.45))],
            step=[S(lambda r: swish(r, 0.6, 260) * 1.2)],
            roar=[S(lambda r: reverb(mixdown(roar_layer(r, 60, 2.2, OO, 22, 2.6, 0.9), at(rumble(r, 1.8, 50) * 0.8, 0.2), bubbles(r, 2.0, 18, 150, 600),
                                             at(chime(r, 1.0, 2200, decay=3) * 0.35, 0.1)), 1.5, 0.45))]),
        "chime_cricket": dict(
            ambient=[S(lambda r: mixdown(*[at(chime(r, 0.25, f, decay=14) * 0.55, k * 0.07) for k, f in enumerate((3500, 4200, 3500))])),
                     S(lambda r: mixdown(*[at(chime(r, 0.22, f, decay=15) * 0.5, k * 0.06) for k, f in enumerate((3900, 3900, 4600, 3900))]))],
            hurt=[S(chitter, 0.15, 30, 4500)],
            death=[S(lambda r: mixdown(*[at(chime(r, 0.5, r.uniform(2600, 4400), decay=8) * 0.45, r.uniform(0, 0.3)) for _ in range(5)]))],
            step=[S(chitter, 0.06, 40, 5000)]),
        "wickling": dict(
            ambient=[S(lambda r: mixdown(crackle(r, 0.6, 10) * 0.6, at(chirp(r, 0.06, 2200, 2900) * 0.6, 0.25))),
                     S(lambda r: crackle(r, 0.5, 8) * 0.7)],
            hurt=[S(lambda r: chirp(r, 0.1, 2800, 3600))],
            death=[S(lambda r: mixdown(hiss(r, 0.6, 3000, 1500, [(0, 0), (0.05, 1), (0.6, 0)]) * 0.6, crackle(r, 0.4, 6) * 0.5))],
            step=[S(pad_step, 0.05, 900), S(pad_step, 0.05, 1000)]),
        "drip_lurker": dict(
            ambient=[S(lambda r: reverb(mixdown(drip(r, 1500), at(drip(r, 1800), 0.5), at(chitter(r, 0.25, 22, 2500) * 0.5, 0.9)), 0.7, 0.4)),
                     S(lambda r: reverb(mixdown(drip(r, 1300), at(drip(r, 1250), 0.35), at(drip(r, 1700), 0.6)), 0.7, 0.4))],
            hurt=[S(lambda r: mixdown(chitter(r, 0.2, 28, 2800), thud(r, 0.15, 160, 90, 0.4, 0.6, 2200)))],
            death=[S(lambda r: mixdown(tumble(r, 1.0, 6, 0.8), chitter(r, 0.5, 18, 2400) * 0.6))],
            step=[S(chitter, 0.05, 35, 2600), S(chitter, 0.05, 40, 2900)]),
        "hushling": dict(
            ambient=[S(lambda r: mixdown(chitter(r, 0.35, 26, 5200) * 0.7, at(voice(r, 0.5, [(0, 300), (0.5, 260)], [(0, 0), (0.1, 0.6), (0.5, 0)], NG, breath=0.9, tilt=2.0), 0.3))),
                     S(lambda r: chitter(r, 0.5, 30, 5600) * 0.8)],
            hurt=[S(lambda r: voice(r, 0.3, [(0, 1600), (0.05, 2200), (0.3, 1200)], [(0, 0), (0.02, 1), (0.3, 0)], EE, tilt=1.4, drive_k=2.0))],
            death=[S(lambda r: voice(r, 0.9, [(0, 900), (0.9, 300)], [(0, 0), (0.05, 1), (0.9, 0)], AH, breath=0.6, growl=(30, 0.6)))],
            step=[S(pad_step, 0.07, 400), S(pad_step, 0.06, 450)]),
        "thousandfoot": dict(
            ambient=[S(lambda r: mixdown(rattle(r, 0.9, 42, 3000) * 0.7, at(chitter(r, 0.4, 24, 2600) * 0.6, 0.5))),
                     S(lambda r: mixdown(rattle(r, 0.7, 50, 2600) * 0.6, at(chime(r, 0.6, 2400, decay=6) * 0.25, 0.2)))],
            hurt=[S(lambda r: mixdown(hiss(r, 0.35, 3600, 1600) * 0.8, chitter(r, 0.3, 30, 2800) * 0.7))],
            death=[S(lambda r: mixdown(rattle(r, 1.6, 36, 2600) * 0.7, *[at(chime(r, 0.6, r.uniform(1800, 3600), decay=7) * 0.35, r.uniform(0.2, 1.3)) for _ in range(7)]))],
            step=[S(rattle, 0.18, 60, 2800), S(rattle, 0.16, 55, 3000)],
            roar=[S(lambda r: reverb(mixdown(hiss(r, 1.6, 2800, 1800, [(0, 0), (0.1, 1), (1.2, 0.8), (1.6, 0)]), rattle(r, 1.6, 48, 2400) * 0.8,
                                             roar_layer(r, 110, 1.4, AH, 40, 2.4, 0.4) * 0.5, *[at(chime(r, 0.7, f, decay=5) * 0.3, 0.15 * k) for k, f in enumerate((2400, 2850, 3200))]), 0.9, 0.35))]),
    }


def creak_(FS, r, dur):
    return FS.creak(r, dur, 8, 22)


# ---------------------------------------------------------------------------------------------------------------------
# The seep heart's voice (audiokit: 44.1 kHz, modal glass, drones, convolution rooms): a heart of Deepglass. Its beat
# is a deep thump with glass ringing in it; it hums; it cracks when struck; its wake is an in-breath, the plates
# cracking open and a hum swelling up; its death the room's light rushing into it, one last bright chord, a long
# exhale and two faint beats. Its blows: the rock groaning, roots bursting, a volley of shards, a pulse, a call.

SEEP_SUBTITLES = {"ambient": "The Seep Heart hums", "hurt": "The Seep Heart cracks", "death": "The Seep Heart falls silent",
                  "roar": "The Seep Heart wakes", "beat": "The Seep Heart beats", "rumble": "Roots push through the rock",
                  "roots": "Roots burst from the floor", "shards": "Deepglass shards fly", "pulse": "The Seep Heart pulses",
                  "call": "The Seep Heart calls to the walls"}
# volume and how far it carries (blocks)
SEEP_MIX = {"ambient": (0.7, 24), "hurt": (1.0, 16), "death": (1.0, 40), "step": (0.5, 12), "roar": (1.0, 48), "beat": (0.8, 16),
            "rumble": (1.0, 24), "roots": (1.0, 24), "shards": (1.0, 32), "pulse": (1.0, 40), "call": (1.0, 48)}


def seep_voices():
    import numpy as np
    import audiokit as A

    def n(sec):
        return A.ns(sec)

    def place(total, *parts):
        """parts: (signal, start seconds, gain) summed into total seconds."""
        out = np.zeros(n(total))
        for x, t, g in parts:
            i = n(t)
            k = max(0, min(len(x), len(out) - i))
            out[i:i + k] += x[:k] * g
        return out

    def trim(y, rel=1e-3):
        idx = np.nonzero(np.abs(y) > rel * (np.max(np.abs(y)) + 1e-12))[0]
        y = y[: (idx[-1] + 1 if len(idx) else len(y))]
        return A.declick(y, 0.002, 0.03)

    def room(x, t60=1.4, mix=0.3, seed=1, bright=0.35):
        ir = A.reverb_ir(t60=t60, predelay=0.015, bright=bright, size=0.9, seed=seed)
        wet = A.mono(A.convolve(x, ir))
        y = wet * mix
        y[:len(x)] += x * (1 - mix)
        return trim(y)

    def thump(f0, f1, dur, seed, knock=0.2):
        b = A.sub_boom(n(dur), f0, f1, t60=dur * 0.8)
        k = A.lowpass(A.noise(n(0.05), "white", seed), 700) * A.perc(n(0.05), 0.001, 0.035) * knock
        b[:len(k)] += k
        return b

    def glass(f, dur, seed, bright=0.6, hit=0.5, t60=None):
        return A.glass(f, n(dur), bright=bright, t60=t60 or dur * 0.8, seed=seed, hit=hit)

    def hum(f0, dur, seed, peak=0.5):
        x = A.drone(f0, n(dur), partials=(1, 2, 3, 5, 7), amps=(1, 0.55, 0.3, 0.12, 0.05), beat=0.25, seed=seed)
        return x * A.swell(n(dur), peak, 1.5)

    def heartbeat(seed, scale=1.0, pitch=1.0):
        return place(0.6, (thump(62 * pitch, 40 * pitch, 0.34, seed), 0, 1.0), (glass(587 * pitch, 0.5, seed + 1, 0.5, 0.0, 0.45), 0.004, 0.13),
                     (thump(72 * pitch, 46 * pitch, 0.28, seed + 2), 0.2, 0.7), (glass(880 * pitch, 0.4, seed + 3, 0.5, 0.0, 0.35), 0.204, 0.08)) * scale

    def suck(dur, seed, freqs=(659, 988, 1319)):
        """A chord of ringing glass played backwards: the light rushing in."""
        ch = sum(glass(f, dur, seed + k, 0.7, 0.8, dur * 0.7) for k, f in enumerate(freqs))
        air = A.highpass(A.noise(n(dur), "pink", seed + 9), 900) * A.line(n(dur), [(0, 0), (0.8, 0.5), (1, 1)]) ** 2 * 0.25
        return np.flip(ch) * 0.5 + A.lowpass(air, 3200)

    def exhale(dur, seed, f_hi=1400.0, f_lo=170.0):
        x = A.noise(n(dur), "pink", seed)
        fall = lambda t, f: 1.0 / np.sqrt(1.0 + (f / (f_hi * (f_lo / f_hi) ** min(1.0, max(0.0, t / dur)))) ** 4)
        y = A.stft_filter(x, fall)
        env = A.line(n(dur), [(0, 0), (0.06, 1), (1, 0)]) ** 1.6
        return y * env

    def crackle(dur, seed, rate=300, bright=0.85):
        return A.crackle(n(dur), rate=rate, seed=seed, bright=bright)

    def groan(f0, f1, dur, seed):
        f = A.glide(n(dur), f0, f1)
        tone = A.sine(f, n(dur)) + 0.35 * A.sine(f * 2.01, n(dur)) + 0.15 * A.sine(f * 3.02, n(dur))
        return A.lowpass(tone, 600) * A.perc(n(dur), 0.02, dur * 0.8)

    def grind(dur, seed, fc=380):
        x = A.lowpass(A.noise(n(dur), "brown", seed), fc)
        return x * (0.6 + 0.4 * A.smooth_noise(n(dur), 6, seed + 1))

    def S(fn):
        return fn

    return {
        "beat": [S(lambda r: room(heartbeat(r), 0.9, 0.22, 3))],
        "ambient": [
            S(lambda r: room(place(3.6, (hum(55, 3.6, r), 0, 0.55), (glass(880, 1.6, r + 1, 0.4, 0.0, 1.6), 0.6, 0.12),
                                   (glass(1175, 1.6, r + 2, 0.4, 0.0, 1.5), 1.4, 0.1), (glass(1319, 1.5, r + 3, 0.4, 0.0, 1.4), 2.3, 0.09),
                                   (heartbeat(r + 4), 1.0, 0.35)), 1.8, 0.4, 5)),
            S(lambda r: room(place(3.3, (hum(49, 3.3, r, 0.4), 0, 0.55),
                                   (A.bandpass(A.noise(n(3.0), "pink", r + 1), 420, 1.6) * A.swell(n(3.0), 0.45, 1.6), 0.1, 0.07),
                                   (crackle(3.0, r + 2, 7, 0.3), 0.2, 0.07), (glass(1047, 1.4, r + 3, 0.4, 0.0, 1.3), 1.8, 0.1)), 1.8, 0.4, 6))],
        "hurt": [
            S(lambda r: room(place(0.8, (crackle(0.25, r, 420, 0.95), 0, 0.55), (glass(1480, 0.5, r + 1, 0.9, 1.6), 0, 0.45),
                                   (glass(2230, 0.35, r + 2, 0.9, 1.0), 0.01, 0.28), (groan(96, 70, 0.6, r + 3), 0.02, 0.7)), 0.9, 0.24, 7)),
            S(lambda r: room(place(0.8, (crackle(0.22, r, 380, 0.95), 0, 0.55), (glass(1320, 0.5, r + 1, 0.9, 1.6), 0, 0.45),
                                   (glass(1980, 0.35, r + 2, 0.9, 1.0), 0.012, 0.28), (groan(106, 78, 0.55, r + 3), 0.02, 0.7)), 0.9, 0.24, 8))],
        "death": [S(lambda r: room(place(4.2, (suck(1.5, r), 0, 0.8),
                                         (glass(659, 2.2, r + 10, 0.8, 1.3), 1.45, 0.45), (glass(988, 2.0, r + 11, 0.8, 1.1), 1.46, 0.35),
                                         (glass(1319, 1.8, r + 12, 0.8, 1.0), 1.47, 0.3), (thump(55, 34, 0.8, r + 13, 0.3), 1.45, 1.0),
                                         (exhale(2.6, r + 14), 1.55, 0.75), (groan(70, 42, 2.4, r + 15), 1.6, 0.55),
                                         (heartbeat(r + 16, 1.0, 0.9), 2.55, 0.32), (heartbeat(r + 17, 1.0, 0.8), 3.2, 0.16)), 2.4, 0.38, 9))],
        "step": [S(lambda r: place(0.4, (A.bandpass(A.noise(n(0.3), "pink", r), 260, 3.0) * A.perc(n(0.3), 0.01, 0.22), 0, 0.6),
                                   (crackle(0.3, r + 1, 30, 0.4), 0.02, 0.2)))],
        "roar": [S(lambda r: room(place(3.0, (suck(0.62, r, (740, 1109, 1480)), 0, 0.7),
                                        (A.bandpass(A.noise(n(0.62), "pink", r + 1), 1200, 0.6) * A.line(n(0.62), [(0, 0), (1, 1)]) ** 2, 0, 0.25),
                                        (crackle(0.4, r + 2, 520, 0.95), 0.6, 0.7),
                                        *[(glass(f, 0.9, r + 3 + k, 0.9, 1.4), 0.6 + k * 0.025, 0.24) for k, f in enumerate((1109, 1480, 1865, 2217, 2637))],
                                        (A.sub_boom(n(1.8), 52, 30, 1.6), 0.6, 1.0), (thump(60, 40, 0.4, r + 9), 0.6, 0.6),
                                        (hum(55, 2.3, r + 10, 0.35), 0.65, 0.5), (hum(82.5, 2.3, r + 11, 0.4), 0.7, 0.25),
                                        (A.choir(110, n(2.2), "o", voices=5, seed=r + 12, bright=0.3) * A.swell(n(2.2), 0.45, 1.5), 0.75, 0.22),
                                        (glass(880, 1.4, r + 13, 0.4, 0.0, 1.2), 0.95, 0.1), (glass(1319, 1.3, r + 14, 0.4, 0.0, 1.1), 1.35, 0.09),
                                        (glass(1760, 1.2, r + 15, 0.4, 0.0, 1.0), 1.8, 0.07)), 2.2, 0.35, 10))],
        "rumble": [S(lambda r: room(place(1.8, (grind(1.8, r, 160) * A.line(n(1.8), [(0, 0.15), (1, 1)]), 0, 0.9),
                                          (crackle(1.7, r + 1, 45, 0.4) * A.line(n(1.7), [(0, 0.1), (1, 1)]), 0.05, 0.35),
                                          (A.bandpass(A.noise(n(1.6), "pink", r + 2), 180, 4.0) * (0.5 + 0.5 * A.smooth_noise(n(1.6), 9, r + 3)), 0.15, 0.35)), 1.2, 0.3, 11))],
        "roots": [S(lambda r: room(place(1.2, (crackle(0.16, r, 650, 0.85), 0, 0.8), (thump(82, 45, 0.45, r + 1, 0.4), 0, 1.0),
                                         (A.bandpass(A.noise(n(0.35), "white", r + 2), 1600, 0.7) * A.perc(n(0.35), 0.002, 0.28), 0.01, 0.45),
                                         (A.bandpass(A.noise(n(0.4), "pink", r + 3), 520, 2.0) * A.perc(n(0.4), 0.004, 0.32), 0.02, 0.4),
                                         (glass(1760, 0.6, r + 4, 0.8, 1.0, 0.5), 0.015, 0.22)), 1.0, 0.25, 12))],
        "shards": [S(lambda r: room(place(1.1, *[(glass(1900 + 1700 * ((k * 0.618) % 1.0), 0.45, r + k, 0.95, 1.3, 0.25), 0.034 * k + 0.012 * (k % 2), 0.26)
                                                  for k in range(7)],
                                          (A.bandpass(A.noise(n(0.4), "white", r + 20), 4200, 1.2) * A.swell(n(0.4), 0.25, 1.4), 0, 0.1)), 0.9, 0.22, 13))],
        "pulse": [S(lambda r: room(place(2.2, (np.flip(A.lowpass(A.noise(n(0.14), "pink", r), 900) * A.perc(n(0.14), 0.002, 0.12)), 0, 0.35),
                                         (A.sub_boom(n(1.6), 58, 28, 1.3), 0.13, 1.0),
                                         (A.lowpass(A.noise(n(0.8), "brown", r + 1), 260) * A.perc(n(0.8), 0.003, 0.6), 0.13, 0.8),
                                         (glass(440, 1.6, r + 2, 0.6, 0.6, 1.4), 0.14, 0.3), (glass(660, 1.5, r + 3, 0.6, 0.6, 1.3), 0.142, 0.24),
                                         (glass(880, 1.4, r + 4, 0.6, 0.6, 1.2), 0.144, 0.18),
                                         (A.bandpass(A.noise(n(0.6), "white", r + 5), 2000, 0.5) * A.perc(n(0.6), 0.004, 0.45), 0.15, 0.28)), 1.6, 0.3, 14))],
        "call": [S(lambda r: room(place(2.6, (A.choir(110, n(1.4), "u", voices=6, seed=r, bright=0.35) * A.swell(n(1.4), 0.6, 1.4), 0, 0.5),
                                        (A.choir(147, n(1.6), "o", voices=6, seed=r + 1, bright=0.4) * A.swell(n(1.6), 0.4, 1.4), 1.0, 0.5),
                                        (glass(880, 1.8, r + 2, 0.4, 0.0, 1.6), 0.4, 0.12), (glass(1319, 1.6, r + 3, 0.4, 0.0, 1.4), 1.2, 0.12),
                                        (grind(2.4, r + 4, 420), 0.1, 0.35), (crackle(2.2, r + 5, 18, 0.5), 0.2, 0.15)), 2.0, 0.4, 15))],
    }


def seep_sounds(sounds_json, lang):
    """Writes the seep heart's voice (fauna/seep_heart/<kind><n>.ogg, bit-exact) and its sounds.json events."""
    import numpy as np
    import audiokit as A
    import fauna_sounds as FS
    table = seep_voices()
    count = 0
    for kind, fns in table.items():
        entries = []
        for k, fn in enumerate(fns):
            x = np.asarray(fn(abs(FS.hash_str(f"seep_heart/{kind}/{k}")) % 100000), dtype=np.float64)
            x = x - np.mean(x)
            x = x / (np.max(np.abs(x)) + 1e-9) * 0.89
            A.write_ogg(os.path.join(FS.SND, "seep_heart", f"{kind}{k + 1}.ogg"), A.declick(x, 0.002, 0.02))
            count += 1
            vol, dist = SEEP_MIX[kind]
            entries.append({"name": f"awaken:fauna/seep_heart/{kind}{k + 1}", "volume": vol, "attenuation_distance": dist})
        key = "fauna.seep_heart." + kind
        if kind == "step":
            sounds_json[key] = {"sounds": entries, "subtitle": "subtitles.awaken.fauna.step"}
        else:
            sounds_json[key] = {"sounds": entries, "subtitle": "subtitles.awaken." + key}
            lang["subtitles.awaken." + key] = SEEP_SUBTITLES[kind]
    return count


VERBS = {"glimmerfin": ("shimmers", "flaps", "dies"), "breathbell": ("chimes", "squelches", "fades"), "brook_otter": ("chirps", "squeals", "dies"),
         "tidesteed": ("whinnies", "neighs", "dies"), "lantern_maw": ("rumbles", "growls", "dies"), "chime_cricket": ("chimes", "clicks", "shatters"),
         "wickling": ("crackles", "squeaks", "gutters out"), "drip_lurker": ("drips", "clicks", "crumbles"), "hushling": ("clicks", "shrieks", "dies"),
         "thousandfoot": ("rattles", "hisses", "dies")}
ROARS = {"lantern_maw": "roars from the deep", "thousandfoot": "hisses and rattles"}
SMALL = {"glimmerfin", "breathbell", "chime_cricket", "wickling"}


def sounds(only=None):
    """Every P3 voice and the seep heart's, or only the creatures named (the others' files and events are left alone)."""
    import fauna_sounds as FS
    import numpy as np
    table = {cid: kinds for cid, kinds in voices().items() if not only or cid in only}
    names = {m["id"]: m["name"] for m in creatures()}
    path = os.path.join(MERGE, "assets/awaken/sounds.json")
    sounds_json = json.load(open(path)) if os.path.exists(path) else {}
    lang = {}
    count = 0
    for cid, kinds in table.items():
        for kind, fns in kinds.items():
            entries = []
            for k, fn in enumerate(fns):
                rng = np.random.default_rng(abs(FS.hash_str(f"{cid}/{kind}/{k}")) % (2 ** 32))
                x = fn(rng)
                rel = f"fauna/{cid}/{kind}{k + 1}"
                FS.encode(x, os.path.join(FS.SND, cid, f"{kind}{k + 1}.ogg"))
                count += 1
                small, beast = cid in SMALL, cid in ROARS
                vol = {"ambient": 0.5 if small else 1.0 if beast else 0.8, "hurt": 0.6 if small else 1.0, "death": 0.7 if small else 1.0,
                       "step": 0.25 if small else 0.7 if beast else 0.45, "roar": 1.0}[kind]
                e = {"name": "awaken:" + rel, "volume": vol}
                if kind == "roar":
                    e["attenuation_distance"] = 48
                elif beast and kind in ("death", "ambient"):
                    e["attenuation_distance"] = 24
                entries.append(e)
            key = f"fauna.{cid}.{kind}"
            ev = {"sounds": entries}
            if kind == "step":
                # every event has a subtitle (the audio module's rule): steps share one
                ev["subtitle"] = "subtitles.awaken.fauna.step"
                lang["subtitles.awaken.fauna.step"] = "Footsteps"
            else:
                ev["subtitle"] = "subtitles.awaken." + key
                if kind == "roar":
                    lang["subtitles.awaken." + key] = f"{names[cid]} {ROARS[cid]}"
                else:
                    lang["subtitles.awaken." + key] = f"{names[cid]} {VERBS[cid][('ambient', 'hurt', 'death').index(kind)]}"
            sounds_json[key] = ev
    if not only:
        # the Hunter's whistle: two rising notes, the second held
        rng = np.random.default_rng(7)
        w = FS.mixdown(FS.whistle(rng, 1800, 2600, 0.18), FS.at(FS.whistle(rng, 2200, 3100, 0.45), 0.2))
        FS.encode(w, os.path.join(FS.SND, "mount", "whistle1.ogg"))
        sounds_json["fauna.mount_whistle"] = {"sounds": [{"name": "awaken:fauna/mount/whistle1", "volume": 0.9, "attenuation_distance": 48}],
                                              "subtitle": "subtitles.awaken.fauna.mount_whistle"}
        lang["subtitles.awaken.fauna.mount_whistle"] = "Hunter's whistle blows"
    if not only or "seep_heart" in only:
        count += seep_sounds(sounds_json, lang)
    with open(path, "w") as f:
        json.dump(dict(sorted(sounds_json.items())), f, indent=2)
        f.write("\n")
    merge_lang(lang)
    print(f"fauna3 sounds: {count + 1} files")


BG_DAY = (96, 122, 138, 255)
BG_NIGHT = (6, 9, 20, 255)


def _add_scale(tree, scale):
    tree["scale"] = (scale, scale, scale)
    tree["pivot"] = (0, 24 - 24 * scale, 0)


def pose_list(rig):
    out = [("idle", [("idle", 0.8, 1.0)])]
    if "walk" in rig.clips:
        out.append(("walk", [("idle", 0.8, 0.4), ("walk", rig.clips["walk"].length * 0.25, 1.0)]))
    if "run" in rig.clips:
        out.append(("run", [("run", rig.clips["run"].length * 0.3, 1.0)]))
    if "fly" in rig.clips:
        out.append(("swim", [("idle", 0.8, 1.0), ("fly", rig.clips["fly"].length * 0.3, 1.0)]))
    for hold in ("hang", "chirp", "ripen", "listen", "deflate", "carry", "rest", "beached", "ridden", "sleep"):
        if hold in rig.clips:
            out.append((hold, [("idle", 0.8, 1.0), (hold, 0.3, 1.0)]))
    if "erupt" in rig.clips:  # the seep heart's root burst
        out += [(f"erupt {t:.2f}s", [("erupt", t, 1.0)]) for t in (0.04, 0.12, 0.4, 1.1)]
    sig = None
    for _, mv in rig.moves.items():
        if mv + "_windup" in rig.clips:
            sig = mv
            break
    if sig:
        out.append((sig + " wind-up", [("idle", 0.8, 1.0), (sig + "_windup", rig.clips[sig + "_windup"].length, 1.0)]))
        out.append((sig + " strike", [(sig + "_strike", rig.clips[sig + "_strike"].length * 0.25, 1.0)]))
    elif "windup" in rig.clips:
        out.append(("wind-up", [("idle", 0.8, 1.0), ("windup", rig.clips["windup"].length, 1.0)]))
        out.append(("strike", [("strike", rig.clips["strike"].length * 0.25, 1.0)]))
    if "roar" in rig.clips:
        out.append(("roar", [("roar", rig.clips["roar"].length * 0.55, 1.0)]))
    out.append(("hurt", [("idle", 0.8, 1.0), ("hurt", 0.07, 1.0)]))
    out.append(("death", [("death", rig.clips["death"].length, 1.0)]))
    return out


def ingame_night(sk, gl, strength=0.85, dark=0.10):
    """What the game draws in a dark place: the skin almost black, the glow layer alpha-blended over it at the layer's
    strength (WildRenderer: full-bright, translucent, about 0.85 for a calm creature), no halo."""
    from PIL import Image
    base = sk.copy()
    px = base.load()
    for y in range(base.height):
        for x in range(base.width):
            r_, g_, b_, a_ = px[x, y]
            px[x, y] = (int(r_ * dark), int(g_ * dark), int(b_ * dark), a_)
    g2 = gl.copy()
    gp = g2.load()
    for y in range(g2.height):
        for x in range(g2.width):
            r_, g_, b_, a_ = gp[x, y]
            gp[x, y] = (r_, g_, b_, int(a_ * strength))
    return Image.alpha_composite(base, g2)


def preview(outdir, only=None):
    from PIL import Image, ImageDraw
    os.makedirs(outdir, exist_ok=True)
    made = []
    for rid, rig, painter in rigs(only):
        skin, glow = paint(rig, painter)
        sk, gl = skin.image(), glow.image()
        lit = Image.alpha_composite(sk, gl)
        rest = rig.tree(rig.sample([("idle", 0.4, 1.0)]))
        cell = (300, 260)
        views = [R.render(rest, lit, yaw=35, pitch=18, size=cell), R.render(rest, lit, yaw=100, pitch=8, size=cell),
                 R.render(rest, lit, yaw=-145, pitch=30, size=cell), R.render(rest, sk, yaw=35, pitch=18, size=cell, night=gl),
                 R.render(rest, ingame_night(sk, gl), yaw=35, pitch=18, size=cell)]
        row1 = Image.new("RGBA", (cell[0] * 5, cell[1]), BG_DAY)
        for i, im in enumerate(views):
            bg = Image.new("RGBA", cell, BG_NIGHT if i >= 3 else BG_DAY)
            bg.alpha_composite(im, (0, 0))
            row1.paste(bg, (i * cell[0], 0))
        unit = 4.0
        tnew = rig.tree(rig.sample([("idle", 0.4, 1.0)]))
        _add_scale(tnew, SCALE.get(rid, 1.0))
        new_im = R.render(tnew, lit, yaw=60, pitch=8, px=unit)
        pl = R.render(R.player_tree(), R.steve(), yaw=60, pitch=8, px=unit)
        bw, bh = BOX.get(rid, (1, 1))
        sc = SCALE.get(rid, 1.0)
        items = [("player", pl), (f"{rid} (box {bw * sc:.2f}x{bh * sc:.2f})", new_im)]
        W2 = sum(im.width for _, im in items) + 60 * len(items) + 140
        H2 = max(im.height for _, im in items) + 40
        row2 = Image.new("RGBA", (max(W2, cell[0] * 5), H2), (150, 170, 160, 255))
        d2 = ImageDraw.Draw(row2)
        x, ground = 20, H2 - 24
        for name, im in items:
            row2.alpha_composite(im, (x, ground - im.height + 8))
            d2.text((x, ground + 6), name, fill=(20, 24, 30, 255))
            x += im.width + 60
        d2.rectangle((x, ground - bh * sc * 16 * unit, x + bw * sc * 16 * unit, ground), outline=(200, 40, 40, 255))
        d2.line((0, ground, row2.width, ground), fill=(60, 80, 60, 255))
        poses = pose_list(rig)
        trees = [rig.tree(rig.sample(layers)) for _, layers in poses]
        yaw, pitch = 55, 12
        b = R.bounds_of(trees, yaw, pitch)
        pc = (220, 200)
        px = min((pc[0] - 16) / max(1e-6, b[1] - b[0]), (pc[1] - 24) / max(1e-6, b[3] - b[2]))
        row3 = Image.new("RGBA", (max(pc[0] * len(poses), cell[0] * 5), pc[1] + 16), (40, 44, 54, 255))
        d3 = ImageDraw.Draw(row3)
        for k, ((label, _), t) in enumerate(zip(poses, trees)):
            im = R.render(t, lit, yaw=yaw, pitch=pitch, px=px, bounds=b,
                          origin=(pc[0], pc[1], (pc[0] - (b[1] - b[0]) * px) / 2 - b[0] * px, pc[1] - 8 - b[3] * px))
            bg = Image.new("RGBA", pc, BG_DAY)
            bg.alpha_composite(im)
            row3.paste(bg, (k * pc[0], 0))
            d3.text((k * pc[0] + 4, pc[1] + 2), label, fill=(220, 225, 235, 255))
        Wt = max(row1.width, row2.width, row3.width)
        sheet = Image.new("RGBA", (Wt, row1.height + row2.height + row3.height + 40), (24, 26, 34, 255))
        sd = ImageDraw.Draw(sheet)
        sheet.paste(row1, (0, 0))
        sheet.paste(row2, (0, row1.height + 20))
        sheet.paste(row3, (0, row1.height + row2.height + 40))
        sd.text((6, row1.height + 4), f"{rid}: {len(rig.bones)} bones, {len(rig.cubes)} cubes, tex {rig.tex[0]}x{rig.tex[1]} "
                                      f"(used {rig.used} rows), {len(rig.clips)} clips", fill=(220, 225, 235, 255))
        path = os.path.join(outdir, f"p3_{rid}.png")
        sheet.save(path)
        sk.resize((sk.width * 4, sk.height * 4), Image.NEAREST).save(os.path.join(outdir, f"p3_{rid}_tex.png"))
        made.append(path)
    print("\n".join(made))


def main(args):
    if args and args[0] == "preview":
        preview(args[1] if len(args) > 1 else os.path.join(ROOT, "build/fauna3_preview"), args[2:] or None)
    elif args and args[0] == "content":
        content()
    elif args and args[0] == "sounds":
        sounds(args[1:] or None)
    elif args and args[0] == "java":
        java(rigs())
    else:
        print(__doc__)


if __name__ == "__main__":
    main(sys.argv[1:])
