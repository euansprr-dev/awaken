"""The Glass Desert's monsters (SPEC §60). Room: pale sand under a white sun. So they are dark glass and shadow, to
show against the glare, and each carries a white light along its edges."""
import math

from . import kit as K
from .kit import Rig, Ramp, hexc, p_bands, p_blotch, p_plates, p_stone, p_strokes, s, c, bump, SIDES2

GLASS = Ramp("#0a0c14", "#141824", "#202636", "#30384c", "#465068", "#667290")
SMOKE = Ramp("#120c14", "#1e1420", "#2e2030", "#423044", "#5c465e", "#7c6480")
SHELL = Ramp("#140e0a", "#221812", "#34261c", "#4a3828", "#624c38", "#7e644a")
CLOTH = Ramp("#120e0c", "#1e1814", "#2c241e", "#3e342a", "#52463a", "#6a5c4c")
EDGE, HOT = "#e6f0ff", "#ffffff"


def glass(P, cube, ramp=GLASS, base=2.6, faces=K.ALL, clear=True, a=170, seed=0, edges="all"):
    """Dark glass, lit only along its edges ("all" four of a face, or its two "sides": a spire's corners); clear: two
    pixels in three of the inside are left out in slanting streaks, so what is behind it shows as through a pane."""
    P.paint(cube, ramp, base, rim=0.4)
    for fname in faces:
        f, g = P.face(cube, fname), P.face(cube, fname, glow=True)
        if f.w < 3 or f.h < 2:
            continue
        for i, j in f.each():
            side = i in (0, f.w - 1)
            if side or (edges == "all" and j in (0, f.h - 1)):
                f.set(i, j, hexc(EDGE))
                g.set(i, j, hexc(EDGE, a))
            elif clear and (i + j + seed) % 3 != 0:
                f.set(i, j, (0, 0, 0, 0))


def blade(P, cube, ramp=GLASS, base=2.4, a=190):
    """A glass blade: dark, with one long edge lit (its narrowest long face) and nothing else."""
    P.paint(cube, ramp, base, rim=0.6)
    fs = {fn: P.face(cube, fn) for fn in K.ALL}
    longest = max(max(f.w, f.h) for f in fs.values())
    edge = min((fn for fn in K.ALL if max(fs[fn].w, fs[fn].h) == longest), key=lambda fn: (min(fs[fn].w, fs[fn].h), K.ALL.index(fn)))
    f, g = fs[edge], P.face(cube, edge, glow=True)
    for i, j in f.each():
        if min(f.w, f.h) == 1 or (i == 0 if f.h >= f.w else j == 0):
            f.set(i, j, hexc(EDGE))
            g.set(i, j, hexc(EDGE, a))


# ---------------------------------------------------------------------------------------------------------------------
# The Shardwalker (glass_husk): a bundle of long glass blades that walks on its points. It leans back before it
# sandblasts, and it shatters when it dies: do not be next to it.

SHARDS = [(0, 0, -4, 0, 0, 3, 15), (1.5, 0, 0, 40, 20, 3, 12), (-1.5, 0, 0, -40, -22, 3, 13), (0, 1.5, -24, 0, 0, 3, 11), (0, -1.5, 18, 90, 0, 2, 9)]


