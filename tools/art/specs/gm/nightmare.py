"""The Nightmare's monsters (SPEC §60). Room: pink and violet dark, furniture that is too big. So they are bedlinen
white and shadow, and each carries a pink light where its eyes open."""
import math

from . import kit as K
from .kit import Rig, Ramp, hexc, p_bands, p_blotch, p_plates, p_stone, p_strokes, s, c, bump, SIDES2

LINEN = Ramp("#5a5868", "#7c7a8c", "#a09eb0", "#c4c2d0", "#e2e0ea", "#f6f5fa")
SHADOW = Ramp("#07050c", "#0f0b18", "#191226", "#261c38", "#36294e", "#4a3a66")
BRASS = Ramp("#3a2608", "#5c3e0e", "#86601a", "#b0862a", "#d6ac44", "#f2d478")
PINK, HOT = "#ff7ac8", "#ffe0f2"


def folds(P, cube, base=4.2, seed=0, faces=K.SIDES):
    """Bedlinen: white, with long soft folds running down it."""
    P.paint(cube, LINEN, base, rim=0.3, ao=0.5)
    for fname in faces:
        f = P.face(cube, fname)
        for i, j in f.each():
            k = (i * 3 + seed) % 5
            if k == 0:
                f.set(i, j, LINEN.at(base - 1.4 - 0.3 * ((j // 3) % 2)))
            elif k == 1:
                f.set(i, j, LINEN.at(min(5, base + 0.6)))


def pink_eye(P, cube, fname="north"):
    """An open eye: pink, lit, a dark slit down it."""
    f, g = P.face(cube, fname), P.face(cube, fname, glow=True)
    for i, j in f.each():
        slit = f.w >= 2 and i == f.w // 2 and 0 < j < f.h - 1 and f.h > 2
        f.set(i, j, SHADOW.at(0) if slit else hexc(PINK))
        g.set(i, j, hexc(PINK, 120 if slit else 245))


# ---------------------------------------------------------------------------------------------------------------------
# The Sheet (sleepwalker): a bedsheet over something taller than a person, two hands pressing out from inside. It moves
# only when you blink. Keep your eyes on it.

def sheet():
    r = Rig("sleepwalker", (128, 64))
    r.shadow = 0.7
    body = r.bone("body", (0, 24, 0))                   # its pivot is the floor: there are no feet under it
    body.cube("drape", -4, -26, -3, 8, 26, 6)
    body.cube("yokeS", -5, -27, -3.5, 10, 4, 7)
    head = body.bone("head", (0, -27, 0))
    head.cube("crownS", -3, -6, -3, 6, 6, 6)
    for side, nm in SIDES2:
        head.bone("orb_" + nm, (side * 1.5, -3, -2.4)).cube("orbS_" + nm, -0.5, -1, -0.5, 1, 2, 1, share=None if side > 0 else "orbS_l")    # shut: inside the cloth
        h = body.bone("arm_" + nm, (side * 2.5, -18, -3))
        h.cube("press_" + nm, -1.5, -1.5, -1, 3, 3, 1, mirror=side < 0, share=None if side > 0 else "press_l")
        h.cube("fingers_" + nm, -1.5, -3.5, -0.7, 3, 2, 1, mirror=side < 0, share=None if side > 0 else "fingers_l")
    for k in range(4):
        a = math.tau * k / 4
        body.bone(f"skirt{k}", (3.5 * math.sin(a), -3, 3 * math.cos(a)), (0, math.degrees(a), 0)).cube(f"hem{k}", -4, 0, 0, 8, 4, 0, share=None if k == 0 else "hem0")
    r.look = [("head", 0.4, 0.2)]
    r.pack()

    def hands(out=0.0, eyes=0.0, dx=0.0, dy=0.0):
        """Both hands pressed `out` px further through the sheet (and slid dx, dy under it); the eyes `eyes` px out."""
        return {"arm_l": {"pos": (dx, dy, -out)}, "arm_r": {"pos": (-dx, dy * 0.6, -out)}, "orb_l": {"pos": (0, 0, -eyes)}, "orb_r": {"pos": (0, 0, -eyes)}}
    lagc = {"head": 0.05, **{f"skirt{k}": 0.12 for k in range(4)}}
    K.moves(r, step="Sleepstep")
    K.act(r, "step", K.plus(hands(-0.6), {"body": {"scale": (1.15, 0.42, 1.15)}, "head": (20, 0, 0)}), K.plus(hands(3.5, 0.9), {"body": {"scale": (0.92, 1.12, 0.92), "rot": (6, 0, 0)}, "head": (-10, 0, 0)}),
          w=0.6, length=0.6, t_hit=0.1, over=0.04, lag=lagc)
    K.plain(r, K.plus(hands(-0.6, 0.4), {"body": (-8, 0, 0)}), K.plus(hands(4.0, 0.9), {"body": {"rot": (12, 0, 0), "pos": (0, 0, -1.5)}, "head": (8, 0, 0)}), lag=lagc)
    K.loop(r, "idle", 8.0, lambda p: K.plus(hands(1.2 * bump(p, 0.3, 0.08) + 0.3, 0, 1.2 * s(p, 0.1), 2.0 * s(p * 2, 0.2)), {"body": (1.0 * s(p), 0, 1.5 * s(p, 0.25)), "head": (4 * s(p, 0.3), 0, 6 * bump(p, 0.7, 0.05))}),
           n=20)
    K.loop(r, "walk", 1.6, lambda p: K.plus(hands(0.6), {"body": (6 + s(p * 2), 0, 2 * s(p)), "head": (-4, 0, 3 * s(p, 0.1)), **{f"skirt{k}": (10 + 3 * s(p * 2, k / 4.0), 0, 0) for k in range(4)}}))
    K.loop(r, "run", 1.0, lambda p: K.plus(hands(2.0, 0.5), {"body": (14 + 1.5 * s(p * 2), 0, 2.5 * s(p)), "head": (-10, 0, 0), **{f"skirt{k}": (22 + 4 * s(p * 2, k / 4.0), 0, 0) for k in range(4)}}))
    K.loop(r, "angry", 1.4, lambda p: K.plus(hands(2.4 + 0.8 * s(p * 2), 0.9, 0.6 * s(p), 0), {"body": (4, 0, 2 * s(p)), "head": (8, 0, 0)}))
    K.flinch(r, K.plus(hands(-0.8), {"body": {"rot": (-8, 0, 5), "scale": (1.04, 0.95, 1.04)}, "head": (-14, 0, 8)}))
    flat = K.plus(hands(-0.8), {"body": {"scale": (1.3, 0.06, 1.3)}})
    K.keyed(r, "death", 1.2, [(0, {}), (0.16, K.plus(hands(4.0, 0.9), {"body": {"scale": (0.94, 1.08, 0.94)}, "head": (-20, 0, 0)})), (0.6, K.plus(hands(-0.8), {"body": {"scale": (1.15, 0.4, 1.15)}})),
                              (0.9, flat), (1.2, flat)], lag=lagc)
    return r


def paint_sheet(r, P):
    folds(P, "drape", 4.2, seed=1)
    folds(P, "yokeS", 4.6, seed=2)
    folds(P, "crownS", 4.6, seed=3)
    f = P.face("crownS", "north")
    for i in (1, 4):
        for j in (2, 3):
            f.set(i, j, LINEN.at(2.2))      # two hollows where the cloth sinks into eyes
    pink_eye(P, "orbS_l", "north")
    for nm, base in (("press_l", 5.0), ("fingers_l", 4.8)):
        P.paint(nm, LINEN, base, rim=0.2)
    ff = P.face("fingers_l", "north")
    for i, j in ff.each():
        if i == 1:
            ff.set(i, j, LINEN.at(2.6))     # between the fingers
    fp = P.face("press_l", "north")
    for i, j in fp.each():
        if i in (0, fp.w - 1) or j == fp.h - 1:
            fp.set(i, j, LINEN.at(2.8))     # the cloth pulled tight round the palm
    for fname in ("north", "south"):
        K.tatter(P, "hem0", LINEN, 3.6, seed=2, faces=(fname,), fall=0.3)
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# The Night Terror (night_terror): a mouth with wings and nothing else. You hear it before you see it. It dives from
# straight above.

def night_terror():
    r = Rig("night_terror", (64, 64))
    r.shadow = 0.5
    b = r.bone("body", (0, 14, 2))                      # the upper jaw; the hinge is at its back
    b.cube("palate", -4, -3, -8, 8, 3, 8)
    b.cube("fangs_f", -4, 0, -8, 8, 2, 0)
    b.cube("fangs_l", 4, 0, -8, 0, 2, 6)
    b.cube("fangs_r", -4, 0, -8, 0, 2, 6, share="fangs_l")
    b.cube("throat", -3, -1, -2, 6, 3, 2)
    jaw = b.bone("jaw", (0, 1, 0), (14, 0, 0))
    jaw.cube("gum", -4, 0, -8, 8, 2, 8)
    jaw.cube("teeth_f", -3.5, -2, -7.5, 7, 2, 0)
    jaw.cube("teeth_l", 3.5, -2, -7.5, 0, 2, 5)
    jaw.cube("teeth_r", -3.5, -2, -7.5, 0, 2, 5, share="teeth_l")
    jaw.bone("mouth", (0, 0, -2)).cube("tongue", -1.5, -1, -5, 3, 1, 5)
    for side, nm in SIDES2:
        w = b.bone("wing_" + nm, (side * 4, -2, -3), (0, 0, side * -14))
        w.cube("membrane_" + nm, 0 if side > 0 else -9, 0, -4, 9, 0, 8, mirror=side < 0, share=None if side > 0 else "membrane_l")
        w.bone("wing_tip_" + nm, (side * 9, 0, 0), (0, 0, side * 26)).cube("tipN_" + nm, 0 if side > 0 else -6, 0, -3, 6, 0, 6, mirror=side < 0, share=None if side > 0 else "tipN_l")
    r.pack()

    flap = lambda a, t=0.0: {"wing_l": (0, 0, -a), "wing_r": (0, 0, a), "wing_tip_l": (0, 0, -t), "wing_tip_r": (0, 0, t)}  # noqa: E731  (up is positive)
    bite = lambda a: {"jaw": (a * 0.6, 0, 0), "body": {"rot": (-a * 0.4, 0, 0)}}  # noqa: E731  (how wide: the top lifts as the bottom drops)
    lagc = {"wing_tip_l": 0.05, "wing_tip_r": 0.05, "mouth": 0.08}
    K.moves(r, dive="Night Dive")
    downed = K.on_floor(r, K.plus(flap(-16, -24), {"jaw": (40, 0, 0), "body": (10, 0, 14)}))
    K.act(r, "dive", K.plus(flap(64, -40), bite(70), {"body": {"pos": (0, 1.5, 1.5)}, "mouth": (-20, 0, 0)}),
          K.plus(flap(-20, -20), {"jaw": (-14, 0, 0), "body": {"rot": (30, 0, 0), "pos": (0, -1, -5)}}), w=0.7, length=0.4, t_hit=0.07, lag=lagc, stuck=downed)
    K.plain(r, K.plus(flap(30), bite(50)), K.plus(flap(-20), {"jaw": (-14, 0, 0), "body": {"rot": (14, 0, 0), "pos": (0, 0, -3)}}), lag=lagc)
    K.loop(r, "idle", 1.2, lambda p: K.plus(flap(22 * s(p), 24 * s(p, -0.2)), {"jaw": (6 + 6 * s(p * 2), 0, 0), "body": {"pos": (0, 0.9 * s(p, 0.25), 0)}, "mouth": (6 * s(p, 0.3), 0, 0)}))
    K.loop(r, "fly", 0.5, lambda p: K.plus(flap(34 * s(p), 30 * s(p, -0.2)), {"jaw": (10, 0, 0), "body": {"rot": (8, 0, 0), "pos": (0, 0.7 * s(p, 0.25), 0)}}))
    K.loop(r, "angry", 0.5, lambda p: K.plus(flap(40 + 16 * s(p), 20 * s(p, -0.2)), bite(40 + 14 * s(p * 2))))
    K.flinch(r, K.plus(flap(50, -50), {"jaw": (-14, 0, 0), "body": {"rot": (-16, 0, 30), "pos": (0, 0, 2)}}))
    K.keyed(r, "death", 0.9, [(0, {}), (0.1, K.plus(flap(60, -40), bite(80))), (0.5, K.on_floor(r, K.plus(flap(-30, -30), {"jaw": (60, 0, 0), "body": (20, 0, 60)}))),
                              (0.9, K.on_floor(r, K.plus(flap(-40, -40), {"jaw": (50, 0, 0), "body": (10, 0, 170)})))], lag=lagc)
    return r


def paint_night_terror(r, P):
    P.paint("palate", SHADOW, 2.2, p_blotch("nt", 4, 0.8), rim=0.5)
    P.paint("gum", SHADOW, 1.8, rim=0.5)
    for nm, fname in (("palate", "bottom"), ("gum", "top")):     # the inside of the mouth: wet pink dark
        f, g = P.face(nm, fname), P.face(nm, fname, glow=True)
        for i, j in f.each():
            f.set(i, j, hexc("#5a1a44" if (i + j) % 2 else "#74245a"))
            g.set(i, j, hexc(PINK, 40))
    K.lit_cube(P, "throat", PINK, HOT, a=230, hot=lambda fn, i, j, f: fn == "north" and 0 < i < f.w - 1 and j == 1)
    P.paint("tongue", Ramp("#3a0e2a", "#5a1a44", "#7a2a5c", "#9a3e76", "#b8588e"), 2.4)
    for nm, faces in (("fangs_f", ("north", "south")), ("fangs_l", ("west", "east")), ("teeth_f", ("north", "south")), ("teeth_l", ("west", "east"))):
        up = nm.startswith("teeth")
        for fname in faces:
            f = P.face(nm, fname)
            for i, j in f.each():
                tip = (j == 0) if up else (j == f.h - 1)
                f.set(i, j, (0, 0, 0, 0) if (tip and i % 2 == 1) else LINEN.at(5 if not tip else 4.2))
    for nm in ("membrane_l", "tipN_l"):
        for fname in ("top", "bottom"):
            f = P.face(nm, fname)
            for i, j in f.each():
                ragged = j == f.h - 1 and i % 2 == 0
                f.set(i, j, (0, 0, 0, 0) if ragged else SHADOW.at(2.4 + 0.5 * (i % 3 == 0) - 0.15 * j))
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# Many Eyes (many_eyes): a low dark heap covered in shut eyelids. When it stares, every eye opens and you slow. They
# shut again, and that is when to go in.

EYES = [(-3, -2, -6, 0), (2.5, -3.2, -6, 0), (6, -2.5, -2.5, -90), (6, -3, 2.5, -90), (-6, -2, 2, 90), (-6, -3.4, -3, 90), (0, -6.6, -4, 0), (4, -6.4, 0, -90),
        (-4, -6.6, 1, 90), (1, -2.4, 6, 180)]       # (x, y, z, turn): where each eye sits on the heap


def many_eyes():
    r = Rig("many_eyes", (128, 64))
    r.shadow = 1.0
    body = K.at(r.bone("body", (0, 19, 0)), 19)
    body.cube("heap", -6, -5, -6, 12, 7, 12)
    body.cube("heap2", -4, -8, -4, 8, 3, 8)
    body.cube("heap3", -2, -10, -3, 5, 2, 5)
    K.splay_pairs(body, 1, (5, 6, 5), (-3, 0, 3), 5, 7, rise=30, thick=1, mounts=True)
    for k, (x, y, z, turn) in enumerate(EYES):
        o = body.bone(f"orb{k}", (x, y, z), (0, turn, 0))
        o.cube(f"ball{k}", -1, -1, -0.3, 2, 2, 1, share=None if k == 0 else "ball0")
        o.bone(f"shade{k}", (0, -1.5, -0.5)).cube(f"skin{k}", -1.5, 0, -0.1, 3, 3, 1, share=None if k == 0 else "skin0")   # the lid: a cap over the eye (so no pink shows past its edge) that draws up from its top
    r.pack()

    SIX = K.tags(3)
    n = len(EYES)

    def lids(open_, which=None, bulge=1.0):
        """Those eyes' lids drawn up (open_ 0..1), the eyes themselves swollen by `bulge`."""
        q = {}
        for k in (range(n) if which is None else which):
            q[f"shade{k}"] = {"scale": (1, max(0.1, 1 - 0.9 * open_), 1)}
            if bulge != 1.0:
                q[f"orb{k}"] = {"scale": (bulge, bulge, 1)}
        return q
    K.moves(r, stare="Staring")
    K.act(r, "stare", K.plus(lids(1.0), {"body": {"scale": (1.05, 1.1, 1.05), "pos": (0, 0.8, 0)}}), K.plus(lids(1.0, bulge=1.3), {"body": {"scale": (1.08, 1.04, 1.08), "pos": (0, 0.4, -1)}}),
          w=1.2, length=0.6, t_hit=0.1, plant=SIX, lag={f"shade{k}": 0.05 * k for k in range(n)}, open_pose={"body": {"scale": (1.06, 0.84, 1.06), "pos": (0, -0.6, 0)}})
    K.plain(r, K.plus(lids(1.0, (0, 1)), {"body": {"rot": (-10, 0, 0), "pos": (0, 0.8, 1.2)}}), K.plus(lids(1.0, (0, 1), 1.2), {"body": {"rot": (12, 0, 0), "pos": (0, -0.4, -2.6)}}), plant=SIX)

    def idle(p):
        q = {"body": {"scale": (1 + 0.02 * s(p * 2), 1 + 0.03 * s(p * 2, 0.25), 1 + 0.02 * s(p * 2))}}
        for k in range(n):   # one eye at a time comes half open, looks, and shuts
            q.update(lids(0.6 * bump(p, (0.07 + 0.19 * ((k * 7) % n)) % 1.0, 0.03), (k,)))
        return q
    K.loop(r, "idle", 10.0, idle, n=40)
    K.loop(r, "walk", 0.8, lambda p: {"body": {"rot": (0, 2 * s(p), 2 * s(p, 0.25)), "scale": (1, 1 + 0.04 * s(p * 2), 1)}})
    K.loop(r, "run", 0.5, lambda p: K.plus(lids(0.5, (0, 1, 6)), {"body": {"rot": (-3, 3 * s(p), 3 * s(p, 0.25)), "scale": (1, 1 + 0.05 * s(p * 2), 1)}}))
    K.fallback_walk(r, K.alternating(3), axis="y")
    K.loop(r, "angry", 1.0, lambda p: K.plus(lids(0.5 + 0.3 * s(p * 2)), {"body": {"pos": (0, 0.6, 0), "scale": (1.04, 1.06, 1.04)}}), plant=SIX)
    K.flinch(r, {"body": {"scale": (1.1, 0.86, 1.1), "pos": (0, -0.4, 1.2), "rot": (0, 0, 5)}}, plant=SIX)
    curl = {f"leg{t}": (0, 0, (-40 if t.endswith("l") else 40)) for t in SIX}
    curl.update({f"shin{t}": (0, 0, (40 if t.endswith("l") else -40)) for t in SIX})
    skip = tuple(f"skin{k}" for k in range(n))
    K.keyed(r, "death", 1.2, [(0, {}), (0.14, K.planted(r, K.plus(lids(1.0, bulge=1.4), {"body": {"scale": (1.08, 1.12, 1.08), "pos": (0, 1, 0)}}), SIX)),
                              (0.6, K.on_floor(r, K.plus(lids(0.3), curl, {"body": {"scale": (1.15, 0.6, 1.15)}}), skip=skip)),
                              (1.2, K.on_floor(r, K.plus(curl, {"body": {"scale": (1.25, 0.4, 1.25)}}), skip=skip))])
    return r


def paint_many_eyes(r, P):
    for nm, base in (("heap", 2.2), ("heap2", 2.6), ("heap3", 3.0)):
        P.paint(nm, SHADOW, base, p_blotch("me" + nm, 4, 0.9), rim=0.5, ao=0.6)
    pink_eye(P, "ball0", "north")
    for fname in ("west", "east", "top", "bottom", "south"):
        K.fill(P, "ball0", fname, SHADOW.at(1.2))
    P.paint("skin0", SHADOW, 2.4)
    f = P.face("skin0", "north")
    for i, j in f.each():
        f.set(i, j, SHADOW.at(1.0) if j == f.h - 1 else SHADOW.at(3.0 - 0.3 * j + 0.3 * (i == 1)))     # a lid of its own skin, a dark line where it shuts
    P.paint("thigh0_l", SHADOW, 3.0)
    P.paint("shinb0_l", SHADOW, 2.2)
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# The Tall One (the_tall_one, the elite): a long black coat on a stand, taller than the door. It is always in the
# doorway behind you. It cannot follow where the ceiling is low.

def tall_one():
    r = Rig("the_tall_one", (128, 128))
    r.shadow = 0.7
    body = K.at(r.bone("body", (0, 4, 0)), 4)
    body.cube("hipsT", -2, -1, -1.5, 4, 2, 3)
    K.legs2(body, "1", (1.5, 1, 0), 9, 11, 16, thick=(2, 1), foot=(2, 2, 4, -2))
    chest = body.bone("chest", (0, -1, 0), (3, 0, 0))
    chest.cube("coat", -5, -22, -3, 10, 26, 6)
    chest.cube("collarT", -3, -24, -2.5, 6, 2, 5)
    for k in range(2):
        chest.bone(f"cape{k}", (-2.5 + 5 * k, 4, 3)).cube(f"tailsT{k}", -2.5, 0, 0, 5, 9, 0, share=None if k == 0 else "tailsT0")
    head = chest.bone("head", (0, -24, 0))
    head.cube("gapT", -2, -3, -2, 4, 3, 4)
    head.cube("brimT", -4, -4, -4, 8, 1, 8)
    head.cube("bowler", -3, -7, -3, 6, 3, 6)
    for side, nm in SIDES2:
        arm = chest.bone("arm_" + nm, (side * 5.5, -21, 0), (2, 0, side * -3))
        arm.cube("sleeveT_" + nm, -1, 0, -1, 2, 14, 2, mirror=side < 0, share=None if side > 0 else "sleeveT_l")
        arm.bone("fore_" + nm, (0, 14, 0), (-6, 0, 0)).cube("cuffT_" + nm, -1, 0, -1, 2, 12, 2, mirror=side < 0, share=None if side > 0 else "cuffT_l")
    r.look = [("head", 0.6, 0.4), ("chest", 0.1, 0.1)]
    r.pack()

    legs = ("1_l", "1_r")
    lagc = {"head": 0.06, "fore_l": 0.08, "fore_r": 0.08, "cape0": 0.12, "cape1": 0.14}
    spread = lambda a, f=0.0, x=0.0: {"arm_l": (x, 0, -a), "arm_r": (x, 0, a), "fore_l": (f, 0, 0), "fore_r": (f, 0, 0)}  # noqa: E731  (out from its sides)
    K.moves(r, doorway="Doorway", lights="Lights Off")
    K.act(r, "doorway", K.plus(spread(4), {"chest": (58, 0, 0), "head": (24, 0, 0), "body": {"pos": (0, -3, 1)}}), K.plus(spread(14, -10), {"chest": (-8, 0, 0), "head": (-10, 0, 0), "body": {"pos": (0, 0.6, -1)}}),
          w=0.7, length=0.6, t_hit=0.14, plant=legs, lag=lagc)
    K.act(r, "lights", K.plus(spread(118, -20), {"chest": (-8, 0, 0), "head": (-12, 0, 0), "body": {"pos": (0, 0.6, 0.5)}}),
          K.plus(spread(-14, -30, -64), {"chest": (16, 0, 0), "head": (14, 0, 0), "body": {"pos": (0, -1, -1)}}), w=1.2, length=0.6, t_hit=0.12, plant=legs, lag=lagc,
          open_pose=K.plus(spread(2, 0, 8), {"chest": (30, 0, 0), "head": (26, 0, 0), "body": {"pos": (0, -1.5, 0)}}))
    K.plain(r, {"arm_r": (-136, 0, 10), "fore_r": (-30, 0, 0), "chest": (-6, 14, 0)}, {"arm_r": (-34, 0, -10), "fore_r": (10, 0, 0), "chest": (14, -18, 0), "body": {"pos": (0, -0.6, -1.5)}},
            plant=legs, lag=lagc)
    K.loop(r, "idle", 9.0, lambda p: {"chest": (1.0 * s(p), 0, 0.8 * s(p, 0.25)), "head": (3 * s(p, 0.2), 24 * (bump(p, 0.3, 0.06) - bump(p, 0.75, 0.06)), 0), "arm_l": (1.5 * s(p, 0.4), 0, 0),
                                      "arm_r": (1.5 * s(p, 0.6), 0, 0), "body": {"pos": (0, 0.4 * s(p * 2), 0)}}, n=20)
    K.loop(r, "walk", 1.3, lambda p: {"chest": (4 + 1.5 * s(p * 2), 3 * s(p), 2.5 * s(p)), "head": (-3 * s(p * 2, 0.1), 0, -2.5 * s(p, 0.1)), "arm_l": (8 * s(p), 0, 0), "arm_r": (8 * s(p, 0.5), 0, 0),
                                      "fore_l": (-4 + 4 * s(p, 0.1), 0, 0), "fore_r": (-4 + 4 * s(p, 0.6), 0, 0)})
    K.loop(r, "run", 0.8, lambda p: {"chest": (14 + 2 * s(p * 2), 4 * s(p), 3 * s(p)), "head": (-12, 0, 0), "arm_l": (20 + 10 * s(p), 0, -6), "arm_r": (20 + 10 * s(p, 0.5), 0, 6)})
    K.fallback_walk(r, [("leg1_l", 0.0, 1), ("leg1_r", 0.5, -1)], swing=(16, 24), lengths=(1.3, 0.8))
    K.loop(r, "angry", 1.6, lambda p: K.plus(spread(20 + 6 * s(p * 2), -16), {"chest": (10, 0, 0), "head": (16, 0, 4 * s(p)), "body": {"pos": (0, -1, 0)}}), plant=legs)
    K.flinch(r, K.plus(spread(16), {"chest": (-9, 6, 4), "head": (-12, 0, 0), "body": {"pos": (0, -0.6, 1.5)}}), plant=legs)
    knees = K.planted(r, {"chest": (20, 0, 0), "head": (24, 0, 0), "body": {"pos": (0, -5, 0)}}, legs)
    flat = K.plus(spread(40, 10, -80), {"body": (84, 0, 5), "chest": (2, 0, 0), "head": (10, 30, 0), "leg1_l": (-30, 0, 0), "leg1_r": (-44, 0, 0), "shin1_l": (30, 0, 0), "shin1_r": (48, 0, 0)})
    skip = ("tailsT0", "tailsT1")
    K.keyed(r, "death", 1.8, [(0, {}), (0.2, K.planted(r, {"chest": (-10, 0, 0), "head": (-14, 0, 0)}, legs)), (0.8, knees)] + K.fall(r, 0.8, knees, 1.4, flat, steps=6, skip=skip)
            + [(1.52, K.on_floor(r, K.plus(flat, {"body": (-4, 0, 0)}), skip=skip)), (1.8, K.on_floor(r, flat, skip=skip))], lag=lagc)
    return r


def paint_tall_one(r, P):
    P.paint("hipsT", SHADOW, 1.6)
    P.paint("coat", SHADOW, 2.0, p_strokes(3, 8, seed=6), rim=0.5, ao=0.6)
    f = P.face("coat", "north")
    for j in range(2, f.h - 4, 4):
        f.set(f.w // 2, j, SHADOW.at(4.6))      # buttons, all the way down
    for j in range(f.h):
        f.set(f.w // 2 - 1, j, SHADOW.at(0.6))  # where it closes
    P.paint("collarT", SHADOW, 2.8, rim=0.8)
    for fname in ("north", "south"):
        K.tatter(P, "tailsT0", SHADOW, 2.2, seed=5, faces=(fname,), fall=0.12)
    P.paint("gapT", SHADOW, 0.0)
    fg, gg = P.face("gapT", "north"), P.face("gapT", "north", glow=True)
    for i in (0, 3):
        fg.set(i, 1, hexc(PINK))
        gg.set(i, 1, hexc(PINK, 250))
    P.paint("brimT", SHADOW, 2.6, rim=0.8)
    P.paint("bowler", SHADOW, 2.4, rim=0.7)
    for fname in K.SIDES:
        fb = P.face("bowler", fname)
        for i in range(fb.w):
            fb.set(i, fb.h - 1, SHADOW.at(4.2))
    P.paint("sleeveT_l", SHADOW, 2.2, p_strokes(2, 6, seed=7), rim=0.4)
    P.paint("cuffT_l", SHADOW, 2.6, rim=0.5)
    K.fill(P, "cuffT_l", "bottom", SHADOW.at(0))
    P.paint("thigh1_l", SHADOW, 1.8)
    P.paint("shinb1_l", SHADOW, 2.2)
    P.paint("footb1_l", SHADOW, 3.0, rim=0.8)
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# The Waking (the_waking, the Warden): a giant of rumpled bedclothes, an alarm clock caged in its chest, ringing. The
# ringing builds before the call. Stop the clock and it sleeps for a moment.

def waking():
    r = Rig("the_waking", (128, 128))
    r.shadow = 1.3
    body = K.at(r.bone("body", (0, 10, 0)), 10)
    body.cube("pelvisW", -5, -2, -3.5, 10, 4, 7)
    K.legs2(body, "1", (3.5, 2, 0), 6, 7, 22, thick=(5, 4), foot=(6, 3, 6, -3))
    chest = body.bone("chest", (0, -2, 0), (6, 0, 0))
    chest.cube("duvet", -7, -15, -4.5, 14, 15, 9)
    for k in range(3):
        chest.cube(f"bar{k}", -3.5 + 3 * k, -12, -7.5, 1, 8, 1, share=None if k == 0 else "bar0")
    chest.cube("railu", -4.5, -13, -7.5, 9, 1, 3)
    chest.cube("raild", -4.5, -4, -7.5, 9, 1, 3, share="railu")
    clock = chest.bone("clock", (0, -8, -4.5))
    clock.cube("dialW", -3, -3, -2, 6, 6, 2)
    for side, nm in SIDES2:
        clock.cube("gong_" + nm, side * 2.2 - 1, -5, -2, 2, 2, 2, share=None if side > 0 else "gong_l")
    clock.bone("hammer", (0, -3.2, -1)).cube("striker", -0.5, -2, -0.5, 1, 2, 1)
    head = chest.bone("head", (0, -15, 0), (20, 0, 0))
    head.cube("pillow", -4, -6, -3, 8, 6, 6)
    for side, nm in SIDES2:
        head.bone("orb_" + nm, (side * 2, -3, -2.3)).cube("orbW_" + nm, -1, -0.5, -0.5, 2, 1, 1, share=None if side > 0 else "orbW_l")
        arm = chest.bone("arm_" + nm, (side * 8.5, -13, 0), (4, 0, side * -6))
        arm.cube("sleeveW_" + nm, -2, 0, -2, 4, 11, 4, mirror=side < 0, share=None if side > 0 else "sleeveW_l")
        fore = arm.bone("fore_" + nm, (0, 11, 0), (-14, 0, 0))
        fore.cube("cuffW_" + nm, -2, 0, -2, 4, 9, 4, mirror=side < 0, share=None if side > 0 else "cuffW_l")
        fore.cube("mitt_" + nm, -2.5, 9, -2.5, 5, 4, 5, mirror=side < 0, share=None if side > 0 else "mitt_l")
    for k in range(3):
        chest.bone(f"cape{k}", (-5 + 5 * k, 0, 4.5)).cube(f"blanket{k}", -2.5, 0, 0, 5, 8 + 2 * (k % 2), 0, share=None)
    r.look = [("head", 0.4, 0.3)]
    r.pack()

    legs = ("1_l", "1_r")
    lagc = {"head": 0.06, "fore_l": 0.06, "fore_r": 0.06, **{f"cape{k}": 0.12 for k in range(3)}}
    eyes = lambda d: {"orb_l": {"pos": (0, 0, -d)}, "orb_r": {"pos": (0, 0, -d)}}  # noqa: E731  (open: out through the pillow)

    def arms(x, z, f=0.0):
        return {"arm_l": (x, 0, -z), "arm_r": (x, 0, z), "fore_l": (f, 0, 0), "fore_r": (f, 0, 0)}
    asleep = K.planted(r, K.plus(arms(6, 2, 10), {"chest": (12, 0, 0), "head": (34, 0, 0), "body": {"pos": (0, -1.5, 0)}}), legs)
    K.moves(r, call="Wake-Up Call", thrash="Thrash", dreams="Bad Dreams")
    woken = K.plus(eyes(0.8), arms(-10, 14, -10), {"chest": {"rot": (-10, 0, 0), "scale": (1.05, 1.03, 1.05)}, "head": (-40, 0, 0), "body": {"pos": (0, 0.5, 0.5)}})
    K.act(r, "call", woken, K.plus(eyes(0.9), arms(-30, 60, -20), {"chest": (12, 0, 0), "head": (-64, 0, 0), "body": {"pos": (0, -0.6, -1.5)}}), w=1.2, length=0.6, t_hit=0.1, plant=legs, lag=lagc,
          open_pose=asleep)
    # the wind-up is the alarm: the striker beats faster and wider between the gongs, the clock shaking on its bars
    ring = []
    for k in range(25):
        u = k / 24.0
        ring.append((1.2 * u, K.planted(r, K.plus(K.scaled(woken, u * u * (3 - 2 * u)), {"hammer": (0, 0, (34 * u + 6) * (1 if k % 2 else -1)), "clock": {"pos": (0.3 * u * (1 if k % 2 else -1), 0, 0)}}), legs)))
    ring[-1] = (1.2, K.planted(r, K.plus(woken, K.scaled(woken, 0.06)), legs))
    K.keyed(r, "call_windup", 1.2, ring, lag=lagc)
    K.act(r, "thrash", K.plus(eyes(0.8), arms(-150, 16, -10), {"chest": (-12, 0, 0), "head": (-30, 0, 0), "body": {"pos": (0, 0.6, 1)}}),
          K.plus(eyes(0.8), arms(-34, 6, 10), {"chest": (24, 0, 0), "head": (-10, 0, 0), "body": {"pos": (0, -2, -2)}}), w=1.0, length=0.6, t_hit=0.1, over=0.04, plant=legs, lag=lagc, open_pose=asleep)
    K.act(r, "dreams", K.plus(arms(-64, -40, -80), {"chest": (8, 0, 0), "head": (10, 0, 0)}), K.plus(eyes(0.8), arms(-24, 74, -10), {"chest": (-8, 0, 0), "head": (-30, 0, 0)}), w=1.0, length=0.6, t_hit=0.12,
          plant=legs, lag=lagc)
    K.plain(r, K.plus(eyes(0.6), {"arm_r": (-40, 0, 60), "fore_r": (-30, 0, 0), "chest": (0, 20, 0)}), K.plus(eyes(0.6), {"arm_r": (-70, 0, -40), "fore_r": (-10, 0, 0), "chest": (8, -26, 0), "body": {"pos": (0, -0.6, -1.5)}}),
            heavy=(K.plus(eyes(0.8), {"arm_r": (-160, 0, 10), "chest": (-10, 10, 0)}), K.plus(eyes(0.8), {"arm_r": (-30, 0, -6), "chest": (22, -10, 0), "body": {"pos": (0, -1.5, -1.5)}})), plant=legs, lag=lagc)
    K.loop(r, "idle", 8.0, lambda p: {"chest": {"scale": (1 + 0.025 * s(p), 1 + 0.02 * s(p), 1 + 0.025 * s(p)), "rot": (1.5 * s(p), 0, 1.0 * s(p, 0.25))}, "head": (6 * s(p, 0.1) + 10 * bump(p, 0.7, 0.04), 0, 5 * s(p, 0.3)),
                                      "hammer": (0, 0, 10 * s(p * 8)), "arm_l": (2 * s(p, 0.3), 0, 0), "arm_r": (2 * s(p, 0.6), 0, 0)}, n=32)
    K.loop(r, "walk", 1.4, lambda p: {"chest": (4 + 1.5 * s(p * 2), 4 * s(p), 3 * s(p)), "head": (6 * s(p * 2, 0.15), 0, -4 * s(p, 0.1)), "arm_l": (10 * s(p), 0, 0), "arm_r": (10 * s(p, 0.5), 0, 0),
                                      "hammer": (0, 0, 14 * s(p * 4))})
    K.loop(r, "run", 0.9, lambda p: K.plus(eyes(0.6), {"chest": (12 + 2.5 * s(p * 2), 5 * s(p), 4 * s(p)), "head": (-20, 0, 0), "arm_l": (-20 + 16 * s(p), 0, -8), "arm_r": (-20 + 16 * s(p, 0.5), 0, 8),
                                                       "hammer": (0, 0, 24 * s(p * 6))}))
    K.fallback_walk(r, [("leg1_l", 0.0, 1), ("leg1_r", 0.5, -1)], swing=(16, 24), lengths=(1.4, 0.9))
    K.loop(r, "angry", 1.6, lambda p: K.plus(eyes(0.8), arms(-20, 20, -20), {"chest": (-4, 0, 0), "head": (-30, 0, 4 * s(p)), "hammer": (0, 0, 30 * s(p * 8)), "body": {"pos": (0, -1, 0)}}), plant=legs, n=32)
    K.flinch(r, K.plus(eyes(0.9), {"chest": (-8, 6, 4), "head": (-30, 0, 8), "arm_l": (-10, 0, -20), "arm_r": (-10, 0, 20), "body": {"pos": (0, -0.6, 1.5)}}), plant=legs)
    knees = K.planted(r, K.plus(arms(10, 4, 10), {"chest": (16, 0, 0), "head": (30, 0, 0), "body": {"pos": (0, -3, 0)}}), legs)
    flat = K.plus(arms(-80, 40, 10), {"body": (80, 0, 6), "chest": (4, 0, 0), "head": (-10, 40, 0), "leg1_l": (-50, 0, 0), "leg1_r": (-60, 0, 0), "shin1_l": (60, 0, 0), "shin1_r": (74, 0, 0)})
    skip = tuple(f"blanket{k}" for k in range(3))
    K.keyed(r, "death", 2.0, [(0, {}), (0.25, K.planted(r, K.plus(eyes(0.9), {"chest": (-10, 0, 0), "head": (-40, 0, 0)}), legs)), (0.9, knees)] + K.fall(r, 0.9, knees, 1.5, flat, steps=6, skip=skip)
            + [(1.62, K.on_floor(r, K.plus(flat, {"body": (-4, 0, 0)}), skip=skip)), (2.0, K.on_floor(r, flat, skip=skip))], lag=lagc)
    return r


def paint_waking(r, P):
    def quilt(nm, base, seed):
        folds(P, nm, base, seed=seed, faces=())
        for fname in K.ALL:
            f = P.face(nm, fname)
            for i, j in f.each():
                if i % 4 == 0 or j % 4 == 0:
                    f.set(i, j, LINEN.at(base - 1.5))      # the stitching of a quilt
                elif (i % 4, j % 4) == (2, 2):
                    f.set(i, j, LINEN.at(min(5, base + 0.7)))
    quilt("duvet", 3.8, 1)
    quilt("pelvisW", 3.4, 2)
    for nm in ("sleeveW_l", "cuffW_l"):
        folds(P, nm, 4.0, seed=3)
    folds(P, "mitt_l", 4.4, seed=4)
    folds(P, "pillow", 4.6, seed=5)
    f = P.face("pillow", "north")
    for i in (1, 2, 5, 6):
        f.set(i, 3, LINEN.at(1.8))       # shut eyes: two creases
    pink_eye(P, "orbW_l", "north")
    P.paint("bar0", BRASS, 2.6, rim=0.6)
    P.paint("railu", BRASS, 3.0, rim=0.8)
    P.paint("dialW", BRASS, 3.4, rim=0.9)
    fd, gd = P.face("dialW", "north"), P.face("dialW", "north", glow=True)
    for i, j in fd.each():
        if math.hypot(i - 2.5, j - 2.5) < 2.7:
            fd.set(i, j, hexc("#f6eedd"))
            gd.set(i, j, hexc(PINK, 70))
    for i, j in ((2, 2), (2, 1), (3, 3), (4, 3)):
        fd.set(i, j, SHADOW.at(0))       # the hands, at a little after three
    P.paint("gong_l", BRASS, 4.2, rim=1.0)
    P.paint("striker", SHADOW, 2.0)
    for k in range(3):
        for fname in ("north", "south"):
            K.tatter(P, f"blanket{k}", LINEN, 3.4, seed=k, faces=(fname,), fall=0.2)
    folds(P, "thigh1_l", 3.6, seed=6)
    folds(P, "shinb1_l", 4.0, seed=7)
    folds(P, "footb1_l", 4.4, seed=8)
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------

STILL = {"blink": [0, 0]}
MONSTERS = [
    K.Monster("sleepwalker", "The Sheet", sheet, paint_sheet, kind="slider", box=(0.7, 2.1), sunk=("hem",),
              motion={"body": K.body(headLevel=0.5, twist=0.2, land=1.0, shift=0.2)},
              life=dict(STILL, temperament="spirit", breathDepth=0.0, idles={"look": 2, "settle": 1}, arms=0.0, bob=0.0, sway=0.9, springHz=1.0, springDamping=0.12, springGain=1.5),
              voice={"ambient": [("block.wool.step", 0.6, 0.5), ("entity.zombie_villager.ambient", 0.25, 0.5)], "hurt": [("block.wool.hit", 0.9, 0.7), ("entity.zombie_villager.hurt", 0.4, 0.5)],
                     "death": [("block.wool.break", 1.0, 0.6), ("entity.zombie_villager.death", 0.4, 0.5)], "step": [("block.wool.step", 0.3, 0.6)]}),
    K.Monster("night_terror", "The Night Terror", night_terror, paint_night_terror, kind="floater", box=(0.8, 0.6), lift=6,
              motion={"fly": K.flight(fast=5, bank=0.5, tilt=0.35, climb=0.6)},
              life=dict(STILL, temperament="spirit", breathDepth=0.0, idles={"settle": 1}, springHz=2.4),
              voice={"ambient": [("entity.phantom.ambient", 0.6, 0.6), ("entity.phantom.flap", 0.4, 0.8)], "hurt": [("entity.phantom.hurt", 0.7, 0.6)],
                     "death": [("entity.phantom.death", 0.8, 0.6)]}),
    K.Monster("many_eyes", "Many Eyes", many_eyes, paint_many_eyes, kind="crawler", box=(1.1, 0.9), sunk=("skin",),
              motion=lambda r: {"gait": K.crawl_gait(K.room(r, K.legs_of(r), 0.4), crouch=0.4, speed=1.0), "body": K.body(headLevel=0.0, twist=0.2, slope=0.9, land=1.0, shift=0.3)},
              life=dict(STILL, temperament="monster", breath=0.4, breathDepth=1.2, idles={"settle": 2}, mass=1.8),
              voice={"ambient": [("entity.spider.ambient", 0.4, 0.5), ("block.sculk.spread", 0.6, 0.7)], "hurt": [("entity.spider.hurt", 0.6, 0.5), ("block.sculk.hit", 0.8, 0.7)],
                     "death": [("entity.spider.death", 0.7, 0.5), ("block.sculk.break", 0.9, 0.7)], "step": [("block.sculk.step", 0.3, 0.9)]}),
    K.Monster("the_tall_one", "The Tall One", tall_one, paint_tall_one, kind="strider", box=(0.7, 3.2), sunk=("tailsT",),
              motion=lambda r: {"gait": K.gait("alternate", K.room(r, K.legs_of(r), 0.5), duty=(0.64, 0.46), lift=(2.2, 3.6), crouch=0.5, swing=0.36, bob=0.5, roll=0.03),
                                "body": K.body(headLevel=0.6, twist=0.2, slope=0.4, land=1.2, shift=0.6)},
              life=dict(STILL, temperament="monster", breathDepth=0.0, idles={"look": 4, "settle": 1}, arms=0.5, headSpeed=2.5, mass=1.8, springHz=1.0, springGain=1.4),
              voice={"ambient": [("entity.enderman.ambient", 0.4, 0.5), ("block.wool.step", 0.4, 0.5)], "hurt": [("entity.enderman.hurt", 0.6, 0.5), ("block.wool.hit", 0.7, 0.6)],
                     "death": [("entity.enderman.death", 0.7, 0.5), ("block.wood.break", 0.7, 0.6)], "step": [("block.wood.step", 0.4, 0.7)]}),
    K.Monster("the_waking", "The Waking", waking, paint_waking, kind="strider", box=(1.3, 2.4), sunk=("blanket", "mitt"),
              motion=lambda r: {"gait": K.gait("alternate", K.room(r, K.legs_of(r), 0.5), duty=(0.66, 0.48), lift=(2.2, 3.6), crouch=0.5, swing=0.36, bob=0.9, roll=0.06),
                                "body": K.body(headLevel=0.2, twist=0.2, slope=0.5, land=1.6, shift=0.7)},
              life=dict(STILL, temperament="beast", breath=0.35, breathDepth=1.2, idles={"settle": 3, "look": 1}, mass=3.2, arms=0.5, springHz=1.0, springGain=1.4, headSpeed=2.5),
              voice={"ambient": [("entity.warden.heartbeat", 0.7, 0.8), ("block.note_block.bell", 0.25, 1.6)], "hurt": [("entity.warden.hurt", 0.6, 0.9), ("block.wool.hit", 0.9, 0.6)],
                     "death": [("entity.warden.death", 0.7, 0.9), ("block.bell.use", 0.5, 1.4)], "step": [("block.wool.step", 0.8, 0.5)]}),
]
