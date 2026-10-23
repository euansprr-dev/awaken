# Lucidium: dreams built from your day

Package `dev.awaken.lucidium` (client: `dev.awaken.lucidium.client`). Feature flag: `AwakenConfig.LUCIDIUM`.
Public API: `dev.awaken.lucidium.Lucidium`. Contract: SPEC section 9 (Lucidium part).

> "In a dream we create and perceive our world simultaneously." The Lucidium is built from the places you actually
> were today, stitched together wrongly, and the more you notice that it's a dream, the more of it you can shape.

## What's in it

### 1. Day Journal (`journal/DayJournal`, `journal/SnapshotFiles`, `data/Dreamer`)
All day the game quietly remembers places as vanilla structure templates
(`<world>/awaken/lucidium/<player-uuid>/<id>.nbt`, 16x12x16, gzipped, openable with a structure block). Feet sit at
template y = 4, so the remembered floor is at y = 3.

| Moment | Trigger | Per day |
|---|---|---|
| Near death | after damage, health under 25% (once per 30 s) | 2 |
| Boss kill | a kill of a `MobRole.BOSS` mob, Wither, Ender Dragon, Elder Guardian or Warden | 3 |
| Home | 5 s within 6 blocks of your respawn bed or 12 of your Hearth; or the bed when you lie down | 1 |
| Vista | 4 s standing still, 25+ blocks above sea level, sky above, the ground 12+ blocks lower ahead | 2 |
| Most kills | the 32x32 area where you killed the most hostiles today (re-captured at 5/12/25 kills, replacing) | 1 |
| New biome / structure | first time ever in that biome / structure type | 3 / 3 |
| Gate mouth | travelling into `awaken:gates` | 2 |

At most 10 per play-day, the 64 most recent are kept (older files are deleted). Block entity data is stripped on
capture (chests come back empty: dreams never copy loot). **Never another player's base:** no capture within 32
blocks of another player's bed (a world-wide bed registry, so offline players count too), their respawn point, or a
Hearth that isn't yours. Each snapshot gets a label ("where you nearly died") and a content description read from its
blocks ("A waterfall with a door behind it").

### 2. Dream plans (`plan/DreamPlanner`, `plan/DreamPlan`, `dream/Planner`)
At dusk (overworld time crossing 12000), at login at night, or when you lie down without one, tonight's plan is
built. `DreamPlanner.plan` is pure and deterministic (seed = world seed + player + play-day + dreams so far):
- 4–8 places: today's snapshots first (newest first), older ones to fill (homes and bosses favoured), authored
  "imagined" places (`plan/Imagined`) if the journal is thin. Your latest home is always included and the dream
  starts there. One place may hang upside down under its island.
- Layout on a golden-angle spiral of floating islands; a minimum spanning tree of links: 3-wide bridges (straight,
  arched, cloud stepping-stones or a colour ribbon) or, when the height gap is steep, a spiral stair around a glowing
  core; plus 1–2 impossible doors (walk through a frame, come out of its twin on another island).
- Palette distortion (`plan/DreamPalette`): one of 7 moods (pearl, amethyst, tide, ember, snowglobe, copper, dusk)
  of exact and token block swaps (stone→smooth quartz, grass→moss, oak→birch...), every dyed block rotated round the
  dye wheel, properties carried over, plus a 1-in-45 sprinkle of Shifting Stone. Modded blocks are left alone.
- Two dream signs (`plan/DreamSign`): one personal sign that recurs every night (from your UUID), one that changes.
- Nightmare (`plan/Nightmares`): your deaths in the last 7 play-days (lucidium's own death marks, which also see
  falls, fire and water) and the progression counters `awaken:killed_by_week/<entity>` map to families falling,
  burning, drowning, undead, arthropod, explosion, generic; the top family appears with 40% + 15% per extra death
  (max 85%), certainly with the "face my fear" intention. Mobs are spawned fresh from the authored arena template via
  `MobHooks.spawnThemed(level, "awaken:nightmare/<family>", MobRole.Role.NIGHTMARE, ...)` (patent rule).
- A mentor's ring if you have a Path (50%), the door of intention, and tonight's vision.
- Length 3–8 minutes (more places, longer).

The blueprint is compiled off-thread (`world/BlueprintCompiler`): the remembered places are read from disk, rotated,
flipped, remapped and dropped onto islands. A plan for a night you don't sleep is thrown away at morning.

