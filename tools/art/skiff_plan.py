"""The Far Landing's skiff and its mooring, as a plan (SPEC §67.4).

The shape lives here: every block of the skiff (with the part of it that moves in flight), of the pier and mast round
it, and the landing's extras. Run it to check the plan (no two blocks in one place, everything inside the footprint), to
draw previews (three outside views and the view from the helm) and to write the Java the game reads:

    python3 tools/art/skiff_plan.py            check + write src/main/java/dev/awaken/frontier/SkiffPlan.java
    python3 tools/art/skiff_plan.py OUT_DIR    the same, and previews into OUT_DIR

Drawn facing south: x across, y up (0 is the main deck's planks), z along, the bow at +z. The pier is on the -x side.
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "../.."))
JAVA = os.path.join(ROOT, "src/main/java/dev/awaken/frontier/SkiffPlan.java")

# what moves in flight: the hull and all on it, the two wings (each about its root), the wind-wheel (about its hub)
HULL, WING_A, WING_B, WHEEL = 0, 1, 2, 3
# the mooring's footprint (inclusive) and the lowest block of the skiff; the pad's top is one under that
FOOT_X, FOOT_Z0, FOOT_Z1, KEEL, PAD, CLEAR_UP = 8, -9, 9, -3, -4, 12
PAD_R2 = 42  # the old landing ring: a disc of 6.5 round the berth's middle
HELM, STAND = (0, 2, -3), (0, 2, -4)
HOLD, BOARD = (-8, 1, -6), (-8, 1, -5)
HUB = (0, 4, -7)
WING_Y, WING_ROOT = 3, 2.5

DECK = "awaken:skiff_deck[axis=z]"
HULLB = "awaken:skiff_hull"
BRASS = "awaken:skiff_brass"
TRIM = "awaken:precursor_trim"
GLOW = "awaken:precursor_glow_panel"
PLATE = "awaken:precursor_plating"


def stair(facing, top=True, kind="awaken:skiff_hull_stairs"):
    return "%s[facing=%s,half=%s]" % (kind, facing, "top" if top else "bottom")


def spar(axis):
    return "awaken:skiff_spar[axis=%s]" % axis


def sail(plane, band=False):
    return "awaken:skiff_sail[band=%s,plane=%s]" % ("true" if band else "false", plane)


def gunwale(sides):
    return "awaken:skiff_gunwale[" + ",".join("%s=%s" % (s, "true" if s in sides else "false") for s in ("east", "north", "south", "west")) + "]"


def lamp(glass, hanging=False):
    return "awaken:skiff_lamp[glass=%s,hanging=%s]" % (glass, "true" if hanging else "false")


def half_width(z):
    """The main deck's half width at each z: five wide, narrowing to the bow's point and the stern."""
    if z < -6 or z > 7:
        return -1
    return {-6: 1, 4: 1, 5: 1, 6: 0, 7: 0}.get(z, 2)


def skiff():
    out = []

    def add(x, y, z, state, rig=HULL):
        out.append((x, y, z, state, rig))

    # the main deck, and the quarterdeck a step up at the stern (the helm stands on it and sees over everything)
    for z in range(-6, 8):
        for x in range(-half_width(z), half_width(z) + 1):
            add(x, 0, z, DECK)
    for z in range(-6, -2):
        for x in range(-half_width(z), half_width(z) + 1):
            add(x, 1, z, DECK)
    # the hull under it: plating with flared shoulders, a gold keel, and the lift core in its fin
    for z in range(-5, 4):
        for x in (-1, 0, 1):
            add(x, -1, z, HULLB)
        add(-2, -1, z, stair("east"))
        add(2, -1, z, stair("west"))
    add(0, -1, 4, HULLB)
    add(-1, -1, 4, stair("east"))
    add(1, -1, 4, stair("west"))
    add(0, -1, 5, stair("north"))
    for x in (-1, 0, 1):
        add(x, -1, -6, stair("south"))
    add(0, -1, 6, stair("north"))
    for z in range(-4, 4):
        add(0, -2, z, TRIM)
    add(0, -2, 4, stair("north"))
    add(0, -2, -5, stair("south"))
    add(0, -3, 0, GLOW)
    add(0, -3, -1, TRIM)
    add(0, -3, 1, TRIM)
    # the gunwales: low, capped in gold, open at the pier's gangway (-x, z 0)
    for z in range(-2, 3):
        if z != 0:
            add(-2, 1, z, gunwale({"west"}))
        add(2, 1, z, gunwale({"east"}))
    for z in (4, 5):
        add(-1, 1, z, gunwale({"west"} | ({"south"} if z == 5 else set())))
        add(1, 1, z, gunwale({"east"} | ({"south"} if z == 5 else set())))
    add(0, 1, 6, gunwale({"east", "west"}))
    for z in range(-5, -2):
        add(-2, 2, z, gunwale({"west"} | ({"north"} if z == -5 else set())))
        add(2, 2, z, gunwale({"east"} | ({"north"} if z == -5 else set())))
    add(-1, 2, -6, gunwale({"west", "north"}))
    add(1, 2, -6, gunwale({"east", "north"}))
    # the stem, the bowsprit and the bow light, all under the helm's line of sight
    add(0, 1, 7, stair("south", False))
    add(0, 1, 8, spar("z"))
    add(0, 1, 9, spar("y"))
    add(0, 2, 9, lamp("blue"))
    # two steps up to the quarterdeck, the helm at its front edge
    add(-1, 1, -2, stair("north", False, "minecraft:spruce_stairs"))
    add(1, 1, -2, stair("north", False, "minecraft:spruce_stairs"))
    add(HELM[0], HELM[1], HELM[2], "awaken:skiff_helm[facing=south]")
    # cargo lashed on the foredeck, either side of the middle
    add(-1, 1, 2, "awaken:cask[orient=upright]")
    add(1, 1, 2, "awaken:cargo_crate")
    add(1, 1, 3, "awaken:sack[count=2,fill=grain]")
    # the sternpost (the wheel's hub is fixed to its head) and the stern light on top of it
    add(0, 2, -6, spar("y"))
    add(0, 3, -6, spar("y"))
    add(0, 4, -6, spar("y"))
    add(0, 5, -6, lamp("blue"))
    # the wind-wheel: four canvas blades on a brass hub, the same from every quarter turn
    hx, hy, hz = HUB
    add(hx, hy, hz, BRASS, WHEEL)
    for r in (1, 2, 3):
        tip = r == 3
        add(hx, hy + r, hz, spar("y"), WHEEL)
        add(hx + 1, hy + r, hz, sail("athwart", tip), WHEEL)
        add(hx - r, hy, hz, spar("x"), WHEEL)
        add(hx - r, hy + 1, hz, sail("athwart", tip), WHEEL)
        add(hx, hy - r, hz, spar("y"), WHEEL)
        add(hx - 1, hy - r, hz, sail("athwart", tip), WHEEL)
        add(hx + r, hy, hz, spar("x"), WHEEL)
        add(hx + r, hy - 1, hz, sail("athwart", tip), WHEEL)
    # the wings: a boom from a king post on each gunwale, canvas swept back from it
    span = {2: 8, 1: 7, 0: 6, -1: 5, -2: 4}
    for side, rig in ((1, WING_A), (-1, WING_B)):
        # the wing's mount: a brass block at the hull's shoulder (it closes the gunwale there), the king post on it
        add(side * 2, 1, 3, BRASS)
        add(side * 2, 2, 3, spar("y"))
        add(side * 2, 3, 3, spar("y"))
        for x in range(3, 9):
            add(side * x, WING_Y, 3, spar("x"), rig)
        for z, far in span.items():
            for x in range(3, far + 1):
                add(side * x, WING_Y, z, sail("flat", x == far), rig)
    return out


def jetty():
    out = []

    def add(x, y, z, state):
        out.append((x, y, z, state, HULL))

    plank, beam, fence = "minecraft:spruce_planks", "minecraft:stripped_spruce_log[axis=z]", "minecraft:spruce_fence"
    # the pier along the -x side, a kerb beam on its outer edge
    for z in range(-8, 5):
        add(-3, 0, z, plank)
        add(-4, 0, z, plank)
        add(-5, 0, z, beam)
    # the bay on its outer side, under a slate roof: the hold and the board stand here at the landing
    for z in range(-8, -3):
        for x in (-8, -7, -6):
            add(x, 0, z, plank)
    for z in (-8, -4):
        for y in (1, 2):
            add(-8, y, z, fence)
            add(-6, y, z, fence)
    for z in range(-8, -3):
        add(-8, 3, z, "awaken:slate_tile_stairs[facing=east,half=bottom]")
        add(-7, 3, z, "awaken:slate_tiles")
        add(-6, 3, z, "awaken:slate_tile_stairs[facing=west,half=bottom]")
    add(-7, 2, -6, lamp("amber", True))
    add(-8, 1, -7, "awaken:bench[facing=east]")
    # the rail along the pier's outer edge, lamps on its end posts
    for z in range(-3, 5):
        add(-5, 1, z, fence)
    add(-5, 2, 4, lamp("amber"))
    add(-5, 1, -8, fence)
    add(-5, 2, -8, lamp("amber"))
    add(-3, 1, -8, fence)
    add(-3, 2, -8, lamp("amber"))
    # goods waiting at the pier's bow end
    add(-4, 1, 4, "awaken:cargo_crate")
    add(-4, 1, 3, "awaken:cask[orient=upright]")
    add(-3, 1, 4, "awaken:sack[count=3,fill=grain]")
    # the mooring mast: three lights you can find your way back by, and a pennant on the wind
    for y in range(0, 8):
        add(-8, y, -3, "minecraft:stripped_spruce_log[axis=y]")
    add(-8, 8, -3, spar("y"))
    add(-8, 9, -3, spar("y"))
    add(-8, 10, -3, lamp("amber"))
    add(-8, 7, -4, "minecraft:stripped_spruce_log[axis=z]")
    add(-8, 7, -2, "minecraft:stripped_spruce_log[axis=z]")
    add(-8, 6, -4, lamp("amber", True))
    add(-8, 6, -2, lamp("amber", True))
    add(-8, 9, -2, sail("fore_aft", False))
    add(-8, 9, -1, sail("fore_aft", True))
    # the cradle the hull rests in
    for z in (-3, 3):
        for x in (-2, -1, 1, 2):
            add(x, -3, z, "minecraft:stripped_spruce_log[axis=x]")
        add(-1, -2, z, "minecraft:stripped_spruce_log[axis=x]")
        add(1, -2, z, "minecraft:stripped_spruce_log[axis=x]")
        add(-2, -2, z, "minecraft:spruce_fence")
        add(2, -2, z, "minecraft:spruce_fence")
    # the old landing ring set in the pad under the core
    for x in range(-2, 3):
        for z in range(-2, 3):
            d = round(math.hypot(x, z))
            if d == 2:
                add(x, PAD, z, TRIM)
            elif d < 2:
                add(x, PAD, z, GLOW if (x, z) == (0, 0) else PLATE)
    for x, z in ((5, 0), (0, 5), (0, -5), (4, 3), (4, -3), (3, 4), (3, -4)):
        add(x, PAD, z, GLOW if (x, z) in ((5, 0), (0, 5), (0, -5)) else TRIM)
    return out


def landing_extras():
    return [(HOLD[0], HOLD[1], HOLD[2], "minecraft:barrel[facing=up,open=false]", HULL),
            (BOARD[0], BOARD[1], BOARD[2], "awaken:notice_board[facing=east]", HULL)]


def ridge_extras():
    return [(HOLD[0], HOLD[1], HOLD[2], "awaken:cask[orient=upright]", HULL),
            (BOARD[0], BOARD[1], BOARD[2], "awaken:cargo_crate", HULL)]


# where the pier's posts and the mast go down to whatever is under them (x, z, first y)
POSTS = [(-5, -8), (-3, -8), (-5, -2), (-5, 4), (-3, 4), (-8, -8), (-8, -4), (-6, -8), (-8, -3)]
# the steps go down from the pier's stern end, toward -z
STEPS_X, STEPS_Z = (-5, -4, -3), -9


# a sail's sides in its own plane, and the way each one points (south-facing: north is -z, west is -x)
SAIL_SIDES = {"flat": ("north", "south", "west", "east"), "athwart": ("up", "down", "west", "east"), "fore_aft": ("up", "down", "north", "south")}
STEP = {"north": (0, 0, -1), "south": (0, 0, 1), "west": (-1, 0, 0), "east": (1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0)}
AXIS_OF = {"north": "z", "south": "z", "west": "x", "east": "x", "up": "y", "down": "y"}
THICK = {"flat": "y", "athwart": "z", "fore_aft": "x"}
ASSETS = os.path.join(ROOT, "src/main/resources/assets/awaken")


def lash(lists):
    """Every sail meets the wood. The canvas fills its own block, but a spar is a pole four texels thick in the middle of
    the block beside it, so a side of a sail with a spar there stood six texels off it. That side is lashed to the spar
    (the state's side=true): the block model runs the cloth on into the pole. Only a spar of the same list and rig, so
    it moves with the sail in flight, and never one that points at the sail (it would run along inside it)."""
    for name, parts in lists.items():
        spars = {(x, y, z): (s.split("axis=")[1][0], rig) for x, y, z, s, rig in parts if s.startswith("awaken:skiff_spar[")}
        out = []
        for x, y, z, s, rig in parts:
            if s.startswith("awaken:skiff_sail["):
                plane = s.split("plane=")[1].split(",")[0].rstrip("]")
                tied = []
                for d in SAIL_SIDES[plane]:
                    dx, dy, dz = STEP[d]
                    spar_here = spars.get((x + dx, y + dy, z + dz))
                    if spar_here and spar_here[1] == rig and spar_here[0] != AXIS_OF[d]:
                        tied.append(d)
                if tied:
                    s = s[:-1] + "".join(",%s=true" % d for d in tied) + "]"
            out.append((x, y, z, s, rig))
        lists[name] = out


def strip(plane, side, band):
    """The lashing strip's model: the canvas from the sail's edge eight texels on into the spar beside it (past the
    pole's middle, so its end is hidden in the wood and no face lies on the pole's). Its texture runs on from the
    sail's hem the way the cloth would; a band's strip keeps to the dyed panel's plain edge, off its gold."""
    lo, hi = {"x": 0, "y": 0, "z": 0}, {"x": 16, "y": 16, "z": 16}
    lo[THICK[plane]], hi[THICK[plane]] = 7, 9
    a = AXIS_OF[side]
    out_low = side in ("north", "west", "down")
    lo[a], hi[a] = (-8, 0) if out_low else (16, 24)
    near, far = (1, 4) if band else (1, 9)

    def depth(c):  # a texture coordinate along the strip, from its block-space coordinate
        inside = 0 if out_low else 16  # the sail's edge
        t = near + (far - near) * abs(c - inside) / 8.0
        return t if inside == 0 else 16 - t

    axes = {"down": ("x", "z"), "up": ("x", "z"), "north": ("x", "y"), "south": ("x", "y"), "west": ("z", "y"), "east": ("z", "y")}
    normal = {"down": "y", "up": "y", "north": "z", "south": "z", "west": "x", "east": "x"}
    f, t = (lo["x"], lo["y"], lo["z"]), (hi["x"], hi["y"], hi["z"])
    faces = {}
    for face in ("down", "up", "north", "south", "west", "east"):
        if normal[face] == a:
            continue  # its ends: one against the sail's own, one inside the pole
        u0, v0, u1, v1 = {
            "down": (f[0], 16 - t[2], t[0], 16 - f[2]), "up": (f[0], f[2], t[0], t[2]),
            "north": (16 - t[0], 16 - t[1], 16 - f[0], 16 - f[1]), "south": (f[0], 16 - t[1], t[0], 16 - f[1]),
            "west": (f[2], 16 - t[1], t[2], 16 - f[1]), "east": (16 - t[2], 16 - t[1], 16 - f[2], 16 - f[1])}[face]
        ua, va = axes[face]
        # each uv value came from a block coordinate on its axis: map the strip's depth back to the sail's edge texels
        if ua == a:
            src = {"down": (f[0], t[0]), "up": (f[0], t[0]), "north": (t[0], f[0]), "south": (f[0], t[0]), "west": (f[2], t[2]), "east": (t[2], f[2])}[face]
            u0, u1 = depth(src[0]), depth(src[1])
        if va == a:
            src = {"down": (t[2], f[2]), "up": (f[2], t[2]), "north": (t[1], f[1]), "south": (t[1], f[1]), "west": (t[1], f[1]), "east": (t[1], f[1])}[face]
            v0, v1 = depth(src[0]), depth(src[1])
        faces[face] = {"texture": "#t", "uv": [round(u0, 3), round(v0, 3), round(u1, 3), round(v1, 3)]}
    tex = "awaken:block/skiff_sail_band" if band else "awaken:block/skiff_sail"
    return {"parent": "minecraft:block/block", "textures": {"particle": tex, "t": tex},
            "elements": [{"from": list(f), "to": list(t), "faces": faces}]}


def write_sail_models():
    """The sail's blockstate, as parts: the canvas in its plane, and a lashing strip on each side tied to a spar."""
    import json
    turn = {"flat": {}, "athwart": {"x": 90}, "fore_aft": {"x": 90, "y": 90}}
    parts = []
    for band in (False, True):
        for plane in ("flat", "athwart", "fore_aft"):
            model = "awaken:block/skiff_sail_band" if band else "awaken:block/skiff_sail"
            parts.append({"when": {"band": "true" if band else "false", "plane": plane}, "apply": dict({"model": model}, **turn[plane])})
    for band in (False, True):
        for plane in ("flat", "athwart", "fore_aft"):
            for side in SAIL_SIDES[plane]:
                name = "skiff_sail_%slash_%s_%s" % ("band_" if band else "", plane, side)
                with open(os.path.join(ASSETS, "models/block", name + ".json"), "w") as fh:
                    json.dump(strip(plane, side, band), fh, indent=2)
                    fh.write("\n")
                parts.append({"when": {"band": "true" if band else "false", "plane": plane, side: "true"}, "apply": {"model": "awaken:block/" + name}})
    with open(os.path.join(ASSETS, "blockstates/skiff_sail.json"), "w") as fh:
        json.dump({"multipart": parts}, fh, indent=2)
        fh.write("\n")


def check(lists):
    seen = {}
    for name, parts in lists.items():
        for x, y, z, s, rig in parts:
            key = (x, y, z)
            if key in seen and not (name.endswith("extras") and seen[key].endswith("extras")):
                raise SystemExit("two parts at %s: %s and %s" % (key, seen[key], name))
            seen[key] = name
            if abs(x) > FOOT_X or z < FOOT_Z0 or z > FOOT_Z1 or y < KEEL - 1 or y > CLEAR_UP:
                raise SystemExit("%s: a part outside the footprint at %s" % (name, key))
    for x, z in POSTS:
        if (x, 0, z) not in seen:
            raise SystemExit("a post under nothing at %s, %s" % (x, z))
    for p in (HELM, (STAND[0], STAND[1] - 1, STAND[2])):
        if p not in seen:
            raise SystemExit("nothing at %s" % (p,))
    if (STAND[0], STAND[1], STAND[2]) in seen or (STAND[0], STAND[1] + 1, STAND[2]) in seen:
        raise SystemExit("the helm's stand is blocked")


def write_java(lists):
    states, rows = [], {}
    for name, parts in lists.items():
        r = []
        for x, y, z, s, rig in parts:
            if s not in states:
                states.append(s)
            r.append("%d,%d,%d,%d,%d" % (x, y, z, states.index(s), rig))
        rows[name] = r
    L = ["package dev.awaken.frontier;", "",
         "/**",
         " * The skiff and its mooring block by block (SPEC §67.4). GENERATED by tools/art/skiff_plan.py: change the plan there,",
         " * look at its previews, and run it again. Rows are x, y, z (south-facing, from the berth's middle at deck height), the",
         " * state's index and the rig it moves with in flight (0 the hull, 1 and 2 the wings, 3 the wind-wheel).",
         " */",
         "final class SkiffPlan {",
         "    private SkiffPlan() {}", "",
         "    static final int FOOT_X = %d, FOOT_Z0 = %d, FOOT_Z1 = %d, KEEL = %d, PAD = %d, CLEAR_UP = %d, PAD_R2 = %d;" % (FOOT_X, FOOT_Z0, FOOT_Z1, KEEL, PAD, CLEAR_UP, PAD_R2),
         "    static final int[] HELM = {%d, %d, %d}, STAND = {%d, %d, %d}, HOLD = {%d, %d, %d}, BOARD = {%d, %d, %d}, HUB = {%d, %d, %d};" % (HELM + STAND + HOLD + BOARD + HUB),
         "    static final int WING_Y = %d;" % WING_Y,
         "    static final float WING_ROOT = %sF;" % WING_ROOT,
         "    static final int[][] POSTS = {%s};" % ", ".join("{%d, %d}" % p for p in POSTS),
         "    static final int[] STEPS_X = {%s};" % ", ".join(str(x) for x in STEPS_X),
         "    static final int STEPS_Z = %d;" % STEPS_Z, "",
         "    static final String[] STATES = {"]
    for s in states:
        L.append('            "%s",' % s)
    L.append("    };")
    for name, r in rows.items():
        L.append("")
        L.append("    static final int[] %s = {" % name.upper())
        for i in range(0, len(r), 8):
            L.append("            " + ", ".join(r[i:i + 8]) + ",")
        L.append("    };")
    L.append("}")
    with open(JAVA, "w") as f:
        f.write("\n".join(L) + "\n")


# ---- previews -------------------------------------------------------------------------------------------------------

COLOURS = {
    "skiff_deck": ((176, 132, 82), (38, 54, 78)), "skiff_hull": ((34, 50, 74), (34, 50, 74)), "skiff_hull_stairs": ((34, 50, 74), (34, 50, 74)),
    "skiff_brass": ((214, 168, 62), (190, 144, 48)), "precursor_trim": ((200, 160, 60), (170, 130, 44)), "precursor_glow_panel": ((120, 200, 255), (120, 200, 255)),
    "precursor_plating": ((30, 46, 70), (30, 46, 70)), "skiff_spar": ((120, 86, 50), (104, 74, 42)), "skiff_sail": ((236, 226, 200), (222, 210, 182)),
    "skiff_gunwale": ((210, 164, 60), (112, 80, 46)), "skiff_helm": ((214, 168, 62), (40, 58, 84)), "skiff_lamp": ((150, 215, 255), (200, 150, 60)),
    "spruce_stairs": ((124, 92, 54), (110, 80, 46)), "spruce_planks": ((124, 92, 54), (110, 80, 46)), "stripped_spruce_log": ((120, 92, 56), (112, 84, 50)),
    "spruce_log": ((70, 48, 28), (60, 40, 24)), "spruce_fence": ((124, 92, 54), (110, 80, 46)), "slate_tile_stairs": ((120, 126, 136), (100, 106, 116)),
    "slate_tiles": ((120, 126, 136), (100, 106, 116)), "cask": ((140, 100, 60), (120, 84, 50)), "cargo_crate": ((150, 110, 66), (130, 94, 56)),
    "sack": ((196, 172, 120), (180, 156, 106)), "bench": ((130, 96, 58), (110, 80, 46)), "barrel": ((132, 98, 58), (116, 84, 50)),
    "notice_board": ((150, 116, 70), (120, 90, 54)), "pad": ((132, 134, 138), (112, 114, 118)), "pad_rim": ((108, 110, 114), (92, 94, 98)),
    "ground": ((92, 150, 70), (120, 86, 60)),
}


def boxes(state):
    """A state's shape as boxes in sixteenths, for the previews only."""
    name = state.split("[")[0].split(":")[1]
    props = dict(p.split("=") for p in state.split("[")[1][:-1].split(",")) if "[" in state else {}
    if name.endswith("stairs"):
        top = props["half"] == "top"
        slab = (0, 8, 0, 16, 16, 16) if top else (0, 0, 0, 16, 8, 16)
        y0, y1 = (0, 8) if top else (8, 16)
        step = {"east": (8, y0, 0, 16, y1, 16), "west": (0, y0, 0, 8, y1, 16), "south": (0, y0, 8, 16, y1, 16), "north": (0, y0, 0, 16, y1, 8)}[props["facing"]]
        return [slab, step]
    if name == "skiff_spar" or name == "spruce_fence":
        a = props.get("axis", "y")
        return [{"x": (0, 6, 6, 16, 10, 10), "y": (6, 0, 6, 10, 16, 10), "z": (6, 6, 0, 10, 10, 16)}[a]]
    if name == "skiff_sail":
        return [{"flat": (0, 7, 0, 16, 9, 16), "fore_aft": (7, 0, 0, 9, 16, 16), "athwart": (0, 0, 7, 16, 16, 9)}[props["plane"]]]
    if name == "skiff_gunwale":
        side = {"north": (0, 0, 0, 16, 12, 3), "south": (0, 0, 13, 16, 12, 16), "west": (0, 0, 0, 3, 12, 16), "east": (13, 0, 0, 16, 12, 16)}
        return [side[s] for s in side if props[s] == "true"]
    if name == "skiff_helm":
        return [(6, 0, 6, 10, 9, 10), (3, 9, 5, 13, 12, 11), (5, 10, 4, 11, 16, 5)]
    if name == "skiff_lamp":
        return [(5, 6, 5, 11, 14, 11), (7, 14, 7, 9, 16, 9)] if props["hanging"] == "true" else [(5, 0, 5, 11, 9, 11)]
    if name in ("bench",):
        return [(0, 0, 4, 16, 8, 12)]
    if name == "notice_board":
        return [(0, 0, 6, 16, 16, 10)]
    if name in ("cask", "sack"):
        return [(2, 0, 2, 14, 14, 14)]
    return [(0, 0, 0, 16, 16, 16)]


def scene(parts, ground=True):
    """Boxes in block units with a colour pair: (x0, y0, z0, x1, y1, z1, top, side)."""
    out = []
    for x, y, z, s, rig in parts:
        name = s.split("[")[0].split(":")[1]
        top, side = COLOURS.get(name, ((200, 60, 200), (160, 40, 160)))
        if name == "skiff_lamp" and "amber" in s:
            top = (255, 200, 110)
        if name == "skiff_sail" and "band=true" in s:
            top, side = (40, 60, 92), (36, 54, 84)
        for b in boxes(s):
            out.append((x - 0.5 + b[0] / 16, y + b[1] / 16, z - 0.5 + b[2] / 16, x - 0.5 + b[3] / 16, y + b[4] / 16, z - 0.5 + b[5] / 16, top, side))
    if ground:
        for x in range(-12, 13):
            for z in range(-14, 13):
                if x * x + z * z <= PAD_R2:
                    rim = x * x + z * z > 30
                    if not any(p[0] == x and p[1] == PAD and p[2] == z for p in parts):
                        c = COLOURS["pad_rim" if rim else "pad"]
                        out.append((x - 0.5, PAD, z - 0.5, x + 0.5, PAD + 1, z + 0.5, c[0], c[1]))
                else:
                    c = COLOURS["ground"]
                    out.append((x - 0.5, PAD - 1, z - 0.5, x + 0.5, PAD, z + 0.5, c[0], c[1]))
        for x, z in POSTS:
            out.append((x - 0.3, PAD, z - 0.3, x + 0.3, 0, z + 0.3, COLOURS["spruce_log"][0], COLOURS["spruce_log"][1]))
        for k in range(4):
            for x in STEPS_X:
                out.append((x - 0.5, -k - 3, STEPS_Z - k - 0.5, x + 0.5, -k + 0.5, STEPS_Z - k + 0.5, COLOURS["spruce_planks"][0], COLOURS["spruce_planks"][1]))
    return out


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c)


