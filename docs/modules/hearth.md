# HEARTH module

Home base, town, trophies, the Echo army, the Training Grounds and the Reassessment trials. Package `dev.awaken.hearth`
(client code in `dev.awaken.hearth.client`). Feature flag: `AwakenConfig.HEARTH` (off = Anchors refuse to root, no upkeep,
no binding; registrations stay so worlds don't break).

Nothing here needs assigning or babysitting: rooms are recognised from what's in them, the town arrives by itself,
Echoes fill role pads by themselves, and every Y/N choice is optional.

## How to try it

1. `/awaken admin awaken`, then get a Lord's Heart (`/give @s awaken:lord_heart`) and craft a **Hearth Anchor**
   (obsidian corners, stone bricks, Lord's Heart in the middle). Place it: radius 48, respawn here, Oren arrives and a small
   Spire (Spire Board + Reassessment Monolith + lanterns) rises on open ground near the Anchor.
2. Use the Anchor: tier, radius, rooms, town, and the next upgrade with its (rank-discounted) price. Y builds it.
3. Build rooms (they must be roofed and closed; doors, panes, fences and walls close gaps):
   - Sanctuary: a bed + a **Meditation Mat** (3 wool around an amethyst shard).
   - Armory: an anvil + a **Forge** + an armour stand.
   - Trophy Hall: a **Trophy Mount** with a trophy on it.
   - Workshop: any block in `#awaken:machines` (filled by the Above module).
   - Barracks: any **Role Pad**.
   `/awaken admin hearth scan` rescans now; `/awaken hearth info` lists rooms, town, pads and Echo tallies. The Status screen
   (K) has a HEARTH section.
4. Echoes: kill an elite/warden/boss (`MobRole.isBindable()`), tap **B** within 6 blocks during the 10 s it lingers.
   Tap B elsewhere to call/dismiss your field army; hold B for the chooser (march / home / release). `/awaken hearth echoes`.
5. Role pads (shapeless: 2 smooth stone slabs + essence shard + iron sword / chest / wheat / iron pickaxe / target).
6. Training Grounds: place a **Start Gate**, then **Checkpoint Rings** in the order to run them (each shows its number),
   then a **Finish Gate**; add **Target Dummies** for a combat drill. Step on the start to run.
7. Reassessment: reach the rank's level, touch a Monolith, press Y. `/awaken admin trial <D|C|B|A|S|penalty>` starts one
   anywhere (a practice run unless you're actually ready for that rank). `/awaken admin town arrive <npc>` brings someone in.

## What's built

**Anchor and data** (`HearthService`, `HearthData`, `HearthRecord`). One Hearth per player, overworld only, saved in
`data/awaken/hearths.dat`. `HearthHooks` installed (nearest / hearthOf). Respawn: placing or using your Anchor sets
`respawnAtHearth`; `PlayerRespawnPositionEvent` sends you to a safe spot beside it (a bed elsewhere switches it off). Only the
owner (or an op) can break it. Upkeep runs once a second per Hearth, staggered by owner, and never allocates.

**Fracture heals at home**: stand still out of combat inside your radius for `30 - 4×(tier-1)` seconds (half that in a
Sanctuary; an overlay counts down) or sleep in a Sanctuary → `Progression.clearFracture`. Sun the Healer clears it for free.

**Rooms** (`room/RoomScanner`). Budgeted job, one at a time, 12k block reads per tick: a palette sweep
(`LevelChunkSection.maybeHas`) skips every section without furniture, then a bounded flood fill (≤4096 cells, ≤32 wide,
must not see the sky, unloaded chunks count as open) from each piece of furniture. Full-block furniture (Forge, machines) is
read from the room's walls; armour stands are counted inside the room's cells. Rescans 2 s after a relevant block changes
nearby, and every 5 minutes. New rooms are announced (not on the first scan after a restart). `Hearth.isSanctuary(level,pos)`
and `Hearth.inRoom(...)` are exact (cell sets).

**Costs that fall** (`Costs`, `Upgrades`). Price ×0.85 per rank you hold above the upgrade's tier (compounding, rounded up,
never below 1). Anchor tiers II-V (D, C, B, A): radius 48/56/64/72/80, faster rest healing; mostly overworld materials plus a
little essence (tier V also a Lord's Heart). Forge tiers II (D) and III (B). Tempering: weapons +1 damage per level, armour
+0.5 armour +0.25 toughness per level, stored in `awaken:temper`; limit 1 anywhere, 2 per Forge tier in an Armory (max VI).
Oren tempers too and brings his own essence.

**Trophies** (`trophy/Trophies`). A player-killed `MobRole` BOSS always drops its first trophy (per boss id), then 20% of the
time; boss id/name/day in the `awaken:trophy` component. Hanging one counts `awaken:trophies_placed` (once per trophy) and
`markNewThing` for a new boss. Trophy Hall bonus per different boss: +1% attack damage, +0.5 max health (cap 20), as
transient modifiers re-applied on login, respawn and scans. The nearest townsperson comments.

**Town** (`town/`). Definitions from `data/awaken/awaken/townsfolk/*.json` (added `vael_rival.json`: arrives at A, leaves at
S with a note). `TownNpc` (player model, generated skins) wanders around the Anchor (`setHomeTo`, opens doors), can't be hurt,
gathers for celebrations, and is respawned if missing (only when the whole home area is loaded; duplicates loaded later
remove themselves). Talking: rank line (or chatter) through `Signal`, plus services: Oren tempers held gear; Mira gives one
rumour per play-day (nearest gate near your rank via `GateHooks.near`, else `#awaken:precursor_ruins`, else a ruined portal)
that becomes a Codex entry (category "Rumours"); Teodor reads your Codex aloud (Path, Truths, Titles, Latents, Gate Lords,
rumours, depth) or identifies the held item; Sun clears Fracture.

**Recognition**. `RankHooks.onRankUp`: new arrivals, then a celebration at the Hearth (now if you're home, otherwise when you
get back): seven fireworks in the rank colour, townsfolk gather to you, a "The town gathers" window with each one's line for
the new rank, the Spire Board refreshes. Rank auras: `awaken:aura` attachment (rank + rank/Path colour) synced to everyone
nearby; the client draws a few motes (more per rank, embers at S+), off when `AwakenConfig.Client.AURA` is false.
Day's End lists a pending celebration.

**Echoes** (`echo/`). Binding: base 55% elite / 35% warden / 20% boss, +1% per SPI over 5, +10% per rank above the fallen
(−15% per rank below), + `Perks.BIND_CHANCE`, clamped 5-95%. Bosses need S-rank or the Shadowbinder Path. One attempt per
lingering Echo. Field cap `3 + SPI/6`; roster cap = field cap + one per role pad. Roster in the player attachment
(`EchoRecord`: entity type, saved entity data minus position/health/UUID/brain, name, level, xp). Summons are faithful copies
of the original type: old goals cleared and replaced (`EchoGoals`: follow owner, strike whatever hurt the owner or what the
owner hit, hunt monsters, melee or ranged), tagged `Forces.Tag.ECHO` and `MobRole` ECHO, scaled by content level, origin and
Echo level, fire-proof, no drops/xp. Allies are enforced three ways: target goals, `LivingChangeTargetEvent` and
`LivingIncomingDamageEvent` (owner, the owner's Echoes, townsfolk and ghosts are never targets or victims). Strikes are
credited to the owner (gate kill XP still counts). Echoes level slowly (kills 1/5/10/25 xp by role, Train pads), fall and
reform after 60 s, follow you across dimensions, leave with you on logout. At most 12 field bodies. Counters:
`awaken:echoes_bound`, `awaken:echo_army_peak`. `Hearth.ownedEchoes(player)` for echo_swap. Clients draw Echoes dark with a
violet outline.

**Duty** (`echo/Duty`, `DutyGoals`). Pads take Echoes that aren't marching (strongest first to Guard pads, then pads in
placement order). Guard: patrols the Hearth, hunts monsters in the radius, goes to hold a break. Haul: picks up loose items
(older than 5 s) and puts them in the chest that already holds that item, else the nearest chest. Farm: harvests ripe crops
within 10 blocks, replants (keeps one seed back), stores the harvest. Mine: every 2 minutes of duty brings ore "from below"
(coal/iron/copper/redstone/lapis/gold, rare diamond; better with Echo level and Anchor tier) into a chest. Train: +1 Echo xp a
minute (×2 in a Barracks). Duty Echoes heal on their pads (faster in a Barracks) and run only while the owner is online and
home is loaded.

**Breaks near home** (`Breaks`). Every 5 s: `GateHooks.near(level, anchor, 256)`; a BROKEN gate is recorded per gate id with
the owner's `breaks_held_at_hearth` value at that moment, Guards go to it. When that gate shows CLEARED and the counter hasn't
moved (the gates module didn't count it), we count it once. `Perks.HEARTH_SHIELD` during an active break: mobs can't grief
and non-player explosions don't break blocks inside the radius.

**Training Grounds** (`training/`). Courses keyed by Start Gate; rings join the nearest Start Gate within 64 blocks in
placement order (renumbered on removal); dummies count when struck or hit by your projectile. The clock holds at zero while
you stand on the start. Laps count `awaken:training_laps`; beating your best counts `awaken:training_records` and
`markNewThing`. Your best run is recorded per tick (≤5 min) and replayed as a translucent ghost the next time you leave the
start. Bests per player; ghosts in `data/awaken/training.dat`.

**Reassessment** (`trial/`). Dimension `awaken:trials` (fixed night, no weather, generator `awaken:trials`): 48×48 walled
arenas on a 512-block grid, even cells are halls (tile and amber lamps), odd cells are the desert-at-night Penalty Zone. One
cell per running trial (`TrialData`). D: three waves. C: two waves then the Second Awakening (`Progression.offerPaths`, a
window listing the Paths that answered). B: a Gate Lord of a theme fitting the rank (`MobHooks.spawnBoss`, falls back to a
themed BOSS, then a scaled zombie). A: five stacked rooms in four minutes; each cleared room's floor opens (slow falling).
S: `awaken:sovereign_mirrors` at half health, with its intro line from the story data. Mobs via `MobHooks.spawnThemed`. Trials
never kill (a lethal hit ends the attempt). Pass → `Progression.passReassessment` and home; fail → home, retry next play-day.
Logging out or leaving fails it; logging in inside the trials dimension sends you home.

**Hardline Penalty Zone**: `Regimen.penaltyHandler` starts it when you're in the overworld and free (otherwise it waits):
survive four minutes of waves in a desert arena; failing Fractures you for 8 minutes.

## Commands
`/awaken hearth info` · `/awaken hearth echoes` · `/awaken admin trial <D|C|B|A|S|penalty>` ·
`/awaken admin town arrive <npc id or first name>` · `/awaken admin hearth scan`.

## Tests (`HearthTests`, all GameTests)
registry + nearest lookup · cost reduction maths · bind chance maths and caps (and Echo levelling) · Echo ally rules (never
targets or hurts its owner or other Echoes) · role pad assignment · room detection in-world (a closed room with a bed and a
mat is a Sanctuary) · D trial passes → rank D · Penalty Zone lifecycle (waves, pass, cleanup) · trials dimension type/biome/
generator decode · pure rules (break counting, lap comparison, compass, arena blocks, ore table, rest times).

## Decisions
- Trials live in their own `awaken:trials` dimension (the builder brief asked for it) rather than the Stillpoint's trial wing
  in SPEC 4.4. SPEC should be updated to match.
- Roster cap = field cap (3 + SPI/6) + role pads, which is how "Echoes on duty don't count against the field army" works.
- Echo strikes credit the owner so gate kill XP and floor kill counts keep working for Shadowbinders.
- Costs fall multiplicatively (×0.85 per rank above), so they never reach zero.
- Trial bosses and wave mobs (gate id `trial:<uuid>`) never drop trophies and can't be bound.
- The SOVEREIGN rank has no Monolith trial: the Monolith says it waits at the Seed (story module).

## Known gaps
- The GameTest server only creates the overworld, so arena generation in the real dimension is covered by decoding the
  dimension JSON and testing the generator's block rules, not by loading a chunk.
- Echo copies of brain-driven mobs (piglins, wardens) keep their brain; the damage filter still stops them hurting you.
- Gate kills by Echoes while you are bare-handed count toward `awaken:kills_unarmed` (progression counts any kill credited
  to an unarmed player).
- Only the town Spire's board refreshes on its own; extra Spire Boards refresh when used.
