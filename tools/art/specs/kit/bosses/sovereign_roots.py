"""The Sovereign of Roots (SPEC §35.11, docs/bosses/ROLLOUT.md): the Hollow Court's seventh Sovereign, a Caster. The
Hearthstone Toad, picked from its silhouette round 2026-10-29: "It made a home and closed every door."

A toad as big as a cottage that has lived under a hearth so long that the hearth has grown into it: its back is the old
hearthstone, split in two across the middle, black with soot at its edges, moss in its cracks and a fern growing out of
the split, and roots have come down through the crack and hang off its flanks to the floor. It sits as a toad sits, low
and broad, its front up on splayed forelegs and its great hind legs folded at its sides. Its skin is warty olive-brown,
pale and mottled underneath; behind each eye a great swollen gland. Its eyes are gold, with a black bar of a pupil
across each, and they glow; and when it opens its wide mouth there is firelight inside it, the hearth it swallowed.

Moves (SPEC §35.11): the Tongue and the Gulp (LIGHT, a string: its tongue flicks out at you and back, then it lunges with
its mouth wide and snaps it shut, and its eyes sink into its head as it swallows); the Leap (HEAVY, its gap closer: it
gathers its hind legs and jumps at you, landing on its belly); Hearth Ring (it rises high on its legs and drops flat);
Root Lance (it presses its hands to the floor and the roots on its back go down into it); Hold Fast (its tongue shoots out
a long way and holds); Rooted Ones (it croaks, its throat swelling huge, and the roots on its back stir); for whoever is
behind it, the Kick; and Thorn Halo (the signature: it blows itself up and stands tall on stiff legs as a toad does,
and its warts burst into thorns). Its intro: it sits as still as a stone with its eyes shut, then opens them, blinks and
croaks. Broken, it flattens on its belly with its legs splayed and its throat heaving; it dies drawing its legs in under
it and shutting its eyes, still as the stone on its back.
"""
import math

from kit import core, ik, parts
from kit.core import Move, keyed, stage_clips
import fauna_rig as R
from fauna_rig import Ramp, hexc, SIDES

DEG = math.pi / 180.0
K = 2  # every length below is in half pixels: the body is built, posed and keyed at twice what is written
ID = "sovereign_roots"
SCALE = 1.9  # the def's scale; the body is modelled at true size and the hitbox set round its body

# The Hearthstone Toad (docs/bosses/ROLLOUT.md, A). Written in half pixels (K): 50 from its snout to its rump and 44
# across its folded knees, its back 23 up and its eyes 28 (3.5 blocks at true size). Value story: the gold of its eyes and the fire in its mouth brightest, then the
# pale stone of the hearthstone and the cream of its belly, the moss, the olive-brown of its skin, the roots darkest.
SIDE = (1, -1, 1, -1)  # legs: 0 front left (+x), 1 front right, 2 back left, 3 back right
FRONT, BACK = (0, 1), (2, 3)
LIMB = {0: ("arm0", "fore0", "hand0"), 1: ("arm1", "fore1", "hand1"), 2: ("thigh2", "shin2", "foot2"), 3: ("thigh3", "shin3", "foot3")}
UPPER = {0: 8, 1: 8, 2: 12, 3: 12}
LOWER = {0: 8, 1: 8, 2: 12, 3: 12}
# the wrists and ankles at rest (x, up, z), each hand and foot flat on the floor: the forelegs splayed out with their
# elbows bowed, the hind legs folded up at its sides with the knees forward by its flanks and the long feet pointing ahead
HAND_Y = 1.5  # how far below the wrist each hand and foot begins (the forearm and shin run on down into it)
WRIST = HAND_Y + 2.0 + 0.15  # the wrists and ankles stand this high (half pixels), the hands and feet flat below them
REST_FOOT = {0: (16.0, WRIST, -17.0), 1: (-16.0, WRIST, -17.0), 2: (19.0, WRIST, 17.0), 3: (-19.0, WRIST, 17.0)}
POLE = {0: (0.8, 0.2, 0.6), 1: (-0.8, 0.2, 0.6), 2: (0.55, -0.1, -0.83), 3: (-0.55, -0.1, -0.83)}  # elbows out and back, knees out and forward
TOE_YAW = {0: 18.0, 1: -18.0, 2: -12.0, 3: 12.0}  # its hands turned in, its feet a little out
ROLL_H, ROLL_Z = 12.0, 2.0  # the roll bone at its body's middle, where it lies over
PAW = {k: [LIMB[k][2]] for k in range(4)}
ROOTS = [(1, 0, 8.0), (1, 1, 2.0), (1, 2, -4.0), (1, 3, -11.0), (-1, 0, 7.0), (-1, 1, 0.0), (-1, 2, -5.0), (-1, 3, -12.0)]  # (side, index, z on its back)


def _kk(v):
    return tuple(K * c for c in v)


def _bone(parent, name, x, up, z, rot=(0, 0, 0)):
    return parent.bone(name, (K * x, -K * up, K * z), rot)


def _box(bone, name, cx, y0, cz, w, h, d, mirror=False):
    return bone.cube(name, K * (cx - w / 2), -K * (y0 + h), K * (cz - d / 2), K * w, K * h, K * d, mirror=mirror)


def _cube(bone, name, x, y, z, w, h, d, mirror=False):  # in the bone's own axes, y down
    return bone.cube(name, K * x, K * y, K * z, K * w, K * h, K * d, mirror=mirror)


def body():
    r = R.Rig(ID, (512, 512))
    base = r.bone("base", (0, 24, 0))
    rl = _bone(base, "roll", 0, ROLL_H, ROLL_Z)
    hips = _bone(rl, "hips", 0, 13 - ROLL_H, 12 - ROLL_Z)
    chest = _bone(hips, "chest", 0, 1, -10, (-8, 0, 0))
    head = _bone(chest, "head", 0, 5, -12, (8, 0, 0))
    jaw = _bone(head, "jaw", 0, -2, 1)
    # its squat rump, low behind, and the broad front of it raised on its forelegs; a swag of belly under it. They and
    # what grows on them hang on bones of their own with no legs below them, round their middles, so they can swell
    # (a scaled bone above a leg would bend its solve)
    ph = _bone(hips, "puff_h", 0, -1.5, -2)
    pc = _bone(chest, "puff_c", 0, -2, -6)
    _box(ph, "rump", 0, -7.5, 0, 30, 15, 18)
    _box(ph, "hump", 0, 6.5, -1, 26, 3, 14)
    _box(pc, "chest", 0, -8, 0, 32, 16, 16)
    _box(pc, "belly", 0, -9, 1, 26, 3, 13)
    # the wide flat head, the blunt snout, the swollen glands behind its eyes; the eyes on bones of their own, to blink
    # down into its head
    _box(head, "skull", 0, -2, -6, 30, 6, 14)
    _box(head, "snout", 0, -2, -14.5, 22, 5, 3)
    _box(head, "crown", 0, 4, -3, 18, 2, 8)
    for s, nm in ((1, "l"), (-1, "r")):
        _box(head, "gland_" + nm, s * 10.5, 3, 4.5, 7, 4, 9, mirror=s < 0)
        _box(head, "lip_" + nm, s * 13, -2.5, -5, 4, 3, 12, mirror=s < 0)
        e = _bone(head, "eye_" + nm, s * 9.5, 4, -7)
        _box(e, "eye_" + nm, 0, -2, 0, 7, 6, 7, mirror=s < 0)
        _box(e, "lid_" + nm, 0, 3.5, 1, 6, 1, 5, mirror=s < 0)
    # the lower jaw from its hinge at the back of the head, the throat sac under it (it swells when it croaks), the
    # tongue folded in its mouth with a sticky pad at its end
    _box(jaw, "jaw", 0, -4, -8.5, 29, 4, 17)
    _box(jaw, "chin", 0, -3.5, -17.5, 20, 3, 2)
    th = _bone(jaw, "throat", 0, -3.5, -7)
    _box(th, "throat", 0, -3, 0, 16, 3, 11)
    tg = _bone(jaw, "tongue", 0, 0.2, -3)
    _box(tg, "tongue", 0, -0.5, -3, 6, 2, 6)
    tt = _bone(tg, "tongue_tip", 0, 0, -6)
    _box(tt, "tongue_tip", 0, -1, -2, 8, 3, 4)
    # the hearthstone on its back, split across the middle: a slab on its rump and a slab on its shoulders, the fern
    # growing out of the split
    sh = _bone(ph, "stone_h", 0, 7.5, 0)
    _box(sh, "stone_h", 0, 0, 1, 26, 3, 16)
    sc = _bone(pc, "stone_c", 0, 8, 0)
    _box(sc, "stone_c", 0, 0, 0, 26, 3, 13)
    fn = _bone(ph, "fern", 1.5, 10.1, -8, (-14, 0, -10))
    _box(fn, "fern", 0, 0, 0, 1, 6, 1)
    for k, (a, b) in enumerate(((1, -20), (-1, 25), (1, 60))):
        f2 = _bone(fn, f"frond{k}", 0, 2 + 1.8 * k, 0, (0, b, a * 40))
        _box(f2, f"frond{k}", 0, 0, 0, 1, 5, 3)
    # the roots come down through the split and hang off its flanks toward the floor, two lengths each, loose
    for s, i, z in ROOTS:
        nm = ("l" if s > 0 else "r") + str(i)
        on_h = z > -6
        a = _bone(ph if on_h else pc, "root_" + nm, s * 13.5, 7.5 if on_h else 7, z + (2 if on_h else 16), (0, 0, -s * (16 + 4 * (i % 2))))
        _box(a, "root_" + nm, 0, -9, 0, 3, 10, 3)
        _box(a, "knot_" + nm, 0, -4 - (i % 2), 0, 4, 2, 4)
        b2 = _bone(a, "rootb_" + nm, 0, -9, 0, (0, 0, s * 14))
        _box(b2, "rootb_" + nm, 0, -7 + (i % 2), 0, 2, 7 - (i % 2), 2)
    # the legs: the forelegs short and splayed with the hands turned in, four fingers; the hind legs long and folded,
    # the feet long and webbed with five toes
    for k in range(4):
        s = SIDE[k]
        up_, lo_, en_ = LIMB[k]
        mir = s < 0
        if k in FRONT:
            a = _bone(chest, up_, s * 13, -4, -6)
            _cube(a, up_, -3, -2, -3, 6, UPPER[k] + 2, 6, mirror=mir)
            f = a.bone(lo_, (0, K * UPPER[k], 0))
            _cube(f, lo_, -2.5, 0, -2.5, 5, LOWER[k] + HAND_Y, 5, mirror=mir)
            h = f.bone(en_, (0, K * LOWER[k], 0))
            _cube(h, en_, -3, HAND_Y, -4, 6, 2, 6, mirror=mir)
            for j, dx in enumerate((-2.5, -0.75, 1.0, 2.5)):
                _cube(h, f"finger{k}{j}", dx - 0.5, HAND_Y + 0.5, -4 - (3 if j in (1, 2) else 2), 1, 1, 3 if j in (1, 2) else 2, mirror=mir)
        else:
            a = _bone(hips, up_, s * 13, -2, 2)
            _cube(a, up_, -3.5, -2, -3.5, 7, UPPER[k] + 2, 7, mirror=mir)
            f = a.bone(lo_, (0, K * UPPER[k], 0))
            _cube(f, lo_, -2.5, 0, -2.5, 5, LOWER[k] + HAND_Y, 5, mirror=mir)
            h = f.bone(en_, (0, K * LOWER[k], 0))
            _cube(h, en_, -3, HAND_Y, -9, 6, 2, 11, mirror=mir)
            for j, dx in enumerate((-3.0, -1.5, 0.0, 1.5, 3.0)):
                ln = (2, 3, 4, 3, 2)[j]
                _cube(h, f"toe{k}{j}", dx - 0.5, HAND_Y + 0.5, -9 - ln, 1, 1, ln, mirror=mir)
    for k in range(4):
        ik.rest_limb(r, *LIMB[k], _kk(REST_FOOT[k]), POLE[k])
    fr0 = core.frames(r, {})
    for k in range(4):  # the hands and feet set flat on the floor at rest, turned as they lie
        end = LIMB[k][2]
        r.by_name[end].rot = tuple(v + w for v, w in zip(r.by_name[end].rot, ik.orient(r, fr0, end, (0, 1, 0), _flat(TOE_YAW[k]))))
        fr0 = core.frames(r, {})
    r.look = [("head", 0.45, 0.25), ("chest", 0.12, 0.08)]
    r.shadow = 2.6
    r.size = 1.0
    # secondary parts (SPEC §35.10): the roots hanging off its flanks and the fern on its back swing loose
    r.springs = [(f"{p}_{('l' if s > 0 else 'r') + str(i)}", 0.35 if p == "root" else 0.5, 2.0 if p == "root" else 2.6, 0.14)
                 for s, i, _ in ROOTS for p in ("root", "rootb")] + [("fern", 0.5, 2.4, 0.16), ("frond0", 0.6, 3.0, 0.18), ("frond1", 0.6, 3.0, 0.18),
                                                                     ("frond2", 0.6, 3.0, 0.18)]
    r.guard = [LIMB[k][2] for k in range(4)] + ["head", "jaw", "throat"]
    return r


