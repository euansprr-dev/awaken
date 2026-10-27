# The shape of danger: where levels sit and what a level changes (plan for Euan's approval, 2026-10-27)

Plan only; nothing is built. Written from a code audit of the build Euan plays (`build/awaken-v1` 0257c666), his world
`New World (3)` (read only, never written) and its log, six open-world references, and a prototype of the proposed rules
that I ran against the real `RegionMap` over hundreds of random worlds (`docs/regions/proto/`, outputs in
`docs/regions/results/`). Approval page: https://claude.ai/artifact/MnswCydLWBrdpXzL3f84YE (source `docs/regions/page.html`).
Status: **waiting for Euan's approval**; nothing is built.
Builds on SPEC §21.2 (regions), §27 (The Way Forward: the world pulls first, no hand-holding) and §28.9 (roads and towns).

---

## 0. What Euan said, and the one-line answer

> "I literally just got out the city 10 minutes ago, walked a bit, and I'm already in an area I would instantly die in
> (and I have died actually lol, I found an ELITE skeleton after 5 minutes outside the city with only a stone sword). I
> don't think we should make difficulty level of areas PURELY based on distance, but we should still figure this out.
> Another thing is what changes between each difficulty level? I just killed a pig here as easily as in a level 1 area."

**The answer.** Distance should set how bad a place *can* get. The land should decide how bad it *is*, and say so
before a creature does. Near the city the ground rises smoothly (a safe start, like any starter zone); further out the
patchwork Euan asked for on 25 October takes over, inside a ceiling that grows with distance. A level should mean a
kind of land with its own look, its own creatures' odds and its own finds, not a bigger number on a pig.

---

## 1. What actually happened (his world, his log; corrections to the brief marked ◆)

World seed 3500122753516418813, Carillon's centre (-1238, -736). He plays level 1 (133 XP), a stone sword, no armour.

| Time | What | Where | Level there (old rules) |
|---|---|---|---|
| 08:48 | world made; the first hamlet's spot found "868 blocks up the road" (it is Wickton, 16 people, `village:first`) | (-1234, -1604) | **72** (inside Black Glades) ◆ |
| 08:54 | the camp's fire lit | (-1231, -1183), 447 north | 4 |
| 09:10:52 | Wickton's people start (he is in range of it, walking the camp road) | | |
| 09:15:02 | "Dev was shot by Skeleton" (`LastDeathLocation`) | (-1287, 69, -1444), 708 north | **8, the Commons' north edge** ◆ |
| 09:20:59 | "Danger · Black Glades" toast offered; shown 09:21:18 (19 s later); 6 s long | at the wall, heading in | 72-78 |
| 09:21, 09:25 | a Stampede and a Caravan happening start for him | (-1239, -1639), (-1172, -1789) | 72 |
| later | the Atlas screenshot: "Black Glades · here · Lv 72-78" | about (-1269, -1650) | 72 |

