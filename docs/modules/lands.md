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

**Wind** (`dev.awaken.terrain.Wind`, common code): a pure function of the world's seed and game time, the same on the
server and every client, no packets. The client only knows the SHA-256 of the seed (the biome zoom seed it gets at
login); `Wind.salt(level)` fingerprints that through the level's own `BiomeManager` (which neighbouring cell each of 24
probe positions picks), and the server, which hashes its seed the same way, gets the same salt.
- *Direction*: a heading for two in-game hours (100 s), then a 20 s turn to the next; headings follow a slow noise over
  the segments, so the wind wanders round over in-game hours and never jumps.
- *Steady strength* 0..1 breathes over a few minutes; rain adds 0.3, thunder another 0.3.
- *Gusts* 0..1 are fronts rolling downwind at 7 blocks/s: the gust at a place is the gust signal delayed by how far
  downwind it lies, so trees upwind move before the ones around you. `strength = min(1, steady (0.6 + 0.8 gust) +
  0.25 gust)`.
- API: a mutable `Wind.Field` (set once a frame or tick, then `gust(x, z)`, `strength(x, z)` anywhere, no allocation).
  The client keeps one (`terrain.client.ClientWind`) for the waving shader, the falling leaves and the rustle, so all
  three move with the same gusts. Tests: `terrain_wind_deterministic`, `terrain_wind_ranges`.

**Waving leaves** (Sodium 0.9.3-beta.1 only; docs/spikes/waving-leaves.md): the shader reads each leaf's gust from a
64-sample profile along the wind through the camera (4 blocks apart) in the block atlas's corner texels, so the canopy
sways hardest where a front is passing.

**Falling leaves** (`terrain.client.FallingLeaves`, `FallingLeafParticle`): Awaken's own particle, never vanilla's.
- *Spawning*: each tick 12 random spots (normal) 6-32 blocks from the camera, 70% within ±70° of where it looks; the
  column's top canopy is searched (≤16 blocks) for a leaf block with air below. Chance = 0.06 × biome rate × leaf rate
  × (0.25 + 1.5 × wind strength there). Held in fixed slots: at most 60 / 150 / 300 alive (`falling_leaves` = off |
  low | normal | high, default normal, `config/awaken-terrain-client.toml`), times a frame-budget scale (1 at 50 fps or
  better, down to 0.25 by 30 fps, so a 60 fps cap costs nothing). Nothing is allocated per tick but the particles.
- *Look*: tinted per leaf block: its BlockColors tint over the average colour of its texture (oak follows the biome's
  foliage, birch and spruce their fixed tints; cherry-like untinted leaves keep their own), a biome may pull every leaf
  toward its colour (Elderwood: gold), ±13% brightness, about one in six already turning. Sprite by tree family
  (`falling_leaf_0..5`: oval, long, round, edge, elder, needles; white, tinted at spawn).
- *Motion*: tumbling (the quad turns about its own horizontal axis, so it thins to an edge and widens again; it falls
  faster edge-on), fluttering side to side across the wind, drifting toward the wind's speed at its position (0.3 blocks/s
  in a breeze, ~3 in a strong gust), lifted a little by gusts, so gusts carry streams. On a block top or water it lies
  flat for 1-3 s (water 2-4 s), then fades over 1 s. Moves itself: one block lookup a tick, light read every 4 ticks.
