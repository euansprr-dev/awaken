# The War Anvil (weapon forging progression)

Plan for Euan's 2026-11-02 request, written by the weapon-forge session on branch `claude/weapon-forge-plan-2e37fb`.
Status: **approved by Euan 2026-11-02 ("go ahead with the weapon forge plan"): all 13 decisions yes, as recommended,
including decision 7's cap and decision 13's first stage.** Stage 1 is being built (SPEC §46). Follows `.claude/PLANNING-PROCESS.md`. Labels used below:
**VERIFIED** (read in this repo at 08e5d57a), **RESEARCH** (an outside source, §2.5), **PROPOSAL** (not built, nothing
measured). Approval page: https://claude.ai/artifact/UzcPa1mNX9gL1dsAuTiAJZ (source `docs/weapon-forge/page.html`;
answers save to its `decisions` collection).

## 0. What Euan asked for, and the answer in one breath

> "The weapons system needs it's own cinematic crafting progression similar to what the armour has now." (2 Nov 2026)

**The answer.** Weapons stop being made on the crafting grid. They're forged at a new block, the **War Anvil**: a horned
black anvil on a carved stone, with a quench trough cut into its side and a lit slot where your finished weapon stands
until you draw it out. The scene is **in first person, through your own eyes, with your own hands**: tongs in the off
hand, a hammer in the main hand. You heat the blank, draw the blade out on **the style's own fighting rhythm**, quench
it, fit the hilt, then **pull it out of the stone** and raise it. Each rank adds one act (C grind, B balance, A twist,
S a starlight quench). Tempering, attunement and Raise become short scenes at the same anvil. The armour's rules for
quality, memory and patterns carry over, so the two forges feel like one game. But the place, the camera, the acts and
the sound are the weapon's own.

## 1. Understanding and success contract

### 1.1 The brief

- **The job.** Euan plays from the first blade to a Lord-forged weapon. Making and improving a weapon should be a
  moment, like forging armour now is, not a grid.
- **The pain (VERIFIED, §2).** A weapon is made in half a second at a crafting table, with its Blueprint laid in the
  grid. Tempering, attuning and raising are each a click in a notice window or a button in the Armoury. The Tasks page
  has a Weapons tab that no module fills with steps.
- **The desired experience.** The weapon's own station, scene, sound and hands. As good as the armour, and not a copy
  of it.
- **His proposed solution, read as intent.** "Similar to what the armour has now" means the same bar (cinematic,
  played, quality worth chasing, a place in the Hearth). It does not mean the same scene: the brief asks for weapons'
  own.
- **Hard rules kept.** The gates give the pattern and the world gives the materials. A pattern is never in a plain
  chest. Boss weapons get rarer the cooler they are. Hale's issue stays. Mastery, Marks and weaves stay. Costs stay as
  they are. No change to combat rules (boss-fights owns those) or to enchanting (the enchanting session owns it).
- **Unknowns.** How the hands and tools read on screen, and whether the heat reads as heat, can only be seen in the
  game. Stage 1 is built to answer exactly that in CI tour shots before anything else is built (§7).

### 1.2 What "good" looks like

| | Strong | Weak | Unacceptable |
|---|---|---|---|
| Look | the glowing blade lengthening under your own hammer, sparks off the strike, the quench's steam, the draw from the stone; you'd screenshot it | a menu with a progress bar and an anvil sound | vanilla particles, the hands hidden, a copy of the armour scene |
| Feel | 25-45 s the first time, learnable, fun the 20th time; a known weapon in about 8 s | a long chore every time | a forge you can fail, or one you must replay to fix a mistake |
| Fit | patterns, metals, tiers, tempering, attunement, Raise and the Hearth all meet at one block | a new block beside the old menus | a second way to make the same thing |
| Clarity | the anvil and the Tasks page say exactly what's missing and where it comes from | you have to guess why the forge won't start | stuck |

### 1.3 Non-goals

- No new weapons, styles, damage numbers or combat moves. The first cut at the end of the scene deals no damage.
- No change to drop rates: Lord-forged (35%, certain by the third Lord), Spoils, Marked and their pities stay.
- Enchanting is not part of this plan. It reads how well an item was forged through `core.api.ForgeHooks` (§4.9).
- Armour is not changed, except one line: the War Anvil counts as the Fitting Stand's anvil (decision 11).
- The Hunter Focus and Mattock are not weapons. They stay on the grid.
- No mixins and no Turbo feature.

### 1.4 How we will know it worked

