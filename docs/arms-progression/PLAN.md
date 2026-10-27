# Arms progression: where every weapon comes from (plan for Euan's approval, 2026-10-27)

Status: **approved 2026-10-27 with changes** (Euan, in chat to the plan session: "go ahead with everything else that's
recommended", with patterns taught by gates, villages and structures instead of the first-elite rule, and weapons from
boss fights). **Built 2026-10-27, phases A to G, on `pack/arms-progression`** (SPEC §25 "Arms progression" records what shipped, and what did not). Plan branch `pack/arms-progression-plan`. Approval page:
https://claude.ai/artifact/DA4J66cpZkKinGbm6X18ML (source `docs/arms-progression/page.html`).
Labels used below: **VERIFIED** (read in this repo at 7de77457 or computed from it), **RESEARCH** (an outside source,
§2), **PROPOSAL** (not built, nothing measured), **UNVERIFIED** (to be checked by the first builder).

## 0. What Euan said, and the one-line answer

> "we need to figure out a better progression system for the weapons like the daggers, hammers, etc. Right now as well
> if I want to build this, I have no idea where to get a fang or a blueprint. We defo need to figure this out ... make it
> truly feel triple A and not too confusing, while also not being too easy either lol. The first gear shouldn't be too
> difficult tho as well." (2026-10-27, playing 0257c666; his two JEI screenshots: `/Volumes/MEDIA/arms-ref/playtest-27oct/`)

**The answer.** The weapons are built and the route to them is invisible. Nothing hands you a first weapon, nothing says
what a Fang is or where a Blueprint comes from, and the one place that sells Blueprints is a Forge you can't have until
you have dug for iron. So:

1. Every item and every ingredient says what it is and where to get more, in plain words, and a test fails if one
   doesn't (§3.5).
2. Hale hands every new hunter one real weapon at the High Gate, free, with its Blueprint and a spare Fang (§3.3).
3. **The gates give the pattern and the world gives the materials** (Euan's rule): every gate has a creature that carries
   its style's Blueprint, structures show patterns on stands built to be looked at, village smiths sell them by their
   land's rank, six people teach early, and Hale's rack sells every one as the floor, so no one is ever locked out (§3.4).
4. One list, the **Armoury**, answers "what can I make, what do I need, where is it": at Hale's rack, at your Forge and
   in the band, with a **Track** button that puts the missing parts on the screen (§3.2, §3.5).
5. Each new tier announces itself with a named job on Hale's board for its signature part, and never needs more than one
   trophy, one metal run and the essence you already earn (§3.6).
6. Boss fights are where the steel comes from, and the cooler the weapon the rarer it is: weaker ones often, an A or S
   weapon about one boss in two or three, and you can see what a boss carries before you fight it. Found weapons
   can be **raised** to your tier at the Forge, so finding and making both stay worth doing (§3.7).
7. Gear stays the floor, not the lever: the sim says skill decides fights, so the first gear can be generous (§3.8).

## 1. The audit: what a new hunter gets today

Paths are under `src/main/java/dev/awaken/` unless they start with `docs/`, `tools/` or `src/`.

### 1.1 The chain from nothing to a Wildhide Daggers (VERIFIED)

| Step | What it needs | Where that comes from today | What tells the player |
|---|---|---|---|
| Essence Shard | nothing | creatures: 182 of 199 entity loot tables carry it (`loot_table/entities/*.json`, `mobs/MobLoot.java`); at level 3 a night hunter drops about 0.6 per kill, a day neutral about 0.25, a named beast about 3.5; a rift pays 2-3 at E (`world/anomaly/Rifts.java:76`); a Hunt 2 or 6; bounties 0-5; gates | the JEI Codex page says "gate monsters, Lords, named beasts, anomalies and ruin caches" and leaves out ordinary creatures (`merge/compat/.../en_us.json:14`); nothing in the world says it |
| Wildhide Fang | 2 Parts (bone, flint, quartz, a family's horn or shell), 1 Binding (string, feather, moss, a gel or wick), 1 shard (`recipe/wildhide_blade.json`) | skeletons, gravel, spiders, birds; the families' parts | the level 3 toast: "Creature hide, bone and essence: your first set. Recipe book." The tooltip: "E-rank Hunter gear · Wildhide". It never says it is a blade, or what it is for |
| Forge | 6 bricks, 2 iron, a blast furnace (5 iron, a furnace, 3 smooth stone): 7 iron in all (`recipe/forge.json`) | clay, a first iron dig | the level 2 toast: "Forge, Target Dummy, Trophy Mount: recipe book." |
| Daggers Blueprint | at a Forge, sneak and use it with an **empty hand**; 4 paper, 1 lapis, 1 shard; one design per use, in a fixed order of 15 (`arms/ArmsForge.java:60, 126, 159-160, 214-217`) | sugar cane; lapis from caves | nothing: the Blueprint has no tooltip, the Forge has no tooltip, JEI has no Blueprint category, no tip, no person, no Now line |
| Wildhide Daggers | Fang + Part + Binding + Blueprint in the style's shape; the Blueprint comes back (`recipe/wildhide_daggers.json`) | the above | JEI and the recipe book |

The tier ladder after E has the same shape: D needs iron and a beast's trophy, C diamond and a D Lord's relic, B Deepglass
and amethyst, A netherite, S starmetal (`docs/modules/gear.md`, SPEC §28.3). Each tier is announced by one toast at level
3, 13, 22, 42, 67 and 97 (`data/awaken/awaken/level_road/hunter_gear_*.json`).

### 1.2 The gaps, exactly (VERIFIED)

| # | Gap | Evidence |
|---|---|---|
| G1 | Nothing hands over a weapon or teaches how a forged weapon is made. The first thread (Bram, High Gate, camp, Ember) ends with the Ember | SPEC §27.6; `data/awaken/awaken/quest/the_last_delivery.json` |
| G2 | Items don't say what they are or where they come from. Six tier blades are named Fang, Cleaver, Sabre, Edge, Warblade, Oathblade, and the tooltip says "E-rank Hunter gear · Wildhide" and nothing else (Euan's screenshot). Blueprints have no tooltip at all | `gear/HunterGearItem.java:35` (the BLADE case is empty); `arms/client/ArmsTooltip.java` (styled weapons only) |
| G3 | **The first hour promises "never go find iron" and the Blueprints break it.** E gear is hide and bone (SPEC §28.0.7), but every style but the Longsword needs a Blueprint, which needs a Forge (7 iron) and lapis (underground). And how to draw one (sneak, empty hand) is told nowhere | `recipe/forge.json`, `ArmsForge.java:159-160, 214`; SPEC §28.3 |
| G4 | The Forge hands Blueprints out as a fixed cycle (each use shows the next of 15, "Not now" to skip), at one price for every style, and a Blueprint is never used up. So all 16 styles open together, around hour 2, with no ladder and nothing to discover | `ArmsForge.java:60, 214-217` |
| G5 | The recipe book floods: holding any ingredient unlocks every recipe it appears in, so one bone lights all 16 Wildhide weapons, 15 of which need a Blueprint you can't have yet | `progression/RecipeUnlocks.java:37-62`; `docs/progression/audit-spine.md:309` (re-check at build: Phase 5 of The Way Forward may have changed the toast) |
| G6 | There is no page for weapons. The Armoury page was planned in Arms S7 and never built (no code mentions it). Mastery shows only under a weapon's name; "Lord-forged 3 / 15" is nowhere | `docs/combat/PLAN.md` S7; grep of `src/` |
| G7 | Tiers announce themselves with a generic toast and say nothing about *where* the signature part comes from. `GearNow` speaks only when you already hold every ingredient (priority 25) | `gear/GearNow.java:35-58` |
| G8 | Finding: Marked weapons drop 4% from elites (5% of overworld spawns, 0.4 a gate floor) and 10% from Wardens, with no pity; named weapons and legendaries are unbuilt (S9); a Lord-forged weapon is "one step above its home rank's forged weapon", so it equals the next tier's forged weapon and falls behind the one after (damage climbs 4.5 to 9.5, one a tier). Found weapons go stale in about a rank | `arms/MarkedDrops.java:21`, `world/fauna/FaunaModule.java:187`, `living/LivingModule.java:44`, `tools/sim/pacing_sim.py:70,191`, SPEC §25 |
| G9 | JEI shows no Blueprint source and no Forge category; the three slot tags read as jargon (`hunter_core_e`); the Essence page leaves out creatures | `compat/client/jei/Recipes.java`; SPEC §13 JEI |
| G10 | Hale is an unmarked figure with a board of sixteen jobs (eight in each of two bands). The collect jobs ask for string, leather and iron; none names a tier's signature part or says what it is for | `living/wayfarer/Wayfarers.java:34`, `data/awaken/awaken/bounty/` |

### 1.3 Corrections to the brief (VERIFIED, so nothing is built on them)

- **Creatures do drop essence in the open world.** The brief said no creature does. 182 of 199 creature loot tables
  carry `awaken:essence_shard` through `awaken:mob_level_count` (count = floor(base + per level x level + a roll); e.g.
  `gloomstalker`: base 0.5, +0.04 a level; `crag_ram`: 60% of the time, base 0.3). Computed with
  `E[floor(x + U)] = x` over the fauna table: **night hunters about 0.6 shards a kill, day neutrals about 0.25, named
  beasts about 3.5, at level 3** (`docs/modules/mobs.md` has the tier table; `docs/modules/fauna.md:252` says so too).
  So SPEC §13's "E-tier is craftable from first-hour creature drops" is true, and the whole E kit (about 7 shards) is one
  first night of hunting. The problem is that nothing says so, and daytime hunting is slow.
- **Mastery isn't off.** The 10-26 progression audit said it was hard-coded off; Arms S7 wired it (SPEC §25 "Mastery").
- **A and S exist.** SPEC §13's "A and S are designed only" is stale: recipes and items exist for all six tiers (23 recipe
  files each for Lordscale and Sovereign) and the 96 forged weapons cover E to S.
