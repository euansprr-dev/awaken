"""Arms' weapons (SPEC §24; docs/combat/PLAN.md §4.5, §4.10): truly 3D models from one catalog and one part grammar.

    python3 tools/art/build_all.py arms_weapons       every file below
    python3 tools/art/specs/arms_weapons.py build     the same, after the checks
    python3 tools/art/specs/arms_weapons.py check     the checks only (on what the grammar builds)
    python3 tools/art/specs/arms_weapons.py preview DIR   a contact sheet per weapon (tools/art/model_preview.py)

From CATALOG (one line per weapon) and the tier's LOOK, for each weapon:
  - its 3D model (models/item/<id>_in_hand.json): the grammar's parts (pommel, grip, guard, blade) as boxes in sword
    space, laid along the item's diagonal with vanilla's sword grip in the hand (so vanilla's hand transforms and the
    first-person swings hold it as they hold an iron sword). The blade is a core with a thinner bevel each side, so it
    reads as ground steel from any angle, not a plank; glowing parts carry light_emission. Every face is mapped at one
    texel per model unit into a packed 64x64 atlas, painted per face from the tier's palette (gear.py's PAL: the
    Hunter set it is forged from); faces another box covers are left out.
  - its icon (models/item/<id>.json, textures/item/arms/<id>_icon.png and _glow): the same grammar seen from the
    front, drawn on the 16x16 diagonal in vanilla's pixel style (dark outline, light from the top left), for the
    inventory, the ground, frames and shelves (items/<id>.json selects on the display context, like the trident).
  - its lang name, recipe (the tier's Hunter blade, its core and its bind; the Forge's blueprints replace it in S7),
    its place in #minecraft:swords, and its row in the generated dev/awaken/arms/ArmsData.java (ids, stats and its
    trail's colour; ArmsItems registers from it).
Checks: every element inside the -16..32 box (rotated too), at most 400 quads, one texel per unit on every face, the
grip at the hand point, no two faces of one model coplanar (z-fighting), the atlas fits, no two weapons' silhouettes
more than 85% alike (front, on a 48 px grid), and no two icons alike in both outline and colour (siblings of one
archetype may share an outline, as vanilla's swords do, if their colours differ). Deterministic: every random choice
is seeded.

The forged Longswords (one per Hunter tier) are the first archetype through it. The look grows with the tier, each its
Hunter set's: a knapped flint leaf on a knuckled bone guard (E); a tusk cleaver with a bronze, toothed spine (D); a
slender slate blade between two gate towers, a signal line down it (C); a flame-waved Veil blade with a living spirit
edge (B); broad molten-edged Lordscale under spread wings (A); the Sovereign's ivory spire, crowned, its runes burning
crimson (S). Stats follow the Hunter blades: half a point more damage, the Longsword's own speed.
"""
import json
import math
import os
import random
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ART = os.path.abspath(os.path.join(HERE, ".."))
for p in (HERE, ART):
    if p not in sys.path:
        sys.path.insert(0, p)
from PIL import Image  # noqa: E402

import gear as G  # noqa: E402  (the Hunter sets' palettes PAL, the diagonal and the outline)

ROOT = os.path.abspath(os.path.join(HERE, "../../.."))
RES = os.path.join(ROOT, "src/main/resources")
MERGE = os.path.join(ROOT, "src/main/merge/arms")
JAVA = os.path.join(ROOT, "src/main/java/dev/awaken/arms/ArmsData.java")
ATLAS = 64
HAND = (3.0, 3.0, 8.0)  # the grip's centre in model space: where iron_sword.png's handle sits, so vanilla's hands fit
ANGLE = -45.0           # the blade along the item's diagonal, tip to the upper right

# ---------------------------------------------------------------------------------------------------------------------
# The catalog: one line per weapon (damage over the player's 1; uses; vanilla rarity; enchantability; the repair tag)

LONGSWORD_SPEED = -2.4  # the Longsword's baseSpeed 1.6 (its timing table rows are cut for it)
CATALOG = [
    # id                      name                     tier dmg  dur   rarity      ench repair
    ("wildhide_longsword", "Wildhide Longsword", "E", 4.5, 220, "COMMON", 15, "hunter_core_e"),
    ("beastbound_longsword", "Beastbound Longsword", "D", 5.5, 500, "UNCOMMON", 12, "hunter_bind_d"),
    ("gatewrought_longsword", "Gatewrought Longsword", "C", 6.5, 1400, "RARE", 12, "hunter_bind_c"),
    ("veilwoven_longsword", "Veilwoven Longsword", "B", 7.5, 1900, "EPIC", 15, "hunter_bind_b"),
    ("lordscale_longsword", "Lordscale Longsword", "A", 8.5, 2300, "EPIC", 15, "hunter_bind_a"),
    ("sovereign_longsword", "Sovereign Longsword", "S", 9.5, 2800, "EPIC", 18, "hunter_bind_s"),
]

