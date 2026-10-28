"""Silhouette rounds (docs/bosses/REWORK.md R1, ROLLOUT.md): three shape ideas per boss as quick massing models, drawn
as black silhouettes (front and side) and in its own values at true size beside a player, with today's body for scale.
Euan picks one before the real model is built. The Ember Tyrant's options live here; every other boss's in kit/sil/.

    python3 tools/art/specs/kit_bodies.py silhouettes DIR [BOSS]

Massing only: no rig for animation, no texture detail. Units are model pixels (16 a block). Every helper takes
heights above the ground, up positive; the rig's own space is y down with the ground at 24.
"""
import math
import os

import fauna_rig as R
from fauna_rig import Ramp

BASALT = Ramp("#120d0c", "#1c1513", "#271d1a", "#342622", "#43312b")
IRON = Ramp("#1c1e23", "#2a2d34", "#3a3e47", "#4c515c", "#626875")
HEAT = Ramp("#c2300a", "#ff5a14", "#ff8a24", "#ffb648", "#ffe08a")
BONE = Ramp("#4a4038", "#6a5c4e", "#8a7a66", "#a89880")
MATS = {"b": (BASALT, 2.6), "i": (IRON, 2.6), "h": (HEAT, 2.6), "o": (BONE, 2.2)}


class Mass:
    """A massing model: bones placed by height above the ground, cubes tagged with a material."""

    def __init__(self, rid, name, idea, mats=MATS):
        self.r = R.Rig(rid, tex=(1024, 1024))
        self.name, self.idea, self.mats = name, idea, mats
        self.root = self.r.bone("root", (0, 24, 0))
        self.mat = {}
        self.n = 0

    def bone(self, parent, x, up, z, rot=(0, 0, 0)):
        self.n += 1
        return (parent or self.root).bone(f"b{self.n}", (x, -up, z), rot)

    def box(self, bone, mat, cx, y0, cz, w, h, d, mirror=False):
        """A cube centred on x and z, from y0 to y0 + h above the bone's pivot."""
        self.n += 1
        nm = f"c{self.n}"
        (bone or self.root).cube(nm, cx - w / 2, -(y0 + h), cz - d / 2, w, h, d, mirror=mirror)
        self.mat[nm] = mat
        return nm

    def pair(self, bone_fn, *a, **k):
        """The same part on both sides: bone_fn(side) -> bone (side 1 is the body's left, +x)."""
        for side in (1, -1):
            bone_fn(side)

    def paint(self, flat=False):
        self.r.pack()
        P = R.Painter(self.r, "sil|" + self.r.id)
        black = Ramp("#000000")
        for c in self.r.cubes:
            ramp, base = (black, 0) if flat else self.mats[self.mat[c.name]]
            P.paint(c.name, ramp, base, rim=0 if flat else 0.6, ao=0 if flat else 0.7, grad=0 if flat else 0.9, light=0 if flat else 1.0)
        return P.skin.image()


