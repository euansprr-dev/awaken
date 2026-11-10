"""The Armour Finish (SPEC §44.1 "The finish"; plan docs/armour/polish/PLAN.md, approved in full 2026-11-10): one pass
laid over every set's own design, so it is written once and lands on all 29. It changes no helm and no silhouette. From
each set's own materials it gives: a gauntlet (the forearm's shell ends at the wrist in a flared cuff, a slimmer glove
with grooved fingers, a plate on the back of the hand: what your own eyes see), a plate language of the set's own where
the later sets all wore one rimmed panel, the light a set's named feature keeps at night, and a soft spill of light
round whatever glows. Its one rule, learned twice: nothing is added that is a lone cube.

ARMOUR_FINISH=0 builds the sets as they were before it (for a before picture). The shader's side of the pass is
mirrored at the bottom, so the kit's previews show what the game would.

armour_sets.py hands itself over as A (the painters and the box maker live there)."""
import os

import numpy as np

import arms_weapons as AW
import voxel as VX

ON = os.environ.get("ARMOUR_FINISH", "1") != "0"
A = None  # armour_sets, set by it on import
B = VX.box


# ---- painters the later sets never had ------------------------------------------------------------------------------

def flutes(every=2, step=-1):
    """Upright grooves down a plate's sides (fluted plate, porcelain ribs, a sun's rays)."""
    def d(t):
        if not t.side:
            return
        on = (t.H % every) == 0
        t.tone[on & ~t.edge.any(axis=1)] += step
    return d


def chitin(every=3):
    """Shell segments: a deep groove and a pale lip under it (a beetle's back, a rib cage)."""
    def d(t):
        if not t.side:
            return
        inner = ~t.edge.any(axis=1)
        t.tone[((t.Y % every) == 0) & inner] -= 2
        t.tone[((t.Y % every) == every - 1) & inner] += 1
    return d


def facets(step=1):
    """A cut face: its upper corner a step lighter, its lower a step darker, split on the diagonal (crystal, mirror)."""
    def d(t):
        if not t.side:
            return
        a, b, _, _ = A._in_face(t)
        u = t.L[:, a] / max(t.box.h[a], 1e-6)
        v = t.L[:, b] / max(t.box.h[b], 1e-6)
        s = u * (1 if t.n[2] >= 0 else -1) + v
        t.tone[s > 0.4] += step
        t.tone[s < -0.4] -= step
    return d


def feathered(rows=2):
    """Feather lames: rows that overlap downward, each row's vanes offset from the last."""
    def d(t):
        if not t.side:
            return
        inner = ~t.edge.any(axis=1)
        r = np.floor_divide(t.Y, rows)
        t.tone[((t.Y % rows) == 0) & inner] -= 1
        t.tone[((t.Y % rows) != 0) & (((t.H + r) % 3) == 0) & inner] -= 1
    return d


def scanlines(ramp_name, step, every=3, emit=0.25):
    """Thin lit lines across a plate, the way the System's own windows are drawn."""
    def d(t):
        if not t.side:
            return
        on = ((t.Y % every) == 0) & ~t.edge.any(axis=1)
        t.put(on, ramp_name=ramp_name, tone=step, metal=0.0, gloss=0.4, emit=emit)
    return d


def hand_gem(ramp_name, step, emit):
    """Two lit texels in the middle of a hand plate (the back of the hand, where your own eye falls in first person)."""
    def d(t):
        if not t.side or abs(t.n[0]) < 0.7:
            return
        on = (np.abs(t.L[:, 2]) < 1) & (t.L[:, 1] > 0)
        t.put(on, ramp_name=ramp_name, tone=step, metal=0.0, gloss=0.5, emit=emit)
    return d


def lit_hem(ramp_name, step, emit):
    """A plate's lower edge lit (a wing's leading edge in storm light)."""
    def d(t):
        on = t.bottom(1) & t.side
        t.put(on, ramp_name=ramp_name, tone=step, metal=0.0, gloss=0.4, emit=emit)
    return d


def copy(m, details=None, **kw):
    out = AW.Mat(m.ramp, m.base, m.metal, m.gloss, m.emit, m.details if details is None else details, m.contact, m.bevel)
    for k, v in kw.items():
        setattr(out, "ramp" if k == "ramp_name" else k, v)
    return out