def _flat(yaw):
    a = yaw * DEG
    return (math.cos(a), 0.0, -math.sin(a))


# ---------------------------------------------------------------------------------------------------------------------
# Paint

SKIN = Ramp("#17120b", "#231b10", "#322616", "#42331d", "#544125", "#67512e", "#7c6238", "#917444", "#a88752")
WART = Ramp("#5c4520", "#765a2a", "#906f34", "#aa853f", "#c29b4c", "#d8b262")
BELLY = Ramp("#5e5440", "#7a6e56", "#968a6e", "#b0a486", "#c8bc9e", "#ddd2b4")
STONE = Ramp("#232325", "#323234", "#434244", "#555354", "#686564", "#7c7875", "#918c86", "#a7a199")
MOSS = Ramp("#18250d", "#243612", "#314818", "#405c1f", "#516f28", "#648432", "#78993e")
ROOT = Ramp("#140d07", "#21160c", "#311f11", "#432c18", "#573a21", "#6c4b2c")
DARK = Ramp("#070504", "#0f0b07", "#18120b", "#22190f")
EMBER = ("#fff3c4", "#ffd474", "#ff9d36", "#ea621e", "#ad3612", "#5a1708")
GOLD = ("#fff2a6", "#f2cf56", "#d6b03a", "#ab872b", "#715719")


def _cells(f):
    """A face's texels as cells of K by K (the paint's own pixels at the size it is written in): (i, j, ci, cj)."""
    for i, j in f.each():
        yield i, j, i // K, j // K


