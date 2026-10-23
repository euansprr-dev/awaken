# The Descent (module `descent`)

The Well near spawn, ten strata under it, and the Endless below the Seed (SPEC section 8). Package
`dev.awaken.descent`, feature flag `AwakenConfig.DESCENT`.

## What is built

| Piece | Where |
|---|---|
| Strata definitions (names, rules, opening ranks, content levels, dimension keys, Endless cells and stacking rules) | `Strata` |
| Ten stratum dimensions + the Endless, declared in data | `data/awaken/dimension/*.json`, `dimension_type/*.json`, `worldgen/biome/stratum_*.json` |
| One chunk generator (`awaken:stratum`, codec field `stratum` 1..10, 0 = the Endless) | `world/StratumGenerator`, `world/Terrain`, `world/Palette`, `world/Layout` |
| Endless biome source (`awaken:endless`) | `world/EndlessBiomeSource` |
| The Well (runtime landmark, SavedData `awaken:descent_well`) | `well/Well`, `well/WellData` |
| Travel: Well, pits, ascent stones, Waystone fast travel, Deep Lift ride | `DescentTravel`, `DescentWaystones`, `Descent.ride` |
| Deep Pack (attachment `awaken:deep_pack`, 27 slots, synced to owner) | `player/DeepPack`, `player/DeepPacks` |
| Grave Echo entity, death rules per difficulty | `entity/GraveEcho`, `player/DeathRules` |
| Strain of climbing | `player/Strain` |
| Player record (attachment `awaken:descent`: visited, lit Waystones, checkpoint, guardians, graves, Seed) | `player/DescentData` |
| Rule engine (zones = one depth with players), all rules, spawner, guardians, HUD | `rules/Zones`, `rules/Flood`, `rules/Foundry`, `rules/DreamRooms`, `spawn/DescentSpawner` |
| Your shadow (rule 9) | `entity/ShadowSelf` |
| Public API for other modules | `Descent` |
| Client: HUD, Grave Echo markers, Waystone travel screen, renderers, Codex source, Deep Pack status section | `client/*` |
| 38 blocks with generated textures | `DescentContent`, `tools/art/specs/descent.py` |
| All JSON (blockstates, models, items, loot, dims, biomes, lang, sounds, tags) | `tools/content/descent_assets.py` (regenerate with `python3 tools/content/descent_assets.py`) |

## The strata

Every stratum is a vertical abyss around a local origin (Endless cells around their cell centre):

- **Arrival platform** at the top (feet y = 200), a Precursor disc with an **ascent stone** at its back.
- **The helix**: a spiral ramp down the shaft wall from the platform to the floor (the GameTest walks it for every form).
  Some forms add drop ledges between turns (10-block shortcuts that cost health).
- **The bottom camp** (feet y = 20, 47 in the Inverted Sea, 40 in the Seed): a Waystone, a straw bed and a lantern.
- **The pit** (5x5) at the centre of the floor leads to the next depth. The **guardian's lair** is 14 blocks north of it.
- **Loot caches** (chests with vanilla loot tables by depth) sit on the helix; **ores** grow richer with depth.

| # | Stratum | Shape | Rule | Opens at | Content level |
|---|---|---|---|---|---|
| 1 | The Rim | widening shaft, roots, glowmoss, caves | none | E | D (17) |
| 2 | The Hanging Gardens | wide shaft, 26 floating moss islands, glow-vines (climbable) | wind gusts push you toward drops (sneak = 20% push) | D | D |
| 3 | The Drowned Choir | narrow shaft, three rings of flooded halls with pillars | the water rises 9 blocks every 5 min, holds, drains | D | C (35) |
| 4 | The Bone Orchard | giant rib arches, bone trees on the floor | the dead rise once (risen mobs carry `Forces.Tag.ECHO`) | C | C |
| 5 | The Silent Halls | corridor grids at four levels | noise meter; Listeners only target what they hear | C | B (57) |
| 6 | The Precursor Foundry | square shaft, machine halls, gantry helix | the pit is sealed until 5 conduits are lit (ring lights-out) | B | B |
| 7 | The Inverted Sea | open cavern, sea below, sea above, floating islands | gravity tides: float up, ebb, land | B | A (85) |
| 8 | The Dreaming Stratum | rings of dream rooms (60 rooms, 6 variants) | unobserved rooms swap contents | A | A |
| 9 | The Hollow Court | square court, colonnades, court halls | your shadow stands up every 3-4 min | S | S (118) |
| 10 | The Seed | spiral down into a domed arena under the Seed | the finale (story module) | S, level 135 | S |
| ∞ | The Endless | one cell per depth, forms 1..9 | its form's rule plus extra rules: 1 + depth/4, max 3 | after the Seed | 150 + 4·depth |