# ---- each set's finish ----------------------------------------------------------------------------------------------
# gauntlet: plate (the hand and finger plates), trim (cuff and knuckles), glow (a material whose ramp lights the hand
# gem, or None), and which of its parts the set takes (a set that already wears something there keeps it)
ALL = ("cuff", "hand")
GAUNTLET = {
    "wildhide": ("hide_dark", "strap", None, ("cuff_sides",)),   # its bone toggles keep the outer face
    "bellguard": ("bronze_plain", "hoop", None, ()),
    "rootwarden": ("bark_plate", "root_wrap", None, ("cuff_sides", "hand")),  # its sap inlay keeps the outer face
    "amberwing": ("leather_st", "amber", None, ("cuff_sides", "hand")),  # its amber inlay keeps the outer face
    "beastbound": ("iron_riv", "iron", None, ("hand",), "tusk"),  # its hide strap is the wrist band; a bone lip on the plate
    "cleftrunner": ("iron", "iron_hem", None, ()),
    "lanternlight": ("iron", "brass", "glass_small", ("hand",)),  # its own brass cuff is at the wrist
    "bladeward": ("steel", "steel_hem", None, ("cuff_of", "hand")),  # its couter sits behind the wrist
    "hornlord": ("iron", "gold", "eye", ("cuff_sides", "hand_seam")),
    "gatewrought": ("stone", "silver", "door", ALL),
    "vaultbreaker": ("steel", "diamond", None, ()),
    "frostwalker": ("ice", "ice_rim", "icicle_glow", ("hand",)),  # its own fur cuff is already at the wrist
    "sunward": ("gold", "gold_rim", "sun_heart", ALL),
    "shardcrown": ("steel", "crystal", "crystal_glow", ALL),
    "veilwoven": ("plate", "silver", "gem", ()),
    "deepcarapace": ("shell", "ridge", "glow", ("cuff_sides", "hand_seam")),
    "cloudrunner": ("plate", "feather_b", "gem", ALL),
    "tidebreaker": ("plate", "spine", "eye", ALL),
    "hollowheart": ("bone", "rib", "heart_core", ALL),
    "lordscale": ("plate_hem", "gold", "eye", ("cuff_sides", "hand_seam")),
    "leviathan": ("abyss_plain", "bone", "lure", ALL),
    "galewing": ("metal_plain", "feather", "slit", ALL),
    "halo_choir": ("porcelain_plain", "gold", "light", ALL),
    "dreamsteel": ("mirror", "shard", "slit", ("cuff",)),
    "sovereign": ("lacquer_hem", "lacquer", "glow", ("cuff_sides", "hand_seam"), "silver"),  # a black cuff, a silver lip
    "starfall": ("star_plain", "silver", "fire_soft", ALL),
    "riftwalker": ("plate", "plate_plain", "rift_soft", ("cuff",)),
    "nightcourt": ("leather", "silver", None, ()),
    "signals_echo": ("plate", "robe_hem", "cyan", ("hand",)),
}
# plate language: the painter that replaces the one rimmed panel on a set's limb and belt plates
LANGUAGE = {
    "vaultbreaker": lambda: (A.rivet_grid("flint", 7, 2),),
    "frostwalker": lambda: (facets(),),
    "sunward": lambda: (flutes(2),),
    "shardcrown": lambda: (facets(),),
    "deepcarapace": lambda: (chitin(3),),
    "cloudrunner": lambda: (feathered(2),),
    "hollowheart": lambda: (chitin(2),),
    "leviathan": lambda: (A.scales(),),
    "galewing": lambda: (feathered(2),),
    "halo_choir": lambda: (flutes(2),),
    "dreamsteel": lambda: (facets(),),
    "starfall": lambda: (facets(),),
    "lanternlight": lambda: (A.rivets("flint", 6, 2),),
    "tidebreaker": lambda: (A.scales(),),
}
# the light a set's named feature keeps at night: material -> emissive, or part -> emissive (that part alone)
NIGHT = {
    "sunward": {"mat:sun": 0.6, "mat:sun_boss": 0.8, "mat:ray": 0.6},
    "shardcrown": {"mat:crystal": 0.5},
    "vaultbreaker": {"mat:diamond": 0.7},
    "tidebreaker": {"mat:spine": 0.6},
    "cloudrunner": {"mat:gem": 0.8},
    "leviathan": {"mat:fin": 0.45},
    "halo_choir": {"part:halo": 0.9},
    "dreamsteel": {"mat:shard": 0.5},
    "sovereign": {"mat:spire": 0.45},
    "signals_echo": {"part:marker": 0.9},
    "amberwing": {"mat:wing": 0.6},
}
# palette: (material, ramp, base) for the sets that sat in one blue-grey crowd
PALETTE = {
    "galewing": (("metal", "flint", 3), ("metal_plain", "flint", 4), ("metal_rim", "flint", 3)),
    "leviathan": (("fin", "spore", 3),),
}


def fingers():
    """A glove's fingers: grooves up its last two rows, round every side and across the fist's end."""
    def d(t):
        if t.side:
            on = (t.y < -9) & ((t.H % 2) == 0)
        else:
            on = (np.floor(t.z + 0.5 + 1e-6).astype(int) % 2) == 0
        t.tone[on & ~t.edge.any(axis=1)] -= 1
    return d


GLOVE = {"wildhide": "wrap"}  # a glove of the forearm's own wrap, where the cap under the hand was only dark hide
CUT = -7  # the wrist: a forearm's shell ends here and the glove begins


