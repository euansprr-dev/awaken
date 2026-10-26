# The Way Forward: progression and guidance (plan for Euan's approval, 2026-10-26)

Lead designer's plan, built from a code audit of the build Euan plays (`build/awaken-v1` 84bea58c), the pacing sims and
AAA research. Approval page: https://claude.ai/artifact/RjzQ2SNdst8vbXPrZrvx1P (source `docs/progression/page.html`).
Nothing below section 1 is built. Each phase waits for Euan's yes.

Supporting files (all in this folder):
- `audit-journey.md`: the first hour minute by minute, and every guidance channel.
- `audit-spine.md`: every unlock with its condition in code, the time curve from `tools/sim/pacing_sim.py`, stalls and floods.
- `audit-locks.md`: every lock, every cheese route, the hooks a fix can use.
- `research.md`: 30 sourced entries on how open worlds guide and gate.

## 0. What Euan said, and the one-line answer

> "I'm walking around in the forest and I don't know what I'm supposed to be doing... I just got out of the city, I go
> into the forest, and I'm like, okay, so what's next?" · "How do I actually unlock everything... I don't even know how
> to get the grapple." · "If I was someone who didn't know about the grapple, I'd just look at that and use blocks to
> build up. So we should probably block that." · "I got the grapple thing when my rank is too low to actually open the
> vault... We should lock that." · "I don't want a guidebook... like Crimson Desert: you don't have a guidebook, but you
> still get guided somehow."

**The answer:** the game already has most of the voices it needs (the Horizon line, the Now line, Directives, Wayfarers,
the Journal, the Atlas, tips), but after the opening none of them is pointed at anything, several are switched off, and
the locks neither hold nor say what they wait for. The plan points them, switches them on, and adds the one thing
missing: **the world remembers each lock you met and calls you back when you hold its key.**

## 1. Done now: the vault's line answers your rank (Phase 0, approved)

Two commits on `pack/progression`:
- **5bbe162e** (CI 40260191638 green): the Tether Vow begins only at **C-rank** (or the vault's own door rank, if
  higher) and only once the sealed door stands open. The order is: open the door, go in, and the vault calls as you come
  back out under its pylons. Short of that, the vault *stirs*: its light rises in the rank's colour, stalls and settles
  back, with "The vault stirs, and stills." / "Its line waits for a C-rank hunter." (or, ranked but with the door still
  shut, it drifts toward the door: "The vault stirs. Its door will know you."). The sealed door now *reads* anyone short
  of its rank who comes within 5 blocks, touches it or strikes it: the ward's HOLD light on the core, its refusing tone,
  "Sealed. It reads you and stays shut. Come back at C-rank." Before, a strike did nothing, which read as a dead block.
  `ascent_tether_vow` covers it. The vow tour films the stir at E, then the vow at C (`/awaken ascent vow open`).
- **945be930** (CI 40260773307 green): a vault's walls hold even inside a Hearth. The audit found that a Hearth Anchor
  beside a vault switched its ward off, so an E-rank player could mine round the rank door. `wilds_ward_rules` covers it.

Euan's own world keeps the rope he already has; nothing is taken back. **The rank is one constant**
(`TetherVow.RANK`); decision 1 recommends D.

## 2. The audit: what a new survival player actually gets