# Per tier: materials (palette names) and the grammar's choices. edge_lo is the left (lit) bevel when it differs;
# trail is the light of its swings (a bright step of the tier's own glow, from PAL).
LOOKS = {
    "E": dict(blade="flint", edge="flint", guard="bone", grip="hide", band="cream", pommel="bone", glow="amber",
              pommel_style="knob", guard_style="knuckles", profile="leaf", length=15.5, bands=(-1.2, 1.2), inlay=None,
              trail="#f1ebd6"),
    "D": dict(blade="bone", edge="bone", guard="bronze", grip="pelt", band="bronze", pommel="bronze", glow="moss",
              pommel_style="tusk", guard_style="fangs", profile="cleaver", length=16.0, bands=(1.9,), inlay="groove",
              spine="bronze", trail="#b9d890"),
    "C": dict(blade="slate", edge="mirror", guard="brass", grip="slate", band="brass", pommel="brass", glow="signal",
              pommel_style="disc", guard_style="gate", profile="straight", length=17.5, bands=(-1.9, 1.9), inlay="line",
              trail="#7fc3ff"),
    "B": dict(blade="veil", edge="mirror", edge_lo="spirit", guard="mirror", grip="obsidian", band="mirror", pommel="mirror",
              glow="spirit", tip="mirror", pommel_style="drop", guard_style="swept", profile="wavy", length=17.5,
              bands=(-1.5, 0.0, 1.5), inlay=None, trail="#c3cbff"),
    "A": dict(blade="lord", edge="gold", guard="gold", grip="lord", band="gold", pommel="lord", glow="pearl", inlay_mat="gold",
              pommel_style="gem", guard_style="wings", profile="broad", length=18.0, bands=(-1.6, 0.0, 1.6), inlay="line",
              trail="#f3c443"),
    "S": dict(blade="ivory", edge="gold", guard="gold", grip="weave", band="gold", pommel="gold", glow="crimson",
              pommel_style="star", guard_style="crown", profile="spire", length=19.0, bands=(-1.8, -0.6, 0.6, 1.8), inlay="runes",
              trail="#ff8a7a"),
}

# ---------------------------------------------------------------------------------------------------------------------
# The part grammar: boxes in sword space (x across the blade, + to the lower right as held; y along it from the grip's
# centre; z its thickness, + toward the viewer)


class Box:
    def __init__(self, part, lo, hi, mat, glow=0, seg=None):
        self.part, self.mat, self.glow, self.seg = part, mat, glow, seg  # seg: the blade's (lo, hi) at this box
        self.lo = tuple(min(a, b) for a, b in zip(lo, hi))
        self.hi = tuple(max(a, b) for a, b in zip(lo, hi))
        self.faces = set(FACES)  # the faces that can be seen (cull() drops the covered ones)

    def size(self):
        return tuple(self.hi[i] - self.lo[i] for i in range(3))


def box(part, x0, x1, y0, y1, d, mat, z=0.0, glow=0, seg=None):
    return Box(part, (x0, y0, z - d / 2), (x1, y1, z + d / 2), mat, glow, seg)


def centred(part, w, y0, y1, d, mat, x=0.0, z=0.0, glow=0):
    return box(part, x - w / 2, x + w / 2, y0, y1, d, mat, z, glow)


GRIP = 5.0
G0, G1 = -GRIP / 2, GRIP / 2


def grip(L):
    out = [centred("grip", 1.5, G0, G1, 1.5, L["grip"])]
    for y in L["bands"]:  # wraps, ferrules and filigree rings
        out.append(centred("band", 1.8, y - 0.25, y + 0.25, 1.8, L["band"]))
    return out


def pommel(L):
    m, s, y, glow = L["pommel"], L["pommel_style"], G0, L["glow"]
    if s == "knob":  # E: a bone knuckle with an amber bead set in it
        return [centred("pommel", 2.0, y - 1.6, y, 2.0, m), centred("pommel", 1.3, y - 2.4, y - 1.6, 1.4, m),
                centred("bead", 0.8, y - 1.2, y - 0.4, 2.2, glow)]
    if s == "tusk":  # D: a bronze collar and a tusk curling off it
        return [centred("pommel", 2.2, y - 0.9, y, 2.2, m), centred("tusk", 1.3, y - 2.1, y - 0.9, 1.3, "bone", x=0.25),
                centred("tusk", 1.0, y - 3.0, y - 2.1, 1.0, "bone", x=0.6), centred("tusk", 0.7, y - 3.6, y - 3.0, 0.7, "bone", x=1.0)]
    if s == "disc":  # C: a wheel, stepped round, its signal stone through the middle
        return [box("pommel", -1.5, 1.5, y - 2.6, y - 0.6, 1.4, m), box("pommel", -1.0, 1.0, y - 3.1, y, 1.6, m),
                centred("gem", 0.9, y - 2.05, y - 1.15, 1.9, glow, glow=9)]
    if s == "drop":  # B: a mirror teardrop holding a spirit light
        return [centred("pommel", 1.6, y - 0.8, y, 1.6, m), centred("pommel", 2.4, y - 2.4, y - 0.8, 2.0, m),
                centred("pommel", 1.4, y - 3.2, y - 2.4, 1.4, m), centred("gem", 1.0, y - 2.0, y - 1.2, 2.3, glow, glow=12)]
    if s == "gem":  # A: a black scale cup and gold claws holding the sky pearl
        return [centred("pommel", 2.6, y - 1.4, y, 2.4, m), centred("gem", 1.8, y - 2.9, y - 1.1, 1.8, glow, glow=14)] + \
               [centred("claw", 0.5, y - 2.4, y - 1.2, 0.8, L["band"], x=side * 0.95) for side in (-1, 1)]
    if s == "star":  # S: a gold star round the Court's crimson
        return [centred("pommel", 3.0, y - 1.2, y, 1.6, m), centred("gem", 1.6, y - 2.8, y - 1.0, 2.0, glow, glow=15),
                centred("pommel", 0.8, y - 3.6, y - 2.6, 0.8, m)] + \
               [centred("pommel", 0.8, y - 2.2, y - 1.4, 0.8, m, x=side * 1.4) for side in (-1, 1)]
    raise ValueError(s)


