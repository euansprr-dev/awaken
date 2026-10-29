# The Living Deep: a full revamp of Awaken's caves

Plan only. Nothing here is built. Written 2026-10-29 by the caves-plan session, following Euan's planning process
(`.claude/PLANNING-PROCESS.md`). Evidence is in `docs/caves/proof/`; the CI tour is `tools/autopilot/caves-today.txt`.
Proposed SPEC section: §42 (free on every branch today).

Euan, 29 Oct 2026: "coming back to mining, it still feels extremely vanilla. Like the cave generation feels very boring.
There's no depth. [...] look at the overworld when there's just like slabs. There's the wild system where it's like it
gets more overgrown. There's a lot of custom blocks. There's a lot of variety, and it feels very alive. [...] caves right
now, they just feel like vanilla still. [...] we need to like plan an entirely new revamp to the caves and the cave
generation system and the ambience to really make it immersive just as much as the overworld is. With custom blocks,
with everything that's needed."

---

## 1. Understanding and the success contract

**The job.** Euan starts a new world, plays it as a player (often in creative to look around first), and goes
underground to explore and mine. He wants the world under the ground to feel like a second world with places of its own,
as alive and as worked-on as the overworld now is.

**The pain.** In his newest world (New World (9), 29 Oct, all of The Singing Deep merged) the caves read as vanilla:
the same grey shapes everywhere, vanilla blocks, nothing growing or moving, an ambience that doesn't carry. "No depth"
means two things in his words and we treat both as required: the caves have no layers of detail (nothing on the
surfaces, no variety), and going deeper doesn't feel like going somewhere.

**The bar he names.** The overworld: the Soft Land's half-block steps and ground cover, the wildness field that
overgrows the wild, the wild's own plants, fireflies and small life, custom blocks, variety. So the caves need the same
four layers: their own shapes, their own blocks and dressing, their own life, their own air.

**His proposed solutions, treated as ideas to test:** a new cave generation system; custom blocks; better ambience. The
audit below supports all three, and adds one he didn't name: light. Most of what makes a cave different can't be seen
in a torch's ten-block bubble.

**Hard constraints** (from the brief, CLAUDE.md and his standing rules):
- It still feels like Minecraft (the Soft Land's line: blocks, pixels, half-block steps and light; no bent surfaces, no
  textures above 16x16).
- Nothing vanilla-looking: every particle, sound and creature has its own look; art comes from `tools/art/specs`.
- Multiplayer-safe: worldgen is deterministic from the seed; client-only life is per client and never matters to play.
- Plain speech in everything the player reads.
- Reuse what works: The Singing Deep (bands, mouths, lodes, the Pick, lamps, styles, the strike, cave air, sound), W5's
  places, Lens looks per biome, held light, the Soft Land's slab tech, the wild plants' block tech, the wildlife layer.
- Costs: SPEC §15's worldgen budgets (any feature ≤ 0.5 ms a chunk on average; chunk generation within 20% of plain
  Tectonic) and his 60 fps on Cinematic (perf pass parked, so this plan must not make it worse).
- Never touch Tectonic's own files; Lithostitched is the precedent for reaching into the generator (`awaken:city_basin`,
  `awaken:eroded`).

**What strong, weak and unacceptable look like.**
- *Strong:* from a single screenshot you can tell which kind of cave you're in. The first three caves you enter from
  spawn are not alike. Floors, walls and roofs carry things. Something glows or moves in most halls. Going from the
  Shallows to the Deep changes the shape of the caves, not only the fog colour. Big caves look big because their own
  light shows their size.
- *Weak:* new blocks sprinkled on the same vanilla shapes; a fog tint per depth; a few rare showpiece rooms.
- *Unacceptable:* caves breaking the surface under roads, villages and the city; flooded or lava-filled regions you can't
  explore; stutter while flying over new land; Far Sight artefacts; vanilla particles; caves so lit that night and
  danger stop mattering.

**Non-goals.** No new dimension. No change to the Descent or gate worlds (they may borrow the vocabulary later). No change
to Tectonic's files, its surface or its mountains. No eighth-layer or smooth terrain. No vein miner. No new tool ladder
(the Delver's Pick stays the tool). No rework of W5's places (they gain the new skin where they sit). The deep dark and
its ancient cities stay vanilla in shape.

**Must not regress.** Mouths and their joints (`delve_cave_routes`), lodes on walls (`delve_lodes_air`), the Hearth's
pointer, W5 places, the city basin, Far Sight's predicted ground, the held-light flood, Lens's underground floor, the
Hollows' spawn rules, chunk generation time beyond the budget.

### The outcome contract (measured, not guessed)

Baselines come from a read-only census of Euan's own New World (9) (9,731 generated chunks, 25.2 million cave cells;
`docs/caves/proof/census.py`, `census-new-world-9.json`). The same census runs on five studio seeds as the acceptance
measure. Targets are proposals with reasons, to confirm in P0.