◆ **Corrections.**
- **He died in the Commons, not in Black Glades.** The death point is the Commons' north edge: level 8 land. A hunter there
  is level 9 and an *elite* level 10, which is health ×4.4 (88 HP, about 18 stone-sword hits) and damage ×1.4. At level 1 that
  is unwinnable, and nothing told him: the Commons never raises a danger warning (a rule added on 26 October so a
  level-1 player in the city didn't read "Danger · The Commons"). The ELITE plate did say what the skeleton was; nothing said
  what the *place* was. The log has no danger toast before 09:20:59.
- **The level-72 land begins about 20 blocks past that spot.** Along the camp road (x = -1238) the level reads 8 at 780 blocks out
  and 72 at 800. The two stories are one bug seen from both sides.
- **The first hamlet itself stands in the level-72 land.** The first hamlet is "placed on purpose 700-1,100 up the camp road" (§28.0,
  `FirstHamlet`) with no look at the level. In his world that is Wickton, in Black Glades, and the camp road (the road the first
  thread sends every player up) leads straight to it: by 09:26 he stood in Wickton (`village` log). The first town is placed the same way.
- **The warning existed and arrived late.** `Danger · Black Glades` is an IMPORTANT notice, so it waited behind other
  toasts for 19 s; the 10-minute cooldown is per player, not per region, so a second danger region inside ten minutes is silent;
  and its text ("be careful") says nothing a player can act on.

---

## 2. The audit: why a level-72 land sits against the Commons

### 2.1 The rules in one paragraph (`living/region/RegionMap.java`, SPEC §21.2)
Hearts on a jittered grid (pitch 900, ±35%), borders from the nearest heart after a 220 warp. The Commons is everything
within 700 of the centre (levels 1 to 8: a ramp from 240 to 700). Every other region is dealt one of five bands (1-8, 8-20,
20-45, 45-80, 80+) by odds that depend on how far its *heart* is: "neighbours" (heart within 1,600) get band 1 or 2 only;
under 2,000 `25/45/25/5/0`; 2,000-5,000 `15/30/30/20/5`; beyond `10/20/25/25/20`. Harsh biomes tilt the band up one 40% of the
time. A region's levels run six wide inside its band, low at its edge, high at its heart.

### 2.2 The causes (in the order they matter)
1. **The safety cap measures the wrong thing** (`RegionMap.java:21, 175-183`). "Neighbours never above 20" tests the distance from the
   city to a region's *heart*. A region 600-1,400 across with its heart 1,703 away reaches the Commons' wall. Black Glades'
   heart is 1,703 from the centre, 103 past the 1,600 cap, so it was dealt like any region "under 2,000".
2. **Five percent became one card in nine, every time** (`RegionMap.java:213-234`). Each 3×3 block gets a deck of nine bands in
   proportion to the odds, rounded up by largest remainder. For `25/45/25/5/0` that is 2.25, 4.05, 2.25, 0.45, so eight
   cards are handed out and the ninth goes to the biggest remainder, the 0.45: **every near block holds exactly one level
   45-80 region (11%, not 5%)**. Black Glades is that card for its block. (Side finding: the 2,000-5,000 deck rounds its 5%
   of level 80+ to zero, so band 80+ never appears there.)
3. **There is no gradient.** A region's level is its own band whatever its neighbour is. The Commons' wall is 8; the region
   outside starts at its band's *low* edge, 72. Nothing eases the step (`levelAt`, lines 108-119). Along the north road the
   level goes 8, 8, 72 across twenty blocks.
4. **Nothing placed on purpose asks the land.** The first hamlet, the first town, the roads' hubs, the first gate and the
   happenings all pick a spot and never ask what level it is.
5. **Elites and density do not follow the start's needs.** Hostile spawns run at full density from 640 out
   (`CityMonsters.pass`), and 5% of every hostile spawn is an elite (`LivingModule.ELITE_CHANCE`), including level 1-8 ground.
   An elite multiplies health by 2.5 on top of its level, which at the Commons' levels is worth far more than the level
   itself (see 3.2).
6. **Two band systems.** The deal uses 1-8 / 8-20 / 20-45 / 45-80 / 80+; the plates, the Atlas colours and the happenings use the ranks E 1-9, D 10-24,
   C 25-44, B 45-69, A 70-99, S 100+ (`Regions.bandOf`). A region "in band 3" can be half B and half A.

### 2.3 How often (prototype run on the real `RegionMap`, 300 random worlds, 32 bearings each, same biome tilt for both models)
| Question | Today |
|---|---|
| Worlds with land at level 20+ within 1,200 blocks of the centre | **60%** |
| Worlds with land at level 45+ within 1,800 | **85%** (within 2,500: 100%) |
| Nearest level 70+ land | median **2,000** blocks out (P10 1,400) |
| First hamlet's 700-1,100 band above level 15 | **8%** of worlds (at most level 10 in only 60%) |
| Biggest level change over 100 blocks, inside 1,800 of the city | median **59** (P90 71, worst 139) |
| Share of 100-block steps (500-6,450 out) that change more than 12 levels | **8%** |

So Euan's walk is the ordinary case, not bad luck. And the other half of what he said is also true: today a *calm* land can
sit right at the wall too (his Amberwood is 1-7) and the next one over is 72.

---

## 3. What a level changes today (confirmed, with corrections)

### 3.1 The table
| Thing | Changes with a level? | Where |
|---|---|---|
| A hostile monster's body | **Yes, only this:** health ×(1 + 0.075·lv), damage ×(1 + 0.04·lv), armour + lv/12. Projectiles and blasts scale the same (`Creatures.onIncomingDamage`) | `Creatures`, `MobScaling`, SPEC §2 |
| An elite | +2 levels, health ×2.5, 12% bigger; 5% of hostile spawns, at every level | `Creatures.tag` (and fauna, `FaunaModule:187`) |
| A passive animal (pig, sheep) | **No.** It gets a level for its plate and its XP (land level − 2) and keeps its vanilla body. So "Pig Lv 68" in a level-72 forest is a pig | `Creatures.tag` |
| XP per kill | A little: +8% per level above you (max +40%), × 1/(1 + levels below / 8). Small next to the hunt budget (0.10 of a level per 10 minutes) | `KillXp` |
| Drops | Only AWAKEN's own creatures: Essence Shards scale `base + per level × level` (`awaken:mob_level_count`). Vanilla monsters and animals drop vanilla loot. The plan's "elite: a guaranteed family material, 10% Power Core Shard" has no code for vanilla elites; they pay 4× XP and a 4% Marked-weapon chance (`arms.MarkedDrops`) | `MobLoot`, `arms/MarkedDrops` |
| Which creatures live there | **No.** Fauna kinds follow the biome's family; named beasts follow "player level 3+". Nothing follows the region's level | `FaunaRules`, `BeastRegistry` |
| Camps and dens | From level 8 (a ledger cell seeds a riftborn camp 45% and a hollow den 30%); a camp's creatures are +3 and +2 per tier | `Camps.seedCell` |
| Happenings | Mostly **no**; only the Wild Hunt and the Beast Duel are limited to ranks D and up | `happening/*.json` |
| Wayfarer chains, bounties | Yes, by their own band or level | `quest/`, `bounty/` |
| A warning | Yes: a caption and a toast when a region's top is 5+ over you, except the Commons | `RegionTracker` |
| The land's look, sound, light, weather | **No.** Region level is a label on whatever biome is there | no code reads it |
| Where roads, hamlets, towns, the first gate go | **No** | no code asks |
| A Hearth's land | Calmed to `min(region, household's highest level)` | `Regions.hearthCalm` |

So the brief was right: nothing but body stats, XP and a few seeds follows a level. Two small corrections: AWAKEN's own
creatures *do* drop more shards at higher levels, and the first elite loot the plan describes was never built for the
overworld.

### 3.2 What a level is worth in numbers (the formulas above, for a stock creature)
| Level | Health | Damage | Armour | An elite of that land (level + 2) |
|---|---|---|---|---|
| 1 | ×1.07 | ×1.04 | +0.1 | health ×3.1 |
| 8 (the Commons' wall) | ×1.60 | ×1.32 | +0.7 | **health ×4.4**, damage ×1.40 |
| 20 | ×2.50 | ×1.80 | +1.7 | health ×6.6 |
| 45 | ×4.38 | ×2.80 | +3.8 | health ×11.3 |
| 72 | ×6.40 | ×3.88 | +6.0 | health ×16.4, damage ×3.96 |

Three things follow. **A level is faint at the start** (level 1 to 8 is 50% more health), which is why the pig felt the same. **At
low levels an elite is a bigger step than the whole ladder** (×2.5 beats 1 to 8's ×1.5), so a level-8 elite is worth almost
a level-72 skeleton in the fight a level-1 player can win. And **a level-72 skeleton with a stone sword is about 26 hits for the player and
two or three arrows for you** (20 HP; its arrows hit ×3.9).

---

## 4. What strong open worlds do (and what we keep)

(Sources at the end of this section. "From memory" means I did not re-check it this session.)

| Game | What it does | We keep | We leave |
|---|---|---|---|
| **World of Warcraft** | Zones carry level ranges that overlap and step: Elwynn Forest 1-10, Westfall 10-20, Duskwood 18-30 in Classic. The start is flanked by gentle neighbours; the worst places sit behind a door or a named elite | Overlapping steps between neighbours; elites at *places*; the level shown against *you* (our chips already go grey and ember) | Hard level locks, invisible walls |
| **Elden Ring** | Miyazaki: where the team did not want a signpost it let tough enemies turn players away; Limgrave's brutes (a mounted sentinel on the road) are visible from afar and avoidable; red Caelid's look says its danger (player commentary) | Danger that is *legible before it is felt*; brutes in named places; the way round always exists | Killing the unwarned: a boss on the first road with nothing but its size to say "not yet" |
| **Breath of the Wild** | No levels: enemy tier by colour (red, blue, white, silver Lynels), regional hazards, and early Lynels that mostly ignore you until provoked | Tiers you read at a glance; danger you can see and choose | Hazards as the only gate (we have levels already) |
| **Valheim** | The biome *is* the tier: Meadows, then Black Forest (copper and tin, trolls that kill the unready), Swamp (iron), Mountains (silver), Plains; each boss drops what the next needs | **The land owns its materials**: going somewhere harder pays in the next tier's metal | Rings with no gradient; an early death with no tell |
| **Skyrim** (from memory) | Zones with a minimum and a maximum level | A floor and a ceiling per land | Levels that freeze while you outgrow them |
| **Monster Hunter** (from memory) | No area levels: *rank* decides which monsters live in a place and which parts they drop; the same map is re-used with new monsters and a new gear tree | **A rank changes what lives there and what it drops**; parts are the tier materials | - |

Also from our own research (`docs/progression/research.md` 13): danger is the honest soft gate, and a text box saying
"this needs level 20" is exposed the first time someone tests it. The land has to *look* like the number.

Sources: [Miyazaki on Elden Ring's signposting (GameBanshee)](https://gamebanshee.com/98hp8) and `progression/research.md` 13 (DICE 2023, Game Developer);
[WoW Classic zone ranges (PC Gamer)](https://www.pcgamer.com/wow-classic-zone-levels/), [zone scaling (Warcraft wiki)](https://warcraft.wiki.gg/wiki/Scaling);
[Valheim biome order (Mobalytics)](https://mobalytics.gg/gamebase/guides/valheim-progression-guide-for-all-biomes);
[Lynel tiers and regions (Zelda Dungeon)](https://zeldadungeon.net/wiki/White-Maned_Lynel) (community guides, not Nintendo).

---

## 5. The plan

### 5.1 The idea in three lines
1. **A ceiling by distance, a temper by region.** Distance from the city decides the *highest* a land can be. Each region has a
   temper (calm to savage) that decides how close to that ceiling it gets. Same distance, very different lands: that is the
   patchwork, with a lid on it.
2. **Borders are slopes, not cliffs.** Levels ease across a border over about 450 blocks.
3. **The land speaks first.** A land says what it is (a tint, a sound, a post on the road, a toast that says what the
   numbers mean) before a creature does, and what a land holds depends on its rank.

### 5.2 Layout (decisions 1-6)

**The ceiling** (decision 1). The highest level a land can have at a distance `d` from the city's centre:

| Distance | 240 | 700 (the wall) | 1,000 | 1,500 | 2,000 | 3,000 | 4,000 | 5,500 | 8,000 |
|---|---|---|---|---|---|---|---|---|---|
| Ceiling | 1 | 8 | 12 | 20 | 30 | 52 | 74 | 105 | 150 |

(Straight lines between the points; it lives in data, `reach.json`, so it can be tuned without code. Inside 700 it is the
Commons' own ramp, unchanged.) The ceiling reaches rank D at about 850 out, C at 1,750, B at 2,700, A at 3,850 and S at 5,300.
A "temper" between 0 and 1 places each land between a calm floor (8% of the ceiling) and the ceiling.

**The temper** is made of the region's own roll (dealt per 3×3 block so every block still holds the spread, as today), a smooth
"mood" of the wider land (about 2,400 across, so there are highlands and lowlands of danger, not just single regions), and
the biome tilt (harsh and gentle, as today, ±0.15). A region is worse at its heart than at its edge (by 20%).

**Soft borders** (decision 2). Every nearby heart votes on the level, the nearer the louder, so no step is bigger than a slope.
Measured: the biggest change over 100 blocks is 4 levels inside 1,800 of the city, 8 inside 3,000, 15 inside 5,000 (today 59, 69, 126).

**A safe heartland** (decision 3). The Commons stays 1 to 8. Out to about 1,000 the ground is the ceiling itself (8 to 12): the
one place where distance is the *whole* answer, like a starter zone. Calm and sharp pockets open up from 1,000 and the full
patchwork from about 1,300. Placed things obey the land:

| Thing | Rule | Why |
|---|---|---|
| The Wayfarer camp (447 north) | unchanged: level 4 | already fine |
| The first hamlet (700-1,100 up the road) | the site must be level ≤ 12 | it is the first place a level-3 player goes |
| The first town (the first region north of the hamlet whose land takes one) | prefer a site ≤ 22 | the 3-5 hour stop |
| The Director's first gate | re-roll the bearing until the land is ≤ your level + 5 | a gate door in a level-72 forest is a trap |
| Wayhouses, other towns | no rule: the ceiling already bounds them | |

**Quiet vales** (decision 4). One region in eight beyond 2,000 is calm wherever it is (level ≤ rank D): somewhere to settle, to
retreat after a bad walk, and Euan's own "a calm one can wait five kilometres out".

**Roads and settled land are calmer** (decision 5). Within 24 blocks of a laid road the level is 6 lower (easing to nothing by 60);
within 96 of a settlement's built ground the land is capped at 10 (hamlet), 14 (village), 18 (town). It is the Hearth's calm,
given to anyone's home. "The road is the safe way in; the verge is the wild" is also what the caravans already assume.

What this does to Euan's world (rules applied to his seed; the Commons, Amberwood and Black Glades are exact, biome tilt is approximate):

| Out from the city | 450 | 700 | 800 | 870 | 1,000 | 1,300 | 1,700 | 2,000 | 2,500 | 3,000 | 4,000 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| North road, **today** | 4 | 8 | **72** | 72 | 72 | 75 | 77 | 74 | 50 | 32 | 30 |
| North road, **proposed** | 4 | 8 | 9 | 10 | 11 | 17 | 24 | 28 | 31 | 28 | 27 |

Black Glades itself becomes level 9 at its edge by the wall and 24 at its heart (the heart is 1,703 out, where the ceiling is
about 24): still the sharpest land near home, now one you can walk into at level 10. The first hamlet is level 10. The map on the approval page shows both.

The rest of the world, 200 random worlds (`docs/regions/results/annuli-final.txt`): share of land by rank at each distance.

| Distance | E 1-9 | D 10-24 | C 25-44 | B 45-69 | A 70-99 | S 100+ | levels P10 / P50 / P90 |
|---|---|---|---|---|---|---|---|
| 700-1,000 | 77% | 23% | | | | | 9 / 9 / 10 |
| 1,000-1,500 | 57% | 43% | | | | | 5 / 9 / 13 |
| 1,500-2,000 | 31% | 67% | 2% | | | | 6 / 12 / 20 |
| 2,000-3,000 | 15% | 57% | 27% | 1% | | | 8 / 18 / 32 |
| 3,000-4,000 | 5% | 36% | 44% | 15% | | | 12 / 28 / 49 |
| 4,000-5,500 | 1% | 21% | 36% | 32% | 10% | | 17 / 40 / 69 |
| 5,500-7,500 | | 11% | 25% | 32% | 25% | 7% | 24 / 55 / 94 |
| *Today, for comparison, at 2,000-3,000* | 21% | 36% | 24% | 13% | 4% | | *4 / 17 / 61* |

It is still a patchwork (at 3,000-4,000 the middle 80% of land runs from level 12 to 49), but no land is more than a
walk away from a land one rank below it.

**Where the teeth are.** The nearest level-20 land is about 1,600 out (today 1,150, and 800 for one world in ten), level 45 at
2,900 (today 1,450), level 70 at 4,100 (today 2,000). Near home the teeth are *places*, not carpets: camps, dens and a named beast's lair at
levels the ground allows (decision 7).

### 5.3 What a level means (decisions 6-10)

**Land ranks** (decision 6). One band system: the ranks the player already knows from their chip, gates and the Atlas
legend: **E 1-9 · D 10-24 · C 25-44 · B 45-69 · A 70-99 · S 100+**. The deal's 1-8 / 8-20 / 20-45 / 45-80 / 80+ goes; the
Atlas legend becomes 1 · 10 · 25 · 45 · 70 · 100. A region's caption is the range it really has ("Lv 9-24").

**What each rank changes, beyond health and damage** (decisions 7-9; the "finds" column was checked with the Arms planner, see below):

| Land | Elites that roam | Dens and lairs | What the land says (decision 11) | What it holds (decision 9) |
|---|---|---|---|---|
| **E** (the Commons, the first ring, quiet vales) | **0%**: none roaming. An elite is a den's guard, visible, in a named place | the first den at the wall | nothing (home) | hide, bone and flint (the E kit), the E beasts' trophies (kept), Hale's issue, the camp, the first hamlet; E-theme riftborn camps in the top of E land |
| **D** | 2% | camps, dens, a named beast's lair (level 3+ players hear of them) | a cool tint and a low sound for anyone 5+ below it | a surface sign of iron (a pull: iron itself stays in lodes by depth); **D trophies**, "Terror of <region>" beside the existing named beasts; lair rumours |
| **C** | 4% | riftborn camps, bigger beasts | a stronger tint and sound | a surface sign of diamond (a pull: diamond stays by depth, any region); Marked weapons from elites |
| **B** | 5% | Lords' camps | strong, with an ember edge | B and C Lords' parts from camps (Deepglass seeps stay below y 0 in any region) |
| **A** | 5% | guardians' camps | strongest | the deep guardians' parts; Lord-forged finds |
| **S** | 5% | the Court's | strongest | Starfall comes more often here (weighted, not exclusive); the Herald's parts |

**What the land owns, agreed in principle with the Arms planner** (their reply of 2026-10-27, with four corrections that are now in
this plan; their own plan is a proposal too: Euan has not answered its 12 decisions, page
https://claude.ai/artifact/DA4J66cpZkKinGbm6X18ML, `docs/arms-progression/PLAN.md` on `pack/arms-progression-plan`, §3.6 and decisions 8 and 9):
- **The land owns the hunt's part**: hide and a named beast's trophy. Hale's part-job text can name the land by its in-world name
  (regions have stable ids). Every part slot is a tag with several entries, so the existing named beasts stay in
  `#awaken:hunter_core_d` and "Terror of <region>" joins them: nothing is removed, no save is stranded, nothing becomes "only here".