def guard(L):
    """The guard's boxes and the height the blade starts at."""
    m, s, y, glow = L["guard"], L["guard_style"], G1, L["glow"]
    if s == "knuckles":  # E: a bone bar, its ends knuckled like a joint
        return [centred("guard", 5.6, y, y + 1.3, 1.8, m)] + \
               [centred("guard", 1.3, y - 0.3, y + 1.6, 2.2, m, x=side * 2.5) for side in (-1, 1)], y + 1.3
    if s == "fangs":  # D: a bronze bar, two tusks curling down to the hand, the beasts' green stone
        out = [centred("guard", 7.0, y, y + 1.4, 2.0, m), centred("gem", 1.1, y + 0.15, y + 1.25, 2.3, glow, glow=6)]
        for side in (-1, 1):
            out += [centred("fang", 1.0, y - 1.3, y, 1.4, "bone", x=side * 3.0),
                    centred("fang", 0.8, y - 2.2, y - 1.3, 1.1, "bone", x=side * 2.6),
                    centred("fang", 0.6, y - 2.8, y - 2.2, 0.8, "bone", x=side * 2.2)]
        return out, y + 1.4
    if s == "gate":  # C: a brass bar between two gate towers, the signal stone in a slate plate
        out = [centred("guard", 7.6, y, y + 1.3, 1.8, m), centred("plate", 2.6, y - 0.7, y + 2.4, 2.0, L["grip"]),
               centred("gem", 1.1, y + 0.4, y + 1.5, 2.3, glow, glow=10)]
        for side in (-1, 1):
            out += [centred("tower", 1.4, y - 0.9, y + 2.2, 2.2, m, x=side * 3.5),
                    centred("tower", 1.8, y + 2.2, y + 2.7, 2.4, m, x=side * 3.5)]
        return out, y + 1.3
    if s == "swept":  # B: a mirror bar whose ends sweep up along the blade, the spirit stone in a Veil plate
        out = [centred("guard", 7.6, y, y + 1.2, 1.8, m), centred("plate", 2.4, y - 0.5, y + 2.0, 2.0, L["blade"]),
               centred("gem", 1.0, y + 0.3, y + 1.1, 2.3, glow, glow=12)]
        for side in (-1, 1):
            out += [centred("guard", 1.0, y + 1.2, y + 2.2, 1.6, m, x=side * 3.3),
                    centred("guard", 0.8, y + 2.2, y + 3.0, 1.4, m, x=side * 2.9),
                    centred("guard", 0.6, y + 3.0, y + 3.6, 1.2, m, x=side * 2.6)]
        return out, y + 1.2
    if s == "wings":  # A: the Lord's wings spread up and out in stepped scale, a scale row under, the pearl in gold
        out = [centred("guard", 9.0, y, y + 1.4, 2.0, m), centred("plate", 3.0, y - 0.8, y + 2.6, 2.2, m),
               centred("gem", 1.3, y + 0.1, y + 1.3, 2.5, glow, glow=13)]
        for side in (-1, 1):
            out += [box("wing", side * 2.4, side * 4.5, y + 1.4, y + 2.4, 1.6, "lord"),
                    box("wing", side * 3.5, side * 5.0, y + 2.4, y + 3.4, 1.4, "lord"),
                    box("wing", side * 4.3, side * 5.4, y + 3.4, y + 4.4, 1.2, "lord"),
                    centred("scale", 2.2, y - 0.9, y, 1.6, "lord", x=side * 2.6)]
        return out, y + 1.4
    if s == "crown":  # S: a gold bar crowned with five points, the crimson in the middle
        out = [centred("guard", 9.0, y, y + 1.4, 2.0, m), centred("plate", 3.4, y - 0.8, y + 2.2, 2.2, m),
               centred("gem", 1.3, y + 0.1, y + 1.25, 2.5, glow, glow=15), centred("point", 1.0, y + 2.2, y + 3.4, 1.6, m)]
        for x, h in ((-4.0, 1.6), (-2.6, 1.0), (2.6, 1.0), (4.0, 1.6)):
            out.append(centred("point", 0.9, y + 1.4, y + 1.4 + h, 1.4, m, x=x))
        return out, y + 1.4
    raise ValueError(s)


# Blade profiles: segments (length, lo, hi[, kind]) up from the guard; kind "ricasso" (unsharpened) or "tip" (the point)

def leaf(L, rng):  # E: a knapped flint leaf, widest two thirds up, its edges chipped flake by flake
    halves = [1.1, 1.25, 1.4, 1.55, 1.65, 1.7, 1.6, 1.4, 1.1, 0.75, 0.4]
    h = L["length"] / len(halves)
    out = []
    for i, w in enumerate(halves):
        chip = 0.14 if i % 2 else 0.0
        out.append((h, -w + rng.uniform(0, 0.1) + chip, w - rng.uniform(0, 0.1) - (0.14 - chip), "tip" if i >= 9 else None))
    return out


