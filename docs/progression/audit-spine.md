# AWAKEN: the unlock spine and its pacing (audit, 2026-10-26)

Read-only audit of `/Volumes/MEDIA/Awaken/.claude/worktrees/progression-8ed284`. While I worked, another session
committed the Tether Vow C-rank fix (`5bbe162e`, then `945be930` for the wards); the worktree is now at `945be930`.
- **"Now"** in this report means `945be930`: the line answers C-rank and needs the vault's door open.
- **"Pre-fix"** means `84bea58c`, the code Euan played: any vault forecourt at any rank, `TetherVow.java:152, 194-197`
  at that commit.
- `docs/progression/` is untracked: the lead designer's plan in progress.

Path shorthands:
- `J/` = `src/main/java/dev/awaken/`
- `R/` = `src/main/resources/data/awaken/awaken/` (`R/level_road/*.json` is the per-level unlock table)
- `D/` = `src/main/resources/data/awaken/`

Times are medians for the **balanced** player from `tools/sim/pacing_sim.py` (300 runs, seed 42), unless marked
otherwise. Section (b) shows how I got them.

Three facts change most rows, so they come first:
1. **Level-road "flag" and "recipe" grants gate almost nothing.**
   - `RecipeUnlocks` puts every AWAKEN recipe in the book the moment the player holds any one of its ingredients
     (`J/progression/RecipeUnlocks.java:31-50`, no awakened check). Vanilla crafting never needs a known recipe anyway.
   - Only the flags descent, dreams, echoes, meditation, veil and truths are ever read (`J/progression/Requirements.java:42`,
     `Lessons.java:150`, `ProgressionModule.java:107`). The flags hearth, rank_trials, path, rank_b, deep, hollow_court
     and echo_roles have no reader.
   - So the road **announces**; the real gates are rank, items, Lord kills and blueprints.
2. **The tip system is dead.** `core.api.TipHooks.install` / `installClient` are never called anywhere (grep: no hits).
   Every `TipHooks.offer/done` is a no-op (`J/core/api/TipHooks.java:35-44`). That covers:
   - every Ascent tier tip (`J/body/ascent/AscentTips.java:31-36`)
   - the Runner kit's just-in-time tips (§16.0 decision 24)
   - the Living Wilds tips
3. **Nothing tells a player how to earn any Ascent tier before it happens.**
   - No Directive, drill, Now line or level-road step mentions a vault, the Tether, the Gauntlet or a sky core (grep of
     `R/directive`, `R/drill`, `R/level_road`; the Now sources listed in `J/**/…Now.java`).
   - The only signposts are the Horizon tease ("· needs the Tether", "· needs Wings": `J/signal/client/TrackerHud.java:190`)
     and, now, the vault's "stir" line ("Its line waits for a C-rank hunter.").

---------------------------------------------------------------------------------------------------------------------

## (a) The unlock table

Columns: unlock | condition in code | gate (rank / level / item / ability) | where | how the player learns it exists |
typical time (balanced median).

### Core spine: levels, points, ranks, Truths, Paths

| Unlock | Condition in code | Gate | Where | How learned | Time |
|---|---|---|---|---|---|
| Awakening, level 2 | Prologue Accept → `Progression.awaken` pays banked XP (`J/progression/Progression.java:565-580`); topped up to level 2 (`topUpToLevel`, :142-149; called from `J/story/Prologue.java:283-300`) | the story (Ritual or Carillon opening) | Bell Crypt ritual gate / Carillon square | scripted | ~6-8 min |
| Level-ups | `xpToNext(L) = 90·L^1.45 + 60` (`J/progression/Curve.java:16-18`); `addXp` loop (`Progression.java:151-163`) | XP | anywhere | level toast "+3 points to spend · K" (:237-240), or the Living Wilds level moment (:235) | see (b) |
| Stat points | +3 per level (`Curve.java:10`, `Progression.java:157`); +1 per Regimen (SPEC §3); spent in K (`spendPoint`, :258-267) | level | K screen | toast and the Now line "Spend N points · K" | every level |
| Level-road unlock per level (1-50, then 67 and 97) | `LevelRoad.catchUp` on every level and on awakening (`J/progression/LevelRoad.java:50-54, 100-125`) | level | — | toast per step (:127-132); some steps add a "goal" Now line (Lessons) | per level |
| Reassessment offered | at `level >= next.levelRequired`: REASSESSMENT window "Take the trial at the Spire, or at any Reassessment Monolith" (`Progression.java:246-252`); Now line "Reassessment ready" (`ProgressionNow.java:18-21`, no compass) | D L10, C L25, B L45, A L70, S L100 (`J/progression/Rank.java:11-17`) | — | window, Now line, K screen, Day's End | L10 3.9 h, L25 16.7 h, L45 46.7 h |
| Reassessment trial | a Monolith block starts it (`J/hearth/trial/Trials.java:186-221`). Kinds (:80-107, :349-380): D three waves; C two waves then the Second Awakening; B a Lord duel; A five rooms in 4 min; S the Echo of the Reflection. Failing locks the trial for the play-day (:209-213, :543) | `reassessmentReady` | a Reassessment Monolith. The "Spire" is a Monolith that Oren raises at your Hearth (`J/hearth/town/Town.java:467-494`). Without a Hearth you craft one (polished deepslate + amethyst + essence) | the window above; L10 road step "Build a Monolith" (`R/level_road/rank_trials.json`) | **D 5.1 h, C 17.9 h, B 47.5 h, A 69.3 h, S 84.6 h** (sim) |
| Rank-up effects | `passReassessment` (`Progression.java:583-599`): new gate tier, markNewThing("rank"), 3 Truths, RankHooks → townsfolk, strata, story beats | pass | — | REASSESSMENT window "X-rank pillars will appear" | as above |
| Truths | flag at L5 → 3 offered (`R/level_road/truths.json`; `offerTruths` `Progression.java:416-446`); 3 more per rank-up (:595) | L5, rank | K › Truths | toast "Truths: who are you becoming?" | L5 ~1.0 h |
| Titles, Latents | counter conditions (`Progression.java:356-397`) | counters | — | NOTICE "Title earned" / "A latent ability woke up" | emergent |
| Directives | each morning, or after a 20-min gap with no new thing (`J/progression/Directives.java:31-60`; `ProgressionModule.java:211-213`) | `requires` tokens | — | DIRECTIVE window, Y to accept | daily |
| Daily Regimen | three drills each morning (SPEC §4.9) | — | — | window | daily |
| Second Awakening: Path | the C trial calls `offerPaths`: the top 3 Paths scored from counters per hour, plus any hidden Path whose condition is met (`Progression.java:602-628`; `Trials.java:435-449`). Picking one grants its signature power, perks and the dream mentor (`Progression.java:641-655`) | C rank | Monolith; chosen in K | REVELATION window "Choose your Path · K" | **~17.9 h** |