- **Metal stays depth-native** (SPEC §28.0.7, W2): iron, diamond (y -58 to -18) and Deepglass (below y 0) are in lodes by depth in
  any region. A higher land may show a surface sign as a pull, but diamond must not exist only in C land (the pacing sim has diamond in hand at
  about 13.8 h against the C tier at 13.9 h).
- **Light stays gate-native**: C's part is a D Lord's relic from D gates, and gate rank rolls from your rank, not from the land.
- **For C and up the tier opens before its land.** Tiers open at 13 (D, after rank 10), 22 (C, rank 25), 42 (B, 45), 67 (A, 70) and 97 (S, 100),
  so C and up open three levels before their rank. The locked tier card and a "What waits" promise that names the land do the pulling, and a tier's kit
  never needs a land-native part before the player can reach that land. (An earlier version of this plan said the land came first.)
- **Marked odds** keep their rates (4% elite, 10% Warden, camp hearts at 10%, a guaranteed first one and a pity). Their pity counts qualifying kills (elite,
  Warden, camp heart), not spawns, with its threshold from their sim row, probably lower at E where elites are rarer. With no roaming elites in E land the
  first Marked comes from a camp heart or a gate elite, which is what their plan assumed.
- **Their assumptions about the Commons hold**: nothing needs the Commons above level 8 or elites roaming there, and E-theme riftborn camps (Ember Mines, Bell
  Crypt, Fungal Hollow, Hive, Overgrown Temple) must stay reachable in the top of E land. SPEC §21.8 seeds camps in ledger cells of level 8 or more; the ring
  700-1,000 out is level 9-10 in the proposed field, so they stay. A Phase 1 test checks that a cell 800 out still seeds them. Hale's issue and rack sit at the High Gate and need no land rule.

