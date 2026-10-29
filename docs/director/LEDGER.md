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