def shardwalker():
    r = Rig("glass_husk", (128, 64))
    r.shadow = 0.7
    body = K.at(r.bone("body", (0, 13, 0)), 13)
    body.cube("heart", -1.5, -2, -1.5, 3, 4, 3)
    for k, (x, z, rx, ry, rz, w, h) in enumerate(SHARDS):
        body.bone(f"shard{k}", (x, -1, z), (rx, ry, rz)).cube(f"pane{k}", -w / 2, -h, -0.5, w, h, 1)
    K.splay_pairs(body, 1, (2, 2), (-2, 2), 7, 16, rise=38, yaws=(42, -42), thick=1, mounts=True)
    r.pack()

    FOUR = K.tags(2)
    spread = lambda a: {"shard1": (0, 0, a), "shard2": (0, 0, -a), "shard3": (-a, 0, 0), "shard4": (a, 0, 0)}  # noqa: E731  (apart is positive)
    K.moves(r, blast="Sandblast")
    K.act(r, "blast", dict(spread(22), body={"rot": (-24, 0, 0), "pos": (0, 0.8, 1.5)}, shard0=(-10, 0, 0)), dict(spread(-12), body={"rot": (20, 0, 0), "pos": (0, -1, -2)}, shard0=(14, 0, 0)),
          w=1.0, length=0.5, t_hit=0.08, plant=FOUR, open_pose=dict(spread(30), body={"rot": (12, 0, 0), "pos": (0, -1.2, 0)}))
    K.plain(r, {"body": {"rot": (-10, 0, 0), "pos": (0, 0.6, 1)}, "leg0_r": (0, -30, 50), "shin0_r": (0, 0, 30)},
            {"body": {"rot": (10, 0, 0), "pos": (0, -0.5, -2.5)}}, plant=FOUR)   # the raised blade comes down on its own spot
    K.loop(r, "idle", 7.0, lambda p: dict(spread(1.5 * s(p) + 5 * bump(p, 0.55, 0.03)), shard0=(1.2 * s(p, 0.3), 0, 1.0 * s(p * 2)), body={"pos": (0, 0.4 * s(p * 2), 0)}), n=18)
    K.loop(r, "walk", 0.9, lambda p: dict(spread(3 * s(p * 2)), shard0=(2 * s(p * 2, 0.1), 0, 2 * s(p, 0.1)), body=(1.5 * s(p * 2), 0, 2 * s(p))))
    K.loop(r, "run", 0.6, lambda p: dict(spread(-6 + 3 * s(p * 2)), shard0=(8, 0, 0), body=(8 + 2 * s(p * 2), 0, 3 * s(p))))
    K.fallback_walk(r, K.alternating(2), axis="y")
    K.loop(r, "angry", 1.0, lambda p: dict(spread(12 + 6 * s(p * 2)), shard0=(-6, 0, 0), body={"pos": (0, -1, 0), "rot": (-6, 0, 0)}), plant=FOUR)
    K.flinch(r, dict(spread(-8), body={"pos": (0, -0.8, 1.5), "rot": (-8, 0, 6)}), plant=FOUR)
    burst = {f"shard{k}": {"pos": (5 * math.cos(k * 1.3), -6 - k, 5 * math.sin(k * 1.3)), "rot": (80 + 30 * k, 40 * k, 60 - 30 * k), "scale": (0.02, 0.02, 0.02)} for k in range(5)}
    mid = {f"shard{k}": {"pos": (3 * math.cos(k * 1.3), 3 - 0.5 * k, 3 * math.sin(k * 1.3)), "rot": (30 + 12 * k, 16 * k, 24 - 12 * k)} for k in range(5)}
    fold = {f"leg{t}": (0, 0, (-30 if t.endswith("l") else 30)) for t in FOUR}
    fold.update({f"shin{t}": (0, 0, (60 if t.endswith("l") else -60)) for t in FOUR})
    skip = tuple(f"pane{k}" for k in range(5))
    broke = K.planted(r, K.plus(mid, {"body": {"pos": (0, -0.6, 0)}}), FOUR)
    K.keyed(r, "death", 1.0, [(0, {}), (0.1, K.planted(r, dict(spread(-10), body={"pos": (0, 1.5, 0)}), FOUR)), (0.26, broke)]
            + K.fall(r, 0.26, broke, 0.7, K.plus(burst, fold, {"body": (10, 0, 30)}), steps=5, skip=skip)
            + [(1.0, K.on_floor(r, K.plus(burst, fold, {"body": (10, 0, 34)}), skip=skip))])
    return r


def paint_shardwalker(r, P):
    P.paint("heart", GLASS, 1.2)
    for fname in K.SIDES:
        K.lit(P, "heart", fname, 1, 1, 1, 2, EDGE, HOT, a=235)
    for k in range(5):
        blade(P, f"pane{k}", GLASS if k % 2 == 0 else SMOKE, 2.0 + 0.4 * (k % 3))
        for fname in ("north", "south"):  # a flaw or two in each, where the light catches
            f = P.face(f"pane{k}", fname)
            for j in range(3, f.h - 1, 4):
                f.set((j + k) % f.w, j, GLASS.at(4.2))
    blade(P, "thigh0_l", SMOKE, 2.6)
    blade(P, "shinb0_l", GLASS, 2.2)
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# The Sand Stalker (sand_stalker): flat and wide, with sickle jaws; buried, only the jaws show. It goes flat into the
# sand and comes up beside you, jaws first.