| Measure | Today (New World (9)) | Target | Why that number |
|---|---|---|---|
| Cave floor in a vanilla stone cave (no cave biome) | 73% overall; **87% in the Shallows**, where the first caves are | ≤ 35% overall, ≤ 40% in the Shallows | the Lands took 40% of land and the land reads as Awaken's; caves need more because nothing else shows down there |
| Cave wall faces that are Awaken's (rock, crust, lichen, formation) | **0.12%** (mostly Deepglass) | ≥ 35% | a region's rock alone gives this where regions are |
| Cave floor faces carrying something (rubble, pebbles, puddle, plant, formation), outside the deep dark | **about 2%** (70k dressing cells on 3.2M floor faces) | ≥ 30% | the surface has something on 56% of its columns |
| Natural light points in cave air | 1.3 per 1,000 cave cells | 4-8 per 1,000 in glowing regions, 0-1 in plain caves | readable halls, dark tunnels; darkness stays a resource |
| Distinct cave kinds met in the first three caves from spawn (five seeds) | 1 (plain stone), sometimes dripstone | ≥ 2 | "every door is different" |
| Chunk generation, pack world | baseline (P0 measures it) | ≤ +20% over plain Tectonic | SPEC §15 |
| Each new feature | — | ≤ 0.5 ms a chunk on average | SPEC §15 |
| Frame, Cinematic, in a busy hall | baseline (Mac perf tour) | ≤ +0.4 ms p50 | his 60 fps goal; perf pass is parked |
| Far Sight's predicted ground | unchanged | bit-identical heights | Far Sight reads the terrain density only |

---

## 2. What really runs underground today (verified audit)

### 2.1 The map

| Component | What it actually does | Evidence | Consequence | Verdict |
|---|---|---|---|---|
| Cave shapes (Tectonic 3.0.31, our `awakened` preset) | `tectonic:caves` = vanilla's own `minecraft:cave_cheese`, `cave_layer`, `spaghetti_2d`, `spaghetti_roughness`, `entrances`, `pillars` and `noodle`, sealed 12 blocks under the ground, plus carvers, underground rivers and lava tunnels | Tectonic jar `data/tectonic/worldgen/density_function/caves.json`, `overworld/final_density.json`; `pack/tectonic/awakened.json` | every cave in the game has vanilla's shapes at vanilla's scales | **extend** (wrap, don't replace) |
| Cave biomes | vanilla's only: dripstone (inland), lush (humid), sulfur (26.3), deep dark (bottom); everywhere else the surface biome runs down to bedrock | `OverworldBiomeBuilder.addUndergroundBiomes`; census by biome | 73% of cave floor is plain stone; lush caves are **0.2%** under the pack's climate scaling | **extend**: add our own cave biomes |
| Rock | stone, deepslate below y 0-8, vanilla's tuff/granite/diorite/andesite blobs | census: 92.2% of wall faces are plain rock | grey everywhere | **add** rock families |
| The Singing Deep §34 | mouths every ~200 m, lodes on walls, thinned ore, the strike, the Pick, Delver lamps, bands, cave air fog, the breath, shafts at mouths, cave styles, Hollows by band, recorded room tones | SPEC §34; code in `delve/` | made the *trip* and *mining* good; by its own non-goals it never touched the generator ("No change to Tectonic", "styles as decoration, no new cave biomes", mining PLAN §1, §4.8) | **keep all**, build on it |
| Cave styles (`CaveStyleFeature`) | 20 random tries a chunk from y -56 to 10 under the ground; most land in rock, so 1-3 small patches a chunk; six styles only under forests, deserts, snow and four Awakened biomes; plains, meadows, hills, jungle-less mountains, savanna, swamp, beaches: **none**; every block it places is vanilla (rooted dirt, hanging roots, cave vines, moss, sandstone, packed ice) | `delve/CaveStyleFeature.java`, `CaveStyle.of` | the only cave dressing reads as vanilla's lush-cave kit, and most caves get nothing | **replace** with region dressing; keep the style→air tint idea |
| W5's places | sinkholes, Delvers' mines, seeps, chutes, grottos: authored, walkable by test, about one per 500 blocks of cave | SPEC §28.5 | excellent, but rare: they can't carry the whole underground | **keep**; they gain the skin around them |
| Bands | three y bands with a fog colour, a caption, a Lens look and a room tone | SPEC §34.5, `delve/api/Bands` | depth changes the tint and the hum, never the shapes or the blocks | **extend**: each band gets its own regions |
| Light | held light floods 10.5 blocks; Lens never lets an underground pixel fall below 85% of the game's own colour; natural light sources are glow lichen, cave vines, lava | SPEC §28.3 held light, §22; census: 1.3 light points per 1,000 cave cells | you see a grey bubble around your torch; any variety beyond 10 blocks is invisible | **add** region light (blocks and drawn light) |
| Air | fog by band (and style), motes only inside your held light, the breath every 3-6 min, light shafts at named mouths only | SPEC §34.5 | between breaths the air is empty | **add** region air |
| Sound | drips at real tips, settling stone, far water, breath of air, the Deep's hum, the band's recorded room tone | SPEC §28.5 senses, §34.5 | good bones; one room tone per band means every cave at y 20 sounds the same | **extend** per region |
| Life | vanilla monsters and bats; the Hollows (drip lurker, chime cricket, hushling) by band; the wildlife layer's 19 kinds are surface-only | SPEC §29, §34.5; `wildlife/` has no underground site | nothing small moves or glows underground | **add** an underground life layer |
| Lens | a look per vanilla cave biome and per band, keyed by biome id | `assets/awaken/lens/looks_delve.json` | the hook for per-region looks already exists | **reuse** |
| Far Sight | predicts far ground from `tectonic:sloped_cheese` only; City Sight snapshots real chunks and closes caves with a curtain | `farsight/FarColumns.java`, SPEC §20 | caves cost Far Sight nothing as long as we stay out of the terrain density | **keep that line** |
| Material rules | 26.3's `MaterialSystem` runs the rule tree on **every** solid block of every column, with the depth to the air above and below | `.ref/src/.../levelgen/material/MaterialSystem.java` lines 155-185 | regional rock and cave floors/roofs can be laid in the pass that already runs, without a feature | **reuse** (key finding) |
| Lithostitched 2.0.6 | wraps density functions and the noise router (our city basin and erosion already do); injects biomes by `replace_partially` with custom density parameters; has `add_points`; `ClimateParameter` includes `depth` | lands and alpine packs; jar class names | the supported way to add cave biomes and cave shapes without touching Tectonic | **reuse** (verify depth-ranged injection in P0) |

