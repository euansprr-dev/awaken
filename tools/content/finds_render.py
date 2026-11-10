#!/usr/bin/env python3
"""Pictures of the places without the game (SPEC 59.7).

A tour on GitHub takes a quarter of an hour and shows five places; this shows all thirty in a minute, from two sides, cut
open and from above, so a build can be looked at before a tour is spent on it. It is a drawing of the blocks, not the game:
no air, no light, no real land round it. A tour is still the last word.

  mkdir -p run/finds-dump && tools/gradle.sh 900 runGameTestServer -Ptests=finds_every_scene   (writes run/finds-dump/*.txt)
  python3 tools/content/finds_render.py run/finds-dump <out dir> [place ids...]

It reads the game's own textures from the reference folder on this machine and copies none of them into the repository;
the pictures stay out of it too.
"""
import sys, re, json, pathlib, math, functools, multiprocessing
from PIL import Image, ImageDraw

ROOT = pathlib.Path(__file__).resolve().parents[2]
TEX = pathlib.Path("/Volumes/MEDIA/Awaken/.ref/assets/assets/minecraft/textures/block")
MODELS = ROOT / "src/main/resources/assets/awaken/models/block"
STATES = ROOT / "src/main/resources/assets/awaken/blockstates"
OWN = ROOT / "src/main/resources/assets/awaken/textures/block"
NAMES = {p.stem for p in TEX.glob("*.png")} | {p.stem for p in OWN.glob("olden_*.png")}

# ---- what a block looks like ---------------------------------------------------------------------------------------------

NOTHING = {"air", "cave_air", "void_air", "light", "barrier", "structure_void", "moving_piston"}
ALIAS = {
    "snow_block": "snow", "magma_block": "magma", "lava": "lava_still", "petrified_oak": "oak_planks", "quartz": "quartz_block_side",
    "quartz_block": "quartz_block_side", "smooth_quartz": "quartz_block_bottom", "smooth_sandstone": "sandstone_top",
    "smooth_red_sandstone": "red_sandstone_top", "campfire": "campfire_log_lit", "soul_campfire": "soul_campfire_log_lit",
    "wall_torch": "torch", "soul_wall_torch": "soul_torch", "chain": "iron_chain", "bamboo": "bamboo_stalk", "fire": "fire_0",
    "soul_fire": "soul_fire_0", "sunflower": "sunflower_front", "lilac": "lilac_top", "rose_bush": "rose_bush_top", "peony": "peony_top",
    "tall_grass": "tall_grass_top", "large_fern": "large_fern_top", "pointed_dripstone": "pointed_dripstone_down_tip",
    "big_dripleaf": "big_dripleaf_top", "small_dripleaf": "small_dripleaf_top", "lectern": "lectern_sides", "bell": "bell_side",
    "dried_kelp_block": "dried_kelp_side", "farmland": "farmland_moist", "decorated_pot": "terracotta", "moss_carpet": "moss_block",
    "pale_moss_carpet": "pale_moss_block", "tall_seagrass": "tall_seagrass_top", "kelp_plant": "kelp_plant", "chest": "oak_planks",
    "trapped_chest": "oak_planks", "skeleton_skull": "bone_block_side", "wither_skeleton_skull": "coal_block", "brewing_stand": "brewing_stand",
    "infested_stone": "stone", "infested_cobblestone": "cobblestone", "smooth_stone_slab": "smooth_stone", "sculk_vein": "sculk_vein",
    "mangrove_roots": "mangrove_roots_side", "muddy_mangrove_roots": "muddy_mangrove_roots_side", "scaffolding": "scaffolding_side",
    "cave_vines_plant": "cave_vines_plant", "weeping_vines_plant": "weeping_vines_plant", "twisting_vines_plant": "twisting_vines_plant",
    "sweet_berry_bush": "sweet_berry_bush_stage3", "wheat": "wheat_stage7", "carrots": "carrots_stage3", "potatoes": "potatoes_stage3",
    "beetroots": "beetroots_stage3", "nether_wart": "nether_wart_stage2", "cocoa": "cocoa_stage2", "turtle_egg": "turtle_egg",
    "redstone_wire": "redstone_block", "tripwire": "tripwire", "cake": "cake_top", "respawn_anchor": "respawn_anchor_side0",
    "bamboo_fence": "bamboo_planks", "bamboo_fence_gate": "bamboo_planks", "dirt_path": "dirt_path_side", "end_portal_frame": "end_portal_frame_side",
}
SHAPED = ("_stairs", "_slab", "_wall", "_fence_gate", "_fence", "_pressure_plate", "_button", "_carpet", "_pane", "_trapdoor", "_door",
          "_wall_sign", "_hanging_sign", "_wall_hanging_sign", "_sign", "_wall_banner", "_banner", "_bed", "_candle", "_shelf")
CROSS = re.compile(r"(^|_)(sapling|tulip|orchid|allium|bluet|daisy|poppy|dandelion|cornflower|lily_of_the_valley|torchflower|eyeblossom|"
                   r"mushroom$|fungus|roots$|sprouts|grass$|fern$|bush$|cobweb|sugar_cane|kelp|seagrass|vines|vines_plant|wart$|"
                   r"wheat|carrots|potatoes|beetroots|sunflower|lilac|peony|pitcher_plant|pointed_dripstone|amethyst_bud|amethyst_cluster|"
                   r"coral$|coral_fan|fire$|spore_blossom|dripleaf|petals|wildflowers|hanging_roots|propagule|cactus_flower|firefly_bush|"
                   r"short_dry_grass|tall_dry_grass|bush)")