### 2.1 The first hour, as built (full table in `audit-journey.md`)
| When | What happens | What the player is told to do next |
|---|---|---|
| 0:00-0:06 | The opening: bell, run, Ritual, the Void, Accept (topped up to level 2) | follow the light column (good) |
| 0:06 | Infirmary: "When you can stand, the Registry's across the hall." | Now line "Report to the Registry", with a compass. **The only waypoint the opening ever gives.** |
| 0:08 | Registry: the Lens, the licence ("E-rank. Everyone starts at E."), the band, K opens by itself, first power, a Directive | the K key is never named; the Directive is a random roll, offered quietly, its "decision waiting" line lasts 30 s |
| 0:10 | "THE CITY IS YOURS." | **nothing.** No Now line, no compass, no map, no person to see. The designed pan to the High Gate and its Now line (onboarding.md §2) were never built |
| 0:10-0:15 | Free in the city | citizens greet; none names a place outside the walls. Kestrel would start The Last Delivery (the designed bridge out) if right-clicked, but nothing says she exists |
| 0:15 | Through the High Gate | Hale stands by the gate with the bounty board, an unmarked figure. The Wayfarer camp is 210 blocks up the road, with no fire, smoke or marker |
| 0:16-0:25 | The forest (Euan's moment, 2.4) | a rift for a minute; three toasts at once at level 3; a vault on the Horizon line with no reason given |

The pending Registry ceremony (`carillon/m4-registry`, not merged) adds Bram's brief ("Gates are how hunters grow..."),
the Atlas reveal and "Kestrel wants her word from the square", all as speech: still no Now line, compass or thread.

### 2.2 The unlock spine and its times (full table in `audit-spine.md`)
Balanced player, `tools/sim/pacing_sim.py`, 300 runs: **L5 at 1 h, L10 at 3.9 h, D at 5.1 h, L25 at 16.7 h, C at
17.9 h, B at 47.5 h, A at 69.3 h, S at 84.6 h, the Seed at 102.8 h.**

| Kind | Unlock | Gate in code | How the player learns it exists | When |
|---|---|---|---|---|
| Rank | Reassessment | level 10/25/45/70/100 + a trial at a Monolith or the Hearth's Spire | window + Now line (no compass) | D 5.1 h, C 17.9 h |
| Powers | first power · Dash · Power Core · first Ultimate | Registry · L3 · L4 · L35 | cards · window · toast · window | 0:08 · 0:30 · 0:50 · **30.8 h** |
| Body | Runner kit | from the start | **nothing**: tips never show | 0 |
| Body | Tether | pre-fix: any vault forecourt, any rank; now: C + door open | Horizon tease "needs Tether"; now the stir | pre-fix ~1 h; now ~18 h |
| Body | Leaper | Tether mastery 2, no rank check | a gate opens in front of you | Tether + 10-40 min |
| Body | Wings | reach a sky platform's core by movement, no rank check | Horizon tease "needs Wings" (circular) | Tether + 1-3 h |
| Body | Skyborn · Sovereign | A + Wings mastery 3 · the Break or Wake ending | a trial gate · gift scene | 69 h · 102 h |
| Arms | 96 forged weapons | crafting, from the first essence shard | **only the recipe book**, which floods (a bone lights all 16 Wildhide weapons) | ~1 h |
| Arms | Lord-forged | 35% per Gate Lord, certain by the third | on drop | 3-6 h |
| Arms | weapon mastery | hard-coded off (`ArmsServer.java:169`) | — | never |
| Wilds | camp chains · found scenes · bounties | none · L8 · bands 0-1 only | nothing points at the camp · a flare · Hale | any · 2.7 h · ends at L20 |
| World | Hearth · Descent strata · Veil · Scanner · Frames | a Lord's Heart · rank · none · spawn vault · blueprints | road steps, often timed wrong (the Well is open from minute 1, announced at 12 h) | 2 h · rank · 5 h · 1 h · 34 h |

### 2.3 Every gate and lock (full table in `audit-locks.md`)
**Good locks to copy:** the Descent's sealed strata ("Gardens is sealed. It opens at D-rank." · "Grow, and come back"),
the Halo ("Your eyes aren't ready yet (B-rank)"), Wren's ledge ("You'll need the Tether to bring her down"): the lock
names its key and gates the ability, not the route.

**Cheeses, ranked by how likely a normal player finds them:**
1. **Nerd-pole a Tether Spire.** The ward's placed block crumbles after 3 s, and you always stand on one younger than
   that; the petals are rest stops. Euan's exact scenario.
2. **Hero-tree crowns:** the ward is a column of radius 9; the canopy is 24-30 across. A tower beside the trunk never
   touches it, and 20 s on the limbs counts as "arrived by movement".
3. **Sky platforms:** the ward ends 72 under the deck; the rig's anchor rings are solid rest stops.
4. **A Hearth Anchor beside a site** switched every ward off. *Fixed for vaults (945be930); still open at climb sites.*
5. **Relog** wipes the "you built your way here" record: tower up, relog, open the chest (best roll paid), hang 7 s,
   and the sky core grants Wings to someone who never held the rope.
6. Pearls from natural high ground; elytra, riptide, wind charges (read as movement; the chest's best roll pays, the
   crown and core don't).

**Unreadable locks:** the best roll lost silently and for everyone when a builder opens a cache; the ward invisible until
it eats a block, its one toast once per lifetime and silent about what the site wants; "These walls answer only to their
door." at doorless sites; the Wings core and Wren's need-text answer only once a minute; "Sky Platform · needs Wings"
(its ladder needs the rope, its core *gives* Wings); spires leave the Horizon once walked past and never come back after
you earn the rope; door ranks invisible from afar; empty lairs below a level; gate cards that never compare the gate to
your rank (and pay ×0.3 below it, silently).

### 2.4 Euan's moment, reconstructed: out of the High Gate into a forest, level 2, E-rank
- **HUD, Now line:** "◇ Spend 3 points · K" (the Accept tops you up to level 2), else "◇ Level 3: Dash". A gate shows
  only if the random first gate happens to be within 400 blocks.
- **HUD, Horizon:** "△ Sealed Vault ↗ 420 m", usually the spawn vault, with no reason to go.
- **Directives:** the one from the Registry, unaccepted, its line gone after 30 s. Accepted ones never show on the HUD.
- **Quests:** none. Every found scene needs level 8; the two level-1 chains wait at an invisible camp.
- **Within ~1 min of leaving the reserve:** a rift ("Something tore open nearby · 54 m NE"), the only short goal.
- **Level 3:** three toasts at once; lair rumours in a burst.
- **The vault:** walk into its forecourt and the rope was given at E. *(Fixed: now it stirs and names its rank.)*

**Missing, exactly:** a short goal with a direction; a medium goal for the session; a long goal (the ladder is hidden by
design and now far away); a pull you can see by day (only a near-white gate pillar); a reason to pick one direction.

### 2.5 The pacing: stalls and floods (from the sims)
- **Movement:** with the Tether at C, nothing between Dash (0.5 h) and C (18 h) but a mount at ~5 h; then Tether, Leaper
  and Wings all land within ~2-3 h of C, on top of the Path, Truths, strata 4-5 and Teodor: **8-10 unlocks after a
  13-hour desert, the worst shape on the curve.** Pre-fix, the opposite: three tiers in evening 1, then nothing to 69 h.
- **Weapons:** no progression after ~6 h (mastery off; named weapons never built; Arms never counts as a new thing).
- **The C band (18-47 h):** ~1.5 h a level; most road steps are shards; Living Wilds content ends at L20; the first
  Ultimate at 31 h.
- **After 51 h:** two level-road steps in 85 levels.
- **The "new thing" clock** counts sights (biomes, craft levels, happenings), not capabilities; no Ascent tier and nothing
  in Arms resets it.
- **Risk under all of it:** the combat sim finds home-rank Gate Lords won ~5% of the time as built, while the Hearth,
  Lord-forged weapons and core pity all hang on Lord kills.

### 2.6 Every guidance channel (full inventory in `audit-journey.md` §b)
| Channel | Today | Verdict |
|---|---|---|
| Now line | one slot, spent on "Spend 3 points" outdoors | works; wrong priorities |
| Horizon line | names the nearest site, never why | the only place-pull; needs a reason and more sites |
| Directives | random; quiet; 30 s; never on the HUD; `context` parsed, never read | switched off in practice |
| Tips (TipHooks) | **never installed**: every tip in the game shows nothing | switched off |
| Wayfarers, quests, bounties | chains carry places, nothing on the HUD uses them; found scenes need L8; camp invisible | unreachable in hour 1 |
| Journal | living chains + discovery-ux's filed notices: two "Journals" on one tab | name clash |
| Atlas | explored land and "?" from Wayfarers only; never opened or named in today's build | quiet |
| Gate pillars | rank-coloured beams to 1600; no toast when yours forms; not on the Atlas; E is near-white | strong, unexplained |
| Landmark markers | a light shaft at dusk only, within 288 | night only |
| Citizens, crier | flavour only, nothing outside the walls | mute |
| Rumours (Mira, Wayfarers) | need a Hearth, or a Wayfarer met | late |
| Happenings, rifts | strong world-first pulls (discovery-ux rebuilt the rift) | good, but no goal attached |
| Region captions | the Commons covers 700 blocks, so leaving the walls has no caption | quiet |

**discovery-ux** (SPEC §26, uncommitted) already builds the right base: one director (`Herald`), tiers CRITICAL /
IMPORTANT / AMBIENT, densities, topic merging, moments, the find cue and a filed **Journal**. This plan sends its
guidance as Now/Horizon HUD *state* (never more toasts), tags any message it adds with a tier and topic, puts tips on
`Notices.claim(IMPORTANT, "tip")`, and files promises in that Journal under "Waiting". The two Journals should become one
page with two headings (living chains; finds and promises).

## 3. Research (summary; 30 entries with sources and dates in `research.md`)

| Principle | Best source | For AWAKEN |
|---|---|---|
| Pull, don't push: places have *gravity*; "lines" railroad 80% of testers | Fujibayashi and Yonezu, CEDEC, 31 Aug 2017 | the first thread ends at a hub with several pulls, not a chain |
| "If you can see it in the distance, you should be able to go there" | Pearl Abyss, Gamescom dev, 27 Aug 2026 | anything Far Sight draws is reachable and says what reaches it |
| Even "no guidance" needs a readable first lock | Crimson Desert's day-one patch to its first sky puzzle, Mar 2026 | play-test the first vault, the first spire, the first platform |
| The wind on demand; rumours capped at two hints per type | Connell, VFX Voice, 15 Sep 2020; Hamilton, GDC 2021 | the glance's Signal thread; people point, with a cap |
| Icons for unseen things were "a mistake" | Philipp Weber (Witcher 3), 21 Jul 2022 | the Atlas shows only what you saw or were told |
| People tell you what's out there | Dan Hay (Far Cry 5), 15 Mar 2018 | Wayfarers, Hale, the clerk and the crier give Atlas "?" |
| Show the lock early, keep the key near, few open locks | GMTK on Metroid Dread (c. 2021) | the first Tether lock and its key in the first session |
| The hookshot "completely breaks the climbing mechanic" | Aonuma and Fujibayashi, IGN, 9 Dec 2017 | Tether locks need what blocks can't fake; its payoff is the line |
| The Old Man names the paraglider and its price | BotW's Great Plateau (Fujibayashi, IGN, 22 Mar 2017) | Euan's "it says you need a grapple" has the best precedent |
| Danger is the soft gate: "this is not the right way" | Miyazaki, DICE, 28 Feb 2023 | region levels gate terrain; never a "you need a higher level" text |
| One tip at a time, from your state | Terraria's Guide | the clerk's "Anything for me?" |
| The vault drops nothing when broken | Minecraft 1.21 Trial Chambers | gate the payoff, not only the route |
| "Cheating can be fun" | Fujibayashi via Polygon, 12 May 2023 | clever climbs still earn the view and the ordinary roll |
| A no-build rule works as a law of the place with a visible cause | Valheim (Svensson, 2021) | the ward is that law: refuse at the first block, show it |
| The ship log as a detective board | Outer Wilds (Mobius, 20 Dec 2017; MCV, 3 Apr 2020) | the Journal's "Waiting" list and the Atlas pins |
| Balance short and long goals | Schell (2008); Meier, GDC 2012 | Now / Next / Far at all times |

## 4. The progression spine (proposed)

**Ranks are the spine; the body is the reward; each rank opens one rung, one world and one weapon tier.** This is
what the band's Far line and the Ascent page show, one rung ahead at a time.

| Rank (arrives) | The body | Powers | Arms | Worlds and people |
|---|---|---|---|---|
| E (0) | Runner, **taught** · Dash (L3, 0.5 h) | first power (0:08) · a core (L4) | Wildhide weapons from the first shard · first Lord-forged (3-6 h) | the camp, the first gate on purpose, the spawn vault seen and its door read |
| **D (~5 h)** | **the Tether at a vault whose door you open** (the spawn vault's is D) · **the Leaper** (rope mastery 4, ~8-12 h) | a core (L9) | Beastbound tier · mastery steps (P5) | strata 2-3, Mira, Wren's Rope, Tether Spires and hero crowns become D content |
| C (~18 h) | **Wings** (sky core, C + Leaper mastery, ~20-25 h) | Path + signature | Gatewrought tier | strata 4-5, Teodor, possession of animals |
| B (~47 h) | Wings mastery, windways | | | the Halo, strata 6-7, Sun |
| A (~69 h) | Skyborn (as approved) | | Lordscale | stratum 8, Vael, possession of any mob |
| S → Sovereign (~85-102 h) | the Sovereign form, **on any ending** | | | the Court, the Seed |

Today vs proposed, the ladder: pre-fix Runner 0 · Tether ~1 h · Leaper ~1.5 h · Wings ~2-4 h · Skyborn 69 h; now (C)
Tether ~18 h · Leaper ~18.5 h · Wings ~19-21 h; **proposed Tether ~5-6 h · Leaper ~8-12 h · Wings ~20-25 h · Skyborn
69 h · Sovereign 102 h.** That matches the approved Ascent plan (Tether E-D, Leaper D-C, Wings C-B) and puts the first
key in the first long session, near the lock the player met in hour 1.

## 5. The guidance design: the world pulls you forward

### 5.1 Pillars (each one is a test a feature must pass)
1. **Every lock is seen before it opens, and says what it waits for.** In the world's own words: a rank, a level, or
   the ability's in-world name ("a line", "wings"). Name the key; never explain how to play it.
2. **The world remembers what you saw.** A lock you met becomes a *promise*, kept in the Journal under "Waiting" and
   pinned on the Atlas with its need.
3. **When you get the key, the world calls you back, once.** "The spire you passed answers you now · ↗ 640 m."
4. **Three goals at all times, never nagging.** *Now* (minutes, with a compass), *Next* (an evening, a place), *Far*
   (the next rank and what it opens).
5. **The world pulls, the HUD confirms.** Smoke, light, sound, people and pillars first (SPEC §26.1); text is the receipt.
6. **Teach once, by doing, in the place.** The Vow's thrown line is the model. Then never again.
7. **Building round a designed lock is answered by the place, at the first block.** Not by a wall of text.

### 5.2 The first thread: from "THE CITY IS YOURS." to the first gate (Phase 1)
One short, compass-led errand that walks the player out and **ends at a hub with several pulls** (BotW's lesson: a line
that keeps leading railroads; points with pull don't).

| t | Where | What happens | HUD |
|---|---|---|---|
| 0:00 | Registry desk | The clerk: "Kestrel at the Roost has letters for the camp up the north road. You're going that way." The Last Delivery starts by itself, tracked. | Now: "Kestrel · the Roost ↗ 90 m" |
| 0:02 | The Roost | Kestrel hands the satchel, one line about the camp. | Now: "The High Gate · north ↗ 240 m" |
| 0:05 | The High Gate | Leaving the walls is a moment: the caption "THE COMMONS · outside the walls" at the reserve's edge. Hale at a real board: "Work for any rank. Read it on your way back." | Now: "The Wayfarer camp ↗ 210 m"; Next: the camp's smoke |
| 0:07 | The road | The first rift tears 50-70 m off the road (the reserve rule already times it here): the first fight, the first find (discovery-ux's moment). | Now: "Rift tear · 54 m NE", while it lives |
| 0:12 | The camp | Fire and a smoke column, seen from the gate. Ilse takes the letters (the chain closes). Hettie and Ilse offer their level-1 chains. From the camp's rise: the first gate's pillar and the spawn vault. | Now: the tracked chain's step; Next: "E-rank gate · north ↗ 380 m" |
| 0:20-0:45 | The first gate | Placed on purpose (SPEC §21.0.14, never built): registered and level 3, 300-500 blocks out along the road you left by, announced "A gate is drawing breath · north", raised on the Horizon and the Atlas. | Now: "Clear a floor · E-rank gate" (the Registry's Directive) |

Ten minutes in the forest become: a camp, a rift, two people with small asks, a gate pillar, a vault that stirs and
names its rank, and a bounty board to come back to.

### 5.3 Three goals, one glance (Phases 1-2)
- **Now** (the one Now line): only things with a place or a deed outrank bookkeeping outdoors. New sources: the tracked
  chain's current step (`where` = its marker; priority ~68, under danger and rifts, over bookkeeping); accepted
  Directives with progress ("Two new biomes · 1/2"). "Spend 3 points" becomes a pip on the band and a dim second line.
- **Next** (the Horizon line): says *why* ("Sealed Vault · something waits inside"), takes raised sites first (a
  tracked chain's destination, a promise you can now keep), and gains the camp and the first gate.
- **Far** (new, on the band, under the level): the next rank's promise, built from what the player has *met*: "D-rank ·
  level 10 · the vault's line, Gardens, D gates". Personal, never a list of spoilers.
- **The glance:** hold the band key (or the Atlas's wrist glance) to see Now / Next / Far for 4 s, while a thread of
  the Signal's light runs low along the ground toward the Now goal (the Guiding Wind, in our blue). Nothing stays on screen.

### 5.4 Promises: locks you met, remembered and called back (Phase 2)
`core.api.Promises` (progression): `seen(ServerPlayer, String key, BlockPos where, Need need, Component what)`,
`kept(ServerPlayer, String key)`. `Need` = `rank:<R>` · `level:<n>` · `tier:<tether|leaper|wings|skyborn>` ·
`item:<id>`. Saved per player (`schemaVersion` 1), at most 64, oldest dropped.
- **Sources** are lines that already speak: the Vow's stir and the door's reading (Phase 0), the Horizon's teases, a
  ward refusal at a climb site, a sealed stratum, the Halo, Possession, a region's danger caption, a gate above your
  rank, Wren's ledge.
- **Kept** in discovery-ux's Journal as an AMBIENT filed line under "Waiting" ("A vault's line waits for D-rank ·
  Sealed Vault, 420 m NE"), and on the Atlas as a pin with a lock glyph in the need's colour. Nothing pops.
- **Called back** on `Ascent.onGranted`, `RankHooks` and level-ups: the nearest promise the player now meets is raised
  on the Horizon for 10 minutes ("answers you now"), with one IMPORTANT toast, topic `promise`, merged if several.

### 5.5 Building round a lock: Euan's question (Phase 3)
**Recommendation: name the need, not the manual, and stop the build at the first block.** (Decisions 3 and 4.)
- At **climb sites** (Tether Spire, sky platform, hero-tree crown) a survival player's block is *refused*, as
  `CityProtection` does, not crumbled: the 3 s crumble *is* the nerd-pole. Vaults, monoliths and labs keep the crumble.
- The place answers, worded by what the player holds (`HorizonHooks.can`):
  - no rope: "The spire sheds what you build. Its platforms answer to a line." Once per site kind, a second line: "A
    vault keeps a line for a hunter it opens to." The promise is recorded.
  - rope held: a tip instead: "Climb it by rope: hold <key> at a ring."
  - sky platform: "Its rings climb to it by rope." (the need becomes the Tether; Wings, Flight and Jet Boots still reach).
- **The link Euan floated:** the first refusal of each kind is a card with one choice, **Show me** (Y), which opens the
  band at **The Ascent**: the tiers you hold in full, the next one as a silhouette with its name and where it comes from,
  the rest "???". After that, only the action-bar line and the Journal. One link, once, to a ladder, not a guidebook.
- **The payoff answers only the line** (Minecraft's own vault pattern): the crown, the core and a cache's best roll.
  A clever climb with pearls or elytra still earns the view and the ordinary roll.
- **Close the holes that make the lock a lie:** relog keeps the "built" verdict (the visit persists 10 minutes); a
  towered open never spends the best roll silently ("The cache's inner seal holds. It opens to a climb."); the hero ward
  covers the dome; the Vow's "kept" needs the rope; refusals answer on first contact, not on the minute; doorless sites
  stop mentioning a door; Hearths never suspend climb-site wards; sealed doors show their rank in its colour from afar.
- **Terrain that's too dangerous** is gated by danger, as now (region levels, plates, the DANGER caption), never by a
  "you need a higher level" text, which a stack of dirt disproves.

### 5.6 People who point, pulls you can see by day (Phase 4)
- The crier and the gate guards name **real** places from the gate registry and landmarks ("Hear ye! A light stands
  north past the High Gate!"); Familiar greetings point at the nearest unvisited site; capped per region.
- Kestrel, Bram, Oren and Hale get idle lines, so named people speak before you click them.
- **The clerk's "Anything for me?"** (Terraria's Guide): one line, the most relevant next step for this player's state.
- One level-1 found scene (a shepherd's flare in the Commons), so a flare can rise in the first 15 minutes.
- The camp's smoke; a faint daytime shaft over the Horizon's site; the E-rank pillar gets a look of its own.
- Seed three "?" on the Atlas at registration: the camp, the first gate, the spawn vault.

### 5.7 Tips, switched on (Phase 4)
`TipHooks.install` has never been called. Build `TipRules`/`TipHud` (onboarding plan §1) on discovery-ux's
`Notices.claim(IMPORTANT, "tip")`, with the first-stretch set: K (the band), M (the Atlas), the power key after the
pick, how XP comes ("discover, hunt, clear a floor"), the first gate, the first night, and the Ascent's six, each fired
by its place, once.

### 5.8 Directives that fit (Phase 1)
Read `def.context()` (parsed, never used): the Registry's "opening" offers `first_floor` or `explore_biomes`, accepted for
you. The waiting line lasts until K is opened, not 30 s. Accepted Directives show progress on the Now line.

## 6. Reward pacing: the first hour, the first evening, week one

| Window | Today (Tether at C) | Proposed |
|---|---|---|
| **Hour 1** | dense but pointless outside: power 0:08, Dash 0:30, core 0:50, Truths 1:00, a random gate; no thread; tips silent | the same unlocks, *taught*; the first thread to the camp; the first floor by ~45 min; the spawn vault met, its door read, its line promised |
| **Evening 1 (1-5 h)** | first Lord ~2 h, Lord-forged 3-6 h, scenes at L8; D at ~5 h opens doors and strata; movement silent | the same, plus Wren's Rope and spires as visible D promises; **D at ~5 h keeps the hour-1 promise: the vault's line** |
| **Week 1 (5-20 h)** | an 18 h movement desert, then 8-10 unlocks stacked on C | Leaper ~8-12 h (spires and crowns as D content); C at ~18 h brings the Path alone; Wings ~20-25 h, inside the C band |
| **Beyond** | C band empty to 47 h; road ends at 51 h | Phase 5: weapon mastery, a capability clock, the road re-timed and extended to 135, band-2 Wilds content |

The test, from the sims after each phase: **no stretch longer than 3 hours without a new capability** (a verb, a power,
a rung, a weapon, a place type) in the first 50 hours, and no rank-up bringing more than four at once.

## 7. Everything found broken or missing, with its fix and phase
| # | Problem | Fix | Phase |
|---|---|---|---|
| 1 | The opening ends with no thread | the first thread (5.2) | 1 |
| 2 | Tracked quests, bounties and Directives never reach the HUD | Now sources with places; Directive progress | 1 |
| 3 | The camp, Kestrel and Hale are unreachable in hour 1 | camp as a Horizon site with fire and smoke; Hale's board; Kestrel started by the clerk | 1 |
| 4 | The first gate is random and unannounced (SPEC §21.0.14 unbuilt) | the first gate on purpose, announced, raised | 1 |
| 5 | The Now line spends its slot on bookkeeping | place-bearing goals outrank it outdoors | 1 |
| 6 | Directives random, quiet, 30 s, invisible | context-read, auto-accepted first one, on the HUD | 1 |
| 7 | The ladder mis-placed (pre-fix: evening 1; now: stacked on C) | decision 1-2: D, D+mastery, C, A, any ending | 2 |
| 8 | Nothing teaches how a rung is earned | promises; the Ascent page with silhouettes; Horizon says why | 2 |
| 9 | "Sky Platform · needs Wings" is circular; spires never come back | need = the Tether; re-raise on `onGranted` | 2 |
| 10 | Ascent tiers and Arms never count as new things | `markNewThing` on tiers, first Arms craft, Lord-forged, first Weave | 2 |
| 11 | Wren's Rope (L8) needs the Tether and blocks every later scene | `requires` at chain level; drop | 2 |
| 12 | The ward's 3 s crumble is the nerd-pole | refuse at climb sites, name the need | 3 |
| 13 | Relog wipes "built" | persist the visit 10 min | 3 |
| 14 | A towered open silently spends the best roll for everyone | the cache's inner seal | 3 |
| 15 | Hero-tree ward too small; no tease | ward the dome; Horizon site | 3 |
| 16 | Hearths suspend wards | done for vaults (945be930); climb sites in Phase 3 | 0 / 3 |
| 17 | Refusals once a minute; door text at doorless sites | first contact; per-kind text | 3 |
| 18 | Door ranks invisible from afar | the rank's colour on the door core | 3 |
| 19 | Gate cards don't compare ranks; below-rank pays ×0.3 silently | "above your rank" / "below your rank · pays less" | 3 |
| 20 | Tips never installed | TipRules/TipHud on Notices | 4 |
| 21 | Citizens and the crier name nothing outside | real places, capped | 4 |
| 22 | Nothing visible by day; E pillar near-white | smoke, daytime shaft, pillar look | 4 |
| 23 | Two Journals on one tab | one Journal, two headings (with discovery-ux) | 4 |
| 24 | Weapon mastery hard-coded off; recipe book floods | wire mastery; one summary toast | 5 |
| 25 | The C band empty; road ends at 51 h; Living Wilds ends at L20 | re-time and extend the road; band-2 chains and bounties (Living Wilds F) | 5 |
| 26 | The Reassessment is hard to find (Monolith vs Spire vs ruin names) | a compass to the nearest stone; rename "Reassessment Stone"; give one with the L10 step | 5 |
| 27 | Road steps announce at the wrong time (the Well at 12 h, open from minute 1) | re-time to the real gates | 5 |
| 28 | The "new thing" clock counts sights | a second, capability clock that surfaces the nearest capability | 5 |
| 29 | An E-rank power is named "Grapple" | rename (decision 11) | 2 |
| 30 | The Become ending grants no Sovereign form | any ending | 2 |

## 8. Phases, gates, effort and risks
Effort is builder time at AI pace (scaled from git history: a Living Wilds phase took 1-2 h, Arms S0-S7 about 9 h).

| Phase | Scope | Effort | Gate to the next |
|---|---|---|---|
| **0** | the vault's lock (done) | done | CI green (5bbe162e: 40260191638; 945be930: 40260773307) |
| **1 The first thread** | 5.2, 5.3 Now, 5.8; items 1-6 | 2-3 h | GameTests `progression_first_thread`, `progression_now_priorities`, `gates_first_gate_on_purpose`; one autopilot tour "leave Carillon"; **Euan's 10-minute session: leave the city in survival** |
| **2 The ladder you can see** | section 4 (decision 1-2), 5.3 Next/Far, 5.4; items 7-11, 29-30 | 2-3 h | `progression_promise_called_back`, `ascent_ladder_ranks`; pacing sim re-run shows the ladder at D/D/C/A |
| **3 Locks that hold and speak** | 5.5; items 12-19 | 2 h | `wilds_ward_refuses_climb`, `wilds_arrival_survives_relog`, `wilds_cache_inner_seal`; one tour at a spire and a platform |
| **4 Teach once, point with people** | 5.6, 5.7; items 20-23 | 2-3 h | tip tests on the director; a tour of the first hour |
| **5 The long middle** | items 24-28 (with Living Wilds F) | 3-4 h | the sim test in section 6 |

Phases 1 and 3 touch different files and can run as two builders at once; 2 follows 1; 4 needs discovery-ux merged;
5 can run beside 4. About 12-15 builder hours in all, a day of wall-clock with two builders.

**Risks:**
- **Shared files.** Phase 1 touches the opening (Carillon's session), living (Living Wilds), gates and signal
  (discovery-ux). Land discovery-ux and the Registry ceremony first, then brief Phase 1 on top of them.
- **Over-guiding.** The first thread must *end* at the camp. After that the player chooses; nothing auto-tracks.
- **Lords.** If the combat sim is right (home Lords won ~5%), everything behind a Lord kill slips by hours. Land the
  combat fixes before tuning the spine numbers.
- **The ladder retune** changes the sims and the feel; re-run `pacing_sim.py` and the Ascent tours.
- **Tours wait** for the machine to be free (Euan is playing).
- **Multiplayer:** promises, the first thread and the first gate are per player; one player's tower must never spend a
  cache's prize for another (Phase 3's inner seal).

## 9. Euan's decisions
1. **The Tether's rank: the vault's own door rank (D near Carillon, ~5-6 h) rather than a flat C (~18 h).**
   *Recommend D.* You said C, and C is what's built now. But vaults near home have D doors, so "open the vault and it
   gives you its line" is your rule at D; C leaves an 18-hour movement desert and stacks the ladder on C.
2. **Spread the ladder: Leaper at D with rope mastery 4 (~8-12 h), Wings at C (~20-25 h), Skyborn at A as approved,
   the Sovereign form on any ending.** *Recommend yes.*
3. **Building up to a climb site: refuse the first block and name the need** (your "block that"), rather than allow
   pillaring and gate only the payoff. *Recommend refuse at climb sites, crumble elsewhere, and the payoff only by the line.*
4. **How the need is said: the place names the key and where it comes from, once; the first time, a "Show me" card
   opens the band's Ascent page (the next rung as a silhouette).** Rather than a hidden "you need to unlock something /
   a higher level". *Recommend naming it.* Danger stays the gate for terrain.
5. **The first thread: the clerk starts The Last Delivery and the compass walks you out to the camp; the first gate is
   placed on purpose beyond it.** *Recommend yes.*
6. **Three goals: Now (places outrank bookkeeping), Next (the Horizon says why), Far (the next rank's promise on the
   band), and the glance with the Signal's thread.** *Recommend yes.*
7. **Promises: locks you met are kept in the Journal and pinned on the Atlas, and the world calls you back once when
   you hold the key.** *Recommend yes.*
8. **Switch tips on, through discovery-ux's director.** *Recommend yes, after discovery-ux merges.*
9. **People point: the crier, guards, idle lines, the clerk's "Anything for me?", a level-1 flare, camp smoke.**
   *Recommend yes.*
10. **Phase 5, the long middle: weapon mastery, a capability clock, the road to 135, band-2 Wilds content.**
    *Recommend yes, as its own short brief after Phases 1-3, folded into Living Wilds F where it overlaps.*
11. **Rename the E-rank "Grapple" power** so "the grapple" means the Tether. *Recommend "Snap Cable".*
