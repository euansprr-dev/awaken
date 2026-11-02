# The Journey's Ledger

Every thing the Journey (SPEC §43) adds or changes, with how you get it, what it's for, how you come to know it exists,
its recipe or source, what it links to, and its edge cases. Euan's bar (29 Oct): "everything actually links,
everything has a recipe, everything has a path ... nothing is something that would be impossible to discover." A
stage's rows are written before its code and finished before hand-off. `journey_ledger` (GameTest) checks the part a
machine can check: every `awaken` item and block has a way in.

Fields: **Get** (where it is or how you get it) · **For** (what it does for you) · **Know** (how you come to know it
exists, the direction) · **Source** (recipe, loot, placement, code) · **Links** · **Edges** (edge cases and what
happens).

---------------------------------------------------------------------------------------------------------------------

## Stage 1: the world rule (§43.1 to 43.4)

### The Lull (the reason the land is empty)
- **Know**: Ilse says it at the Last Fire (her lines 2 to 4); every Stilled shows it; Teodor at the archive says it
  if you ask him about the land; holdfast people talk of it.
- **For**: why only the Awakened go out, why the bells matter. No mechanic of its own: it's the rule behind the
  others.
- **Edges**: classic worlds (no Carillon) keep the same rule: the camp is 300 blocks north of spawn and the ridge 60
  past it.

### The Last Fire and Ilse
- **Get**: the Wayfarer camp, 450 blocks up the north road (300 in a classic world), its fire, Ilse alone. The camp's
  smoke shows from 1,000 blocks; the High Gate's shot (first time out) points the camera at it.
- **For**: the last person you meet; her coal is your Ember; she names Aldo, the Lull and the holdfasts.
- **Know**: Bram's errand (The Last Delivery) sends you; his ask: "Ilse keeps the last fire up the north road. Past it,
  it's only hunters." The quest's places read "the Last Fire".
- **Source**: `living.wayfarer.Scenes` places Ilse once a player is within 96; `living.FirstScenes` plays her scene
  (her ten lines from JOURNEY part 1, verbatim, timed by reading length).
- **Links**: the Ember (Hearth), Aldo (the first Stilled), the holdfast rumour, the ridge rule. Stage 7: she walks to
  your home once after the Wake.
- **Edges**: a player who already has a home fire or an Ember hears lines 1 to 7, 9 and 10 (no coal). Walking off
  mid-scene ends it; talking to her again with the letters step open restarts it. After her step she has her own
  idle lines and the pulse rumours, never a task. Old worlds: Hettie's body at the camp leaves on load; Ilse stays.
  Several players: each gets the scene for themselves; Ilse's coal is per player.

### The ridge (the bells' edge)
- **Get**: a circle round Carillon's centre through a point 60 blocks north of the camp's nominal spot (510 blocks in a
  Carillon world, 360 in a classic one). `core.api.TheLull` holds the numbers.
- **For**: the Ember sets only past it; the Stilled's first three sit just past it; holdfasts keep 1,500 blocks from it.
- **Know**: Ilse: "Past that ridge the bells don't carry"; the Ember's refusal inside it says so in plain words.
- **Edges**: Hearths placed inside it before this change stay where they are. Moving home uses the same rule. The
  refusal: "You can still hear the city's bells here. Your home has to be out past the ridge beyond the Last Fire, where
  they don't carry." A holdfast's: "You can still hear the holdfast's bell here. Your home has to be further out, where
  it doesn't carry." Only the overworld has a ridge. Tests measure from `TheLull.testCentre`.

### Empty settlements
- **Get**: every hamlet, village, mining village, town and wayhouse AWAKEN places (§28 W0, W3, W7) still generates,
  with no people: no citizens, troubles, settlers or scenes. Furniture, beds, chests, chimes, notice boards and roads
  stay. Waymarkers still name them.
- **For**: shelter on long trips, the Stilled's left chests, letters (stage 4's leads), beasts (stages 2 and 5).
- **Know**: roads and waymarkers lead to them; their roofs show from high ground; keys name them.
- **Source**: `village.VillageService.start` registers people only at holdfasts.
- **Links**: left chests, Stilled keys, holdfasts (the only settlements with people), the wildness field (they no
  longer tame the land, so the wild grows in).
- **Edges**: a notice board in an empty settlement reads "[The notices are years old. The last one says the chime has
  stopped and they're all going to the city.]" Beds are ordinary beds. Old worlds: settlements whose people were saved
  are never started again; their saved records stay inert. Village troubles never start (§43.1; beast troubles come in
  stage 5). The Ember is still refused inside one ("Take it past the last house").

### Holdfasts
- **Get**: the town (§28.9 W7) of a region chosen by `village.road.Holdfasts`: the first is the lowest-rolled town
  region whose heart lies 1,500 to 2,500 blocks from the ridge and whose land takes a town (found at server start, like
  the first town); then in each 3,000-block cell round Carillon, the lowest-rolled town region whose heart is 1,500 or
  more from the ridge, unless it lies within 2,000 of the first or within 1,500 of a neighbouring cell's lower-rolled
  pick.
- **For**: the only people outside the city: a market (the town's traders, emeralds), an inn bed and a meal, rumours,
  a ringer on shift; their bell is one of the hundred (stage 4 files it as Rung).
- **Know**: Ilse's line about walled towns; the bell, a single toll on every in-game hour and three at dawn, noon and
  dusk, heard to 768 blocks (`awaken:holdfast.bell`, the city's bell recordings, lower and slower); a player who hears
  it gets one System line the first time ("[A bell. A real one, ringing, somewhere {bearing}.]") and the bearing is
  kept for the Map (stage 4 shades it with the other bells).
- **Source**: `village.VillageService.start` registers a holdfast's people; `village.Holdfast` rings the bell and
  keeps who heard it and who stood inside.
- **Links**: the wildness field (a tamer, as a town), the Ember (refused within 768 of its bell), the Bell Book (stage
  4: Rung on first standing inside).
- **Edges**: no troubles, no notice-board requests and no runners ever (holdfast people never give tasks); festivals
  still happen. A square whose pick's land takes no town has no holdfast (its heart is still tamed); a camp's raid
  never becomes a holdfast's (or any village's) Burning. Its notice board: "[The board has the bell's shift list and a
  few notes they've left each other. Nothing on it is for you.]" Test: `village_holdfasts`. Old worlds: the choice
  is pure from the seed, so an old world's towns are holdfasts or empty the same way. Several players each hear and
  file it for themselves. The bell rings only while a player is within its reach (no cost otherwise).

### The Stilled (the stone people)
- **Get**: posed stone figures, block `awaken:stilled` (a block entity drawn by its own renderer; about twelve poses).
  The first three on the north road in the first 400 blocks past the ridge (Aldo on the milestone just past it); one
  or two on the road to the first hamlet; then one per 1,000 x 1,000 cell within 3,000 blocks of Carillon (two cells in
  three), one per 2,000 x 2,000 to 6,000, one per 3,000 x 3,000 past that; a cell's sits at the edge of the empty
  settlement nearest its spot (within 400, on the spot's side, facing home), else beside the road nearest it (within
  300), else nowhere; never within 150 blocks of another (a seated pair is one figure); never in a gate, below the
  surface or in the city. When the first hamlet is as near as the third, the third sits at its near edge instead,
  reaching for it with its key (built). Stage 4 adds the one on the way to the first sleeping bell near your Ember.
- **For**: the feeling of the Lull; each carries a thing (below).
- **Know**: Ilse names Aldo; they sit on the roads you walk; reading one (use) gives the System's bracketed line and
  what they hold.
- **Source**: `world.stilled.StilledPlacer` works a spot out once a player is within 640 and places the figure once
  one is within 96 and its ground is loaded (natural ground: never wood, planks, path, slabs, stairs, cobblestone,
  bricks, farmland or anything with a block entity; two free cells above; searched 6 round; not in a Hearth, the city
  reserve or a settlement's ground), and keeps each spot placed or dropped in saved data so it's never tried twice.
- **Links**: letters (stage 4's Found leads), keys (left chests), keepsakes (Carillon's families), Lampwings and
  Dumplins (stage 2 and 5 roost on them), the Wake (stage 7: they stand and walk home).
- **Edges**: unbreakable (no tool, explosion, piston or power moves or breaks one; `destroySpeed` -1, blast resistance
  of bedrock, push reaction IMMOVEABLE; dragon- and wither-immune); a creative player can't break one either (only `/awaken stilled remove`). What they
  carry is per player: each player takes it once; if you lose it before using it, the Stilled still holds it for you.

### Things the Stilled carry
- **A letter** (`awaken:stilled_letter`): a readable letter in plain speech (a mother to a son, a note left on a
  door). **For**: stage 4 files the bell lead it names as Found; stage 1 keeps which letters each player has read so
  stage 4 files them at once. **Source**: taken from a Stilled. **Edges**: read again any time; dropped like any item.
- **A key** (`awaken:stilled_key`): its tag names an empty settlement within 1,200 blocks and "by the well". **For**:
  opens that settlement's left chest. **Know**: the System reads the tag aloud; Track gives the settlement's bearing.
  **Edges**: the chest is placed by the well when a player holding the key comes within 64; the key is used up when the
  chest opens; a lost key can be taken from the Stilled again until its chest is opened.
- **A keepsake** (`awaken:stilled_keepsake`; `awaken:keepsake` is the village troubles' own): a named thing (a dog
  whistle, a ring, a christening spoon) with a Carillon family's name. **For**: the city remembers them. **Know**: the
  System: "Somebody in Carillon might know this name"; used inside Carillon, the System keeps the city's register (Teodor
  has no archive): it says which district a member of that family is in right now and Track points there (a line to try
  in the daytime when none of them are out). **Source**: the family names are Carillon's own households (`data/awaken/city/citizens.json`).
  **Edges**: showing it (use on a citizen of that family) gives their reaction in plain words, once; you keep the
  keepsake. Never a task, never a reward screen.

### Left chests
- **Get**: block `awaken:left_chest`, placed by an empty settlement's well when a key's holder comes near.
- **For**: what someone left behind: per player, on first opening, the first of the early weapon patterns they don't
  know yet (Spear, Rapier, Chain-blade, Glaive, Twinblades: the five the Wayfarers used to teach), else the first of the
  six old relics they haven't had from a chest (Shepherd's Lantern, Lumen Tonic, Climber's Hook, Caravan Writ,
  Herdcaller Horn, Rider's Spurs: the Wayfarers' chains' rewards), else three essence shards; with it some supplies
  (bread, torches, string, candles, leather, dried kelp, paper). Patterns through `ArmsApi.leftChestPattern`.
- **Know**: the key's tag; the chest is by the well, the middle of every settlement.
- **Source**: `world.stilled.LeftChests`; loot table `awaken:chests/left_behind` for the supplies.
- **Links**: arms (the five patterns' early home moves from the Wayfarers to here: `data/awaken/arms/style_homes.json`
  "found" in place of "teacher"; the Armoury says "Found early in a chest someone left behind").
- **Edges**: per player (each opens their own with their own key; one placed key per settlement); always unbreakable,
  like the Stilled; the key stays in the lock; opening without the key: "[Locked. Whoever had the key went back for
  something.]"; opened again: "[It's empty now. You took what they left, and the key's still in the lock.]"

