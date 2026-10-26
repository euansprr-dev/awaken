# AWAKEN lock audit: gates, locks, cheese routes, readability

Read-only audit of `/Volumes/MEDIA/Awaken/.claude/worktrees/progression-8ed284` (branch `claude/progression-8ed284`,
HEAD 84bea58c). Paths below are relative to `src/main/java/dev/awaken/` unless they start with `src/`, `docs/` or
`/Volumes`. The code is the truth; where SPEC and code differ, the code is quoted.

Note: `body/ascent/TetherVow.java`, `AscentPayloads.java`, `client/VowScene.java`, `above/ruin/PrecursorRuinPiece.java`
and the ascent lang file have **uncommitted edits** in this worktree (the separate Vow fix: C-rank, door open, a
"stir"). TetherVow line numbers are against that working copy.

---

## (a) Lock inventory

Legend for the verdict: **Good** = readable and enforced; **Soft** = by design a warning, not a wall; **Weak** = enforced
but hard to read; **Hole** = can be bypassed by an ordinary survival player.

### Movement- and site-gated (the Wilds, the Ascent)

| Lock | Code | What it gates | How the player learns it in the world | Exact on-screen string | Cheese routes (details in b) | Verdict |
|---|---|---|---|---|---|---|
| **Precursor ward: placed blocks crumble** | `wilds/ward/Wards.java:38-40` (MARGIN 12, SKY_BELOW 48), `:62-66` (`enforced`), `wilds/ward/WardEvents.java:64-77`, `wilds/ward/Crumble.java:32` (DELAY 60 ticks) | Building at sites tagged `#awaken:warded` (vault, spawn vault, ocean lab, monolith: `src/main/merge/wilds/data/awaken/tags/worldgen/structure/warded.json`; Tether Spire: `src/main/merge/ascent/.../warded.json`) and `#awaken:warded_sky` (sky platform: `src/main/merge/wilds/.../warded_sky.json`). Hero-tree crowns via a code Source (`body/ascent/HeroCrowns.java:187-192`) | Nothing until you place a block. Then: MARK sparks + chime, 3 s later CRUMBLE grains + sound (`WardFx.java:57-70`, `wilds/client/WardFxClient.java`); the item comes back. A toast **once per player, ever** (flag `awaken:ward_noticed`, `WardEvents.java:69-73`) | Toast title "Precursor ward", body "Nothing you bring stays here. It comes back to you." | 3-second window lets a nerd-pole or speed-bridge keep going (you always stand on a block under 3 s old); any Hearth's radius switches the ward off; buckets fire no place event; pistons placed outside push in; towers outside the box | **Hole + Weak.** Invisible until used; never says what the site wants; once per lifetime; does not stop a continuous climb |
| **Ward box sizes** | `Wards.java:96-99`; vanilla `Structure.adjustBoundingBox` inflates by 12 when `terrain_adaptation` is not none (`/Volumes/MEDIA/Awaken/.ref/src/net/minecraft/world/level/levelgen/structure/Structure.java:82-84`); structure JSONs in `src/main/resources/data/awaken/worldgen/structure/` | — | — | — | Spire, vault, spawn vault, monolith (`beard_thin`): piece + 12 + 12 = **24 beyond the piece**, so the spire's 67-tall piece (`above/ruin/RuinBuilder.java:64, 240-241`) is fully covered (crown deck at plaza + 59; ward reaches 36 from the column). Sky platform and ocean lab (`none`): piece + 12; the sky platform's box is 49 x 26 x 49 at y 262-269 (`PrecursorRuinStructure.java:26, 58`), deck at box + 12 (`SkyRig.java:46`), so the ward reaches ±36 from centre, 12 over the top and **72 under the deck**. Hero crown: column of radius **9** (`HeroCrowns.java:60, 267-270`) from foot + 10 to crown + 6, while the Colossus dome is 24-30 in radius with limbs reaching 17-28 out (`terrain/hero/ElderColossus.java:27, 78`) | Spire/vault: big enough. Sky: big enough vertically, but the rig's rings are rest stops. **Hero crown: far too small** |
| **Ward: sealed walls** | `WardEvents.java:79-96`, `Wards.java:70-72`, tag `src/main/merge/wilds/data/awaken/tags/block/ward_sealed.json` | Breaking Precursor blocks and the deepslate/blackstone dressing inside an enforced ward (keeps the vault chamber from being dug round the rank door) | HOLD sparks + sound every time; action-bar line at most every 5 s | "The ward holds. These walls answer only to their door." | **A Hearth anywhere within 48+ blocks switches it off** (`Wards.java:65`); the plating is then minable with any pickaxe (strength 4, `above/AboveBlocks.java:37-38`, only `mineable/pickaxe`, no tier tag) | Good at vaults; **wrong text at spires and sky platforms, which have no door**; Hole via Hearth |
| **`awaken:arrived_by_movement` (best roll)** | `wilds/arrival/ArrivedByMovement.java:35-37`; `wilds/arrival/Arrival.java:143-192`; used by `src/main/resources/data/awaken/loot_table/chests/precursor_{sky,spire,vault,monolith,ocean}.json` | The prize pool: sky = 2 Power Core Shards + 1000-essence cell or Jet Boots; spire = 1 shard + cell or shard; vault/monolith/lab = 1 shard (SPEC §17.4) | **Nothing.** No message, no sound. The chest just has the ordinary roll | none | Relog wipes the record (`Arrival.java:75, 188`); a tower older than 10 min is not "built" (`PlacedBlocks.java:12`); elytra, riptide, wind charges, pearls all read as movement; a tower outside the radius-9 hero ward plus 20 s on the canopy | **Hole + invisible.** And the loss is **permanent and shared**: vanilla rolls a chest's table once, so a towered first open spends the prize for everyone, forever |
| **Spire crown** | `body/ascent/SpireCrownBlockEntity.java:100-122` | 10 Tether mastery (2 after), timed records, `awaken:ascent_spires_climbed`, quest `discover/spire` | Crown block light 14 + a breathing ring every 4 s (`:167-169`), visible from the ground | Refused: action bar "The crown stays dark. It answers only to a climb." (`src/main/merge/ascent/assets/awaken/lang/en_us.json:65`). Paid: toast "Tether Spire climbed · 23.4 s" + best/record line | Needs `ArrivalHooks.byMovement` **and** `Ascent.arrivedByMovement(p,20)` (a third of the last 20 s in an Ascent state, a wall-run or a ledge hang, no block placed; `Ascent.java:84-91`). Both reset on relog (`AscentMotion.java:3`); a Runner ledge hang (everyone has it, `body/Body.java:40-41`) fills the trail in ~7 s | Good text; Hole only with relog + hang |
| **Hero-tree crowns** (Elder Colossus, Lumen Spire) | `HeroCrowns.java:60, 187-192, 267-270`; same crown BE with `SITE_COLOSSUS/LUMEN` | First reach: 10 Tether mastery, **a fifth of a level** (`SpireCrownBlockEntity.java:139-143`), counter, quest; cache chest (`precursor_spire` table) | Glowing anchor rings up the trunk; the crown's light. **No Horizon line and no landmark** (SPEC §17.16 "Not yet") | Same refusal string; paid "The Elder Colossus's crown%s" / "The Lumen Spire's crown%s" | Build outside the radius-9 column into the dome (no crumble), walk the limbs 20 s, hang 7 s | **Weak ward, no tease** |
| **Sky platform core (Wings)** | `body/ascent/WingCoreBlockEntity.java:26-52` | The Wings tier | Breathing ring every 3 s, only visible on the deck | "The core stays dark. It answers only to those who came up the long way." — sent only when `gameTime % 1200 < 10` (`:39`), i.e. **once a minute on the minute**: a refused player can stand there up to 60 s with no reply | Same two checks as the crown: relog + ledge hang after a tower | **Weak**: delayed, vague ("the long way" never names the ring ladder) |
| **Sky platform on the Horizon** | `world/landmark/LandmarkHorizon.java:70-75` (`SKY_PLATFORM -> WINGS`); reach answers `body/ascent/AscentModule.java:55-57` (Wings, or Tether **and** Leaper), `powers/PowersModule.java:32` (Flight power), `above/AboveModule.java:30-32` (Jet Boots + essence) | Whether the platform is a target or a tease, and whether it goes on the Gate Map (`LandmarkHorizon.java:91`) | Tease 15 s in every 5 min (`core/api/HorizonHooks.java:27`); rumour "Something up there. Bring wings." (`world/landmark/LandmarkKind.java:23-25`) | "Sky Platform · needs Wings" (`signal/client/TrackerHud.java:190`) | — | **Misleading**: the rig (§17.9) is a rope-only ladder, and Wings are *given* by the platform's core. A Tether holder without the Leaper is told the platform needs the very thing it hands out |
| **Tether Spire on the Horizon** | `LandmarkHorizon.java:73`, `AscentModule.java:59`; "reached" in `world/landmark/LandmarkWatch.java:45, 87-88` | Tease / target | Tease; rumour "Platforms up its side, a light at the top. No stairs." (`LandmarkKind.java:28-30`) | "Tether Spire · needs Tether" | — | Good tease, but the spire counts as **reached** once you walk within 22 of its piece at ground level, and reached landmarks never return to the line (`LandmarkHorizon.java:61, 85`): earn the rope later and nothing points you back |
| **Tether Vow** (being fixed elsewhere) | `body/ascent/TetherVow.java:219-235` (working copy) | The rope | Forecourt "stir" (new) | "The vault stirs, and stills." / "Its line waits for a %s-rank hunter." / "The vault stirs. Its door will know you." (uncommitted lang) | Vow "kept" (`TetherVow.java:177-205`) has no movement check: a tower to the mast still pays 10 mastery + a fifth of a level | Out of scope except the kept check |
| **Wren's ledge** (quest step) | `src/main/resources/data/awaken/awaken/quest/wrens_rope.json` (`needs: tier:tether`); `living/quest/QuestEngine.java:164-169, 302-305`; Horizon `living/wayfarer/Scenes.java:475` | Getting Wren down | Horizon "Wren, stranded · needs Tether" (a raised site); toast | "Wren's ledge is a rope's reach. You'll need the Tether to bring her down." — only in a 1-s window per minute (`QuestEngine.java:303`) | None: the check is on the tier, not on how you got up | **The model lock** (gate the ability, not the route), but the toast can be 60 s late |
| **Leaper's Gauntlet** | `body/ascent/AscentTrials.java:100-107` (due: Tether + Tether mastery 2) | The Leaper tier (given at the chasm's edge) | A scripted gate opens in front of you | "A gate is beating like a running heart: the Leaper's Gauntlet." (`src/main/merge/ascent/.../en_us.json:56`) | Gates refuse blocks and buckets (`gates/GateEvents.java:213-225`) but not ender pearls; the leap is given at the edge anyway | Good; invisible until due |
| **Sky Leviathan's trial** | `AscentTrials.java:53-61` (A-rank, Wings, Wings mastery 3) | Skyborn | Auto-opened gate | "A gate opens onto open sky: the Sky Leviathan's trial." | — | Good; invisible until due |
| **Updraft vents** | `body/ascent/Updrafts.java`; only placed by `AscentSandbox.java:61` | Nothing in survival (no vents generate) | — | — | — | Not a lock today |
| **Windways** | `Windways.java`, `WindwaysServer.java` | Long glides between sky platforms (needs a glide: Wings) | Mint curls within 280 of the camera, faint by day | — | — | Fine (soft) |

