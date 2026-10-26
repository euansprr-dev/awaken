# AWAKEN Hearth audit: what the code does today (for an early, read-your-own-base Hearth)

Read-only audit of `/Volumes/MEDIA/Awaken/.claude/worktrees/world-depth-e0a13d` (branch `claude/world-depth-e0a13d`,
HEAD c7846bc2). Paths are relative to `src/main/java/dev/awaken/` unless they start with `src/`, `docs/`, `tools/`
(repo root) or `/Volumes`. The code is the truth; where SPEC or `docs/modules/hearth.md` differ, the code is quoted.

SPEC drift found on the way (details in each section):
- SPEC §10: "Anchor block (crafted from first boss essence)"; code: a shaped recipe with a Lord's Heart (a).
- SPEC §10: Armory "anvil + forge + rack"; code: anvil + Forge + **armour stand** (`hearth/room/RoomType.java:24`).
- SPEC §21.0 #14: "the Director's first gate opens at level 4, or after 45 minutes outside". `gates/Director.java:196-214`
  only asks that the player is awakened and in the overworld; grep found no level-4 / 45-minute hold anywhere.
- `docs/modules/hearth.md` says "Workshop: a place for the Above's machines"; in code the Workshop gives nothing at all
  (no reader of `RoomType.WORKSHOP` outside the guide and the toasts).

---

## (a) When and how a player gets a Hearth today

### The Anchor