**The pig** (decision 8). Passive animals do not carry a danger level: the plate shows the name and no chip, they are not
scaled, and the XP stays small and flat. A pig is a pig. What changes is the *game*: in D land and up each passive animal drops
one more of its ordinary drop per rank above E (a pig in B land: four chops), which is also where the hides for the Hide
tiers come from. A neutral or hunting creature, which can hurt you, keeps its level and its scaling.

**Elites live at places** (decision 7). Roaming share by land rank: E 0%, D 2%, C 4%, B and up 5% (both spawn paths:
`Creatures` and `FaunaModule`). A den's guard is always an elite and always stands in the open at a named place. The pacing
sim is indifferent to this (7.2).

**Going above your level pays in finds, not in bigger numbers** (decision 10). The kill-XP bonus stays at +40% (+8% a level up to
+5), because a bigger one rewards walking into death; the first step into each land rank above yours is a small discovery
("First steps in C land", a tenth of a level, once per rank, kept through death); and everything the table above says the
land holds. Wonders (one a region) and secrets rank with their land.

### 5.4 The land speaks first (decision 11)

All relative to the player's own level, client side, no new state: **the gap = the land's level where you stand minus yours.**

| Gap | The land |
|---|---|
| under 5 | nothing |
| 5 to 9 | a faint cold tint (Lens look `living:dread`, level 1) and a low bed under the sound |
| 10 to 19 | stronger, with an ember edge and a slow pulse (off with reduced motion) |
| 20 and up | strongest; the toast has already come (5.5) |