def sand_stalker():
    r = Rig("sand_stalker", (128, 64))
    r.shadow = 0.9
    body = K.at(r.bone("body", (0, 20, 0)), 20)
    body.cube("shell", -6, -2, -5, 12, 3, 10)
    body.cube("ridge", -3, -3, -4, 6, 1, 8)
    K.splay_pairs(body, 0, (5, 6, 5), (-3, 0, 3), 5, 8, rise=30, thick=1, mounts=True)
    head = body.bone("head", (0, -0.5, -5))
    head.cube("face", -3, -1.5, -2, 6, 2, 2)
    for side, nm in SIDES2:
        m = head.bone("mand_" + nm, (side * 2.5, -0.5, -2), (0, side * -26, 0))
        m.cube("sickle_" + nm, -0.5, -0.5, -6, 1, 1, 6, share=None if side > 0 else "sickle_l")
        m.bone("mand_tip_" + nm, (0, 0, -6), (0, side * 56, 0)).cube("hook_" + nm, -0.5, -0.5, -5, 1, 1, 5, share=None if side > 0 else "hook_l")
    r.look = [("head", 0.3, 0.1)]
    r.pack()

    SIX = K.tags(3)
    snap = lambda a: {"mand_l": (0, -a, 0), "mand_r": (0, a, 0)}  # noqa: E731  (open is positive)
    K.moves(r, burrow="Burrow Strike")
    K.act(r, "burrow", dict(snap(40), body={"pos": (0, -1.8, 0), "scale": (1.12, 0.4, 1.12)}), dict(snap(-22), body={"pos": (0, 3.5, -2), "rot": (-24, 0, 0)}),
          w=0.8, length=0.5, t_hit=0.08)
    K.plain(r, dict(snap(46), head=(-8, 0, 0), body={"pos": (0, 0, 1.2)}), dict(snap(-24), head=(6, 0, 0), body={"pos": (0, 0, -2.6)}),
            # its other swing: the snap again with its head rolled over
            second=(dict(snap(52), head=(-8, 0, 16), body={"pos": (0, 0, 1.2)}), dict(snap(-26), head=(6, 0, -14), body={"pos": (0, 0, -2.8)})), plant=SIX)
    K.loop(r, "idle", 5.0, lambda p: dict(snap(6 + 6 * s(p) + 14 * bump(p, 0.7, 0.03)), head=(0, 6 * s(p, 0.3), 0), body={"pos": (0, 0.3 * s(p * 2), 0)}), n=16)
    K.loop(r, "walk", 0.6, lambda p: dict(snap(8 + 4 * s(p * 2)), body=(0, 3 * s(p), 1.5 * s(p, 0.25)), head=(0, -3 * s(p), 0)))
    K.loop(r, "run", 0.4, lambda p: dict(snap(22 + 6 * s(p * 2)), body=(-3, 4 * s(p), 2 * s(p, 0.25)), head=(-4, -4 * s(p), 0)))
    K.fallback_walk(r, K.alternating(3), axis="y")
    K.loop(r, "angry", 0.7, lambda p: dict(snap(30 + 10 * s(p * 2)), head=(-10, 0, 0), body={"pos": (0, 0.8, 0), "rot": (-8, 0, 0)}), plant=SIX)
    K.flinch(r, dict(snap(-10), body={"pos": (0, 0, 1.5), "rot": (0, 0, 8)}, head=(10, 0, 0)), plant=SIX)
    curl = {f"leg{t}": (0, 0, (-50 if t.endswith("l") else 50)) for t in SIX}
    curl.update({f"shin{t}": (0, 0, (50 if t.endswith("l") else -50)) for t in SIX})
    over = dict(curl, **snap(50), body=(0, 0, 176))
    start = K.planted(r, dict(snap(40), body={"pos": (0, 1.5, 0)}), SIX)
    K.keyed(r, "death", 1.0, [(0, {}), (0.12, start)] + K.fall(r, 0.12, start, 0.7, over, steps=6)
            + [(0.82, K.on_floor(r, K.plus(over, {"body": (0, 0, -8)}))), (1.0, K.on_floor(r, K.plus(over, snap(-30))))])
    return r


def paint_sand_stalker(r, P):
    P.paint("shell", SHELL, 2.4, p_plates(4, 3, 0.8), rim=0.7, ao=0.6)
    P.paint("ridge", SHELL, 3.2, p_bands(2, 0.7), rim=0.6)
    f, g = P.face("shell", "top"), P.face("shell", "top", glow=True)
    for i in range(f.w):  # the rim of its shell, lit: all you see of it above the sand
        for j in (0, f.h - 1):
            f.set(i, j, hexc(EDGE))
            g.set(i, j, hexc(EDGE, 150))
    P.paint("face", SHELL, 1.8, rim=0.5)
    for i in (1, 4):
        K.lit(P, "face", "north", i, 0, 1, 1, EDGE, HOT, a=255)
    for nm in ("sickle_l", "hook_l"):
        blade(P, nm, GLASS, 2.4, a=220)
    P.paint("thigh0_l", SHELL, 3.0)
    P.paint("shinb0_l", SHELL, 2.2)
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# The Shimmer (heat_shimmer): thin glass plates stacked in a twist, almost clear, glinting at the edges. You see it
# best edge-on; it turns flat toward you before the gust.

PLATES = [(0, -1, 8), (1, -1, 6), (2, 1, 8), (3, 1, 7), (4, 1, 5)]   # (index, above or below the bead, width)


