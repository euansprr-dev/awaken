"""The Thousand Legs (SPEC §35.11, docs/bosses/ROLLOUT.md): a Nightmare, a Crawler, the fear of what lives in the walls.
The House Centipede, picked 2026-10-30: "You can hear them in the walls."

A house centipede as long as a cart, low and fast. Its body is a short row of dusty grey-yellow plates with three dark
violet stripes down its back, and out from under it fan ten pairs of long thin legs, banded pale and violet, each bowed
up to a knee over its back and down to a fine claw well out from it, longer toward the back, so the whole of it is a
ring of legs round a thin body. Its last pair is the longest of all and held up behind it like a second pair of
feelers, so you can't tell which end is its head; its real feelers are as long again, whipping and tasting the air.
Its head is small and flat, with two big dark eyes of many facets and a pair of curved poison claws under its front.

Moves (SPEC §35.11): the Bite (LIGHT: it draws its head up and back with its poison claws spread, then plunges, the
claws snapping shut on you and its front legs slamming down); the Lasso (LIGHT, after the Bite: it throws its front
legs over you and folds them back to its jaws); the Drop (HEAVY, its gap closer: it rears its front up high with its
legs spread, holds, and springs, coming down on you with every leg splayed); Web (it rears and flings its front legs at
you, and the threads fly from them); the Lash (for whoever is behind it: its back end swings round and its long back
legs whip across); Hatchlings (it presses itself flat and shudders, and its young pour out from under it); The Walls
Open (into the second phase: it rears up with its front legs spread like a crown, trembles, and drops flat as its young
pour out); and A Thousand Legs (PERILOUS, the signature: it rears up over you with its front legs fanned out, comes
down on you, and its legs hammer the floor all round you in a ripple). It runs with a wave of legs passing down each
side from its back to its front. It dies thrashing, then curls up on the floor, its legs folding in one pair after
another.
"""
import math

from kit import core, ik
from kit.core import DEG, Move, keyed, stage_clips
import fauna_rig as R
from fauna_rig import Ramp, SIDES

ID = "nightmare_arthropod"
SCALE = 2.6

# The trunk is six plates in a row, t0 behind the head to t5 at the back; t3, in the middle, is the root, so its front
# can rear up off the floor and its back end swing. Kit bodies are drawn at true size (16 pixels a block): its middle
# is 1.1 blocks up, its plates 0.9 blocks wide, 3.5 blocks from its head to its back end; the knees stand 1.4 to 2.1
# blocks off the floor, its claws stand 4.8 blocks apart across it and 5.2 from the front ones to the back, and each
# feeler is 3 blocks long. Its box is 3.9 blocks square and 2.2 high: its ring of legs, not its feelers. Value story: the plates are the lightest thing on it, cut by its dark stripes; the legs are banded
# light and dark; the eyes are the darkest.
U = 1.75  # the poses' pixel offsets were set on a body this much smaller: they are scaled by it
BODY = 18.0  # the trunk's middle, pixels above the floor
SEG = 7  # a plate's length
HIPX, HIPY = 6.0, 2.0  # each hip out from the middle and down from it
NW = 10  # walking pairs, front to back (its eleventh pair, its back legs, is held up behind it)
NL = 2 * NW  # legs 2i (its left, +x) and 2i + 1 (its right) are pair i
SEG_OF = ("t0", "t0", "t1", "t1", "t2", "t2", "t3", "t3", "t4", "t4")
HIPZ = (-5.0, -1.8, -5.0, -1.8, -5.0, -1.8, -1.75, 1.75, 1.8, 5.0)  # each pair's hip along its plate
ANG = tuple(52.0 - 12.2 * i for i in range(NW))  # each pair's way out, degrees toward the front from straight out
THIGH = tuple(15 + round(1.0 * i) for i in range(NW))
SHIN = tuple(23 + round(2.0 * i) for i in range(NW))
REACH = tuple(27.0 + 2.35 * i for i in range(NW))  # each claw at rest, out from its middle
FRONT_W = {"t2": 0.3, "t1": 0.35, "t0": 0.35}  # how a rear or a bend shares out along the front
BACK_W = {"t4": 0.5, "t5": 0.5}
ANT, TL = 16, 18  # the feelers' and the back legs' links


def _side(k):
    return 1 if k % 2 == 0 else -1