def cleaver(L, rng):  # D: broad and straight-backed, the point clipped up from the edge to the spine
    tip = [(1.3, -1.1, 1.85), (1.2, -0.5, 1.75), (1.0, 0.1, 1.5), (0.9, 0.6, 1.15)]
    return [(L["length"] - sum(t[0] for t in tip), -1.5, 1.8, None)] + [t + ("tip",) for t in tip]


def straight(L, rng):  # C: slender and straight off a squared ricasso, the point drawn long
    tip = [(1.2, -1.05, 1.05), (1.1, -0.8, 0.8), (1.0, -0.55, 0.55), (0.9, -0.3, 0.3)]
    return [(2.0, -0.9, 0.9, "ricasso"), (L["length"] - 2.0 - sum(t[0] for t in tip), -1.25, 1.25, None)] + \
           [t + ("tip",) for t in tip]


def wavy(L, rng):  # B: a flamberge, waved like the Veil's ripple, the point mirror-bright
    tip = [(1.1, -0.95, 0.95), (1.0, -0.65, 0.65), (0.9, -0.35, 0.35)]
    n = 10
    h = (L["length"] - sum(t[0] for t in tip)) / n
    return [(h, -w, w, None) for w in (1.45 if i % 2 == 0 else 1.1 for i in range(n))] + [t + ("tip",) for t in tip]


def broad(L, rng):  # A: broad at the guard, tapering straight to a strong point
    tip = [(1.2, -1.15, 1.15), (1.1, -0.85, 0.85), (0.9, -0.55, 0.55), (0.8, -0.3, 0.3)]
    n = 7
    h = (L["length"] - sum(t[0] for t in tip)) / n
    return [(h, -(1.8 - 0.07 * i), 1.8 - 0.07 * i, None) for i in range(n)] + [t + ("tip",) for t in tip]


def spire(L, rng):  # S: a long spire, straight for a third, then tapering all the way to the point
    n = 9
    h = (L["length"] - 6.0) / n
    return [(6.0, -1.6, 1.6, None)] + [(h, -(1.6 - 0.15 * (k + 1)), 1.6 - 0.15 * (k + 1), "tip") for k in range(n)]


PROFILES = {"leaf": leaf, "cleaver": cleaver, "straight": straight, "wavy": wavy, "broad": broad, "spire": spire}