Content level = `Rank.of(min(6, (n+1)/2)).contentLevel` (as specified), so stratum 1 already uses D-rank numbers.

### Rules in detail
- **Wind (2)**: gusts every 8-18 s, warned 1.5 s ahead by a howl. While a gust blows, a player with a drop of 7+ blocks
  within 4.5 blocks downwind is pushed (0.13/4 ticks; sneaking 0.025).
- **Rising water (3)**: 5-minute cycle (calm 3:40, the Choir sings 10 s, rises 20 s, holds 30 s, drains 20 s). Water
  is placed as still source blocks around each player (radius 11) in the hall they stand in or at the bottom of the
  shaft, and every block placed is remembered in SavedData `awaken:descent_flood` so draining (or a restart) removes
  exactly what the flood added.
- **The dead rise (4)**: a hostile that dies here (not a boss, not already risen) rises 3 s later as a themed mob
  tagged `Forces.Tag.ECHO`. It never rises twice.
- **Listeners (5)**: noise 0-100. Sprinting +0.9/t, walking +0.25/t, sneaking 0; attacking +16, getting hurt +8,
  breaking +15, placing +5, jumping +3 (halved while sneaking); decays 0.3/t (0.6 sneaking). At 35 you are *heard*
  (Listeners walk to you), at 70 *hunted*. Listeners cancel any target they can't hear. Loud players spawn more of them.
  Crossing the stratum (entrance to pit) without ever being heard counts `awaken:latent/silent_halls_unheard`.
- **Machines (6)**: five conduits ring the sealed pit. Touching one flips it and its two neighbours; every pattern on
  five is solvable (tested). When all five burn the seal lifts for everyone. Touches can wake Foundry sentries.
- **Tides (7)**: 2-minute cycle: calm, swell (5 s warning), inverted (20 s, gravity ×-0.06 = slow upward drift), ebb
  (6 s, gravity ×0.15), calm. Fall distance is reset during inverted and ebb. Airborne when the ebb starts and on the
  ground before it ends → `awaken:latent/inverted_landing`.
- **Rooms (8)**: every 2 s the server checks each room near players. A room seen in the last minute that nobody is
  looking at (view cone dot > 0.35 within 60 blocks, or within 10 blocks) and nobody stands in may swap to another
  variant (30% per check, at most every 30 s per room). Variants are saved (`awaken:descent_dream_rooms`).
- **Shadow (9)**: 1-2 min after you arrive, then every 3-4 min: `ShadowSelf` with your skin (dark, translucent), your
  held items (never dropped), your max health, 80% of your attack and 50% of your armour. It hunts only you, copies
  your sneaking, and fades if you leave.
- **The Seed (10)**: `Descent.beginFinale(player)` spawns `awaken:signals_shadow` in the arena. Its death (or
  `Descent.completeSeed`) opens the Endless for nearby players. If the story feature is off, walking into the arena
  begins the finale by itself.

## Travel, safety, Strain
- **The Well**: 400-700 blocks from spawn, chosen deterministically from the seed among 32 candidates on dry, flat land
  (estimated from the generator off-thread), built once over ~2 s of ticks: a ring of Well Stone with a glyph circle and
  four pillars, a knee-high rim, a watertight lined shaft 110 deep with glyph lights every 12 blocks and Deep Lights in
  a pool at the bottom. Falling in (or the **Well Lift** on the rim) takes you to stratum 1's platform.
- **Arrivals** always land on a generated platform; the chunk is loaded first (off-tick), and fall damage is cancelled
  for 6 s after any Descent teleport.
- **Pits** take you to the next depth's platform if your rank opens it; otherwise you are thrown back over the rim
  with a WARNING window saying what opens it.
- **Ascent stones** climb one stratum up (to the previous camp, or the Well), after a Y/N window that shows the Strain.
- **Waystones**: use to light (checkpoint, +`awaken:waystones_lit`, rest), use again to rest and open the travel list
  (lit camps + the Well); sneak-use rests only. The Well Lift opens the same list once you have lit a Waystone.
- **Strain**: climbing on foot (ascent stones, Waystone travel upward, travel to the Well) sets
  `STRAIN = min(0.4, current + 0.05 × strata climbed)` then `Progression.applyAttributes`. Progression decays it.
  `Descent.ride(player, depth)` (the Deep Lift) never strains.
- **Respawn**: dying in the Descent respawns you at your checkpoint camp (`PlayerRespawnPositionEvent`).

## Deep Pack and Grave Echoes
- Everything picked up inside the Descent goes to the Deep Pack (27 slots) unless you dropped it yourself. A full pack
  leaves items on the ground. It shows in the HUD and in Status (K) → DEEP PACK.