FLAT = re.compile(r"(_carpet$|^snow$|_pressure_plate$|^lily_pad$|rail$|^leaf_litter$|^glow_lichen$|^sculk_vein$|^vine$|^frogspawn$|petals$|^resin_clump$)")
POST = re.compile(r"(_fence$|_wall$|^iron_bars$|_bars$|_pane$|^glass_pane$|chain$|^end_rod$|lightning_rod$|^bamboo$)")
LIT = re.compile(r"(lantern|torch|glowstone|shroomlight|froglight|sea_lantern|campfire|^fire|soul_fire|lava|magma|candle|glow_lichen|"
                 r"amethyst_cluster|jack_o_lantern|redstone_lamp|copper_bulb|end_rod|beacon|conduit|cave_vines|sea_pickle|crying_obsidian|^light$|olden_lamp_lit|olden_seal|olden_lichen)")
GRASSY = re.compile(r"(^short_grass$|^tall_grass$|^fern$|^large_fern$|^vine$|^lily_pad$|^sugar_cane$|^bush$|^leaf_litter$)")
LEAF_TINT = {"birch_leaves": (0.50, 0.65, 0.33), "spruce_leaves": (0.38, 0.60, 0.38)}
FOLIAGE, GRASS, WATER = (0.33, 0.62, 0.20), (0.49, 0.76, 0.34), (52, 102, 205)
DIR = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}


def plain(name):
    n = name.split(":", 1)[-1]
    return n[6:] if n.startswith("waxed_") else n


@functools.lru_cache(None)
def image(tex):
    """A texture's first frame, RGBA 16x16, or None."""
    if tex not in NAMES:
        return None
    im = Image.open((OWN if tex.startswith("olden_") else TEX) / (tex + ".png")).convert("RGBA")
    return im.crop((0, 0, 16, 16)) if im.size != (16, 16) else im


def first(*cands):
    for c in cands:
        if c in NAMES:
            return c
    return None


@functools.lru_cache(None)
def textures(n):
    """(side, top) texture names of a vanilla block, from its id alone."""
    if n in ALIAS:
        a = ALIAS[n]
        return a, first(a.replace("_side", "_top"), a) if a.endswith("_side") else a
    m = n
    for suf in SHAPED:
        if n.endswith(suf) and n != "smooth_stone_slab":
            m = n[:-len(suf)]
            break
    if m != n:
        if m.endswith("_wool") or m in ("moss", "pale_moss"):
            pass
        colours = ("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black")
        if m in colours:
            m = m + ("_stained_glass" if n.endswith("_pane") else "_candle" if n.endswith("_candle") else "_wool")
        if n.endswith("_stained_glass_pane"):
            m = n[:-5]
        if n == "glass_pane":
            m = "glass"
        if n.endswith(("_trapdoor", "_door")):
            t = first(n, n + "_top")
            if t:
                return t, t
        if m in ALIAS:
            a = ALIAS[m]
            return a, first(a.replace("_side", "_top"), a)
    if m.endswith("_wood") or m.endswith("_hyphae"):
        m = m.replace("_wood", "_log").replace("_hyphae", "_stem")
    side = first(m + "_side", m, m + "s", m + "_planks", m + "_block", m.replace("brick", "bricks"), m.replace("tile", "tiles"), m + "_front",
                 m + "_still", m + "_0", m + "_stage0", m + "_bottom", m + "_top", m + "_block_side", m + "_lit")
    top = first(m + "_top", m + "_end", m + "s_top", m + "_block_top", side or "")
    if side is None:
        near = sorted(t for t in NAMES if t.startswith(m))
        side = near[0] if near else None
        top = top or side
    return side, top or side


def tint_of(n, tex):
    if n == "grass_block" and tex == "grass_block_top":
        return GRASS
    if n.endswith("_leaves") and n not in ("azalea_leaves", "flowering_azalea_leaves", "cherry_leaves", "pale_oak_leaves"):
        return LEAF_TINT.get(n, FOLIAGE)
    if GRASSY.search(n):
        return GRASS
    return None


@functools.lru_cache(None)
def mean(tex, tint=None):
    im = image(tex)
    if im is None:
        return (255, 0, 255)
    r = g = b = w = 0
    for pr, pg, pb, pa in im.getdata():
        if pa > 24:
            r += pr * pa; g += pg * pa; b += pb * pa; w += pa
    if w == 0:
        return (120, 120, 120)
    c = (r / w, g / w, b / w)
    if tint:
        c = tuple(c[i] * tint[i] for i in range(3))
    return tuple(int(v) for v in c)


UNKNOWN = set()


def opaque(b):
    """Whether a block hides what is behind it: a full cube that isn't glass, water or a plant."""
    if b is None:
        return False
    return kind_of(plain(b[0]), b[0]) == "cube" and not re.search(r"glass|ice$|^water$", plain(b[0]))