It eases in over about 4 seconds as you walk, never snaps. A client setting turns it off. It stays on with the game rule
`awaken:pure_survival` (it is atmosphere, not a plate). **March posts**: where a laid road first passes into land a rank above the land
behind it, a post stands (the waymarker's own pieces) whose sign reads the land's name and "Creatures level 10 to 24".
No sign is written on trails.

### 5.5 Warning before death, never a wall of text (decision 12)
- **Look ahead.** Every two seconds the server looks 150 blocks ahead along your heading. When the land ahead is 10 or more above you *and* rising, you
  get one toast, at CRITICAL tier (the tier that skips the notice pace; the 19 s wait is that pace queue holding an IMPORTANT notice), once per land per five minutes
  (the cooldown moves from per player to per region).
- **The words are plain and carry the numbers:** "Black Glades ahead. Creatures there are about six times as tough as you
  and hit nearly four times as hard. Turn back, or go in only to look." (computed from the formulas in 3.2 against your level;
  two lines at most.)
- **The Commons' wall warns too**, for a player 5 or more below the local level (a level-1 player at 640 out reads "the land rises: level 8 ahead").
  Inside the city's reserve nothing is sent, as now.
- The Atlas shades by the real level (a smooth field, not one flat colour per region) and labels each region with the range it really
  spans. A level-5 player reads the red from the Horizon before arriving.