### Rank, level and dimension locks

| Lock | Code | What it gates | How learned | Exact string | Cheese | Verdict |
|---|---|---|---|---|---|---|
| **Vault rank door** | `above/block/DoorCoreBlock.java:60-62, 108-121`; door rank by distance `above/ruin/RuinBuilder.java:933-938` (D under 1200 from origin, C under 2800, B under 5000, else A; spawn vault D, `PrecursorRuinStructure.java:69`); door blocks unbreakable `AboveBlocks.java:62-65` | The sealed chamber and its chest | Touch. The hum + END_ROD particles play **only for players who can open it** (`DoorCoreBlockEntity.java:22-28`); one model for every rank (`assets/awaken/blockstates/precursor_door_core.json`); Jade tooltip on the core block only, not the panels (`compat/client/jade/JadeClient.java:62, 248-255`); rumour "The door hums for some people. Not for me." | Refused: action bar "Sealed. It reads you and stays shut. Come back at C-rank." + SCAN_FAIL sound. Jade: "Opens for C-rank hunters" / "Your rank is too low". Open: toast "The door knows you." | **Hearth beside the vault → dig the plating** (see b4); a higher-rank friend folds the door away for good (`DoorCoreBlock.java:124-131`) | Good on touch; **invisible from afar** (no rank colour on the door) |
| **Gates (dungeon entry)** | `gates/run/Runs.java:95-115` | Nothing by rank: any rank walks in | Pillar coloured by rank, seen 1500 blocks off (SPEC §7); card within 24 | Card title "C-rank gate", rows Omen/Breaks in, footer "Walk in to enter · scout it from the Veil to learn more" (`gates/GateCard.java:74-128`) | — | **Soft.** The card never compares the gate to your rank, and below-rank gates pay ×0.3 silently (`progression/Curve.java:13`, `Runs.java:81`) |
| **Descent strata** | `descent/Strata.java:222-238`; `descent/DescentTravel.java:204-241` | Strata 2-9 by rank (Gardens D, Orchard C, Foundry B, Dreaming A...), 10 at S + level 135, the Endless after the Seed | Thrown back over the rim + sound + WARNING card | "The way down is sealed" / "Gardens is sealed. It opens at D-rank." / footer "Grow, and come back" | None (separate dimensions) | **Good: the best lock in the game** |
| **The Halo** | `above/machine/HomeMachines.java:39, 180-190`; `above/machine/LaunchRing.java:45-47, 61-66` | Seeing the Halo (B-rank), then launching (frames + known destination + essence) | Observatory panel | "Something glints · Past the sky, too far to make out. Your eyes aren't ready yet (B-rank)." / "Nowhere yet. Something has to show you where to aim. Try an Observatory." / "Needs a formed ring, a destination and N essence." | — | **Good** |
| **Veil possession** | `veil/Possession.java:71` | Wearing animals (C), mobs (A) | Toast on try | "The living won't let you in yet. (C-rank)" | — | Good |
| **Story seals** | `story/Ritual.java:214-217`, `story/StoryModule.java:116-123` | Scripted gates, the Seed stratum | Toast / overlay | "This bell isn't ringing for you." / "The Hollow Court still holds the Seed shut. 3 Sovereigns remain." | — | Good |
| **Region danger** | `living/region/RegionTracker.java:27, 58-72` | Nothing hard; regions 5+ levels over you | Ember caption + amber WARNING toast at most every 10 min | "Danger · <region>" / "Creatures here are level 20 to 26." | Walk in anyway | **Soft, good** |
| **Lair beasts** | `world/fauna/Lairs.java:157`; `world/fauna/FaunaRules.java:126` | A beast wakes only above its habitat's `minPlayerLevel` | **Nothing**: the lair is just empty | none | — | **Unreadable** (an empty lair reads as a bug or a dud) |
| **Landmark reason levels** | `LandmarkKind.java:11-30` (Well 10, Vault 4, Monolith 4, Ocean Lab 6) | When the Horizon names a site | The line simply appears | — | — | Fine (invisible by design) |
| **Quest/directive requirements** | `progression/Requirements.java:47`, `progression/Directives.java:49` | Offers by level/rank | They appear when met | — | — | Fine |
| **Hearth exemption** | `Wards.java:65`; `hearth/HearthService.java:162-188` (no ward or structure check); `hearth/HearthRecord.java:241-249` (radius 48 + 8/tier, y −32..+64) | Not a lock: **switches every ward off**, for everyone, inside any Hearth | Nothing tells you the ward is suspended | — | See b4 | **Hole** (tested as intended: `wilds/ward/WardTests.java:88-89`) |
| **City protection** (precedent) | `city/CityProtection.java:53-79` | Breaking/placing/buckets in civic Carillon | Cancelled + throttled action bar | "Carillon's stones belong to the city." | — | The "cancel and say why" pattern a fix can copy |