| Goal | Recognised by |
|---|---|
| Looks epic, its own | Euan's eye on stage 1's CI tour shots (each beat of one forged weapon) before stage 2 is built |
| Fun, not a chore | a weapon takes 25-45 s; from memory about 8 s; nothing fails; Euan forges three in game and wants a fourth |
| Never stuck | `arms_anvil_goals`: every missing piece is named with where it comes from; the forge never starts without it |
| No regression | `arms_anvil_costs`: every weapon costs exactly what its grid recipe cost, minus the Blueprint |
| Multiplayer-safe | the server judges every strike by its own clock (as the armour's does); the work lives on the block |

## 2. Verified audit and diagnosis

### 2.1 How a player gets and improves a weapon today (VERIFIED)

| Step | What happens | Where | Feels |
|---|---|---|---|
| First weapon | Hale's issue: pick Daggers, Axe or Maul; you get the Wildhide weapon, its Blueprint and a spare Fang | `arms/ArmsIssue.java` | good |
| Learn a style | a gate's bearer, a Pattern Stand, a village smith, six people, or Hale's rack | `ArmsPatterns`, `ArmsStands`, `ArmsHomes` | good, and stays |
| See what it needs | the Armoury lists tiers and needs, with Track | `ArmsArmoury` (needs read from the recipe JSON) | good |
| Make the blade | the tier's blade (Fang, Cleaver, Sabre, Edge, Warblade, Oathblade) on the grid from Parts, Binding, metal and a shard | `data/awaken/recipe/<tier>_blade.json` | **flat** |
| Make the weapon | the blade, a Part, a Binding or metal, and the style's Blueprint in the grid, in the style's shape; the Blueprint comes back | 96 files `data/awaken/recipe/<tier>_<style>.json` | **flat: half a second at a crafting table** |
| Temper | hold it, use the Forge: a notice window, "Temper it to II", iron, gold, diamond, essence and coal; +1 damage a level, to VI; the cap is the Forge's tier x2 in an Armoury | `hearth/Upgrades.java` | **flat: a click** |
| Attune | sneak-use the Forge with a catalyst in the off hand: a notice window, 6 essence, a Core Shard, the catalyst | `arms/ArmsForge.java` | **flat: a click** |
| Raise a found weapon | the Armoury's Raise button at a Forge: the next tier's parts, Marks and name kept | `arms/ArmsRaise.java` | **flat: a button** |
| Lord-forged | 35% a Lord kill, certain by the third: a pillar, a ring, a toast, the weapon in your hand | `arms/LordForged.java` | good, and stays |
| Mastery | five steps a style from kills | `ArmsMastery` | good, and stays |
| Weapons on the Tasks page | the tab exists, but only Now suggestions and trails land there; no module registers weapon steps | `tasks/client/TaskCache.java:21`, `tasks/NowMap.java:43` | **empty** |

### 2.2 The armour, the bar to match (VERIFIED, SPEC §44)

The Fitting Stand and the Forge Song: heat in the Forge, shape on the set's rhythm aiming at marks, quench on a bell
cue, fit; E by hand (cut, rivets). Each rank adds a verb (C Inlay, B Tune, A Fold, S Starfire). Quality Plain, Fine,
Masterwork or Flawless sets the starting Temper (0-3). Masterwork puts a piece in memory (one heat, one strike, always
Fine). A helper strikes the off-beats (twin-struck). The server judges by its own clock. The camera is cinematic and
**hides your hands** (`armour/client/ForgeCamera.java`: "Your hands, the block outline and your own body are hidden").

### 2.3 Why weapons feel flat next to it (root causes)

| Symptom | Cause | Evidence | Fix |
|---|---|---|---|
| making a weapon is nothing | it's a grid recipe | the 96 recipe files; the Blueprint is a grid ingredient that comes back | forge it at the War Anvil (§4) |
| improving it is nothing | tempering, attuning and raising are notice windows and a button | `Upgrades.offerTemper`, `ArmsForge.offerAttunement`, `ArmsArmoury` RAISE | short scenes at the same anvil (§4.6) |
| weapons have no place at home | the Armoury room is built around the Fitting Stand | SPEC 27.13; `Furniture` has no weapon station | the War Anvil belongs in the Armoury (§4.8) |
| the Weapons tab is empty | no `Goals` steps for weapons | `TaskCache.TABS` lists WEAPONS; `NowMap` maps only `arms:` and `gear:` suggestions | `arms.anvil.WeaponGoals` (§4.8) |

### 2.4 Found while auditing, not changed here

- **Tempering outweighs tiers.** Temper is +1 damage a level, to VI. A tier is +1 damage. A Wildhide Longsword at Temper
  VI (4.5 + 6) hits harder than an untempered Sovereign one (9.5). The forge's quality adds starting Temper, so this plan
  caps that at the Forge's temper limit (§4.5): skill saves you the materials, but never lifts a weapon past what you
  could buy. The numbers themselves are Euan's call for another day.
- The Armoury reads a weapon's needs from its recipe JSON (`ArmsArmoury.raw`), and Raise reads them through it. Both
  move to the new cost table (§5.1).

### 2.5 Research (RESEARCH; what it changes here)

| Claim | Source (fetched 2026-11-02) | What it means here |
|---|---|---|
| Kingdom Come: Deliverance II's smithing makes a forged blade feel earned beyond combat | [Best Buy Canada review](https://blog.bestbuy.ca/video-games/kingdom-come-deliverance-ii-review) | the forged moment is worth building |
| Some players find the same smithing a chore. They ask to remove it, or use a mod that stops mistakes. A DLC reviewer would stop forging once their forge was fully upgraded | [Steam thread](https://steamcommunity.com/app/1771300/discussions/0/601894230866015292/?ctp=3), [Steam thread](https://steamcommunity.com/app/1771300/discussions/0/592890434797215686), [Fingerguns DLC review](https://fingerguns.net/reviews/2025/09/16/kingdom-come-deliverance-ii-legacy-of-the-forge-dlc-review-ps5-more-than-a-forgery/) (player posts; weak evidence) | never fail a forge; from memory as the fast path; short acts; a new act each rank so it doesn't go stale |
| Mabinogi sets an item's quality from how well the smithing minigame went; Jacksmith ties weak spots to hammer timing | [Mabinogi wiki](https://wiki.mabinogiworld.com/view/Blacksmith), [Jacksmith write-up](https://www.seeles.ai/games/simulation/jacksmith-the-fantasy-blacksmith-adventure) | skill sets quality, never success (as the armour already does) |

Nothing outside says how to film hands at an anvil in Minecraft. That's what stage 1's shots are for.

## 3. Alternatives and the recommendation

### 3.1 The station

| Way | Gives | Why not / why |
|---|---|---|
| A. Reuse the Fitting Stand, Forge and anvil | nothing to build | it's the armour's; weapons would feel like an add-on to it |
| B. A weapon rack (display) with the work done on a vanilla anvil | a display | the vanilla anvil costs 31 iron and reads as vanilla; the rack isn't where anything happens |
| **C. The War Anvil** (recommended) | one hero block where weapons are made, tempered, attuned and reforged, and where a finished one stands in the stone | its own look and its own reveal (the draw); cheap at E; doubles as the armour's anvil |

### 3.2 The scene

| Way | First time | 20th time | Distinct from armour? |
|---|---|---|---|
| The armour's camera and acts with weapon meshes | yes | yes | no: a copy |
| A long realistic smithing sim | yes | no: a chore (§2.5) | yes |
| **First person with your hands; the style's own rhythm; acts armour doesn't have** (recommended) | yes | yes, with from memory | yes |

### 3.3 Where Lord-forged weapons come in

| Way | Why not / why |
|---|---|
| Make Lord-forged weapons forgeable with a Lord's Heart | breaks "rarer the cooler they are" |
| The Lord's weapon drops broken and is woken at your anvil | delays the best reward in the game and takes away its reveal |
| **Keep the drop and its reveal; the anvil is where it's reforged** (recommended) | the reveal stays where Euan liked it; at home, Reforge keeps it up with your rank, in its Lord's colour |

## 4. Behaviour

### 4.1 The War Anvil (`awaken:war_anvil`, module `arms`)

- **The look** (bold, first instinct; our voxel style like the weapons). A soot-black iron anvil with a long horn,
  sitting on a block of carved dark stone. On the stone's front is a narrow slot lit dull orange from inside. A finished
  weapon stands in it hilt up, glowing in its own light, until it's drawn. A water trough is cut into the stone's side;
  it steams while the work is hot. A hammer and tongs hang from an iron peg on the side, and they're gone from the peg
  while they're in your hands. At night the slot and the trough's steam light the room.
- **Recipe** (the only grid step): three stone, a copper ingot and a log. It's granted at level 3 with the Fitting
  Stand's recipe, so it needs no iron.
- **What it needs near it.** E weapons are knapped by hand on the anvil itself. D and up need the Hearth's Forge within
  6 blocks (the armour's reach, so one smithy serves both). C and up also need a grindstone within 6 blocks. The list
  says plainly what's missing, and nothing starts without it.
- **Use it** (empty hand): the anvil's list. **Use it holding a weapon**: what can be done to that weapon (Temper,
  Attune, Reforge). **Sneak-use**: draw out what stands in the slot.
- **It counts as an anvil** for the Fitting Stand (decision 11), so a player needs one anvil, not two.

### 4.2 The list

The Armoury's own list, with one more door. Styles you know, their six tiers, have and need for each, where each need
comes from, and Track. At a War Anvil, a tier you can make has **Forge it**, and one in memory also has **From memory**.
Nothing new to learn: it's the screen the player already uses at Hale's rack and the Forge.

### 4.3 The scene, D and up (through your eyes)

The view stays at your eyes and turns to frame the work. When the work moves to the Forge or the grindstone, the view
glides there and back (a client camera; the player never moves, as SPEC §24 asks). The HUD steps aside. **Your hands
stay**: SPEC §24's clean vanilla fists, the off hand holding the tongs and the main hand the hammer, both drawn as our
own voxel tools. Others see your body at the anvil, swinging the hammer in third person.

| Act | You do | You see and hear | Scored |
|---|---|---|---|
| **Heat** | the blank goes into the Forge in your tongs; press use to pull it out | the glow runs from the tip toward your tongs, dull red to yellow; the bellows breathe | pull when the glow reaches the tongs. Early, strikes count a little less. Late, the tip spits sparks and comes out **burnt** (a flaw: never Flawless) |
| **Draw out** | strike (attack) on the beat, aiming at the glowing mark | the hammer comes down in your hand; sparks; **the blade lengthens strike by strike**, from a squat glowing bar to the weapon's shape. Halfway the tongs flip it over | each strike: timing on the style's beat, and aim on the mark (the armour's 0.6 / 0.4). 8-14 strikes by weapon |
| **Quench** | hold use to plunge it in the trough; let go | steam roars and falls to a hiss; a dark line climbs the blade from the point; a small gold notch near the guard | let go when the line reaches the notch. Early, it's soft (lower score). Past it, **brittle** (a flaw) |
| **Hilt** | three taps (attack) on the beat | the guard slides on, the grip is wrapped, the pommel is peened | timing only |
| **The draw** | stand it in the stone, then hold use to pull it out | a long scrape of steel on stone that rises into the blade's note; you raise it before your eyes; its sheen runs hilt to tip and its own light wakes; the card: "Beastbound Longsword · Masterwork · Forged by Euan" | not scored |
| **The first cut** | strike once (optional) | the style's first strike. With a Target Dummy within 6 blocks, the view turns to it and the dummy rocks; otherwise it cuts the air | not scored |

**Every style forges to its own fighting rhythm.** The hammer keeps the beat of the style's chain, so the anvil teaches
you the weapon's tempo before you fight with it. For example (a step is 3-6 ticks; written into
`data/awaken/arms/anvil_songs.json` from each style's timing row):

```
Daggers    3 ticks  x.x.x.xx            four quick cuts, like its chain
Longsword  4 ticks  x...x...x.x.....    two, then the finisher's pair
Maul       6 ticks  x.......x.......    slow; every blow lands a quake
Rapier     3 ticks  x..x.x..x..x.x..    thrust, and the counter
Katana     4 ticks  x.......xx.x....    the draw from the sheath, then the cuts
Gauntlets  3 ticks  x.x.x.x.xx......    strings of punches
```

Pairs (Daggers, Gauntlets, Twinblades' two ends) are worked as one piece, the tongs switching between them at the flip.

**Its own sound: steel that rings truer.** The armour's sound is a bell melody, a note per strike. A weapon's is the
blade's own ring: each clean strike rings the blade a step nearer **the style's true note** (a Maul low, Daggers high),
and the draw from the stone rings it whole. The parts, from CC0 recordings and synthesis like the Arms kit: a strike for
each metal (thinner and brighter than the armour's plate), the ring, the flip, the quench's roar, the peening taps, the
stone's scrape, knapping clicks, the cord, the wheel, the twist's groan, the Deepglass hum and the starlight. Euan judges
them on a listening page (stage 5).

### 4.4 Each rank adds one act

| Rank | Act | What you do | Why this one |
|---|---|---|---|
| **E** | **Knap and bind**, by hand on the anvil, no Forge | strike the flake points along the bone or flint edge (aim, at your own pace), then hold use and draw circles to wind the cord round the grip, evenly; then the draw | E weapons are bone, flint and hide (SPEC §28.0.7). About 20 s |
| **D** | the base song | heat, draw out, quench, hilt, the draw | iron |
| **C** | **Grind**, at a grindstone | after the quench the view glides to the wheel; hold use to turn it and keep the edge on the line as it drifts, one pass an edge; sparks shower toward you | diamond grit; the edge is set |
| **B** | **Balance**, on the anvil's horn | the blade rests across the horn and tips; slide the Deepglass pommel weight with the mouse until it settles level and holds | B's part is Deepglass: it becomes the pommel weight, and it hums pure when the blade is true |
| **A** | **Twist**, before the draw out | the netherite bar is clamped in the anvil; hold use and turn the mouse in circles: three even turns | a well-twisted A blade shows the twist's ripples in its steel (a shader pattern; cosmetic only) |
| **S** | **Starlight**, instead of water | a column of white light comes down onto the anvil; hold the blade up into it and press as the light's ring closes on the point | starmetal fell from the sky; it's quenched in light. The blade drinks it, and its runes wake |

Each act done is a tenth of the quality (the armour's rule: score x (1 - 0.1 an act) + 0.1 x each act's mark). A scene
at S takes about 45 s. None of them can fail.

### 4.5 Quality, memory, flaws, helpers (the armour's rules, so both forges agree)

- **Quality** at the anvil: 0.2 heat + 0.55 strikes (early heat x0.7-1) + 0.15 quench + 0.1 hilt, then the acts. By
  hand: 0.6 knap + 0.4 bind. Plain below 0.55, Fine, Masterwork from 0.75, Flawless from 0.9.
- **Starting Temper** Plain 0, Fine I, Masterwork II, Flawless III, **capped at your Forge's temper limit** (§2.4).
  Oren's Mark (`awaken:temper_start`) adds its levels on top, to the same cap.
- **From memory.** A weapon forged at Masterwork or better (style and tier) can be forged from memory: one heat, one
  strike, the quench, the draw; about 8 s; always Fine. Rank acts aren't played from memory.
- **Flaws** show on the weapon: a burnt tip is darker, a brittle blade shows a hairline. Cosmetic, and never Flawless.
- **A helper** (anyone else using the anvil while it's being drawn out) strikes the off-beats: each adds 0.01, 0.05 at
  most, and three make it **twin-struck**.
- **A weapon's story** (`awaken:weapon_forged`): quality, flaw, twin-struck, who made it, which acts were done well
  (ground true, balanced, twisted, starlit). The tooltip says it plainly: "Masterwork, twin-struck. Forged by Euan." A
  weapon made before the War Anvil gains a Plain story the first time it's held.

### 4.6 Improving a weapon at the same anvil

Use the War Anvil holding a weapon:

| | Today | At the War Anvil | Cost |
|---|---|---|---|
| **Temper** | a notice window at the Forge | **one heat to the cap**: reheat, one strike on the beat for each level gained, quench (about 10 s whatever the levels) | unchanged (`Upgrades.temper`: iron, gold, diamond, essence, coal); the cap is still the Forge's tier x2 in an Armoury |
| **Attune** | sneak-use the Forge with a catalyst in the off hand | **set the stone**: the catalyst goes into the guard while the steel is hot; three strikes drive it home; the blade takes its Force's colour (about 8 s) | unchanged (6 essence, a Core Shard, the catalyst) |
| **Reforge** (Raise) | the Armoury's button | **reforge**: heat the whole weapon, the next tier's metal is folded in on its beat, quench, draw (about 20 s). Marks, Art, attunement and name are kept. A Lord-forged weapon reforges in its Lord's colour, its sigil glowing in the steel | unchanged (the next tier's parts, no Blueprint) |

The Forge's old paths for **weapons** point here in plain words ("Weapons are tempered at a War Anvil."). Armour still
tempers at the Forge. Oren still tempers anything for you when you ask him (he brings the essence).

### 4.7 Patterns, materials, tiers, weaves (what stays, what connects)

- **Patterns**: the same five doors (gates, stands, villages, people, Hale). The anvil asks only that you know the
  style. A Blueprint item teaches its style when used, and is otherwise a copy to give a friend (the Forge still draws
  copies). It no longer goes into anything.
- **Materials**: costs are exactly today's recipes, minus the Blueprint: the tier's blade parts and the style's parts.
  A tier blade you carry (Fang, Cleaver...) stands in for the blade's parts, so Hale's spare Fang and old saves' blades
  are still worth something. The tier blades are forged at the anvil too.
- **Tiers** open at the same levels (3, 13, 22, 42, 67, 97). Each tier's metal is what you hammer: hide and bone, iron,
  diamond, Deepglass, netherite, starmetal. The strike sound and the blank's look follow the metal.
- **Weaves and powers**: attunement is set at the anvil, so the forge is where a weapon is tied to your powers (its Marks
  take the Force, and it primes both ways). The result card says what it'll do with your powers.
- **Lord-forged and Spoils**: drops unchanged (§3.3). The anvil reforges them.

### 4.8 The Hearth and the Tasks page

- **The Armoury room** (`RoomType.FITTING`) is where both forges live. A War Anvil there is the room's weapon half
  (`Furniture.WAR_ANVIL`, appended). Its gift matches the armour's: a weapon finished at a War Anvil in your Armoury
  lasts a quarter longer.
- **The Weapons tab gets steps** (`arms.anvil.WeaponGoals`, through `core.api.Goals`): "Set up a War Anvil in your
  Armoury" once you know a style beyond your issued one; "Forge your first weapon" (its choices: the styles you know,
  each with what it still takes); "Make your D-rank weapon" when a tier opens; "Temper it" as an optional step when your
  Forge allows more. A done step never shows (SPEC 27.12).
- **The Now line** says "You can forge Beastbound Daggers at your War Anvil" (it replaces GearNow's "at a crafting
  table" for blades).
- **Words**: the level road's E step says weapons are made at a War Anvil; Hale adds one line to his issue ("When you
  want to make your own, set up a War Anvil at home.").

### 4.9 The line with enchanting (agreed with the enchanting session, local_888f6d4b, 2026-11-02)

**The line.** This plan owns the metal: weapon crafting, forging at the War Anvil, tiers, quality, tempering, Reforge
(Raise), attunement to a Force, Blueprints and Marks. The enchanting plan (`dev.awaken.enchant`, its own station and
scene) owns what is written into an item after it exists, on weapons and armour alike, found and Lord-forged included.
Enchanting adds no flat damage or armour (tempering and tier), no element (attunement and inlay) and no fight-moment
triggers (Marks). Tempering keeps its numbers (§4.6), so enchanting stays clear of raw damage and armour numbers.

**No enchanting step inside the forge scene.** Both sessions agreed it keeps the scene focused.

**The hook, `core.api.ForgeHooks`** (one small class, named the same in both plans):

| Call | What it does | Who |
|---|---|---|
| `quality(ItemStack)` → `OptionalInt` 0-3 | how well an item was forged (Plain, Fine, Masterwork, Flawless); empty if it never was. Enchanting reads it for an item's room for enchantments | arms registers a reader for `awaken:weapon_forged`, armour one for `awaken:forged` (one line in armour, reported) |
| `rank(ItemStack)` → `"E".."S"` or null | the item's tier | the same readers |
| `forged(player, stack, quality, station)` | an event after every forge and reforge | arms (and armour, if it wants) fires it; enchanting may listen |
| `cardLine(...)` (optional) | one line enchanting may add under the forge's result card, e.g. "Room for 3 inscriptions"; nothing shows if it registers none | enchanting decides whether to use it |

It lands in stage 2 with quality; until then `quality` is empty. Neither module reads or writes the other's component.

## 5. Architecture, data and lifecycle

### 5.1 Code (module `arms`, package `dev.awaken.arms.anvil`, client `arms.anvil.client`)

| Piece | What it does |
|---|---|
| `WarAnvilBlock`, `WarAnvilBlockEntity` | the block; holds the work and the weapon in the slot; synced to everyone near (stage, heat, how far drawn out) |
| `AnvilSong` (common) | stages, the 16 rhythms and notes, strike counts and marks, scoring, quality: the same on both sides |
| `WarAnvil` (server) | the list's Forge-it door, start (materials into the work), acts judged by the server's clock (the armour's lag clamp: 10 ticks behind, 2 ahead), finish (story, Temper, the slot) |
| `AnvilWork` | the saved work (schemaVersion 1): kind (forge, temper, attune, reforge), style, tier, stage, scores, materials, smith, the weapon being worked |
| `AnvilCosts` | reads `data/awaken/arms/anvil_costs.json`, written by `tools/art/specs/arms_weapons.py costs` from the same source the recipe writer used, so the numbers can't drift |
| `WeaponForged` | the item component `awaken:weapon_forged` (schemaVersion 1) |
| `WeaponGoals`, `AnvilNow` | the Tasks steps and the Now line |
| `core.api.ForgeHooks` | the read hook for enchanting (§4.9): `quality`, `rank`, `forged`, the optional `cardLine` |
| client: `WarAnvilRenderer` | the anvil, the work on it (the weapon's own mesh, squashed and stretched as it's drawn out, glowing by its heat), the weapon in the slot |
| client: `AnvilScene` | the first-person framing and glide (built on the armour's camera entity and projection; §5.4) |
| client: `AnvilHands` | a SPEC §24 world source (interactions, 200): fists placed on the tools; the hammer's arc keyed to the beat; tongs follow the work |
| client: `AnvilSongScreen` | the mark on the metal with its closing ring, a thin beat lane, the result card. Doesn't pause the game |
| art | `tools/art/specs/war_anvil.py` (the block, hammer, tongs, hammerstone, in the weapons' voxel style); the weapon shader `arms_weapon` gains a heat mode (dull red to white by its "along" coordinate) and the A ripples, mirrored in `tools/art/mesh_render.py` |
| sound | `tools/art/specs/audio_anvil.py`, events `awaken:anvil.*` in `src/main/merge/arms/` |

### 5.2 State and old saves

| State | Where | Saved |
|---|---|---|
| the work, and the weapon standing in the slot | the War Anvil's block entity | yes; breaking the anvil gives back the materials, the work's weapon and what stands in the slot |
| a weapon's story | `awaken:weapon_forged` on the item | yes, synced |
| in memory, first forged | `ArmsProgress` schemaVersion 1 → 2 (`masterwork`: "style:tier"; `forged`: styles forged at an anvil) | yes, kept through death; a v1 save reads with both empty |

- **Old worlds**: every weapon already made keeps working, and gains a Plain story when first held. Blueprints held
  still teach. The grid recipes are removed, so their recipe-book entries vanish. Nothing is charged twice.
- **The work only moves while its smith is at it** (the armour's rule): Esc, logging out, dying or walking 12 blocks away
  holds it; coming back restarts the stage (counted in again).
- **A finished weapon stands in the slot** until someone draws it; the smith's scene draws it at once. If the smith left
  before the draw, anyone at the anvil can draw it.

### 5.3 Multiplayer

The server judges every act. Others see the glowing blade on the anvil, the sparks and steam, and the smith's body
hammering; they hear the server's copy of each sound (the smith hears their own on the frame it happens). One smith at a
time; others can help on the off-beats. Every memory and story is per player and per item.

### 5.4 Shared code with the armour

The armour's forge camera (`armour/client/ForgeCamera.java`) is package-private and hides the hands. The builder lifts
its camera entity and world-to-screen projection into a small shared class that both forges use, with the armour's
behaviour unchanged and checked by its own test. If that's risky, the builder copies it instead. One line in
`ArmourForge.anvil` accepts the War Anvil (decision 11), and one registers armour's `awaken:forged` reader with
`core.api.ForgeHooks` (§4.9). All are reported at hand-off. Hearth changes are small seams:
`Furniture.WAR_ANVIL` appended, and the Forge's weapon paths pointing to the anvil.

### 5.5 Performance

The scene runs on the smith's client only. The anvil syncs its work at stage changes, not every tick. No allocations per
frame in the renderer or the hands. Sparks and steam come from the shared effects pool and its budget. The weapon
shader's heat mode costs a branch only on the work's mesh.

## 6. Concrete walkthroughs (PROPOSAL; same inputs before and after)

**1. First weapon of your own** (an E Daggers pattern from the first gate's bearer, about hour 1).
Before: craft a Fang at a crafting table, then lay the Fang, a Part, a Binding and the Blueprint in the Daggers' shape:
done in a second. After: the Weapons tab says "Set up a War Anvil in your Armoury" (three stone, a copper ingot, a log).
At the anvil you knap eight flakes off a bone edge and wind the cord, then pull the Daggers out of the stone and raise
them: about 20 s. They're Fine, Temper I, "Forged by Euan".

**2. Euan's case: a D weapon** (level 13, iron in hand). Before: the grid. After: the full scene (Heat, Draw out on the
Longsword's beat, Quench, Hilt, the draw, the first cut at your dummy): about 30 s. The next time it's from memory if it
came out Masterwork.

**3. A found weapon** (a C Lord's Spoils, a Marked Glaive, carried into B). Before: a Raise button, then six temper
clicks. After: Reforge at the anvil (heat, the Deepglass folded in on the Glaive's beat, quench, draw: about 20 s, Marks
kept), then one heat to temper it to the cap.

**4. Something missing** (D, but the Forge is in another room). Before: nothing; the grid didn't care. After: the list
says "Metal is heated in a Forge. Set your Hearth's Forge within six blocks of this anvil." The Weapons step says it too.
Nothing is taken until you start.

**5. A Lord's weapon** (the Hive Regent's Daggers drop at E: the pillar and toast as today). At D you reforge it at
home, in the Hive Regent's colour, its sigil glowing in the steel, and it keeps its Royal Dive.

## 7. Stages (staged, each handed back separately; effort at this project's pace)

| Stage | Scope | Effort | Gate |
|---|---|---|---|
| **1 · The first weapon at the War Anvil** | the block (model, renderer, recipe, ledger), the D scene for **one weapon (the Beastbound Longsword)** with your hands, hammer and tongs, the weapon shader's heat and draw-out, sparks and steam, sounds from the forge kit for now, `arms_anvil_song`, a CI tour shooting every beat | 4-6 h | **Euan looks at the shots** and says yes or what to change, before stage 2 |
| **2 · Every weapon at the anvil** | the cost table and the grid recipes removed; the 16 rhythms; E knap and bind; quality, memory, flaws, helpers; the story; `core.api.ForgeHooks` (§4.9); the list's door; patterns taught on use; the Hearth room, Tasks steps, Now line, Hale's and the level road's words; JEI's War Anvil page; ledger rows; old saves; the smith's third-person pose | 5-7 h | CI green; one tour (E and three styles) |
| **3 · Temper, attune, reforge** | the three short scenes; the Forge's weapon paths point here; Lord-forged reforged in its colour | 3-4 h | CI green |
| **4 · The ranks' acts** | grind, balance, twist (with the ripples), starlight | 4-5 h | CI green; one tour |
| **5 · Sound and finish** | the anvil's own sound kit and a listening page for Euan; enchanting's card line if it wants one; reduced motion (glides become cuts); pacing check | 3-4 h | Euan's ear; the full suite on CI |

About 19-26 builder hours in all. One builder, stage by stage (rule: no sub-agents). It doesn't split well: every stage
touches the same scene.

**Tests** (one per mechanism, CI runs the suite): `arms_anvil_song` (a D forge judged by the server's clock: early, gold
and late heat; strikes on and off the beat; the quench; quality and the capped Temper; materials taken, and given back
when the anvil breaks), `arms_anvil_costs` (every weapon costs its old recipe minus the Blueprint; a carried blade stands
in; no weapon grid recipe left), `arms_anvil_hand` (E knap and bind), `arms_anvil_memory`, `arms_anvil_temper` (one heat
to the cap; Oren's Mark), `arms_anvil_attune`, `arms_anvil_reforge` (Marks, Art, name and attunement kept; Lord-forged +1),
`arms_anvil_verbs` (the four acts and the tenth rule), `arms_anvil_goals` (the Weapons steps; a done one never shows),
`journey_ledger`. Retired or updated: `arms_recipe_unlock` (no weapon recipes), `arms_armoury` and `arms_raise` (needs
from the cost table).

**Rollback.** Stage 1 adds a block and touches no existing path. Stage 2 is the switch (the grid recipes go): if it has
to be undone, the recipes come back from the art tool's writer in one command, and the anvil keeps working beside them.

## 8. Failure register

| Case | What happens |
|---|---|
| the Forge or grindstone is removed mid-work | the work waits at that stage and says what's gone; nothing is lost |
| the anvil is broken mid-work | materials, the work's weapon and whatever stands in the slot drop for the smith |
| the smith logs out, dies or walks away | the work holds; coming back counts the stage in again |
| lag | the server judges by its own clock within 10 ticks behind and 2 ahead (the armour's clamp) |
| a full pack at the draw | the weapon goes to the hand, what was there to the pack, else at your feet |
| two people at one anvil | one smith; others help on the off-beats; a second start is refused in plain words |
| an old weapon, an old Blueprint, an old Fang | all still work (§5.2) |
| F1, or a shader pack that can't build the weapon shader | the scene still runs; the work draws through vanilla's entity light without the heat glow |
| reduced motion | the glides become cuts and the hands' sway halves |

## 9. Decisions for Euan

1. **Weapons are never made on the crafting grid.** All 96 weapons and the six tier blades are forged at a War Anvil.
   Costs stay exactly as today, minus the Blueprint. *Recommended: yes.*
2. **The War Anvil**: a horned black anvil on a carved stone with a quench trough and a lit slot where your finished
   weapon stands until you draw it out. Three stone, a copper ingot and a log, at level 3. *Recommended: yes.*
3. **First person with your hands**: your eyes, the tongs in your off hand and the hammer in your main hand, the view
   gliding to the Forge and back. The armour keeps its own cinematic camera. *Recommended: yes.*
4. **Each style forges to its own fighting rhythm, and rings toward its own note**: 16 rhythms, the anvil teaching each
   weapon's tempo. *Recommended: yes.*
5. **The scene**: heat (pull when the glow reaches the tongs), draw out (the blade lengthens strike by strike), quench
   (let go at the notch), hilt (three taps), the draw from the stone, and an optional first cut at a Target Dummy.
   *Recommended: yes.*
6. **Each rank adds an act**: E knap and bind by hand (no Forge), C grind at a grindstone, B balance with the Deepglass
   pommel, A twist (the ripples show in the steel), S quenched in starlight. *Recommended: yes.*
7. **The armour's quality rules**: Plain to Flawless sets starting Temper 0-3, **capped at your Forge's limit**; from
   memory once Masterwork; flaws show; helpers make it twin-struck. *Recommended: yes.*
8. **Temper, attune and Raise move to the anvil** as short scenes (Raise is called Reforge there). Prices unchanged.
   Armour still tempers at the Forge, and Oren still tempers for you. *Recommended: yes.*
9. **Lord-forged and boss weapons stay drops**, as rare as now, with their reveal. The anvil reforges them in their
   Lord's colour. *Recommended: yes.*
10. **The Hearth and Tasks**: the War Anvil belongs in the Armoury room (a weapon made there lasts a quarter longer), and
    the Weapons tab gets real steps. *Recommended: yes.*
11. **The War Anvil counts as the anvil for the Fitting Stand**, so a home needs one anvil, not a 31-iron vanilla one as
    well. One line in the armour code. *Recommended: yes.*
12. **The line with enchanting**: no enchanting step inside the forge scene. Enchanting reads how well a weapon or armour
    piece was forged (and its rank) through `core.api.ForgeHooks`, so forging well gives more room for enchantments, and
    it may add one line to the forge's result card. *Recommended: yes, as agreed with that session.*
13. **Stages in order**, starting with one Beastbound Longsword forged at the anvil and shown to you in CI tour shots
    before anything else is built. *Recommended: yes.*
