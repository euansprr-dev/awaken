# lands/biomes-3: hand-off (2026-10-24, cloud session)

Frost Spires and Mirror Marsh, the third pair of Awakened biomes, and the awakening share tuned to the plan's 40% of
land (docs/modules/lands.md, SPEC §15). Branch `lands/biomes-3`, from `lands/biomes-2` (2ca1eee, green).

## Done

- **A shared block set.** `terrain.block.BlockSet` holds a block set's registers, its items and its creative tab
  (logs, planks, leaves, saplings, block items); `LandsBlocks`, `GlassBlocks`, `FaultBlocks`, `FrostBlocks` and
  `MireBlocks` all use it instead of four copies of the same helpers. Sunshard and frostspar slivers are one
  `terrain.block.CrystalClusterBlock` (it was `glass.ShardClusterBlock`).
- **Frost Spires** (`terrain.frost`; replaces snowy plains, snowy taiga, grove and ice spikes):
  - Blocks: bluepine wood set (scaled blue-grey bark, ice-blue needles), frostspar (ice crystal with the aurora's
    green, cyan and violet in its seams as emissive faces), frostspar slivers, hoarfrost (frozen ground, soil, holds no
    snow). From a peak the ground reads ice-blue on white.
  - Silhouette: frost spire sheaves (`awaken:frost_spires`), 4–8 square needles bursting every way from one root and
    branching at about 55° like frost on a pane. Never the steppe's hexagonal prisms, never all leaning one way.
  - Trees: the bluepine is a pagoda of narrowing tiers, never a spruce cone. It uses the new `taper` on
    `limbed_trunk_placer`.
  - Air, sound and Lens: snow blown sideways and cold glints at night (`LandsMotes` kind `frost`); `amb.frost_spires`
    (whistling wind, hissing snow, ice chimes); the Lens look `frost_spires`.
  - Landmark: the **Aurora Spire**, a 42–58 frostspar needle on a frozen tarn. It shares the level-site search with
    the Gate Scar (`LandmarkStructure.levelSite`).
- **Mirror Marsh** (`terrain.mire`; replaces swamp and mangrove swamp):
  - Blocks: weepwood wood set (pale silver bark, silver-sage hanging leaves), tendrils, moonbloom (a lily pad with
    glowing petals) and mirepeat.
  - Ground: the whole floor, under water too, from a new `_floor` rule spliced first into the overworld's `surface`.
    Wider still pools than a swamp, water `#11191c` so it reads black and mirrors the sky.
  - Trees: weepwoods stand in the shallows and hang curtains of tendrils that stop at the water.
  - Air, sound and Lens: low mist on still water (`LandsMotes` kind `mist`); `amb.mirror_marsh` (deliberately still);
    the Lens look `mirror_marsh`.
  - Hero tree: the **Weeping Matriarch**, the first hero that stands in water (`HeroTree.wetDepth`,
    `HeroStructure.standing`, `HeroTree.onWater` for its moonblooms). Prop roots, bowing limbs under low domes, about
    45 across.
- **Tests.** `Lands3Tests` covers blocks, trees, biomes, frost features, and mire trees growing in water. The landmark
  and hero tests now cover the Aurora Spire and the Matriarch: shape, build in its box, sites. Numbers are in
  docs/modules/lands.md, "Checks".
- **Two bugs found by the tests, fixed:**
  - 26.3's material rules take a block as an id or an `{"id", "properties"}` map, never `Name`/`Properties`, and
    there is no `all_of` condition. Conditions nest; recorded in SPEC.
  - A blueprint tube thinner than 0.87 can leave a cell touching the rest only at an edge. One Matriarch seed had a
    loose root block. Its tubes are now at least 0.9 thick.
