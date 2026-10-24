# HEARTH module

Home base, town, trophies, the Echo army, the Training Grounds and the Reassessment trials. Package `dev.awaken.hearth`
(client code in `dev.awaken.hearth.client`). Feature flag: `AwakenConfig.HEARTH` (off = Anchors refuse to root, no upkeep,
no binding; registrations stay so worlds don't break).

Nothing here needs assigning or babysitting: rooms are recognised from what's in them, the town arrives by itself,
Echoes fill role pads by themselves, and every Y/N choice is optional.

## How to try it

1. `/awaken admin awaken`, then get a Lord's Heart (`/give @s awaken:lord_heart`) and craft a **Hearth Anchor**
   (copper ingot corners, stone bricks, Lord's Heart in the middle; copper rather than obsidian so your first base needs only stone tools). Place it: radius 48, respawn here, Oren arrives and a small
   Spire (Spire Board + Reassessment Monolith + lanterns) rises on open ground near the Anchor. The recipe book learns the
   Hearth's furniture (Meditation Mat, Forge, Trophy Mount, role pads). A toast says "Home exists"; the Now line says
   "Use the Anchor for a guide"; six seconds later Oren offers a cottage (see *The Hearth guide*).
2. Use the Anchor: the **Your Hearth** screen (below). The radius shows in the world for eight seconds. The upgrade
   button (or Y) builds the next tier. `/awaken hearth guide` opens it from anywhere; the Status screen's HEARTH section
   has a button for it too.
3. Build rooms (they must be closed and roofed, at most 32 wide; doors, trapdoors, panes, bars, fences, walls, slabs and
   stairs close gaps):
   - Sanctuary: a bed + a **Meditation Mat** (wool, an amethyst shard, wool in a row).
   - Armory: an anvil + a **Forge** + an armour stand.
   - Trophy Hall: a **Trophy Mount** with a trophy on it.
   - Workshop: any block in `#awaken:machines` (filled by the Above module).
   - Barracks: any **Role Pad**.
   The screen and the Now tracker say what's missing and why a room isn't recognised. `/awaken admin hearth scan` rescans
   now (and lists every "not a room" with its reason); `/awaken hearth info` lists rooms, town, pads and Echo tallies.
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

**Rooms** (`room/RoomScanner`, `RoomType`, `Furniture`). Budgeted job, one at a time, 12k block reads per tick: a palette
sweep (`LevelChunkSection.maybeHas`) skips every section without furniture, then a bounded flood fill (≤4096 cells, ≤32
wide, must not see the sky, unloaded chunks count as open) from each piece of furniture. Full-block furniture (Forge,
machines) is read from the room's walls (and no longer floods the same room twice); armour stands are counted inside the
room's cells. "Sees the sky" is the motion-blocking heightmap plus a look up to 24 blocks for a slab or trapdoor roof.
Rescans 2 s after a relevant block changes nearby (furniture, full blocks, gap closers, armour stands placed or broken),
when the owner uses the Anchor, and every 5 minutes. New rooms are announced with a toast ("Sanctuary recognised" /
"Sleep here: Fracture heals."; not on the first scan after a restart). `Hearth.isSanctuary(level,pos)` and
`Hearth.inRoom(...)` are exact (cell sets).

The rule lives once: `RoomType.furniture` lists what each room needs; `Room.classify`, the toasts, the Status chips, the
guide's checklists and the Now lines all read it. `Furniture` holds each piece's label, short name, icon and the AWAKEN
recipe the guide shows.

**Why a room isn't recognised** (`room/Miss`, `guide/WhyNot`). When a flood leaks, a second bounded fill of the *covered*
space around the furniture (open sky counts as a wall) says why, and furniture in the same leaky space shares the answer
without another fill (≤16 explanations per scan): a covered cell with sky right above it → **it sees the sky above the
bed (no roof)**; sky beside the space inside its footprint or with a wall right behind it → **a hole in the roof at x y
z**; sky beside it at the edge → **an opening at x y z** (the covered cell the space leaks through); bigger than 32 →
**too big (over 32 wide)**. A closed room holding part of a room's furniture → **missing: a Meditation Mat**. The scan
also counts each piece of furniture in the radius (mounted trophies only; armour stands by an entity query at the end)
for the guide's ticks. Cost: one extra bounded fill per leaky space, inside the same 12k-per-tick budget.

**The Hearth guide** (`guide/Guide`, `GuideData`, `client/HearthScreen`, `client/GuideClient`). Using your own Anchor
opens **Your Hearth** (a `QuietScreen`: toasts wait while it's open) instead of a notice; a client without the screen
still gets the old notice and its Y. Status-screen glass with a lantern edge, one scrolling column laid out with
`signal.client.Ui`:
- header: YOUR HEARTH, the tier chip, the Anchor's position; then radius, rest healing (and half in a Sanctuary), where
  you respawn;
- Oren's cottage offer on top until it stands (Raise it / Try again / being built);
- ROOMS: the rule in one line (from `RoomScanner.MAX_EXTENT` and the gap closers), then a card per `RoomType`: name and
  status (Built · x y z, Built ×N, Not yet), what it gives (`RoomType.does`), a checklist with item icons (✓ when that
  furniture is anywhere in the radius; the last line "all of it in one room"), the rule in one line ("A closed, roofed
  room: walls, a roof, a door; at most 32 wide."), the real recipe of each
  missing piece of Hearth furniture drawn as a grid (tag slots cycle; read from the server's recipe manager), the reason
  it isn't recognised (amber), and "Oren built this one. Build the rest yourself." on the cottage's Sanctuary;
- TOWN: who lives here, who comes next and at which rank;
- NEXT UPGRADE: the tier's radius and rest time, each part as have/need with its icon (green when you have it), the rank
  discount, and **[Y] Build Tier II** (the button or the accept key; the server re-checks everything, `Upgrades.upgradeAnchor`).
The screen asks for fresh numbers every 2 s and gets them after every scan, upgrade and cottage step. Using the Anchor
shows the radius in the world for 8 s: amber motes along the square edge at ground level (≤24 a tick, client side).
While the screen is open, and 30 s after, a pulsing signal-blue mark sits on each leak (`hearth_ring`, `hearth_leak`).

**Taught in play** (`HearthNow`, one line on the Now tracker, never a list). Placing the Anchor: the toast "Home exists ·
Within 48 blocks: yours."; at home, "Use the Anchor for a guide" (priority 74 for ten minutes, then 56) until the screen
has been opened once; then the next room with live ticks ("Sanctuary: bed ✓ · mat ✗", shortened to fit 148 px) for a
Sanctuary, then an Armory, then a Barracks, Trophy Hall or Workshop once you hold or placed what it's made of; and, when a
room you're building isn't recognised, why ("Sanctuary: no roof", "Armory: wall gap", with a compass to the spot,
priority 62). Village beds and anvils alone never trigger a why-line (only the current goal, mod furniture, or two pieces
of one room). The toast when a room is recognised closes the loop. Every line is checked against the toast (144 px) and
tracker widths (148 px, 100 with a compass) by `GuideTests`.

**Oren's cottage** (`guide/Cottage`, `CottagePlan`). Once per player, six seconds after the Anchor takes root, a choice
card: "A cottage from Oren? · Free: your first Sanctuary." [Y] Raise it · [N] I'll build my own (no only marks it
declined; the screen keeps the offer). Yes starts a job:
- *Site*: 112 candidate centres 10–22 blocks from the Anchor, the door facing it, ranked by heightmap flatness and
  distance (one tick), then two full checks a tick: every column of the 12 × 13 box must be terrain (dirt, sand, stone,
  terracotta, gravel, clay, snow, mud...) with only plants, snow, vines and natural (non-persistent) leaves above it, up
  to three natural trees (logs whose top touches natural leaves) that get felled, no fluid; the footprint may fill down
  4 or cut up 2, the rim may rise 1; it keeps 2 blocks from the Anchor and 3 from the Spire. Anything a player makes
  (planks, cobble, farmland, paths, placed leaves, chests) rejects the spot. None in 40 tries → a notice ("No room for a
  cottage... clear a patch about 12 blocks across... then ask again in your Hearth") and the screen's Try again.
- *Build*: trees one a tick, then the plants and the bumps in one pass, then four blocks a tick (about seven seconds) in
  order foundation → walls → plate and ceiling → roof → chimney → furnishing, layer by layer and row by row, a row's
  material's place sound at most every three ticks and embers on new blocks; Oren walks to the door and swings a mace as a hammer. It never
  replaces a block that isn't terrain or a plant (someone building there meanwhile keeps it). The server stopping
  mid-build places the rest at once. On completion: the Anchor chime, "Oren's cottage · Your first Sanctuary is up.",
  `HearthRecord.cottage` (a cell inside, saved) and a rescan, so "Sanctuary recognised" follows within a second.
- *Design* (`CottagePlan`, local frame, turned to face the Anchor): 9 × 9 on a stone plinth that fills down to the
  ground; log corner, door and mid posts; a stone lower course with weathered patches; plank walls four high; seven
  two-tall windows with open trapdoor shutters, flower boxes under the two front ones and a light over the door; a log
  wall plate; a plank ceiling (so the room is closed whatever the roof does); a 45° stair gable roof with a one-block
  overhang, thick gable edges (upside-down stairs under the edge), a slab-capped ridge whose log beam shows at both
  gables, and an attic window in each gable; a chimney on the right gable with a campfire on top and a stove behind the
  wall; two lanterns hanging under the front eave; a step or a dirt path toward the Anchor. Inside (7 × 7 × 4): a bed under
  the back window with a barrel nightstand and a lantern, the Meditation Mat, a 3 × 3 rug, a hanging lantern, a chest, a
  crafting table, a smoker, a bookcase and potted plants. Ten biome palettes (meadow, taiga/snowy/cold, birch, jungle,
  savanna, desert, badlands, swamp/dark forest, mangrove, cherry) set the planks, posts, stone, roof, door, shutters,
  chimney, step and bed and rug colours (the desert: pale sandstone, spruce beams and a clay-red acacia roof).
  Previews: the GameTest dumps every palette to `run/structures/cottage_<palette>.json`;
  `python3 tools/art/specs/structures.py preview DIR cottage` draws them (two angles, day and night).

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
`/awaken hearth info` · `/awaken hearth echoes` · `/awaken hearth guide` · `/awaken admin trial <D|C|B|A|S|penalty>` ·
`/awaken admin town arrive <npc id or first name>` · `/awaken admin hearth scan` ·
`/awaken admin hearth cottage` (raise it now) `| reset` (forget the once) `| offer` `| view <outside|inside|side>`
(camera spots for screenshots).

Autopilot: `tools/autopilot/hearth-guide.txt` (the integrator runs it).

## Tests (`HearthTests`, `guide/GuideTests`, all GameTests)
registry + nearest lookup · cost reduction maths · bind chance maths and caps (and Echo levelling) · Echo ally rules (never
targets or hurts its owner or other Echoes) · role pad assignment · room detection in-world (a closed room with a bed and a
mat is a Sanctuary) · D trial passes → rank D · Penalty Zone lifecycle (waves, pass, cleanup) · trials dimension type/biome/
generator decode · pure rules (break counting, lap comparison, compass, arena blocks, ore table, rest times) ·
`hearth_guide_matches_roomtype` (each card's checklist is `RoomType.furniture`, `classify` needs all of it, every toast and
Now line fits, the guide data round-trips, the site rules) · `hearth_cottage_is_a_sanctuary` (two cottages, one turned and
in the desert palette, both scan as Sanctuaries; the guide says Oren built one) · `hearth_why_not_reasons` (roofless →
sees the sky; a gap → an opening at that block; a hole in the roof → there; no mat → missing: a Meditation Mat; fixed →
a Sanctuary) · `hearth_cottage_offer_once` (one card, never twice, not after a no, the screen still offers it).

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

## SPEC-worthy decisions (for the integrator)
1. Using your own Anchor opens the **Your Hearth** screen (a `QuietScreen`); the old notice remains only for clients
   without the screen. The upgrade is built from the screen's button or the accept key, re-validated on the server.
2. Stairs and slabs close gaps like doors and panes (a stair or slab roof is a roof); slabs and trapdoors up to 24 blocks
   above the heightmap count as a roof.
3. Why a room isn't recognised comes from a second bounded fill of the covered space (sky as a wall), ≤16 per scan, in
   the scan's budget: sees the sky (no roof), a hole in the roof, an opening at x y z, too big (over 32 wide), missing: X.
4. The Anchor teaches the Hearth's furniture recipes (Meditation Mat, Forge, Trophy Mount, the five role pads) when it
   takes root, whatever the level.
5. Oren's cottage: offered once per player (a choice card six seconds after the first Anchor), free, never on anything a
   player made: only terrain, plants, natural leaves and up to three natural trees give way. 9 × 9 outside, 7 × 7 × 4
   inside, ten biome palettes, four blocks a tick (~7 s). Its room is a Sanctuary on completion. No paid cottages yet.
6. The Now tracker teaches the Hearth one line at a time: "Use the Anchor for a guide" (74 for ten minutes, then 56) until
   the screen is opened once; then the next room with live ticks (50); a why-line for a room being built (62). Lines are
   chosen server side to fit 148 px (100 with a compass), toasts' first sentences 144 px.
7. Using the Anchor shows the radius (a square, |dx|, |dz| ≤ r) as a ground-level ember ring for 8 s, client side.
8. Saved additions: `HearthRecord.cottage` (optional block pos in `core`), `HearthPlayer.tips` markers `guide_seen`,
   `cottage_offered`, `cottage_declined`, `cottage_built`, `cottage_no_room` (no schema change: both are optional fields /
   free-form tips).
9. Room toasts read "<Room> recognised" with a one-line gain (`RoomType.toast`); the full text (`RoomType.does` and the
   furniture) stays in Recent messages.
