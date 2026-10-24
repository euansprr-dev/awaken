# Awakened Lands: the overworld's own look

Plan: `docs/plan-lands.html` (approved 2026-10-24). Contract: `docs/SPEC.md` §15. Code: `dev.awaken.terrain`
(studio commands, the lands pack, biome table API), `dev.awaken.terrain.client` (wind, falling leaves, waving).
Data: `src/main/resources/datapacks/awakened_lands/` (new worlds only). Art: `tools/art/specs/lands.py`.
Studio: `tools/terrain/studio.sh`, `tools/terrain/survey.py`, `tools/autopilot/terrain-studio.txt`.

## The story the land tells

The gates are not new. Long before the player, the Signal's energy soaked into the land wherever its old gates stood,
and the land woke a little: trees grew past any natural size, moss learned to glow, sand ran to glass, mountains split
along lines of light. Those places are the **Awakened lands**. They keep the climate of what they used to be (an
Elderwood was a birch wood, a Lumen Forest a dark forest), so they sit naturally among vanilla biomes, and they come in
regions you walk *into*: the `awaken:lands/awakening` field is a slow noise, so Awakened lands are patches hundreds of
blocks across, never confetti.

## Six signatures every Awakened biome has

A biome is only done when it has all six, each readable at its own distance:

| Signature | Read from | Rule |
|---|---|---|
| **Silhouette** | the horizon (far terrain shows logs and leaves only) | one tree or landform shape no vanilla biome has |
| **Ground** | a mountain top (far terrain shows the main ground block) | a main surface colour distinct from grass, sand and snow |
| **Night light** | 100+ blocks at night | a glow of its own, or a deliberate darkness |
| **Air** | standing in it | something drifting: leaves, petals, motes, ash, dust, snow |
| **Sound** | eyes closed | an ambience bed of its own |
| **Light** | the screen's mood | a Lens look that eases in as you enter |

## Trees

- **Data trees** (vanilla's `minecraft:tree` feature on Awaken's own trunk and canopy shapes, Awaken logs and leaves):
  the common trees. Canopies stay within about 12 blocks of the trunk (a feature may write one chunk beyond its own).
  Variety comes from 2–3 sizes and shapes per biome, never one tree copied.
- **Hero trees** are structures (placed like lairs, `structure_set` in the lands pack, biome tag from the table): the
  giants you remember and steer by. Rare: roughly one per 300–500 blocks in their biome. Each one costs at most 2 ms to
  build and never writes outside its reserved box.
- Logs join `#minecraft:logs` (and `#minecraft:logs_that_burn` where they burn), leaves `#minecraft:leaves`, so gates,
  stars and every vanilla rule treat them as trees. Leaves decay like vanilla.
- Fuller trees come from art and silhouette, not extra leaf layers: every see-through leaf pixel is costly on Apple
  GPUs. Canopy masses are clusters with hollow cores (MoreCulling then culls the inside faces).
- No ticking or growing trees, no block entities in trees.

## Leaves in the air

Wind (`terrain.Wind`): direction, strength and gusts from world time and seed, the same on every client, no packets.
Falling leaves come from Awaken's own particle pool, not the vanilla particle: coloured from the leaf block (its tint
or its texture), sized and shaped per tree family, tumbling, carried by the wind, streaming in gusts, settling for a
moment on the ground before fading. Capped (about 150 at once), scaled down by the frame budget, spawned only from
leaves with air below within ~32 blocks. Vanilla leaves get them too (subtler); Awakened trees get their own colours.

## The first two (the vertical slice)

### Elderwood (Greenwild; replaces forest, birch forest, old-growth birch forest and flower forest in the field)

- **Silhouette:** towering pale trunks under a high gold-green canopy; once in a while an **Elder Colossus**.
- **Trees:** *Elder* (2×2 trunk, 18–26 tall, layered canopy of 2–3 masses, gold-green), *Young elder* (1×1, 9–13 tall,
  leaning), *Elder Colossus* (structure: trunk 6–9 across, 55–85 tall, buttress roots arching 8–14 blocks out,
  4–6 great limbs reaching 14–22 blocks, canopy clusters; a landmark from far terrain).
