# The Singing Deep: mining, caves and the pick, rebuilt (plan for Euan's approval, 2026-10-28)

Approval page: https://claude.ai/artifact/Jp3EjBR1Cb4XqBjSN6zU9x (private; share from its Share menu). Plan only. Nothing here is built. Euan's message of 28 Oct is the brief (quoted in full in the session that wrote this);
the process is `.claude/PLANNING-PROCESS.md`. Evidence lives in `docs/mining/proof/` (a probe run on read-only copies of
Euan's own world). SPEC sections this builds on: §28.3 (W2, the deep's metal), §28.5 (W5, cave places), §21.9 (the Mining
craft), §22 (Lens), §24 (Hands), §27.11 (the Now corner), gear (`docs/modules/gear.md`).

---

## 1. What Euan needs, and how we'll know we got it

**The job.** After the first house, go and get metal (iron first: Hearth Tier II needs 16 and 4 lanterns, the Forge 7,
the D kit about 13) and have that trip feel as good as a gate run: a cinematic, immersive descent with a reason to go,
something to fight, and a tool that grows.

**The pain, in his words and in his save.**
- "I don't know how to find a cave": he couldn't see a mouth to walk into, so he dug a staircase.
- "It just had no motivation to dig down in a straight line and find iron": his save shows the trip: 46 stone, 10 iron
  ore, 2 coal, **7 iron ingots, then he stopped**, short of Tier II's 16.
- The break particles "look like boring round circles", "not AAA at all".
- "No real progression system for mining tools", and it mustn't be the logical kind ("break 20 blocks at once").
- Caves aren't cinematic: atmosphere, fog, the colour of the air, sound, fights.

**What he wants instead.** Going underground should feel like "entering a new world", the way the plates over mobs and
the blue XP motes make the surface feel. Magical, not logical. A true RPG.

**His proposed fixes, taken as ideas to test:** much better or more minimal particles; fog and colour in the air;
sound; cave styles; a magical tool progression. All are in the plan, each because the audit says it's a real cause.

**Hard constraints.** AAA look, never vanilla particles or reuse; plain spoken text; server authoritative and
multiplayer-safe; the SPEC header's budgets on an M4 (16 GB, Sodium, Lens Cinematic); no mixins unless unavoidable
(none needed here); his current world keeps working.

**Success, as things you can see.**
| | Strong | Weak | Unacceptable |
|---|---|---|---|
| Finding a cave | from any Hearth on new land, a mouth that announces itself is within 200 m, and the Hearth tells you where | mouths exist but you still have to stumble on them | you dig a staircase because nothing else is better |
| Getting iron | one 15-25 minute trip through a cave gives the 20+ iron Tier II needs; a staircase gives clearly less | caves and staircases pay the same | caves pay less |
| The strike | every bite reads as stone and metal: chips, short streaks, dust, its own sound and motion | prettier blobs | round circles; vanilla crack particles underneath |
| The tool | one pick that changes how you explore as it wakes, earned in caves | a stat ladder with a new skin | anything that breaks a room at once |
| The air | each depth has its own air, light and sound; one cavern you light up stays lit | a darker filter | fog that hides everything or costs frames |
| Cost | ≤ 0.3 ms a frame for the air and particles; worldgen within 5% of today | | a visible hitch in a fight or on chunk load |

**Non-goals.** No vein miner or area mining. No change to Tectonic's own files. No new dimension (the Descent stays the
story deep). Bosses and creature animation belong to the boss planner; power level-ups to the power-levels planner.

**Must not regress.** Lodes and the vein giving way, cave-ins, the heavy strike, the Tuning Fork, ropes and rappel,
held light, the cave places and their route test, the way home, cave sound, Lens looks, the D/C/B metal rule (§28.0.7).

---

## 2. Audit: what is really there (verified)