- What was missing for Euan, in order: (1) the place never said it was above him, (2) the elite's plate said ELITE but arrived
  once he was already in range, (3) the toast came late, (4) "be careful" is not advice.

### 5.6 Existing worlds (decision 13)
`RegionService` saves only the centre and a `fixed` flag (`awaken:living_regions`, schema 1). Every region and level is
recomputed from the seed and the centre each time the world loads, so **new rules apply to every existing world the next time
it loads, with no migration**: Euan's Black Glades becomes level 9-24 and Wickton (his first hamlet) level 10 on his next launch.
What needs care:
- **Names.** `Names.name` uses the band for its grim words, so a changed band could rename "Black Glades". New saves write
  `rules: 2` into `awaken:living_regions` (schema 2, `optionalFieldOf`); a save without it is `rules: 1` and keeps naming by the
  old band roll, so every discovered name stays. New worlds name from their new level, so a level-70 land sounds like one.
- **Creatures already alive** keep their tag and level (permanent modifiers); hostile ones despawn within a play-day. Ledger
  herds and packs, camps and lairs re-level from the land the next time they spawn (`creatureLevel` reads the field at spawn).
- **Already-seeded camps** in cells that were level 8+ stay; the field only moves their level.
- **Promises** `region:<id>` keep their id; their "needs level N" refreshes at the next crossing.
- **Calm lands near the wall that were 1-7 (his Amberwood) become 9-10**: the price of a smooth start. Say so in the build log.

### 5.7 Multiplayer
The level field is a pure function of the seed and the centre: every player sees the same world. Warnings, tints and the XP
bonus read each player's own level (a party's tint is per person). Elite shares depend on the land only. Hearth calm, the
camp's safe ground and Together's group rules are unchanged. Nothing is stored per player but the "first steps" discoveries.

