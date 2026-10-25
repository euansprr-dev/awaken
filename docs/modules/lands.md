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
  giants you remember and steer by. Rare: roughly one per 300–500 blocks in their biome. Each one is built once (about
  2 ms for the largest, see Costs), cached for all its chunks, and never writes outside its reserved box.
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
- Every Awakened biome: census test (archetype, fauna, cottage, spirits), a placement test (its injector, ground splices
  and share of land), a worldgen test that its trees and hero structures place on ground without floating or cutting,
  a cost bench (shape costs asserted on the fastest of eight seeds: other work on the machine only ever adds time), and
  a contact sheet (noon, golden hour, night, from the ground and from 60 blocks up).
- `terrain_lands_placement` reads the lands pack's Lithostitched injectors as data: one per Awakened biome, its
  archetype among its targets, only vanilla overworld targets, no target claimed twice, a field that exists; every
  `material_rule/lands/<biome>_surface|_under` spliced first by a modifier of the same name, guarded by its biome. Then
  it applies the injectors to the vanilla generator the way Lithostitched does (first matching injector wins, the field
  sampled at the biome's quart corner) on the five studio seeds, 3,072 blocks round the origin every 96, and logs the
  share of land each Awakened biome takes, how much of its archetypes each claims, and the field's deciles over the
  archetypes' land (for tuning: the field is terrain-blind). Measured 2026-10-24 with the tuned thresholds (SPEC §15):
  41.5% of land on vanilla shapes (36-45% a seed; the archetypes cover 52-55%), of it Elderwood 52%, Glass Steppe
  20%, Frost Spires 10%, Lumen Forest 8%, Mirror Marsh 6%, Shattered Highlands 4%; the test wants 34-48%. With
  Tectonic's `awakened` preset (the `/awaken terrain survey` grids of the five studio seeds, taken on a dedicated
  server with the pack's world profile): 40.6% of land (28-50% a seed), Elderwood 17.4%, Glass Steppe 8.5%, Frost
  Spires 6.8%, Shattered Highlands 4.6%, Lumen Forest 2.5%, Mirror Marsh 0.7% (swamps are rare on Tectonic's land).
  The survey runs from the server console too (round the world spawn).

## The slice as built (2026-10-24)

Everything below is generated by `tools/content/lands_content.py` (assets, loot, recipes, tags, data maps, every lands
pack file but the biome tags) and `tools/art/specs/lands.py` (textures); edit those, never the JSON. Biome tags,
including the hero trees' `awaken:has_structure/<tree>`, come from the biome table (`tools/terrain/biomes.py`, column
`heroes`).

### Blocks (`terrain.LandsBlocks`)

Elder: `elder_log`, `stripped_elder_log`, `elder_wood`, `stripped_elder_wood`, `elder_planks`, `elder_leaves`,
`elder_leaves_gold` (the warm tone of crown tops), `elder_sapling`. Lumen: `lumen_log`, `stripped_lumen_log`,
`lumen_wood` (limbs and boughs: a darker slate bark, so branches read as wood under the pads, not as beams),
`stripped_lumen_wood`, `veined_lumen_log` (the Spire's bark, glowing veins, bark on every face), `lumen_planks`, `lumen_leaves`, `lumen_sapling`, `lumen_moss` (carpet), `lumen_cap`
(cross plant, light 4: the forest floor's only real light, a soft pool round each cluster), `lumen_vines` (hanging,
vanilla `HangingMossBlock`; the tip ends in a glowing bulb). Logs and woods are in `#awaken:elder_logs` /
`#awaken:lumen_logs` → `#minecraft:logs_that_burn`; leaves `#minecraft:leaves`; stripping and fire are NeoForge data
maps (`transformables`, `flammable`). Leaves are plain `LeavesBlock`s with no vanilla falling-leaf particle (the lands'
own particle pool covers them) and no ambient sound. Elder leaves are untinted.

**Glow without overdraw or light.** Lumen leaves are a normal cutout leaf cube plus one-pixel (sides) or 2×2 (top and
bottom) *solid* emissive faces laid 0.05 px in front of the leaf face, sampling the solid `lumen_glow` tile, so the
glow costs no alpha-tested overdraw (Apple GPUs) and keeps its colour at every mip level. Few and bright (two spores on
the top, one under, one on each side, on half the blocks; the other half plain): with the Lens look's bloom they read
as a soft glow, where many small ones read as a starfield. The extra quads come to about 1.6× a vanilla leaf. Lumen moss does the same on its top.
Caps, vine tips and the veined log use vanilla's `cross_emissive`-style overlay layer (few blocks).

### Trees (lands pack `worldgen/feature/lands/*`, Awaken placers in `terrain.tree`)

- `awaken:limbed_trunk_placer`: a 1×1 or 2×2 trunk that may lean (`lean`, `lean_start`), a flared root base
  (`root_flare`, columns that reach down to the ground or are skipped), `limbs` leaving the trunk between `limb_start`
  and the top at golden-angle steps and rising `limb_rise` per block, a twig cross (`twig`) inside every mass, and the
  masses' size (`limb_mass`, `crown_mass` added to the foliage radius). Limbs, roots and twigs are the `branch_provider`
  (bark on all sides).
- `awaken:cloud_foliage_placer`: an ellipsoid mass (domed `height` above, `depth` × height below, `ragged` edge,
  optional `hollow` core, `hang` drooping leaves; `top_provider` gives the part above `top_from` of its height other
  leaves, e.g. a noise-picked warm tone). It only keeps leaves that reach wood within six steps through other
  leaves (a walk out from the logs already in the mass), which is vanilla's decay rule: no leaf is born decaying and no
  leaf hangs loose at the ragged edge (both checked by `terrain_lands_trees_grow`).
- `awaken:hanging_vines` decorator: strands under leaves with air below, `body` down to `tip`.

| Tree | Trunk | Height | Crown | Extras |
|---|---|---|---|---|
| `lands/elder` | 2×2 elder log, root flare | 18–26 | 3–5 limbs from half height, masses r 3–4 on limbs and top: a layered crown; its top gold where a slow noise runs high (`top_provider`, about a third of the wood) | gold litter, shelf mushrooms, moss/podzol ring |
| `lands/young_elder` | 1×1, leans up to 2 | 9–13 | 1–2 short limbs, r 2–3 | litter |
| `lands/lumen` | 2×2 lumen log | 10–16 | 2–4 slate limbs climbing at 45°, broad flat pads r 3–4 over them | a curtain of glowing vines (17% of pad undersides, 2–6 long), lumen moss and caps round the foot |
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
- Placement: Lithostitched `replace_partially` where `awaken:lands/awakening` passes the biome's threshold (SPEC §15:
  Elderwood −0.22, Lumen Forest −0.34): Elderwood for forest, birch forest, old-growth birch and flower forest; Lumen
  Forest for dark forest.

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

- **Elder Colossus**: a trunk 6–9 across that swells to twice that at the ground, deeply fluted there, with a slow
  sway, all bark (`elder_wood`: no end grain on its ledges), ending inside its dome (it never pokes through: on far
  terrain a trunk above the crown read as a thin post); crown top 55–90 above the foot; 6–8 buttress roots arching 9–17 out; one broad
  umbrella dome (radius 24–30) of billowing masses (a big hollow ellipsoid and two or three puffs each) carried on 6–8
  great limbs from 46–76% of the height, two boughs off each, and 4–5 leaders; the upper masses gold. The dome is
  open round the trunk's head and pierced by four shafts between the limbs, so the sky reaches the bark under it (sky
  light beside the trunk 12; under a closed dome 0–6, and the trunk read as a black pole).
