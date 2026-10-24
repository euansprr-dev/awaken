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

- **Data trees** (vanilla trunk and foliage placers, Awaken logs and leaves): the common trees. Canopies stay within
  about 12 blocks of the trunk (a feature may write one chunk beyond its own). Variety comes from 2–3 sizes and shapes
  per biome, never one tree copied.
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
