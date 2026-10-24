#!/usr/bin/env python3
"""The one biome table (docs/SPEC.md §15): what Awaken gives every overworld biome, vanilla or Awakened.

    python3 -I tools/terrain/biomes.py write    writes every file below from the table (and removes stale ones)
    python3 -I tools/terrain/biomes.py check    exits 1 if a written file is stale or the table has a hole

Base Awaken data (src/main/resources/data/awaken/tags/worldgen/biome) lists vanilla biomes only, so a world without
the Awakened Lands pack keeps exactly what it had:
    fauna/<group>       the biomes of the spawn modifier neoforge/biome_modifier/fauna_<group>.json
    has_lair/<beast>    where that named beast's lair may stand (worldgen/structure/lair/<beast>.json)
    lair_style/<style>  where that lair wears its second look (red sand, snow, pale oak, coral, moss)
    cottage/<plan>      the guide cottage's wood and stone (hearth.guide.CottagePlan)
The Awakened Lands pack (src/main/resources/datapacks/awakened_lands, new worlds only) adds each Awakened biome to the
same tags (tags merge across packs), to awaken:archetype/<vanilla biome>, and to every minecraft:, c: and awaken: biome
tag its archetype is in (nested tags resolved), so code that asks vanilla tags (rifts, gates, the cottage, structures)
treats it like its archetype; dev.awaken.terrain.api.BiomeTable.archetype() answers for code that reads names (spirits).
It also copies the archetype's villager type (NeoForge's data map). This script owns those tag folders of the base
data and every biome tag and the villager-type map of the lands pack: never edit them by hand.

Vanilla tags are read from the 26.3 sources (AWAKEN_REF, default /Volumes/MEDIA/Awaken/.ref): Minecraft's in
assets/data/minecraft, NeoForge's c: tags in neo/data/c. Stdlib only, deterministic.
"""
import json
import os
import re
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "../.."))
REF = os.environ.get("AWAKEN_REF", "/Volumes/MEDIA/Awaken/.ref")
RES = os.path.join(ROOT, "src/main/resources")
BASE = os.path.join(RES, "data")
LANDS = os.path.join(RES, "datapacks/awakened_lands/data")
MERGE = os.path.join(ROOT, "src/main/merge")
BIOME_TAGS = "tags/worldgen/biome"
VILLAGER_TYPES = "neoforge/data_maps/worldgen/biome/villager_types.json"
VANILLA_TAG_DIRS = [("minecraft", os.path.join(REF, "assets/data/minecraft", BIOME_TAGS)),
                    ("c", os.path.join(REF, "neo/data/c", BIOME_TAGS))]
VANILLA_VILLAGER_TYPES = os.path.join(REF, "neo/data", VILLAGER_TYPES)

# ---------------------------------------------------------------------------------------------------------------------
# What there is to give

# One fauna group per spawn modifier (creatures and weights: tools/art/specs/fauna.py, fauna2.py, fauna3.py).
GROUPS = ("greenwild", "greenwild_woods", "blossom", "blossom_meadow", "canopy", "caves", "crag", "duskwood", "frostwild",
          "frostwild_taiga", "hollows_deep_dark", "hollows_dripstone", "hollows_lush", "mire", "spore", "sunscorch",
          "tidewater_deep", "tidewater_lakes", "tidewater_ocean", "tidewater_rivers")
# The named beasts with a lair (world.fauna.lair.LairStyle; structures from fauna2.py and fauna3.py).
BEASTS = ("briar_sow", "dune_wyrm", "empress_moth", "lantern_maw", "mire_mother", "mycelord", "old_antler", "plumed_coil",
          "stonehorn", "thousandfoot", "weaver_queen")
# Lair styles with a second look (LairStyle.variant): where the tag lists the biome, the lair wears it.
LOOKS = ("glass_hollow", "antler_grove", "silk_hollow", "rams_cairn", "sunken_shrine", "crystal_nest")
# The guide cottage's palettes (hearth.guide.CottagePlan.PALETTES).
PLANS = ("meadow", "taiga", "birch", "jungle", "savanna", "desert", "badlands", "swamp", "mangrove", "cherry")
# Tag references kept in the base tags, so any mod's biome in them is treated as before (red sand in every badlands).
REFS = {"awaken:lair_style/glass_hollow": ["#minecraft:is_badlands"]}