Companion mods: the pack has **no movement mods** (ParCool, Create, Waystones are on the "no" list,
`docs/plan-pack.html:223, 425`). Jade is present (tooltips only). The End is vanilla, so elytra exist late.

---

## (b) Cheese routes, ranked by how likely a normal player finds them

1. **Nerd-pole a Tether Spire, petal to petal.** *(Very likely: this is Euan's exact scenario.)*
   Route: stand on the plaza, pillar-jump 9 blocks next to the first petal; each block chimes and crumbles 3 s after
   it is placed (`Crumble.java:32`), but you always stand on one placed under a second ago, and the item comes back to
   you (`Crumble.java:101-112`), so 6 blocks in hand are enough. Step onto the petal (a sealed, safe rest). Repeat:
   10 up beside the next petal (a sixth of a turn round, about 5 blocks over), bridge 2-3 blocks, rest; five times;
   then 10 more onto the crown deck's trim rim. Ward: covers all of it (piece + 24). The first block ever shows the
   "Precursor ward" toast; nothing after that says anything.
   What pays: the cache's **ordinary roll** (essence, alloy, leads, string, pearls, wind charges, feathers), the
   terminal's logs (Lorekeeper counters), the broken machines' scans. What doesn't: the crown ("The crown stays dark.
   It answers only to a climb."), and the best roll, **which is silently and permanently spent** on that first open.
   Same route works on the vault roof/mast (only matters for the Vow's "kept", which has no movement check).

2. **Hero tree: tower beside the trunk, not under the crown.** *(Likely: the ward never even triggers.)*
   The ward is a column of radius 9 (`HeroCrowns.java:60`); the Colossus dome is 24-30 across with limbs 17-28 out.
   Route: build a normal tower 10-20 blocks from the trunk up into the dome (no crumble, nothing said), walk inward on
   limbs and leaf masses. If the walk to the column takes 20 s (`ArrivalLog.SIZE` 80 x 5 ticks), the visit opens "by
   movement" (`Arrival.java:147-149`): the cache's **best roll pays**. Add ~7 s hanging from a limb's edge (Runner
   ledge hang, everyone has it) and the crown pays too: 10 Tether mastery + **a fifth of a level** + quest step.
   No Horizon line or landmark exists for hero trees, so nothing ever told the player this was a rope site.

3. **Sky platform: tower to the ward floor, then nerd-pole ring to ring.** *(Likely for determined builders.)*
   The ward ends 72 under the deck (deck at y ~274-281, ward floor ~y 202-209). Below that a tower is permanent and
   unremarked. Above it, the rig's anchor rings are **full, unbreakable blocks with light 12** (`AscentContent.java:53-55`,
   `registerSimpleBlock`), every ≤22 blocks (SPEC §17.9): each is a free rest stop for a pillar-jumper. Then the perch
   (3x3 plating) and 7 more onto the pier. Alternative: a permanent tower 37+ blocks out (outside the ±36 ward) to deck
   height, then speed-bridge in (each bridge block lasts 3 s) or throw a pearl. Pays: the ordinary sky roll (which
   itself contains pearls and wind charges: the tools for the next cheese). Core: "dark", but see 5.