# ---- A: the Furnace King ---------------------------------------------------------------------------------------------
def furnace_king():
    m = Mass("sil_a", "A · The Furnace King",
             "Hunched like a gorilla, the head sunk low and forward between two mountains of shoulder. Forearms like "
             "anvils hang to its knees. Its chest is a furnace behind iron ribs. The great axe is held high in one "
             "fist, the blade rearing up in front of it.")
    # legs: short, wide, planted
    for s in (1, -1):
        leg = m.bone(None, s * 14, 46, 2, (8, 0, s * -4))
        m.box(leg, "b", 0, -20, 0, 18, 20, 20)  # thigh
        shin = m.bone(leg, 0, -20, 0, (-14, 0, 0))
        m.box(shin, "b", 0, -22, 0, 15, 22, 16)
        m.box(shin, "i", 0, -14, -2, 17, 10, 18)  # greave band
        m.box(None, "b", s * 15, 0, -4, 18, 7, 26)  # foot
        m.box(None, "o", s * 15 + s * 5, 0, -18, 4, 4, 4)  # claws
        m.box(None, "o", s * 15 - s * 3, 0, -18, 4, 4, 4)
    m.box(None, "b", 0, 42, 2, 36, 14, 26)  # pelvis
    m.box(None, "i", 0, 50, 2, 40, 6, 28)  # belt
    for s in (1, -1):  # hanging iron tassets
        t = m.bone(None, s * 12, 50, -14, (-8, 0, s * 6))
        m.box(t, "i", 0, -16, 0, 14, 16, 3)
    # the torso leans forward 28°
    tor = m.bone(None, 0, 54, 4, (28, 0, 0))
    m.box(tor, "b", 0, 0, 0, 34, 14, 26)  # waist
    m.box(tor, "b", 0, 12, 0, 58, 34, 40)  # chest barrel
    m.box(tor, "h", 0, 18, -19, 26, 20, 4)  # the furnace glow behind the ribs
    for k in range(4):  # iron ribs across the furnace
        m.box(tor, "i", 0, 17 + k * 6, -22, 44 - abs(k - 1.5) * 6, 3, 4)
    m.box(tor, "i", 0, 15, -23, 6, 28, 5)  # sternum bar
    # shoulder mountains, above the head
    for s in (1, -1):
        sh = m.bone(tor, s * 32, 34, 2, (0, 0, s * 6))
        m.box(sh, "b", 0, -6, 0, 28, 24, 30)
        pa = m.bone(sh, s * 2, 16, 0, (0, 0, s * 18))
        m.box(pa, "i", 0, 0, 0, 34, 6, 34)
        m.box(pa, "i", s * 6, 5, 0, 26, 5, 28)
        m.box(pa, "i", s * 9, 9, 0, 16, 4, 20)
        # the arm: hanging, forearms like anvils down to the knees
        arm = m.bone(sh, s * 4, -4, -2, (-14, 0, s * -6))
        m.box(arm, "b", 0, -24, 0, 18, 26, 18)
        fa = m.bone(arm, 0, -22, 0, (-12, 0, 0))
        m.box(fa, "b", 0, -30, 0, 26, 30, 26)
        m.box(fa, "i", 0, -18, 0, 29, 12, 29)  # bracer
        m.box(fa, "h", 0, -6, -13, 10, 4, 2)  # a vein above it
        m.box(fa, "b", 0, -44, -2, 24, 15, 24)  # fist
        m.box(fa, "i", 0, -40, -14, 22, 5, 4)  # knuckle plate
    # back: a ridge of obsidian spikes raking back
    for k in range(4):
        sp = m.bone(tor, 0, 18 + k * 8, 20, (-50 + k * 6, 0, 0))
        m.box(sp, "o", 0, 0, 0, 6, 12 + k * 2, 6)
    # the head: sunk low and forward, under the shoulder line
    hd = m.bone(tor, 0, 44, -26, (-30, 0, 0))
    m.box(hd, "b", 0, -6, -4, 22, 16, 20)
    m.box(hd, "b", 0, 7, -10, 28, 6, 10)  # heavy brow
    m.box(hd, "h", 0, 4, -15, 18, 2, 1)  # one burning slit under the brow
    m.box(hd, "b", 0, -12, -10, 20, 8, 16)  # underslung jaw
    for s in (1, -1):
        m.box(hd, "o", s * 7, -6, -18, 3, 7, 3)  # tusks
        h1 = m.bone(hd, s * 12, 8, -6, (0, 0, s * -75))
        m.box(h1, "o", 0, 0, 0, 10, 18, 10)
        h2 = m.bone(h1, 0, 17, 0, (20, 0, s * 55))
        m.box(h2, "o", 0, 0, 0, 8, 18, 8)
        h3 = m.bone(h2, 0, 17, 0, (25, 0, s * 25))
        m.box(h3, "o", 0, 0, 0, 5, 14, 5)
    # the great axe over its right shoulder, the blade rearing up behind the head
    ax = m.bone(None, -48, 22, -10, (-16, 0, 14))
    m.box(ax, "o", 0, 0, 0, 5, 92, 5)  # haft, 5.75 blocks
    for k, (w, h, z) in enumerate(((4, 40, 10), (4, 30, 17), (4, 18, 23))):
        m.box(ax, "i", 0, 78 - h / 2, z, w, h, 8)  # the blade in stepped tiers (no round shapes)
    m.box(ax, "h", 0, 70, 27.5, 2, 18, 1)  # its edge burns
    m.box(ax, "i", 0, 82, -6, 4, 12, 8)  # back spike
    return m


