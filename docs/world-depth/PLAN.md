# Between the Gates: depth for the time you're not in a gate (plan for Euan's approval, 2026-10-26)

Lead designer's plan, built from code audits of the build Euan plays (`build/awaken-v1` c7846bc2), 55 + sourced
research entries on open worlds and Minecraft mods, and The Way Forward (`docs/progression/PLAN.md`, approved
2026-10-26). Approval page: https://claude.ai/artifact/ATZfZSczLg95kNSgnLF42M (source `docs/world-depth/page.html`).
**Approved in full by Euan on 2026-10-26** ("Go ahead with all recommendations": all thirteen decisions). SPEC §28
holds the decisions and which phase owns which files. W1 and W2 started the same day as two builders.

Supporting files (this folder):
- `audit-hearth.md`: when and how a player gets a Hearth, room detection, tiers, the town, Echoes, requests.
- `audit-villages.md`: vanilla villages today, Carillon's people engine and grammar, the people of the wilds.
- `audit-mining.md`: every ore and where AWAKEN uses it, ore generation, mining XP and crafts, tools, the gear ladder.
- `audit-caves.md`: overworld caves, life underground, the Descent, every move in rock, Living Wilds F and G.
- `research.md` (index and principles), `research-games.md` (G1-G55), `research-mods.md` (M entries).
- `concept/01-09`: concept frames for the page (generated, our own; no Mojang assets).

---

## 0. What Euan said, and the one-line answer

> "I'm just really trying to massively level up the experience between the gates [...] I still want it to feel like
> Minecraft, and for the things that you do in Minecraft to have sense and an objective and reasoning for doing them."
> · "Every village uses the same logic that we built the city with [...] sometimes you go to a village and someone
> comes to you [...] this person got kidnapped and taken into a lair of this beast." · "Why would I go mining? [...]
> Diamond swords are probably the most boring weapon in the game." · "When you build your base, it kind of plays into
> the system of the hearth." · "Maybe inside of caves sometimes you have to use your parkour skills." · "I don't want
> fluff."

**The answer.** The gates give you power. The time between them should give you **a life**: a home that reads what
you build, villages that live on the city's own logic and need you, and a deep underground that holds the metal
everything is made from. Every ordinary Minecraft verb (dig, build, farm, trade, fish, explore) feeds one of those
three, the movement system is how you get through all of them, and each one you touch changes something you can see.

**Does this go with The Way Forward?** It's its own plan, joined at three seams: the first thread ends at the
Wayfarer camp, which is where your Hearth starts (4.2); locks you meet underground and in villages become The Way
Forward's promises (11.1); and this plan is most of what fills The Way Forward's "long middle" (its Phase 5).

---

## 1. The audit: what's between the gates today

Full tables with `path:line` in the four audit files. The short version:

| Area | What the code does | What's missing |
|---|---|---|
| **The Hearth** | Needs a Lord's Heart: 1-2 h in, after the first Gate Lord. Nobody in the world mentions it (the recipe book and a level-6 card do). Founding stamps a prefab Spire into nine of your ground blocks. A room is a furniture checklist with no minimum size. Upgrades change no blocks. Townsfolk wander within 20 blocks of the Anchor with no homes, beds or work | Any reading of quality: light, size, materials, windows, warmth, comfort (`Room.volume()` has no caller). Any reason to build well. A start that ties to leaving the city. The Workshop room gives nothing; the Mine Echo rolls dice |
| **Villages** | Untouched vanilla villages. Frost Spires has wiped out most snowy ones; nothing keeps one out of Carillon's basin. No trade, raid or villager code. No coin anywhere (`EconomyHooks` never installed) | Everything Euan asked for |
| **The city's people** | A good, cheap, generic engine: 468 people for p95 0.06 ms a tick, ≤ 96 bodies, days as segments of acts at places, a street graph, jobs that change the world, speech, recognition and gossip, a fire with a bucket line and masons who repair | It's one static singleton for one plan made offline in Python. The grammar fills plots but can't lay out a settlement. No building has an interior except Oren's cottage |
| **Mining** | Gates, chests, a market (a diamond for 5 essence) and the Mine Echo hand out every ore. A diamond pays the XP of a lump of coal. The Mining craft can't really be levelled by mining (Ore Sense is ~25-38 h away) and ignores AWAKEN's mattock. The pickaxe ladder was removed on purpose | A job for diamond, lapis, emerald; any AWAKEN ore; any feel (no sound, particle, hit or reveal on a strike); diamond armour quietly beats C-rank Gatewrought and nothing says so |
| **Caves** | Vanilla 26.3's caves (lush, dripstone, deep dark, the new sulfur caves; Tectonic's rivers and lava tunnels). One AWAKEN place: the Crystal Nest, sealed, no tunnel in, no depth on its Now line. Hangs, drops, mantles, slides into crawlspaces, vine climbs and the Tether (any block, falls free) all work in rock | Places to walk into; anything living below the surface; a cave sound or light; wall-run walls (they need flat square faces); a rappel; a way home; any link to the Descent |
| **Living Wilds F and G** | Not built: no `living/wonder`, no `living/village` | Wonders, secrets, legends, maps; settlers, your village, roads |

---

## 2. Research (summary; sources with dates in `research-games.md`, `research-mods.md`, index in `research.md`)

| Principle | Best source | What it means here |
|---|---|---|
| Praise lands on places and small stories; blame on repeated quest verbs | Crimson Desert reviews, Mar-May 2026 (G2) | every trouble has its own verb, never "go, kill, come back" |
| The reference game is faulted for too many systems at once | Game Informer, Gamer Headlines, 2026 (G7) | new verbs arrive one or two at a time (10.2) |
| An event that can undo a place needs real pay and an off switch | Crimson Desert Re-Blockade, patches 1.05-1.10, 2026 (G4) | troubles always pay; a "calm villages" world option |
| Fix the carry before adding ore | Crimson Desert patches 1.00.03-1.04, 2026 (G5) | lodes give lots at once; the Hearth's crafting pulls from nearby chests |
| A village is a set of claimed beds, job blocks and a bell | Minecraft Java villager and raid rules (G30, G31) | the Hearth reads the same points in your build |
| Comfort: a short public list, the best item per category counts | Valheim (G18) | the Hearth's Comfort and Rested |
| One key shows what a room is and what it lacks | Dragon Quest Builders 2 (G35) | the glance reads the room you stand in |
| The weakest stat counts most | RimWorld (G37) | a room's grade is held down by its weakest reading |
| Arrivals come from a deed, by a readable rule | Terraria town NPCs (G26), Dwarf Fortress nobles (G38) | settlers come when you've met them and have a room |
| A base ladder of people + a boss + an ore, each rung opening a place | Enshrouded's Flame Altar (G22) | Hearth tiers ask for settlers, a gate's Heart and a metal |
| Finding people across the map grows the town | BotW Tarrey Town (G9) | settlers are people you helped out there |
| One metal per place, and a reason to haul it home | Valheim (G19), Terraria pickaxe power (G29) | hide, metal, light (7.1) |
| Ore that buys the toys | TotK's Zonaite (G12) | metal fittings for the rope (7.5) |
| A chore earns its place only if it pays something you can't buy | The Outline on RDR2, 2019 (G15) | gates stop handing out ore |
| Caves from authored rooms strung together, each tested with every movement tool | Deep Rock Galactic (G45) | cave places as room libraries, tested with every move |
| The main path never needs the rope; the rope buys shortcuts and treasure | Spelunky (G46), Hollow Knight (G47) | every cave place has an E route |
| Build for the grapple, don't bolt it on | Sekiro (G48), Valheim 1.0 (G20) | the city's climbing grammar carried underground |
| One cave changes how you see every cliff; vary the last room | TotK (G10), Elden Ring (G49) | not a boss at the end of every cave |
| Systemic reactions need caps and cooldowns | Skyrim Radiant, Nesmith 2012 (G52) | ≤ 1 trouble per village, ≤ 2 near you |