# ---------------------------------------------------------------------------------------------------------------------
# The vanilla side: one row per vanilla overworld biome (#minecraft:is_overworld). It reproduces what Awaken gave each
# biome before the table (the fauna modifiers' id lists, the has_lair lists, LairStyle's and Cottage's id and tag checks);
# the parity GameTest (terrain_biome_table_parity) holds it to that.
#   biome                         fauna groups                                    lairs           second looks             cottage
VANILLA = {
    "badlands":                  ("sunscorch",                                    "dune_wyrm",    "glass_hollow",          "badlands"),
    "bamboo_jungle":             ("canopy",                                       "plumed_coil",  "",                      "jungle"),
    "beach":                     ("",                                             "",             "",                      "meadow"),
    "birch_forest":              ("blossom greenwild_woods tidewater_lakes",      "empress_moth", "",                      "birch"),
    "cherry_grove":              ("blossom tidewater_lakes",                      "empress_moth", "",                      "cherry"),
    "cold_ocean":                ("tidewater_ocean",                              "",             "",                      "taiga"),
    "dappled_forest":            ("greenwild greenwild_woods",                    "briar_sow",    "",                      "meadow"),
    "dark_forest":               ("duskwood tidewater_lakes",                     "weaver_queen", "",                      "swamp"),
    "deep_cold_ocean":           ("tidewater_deep tidewater_ocean",               "lantern_maw",  "",                      "taiga"),
    "deep_dark":                 ("hollows_deep_dark",                            "",             "",                      "meadow"),
    "deep_frozen_ocean":         ("tidewater_deep",                               "lantern_maw",  "",                      "taiga"),
    "deep_lukewarm_ocean":       ("tidewater_deep tidewater_ocean",               "lantern_maw",  "sunken_shrine",         "meadow"),
    "deep_ocean":                ("tidewater_deep tidewater_ocean",               "lantern_maw",  "",                      "meadow"),
    "desert":                    ("sunscorch",                                    "dune_wyrm",    "",                      "desert"),
    "dripstone_caves":           ("caves hollows_dripstone",                      "thousandfoot", "",                      "meadow"),
    "eroded_badlands":           ("sunscorch",                                    "dune_wyrm",    "glass_hollow",          "badlands"),
    "flower_forest":             ("blossom tidewater_lakes",                      "empress_moth", "",                      "meadow"),
    "forest":                    ("greenwild greenwild_woods tidewater_lakes",    "briar_sow",    "",                      "meadow"),
    "frozen_ocean":              ("",                                             "",             "",                      "taiga"),
    "frozen_peaks":              ("crag",                                         "stonehorn",    "rams_cairn",            "taiga"),
    "frozen_river":              ("tidewater_rivers",                             "",             "",                      "taiga"),
    "grove":                     ("frostwild",                                    "old_antler",   "antler_grove",          "taiga"),
    "ice_spikes":                ("frostwild",                                    "",             "",                      "taiga"),
    "jagged_peaks":              ("crag",                                         "stonehorn",    "rams_cairn",            "taiga"),
    "jungle":                    ("canopy tidewater_lakes",                       "plumed_coil",  "",                      "jungle"),
    "lukewarm_ocean":            ("tidewater_ocean",                              "",             "",                      "meadow"),
    "lush_caves":                ("caves hollows_lush",                           "thousandfoot", "crystal_nest",          "meadow"),
    "mangrove_swamp":            ("mire",                                         "mire_mother",  "",                      "mangrove"),
    "meadow":                    ("blossom_meadow greenwild tidewater_lakes",     "briar_sow",    "",                      "meadow"),
    "mushroom_fields":           ("spore",                                        "mycelord",     "",                      "meadow"),
    "ocean":                     ("tidewater_ocean",                              "",             "",                      "meadow"),
    "old_growth_birch_forest":   ("blossom greenwild_woods",                      "empress_moth", "",                      "birch"),
    "old_growth_pine_taiga":     ("frostwild frostwild_taiga tidewater_lakes",    "old_antler",   "",                      "taiga"),
    "old_growth_spruce_taiga":   ("frostwild frostwild_taiga tidewater_lakes",    "old_antler",   "",                      "taiga"),
    "pale_garden":               ("duskwood",                                     "weaver_queen", "silk_hollow",           "swamp"),
    "plains":                    ("greenwild tidewater_lakes",                    "briar_sow",    "",                      "meadow"),
    "river":                     ("tidewater_rivers",                             "",             "",                      "meadow"),
    "savanna":                   ("sunscorch tidewater_lakes",                    "dune_wyrm",    "",                      "savanna"),
    "savanna_plateau":           ("sunscorch",                                    "dune_wyrm",    "",                      "savanna"),
    "snowy_beach":               ("",                                             "",             "",                      "taiga"),
    "snowy_plains":              ("frostwild tidewater_lakes",                    "old_antler",   "antler_grove",          "taiga"),
    "snowy_slopes":              ("frostwild",                                    "",             "antler_grove rams_cairn", "taiga"),
    "snowy_taiga":               ("frostwild tidewater_lakes",                    "old_antler",   "antler_grove",          "taiga"),
    "sparse_jungle":             ("canopy",                                       "plumed_coil",  "",                      "jungle"),
    "stony_peaks":               ("crag",                                         "stonehorn",    "",                      "meadow"),
    "stony_shore":               ("crag",                                         "",             "",                      "taiga"),
    "sulfur_caves":              ("",                                             "",             "",                      "meadow"),
    "sunflower_plains":          ("blossom_meadow greenwild tidewater_lakes",     "briar_sow",    "",                      "meadow"),
    "swamp":                     ("mire tidewater_lakes",                         "mire_mother",  "",                      "swamp"),
    "taiga":                     ("frostwild frostwild_taiga tidewater_lakes",    "old_antler",   "",                      "taiga"),
    "warm_ocean":                ("tidewater_ocean",                              "",             "sunken_shrine",         "meadow"),
    "windswept_forest":          ("frostwild frostwild_taiga",                    "old_antler",   "",                      "taiga"),
    "windswept_gravelly_hills":  ("crag",                                         "stonehorn",    "",                      "taiga"),
    "windswept_hills":           ("crag",                                         "stonehorn",    "",                      "taiga"),
    "windswept_savanna":         ("sunscorch",                                    "dune_wyrm",    "",                      "savanna"),
    "wooded_badlands":           ("sunscorch",                                    "dune_wyrm",    "glass_hollow",          "badlands"),
}


