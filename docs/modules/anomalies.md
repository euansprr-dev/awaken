# Anomalies: the world comes to you (plan v2 §4.3, P1)

Built in `world/anomaly/**` (client: `world/anomaly/client/**`), wired by one line each in `world/WorldModule` and
`world/client/WorldClient`. Assets: `data/awaken/tags/block/anomaly_avoid.json`,
`data/awaken/loot_table/blocks/anomaly_spirit_cache.json`, `assets/awaken/{blockstates,models/block}/anomaly_spirit_cache.json`,
textures `entity/{rift_tear,rift_core,anomaly_mote}.png` and `block/anomaly_spirit_cache{,_top}.png` (from
`tools/art/specs/anomaly.py`), lang and sounds in `src/main/merge/anomaly/`. Autopilot: `tools/autopilot/anomalies.txt`.

Gates stand 300–1500 m away. Anomalies are small events 32–96 blocks from the player, so there is always something
within a short walk, while days stay calm and home stays quiet.

## The director (`AnomalyDirector`, `Pacing`, `Eligibility`)

- One countdown per player, in **ticks of outdoor play**: 6–12 minutes by day, the clock running **1.5× faster at night**
  (4–8 min). Average ~9 outdoor minutes, what `tools/sim/pacing_sim.py` assumes. The first one after awakening comes
  after 3–6 minutes; the countdown is checked every 20 ticks per player (spread by UUID).
- The countdown only runs when the player is: awakened, in the overworld, not in a gate run (`GateHooks.inGate`), not a
  spectator, **not asleep**, past the **2-minute grace** after awakening, **not at home** (any Hearth Anchor within
  80 blocks, the largest Hearth radius, via `HearthHooks.nearest`), and has **no active anomaly** (one at a time).
- When it runs out the kind is rolled (`Pacing.weights`): day rift 55 / wandering gate 15 / calm 30, night rift 80 /
  gate 20 / calm 0. The first anomaly a player ever meets is a rift tear. No wandering gates before level 3 (Accept makes
  you level 2 and the Bell Crypt comes first). No rifts on Peaceful. The calm event never twice in a row.
- A spot is searched (16 tries, see below). None valid: **skipped quietly**, tried again a minute later.
- Every decision goes to `DirectorLog` (system `anomaly`): countdowns set, pauses (asleep, at home, in a gate...), the
  roll and its weights, refused spots by reason, starts, ends and why, rewards with the core roll.

## Spot validation (`Spots`)

32–96 blocks horizontally (rifts try 32–72 first; processions start 32–64), at most 24 blocks above or below the
player, on the top non-leaf block (`MOTION_BLOCKING_NO_LEAVES`) and:
- loaded and entity-ticking (an 11×11 area), never loading a chunk to look;
- not within 96 of a Hearth (80 radius + room for whatever spills out), 24 of a gate, 48 of another anomaly;
- the ground is solid, not leaves, not fluid, not a build; 3 blocks of open air above;
- nothing built or placed in an 11×11×8 box: no block entity (chests, beds, furnaces, signs, spawners...) and nothing in
  `#awaken:anomaly_avoid` (planks, wooden stairs/slabs, fences, doors, trapdoors, beds, wool, glass and panes, bricks,
  stone bricks, cobblestone, torches, lanterns, rails, concrete, crafting table, farmland, paths...);
- dry: no fluid within 3 blocks of the spot at ground level.

## Rift tear (`Rifts`, `RiftEntity`)

- A crack of light ~3.6 blocks tall over a floating core (entity `awaken:rift_tear`, never saved), full-bright, with
  particles pulled into it and a hum that carries ~40 blocks (readable from 30+). Toast: "Something tore open nearby ·
  52 m NE / Break its core to close it". Now: "Rift tear · 40 m NE" (80), "Break the rift's core · 3 hits" (85) within 8 m.