def shimmer():
    r = Rig("heat_shimmer", (64, 64))
    r.shadow = 0.3
    b = r.bone("body", (0, 14, 0))
    b.bone("bead", (0, 0, 0)).cube("bead", -1, -1, -1, 2, 2, 2)
    up = b.bone("plate0", (0, -2.5, 0), (0, 18, 0))
    up.cube("sheet0", -4, 0, -4, 8, 0, 8)
    up.bone("plate1", (0, -2.5, 0), (0, 18, 0)).cube("sheet1", -3, 0, -3, 6, 0, 6)
    dn = b.bone("plate2", (0, 2.5, 0), (0, -18, 0))
    dn.cube("sheet2", -4, 0, -4, 8, 0, 8, share="sheet0")
    d3 = dn.bone("plate3", (0, 2.5, 0), (0, -18, 0))
    d3.cube("sheet3", -3.5, 0, -3.5, 7, 0, 7)
    d3.bone("plate4", (0, 2.5, 0), (0, -18, 0)).cube("sheet4", -2.5, 0, -2.5, 5, 0, 5)
    r.pack()

    def stack(tw=0.0, gap=0.0):
        """Every plate turned tw further than the one before it, and gap px further from it (minus: closed up)."""
        q = {}
        for k, sgn, _ in PLATES:
            q[f"plate{k}"] = {"rot": (0, -sgn * tw, 0), "pos": (0, -sgn * gap, 0)}
        return q
    K.moves(r, gust="Mirage Gust", shard="Glass Gust")
    K.act(r, "gust", K.plus(stack(-18, -1.6), {"body": {"rot": (80, 0, 0), "pos": (0, 1, 1.5)}}), K.plus(stack(-18, 1.2), {"body": {"rot": (84, 0, 0), "pos": (0, 0, -3)}}),
          w=0.9, length=0.5, t_hit=0.08, open_pose=K.plus(stack(6, 1.0), {"body": {"rot": (56, 0, 10), "pos": (0, -2, 0)}}))
    K.act(r, "shard", K.plus(stack(30, -0.8), {"body": {"rot": (-20, 0, 0), "pos": (0, 0.8, 1)}}), K.plus(stack(-34, 0.6), {"body": {"rot": (22, 0, 0), "pos": (0, 0, -2.5)}}),
          w=0.7, length=0.45, t_hit=0.07)
    K.plain(r, K.plus(stack(10), {"body": {"rot": (0, 0, -40), "pos": (0, 0.5, 1)}}), K.plus(stack(-10), {"body": {"rot": (10, 0, 50), "pos": (0, 0, -3)}}))
    K.loop(r, "idle", 6.0, lambda p: K.plus(stack(10 * s(p), 0.4 * s(p * 2)), {"body": {"pos": (0, 0.9 * s(p * 2, 0.2), 0), "rot": (2 * s(p, 0.3), 20 * s(p, 0.1), 2 * s(p))}}), n=16)
    K.loop(r, "fly", 1.2, lambda p: K.plus(stack(6 * s(p), 0.3), {"body": {"rot": (14, 0, 3 * s(p))}}))
    K.loop(r, "angry", 0.8, lambda p: K.plus(stack(24 * s(p), -0.6), {"body": (-8, 0, 0)}))
    K.flinch(r, K.plus(stack(-20, 1.4), {"body": {"rot": (-16, 0, 24), "pos": (0, 0, 2)}}))
    apart = {f"plate{k}": {"rot": (20 * (k - 2), 30 * k, 16 * (2 - k)), "pos": (1.5 * (k - 2), -1.5 * k, 0)} for k, _, _ in PLATES}
    K.keyed(r, "death", 1.0, [(0, {}), (0.1, stack(-20, 1.6)), (0.4, K.plus(apart, {"body": {"pos": (0, -4, 0), "rot": (20, 0, 20)}})),
                              (0.75, K.on_floor(r, K.plus(stack(40, -2.2), {"body": (6, 30, 8)}))), (1.0, K.on_floor(r, K.plus(stack(44, -2.25), {"body": (3, 34, 5), "bead": {"scale": (0.02, 0.02, 0.02)}})))])     # the plates lie in a heap, flat on the floor
    return r


def paint_shimmer(r, P):
    K.lit_cube(P, "bead", EDGE, HOT, a=220, hot=lambda fn, i, j, f: True)
    for k, nm in enumerate(("sheet0", "sheet1", "sheet3", "sheet4")):
        glass(P, nm, GLASS if k % 2 == 0 else SMOKE, 2.8, faces=("top", "bottom"), a=140, seed=k)
        for fname in ("top", "bottom"):  # one glint across each
            f, g = P.face(nm, fname), P.face(nm, fname, glow=True)
            for i in range(1, f.w - 1):
                if 0 < f.w - 1 - i < f.h - 1 and i % 3 == k % 3:
                    f.set(i, f.w - 1 - i, hexc(EDGE))
                    g.set(i, f.w - 1 - i, hexc(EDGE, 110))
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# The Prism (prism_sentinel, the elite): a spire of glass hanging point down, a light climbing inside it. When the
# light reaches the tip, it fires. It breaks into pieces to move and takes a moment to come back together.