# ---------------------------------------------------------------------------------------------------------------------
# The Awakened side: one row per Awakened biome. A biome takes everything its archetype (the vanilla biome it stands
# in for) has: fauna groups, lairs, second looks, cottage plan, and every vanilla, c: and awaken: tag. It also joins its
# family's fauna group (the plan's family) where the archetype lacks it; `add` and `drop` name tags to join or leave on
# top; `heroes` names the hero-tree structures that grow there (awaken:has_structure/<hero>, read by the lands pack's
# worldgen/structure/<hero>.json). Output is written only for biomes the lands pack has (worldgen/biome/<id>.json);
# `planned` rows show the shape.

class Awakened:
    def __init__(self, biome, family, archetype, add=(), drop=(), heroes=(), planned=False):
        self.id, self.family, self.archetype = "awaken:" + biome, family, archetype
        self.add, self.drop, self.heroes, self.planned = tuple(add), tuple(drop), tuple(heroes), planned


# The hero trees (dev.awaken.terrain.hero.HeroTree): giant trees built as structures, only in the biomes that list them;
# and the landmarks (dev.awaken.terrain.landmark.Landmark), placed the same way.
HEROES = ("elder_colossus", "lumen_spire", "weeping_matriarch", "gate_scar", "shard_drift", "aurora_spire")


AWAKENED = [
    # Greenwild colossal trees where the forests and birch woods were: the birch forest's life, the Greenwild's and its beast
    Awakened("elderwood", "greenwild", "old_growth_birch_forest", add=("awaken:has_lair/briar_sow",), heroes=("elder_colossus",)),
    # Duskwood: the dark forest's life in a forest that glows at night, under the Lumen Spires
    Awakened("lumen_forest", "duskwood", "dark_forest", heroes=("lumen_spire",)),
    # Sunscorch: the savanna's life on plains melted to glass where gates broke, round the Gate Scars
    Awakened("glass_steppe", "sunscorch", "savanna", heroes=("gate_scar",)),
    # Crag: the windswept hills' life on mountains split along lines of light, under the Shard Drifts
    Awakened("shattered_highlands", "crag", "windswept_hills", heroes=("shard_drift",)),
    # Frostwild: the snowy taiga's life among ice-crystal spires and blue pine giants, round the Aurora Spires
    Awakened("frost_spires", "frostwild", "snowy_taiga", heroes=("aurora_spire",)),
    # Mire: the swamp's life on still black water under weeping trees, round the Weeping Matriarchs
    Awakened("mirror_marsh", "mire", "swamp", heroes=("weeping_matriarch",)),
]