def _trim(boxes, glove):
    """A forearm's shell cut at the wrist (a flare wider than the shell stays); returns what is left and the material
    the old cap under the hand was (the glove takes it)."""
    out = []
    for b in boxes:
        if not np.allclose(b.R, np.eye(3)):
            out.append(b)
            continue
        lo, hi = b.c - b.h, b.c + b.h
        shell = hi[0] - lo[0] == 6 and hi[2] - lo[2] == 6
        if not shell or lo[1] >= CUT:
            out.append(b)
        elif hi[1] > CUT:
            out.append(VX.Box((b.c[0], (hi[1] + CUT) / 2, b.c[2]), (b.h[0], (hi[1] - CUT) / 2, b.h[2]), b.mat, b.part, None, b.name, **b.data))
        else:
            glove[0] = b.mat
    return out


def gauntlet(sid, d):
    """The hand of the cuirass's arm, which is what your own eyes see: the forearm's shell ends at the wrist in a
    flared cuff; under it a glove half a texel proud of the hand (slimmer than the forearm, so the wrist reads), its
    fingers grooved; a plate on the back of the hand with a lip of the set's trim and, from C, its light."""
    plate, trim, glow, parts = GAUNTLET[sid][:4]
    mats = d["mats"]
    lip = mats[GAUNTLET[sid][4]] if len(GAUNTLET[sid]) > 4 else mats[trim]
    arms = d["pieces"]["chestplate"]
    lang = LANGUAGE[sid]() if sid in LANGUAGE else (A.lames(1, -1),)
    gem = ()
    if glow is not None:
        g = mats[glow]
        gem = (hand_gem(g.ramp, g.base, max(0.8, g.emit)),)
    t = mats[trim]
    glove = [plate]
    right = _trim(arms["right_arm"], glove)
    glove[0] = GLOVE.get(sid, glove[0])
    left = _trim(arms["left_arm"], [plate])
    if "hand_seam" in parts and glow is not None:  # the forearm's molten line, run on down the back of the hand
        gem = (A.side_seam(mats[glow].ramp, max(0.8, mats[glow].emit)),)
    mats["g_hand"] = copy(mats[plate], details=tuple(lang) + (A.hem(lip.ramp, lip.base, metal=lip.metal, gloss=lip.gloss),) + gem, emit=0.0)
    mats["g_trim"] = copy(t, details=(), emit=0.0)
    mats["g_glove"] = copy(mats[glove[0]], details=tuple(mats[glove[0]].details) + (fingers(),), emit=0.0)
    add = [B(-3.5, 1.5, -11, CUT, -2.5, 2.5, "g_glove", "g_glove")]
    # a flare a texel proud round the forearm's end, open toward the body: its outer, front and back bars ("cuff" is all
    # three; "cuff_sides" the front and back alone, flush with an outer face that keeps a line or a detail of the set's;
    # "cuff_of" outer and front where the set wears something behind the wrist)
    if "cuff" in parts or "cuff_of" in parts:
        add.append(B(-5, -4, CUT, CUT + 1, -3, 3, "g_trim", "g_cuff"))
    x0 = -4 if "cuff_sides" in parts else -5
    if "cuff" in parts or "cuff_sides" in parts or "cuff_of" in parts:
        add.append(B(x0, 2, CUT, CUT + 1, 3, 4, "g_trim", "g_cuff"))
    if "cuff" in parts or "cuff_sides" in parts:
        add.append(B(x0, 2, CUT, CUT + 1, -4, -3, "g_trim", "g_cuff"))
    if "hand" in parts:      # the back of the hand
        add.append(B(-4.5, -3.5, -10, -8, -2, 2, "g_hand", "g_hand"))
    if "hand_seam" in parts:  # a longer plate from the wrist to the knuckles, the line down its middle
        add.append(B(-4.5, -3.5, -10, CUT, -2, 2, "g_hand", "g_hand"))
    arms["right_arm"] = right + add
    arms["left_arm"] = left + A.mirror(add)


def relanguage(sid, d):
    """The rimmed panel on limb and belt plates becomes the set's own language (the breast and pauldron keep their rim)."""
    lang = LANGUAGE[sid]()
    mats = d["mats"]
    for piece, bones in d["pieces"].items():
        for bone, boxes in bones.items():
            limb = bone.endswith("_arm") or bone.endswith("_leg")
            for b in boxes:
                if not b.mat.endswith("_rim") or b.part.startswith(("pauldron", "cuff", "g_")):
                    continue
                if not limb and b.part not in ("belt", "fauld"):
                    continue
                name = b.mat + "_lang"
                if name not in mats:
                    mats[name] = copy(mats[b.mat], details=lang)
                b.mat = name


def night(sid, d):
    mats = d["mats"]
    for key, emit in NIGHT[sid].items():
        kind, name = key.split(":")
        if kind == "mat":
            mats[name].emit = emit
            continue
        for bones in d["pieces"].values():
            for boxes in bones.values():
                for b in boxes:
                    if b.part == name or b.part.rstrip("0123456789") == name:
                        lit = b.mat + "_lit"
                        if lit not in mats:
                            mats[lit] = copy(mats[b.mat], emit=emit)
                        b.mat = lit