def faces(b):
    x0, y0, z0, x1, y1, z1, top, side = b
    return [
        ([(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)], (0, 1, 0), shade(top, 1.0)),
        ([(x0, y0, z0), (x1, y0, z0), (x1, y0, z1), (x0, y0, z1)], (0, -1, 0), shade(side, 0.5)),
        ([(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0)], (0, 0, -1), shade(side, 0.8)),
        ([(x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)], (0, 0, 1), shade(side, 0.8)),
        ([(x0, y0, z0), (x0, y0, z1), (x0, y1, z1), (x0, y1, z0)], (-1, 0, 0), shade(side, 0.62)),
        ([(x1, y0, z0), (x1, y0, z1), (x1, y1, z1), (x1, y1, z0)], (1, 0, 0), shade(side, 0.62)),
    ]


def render(sc, eye, target, fov, size, path):
    """A perspective picture of the boxes with a depth buffer, flat-shaded like the game shades faces."""
    import numpy as np
    from PIL import Image
    W, H = size
    f = np.array(target, float) - np.array(eye, float)
    f /= np.linalg.norm(f)
    r = np.cross(f, [0, 1, 0])
    r /= np.linalg.norm(r)
    u = np.cross(r, f)
    k = (H / 2) / math.tan(math.radians(fov) / 2)
    img = np.zeros((H, W, 3), np.uint8)
    img[:] = (150, 190, 240)
    img[H // 2:] = (170, 205, 245)
    depth = np.full((H, W), 1e9)
    e = np.array(eye, float)
    for b in sc:
        for quad, n, col in faces(b):
            c = np.mean(np.array(quad), axis=0)
            if np.dot(np.array(n, float), c - e) >= 0:
                continue
            pts = []
            ok = True
            for p in quad:
                d = np.array(p, float) - e
                zc = np.dot(d, f)
                if zc < 0.05:
                    ok = False
                    break
                pts.append((W / 2 + np.dot(d, r) / zc * k, H / 2 - np.dot(d, u) / zc * k, zc))
            if not ok:
                continue
            for tri in ((pts[0], pts[1], pts[2]), (pts[0], pts[2], pts[3])):
                xs = [p[0] for p in tri]
                ys = [p[1] for p in tri]
                x0, x1 = max(0, int(min(xs))), min(W - 1, int(max(xs)) + 1)
                y0, y1 = max(0, int(min(ys))), min(H - 1, int(max(ys)) + 1)
                if x0 > x1 or y0 > y1:
                    continue
                gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
                (ax, ay, az), (bx, by, bz), (cx, cy, cz) = tri
                den = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
                if abs(den) < 1e-9:
                    continue
                w0 = ((by - cy) * (gx - cx) + (cx - bx) * (gy - cy)) / den
                w1 = ((cy - ay) * (gx - cx) + (ax - cx) * (gy - cy)) / den
                w2 = 1 - w0 - w1
                inside = (w0 >= 0) & (w1 >= 0) & (w2 >= 0)
                zz = 1 / (w0 / az + w1 / bz + w2 / cz + 1e-12)
                sub = depth[y0:y1 + 1, x0:x1 + 1]
                hit = inside & (zz < sub)
                sub[hit] = zz[hit]
                img[y0:y1 + 1, x0:x1 + 1][hit] = col
    Image.fromarray(img).save(path)


def previews(out_dir, lists):
    os.makedirs(out_dir, exist_ok=True)
    whole = scene(lists["skiff"] + lists["jetty"] + lists["landing_extras"])
    alone = scene(lists["skiff"], ground=False)
    size = (1100, 620)
    render(whole, (12, 5, 15), (0, 2, 0), 60, size, os.path.join(out_dir, "plan_bow_quarter.png"))
    render(whole, (-14, 7, -16), (0, 2, 0), 60, size, os.path.join(out_dir, "plan_stern_quarter.png"))
    render(whole, (5, 30, -6), (0, 0, 0), 60, size, os.path.join(out_dir, "plan_above.png"))
    render(whole, (14, -2, 4), (0, 1.5, 0), 60, size, os.path.join(out_dir, "plan_low_side.png"))
    render(whole, (-4, 2.62, -8.5), (0, 2.4, 2), 70, size, os.path.join(out_dir, "plan_from_pier.png"))
    eye = (STAND[0], STAND[1] + 1.62, STAND[2])
    render(whole, eye, (0, eye[1] - 1.8, eye[2] + 10), 70, size, os.path.join(out_dir, "plan_helm.png"))
    render(alone, (11, 3, 12), (0, 2, -1), 50, size, os.path.join(out_dir, "plan_skiff_alone.png"))
    render(alone, (0.01, 26, 0), (0, 0, 0.01), 50, size, os.path.join(out_dir, "plan_skiff_top.png"))


def main():
    lists = {"skiff": skiff(), "jetty": jetty(), "landing_extras": landing_extras(), "ridge_extras": ridge_extras()}
    check(lists)
    lash(lists)
    write_java(lists)
    write_sail_models()
    print("skiff %d blocks, mooring %d; SkiffPlan.java written" % (len(lists["skiff"]), len(lists["jetty"])))
    if len(sys.argv) > 1:
        previews(sys.argv[1], lists)
        print("previews in", sys.argv[1])


if __name__ == "__main__":
    main()