---

## 6. The decisions (all with a recommended answer)

| # | Decision | Recommended | Why |
|---|---|---|---|
| 1 | A **ceiling by distance** with a temper per region under it (table in 5.2) replaces the dealt bands | **Yes** | removes the cause of the 72; keeps "a dangerous valley two km out, a calm one five out" |
| 2 | **Soft borders**: levels ease across ~450 blocks; at most ~12 levels in 100 blocks inside 5 km | **Yes** | measured: today 59 inside 1,800 |
| 3 | A **safe heartland**: Commons as is, ground ≈ the ceiling to 1,000; first hamlet ≤ 12, first town ≤ 22, first gate ≤ your level + 5 | **Yes** | the first three hours stay survivable and the first stops are never in a trap |
| 4 | **Quiet vales**: one region in eight beyond 2,000 is calm | **Yes** | settle and retreat; keeps the original "calm far out" |
| 5 | **Roads and settled land are calmer** (−6 on roads, caps 10 / 14 / 18 round settlements) | **Yes** | safe corridors; the Hearth's calm for everyone's home |
| 6 | **One band system**: land ranks E-S = the plate and Atlas ranks (1 · 10 · 25 · 45 · 70 · 100) | **Yes** | the chip, the gate and the land say the same word |
| 7 | **Elites live at places**: roaming share E 0 / D 2 / C 4 / B+ 5 %; dens keep an elite guard | **Yes** | the thing that killed him; pacing sim unchanged (7.2) |
| 8 | **Passive animals** carry no level chip, no scaling; richer game by rank | **Yes** | the pig |
| 9 | **The land owns the hunt's parts and the pull**: trophies and hide by land, surface signs of metal; metal stays by depth, light stays in gates; nothing is "only here" (agreed in principle with the Arms planner; their plan is not approved yet) | **Yes** | going somewhere harder pays, and no save or tier gets a wall; Valheim and Monster Hunter |
| 10 | **Going above your level pays in finds**; kill-XP bonus stays at +40%; first steps into a rank are a discovery | **Yes** | no reward for suicide |
| 11 | **The land speaks first**: dread tint and sound by the gap, march posts on roads | **Yes** | "danger is the honest soft gate" only works if it looks like danger |
| 12 | **Warn early and plainly**: 150-block look-ahead, CRITICAL tier, per-region cooldown, numbers in the words; the Commons' wall warns a level-1 player | **Yes** | the 19 s delay, the missing warning at the wall |
| 13 | **Existing worlds** switch on load, names kept, living creatures finish their lives | **Yes** | no migration; Euan's world is fixed on next launch |
| 14 | **Build order**: Phase 1 now (1-6, 7, 8, 12, 13), Phase 2 straight after (11), Phase 3 with the Arms and deep builds (9, 10) | **Yes** | Phase 1 is the fix |

---

## 7. The numbers

### 7.1 The pacing sim (`tools/sim/pacing_sim.py`, balanced player, 300 runs; `docs/regions/proto/sim_variants.py`)
The sim does not model the map; it models the share of hunting minutes spent where creatures are within two levels of you (`zone_fit`
0.5-0.8) and the elite share (5%). The plan changes both, so I ran it both ways:

| Run | Level 5 | 10 | 25 | 50 | 135 | Targets |
|---|---|---|---|---|---|---|
| Today | 0.97 h | 3.94 | 16.82 | 51.52 | 102.14 | pass |
| Elites 0 / 2 / 4 / 5% by rank | 0.99 | 3.90 | 16.83 | 51.25 | 101.93 | pass |
| + zone fit −0.1 everywhere | 1.00 | 3.93 | 17.06 | 51.99 | 102.65 | pass |
| + zone fit −0.3 everywhere | 1.00 | 3.93 | 17.18 | 52.35 | 103.61 | pass |
| + zone fit −0.5 below level 25 | 1.00 | 4.00 | 17.52 | 52.18 | 102.98 | **fails minute-60 level (4, target 5-6)** |
| + zone fit −0.5 everywhere | 1.01 | 3.98 | 17.54 | 53.26 | 104.94 | **fails level 5 (1.01 h, target 0.75-1.0) and minute 60** |

(Targets: level 5 at 0.75-1.0 h, 10 at 3-4, 25 at 15-20, 50 at 50-60, 135 at 95-105; level at minute 30: 3-4, minute 60: 5-6.)