# ---- B: the Iron Warlord ---------------------------------------------------------------------------------------------
def iron_warlord():
    m = Mass("sil_b", "B · The Iron Warlord",
             "Upright and tall, plated like a siege tower. A visored helm with a crown of iron teeth, two horns raked "
             "back. Its pauldrons step up into spikes, an X from shoulder to boot. A long tattered plate cape. It "
             "leans on its great axe like a staff, planted head-down at its side.")
    for s in (1, -1):
        m.box(None, "i", s * 11, 0, -4, 16, 6, 24)  # sabaton
        m.box(None, "b", s * 11, 6, 0, 14, 28, 15)  # shin
        m.box(None, "i", s * 11, 10, -6, 16, 22, 6)  # greave
        kn = m.bone(None, s * 11, 34, -8, (-20, 0, 0))
        m.box(kn, "i", 0, -2, 0, 16, 8, 6)  # knee plate
        m.box(kn, "i", 0, 6, 1, 6, 6, 4)  # its spike
        m.box(None, "b", s * 11, 34, 0, 15, 24, 17)  # thigh
    m.box(None, "b", 0, 56, 0, 30, 12, 20)  # pelvis
    m.box(None, "i", 0, 60, 0, 34, 6, 23)  # belt
    for k, (w, y) in enumerate(((32, 52), (36, 42), (40, 32))):  # a skirt of plates, front and sides, stepped
        for s, rz in ((1, 10), (-1, -10)):
            t = m.bone(None, s * (6 + k * 2), y + 10, -11, (-6, 0, rz))
            m.box(t, "i", 0, -12, 0, w / 2 - 2, 12, 3)
    m.box(None, "b", 0, 66, 0, 26, 12, 18)  # waist
    ch = m.bone(None, 0, 78, 0)
    m.box(ch, "b", 0, 0, 0, 44, 26, 34)
    for s in (1, -1):  # a V of chest plates
        v = m.bone(ch, s * 10, 2, -17, (0, 0, s * 14))
        m.box(v, "i", 0, 0, 0, 22, 24, 4)
    m.box(ch, "h", 0, 6, -20, 4, 16, 1)  # the core as a slit of heat down the middle
    for s in (1, -1):
        sh = m.bone(ch, s * 26, 22, 0, (0, 0, s * -22))
        m.box(sh, "i", 0, -4, 0, 26, 10, 28)
        m.box(sh, "i", s * 4, 5, 0, 22, 7, 24)
        m.box(sh, "i", s * 8, 11, 0, 14, 6, 18)
        sp = m.bone(sh, s * 10, 16, 0, (0, 0, s * -14))
        m.box(sp, "i", 0, 0, 0, 5, 18, 5)  # the X's upper arms
        arm = m.bone(ch, s * 27, 18, 0, (-8, 0, s * -8))
        m.box(arm, "b", 0, -22, 0, 14, 24, 14)
        fa = m.bone(arm, 0, -22, 0, (-22, 0, 0))
        m.box(fa, "i", 0, -22, 0, 17, 22, 17)
        m.box(fa, "b", 0, -32, 0, 14, 11, 14)  # gauntlet
    # cape: three widening plates, raking back
    cp = m.bone(ch, 0, 24, 13, (-6, 0, 0))
    for k, (w, h) in enumerate(((40, 22), (46, 22), (52, 22))):
        m.box(cp, "b", 0, -22 * (k + 1), 2 + k, w, h, 4)
    for k in range(5):  # the hem torn into strips
        m.box(cp, "b", -20 + k * 10, -66 - (8 + (k * 7) % 9), 5, 7, 8 + (k * 7) % 9, 4)
    # helm: a tall visored box, a crown of iron teeth, horns raked back
    hd = m.bone(ch, 0, 26, -2)
    m.box(hd, "b", 0, 0, 0, 12, 6, 12)  # gorget
    m.box(hd, "i", 0, 4, -1, 20, 20, 20)
    m.box(hd, "h", 0, 14, -11, 14, 2, 1)  # visor slit
    m.box(hd, "i", 0, 4, -12, 14, 8, 2)  # faceplate below the slit
    for k in range(5):
        m.box(hd, "i", -8 + k * 4, 24, -6, 3, 4 + (2 - abs(k - 2)) * 3, 3)
    for s in (1, -1):
        h1 = m.bone(hd, s * 10, 16, 4, (-60, 0, s * -40))
        m.box(h1, "o", 0, 0, 0, 6, 12, 6)
        h2 = m.bone(h1, 0, 11, 0, (-30, 0, s * 20))
        m.box(h2, "o", 0, 0, 0, 4, 12, 4)
        h3 = m.bone(h2, 0, 11, 0, (-20, 0, 0))
        m.box(h3, "o", 0, 0, 0, 3, 9, 3)
    # the great axe planted head-down at its right side, the gauntlet on the pommel like a staff
    ax = m.bone(None, -36, 0, -8)
    m.box(ax, "o", 0, 4, 0, 4, 70, 4)
    m.box(ax, "i", 0, 72, 0, 7, 5, 7)  # pommel
    for s in (1, -1):  # a double head, stepped wide
        m.box(ax, "i", s * 9, 2, 0, 14, 22, 3)
        m.box(ax, "i", s * 18, 0, 0, 6, 28, 3)
        m.box(ax, "h", s * 21.5, 0, 0, 1, 28, 2)
    return m


