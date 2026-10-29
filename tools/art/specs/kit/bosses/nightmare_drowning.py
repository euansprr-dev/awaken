"""The Undertow (SPEC §35.11, docs/bosses/ROLLOUT.md): a Nightmare, a Caster, the fear of drowning. The Old Snapper,
picked 2026-10-29: "Pulls you under and holds you there."

An alligator snapping turtle as big as a boat, so old that the bottom of the river has grown over it. Its shell is three
rows of ridged keels, black-green with slime and patched with weed, with weed hanging off its rim in long rags and a
length of rusted chain still caught on it; at the back the shell's edge is a row of teeth. Its head is huge and flat,
a hooked beak at the front and little eyes high up on the sides, its skin grey-brown and warty. In its mouth, on its
tongue, is a worm of cold teal light: it lies with its jaws open and the worm wriggling, and whatever comes to the light
it takes. It never touches the ground: it swims through the air the way it swims at the bottom of a river, slowly,
paddling with clawed webbed feet, and its weed and its chain trail after it.

Moves (SPEC §35.11): the Snap and the Rake (LIGHT, a string: it draws its head back into its shell and its neck shoots
out and its beak snaps shut on you; it rolls toward you and rakes a fore claw down across you); the Lunge (HEAVY, its gap
closer: it draws everything in, holds, and shoots forward with one stroke of all four feet, its mouth wide); the
Undertow (it opens its mouth wide and the worm on its tongue wriggles, and the water pulls you in, until it snaps shut);
the Rush of Water (it rears its front up and sweeps both forefeet down together, and the water rushes out ahead);
Lungful (into the second phase: it draws its head and legs into its shell and sinks, trembling, and bursts out of it
gaping, and the air goes out of you); Black Water (it lifts its head and spits black water, four times); for whoever is
behind it, the Tail (it swings round and sweeps its saw-ridged tail across); and Which Way Is Up (the signature: it rises
over you and rolls over onto its back, its feet paddling at nothing, and drops on you shell first). Its intro: it lies
on the floor like a rock grown over with weed, its head and legs drawn in; its head slides out; its mouth opens and the
worm wriggles; it shuts it, puts out its feet and lifts off the floor. Broken, it sinks to the floor and draws in. Its
death: it gapes and paddles at nothing, and slowly rolls over onto its back as it sinks, and lies there belly up, its
feet curling, until one last kick.
"""
import math

from kit import core, parts
from kit.core import Move, keyed, stage_clips
import fauna_rig as R
from fauna_rig import Ramp, hexc, SIDES

ID = "nightmare_drowning"
SCALE = 2.0  # the def's scale; the body is modelled at true size and the hitbox set round its shell

# The Old Snapper (docs/bosses/ROLLOUT.md, A). Its shell 42 px long and 34 wide (2.6 by 2.1 blocks), 6 blocks from the
# tip of its beak to the end of its tail with its neck out; it swims with its belly 1.3 blocks over the floor. Value
# story: the worm of light on its tongue brightest, then its eyes; then the pale of its throat and belly and the tips
# of its keels where the slime wears off; its shell and skin dark and mottled; the inside of its mouth and the weed's
# shadows darkest.
HOVER = 26.0
SIDE = (1, -1, 1, -1)  # legs: 0 front left (+x), 1 front right, 2 back left, 3 back right
FRONT = (True, True, False, False)
NWEED = 6
WEED_AT = [(17.5, -9, 1), (17.5, 9, 1), (-17.5, -6, -1), (-17.5, 11, -1), (8, 21.5, 0), (-10, 21.5, 0)]  # (x, z, side; 0 is the back)
CHAIN = ["chain0", "chain1", "chain2"]
TRAIL = [f"weed{i}" for i in range(NWEED)] + CHAIN
SIDES_ = (("l", 1), ("r", -1))


def _bone(parent, name, x, up, z, rot=(0, 0, 0)):
    return parent.bone(name, (x, -up, z), rot)


def _box(bone, name, cx, y0, cz, w, h, d, mirror=False):
    return bone.cube(name, cx - w / 2, -(y0 + h), cz - d / 2, w, h, d, mirror=mirror)


def body():
    r = R.Rig(ID, tex=(256, 256))
    base = r.bone("base", (0, 24, 0))
    fl = _bone(base, "float", 0, HOVER, 0)
    sh = _bone(fl, "shell", 0, 0, 0)
    # its shell: the plastron under it, the carapace rising in a low dome to a crown, three rows of keels, a row of
    # teeth along its back edge
    _box(sh, "plastron", 0, -5, -1, 26, 2, 34)
    _box(sh, "shell", 0, -3, 0, 34, 9, 42)
    _box(sh, "dome", 0, 6, 1, 26, 4, 34)
    _box(sh, "crown", 0, 10, 2, 14, 2, 24)
    for i in range(5):
        _box(sh, f"keelm{i}", 0, 12, -10 + 6 * i, 3, 3, 4)
    for nm, sg in SIDES_:
        for i in range(4):
            _box(sh, f"keel{nm}{i}", sg * 9, 10, -9 + 7 * i, 3, 3, 4)
        _box(sh, f"rim_{nm}", sg * 17.5, -2, 1, 1, 5, 38)
    for i, x in enumerate((-12, -6, 0, 6, 12)):
        _box(sh, f"serr{i}", x, -2, 22.5, 4, 5, 3)
    for i, (x, z) in enumerate(((-6, -6), (8, 4), (-3, 12))):  # tufts of weed grown on top of it
        _box(sh, f"tuft{i}", x, 10 if abs(x) < 7 else 9, z, 5, 2, 5)
    # weed hanging off its rim in rags, and a length of rusted chain caught on its back
    for i, (x, z, s) in enumerate(WEED_AT):
        w = _bone(sh, f"weed{i}", x, -1, z)
        _box(w, f"weed{i}", 0, -16 + (i % 3) * 2, 0, 1 if s else 6, 16 - (i % 3) * 2, 6 if s else 1)
    c0 = _bone(sh, "chain0", 15, -2, 16)
    _box(c0, "chain0", 0, -4, 0, 2, 4, 1)
    c1 = c0.bone("chain1", (0, 4, 0))
    _box(c1, "chain1", 0, -4, 0, 1, 4, 2)
    c2 = c1.bone("chain2", (0, 4, 0))
    _box(c2, "chain2", 0, -4, 0, 2, 4, 1)
    _box(c2, "shackle", 0, -7, 0, 4, 3, 1)
    # its neck, thick and wrinkled, coming out of the front of its shell; its head, huge and flat, a hooked beak
    n0 = _bone(sh, "neck0", 0, 0, -21)
    _box(n0, "neck0", 0, -5.5, -3, 13, 11, 6)
    _box(n0, "nape", 0, 5.5, -3, 11, 1, 5)
    n1 = n0.bone("neck1", (0, 0, -6))
    _box(n1, "neck1", 0, -5, -2.5, 12, 10, 5)
    _box(n1, "throat", 0, -6, -2.5, 10, 1, 5)
    hd = _bone(n1, "head", 0, 1, -5)
    _box(hd, "skull", 0, -3, -7.5, 18, 9, 15)
    _box(hd, "snout", 0, -2, -17.5, 11, 7, 5)
    _box(hd, "hook", 0, -5.5, -20.5, 5, 7, 2)
    for nm, sg in SIDES_:
        _box(hd, f"brow_{nm}", sg * 5.5, 6, -9, 4, 2, 6)
        _box(hd, f"eye_{nm}", sg * 9.5, 2, -11, 1, 2, 3)
        _box(hd, f"cheek_{nm}", sg * 8, -3, -2.5, 4, 8, 5)
    jw = _bone(hd, "jaw", 0, -3, -1)
    _box(jw, "jaw", 0, -3, -9, 16, 3, 17)
    _box(jw, "chin", 0, -3, -18.5, 7, 4, 2)
    l0 = _bone(jw, "lure0", 0, 0, -12)
    _box(l0, "lure0", 0, 0, -1.5, 2, 1, 3)
    l1 = l0.bone("lure1", (0, 0, -3))
    _box(l1, "lure1", 0, 0, -1.5, 1, 1, 3)
    # its legs: thick and scaled, out to the sides; webbed feet, three claws on each
    for k in range(4):
        front = FRONT[k]
        # the upper leg goes out nearly flat from under the shell's rim, the elbow (or knee) turns the lower leg down
        a = _bone(sh, f"leg{k}a", SIDE[k] * (13 if front else 12), -2, -13 if front else 14, (10 if front else -10, 0, -SIDE[k] * 70))
        _box(a, f"leg{k}a", 0, -9, 0, 7 if front else 8, 9, 7 if front else 8)
        _box(a, f"scale{k}", 0, -5, -1 if front else 1, 8 if front else 9, 4, 6)
        b = a.bone(f"leg{k}b", (0, 9, 0), (0, 0, SIDE[k] * 58))
        _box(b, f"leg{k}b", 0, -8, 0, 6, 8, 6)
        f = b.bone(f"foot{k}", (0, 8, 0), (0, 0, SIDE[k] * 8))
        fw = 7 if front else 8
        _box(f, f"foot{k}", 0, -2, -1, fw, 2, fw)
        for j, x in enumerate((-3, 0, 3)):
            _box(f, f"claw{k}{j}", x, -2, -1 - fw / 2 - 1.5, 1, 1, 3)
    # its tail: long, thick at the root, a saw-toothed ridge along its top
    t0 = _bone(sh, "tail0", 0, -1, 21, (-15, 0, 0))
    _box(t0, "tail0", 0, -3, 4, 7, 6, 8)
    _box(t0, "tsp0", 0, 3, 4, 2, 3, 7)
    t1 = t0.bone("tail1", (0, 0, 8), (-5, 0, 0))
    _box(t1, "tail1", 0, -2.5, 4, 5, 5, 8)
    _box(t1, "tsp1", 0, 2.5, 4, 1, 3, 7)
    t2 = t1.bone("tail2", (0, 0, 8), (-5, 0, 0))
    _box(t2, "tail2", 0, -1.5, 3.5, 3, 3, 7)
    _box(t2, "tsp2", 0, 1.5, 3.5, 1, 2, 6)
    r.look = [("head", 0.5, 0.35), ("neck1", 0.25, 0.2), ("neck0", 0.15, 0.1)]
    r.shadow = 2.6
    r.size = 1.0
    # secondary parts (SPEC §35.10): its weed and its chain trail after it, its tail's end lags
    r.springs = [(f"weed{i}", 0.5, 1.4, 0.18) for i in range(NWEED)] + [("chain0", 0.4, 1.8, 0.14), ("chain1", 0.5, 1.8, 0.14), ("chain2", 0.6, 1.8, 0.12),
                                                                     ("tail2", 0.4, 1.6, 0.1)]
    r.guard = [f"foot{k}" for k in range(4)] + ["head", "jaw", "tail2", "shell", "chain2"] + [f"weed{i}" for i in range(NWEED)]
    return r