- **The awakening share, 24% → 40.6% of land.**
  - How it was measured: the studio's `/awaken terrain survey` now runs from a server console. I ran it on a
    dedicated dev server here with the pack's world profile (Tectonic `awakened` preset, Lithostitched) on the five
    studio seeds.
  - Before: 24.2% of land with every threshold 0 (18–33% a seed). The six archetype groups cover about 50% of land.
  - The thresholds are now one table, `AWAKENING` in `tools/content/lands_content.py`:

    | Biome | Threshold |
    |---|---|
    | Elderwood | −0.22 |
    | Glass Steppe | −0.27 |
    | Frost Spires | −0.27 |
    | Lumen Forest | −0.34 |
    | Shattered Highlands | −0.34 |
    | Mirror Marsh | −0.41 |

  - Why per biome: forests are the commonest archetype, so Elderwood claims less of them (about 75%) and stays one
    Awakened biome among six. The rare archetypes claim 80–90%.
  - Targets added: Glass Steppe also takes windswept savanna, and Shattered Highlands windswept forest; each is a
    variant of its archetype.
  - After, Tectonic: **40.6% of land** (28–50% a seed). Elderwood 17.4%, Glass Steppe 8.5%, Frost Spires 6.8%,
    Shattered Highlands 4.6%, Lumen Forest 2.5%, Mirror Marsh 0.7%.
  - After, vanilla shapes: 41.5%. `terrain_lands_placement` now fails outside 34–48% and logs each biome's claim of
    its archetypes and the field's deciles.
- The two timed shape tests (`terrain_hero_blueprints`, `terrain_landmark_shapes`) run in batches of their own: in
  the shared batch, other tests' chunk generation pushed the Colossus past its bound on CI (see CI below).
- `/awaken terrain lands2cost` is now `landscost`. It covers every pair's features and landmarks; both biomes-2 tours
  are updated.

## Left

- **The Mac tours** (no GPU in the cloud). Run them in this order:
  1. Looks, a Vulkan contact sheet: `PACK=perf,world TECTONIC=awakened RD=24 tools/autopilot.sh
     tools/autopilot/lands-biomes3.txt 1800`.
     - Shots: noon, golden hour and night, from the ground and from 60 up, plus the Aurora Spire and the Weeping
       Matriarch from 80 and at the foot.
     - Review honestly against the six signatures in docs/modules/lands.md. The snow must blow sideways and the
       frostspar wake at night. The marsh water must read black and mirror the sky, with the mist lying low.
     - Either must not read as a vanilla snowy taiga or swamp re-skin.
  2. Frame time beside the vanilla archetypes: `PACK=perf,world TECTONIC=awakened tools/autopilot.sh
     tools/autopilot/lands-biomes3-perf.txt 1500`.
     - Shots: the densest Frost Spires at its Aurora Spire against vanilla snowy taiga, and the night marsh at its
       Matriarch against swamp, two passes each.
     - Budget: p50 ≤ 9.1 ms at 2880x1800, within about half a millisecond of the archetype.
     - The closing `landscost` line gives the real worldgen cost (≤ 2 ms a shape; ≤ 0.5 ms a chunk for features).
       The frost spires measured 1.2 ms at the fastest in a live level here, which is above worldgen's cost.
  3. The real Awakened share on the Mac, to confirm the 40.6% measured here: `tools/terrain/studio.sh awakened
     survey`.
  4. The biomes-2 tours, if not run yet (docs/handoff/lands-biomes-2.md). They now call `landscost`.
- **The aurora in the sky.** The Lens module has no aurora field. Frost Spires gets the closest grade the look fields
  allow: cold, with green and violet in the split tone. A real aurora (sky bands at night) is the Lens owner's to add.
- **Mirror Marsh is rare on Tectonic's land.** Swamps are only 0.9% of it, so the marsh is 0.7% of land even claiming
  most of them, and three of the five studio seeds have none near spawn. Making it findable means more swamp-like targets or a
  change to Tectonic; that is a design call, not made here.
- **The headless survey** was a scratch script (a dev server per seed, driven over RCON). Committing it would need
  the dev `server` run to take its own game folder, like the client's `-PgameDir`: a `build.gradle` change for the
  owner to decide.
- `hearth_why_not_reasons` (seen once in biomes-2, not terrain) is still unexplained.

## CI

- **Green:** GameTests run 40117471853 on 7f23e88 (https://github.com/euansprr-dev/awaken/actions/runs/40117471853),
  all required tests passed. The full local run on the tuned state passed too (339 required tests, `./gradlew build
  runGameTestServer`).
- **The red run before it:** 40116629951 on 90d3153 failed one test. `terrain_hero_blueprints` measured the Elder
  Colossus at 8.27 ms at its fastest against its 8 ms bound; the Colossus is about 5 ms alone, and its code is
  unchanged here.
  - Cause: the test timed CPU work inside the shared default batch, where the chunk workers generate round a couple
    of hundred other tests on the same cores. This branch's new tests made that batch heavier.
  - Fix (7f23e88): it and `terrain_landmark_shapes`, the other timed test, now run in batches of their own.