@functools.lru_cache(None)
def kind_of(n, full):
    if n in NOTHING:
        return "none"
    if full.startswith("awaken:") and not n.startswith("olden_"):
        return "model"                                        # a prop: drawn from its own model. Olden stone is plain masonry.
    if n == "water":
        return "water"
    if n.endswith("_slab"):
        return "slab"
    if n.endswith("_stairs"):
        return "stairs"
    if re.search(r"(wall_torch|torch)$", n):
        return "torch"
    if n.endswith(("_wall_sign", "_wall_banner", "_wall_hanging_sign")):
        return "flatwall"
    if POST.search(n):
        return "post"
    if n.endswith("_fence_gate"):
        return "gate"
    if n.endswith("_trapdoor"):
        return "trapdoor"
    if n.endswith("_door"):
        return "door"
    if n == "ladder":
        return "ladder"
    if FLAT.search(n):
        return "flat"
    if n.endswith("lantern") and n not in ("sea_lantern", "jack_o_lantern"):
        return "lantern"
    if n.endswith("candle") or n == "sea_pickle":
        return "candle"
    if n.startswith("potted_") or n == "flower_pot":
        return "pot"
    if n.endswith(("_sign", "_banner", "_hanging_sign")):
        return "sign"
    if n.endswith(("campfire",)):
        return "campfire"
    if n.endswith(("_bed",)):
        return "bed"
    if n.endswith(("skull", "_head")):
        return "head"
    if n in ("chest", "trapped_chest", "ender_chest", "cauldron", "composter", "hopper", "anvil", "grindstone", "lectern", "bell", "brewing_stand",
             "enchanting_table", "decorated_pot", "stonecutter", "daylight_detector", "cake", "turtle_egg", "sniffer_egg", "dragon_egg", "conduit",
             "lodestone_x", "heavy_core", "water_cauldron", "lava_cauldron", "powder_snow_cauldron"):
        return "small"
    if CROSS.search(n):
        return "cross"
    return "cube"


def boxes_of(kind, n, p, arms):
    """A block's shape as boxes (x0, y0, z0, x1, y1, z1) in the unit cell."""
    if kind == "slab":
        t = p.get("type", "bottom")
        return [(0, 0.5, 0, 1, 1, 1)] if t == "top" else [(0, 0, 0, 1, 1, 1)] if t == "double" else [(0, 0, 0, 1, 0.5, 1)]
    if kind == "stairs":
        dx, dz = DIR.get(p.get("facing", "north"), (0, -1))
        up = p.get("half", "bottom") == "top"
        base = (0, 0.5, 0, 1, 1, 1) if up else (0, 0, 0, 1, 0.5, 1)
        y0, y1 = (0, 0.5) if up else (0.5, 1)
        step = (0.5 if dx > 0 else 0, y0, 0.5 if dz > 0 else 0, 0.5 if dx < 0 else 1, y1, 0.5 if dz < 0 else 1)
        return [base, step]
    if kind == "post":
        w = 0.25 if n.endswith("_wall") else 0.07 if n.endswith(("_pane", "bars")) or n == "glass_pane" else 0.12 if "chain" in n or "rod" in n else 0.15
        h = 1.0 if not n.endswith("_wall") or arms & 16 else 0.9
        if "chain" in n and p.get("axis", "y") != "y":
            return [(0, 0.4, 0.4, 1, 0.6, 0.6)] if p.get("axis") == "x" else [(0.4, 0.4, 0, 0.6, 0.6, 1)]
        out = [(0.5 - w, 0, 0.5 - w, 0.5 + w, h, 0.5 + w)]
        thin = min(w, 0.1)
        top = 0.85 if n.endswith("_wall") else 0.95
        lo = 0 if n.endswith(("_wall", "_pane", "bars")) or n == "glass_pane" else 0.4
        if arms & 1: out.append((0.5 - thin, lo, 0, 0.5 + thin, top, 0.5))       # north
        if arms & 2: out.append((0.5, lo, 0.5 - thin, 1, top, 0.5 + thin))       # east
        if arms & 4: out.append((0.5 - thin, lo, 0.5, 0.5 + thin, top, 1))       # south
        if arms & 8: out.append((0, lo, 0.5 - thin, 0.5, top, 0.5 + thin))       # west
        return out
    if kind == "gate":
        return [(0, 0.3, 0.42, 1, 1, 0.58)] if p.get("facing", "north") in ("north", "south") else [(0.42, 0.3, 0, 0.58, 1, 1)]
    if kind == "trapdoor":
        if p.get("open") == "true":
            dx, dz = DIR.get(p.get("facing", "north"), (0, -1))
            return [(0.82 if dx < 0 else 0, 0, 0.82 if dz < 0 else 0, 0.18 if dx > 0 else 1, 1, 0.18 if dz > 0 else 1)]
        return [(0, 0.82, 0, 1, 1, 1)] if p.get("half") == "top" else [(0, 0, 0, 1, 0.18, 1)]
    if kind in ("door", "ladder", "flatwall"):
        dx, dz = DIR.get(p.get("facing", "north"), (0, -1))
        if kind == "door":
            dx, dz = -dx, -dz
        t = 0.18 if kind == "door" else 0.08
        # against the side the block faces away from
        return [(1 - t if dx < 0 else 0, 0, 1 - t if dz < 0 else 0, t if dx > 0 else 1, 1, t if dz > 0 else 1)]
    if kind == "flat":
        h = 0.12 * int(p.get("layers", 1)) if n == "snow" else 0.07
        return [(0, 0, 0, 1, min(1, h), 1)]
    if kind == "lantern":
        return [(0.3, 0.45, 0.3, 0.7, 1, 0.7)] if p.get("hanging") == "true" else [(0.3, 0, 0.3, 0.7, 0.55, 0.7)]
    if kind == "torch":
        return [(0.42, 0, 0.42, 0.58, 0.65, 0.58)]
    if kind == "candle":
        return [(0.4, 0, 0.4, 0.6, 0.4, 0.6)]
    if kind == "pot":
        return [(0.32, 0, 0.32, 0.68, 0.38, 0.68)]
    if kind == "sign":
        return [(0.45, 0, 0.45, 0.55, 0.5, 0.55), (0.05, 0.5, 0.44, 0.95, 1, 0.56)]
    if kind == "campfire":
        return [(0.05, 0, 0.05, 0.95, 0.4, 0.95)]
    if kind == "bed":
        return [(0, 0, 0, 1, 0.56, 1)]
    if kind == "head":
        return [(0.25, 0, 0.25, 0.75, 0.5, 0.75)]
    if kind == "small":
        if n in ("enchanting_table", "stonecutter", "daylight_detector"):
            return [(0, 0, 0, 1, 0.75 if n == "enchanting_table" else 0.56, 1)]
        if n == "bell":
            return [(0.25, 0.25, 0.25, 0.75, 0.8, 0.75), (0.4, 0.8, 0.4, 0.6, 1, 0.6)]
        if n == "lectern":
            return [(0, 0, 0, 1, 0.12, 1), (0.25, 0.12, 0.25, 0.75, 0.85, 0.75), (0, 0.8, 0, 1, 1, 1)]
        if n == "anvil":
            return [(0.12, 0, 0.12, 0.88, 0.25, 0.88), (0.3, 0.25, 0.3, 0.7, 0.6, 0.7), (0.2, 0.6, 0, 0.8, 1, 1)]
        return [(0.06, 0, 0.06, 0.94, 0.88, 0.94)]
    return [(0, 0, 0, 1, 1, 1)]