# ---------------------------------------------------------------------------------------------------------------------
# Paint

SHELL = Ramp("#0a0c08", "#11150e", "#181e13", "#202818", "#29321e", "#333d25", "#3e482d", "#4b5537", "#5a6343", "#6b7350", "#7e855f")
SKIN = Ramp("#0d0c0a", "#161411", "#1f1c18", "#29261f", "#343028", "#403b31", "#4d473b", "#5b5446", "#6a6251", "#7a715d")
BELLY = Ramp("#1c1a10", "#2a2617", "#3a3520", "#4b452b", "#5d5636", "#706842", "#857c50")
HORN = Ramp("#0b0a08", "#151310", "#201d18", "#2c2821", "#39342b", "#48423a")
WEED = ("#08140a", "#0f2410", "#173614", "#20481a", "#2c5a22", "#3d6e2e", "#52853c")
RUST = Ramp("#1a110b", "#291a10", "#3b2617", "#50331e", "#674327", "#7d5432")
MOUTH = Ramp("#06090a", "#0b1113", "#111a1d", "#182428")
TEAL = ("#e6fffb", "#a8fff4", "#5ff0e0", "#3dd8c8", "#22a89c", "#137068")


def _n(i, j, seed):
    return (math.sin(i * 2.1 + seed) * math.sin(j * 1.7 + seed * 1.3) + 0.6 * math.sin((i + 3 * j) * 0.9 + seed * 2.3)) / 1.6


def _shell_pattern(seed, top=True):
    """Its shell's scutes: a darker seam round each plate, growth rings inside it, the slime and weed mottling over it."""
    def pat(f, i, j):
        si, sj = i % 9, j % 10
        if si == 0 or sj == 0:
            return -1.6
        d = math.hypot(si - 4.5, sj - 5)
        v = 0.45 * math.sin(d * 1.4 + seed) + 0.35 * _n(i, j, seed)
        if f.name in SIDES:
            v -= 0.3 * j / max(1, f.h - 1)
        return v
    return pat


def _skin_pattern(seed):
    """Grey-brown skin, blotched darker, its warts the pale points."""
    def pat(f, i, j):
        v = 0.5 * _n(i * 0.7, j * 0.7, seed)
        if (i * 7 + j * 13 + seed) % 17 == 0:
            v += 1.3
        elif (i * 5 + j * 3 + seed) % 11 == 0:
            v -= 0.8
        return v
    return pat


def _weed(f, gl, seed, glow_tips=False):
    """Weed in rags: green-black, lighter along its middle, its end torn into strips (see-through), the tips sometimes
    glowing the teal of the worm's light."""
    for i, j in f.each():
        s = j / max(1, f.h - 1)  # 0 where it hangs from, 1 at its end
        lim = 0.7 + 0.25 * math.sin(i * 2.7 + seed) + 0.1 * math.sin(i * 5.3 + seed)
        if s > lim or (s > 0.3 and _n(i, j, seed) > 0.55):
            f.set(i, j, hexc(WEED[0], 0))
            continue
        k = int(round(2.5 + 2.0 * (0.5 - abs(i - (f.w - 1) / 2) / max(1.0, f.w / 2)) + 1.2 * _n(i, j, seed * 1.3) - 1.5 * s))
        f.set(i, j, hexc(WEED[max(0, min(len(WEED) - 1, k))], 255))
        if glow_tips and s > lim - 0.12 and (i + seed) % 2 == 0:
            f.set(i, j, hexc(TEAL[3], 255))
            gl.set(i, j, hexc(TEAL[3], 150))


