"""The Choirmaster (SPEC §35.11, docs/bosses/ROLLOUT.md): the Drowned Choir's Guardian, a Caster. The Cantor, picked
from its silhouette round 2026-10-29: "The singing was coming from her all along."

A great pale octopus standing up on six of her arms, her mantle swaying up behind her head like a hood, her two front
arms raised to lead the choir. She sings through her siphon, which flares in front of her like a horn, its throat
glowing. Her skin is pale pearl and rose, dusky along the backs of her arms, ringed all over with glowing blue rings;
her suckers are cream; her eyes are gold, with a dark bar for a pupil.

Moves (SPEC §35.11): a front arm lashed across in front of her, then the other (LIGHT); the Breaker (both front arms
raised high and brought down together like a wave breaking: HEAVY); a back arm swept round behind her; the jet (she
draws her arms in under her, her mantle swelling, and shoots at you along the floor, arms trailing, and spreads them
over you: a gap closer); the Hymn of Salt (she rises tall and beats time with her front arms, and each beat she sings
a bolt of water); the Refrain (she wraps her arms round herself and hums, and heals); the Choir (a long note through
her horn, her arms beckoning up out of the floor); Undertow (both front arms reach far out at you and haul back, hand
over hand); and Rising Tide (the signature: every arm rises up round her, holds, and comes down on the floor at once,
and the water rises). Broken, she sinks down onto her curled arms; that is how she waits for you, too. She dies as an
octopus does, sinking and spreading, her colour going out.
"""
import math

from kit import core, ik, parts
from kit.core import DEG, Move, keyed, stage_clips
import fauna_rig as R
from fauna_rig import Ramp, hexc, SIDES

ID = "guardian_choir"
SCALE = 2.0  # the hitbox on the def's WITCH model at size 0.8 x 1.9: 1.6 x 3.8 blocks, round her body and mantle

# The Cantor (docs/bosses/ROLLOUT.md, C). The arm crown 1.4 blocks up, the top of her mantle 4 blocks; her standing arms
# reach 2.3 blocks out from her middle to the floor. Value story: the glowing rings and the siphon's throat brightest,
# then her pale pearl skin and cream suckers, then the rose and dusk along the backs of her arms; the eyes gold.
HUB = 22.0  # the arm crown's middle (pixels above the floor)
N = 7  # segments an arm
SEG_L = (8, 8, 7, 7, 6, 5, 5)  # each segment's length, crown to tip: 46 px an arm
SEG_W = (6, 5, 5, 4, 3, 3, 2)
SEG_H = (5, 5, 4, 4, 3, 2, 2)
REACH = 4  # the segments that reach for the floor; the last three lie along it and curl up at the tip
# the arms round her crown, front to back (+x her left): 0, 1 the front pair she leads with; 2 to 7 stand
ANGLE = (24, 24, 64, 64, 112, 112, 154, 154)  # degrees from straight ahead
SIDE = (1, -1, 1, -1, 1, -1, 1, -1)
LEAD = (0, 1)
STAND = (2, 3, 4, 5, 6, 7)
RING = 5.0  # the arms' roots round the crown
SPREAD = 27.5  # where the standing arms meet the floor, from her middle
ARM_YAW = tuple(-SIDE[k] * ANGLE[k] for k in range(8))  # each arm's root turned to face out along it (y: + to her right)
CONTACT = 22.0  # degrees down from the floor's line at which a standing arm meets the floor (a wish: the floor point wins)
CURL = 34.0  # degrees the tip of a standing arm curls up off the floor


def _out(k, d):
    """A point d out from her middle along arm k's way, (x, z)."""
    a = ARM_YAW[k] * DEG
    return (-math.sin(a) * d, -math.cos(a) * d)


REST_TIP = {k: (_out(k, SPREAD)[0], SEG_H[REACH - 1] / 2 + 0.9, _out(k, SPREAD)[1] + 1.0) for k in STAND}  # (x, height, z)
# the front arms at rest, raised to lead: (root pitch, each joint's curl, its growth along the arm, yaw out, side)
LEAD_REST = (-74.0, 2.0, 2.6, 18.0, 0.0)


def _bone(parent, name, x, up, z, rot=(0, 0, 0)):
    return parent.bone(name, (x, -up, z), rot)


def _box(bone, name, cx, y0, cz, w, h, d, mirror=False):
    return bone.cube(name, cx - w / 2, -(y0 + h), cz - d / 2, w, h, d, mirror=mirror)


def body():
    r = R.Rig(ID, (512, 512))
    base = r.bone("base", (0, 24, 0))
    bd = _bone(base, "body", 0, HUB, 0)
    # the crown the arms grow from, webbed between them, the beak under it
    _box(bd, "web", 0, -4, 0, 14, 7, 14)
    _box(bd, "webl", 0, -5, 0, 10, 1, 10)
    hd = _bone(bd, "head", 0, 3.0, 0)
    _box(hd, "headc", 0, 0, 0, 14, 10, 12)
    _box(hd, "brow", 0, 8, -2, 12, 3, 8)
    for s, nm in ((1, "l"), (-1, "r")):
        _box(hd, "eye_" + nm, s * 6.5, 4, -2, 3, 5, 5)
        _box(hd, "horn_" + nm, s * 5.0, 10, -3, 2, 3, 2)  # the skin horns over her eyes
    # the siphon, a horn under her eyes that widens to its mouth
    sp = _bone(hd, "siphon", 0, 1.5, -5.0, (30, 0, 0))
    _box(sp, "siphonc", 0, -2, -3.5, 4, 4, 7)
    bl = _bone(sp, "bell", 0, 0, -7.0)
    _box(bl, "bell1", 0, -3, -0.5, 6, 6, 1)
    _box(bl, "bellc", 0, -4, -1.5, 8, 8, 1)
    # the mantle, swaying up behind her head: narrow where it leaves the head, swelling to a great bulb, rounded on top
    m1 = _bone(hd, "mantle", 0, 7.0, 4.0, (-34, 0, 0))
    _box(m1, "mantle1", 0, 0, 1, 15, 12, 14)
    m2 = _bone(m1, "mantle2", 0, 11.0, 1.0, (-8, 0, 0))
    _box(m2, "mantle2c", 0, 0, 0, 18, 12, 17)
    for k, (x, z) in enumerate(((5, -6), (-6, 3), (2, 6))):
        _box(m2, f"pap{k}", x, 12, z, 2, 2, 2)  # warts of skin on it
    m3 = _bone(m2, "mantle3", 0, 11.0, 0.0, (-10, 0, 0))
    _box(m3, "mantle3c", 0, 0, 0, 14, 7, 13)
    _box(m3, "mantle3t", 0, 7, 0, 8, 2, 8)
    # the arms: seven segments each, tapering, the suckers in a row down the side toward the floor
    for k in range(8):
        ox, oz = _out(k, RING)
        b = _bone(bd, f"a{k}_0", ox, -1.5, oz, (0, ARM_YAW[k], 0))
        for i in range(N):
            if i > 0:
                b = b.bone(f"a{k}_{i}", (0, 0, -SEG_L[i - 1]))
            L_, w, h = SEG_L[i], SEG_W[i], SEG_H[i]
            lap = 1 if i > 0 else 0  # each segment runs a pixel back into the one before, so a bend never opens a gap
            b.cube(f"arm{k}_{i}", -w / 2, -h / 2, -L_, w, h, L_ + lap)
            if i < N - 2:
                b.cube(f"suck{k}_{i}", -(w - 2) / 2, h / 2, -L_ + 0.5, w - 2, 1, L_ - 1)
    _set_rest(r)
    r.look = [("head", 0.35, 0.3)]
    r.shadow = 2.2
    r.size = 1.0
    # secondary parts (SPEC §35.10): the top of the mantle sways after her, the front arms' tips
    r.springs = [("mantle3", 0.5, 1.0, 0.1), ("a0_6", 0.4, 1.5, 0.1), ("a1_6", 0.4, 1.5, 0.1)]
    r.guard = [f"a{k}_{i}" for k in range(8) for i in (5, 6)]
    return r


# ---------------------------------------------------------------------------------------------------------------------
# Paint

SKIN = Ramp("#4a2e38", "#6a4650", "#8c6670", "#ad8890", "#c8a8ac", "#dcc4c4", "#ead8d4", "#f4e8e2")
DUSK = Ramp("#2a1828", "#3e2436", "#563244", "#704456", "#8c5a6a", "#a8727e")
SUCK = Ramp("#7a6450", "#a08a70", "#c4ae90", "#dccaa8", "#eee0c4", "#f8f0dc")
RINGC = ("#0a1830", "#1a3a6a", "#3a8ad0", "#7ad8ff", "#c8f4ff")
EYE = ("#1a1004", "#5a3a10", "#c89030", "#f0c868")
THROAT = Ramp(*RINGC)