@functools.lru_cache(None)
def model_boxes(full, form, facing):
    """An Awaken block's model as boxes with colours: [(box, top colour, side colour)], turned as its blockstate turns it."""
    name = full.split(":", 1)[1]
    rot = 0
    model = None
    st = STATES / (name + ".json")
    if st.exists():
        try:
            variants = json.loads(st.read_text()).get("variants", {})
            best, score = None, -1
            for key, v in variants.items():
                kv = dict(x.split("=") for x in key.split(",") if "=" in x)
                s = (kv.get("form") == form) * 2 + (kv.get("facing") == facing) + (not kv)
                if (("form" not in kv) or kv["form"] == form) and (("facing" not in kv) or kv["facing"] == facing) and s > score:
                    best, score = v, s
            if isinstance(best, list):
                best = best[0]
            if best:
                model = best["model"].split("/")[-1]
                rot = int(best.get("y", 0)) // 90 % 4
        except (ValueError, KeyError):
            pass
    out = []
    seen = set()
    while model and model not in seen:
        seen.add(model)
        f = MODELS / (model + ".json")
        if not f.exists():
            break
        m = json.loads(f.read_text())
        tex = m.get("textures", {})

        def colour(ref):
            while ref and ref.startswith("#"):
                ref = tex.get(ref[1:])
            return mean(ref.split("/")[-1]) if ref else (150, 150, 150)
        for e in m.get("elements", []):
            a, b = e["from"], e["to"]
            box = [a[0] / 16, a[1] / 16, a[2] / 16, b[0] / 16, b[1] / 16, b[2] / 16]
            for _ in range(rot):  # a quarter turn clockwise seen from above: (x, z) -> (1 - z, x)
                box = [1 - box[5], box[1], box[0], 1 - box[2], box[4], box[3]]
            faces = e.get("faces", {})
            side = next((faces[k]["texture"] for k in ("south", "east", "north", "west") if k in faces), None)
            top = faces.get("up", {}).get("texture", side)
            out.append((tuple(box), colour(top), colour(side or top)))
        if out:
            break
        model = m.get("parent", "").split("/")[-1] if "awaken:" in m.get("parent", "") else None
    return out or [((0.2, 0, 0.2, 0.8, 0.9, 0.8), (200, 60, 200), (160, 40, 160))]


# ---- drawing -------------------------------------------------------------------------------------------------------------