- **Bark:** pale silver-cream with dark vertical fissures (`elder_log`); planks warm cream.
- **Leaves:** gold-green, warmer at the canopy top (`elder_leaves`, fixed colours, not biome-tinted).
- **Ground:** moss and moss carpet in patches, gold leaf litter (the biome's dry-foliage colour is gold), ferns, a few
  fallen elder logs.
- **Air:** gold leaves drifting all day, thicker in gusts; dust motes in the light shafts.
- **Night:** fireflies near water; otherwise dark and calm.
- **Sound:** deep wind high in the canopy, slow creaks, birdsong by day.
- **Lens:** warm gold grade, soft haze, light shafts (Cinematic).

### Lumen Forest (Duskwood; replaces dark forest in the field)

- **Silhouette:** broad flat canopies on pale trunks, hanging vines; once in a while a **Lumen Spire** (structure,
  30–45 tall, a crown of hanging glowing vines).
- **Trees:** *Lumen tree* (2×2 trunk 10–16, broad flat dark-oak-like canopy), *Lumen sapling-size* (1×1, 6–9).
- **Bark:** ash white with faint blue veins (`lumen_log`).
- **Leaves:** deep teal to cyan with glowing specks (`lumen_leaves`: cutout leaves plus an emissive speck layer using
  model `light_emission`, no real light, so worldgen never runs light updates for them).
- **Ground:** lumen moss carpet (emissive specks), lumen caps (small glowing mushrooms), dense firefly bushes.
- **Air:** slow lumen motes at night (glowing, drifting up), pale spores by day.
- **Night light:** the whole forest glows softly blue-cyan; you can see it from a ridge.
- **Sound:** soft chimes, a low hum, owls.
- **Lens:** cool teal at night, bloom picks up the specks.

## How to check

- Studio: `tools/terrain/studio.sh <preset>` for maps and numbers; `/awaken terrain visit awaken:<biome>` to stand in one.
- Every Awakened biome: census test (archetype, fauna, cottage, spirits), a worldgen test that its trees and hero
  structures place on ground without floating or cutting, a cost bench, and a contact sheet (noon, golden hour, night,
  from the ground and from 60 blocks up).

## The slice as built (2026-10-24)

Everything below is generated by `tools/content/lands_content.py` (assets, loot, recipes, tags, data maps, every lands
pack file but the biome tags) and `tools/art/specs/lands.py` (textures); edit those, never the JSON. Biome tags,
including the hero trees' `awaken:has_structure/<tree>`, come from the biome table (`tools/terrain/biomes.py`, column
`heroes`).

### Blocks (`terrain.LandsBlocks`)