| Stage | What it actually does | Evidence | Consequence | Verdict |
|---|---|---|---|---|
| Natural cave mouths | Tectonic forces every noise cave (cheese, spaghetti, noodle **and vanilla's own `caves/entrances`**) to solid where `depth < 0.1`, about 12 blocks under the ground; only old carvers and Tectonic's river tunnels open to the sky | `tectonic-3.0.31` `data/tectonic/worldgen/density_function/caves.json` and `__constants/cave/depth_cutoff.json`; our preset keeps Tectonic's default `depth_cutoff_start 0.1` (`pack/tectonic/awakened.json`) | mouths are narrow tunnels and ravine slots in valley floors and under canopy, never the wide dark openings vanilla players expect | keep Tectonic; work round it |
| How many mouths there are | 57 mouths over 1.9 km² of his explored land: **median 106 m** from any spot, 80% of land within 200 m; 25 lead 30+ blocks down (median 136 m). The nearest to his Hearth is 188 m away, at y 55, in a valley | `docs/mining/proof/mouths-results.txt` (probe on read-only region copies; it finds the Delvers' mine he visited as a mouth, a sanity check) | the problem is **noticing and invitation**, not count: nothing announces them, nothing says go | the core finding |
| Authored places that show above ground | sinkhole grid 44 chunks (19 of 40 candidates find a site), the mine's 52 (7 of 40): about one surface place per km², nearest typically 400-600 m | §28.5 numbers | too rare to be the answer to "where's a cave near my house" | keep; add a frequent, smaller kind |
| The signpost to caves | the only pointer to a sinkhole is the Horizon line ("Sinkhole · a way down"), **off by default since 27 Oct** | `core.AwakenConfig` `horizonLineShown` false; `delve.cave.CaveHorizon` feeds only `HorizonHooks` | nothing points a new player at a cave at all | replace with a pointer that has a reason |
| Where the iron is | **89 vanilla iron ore blocks a chunk; 11 iron lode hearts in a 512×512 region** (lodes are under 1% of the iron) | ore count over 1,021 chunks round his Hearth (`mouths-results.txt`) | "metal from the deep" changed the tags, not where metal comes from: digging anywhere still finds iron, so the staircase stays the best move | the second core finding |
| What the Hearth says about iron | the upgrade lists "Iron Ingot 7/16"; the only hint anywhere is a tooltip: "Smelt raw iron from iron ore and iron lodes" | `hearth/Upgrades.anchorTier`; `merge/arms/.../en_us.json` | a goal with no direction | add the direction |
| A real cave place, found | he did find a Delvers' mine 580 m from home (counter `discovered/cave/mine/...`), read two lode maps, and still felt no pull | his player data | a great place that isn't on the way to what you need doesn't change the trip | tie places to the need |
| The strike's particles | our sparks are soft round 8-pixel blobs, camera-facing, shrinking (`delve_spark_0..3.png`); grit the same; **vanilla's block-crack particles still spawn underneath** | sprites rendered at 12×; `delve.client.StrikeFeel`, `DelveParticles` | exactly "round circles" | rebuild |
| Owning the break particles | NeoForge lets a mod give *any* block, vanilla stone and ores included, its own hit and break particles (`RegisterClientExtensionsEvent.registerBlock`, read by `ClientLevel.addBreakingBlockEffects` / `addDestroyBlockEffect`) | `.ref/neo/.../IClientBlockExtensions.java`, `.ref/src/.../ClientLevel.java:1051,1094` | we can replace vanilla's crack particles cleanly, no mixin | use it |
| Particle shapes we can draw | `SingleQuadParticle.extractRotatedQuad` takes any rotation; `terrain.client.FallingLeafParticle` already tumbles quads in 3D; powers' `FxRender` draws streaks as custom geometry | code | tumbling chips and velocity streaks are proven paths here | reuse |
| The dig motion | vanilla's swing; Hands (§24) keys weapons, crafting and moves, not mining | §24, `anims/client` | the hand doesn't feel the rock | add one |
| Tool progression | vanilla's wood-stone-iron-diamond ladder, and Hunter mattocks that mirror it tier for tier (E stone ... B netherite) | `docs/modules/gear.md` | the logical ladder Euan named | replace with magic |
| Cave air | Lens has a look per cave biome and an underground look, but "underground the frame's haze barely reaches (it is scaled by the render distance)", so caves have no air, only a grade | `assets/awaken/lens/looks_delve.json` `_about` | a tunnel ends in black, not in coloured murk | add short-range cave air |
| Cave sound | drips at real dripstone, far water, stone settling, a breath from openings, the deep's hum, Sound Physics' reverb (in the pack) | §28.5; `pack` mods | good bones, **never judged by ear**; everything synthesised (Euan called synthesised bells fake on 28 Oct; the city now uses CC0 recordings, `tools/art/sources/city/README.md`) | re-voice with recordings |
| Cave fights | the Hollows (Hushling, Drip Lurker, ...) spawn only in dripstone, lush and deep-dark caves; ordinary stone caves hold vanilla mobs | `data/awaken/neoforge/biome_modifier/fauna_hollows_*.json` | most caves fight like vanilla | spread them |
| Light and finding your way | held light lights caves (drawn), the miner's lamp, the way home thread | §28.3, §28.5 | strong; build on it | keep |

**What's in flight that touches this** (Codex log, 28 Oct): road trade (roads only), Soft Land P5 rank palettes (surface
palettes; new mouths should use them once merged), opening audio and city audio (not caves), fauna navigation. Nothing
touches caves or the delve module. Two planners run now: bosses and creature animation, and power level-ups.

### Root causes, ranked
1. **The fastest iron is vanilla's scattered ore** (89 a chunk vs lodes under 1%). While that's true, no polish moves
   the player off the staircase. *Smallest test:* thin it in a test world and compare iron a minute by staircase and by
   cave route (§6).
2. **Mouths don't announce themselves, and nothing gives you a reason to look.** They exist (median 106 m) but sit in
   valleys and under trees; the one pointer is hidden. *Test:* the coverage probe on new mouths; the iron-trip GameTest.
3. **The strike looks and moves like vanilla with blobs on top.** *Test:* side-by-side clip review by Euan.
4. **No progression that changes play.** The tools are numbers. *Test:* does each wake change what you do next.
5. **Caves have a grade but no air.** *Test:* a Lens frame-time check and Euan's eye.

Hypotheses not yet proved: that cave sound is too subtle to notice (only Euan's ear can say); that jungle canopy is why
he missed the 188 m mouth (likely; not checked in game).

---

## 3. Approaches, and the one I recommend

**A. Targeted repair.** New particles, turn the Horizon line back on for caves, more sinkholes, a Hearth hint. Cheap
(about 4 h). Loses: iron still comes from anywhere, so the trip stays optional and the staircase stays best; no tool
progression; caves still have no air.

**B. Structural: make caves the place metal lives, and make getting there a story (recommended).** Change where early
metal is (lodes that open onto cave walls; vanilla's scatter thinned in new land), add a frequent mouth that announces
itself, have the Hearth point to one, rebuild the strike, give caves air, and replace the tool ladder with one pick that
wakes through what you do underground. Reuses nearly everything W2 and W5 built (lode plans, places, routes, held light,
Lens looks, cave sound, the way home).

**C. Most ambitious: let caves breathe through Tectonic.** A density function of ours wrapping Tectonic's cave cutoff
so real noise caves break the surface in chosen hillsides, on top of B. Truest-looking mouths, but it changes every
hillside near roads and villages, can't promise a route, and costs a density hook per noise cell. **Not now:** B's
authored mouths give a guaranteed walkable route and an art-directed look; C is worth an experiment later if Euan wants
wilder land.

Why B wins: the first two causes are about *where things are* and *what tells you*, so particles alone (A) can't fix the
trip, and C solves a problem (count) the probe says we don't have.

---

## 4. The design

Eight parts. Each says: problem → change → benefit → what it keeps → trade-off → test.

### 4.1 Caves that call you (mouths, tells, a reason)
- **Problem:** mouths exist but nobody sees them; nothing says "go there".
- **Change: Mouths, a new frequent cave place** (`delve.cave`, the same `CavePiece`/`Spec`/`CaveSites`/`Routes`
  machinery as the sinkhole). Four forms, picked by the ground:
  - **Hill mouth** (needs 8+ of rise in 16): a dark arch 5-9 wide and 6-11 high cut into a slope, hanging roots and vines
    over the lip, a throat falling about 1 in 2 for 16-40 blocks with a few drops of 1-2 to natural cave air.
  - **Delver adit**: a timbered square mouth in a hillside, old rails, a short drift (12-30) that ends at a lode outcrop
    (iron or copper above y 30). The first trip's place.
  - **Doline** (flat ground): a collapsed round hole 6-10 across and 10-25 deep, fallen blocks and roots spiralling down,
    opening sideways into a cave.
  - **Cleft**: a slot gorge 2-4 wide cut into a cliff, narrowing into a cave.
  Spacing about 12 chunks (separation 4), so one mouth about every 200 m: the target is 90% of new land within 200 m of
  one (the probe says natural mouths reach 80% today, undressed). Every mouth joins real cave air, passes `Routes` in
  and out without the rope, keeps out of water, the city reserve, settlement yards and roads.
- **Tells, so you notice from far:** the mouth **breathes** (pale mist exhaled at dawn and after rain, rising and
  thinning, visible from 100+ m: the sinkhole's mist sprite, our own); a **cold draught** sound within 32 m; a **Delver
  cairn** (stacked stones and an iron spike; at adits a lantern lit at night, seen from far) on the nearest open ground;
  bats at dusk from the bigger ones; Far Sight draws mouths dark (it does already).
- **A reason: the Hearth points.** When a Hearth upgrade, the Forge or a piece of gear is short of a metal, the task
  card's Hearth step says, in plain words: *"Your Hearth needs iron. The old Delvers cut into the ridge 210 m north-east
  of here. Their adit should still have iron in it."* with **Mark it on my map** (sets an Atlas destination through
  `core.api.Destinations`; never set without the click). The answer comes from the nearest mouth whose route passes a
  planned lode of that metal (pure from the seed, so no chunk loads), else the nearest known cave place with one.
  **No answer is said honestly:** "I don't know of any old workings near here. Your pick hums near iron: follow it." It
  never invents a place. Discovered mouths and places get Atlas pins (atlas module).
- **Benefit:** a new player walks to a cave instead of digging. **Keeps:** sinkholes, mines, seeps, chutes, grottos,
  the first sinkhole. **Trade-off:** more worldgen (site search 9-18 ms per candidate chunk, one candidate per ~144
  chunks, about 0.1 ms a chunk on average); new land only. **Tests:** `delve_mouth_routes` (every form, four seeds,
  in and out without rope), `delve_mouth_coverage` (placement density from the seed alone), `delve_hearth_points` (a
  staged Hearth short of iron gets a mouth whose route reaches a lode of 20+ iron; with none, the honest line).

### 4.2 Metal lives in the caves
- **Problem:** 99% of iron is scattered blobs, so any hole finds it.
- **Change:** (a) **Lodes reach for the air.** `LodePlans` places each lode's heart within 3 of natural cave air (read
  from the generator's base columns, as `CaveSites` does) and walks the seam along the cave wall, so a third to a half of
  its blocks face open air and glint in your light (the glint already exists). Shallow lodes for the first trips: iron
  6 a region (y -20..72), copper 4, coal 4; deeper ones as today. Every mouth's tunnel is laid past a lode (as the
  sinkhole's floor is). (b) **Vanilla's scatter thinned in new land:** iron, copper, coal and gold blobs to 35% of their
  count (NeoForge `remove_features` plus our own copies at 35%; diamonds, redstone, lapis, emerald unchanged). Never
  zero: a staircase still finds some.
- **Benefit:** caves become the best place for metal, so the fun path is also the efficient one. One shallow iron lode
  (30-60 blocks plus its heart's 4-7 lump) covers Tier II. **Keeps:** the vein giving way, the D/C/B metal rule, ore XP,
  the Tuning Fork. **Trade-off:** players who love strip mining get less; old chunks keep vanilla ore. The pacing sim
  (`tools/sim/pacing_sim.py`) is re-run and the D kit's time recorded. **Tests:** `delve_lodes_air` (share of lode
  blocks facing air), `delve_thinned_ores` (counts in a fresh chunk set), the iron-a-minute comparison (§6).

### 4.3 The strike (particles, cracks, motion, sound, true strikes)
- **Problem:** round blobs over vanilla's crack particles; vanilla's swing; synthesised sound.
- **Change:**
  - **We own the hit and break particles** of rock, ore, dirt, gravel, sand and deepslate families (client extensions,
    registered only for blocks that have none, so another mod's are never overwritten).
  - **Chips**: 2-4 a bite, 8-14 on the break, cut from the block's own texture (a random small square of it, like
    vanilla's but tumbling in 3D the way `FallingLeafParticle` does), lit by the world's light (never full bright), one
    small bounce, then they settle on the floor and fade over 0.8-1.6 s. Stone reads as stone because it *is* stone.
  - **Sparks become streaks**: thin lines drawn along their motion (custom geometry like powers' streaks), white-gold
    cooling to the ore's colour, 4-8 ticks long, only off metal ores and hard rock struck with a pick: 2-6 a bite, 6-10
    on the last blow. No round sprites anywhere.
  - **A breath of dust** on the break: 3-5 soft, low wisps in the block's colour that catch your held light and drift.
  - **Cracks**: new break-stage textures (`minecraft:textures/block/destroy_stage_0..9`, our own art): hairline fissures
    that branch and widen, with a faint warm glow in the deepest cracks of an ore.
  - **The dig motion** (Hands, first person and as others see you): a pick's strike is a short wind-up, a strike and a
    small recoil on contact, keyed to vanilla's bite every 4 ticks; a shovel scoops. A tiny view nudge on contact,
    scaled by the camera-kick comfort setting.
  - **Sound from recordings**: a pick on stone, on deepslate, on each metal (a ring layer per ore), gravel and dirt,
    and the break, from CC0 recordings processed like the city bells (sources, licences and hashes in
    `tools/art/sources/delve/README.md`); synthesis only for the magic layers.
  - **True strikes.** On an ore (and any lode block) a thin bright seam shows on the face you're hitting, at a spot
    seeded by the block and the strike count. A bite that lands within 0.22 blocks of it is a **true strike**: that bite
    counts about double (good aim breaks an ore 1.6-2x faster), a brighter streak burst and a soft chime in the ore's own
    note; three in a row rise a step in pitch. The breaking blow as a true strike gives a 25% chance of one more ore.
    Holding still mines at normal speed. Server-authoritative: the server ray-casts from the player's eye with their
    synced look and the same seed; the client predicts with the same rule.
- **Benefit:** every ore is a small, skilful moment; the look is physical and restrained. **Keeps:** the vein giving
  way, the heavy strike, cave-in dust, ore sounds by kind (re-voiced). **Trade-off:** a few more particles than today's
  sparks (capped: at most 48 chips and 24 streaks alive from mining). **Tests:** `delve_true_strike` (server accepts and
  refuses by aim), `delve_strike_rules` (counts per bite, chip lighting, caps, only blocks without another mod's
  extensions), the art preview (`delve.py preview`), Euan in game.

### 4.4 The Delver's Pick: a tool that wakes
- **Problem:** the tool ladder is logical; Euan wants magical, not overpowered.
- **Change:** one pick, **the Delver's Pick**, found **stuck in the lode at the end of the first adit** the Hearth points
  you to ("pull it from the stone": right-click; each player gets their own; a world with no adit near hands it over at
  your Hearth instead). It starts **asleep** and wakes **verse by verse through what you do underground**. Each verse
  changes its look (its head and edge glow differently) and gives a power that changes how you explore. None breaks
  more than the heavy strike's three blocks.
  | Verse | Wakes when | Mines like | Its gift |
  |---|---|---|---|
  | Asleep | you pull it from the stone | stone pick | **Hum**: it hums softly when its head points at a lode within 16 (the Tuning Fork's song, no fork needed) |
  | I Kindle | you break your first lode heart | iron pick | sneak and strike bare rock: an **ember** sticks in the wall and lights the cave round it for 5 minutes (drawn light, three at a time). Light without torches |
  | II Echo | you light your first Delver lamp (4.6) | iron | strike stone and hold still: a **ring goes out through the rock** and ores and open caves within 12 glow through it for 4 s (20 s rest) |
  | III Tempered | at your Forge, with 3 diamonds | diamond pick | the edge turns blue-white; **true strikes come easier** (the seam is wider) |
  | IV Glass | you cut your first Deepglass | diamond | charge the heavy strike to its glint, aim at rock and let go upward: for 5 s the **rock in front turns to glass to your eyes**: caves, creatures and ore beyond show through (40 s rest) |
  | V Hush | you quiet a seep | netherite pick | your strikes make **no sound to creatures**, and you mine at full speed while sneaking |
  | VI Call | at your Forge, with starmetal | netherite+ | when you break a lode's heart, its open blocks within 8 **answer**: they crack and their ore flies to you over 3 s (at most 24) |
  The pick is kept through death, saved as a component on the item (`awaken:pick_verses`, schema 1), mends at the
  Forge, and has the heavy strike and true strikes. Vanilla picks stay craftable but plain; the Hunter mattock's
  recipes retire for new crafting (mattocks already made keep working). The Mining craft's perks stay: Ore Sense is
  passive and small, Echo is the pick's big active one.
- **Benefit:** progression is earned in caves and changes what you do; each wake is a moment. **Keeps:** D/C/B metal
  sinks (diamonds and starmetal go into the temper rites). **Trade-off:** retiring the mattock line touches gear and
  Arms' rows; the pick has to arrive early (the first trip) or it's late to matter. **Tests:** `delve_pick_verses`
  (each wake condition, once each, per player), `delve_pick_glass` (client-only; server never reveals), `delve_pick_call`
  (only that lode's open blocks, ≤ 24), `delve_pick_hush` (no noise event).

### 4.5 The air below (depth bands, cave air, motes, shafts, breaths, sound)
- **Problem:** caves have a colour grade but no air; one plain look for every depth.
- **Change:**
  - **Three bands**, each with its own air, light, sound and a quiet caption the first time each play-day (like a
    region's): **The Shallows** (y 40 and up, under rock): cool blue-grey air, roots, drips; **The Middle Dark** (0 to
    40): warm amber dust, still air; **The Deep** (under 0): ink and violet, the hum (exists), the pressure. Cave biomes
    keep their own looks on top.
  - **Cave air in Lens** (Swift, Cinematic, Ultra): a short-range haze that starts about 8 blocks out and closes about
    40-60 out, tinted by band, only where "under" (so a far tunnel fades into coloured dark, not black); lights already
    glow into it (`fogGlow`). New look fields, a few operations in the existing composites, no new pass.
  - **Motes in your light**: dust drifting only in the air your light reaches (held light, lamp, embers), at most 30.
  - **Light shafts** at mouths, dolines and sinkholes while the sun is up: soft shafts of daylight falling into the dark
    with dust in them (custom geometry, at most 3 drawn, gone at night and in storms).
  - **The Deep breathes**: every 3-6 minutes underground a slow exhale runs through the tunnels toward the nearest
    mouth: a moan, dust along the floor, lights leaning; in the Deep, a pressure swell (the hum rises, lights dim, the
    Hollows stir). It also tells you which way is out.
  - **Sound**: band room tones and drips from recordings; the breath of air from openings re-voiced; "the cave answers"
    when a heart breaks (a long ring in the ore's note).
- **Benefit:** going down feels like going somewhere. **Keeps:** cave-biome looks, CaveSound, the hum. **Trade-off:**
  needs Lens's composites touched (Lens session's files); Lens off gets motes, shafts and sound but no cave air.
  **Tests:** `delve_bands` (band by height and cover), a Lens frame-time check on the Mac (orchestrator), Euan's eye
  and ear.

### 4.6 Delver lamps: light a cavern and it stays lit
- **Problem:** caves are dark tunnels you leave; nothing becomes *yours* underground.
- **Change:** old **Delver lamps** (an iron cage round a clouded crystal) hang in the big halls of cave places and in
  some large natural caverns (about one per 300 m of cave). **Strike one with your pick**: it rings, and light floods
  the cavern as a wave (drawn, 16 blocks a second, up to 40), then the server sets invisible light sources
  (`minecraft:light`, level 11) on a 6-block lattice through the air the wave reached (at most 120, spread over ticks,
  ≤ 1 ms a tick). The hall stays lit for everyone, forever: no monsters spawn in it, it's named ("THE IRON HALL · lamp
  lit"), pinned on the Atlas, and the way home counts it. Breaking the lamp takes its lights back.
- **Benefit:** the cinematic reveal Euan asked for, a safe camp, a reason to explore further, a wake condition for Echo.
  **Keeps:** caves outside lit halls stay dark. **Trade-off:** light blocks are world state (tracked per lamp in
  `DelveData`, schema bump). **Tests:** `delve_lamp_lattice` (only reached air, ≤ 120, removed with the lamp),
  `delve_lamp_spawns` (no monster spawns in a lit hall).

### 4.7 The dark bites back (fights)
- **Change:** the Hollows spawn in every cave by band (Shallows: Drip Lurkers and Chime Crickets; Middle: Hushlings
  join; Deep: all, at higher levels), not just three cave biomes, with their plates and levels as on the surface. Your
  strikes are **noise**: Hollows hunters within 24 come to it (not with Hush). They keep their light rule, so embers,
  lamps and your light are tactics. A lode heart may wake a guardian: left to the boss and creature planner as a hook
  (`Lodes` event), not designed here.
- **Tests:** `delve_hollows_bands` (spawn rules), `delve_strike_noise`.

### 4.8 Cave styles from the land above
- **Change:** caves take a style from the biome over them, as decoration (no new cave biomes): roots and glow under
  Elderwood and forests; crystal growths under the crystal lands (Glass Steppe's Sunshard, Frost Spires' Frostspar,
  the Highlands' Fault Seam) that you can mine for catalysts; sandstone and amber dust under deserts; ice and blue
  light under snow. Each style has its own look tint for the band.
- **Tests:** `delve_cave_styles` (feature placement by biome), Euan's eye.

### 4.9 Contracts and state (the parts that save or sync)
| Thing | Where | Saved / synced | Lifecycle |
|---|---|---|---|
| Mouths | structure pieces (new worldgen) | the world's structure starts | new chunks only; `Spec.STYLE` bump |
| Lodes near air, thinned ore | worldgen | blocks | new chunks only; old chunks untouched |
| The Hearth's pointer | server, pure from the seed + `DelveData` | sent with the task card; destination only on the click | refreshed when the shortfall or the Hearth changes |
| True strikes | both sides, same seed | nothing saved | per bite |
| The Delver's Pick | item component `awaken:pick_verses` (schema 1) | on the item; kept through death | each verse once per pick |
| Pick-in-the-stone | block entity at an adit's lode | the UUIDs that took one | one pick per player |
| Embers (Kindle) | server, timed | synced to clients within 48 | 5 min, three a player |
| Delver lamps | block + `DelveData` (schema 2: lamp → light positions) | world state | removed with the lamp |
| Bands, air, motes, shafts | client | from place and height | per frame |

Invariants: never a light block outside the lamp's reached air; never a pointer to a place that doesn't exist; the pick
never breaks more than three blocks at once; no block changed inside a Hearth, a ward or the city by any of this;
another mod's particle extensions are never overwritten.

### 4.10 What fails, and what happens then
| Case | Detection | What the player sees | Recovery / test |
|---|---|---|---|
| No mouth with a lode near this Hearth (old land, ocean island) | pointer search empty | the honest line; the pick's hum still guides | `delve_hearth_points` |
| A mouth's site fails (water, reserve, no cave air) | site search | no mouth there; the next cell may have one | route + coverage tests |
| Another mod owns a block's particles | extension already set | that block keeps the other mod's | `delve_strike_rules` |
| Lens off or an Iris shaderpack on | Lens state | no cave air; motes, shafts, sound stay | |
| The lamp flood is cut off by a chunk unloading | lattice job | lights placed so far stay; resumes when loaded | `delve_lamp_lattice` |
| Two players strike one lamp | lamp state | one wave, one lattice | idempotent by lamp |
| A pick lost | item gone | Oren re-forges it at the Forge (essence and iron), verses kept on the player | `delve_pick_verses` |

### 4.11 Performance budget (M4, SPEC header)
- Worldgen: mouth site search ≈ 0.1 ms a chunk on average (measured costs from §28.5's CaveSites); lode air checks
  reuse the same base columns; thinning removes work. Target: chunk time within 5% of today.
- Client: chips ≤ 48, streaks ≤ 24, motes ≤ 30, shafts ≤ 3 (all capped, no per-frame allocation); cave air is a few
  operations in passes that already run. Target ≤ 0.3 ms a frame in a cave with all of it on.
- Server: true-strike check is one ray per bite; lamp lattice ≤ 1 ms a tick; ember timers trivial.
- Multiplayer: everything server-decided; particles, air and motes are client dressing from synced state.

---

## 5. Walkthroughs (same inputs, before and after)

### 5.1 "I need iron for my Hearth" (his case)
**Before (from his save).** Tier II asks for 48 stone bricks, 16 iron, 4 lanterns, 4 essence. The window says "Iron
Ingot 7/16" and nothing about where. The nearest mouth is 188 m away at y 55 in a valley; he never sees it. He digs down
from home: 46 stone, 10 iron ore, 2 coal. Round puffs pop off the stone. 7 ingots, and he stops. The Delvers' mine he
found 580 m away earlier never comes back to mind.

**After.** The task card: *"Your Hearth needs iron. The old Delvers cut into the ridge 210 m north-east of here. Their
adit should still have iron in it." [Mark it on my map]*. He clicks; the corner shows "◆ Delver adit ↗ 210 m". Over the
ridge, mist breathes out of a timbered mouth, a cairn with an iron spike beside it, a cold draught. Inside, the Shallows:
cool blue air, drips, his torch pooling light on wet rock, motes turning in it. At the end of the drift an old pick is
stuck in an iron seam. He pulls it free; it hums. The seam runs on along the cave wall, glinting orange where his light
falls. He strikes: chips of stone tumble and settle, short white-gold streaks fly off the iron, a seam of light shows on
the ore; he hits it and it breaks fast with a chime. The strikes carry: a Drip Lurker drops from the roof (plates, a
fight in the dark). The seam leads to a hall with a dead lamp; he strikes it and light floods the hall: "THE IRON HALL ·
lamp lit". He breaks the lode's heart, the vein gives, 30 s of fast mining, and the pick wakes: *Kindle*. Twenty minutes,
about 28 iron, and a lit hall to come back to.

### 5.2 An ordinary later trip (diamonds for C)
Before: strip-mine at y -58 for an hour, Tuning Fork if you have amethyst. After: the pick's Echo finds a cave behind a
wall, the Deep breathes and points the way on, a lamp hall holds a seep's edge, and at the Forge the 3 diamonds wake
*Tempered*.

### 5.3 A hard case (a Hearth on flat plains far from hills)
No hill mouths; a doline or cleft within 200 m (flat-ground forms), or the nearest mouth further out. The pointer says
the real distance; nothing is invented.

### 5.4 Missing data (his current world)
His explored land keeps vanilla ore and has no new mouths; the pointer finds nothing near and says so; his pick arrives
at his Hearth instead of in a stone. As he explores new chunks, mouths and air-facing lodes appear. Everything else
(particles, motion, true strikes, the pick, cave air, lamps in new places, Hollows) works in his world. A fresh world
gets all of it from the first evening.

### 5.5 The flow (now → proposed)
```
NOW       Hearth "Iron 7/16" → (nothing) → dig a staircase → vanilla blobs → round puffs → 7 ingots → stop
PROPOSED  Hearth short of iron → pointer (nearest mouth whose route passes an iron lode; else honest "none")
          → [Mark it] → Atlas destination → mouth's tells (breath, draught, cairn) → the Shallows' air
          → the pick in the stone (hum) → lode on the cave wall (glint) → strikes (chips, streaks, true strikes)
          → noise → Hollows → lamp hall (lit, named, pinned) → heart → vein gives → pick wakes → way home / shaft out
```

---

## 6. Evaluation and delivery

**Baselines (measured):** mouths median 106 m, 80% within 200 m (undressed, unannounced); iron 89 blocks a chunk,
lodes under 1%; his trip 7 iron; sparks are blobs.

**Gates before Euan sees it** (GameTests on CI; Euan tests in game, no builder tours):
1. Every mouth form routes in and out without rope on four seeds and turns (`delve_mouth_routes`).
2. Placement puts a mouth within 200 m of 90% of spots, from the seed alone (`delve_mouth_coverage`).
3. A staged Hearth short of iron gets a pointer whose route reaches 20+ iron within 120 blocks of walking, or the
   honest line (`delve_hearth_points`).
4. **Iron a minute:** in a thinned test world, a scripted cave route along an air-facing lode yields at least twice the
   iron of a scripted staircase of the same length (`delve_iron_rate`).
5. True strikes accepted and refused by aim; verses wake once each; lamp lattices stay in reached air and leave with
   the lamp.
6. The pacing sim re-run: the D kit's iron still in hand before level 13.
7. Frame-time: the orchestrator checks a cave scene on the Mac (perf stays on the Mac) against ≤ 0.3 ms.
8. Euan in game: the iron trip, the strike, the air, the first wake. His judgement is the real gate; side questions
   go to him only if seeing one thing would settle it.

**Phases** (one session, in order; each merges on green CI):
| Phase | What | Lines (est.) | Time at AI pace |
|---|---|---|---|
| **P1 The first iron trip** (the vertical slice) | strike rebuild (particles, cracks, dig motion, recorded sounds, true strikes); hill mouth and adit with tells; the Hearth's pointer; the pick in the stone (asleep, Hum); lodes reach for the air; thinning; cave air in Lens and motes | ~6.5k | 6-8 h |
| **P2 The pick and the lamps** | verses I-VI and their looks; Delver lamps (places and natural caverns), the reveal, lattice | ~6.5k | 5-6 h |
| **P3 Air and the dark** | bands and captions, the Deep breathes, light shafts, band sound from recordings; Hollows by band, strike noise | ~2.5k | 3 h |
| **P4 Styles and more mouths** | cave styles from the land above; doline and cleft forms | ~2k | 2.5-3 h |
| | **Total** | **~17.5k** | **~17-20 h** |

**How the estimate was made.** The delve module (W2 and W5: lodes, places, routes, senses, 12.4k lines with tests, art
and sounds) went from its first commit to merged in about 8.5 hours on 26 Oct, about 1.4k lines an hour for one
builder. All of AWAKEN (143k lines by the brief's count; 360k Java lines in the repo today) took about 30 hours of
parallel builders. This plan is ~1.4x the delve module. Could move it: the art (particle, crack and pick textures, the
mouth dressing) and finding good CC0 recordings are the least predictable parts; Lens's composites belong to the Lens
session's files.

**One session or split?** Euan's rule is one session, no sub-agents. That's what I recommend: P1 alone is a complete,
testable improvement after ~7 h. If he wants it faster, P2, P3 and P4 touch separate files after P1 (pick and lamps;
air and spawns; styles and forms) and could run as three sessions at once, cutting the wall clock from ~19 h to ~12 h.
His call.

**Seams outside `delve`** (small edits, each named so the orchestrator can clear it): `anims` (the dig motion keys in
`tools/art/specs/fp_moves.py` and a hand hook), `lens` (cave-air look fields and two composites), `gear` and Arms' rows
(mattock recipes retired), `hearth` (the pointer's step on the task card), `atlas` (pins), `mobs`/fauna (Hollows spawn
rules), resource overrides (`destroy_stage` textures). SPEC: a new §34 "The Singing Deep" in the same commits.

**Rollback.** Every worldgen change is new-land only and behind the pack's feature flag style (`awaken:singing_deep`);
client parts have settings (`strike_particles`, `cave_air`, `true_strikes`); the old StrikeFeel stays until P1's gates
pass. Turning the flag off stops new mouths and thinning; generated land stays as made.

**Risks.**
- Thinning disappoints strip miners: kept at 35%, not zero; Euan decides (decision 2).
- Recordings: finding clean CC0 pick-on-rock and cave tones takes time; each download listed to Euan first.
- The pick arriving late: tied to the first trip on purpose; fallback hand-over at the Hearth.
- Overlap with the creature/boss planners (Hollows bodies, a guardian): spawns only here; bodies and fights theirs.
- Lens files: coordinate with the Lens session or have the orchestrator clear the edit.
- His world has old land nearby: said plainly in 5.4; a fresh world shows the whole thing.

---

## 7. Decisions for Euan (recommendations first)
1. **Direction**: make caves the place metal lives, and make the trip a story (approach B). **Yes**: particles alone
   can't fix a trip whose fastest route is a staircase.
2. **Thin vanilla's scattered iron, copper, coal and gold to 35% in new land** and put early metal in lodes that open
   onto cave walls. **Yes**: while 99% of iron is everywhere, nothing pulls you into a cave.
3. **Mouths**: a new frequent place (hill mouth, adit, doline, cleft) about every 200 m, with tells (breath, draught,
   cairn, lantern), rather than changing Tectonic. **Authored mouths**: walkable by test, art-directed, no holes under
   roads or villages.
4. **The Hearth points the way** when it's short of a metal, with a "Mark it on my map" button, and says so honestly
   when it knows nothing. **Yes**: a goal with a direction is a pull, not a push.
5. **Particles: rebuild, restrained and physical** (stone chips that tumble and settle, short spark streaks only off
   metal and hard rock, a breath of dust, new cracks), replacing vanilla's crack particles too. **Rebuild**: physical
   chips read as stone, not as effects; minimal alone loses the hit.
6. **True strikes**: a seam on ores you can aim for (faster, a chime, a chance of one more ore); holding still still
   works. **Yes**: skill in every ore instead of a speed tier.
7. **The Delver's Pick**, waking through six verses (Kindle, Echo, Tempered, Glass, Hush, Call), replaces the tool
   ladder; vanilla picks stay; the Hunter mattock's recipes retire. **Yes**: progression earned underground that changes
   how you explore; nothing breaks more than three blocks.
8. **The pick is pulled from the stone** at the end of your first adit (handed over at the Hearth when no adit is near).
   **Yes**: it turns the first trip into a moment and arrives early enough to matter.
9. **Cave air**: three depth bands with their own air, light and sound; short-range coloured haze in Lens; motes in your
   light; light shafts at openings; the Deep breathes. **Yes**.
10. **Delver lamps**: strike one and the cavern floods with light that stays (invisible light sources, shared). **Yes**:
    the cinematic reveal, a safe camp and a place that's yours.
11. **Sounds from CC0 recordings** for the strike and the caves, processed like the city bells (each download listed to
    you first); synthesis only for magic. **Yes**: you said synthesised sounds read fake.
12. **Fights**: the Hollows in every cave by depth, drawn by your strikes, wary of light; a lode guardian left to the
    boss planner. **Yes**.
13. **How to build it**: one session, P1 first (~7 h, the whole first iron trip), then P2-P4 (~12 h). Or split P2-P4
    into three sessions after P1 to save ~7 h of wall clock. **One session**, as you asked.

Not a decision, a note: **your current world** keeps working. New land you explore gets mouths and cave-wall lodes;
everything else (the strike, the pick, cave air, lamps in new places, the Hollows) works now. A fresh world shows the
whole thing from the first evening.

One thing seeing would settle: the particle look. If Euan wants, P1's first commit can be the particles alone so he can
judge the chips and streaks in game before the rest lands.