### Echoes removed (§43.4)
- **What goes**: binding, the Echo roster, the field army, the chooser screen and its payloads, the Echo cap and SPI's
  line, role pads' work and recipes and the Barracks room, Guard, Haul, Farm, Mine and Train, Echo Duty, the Mine Echo,
  the JEI "Mine Echo" page, the pad tooltip, `/awaken hearth echoes`, the "Echoes bound" counter row, the first-Echo
  Signal line, the Echo glossary entry, the bind sounds. The B key stays: the Veil's "wear a creature" uses it (its
  label now says so).
- **What took their place in progression** (nothing may point at binding):
  - Shadowbinder path: "You command shadows. The soldiers you raise hit harder and stay longer." Scored on shadow power
    use (3 a use); its perk is the new **Summon Strength** (+25%: Legion's soldiers and Tech drones hit harder and last
    longer, by the same fraction). The truth "I command the shadows." (200 shadow uses, +15%) and the title Shadow Lord
    (500 uses, +10%) carry it too. Echo Whisper (latent) now gives +64 Veil cord.
  - Directives and drill: "Use shadow powers twenty times." (Shadowbinder), "Take 200 damage holding the line."
    (Warden), drill "Use shadow powers ten times"; "Bind an Echo." is gone.
  - Level road: 7 "What they carried" ("The stone people still hold things. A key names the town its chest is in."),
    30 "The far towns" ("A few walled towns kept their bells and their people. Listen for one ringing on the hour."),
    44 "What was left" (12 essence; its goal: open a chest someone left behind, counter `awaken:left_chests_opened`).
    7, 30 and 44 are the never-stuck nudges for the Stilled's keys, the holdfasts and the left chests.
  - The trail "Trophies and Echoes" is "Trophies" (trophy, Trophy Hall, a break held at home).
- **What stays**: the Echoing tag, the Grave Echo, gates' ghosts, the pick's Echo rung, Echo Palm, Heavy Echo. Echo
  Swap swaps with your own summons and decoys. Charge overcharges your own summons ("Charge meets your summons").