Elder: `elder_log`, `stripped_elder_log`, `elder_wood`, `stripped_elder_wood`, `elder_planks`, `elder_leaves`,
`elder_sapling`. Lumen: `lumen_log`, `stripped_lumen_log`, `lumen_wood`, `stripped_lumen_wood`, `veined_lumen_log` (the
Spire's bark, glowing veins), `lumen_planks`, `lumen_leaves`, `lumen_sapling`, `lumen_moss` (carpet), `lumen_cap`
(cross plant, light 4: the forest floor's only real light, a soft pool round each cluster), `lumen_vines` (hanging,
vanilla `HangingMossBlock`; the tip ends in a glowing bulb). Logs and woods are in `#awaken:elder_logs` /
`#awaken:lumen_logs` → `#minecraft:logs_that_burn`; leaves `#minecraft:leaves`; stripping and fire are NeoForge data
maps (`transformables`, `flammable`). Leaves are plain `LeavesBlock`s with no vanilla falling-leaf particle (the lands'
own particle pool covers them) and no ambient sound. Elder leaves are untinted.

**Glow without overdraw or light.** Lumen leaves are a normal cutout leaf cube plus one-pixel (sides) or 2×2 (top and
bottom) *solid* emissive faces laid 0.05 px in front of the leaf face, sampling the solid `lumen_glow` tile, so the
glow costs no alpha-tested overdraw (Apple GPUs) and keeps its colour at every mip level. Three weighted variants
(two speckled, one plain) keep the extra quads to about 2.5× a vanilla leaf. Lumen moss does the same on its top.
Caps, vine tips and the veined log use vanilla's `cross_emissive`-style overlay layer (few blocks).

### Trees (lands pack `worldgen/feature/lands/*`, Awaken placers in `terrain.tree`)

- `awaken:limbed_trunk_placer`: a 1×1 or 2×2 trunk that may lean (`lean`, `lean_start`), a flared root base
  (`root_flare`, columns that reach down to the ground or are skipped), `limbs` leaving the trunk between `limb_start`
  and the top at golden-angle steps and rising `limb_rise` per block, a twig cross (`twig`) inside every mass, and the
  masses' size (`limb_mass`, `crown_mass` added to the foliage radius). Limbs, roots and twigs are the `branch_provider`
  (bark on all sides).
- `awaken:cloud_foliage_placer`: an ellipsoid mass (domed `height` above, `depth` × height below, `ragged` edge,
  optional `hollow` core, `hang` drooping leaves). It only keeps leaves that reach wood within six steps through other
  leaves (a walk out from the logs already in the mass), which is vanilla's decay rule: no leaf is born decaying and no
  leaf hangs loose at the ragged edge (both checked by `terrain_lands_trees_grow`).
- `awaken:hanging_vines` decorator: strands under leaves with air below, `body` down to `tip`.

| Tree | Trunk | Height | Crown | Extras |
|---|---|---|---|---|
| `lands/elder` | 2×2 elder log, root flare | 18–26 | 3–5 limbs from half height, masses r 3–4 on limbs and top: a layered gold crown | gold litter, shelf mushrooms, moss/podzol ring |
| `lands/young_elder` | 1×1, leans up to 2 | 9–13 | 1–2 short limbs, r 2–3 | litter |
| `lands/lumen` | 2×2 lumen log | 10–16 | 3–5 near-level limbs, flat pads r 3–4 one block over the twigs | glowing vines, lumen moss and caps round the foot |
| `lands/small_lumen` | 1×1 | 6–9 | 2–3 limbs, pads r 2–3 | short vines |
| `lands/fallen_elder`, `lands/fallen_lumen` | vanilla `fallen_tree` | 5–11 / 4–8 long | – | moss carpet / lumen moss on top, mushrooms / caps |

Saplings grow them (one sapling: young elder / small lumen; four: elder / lumen tree) wherever the lands pack is on.

### The two biomes (lands pack `worldgen/biome/*.json`)

Copied from their archetype with vanilla spawns, carvers, ores and order kept (vanilla features keep their relative
order across biomes: the feature sorter rejects cycles), the archetype's trees swapped for ours.

- **Elderwood** (old-growth birch forest): grass `#8ab35c`, foliage `#a9b64c`, dry foliage `#d9a53c` (the leaf litter
  is gold), water `#4aa7c9`, warm fog `#d8d8bf`, sky `#88b2f4`. Vegetation: moss patches (moss block, carpet, ferns,
  litter), 6–8 trees a chunk (55% Elder, 42% young, 3% fallen), forest flowers and wildflowers, bushes, grass, ferns
  and large ferns, dense gold litter, mushrooms, pumpkins, cane, fireflies by water.
- **Lumen Forest** (dark forest): grass `#40735f`, foliage `#2f8076`, dry foliage `#5f8a8c`, water `#2a93a6`, fog
  `#a3c4ca`, sky `#7fa3e4`. Vegetation: lumen moss patches (carpet over grass, caps, ferns), 5–7 trees a chunk (55%
  Lumen tree, 42% small, 3% fallen; no huge mushrooms, their brown and orange caps broke the teal), grass, lumen caps, firefly bushes everywhere (not only by
  water), litter, mushrooms. No forest flowers.
- Placement: Lithostitched `replace_partially` where `awaken:lands/awakening` ≥ 0: Elderwood for forest, birch forest,
  old-growth birch and flower forest; Lumen Forest for dark forest.

### Hero trees (structures, `terrain.hero`)

One structure type `awaken:hero_tree` (`"tree": "elder_colossus" | "lumen_spire"`) and one piece. A tree is a
**blueprint** (`Blueprint`): a dense voxel grid round its foot built from the structure's seed alone (fluted trunk,
buttress-root fins, limbs as tapered tubes, canopy masses as hash-ragged ellipsoids, twig spokes), then its leaves
settled (each leaf's distance to wood through other leaves; leaves no wood reaches are dropped, ones beyond six are
made persistent), vines hung, columns sealed. Blueprints are cached (12, thread-safe) while their chunks generate,
so every chunk places from the same tree. The piece's box is reserved from the blueprint's own bounds plus the ground
cover round the foot and 24 blocks below for roots; each chunk writes only its part. Placement replaces only air,
plants, snow, water and leaves (never logs, ground or anything built); trunk and root columns continue down to the
real ground (so roots wall down a slope and sink into a hump: nothing floats); ground cover (Elder: moss, podzol, gold
litter, ferns; Spire: lumen moss and caps) is laid round the foot first.