### 2.2 The census (New World (9), Euan's newest world, read-only)

`docs/caves/proof/census.py` decoded every generated chunk (9,731; 987 chunks with buildings skipped, mostly the city),
took "cave" as open cells more than five blocks under the column's top rock, and counted what touches them.

- **Wall faces (16.6M):** deepslate 39.4%, stone 38.4%, tuff 3.5%, andesite 3.0%, diorite 2.7%, granite 2.7%, gravel 1.5%,
  dirt 1.0% → **92.2% plain rock**. Everything of Awaken's together: **0.12%** (Deepglass, the Lands' crystals under their
  biomes, the Well).
- **Dressing inside caves:** 1.04M cells, of which 0.89M are the deep dark's sculk and 87k are buildings (mineshafts,
  mines). Natural dressing outside the deep dark: **70k cells for 25.2M cave cells (2.8 per 1,000)**: glow lichen 29k,
  hanging roots 16k, moss carpet 10k, cave vines 2.4k, vines 2.4k. On 3.2M floor faces that is at most about 2%.
- **The surface, same chunks:** 56% of surface columns carry something on top; a fifth of that cover is Awaken's own
  (turf slabs 152k, meadow grass, long grass, bramble, bracken, pebbles, wild rye, clover...).
- **Cave floor by biome** (342k sampled standing spots, `spots-all-new-world-9.jsonl`): plain 73.0%, deep dark 19.6%,
  dripstone 5.1%, sulfur 2.2%, lush 0.2%. By band: the Shallows 87.4% plain, the Middle Dark 77.6%, the Deep 64.0%.
- **Scale is not the problem:** 61% of cave volume is under a roof 9 or more blocks high, 12.6% under 33 or more.
  Tectonic's cheese caves are big. They're the same everywhere and you can't see them.
- **Water:** 10% of cave volume is water (2.57M cells), 2% lava. Any new shape must expect aquifers.
- **What Euan did in it:** 22 minutes, 4 km flown in creative, nothing mined (his stats). He looked around, which fits
  "it just felt very vanilla and boring": this is a first-look judgement, so what you see in the first minutes matters
  most.

### 2.3 What a player meets (CI tour, the same seed)

`tools/autopilot/caves-today.txt` rebuilds New World (9) from its seed (-9010908364552097449) with the pack's terrain
(Tectonic, the `awakened` preset, every Awaken data pack) on GitHub's macOS runner, Lens Cinematic, Bright gamma, a torch
in the off hand. It stands in eight ordinary caves picked from his saved chunks by `spots.py` (the first Shallows cave
by spawn, the Shallows and the Middle Dark under the Elderwood, a Middle Dark cavern, the Deep twice, a lush and a
sulfur cave), each shot as a player sees it and again with night vision to show its shape and blocks; then the Singing
Deep's nearest mouth (outside, its throat, its joint into the cave), the first sinkhole, and the Elderwood's ground above
for the bar. The page shows the shots; what they show is summarised in section 2.4.

### 2.4 Why the caves still read as vanilla (root causes, ranked)