def paint(r, P_):
    """Its shell black-green with slime, each scute seamed and ringed and patched with weed, the keels' and the back
    teeth's tips worn paler; its skin grey-brown, blotched and warty, its throat and belly a dirty yellow; its beak dark
    horn; inside its mouth near black, the worm on its tongue the cold teal light of the drowned; its eyes small and
    teal; weed in green-black rags; the chain red with rust."""
    of = lambda *ps: parts.cubes_of(r, *ps)
    for k, c in enumerate(["shell", "dome", "crown"] + of("rim_")):
        P_.paint(c, SHELL, 4.2, _shell_pattern(k * 5), grad=1.4, rim=1.0, ao=1.0, noise=0.5, nscale=3)
    for k, c in enumerate(of("keel", "serr")):
        P_.paint(c, SHELL, 4.6, lambda f, i, j, k=k: (1.6 if f.name == "top" or j == 0 else 0) + 0.3 * _n(i, j, k), grad=1.6, rim=1.4, ao=1.2, noise=0.3, nscale=2)
    P_.paint("plastron", BELLY, 2.6, lambda f, i, j: -1.2 if i % 8 == 0 or j % 9 == 0 else 0.3 * _n(i, j, 3), grad=0.8, rim=0.6, ao=0.6)
    for c in of("tuft"):
        P_.paint(c, SHELL, 3.0, None, grad=0.6, rim=0.6, ao=0.4)
        for fname in ("top",) + SIDES:
            f = P_.face(c, fname)
            for i, j in f.each():
                f.set(i, j, hexc(WEED[2 + (i * 3 + j * 5) % 4], 255))
    # slime and weed grown over the shell in patches, a few points of the worm's teal light in it
    for c in ("shell", "dome", "crown"):
        for fname in ("top", "east", "west", "north", "south"):
            f, gl = P_.face(c, fname), P_.face(c, fname, glow=True)
            for i, j in f.each():
                n = _n(i * 0.45, j * 0.45, len(c) + len(fname))
                if n > 0.42:
                    f.set(i, j, hexc(WEED[1 + int((n - 0.42) * 8) % 4], 255))
                    if (i * 11 + j * 7 + len(c)) % 37 == 0:
                        f.set(i, j, hexc(TEAL[3], 255))
                        gl.set(i, j, hexc(TEAL[3], 160))
    skin = ["neck0", "nape", "neck1", "skull"] + of("brow_", "cheek_", "leg", "scale")
    for k, c in enumerate(skin):
        P_.paint(c, SKIN, 4.3, _skin_pattern(k * 3), grad=1.4, rim=1.2, ao=1.0, noise=0.4, nscale=3)
    for c in ("throat",):
        P_.paint(c, BELLY, 3.0, _skin_pattern(2), grad=0.6, rim=0.4, ao=0.4)
    for c in ("neck0", "neck1"):  # its throat's underside is the pale of its belly
        f = P_.face(c, "bottom")
        for i, j in f.each():
            f.set(i, j, BELLY.at(3.0 + 0.5 * _n(i, j, 4)))
    # the folds in its neck, soft rings round it
    for c in ("neck0", "neck1"):
        for fname in ("east", "west", "top"):
            f = P_.face(c, fname)
            for i, j in f.each():
                ring = (i + int(round(1.2 * math.sin(j * 0.7 + len(c))))) if fname != "top" else (j + int(round(1.2 * math.sin(i * 0.7))))
                if ring % 4 == 0:
                    f.set(i, j, SKIN.at(2.6 + 0.4 * _n(i, j, 1)))
    # its beak: dark horn, its cutting edges paler
    for c in ("snout", "hook", "jaw", "chin"):
        P_.paint(c, HORN if c != "jaw" else SKIN, 3.0, lambda f, i, j: 1.2 if j == f.h - 1 and f.name in SIDES else 0.2 * _n(i, j, 7), grad=1.0, rim=1.2, ao=0.8)
    f = P_.face("jaw", "bottom")
    for i, j in f.each():
        f.set(i, j, BELLY.at(2.6 + 0.6 * _n(i, j, 9)))
    # inside its mouth: near black, the roof and the floor of it, the worm's light on them
    for c, fname in (("jaw", "top"), ("skull", "bottom"), ("snout", "bottom"), ("chin", "south"), ("hook", "south")):
        f, gl = P_.face(c, fname), P_.face(c, fname, glow=True)
        for i, j in f.each():
            f.set(i, j, MOUTH.at(1.5 + 0.8 * _n(i, j, 2)))
            if c == "jaw" and abs(i - (f.w - 1) / 2) < 3 and (i + j) % 3 == 0:
                gl.set(i, j, hexc(TEAL[4], 90))
    # the worm of light on its tongue
    for c in ("lure0", "lure1"):
        for fname in ("top", "bottom") + SIDES:
            f, gl = P_.face(c, fname), P_.face(c, fname, glow=True)
            for i, j in f.each():
                col = TEAL[1] if (i + j) % 2 == 0 else TEAL[2]
                if c == "lure1" and fname in ("north",):
                    col = TEAL[0]
                f.set(i, j, hexc(col, 255))
                gl.set(i, j, hexc(col, 255))
    # its eyes: small, set high on the sides, teal with a dark slit
    with P_.eyes():
        for nm, fname in (("l", "east"), ("r", "west")):
            f, gl = P_.face(f"eye_{nm}", fname), P_.face(f"eye_{nm}", fname, glow=True)
            for i, j in f.each():
                col = "#06100f" if i == 1 else TEAL[2] if j == 0 else TEAL[3]
                f.set(i, j, hexc(col, 255))
                if i != 1:
                    gl.set(i, j, hexc(col, 220))
    # its feet: webbed, its claws dark horn with pale points
    for k in range(4):
        P_.paint(f"foot{k}", SKIN, 3.0, lambda f, i, j: (-1.0 if f.name == "top" and i % 3 == 1 and j > 1 else 0) + 0.3 * _n(i, j, k), grad=0.8, rim=0.8, ao=0.6)
    for c in of("claw"):
        P_.paint(c, HORN, 2.4, lambda f, i, j: 2.0 if (f.name in SIDES and i == 0) or f.name == "north" else 0, grad=0.6, rim=0.6, ao=0.2)
    # its tail: skin like its legs, the ridge a row of dark teeth (see-through between them)
    for k, c in enumerate(("tail0", "tail1", "tail2")):
        P_.paint(c, SKIN, 3.4, _skin_pattern(k * 7 + 1), grad=1.4, rim=1.0, ao=1.0, noise=0.3, nscale=2)
    for k, c in enumerate(("tsp0", "tsp1", "tsp2")):
        P_.paint(c, SHELL, 4.0, lambda f, i, j: 1.4 if j == 0 else 0, grad=1.2, rim=1.0, ao=0.6)
        for fname in ("east", "west"):
            f = P_.face(c, fname)
            for i, j in f.each():
                if j < (i % 3):  # the saw: each tooth's back edge cut away
                    f.set(i, j, hexc("#000000", 0))
    # weed and chain
    for k, c in enumerate(of("weed")):
        for fname in ("top", "bottom") + SIDES:
            _weed(P_.face(c, fname), P_.face(c, fname, glow=True), k * 5 + len(fname), glow_tips=k % 2 == 0)
    for k, c in enumerate(CHAIN + ["shackle"]):
        P_.paint(c, RUST, 3.0, lambda f, i, j: (1.4 if (i + j) % 4 == 0 else 0) - (1.0 if (i * 3 + j) % 5 == 0 else 0), grad=0.8, rim=1.0, ao=0.6)
        for fname in ("north", "south") if c != "chain1" else ("east", "west"):  # the link's hole
            f = P_.face(c, fname)
            for i, j in f.each():
                if 0 < j < f.h - 1 and 0 < i < f.w - 1:
                    f.set(i, j, hexc("#000000", 0))
    return P_.skin, P_.glow


# ---------------------------------------------------------------------------------------------------------------------
# Motion: one pose call (its body, neck and head, its jaw and the worm, its legs, its tail, its weed)

SHELLS = ("shell",)
SOLID = SHELLS + tuple(f"{n}{k}" for k in range(4) for n in ("leg", "foot")) + tuple(f"leg{k}b" for k in range(4)) + ("neck0", "neck1", "head", "jaw", "tail0",
                                                                                                                      "tail1", "tail2")
TUCK_IN = 4.0  # px its legs draw in toward its shell when it tucks


def _ease(f):
    f = max(0.0, min(1.0, f))
    return f * f * (3 - 2 * f)


def _wave(u, at, width):
    x = (u - at) / width
    return math.cos(math.pi * x) ** 2 if abs(x) < 0.5 else 0.0


def _pose(lift=0.0, shift=(0.0, 0.0), pitch=0.0, yaw=0.0, roll=0.0, neck=(0, 0, 0), head=(0, 0, 0), jaw=0.0, lure=(0.0, 0.0), legs=None, tuck=0.0,
          tail=(0, 0, 0), weed=0.0):
    """pitch lifts its nose (+), yaw turns it (+ to its right), roll turns it about its length (+ its right side down);
    lift raises it (px), shift moves it (x to its left, z back); neck (out, px, + reaching its head out of its shell
    and - drawing it in; a nod, + lowering its head; a turn, + to its right) and head (a nod, a turn, a tilt, its own);
    jaw opens its mouth (+); lure (a wriggle's swing, its phase) works the worm on its tongue; legs {leg: (a stroke, +
    forward; a raise, + up and out from its body; a curl, + bending the lower leg back; a flex of its foot)}; tuck draws
    its legs and head toward its shell (0 to 1); tail (a lift, + up; a swing, + to its left; a curl, + up toward its
    end); weed swings its weed and chain out from where they hang (degrees, 0 hanging, 85 lying out flat)."""
    out, nod, turn = neck
    out -= 10.0 * tuck
    p = {"float": {"rot": (-pitch, yaw, roll), "pos": (shift[0], lift, shift[1])},
         "neck0": {"rot": (0.4 * nod, 0.4 * turn, 0.0), "pos": (0.0, 0.0, -0.6 * out)},
         "neck1": {"rot": (0.6 * nod, 0.6 * turn, 0.0), "pos": (0.0, 0.0, -0.4 * out)},
         "head": tuple(head), "jaw": (jaw, 0.0, 0.0),
         "lure0": (-0.3 * abs(lure[0]), lure[0] * math.sin(lure[1]), 0.0), "lure1": (-0.2 * abs(lure[0]), 1.4 * lure[0] * math.sin(lure[1] - 1.2), 0.0),
         "tail0": (0.5 * tail[0] - 10 * tuck, 0.4 * tail[1], 0.0), "tail1": (0.3 * tail[0] + 0.5 * tail[2], 0.35 * tail[1], 0.0),
         "tail2": (0.2 * tail[0] + tail[2], 0.25 * tail[1], 0.0)}
    for k in range(4):
        st, ra, cu, fx = (tuple(legs[k]) + (0, 0, 0, 0))[:4] if legs and k in legs else (0, 0, 0, 0)
        ra -= 30 * tuck
        cu += 50 * tuck
        p[f"leg{k}a"] = {"rot": (-st, 0.0, -SIDE[k] * ra), "pos": (-SIDE[k] * TUCK_IN * tuck, 0.5 * TUCK_IN * tuck, (2 if FRONT[k] else -2) * tuck)}
        p[f"leg{k}b"] = (cu, 0.0, 0.0)
        p[f"foot{k}"] = (fx, 0.0, 0.0)
    for i, (x, z, s) in enumerate(WEED_AT):
        p[f"weed{i}"] = (weed if s == 0 else 0.0, 0.0, -s * weed)
    p["chain0"] = (0.6 * weed, 0.0, -0.6 * weed)
    p["chain1"] = (0.2 * weed, 0.0, 0.0)
    return p