- **Old saves** (HearthPlayer schema 5): bound Echoes are released on load with one System line, "Your Echoes have
  gone back to the Deep."; an Echo's body in the world (an entity with the old Echo link or the ECHO mob role) is
  removed when it loads. Role pads stay as plain blocks that drop their materials (two smooth stone slabs, an essence
  shard and the pad's tool) and can't be crafted any more. A saved Barracks reading becomes no room type.
- **SPI**: stage 1 says "Stronger in the Veil and in dreams."; stage 2 adds ", and the wild's beasts warm to you
  sooner." with Kinship (+1% per point to +30%).
- **Test**: `hearth_old_echoes_released` (an old roster is read, told once, never written back; an Echo's body is
  recognised).

### No people at the Hearth
- **What goes**: settlers moving in (W1, W6), the townsfolk at the Hearth (Oren, Mira, Teodor, Sun, Vael), their work
  (fields, pens, the Mine head's miner, commissions, brews), Oren's cottage offer.
- **Old saves**: settlers leave on load with one line, "The people who lived at your Hearth have moved into Carillon.";
  their rooms keep their furniture.
- **What replaces it**: stage 2's beasts (fields, the Ore Shed); the cottage: stage 1 leaves the guide's own plan for
  you to build; stage 2's first Thatchback raises it from that plan over a play-day once the Anchor stands.
- **The Mine head**: stays a room type and does nothing until stage 2 turns it into the Ore Shed.
- **Tiers that asked for people**: IV now asks for a bell tower and six rooms, V for ten rooms with two of them Grand
  (`hearth.TierRules`, §43.6). Stage 2's nooks grow with the tier instead.
- **The Spire's Board and Monolith** came with the first settler; they come with the Anchor now (Tier I), and an old
  world's Anchor without them gets them on the owner's next visit. Both keep their recipes.
- **Tests**: `hearth_people_moved_to_city` (an old settler with a commission: delivered, gone, the line; no cottage
  offer; no tier asks for people), the tier test (six rooms, then ten with two Grand).

### No people in the wild
- **What goes**: found scenes (Wren, Gus, Pell, Marta and their chains: Wren's Rope, Ash on the Wind, the Hermit's Horn,
  Iron in the Ranks, Crowns in the Dark, Marta's Wheel, the Caravan Road, Pell's Message, Silk on the Road, the Bell at
  the Crossing), Ilse's Moth Lights and Hettie's Ten Sheep Short (people never give tasks outside the opening).
- **Hettie** lives in Carillon now, in the market square by the cloth and produce stalls (city spot `hettie`, on the
  square's floor two below the street; kept out of plan.json so worlds built before keep their plan hash and citizens), with her own lines about the hills and the walk in (never a task). A classic
  world has no city and no Hettie.
- **Hale** stays at the High Gate's quartermaster spot (in the city).
- **Ilse's lines** after her scene: four of her own (the kettle, what it looks like out there, Aldo, watch before you
  draw), after the pulse rumours; her coal line again for a lost Ember ("Lost your coal? That's all right. Take another
  from the fire.").
- **Bram**: "Ilse keeps the last fire up the north road. Past it, it's only hunters." in his brief and his ask; the
  errand's place is "the Last Fire"; the High Gate's shot names THE LAST FIRE; so does the Horizon line.
- **Gone with the found scenes**: their distress flares (the `living_tells` payload and its drawing), the dev commands
  `living scene` and `living view`, the found-scene tours' steps (`living-c`, `living-c-check`).
- **Their relics** keep a way in: the six (Shepherd's Lantern, Lumen Tonic, Climber's Hook (was Wren's Hook), Caravan
  Writ, Herdcaller Horn, Rider's Spurs (was Pell's Spurs)) wait in left chests after the five patterns (see Left
  chests). Pell's Letter, an errand item with no errand left, goes on the never-held list.
- **Their patterns**: Spear, Glaive, Rapier, Chain-blade and Twinblades are found early in left chests (style homes
  `"found": "left_chest"`); Oren still teaches the Greatblade at the end of his chain. The Finding Patterns trail's
  people step becomes "Find a pattern in a chest someone left behind" (counter `awaken:patterns_from_left_chests`); its
  village step names a holdfast's smith.
- **Old saves**: a retired chain in a player's Journal is dropped quietly at login (`QuestEngine.prune`); the scenes
  record (schema 2) drops found scenes and places Hettie again in the city; a scene's Wayfarer leaves once its scene
  is gone, and Hettie's camp body once she's out of the city, each only when nobody stands within 16.
- **Vael**: his rank beats wait until you're next in the city or in a gate (marked waiting in his seen set, so a
  restart keeps them; the latest plays first).
- **Tests**: `arms_style_homes` (Oren once; five found in left chests, in order, skipping known ones),
  `city_spots` (Hettie's spot has room and a floor), `hearth_ember_founds` (the ridge refuses; past it the coal takes),
  `living_camp_keeps` (nothing needs the night now).

### The wildness field (§41)
- **Tamers now**: Carillon (and its farmland), the laid roads, the holdfasts' towns, Hearths. Empty settlements no
  longer tame, so the wild grows into them.
- **Edges**: a chunk's wildness is kept once worked out, so land already generated keeps its old value; new land follows
  the new tamers.

---------------------------------------------------------------------------------------------------------------------

## Stage 2: the first beasts, Kinship, the Hearth page, Fields and the Ore Shed (§43.5, §43.6)

### The four kin (Lampwing, Burrowbun, Mossback, Pebblepup)
- **Get**: entities `awaken:kin_lampwing`, `kin_burrowbun`, `kin_mossback`, `kin_pebblepup` (prefixed: `awaken:mossback` is the gate worlds' grazer), only past the ridge. The land is cut into
  128-block cells; about half hold one site whose kind its biome picks (woods: a Lampwing; plains and meadow: two
  Burrowbuns; swamps and rivers: a Mossback; mountains, windswept land and stony shores: two Pebblepups, with their
  pile). A site fills when a player is 24 to 96 blocks from it and again three days after its beasts are gone. Never
  where the bells carry, in the city's basin, on a settlement's ground or inside a Hearth.
- **For**: each lives with you and does a boring job (below). Never hunted: no drops, and killing one costs Kinship.
- **Know**: the Hearth page's needs each carry a rumour of a real site 100 to 600 blocks out ("A stone-skinned pup that
  hoards shiny stones lives on the rocky slopes, about 300 blocks north-east of your home"), Track gives a rough
  bearing; reading one with the band or Binoculars files its card ("Could live with you", its job).
- **Source**: `kin.KinSpawner` (sites from the seed and biomes; no chunk loads), `kin.KinBeast`.
- **Links**: the Hearth page; Kinship; the Lampwing lesson; Fields; the Ore Shed; §43.11's never stuck.
- **Edges**: a rumour is promised to its site, so the kind it named is what fills there; a player standing on the spot
  waits (nothing pops in at under 24 blocks); a befriended beast never dies (hurt, it hides at its nook and heals);
  its Hearth gone, it goes back to the wild where it stands; `/kill` takes it off its home's list.

### Kinship and the five steps
- **Get**: per player, per kind (saved on the player, schema 1, kept through death): Wary, Curious (10), Friendly (40),
  Kin (100). Help +10, each day one lives with you +4, hurt −15, killed −60 and that kind runs from you for three days.
  Spirit warms it: +1% a point, to +30%.
- **For**: how near a kind lets you come, how much the System puts its sounds into words, and at Kin its own name for
  you (the Lampwing calls you Warm One).
- **Know**: the card's standing line; the bracketed lines up close (only for kinds read).
- **Source**: `kin.Kinship`, `kin.KinWatch` (read 1 s at 32 blocks with the band, 128 with Binoculars; watch 3 s at
  16 to learn one want line of three), `kin.KinHelp` (the right act at its spot; three helps of one beast and it trusts
  you).
- **Links**: the Hearth page's Kin list; homes.
- **Edges**: the help is never stated; a near miss gets a nudge line; after three misses the line points closer, never
  naming the act; armed players scare a Wary kind (the lesson Lampwing doesn't scare).

### The first fire's Lampwing (the lesson)
- **Get**: once per player, the first night they're within 20 blocks of their own Hearth (any tier).
- **For**: the whole loop in a few minutes: read it, watch it, set a warm light where it can see, it trusts you and
  settles at your fire.
- **Know**: "[Something's been watching your fire from the trees. It's on your Hearth page now.]"
- **Source**: `KinHome.lessonTick`; `/awaken admin kin lesson` sends it now.
- **Edges**: quick to read and trust, never scared unless you're armed; settling it ends the lesson; if it wanders off
  the Hearth page still names it as "the one watching your fire".

### Homes and nooks
- **Get**: a trusted beast follows you if your Hearth has a free nook it likes (2 at the Hearthfire, 4, 8, 12, 18, 24 by
  tier, plus 2 once the Ore Shed's heart is set down); it settles when you're both home.
- **For**: daily Kinship, and its job.
- **Know**: the offer line names what's missing ("It needs a tilled field at your home"); Your beasts on the page.
- **Source**: `KinHome.offer/arrive/settle`, `KinWorld` (saved data `awaken:kin`, schema 1).
- **Edges**: no Hearth, no free nook, or nothing it likes: a line saying so, and it stays wild and trusting; follows up to
  48 blocks behind, then gives up until you come back.

### The beasts' work
- **Lampwing**: up at night over its roost, which is lit (a real light, level 13, gone by day); nothing from the dark
  spawns naturally within 32 blocks of a Lampwing's roost.
- **Burrowbun (Fields)**: harvests ripe crops within 14 of its nook, plants them again from what it picked (or seeds in
  the field's chest), carries the harvest to the nearest chest or barrel within 8.
- **Mossback**: crops within 8 grow faster (six random ticks every five seconds by day).
- **Pebblepup (the Ore Shed)**: one item every two seconds from the shed's barrels (within 4 of the heart): rubble
  (cobblestone, cobbled deepslate, tuff, andesite, diorite, granite, calcite, netherrack, blackstone) to gravel, gravel
  to flint (1 in 4) or sand, an ore block to its drops as an iron pick would; one nugget in 32 from rubble or gravel.
  Outputs go onto a barrel already holding them, else the emptiest. Never ore from nothing.
- **Source**: `KinHome.work`; the page shows what each made today.
- **Edges**: works only while its Hearth stands; sleeps by night (the Lampwing by day); a full chest leaves the harvest
  carried until there's room.

### The Ore Shed (was the Mine head)
- **Get**: room type `MINE_HEAD` (constant kept so saved rooms read the same), now "Ore Shed": a closed room with a lode
  heart (new furniture `LODE_HEART`) and a barrel.
- **For**: the Pebblepups' work and their two nooks.
- **Know**: the Hearth page ("The Ore Shed needs a lode heart. Hearts beat inside lodes, deep underground; break one free
  and bring it home."); setting a heart down at home says to build the shed round it.
- **Source**: every `*_lode_heart` loot table now also drops the heart itself (they left the never-held list); a heart a
  player set down drops only itself when broken again (`KinWorld.placedHearts`, `KinHome.heartDrops`), so it never
  gives its ore twice.

### The Hearth page
- **Get**: the band's seventh page, Hearth, between Journal and Truths.
- **For**: Home (the tier and nooks, each beast need with its rumour and Track, the next tier's needs, the Great Belfry
  pinned and dim), Your beasts (what each is doing now, how it's feeling, what it made today), Kin (met cards: where and
  when, "Could live with you", its job, its want so far or "?", your standing, its latest line; heard-of kinds as dim
  outlines with their rumour).
- **Know**: the rail; the lesson's line; reading a kind.
- **Source**: `KinPage` (server), `KinNet` (payloads `kin_page`, `kin_ask`, `kin_track`), `kin.client.KinPageSection`.
- **Edges**: no home: "No home yet..."; the Kin list always holds a rumour not yet followed when one exists within 600
  blocks; nothing on it expires or renews daily; a rumour unfollowed for a play-day gets plainer (distance to 50 blocks
  and the land's name), after two Track's bearing narrows from ±48 to ±8.

## Stage 3: Faults, the Ringer's Great Fault, the first raid (§43.8)

### Faults (the kit)
- **Get**: played by the server through `fault.Faults` (`play`, `playNear`, `mend`, `mendNear`): a flicker (14 ticks), a
  Fault (about 4 s) or a Great Fault (10 to 20 s; each key plays once per player, counted `awaken:fault_<key>`, then as a
  Fault).
- **For**: the start of every Big Day that threatens you and of each Proving; the mend ends a woken event (and, from
  stage 4, every bell).
- **Know**: it is the opening's failure again (the tell, the tear, the System's words in the black, the held cut, the
  plain line after), so the player learns it once.
- **Source**: `fault.Fault`, `fault.Faults`, `fault.FaultNet` (`fault_play`, `fault_mend`), `fault.client.FaultHud`;
  sounds `fault.tell/tear/voice/drop/mend/flicker` from `tools/art/specs/audio_fault.py`.
- **Links**: the Ringer; raids; stage 4's bells (the mend).
- **Edges**: client setting `faults` full/gentle/still (`config/awaken-faults-client.toml`); screen flashes or camera
  shake off play FULL as GENTLE; band layouts change at most every 7 ticks (under three a second) and slide, never
  strobe; a Fault replacing a running one ends its cut first; logout ends it and the edge; the edge gives up after 20
  minutes without a mend; a client without the mod's channel is skipped; Lens off still plays it (the crush is extra).

### The Ringer's Great Fault
- **Get**: the D-rank Reassessment (the waves trial, until the Ringer's hall is built) opens on the Great Fault
  `ringer`: "[ERROR] … THAT'S NOT OUR BELL / READING · RANK ▮ · RANK ▮ · RANK ▮", the leak "it rang for you last time too",
  then "That isn't the Registry's bell. Something else is ringing it."
- **For**: the first Proving's surprise; it ends on the mend when the trial ends, held or not.
- **Know**: it comes unannounced, the first break since the awakening.
- **Source**: `hearth.trial.Trials.begin` / `end`.
- **Links**: Faults; raids (D rank opens them).
- **Edges**: practice runs and every later D attempt play it as a plain Fault; the first wave waits for the screen to
  come back; the Ringer itself (the Drowned Belfry, the Tether, the Tenor) is GOALS part 2's Proving, not built here.

### Raids (the first Big Day)
- **Get**: per home, once its owner holds D rank (the first Great Bell's stand-in until the Ringer gives the Tenor):
  never in a world's first two hours of play, at most one Big Day every five play-days, due a play-day or two later and
  never said when.
- **For**: the night you hold your home. Rewards: the Lord's Heart (dropped by the raid's Lord), Kinship a step with
  every kind living there for each player who held (and a shiny stone from a Pebblepup), `awaken:raids_held`, a best
  moment. The Big Day bell joins with stage 4's bells.
- **Know**: the day before, a note (never an order): "Your beasts are frightened of something to the east." (with no
  beasts: "The wild things are all moving away from the east."); four wild kin run past the house the other way; the
  home's beasts carry things into their nooks and its Lampwings keep to their roosts.
- **Source**: `bigday.BigDays` (schedule, warning, dusk, waves, Lord, dawn, the away raid, mending), `bigday.BigDayData`
  (`big_days`, schema 1). Commands `/awaken admin bigday raid [first] | dawn`.
- **Links**: Faults (a Great Fault `first_raid` the first time, a Fault after, with a held look along the breach's
  bearing); the kin (`KinHome.hide`, `checkOn`, `stream`, the unease in `KinHome.work`); the gates (the breach is the
  nearest standing gate within 700 blocks, its theme the raid's; else 400 blocks off on the home's own bearing and the
  owner's D theme); the Lampwings' spawn guard keeps the yard free of risers.
- **Edges**: waves 4, 6 and 8 (0, 1, 2 elites) come 52 blocks out along the bearing, the next when two or fewer are left
  or after a minute, the Lord (a roaming break Lord, level 12 the first time, the owner's level after) when one is
  left; they hunt whoever of the household is within 48, else walk to the Hearth; first light (or a slept night)
  clears what's left and mends. Nobody home at dusk: at dawn it breaks at most 10 doors, trapdoors, gates, wooden fences
  or glass on its side (never a chest, a rack, a bed, the fire or a beast; put back one every 15 s where nothing has
  been put since) and carries at most 9 stacks of food out of the fullest larder chest, left in a chest 70 blocks along
  its trail and tracked on the card. Peaceful: no raid (the gate settles). The server down at dusk or a skipped night:
  it came while you were away. A Hearth taken up drops its Big Days. Shared Hearths: every member home counts and is
  hunted; the warning and the away note go to every member online.

---------------------------------------------------------------------------------------------------------------------

## Stage 4: the first thirty bells and the tower's song (§43.7)

### The hundred and the Bell Book
- **Get**: the Book is yours from your first bell (it lives on you, kept through death). Thirty bells can be rung in
  stage 4 (SPEC §43.7 "Built (stage 4)" lists them); 00 and 31 hold their places; the rest are empty slots with a rank.
- **For**: the long goal of the game: a hundred bells, each a note in your song and most with a voice that helps your
  home; three together make a peal.
- **Know**: the first bell's card says where it went ("It's gone home to your tower") and what it does; six seconds
  later the Signal says there were a hundred and to find them; the Codex shows the hundred, each slot's rank telling
  how far off it is.
- **Source**: `bells.BellCatalog`, `bells.BellBook` (attachment `bell_book`, schema 1), `bells.Bells` (public API: `ring`,
  `has`, `count`, `voice`, `lordFell`, `holdfastHeard`), counter `awaken:bells`.
- **Links**: every system that gives one (below); the tower; the voices; stage 5's leads (Sung, Found).
- **Edges**: a bell rings home once per player; held bells stay held through death; the slots never name a bell you
  have no lead on.

### Bells from moments (Ember, Nook, Lintel, Founding, Registry, Tenor, Second, Garden, Drowned Choir, Dreaming, Spirit, Night, Watch, Curfew, and the gate worlds' six)
- **Get**: doing the thing: a Hearth, a beast living with you, a Grand room, Hearth tier 2, the first gate cleared, D
  and C rank, Descent strata 2 and 3, the first lucid dream, the first spirit spoken to, a raid held to dawn, ringing a
  holdfast's bell from inside its walls (Watch, then Curfew), and the Lord of a Bell Crypt, Fungal Hollow, Ember Mine,
  Hive, Overgrown Temple or Lantern Night Market gate at the bell's rank or above.
- **For**: each is its voice in your tower and its note in your song.
- **Know**: the moment itself (the card names the bell and where it rang); a holdfast's bell is filed as a Rung lead
  the first time you stand inside its walls ("Ring it yourself and its note goes home with you").
- **Source**: `Bells.moments` (every 5 s per player), `BigDays` dawn (Night), `gates.GateEvents` (a Lord's death),
  `village.Holdfast.bellAt` with a right-click on its bell (`BellModule`).
- **Links**: the Hearth, kin, the Registry, Provings, raids, holdfasts, the gates, the Descent, Lucidium, the Veil.
- **Edges**: states already true in an old world ring at once the first time (an old Hearth rings the Ember); a gate
  below the bell's rank gives nothing (the Crypt Bell wants D); the whole run's party within 96 of the Lord shares it;
  without a home the bell is held and hangs once there is one.

### Sleeping places (Wren, Barrow, Mere, Wayside, Cairn, Spire, Reed, Thorn, Crag, Fen)
- **Get**: out in the land: the Wren Bell 90 to 160 blocks from every home; the others in the 640-block cells past the
  ridge, E bells nearest, then D, then C. Strike the bell (use or hit) once it's free: dug out, dived for, climbed to, or
  its keeper killed.
- **For**: the bells nobody hands you, and the land put back: a grey, dead patch turns green again round a woken bell.
- **Know**: the hum. Within 64 you hear a bell's note from its direction (a Heard lead with a bearing, in the Book);
  from another side it crosses into an area you can Track; within 24 the grey ground is Seen ("Something here is keeping
  it that way").
- **Source**: `bells.BellSites` (planned from the seed, built when a player comes within 96), `bells.BellWorld` (saved
  `bells`, schema 1), block `awaken:sleeping_bell` (no item: it can't be broken or moved, and drops nothing).
- **Links**: the Book's leads and Track; the keeper is a themed elite (`MobHooks.spawnThemed`, gate id `bell:<key>`);
  the Fault mend; the tower.
- **Edges**: no site in the city, a Hearth or deep water (the cell stays empty); a home's Wren site avoids the city
  (12 tries, else 120 east); Peaceful: no keeper, the bell is free; a keeper that despawns comes back next time someone
  is within 16; everyone within 48 at the waking rings it home; a woken bell still rings home for a player who arrives
  later and strikes it; the painting back finishes even if the player leaves.

### Leads (Heard, Seen, Rung)
- **Get**: filed by the System when you hear, see or stand at a bell (above). Sung (the Chimewren) and Found (the
  Stilled's letters) are stage 5's sources; Remembered is the Deep's.
- **For**: direction without telling: a bearing, then an area, then the place.
- **Know**: the System's bracketed line when each is filed; the Book's Bell Leads lists them newest first.
- **Source**: `Bells.heard/seen/holdfastHeard`, `Bells.track` (the top-right card, kind `bell`).
- **Links**: `core.api.Destinations` (the card); the Book page.
- **Edges**: one bearing: Track points to where you heard it; two bearings: the area's middle, up to 20 blocks off the
  bell, so the last stretch is yours to search; ringing the bell clears its lead and the card.

### The bell frame, the belfry and the tower
- **Get**: the bell frame comes with your first bell; lost, make one (planks over logs and a bell). Set it down inside
  your own Hearth.
- **For**: where your bells hang and your song rings from; at 20 bells a belfry goes up round it, at 50 a stone tower.
- **Know**: placing it says how many hang and that they ring at dawn and dusk; using it says how many more until the
  belfry or tower; the Book's Your Tower says the same.
- **Source**: block `awaken:bell_frame` (loot table drops itself), `bells.BellTower`; belfry and tower built from vanilla
  blocks (stripped dark oak, dark oak planks, deepslate tiles, stone bricks, bells).
- **Links**: the Hearth; every member's bells; the song; Gather's meeting place.
- **Edges**: outside your own Hearth it pops off with a line; a second frame pops off and says where the first is; a
  broken frame drops and the song rings from the Anchor until a new one is set; the belfry or tower waits for clear
  space (a once-a-day note) and tries again at every song; a tower takes the belfry's leftovers down; shared Hearths
  hang every member's bells and a bell once hung stays.

### The song and the beasts
- **Get**: every dawn and dusk at home, from the frame (the belfry's or tower's top once built).
- **For**: hearing what you have and what's missing: each bell's note in its place, a knock where one's gone; and your
  beasts' answer, which grows with your bells (hide, hunker, listen, gather, sing).
- **Know**: it rings on its own; the Book has "Play the song" to hear it anywhere.
- **Source**: `BellTower.tick/song`, payload `bells_song`, `bells.client.SongPlayer`, `KinHome.song`.
- **Links**: the kin; the hundred; stage 7's Hundred Peal.
- **Edges**: heard within 256; a night slept through still wakes to the dawn song; a restart in the song's window may
  ring it again (harmless); no bells hung, no song.

### Voices
- **Get**: each bell's voice works while it hangs at home (the card and the Book say what it does).
- **For**: Rest, Growth, Kin, Ward, Stride, Glow, Shed, Raid, Homeward (numbers in SPEC §43.7).
- **Know**: the bell's card; Your Tower lists every voice your bells have, with counts.
- **Source**: `Bells.voice` (public), used by `Bells.body`, `BellModule` (crops, spawns), `kin.Kinship`, `kin.KinHome`,
  `bigday.BigDays`, `Peals`.
- **Links**: the Hearth, kin, raids, the Ore Shed, the handbell.
- **Edges**: counted over the home's hung bells, so a household shares them; caps keep them from running away (Growth
  and Kin count 5 bells at most, Ward 80%, Shed 1 in 16).

### The handbell and its peals
- **Get**: with your first bell; lost, make one (a stick over two copper ingots).
- **For**: a plain ring (out in the wild the land's beasts run from it for a minute), and the peals: Homeward (Ember,
  Wren, Wayside), Gather (Nook, Founding, Root), Stillness (Tenor, Night, Watch).
- **Know**: its tooltip ("Sneak and use to change the peal"); a bell's card names a peal it completes; Your Tower shows
  peals you have and those one bell away.
- **Source**: item `awaken:handbell`, `bells.Peals`.
- **Links**: the Hearth (Homeward, Gather), kin (Gather, the scare), raids (Stillness).
- **Edges**: Homeward: no home, not the overworld, moved or hit while it gathers, or rung again inside 5 minutes: it
  says why; a restart cancels one gathering. Gather with no beasts says so. Stillness only during a raid's night at your
  home, once a night. A peal whose bells you no longer all hold (never, today) falls back to a plain ring.

### The Bell Book page
- **Get**: the Codex, once you hold a bell: The Hundred Bells, Bell Leads, Your Tower.
- **For**: seeing the whole goal at a glance, following leads, knowing your tower.
- **Know**: the first bell's Signal line; the Codex tab.
- **Source**: `bells.BellPage` (built on the server), `bells.BellNet` (`bells_page`, `bells_ask`, `bells_track`),
  `bells.client.BellBookSection`.
- **Links**: Track and the top-right card; the song player.
- **Edges**: the page asks every two seconds while open; hovering never moves the page (two lines kept under the
  board); a bell you have no lead on shows only its number and rank.

## Stage 5: the rest of the kin and the works to the Foundry, with the Storehouse (§43.12)

### The eight working kin (Totter, Thatchback, Emberkit, Puffwool, Dumplin, Pondling, Snufflet, Chimewren)
- **Get**: meet one at its site (the biome table in §43.12), read it, watch its three want lines, help it three times,
  and give it a nook it likes at home.
- **For**: each runs one work: Totters carry and shelve, Thatchbacks build from plans, Emberkits smelt, Puffwools give
  wool, Dumplins eggs, Pondlings fish, Snufflets forage, Chimewrens find bells.
- **Know**: a rumour on the Hearth page names the sort of animal and its place; its site is dressed for its want (the
  scattered heap, the short dam, the cold spring, the burrs, the empty nest, the rotten pond, the snuffled spot, the
  same three notes); the want lines say it plainly by the third; the card says "Could live with you" and its job.
- **Source**: `kin.KinSpawner` (sites and dressing), `kin.KinHelp` (helps and nudges), `kin.KinHome` (nooks),
  `kin.KinWorks` (work), art `tools/art/specs/kin.py`.
- **Links**: Kinship (§43.5); the Hearth page's needs (each new kind leads a need line once a work wants it); the works
  below; bells (Chimewrens hear yours).
- **Edges**: a help only counts from a player (a tossed item needs a thrower); Dumplins only follow a player standing
  1.5 blocks above them, on the ground, within 10, and let go past 32; a flock left at its nest 2,400 ticks without
  help is lost again 16 to 24 blocks off; pond rot regrows while any is left; the Snufflet's spot moves after each
  dig; Chimewrens need a player who holds a bell; a frightened kind (a killing) won't take help for three days; an
  Emberkit can't burn; a Puffwool brushed at home gives wool once a day per beast; a nook that goes away sends the
  beast looking for a better one every 1,200 ticks.

### Work Plans
- **Get**: craft one (paper, coal, paper over planks and two sticks). Its recipe is in the book from the first paper.
- **For**: a Thatchback builds the work for you: Smeltery, Rookery, Foundry, Storehouse or Ore Shed.
- **Know**: using it reads the bill (only what isn't already standing) or how far it's got and what it waits on;
  sneak-use changes the work; the Hearth page lists each plan's progress; with no Thatchback it says you can build the
  room yourself.
- **Source**: block `awaken:work_plan` (drops itself), `kin.works.WorkPlans`, `WorkPlanBlockEntity` (schema 1).
- **Links**: Thatchbacks; every work; the Hearth's room scan (the built room reads as its work).
- **Edges**: materials come from any chest or barrel within eight blocks that holds them (the plan's own barrels never
  hide yours); no glass walls the window up; no lanterns leaves them out; a block of yours in the way is built round;
  a block already right is counted; the work can't change once building starts (break the plan and lay it again);
  plans more than 64 blocks from the Thatchback's nook are left for one nearer.

### Smeltery and its Emberkit
- **Get**: a room with a vent stone and a furnace (a Work Plan builds it; vent stones come from Emberkit springs).
- **For**: ore and raw metal you bring are smelted with no fuel; the Forge stays yours.
- **Know**: the Emberkit's need line names a Smeltery or a lit campfire; at work it lies at the vent and the furnace
  crackles; the Smeltery bells say they speed it.
- **Source**: `KinWorks.emberkit`, block `awaken:vent_stone` (drops itself).
- **Links**: Totters carry its ingots to the Armoury; bells 14 and 45 (SMELT).
- **Edges**: only ore and raw materials, never anything else; with every chest in the room full the ore waits; no Smeltery,
  the Emberkit warms a campfire nook and smelts nothing.

### The Storehouse, the Drop and the Ledger
- **Get**: the Drop (planks, a granary keystone and two barrels; keystones stand in every town's granary), the Ledger
  desk (a book and quill over planks and sticks), in a Storehouse room with chests or barrels; Totters living with you.
- **For**: one place to drop everything; Totters shelve it by sort; the Ledger finds anything.
- **Know**: the Drop says how full the heap is and whether the hall is full; new bays get a painted sign; the Ledger
  lists every bay with a search; the Totter's need line names the Storehouse.
- **Source**: `kin.works.Storehouse`, `DropBlockEntity` (schema 1, 64 stacks), `client.DropRenderer`,
  `client.LedgerScreen`, payloads `works_ledger` and `works_ledger_ask`.
- **Links**: Totters; the Smeltery's and fields' carrying; the Armoury and Kitchen.
- **Edges**: a full heap refuses more and says so; a full hall puts the armful back and the Totter rests; "empty my
  bag" keeps the hotbar, armour and marked items; a fetch with no Totter free is set out on the desk at once; a
  restart drops a Totter's armful where it stood; the heap draws a capped number of items.

### Rookery and the Chime Perch
- **Get**: the Chime Perch (planks over a woken clapper and a stick; a clapper comes from every bell you wake) in a
  room; Chimewrens living with you.
- **For**: one Sung lead a day for each member: a bearing toward a sleeping bell you have no lead on.
- **Know**: the Chimewren's need line names the Rookery; the lead's line names the direction; the Book files it.
- **Source**: `KinWorks.chimewren`, `Bells.sung`, block `awaken:chime_perch` (drops itself).
- **Links**: the Bell Book and Track; the hundred.
- **Edges**: offline members get none that day; no unled sleeping bell within 1,200 blocks gives no lead and no line.

### Foundry and the Crucible
- **Get**: the Crucible (bell bronze, deepglass shards and a furnace) in a room with chests.
- **For**: the Singing Deep's five bells cast from metal you bring (§43.12 lists the moulds).
- **Know**: using the Crucible says which bell is next and what it still needs; it glows while casting; every member
  hears the bell ring home.
- **Source**: `KinWorks.foundry`, block `awaken:foundry_crucible` (drops itself), `KinWorld.cast`.
- **Links**: the Singing Deep's metals; the Bell Book; Thatchbacks (they halve the time).
- **Edges**: the metal is taken only at the end, so a cast cut off by a restart or an unloaded chunk starts again;
  members away get the bell when they next come home; the True Bell is never cast.

### Sung and Found leads
- **Get**: Sung from a Rookery's Chimewrens; Found from an old letter a Snufflet's spot gives up (1 in 4 digs).
- **For**: direction to a sleeping bell without being told where.
- **Know**: the bracketed line when it's filed; the Book's Bell Leads list.
- **Source**: `Bells.sung`, `Bells.found` (public API).
- **Links**: Track; the sleeping places.
- **Edges**: a Found lead points at an area 20 blocks off the bell; with no sleeping bell within 800 the letter is
  something ordinary instead; Found tracks only if nothing else is tracked.

### Home outputs (wool, eggs, fish, forage)
- **Get**: a Puffwool brushed at home (3 wool a day), Dumplins (2 eggs a day), Pondlings (4 fish a day), Snufflets (4
  finds a day: mushrooms, berries, herbs).
- **For**: the tended things a home grows; never ore, gems, gate loot or XP.
- **Know**: the beast's Your beasts line says what it made today.
- **Source**: `KinWorks.dumplin/pondling/snufflet/brushed`.
- **Links**: the nearest chest; Totters carry food to the Kitchen.
- **Edges**: with no chest within eight blocks, or a full one, it's set down on the ground by the beast; Pondlings need
  water by their nook; only members get wool; the day's count starts again each morning.

### New blocks and items
- **Vent stone**: two at every Emberkit spring; drops itself. **Pond rot**: on wild Pondling ponds; drops nothing, never
  held. **Woken clapper**: from every bell you wake. **Granary keystone**: in every town granary; drops itself.
- **Source**: `WorksModule` (`Sources.declare`), loot tables, recipes above.

## Stage 6: the second half's bells and beasts (§43.13)

### Bells from the second half (gates, the Court, the Above, the Deep, the city)
- **Get**: a gate Lord felled at its bell's rank or above (every theme has one); a Sovereign felled (its domain's bell,
  for everyone within 96); the Descent's strata reached; the Above's firsts (wind, island, Halo, star, storm); a Siege
  with the Night Bell at B rank; the Carillon's four (Roost, Choir Square, market, Quay) by rank or bell count.
- **For**: the hundred filling as you go through the whole game, each bell a mark of a place or a deed.
- **Know**: the bracketed line and the Book; due city bells you're away from are remembered aloud with where they are.
- **Source**: `Bells.lordFell`, `Bells.sovereignFell`, `Bells.moments`, `Bells.city`, `BigDays`.
- **Links**: the tower's song; the voices.
- **Edges**: a Sovereign's kill credit must be a player; a bell already held never rings twice; city bells ring only in
  the city.

### Bells of the land further out, and people remembering them
- **Get**: the C, B and A bands' sleeping bells (the people's homes, the Hill, the Ford, the Thaw, the Boulder, the
  Leviathan's), woken where they lie.
- **For**: reasons to travel far out as rank rises.
- **Know**: once a play-day in Carillon someone remembers one still asleep within 7,200 blocks (a Remembered lead with
  an area 20 blocks off).
- **Source**: `BellSites` (bands and pools), `Bells.city` (Remembered leads), `BellBook.rememberedDay` (schema 2).
- **Links**: Track; the Book's leads.
- **Edges**: none within reach and no lead is filed; a lead for a bell since woken drops from the Book.

### Spirit bells and dream bells
- **Get**: spirit bells (the Veil: five, on their own 960-block grid) rung by a spirit coming within 3; dream bells (the
  Lucidium: five, one a dream) struck lucid enough (40, 60, 80) and carried out on waking.
- **For**: the Veil and the Lucidium each holding part of the hundred.
- **Know**: a body near a spirit bell feels "cold and still" (a Seen lead); a spirit hears its hum; a dream says a bell is
  ringing; waking without it files a Dreamt lead.
- **Source**: `BellSites.tickSpirits`, `DreamBells`.
- **Links**: the Veil (`Veil.isSpirit`), the Lucidium (`Lucidium.isDreaming`, `lucidity`).
- **Edges**: a dream isn't saved (logging out forgets it); too dim a strike says so plainly; a spirit bell's toll reaches
  only spirits.

### The four last kin (Bumblepuff, Loomworm, Cloudcalf, Boulderback)
- **Get**: met in the wild (flower fields; old, dark and birch woods; the sky over peaks 4,800 out; caves under the
  land), helped three times, then they follow you home to a nook.
- **For**: honey and wax, thread and cloth, rain on the fields, stone and faster building.
- **Know**: three watch lines each, vague to plain; rumours on the Hearth page with the sort of place and a real region.
- **Source**: `KinSpawner` (sites), `KinHelp` (acts), `KinHome.nookFor`, `KinWorks.daily/rain`.
- **Links**: the Cloud Bell (a Cloudcalf brought home); Thatchbacks build faster with a Boulderback home.
- **Edges**: a Bumblepuff with one or two flowers asks for lots; a Loomworm refuses the wrong tree; a Cloudcalf won't
  follow anyone more than 4 below it and is lost again two minutes after it's home; a Boulderback backs from any light
  in either hand; with no cave under its site it gets a cut pocket at y -30; each day's output starts again each morning.

### The wild's bells (Stampede, Flood, Eclipse)
- **Get**: from C rank, witness a Stampede (Stampede Bell) or the Deep Tide on a new moon (Flood Bell); at A rank, stand
  under open sky in the middle of a new moon's night (Eclipse Bell).
- **For**: the last three of the hundred's sources; reasons to be out in the wild at its biggest moments.
- **Know**: the bracketed ring line; the Book lists them under the wilds.
- **Source**: `Bells.witnessed` (from `Pulse.witness`), `Bells.moments`.
- **Links**: the World Pulse's happenings (§21.6); Stride, Breath and Sight voices.
- **Edges**: witnessed below C rank it rings nothing then; the next Stampede or Deep Tide after C does; a roof over you
  on the new moon's night and the Eclipse Bell waits.

## Stage 7: the Hundred Peal, the Kinsong, the Deep's answer and the Wake (§43.14)

### Heart-clapper (item)
- **Get**: the Lord of any gate world at S rank or above, to the killer and their party within 96.
- **For**: one of the Great Belfry's two pieces, set into your bell frame by hand.
- **Know**: its tooltip says what it is and where it goes; the Hearth page's Great Belfry pin names it from A rank;
  the Bell Book's next line names it once your tower is full.
- **Source**: `Bells.lordFell` → `Bells.belfryPiece`; `Sources.declare` in `BellModule`.
- **Links**: S-rank gates (§11), the stone tower (§43.7), the Seed Bell.
- **Edges**: never given to someone already carrying one, or whose home has set it or has a Great Belfry; lost or
  dropped, the next S-rank Lord gives another. Set into someone else's frame, or before your tower stands, it says why
  and stays in your hand.

### Sovereign's Relic (item)
- **Get**: every Sovereign felled, to the killer and their party within 96.
- **For**: the Great Belfry's other piece.
- **Know**: as the Heart-clapper.
- **Source**: `Bells.sovereignFell` → `Bells.belfryPiece`.
- **Links**: the Court (§37), Grieving Sovereigns.
- **Edges**: as the Heart-clapper; eight Sovereigns, and they come back, so it's never the last chance.

### The Great Belfry (tower stage 3)
- **Get**: both pieces in the frame of a stone tower (50 bells); your beasts raise it in 30 s, or with none it goes up
  at the next song.
- **For**: holds all hundred bells; the Seed Bell; the Hundred Peal rings from it.
- **Know**: the frame's lines as each piece goes in; the Hearth page pin (needs, then "stands"); the Bell Book's next line.
- **Source**: `GreatBelfry.offer/begin/tick/build`, `BellTower.great`, `BellTower.song`.
- **Links**: kin gather (§43.6), the tower (§43.7).
- **Edges**: needs a clear 7 by 7, 23 high; blocked, a note to members and it tries again every song. The old belfry
  comes down under it. A member away when it goes up gets the Seed Bell the next time they're about.

### The Seed Bell (00) and the True Bell (36)
- **Get**: the Seed with the Great Belfry; the True Bell cast at your Foundry (8 Starmetal Ingots, 16 Bell Bronze) while
  a member of S rank is online.
- **For**: the last two of the hundred; Kin and Glow voices.
- **Know**: the Seed's ring line; the Foundry's status says the True Bell waits for an S-rank member.
- **Source**: `GreatBelfry.seed`, `KinWorks.MOULDS` ("true"), `KinWorks.next`, `KinWorks.foundryStatus`.
- **Links**: Starmetal (§34), the Foundry (§43.12).
- **Edges**: the Seed can't be the bell you're missing at 99: the Great Belfry needs only fifty. `bells tower <n>` skips it.

### The Hundred Peal
- **Get**: the first song after your Great Belfry holds all hundred bells; once per home.
- **For**: a Kinship step for every kind you've met, the Kinsong, the Wake opening, a best moment; wild kin come to the tower.
- **Know**: the REVELATION to everyone in the overworld, the members' summary line, the Bell Book ("Your song is whole.
  Ring your bells..."), the ending screen's Wake card says it needs it.
- **Source**: `HundredPeal.due/ring`, `KinApi.hundredPeal/peal`.
- **Links**: Kinship (§43.5), the Wake (§43.14), the Deep's answer.
- **Edges**: a member offline at the time gets the Kinsong and the Wake the next time they're about (`Bells.moments`);
  another home's Peal later still steps up its own members but the Deep answers only once.

### The Kinsong
- **Get**: the handbell's peal for anyone with the Hundred Peal (sneak-use cycles to it).
- **For**: calming the land's beasts and stampedes within reach, freeing the Gnawed, keeping the Gnawed off for 3 days
  within 256.
- **Know**: the Bell Book's tower list shows it with its words; the overlay says what it calmed.
- **Source**: `Peals.ring` → `HundredPeal.kinsong`, `KinApi.kinsong`, `LivingHooks.calm` → `Pulse.calm`.
- **Links**: stampedes (§21.6), the Gnawed.
- **Edges**: never touches monsters, gate things, wardens, bosses or camps; after the Deep answers it draws 2 to 4 Deep
  creatures, at most once in five minutes; creative and spectator players draw nothing.

### The Deep's answer (world state)
- **Get**: the world's first Hundred Peal; 30 s later the Great Fault `deep_answer`.
- **For**: the endgame's pressure: gates break in half the time, spill half as many again, 10 levels higher; angrier
  Sovereigns; the Gnawed.
- **Know**: the Great Fault's plain line says exactly that.
- **Source**: `HundredPeal.answer`, `BellHooks.deepAnswered` read by `Breaks`, `Court`, `KinSpawner`.
- **Links**: breaks (§11), the Court (§37), the Gnawed.
- **Edges**: saved (`deep_answered`), so a restart keeps it; a player offline at the time doesn't see the Fault (they
  hear about it in the Gnawed's and the breaks' lines).

### The Gnawed (kin state)
- **Get**: after the Deep answers, one in four new kin sites, away from a recent Kinsong.
- **For**: something to free rather than kill; a Help step of Kinship.
- **Know**: a flicker and a line the first time; a line every 2 minutes after; the look (dark tint, ember eyes, ash).
- **Source**: `KinSpawner`, `KinBeast.gnawed`, `KinWatch.sawGnawed/freed`, `KinApi.peal/kinsong`.
- **Links**: Kinship, the Kinsong, the Hundred Peal.
- **Edges**: can't be killed by a player (freed at 1 health); dies to other things like any beast with no Kinship cost.

### Grieving Sovereigns
- **Get**: after the Deep answers and all eight Sovereigns are beaten, each comes back Grieving (+20 levels).
- **For**: endgame fights; half a level and a REVELATION the first time you fell each.
- **Know**: the name ("Grieving <name>").
- **Source**: `Court.courtTick`, the Court's death handler.
- **Links**: the Court (§37), the Sovereign's Relic.
- **Edges**: the reward once per Sovereign per player; they keep coming back.

### The Wake's door (Not yet)
- **Get**: the ending screen at the Seed.
- **For**: the Wake needs the Peal; "Not yet" lets you leave and come back.
- **Know**: the dimmed Wake card's words; "The Seed will wait."
- **Source**: `Court.offer/choose/laterTick`, `EndingScreen`.
- **Links**: the Descent (§8), the Hundred Peal.
- **Edges**: offered again on coming back down to the Seed; the server refuses a Wake without the Peal (a re-offer).

### The risen and Ilse's visit
- **Get**: after the Wake: the Stilled stand up in loaded chunks; three play-days on, Ilse visits each home once by day.
- **For**: the world waking up; the end of Ilse's and Aldo's story.
- **Know**: they're in plain sight and talk when spoken to; a bracketed line when Ilse comes up the path.
- **Source**: `StilledPlacer.rise`, `LivingHooks.risen/visit`, `Wayfarers.risen/visit`, `Wayfarer.risen/visiting`, `Bells.ilse`.
- **Links**: the Stilled (§43.3), the Last Fire (§21), the Wake.
- **Edges**: a Stilled in an unloaded chunk stands the next time its chunk loads near a player; Ilse doesn't come by night
  or when nobody's at the fire, and comes the next fair day; she visits a home once (`ilse` in `bells`).

---------------------------------------------------------------------------------------------------------------------

## Bells you carry and hang (§43.16 stage 2, plan docs/director/BELLS.md §4)

### The bell (`awaken:bell`, one item for all hundred)
- **Get**: winning a bell anywhere (`Bells.ring`): it's filed in the Book and put in your hands; Ilse puts the Ember Bell
  in your hands at the coal; the Lampwing's old bell is lifted from its tree. The far six (Roost, Square, Market,
  Harbour, Watch, Curfew) never become things: they stay where they hang.
- **For**: hung at home it's a note in your song and its voice; three hung make a peal.
- **Know**: its card ("It's yours. Take it home and hang it."; a Great Bell's names the Great Beam), its tooltip (number,
  rank, what it does while it hangs, where it hangs), the first bell's line about the Bell Frame, the Bells tab's step
  "Hang the bells in your bag".
- **Source**: `bells.BellItem`, component `awaken:bell` (the number), `Sources.declare("bell", ...)`.
- **Links**: the Bell Frame, Bell Beam and Great Beam; the Book; peals; the song.
- **Edges**: one a stack; can't burn, be hurt or despawn; falling out of the world brings it back to its owner's hands
  (or to the world's spawn); never dropped at death (owed back to your hands); a bell already in your home's song won't
  hang twice ("This one's yours to keep"); used in the hand it rings its note; on any other block it says how hanging works.

### The Bell Frame, the Bell Beam and the Great Beam
- **Get**: made at a table: the frame from planks and logs (no vanilla bell any more), the Bell Beam from logs and iron
  nuggets, the Great Beam from logs, iron chain and an iron ingot.
- **For**: hooks: the frame 3 (by your fire, no height rule), a Bell Beam 4 (8 or more above your fire), a Great Beam 1
  (a Great Bell, 12 or more above your fire).
- **Know**: tooltips; the frame's placing line; the refusals in plain words ("A bell hung this low won't carry...");
  the frame-full line names the Bell Beam; the Bells tab names each next step.
- **Source**: `bells.HookBlock` (`BellFrameBlock`, `BellBeamBlock`), `bells.HooksEntity` (`bell_hooks`, schema 1),
  `bells.Hanging`; recipes `bell_frame`, `bell_beam`, `great_beam`; loot drops itself.
- **Links**: the Hearth (only inside one you belong to); the song (`BellTower.places`); the Great Belfry (stage 3+).
- **Edges**: breaking one drops its bells as items and takes them out of the song; only whoever hung a bell (or the
  owner) takes it down (sneak-use, empty hand); empty hand rings the nearest bell; hooks face you when placed.

### Found bells and the Lampwing's old bell (`awaken:found_bell`, no item)
- **Get**: a bell lying where it was won; the first is the Nook Bell, hung by the game under a branch of the nearest
  tree to a new home's Lampwing perch (else from an old post) the first time its owner comes near.
- **For**: the Nook Bell is the home's own first find; the same block carries stage 4's Lord bells.
- **Know**: it glows while something sleeps in it ("[Something's asleep in it.]"); the day a beast comes to live there:
  "[The Lampwing's left its old bell in the tree. It's yours.]"; the Bells tab: "Take the Lampwing's old bell down".
  The first fire's Lampwing perches by it.
- **Source**: `bells.FoundBells`, `FoundBellBlock`, `FoundBellEntity` (schema 1); `Sources.neverHeld("found_bell")`.
- **Links**: kin (`KinWorld`), `BellHooks.roost`, KinHome's lesson.
- **Edges**: unbreakable and immovable; someone from another fire can't lift a home's; a bell left for named hunters
  (claimants) is theirs alone, and the block goes once every one of them has lifted theirs; a home whose owner already
  holds the Nook never gets one.

### Old saves (Book schema 5, World schema 3)
- **Get**: automatic, once per Book.
- **Know**: "[Your bells are waiting to be hung. Use each one on your Bell Frame by your fire.]"
- **Source**: `Bells.upgrade`, `Bells.owed`.
- **Edges**: every held bell but the far six comes back as a thing to hang; the far six get their places (the city's,
  the holdfasts' heard from inside, else the square, else where you stand); old homes' `hung` lists are dropped, so
  nothing rings until bells are hung again; the Signal's first-bell line isn't said again.

## Hang them high (§43.16 stage 3, plan docs/director/BELLS.md §4.4, §4.8)

### The Belfry and the Bell Tower (Work Plans)
- **Get**: a Work Plan (as before), used to turn its choice to "Belfry" or "Bell Tower"; a Thatchback living with you
  builds it from the chests within eight, one block a turn; or you build it yourself from the plan's bill.
- **For**: Bell Beams (and, in the tower, Great Beams) high up without a scaffold: the Belfry sixteen hooks nine up, the
  Bell Tower 24 hooks and two Great hooks thirteen up; the Hearth's tier II bell tower.
- **Know**: the plan's line when chosen and its status (the bill, then blocks done and what it's waiting on); the Bells
  tab's "Your frame's full" step and the page's next line name the plans; "[The Thatchbacks finished the Belfry. Climb
  the ladder and hang your bells from its beams.]"
- **Source**: `kin.works.WorkPlans` (`belfry`, `bellTower`), `BellHooks.raised`.
- **Links**: Bell Beam and Great Beam recipes; ladders; the Hearth's tier II; the Great Belfry (needs its 7 by 7 clear).
- **Edges**: the plan block goes when the tower's done (even if its last block was already there); beams the Thatchbacks
  set carry at any height, so a plan laid below the fire still works; anything of yours in the way is built round; no
  glass walls a window up.

### The Registry's wagon (the Provings' Great Bells)
- **Get**: winning a rank's Proving puts its Great Bell on the wagon (Book `wagon`); on a later play-day, while you're at
  home, it's left in your yard two to five blocks from the Anchor as a found bell.
- **For**: the Great Bells come home without carrying a giant bell out of the Registry.
- **Know**: the bell's card says the wagon leaves it in your yard by morning (once you have a home); "[The Registry's
  wagon left the Tenor in your yard...]"; the Bells tab's "The Tenor is in your yard".
- **Source**: `Bells.ring` (PROVING), `FoundBells.wagon`, `FoundBellEntity.delivered`, World `yard`.
- **Links**: the Great Beam; the Great Belfry carries Great Bells up into it.
- **Edges**: never the same play-day; waits while you have no home or the yard isn't loaded or has no room; only you can
  lift it; lifted, it's in your hands like any bell; the admin `tower` command gives wagon bells at once.

### The Hearth's bell tower and the old self-raised towers
- **Get**: a bell hanging 8 or more above your fire, or on a beam the Thatchbacks set, or an old tower's beams.
- **For**: tier II's need; a gate near home warned two play-days before it breaks (tolled from your highest bell); calm
  Deep Tide nights. Tier IV asks for thirty bells in your song.
- **Know**: the Hearth page's tier needs ("a bell tower: a bell hanging 8 or more above your fire", "thirty bells in
  your song (n/30)").
- **Source**: `BellHooks.tower`, `BellHooks.song`, `BellHooks.toll`, `RoomEffects.bellTower`, `TierRules`,
  `BellTower.convert` (old towers, World `beams`).
- **Edges**: the Bell tower room still counts but no longer rings vanilla bells; an old belfry's, tower's or Great
  Belfry's vanilla bells become beams once, counting at any height; no tower raises itself any more.

## Lord bells lie in the boss room (§43.16 stage 4)

### A bell a gate's Lord kept (`awaken:found_bell` on the arena's floor)
- **Get**: bring down a gate's Lord that kept a bell for you (a gate world's own bell at its rank; else a swallowed one:
  your first Lord the Wren, then never two in a row, one in three, always by the fourth since); it lies on the floor of
  its room near where it fell; lift it.
- **For**: the bell, to carry home and hang.
- **Know**: the line at the fall ("It's lying where the Lord fell..."); its glow in the dark room; the Bells tab's "Lift
  the X from where the Lord fell" while you're in the gate.
- **Source**: `Bells.lordFell`, `FoundBells.keep`, `FoundBellEntity.gate`, World `kept`.
- **Links**: the Book (filed where it was won); the clapper (a Chime Perch); the Bell Frame and beams.
- **Edges**: one per party, each claimant lifts their own; with no floor near it goes straight to their hands; a gate
  that closes first takes it back to the pool (`FoundBells.closed`, `lost`): the next Lord keeps one for that hunter;
  a party member who already has it isn't a claimant; it can't be broken or pushed.

## Every bell at a real place (§43.16 stage 5)

### A bell set down where it was won (`awaken:found_bell`, no gate)
- **Get**: win it at its place: a Foundry cast (by the Crucible), the Founding Bell (by your fire), a stratum of the
  Descent, a Spire or platform ruin, aboard the Halo, where a Sovereign fell, where a raid breached your wall, a
  stampede's wake, the Deep Tide, the eclipse sky; lift it.
- **For**: the bell, to carry home and hang.
- **Know**: the line at the place ("There's a bell set into the rock here..."); its glow; the Bells tab's "Lift the X",
  pointing at it.
- **Source**: `FoundBells.leave`, `Bells.leave` (KinWorks, BigDays), `Bells.found`, World `kept` (`dim`).
- **Links**: the Book; the Bell Frame and beams.
- **Edges**: one per party, each lifts their own; never twice; never in a block someone stands in; no floor near: into
  their hands; knocked out of its place: into their hands when they're about (`FoundBells.watch`).

### The Registry's bells from Bram
- **Get**: clear your first (and twenty-fifth) gate, then stand within 8 of Bram in Carillon.
- **For**: the Registry Handbell and the Toll Bell.
- **Know**: "[Bram at the Registry has something for you.]"; his words as he hands it over.
- **Source**: `Bells.bram`, `Bells.handed`, `CitizenHooks.where`.
- **Edges**: Bram has no body when nobody's near the Registry, so it waits; old Books that hold them already keep them.

### The villages' five (a quest each)
- **Get**: a named citizen remembers one aloud when you're near them in Carillon (Sun, Oren, Kestrel, Teodor, Mira; one a
  play-day, the bell's rank); go to the empty village they name, read the letter on the lectern by the well, find the
  bell where it says (sunk, under a mossy stone, on the highest ground, by a wolf in the fold); lift it.
- **For**: Fenwick's, the Mill, the Shepherd's, the Hill and the Ford bells, and the teller's thanks.
- **Know**: the rumour; "[X is out to the west. It's on your Tasks.]"; the letter's own words; the muffled hum within 32;
  the Tasks' "Find the letter about X in Y", then "Find X where the letter says".
- **Source**: `VillageBells`, `Villages.emptyNear`, World `villages`, `quests`; vanilla lectern, written book and wolf.
- **Links**: the Bell Book; a bone for the wolf; a shovel for the mossy stone; water breathing for a deep bed.
- **Edges**: one village per bell per world, chosen 250 to 2,400 from the square; laid once someone's within 96 of the
  well; laid again for the next who comes without it; reading the carried book counts; a sunk bell with no deep water
  near is buried instead; the teller says thanks once.

### The dreams' six puzzles
- **Get**: dream; the next dream bell you don't hold is behind its puzzle (the Dreaming Bell waiting, the Daydream that
  moves when you look away, the Lullaby by a sleeper, the Falling Bell at the foot of a fall that ends when you look
  up, the Nightmare's carried by what hunts you, the Lucid Bell once you've noticed a dream sign); strike it lucid
  enough; wake.
- **For**: the Lucidium's bells.
- **Know**: each dream's line; the note every 5 s; the second lift's line; a Dreamt lead in the puzzle's words.
- **Source**: `DreamBells`, `Lucidium.signsNoticed`, `MobHooks.spawnThemed` (the sleeper and the chaser).
- **Edges**: one a dream; not saved; beasts discarded on waking; no hurt from a fall the dream took over.

### The Veil's five kept by spirits
- **Get**: hear a spirit's whole story while one of the five waits for your rank; do what it stayed for where it stands.
- **For**: Ghost Lantern, Whisper, Wake, Mourner's and the Veil Bell.
- **Know**: the spirit's errand in its own words; the Tasks' "Light a lantern where X stands" and the rest, pointing there.
- **Source**: `SpiritBells`, `BellHooks.spirit` (from `veil.Spirits.talk`), World `keepers`.
- **Links**: a lantern, flowers, a candle; first light; the other four for the Veil Bell.
- **Edges**: one spirit, one bell; the errand can be done in body; checked within 16 of the spirit.

## Enchanting: the Inscribing Table (SPEC §45)

### The Inscribing Table (`awaken:inscribing_table`)
- **Get**: crafted: lapis, book, lapis / planks, essence shard, planks / stone, lapis, stone. Any vanilla enchanting table
  you use (old worlds, villages, structures) turns into one.
- **For**: writing the enchantments you've learned onto your own gear, deepening them, scraping them off.
- **Know**: the Tasks page's main path right after your first gate ("Make an Inscribing Table", with its items) once
  you know an enchantment; the page's own hover line ("Write it onto your gear at an Inscribing Table"); the learn message.
- **Source**: `data/awaken/recipe/inscribing_table.json`; `Fold.swap`; loot `blocks/inscribing_table`.
- **Links**: pages (learning), the Forge Song and Armoury (room), the Study (depth past II), the market (pages).
- **Edges**: one writer at a time; the piece never leaves the hand (no dupes, nothing drops if the table breaks); walking
  away, Esc or logging out refunds a choice not yet written; the vanilla table can't be crafted.

### The enchantment page (`awaken:enchantment_page`)
- **Get**: a gate's Lord carries its kind's page (the first clear of each kind, then 1 in 5); the village scribe sells
  three a day; old enchanted books teach theirs when used.
- **For**: learning an enchantment for good (spent when learned; a known one can be given away).
- **Know**: it lands in your pack with a line naming the Lord and the enchantment; the first ever toasts that every kind
  of gate keeps its own; Tasks shows "Read the page you're carrying" and the Armour/Weapons "Find an enchantment" rows;
  the scribe lists the ones you don't know first.
- **Source**: `Sources.declare` in `EnchantModule` (`enchant.Pages.onDeath`, `village.Trade`, `Pages.onUseItem`).
- **Links**: the Inscribing Table, the Journal's ENCHANTMENTS category, the gates.
- **Edges**: a page whose enchantment can't be written any more fades to paper; a known page stays; pages stack to 16.

### The Word Stone (`awaken:word_stone`)
- **Get**: never held: one stands in every Precursor ruin (the vaults' sealed chamber across the terminal from the
  drafting stand, the monolith's chamber, the ocean lab's storage dome, the sky platform's cabin, the spire's crown).
- **For**: learning its enchantment for good by holding your hand to it, once for each player (the stone stays for the
  next). Vaults and the monolith carry Insight, Efficiency, Infinity or Feather Falling; the sky ruins Feather Falling,
  Infinity or Insight; the ocean lab one of the water's six.
- **Know**: its letters lift off the carved face in the enchantment's colour while you haven't learned it, with a faint
  hum; looking at it says "A Word Stone carved with Insight. Hold your hand to it to learn it."; the learn message.
- **Source**: `Sources.declare` in `EnchantModule`; placed by `above.ruin.RuinBuilder` through `EnchantApi.placeStone`.
- **Links**: the Inscribing Table, the Journal's ENCHANTMENTS category, the Precursor ruins.
- **Edges**: unbreakable and drops nothing; a known stone says so and teaches nothing; the carving is the block state, so
  a ruin always shows the same one.

### The burin (`awaken:burin`)
- **Get**: never held: it is the table's engraving tool, drawn in your main hand only while you write at an Inscribing
  Table (SPEC §45.10). Creative and commands can give it; it does nothing on its own.
- **Source**: `Sources.neverHeld` in `EnchantModule`.
- **Links**: the Inscribing Table's writing scene.

---------------------------------------------------------------------------------------------------------------------

## The War Anvil (SPEC §46; plan docs/weapon-forge/PLAN.md, not part of §43)

### War Anvil
- **Get**: crafted from three stone, a copper ingot and a log (granted at level 3 with the Fitting Stand).
- **For**: every weapon and Hunter blade is forged on it (stage 2, SPEC §46.7): by hand at E (knap the edge, bind the
  grip; no Forge), from D in a scene of your own (heat in the Forge, draw out on each style's own rhythm, quench, hilt,
  the draw from the stone). It leaves Plain to Flawless with Temper up to your Forge's limit and its story
  ("Masterwork, twin-struck. Forged by Euan."); Masterwork or better goes into your memory (one strike, always Fine); a
  friend's off-beats make it twin-struck; set in an Armoury room it makes weapons last a quarter longer. It is the
  Hearth's anvil too (`#minecraft:anvil`).
- **Know**: the level road's level 3 step names it and its recipe; the recipe book; the Weapons tab ("Set up a War
  Anvil", "Forge your first weapon" with the styles you know as choices, "Make your C-rank weapon"); the Now line ("You
  can forge Beastbound Daggers at your War Anvil"); use it and the Armoury opens at its door with what each weapon takes,
  Forge it, From memory, Forge the blade, and one plain line for what stands in the way; JEI's War Anvil page.
- **Source**: recipe `war_anvil.json`; block `awaken:war_anvil` (drops itself), `arms.anvil.WarAnvil`,
  `WarAnvilBlockEntity` (schema 1), `AnvilWork` (schema 2), `SmithMemory` (schema 2).
- **Links**: the Hearth's Forge (heat from D, the temper limit); Oren's Mark (`awaken:temper_start`); the tier's Hunter
  blade (carried, it stands in for its parts; else they're added); Blueprints (used, they teach the pattern the anvil
  asks for); the Fitting Stand's smithy (one Forge serves both); the Hearth's Armoury room; ForgeHooks (enchanting).
- **Edges**: an unknown pattern, a tier above your level or no Forge near from D: the line says so and nothing starts;
  someone else's work is theirs (a line says who, and using it then is a helper's strike); walking 12 blocks off,
  logging out or Esc holds the work and coming back counts the stage in again; breaking it mid-work drops what was
  taken in and what stands in the slot; a full pack at the draw puts what was in the hand at your feet; a weapon left in
  the slot waits for anyone to sneak-use and draw it.

### Forged weapons and Hunter blades (the 96 weapons, the six blades)
- **Get**: forged at a War Anvil from `data/awaken/arms/anvil_costs.json` (each its old grid recipe minus the
  Blueprint); never on the crafting grid. Hale's issue gives the first; Lords and Marked finds drop their own kinds.
- **For**: the fighting styles at each tier; a blade is the part every forged weapon of its tier starts from.
- **Know**: the Armoury at the anvil, the Weapons tab, the Now line, JEI; the tooltip tells its story.
- **Source**: `Sources.declare` in `AnvilModule` (every key of the cost table); `arms_anvil_costs` proves each has its
  cost and no crafting recipe.
- **Links**: Blueprints (the pattern), beast cores, the tier's metal from D or binding at E, the Forge, Temper.
- **Edges**: a weapon made before the War Anvil keeps working and gains a Plain story when first held; a found one never
  does; old Blueprints held were learned by the backfill; recipe-book entries for the old grid recipes are gone.