def _dir(k):
    a = ANG[k // 2] * DEG
    return (_side(k) * math.cos(a), -math.sin(a))  # (x, z), front -z


def _hip_yaw(k):
    """Each hip turned to face down its leg, so the thigh's turn in it stays far from the Euler lock at y = 90."""
    a = ANG[k // 2] * DEG
    return math.degrees(math.atan2(-_side(k) * math.cos(a), math.sin(a)))


def _seg_z(name):
    """Each plate's pivot along the trunk at rest."""
    return {"t3": 0.0, "t2": -3.5, "t1": -10.5, "t0": -17.5, "t4": 3.5, "t5": 10.5}[name]


def _hip_world(k):
    i = k // 2
    return (_side(k) * HIPX, BODY - HIPY, _seg_z(SEG_OF[i]) + HIPZ[i])


REST_FOOT = []
for _k in range(NL):
    _hx, _hy, _hz = _hip_world(_k)
    _dx, _dz = _dir(_k)
    REST_FOOT.append((_dx * REACH[_k // 2], 0.5, _hz + _dz * (REACH[_k // 2] - HIPX)))
POLE = [(_dir(k)[0] * 0.45, -1.0, _dir(k)[1] * 0.45) for k in range(NL)]  # the knees ride up and out


# ---------------------------------------------------------------------------------------------------------------------
# The legs' two-bone reach: each leg is a hip, a thigh and a shin, its claw the shin's far end (no bone of its own)

def _two_bone(r, fr, k, target, pole):
    up, lo = r.by_name[f"thigh{k}"], r.by_name[f"shin{k}"]
    Mp, Tp = fr[up.parent.name]
    p0 = ik.v(Tp, R._mv(Mp, up.pivot))
    a, b = lo.pivot[1], SHIN[k // 2]
    d = ik.sub(target, p0)
    L = max(abs(a - b) + 1e-3, min(a + b - 1e-3, math.sqrt(ik.dot(d, d))))
    u = ik.unit(d)
    n = ik.unit(ik.v(pole, u, -ik.dot(pole, u)))
    ca = (a * a + L * L - b * b) / (2 * a * L)
    sa = math.sqrt(max(0.0, 1 - ca * ca))
    joint = ik.v(ik.v(p0, u, a * ca), n, a * sa)
    ua = ik.unit(ik.sub(joint, p0))
    fd = ik.unit(ik.sub(ik.v(p0, u, L), joint))
    perp = ik.v(fd, ua, -ik.dot(fd, ua))
    z = ik.unit(perp) if ik.dot(perp, perp) > 1e-8 else n
    z = tuple(-c for c in z)
    x = ik.cross(ua, z)
    M = tuple((x[i], ua[i], z[i]) for i in range(3))
    return core._euler(R._mm(core._t(Mp), M)), math.atan2(ik.dot(fd, z), ik.dot(fd, ua))


def _limb(r, fr, k, target, pole):
    (ax, ay, az), kn = _two_bone(r, fr, k, target, pole)
    up, lo = r.by_name[f"thigh{k}"], r.by_name[f"shin{k}"]
    return {f"thigh{k}": ik.delta((ax / DEG, ay / DEG, az / DEG), up.rot),
            f"shin{k}": ((kn / DEG - lo.rot[0] + 180) % 360 - 180, -lo.rot[1], -lo.rot[2])}


def _bone(parent, name, x, up, z, rot=(0, 0, 0)):
    return parent.bone(name, (x, -up, z), rot)


def _box(bone, name, cx, y0, cz, w, h, d):
    return bone.cube(name, cx - w / 2, -(y0 + h), cz - d / 2, w, h, d)


def _aim(r, name, d, xd=(1, 0, 0)):
    """Sets a bone's rest turn so its +y lies along d (a model direction) at rest."""
    r.by_name[name].rot = ik.orient(r, core.frames(r, {}), name, d, xd)


def body():
    r = R.Rig(ID, (512, 512))
    base = r.bone("base", (0, 24, 0))
    t3 = _bone(base, "t3", 0, BODY, 0)
    t2 = t3.bone("t2", (0, 0, -SEG / 2))
    t1 = t2.bone("t1", (0, 0, -SEG))
    t0 = t1.bone("t0", (0, 0, -SEG))
    hd = t0.bone("head", (0, 0, -SEG))
    t4 = t3.bone("t4", (0, 0, SEG / 2))
    t5 = t4.bone("t5", (0, 0, SEG))
    # the plates: each a shingle over the one behind it, the body under it, a pale strip of belly; the back end tapers
    for nm, b, z0 in (("t0", t0, -SEG), ("t1", t1, -SEG), ("t2", t2, -SEG), ("t3", t3, -SEG / 2), ("t4", t4, 0), ("t5", t5, 0)):
        k = nm[1]
        last = nm == "t5"
        cz = z0 + SEG / 2
        _box(b, "plate" + k, 0, 2.5, cz + 1, 13 if last else 15, 2, SEG + 2)
        _box(b, "core" + k, 0, -2.5, cz, 11 if last else 13, 5, SEG)
        _box(b, "belly" + k, 0, -4.5, cz, 7 if last else 9, 2, SEG - 1)
    _box(t5, "end", 0, -2, SEG + 1.5, 6, 4, 3)
    # the head: small and flat, a crest down it, two big eyes standing out of its sides, its mouth under its front
    _box(hd, "head", 0, -3, -5, 11, 6, 10)
    _box(hd, "crest", 0, 3, -4.5, 8, 2, 8)
    _box(hd, "mouth", 0, -5, -8, 8, 2, 4)
    _box(hd, "eye_l", 6.0, -1.5, -7, 2, 4, 4)
    _box(hd, "eye_r", -6.0, -1.5, -7, 2, 4, 4)
    # its poison claws under the front of its head, curving in to meet
    for s, n in ((1, "l"), (-1, "r")):
        fc = _bone(hd, "fc_" + n, s * 3.0, -4.0, -9.5)
        fc.cube("fcb_" + n, -1, -1, -5, 2, 2, 5)
        fc.cube("fct_" + n, -1 - 1.0 * s, -1, -8, 2, 2, 3)
    # its feelers, from the top of its head's front, and its back legs, from the end of its back plate: long whips of
    # three links, banded
    for s, n in ((1, "l"), (-1, "r")):
        a0 = _bone(hd, f"ant_{n}0", s * 3.0, 2.5, -9.5)
        a0.cube(f"ant_{n}0", -1, 0, -1, 2, ANT, 2)
        a1 = a0.bone(f"ant_{n}1", (0, ANT, 0))
        a1.cube(f"ant_{n}1", -0.5, 0, -0.5, 1, ANT, 1)
        a2 = a1.bone(f"ant_{n}2", (0, ANT, 0))
        a2.cube(f"ant_{n}2", -0.5, 0, -0.5, 1, ANT, 1)
        _aim(r, f"ant_{n}0", (s * 0.42, -0.42, -0.80))
        b0 = _bone(t5, f"tl_{n}0", s * 4.0, -1.0, 6.0)
        b0.cube(f"tl_{n}0", -1, 0, -1, 2, TL, 2)
        b1 = b0.bone(f"tl_{n}1", (0, TL, 0))
        b1.cube(f"tl_{n}1", -0.5, 0, -0.5, 1, TL, 1)
        b2 = b1.bone(f"tl_{n}2", (0, TL, 0))
        b2.cube(f"tl_{n}2", -0.5, 0, -0.5, 1, TL, 1)
        _aim(r, f"tl_{n}0", (s * 0.48, -0.40, 0.78))
    # ten pairs of walking legs: the coxa at the hip, the thigh up to its knee, the long shin down to a fine claw
    for k in range(NL):
        i, s = k // 2, _side(k)
        seg = r.by_name[SEG_OF[i]]
        hx, hy, hz = _hip_world(k)
        hp = seg.bone(f"hip{k}", (hx, -(hy - BODY), hz - _seg_z(SEG_OF[i])), (0, _hip_yaw(k), 0))
        hp.cube(f"coxa{k}", -2, -2, -2, 4, 4, 4)
        th = hp.bone(f"thigh{k}", (0, 0, 0))
        th.cube(f"femur{k}", -1.5, 0, -1.5, 3, THIGH[i], 3)
        sh = th.bone(f"shin{k}", (0, THIGH[i], 0))
        tib = round(SHIN[i] * 0.55)
        sh.cube(f"tibia{k}", -1, 0, -1, 2, tib, 2)
        sh.cube(f"tarsus{k}", -0.5, tib, -0.5, 1, SHIN[i] - tib, 1)
    fr = core.frames(r, {})
    for k in range(NL):
        (ax, ay, az), kn = _two_bone(r, fr, k, ik.model(REST_FOOT[k]), POLE[k])
        r.by_name[f"thigh{k}"].rot = (ax / DEG, ay / DEG, az / DEG)
        r.by_name[f"shin{k}"].rot = (kn / DEG, 0.0, 0.0)
        fr = core.frames(r, {})
    r.look = [("head", 0.6, 0.4)]
    r.shadow = 2.6
    r.size = 1.0
    # secondary parts (SPEC §35.10): the feelers and the back legs whip after the body, the poison claws tremble
    r.springs = [(f"ant_{n}{j}", 0.55, 1.8 + 0.5 * j, 0.12) for n in ("l", "r") for j in (1, 2)] + \
                [(f"tl_{n}{j}", 0.5, 1.6 + 0.4 * j, 0.14) for n in ("l", "r") for j in (1, 2)]
    r.guard = [f"shin{k}" for k in range(NL)] + ["head", "fc_l", "fc_r", "ant_l2", "ant_r2", "tl_l2", "tl_r2"]  # bones
    return r


# local rest offsets, filled by design(): each claw's rest point in its plate's own frame, and each plate's pivot
LOCAL_FOOT = {}


# ---------------------------------------------------------------------------------------------------------------------
# Paint

CHITIN = Ramp("#15120c", "#201b12", "#2c2618", "#3b3221", "#4b402b", "#5d5036", "#706142", "#84734f", "#98865f", "#ac9a72", "#c0ae86")
STRIPE = Ramp("#06050a", "#0c0a14", "#141020", "#1d182e", "#29213f", "#352b52", "#443866")
LEG = Ramp("#2e291f", "#453e30", "#5e5543", "#786d57", "#93876e", "#ada188", "#c6bba2", "#dcd2b9")
EYE = Ramp("#030205", "#0a0812", "#151124", "#221c3a", "#3a3060", "#625488")


def _bands(P_, c, ramp, base, dark, every, width, faces=SIDES, start=0, tip=None):
    """Bands across a leg or a feeler, every few pixels down its length; tip: the last pixels darkest."""
    for fname in faces:
        f = P_.face(c, fname)
        for i, j in f.each():
            if (j + start) % every < width:
                f.set(i, j, STRIPE.at(dark + (0.6 if fname == "north" else 0)))
            if tip and j >= f.h - tip:
                f.set(i, j, STRIPE.at(1.5))


def paint(r, P_):
    """Dusty grey-yellow plates with three dark violet stripes down its back and a dark stripe along each side; a paler
    belly; long legs and feelers banded pale and violet, the bands closer toward the claws; eyes black with a violet
    sheen in their facets; the poison claws dark at the tips."""
    of = lambda *ps: [c.name for c in r.cubes if any(c.name.startswith(p) for p in ps)]
    speck = R.p_strokes(cell=2, length=2, dark=0.6, light=0.35, density=0.5, seed=7, tops=True)
    for k in "012345":
        P_.paint("plate" + k, CHITIN, 6.6, speck, grad=0.6, rim=0.9, ao=0.6, noise=0.6, nscale=3)
        P_.paint("core" + k, CHITIN, 5.0, speck, grad=1.0, rim=0.4, ao=0.8, noise=0.5, nscale=3)
        P_.paint("belly" + k, CHITIN, 7.4, None, grad=0.4, rim=0.2, ao=0.3, noise=0.4)
        # three stripes down the back: a broad one down the middle, a thin one either side; the plate's back edge
        # shadowed where it laps over the next
        f = P_.face("plate" + k, "top")
        mid = f.w // 2
        for i, j in f.each():
            if i in (mid - 1, mid) and not (k == "5" and j < 2):
                f.set(i, j, STRIPE.at(2.6 + 0.8 * ((i + j) % 2)))
            elif i in (1, f.w - 2):
                f.set(i, j, STRIPE.at(3.4))
            if j == 0:
                f.set(i, j, CHITIN.at(3.0))
        for fname in ("west", "east"):  # the stripe along each side, at the plate's edge
            f = P_.face("core" + k, fname)
            for i, j in f.each():
                if j == 1:
                    f.set(i, j, STRIPE.at(3.0 + 0.6 * (i % 2)))
        f = P_.face("plate" + k, "south")
        for i, j in f.each():
            f.set(i, j, CHITIN.at(2.6))
    P_.paint("end", CHITIN, 4.6, speck, grad=0.8, rim=0.4, ao=0.6)
    # the head: a dark stripe down its crest, the mouth darker, the eyes black with a violet sheen in their facets
    P_.paint("head", CHITIN, 6.0, speck, grad=0.8, rim=0.8, ao=0.6, noise=0.5, nscale=3)
    P_.paint("crest", CHITIN, 6.8, None, grad=0.4, rim=0.8, ao=0.3, noise=0.4)
    f = P_.face("crest", "top")
    for i, j in f.each():
        if i in (f.w // 2 - 1, f.w // 2):
            f.set(i, j, STRIPE.at(3.0))
    P_.paint("mouth", CHITIN, 3.0, None, grad=0.4, rim=0.2, ao=0.4)
    for c in ("eye_l", "eye_r"):
        P_.paint(c, EYE, 1.2, None, grad=0.0, rim=0.0, ao=0.0)
        for fname in ("top", "north") + SIDES:
            f = P_.face(c, fname)
            for i, j in f.each():
                f.set(i, j, EYE.at(1 + ((i + 2 * j) % 3 == 0) * 2.4 + (j == 0) * 1.0))
    for c in of("fcb_"):
        P_.paint(c, CHITIN, 4.0, None, grad=0.4, rim=0.4)
    for c in of("fct_"):
        P_.paint(c, STRIPE, 1.2, None, grad=0.0, rim=0.4)
    # the legs: the coxa plain, the thigh banded wide, the shin banded closer toward the claw, its tip dark
    for c in of("coxa"):
        P_.paint(c, CHITIN, 5.4, None, grad=0.4, rim=0.4, ao=0.4)
    for c in of("femur"):
        P_.paint(c, LEG, 5.0, None, grad=0.3, rim=0.6, ao=0.0, noise=0.4)
        _bands(P_, c, LEG, 5.0, 3.2, 4, 1, start=2)
        for fname in SIDES:  # the knee dark
            f = P_.face(c, fname)
            for i in range(f.w):
                f.set(i, f.h - 1, STRIPE.at(2.4))
    for c in of("tibia"):
        P_.paint(c, LEG, 5.6, None, grad=0.2, rim=0.5, ao=0.0, noise=0.3)
        _bands(P_, c, LEG, 5.6, 3.4, 3, 1)
    for c in of("tarsus"):
        P_.paint(c, LEG, 6.2, None, grad=0.2, rim=0.4, ao=0.0, noise=0.3)
        _bands(P_, c, LEG, 6.2, 3.6, 2, 1, tip=2)
    # the feelers and the back legs: banded all the way, closer and darker toward their tips
    for n in ("l", "r"):
        for j in range(3):
            P_.paint(f"ant_{n}{j}", LEG, 5.8 - 0.5 * j, None, grad=0.0, rim=0.3, ao=0.0)
            _bands(P_, f"ant_{n}{j}", LEG, 5.8, 3.0 + 0.3 * j, 3 - (j == 2), 1, faces=("top", "bottom") + SIDES, tip=1 if j == 2 else None)
            P_.paint(f"tl_{n}{j}", LEG, 6.0 - 0.4 * j, None, grad=0.0, rim=0.3, ao=0.0)
            _bands(P_, f"tl_{n}{j}", LEG, 6.0, 3.0, 4 - j, 2 if j == 0 else 1, faces=("top", "bottom") + SIDES, tip=1 if j == 2 else None)
    return P_.skin, P_.glow


# ---------------------------------------------------------------------------------------------------------------------
# Posing: the trunk set, each leg reaching its claw to where it stands (or carried with its plate), the feelers and
# the back legs aimed

def _feeler(lift, sweep, s, back=False, el0=24.0, az0=28.0):
    """A feeler's (or a back leg's) way out of its head (or its back plate): el degrees up and az out to its side."""
    el, az = (el0 + lift) * DEG, (az0 + sweep) * DEG
    return (s * math.cos(el) * math.sin(az), -math.sin(el), (1 if back else -1) * math.cos(el) * math.cos(az))


def _pose(r, lift=0.0, pitch=0.0, yaw=0.0, roll=0.0, shift=(0.0, 0.0), rear=0.0, curl=0.0, tail=(0.0, 0.0), wave=(0.0, 0.0),
          head=(0.0, 0.0, 0.0), feet=None, ant=(0.0, 0.0, 0.0, 0.0), tl=(0.0, 0.0, 0.0, 0.0), fangs=0.0, droop=(0.0, 0.0), extra=None):
    """lift (+ up), pitch (+ its front dipping), yaw (+ to its left), roll (+ its left side down) and shift (x, z) move
    its middle; rear (+ up) lifts its front off the floor and curl (+ to its left) bends it, front and back; tail (lift,
    swing to its left) moves its back end; wave (degrees, phase) runs a ripple down it from its head; head (nod + down,
    turn + left, tilt). feet {leg: where}: (x, height, z) on the floor or in the air, ("h", out, up, fwd) carried with
    its plate from where it stands at rest, ("f", out, up, fwd) carried with its plate but standing on the floor; legs
    not named stand where they stand at rest. ant and tl (left lift, right lift, left sweep, right sweep) aim the
    feelers and the back legs (+ up, + out); droop (feelers, back legs) bends their links; fangs opens the poison
    claws."""
    p = {"t3": {"rot": (pitch, yaw, roll), "pos": (U * shift[0], U * lift, U * shift[1])}}
    amp, ph = wave
    order = ("head", "t0", "t1", "t2", "t4", "t5")
    wv = {b: amp * math.sin(ph - 1.1 * j) for j, b in enumerate(order)}
    for b, w in FRONT_W.items():
        p[b] = (-rear * w, -(curl * w * 0.7 + wv[b]), 0.0)  # +y turns a front plate's tip to its right
    for b, w in BACK_W.items():
        p[b] = (tail[0] * w, (curl * 0.6 + tail[1]) * w + wv[b], 0.0)  # and a back plate's to its left
    p["head"] = (head[0], -(head[1] + wv["head"]), head[2])
    p["fc_l"] = (0, -fangs, 0)
    p["fc_r"] = (0, fangs, 0)
    fr = core.frames(r, core.as_sample(p))
    for s, n, j in ((1, "l", 0), (-1, "r", 1)):
        Mh = fr["head"][0]
        d = R._mv(Mh, _feeler(ant[j], ant[2 + j], s))
        p[f"ant_{n}0"] = ik.orient(r, fr, f"ant_{n}0", d, R._mv(Mh, (1, 0, 0)))
        p[f"ant_{n}1"] = (8 + droop[0], 0, 0)
        p[f"ant_{n}2"] = (10 + 1.4 * droop[0], 0, 0)
        Mt = fr["t5"][0]
        d = R._mv(Mt, _feeler(tl[j], tl[2 + j], s, back=True, el0=24.0, az0=28.0))
        p[f"tl_{n}0"] = ik.orient(r, fr, f"tl_{n}0", d, R._mv(Mt, (1, 0, 0)))
        p[f"tl_{n}1"] = (-6 - droop[1], 0, 0)
        p[f"tl_{n}2"] = (-8 - 1.4 * droop[1], 0, 0)
    fr = core.frames(r, core.as_sample(p))
    feet = feet or {}
    for k in range(NL):
        p.update(_limb(r, fr, k, _target(fr, k, feet.get(k)), _pole(fr, k)))
    if extra:
        p.update(extra)
    return p


def _pole(fr, k):
    return R._mv(fr[SEG_OF[k // 2]][0], POLE[k])


def _target(fr, k, where):
    """A claw's place in model pixels."""
    if where is None:
        return ik.model(REST_FOOT[k])
    if isinstance(where[0], str):
        mode, out, up, fwd = where
        out, up, fwd = U * out, U * up, U * fwd
        dx, dz = _dir(k)
        lx, ly, lz = LOCAL_FOOT[k]
        q = core.point(fr, SEG_OF[k // 2], (lx + dx * out, ly - up, lz + dz * out - fwd))
        if mode == "f":
            q = (q[0], core.GROUND - 0.5 - up, q[2])
        return q
    return ik.model(where)


def _foot(k, out=0.0, side=0.0, up=0.0, dz=0.0, dx=0.0):
    """Leg k's claw moved from where it stands at rest: out along its way out, side across it (toward its front), up,
    and by (dx, dz)."""
    ox, oz = _dir(k)
    x, h, z = REST_FOOT[k]
    sx, sz = (oz, -ox) if _side(k) > 0 else (-oz, ox)
    out, side, up, dz, dx = U * out, U * side, U * up, U * dz, U * dx
    return (x + ox * out + sx * side + dx, h + up, z + oz * out + sz * side + dz)


def _legs(pairs, fn):
    out = {}
    for i in pairs:
        for k in (2 * i, 2 * i + 1):
            out[k] = fn(k)
    return out


def _all(fn):
    return _legs(range(NW), fn)


def _with(*ds):
    out = {}
    for d in ds:
        out.update(d)
    return out


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


def _back(r, end):
    home = _pose(r)
    return [(0.45, _mix(end, home, 0.45)), (0.7, _mix(end, home, 0.82))]


def _plant(r, clip, legs, where=None):
    """The legs that stand through a move keep their claws on the floor where they stand, every tick."""
    where = where or {}

    def fn(t, s, fr):
        out = {}
        for k in legs:
            out.update(_limb(r, fr, k, _target(fr, k, where.get(k)), _pole(fr, k)))
        return out
    ik.retrack(r, clip, fn)


def _unwrap(keys):
    """Each key's turns written within half a turn of the key before, so nothing spins round between keys."""
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


FLOOR = -(BODY - 5.0) / U  # the lift that sets its belly on the floor


# ---------------------------------------------------------------------------------------------------------------------
# Clips

def _phase(k):
    """The wave of steps runs up each side from its back legs to its front ones; the two sides out of step."""
    return ((NW - 1 - k // 2) / NW + 0.5 * (k % 2)) % 1.0


def _gait(u, stride_px, beta, lift, turn=0.0):
    """The claws through a stride at u: down, each moves back as far as it travels, so it stays put on the floor; up, it
    lifts and reaches forward again. turn swings the steps round (+ to its left), more the further a leg is from its
    middle."""
    feet = {}
    st = stride_px * beta
    lift, turn = U * lift, U * turn
    for k in range(NL):
        q = (u - _phase(k)) % 1.0
        x, h, z = REST_FOOT[k]
        sw = turn * (z / 35.0)  # a step's sideways part when it turns, front legs one way and back legs the other
        if q < beta:
            f = q / beta
            feet[k] = (x - sw * (f - 0.5) * 2, h, z - st / 2 + st * f)
        else:
            f = (q - beta) / (1 - beta)
            e = core.ease_io(f)
            feet[k] = (x - sw * (0.5 - e) * 2, h + lift * math.sin(math.pi * f) ** 0.8, z + st / 2 - st * e)
    return feet


def _idle(r):
    """It stands low on its ring of legs, nearly still; its feelers never stop, sweeping and tasting the air one after
    the other, and its back legs sway behind it; its head turns a little; now and then a shiver runs up its legs from
    the back to the front, each pair lifting and setting down again in turn, the way they do in a wall."""
    L, n = 6.0, 120
    keys = []
    for kk in range(n + 1):
        u = kk / n
        w = 2 * math.pi * u
        feet = {}
        for k in range(NL):  # the shiver: from 2.4 s, a pair every 0.06 s from the back, both sides nearly together
            t0 = 2.4 + 0.06 * (NW - 1 - k // 2) + 0.03 * (k % 2)
            f = (L * u - t0) / 0.22
            if 0 < f < 1:
                feet[k] = _foot(k, up=3.0 * math.sin(math.pi * f), out=0.8 * math.sin(math.pi * f))
        keys.append((L * u, _pose(r, lift=0.35 * math.sin(2 * w), pitch=0.6 * math.sin(w + 0.4), curl=1.5 * math.sin(w), rear=1.2 + 1.0 * math.sin(2 * w + 1),
                                  head=(2 * math.sin(2 * w), 9 * math.sin(w + 0.5) + 3 * math.sin(3 * w), 2 * math.sin(2 * w + 1)),
                                  ant=(10 + 10 * math.sin(3 * w), 10 + 10 * math.sin(3 * w + 2.2), 6 * math.sin(4 * w + 0.4) + 6 * math.sin(w),
                                       6 * math.sin(4 * w + 2.7) - 6 * math.sin(w)),
                                  tl=(4 * math.sin(2 * w + 0.6), 4 * math.sin(2 * w + 2.4), 5 * math.sin(w + 1.3), 5 * math.sin(w + 4.0)),
                                  droop=(4 * math.sin(2 * w), 3 * math.sin(2 * w + 1)), fangs=4 + 4 * math.sin(5 * w), feet=feet)))
    keyed(r, "idle", L, keys, loop=True)


def _locomotion(r):
    """Its walk: a wave of steps runs up each side from its back legs to its front ones, both sides out of step, so
    its legs ripple; its body rides low and level with a little sway, its feelers held forward and tasting, its back
    legs trailing. Running it goes lower and much faster and its feelers sweep back."""
    for name, L, stride, beta, lift, drop, sway, antl in (("walk", 0.8, 1.75, 0.6, 4.5, 0.0, 2.0, 16.0), ("run", 0.45, 3.0, 0.5, 6.0, 1.6, 3.0, 4.0)):
        sp = stride * 16
        keys = []
        n = 28
        for kk in range(n + 1):
            u = kk / n
            w = 2 * math.pi * u
            keys.append((L * u, _pose(r, lift=-drop + 0.3 * math.sin(4 * w), pitch=0.5 * math.sin(2 * w), wave=(sway, w), rear=1.0,
                                      head=(1.0 * math.sin(2 * w), 0, 0), ant=(antl + 6 * math.sin(w), antl + 6 * math.sin(w + math.pi), 8 * math.sin(w),
                                                                              8 * math.sin(w + math.pi)),
                                      tl=(-4 + 3 * math.sin(2 * w), -4 + 3 * math.sin(2 * w + 1), 4 * math.sin(w), -4 * math.sin(w)),
                                      droop=(6 if name == "run" else 2, 4), feet=_gait(u, sp, beta, lift))))
        keyed(r, name, L, keys, loop=True)
    r.walk_stride, r.run_stride = 1.75, 3.0
    r.run_speed = (0.14, 0.3)
    for nm, sgn in (("turn_l", 1), ("turn_r", -1)):
        keys = []
        n = 24
        for kk in range(n + 1):
            u = kk / n
            w = 2 * math.pi * u
            keys.append((0.7 * u, _pose(r, yaw=sgn * 3.0, curl=sgn * 6.0, head=(0, sgn * 10, 0), ant=(14, 14, 6 * sgn, -6 * sgn), feet=_gait(u, 0.0, 0.6, 4.0, turn=sgn * 6.0),
                                        tl=(0, 0, -6 * sgn, 6 * sgn))))
        keyed(r, nm, 0.7, keys, loop=True)


def _reactions(r):
    """Struck, it jerks back on its legs with its front flung up, its feelers flung back and its poison claws open; from
    the side it curls away from the blow. Its stance broken, its legs slide out and it drops flat on the floor; it gathers
    them in and lifts itself again."""
    hit = _pose(r, lift=2.0, rear=14, shift=(0, 2.5), head=(-14, 0, 0), ant=(36, 36, 30, 30), tl=(16, 16, 10, 10), fangs=22, droop=(-6, -4))
    back = _pose(r, lift=-1.0, rear=-3, shift=(0, 0.6), head=(4, 0, 0), ant=(6, 6, -4, -4), tl=(-4, -4, 0, 0), fangs=2, droop=(6, 4))
    keyed(r, "hurt", 0.55, [(0, _pose(r)), (0.08, hit), (0.3, back), (0.55, _pose(r))])
    for nm, sgn in (("hurt_l", 1), ("hurt_r", -1)):  # struck from its left it curls away to its right
        h = _pose(r, lift=1.2, roll=-6 * sgn, curl=-14 * sgn, shift=(-sgn * 2.5, 0), head=(-6, -16 * sgn, 0), ant=(24, 24, 20, 20), tl=(10, 10, 0, 0), fangs=16,
                  feet=_all(lambda k: ("f", 0, 0, 0)))
        b = _pose(r, lift=-0.4, roll=2 * sgn, curl=4 * sgn, shift=(sgn * 0.6, 0), head=(2, 4 * sgn, 0), ant=(8, 8, 0, 0), fangs=2)
        keyed(r, nm, 0.6, [(0, _pose(r)), (0.08, h), (0.32, b), (0.6, _pose(r))])
    half = _pose(r, lift=-4, feet=_all(lambda k: _foot(k, out=3)), head=(6, 0, 0), ant=(-4, -4, 10, 10), droop=(10, 8))
    flat = _pose(r, lift=FLOOR, feet=_all(lambda k: _foot(k, out=7)), head=(8, 0, 0), ant=(-16, -16, 20, 20), tl=(-12, -12, 10, 10), droop=(14, 10))
    keyed(r, "kneel", 0.9, [(0, _pose(r)), (0.3, half), (0.62, _mix(half, flat, 0.85)), (0.9, flat)])
    push = _pose(r, lift=-5, feet=_all(lambda k: _foot(k, out=2)), head=(2, 0, 0), ant=(6, 6, 8, 8), droop=(6, 4))
    keyed(r, "rise", 1.0, [(0, flat), (0.4, push), (0.75, _pose(r, lift=0.8, ant=(16, 16, 0, 0))), (1.0, _pose(r))])
    _death(r)


def _death(r):
    """Its last: it rears and thrashes, its front whipping one way and the other, its front legs clawing at the air; it
    drops, its legs sliding out from under it; it curls up on the floor into a hook, and its legs fold in under it one
    pair after another from the back; one twitches, and it is still."""
    fl = lambda up, out, fwd: _legs((0, 1), lambda k: ("h", out, up, fwd))
    thrash1 = _pose(r, lift=2, rear=30, curl=22, head=(-20, 10, 0), fangs=40, ant=(40, 30, 30, 10), tl=(20, 30, 10, 20), feet=fl(8, 4, 2))
    thrash2 = _pose(r, lift=1.5, rear=24, curl=-26, head=(-10, -20, 0), fangs=30, ant=(20, 40, 0, 30), tl=(30, 10, 20, 0), feet=fl(6, 6, -4))
    thrash3 = _pose(r, lift=1, rear=14, curl=18, roll=5, head=(-6, 14, 6), fangs=34, ant=(30, 10, 20, 0), feet=fl(4, 2, 0))
    drop = _pose(r, lift=FLOOR + 1.5, rear=2, curl=8, roll=2, head=(6, 6, 0), fangs=10, ant=(-6, -10, 20, 24), tl=(-8, -8, 10, 10), droop=(10, 8),
                 feet=_all(lambda k: _foot(k, out=6)))
    flat = _pose(r, lift=FLOOR, curl=10, roll=1.5, head=(8, 4, 0), fangs=6, ant=(-14, -16, 24, 26), tl=(-12, -12, 12, 12), droop=(14, 10),
                 feet=_all(lambda k: _foot(k, out=7)))

    def curled(f, twitch=None):
        """Curled up f of the way, the pairs folding in from the back."""
        feet = {}
        for k in range(NL):
            g = max(0.0, min(1.0, f * 1.6 - 0.06 * (NW - 1 - k // 2)))
            up = 6.0 if twitch == k else 0.0
            feet[k] = ("f", 7 * (1 - g) - 9 * g, up + 1.5 * g, 0) if g < 0.999 else ("f", -9, 1.5 + up, 0)
        return _pose(r, lift=FLOOR, curl=10 + 60 * f, tail=(2, 18 * f), roll=1.5 + 4 * f, head=(8 + 6 * f, 4 + 10 * f, 0), fangs=6 - 4 * f,
                     ant=(-14, -16, 24 - 20 * f, 26 - 10 * f), tl=(-12, -12, 12 - 10 * f, 12), droop=(14 + 6 * f, 10 + 4 * f), feet=feet)
    keys = [(0, _pose(r)), (0.3, thrash1), (0.65, thrash2), (1.0, thrash3), (1.4, drop), (1.75, flat), (2.3, curled(0.25)), (2.9, curled(0.55)),
            (3.5, curled(0.85)), (3.8, curled(0.9, 13)), (4.1, curled(0.92)), (5.0, curled(0.95))]
    keyed(r, "death", 5.0, _unwrap(keys))


# ---------------------------------------------------------------------------------------------------------------------
# Moves

NO_LAG = {b + str(k): 0 for b in ("hip", "thigh", "shin") for k in range(NL)} | {b: 0 for b in ("t3", "t4", "t5")}


def _lag(**kw):
    out = dict(NO_LAG)
    out.update(kw)
    return out


def _claw(k):
    return (f"shin{k}", (0, SHIN[k // 2], 0), 4.0)


def bite(r):
    """The Bite: it draws its head up and back, its front lifting off the floor and its poison claws spreading, its
    front legs raised; then it plunges, its claws snapping shut on you and its front legs slamming down before it
    (LIGHT)."""
    up = lambda h, o, fw: _legs((0, 1), lambda k: ("h", o, h, fw))
    gather = _pose(r, lift=-1.5, rear=8, shift=(0, 2), head=(-10, 0, 0), fangs=24, ant=(30, 30, 20, 20), feet=up(3, 0, -1))
    peak = _pose(r, lift=-1.2, rear=18, shift=(0, 3.5), head=(-18, 0, 0), fangs=42, ant=(42, 42, 30, 30), tl=(10, 10, 0, 0), feet=up(6, 1, -2))
    strike = _pose(r, lift=-3.2, rear=-10, pitch=3, shift=(0, -5.5), head=(16, 0, 0), fangs=-6, ant=(8, 8, -10, -10),
                   feet=_legs((0, 1), lambda k: _foot(k, dz=-11, out=-2)))
    held = _pose(r, lift=-3.8, rear=-12, pitch=3, roll=1.5, shift=(0, -6.2), head=(19, 6, 4), fangs=-10, ant=(4, 12, -14, -6),  # it worries at you
                 feet=_legs((0, 1), lambda k: _foot(k, dz=-11.5, out=-2)))
    return Move("bite", "LIGHT", 10, 4, 14, through=held, track=8.0, rng=(0, 3.0), kinds=("combo", "near"), damage=1.0, knock=0.5,
                hit=[("head", (0, 0, -10), 7.0), ("fc_l", (0, 0, -8), 4.0), ("fc_r", (0, 0, -8), 4.0)], plant=(),
                stages={"windup": [(0.2, _mix(_pose(r), gather, 0.45)), (0.5, gather), (1.0, peak)], "active": [(0.5, strike), (1.0, held)],
                        "recover": _back(r, held)}, lag=_lag())


def lasso(r):
    """The Lasso (LIGHT, after the Bite): its front legs lift high and back over its head; then it lunges and throws
    them over you in a ring, and folds them back to its jaws."""
    gather = _pose(r, lift=-1.5, rear=5, shift=(0, 1.5), head=(-12, 0, 0), fangs=30, ant=(30, 30, 30, 30), feet=_legs((0, 1, 2), lambda k: ("h", -1, 10, -4)))
    ring = {0: (7, 3, -38), 1: (12, 3, -34), 2: (15, 3, -29)}
    throw = _pose(r, lift=-0.5, rear=2, pitch=3, shift=(0, -4), head=(4, 0, 0), fangs=40, ant=(10, 10, -10, -10),
                  feet=_legs((0, 1, 2), lambda k: (U * _side(k) * ring[k // 2][0], U * ring[k // 2][1], U * ring[k // 2][2])))
    fold = {0: (3, 6, -27), 1: (5, 6, -25), 2: (7, 5, -22)}
    pull = _pose(r, lift=0, rear=6, pitch=1, shift=(0, -3), head=(10, 0, 0), fangs=-6, ant=(12, 12, 0, 0),
                 feet=_legs((0, 1, 2), lambda k: (U * _side(k) * fold[k // 2][0], U * fold[k // 2][1], U * fold[k // 2][2])))
    return Move("lasso", "LIGHT", 10, 5, 16, through=pull, track=6.0, rng=(0, 3.5), kinds=("combo", "near"), damage=1.1, knock=0.3,
                hit=[_claw(k) for k in range(6)], plant=(),
                stages={"windup": [(0.2, _mix(_pose(r), gather, 0.4)), (0.6, _mix(_pose(r), gather, 0.85)), (1.0, gather)], "active": [(0.4, throw), (1.0, pull)],
                        "recover": _back(r, pull)},
                lag=_lag())


def drop(r):
    """The Drop (HEAVY, its gap closer): it crouches on its legs, then rears its front up high with its front legs
    spread wide in the air, and holds; then it springs, its legs drawn in, and comes down on you flat with every leg
    splayed out on the floor round it."""
    crouch = _pose(r, lift=-3, feet=_all(lambda k: _foot(k, out=3)), head=(4, 0, 0), ant=(-4, -4, 20, 20), fangs=10)
    rear = _pose(r, lift=3, rear=42, pitch=-4, head=(-20, 0, 0), fangs=40, ant=(50, 50, 30, 30), tl=(20, 20, 10, 10),
                 feet=_with(_legs((0, 1, 2), lambda k: ("h", 4, 8, -2)), _legs((3, 4, 5, 6, 7, 8, 9), lambda k: _foot(k, out=2))))
    leap = _pose(r, lift=16, rear=20, pitch=-8, head=(-10, 0, 0), fangs=30, ant=(30, 30, 10, 10), tl=(-10, -10, 0, 0), droop=(-6, -6),
                 feet=_all(lambda k: ("h", -2, 3, -8)))  # its legs swept back along it as it springs
    top = _pose(r, lift=18, rear=-10, pitch=10, head=(8, 0, 0), fangs=44, ant=(20, 20, 20, 20), tl=(14, 14, 10, 10),
                feet=_all(lambda k: ("h", 8, -3, 0)))  # then flung out wide, reaching down for the floor
    land = _pose(r, lift=-3.5, rear=-4, pitch=2, head=(10, 0, 0), fangs=-4, ant=(0, 0, 30, 30), tl=(-6, -6, 10, 10), droop=(10, 8),
                 feet=_all(lambda k: _foot(k, out=6)))
    push = _pose(r, lift=-1, feet=_all(lambda k: _foot(k, out=2.5)), head=(4, 0, 0), ant=(10, 10, 10, 10))
    return Move("drop", "HEAVY", 18, 10, 22, through=_mix(land, push, 0.3), title="Drop", hold=8, track=6.0, lock=12, advance=6.0, ability="Drop",
                rng=(4, 12), kinds=("gap", "area"), damage=1.3, knock=1.2, plant=(), disc=3.0,
                hit=[("t0", (0, 0, -5), 10.0), ("t2", (0, 0, -5), 10.0), ("head", (0, 0, -7), 8.0)],
                stages={"windup": [(0.35, crouch), (1.0, rear)], "active": [(0.3, leap), (0.6, top), (1.0, land)], "recover": [(0.5, push)]}, lag=_lag())


def web(r):
    """Web: it rears its front up, its front legs drawn in under its jaws; then it flings them out at you wide apart, and
    the threads fly from them."""
    gather = _pose(r, lift=1, rear=14, head=(-6, 0, 0), fangs=20, ant=(20, 20, 10, 10), feet=_legs((0, 1, 2), lambda k: ("h", -4, 6, -2)))
    peak = _pose(r, lift=2.5, rear=26, shift=(0, 1.5), head=(-14, 0, 0), fangs=35, ant=(40, 40, -6, -6), tl=(10, 10, 0, 0),
                 feet=_legs((0, 1, 2), lambda k: ("h", -3, 10, -3)))
    fling = _pose(r, lift=1, rear=10, shift=(0, -3), head=(6, 0, 0), fangs=45, ant=(20, 20, 30, 30), feet=_legs((0, 1, 2), lambda k: ("h", 6, 6, 12)))
    # the threads tugged: its front bobs and rocks, the legs shaking them out
    held = _pose(r, lift=-0.4, rear=6, roll=2.5, shift=(0, -3.6), head=(9, -6, 0), fangs=36, ant=(14, 22, 34, 28), feet=_legs((0, 1, 2), lambda k: ("h", 8, 3, 14)))
    shake = _pose(r, lift=1.2, rear=11, roll=-2.5, shift=(0, -2.4), head=(3, 6, 0), fangs=28, ant=(22, 14, 24, 32), feet=_legs((0, 1, 2), lambda k: ("h", 5, 7, 9)))
    return Move("web", "NONE", 14, 14, 16, through=shake, title="Web", track=9.0, ability="Web", rng=(4, 14), kinds=("ranged",), damage=0, knock=0,
                plant=(), stages={"windup": [(0.35, gather), (1.0, peak)], "active": [(0.35, fling), (0.7, held), (1.0, shake)], "recover": _back(r, shake)},
                lag=_lag())


def lash(r):
    """For whoever is behind it, the Lash: its head turns back over itself and its back end swings out to one side,
    its long back legs lifting high; then the back end whips round across behind it, the back legs lashing (LIGHT)."""
    back = _legs((8, 9), lambda k: ("f", 0, 0, 0))
    # the whole of it twists into the swing: its middle turns and rolls with its back end, its legs holding the floor
    gather = _pose(r, lift=-0.6, yaw=7, roll=-2, curl=-8, tail=(8, 22), head=(-4, 22, 0), tl=(30, 30, 10, 10), ant=(10, 10, 10, 10), feet=back)
    peak = _pose(r, lift=0.6, yaw=15, roll=-5, curl=-14, tail=(12, 40), head=(-6, 32, 0), tl=(46, 46, 22, -10), ant=(18, 18, 22, 0), feet=back)
    strike = _pose(r, lift=-1.2, yaw=-7, roll=3, curl=10, tail=(6, -32), head=(0, 10, 0), tl=(20, 20, -20, 30), droop=(0, -10), feet=back)
    out = _pose(r, lift=-1.4, yaw=-10, roll=4, curl=12, tail=(4, -40), head=(0, 6, 0), tl=(14, 14, -24, 36), droop=(0, -6), feet=back)
    return Move("lash", "LIGHT", 10, 4, 14, through=out, title="The Lash", track=0.0, rng=(0, 3.5), kinds=("behind",), damage=0.9, knock=0.9,
                hit=[("tl_l2", (0, TL, 0), 4.0), ("tl_r2", (0, TL, 0), 4.0), ("tl_l1", (0, TL, 0), 4.0), ("tl_r1", (0, TL, 0), 4.0), ("t5", (0, 0, 9), 7.0)],
                plant=(), stages={"windup": [(0.2, _mix(_pose(r), gather, 0.3)), (0.5, gather), (1.0, peak)], "active": [(0.5, strike), (1.0, out)],
                                  "recover": _back(r, out)}, lag=_lag())


def _shudder(r, j, low=-5.5, out=5.0, rear=0.0):
    """Pressed flat to the floor, shuddering: j runs -1..1."""
    return _pose(r, lift=low + 0.5 * abs(j), rear=rear, roll=3 * j, wave=(3.0, 2.0 * j), tail=(4 * j, 0), head=(6, 6 * j, 0), fangs=10 + 10 * abs(j),
                 ant=(-10 + 8 * abs(j), -10 + 8 * abs(j), 30, 30), tl=(-6 + 10 * abs(j), -6 + 10 * abs(j), 10, 10), droop=(8, 6),
                 feet=_all(lambda k: _foot(k, out=out)))


def hatch(r):
    """Hatchlings: it presses itself flat to the floor and shudders, its back heaving, and its young pour out from
    under it; it lifts itself again."""
    act = [(0.16, _shudder(r, 1)), (0.33, _shudder(r, -0.4)), (0.5, _shudder(r, -1)), (0.66, _shudder(r, 0.4)), (0.83, _shudder(r, 1)), (1.0, _shudder(r, 0))]
    return Move("hatch", "NONE", 14, 26, 16, through=_shudder(r, -0.3), title="Hatchlings", track=0.0, ability="Hatchlings", rng=(0, 99), kinds=("summon",),
                damage=0, knock=0, plant=(), stages={"windup": [(0.4, _mix(_pose(r), _shudder(r, 0), 0.5)), (1.0, _shudder(r, 0))], "active": act,
                                                     "recover": _back(r, _shudder(r, -0.3))}, lag=_lag())


def walls_open(r):
    """The Walls Open (into the second phase): it rears up with its front legs spread round its head like a crown and
    its feelers high, and trembles; then it drops flat to the floor and shudders as its young pour out from under it."""
    crown = lambda j: _pose(r, lift=3, rear=50 + 3 * j, pitch=-4, roll=3 * j, head=(-24, 6 * j, 0), fangs=45, ant=(60, 60, 40 + 10 * j, 40 - 10 * j),
                            tl=(30, 30, 20, 20), feet=_with(_legs((0, 1, 2, 3), lambda k: ("h", 8, 10, -2)), _legs((4, 5, 6, 7, 8, 9), lambda k: _foot(k, out=2))))
    act = [(0.12, crown(1)), (0.24, crown(-1)), (0.36, crown(0.8)), (0.48, crown(-0.6)), (0.62, _shudder(r, 0, out=6)), (0.75, _shudder(r, 1, out=6)),
           (0.88, _shudder(r, -1, out=6)), (1.0, _shudder(r, 0.3, out=6))]
    return Move("walls", "NONE", 16, 34, 20, through=_shudder(r, -0.2, out=6), title="The Walls Open", track=0.0, ability="Hatchlings", rng=(0, 99),
                kinds=("summon",), damage=0, knock=0, plant=(), stages={"windup": [(0.4, _mix(_pose(r), crown(0), 0.55)), (1.0, crown(0))], "active": act,
                                                                         "recover": _back(r, _shudder(r, -0.2, out=6))}, lag=_lag())


def thousand(r):
    """A Thousand Legs (PERILOUS, the signature): it rears up over you, its front legs fanned out wide and high round its
    head, every face of its eyes on you, and holds; then it comes down on you, its front legs slamming down in a ring
    round you, and its legs hammer the floor in a ripple, pair after pair, and it settles."""
    fan = _legs((0, 1, 2, 3, 4), lambda k: ("h", 6 + 2 * (k // 2), 14 - (k // 2), 4 - 2 * (k // 2)))
    stand = _legs((5, 6, 7, 8, 9), lambda k: _foot(k, out=1))
    begin = _pose(r, lift=1, rear=20, head=(-8, 0, 0), fangs=20, ant=(30, 30, 20, 20), feet=_with(_legs((0, 1, 2), lambda k: ("h", 2, 6, 0)), stand))
    up_ = lambda j: _pose(r, lift=4, rear=56 + 2 * j, pitch=-6, roll=2 * j, head=(-24, 4 * j, 0), fangs=50, ant=(60, 60, 50, 50), tl=(40, 40, 30, 30),
                          feet=_with(fan, stand))
    ring = _legs((0, 1, 2, 3, 4), lambda k: _foot(k, out=8, dz=-8 + 1.2 * (k // 2)))
    slam = _pose(r, lift=-3, rear=-10, pitch=4, head=(14, 0, 0), fangs=-4, ant=(0, 0, 30, 30), tl=(10, 10, 10, 10), droop=(10, 6), feet=_with(ring, stand))

    def drum(j):
        """Pair after pair lifting and stamping: j picks which half is up."""
        feet = {}
        for k in range(NL):
            base = ring[k] if k < 10 else _foot(k, out=1)
            up = 5.0 if (k // 2 + j) % 2 == 0 else 0.0
            feet[k] = (base[0], base[1] + up, base[2])
        return _pose(r, lift=-2.5 - 0.6 * j, rear=-8, pitch=3, wave=(2.0, 1.5 * j), head=(12, 4 * (1 - 2 * j), 0), fangs=6, ant=(4, 4, 28, 28), droop=(8, 6),
                     feet=feet)
    end = _pose(r, lift=-3, rear=-8, pitch=3, head=(12, 0, 0), fangs=0, ant=(2, 2, 30, 30), droop=(10, 6), feet=_with(ring, stand))
    push = _pose(r, lift=-1, rear=-2, head=(4, 0, 0), feet=_all(lambda k: _foot(k, out=2)), ant=(12, 12, 10, 10))
    return Move("thousand", "PERILOUS", 30, 14, 30, through=_mix(end, push, 0.3), title="A Thousand Legs", signature=True, track=4.0, rng=(0, 5.0),
                kinds=("area",), damage=1.8, knock=1.5, plant=(), disc=4.0,
                hit=[("head", (0, 0, -8), 8.0), ("t0", (0, 0, -5), 10.0), ("t1", (0, 0, -5), 10.0)] + [_claw(k) for k in range(10)],
                stages={"windup": [(0.3, begin), (0.7, up_(0)), (0.85, up_(1)), (1.0, up_(-1))],
                        "active": [(0.25, slam), (0.42, drum(0)), (0.58, drum(1)), (0.74, drum(0)), (0.9, drum(1)), (1.0, end)],
                        "recover": [(0.5, push)]}, lag=_lag())


def wake(r):
    """Its intro, "You can hear them in the walls.": it lies flat to the floor, its legs pressed out along it and its
    feelers laid back; a feeler lifts, then the other; a shiver runs up its legs from the back to the front, each pair
    drawing in under it in turn; it rises onto them all at once and sweeps its feelers at you."""
    flat = _pose(r, lift=FLOOR + 0.5, feet=_all(lambda k: _foot(k, out=6, dz=3)), head=(8, 0, 0), ant=(-16, -16, -10, -10), tl=(-14, -14, -6, -6), droop=(12, 10))
    act = []
    for kk in range(1, 21):
        f = kk / 20
        feet = {}
        for k in range(NL):
            t0 = 0.36 + 0.035 * (NW - 1 - k // 2) + 0.015 * (k % 2)
            g = max(0.0, min(1.0, (f - t0) / 0.1))
            lift = 3.5 * math.sin(math.pi * g)
            feet[k] = _foot(k, out=6 * (1 - g) + 1 * g, dz=3 * (1 - g), up=lift)
        rise = max(0.0, min(1.0, (f - 0.78) / 0.15))
        a_l = 30 * max(0.0, min(1.0, (f - 0.08) / 0.12)) - 16
        a_r = 30 * max(0.0, min(1.0, (f - 0.2) / 0.12)) - 16
        sweep = max(0.0, min(1.0, (f - 0.86) / 0.14))
        act.append((f, _pose(r, lift=(FLOOR + 0.5) * (1 - core.ease_io(rise)), head=(8 - 14 * rise, 0, 0), fangs=4 + 20 * sweep,
                             ant=(a_l + 6 * sweep, a_r + 6 * sweep, -10 + 30 * sweep + 4 * math.sin(9 * f), -10 + 30 * sweep + 4 * math.sin(9 * f + 2)),
                             tl=(-14 + 14 * rise, -14 + 14 * rise, -6 + 6 * rise, -6 + 6 * rise), droop=(12 - 10 * rise, 10 - 8 * rise), feet=feet)))
    end = act[-1][1]
    return Move("wake", "NONE", 16, 40, 16, through=_mix(end, _pose(r), 0.4), track=0.0, rng=(0, 99), kinds=("intro",), damage=0, knock=0, plant=(),
                stages={"windup": [(0.5, _mix(_pose(r), flat, 0.6)), (1.0, flat)], "active": act, "recover": _back(r, end)}, lag=_lag())


# the ground (SPEC §35.10): the claws and knees, the head and its claws, and the tips of the feelers and the back legs
# stay out of the floor
LEG_GROUND = [([f"shin{k}"], [(f"thigh{k}", 0, 30), (f"shin{k}", 0, 40)]) for k in range(NL)]
HEAD_GROUND = [(["head", "fc_l", "fc_r"], [("head", 0, 40, -1)])]
WHIP_GROUND = [([f"ant_{n}2", f"ant_{n}1"], [(f"ant_{n}0", 0, 60), (f"ant_{n}1", 0, 40)]) for n in ("l", "r")] + \
              [([f"tl_{n}2", f"tl_{n}1"], [(f"tl_{n}0", 0, 60), (f"tl_{n}1", 0, 40)]) for n in ("l", "r")]
STANDS = {"bite": range(4, NL), "lasso": range(6, NL), "drop": (), "web": range(6, NL), "lash": range(0, 16), "hatch": (), "walls": (),
          "thousand": (), "wake": ()}


def _drift(r, clip, ramp=0.25):
    """Under a move, once it is made: its feelers keep tasting the air, its back legs sway and its poison claws work,
    faded in and out at the ends so it hands back to the idle exactly as it was."""
    c = r.clips[clip]
    L = c.length
    n = int(round(L / (2 * core.TICK)))

    def env(t):
        e = max(0.0, min(1.0, t / ramp, (L - t) / ramp))
        return e * e * (3 - 2 * e)

    def add(bone, f):
        old = c.tracks.get((bone, "rot"))
        keys = []
        for k in range(n + 1):
            t = min(L, k * L / n)
            base = list(R._interp(old, t)) if old else [0.0, 0.0, 0.0]
            d = f(t)
            keys.append((t, tuple(base[i] + env(t) * d[i] for i in range(3)), "cat"))
        c.tracks[(bone, "rot")] = keys
    w = 2 * math.pi
    for j, n_ in enumerate(("l", "r")):
        add(f"ant_{n_}0", lambda t, j=j: (5.0 * math.sin(w * 0.9 * t + 2.2 * j), 0.0, 6.0 * math.sin(w * 0.6 * t + 1.1 + 2.7 * j)))
        add(f"ant_{n_}1", lambda t, j=j: (4.0 * math.sin(w * 1.3 * t + 0.7 + j), 0.0, 0.0))
        add(f"tl_{n_}0", lambda t, j=j: (4.0 * math.sin(w * 0.5 * t + 1.9 * j), 0.0, 4.0 * math.sin(w * 0.4 * t + 0.5 + 2.4 * j)))
        add(f"fc_{n_}", lambda t, j=j: (0.0, 4.0 * (1 if j else -1) * math.sin(w * 1.1 * t), 0.0))
    add("head", lambda t: (1.5 * math.sin(w * 0.7 * t), 2.0 * math.sin(w * 0.45 * t + 0.8), 0.0))


def clips(r):
    _idle(r)
    _locomotion(r)
    _reactions(r)
    mv = [bite(r), lasso(r), drop(r), web(r), lash(r), hatch(r), walls_open(r), thousand(r), wake(r)]
    for m in mv:
        stage_clips(r, m)
        if STANDS[m.id]:
            _plant(r, core.clip_name(m), list(STANDS[m.id]))
        _drift(r, core.clip_name(m))
    everyday = [n for n in r.clips if n != "death"]
    core.ground_pass(r, LEG_GROUND, clips=everyday)
    core.ground_pass(r, HEAD_GROUND + WHIP_GROUND, clips=everyday)
    core.ground_pass(r, LEG_GROUND + HEAD_GROUND + WHIP_GROUND, clips=["death"], skip=())
    return mv


# Its sounds (SPEC §35.8): a silverfish's skitter and a spider's hiss, the clatter of its legs; chosen on paper, not heard
# in game.
SOUNDS = {
    "tell_light": [("entity.silverfish.ambient", 1.0, 0.6), ("entity.spider.ambient", 0.6, 0.9)],
    "tell_heavy": [("entity.spider.ambient", 1.2, 0.5), ("entity.silverfish.ambient", 1.2, 0.4), ("block.scaffolding.step", 0.8, 0.6)],
    "tell_perilous": [("entity.spider.ambient", 1.6, 0.4), ("entity.silverfish.hurt", 1.4, 0.4), ("entity.endermite.ambient", 1.2, 0.5)],
    "swing_light": [("entity.player.attack.sweep", 0.7, 1.2), ("entity.silverfish.step", 1.0, 0.7)],
    "swing_heavy": [("entity.player.attack.sweep", 1.0, 0.7), ("block.scaffolding.break", 1.0, 0.6)],
    "cast": [("block.cobweb.place", 1.4, 0.6), ("entity.spider.ambient", 0.8, 0.7)],
    "impact": [("block.scaffolding.fall", 1.4, 0.5), ("entity.silverfish.step", 1.6, 0.5), ("block.pointed_dripstone.land", 1.0, 0.6)],
    "roar": [("entity.silverfish.ambient", 1.8, 0.35), ("entity.spider.ambient", 1.6, 0.35), ("entity.endermite.ambient", 1.4, 0.4)],
    "step": [("entity.silverfish.step", 0.9, 0.6), ("entity.spider.step", 0.5, 0.8)],
}
MOVE_SOUNDS = {"web": {"swing": "cast"}, "hatch": {"swing": "roar", "impact": ""}, "walls": {"swing": "roar", "impact": ""}, "thousand": {"swing": "roar"},
               "wake": {"swing": "roar", "impact": ""}}

FIGHT = {
    "intro": {"pose": "idle", "move": "wake"},  # it lies flat; a shiver runs up its legs and it rises
    "phases": [
        {"pool": [
            {"move": "bite", "weight": 3, "when": "near", "then": [["lasso"], ["lasso", "bite"]]},
            {"move": "lasso", "weight": 2, "when": "near"},
            {"move": "drop", "weight": 2, "when": "far", "then": [["bite"]]},
            {"move": "web", "weight": 3, "when": "far"},
            {"move": "lash", "weight": 3, "when": "behind"},
        ]},
        # the walls open, as in DeepBestiary: its young, and every leg at once
        {"transition": "walls", "pool": [
            {"move": "bite", "weight": 3, "when": "near", "then": [["lasso"], ["lasso", "bite"]]},
            {"move": "lasso", "weight": 2, "when": "near"},
            {"move": "drop", "weight": 2, "when": "far", "then": [["bite", "lasso"]]},
            {"move": "web", "weight": 2, "when": "far"},
            {"move": "hatch", "weight": 1},
            {"move": "thousand", "weight": 2},
            {"move": "lash", "weight": 3, "when": "behind"},
        ]},
    ],
    "enrage": {"below": 0.15, "speed": 1.3},
}


def design():
    r = body()
    fr = core.frames(r, {})
    for k in range(NL):
        M, T = fr[SEG_OF[k // 2]]
        q = ik.model(REST_FOOT[k])
        LOCAL_FOOT[k] = (q[0] - T[0], q[1] - T[1], q[2] - T[2])  # the plates have no rest turn
    r.pack()
    mv = clips(r)
    return {"rig": r, "plan": None, "moves": mv, "fight": FIGHT, "paint": paint, "scale": SCALE, "half": 1.95, "sounds": SOUNDS,
            "move_sounds": MOVE_SOUNDS}