REST = _pose()


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


def _on(base, p):
    """p, where base is."""
    q = dict(p)
    q["float"] = base["float"]
    return q


def _floor(r, p, gap=0.3, on=SOLID):
    """The pose let down (or lifted) until the lowest of it (or of the bones on) just rests on the floor."""
    fr = core.frames(r, core.as_sample(p))
    up = core.lowest(r, fr, on) - (core.GROUND - gap)
    q = dict(p)
    f = q["float"]
    q["float"] = {"rot": f["rot"], "pos": (f["pos"][0], f["pos"][1] + up, f["pos"][2])}
    return q


def _lift_clear(r, p, gap=0.5):
    """The pose as it is, or lifted just enough that nothing of it is under the floor."""
    fr = core.frames(r, core.as_sample(p))
    under = (core.GROUND - gap) - core.lowest(r, fr, SOLID)
    return p if under >= 0 else _floor(r, p, gap)


def _staged(mv, stage, f):
    W, A = mv.seconds("windup"), mv.seconds("active")
    return {"windup": 0.0, "active": W, "recover": W + A}[stage] + f * mv.seconds(stage)


def _legs(**kw):
    """Leg tuples by name: fl, fr, bl, br (front left and right, back left and right)."""
    ix = {"fl": 0, "fr": 1, "bl": 2, "br": 3}
    return {ix[k]: v for k, v in kw.items()}


def _paddle(u, offs, stroke, raise_, curl):
    """Its swimming stroke: each foot sweeps back low and flat (the power stroke), then comes forward raised and
    folded, feathered (the recovery)."""
    legs = {}
    for k, off in offs.items():
        ph = 2 * math.pi * (u - off)
        f = FRONT[k]
        rec = max(0.0, math.sin(ph))
        legs[k] = (stroke[f] * math.cos(ph), raise_[f] * math.sin(ph), curl[f] * rec, 20 * rec - 10 * max(0.0, -math.sin(ph)))
    return legs


DIAG = {0: 0.0, 3: 0.0, 1: 0.5, 2: 0.5}  # it paddles in diagonal pairs, the way a turtle does on the bottom
BOTH = {0: 0.0, 1: 0.0, 2: 0.15, 3: 0.15}  # hurrying, it strokes both forefeet together and its hind feet after them


# ---------------------------------------------------------------------------------------------------------------------
# Clips

def _idle(r):
    """It hangs in the water (8 s), bobbing and rocking a little, its feet paddling slowly in diagonal pairs to hold its
    place; it lowers its head and opens its mouth and the worm on its tongue wriggles, the way a snapper waits on the
    bottom, and shuts it; it draws its head in a little and turns it to look; its tail sways; its weed stirs."""
    L, n = 8.0, 160
    keys = []
    for k in range(n + 1):
        u = k / n
        t = L * u
        bob = math.sin(2 * math.pi * 0.25 * t)
        gape = _wave(u, 0.42, 0.34)
        look = _wave(u, 0.84, 0.18)
        legs = _paddle(u * 4, DIAG, {True: 9, False: 7}, {True: 6, False: 5}, {True: 14, False: 10})
        keys.append((t, _pose(lift=0.8 * bob, pitch=1.2 * math.sin(2 * math.pi * 0.25 * t + 0.8) + 2 * gape, roll=1.0 * math.sin(2 * math.pi * u),
                              neck=(1 - 3 * look + 1.5 * gape, 6 * gape - 4 * look, 22 * look * math.sin(2 * math.pi * 1.5 * u)),
                              head=(4 * gape - 3 * look, 8 * look, 3 * look), jaw=34 * gape, lure=(26 * gape, 2 * math.pi * 2.5 * t), legs=legs,
                              tail=(-4 + 2 * bob, 10 * math.sin(2 * math.pi * u), 4), weed=4 + 3 * bob)))
    keyed(r, "idle", L, keys, loop=True)


def _locomotion(r):
    """Its weed and chain stir always, as if in a current (the fly layer, under everything). It swims slowly, paddling
    in diagonal pairs, rocking (1.6 s, 1.4 blocks); hurrying, it strokes its forefeet together and its hind feet after,
    its neck out and its weed streaming back (0.9 s, 2.6 blocks)."""
    L, n = 2.0, 20
    keys = []
    for k in range(n + 1):
        u = k / n
        p = _pose()
        for i, (x, z, s) in enumerate(WEED_AT):
            a, b = 6 * math.sin(2 * math.pi * u + 1.1 * i), 5 * math.sin(2 * math.pi * 2 * u + 0.7 * i)
            p[f"weed{i}"] = (a, 0.0, b)
        p["chain0"] = (4 * math.sin(2 * math.pi * u + 0.5), 0.0, 3 * math.sin(2 * math.pi * u + 2.0))
        p["chain1"] = (5 * math.sin(2 * math.pi * u - 0.3), 0.0, 3 * math.sin(2 * math.pi * 2 * u))
        keys.append((L * u, p))
    keyed(r, "fly", L, keys, loop=True)
    L, n = 1.6, 24
    keys = []
    for k in range(n + 1):
        u = k / n
        s2 = math.sin(2 * math.pi * 2 * u)
        keys.append((L * u, _pose(lift=0.8 * s2, pitch=1.5 * s2 - 2, roll=2.0 * math.sin(2 * math.pi * u), neck=(2, 2 * math.sin(2 * math.pi * 2 * u + 1.0), 0),
                                  head=(1.5 * math.sin(2 * math.pi * 2 * u + 1.4), 0, 0),
                                  legs=_paddle(u, DIAG, {True: 26, False: 20}, {True: 14, False: 10}, {True: 34, False: 24}),
                                  tail=(0, 10 * math.sin(2 * math.pi * u + 0.6), 2), weed=10)))
    keyed(r, "walk", L, keys, loop=True)
    L, n = 0.9, 24
    keys = []
    for k in range(n + 1):
        u = k / n
        s = math.sin(2 * math.pi * u)
        keys.append((L * u, _pose(lift=1.6 * math.sin(2 * math.pi * u - 0.6), pitch=3 * math.sin(2 * math.pi * u + 0.4) - 4,
                                  neck=(5, 6 - 3 * math.sin(2 * math.pi * u + 0.8), 0), head=(-4, 0, 0),
                                  legs=_paddle(u, BOTH, {True: 40, False: 30}, {True: 22, False: 16}, {True: 44, False: 30}),
                                  tail=(6 + 2 * s, 6 * math.sin(2 * math.pi * u + 1.0), 4), weed=24)))
    keyed(r, "run", L, keys, loop=True)
    r.walk_stride, r.run_stride = 1.4, 2.6
    for nm, sg in (("turn_l", 1), ("turn_r", -1)):  # turning to its left, its right feet stroke and its left feet hold
        keys = []
        n = 20
        for k in range(n + 1):
            u = k / n
            legs = _paddle(u, DIAG, {True: 18, False: 14}, {True: 10, False: 8}, {True: 24, False: 18})
            for q in legs:
                if SIDE[q] == sg:
                    legs[q] = tuple(0.3 * v for v in legs[q])
            keys.append((1.2 * u, _pose(yaw=-5 * sg, roll=-3 * sg, neck=(2, 0, -16 * sg), head=(0, -8 * sg, 0), legs=legs, tail=(0, 16 * sg, 0), weed=6)))
        keyed(r, nm, 1.2, keys, loop=True)


def _lying(r, flat=1.0):
    """Down on the floor: its legs and head drawn half into its shell, its shell resting on the floor, its weed lying
    out round it."""
    p = _pose(neck=(-2, 6, 6), head=(4, 6, 0), jaw=2, legs={0: (10, 0, 0), 1: (8, 0, 0), 2: (-8, 0, 0), 3: (-10, 0, 0)}, tuck=0.6 * flat, tail=(-10, 14, 0),
              weed=85)
    return _floor(r, p)