TIERS = (6, 5, 4, 3, 2, 1)


def prism():
    r = Rig("prism_sentinel", (64, 64))
    r.shadow = 0.5
    b = r.bone("body", (0, 3, 0))
    sp = b.bone("spire", (0, 0, 0), (0, 45, 0))     # seen corner-on
    sp.cube("crown", -4, -1, -4, 8, 1, 8)
    sp.cube("tier0", -3, 0, -3, 6, 3, 6)
    sp.bone("light", (0, 1.5, 0)).cube("spark", -1, -1, -1, 2, 2, 2)
    prev = sp
    for k in range(1, 6):
        w = TIERS[k]
        prev = prev.bone(f"tier{k}", (0, 3, 0))
        prev.cube(f"tierc{k}", -w / 2, 0, -w / 2, w, 3, w)
    r.pack()

    light = lambda y, k=1.0: {"light": {"pos": (0, -y, 0), "scale": (k, k, k)}}  # noqa: E731  (px down the spire from its top)
    part = lambda d, tw=0.0: {f"tier{k}": {"pos": (0, -d, 0), "rot": (0, tw, 0)} for k in range(1, 6)}  # noqa: E731
    K.moves(r, lance="Prism Lance", refract="Refract")
    K.act(r, "lance", K.plus(light(15.5, 1.3), part(0, 8), {"body": {"rot": (-14, 0, 0), "pos": (0, 1, 1)}}), K.plus(light(16.5, 2.4), {"body": {"rot": (10, 0, 0), "pos": (0, 0, 2)}}),
          w=1.1, length=0.5, t_hit=0.08, open_pose=K.plus(light(16.5, 0.02), part(1.0, -6), {"body": {"rot": (24, 0, 10), "pos": (0, -2, 0)}}))
    K.act(r, "refract", K.plus(light(6, 1.6), part(2.4, 40), {"body": {"pos": (0, 1.5, 0)}}), K.plus(light(2, 1.2), part(-0.4, -6), {"body": {"pos": (0, -1, 0)}}),
          w=0.7, length=0.6, t_hit=0.12, over=0.3, rest=light(0))
    K.plain(r, K.plus(light(12), {"body": {"rot": (-30, 0, 0), "pos": (0, 2, 1.5)}}), K.plus(light(16, 1.5), {"body": {"rot": (52, 0, 0), "pos": (0, -2, -4)}}))
    K.spin_loop(r, "idle", 6.0, lambda p: K.plus(light(5 + 4 * s(p)), {"body": {"pos": (0, 1.0 * s(p * 2), 0), "rot": (1.5 * s(p), 0, 1.5 * s(p, 0.25))}}), "spire", 1, 90, n=16)
    K.spin_loop(r, "fly", 2.0, lambda p: K.plus(light(4 + 2 * s(p)), {"body": (12, 0, 2 * s(p))}), "spire", 1, 90)
    K.spin_loop(r, "angry", 0.8, lambda p: K.plus(light(10 + 5 * s(p * 2), 1.3), {"body": (-8, 0, 0)}), "spire", 1, 90)
    K.flinch(r, K.plus(light(2, 0.5), part(1.2, 14), {"body": {"rot": (-14, 0, 22), "pos": (0, 0, 2)}}))
    lying = K.plus(light(8, 0.02), part(0.8), {"body": (84, 0, 8), "tier2": (0, 0, 14), "tier4": (0, 0, -20)})
    K.keyed(r, "death", 1.2, [(0, {}), (0.12, K.plus(light(16, 2.6), {"body": (-10, 0, 0)})), (0.3, K.plus(light(16, 0.02), part(1.6, 20), {"body": (16, 0, 12)})),
                              (0.8, K.on_floor(r, lying)), (0.92, K.on_floor(r, K.plus(lying, {"body": (-7, 0, 0)}))), (1.2, K.on_floor(r, lying))])
    return r


def paint_prism(r, P):
    glass(P, "crown", SMOKE, 2.8, faces=("top", "bottom"), clear=False, a=200)
    glass(P, "tier0", GLASS, 2.6, faces=K.SIDES, a=180, edges="sides")
    for k in range(1, 6):
        if TIERS[k] >= 3:
            glass(P, f"tierc{k}", GLASS if k % 2 == 0 else SMOKE, 2.6, faces=K.SIDES, a=180, seed=k, edges="sides")
        else:
            P.paint(f"tierc{k}", GLASS, 3.0, rim=0.8)
    K.lit_cube(P, "spark", EDGE, HOT, a=255, hot=lambda fn, i, j, f: True)
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# The Mirage (the_mirage, the Warden): a traveller's cloak and wide hat with nothing inside. It splits into several;
# only the real one's hat casts a shadow.