### Powers

| Unlock | Condition | Gate | Where | How learned | Time |
|---|---|---|---|---|---|
| First power (1 of 3 cards) | after awakening, 140 ticks later (`J/powers/Starter.java:80`); in Carillon, at the Registry (:115). Pool: Haymaker, Shockwave, Charged Shot, Beam, Spirit Palm, Shadow Step, three families (:43-44, :126-140) | awakened | anywhere / the Registry | the cards; Now line "Choose your first power · K" (:86-88) | **minute 8** (sim) |
| Dash (movement slot, Left Alt) | `R/level_road/dash.json` → `RewardHooks.learnPower` | L3 | — | the power's own "New power" window | **0.5 h** |
| Power Cores from the level road | L4, L9, L20, L28, L39, L40, L46, L50 (×2) (`R/level_road/*.json` type `power_core`). Rolled at your rank, own-rank powers weighted ×3 (`J/powers/PowerRegistry.java:48-75`) | level | — | toast "Use it to learn a second power" | L4 0.8 h, L9 3.2 h, L20 11.9 h, L28 20.7 h … |
| Power Cores from gates | Lord hoard 50%, guaranteed after 3 bosses without one (`J/gates/run/Loot.java:89-97`; `Runs.java:472`). Gate elites 15%, ×1.5 after a 20-min gap (`Loot.java:82-83`; `Runs.java:666`) | gate runs | gates | "+ Power Core (in your Satchel)" toast | first Lord ~2 h (sim) |
| Power Cores from the overworld | overworld Lords 40%, guardians 50% (`J/mobs/MobLoot.java:110-120`); elites 6%, wardens 15%, ×1.5 after a gap (:160-176); named beasts 45% (`J/mobs/entity/NamedBeast.java:43`); 4 Core Shards fuse into one (`J/hearth/request/CoreShardItem.java:25`) | — | world | drops | ongoing |
| Grapple power (Tech, movement slot) | rollable at E (`J/powers/catalog/TechPowers.java:49-55`): about 1/17 of an E core, 1/62 of a D core | a core roll | — | only if it rolls | luck |
| Leap power (Kinetic, movement) | rollable at E (`J/powers/catalog/KineticPowers.java:50-51`) | a core roll | — | — | luck |
| Wall Stride, Water Run, Jet Burst | D-rank movement and passive powers (catalog `unlock(Rank.D)`) | a core at D | — | — | luck |
| Overload (the first Ultimate, R) | `R/level_road/ultimate.json` | L35 | — | its power window | **30.8 h** |
| Path signature power | `choosePath` → `learnPower` (`Progression.java:648`). Several are Ultimates (citadel, drone_swarm, legion, hundred_palms, void_maw, prism_lance; `R/path/*.json`) | C | — | REVELATION | ~18 h |
| Flight power | `J/powers/catalog/FlightPowers.java:54-55` | A (a core) | — | — | ≥69 h, luck |
| Power mastery levels | use-based (`J/powers/Mastery.java`). Sim: main power L2 at 4.4 h, L3 at 11.6 h, L4 at 27.7 h, L5 at 50.2 h | use | — | — | as stated |

### The Ascent (movement ladder; `J/body/ascent/Ascent.java:21-22`, grant at :53-66)