def _warts(P_, cube, faces, seed, every=4):
    """Warts on its skin: a pale ochre bump lit on top, its shadow under it, scattered."""
    rng = (seed * 7919 + 31) % 9973
    for fname in faces:
        f = P_.face(cube, fname)
        cw, ch = max(1, f.w // K), max(1, f.h // K)
        bumps = {}
        for _ in range(max(1, (cw * ch) // (every * every))):
            rng = (rng * 1103 + 12345) % 9973
            ci, cj = rng % cw, (rng // 7) % ch
            big = rng % 5 == 0
            bumps[(ci, cj)] = WART.at(4.4 if big else 3.4)
            if big:
                bumps.setdefault((ci + 1, cj), WART.at(3.6))
            bumps.setdefault((ci, cj + 1), SKIN.at(1.6))
        for i, j, ci, cj in _cells(f):
            if (ci, cj) in bumps:
                f.set(i, j, bumps[(ci, cj)])


def _blotches(P_, cube, faces, seed):
    """Dark blotches on its skin, a little paler round their edges."""
    rng = (seed * 4241 + 7) % 9973
    for fname in faces:
        f = P_.face(cube, fname)
        cw, ch = max(1, f.w // K), max(1, f.h // K)
        spots = []
        for _ in range(max(1, (cw * ch) // 40)):
            rng = (rng * 1103 + 12345) % 9973
            spots.append((rng % cw, (rng // 13) % ch, 1.2 + (rng % 3) * 0.6))
        for i, j, ci, cj in _cells(f):
            for x, y, rad in spots:
                d = math.hypot(ci - x, (cj - y) * 1.2)
                if d < rad:
                    f.set(i, j, SKIN.at(2.0))
                elif d < rad + 0.9:
                    f.set(i, j, SKIN.at(5.8))


def paint(r, P_):
    """Warty olive-brown skin darkening along its back, pale mottled cream under its belly and throat, the glands swollen
    and pitted; the hearthstone pale grey, black with soot at its edges, cracked, moss in the cracks; dark roots; its
    eyes gold with a black bar across each, lit; the inside of its mouth the glow of a hearth fire, its tongue dark red."""
    of = lambda *ps: parts.cubes_of(r, *ps)
    skin = ["rump", "hump", "chest", "skull", "snout", "crown", "jaw", "chin"] + of("lip_", "arm", "fore", "thigh", "shin", "hand", "foot")
    for k, c in enumerate(skin):
        P_.paint(c, SKIN, 5.0, None, grad=1.4, rim=1.0, ao=1.0, noise=0.35, nscale=3 * K)
        f = P_.face(c, "bottom")
        for i, j, ci, cj in _cells(f):
            f.set(i, j, BELLY.at(3.0 + 0.9 * ((ci * 5 + cj * 3) % 7 == 0) - 1.0 * ((ci * 3 + cj * 7) % 11 == 0)))
        _blotches(P_, c, ("west", "east", "top", "south"), k + 3)
        if c.startswith(("thigh", "shin", "arm", "fore")):  # dark bands across its legs
            for fname in SIDES:
                f = P_.face(c, fname)
                for i, j, ci, cj in _cells(f):
                    if cj % 5 in (2, 3):
                        f.set(i, j, SKIN.at(2.2 + 0.5 * (cj % 5 == 2)))
        _warts(P_, c, ("west", "east", "top", "south"), k + 1, every=5 if c in ("rump", "chest") else 4)
    for c in ("belly",) + tuple(of("throat")):
        P_.paint(c, BELLY, 3.8, None, grad=1.0, rim=0.8, ao=0.6, noise=0.3, nscale=2 * K)
        for fname in SIDES + ("bottom",):
            f = P_.face(c, fname)
            for i, j, ci, cj in _cells(f):
                if (ci * 7 + cj * 5) % 9 == 0:
                    f.set(i, j, BELLY.at(1.8))
    for c in of("throat"):  # the fire it swallowed shows through its throat: ember veins, brightest when the sac stretches
        for fname in SIDES + ("bottom",):
            f, gl = P_.face(c, fname), P_.face(c, fname, glow=True)
            for i, j, ci, cj in _cells(f):
                vein = (ci + (cj // 2) % 2) % 4 == 0 or (fname == "bottom" and (ci + cj) % 6 == 0)
                if vein:
                    f.set(i, j, hexc(EMBER[3 if (ci + cj) % 3 else 2], 255))
                    gl.set(i, j, hexc(EMBER[2], 70 + 30 * ((ci + cj) % 2)))
    for g, c in enumerate(of("gland_")):  # the glands: swollen, paler on top, pitted with pores
        P_.paint(c, WART, 3.0, None, grad=1.2, rim=1.2, ao=0.8, noise=0.3, nscale=2 * K)
        for fname in ("top", "west", "east", "north", "south"):
            f = P_.face(c, fname)
            for i, j, ci, cj in _cells(f):
                h = (ci * 7919 + cj * 104729 + g * 31 + len(fname)) % 97
                if h % 7 == 0:
                    f.set(i, j, SKIN.at(1.6))
                elif h % 11 == 0:
                    f.set(i, j, WART.at(5.0))
    for c in of("finger", "toe"):
        P_.paint(c, BELLY, 3.4, None, grad=0.6, rim=0.6, ao=0.3)
    for c in ("stone_h", "stone_c"):
        P_.paint(c, STONE, 5.2, None, grad=1.0, rim=1.2, ao=0.8, noise=0.45, nscale=3 * K)
        f = P_.face(c, "top")
        cw, ch = f.w // K, f.h // K
        for i, j, ci, cj in _cells(f):  # cracks across the slab, moss in them and spreading from the split
            h = (ci * 7919 + cj * 104729 + (3 if c == "stone_h" else 0)) % 9973
            edge = min(ci, cj, cw - 1 - ci, ch - 1 - cj)
            split = (cj <= 1) if c == "stone_h" else (cj >= ch - 2)
            if split or h % 13 == 0:
                f.set(i, j, MOSS.at(3.4 + (h % 3)))
            elif (ci * 2 + cj * 3) % 17 == 0 or (ci + cj * 2) % 23 == 0:
                f.set(i, j, STONE.at(1.6))  # a crack
            elif edge == 0:
                f.set(i, j, STONE.at(2.6))  # soot at its edges
        for fname in SIDES:
            f = P_.face(c, fname)
            for i, j, ci, cj in _cells(f):
                f.set(i, j, STONE.at(1.2 + 0.6 * (cj > 0)) if cj < 1 else STONE.at(3.6 + 0.8 * ((ci + cj) % 4 == 0)))
                if cj == f.h // K - 1 and (ci * 5) % 7 < 3:
                    f.set(i, j, MOSS.at(2.4))  # moss creeping out from under the slab
    for c in of("root_", "rootb_", "knot_"):
        P_.paint(c, ROOT, 3.2, None, grad=1.2, rim=1.0, ao=0.4, noise=0.4, nscale=2 * K)
    for c in of("fern", "frond"):
        P_.paint(c, MOSS, 5.0, None, grad=1.0, rim=1.2, ao=0.3, noise=0.3, nscale=2 * K)
    for c in of("eye_"):  # gold, a black bar of a pupil across it, the lit iris glowing
        P_.paint(c, SKIN, 4.2, None, grad=0.8, rim=0.6, ao=0.4)
        for fname in ("north", "west", "east"):
            f, gl = P_.face(c, fname), P_.face(c, fname, glow=True)
            cw, ch = f.w // K, f.h // K
            for i, j, ci, cj in _cells(f):
                if cj == 0:
                    continue  # the lid's edge
                bar = cj == ch // 2 and 0 < ci < cw - 1
                ring = ci in (0, cw - 1) or cj == ch - 1
                hot = not ring and (i + j) % 3 == 0
                col = DARK.at(0.6) if bar else hexc(GOLD[3] if ring else (GOLD[0] if hot else GOLD[1]), 255)
                f.set(i, j, col)
                if not bar:
                    gl.set(i, j, hexc(GOLD[2] if ring else GOLD[0], 150 if ring else 210))
    for c in of("lid_"):
        P_.paint(c, SKIN, 4.6, None, grad=0.6, rim=1.0, ao=0.3)
    # the mouth: the roof of it and the floor of it lit like a hearth, darker toward the sides; the tongue dark red
    for c, fname in (("jaw", "top"), ("skull", "bottom"), ("snout", "bottom"), ("chin", "top")):
        f, gl = P_.face(c, fname), P_.face(c, fname, glow=True)
        cw = f.w // K
        for i, j, ci, cj in _cells(f):
            m = min(ci, cw - 1 - ci)
            heat = max(0, min(5, 5 - m // 3))
            ember = (ci * 7 + cj * 3) % 5 == 0
            col = EMBER[max(0, 5 - heat - (1 if ember else 0))] if m > 1 else EMBER[5]
            f.set(i, j, hexc(col, 255))
            if m > 1:
                gl.set(i, j, hexc(EMBER[min(5, 6 - heat)], 120 + 22 * heat))
    for c in of("tongue"):
        P_.paint(c, Ramp("#3a0a08", "#5a1410", "#7c2016", "#9c2e1e", "#bc4228"), 3.0, None, grad=0.6, rim=0.6, ao=0.2)
    _face(P_)
    return P_.skin, P_.glow


def _face(P_):
    """The snout from the front: two nostrils, the line of its mouth dark across its face from lip to lip."""
    f = P_.face("snout", "north")
    cw, ch = f.w // K, f.h // K
    for i, j, ci, cj in _cells(f):
        if cj == ch - 1:
            f.set(i, j, DARK.at(0.8))  # the mouth's line
        elif cj == 1 and ci in (cw // 2 - 4, cw // 2 + 3):
            f.set(i, j, DARK.at(0.4))  # a nostril
        else:
            f.set(i, j, SKIN.at(4.4 + 0.6 * ((ci + cj) % 5 == 0)))
    for c in ("lip_l", "lip_r"):
        for fname in ("west", "east", "north"):
            f = P_.face(c, fname)
            for i, j, ci, cj in _cells(f):
                if cj == f.h // K - 1:
                    f.set(i, j, DARK.at(1.0))
    for fname in ("west", "east"):
        f = P_.face("jaw", fname)
        for i, j, ci, cj in _cells(f):
            if cj == 0:
                f.set(i, j, DARK.at(1.0))


# ---------------------------------------------------------------------------------------------------------------------
# Keys

def _ease(f):
    f = max(0.0, min(1.0, f))
    return f * f * (3 - 2 * f)


def _wave(u, at, width):
    """A bump 0..1..0 centred on at, width wide."""
    x = (u - at) / width
    return math.cos(math.pi * x) ** 2 if abs(x) < 0.5 else 0.0


def _pose(r, lift=0.0, pitch=0.0, yaw=0.0, roll=0.0, shift=(0.0, 0.0), bend=0.0, side=0.0, twist=0.0, head=(0, 0, 0), jaw=0.0, eyes=(0.0, 0.0),
          tongue=0.0, tilt=0.0, sink=0.0, spin=0.0, feet=None, reach=None, local=None, poles=None, hands=None, limbs=None):
    """lift (+ up), pitch (+ its front dipping), yaw (+ to its right), roll (+ its left side down) and shift (x, z) move
    its hips; bend (+ the front dipping), side (+ to its right) and twist turn its shoulders on its rump; head (nod +
    down, turn + to its right, tilt) turns on top of its rest; jaw opens its mouth (degrees); eyes (left, right: 0 open,
    1 drawn down into its head); tongue tips its tongue down in its mouth (degrees); tilt and sink turn and lower it on
    its roll bone, spin turns all of it round on the spot (+ to its right). feet {leg: (x, height, z)}: where the wrists
    and ankles go (the rest stand where they stand at rest); reach {leg: (dx, dup, dz)}: the same, from where the leg's
    root is posed; local {leg: (x, down, z)}: from the root in its hips' own axes, however it lies; limbs {leg: {bone:
    turn}} sets a leg outright; hands {leg: "free" | degrees}: that hand or foot hangs as its leg leaves it, or curled
    so far (flat on the floor if not given)."""
    p = {"roll": {"rot": (0, spin, tilt), "pos": (0, -K * sink, 0)},
         "hips": {"rot": (pitch, yaw, roll), "pos": (K * shift[0], K * lift, K * shift[1])},
         "chest": (bend, side, twist), "head": tuple(head), "jaw": (jaw, 0, 0), "tongue": (tongue, 0, 0),
         "eye_l": {"rot": (0, 0, 0), "pos": (0, -4.0 * K * eyes[0], 0)}, "eye_r": {"rot": (0, 0, 0), "pos": (0, -4.0 * K * eyes[1], 0)}}
    fr = core.frames(r, core.as_sample(p))
    feet, reach, poles, limbs, hands, local = feet or {}, reach or {}, poles or {}, limbs or {}, hands or {}, local or {}
    for k in range(4):
        if k in limbs:
            lp = {b: limbs[k].get(b, (0.0, 0.0, 0.0)) for b in LIMB[k][:2]}
        elif k in local:
            root = core.point(fr, LIMB[k][0], (0, 0, 0))
            d = R._mv(fr["hips"][0], _kk(local[k]))
            lp = ik.limb(r, fr, *LIMB[k], tuple(root[i] + d[i] for i in range(3)), poles.get(k, POLE[k]))
        else:
            if k in reach:
                root = core.point(fr, LIMB[k][0], (0, 0, 0))
                dx, du, dz = _kk(reach[k])
                tgt = (root[0] + dx, 24.0 - root[1] + du, root[2] + dz)
            else:
                tgt = _kk(feet.get(k, REST_FOOT[k]))
                if spin:  # turned round on the spot, its feet stand where they would if it had turned
                    a, z0 = spin * DEG, K * ROLL_Z
                    tgt = (tgt[0] * math.cos(a) + (tgt[2] - z0) * math.sin(a), tgt[1],
                           -tgt[0] * math.sin(a) + (tgt[2] - z0) * math.cos(a) + z0)
            lp = ik.limb(r, fr, *LIMB[k], ik.model(tgt), poles.get(k, POLE[k]))
        p.update(lp)
    fr = core.frames(r, core.as_sample(p))
    for k in range(4):
        end = LIMB[k][2]
        how = hands.get(k, "free" if (k in limbs or k in local) else None)
        if how is None:
            p[end] = ik.orient(r, fr, end, (0, 1, 0), _flat(TOE_YAW[k] + yaw + spin))
        else:
            p[end] = (0.0 if how == "free" else float(how), 0.0, 0.0)
    return p


def _foot(k, up=0.0, dz=0.0, dx=0.0):
    x, h, z = REST_FOOT[k]
    return (x + dx, h + up, z + dz)


def _with(a, b):
    return {**a, **b}


def _mix(a, b, f):
    out = {}
    for k in set(a) | set(b):
        va, vb = a.get(k, (0, 0, 0)), b.get(k, (0, 0, 0))
        ra = va["rot"] if isinstance(va, dict) else va
        rb = vb["rot"] if isinstance(vb, dict) else vb
        rot = tuple(x + (y - x) * f for x, y in zip(ra, rb))
        if isinstance(va, dict) or isinstance(vb, dict):
            pa = va.get("pos", (0, 0, 0)) if isinstance(va, dict) else (0, 0, 0)
            pb = vb.get("pos", (0, 0, 0)) if isinstance(vb, dict) else (0, 0, 0)
            out[k] = {"rot": rot, "pos": tuple(x + (y - x) * f for x, y in zip(pa, pb))}
        else:
            out[k] = rot
    return out


def _unwrap(keys):
    """Each key's turns written within half a turn of the key before (each axis by whole turns, the same pose)."""
    out, last = [], {}
    for t, p in keys:
        q = {}
        for b, v in p.items():
            rot = v["rot"] if isinstance(v, dict) else v
            if b in last:
                rot = tuple(c + 360 * round((last[b][i] - c) / 360) for i, c in enumerate(rot))
            last[b] = rot
            q[b] = {**v, "rot": rot} if isinstance(v, dict) else rot
        out.append((t, q))
    return out


def _floor(r, p, gap=0.3):
    """The pose let down or lifted on its roll bone until its lowest part just rests on the floor."""
    fr = core.frames(r, core.as_sample(p))
    up = core.lowest(r, fr, [b.name for b in r.bones if b.cubes]) - (core.GROUND - gap)
    q = dict(p)
    rl = q["roll"]
    q["roll"] = {"rot": rl["rot"], "pos": (rl["pos"][0], rl["pos"][1] + up, rl["pos"][2])}
    return q


def _plant(r, clip, legs, where=None):
    """The hands and feet that stand through a move keep where they stand on the floor, every tick, and lie flat on it
    heading the way they head."""
    where = where or {}

    def fn(t, s, fr):
        out = {}
        for k in legs:
            out.update(ik.limb(r, fr, *LIMB[k], ik.model(_kk(where.get(k, REST_FOOT[k]))), POLE[k]))
        s2 = dict(s)
        s2.update(core.as_sample(out))
        fr2 = core.frames(r, s2)
        for k in legs:
            end = LIMB[k][2]
            M = fr2[end][0]
            across = (M[0][0], 0.0, M[2][0])
            if abs(across[0]) + abs(across[2]) > 1e-3:
                out[end] = ik.orient(r, fr2, end, (0, 1, 0), across)
        return out
    ik.retrack(r, clip, fn)


def _on_floor(r, clip):
    """Between keys a hand or foot can sag a hair into the floor (its leg's turns blend, not its path): wherever one
    would, its leg is solved again that tick to set it on the floor where it was, the joint its own way and the hand
    turned as it was."""
    floor = core.GROUND - 0.3
    still = {"pos": (0, 0, 0), "rot": (0, 0, 0), "scale": (1, 1, 1)}

    def fn(t, smp, fr):
        out = {}
        for k in range(4):
            up, lo, en = LIMB[k]
            v = core.lowest(r, fr, PAW[k])
            if v <= floor:
                out.update({b: tuple(c / DEG for c in smp.get(b, still)["rot"]) for b in (up, lo, en)})
                continue
            end, joint, root = (core.point(fr, b, (0, 0, 0)) for b in (en, lo, up))
            pole = ik.unit(ik.sub(joint, tuple((root[i] + end[i]) / 2 for i in range(3))))
            M = fr[en][0]
            ydir, xdir = (M[0][1], M[1][1], M[2][1]), (M[0][0], M[1][0], M[2][0])
            to = (end[0], end[1] - (v - floor), end[2])
            short = abs(r.by_name[lo].pivot[1] - r.by_name[en].pivot[1]) + 0.5
            dh = to[1] - root[1]
            flat = math.hypot(to[0] - root[0], to[2] - root[2])
            if dh * dh + flat * flat < short * short and flat > 1e-6:
                kk = math.sqrt(max(0.0, short * short - dh * dh)) / flat
                to = (root[0] + (to[0] - root[0]) * kk, to[1], root[2] + (to[2] - root[2]) * kk)
            e0, k0 = ik.two_bone(r, fr, up, lo, en, end, pole)
            e1, k1 = ik.two_bone(r, fr, up, lo, en, to, pole)
            Mp = fr[r.by_name[up].parent.name][0]
            turn = R._mm(R._mm(Mp, R._rot(*e1)), core._t(R._mm(Mp, R._rot(*e0))))
            whole = core._euler(R._mm(core._t(Mp), R._mm(turn, fr[up][0])))
            cur = smp.get(lo, still)["rot"]
            q = {up: ik.delta(tuple(c / DEG for c in whole), r.by_name[up].rot), lo: ((cur[0] + k1 - k0) / DEG, cur[1] / DEG, cur[2] / DEG)}
            s2 = dict(smp)
            for b, e in q.items():
                s2[b] = {**smp.get(b, still), "rot": tuple(c * DEG for c in e)}
            q[en] = ik.orient(r, core.frames(r, s2), en, ydir, xdir)
            out.update(q)
        return out
    ik.retrack(r, clip, fn)


def _spin(r, clip, bone, axis, turns, t0, t1):
    """Turns a bone round whole turns on one axis between t0 and t1, eased, on top of its keys (a key a tick)."""
    c = r.clips[clip]
    old = c.tracks.get((bone, "rot"))
    keys = []
    for k in range(int(round(c.length / core.TICK)) + 1):
        t = min(c.length, k * core.TICK)
        base = list(R._interp(old, t)) if old else [0.0, 0.0, 0.0]
        f = 0.0 if t <= t0 else 1.0 if t >= t1 else core.ease_io((t - t0) / (t1 - t0))
        base[axis] += 360.0 * turns * f
        keys.append((t, tuple(base), "cat"))
    c.tracks[(bone, "rot")] = keys


def _scale3(r, clip, bone, pts, mode="lin"):
    """A clip's scale keys for one bone: [(seconds, (x, y, z))], 1 at both ends unless given."""
    c = r.clips[clip]
    ks = {0.0: (1.0, 1.0, 1.0), c.length: (1.0, 1.0, 1.0)}
    ks.update((round(t, 4), tuple(v)) for t, v in pts)
    c.scale(bone, [(t, *v) for t, v in sorted(ks.items())], mode)


def _swell(r, clip, pts):
    """Its body blown up round its middle by [(seconds, scale)] (1 at both ends): a little more tall than wide."""
    for b in ("puff_h", "puff_c"):
        _scale3(r, clip, b, [(t, (v, 1 + (v - 1) * 1.2, 1 + (v - 1) * 0.7)) for t, v in pts], "cat")


def _tongue(r, clip, pts):
    """Its tongue out: [(seconds, how many times its length)], the sticky tip kept its own size."""
    _scale3(r, clip, "tongue", [(t, (1.0, 1.0, v)) for t, v in pts])
    _scale3(r, clip, "tongue_tip", [(t, (1.0, 1.0, 1.0 / v)) for t, v in pts])


def _sac(a):
    """Its throat sac swollen by a (0 at rest, 1 a full croak)."""
    return (1.0 + 0.8 * a, 1.0 + 2.2 * a, 1.0 + 0.6 * a)


# ---------------------------------------------------------------------------------------------------------------------
# Clips

def _idle(r):
    """It sits like a stone and breathes (8 s), its throat fluttering all the while; it blinks, drawing its eyes down
    into its head; it turns its head slowly to look one way, then the other; its tongue flicks out at a fly and back
    and it swallows, its eyes sinking as it does; it wipes one eye with a hand; its weight shifts on its forelegs."""
    L, n = 8.0, 160
    keys, sac, tng = [], [], []
    for k in range(n + 1):
        u = k / n
        t = L * u
        w = 2 * math.pi * u
        blink = max(_wave(u, 0.12, 0.035), _wave(u, 0.66, 0.035))
        look_r, look_l = _wave(u, 0.3, 0.16), _wave(u, 0.55, 0.16)
        fly = _wave(u, 0.42, 0.03)
        gulp = _wave(u, 0.455, 0.04)
        wipe = _wave(u, 0.86, 0.1)
        breath = math.sin(2 * math.pi * 0.375 * t)
        feet = {0: (12.5 - 2 * wipe, WRIST + 10 * wipe, -17.0 + 1.5 * wipe)} if wipe > 0.01 else None
        keys.append((t, _pose(r, lift=0.25 * breath - 0.3 * gulp, bend=0.5 * breath + 6 * wipe, roll=0.6 * math.sin(w) - 1.5 * wipe, side=4 * look_r - 4 * look_l - 3 * wipe,
                              head=(-4 * fly + 3 * gulp + 8 * wipe, 16 * look_r - 16 * look_l - 10 * wipe, 4 * look_r - 4 * look_l - 8 * wipe), jaw=24 * fly,
                              tongue=-10 * fly, eyes=(max(blink, gulp, 0.9 * wipe), max(blink, gulp)), feet=feet,
                              hands={0: -50 * wipe} if feet else None)))
        flut = math.sin(2 * math.pi * 2.4 * t)
        sac.append((t, _sac(0.08 + 0.06 * flut - 0.08 * gulp)))
        tng.append((t, 1.0 + 6.0 * fly))
    keyed(r, "idle", L, keys, loop=True)
    _scale3(r, "idle", "throat", sac, "cat")
    _tongue(r, "idle", tng)


WALK = {2: 0.0, 0: 0.25, 3: 0.5, 1: 0.75}  # its crawl: a hand or foot at a time, back left, front left, back right, front right


def _gait(u, stride, beta, lift, groups):
    """The hands and feet through a stride at u (stride in its own half pixels): down, each moves back as far as it
    travels, so it stays put on the floor; up, it lifts and reaches forward again."""
    feet, flex = {}, {}
    st = stride * beta
    for k, ph in groups.items():
        q = (u - ph) % 1.0
        if q < beta:
            feet[k] = _foot(k, dz=-st / 2 + st * q / beta)
        else:
            f = (q - beta) / (1 - beta)
            feet[k] = _foot(k, up=lift * math.sin(math.pi * f) ** 0.8, dz=st / 2 - st * core.ease_io(f))
            flex[k] = 50.0 * math.sin(math.pi * f) ** 1.5 if k in FRONT else 20.0 * math.sin(math.pi * f) ** 1.5
    return feet, flex


WALK_STRIDE, HOP_STRIDE = 1.0, 2.6  # blocks a walk cycle and a hop cover


def _hop(u):
    """One hop at u: it crouches on its haunches, its hind legs fling it up and forward, it flies with its hands reaching
    ahead and its feet trailing, lands on its hands and swings its feet in under it again."""
    st = HOP_STRIDE * 16 / K
    feet, flex = {}, {}
    for k, (land, beta) in {0: (0.68, 0.62), 1: (0.68, 0.62), 2: (0.82, 0.58), 3: (0.82, 0.58)}.items():
        q = (u - land) % 1.0
        if q < beta:  # planted, going back under it as it travels
            feet[k] = _foot(k, dz=-st * beta / 2 + st * q)
        else:
            f = (q - beta) / (1 - beta)
            back = st * beta / 2
            trail = (7.0 * math.sin(math.pi * min(1.0, f * 1.6)) if k in BACK else 0.0)
            feet[k] = _foot(k, up=(6.0 if k in BACK else 7.0) * math.sin(math.pi * f) ** 0.7, dz=back - st * beta * core.ease_io(f) + trail)
            flex[k] = 40.0 * math.sin(math.pi * f) if k in FRONT else -30.0 * math.sin(math.pi * f)
    fl = _wave(u, 0.47, 0.42)
    crouch = _wave(u, 0.1, 0.3) + _wave(u, 1.1, 0.3)
    return feet, flex, fl, crouch


def _locomotion(r):
    """It crawls a hand and a foot at a time, low and heavy, its body rocking from side to side and its head swinging
    against it. It runs by hopping: it crouches, flings itself up and forward with its hind legs, flies with its hands
    reaching ahead and its long feet trailing, lands on its hands and gathers its feet in under it for the next."""
    L, n = 1.6, 32
    keys = []
    for k in range(n + 1):
        u = k / n
        s = math.sin(2 * math.pi * u)
        feet, flex = _gait(u, WALK_STRIDE * 16 / K, 0.72, 3.0, WALK)
        keys.append((L * u, _pose(r, lift=-0.4 - 0.3 * math.cos(4 * math.pi * u), roll=2.2 * s, yaw=2.0 * s, side=3.0 * s, twist=-2 * s,
                                  head=(1.5 * math.sin(4 * math.pi * u + 0.8), -5 * s, -2 * s), feet=feet, hands=flex)))
    keyed(r, "walk", L, keys, loop=True)
    L, n = 0.75, 30
    keys = []
    for k in range(n + 1):
        u = k / n
        feet, flex, fl, crouch = _hop(u)
        keys.append((L * u, _pose(r, lift=6.0 * fl - 2.0 * crouch, pitch=-9 * _wave(u, 0.32, 0.3) + 8 * _wave(u, 0.66, 0.24), bend=-4 * fl,
                                  head=(4 * fl - 4 * crouch, 0, 0), jaw=4 * fl, eyes=(0.35 * _wave(u, 0.72, 0.12), 0.35 * _wave(u, 0.72, 0.12)),
                                  feet=feet, hands=flex)))
    keyed(r, "run", L, keys, loop=True)
    r.walk_stride, r.run_stride = WALK_STRIDE, HOP_STRIDE
    for nm, sgn in (("turn_l", -1), ("turn_r", 1)):  # + is to its right
        keys = []
        n = 20
        A = 14.0 * DEG
        for k in range(n + 1):
            u = k / n
            feet, flex = {}, {}
            for j, ph in WALK.items():  # each hand and foot steps round the way it turns while the others hold
                q = (u - ph) % 1.0
                if q < 0.7:
                    a, up_ = -sgn * A * (0.5 - q / 0.7), 0.0
                else:
                    f = (q - 0.7) / 0.3
                    a = -sgn * A * (-0.5 + core.ease_io(f))
                    up_ = 3.0 * math.sin(math.pi * f)
                    flex[j] = 40.0 * math.sin(math.pi * f) if j in FRONT else 15.0 * math.sin(math.pi * f)
                x, h, z = REST_FOOT[j]
                feet[j] = (x * math.cos(a) + (z - ROLL_Z) * math.sin(a), h + up_, -x * math.sin(a) + (z - ROLL_Z) * math.cos(a) + ROLL_Z)
            keys.append((1.0 * u, _pose(r, lift=-0.4, yaw=sgn * 2.0, side=sgn * 5.0, roll=1.2 * math.sin(4 * math.pi * u), head=(0, sgn * 12, 0),
                                        feet=feet, hands=flex)))
        keyed(r, nm, 1.0, keys, loop=True)


SPLAY = {0: (22.0, WRIST, -21.0), 1: (-22.0, WRIST, -21.0), 2: (25.0, WRIST, 21.0), 3: (-25.0, WRIST, 21.0)}  # flat on its belly, legs out


def _flat_out(r, breath=0.0, eyes=0.6, nod=4.0):
    """Down flat on its belly, its legs splayed out round it, its chin on the floor."""
    p = _pose(r, lift=-4.0 + 0.4 * breath, bend=2 - 0.5 * breath, head=(nod, 0, 0), jaw=2 + 3 * breath, eyes=(eyes, eyes), feet=SPLAY,
              hands={k: 0.0 for k in range(4)})
    for k in range(4):  # the hands and feet flat, turned out
        fr = core.frames(r, core.as_sample(p))
        p[LIMB[k][2]] = ik.orient(r, fr, LIMB[k][2], (0, 1, 0), _flat(TOE_YAW[k] + (30 if SIDE[k] < 0 else -30) * (1 if k in FRONT else -1)))
    return _floor(r, p, gap=0.3)


def _reactions(r):
    """Struck, it flinches flat with its eyes pulled down and its mouth open; from the side it twists away from the
    blow. Broken, it sinks flat on its belly with its legs splayed out round it, its throat heaving; it pushes itself
    up on its hands and draws its feet in under it."""
    home = _pose(r)
    hit = _pose(r, lift=-2.0, bend=3, head=(10, 0, 0), jaw=16, eyes=(1, 1))
    back = _pose(r, lift=0.3, bend=-1, head=(-3, 0, 0), jaw=4, eyes=(0.3, 0.3))
    keyed(r, "hurt", 0.5, [(0, home), (0.06, hit), (0.26, back), (0.5, home)])
    _scale3(r, "hurt", "throat", [(0.06, _sac(0.3)), (0.3, _sac(0.05))])
    for nm, sgn in (("hurt_l", 1), ("hurt_r", -1)):  # struck from its left it twists away to its right
        h = _pose(r, lift=-1.6, roll=-5 * sgn, yaw=6 * sgn, side=8 * sgn, shift=(-sgn * 1.5, 0), head=(6, 14 * sgn, -6 * sgn), jaw=14,
                  eyes=(1.0 if sgn > 0 else 0.6, 0.6 if sgn > 0 else 1.0))
        b = _pose(r, roll=1.5 * sgn, side=-2 * sgn, head=(0, -5 * sgn, 2 * sgn), jaw=3, eyes=(0.2, 0.2))
        keyed(r, nm, 0.55, [(0, home), (0.06, h), (0.3, b), (0.55, home)])
    down = _flat_out(r)
    keyed(r, "kneel", 1.2, [(0, home), (0.3, _mix(home, down, 0.5)), (0.7, _flat_out(r, breath=1.0)), (0.95, _flat_out(r, breath=0.0)), (1.2, down)])
    _scale3(r, "kneel", "throat", [(0.7, _sac(0.5)), (0.95, _sac(0.15)), (1.2, _sac(0.4))])
    push = _pose(r, lift=-1.0, pitch=-4, bend=-4, head=(-2, 0, 0), jaw=4, eyes=(0.2, 0.2), feet={2: SPLAY[2], 3: SPLAY[3]})
    gather = _pose(r, lift=0.4, pitch=-1, head=(-2, 0, 0), eyes=(0, 0), feet={2: _foot(2, up=3.0, dx=2.0), 3: _foot(3, up=3.0, dx=-2.0)}, hands={2: -20.0, 3: -20.0})
    keyed(r, "rise", 1.2, [(0, down), (0.4, push), (0.75, gather), (1.0, _pose(r, lift=0.3)), (1.2, home)])
    _death(r)


def _death(r):
    """Its last: it blows itself up one last time, high on its legs, and lets the air out of itself; it sinks down
    flat; it draws its hands and feet in under it, lays its chin on the floor, shuts its eyes and goes still, as still as
    the stone on its back; its throat flutters twice more and stops."""
    home = _pose(r)
    puff = _pose(r, lift=4.0, pitch=2, bend=3, head=(10, 0, 0), jaw=0, eyes=(0.4, 0.4))
    sag = _pose(r, lift=-1.0, bend=2, head=(6, 0, 0), jaw=6, eyes=(0.5, 0.5))
    flat = _flat_out(r, eyes=0.7, nod=6)
    tuck = {0: (12.0, WRIST, -16.0), 1: (-12.0, WRIST, -16.0), 2: (15.0, WRIST, 13.0), 3: (-15.0, WRIST, 13.0)}
    shut = lambda nod, eyes, breath=0.0: _floor(r, _pose(r, lift=-4.0 + 0.3 * breath, bend=3, head=(nod, 0, 0), jaw=1, eyes=(eyes, eyes), feet=tuck,
                                                         hands={k: 0.0 for k in range(4)}), gap=0.3)
    keys = [(0, home), (0.5, puff), (1.0, _mix(puff, home, 0.6)), (1.4, sag), (2.0, flat), (2.6, shut(8, 0.8)), (3.1, shut(10, 1.0, 1.0)),
            (3.6, shut(10, 1.0)), (4.1, shut(11, 1.0, 0.6)), (4.6, shut(11, 1.0)), (5.0, shut(11, 1.0))]
    keyed(r, "death", 5.0, keys)
    _swell(r, "death", [(0.5, 1.12), (1.4, 0.97), (5.0, 0.97)])
    _scale3(r, "death", "throat", [(0.5, _sac(0.6)), (1.4, _sac(0.0)), (3.1, _sac(0.25)), (3.4, _sac(0.0)), (4.1, _sac(0.15)), (4.4, _sac(0.0)), (5.0, _sac(0.0))])


# ---------------------------------------------------------------------------------------------------------------------
# Moves

NO_LAG = {b: 0 for k in range(4) for b in LIMB[k]}
NO_LAG.update({"jaw": 0, "roll": 0, "tongue": 0, "tongue_tip": 0, "eye_l": 0, "eye_r": 0})


def _lag(**kw):
    out = dict(NO_LAG)
    out.update(kw)
    return out


def _back(r, end):
    home = _pose(r)
    return [(0.45, _mix(end, home, 0.45)), (0.7, _mix(end, home, 0.82))]


def tongue(r):
    """The Tongue (LIGHT): it draws its head back with its eyes on you and its mouth opening on the fire inside it; its
    tongue flicks out at you, slaps, and is back in its mouth."""
    draw = _pose(r, lift=1.0, shift=(0, 1.5), bend=-2, head=(-6, 0, 0), jaw=10, tongue=-4, eyes=(0, 0))  # up on its arms as it draws back
    out = _pose(r, lift=-0.6, shift=(0, -1.4), bend=3, head=(3, 0, 0), jaw=30, tongue=-16, eyes=(0.15, 0.15))  # and low as it flicks
    held = _pose(r, lift=-0.7, shift=(0, -1.6), bend=3.5, head=(4, 0, 0), jaw=26, tongue=-14, eyes=(0.2, 0.2))
    m = Move("tongue", "LIGHT", 10, 4, 14, through=held, title="Tongue", track=8.0, rng=(0, 5.0), kinds=("combo", "near"), damage=0.9, knock=0.6,
             hit=[("tongue_tip", (0, 0, -4), 6.0), ("tongue", (0, 0, -6), 4.0)], plant=(),
             stages={"windup": [(0.4, _mix(_pose(r), draw, 0.6)), (1.0, draw)], "active": [(0.5, out), (1.0, held)], "recover": _back(r, held)}, lag=_lag())
    m.tongue = lambda W, A, R: [(W, 1.0), (W + 0.5 * A, 5.5), (W + A, 4.5), (W + A + 0.25 * R, 1.0)]
    return m


def gulp(r):
    """The Gulp (LIGHT): it sits back on its haunches; it lunges with its whole front, its mouth gaping wide on the fire
    in it, snaps it shut on you, and swallows, its eyes sinking down into its head."""
    gather = _pose(r, lift=-1.8, pitch=-5, shift=(0, 3.5), bend=-4, head=(-9, 0, 0), jaw=18, eyes=(0, 0), feet={0: _foot(0, dz=1.5), 1: _foot(1, dz=1.5)})
    lunge = _pose(r, lift=1.0, pitch=4, shift=(0, -6.0), bend=5, head=(2, 0, 0), jaw=42, eyes=(0, 0))
    bite = _pose(r, lift=0.4, pitch=5, shift=(0, -7.0), bend=6, head=(6, 0, 0), jaw=0, eyes=(0.2, 0.2))
    held = _pose(r, lift=0.2, pitch=5.2, shift=(0, -7.0), bend=6.2, head=(7, 0, 0), jaw=0, eyes=(1.0, 1.0))
    return Move("gulp", "LIGHT", 10, 4, 14, through=held, title="Gulp", track=8.0, rng=(0, 4.5), kinds=("combo", "near"), damage=1.1, knock=0.5,
                hit=[("head", (0, 2, -26), 7.0), ("jaw", (0, 4, -26), 6.0)], plant=(),
                stages={"windup": [(0.4, _mix(_pose(r), gather, 0.6)), (1.0, gather)], "active": [(0.45, lunge), (0.75, bite), (1.0, held)],
                        "recover": [(0.35, _mix(held, _pose(r), 0.3)), (0.7, _mix(held, _pose(r), 0.8))]}, lag=_lag())


ADVANCE = 7.0  # blocks the Leap carries it


def leap(r):
    """The Leap (HEAVY, its gap closer): it sinks back on its haunches with its great hind legs bunched up at its sides
    and its eyes on you, holds, and jumps: it flies at you with its hands reaching ahead and its long feet trailing, and
    comes down on its belly with a thump, its legs splayed."""
    def crouch(q=0.0):
        return _pose(r, lift=-2.6 - 0.5 * q, pitch=-4 - 1.5 * q, shift=(0, 1.5 + 0.5 * q), bend=-3, head=(-4 - 3 * q, 0, 0), jaw=4 + 4 * q, eyes=(0, 0),
                     feet={2: _foot(2, dz=-3.0), 3: _foot(3, dz=-3.0)})
    push = _pose(r, lift=3.0, pitch=-10, shift=(0, -3.0), bend=-4, head=(-6, 0, 0), jaw=6, local={2: (0.0, 9.0, 16.0), 3: (0.0, 9.0, 16.0)},
                 hands={2: -40.0, 3: -40.0})
    fly = _pose(r, lift=9.0, pitch=2, shift=(0, -4.0), bend=-2, head=(-2, 0, 0), jaw=8, local={0: (0.0, 9.0, -9.0), 1: (0.0, 9.0, -9.0), 2: (0.0, 5.0, 22.0),
                                                                                                3: (0.0, 5.0, 22.0)},
                hands={0: 30.0, 1: 30.0, 2: -60.0, 3: -60.0})
    land = _floor(r, _pose(r, lift=-4.0, pitch=4, shift=(0, -2.0), bend=3, head=(6, 0, 0), jaw=10, eyes=(0.8, 0.8), feet=SPLAY, hands={k: 0.0 for k in range(4)}))
    after = _floor(r, _pose(r, lift=-3.6, pitch=3, shift=(0, -1.5), bend=2, head=(4, 0, 0), jaw=4, eyes=(0.3, 0.3), feet=SPLAY, hands={k: 0.0 for k in range(4)}))
    return Move("leap", "HEAVY", 18, 10, 22, through=after, title="the Leap", hold=6, track=6.0, lock=12, advance=ADVANCE, rng=(5, 14), kinds=("gap",),
                damage=1.3, knock=1.2, hit=[("chest", (0, 18, -12), 10.0), ("hips", (0, 14, 0), 9.0)], plant=(),
                stages={"windup": [(0.35, _mix(_pose(r), crouch(), 0.6)), (0.75, crouch()), (1.0, crouch(1))],
                        "active": [(0.25, push), (0.6, fly), (1.0, land)], "recover": [(0.4, after), (0.75, _mix(after, _pose(r), 0.7))]}, lag=_lag())


def ring(r):
    """Hearth Ring (HEAVY): it rises up as high as its legs will lift it, its throat swelling, holds, and drops flat on
    its belly with all its weight; the roots on its back go down into the floor in a ring round it."""
    def high(q=0.0):
        return _pose(r, lift=6.0 + 0.4 * q, pitch=-3, bend=-2, head=(-6, 0, 0), jaw=4 + 4 * q, eyes=(0, 0))
    drop = _floor(r, _pose(r, lift=-4.0, pitch=2, bend=4, head=(8, 0, 0), jaw=14, eyes=(0.8, 0.8), feet=SPLAY, hands={k: 0.0 for k in range(4)}))
    held = _floor(r, _pose(r, lift=-3.8, pitch=2, bend=4, head=(8, 0, 0), jaw=8, eyes=(0.6, 0.6), feet=SPLAY, hands={k: 0.0 for k in range(4)}))
    return Move("ring", "HEAVY", 18, 6, 22, through=held, title="Hearth Ring", hold=8, track=0.0, ability="Hearth Ring", rng=(0, 6.0), kinds=("area", "near"),
                damage=1.2, knock=1.3, disc=6.0, hit=[("chest", (0, 14, -10), 11.0), ("hips", (0, 14, 0), 11.0)], plant=(),
                stages={"windup": [(0.3, _mix(_pose(r), high(0.0), 0.55)), (0.65, high(0.3)), (1.0, high(1.0))], "active": [(0.6, drop), (1.0, held)],
                        "recover": [(0.4, held), (0.75, _mix(held, _pose(r), 0.65))]}, lag=_lag())


def lance(r):
    """Root Lance: it leans over its hands and presses them flat on the floor ahead of it, its head low toward you; its
    whole body heaves twice, and the roots on its back go taut and down into the floor."""
    press = lambda h: _pose(r, lift=-0.8 - 0.4 * h, pitch=3 + 1.5 * h, shift=(0, -1.5), bend=6 + 2 * h, head=(6 + 2 * h, 0, 0), jaw=6 + 6 * h, eyes=(0.1 * h, 0.1 * h),
                            feet={0: _foot(0, dz=-4.0), 1: _foot(1, dz=-4.0)})
    act = [(0.15, press(1)), (0.4, press(0.2)), (0.6, press(1)), (0.85, press(0.3)), (1.0, press(0.5))]
    return Move("lance", "NONE", 14, 20, 16, through=press(0.4), title="Root Lance", track=6.0, ability="Root Lance", rng=(4, 18), kinds=("ranged",),
                damage=0, knock=0, plant=(),
                stages={"windup": [(0.5, _mix(_pose(r), press(0), 0.6)), (1.0, press(0))], "active": act, "recover": _back(r, press(0.4))}, lag=_lag())


def holdfast(r):
    """Hold Fast: it fixes its eyes on you and opens its mouth wide on the fire inside it, and its tongue shoots out the
    whole way to you and holds, tugging; then it reels it in and swallows."""
    aim = _pose(r, lift=0.6, pitch=-2, shift=(0, 1.0), bend=-2, head=(-4, 0, 0), jaw=20, tongue=-8, eyes=(0, 0))
    shot = lambda q: _pose(r, lift=0.4 - 0.3 * q, pitch=-1 - 1.5 * q, shift=(0, -0.5 + 2.0 * q), bend=1 + 0.5 * q, head=(-3 - 2 * q, 0, 0), jaw=34, tongue=-18,
                           eyes=(0.1 * q, 0.1 * q))  # each tug pulls it back on its haunches, its head coming up: the tongue stays off the floor
    swallow = _pose(r, lift=0.5, bend=-0.5, head=(-3, 0, 0), jaw=0, eyes=(0.4, 0.4))  # mouth shut on it, head up, about to swallow
    gulp_ = _pose(r, lift=-0.9, bend=2.5, head=(8, 0, 0), jaw=0, eyes=(1, 1))  # it swallows hard, its eyes sunk in
    m = Move("holdfast", "NONE", 12, 18, 16, through=gulp_, title="Hold Fast", track=6.0, ability="Hold Fast", rng=(3, 9), kinds=("ranged",), damage=0, knock=0,
             plant=(),
             stages={"windup": [(0.45, _mix(_pose(r), aim, 0.6)), (1.0, aim)],
                     "active": [(0.15, shot(0)), (0.35, shot(1)), (0.55, shot(0)), (0.75, shot(1)), (0.9, _mix(shot(0), swallow, 0.5)), (1.0, swallow)],
                     "recover": [(0.5, _pose(r, lift=0.5, bend=-1.5, head=(-4, 0, 0), jaw=3, eyes=(0.1, 0.1))),  # head up, eyes open on you
                                 (0.72, _mix(_pose(r, lift=0.2, head=(-1, 0, 0), eyes=(0.5, 0.5)), _pose(r), 0.4))]}, lag=_lag())
    m.tongue = lambda W, A, R: [(W, 1.0), (W + 0.15 * A, 9.5), (W + 0.35 * A, 9.0), (W + 0.55 * A, 9.5), (W + 0.75 * A, 9.0), (W + 0.92 * A, 1.0)]
    return m


def croak(r):
    """Rooted Ones: it sits up on its forelegs and croaks, three times, its throat swelling out huge and pale each time
    and its whole body shaking with it, and the roots on its back stir."""
    up = lambda q: _pose(r, lift=1.0 + 0.6 * q, pitch=-5, bend=-4 - 2 * q, head=(-8 - 3 * q, 0, 0), jaw=4 * q, eyes=(0.2 * q, 0.2 * q),
                         roll=1.5 * math.sin(7 * q))
    act = [(f, up(q)) for f, q in ((0.15, 1.0), (0.28, 0.2), (0.48, 1.0), (0.6, 0.2), (0.82, 1.0), (1.0, 0.3))]
    m = Move("croak", "NONE", 12, 24, 14, through=up(0.3), title="Rooted Ones", track=0.0, ability="Rooted Ones", rng=(0, 99), kinds=("summon",), damage=0,
             knock=0, plant=(), stages={"windup": [(0.5, _mix(_pose(r), up(0), 0.6)), (1.0, up(0))], "active": act, "recover": _back(r, up(0.3))},
             lag=_lag())
    m.sac = lambda W, A, R: [(W, _sac(0.1))] + [(W + f * A, _sac(1.0 if q > 0.5 else 0.15)) for f, q in ((0.15, 1.0), (0.28, 0.2), (0.48, 1.0), (0.6, 0.2),
                                                                                                         (0.82, 1.0), (1.0, 0.3))] + [(W + A + 0.5 * R, _sac(0.05))]
    return m


def halo(r):
    """Thorn Halo (the signature): it blows itself up as a toad does when something means to eat it: it swells, rises
    high on stiff straight legs, lowers its head and tips toward you, trembling; and at its fullest its warts burst
    into thorns all round it and the air goes out of it."""
    def puffed(q=0.0, j=0.0):
        return _pose(r, lift=6.0 + 0.5 * q, pitch=6 + 2 * q, bend=4, head=(14 + 2 * q, 0, 0), roll=1.2 * j, side=1.5 * j, jaw=0, eyes=(0.5, 0.5))
    act = [(f, puffed(0.6, (-1) ** n)) for n, f in enumerate((0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7))] + [(0.8, puffed(1.0)), (0.88, puffed(1.0))]
    slump = _pose(r, lift=-1.5, pitch=2, bend=3, head=(6, 0, 0), jaw=10, eyes=(0.3, 0.3))
    act.append((1.0, slump))
    m = Move("halo", "NONE", 16, 26, 18, through=slump, title="Thorn Halo", signature=True, track=4.0, ability="Thorn Halo", rng=(0, 99), kinds=("area",),
             damage=0, knock=0, plant=(),
             stages={"windup": [(0.4, _mix(_pose(r), puffed(), 0.5)), (1.0, puffed())], "active": act, "recover": [(0.35, slump), (0.75, _mix(slump, _pose(r), 0.7))]},
             lag=_lag())
    m.swell = lambda W, A, R: [(W * 0.2, 1.0), (W, 1.16), (W + 0.8 * A, 1.22), (W + 0.88 * A, 1.27), (W + A, 0.98), (W + A + 0.6 * R, 1.0)]
    return m


def kick(r):
    """The Kick (for whoever is behind it): it drops onto its hands, draws its feet up under it, and kicks both hind
    legs out straight behind it."""
    tuck = _pose(r, lift=-0.7, pitch=7, bend=4.5, head=(5, 0, 0), jaw=5, eyes=(0.3, 0.3), feet={2: _foot(2, up=2.5, dz=-4.5), 3: _foot(3, up=2.5, dz=-4.5)},
                 hands={2: -26.0, 3: -26.0})
    out = _pose(r, lift=2.5, pitch=10, bend=5, head=(6, 0, 0), jaw=8, eyes=(0.6, 0.6), local={2: (0.0, 3.5, 23.0), 3: (0.0, 3.5, 23.0)}, hands={2: -70.0, 3: -70.0})
    held = _pose(r, lift=2.3, pitch=9.5, bend=5, head=(6, 0, 0), jaw=6, eyes=(0.5, 0.5), local={2: (0.0, 4.0, 22.0), 3: (0.0, 4.0, 22.0)}, hands={2: -66.0, 3: -66.0})
    return Move("kick", "LIGHT", 10, 4, 14, through=held, title="the Kick", track=0.0, rng=(0, 5.0), kinds=("behind",), damage=1.0, knock=1.4,
                hit=[("foot2", (0, 2, -10), 7.0), ("foot3", (0, 2, -10), 7.0)], plant=(),
                stages={"windup": [(0.4, _mix(_pose(r), tuck, 0.6)), (1.0, tuck)], "active": [(0.45, out), (1.0, held)],
                        "recover": [(0.3, _mix(held, tuck, 0.35)), (0.55, _mix(held, tuck, 0.8)), (0.8, _mix(tuck, _pose(r), 0.75))]}, lag=_lag())


def arrive(r):
    """Its intro: it settles as still as a stone, its eyes shut; then its eyes open on you; it blinks, slowly, and
    croaks once, long, its throat swelling out huge; and it lowers itself, watching you."""
    stone = _pose(r, lift=-1.5, bend=2, head=(4, 0, 0), eyes=(1, 1))
    look = _pose(r, lift=-1.0, bend=1, head=(0, 0, 0), eyes=(0, 0))
    blink = _pose(r, lift=-1.0, bend=1, head=(1, 0, 0), eyes=(1, 1))
    call = _pose(r, lift=1.2, pitch=-4, bend=-4, head=(-10, 0, 0), jaw=4, eyes=(0.1, 0.1))
    low = _pose(r, lift=-1.6, pitch=1, bend=3, head=(2, 0, 0), jaw=2, eyes=(0, 0))
    m = Move("arrive", "NONE", 14, 30, 14, through=_mix(low, _pose(r), 0.2), track=0.0, rng=(0, 99), kinds=("intro",), damage=0, knock=0, plant=(),
             stages={"windup": [(0.5, _mix(_pose(r), stone, 0.7)), (1.0, stone)],
                     "active": [(0.2, stone), (0.32, look), (0.42, look), (0.48, blink), (0.55, look), (0.68, call), (0.86, call), (1.0, low)],
                     "recover": [(0.6, _mix(low, _pose(r), 0.5))]}, lag=_lag())
    m.sac = lambda W, A, R: [(W + 0.6 * A, _sac(0.05)), (W + 0.72 * A, _sac(1.1)), (W + 0.88 * A, _sac(1.0)), (W + A, _sac(0.1))]
    return m


# the ground (SPEC §35.10): the hands and feet stay out of the floor (the per-tick solve, then a flex of the wrist or ankle)
PAW_GROUND = [(PAW[k], [(LIMB[k][2], 0, 40)]) for k in range(4)]
HEAD_GROUND = [(["head", "jaw", "throat"], [("head", 0, 30, -1)])]
STANDS = {"tongue": (0, 1, 2, 3), "gulp": (2, 3), "leap": (), "ring": (), "lance": (2, 3), "holdfast": (0, 1, 2, 3), "croak": (0, 1, 2, 3), "halo": (0, 1, 2, 3),
          "kick": (0, 1), "arrive": (0, 1, 2, 3)}


def clips(r):
    _idle(r)
    _locomotion(r)
    _reactions(r)
    mv = [tongue(r), gulp(r), leap(r), ring(r), lance(r), holdfast(r), croak(r), halo(r), kick(r), arrive(r)]
    for m in mv:
        stage_clips(r, m)
        n = core.clip_name(m)
        if STANDS[m.id]:
            _plant(r, n, STANDS[m.id])
        W, A, R_ = m.seconds("windup"), m.seconds("active"), m.seconds("recover")
        if hasattr(m, "tongue"):
            _tongue(r, n, m.tongue(W, A, R_))
        if hasattr(m, "sac"):
            _scale3(r, n, "throat", m.sac(W, A, R_), "cat")
        if hasattr(m, "swell"):
            _swell(r, n, m.swell(W, A, R_))
    everyday = [n for n in r.clips if n != "death"]
    for n in r.clips:
        _on_floor(r, n)
    core.ground_pass(r, PAW_GROUND, clips=everyday)
    core.ground_pass(r, PAW_GROUND + HEAD_GROUND, clips=["death", "kneel", "rise"], skip=())
    return mv


# Its sounds (SPEC §35.8): a frog's croaks, its tongue and its jumps, deep and slow; roots and mud; the creaking's
# groans; the crackle of the fire in it. Chosen on paper, not heard in game.
SOUNDS = {
    "tell_light": [("entity.frog.ambient", 1.0, 0.5), ("block.mangrove_roots.break", 0.6, 0.7)],
    "tell_heavy": [("entity.creaking.ambient", 1.1, 0.6), ("entity.frog.ambient", 1.0, 0.4)],
    "tell_perilous": [("entity.creaking.activate", 1.2, 0.5), ("block.campfire.crackle", 1.0, 0.6)],
    "swing_light": [("entity.frog.tongue", 1.2, 0.5), ("block.hanging_roots.break", 0.8, 0.7)],
    "swing_heavy": [("entity.frog.long_jump", 1.2, 0.5), ("entity.creaking.attack", 1.0, 0.6)],
    "cast": [("block.rooted_dirt.break", 1.2, 0.5), ("entity.creaking.sway", 1.0, 0.6)],
    "impact": [("block.mud.break", 1.2, 0.5), ("item.mace.smash_ground", 1.0, 0.6)],
    "roar": [("entity.frog.ambient", 1.4, 0.3), ("entity.ravager.roar", 0.5, 1.4)],
    "eat": [("entity.frog.eat", 1.2, 0.5), ("block.campfire.crackle", 0.8, 0.8)],
    "thorns": [("block.roots.break", 1.4, 0.6), ("entity.creaking.spawn", 1.0, 0.7)],
    "step": [("entity.frog.step", 1.2, 0.4), ("block.mud.step", 0.8, 0.6)],
}
MOVE_SOUNDS = {"gulp": {"impact": "eat"}, "holdfast": {"swing": "swing_light", "impact": ""}, "croak": {"swing": "roar", "impact": ""},
               "halo": {"swing": "thorns", "impact": ""}, "lance": {"swing": "cast", "impact": ""}, "arrive": {"swing": "roar", "impact": ""}}

FIGHT = {
    "intro": {"pose": "idle", "move": "arrive"},  # still as a stone, then its eyes open and it croaks
    "phases": [
        {"pool": [
            {"move": "tongue", "weight": 3, "when": "near", "then": [["gulp"]]},
            {"move": "leap", "weight": 2, "when": "far"},
            {"move": "lance", "weight": 3, "when": "far"},
            {"move": "holdfast", "weight": 2},
            {"move": "kick", "weight": 3, "when": "behind"},
        ]},
        # "Stay.": its croak brings its rooted ones, and its Hearth Ring
        {"transition": "croak", "pool": [
            {"move": "tongue", "weight": 3, "when": "near", "then": [["gulp"]]},
            {"move": "ring", "weight": 2, "when": "near"},
            {"move": "lance", "weight": 3, "when": "far"},
            {"move": "leap", "weight": 2, "when": "far", "then": [["tongue"]]},
            {"move": "croak", "weight": 1},
            {"move": "holdfast", "weight": 2},
            {"move": "kick", "weight": 3, "when": "behind"},
        ]},
        # "Never leave.": it blows itself up into the Thorn Halo and holds harder
        {"transition": "halo", "pool": [
            {"move": "holdfast", "weight": 3},
            {"move": "ring", "weight": 2, "when": "near"},
            {"move": "tongue", "weight": 2, "when": "near", "then": [["gulp"]]},
            {"move": "lance", "weight": 2, "when": "far"},
            {"move": "leap", "weight": 2, "when": "far", "then": [["tongue", "gulp"]]},
            {"move": "halo", "weight": 1},
            {"move": "kick", "weight": 3, "when": "behind"},
        ]},
    ],
    "enrage": {"below": 0.15, "speed": 1.2},
}


def design():
    r = body()
    r.pack()
    mv = clips(r)
    return {"rig": r, "plan": None, "moves": mv, "fight": FIGHT, "paint": paint, "scale": SCALE, "half": 1.0, "sounds": SOUNDS,
            "move_sounds": MOVE_SOUNDS}


def probe():
    r = body()
    fr = core.frames(r, {})
    for b, p in (("hand0", (0, 0, 0)), ("foot2", (0, 0, 0)), ("head", (0, 0, 0)), ("eye_l", (0, -4, 0)), ("fern", (0, -6, 0)), ("rootb_l0", (0, 7, 0))):
        print(b, p, [round(v, 2) for v in core.body(core.point(fr, b, p))])
    print("faces", core.faces(r), "cubes", sum(len(b.cubes) for b in r.bones), "bones", len(r.bones))
    print(core.floating(r))


if __name__ == "__main__":
    probe()