### 2b. What mods, packs and Mojang show (`research-mods.md`, M1-M63)
| Finding | Best source | What it means here |
|---|---|---|
| **Nobody does it**: no mod, pack or Mojang feature has a village burn and be rebuilt over days, or someone taken to a lair and brought back, with the village remembering | M63 (searched across ~60 mods and packs) | troubles (section 6) are open ground |
| A village as a ledger, not a script: population, stock, projects, routes, caught up while unloaded; a board of wants; staged visible builds | Live Villages, 2026 (M5) | Carillon's engine already is that ledger; growth as staged builds (6.4) |
| Danger in readable phases: rumour, scouts, a bad night, the attack | Pueblo Vivo (M7) | the Burning's warning ladder |
| End every event on a persistent mark; a 40-minute buff is forgotten | vanilla's cure discount (M21), MineColonies' no-loss bonus (M1) | every trouble leaves a change in blocks or people |
| Tie settlement tiers to a mined material | MineColonies hut levels (M1) | Hearth tiers ask for a metal (4.8) |
| ~223 million vein-miner downloads: the repeated identical break is the boredom | VeinMiner, FTB Ultimine (M33) | keep the act, change the unit: the lode and its heart (7.3) |
| A short read-the-wall act with a loss if you're greedy | Cobblemon's mining minigame, 685k downloads (M34) | the vein gives; the cave-in is the greed risk (7.4) |
| Read the ground with a sense, not an x-ray | Geolosys and prospecting mods (M32) | the Tuning Fork's pitch (7.5) |
| A new ore earns its place with a verb and an address | Oreganized vs Mythic Metals (M35) | Deepglass (seeps, the Spirit catalyst), starmetal (fallen stars) |
| Five tests for a memorable cave: a rule, a resource, a sensory set, a story entrance, a route | synthesis of Alex's Caves, YUNG's, Spelunkery (M44) | every cave place passes all five (8.2) |
| Mojang's 2026 caves have a hazard and a rule (sulfur caves' gas and geysers over magma) | Java 26.2-26.4 (M23) | sulfur caves get vents; AWAKEN is on Mojang's own line |
| Don't build a mining dimension (all under 35k downloads); put depth under where people live | M36 | the overworld's caves, not a new world |
| Read a build by its contents with a short rule; never demand a builder | Confluence's Terraria housing (M49), MineColonies' contrast (M50) | the Hearth reads objects and space, never a schematic |
| "Kitchen sink" is the word for bloated packs; the cure is one loop every system feeds | pack reviews (M62) | hide, metal, light; home, people, below |
| Players pay for travel (Waystones 28M) but warping skips the world; roads with life on them are travel and story at once | M18 | roads in W7 carry caravans |

---

## 3. The idea: three places, one rule, one rhythm

- **Home** (section 4): the Hearth starts on your first evening, and from then on it reads what you build.
- **People** (sections 5-6): villages from hamlet to town, alive on Carillon's people engine, with troubles that
  change them for good.
- **Below** (sections 7-8): the deep, where every tier's metal is, and caves made for the way AWAKEN moves.
- **One rule**: *hide from the hunt, metal from the deep, light from the gates.* Your gear needs all three loops.
- **One rhythm**: one or two new things at a time, each found in the world, never a menu of systems (10.2).

**The test every feature passed** (and the ones that didn't are in section 9's cut list): *why would a player do this,
what does it give them, how does it feel the first time, and does it still feel like Minecraft?*

---

## 4. Home: the carried flame, your own ground, a Hearth that reads what you build

### 4.1 Why you leave the city
One line of fiction, said by Bram at the Registry and already true in code: **"The bells hold the city. Hunters hold
the wilds."** The Association houses nobody. A licence comes with a duty to keep a light out there, and a hunter's
Hearth really does calm the land round it (gates never form within 64 of one, camps never within 120, creatures level
to the household, happenings stay off it). Leaving isn't an errand. It's the job.

### 4.2 The Ember (minute ~12, dusk on the first day)
The Way Forward's first thread ends at the Wayfarer camp: Ilse takes Kestrel's letters. Then, as the light goes:

> "First night out? Take a coal from our fire. Carry it till you find your ground, then set it down. It'll know."