def mirage():
    r = Rig("the_mirage", (128, 128))
    r.shadow = 1.0
    body = r.bone("body", (0, 2, 0))
    body.cube("yoke", -5, -1, -3, 10, 3, 6)
    body.cube("hollow", -4, 2, -2, 8, 17, 4)
    body.cube("collar", -4, -3, -3, 8, 2, 6)
    body.bone("cape0", (0, 1, 3)).cube("cloth0", -5.5, 0, 0, 11, 21, 0)
    for side, nm, k in ((1, "l", 1), (-1, "r", 2)):
        body.bone(f"cape{k}", (side * 5.5, 1, 0)).cube(f"cloth{k}", 0, 0, -3, 0, 21, 6, share=None if k == 1 else "cloth1")
        body.bone(f"cape{k + 2}", (side * 3, 1, -3)).cube(f"cloth{k + 2}", -2.5, 0, 0, 5, 21, 0, share=None if k == 1 else "cloth3")
    head = body.bone("head", (0, -3, 0))
    head.cube("brim", -8, -2, -8, 16, 1, 16)
    head.cube("crownh", -3.5, -6, -3.5, 7, 4, 7)
    head.cube("shade", -3, -1, -3, 6, 2, 6)
    for side, nm in SIDES2:
        arm = body.bone("arm_" + nm, (side * 6, 0, 0), (0, 0, side * -6))
        arm.cube("sleeve_" + nm, -1.5, 0, -1.5, 3, 8, 3, mirror=side < 0, share=None if side > 0 else "sleeve_l")
        fore = arm.bone("fore_" + nm, (0, 8, 0), (-10, 0, 0))
        fore.cube("cuff_" + nm, -1.5, 0, -1.5, 3, 6, 3, mirror=side < 0, share=None if side > 0 else "cuff_l")
        if side < 0:
            fore.bone("staff", (0, 5, -1), (10, 0, 6)).cube("staffc", -0.5, -19, -0.5, 1, 27, 1)
    r.look = [("head", 0.6, 0.3)]
    r.pack()

    lagc = {"fore_l": 0.06, "fore_r": 0.06, "head": 0.05, **{f"cape{k}": 0.1 for k in range(5)}}

    def flare(a):
        """The cloak thrown open (a degrees; minus: wrapped tight)."""
        return {"cape0": (0.6 * a, 0, 0), "cape1": (0, 0, -a), "cape2": (0, 0, a), "cape3": (-0.3 * a, 0, -a), "cape4": (-0.3 * a, 0, a)}
    heap = {"body": {"scale": (1.1, 0.45, 1.1), "pos": (0, -12, 0)}, "head": (26, 0, 0), "arm_l": (0, 0, -30), "arm_r": (0, 0, 30)}
    K.moves(r, mirages="Mirages", shards="Sunshards", veil="Heat Mirage")
    K.act(r, "mirages", K.plus(flare(-5), {"body": (0, 40, 0), "head": (22, -20, 0), "arm_l": (0, 0, 10), "arm_r": (0, 0, -10)}),
          K.plus(flare(42), {"body": (0, -30, 0), "head": (-12, 16, 0), "arm_l": (-40, 0, -60), "arm_r": (-30, 0, 50)}), w=1.0, length=0.6, t_hit=0.12, lag=lagc)
    K.act(r, "shards", K.plus(flare(8), {"body": (0, 26, 0), "arm_l": (-124, 0, -20), "fore_l": (-30, 0, 0), "head": (-6, -14, 0)}),
          K.plus(flare(20), {"body": (0, -32, 0), "arm_l": (-64, 0, 44), "fore_l": (-6, 0, 0), "head": (8, 16, 0)}), w=0.9, length=0.55, t_hit=0.1, lag=lagc,
          open_pose=K.plus(flare(10), {"body": (6, -20, 0), "arm_l": (-8, 0, 10), "head": (30, 10, 0)}))
    K.act(r, "veil", K.plus(flare(-6), heap), K.plus(flare(26), {"body": {"scale": (0.94, 1.1, 0.94), "pos": (0, 0.8, 0)}, "head": (-10, 0, 0)}), w=0.7, length=0.6, t_hit=0.14, lag=lagc)
    K.plain(r, {"arm_r": (-104, 0, 0), "fore_r": (-20, 0, 0), "body": {"rot": (-6, 14, 0)}, "head": (-6, 0, 0)}, {"arm_r": (-22, 0, 0), "fore_r": (10, 0, 0), "body": {"rot": (8, -16, 0), "pos": (0, 0, -2)}},
            heavy=(K.plus(flare(10), {"arm_r": (-150, 0, 10), "arm_l": (-150, 0, -10), "body": (-10, 0, 0)}), K.plus(flare(30), {"arm_r": (-30, 0, 0), "arm_l": (-30, 0, 0), "body": {"rot": (14, 0, 0), "pos": (0, 0, -2)}})),
            lag=lagc)
    K.loop(r, "idle", 8.0, lambda p: K.plus(flare(2 + 2 * s(p * 2)), {"head": (3 * s(p, 0.2), 14 * s(p), 0), "body": {"pos": (0, 0.6 * s(p * 2), 0), "rot": (0, 3 * s(p, 0.1), 0)},
                                                                      "arm_l": (3 * s(p, 0.3), 0, 0), "arm_r": (2 * s(p, 0.6), 0, 0)}), n=16)
    K.loop(r, "walk", 1.4, lambda p: {"body": {"rot": (6 + s(p * 2), 0, 1.5 * s(p))}, "cape0": (14 + 3 * s(p * 2), 0, 0), "cape1": (8, 0, -2 * s(p)), "cape2": (8, 0, 2 * s(p, 0.5)),
                                      "cape3": (6 + 2 * s(p * 2, 0.2), 0, 0), "cape4": (6 + 2 * s(p * 2, 0.7), 0, 0), "head": (-4, 0, 0)})
    K.loop(r, "run", 0.9, lambda p: {"body": {"rot": (14 + 1.5 * s(p * 2), 0, 2 * s(p))}, "cape0": (30 + 5 * s(p * 2), 0, 0), "cape1": (20, 0, -4), "cape2": (20, 0, 4),
                                     "cape3": (16 + 3 * s(p * 2, 0.2), 0, 0), "cape4": (16 + 3 * s(p * 2, 0.7), 0, 0), "head": (-10, 0, 0), "arm_l": (20, 0, 0)})
    K.loop(r, "angry", 1.4, lambda p: K.plus(flare(14 + 6 * s(p * 2)), {"head": (16, 0, 3 * s(p)), "arm_l": (-20, 0, -20), "arm_r": (-10, 0, 10)}))
    K.flinch(r, K.plus(flare(20), {"head": (-24, 0, 10), "body": {"rot": (-6, 0, 4), "pos": (0, 0, 1.5)}}))
    # nothing inside: the cloak drops flat, and the hat comes down on top of it
    flat = {"body": {"scale": (1.2, 0.08, 1.2), "pos": (0, -20.5, 0)}, "head": {"scale": (1 / 1.2, 12.5, 1 / 1.2), "rot": (0, 30, 0), "pos": (0, 6, 0)},
            "arm_l": (0, 0, -70), "arm_r": (0, 0, 70)}
    K.keyed(r, "death", 1.4, [(0, {}), (0.2, K.plus(flare(30), {"head": (-20, 0, 0), "arm_l": (-40, 0, -40), "arm_r": (-40, 0, 40)})),
                              (0.7, K.plus(flare(10), {"body": {"scale": (1.15, 0.3, 1.15), "pos": (0, -15, 0)}, "head": {"scale": (1 / 1.15, 3.3, 1 / 1.15), "rot": (0, 16, 0)}})),
                              (1.0, flat), (1.4, flat)], lag=lagc)
    return r