# ---------------------------------------------------------------------------------------------------------------------


def words(s):
    return s.split() if s else []


def table_tags():
    """The base tags the vanilla rows make: tag id -> member list (references first, then sorted biome ids)."""
    out = {}
    for name in GROUPS:
        out["awaken:fauna/" + name] = []
    for name in BEASTS:
        out["awaken:has_lair/" + name] = []
    for name in LOOKS:
        out["awaken:lair_style/" + name] = []
    for name in PLANS:
        out["awaken:cottage/" + name] = []
    for biome, (groups, lairs, looks, plan) in VANILLA.items():
        b = "minecraft:" + biome
        for g in words(groups):
            out["awaken:fauna/" + g].append(b)
        for l in words(lairs):
            out["awaken:has_lair/" + l].append(b)
        for s in words(looks):
            out["awaken:lair_style/" + s].append(b)
        out["awaken:cottage/" + plan].append(b)
    return {t: REFS.get(t, []) + sorted(v) for t, v in out.items() if v or t in REFS}


def tag_path(tag):
    ns, path = tag.split(":", 1)
    return os.path.join(ns, BIOME_TAGS, path + ".json")


def owned_base(rel):
    """Base data files this script owns (relative to src/main/resources/data)."""
    return any(rel.startswith(os.path.join("awaken", BIOME_TAGS, d) + os.sep) for d in ("fauna", "has_lair", "lair_style", "cottage"))


def owned_lands(rel):
    """Lands pack files this script owns (relative to the pack's data folder): every biome tag and the villager types."""
    parts = rel.split(os.sep)
    return (len(parts) > 4 and os.sep.join(parts[1:4]) == BIOME_TAGS) or rel == VILLAGER_TYPES


def read_tags(root, ns, into, skip=None):
    """Adds every biome tag under root (one namespace's tags/worldgen/biome folder) to into, merging values."""
    if not os.path.isdir(root):
        return
    for dp, dn, fn in os.walk(root):
        dn.sort()
        for f in sorted(fn):
            if not f.endswith(".json"):
                continue
            full = os.path.join(dp, f)
            if skip and skip(full):
                continue
            tag = ns + ":" + os.path.relpath(full, root)[:-5].replace(os.sep, "/")
            into.setdefault(tag, []).extend(json.load(open(full))["values"])


def known_tags():
    """Every biome tag an Awakened biome may inherit: vanilla's, NeoForge's c:, Awaken's own base tags (other modules'
    from src/main/resources and src/main/merge, plus the table's)."""
    tags = {}
    for ns, root in VANILLA_TAG_DIRS:
        if not os.path.isdir(root):
            sys.exit(f"biomes.py: no vanilla tags at {root} (set AWAKEN_REF)")
        read_tags(root, ns, tags)
    roots = [BASE] + [os.path.join(MERGE, m, "data") for m in sorted(os.listdir(MERGE)) if os.path.isdir(os.path.join(MERGE, m, "data"))]
    for data in roots:
        for ns in sorted(os.listdir(data)) if os.path.isdir(data) else []:
            read_tags(os.path.join(data, ns, BIOME_TAGS), ns, tags,
                      skip=lambda full, data=data: data == BASE and owned_base(os.path.relpath(full, BASE)))
    for t, v in table_tags().items():
        tags.setdefault(t, []).extend(v)
    return tags


def resolver(tags):
    memo = {}

    def resolve(tag, stack=()):
        if tag in memo:
            return memo[tag]
        if tag in stack:
            sys.exit(f"biomes.py: tag cycle through #{tag}")
        out = set()
        for v in tags.get(tag, []):
            v = v["id"] if isinstance(v, dict) else v
            out |= resolve(v[1:], stack + (tag,)) if v.startswith("#") else {v}
        memo[tag] = out
        return out
    return resolve


def lands_biomes():
    d = os.path.join(LANDS, "awaken/worldgen/biome")
    return sorted("awaken:" + f[:-5] for f in os.listdir(d) if f.endswith(".json")) if os.path.isdir(d) else []