You get the **Hearth Ember**: a small iron lantern with a bell-gold coal in it, held like the Watch's lantern, its
light drawn on the ground round you (the city's drawn-light technique, `citizens.client.CityGlow`, no light blocks).
It lights your first night. The Now line says "Set your Ember where you'd live"; Next keeps the first gate. No timer:
you can carry it for days. One per player, kept through death; any Wayfarer camp gives another if it's lost. Classic
worlds: the Ember is part of the awakening's kit.

### 4.3 Setting it down
Use it on open ground (not the city reserve, a ward, within 64 of a gate, or someone else's Hearth). The coal sinks
into a ring of stones and takes. Warmth runs out across 24 blocks: the grass brightens a shade, embers lift, the
world's sound opens into a low hearth tone, fireflies at night. The band: **YOUR GROUND**, and a card to name it (a
name from the region's grammar is offered). From then on, walking onto your land shows **ASHFORD · your hearth**.

This is **Tier 0, the Hearthfire**: radius 24, respawn here unless you sleep elsewhere, rest heals, calm land, no
happenings or gates on it. No town, no Spire: the first nights are yours alone. Until your first room stands you can
lift the Ember and carry it on (choosing a place is part of the fun).

### 4.4 The first night
Minecraft's oldest instinct is walls and a roof before dark. The Hearth answers it: the first closed, roofed space in
the radius is named ("First shelter · rest heals here"), and from then on everything you build is read.

### 4.5 The reading: what the Hearth sees in a room
Measured inside the scan the Hearth already runs (`RoomScanner.build` visits every cell and wall face of a closed
room; light is one engine read per floor cell, inside the same 12,000-reads-a-tick budget). Six readings, 0-3 each:

| Reading | What counts | Never |
|---|---|---|
| **Closed** | walls and a roof (as today: doors, panes, fences, stairs and slabs close gaps) | — |
| **Light** | the share of the floor at light 8+ at night; windows (glass, panes) in the walls by day | a named lamp |
| **Warmth** | a lit fire in or beside it: campfire, fireplace, furnace, smoker, candles | — |
| **Room to live** | floor and height for what it's for (tall halls allowed: caps raised to 48 a side) | a fixed size |
| **Made with care** | the share of worked blocks (planks, bricks, stripped logs, cut or polished stone, tiles, glass), at least three different ones | a named block |
| **Detail and comfort** | trim in walls and ceiling (stairs, slabs, trapdoors, fences, walls, chains, lanterns) and soft things (carpets, banners, paintings, pots, candles, bookshelves) | a stack: the best of each kind counts (Valheim, G18) |

The **grade**: **Bare** (it's a room: it works), **Snug**, **Fine**, **Grand**, from the average of the readings but
never more than one step above the weakest (RimWorld, G37): a grand hall with one dark corner is Fine until you light
it. Grades never ask for a block by name; only furniture names a room's purpose.

The **name**: grade, main material and purpose ("the snug timber bedroom", "the grand stone hall"), or the words on a
sign you hang inside. The first time you walk into a room after it's recognised or regraded, its name shows as a quiet
caption (AMBIENT, through the Herald). The Hearth never toasts per block.

**Reading a room on demand** (Dragon Quest Builders 2, G35): inside your Hearth, The Way Forward's glance (hold the
band key) shows the room you stand in: its name and grade, the six readings as pips, and the one thing that would lift
it most ("windows on the north wall", "a fire"). That's the whole manual.

**Comfort** and **Style**: the home has a Comfort number (your best bedroom's grade, a warm hearth, a table and seats,
soft things, plants, and the land's features: a field, a pen, a garden, a lit yard at night, a wall; best of each
counts) and a Style in words ("timber and river stone") that settlers and visitors say back to you. Comfort sets
**Rested** when you wake in your own bed: 8 minutes plus 1 per point, energy regenerates a quarter faster and crafts
learn a quarter faster. You feel it, and it never gates anything.

### 4.6 What rooms are for (each one does something real)
| Room | Furniture in it | What it gives |
|---|---|---|
| Shelter | closed and roofed | rest heals; your first night |
| Bedroom | a bed | yours: Rested on waking; a spare one: someone can move in |
| Sanctuary (today) | bed + Meditation Mat | Fracture cleared on sleep, dreams (as today) |
| Kitchen | furnace or smoker, crafting table, barrel | meals made here last half again as long |
| Forge (today's Armory) | anvil, Forge, armour stand | the temper cap (as today); Oren works here |
| Study | lectern, 6 bookshelves | Blueprints for half the paper; Teodor moves in; identifying items |
| Trophy Hall (today) | trophies | as today |
| Workshop (today: nothing) | a machine | machines spend a quarter less essence |
| Barracks (today) | a role pad | as today |
| Stable | hay, a fence gate | your bonded mount comes home here; the whistle reaches twice as far in the region |
| Greenhouse | farmland or pots under a glass roof | herbs ripen three times as often inside |
| Hall | a big floor, six seats, a table | a meal eaten here feeds everyone in the hall; your festival comes here (6.4) |
| Bell tower | a bell, under a roof, 8+ above the ground | your own bell: rings at dawn and dusk; breaks within 256 warned two play-days early; Deep Tide nights stay calm on your land |
| Lookout | 12+ above the ground, windows on three sides | the Atlas fills in 256 round your home; one more rumour a play-day |
| Mine head | a shaft 16+ deep from its floor, with a ladder or rail | a miner can work it (7.7) |

The guide shows the rooms you have and at most three suggestions from what you've built and who's waiting for one.
Unbuilt kinds sit as one dim line each, never a wall of cards. Quality of life (G5, G24): crafting at your Hearth draws
from chests within 8 blocks.

### 4.7 People who live with you (absorbs Living Wilds Phase G's settlers)
Someone moves in when three things are true (Terraria's readable rule, G26): **you know them** (you finished their
chain, rescued them, reached Friend in their village, or Trusted in Carillon), **a spare bedroom of Snug or better
stands**, and **their trade has its room**. They walk up the road with a bundle on their back, take the room ("Ilse's
room"), and live a day on the city's people engine: work in their room or your fields, eat in the kitchen or hall,
sleep in their bed. Today's townsfolk keep their ranks (Oren E, Mira D, Teodor C, Sun B) but now wait for a bed
("Oren will come when there's a bed for him").

What they do with what you bring home (Euan: "you bring back materials and you give them to workers"):

| Who | Their room | What they do |
|---|---|---|
| Oren | Forge | tempers (as today); **commissions**: hand him the metal and the hide and he forges the piece overnight, a temper up |
| Hettie | a bedroom by your pens and fields | tends crops (harvest and replant into the nearest chest) and keeps two of each animal breeding |
| Ilse | Greenhouse | brews tonics from the herbs you bring |
| a miner (rescued from a mining town) | Mine head | goes down your shaft by day; brings up ore from its depth each evening (7.7) |
| Mira | Lookout | rumours; the Atlas round home |
| Teodor | Study | reads relics and logs; identifies |
| Sun | a bedroom by a Sanctuary | heals |
| Wren | anywhere | strings rope lines between your roofs: your home becomes a course |
| Pell | a bedroom | a letter and a rumour each play-day; carries deliveries to villages you've helped |

### 4.8 Tiers grow from the home
Today's ranks and prices stay (they're the ore's job at home), each tier also asks for a home that has grown (people,
a gate's Heart, a metal: Enshrouded's ladder, G22), and **the Anchor's flame shows the tier** (a block state: today
upgrades change no blocks).

| Tier | Asks for | Radius | Opens |
|---|---|---|---|
| 0 Hearthfire | the Ember | 24 | respawn, rest, calm land |
| I Homestead | the Anchor (a Lord's Heart on the fire), a Snug bedroom | 48 | Oren, requests, the guide |
| II Steading | D-rank, four rooms (one Fine), a field or a pen; iron | 56 | two settlers |
| III Hall | C-rank, a Grand room; gold, amethyst | 64 | four settlers, sister homes |
| IV Village | B-rank, a bell tower, four settlers; diamonds | 80 | **named on the Atlas**, caravans on your road, your festival |
| V Town | A-rank, eight settlers; diamonds, obsidian, a Lord's Heart | 96 | the Spire's trials at home |

The Spire is no longer stamped into your ground: its Board and Monolith come as items you place where you like.

### 4.9 Fixes the reading needs (from `audit-hearth.md`)
Room caps (4096 cells, 32 a side) raised and the "too big" reason corrected; role pads placed before the Anchor
registered; the Anchor refused in the city reserve (today it founds a Hearth for a second); the Workshop gives
something; the reading saved, so a restart doesn't re-read every home at once.

---

## 5. People: living villages on the city's logic

### 5.1 One people engine, many places
Carillon's people engine becomes **one engine with many settlements**: a `Settlement` per place (its ledger, street
graph, saved state, work and events), every body carrying its settlement id, one world pool of bodies shared
nearest-first so a village never starves the city. Carillon becomes the first and largest settlement on it and
nothing it does changes. This refactor makes "every village uses the same logic we built the city with" literally
true. It's done with the Carillon session, which owns `citizens` and `city`.

### 5.2 Where villages are, and how big
**AWAKEN's settlements replace vanilla villages in new worlds** (vanilla's village structure sets are switched off by
the lands pack; old worlds keep theirs). One settlement plan per region from the region map, by its land and band:

| Kind | Homes / people | How often | Where |
|---|---|---|---|
| Hamlet | 3-6 / 8-16 | most regions have one or two | anywhere liveable |
| Village | 8-20 / 20-50 | about one region in two | rivers, plains, valleys, coasts |
| Town | 25-45 / 60-120, walled, a market and an inn | about one in five regions, never in the Commons | big valleys, river forks |
| Special | by land | rare | **a mining town** at a mountain's foot over its own mine; **a fishing village** on piers; **stilt houses** in the Mire (Living Wilds chain 12 wants one); **a frost village** of longhouses; **a caravanserai** at a Sunscorch oasis; **a woodcutters' camp** in Elderwood |

The first one a new player meets is **a hamlet 700-1,100 blocks from Carillon on the road past the Wayfarer camp**,
placed on purpose like The Way Forward's first gate: hour one shows the city, a camp, then ordinary people living
outside the walls.

### 5.3 How a village is made
1. **A planner in Java** (seeded, deterministic, no chunk loads: it reads the noise like the region map does) picks a
   site (gentle ground, water near, clear of the city reserve, wards and lairs), lays a road skeleton that follows the
   contours (a main street, lanes, a green with the well, the bell post and the notice board), packs plots along the
   frontages and assigns buildings by size and land: cottages, longhouses, two-storey houses, the smithy, the inn, the
   mill on water, barns, the granary, the bell tower, pens, strip fields, a dock, a mine head. It writes the same
   `Plot` and raster records the city's grammar reads.
2. **The city's grammar paints it**, with styles keyed by biome and wealth instead of Carillon's seven districts (the
   `Style` record already holds materials, courses, windows, roofs, chimneys, shutters, trim). Village houses are lower
   and plainer than Carillon's, with the same climbing grid (hoods, cornices, window boxes, drainpipes): a village is a
   small course too.
3. **Interiors, at last** (shared with Carillon's B4): a room filler that furnishes a house from who lives there: a bed
   per person, a table and seats, a hearth or stove, storage, the trade's blocks (anvil and forge, loom, barrels,
   lectern), lanterns, carpets. Every village house can be walked into. They're also good examples of the rooms your
   Hearth reads.
4. **Painted per chunk at world generation** like every ruin (a structure start; pieces ≤ 14×14 written straight into
   sections). The people start the first time a player comes within 256.

### 5.4 A village's day
The engine's segments, with village jobs added to the city's 21: farmer, shepherd, woodcutter, miner, fisher, hunter
(goes out at dawn and comes home with a carcass on a pole), carter, elder, plus the smith, baker, washer, lamplighter,
bellringer, tavernkeeper, mason, the Watch, healer and children. The bell rings dawn and dusk (Minecraft's own village
bell, G30). People walk to the fields, the mill, the woodlot and the mine, come home at midday, gather at the inn at
night; the lamplighter does his round; the Watch walks the edge after dark. Chimneys smoke when someone's home. You
first see it from a hill, and that's what makes it alive.

### 5.5 What you do there
- **Talk** (the city's speech pools, village texts): greetings by standing, news of the region, rumours.
- **Trade, in emeralds** (decision 5): each trade sells what its people make, and each region makes something of its
  own (frost villages furs and frostcap, the caravanserai glass and emberroot, the mining town ingots and lode maps,
  the fishing village fish and pearls). Your surplus (ores, hides, crops, fish, meals) sells for emeralds; emeralds buy
  commissions, maps, regional goods, a room at the inn and a stake in the village's growth. Prices show your standing
  (Terraria's visible price, G27).
- **Requests** on the notice board and from people: real needs from the village's state ("iron for the bell frame",
  "timber after the storm"), counts shown, paid in emeralds and standing.
- **Standing** per village: Stranger, Known, Friend, Kin (the city's recognition, per settlement). Friends hear the
  bigger troubles, get better prices, and can move to your Hearth.
- **A bed at the inn**: respawn there once, rest, the night's rumours.
- **A town scribe** sells enchanted books, so vanilla's enchanting loop survives the villagers' leaving.

### 5.6 Its state
Each settlement keeps (saved, versioned): its people, prosperity, mood, damage (burned houses with their original
blocks, as the camp painter already records), buildings added, each player's standing, its trouble, and a short
history the Journal shows ("Hollowmere: you brought Bren back from the Thousandfoot's nest").

---

## 6. Troubles: when a village needs you

### 6.1 The shape every trouble has
**A call** you see or hear from afar → **an ask** from a person (a choice card: Help / Not now) → **a place** (a lair,
a gate, a mine, a road, a tower) → **a deed** (a fight, a rescue, a climb, a defence, a delivery) → **the return** (the
village reacts in the square) → **a lasting change** in the village's blocks, people and standing.

### 6.2 Rules
- **Caused by the world, never rolled from nothing**: a camp that grew (Living Wilds camps already raid at tier 4), a
  gate due to break (its own breaks-in clock), a beast's lair, the town's mine, the Deep Tide's new moon, a storm. The
  patent rule holds: consequences are world state.
- **Capped**: at most one trouble per village and two near a player (1,500 blocks); none in the first two hours of a
  world; none inside a Hearth (Skyrim's lesson, G52).
- **Found, never listed**: the call is in the world: smoke over burned roofs, the village bell ringing the alarm (fast,
  heard to 256 blocks), a runner on the road who waves you down (only if you're Known there), lanterns and music for a
  festival. The Journal holds a trouble only once you've said Help.
- **Missable, with weight, never a dead end** (Kingdom Come's fail-forward, G17): a trouble runs on its own clock.
  Miss it and it ends without you, worse: a house stays empty, a field stays grey, a family leaves for Carillon. No
  named person dies for good and nothing shows a fail screen. The village remembers whether you came.
- **Always pays, and can be calmed** (G4): emeralds, standing, a keepsake, XP through its budget. A world option,
  **calm villages**, turns troubles off (festivals and growth stay).

### 6.3 The launch set
| Trouble | Caused by | The call | The deed | The lasting change |
|---|---|---|---|---|
| **Taken** | a beast's lair or a Hollows den within 400 | the alarm bell at night; next morning a parent in the square | follow the trail (drag marks, torn cloth; the Tracks perk lights it) into the lair under the hill; fight; cut the person free; **bring them out**: they hold on to your back (no sprint or wall-run while carried), or follow while you hang a Runner's rope for them | the lair cleared for good; the person greets you by name from then on and can move to your Hearth; a carved token of yours on the family's door |
| **The Burning** | a camp or nest at tier 4+ within 800 (the raid Living Wilds already warns of) | a ladder you can read (Pueblo Vivo, M7): a rumour, scouts seen at the treeline, then the warning a play-day ahead ("they come tomorrow night"); if missed, smoke columns | **with warning**: hold the village: shutters close, the Watch lines the square, fire arrows set roofs alight, the villagers form a bucket line you can join (the city's fire, scaled up); **missed**: charred houses and survivors at the well | burned houses stay charred until rebuilt: **the Rebuild** follows |
| **The Rebuild** | after a Burning, or any damage | the elder at the well | bring timber, stone and iron (counts on the notice board); masons raise scaffolding and rebuild over three play-days | the new houses carry a beam of your colour; a family that had left comes back |
| **The Bell** | the Unbound steal bell bronze (Carillon's C-rank Bell Metal thread) or a break cracks it | the bell silent at dusk; night hunters bolder there | take it back from an Unbound camp, **or** cast a new one: copper and amethyst to the smith (bell bronze, 7.3); then haul it up the tower yourself | the bell rings dawn and dusk again; a ring of light runs over the land; gate breaks nearby are warned earlier and blight stays off its fields |
| **Gone Missing** | a child, a miner or a hunter didn't come home | a search party with lanterns at dusk | a night search: clues on the land, a ledge only the rope reaches, a shaft only a climb goes down, a cave with a slide out | the family's thanks, a keepsake, Friend standing |
| **The Grey Fields** | a gate near the village due to break | the elder: "It breaks in two days. It'll spill onto our fields." | clear the gate | if you did: nothing happens (the best ending); if it broke: blight greys the fields and some people are **Hollowed**; Sun's Cure brings them back |
| **The Deep Mine** (mining towns) | the town's mine broke into a seep or a Hollows nest | the mine bell; miners at the shaft head | go down the town's mine (lifts, ladders, rope, a cave-in on the way out) and bring the miners up | the mine reopens (the town sells ore and lode maps), or is sealed for good if nobody came |
| **The Festival** | the calendar: every ~10 play-days per village (harvest, first snow, the bell's day) | lanterns strung, music heard far off at dusk | join or watch: the rooftop race (an Ascent route over the roofs), the fishing and cooking contests, dancing; the market closes for the day (Stardew, G39) | prizes, standing; settlers decide to come home with you at festivals |

### 6.4 Growth
A village's **prosperity** rises with troubles solved, trade and what you deliver. When it rises far enough the village
builds: a fenced plot gets scaffolding, masons work it over two or three play-days, and a cart brings a new family. A
hamlet can become a village in tens of hours, a village a town. Deliveries speed it up, and that's where a lot of
ordinary mining and woodcutting goes. Your own Hearth at Tier IV is a village too: it gets a festival (in your Hall)
and caravans on your road.

---

## 7. The deep: mining with meaning

### 7.1 One rule: hide from the hunt, metal from the deep, light from the gates
Every forged weapon, Hunter armour piece and mattock from D up needs all three loops: **the hunt's part** (hide, core,
bind: as today), **the deep's metal** (the tier's signature mineral: new, required, no alternative), and **the gates'
light** (essence, Core Shards, Lord's Hearts: as today). Gates alone can't make your gear, and neither can mining. E
stays hide and bone, so the first hour is still never "go find iron" (`docs/modules/gear.md`'s decision holds).

| Tier | Signature mineral | Where you find it | The feeling |
|---|---|---|---|
| E Wildhide (L3) | none: hide and bone | — | the start stays free |
| D Beastbound (L13) | **iron** | any cave, any hillside | "bound in iron": your first real dig |
| C Gatewrought (L22) | **diamond** | deep, below y 0, best in lodes | diamond finally means C-rank gear, and Gatewrought beats diamond armour |
| B Veilwoven (L42) | **amethyst and Deepglass** | geodes; seeps where the Deep leaks into caves | resonance and the Veil |
| A Lordscale (L67) | **netherite** | the Nether's ancient debris | the Nether finally has a reason in AWAKEN |
| S Sovereign (L97) | **starmetal** | the cores of fallen stars | you chase stars across the night |

The Arms and gear owners keep the recipes; the numbers move with them (Gatewrought to 20 armour and 2.0 toughness, so
a diamond spent on it beats a diamond chestplate).

### 7.2 What every ore is for
| Ore | Its job in AWAKEN (new in bold) |
|---|---|
| Coal | fuel; **the Forge burns a coal per temper**; Forge Tier II; torches |
| Copper | the Anchor, circuits, the Charge catalyst (as today); **bell bronze**; **the Tuning Fork**; **the miner's lamp** |
| Iron | **D-tier's metal**; the Forge, tempering, the Fabricator (as today); **Runner's pitons**; **the rope's first fitting** |
| Gold | **the power's metal: every power focus from D up needs gold**; tempering past II, Anchor III (as today) |
| Redstone | circuits for every Precursor machine (as today) |
| Lapis | **ink: Blueprints are drawn in it** (4 paper, 1 lapis, 1 essence, no iron), and lode maps and treasure maps are read with it |
| Emerald | **money**: the coin of every village, town and caravan |
| Diamond | **C-tier's edge**; tempering IV-VI, Anchor IV-V (as today) |
| Amethyst | **B-tier with Deepglass**; bell bronze; the Tuning Fork; monoliths, mats and machines (as today) |
| Obsidian | Anchor V (as today) |
| Quartz | stays a Nether building stone; AWAKEN doesn't pretend it needs it |
| Netherite | **A-tier's metal** (S's hide becomes starmetal) |
| The Lands' crystals: Sunshard, Frostspar, Fault Seam (today decoration) | **attunement catalysts**: Heat from Sunshard (Glass Steppe), Frost from Frostspar (Frost Spires), Gravity from Fault Seam (Shattered Highlands); Charge stays copper; Spirit becomes Deepglass. Every region has a stone worth going for |

**The handouts stop** (decision 8; RDR2's chores lesson, G15): gates pay essence, cores, parts, Hearts and gear but no
raw ore beyond their theme's flavour; the gate market stops selling diamonds; the Mine Echo works a real mine head
(7.7) instead of rolling dice.

### 7.3 Two new ores, one alloy, and lodes
Only where they earn a place:
- **Deepglass**: black-violet crystal that grows where the Deep presses up into caves (a **seep**, 8.2), below y 0,
  and richly in the Descent's strata walls. It hums; the Veil sees it from far off; it needs a diamond edge to cut.
  B-tier's mineral with amethyst, the Spirit catalyst, the lantern of a Hearth's bell tower. It gives the deep its own
  reason and the Descent a mining job.
- **Starmetal**: the core of a fallen star (the anomaly exists: a crater, a sealed meteor, a guardian of your rank).
  Its loot becomes starmetal you cut out while it cools. S-tier's metal. Seeing one fall at night and racing its
  guardian to it is the hunt.
- **Bell bronze**: copper and amethyst cast at a smith's. Bells: your Hearth's bell tower, a village's new bell (the
  Bell trouble), the Bellbronze blade (Living Wilds' Bell Metal chain).
- **Lodes**: big natural veins of vanilla ores, 20-120 blocks of one ore in a seam along a cave wall or through solid
  rock, a few per region at their ore's depth (iron and copper common and shallow, gold and redstone deeper, diamond
  rare and deep). A lode glints in lantern light; Ore Sense and the Tuning Fork find it; at its centre is a **heart**.
  One lode beats an hour of branch mining, so the reason to explore caves instead of strip-mining is in the rock.
  Vanilla's scattered ores stay exactly as they are.

### 7.4 Mining as a cinematic act
The strike gets the feel layer Arms gave the blade (`core.api.CombatFeedback`), with its own voice:
- **Every ore sounds like itself** when the pick bites: iron rings, copper clanks dull, gold is soft, diamond cracks like
  glass, Deepglass hums. Sparks off hard stone, grit off soft; a short hit-stop on the last blow of an ore.
- **The vein gives**: break a lode's heart and its outline lights through the rock (Ore Sense's glass, the whole vein
  this time), white cracks run along it, the camera kicks, and for 30 seconds the whole lode breaks in two blows each.
  The System names it: "LODE · iron · 64".
- **The deep presses**: below y 0 the air's sound thins and lowers, echoes lengthen, and near a seep the world muffles
  and a low hum rises.
- **Cave-ins** (rare, warned, fair): break a lode heart in a big unsupported chamber and dust trickles for three
  seconds before the roof comes down (the gates' `RigCaveIn` already does this). Run, slide or wall-run out. Never in a
  Hearth, never on placed blocks.
- **The heavy strike**: hold the attack with a pick or mattock to charge, release to bore three blocks along your aim
  (more durability and hunger). Mining's own heavy, from the same timing table as a weapon's.

### 7.5 Tools that matter
| Tool | Made from | What it does |
|---|---|---|
| **Tuning Fork** | amethyst, copper | strike it on stone and it chimes; the chime climbs in pitch as you turn toward the nearest lode or seam of the mineral you tuned it to (within 48); the band shows a soft arc. Prospecting by ear, with Minecraft's most beautiful sound |
| **Runner's pitons and rope** | iron, string | hammer a piton into a lip and a rope hangs 32 blocks down, climbable by anyone with the grip (before the Tether, and for people you're bringing out); two pitons in sight of each other make a zip line |
| **Miner's lamp** | copper, glowstone | worn on the head; its light is drawn on the rock ahead, so you see a lode's glint without torching every wall |
| **Rope fittings** (with the Ascent) | iron, gold, Deepglass | metal for the movement system (TotK's Zonaite, G12): an iron line reaches 6 further, a gold reel winds faster, a Deepglass line pays out smoothly for the rappel (8.3) |
| **Picks and mattocks, fixed** | as today | Ore Sense works with mattocks; mattocks from D need their tier's metal; both get the heavy strike and the strike's feel |

### 7.6 Mining that pays
Ore XP by rarity and depth instead of flat (coal ×0.5, copper ×0.7, iron ×1, gold, redstone and lapis ×1.5, emerald
×4, diamond and Deepglass ×6, a lode's heart a lump), the first of each ore a discovery, deep ores under their own
budget. The Mining craft levels from mining again (its craft XP keeps its own pace; only the Hunter-level share is
budgeted), and its planned perks are built as planned: **Vein Sight** outlines the whole vein, **Deep Ear** hears a cave
or a seep behind a wall (a soft sound from its direction), **Deepglass Cutter** replaces "alloy from deepslate".

### 7.7 Your own mine
A **Mine head** room (4.6) over a shaft you dug is read by the Hearth: its depth, and any lode it cuts. A miner settler
(or the Mine Echo) works it: each evening the ore of that depth comes up, and if your shaft cut a lode, that lode's ore.
Dig deeper, find a lode, and your mine gets richer. The dice are gone.

---

## 8. Below: caves worth entering

### 8.1 The rule: keep vanilla's caves, put places in them
1.18's caves are good and every player knows how to read them. AWAKEN keeps the generator and adds what it lacks:
**places** (authored sites joined to the natural caves), **affordances** (the city's climbing language carried
underground, so the movement system has something to do), and **senses** (sound, light, names and a way home). Cave
places are built from libraries of authored rooms strung together and tested with every move (Deep Rock Galactic,
G45); every one has a route that never needs the rope, and the rope buys shortcuts and treasure (Spelunky, G46).
Each passes the five tests of a memorable cave (M44): a rule, a resource, a sensory set, a story entrance, a route.

### 8.2 Places underground
Each is a structure placed with the lair's cave placement (cave biome, depth, rock overhead) plus a new check for
natural air, and it carves short tunnels into the nearest cave so you can walk in.

| Place | What it is | Movement | Found by | Holds |
|---|---|---|---|---|
| **Sinkhole** | a round shaft 15-30 across and 40-110 deep from the surface into a cave system: ledge rings, hanging roots and vines, a waterfall into a deep pool, a lit grotto at the bottom | **E route**: ledge drops and hangs down the rings, vines, old ropes; **the dive**: the pool is deep enough to jump from the rim; **D route**: rope off any wall | mist rising by day, bats at dusk; Horizon: "Sinkhole · a way down" | a lode; the first one is placed on purpose near the Commons' edge |
| **The Delvers' mine** | the abandoned workings of the people who dug Carillon's bell metal: dressed galleries, timber shafts, rail inclines, rope bridges over chasms, a foreman's office, a winch | cut walls are flat: **real wall-runs underground**; ropes and ladders for the grip; a minecart incline to ride down; collapsed sections to slide under | a timbered adit in a hillside; rails leading in | lode maps (read with lapis), a Delver's log for the Codex, an old pick (relic); the logs say they dug too deep and broke into a seep |
| **Seep** | below y 0, where the Deep presses into the rock: a cavern stained violet-black, Deepglass growing in veins and spires, sound muffled, a low hum | ruined galleries, Deepglass spires to climb, an updraft over a magma vent | Deep Ear hears it behind a wall; the Tuning Fork tuned to Deepglass | Deepglass; a Hollows nest; the **seep heart** (a Deep-born creature three levels over the region); region level +5 inside |
| **Chute** | a long slope paved with old stair-cut steps (a Delver ore chute) or ice | the **stair slide**, which already works on steps: a 60-block ride that throws you out over an underground lake | a cold draught at its mouth | the fast way down, and the fun one |
| **Grotto** | behind a waterfall, a glowing pool, a geode you climb inside, a buried cache | climbs and dives | the sound of water; the Veil | **region secrets** (the `awaken:secrets_found` counter exists), a legendary fishing spot, a Precursor log |
| **Cave lairs** | the Crystal Nest joined to the caves by a tunnel; a Hollows den in a dripstone hall; a beast's cave under a hill | the arena's own ledges | the lair line, now with depth ("Lair of the Thousandfoot · 1.2 km NE · 40 below") | the **Taken** trouble's destination |
| **Vents** | in lava tunnels and sulfur caves, hot air over magma (sulfur caves' geysers are vanilla 26.2's own, M23) | updrafts (the Ascent's vents, today only in the sandbox): glide up, refill Aether | shimmering air | the C-rank way up a great cavern |

Not every place ends in a fight (Elden Ring's lesson, G49): a vista, a rope puzzle, a lake, a log, a lode.

### 8.3 Moving through rock
- **What already works stays the core**: hangs, ledge drops, mantles into 2-high passages, slides into crawlspaces,
  vine climbs, Tether zips and swings off any rock.
- **The places carry the city's climbing grammar underground**: ledge courses in odd rows, flat cut faces for wall-runs,
  hanging ropes and ladders for the grip, stair-cut chutes for the slide, rings for the rope.
- **Two mechanics, with the Ascent**:
  - **Rappel**: on a Tether line, sneak and jump pays rope out instead of reeling in. Lower yourself 40 blocks down a
    shaft at your own pace, kick off the wall, swing into a side passage halfway down.
  - **Runner's rope** (7.5): a fixed rope anyone can climb, before the Tether and for the people you bring out.
- Falls on a rope stay free: the rope is a reward. Depth is dangerous for other reasons: the dark, the Hollows, seeps,
  water, cave-ins, getting lost.

### 8.4 The senses
- **Sound**: a Hollows soundscape (drips, far water, stone settling, a breath of air from a passage that leads out)
  and the deep's hum below y 0, louder near a seep.
- **Light**: a Lens look for each cave biome: lush caves' green glow, dripstone's amber, the deep dark's ink, sulfur's
  yellow haze, a seep's violet.
- **Names**: big places get a name and a quiet caption ("THE DELVERS' DEEP · old mine").
- **A way home**: underground, The Way Forward's glance adds "Surface ↑ 34 · NE 60", and the Signal's thread runs
  along the floor toward the nearest way up. Nobody stays lost for long.
- **Sulfur caves** (new in 26.3) get a reason: vents, a Lens look, the Hollows' sound.

### 8.5 With the Descent
The overworld's caves are **the near deep**: materials, routes and the villages' troubles, close to home. The Descent
is **the story deep**, reached only by the Well, and that stays. The bridge is language, not tunnels: seeps speak the
Descent's (Deepglass, the hum, the Hollows), the Delvers' logs name the Well, and the Descent's empty tunnels get the
same dressing (Deepglass lodes, small grottos), which gives the strata a mining job.

---

## 9. Beyond the list

Euan asked for more than villages, mining, caves and the Hearth. Every candidate went through the test in section 3.

| Idea | Verdict | Why |
|---|---|---|
| **Roads and inns** between Carillon, the camp and the settlements: paths in each region's own blocks, waymarkers, an inn about a day's walk apart, caravans and travellers on them (Living Wilds' caravan happening walks a real road) | **In** (phase W7) | the world joins up; a road you follow to a village you didn't know is a classic Minecraft moment; caravans on your road are Tier IV's reward |
| **The emerald economy** | **In** (W3) | trade gives every surplus a use and every region a reason |
| **Your home as a course**: Carillon's climbable kit (drainpipes, beams, window boxes, wall ivy, hanging ladders) craftable; Wren's rope lines; the Training Grounds' ghost on your own route | **In** (W6) | the thing Euan loves most, at home |
| **Fishing**: a legendary fish in some named waters (grottos included), the fishing contest at festivals, the fishing village's trade | **In** (W4, W5) | small, data, uses the Fishing craft |
| **Crafting draws from nearby chests at home** | **In** (W1) | the cheapest quality-of-life win in base games (G5, G24) |
| **A hound** from a village kennel that follows tracks with the Tracks perk | **Later** | lovely, but one more verb in the first week |
| **The Atlas below**: the caves you've walked drawn as a slice under your feet | **Later** | big client work; the depth word on the lair line and the way-home glance come first |
| Seasons, temperature, thirst | **Cut** | survival friction that pays nothing you can't buy |
| Relationships, marriage, families (MCA-style) | **Cut** | a game of its own; AWAKEN's people get routines and a few arcs (Stardew's ratio, G41) |
| Colony management (MineColonies-style worker trees, research, logistics) | **Cut** | 45 worker types is a second game (G33); AWAKEN's settlers do one job each |
| Vein-mining a whole vein with one swing | **Cut** | it kills the strike; the lode heart is the better version |

---

## 10. The first evening and the first week

### 10.1 Before and after
| Moment | Today | After |
|---|---|---|
| Leaving Carillon | an errand to a camp (The Way Forward), then a forest | the errand ends at dusk with a coal from the camp's fire and "set it down where you mean to stay" |
| Night one | dig a hole, wait | find your ground, set the Ember, build a shelter; the Hearth names it |
| Your base | invisible to AWAKEN until a Lord's Heart, then a checklist | every room read, named and graded; Comfort, Rested; people move in when there's a bed |
| A village | vanilla: nodding traders, nothing happens | a hamlet at work: fields, a smith, the bell at dusk, the lamplighter; it can burn, lose someone, ring again |
| Finding diamonds | worth a lump of coal; gates hand them out | C-rank gear is made from them; a diamond lode breaking open is an event |
| A cave | vanilla noise; the movement system has little to grip | a sinkhole with a route down, an old mine you wall-run through, a seep that hums, a way home |

### 10.2 One or two new things at a time (the reveal; balanced player, pacing_sim hours)
| When | What opens | New verbs |
|---|---|---|
| 0:12 | the camp at dusk: the Ember | carry the flame |
| 0:15-0:40 | your ground, the first shelter, then The Way Forward's first gate | read a room |
| 1-2 h | the first hamlet up the road: trade, the bell at dusk, a small request; the first sinkhole near the Commons' edge | trade in emeralds, the climb down |
| ~2 h | the first Lord's Heart: the Anchor on your fire, Tier I; Oren comes when there's a bed | rooms that do things |
| 2-5 h | the first lode ("LODE · iron"); the hamlet's first trouble (Gone Missing); the Tuning Fork from the hamlet's smith | prospect |
| ~5-6 h | D-rank: the Tether at the vault (The Way Forward); Beastbound needs iron; sinkholes become playgrounds | rope in rock |
| 6-12 h | a village further out: a festival, the Taken; Hettie or Ilse moves in; Tier II | settlers |
| 12-20 h | below y 0: diamond lodes and the first seep; Gatewrought; the Bell trouble; a town over the pass | the deep |
| 20-50 h (the long middle) | towns' troubles, the Deep Mine, seep hearts, Deepglass for Veilwoven, Tier IV: your village named on the Atlas, caravans on your road | your place in the world |

The test, as The Way Forward set it: no stretch longer than 3 hours without a new capability in the first 50 hours,
and no rank-up bringing more than four at once. This plan's verbs are spread through the D and C bands, where the sims
found the deserts.

---

## 11. How it fits

### 11.1 The Way Forward (being built: Phases 1 and 3)
- **Phase 1 (the first thread)** ends at the camp; W1 adds Ilse's Ember to that ending and one line to Bram's brief.
  Build W1's hand-off on top of Phase 1's merge, never against it.
- **Phase 2 (promises and the ladder)**: locks met underground and in villages become promises (a seep's sealed
  gallery "needs D", a shaft "a rope's reach", a village's bigger ask "for a Friend"); the glance gains the room
  reading at home and the way home underground.
- **Phase 5 (the long middle)**: its capability clock counts this plan's firsts (the Ember, the first lode, the Tuning
  Fork, the rappel, the first settler, each Hearth tier); its band-2 content is largely W4-W6.

### 11.2 Living Wilds F and G
- **G moves here**: settlers (4.7), your named village (4.8), roads (W7). Its caravan happening walks roads in W7.
- **F splits**: region secrets underground and treasure maps (read with lapis) come here (W5); **wonders and legendary
  beasts stay with Living Wilds F**.
- Village happenings join the World Pulse as data plus behaviours; camps target villages as well as Hearths; the Mining
  craft's fixes (7.6) are in `living.craft`. All agreed with the Living Wilds owner before building.

### 11.3 Carillon
- The people engine's refactor to many settlements (W0) and interiors (shared with M4 B4) are done with the Carillon
  session, which owns `citizens` and `city`. Carillon keeps its behaviour exactly.
- Kestrel, Bram and the clerk stay Carillon's; this plan asks for Bram's one line.
- Carillon's planned bell marks are dropped for the emerald (decision 5); `EconomyHooks` is installed with emeralds.

### 11.4 Arms and gear
- Tier metals (7.1) change recipes and tags the Arms and gear owners hold, and Gatewrought's armour numbers; Blueprints
  move to lapis; attunement catalysts move to the Lands' crystals and Deepglass. Briefed to the Arms session after its
  current batch, as data.

### 11.5 The Ascent
- The rappel and rope fittings are the Ascent's (body.ascent); natural updraft vents reuse `Updrafts`. Caves are built
  to the Ascent's numbers (wall-run faces 2+ tall and flat, ledges 1.2-2.4 over the feet, drops 0.9-3.2).

---

## 12. Modules, APIs, budgets, multiplayer, old worlds

**New modules** (same rules as SPEC §1):
- `village` (`dev.awaken.village`): the settlement planner, biome styles, interiors, settlements on the people engine,
  village jobs, trade, standing, troubles, growth, roads (W7). Commands `/awaken village ...`.
- `delve` (`dev.awaken.delve`): lodes, Deepglass, starmetal, bell bronze, the strike's feel (client), Tuning Fork,
  pitons and rope, miner's lamp, cave places, cave senses with core audio and Lens, the way home.

**Changed modules** (each at the seam, agreed with its owner): `hearth` (the Ember, Tier 0, the reading, rooms,
Rested, settlers, tiers, fixes), `citizens` and `city` (many settlements, interiors; Carillon), `gear` and `arms`
(metals, numbers, Blueprints, catalysts; Arms), `gates` (loot and market), `progression` (ore XP), `living` (Mining
craft, Pulse kinds, camp targets), `body.ascent` (rappel, fittings), `lens` and `core.audio` (cave looks and sound),
`atlas` (settlements and the depth word), `terrain` (vanilla villages off in the lands pack).

**APIs** (SPEC §12, proposed): `dev.awaken.village.api.Villages` (`settlementAt`, `standing`, `trouble`, `nearest`);
`dev.awaken.hearth.Hearth` gains `rooms(HearthInfo)` with kind, grade and name, `comfort`, `style`;
`dev.awaken.delve.api.Lodes` (`nearest`, `heart`); `core.api.SettlementHooks` for modules that mustn't depend on
`village`.

**Budgets**: settlements share the people engine's (ledger ≤ 0.5 ms a tick, bodies ≤ 1.5 ms, ≤ 96 bodies in one world
pool, nearest first); a 50-person village costs almost nothing in the ledger. The planner runs once per settlement on
a worldgen thread and is cached; pieces cost what the city's do. The Hearth's reading stays inside its 12,000 reads a
tick. Lodes are features recorded in a per-region index (the Tuning Fork reads the index, never a 48-block scan).
Mining feel is client-only and uses the effects engine's budget. Cave places are structures with the ruins' costs.

**Multiplayer** (Euan's guild-server ambition): settlements, their troubles and growth are shared world state;
standing is per player; a trouble counts for a party within 48 (`PartyHooks`); settlers belong to a Hearth's household;
lodes are shared; a cave-in can't be triggered on another player's placed blocks.

**Old worlds**: villages, caves, lodes and new ores apply to new chunks only; Euan's own world keeps its vanilla
villages. The Hearth's reading, room kinds, Rested and the recipe changes apply everywhere (existing Anchors become
Tier I with the reading on; gear already made is kept).

---

## 13. Phases, effort, tests and tours

Effort is builder time at AI pace, scaled from git history: the whole Hearth module was built in about an hour on
23 Oct; Carillon's M1 city in ~3-6 h and its M3 citizens in ~3.5 h; Living Wilds phases B-E took 1-2 h each.

| Phase | Scope | Effort | Proves | Tests (one per mechanism) | Tour |
|---|---|---|---|---|---|
| **W1 The carried flame** | 4.1-4.9: the Ember, Tier 0, the reading and grades, names, the room glance, Comfort and Rested, room kinds (all but the Mine head), chests at home, a coal per temper, the fixes | 3-4 h | a hand-built shelter is read, named and graded; the first night has a reason | `hearth_ember_founds`, `hearth_room_grades`, `hearth_rested` | "first night": camp → Ember → a built room's caption and glance |
| **W2 Metal from the deep** | 7.1-7.7: tier metals (with Arms), handouts trimmed, ore XP, the Mining craft's fixes, lodes and hearts, the strike's feel, Tuning Fork, pitons and rope, lamp, Deepglass, starmetal, bell bronze, crystal catalysts (the mine head moves to W6, rope fittings to W5, so W1 and W2 never share a file) | 4-6 h | a lode breaking open is an event; every ore has a job | `delve_lode_heart`, `delve_tier_metals`, `delve_tuning_fork`, `progression_ore_xp` | "the strike": a lode heart, the fork, the cave-in |
| **W0 Many settlements** (with Carillon) | 5.1: the people engine per settlement, one body pool, saved state v2 | 2-3 h | Carillon unchanged, a second settlement ticking beside it | `citizens_many_settlements`, Carillon's own tests green | none (Carillon's tour unchanged) |
| **W3 Living villages** (built 2026-10-26, `pack/world-depth-w3`, SPEC §28.6) | 5.2-5.6: planner, biome styles, interiors (shared with B4), village jobs, trade in emeralds, standing, the scribe, vanilla villages off, the first hamlet on purpose | 8-12 h | a hamlet and a village at work from a hill at dusk; every house enterable | `village_plan_deterministic`, `village_day`, `village_trade` | "the hamlet at dusk", a village by day and night |
| **W5 Caves worth entering** | 8.1-8.5: sinkholes, the Delvers' mines, seeps, chutes, grottos, joined lairs, vents; the rappel and rope fittings (with the Ascent); cave sound, looks, names, the way home | 6-9 h | each place climbed with every move; an E route in every one | `delve_cave_routes`, `delve_seep`, `ascent_rappel` | "the descent": a sinkhole by every route, a mine's wall-run, a seep |
| **W4 Troubles** (built 2026-10-26, `pack/world-depth-w4`, SPEC §28.7) | 6.1-6.4: the village director, the eight launch troubles, growth, the calm-villages option | 5-7 h | a village burned, rebuilt and remembering you | `village_trouble_taken`, `village_burning_rebuild`, `village_director_caps` | "Taken": the bell, the trail, the lair, the carry out |
| **W6 Your village** | 4.7-4.8, 7.7 and section 9's home course: settlers and their work, tiers that grow from the home, the Mine head and the Mine Echo's rework, the named village, the kit craftable | 3-5 h | someone you saved moves into the room you built | `hearth_settler_moves_in`, `hearth_tier_reading` | "Ashford at Tier IV" |
| **W7 Roads and towns** | roads and inns, caravans on roads, walled towns, mining towns over their mines | 6-10 h | the world joined up | `village_roads` | a road from the camp to a town |

**Order**: W1 and W2 first, as two builders at once (different modules: `hearth` against `delve`/gear). W0 then W3
with the Carillon session; W5 runs beside W3 (terrain and delve). W4 after W3, W6 after W1 and W3, W7 last.
**About 37-56 builder hours in all**, two to three days of wall-clock with two builders.

Every builder: Opus at extra-high effort for anything that decides the look or feel (the reading's names and captions,
villages, interiors, cave places, the strike); Sonnet at extra-high effort for data, generators, tests and docs. Every
Gradle run and tour through `slot.sh`. One test per mechanism, the suite once at hand-off, CI for the rest; one tour per
phase, judged against the visual bar (our own particles, sounds and light, never vanilla's reused).

---

## 14. Risks

- **Scope.** This is the biggest plan since Arms. It's phased so each phase stands alone and is worth having: W1 and
  W2 change the first evening and every cave trip within a day.
- **Shared code.** W0 touches Carillon's engine; W2 touches Arms' recipes; W5 touches the Ascent. Each is agreed with its
  owner first, with that owner's tests green.
- **Interiors and the planner are the hard part.** Interiors are shared with Carillon B4; the planner is new code.
  W3 starts with hamlets and villages; towns wait for W7.
- **Removing vanilla villagers** ends iron farms and villager trading halls in new worlds. That's deliberate (iron
  comes from the deep, books from the scribe), and it's decision 4. Zombie villagers still spawn; a cured one becomes
  one of our people, a refugee who can settle.
- **Overload.** The reveal (10.2) is the guard: one or two new verbs at a time, nothing listed, the calm-villages
  option. Crimson Desert's critics are the warning (G7).
- **Ore scarcity tuning.** Taking ore out of gates moves the pace of D and C gear onto mining; the pacing sim gets a
  mining model before W2 merges, and lodes make deep metal quick to get once you go looking.
- **Old worlds.** New places need new chunks; Euan's current world will see the Hearth and mining changes, not new
  villages near spawn.
- **Machine time.** Tours wait for the slot; Euan's play wins.

---

## 15. Decisions for Euan

1. **The Hearth starts on your first evening**: Ilse gives you a coal from the camp's fire, you carry it until you find
   your ground, and setting it down founds a Tier 0 Hearth. The Lord's Heart still makes the Anchor (Tier I).
   *Recommend yes.*
2. **The Hearth reads what you build**: every room named and graded on light, warmth, space, care and detail (the
   weakest reading holds it down); the glance reads the room you stand in; Comfort sets Rested; rooms do things; people
   move in when there's a bed. *Recommend yes.*
3. **AWAKEN's own settlements replace vanilla villages in new worlds**: hamlets to walled towns plus special kinds,
   built on Carillon's people engine (made to run many places, with the Carillon session) and the city's grammar, with
   real interiors; the first hamlet placed on purpose up the camp road. *Recommend yes.*
4. **Village people are people** (Carillon's citizens), not vanilla villagers. Vanilla villagers leave new worlds, which
   ends iron farms and trading halls there; a town scribe keeps enchanted books. *Recommend yes.*
5. **The emerald is the coin of the world**, and Carillon's planned bell marks are dropped. *Recommend yes.*
6. **Troubles**: caused by the world, found never listed, missable with a lasting cost and never a dead end or a named
   death, always paid, and a "calm villages" world option. Launch set of eight. *Recommend yes.*
7. **Hide from the hunt, metal from the deep, light from the gates**: from D up every tier needs its mineral (iron,
   diamond, amethyst and Deepglass, netherite, starmetal); E stays free; Gatewrought rises to beat diamond armour.
   *Recommend yes.*
8. **Give mining its job back**: gates stop paying raw ore, the gate market stops selling diamonds, and the Mine Echo
   works a real mine head instead of rolling dice. *Recommend yes.*
9. **New finds only where they earn a place**: Deepglass and starmetal, bell bronze, lodes with hearts; the Lands'
   crystals become attunement catalysts; lapis becomes ink. *Recommend yes.*
10. **Mining as a cinematic act**: every ore its own sound, the vein gives, the heavy strike, rare fair cave-ins, the
    Tuning Fork, pitons and rope, rope fittings from metal. *Recommend yes.*
11. **Caves**: keep vanilla's generator and add places in it (sinkholes, the Delvers' mines, seeps, chutes, grottos,
    joined lairs, vents), the rappel, cave sound and light, and a way home; an E route through every one.
    *Recommend yes.*
12. **This plan takes Living Wilds G** (settlers, your named village, roads) and F's underground secrets and maps;
    F keeps wonders and legendary beasts. *Recommend yes.*
13. **Order**: W1 and W2 now as two builders; then W0 and W3 with Carillon, W5 alongside; W4, W6; W7 last.
    *Recommend yes.*