### 3. The Lucidium dimension (`world/DreamChunkGenerator`, `world/Cells`)
`awaken:lucidium` (`data/awaken/dimension_type/lucidium.json`, `dimension/lucidium.json`, biome
`worldgen/biome/lucidium_dream.json`): fixed lilac golden hour, stars visible by day, a cloud sea at y 70 under the
islands (islands float around y 88–125), spore dust and the odd end-rod mote, cherry-grove music, no weather, no
natural spawns, monsters don't burn, beds don't work. Generator `awaken:dream_cells`: one dream per 512x512 cell
aligned to region files (cell i at ((i mod 64)·1024, (i div 64)·1024)); cells are never reused in a session and the
whole Lucidium folder is wiped on server stop and before server start.

### 4. Falling asleep, waking up (`dream/Sleep`, `dream/Dreams`)
- When you lie down: chance = 40% + SPI% + 10% Sanctuary (any `awaken:*meditation*` block within 6 of the bed) + 5%
  home bed (Hearth within 48) + 10% intention + 30% WBTB, max 95%. Your **first night always dreams**.
- **Sneak** as you lie down (or hold it in bed: the client reads the raw key) for a dreamless night.
- If a dream rolled and the plan is ready, the arrival island is loaded while the screen fades; at sleep timer 90
  (vanilla skips the night at 100) you, and anyone sleeping within 10 blocks, slip into the dream. Plan not ready:
  a normal night and "You slept deeply." Sleep is never blocked.
- In a dream: Adventure mode (survival players), slow falling on arrival, the dream shader. Nothing breaks, explodes,
  or gets placed; things you toss go back in your pack; mobs drop Dream Residue instead of loot.
- Waking (time up, `/awaken dream wake`, the bell, falling twice, dying, logout, server stop): back to the bed's
  stand-up spot (or where you were), game mode, flight and shader restored. If everyone else is asleep (always true
  in single player) the night ends, the weather clears and the Day's End card shows.
- Dying in a dream: you wake **Shaken** (40 s, -8% speed, -10% attack speed), at least half health, nothing lost.
- Logging out mid-dream: the dream state is saved on the player; the next login wakes you in your bed.

### 5. Lucidity (`dream/Lucidity`)
0–100, synced HUD. Start: intention +10 (MILD), WBTB +25, Recall +0.5 per written dream (cap +10), perk
`awaken:lucidity_start`. Gains (each ×(1 + SPI/100)): reality check +12 (H; 3 s cooldown; half after the fifth),
dream sign noticed +15, the door of intention +20, beating the nightmare +10, the first fall +5. Awake, H only counts
`awaken:reality_checks`. In a dream the hand flickers and the HUD text scrambles.
- **Dream Journal** (item; book + feather + amethyst shard; you also wake from your first dream holding one): use it,
  or right-click a bed with it before choosing, to set tonight's intention: find the door / face my fear / fly. Within
  2 minutes of waking it offers "Write down the dream" (+1 Recall).
- **Dream Bell** (block): right-click to arm. When a sleeper with an armed bell within 8 blocks would skip the night
  before midnight, the night stops at midnight instead and they wake; a dreamer whose bed has an armed bell is woken
  at midnight. Sleep again within one in-game hour: +30% dream chance and +25 starting lucidity.

### 6. Lucid powers (`dream/Manifest`, `dream/Mentors`, `dream/ActiveDream`)
- 50+: you can fly (35+ with the "fly" intention); the dream shader sharpens; counts `awaken:lucid_dreams` (first one
  ever: `markNewThing` + best moment).
- Hold H (1.2 s) at 50+: on a wall (6 of 9 blocks solid) a doorway opens with a door; on a floor a stair climbs away;
  into open air a bridge reaches out. Built from the commonest blocks of today's places. Counts
  `awaken:dream_manifests`; shaping in the last 10 s before waking sets `awaken:latent/manifest_woke`.
- Sneak + hold H at 80+: a creature from memory fights beside you (the wolf you tamed, else a guardian golem).
- Rooms you haven't looked at for 6 s (and aren't within 26 blocks of) change: swapped for a spare memory or turned
  around, rewritten over a few ticks (384 blocks per tick).
- Training: powers used in a dream (polled from `awaken:power_uses/*`) give DREAM XP (0.4% of a level each, max 15%
  per dream); the nightmare gives 10%; beating your Path's mentor 20% (`awaken:mentor_duels_won`). The mentor is a
  fresh authored opponent whose blows stop short and who yields at 35% health.

### 7. Nightmares and visions (`dream/Visions`)
- Beat the nightmare (all its mobs gone): `awaken:nightmares_beaten`, that family's `killed_by_week` counters and death
  marks reset, Dream Residue, best moment.