def awakened_tags(row, tags, resolve):
    """The tags one Awakened biome joins: its archetype's (resolved), its archetype tag, its family, then add and drop."""
    arch = "minecraft:" + row.archetype
    got = {t for t in tags if arch in resolve(t)}
    got |= {"awaken:archetype/" + row.archetype, "awaken:fauna/" + row.family} | set(row.add)
    got |= {"awaken:has_structure/" + h for h in row.heroes}
    return got - set(row.drop)


def outputs():
    """Every file this script writes: (folder, relative path) -> JSON."""
    out = {}
    for tag, values in table_tags().items():
        out[(BASE, tag_path(tag))] = {"values": values}
    tags = known_tags()
    resolve = resolver(tags)
    members = {}
    villagers = json.load(open(VANILLA_VILLAGER_TYPES))["values"] if os.path.exists(VANILLA_VILLAGER_TYPES) else {}
    types = {}
    have = set(lands_biomes())
    for row in AWAKENED:
        if row.id not in have:
            continue
        for t in awakened_tags(row, tags, resolve):
            members.setdefault(t, []).append(row.id)
        if "minecraft:" + row.archetype in villagers:
            types[row.id] = villagers["minecraft:" + row.archetype]
    for tag, ids in members.items():
        out[(LANDS, tag_path(tag))] = {"values": sorted(ids)}
    if types:
        out[(LANDS, VILLAGER_TYPES)] = {"values": dict(sorted(types.items()))}
    return out


def text(data):
    return json.dumps(data, indent=2) + "\n"


def on_disk():
    """Files currently in the folders this script owns: (folder, relative path)."""
    found = set()
    for root, owned in ((BASE, owned_base), (LANDS, owned_lands)):
        for dp, dn, fn in os.walk(root):
            for f in fn:
                rel = os.path.relpath(os.path.join(dp, f), root)
                if owned(rel):
                    found.add((root, rel))
    return found