4. **Hearth Anchor beside a site.** *(Medium: accidental for anyone who builds their base by a ruin, deliberate for
   anyone who notices.)* The anchor can be placed anywhere in the overworld not already in a Hearth (`HearthService.java:162-188`);
   it needs a Lord Heart (`src/main/resources/data/awaken/recipe/hearth_anchor.json`), so it is early-game. Inside its
   radius (48+ blocks, y −32..+64) **every** ward is off (`Wards.java:65`), including other players' Hearths.
   - Vault: dig through the plating (any pickaxe) round the unbreakable door into the sealed chamber at **E-rank**;
     walk in, so the vault chest's best roll pays. The rank door is defeated outright.
   - Spire: an anchor on the plaza covers to +64, the crown is at +59: permanent stairs to the crown deck. Wait 10 min
     (or relog) and the stairs no longer read as "built".
   - Sky: an anchor on a tower at y ~215 covers to ~279: a permanent staircase through the ward.
   The spawn vault sits near spawn, exactly where new Hearths go.

5. **Relog after any built arrival.** *(Medium-low to find on purpose, but it happens by accident: quit on the deck,
   come back tomorrow.)* `Arrival` keeps nothing across a logout (`Arrival.java:75`) and treats "no record" as movement
   (`:188`); a fresh login inside a ward opens a new visit with an empty log, so by movement (`:147-149`). The Ascent
   trail is also unsaved (`AscentMotion.java:3`). So: tower up, relog, open the chest: **full best roll** (2 Power Core
   Shards + cell or Jet Boots on a sky platform). Add a 7-10 s ledge hang off the deck's edge and walk to the core:
   **Wings granted** to a player who never held the rope. Same for the spire crown.