- **Elder Colossus**: trunk 6–9 across swelling into the roots, 55–85 tall with a slow sway; 5–7 buttress roots arching
  8–15 out; 4–6 great limbs from 44–78% of the height, 14–24 long, near level then rising; on each a cumulus mass (one
  big hollow ellipsoid and 2–4 puffs) at the end, one along it and two on side branches; three or four leaders round a
  deliberately small top crown, so the sky reaches the trunk between the limbs (sky light beside the trunk 12; with a
  big crown over it, 4–6, and the trunk read as a black pole).
- **Lumen Spire**: a three-lobed trunk twisting like rope, 30–45 tall, veined lumen log (glowing veins); 4–6 roots;
  two small pad tiers on the way up; 6–8 crown limbs rising then drooping with pads along them and one over the
  middle; a curtain of glowing vines from the crown (20% of the open leaf undersides, 2–6 long, up to 5 longer toward
  the rim).
- Rarity: `random_spread` spacing 26 chunks (Colossus) and 22 (Spire), the site anywhere within 4 chunks of the
  candidate where the biome is right (checked first), the land dry, the footprint level (≤ 3 steps over 10/6 blocks)
  and the root ring within 10/6 of the centre's height. Inside their biome that is roughly one per 300–500 blocks.
- `/awaken terrain hero <tree> [seed]` grows one where you stand; `/awaken terrain herovisit <tree> [distance above]`
  stands you south of the nearest generated one, looking at it, and logs the sky light beside its trunk.

### Checks

GameTests: `terrain_lands_blocks`, `terrain_lands_trees_grow` (6 seeds of each data tree on a platform: grows, wood
and canopy, no leaf born decaying, nothing loose, reach ≤ 13, height range), `terrain_lands_biomes`,
`terrain_hero_blueprints` (8 seeds each: sizes, nothing disconnected, few persistent leaves, cost),
`terrain_hero_builds_in_box` (a Colossus on a slope and a Spire, chunk by chunk: inside the box, on the ground,
nothing loose, no decaying leaves, cost per chunk), `terrain_hero_sites` (site search on a real overworld).
Tour: `tools/autopilot/lands-slice.txt` (run with `PACK=perf,world TECTONIC=awakened`), light probe
`tools/autopilot/lands-probe.txt`.

