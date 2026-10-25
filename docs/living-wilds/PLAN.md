# The Living Wilds · Phase A plan (for Euan's approval)

Ordinary survival in a world that's always up to something: everything you do outside the gates leads to something
and you can see it; the land is a patchwork of named regions with wonders, legends and people in trouble; migrations,
caravans, storms and hunts happen on their own clock; and your home becomes a village the world passes through.
Survival stays the core.

Euan approved the direction on 2026-10-25 ("All four phases, approval page first"). This file is the source of the
approval page; on his yes it becomes SPEC §21 "The Living Wilds" (§18 Pixels exists; §19 Turbo and §20 Far Sight are
reserved). Nothing here is built yet.

Read with: SPEC §2 (numbers), §4.3 + "Plan v2 · onboarding and progression" (home XP), "Plan v2 · overworld creatures"
and docs/modules/fauna.md (44 creatures, lairs), "Plan v2 · anomalies", the Holo UI section (SPEC ~688-757), §16
(Carillon, its Renown and deferred MMO-lite), §17.4 and §17.11 (the Wilds), Carillon's M4 story and onboarding
(origin/carillon/m4-look: docs/carillon/m4/story.md, onboarding.md), docs/ascent/BRIEF.md (the "genuinely magical" bar).

Approval page: https://claude.ai/artifact/C97MPmz9gBFJMMjL7Eu8hT (source `plan.html`). Concept images: `concept/01..08`. XP model: `living_wilds_sim.py` (extends tools/sim/pacing_sim.py), output in
`sim-results.txt`.

---

## The feeling (v3, Euan, 2026-10-25)

"A mix between normal MC survival and Crimson Desert [...] you can keep exploring, there's always new things to do, but
you don't have to do them [...] always more things to unlock and more ways to play [...] you're doing a normal
survival, but it's always interesting [...] you can always stumble upon new quests or new biomes you haven't seen or
new areas where there's even stronger and unique experiences [...] world events that you stumble on [...] things
actually alive and happening, not always stuck. Sometimes you see a migration of animals going somewhere [...] whilst
also still being very survival-based, like MC, where you can build your home somewhere and build a base and build a
village [...] to the highest level."

So the plan has one job above the others: **you're playing ordinary survival, and the world around you is always up
to something.** Three layers make it:

1. **It happens without you.** A director, the **World Pulse**, keeps the land you can see busy: migrations, caravans,
   storms that cross the plains, festivals, hunts under the full moon. They start and end on their own clock; you can
   join, watch, or miss them and hear about them later (§3).
2. **There's always somewhere new.** Regions with names and levels (§1.2), one **wonder** per region, secrets,
   legendary beasts, treasure maps (§5), Wayfarers in trouble (§2): explore any direction for hours and keep finding
   things.
3. **Your home is part of it.** People you helped can settle at your Hearth, your village gets a name on the map,
   caravans come along the roads you mark, migrations pass your fields, raids come with warning (§6). Building stays
   free-form Minecraft.

**The Hilltop Test** (the bar, like the Ascent's "genuinely magical" list; judged from tour screenshots and play, never
from code alone):
1. From any hilltop in daylight, within 30 seconds, you can see at least one thing that pulls you: a dust haze over a
   herd, a caravan's smoke, a storm cell, a festival's lanterns, a gate's pillar, kites circling something dead.