# ---- C: the Living Forge ---------------------------------------------------------------------------------------------
def living_forge():
    m = Mass("sil_c", "C · The Living Forge",
             "A walking mountain, lopsided. A rock carapace rises off its back in slabs, with three chimneys venting "
             "fire. A long neck carries a horned skull mask out in front, low like a hunting beast. The right arm has "
             "burst into a molten fist the size of a cart. The left drags the great axe behind it, its blade scoring "
             "the ground.")
    for s, w in ((1, 18), (-1, 20)):
        leg = m.bone(None, s * 15, 44, 4, (14, 0, s * -6))
        m.box(leg, "b", 0, -22, 0, w, 22, w + 2)
        shin = m.bone(leg, 0, -22, 0, (-22, 0, 0))
        m.box(shin, "b", 0, -24, 0, w - 3, 24, w - 2)
        m.box(None, "b", s * 17, 0, -2, w + 2, 7, 26)
        if s < 0:
            m.box(shin, "h", 0, -16, -9, 4, 10, 1)  # the right leg cracked with heat
    m.box(None, "b", 0, 40, 4, 38, 14, 26)  # pelvis
    tor = m.bone(None, 0, 52, 6, (36, 0, -4))
    m.box(tor, "b", 0, 0, 0, 40, 18, 30)
    m.box(tor, "b", -4, 16, 0, 60, 28, 40)  # chest, heavier on the right
    m.box(tor, "h", 0, 22, -21, 10, 12, 2)  # the core through a crack
    # the carapace: slabs stepping up off the back like a ridge, with three chimneys
    for k, (w, h, z, y) in enumerate(((54, 16, 22, 30), (44, 18, 28, 38), (30, 16, 32, 48), (16, 12, 34, 58))):
        m.box(tor, "o" if k % 2 else "b", 2, y, z, w, h, 12)
    for k, (x, hgt) in enumerate(((-14, 20), (0, 26), (14, 18))):
        ch = m.bone(tor, x, 54, 30, (-30 + k * 6, 0, 0))
        m.box(ch, "i", 0, 0, 0, 8, hgt, 8)
        m.box(ch, "h", 0, hgt, 0, 6, 3, 6)
    # the neck and the skull mask, out in front and low
    nk = m.bone(tor, 0, 34, -20, (20, 0, 0))
    m.box(nk, "b", 0, 0, -6, 12, 12, 16)
    hd = m.bone(nk, 0, 4, -16, (-56, 0, 0))
    m.box(hd, "o", 0, -2, -10, 18, 14, 24)  # the mask, long like a beast's
    m.box(hd, "b", 0, -8, -12, 14, 6, 18)  # jaw
    for s in (1, -1):
        m.box(hd, "h", s * 5, 6, -22.5, 4, 2, 1)  # eyes, deep in the mask
        h1 = m.bone(hd, s * 8, 10, 0, (-70, 0, s * -18))
        for k, (w, ln) in enumerate(((6, 14), (5, 14), (4, 12), (3, 10))):
            m.box(h1, "o", 0, k * 13, -k * 2, w, ln, w)  # long horns swept back along the ridge, stepped
    # the right arm: a molten fist the size of a cart
    sh = m.bone(tor, -34, 34, 2, (0, 0, 20))
    m.box(sh, "b", 0, -8, 0, 26, 24, 28)
    m.box(sh, "i", 0, 12, 0, 30, 6, 30)
    ra = m.bone(sh, -2, -6, -2, (-30, 0, 10))
    m.box(ra, "b", 0, -26, 0, 20, 26, 20)
    rf = m.bone(ra, 0, -24, 0, (-20, 0, 0))
    m.box(rf, "b", 0, -30, 0, 30, 30, 30)
    for k, (x, y, h) in enumerate(((-8, -26, 20), (2, -22, 14), (9, -28, 18))):  # it has burst: cracks of heat
        m.box(rf, "h", x, y, -15.5, 2, h, 1)
    m.box(rf, "h", -15.5, -24, 0, 1, 16, 2)
    m.box(rf, "b", 0, -52, -2, 34, 22, 32)  # the fist
    for k in range(4):  # knuckles
        m.box(rf, "b", -12 + k * 8, -50, -20, 7, 9, 6)
    m.box(rf, "b", 16, -46, -10, 6, 10, 12)  # thumb
    m.box(rf, "h", 0, -42, -18.5, 26, 2, 1)
    # the left arm, slighter, dragging the axe behind
    sl = m.bone(tor, 30, 30, 2, (0, 0, -14))
    m.box(sl, "b", 0, -6, 0, 20, 18, 22)
    la = m.bone(sl, 2, -6, 0, (20, 0, -8))
    m.box(la, "b", 0, -24, 0, 15, 24, 15)
    lf = m.bone(la, 0, -22, 0, (14, 0, 0))
    m.box(lf, "b", 0, -24, 0, 17, 24, 17)
    m.box(lf, "b", 0, -34, 0, 15, 11, 15)
    ax = m.bone(None, 34, 18, 6, (-74, 0, 0))
    m.box(ax, "o", 0, 0, 0, 4, 72, 4)
    m.box(ax, "i", 0, 50, 6, 3, 30, 12)
    m.box(ax, "i", 0, 56, 14, 3, 18, 6)
    m.box(ax, "h", 0, 52, 17.5, 2, 14, 1)
    return m