- The Blueprint price in the brief (4 paper, 1 lapis, 1 essence) is right (W2).

### 1.4 What already works, and stays

The three loops (hide from the hunt, metal from the deep, light from the gates, SPEC §28.0.7) and their tier table; the
Lord-forged reveal and its pity; Marks, mastery, attunement and the Study's half-price paper; the spoiler guard and the
JEI categories; the K band's Promises ("What waits"), the task card (§27.10) and the Now line; plain-speech rules.
Nothing here changes a tier's stats, a recipe's metal or a Lord's numbers.

## 2. Research (what we keep; sources fetched 2026-10-27)

| Game | What it does | Keep | Source |
|---|---|---|---|
| Monster Hunter | Every weapon type has upgrade trees from a free bone or ore weapon; branches reveal as you collect the materials; most strong upgrades are monster parts, and the tree screen shows what is craftable now and what is still locked | a free first weapon, a visible tree with locked branches, parts as the progress currency | [Steam discussion](https://steamcommunity.com/app/582010/discussions/0/3203652426719674261), [Push Square tree guides](https://www.pushsquare.com/news/2018/02/guide_monster_hunter_world_weapons_-_all_hammers_upgrade_trees_and_how_to_craft_them) (fan sources) |
| Valheim | Picking up a new material teaches the recipes that use it; stations upgrade with materials; biome progress is tied to bosses | recipes appear when you hold the part (we have it) but only the ones you can actually make | [jeu.video guide](https://jeu.video/en/guide/valheim-how-to-unlock-recipes-next-biome), [Game8](https://game8.co/games/Valheim/archives/321762) |
| Elden Ring | Weapons are found or bought; one smith upgrades them with tiered stones; bell bearings unlock buying the stones, so the floor is guaranteed and the chase is optional | a guaranteed floor (Hale's rack) and a finding chase | [Tom's Guide](https://www.tomsguide.com/how-to/how-to-find-smithing-stones-upgrade-weapons-elden-ring), [Maxroll](https://maxroll.gg/elden-ring/guides/smithing-stones-guide) |
| Terraria | The Guide shows the recipe for any item you show him, with the station it needs | "show me how to make this" from the item itself (our Track) | [Terraria wiki](https://terraria.wiki.gg/wiki/Guide) |
| Diablo 4 | The Codex of Power keeps a power forever once you earn it in a dungeon; each dungeon shows which power it grants | a place that visibly teaches one thing, kept for good | [GamesRadar](https://www.gamesradar.com/diablo-4-aspects) |
| Minecraft Dungeons | Common, Rare, Unique; a unique is the base item plus one extra; 145 of about 232 items are uniques | found = the same item with a story, not a bigger number | [minecraft.wiki](https://minecraft.wiki/w/Dungeons:Item) |
| Breath of the Wild | Breakable weapons drop from the enemies that carry them, so the hunt for a weapon is a reason to fight | the thing you want is carried by something you can see | [Inverse](https://inverse.com/gaming/breath-of-the-wild-2-weapon-durability) (secondhand quote) |
| Arms' own research | Better Combat, Epic Fight, Minecraft Dungeons weapons with built-in abilities | weapons with their own ability and rarity colours | `docs/combat/PLAN.md` §8 |

What the research does not give us: a verified in-game wording for Monster Hunter's Smithy screen (the search found
only fan pages). The design below uses the *pattern* (a visible tree with locked branches), not the wording.

## 3. The design

### 3.1 Three rules (each is a test a feature must pass)

1. **An item answers three questions on its own:** what is it, what is it for, where do I get more. A stranger can read
   a Fang or a Blueprint and know what to go and do.
2. **Every style has a home, and there is always a floor.** One person or one place teaches it (the world pays early and
   it is the story); Hale's rack sells it by rank (the floor: nobody is ever locked out by luck).
3. **Making and finding both matter.** You *make* the weapon that fits your build and your tier. You *find* the ones
   with a story, and the Forge can always raise a found weapon to your tier, keeping what makes it special.

### 3.2 The Armoury: one list, four doors

The Armoury is the single answer to "what can I make and what do I need". It is a screen (a QuietScreen, like "Your
Hearth", SPEC §13 "Hearth guide") and a section of the band. Four doors lead in:

| Door | Where | What you can do there |
|---|---|---|
| **Hale's rack** | the High Gate yard, from the first minute out of the city | read everything; buy the Blueprints your rank opens, for essence only (Hale supplies the ink); take your issue (§3.3) |
| **Your Forge** | at home (and any placed Forge) | the same list; draw any Blueprint you know for 4 paper, 1 lapis, 1 shard (the Study halves the paper); raise a weapon (§3.7); attune and temper as today |
| **A village smithy** | any village or town smith (its Forge) | draw patterns you know, raise, attune; the smith's rack sells two patterns of his land's rank (§3.4) |
| **The band** | K, Powers, THE ARMOURY | read what you know, track a recipe; buying and drawing need a door |

What changes at the Forge: a use with an **empty hand opens the Armoury** (no more sneak, no more cycling). Tempering
(plain use with a weapon) and attuning (sneak with a weapon) stay as they are.

**Hale's rack is a real thing in the world** (recommended): the five E weapons hang on it, as the model weapons you will
make, with a plaque ("E-rank patterns · 4 essence each") and, beside it, a rope with crates marked "D-rank", "C-rank",
"B-rank" and "A-rank". The locks are seen before they open and name what they wait for (SPEC §27, rule 1). A menu-only
version (talk to Hale) is the cheap fallback; it keeps every rule.

**What the screen shows** (every word plain, `docs/modules/arms.md` gets the final copy):

```
THE ARMOURY                                                     Styles 3 of 16 · Mastery: Daggers II
  Longsword   You have it.   Balanced. Good at guarding and answering.
  Daggers     You hold it.   Fast and close. Quick strikes, and you hit hardest from behind.
     E Wildhide    made      D Beastbound   needs: Fang 1/1 · Iron 2/2 · Part 0/1 · Binding 1/1 · Shard 1/2   [Track]
     C Gatewrought  opens at level 22 · set in diamond
  Spear       Hettie teaches it (Ten Sheep Short) · or Hale at D-rank, 6 essence
  ...
  Hale keeps more patterns. He shows you more at D-rank.
FINDS   Lord-forged 1 of 15 · Marked 2 · Named 0 of ??
```

Rules for the page: it lists only styles you know plus the ones whose home you have met (SPEC hard rule: undiscovered
things are never listed; the count is "N of 16"); a locked tier or rack row says what it waits for in the world's words
(a level, a rank, a person) and where to see it; every part line names its source (§3.5).

### 3.3 The first hour (Euan's question 1)

**Hale's Association issue.** The first time a new hunter comes within a few blocks of Hale on the way out, he stops
them (no camera; the High Gate's shot still plays when they leave). He holds out the rack and says:

> "Hold on. You're not going out there with nothing in your hands. The Association gives every new hunter one weapon.
> Take whichever one you like. They're all fine."

The player picks one of **three** (the same pick-one-of-three card as the first power, `powers/Starter.POOL`): **Daggers**
(fast and close), **Axe** (wide cuts that break a guard), **Maul** (slow and heavy). The Longsword is the default every
hunter already has, so the three are the three other *feels*. They receive the finished **Wildhide weapon, its
Blueprint and a spare Fang**, taken from Hale's hand (the Registry's hand-over, `HandOver`), and Hale says:

> "Good choice. That one's yours. I've put its pattern in your pack too, so you can make more of them later. The spare
> blade is a Fang. Everything you forge starts from one. When you want a different style, come and see me. I sell
> the patterns for essence, and the more you've done the more I'll show you."

If a player misses Hale (Dawn Gate, a skip), **Ilse** hands the same issue at the camp "in Hale's name", and the Now line
says "Hale has your hunter's issue · High Gate ↗" (priority 70, just above the first thread's own steps at 68, until taken). Old worlds get it once if they hold no styled weapon.

**Why free, and why this is still earned.** The weapon is E-tier (5 damage, 1.8/s, the verbs) and costs nothing; the
armour, every later weapon and every higher tier are earned. Monster Hunter starts you with a free bone weapon and Hades
lets you pick a weapon before the first room; neither feels cheap because the first real choice is yours. Euan's own
rule is "the first gear shouldn't be too difficult".

**The first night builds the kit** (PROPOSAL; times from `tools/sim/pacing_sim.py`, balanced player: level 3 at 27
minutes, level 5 at 58):

| t | What happens | Who says it |
|---|---|---|
| 0:15 | Hale's issue (above) | Hale, the Now line |
| 0:20 | Ilse, the Ember | existing |
| 0:20-0:45 | the first Essence Shard drops; a card says what it is ("What awakened creatures leave behind. Hunters at night drop about one for every two kills. Armour, weapons and patterns are made with it."), filed in the Journal as a find | the Finds director |
| 0:27 | level 3: the Hunter gear E recipes arrive; the toast now says what you need and where it comes from: "Hide from creatures, a bone or flint and a string or feather, and one Essence Shard for each piece." The Armoury appears in the band | level road text, Armoury |
| first night | the first armour piece (a Hood: 4 hide, 1 shard) in about five kills, the set (about 15 hide, 7 shards) over the night | the Now line: "You can make a Wildhide Hood" (exists), with Track for the rest |
| ~1 h | the first gate: its **bearer** (a creature wielding the gate's style) drops the gate's pattern, a second style, free; an E elite may drop a Marked weapon | the gate card, the bearer's weapon, the reveal |

Essence supply for that kit: about 12 night-hunter kills, or two rifts and a few kills. Two small changes make sure bad
luck can't stall it: a **no-drought rule** (the 5th kill in a row with no shard drops one; per player, E and D ranks
only) and **Ilse's Moth Lights and Marta's Wheel pay shards** (3 between them). The pity matters mostly for day neutrals, where the chance
of four misses in a row is about a third.

**Should creatures drop essence?** They already do. Should anyone hand over essence? Only through jobs (Ilse's, bounties).
Handing over a first stack would teach the wrong lesson ("shards come from a person"), when they come from the hunt.

### 3.4 How each style is found (question 2)

Euan, 2026-10-27, after the first version: "I think unlocking them in gates is a good idea, and then also maybe in
villages ... if I go in a C-tier gate, and then I defeat all the mobs, then at least one of them should drop a blueprint
for a specific weapon ... you get the blueprint in gates, and then you need to get the materials in the world to then
build that blueprint." And: "it shouldn't only be from villages, it should also be structures overall. But not just in a
boring chest ... built really nicely ... set on a nice rack or something ... but you still know that that's something you
can take." And, of the tooltips: "found in a X rank gate, or in a level X-X structure".

**The rule: the gates give the pattern, the world gives the materials.** A Blueprint is the gates' light (with the essence
and the Core Shards); the hunt's Part and the deep's metal still come from the world, so the three loops hold (SPEC
§28.0.7). **A pattern is never left in a plain chest.** It is carried by a creature, shown on a stand, or sold from a rack,
somewhere built to be looked at, and it always reads as something you can take.

Five doors lead to the same pattern; each style has exactly one **gate that teaches it** (its Lord's gate, so a rank's gates
teach that rank's styles), and Hale's rack is the floor under all of them.

| Style | In plain words | The gate that teaches it | Hale's rack | Also taught early by | Its master (Lord-forged) |
|---|---|---|---|---|---|
| Longsword | Balanced. Good at guarding and answering. | everyone has it (no Blueprint) | - | - | none |
| Daggers | Fast and close. You hit hardest from behind. | the Hive (E) | E | - | Hive Regent |
| Axe | Wide, heavy cuts that break a guard. | the Ember Mines (E) | E | - | Ember Tyrant |
| Maul | Slow and heavy. Breaks armour, slams the ground. | the Bell Crypt (E) | E | - | Bell Warden |
| Scythe | Wide sweeps that cut spirits. | the Fungal Hollow (E) | E | - | Mother of Spores |
| War-staff | Each hit charges your power energy. | the Overgrown Temple (E) | E | - | Root Saint |
| Spear | Long reach. Keep things off you and thrust. | the Drowned Archive (D) | D | Hettie (Ten Sheep Short) | Drowned Archivist |
| Rapier | Precise thrusts. Counters hit hard. | the Glass Desert (D) | D | Pell (Pell's Message) | Glass Serpent |
| Chain-blade | A blade on a chain: mid-range, pulls things in. | the Lantern Night Market (D) | D | Marta (The Caravan Road) | Lantern Broker |
| Gauntlets | Fists: strings of punches and counters. | the Clockwork Foundry (D) | D | - | Gearwright Colossus |
| Glaive | Big sweeping arcs for crowds. | the Frost Monastery (C) | C | Gus (The Hermit's Horn) | Abbot of Frost |
| Shieldblade | Blade and shield: block first, bash back. | the Mirror Palace (C) | C | - | Mirror Knight |
| Precursor Arm | An energy blade that changes length. | the Precursor Lab (C) | C | - | Unit Zero |
| Katana | Draw and cut. The guard is the sheath. | the Nightmare gates (C) | C | - | Sleeper |
| Twinblades | Two blades, always spinning. | the Sky Archipelago (B) | B | Wren (Wren's Rope) | Sky Leviathan |
| Greatblade | Slow two-handed cuts that don't flinch. | the Abyssal Shrine (A) | A | Oren (Oren's First Steel) | Hollow Herald |

(Styles, Lords and gate themes: SPEC §25 and `mobs/def/Theme.java`. The rack rule is *a style's rack rank is its master
Lord's rank*. The six people are early shortcuts to styles whose gates are further up the ranks; the Greatblade is taught
early by Oren and sold at A.)

**1. Gates (the main door).** Each gate has one **bearer**: on its first floor one creature (an elite if the floor has one,
else a normal creature) wields the gate's style's own weapon, a creature's held weapon being only a look
(`AwakenMob.gearStack`, SPEC §25 S0), with a faint blueprint-white glint so you can tell which one it is. Defeat the floor and the bearer
has fallen: the pattern drops to every fighter on the floor, each their own, at the bearer's feet with the reveal ("PATTERN ·
Daggers · the Hive's"). It is the *gate's own style* if you don't know it; if you do, **an unknown style of the gate's
rank**; if you know that whole rank, its price in essence. If you reach the floor's shrine without downing the bearer, the
Lord's hoard carries it for you instead (per player, once), so **every gate you clear pays**. A pattern goes straight into
your pack and is known at once: **never the Satchel**, because it is knowledge and a death later can't take it. Once per
gate per player. A gate below your rank still teaches its own rank's band. The trigger is the bearer's death, not the
existing floor-clear (which fires on reaching the floor's shrine, `Runs.floorCleared`, and doesn't prove every creature is
down); `Runs.onKill` already sees each kill.

**2. Structures (the world's own doors).** Any built place worth a visit can hold a **Pattern Stand** (a new block,
`awaken:pattern_stand`, owned by arms; the structure's own module places it through `ArmsApi.placeStand`). It is built
into the place so it looks as if it belongs: a slanted drafting stand under the pylons of a Precursor vault, a weapon rack
in a smithy, a lectern at the top of a spire, a bier under a bell in a crypt, a fallen hunter's bedroll and weapon rack in a
lair. The style's weapon of that tier hangs above it and the pattern is pinned in the Blueprint paper's white line. A soft
blue-white light and the System's find cue ("PATTERN · Katana · use to take it") say it is yours to take, with no wall of
text. You take it with an empty hand, **once per stand per player** (the claim id is the stand's position, so "a reward is
granted at most once per claim id" holds): the stand stays for everyone else and dims for you. A pattern you already
know pays its price in essence instead.

| Structure | Owner | Where the stand is | Its rank |
|---|---|---|---|
| Precursor vaults, monoliths, ocean labs, sky platforms | `above`, `wilds` | the inner chamber | the door's rank (the Horizon line already says it) |
| Tether Spire crowns and hero-tree crowns | `wilds` | the crown (the payoff answers the line, SPEC §27.5) | the region's level |
| Riftborn camps, hollow dens | `living/camp` | the camp's heart carries it like a bearer; a den holds a fallen hunter's stand | the camp's level |
| Named beasts' lairs | `world/fauna/lair` | a fallen hunter's rack | the lair's level |
| Villages, towns, wayhouses | `village` | the smithy's rack (sold, below) | the village's level |

A structure's **rank is the rank of its level** (the plate bands: E 1-9, D 10-24, C 25-44, B 45-69, A 70-99, S 100+;
`docs/regions/PLAN.md` is realigning region bands to them). Its pattern is drawn from that rank's band by the structure's
seed, so every structure has its own and shows the same one every time.

**3. Villages (the second door).** A village's rank is its region's: a village in a level 70 land is an A village. Its
smith's rack shows **two patterns of that rank's band**, picked by the village's seed and never one you know, and sells
them for **emeralds**, the coin (E 6, D 10, C 16, B 24, A 32; PROPOSAL against an iron sword's 6), with standing in the
price like every trade (a stranger pays a little over, a friend and kin under, `village/Trade.java`). So a high-level
village sells patterns Hale's rack won't show for hours: you travel somewhere hard to buy something early. The smithy's
forge is an Armoury door (draw patterns you know, Raise, attune): the builder places an `awaken:forge` beside the anvil
where there is none (UNVERIFIED which smithies have one). **Not finished weapons:** a bought weapon would skip the Part
and the metal, which is what makes a tier earned. If Euan wants a "buy" path later, the smith can sell the Parts of his
land.

**4. People (six early shortcuts).** As before: the chain's last step pays the Blueprint in the person's own plain voice.
Oren: "A big blade is a hammer that's been taught manners. I'll show you once." A style you know pays its price in essence.

**5. Hale's rack (the floor).** Every pattern at its rank for essence only (4 at E, then 6, 8, 10, 12), so a player who
never finds any other door loses only a few shards.

**How a player finds out any of this** (Euan: "even if it says somewhere, how do you know how to find anything that's
going to teach you this?"). The rule is **you are told at a place you will already be, never in a menu you have to go and find**:

1. **Gate cards** say it once the card shows the theme: "The first floor carries a pattern: Daggers." Nothing shows for a
   style you already know, and the bearer's weapon says the same thing in the room.
2. **Camps and structures** say it when you come near: the stand glints and the find cue names the style; a camp's name
   carries the line (UNVERIFIED which surface a camp has today).
3. **People** name their reward in their own offer ("If you help me find the ten sheep, I'll show you how to use a spear.
   It keeps wolves off.").
4. **Hale's plaques** read "4 essence, or free: the Hive's gates teach it", and "Anything on patterns?" names the nearest home
   you have *met*, with a direction (the clerk's "Anything for me?", SPEC §27.8). At each rank he says what the rack gained.
5. **The tooltip says it everywhere.** A Blueprint's tooltip and its JEI line read "Found in: C-rank gates, level 25-44
   structures, villages of level 25-44. Hale sells it at C-rank for 8 essence." (rank and level only, as Euan asked); the
   theme's name (the Frost Monastery) appears once you have cleared that theme (the spoiler rule).
6. **What you've met is kept for you:** a home you've seen and can't use yet is filed under "What waits"
   (`core.api.Promises`: "Katana · the Nightmare gates · 320 m NE"). Homes you haven't met are never listed.

Use earns depth, not access: mastery (five steps, 20 to 800 kills, SPEC §25) stays where it is. Gating a style behind another
style's kills would punish trying something new.

### 3.5 Clarity (question 3)

**Words.** Three plain words everywhere: **Hide** (the body of armour), **Part** (the hard heart: bone, flint, a horn, a
beast's trophy) and **Binding** (what holds it: string, feather, moss, and later an ingot). Tags get names ("Any
E-rank Part: bone, flint, quartz, Crag Horn..."), JEI shows them, tooltips use them. Item names stay: Fang, Cleaver,
Sabre, Edge, Warblade, Oathblade are good names; the *type line* is what was missing (decision 5).

**Tooltips** (copy for the builder to polish, read aloud before shipping):

```
Wildhide Fang                                    Daggers Blueprint
E-rank Hunter blade                              A design. Put it in the crafting grid with a Fang and parts,
A short blade that fights like a sword.          and it comes back out when the weapon is made.
Every forged weapon is made from one.            Found in: E-rank gates, level 1-9 structures, villages of level 1-9.
                                                 Hale sells it at E-rank for 4 essence; a Forge can draw copies.
Made from: 2 Parts, 1 Binding, 1 Essence Shard

Essence Shard                                    Forge
What awakened creatures leave behind.            Temper a weapon: use it. Attune: sneak and use it.
Night hunters drop about one for every two       Use it with an empty hand to open the Armoury.
kills; gate monsters and bosses drop more.
```

Shift expands any ingredient tooltip with its **source line**: "Bone: skeletons, mostly at night. Flint: gravel. String:
spiders. Leather: cows. Crag Horn: Crag Rams in the Crags."

**Source lines are data, and a test enforces them.** `tools/arms/sources.py` reads every creature loot table and writes
`data/awaken/arms/sources.json` (item or tag, a plain line, and a place kind: creature family, ore, vendor, event, gate,
craft); hand-written entries cover vanilla items. **`arms_sources` fails if any ingredient of any Awaken gear or weapon
recipe has no line**, so a new material can't ship without saying where it comes from. Until a creature is in your Codex a
line names its family and region, not the creature (SPEC §13 spoiler rule).

**Track.** In the Armoury, any tier of a style has a Track button. One item is tracked at a time. The task card (§27.10)
lists what is missing, one line each, with counts and the source: "Wildhide Daggers · Fang 1/1 · Parts 1/2 (bone:
skeletons) · Binding 0/1 (string: spiders)". When the missing piece has a known place (a lair you heard of, a lode you
opened, a camp), the Now line (priority 52, below places and rifts) points at it with a compass: "Briar Tusk · the Briar
Sow's lair ↗ 1.2 km NE". This is the one feature that most directly ends "I have no idea where to get a fang."

**Journal and band.** Finds (the Fang you forged first, a style learned, a tier opened, a Marked weapon, a Lord-forged)
are filed lines, and each counts as a *capability* for the new-thing clock (SPEC §27.9). Locks you met go to "What
waits" through `core.api.Promises`: "Hale's D-rank patterns · needs D-rank · High Gate ↗ 90 m".

**What JEI shows** (client only):

- Every Awaken Crafting recipe adds a chip row: style, tier, "Blueprint: Hale's rack or a Forge", the level road step.
- Every ingredient slot's tooltip carries its source line; tag slots show their plain name and what is in them.
- Three new categories: **Armoury** (Hale's prices by rank, the village smiths' by level), **Blueprint** (the Forge draw,
  with the Study's discount) and **Raise** (§3.7).
- A Blueprint's tooltip and its JEI "Obtain" line read from `style_homes.json`: "Found in: C-rank gates, level 25-44 structures,
  villages of level 25-44. Hale sells it at C-rank for 8 essence." The gate's theme appears once you have cleared it.
- The Essence Shard Codex page lists creatures first.
- The spoiler guard is unchanged: undiscovered items and their recipes stay hidden.
- The recipe book unlocks a style's recipes **when you hold its Blueprint**, not when you hold a bone (G5): one new
  line in `RecipeUnlocks` (a recipe whose ingredients include a Blueprint unlocks on the Blueprint).

### 3.6 The tier ladder, E to S (question 4)

Hours are the balanced player from `tools/sim/pacing_sim.py` (levels interpolated between its checkpoints); the part and
metal rows are the shipped recipes (`docs/modules/gear.md`, SPEC §28.3).

| Tier | Opens at (level, hour) | The hunt's Part | The deep's metal | The gates' light | The pull (what the world says first) |
|---|---|---|---|---|---|
| **E Wildhide** | 3 (0.45 h) | bone, flint, quartz, a family's horn or shell | none | 1 shard a piece | Hale's issue |
| **D Beastbound** | 13 (~6.3 h; D-rank 5.1 h) | a named beast's trophy or an E Lord's part | iron, 14 | 1-2 shards a piece, gold for the focus | the lair rumours (from level 3), the first iron lode, **Hale's job: the Briar Tusk** |
| **C Gatewrought** | 22 (~14 h; C-rank 17.9 h) | a D Lord's relic | diamond, 14 (a lode, or two hours below y 0) | 1-3 shards, a Lord's Heart for the chest | diamond lodes, the first D Lord's parts, **Hale's job** |
| **B Veilwoven** | 42 (~42 h; B-rank 47.9 h) | C and B Lords' parts | Deepglass and amethyst | 1-3 shards, a Lord's Heart | Deepglass seeps, **Hale's job** |
| **A Lordscale** | 67 (~66 h; A-rank 69.5 h) | B and A Lords' parts, the deep guardians' | netherite scrap | a Lord's Heart | ancient debris in the Nether, **Hale's job** |
| **S Sovereign** | 97 (~82 h; S-rank 84.6 h) | the Herald's and the Court's parts | starmetal | a Lord's Heart | fallen stars at night, **Hale's job** |

**How a tier is announced and pulled toward** (PROPOSAL; three beats, never a wall of text):

1. **Foreshadow (the lock is seen before it opens).** The first time you hold or see a tier's signature part (a beast's
   tusk, a diamond), the Armoury shows the next tier's card as a silhouette with what it waits for, in the world's words
   ("Beastbound · opens at level 13 · iron, and a beast's trophy"), and "What waits" files it.
2. **Open.** At the level, the toast names the job in plain speech ("Beastbound gear is open to you. You'll need 14 iron
   and a trophy from a named beast. Hale has a job on his board for the first one.") and the tier card lights up with
   have/need and Track.
3. **The job.** Hale's board posts a **part job** for the tier: a `collect` bounty on the signature part whose text names
   the creature and the place ("A Briar Sow is rooting up the Greenwild. Bring back its tusk. Eight essence."). The bounty
   system already supports this (`collect` objectives, `essence` pay, `requires` tokens `level:` and `rank:`). Oren's
   idle line for the rank (already in `townsfolk/oren_blacksmith.json`) points at the Forge.

**No grind walls** (the rule, checked by a sim row): a tier's kit needs at most **one trophy** (a named beast, a Lord's
part; parts drop at a boss's *phase changes*, not only on the kill), **one metal run** (a lode or an hour deep; W2's sim
has the metal in hand at about the level: iron 6.3 h against level 13 at 6.3 h, diamond 13.8 h against level 22 at
13.9 h) and **essence you already earn**. Every part slot takes several things (tags), so a missed drop never blocks.

### 3.7 Finding and making (question 5)

| Kind | How you get it | What it is | Role |
|---|---|---|---|
| **Forged** (crafted) | you make it, at your tier | the tier's weapon: reliable, chosen, attunable and temperable | your build; always current |
| **Marked** (blue) | an elite, a Warden or a riftborn camp's heart drops a forged weapon of your tier with 1-2 Marks | the same weapon with a quirk you didn't choose | try something new |
| **Named** | a place gives it: a theme's last-floor cache, a region's secret, a troubled village's reward, a chain's end; **told by a rumour first** (an Atlas "?") | a fixed weapon with a name, 1-2 Marks and one plain line of history | a keepsake from somewhere you went |
| **Boss weapon** (blue, two Marks) | a chance on every Gate Lord kill, falling with rank (60% at E to 15% at A); guardians, Sovereigns and named beasts too (below) | a forged weapon of the boss's rank in the boss's own style | a boss often pays in steel at low ranks and rarely at high ones |
| **Lord-forged** (ember-gold) | 35% a Lord kill, certain by the third without one (unchanged) | the Lord's weapon and its signature move as the Art | a trophy of a kill |
| **Legendary** | authored, tied to the story | unique model and moveset twist | the long goal (S9, later) |

**Marked, unchanged in rate, fixed in feel** (PROPOSAL): keep 4% (elites) and 10% (Wardens); add **camp hearts at 10%**;
add a **pity** (certain by the 25th elite, Warden or heart without one; per player, in `awaken:arms_memory`); make the
**first** one a guaranteed drop from the first Warden or camp heart you down (so the first one is a lesson, not luck);
give Marked drops the Lord-forged reveal in miniature (a short pillar and the same toast). At the sim's mix (5% of
overworld kills are elites) 4% is about one in 500 kills; the sim doesn't say kills an hour, so **Phase E adds a gear row
to `pacing_sim.py`** (kits, Blueprints, Marked) and the pity is set from it. UNVERIFIED: how often this fires today.

**Boss weapons** (Euan, 2026-10-27: "weapon drops from boss fights would be great", then: "I don't think EVERY boss fight should
drop a weapon. It should still feel like an achievement ... weaker ones more often is fine, but really cool ones should still
be a bit of luck so you might have to do 2-3 S rank gates to get one weapon ... a real grind to it. But again it should be
clear you can find it in these drops."). So **the cooler the weapon, the rarer**, rolled per fighter in the Lord's hoard
(like `Loot.boss` and the Power Cores, so a party each rolls their own), straight into the pack (never the Satchel), with the
Lord-forged reveal's smaller twin for a Spoils drop (a pillar of the boss's colour and the System naming it: "SPOILS · Hive
Regent's Daggers"). Two rolls on a Gate Lord kill (PROPOSAL; the Phase E sim row tunes them):

| Gate rank | Spoils: a Marked weapon of the Lord's style, two Marks | Lord-forged (unchanged) | Any weapon from one Lord | Lords per weapon |
|---|---|---|---|---|
| E | 60% | 35%, certain by the third without one | about 74% a Lord | 1.3 (simulated, with both pities) |
| D | 45% | the same | about 64% | 1.5 |
| C | 30% | the same | about 55% | 1.7 |
| B | 22% | the same | about 49% | 1.8 |
| A | 15% | the same | about 45% | 1.9 |
| S | 40% (no Lord-forged weapon exists at S until S9; then Spoils drop to 15% and the Lord-forged takes the slot) | none yet | about 40% | **2.4** |

(`tools/sim/gear_sim.py`, 40,000 fighters a rank, reading these rates from `data/awaken/arms/style_homes.json` and the Lord-forged constants:
the pities pull the mean down from the naive 1/chance. The Lord-forged weapon alone falls about every 2.1 Lords, which is Euan's "2-3 gates
to one" for the cool one; at S the 40% Spoils with the pity of six is 2.4 Lords, median 2, one fighter in ten needing five.)

So a low-rank Lord usually pays and a high-rank one is a **real grind, two or three S gates to one weapon**, as Euan said.
A **weapon pity** keeps the tail honest: certain by the sixth Lord kill without any weapon (per player, in
`awaken:arms_memory`). A broken gate's Lord rolls one extra Spoils. Other bosses (PROPOSAL): Descent guardians 30%,
Sovereigns 40%, named beasts 15% (no guaranteed first kill; the one guaranteed first is the lowest rung, a first Marked weapon
from an elite, Warden or camp heart); the final bosses wait for S9's legendary weapons.

**It must be clear where a weapon comes from** (Euan: "it should be clear you can find it in these drops"):

- **The boss wields it.** A Lord holds its own Lord-forged weapon in its hand, glinting (a creature's held weapon is only a look,
  `AwakenMob.gearStack`; `docs/combat` already has the weapon models), so you can see what you are fighting for before the fight starts.
- **The Armoury's FINDS lists every Lord you have met** with its weapon as a dark silhouette until you own it ("Hive Regent · Daggers
  · not yet", "Lord-forged 1 of 15"), and says "The next Lord is certain to carry its weapon" when the pity has come round.
- **Tooltips and JEI say it, in words, not percentages:** a Lord-forged or Spoils weapon's tooltip reads "Dropped by: the Hive Regent
  in E-rank gates. Sometimes: certain by the third Lord." JEI gets a **Boss drops** category: each boss, its weapon, and Often,
  Sometimes or Rarely (Often is half or more, Sometimes a quarter to a half, Rarely less). Names show once you have met the boss
  (the spoiler rule).

A spare weapon can be **salvaged** at any Armoury door for essence (the tier's pattern price), so drops are never clutter (optional).

**Raise (the answer to "finding versus making").** At a Forge, the Armoury's *Raise* tab takes the weapon in your hand
and lifts it to your next tier for that tier's parts (the same Fang, Parts, Binding, metal and shards the crafted version
costs, no Blueprint, no extra), **keeping its Marks, its Art, its attunement and its name**. So a Lord-forged Maul stays
yours from E to S; the crafted weapon is what you reach for when you want a particular style at your tier *now*, the found
one is what you keep. Neither is a strict upgrade of the other. A found weapon is never stronger than the crafted weapon
of its tier; its edge is the Marks and the Art (the sim already showed an item-level multiplier breaks the ordinary fight,
`docs/combat/sim-2026-10-26.md`). Lord-forged stays one point above its home rank's forged weapon and Raise keeps that gap.

### 3.8 Difficulty (question 6): generous gear, hard fights

The combat sim (`docs/combat/sim-2026-10-26.md`, 400 seeds a cell) says **gear is the floor and skill is the lever**:

- On the Drowned Archivist at D, a Parrier with the forged Longsword wins 6%; with a diamond sword 12%; an Expert wins
  99% in 43 s. The Tank (no defence) loses to every Lord in 4-21 s whatever it holds.
- An Expert kills only 7-11% faster than a Parrier on the same Lord; on-level offence per 1,000 Lord health is flat
  (10.2-11.2) from E to A.
- A tier step is one point of damage, about a tenth to a fifth of a weapon's (5.5 at E, 10.5 at S with the player's 1), so missing a tier is a handicap, not a wall.

So the first gear can be generous, and **nothing here changes how hard a fight is**. Where "not too easy" lives: the metal
run from D, the trophy hunt, the Lords (C and above need Lord parts), Marks and Arts you have to find, and the three-Lord
session still to come: the sim finds only 3 of 15 Lords inside their targets even for the Expert, and its recommended
Lord health and damage changes wait for Euan's session. **Risk:** if the Lords stay as hard as the sim finds, kits from C
slip by hours, because they need Lord parts; parts at phase changes and the part jobs soften that, and §6 decision 11 asks
Euan to confirm the stance.

## 4. Data, state, multiplayer, old saves

| Thing | Where | Saved? | Notes |
|---|---|---|---|
| Style homes, rack ranks, prices | `data/awaken/arms/style_homes.json` (schemaVersion 1; read on both sides like `archetypes.json`) | data | one row per style: its gate theme and rank, the rack rank and price, the early teacher; also the village price by rank and the plate bands |
| Source lines | `data/awaken/arms/sources.json`, written by `tools/arms/sources.py` | data | checked by `arms_sources` |
| What a player knows | `awaken:arms_memory` (now schemaVersion 3): `styles` (learned set), `issued`, `shardMisses`, `markedMisses`, `claimedGates` (the last 64 gate ids), `claimedStands` (stand ids, a long each), `claimedPeople` | yes, kept through death | per player; migration backfills `styles` from Blueprints held and styled weapons made |
| Tier cards, the issue | derived; no extra state | - | the rack and the screen read the rule |
| Armoury screen and Track | client screen; `arms_armoury` payload (open, buy, draw, raise, track); the tracked item is per player | track: attachment, saved | the server re-checks every price and rank |
| Pattern Stand | block `awaken:pattern_stand` (kinds: rack, lectern, plinth, bier), one block entity: the style and tier, no per-player state | the world | the claim is the player's memory above; a stand dims for a player who has claimed it |
| Bearer | an `AwakenMob` tag `awaken_bearer` and a held weapon (`gearStack`) | with the creature | dies with the gate; the Lord's hoard is the fallback |
| Public API | `core.api.ArmsApi` (new, small): `knows(player, style)`, `learn(player, style, source)`, `homeFor(theme)`, `placeStand(level, pos, rank, kind, seed)`, `bearer(mob, gate)` | - | gates, living, village, above, wilds and progression call this; they never reach into arms |

- **Multiplayer-safe:** every counter, memory and issue is per player; the rack and Forge are shared and never run out;
  Blueprints are items that can be traded (a guild can lend patterns, and a Forge can draw copies for a friend); a pattern
  is given per player (to each fighter on the floor, to each player who claims a stand), "a reward is granted at most once
  per claim id"; boss weapons are rolled per fighter in the hoard; Marked drops stay ground items as today.
- **Old saves:** players who already drew Blueprints keep them and are marked as knowing those styles; the issue is offered
  once to an awakened E-rank player with no styled weapon; nobody is charged for anything they already hold.
- **Public repo:** only names, numbers and rules; no secrets.
- **Seams:** `gates` (the bearer on the first floor, the Lord's hoard fallback, boss weapons, one line on the gate card), `village` (the smith's rack, the village's rank, the smithy forge door, the stand), `above` and `wilds` (stands in vaults, monoliths, labs, platforms, spires and crowns), `living` (Hale's scene, the rack placement, chain rewards, Hale's "Anything on patterns?"), `living/camp` (a camp heart as a bearer, its caption), `world/fauna/lair` (a fallen hunter's stand), `mobs` (the shard pity, boss weapons for guardians, Sovereigns and named beasts), `progression` (`RecipeUnlocks`, the new-thing clock), `compat/client/jei`, and the regions plan (`docs/regions/PLAN.md`, the level bands). Each is a few lines through `ArmsApi` or data, agreed with its owner before building.

## 5. Phases, effort, tests, risks

Effort is builder time at this project's pace (scaled from git history: Arms S7, with Marks, the Forge, Lord-forged and
mastery and their tests, ran about 4.5 hours from first to last commit on 2026-10-26; a Way Forward phase was estimated at
2-3 hours; batches of 24 to 42 forged weapons landed 20 to 40 minutes apart). Not human-team time.

| Phase | Scope | Effort | Gate |
|---|---|---|---|
| **A · Say where it comes from** | tooltips (Fang, Blueprint with its "Found in", Shard, Forge), Hide/Part/Binding words, `sources.json` and its generator, the Essence page, JEI chips and slot tooltips, the recipe-book fix, plain level road text | 2-3 h | `arms_sources`; ships on its own, no design change |
| **B · The first hour** | Hale's issue and rack (menu first, the model rack if time), the first-shard find, the shard pity, Ilse's and Marta's pay, the Now line | 2-3 h | `arms_issue`; **Euan plays the first 30 minutes** |
| **C · The Armoury** | the screen, the band section, Track, Blueprint routing (rack by rank, Forge choose-list, learned styles, migration), the Forge's empty-hand door | 3-4 h | `arms_armoury`, `arms_rack` |
| **D · Gates, camps and people teach** | the bearer on a gate's first floor and the hoard fallback, the camp heart as a bearer, `style_homes.json`, the gate-card and camp lines, the six chain rewards, the rack plaques, Hale's "Anything on patterns?", the "What waits" entries, plain dialogue | 3-4 h | `arms_gate_pattern` (the bearer drops once per fighter, the fallback pays, a known style gives another of its rank, never the Satchel), `arms_homes` |
| **E · Boss weapons, finds and Raise** | the two boss rolls per fighter by rank and the weapon pity, the reveal, the Lord wielding its weapon, the Armoury's FINDS and JEI's Boss drops, guardians, Sovereigns and named beasts, Marked pity and the guaranteed first, Raise, salvage, first-forged moments, the capability clock, the sim gear row (the rates above are set from it) | 3-4 h | `arms_boss_weapon`, `arms_raise`, `arms_marked_pity` |
| **F · Tiers announced** | part jobs on Hale's board, tier cards, foreshadow, Oren's lines, the Horizon pulls | 1.5-2 h | `arms_tier_cards` |
| **G · Villages and structures** | the smith's two patterns for emeralds and the village's rank, the smithy forge door, the Pattern Stand block and its model, placing it in vaults, labs, platforms, spires, lairs and dens | 3-5 h | `arms_village_patterns`, `arms_stand` (once per player, dims, a known style pays essence) |

**Total about 18-25 builder-hours; with three builders (A+B, then C with D and G beside it, then E and F) about a day and a half of wall
clock**, plus CI runs and Euan's play. G waits on the regions plan's level bands (`docs/regions/PLAN.md`) for the village's rank and touches
the village, above and wilds modules, so it is its own builder. **S9 named weapons** (about 30 first) are a separate later job, about
3-4 hours once the art pipeline's batch rate (24 to 42 weapons every 20 to 40 minutes) and the writing are counted. Bottlenecks that
don't compress: GameTest runs on CI, Euan's play, and the three-Lord session.

**Tests** (one per mechanism; the full suite once at hand-off, CI the rest): `arms_sources`, `arms_issue` (once per player, the
pick gives weapon, Blueprint and Fang, Ilse's fallback), `arms_rack` (stock by rank, price, refusal below rank), `arms_gate_pattern`,
`arms_homes` (a person teaches once; a known style pays its price), `arms_village_patterns` (two patterns of the village's rank, never
a known one, emerald price with standing), `arms_stand`, `arms_boss_weapon` (each fighter rolls their own, the odds fall with rank, the pity fires at six, a Lord-forged replaces the Spoils), `arms_marked_pity`, `arms_raise` (keeps Marks, Art, attunement; costs the next tier's parts), `arms_tier_cards`,
`progression_shard_pity`, `arms_recipe_unlock` (a Blueprint recipe appears with its Blueprint, not a bone).

**Risks:** Hale's yard belongs to the city and living modules (the rack needs a spot beside him, agreed with their owners); the
bearer and the boss weapon touch `gates/run`; the stand is placed by four other modules' structure code; the village's rank waits for
the regions plan; Lords being too hard slows C and up; about 80 lines of new player-facing text, each read aloud (plain speech
rule); the Armoury screen and the stand's model are the largest pieces (3D previews optional).

## 6. Decisions (numbered; Euan answered 2026-10-27)

Euan: "go ahead with everything else that's recommended". So 1-3 and 5-12 stand as recommended below, **4 changed** as he
asked, and 13-15 are his three additions, with the design written for his check.

1. **Ship the clarity pass on its own, now (Phase A).** Tooltips, plain words, source lines with a CI test, JEI (a Blueprint says
   "Found in: C-rank gates, level 25-44 structures..."), the recipe-book fix. *Approved.*
2. **A first weapon from Hale:** pick one of Daggers, Axe or Maul at the High Gate; you get the finished Wildhide weapon, its
   Blueprint and a spare Fang; Ilse hands it over if you miss him. *Approved.*
3. **The Armoury is the one door for Blueprints:** Hale's rack (essence only, stocked by the master Lord's rank, 4 to 12 essence),
   your Forge and a village smithy's (paper, lapis, essence; any pattern you know), a real rack of weapons in his yard, a choose-list
   instead of the random cycle. *Approved.*
4. **Homes: CHANGED.** The **gates give the pattern**: each gate's bearer drops its style's Blueprint on the first floor (else the Lord's
   hoard), and every gate you clear pays; a style you know gives another of that rank. **Structures** show patterns on stands built
   into the place (never a plain chest). **Villages** sell two patterns of their rank for emeralds. Six people teach early. Hale's rack
   is the floor. Each tells you at the place, and a tooltip says "found in an X-rank gate, or a level X-X structure". *Approved as
   Euan described it; the design is in §3.4.*
5. **Names and words:** keep Fang, Cleaver, Sabre, Edge, Warblade, Oathblade, add a plain type line; use Hide, Part and Binding
   everywhere. *Approved.*
6. **The Armoury page in the band, Track, and Now-line hints** with a compass to known places. *Approved.*
7. **Essence:** say where it comes from, the no-drought rule (the 5th kill with none drops one, E and D only), and Ilse's Moth Lights and
   Marta's Wheel pay shards (3 between them). *Approved.*
8. **Tiers announce themselves:** foreshadow, open, and a part job on Hale's board for each tier's signature part. *Approved.*
9. **Marked weapons:** keep 4% and 10%, camp hearts at 10%, a pity (certain by the 25th), a guaranteed first one; named weapons told by
   rumour and found at places, about 30 first, after Phase E. *Approved; the pity is set from the Phase E sim row.*
10. **Raise:** a found weapon can be raised to your tier at the Forge for that tier's parts, keeping its Marks, Art and attunement.
    *Approved.*
11. **Difficulty stance:** tiers stay one point of damage apart, no item-level multiplier, nothing here touches a Lord's numbers (those
    wait for the three-Lord session). *Approved.*
12. **Order:** A and B first, then C with D and G beside it, then E and F; named weapons wait. *Approved, with G added.*
13. **Boss weapons: rarer the cooler they are (Euan's request, then his correction: not every boss).** A Gate Lord kill rolls a Marked
    "Spoils" weapon in the Lord's style (60% at E falling to 15% at A, 40% at S) and the Lord-forged weapon (35%, certain by the third);
    that is about 74% of Lords at E and about 40% at S, **two or three S gates to one weapon**, with a pity at six. The Lord wields its
    weapon (Euan: the gate card does not show it), the Armoury names it, and tooltips and JEI say "Dropped by... Sometimes". *Design in §3.7; the rates are set
    from the Phase E sim row and Euan checks them when he plays it.*
14. **Villages sell patterns (Euan's request).** Two patterns of the village's rank, for emeralds, by its region's level; the smithy's
    forge is an Armoury door. Not finished weapons (they would skip the Part and the metal). *Design in §3.4.3; alternative if he wants it:
    the smith also sells the Parts of his land.*
15. **Structures hold patterns on stands, not in chests (Euan's request).** A Pattern Stand built into vaults, labs, platforms, spires,
    lairs and dens, claimed once per player, with the weapon of that tier hung above it and a light that says "take me". *Design in §3.4.2.*

## 7. Not verified, and left out on purpose

- UNVERIFIED (new): whether a gate's first floor always has an elite to be the bearer; which village smithies already place a Forge; which surface
  a camp has for a name (a Horizon site, a caption); the village's rank function (the regions plan's level bands); whether a gate's creatures can all
  be counted at the floor's end (the bearer's death is the trigger, not the shrine).
- UNVERIFIED: how often a Marked weapon drops in play (needs the sim row); the recipe-book toast after Phase 5 of The Way
  Forward; whether the Wayfarer chains' `reward` accepts a second item beside `levels`; the exact spot beside Hale for a rack.
- Monster Hunter's in-game Smithy wording (fan sources only); Elden Ring's exact stone tiers vary by source.
- Left out: new weapon stats, a vendor for gear beyond patterns, Lord changes, S9's weapon list, and anything that needs
  the client to launch (no tour was run for this plan).
