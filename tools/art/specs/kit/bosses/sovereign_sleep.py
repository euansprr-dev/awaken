"""The Sovereign of Sleep (SPEC §35.11, docs/bosses/ROLLOUT.md): the Hollow Court's third Sovereign, a Flier. The Night
Owl, picked from its silhouette round 2026-10-29: "It never let anyone wake up."

A great owl nine blocks across the wings that hangs in the air asleep. Its eyes are shut under heavy feathered lids and
it dozes as it hovers: its head nods slowly forward and jerks up again. Its back and the tops of its wings are the
night, dark plum speckled with small pale stars; beneath it is pale dusk lilac barred with plum, its breast flecked
with chevrons. Its face is a pale heart-shaped disc rimmed in dark plum, with a small hooked beak and two long ear
tufts that sweep back like a crown, a pale crescent moon on its brow between them. Its legs are feathered to the toes,
the talons dark. Every primary feather of its wingtips is its own bone, so they spread like fingers.

Moves (SPEC §35.11): it rakes down at you with one foot's talons and then the other (LIGHT) and buffets you with a
wing; Night Dive (HEAVY: it climbs, folds its wings and stoops on you head first, throwing its talons forward at the
last); Lullaby (it wraps itself in its wings like a blanket and rocks, hooting softly, the throat swelling, and the
dark comes down); Lullabies (it fluffs itself up and shakes, and three small owls fly out of its feathers); Dream Fall
(it rises and beats down hard, three times, and the dreams fall); whoever is behind it, it looks at: its head turns
right round, and a wing comes back at them; and Never Wake (the signature: it hides its face in its wings, then throws
them wide and opens its eyes for the first time, and screams). Broken, it nods off and sinks in the air, its wings
drooping. Its death: it opens its eyes and looks at you, they close slowly, and it falls asleep out of the air onto
its back, its wings spread.
"""
import math

from kit import core
from kit.core import Move, keyed, stage_clips
import fauna_rig as R
from fauna_rig import Ramp, hexc, SIDES

ID = "sovereign_sleep"
SCALE = 3.2  # the def's PHANTOM model scale; the owl is modelled at true size and the hitbox set round its body

# The owl is built level, as it flies (its body along z, head at -z, the wings in its x-z plane), and hangs in the air at
# rest pitched nose up by CORE_PITCH, its head and legs turned back level, as a hovering bird does. 148 px from wingtip
# to wingtip (9 blocks). Value story: the pale face disc and the underparts lightest; the night of its back and wing
# tops darkest; the light is its eyes, the stars on its wings and the moon on its brow.
HOVER = 46.0
CORE_PITCH = -50
SIDES_ = (("l", 1), ("r", -1))
WING = (16, 18, 14)  # arm, forearm, hand
PRIM = ((-2.0, 14, 4), (1.5, 16, 12), (5.0, 16, 20), (8.5, 13, 28))  # each primary: z at the hand's tip, length, sweep back
LID_OPEN = 0.05  # a lid's scale when open


def body():
    r = R.Rig(ID, (512, 512))
    base = r.bone("base", (0, 24, 0))
    cb = base.bone("core", (0, -HOVER, 0), (CORE_PITCH, 0, 0))
    # the body, level: the breast forward and round, the back, the rump and the fluff under the belly
    cb.cube("breast", -10, -8, -14, 20, 18, 16)
    cb.cube("back", -9, -9, 0, 18, 16, 12)
    cb.cube("rump", -7, -6, 12, 14, 11, 6)
    cb.cube("fluff", -8, 9, -12, 16, 4, 18)
    cb.cube("mantle", -11, -10, -12, 22, 4, 14)  # the shoulders' feathers over the wing roots
    # the head, turned level on the neck: a broad round skull, the heart-shaped face disc and its dark rim, the beak, the
    # throat (it swells as it hoots), the shut eyes under their lids, the ear tufts and the moon on its brow
    hd = cb.bone("head", (0, -4, -14), (-CORE_PITCH, 0, 0))
    hd.cube("skull", -11, -19, -8, 22, 19, 16)
    hd.cube("crown", -9, -21, -6, 18, 2, 12)
    hd.cube("cheek_l", 9, -15, -9, 3, 13, 10)
    hd.cube("cheek_r", -12, -15, -9, 3, 13, 10)
    hd.cube("disc", -10, -17, -10, 20, 16, 2)
    hd.cube("ruff", -11, -2, -9.5, 22, 3, 3)
    hd.cube("ridge", -1.5, -15, -11, 3, 7, 1)
    for nm, sg in SIDES_:
        x = 2.5 if sg > 0 else -7.5
        hd.cube("eye_" + nm, x, -13, -10.5, 5, 5, 1)
        ld = hd.bone("lid_" + nm, (x + 2.5, -13.5, -11))
        ld.cube("lid_" + nm, -3, 0, -0.5, 6, 6, 1)
        tf = hd.bone("tuft_" + nm, (sg * 7, -20, -1), (-32, 0, -sg * 22))
        tf.cube("tuft_" + nm, -1.5, -10, -1.5, 3, 10, 3)
        tf.cube("tufttip_" + nm, -1, -14, -1, 2, 4, 2)
    hd.cube("moon0", -1, -20.5, -7, 2, 3, 1)
    hd.cube("moon1", 1, -21.5, -7, 1, 2, 1)
    bk = hd.bone("beak", (0, -8, -10.5))
    bk.cube("beak", -1.5, -1, -2, 3, 4, 3)
    bk.cube("hook", -1, 3, -2.5, 2, 2, 2)
    th = hd.bone("throat", (0, -1, -6))
    th.cube("throat", -5, -3, -2, 10, 5, 4)
    # the wings, level: arm, forearm and hand, the coverts over them, the long secondaries trailing off the forearm, and
    # four primaries on their own bones off the hand's tip
    for nm, sg in SIDES_:
        mir = sg < 0
        x0 = lambda w: 0 if sg > 0 else -w
        w0 = cb.bone(f"wing_{nm}0", (sg * 10, -5, -9))
        w0.cube(f"cov_{nm}0", x0(WING[0]), -1.5, -3, WING[0], 3, 12, mirror=mir)
        w0.cube(f"sec_{nm}0", x0(WING[0]), -0.5, 9, WING[0], 1, 6, mirror=mir)
        w1 = w0.bone(f"wing_{nm}1", (sg * WING[0], 0, 0))
        w1.cube(f"cov_{nm}1", x0(WING[1]), -1.5, -3, WING[1], 3, 10, mirror=mir)
        for k, (a, ln) in enumerate(((0, 18), (6, 20), (12, 17))):  # the secondaries' scalloped trailing edge
            w1.cube(f"sec_{nm}1{k}", (a if sg > 0 else -a - 6), -0.5, 7, 6, 1, ln - 7, mirror=mir)
        w2 = w1.bone(f"wing_{nm}2", (sg * WING[1], 0, 0))
        w2.cube(f"cov_{nm}2", x0(WING[2]), -1.5, -3, WING[2], 2, 9, mirror=mir)
        w2.cube(f"sec_{nm}2", x0(WING[2]), -0.5, 6, WING[2], 1, 8, mirror=mir)
        for k, (z, ln, sw) in enumerate(PRIM):
            pb = w2.bone(f"prim_{nm}{k}", (sg * WING[2], 0, z), (0, -sg * sw, 0))
            pb.cube(f"prim_{nm}{k}", x0(ln), -0.5, -1.5, ln, 1, 3, mirror=mir)
    # the tail: a fan, its outer feathers on their own bones
    tl = cb.bone("tail", (0, -1, 17))
    tl.cube("tail", -6, -0.5, 0, 12, 1, 15)
    for nm, sg in SIDES_:
        tb = tl.bone("tailf_" + nm, (sg * 5, 0, 1), (0, sg * 16, 0))
        tb.cube("tailf_" + nm, -2.5, -0.5, 0, 5, 1, 14)
    # the legs, hanging level: feathered thighs and shanks, the feet, two toes forward and two back with dark talons
    for nm, sg in SIDES_:
        mir = sg < 0
        tg = cb.bone("thigh_" + nm, (sg * 5, 8, -1), (-CORE_PITCH, 0, 0))
        tg.cube("thigh_" + nm, -3.5, -1, -3.5, 7, 10, 7, mirror=mir)
        sk = tg.bone("shank_" + nm, (0, 9, 0))
        sk.cube("shank_" + nm, -2, 0, -2, 4, 8, 4, mirror=mir)
        ft = sk.bone("foot_" + nm, (0, 8, 0))
        ft.cube("foot_" + nm, -3, 0, -3, 6, 2, 6, mirror=mir)
        tf = ft.bone("toesf_" + nm, (0, 1, -3))
        for k, x in enumerate((-2.5, 0.5)):
            tf.cube(f"toef{k}_" + nm, x, -0.5, -5, 2, 2, 5, mirror=mir)
            tf.cube(f"clawf{k}_" + nm, x + 0.5, 1, -6, 1, 2, 2, mirror=mir)
        tb = ft.bone("toesb_" + nm, (0, 1, 3))
        for k, x in enumerate((-2.5, 0.5)):
            tb.cube(f"toeb{k}_" + nm, x, -0.5, 0, 2, 2, 4, mirror=mir)
            tb.cube(f"clawb{k}_" + nm, x + 0.5, 1, 3, 1, 2, 2, mirror=mir)
    r.look = [("head", 0.6, 0.35)]
    r.shadow = 3.0
    r.size = 1.0
    # secondary parts (SPEC §35.10): the ear tufts, the primaries, the tail's outer feathers, the throat and the toes
    r.springs = ([("tuft_" + nm, 0.5, 2.4, 0.2) for nm, _ in SIDES_] + [(f"prim_{nm}{k}", 0.3 + 0.05 * k, 2.0, 0.2) for nm, _ in SIDES_ for k in range(4)]
                 + [("tailf_" + nm, 0.4, 2.0, 0.2) for nm, _ in SIDES_] + [("tail", 0.3, 1.6, 0.15)])
    r.guard = []
    return r


