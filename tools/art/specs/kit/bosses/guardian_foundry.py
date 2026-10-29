"""The Furnace Engine (SPEC §35.11, docs/bosses/ROLLOUT.md): the Precursor Foundry's Guardian, a Brute. The Iron Horse,
picked from its silhouette round 2026-10-29: "The foundry's heart is still warm."

A steam locomotive that grew legs and woke. Its body is the engine: a long black boiler banded in brass, a steam dome
and a sand dome on its back, a cab at its rear with its windows lit by the fire inside, and the firebox under the cab
glowing through its grate onto the floor. Its head is the smokebox, a sooty iron drum at the front of the boiler with
a tall flared chimney standing on it, a lamp on its brow for its one eye, and its round door for a mouth: it drops open
on its hinge and the fire shows inside. A red buffer beam and two buffers stand out under its face like tusks, and a
slatted cowcatcher hangs under them like a beard. Four great red driving wheels turn on its flanks, and its legs are
its rods: a polished connecting rod from each wheel's hub down to a piston, the piston driving down into an iron hoof
rimmed like a wheel's flange. Its coupling chain hangs behind it for a tail.

It walks like a cart horse, heavy and nodding, its wheels turning as it goes; it runs at a pounding trot. It stands
breathing like an engine, its door rattling, its wheels easing round and back.

Moves (SPEC §35.11): it strikes down at you with one forehoof, then the other (LIGHT); the Engine Slam (HEAVY: it rears
up on its hind legs, its forehooves pawing the air and its wheels spinning, holds, and slams them down); a buck at
whoever is behind it, both hind hooves; Full Steam (a gap closer: it lowers its head and paws the floor, its wheels
spinning up, then charges at you and rams you with its cowcatcher); Arc Weld (the signature: it plants its hooves wide,
lowers its head and opens its door, the fire roaring inside, and the arc burns at you from its lamp); Rivet Rain (it
rears its chimney to the sky and chuffs, its whole body pumping, and the rivets come down); and Wake the Drones (it
throws its head up and blows its whistle). It waits for you lying down cold with its legs folded under it, like a
horse in a stall.
"""
import math

from kit import core, ik, parts
from kit.core import DEG, Move, keyed, stage_clips
from kit.paint import edge_glow, radial
import fauna_rig as R
from fauna_rig import Ramp, hexc, SIDES

ID = "guardian_foundry"
SCALE = 1.8  # the hitbox on the def's IRON_GOLEM model at size 1.6 x 2.3: 2.9 x 4.1 blocks, round its body; the body is at true size

# The Iron Horse (docs/bosses/ROLLOUT.md, A). 4.8 blocks from its cowcatcher to its cab, 4.25 to its chimney's crown,
# its boiler's middle 2.6 up. Value story: the fire brightest (the lamp, the open door, the grate, the cab's windows, the
# chimney's crown), then brass (bands, domes, cranks, glands), then the red of its wheels and buffer beam, then its
# polished rods, then black iron, its smokebox and chimney the darkest.
SIDE = (1, -1, 1, -1)  # legs: 0 front left (+x), 1 front right, 2 back left, 3 back right
FRONT, BACK = (0, 1), (2, 3)
LIMB = {0: ("arm0", "fore0", "hand0"), 1: ("arm1", "fore1", "hand1"), 2: ("thigh2", "shin2", "foot2"), 3: ("thigh3", "shin3", "foot3")}
UPPER, LOWER = 12.5, 13.5  # a leg's iron casing, and its cylinder and polished rod down to the hoof
LEGX = 16.0  # the legs hang from the wheels' hubs, outside them
WHEELX = 11.5
# the fetlocks: each hoof a hair over the floor (the ground pass takes 0.2 px under it as in it), the legs nearly
# straight under the body like a cart horse's (24.8 of their 26 px)
REST_FOOT = {0: (LEGX, 5.2, -6.0), 1: (-LEGX, 5.2, -6.0), 2: (LEGX, 5.2, 21.0), 3: (-LEGX, 5.2, 21.0)}
POLE = {0: (0.0, 0.0, -1.0), 1: (0.0, 0.0, -1.0), 2: (0.0, 0.0, 1.0), 3: (0.0, 0.0, 1.0)}  # the front knees forward, the hocks back
WHEELS = ("wheel0", "wheel1", "wheel2", "wheel3")
WHEEL_R = 10.0
ROLL_H, ROLL_Z = 26.0, 2.0  # the roll bone at its body's middle, where it keels over


def _bone(parent, name, x, up, z, rot=(0, 0, 0)):
    return parent.bone(name, (x, -up, z), rot)


def _box(bone, name, cx, y0, cz, w, h, d, mirror=False):
    return bone.cube(name, cx - w / 2, -(y0 + h), cz - d / 2, w, h, d, mirror=mirror)