- Visions: at noon the game prepares one: the nearest surface structure (random-spread placement maths + biome check,
  16 samples max, then the chunk loads in the background and the structure is confirmed) 160–1400 blocks away that you
  have never been near; otherwise an old remembered place (3+ days old, 150+ blocks away). The dream shows it undistorted
  inside a ring of dream glass. On waking it becomes a Codex rumour ("Visions": "A waterfall with a door behind it" /
  "It lies far to the north-east of your bed."). Walk within 16 blocks awake: found, `awaken:secrets_found`, and
  `awaken:latent/vision_found_same_day` if it's still the dream's play-day. Dream signs you've noticed also appear in
  the Codex.

### 8. Dream signs
A creature with no shadow (floats, slowly turning, its shadow never drawn), a clock face whose hand spins, signs whose
words scramble every second, a door hanging in the sky, rain falling up (rising drops around you; look up to notice).

### 9. Counters
`awaken:dreams`, `awaken:lucid_dreams`, `awaken:reality_checks`, `awaken:nightmares_beaten`, `awaken:dream_manifests`,
`awaken:latent/manifest_woke`, `awaken:latent/vision_found_same_day`, `awaken:secrets_found` (visions), plus
`awaken:dreams_written`, `awaken:mentor_duels_won`.

## Try it
- `/awaken dream now` (admin): dream right away (captures where you stand if the journal is empty).
- `/awaken dream plan` (admin): tonight's plan, node by node. `/awaken dream journal`: your snapshots and visions.
- `/awaken dream wake`: wake yourself. Admin extras: `capture`, `lucidity <0-100>`, `replay <index>`, `vision`.
- Creative tab "AWAKEN": Dream Journal, Dream Residue, Dream Bell, Dream Cloud (no fall damage), Dream Glass,
  Shifting Stone. Recipes use Dream Residue (bell, 8 glass, 8 stone, 4 clouds).
- `/awaken why` shows the dream chance roll, plans, captures and skips.
- Debug: `AWAKEN_LUCIDIUM_MAP=/path/map.png tools/gradle.sh 1200 runGameTestServer` renders a top-down picture of a
  compiled dream.

## API (`Lucidium`)
`isDreaming(player)` (both sides), `lucidity(player)`, `wakeUp(player)`, `recordDream(player)` → index,
`dreams(player)`, `replay(player, index)` (Dream Recorder: rebuilds the stored plan in a fresh cell, no time skip),
`startShared(players)` (everyone dreams the first player's dream), `dreamlessTonight(player)`.

## Tests (`LucidiumTests`)
`lucidium_snapshot_cap_rotation`, `lucidium_snapshot_capture_file`, `lucidium_plan_determinism` (also codec round
trip, blueprint determinism, cell bounds), `lucidium_palette_remap`, `lucidium_lucidity_maths`,
`lucidium_nightmare_families`, `lucidium_plan_not_ready_fallback`, `lucidium_safe_wake` (enter, wake back in place,
shader and flight restored, dream discarded; a fatal blow wakes you Shaken), `lucidium_dimension_data` (the
GameTest server has no mod dimensions, so the dimension JSON is decoded against live registries).

## Decisions
- Dreams start at sleep timer 90 instead of skipping the night first, so the night passes *in* the dream and the
  Day's End card comes after it. Progression's card is triggered by posting `PlayerWakeUpEvent(wakeImmediately=false)`
  when the morning comes (its `DaysEnd.send` is package-private).
- Per-player data is a player attachment (`awaken:dreamer`, kept on death); the active dream is another
  (`awaken:dream`) with everything needed to wake safely. Runtime (mobs, swaps, signs) is memory only.
- Visions show the real place undistorted so it can be recognised; everything else is distorted.
- Intentions last until the next in-game morning (also across a WBTB second sleep).
- The Sanctuary bonus is "an awaken meditation block near the bed" until Hearth exposes room detection.

## Known gaps
- Not seen in a real client yet: the shader, HUD, models and particles are verified only by compilation and resource
  loading. Look at: `/awaken dream now`, then the HUD on the left edge.
- The Dream Bell's midnight stop uses the vanilla sleep skip, so in multiplayer everyone asleep wakes at midnight, and
  progression's Day's End card appears at that midnight wake.
- `awaken:killed_by_week/*` is never reset weekly by progression; lucidium's own marks use a true 7-play-day window and
  the larger of the two scores counts.
- Shared dreams: the nightmare and the vision are the host's.
- Visions search random-spread surface structures in the overworld only.