| # | Symptom | Cause | Evidence for | Evidence against / limits | Smallest test | Impact if fixed |
|---|---|---|---|---|---|---|
| R1 | "the cave generation feels very boring" | every cave is vanilla's noise families at vanilla's scales; Awaken never shapes a cave except its rare authored places | Tectonic's `caves.json` names only `minecraft:` cave noises; the mining plan's non-goals | caves *are* big (61% under 9+ roofs), so the fix is variety and vertical structure, not size | one region's shape on a studio seed, cross-section sheets | high |
| R2 | "just felt very vanilla" | the walls are bare vanilla rock: 92.2% plain, 0.12% ours; the only dressing is vanilla's lush kit, in a few biomes, a few patches a chunk | census; `CaveStyle.of` returns NONE for plains, meadows, hills, savanna, swamp, beaches | — | census on a region after its rock and skin | high |
| R3 | "no depth" (no layers of detail) | almost nothing sits on cave surfaces: about 2% of floor faces, against 56% of surface columns | census | sculk makes the deep dark the one dressed place, and it's vanilla's | census after the skin | high |
| R4 | variety exists but isn't seen | caves are dark: 1.3 light points per 1,000 cave cells; the torch shows 10.5 blocks; any difference past that is black | census; held light reach (SPEC §28.3) | Lens's floor keeps the dark readable up close | tour: a lit region hall against today's | high |
| R5 | "no depth" (going deeper isn't going anywhere) | the bands change fog, caption and hum; shapes, rock and life are the same at y 40 and y -40 (bar deepslate) | SPEC §34.5; census by band | deep dark and sulfur are deeper biomes, but rare and vanilla | per-band cross-sections | medium-high |
| R6 | "not alive" | nothing small lives underground: the wildlife layer has no cave site; Hollows are few and hostile | `wildlife/`, SPEC §29, §34.5 | bats exist (vanilla) | a life layer in one region | medium-high |
| R7 | ambience doesn't carry | one fog colour and one room tone per band for the whole world; no particles in the air unless your torch lights motes | SPEC §34.5, §28.5 | the breath and the cave answers are good moments, just rare | per-region air in the tour | medium |
| R8 | the best things are rare | W5's places are ~1 per 500 blocks of cave; mouths every ~200 m lead into plain caves | SPEC §28.5 cost; mining proof | they are the strong existing result; keep them | — | medium (context) |

What would prove this wrong: if the census of a region-dressed seed reached its targets and the tour still read as
vanilla, the cause would be the art (textures, particles), not the systems. That's why the plan puts a style sheet in
front of Euan before textures, and a two-region slice in his hands before the rest.

---

## 3. Alternatives and the recommendation

**A. Dress the caves we have (targeted repair).** Keep every shape; add a dense skin (rubble, formations, lichens,
puddles), our own blocks, an underground life layer and region-less air. Cheap (features only), fast, low risk. Fixes
R2, R3, R6 and part of R4/R7. Leaves R1 and R5: the generation stays vanilla's, every cave the same shape, the bands
still only a tint. He named the generation first, so this alone doesn't answer him.

**B. Cave regions (structural; recommended).** The underground gets its own geography, as the overworld has biomes.
Eight Awakened cave regions, each a cave biome injected across a depth range by Lithostitched, driven by an underground
region field, so you walk from one into another. Each region is a full kit: its own shapes (a density wrapper inside
Tectonic's caves), its own rock and floors (material rules, nearly free), its own dressing and formations (features),
its own light, life, air, sound and Lens look, and its own place in mining. Every cave, region or not, gets A's skin.
Reuses the bands, mouths, lodes, places, the Pick, lamps, Lens hooks, the Soft Land's slab tech and the wild plants'
block tech.

**C. Our own cave generator (most ambitious).** Switch off Tectonic's cheese, spaghetti and noodle in our preset and
generate every cave from Awaken's own density functions and cave-system structures. Total control of shape. But it
re-tunes aquifers, ore placement, ancient cities and trial chambers, the lodes' wall search, the mouths' joints and
mining pace all at once; it throws away vanilla's proven connectivity (every cave joins every other); and it costs the
most generation time. It solves R1 more completely than B, and nothing else better.

**Why B.** R1-R7 are all "no identity, no detail, no light, no life". B gives every one of them a home (the region) and
keeps what already works (vanilla's connected network under it, Tectonic, the Singing Deep). C buys more shape control
than the evidence asks for, at the highest risk. A is B's first phase, so nothing is lost by choosing B: the skin ships
first and helps every cave even before a region exists.

**What would change the recommendation.** If P0's bench shows the density wrapper costs more than the 20% budget even
when it's interpolated and masked to regions, B falls back to "regions by rock, dressing, light, life and air, with
shapes from features only" (still B, with less shape). If Lithostitched can't inject depth-ranged cave biomes, regions
fall back to a 3D region field read by features and material rules (no biome ids), and the Lens looks key off the
field instead.

**Overlooked opportunities this finds.**
- Material rules give whole-volume rock for free: mine into the Ochre Deep and the rock behind the wall is ochre too.
- Cave biomes carry water colour: an orange-stained pool in iron country, a black mirror in the Drowned Galleries, with
  no code.
- The lodes' wall search and the mouths' joint search read the generator's base columns, so they find the new shapes by
  themselves.
- Lens already keys looks by biome id: a region look is one JSON entry.
- Far Sight's predictor never reads the cave density: shapes cost it nothing.

---

## 4. The design

### 4.1 The underground geography

```
            land above (biome, wildness, region rank)      the region field (noise, 300-800 blocks)
                              \                                  /
                               v                                v
   y ≥ 40  THE SHALLOWS   Rootways · Pale Halls · Windcleft · (plain caves, dripstone)
   0..39   THE MIDDLE DARK Ochre Deep · Drowned Galleries · Glimmer Hollows · (plain, sulfur)
   < 0     THE DEEP        Black Vaults · Violet Deep · (plain, deep dark + ancient cities)
              ^ chasms and shafts join the bands inside regions
```

A region is chosen by the land above it (which family can appear) and by the region field (which one does, and where
none does), so caves under a forest tend to the Rootways, but a forest can sit over Pale Halls too. Edges are biome
edges dithered by noise in the rock rules, never a straight chunk line. Coverage target: two thirds of the Shallows and
the Middle Dark, half of the Deep; the rest stays plain caves (with the skin) so regions read as places you enter.

### 4.2 The eight regions

Each is a proposal for Euan's style sheet; names, palettes and kits are his call before textures are made.

| Region | Band, under | Shape (density) | Rock and floor (material rules) | Dressing (features) | Light | Life | Sound and air | In mining |
|---|---|---|---|---|---|---|---|---|
| **The Rootways** | Shallows; forests, jungle, taiga, Elderwood, Lumen Forest | winding galleries 3-6 high, low wide root halls, root pillars floor to roof | rootstone (brown, earthy), loam floors | root curtains that sway, old root arches across passages, bracket fungus (exists), moss in wet places, glowcaps | glowcaps (teal, sparse); daylight through root holes near mouths | moths to your light, beetles on walls, glow-worm threads | creak of roots, soft drips, muffled birds above; drifting spores and pollen | copper and coal lodes favour it; root amber (a find) |
| **The Pale Halls** | Shallows; plains, meadow, savanna, beach (today's emptiest caves) | wide flat-roofed halls on bedding planes, rimstone terraces stepping down slopes | pale limestone and its bedding, flowstone | limestone straws and stalactites, rimstone pools holding still water, cave pearls, wet sheen streaks | glow-worm ceilings (a drawn star field), pools catching it | blind fish in pools, glow-worm threads, crickets heard | echoing drips into pools pitched by the pool, trickles; mist over water | iron in the limestone's red stains; clay; cave pearls (a find) |
| **The Windcleft** | Shallows into Middle; hills, mountains, Shattered Highlands, peaks | tall fissures 2-5 wide and 20-60 high with ledges, wedged chockstones, joining shafts | banded slate, scree | scree slopes of rubble slabs, fallen slabs, rime on high ledges, old Delver ropes and pitons now and then | mica glints in the slate (drawn) | cave swifts nesting in the cracks, roosting bats | wind moaning in the cracks (draughts you can feel as streaks), falling pebbles | gold and iron seams; the Highlands' fault seam |
| **The Ochre Deep** | Middle Dark; any land | layered terraces: stacked galleries with ledges and drops of 3-6, flowstone cascades | ochre stone (warm red-orange, banded), rust crust | rust flowstone, orange-stained drip pools (the biome's water colour), ochre rubble | ember lichen (a faint warm glow) | rust beetles, the chime crickets' chorus | a warm hum, ticking rock; warm dust | iron's home: iron lodes favour it most |
| **The Drowned Galleries** | Middle Dark; rivers, coasts, Mirror Marsh | lake caverns, sumps, half-flooded passages; Tectonic's underground rivers widened | siltstone, mud floors | silt banks (soft slabs), driftwood, pale cave weed, glowing algae at the waterline | the algae (teal-green), water caustics (drawn) | blind fish, cave crayfish, water striders | lapping, flow, drips into deep water; low mist | lapis, clay, copper; legendary fishing |
| **The Glimmer Hollows** | Middle Dark; Glass Steppe, Frost Spires, deserts, badlands | crystal-lined cavities and geode rooms off narrow throats | the land's crystal family (sunshard amber, frostspar blue, amethyst under sand) in host rock | crystal growths (extends today's styles into whole rooms) | crystals glow softly (light 3-5) and glint | glass moths | crystals ring when you pass; glittering dust | the Forge's catalysts; amethyst; redstone |
| **The Black Vaults** | Deep; any land | colossal halls 40-80 high, ranks of basalt columns, chasms to the lava at the bottom | columnar basalt, deepslate, obsidian seams, ash floors | column stumps as natural stairs, cooled lava flows, ash drifts | lava's glow, magma vents (exist), heat shimmer | ash moths, echo bats | deep rumble, lava bubbling, the Deep's hum (exists) | diamond lodes favour it; gold |
| **The Violet Deep** | Deep; any land | warped tight tunnels opening into vaulted chambers; seeps (exist) sit here | seepstone (violet-stained deepslate), Deepglass veins | violet growths, shed chitin, Hollows' nests | Deepglass's faint glow and bloom | the Hollows' home ground | the seep's second voice, hums | Deepglass (tier B); the danger region |

The deep dark keeps its shapes and ancient cities. Vanilla's dripstone, lush and sulfur caves stay at their shares and
get the skin, life and air like every cave.

### 4.3 The skin (every cave, region or not): "the underground Soft Land"

The surface got seams, half steps and ground cover; caves get the same idea in their own materials. Rock-aware, so each
piece is made of the rock it sits on (stone, deepslate, tuff and every region rock).
- **Rubble and half steps:** rubble slabs where a floor meets a wall and on one-block risers (the Soft Land's slab tech
  underground); scree fans at the foot of slopes.
- **Loose stones:** pebbles of the local rock (the ground cover block, rock-tinted), now and then bones.
- **Formations:** stalactites and stalagmites of the local rock (vanilla only has dripstone's), four thicknesses like
  pointed dripstone, where water drips (a wet roof over an open floor).
- **Wall overlays** (thin multiface blocks, like glow lichen): cave lichen in the band's colour, mineral crust
  (efflorescence), wet streaks that catch light.
- **Puddles:** a shallow water skin under drip points (a ground cover, not a fluid), rippling when a drip lands (drawn).
- **Webs:** our own dusty webs with dew glints in dry dead ends (vanilla's cobweb reads vanilla).
- Floors and roofs that don't need a feature (gravel, silt, mud, ash, loam patches by noise) are laid by the material
  rules' floor and ceiling depths, so they cost nothing extra. Carvers' tunnels come after that pass, so they get the
  feature skin only.
- Never on a W5 place's dressed stone, a mine's timber, a lode block, an ore, Deepglass or a player's build.

### 4.4 Light that shows the size of a cave

- **Glowing life and minerals** (real block light 2-7, sparse): glowcaps, ember lichen, glowing algae, crystals,
  Deepglass. Sparse so dark patches stay: monsters still spawn, torches still matter.
- **Drawn light** (no light blocks, no spawn change, like the grotto's glow): glow-worm star fields on high roofs,
  water caustics, mica glints, lava's heat glow on nearby rock. Seen to 48 blocks, so a big hall reads as big.
- **Daylight:** the light shafts the Singing Deep draws at named mouths extend to every cave opening the server knows
  near you (ravines, sinkholes, root holes).
- **Plain caves stay dark**, readable up close through Lens's floor and your own light. The contrast is the point.

### 4.5 Life underground

- **The underlife** (client only, like §29's wildlife: never saved, never synced, no server cost): moths that come to
  your light, glow-worm threads, blind fish, cave crayfish, water striders, beetles and millipedes on walls, cave swifts,
  roosting bats in clusters that scatter as you pass (our own bodies, not vanilla's bat), crickets you hear and sometimes
  see. Sites from the blocks around you (a pool, a wet roof, a crack), capped (about 40 bodies, frame cost ≤ 0.2 ms).
- **Creatures that matter** stay the fauna module's: the Hollows by band and region (the Violet Deep their home), a
  region's signature creature later from the creature sessions (no new hostile mob in this plan).

### 4.6 The air

Per region, through the existing hooks: Lens look by biome id (`looks_caves.json`), cave air fog colour (Depths reads
the region where today it reads band and style), the room tone and a region layer (CaveSound/Conductor), water colour
by biome. Particles of our own (tools/art/specs): spores, pollen, warm dust, glitter, ash, mist over water, drips that
fall and splash, grit near you. Capped per frame; half at Decreased, none at Minimal.

### 4.7 Shapes: the density wrapper

- **Where:** a Lithostitched `wrap_density_function` on `tectonic:caves` (inside Tectonic's interpolation and depth
  seal, before the beardifier), with a vanilla twin on `minecraft:overworld/caves/...` for worlds without Tectonic, as
  `eroded_tectonic.json` / `eroded_vanilla.json` do today. Our function `awaken:caves/shape` returns
  `min(input, region shape)` where a region wants more cave, and lifts the input where a region wants less (fewer
  noodles in the Pale Halls, solid partitions between Windcleft fissures).
- **Shapes:** halls (a low-frequency 3D noise opening tall rooms, with column ranks in the Vaults from a cellular
  pattern), fissures (a ridged 2D noise in x/z gated by depth), terraces (a stepped y function stacking galleries),
  lake caverns (wide low ellipsoids under the local aquifer level), shafts and chasms (sparse vertical features from a
  cell grid joining bands inside regions).
- **Rules:** the seal stays (no new opening to the sky: openings remain the authored mouths and sinkholes, which already
  check roads, places and the city); never inside the city basin's blend; never within 8 of a structure start's box
  (the beardifier still applies after us); the region field is 2D and cached per column, every 3D noise only at
  interpolation corners.
- **Aquifers:** halls under the local water level flood; the Drowned Galleries want that, the others measure their
  flooded share in P0 and lift their floor where it's too wet.

### 4.8 Rock, ores and mining

- **Rock families** (new blocks, art from `tools/art/specs/caves.py`): rootstone, limestone (+ bedding, flowstone),
  banded slate, ochre stone, siltstone, columnar basalt (vaults), seepstone; each with its cobbled/polished/brick set
  only where a place needs it. Each joins `#stone_ore_replaceables` (or the deepslate tag), the strike's rock list
  (chips in its own texture, its own strike sound family), the Echo gift's rock and the cave-ins' roof list.
- **Ores in region rock:** an ore in ochre must not wear a stone background. Recommended: region-rock twins only for
  the ores that live there (iron in ochre and limestone, copper in rootstone, lapis in siltstone, gold in slate),
  composited by the art generator from the rock and the ore overlay, dropping the same items, in the same tags. About 8
  twins, not 7 × 9.
- **Lodes by region:** `LodePlans` favours a region for its metal (iron lodes twice as likely in the Ochre Deep...),
  new worlds only, so old plans agree. The Hearth's pointer names the region ("The old Delvers' iron is in the ochre caves
  under the ridge, 300 m north of your home."); the Tuning Fork and the Pick's hum are unchanged.
- **Discovery:** the first time you enter each region is a discovery and a caption through CaveHud ("THE OCHRE DEEP ·
  iron country"), a Journal line and a Codex page; a region's find (root amber, cave pearls, rust bloom...) is a small
  trophy or Hearth decoration, not a new crafting tree.
- **Danger:** the Hollows follow bands and regions; the Violet Deep is where the Deep presses hardest.

### 4.9 Contracts and state

| Thing | Where | Saved / synced | Lifecycle |
|---|---|---|---|
| Region field, cave biomes, shapes, rock, floors | worldgen data pack `datapacks/awakened_caves`, feature flag `awaken:awakened_caves` (precedent: lands, ground) | the chunk's blocks and biomes | new worlds only; a world without the flag never changes |
| Skin, formations, dressing | features in the same pack | blocks | new chunks of a flagged world |
| Region identity at run time | the biome at the camera (client), the biome at the player (server) | nothing new | read on demand |
| Discoveries | `awaken:discovered/cave_region/<id>` (existing discovery store) | saved per player | once per region |
| Underlife, drawn light, particles | client only | nothing | rebuilt from the world each session |
| Settings | `awaken-caves-client.toml`: `underlife`, `cave_particles`, `drawn_cave_light` (all on) | client | — |
| Versioning | `caves_version` in the pack; a later change to the field or shapes is a new-worlds-only change | — | chunks never seam |

Invariants: no new opening to the sky; no change to the surface height anywhere (Far Sight); never inside the city
basin, a structure's box, a W5 place or a player's build; nothing placed in old chunks; no light block from drawn light;
all player text plain speech.

### 4.10 Failure register (the ones that matter)

| Case | Detection | Containment / behaviour | Test |
|---|---|---|---|
| A region floods (halls below the aquifer) | census flooded share per region on 5 seeds | lift that region's floor; Drowned Galleries exempt | `caves_region_shares` |
| Shapes cut a structure (ancient city, trial chamber, stronghold) | structure boxes vs. carved air | wrapper masked near structure starts; beardifier after us | `caves_structures_intact` |
| Shapes open the surface | base height with and without the wrapper | the seal stays; any diff fails | `caves_surface_untouched` (also guards Far Sight) |
| Chunk generation too slow | bench per chunk, fastest of 8 seeds | P0 go/no-go; fall back to feature-only shapes | `caves_gen_cost` |
| A mouth's joint or a place's joint finds no cave | the existing route tests on the new shapes | they read base columns, so they adapt; any fail blocks | `delve_cave_routes` |
| Lodes no longer face air | the lodes' air test on region rock | the seam walker accepts region rock as wall | `delve_lodes_air` |
| Ores invisible in region rock (stone-backed) | the art sheet | region twins (decision 7) | `caves_ore_twins` |
| Lithostitched missing (no pack) | lands precedent: the modifiers do nothing | caves stay vanilla; client air still runs | `caves_pack_new_worlds_only` |
| Too much light (no spawns, no tension) | spawnable dark share per region | sparse light, drawn light never a block | `caves_dark_share` |
| Particles or underlife cost | Mac perf tour in a hall (Mac only) | caps; settings | perf tour at the end |

### 4.11 Cost

- **Worldgen:** the shape wrapper is the only new noise work: a 2D region field per column (cached) and at most two 3D
  noises per interpolation corner (5 × 49 × 5 corners a chunk), only where a region wants shape. Material rules add a
  few condition checks per solid block in a pass that already runs. Features: the skin ≤ 1 ms a chunk in all, each
  region's dressing ≤ 0.5 ms. P0 measures all of it before anything else is built: the go/no-go is chunk generation
  within 20% of plain Tectonic on five seeds (the same 20% the Lands kept).
- **Far Sight:** the predictor reads `tectonic:sloped_cheese` only, so its far ground is unchanged and costs nothing
  more; City Sight snapshots real chunks, so caves near the surface draw with their curtain as today. New rocks get
  their colour in City Sight's palette (`VoxelKinds`) so a cliff cave shows ochre, not grey.
- **Client:** underlife ≤ 0.2 ms, particles within the existing particle budget, drawn light ≤ 0.1 ms, Lens looks free
  (a look is data). Measured once on the Mac at the end.
- **Disk:** more distinct blocks per section (palettes), no new saved data beyond discoveries.

### 4.12 Existing worlds

Generation is new worlds only, by the feature flag, exactly as the Lands and the Soft Land's floor: an old world's
chunks never change and new chunks there stay vanilla, so no seams. Everything client-side that can run on any world
does: the underlife, particles, drawn light and air read the biome where a region exists and fall back to band and
style elsewhere, so New World (9) gets livelier caves but not new shapes or rock. To see the regions, Euan starts a new
world (as he does after each terrain change).

---

## 5. Before and after (illustrative; proposed behaviour, not built)

| Case | Today (CI tour) | After |
|---|---|---|
| **Ordinary**: the first cave by spawn (Shallows under jungle, y 40) | grey stone tunnel, torch bubble, black beyond | the Rootways: brown rootstone, root curtains swaying, rubble at the wall's foot, teal glowcaps 20 blocks off showing the gallery goes on, moths at your torch, a root creak |
| **The disappointing case**: a big Middle Dark cavern | a huge grey room you can't see, gravel, the same fog as every cave at y 29 | the Ochre Deep: terraces of red rock stepping down, ledges you drop between, ember lichen tracing the far wall, an orange drip pool, the chime crickets' chorus |
| **Hard**: a flooded or deep region | dark water, deepslate | the Drowned Galleries' lake with glowing algae at the waterline and fish in it; or the Black Vaults' column ranks lit by lava from below |
| **Missing data**: an old world, no pack, or Minimal particles | — | old world: vanilla shapes, but the skin-free caves still get underlife, drawn light and air by band; no pack: caves exactly as today; Minimal: no particles, everything else |

Flow, current → proposed:

```
TODAY                                         PROPOSED
Tectonic caves (vanilla noises) ──► rock      Tectonic caves ──► awaken:caves/shape (region field) ──► carve
surface biome runs down        ──► features   cave biomes injected by depth + region (Lithostitched)
CaveStyle (20 tries, vanilla blocks)          material rules: region rock, floors, roofs (same pass)
bands: fog + caption + room tone              features: skin everywhere, region dressing, formations, light
                                              client: underlife · drawn light · region air · Lens look · sound
```

---

## 6. Evaluation and delivery

### 6.1 How we'll know (before tuning anything)
- **The census** (exists: `docs/caves/proof/census.py`) on five studio seeds, from a GameTest-free CI run of a new
  world's chunks, holds every number in the contract. Baseline = today on the same seeds.
- **Cross-section sheets** (a cave studio, like the terrain studio): `/awaken caves survey` writes slices; a script draws
  the region map and side sections per band. Euan sees one sheet per phase.
- **CI look tours** of fixed spots per region (the same tour shape as `caves-today.txt`), before/after on the same seed.
  Builders run no tours by his rule; the orchestrator runs the phase tour if Euan wants shots, otherwise he plays it.
- **GameTests**, one per mechanism (token rule): `caves_gen_cost`, `caves_surface_untouched`, `caves_structures_intact`,
  `caves_region_shares`, `caves_skin_rocks`, `caves_ore_twins`, `caves_dark_share`, `caves_pack_new_worlds_only`; plus
  the existing `delve_cave_routes`, `delve_lodes_air`, `delve_hearth_points`, `terrain_biome_table_parity`.
- **Euan plays it** after P2 (the two-region slice): the human gate on look and feel.

### 6.2 Phases (one builder, narrow vertical slice first)

| Phase | What | Gate |
|---|---|---|
| **P0 Ground truth** | the pack and flag; the region field; the shape wrapper with one shape; depth-ranged cave-biome injection proven; the bench; the census on five seeds | **go/no-go:** chunk generation ≤ +20%, surface untouched, structures intact |
| **P1 The skin, light and life** | rock-aware rubble, pebbles, formations, overlays, puddles, webs on every cave; drawn light; the underlife; cave particles; style sheet to Euan first | census: floors dressed ≥ 30%; Euan's style-sheet OK |
| **P2 Two regions** | the Rootways (Shallows) and the Ochre Deep (Middle Dark): shape, rock, ores, dressing, light, life, sound, Lens look, discovery | **Euan plays a new world**: does it still read vanilla? |
| **P3 The rest** | the other six regions; chasms and shafts between bands | census targets on five seeds |
| **P4 Mining ties** | lodes by region, the Hearth's region line, Journal/Codex/Trails, finds | `delve_hearth_points` and the new region tests |

Rollback: everything sits behind the pack and its flag (a world created without it is untouched), the client parts
behind settings. Any phase failing its gate stops the next.

**Effort (at our pace, from git history):** The Singing Deep's four phases took one session one day; the Wild Lands'
four stages one day plus a plants session. This is bigger in art (about 30 blocks, 8 ore twins, ~10 creatures' small
bodies, ~10 particles, region sounds): roughly P0 half a day, P1 a day, P2 a day, P3 a day and a half, P4 half a day,
so about four to five builder-days. What could change it: Lithostitched's depth injection (P0), the art bar on the
style sheet, and the shape wrapper's cost.

### 6.3 Risks
- **The wrapper's cost** is the one unknown that could reshape the plan: hence P0 first, measured on the pack's world.
- **The art bar**: new rocks and creatures that look vanilla would undo it all; the style sheet goes to Euan before
  textures, as the plants did.
- **Mining balance**: region rock must not slow mining (same hardness as stone/deepslate) and region lodes must keep
  the pacing sim's numbers (`tools/sim/pacing_sim.py` re-run in P4).
- **Seams with other modules**: delve (Depths fog, CaveHud, LodePlans, StrikeBlocks), lens (looks file only), farsight
  (VoxelKinds colours), terrain (biome table rows for the new cave biomes), fauna (Hollows spawn rules by region),
  wildlife untouched (the underlife lives in `caves.client`). Each is a small, named touch.

---

## 7. Decisions for Euan

1. **The approach:** cave regions (B), with the skin first for every cave. *Recommended: yes.*
2. **How much is Awakened:** regions take about two thirds of the Shallows and the Middle Dark and half of the Deep; the
   rest stay plain caves (with the skin) so regions feel like places. *Recommended: yes.*
3. **The eight regions** and their names (Rootways, Pale Halls, Windcleft, Ochre Deep, Drowned Galleries, Glimmer
   Hollows, Black Vaults, Violet Deep), with a style sheet (palettes, sample textures, a particle and a creature each)
   to you before textures are made. *Recommended: yes.*
4. **Light:** regions bring sparse glowing life and minerals plus drawn light that shows a hall's size; plain caves stay
   dark. *Recommended: yes.*
5. **No new openings to the sky:** caves keep Tectonic's seal; the ways in stay the authored mouths and sinkholes.
   *Recommended: yes* (keeps roads, villages and Far Sight safe).
6. **New worlds only for generation**; underlife, particles and air in every world. *Recommended: yes.*
7. **Ore twins:** ores in region rock get their own textures (about eight), same drops and tags. *Recommended: yes.*
8. **Life:** a client-only underlife layer; no new hostile mob here (region creatures later through the creature
   sessions, once one passes your bar). *Recommended: yes.*
9. **Sounds:** region beds from CC0 recordings listed to you file by file before download, as with the Singing Deep;
   synthesised where none fits. *Recommended: yes.*
10. **Budget gate:** chunk generation within 20% of plain Tectonic on five seeds before anything else is built.
    *Recommended: yes.*
11. **Who builds:** this session builds P0-P4 itself, no sub-agents, one phase after another, and stops after P2 for you
    to play a new world. *Recommended: yes.*