def finish(sid, d):
    if not ON:
        return d
    mats = d["mats"]
    for name, ramp, base in PALETTE.get(sid, ()):
        mats[name].ramp, mats[name].base = ramp, base
    if sid == "galewing":  # the folded wings' edges take the storm's light
        for name in ("wing", "wing_grey"):
            mats[name].details = (A.lames(2, -1), lit_hem("volt", 4, 0.55))
    if sid in LANGUAGE:
        relanguage(sid, d)
    if sid in NIGHT:
        night(sid, d)
    if sid in GAUNTLET:
        gauntlet(sid, d)
    return d


def finished(sid, fn):
    def design():
        return finish(sid, fn())
    design.__name__ = fn.__name__
    design.__doc__ = fn.__doc__
    return design


# ---- light that spills ----------------------------------------------------------------------------------------------

def spill(t, alb, mat):
    """What glows lights the texels beside it on its own face: a texel away a quarter of its light and some of its
    colour, two away a little (more than this and a narrow plate takes the glow's colour whole). A slit reads as a light, not a painted bar. alb, mat: the face's texels (0-255)."""
    if not ON:
        return
    f = t.f
    emit = mat[:, 2].astype(float) / 255.0
    src = emit >= 0.5
    if not src.any() or src.all():
        return
    H, W = f.sv + 4, f.su + 4
    E = np.zeros((H, W)); C = np.zeros((H, W, 3)); F = np.zeros((H, W))
    jj, ii = f.j + 2, f.i + 2
    E[jj[src], ii[src]] = emit[src]
    C[jj[src], ii[src]] = alb[src, :3].astype(float)
    F[jj[src], ii[src]] = mat[src, 3].astype(float)
    for reach, share, tint in ((1, 0.28, 0.3), (2, 0.09, 0.1)):
        best = np.zeros((H, W)); col = np.zeros((H, W, 3)); n = np.zeros((H, W)); flow = np.zeros((H, W))
        for dy in range(-reach, reach + 1):
            for dx in range(-reach, reach + 1):
                if max(abs(dy), abs(dx)) != reach:
                    continue
                e = np.roll(np.roll(E, dy, 0), dx, 1)
                best = np.maximum(best, e)
                col += np.roll(np.roll(C, dy, 0), dx, 1) * (e > 0)[..., None]
                flow = np.maximum(flow, np.roll(np.roll(F, dy, 0), dx, 1))
                n += e > 0
        got = (best[jj, ii] > 0) & ~src & (emit < share * best[jj, ii])
        if not got.any():
            continue
        c = col[jj, ii][got] / np.maximum(n[jj, ii][got], 1)[:, None]
        alb[got, :3] = np.clip(alb[got, :3] * (1 - tint) + c * tint, 0, 255).round().astype(np.uint8)
        mat[got, 2] = np.clip(share * best[jj, ii][got] * 255, 0, 255).round().astype(np.uint8)
        mat[got, 3] = np.maximum(mat[got, 3], flow[jj, ii][got].astype(np.uint8))


# ---- the armour shader, mirrored (assets/awaken/shaders/core/armour.fsh: form light, the moonlit edge, the sheen under
# the world's light), so the kit's previews show what the game would

MOON = np.array([0.50, 0.62, 0.92])


def shade(alb, mat, n_world, n_view, along, accent, light=1.0, time=0.0, energy=0.0, flash=0.0, sky=1.0, **_):
    MR = A.MR
    metal, gloss, emit, flow = mat[:, 0:1], mat[:, 1:2], mat[:, 2:3], mat[:, 3:4]
    d = np.maximum(n_world @ MR.L0, 0) + np.maximum(n_world @ MR.L1, 0)
    diffuse = np.minimum(1.0, d * 0.6 + 0.4)[:, None]
    diffuse = np.floor(diffuse * 5.0 + 0.5) / 5.0 * 0.6 + diffuse * 0.4
    V = np.array([0.0, 0.0, 1.0])
    R = 2 * (n_view @ V)[:, None] * n_view - V[None]
    e = MR.env(R)
    rgb = alb[:, :3]
    col = rgb * diffuse * (1 - metal) + rgb * (0.28 + 0.9 * e) * (0.75 + 0.25 * diffuse) * metal
    spec = np.maximum(R @ MR.KEY, 0)[:, None] ** (6.0 + 84.0 * gloss) * gloss
    col = col + spec * (np.ones(3) * (1 - metal) + np.minimum(rgb * 1.7, 1.0) * metal) * 0.85
    rim = (1 - np.clip(n_view @ V, 0, 1))[:, None] ** 3
    up = MR.smoothstep(-0.5, 0.6, n_world[:, 1:2])
    col = col + (rgb * 1.3 + MR.SKY[None] * 0.04) * np.minimum(rim, 0.6) * up * (0.06 + 0.18 * gloss) * (1.0 - 0.6 * emit)
    col = col + rgb * rim * 0.25 * (1 - metal) * (1 - gloss)
    col = col + accent[None] * rim * (0.12 + 0.55 * energy * rim) * 0.6
    dark = 1.0 - min(1.0, light)
    col = col * light
    top = np.maximum(n_world[:, 1:2], 0)
    col = col + MOON[None] * (0.30 * top + 0.45 * rim * up) * (0.03 + 0.20 * gloss) * (0.35 + 0.65 * rgb.max(axis=1, keepdims=True)) * sky * dark
    cycle = (time * 240.0) % 1.0
    band = np.exp(-(((along - (cycle * 1.8 - 0.4)) * 7.0) ** 2))[:, None]
    col = col + band * (0.12 + 0.55 * metal) * gloss * (0.6 + flash) * max(light, flash)
    pulse = np.where(flow > 0, 0.55 + 0.45 * np.sin(time * 240.0 * 2 * np.pi * 0.6 - flow * 12.0), 1.0)
    col = col + rgb * emit * pulse * (1.0 + 1.5 * energy) * 1.6
    col = col + accent[None] * flash * (0.18 + 0.6 * emit + 0.35 * rim)
    return np.clip(col, 0, 1)