def body():
    r = R.Rig(ID, (512, 512))
    base = r.bone("base", (0, 24, 0))
    rl = _bone(base, "roll", 0, ROLL_H, ROLL_Z)
    hips = _bone(rl, "hips", 0, 36 - ROLL_H, 16 - ROLL_Z)
    chest = _bone(hips, "chest", 0, 4, -14)
    head = _bone(chest, "head", 0, 3, -30)
    # the boiler: an eight-sided drum banded in brass, its domes on top, running boards along its flanks
    _box(chest, "boiler", 0, -5, -14, 22, 14, 28)
    _box(chest, "boiler_t", 0, 9, -14, 14, 4, 28)
    _box(chest, "boiler_b", 0, -9, -14, 14, 4, 28)
    for k, z in enumerate((-25, -14, -3)):
        _box(chest, f"band{k}", 0, -6, z, 24, 16, 2)
        _box(chest, f"bandv{k}", 0, -10, z, 16, 24, 2)
    _box(chest, "dome", 0, 13, -9, 8, 4, 8)
    _box(chest, "domecap", 0, 17, -9, 6, 2, 6)
    _box(chest, "sand", 0, 13, -19, 6, 3, 6)
    _box(chest, "sandcap", 0, 16, -19, 4, 1, 4)
    for s, nm in ((1, "l"), (-1, "r")):
        _box(chest, f"board_{nm}", s * 12.5, -8, -14, 3, 1, 30)
        # the cylinders under its face, their brass covers, and the piston rods running back to the front wheels
        _box(chest, f"cyl_{nm}", s * 12, -13, -25, 6, 7, 9)
        _box(chest, f"cylcap_{nm}", s * 12, -13.5, -30, 7, 8, 1)
        _box(chest, f"crod_{nm}", s * 12, -10.5, -15, 2, 2, 12)
    # under its face: the frame, the red buffer beam with its two buffers, and the slatted cowcatcher leaning forward
    _box(chest, "frame", 0, -13, -32, 18, 4, 14)
    _box(chest, "saddle", 0, -7, -29.5, 20, 18, 3)  # the smokebox's saddle ring, where the head turns
    _box(chest, "beam", 0, -13, -40, 28, 5, 3)
    for s, nm in ((1, "l"), (-1, "r")):
        _box(chest, f"buffer_{nm}", s * 9, -12, -43, 3, 3, 3)
        _box(chest, f"bufhead_{nm}", s * 9, -13, -45, 5, 5, 1)
    pil = _bone(chest, "pilot", 0, -13, -40, (-35, 0, 0))
    for k, x in enumerate((-10, -6, -2, 2, 6, 10)):
        _box(pil, f"slat{k}", x, -14 + abs(x) // 4, 0, 2, 14 - abs(x) // 4, 2)
    _box(pil, "pilotbar", 0, -12, 0, 20, 2, 2)
    # its cheeks, stepped plates so it reads as a wedge from the side
    pc = _bone(pil, "pilotside", 0, 0, 0, (35, 0, 0))  # level again, so its steps stand plumb
    for k, (y0, h, d) in enumerate(((-3, 3, 2), (-6, 3, 4), (-9, 3, 6), (-12, 2, 8))):
        for s, nm in ((1, "l"), (-1, "r")):
            _box(pc, f"cheek{k}_{nm}", s * 11, y0, -d / 2 + 0.5, 2, h, d)
    # the smokebox, its head: a sooty drum, the door's rim, the fire behind the door, the lamp on its brow
    _box(head, "smokebox", 0, -8, -5, 22, 16, 10)
    _box(head, "smoke_t", 0, 8, -5, 14, 3, 10)
    _box(head, "smoke_b", 0, -11, -5, 14, 3, 10)
    _box(head, "ring", 0, -6, -10.5, 18, 12, 1)
    _box(head, "ringv", 0, -9, -10.5, 12, 18, 1)
    _box(head, "maw", 0, -5, -11.5, 14, 10, 1)
    _box(head, "mawv", 0, -7, -11.5, 10, 14, 1)
    _box(head, "lamp", 0, 10, -8, 8, 6, 6)
    _box(head, "lens", 0, 11, -11.5, 6, 4, 1)
    _box(head, "lamphood", 0, 16, -8.5, 10, 1, 8)
    # the door, its mouth: hinged at its foot, it drops open forward; a cross handle and a brass plate on it
    door = _bone(head, "door", 0, -7, -12)
    _box(door, "door", 0, 2, -0.5, 14, 10, 1)
    _box(door, "doorv", 0, 0, -0.5, 10, 14, 1)
    _box(door, "handle", 0, 6.5, -1.5, 10, 1, 1)
    _box(door, "doorhub", 0, 5.5, -2, 3, 3, 1)
    _box(door, "plate", 0, 10.5, -1.5, 6, 2, 1)
    # the chimney, flared at its crown
    st = _bone(head, "stack", 0, 11, -3)
    _box(st, "stack", 0, 0, 0, 8, 10, 8)
    _box(st, "stack_lip", 0, 10, 0, 10, 2, 10)
    _box(st, "stack_cap", 0, 12, 0, 12, 2, 12)
    # the firebox and the cab at its rear: the grate glowing under it, the cab's walls, its roof overhanging
    _box(hips, "collar", 0, -5, -12, 20, 18, 6)
    _box(hips, "firebox", 0, -6, -1, 24, 20, 18)
    _box(hips, "grate", 0, -8, -1, 16, 2, 12)
    _box(hips, "backplate", 0, -6, 9, 22, 18, 2)
    _box(hips, "cab", 0, 14, 1, 26, 12, 16)
    _box(hips, "roof", 0, 26, 2, 30, 2, 22)
    _box(hips, "roof_t", 0, 28, 2, 22, 1, 20)
    wh = _bone(hips, "whistle", 0, 14, -9)
    _box(wh, "whistle", 0, 0, 0, 2, 5, 2)
    _box(wh, "whistle_bell", 0, 5, 0, 4, 3, 4)
    parts.chain(hips, "chain", (0, 7, 10), 3, link=(2, 4, 2))
    # the driving wheels on its flanks, red, with brass hubs
    for k in range(4):
        s = SIDE[k]
        on, (y, z) = (chest, (-10, -6)) if k in FRONT else (hips, (-6, 3))
        w = _bone(on, f"wheel{k}", s * WHEELX, y, z)
        w.cube(f"wrim{k}", -1.5, -10, -4, 3, 20, 8)
        w.cube(f"wrimh{k}", -1.5, -4, -10, 3, 8, 20)
        w.cube(f"wdisc{k}", -1.5, -8, -8, 3, 16, 16)
        w.cube(f"whub{k}", 1.5 if s > 0 else -2.5, -2, -2, 1, 4, 4)
    # the legs: a polished rod from a brass crank, a piston into an iron hoof rimmed like a wheel's flange
    for k in range(4):
        s = SIDE[k]
        up_, lo_, en_ = LIMB[k]
        on, (y, z) = (chest, (-10, -6)) if k in FRONT else (hips, (-6, 3))
        a = _bone(on, up_, s * LEGX, y, z)
        a.cube(up_, -3, 0, -3.5, 6, 12, 7)  # the casing, its back a little deeper like a gaskin
        a.cube(f"crank{k}", -3.5, -3.5, -3.5, 7, 7, 7)  # the crank on the wheel's hub
        a.cube(f"axle{k}", -4.5 if s > 0 else 2.5, -1.5, -1.5, 2, 3, 3)  # through the hub into the wheel
        a.cube(f"cband{k}", -3.5, 4, -4, 7, 1, 8)
        f = a.bone(lo_, (0, UPPER, 0))
        f.cube(f"knee{k}", -3.5, -2, -3.5, 7, 4, 7)
        f.cube(f"pcyl{k}", -2.5, 2, -2.5, 5, 6, 5)
        f.cube(f"gland{k}", -2, 8, -2, 4, 1, 4)
        f.cube(f"prod{k}", -1.5, 9, -1.5, 3, 5, 3)
        h = f.bone(en_, (0, LOWER, 0))
        h.cube(f"coronet{k}", -3, 0, -3, 6, 1, 6)
        h.cube(f"hoof{k}", -4.5, 1, -5, 9, 3, 10)
        h.cube(f"flange{k}", -5, 4, -5.5, 10, 1, 11)
    for k in range(4):
        ik.rest_limb(r, *LIMB[k], REST_FOOT[k], POLE[k])
    fr0 = core.frames(r, {})
    for k in range(4):  # the hooves set flat on the floor at rest
        end = LIMB[k][2]
        r.by_name[end].rot = tuple(v + w for v, w in zip(r.by_name[end].rot, ik.orient(r, fr0, end, (0, 1, 0), (1, 0, 0))))
        fr0 = core.frames(r, {})
    r.look = [("head", 0.5, 0.35)]
    r.shadow = 2.8
    r.size = 1.0
    # secondary parts (SPEC §35.10): the chimney rocks on its seat, the door rattles, the whistle and the chain swing
    r.springs = [("stack", 0.25, 2.4, 0.14), ("door", 0.2, 3.0, 0.18), ("whistle", 0.4, 3.2, 0.12), ("chain0", 0.5, 2.0, 0.14),
                 ("chain1", 0.5, 2.2, 0.14), ("chain2", 0.5, 2.4, 0.16)]
    r.guard = [LIMB[k][2] for k in range(4)] + ["pilot", "pilotside"]
    return r


# ---------------------------------------------------------------------------------------------------------------------
# Paint

IRON = Ramp("#0c0f13", "#12161b", "#191e24", "#21272e", "#2a3139", "#343c45", "#3f4852", "#4b5560")
SOOT = Ramp("#07080a", "#0c0e11", "#121418", "#181b20", "#1f2329", "#272c33")
BRASS = Ramp("#3a2a10", "#583f17", "#77571f", "#96712b", "#b48c3c", "#cea855", "#e4c477", "#f4dea2")
RED = Ramp("#22080a", "#340c0d", "#481211", "#5e1815", "#76201a", "#8e2a21", "#a8382b", "#c04a36")
STEEL = Ramp("#22272c", "#30363c", "#40474e", "#525a62", "#666e76", "#7b848c", "#939ba2", "#adb4ba")
FIRE = ("#fff0b8", "#ffd27a", "#ffb04a", "#ff8a2a", "#e8601a", "#b83c10")
LAMP = ("#fffbe8", "#fff2c0", "#ffe08a", "#f4c060")


def _rivets(every=4, seam=6, depth=1.0):
    """Riveted plates: a dark seam every few rows with a row of bright rivet heads along it, and the plates between
    lightly mottled."""
    def f(face, i, j):
        if face.name in ("top", "bottom") and face.h < 6:
            return 0.0
        if j % seam == 0:
            return -1.0 * depth
        if j % seam == 1 and i % every == 1:
            return 1.4 * depth
        if i % 9 == 0 and face.name in SIDES:
            return -0.5 * depth
        return 0.4 * depth if (i * 5 + j * 3) % 13 == 0 else 0.0
    return f


def paint(r, P_):
    """Black boiler plate riveted in rows and banded in brass; the smokebox and chimney sooty and matte; the wheels and
    the buffer beam red, the wheels spoked, their rims steel; the rods polished steel on brass cranks; the cab maroon
    with its windows lit by the fire; the lamp, the open door, the grate and the chimney's crown burn."""
    of = lambda *ps: parts.cubes_of(r, *ps)
    plate = _rivets()
    for c in ("boiler", "boiler_t", "boiler_b", "collar", "firebox", "backplate", "frame", "saddle") + tuple(of("board_", "cyl_")):
        P_.paint(c, IRON, 4.0, plate, grad=1.2, rim=0.8, ao=0.9, noise=0.5, nscale=6)
    bandp = lambda f, i, j: (1.0 if j == 0 else -0.7 if (i + j) % 7 == 0 else 0)
    for c in of("band", "dome", "domecap", "sand", "sandcap", "cylcap_", "crank", "axle", "cband", "knee", "coronet", "gland", "whistle", "whub", "plate", "doorhub", "lamp"):
        if c in ("lamphood",):
            continue
        P_.paint(c, BRASS, 4.6 if c.endswith("cap") else 4.0, bandp, grad=1.0, rim=0.9, ao=0.7, noise=0.3)
    for c in ("smokebox", "smoke_t", "smoke_b", "lamphood") + tuple(of("stack")):
        P_.paint(c, SOOT, 3.6, R.p_strokes(cell=3, length=2, dark=0.6, light=0.25, seed=4), grad=1.0, rim=0.6, ao=0.8, noise=0.6, nscale=4)
    for c in ("ring", "ringv"):  # the door's ring, studded with bolts
        P_.paint(c, IRON, 3.2, lambda f, i, j: (2.0 if f.name == "north" and (i + j) % 3 == 0 and min(i, j, f.w - 1 - i, f.h - 1 - j) == 0 else 0), rim=0.8, ao=0.4)
    for c in ("door", "doorv", "handle"):
        P_.paint(c, IRON, 3.4 if c != "handle" else 5.0, lambda f, i, j: (0.9 if f.name == "north" and (i in (0, f.w - 1) or j in (0, f.h - 1)) and (i + j) % 2 == 0 else 0),
                 grad=0.8, rim=0.7, ao=0.5, noise=0.3)
    # the cab: maroon panels lined in brass, its windows lit orange by the fire, the roof black
    P_.paint("cab", RED, 3.4, lambda f, i, j: (1.0 if j == 0 else -0.6 if i % 8 == 0 else 0), grad=1.0, rim=0.6, ao=0.8, noise=0.3)
    for fname, x0, x1 in (("west", 3, 12), ("east", 3, 12), ("north", 4, 22), ("south", 4, 22)):
        f, g = P_.face("cab", fname), P_.face("cab", fname, glow=True)
        for i, j in f.each():
            if x0 <= i < x1 and 2 <= j < 8 and not (fname in ("north", "south") and 11 <= i < 15):
                f.set(i, j, hexc("#ffb04a"))
                g.set(i, j, hexc(FIRE[2] if j > 3 else FIRE[3], 200))
            elif x0 - 1 <= i <= x1 and 1 <= j <= 8:
                f.set(i, j, BRASS.at(4.0))
    for c in ("roof", "roof_t"):
        P_.paint(c, IRON, 3.0, lambda f, i, j: (0.8 if j == 0 else 0), grad=0.8, rim=0.6, ao=0.6)
    # the wheels: red, spoked, a steel tyre round each, a dark counterweight
    for k in range(4):
        for c in (f"wrim{k}", f"wrimh{k}", f"wdisc{k}"):
            P_.paint(c, RED, 4.0, None, grad=0.6, rim=0.4, ao=0.5, noise=0.3)
        for c in (f"wrim{k}", f"wrimh{k}", f"wdisc{k}"):
            for fname in ("west", "east"):
                f = P_.face(c, fname)
                for i, j in f.each():
                    # the face's pixel in the wheel's own frame (its middle at the bone's pivot)
                    u, v = _wheel_uv(c, fname, i, j, f.w, f.h)
                    d = math.hypot(u, v)
                    a = math.atan2(v, u)
                    if d > 8.6:
                        f.set(i, j, STEEL.at(3.6 + (0.8 if v < 0 else 0)))
                    elif d > 7.4:
                        f.set(i, j, RED.at(2.0))
                    elif d < 2.2:
                        f.set(i, j, BRASS.at(4.4))
                    elif abs(math.sin(4 * a)) < 0.28:
                        f.set(i, j, RED.at(5.6))
                    elif 4.0 < d < 7.4 and -2.2 < a < -0.9:
                        f.set(i, j, RED.at(1.4))
                    else:
                        f.set(i, j, RED.at(2.6))
    # the legs: riveted iron casings, the cylinders below them iron, the rods polished steel with a bright line down
    # them; the hooves dark, their flanges steel like a wheel's tyre
    for c in of("arm", "thigh"):
        P_.paint(c, IRON, 4.0, lambda f, i, j: (1.5 if f.name in ("north", "south") and i in (1, f.w - 2) and j % 3 == 1 else -0.6 if j == f.h - 1 else 0),
                 grad=1.4, rim=0.9, ao=0.8, noise=0.4, nscale=5)
    for c in of("prod", "crod_"):
        P_.paint(c, STEEL, 4.8, lambda f, i, j: (1.6 if i == f.w // 2 else 0), grad=0.8, rim=1.0, ao=0.4, noise=0.2)
    for c in of("pcyl"):
        P_.paint(c, IRON, 3.6, lambda f, i, j: (1.0 if j == 0 else -0.6 if j % 3 == 2 else 0), grad=1.0, rim=0.8, ao=0.7, noise=0.3)
    for c in of("hoof"):
        P_.paint(c, IRON, 2.8, None, grad=0.8, rim=0.6, ao=0.6, noise=0.4)
    for c in of("flange"):
        P_.paint(c, STEEL, 3.4, None, rim=0.8, ao=0.3)
    # the buffer beam red, the buffers' heads steel, the cowcatcher's slats iron with red tips
    P_.paint("beam", RED, 4.2, lambda f, i, j: (1.0 if j == 0 else 0), grad=0.8, rim=0.8, ao=0.6, noise=0.3)
    for c in of("buffer_", "bufhead_"):
        P_.paint(c, STEEL, 4.4 if c.startswith("bufhead") else 3.4, None, rim=0.9, ao=0.4)
    for c in of("slat", "pilotbar", "cheek"):
        P_.paint(c, IRON, 3.4, lambda f, i, j: (1.0 if j >= f.h - 2 else 0), grad=0.8, rim=0.8, ao=0.5)
    for c in of("chain"):
        P_.paint(c, IRON, 3.6, None, rim=0.8, ao=0.4)
    # the fire: the lamp's lens its eye, the maw behind the door, the grate under it, the chimney's crown
    P_.paint("lens", Ramp("#c8a050", "#f0d080"), 1.6)
    radial(P_, "lens", "north", list(LAMP))
    for c in ("maw", "mawv"):
        P_.paint(c, Ramp("#5a1a08", "#a83810"), 1.4)
        radial(P_, c, "north", list(FIRE))
    P_.paint("grate", Ramp("#3a1406", "#6a2808"), 1.6)
    for fname in ("bottom", "west", "east", "north", "south"):
        radial(P_, "grate", fname, list(FIRE[1:]), alpha=230)
    g = P_.face("stack_cap", "top", glow=True)
    f = P_.face("stack_cap", "top")
    for i, j in f.each():
        if 2 <= i < f.w - 2 and 2 <= j < f.h - 2:
            f.set(i, j, hexc("#3a1406"))
            g.set(i, j, hexc(FIRE[3 + (R._h("cap", i, j) % 3)], 150 + (R._h("cap2", i, j) % 4) * 25))
    # the firebox's vents: a row of slots low on its flanks, the fire showing
    for fname in ("west", "east"):
        f, gl = P_.face("firebox", fname), P_.face("firebox", fname, glow=True)
        for i, j in f.each():
            if j >= f.h - 4 and j < f.h - 2 and i % 3 != 0 and 2 <= i < f.w - 2:
                f.set(i, j, hexc("#ff8a2a"))
                gl.set(i, j, hexc(FIRE[3], 190))
    for fname in ("west", "east"):
        edge_glow(P_, "firebox", fname, FIRE[4], rows=1, alpha=60)
    return P_.skin, P_.glow


WHEEL_BOX = {"wrim": (-10, -4, 20, 8), "wrimh": (-4, -10, 8, 20), "wdisc": (-8, -8, 16, 16)}  # (y0, z0, h, d) on the wheel's pivot


def _wheel_uv(c, fname, i, j, w, h):
    """A wheel face's pixel (i across, j down) as (z, y) from the wheel's middle: the west face reads z one way, the
    east face the other."""
    key = c.rstrip("0123456789")
    y0, z0, hh, dd = WHEEL_BOX[key]
    zz = (z0 + i + 0.5) if fname == "west" else (z0 + dd - i - 0.5)
    yy = y0 + j + 0.5
    return zz, yy


# ---------------------------------------------------------------------------------------------------------------------
# Motion

def _ease(f):
    f = max(0.0, min(1.0, f))
    return f * f * (3 - 2 * f)


def _wave(u, at, width):
    """A bump 0..1..0 centred on at, width wide."""
    x = (u - at) / width
    return math.cos(math.pi * x) ** 2 if abs(x) < 0.5 else 0.0


def _flat(yaw):
    a = yaw * DEG
    return (math.cos(a), 0.0, -math.sin(a))


CHAIN_W = (0.3, 0.33, 0.37)  # a tail swing shared down the chain, more toward its end
DOOR = 1.0  # the door's turn that opens it


def _pose(r, lift=0.0, pitch=0.0, yaw=0.0, roll=0.0, shift=(0.0, 0.0), bend=0.0, side=0.0, twist=0.0, head=(0, 0, 0), door=0.0,
          stack=(0.0, 0.0), whistle=0.0, chain=(0.0, 0.0), wheels=0.0, pilot=0.0, tilt=0.0, sink=0.0, feet=None, reach=None, local=None,
          poles=None, hands=None, limbs=None):
    """lift (+ up), pitch (+ its front dipping), yaw (+ to its left), roll (+ its left side down) and shift (x, z) move
    its hips, the rear unit; bend (+ its front dipping), side (+ to its left) and twist turn its boiler, the front unit,
    on the rear; head (nod + down, turn + to its left, tilt) turns the smokebox on its saddle; door (degrees) opens its
    door, stack (+ forward, + to its left) rocks its chimney, whistle shakes its whistle, chain (lift, sweep) swings its
    tail, wheels turns its driving wheels (degrees, + rolling forward), pilot folds its cowcatcher back; tilt and sink
    turn and lower it on its roll bone. feet {leg: (x, height, z)}: where the hooves go (the rest stand where they stand
    at rest); reach {leg: (dx, dup, dz)}: the same, from where the leg's root is posed; local {leg: (x, down, z)}: from
    the root in its hips' own axes, however it lies (a leg out stiff from a body on its side); limbs {leg: {bone: turn}} sets a
    leg's turns outright; hands {leg: "free" | degrees}: that hoof hangs as its leg leaves it, or flexed back so far
    (flat on the floor if not given)."""
    p = {"roll": {"rot": (0, 0, tilt), "pos": (0, -sink, 0)},
         "hips": {"rot": (pitch, yaw, roll), "pos": (shift[0], lift, shift[1])},
         "chest": (bend, side, twist), "head": tuple(head), "door": (DOOR * door, 0, 0), "stack": (stack[0], 0, stack[1]),
         "whistle": (whistle, 0, 0.6 * whistle), "pilot": (pilot, 0, 0)}
    for i, w in enumerate(CHAIN_W):
        p[f"chain{i}"] = (chain[0] * w, chain[1] * w, 0)
    for k in range(4):
        p[WHEELS[k]] = (wheels, 0, 0)
    fr = core.frames(r, core.as_sample(p))
    feet, reach, poles, limbs, hands, local = feet or {}, reach or {}, poles or {}, limbs or {}, hands or {}, local or {}
    for k in range(4):
        if k in limbs:
            lp = {b: limbs[k].get(b, (0.0, 0.0, 0.0)) for b in LIMB[k][:2]}
        elif k in local:
            root = core.point(fr, LIMB[k][0], (0, 0, 0))
            d = R._mv(fr["hips"][0], local[k])
            lp = ik.limb(r, fr, *LIMB[k], tuple(root[i] + d[i] for i in range(3)), poles.get(k, POLE[k]))
        else:
            if k in reach:
                root = core.point(fr, LIMB[k][0], (0, 0, 0))
                dx, du, dz = reach[k]
                tgt = (root[0] + dx, 24.0 - root[1] + du, root[2] + dz)
            else:
                tgt = feet.get(k, REST_FOOT[k])
            lp = ik.limb(r, fr, *LIMB[k], ik.model(tgt), poles.get(k, POLE[k]))
        p.update(lp)
    fr = core.frames(r, core.as_sample(p))
    flat = _flat(yaw)
    for k in range(4):
        end = LIMB[k][2]
        how = hands.get(k, "free" if k in limbs else None)
        if how is None:
            p[end] = ik.orient(r, fr, end, (0, 1, 0), flat)
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
    """The pose let down or lifted on its roll bone until its lowest part just rests on the floor (lying down, lying on
    its side)."""
    fr = core.frames(r, core.as_sample(p))
    up = core.lowest(r, fr, list(r.by_name)) - (core.GROUND - gap)
    q = dict(p)
    rl = q["roll"]
    q["roll"] = {"rot": rl["rot"], "pos": (rl["pos"][0], rl["pos"][1] + up, rl["pos"][2])}
    return q


def _plant(r, clip, legs, where=None):
    """The hooves that stand through a move keep where they stand on the floor, every tick, and lie flat on it heading
    the way it heads."""
    where = where or {}

    def fn(t, s, fr):
        out = {}
        for k in legs:
            out.update(ik.limb(r, fr, *LIMB[k], ik.model(where.get(k, REST_FOOT[k])), POLE[k]))
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


HOOF = {k: [LIMB[k][2]] for k in range(4)}  # the hoof bones (coronet, hoof and flange)


def _on_floor(r, clip):
    """Between keys a hoof can sag a hair into the floor (its leg's turns blend, not its path): wherever one would, its
    leg is solved again that tick to set it on the floor where it was, the knee its own way and the hoof turned as it
    was (turning the shin after the fact, as the ground pass would, kinks it)."""
    floor = core.GROUND - 0.3
    still = {"pos": (0, 0, 0), "rot": (0, 0, 0), "scale": (1, 1, 1)}

    def fn(t, smp, fr):
        out = {}
        for k in range(4):
            up, lo, en = LIMB[k]
            v = core.lowest(r, fr, HOOF[k])
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


def _drive(r, clip, speeds, whole=True):
    """The driving wheels turned through a clip after it is made, at speeds [(seconds, degrees a second)] eased between
    them; with whole, scaled to end on whole turns, so it hands back to the idle as it was (a whole turn is no turn)."""
    c = r.clips[clip]
    n = int(round(c.length / core.TICK))

    def v(t):
        if t <= speeds[0][0]:
            return speeds[0][1]
        for (t0, v0), (t1, v1) in zip(speeds, speeds[1:]):
            if t0 <= t <= t1:
                return v0 + (v1 - v0) * _ease((t - t0) / max(1e-6, t1 - t0))
        return speeds[-1][1]
    ang, acc = [0.0], 0.0
    for k in range(n):
        for j in range(8):
            acc += v((k + (j + 0.5) / 8) * core.TICK) * core.TICK / 8
        ang.append(acc)
    sc = (360.0 * round(acc / 360.0) / acc if abs(acc) > 1e-6 else 0.0) if whole else 1.0
    for w in WHEELS:
        old = c.tracks.get((w, "rot"))
        keys = []
        for k in range(n + 1):
            t = min(c.length, k * core.TICK)
            base = list(R._interp(old, t)) if old else [0.0, 0.0, 0.0]
            base[0] += ang[k] * sc
            keys.append((t, tuple(base), "cat"))
        c.tracks[(w, "rot")] = keys


# ---------------------------------------------------------------------------------------------------------------------
# Clips

def _idle(r):
    """It stands breathing like an engine at rest: a slow chuff, its boiler swelling, its chimney rocking on its seat
    with each one and its door rattling; its wheels ease round and back; it looks about, its lamp turning; and it paws
    the floor with a forehoof, impatient, as a horse does, looking down at it."""
    L, n = 5.0, 100
    # the paw: the hoof lifted and swung forward, struck down and dragged back along the floor (q, up, dz)
    paw_keys = ((0.0, 0.0, 0.0), (0.3, 5.0, -2.5), (0.55, 0.4, -4.5), (0.8, 0.0, 1.5), (1.0, 0.0, 0.0))

    def paw(q):
        for (q0, u0, z0), (q1, u1, z1) in zip(paw_keys, paw_keys[1:]):
            if q0 <= q <= q1:
                f = _ease((q - q0) / (q1 - q0))
                return u0 + (u1 - u0) * f, z0 + (z1 - z0) * f
        return 0.0, 0.0
    keys = []
    for k in range(n + 1):
        u = k / n
        w = 2 * math.pi * u
        br = math.sin(2 * w)
        ch = _wave(u, 0.2, 0.14) + _wave(u, 0.7, 0.14)  # its two chuffs
        q = (u - 0.36) / 0.26
        pw = _wave(u, 0.49, 0.3)
        feet = None
        if 0 < q < 1:
            up_, dz = paw(q)
            feet = {0: _foot(0, up=up_, dz=dz)}
        keys.append((L * u, _pose(r, lift=-0.3 + 0.3 * br - 0.3 * ch, pitch=0.4 * br, yaw=1.0 * math.sin(w), roll=0.6 * math.sin(w + 1.0) - 1.2 * pw,
                                  bend=0.8 * br + 0.6 * ch, side=1.5 * math.sin(w + 0.5), head=(2 * math.sin(2 * w + 1.0) + 8 * pw, 14 * math.sin(w + 0.6) * (1 - pw) + 6 * pw, 2 * math.sin(w)),
                                  door=3.0 * ch * (0.5 + 0.5 * math.sin(26 * w)), stack=(4.0 * ch + 1.0 * br, 1.5 * math.sin(w + 1.2)),
                                  chain=(2 * math.sin(w), 7 * math.sin(w + 0.8)), wheels=10.0 * math.sin(w), feet=feet,
                                  hands={0: 25 * math.sin(math.pi * min(1.0, max(0.0, q / 0.55)))} if feet else None)))
    keyed(r, "idle", L, keys, loop=True)


WALK = {2: 0.0, 0: 0.25, 3: 0.5, 1: 0.75}  # its walk: each hoof by turns, back left, front left, back right, front right
TROT = {0: 0.0, 3: 0.0, 1: 0.5, 2: 0.5}  # its run: the diagonals together, like a cart horse's trot


def _gait(u, stride_px, beta, lift, groups):
    """The hooves through a stride at u: down, each moves back as far as it travels, so it stays put on the floor; up,
    it lifts and reaches forward again, its knee high."""
    feet, flex = {}, {}
    st = stride_px * beta
    for k, ph in groups.items():
        q = (u - ph) % 1.0
        if q < beta:
            feet[k] = _foot(k, dz=-st / 2 + st * q / beta)
        else:
            f = (q - beta) / (1 - beta)
            feet[k] = _foot(k, up=lift * math.sin(math.pi * f) ** 0.8, dz=st / 2 - st * core.ease_io(f))
            flex[k] = 40.0 * math.sin(math.pi * f) ** 1.5 if k in FRONT else 20.0 * math.sin(math.pi * f) ** 1.5
    return feet, flex


def _locomotion(r):
    """Its walk is a cart horse's, one hoof at a time, heavy, its head nodding with each forehoof and its boiler rocking
    on its rear with the steps, its wheels turning once a stride. Running, it pounds along in a trot, the diagonals
    together, its whole body bounding and its chimney rocking, its wheels racing."""
    for name, L, stride, beta, lift, groups, bob, turns in (("walk", 1.3, 1.1, 0.7, 6.0, WALK, 0.6, 1), ("run", 0.72, 2.0, 0.5, 9.0, TROT, 1.6, 2)):
        sp = stride * 16
        keys = []
        n = 30
        for k in range(n + 1):
            u = k / n
            s = math.sin(2 * math.pi * u)
            s2 = math.sin(4 * math.pi * u)
            c2 = math.cos(4 * math.pi * u)
            feet, flex = _gait(u, sp, beta, lift, groups)
            run = bob > 1
            keys.append((L * u, _pose(r, lift=-0.9 - bob * 0.5 * (1 + c2), pitch=(1.5 if run else 0.0) + (1.2 if run else 0.5) * s2, roll=1.6 * s, yaw=1.0 * s,
                                      bend=(1.5 if run else 0.8) * s2, side=2.5 * s, head=(3 + (5 if run else 3) * math.sin(4 * math.pi * u + 0.9), -4 * s, 0),
                                      door=(3.0 if run else 1.0) * (1 + math.sin(8 * math.pi * u)), stack=((3 if run else 1.5) * math.sin(4 * math.pi * u + 1.6), 1.5 * s),
                                      chain=(3 + 3 * s2, -10 * math.sin(2 * math.pi * u - 0.6) * (1.5 if run else 1.0)), feet=feet,
                                      hands={k_: v for k_, v in flex.items()})))
        keyed(r, name, L, keys, loop=True)
        c = r.clips[name]
        for w in WHEELS:  # its wheels turn round at the pace of its stride, whole turns a loop
            c.tracks[(w, "rot")] = [(L * k / 36, (10.0 * turns * k, 0.0, 0.0), "lin") for k in range(37)]
    r.walk_stride, r.run_stride = 1.1, 2.0
    r.run_speed = (0.12, 0.26)
    for nm, sgn in (("turn_l", 1), ("turn_r", -1)):
        keys = []
        n = 18
        A = 14.0 * DEG
        for k in range(n + 1):
            u = k / n
            feet, flex = {}, {}
            for j, ph in WALK.items():  # each hoof steps round the way it turns while the others hold
                q = (u - ph) % 1.0
                if q < 0.7:
                    a = sgn * A * (0.5 - q / 0.7)
                    up_ = 0.0
                else:
                    f = (q - 0.7) / 0.3
                    a = sgn * A * (-0.5 + core.ease_io(f))
                    up_ = 5.0 * math.sin(math.pi * f)
                    flex[j] = 30.0 * math.sin(math.pi * f)
                x, h, z = REST_FOOT[j]
                feet[j] = (x * math.cos(a) + z * math.sin(a), h + up_, -x * math.sin(a) + z * math.cos(a))
            keys.append((0.9 * u, _pose(r, lift=-0.8, yaw=sgn * 2.0, side=sgn * 4.0, roll=1.0 * math.sin(4 * math.pi * u), head=(3, sgn * 14, 0), chain=(2, -sgn * 10),
                                        stack=(0, -sgn * 3), feet=feet, hands=flex)))
        keyed(r, nm, 0.9, keys, loop=True)
        c = r.clips[nm]
        for j, w in enumerate(WHEELS):  # turning on the spot, its wheels rock against each other, the outer side forward
            a = 12.0 * sgn * -SIDE[j]
            c.tracks[(w, "rot")] = [(0.9 * k / 18, (a * math.sin(2 * math.pi * k / 18), 0.0, 0.0), "cat") for k in range(19)]


# its lying: down on its belly on the floor, its forelegs folded under it, knees forward, its hind legs folded, hocks
# back, its head low and its cowcatcher folded up under its chin
def _lying(r, nod=10.0, breath=0.0, door=0.0, chain=(-30.0, 10.0), roll=0.0, side=0.0):
    p = _pose(r, lift=-19.0 + breath, roll=roll, side=side, bend=-1.0 - breath * 0.5, head=(nod, 4, 0), door=door, pilot=80, chain=chain,
              stack=(-2.0, 0.0), reach={0: (0.0, -7.6, 3.0), 1: (0.0, -7.6, 3.0), 2: (0.0, -7.6, -5.0), 3: (0.0, -7.6, -5.0)},
              poles={0: (0.0, -0.4, -1.0), 1: (0.0, -0.4, -1.0), 2: (0.0, -0.2, 1.0), 3: (0.0, -0.2, 1.0)}, hands={k: 60.0 if k in FRONT else -30.0 for k in range(4)})
    return _floor(r, p, gap=0.3 - breath)


def _kneeling(r, nod=8.0):
    """Down on its front knees, its hind legs still under it, the way a horse goes down."""
    return _pose(r, lift=-9.0, pitch=18, head=(nod, 0, 0), pilot=60, chain=(-6, 0), stack=(3, 0),
                 reach={0: (0.0, -8.0, 3.0), 1: (0.0, -8.0, 3.0)}, poles={0: (0.0, -0.5, -1.0), 1: (0.0, -0.5, -1.0)}, hands={0: 60.0, 1: 60.0})


def _reactions(r):
    """Struck, it jolts, its head thrown up, its door banging open on its fire and its chimney whipping back; from the
    side it rocks away. Its stance broken, it goes down as a horse does, on its front knees first and then its rear,
    and lies there; it gets up the same way, its forelegs first."""
    home = _pose(r)
    hit = _pose(r, lift=-1.0, pitch=-3, bend=-3, head=(-12, 0, 0), door=28, stack=(-9, 0), chain=(10, 0), wheels=-14)
    back = _pose(r, lift=-0.6, pitch=1, bend=1, head=(4, 0, 0), door=6, stack=(3, 0), chain=(-2, 0), wheels=4)
    keyed(r, "hurt", 0.55, [(0, home), (0.08, hit), (0.3, back), (0.55, home)])
    for nm, sgn in (("hurt_l", 1), ("hurt_r", -1)):  # struck from its left it rocks away to its right
        h = _pose(r, lift=-0.8, roll=-4 * sgn, yaw=-5 * sgn, side=-7 * sgn, shift=(-sgn * 1.2, 0), head=(-5, -14 * sgn, 0), door=16, stack=(0, -9 * sgn),
                  chain=(6, 14 * sgn), wheels=-10)
        b = _pose(r, roll=1.5 * sgn, side=2 * sgn, head=(1, 4 * sgn, 0), door=4, stack=(0, 3 * sgn), chain=(0, -4 * sgn), wheels=3)
        keyed(r, nm, 0.6, [(0, home), (0.08, h), (0.32, b), (0.6, home)])
    knees = _kneeling(r)
    lie = _lying(r)
    settle = _lying(r, nod=12, breath=0.4)
    keyed(r, "kneel", 1.4, [(0, home), (0.25, _mix(home, knees, 0.45)), (0.55, knees), (0.95, _mix(knees, lie, 0.75)), (1.15, settle), (1.4, lie)])
    # rising: its forelegs stretched out before it on the floor, it pushes its front up, then heaves its rear up
    reach_ = _pose(r, lift=-16.0, pitch=-6, head=(-4, 0, 0), pilot=60, chain=(-18, 0), stack=(-3, 0),
                   feet={0: (LEGX, 5.2, -24.0), 1: (-LEGX, 5.2, -24.0)}, reach={2: (0.0, -7.6, -5.0), 3: (0.0, -7.6, -5.0)},
                   poles={2: (0.0, -0.2, 1.0), 3: (0.0, -0.2, 1.0)}, hands={2: -30.0, 3: -30.0})
    sit = _pose(r, lift=-11.0, pitch=-24, head=(-8, 0, 0), pilot=30, chain=(-10, 0), stack=(-6, 0), door=8,
                feet={0: (LEGX, 5.2, -12.0), 1: (-LEGX, 5.2, -12.0)}, reach={2: (0.0, -9.0, -6.0), 3: (0.0, -9.0, -6.0)},
                poles={2: (0.0, -0.2, 1.0), 3: (0.0, -0.2, 1.0)}, hands={2: -30.0, 3: -30.0})
    heave = _pose(r, lift=-3.0, pitch=-6, head=(4, 0, 0), door=4, stack=(4, 0), chain=(4, 0), feet={0: (LEGX, 5.2, -9.0), 1: (-LEGX, 5.2, -9.0)})
    keyed(r, "rise", 1.4, [(0, lie), (0.3, reach_), (0.65, sit), (1.0, heave), (1.4, home)])
    _death(r)


TILT = -1.0  # the roll bone's turn that lays it over onto its right side


def _death(r):
    """Its last: it shudders, its head thrown up, its door banging open and its fire flaring, its wheels racing; its
    right foreleg buckles and it goes down on its knees; it keels over onto its right side with a crash and rocks; its
    legs settle, its head drops to the floor, its door swings open on its hinge, its boiler breathes out once and its
    wheels run down and stop."""
    home = _pose(r)
    shudder = _pose(r, lift=-0.6, pitch=-4, bend=-3, head=(-16, 0, 0), door=48, stack=(-12, 0), chain=(10, 0), whistle=12)
    buckle = _pose(r, lift=-3.0, pitch=6, roll=-6, side=-6, head=(10, -10, 0), door=36, stack=(4, -4), chain=(0, 8),
                   feet={1: (-14.0, 9.0, -10.0)}, poles={1: (0.0, -0.6, -1.0)}, hands={1: 40.0})
    knees = _kneeling(r, nod=6)
    knees = _with(knees, {"hips": {"rot": (17, 0, -6), "pos": (0, -8.0, 0)}})
    # on its side its legs stick out stiff from it; then the upper pair droop down across the lower onto the floor
    stiff = {k: (0.0, 24.5, -3.0 if k in FRONT else 3.0) for k in range(4)}
    droop = {0: (-13.0, 19.0, -5.0), 2: (-13.0, 19.0, 5.0), 1: (-1.0, 24.0, -5.0), 3: (-1.0, 24.0, 5.0)}

    def over(a, gap=0.3, nod=0.0, flop=0.0, door=40.0, breath=0.0):
        legs = {k: tuple(x + (y - x) * flop for x, y in zip(stiff[k], droop[k])) for k in range(4)}
        return _floor(r, _pose(r, tilt=TILT * a, lift=-2.0, bend=-2 - breath, head=(nod, -6, 8 * a / 88), door=door, stack=(0, 0), chain=(-6, 14), pilot=20,
                               local=legs, poles={k: (0.0, 0.0, -1.0) if k in FRONT else (0.0, 0.0, 1.0) for k in range(4)},
                               hands={k: 10.0 + 20.0 * flop if k in FRONT else -10.0 - 10.0 * flop for k in range(4)}), gap=gap)
    keys = [(0, home), (0.35, shudder), (0.75, buckle), (1.15, knees), (1.6, over(55, nod=6)), (1.9, over(90, nod=8)), (2.05, over(86, gap=1.6, nod=4)),
            (2.25, over(90, nod=10)), (2.9, over(90, nod=14, flop=0.8, door=58)), (3.5, over(90, nod=16, flop=1.0, door=70, breath=2.0)),
            (4.1, over(90, nod=16, flop=1.0, door=72)), (5.0, over(90, nod=16, flop=1.0, door=72))]
    keyed(r, "death", 5.0, _unwrap(keys))
    _drive(r, "death", [(0.0, 0.0), (0.35, 720.0), (1.6, 540.0), (3.0, 140.0), (4.4, 0.0)], whole=False)


# ---------------------------------------------------------------------------------------------------------------------
# Moves

NO_LAG = {b: 0 for k in range(4) for b in LIMB[k]}
NO_LAG.update({w: 0 for w in WHEELS})


def _lag(**kw):
    out = dict(NO_LAG)
    out.update(kw)
    return out


def _back(r, end):
    home = _pose(r)
    return [(0.45, _mix(end, home, 0.45)), (0.7, _mix(end, home, 0.82))]


def stamp(r, k=1, mid="stamp"):
    """A forehoof struck down at you (LIGHT): it shifts its weight back and away and lifts the hoof high, its knee up
    by its chest and its head up and turned toward you; then it strikes it out and down, stamping on the floor before
    it, its whole front driving down after it and its door banging."""
    s = SIDE[k]
    knee = {k: (0.0, -0.2, -1.0)}
    gather = _pose(r, lift=-0.8, pitch=-1.5, roll=-1.5 * s, shift=(-s * 1.2, 1.5), side=-2 * s, head=(-6, 5 * s, 0), stack=(-3, 0), chain=(4, -6 * s),
                   feet={k: _foot(k, up=12, dz=0)}, poles=knee, hands={k: 35.0})
    rear = _pose(r, lift=-1.0, pitch=-2.5, roll=-2 * s, shift=(-s * 1.5, 2.0), bend=-2, side=-3 * s, head=(-10, 8 * s, 0), door=6, stack=(-5, 0),
                 chain=(6, -8 * s), feet={k: _foot(k, up=19, dz=-4)}, poles=knee, hands={k: 55.0})
    strike = _pose(r, lift=-1.6, pitch=2, roll=-1 * s, shift=(-s * 1.0, -1.5), bend=1, side=-1 * s, head=(-2, 5 * s, 0), door=4, stack=(1, 0), chain=(4, -4 * s),
                   feet={k: (s * 15.0, 12.0, -22.0)}, poles={k: (0.0, 0.0, -1.0)}, hands={k: 20.0})
    hit = _pose(r, lift=-2.2, pitch=4, roll=0.5 * s, shift=(-s * 0.8, -4.0), bend=3, side=1 * s, head=(9, 3 * s, 0), door=14, stack=(7, 0), chain=(0, 2 * s),
                feet={k: (s * 14.5, 5.3, -20.0)}, poles={k: (0.0, -0.2, -1.0)})
    held = _pose(r, lift=-2.6, pitch=4.5, roll=0.8 * s, shift=(-s * 0.8, -4.0), bend=3.5, side=1.2 * s, head=(10, 3 * s, 0), door=7, stack=(3, 0), chain=(-1, 3 * s),
                 feet={k: (s * 14.5, 5.3, -20.0)}, poles={k: (0.0, -0.2, -1.0)})
    through = _pose(r, lift=-2.2, pitch=3.5, shift=(-s * 0.5, -3.0), bend=2.5, side=1 * s, head=(7, 2 * s, 0), door=4, stack=(1, 0), chain=(-1, 2 * s),
                    feet={k: (s * 15.0, 7.5, -17.0)}, poles={k: (0.0, -0.2, -1.0)}, hands={k: 15.0})
    step = _pose(r, lift=-1.2, pitch=1, shift=(0, -1.0), head=(3, s, 0), feet={k: _foot(k, up=5, dz=-4)}, poles=knee, hands={k: 30.0})
    return Move(mid, "LIGHT", 10, 4, 14, through=through, title="", track=8.0, rng=(0, 3.4), kinds=("combo", "near"), damage=1.0, knock=0.6,
                hit=[(f"hand{k}", (0, 2.5, 0), 5.0), (LIMB[k][1], (0, 10, 0), 4.0)], plant=(),
                stages={"windup": [(0.3, gather), (1.0, rear)], "active": [(0.35, strike), (0.6, hit), (1.0, held)], "recover": [(0.45, step), (0.72, _mix(step, _pose(r), 0.75))]},
                lag=_lag())


def slam(r):
    """The Engine Slam (HEAVY): it crouches and then rears right up on its hind legs, its forehooves pawing the air and
    its wheels spinning, its chimney thrown back and its door open on the fire; holds; and slams them down onto the
    floor before it, its whole weight after them, its head driving down."""
    gather = _pose(r, lift=-2.8, pitch=3, bend=2, head=(9, 0, 0), stack=(4, 0), chain=(-4, 0))
    paw = lambda a, b: {0: (0.0, -13.0 + a, -11.0), 1: (0.0, -13.0 + b, -11.0)}
    flex = {0: 55.0, 1: 55.0}
    knee = {0: (0.0, 0.3, -1.0), 1: (0.0, 0.3, -1.0)}
    rise = _pose(r, lift=-2.0, pitch=-24, bend=-6, head=(-8, 0, 0), door=18, stack=(-9, 0), chain=(14, 0), reach=paw(0, -3), poles=knee, hands=flex)
    peak = _pose(r, lift=-3.0, pitch=-38, bend=-8, head=(-14, 0, 0), door=30, stack=(-14, 0), chain=(24, 0), whistle=6, reach=paw(-2, 3), poles=knee, hands=flex)
    down = {0: (LEGX - 1, 5.3, -18.0), 1: (-LEGX + 1, 5.3, -18.0)}
    fwd = {0: (0.0, -0.2, -1.0), 1: (0.0, -0.2, -1.0)}
    over = _pose(r, lift=-2.5, pitch=-12, bend=-2, head=(-4, 0, 0), door=26, stack=(-6, 0), chain=(16, 0), reach={0: (0.0, -21.0, -9.0), 1: (0.0, -21.0, -9.0)},
                 poles=fwd, hands={0: 15.0, 1: 15.0})
    hit = _pose(r, lift=-4.0, pitch=5, bend=4, shift=(0, -3.0), head=(16, 0, 0), door=40, stack=(10, 0), chain=(-6, 0), feet=down, poles=fwd)
    held = _pose(r, lift=-4.6, pitch=5.5, bend=4.5, shift=(0, -3.0), head=(13, 0, 0), door=22, stack=(4, 0), chain=(-8, 0), feet=down, poles=fwd)
    through = _pose(r, lift=-4.0, pitch=4.5, bend=3.5, shift=(0, -2.5), head=(10, 0, 0), door=12, stack=(1, 0), chain=(-4, 0),
                    feet={k: (v[0], v[1], v[2] + 1.0) for k, v in down.items()}, poles=fwd)
    step = _pose(r, lift=-2.5, pitch=2, shift=(0, -1.0), head=(4, 0, 0), door=4, feet={0: _foot(0, up=5, dz=-6), 1: (-LEGX + 1, 5.3, -14.0)},
                 poles={0: (0.0, -0.2, -1.0)}, hands={0: 30.0})
    return Move("slam", "HEAVY", 18, 6, 22, through=through, title="Engine Slam", hold=8, track=4.0, ability="Engine Slam", rng=(0, 3.6), kinds=("area", "near"),
                damage=1.3, knock=1.2, disc=4.5, hit=[("hand0", (0, 2.5, 0), 5.5), ("hand1", (0, 2.5, 0), 5.5)], plant=(),
                stages={"windup": [(0.3, gather), (0.7, rise), (1.0, peak)], "active": [(0.35, over), (0.7, hit), (1.0, held)],
                        "recover": [(0.45, step), (0.72, _mix(step, _pose(r), 0.75))]}, lag=_lag())


def buck(r):
    """For whoever is behind it (LIGHT): it drops its head and leans onto its forelegs, its hind legs gathered under
    it; then it bucks, its rear thrown up and both hind hooves kicked out behind it, its chain swinging up."""
    gather = _pose(r, lift=-1.5, pitch=3, shift=(0, -2.0), head=(12, 0, 0), door=0, stack=(3, 0), chain=(-6, 0), feet={2: _foot(2, up=3, dz=-6), 3: _foot(3, up=3, dz=-6)},
                   hands={2: -20.0, 3: -20.0})
    load = _pose(r, lift=-2.5, pitch=5, shift=(0, -3.0), bend=2, head=(16, 0, 0), door=0, stack=(5, 0), chain=(-10, 0), feet={2: _foot(2, up=5, dz=-9), 3: _foot(3, up=5, dz=-9)},
                 hands={2: -30.0, 3: -30.0})
    kick = {2: (0.0, -11.0, 21.0), 3: (0.0, -10.0, 22.0)}
    back = {2: (0.0, 0.1, 1.0), 3: (0.0, 0.1, 1.0)}
    hit = _pose(r, lift=2.5, pitch=13, shift=(0, -3.0), bend=-3, head=(18, 0, 0), door=12, stack=(-4, 0), chain=(40, 0), reach=kick, poles=back, hands={2: 20.0, 3: 20.0})
    held = _pose(r, lift=2.8, pitch=14, shift=(0, -3.0), bend=-3.5, head=(17, 0, 0), door=8, stack=(-6, 0), chain=(48, 0), reach=kick, poles=back, hands={2: 25.0, 3: 25.0})
    through = _pose(r, lift=1.0, pitch=9, shift=(0, -2.5), bend=-2, head=(14, 0, 0), door=4, stack=(-2, 0), chain=(30, 0),
                    reach={2: (0.0, -17.0, 14.0), 3: (0.0, -17.0, 14.0)}, poles=back, hands={2: 0.0, 3: 0.0})
    return Move("buck", "LIGHT", 10, 4, 14, through=through, title="", track=0.0, rng=(0, 3.6), kinds=("behind",), damage=0.9, knock=1.1,
                hit=[("foot2", (0, 2.5, 0), 5.5), ("foot3", (0, 2.5, 0), 5.5)], plant=(),
                stages={"windup": [(0.3, gather), (1.0, load)], "active": [(0.5, hit), (1.0, held)], "recover": _back(r, through)}, lag=_lag())


ADVANCE = 8.0  # blocks Full Steam runs


def charge(r):
    """Full Steam (HEAVY, a gap closer): it lowers its head, its cowcatcher to the floor, and paws the floor twice, its
    wheels spinning up and its door rattling; then it charges at you at a pounding gallop, head down, and rams you with
    its cowcatcher; it skids to a stop on braced forelegs and rocks back."""
    def low(a, pw=0.0, dz=0.0):
        return _pose(r, lift=-2.6, pitch=4 + a, bend=3, head=(14 + a, 0, 0), door=4 + 4 * a, stack=(-4, 0), chain=(-4, 0),
                     feet={1: _foot(1, up=pw, dz=dz)} if pw or dz else None, hands={1: 30.0 * min(1.0, pw / 4)} if pw else None)
    wind = [(0.2, low(0)), (0.35, low(0.5, 5, -3)), (0.5, low(1, 0.2, 2)), (0.65, low(1.5, 5, -3)), (0.8, low(2, 0.2, 2)), (1.0, low(2.5))]
    # the gallop: the hind pair and then the fore pair, a bound, twice; its body rocking with it
    act = []
    for j in range(1, 9):
        f = j / 8
        u = (2 * f) % 1.0
        groups = {2: 0.0, 3: 0.08, 0: 0.5, 1: 0.58}
        feet, flex = _gait(u, 30.0, 0.42, 9.0, groups)
        rock = math.sin(2 * math.pi * u)
        act.append((f, _pose(r, lift=-2.4 + 0.8 * rock, pitch=6 + 4 * rock, bend=3 - 2 * rock, head=(16 + 3 * rock, 0, 0), door=8, stack=(-6 - 3 * rock, 0),
                             chain=(16 + 8 * rock, 6 * rock), feet=feet, hands=flex)))
    brace = {0: (LEGX, 5.3, -14.0), 1: (-LEGX, 5.3, -14.0)}
    skid = _pose(r, lift=-3.6, pitch=-3, bend=-4, shift=(0, 2.0), head=(-6, 0, 0), door=24, stack=(-12, 0), chain=(20, 0), feet=brace, poles={0: (0, 0, -1), 1: (0, 0, -1)})
    rock_ = _pose(r, lift=-2.0, pitch=2, bend=2, head=(6, 0, 0), door=8, stack=(5, 0), chain=(-6, 0), feet={0: (LEGX, 5.3, -11.0), 1: (-LEGX, 5.3, -11.0)})
    mv = Move("charge", "HEAVY", 18, 14, 22, through=skid, title="Full Steam", hold=6, track=6.0, lock=12, advance=ADVANCE, rng=(5, 16), kinds=("gap",),
              damage=1.3, knock=1.5, hit=[("pilot", (0, 9, 0), 9.0), ("head", (0, 0, -8), 9.0)], plant=(),
              stages={"windup": wind, "active": act, "recover": [(0.5, rock_), (0.72, _mix(rock_, _pose(r), 0.75))]}, lag=_lag())
    return mv


WELD_FROM = (0.0, 2.9, 2.3)  # where the arc leaves it: its lamp, its head lowered (blocks: x, up, forward)


def weld(r):
    """The Arc Weld (PERILOUS, the signature): it sets its forehooves out wide, one and then the other, and lowers its
    head at you; its door drops open on the fire roaring inside, its wheels driving round, its whole body shaking; the
    arc burns from its lamp at you, its head following you; it closes its door with a clang."""
    wide = {0: (LEGX + 5, 5.3, -10.0), 1: (-LEGX - 5, 5.3, -10.0)}

    def set_(a, b, door=0.0, nod=4.0):
        f = {0: tuple(x + (y - x) * a for x, y in zip(REST_FOOT[0], wide[0])), 1: tuple(x + (y - x) * b for x, y in zip(REST_FOOT[1], wide[1]))}
        lifted = lambda q: 5.0 * math.sin(math.pi * q)
        f[0] = (f[0][0], f[0][1] + lifted(a) * (0 < a < 1), f[0][2])
        f[1] = (f[1][0], f[1][1] + lifted(b) * (0 < b < 1), f[1][2])
        return _pose(r, lift=-1.0 - 1.6 * min(a, b), pitch=1 + 2 * min(a, b), roll=1.5 * (a - b), side=2 * (b - a), head=(nod, 0, 0), door=door, stack=(-2, 0),
                     chain=(-4, 0), feet=f, poles={0: (0.3, 0, -1), 1: (-0.3, 0, -1)}, hands={k: 30.0 for k, q in ((0, a), (1, b)) if 0 < q < 1})

    def burn(a, b=0.0, door=74.0):  # a: the shake, b: the slow heave under it
        q = set_(1, 1, door, 9 + 2.0 * a - 2.0 * b)
        return _with(q, {"hips": {"rot": (3 + 1.0 * a + 1.0 * b, 0, 1.4 * a), "pos": (0.4 * a, -2.6 + 0.5 * a - 0.6 * b, 0)}, "chest": (2.5 - 2.0 * a + 1.5 * b, 2.0 * a, 0.8 * a),
                         "head": (9 + 2.0 * a - 2.0 * b, 2.5 * a, -2.0 * a), "door": (DOOR * (door + 5 * a), 0, 0), "stack": (-3 + 5 * a, 0, 3 * a),
                         "whistle": (4 * a, 0, 2.4 * a), "chain0": (4 * b, 6 * a, 0), "chain1": (4 * b, 7 * a, 0), "chain2": (4 * b, 8 * a, 0)})
    # as the door drops open the shaking takes it, harder and harder
    wind = [(0.15, set_(0.5, 0)), (0.3, set_(1, 0)), (0.45, set_(1, 0.5)), (0.6, set_(1, 1, 20, 6))] + \
           [(f, _mix(set_(1, 1, door, 8), burn(a, -0.6 * a * a, door), min(1.0, abs(a) * 1.3))) for f, a, door in ((0.7, 0.3, 34), (0.8, -0.5, 50), (0.9, 0.7, 62), (1.0, -0.85, 70))]
    act = [(f, burn(a, b)) for f, a, b in ((0.1, 1, 0.3), (0.2, -1, 0.6), (0.3, 0.8, 0.9), (0.4, -0.9, 1.0), (0.5, 1, 0.8), (0.6, -0.8, 0.4), (0.7, 0.9, 0.0),
                                           (0.8, -1, -0.4), (0.9, 0.7, -0.5), (1.0, 0, 0))]
    # the door clangs shut: the whole body heaves down and then jolts up with it, its head coming up
    sag = _with(burn(0, 1.2, 36), {"hips": {"rot": (5, 0, 0), "pos": (0, -3.4, 0)}, "chest": (4.5, 0, 0)})
    shut = _with(set_(1, 1, 0, 2), {"stack": (8, 0, 0), "hips": {"rot": (-0.5, 0, 0), "pos": (0, -1.0, 0)}, "chest": (-1.5, 0, 0), "chain0": (-8, 0, 0)})
    home_l = _pose(r, lift=-1.2, pitch=1, roll=1.5, side=-2, head=(3, 0, 0), feet={0: (LEGX + 2, 9.0, -8.0), 1: wide[1]}, poles={0: (0.3, 0, -1), 1: (-0.3, 0, -1)},
                   hands={0: 30.0})
    return Move("weld", "PERILOUS", 26, 30, 22, through=sag, title="Arc Weld", signature=True, track=3.0, ability="Arc Weld",
                origin=WELD_FROM, rng=(3, 16), kinds=("ranged",), damage=0, knock=0, plant=(),
                stages={"windup": wind, "active": act, "recover": [(0.4, shut), (0.65, home_l)]}, lag=_lag())


def rivets(r):
    """Rivet Rain: it sits back and rears its head, its chimney to the sky, its door shut; and it chuffs, five times,
    its whole body pumping with each one, its wheels driving round, its door rattling, and the rivets go up out of its
    chimney and come down."""
    sit = _pose(r, lift=-2.6, pitch=-4, bend=-6, head=(-18, 0, 0), stack=(-6, 0), chain=(-6, 0))

    def chuff(a):  # a: 1 at the chuff's blast, 0 between
        return _pose(r, lift=-2.6 - 1.2 * a, pitch=-4 + 1.5 * a, bend=-6 + 3.5 * a, head=(-18 + 5 * a, 0, 0), door=6 * a, stack=(-6 + 9 * a, 0), chain=(-6 + 8 * a, 0),
                     whistle=2 * a)
    act = []
    for j in range(5):
        f0 = j / 5
        act += [(f0 + 0.06, chuff(1.0)), (f0 + 0.2, chuff(0.0))]
    act[-1] = (1.0, chuff(0.0))
    return Move("rivets", "NONE", 14, 24, 16, through=_mix(chuff(0), _pose(r), 0.3), title="Rivet Rain", track=4.0, ability="Rivet Rain", rng=(3, 14),
                kinds=("ranged", "area"), damage=0, knock=0, plant=(), stages={"windup": [(0.5, _mix(_pose(r), sit, 0.6)), (1.0, sit)], "active": act,
                                                                           "recover": _back(r, _mix(chuff(0), _pose(r), 0.3))}, lag=_lag())


def drones(r):
    """Wake the Drones: it throws its head up and blows its whistle, long, its door chattering and its body shaking
    with it, pawing the floor; and the drones wake."""
    up = lambda a, pw=0.0: _pose(r, lift=-1.0 + 0.4 * a, pitch=-2.5 + 0.8 * a, bend=-4 - 1.5 * a, roll=1.5 * a, yaw=2 * a, head=(-24 + 4 * abs(a), 12 * a, 5 * a),
                                  door=10 + 8 * abs(a), stack=(-8 + 3 * abs(a), 4 * a), whistle=12 * a, chain=(6, 12 * a), side=4 * a, feet={0: _foot(0, up=pw, dz=-2 if pw else 0)} if pw else None, hands={0: 35.0} if pw else None)
    act = [(0.12, up(1, 6)), (0.25, up(-1)), (0.37, up(1, 6)), (0.5, up(-1)), (0.62, up(0.8)), (0.75, up(-0.7)), (0.88, up(0.5)), (1.0, up(-0.3))]
    return Move("drones", "NONE", 12, 26, 14, through=_mix(up(0), _pose(r), 0.3), title="Wake the Drones", track=0.0, ability="Wake the Drones", rng=(0, 99),
                kinds=("summon",), damage=0, knock=0, plant=(), stages={"windup": [(0.45, up(0.3)), (1.0, up(0))], "active": act,
                                                                      "recover": _back(r, _mix(up(0), _pose(r), 0.3))}, lag=_lag())


def wake(r):
    """Its intro, once it has got up: its door opens a crack on the fire as it catches; it lifts its head and looks
    about for you, its lamp turning; it stamps a forehoof, twice, its wheels jerking round; it throws its head up and
    blows its whistle."""
    look = lambda a: _pose(r, lift=-1.2 - 0.4 * abs(a), pitch=1, head=(4 - 3 * abs(a), 24 * a, 5 * a), yaw=5 * a, side=7 * a, roll=-2.5 * a, door=10, stack=(1, 5 * a),
                           chain=(2, -14 * a))
    st = lambda up: _pose(r, lift=-1.6 - 0.6 * (up == 0), pitch=1.5, head=(6, 4, 0), door=12, stack=(3 * (up == 0), 0), feet={1: _foot(1, up=up, dz=-3 if up else -1)},
                          hands={1: 40.0} if up else None)
    blow = _pose(r, lift=-1.0, pitch=-2.5, bend=-4, head=(-22, 0, 0), door=18, stack=(-8, 0), whistle=10, chain=(8, 0))
    # and it shakes its head out, as a horse does, settling
    toss = _pose(r, lift=-0.8, pitch=-1, side=3, roll=1, head=(-8, 12, 7), door=8, stack=(-2, 6), whistle=4, chain=(4, -10))
    settle = _pose(r, lift=-0.6, side=-2, roll=-0.6, head=(2, -6, -3), door=3, stack=(1, -3), chain=(0, 6))
    return Move("wake", "NONE", 14, 30, 14, through=toss, track=0.0, rng=(0, 99), kinds=("intro",), damage=0, knock=0, plant=(),
                stages={"windup": [(0.3, _mix(_pose(r, door=8), look(-1), 0.3)), (0.65, _mix(_pose(r, door=8), look(-1), 0.75)), (1.0, look(-1))],
                        "active": [(0.2, look(1)), (0.36, st(9)), (0.46, st(0)), (0.56, st(9)), (0.66, st(0)), (0.82, blow), (1.0, _mix(blow, _pose(r), 0.3))],
                        "recover": [(0.5, settle)]}, lag=_lag())


# the ground (SPEC §35.10): the hooves stay out of the floor (the per-tick solve, then a flex of the fetlock); the
# cowcatcher folds back off it
HOOF_GROUND = [(HOOF[k], [(LIMB[k][2], 0, 40)]) for k in range(4)]
PILOT_GROUND = [(["pilot", "pilotside"], [("pilot", 0, 90, 1)])]
STANDS = {"stamp": (0, 2, 3), "stamp_b": (1, 2, 3), "slam": (2, 3), "buck": (0, 1), "charge": (), "weld": (2, 3), "rivets": (0, 1, 2, 3), "drones": (1, 2, 3),
          "wake": (0, 2, 3)}


def clips(r):
    _idle(r)
    _locomotion(r)
    _reactions(r)
    mv = [stamp(r, 1, "stamp"), stamp(r, 0, "stamp_b"), slam(r), buck(r), charge(r), weld(r), rivets(r), drones(r), wake(r)]
    by = {m.id: m for m in mv}
    for m in mv:
        stage_clips(r, m)
        if STANDS[m.id]:
            _plant(r, core.clip_name(m), STANDS[m.id])
    # the wheels: they spin up as it rears and run down after the slam, race through Full Steam, drive round through the
    # arc and with each chuff, and jerk round as it wakes
    sl, ch, we, ri, wk = by["slam"], by["charge"], by["weld"], by["rivets"], by["wake"]
    W, A = sl.seconds("windup"), sl.seconds("active")
    _drive(r, core.clip_name(sl), [(0.2, 0.0), (W, 900.0), (W + A, 500.0), (W + A + 0.6, 0.0)])
    W, A = ch.seconds("windup"), ch.seconds("active")
    _drive(r, core.clip_name(ch), [(0.1, 0.0), (W, 900.0), (W + 0.1, 1400.0), (W + A, 1400.0), (W + A + 0.7, 0.0)])
    W, A = we.seconds("windup"), we.seconds("active")
    _drive(r, core.clip_name(we), [(0.6 * W, 0.0), (W, 200.0), (W + A, 200.0), (W + A + 0.4, 0.0)])
    W, A = ri.seconds("windup"), ri.seconds("active")
    _drive(r, core.clip_name(ri), [(W, 0.0)] + [(W + A * (j / 5 + d), v) for j in range(5) for d, v in ((0.02, 600.0), (0.14, 0.0))])
    W, A = wk.seconds("windup"), wk.seconds("active")
    _drive(r, core.clip_name(wk), [(W + 0.3 * A, 0.0), (W + 0.4 * A, 500.0), (W + 0.5 * A, 0.0), (W + 0.56 * A, 500.0), (W + 0.68 * A, 0.0)])
    everyday = [n for n in r.clips if n != "death"]
    for n in r.clips:
        _on_floor(r, n)
    core.ground_pass(r, HOOF_GROUND + PILOT_GROUND, clips=everyday)
    core.ground_pass(r, HOOF_GROUND + PILOT_GROUND, clips=["death"], skip=())
    return mv


# Its sounds (SPEC §35.8): iron and pistons, the blast furnace's fire and the hiss of steam, an anvil's weight under it,
# a horse's anger pitched down into the iron, the raid horn high for its whistle; chosen on paper, not heard in game.
SOUNDS = {
    "tell_light": [("block.piston.contract", 0.7, 0.7), ("entity.iron_golem.step", 0.8, 0.6)],
    "tell_heavy": [("block.piston.extend", 1.0, 0.5), ("entity.horse.angry", 0.9, 0.5)],
    "tell_perilous": [("entity.guardian.attack", 1.2, 0.6), ("block.blastfurnace.fire_crackle", 1.6, 0.6), ("block.lava.extinguish", 1.0, 0.6)],
    "swing_light": [("entity.iron_golem.attack", 0.9, 0.7), ("block.lava.extinguish", 0.5, 1.2)],
    "swing_heavy": [("entity.iron_golem.attack", 1.0, 0.5), ("block.lava.extinguish", 0.9, 0.6)],
    "cast": [("block.respawn_anchor.charge", 1.0, 0.7), ("block.lava.extinguish", 1.0, 0.8)],
    "impact": [("block.anvil.land", 1.0, 0.6), ("item.mace.smash_ground_heavy", 0.8, 0.6), ("entity.iron_golem.step", 1.0, 0.5)],
    "roar": [("event.raid.horn", 1.0, 1.4), ("entity.horse.angry", 1.0, 0.5)],
    "step": [("entity.iron_golem.step", 0.6, 0.7), ("block.chain.step", 0.4, 0.6)],
}
MOVE_SOUNDS = {"weld": {"swing": "cast"}, "rivets": {"swing": "cast", "impact": ""}, "drones": {"swing": "roar", "impact": ""}, "wake": {"swing": "roar"}}

FIGHT = {
    "intro": {"pose": "kneel", "move": "wake"},  # it waits for you lying cold in its stall, and gets up as its fire catches
    "phases": [
        {"pool": [
            {"move": "stamp", "weight": 3, "when": "near", "then": [["stamp_b"], ["stamp_b", "slam"]]},
            {"move": "slam", "weight": 2, "when": "near"},
            {"move": "charge", "weight": 3, "when": "far", "then": [["stamp"]]},
            {"move": "weld", "weight": 2, "when": "far"},
            {"move": "buck", "weight": 3, "when": "behind"},
        ]},
        # the machines wake with the second phase, as in DeepBestiary: the drones, and the rivets
        {"transition": "drones", "pool": [
            {"move": "stamp", "weight": 3, "when": "near", "then": [["stamp_b"], ["stamp_b", "slam"]]},
            {"move": "slam", "weight": 2, "when": "near"},
            {"move": "charge", "weight": 2, "when": "far", "then": [["stamp", "stamp_b"]]},
            {"move": "rivets", "weight": 2},
            {"move": "weld", "weight": 1, "when": "far"},
            {"move": "drones", "weight": 1},
            {"move": "buck", "weight": 3, "when": "behind"},
        ]},
    ],
    "enrage": {"below": 0.15, "speed": 1.2},
}


def design():
    r = body()
    r.pack()
    mv = clips(r)
    return {"rig": r, "plan": None, "moves": mv, "fight": FIGHT, "paint": paint, "scale": SCALE, "half": 1.45, "sounds": SOUNDS,
            "move_sounds": MOVE_SOUNDS}


def probe():
    r = body()
    fr = core.frames(r, {})
    for b, p in (("hand0", (0, 0, 0)), ("foot2", (0, 0, 0)), ("head", (0, 0, 0)), ("stack", (0, -14, 0)), ("wheel0", (0, 0, 0))):
        print(b, p, [round(v, 2) for v in core.body(core.point(fr, b, p))])
    print("faces", core.faces(r), "cubes", sum(len(b.cubes) for b in r.bones), "bones", len(r.bones))
    print(core.floating(r))


if __name__ == "__main__":
    probe()