- Open **2 minutes**. Spills monsters with `MobHooks.spawnThemed` at the player's rank content level (theme from the
  rank's gate themes, nudged by biome: desert → glass/ember, snow → frost, forest → temple/fungal...): 2 at once for a
  player's first rift, else 2 (+1 at night, +1 from C-rank); then a wave of 1–2 (+1 at night, 50%) every 30 s while a
  player is within 48 blocks, alive cap 4 + rank (max +3) (+1 at night). They spawn with **no gate id** (so they despawn
  normally) and guard the tear (home radius 16: they don't chase you across the world).
- **Closing:** break the core: E 4 hits, D 5, C 6, B 7, A+ 8; only players' hits count (melee, arrows, powers), at
  most one per 8 ticks. A strike sets the spilled monsters on the striker. At half, it pushes out one guard (an elite
  from D-rank up, a normal at E).
- **Reward, once** (`paid` flag): essence shards (2 + rank + 0–(1 + rank)) fly out of the core,
  `Progression.grantXp(player, 0.3 × Curve.floorReward(rank, 1), XpSource.ANOMALY)`, 12% `PowerCoreHooks.roll`,
  counter `awaken:rifts_closed`, toast "Rift closed / +3 essence · +83 XP".
- **Ignored:** it seals itself after its time; nothing is lost, the monsters it let out stay and despawn the vanilla way.

## Wandering gate (`WanderingGates`)

`GateHooks.openWandering(player, spot, 1 floor (65%) or 2 (35%), 5–8 min)`: a short gate at the player's rank that fades
unless entered and never breaks. Toast "A wandering gate formed · 64 m NE / It fades in 6 min unless you enter". The
gates module already suggests nearby wandering gates in the Now tracker, so this module doesn't. If no gate can form (no
gate world, no free cell) a rift opens on the same spot instead. The anomaly ends when the gate is cleared, released or
gone, or after its time unless someone is inside.

## Spirit procession (`Processions`, `AnomalyMote`, `SpiritCacheBlock`), the calm one

Day only. Five warm lights (`awaken:anomaly_mote`, never saved) circle a spot 32–64 blocks away. Toast "Lights gather
nearby · 38 m SW / Follow them"; Now "Follow the lights · 30 m SW" (55). Come within 16 blocks and they set off along a
20–36 block path (onward, away from you, ending on another valid spot), moving only while you stay within 12 blocks of
the lead. At the end they sink into a **Spirit Cache** (block, no item; toast "The lights leave something behind",
Now "Spirit cache · 12 m" 60). Use or break it: its loot table (1–2 essence, plus one of glow berries, amethyst, golden
carrots, iron, XP bottles, lapis, emeralds, a rare name tag), `Progression.worldXp(player, "anomaly", 0.04)`, counter
`awaken:spirit_caches`, and the first one is a discovery. Nobody follows within 6 minutes: the lights fade. An unopened
cache fades after 5 minutes.

## Discovery, Signal, state