# ---- checks ---------------------------------------------------------------------------------------------------------

def overlaps(design):
    """Faces the pass added that lie in one plane with, and over, a face of a part the set already had (two faces in one
    place flicker in the game; the kit's own plane check only sees different texel grids). Returns messages."""
    bad = []
    for piece, bones in design["pieces"].items():
        for bone, boxes in bones.items():
            ours = [b for b in boxes if b.part.startswith("g_") and np.allclose(b.R, np.eye(3))]
            theirs = [b for b in boxes if not b.part.startswith("g_") and np.allclose(b.R, np.eye(3))]
            for a in ours:
                alo, ahi = a.c - a.h, a.c + a.h
                for b in theirs:
                    blo, bhi = b.c - b.h, b.c + b.h
                    for ax in range(3):
                        o = [i for i in range(3) if i != ax]
                        over = all(min(ahi[i], bhi[i]) - max(alo[i], blo[i]) > 1e-6 for i in o)
                        if not over:
                            continue
                        for pa, pb in ((alo[ax], blo[ax]), (ahi[ax], bhi[ax])):
                            if abs(pa - pb) < 1e-6:
                                bad.append(f"{piece}/{bone}: {a.part} and {b.part} share a face (axis {'xyz'[ax]} at {pa:g})")
    return sorted(set(bad))


# ---- living parts (SPEC §44.10) -------------------------------------------------------------------------------------
# One or two things per set that should not stand still: a halo, a marker, a plume, wings. A living part is some of a
# bone's own boxes, written as a mesh of their own beside the bone's still one, with what moves it: a pivot and an axis
# in the bone's design space (x your left, y up, z forward; texels), and a kind:
#   turn   round the axis, speed degrees a tick
#   tilt   leans amount degrees round and round (a halo), speed radians a tick
#   sway   swings amount degrees about the axis: a share from idling (speed radians a tick), a share from the stride, a
#          share from the wind of falling, gliding and running (wings open, a plume streams)
#   drift  wanders amount texels on its own slow paths (shards, a tear)
#   pulse  swells by amount on a double beat, speed beats a tick (a heart)
# bob lifts it up and down besides (texels). A set with nothing here stands still, and some should (Bellguard, Beastbound).

def _live(piece, bone, parts, kind, pivot, axis=(0, 1, 0), speed=0.05, amount=0.0, bob=0.0, phase=0.0, idle=0.35, stride=0.65,
          wind=0.0, side=0, through=(), kick=0.0):
    """through: parts it may pass into as it moves (rays turning behind their disc, a plume over the ridge it rises
    from); the sweep check refuses every other. kick: how hard the part answers when its set's ability fires (SPEC
    §44.13): a sway swings that many times its amount further, a tilt or a drift grows by that share, a pulse swells by
    it, a turn whips half a turn times it and settles."""
    return dict(piece=piece, bone=bone, parts=tuple(parts), kind=kind, pivot=tuple(float(v) for v in pivot),
                axis=tuple(float(v) for v in axis), speed=speed, amount=amount, bob=bob, phase=phase, idle=idle, stride=stride,
                wind=wind, side=side, through=tuple(through), kick=kick)


def _both_sides(piece, bone, parts, kind, pivot, axis, **kw):
    """One bone's left and right (a head's two wings, a back's): the right-hand one (x < 0) mirrors the left."""
    px, py, pz = pivot
    ax, ay, az = axis
    return [_live(piece, bone, parts, kind, (px, py, pz), (ax, ay, az), side=1, **kw),
            _live(piece, bone, parts, kind, (-px, py, pz), (ax, -ay, -az), side=-1, **kw)]


def _both_arms(piece, parts, kind, pivot, axis, **kw):
    """The same part on each arm: given for the right arm (its outer side is -x), mirrored for the left."""
    px, py, pz = pivot
    ax, ay, az = axis
    return [_live(piece, "right_arm", parts, kind, (px, py, pz), (ax, ay, az), **kw),
            _live(piece, "left_arm", parts, kind, (-px, py, pz), (ax, -ay, -az), **kw)]