def paint_mirage(r, P):
    P.paint("yoke", CLOTH, 2.8, p_strokes(3, 4, seed=2), rim=0.5)
    P.paint("collar", CLOTH, 3.4, rim=0.8)
    P.paint("hollow", GLASS, 0.2)
    for nm, faces in (("cloth0", ("north", "south")), ("cloth1", ("west", "east")), ("cloth3", ("north", "south"))):
        K.tatter(P, nm, CLOTH, 2.6, seed=len(nm) + ord(nm[-1]), faces=faces, fall=0.14)
        for fname in faces:  # folds, and dust along the hem
            f = P.face(nm, fname)
            for i, j in f.each():
                if f.get(i, j)[3] and i % 3 == 1:
                    f.set(i, j, CLOTH.at(1.6))
                elif f.get(i, j)[3] and j > f.h - 5 and (i + j) % 2 == 0:
                    f.set(i, j, CLOTH.at(4.4))
    P.paint("brim", CLOTH, 3.0, p_bands(2, 0.4), rim=0.6)
    K.fill(P, "brim", "bottom", GLASS.at(0.4))
    for fname in K.SIDES:  # the brim's edge: where the sun catches it
        f, g = P.face("brim", fname), P.face("brim", fname, glow=True)
        for i in range(f.w):
            f.set(i, 0, hexc(EDGE))
            g.set(i, 0, hexc(EDGE, 170))
    P.paint("crownh", CLOTH, 3.4, rim=0.8)
    for fname in K.SIDES:
        f = P.face("crownh", fname)
        for i in range(f.w):
            f.set(i, f.h - 1, CLOTH.at(0.8))  # the band
    P.paint("shade", GLASS, 0.0)
    P.paint("sleeve_l", CLOTH, 2.8, p_strokes(2, 4, seed=5), rim=0.4)
    P.paint("cuff_l", CLOTH, 3.2, rim=0.6)
    K.fill(P, "cuff_l", "bottom", GLASS.at(0.0))   # an empty cuff
    P.paint("staffc", SHELL, 3.4, p_bands(5, 0.6))
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------