def problems():
    """Holes and contradictions in the table, checked against the files it describes."""
    errs = []
    tags = known_tags()
    resolve = resolver(tags)
    overworld = sorted(b[len("minecraft:"):] for b in resolve("minecraft:is_overworld") if b.startswith("minecraft:"))
    for b in overworld:
        if b not in VANILLA:
            errs.append(f"vanilla overworld biome minecraft:{b} has no row")
    for b in VANILLA:
        if b not in overworld:
            errs.append(f"row {b} is not a vanilla overworld biome")
    for b, (groups, lairs, looks, plan) in VANILLA.items():
        errs += [f"{b}: unknown fauna group {g}" for g in words(groups) if g not in GROUPS]
        errs += [f"{b}: unknown lair {l}" for l in words(lairs) if l not in BEASTS]
        errs += [f"{b}: unknown second look {s}" for s in words(looks) if s not in LOOKS]
        if plan not in PLANS:
            errs.append(f"{b}: unknown cottage plan {plan}")
    # the spawn modifiers and lair structures read these tags, one each
    mods = os.path.join(BASE, "awaken/neoforge/biome_modifier")
    seen = set()
    for f in sorted(os.listdir(mods)):
        if f.startswith("fauna_") and f.endswith(".json"):
            g = f[len("fauna_"):-5]
            seen.add(g)
            want = "#awaken:fauna/" + g
            if json.load(open(os.path.join(mods, f))).get("biomes") != want:
                errs.append(f"biome_modifier/{f} should name {want} (regenerate with tools/art/specs/fauna*.py content)")
    errs += [f"fauna group {g} has no biome_modifier/fauna_{g}.json" for g in GROUPS if g not in seen]
    errs += [f"biome_modifier/fauna_{g}.json has no fauna group in the table" for g in sorted(seen - set(GROUPS))]
    lairs = os.path.join(BASE, "awaken/worldgen/structure/lair")
    styles = set()
    for beast in BEASTS:
        p = os.path.join(lairs, beast + ".json")
        if not os.path.exists(p):
            errs.append(f"lair {beast} has no worldgen/structure/lair/{beast}.json")
            continue
        s = json.load(open(p))
        styles.add(s.get("lair"))
        if s.get("biomes") != "#awaken:has_lair/" + beast:
            errs.append(f"structure/lair/{beast}.json should name #awaken:has_lair/{beast}")
    errs += [f"lair structure {f[:-5]} has no row in BEASTS" for f in sorted(os.listdir(lairs)) if f[:-5] not in BEASTS]
    errs += [f"second look {s} is no lair style" for s in LOOKS if s not in styles]
    plan_src = open(os.path.join(ROOT, "src/main/java/dev/awaken/hearth/guide/CottagePlan.java")).read()
    if sorted(re.findall(r'new Palette\("(\w+)"', plan_src)) != sorted(PLANS):
        errs.append("PLANS differ from CottagePlan's palettes")
    # the Awakened rows: one per biome of the lands pack, each complete
    have = set(lands_biomes())
    rows = {r.id: r for r in AWAKENED}
    errs += [f"lands pack biome {b} has no row" for b in sorted(have - set(rows))]
    for r in AWAKENED:
        if r.planned and r.id in have:
            errs.append(f"{r.id} exists in the lands pack: it is no longer planned")
        if not r.planned and r.id not in have:
            errs.append(f"{r.id} has a row but no worldgen/biome/{r.id[7:]}.json in the lands pack")
        if r.archetype not in VANILLA:
            errs.append(f"{r.id}: archetype minecraft:{r.archetype} is not a vanilla overworld biome")
            continue
        if r.family not in GROUPS:
            errs.append(f"{r.id}: family {r.family} is no fauna group")
        inherited = awakened_tags(Awakened(r.id[7:], r.family, r.archetype), tags, resolve)
        errs += [f"{r.id}: drops {t}, which its archetype lacks" for t in r.drop if t not in inherited]
        errs += [f"{r.id}: adds unknown tag {t}" for t in r.add if t not in tags]
        got = awakened_tags(r, tags, resolve)
        if "awaken:fauna/" + r.family not in got:
            errs.append(f"{r.id}: not in its family's group #awaken:fauna/{r.family}")
        if sum(t.startswith("awaken:cottage/") for t in got) != 1:
            errs.append(f"{r.id}: needs exactly one cottage plan")
        if "minecraft:is_overworld" not in got:
            errs.append(f"{r.id}: not in #minecraft:is_overworld")
        errs += [f"{r.id}: unknown hero tree {h}" for h in r.heroes if h not in HEROES]
    # every hero tree grows somewhere, and its structure reads its tag
    for h in HEROES:
        if not any(h in r.heroes for r in AWAKENED if r.id in have):
            errs.append(f"hero tree {h} grows in no biome of the lands pack")
        p = os.path.join(LANDS, "awaken/worldgen/structure", h + ".json")
        if os.path.exists(p) and json.load(open(p)).get("biomes") != "#awaken:has_structure/" + h:
            errs.append(f"lands pack structure/{h}.json should name #awaken:has_structure/{h}")
    return errs


def main(mode):
    errs = problems()
    out = outputs()
    want = {k: text(v) for k, v in out.items()}
    stale = sorted(os.path.relpath(os.path.join(root, rel), ROOT) for (root, rel), t in want.items()
                   if not os.path.exists(os.path.join(root, rel)) or open(os.path.join(root, rel)).read() != t)
    extra = sorted(on_disk() - set(want))
    if mode == "write":
        for (root, rel), t in want.items():
            p = os.path.join(root, rel)
            os.makedirs(os.path.dirname(p), exist_ok=True)
            if not os.path.exists(p) or open(p).read() != t:
                with open(p, "w") as f:
                    f.write(t)
        for root, rel in extra:
            os.remove(os.path.join(root, rel))
        print(f"biomes: {len(VANILLA)} vanilla rows, {sum(not r.planned for r in AWAKENED)} Awakened, {len(want)} files"
              f" ({len(stale)} written, {len(extra)} removed)")
    else:
        errs += [f"stale: {rel} (run tools/terrain/biomes.py write)" for rel in stale]
        errs += [f"not the table's: {os.path.relpath(os.path.join(root, rel), ROOT)}" for root, rel in extra]
    for e in errs:
        print("biomes.py: " + e, file=sys.stderr)
    if errs:
        sys.exit(1)
    if mode == "check":
        print(f"biomes: table and {len(want)} files up to date")


if __name__ == "__main__":
    if len(sys.argv) != 2 or sys.argv[1] not in ("write", "check"):
        sys.exit(__doc__)
    main(sys.argv[1])