LIVE = {
    # the System's marker turns over the hood and rides up and down
    "signals_echo": [_live("helmet", "head", ("marker", "marker_core"), "turn", (0, 14.5, 0), (0, 1, 0), speed=3.0, bob=0.6, kick=1.0)],
    # the sun's rays turn slowly behind the head
    "sunward": [_live("helmet", "head", ("ray",), "turn", (0, 5, -8), (0, 0, 1), speed=0.6, through=("disc",), kick=0.5)],
    # the halo leans round and round and rides; the wings at the temples breathe
    "halo_choir": [_live("helmet", "head", ("halo",), "tilt", (0, 7, -7), (0, 0, 1), speed=0.045, amount=6.0, bob=0.4, kick=0.6)]
                  + _both_sides("helmet", "head", ("hwing",), "sway", (4.5, 6.0, -7.0), (0, 0, 1), speed=0.045, amount=5.0, idle=1.0, stride=0.0, kick=2.5),
    # the little lamp at the brow swings on its chain as you walk; the shoulder lanterns rock on their feet
    "lanternlight": [_live("helmet", "head", ("lamp", "lamp_glass"), "sway", (0, 7, 6), (1, 0, 0), speed=0.07, amount=9.0, idle=0.3, stride=0.7, kick=1.2)]
                    + _both_arms("chestplate", ("lantern", "lantern_glass"), "sway", (-3, 4, 0), (0, 0, 1), speed=0.06, amount=3.5, idle=0.25, stride=0.75, kick=0.5),
    # a plume that nods with your step and streams when you run or fall
    "bladeward": [_live("helmet", "head", ("plume",), "sway", (0, 10, -2), (1, 0, 0), speed=0.06, amount=7.0, idle=0.3, stride=0.5, wind=1.6,
                        through=("ridge",), kick=1.2)],
    "nightcourt": [_live("helmet", "head", ("plume",), "sway", (4.5, 10.5, 1.5), (1, 0, 0), speed=0.05, amount=6.0, idle=0.35, stride=0.5, wind=1.6, kick=1.2)],
    # the wings that hang down the back (the cape's primaries) breathe, and fan open when you fall, glide or run
    "galewing": _both_sides("chestplate", "cape1", ("prim",), "sway", (0.0, 0.0, -0.5), (0, 0, -1), speed=0.04, amount=1.5, idle=1.0,
                            stride=0.0, wind=20.0, kick=20.0),
    # the broken crown drifts over the head, half of it against the other half
    "dreamsteel": [_live("helmet", "head", ("crown0", "crown2", "crown4"), "drift", (0, 12, 0), speed=0.035, amount=0.5, kick=0.5),
                   _live("helmet", "head", ("crown1", "crown3", "crown5"), "drift", (0, 12, 0), speed=0.035, amount=0.5, phase=2.1, kick=0.5)],
    # the tear behind you never quite holds still
    "riftwalker": [_live("chestplate", "body", ("tear", "glow"), "drift", (0, 0, -8), speed=0.09, amount=0.3, kick=4.0)],
    # the caged heart beats
    "hollowheart": [_live("chestplate", "body", ("heart", "heart_tip", "heart_core"), "pulse", (0, -4, 4.5), speed=1 / 17.0, amount=0.09, kick=2.0)],
    # the frills at the ears open and close like gills
    "leviathan": _both_sides("helmet", "head", ("frill",), "sway", (5.0, 6.0, -2.0), (0, 1, 0), speed=0.035, amount=5.0, idle=1.0, stride=0.0, kick=2.0),
    # the ribbons at the wrists swing
    "veilwoven": _both_arms("chestplate", ("ribbon",), "sway", (-4.5, -9.0, -0.5), (1, 0, 0), speed=0.06, amount=10.0, idle=0.3, stride=0.7, kick=1.0),
}


def is_part(box, names):
    """A box belongs to a living part by its part's name, numbered or not (ray, ray3, hwing0t)."""
    base = box.part.rstrip("0123456789-")
    return box.part in names or base in names or base.rstrip("t").rstrip("0123456789-") in names


def _mine(box, spec):
    if not is_part(box, spec["parts"]):
        return False
    return spec["side"] == 0 or (box.c[0] > 0) == (spec["side"] > 0)


def live_specs(design):
    """(piece, bone) -> its living parts, in order."""
    out = {}
    if not ON:
        return out
    for spec in LIVE.get(design["id"], ()):
        out.setdefault((spec["piece"], spec["bone"]), []).append(spec)
    return out


# armour_sets.make() asks is_part(box, spec["parts"]); a sided part needs the whole spec
def split(boxes, spec):
    return [b for b in boxes if _mine(b, spec)]