def _rings(P_, c, big, seed=0):
    """Her blue rings: on the broad skin of her head and mantle a ring of glowing blue round a dark heart (3 x 3); on her
    arms a glowing fleck with a dark edge. Scattered, about one in every 6 x 6 pixels on the broad skin."""
    step = 6 if big else 4
    for fname in SIDES + ("top",):
        f, g = P_.face(c, fname), P_.face(c, fname, glow=True)
        if f.w < 3 or f.h < 3:
            continue
        for ci in range(1, f.w - 1, step):
            for cj in range(1, f.h - 1, step):
                h = R._h(c, fname, seed, ci, cj)
                if h % (2 if big else 3):
                    continue
                i = min(f.w - 2, ci + h % 3)
                j = min(f.h - 2, cj + (h // 3) % 3)
                if big:
                    for di in (-1, 0, 1):
                        for dj in (-1, 0, 1):
                            if di or dj:
                                f.set(i + di, j + dj, hexc(RINGC[3], 255))
                                g.set(i + di, j + dj, hexc(RINGC[3], 190))
                    f.set(i, j, hexc(RINGC[0], 255))
                else:
                    for di, dj in ((-1, 0), (1, 0), (0, -1), (0, 1)):
                        f.set(i + di, j + dj, hexc(RINGC[1], 255))
                    f.set(i, j, hexc(RINGC[3], 255))
                    g.set(i, j, hexc(RINGC[3], 200))


def paint(r, P_):
    """Pale pearl and rose skin, darker along the tops of the arms and over the mantle, with a soft mottle; glowing blue
    rings scattered over it; the suckers cream discs with darker rims; the siphon's throat glowing blue; gold eyes with a
    dark bar of a pupil."""
    of = lambda *ps: parts.cubes_of(r, *ps)
    mott = R.p_blotch("skin", scale=3, amount=0.6)
    for c in ("web", "headc", "brow", "mantle1", "mantle2c", "mantle3c", "mantle3t"):
        P_.paint(c, SKIN, 5.0, mott, grad=1.2, rim=0.6, ao=0.8, noise=0.5, nscale=3)
    P_.paint("webl", SKIN, 3.6, None, grad=0.4, rim=0.3, ao=0.4)
    for c in of("pap", "horn_"):
        P_.paint(c, SKIN, 4.2, None, grad=0.8, rim=0.8, ao=0.3)
    for c in ("eye_l", "eye_r"):
        P_.paint(c, SKIN, 4.4, None, grad=0.6, rim=0.6, ao=0.4)
    # the backs of her arms dusky, their undersides pale; darker toward the crown
    for c in of("arm"):
        P_.paint(c, SKIN, 5.2, mott, grad=1.0, rim=0.6, ao=0.6, noise=0.4, nscale=3)
        f = P_.face(c, "top")
        for i, j in f.each():
            edge = i == 0 or i == f.w - 1
            f.set(i, j, DUSK.at((3.4 if edge else 2.6) + (R._h(c, i, j) % 3) * 0.3))
        for fname in ("west", "east"):
            f = P_.face(c, fname)
            for i, j in f.each():
                if j < f.h // 2:
                    f.set(i, j, DUSK.at(4.2 + (R._h(c, fname, i) % 2) * 0.4))
    # the mantle dusky over its back
    for c in ("mantle1", "mantle2c", "mantle3c"):
        for fname in ("top", "south"):
            f = P_.face(c, fname)
            for i, j in f.each():
                if R._h(c, fname, i // 2, j // 2) % 3:
                    f.set(i, j, DUSK.at(3.8 + (R._h(c, i, j) % 3) * 0.4))
    for c in of("suck"):
        P_.paint(c, SUCK, 3.4, None, grad=0.4, rim=0.4, ao=0.3)
        f = P_.face(c, "bottom")
        for i, j in f.each():  # a row of discs, a darker rim round each pale cup
            u, v = i % 3, j % 3
            f.set(i, j, SUCK.at(4.6) if (u == 1 and v == 1) else SUCK.at(1.6) if (u + v) % 2 else SUCK.at(3.2))
    for c in of("arm", "web", "mantle", "headc"):
        _rings(P_, c, big=not c.startswith("arm"), seed=1)
    # the siphon: pale skin outside, the bell's lip rose, its throat glowing blue
    P_.paint("siphonc", SKIN, 4.6, None, grad=0.8, rim=0.6, ao=0.6)
    for c in ("bell1", "bellc"):
        P_.paint(c, SKIN, 5.4, None, grad=0.6, rim=1.0, ao=0.3)
    f, g = P_.face("bellc", "north"), P_.face("bellc", "north", glow=True)
    for i, j in f.each():  # its lip rose, and its throat glowing blue, brightest deepest in
        ring = min(i, j, f.w - 1 - i, f.h - 1 - j)
        if ring == 0:
            f.set(i, j, DUSK.at(4.6))
        else:
            f.set(i, j, THROAT.at(1.0 + ring * 0.9))
            g.set(i, j, hexc(RINGC[min(4, 1 + ring)], 70 + 60 * ring))
    # the eyes: gold, a dark bar across, a pale glint
    with P_.eyes():
        for nm, fname in (("eye_l", "west"), ("eye_l", "east"), ("eye_r", "west"), ("eye_r", "east")):  # the inner face is hidden
            f, g = P_.face(nm, fname), P_.face(nm, fname, glow=True)
            for i, j in f.each():
                if 0 < i < f.w - 1 and 0 < j < f.h - 1:
                    f.set(i, j, hexc(EYE[2], 255))
                    g.set(i, j, hexc(EYE[3], 150))
            for i in range(1, f.w - 1):
                f.set(i, f.h // 2, hexc(EYE[0], 255))
                g.set(i, f.h // 2, (0, 0, 0, 0))
            f.set(1, 1, hexc(EYE[3], 255))
    return P_.skin, P_.glow


# ---------------------------------------------------------------------------------------------------------------------
# The arms: each is posed as one smooth curve in its own upright plane. A standing arm reaches its floor point with its
# first five segments (the root's pitch, then each joint's turn growing evenly along the arm, solved so that it gets
# there meeting the floor at CONTACT degrees), lies along the floor with the next, and curls up at its tip.

def _plane(th0, a, b, m):
    """The end of segment m-1 in the arm's plane (out, down) and its pitch, radians."""
    phi, x, y = th0, 0.0, 0.0
    for i in range(m):
        if i > 0:
            phi += a + b * i
        x += SEG_L[i] * math.cos(phi)
        y += SEG_L[i] * math.sin(phi)
    return x, y, phi


def _solve(D, H, end, guess, ref=None, floor=0.0):
    """(th0, a, b) radians that take the reaching segments to (D out, H down) arriving at pitch end. Setting the rest
    (no ref): exactly, from the last solution if it still gets there, else the best of a few starts, the nearest it can
    get if out of reach. Posed (ref, the rest's): the least-squares best that still puts the tip where it goes but lets
    its angle onto the floor give before the arm bends far from its rest shape; one smooth answer for each target, so
    nearby targets get nearby arms, a loop meets itself and nothing flips."""
    if ref is not None:
        return _newton(D, H, end, guess or ref, ref, floor)  # on from the last tick's (each clip starts from the rest's)
    best, bad = None, None
    starts = ([guess] if guess else []) + [(math.atan2(H, D) + 0.35, -0.08, -0.02), (math.atan2(H, D), 0.0, 0.0), (0.9, -0.2, 0.0)]
    for g in starts:
        q = _newton(D, H, end, g)
        x, y, p = _plane(*q, REACH)
        miss = math.hypot(x - D, y - H) + abs(p - end) * 3.0 + 0.5 * (abs(q[1]) + abs(q[2]) * 3)
        if bad is None or miss < bad:
            best, bad = q, miss
        if g is guess and math.hypot(x - D, y - H) < 0.05 and abs(p - end) < 0.01:
            return q
    return best


W_END, W_REG = 3.0, (0.6, 1.2, 3.6)  # posed: what the arm's angle onto the floor, and its bend from rest, weigh against a pixel


W_FLOOR = 4.0  # posed: what each pixel of an arm's middle in the floor weighs


def _res(D, H, end, q, ref, floor=0.0):
    x, y, p = _plane(*q, REACH)
    if ref is None:
        return (x - D, y - H, (p - end) * 3.0)
    # the middle of the arm kept out of the floor: the floor runs through the tip's point at pitch floor (the tip's
    # middle sits REST_TIP's height over it), and each joint sits at least its segment's half-height over it
    nx, ny = -math.sin(floor), math.cos(floor)
    sink = []
    for m in range(1, REACH):
        jx, jy, _ = _plane(*q, m)
        over = REST_TIP[2][1] - SEG_H[m] / 2 - 0.4
        sink.append(W_FLOOR * max(0.0, (jx - D) * nx + (jy - H) * ny + over))
    return (x - D, y - H, (p - end) * W_END) + tuple(W_REG[i] * (q[i] - ref[i]) for i in range(3)) + tuple(sink)


def _cost(D, H, end, q, ref, floor=0.0):
    return sum(c * c for c in _res(D, H, end, q, ref, floor))


def _newton(D, H, end, guess, ref=None, floor=0.0):
    q = list(guess)
    for _ in range(60):
        e = _res(D, H, end, q, ref, floor)
        if ref is None and abs(e[0]) + abs(e[1]) + abs(e[2]) < 1e-3:
            break
        J = []
        for j in range(3):
            qq = list(q)
            qq[j] += 1e-4
            e2 = _res(D, H, end, qq, ref, floor)
            J.append([(e2[n] - e[n]) / 1e-4 for n in range(len(e))])
        # (J^T J + l I) dq = -J^T e, J[j] being the column for unknown j
        A = [[sum(J[i][n] * J[j][n] for n in range(len(e))) + (0.02 if i == j else 0.0) for j in range(3)] for i in range(3)]
        g = [-sum(J[i][n] * e[n] for n in range(len(e))) for i in range(3)]
        dq = _lin3(A, g)
        big = max(abs(c) for c in dq)
        if big > 0.3:  # the whole step scaled down, never each part on its own (that bends it off its way)
            dq = [c * 0.3 / big for c in dq]
        err = sum(c * c for c in e)
        for _ in range(8):  # and halved until it helps
            nq = [q[i] + dq[i] for i in range(3)]
            if _cost(D, H, end, nq, ref, floor) < err:
                break
            dq = [c * 0.5 for c in dq]
        else:
            break
        if ref is not None and err - _cost(D, H, end, nq, ref, floor) < 1e-7:
            q = nq
            break
        q = nq
    return tuple(q)


def _lin3(A, g):
    m = [row[:] + [g[i]] for i, row in enumerate(A)]
    for c in range(3):
        p = max(range(c, 3), key=lambda r_: abs(m[r_][c]))
        m[c], m[p] = m[p], m[c]
        if abs(m[c][c]) < 1e-12:
            return [0.0, 0.0, 0.0]
        for r_ in range(3):
            if r_ != c:
                f = m[r_][c] / m[c][c]
                m[r_] = [m[r_][n] - f * m[c][n] for n in range(4)]
    return [m[i][3] / m[i][i] for i in range(3)]


GUESS = {}  # each standing arm's last solution, so the next one starts there and never jumps to another shape
REST_GUESS = {}


def _fresh():
    """Every clip's solving starts from the rest's arms, never from the clip solved before it (so a loop meets itself)."""
    GUESS.clear()
    GUESS.update(REST_GUESS)


WRITHE = 7.0  # degrees a standing arm writhes along its length while its tip stays where it is


def _null(rel):
    """Two ways (unit, at right angles) to turn the reaching segments' four pitches that leave their end where it is."""
    phi, cols = 0.0, []
    acc = []
    for i in range(REACH):
        phi += rel[i]
        acc.append(phi)
    for j in range(REACH):
        cols.append((-sum(SEG_L[i] * math.sin(acc[i]) for i in range(j, REACH)), sum(SEG_L[i] * math.cos(acc[i]) for i in range(j, REACH))))
    rows = [[c[0] for c in cols], [c[1] for c in cols]]
    basis = []
    for v in rows:
        for u in basis:
            d = sum(a * b for a, b in zip(v, u))
            v = [a - d * b for a, b in zip(v, u)]
        n = math.sqrt(sum(a * a for a in v)) or 1.0
        basis.append([a / n for a in v])
    out = []
    for v in ([0.0, 1.0, -1.5, 1.0], [1.0, -0.5, 0.5, -1.0]):
        for u in basis + out:
            d = sum(a * b for a, b in zip(v, u))
            v = [a - d * b for a, b in zip(v, u)]
        n = math.sqrt(sum(a * a for a in v)) or 1.0
        out.append([a / n for a in v])
    return out


def _stand_arm(r, fr, k, target, contact=CONTACT, curl=CURL, side=0.0, writhe=None):
    """Arm k's whole turns (degrees) reaching target (x, height, z) on the floor, its body posed as in fr. writhe
    (p1, p2), phases: the arm writhes along its length on two slow waves while its tip stays put."""
    M = fr["body"][0]
    T = fr[f"a{k}_0"][1]
    p = ik.model(target)
    d = [p[i] - T[i] for i in range(3)]
    loc = [sum(M[i][j] * d[i] for i in range(3)) for j in range(3)]  # into the crown's frame (M is a rotation)
    psi = math.atan2(-loc[0], -loc[2])
    D = math.hypot(loc[0], loc[2])
    H = loc[1]
    # the floor's line in the arm's plane: the world's down, seen from the crown
    gl = [M[1][j] for j in range(3)]
    u = (-math.sin(psi), 0.0, -math.cos(psi))
    floor = math.atan2(-(u[0] * gl[0] + u[2] * gl[2]), gl[1])
    th0, a, b = _solve(D, H, floor + contact * DEG, GUESS.get(k), REST_GUESS.get(k), floor)
    GUESS[k] = (th0, a, b)
    rel = [th0] + [a + b * i for i in range(1, REACH)]
    if writhe is not None:
        n1, n2 = _null(rel)
        w1, w2 = math.sin(writhe[0] + 1.9 * k), math.sin(writhe[1] + 0.7 * k)
        rel = [rel[i] + WRITHE * DEG * (w1 * n1[i] + 0.7 * w2 * n2[i]) for i in range(REACH)]
    phi = sum(rel)
    out = {f"a{k}_0": (rel[0] / DEG, psi / DEG, 0.0)}
    for i in range(1, REACH):
        out[f"a{k}_{i}"] = (rel[i] / DEG, side, 0.0)
    out[f"a{k}_{REACH}"] = ((floor - phi) / DEG - 3.0, side, 0.0)  # along the floor, a hair up off it
    out[f"a{k}_{REACH + 1}"] = (-curl * 0.35, side * 1.5, 0.0)  # and curling up at its tip
    out[f"a{k}_{REACH + 2}"] = (-curl * 0.65, side * 2.0, 0.0)
    return out


def _free_arm(k, th0, a, b, yaw=0.0, side=0.0, wave=None):
    """Arm k's whole turns for a shape: the root pitched th0 (+ down) and turned yaw (+ out to its side) from its own
    way; each joint after it turned a + b * i (+ curling down) and side (+ toward her left); wave(i) -> (pitch, side)
    more on joint i."""
    out = {f"a{k}_0": (th0, ARM_YAW[k] - SIDE[k] * yaw, 0.0)}
    for i in range(1, N):
        w = wave(i) if wave else (0.0, 0.0)
        out[f"a{k}_{i}"] = (a + b * i + w[0], side + w[1], 0.0)
    return out


def _set_rest(r):
    """The rig's rest: standing on six arms, the front two raised; every pose is turns on top of it."""
    fr = core.frames(r, {})
    turns = {}
    for k in STAND:
        GUESS.pop(k, None)
        for _ in range(2):
            turns.update(_stand_arm(r, fr, k, REST_TIP[k]))
    for k in LEAD:
        turns.update(_free_arm(k, *LEAD_REST))
    for b, rot in turns.items():
        r.by_name[b].rot = rot
    r.rest_guess = dict(GUESS)


# ---------------------------------------------------------------------------------------------------------------------
# Posing

def _ease(f):
    f = max(0.0, min(1.0, f))
    return f * f * (3 - 2 * f)


def _pose(r, lift=0.0, pitch=0.0, yaw=0.0, roll=0.0, shift=(0.0, 0.0), head=(0, 0, 0), mantle=(0, 0, 0), sway=(0.0, 0.0), siphon=(0.0, 0.0),
          bell=0.0, tips=None, free=None, contact=None, curl=None, side=None, extra=None, writhe=None):
    """lift (+ up), pitch (+ leaning forward), yaw (+ to her left), roll (+ her left side down) and shift (x, z) move her
    crown; head turns her head (and the mantle on it), mantle (pitch + forward, yaw, roll) the mantle at its root and
    sway (pitch, roll) shared down its upper parts; siphon (pitch + down, yaw) aims the horn and bell (0 to 1) flares
    its mouth open. tips {arm: (x, height, z)}: where a standing arm meets the floor (else where it stands at rest);
    free {arm: (th0, a, b, yaw, side)}: an arm off the floor in that shape (the front arms raised to lead, if not
    given); contact, curl, side {arm: degrees}: how a standing arm meets the floor, how its tip curls, and its sideways
    lean."""
    p = {"body": {"rot": (pitch, yaw, roll), "pos": (shift[0], lift, shift[1])},
         "head": head,
         "mantle": mantle,
         "mantle2": (sway[0] * 0.5, 0, sway[1] * 0.5),
         "mantle3": (sway[0] * 0.6, 0, sway[1] * 0.6),
         "siphon": (siphon[0], siphon[1], 0),
         "bell": {"rot": (-8.0 * bell, 0, 0), "pos": (0, 0, 0)}}
    fr = core.frames(r, core.as_sample(p))
    tips, free, contact, curl, side = tips or {}, free or {}, contact or {}, curl or {}, side or {}
    whole = {}
    for k in range(8):
        if k in free:
            whole.update(_free_arm(k, *free[k]))
        elif k in LEAD:
            whole.update(_free_arm(k, *LEAD_REST))
        else:
            whole.update(_stand_arm(r, fr, k, tips.get(k, REST_TIP[k]), contact.get(k, CONTACT), curl.get(k, CURL), side.get(k, 0.0), writhe))
    for b, rot in whole.items():
        p[b] = ik.delta(rot, r.by_name[b].rot)
    if extra:
        p.update(extra)
    return p


def _tip(k, out=0.0, up=0.0, along=0.0):
    """A standing arm's floor point moved out from her middle, up off the floor, and along (+ toward her front)."""
    x, h, z = REST_TIP[k]
    dx, dz = _out(k, out)
    return (x + dx, h + up, z + dz - along)


def _floor_yaw(k, spread, back=False):
    """The yaw (as _free_arm takes it) that points arm k straight ahead of her, or straight back, spread out to its side."""
    return (180.0 - spread - ANGLE[k]) if back else (spread - ANGLE[k])


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


def _wave(u, at, width):
    x = (u - at) / width
    return math.cos(math.pi * x) ** 2 if abs(x) < 0.5 else 0.0


def _writhe(t, k):
    """How a standing arm lives while it stands: how it meets the floor and how its tip curls, never still."""
    return CONTACT + 7.0 * math.sin(2 * math.pi * t / 1.5 + 1.3 * k), CURL + 16.0 * math.sin(2 * math.pi * t / 1.2 + 2.1 * k)


GRIP = (14.0, 34.0)  # degrees more contact and curl her standing arms brace with through a blow


def _brace(t, w, a, r_):
    """How hard her standing arms grip through a blow (0 to 1): tightening through the wind-up, held through the
    strike and the start of the recovery (a string's next blow links on still braced), let go before it ends."""
    if t < w:
        return core.ease_io(t / w) if w > 0 else 1.0
    if t < w + a:
        return 1.0
    return 1.0 - core.ease_io(min(1.0, max(0.0, t - w - a - 0.4 * r_) / max(0.05, 0.5 * r_)))


def _plant(r, clip, arms, where=None, t0=0.0, grip=None):
    """The arms that stand through a clip keep where they meet the floor, every tick, their tips writhing; grip(t) (0
    to 1): how hard they brace."""
    where = where or {}
    _fresh()

    def fn(t, s, fr):
        out = {}
        g = grip(t) if grip else 0.0
        for k in arms:
            ct, cu = _writhe(t + t0, k)
            ct, cu = ct + GRIP[0] * g, cu + GRIP[1] * g
            tt = t + t0
            for b, rot in _stand_arm(r, fr, k, where.get(k, REST_TIP[k]), ct, cu, writhe=(2 * math.pi * tt / 1.5, 2 * math.pi * tt / 1.2)).items():
                out[b] = ik.delta(rot, r.by_name[b].rot)
        return out
    ik.retrack(r, clip, fn)
    # a tick's key dropped where the curve through its neighbours passes within 1.5 degrees of it (slow stretches keyed
    # every other tick, fast ones every tick), so her rig stays near the size of the others'
    c = r.clips[clip]
    for k in arms:
        for i in range(N):
            keys = c.tracks.get((f"a{k}_{i}", "rot"))
            if not keys or len(keys) < 4:
                continue
            kept = [keys[0]]
            for j in range(1, len(keys) - 1):
                if j % 2 == 0 or max(abs(keys[j][1][x] - (keys[j - 1][1][x] + keys[j + 1][1][x]) / 2) for x in range(3)) > 1.5:
                    kept.append(keys[j])
            c.tracks[(f"a{k}_{i}", "rot")] = kept + [keys[-1]]


def _swell(r, clip, keys):
    """The mantle swelling (it draws breath to sing, it pumps to jet): [(seconds, size)] as a scale track."""
    r.clips[clip].scale("mantle", [(t, v, 1.0 + (v - 1.0) * 1.3, v) for t, v in keys])


# ---------------------------------------------------------------------------------------------------------------------
# Clips

def _lead(u, beat=0.0, lift=0.0, spread=0.0):
    """The front arms leading the choir at u (0..1 through a measure): each rises and falls out of step with the other,
    its tip curling as it falls."""
    out = {}
    for k in LEAD:
        s = SIDE[k]
        ph = 2 * math.pi * u + (0.0 if k == 0 else math.pi * 0.6)
        up = math.sin(ph)
        th0, a, b, yaw, side = LEAD_REST
        out[k] = (th0 - 6 * up - lift, a + 3 * up + beat, b + 0.8 * up, yaw + spread + 5 * math.sin(ph * 0.5), 4 * math.cos(ph) * s)
    return out


def _idle(r):
    """She breathes, her mantle swelling and swaying up behind her; she hums, her horn's mouth opening a little with
    each breath; her front arms lead a slow measure, rising and falling out of step; her standing arms writhe where they
    stand, and now and then one lifts its tip off the floor and sets it down again further round; her head tilts."""
    L, n = 6.0, 60
    _fresh()
    keys = []
    for kk in range(n + 1):
        u = kk / n
        w = 2 * math.pi * u
        br = math.sin(2 * w)
        shift = {k: _tip(k, up=6 * _wave(u, at, 0.16), out=2 * _wave(u, at, 0.16)) for k, at in ((2, 0.2), (5, 0.45), (7, 0.7), (4, 0.9))}
        keys.append((L * u, _pose(r, lift=0.8 * br, pitch=1.5 * math.sin(w), roll=2.0 * math.sin(w + 0.8), yaw=2.5 * math.sin(w + 2.0),
                                  head=(3 * math.sin(w + 1), 6 * math.sin(w * 1 + 0.4), 5 * math.sin(2 * w + 0.5)), mantle=(3 * br, 2 * math.sin(w), 0),
                                  sway=(4 * math.sin(2 * w - 0.8), 6 * math.sin(w - 0.6)), siphon=(-4 * br, 4 * math.sin(w)), bell=0.3 + 0.3 * br,
                                  free=_lead(2 * u), tips=shift,
                                  writhe=(2 * math.pi * L * u / 1.5, 2 * math.pi * L * u / 1.2), contact={k: _writhe(L * u, k)[0] for k in STAND}, curl={k: _writhe(L * u, k)[1] + 20 * _wave(u, at, 0.16) for k, at in ((2, 0.2), (5, 0.45), (7, 0.7), (4, 0.9), (3, 2), (6, 2))})))
    keyed(r, "idle", L, keys, loop=True)
    core.bake(r.clips["idle"])
    _swell(r, "idle", [(L * k / 12, 1.0 + 0.035 * (1 - math.cos(4 * math.pi * k / 12)) / 2) for k in range(13)])


GROUPS = ({2: 0.0, 5: 0.0, 6: 0.0, 3: 0.5, 4: 0.5, 7: 0.5})  # two threes of arms by turns
SWAY = {2: 0.0, 3: 0.33, 4: 0.17, 5: 0.5, 6: 0.67, 7: 0.83}  # walking, one arm after another round her


# where each arm's stride is centred (+ toward her front): her arms stand near their full reach, so the front ones
# step behind their rest and the back ones ahead of it, and none is ever pulled past what it can reach
GAIT_C = {2: -4.0, 3: -4.0, 4: 3.0, 5: 3.0, 6: 5.0, 7: 5.0}


def _gait(u, stride, beta, lift, phases, t, curl_up=24.0, centre=1.0):
    tips, curl = {}, {}
    st = stride * beta
    for k, ph in phases.items():
        q = (u - ph) % 1.0
        c = GAIT_C[k] * centre
        if q < beta:
            tips[k] = _tip(k, along=c + st / 2 - st * q / beta)
            curl[k] = _writhe(t, k)[1]
        else:
            f = (q - beta) / (1 - beta)
            tips[k] = _tip(k, up=lift * math.sin(math.pi * f), along=c - st / 2 + st * core.ease_io(f), out=2.0 * math.sin(math.pi * f))
            curl[k] = _writhe(t, k)[1] + curl_up * math.sin(math.pi * f)
    return tips, curl


def _locomotion(r):
    """Her walk: her six standing arms reach and pull one after another round her, each lifting its tip and curling it
    as it reaches, her body gliding and swaying above them, her front arms swaying up before her, her mantle swinging
    after. Running, her arms pull in two threes by turns, her body low and leaning on, her front arms swept back."""
    for name, L, stride, beta, lift, phases, run in (("walk", 1.6, 1.3, 0.7, 6.0, SWAY, False), ("run", 0.6, 1.5, 0.55, 5.0, GROUPS, True)):
        sp = stride * 16
        _fresh()
        keys = []
        n = 32
        for kk in range(n + 1):
            u = kk / n
            s = math.sin(2 * math.pi * u)
            s2 = math.sin(4 * math.pi * u)
            tips, curl = _gait(u, sp, beta, lift, phases, L * u, centre=0.6 if run else 1.0)
            lead = _lead(u, lift=-24 if run else 0, spread=10 if run else 0) if not run else \
                {k: (-30.0, 6.0 + 3 * s2, 1.5, _floor_yaw(k, 40, back=True) * 0.0 + 30.0, 3 * s * SIDE[k]) for k in LEAD}
            keys.append((L * u, _pose(r, lift=(-3.0 if run else 0.0) + (1.0 if run else 0.6) * s2, pitch=(10.0 if run else 2.0) + 2 * s2,
                                      roll=2.5 * s, yaw=2.0 * s, head=(-4 if run else 0, 4 * s, 0), mantle=((14 if run else 4) + 3 * s2, -3 * s, 0),
                                      sway=(6 * math.sin(4 * math.pi * u - 1.0), -6 * math.sin(2 * math.pi * u - 0.8)), siphon=(0, 0), bell=0.2,
                                      free=lead, tips=tips, curl=curl)))
        keyed(r, name, L, keys, loop=True)
        core.bake(r.clips[name])
    r.walk_stride, r.run_stride = 1.3, 1.5
    r.run_speed = (0.13, 0.3)
    for nm, sgn in (("turn_l", 1), ("turn_r", -1)):
        _fresh()
        keys = []
        n = 20
        A = 18.0 * DEG
        for kk in range(n + 1):
            u = kk / n
            tips, curl = {}, {}
            for j, ph in SWAY.items():
                q = (u - ph) % 1.0
                if q < 0.7:
                    a, up_ = sgn * A * (0.5 - q / 0.7), 0.0
                else:
                    f = (q - 0.7) / 0.3
                    a, up_ = sgn * A * (-0.5 + core.ease_io(f)), 5.0 * math.sin(math.pi * f)
                x, h, z = REST_TIP[j]
                tips[j] = (x * math.cos(a) + z * math.sin(a), h + up_, -x * math.sin(a) + z * math.cos(a))
                curl[j] = CURL + 20 * up_ / 5
            keys.append((1.0 * u, _pose(r, yaw=sgn * 3.0, roll=1.5 * math.sin(4 * math.pi * u), head=(0, sgn * 10, 0), mantle=(2, -sgn * 4, 0),
                                        sway=(0, -sgn * 6), free=_lead(u), tips=tips, curl=curl)))
        keyed(r, nm, 1.0, keys, loop=True)
        core.bake(r.clips[nm])


def _sunk(r, f=1.0, spin=0.0):
    """Sunk down onto her curled arms (f 0 to 1): her crown let down near the floor, her standing arms pulled in under
    her and curled tight, her front arms coiled before her, her mantle drooping forward over her head."""
    coil = {k: tuple(x + (y - x) * f for x, y in zip(LEAD_REST, (24.0, -20.0, -3.0, 6.0, 0.0))) for k in LEAD}  # rolled up on the floor
    return _pose(r, lift=-11.0 * f, pitch=2 * f, mantle=(52 * f, spin, 0), sway=(26 * f, 0), siphon=(10 * f, 0), head=(8 * f, 0, 0),
                 free=coil, tips={k: _tip(k, out=-7.0 * f) for k in STAND}, contact={k: CONTACT + 30 * f for k in STAND},
                 curl={k: CURL + 60 * f for k in STAND})


def _reactions(r):
    """Struck, she flinches: her crown drops and draws back, her mantle jerks forward, her front arms whip in and her
    arms' tips curl tight; from the side she leans away. Broken, she sinks down onto her curled arms, her mantle drooping
    over her, and lies there; she rises out of it, her front arms unfurling first."""
    _fresh()
    flinch = {k: (-46.0, 10.0, 2.4, 6.0, 0.0) for k in LEAD}
    hit = _pose(r, lift=-3, pitch=-4, shift=(0, 1.5), head=(10, 0, 0), mantle=(16, 0, 0), sway=(12, 0), free=flinch, curl={k: CURL + 30 for k in STAND})
    back = _pose(r, lift=0.5, pitch=1, mantle=(-4, 0, 0), sway=(-4, 0), free=_lead(0.1))
    keyed(r, "hurt", 0.55, [(0, _pose(r)), (0.08, hit), (0.3, back), (0.55, _pose(r))])
    for nm, sgn in (("hurt_l", 1), ("hurt_r", -1)):  # struck from her left she leans away to her right
        h = _pose(r, lift=-2, roll=-8 * sgn, yaw=-6 * sgn, shift=(-sgn * 1.5, 0), head=(4, -12 * sgn, 0), mantle=(6, 8 * sgn, -6 * sgn),
                  sway=(4, 10 * sgn), free={k: (-50.0, 12.0, 3.0, 10.0 - 10 * sgn * SIDE[k], 0.0) for k in LEAD}, curl={k: CURL + 20 for k in STAND})
        b = _pose(r, roll=2 * sgn, head=(0, 4 * sgn, 0), sway=(0, -4 * sgn), free=_lead(0.2))
        keyed(r, nm, 0.6, [(0, _pose(r)), (0.08, h), (0.32, b), (0.6, _pose(r))])
    keyed(r, "kneel", 1.1, [(0, _pose(r)), (0.3, _mix(_pose(r), _sunk(r, 0.5), 0.6)), (0.7, _sunk(r, 0.9)), (0.9, _sunk(r, 1.04)), (1.1, _sunk(r))])
    unfurl = _with(_sunk(r, 0.7), {})
    keyed(r, "rise", 1.3, [(0, _sunk(r)), (0.35, _pose(r, lift=-8, mantle=(26, 0, 0), sway=(10, 0), free={k: (-60.0, 4.0, 2.0, 12.0, 0.0) for k in LEAD},
                                                     tips={k: _tip(k, out=-5) for k in STAND}, curl={k: CURL + 40 for k in STAND})),
                           (0.8, _pose(r, lift=-2, mantle=(6, 0, 0), sway=(2, 0), free=_lead(0.25))), (1.05, _pose(r, lift=0.8, mantle=(-3, 0, 0), free=_lead(0.3))),
                           (1.3, _pose(r))])
    for nm in ("hurt", "hurt_l", "hurt_r", "kneel", "rise"):
        core.bake(r.clips[nm])
    _swell(r, "kneel", [(0, 1.0), (0.5, 0.97), (1.1, 0.94)])
    _swell(r, "rise", [(0, 0.94), (0.6, 1.04), (1.3, 1.0)])
    _death(r)


def _death(r):
    """Her last: she rears up, her front arms flung up and writhing; she shudders; she sinks, her arms sliding out from
    under her and spreading flat over the floor, her mantle slumping forward onto it; she breathes once, her mantle
    swelling and falling, an arm's tip curls, and she is still."""
    _fresh()
    up = _pose(r, lift=3, pitch=-6, head=(-10, 0, 0), mantle=(-14, 0, 0), sway=(-8, 0), siphon=(-20, 0), bell=1.0,
               free={k: (-88.0, 4.0, 4.0, 24.0, 6.0 * SIDE[k]) for k in LEAD}, curl={k: CURL + 30 for k in STAND})
    shud = lambda a: _pose(r, lift=1, pitch=-3, roll=4 * a, head=(-4, 6 * a, 0), mantle=(-8, 4 * a, 0), sway=(-4, 8 * a), bell=0.6,
                           free={k: (-70.0 + 10 * a * SIDE[k], 10.0, 3.0 - a, 18.0, 6.0 * a) for k in LEAD}, curl={k: CURL + 10 for k in STAND})
    def lie(fl=0.0):
        """Lying spread on the floor, every arm flat along it in a limp curve; fl: her last arm's tips curling up off it."""
        fr_ = {}
        for k in range(8):
            th = 2.4 - 4.0 * math.cos(ANGLE[k] * DEG)  # each arm sloping down onto the floor, against her crown's lean
            fr_[k] = (th, 1.3 - (fl if k in (0, 5) else 0.0), 0.0, 6.0 if k in LEAD else 4.0, 7.0 * (1 if k % 3 else -1))
        return _pose(r, lift=-14.5, pitch=4, head=(14, 0, 0), mantle=(80, 4, 6), sway=(40, 6), siphon=(20, 0), free=fr_)
    flat = lambda f: _mix(_pose(r, lift=0.5, pitch=-1, mantle=(-4, 0, 0), free=_lead(0.1)), lie(), f)
    keys = [(0, _pose(r)), (0.35, up), (0.7, shud(1)), (0.9, shud(-1)), (1.1, shud(0.6)), (1.7, flat(0.6)), (2.1, lie()), (2.25, flat(0.96)),
            (2.45, lie()), (3.3, lie(8)), (3.9, lie(2)), (4.6, lie())]
    keyed(r, "death", 4.6, _unwrap(keys))
    core.bake(r.clips["death"])
    _swell(r, "death", [(0, 1.0), (0.35, 1.08), (1.1, 1.02), (2.1, 0.94), (3.3, 1.04), (3.9, 0.9), (4.6, 0.88)])


# ---------------------------------------------------------------------------------------------------------------------
# Moves

NO_LAG = {"body": 0}


def _lag(**kw):
    out = {f"a{k}_{i}": 0 for k in STAND for i in range(N)}
    out.update(kw)
    return out


def _back(r, end, home=None):
    home = home or _pose(r)
    return [(0.45, _mix(end, home, 0.45)), (0.7, _mix(end, home, 0.82))]


def _arc(k, th0, a, b, yaw, side=0.0):
    return {k: (th0, a, b, yaw, side)}


def lash(r, k=0, mid="lash"):
    """A front arm drawn back up and across her body to her far side, her body turning after it; then whipped across in
    front of her low, a wave running down it to its tip, which cracks out past her other side (LIGHT). The arm's own lag
    down its length is the whip."""
    s = SIDE[k]
    o = 1 - k  # the other front arm
    cock = _pose(r, lift=1, yaw=-6 * s, roll=2 * s, head=(-2, -8 * s, 0), mantle=(-4, 6 * s, 0), sway=(-2, 8 * s), free={**_arc(k, -70, 6, 3, -40), **_arc(o, -36, 3, 1.5, 46)})
    wind = _pose(r, lift=3, pitch=-4, yaw=-13 * s, roll=4 * s, head=(-6, -16 * s, 0), mantle=(-10, 13 * s, 0), sway=(-6, 14 * s),
                 free={**_arc(k, -84, 14, 4.5, -76), **_arc(o, -100, 8, 3.0, 46)})
    # mid-swing: the arm flung out straight and low across her front, her body thrown after it, the other arm back
    swing = _pose(r, lift=-3, pitch=13, yaw=7 * s, roll=-2 * s, head=(8, 6 * s, 0), mantle=(16, -6 * s, 0), sway=(14, -8 * s),
                  free={**_arc(k, -16, -5, 0.2, -12), **_arc(o, -104, 2, 1.0, 50)})
    hit = _pose(r, lift=-1.5, pitch=6, yaw=14 * s, roll=-3 * s, head=(4, 12 * s, 0), mantle=(8, -10 * s, 0), sway=(6, -12 * s),
                free={**_arc(k, 6, -2, 0.5, 40), **_arc(o, -84, 4, 2.0, 40)})
    held = _pose(r, lift=-2, pitch=7, yaw=17 * s, roll=-4 * s, head=(5, 14 * s, 0), mantle=(9, -12 * s, 0), sway=(8, -14 * s),
                 free={**_arc(k, 10, -3, 0.2, 66), **_arc(o, -70, 4, 2.6, 30)})
    through = _pose(r, lift=-1, pitch=4, yaw=20 * s, roll=-4 * s, head=(3, 16 * s, 0), mantle=(6, -14 * s, 0), sway=(4, -16 * s),
                    free={**_arc(k, -14, 3, 1.6, 96), **_arc(o, -64, 4, 2.4, 22)})
    back = _pose(r, lift=0, pitch=1, yaw=4 * s, head=(0, 4 * s, 0), sway=(2, -4 * s), free={**_arc(k, -40, 6, 2.2, 40), **_arc(o, *LEAD_REST)})
    return Move(mid, "LIGHT", 10, 4, 14, through=through, track=8.0, rng=(0, 3.4), kinds=("combo", "near"), damage=1.0, knock=0.6,
                hit=[(f"a{k}_6", (0, 0, -5), 3.0), (f"a{k}_5", (0, 0, -5), 3.0), (f"a{k}_4", (0, 0, -6), 3.0)], plant=(),
                stages={"windup": [(0.3, cock), (1.0, wind)], "active": [(0.25, swing), (0.5, hit), (1.0, held)], "recover": [(0.5, back)]}, lag=_lag())


def breaker(r):
    """The Breaker (HEAVY): she rises tall and leans back, both front arms raised high up over her and curled back like a
    wave about to break; holds; and brings them crashing down together on the floor before her."""
    gather = _pose(r, lift=-4, pitch=6, head=(8, 0, 0), mantle=(12, 0, 0), sway=(8, 0), free={k: (8.0, 2.0, 0.6, 56.0, 0.0) for k in LEAD},
                   curl={k: CURL + 14 for k in STAND})
    wide = _pose(r, lift=1, pitch=-3, head=(-4, 0, 0), mantle=(-6, 0, 0), sway=(-4, 0), siphon=(-6, 0), bell=0.3,
                 free={k: (-46.0, 1.0, 0.4, 96.0, 0.0) for k in LEAD})  # scooped out wide round her sides on the way up
    rise = _pose(r, lift=4, pitch=-8, head=(-10, 0, 0), mantle=(-16, 0, 0), sway=(-10, 0), siphon=(-14, 0), bell=0.6,
                 free={k: (-104.0, -2.0, -2.2, 12.0, 0.0) for k in LEAD})
    crash = _pose(r, lift=-3, pitch=10, head=(10, 0, 0), mantle=(14, 0, 0), sway=(10, 0), free={k: (30.0, -4.0, -0.6, 6.0, 0.0) for k in LEAD},
                  curl={k: CURL - 10 for k in STAND})
    held = _pose(r, lift=-3.5, pitch=11, head=(12, 0, 0), mantle=(16, 0, 0), sway=(14, 0), free={k: (34.0, -5.0, -0.5, 8.0, 0.0) for k in LEAD},
                 curl={k: CURL - 12 for k in STAND})
    through = _pose(r, lift=-2.5, pitch=9, head=(9, 0, 0), mantle=(12, 0, 0), sway=(10, 0), free={k: (28.0, -3.0, -0.2, 10.0, 0.0) for k in LEAD})
    return Move("breaker", "HEAVY", 16, 6, 20, through=through, title="The Breaker", hold=6, track=4.0, rng=(0, 4.0), kinds=("area", "near"), damage=1.3,
                knock=1.2, disc=2.6, hit=[(f"a{k}_6", (0, 0, -5), 3.5) for k in LEAD] + [(f"a{k}_5", (0, 0, -5), 3.5) for k in LEAD], plant=(),
                stages={"windup": [(0.3, gather), (0.62, wide), (1.0, rise)], "active": [(0.35, crash), (1.0, held)], "recover": [(0.5, _mix(through, _pose(r), 0.5))]},
                lag=_lag())


SWEEPER = 6  # her back left arm


def sweep(r):
    """For whoever is behind her (LIGHT): she leans away and lifts a back arm off the floor, drawn round to her side;
    then sweeps it low across the floor behind her and out the other side."""
    k = SWEEPER
    stand = [j for j in STAND if j != k]
    wind = _pose(r, lift=2, pitch=-3, yaw=14, roll=5, head=(-4, 28, 0), mantle=(-12, -14, 0), sway=(-8, -16), free={k: (-30.0, 8.0, 2.0, -40.0, 0.0),
                 **{j: (-90.0, 6.0, 3.0, 30.0, 0.0) for j in LEAD}})
    hit = _pose(r, lift=-1, pitch=1, yaw=-10, roll=-3, head=(0, 12, 0), mantle=(4, 10, 0), sway=(4, 12), free={k: (40.0, -6.0, 0.0, 80.0, 0.0)})
    held = _pose(r, lift=-1.5, pitch=1, yaw=-12, roll=-3.5, head=(0, 10, 0), mantle=(5, 12, 0), sway=(6, 14), free={k: (42.0, -6.0, 0.0, 100.0, 0.0)})
    back = _pose(r, yaw=-3, head=(0, 4, 0), sway=(2, 4), free={k: (20.0, -2.0, 1.0, 60.0, 0.0)})
    mv = Move("sweep", "LIGHT", 10, 4, 14, through=held, track=0.0, rng=(0, 3.6), kinds=("behind",), damage=0.9, knock=1.0,
              hit=[(f"a{k}_6", (0, 0, -5), 3.5), (f"a{k}_5", (0, 0, -5), 3.5), (f"a{k}_4", (0, 0, -6), 3.5)], plant=(),
              stages={"windup": [(0.3, _mix(_pose(r), wind, 0.5)), (1.0, wind)], "active": [(0.5, hit), (1.0, held)], "recover": [(0.5, back)]},
              lag=_lag(**{f"a{SWEEPER}_{i}": core.BREAK * i for i in range(N)}))
    mv.stand = stand
    return mv


JET = 7.0  # blocks she shoots along the floor


def jet(r):
    """The jet (HEAVY, a gap closer): she draws her arms in under her and crouches low, her mantle swelling, her horn
    turned back under her; she shoots along the floor at you, her arms streaming out behind; and spreads every arm wide
    and down over you as she lands."""
    pull = {k: _tip(k, out=-8) for k in STAND}
    crouch = _pose(r, lift=-8, pitch=8, head=(8, 0, 0), mantle=(24, 0, 0), sway=(10, 0), siphon=(70, 0), free={k: (-20.0, 14.0, 3.0, 0.0, 0.0) for k in LEAD},
                   tips=pull, contact={k: CONTACT + 26 for k in STAND}, curl={k: CURL + 40 for k in STAND})
    trail = {k: (-4.0, 1.0, 0.3, _floor_yaw(k, 14 + 6 * (k // 2), back=True), 0.0) for k in range(8)}
    def fly(f):
        rip = {k: (v[0], v[1], v[2], v[3], v[4]) for k, v in trail.items()}
        fr_ = {}
        for k, (th0, a, b, yaw, side) in rip.items():
            fr_[k] = (th0 + 4 * math.sin(2 * math.pi * f * 1.5 + 0.7 * k), a, b, yaw, side)
        return _pose(r, lift=-6 + 3 * f, pitch=-4 + 2 * f, roll=3 * math.sin(2 * math.pi * f), head=(-6, 0, 0), mantle=(-24 + 6 * f, 0, 0),
                     sway=(-12 + 8 * f, 4 * math.sin(2 * math.pi * f)), siphon=(90, 0), free=fr_)
    spread = {k: (34.0, -4.0, 0.4, _floor_yaw(k, 16 + 22 * (k // 2)), 0.0) for k in range(8)}
    land = _pose(r, lift=-6, pitch=10, head=(10, 0, 0), mantle=(20, 0, 0), sway=(16, 0), siphon=(30, 0), free=spread)
    held = _pose(r, lift=-6.5, pitch=11, head=(12, 0, 0), mantle=(22, 0, 0), sway=(18, 0), siphon=(30, 0),
                 free={k: (36.0, -5.0, 0.4, _floor_yaw(k, 18 + 22 * (k // 2)), 0.0) for k in range(8)})
    gather = _pose(r, lift=-4, pitch=6, head=(6, 0, 0), mantle=(12, 0, 0), sway=(8, 0), free=_lead(0.1, lift=20),
                   tips={k: _tip(k, out=-3) for k in STAND}, curl={k: CURL + 20 for k in STAND})
    mv = Move("jet", "HEAVY", 16, 10, 20, through=held, title="", hold=6, track=6.0, lock=10, advance=JET, rng=(5, 14), kinds=("gap",), damage=1.2, knock=1.2,
              hit=[("body", (0, 0, -4), 26.0)], plant=(),
              stages={"windup": [(0.3, _mix(_pose(r), crouch, 0.4)), (1.0, crouch)], "active": [(0.3, fly(0.3)), (0.7, fly(0.7)), (0.85, land), (1.0, held)],
                      "recover": [(0.45, gather), (0.7, _mix(gather, _pose(r), 0.6))]}, lag=_lag())
    W, A, R_ = mv.seconds("windup"), mv.seconds("active"), mv.seconds("recover")
    mv.swell = [(0, 1.0), (0.5 * W, 1.06), (W, 1.16), (W + 0.15 * A, 0.86), (W + A, 0.94), (W + A + R_, 1.0)]
    mv.stand = []
    return mv


def hymn(r):
    """The Hymn of Salt: she rises tall, her horn lifted, and beats time with both front arms, four beats, and with each
    beat her horn's mouth flares and her mantle pulses as she sings a bolt of water."""
    tall = lambda: _pose(r, lift=3, pitch=-4, head=(-8, 0, 0), mantle=(-8, 0, 0), sway=(-4, 0), siphon=(-26, 0), bell=0.5, free=_lead(0.0, lift=10, spread=4))

    def beat(n, down):
        u = n / 4
        return _pose(r, lift=3 - 1.5 * down, pitch=-4 + 3 * down, roll=2 * (1 if n % 2 else -1) * down, head=(-8 + 4 * down, 0, 3 * (1 if n % 2 else -1) * down),
                     mantle=(-8 + 6 * down, 0, 0), sway=(-4 + 8 * down, 3 * (1 if n % 2 else -1)), siphon=(-26 + 6 * down, 0), bell=0.4 + 0.6 * down,
                     free={k: (-74.0 + 46 * down, 2.0 + 6 * down, 2.6 + 0.8 * down, 18.0 + 6 * down, 0.0) for k in LEAD})
    act = []
    for n in range(4):
        act += [((n + 0.35) / 4, beat(n, 1.0)), ((n + 0.85) / 4, beat(n, 0.15))]
    mv = Move("hymn", "NONE", 14, 24, 16, through=beat(3, 0.3), title="Hymn of Salt", track=4.0, ability="Hymn of Salt", rng=(4, 14), kinds=("ranged",),
              damage=0, knock=0, plant=(), stages={"windup": [(0.4, _mix(_pose(r), tall(), 0.5)), (1.0, tall())], "active": act,
                                                    "recover": _back(r, beat(3, 0.3))}, lag=_lag())
    W, A = mv.seconds("windup"), mv.seconds("active")
    mv.swell = [(0, 1.0), (W, 1.08)] + [(W + (n + 0.35) / 4 * A, 1.0) for n in range(4)] + [(W + (n + 0.75) / 4 * A, 1.08) for n in range(4)] + \
               [(W + A + mv.seconds("recover"), 1.0)]
    mv.swell.sort()
    mv.stand = STAND
    return mv


def refrain(r):
    """The Refrain: she wraps her front arms round herself and sways, humming, her horn's mouth barely open, and heals."""
    wrap = lambda a: _pose(r, lift=-2, roll=4 * a, yaw=3 * a, head=(6, 6 * a, 4 * a), mantle=(8, 4 * a, -4 * a), sway=(6, 10 * a), siphon=(8, 0), bell=0.2,
                           free={k: (-30.0 + 4 * a * SIDE[k], 3.0, 0.5, -55.0 + 6 * a * SIDE[k], -SIDE[k] * (14.0 + 2 * a * SIDE[k])) for k in LEAD})
    act = [(0.2, wrap(1)), (0.45, wrap(-1)), (0.7, wrap(1)), (1.0, wrap(-0.6))]
    mv = Move("refrain", "NONE", 12, 24, 14, through=wrap(-0.4), title="Refrain", track=0.0, ability="Refrain", rng=(0, 99), kinds=("heal",), damage=0,
              knock=0, plant=(), stages={"windup": [(0.5, _mix(_pose(r), wrap(0), 0.6)), (1.0, wrap(0))], "active": act, "recover": _back(r, wrap(-0.4))},
              lag=_lag())
    W, A = mv.seconds("windup"), mv.seconds("active")
    mv.swell = [(0, 1.0), (W, 1.05), (W + 0.5 * A, 1.1), (W + A, 1.04), (W + A + mv.seconds("recover"), 1.0)]
    mv.stand = STAND
    return mv


def choir(r):
    """The Choir: she lifts her horn and sings one long note, her mantle swelling and holding, while her front arms reach
    down to the floor and sweep up from it, twice, beckoning the drowned up out of it."""
    note = lambda reach, a=0.0: _pose(r, lift=2 - 3 * reach, pitch=-6 + 10 * reach, roll=2 * a, head=(-12 + 8 * reach, 0, 0), mantle=(-10 + 6 * reach, 0, 0),
                                      sway=(-6 + 4 * reach, 4 * a), siphon=(-40 + 10 * reach, 0), bell=1.0,
                                      free={k: (-70.0 + 110 * reach, 4.0 - 2 * reach, 2.6 - 2.4 * reach, 20.0 + 10 * reach, 0.0) for k in LEAD})
    act = [(0.2, note(1, 1)), (0.45, note(0.1, -1)), (0.7, note(1, 1)), (1.0, note(0.1, -0.5))]
    mv = Move("choir", "NONE", 12, 28, 14, through=note(0.2), title="The Choir", track=0.0, ability="The Choir", rng=(0, 99), kinds=("summon",), damage=0,
              knock=0, plant=(), stages={"windup": [(0.5, _mix(_pose(r), note(0), 0.5)), (1.0, note(0))], "active": act, "recover": _back(r, note(0.2))},
              lag=_lag())
    W, A = mv.seconds("windup"), mv.seconds("active")
    mv.swell = [(0, 1.0), (W, 1.14), (W + A, 1.1), (W + A + mv.seconds("recover"), 1.0)]
    mv.stand = STAND
    return mv


def undertow(r):
    """Undertow: both front arms reach far out at you, straight and low; and she hauls back on them hand over hand, one
    pulling in while the other reaches out again, twice, leaning back into it."""
    reach = lambda kk: (6.0, -1.0, 0.0, 6.0, 0.0)
    pull = lambda kk: (-34.0, 14.0, 3.0, 30.0, 0.0)
    out_ = _pose(r, lift=-1, pitch=8, head=(6, 0, 0), mantle=(10, 0, 0), sway=(6, 0), free={k: reach(k) for k in LEAD})

    def haul(a):  # a +1: the left pulling in, the right reaching out again
        f = (a + 1) / 2
        fr_ = {0: tuple(x + (y - x) * f for x, y in zip(reach(0), pull(0))), 1: tuple(x + (y - x) * (1 - f) for x, y in zip(reach(1), pull(1)))}
        return _pose(r, lift=0, pitch=-4, yaw=4 * a, roll=-2 * a, head=(-4, 6 * a, 0), mantle=(-8, -4 * a, 0), sway=(-6, -6 * a), free=fr_)
    act = [(0.2, haul(1)), (0.45, haul(-1)), (0.7, haul(1)), (1.0, haul(-1))]
    mv = Move("undertow", "NONE", 12, 20, 14, through=_mix(haul(-1), _pose(r), 0.3), title="Undertow", track=6.0, ability="Undertow", rng=(4, 12),
              kinds=("ranged",), damage=0, knock=0, plant=(), stages={"windup": [(0.4, _mix(_pose(r), out_, 0.5)), (1.0, out_)], "active": act,
                                                                       "recover": _back(r, _mix(haul(-1), _pose(r), 0.3))}, lag=_lag())
    mv.stand = STAND
    return mv


TIDE = (0, 1, 2, 3)  # the arms she raises for the Rising Tide; she stands on the back four


def tide(r):
    """Rising Tide (PERILOUS, the signature): her four front arms rise up round her one after another, her body rising
    tall on the back four and her mantle swelling and leaning back; they hold high, trembling; and she brings all four
    down on the floor at once, dropping with them."""
    def up(f, tr=0.0):
        fr_ = {}
        for i, k in enumerate(TIDE):
            g = _ease(f * 1.6 - i * 0.2)
            th, a, b, yaw = (-74.0, 2.0, 2.6, 18.0) if k in LEAD else (20.0, -2.0, 0.0, 10.0)
            rip = lambda j, ph=2 * math.pi * 2.5 * f + 1.3 * k: (5.0 * math.sin(ph - 0.9 * j), 3.0 * math.cos(ph - 0.9 * j))  # a ripple up it
            fr_[k] = (th + (-100.0 - th) * g + tr * (1 if i % 2 else -1), a + (-1.0 - a) * g, b + (-2.0 - b) * g, yaw + (28.0 + 12 * (k // 2) - yaw) * g, 0.0, rip)
        return _pose(r, lift=4 * f, pitch=-8 * f, head=(-12 * f, 0, 0), mantle=(-14 * f, 0, 0), sway=(-8 * f, 0), siphon=(-30 * f, 0), bell=0.8 * f, free=fr_,
                     tips={k: _tip(k, along=-3 * f) for k in (4, 5, 6, 7)})
    wind = [(0.2, up(0.25)), (0.4, up(0.5)), (0.6, up(0.75)), (0.8, up(1.0, 3)), (0.9, up(1.0, -3)), (1.0, up(1.0, 2))]
    down = {k: (40.0, -5.0, -0.3, 18.0 + 14 * (k // 2), 0.0) for k in TIDE}
    slam = _pose(r, lift=-5, pitch=10, head=(12, 0, 0), mantle=(18, 0, 0), sway=(16, 0), siphon=(20, 0), free=down,
                 tips={k: _tip(k, along=-1) for k in (4, 5, 6, 7)}, curl={k: CURL - 14 for k in (4, 5, 6, 7)})
    held = _pose(r, lift=-5.5, pitch=11, head=(13, 0, 0), mantle=(19, 0, 0), sway=(18, 0), siphon=(20, 0),
                 free={k: (42.0, -5.5, -0.3, 20.0 + 14 * (k // 2), 0.0) for k in TIDE}, curl={k: CURL - 14 for k in (4, 5, 6, 7)})
    end = _pose(r, lift=-4, pitch=8, head=(10, 0, 0), mantle=(14, 0, 0), sway=(12, 0), free={k: (38.0, -4.0, 0.0, 18.0 + 14 * (k // 2), 0.0) for k in TIDE})
    mv = Move("tide", "PERILOUS", 26, 10, 22, through=_mix(end, _pose(r), 0.25), title="Rising Tide", signature=True, track=4.0, ability="Rising Tide",
              rng=(0, 9), kinds=("area", "ranged"), damage=0, knock=0, disc=3.0, plant=(),
              stages={"windup": wind, "active": [(0.3, slam), (0.6, held), (1.0, end)], "recover": _back(r, _mix(end, _pose(r), 0.25))}, lag=_lag())
    W, A = mv.seconds("windup"), mv.seconds("active")
    mv.swell = [(0, 1.0), (W, 1.15), (W + 0.3 * A, 0.95), (W + A + mv.seconds("recover"), 1.0)]
    mv.stand = [4, 5, 6, 7]
    return mv


def wake(r):
    """Her intro, once she has risen: she draws herself up tall, takes a breath, her mantle swelling, and lifts her front
    arms wide like a conductor before the first note; she sings it through her lifted horn, her arms spreading; and
    brings them in."""
    breath = _pose(r, lift=2, pitch=-3, head=(-6, 0, 0), mantle=(-6, 0, 0), sway=(-4, 0), siphon=(-10, 0), bell=0.2, free=_lead(0.0, lift=6, spread=10))
    poised = _pose(r, lift=3.5, pitch=-6, head=(-10, 0, 0), mantle=(-12, 0, 0), sway=(-6, 0), siphon=(-30, 0), bell=0.4,
                   free={k: (-96.0, 0.0, 2.0, 34.0, 0.0) for k in LEAD})
    sing = lambda a: _pose(r, lift=3 - a, pitch=-6 + 2 * a, roll=2 * a, head=(-12, 4 * a, 2 * a), mantle=(-12 + 4 * a, 2 * a, 0), sway=(-8 + 4 * a, 6 * a),
                           siphon=(-36, 0), bell=1.0, free={k: (-60.0 + 4 * a * SIDE[k], 6.0, 2.6, 50.0 + 6 * a, 0.0) for k in LEAD})
    mv = Move("wake", "NONE", 14, 30, 14, through=_mix(sing(0), _pose(r), 0.4), track=0.0, rng=(0, 99), kinds=("intro",), damage=0, knock=0, plant=(),
              stages={"windup": [(0.4, breath), (1.0, poised)], "active": [(0.15, sing(0)), (0.4, sing(1)), (0.65, sing(-1)), (0.85, sing(0.5)), (1.0, _lead_pose(r))],
                      "recover": _back(r, _mix(sing(0), _pose(r), 0.4))}, lag=_lag())
    W, A = mv.seconds("windup"), mv.seconds("active")
    mv.swell = [(0, 1.0), (W, 1.14), (W + 0.85 * A, 1.06), (W + A + mv.seconds("recover"), 1.0)]
    mv.stand = STAND
    return mv


def _lead_pose(r):
    return _pose(r, lift=1, head=(-2, 0, 0), free=_lead(0.25, spread=6))


# the ground (SPEC §35.10): no arm's tip goes into the floor; a free arm curls its last segments up to keep out of it
GROUND = [([f"a{k}_5", f"a{k}_6"], [(f"a{k}_4", 0, 40, -1), (f"a{k}_3", 0, 30, -1)]) for k in range(8)]
STANDS = {"lash": STAND, "lash_b": STAND, "breaker": STAND}


def clips(r):
    _idle(r)
    _locomotion(r)
    _reactions(r)
    mv = []
    for f, a in ((lash, (0, "lash")), (lash, (1, "lash_b")), (breaker, ()), (sweep, ()), (jet, ()), (hymn, ()), (refrain, ()), (choir, ()),
                 (undertow, ()), (tide, ()), (wake, ())):
        _fresh()
        mv.append(f(r, *a))
    for m in mv:
        stage_clips(r, m)
        stand = getattr(m, "stand", STANDS.get(m.id, STAND))
        if stand:
            w_, a_, r_ = m.seconds("windup") + m.hold * core.TICK, m.seconds("active"), m.seconds("recover")
            grip = (lambda t, w_=w_, a_=a_, r_=r_: _brace(t, w_, a_, r_)) if m.cls != "NONE" else None
            _plant(r, core.clip_name(m), stand, grip=grip)
        if getattr(m, "swell", None):
            _swell(r, core.clip_name(m), m.swell)
    everyday = [n for n in r.clips if n != "death"]
    core.ground_pass(r, GROUND, clips=everyday)
    # the arms off the floor in a move (her front arms, or all of them in the jet) bring their whole length down onto
    # it, never into it: their middles and tips guarded, lifted at the joints above them
    for m in mv:
        stand = getattr(m, "stand", STANDS.get(m.id, STAND))
        free = [k for k in range(8) if k not in stand]
        core.ground_pass(r, [([f"a{k}_{j}" for j in range(3, N)], [(f"a{k}_2", 0, 30, -1), (f"a{k}_1", 0, 30, -1), (f"a{k}_0", 0, 20, -1)])
                             for k in free], clips=[core.clip_name(m)])
    return mv


# Her sounds (SPEC §35.8): the squid's voice and its squirt, an allay's sung note and amethyst ringing for her singing,
# the elder guardian's moan and the conduit for her hymns, splashes for her blows; chosen on paper, not heard in game.
SOUNDS = {
    "tell_light": [("entity.squid.ambient", 1.0, 0.6), ("block.amethyst_block.chime", 0.6, 0.8)],
    "tell_heavy": [("entity.squid.squirt", 1.0, 0.6), ("entity.elder_guardian.ambient", 0.5, 1.2)],
    "tell_perilous": [("entity.elder_guardian.curse", 0.8, 0.8), ("block.conduit.activate", 1.0, 0.7), ("entity.allay.ambient_without_item", 1.0, 0.5)],
    "swing_light": [("entity.player.attack.sweep", 0.8, 1.0), ("entity.axolotl.splash", 0.8, 0.8)],
    "swing_heavy": [("entity.player.attack.knockback", 0.8, 0.7), ("entity.squid.squirt", 1.4, 0.5), ("entity.dolphin.splash", 1.2, 0.6)],
    "cast": [("entity.allay.ambient_without_item", 1.2, 0.6), ("block.amethyst_block.resonate", 1.0, 0.7), ("block.bubble_column.upwards_ambient", 0.8, 1.0)],
    "impact": [("entity.player.splash.high_speed", 1.0, 0.7), ("entity.generic.splash", 1.0, 0.8)],
    "roar": [("entity.allay.ambient_without_item", 1.4, 0.5), ("block.conduit.ambient", 1.0, 0.6)],
    "step": [("block.wet_sponge.place", 0.4, 1.2), ("entity.squid.squirt", 0.2, 1.6)],
}
MOVE_SOUNDS = {"hymn": {"swing": "cast"}, "refrain": {"swing": "cast", "impact": ""}, "choir": {"swing": "roar", "impact": ""},
               "undertow": {"swing": "cast", "impact": ""}, "tide": {"swing": "roar"}, "wake": {"swing": "roar"}}

FIGHT = {
    "intro": {"pose": "kneel", "move": "wake"},  # she waits sunk down on her curled arms, and rises
    "phases": [
        {"pool": [
            {"move": "lash", "weight": 3, "when": "near", "then": [["lash_b"], ["lash_b", "breaker"]]},
            {"move": "breaker", "weight": 2, "when": "near"},
            {"move": "hymn", "weight": 3, "when": "far"},
            {"move": "jet", "weight": 2, "when": "far", "then": [["lash"]]},
            {"move": "refrain", "weight": 1},
            {"move": "sweep", "weight": 3, "when": "behind"},
        ]},
        # the water rises with the second phase, as in DeepBestiary: the choir, the tide and the undertow
        {"transition": "choir", "pool": [
            {"move": "lash", "weight": 3, "when": "near", "then": [["lash_b"], ["lash_b", "breaker"]]},
            {"move": "breaker", "weight": 2, "when": "near"},
            {"move": "hymn", "weight": 2, "when": "far"},
            {"move": "undertow", "weight": 2, "when": "far", "then": [["lash"]]},
            {"move": "jet", "weight": 1, "when": "far"},
            {"move": "tide", "weight": 2},
            {"move": "choir", "weight": 1},
            {"move": "refrain", "weight": 1},
            {"move": "sweep", "weight": 3, "when": "behind"},
        ]},
    ],
    "enrage": {"below": 0.15, "speed": 1.2},
}


def design():
    r = body()
    r.pack()
    REST_GUESS.clear()
    REST_GUESS.update(r.rest_guess)
    _fresh()
    mv = clips(r)
    return {"rig": r, "plan": None, "moves": mv, "fight": FIGHT, "paint": paint, "scale": SCALE, "half": 1.0, "sounds": SOUNDS,
            "move_sounds": MOVE_SOUNDS}