def _reactions(r):
    """Struck, its head snaps back into its shell and its feet flinch in, and it lurches back; from the side it rolls
    away from the blow, its head turning to it. Broken, it sinks to the floor and draws in; it puts its feet out and
    pushes itself up off the floor with a stroke."""
    hit = _pose(lift=1, shift=(0, 3), pitch=5, neck=(-8, -6, 0), head=(-6, 0, 0), jaw=12, legs={0: (14, 20, 20), 1: (14, 20, 20), 2: (-10, 16, 16), 3: (-10, 16, 16)},
                tuck=0.3, tail=(8, 0, 4), weed=14)
    back = _pose(shift=(0, 1), pitch=-1, neck=(-1, 3, 0), head=(2, 0, 0), weed=6)
    keyed(r, "hurt", 0.6, [(0, REST), (0.08, hit), (0.32, back), (0.6, REST)])
    for nm, sg in (("hurt_l", 1), ("hurt_r", -1)):
        h = _pose(shift=(-4 * sg, 1), yaw=8 * sg, roll=10 * sg, neck=(-5, 0, 18 * sg), head=(-4, 8 * sg, 0), jaw=10,
                  legs={0: (6, 24 if sg > 0 else 4, 20), 1: (6, 4 if sg > 0 else 24, 20), 2: (-4, 14, 10), 3: (-4, 14, 10)}, tail=(4, -18 * sg, 0), weed=14)
        b = _pose(shift=(-1 * sg, 0.5), roll=2 * sg, neck=(0, 2, -4 * sg), weed=6)
        keyed(r, nm, 0.65, [(0, REST), (0.08, h), (0.34, b), (0.65, REST)])
    low = _lying(r)
    sink = _pose(lift=-6, pitch=-3, neck=(-4, 10, 0), head=(4, 0, 0), jaw=6, legs={0: (16, 20, 20), 1: (16, 20, 20), 2: (-12, 16, 16), 3: (-12, 16, 16)},
                 tuck=0.25, tail=(-4, 8, 0), weed=30)
    keyed(r, "kneel", 1.2, [(0, REST), (0.3, sink), (0.75, _mix(sink, low, 0.75)), (1.2, low)])
    push = _floor(r, _pose(pitch=4, neck=(1, 2, 0), head=(-2, 0, 0), legs={0: (-24, -10, 4, 10), 1: (-24, -10, 4, 10), 2: (-26, -8, 4, 10), 3: (-26, -8, 4, 10)},
                           tail=(-6, 6, 0), weed=60))
    up_ = _pose(lift=2, pitch=2, neck=(1, -2, 0), legs={0: (-30, 10, 30), 1: (-30, 10, 30), 2: (-24, 8, 24), 3: (-24, 8, 24)}, weed=20)
    keyed(r, "rise", 1.4, [(0, low), (0.4, _mix(low, push, 0.6)), (0.7, push), (1.05, up_), (1.4, REST)])
    _death(r)


def _death(r):
    """Its death: it gapes with its neck stretched up and paddles at nothing, faster, then slower; it rolls slowly over
    onto its back as it sinks, and lies there belly up on the floor, its head hanging out to one side and its mouth
    open, its feet curling slowly in; one of its hind feet kicks once; and it is still."""
    gape = lambda q: _pose(lift=-1, pitch=8, neck=(5, -16, 4 * q), head=(-10, 0, 6 * q), jaw=44, legs={0: (30 * q, 30, 30), 1: (-30 * q, 30, 30), 2: (-20 * q, 20, 20),
                                                                                                    3: (20 * q, 20, 20)}, tail=(6, 10 * q, 4), weed=14)
    over = lambda roll, sink, weed: _lift_clear(r, _pose(lift=-sink, roll=roll, neck=(3, -6, 10), head=(-4, 10, 0), jaw=30,
                                                         legs={0: (20, 40, 30), 1: (-10, 30, 40), 2: (10, 30, 30), 3: (-20, 30, 30)}, tail=(10, 10, 0), weed=weed))
    back = lambda curl, kick=0.0: _floor(r, _pose(roll=180, neck=(4, -20, 26), head=(-10, 20, 10), jaw=26,
                                                  legs={0: (20, 30 - curl * 0.3, 20 + curl), 1: (10, 30 - curl * 0.3, 20 + curl), 2: (-10 + kick, 25, 20 + curl - kick),
                                                        3: (-20, 25, 20 + curl)}, tail=(20, -10, 0), weed=170))
    keys = [(0, REST), (0.4, gape(1)), (0.75, gape(-1)), (1.1, gape(0.8)), (1.5, gape(-0.5)), (2.0, over(60, 8, 60)), (2.6, over(120, 14, 120)),
            (3.1, _mix(over(170, 18, 165), back(0), 0.7)), (3.5, back(0)), (4.0, back(20)), (4.4, back(26, 30)), (4.7, back(30)), (5.5, back(34))]
    keyed(r, "death", 5.5, keys)


# ---------------------------------------------------------------------------------------------------------------------
# Moves

HEAD_HIT = (0, 1, -17)
FOOT_HIT = (0, 1, -4)
TAIL_HIT = (0, 0, 6)
MOUTH_AT = (0.0, 1.5, 3.4)  # blocks (x, up, forward): its mouth


def _back(end):
    return [(0.45, _mix(end, REST, 0.45)), (0.72, _mix(end, REST, 0.82))]


def snap(r):
    """The Snap (LIGHT): it draws its head back into its shell, its feet bracing and its mouth just open, and its neck
    shoots out and its beak snaps shut on you."""
    draw = _pose(lift=1, shift=(0, 2), pitch=8, neck=(-9, -15, 0), head=(-16, 0, 0), jaw=12,
                 legs=_legs(fl=(24, 20, 34, 12), fr=(24, 20, 34, 12), bl=(16, 14, 22, 10), br=(16, 14, 22, 10)), tail=(12, 0, 8), weed=12)
    shut = _pose(lift=-1, shift=(0, -3), pitch=-5, roll=-4, neck=(12, 12, -4), head=(9, -3, 0), jaw=3,
                 legs=_legs(fl=(32, -6, 12, -10), fr=(32, -6, 12, -10), bl=(20, -4, 8), br=(20, -4, 8)), tail=(-6, -8, 2), weed=16)
    held = _pose(lift=-1.1, shift=(0, -3.2), pitch=-5.3, roll=-4.3, neck=(12.5, 12.5, -4.3), head=(9.4, -3.2, 0), jaw=3,
                 legs=_legs(fl=(33, -6, 12, -11), fr=(33, -6, 12, -11), bl=(21, -4, 8), br=(21, -4, 8)), tail=(-6, -8.5, 2), weed=16)
    return Move("snap", "LIGHT", 10, 5, 14, through=held, title="Snap", track=8.0, rng=(0, 4.5), kinds=("combo", "near"), damage=1.0, knock=0.6, plant=(),
                hit=[("head", HEAD_HIT, 7.0)],
                stages={"windup": [(0.2, _mix(REST, draw, 0.6)), (0.6, _mix(REST, draw, 0.9)), (1.0, draw)],
                        "active": [(0.4, _mix(draw, shut, 0.55) | {"jaw": (40.0, 0.0, 0.0)}), (1.0, shut)], "recover": _back(held)})


def rake(r):
    """The Rake (LIGHT, after the Snap): it rolls onto its right side toward you, its left forefoot raised high and
    forward with its claws spread, and rakes it down and back across you, rolling back."""
    up_ = _pose(lift=1, roll=-20, yaw=-10, neck=(2, -6, -16), head=(-4, -8, 0), jaw=14, legs=_legs(fl=(70, 60, 50, -30), fr=(-16, 16, 20), bl=(-10, 10, 10),
                                                                                                    br=(8, 8, 8)), tail=(4, -16, 0), weed=14)
    down = _pose(lift=-1, roll=8, yaw=6, neck=(4, 4, 6), head=(2, 4, 0), jaw=6, legs=_legs(fl=(-34, -6, 4, 10), fr=(6, 14, 14), bl=(4, 8, 6), br=(-6, 6, 6)),
                 tail=(2, 10, 0), weed=16)
    held = _pose(lift=-1.1, roll=8.5, yaw=6.4, neck=(4, 4.4, 6.4), head=(2, 4, 0), jaw=6, legs=_legs(fl=(-35, -6, 4, 11), fr=(6, 14, 14), bl=(4, 8, 6),
                                                                                                    br=(-6, 6, 6)), tail=(2, 10, 0), weed=16)
    return Move("rake", "LIGHT", 10, 5, 14, through=held, title="Rake", track=8.0, rng=(0, 4.0), kinds=("combo", "near"), damage=1.1, knock=0.8, plant=(),
                hit=[("foot0", FOOT_HIT, 6.0)],
                stages={"windup": [(0.3, _mix(REST, up_, 0.52)), (0.65, _mix(REST, up_, 0.88)), (1.0, up_)], "active": [(0.4, _mix(up_, down, 0.55)), (1.0, down)],
                        "recover": _back(held)})


ADVANCE = 8.0  # blocks the Lunge carries it