2. At night there's at least one light on the land that isn't yours.
3. Every ~15 minutes of wandering in new land you stumble on something you haven't seen before (for the first ~30 hours).
4. Nothing waits for you: happenings run on their own clock and you can miss them ("You missed the crossing. It went
   over at dawn.").
5. Nothing nags: no markers over heads, no timers on screen, nothing in the Journal until you choose it.
6. It looks and sounds like Awaken: our particles, our sounds, tells drawn at a distance like the gate pillars.
7. Survival first: your base is part of the world and never the victim of something unwarned.
8. 120 fps on the M4 with three happenings in view; the Pulse within 0.5 ms a server tick.

---

## 0. Why it doesn't feel like an MMO today (the diagnosis Euan agreed with)

A lot already pays: hostile kills, ores and building give a hidden trickle (§4.3, budgets per 10 minutes); fauna has
44 creatures in families, named beasts with lairs and Power Cores; anomalies; the Wilds pays discoveries; Directives,
Truths and Titles. It doesn't read as progression because:

1. **It's invisible.** No numbers, no bars, no "you got something". A sheep pays nothing at all.
2. **The world follows you.** Creature content level = player level − 1, so no place is "too dangerous yet" and none is
   "where I farm".
3. **No people in the wilds.**
4. **Spawns are random around the player**, not creatures that live somewhere.

Phases B, C and D answer these in order; E, F and G add crafts, wonders and your place in the world.

---

## 1. Phase B · Everything pays, and you see it (the Holo combat layer, regions, the XP economy)

### 1.1 What the player sees

**The band reads what you look at** (concept 01). Once you hold the Holo band (fitted at Carillon's Registry walk; in
classic worlds at the awakening), the band scans the creature under your crosshair: a hairline of light from the
band's diamond, one scan band across the body (180 ms), and a **plate** of projected Holo glass forms one block above
it, tilted toward you like a real object:

- **Name** (cyan-white light text), or "Unknown" for a kind not yet in your Codex (Jade's rule).
- **Level chip** in the rank colour of that level's band (E #F4F6FB 1-9, D #5FD08F 10-24, C #6FB3FF 25-44, B #B48CFF
  45-69, A #F2A541 70-99, S #FF5A6A 100+). Ten or more levels below you the chip greys ("trivial"); five or more above,
  it turns ember and a small skull glyph shows ("deadly").
- **A segmented health bar**: 10 glass segments, filled in light. A hit breaks segments off: the lost part flashes
  amber and drains over 300 ms. Armour shows as a thin sliver over the bar, Force states (frost, burning, charged,
  poisoned) as glyphs at the right end.
- **Tags**: ELITE (amber glass chip), the beast's title for a named beast ("The Briar Sow · Lord of the Commons"),
  CAMP or NEST for a camp's heart.
- **Temper**: the rim tints by intent: hostile to you warm, neutral calm cyan, passive no rim.

Rules: plates show for the creature you look at (24 blocks; PER 20/40 widen it to 32/40), for every creature in a
fight with you (it hurt you or you hurt it in the last 6 s), and for elites and named beasts once seen. At most **4
plates** at once (nearest threats first). A plate fades 3 s after its fight ends, or 0.6 s after you look away from a
calm creature. No plates on players, Echoes, citizens or Wayfarers (they get a name line on look only). Plates draw on
vanilla mobs too (a zombie at night shows its region's level). Same plates inside gates.

**Damage as Holo glyphs** (concept 02). Each hit you deal prints a number written in strokes of light at the hit
point, drifting up and fading over 700 ms, coloured by Force (kinetic white, heat ember, frost pale blue, charge
yellow, gravity violet, spirit teal). Crits print 1.6× larger in gold with a brief fractured-glass flare behind them.
Hits within 6 ticks on the same target merge into one rising number. Damage you take is not printed (the plate and
the screen already say it). Setting `damageNumbers` FULL (default) | CRITS (crits and kills only) | OFF.

**XP streams into the band** (concept 02). When a creature dies to you, pale square motes lift off the body over its
death and stream in a curving ribbon into your wrist band; a small "+142 XP" rides the ribbon. The band's segment
nodes light in sequence and a thin XP arc round the band fills; the HUD level line (`LV 14 · D`) gets the same thin
arc under it. Other XP (a herb, an ore, a catch, a quest step, a discovery) streams the same way from its source,
smaller. Grants within 1.5 s merge ("+312 XP · 3 kills"). XP shown is absolute XP, the same number the Status tab uses.

**Fresh and worn** (the budgets made visible). Repeatable sources already pay full up to a budget per ~10 minutes and
less after. The XP arc shows it: while a source is fresh its motes are bright; as you grind one thing they thin and the
"+N" shrinks, and a one-time toast says "Worn · hunting pays less for a while. Try something else." Variety is always
fresh. This turns a hidden cap into a rule the player can play with (rested XP, inverted).

**Level-up is a System moment** (concept 03). The band flares; the world dims a touch for 1.2 s (`LensClient.override`
with a `living:level` look); two rings of light expand across the ground from your feet lifting square motes (own
particles, never vanilla's); a faint column of light rises; a tall pane of Holo glass unfolds from the band with
"LEVEL 15 · +3 STAT POINTS" typed in light and the rank line under it; a rising glass chord in the System's voice
(D major pentatonic, `living.level_up`). About 2.4 s, then the pane folds into the band. In combat it is compact (the
flare and a toast) and the full moment plays when the fight ends. Players nearby see the rings and the column, never
the pane. Rank-ups keep their Reassessment scene.

**Rare drops stand in a light pillar** like a gate's, in their rarity colour (uncommon green, rare blue, epic violet,
legendary amber; a Power Core in its own white-gold), visible 48 blocks, until picked up. Rarity is an item tag
(`#awaken:rarity/<tier>`).

### 1.2 Regions: the world stops following you (concept 04)

Revised 2026-10-25 after Euan's note on the first draft (distance rings only): "Distance [shouldn't] be the only thing
[...] otherwise it makes the world too linear [...] it removes the core of what Minecraft is, that you can explore for
hours and hours [...] less about distance [...] more about different zones and areas."

So the overworld is a **patchwork of named regions**, each with its own level, mixed in every direction. Distance from
Carillon only tilts the odds: a dangerous valley can sit a couple of kilometres from the wall, and a calm one can wait
five kilometres out. This replaces the follow-you scaling for every overworld creature, fauna and vanilla hostiles
alike.

- **Regions** are 600-1,400 blocks across: a jittered grid of region hearts about 900 apart, with borders warped by
  noise so they lean toward rivers, ridges and biome edges. Each has a seeded name from biome grammars ("Rimewold",
  "Briar Downs", "Ashfen", "the Gloam Reach", "Sunglass Waste", "Willowmere") and a level span of about six, rising from
  its edge to its heart (Ashfen 30 at the border, 36 at its heart: the deeper in, the worse it gets).
- **The Commons** is the fixed home region round Carillon (in classic worlds, round world spawn): levels 1-8, the
  first hour. Its neighbours never go above 20. From the second ring on, anything the odds allow.
- **The odds by distance** (a region's heart from the city centre):

| Distance | Lv 1-8 | 8-20 | 20-45 | 45-80 | 80+ |
|---|---|---|---|---|---|
| The Commons | 100% | | | | |
| Its neighbours | 40% | 60% | | | |
| Under 2,000 | 25% | 45% | 25% | 5% | |
| 2,000-5,000 | 15% | 30% | 30% | 20% | 5% |
| 5,000+ | 10% | 20% | 25% | 25% | 20% |

- **Biome tilt:** harsh land (pale garden, badlands, jagged and frozen peaks, dark forest) moves a region one band up
  40% of the time; gentle land (plains, meadow, flower forest, cherry grove, mushroom fields) one band down 40% of the
  time. The deep dark adds +10 under any region.
- **Every direction is fair:** bands are dealt from a seeded shuffle per 3×3 block of regions, so each block holds a
  spread for its distance. No direction is all deadly or all easy, and wherever you wander there is somewhere at your
  level within a region or two.
- `levelAt(x, z)` is a pure function: the nearest of 9 jittered hearts after the warp, the region's record (name,
  band, span, dominant biome sampled once at its heart) from a small cache, then the edge-to-heart curve. Seeded,
  allocation-free per lookup, thread-safe: worldgen, spawning and the client's Atlas share it.
- Creature level = `levelAt` + role offset (ambient −2, neutral 0, hunter +1, elite +2, camp heart +3, named beast at
  its lair +3). Mob scaling stays SPEC §2's (`health × (1 + 0.075 lv)`, `damage × (1 + 0.04 lv)`), applied once at
  spawn as permanent modifiers, so a zombie in Ashfen is a level-33 zombie.
- **Elites in the wild:** 5% of neutral and hunter spawns are Elite (×2.5 health, +2 levels, a rank-coloured rim glow,
  a guaranteed family material, 10% Power Core Shard).
- **The Hearth calms its land:** inside a Hearth's radius plus 96 blocks, the level is
  `min(region, the household's highest level)`, so a base built in a hard region never has nights above you.
- **Named beasts become each region's bosses at their lairs** (lairs already sit every ~1,500-1,800 blocks): a Briar
  Sow lair in Briar Downs is a level-15 boss; the same kind in the Gloam Reach is level 58. Their plate shows their
  title ("The Briar Sow · Terror of Briar Downs").
- **Entering a region** shows a place caption in the letterbox style Carillon uses ("ASHFEN · Lv 30-36"), once per
  region per play-day. **Far above your level** (region ≥ your level + 5) the caption turns ember and the band warns
  with an amber toast ("Danger · creatures here are Lv 30+"), at most once per 10 minutes.
- **The Atlas shows the patchwork** (concept 04): each region tinted by threat with its name and levels as light text,
  lairs, nests and Wayfarer camps you know. A region shows once you've stood at its edge or heard of it (Mira's
  rumours, Wayfarers' talk); the rest stays under cloud. Until the Atlas (Carillon M5) ships, the K menu's Gate Map
  lists the regions you've entered with their levels and directions.
- Nether and End keep their vanilla danger for now. Gates keep their own content levels (§2).

### 1.3 The XP economy (numbers from the sim)

Principles: everything you do outside pays something you can see; on-level work in a region at your level pays fully; grinding one
thing levels off ("worn"), variety stays fresh; gates stay the big jumps and the only source of Lords' hearts, gate
gear and most Power Cores; the balanced player's story length holds.

| Source | Today | The Living Wilds |
|---|---|---|
| Hostile kills | 4% of a level × min(2, hp/20), budget 0.15 per 10 min, tapered vs content level (vanilla mobs count as 0) | **every creature counts**, by the creature's level: hunter-role 3%, neutral 1.5%, ambient or passive animal 0.45%, elite 12% of a level; budget 0.10 per 10 min; then ×(1 + 0.08 per level above you, max +40%) or ×1/(1 + levels below / 8) |
| A sheep, a cow | nothing | ambient rate + Hunting craft XP |
| Ores | 1% per ore, budget 0.06 | unchanged, plus Mining craft XP |
| Building | 0.05% per block, budget 0.03 | unchanged |
| Crafts (Phase E) | none | Foraging, Mining, Fishing 0.012 each, Cooking and Smithing 0.007 each, per 10 min |
| Wayfarer quests (Phase C) | none | 0.06-0.2 of a level per step, a chain's finale ~0.8, once per claim id; plus the chain's relic |
| Association bounties | none | 0.15-0.3 of a level + essence (coin once Carillon's economy ships), once per claim |
| Camps and nests (Phase D) | none | 0.4 × floor 1 of the region's rank, budget 0.10, once per camp per cycle |
| Discoveries, anomalies, Directives, Regimen | as today | as today |
| Gate floors | SHARE (§2) | **× 0.76** (the wilds carry part of the climb; loot, cores and hearts unchanged) |

What the sim says (`living_wilds_sim.py`, 200 runs per player kind; same model before and after, so the change is
what counts; Phase B ports it into tools/sim/pacing_sim.py and retunes to its 95-105 h target):

| Player | Hours to 50, today → Living Wilds | Hours to 135, today → Living Wilds | Share of XP from the wilds (hunt, crafts, quests, camps) |
|---|---|---|---|
| Balanced | 41.8 → 36.6 | 82.1 → 79.1 (−4%) | 1% → 17% |
| Careful | 49.4 → 38.3 | 100.3 → 86.7 | 2% → 23% |
| Gate-rusher | 46.4 → 44.7 | 84.8 → 87.9 (+4%) | 1% → 8% |
| Builder | 41.6 → 35.6 | 84.8 → 81.1 | 2% → 19% |
| **Wanderer** (new: a gate in a third of its cycles, long stretches outside) | not viable today | 32.4 h to 50, 79.7 h to 135 | 38% (+54% from discoveries, anomalies and dailies) |

Hourly, through the mid-game: focused hunting in a region at your level ≈ 0.55 levels/h, ten levels below you ≈ 0.25;
mixed wilds play (hunting, two crafts, a quest step, a camp) ≈ 0.9; three gate floors ≈ 0.65 (C) to 2.0 (B). So the
wilds can carry you, and gates make you strong. First hour: level 3 at ~27 min, level 5 at ~54 min (target 45-60).

Happenings witnessed (~28 kinds), wonders (~40) and legends add to the discovery pool (140 → ~210 over the game); the
Phase B retune absorbs them.

Note for the pacing owner: pacing_sim counts in-game days (Regimen, Directives, dreams) only in outdoor minutes; real
days also pass while you're in a gate. Counting them makes every player ~15-20% faster in the same model. Both columns
above use the corrected count, so the comparison is fair; the absolute calibration stays pacing_sim's.

### 1.4 Systems and data (Phase B)

- `living.region.RegionMap` (pure), `Regions` API, region names (`living.region.Names`, data grammars per biome), captions
  and warnings (the server sends a region id on change, ≤ 1 per 2 s).
- Spawn hook: `FinalizeSpawnEvent` (verify in 26.3) sets the region's level on fauna and vanilla monsters through
  `MobRole` (core.api) and applies the scaling modifiers; fauna's `contentLevel` rule switches from the player's level
  to `Regions.creatureLevel`. Persistent beasts re-level at their lair when it wakes them.
- Kill XP: `ProgressionModule.onDeath` pays by the creature's level and role (every `LivingEntity` with a role or a
  vanilla category, not only `Enemy`), through the existing `XpBudget` with the new table; the taper moves after the
  budget as today. `XpSource` gains HUNT, CRAFT, QUEST (the ledger splits them).
- Sync: health is vanilla's synced entity data; level, role, elite and title ride a small synced attachment
  (`awaken:creature_tag`, set once at spawn; verify the 26.3 attachment sync API). Damage glyphs come from health
  deltas the client already sees, plus one compact `living:hit_fx` (crit, Force) to the attacker only. XP streams come
  from one `living:xp_fx` (amount, source position, kind) to the earner only.
- Client: `living.combat.client.Plates` (≤ 4, drawn through the Holo's world-space glass: needs a small public holo
  API, §5.3), `Glyphs` (a pool of 24), `XpStream` (≤ 64 motes), `LevelMoment`, `LootPillars`. Settings
  (`awaken-living-client.toml`): `plates` (on), `damageNumbers` (FULL|CRITS|OFF), `xpStream` (on), `levelMoment`
  (FULL|COMPACT), all honouring `reducedMotion`.
- Sounds (tools/art audio pipeline, the System's glass voice): `living.scan` (the band reading a creature, very soft),
  `living.crit`, `living.xp_tick`, `living.level_up`, `living.region_enter`.

---

## 2. Phase C · Wayfarers and quests (concept 07)

### 2.1 What the player sees

People out in the wilds, met where they are, in trouble or at work: a climber stranded on a cliff, a caravan under
attack, a hermit who wants a beast's horn, a Precursor scholar at a ruin, a wounded Runner. You find them; nobody
hands you a list.

- **Found, never forced.** A Wayfarer scene is placed in the world near you in a region of the right level, with a sign
  you can see from afar: a distress flare (a red column of light), a caravan's smoke, a lantern on a ledge, a song.
  The Horizon line names it only once you've seen the sign ("△ Wren, stranded ↗ 40 m"). No "!" over heads, no
  waypoint spam.
- **Solved with what you can do.** Quests are built around the player's abilities: the rope (zip, swing), the
  Leaper, wings and flight, powers, the Veil (spirit sight, cord), the Lucidium (dreams), PER (tracking, omen detail),
  mounts, Runner moves. A step that needs a tier you don't have says so and waits ("Wren's ledge · needs Tether").
- **Rewards that change how you play:** a **relic** per chain (a named item with its own effect, e.g. "Wren's Hook:
  your rope reaches 6 further"), recipes, titles, Power Core Shards, Atlas reveals, renown with Carillon's factions.
- **The Journal** on the Holo: a new K-menu tab holding only the chains you've found and taken (at most 5 active, 1
  tracked), written as short journal pages, with the next step in a line. Never an undiscovered quest. The tracked
  step takes the Horizon line.
- **Association bounties:** three notices at a time on the High Gate quartermaster's board (and the Association's in
  the city), for your level and rank only: cull, hunt an elite, bring family materials, clear a camp you've seen. A new
  one next play-day after you take one; no expiry.
- **World events you stumble on** come from the World Pulse (§3): migrations, caravans, festivals, refugees, the Wild
  Hunt. Many open a chain or a bounty.

### 2.2 The twenty chains (hand-written; outlines, full text in Phase C)

Consistent with Carillon's story (the Choir under the city, the bells that calm gates, the Runners and Kestrel, Bram
and the Association, the gate inside the walls as the open question, the C-rank "bell metal" thread) and STORY.md (the
Precursors' three paths, the Hollow Court, the Signal). Tone: bright, funny and sincere, then suddenly brutal. No em
dashes in player text. The Rival (Vael) stays out: the patent rule keeps his beats on rank milestones.

| # | Chain | Levels | Who | What happens | Abilities | Reward |
|---|---|---|---|---|---|---|
| 1 | **The Last Delivery** | 1-8 | Kestrel, the Roost | The word you never brought back from the square, and one last bag: through the High Gate to the gatehouse, then on to the Wayfarer camp. Ends with the Roost letting you go. (Shared with Carillon's "Roost's word" thread.) | Runner moves, the first stretch outside | **Courier's Knot** (tactical sprint recharges 25% faster), Runners' Guild renown |
| 2 | **Ten Sheep Short** | 1-8 | Hettie, shepherd at the farmstead | Something takes a sheep a night. Read the tracks to a Gloomstalker den, take it by day while they sleep, bring the old ram home. | PER tracking, light, night fighting | **Shepherd's Lantern** (night hunters won't come within 8 blocks), its recipe |
| 3 | **Moth Lights** | 1-8 | Ilse, herbalist at the Wayfarer camp | Follow Lumen Moths at night to where they circle buried ore; gather what grows there. Teaches Foraging. | night travel, Foraging, digging | **Lumen Tonic** recipe (night sight without the vignette), Foraging's first perk early |
| 4 | **Oren's First Steel** | 1-8 → 8-20 | Oren, the smith | Ore from three places and a temper at his forge; he complains the whole way. Teaches Mining and Smithing. | Mining, Smithing | **Oren's Mark** (your next blade starts one temper up), Wildhide rivet recipe |
| 5 | **Wren's Rope** | 8-20 | Wren, a young Runner | Stranded on a cliff ledge over a ravine after something cut her line; she was carrying a crate of bell bronze, now gone (thread to 10). | the rope (zip, swing, vault) | **Wren's Hook** (your rope reaches 6 further) |
| 6 | **The Caravan Road** | 8-20 | the Merchants' Guild | A caravan under attack at dusk on the Sunscorch road; hold the wagons, then see it to the oasis outpost through the night. | powers in a crowd, mounts | **Caravan Writ** (Merchants' Guild shops 15% cheaper), a Power Core Shard |
| 7 | **The Hermit's Horn** | 8-20 | Gus, a hermit in the Crags | He wants a Stonehorn shard. The lair is up the cliffs; the horn he makes from it calls herds. | climbing, wall moves, a beast fight | **Herdcaller Horn** (once a play-day: call the nearest herd to stampede along your aim, or calm a charging beast) |
| 8 | **Pell's Message** | 8-20 | Pell, a wounded Runner | Legs broken in a fall, with a message for the Association about a gate forming where none should. Get Sun's salve from the city and back before dark, then carry the message. | the run, mounts | **Pell's Spurs** (your mount sprints 15% faster, the whistle reaches 80 further), Association renown |
| 9 | **The Drowned Bell** | 8-20 | Ama Voss, Teodor's old student, at a sunken ruin | A Precursor bell under a lake: the Choir had outposts. Dive, scan the machines, and see the bell's resting place with spirit sight. | swimming, the Scanner, the Veil (from 15) | **Voss's Lens** (Scanner range ×2, it hears sunken ruins), Codex pages, Archive renown |
| 10 | **Bell Metal** | 20-45 | the Association | The bronze from Wren's crate is being melted in a camp of the Unbound, rogue Awakened who farm gate breaks and hate the bells that calm them. Scout in the Veil, then break the camp. (Carillon's C-rank spine.) | the Veil, stealth, a human fight | title **Bellward**, Bellbronze blade recipe (its ring staggers gate creatures) |
| 11 | **The Weaver's Bargain** | 20-45 | a Duskwood tailor, and a spirit | The tailor wants the Weaver Queen's silk; a spirit says the Queen guards a Sleeper's dream-cocoon. Kill her, or bargain. | the Veil, the Lucidium, a beast fight | choose: **Queen's Silk Mantle** (glide drag −20%) or **Dreamthread** (dreams start 15 more lucid) |
| 12 | **Stilts of the Mire** | 20-45 | a Mire village on stilts | The Mire Mother woke and the bog rose; children are stranded on toppled huts. Glide them out, then calm or fight her. | wings and glide, the rope | **Mire Charm** (no bog poison, no slowdown on mud), Mire renown |
| 13 | **The Keeper's Lantern** | 20-45 | a Keeper's spirit at a monolith | One of those who went Down asks you to carry a lantern of its memory to the Well; the lantern exists only in spirit. | the Veil (cord length puzzles) | title **Lanternbearer**, a Latent (Veil time +20%), a Revelation fragment |
| 14 | **The Crossing** | 20-45 | herders at the Antler Grove | The great migration fords the river; the packs wait; the Old Antler walks with the herd. See them across. | mounts, powers, Hunting | **Antler Crown** (herd animals and your mount never flee you; +2 PER) |
| 15 | **Sun's Bitter Cure** | 20-45 | Sun, the healer | Wayfarers near a nest are blight-sick. Three herbs from three regions and a meal only a cook can make. | Foraging, Cooking | **Sun's Cure** recipe (clears blight sickness), Healer renown |
| 16 | **The Relay Spires** | 45+ | Ama Voss, again | Ascendant relays on five Tether Spires and a sky platform; align them and the Halo answers once. | Tether, Wings, Skyborn flight | **Relay Key** (travel between spires you've climbed), a Halo lead in the Codex |
| 17 | **Mother of Nests** | 45+ | Wayfarers of the far camps | A nest from an ancient break, grown to tier 5; three stages with Wayfarer allies. | everything you have in a fight | **Ashsteel** recipe, title **Blightbreaker** |
| 18 | **The Sleeping Village** | 45+ | nobody awake | A village where everyone sleeps (the Sleepers' dream leaking). Enter their dreams through their beds and wake them one by one. | the Lucidium (reality checks, manifest) | **Sleeper's Bell** (ring it to end a nightmare at once), title |
| 19 | **Mira's Last Map** | 45+ | Mira, the scout | Light the beacons of five far landmarks for the map she can't finish herself. | exploration, flight | **Mira's Compass** (the Horizon line names one unheard ruin a play-day), an Atlas reveal |
| 20 | **The Courier Who Never Came Back** | 45+ | Kestrel | Kestrel's old partner vanished in the far regions twenty years ago; his Runner marks still lead across them, ending at what's left of him and the letter he was carrying. To her. | movement mastery (Flow chains, Leaper, wings) | **Last Mark** (Flow drains 30% slower), Kestrel's last line |

Relics are bound to you: one per player per chain, kept on death, never duplicated (claim ids), their story on hover.

### 2.3 Systems and data (Phase C)

- Chains as data: `data/awaken/awaken/quest/<chain>.json` (schema 1): steps with an objective (`talk`, `reach`,
  `kill`, `collect`, `deliver`, `escort`, `protect`, `use` (an ability or tier: `ascent:tether`, `veil`, `dream`,
  `mount`), `discover`), requirements (`level`, `rank`, `tier`, `band`), text lines, rewards (claim id
  `quest/<chain>/<step>`). Lines authored, no language model.
- Scenes: `living.wayfarer.Scenes` finds a spot for a chain's opening (a cliff face 12+ high with a ledge, a road, a
  ruin, a lakeshore) within 64-160 blocks in a region of the right band, like the anomaly spot finder, at most one open scene per
  player, every ~25-40 outdoor minutes, never with an anomaly running. Scene state is saved (schema 1); a scene you
  leave alone fades after a play-day and comes back elsewhere later.
- Wayfarers: an NPC entity drawn with `core.client.skin.SkinnedMobRenderer` (Carillon's citizens' renderer, never
  `AvatarRenderState`), a few poses, lines through Signal story lines; hurt, never killed (Carillon's citizen rule).
- Wayfarer camps (one in the Commons on the High Gate road, then about one per 2,000 blocks of road): a fire, wagons,
  two or three Wayfarers, a bounty post; safe from spawns within 24 blocks.
- Journal: a K-menu tab (holo, §7.3), `living.quest.client.JournalPage`; Horizon: one `HorizonHooks.Source` for the
  tracked step (raised); arrival within 32 blocks pings the band and shimmers the target once.
- Bounties: `living.bounty`, templates in data, three notices per player for their level and rank, claim once;
  coin through `EconomyHooks.pay` once Carillon's economy exists, essence and XP until then; renown via
  `RenownHooks.witness` (the quartermaster witnesses the turn-in).
- World events: `living.event`, scheduled by play-day per region near players, announced only when you're near or a
  rumour reaches you (`WorldHooks.landmarkRumour` style through Mira).
- Multiplayer: steps count for a party within 48 blocks (`PartyHooks`); relics and claims per player.

---

## 3. Phase D · The world is alive: the World Pulse, happenings, creatures and camps (concepts 05, 06, 09, 12)

### 3.0 The World Pulse

One director for everything that happens in the open world, so the cadence is designed rather than random: today's
anomalies (rifts, processions, wandering gates, stars, groves) become happenings under it, beside the new ones.

- **Around each player** (merged when players are near each other) it keeps a liveliness target: one happening with a
  visible tell within ~256 blocks most of the time outdoors, at most three active near you, a new kind you haven't
  witnessed every ~15 minutes of exploring new land during the first ~30 hours (it reads the Director's novelty
  memory), quieter inside a Hearth's radius (your village has its own life, §6), nothing in gates.
- **The moon calendar:** vanilla's 8-phase moon drives the rhythm: full moon the Wild Hunt and wolves, new moon the
  Deep Tide (nests surge), the half moons caravan days and festivals. Weather drives storms and blooms; time of day
  drives murmurations at dusk and migrations at dawn.
- **Tells are seen from afar:** each happening has a tell drawn on the client at a clamped distance like the gate
  pillars (a dust haze, a smoke column, lantern lights, a storm cell, a circling flock), from one compact record (kind,
  position, intensity) the server sends to players within 512 blocks, every 2 s when changed.
- **Missable and world-real:** happenings are world state (patent rule: consequences go to world state), shared in
  multiplayer, and end on their own. Missing one leaves a trace (tracks, a trampled ford, a burnt-out camp) and a
  rumour.
- **Rumours** are how you stumble on more: Wayfarers, Carillon's taverns (its gossip), Pell's Runner post at your
  village and Mira tell of happenings, wonders and legends in named regions ("A thousand stags crossing the Rimewold
  ford at dawn, they say."). A rumour puts a faint "?" on that region of the Atlas. Never a list.
- **The Almanac:** the first time you witness each kind of happening is a discovery (0.1 of a level, "Witnessed · the
  Great Migration"), recorded in the Codex with a count ("Happenings witnessed 7 / ??", the Codex's own rule).
- Data-driven: `data/awaken/awaken/happening/*.json` (where: band, biomes, terrain; when: time, moon, weather; tell;
  duration; participants; outcome; rewards) plus a small behaviour class per kind.

### 3.1 The happenings (launch set)

| Family | Happening | Tell from afar | What you can do |
|---|---|---|---|
| Creature life | **The Great Migration** | a dust haze and a low rumble over a valley at dawn, birds wheeling above | ride along on your mount, hold the ford against the packs, hunt the stragglers, meet the beast that walks with it; the herd's home moves on to the next region |
| | **Stampede** | thunder of hooves, a dust line | get clear (it hurts), or turn it into your enemies with the Herdcaller Horn |
| | **Murmuration** | a living cloud of finches or petalwings over a lake at dusk | watch it: that night's dreams start more lucid; sometimes a white finch's feather falls |
| | **Spawning Run** | silver splashing along a river, bears and kites gathering | fish (catches ×3) beside the Honey Bears, who don't like being crowded |
| | **Beast Duel** | two roars and falling trees at a region border | watch, then finish the weakened winner, or tip the fight; both parts drop |
| | **Carrion Circle** | kites circling high over one spot | find what died: a beast's carcass and its part, a fallen hunter's last log and gear, or a lure |
| | **Egg Season** | a cliff loud with calls | climb for eggs with the rope while the parents dive at you |
| | **The Wild Hunt** (full moon) | a ghost-blue light and howling moving across the land | hide, follow at a distance, or fight a beast and its pack for the best night of loot in the game |
| Sky and land | **Starfall** | streaks across the night, then several craters | a shower of fallen stars over one region |
| | **Aurora night** (cold regions) | curtains of green and violet | spirits show without the Veil, dreams run lucid, Veil-only paths open |
| | **The Rolling Storm** | a dark cell with lightning crossing the plains over minutes | Charge powers grow stronger inside it; struck ground leaves charged glass; storm fishing |
| | **Signal Storm** | the sky fractures in the Signal's colours | where the last bolt lands, a gate forms |
| | **The Deep Tide** (new moon) | grey fog fingers creeping from old break sites at dusk | nests surge (raids likelier), the night runs two levels harder, blight creatures walk |
| | **Bloom** | a whole meadow region turns to flower for a day | Foraging pays double; the Empress Moth wanders out |
| | **The Opening** | a dust plume and a boom | a sinkhole opens in wild ground into a cave hollow with ore and a den (never near a Hearth) |
| | **Eclipse** (rare, from rank A) | the sun goes dark for minutes | every gate flares, the Hollow Court whispers (lore), hostiles walk by day |
| People | **Caravan** | lanterns and smoke moving along a road | trade, escort it, or defend it from an ambush |
| | **Travelling Merchant** | a lone painted cart | rare stock for a day: recipes, treasure maps, mount gear |
| | **Rival Hunters** | three hunters' campfire near a beast's lair | race them to the beast or join them and split the loot |
| | **Ambush** | smoke and the clash of a fight | the Unbound or riftborn hitting a caravan or a camp |
| | **Refugees** | a line of walkers with carts | people fleeing a gate break; escort them to Carillon or your village, where they may stay (§6) |
| | **Festival** | lanterns and music at a Wayfarer camp at night | the wilds' own Bell Festival: a climbing pole, an archery match, a race, a feast |
| | **Pilgrims** | robed walkers with bells on a path | follow them and they lead you to a wonder |
| | **Runners' Relay** | a flare passed from rooftop to ridge | join the relay: a movement run across the region for the Runners' Guild |
| The Signal | **A ruin wakes** | bells ringing from a Precursor ruin at night | its machines light for one night: a power puzzle that opens a sealed room |
| | **Monolith beam** | a beam from a monolith at dusk pointing across the land | it points at one of the region's secrets |
| | gates forming and breaking, rift tears, spirit processions, shimmer groves, fallen stars, wandering gates | as today | as today, scheduled by the Pulse |

### 3.2 Creatures live somewhere: what the player sees

- **Herds** graze between pastures on trails you can see in the grass (Frost Stags, Mossbacks, Crag Rams, Dune Rays,
  Honey Bears): the same herd is in the same valley tomorrow, a little further along.
- **Packs have dens** (Wisp Hounds, Gloomstalkers, Canopy Prowlers, Thornlings): by day they sleep there (raid a den
  by day, or follow them out at dusk); by night they stalk the herds, and you find what they left. Kill a pack and the
  valley's herd grows.
- **Flyers roost on cliffs** (Peak Kites, Hollow Owls, Frostfinch flocks) and leave at dawn or dusk.
- **Hostiles come from places:** blight nests left by broken gates, riftborn camps (gate creatures that got out),
  hollow dens at cave mouths, ruins whose machines wake. Clear one and it stays clear for days (ash, then green again);
  an ignored nest grows a tier every 2 play-days while players are around, and at tier 4 it raids the nearest
  Wayfarer camp, outpost or Hearth at night, warned a play-day ahead like a gate break.
- Vanilla's night stays dangerous: zombies and skeletons still spawn in the dark anywhere (at the region's level).

### 3.3 The model (cheap by design)

- **A ledger, not entities** (Carillon's citizens pattern, measured at p95 0.015-0.04 ms): per 512×512 region,
  generated lazily the first time a player enters it from the world seed, at most 6 population records (herd, pack,
  roost) and 2 camp records. Record ≈ 40 bytes: species, home, route seed, count, last play-day, tier, cleared-until.
- **Deterministic movement:** a herd's position is a pure function of its route seed and the time of day and play-day
  (pastures picked from grass-rich spots at generation), so nothing simulates while nobody watches. Packs' routes trail
  their herd's at night and return to the den at dawn.
- **Bodies near players only:** within 96 blocks a record materializes its bodies (herd ≤ 12, pack ≤ 5, roost ≤ 6,
  camp ≤ 10), past 128 they go back into the count. Per player ≤ 24 ledger bodies, outside the fauna category's caps;
  herd species leave fauna's natural spawn lists in Living mode (ambient creatures keep natural spawning).
- **Catch-up, not ticking:** when a region is next visited, counts regrow by play-days elapsed and a pack's hunts are
  settled in one step (each night a pack hunted, the herd lost one; tracks and a carcass mark the last one).
- **Camps from causes:** a gate break left uncleared for 2 play-days seeds a blight nest at its site (the blight ring is
  already there); riftborn camps and hollow dens are seeded in the region record (level 8 and up). Tiers 1-5:
  +2 levels, +1 wave and a bigger build per tier; the heart is the camp's boss.
- Budgets: ledger work ≤ 0.3 ms per tick, sliced 16 records a tick; one region generation per tick at most; camp
  builds through the per-chunk painter pattern (`Sink`), ≤ 4 light sources per chunk; saved data
  `awaken:living_world` (schema 1), only visited regions.

---

## 4. Phase E · Crafts (concept 08)

Six crafts that level as you do them, each levels 1-50, with perks that change how you play survival. Their XP also
feeds the Hunter level (through their budgets, §1.3), so a sheep finally leads to something.

| Craft | Levels from | Perks (5 · 10 · 20 · 30 · 40 · 50) |
|---|---|---|
| **Hunting** | any kill, a first kill per kind, skinning drops, tracking to a den | field dressing (+1 hide chance) · **tracks** (a creature you've sighted leaves glowing tracks on the Holo for 30 s) · weak spot (+15% crit on kinds you've killed 20 of; the plate marks it) · stalker (herds don't bolt from a sneak) · trophy (beast parts ×2) · apex (a beast's plate shows its next phase) |
| **Foraging** | berries, mushrooms, flowers, wild herbs | **herb glint** (ripe herbs glint within 16) · wild larder (+1 berries and mushrooms) · root sense (hidden roots show) · seed keeper (rare seeds) · bloom (Bloom Sprites follow you) · wildheart (regeneration in forests) |
| **Mining** | ores, Precursor alloy, deepslate | clean strike · **ore-sense** (the band pulses every 4 s while you face stone with a pick: ore within 6 blocks shows through it as projected glass) · vein-sight (the whole vein outlined) · deep ear (hear caves behind walls) · alloy from deepslate · heart of stone |
| **Fishing** | catches, Tidewater creatures | calm water (bites sooner) · shimmer (rare catches glint on the water) · Tidewater bait · pearl diver · leviathan line · storm caller |
| **Smithing** | Forge tempering, Hunter gear, repairs | patch (cheaper repairs) · temper discount · field forge (repair at a campfire) · masterwork chance · sigil slot · signature (your name on what you make) |
| **Cooking** | campfire, smoker and furnace food, herb meals | hearty (+saturation) · **campfire feast** (a meal shared with your party) · hunter's stew · long meals · spirit tea (energy regeneration) · banquet |

- Curve `craftXpToNext(c) = 40 c^1.3 + 40`; craft actions pay craft XP through the same per-source budgets, so AFK
  farms level off. Wild herbs are not new blocks: a natural plant is "ripe" by a pure function of its position and the
  play-day (no block entity), shown by the glint and checked by the server on harvest. New items: six herbs, about
  eight meals with buffs.
- Saved in its own attachment `awaken:crafts` (schema 1, kept through death, synced to the owner). K-menu tab
  "Crafts" (holo, §7.3). A craft level is a toast with its icon; a perk is a toast and a line in the tab.
- Perk looks are Awaken's own: the herb glint a thin vertical shimmer in the Horizon's sea-green, ore-sense a band
  pulse and glass outlines (server-sent positions, like the Veil's ore glow), tracks glowing pawprints that fade.

---

## 5. Phase F · Wonders, secrets and legends (concepts 11, 12)

Always somewhere new, in every direction.

- **One wonder per region**, chosen from an authored library of about forty by the region's land (some are one per
  world): **the Frozen Gate** (a break caught in ice mid-spill, monsters frozen in the air), **the Bell Tree** (an
  ancient tree grown round a Precursor bell; ring it once a day), **the Glass Sea** (a desert of fused glass where a
  wyrm sleeps), **the Upside-down Falls** (water rising into the sky; the Veil shows why), **the Giant's Arch** (a
  skeleton spanning a valley; climb it), **the Singing Canyon** (the wind plays the Choir's notes; stand in the right
  place at dusk for a vision), **the Sunken Spire** (a Precursor tower tilted into a lake), **the Statue Field**
  (hundreds of stone figures, the Sleepers' kin), **the Petrified Herd** (a migration turned to stone mid-stride),
  **the Star Crater** (a lake in an old impact, glowing at night), **the Chain Bridge** (a broken Precursor bridge over
  a gorge, a rope route), **the Mirror Lake** (it reflects a different sky). Each is a structure placed by worldgen
  (warded, like the ruins), a discovery (0.15 of a level), a Codex page, a unique resource or effect, and usually a
  quest hook or a happening of its own.
- **Region secrets:** each region hides three from a library (a cairn over a buried cache, a cave behind a waterfall, a
  Precursor log, a puzzle shrine, a spirit's grave, a legendary fishing spot). Never listed: the Atlas's region card
  says "Secrets found 1 / ??".
- **Legendary beasts:** eight unique creatures, one of each per world, roaming a band of regions: **Old Greymane** (a
  white stag the size of a house who walks with the migrations and leads the Wild Hunt under an aurora), Mother Thorn,
  the Glass Wyrm, Hush (a silent hound that hunts only in auroras), the Kingfisher, the Lantern Queen, Stonefather,
  the Drowned King. Rumours precede them. Fell one and it's gone from that world for good (every player hears); some
  can be calmed instead (Old Greymane with the Herdcaller Horn blesses your mount). Trophy, relic, title.
- **Treasure maps** from ruins, camps and merchants mark a cache somewhere in a region with terrain clues, never a
  pin.

## 6. Phase G · Your place in the world (concept 10)

Build wherever you like, the Minecraft way; the living world comes to you.

- **Settlers:** Wayfarers whose chains you finished can move to your Hearth, each needing a room the Hearth recognises
  (beside the townsfolk who come from Carillon at Trusted renown): Ilse keeps an herb garden and teaches Foraging, Hettie
  tends your animals, Gus carves horn and mounts trophies, Pell runs a Runner post (letters and a rumour every
  play-day), Ama Voss keeps an archive desk (reads relics and logs), Wren strings rope lines round your village, a
  caravan master opens a market stall. Refugees you escorted can stay too.
- **Your village has a name** you give it at the Anchor; it shows on the Atlas as a settlement and greets you with its
  own place caption ("HOLLYFORD · your village").
- **Roads:** place road markers (a craftable post) from your village toward a Wayfarer camp or Carillon; once they join
  up (each within 64 blocks of the next), caravans travel that road every few play-days, bringing trade and news, and a
  caravan on your road is worth defending.
- **The world passes through:** migrations can cross your fields (fences turn them), refugees knock, a festival comes to
  your village once four settlers live there, and a nest that grows nearby raids at night with a play-day's warning
  (your Echoes on Guard, settlers take shelter). The Hearth keeps its land at your level (§1.2).

---

## 7. The first hour, survival, ownership, tests, risks

### 7.1 The first hour (with Carillon's onboarding)

1. The Registry walk ends on the balcony; Bram's pan frames the High Gate. Proposed change to one line (Carillon's
   call): "That's the High Gate. The Commons start past it. Hale at the gatehouse has work for new licences."
2. Outside the High Gate: the gatehouse yard. **Quartermaster Hale** and the bounty board: three first notices
   ("Cull four Gloomstalkers", "Bring six Mossback moss", "Find the Wayfarer camp on the north road").
3. The Commons: creatures at level 1-3 near the wall; Mossbacks and stags by day, Gloomstalkers at dusk; a Briar Sow
   lair at the Commons' edge as its boss.
4. The Wayfarer camp ~300 blocks up the road: Ilse (Moth Lights), Hettie's farmstead nearby (Ten Sheep Short).
5. You reach level 3 at ~27 minutes and 4-5 by 40-55. **The Director's first gate opens at level 4, or after 45
   minutes outside**, in the Commons' outer edge (Carillon worlds; classic worlds as today).
6. Tips through Carillon's `TipHooks` (never a second tips UI): the first plate ("Your band reads what you look at"),
   the first region caption, the first bounty, the first Wayfarer sign, the first craft level.

### 7.2 Survival stays the core

Hunger, building, crafting and dangerous nights are unchanged; vanilla animals farm as ever. Regions change levels, not
what lives where. Quests are found, never assigned; nothing is listed before you find it. World option **Pure
survival**: no plates, damage numbers, XP stream, quests or bounties (regions, crafts and their perks stay: they're the
world and survival skills).

### 7.3 Ownership, packages and APIs

New module **`living`** (package `dev.awaken.living`; same module rules as §1): `living.region`, `living.combat`
(+`.client`), `living.wayfarer`, `living.quest`, `living.bounty`, `living.pulse` (the World Pulse, happenings, rumours, the
Almanac), `living.population`, `living.camp`, `living.craft`, `living.wonder` (wonders, secrets, legends, treasure
maps), `living.village` (settlers, the village name, roads, caravans); commands `/awaken living ...`; payloads `living:*`; merge folder `src/main/merge/living/`.

New public API (SPEC §12):
- `dev.awaken.living.api.Regions`: `levelAt(Level, x, z)`, `regionAt(Level, x, z)` (id, name, band, span),
  `creatureLevel(Level, pos, role)`,
  `bandOf(level)`.
- `dev.awaken.living.api.Quests`: `active(Player)`, `tracked(Player)`, `advance(ServerPlayer, Identifier objective,
  Object detail)` (other modules report ability uses, e.g. a zip, a Veil entry, a dream).
- `dev.awaken.living.api.Crafts`: `grant(ServerPlayer, Craft, double)`, `level(Player, Craft)`, `has(Player, Perk)`.
- `dev.awaken.core.api.LivingHooks` (install-once): `regionLevel`, `onKill` data for modules that don't depend on living.

Changes in existing modules (each small, at the seam):
| Module | Change | Owner to agree |
|---|---|---|
| progression | kill XP by creature level and role, new budget table, `XpSource` HUNT/CRAFT/QUEST, gate SHARE trim, Director's first gate at level 4 (Carillon worlds) | orchestrator |
| world (fauna), mobs | content level from `Regions`; elite variants; herd species leave natural spawns in Living mode; beasts re-level at lairs | orchestrator |
| holo | a small public world-space glass API (`HoloWorld.plate(...)`), two K-menu tabs (Journal, Crafts) through a tab-provider hook | pack session (holo owner) |
| compat (Jade) | hide Jade's creature lines while the plate shows them (one source of truth) | orchestrator |
| wilds | none inside it: the tracked quest is a `HorizonHooks.Source` | Ascent session (informed) |
| Carillon (city, citizens, opening, atlas, economy, renown) | the gatehouse yard and Hale, Bram's line, bounties on the contracts board, Atlas region layer (`AtlasHooks`), coin and renown through their hooks, TipHooks | Carillon session |
| world (anomalies) | today's anomaly kinds become happenings scheduled by the World Pulse (their behaviour stays; the scheduler moves) | orchestrator |
| hearth | settlers as townsfolk with rooms, the village name and its caption, road markers, raid warnings, a festival | orchestrator (hearth) and Carillon |
| terrain / world (worldgen) | wonder structures and their sets, placed one per region through the region map; region secrets | orchestrator (terrain) |

### 7.4 Tests (one per mechanism; run with `-Ptests=living_`; CI runs the full suite)

| Phase | Test | Checks |
|---|---|---|
| B | `living_region_map` | the Commons and its neighbours, the odds by distance over many regions, every 3×3 block fair, biome tilt, edge-to-heart curve, Hearth calm, classic origin |
| B | `living_kill_xp` | on-level, above (+40% cap) and below (taper) through the budget; a sheep pays; party share |
| B | `living_plate_rules` | which creatures get plates, the 4-plate cap, fade timings (pure rules) |
| C | `living_quest_engine` | a data chain runs start → steps → reward once per claim id; relog keeps it; a tier-gated step waits |
| C | `living_bounty_board` | three notices for level and rank, refresh next play-day, claim once |
| C | `living_scene_finder` | a cliff scene and a road scene find valid spots in a real world, never in a Hearth, ward or the city |
| D | `living_population_ledger` | deterministic route, bodies within 96 and gone past 128, a kill lowers the count, regrowth and pack catch-up by play-days |
| D | `living_camp_lifecycle` | a break seeds a nest, clear → stays clear N days, ignored → grows → raid warned a play-day ahead |
| D | `living_pulse_cadence` | over simulated hours round a moving player: a tell within 256 blocks most of the time, ≤ 3 active, a new kind every ~15 min early on, quiet in a Hearth, none in gates, the moon calendar (pure scheduler) |
| E | `living_craft_xp` | an action pays craft and Hunter XP through budgets; perks unlock at tiers |
| F | `living_wonder_placement` | one wonder per region in a real world, in fitting land, warded, one-per-world kinds unique |
| G | `living_settlers_and_roads` | a finished chain's Wayfarer settles into a recognised room; joined road markers make a caravan route |
| all | `living_pure_survival` | the option removes plates, numbers, quests and bounties; regions and crafts stay |

Plus the pacing check in tools/sim (the balanced player's targets hold). One `slot.sh light` tour per phase for
screenshots (plates in a fight by day and night, the level moment, a region caption, a scene, a herd and a nest), and
from Phase D a **Hilltop tour**: twenty random hilltops by day and night, judged against the Hilltop Test.

### 7.5 Risks and performance

- **Frame budget:** plates (≤ 4 glass quads with text), glyphs (≤ 24), XP motes (≤ 64), pillars: target ≤ 0.3 ms a
  frame total; they also draw in gate fights, where SPEC's p50 ≥ 110 fps must hold.
- **Server:** region lookup O(1) and allocation-free; one attachment sync per spawn; two tiny event payloads; ledger
  ≤ 0.3 ms/tick sliced; quests and crafts event-driven. Tick p95 ≤ 25 ms holds.
- **A hard region next door:** a level-30 valley can sit two regions from the wall. The caption turns ember, the
  band warns, plates show skulls, and the Atlas marks it: a place to come back to, not a trap. The Commons' neighbours
  never exceed 20.
- **Bases in hard regions:** the Hearth calms its land.
- **The Pulse:** ≤ 0.5 ms a server tick in all; tells are client-drawn from tiny records; happening entities only within
  96-128 blocks (the ledger's rule). Happenings never change terrain within a Hearth's radius plus 96.
- **Worldgen:** wonders are structures with their own sets placed by the region map; new chunks only, like every ruin.
- **MMO creep:** found-never-forced scenes, no markers, the Journal limited to what you took, Pure survival.
- **Two sessions near the same code:** Carillon (the gate yard, Bram, the board, the Atlas) and the pack session (holo
  API) are asked first; the wilds module is untouched.
- **Old worlds:** regions apply to new spawns; existing persistent beasts re-level when their lair wakes them.

### 7.6 Order and size

One builder at a time per phase, Opus at extra-high effort for the look (the combat layer, scenes, the level moment)
and the quest writing, Sonnet subagents for data, templates, spawn tables, tests and docs.
B (combat layer, regions, XP) → C (Wayfarers, quests, bounties, rumours: the eight chains for levels 1-20 first, then
the twelve) → D (the World Pulse, happenings, living creatures, camps) → E (crafts) → F (wonders, secrets, legends) →
G (settlers, your village, roads). Each phase pushes to pack/living-wilds for CI and goes to the
orchestrator with its CI run id.

### 7.7 What opens when (always more to unlock; balanced player, pacing_sim hours, approximate)

| When | What opens |
|---|---|
| 0 h | the Commons, plates and XP, Hale's first bounties, the Wayfarer camp, first creatures, first happenings (a murmuration, a caravan) |
| ~0.7 h | the first Director gate (level 4) |
| 1-2 h | the first chains (The Last Delivery, Ten Sheep Short, Moth Lights), the first regions past the Commons, a beast's lair |
| 2-5 h | the rope at the first vault: cliff rescues, spires, egg-season climbs |
| ~4 h | mounts (level 11): ride with migrations, escort caravans, The Crossing |
| ~5 h | D rank: harder regions, camps and nests, Wild Hunt nights worth fighting |
| ~6 h | the first wonder; the first settler at your Hearth |
| ~8 h | the Veil (level 15): aurora paths, spirit chains, the Keeper's Lantern |
| 12-18 h | craft perks (tracks, ore-sense), legendary beast rumours, caravans on your road |
| ~18 h | C rank: Deep Tide nights, Bell Metal, the Weaver, Mire rescues by glide |
| 30-55 h | wings and flight: sky happenings, the Relay Spires, the far wonders |
| 55 h + | B rank and beyond: regions of 80+, the oldest nests, eclipses, the last chains |

### 7.8 Decisions for Euan (each with a recommendation; "go with your recommendations" says yes to all)

1. Regions replace follow-you scaling for every overworld creature; distance only tilts the odds (§1.2). **Yes.**
2. The Hearth calms its land. **Yes.**
3. XP rebalance: every creature pays by its level, crafts, quests, bounties and camps pay, gate floors ×0.76. **Yes.**
4. Plates, damage numbers and the XP stream on by default, with settings. **On.**
5. Level-up as a System moment, seen by others nearby, compact in a fight. **Yes.**
6. Elites in the wild (5%). **Yes.**
7. The Journal amends "Never a quest list": only chains you found and took. **Yes.**
8. Twenty hand-written chains in two drops; relics bound to you. **Yes.**
9. Bounties as the first slice of Carillon's contracts board. **Yes, agreed with Carillon.**
10. Creatures in a ledger (herds, packs, roosts, camps); ignored nests raid with warning. **Yes.**
11. Six crafts with perks and a Crafts tab. **Yes.**
12. Jade hides creature lines while plates show them. **Yes.**
13. Pure survival world option. **Yes.**
14. The first hour: first gate at level 4 or 45 minutes outside; Hale and a Wayfarer camp in the Commons. **Yes.**
15. The Nether and End keep vanilla danger for now. **Yes.**
16. The World Pulse: one director for every happening, the Hilltop Test as the bar, the moon calendar, missable
    happenings, today's anomalies folded in (§3). **Yes.**
17. Wonders (one per region), region secrets, eight legendary beasts that can fall for good, treasure maps (§5). **Yes.**
18. Your place in the world: settlers, a named village on the Atlas, roads that caravans travel, warned raids (§6).
    **Yes, agreed with the hearth and Carillon owners.**
19. Rumours and the Almanac: witnessing a happening kind the first time is a discovery; rumours mark regions with a
    "?" (§3.0). **Yes.**