- It becomes yours when you rest at a Waystone (use it, or sleep in the camp bed) or leave the Descent.
- Death: STORY keeps half (half of every stack rounded up; unstackables alternate) and returns it on respawn; the rest
  goes to the Grave Echo. HUNTER, HARDLINE and HARDCORE leave everything in the Grave Echo. STORY/HUNTER keep up to 3
  echoes; HARDLINE/HARDCORE keep one (a second death before you reach it loses the first).
- The Grave Echo never despawns, can't be hurt, and returns everything when its owner walks into it or uses it. It is
  marked in the world (always-on-top label) and in the HUD (direction and distance).

## Progression hooks
- `awaken:depth_record` via `Progression.record` (strata = their number, Endless = 10 + depth); new records call
  `markNewThing("depth record")` + `bestMoment`.
- First guardian kill: `markNewThing("guardian")`, `bestMoment`, a Power Core roll and essence shards into the Deep
  Pack, +0.4 levels; `awaken:guardians_defeated` counter.
- `awaken:waystones_lit`, `awaken:latent/silent_halls_unheard`, `awaken:latent/inverted_landing`,
  `awaken:latent/slept_in_descent` and `awaken:slept_in_descent` (completed sleep anywhere in the Descent),
  `awaken:shadows_defeated`.
- XP: first arrival 0.3 levels (Endless 0.2), lighting a Waystone 0.1, kills of Descent mobs 1.2% (elite 5%) of
  `xpToNext(content level)`, guardians 0.4 levels, your shadow 0.12.
- Day's End line: "The Well. Faint lights, far below." / "Stratum N · X is open below you" / the Endless.
- Codex: the Well, every stratum visited (flavour, rule, Waystone), the next stratum as "?", the Endless after the Seed.
- Rank-ups toast when they open a new stratum.

## Try it
- `/awaken descent well` — where the Well is (anyone).
- `/awaken descent go <0-10>` / `go endless <d>` (admin) — to a stratum's arrival platform (0 = the Well).
- `/awaken descent rule <n>` — show a stratum's rule.
- `/awaken descent strain clear` (admin).
- `/awaken descent finale` / `seed` (admin) — start the finale (in stratum 10) / mark the Seed done.
- `/awaken descent travel <depth>` — fallback for clients without the travel screen.

## Decisions
- SPEC §8 has no opening table; the ranks come from `docs/plan.html` (E, D, D, C, C, B, B, A, S, Sovereign). Because
  SPEC 4.4 makes the Seed the Sovereign trial, stratum 10 opens at **S-rank with level 135** (Sovereign's level) instead
  of at Sovereign rank, which would be a deadlock.
- Waystone travel upward counts as climbing (it strains); only the Deep Lift is free. Otherwise the Strain would never
  matter.
- All strata use one dimension height (0..256) and one generator with a form per stratum. Endless cells are 1024 apart,
  64 per row, cell d centred at ((d-1) mod 64 · 1024, (d-1) div 64 · 1024); the form of a cell depends on d only (so
  the biome source can know it), the shape on the world seed.
- Beds work in every stratum (`bed_rule` and `straw_bed_rule`: sleep always, never set spawn); the camp checkpoint is
  the Descent's own respawn.
- The Endless rules come from {wind, water, dead rise, Listeners, tides, shadow} plus the form's own rule; rooms and
  machines appear only in their own forms.

## Known gaps
- The GameTest server never creates datapack dimensions, so travel into strata is not integration-tested there; the
  tests cover the generator (determinism, landmarks, a full walk of every helix), the data (every dimension JSON is
  decoded with our codec) and all rule maths. Teleports were not exercised in a client.
- Mobs, guardians and the finale boss come from `MobHooks`; until the mobs module installs its bestiary they are the
  fallback vanilla mobs. Listener behaviour works with any mob (targets are filtered by event).
- The flood water is placed only around players (radius 11), so from far away a flooded hall looks dry.

## Requests for other modules
- **mobs**: themes `awaken:stratum_1` … `awaken:stratum_9` (roles NORMAL/ELITE) and bosses `awaken:guardian_rim`,
  `guardian_gardens`, `guardian_choir`, `guardian_orchard`, `guardian_silent`, `guardian_foundry`, `guardian_inverted`,
  `guardian_dreaming`, `guardian_court`, `awaken:signals_shadow`. The Descent grants kill XP for mobs it spawned
  (attachment `awaken:descent_mob` flag 1); please don't grant generic kill XP for those too.
- **story**: call `Descent.beginFinale(player)` in stratum 10 and `Descent.completeSeed(player)` after the ending.
- **above**: the Deep Lift calls `Descent.ride(player, depth)` (0 = the Well; camps must be lit).
- **core/SPEC**: add the stratum opening table and the stratum 10 rule to §8.