def lunge(r):
    """The Lunge (HEAVY, its gap closer): it draws its head into its shell and all four feet forward, coiled, and
    holds; then it shoots forward with one stroke of all four, its neck out and its mouth wide, and snaps it shut."""
    coil = _pose(lift=-1, shift=(0, 3), pitch=4, neck=(-9, -4, 0), head=(-2, 0, 0), jaw=6,
                 legs={0: (44, 24, 36, -10), 1: (44, 24, 36, -10), 2: (36, 18, 30, -10), 3: (36, 18, 30, -10)}, tail=(-6, 0, -4), weed=6)
    shoot = _pose(lift=1, shift=(0, -8), pitch=-6, neck=(10, 6, 0), head=(4, 0, 0), jaw=48,
                  legs={0: (-46, -6, 0, 20), 1: (-46, -6, 0, 20), 2: (-40, -4, 0, 20), 3: (-40, -4, 0, 20)}, tail=(10, 0, 6), weed=40)
    shut = _pose(lift=0, shift=(0, -11), pitch=-4, neck=(12, 8, 0), head=(6, 0, 0), jaw=3, legs={0: (-40, -2, 6, 14), 1: (-40, -2, 6, 14), 2: (-34, 0, 6, 14),
                                                                                                3: (-34, 0, 6, 14)}, tail=(8, 0, 4), weed=36)
    held = _pose(lift=0, shift=(0, -11.4), pitch=-4.3, neck=(12.4, 8.4, 0), head=(6.3, 0, 0), jaw=3,
                 legs={0: (-41, -2, 6, 14), 1: (-41, -2, 6, 14), 2: (-35, 0, 6, 14), 3: (-35, 0, 6, 14)}, tail=(8, 0, 4), weed=36)
    stop = _pose(shift=(0, -5), pitch=2, neck=(4, 2, 0), head=(0, 0, 0), legs={0: (20, 14, 20), 1: (20, 14, 20), 2: (14, 10, 14), 3: (14, 10, 14)}, weed=18)
    return Move("lunge", "HEAVY", 18, 10, 22, through=held, title="Lunge", hold=6, track=6.0, lock=12, advance=ADVANCE, rng=(5, 13), kinds=("gap",), damage=1.4,
                knock=1.2, plant=(), hit=[("head", HEAD_HIT, 8.0), ("shell", (0, 0, -18), 12.0)],
                stages={"windup": [(0.2, _mix(REST, coil, 0.7)), (0.6, coil), (1.0, coil | {"jaw": (10.0, 0.0, 0.0)})], "active": [(0.4, shoot), (1.0, shut)],
                        "recover": [(0.4, stop), (0.75, _mix(stop, REST, 0.75))]})


def undertow(r):
    """The Undertow: it lowers its head and opens its mouth as wide as it goes, and the worm on its tongue wriggles and
    wriggles, its forefeet sculling to hold it where it is while the water goes into its mouth, its head drawing back a
    little as if it drank; and it snaps it shut."""
    open_ = lambda q=0.0: _pose(pitch=2 + 0.6 * q, roll=0.8 * q, neck=(-1.5 + 0.5 * q, 10, 0), head=(4, 1.5 * q, 0), jaw=56,
                                legs={0: (-10 + 14 * q, 10 + 6 * q, 20, 10 * q), 1: (-10 - 14 * q, 10 - 6 * q, 20, -10 * q), 2: (-6, 6, 10), 3: (-6, 6, 10)},
                                tail=(-4, 6 * q, 0), weed=8)
    act = [(0.1, open_(1)), (0.25, open_(-1)), (0.4, open_(1)), (0.55, open_(-1)), (0.7, open_(1)), (0.85, open_(-1)), (0.94, open_(0)),
           (1.0, open_(0) | {"jaw": (3.0, 0.0, 0.0), "head": (8.0, 0.0, 0.0)})]
    end = _mix(open_(0) | {"jaw": (3.0, 0.0, 0.0)}, REST, 0.3)
    mv = Move("undertow", "NONE", 14, 34, 16, through=end, title="Undertow", track=6.0, ability="Undertow", origin=MOUTH_AT, rng=(4, 14), kinds=("ranged",),
              damage=0, knock=0, plant=(), stages={"windup": [(0.3, _mix(REST, open_(), 0.5)), (1.0, open_())], "active": act, "recover": _back(end)})
    mv._lure = 20.0
    return mv


def rush(r):
    """The Rush of Water: it rears its front up with both forefeet raised high and forward, and sweeps them down and
    back together as hard as it can, and the water rushes out in front of it."""
    rear = _pose(lift=2, shift=(0, 2), pitch=22, neck=(2, -12, 0), head=(-6, 0, 0), jaw=20, legs=_legs(fl=(64, 44, 40, -16), fr=(64, 44, 40, -16), bl=(-14, 4, 6),
                                                                                                        br=(-14, 4, 6)), tail=(-14, 0, -4), weed=10)
    sweep = _pose(lift=-1, shift=(0, -4), pitch=-9, neck=(6, 6, 0), head=(4, 0, 0), jaw=30, legs=_legs(fl=(-54, -10, 0, 20), fr=(-54, -10, 0, 20), bl=(10, 8, 10),
                                                                                                         br=(10, 8, 10)), tail=(14, 0, 6), weed=40)
    held = _pose(lift=-1.1, shift=(0, -4.2), pitch=-9.4, neck=(6.2, 6.4, 0), head=(4, 0, 0), jaw=30,
                 legs=_legs(fl=(-55, -10, 0, 21), fr=(-55, -10, 0, 21), bl=(10, 8, 10), br=(10, 8, 10)), tail=(14, 0, 6), weed=40)
    return Move("rush", "NONE", 16, 10, 18, through=held, title="Rush of Water", track=4.0, ability="Rush of Water", origin=(0.0, 1.4, 2.6), rng=(0, 7),
                kinds=("area",), damage=0, knock=0, plant=(),
                stages={"windup": [(0.25, _mix(REST, rear, 0.6)), (0.6, _mix(REST, rear, 0.92)), (1.0, rear)], "active": [(0.4, _mix(rear, sweep, 0.6)), (1.0, sweep)],
                        "recover": _back(held)})


def lungful(r):
    """Lungful (into the second phase): it draws its head and its legs into its shell and sinks, trembling, its shell
    shaking harder and harder; then it bursts out of it, its legs flung wide and its neck stretched up and its mouth
    gaping, and the air goes out of everything round it."""
    w = []
    n = 12
    for i in range(1, n + 1):
        u = i / n
        f = _ease(min(1.0, u / 0.45))
        q = (-1) ** i * min(1.0, 0.25 + u) * f
        w.append((u, _pose(lift=-3 * f + 0.6 * q, pitch=-1.5 * f + q, roll=2.2 * q, neck=(0, 6 * f, 0), head=(4 * f, 0, 0), jaw=2,
                           legs={0: (2 * q, 0, 0), 1: (-2 * q, 0, 0), 2: (2 * q, 0, 0), 3: (-2 * q, 0, 0)}, tuck=f, tail=(-6 * f, 3 * q, 0), weed=20 * f)))
    burst = lambda q=0.0: _pose(lift=4 + 0.6 * q, pitch=8 + 1.5 * q, roll=2 * q, neck=(6, -22 + 3 * q, 4 * q), head=(-10, 4 * q, 3 * q), jaw=54,
                                legs={0: (20 + 6 * q, 56, -10, -20), 1: (20 - 6 * q, 56, -10, -20), 2: (-16 - 5 * q, 46, -10, -20), 3: (-16 + 5 * q, 46, -10, -20)},
                                tail=(16, 10 * q, 8), weed=50)
    act = [(0.25, burst()), (0.44, _mix(burst(1), REST, 0.05)), (0.62, _mix(burst(-1), REST, 0.1)), (0.8, _mix(burst(0.7), REST, 0.15)), (1.0, _mix(burst(), REST, 0.2))]
    sag = _pose(lift=-1, pitch=-2, neck=(0, 6, 0), head=(3, 0, 0), jaw=8, weed=10)
    return Move("lungful", "NONE", 20, 18, 18, through=_mix(burst(), sag, 0.4), title="Lungful", track=0.0, ability="Lungful", origin=(0.0, 1.6, 0.0), rng=(0, 7),
                kinds=("area",), damage=0, knock=0, plant=(), stages={"windup": w, "active": act, "recover": [(0.45, sag), (0.75, _mix(sag, REST, 0.75))]})