class Pen:
    """Sprites of blocks for one scale: a cube's top rhombus is 2a wide and a high, its sides h high."""

    def __init__(self, a):
        self.a, self.h = a, a + 1 + a // 6
        self.w, self.t = 4 * a, 2 * a + 3 * self.h + 2
        self.oy = a // 2 + self.h
        self.cache = {}

    def pt(self, u, v, w):
        return (2 * self.a + (u - w) * self.a, self.oy + (u + w) * self.a / 2 + (1 - v) * self.h)

    def box(self, d, b, top, side, shade=1.0):
        x0, y0, z0, x1, y1, z1 = b
        s = lambda c, f: tuple(max(0, min(255, int(v * f * shade))) for v in c[:3]) + (c[3] if len(c) > 3 else 255,)
        d.polygon([self.pt(x0, y1, z1), self.pt(x1, y1, z1), self.pt(x1, y0, z1), self.pt(x0, y0, z1)], fill=s(side, 0.62))
        d.polygon([self.pt(x1, y1, z0), self.pt(x1, y1, z1), self.pt(x1, y0, z1), self.pt(x1, y0, z0)], fill=s(side, 0.80))
        d.polygon([self.pt(x0, y1, z0), self.pt(x1, y1, z0), self.pt(x1, y1, z1), self.pt(x0, y1, z1)], fill=s(top, 1.0))

    def cube(self, side, top, tint_side, tint_top, alpha=255, backing=None):
        """A full cube with its textures laid on the three faces that show."""
        a, h, oy = self.a, self.h, self.oy
        im = Image.new("RGBA", (self.w, self.t), (0, 0, 0, 0))
        faces = [
            (side, tint_side, 0.62, (16 / a, 0, -16, -8 / h, 16 / h, -(8 * 2 * a + 16 * (oy + a / 2) - 8 * a) / h + 0),  # +z, filled in below
             [self.pt(0, 1, 1), self.pt(1, 1, 1), self.pt(1, 0, 1), self.pt(0, 0, 1)]),
            (side, tint_side, 0.80, None, [self.pt(1, 1, 0), self.pt(1, 1, 1), self.pt(1, 0, 1), self.pt(1, 0, 0)]),
            (top, tint_top, 1.0, None, [self.pt(0, 1, 0), self.pt(1, 1, 0), self.pt(1, 1, 1), self.pt(0, 1, 1)]),
        ]
        for i, (tex, tint, shade, _, poly) in enumerate(faces):
            src = image(tex)
            mask = Image.new("L", im.size, 0)
            ImageDraw.Draw(mask).polygon(poly, fill=255)
            if src is None:
                layer = Image.new("RGBA", im.size, tuple(int(v * shade) for v in mean(tex or "", tint)) + (alpha,))
            else:
                if backing is not None:  # leaves: what shows through the gaps is more of the tree
                    bg = Image.new("RGBA", (16, 16), backing + (255,))
                    bg.alpha_composite(src)
                    src = bg
                # the affine map from picture to texture, from three corners of the face
                (x0, y0), (x1, y1), (x3, y3) = poly[0], poly[1], poly[3]
                ux, uy, vx, vy = x1 - x0, y1 - y0, x3 - x0, y3 - y0
                det = ux * vy - uy * vx
                aa, bb = 16 * vy / det, -16 * vx / det
                dd, ee = -16 * uy / det, 16 * ux / det
                layer = src.transform(im.size, Image.AFFINE, (aa, bb, -(aa * x0 + bb * y0), dd, ee, -(dd * x0 + ee * y0)), resample=Image.BILINEAR)
                f = [shade * (tint[k] if tint else 1) for k in range(3)]
                r, g, b, al = layer.split()
                layer = Image.merge("RGBA", (r.point(lambda v, m=f[0]: min(255, int(v * m))), g.point(lambda v, m=f[1]: min(255, int(v * m))),
                                             b.point(lambda v, m=f[2]: min(255, int(v * m))), al.point(lambda v: v * alpha // 255)))
                mask = Image.composite(layer.getchannel("A"), Image.new("L", im.size, 0), mask)
            im.paste(layer, (0, 0), mask)
        return im

    def sprite(self, b, arms, view, dark):
        key = (b, arms, view, dark)
        s = self.cache.get(key)
        if s is not None:
            return s
        full, props = b
        p = dict(props)
        n = plain(full)
        kind = kind_of(n, full)
        if n == "olden_lamp" and p.get("lit") == "true":
            n = "olden_lamp_lit"
        im = None
        if kind == "cube":
            side, top = textures(n)
            if side is None:
                UNKNOWN.add(n)
            leaf = n.endswith("_leaves")
            glass = "glass" in n or n.endswith("ice")
            ts, tt = tint_of(n, side or ""), tint_of(n, top or "")
            im = self.cube(side, top, ts, tt, alpha=150 if glass else 255,
                           backing=tuple(int(v * 0.55) for v in mean(side, ts)) if leaf and side else None)
        elif kind == "water":
            im = Image.new("RGBA", (self.w, self.t), (0, 0, 0, 0))
            d = ImageDraw.Draw(im)
            lv = 0.88
            d.polygon([self.pt(0, lv, 0), self.pt(1, lv, 0), self.pt(1, lv, 1), self.pt(0, lv, 1)], fill=WATER + (175,))
            if arms & 32:  # nothing in front: the water's own sides show
                d.polygon([self.pt(0, lv, 1), self.pt(1, lv, 1), self.pt(1, 0, 1), self.pt(0, 0, 1)], fill=tuple(int(v * 0.6) for v in WATER) + (175,))
                d.polygon([self.pt(1, lv, 0), self.pt(1, lv, 1), self.pt(1, 0, 1), self.pt(1, 0, 0)], fill=tuple(int(v * 0.8) for v in WATER) + (175,))
        elif kind == "cross":
            side, _ = textures(n)
            src = image(side) if side else None
            im = Image.new("RGBA", (self.w, self.t), (0, 0, 0, 0))
            if src is None:
                UNKNOWN.add(n)
            else:
                tint = tint_of(n, side)
                if tint:
                    r, g, bl, al = src.split()
                    src = Image.merge("RGBA", (r.point(lambda v: int(v * tint[0])), g.point(lambda v: int(v * tint[1])), bl.point(lambda v: int(v * tint[2])), al))
                size = (int(self.a * 1.5), int(self.h * 1.25))
                sp = src.resize(size, Image.NEAREST)
                cx, cy = self.pt(0.5, 0, 0.5)
                im.alpha_composite(sp, (int(cx - size[0] / 2), int(cy - size[1])))
        elif kind == "model":
            im = Image.new("RGBA", (self.w, self.t), (0, 0, 0, 0))
            d = ImageDraw.Draw(im)
            bs = []
            for box, top, side in model_boxes(full, p.get("form", "0"), p.get("facing")):
                bs.append((turn_box(box, view), top, side))
            for box, top, side in sorted(bs, key=lambda e: (e[0][0] + e[0][2] + e[0][1])):
                self.box(d, clampbox(box), top, side)
        else:
            side, top = textures(n if kind not in ("pot",) else "flower_pot")
            if side is None:
                UNKNOWN.add(n)
            ts, tt = tint_of(n, side or ""), tint_of(n, top or "")
            cs, ct = mean(side or "", ts), mean(top or side or "", tt)
            if LIT.search(n) and kind in ("lantern", "torch", "candle", "campfire"):
                cs = ct = (255, 205, 110) if "soul" not in n else (110, 220, 235)
            im = Image.new("RGBA", (self.w, self.t), (0, 0, 0, 0))
            d = ImageDraw.Draw(im)
            bs = [turn_box(bx, view) for bx in boxes_of(kind, n, p, arms)]
            for bx in sorted(bs, key=lambda e: e[0] + e[2] + e[1]):
                self.box(d, bx, ct, cs)
            if kind == "pot" and n.startswith("potted_"):
                side2, _ = textures(n[7:])
                src = image(side2) if side2 else None
                if src is not None:
                    size = (int(self.a * 1.1), int(self.h * 0.9))
                    cx, cy = self.pt(0.5, 0.38, 0.5)
                    im.alpha_composite(src.resize(size, Image.NEAREST), (int(cx - size[0] / 2), int(cy - size[1])))
        if dark:
            r, g, bl, al = im.split()
            im = Image.merge("RGBA", (r.point(lambda v: int(v * 0.66)), g.point(lambda v: int(v * 0.66)), bl.point(lambda v: int(v * 0.70)), al))
        box = im.getbbox()
        s = (im.crop(box), box[0], box[1]) if box else None
        self.cache[key] = s if s else False
        return self.cache[key]


def clampbox(b):
    return (max(-0.5, b[0]), max(-1, b[1]), max(-0.5, b[2]), min(1.5, b[3]), min(2, b[4]), min(1.5, b[5]))


def turn(x, z, view):
    for _ in range(view):
        x, z = -z, x
    return x, z


def turn_box(b, view):
    x0, y0, z0, x1, y1, z1 = b
    for _ in range(view):  # the cell's own quarter turn, to match turn()
        x0, z0, x1, z1 = 1 - z1, x0, 1 - z0, x1
    return (x0, y0, z0, x1, y1, z1)


def turn_props(b, view):
    """A block's facing and axis, turned with the view, so stairs and doors still point the right way."""
    if view == 0:
        return b
    order = ["north", "east", "south", "west"]
    full, props = b
    out = []
    for k, v in props:
        if k == "facing" and v in order:
            v = order[(order.index(v) + view) % 4]
        elif k == "axis" and v in ("x", "z") and view % 2:
            v = "z" if v == "x" else "x"
        out.append((k, v))
    return (full, tuple(out))


# ---- a place ---------------------------------------------------------------------------------------------------------------

KEEP = ("facing", "half", "type", "axis", "open", "hanging", "layers", "form", "lit", "berries")


def parse(path):
    d = dict(ground={}, blocks={}, cams=[], parts={}, spawns=[], lands=[])
    for line in path.read_text().splitlines():
        t = line.split(" ")
        if t[0] == "K":
            d["id"], d["n"], d["reach"], d["name"] = t[1], int(t[2]), int(t[3]), " ".join(t[4:])
        elif t[0] == "L":
            d["lands"] = t[1:]
        elif t[0] == "W":
            d["wide"] = int(t[1])
        elif t[0] == "G":
            d["ground"][(int(t[1]), int(t[2]))] = int(t[3])
        elif t[0] == "B":
            m = re.match(r"\{?([^}]+)\}(?:\[(.*)\])?", " ".join(t[4:]))
            props = tuple(sorted((k, v) for k, v in (kv.split("=") for kv in (m.group(2) or "").split(",") if "=" in kv) if k in KEEP))
            d["blocks"][(int(t[1]), int(t[2]), int(t[3]))] = (m.group(1), props)
        elif t[0] == "C":
            d["cams"].append(tuple(int(v) for v in t[1:6]))
        elif t[0] == "P":
            d["parts"].setdefault(t[1], []).append((int(t[2]), int(t[3]), int(t[4])))
        elif t[0] == "S":
            d["spawns"].append((t[1], int(t[2]), int(t[3]), int(t[4])))
    return d


SKIN = {"DESERT": ("sand", "sandstone"), "BADLANDS": ("red_sand", "terracotta"), "SNOW": ("snow_block", "dirt"), "MUSHROOM": ("mycelium", "dirt"),
        "SWAMP": ("grass_block", "mud"), "MOUNTAIN": ("grass_block", "stone")}


def world(d, margin=5):
    """The place on a slab of its land: every column filled to its shaped ground (0 where the place didn't shape it).
    The slab is as wide as what was built, not as wide as the place's reach: empty ground round it tells nothing."""
    built = [p for p, b in d["blocks"].items() if plain(b[0]) not in NOTHING and (p[1] >= 2 or p[1] <= -2) and kind_of(plain(b[0]), b[0]) != "cross"]
    R = min(d["reach"] + 2, max(max(abs(p[0]), abs(p[2])) for p in built) + margin) if built else d["reach"]
    R = d.get("wide", R)   # a monument where the world put it: the land round it is the point
    d["slab"] = R
    ys = [p[1] for p in d["blocks"]] + list(d["ground"].values())
    lo = max(-72 if "wide" in d else -34, min(ys) - 2)   # (real land is drawn down to its valleys, so water lies in them)
    top, under = SKIN.get(d["lands"][0] if d["lands"] else "", ("grass_block", "dirt"))
    if not d["lands"]:
        top, under = "sand", "sandstone"   # the shore places
    vox = {}
    T, U, S = ("minecraft:" + top, ()), ("minecraft:" + under, ()), ("minecraft:stone", ())
    for x in range(-R, R + 1):
        for z in range(-R, R + 1):
            g = d["ground"].get((x, z), 0)
            for y in range(lo, g + 1):
                vox[(x, y, z)] = T if y == g else U if y > g - 4 else S
    for pos, b in d["blocks"].items():
        if plain(b[0]) in ("air", "cave_air"):
            vox.pop(pos, None)
        else:
            vox[pos] = b
    return vox, lo


def render(vox, a, view=0, keep=None, lights=False):
    """One picture: the camera stands to the south-east of the turned place and looks down at it."""
    pen = PENS.setdefault(a, Pen(a))
    V = {}
    for (x, y, z), b in vox.items():
        X, Z = turn(x, z, view)
        if keep is None or keep(X, y, Z):
            V[(X, y, Z)] = turn_props(b, view)
    solid = {p for p, b in V.items() if opaque(b)}
    roof = {}
    for (X, y, Z) in solid:
        if y > roof.get((X, Z), -99):
            roof[(X, Z)] = y
    items = []
    for (X, y, Z), b in V.items():
        if (X + 1, y, Z) in solid and (X, y + 1, Z) in solid and (X, y, Z + 1) in solid:
            continue
        n = plain(b[0])
        kind = kind_of(n, b[0])
        if kind == "none":
            continue
        arms = 0
        if kind == "post":
            for bit, (dx, dz) in ((1, (0, -1)), (2, (1, 0)), (4, (0, 1)), (8, (-1, 0))):
                nb = V.get((X + dx, y, Z + dz))
                if nb is not None and (opaque(nb) or kind_of(plain(nb[0]), nb[0]) in ("post", "gate")):
                    arms |= bit
            if V.get((X, y + 1, Z)) is not None:
                arms |= 16
        elif kind == "water":
            up = V.get((X, y + 1, Z))
            if up is not None and plain(up[0]) == "water":
                continue
            if V.get((X + 1, y, Z)) is None or V.get((X, y, Z + 1)) is None:
                arms |= 32
        # under cover: something roofs this column and this block's own top is open to the air below it (a floor, a bench);
        # a wall's blocks have masonry over them and are lit from the side like any other
        dark = roof.get((X, Z), -99) > y + 1 and kind != "water" and (X, y + 1, Z) not in solid
        items.append((X + Z + y, y, X, Z, b, arms, dark))
    if not items:
        return Image.new("RGB", (64, 64), (24, 26, 30))
    items.sort(key=lambda e: e[:4])
    xs = [(e[2] - e[3]) * pen.a for e in items]
    ys = [(e[2] + e[3]) * pen.a / 2 - (e[1] + 1) * pen.h for e in items]
    ox, oy = int(-min(xs) + 2 * pen.a + 4), int(-min(ys) + pen.oy + 4)
    img = Image.new("RGBA", (int(max(xs) - min(xs) + 4 * pen.a + 8), int(max(ys) - min(ys) + pen.t + 8)), (24, 26, 30, 255))
    glow = []
    for _, y, X, Z, b, arms, dark in items:
        s = pen.sprite(b, arms, view, dark)
        if not s:
            continue
        px = ox + (X - Z) * pen.a - 2 * pen.a
        py = oy + int((X + Z) * pen.a / 2) - (y + 1) * pen.h - pen.oy
        img.alpha_composite(s[0], (px + s[1], py + s[2]))
        if lights and LIT.search("olden_lamp_lit" if plain(b[0]) == "olden_lamp" and dict(b[1]).get("lit") == "true" else plain(b[0])) and plain(b[0]) != "olden_lamp" or lights and plain(b[0]) == "olden_lamp" and dict(b[1]).get("lit") == "true":
            glow.append((px + 2 * pen.a, py + pen.oy + pen.h // 2))
    if lights:  # the same picture at night: dark, with a pool of light round everything that burns
        night = Image.eval(img.convert("RGB"), lambda v: int(v * 0.30))
        halo = Image.new("L", img.size, 0)
        d = ImageDraw.Draw(halo)
        r = pen.a * 7
        for gx, gy in glow:
            d.ellipse([gx - r, gy - r * 0.6, gx + r, gy + r * 0.6], fill=255)
        from PIL import ImageFilter
        halo = halo.filter(ImageFilter.GaussianBlur(pen.a * 2.2))
        lit = Image.eval(img.convert("RGB"), lambda v: min(255, int(v * 1.0)))
        warm = Image.merge("RGB", [lit.getchannel(c).point(lambda v, m=m: min(255, int(v * m))) for c, m in (("R", 1.08), ("G", 0.95), ("B", 0.72))])
        return Image.composite(warm, night, halo)
    return img.convert("RGB")


PENS = {}


def plan(d, vox, lo, scale=6):
    """Straight down, north at the top, with the tour's cameras marked."""
    R = max(abs(k[0]) for k in vox)
    size = (2 * R + 1) * scale
    im = Image.new("RGB", (size, size), (24, 26, 30))
    dr = ImageDraw.Draw(im)
    tops = {}
    for (x, y, z), b in vox.items():
        if y >= tops.get((x, z), (-99,))[0]:
            tops[(x, z)] = (y, b)
    for (x, z), (y, b) in tops.items():
        n = plain(b[0])
        kind = kind_of(n, b[0])
        if kind == "water":
            c = WATER
        elif kind == "model":
            c = model_boxes(b[0], dict(b[1]).get("form", "0"), dict(b[1]).get("facing"))[0][1]
        else:
            side, top = textures(n)
            c = mean(top or side or "", tint_of(n, top or side or ""))
        f = 0.72 + 0.28 * max(0, min(1, (y + 4) / 26))
        west = tops.get((x - 1, z), (y,))[0]
        north = tops.get((x, z - 1), (y,))[0]
        f *= 1.0 + 0.10 * max(-2, min(2, (y - west) + (y - north)))
        dr.rectangle([(x + R) * scale, (z + R) * scale, (x + R + 1) * scale - 1, (z + R + 1) * scale - 1], fill=tuple(max(0, min(255, int(v * f))) for v in c))
    for i, (cx, cy, cz, yaw, pitch) in enumerate(d["cams"]):
        px, pz = (cx + R + 0.5) * scale, (cz + R + 0.5) * scale
        ang = math.radians(yaw)
        ex, ez = px - math.sin(ang) * scale * 5, pz + math.cos(ang) * scale * 5
        dr.line([px, pz, ex, ez], fill=(255, 60, 60), width=2)
        dr.ellipse([px - 4, pz - 4, px + 4, pz + 4], fill=(255, 230, 60), outline=(0, 0, 0))
        dr.text((px + 6, pz - 6), str(i), fill=(255, 255, 255))
    rr = d["reach"] * scale
    dr.rectangle([size / 2 - rr, size / 2 - rr, size / 2 + rr, size / 2 + rr], outline=(255, 255, 255))
    return im


def fit(im, w, h):
    f = min(w / im.width, h / im.height, 1.0)
    return im.resize((max(1, int(im.width * f)), max(1, int(im.height * f))), Image.LANCZOS) if f < 1 else im


def one(args):
    path, out = args
    d = parse(path)
    vox, lo = world(d)
    R = d["slab"]
    a = max(6, min(14, 1560 // (4 * R)))
    inside = d["cams"][3] if len(d["cams"]) > 3 else (0, 2, 0, 0, 0)
    views = {
        "se": render(vox, a, 0),
        "sw": render(vox, a, 1),
        "nw": render(vox, a, 2),
        # cut open through where the tour's inside camera stands: the far half of the place, seen from the south and from the east
        "cutz": render(vox, a, 0, keep=lambda X, y, Z, c=inside[2]: Z <= c),
        "cutx": render(vox, a, 0, keep=lambda X, y, Z, c=inside[0]: X <= c),
        # the roof off: everything above the inside camera's head gone
        "open": render(vox, a, 0, keep=lambda X, y, Z, c=inside[1] + 2: y <= c),
        "night": render(vox, a, 0, lights=True),
        "plan": plan(d, vox, lo),
    }
    stem = f"p{d['n']:02d}_{d['id']}"
    for k, im in views.items():
        im.save(out / f"{stem}_{k}.png")
    cw, ch = 1040, 760
    order = ["se", "sw", "cutz", "open", "night", "plan"]
    sheet = Image.new("RGB", (cw * 3, ch * 2), (24, 26, 30))
    dr = ImageDraw.Draw(sheet)
    for i, k in enumerate(order):
        im = fit(views[k], cw - 8, ch - 8)
        x, y = (i % 3) * cw, (i // 3) * ch
        sheet.paste(im, (x + (cw - im.width) // 2, y + (ch - im.height) // 2))
        dr.text((x + 8, y + 6), f"{d['n']} {d['name']}  [{k}]" if i == 0 else f"[{k}]", fill=(255, 255, 255))
    sheet.save(out / f"{stem}.jpg", quality=88)
    return stem, len(d["blocks"]), sorted(UNKNOWN)


def contact(out):
    """Six places a page from the south-east, to look over all thirty quickly."""
    files = sorted(out.glob("p??_*_se.png"))
    cw, ch = 1040, 700
    for i in range(0, len(files), 6):
        sheet = Image.new("RGB", (cw * 3, ch * 2), (24, 26, 30))
        dr = ImageDraw.Draw(sheet)
        for j, f in enumerate(files[i:i + 6]):
            im = fit(Image.open(f), cw - 8, ch - 8)
            x, y = (j % 3) * cw, (j // 3) * ch
            sheet.paste(im, (x + (cw - im.width) // 2, y + (ch - im.height) // 2))
            dr.text((x + 8, y + 6), f.stem[:-3], fill=(255, 255, 255))
        sheet.save(out / f"all_{i // 6 + 1}.jpg", quality=88)


def main():
    src, out = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2])
    out.mkdir(parents=True, exist_ok=True)
    want = set(sys.argv[3:])
    files = [p for p in sorted(src.glob("*.txt")) if not want or p.stem in want]
    unknown = set()
    with multiprocessing.Pool(min(8, max(1, len(files)))) as pool:
        for stem, n, unk in pool.imap_unordered(one, [(p, out) for p in files]):
            print(stem, n, "blocks")
            unknown.update(unk)
    if unknown:
        print("no texture found for:", ", ".join(sorted(unknown)))
    contact(out)


if __name__ == "__main__":
    main()