# ---------------------------------------------------------------------------------------------------------------------
# Paint

NIGHT = Ramp("#07060c", "#0e0a17", "#161024", "#1f1731", "#2a1f40", "#36294f", "#43335f")
PLUME = Ramp("#1d1424", "#2a1d33", "#392843", "#4a3554", "#5c4466", "#6f5579", "#84688d", "#9a7ea1", "#b195b6", "#c8aecb", "#ddc8de")
CREAM = Ramp("#4a3a44", "#665260", "#836d7c", "#a08999", "#bba6b4", "#d3c2cc", "#e6dae0", "#f3ecef")
HORN = Ramp("#120d0f", "#211819", "#332625", "#483733", "#5f4b45")
BEAK = Ramp("#3a3134", "#5a4c4e", "#7c6c6c", "#9e8f8c", "#bfb2ab")
EYE = ("#fff4fb", "#f6c6e6", "#d88ac0", "#9a4f88")
STAR = (246, 198, 230)


def paint(r, P_):
    """The night on top: the back and the wings' tops dark plum, speckled with small pale stars that glow, barred darker
    toward the tips; beneath, pale dusk lilac barred with plum, the breast flecked with chevrons; the face a pale disc
    darkening at its rim; the lids pale, a dark lash line along their edge; the eyes pink light under them."""
    of = lambda *ps: parts.cubes_of(r, *ps)
    h = R._h
    wing = of("cov_", "sec_", "prim_")
    bodyc = ["breast", "back", "rump", "fluff", "mantle", "tail", "tailf_l", "tailf_r"]
    for c in bodyc + wing:
        P_.paint(c, PLUME, 5.0, None, grad=1.0, rim=0.8, ao=0.9, noise=0.6, nscale=4)
    # the tops are the night: dark plum with stars that glow, and plum bars across the flight feathers
    for c in wing + ["back", "rump", "mantle", "tail", "tailf_l", "tailf_r"]:
        f, g = P_.face(c, "top"), P_.face(c, "top", glow=True)
        for i, j in f.each():
            k = h(c, i, j)
            v = 3.0 + 0.8 * ((i // 3 + j // 2) % 2) * (c.startswith(("prim_", "sec_", "tail")))
            f.set(i, j, NIGHT.at(v + (k % 3) * 0.3))
            if k % 23 == 0:
                f.set(i, j, hexc("#f3dcef", 255))
                g.set(i, j, STAR + (220,))
            elif k % 23 == 11 and k % 2:
                f.set(i, j, NIGHT.at(6.4))
    # beneath: pale lilac barred with plum
    for c in wing + ["tail", "tailf_l", "tailf_r"]:
        f = P_.face(c, "bottom")
        for i, j in f.each():
            bar = (i // 3) % 2 == 0 and c.startswith(("prim_", "sec_", "tail"))
            f.set(i, j, CREAM.at((3.0 if bar else 5.6) + (h(c, i, j) % 3) * 0.25))
    # the breast and the fluff: pale, flecked with plum chevrons
    for c in ("breast", "fluff"):
        for fname in ("bottom", "north", "west", "east"):
            f = P_.face(c, fname)
            for i, j in f.each():
                chev = (j + abs((i % 6) - 3)) % 5 == 0 and (i // 6 + j // 5) % 2 == 0
                f.set(i, j, CREAM.at((2.4 if chev else 5.4) + (h(c, fname, i, j) % 3) * 0.25 - 0.6 * (fname != "bottom")))
    # the legs: feathered pale to the toes, the talons dark horn
    for c in of("thigh_", "shank_", "foot_", "toef", "toeb"):
        P_.paint(c, CREAM, 5.0, None, grad=1.2, rim=0.8, ao=1.0, noise=0.6, nscale=3)
    for c in of("clawf", "clawb"):
        P_.paint(c, HORN, 2.4, None, grad=0.8, rim=1.0, ao=0.3)
    # the head: plum over the skull and crown, the cheeks barred, the disc pale and darkening to a plum rim
    for c in ("skull", "crown", "cheek_l", "cheek_r", "ruff") + tuple(of("tuft_", "tufttip_")):
        P_.paint(c, PLUME, 4.6, None, grad=1.0, rim=1.0, ao=0.8, noise=0.6, nscale=3)
    for c in ("skull", "crown"):
        f = P_.face(c, "top")
        for i, j in f.each():
            f.set(i, j, NIGHT.at(4.0 + (h(c, i, j) % 3) * 0.3))
    f = P_.face("disc", "north")
    cx, cy = (f.w - 1) / 2, (f.h - 1) / 2
    for i, j in f.each():
        # a heart: two lobes up top round the eyes, narrowing to the beak at the bottom
        lobe = min(math.hypot((i - (cx - 4.5)) / 5.5, (j - 5) / 6.5), math.hypot((i - (cx + 4.5)) / 5.5, (j - 5) / 6.5))
        chin = abs(i - cx) / max(1.0, (f.h - j) * 0.75) if j > 7 else 9
        d = min(lobe, chin)
        f.set(i, j, CREAM.at(6.6 - 1.2 * d) if d < 1.0 else PLUME.at(2.6 + (h("disc", i, j) % 3) * 0.3))
    for c in ("ridge",):
        P_.paint(c, CREAM, 6.0, None, grad=0.6, rim=0.8, ao=0.4)
    P_.paint("ruff", PLUME, 2.4, None, grad=0.6, rim=0.6, ao=0.6)
    for c in ("beak", "hook"):
        P_.paint(c, BEAK, 2.6, None, grad=1.0, rim=1.2, ao=0.5)
    P_.paint("throat", CREAM, 4.8, None, grad=1.0, rim=0.6, ao=0.8)
    # the eyes: a great dark pupil (no glow, so it stays black when they open) in a thin ring of glowing pink iris, one
    # white catchlight; the lids pale with a dark lash line along the bottom edge
    for nm in "lr":
        f, g = P_.face("eye_" + nm, "north"), P_.face("eye_" + nm, "north", glow=True)
        for i, j in f.each():
            d = math.hypot(i - 2, j - 2)
            if (i, j) == (1, 1):
                f.set(i, j, hexc("#ffffff", 255))
                g.set(i, j, hexc("#ffffff", 255))
            elif d < 1.5:
                f.set(i, j, hexc("#14060f", 255))
            elif d < 2.5:
                f.set(i, j, hexc(EYE[1], 255))
                g.set(i, j, hexc(EYE[1], 245))
            else:
                f.set(i, j, hexc(EYE[3], 255))
                g.set(i, j, hexc(EYE[2], 160))
        P_.paint("lid_" + nm, CREAM, 5.8, None, grad=0.8, rim=0.6, ao=0.4)
        f = P_.face("lid_" + nm, "north")
        for i in range(f.w):
            f.set(i, f.h - 1, PLUME.at(1.2))
            if i % 2:
                f.set(i, f.h - 2, CREAM.at(4.6))
    # the moon on its brow
    for c in ("moon0", "moon1"):
        for fname in SIDES + ("top",):
            f, g = P_.face(c, fname), P_.face(c, fname, glow=True)
            for i, j in f.each():
                f.set(i, j, hexc(EYE[0], 255))
                g.set(i, j, hexc(EYE[1], 210))
    return P_.skin, P_.glow


from kit import parts  # noqa: E402  (painting helpers)


# ---------------------------------------------------------------------------------------------------------------------
# Posing

def _wing(p, nm, sg, lift=0.0, curl=0.0, sweep=0.0, fold=0.0, fan=0.0, twist=0.0, wave=0.0, ph=0.0):
    """One wing: lift raises it at the root (degrees, + up), curl bends it up along its length (+ up, the degrees spread
    toward the tip), sweep turns it back at the root (- forward), fold folds it against the body (1: folded, the forearm
    back along the side and the hand along it), fan spreads the primaries (+) or closes them (-), twist turns its leading
    edge down, and wave runs a beat out along it (its amplitude, at phase ph; + up at ph = pi/2)."""
    a = [lift + curl * 0.2, curl * 0.35, curl * 0.45]
    tw = [twist * 0.4, twist * 0.7, twist]
    if wave:
        for k in range(3):
            a[k] += wave * (0.55, 0.3, 0.3)[k] * math.sin(ph - 0.45 * k)
            tw[k] -= 0.3 * wave * (k + 1) / 3 * math.cos(ph - 0.45 * k)
    ys = (-sg * (sweep + 62 * fold), sg * 150 * fold, -sg * 140 * fold)
    for k in range(3):
        p[f"wing_{nm}{k}"] = (tw[k], ys[k], -sg * a[k])
    for k in range(4):
        p[f"prim_{nm}{k}"] = (0, -sg * (fan * 6 * k - 10 * fold * k), 0)


def _legs(p, reach=0.0, tuck=0.0, grip=0.0, spread=0.0, side=None):
    """The legs: reach swings them forward (+, the talons thrown ahead) or back, tuck folds them up under it, grip
    closes the talons (1) or opens them wide (-1), spread opens them apart. side: a dict of per-side extras."""
    for nm, sg in SIDES_:
        e = (side or {}).get(nm, {})
        rch = reach + e.get("reach", 0.0)
        tk = tuck + e.get("tuck", 0.0)
        gp = grip + e.get("grip", 0.0)
        p["thigh_" + nm] = (-rch - 30 * tk, 0, sg * (spread + e.get("spread", 0.0)))
        p["shank_" + nm] = (70 * tk, 0, 0)
        p["foot_" + nm] = (-30 * tk + 8 * rch / 40, 0, 0)
        p["toesf_" + nm] = (40 * gp, 0, 0)
        p["toesb_" + nm] = (-40 * gp, 0, 0)


def _pose(pitch=0.0, yaw=0.0, roll=0.0, at=(0.0, 0.0, 0.0), wings=None, wl=None, wr=None, head=(0, 0, 0), beak=0.0, throat=0.0, legs=None,
          tail=(0.0, 0.0), tufts=0.0):
    """The body: pitch (+ nose down, on top of its hovering pitch), yaw (+ to its left), roll (+ its left wing down), at
    (x to its left, up, z back); wings: _wing arguments for both, or wl and wr each its own; head (pitch + down, yaw +
    to its left, roll), beak opens, throat swells forward, legs: _legs arguments; tail (lift + up, fan); tufts lifts the
    ear tufts (+) or lays them back (-)."""
    p = {"core": {"rot": (pitch, yaw, roll), "pos": tuple(at)}, "head": tuple(head), "beak": (-beak, 0, 0), "throat": (0, 0, 0),
         "tail": (-tail[0], 0, 0)}
    if throat:
        p["throat"] = {"rot": (0, 0, 0), "pos": (0, 0, -throat)}
    _wing(p, "l", 1, **(wl if wl is not None else wings or {}))
    _wing(p, "r", -1, **(wr if wr is not None else wings or {}))
    _legs(p, **(legs or {}))
    for nm, sg in SIDES_:
        p["tailf_" + nm] = (0, sg * 10 * tail[1], 0)
        p["tuft_" + nm] = (tufts * 20, 0, -sg * tufts * 6)
    return p


def _mix(a, b, f):
    """Between two keys (the bones of either), f of the way from a to b."""
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


REST = dict(wings=dict(lift=24, curl=10, fan=0.6))  # hanging in the air, its wings held out between beats


def _rest(**kw):
    return _pose(**{**REST, **kw})


def _beat(u, amp=34.0, lift=24.0, fan=0.6, sweep=0.0, curl=10.0):
    """A wingbeat at phase u (0..1, from the top of the stroke): the wings' arguments."""
    ph = 2 * math.pi * u + math.pi / 2
    return dict(lift=lift, curl=curl + 10 * math.sin(ph - 1.0), wave=amp, ph=ph, fan=fan + 0.5 * math.cos(ph), sweep=sweep)


def _lids(r, clip, pts):
    """The lids' scale (1 shut, LID_OPEN open), [(seconds, scale)] as scale tracks."""
    c = r.clips[clip]
    for nm in "lr":
        c.scale("lid_" + nm, [(t, 1.0, v, 1.0) for t, v in pts], "lin")


def _staged(mv, stage, f):
    W, A = mv.seconds("windup"), mv.seconds("active")
    return {"windup": 0.0, "active": W, "recover": W + A}[stage] + f * mv.seconds(stage)


def _track(r, mv, bones, pts, axes=(1, 1, 1), interp="cat"):
    """A move's scale keys for some bones: [(stage, fraction, scale)] in its stages' time (1 at both ends)."""
    c = r.clips[core.clip_name(mv)]
    ks = [(0.0, 1.0)] + [(_staged(mv, s, f), v) for s, f, v in pts] + [(c.length, 1.0)]
    for b in bones:
        c.scale(b, [(t, *(v if a else 1.0 for a in axes)) for t, v in sorted(ks)], interp)


# ---------------------------------------------------------------------------------------------------------------------
# Clips

def _idle(r):
    """It hangs in the air asleep: slow deep beats, its body rising on each downstroke; its head nods slowly forward as
    it drops off, and jerks up again, and it shakes its head; later it turns its head slowly right round to one side
    and back; its legs dangle and its toes flex; its tail fans; its throat swells with a soft hoot."""
    L, n = 8.0, 80
    keys = []
    for k in range(n + 1):
        t = L * k / n
        u = (t / 1.6) % 1.0
        ph = 2 * math.pi * u
        # dozing: the head sinks over 2.6 s, snaps up, a shake; then a slow look round from 4.6 s to 7 s
        if t < 2.6:
            hp, hy, hr = 26 * (t / 2.6) ** 1.6, 0.0, 4 * (t / 2.6)
        elif t < 2.85:
            f = (t - 2.6) / 0.25
            hp, hy, hr = 26 * (1 - f) - 6 * f, 0.0, 4 * (1 - f)
        elif t < 3.6:
            f = (t - 2.85) / 0.75
            hp, hy, hr = -6 * (1 - f), 10 * math.sin(f * 3 * 2 * math.pi) * (1 - f), 0.0
        elif t < 4.6:
            hp, hy, hr = 0.0, 0.0, 0.0
        else:
            f = (t - 4.6) / 3.4
            sn = max(0.0, math.sin(math.pi * f))
            hp, hy, hr = 2 * sn, 85 * sn ** 1.5, 6 * sn
        hoot = max(0.0, math.sin(2 * math.pi * (t - 3.7) / 1.4)) if 3.7 < t < 4.4 else 0.0
        keys.append((t, _pose(pitch=-2 * math.cos(ph), roll=1.5 * math.sin(2 * math.pi * t / L), at=(0, 2.2 * math.sin(ph - 0.9), 0),
                              wings=_beat(u), head=(hp - 2 * math.cos(ph), hy, hr), throat=1.6 * hoot,
                              legs=dict(reach=4 + 4 * math.sin(ph - 1.6), grip=0.25 + 0.2 * math.sin(2 * math.pi * t / 2.0),
                                        side={"l": dict(reach=3 * math.sin(2 * math.pi * t / L))}),
                              tail=(4 * math.sin(ph - 1.8), 0.3 + 0.2 * math.sin(2 * math.pi * t / 4)), tufts=0.15 * math.sin(ph - 2.0))))
    keyed(r, "idle", L, keys, loop=True)


def _flight(r):
    """Flying: it levels out, nose down, and its beats go long and low, the legs tucked back under it; it banks into its
    turns, its head held level."""
    L, n = 1.2, 12
    keys = []
    for k in range(n + 1):
        u = k / n
        keys.append((L * u, _pose(pitch=34, at=(0, -2 * math.sin(2 * math.pi * u - 0.9), 0), wings=_beat(u, amp=30, lift=6, fan=0.2, sweep=4),
                                  head=(-30, 0, 0), legs=dict(reach=-40, tuck=0.6, grip=0.6), tail=(-6, 0.2), tufts=-0.6)))
    keyed(r, "fly", L, keys, loop=True)
    for nm, sg in (("turn_l", 1), ("turn_r", -1)):
        keys = []
        for k in range(n + 1):
            u = k / n
            b = _beat(u, amp=24)
            keys.append((1.2 * u, _pose(roll=14 * sg, yaw=-4 * sg, at=(sg * 2, 0, 0), wl={**b, "lift": 14 - 6 * sg}, wr={**b, "lift": 14 + 6 * sg},
                                        head=(0, 10 * sg, -10 * sg), legs=dict(reach=4), tail=(0, 0.8))))
        keyed(r, nm, 1.2, keys, loop=True)


def _sag(low=1.0):
    """Its stance broken: it nods off and sinks in the air, its head hanging, its wings drooping, its legs dangling."""
    return _pose(pitch=8 * low, roll=5 * low, at=(0, -12 * low, 0), wings=dict(lift=-24 * low, curl=-20 * low, fan=-0.5 * low, twist=6 * low),
                 head=(34 * low, 0, 10 * low), legs=dict(reach=10 * low, grip=-0.3 * low), tail=(-10 * low, 0.0), tufts=-0.8 * low)


def _reactions(r):
    """Struck, it jolts back, its wings flinching up and its head jerking; from the side it rolls away. Broken, it nods
    off and sinks; it rises out of it with one great beat. Its death: it opens its eyes and looks at you, they close
    slowly, its head falls, and it drops out of the air onto its back, its wings spread on the floor."""
    hit = _pose(pitch=-10, at=(0, 2, 3), wings=dict(lift=34, curl=24, fan=1.2, twist=-8), head=(-14, 0, 0), beak=8, legs=dict(reach=16, grip=-0.6),
                tail=(8, 0.8), tufts=0.8)
    back = _rest(pitch=3, at=(0, -1, 0), head=(4, 0, 0), legs=dict(reach=2))
    keyed(r, "hurt", 0.6, [(0, _rest()), (0.08, hit), (0.32, back), (0.6, _rest())])
    for nm, sg in (("hurt_l", 1), ("hurt_r", -1)):
        h = _pose(roll=-18 * sg, yaw=-8 * sg, pitch=-4, at=(-3 * sg, 2, 1), wl=dict(lift=30 if sg > 0 else -6, curl=20 if sg > 0 else -8, fan=1),
                  wr=dict(lift=30 if sg < 0 else -6, curl=20 if sg < 0 else -8, fan=1), head=(-6, 22 * sg, 10 * sg), beak=6, legs=dict(reach=10, spread=6),
                  tufts=0.6)
        b = _rest(roll=4 * sg, at=(0.5 * sg, -0.5, 0), head=(0, -6 * sg, 0))
        keyed(r, nm, 0.65, [(0, _rest()), (0.08, h), (0.34, b), (0.65, _rest())])
    keyed(r, "kneel", 0.9, [(0, _rest()), (0.3, _pose(pitch=-4, at=(0, 2, 0), wings=dict(lift=24, curl=14, fan=0.8), head=(-8, 0, 0))),
                            (0.65, _sag(1.08)), (0.9, _sag())])
    up_ = _pose(pitch=-6, at=(0, -8, 0), wings=dict(lift=62, curl=24, fan=1.4, twist=-8), head=(-6, 0, 0), legs=dict(reach=6), tail=(6, 1.0), tufts=0.6)
    down = _pose(pitch=4, at=(0, 4, 0), wings=dict(lift=-36, curl=-30, fan=0.8, twist=10), head=(4, 0, 0), legs=dict(reach=-6), tail=(-4, 0.6))
    keyed(r, "rise", 1.1, [(0, _sag()), (0.4, up_), (0.75, down), (1.1, _rest())])
    look = _rest(pitch=-4, head=(-6, 0, 0), wings=dict(lift=20, curl=8, fan=0.6), tufts=0.8)
    droop = _pose(pitch=6, at=(0, -4, 0), wings=dict(lift=-10, curl=-14, fan=-0.3), head=(30, 0, 12), legs=dict(reach=8, grip=-0.2), tufts=-0.6)
    fall = _pose(pitch=-40, roll=12, at=(0, -19, 4), wings=dict(lift=40, curl=30, fan=0.4, twist=-10), head=(-10, 0, 10), legs=dict(reach=30, grip=0.2),
                 tail=(10, 0.3), tufts=-0.8)
    lie = _laid(pitch=-125, roll=6, at=(0, 0, 6), wings=dict(lift=8, curl=-6, fan=0.9, sweep=-6), head=(-30, 0, 20), legs=dict(reach=10, tuck=0.6, grip=1.0),
                tail=(0, 0.6), tufts=-1.0)
    keyed(r, "death", 4.6, [(0, _rest()), (0.4, look), (1.6, look), (2.3, droop), (3.0, fall), (3.55, lie), (3.8, _mix(lie, fall, -0.04)), (4.6, lie)])
    _lids(r, "death", [(0, 1.0), (0.35, 1.0), (0.6, LID_OPEN), (1.5, LID_OPEN), (2.4, 1.0), (4.6, 1.0)])


def _laid(**kw):
    """A key lying on the floor: the body set down so its lowest part rests on it."""
    p = _pose(**kw)
    rig = _RIG[0]
    low = core.lowest(rig, core.frames(rig, core.as_sample(p)), [b.name for b in rig.bones if b.cubes])
    at = kw.get("at", (0, 0, 0))
    return _pose(**{**kw, "at": (at[0], at[1] + (low - 24.0) - 0.3, at[2])})


_RIG = [None]


# ---------------------------------------------------------------------------------------------------------------------
# Moves

def _lag(**kw):
    out = {"head": 0.05, "beak": 0.04}
    out.update(kw)
    return out


def talon(side="r", mid="talon"):
    """The Talon: it rears back in the air, its wings lifting, and draws one foot up with its talons open; then it
    lunges and rakes the foot down and across in front of it, the talons closing (LIGHT)."""
    sg = 1 if side == "l" else -1
    other = "r" if side == "l" else "l"
    cock = _pose(pitch=-18, yaw=8 * sg, roll=-6 * sg, at=(0, 4, 3), wings=dict(lift=44, curl=20, fan=1.2, twist=-8), head=(14, -6 * sg, 0), beak=4,
                 legs=dict(reach=10, side={side: dict(reach=46, grip=-1.0, spread=4), other: dict(reach=-10, grip=0.6)}), tail=(10, 0.9), tufts=0.6)
    strike = _pose(pitch=10, yaw=-10 * sg, roll=8 * sg, at=(0, -2, -6), wings=dict(lift=-20, curl=-18, fan=0.6, twist=8), head=(-6, 8 * sg, 0), beak=8,
                   legs=dict(reach=6, side={side: dict(reach=58, grip=0.4, spread=-6), other: dict(reach=-14, grip=0.6)}), tail=(-4, 0.5), tufts=0.2)
    held = _pose(pitch=12, yaw=-12 * sg, roll=9 * sg, at=(0, -2.5, -6.5), wings=dict(lift=-24, curl=-20, fan=0.5, twist=8), head=(-4, 9 * sg, 0), beak=6,
                 legs=dict(reach=4, side={side: dict(reach=50, grip=1.0, spread=-8), other: dict(reach=-14, grip=0.6)}), tail=(-6, 0.4))
    return Move(mid, "LIGHT", 10, 4, 14, title="Talon", track=8.0, rng=(0, 5.0), kinds=("combo", "near"), damage=1.0, knock=0.6, plant=(),
                hit=[("toesf_" + side, (0, 0, -5), 4.0), ("foot_" + side, (0, 1, 0), 4.0)], through=_mix(held, _rest(), -0.05), lag=_lag(),
                stages={"windup": [(0.3, _mix(_rest(), cock, 0.62)), (1.0, cock)], "active": [(0.45, strike), (1.0, held)],
                        "recover": [(0.45, _mix(held, _rest(), 0.45)), (0.7, _mix(held, _rest(), 0.8))]})


def buffet(r):
    """The Buffet: it turns in the air and draws its right wing far back, then sweeps it round and across in front of it
    in one heavy beat, the primaries spread like a hand."""
    cock = _pose(yaw=-34, roll=-16, pitch=-12, at=(0, 3, 3), wl=dict(lift=34, sweep=-24, curl=16, fan=1.2, twist=-8),
                 wr=dict(lift=44, sweep=62, curl=22, fan=1.8, twist=-14), head=(8, 30, 6), legs=dict(reach=24, spread=10, grip=-0.6), tail=(10, 1.4), tufts=0.8)
    sweep = _pose(yaw=22, roll=12, pitch=6, at=(-2, -2, -4), wl=dict(lift=2, curl=0, fan=0.4), wr=dict(lift=4, sweep=-50, curl=-10, fan=1.6, twist=10),
                  head=(-2, -14, 0), beak=6, legs=dict(reach=8), tail=(-2, 0.6))
    held = _pose(yaw=28, roll=14, pitch=8, at=(-2.5, -2.5, -4.5), wl=dict(lift=2, fan=0.4), wr=dict(lift=0, sweep=-58, curl=-14, fan=1.4, twist=12),
                 head=(0, -18, 0), beak=4, legs=dict(reach=8), tail=(-4, 0.5))
    return Move("buffet", "LIGHT", 12, 5, 16, title="Buffet", track=6.0, rng=(0, 6.0), kinds=("combo", "near"), damage=1.1, knock=1.0, plant=(),
                hit=[("prim_r2", (-10, 0, 0), 5.0), ("wing_r2", (-8, 0, 4), 6.0), ("wing_r1", (-9, 0, 6), 6.0)], through=_mix(held, _rest(), -0.05),
                lag=_lag(), stages={"windup": [(0.3, _mix(_rest(), cock, 0.4)), (1.0, cock)], "active": [(0.5, sweep), (1.0, held)],
                                    "recover": [(0.45, _mix(held, _rest(), 0.45)), (0.7, _mix(held, _rest(), 0.8))]})


def dive(r):
    """Night Dive (HEAVY, a gap closer): two strong beats carry it up, its head tipping to keep you in sight; at the
    top it folds its wings and drops on you head first, and at the last it throws its talons forward, open, its wings
    flaring wide to brake."""
    up1 = _pose(pitch=-14, at=(0, 6, 2), wings=dict(lift=60, curl=26, fan=1.4, twist=-8), head=(18, 0, 0), legs=dict(reach=-10, tuck=0.4), tail=(8, 1.0))
    up2 = _pose(pitch=-8, at=(0, 12, 3), wings=dict(lift=-34, curl=-26, fan=0.8, twist=10), head=(14, 0, 0), legs=dict(reach=-14, tuck=0.5), tail=(-4, 0.8))
    top = _pose(pitch=24, at=(0, 16, 2), wings=dict(lift=40, curl=10, fold=0.55, fan=-0.5), head=(-14, 0, 0), legs=dict(reach=-30, tuck=0.8, grip=0.8),
                tail=(4, 0.0), tufts=-1.0)
    drop = _pose(pitch=62, at=(0, 6, -10), wings=dict(lift=30, fold=0.8, fan=-0.8), head=(-52, 0, 0), beak=4, legs=dict(reach=-30, tuck=0.8, grip=0.8),
                 tail=(0, 0.0), tufts=-1.0)
    strike = _pose(pitch=-12, at=(0, -8, -20), wings=dict(lift=46, curl=26, sweep=-10, fan=1.6, twist=-14), head=(6, 0, 0), beak=10,
                   legs=dict(reach=70, grip=-1.0, spread=6), tail=(12, 1.2), tufts=0.4)
    clutch = _pose(pitch=-8, at=(0, -9, -21), wings=dict(lift=30, curl=16, sweep=-6, fan=1.4, twist=-10), head=(8, 0, 0), beak=6,
                   legs=dict(reach=62, grip=1.0, spread=4), tail=(10, 1.0), tufts=0.2)
    return Move("dive", "HEAVY", 22, 12, 20, title="Night Dive", hold=6, track=6.0, lock=8, advance=8.0, ability="Night Dive", rng=(5, 18), kinds=("gap",),
                damage=1.4, knock=1.4, plant=(), hit=[("toesf_l", (0, 0, -5), 5.0), ("toesf_r", (0, 0, -5), 5.0), ("core", (0, 0, 0), 12.0)],
                through=clutch, lag=_lag(),
                stages={"windup": [(0.3, up1), (0.6, up2), (1.0, top)], "active": [(0.35, drop), (0.75, strike), (1.0, clutch)],
                        "recover": [(0.45, _mix(clutch, _rest(), 0.5)), (0.72, _mix(clutch, _rest(), 0.85))]})


def lullaby(r):
    """Lullaby: it wraps itself in its wings like a blanket, its head bowed into them, and rocks slowly from side to side,
    hooting softly, its throat swelling with each hoot, while the dark comes down."""
    wrap = lambda a: _pose(pitch=-6, roll=8 * a, yaw=4 * a, at=(1.5 * a, 1, 0), wings=dict(lift=-30, sweep=-70, curl=-40, fan=-0.6, twist=20),
                           head=(16, 6 * a, 10 * a), legs=dict(reach=8, tuck=0.4, grip=0.5), tail=(-6, 0.0), tufts=-0.4)
    act = [(0.12, wrap(0)), (0.3, wrap(1)), (0.55, wrap(-1)), (0.8, wrap(1)), (1.0, wrap(0))]
    mv = Move("lullaby", "NONE", 16, 30, 16, title="Lullaby", track=0.0, ability="Lullaby", rng=(0, 16), kinds=("area",), damage=0, knock=0, plant=(),
              through=_mix(wrap(0), _rest(), 0.25), lag=_lag(),
              stages={"windup": [(0.45, _mix(_rest(), wrap(0), 0.5)), (1.0, wrap(0))], "active": act,
                      "recover": [(0.45, _mix(wrap(0), _rest(), 0.45)), (0.7, _mix(wrap(0), _rest(), 0.8))]})
    mv._throat = [(0.2, 0.0), (0.3, 2.2), (0.42, 0.0), (0.55, 2.2), (0.67, 0.0), (0.8, 2.2), (0.92, 0.0)]
    return mv


def lullabies(r):
    """Lullabies (summoning): it fluffs itself up until it is round, and shakes like a wet bird, its wings shivering;
    then it throws its wings open and three small owls fly out of its feathers."""
    fluff = lambda a: _pose(pitch=-4, roll=6 * a, at=(0, 1, 0), wings=dict(lift=-10 + 6 * a, curl=-8, fan=0.2 + 0.4 * a, twist=6), head=(4, 18 * a, 14 * a),
                            legs=dict(reach=6, grip=0.6), tail=(4 * a, 1.0), tufts=0.8)
    open_ = _pose(pitch=-14, at=(0, 4, 2), wings=dict(lift=58, curl=24, fan=1.6, twist=-10), head=(-10, 0, 0), beak=8, legs=dict(reach=16, grip=-0.8),
                  tail=(10, 1.4), tufts=1.0)
    act = [(0.1, fluff(1)), (0.2, fluff(-1)), (0.3, fluff(1)), (0.4, fluff(-1)), (0.5, fluff(1)), (0.6, fluff(0)), (0.78, open_), (1.0, _mix(open_, _rest(), 0.2))]
    mv = Move("lullabies", "NONE", 14, 28, 16, title="Lullabies", track=0.0, ability="Lullabies", rng=(0, 99), kinds=("summon",), damage=0, knock=0,
              plant=(), through=_mix(open_, _rest(), 0.4), lag=_lag(),
              stages={"windup": [(0.5, _mix(_rest(), fluff(0), 0.5)), (1.0, fluff(0))], "active": act,
                      "recover": [(0.45, _mix(open_, _rest(), 0.55)), (0.7, _mix(open_, _rest(), 0.85))]})
    mv._fluff = [("windup", 1.0, 1.14), ("active", 0.6, 1.16), ("active", 0.78, 1.0)]
    return mv


def dreamfall(r):
    """Dream Fall: it rises with its wings high over its back and beats down hard, three times, each beat throwing it up
    and its primaries spreading, and the dreams fall round you."""
    high = _pose(pitch=-10, at=(0, 6, 1), wings=dict(lift=66, curl=26, fan=1.4, twist=-10), head=(10, 0, 0), legs=dict(reach=8, grip=-0.4), tail=(6, 1.2),
                 tufts=0.6)
    low = lambda k: _pose(pitch=6, at=(0, 2 + 3 * k, 0), wings=dict(lift=-38, curl=-30, fan=1.8, twist=12), head=(2, 0, 0), beak=6, legs=dict(reach=14, grip=-0.8),
                          tail=(-6, 1.2), tufts=0.2)
    hi = lambda k: _pose(pitch=-8, at=(0, 4 + 3 * k, 1), wings=dict(lift=58, curl=22, fan=1.2, twist=-8), head=(8, 0, 0), legs=dict(reach=10), tail=(6, 1.0))
    act = [(0.14, low(0)), (0.33, hi(1)), (0.47, low(1)), (0.66, hi(2)), (0.8, low(2)), (1.0, _mix(low(2), _rest(), 0.2))]
    return Move("dreamfall", "NONE", 16, 26, 18, title="Dream Fall", track=0.0, ability="Dream Fall", rng=(0, 18), kinds=("ranged", "area"), damage=0, knock=0,
                plant=(), through=_mix(low(2), _rest(), 0.4), lag=_lag(),
                stages={"windup": [(0.5, _mix(_rest(), high, 0.55)), (1.0, high)], "active": act,
                        "recover": [(0.45, _mix(low(2), _rest(), 0.55)), (0.7, _mix(low(2), _rest(), 0.85))]})


def swivel(r):
    """For whoever is behind it: its head turns slowly right round until its face looks back over its back; then it
    rolls and swings a wing back at them."""
    look = _rest(head=(0, 170, 0), tufts=0.8, wings=dict(lift=18, curl=8, fan=0.6))
    cock = _pose(roll=-18, yaw=-16, pitch=-6, at=(0, 3, 0), wl=dict(lift=44, sweep=-40, curl=22, fan=1.6, twist=-12), wr=dict(lift=30, curl=12, fan=1.0),
                 head=(0, 168, 0), legs=dict(reach=20, spread=8, grip=-0.6), tail=(10, 1.4), tufts=0.8)
    swing = _pose(roll=14, yaw=16, at=(1, -1, 2), wl=dict(lift=0, sweep=62, curl=-10, fan=1.6, twist=10), wr=dict(lift=8, fan=0.5), head=(0, 166, 0), beak=6,
                  legs=dict(reach=2), tail=(-2, 0.8))
    held = _pose(roll=16, yaw=18, at=(1, -1.5, 2), wl=dict(lift=-4, sweep=66, curl=-14, fan=1.4, twist=12), wr=dict(lift=8, fan=0.5), head=(0, 160, 0),
                 beak=4, legs=dict(reach=2), tail=(-4, 0.6))
    return Move("swivel", "LIGHT", 16, 5, 18, title="Over Its Back", track=0.0, rng=(0, 5.0), kinds=("behind",), damage=1.0, knock=1.1, plant=(),
                hit=[("prim_l2", (10, 0, 0), 5.0), ("wing_l2", (8, 0, 4), 6.0), ("wing_l1", (9, 0, 6), 6.0)], through=_mix(held, _rest(), -0.04),
                lag={"head": 0.0, "beak": 0.0},
                stages={"windup": [(0.3, _mix(_rest(), look, 0.5)), (0.55, look), (1.0, cock)], "active": [(0.5, swing), (1.0, held)],
                        "recover": [(0.35, _mix(held, look, 0.6)), (0.72, _mix(look, _rest(), 0.5))]})


def neverwake(r):
    """Never Wake (PERILOUS, the signature): it hides its face in its wings, folding them round its head and bowing into
    them, and trembles; then it throws them wide, lifts its head, opens its eyes for the first time and screams."""
    hide = lambda a: _pose(pitch=-4, roll=2 * a, at=(0, 2, 0), wings=dict(lift=6, sweep=-80, curl=-30, fan=-0.4, twist=24), head=(30, 2 * a, 2 * a),
                           legs=dict(reach=6, tuck=0.3, grip=0.8), tail=(-4, 0.2), tufts=-1.0)
    wide = lambda a: _pose(pitch=-16, at=(0, 6, 3), wings=dict(lift=46 + 3 * a, curl=20, sweep=6, fan=1.8, twist=-12), head=(-20, 0, 3 * a), beak=16,
                           legs=dict(reach=24, grip=-1.0, spread=8), tail=(12, 1.6), tufts=1.0)
    wind = [(0.25, hide(0)), (0.45, hide(1)), (0.6, hide(-1)), (0.72, hide(1)), (0.84, hide(-1)), (0.93, hide(1)), (1.0, hide(0))]
    act = [(0.2, wide(0)), (0.45, wide(1)), (0.7, wide(-1)), (1.0, wide(0))]
    mv = Move("neverwake", "PERILOUS", 30, 14, 28, title="Never Wake", signature=True, track=0.0, ability="Never Wake", rng=(0, 15), kinds=("area",),
              damage=0, knock=0, plant=(), through=_mix(wide(0), _rest(), 0.2), lag=_lag(),
              stages={"windup": wind, "active": act, "recover": [(0.35, _mix(wide(0), _rest(), 0.45)), (0.7, _mix(wide(0), _rest(), 0.85))]})
    mv._lids = [("active", 0.0, 1.0), ("active", 0.18, LID_OPEN), ("recover", 0.5, LID_OPEN), ("recover", 0.95, 1.0)]
    mv._throat = [("active", 0.1, 0.0), ("active", 0.3, 2.6), ("active", 1.0, 1.6), ("recover", 0.5, 0.0)]
    return mv


def wake(r):
    """Its intro: it hangs asleep, wrapped in its wings with its head tucked down into them; its tufts lift; it raises
    its head (its eyes still shut) and turns it toward you; it shakes its feathers out, and unfolds its wings with one
    great beat."""
    tucked = _pose(pitch=-2, at=(0, -4, 0), wings=dict(lift=-30, sweep=-70, curl=-40, fan=-0.6, twist=20), head=(34, 0, 14), legs=dict(reach=6, tuck=0.5, grip=0.8),
                   tail=(-8, 0.0), tufts=-1.0)
    lift_ = _pose(pitch=-2, at=(0, -3, 0), wings=dict(lift=-26, sweep=-62, curl=-36, fan=-0.5, twist=18), head=(-4, 0, 0), legs=dict(reach=6, tuck=0.5, grip=0.8),
                  tail=(-6, 0.2), tufts=0.8)
    shake = lambda a: _pose(pitch=-2, roll=5 * a, at=(0, -2, 0), wings=dict(lift=-12, sweep=-30, curl=-20, fan=0.2, twist=8), head=(0, 14 * a, 10 * a),
                            legs=dict(reach=6, grip=0.4), tail=(2 * a, 0.6), tufts=0.8)
    spread = _pose(pitch=-12, at=(0, 4, 2), wings=dict(lift=60, curl=24, fan=1.5, twist=-10), head=(-6, 0, 0), beak=4, legs=dict(reach=10, grip=-0.4),
                   tail=(8, 1.2), tufts=1.0)
    beat = _pose(pitch=4, at=(0, 6, 0), wings=dict(lift=-30, curl=-26, fan=1.0, twist=10), head=(0, 0, 0), legs=dict(reach=6), tail=(-4, 0.8), tufts=0.6)
    mv = Move("wake", "NONE", 16, 36, 16, track=0.0, rng=(0, 99), kinds=("intro",), damage=0, knock=0, plant=(), lag=_lag(), through=_mix(beat, _rest(), 0.3),
              stages={"windup": [(0.5, _mix(_rest(), tucked, 0.7)), (1.0, tucked)],
                      "active": [(0.18, tucked), (0.36, lift_), (0.48, shake(1)), (0.56, shake(-1)), (0.64, shake(1)), (0.72, shake(0)), (0.86, spread), (1.0, beat)],
                      "recover": [(0.45, _mix(beat, _rest(), 0.5)), (0.7, _mix(beat, _rest(), 0.85))]})
    return mv


def moves(r):
    return [talon("r", "talon"), talon("l", "talon_b"), buffet(r), dive(r), lullaby(r), lullabies(r), dreamfall(r), swivel(r), neverwake(r), wake(r)]


def clips(r):
    _RIG[0] = r
    _idle(r)
    _flight(r)
    _reactions(r)
    mv = moves(r)
    for m in mv:
        stage_clips(r, m, rest=_rest())
    by = {m.id: m for m in mv}
    # the swivel's head turn is its own track: right round and held through the blow, then back the long way slowly (the
    # settle key would swing it past rest)
    m = by["swivel"]
    c = r.clips[core.clip_name(m)]
    c.tracks[("head", "rot")] = []
    c.rot("head", [(0.0, 0, 0, 0), (_staged(m, "windup", 0.3), 0, 70, 0), (_staged(m, "windup", 0.6), 0, 162, 2), (_staged(m, "windup", 1.0), 0, 170, 0),
                   (_staged(m, "active", 1.0), 0, 166, 0), (_staged(m, "recover", 0.3), 0, 150, 0), (_staged(m, "recover", 0.65), 0, 60, 0),
                   (_staged(m, "recover", 0.9), 0, 8, 0), (c.length, 0, 0, 0)])
    # lids straight (a curve would overshoot past open and fold them down over the eyes)
    _track(r, by["neverwake"], ["lid_l", "lid_r"], by["neverwake"]._lids, axes=(0, 1, 0), interp="lin")
    c = r.clips[core.clip_name(by["lullabies"])]
    ks = [(0.0, 1.0)] + [(_staged(by["lullabies"], s, f), v) for s, f, v in by["lullabies"]._fluff] + [(c.length, 1.0)]
    c.scale("core", [(t, v, v, v) for t, v in sorted(ks)], "cat")
    for mid in ("lullaby", "neverwake"):
        m = by[mid]
        c = r.clips[core.clip_name(m)]
        if mid == "lullaby":
            pts = [(_staged(m, "active", f), v) for f, v in m._throat]
        else:
            pts = [(_staged(m, s, f), v) for s, f, v in m._throat]
        c.scale("throat", [(0.0, 1, 1, 1)] + [(t, 1 + 0.12 * v, 1 + 0.18 * v, 1 + 0.14 * v) for t, v in sorted(pts)] + [(c.length, 1, 1, 1)], "cat")
    # in the idle its throat swells as it hoots, and nothing else scales
    c = r.clips["idle"]
    c.scale("throat", [(0.0, 1, 1, 1), (3.7, 1, 1, 1), (4.05, 1.18, 1.3, 1.2), (4.4, 1, 1, 1), (8.0, 1, 1, 1)], "cat")
    # the dead owl's wings and talons stay out of the floor as it lands
    floor = [([f"prim_{nm}{k}" for k in range(4)] + [f"wing_{nm}2"], [(f"wing_{nm}2", 2, 30), (f"wing_{nm}1", 2, 30)]) for nm, _ in SIDES_]
    floor += [(["tail", "tailf_l", "tailf_r"], [("tail", 0, 80, -1)]), (["head"], [("head", 0, 30)]),
              (["tuft_l", "tuft_r"], [("tuft_l", 0, 40), ("tuft_r", 0, 40), ("head", 0, 20)])]
    core.ground_pass(r, floor, clips=list(r.clips), skip=())
    return mv


# Its sounds (SPEC §35.8): soft wingbeats, an owl's hoot made of the vanilla sounds nearest it, a lullaby's chime and the
# phantom's screech; chosen on paper, not heard in game.
SOUNDS = {
    "tell_light": [("entity.phantom.flap", 0.9, 0.7), ("entity.parrot.fly", 0.8, 0.6)],
    "tell_heavy": [("entity.phantom.flap", 1.4, 0.5), ("entity.phantom.ambient", 1.0, 0.6)],
    "tell_perilous": [("block.note_block.chime", 1.4, 0.5), ("entity.phantom.ambient", 1.6, 0.5), ("entity.warden.heartbeat", 1.0, 0.7)],
    "swing_light": [("entity.phantom.swoop", 1.0, 0.8), ("entity.player.attack.sweep", 0.8, 0.7)],
    "swing_heavy": [("entity.phantom.swoop", 1.6, 0.6), ("entity.ender_dragon.flap", 1.0, 0.8)],
    "cast": [("block.note_block.chime", 1.2, 0.6), ("block.amethyst_block.chime", 1.2, 0.7)],
    "impact": [("entity.phantom.bite", 1.4, 0.6), ("entity.player.attack.crit", 1.0, 0.7)],
    "roar": [("entity.phantom.death", 1.6, 0.5), ("entity.phantom.ambient", 2.0, 0.4)],
    "step": [("entity.phantom.flap", 0.6, 0.6)],
}
MOVE_SOUNDS = {"lullaby": {"swing": "cast", "impact": ""}, "lullabies": {"swing": "cast", "impact": ""}, "dreamfall": {"swing": "cast"},
               "neverwake": {"swing": "roar"}, "wake": {"swing": "cast", "impact": ""}}

FIGHT = {
    "intro": {"pose": "idle", "move": "wake"},  # it hangs asleep in its wings, and wakes enough to fight you
    "phases": [
        {"pool": [
            {"move": "talon", "weight": 3, "when": "near", "then": [["talon_b"], ["talon_b", "buffet"]]},
            {"move": "buffet", "weight": 2, "when": "near"},
            {"move": "dive", "weight": 3, "when": "far", "then": [["talon"]]},
            {"move": "lullaby", "weight": 1},
            {"move": "swivel", "weight": 3, "when": "behind"},
        ]},
        # you feel heavy, as in DeepBestiary: the small owls, the dreams falling
        {"transition": "lullabies", "pool": [
            {"move": "talon", "weight": 3, "when": "near", "then": [["talon_b"], ["talon_b", "buffet"]]},
            {"move": "dive", "weight": 3, "when": "far", "then": [["talon", "talon_b"]]},
            {"move": "dreamfall", "weight": 2},
            {"move": "lullabies", "weight": 1},
            {"move": "swivel", "weight": 3, "when": "behind"},
        ]},
        # never wake
        {"transition": "neverwake", "pool": [
            {"move": "neverwake", "weight": 2},
            {"move": "dive", "weight": 3, "when": "far", "then": [["buffet"]]},
            {"move": "talon", "weight": 3, "when": "near", "then": [["talon_b", "buffet"]]},
            {"move": "lullaby", "weight": 2},
            {"move": "dreamfall", "weight": 1},
            {"move": "swivel", "weight": 3, "when": "behind"},
        ]},
    ],
    "enrage": {"below": 0.15, "speed": 1.2},
}


def design():
    r = body()
    r.pack()
    mv = clips(r)
    return {"rig": r, "plan": None, "moves": mv, "fight": FIGHT, "paint": paint, "scale": SCALE, "half": 1.4, "sounds": SOUNDS,
            "move_sounds": MOVE_SOUNDS}