| Tier | Earn condition in code | Rank / level gate | Where | How learned | Time |
|---|---|---|---|---|---|
| **Runner** (wall-run, wall-jump, ledge hang, shimmy, ledge jump, slide, mantle, roll, tactical sprint) | from the first second when `runnersKit` is on (SPEC §17.2). Ranks lengthen moves: wall-run 35→40 ticks at D, +1 wall-jump link at C, shimmy and ledge reach at B (`J/body/RunnerRules.java:114-134`) | none | anywhere | **nothing**: the just-in-time tips never show (TipHooks not installed); the rank boosts are silent | 0 |
| **Tether** (rope: swing, zip, reel) | `TetherVow.tick`/`look`: in a vault's forecourt, the call (3 s), then `Ascent.grant(TETHER)` (`TetherVow.java:186-198, 213-217`). **Pre-fix (84bea58c):** any awakened player stepping into any vault forecourt, no rank check (`TetherVow.java:152, 194-197` at 84bea58c). **Now (5bbe162e):** rank ≥ max(C, the vault's door rank) **and** the sealed door already open (`TetherVow.java:39, 106-119, 220-222`). Otherwise the vault "stirs" (:229-239) | pre-fix none; now **C** | a Precursor vault (every ~640 blocks; spawn vault 240-360 from spawn). Door rank by distance from origin: D <1200, C <2800, B <5000, A beyond (`J/above/ruin/RuinBuilder.java:934-936`) | Horizon points to a "Sealed Vault" from L4 (`J/world/landmark/LandmarkKind.java:14`); Tether Spires teased "needs the Tether" from L0 (:28; `AscentModule.java:59`); now, the stir line. No step, Directive or Now line | pre-fix **~1 h** (Euan's case); now **~17.9 h** (C) |
| Vow kept (bonus) | reach the beacon mast by rope within 2 min: +10 Tether mastery, 0.2 of a level (`TetherVow.java:253-262`) | Tether | same vault | "Reach the beacon mast." | minutes after |
| **Leaper** (double jump, air dash, dive slam) | `AscentTrials.leaperDue`: has Tether, not Leaper, **Tether mastery ≥ 2** (100 points; level = ⌊√(points/25)⌋, `AscentMemory.java:43-46`), on the ground in the overworld, offered once (`AscentTrials.java:22, 100-107`). A scripted D-rank Foundry gate opens in front of you (:110-118). The leap is given at the chasm's edge (:121-127); the far landing adds 0.2 of a level and +10 mastery (:130-143). Mastery: 2 per new rope (`AscentServer.java:226`), 3 per launch (:263), 10 for the Vow | **no rank check**; follows the Tether by ~20 swings | a gate that opens in front of you | toast "A gate is beating like a running heart: the Leaper's Gauntlet." (lang) | Tether + ~10-40 min |
| **Wings** (glide) | `WingCoreBlockEntity.reached`: within 3 of a sky platform's core, having come up "by movement" (`ArrivalHooks.byMovement` **and** `Ascent.arrivedByMovement(p,20)`: a third of the last 20 s in an Ascent state or Runner move, no block placed) (`WingCoreBlockEntity.java:26-53`). The rings start 26 above the ground (SPEC §17.9), so in practice you need the Tether | **no rank check** | sky platforms (every ~44 chunks, ~700 blocks; deck about Y 275) | Horizon "Sky Platform · needs Wings" teased; once you hold Tether + Leaper it counts as reachable (`AscentModule.java:56-57`). Rumour "Bring wings." from L20 (`LandmarkKind.java:23-25`) | Tether + Leaper + a trip: ~1-3 h after the Tether |
| **Skyborn** (true flight on Aether) | `AscentTrials.skybornDue`: **rank ≥ A**, Wings, **Wings mastery ≥ 3** (225 points; deploy 2, boom 6: `AscentServer.java:266-269`), on the ground, once (`AscentTrials.java:25, 53-61`). The Sky Leviathan trial gate; Skyborn given at the arena edge (:75-81), the kill adds 0.2 of a level (:84-97) | **A** | a gate that opens in front of you | toast "A gate opens onto open sky…" | **~69 h** |
| **Sovereign** (god form, U key) | `Sovereign.tick`: once the ending is seen. `endingSeen` = gates calm (the Break ending) **or** colour restored (the Wake ending) (`Sovereign.java:55, 67-78`). **The "Become" ending never grants it** | story end | — | gift scene | **~102 h** |
| Old worlds | first load: rank B → Wings, rank A → Skyborn ("your body remembers") (`AscentServer.java:129-152`) | rank | — | scene | — |

### Arms (weapons; details from a sub-audit, spot-checked)

| Unlock | Condition | Gate | Where | How learned | Time |
|---|---|---|---|---|---|
| Arms verbs on vanilla swords, axes and the mace; awaken `*_blade` fights as a Longsword | `J/arms/Arms.java:39-49` | none | — | nothing | 0 |
| Hunter blade (Wildhide Fang, E) | shaped craft: 2 core (bone, flint or quartz) + essence shard + bind (string, feather or moss) (`D/recipe/wildhide_blade.json`) | materials only (the road's L3 recipe grant is redundant) | crafting table | L3 toast "Hunter gear · E" (`R/level_road/hunter_gear_e.json`); GearNow line (`J/gear/GearNow.java:45-54`) | first essence shard (most AWAKEN creatures drop them): ~0.5-1 h |
| 96 forged Arms weapons (16 styles × 6 tiers) | vanilla shaped crafts: the tier's Hunter blade(s) + core + bind (`D/recipe/wildhide_longsword.json` …) | none in code. Materials gate each tier: D needs beast trophies or E-Lord parts; C D-Lord relics; and so on | crafting table | **only the recipe book**. Holding a bone or string unlocks all 16 Wildhide weapons at once (`RecipeUnlocks.java:61-64`). No road step, Now line or markNewThing | E: minutes after the Fang |
| Lord-forged weapon (15, one per Gate Lord) | 35% per Gate Lord kill, certain on the 3rd in a row without one (`J/arms/LordForged.java:118-124, 126-140`) | needs Lord kills | gate boss rooms, the B trial duel | only on drop (pillar, toast "LORD-FORGED · name") | first Lord ~2 h (sim); expect one after ~2 Lords ≈ 3-6 h |
| Lord-forged Art | heavy at full charge with Resonance full (`LordForged.java:180-218`) | the weapon | — | overlay at release | after the weapon |
| Weaves (36) | an opening (deflect, perfect dodge, Break, finisher), then a power within 12 ticks (`J/arms/ArmsWeaves.java:42, 85-98`) | ≥1 power + a styled weapon | any fight | nothing until the first one fires (toast) | by accident |
| Weapon mastery chains | `masteryChain` data exists, but `ArmsRules.tap(..., 0)` hard-codes `masterySteps = 0` (`J/arms/ArmsServer.java:169`; client `ArmsPredict.java:153`) | **unreachable** | — | — | never |
| Hearth temper (+1 damage a level, max 6) | `J/hearth/Upgrades.java:39, 67-73` | Hearth + Forge tier | home | — | after the Hearth |
| Named weapons, legendaries, Marks, blueprints, attunement, Armoury page | not built (grep: no hits) | — | — | — | never |

### Living Wilds (details from a sub-audit, spot-checked)

| Unlock | Condition | Gate | Where | How learned | Time |
|---|---|---|---|---|---|
| Region levels | Commons 1-8 within 700 blocks of the city or spawn, other regions banded 1-8 … 80-150 (`J/living/region/RegionMap.java:19-33, 84-91, 146-166`) | none (they only scale creatures) | world | caption "Lv low-high" once per region per play-day; amber DANGER toast | 0 |
| Kill / quest / bounty XP | kills through a 0.10-level hunt budget per 10 min (`J/living/LivingModule.java:51`; `combat/KillXp.java:23-67`); quest steps 0.05-0.15, chain finish 0.4-0.6 (`quest/QuestEngine.java:180-207`); bounties 0.15-0.30 (`bounty/Bounties.java:158-162`) | — | — | — | ongoing |
| Happenings (9 kinds) | the first comes ~2 min outdoors after awakening (`J/living/pulse/Pulse.java:47, 171-185`) | Wild Hunt and Beast Duel need band ≥1 | within 256 blocks, outside Hearth and city | visual tell; rumours plus a map mark; "Witnessed" toast | minutes |
| Camp chains: Ten Sheep Short, Moth Lights | Wayfarer camp, no requirement (`J/living/wayfarer/Wayfarers.java:86-93`; `Scenes.java:354-358`) | none | 450 blocks north of the city (300 north of spawn in classic worlds) | **nothing points to the camp** | any time if found |
| Carillon chains: The Last Delivery, Oren's First Steel | `awaken:carillon_registered` (`J/opening/OpeningDirector.java:563,591`); Oren needs a temper at a Forge | registered | the city | NPC talk | early |
| Found scenes: Caravan Road, Hermit's Horn, Pell's Message, **Wren's Rope** | one every 25 min outdoors; the lowest band wins, ties broken alphabetically; one owned scene blocks all new ones (`J/living/wayfarer/Scenes.java:170-196`). All need L8. **Wren's first step needs `tier:tether`** (`R/quest/wrens_rope.json`; `QuestEngine.java:164-168`) and chains can't be dropped | L8; Wren needs the Tether | 64-160 blocks from you | horizon line "Wren, stranded"; a flare | L8 ≈ 2.7 h |
| Bounties (Hale, High Gate yard) | bands 0 (<L8) and 1 (L8-19) only; **no data for L20+** (`Bounties.java:64-66, 79, 109-110`) | level band | High Gate yard | Hale's idle line only | — |
| Camps and nests | seeded where place level ≥ 8 (`J/living/camp/Camps.java:153-160`) | level | wild | seen; raid warning toast | ~L8+ |
| Crafts (6) with perks at 5/10/20/30/40/50 | `J/living/craft/CraftEvents.java:120-247`. Budgets 0.012/0.007 of a level per 10 min (`LivingModule.java:55-59`) cap craft XP so hard that craft L10 takes 25-81 h of one activity (sub-audit's arithmetic from `Craft.java:72`) | — | anywhere | toasts; K › Crafts | perks mostly out of reach |
| Relic perks | Wren's Hook +6 rope reach (`AscentServer.java:243`); Courier's Knot. `shop_discount`, `mount_sprint` and `whistle_range` are read by nothing | quests | — | — | — |

### Gates

| Unlock | Condition | Gate | Where | How learned | Time |
|---|---|---|---|---|---|
| First open-world gate | Director upkeep every 1000 ticks per player; spawn chance 1.0 when you have none standing (`J/gates/Director.java:41-42, 198-215`) | awakened, overworld | 300-1500 blocks out, light pillar coloured by rank | the pillar; gate card; GatesNow | **~1 min after awakening** |
| Gate rank roll | 70% your rank, 20% one below, 10% one above (`Director.java:58-63`) | — | — | — | — |
| Lord's Heart | every Lord kill (`J/gates/run/Loot.java:91`; `J/mobs/MobLoot.java:128`) | a Lord kill | — | — | ~2 h (sim) |
| Twists | 1/8, pity on the 12th (`Director.java:117`) | — | — | L28 road step | — |
| Leaper's Gauntlet, Sky Leviathan trial | see the Ascent table | — | — | — | — |

### Hearth, Descent, Veil, Lucidium, Above, Story (details from a sub-audit, spot-checked)

| Unlock | Condition | Gate | Where | How learned | Time |
|---|---|---|---|---|---|
| Hearth Anchor | crafted from a **Lord's Heart** (`J/hearth/HearthService.java:~195-218`) | a Lord kill | anywhere | L6 road step (`R/level_road/hearth_anchor.json`); HearthNow when holding a Heart | L6 1.6 h, but really the first Lord ~2 h |
| Townsfolk | by household rank: Oren E, Mira D, Teodor C, Sun B, Vael A (`J/hearth/town/Town.java:83-149`) | rank | Hearth | arrival window | 0 / 5 / 18 / 47 / 69 h |
| Anchor tiers II-V | rank D/C/B/A (`J/hearth/Upgrades.java:52-55`) | rank + materials | Hearth | — | rank times |
| Echo binding | elites always; bosses at S or with Shadowbinder (`J/hearth/echo/BindMath.java:40-42`) | — | — | L7 road step | 2 h |
| The Well / stratum 1 | open to everyone (`J/descent/DescentModule.java:161-175`) | **none** | 400-700 blocks from spawn | proximity window; L20 road step | from minute 1; announced at **11.9 h** |
| Strata 2-3 / 4-5 / 6-7 / 8 / 9 / 10 | rank D/D/C/C/B/B/A/S/S (`J/descent/Strata.java:33-69`); the Seed also needs L135 and all 8 Sovereigns | rank | pits | rank-up toast | 5 / 18 / 47 / 69 / 85 / 102 h |
| Meditation, Veil, Stillpoint | usable from awakening (`J/veil/Projection.java:186-197`) | none | anywhere | L11 / L15 road steps and Lessons | taught at 4.8 h / 7.8 h |
| Veil possession | animals at C, any mob at A (`Projection.java:173-178`) | rank | Veil | HUD hint only | 18 h / 69 h |
| Dreams; lucid flight in dreams; dream shaping; mentor | the first night always dreams (`J/lucidium/…/Sleep.java:137-139`); lucidity thresholds (`Lucidity.java:14-17`); the mentor needs a Path | — | bed | L12 / L23 / L29 road steps | night 1 / 14.7 h / 22 h |
| Scanner | the spawn-vault antechamber chest (guaranteed) or L16 | none | spawn vault, open at E | L16 toast and goal | ~0.5-1 h if found, else 8.6 h |
| Blueprints → Fabricator → machines | scan a broken machine (`J/above/scan/Scanning.java:61-90`) | Scanner + a ruin | ruins | L17 / L18 goals | ~9-10 h |
| Vault doors | rank by distance (`RuinBuilder.java:934-936`; `DoorCoreBlock.java:68-70, 122-138`) | D/C/B/A | vaults | only the door's own line "Come back at X-rank" | 5 h+ |
| Frame / **Jet Boots** | Frame Forge blueprint (weight 8/109) + Reactor + alloy; Jet Boots module from loot or a craft | **no rank** | home | L37 road step | announced at 33.8 h; possible much earlier by luck |
| Deep Lift / Observatory / Launch Ring → Halo | blueprints (weights 8/6/4); the Observatory and Halo need B | blueprint luck + B | home | L34 / L47 / L49 | 29-50 h+ |
| Story | prologue (~10 min); revelations at rank D/C/B/A/S, the Halo, first Sovereign, the Seed (`J/story/Beats.java:24-45`); Vael scenes per rank (`J/story/Rival.java:34-42`); the Court's Sovereigns in a fixed order (`J/story/Court.java:95-97`) | **rank only** (no Act state in code) | — | Signal windows | rank times |
| Endgame | finishing at the Seed: S → Sovereign rank with no trial, the Endless opens (`Court.java:234-238`; `Descent.java:69`). Ending effects apply once per world: Ascension +1 cap 10, calm gates, colour (`Court.java:179-201`). Tiers 2-10 are unreachable in one world; there is no NG+ reset | L135 + 8 Sovereigns | stratum 10 | — | ~102 h |

---------------------------------------------------------------------------------------------------------------------

## (b) The time curve

**How I got it.**
- Copied `tools/sim/pacing_sim.py` and its input `gate_tuning.json` to
  `/private/tmp/claude-501/-Volumes-MEDIA-Awaken--claude-worktrees-progression-8ed284/31520be3-5998-4e98-8f06-b9e70a51fa57/scratchpad/sim/`
  and ran `python3 -I pacing_sim.py`. It matches the committed `tools/sim/pacing_results.json` exactly: D 5.14 h,
  C 17.85 h, B 47.52 h, A 69.29 h, S 84.58 h, Sovereign 102.84 h.
- Two wrappers over the copy:
  - `curve.py`: level and rank at chosen hours, all four player types.
  - `levels.py`: the balanced player's median hours to every level.
- A third copy, `pacing_sim2.py`, is instrumented with gate and Lord-kill timestamps (`gates.py`).
- `docs/living-wilds/living_wilds_sim.py` is stale: its "now" branch crashes on the missing `KILL_FRAC`, and its v3 branch
  double-applies the gate trim. pacing_sim is the live model (it already includes the Living Wilds XP: `WILDS_TRIM` 0.8,
  hunt/craft/quest/camp streams).

**Level and rank by hours played** (medians; p10-p90 in brackets for balanced):

| Hours | Balanced | Careful | Greedy | Builder | Share of balanced ≥ D / ≥ C |
|---|---|---|---|---|---|
| 0.5 | L3 (2-3) E | L3 E | L3 E | L3 E | 0 / 0 |
| **1** | **L5 (3-5) E** | L4 E | L5 E | L5 E | 0 / 0 |
| 2 | L6 (5-7) E | L6 | L6 | L6 | 0 / 0 |
| 3 | L8 (7-9) E | L8 | L8 | L8 | 0 / 0 |
| **3.5** | **L9 (8-10) E** | L9 E | L9 E | L9 E | 1% / 0 |
| **4 (an evening)** | **L10 (9-10) E** | L9 E | L9 E | L10 E | 7% / 0 |
| 5 | L11 (10-12) E | L11 E | L10 E | L11 E | 45% / 0 |
| 6 | L12 (11-13) **D** | L12 D | L11 D | L12 D | 78% / 0 |
| 8 | L15 D | L14 D | L14 D | L15 D | 99% / 0 |
| 10 | L17 D | L17 | L16 | L18 | 100% / 0 |
| 12 | L19 D | L19 | L18 | L20 | — / 0 |
| **15** | **L23 (21-24) D** | L22 D | L21 D | L23 D | — / 1% |
| **17** | **L25 (23-26) D** | L24 D | L23 D | L25 D | — / 28% |
| **20** | **L27 (26-28) C** | L27 C | L25 C | L28 C | — / 92% |
| 25 | L31 C | L30 | L28 | L31 | |
| 30 | L34 C | L34 | L31 | L35 | |
| 40 | L41 C | L40 | L36 | L42 | |
| 50 | L48 B (86% ≥ B) | L48 B | L42 C | L51 B | |
| 60 | L60 B | L60 | L49 B | L63 | |
| 70 | L71 A | L70 B | L61 B | L76 A | |
| 85 | L102 S | L96 A | L85 A | L103 S | |
| 100 | L131 S | L121 S | L120 S | L129 S | |

- **Rank arrival (balanced):** D 5.1 h, C 17.9 h, B 47.5 h, A 69.3 h, S 84.6 h, Sovereign 102.8 h.
  - Careful: D 5.3, C 18.2.
  - Greedy: D 5.2, C 19.6, B 56.7.
- **D comes ~1.2 h after L10 (3.9 h)** because the sim fails about 40% of first D trials: pass chance
  0.55 × level / content level 17 × skill + 0.25 ≈ 0.57, and a failure waits a play-day. The sim also takes the trial
  the moment it's due; a real player must find or build a Monolith first, so real D is later.
- **Hours to level (balanced):**
  - L3 0.5, L5 1.0, L8 2.7, L10 3.9, L12 5.7, L15 7.8, L20 11.9, L25 16.7, L30 23.4
  - L35 30.8, L40 38.4, L45 46.7, L50 51.2, L60 59.4, L70 68.1, L100 83.3, L135 101.7
  - The C band, L25→45, costs **~1.5 h a level** and is the slowest stretch of the game.
- **Gates and Lords (instrumented copy, balanced):**
  - 1 gate by 1 h (the Bell Crypt), 4 by 4 h, 9 by 10 h, 16 by 17 h
  - Lords killed: 2 by 4 h, 4 by 10 h, 10 by 17 h, 20 by 50 h
  - First Lord kill median 1.95 h (p10 1.7, p90 3.1)
- **First power:** minute 8 for every profile.
- **Caveat on Lords.** `docs/combat/sim-2026-10-26.md` finds that as built, a Dodger beats a home-rank Lord 5% of the time
  and a Parrier 6%. pacing_sim assumes about 75% of E gates end with the Lord dead. If the combat sim is right, everything
  behind a Lord kill slips, possibly by hours: the Lord's Heart, so the Hearth Anchor and the Spire; Lord-forged weapons;
  core pity; and D-tier gear materials.

---------------------------------------------------------------------------------------------------------------------

## (c) The unlocks on the curve

"Capability" means a new verb, power, movement, weapon or place type. "Filler" means shards, a Heart or a core-only step.
Times are balanced medians.

### Hour 1 (0-1 h): dense, as designed (`docs/modules/onboarding.md:62-64`)
- 0:06 awakening at L2. 0:08 **first power** (cards).
- Runner kit live from the first second, but **untaught** (dead tips).
- First open-world gate pillar within about a minute; first happening about 2 min outdoors; biome and landmark firsts.
- 0:30 L3: **Dash** (first movement upgrade). Same moment: Hunter gear E recipes. In practice the Fang and **all 16 E
  Arms weapons** are craftable once you hold a shard; the recipe book floods as soon as you hold a bone or string.
- 0:50 L4: **a Power Core** (second power).
- 1:00 L5: Truths.
- From L4 the Horizon points at a "Sealed Vault".
  - **Pre-fix:** walking into its forecourt gives the **Tether** right there (~1 h). The Leaper's Gauntlet follows after
    ~20 swings, and the Horizon then steers you to a sky platform for **Wings**. Three Ascent tiers can land in the
    first 2-4 hours.
  - **Now:** the vault only stirs, "Its line waits for a C-rank hunter."

### Evening 1 (1-5 h): still good for systems; movement and weapons go quiet
- 1:36 L6: Hearth Anchor recipe. It needs a Lord's Heart; first Lord ~2 h.
- 2:00 L7: Echo binding (B key).
- ~2-6 h: first **Lord-forged weapon** (35% per Lord, certain by the 3rd).
- 2:40 L8: found quest scenes start; camps appear.
- 3:12 L9: Power Core.
- 3:54 L10: Reassessment offered (Monolith recipe).
- 4:48 L11: Meditation, wild mounts (bridle).
- ~5:06: **D-rank** (median). Brings:
  - strata 2-3, Mira, Anchor II, D gates
  - the Runner's wall-run +5 ticks (silent)
  - D-rank vault doors open (the spawn vault)

### Week 1 (5-20 h): many systems, **no movement and no weapon verbs**
- 5:42 L12 Lucid dreams · 6:24 L13 whistle, Beastbound D blades · 7:06 L14 Training Grounds · 7:48 L15 The Veil
- 8:36 L16 Scanner · 9:24 L17 Fabricator · 10:12 L18 machines · 11:00 L19 Veil scouting
- 11:54 L20 The Well (open since minute 1) + a core · 12:48 L21 Dream Bell · 13:48 L22 Gatewrought C blades
- 14:42 L23 dream shaping · 15:42 L24 Hanging Gardens (shards) · 16:42 L25 Second Awakening flag
- Living Wilds: bounties stop at L20 and quest chains top out at band 1, so the open world's authored content runs out
  around L20 (≈12 h).
- **~17:54: C-rank, the flood.** Path choice plus signature power (often an Ultimate), 3 Truths, Teodor, strata 4-5,
  Anchor III, animal possession, C gates. Now, on top of that: the **Tether**, then the **Leaper**
  ~10-40 min later, then **Wings** as soon as a sky platform is reached. That is 8-10 capability unlocks inside ~2-3 h.

### Beyond (20-102 h)
- **20-47 h (the C band):** the road is mostly filler.
  - L26 Spire Board · L27 nightmare · L28 core · L29 mentor · L30 Echo roles · L31 stratum 3 goal · L32 named beasts
  - L33-34 strata and Deep Lift goals
  - **L35 Overload, 30.8 h: the first Ultimate.** The R key sits empty for 30 h unless the Path gave one.
  - **L37 Frames and Jet Boots, 33.8 h** · L38-44 shards, cores and goals
- **47-51 h (the B flood):** B trial and B rank (strata 6-7, Sun, Anchor IV, Forge III), plus L46 Foundry core,
  L47 Observatory, L48 stratum 7, L49 Launch Ring and Halo, L50 Hollow Court (2 cores, but it points at S content
  30 h away).
- **51-102 h:** the road has two entries in 85 levels: L67 Lordscale A gear (65 h) and L97 Sovereign Weave (82 h).
  - A at 69 h: **Skyborn** trial (needs Wings mastery 3), Vael, stratum 8, any-mob possession.
  - S at 85 h: Court strata, boss Echoes.
  - The Seed and Sovereign rank at ~102 h; the **Sovereign form** only on the Break or Wake ending.

---------------------------------------------------------------------------------------------------------------------

## (d) Stalls and floods

### The reward rhythm, by capability (balanced; Tether at C, as now)

```
hours      0    1    2    4    6    8   10   12   15   18   20   25   30   35   40   47   51   60   70   85   102
powers     ██   █         █                   █         ██        █    █(Ult)    ·    █    ██        ·    ·    ·
movement   ██   ·    ·    ·    ·    ·    ·    ·    ·    ███  ·    ·    ·    █(JB) ·    ·    ·    ·    █(Sky) ·  █(Sov)
weapons    ██   ·    █(LF)·    ·    ·    ·    ·    ·    ·    ·    ·    ·    ·    ·    ·    ·    ·    ·    ·    ·
places/sys ██   █    █    ██   ██   ██   ██   █    █    ███  █    █    ·    █    ·    ███  █    ·    █    █    █
                                                       ^ C flood                     ^ B flood
```
Legend: █ = a capability unlock, · = none. LF = Lord-forged weapon; JB = Jet Boots (announced, blueprint luck);
Ult = Overload; Sky = Skyborn; Sov = Sovereign form.

**Stalls (long stretches with nothing new of that kind):**
1. **Movement, ~0.5 h → ~18 h.** After Dash (L3) the only guaranteed movement before C is the wild mount (L11, ~5 h,
   a different thing). Grapple, Leap and Wall Stride come only from core rolls, about 1.6-6% per core. The Runner's rank
   boosts at D are silent. Pre-fix, the stall instead sits at **~3 h → 69 h** (all three early tiers taken in evening 1).
2. **Weapons: no progression after ~6 h.** After the first Arms craft and the first Lord-forged drop:
   - The only weapon change is a material tier swap. Blades are announced at L13/22/42/67/97; the Arms weapons
     themselves never are.
   - Mastery is unreachable (`masterySteps = 0`), and named or legendary weapons were never built.
   - Nothing marks a weapon as a "new thing".
3. **The C band, 18 → 47 h (L25-45, ~1.5 h a level).** About 20 road steps, roughly 12 of them shards, Hearts, cores or
   goals. The only new verbs are Overload at 31 h and Frames at 34 h. The Living Wilds has no content past L20.
4. **The level road after 51 h.** Two entries in 85 levels; ranks A and S and the story carry ~50 h alone. Nothing
   announces the A or S trials, stratum 8, the Court or the Seed's L135 requirement.
5. **Ultimate slot empty until 31 h** (L35 Overload), unless the C Path gives an Ultimate.

**Floods:**
1. **Hour 1** is dense by design: power, Dash, a core, Truths, a gate, happenings, the Atlas. It works, but two things
   add noise:
   - RecipeUnlocks fires a recipe toast per ingredient, so a bone or string lights all 16 Wildhide weapons.
   - **Pre-fix**, the Tether, Leaper and possibly Wings also land inside the first 2-4 h. That is the whole ladder below
     A, burned in one evening.
2. **C-rank (~18 h)** as now: Path + signature power + Truths + Teodor + strata 4-5 + Anchor III +
   possession + **Tether + Leaper + Wings**. That's 8-10 unlocks in ~2-3 h, after a 13-hour movement desert. The worst
   shape on the curve.
3. **B-rank (47-51 h):** rank effects plus road L45-50 (Foundry, Observatory, stratum 7, Launch Ring, Hollow Court,
   3 cores) in ~4 h, then nothing on the road for 14 h.

### The Director's "new thing" rhythm (SPEC §2, second reward rhythm)

**Implemented.** `Progression.markNewThing` stamps `lastNewThing` (`J/progression/Progression.java:545-556`). Four
consumers read `minutesSinceNewThing > 20`:
- overworld elite and warden core chance ×1.5 (`J/mobs/MobLoot.java:170`)
- gate elite loot core bias (`J/gates/run/Runs.java:666`; `Loot.java:82`)
- a Directive offer, 1 in 4 each minute (`J/progression/ProgressionModule.java:211-213`)
- prefer a quick Directive (`Directives.java:39`)

The Power Core pity is in: gate Lords at 3 bosses (`Runs.java:472`) and overworld bosses (`MobLoot.java:130`).

**What counts (~40 call sites).** Rank, Path, power learned, title, Truth, latent, Hearth, townsfolk, Echo, trophy, Hearth
tier, request, training record, machine blueprint and build, ruin found, vault door opened, Halo, depth record, guardian,
lucid dream, vision, Veil gate, spirit story, Sovereign, ending, core drop. Also the cheap ones:
- first visit to each biome (`J/wilds/BiomeDiscovery.java:60`)
- **every craft level** (`J/living/craft/Crafts.java:71`)
- every happening kind (`Pulse.java:163`)
- each landmark kind and gate theme (`LandmarkWatch`; `Runs.java`)
- each fauna kill (`J/world/fauna/FaunaModule.java`)
- each anomaly kind

**What does NOT count:**
- **any Ascent tier.** The only `Ascent.onGranted` listener is the dead tip system (`AscentTips.java:32`); TetherVow,
  AscentTrials, WingCore and Sovereign never call markNewThing.
- **anything in Arms**: Lord-forged, first Weave, first Arms craft (grep: none in `J/arms/`)
- mounts tamed
- Living quests, bounties and relics
- level-road steps that aren't powers

**Verdict.** The clock measures sightseeing, not capability. In the first ~15 h, biomes, crafts, fauna and happenings
reset it constantly, so the bias rarely fires exactly when the player is starved of new abilities. And when it does fire,
it can only nudge core odds or a Directive; it can't surface the next movement tier or weapon.

---------------------------------------------------------------------------------------------------------------------

## (e) The Tether at C, and where the Tether should sit

**What the code does:**
- **Pre-fix (84bea58c):** Tether at any vault forecourt, any rank, ~1 h (Euan's case). Leaper follows after 100 rope-mastery points
  (~20 swings). Wings follows at the first sky platform you rope up to; there is no rank check on the core.
- **Now (5bbe162e):** Tether needs rank ≥ max(C, the door's rank) **and** the door open, so **~17.9 h** (18.2 careful,
  19.6 greedy). Leaper and Wings have no rank checks of their own, so they follow within ~1-3 h. **The whole Runner →
  Wings stretch collapses onto C.**

**The ladder with the Tether at C:**

| Tier | Now | Approved plan (`docs/ascent/BRIEF.md:61`) |
|---|---|---|
| Runner | 0 h | 0 |
| Tether | ~18 h (C) | E-D |
| Leaper | ~18.5 h | D-C |
| Wings | ~19-21 h | C-B |
| Skyborn | ~69 h (A, needs Wings mastery 3) | A |
| Sovereign | ~102 h (Break or Wake only) | story end |

**Is there a movement gap between Runner and Tether? Yes: ~17 h,** filled only by:
- Dash at 0.5 h
- an untaught Runner kit
- silent D-rank Runner boosts
- the mount at ~5 h
- core-roll luck (Grapple, Leap, Wall Stride, Jet Burst)

Meanwhile the world teases what's locked:
- Tether Spires, about every 384 blocks, teased "needs the Tether" from minute 1 (`LandmarkKind.java:28`;
  `D/worldgen/structure_set/precursor_tether_spires.json`, spacing 24)
- the vault on the Horizon from L4
- hero-tree crowns
- sky platforms, "Bring wings." from L20

**Two knock-on breaks:**
- **Wren's Rope** (L8, ~2.7 h) needs the Tether at its first step, and chains can't be dropped. A player who takes it
  at L8-12 has every later found scene blocked until C (`Scenes.java:178`; `QuestEngine.java:164-168`).
- With the pre-fix rule gone, a vault the player **can** open at D (the spawn vault's door is D) gives nothing at D: the line
  asks for C (`TetherVow.java:106-108`).

**Recommendation: the Tether at D, earned at a vault whose door you can open. The line answers the door's own rank.**
- In code: `needs()` = the door rank, with D as the floor. Keep the current "door open" rule and the stir.
- **When:** D arrives at ~5.1 h median (45% of players by 5 h, 78% by 6 h, 99% by 8 h). The real time is a bit later,
  since the player must find or build a Monolith. That makes the rope the reward of the **first rank-up**, at the end
  of the first long session or the start of the second.

**Why D:**
1. **It fixes what Euan actually hit, and matches what he described.** He got the rope at E walking in under-ranked. He
   said "you need the C rank to get into the vault", but the vaults near home have **D** doors (under 1,200 blocks,
   `RuinBuilder.java:934`; the spawn vault is one). "Open the door, and the vault gives you its line" is exactly his
   rule. A C number only holds for vaults 1,200-2,800 blocks out. **Euan should confirm D over C; it is his call.**
2. **It matches the approved ladder** (Tether E-D, BRIEF.md:61) and the Wilds' purpose. Spires, crowns and Wren's Rope
   become D-band content instead of 17 h of teasing.
3. **It splits the C flood and fills the E→C movement desert:** one tier at ~5 h, not three at ~18 h.
4. **It keeps a rank gate on it** (no more E walk-ins), and "earned at your first rank-up" is easy to tell the player.

**Spread the rest so they don't trail the Tether by minutes.** This needs small rank checks that don't exist today:
- **Leaper:** keep the Gauntlet, but raise the bar from Tether mastery 2 to **mastery 4 (400 points ≈ 80 swings)**, at
  rank ≥ D (`AscentTrials.LEAPER_FROM` and `leaperDue`). It then lands mid-D, ~8-12 h. That estimate needs a tour or
  telemetry. Alternatively, gate it at **C** so the Path and the Leaper share the C moment, which is 2 things, not 8.
- **Wings:** add **rank ≥ C** to `WingCoreBlockEntity.reached`, ideally with Leaper mastery 3, so Wings lands ~20-25 h,
  inside the long C band, which is the biggest content hole. The plan said C-B.
- **Skyborn:** A as approved (~69 h). Moving it to **B (~47 h)** would put a rung in the 50-hour C→A hole, but it's
  off-plan: Euan's call.
- **Sovereign:** at the story end. **Also grant it on the Become ending** (`Sovereign.endingSeen`); today only Break
  and Wake give it.

| Tier | Recommended rank | Time (balanced) |
|---|---|---|
| Runner | from the start, **taught** | 0 |
| Tether | D, at an openable vault | ~5-6 h |
| Leaper | D + rope mastery 4 (or C) | ~8-12 h (or ~18 h) |
| Wings | C (+ Leaper mastery) | ~20-25 h |
| Skyborn | A (or B) | ~69 h (or ~47 h) |
| Sovereign | any ending | ~102 h |

**If Euan keeps C:**
- at minimum gate Leaper and Wings so they don't stack onto C (Wings at B ~47 h; Leaper at C + mastery)
- make Wren's Rope `requires` the Tether at chain level, not step level
- add a guaranteed movement taste in the E-D band: make the L9 core a Grapple or Leap pick, or put Jet Boots on the road
  with a guaranteed blueprint
- either way, **rename the Grapple power**. Euan calls the Tether "the grapple", and an E-rank power literally named
  Grapple (`TechPowers.java:49`) will confuse "how do I get the grapple".

---------------------------------------------------------------------------------------------------------------------

## (f) The 10 biggest problems, ranked, with fix ideas

1. **The movement ladder is mis-placed either way.**
   - Pre-fix: three tiers in evening 1, then nothing until A at 69 h.
   - Now: an 18 h movement desert, then three tiers stacked on C (~18 h).
   - Fix: section (e). Tether at D via the door rank; rank checks for Leaper (D + mastery 4, or C) and Wings (C); Skyborn
     A or B; Sovereign on any ending.
2. **Nothing teaches how to earn anything in the Ascent, or even the Runner kit.**
   - TipHooks is never installed, so every tip is a no-op.
   - No road step, Directive, drill or Now line names the vault, the Gauntlet or the sky core.
   - The Horizon's "needs the Tether" never says where the Tether comes from.
   - Fix:
     - Install the tip system, or route the tips through Lessons.
     - Add road steps with goal Now lines and a compass: "Open a vault door (D)", "Rope to a Tether Spire's crown",
       "Reach a sky platform's core".
     - Teach the next rung in the Horizon tease ("needs the Tether · a vault's line").
     - Give each tier grant `markNewThing` plus a DIRECTIVE pointing at the next rung.
3. **Weapons have no progression or discovery.**
   - Mastery chains are dead (`ArmsServer.java:169` passes 0).
   - Named, legendary, Marks and the Armoury were never built.
   - The 96 Arms weapons are found only through a flooding recipe book; the road never mentions them.
   - Lord-forged comes only from the 15 Gate Lords (no guardians, Sovereigns or broken-gate Lords).
   - GearNow stops proposing blades once you craft an Arms weapon.
   - Fix:
     - Wire `masterySteps` to a per-style kill or Weave counter, with a toast at each step.
     - Add road steps "Your first Arms weapon" (L2-3) and per-tier "new style" moments.
     - `markNewThing` on the first Arms craft, Lord-forged and the first Weave.
     - Extend Lord-forged to guardians and Sovereigns.
     - Gate RecipeUnlocks on awakening and show one summary toast.
4. **The C band (18→47 h, L25-45) is the longest, slowest and emptiest stretch.**
   - ~1.5 h a level; ~12 of ~20 road steps are shards, Hearts, cores or goals.
   - Living Wilds content ends at L20 (no band ≥2 quests, no bounties for L20+).
   - The first Ultimate comes at 31 h.
   - Fix: put Wings here (C + mastery). Add band-2 chains and L20+ bounties. Move Overload to ~L20-25. Turn the C-band
     road steps into real verbs: possession at C, Jet Boots with a guaranteed blueprint, dream flight.
5. **The level road ends at 50 (51 h).** Two steps in L51-135; ~50 h with only rank-ups and story; nothing announces the
   A or S trials, stratum 8, the Court or the Seed's L135 + 8 Sovereigns.
   - Fix: extend the road to 135 at least every 3-5 levels: Skyborn readiness, Halo, strata 8-10, each Sovereign hunt,
     Sovereign rank, Ascension.
6. **The road's announcements don't match the real gates.**
   - The Well is open from minute 1 but announced at L20.
   - The Veil and meditation work at L2 but are taught at L11/L15; dreams happen night 1 but are taught at L12.
   - L40 says strata 4-5 "are open", though they open at C (~18 h) and L33/L36 goals already sent you there.
   - L50 Hollow Court points at S content ~30 h ahead.
   - 7 flags are read by nothing; recipe grants duplicate RecipeUnlocks.
   - Fix: re-time each step to its real gate (Well ~L5-8, Veil ~L6), move the rank-gated steps onto rank-up hooks, and
     delete or wire the dead flags.
7. **The "new thing" rhythm counts sights, not capabilities.** Biomes, every craft level, fauna and happenings reset it.
   Ascent tiers, Arms, mounts and quests don't count. The bias can only nudge cores and Directives.
   - Fix: keep two clocks. "Novelty" stays as is. A new "capability" clock is reset only by power, tier, weapon, Path,
     rank, new place type. When the capability clock passes ~60-90 min, surface the nearest available capability
     (Now line plus compass) instead of only raising odds.
8. **The Reassessment is hard to find and to start.**
   - "The Spire" exists only with a Hearth, and the Hearth needs a Lord's Heart.
   - The text collides with the Precursor "Monolith" ruin (`Scanning.java:110`) and the "Tether Spire".
   - The Now line has no compass; a failure locks the trial for a play-day.
   - At L135 the K screen says "take the Sovereign-rank trial at the Spire" but the Monolith refuses (`Trials.java:195-199`).
   - Fix:
     - Give the Now line a target: the nearest Monolith or Spire, or "craft one: recipe".
     - Rename the trial block ("Reassessment Stone").
     - Hand out a Monolith with the L10 step.
     - Fix the Sovereign-rank text.
9. **Much of the spine hangs on Gate Lord kills that the combat sim says players lose.** The Hearth Anchor (so the Spire,
   townsfolk and tempering), Lord-forged weapons, core pity and D-tier materials all need them. Combat sim as built:
   Dodger 5% and Parrier 6% wins against home-rank Lords. pacing_sim assumes about 75% of E gates end with the Lord dead.
   - Fix: land the combat-sim fixes (bolt classes, a level-scaled Lord damage cut) before tuning this spine. Add a
     non-Lord Heart path, e.g. an E-rank story gift or a guardian.
10. **Living Wilds quest pipeline traps and dead rewards.**
    - Wren's Rope needs the Tether at step 1, chains can't be dropped (`QuestEngine.drop/track` have no callers), and
      one owned scene blocks all new ones.
    - The alphabetical band tie-break can retry an unplaceable chain forever.
    - Bounties can be farmed (claim id includes the done count).
    - Three relic perks are read by nothing.
    - Craft perks are budget-capped out of reach.
    - Fix: move `tier:tether` to the chain's `requires`; add drop; rotate past an unplaceable chain; key bounty claims by
      id per day; wire or replace the dead perks; raise the craft budgets ~5×.

Smaller items:
- The "Become" ending grants no Sovereign form.
- Ascension tiers 2-10 are unreachable in one world, and SPEC §10 still promises NG+.
- `living_wilds_sim.py` crashes.
- SPEC §25 still describes "six forged Longswords".