- First time a player meets each kind (within 24 blocks of a rift or a gate's mouth, closing a rift, opening a cache):
  `Progression.discover(player, "anomaly/<kind>", 0.1)` + `markNewThing`, counter `awaken:anomalies_seen`.
- Signal: toasts only, never a choice card.
- World state: `AnomalyData` (`data/awaken/anomaly_state.dat`, schema 1): id counter, world switch, active anomalies
  (`Anomaly.Saved`: base, rift part, trail part, all with defaults), caches waiting for their chunk to be taken down.
- Player state: attachment `awaken:anomaly_memory` (schema 1, kept on death): countdown, awakening time, tallies.
- **After a restart** (`ServerStartedEvent`) every anomaly that was running counts as interrupted and is dropped: its
  entities were never saved, an unopened cache is taken down (now, or when its chunk next loads), a wandering gate fades
  on its own, spilled monsters despawn normally. If a chunk unloads while an anomaly runs, its tear or lights come back
  when the chunk ticks again.

## Commands

- `/awaken anomaly why` (anyone): the countdown or what pauses it, your active one, and the last decisions.
  `/awaken why` shows them too.
- Game masters: `now [kind]` (starts one 14–26 blocks where you look, else anywhere valid nearby; kinds `rift_tear`,
  `wandering_gate`, `spirit_procession`), `list`, `clear`, `skip` (a procession jumps to its cache, anything else to its
  last 5 seconds), `timer <seconds>`, `on` / `off` (the world switch).

## Performance

Per player one cheap check per second; active anomalies are driven every 10 ticks (a handful of entity lookups). The
spot search runs at most once a minute per player when due (≤ 32 candidates × an 11×11×8 block scan, loaded chunks
only). No per-tick allocation in entity ticks; the tear and lights are single quads.

## Tests (GameTests)

`anomaly_pacing_and_kinds` (delays, the 1.5× night clock over 400 simulated events, kind weights, compass words),
`anomaly_eligibility_rules` (every pause rule), `anomaly_avoid_tag_has_builds`,
`anomaly_spot_rejects_builds_water_hearth` (planks, glass, bricks, a bed, a chest, water under and beside, a roof,
distance, height, Hearth, another anomaly), `anomaly_director_pauses_and_caps` (nothing before awakening, grace, asleep
keeps the countdown, one at a time for the director and for commands), `anomaly_rift_close_pays_once`,
`anomaly_rift_ignored_closes_cleanly`, `anomaly_state_round_trip` (world data and memory, old/empty files),
`anomaly_restart_cleans_up`, `anomaly_procession_leads_to_a_cache`, `anomaly_wandering_gate_tracked` (with the gates
module's test level).

## P2: the fallen star and the shimmer grove

Two more kinds through the same director, spot validation, saved state, Now lines, toasts and discovery
(`Progression.discover(player, "anomaly/<kind>", 0.1)`). Code: `Stars`, `FallingStarEntity`, `FallenStarBlock`,
`Groves`, `GroveVariant`, `ShimmerEntity`; client `StarRenderer`, `ShimmerRenderer`, `OreSight`. Tests:
`AnomalyP2Tests`. Autopilot: `tools/autopilot/anomalies2.txt` (with the landmarks).

### Kind weights (rift / gate / procession / star / grove)

| | rift | wandering gate | procession | fallen star | shimmer grove |
|---|---|---|---|---|---|
| day | 30 | 15 | 20 | 6 | 25 |
| night | 45 | 15 | 0 | 35 | 5 |

- Calm days (by day rifts are under a third, the peaceful kinds nearly half), dangerous nights (rifts and stars with their
  guardians ~80%).
- The first anomaly is still a rift; gates still wait for level 3; no rifts on Peaceful.
- **No kind twice running** (a rift may repeat, at half weight); if nothing else fits, a repeat beats nothing.
- **Novelty:** kinds the player has never met (no `awaken:discovered/anomaly/<kind>` yet) weigh double, so the first hour
  shows what the world can do. Simulated 60-minute sessions (5 anomalies, the clock's day and night): 3.6 kinds on
  average, every kind in over a third of sessions (`anomaly_p2_kinds_and_variety`).

### Fallen star (`Stars`), mostly at night

- Spot: the usual rules plus **open sky** (`Spots.Rules.sky`: never under a tree or a roof), 32–80 m first.
- A light (`awaken:anomaly_falling_star`, never saved) streaks down from ~80 blocks up, off to one side of the player so
  it crosses their view, over 2 s with a long firework trail, whistling (`anomaly.star_fall`, heard ~96 m). Toast "A star
  fell nearby · 52 m NE / something guards it".
- Impact: a shallow crater (radius ~3, two deep in the middle) dug only out of natural ground (dirt, sand, stone, snow,
  terracotta, gravel...; builds, block entities and anything else are left alone), lined with blackstone, tuff and coarse
  dirt, magma under the meteor; boom and smoke. The crater stays as a mark on the land.
- The meteor (`awaken:anomaly_fallen_star`, no item, light 15, state `sealed`) is **sealed** (white-hot, can't be broken
  in survival) until its guardian is dealt with.
- **Guardian:** when anyone comes within 16 blocks, one monster of the owner's rank rises from it (`MobHooks.spawnThemed`,
  theme by biome as for rifts; an elite from D-rank up, a normal at E), glowing, kept within 10 blocks of the meteor,
  for **90 s**. Slain: the star cools at once and pays more. Not slain: it fades back into the light and the star cools
  anyway. On Peaceful no guardian rises; the star simply cools.
- **Open it** (use or break, once cooled): its loot table `blocks/anomaly_fallen_star` (2–3 of raw iron, iron nuggets,
  raw gold, amethyst, glowstone dust, raw copper, obsidian, emerald, a diamond) drops with the block; plus essence
  shards `1 + rank + 0..(1 + rank)` (+2 if slain), XP `0.2 × Curve.floorReward(rank, 1)` (0.3× if slain) and a Power Core
  roll of 10% (25% if slain). Counters `awaken:stars_opened`, `awaken:star_guardians_slain`. Toast "Star opened / +3
  essence · +55 XP".
- Unopened, it burns out after 6 minutes (the meteor goes, the crater stays). A guardian still standing when its star
  ends fades. A guardian whose star is gone (a restart, an unloaded chunk coming back) is turned away when it would join
  the world (`EntityJoinLevelEvent`, entity tag `awaken_star_guard` + the anomaly id).
- Now: "Fallen star · 60 m NE" (75), "Defeat the star's guardian" (85, within 32 m), "Open the fallen star" (82, within
  12 m once cooled).

### Shimmer grove (`Groves`), mostly by day, peaceful

- Spot: the usual rules, 32–72 m first. A ring of faint light curtains (radius 7) in the variant's colour, a soft column
  at its heart, sparkles (`awaken:anomaly_shimmer`, never saved). Toast "The air shimmers nearby · 40 m SW / the rules
  bend there". It waits 5 minutes for someone.
- Step inside (within 7 blocks of its heart): the rules bend there for **2½ minutes** (toast "Shimmer grove · Light steps /
  Your steps are light here · 2:30"), a discovery the first time, `Progression.worldXp(player, "anomaly", 0.04)`, counter
  `awaken:groves_visited`. The variant (never the same as the player's last grove):
  - **Light steps** (`lightfoot`): low gravity (`GRAVITY` ×0.35) and +16 safe fall, as transient modifiers on whoever stands
    inside (never saved, gone on relog); leaving or the grove ending takes them off, with 4 s of slow falling if you are in
    the air.
  - **Ore sight** (`ore_sight`): the client outlines ore within 12 blocks around and 18 below the grove's heart through the
    stone (per-tick gizmos, coloured by kind, at most 64, rescanned every 2 s) while you stand in it.
  - **A spirit speaks** (`whisper`): the spirit at its heart whispers where the nearest landmark worth your level lies
    ("“Something sealed, 420 m NW”", which the Now tracker then raises for 10 minutes, see landmarks.md), else the
    nearest open gate ("“A D-rank gate, 800 m E.”"), else old words.
  - **Healing flowers** (`bloom`): up to 14 wildflowers spring up around the heart (they stay afterwards) and whoever
    stands among them gets Regeneration II, refreshed every 2 s.
- Now: "Shimmer grove · 40 m SW" (55); inside, "Light steps · 2 min left" (50; whole minutes so the line doesn't tick).

### State

- `AnomalyData` schema **2**: each anomaly gains a `star` part (landed, guardian state, until, guardian id) and a `grove`
  part (variant, spoke); schema-1 records load with defaults. The meteor joins the spirit cache as a block the removal
  list takes down when its chunk loads.
- `AnomalyMemory` schema **2**: `stars`, `groves`, `last_grove`.
- After a restart, a landed star's meteor is taken down (now or when its chunk loads); a grove's modifiers were never
  saved.

### Commands

- `now fallen_star`, `now shimmer_grove [lightfoot|ore_sight|whisper|bloom]`.
- `skip`: a falling star lands; a guardian fades; a star with no guardian yet cools; a waiting grove wakes for you.
- `goto` (game master): stands you beside your active anomaly's heart (a grove wakes as you arrive, a guardian rises).

### Performance

Stars and groves are driven by the same 10-tick director pass: a block lookup, a nearest-player query and (light steps,
flowers) a loop over the level's players. No per-tick allocations in their entities (the falling light interpolates with
plain arithmetic). The crater is ~45 block writes once. Ore sight is client-only: one bounded block scan (25×25×24)
every 2 s while you stand in such a grove, then cached outlines.

## Known gaps

- (Resolved since P1: the Hearth check uses `HearthHooks.inside`, and `features.anomalies` is the config switch; both
  cover the P2 kinds too.)
- Ore sight shows ore only to clients that hold the chunks (always true for a player standing there); the outlines are
  drawn on top of everything, like the spectator outline.
- The star's crater and the bloom grove's flowers are left in the world on purpose (marks of what happened); both only
  touch natural ground that passed the spot rules.
- The rift's (and the star guardian's) theme ids are listed in `Rifts.THEMES` (the mobs module's gate themes and their ranks); a new gate theme
  needs adding there.

## SPEC-worthy decisions (for the integrator)

1. New §: Anomalies. Countdown of outdoor play 6–12 min (first 3–6 min after awakening), ×1.5 at night; paused when not
   awakened / gate run / not overworld / spectator / asleep / < 2 min since awakening / Anchor within 80 / one active.
   Kind weights day 55/15/30, night 80/20/0; first is a rift; gates from level 3; no rifts on Peaceful.
2. Spot rules as above (32–96 m, ±24 y, Hearth 96, gate 24, anomaly 48, 11×11×8 build scan, dry within 3, tag
   `#awaken:anomaly_avoid`); no valid spot → retry in 1 min, no message.
3. Rift: 2 min; hits E4 D5 C6 B7 A+8, one per 8 ticks, players only; waves every 30 s while a player is within 48;
   cap 4 + min(3, rank) (+1 night); monsters have no gate id and a home radius of 16; reward once: shards
   2 + rank + rand(0..1+rank), XP 0.3 × floorReward(rank, 1), Power Core 12%.
4. Procession: day only, 5 lights, start 32–64, path 20–36, notice 16, follow 12, wait 6 min, cache 5 min; loot table
   `blocks/anomaly_spirit_cache`; worldXp "anomaly" 0.04.
5. Wandering gate: floors 1 (65%) / 2 (35%), 5–8 min; falls back to a rift.
6. Discoveries `anomaly/<kind>` at 0.1 of a level. Counters `awaken:rifts_closed`, `awaken:spirit_caches`,
   `awaken:anomalies_seen`.
7. State: SavedData `awaken:anomaly_state` (schema 1), attachment `awaken:anomaly_memory` (schema 1, copy on death);
   anomalies interrupted by a restart are cleaned up on server start.
8. (P2) Kind weights day 30/15/20/6/25, night 45/15/0/35/5 (rift/gate/procession/star/grove); no kind twice running
   (a rift at half weight); kinds never met weigh ×2.
9. (P2) Fallen star: open-sky spot 32–80 m first; fall 2 s; crater radius ~3 (natural ground only), sealed meteor; a
   guardian of the owner's rank (elite from D) rises within 16 blocks and lasts 90 s; opened once cooled: loot table
   `blocks/anomaly_fallen_star`, shards 1 + rank + rand(0..1+rank) (+2 slain), XP 0.2 × floorReward(rank, 1) (0.3 slain),
   Power Core 10% (25% slain); burns out 6 min after landing. Counters `awaken:stars_opened`,
   `awaken:star_guardians_slain`.
10. (P2) Shimmer grove: radius 7, waits 5 min, bends the rules 2½ min once stepped into; variants light steps (gravity
    ×0.35, +16 safe fall, transient), ore sight (client outlines within 12/18 blocks), a spirit (landmark/gate hint), bloom
    (≤ 14 flowers, Regeneration II); worldXp "anomaly" 0.04 on entering; counter `awaken:groves_visited`.
11. (P2) State: `awaken:anomaly_state` schema 2 (star and grove parts), `awaken:anomaly_memory` schema 2 (stars, groves,
    last grove).