6. **Ender pearls from natural high ground.** *(Medium: spires spawn in stony peaks, windswept hills, frost spires.)*
   Throw from a peak onto a petal or the crown deck. No block placed, so the chest's best roll pays; crown and core do
   not (no Ascent trail). From a tower instead of a peak: BUILT for 20 s, unless relog or a 10-min-old tower.

7. **Elytra + rockets, riptide in rain, wind charges.** *(Medium late-game; wind charges come from the spire and sky
   caches themselves.)* All read as AIRBORNE/SWIM (`Arrival.java:115-123`): the chest's best roll pays at spires and
   sky platforms; crown/core do not. (The Flight power and Jet Boots are intended reach answers.)

8. **Wait out the memory.** `PlacedBlocks` forgets after 10 minutes or 512 placements (`PlacedBlocks.java:11-12`): a
   tower built yesterday, or before 512 other placements, is ground, not a tower.

9. **Blocks the ward never sees.** *(Low.)* Buckets fire no place event (`/Volumes/MEDIA/Awaken/.ref/neo/net/neoforged/neoforge/common/CommonHooks.java:651`),
   so water stays and swimming is SWIM, not BUILT; lava + water makes permanent cobble; bonemealed twisting vines,
   frost walker, endermen, dispensers. Pistons placed just outside push into a ward (`WardEvents.java:103-105` only
   checks the piston's own position).

10. **Inside gates: pearls.** *(Low.)* Gates refuse blocks and buckets (`GateEvents.java:213-225`) but have no pearl
    rule: Gravity Wells' chasms ("without the wells it can't", SPEC §17.5) and the Leaper chasm can be pearled.

11. **Multiplayer.** One player's tower spends a cache's prize for everyone; a high-rank friend opens a rank door for
    good.

---

## (c) Unreadable locks

1. **The best roll is lost with no cue, permanently, for everyone** (`ArrivedByMovement.java:35-37`). The worst one:
   the player who did exactly what Euan describes never learns a prize existed.
2. **The ward is invisible until it eats a block**, and its one toast is once per lifetime and generic
   (`WardEvents.java:69-73`). It never says *what the site wants* ("a climb by rope"), so building reads as the
   intended solution that just happens to be "buggy".
3. **"These walls answer only to their door."** at a Tether Spire or sky platform, which have no door
   (`WardEvents.java:93`). Reads as a bug.
4. **The Wings core answers once a minute, on the minute** (`WingCoreBlockEntity.java:39`): reach it the wrong way and
   you can stand there 60 s in silence. "Those who came up the long way" never names the ring ladder. A player who flew
   up with the Flight power or Jet Boots was *told by the Horizon* the platform was reachable, then the core goes dark.
5. **Wren's need-text has the same once-a-minute window** (`QuestEngine.java:303`).
6. **"Sky Platform · needs Wings"** for a Tether holder (`LandmarkHorizon.java:72`, `AscentModule.java:55-57`): the
   platform's rig is a rope ladder and its core gives Wings; the tease is circular.
7. **Rank doors are invisible from afar**: one model for all ranks, the hum only for those who can open
   (`DoorCoreBlockEntity.java:25`), Jade only on the core block. You learn the rank by walking up and pressing it.
8. **Spires drop off the Horizon at ground level** (`LandmarkWatch.java:87-88`) and never come back after you earn the
   rope; there is no "you can climb that now" moment.
9. **Hero trees have no tease, no landmark** (SPEC §17.16 "Not yet"), yet they are rope sites with a ward.
10. **Empty lairs** under `minPlayerLevel` (`Lairs.java:157`).
11. **Gate cards don't compare ranks**: no "above your rank", and below-rank gates pay ×0.3 without saying so.
12. **The Hearth silently suspends wards** (no message that the ruin's walls became diggable).
13. Possible false negative to watch: the crown needs a third of the last 20 s in an Ascent state or a Runner move; a
    legit Colossus climber who walks the dome's limbs for 14+ s before the crown would be told "It answers only to a
    climb." (not verified in play; `ascent_hero_crowns` covers placement, not this).

---

## (d) Visible-before-usable affordances

**Exist (keep them):**
- Tether Spire: a lit pylon with seams, glowing anchor rings under each petal, the crown's light and breathing ring;
  Mira's rumour "No stairs."; Horizon "Tether Spire · needs Tether".
- Sky platform: glows from below at night; the Observatory lists it "above Y 260"; rumour "Bring wings."; the rig's ring
  ladder hangs to 26 over the ground, lit (a rope route you can see from the ground).
- Hero trees: rings up the trunk, the Lumen Spire's night spiral.
- Gate pillars rank-coloured at 1500 blocks.
- The Halo's "Your eyes aren't ready yet (B-rank)", the Descent's "It opens at D-rank", Possession "(C-rank)",
  Wren's "needs Tether" line and need-text, the vault rumour "The door hums for some people. Not for me.", the
  (in-progress) Vow stir with its rank line.

**Missing:**
- A ward you can see: nothing marks its edge or says "this is a climb" until you lose a block.
- The rank on a sealed door (colour or glyph by rank; Jade on the panels too).
- Any page that lists the Ascent ladder before you hold it: the Body section shows only moves you have
  ("moves still to come are never listed", `body/client/BodySection.java:26`). A "link to what the grapple is" has no
  page to land on today.
- A re-call when a tier is granted ("the spire you passed can be climbed now"). Tips teach keys, not places.
- A Horizon line / landmark for hero trees.
- The right need on sky platforms (Tether, with Wings/Flight/Jet Boots as alternatives).
- A cue that a cache keeps its best for a climb (an inner seal, a second lock, or a line when it stays shut).

---

## (e) Hooks a fix can use today

Ward and arrival:
- `core.api.WardHooks.at(ServerLevel level, BlockPos pos) -> Optional<WardHooks.Ward>` (`core/api/WardHooks.java:50`);
  `record Ward(String id, String kind, BoundingBox box)` (`:23`). `kind` is the structure's path
  (`precursor_tether_spire`, `precursor_sky_platform`, `precursor_vault`, `precursor_spawn_vault`, `precursor_monolith`,
  `precursor_ocean_lab`) or `hero_crown`; `id` is `ruin:x,z` / `site:x,z` / `hero/<pos>`. `register(Source)`,
  `interface Source { void wardsAt(ServerLevel, BlockPos, Consumer<Ward>); }`.
- `wilds.ward.Wards`: `enforced(ServerLevel, BlockPos) -> Optional<Ward>` (honours config + Hearth), `binds(Player)`,
  `sealed(ServerLevel, BlockPos, BlockState)`, `around(BoundingBox, boolean sky)`, `MARGIN`, `SKY_BELOW`.
- `wilds.ward.WardEvents.placed(ServerPlayer, ServerLevel, BlockPos, BlockState) -> boolean` (the placement hook, runs
  at `EventPriority.LOWEST`, skips cancelled events, `:45`); `refuses(ServerPlayer, ServerLevel, BlockPos, BlockState)`;
  flag key `NOTICED = "awaken:ward_noticed"` read with `Progression.profile(p).counter(key)` and set with
  `Progression.setCounter(p, key, 1)`; `HOLD_TELL_EVERY = 100`.
- `wilds.ward.WardFx.play(ServerLevel, WardFx.Kind, BlockPos, BlockState)` (package-private); `enum Kind { MARK, CRUMBLE,
  HOLD }` (ordinal on the wire); payload `awaken:ward_fx` = `record Event(int kind, BlockPos pos, int state)`; client
  `wilds/client/WardFxClient.play`. A new "refuse" look needs a new Kind on both sides (or reuse HOLD's sparks).
- `wilds.ward.Crumble.DELAY = 60`; `Crumble.queue(...)` (package).
- `core.api.ArrivalHooks.byMovement(ServerPlayer)`, `report(ServerPlayer, Way, int ticks)`, `history(ServerPlayer)`;
  `wilds.arrival.Arrival.describeVisit(ServerPlayer)`; `body.ascent.Ascent.arrivedByMovement(ServerPlayer, int seconds)`,
  `Ascent.has(Player, Tier)`, `Ascent.highest(Player)`, `Ascent.onGranted(BiConsumer<ServerPlayer, Tier>)` (the hook for
  a "you can climb that now" call-back).

Horizon and tips:
- `core.api.HorizonHooks` (`core/api/HorizonHooks.java`): `WINGS = "wings"`, `TETHER = "tether"`;
  `record Site(String id, String name, BlockPos where, String need, boolean raised)`;
  `record Line(String id, String name, BlockPos where, String need, boolean tease)`; `register(Source)` with
  `List<Site> sites(ServerPlayer)`; `addReach(Reach)` with `boolean can(ServerPlayer, String need)`;
  `can(ServerPlayer, String need)` (use it to word a ward message by what the player already has);
  `collect(ServerPlayer) -> Optional<Line>`; `pick(List<Site>, BlockPos, Predicate<String>, long, int)`;
  `teasing(long time, int stagger)`; `TEASE_EVERY = 6000`, `TEASE_FOR = 300`. A `raised` site is shown first, so a
  ward refusal could raise that site's tease for a few minutes. The HUD label is `TrackerHud.needLabel` + " · needs ".
- `world/landmark/LandmarkHorizon.need(LandmarkKind)` (package) is where the sky platform's need is chosen.
- `core.api.TipHooks.offer(ServerPlayer, Identifier)`, `done(ServerPlayer, Identifier)`; client
  `TipHooks.client().offer(Identifier)`, `.hold(Object, boolean)`, `.wants(Identifier)`. Tips are data in
  `src/main/resources/assets/awaken/tips/<group>/<name>.json` (`text`, `short`, `priority`, `show_for`,
  `retire_after_done`, `retire_after_shown`, `cooldown`, `awake`), with `{key.awaken.movement}` placeholders that print
  the player's own binding. Good for "you have the rope: hold <key> at a ring" once the tier is held.

Messages and the "link":
- `signal.Signal.toast(ServerPlayer, Component)`; `Signal.send(ServerPlayer, SignalWindow)`;
  `SignalWindow.builder(Kind, Component).body(..).row(label, value).footer(..).presentation(Presentation.TOAST)
  .ttl(ticks).priority(p).build()`; **`.choice(String actionId, String yesDoes, String noDoes)`** makes a Y/N card, routed
  to `Signal.onAction(String prefix, ActionHandler)` with `void handle(ServerPlayer, String action, boolean accepted)`
  (`signal/Signal.java:23-25, 54`). That is the in-game "hyperlink": Y = "Show me".
- Status screen (client): `new progression.client.StatusScreen(StatusSections.Tab.CODEX)` opens a tab;
  `StatusSections.add(Tab, Section)` adds a section (`progression/client/StatusSections.java:46`). Opening it from the
  server's Y answer needs one small payload.
- `core.Throttle.allow(ServerPlayer, String key, int ticks)` (`core/Throttle.java:20`) for "say it at most every N".
- Precedent to copy for refusing a placement: `city/CityProtection.java:74-79` (cancel + `tell` with Throttle, buckets
  via `PlayerInteractEvent.RightClickBlock`), `gates/GateEvents.java:213-225` (cancel blocks and buckets).
- Quest-style ability gate: a step's `needs: "tier:tether"` + `needs_text`, checked by `QuestEngine.blocked`
  (`living/quest/QuestEngine.java:164-169`).
- Rank wording already in use: `DoorCoreBlock.required(BlockState) -> Rank`, `Rank.label`, `Rank.colour`;
  `Strata.sealedMessage(Access, int)`.
- Atlas: `core.api.AtlasHooks` has no "needs" concept; `core.api.MapMarks.register(Source)` (client) could pin a
  locked site on the Gate Map.

---

## (f) Recommendation for Euan's question

**Name the key, say where it comes from in one line, and stop the build at the first block. Keep the rest of the
ladder as silhouettes.** Reasons:

1. *Hiding it would contradict what the game already says.* The Horizon prints "Tether Spire · needs Tether", Mira
   says "No stairs." and "Bring wings.", Wren's step says "You'll need the Tether". A ward that only says "you need to
   unlock something" would be the vaguest voice in the game, and vague refusals are what read as bugs.
2. *"A higher level" would be untrue.* The spire's lock is an ability; the ability's own lock is a rank (the Vow at
   C-rank, per the fix in progress). Saying "needs the Tether, a vault's line, C-rank" gives a concrete next goal the
   player already understands (ranks), without spoiling how the Vow plays.
3. *Good metroidvania locks are visible and named, the keys are not explained.* Show the lock and the key's name; let
   the Codex hold the rest as a silhouette until earned.

Concretely (all on existing hooks):
- **Refuse, don't crumble, at climb sites.** For wards whose kind is `precursor_tether_spire`,
  `precursor_sky_platform` or `hero_crown`, cancel survival placements (and bucket use) the way `CityProtection` does,
  instead of the 3-second crumble. The crumble's window *is* the nerd-pole; refusing it at block 1 also puts the
  message at the moment of the attempt. Keep the crumble at walk-in sites (vaults, monolith, lab), where building is
  not a shortcut.
- **Say what the site wants, per kind and per player**, using `HorizonHooks.can(p, need)`:
  - no rope: card "Tether Spire" · "Its platforms answer to the rope. Blocks won't hold here." · row "Needs: the
    Tether · a Precursor vault's line (C-rank)" · choice "Y: Show me the Ascent".
  - rope held: a tip, not a wall of text: "Climb it by rope: hold {key.awaken.movement} at a ring or a ledge above."
  - sky platform: "The rings climb to it by rope." (needs Tether; Wings, Flight or Jet Boots also reach it).
  - Show the card once per site visit (not once per lifetime), then a throttled action-bar line.
- **The "link"**: Y opens the Codex tab at a new "The Ascent" section: the tiers you hold in full, the next one named
  with where it comes from, the ones after as "???". That is Euan's hyperlink without a tutorial dump.
- **Close the holes that make the lock a lie:** wards beat Hearths at climb sites and for sealed walls (or refuse an
  anchor within a ward's reach); a login inside a ward opens the visit as "built" (or persist the visit verdict for a
  few minutes); never let a towered open spend the best roll silently (refuse to open with a line such as "The
  cache's inner seal holds. It opens to a climb.", or pay the best roll per player from the crown); widen the hero
  ward to the dome (the tree's blueprint bounds); fix the sky platform's need to the Tether; re-raise spires on the
  Horizon when the rope is granted (`Ascent.onGranted`); send the core's and Wren's refusals on the first refusal
  per visit instead of on the minute; fix the HOLD text at doorless sites.
