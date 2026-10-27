# Arms progression: builder briefs (approved 2026-10-27)

The plan is `docs/arms-progression/PLAN.md` (read it whole first; §6 is the 15 decisions Euan approved, §3 the design,
§5 the phases). Page: https://claude.ai/artifact/DA4J66cpZkKinGbm6X18ML. Euan, 2026-10-27: "go ahead with everything else that's recommended", then "go with all recommendations", with his
changes already folded in (gates give the pattern, structures hold patterns on stands, villages sell them, boss weapons
rarer the cooler they are, the gate card does not show a Lord's weapon).

## Rules for every builder (put these in the brief you start a session with)

- **Effort xhigh** for every builder session and every sub-agent (Euan's standing rule). Do the work yourself; no sub-agents
  unless a broad code search truly needs one.
- **Start from `pack/arms-progression-plan`** (it holds the plan and the repo at the plan's base), then `git fetch origin
  build/awaken-v1 && git merge origin/build/awaken-v1` before you write code. Work on your own branch
  `pack/arms-progression-<letter>`. Small commits, each ending `Co-Authored-By: Claude <model> <noreply@anthropic.com>` with
  your real model name (CLAUDE.md). Push your own branch for CI (never force, never push `build/awaken-v1`).
- Read `CLAUDE.md` first. Build only through `tools/gradle.sh <timeout> <args>`. **Never launch the game client or a tour.**
  Verify every Minecraft or NeoForge API in `docs/api/*.md` and `/Volumes/MEDIA/Awaken/.ref/{src,neo,assets}` (26.3 is not 1.21.1).
- **Tests: one GameTest per mechanism** (names in the plan), run only yours while you work
  (`tools/gradle.sh 900 runGameTestServer -Ptests=arms_`), the full suite once at hand-off; CI does the rest. No re-run loops, no
  flake hunts.
- **Plain speech.** Every line a player reads (tooltips, toasts, NPC lines, card text) is natural, complete sentences, read aloud
  before you ship it. No short poetic lines or riddles. Euan's rule, 2026-10-26.
- **Boundaries.** Own your module's package and `src/main/merge/<module>/`. The plan names the seams in other modules (each a
  few lines, through `core.api.ArmsApi` or data); make exactly those, list each in your report, and touch nothing else. If you
  decide something new, add it to `docs/SPEC.md` §25 in the same commit.
- Server authoritative; per-player state only (multiplayer-safe); versioned saves (`schemaVersion`); codecs for saved data,
  StreamCodec for sent data; no per-tick allocation in hot paths. Public repo: nothing secret.
- **Hand-back** (your final message, first 400 characters matter): branch and last commit, CI run link and result, what is done,
  anything you did differently from the plan and why, the UNVERIFIED items in PLAN §7 you resolved (and how), and anything left.
  Update `docs/modules/arms.md` and SPEC §25 for what you built.

## Order

A and B can run at once (different files). C needs A and B merged. D, E, F and G need C. D and E touch `gates/run`: run them one
after the other, or on separate files only. G also waits for the regions plan's level bands (`docs/regions/PLAN.md`, branch
`pack/regions-plan`) for a village's rank; if they haven't landed, build G on the plate bands in PLAN §3.4 (E 1-9, D 10-24, C 25-44,
B 45-69, A 70-99, S 100+) behind one function.

## A · Say where it comes from (branch `pack/arms-progression-a`, about 2-3 h)

PLAN §3.5, decisions 1, 5 and the JEI part of 3. Deliver: the Hide, Part and Binding words (lang for every `hunter_{hide,core,bind}_*`
tag, shown by JEI); tooltips (Wildhide Fang and the other five blades: a type line and "Made from"; every Blueprint: what it is and
"Found in: E-rank gates, level 1-9 structures, villages of level 1-9. Hale sells it at E-rank for 4 essence."; Essence Shard; Forge); Shift
expands an ingredient with its source line; `tools/arms/sources.py` writes `data/awaken/arms/sources.json` from every creature loot table
plus hand-written vanilla entries (creature lines name the family and region until the creature is in the Codex); a first
`data/awaken/arms/style_homes.json` (PLAN §3.4 table: each style's gate theme and rank, rack rank, early teacher, price by rank; data only,
later phases read it); JEI: a chip row on every Awaken Crafting recipe (style, tier, where the Blueprint comes from, the level road step),
source lines on ingredient slots, the Essence page listing creatures first, the **Armoury** category (Hale's prices by rank) and the
**Blueprint** category (the Forge draw); `RecipeUnlocks` unlocks a recipe containing a Blueprint on the Blueprint, not on a bone; plain-speech text for
`level_road/hunter_gear_*.json`. Seams (one change each, listed): `gear/HunterGearItem`, `progression/RecipeUnlocks`, `compat/client/jei/**`,
lang in core, gear, arms and compat. Tests: `arms_sources` (every ingredient of every Awaken gear and arms recipe has a source line),
`arms_recipe_unlock`.

## B · The first hour (branch `pack/arms-progression-b`, about 2-3 h)

PLAN §3.3, decisions 2, 7 and 12. Deliver: `core.api.ArmsApi` (new, small: `knows(player, style)`, `learn(player, style, source)`) and
`awaken:arms_memory` schemaVersion 3 (`styles`, `issued`, `shardMisses`, with a migration that backfills `styles` from Blueprints held and
styled weapons made); **Hale's issue** (the first time an awakened E-rank player comes within a few blocks of Hale with no styled weapon: his lines, the pick of
Daggers, Axe or Maul on the first power's card UI, the finished Wildhide weapon, its Blueprint and a spare Fang handed over with the Registry's `HandOver`;
Ilse hands the same issue "in Hale's name" at the camp if he was missed; the Now line "Hale has your hunter's issue" at priority 70 until taken);
**Hale's rack** as a menu first (talk to him: the five E patterns, 4 essence each, plaques that say where each is free; the model rack in his yard only if time); the
first-shard find card; the **shard pity** (the 5th kill in a row with no shard drops one; per player; E and D ranks only); Ilse's Moth Lights and Marta's Wheel pay 3
shards between them. Use the lines in PLAN §3.3 as the starting text. Seams: `living` (Hale and Ilse scenes, the two quest rewards), `mobs` (the pity), `signal`
(the find card). Tests: `arms_issue` (once per player, the pick gives weapon, Blueprint and Fang, Ilse's fallback), `arms_rack` (stock by rank, price, refusal), `progression_shard_pity`.

## C · The Armoury (after A and B, branch `pack/arms-progression-c`, about 3-4 h)

PLAN §3.2, §3.5 (Track, Now line), decisions 3 and 6. Deliver: the Armoury screen (a QuietScreen like "Your Hearth": styles you know, tier cards with have and need, buy,
draw, a choose-list instead of the random cycle), the band section K > Powers > THE ARMOURY (styles, tiers, FINDS; never lists what you haven't met), **Track** (one item at a time
on the task card, the Now line at priority 52 with a compass to a known place), the Forge's empty-hand door, Blueprint routing (Hale's rack by rank and price, the Forge's
draw for patterns you know, learned styles), payload `arms_armoury`. Tests: `arms_armoury`, `arms_rack` extended.

## D · Gates, camps and people teach (after C, branch `pack/arms-progression-d`, about 3-4 h)

PLAN §3.4 doors 1, 4 and 5, decision 4. Deliver: the **bearer** on a gate's first floor (an elite if there is one; it wields the style's weapon via `AwakenMob.gearStack`
with a faint blueprint-white glint; its death gives each fighter on the floor the pattern straight into the pack, never the Satchel; a style you know gives an unknown style
of that rank, then essence; the Lord's hoard is the fallback; once per gate per player), a riftborn camp's heart as a bearer, the gate-card and camp lines ("The first floor
carries a pattern: Daggers"), the six chain rewards in the people's own plain voice, Hale's rack plaques and "Anything on patterns?", the "What waits" entries (`core.api.Promises`).
Seams: `gates/run` (`Runs.onKill` sees each kill; the bearer's death is the trigger, not `floorCleared`), `living`, `living/camp`. Tests: `arms_gate_pattern`, `arms_homes`.

## E · Boss weapons, finds and Raise (after C; not at the same time as D unless on separate files; branch `pack/arms-progression-e`, about 3-4 h)

PLAN §3.7, decisions 9, 10 and 13. Deliver: the two boss rolls per fighter in the hoard by rank (Spoils: E 60%, D 45%, C 30%, B 22%, A 15%, S 40%; Lord-forged unchanged), the weapon pity at six,
the reveal, the Lord wielding its Lord-forged weapon (no weapon line on the gate card: Euan said no), guardians 30%, Sovereigns 40%, named beasts 15%, the Armoury's FINDS
and JEI's **Boss drops** and **Raise** categories ("Dropped by the Hive Regent in E-rank gates. Sometimes."), Marked camp hearts at 10%, the Marked pity (certain by the 25th) and the
guaranteed first, Raise at a Forge, salvage, first-forged moments for the new-thing clock, and **a gear row in `tools/sim/pacing_sim.py`** (kits, Blueprints, Marked, boss rolls) that
sets the rates. Tests: `arms_boss_weapon`, `arms_raise`, `arms_marked_pity`.

## F · Tiers announced (after C, branch `pack/arms-progression-f`, about 1.5-2 h)

PLAN §3.6, decision 8. Deliver: the foreshadow (the locked tier card and a "What waits" entry), the open toast naming the job, a **part job** on Hale's board per tier from D up
(`collect` bounties on the signature part, text naming the creature and the place, `requires` level or rank tokens, in plain speech), tier cards with have and need, Oren's rank lines,
the Horizon pulls. Test: `arms_tier_cards`.

## G · Villages and structures (after C, its own builder, branch `pack/arms-progression-g`, about 3-5 h)

PLAN §3.4 doors 2 and 3, decisions 14 and 15. Deliver: the village's rank (from its region's level), the smith's rack with two patterns of that rank for emeralds (`village/Trade.java`,
never a known pattern, standing in the price), the smithy's forge as an Armoury door (place an `awaken:forge` beside the anvil where there is none), the **Pattern Stand** block
(`awaken:pattern_stand`: rack, lectern, plinth, bier; the tier's weapon hung above, the pattern pinned in blueprint white line, a soft light and the find cue; claimed once per player;
a known style pays its essence price) with `ArmsApi.placeStand`, and its placement in vaults, monoliths, ocean labs, sky platforms, spire crowns, hero-tree crowns, lairs and dens through each
owner's structure code (agreed with `above`, `wilds`, `world/fauna/lair` and `village`). Patterns are never in a plain chest. Tests: `arms_village_patterns`, `arms_stand`.

## Not in these briefs

S9's named weapons (about 30 first), any change to Lord numbers (they wait for Euan's three-Lord session), and the model rack in Hale's yard if B ran out of time.