- **Lumen Spire**: a three-lobed trunk twisting like rope, 40–52 tall (three times the canopy, so it reads from afar), veined lumen log (glowing veins); 4–6 roots;
  two small pad tiers on the way up; 6–8 slender slate limbs rising then drooping with pads along them and one over the
  middle; a curtain of glowing vines from the crown (24% of the open leaf undersides, 2–7 long, up to 5 longer toward
  the rim).
- Rarity: `random_spread` spacing 26 chunks (Colossus) and 22 (Spire), the site anywhere within 4 chunks of the
  candidate where the biome is right (checked first), the land dry, the footprint level (≤ 3 steps over 10/6 blocks)
  and the root ring within 10/6 of the centre's height. Inside their biome that is roughly one per 300–500 blocks.
- `/awaken terrain hero <tree> [seed]` grows one where you stand; `/awaken terrain herovisit <tree> [distance above]`
  stands you south of the nearest generated one, looking at it (from the sea's surface when that ground is water), and
  logs the sky light beside its trunk.

### Checks

GameTests: `terrain_lands_blocks`, `terrain_lands_trees_grow` (6 seeds of each data tree on a platform: grows, wood
and canopy, no leaf born decaying, nothing loose, reach ≤ 13, height range), `terrain_lands_biomes`,
`terrain_hero_blueprints` (8 seeds each: sizes, the Colossus trunk ending inside its crown, nothing disconnected, few
persistent leaves; cost: the fastest seed ≤ 8 ms, so a loaded machine can't fail it; fastest, average and worst logged),
`terrain_hero_builds_in_box` (a Colossus on a slope and a Spire, chunk by chunk: inside the box, on the ground,
nothing loose, no decaying leaves; cost per chunk logged, not asserted), `terrain_hero_sites` (site search on a real
overworld). Data-tree cost is logged, not asserted.
Tours: `tools/autopilot/lands-slice.txt` (run with `PACK=perf,world TECTONIC=awakened`), far terrain
`tools/autopilot/lands-far.txt` (`PACK=perf,horizon,world DH=swift`: the hero trees from 300–600 blocks), frame times
`tools/autopilot/lands-perf.txt`, light probe `tools/autopilot/lands-probe.txt`.

**Lens: Swift and Cinematic.** The two looks differ only by Cinematic's light shafts (`lens_shafts`), which need the
sun on screen and open sky round it. Looking away from the sun, or at golden hour with the sun behind the canopy or
below the hills, the two shots are the same image; the slice tour shoots the A/B into the afternoon sun (time 11000,
west, 18° up) through the Elderwood canopy.

### Costs (measured 2026-10-24, machine shared with 3 other builds: compare within a run)

- Frame time, `tools/autopilot/lands-perf.txt`, 2880×1800, perf + world pack, Tectonic `awakened`, each Awakened
  forest beside its vanilla archetype in the same run, two passes, p50 (load in brackets). After the polish pass:
  Elderwood (dense, 45 blocks from a Colossus, noon) 10.22 (29) / 14.49 (37) ms against old-growth birch 9.53 (28) /
  12.66 (37); Lumen Forest (night) 12.59 (32) / 11.56 (28) against dark forest 13.54 (34) / 10.94 (26). Lumen stays
  within a millisecond of its archetype either way; Elderwood beside a Colossus is now 0.7–1.8 ms over birch (before the
  polish 0.5–0.6, with a Colossus half the size: its dome is ~28,600 blocks now, see-through leaves). Absolute numbers
  sit above the 9.1 ms Swift budget only because of the load; the earlier quiet runs were 5.8–8.5 ms. Before the slice
  trimmed them, 6–8 trees a chunk with 55% Elders (about 3,000 leaf blocks a chunk) cost Elderwood 2 ms more than birch.
- Hero blueprints (`terrain_hero_blueprints`, 8 seeds after 3 warm-up builds, four runs): Colossus 1.82–3.13 ms for
  the fastest seed, 3.8–5.4 average, 6.1–11.7 worst; Spire 0.56–0.88 fastest, 0.67–1.14 average. The Colossus is
  ~26,000 cells (twice the first one); at ~70 ns a cell (written once, every leaf visited once by the distance pass, no
  per-cell hashing or trig outside the masses' rims) that is 1.8 ms on a quiet core, and it rises with machine load.
  Reliably under 2 ms would mean a smaller crown, which is the thing it must not be. It is paid once per tree (one
  per 300–500 blocks), cached for all its 25 chunks, on a worldgen worker thread.
- Placement into a live level (`terrain_hero_builds_in_box`, block updates and light included, so above worldgen's
  cost): Colossus ~28,600–29,500 blocks over 25 chunks at 1.7–2.1 ms a chunk on average (worst 5.7–6.5) on a quieter
  machine, 4–7 ms (worst 19–34) while three builds run; Spire ~3,600 blocks over 9 chunks at 0.9–1.6 ms (worst 3–7).
- Data trees (`terrain_lands_trees_grow`): Elder ~160 logs and ~700 leaves, young elder 24 / 113, Lumen tree 92 / 272
  (fewer, shorter limbs than the slice's 115 / 237), small Lumen 36 / 96.

### Known gaps

- Air, sound and the Lens look are other builders' (falling leaves already list `awaken:elder_leaves`; Lumen motes and
  the ambience beds are not in yet). Fireflies and caps are the only night life of Lumen Forest so far.
- The pale flat land beside the Lumen canopy in the first slice sheet's golden-hour view from above is vanilla
  `minecraft:beach` (dry sand at sea level, `/awaken terrain at` and a survey of that view), not a seam: the Awakened
  forests still meet vanilla shores directly. A shore treatment of their own is for a later biome pass.
- Far terrain (Distant Horizons, `DH=swift`): the Colossus's dome reads on the horizon at 450–600 blocks as one broad
  umbrella (its LOD keeps the shape, flattened, and the colour); the Spire is thin and faint at 300 blocks and lost
  behind terrain at 450 (first check, before it grew to 40–52); at night its glow does not carry that far.
- The studio's `visit` now prefers a sizeable dry patch; on Tectonic worlds a biome's nearest patch can still be a
  mountainside, so the slice tour shoots Elderwood beside its Colossus.


## The second pair as built (2026-10-24): Glass Steppe and Shattered Highlands

Generated like the slice: blocks, models, loot, recipes, tags, data maps, lang and every lands pack file by
`tools/content/lands2_content.py` (called from `lands_content.py`, so one run writes both), textures by
`tools/art/specs/lands2.py` (`python3 tools/art/build_all.py lands2`; preview `python3 tools/art/specs/lands2.py preview
<dir>`), the two ambience beds by `tools/art/specs/audio_lands.py`. Code: `terrain.glass` (Glass Steppe), `terrain.fault`
(Shattered Highlands), `terrain.landmark` (the Gate Scar and the Shard Drift, `awaken:landmark`), `terrain.client.LandsMotes`
(their air). Biome table rows, looks and air rows as for the slice.

### Glass Steppe (Sunscorch; replaces savanna, savanna plateau and windswept savanna in the field)

Plains melted to glass where gates broke long ago.

- **Blocks** (`terrain.glass.GlassBlocks`): `glassbark_log`, `stripped_glassbark_log`, `glassbark_wood`,
  `stripped_glassbark_wood`, `glassbark_planks`, `glassbark_leaves`, `glassbark_sapling` (bleached driftwood bark with
  beads of amber resin turned to glass; amber leaves, fixed colours, cutout, not translucent); `fused_glass` (the bare
  ground: an ivory glaze broken into a crackle of plates, three variants turned every way); `sunshard` (glass grown into
  crystal: flat facets, an amber heart, two or three amber specks per face as emissive model faces); `sunshard_cluster`
  (slivers of sunshard in the grass, cross model with glowing tips); `scorched_glass` (black glass split by amber cracks:
  each straight run of a crack is one emissive face, 6-7 a face). No block light anywhere.
- **Ground**: sun-gold grass (`#cfb35a`, foliage `#c8a24a`), fused glass where the lands pack's noise `lands/glass`
  passes 0.3 (patches tens of blocks across, about a fifth of the ground) with a second layer of glass and sandstone
  under it, coarse dirt in dusty streaks: a material rule (`worldgen/material_rule/lands/glass_steppe_surface`,
  `_under`) spliced first into the overworld's `biome_surface` and `under_biome_surface` by Lithostitched
  `set_material_rule` modifiers, so the surface pass lays it and far terrain shows it.
- **Silhouette**: glass shard spires (`awaken:glass_shards`): clusters of 3-7 hexagonal sunshard prisms 8-14 tall (a
  small kind 3-7), all leaning roughly one way as if one blast threw them, glass spattered round their feet; one in four
  chunks, the small ones one in two. Every spire's foot reaches down to the ground.
- **Trees**: *Glassbark* (1x1, leaning, 2-3 rising limbs, flat amber pads: an acacia's sweep in bleached wood) and a
  *tall* one (3-4 limbs), with `limbed_trunk_placer` and `cloud_foliage_placer`; a fallen glassbark now and then. Half a
  tree a chunk: it stays a steppe. Saplings grow them.
- **Mirror pools** (`awaken:mirror_pool`, one in seven chunks, before vegetation): shallow water on a bed of scorched
  glass (dark and glossy by day, the amber cracks glowing up through it at night), ringed with fused glass and slivers;
  the water's level is the rim's lowest ground, and a pool only forms where the ground is level enough to hold it.
- **Air**: glints, by day: where the sun reaches glass (`#awaken:lands/glints`) a four-pointed star blinks on the face
  the camera sees (`LandsMotes`, kind `glint`); amber leaves off the glassbarks.
- **Night**: the spires' amber specks, the scars' and pools' amber cracks, the clusters' tips.
- **Sound**: `amb.glass_steppe` (warm wind over dry grass, glass ticking and ringing faintly as it warms), the biome's
  `minecraft:audio/ambient_sounds` loop.
- **Lens** (`glass_steppe`): bleached and high-key (exposure 1.04, saturation 0.92), warm, a strong sun glow (1.25),
  heat haze starting close (0.46 from 0.22).
- **Landmark: the Gate Scar** (`terrain.landmark.GateScar`): a bowl 30-50 across and 7-10 deep, black scorched glass at
  its heart and in radial cracks running out through the ivory glass, a raised rim, 8-12 rays of splashed glass out
  across the grass to nearly twice its radius, 10-16 sunshard spires round the rim leaning outward (three or four of
  them 18-28 tall), and in the middle the gate's broken ring of deepslate stumps, one fallen across the floor, sunshard
  growing out of them, one crystal standing where the gate stood. The ground is reshaped column by column from each
  column's own ground and its distance from the centre (chunks agree without seeing each other); the voxels then go into
  air, plants and water only, standing columns continued down to the ground. Sites: dry steppe at least 12 above the
  sea (the bowl stays dry), ground within 5 at 16 out and 8 at 28 out. `random_spread` spacing 28, separation 10.

### Shattered Highlands (Crag; replaces windswept hills, windswept gravelly hills, windswept forest and stony peaks in the field)

Mountains split along lines of light.

- **Blocks** (`terrain.fault.FaultBlocks`): `faultstone` (dark violet slate split by jagged cyan cracks: the cracks
  are emissive model faces, about 8 a face, with a violet bleed in the base texture so the band reads violet from far
  off), `fault_seam` (crystal split open on light: the whole cube emissive, no block light), `riven_shale` (layered
  blue-grey rock with a rust band, a pillar block).
- **Ground**: riven shale on steep slopes and over about half of the rest (the vanilla `surface` noise), a little
  gravel scree, cool sage grass (`#7c8b68`) on the flats (material rule as above).
- **Faults** (`terrain.fault.FaultLinesFeature`, `awaken:fault_lines`, once a chunk before vegetation): a fault runs
  along the zero line of the pack's noise `lands/fault` (base octave -8 roughened by four finer octaves, so lines run
  jagged like cracks, about 5 crossings per km of travel). A column's distance to the line is the noise over its slope
  (the noise sampled on a 2-block lattice and blended), so a fault keeps its width wherever it runs; a threshold on the
  noise itself swelled into fields where the noise was flat (measured: 20-block runs, 120 at worst). Within 1.6 blocks
  of the line: faultstone three deep, the seam within 0.55 (the line's glowing core). Where the noise `lands/fault_trench`
  rises the fault widens (to 4.4 either side) and opens into a V-shaped trench up to 9 deep, its floor and the walls the
  deeper columns beside it expose relined. Every decision comes from the noises and the column itself: a fault runs on
  across chunk borders whole, and never reads what a neighbour chunk did. Never into water, never down to the sea; snow
  never settles on faultstone or the seam (`#minecraft:cannot_support_snow_layer`), so the lines run on through snow.
- **Silhouette**: rock shards hanging over the faults (`awaken:floating_shard`, tried in one chunk in two, built only
  where a line passes within six blocks and only into open air): an upturned point of riven shale 2-4 across,
  faultstone in its lower half, the seam at its tip, turf on one in three, 7-20 above the ground; a chip of rubble
  beside it. Sparse spruce and pine, riven shale boulders.
- **Air**: at night sparks rise off the faults (`#awaken:lands/faults`), cyan and violet, flickering and bending with
  the wind; by day grains of dust ride the wind low over the rock (`LandsMotes`, kind `fault`).
- **Sound**: `amb.shattered_highlands` (a cold high wind, the faults' low hum of two close tones beating slowly,
  rock crackling and ticking).
- **Lens** (`shattered_highlands`): crisp and clear (haze 0.24 from 0.55), contrast 1.14, violet-tinged shadows and
  split tone, bloom from 0.52 so the faults bloom at night.
- **Landmark: the Shard Drift** (`terrain.landmark.ShardDrift`): 7-12 floating shards strung along a fault: a great
  one 10-13 across over the line itself, 20-28 up, smaller ones before and after it rising toward the drift's ends like a
  wave, each the upturned point of shale and faultstone with the seam's light at its tip, a third grassed with roots
  hanging from the rim, rubble drifting between. The site search walks onto the line along the fault noise (Newton
  steps); the start samples the ground under each shard once, so every chunk builds the same drift at the same
  heights. Everything here floats on purpose. `random_spread` spacing 22, separation 8.

### Commands

`/awaken terrain landmark <gate_scar|shard_drift> [seed]` builds one where you stand; `/awaken terrain landmarkvisit
<landmark> [distance above]` stands you south of the nearest generated one, looking at it; `/awaken terrain landscost`
prints what the Awakened lands' worldgen has cost since the server started (calls, average and total per feature and
landmark, measured on the worldgen threads as chunks generate).

### Checks

GameTests: `terrain_lands2_blocks`, `terrain_lands2_trees_grow`, `terrain_lands2_biomes`, `terrain_glass_features`
(spires stand and stay near their centre, pools hold their water and never spill), `terrain_fault_noise` (the studio
numbers: band share, width, crossings per km, how much opens), `terrain_fault_features` (a real open stretch of fault
on nine chunks painted one by one: only band columns touched, floors lined, the seam on the line, depth within the V, no
bigger step at a chunk border than inside one; shards in open air, well clear of the ground, the seam at their tips),
`terrain_landmark_shapes` (eight seeds each: a scar 30-50 across and 7-11 deep with 8-12 rays, its spires, the gate's
stumps and the crystal where the gate stood, nothing standing unjoined; a drift's shards, the great one, the seam at
the tips, every shard hanging; the fastest seed within the 2 ms hero budget: 0.63 ms a scar and 0.90 ms a drift in the
cloud VM, 2026-10-24), `terrain_landmark_builds_in_box` (a Gate Scar on a slope and a Shard Drift chunk by chunk:
everything inside the box, the crystal standing on the ground, the heart deep and black, the drift's lowest rock clear of
the ground), `terrain_landmark_sites`. Tours: `tools/autopilot/lands-biomes2.txt` (looks),
`tools/autopilot/lands-biomes2-perf.txt` (frame times and worldgen cost).

## The third pair as built (2026-10-24): Frost Spires and Mirror Marsh

Generated like the others: blocks, models, loot, recipes, tags, data maps, lang and every lands pack file by
`tools/content/lands3_content.py` (called from `lands_content.py`), textures by `tools/art/specs/lands3.py`
(`python3 tools/art/build_all.py lands3`; preview `python3 tools/art/specs/lands3.py preview <dir>`), the ambience beds
by `tools/art/specs/audio_lands.py`. Code: `terrain.frost` (Frost Spires), `terrain.mire` (Mirror Marsh), the Aurora
Spire in `terrain.landmark`, the Weeping Matriarch in `terrain.hero`, the air in `terrain.client.LandsMotes`. The
blocks of all three pairs past the slice register through one `terrain.block.BlockSet` (a block set's registers, its
items and its creative tab), and every crystal sliver (sunshard, frostspar) is a `terrain.block.CrystalClusterBlock`.

### Frost Spires (Frostwild; replaces snowy plains, snowy taiga, grove and ice spikes in the field)

Where an old gate froze, the cold grew into crystal.

- **Blocks** (`terrain.frost.FrostBlocks`): `bluepine_log`, `stripped_bluepine_log`, `bluepine_wood`,
  `stripped_bluepine_wood`, `bluepine_planks`, `bluepine_leaves` ("Bluepine Needles"), `bluepine_sapling` (blue-grey
  bark in uneven scaled plates with dark fissures between, never a grid, frost caught on some lips; ice-pale heartwood;
  short deep ice-blue needles frosted at their tips); `frostspar` (ice grown into crystal: long straight facets in pale
  ice-blue, two seams of the aurora's green, cyan and violet light running through it as emissive model faces);
  `frostspar_cluster` (four thin needles splayed from one spot, the aurora at their tips); `hoarfrost` (frozen ground
  under feathers of frost: a pale ice-blue crust with two or three frost ferns; soil, so saplings root in it). No block
  light anywhere.
- **Ground**: hoarfrost where the pack's noise `lands/rime` passes −0.3 (drifts tens of blocks across, most of the
  ground), grass under snow between (`worldgen/material_rule/lands/frost_spires_surface`). Snow never settles on
  hoarfrost or frostspar (`#minecraft:cannot_support_snow_layer`), so from a peak the biome reads ice-blue on white,
  never a vanilla snowfield. Grass `#8fb1aa`, foliage `#5f8fa8`, water `#3f76c9`, fog `#d6e6f4`, sky `#86b6ee`.
- **Silhouette**: frost spires (`awaken:frost_spires`): a sheaf of 4–8 frostspar needles 9–16 tall bursting from one
  root, the tallest near upright, the rest splayed every way up to half a radian, the big ones branching once or twice
  at about 55° as hoarfrost's dendrites do on a pane; square needles turned 45° to a point, never the steppe's hexagonal
  prisms, never all leaning one way. The root freezes the ground round it to hoarfrost with slivers in it. One in three
  chunks; a small kind (2–4 needles, 3–7 tall) one in two; frostspar slivers scattered in the snow.
- **Trees**: *Bluepine* (1×1, 11–16 tall: six to nine tiers of short level limbs spiralling up a dark trunk, flat blue
  pads drooping at their rims, the tiers narrowing toward the top: a pagoda, never a spruce's solid cone) and the
  *giant bluepine* (2×2, 22–32 tall, nine to twelve tiers), both on `limbed_trunk_placer` with its new `taper` (each
  limb shorter and its mass smaller the higher it leaves the trunk); a fallen bluepine under snow now and then; 1–3 a
  chunk, so the spires have their clearings. Saplings grow them (four: the giant).
- **Air**: snow blown sideways on the wind, day and night, born upwind and streaming past, thicker in the gusts; at
  night cold glints wake on the frostspar (`#awaken:lands/frost_glints`) (`LandsMotes`, kind `frost`); a few needles.
- **Night**: the frostspar's aurora seams on every spire, sliver and the Aurora Spire, the brightest thing in the snow.
- **Sound**: `amb.frost_spires` (a thin wind whistling through the spires, snow hissing over the crust in the gusts,
  high ice chimes far apart, the odd deep creak of ice settling).
- **Lens** (`frost_spires`): cold and clear (warmth −0.14, haze 0.30 from 0.40, tinted ice-blue), blue shadows, the
  aurora's green and violet in the split tone (0.24), bloom from 0.50 so the spires bloom at night. The aurora in the
  sky itself is the Lens module's (it has no aurora field yet): flagged in the hand-off.
- **Landmark: the Aurora Spire** (`terrain.landmark.AuroraSpire`, `awaken:landmark` `"landmark": "aurora_spire"`): a
  frostspar needle 42–58 tall standing from the middle of a frozen tarn 17–27 across, four to six lesser needles 16–34
  long leaning out round its foot, every big one branching into side crystals at about 55°, sheaves of small spires
  round the tarn's rim and slivers on the ice. The tarn is packed ice level with the centre's ground, cut down to it or
  built up to it column by column (at most 5), at least two layers thick, a ring of hoarfrost round it with its snow
  cleared; the voxels then go into air, plants, snow and water only, standing needles continued down to the ice or the
  ground. Sites: dry, level snow (within 4 at 12 out and 6 at 20 out) at least 2 above the sea, 70 under the build
  limit (`LandmarkStructure.levelSite`, shared with the Gate Scar). `random_spread` spacing 26, separation 9.

### Mirror Marsh (Mire; replaces swamp and mangrove swamp in the field)

Still black water that holds the sky.

- **Blocks** (`terrain.mire.MireBlocks`): `weepwood_log`, `stripped_weepwood_log`, `weepwood_wood`,
  `stripped_weepwood_wood`, `weepwood_planks`, `weepwood_leaves`, `weepwood_sapling` (smooth pale bark, silver going
  green in the damp, dark lenticels; cream-green heartwood; long narrow silver-sage leaves hanging down);
  `weepwood_tendrils` (a hanging strand of tiny paired leaflets, a pale catkin at the tip; vanilla's hanging moss block);
  `moonbloom` (a round dark pad notched to its middle, a pale six-petalled flower on it whose petals glow as emissive
  model faces; floats on water like a lily pad); `mirepeat` (dark wet peat with long threads of silver moss wandering
  over it; soil). No block light anywhere.
- **Ground**: the whole floor is the marsh's (`worldgen/material_rule/lands/mirror_marsh_floor`, spliced first into the
  overworld's own `surface` rule, guarded by the biome, so the bed under the water is peat too): wide still pools at
  the sea's edge (the swamp's own rule, nested as vanilla nests it, where `minecraft:surface_swamp` passes −0.2 instead
  of 0: more water), mirepeat over most of the dry ground (`lands/peat` past −0.45), the marsh's dark grass between,
  mirepeat under all of it (`_under`). Water `#11191c` and water fog `#030607`: the pools read black and mirror the sky.
  Grass `#4f6250`, foliage `#5d6f5c`, fog `#9fb0ac`, sky `#8aa3c0`.
- **Silhouette**: weeping domes over their reflections; once in a while a **Weeping Matriarch**.
- **Trees**: *Weepwood* (1×1, 7–11 tall, leaning from a swollen foot, three or four rising limbs under low domes, a
  curtain of tendrils from a third of the dome undersides, 3–8 long) and the *great weepwood* (2×2, 9–15, four or five
  limbs, tendrils 4–10; four saplings in a square grow one); a fallen weepwood under moss. They stand in the shallows as swamp oaks do (water up to two
  deep), 1–3 a chunk; the tendrils stop at the water's surface (the decorator hangs them into air only). Saplings grow
  them on peat, mud, dirt or grass.
- **Moonblooms** in place of lily pads, on the still water.
- **Air**: mist: soft wisps lying just over open water, most at night and dawn (`LandsMotes`, kind `mist`); silver
  leaves off the weepwoods.
- **Night**: deliberately dark: the moonblooms' pale petals on black water, nothing else.
- **Sound**: `amb.mirror_marsh`, deliberately still (a low soft drone of two near tones breathing, the faintest air,
  slow drips into still water, once or twice a far hollow call).
- **Lens** (`mirror_marsh`): still and dark (exposure 0.98, contrast 1.14, saturation 0.88, vignette 0.24), a
  silver-green mist low and close (haze 0.50 from 0.18, desaturated), bloom from 0.52 so the moonblooms bloom.
- **Hero tree: the Weeping Matriarch** (`terrain.hero.WeepingMatriarch`, `awaken:hero_tree` `"tree":
  "weeping_matriarch"`): the marsh's mother tree, standing in its own black water. A pale gnarled trunk 4–6 across,
  leaning, 22–30 tall, lifted on a cage of six to nine prop roots arching out of the water all round it; five to seven
  great limbs arching out 12–17 blocks and bowing at their ends, each under a broad low dome of silver leaves, and one
  dome over the trunk; from every dome a curtain of tendrils falls toward the water, longest at the rim. From far off the
  crown reads as one weeping dome about 45 across over its reflection. Every root and limb is at least 0.9 thick: a
  thinner tube can leave a block joined to the rest only at an edge. It is the first hero tree that stands in water
  (`HeroTree.wetDepth` 3: the site's water at most three over the ground and the ground no deeper than that below the
  sea; `HeroStructure.standing`); its strands end at the water's surface, the last one above it a tip, and moonblooms
  float under its crown (`HeroTree.onWater`), mirepeat round its feet where they stand dry. `random_spread` spacing 24,
  separation 9.

### Commands

`/awaken terrain landmark aurora_spire [seed]` and `/awaken terrain hero weeping_matriarch [seed]` build one where you
stand; `landmarkvisit` and `herovisit` find the nearest. `/awaken terrain landscost` (was `lands2cost`) prints what the
Awakened lands' worldgen has cost since the server started, the frost spires and the Aurora Spire's shape included.

### Checks

GameTests (`terrain.landmark.Lands3Tests` and the shared landmark and hero tests): `terrain_lands3_blocks`,
`terrain_lands3_trees_grow` (six seeds of each data tree: grows, nothing loose or born decaying, reach and height; the
bluepines' tiers narrow toward the top, the weepwoods hang curtains; measured in the cloud VM 2026-10-24: bluepine 50
logs / 278 leaves, top 14, reach 7; giant 200 / 475, top 29, reach 9; weepwood 45 / 403 and 164 tendrils; great
weepwood 115 / 620 and 338 tendrils), `terrain_lands3_biomes`, `terrain_frost_features` (eight sheaves on snow: every
needle joins the ground, within 13 of the root, needles on at least two sides of it, hoarfrost round the root; 159
blocks, 1.2 ms at the fastest in a live level), `terrain_mire_trees_in_water` (weepwoods grown with their foot in water:
whole, and no tendril in the water; moonblooms float and never sit on peat), `terrain_landmark_shapes` (the Aurora
Spire too: tarn, height, frostspar, the great needle in the middle, nothing cut off at the grid's edge, nothing
unjoined; 0.72 ms at the fastest), `terrain_landmark_builds_in_box` (an Aurora Spire on a slope: its tarn level ice at
the centre's ground across all of it, the hoarfrost ring whole, nothing floating; 3,084 blocks over 9 chunks, 0.84 ms a
chunk), `terrain_landmark_sites` (level snow: 26 of 40 candidates on seed 777), `terrain_hero_blueprints` (the
Matriarch: trunk top, prop roots, crown wider than it is tall, its tendrils; 3.5 ms at the fastest),
`terrain_hero_builds_in_box` (a Matriarch in two blocks of water: inside its box, standing on the peat, no tendril in
the water, moonblooms under its crown), `terrain_hero_sites` (swamps: 6 of 10 candidates). Tours:
`tools/autopilot/lands-biomes3.txt` (looks), `tools/autopilot/lands-biomes3-perf.txt` (frame times beside snowy taiga
and swamp, and `landscost`).