def live_json(design):
    """What the game reads beside the meshes (assets/awaken/armour/<set>/live.json): each living part's mesh and what
    moves it, in the bone's part space (blocks; y down, z back: design space turned half round x)."""
    out = []
    for (piece, bone), specs in live_specs(design).items():
        for k, s in enumerate(specs):
            px, py, pz = s["pivot"]
            a = np.asarray(s["axis"], dtype=float)
            a = a / max(np.linalg.norm(a), 1e-9)
            u, v = _lean(a)
            flip = np.array([1.0, -1.0, -1.0])  # design space to part space
            drift = s["kind"] == "drift"
            out.append({"piece": piece, "bone": bone, "mesh": f"{piece}_{bone}_live{k}", "kind": s["kind"],
                        "pivot": [round(px / 16.0, 5), round(-py / 16.0, 5), round(-pz / 16.0, 5)],
                        "axis": [round(float(x), 5) for x in a * flip], "u": [round(float(x), 5) for x in u * flip],
                        "v": [round(float(x), 5) for x in v * flip], "speed": s["speed"], "amount": 0.0 if drift else s["amount"],
                        "reach": round(s["amount"] / 16.0, 5) if drift else 0.0, "bob": round(s["bob"] / 16.0, 5),
                        "phase": s["phase"], "idle": s["idle"], "stride": s["stride"], "wind": s["wind"], "kick": s.get("kick", 0.0)})
    return out


# ---- the maker's mark (SPEC §44.11) ---------------------------------------------------------------------------------
# A Flawless piece carries its maker's mark: a small hollow diamond struck into the plate, on the side its wearer sees
# most. The kit only leaves room for it: four texels that glow nothing but carry a flow of 1, which the armour shader
# cuts dark and lights in the set's own colour when the piece is Flawless, and draws as plain plate otherwise.
# piece: (bone, the face's normal, where on it (x, y, z in the bone's design space; the axis along the normal ignored))
MARK_AT = {"helmet": ("head", (0, 0, -1), (0.0, 2.5, 0.0)),            # the back of the helm, low
           "chestplate": ("right_arm", (-1, 0, 0), (0.0, -4.5, 0.0)),  # the outside of the right forearm: your own eyes see it
           "leggings": ("right_leg", (-1, 0, 0), (0.0, -3.0, 0.0)),    # the outside of the right thigh
           "boots": ("right_leg", (-1, 0, 0), (0.0, -10.0, 0.0))}      # the outside of the right boot
MARK_REACH = 3.6  # texels: no flat place this near, and the piece carries no mark


def maker_marks(faces, painted):
    """Strikes each piece's mark into its painted faces (in place). Returns the pieces marked."""
    for _, m in painted:
        m[m[:, 2] == 0, 3] = 0  # flow means nothing where nothing glows: keep it free for the mark
    if not ON:
        return []
    done = []
    for piece, (bone, normal, at) in MARK_AT.items():
        nrm, at = np.asarray(normal, dtype=float), np.asarray(at, dtype=float)
        keep = 1.0 - np.abs(nrm)  # the two axes that lie in the face
        best = None
        for f, (a, m) in zip(faces, painted):
            p, b = f.group
            if p != piece or b not in (bone, bone + "@still") or float(f.n @ nrm) < 0.95:
                continue
            where = {(int(i), int(j)): k for k, (i, j) in enumerate(zip(f.i, f.j))}
            for (i, j), k in where.items():
                cross = [where.get(c) for c in ((i, j), (i + 1, j), (i - 1, j), (i, j + 1), (i, j - 1))]
                if any(c is None for c in cross) or m[cross, 2].any():
                    continue
                d = float(np.linalg.norm((f.P[k] - at) * keep))
                if d > MARK_REACH:
                    continue
                rough = float(a[cross, :3].astype(float).std(axis=0).sum())  # plain plate first, then the nearest
                score = d + rough * 0.15
                if best is None or score < best[0]:
                    best = (score, m, cross[1:])
        if best is not None:
            best[1][best[2], 3] = 255
            done.append(piece)
    return done


def _lean(a):
    """The two directions a tilt leans in: square to its axis and to each other."""
    u = np.cross(a, (0, 1, 0) if abs(a[1]) < 0.9 else (1, 0, 0))
    u = u / max(np.linalg.norm(u), 1e-9)
    v = np.cross(a, u)
    return u, v / max(np.linalg.norm(v), 1e-9)


def _rot(axis, deg):
    a = np.asarray(axis, dtype=float)
    a = a / max(np.linalg.norm(a), 1e-9)
    t = np.radians(deg)
    K = np.array([[0, -a[2], a[1]], [a[2], 0, -a[0]], [-a[1], a[0], 0]])
    return np.eye(3) + np.sin(t) * K + (1 - np.cos(t)) * (K @ K)


def beat(x):
    f = x % 1.0
    return np.exp(-((f - 0.1) * 9.0) ** 2) + 0.6 * np.exp(-((f - 0.32) * 9.0) ** 2)