def blade(L, y0, rng):
    """The blade on its profile: each segment a core and a thinner bevel each side (equal neighbours merged), with the
    tier's spine, inlay or runes."""
    segs = []
    for s in PROFILES[L["profile"]](L, rng):
        h, lo, hi, kind = s
        lo, hi = round(lo, 3), round(hi, 3)
        if segs and segs[-1][1:] == (lo, hi, kind):
            segs[-1] = (segs[-1][0] + h, lo, hi, kind)
        else:
            segs.append((h, lo, hi, kind))
    out, y = [], y0
    body = []  # (y0, y1, lo, hi) of the sharpened, untapered segments: where spines and inlays run
    for h, lo, hi, kind in segs:
        w, seg = hi - lo, (lo, hi)
        core = L.get("tip", L["blade"]) if kind == "tip" else L["blade"]
        if kind == "ricasso":
            out.append(box("ricasso", lo, hi, y, y + h, 1.0, L["grip"], seg=seg))
        elif w < 1.0:  # the very point: all edge
            out.append(box("blade", lo, hi, y, y + h, 0.5, L["edge"], seg=seg))
        else:
            e = min(0.45, w * 0.22)
            thick = 0.8 if w > 1.8 else 0.7
            lo_mat = L.get("edge_lo", L["edge"])
            out += [box("blade", lo + e, hi - e, y, y + h, thick, core, seg=seg),
                    box("edge", lo, lo + e, y, y + h, 0.4, lo_mat, glow=11 if lo_mat == "spirit" else 0, seg=seg),
                    box("edge", hi - e, hi, y, y + h, 0.4, L["edge"], seg=seg)]
            if kind is None:
                body.append((y, y + h, lo, hi))
        y += h
    if L.get("spine"):  # D: a bronze back along the body, three teeth off it
        b0, b1, lo = body[0][0], body[-1][1], body[0][2]
        out.append(box("spine", lo - 0.45, lo + 0.2, b0, b1 - 0.4, 1.0, L["spine"]))  # short of the clip
        for k in range(3):
            ty = b0 + 2.0 + k * 2.8
            out.append(box("tooth", lo - 1.0, lo - 0.45, ty, ty + 0.8, 0.5, "bone"))
    inlay = L.get("inlay")
    if inlay in ("line", "runes"):
        mat = L.get("inlay_mat", L["glow"])
        b0, b1 = body[0][0] + 0.8, body[-1][1] - 0.8
        spans = [(b0, b1)] if inlay == "line" else [(b0 + k * 1.8, b0 + k * 1.8 + 0.8) for k in range(int((b1 - b0 + 1.0) // 1.8))]
        thick = 0.8 if min(s[3] - s[2] for s in body) > 1.8 else 0.7
        for a, b in spans:
            for side in (-1, 1):  # laid just proud of each flat (never coplanar with it)
                out.append(box("inlay" if inlay == "line" else "rune", -0.3, 0.3, a, b, 0.04, mat,
                               z=side * (thick / 2 + 0.02), glow=10 if inlay == "line" else 15))
    return out, y


def longsword(t):
    L = LOOKS[t]
    rng = random.Random("arms_longsword_" + t)
    boxes = pommel(L) + grip(L)
    gb, y = guard(L)
    bb, _ = blade(L, y, rng)
    return cull(boxes + gb + bb)


GRAMMARS = {"longsword": longsword}

# ---------------------------------------------------------------------------------------------------------------------
# Faces: which are seen, their sizes, coplanar pairs

FACES = ("north", "south", "east", "west", "up", "down")
# face -> (axis, side): the axis its normal runs along, and whether it is the box's high (+1) or low (-1) end
NORMAL = {"east": (0, 1), "west": (0, -1), "up": (1, 1), "down": (1, -1), "south": (2, 1), "north": (2, -1)}
EPS = 1e-6


def face_plane(b, face):
    axis, side = NORMAL[face]
    return axis, side, (b.hi if side > 0 else b.lo)[axis]


def face_rect(b, axis):
    return [(b.lo[i], b.hi[i]) for i in range(3) if i != axis]


def cull(boxes):
    """Leaves out each face another box fully covers (its volume reaches past the face's plane over the whole face), and
    any box left with none (the game refuses an element without faces)."""
    for b in boxes:
        for face in FACES:
            axis, side, plane = face_plane(b, face)
            rect = face_rect(b, axis)
            for o in boxes:
                if o is b:
                    continue
                reach = (o.lo[axis] <= plane + EPS and o.hi[axis] > plane + EPS) if side > 0 else \
                        (o.hi[axis] >= plane - EPS and o.lo[axis] < plane - EPS)
                if reach and all(ol <= r0 + EPS and oh >= r1 - EPS for (r0, r1), (ol, oh) in zip(rect, face_rect(o, axis))):
                    b.faces.discard(face)
                    break
    return [b for b in boxes if b.faces]


def coplanar(boxes):
    """Pairs of faces in one plane facing one way and overlapping: the GPU would flicker between them."""
    bad = []
    for i, a in enumerate(boxes):
        for b in boxes[i + 1:]:
            for face in a.faces & b.faces:
                axis, _, pa = face_plane(a, face)
                _, _, pb = face_plane(b, face)
                if abs(pa - pb) > EPS:
                    continue
                if all(min(a1, b1) - max(a0, b0) > EPS for (a0, a1), (b0, b1) in zip(face_rect(a, axis), face_rect(b, axis))):
                    bad.append((a.part, b.part, face))
    return bad


def face_dims(b, face):
    sx, sy, sz = b.size()
    return {"north": (sx, sy), "south": (sx, sy), "east": (sz, sy), "west": (sz, sy), "up": (sx, sz), "down": (sx, sz)}[face]


def texels(v):
    return max(1, int(math.ceil(v - EPS)))


# ---------------------------------------------------------------------------------------------------------------------
# The atlas and its painting

class Atlas:
    """A shelf packer over faces sorted tallest first: each face a rect at one texel per model unit."""

    def __init__(self, size=ATLAS):
        self.size, self.x, self.y, self.row = size, 0, 0, 0
        self.img = Image.new("RGBA", (size, size), (0, 0, 0, 0))

    def place(self, w, h):
        if self.x + w > self.size:
            self.x, self.y, self.row = 0, self.y + self.row, 0
        if self.y + h > self.size or w > self.size:
            raise ValueError("atlas full")
        r = (self.x, self.y, w, h)
        self.x += w
        self.row = max(self.row, h)
        return r


def shade(mat, i):
    r = G.PAL[mat]
    return G.P(mat, max(0, min(len(r) - 1, i)))


def top(mat):
    return len(G.PAL[mat]) - 1


GLOWING = ("gem", "inlay", "rune")
METAL = ("gold", "brass", "bronze", "mirror", "bone", "ivory", "cream")


def paint(img, rect, b, face, look, rng):
    """One face's texels. v runs down the face from its top; u across it."""
    x0, y0, w, h = rect
    mat, hi = b.mat, top(b.mat)
    flat = face in ("north", "south")
    for j in range(h):
        for i in range(w):
            u, v = (i + 0.5) / w, (j + 0.5) / h
            if b.glow:  # light itself: the brightest steps, a hotter heart
                c = shade(mat, hi - (0 if 0.25 < u < 0.75 and 0.25 < v < 0.75 else 1))
            elif b.part == "blade":
                if flat:  # ground steel: lines along the blade (one per column), now and then a fleck
                    lvl = (hi + 1) // 2 + (1 if (i * 7 + int(rng.random() < 0.15)) % 3 == 0 else 0)
                    if look.get("inlay") == "groove" and abs(u - 0.5) * w < 0.6 and 0.04 < v < 0.96:
                        lvl = 1  # the fuller, cut down the middle
                    if rng.random() < 0.05:
                        lvl -= 1
                else:
                    lvl = hi - 1
                c = shade(mat, lvl)
            elif b.part == "edge":  # the bevels catch the light: brightest on their outer faces
                c = shade(mat, hi if not flat else hi - (1 if (j + i) % 4 else 0))
            elif b.part == "grip":  # a diagonal wrap
                c = shade(mat, 3 if (i + j) % 3 else 1) if face not in ("up", "down") else shade(mat, 2)
            elif b.part in ("wing", "scale", "spine") and mat == "lord":  # Lordscale: overlapping scales, rims caught
                c = shade(mat, 4 if (i + 2 * (j // 2)) % 3 == 0 and j % 2 == 0 else (2 if j % 2 else 3))
            elif b.part == "ricasso":
                c = shade(mat, 2 if 0.15 < v < 0.85 else 3)
            else:  # metal and bone: lit from above, a rim highlight on the sides, a little wear
                if face == "up":
                    lvl = 4
                elif face == "down":
                    lvl = 1
                else:
                    lvl = 4 if v < 0.34 else (3 if v < 0.7 else 2)
                    if i == 0 or i == w - 1:
                        lvl -= 1
                if rng.random() < 0.08:
                    lvl -= 1
                c = shade(mat, lvl + (1 if mat in METAL else 0))
            img.putpixel((x0 + i, y0 + j), (c[0], c[1], c[2], 255))


# ---------------------------------------------------------------------------------------------------------------------
# The 3D model

def rotate(p):
    """Sword space to model space: the grip at the hand point, the blade along the diagonal (the rotation every element
    carries, so the parts stay one rigid weapon)."""
    a = math.radians(ANGLE)
    x, y, z = p
    return (HAND[0] + x * math.cos(a) - y * math.sin(a), HAND[1] + x * math.sin(a) + y * math.cos(a), HAND[2] + z)


def model_json(boxes, look, seed):
    atlas = Atlas()
    rng = random.Random(seed)
    jobs = [(bi, face) for bi, b in enumerate(boxes) for face in FACES if face in b.faces]
    jobs.sort(key=lambda j: (-texels(face_dims(boxes[j[0]], j[1])[1]), -texels(face_dims(boxes[j[0]], j[1])[0])))
    uvs = {}
    s = 16.0 / ATLAS
    for bi, face in jobs:
        b = boxes[bi]
        fw, fh = face_dims(b, face)
        rect = atlas.place(texels(fw), texels(fh))
        paint(atlas.img, rect, b, face, look, rng)
        x, y, _, _ = rect
        uvs[(bi, face)] = [round(x * s, 4), round(y * s, 4), round((x + fw) * s, 4), round((y + fh) * s, 4)]
    elements = []
    for bi, b in enumerate(boxes):
        el = {"name": b.part, "from": [round(b.lo[i] + HAND[i], 4) for i in range(3)], "to": [round(b.hi[i] + HAND[i], 4) for i in range(3)],
              "rotation": {"origin": list(HAND), "x": 0, "y": 0, "z": ANGLE},
              "faces": {f: {"uv": uvs[(bi, f)], "texture": "#atlas"} for f in FACES if f in b.faces}}
        if b.glow:
            el["light_emission"] = b.glow
        elements.append(el)
    return elements, atlas.img


def bounds(boxes):
    pts = [rotate((x, y, z)) for b in boxes for x in (b.lo[0], b.hi[0]) for y in (b.lo[1], b.hi[1]) for z in (b.lo[2], b.hi[2])]
    return [min(p[i] for p in pts) for i in range(3)], [max(p[i] for p in pts) for i in range(3)]


# ---------------------------------------------------------------------------------------------------------------------
# The icon: the grammar's front on vanilla's diagonal

ICON_FROM, ICON_TO = (1.2, 14.8), (14.8, 1.2)  # the pommel's end and the tip, in icon pixels


def front(boxes, x, y):
    """The box seen at (x, y) in sword space from the front: the one reaching furthest toward the viewer."""
    best = None
    for b in boxes:
        if b.lo[0] <= x <= b.hi[0] and b.lo[1] <= y <= b.hi[1] and (best is None or b.hi[2] > best.hi[2]):
            best = b
    return best


# Widths in the icon, as shares of to scale: the outline costs every part a pixel each side, so a blade drawn to scale
# reads as a dagger (seen in the first sheet); vanilla's own blades are about a seventh as wide as they are long.
ICON_BLADE, ICON_HILT = 0.6, 1.0


def icon(boxes, look):
    """16x16, vanilla's style: a dark outline, light from the top left, the blade's lit edge bright and its far edge
    dark; and the glow layer (what shines at night). The sword fills the diagonal; a part thinner than a pixel at that
    scale is held to one."""
    y_lo = min(b.lo[1] for b in boxes)
    y_hi = max(b.hi[1] for b in boxes)
    k = math.dist(ICON_FROM, ICON_TO) / (y_hi - y_lo)
    ox, oy = ICON_FROM
    c, glow = G.Canvas(), G.Canvas()
    for py in range(16):
        for px in range(16):
            t, s = G.diag(px, py, ox, oy)
            y = y_lo + t / k
            best = None
            for b in boxes:
                kx = k * (ICON_BLADE if b.seg else ICON_HILT)
                pad_x = max(0.0, (1.0 - (b.hi[0] - b.lo[0]) * kx) / 2)
                pad_y = max(0.0, (1.0 - (b.hi[1] - b.lo[1]) * k) / 2) / k
                if b.lo[1] - pad_y <= y <= b.hi[1] + pad_y and b.lo[0] * kx - pad_x <= s <= b.hi[0] * kx + pad_x \
                        and (best is None or b.hi[2] > best[0].hi[2]):
                    best = (b, s / kx)
            if best is None:
                continue
            col = icon_colour(best[0], best[1], y, look)
            c.set(px, py, col)
            if best[0].glow:
                glow.set(px, py, col)
    G.outline(c)
    return c, glow


def icon_colour(b, x, y, look):
    mat, hi = b.mat, top(b.mat)
    if b.glow:
        return shade(mat, hi)
    if b.seg is not None:  # the blade as one band across: lit edge, flat, shadowed edge
        lo, h = b.seg
        sn = (x - (lo + h) / 2) / max((h - lo) / 2, EPS)
        edge_lo = look.get("edge_lo", look["edge"])
        if sn < -0.45:
            return shade(edge_lo, top(edge_lo))
        if sn > 0.6:
            return shade(look["edge"], 1)
        core = b.mat if b.part != "edge" else look["blade"]
        return shade(core, (top(core) + 1) // 2 + (1 if sn < 0.1 else 0))
    if b.part == "grip":
        return shade(mat, 3 if int((y - G0) * 1.3) % 2 else 1)
    sn = (x - (b.lo[0] + b.hi[0]) / 2) / max((b.hi[0] - b.lo[0]) / 2, EPS)
    lvl = 4 if sn < -0.3 else (3 if sn < 0.4 else 1)
    return shade(mat, lvl + (1 if mat in METAL else 0))


def silhouette(boxes, px=48):
    """The front projection on a px grid in model space (for the distinctness check)."""
    lo, hi = -4.0, 24.0
    a = math.radians(-ANGLE)
    grid = [[0] * px for _ in range(px)]
    for gy in range(px):
        for gx in range(px):
            X = lo + (gx + 0.5) * (hi - lo) / px - HAND[0]
            Y = lo + (gy + 0.5) * (hi - lo) / px - HAND[1]
            rx, ry = X * math.cos(a) - Y * math.sin(a), X * math.sin(a) + Y * math.cos(a)
            grid[gy][gx] = 1 if front(boxes, rx, ry) else 0
    return grid


def icon_likeness(a, b):
    """(outline overlap, mean colour distance 0..1 where both are drawn) of two icons."""
    both = either = 0
    dist = 0.0
    for y in range(16):
        for x in range(16):
            ca, cb = a.get(x, y), b.get(x, y)
            if ca[3] and cb[3]:
                both += 1
                dist += math.dist(ca[:3], cb[:3]) / 441.7
            if ca[3] or cb[3]:
                either += 1
    return both / max(1, either), dist / max(1, both)


ICON_OUTLINE_MAX, ICON_COLOUR_MIN = 0.8, 0.12  # alike in outline past the first, they must differ by the second


def alike(a, b):
    inter = sum(1 for ra, rb in zip(a, b) for x, y in zip(ra, rb) if x and y)
    union = sum(1 for ra, rb in zip(a, b) for x, y in zip(ra, rb) if x or y)
    return inter / union if union else 1.0


# ---------------------------------------------------------------------------------------------------------------------
# Output

def weapon_boxes(row):
    return GRAMMARS["longsword"](row[2])


def write_all():
    lang, java_rows = {}, []
    tex_dir = os.path.join(RES, "assets/awaken/textures/item/arms")
    os.makedirs(tex_dir, exist_ok=True)
    for idx, row in enumerate(CATALOG):
        wid, name, tier, dmg, dur, rarity, ench, repair = row
        boxes = weapon_boxes(row)
        look = LOOKS[tier]
        elements, atlas = model_json(boxes, look, 7700 + idx)
        atlas.save(os.path.join(tex_dir, wid + ".png"))
        ic, glow = icon(boxes, look)
        ic.image().save(os.path.join(tex_dir, wid + "_icon.png"))
        glow.image().save(os.path.join(tex_dir, wid + "_icon_glow.png"))
        tex = "awaken:item/arms/" + wid
        _write(os.path.join(RES, "assets/awaken/models/item", wid + "_in_hand.json"), {
            "parent": "minecraft:item/handheld", "texture_size": [ATLAS, ATLAS],
            "textures": {"atlas": tex, "particle": tex + "_icon"}, "elements": elements})
        _write(os.path.join(RES, "assets/awaken/models/item", wid + ".json"), {
            "parent": "minecraft:item/handheld",
            "textures": {"layer0": tex + "_icon", "layer1": {"sprite": tex + "_icon_glow", "neoforge_data": {"light_emission": 15}}}})
        _write(os.path.join(RES, "assets/awaken/items", wid + ".json"), {"model": {
            "type": "minecraft:select", "property": "minecraft:display_context",
            "cases": [{"when": ["gui", "ground", "fixed", "on_shelf"], "model": {"type": "minecraft:model", "model": "awaken:item/" + wid}}],
            "fallback": {"type": "minecraft:model", "model": "awaken:item/" + wid + "_in_hand"}}})
        _write(os.path.join(RES, "data/awaken/recipe", wid + ".json"), {
            "type": "minecraft:crafting_shaped", "category": "equipment", "group": "awaken_arms_longsword",
            "key": {"C": "#awaken:hunter_core_" + tier.lower(), "X": "awaken:" + G.SET[tier] + "_blade", "B": "#awaken:hunter_bind_" + tier.lower()},
            "pattern": ["C", "X", "B"], "result": {"id": "awaken:" + wid}})
        lang["item.awaken." + wid] = name
        trail = "0x" + look["trail"].lstrip("#").upper()
        java_rows.append(f'            new Weapon("{wid}", "longsword", "{tier}", {dmg}F, {LONGSWORD_SPEED}F, {dur}, Rarity.{rarity}, {ench}, "{repair}", {trail})')
    # swords to vanilla (its sword enchantments, sweeping, durability) alongside the Hunter blades (merged at build)
    _write(os.path.join(MERGE, "data/minecraft/tags/item/swords.json"), {"values": ["awaken:" + row[0] for row in CATALOG]})
    lpath = os.path.join(MERGE, "assets/awaken/lang/en_us.json")
    os.makedirs(os.path.dirname(lpath), exist_ok=True)
    cur = json.load(open(lpath)) if os.path.exists(lpath) else {}
    cur.update(lang)
    with open(lpath, "w") as fh:
        json.dump(dict(sorted(cur.items())), fh, indent=2, ensure_ascii=False)
        fh.write("\n")
    with open(JAVA, "w") as fh:
        fh.write(JAVA_HEAD + ",\n".join(java_rows) + "\n    );\n}\n")


JAVA_HEAD = """package dev.awaken.arms;

import net.minecraft.world.item.Rarity;

import java.util.List;

// GENERATED by tools/art/specs/arms_weapons.py from its CATALOG: edit the catalog there, not this file.

/**
 * Arms' weapons as plain data (SPEC §24): ids, fighting style, Hunter tier, stats. {@link ArmsItems} registers one item
 * per row; the models, textures, recipes and names come from the same catalog.
 */
public final class ArmsData {
    private ArmsData() {}

    /**
     * damage: attack damage over the player's 1; speed: the attack speed modifier; enchantability as vanilla's; repair:
     * the item tag that mends it; trail: the light of its swings (rgb).
     */
    public record Weapon(String id, String style, String tier, float damage, float speed, int durability, Rarity rarity,
                         int enchantability, String repair, int trail) {}

    public static final List<Weapon> WEAPONS = List.of(
"""


def _write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as fh:
        json.dump(obj, fh, indent=1)
        fh.write("\n")


def build(out):
    """build_all.py entry."""
    write_all()


def check():
    """Every structural check on every weapon; prints each weapon's numbers. Returns the problems."""
    bad, sils, icons = [], {}, {}
    for idx, row in enumerate(CATALOG):
        wid = row[0]
        boxes = weapon_boxes(row)
        lo, hi = bounds(boxes)
        raw_lo = min(b.lo[i] + HAND[i] for b in boxes for i in range(3))
        raw_hi = max(b.hi[i] + HAND[i] for b in boxes for i in range(3))
        if min(lo) < -16 or max(hi) > 32 or raw_lo < -16 or raw_hi > 32:
            bad.append(f"{wid}: outside the model box")
        quads = sum(len(b.faces) for b in boxes)
        if quads > 400:
            bad.append(f"{wid}: {quads} quads (max 400)")
        g = [b for b in boxes if b.part == "grip"][0]
        if any(abs((g.lo[i] + g.hi[i]) / 2) > EPS for i in range(3)):
            bad.append(f"{wid}: the grip isn't at the hand point")
        for b in boxes:
            if min(b.size()) <= EPS:
                bad.append(f"{wid}: {b.part} is flat")
        for a, b, face in coplanar(boxes):
            bad.append(f"{wid}: {a} and {b} share a {face} face (z-fighting)")
        try:
            model_json(boxes, LOOKS[row[2]], 7700 + idx)
        except ValueError as e:
            bad.append(f"{wid}: {e}")
        sils[wid] = silhouette(boxes)
        icons[wid] = icon(boxes, LOOKS[row[2]])[0]
        print(f"  {wid:24s} {len(boxes):3d} boxes {quads:4d} quads  tip {hi[1]:.1f}")
    ids = list(sils)
    for i in range(len(ids)):
        for j in range(i + 1, len(ids)):
            a, b = ids[i], ids[j]
            s = alike(sils[a], sils[b])
            shape, colour = icon_likeness(icons[a], icons[b])
            print(f"  {a:24s} ~ {b:24s} silhouette {s:.0%}  icon outline {shape:.0%} colour apart {colour:.2f}")
            if s > 0.85:
                bad.append(f"{a} and {b}: silhouettes {s:.0%} alike")
            if shape > ICON_OUTLINE_MAX and colour < ICON_COLOUR_MIN:
                bad.append(f"{a} and {b}: icons {shape:.0%} alike in outline and only {colour:.2f} apart in colour")
    return bad


def main(argv):
    cmd = argv[0] if argv else "build"
    if cmd in ("build", "check"):
        bad = check()
        for b in bad:
            print("CHECK", b)
        if bad:
            print(f"{len(bad)} problems")
            return 1
        if cmd == "build":
            write_all()
            print(f"{len(CATALOG)} weapons written")
        else:
            print("ok")
        return 0
    if cmd == "preview":
        import model_preview
        out = argv[1] if len(argv) > 1 else "."
        print(model_preview.sheet([(row[0], row[1]) for row in CATALOG], os.path.join(out, "arms_weapons.png")))
        return 0
    raise SystemExit(__doc__)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