STILL = {"blink": [0, 0]}
MONSTERS = [
    K.Monster("glass_husk", "The Shardwalker", shardwalker, paint_shardwalker, kind="crawler", box=(0.9, 1.7), sunk=("pane", "shinb"),   # it walks on points: they dig in
              motion=lambda r: {"gait": K.crawl_gait(K.room(r, K.legs_of(r), 0.6), duty=(0.72, 0.52), lift=(2.6, 4.0), swing=0.28, bob=0.4, speed=0.5),
                                "body": K.body(headLevel=0.0, twist=0.15, slope=0.7, land=1.2, shift=0.5)},
              life=dict(STILL, temperament="monster", breathDepth=0.0, idles={"settle": 2}, springHz=3.0, mass=1.4),
              voice={"ambient": [("block.amethyst_block.chime", 0.5, 0.6), ("block.glass.step", 0.6, 0.7)], "hurt": [("block.glass.hit", 0.9, 0.8), ("block.amethyst_cluster.hit", 0.7, 0.7)],
                     "death": [("block.glass.break", 1.0, 0.7), ("block.amethyst_cluster.break", 0.9, 0.7)], "step": [("block.amethyst_block.step", 0.35, 0.8)]}),
    K.Monster("sand_stalker", "The Sand Stalker", sand_stalker, paint_sand_stalker, kind="crawler", box=(1.2, 0.5), sunk=("sickle", "hook"),
              motion=lambda r: {"gait": K.crawl_gait(K.room(r, K.legs_of(r), 0.4), crouch=0.4, speed=1.1), "body": K.body(headLevel=0.0, twist=0.25, slope=0.95, land=0.8, shift=0.2)},
              life=dict(STILL, temperament="insect", idles={"settle": 2, "flick": 1}),
              voice={"ambient": [("block.sand.idle", 0.8, 0.8), ("entity.silverfish.ambient", 0.4, 0.6)], "hurt": [("entity.silverfish.hurt", 0.7, 0.6), ("block.sand.hit", 0.8, 0.8)],
                     "death": [("entity.silverfish.death", 0.8, 0.6), ("block.sand.break", 0.8, 0.8)], "step": [("block.sand.step", 0.3, 1.2)]}),
    K.Monster("heat_shimmer", "The Shimmer", shimmer, paint_shimmer, kind="floater", box=(0.6, 1.0), lift=2, grounds=False,
              motion={"fly": K.flight(fast=3, bank=0.3, tilt=0.25, climb=0.4)},
              life=dict(STILL, temperament="spirit", breathDepth=0.0, idles={"settle": 1}),
              voice={"ambient": [("entity.breeze.idle_air", 0.5, 1.2), ("block.amethyst_block.chime", 0.4, 1.3)], "hurt": [("block.glass.hit", 0.8, 1.3), ("entity.breeze.hurt", 0.4, 1.3)],
                     "death": [("block.glass.break", 0.9, 1.3), ("entity.breeze.death", 0.4, 1.3)]}),
    K.Monster("prism_sentinel", "The Prism", prism, paint_prism, kind="floater", box=(0.7, 1.6), lift=3, grounds=False,
              motion={"fly": K.flight(fast=3, bank=0.15, tilt=0.2, climb=0.4)},
              life=dict(STILL, temperament="spirit", breathDepth=0.0, idles={"settle": 1}, mass=1.6),
              voice={"ambient": [("block.beacon.ambient", 0.5, 1.2), ("block.amethyst_block.resonate", 0.5, 0.8)], "hurt": [("block.amethyst_cluster.hit", 0.9, 0.8), ("block.glass.hit", 0.7, 0.8)],
                     "death": [("block.beacon.deactivate", 0.8, 1.1), ("block.amethyst_cluster.break", 0.9, 0.8)]}),
    K.Monster("the_mirage", "The Mirage", mirage, paint_mirage, kind="slider", box=(1.0, 2.0), sunk=("cloth", "staffc"),
              motion={"body": K.body(headLevel=0.5, twist=0.3, land=1.0, shift=0.2)},
              life=dict(STILL, temperament="spirit", breathDepth=0.0, idles={"look": 3, "settle": 1}, bob=0.0, sway=0.8, arms=0.4, springHz=1.0, springDamping=0.12, springGain=1.5),
              voice={"ambient": [("entity.illusioner.ambient", 0.5, 0.7), ("block.sand.idle", 0.7, 0.6)], "hurt": [("entity.illusioner.hurt", 0.6, 0.7), ("block.wool.hit", 0.8, 0.7)],
                     "death": [("entity.illusioner.death", 0.7, 0.7), ("block.wool.break", 0.9, 0.6)], "step": [("block.sand.step", 0.25, 0.7)]}),
]