Reading: the hours hold with a lot of room: the hunt budget (0.10 of a level per 10 minutes) caps kill XP, so neither
fewer elites nor a worse fit moves it. Only a very bad fit *early* (half the first-hour hunts nowhere near on-level
creatures) breaks the first-hour check, and the heartland is built to prevent that: from the Monte Carlo, the nearest land within
3 levels of a level 3, 5, 8, 12, 18, 25, 35, 50 or 70 player is at 566 / 566 / 566 / 800 / 1,281 / 1,720 / 2,236 / 2,973 / 3,863
blocks (today 566 / 566 / 566 / 632 / 800 / 1,612 / 1,612 / 2,000 / 2,010). The first three match today; the later ones are
farther by up to a kilometre or two, which is fast travel for a player with a mount (level 11) and gone for a flyer.
**What the sim cannot check:** deaths in the open world (it only models gates), and the real `zone_fit` (unmeasured: it was
chosen, not observed). Both want a tour after Phase 1 (8.3).

### 7.2 The prototype (`docs/regions/proto/NewMap.java`; same grid, warp and hashing as the build)
Everything in 2.3 and 5.2 comes from `Compare`, `Annuli`, `Cliffs`, `EuanWorld` (run `proto/run.sh`). One lookup costs 1.6 µs
against 0.3 µs today (the build will cache each heart's temper and allocate nothing); lookups happen at spawn, once per player
per two seconds and on the Atlas, so it is not a budget item.

---

## 8. Building it

### 8.1 Phases (one builder at a time, at xhigh, no sub-agents)
| Phase | What | Files | Estimate |
|---|---|---|---|
| **1. The fix** (decisions 1-8, 12, 13) | the level field and its data file; one band system; `Region` low/high from the field; `rules` flag and name freeze; placed-thing caps; road and settlement calm; elites by rank on both paths; passive plates with no chip; look-ahead warning at CRITICAL with plain words; the Atlas's smooth shading and legend; SPEC §21.2 rewritten; the sim's elite and zone knobs added to `pacing_sim.py` and `Curve`'s mirror | `living/region`, `living/api`, `living/combat`, `living/client`, `village` (hamlet, town placement), `gates` (first gate), `atlas`, tests | **4-5 h** |
| **2. The land speaks** (11) | `living:dread` Lens look, the sound bed, March posts, client settings, a CI look tour (gap 0 / +5 / +15) and a listening pass on the Mac | `lens`, `living/client`, `village/road`, audio, `tools/autopilot` | **3-4 h** |
| **3. Finds by land** (9, 10) | land rank on the Arms `sources.json` places, "Terror of <region>" trophies beside the existing named beasts, surface signs for lodes (W2 owner; a pull only), richer game, shard scaling for vanilla creatures, "first steps" discoveries | `arms`, `delve`, `mobs` loot, `living` | **2-3 h of ours**, inside the Arms and deep builders' files |

**Total 9-12 hours of builder time at the pace git history shows** (Living Wilds Phase B, regions, levels, XP and plates,
landed in one working morning; Between the Gates W7, roads and towns, in a day). Phases 1 and 2 back to back are about one working day.

### 8.2 Tests (one per mechanism, filtered runs while building, the suite once at hand-off, CI on push)
`living_region_ceiling` (no sampled point above the ceiling, 8 seeds), `living_region_no_cliffs` (no 100-block step above 15 inside 5 km),
`living_region_placed_levels` (hamlet ≤ 12, town ≤ 22, first gate ≤ level + 5, camp), `living_region_calm` (road −6, settlement caps),
`living_region_old_world` (a `rules: 1` save keeps its names and ids), `living_elite_by_rank`, `living_warning_ahead` (CRITICAL, within
2 s, per-region cooldown), `living_passive_plate` (no chip), `living_dread_gap` (the tint and sound by gap, pure rules).

### 8.3 What I would look at after Phase 1 (one CI look tour, not before)
The camp road from the High Gate to 2,500 out at three framings, with the level on the Horizon line; and `/awaken living regions 3000` on Euan's world.

---

## 9. Risks and what this plan does not claim
- **The ceiling table is a design number, not a measurement.** I picked it so level 20 is 1,600 out, 45 is 2,900, 70 is 4,100 (`reach.json` makes that a
  five-minute change). Euan's play decides whether it feels too tame or too far.
- **The near ring is flat by design** (levels 8 to 12 out to 1,000). It is where distance is the whole answer.
- **Biome tilt is approximate** in my numbers (random 10% harsh, 25% gentle for both models). The real tilt moves single regions, not the shape.
- **Decision 9 follows the Arms plan,** which Euan has not approved either. If they change a tier's part, the "holds" column changes with it.
- **Roaming elites at 0% in E** could make the first hours feel quieter; dens keep a signposted fight in reach from the wall.
- **Not covered:** the Nether and the End (they keep vanilla danger, §21.0.15), gates (their own levels), the deep dark (+10, unchanged).

---

## 10. Files
- `docs/regions/PLAN.md` (this file); `docs/regions/page.html` (the approval page); `docs/regions/euan-black-glades.webp` (Euan's Atlas screenshot).
- `docs/regions/proto/` (the prototype and scripts), `docs/regions/results/` (outputs quoted above, the map data, the ASCII maps, the sim runs).
- Approval page: https://claude.ai/artifact/MnswCydLWBrdpXzL3f84YE (private to its owner until shared).