| Fact | Code |
|---|---|
| Recipe: shaped `OSO / SHS / OSO`, O = copper ingot, S = stone bricks, H = `awaken:lord_heart` (4 copper, 4 stone bricks, 1 Lord's Heart). Matches SPEC "Plan v2 · System and Hearth" | `src/main/resources/data/awaken/recipe/hearth_anchor.json` |
| Block: strength 25, blast 1200, light 14, immovable, drops itself | `hearth/HearthContent.java:78-80`, `src/main/resources/data/awaken/loot_table/blocks/hearth_anchor.json` |
| No level, rank or "awakened" check on placing. Refused only when: the Hearth feature is off; not the overworld ("The Anchor only takes root in the waking world."); you already own one; the spot is inside someone else's radius; a household rule (sister homes) | `hearth/HearthService.java:162-188` |
| No check for the city, wards or structures. Carillon's civic protection cancels the place event (`city/CityProtection.java:54-80`), but NeoForge fires that event **after** `setPlacedBy` has run (`/Volumes/MEDIA/Awaken/.ref/neo/net/neoforged/neoforge/common/CommonHooks.java:654-690`), so an Anchor tried inside the walls most likely founds a Hearth ("Home exists") that the next upkeep drops again ("Your Anchor went cold", `hearth/HearthService.java:561-564`). Read from code, not tested. `CityBasin.reserved` (core + 48) is not checked at all | `hearth/block/HearthAnchorBlock.java:28-40` |
| Only the owner (or an op) can break it | `hearth/HearthService.java:351-363` |

### Where a Lord's Heart comes from, and roughly when

| Source | Code | Note |
|---|---|---|
| Every Gate Lord's hoard (per player, into the Satchel) | `gates/run/Loot.java:88-91`, called from `gates/run/Runs.java:465-473` (`claimLord`) | Any rank, E included |
| A LORD-tier boss also drops one on death when a player kills it | `mobs/MobLoot.java:122-128` (`AwakenBoss.dropCustomDeathLoot`, `mobs/entity/AwakenBoss.java:157-159`) | Gate Lords are LORD tier (e.g. `mobs/def/GateBestiary.java:55`); so likely two per Gate Lord kill (not verified in play). Overworld named beasts are WARDEN tier (`mobs/def/FaunaBestiary.java:44, 70`): no Heart |
| A broken gate's roaming Lord: `breakLord` (includes a boss hoard) for each player nearby, plus one more on the ground | `gates/Breaks.java:267-282`, `gates/run/Loot.java:100-106` | |
| A gate merchant stall: 12 essence shards → 1 Lord's Heart | `gates/run/Rooms.java:560` | Rare room |
| Level road gifts at 29, 41, 47 | `src/main/resources/data/awaken/awaken/level_road/your_mentor.json`, `hold_the_line.json`, `the_observatory.json` | Far too late for "early" |

Timing: the first Heart needs the first Gate Lord kill, i.e. finishing an E-rank gate to its last floor. The Director
offers gates to any awakened overworld player every 1000 ticks (100% when none of theirs is open within 1500 blocks,
300-1500 blocks away: `gates/Director.java:41-42, 196-214`). The pacing sim (`tools/sim/pacing_results.json`, balanced)
has level 5 at 0.98 h, level 6 at about 2 h (`level_at_minutes` 120 → 6), and Lords killed in 7.5 of 10 E gates
(careful players 3.7). The sim does not time the first Heart; realistically **1-2 hours in**, around the level-6 road
step.

### How the player first learns the Hearth exists

| Channel | What it says | Code |
|---|---|---|
| Recipe book, very early | Any AWAKEN recipe unlocks the first time you hold one of its ingredients, checked every 40 ticks. Holding a copper ingot or stone bricks puts the Anchor in the book long before you can make it | `progression/RecipeUnlocks.java:19-55` |
| Level road, level 6 (not quiet) | "Hearth Anchor · Craft it from a Lord's Heart: claim a home." Grants the recipe and flag `awaken:hearth`. No Java code reads that flag (grep) | `src/main/resources/data/awaken/awaken/level_road/hearth_anchor.json` |
| Now tracker (awakened only) | Holding a Lord's Heart: "Craft a Hearth Anchor: Lord's Heart, 4 stone bricks, 4 copper" (70); holding an Anchor: "Place your Hearth Anchor where you want your home" (72). Nothing at all before you hold one | `hearth/HearthNow.java:36-47` |
| Status screen HEARTH section | "No Hearth yet · craft an Anchor from a Lord's Heart, 4 stone bricks and 4 copper", but the section is only visible once you have a Hearth **or Echoes** | `hearth/client/HearthSection.java:32-45` |
| Glossary | "Fracture ... Resting at your Hearth or meditating heals it faster." | `signal/ui/Glossary.java:23` |
| NPCs, quests, tips | **None.** No Carillon citizen line, quest or `TipHooks` tip mentions the Hearth or the Anchor (grep of `src/main/resources/data/awaken/city/`, `.../awaken/quest/`, `src/main/resources/assets/awaken/tips/`, `city/`, `citizens/`). Carillon has its own Oren, Mira, Teodor and Sun as citizens (`citizens/CitizensModule.java:108`, `src/main/resources/data/awaken/city/citizens.json`), separate from the Hearth's townsfolk | |

### What placing it does (in order, `hearth/HearthService.java:190-219`)

1. A `HearthRecord` (tier 1, first scan in 20 ticks) and `respawnAtHearth = true`; Anchor sound and 40 embers.
2. Toast "Home exists · Within 48 blocks: yours. You wake here, you heal here, and people will come. Use the Anchor for
   a guide to your Hearth." (`:208-211`).
3. The recipe book learns the Hearth furniture whatever the level: Meditation Mat, Forge, Trophy Mount and the five
   role pads (`:212, 222-230`; list `hearth/guide/Guide.java:65-66`).
4. `markNewThing("hearth")`; the town arrives at the household's rank: at E that is **Oren**, and because someone
   arrived, the **town Spire is raised** (`hearth/town/Town.java:75-77, 108-149, 127`).
5. Six seconds later Oren's cottage offer, once per player ever (`hearth/guide/Cottage.java:83-104`).
6. The first Anchor request comes 90 s later (`hearth/request/Requests.java:74-75, 186-189`).

The radius is a **square**, not a circle, and a slab of height: `|dx| ≤ r`, `|dz| ≤ r`, from 32 below to 64 above the
Anchor; r = 48 + 8 × (tier − 1) (`hearth/HearthRecord.java:241-249`). Tier I = 48, and tier II needs D-rank (c).

### What the player gets inside the radius

| Benefit | Rule | Code |
|---|---|---|
| Respawn | At a safe spot beside the Anchor while `respawnAtHearth`; a bed elsewhere switches it off, using the Anchor switches it on (members: the Hearth whose Anchor they used last) | `hearth/HearthService.java:398-441, 244-245` |
| Rest heals Fracture | Stand still (moved < 0.5 blocks a second) or sleep, not hurt by a mob for 10 s: max(10, 30 − 4 × (tier − 1)) s, so 30/26/22/18/14; half inside a Sanctuary. Only when fractured | `hearth/HearthService.java:482-517` |
| Sleep in a Sanctuary | Clears Fracture, Regeneration I 30 s, toast | `hearth/HearthService.java:466-473` |
| Dreams | A Sanctuary bed (or a Meditation Mat within 6 of the bed, Hearth or not) and a bed within 48 of your Anchor raise the dream chance | `lucidium/dream/Sleep.java:135-137` |
| Calm land (Living Wilds) | Within radius + 96 (Euclidean), creature level = min(region, the household's highest **online** level) | `living/api/Regions.java:36-37, 59-81` |
| No world events on your land | Pulse happenings, Wayfarer scenes and creature behaviours skip Hearth land; camps never form within 120 of an Anchor | `living/pulse/Pulse.java:172`, `living/wayfarer/Scenes.java:177, 265`, `living/pulse/Behaviours.java:83`, `living/camp/Camps.java:177` |
| Gates | A gate vortex never forms within 64 of an Anchor; a gate rolled for someone else never forms within 256 of your Hearth | `gates/Overworld.java:456`, `gates/Director.java:285-292` |
| Wards off | Precursor wards do nothing inside any Hearth's radius | `wilds/ward/Wards.java:62-66` |
| Break warnings (256 blocks) | "A gate broke near your Hearth" (rank, theme, distance, "The Guards are on their way." / "Hold the line. Guard pads would help."), Guards go to it, "The Hearth held" when it is cleared | `hearth/Breaks.java:28, 46-104` |
| Hearth Shield (perk `awaken:hearth_shield`) | Only during an active break near home: mobs can't grief, non-player explosions break no blocks in the radius | `hearth/HearthService.java:369-394` |
| Monster spawns | **Not changed.** Nothing cancels vanilla night spawns in the radius (the only spawn-check listener in the mod is Carillon's lamps, `citizens/CitizensModule.java:76-81`). Guards hunt them | |
| Threat | Tier-4 camps raid the nearest Hearth within 800 (a play-day's warning) | `living/camp/Camps.java:45-49, 404` |

Building XP is **not** a Hearth thing: 0.05% of a level per block placed anywhere in the overworld, budget-capped
(`progression/ProgressionModule.java:247-251, 294-298`).

---

## (b) Room detection (`hearth/room/`)

### The algorithm (`hearth/room/RoomScanner.java`)

| Step | What it does | Code |
|---|---|---|
| 1. Sweep | Every chunk section in the radius (already loaded chunks only, `getChunkNow`): if `section.maybeHas(furniture)` by its palette, read all 4096 blocks and record each piece of furniture inside the radius as a seed (cost 1 for a skipped section, 4097 for a read one). Also tallies furniture for the guide's ticks (trophy mounts only with a trophy) | `:121-157` (palette test `:130`) |
| 2. Order | Seeds sorted nearest the Anchor first ("most likely the owner's own") | `:160-178` |
| 3. Flood | From each seed (a full-block seed like the Forge floods from its open neighbours): BFS over passable cells; **open** (not a room) as soon as a cell sees the sky, an unloaded neighbour is met, more than 4096 cells, or an extent over 32 in x, y **or** z. Cells already claimed by an earlier flood are skipped (they are that room or that leak) | `:182-254`; caps `:45-46`, `:223-233`, `:239-242` |
| 4. Build | For a closed flood: count furniture in the cells, then look at **every wall face** of every cell for full-block furniture (Forge, machines, mounts); armour stands by an entity query in the room's box, counted if standing in a cell; classify | `:384-426` (wall pass `:405-417`) |
| 5. Why not | For a leaky flood: a second bounded fill of the covered space (sky counts as a wall): sky above a start cell → SKY; sky above a cell → SKY at it; sky beside the space inside its footprint or with a wall behind it → ROOF_HOLE at x y z; else OPENING at x y z; over the caps → TOO_BIG. Other furniture in the same leaky space inherits the answer. At most 24 explanations per scan, 4 per kind | `:271-372`, `:257-262`, `:48` |
| 6. Finish | Armour stands in the whole radius counted by one entity query; result handed to `HearthService.onRoomsScanned` | `:376-382` |

Rules:
- **Sky**: a cell sees the sky unless it is below the `MOTION_BLOCKING` heightmap; above it, a look of up to 24 blocks
  for a slab or trapdoor roof (`:443-452`, `ROOF_REACH` `:50`). So anything solid overhead counts as a roof: glass, leaves,
  a cliff, a cave ceiling, a tree canopy.
- **Passable** (room space): air, and any block whose collision is not a full block, except gap closers (`:470-475`).
  Carpets, beds, chests, torches, lanterns, flower pots, signs, farmland, water: all space.
- **Walls**: every full-collision block (any material: dirt, glass, leaves, logs) plus the gap closers: doors, trapdoors,
  fence gates, fences, walls, bars, glass panes, stairs, slabs (`:478-482`). A door or trapdoor closes the room open or
  shut.
- A room's interior height counts towards both caps. 4096 cells is reached before 32 wide by any tall room: a 24 × 24
  hall 8 high inside is 4608 cells and is told "too big (over 32 wide)" (`hearth/guide/WhyNot.java:78`), which is wrong
  for that case.

### Room kinds (`hearth/room/RoomType.java:20-35`; all of the furniture, at least one each, in one closed room)

| Room | Needs | What it gives in code | Code reading it |
|---|---|---|---|
| Sanctuary | bed (head half) + Meditation Mat | Rest heals in half the time; sleeping clears Fracture + Regeneration 30 s; dreams more likely | `hearth/HearthService.java:466-473, 508`; `lucidium/dream/Sleep.java:135` |
| Armory | anvil + Forge + armour stand | Temper cap 2 × Forge tier (2/4/6) instead of 1; the Forge must border the Armory (or Oren tempers to the Hearth's best Armory) | `hearth/Upgrades.java:187-190, 235-246` |
| Trophy Hall | ≥ 1 Lord's trophy on a Trophy Mount | +1% attack damage and +0.5 max health per distinct boss, cap 20 | `hearth/trophy/Trophies.java:41-42, 102-106`; `hearth/HearthService.java:587-593` |
| Workshop | any block in `#awaken:machines` (13 Above machines, `src/main/merge/above/data/awaken/tags/block/machines.json`) | **Nothing** (recognised and shown only) | — |
| Barracks | any role pad | Echoes on pads here heal 1.0 a second instead of 0.25; Train pads train twice as fast | `hearth/echo/Duty.java:153-158` |

Types overlap: `Room.classify` returns an EnumSet (`hearth/room/Room.java:47-53`), so one closed box holding a bed, a
mat, an anvil, a Forge, a stand and a pad is a Sanctuary, Armory and Barracks at once. Any number of rooms of a kind
count ("Built ×N"). There is no minimum size: a bed and a mat walled into a 3-cell nook is a Sanctuary.

### Furniture (`hearth/room/Furniture.java:25-71`)

| Furniture | Block | Awaken or vanilla | Its recipe |
|---|---|---|---|
| bed | `#minecraft:beds`, head half | vanilla | — |
| Meditation Mat | `MeditationMatBlock` | Awaken | `WAW`: 2 wool + amethyst shard (`src/main/resources/data/awaken/recipe/meditation_mat.json`) |
| anvil | `#minecraft:anvil` | vanilla | — |
| Forge | `ForgeBlock` (full block, light 11) | Awaken | bricks ×6, iron ingot ×2, **blast furnace** (`.../recipe/forge.json`) |
| armour stand | entity | vanilla | — |
| trophy | `TrophyMountBlock` holding a trophy item | Awaken | mount: planks ×5 + gold ingot (`.../recipe/trophy_mount.json`); trophy: first kill of each BOSS-role mob, then 20% (`hearth/trophy/Trophies.java:41, 46-59`) |
| machine | `#awaken:machines` | Awaken (Above) | Fabricator blueprints |
| role pad | `RolePadBlock` ×5 | Awaken | 2 smooth stone slabs + essence shard + iron sword / chest / wheat / iron pickaxe / target (`.../recipe/role_pad_*.json`) |

For a Sanctuary at the 1-2 h mark the friction is the amethyst shard (a geode); for an Armory, the blast furnace.

### Rescans, caching, saving

- One scan at a time on the whole server, `SCAN_BUDGET` 12,000 block reads a tick ("roughly 0.2-0.4 ms"), a queue
  filled every 10 ticks, only while the Anchor's chunk is loaded (`hearth/HearthService.java:75-80, 529-547`).
- Triggers: 40 ticks after any room-relevant block is placed or broken in the radius, debounced: furniture, gap closers
  **and any full block** (`:322-335, 327-330, 364`); armour stands placed or broken (`:338-349`); pads (`:281, 295`);
  using the Anchor (1 tick, `hearth/guide/Guide.java:151`); a tier upgrade (`hearth/Upgrades.java:136`); every 5 minutes
  (`RESCAN_EVERY`, `hearth/HearthService.java:77, 532`).
- Results are **runtime only**: `rooms`, `misses`, `furniture`, `hallTrophies` are not saved (`hearth/HearthRecord.java:60-75`);
  after a restart the first scan rebuilds them (`scanAt = 1`, `:69`) and does not toast rooms again (`:73-75`,
  `hearth/HearthService.java:594-606`). Only the cottage's inside cell is saved (`HearthRecord.java:51-52`).
- New rooms: toast "<Room> recognised" + a one-line gain (`hearth/HearthService.java:597-606`).

### What measures quality

**Nothing.** No code reads a room's light, size beyond the caps, materials, windows, decoration, style or comfort.
`Room.volume()` exists and has no caller (`hearth/room/Room.java:24-26`; grep). The only "quality" is the furniture list.

### Free-form builds vs prefab

- **Read from the world**: every room. The scanner never knows who placed a block; a player's own house, a hillside dug
  room, a glass box, a cave or a village house all count if closed and furnished. A base built before the Anchor counts
  on the first scan.
- Exceptions that do not read a pre-built base:
  - Role pads placed **before** the Anchor (or outside the old radius before a tier-up) are never registered: pads join
    a Hearth only in `onPadPlaced` (`hearth/HearthService.java:278-288`; the only `pads.put`). They still count as
    Barracks furniture, but no Echo works them until broken and replaced.
  - Anything more than 32 below or 64 above the Anchor is outside the Hearth (`hearth/HearthRecord.java:245-249`).
- **Prefab**:
  - **Oren's cottage** (`hearth/guide/Cottage.java`, `CottagePlan.java`): offered once per player six seconds after
    the first Anchor ("A cottage from Oren? · Free: your first Sanctuary."), free, never again once built anywhere
    (`Cottage.java:116-124, 127-136`). Site: 112 centres 10-22 from the Anchor, door facing it, flat by the heightmap
    (`:170-206`), every column natural terrain and plants only (`:215-300`). Built at 4 blocks a tick (`:63-66`); box
    12 × 13 (`CottagePlan.java:37`); ten biome palettes (`:86`). It counts as a Sanctuary through the same scanner.
  - **The town Spire**: raised at founding, 6-14 blocks from the Anchor on the first ring spot whose 3 × 3 ground is
    sturdy with no block entity and 3 free blocks above; it **replaces those 9 ground blocks** with polished andesite and
    adds a Reassessment Monolith, a Spire Board and three lanterns (`hearth/town/Town.java:468-525`). Unlike the
    cottage it does not check that the ground is natural, so it can stamp into a player's courtyard floor. No spot →
    the Board and Monolith go to the owner's inventory (`:471-478`).
  - Trial arenas live in their own dimension (`hearth/trial/Trials.java:78`).

---

## (c) Tiers, upgrades and costs (`Upgrades`, `Costs`, `Offers`, `Later`)

Price rule: × 0.85 per rank you hold above the upgrade's rank, compounding, rounded up, never below 1
(`hearth/Costs.java:19, 28-45`). The rank is also a hard gate: below it you are told "Tier II at D-rank"
(`hearth/Upgrades.java:103-107, 128`). Materials are taken from the inventory only (`Costs.java:62-83`).

| Upgrade | Rank | Base price | At own rank / +1 / +2 ranks | Code |
|---|---|---|---|---|
| Anchor Tier II (r 56, rest 26 s) | D | 48 stone bricks, 16 iron ingots, 4 lanterns, 4 essence | D 48/16/4/4 · C 41/14/4/4 · B 35/12/3/3 | `hearth/Upgrades.java:52` |
| Anchor Tier III (r 64, rest 22 s) | C | 48 deepslate tiles, 12 gold ingots, 12 amethyst shards, 8 essence | C 48/12/12/8 · B 41/11/11/7 · A 35/9/9/6 | `:53` |
| Anchor Tier IV (r 72, rest 18 s) | B | 48 polished blackstone bricks, 6 diamonds, 32 copper ingots, 14 essence | B 48/6/32/14 · A 41/6/28/12 · S 35/5/24/11 | `:54` |
| Anchor Tier V (r 80, rest 14 s) | A | 32 obsidian, 12 diamonds, 8 amethyst blocks, 22 essence, 1 Lord's Heart | A 32/12/8/22/1 · S 28/11/7/19/1 | `:55` |
| Forge Tier II (temper to IV in an Armory) | D | 4 iron blocks, 4 coal blocks, 4 essence | D 4/4/4 · B 3/3/3 | `:59-64` |
| Forge Tier III (temper to VI) | B | 3 diamonds, 2 gold blocks, 8 magma blocks, 10 essence | B 3/2/8/10 · A 3/2/7/9 | `:63` |
| Temper level n (1-6) | E for I-II, D for III-IV, C for V-VI | 3n iron; + 2(n−2) gold from III; + (n−3) diamonds from IV; + n essence (none when Oren does it) | — | `:67-74` |

So: vanilla overworld materials plus essence shards (gate loot and level-road gifts); no creature parts; one Lord's
Heart at Tier V. Request Forge credit refunds up to 50% of the next temper or Forge upgrade (f).

What an upgrade changes in the world: **no blocks, no new look**. The Anchor block has no tier state
(`hearth/block/HearthAnchorBlock.java`). Tier raises the radius (+8) and rest speed, rescans, plays a sound and 60
embers, toasts "The Anchor burns brighter. Home got bigger." (`hearth/Upgrades.java:122-147`); it also improves Mine
Echo ore (`hearth/echo/Duty.java:193-203`) and opens sister-home slots from Tier III (`hearth/Household.java:50-53`).
Owner or any member may build it, paying themselves (`Upgrades.java:121-125`). An E-rank player is stuck at Tier I.

`Offers` is the Y/N plumbing: one pending action per key per player, re-validated on accept, expiring with its window
(`hearth/Offers.java:15-51`). `Later` is a tick-delay queue for set pieces such as the cottage card and fireworks
(`hearth/Later.java:11-45`).

---

## (d) The town (`hearth/town/`)

Data-driven definitions from `data/*/awaken/townsfolk/*.json` (`hearth/town/Townsfolk.java:28-41, 104-120`):

| Who (file) | Arrives | Leaves | Service when you talk | Code |
|---|---|---|---|---|
| Oren the Blacksmith (`oren_blacksmith.json`) | E, so **at founding** | — | Tempers the held weapon or armour; brings his own essence; his cap uses the best Armory anywhere in the Hearth | `hearth/town/Town.java:336-344`; `hearth/Upgrades.java:240-246, 266` |
| Mira the Scout (`mira_scout.json`) | D | — | One rumour a play-day into the Codex ("Rumours"): the uncleared gate within 2000 nearest your rank, else a landmark rumour, else the nearest Precursor ruin, else a ruined portal; "Ask me tomorrow" if nothing | `Town.java:346-356, 584-586`; `hearth/town/Rumours.java:35-75` |
| Teodor the Archivist (`teodor_archivist.json`) | C | — | Identifies the held item, or reads the Codex aloud (Path, Truths, Titles, Latents, Gate Lords, last rumour, depth) | `Town.java:358-435` |
| Sun the Healer (`sun_healer.json`) | B | — | Clears Fracture free + Regeneration II 10 s; otherwise Regeneration I 8 s | `Town.java:370-387` |
| Vael the Rival (`vael_rival.json`) | A | S ("Gone to the Court. Don't follow. (Follow.)") | Lines and chatter only | `Town.java:164-175, 331-332` |

- **Arrival**: on founding, every 10 s in upkeep, and on rank-ups; by the household's highest rank online
  (`Town.java:83-149, 95-102, 226-227`). Notice "<Name> arrived" / "Someone moved in".
- **Where they stand**: spawned at a safe spot beside the Anchor (`Town.java:178-193`), home radius 20
  (`Town.java:71, 185`), wander, open doors, look at players (`hearth/town/TownNpc.java:86-95`); teleported back past 48;
  respawned when missing, only while the whole home area is entity-ticking (`Town.java:213-225, 239-242`). **No
  buildings, rooms, beds, jobs, schedules, day/night routines or routes**: "Never needs anything from you: no beds, no
  jobs, no assignments." (`TownNpc.java:37-41`). They do not use the player's rooms in any way.
- **Entity and look**: `awaken:townsfolk`, a `PathfinderMob`, MISC, 0.6 × 1.95, invulnerable, persistent
  (`hearth/HearthContent.java:155-156`; `TownNpc.java:41-52, 114-121`); drawn by `SkinnedMobRenderer` with
  `textures/entity/townsfolk/<first word of id>.png` (`hearth/client/TownNpcRenderer.java:15-38`).
- **Talking**: rank line, or a chatter line one time in three; to a stranger "They're busy looking after someone else's
  Hearth." (`Town.java:304-334`). Services are hard-coded by skin name (`:326-333`), so a new JSON townsperson gets
  lines but no service. They do not call `Quests.talk`.
- **Recognition**: a rank-up is celebrated at home (now or on return): 7 fireworks in the rank colour, townsfolk gather
  for 15 s, a window with up to 4 of their lines (`Town.java:253-289`). The nearest comments on a newly hung trophy
  (`:438-463`).
- **The Spire Board**: owner's name, rank, level, gates cleared, Gate Lords, deepest stratum, Echoes, best lap; refreshed
  every 10 s; using the Spire tells the owner the next Reassessment (`Town.java:529-573`). The Monolith beside it runs
  the rank trials (e).

**Reputation** belongs to the Anchor requests and is saved **per player** (`hearth/request/RequestState.java`, attachment
`hearth/request/RequestContent.java:27`), not per Hearth: each member of a shared Hearth has their own standing.
Tiers and town touches (`hearth/request/RequestRules.java:170-175`):

| Standing | At | Touch in code |
|---|---|---|
| Known | 6 | Townsfolk within 48 walk over to watch a delivery; the nearest says a line (`hearth/request/Requests.java:552-582`) |
| Trusted | 18 | At night, within 32 of the Anchor, embers rise round it, sent only to you (particles, no lamps placed) (`:584-595`) |
| Valued | 40 | +25% essence on requests (`RequestRules.java:40, 157-158`) |
| Beloved | 80 | Once a play-day: 4 bread, 3 + 2 × rank essence, 25% a Power Core Shard (`Requests.java:597-612`) |

---

## (e) Workers and Echoes (`hearth/echo/`, role pads), Training, Trials, Trophies

**Binding**: a mob with a bindable `MobRole` (elite, warden, boss) killed by a player lingers 10 s; press B within 6
blocks (`hearth/echo/Echoes.java:76-78, 155-171`). Chance: base 55% elite / 35% warden / 20% boss, +1% per SPI over 5,
+10% per rank above it (−15% per rank below), + perk, clamped 5-95%; bosses only at S-rank or as a Shadowbinder
(`hearth/echo/BindMath.java:18-42`). Caps: field 3 + SPI/6, total = field + one per role pad, at most 12 bodies out
(`BindMath.java:16, 45-52`). Pads take Echoes that aren't marching, strongest first, Guard pads first, then in
placement order (`hearth/echo/Duty.java:79-94`). Duty runs only while the **owner** is online and the Anchor is
entity-ticking (`hearth/HearthService.java:566-571`); members' Echoes never work pads.

| Role | What it actually does | Output goes to | Code |
|---|---|---|---|
| Guard | Patrols within max(8, r/2) of the Anchor; targets any `Enemy` within 16 that is inside the Hearth, or round a break it was sent to (48) | — | `hearth/echo/DutyGoals.java:39-41, 88-169` |
| Haul | Picks up loose items within 24 (±6 y) of its pad, inside the Hearth, older than 5 s | The chest or barrel already holding that item (any distance), else the nearest one within 16 of the pad, else dropped | `DutyGoals.java:172-264`; `Duty.java:238-306` |
| Farm | Harvests fully grown `CropBlock` crops within 10 (±2 y) of its pad, resets them to age 0, keeps one seed back | Same chest rule | `DutyGoals.java:265-386` |
| Mine | **Does not mine.** Every 2 min of duty a roll: coal 3+t, raw iron 2+t/2, raw copper 3+t, redstone 4+t, lapis 3+t, raw gold 1+t/2, rare diamond (t = Anchor tier; Echo level and tier shift the odds); stone-dust particles on the pad | Same chest rule from the pad | `Duty.java:50, 173-203` |
| Train | +1 Echo XP a minute (twice in a Barracks); spars on the pad | — | `Duty.java:51, 157-163`; `DutyGoals.java:387+` |

Pads heal their Echo 0.25 a second (1.0 in a Barracks) (`Duty.java:153-154`). Pad recipes are taught when the Anchor
takes root (a), and again at level 30 (`src/main/resources/data/awaken/awaken/level_road/echo_roles.json`).

**Training Grounds** (`hearth/training/Training.java`): Start Gate, Checkpoint Rings (join the nearest Start Gate within
64, in placement order), Finish Gate, optional Target Dummies; laps up to 10 min, your best replayed as a ghost (up to
5 min) (`:45-49`). Works anywhere, Hearth or not.

**Reassessment trials** (`hearth/trial/Trials.java`): touch any Monolith (the town Spire has one; the recipe comes at
level 10) at the rank's level (D 10, C 25, B 45, A 70, S 100: `progression/Rank.java:11-17`); one try per play-day; run
in the `awaken:trials` dimension (`Trials.java:78, 186-223`).

**Trophies**: a player-killed BOSS drops its first trophy per boss id, then 20% (`hearth/trophy/Trophies.java:41, 46-59`);
the Trophy Hall bonus is applied to everyone who lives there (`hearth/HearthService.java:608-613`).

---

## (f) Requests (`hearth/request/`)

Only with a Hearth and awakened; one at a time, two from C-rank (`hearth/request/RequestRules.java:54-56`); offered
quietly 90 s after founding, each play-day and 30 s after each delivery, never in a fight, a gate, asleep or outside
the overworld; an offer lasts one play-day; declining or ignoring costs nothing (`hearth/request/Requests.java:74-82,
153-200`). Delivery: use the Anchor carrying them; part loads count (`hearth/HearthService.java:246`; `Requests.java:305-340`).

Tokens (only things you can really get, `Requests.java:123-143`): `seen:<creature>` = a fauna discovery counter;
`killed:<entity>` = vanilla kill statistic; `mined:<block>` = vanilla mined statistic; `got:<item>` = picked up or
crafted statistic; `depth:<y>` = the lowest overworld Y you stood at. Others go to progression's `Requirements`.

Pools: 96 files in `src/main/resources/data/awaken/awaken/request/` (schema 1; `{item|tag, count{rank:n}, weight,
value, requires, any_of, hint}`): **34 vanilla**, 62 Awaken creature parts and Lord relics. Vanilla asks (count by rank;
token that unlocks it):

| Rank band | Vanilla materials asked for |
|---|---|
| E (from day one) | logs 16 (any), wool 6, string 8, bone 8, feather 8, leather 6, wheat 12 (no token); coal 12 (mined coal), raw copper 12, raw iron 8 (mined ore); moss block 8 (seen Mossback), turtle scute 3 (seen Mossback), glowstone dust 8, honeycomb 4, pink petals 12, quartz 6, slime ball 4, phantom membrane 4 (a sighting or a kill) |
| D | the above at higher counts, + amethyst shard 8, lapis 12, redstone 16, raw gold 6, cobbled deepslate 32 (`depth:0`) |
| C | diamond 4, emerald 4, ender pearl 4, obsidian 8 |
| B-S | blaze rod, ghast tear, netherite scrap, prismarine crystals, wind charge, shulker shell, echo shard |

Rewards (`RequestRules.java:155-165`), effort = count × value (`hearth/request/ActiveRequest.java:31-33`): essence
max(2, round(effort × (0.6 + 0.2 × rank) × streak × 1.25 if Valued)); min(0.4, 0.02 × effort) of a level through
`Progression.worldXp` source `hearth_request` (its own budget); Forge credit +10% (+15% from effort 12), capped 50%; a
Power Core Shard with chance min(0.6, 0.04 × effort) plus one on streak days 3, 5, then every 5th; a 25% chance of a
recipe from the next 6 levels of the road; reputation max(1, round(effort / 2)). Streak +10% a delivery day, up to
+50%, lapses after 3 play-days (`:32-34, 126-144`; `Requests.java:363-387, 419-447`).

---

## (g) The guide (`hearth/guide/`) and the "Your Hearth" screen

Opened by using your own Anchor (unless a request delivery happens first), `/awaken hearth guide`, or the Status
screen's button; a client without the screen gets the old notice (`hearth/guide/Guide.java:143-153`;
`hearth/Upgrades.java:85-119`). Data (`hearth/guide/GuideData.java:17-19`), built in `Guide.java:180-189`:

- Header: tier, radius, rest time (and half in a Sanctuary), where you respawn, the Anchor's position.
- Oren's cottage offer, on top until it stands (Raise it / Try again / being built / "Changed your mind?").
- ROOMS: "Build a room, put its furniture inside, and the Hearth notices: no menus. Doors, glass panes, fences, walls,
  slabs and stairs close gaps." (`hearth/client/GuideSections.java:150-151`); a card per `RoomType`: Built · x y z /
  Built ×N / Not yet, what it gives, a furniture checklist ticked when that piece is **anywhere** in the radius, the
  missing Awaken furniture's real recipe grid, the why-line ("Closed and roofed, but missing: …", "Not a room yet: it
  sees the sky above the bed (no roof).", "… an opening at x y z. Close it with a block, a door or a glass pane.", "…
  too big (over 32 wide)…"), "Oren built this one. Build the rest yourself." (`Guide.java:192-214`;
  `hearth/guide/WhyNot.java:31-82`).
- TOWN: who lives here and who comes next at which rank (`Guide.java:249-270`).
- NEXT UPGRADE: radius, rest, each part as have/need, the rank discount, **[Y] Build Tier II**, re-validated on the
  server (`Guide.java:125-128, 272-287`).
- In the world: the radius as a ground-level ember square for 8 s; leak marks while open and 30 s after; the screen
  asks for fresh data every 2 s (`hearth/client/GuideClient.java:27, 45-47, 101-102`).

Now-tracker lessons, one line at a time (`hearth/HearthNow.java:36-155`): "Use the Anchor for a guide" (74 for ten
minutes, then 56) until opened once; then a why-line for a room being built (62, with a compass); else the next room
with live ticks ("Sanctuary: bed ✓ · mat ✗", 50): Sanctuary, then Armory, then Barracks / Trophy Hall / Workshop once you
hold or placed their furniture. Village beds and anvils alone never raise a why-line (`:88-100`).

---

## (h) Multiplayer ("Together"): `Household`, members, the `Hearth` API

- One owner, any number of members; a player owns at most one Hearth and may share many (`hearth/HearthRecord.java:53-58`;
  saved household schema 1 `:142-152`; `HearthData` schema 2 with `kept` households, `hearth/HearthData.java:24`).
- Joining: `/awaken hearth invite <player>` (2-minute card) or a stranger using the Anchor asks the owner (once per 30 s)
  (`hearth/Household.java:28-32, 57-99, 183-210`). Leaving / removing: `:121-142`.
- Members share respawn, rest, the guide, deliveries, the Trophy Hall bonus, townsfolk services, break warnings, the
  Shield, and may pay for upgrades. Owner only: moving the Anchor, inviting, Oren's cottage (`Cottage.start` uses
  `hearthOf`), and role pads (owner's Echoes). Requests and reputation are per player.
- The town follows the household's highest online rank; the calm land the highest online level (d, a).
- Sister homes: from Tier III a member's own Anchor founds one; one per tier from III (`Household.java:28, 50-53,
  144-173`).
- Public API `dev.awaken.hearth.Hearth` (`hearth/Hearth.java:29-103`) matches SPEC §12: `HearthInfo(owner, ownerName,
  anchor, tier, radius, members, foundedFrom)` + `has`, `hearthsOf(server, uuid)` / `hearthsOf(player)`, `home`,
  `isMember` ×2, `hearthAt`, `isSanctuary`, `inRoom`, `hasRoom(player, type)`, `ownedEchoes`, `anchor`, `atHome`. Rooms
  are exposed only as yes/no queries: no list of rooms, their cells or any measure.
- `core.api.HearthHooks` (`core/api/HearthHooks.java:19-90`) is the install-once bridge most modules use (`inside`,
  `nearest`, `within`, `belongs`, `hearthsOf`, `household`, `shareHearth`).

---

## (i) Verdict for the designer

| Question | Answer from code |
|---|---|
| Earliest realistic time to place a Hearth | No level gate on placing; the gate is the Lord's Heart, which needs a Gate Lord kill (finishing an E gate) or a broken gate's Lord. Level road teaches it at level 6, ~2 h for the balanced sim; the first Heart realistically comes at 1-2 h. Nothing ties it to leaving Carillon (`gates/run/Loot.java:91`, `mobs/MobLoot.java:128`, `hearth/HearthService.java:162-188`) |
| How a new player learns of it | The recipe book (holding copper or stone bricks), the level-6 road card, and Now lines only once a Heart or an Anchor is in the pack. No NPC, quest or tip. The Status screen's line is hidden until you have Echoes (`progression/RecipeUnlocks.java:19-55`, `hearth/HearthNow.java:40-46`, `hearth/client/HearthSection.java:32-34`) |
| Does a hand-built base count, and how far | Yes, fully, for rooms: any closed, roofed space with the furniture, any material, built before or after the Anchor, caves included. Limits: the square radius (48 at Tier I) from −32 to +64 Y; ≤ 4096 cells and ≤ 32 in each axis (tall halls fail early, with the wrong reason); loaded chunks only; pre-existing role pads ignored; the Spire may replace 9 of your ground blocks (`hearth/room/RoomScanner.java:45-46, 230-233`, `hearth/HearthService.java:278-288`, `hearth/town/Town.java:482`) |
| What is prefab-only | Oren's cottage (once per player), the town Spire (always, at founding), trial arenas. Townsfolk have no buildings (`hearth/guide/Cottage.java`, `hearth/town/Town.java:468-494`) |
| What measures quality | Nothing: only furniture presence. No light, size, material, window, decoration, style or comfort read; `Room.volume()` is unused (`hearth/room/Room.java:24-26`) |
| Which vanilla materials the Hearth consumes | Anchor: 4 copper ingots, 4 stone bricks. Upgrades: stone bricks, iron, lanterns, deepslate tiles, gold, amethyst shards and blocks, polished blackstone bricks, diamonds, copper, obsidian; Forge: iron and coal blocks, diamonds, gold blocks, magma; tempering: iron, gold, diamonds. Requests ask 34 vanilla items (f). Furniture: wool, amethyst shard, bricks, blast furnace, iron, planks, gold, smooth stone slabs (`hearth/Upgrades.java:49-74`) |
| Cheap hooks to extend room kinds and quality | (1) A new `RoomType` from existing furniture is one enum line: classify, toasts, guide cards, why-lines and Now lines all read `RoomType.furniture` (`hearth/room/RoomType.java:15-35`). (2) New furniture: one case in `Furniture.of` (tag-based groups like `#awaken:machines` let data add blocks, `hearth/HearthContent.java:74`), but counts are fixed fields of the `Room` record, so also `Room.java:13-14, 33-44` and `RoomScanner.build`. (3) Quality: `RoomScanner.build` (`:384-426`) already visits every cell and every wall face of a closed room, so light, floor area, height, wall materials, glass and decoration could be tallied in that pass at no extra block reads (light is a separate engine read). (4) The guide/WhyNot/Now plumbing carries any new reason. (5) Townsfolk are JSON (services are code). (6) Living Wilds Phase G (settlers "each needing a room the Hearth recognises", `docs/living-wilds/PLAN.md:480-500`) is not built: no `living/village` package |
| Perf limits of the scanner | 12,000 reads a tick, one Hearth at a time server-wide (`hearth/HearthService.java:75-80, 529-547`). Sweep: about 343-512 sections at r 48, up to ~850 at r 80; 1 read per empty section, 4097 per section whose palette may hold furniture. A flood costs ~8 reads a cell, ≤ 4096 cells; ≤ 24 extra why-fills. Any full block placed in the radius schedules a rescan 2 s later (debounced), plus every 5 min. Per-flood allocations (a hash set and a queue per seed, `RoomScanner.java:190-191`). Results are not saved; recomputed after every restart |