- *Lumen Forest*: glowing motes rising slowly at night (full bright, pulsing), no leaves (`lumen_mote_0/1`).
- **The table** `assets/awaken/lands/air.json` (schemaVersion 1; any `lands/air*.json`, later files' rules first):
  per leaf block or `#tag` (`sprites`, `size`, `rate`, `color`, `skip`) and per biome, `#tag` or `*` (`rate`, `color` +
  `colorMix`, `sprites`, `size`, `motes {color, rate, night}`). Today: vanilla forests 1.0, everything else 0.5, cherry
  grove 0 and cherry leaves skipped (vanilla already drops petals), Elderwood 3.0 and gold, Lumen Forest motes only;
  `awaken:elder_leaves` and `awaken:lumen_leaves` are already listed for the slice's trees.

**Leaf rustle** (`terrain.client.LeafRustle`, Ambient/Environment slider): two mono beds around the listener,
`amb.lands_rustle` (leaf ticks over a soft shush, a little air) and `amb.lands_wind_high` (a slow low howl, rumble,
a faint whistle), rendered by `tools/art/specs/audio_lands.py`. Rustle volume = canopy × (0.12 + 0.88 strength) ×
(0.75 + 0.25 gust), canopy being the share of 16 columns on rings of 5 and 11 blocks whose top is leaves above head
height; high wind = open sky × (0.12 + 0.88 height above sea level + 30, over 70 blocks) × (0.1 + 0.9 strength) ×
(1 − 0.6 canopy). Both ease in and out and stop when silent. Setting `leaf_rustle` (default on).

**Commands** (client): `/awaken terrain leaves status|amount <off|low|normal|high>|biome <id|clear>` (the biome line
treats every spot as that biome: a showcase aid), `/awaken terrain wind status|force <steady> <gust>|real`,
`/awaken terrain waving on|off|status|debug <0..2>`.

**Measured** (2026-10-24, M4, OpenGL, framebuffer 2880×1658, `PACK=perf,world TECTONIC=awakened`, the heart of the
nearest dark forest by `/awaken terrain visit minecraft:dark_forest`, seed 777; the Mac carried other builders' work,
load 14-32, so frame times swing by a millisecond between identical scenes; logs in `run-autopilot/lands-*-1/`):
- Frame time p50, everything on (waving, falling leaves normal): 5.8-8.5 ms in the cost run (the forced Elderwood
  air at the cap 9.0 ms, load 30) and 7.8-8.0 ms in the first perf run (one pair at load 19 read 11.1 and 11.6 ms,
  leaves on and off alike): under the 9.1 ms Swift budget. In the third run (load 17-31 throughout) every setting,
  leaves off and waving off included, swung between 8.0 and 12.8 ms, so its
  p50s say more about the machine than the feature. Leaves normal vs off and waving on vs off, alternated 2-4 times
  each in all three runs, never differ by more than that swing, in either direction: the frame cost is below what
  `perf` resolves on this machine today, which is why the CPU is counted directly (next line).
- CPU, summed by `/awaken terrain leaves status` (load-proof): spawner + rustle 18-54 µs and particles 5-29 µs a tick,
  wind field + shader texels 38-112 µs and particle drawing 2-26 µs a frame: **0.05-0.13 ms a frame** in all, the
  worst being the forced Elderwood at 168 leaves alive. Most of it is the per-frame texel upload, paid only while the
  waving shader is on.
- Proof sheets: `docs/spikes/falling-leaves/leaves-motion.jpg` (frame-to-frame change: the real wind, a forced gust,
  the settle, Elderwood gold, Lumen motes) and `leaves-look.jpg`, from the leaf studio
  (`docs/spikes/falling-leaves/leaf-studio.txt`: a grove built at y 200 so leaves read against the sky). Tours:
  `lands-proof.txt`, `lands-perf.txt`, `lands-cost.txt` in the same folder.

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
glow costs no alpha-tested overdraw (Apple GPUs) and keeps its colour at every mip level. Three equally weighted variants
(two speckled, one plain) keep the extra quads to about twice a vanilla leaf. Lumen moss does the same on its top.
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
  litter), 5–7 trees a chunk (47% Elder, 50% young, 3% fallen), forest flowers and wildflowers, bushes, grass, ferns
  and large ferns, dense gold litter, mushrooms, pumpkins, cane, fireflies by water.
- **Lumen Forest** (dark forest): grass `#40735f`, foliage `#2f8076`, dry foliage `#5f8a8c`, water `#2a93a6`, fog
  `#a3c4ca`, sky `#7fa3e4`. Vegetation: lumen moss patches (carpet over grass, caps, ferns), 4–6 trees a chunk (55%
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

### Costs (measured 2026-10-24, machine shared with other builds: compare within a run)

- Frame time, `tools/autopilot/lands-perf.txt`, 2880×1800, perf + world pack, Tectonic `awakened`, each Awakened
  forest beside its vanilla archetype in the same run, two passes (p50): Elderwood (dense, beside a Colossus, noon)
  12.78 / 8.62 ms against old-growth birch 12.26 / 7.99; Lumen Forest (night) 8.74 / 8.29 against dark forest 8.35 /
  9.02. Within about half a millisecond of their archetypes. That took trimming: before, 6–8 trees a chunk with 55%
  Elders (about 3,000 leaf blocks a chunk) cost Elderwood 2 ms more than birch.
- Hero blueprints (`terrain_hero_blueprints`, 8 seeds after warm-up): Colossus 3.1–3.8 ms average (worst 5.7) on a
  quiet machine, up to 8 ms while other builds load it; Spire 1.4–2.1 ms (worst 3.6). Built once per tree and cached. Placement into a live level
  (`terrain_hero_builds_in_box`, block updates and light included, so above worldgen's cost): Colossus ~14,500 blocks
  over 16 chunks at 1.5–2.1 ms a chunk on average (worst 7–8), Spire ~3,400 blocks over 9 chunks at 0.6–1.1 ms. The
  Colossus blueprint is above the plan's 2 ms ceiling; it is paid once per tree, on a worker thread.
- Data trees (`terrain_lands_trees_grow`): Elder ~160 logs and ~700 leaves, young elder 24 / 113, Lumen tree 115 / 237,
  small Lumen 35 / 97.

### Known gaps

- Air, sound and the Lens look are other builders' (falling leaves already list `awaken:elder_leaves`; Lumen motes and
  the ambience beds are not in yet). Fireflies and caps are the only night life of Lumen Forest so far.
- Distant Horizons with the hero trees is untested (blueprints are thread-safe and cached, but not measured there).
- The studio's `visit` now prefers a sizeable dry patch; on Tectonic worlds a biome's nearest patch can still be a
  mountainside, so the slice tour shoots Elderwood beside its Colossus.

