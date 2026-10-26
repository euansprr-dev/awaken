# The deep (delve module)

"Between the Gates" phase W2, *Metal from the deep* (docs/world-depth/PLAN.md section 7, SPEC §28.3). One rule: hide
from the hunt, metal from the deep, light from the gates. From D up every Hunter piece, mattock and forged weapon needs
its tier's mineral, gates stop paying raw ore, and mining gets its own reasons, finds, tools and feel.

Code: `src/main/java/dev/awaken/delve/**` (server and common) and `delve/client/**`. Wired by one line each in
`Awaken.java` and `AwakenClient.java`. Data, textures and sounds are generated: `tools/art/specs/delve.py` and
`tools/art/specs/audio_delve.py`.

## What's in it

| Class | What it does |
|---|---|
| `Ore` | The nine lode minerals (vanilla's eight and Deepglass): depth band, lodes per region, size, colour, heart |
| `LodePlans`, `Lode` | Where the lodes are, from the seed alone: 256 x 256 regions, a seam walked out from each heart (SplitMix64), cached |
| `LodeFeature` | `awaken:lodes`: each overworld chunk writes its own share at `underground_ores` (stone, deepslate or another ore only) |
| `Veins` | The nearest lode (no scan), the heart breaking ("the vein gives"), the 30 s two-blow window, telling clients what's near |
| `api.Lodes` | The public API: `nearest(level, pos, ore[, radius])`, `heart(level, pos)` |
| `CaveIns` | Fair cave-ins: the chamber test, the warning, the falling roof; never in a Hearth, the city, a ward or under building |
| `DelveData` | Saved per dimension (schemaVersion 1): hearts that gave, sections players built in underground |
| `HeavyStrikes` | The heavy strike's server half: checks, the three-block bore through the game's own break, wear and hunger |
| `TuningForkItem` | Tune on an ore, strike on stone, the server names the block to sing toward |
| `PitonBlock`, `RunnerLineBlock`, `RunnerRopeItem` | A piton on a wall, a rope paid out 32 down, the coil back when it's taken |
| `DeepglassBlock`, `LodeHeartBlock` | Deepglass's hum and motes; a heart's glints |
| `DelveContent`, `DelveNet`, `DelveCommands`, `DelveTests` | Registrations, messages, `/awaken delve`, the GameTests |
| `client.StrikeFeel` | Every bite: the ore's sound, sparks or grit, the last blow's crack, hold and kick |
| `client.HeavyCharge` | Sneak + hold attack with a pick: the charge, the glint, the release; the bore's burst for everyone near |
| `client.VeinFx` | Glints on lit lode blocks; the vein giving: light running out along it inside the rock (the blocks' own faces lit, glowing cracks), no outline; the client's two-blow speed |
| `client.CaveInFx` | The cave-in: cracks spreading over the roof, dust pouring from the seams, each stone's tear and landing (dust, thud), the shake |
| `client.ForkSong` | The fork's ring (pitch by how squarely you face the lode) and its arc on the band |
| `client.LampGlow` | The miner's lamp: the rock ahead lit by redrawing its lit faces' own quads additively, a haze, a flame on the brow |
| `client.DelveRender` | The two additive passes the lamp and the vein share: a block face's own quads lit, sprites on faces; soft glow |
| `client.DelveParticles` | Spark, grit, dust, glint, mote |

Seams the phase touched in other modules (SPEC §28.1): `gear` (recipes via `tools/art/specs/gear.py`, `HunterTier`
numbers and `metalTag`), Arms' forged recipes (`tools/art/specs/arms_weapons.py recipes`) and `ArmsForge` (catalysts,
lapis Blueprints) with `ArmsTests.forge`, `gates/run/Loot` and `Rooms` (no ore handouts), `progression/ProgressionModule`
(`OreXp`) and `XpBudget.DEEP`, `living/craft` and `living/client/CraftFx` (the Mining craft), `descent/world/Palette`
(Deepglass in the strata), the fallen star's loot table.

## Numbers

All in SPEC §28.3. The ones to know: about 13 of a tier's metal for its seven pieces; ore XP 0.01 of a level x coal 0.5
… diamond 6, deep ores through their own budget; lodes 20-120 blocks, a few a region at their ore's depth; the vein's
window 30 s at two blows a block; cave-ins 60% of the time in a chamber of 160+ air blocks 7+ across, the roof letting go
over 1.5 s nearest first; the heavy strike
on the Maul's charge (6 / 20 / 30 ticks), three blocks, x3 wear (x2 on the glint); the fork reaches 48; a rope 32.

## How to try it

- Survival: dig below y 0 with an iron pick. Make a Tuning Fork (2 amethyst, 3 copper), touch it to an iron ore and
  strike it on stone; turn until the ring is highest. Break the lode's heart.
- `/awaken delve`: the nearest lode of each ore within 96.
- Admin (tours, tests): `/awaken delve stage cave [ore]` carves a chamber round you with a lode on its north wall;
  `stage rope` a shaft with a piton and its rope; `fork <ore>` a tuned fork; `ring` strikes it on the stone underfoot;
  `heart [calm]` breaks the nearest heart within 16 (calm: no cave-in); `cavein` brings the nearest heart's roof down;
  `heavy` a heavy strike released on the glint; `demo strike|charge` the client's strike feel on the block in view.
- Tour: `tools/autopilot/world-depth-w2.txt` (`slot.sh light`).

## Tests

`delve_lode_heart` (isolated: a chamber with an iron lode; the heart opens it once, the lump, the window; a heavy strike
on the glint bores three along the aim with the extra wear and a breath before the next; the cave-in refused under
building and in a Hearth, then warned and fallen), `delve_tier_metals` (each metal tag; every D-S gear
recipe and forged weapon has a metal-only slot; foci gold; Veilwoven amethyst; Gatewrought 20/2.0; lapis Blueprints;
the five catalysts; 300 A-rank caches pay no raw ore; starmetal smelts, bell bronze casts a bell), `delve_tuning_fork`
(tuned on iron ore; the nearest block of an iron lode 30 off; nothing for a gold lode 70 off), `progression_ore_xp` (ore
kinds, a diamond against coal, the deep budget after the shallow one is worn, discoveries, the Mining craft unbudgeted,
`awaken:craft_rate`).

## Known gaps

- Lodes and Deepglass veins come only in new chunks; an old world's fork finds nothing in old terrain (it checks).
- The heavy strike needs sneak: holding attack alone stays vanilla mining.
- The lamp's light is drawn, not real light: mobs don't see it and it doesn't stop spawns; its rays allocate a little per
  frame while someone wears one.
- No zip line between pitons (the Ascent has nothing that rides a run of blocks yet).
- Not proven in a tour yet: the Deepglass block's own look (its shot shows the glow specks, not the crystal), a rope
  hanging from a piton (the shots show the shaft and the grip, not the rope clearly), the lamp seen in third person
  (its shot is black).
- Judged on the preview sheet and the tour (shots: /Volumes/MEDIA/tour-shots/world-depth-w2-2026-10-26); the sounds were
  checked by their loudness, not yet by ear in game. The tour stages its chamber 700 blocks off Carillon: the city's
  footprint is protected all the way down for survival players, so neither the heavy strike nor a cave-in happens there.

## W5: caves worth entering

"Between the Gates" phase W5 (PLAN section 8, SPEC §28.5): places in the caves joined to the natural ones, every one with a
route that needs no rope; the rappel and rope fittings (in `body/ascent`); sound and light below; names; a way home; vents;
the Descent's tunnels dressed.

| Class | What it does |
|---|---|
| `cave.CaveKind`, `cave.Spec` | The five kinds; one place as data (origin, turn, seed, joint, numbers), saved with its piece |
| `cave.CaveSites` | Where a place goes: depth, rock overhead, dry, out of the city, natural cave air from the base columns, a lode beside a sinkhole, a Deepglass lode in a seep, a hillside for a mine; within 8 chunks of its start |
| `cave.CaveStructure`, `cave.CavePiece`, `cave.NearCityPlacement` | The structure type, its one code-built piece, the first sinkhole's placement off the camp road |
| `cave.Sinkhole`, `Mine`, `Seep`, `Chute`, `Grotto` | The builders (rooms strung like beads, G45), into a `Sink`: a chunk of the world, all of it, or a `Grid` |
| `cave.Carve`, `cave.Frame` | Hashing and noise, carving that keeps the bedrock, blobs, walkable tunnels, the rock of a depth; a place's turned axes |
| `cave.Routes`, `cave.Grid` | The no-rope route search over a built place with the Runner's moves and numbers; a place built in memory |
| `cave.CavePlaces`, `cave.CaveData` | At run time: your place (sent to your client), discovery and caption, a seep's +5, heart and nest, quiet seeps, a sinkhole's bats, a grotto's cache and fishing spot, the places near you |
| `cave.CaveHorizon` | "Sinkhole · a way down" on the Horizon, found by the sinkholes' own site search |
| `cave.CaveItems` | The Delvers' logs (Codex), the lode map (lapis), the old pick (the fork's reach) |
| `cave.CaveJoints` | Cave air near a point, for other structures (the Crystal Nest's tunnel) |
| `cave.CaveCommands`, `cave.CaveStaging`, `cave.CaveTests` | `/awaken delve place`, staged places, `delve_cave_routes` and `delve_seep` |
| `home.*` | The way home (trail, Now line, thread), the vents feature, `delve_way_home` |
| `sense.*` | The cave soundscape's sound events, `/awaken delve senses goto` |
| `client.CaveClient`, `CaveHud`, `CaveFx`, `CaveState` | The place you're in, its caption, mist and draught, logs and lode marks |
| `client.SenseClient`, `SenseState`, `CaveSound`, `DeepHum`, `CaveLook`, `SenseLoop` | Sound and light below |
| `client.HomeClient`, `VentFx` | The thread home, the vents' embers |

Seams outside delve: `living/api/Regions.addLevelExtra` (the seep's +5), `world/fauna/lair` (`LairStructure`, `LairPiece`,
`LairBuilder.Sink.joint`, `DeepLairs.joinTunnel`: the joined Crystal Nest), `world/fauna/FaunaNow.depth` (the lair line),
`world/fauna/Lairs.placeAt` (a laid nest's pocket), `delve/TuningForkItem` (`awaken:fork_reach`), `delve/LodePlans.stage`,
`lens/client/LensClient.placeLook`, `core/audio` (two Drives, `Listener.under/depth`), `signal/client/NowClient.ownWay`,
`descent/world/Terrain` and `Palette` (seams and grottos), `body/ascent` (the rappel, fittings), `mobs` (the seep heart).

### How to try it

- Survival: find a sinkhole by the Horizon line (the first is 600-900 from Carillon, off the camp road), walk the shelves
  down, climb the vine cleft back up, dive from the lip by the spring. A mine's adit is a timbered hole in a hillside.
- Admin (tours, tests): `/awaken delve place <sinkhole|mine|seep|chute|grotto>` lays one round you (a sinkhole's rim
  where you stand; the rest with their floor at your feet, facing your way) and puts you at its way in; `place go <mark>
  [toward]` (marks: start, goal, view, shelf, dive, pool, cleft, heart; office, stope, incline, crawl, bridge; hall,
  socket, nest, vent, spire0-3; mouth, lip; curtain, room, cache); `places`; `seep quiet|wake`; `home stage`, `home
  thread`, `vent stage`, `descent grotto <n>`; `senses goto <cave biome>`, client `senses [status]`, `senses seep <w>|off`;
  `/awaken ascent rappel stage [deepglass]`, `ascent rappel fit <iron|gold|deepglass>`, `ascent pilot run rappel`.
- Tour: `tools/autopilot/world-depth-w5.txt` (on GitHub's Mac runners: `tools/tour-ci.sh`).

### Known gaps

- The cave sound is unheard on a Mac yet (listening check owed). Places come in new chunks only.
- Places laid by `/awaken delve place` are known until the server stops (they have no structure start).
- The Horizon finds sinkholes only; the other places are found by walking into them, the fork, Deep Ear and the Veil.