def black_water(r):
    """Black Water: it lifts its head with its neck drawn in, and spits black water at you, four times, its neck
    jerking out and its mouth opening with each, its head turning a little between them."""
    high = _pose(pitch=4, neck=(-5, -18, 0), head=(-6, 0, 0), jaw=6, legs=_legs(fl=(-8, 8, 10), fr=(-8, 8, 10)), weed=8)
    spit = lambda q: _pose(pitch=1, neck=(5, -4, 6 * q), head=(6, 4 * q, 0), jaw=36, legs=_legs(fl=(-14, 6, 6), fr=(-14, 6, 6)), tail=(0, -4 * q, 0), weed=14)
    act = []
    for i in range(4):
        f0 = i / 4
        act += [(f0 + 0.1, spit((-1) ** i)), (f0 + 0.22, _mix(high, REST, 0.12))]
    act[-1] = (1.0, _mix(high, REST, 0.3))
    end = _mix(high, REST, 0.4)
    return Move("black_water", "NONE", 12, 26, 14, through=end, title="Black Water", track=8.0, ability="Black Water", origin=MOUTH_AT, rng=(4, 20),
                kinds=("ranged",), damage=0, knock=0, plant=(), stages={"windup": [(0.3, _mix(REST, high, 0.5)), (1.0, high)], "active": act, "recover": _back(end)})


def tail(r):
    """The Tail (for whoever is behind it): it turns its body one way and swings its tail out the other, then sweeps the
    saw-ridged tail across behind it, its body turning back."""
    cock = _pose(yaw=-28, roll=-7, neck=(-3, 4, 22), head=(0, 10, 0), legs=_legs(fl=(24, 18, 24), fr=(-18, 8, 12), bl=(-14, 14, 14), br=(22, 22, 24)),
                 tail=(10, 62, -6), weed=12)
    sweep = _pose(yaw=16, roll=4, neck=(1, 2, -10), head=(0, -4, 0), legs=_legs(fl=(-10, 4, 6), fr=(14, 10, 14), bl=(12, 14, 14), br=(-8, 8, 8)), tail=(8, -56, 6),
                  weed=20)
    held = _pose(yaw=16.5, roll=4.2, neck=(1, 2, -10.5), head=(0, -4, 0), legs=_legs(fl=(-10, 4, 6), fr=(14, 10, 14), bl=(12, 14, 14), br=(-8, 8, 8)),
                 tail=(8, -58, 6), weed=20)
    return Move("tail", "LIGHT", 12, 4, 14, through=held, title="Tail", track=0.0, rng=(0, 5.0), kinds=("behind",), damage=1.1, knock=1.4, plant=(),
                hit=[("tail2", TAIL_HIT, 7.0), ("tail1", (0, 0, 4), 6.0)],
                stages={"windup": [(0.2, _mix(REST, cock, 0.6)), (0.6, _mix(REST, cock, 0.9)), (1.0, cock)], "active": [(0.45, _mix(cock, sweep, 0.6)), (1.0, sweep)],
                        "recover": _back(held)})


def which_way(r):
    """Which Way Is Up (the signature): it rises up over you, rolling slowly over onto its back as it goes, until it
    hangs belly up above you with its feet paddling at nothing and its head hanging; it trembles there; and it drops,
    and crashes down on you shell first; and it rolls back over and lifts off the floor."""
    w = []
    n = 14
    for i in range(1, n + 1):
        u = i / n
        f = _ease(min(1.0, u / 0.6))
        q = (-1) ** i
        sh = _ease(max(0.0, (u - 0.6) / 0.3))
        legs = {k: ((24 if FRONT[k] else 18) * math.sin(2 * math.pi * 2.0 * u + (0 if k in (0, 3) else math.pi)), 30 * f, 30 * f, 10) for k in range(4)}
        w.append((u, _pose(lift=18 * f + 0.8 * q * sh, roll=180 * f + 1.5 * q * sh, neck=(4 * f, -14 * f, 0), head=(-8 * f, 0, 3 * q * sh), jaw=30 * f, legs=legs,
                           tail=(10 * f, 6 * q * sh, 0), weed=170 * f)))
    crash = _floor(r, _pose(roll=180, neck=(3, -20, 0), head=(-10, 0, 0), jaw=40, legs={0: (20, 50, 10, -10), 1: (20, 50, 10, -10), 2: (-16, 44, 10, -10),
                                                                                        3: (-16, 44, 10, -10)}, tail=(20, 0, 0), weed=170), gap=0.5)
    bounce = _floor(r, _pose(roll=176, neck=(3, -16, 4), head=(-8, 4, 0), jaw=30, legs={0: (10, 40, 30), 1: (10, 40, 30), 2: (-10, 36, 30), 3: (-10, 36, 30)},
                             tail=(16, 4, 0), weed=168), gap=1.5)
    act = [(0.35, _mix(w[-1][1], crash, 0.6)), (0.7, crash), (1.0, bounce)]
    # rolling back over it pulls its feet half in, so they pass over the floor
    right = lambda roll, up: _pose(lift=up, roll=roll, neck=(2, -6, 0), head=(-2, 0, 0), jaw=10, legs={k: (0, 30, 20) for k in range(4)}, tuck=0.5 * min(1.0, roll / 90),
                                   tail=(6, 0, 0), weed=roll * 0.9)
    mv = Move("which_way", "PERILOUS", 30, 12, 30, through=bounce, title="Which Way Is Up", signature=True, track=0.0, rng=(0, 9), kinds=("area",), damage=1.8,
              knock=1.4, disc=6.0, plant=(), hit=[("shell", (0, -11, 0), 14.0), ("head", HEAD_HIT, 8.0)],
              stages={"windup": w, "active": act,
                      "recover": [(0.3, _mix(bounce, _lift_clear(r, right(120, 0)), 0.8)), (0.55, _lift_clear(r, right(60, 0))), (0.8, _mix(right(0, 4), REST, 0.5))]})
    return mv


def arrive(r):
    """Its intro: it lies on the floor like a rock grown over with weed, its head and legs drawn in; its head slides out
    and looks at you; its mouth opens and the worm on its tongue wriggles; it shuts it, puts out its feet, and lifts off
    the floor with a slow stroke."""
    low = _lying(r)
    peer = _on(low, _pose(neck=(-1, 4, 0), head=(2, 0, 0), jaw=2, legs={0: (10, 0, 0), 1: (8, 0, 0), 2: (-8, 0, 0), 3: (-10, 0, 0)}, tuck=0.6, tail=(-10, 14, 0),
                          weed=85) | {"neck0": {"rot": (1.6, 0.0, 0.0), "pos": (0.0, 0.0, 0.6)}, "neck1": {"rot": (2.4, 0.0, 0.0), "pos": (0.0, 0.0, 0.4)}})
    gape = lambda q: _on(low, _pose(neck=(1, 6, 0), head=(3, 0, 0), jaw=40, lure=(22, q), legs={0: (10, 0, 0), 1: (8, 0, 0), 2: (-8, 0, 0), 3: (-10, 0, 0)},
                                    tuck=0.6, tail=(-10, 14, 0), weed=85))
    feet = _floor(r, _pose(neck=(1, 2, 0), jaw=4, legs={0: (-10, -8, 4, 10), 1: (-10, -8, 4, 10), 2: (-12, -6, 4, 10), 3: (-12, -6, 4, 10)}, tail=(-6, 8, 0), weed=70))
    lift_ = _pose(lift=-4, pitch=3, neck=(1, -2, 0), legs={0: (-34, 10, 30), 1: (-34, 10, 30), 2: (-26, 8, 24), 3: (-26, 8, 24)}, tail=(-2, 0, 0), weed=24)
    look = _pose(lift=1, neck=(3, -4, 0), head=(-2, 0, 0), jaw=8, legs={0: (14, 12, 20), 1: (14, 12, 20), 2: (10, 8, 14), 3: (10, 8, 14)}, weed=10)
    mv = Move("arrive", "NONE", 16, 44, 16, through=_mix(look, REST, 0.4), track=0.0, rng=(0, 99), kinds=("intro",), damage=0, knock=0, plant=(),
              stages={"windup": [(0.5, _mix(low, peer, 0.5)), (1.0, peer)],
                      "active": [(0.12, gape(0.0)), (0.22, gape(3.0)), (0.32, gape(6.0)), (0.42, gape(9.0)), (0.5, _on(low, _mix(gape(12.0), peer, 0.8))),
                                 (0.62, feet), (0.8, lift_), (1.0, look)],
                      "recover": [(0.6, _mix(look, REST, 0.7))]})
    mv._first = low  # it starts where the kneel left it, on the floor drawn in
    return mv


def moves(r):
    return [snap(r), rake(r), lunge(r), undertow(r), rush(r), lungful(r), black_water(r), tail(r), which_way(r), arrive(r)]


def _stage(r, mv):
    """stage_clips, but a move with a first pose of its own (the intro, from the kneel) starts from it."""
    first = getattr(mv, "_first", None)
    if first is None:
        return stage_clips(r, mv)
    orig = core.keyed

    def from_first(rig, name, length, keys, **kw):
        keys = list(keys)
        keys[0] = (0.0, first)
        return orig(rig, name, length, keys, **kw)
    core.keyed = from_first
    try:
        stage_clips(r, mv)
    finally:
        core.keyed = orig


