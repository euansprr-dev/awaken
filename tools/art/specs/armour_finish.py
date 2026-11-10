"""DRAFT, NOT APPROVED (docs/armour/polish/PLAN.md): the finishing pass tried on every armour set, so the plan's page
can show each set before and after in the real game. It changes no design's helm or silhouette. It adds, from each set's
own materials: a gauntlet (cuff, hand plate, knuckles, finger plate: what first person shows), a plate language of the
set's own where the later sets all used one rimmed panel, the light its named feature should keep at night, and a soft
spill of light round whatever glows. ARMOUR_FINISH=0 builds the sets as they were.

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
    "wildhide": ("hide_dark", "strap", None, ("cuff",)),
    "bellguard": ("bronze_plain", "hoop", None, ()),
    "rootwarden": ("bark_plate", "root_wrap", None, ALL),
    "amberwing": ("leather_st", "amber", None, ALL),
    "beastbound": ("iron_riv", "tusk", None, ALL),
    "cleftrunner": ("iron", "iron_hem", None, ()),
    "lanternlight": ("iron", "brass", "glass_small", ALL),
    "bladeward": ("steel", "steel_hem", None, ALL),
    "hornlord": ("iron", "gold", "eye", ("cuff_sides", "hand_seam")),
    "gatewrought": ("stone", "silver", "door", ALL),
    "vaultbreaker": ("steel", "diamond", None, ()),
    "frostwalker": ("ice", "ice_rim", "icicle_glow", ALL),
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
    "sovereign": ("lacquer_hem", "silver", "glow", ("cuff_sides", "hand_seam")),
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
    "galewing": (("metal", "flint", 4), ("metal_plain", "flint", 5), ("metal_rim", "flint", 4)),
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
    plate, trim, glow, parts = GAUNTLET[sid]
    mats = d["mats"]
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
    mats["g_hand"] = copy(mats[plate], details=tuple(lang) + (A.hem(t.ramp, t.base, metal=t.metal, gloss=t.gloss),) + gem, emit=0.0)
    mats["g_trim"] = copy(t, details=(), emit=0.0)
    mats["g_glove"] = copy(mats[glove[0]], details=tuple(mats[glove[0]].details) + (fingers(),), emit=0.0)
    add = [B(-3.5, 1.5, -11, CUT, -2.5, 2.5, "g_glove", "g_glove")]
    if "cuff" in parts:      # a flare a texel proud round the forearm's end, open toward the body
        add.append(B(-5, -4, CUT, CUT + 1, -3, 3, "g_trim", "g_cuff"))
    if "cuff" in parts or "cuff_sides" in parts:  # (a set whose line runs through keeps the outer face clear)
        x0 = -5 if "cuff" in parts else -4
        add += [B(x0, 2, CUT, CUT + 1, 3, 4, "g_trim", "g_cuff"), B(x0, 2, CUT, CUT + 1, -4, -3, "g_trim", "g_cuff")]
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
    """What glows lights the texels beside it on its own face: a texel away a third of its light and some of its
    colour, two away a little. A slit reads as a light, not a painted bar. alb, mat: the face's texels (0-255)."""
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
    for reach, share, tint in ((1, 0.34, 0.45), (2, 0.14, 0.2)):
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


# ---- the shader's draft, mirrored (assets/awaken/shaders/core/armour.fsh: form light, the moonlit edge, the sheen
# under the world's light), so the kit's previews show what the game would

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
    col = col + MR.SKY[None] * rim * up * (0.07 + 0.26 * gloss + 0.10 * metal)
    col = col + rgb * rim * 0.25 * (1 - metal) * (1 - gloss)
    col = col + accent[None] * rim * (0.12 + 0.55 * energy * rim) * 0.6
    dark = 1.0 - min(1.0, light)
    col = col * light
    top = np.maximum(n_world[:, 1:2], 0)
    col = col + MOON[None] * (0.30 * top + 0.55 * rim * up) * (0.04 + 0.26 * gloss) * sky * dark
    cycle = (time * 240.0) % 1.0
    band = np.exp(-(((along - (cycle * 1.8 - 0.4)) * 7.0) ** 2))[:, None]
    col = col + band * (0.12 + 0.55 * metal) * gloss * (0.6 + flash) * max(light, flash)
    pulse = np.where(flow > 0, 0.55 + 0.45 * np.sin(time * 240.0 * 2 * np.pi * 0.6 - flow * 12.0), 1.0)
    col = col + rgb * emit * pulse * (1.0 + 1.5 * energy) * 1.6
    col = col + accent[None] * flash * (0.18 + 0.6 * emit + 0.35 * rim)
    return np.clip(col, 0, 1)