def today(boss="ember_tyrant"):
    """Today's body at rest, for scale."""
    import importlib
    mod = importlib.import_module("kit.bosses." + boss)
    d = mod.design()
    return d["rig"]


def options(boss):
    """The round's three options: the Tyrant's here, any other boss's from kit/sil/<boss>.py."""
    if boss == "ember_tyrant":
        return [furnace_king(), iron_warlord(), living_forge()]
    import importlib
    return importlib.import_module("kit.sil." + boss).options()


def framed(tree, tex, yaw, pitch, px):
    """A render framed down to the ground under the body, so a flier hangs at its true height above the line."""
    x0, x1, y0, y1 = R.bounds_of([tree], yaw, pitch)
    ya, pa = math.radians(yaw), math.radians(pitch)
    g = R._mv(R._mm(R._rot(pa, 0, 0), R._rot(0, ya, 0)), (0, 24, 0))[1]
    return R.render(tree, tex, yaw=yaw, pitch=pitch, px=px, bounds=(x0, x1, y0, max(y1, g)))


def sheet(outdir, boss="ember_tyrant"):
    from PIL import Image, ImageDraw
    os.makedirs(outdir, exist_ok=True)
    px = 2.4 if boss == "ember_tyrant" else 3.6  # the Tyrant's round was drawn at 2.4; smaller bosses get more pixels
    player = R.player_tree()
    steve_black = Image.new("RGBA", R.steve().size, (0, 0, 0, 255))
    out_paths = []
    opts = options(boss)
    tr0 = today(boss)
    tr0.pack()
    black0 = Image.new("RGBA", tuple(tr0.tex), (0, 0, 0, 255))
    rows = []
    for m in opts:
        flat, val = m.paint(flat=True), m.paint(flat=False)
        tree = m.r.tree()
        views = [framed(tree, flat, 0, 0, px), framed(tree, flat, 90, 0, px), framed(tree, val, 32, 8, px)]
        pl = R.render(player, steve_black, yaw=0, pitch=0, px=px)
        rows.append((m, views, pl))
        # one image per option, for the page
        W = sum(v.width for v in views) + pl.width + 40 * 4
        H = max(v.height for v in views) + 30
        im = Image.new("RGBA", (W, H), (214, 218, 224, 255))
        x = 20
        ground = H - 14
        for v in [pl] + views:
            im.alpha_composite(v, (x, ground - v.height))
            x += v.width + 40
        ImageDraw.Draw(im).line((0, ground, W, ground), fill=(120, 124, 132, 255), width=2)
        p = os.path.join(outdir, f"{m.r.id}.png")
        im.save(p)
        out_paths.append(p)
        print(p, f"{len(m.r.cubes)} cubes, {blocks(tree):.1f} blocks tall")
    t0 = tr0.tree(tr0.sample([("idle", 0.4, 1.0)]))
    views = [framed(t0, black0, 0, 0, px), framed(t0, black0, 90, 0, px)]
    pl = R.render(player, steve_black, yaw=0, pitch=0, px=px)
    W = sum(v.width for v in views) + pl.width + 40 * 3
    H = max(v.height for v in views) + 30
    im = Image.new("RGBA", (W, H), (214, 218, 224, 255))
    x, ground = 20, H - 14
    for v in [pl] + views:
        im.alpha_composite(v, (x, ground - v.height))
        x += v.width + 40
    ImageDraw.Draw(im).line((0, ground, W, ground), fill=(120, 124, 132, 255), width=2)
    p = os.path.join(outdir, f"sil_today_{boss}.png")
    im.save(p)
    out_paths.append(p)
    print(p, f"{len(tr0.cubes)} cubes, {blocks(t0):.1f} blocks tall")
    return out_paths, opts


def blocks(tree):
    return (max(0, -min(q[1] for vs, _, _, _ in R.quads(tree) for q in vs)) + 24) / 16