def _drift(r, clip, lure=6.0, ramp=0.25):
    """Under a move, once it is made: its feet scull, its tail sways, its head and jaw work a little and the worm on
    its tongue wriggles (lure: how hard), faded in and out at the ends so it hands back to the idle exactly as it was."""
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
    for k in range(4):
        add(f"leg{k}a", lambda t, k=k: (4.0 * math.sin(2 * math.pi * 0.5 * t + 1.6 * k), 0.0, 3.0 * SIDE[k] * math.sin(2 * math.pi * 0.5 * t + 1.6 * k + 1.2)))
        add(f"leg{k}b", lambda t, k=k: (4.0 * math.sin(2 * math.pi * 0.5 * t + 1.6 * k + 0.7), 0.0, 0.0))
        add(f"foot{k}", lambda t, k=k: (6.0 * math.sin(2 * math.pi * 0.5 * t + 1.6 * k + 1.4), 0.0, 0.0))
    add("tail1", lambda t: (2.0 * math.sin(2 * math.pi * 0.4 * t), 6.0 * math.sin(2 * math.pi * 0.5 * t + 0.5), 0.0))
    add("tail2", lambda t: (3.0 * math.sin(2 * math.pi * 0.4 * t - 0.6), 8.0 * math.sin(2 * math.pi * 0.5 * t - 0.2), 0.0))
    add("head", lambda t: (1.5 * math.sin(2 * math.pi * 0.45 * t), 1.5 * math.sin(2 * math.pi * 0.35 * t + 1.0), 0.0))
    add("jaw", lambda t: (1.0 + 1.0 * math.sin(2 * math.pi * 0.9 * t), 0.0, 0.0))
    add("lure0", lambda t: (0.0, lure * math.sin(2 * math.pi * 2.0 * t), 0.0))
    add("lure1", lambda t: (0.0, 1.4 * lure * math.sin(2 * math.pi * 2.0 * t - 1.2), 0.0))
    add("neck0", lambda t: (1.5 * math.sin(2 * math.pi * 0.4 * t + 0.3), 1.5 * math.sin(2 * math.pi * 0.3 * t), 0.0))
    for i, (x, z, s) in enumerate(WEED_AT):  # its weed and chain sway as if in a current
        add(f"weed{i}", lambda t, i=i: (6.0 * math.sin(2 * math.pi * 0.5 * t + 1.1 * i), 0.0, 5.0 * math.sin(2 * math.pi * 0.7 * t + 0.7 * i)))
    for i, link in enumerate(CHAIN):
        add(link, lambda t, i=i: (5.0 * math.sin(2 * math.pi * 0.6 * t - 0.6 * i), 0.0, 4.0 * math.sin(2 * math.pi * 0.45 * t - 0.5 * i)))
    add("shell", lambda t: (0.8 * math.sin(2 * math.pi * 0.3 * t), 0.0, 0.8 * math.sin(2 * math.pi * 0.22 * t + 0.4)))


# the ground (SPEC §35.10): its feet lift out of the floor, its head, tail, weed and chain lift off it
FOOT_GROUND = [([f"foot{k}"], [(f"leg{k}a", 2, 40, -SIDE[k]), (f"leg{k}b", 0, 30, 1)]) for k in range(4)]
HEAD_GROUND = [(["head", "jaw"], [("neck1", 0, 30, -1), ("neck0", 0, 30, -1)])]
TAIL_GROUND = [(["tail2"], [("tail1", 0, 30, 1), ("tail0", 0, 20, 1)])]
TRAIL_GROUND = [([f"weed{i}"], [(f"weed{i}", 0 if s == 0 else 2, 90, 1 if s == 0 else -s)]) for i, (x, z, s) in enumerate(WEED_AT)] \
    + [(["chain2"], [("chain0", 2, 80, -1), ("chain1", 0, 60, 1)])]


def clips(r):
    _idle(r)
    _locomotion(r)
    _reactions(r)
    mv = moves(r)
    for m in mv:
        _stage(r, m)
        _drift(r, core.clip_name(m), lure=getattr(m, "_lure", 6.0))
    core.ground_pass(r, FOOT_GROUND + HEAD_GROUND + TAIL_GROUND + TRAIL_GROUND, clips=list(r.clips), skip=())
    return mv


# Its sounds (SPEC §35.8): a turtle's hurt and death and its swimming kept low and slow, the drowned's gurgling under
# them; water for everything else, a splash, a bubble column's churn and a whirlpool's pull, an elder guardian's curse
# for Lungful; a beak's crunch and the chain's clank; chosen on paper, not heard in game.
SOUNDS = {
    "tell_light": [("entity.turtle.ambient_land", 0.9, 0.5), ("entity.drowned.ambient_water", 0.7, 0.6)],
    "tell_heavy": [("entity.drowned.ambient_water", 1.1, 0.5), ("block.bubble_column.upwards_inside", 0.8, 0.6)],
    "tell_perilous": [("entity.elder_guardian.ambient", 1.2, 0.5), ("block.bubble_column.whirlpool_inside", 1.0, 0.6), ("block.chain.step", 0.8, 0.5)],
    "swing_light": [("entity.turtle.swim", 1.0, 0.6), ("entity.player.attack.sweep", 0.5, 0.6)],
    "swing_heavy": [("entity.player.splash.high_speed", 1.0, 0.5), ("entity.turtle.swim", 1.2, 0.5)],
    "cast": [("entity.drowned.shoot", 1.0, 0.5), ("entity.generic.splash", 0.9, 0.6)],
    "impact": [("entity.evoker_fangs.attack", 0.9, 0.6), ("entity.ravager.attack", 0.9, 0.6), ("entity.generic.splash", 0.8, 0.5)],
    "roar": [("entity.turtle.death", 1.2, 0.4), ("entity.drowned.death_water", 1.0, 0.5), ("entity.elder_guardian.curse", 0.7, 0.6)],
    "pull": [("block.bubble_column.whirlpool_inside", 1.3, 0.5), ("ambient.underwater.enter", 1.0, 0.6)],
    "step": [("entity.turtle.swim", 0.4, 0.6)],
}
MOVE_SOUNDS = {"undertow": {"swing": "pull", "impact": ""}, "rush": {"swing": "swing_heavy", "impact": ""}, "lungful": {"swing": "roar", "impact": ""},
               "black_water": {"swing": "cast", "impact": ""}, "which_way": {"swing": "roar"}, "arrive": {"swing": "roar", "impact": ""}}

FIGHT = {
    "intro": {"pose": "kneel", "move": "arrive"},  # it lies on the floor like a rock until its head comes out
    "phases": [
        {"pool": [
            {"move": "snap", "weight": 3, "when": "near", "then": [["rake"], ["rake", "lunge"]]},
            {"move": "rake", "weight": 2, "when": "near"},
            {"move": "lunge", "weight": 3, "when": "far", "then": [["snap"]]},
            {"move": "undertow", "weight": 3, "when": "far"},
            {"move": "rush", "weight": 2},
            {"move": "tail", "weight": 3, "when": "behind"},
        ]},
        # no air: Lungful, Black Water, Which Way Is Up
        {"transition": "lungful", "pool": [
            {"move": "snap", "weight": 3, "when": "near", "then": [["rake"], ["rake", "undertow"]]},
            {"move": "lunge", "weight": 3, "when": "far", "then": [["snap", "rake"]]},
            {"move": "black_water", "weight": 3, "when": "far"},
            {"move": "undertow", "weight": 2},
            {"move": "lungful", "weight": 2},
            {"move": "rush", "weight": 1},
            {"move": "which_way", "weight": 2},
            {"move": "tail", "weight": 3, "when": "behind"},
        ]},
    ],
    "enrage": {"below": 0.15, "speed": 1.2},
}


def design():
    r = body()
    r.pack()
    mv = clips(r)
    return {"rig": r, "plan": None, "moves": mv, "fight": FIGHT, "paint": paint, "scale": SCALE, "half": 1.1, "sounds": SOUNDS,
            "move_sounds": MOVE_SOUNDS}


def probe():
    r = body()
    fr = core.frames(r, {})
    for b, p in (("float", (0, 0, 0)), ("head", HEAD_HIT), ("foot0", FOOT_HIT), ("foot2", FOOT_HIT), ("tail2", TAIL_HIT), ("weed0", (0, 14, 0)), ("chain2", (0, 7, 0)),
                 ("lure1", (0, 0, -3))):
        print(b, p, [round(v, 2) for v in core.body(core.point(fr, b, p))])
    print("faces", core.faces(r), "cubes", sum(len(b.cubes) for b in r.bones), "bones", len(r.bones))
    print(core.floating(r))


if __name__ == "__main__":
    probe()