def pose(spec, age, stride=0.0, walk=0.0, wind=0.0, kick=0.0):
    """(R, t): a living part's boxes at this moment are R (p - pivot) + pivot + t, in design space (the game does the same
    in part space, ArmourLive.pose). kick 0..1: how freshly the set's ability fired."""
    k, sp, am, ph = spec["kind"], spec["speed"], spec["amount"], spec["phase"]
    kw = spec.get("kick", 0.0) * kick
    R, t = np.eye(3), np.zeros(3)
    if k == "turn":
        R = _rot(spec["axis"], sp * age + 180.0 * kw * kick)
    elif k == "tilt":
        a = np.asarray(spec["axis"], dtype=float)
        u, v = _lean(a / max(np.linalg.norm(a), 1e-9))
        am = am * (1 + kw)
        R = _rot(u, am * np.sin(sp * age + ph)) @ _rot(v, am * np.cos(sp * age + ph))
    elif k == "sway":
        R = _rot(spec["axis"], am * (spec["idle"] * np.sin(sp * age + ph) + spec["stride"] * stride * np.sin(walk * 0.6662 + ph)
                                     + max(spec["wind"] * wind, kw)))
    elif k == "drift":
        t = am * (1 + kw) * np.array([np.sin(sp * age + ph), np.sin(0.7 * sp * age + 2 * ph), np.sin(1.3 * sp * age + 3 * ph)])
    elif k == "pulse":
        R = np.eye(3) * (1 + am * (beat(sp * age + ph) + kw))
    t = t + np.array([0.0, spec["bob"] * np.sin(0.05 * age + ph), 0.0])
    return R, t


def posed(design, age, stride=0.0, walk=0.0, wind=0.0):
    """The design with its living parts where they are at this moment (for a preview, a loop, the sweep check)."""
    for (piece, bone), specs in live_specs(design).items():
        boxes = design["pieces"][piece][bone]
        out = list(boxes)
        for spec in specs:
            R, t = pose(spec, age, stride, walk, wind)
            piv = np.asarray(spec["pivot"])
            for b in split(boxes, spec):
                if abs(np.linalg.det(R) - 1) > 1e-6:   # a pulse: a preview shows it as a nudge outward, not a scale
                    moved = VX.Box(piv + R @ (b.c - piv) + t, b.h, b.mat, b.part, b.R, b.name, **b.data)
                else:
                    moved = VX.Box(piv + R @ (b.c - piv) + t, b.h, b.mat, b.part, R @ b.R, b.name, **b.data)
                out[out.index(b)] = moved
        design["pieces"][piece][bone] = out
    return design


def sweep(design):
    """A living part must not swing into the body or the rest of its piece: at the ends of its motion no corner of it
    may be inside a still box of its bone (or the limb under it) that it was clear of at rest. Returns messages."""
    bad = []
    for (piece, bone), specs in live_specs(design).items():
        boxes = design["pieces"][piece][bone]
        skin = A.SKIN.get(bone, [])
        for spec in specs:
            mine = split(boxes, spec)
            others = [b for b in boxes if not any(_mine(b, s) for s in specs)] + list(skin)
            piv = np.asarray(spec["pivot"])

            def corners(b, R, t):
                pts = np.array([[sx, sy, sz] for sx in (-1, 1) for sy in (-1, 1) for sz in (-1, 1)], dtype=float) * (b.h - 0.3)
                P = b.c + pts @ b.R.T
                return piv + (P - piv) @ R.T + t
            rest = [corners(b, np.eye(3), np.zeros(3)) for b in mine]

            def joined(b, o):
                # a part that grows out of a box (a plume from its ridge, a ray from its disc) may turn in it
                pts = np.array([[sx, sy, sz] for sx in (-1, 1) for sy in (-1, 1) for sz in (-1, 1)], dtype=float) * (b.h + 0.51)
                return bool(o.inside(b.c + pts @ b.R.T).any()) or bool(b.inside(o.c).any())
            free = {(id(b), id(o)) for b in mine for o in others if not joined(b, o) and not is_part(o, spec["through"])}
            ages = range(0, 4000, 37)
            worst = set()
            for age in ages:
                # standing, striding, in the wind, and with the set's ability just fired (a wing never takes the wind
                # and the kick at once past what the wind alone opens it: the game adds the larger of the two)
                for stride, wind, kick in ((0.0, 0.0, 0.0), (1.0, 0.0, 0.0), (1.0, 1.0, 0.0), (0.0, 1.0, 0.0), (0.0, 0.0, 1.0), (1.0, 0.0, 1.0)):
                    R, t = pose(spec, age, stride, age * 0.9, wind, kick)
                    for b, r0 in zip(mine, rest):
                        P = corners(b, R, t)
                        for o in others:
                            if (id(b), id(o)) not in free:
                                continue
                            hit = o.inside(P) & ~o.inside(r0)
                            if hit.any():
                                worst.add((b.part, o.part))
                if spec["kind"] in ("drift", "pulse") and age > 600:
                    break
            for a, o in sorted(worst):
                bad.append(f"{piece}/{bone}: living part {a} swings into {o}")
    return bad
