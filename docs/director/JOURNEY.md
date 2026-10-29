# AWAKEN: the whole journey (director; approved)

29 Oct 2026. Written from Euan's fourth note and revised the same day with his approval: **"Go ahead with the entire
plan."** He changed four things (below): no Echoes at all, stone people on the roads, taming you can find out about, and
beasts that do only the boring work. A later note the same day added a fifth: a few rare walled holdfasts with
people. Nothing here is code. SPEC §43 holds the rules builders follow; this file holds the detail. This pass keeps what he liked in `GOALS.md` (the System holds every goal; found, not told; the Hundred
Bells; the Provings; Big Days) and replaces everything that leaned on people outside the city: settlers, villagers,
the Hearth Board with its people's cards, and leads told at your supper table. Page:
https://claude.ai/artifact/UZ4vpKGoSJkisZRkmoHj3D (same link, marked approved).

## What Euan said when he approved it

- **Stone people.** "Along the path, like the first few paths, there are a few... people sitting down that basically
  became stone." People tried going back out after everyone moved into the city, and the Lull turned them to stone. A few
  on the first paths so you feel it as you go, then "here and there for the world, like, not super often". (Part 1.)
- **No Echoes.** "I'm not a fan of echoes. Let's not have that. I don't want something else fighting for me, because
  then it removes all the fun. The whole lore here is that you and the other players of your multiplayer are the only
  people that can actually" go out there. (Parts 1, 2 and 9.)
- **You must know you can tame them.** Nobody tells you a horse takes apples, but this matters to the story and the
  Hearth, so give a direction without the full answer: "you need to find this type of animal in this region, and then
  you actually have to go and find it." Look at how other games do it. (Part 2, "How you find out".)
- **Beasts do the boring work, not the adventure.** "I still want to have to go out and go and find iron, or go out and
  go and find stuff in caves... I bring it back and then they do like the boring work." Automation that grows over time
  is welcome: "at first you do more of the stuff, and then over time... things get more and more automated", such as
  carrying the ingots from one work to the forge. (Part 3, "The automation ladder".)
- "Go ahead with the entire plan. I think this is really, really good." He'll play it through himself to check it.

## The bar (Euan, 29 Oct, as the build began)

"Make sure this is fully featured. You truly think of every single edge case, you think of every single connection ...
so everything actually links, everything has a recipe, everything has a path, and nothing is ... left for assumption
... nothing is something that would be impossible to discover, and everything has a direction but that isn't clearly
stated ... taking inspiration from all of the best games ... you're never left unsure about how to play the game and
how to keep going." Built at AAA level, with the mobs and the kin on par with Fresh Animations and the bar we keep
setting: "the single best RPG someone could ever want."

How the build holds to it (each is checked, not hoped for):
1. **The Ledger** (`docs/director/LEDGER.md`). Every thing the Journey adds or changes (item, block, beast, work, bell,
   line, screen, rule) has a row: how you get it, what it's for, how you come to know it exists (its direction), its
   recipe or source, what it links to, and its edge cases. A stage's rows are written before its code and finished
   before hand-off; no row may say "later" or "TBD" when a stage is handed over.
2. **Nothing without a way in** (GameTest `journey_ledger`). Every `awaken` item and block has at least one way in: a
   recipe, a loot table, a drop, a trade, a work that makes it, a reward or a placement in the world, each declared in
   code where it isn't data, or it's on a short reviewed list of things that are never meant to be held (technical
   blocks). Every recipe the Journey adds unlocks in the recipe book the first time you hold one of its ingredients.
3. **Direction without telling.** Every open goal has at least one pull in the world you can find without being told:
   something you can see from far off, hear, read or track. What we take from the best:
   - *Breath of the Wild*: you see it, you go there. Bell towers, a holdfast's bell, a camp's smoke, the gates' pillars
     and the Stilled on the roads are landmarks you can spot from high ground.
   - *Elden Ring*: the guidance is in the world, not in a list. A sleeping bell hums and the Map shades its bearing.
   - *Outer Wilds*: rumours that point at places and mark where there's more, never the answer. Kin rumours and the
     Bell Book's leads work this way.
   - *Subnautica*: scanning teaches. Reading a beast with the band, a Stilled's letter or a holdfast's talk files
     what you learned.
   - *Stardew Valley*: the Community Center's bundles, restored by the Junimos. The Hearth page's needs show what's
     missing and fill as you bring things; the beasts then do the work.
   - *Minecraft*: the recipe book opens a recipe as soon as you hold an ingredient.
   - *Monster Hunter*: research comes in steps. Each watch adds a line to a kind's card.
   - *Hollow Knight*: you hear the mapmaker humming before you find him. You hear a bell before you see it.
4. **Never stuck.** The journey test (part 8) at every stop. And in play: when a player has moved no goal forward for
   a whole play-day of play time, the System makes the top need's rumour one step plainer, and after a second day the
   Track bearing narrows. It never says what to do, only where to look.
5. **Edge cases first.** Every stage's SPEC lines say what happens with: shared Hearths and parties, two players at
   the same beast or bell, death and respawn, logging out mid-way, chunk unloads, a server restart, old saves, other
   dimensions, Peaceful, the game rules, a full inventory, a broken work, and a beast that can't reach its nook.
6. **The creature bar.** Every kin and beast reaches the bar of the creature sessions (Fresh Animations level): its
   own model, never a vanilla mob reskinned; at least three idles; head and eyes that look at you; blinking and
   breathing; ears, tails and fluff that follow through; its own walk, run or hop; reactions to the bells, to help, to
   fear, joy and sleep; its own sounds; and its own particles where it shows a want. The Lampwing goes to Euan in game
   first, and no other kind is built until it passes.
7. **Moments made like the opening.** Each stage lists its moments (the first Kin card, the first bell, the first raid)
   with the camera, sound and light designed for each, and each plays right with Lens off and on.
8. **A review before hand-off.** At each stage's end: the Ledger re-read against the code, the stage's diff reviewed
   for bugs, the full GameTest suite on CI.

## What Euan said (fourth note)

- **No people outside the city.** The world changed and only hunters can live out there, so everyone lives in Carillon.
  The last person you see is Ilse: past this point only hunters go, it's too dangerous for us, good luck.
- **No settlers at your Hearth.** Having other people there "feels weird... less private". Vanilla Minecraft feels like
  being an adventurer, and that's the feeling to keep.
- **Your workers are beasts.** Small, super cute, bouncy, really custom beasts and mobs that you tame. Over time they run
  the Hearth: they grow your crops, farm, automate the whole place. You still forge your own gear.
- **A relationship with the world's beasts is a progression of its own.** The wild isn't as dangerous as people said.
  Many beasts are friendly, each has its own goals, and they're part of the world.
- **The bells are tied to the beasts.** Until you have all the bells, the beasts really don't like the sound. Once you
  have all hundred and play it, many beasts become friendlier. Fighting has to stay after the end, or it gets boring.
- **No Board.** "Let's just keep the hearth then to the system, where the system is kind of the link between you and the
  beasts. And it's like the system guides everything."
- **Big moments look like the opening.** The first raid and every big moment gets visuals on par with the glitchy
  error-on-screen just before the awakening, with really good sound, so it feels like the game itself is glitching.
- **The goal of this pass:** polish the whole start-to-end experience. There's always something to do, there's always a
  clear direction, and you want to go into gates and go mining because you're moving toward something. Expand the
  Hearth with the beasts, build mock-ups, and map the final level-up pass for a real full playthrough with no problems
  that's genuinely enjoyable.

---------------------------------------------------------------------------------------------------------------------

## The shape in one page

**The world:** one city full of people, and the wild, which belongs to the beasts. Only the Awakened can live out
there: you, and the other players on your server. The villages and towns stand empty. Ilse keeps the last fire at the
edge, and on the roads past it sit the stone people, who tried to go back. Very rarely, far out, a walled holdfast
still rings its own bell and has people inside.

**Three lines move you, and you can always see all three in the System:**
1. **Rank**, measured at the Registry. Each measuring after the first is a Proving: one great fight, never announced.
2. **Bells**, the hundred pieces of the world's old song. The far goal, told once and always in sight and hearing.
3. **Kinship**, how the world's beasts feel about you, kind by kind. It decides who will live with you and what your
   Hearth can do by itself.

**The Hearth is where the three meet.** Your beasts run it. It turns what you bring home (a Lord's Heart, a sung vein, a
sky core) into what you need next, and every need it has points at a place in the world.

**One guide: the System.** It reads the beasts, puts their sounds into words, and holds every goal. People in the city
talk and react; they never hand you a task. Nothing is daily and nothing names a fight before you meet it.

**Big moments break the screen.** The Deep pushing through looks and sounds like the System failing, as it did the
morning the bell rang wrong. Bells do the opposite: each one you wake mends a little of it.

---------------------------------------------------------------------------------------------------------------------

## 1. One city, and the wild

### The rule
About nine-tenths of the world is wild, broken and empty of people. Nearly everyone lives in Carillon. The only ones who
can go out and live in the wild are the Awakened: you and the other players on your server. Out there you find the
traces of everyone else (an empty house, a letter on a table, a cold camp), the stone people (below), and, very rarely,
a **holdfast**: a walled town far out that kept its bell ringing and still has people inside (below). Vael is Awakened
too; you meet him in the city and in gates, and any of his beats that today happen in the wild move to one of those.

### Why: the Lull
The Deep is everything the world refuses to feel (STORY.md). Where the gates leak it, it settles over the land, and
ordinary people out in it stop feeling things. First they get tired. Then they sit down somewhere. Then they don't get
up: they go still, and over the months they turn to stone. That's **the Lull**. Nobody in the city knows whether the
stone ones are dead. Teodor at the archive thinks they're only stilled, and the city hopes he's right. The Choir's bells keep it off: that's why Carillon is a city of bells, why they ring every
hour, and why everyone lives within their sound.

The Awakened are the one exception. You died inside a gate and kept thinking. Whatever the Lull does, it can't do it to
someone who has already come back. That's what a hunter's licence really means, and Ilse says so.

It happened in living memory. Eleven years ago the bells out in the land went quiet one by one (they fell asleep: part
1), the Lull came down the valleys, and the villages walked to the city. A few walled towns rang their bells by hand
and stayed (the holdfasts, below). People in Carillon still talk about the homes
they left, and that's where much of the city's warmth comes from.

### The empty villages
Every settlement that AWAKEN already places (Between the Gates W0/W3/W7: hamlets, villages, towns, wayhouses, the first
hamlet up the camp road) still generates. Apart from the rare holdfasts, it's just empty. A table set for supper, a cradle, a letter on the
mantelpiece, a village chime silent in its frame, a garden gone to seed, roads grown over. The wild moved in: beasts
nest in the houses, a Totter is sorting a kitchen, Thatchbacks have dammed the mill race.

They become some of the best places in the game, and they cost almost nothing new:
- **Shelter and loot**: a roof on a long trip, a chest someone left, tools.
- **Leads**: letters and notes ("The chime stopped last winter. Mam says we're going to the city.") that file bell leads
  in the Book (part 4).
- **Beasts**: many kinds live in or near them; they're where you meet half the kin.
- **The city's memory**: someone in Carillon came from each one. Wake its chime and that person mentions it the next
  time you pass ("You rang Fenwick's chime? My gran was married under that bell.").

### Holdfasts: the few walled towns with people (Euan's addition)
"It would be unnatural for every single person in the entire world to go to a single city." So a few places held out.
A **holdfast** is a big walled town that never let its bell go quiet. When the Lull came down the valleys, its people
rang their bell by hand, day and night in shifts, and they still do. Inside the bell's sound the Lull can't reach them,
and the walls keep the Deep's things out. Like the city's people, they can't leave: past their bell's sound they'd
sit down like everyone else. So the Awakened are the only ones who ever pass between them and Carillon.
- **Rare and far.** None within 1,500 blocks of the Last Fire, so your first hours out stay lonely. The first one sits
  somewhere 1,500 to 2,500 blocks out; after that about one in every 3,000 by 3,000 blocks. They're the biggest
  settlements AWAKEN already places (the walled towns of Between the Gates, W7), kept full of people; every other
  settlement stands empty.
- **You hear them first.** A holdfast's bell rings every hour, and you hear it from far off, a real bell in the empty
  land. The Map shades the bearing, like a sleeping bell's hum but awake.
- **Inside.** Walls, a gate that opens for you, people going about their day, a small market (the emerald still works),
  a bed, a cook, a bell tower with a ringer on shift. Their people talk and react the way Carillon's do: they're amazed
  to see anyone from outside, ask what the city's like, and remember the land as it was, so they carry rumours (kin,
  bells, a lost village). They never hand you a task: every goal still comes from the System.
- **Their bell counts.** Each holdfast's bell is one of the hundred, already awake. It's filed in the Bell Book the
  first time you stand inside its walls and hear it ring: the reward for finding a holdfast.
- **Settled ground.** Each is a tamer on the wildness field (§41): small fields and pasture inside the bell's reach,
  wild past it.
- **Never your home.** Holdfast people never come to your Hearth, and your Ember can't be set within a holdfast's sound.

### The stone people (Euan's addition)
After the villages walked to the city, some people went back out anyway: for a dog, a ring, a mother's grave, the
harvest they'd left in the field. The Lull caught them on the way. They sat down, and they're still sitting there,
gone to stone, moss on their shoulders. The System calls them **the Stilled**; in the city people say "the ones who
went back".
- **On the first paths, so you feel it as you go.** Three on the north road in the first 400 blocks past the Last
  Fire's ridge. The first, Aldo, sits on the milestone just past the ridge, and Ilse asks you to say hello to him. One
  or two more on the old road to the first empty hamlet, and one on the way to the first sleeping bell near your home.
- **Then rarely, here and there.** Near roads, villages and the homes they were going back to: about one in every
  1,000 by 1,000 blocks within 3,000 blocks of Carillon, and rarer past that, because few got far. Never two within
  150 blocks unless they're a pair sitting together. Never in a gate or a deep cave.
- **What they look like.** Life-size, stone-grey with their clothes' colours faint underneath, each posed by hand from
  a set of about twelve: on a milestone with a pack, kneeling at a grave, two side by side on a cart's tail, asleep
  against a tree with a dog's lead in hand, one reaching for a door that's still shut. Weathered, lichen on the side
  the weather comes from. A look of their own (the visual quality rule): not an armour stand, not a statue block. They
  never move or speak.
- **What they carry.** Most hold something. A letter can file a bell lead in the Book (Found). A key opens a chest in
  the village they were heading for. A keepsake carries a name. The System reads them the way it reads beasts, small
  and in brackets: "[She was going back for something. She got as far as the bridge.]"
- **The city remembers them.** Some have family in Carillon. A keepsake's name files "Somebody in Carillon might know
  this name." Show it to the right person and they react, in plain words. It's never a task and never a reward screen.
- **The beasts are gentle with them.** A Lampwing roosts on one's shoulder at dusk, Dumplins nest in a lap, a Mossback
  sits beside one for a season. That's part of how the wild feels: still, sad and kind.
- **Nothing can hurt them.** They can't be broken, moved or mined; powers and raids leave them as they are.
- **At the Wake** (an addition of mine; Euan can strike it): the Lull lifts and the Stilled stand up. Over the next
  play-day they walk back toward the bells. Aldo is first, and Ilse is the one who sees him come over the ridge.

### The edge: Ilse at the Last Fire
The Wayfarer camp, 450 blocks up the north road, becomes **the Last Fire**: the furthest point where the city's bells
can still just be heard, and the last place anyone lives. Ilse keeps it for new hunters. Bram's brief at the Registry
already sends you there; his line changes to "Ilse keeps the last fire up the north road. Past it, it's only hunters."

Her scene (plain speech, read aloud; Ilse sitting by the fire, the ridge and the wild behind her):
- "You're the one from the square. Sit down, the kettle's nearly on."
- "This is the last fire. Past that ridge the bells don't carry, and that's where the Lull starts."
- "Ordinary people who stay out there get tired. Then they sit down somewhere, and they don't get up again. That's why
  we all live in the city now."
- "Some went back out anyway. For a dog, or a ring, or their mam's grave. You'll pass them on the road. They sat down,
  and they've gone to stone. Leave them be."
- "The first one's on the milestone just past the ridge. That's Aldo. He went back for his dog. Say hello to him for
  me."
- "They say a few walled towns far out kept their bells going and never left. I've never seen one."
- "Not you, though. You died once and came back. Whatever the Lull does, it can't do it to you. That's what the licence
  really means."
- "Here. It's a coal from this fire. Put it down wherever you want to live, and that's your home. Nobody will come and
  bother you out there. Nobody can."
- "And don't believe everything the Association says. It isn't all monsters out there. Most of what lives out there
  just wants to be left alone. Watch before you draw."
- "Go on, then. Good luck. Come back now and then and tell me what it looks like. I haven't seen past that ridge in
  eleven years."

From then on Ilse is at the Last Fire whenever you pass. She asks what you've seen and reacts to it (your bells, the
beasts you've befriended, the villages you've woken), and that's all: she never gives you a task. **She is the one
person who ever comes to your home**, once, after the Wake ending lifts the Lull (part 7): she walks past the ridge for
the first time in eleven years and finds you.

The Ember sets anywhere past the Last Fire's ridge (the bells' edge), so your home is always in the wild and always
yours.

### The city's role
Carillon is the only place with people, and it gets more important, not less: the Registry (ranks, and the Reading
Bell where Provings begin), the market (the emerald is still the coin; the town scribe's enchanted books move here),
the archive (Teodor reads what you bring back), the bell-founders, the infirmary, the Roost, the gate contracts. You
come back to sell, buy, be measured and hear the city talk about you. The city's people react to everything you do out
there, so the world still feels lived in; it's just lived in from the walls inward.

### What happens to what's built (detail in part 9)
Villages, towns, wayhouses: empty, generated as now, except the rare walled holdfasts, which keep their people.
Village troubles, settlers, the Hearth's town NPCs and Wayfarers' found scenes: gone from the wild. Troubles become
beast troubles; found scenes become beast scenes and the stone people's things. Roads stay, grown over. Echoes are
removed from the game. The wildness field (§41) loses every tamer except Carillon, its farmland, roads, the holdfasts
and your Hearth, so the land past the Last Fire is wild, which is what Euan asked for.

---------------------------------------------------------------------------------------------------------------------

## 2. The beasts

### The rules
- **Small, round, bouncy, ours.** Fist-sized to dog-sized, chunky shapes that read as Minecraft, big eyes, a squash and
  a hop when they move, a sound of their own. Never a vanilla animal with a new skin. The bar is the creature-life and
  Fresh Animations bar, and per the visual quality rule each kind has a look nothing else in the game shares. Style
  sheet first; one kind judged by Euan before any other is built (how the bosses started).
- **They have lives.** Every kind has a want (its own goal) and a day: Pebblepups dig in the morning and sun on warm
  rock at noon; Lampwings rise at dusk and follow the river; Thatchbacks build a dam that grows over real days and
  changes the stream. You see them going about it whether or not you're there.
- **The wild isn't what people said.** Most of what lives past the Last Fire is shy, not hostile. Some things that look
  like monsters are the gentlest kin there are (the Boulderback).
- **Three natures, plain to see.**
  - **Kin**: the land's own beasts. They can be befriended, and some will come to live with you. Never hunted.
  - **Wild quarry**: the big herds and their predators (the Wild Lands' herds and packs, its named beasts). Hunted as
    the Wild Lands designed, respected, never tamed.
  - **The Deep's**: everything that came out of a gate, and the **Gnawed**, beasts the Deep got into (dark veins, eyes
    like embers, wrong sounds). Always a fight. Night's monsters are the Deep's too.
- **Nothing fights for you.** Not your beasts, and not anything else: Echoes are gone. ("I don't want something else
  fighting for me, because then it removes all the fun.") The only ones who fight out there are the Awakened: you and
  the other players on your server. Kin warn you and hide. Your own powers' summons and decoys stay: they're your power,
  cast and gone in seconds. (If Euan wants those gone too, that's one line in a later stage.)
- **They do the boring work, never the adventure.** You go out and find everything: ore, gems, gate drops, wild seed
  and every rare thing. Your beasts tend, carry, sort, smelt and keep what you bring home. Nothing at home makes ore,
  gems, gate loot, essence or XP out of nothing. What grows by itself (crops, wool, eggs, honey) they tend, because
  that's chores. Over time more of the carrying happens by itself (part 3, the automation ladder).
- **Nothing you've befriended dies.** Hurt, it hides and comes back. A raid never costs you a beast.

### The kin (first sketch; the style sheet decides looks)
Fifteen kinds that come to live with you, and the Boulderback, a "monster" that isn't. Where they live is the land's
kind, not a biome list: the plan places them on the Awakened Lands' biomes. The "How you help" column is for builders.
The game never shows it: the player gets what "How you find out" (below) gives.

| Kind | What it is | Where it lives | What it wants | How you help | At your Hearth |
|---|---|---|---|---|---|
| **Lampwing** | A moth the size of a cat, round and furry, with a glowing belly | Woods' edges at dusk | To be near a warm light | Hang a lantern where it can see it from the trees | Lights the Hearth at night; nothing spawns where Lampwings roost; comfort |
| **Burrowbun** | A round rabbit-mole with spade paws | Soft meadow ground | The perfect burrow (it tests the soil and gives up on hard ground) | Till a patch of soft soil near it; it tries it, then chases you round it | Tills, plants and harvests the fields |
| **Mossback** | A slow dome with a tiny garden growing on its shell | Wet ground in sun, mere shores | Somewhere wet and sunny to sit for years | Cut back the branch shading its spot, or bring water to its patch | Crops near it grow faster; wild seed you bring takes beside it, so the Wild Lands' plants become crops |
| **Pebblepup** | A stone-skinned pup that eats grit and hoards shiny stones | Rocky slopes, cave mouths | A bigger pile of shiny stones | Leave a shiny stone (quartz, an amethyst shard) on its pile | Sorts the ore and rubble you bring home (the Ore Shed); with you in caves it noses toward lode hearts, and you dig |
| **Totter** | A round beetle with a flat back that carries things and sorts them by colour | Woods' floors, empty villages (it tidies them) | Everything in neat rows | Put back what's scattered where it's working | Hauls and sorts into chests; moves goods between jobs |
| **Thatchback** | A beaver-badger with a broad flat tail it carries planks on | Rivers; it builds dams that grow over days | To build something bigger | Bring the timber it's short of, or mend its dam after a flood | Builds your works from their plans with the stone and metal you bring; fells and replants the woodlot |
| **Emberkit** | A salamander kitten that glows from inside | Hot springs, vents, warm caves | To stay warm | Carry it a warm stone, or light a fire where it's shivering | Smelts ore and keeps the furnaces and your Forge hot (you still forge) |
| **Puffwool** | A cloud of a sheep on tiny legs; bounces when it's happy | High pasture | To be brushed (thorns tangle it) | Brush the burrs out | Wool by brushing; calms the pens |
| **Dumplin** | A round bird that can't fly and follows the tallest thing it sees | Grassland, in flocks | To follow someone | Stand on a rock and lead the lost flock back to its nest | Leaves eggs for you; keeps the yard's bugs down |
| **Bumblepuff** | A fuzzy bee as big as a loaf that floats more than it flies | Flower fields | Flowers, lots | Plant flowers near its hive | Honey, wax for candles (comfort); fields set more seed |
| **Pondling** | A frog-axolotl that fishes | Still, clean water | Clean water (the Deep's rot sickens it) | Clear the rot out of its pond | Fishes; keeps ponds clear |
| **Snufflet** | A piglet with a long snout that finds what's buried | Old woods, near ruins | Whatever smells interesting under the ground | Dig where it's snuffling (it found something it can't reach) | Forages herbs and mushrooms in your woodlot; out with you, it snuffles where something's buried, and you dig (letters and keys among them) |
| **Chimewren** | A round songbird that copies sounds, bells most of all | Everywhere, in flocks | New songs | Let it hear a bell you've woken; it's the one kind that likes the bells | In the Rookery, wrens come back singing notes your song is missing (a direction, never the place); as your companion it chirps toward sleeping bells |
| **Loomworm** | A fat, fuzzy caterpillar that spins | Old woods' canopy | The leaves of one tree | Bring it a branch of that tree | Thread and cloth: beds, banners, the Hearth's comfort |
| **Cloudcalf** | A sky-whale calf as big as a cow that drifts | The sky islands (B) | To find its pod | Guide it back to the pod across the islands | Carries you between islands before Skyborn; waters the fields with its own small rain |
| **Boulderback** | Big, tusked, mossy, eyes that glow in the dark. Looks like a monster. Shy as anything | Deep caves | To be left alone in the dark | Put your light away and wait; let it come to you | Carries stone for the big works; a slow, steady mount underground |

The Gnawed version of any kind (the Deep in it) is a fight, and freeing the land of one is a beast trouble (below).

### Their own goals in the world
- **Days:** each kind has a routine you can learn and time (when Lampwings come out, where Pebblepups sun).
- **Seasons and moves:** Mossbacks walk to the mere in summer; a Dumplin flock crosses the road every few days.
- **Works of their own:** a Thatchback dam floods a meadow over a week; Totters clear an empty village's square; a
  Chimewren flock learns the note of a bell you woke and sings it across the valley afterwards.
- **Fears:** gate light, the Gnawed, a break. A gate left open near a meadow empties the meadow, which is one of the
  ways the world tells you something's wrong (part 6).
- **Beast troubles** replace village troubles (W4's rules unchanged: caused by world state, found never listed, at most
  two near you, missable with a lasting cost, never a dead end, always paid): rot in the Pondlings' pond from a gate
  nearby, a Gnawed alpha harrying a herd, a flock trapped by a cave-in, a dam that floods a burrow field, Emberkits
  frozen out of a vent a gate's cold has choked.

### How you befriend them: Kinship
Taming is understanding, not throwing food. Every kind runs the same five steps, each with its own small play:
1. **Hear of it, then meet and read it.** Before you've met a kind you may have heard of it: a rumour on the Hearth
   page says what sort of animal and roughly where (below). When you see one, look at it with your band up or through
   the Binoculars (§39) for a moment. The System files it on the Kin list: its name, a drawing, where and when you saw
   it, "Could live with you", "Wants: ?".
2. **Watch.** Stay near without scaring it (crouch, keep your weapon away; long grass hides you, §41 decision 10). Each
   time you see it do its thing, the System adds a line, from vague to plain: "It keeps going back to a pile of
   stones." Then "It only adds the shiny ones." Then "It looks like it wants more shiny ones." The third watch always
   says the want plainly.
3. **Help.** Work out what to do from the want, and do it in its own way: a stone on the pile, soft soil, a lantern in
   the trees, the burrs brushed out, the lost flock led home. A small act, never a fetch list. The System says the
   want, never the help.
4. **Trust.** Its tell: a hop, a chirp, a few steps after you. Help the same one three times and it trusts you.
5. **Home.** A trusted beast follows you home if your Hearth has a nook it would like (a wet sunny corner, a warm stone
   by the forge, a lantern post). It moves in, looks around, and starts its work the next morning.

**Kinship with a kind** goes up each time you help one of them and each day one lives with you, and down if you hurt
one (a lot if you kill one: that kind runs from you for days). Four steps:
- **Wary**: it runs from you.
- **Curious**: it lets you watch.
- **Friendly**: wild ones of that kind no longer run, and let you close.
- **Kin**: they come to you, follow you a while, and show you things: Pebblepups dig toward a lode, Chimewrens fly
  toward a sleeping bell, Snufflets snuffle where something's buried. You still do the digging.

### How you find out (Euan: "you actually know you can tame them somehow")
Euan's worry: nobody in vanilla tells you a horse takes apples, and here taming carries the story and the Hearth, so
the player must know it's possible and have a direction, without being handed the answer. How other games do it:

| Game | What it does | What we take |
|---|---|---|
| Valheim | Never says taming exists; players learn it from guides. Once you're on the right track, hearts and a tameness readout show progress ([Digital Trends](https://digitaltrends.com/?p=2628183), [Windows Central](https://www.windowscentral.com/valheim-animal)) | Not knowing is the failure Euan named. Take only the clear feedback once you're on the right track. |
| Red Dead Redemption 2 | Study an animal, from afar with binoculars, and it's written into the compendium ([GamerGuides](https://www.gamerguides.com/red-dead-redemption-2/guide/extras/compendium)) | Looking teaches. The band and our Binoculars read a kind from a distance. |
| Monster Hunter: World | Tracks raise a research level for each monster, and each level adds to its notes ([Fextralife](https://monsterhunterworld.wiki.fextralife.com/Ecological+Research)) | Knowledge comes in steps, kind by kind: each watch adds a line. |
| Palworld | Once you've met a Pal, the Paldeck shows where it lives, by day and by night ([xGamingServer](https://xgamingserver.com/blog/?p=21885)) | Where and when, after you've met it. |
| Monster Hunter Stories 2 | The Monsterpedia marks which monsters can be hatched ([Nightly Gaming Binge](https://nightlygamingbinge.com/monster-hunter-stories-2-monster-guide/)) | Say plainly which kinds can live with you. |
| Outer Wilds | The ship's log holds rumours that point at places and marks where there's more to find, never the answer ([Archania](https://archania.org/p/the-symbolic-world/symbolic-works/video-games/outer-wilds), [Steam](https://steamcommunity.com/app/753640/discussions/0/5185299686169941344)) | A rumour names a kind and a region, never the method. |

So the System always tells you three things and never a fourth:
- **That a kind can live with you.** Every kin card says "Could live with you" and what it would do at home. Wild
  quarry's cards say "Quarry". The Deep's things get no card.
- **Roughly where to look, before you've met it.** Each need on the Hearth page carries a rumour: what sort of animal
  and what sort of place, with a real region. "Nobody's minding the fields. Something small and round that digs lives
  in soft meadow ground, south of the river." The System picks an actual place where that kind lives within about 600
  blocks and names it by what you know (a river, a hamlet you've passed, a direction from home). Track gives a rough
  bearing, never the spot. Kinds you've heard of but not met sit on the Kin list as a dark outline with their rumour.
  Rumours come from the world too: a letter in an empty village ("Our buns dug the best soil in the valley"), a stone
  person's keepsake, someone in Carillon remembering ("We had Dumplins following the cart every summer").
- **What it wants, once you've watched it.** In steps, as above. Each kind's want is something you can see it doing
  in the world, with its own animation: the pile, the soil test, the shivering by a cold vent.
- **Never what to do.** It says the want, not the help: "It wants more shiny stones", never "give it quartz". The
  world makes it readable: the pile is right there, and it pushes a dull stone off.

**Clear when you're right** (Valheim's lesson). The right act gets the beast's tell (a hop, a chirp), a soft chime from
the band and a line: "[It took the stone. It's looking at you differently.]" Close but wrong gets a nudge: "[It
pushed the dull one off.]" Nothing on screen fills up; the Kin card shows the steps.

**Never stuck.** After a few wrong tries the bracketed line points closer ("[It's looking at the amethyst on the cave
wall.]") and still never says the act. At any time the Kin list holds at least one rumour you haven't followed, tied
to the Hearth's next need.

**Taught once, gently.** The Lampwing at your first fire runs the whole loop in a few minutes (part 3).

### The System is the link
The System can read beasts because it is the Choir's voice, and the beasts grew up with the Choir's song (part 4). So
the System puts their sounds into words, and it says more as you grow closer:
- Wary: "[It doesn't trust you.]"
- Curious: "[It's looking at the stone in your hand.]"
- Friendly: "[It wants the shiny one, not the dull one.]"
- Kin: "[It's telling the others you're all right.]" and each kind's own name for you ("[It calls you Big Stone.]").

Those lines show where subtitles do, small and in brackets, only when you're close and only for kinds you've read.

---------------------------------------------------------------------------------------------------------------------

## 3. The Hearth, run by beasts

### From the first night to the last work
- **The first night.** You set Ilse's coal down and the Hearthfire takes. That night a Lampwing comes out of the trees
  to the light and sits on a stone by the fire, watching you. The System reads it for you, the first Kin card, and the
  Hearth page opens: "Something's been watching your fire from the trees." That's the taming lesson, played once, gently.
- **Hours 1 to 4.** Two to four beasts. The first field is tilled and planted by a Burrowbun while you're away, and the
  Mossback in the wet corner makes it grow. A Pebblepup is digging in the hill behind the house.
- **Day by day.** Every beast does its chore without being told: the fields tended, the pens kept, the ore you dropped
  in the Smeltery's chest smelted. What's done waits in that work's chest, and crafting already pulls from the home's
  chests. At first you carry things between works yourself; later the beasts do (the automation ladder, below).
- **The works.** The Hearth grows by works the Thatchbacks build from plans, with timber from the woodlot they keep and
  the stone and metal you bring. You also bring the one to three rare things each work needs, and those rare things are
  what send you into the gates, the deep, the Veil and the sky.
- **By the end** your home is a small working village of beasts: fields, pens, hives, an ore shed, a smeltery, a
  rookery, the Works with machines running off your bells, and the Great Belfry. Every ore in it you mined yourself.

### What's still yours
- **You forge.** The Forge is yours alone: patterns, the Armoury, tempering. Emberkits keep it hot and smelt the ore;
  the hammer is yours.
- **You go out and find it.** Every ore and gem (you mine it; nothing at home does), every gate drop, the wild seed,
  and the rare things: a Lord's Heart, a sung vein, Deepglass, a sky core, a spirit's blessing, the Heart-clapper.
  Nothing at home makes ore, gems, gate loot, essence or XP out of nothing.
- **You hang the bells and choose the plan.** Where things go, what gets built next. Building by hand is still a
  choice: the room reading reads what you build, and a Grand room still pays more.

### Nooks, and how many
Each beast needs a nook it likes. How many can live with you grows with the Hearth: 2 at the Ember, 4 at the Anchor,
then 8, 12, 18, 24 by its last tier, plus the nooks each work brings.

### The works (built by Thatchbacks; first sketch)
| When | Work | The rare thing you bring | Who works it | What it does for you |
|---|---|---|---|---|
| E | **The Anchor** | Your first Lord's Heart (a gate) | | More nooks; the Hearth's radius; the first rooms read |
| E | **Fields and Pens** | Soft soil, seed from the wild | Burrowbuns, Mossbacks, Puffwools, Dumplins | Food, wool, eggs every day |
| E to D | **The Forge** | Iron (mining) and a pattern from a gate | Emberkits keep it hot | Where you forge D-rank gear and up |
| D | **The Ore Shed** (was the Mine head) | A lode heart (mining) | Pebblepups, later Boulderbacks | Sorts and crushes the ore and rubble you bring home: ore out of gravel, a few nuggets from a stack of your rubble. Never makes ore by itself |
| D | **The Smeltery** | A vent stone from a hot spring (caves) | Emberkits | Smelts the ore you drop in its chest |
| D | **The Rookery** | A bell you've woken (any) | Chimewrens | Bell leads by song; the Map fills round you |
| C | **The Foundry** | Bell bronze and Deepglass (the Singing Deep, the Descent) | Emberkits, Thatchbacks | Casts the six bells of the Singing Deep, and the tower's frames |
| C | **The Storehouse** (Euan's addition, 29 Oct) | A Totter that's Kin, and the keystone from an empty town's granary | Totters | One place to drop everything. You tip it into the Drop, you watch it pile up, the Totters sort it onto its shelves, and the Ledger finds anything (below) |
| C | **Hives and the Loom** | A queen's comb from a flower field; a branch of the loom tree | Bumblepuffs, Loomworms | Candles, cloth, comfort; Rested lasts longer |
| B | **The Works** | A sky core and a machine plan (the Above) | Emberkits for heat, Totters for lines | Machines run off your bells; lines move things across the whole home |
| B | **The Shrine** | A spirit's blessing (the Veil) | | Fracture care; dreaming on purpose (the Lucidium) |
| A | **The Ringing Gate** | A Halo shard (the Halo) | | Travel between every place you've belled |
| S | **The Great Belfry** | The Heart-clapper (the deepest room of an S-rank gate) and a Sovereign's relic (the Court) | Every beast you have | Holds all hundred bells; plays the full peal |

### The automation ladder (Euan: "at first you do more of the stuff")
The beasts take the boring work a step at a time. The right-hand column never changes: you go out.

| When | What runs by itself | What you still do |
|---|---|---|
| E, first home | One field tended (a Burrowbun); the yard lit at night (a Lampwing) | Everything else: mine, smelt in your own furnace, carry, craft, forge |
| D | Each work has its beast and its chest. Drop raw ore in the Smeltery's chest and the Emberkits smelt it; drop rubble in the Ore Shed and the Pebblepups sort it; the fields and pens run | Find and mine every ore; carry ingots from the Smeltery to the Forge; fetch the rare things; forge |
| C | Totters carry between works inside the yard: Smeltery to the Forge's chest, fields to the larder, wool to the Loom. The Storehouse: drop anything in one place and the Totters sort it onto its shelves | Find and mine; fetch; forge |
| B | The Works' lines: machines off your bells move things across the whole home (ore from the Ore Shed through the Smeltery into the Forge's rack; every work takes what it needs from the Storehouse and sends what it makes back) | Find and mine; fetch; forge; choose the plan |
| S | Every work linked; the Great Belfry built by every beast you have | Find and mine; fetch; forge; hang the bells |

### The Storehouse (Euan, 29 Oct: "dump anything in there ... and your beasts actually come and sort it all")
At C, with the Totters, the Thatchbacks can build **the Storehouse**: a long timber hall with a skylight over a wide
sunken bin in the middle, **the Drop**, and its walls lined with shelves, racks, crates and barrels. It's the answer to
coming home from a long trip with a full bag.
- **You dump it all in one place.** Tip anything into the Drop: by hand, a stack at a time, or "Empty my bag" (keeps
  your hotbar, armour and anything you've marked). What you drop falls in as real things and piles up: ingots clink onto
  the heap, logs roll to the edge, gems skitter, a sword lands across the top. The heap rises with how much is in it.
  Each kind of thing lands with its own sound.
- **Then the Totters sort it.** They come down the ramps from their nooks, each takes an armful (you see what it's
  carrying), waddles it to where it belongs and puts it away: blocks stacked in crates, ingots on racks, tools on pegs,
  food in baskets, seeds in drawers, gate loot in its own locked press. The shelves fill where you can see them. They
  paint a sign over each bay the first time something goes there. The more Totters live with you, the faster the heap
  goes down; one Totter clears a full bag in about a minute, four in a few seconds.
- **The bays.** Stone and earth, wood, ore and metal, gems, food, plants and seed, beast and monster drops, gate
  loot, gear, bells and leads, odd things. A bay that fills grows a second rack; when the whole hall is full the
  Totters stack the overflow under an awning outside, and the Hearth page says the Storehouse needs another bay
  (timber and iron you bring).
- **You can find anything.** At the Ledger desk by the door you look anything up (type or browse by bay). Pick it and
  the Totter on duty fetches it to the desk while you watch; or "Show me" lights a lantern over its shelf. Crafting at
  home already takes from the home's chests, and it takes from the Storehouse too.
- **It only holds what you bring.** Nothing in it grows or multiplies. At B the Works' lines connect it to every work
  (the Smeltery takes its ore from the Storehouse and the Forge's rack its ingots); at S everything is linked.
- **Made to feel good.** The heap and the shelves are the show: real 3D items on the heap and on the shelves (capped
  and drawn more simply far away, so it costs little), a short settle as things land, the Totters' own carrying walk
  and their pleased chirp when a bay fills, soft light through the skylight onto the heap, warm lanterns over the bays.
  A shared Hearth works the same for every member.
- Built in stage 5, with the Totters and the works up to the Foundry.

### The Hearth page (the System)
Euan dropped the Board: "keep the hearth then to the system". The Hearth becomes one page of the band, **Hearth**,
between Journal and Truths (the rail fits seven), opened by Tab or K like any page. Three parts:
- **Home.** What your Hearth can't do yet, in the System's words, each with a rumour of where to look: "Nobody's
  minding the fields. Something small and round that digs lives in soft meadow ground, south of the river." "The Ore
  Shed needs a lode heart. Hearts beat in lodes below y 0; a Pebblepup can hear them." The next work, with its rare thing and its guidance. The
  Great Belfry, pinned and dim from the first day: "Holds all hundred bells", its two needs unknown.
- **Your beasts.** Who lives with you, what each is doing right now, how it's feeling (its nook, its kind's company, the
  song), and what it made today.
- **Kin.** Every kind you've met: where and when, "Could live with you" and what it would do at home, its want so far
  (or "?"), your standing (Wary to Kin) and the System's latest line for its sounds. Kinds you've only heard of show as
  a dark outline with their rumour.

**Track** on any need, lead or kind gives a rough bearing on the top-right card, never the spot (pass three's rule).
Nothing on the page expires or renews each morning. There's always something on it, because there's always a nook
empty, a kind not met, a work next.

---------------------------------------------------------------------------------------------------------------------

## 4. The bells and the beasts

### The song
The hundred bells are the Choir's song in the Waking world (GOALS.md part 1). The beasts grew up with it: when all
hundred rang across the land, the kin sang along. Eleven years ago the bells fell asleep one by one, and the song broke.
A song with holes in it sounds wrong to the beasts, the way the city's bell rang wrong the morning it chose you. So they
hate it, and the Chimewrens are the exception because they're trying to fill the holes.

### What you see and hear
Your tower rings your song at dawn and dusk, gaps and all. **Your own beasts' reaction is your progress, felt, not
read:**
| Bells | Your beasts when the tower rings |
|---|---|
| Under 20 | They bolt for their nooks, paws over ears, and stay in till it stops. Wild beasts nearby scatter. |
| 20 to 49 | They hunker down where they are, ears flat, and wait it out. |
| 50 to 79 | They stop and listen, uneasy. A few hum along. |
| 80 to 99 | They gather under the tower and wait through the gaps, as if they know what's missing. |
| 100 | They sing. |

**In the wild,** ringing a bell (a peal from your handbell) scares nearby kin away for a minute, and you can't help or
read a beast while they're running. Peals are still worth it in a fight; they just cost you the beasts' patience.

### The Hundred Peal
Hang the hundredth bell and ring. It's the one big moment in the game that doesn't glitch: the screen goes perfectly
clear, the System's glass turns warm, and the full song rolls out over the land for the first time in eleven years.
Every beast in hearing comes. Across the whole land, many kinds go up a step of Kinship at once, and the ones that had
been waiting for the song go straight to Friendly. The legendary beasts come to your tower. Your own beasts sing.

After it you have **the Kinsong**: play it in the wild and the land's beasts calm. A stampede stops, a frightened herd
settles, a raging alpha lies down.

### The fighting doesn't stop: the Deep answers
The Deep heard the song too. From the Hundred Peal on:
- Gates break more often and harder, and the Gnawed spread where the Kinsong doesn't reach.
- The Deep's things hate the Kinsong and come for whoever plays it.
- Any Sovereign you haven't beaten wakes angrier, and the ones you have beaten can be fought again as their Grieving
  forms (harder, with new rewards).
- The Endless Descent and Ascension (the Become loop) go on as built.

So the end of the bells makes the land kinder and the Deep angrier: the wild becomes yours, and the fighting moves to
where it always lived, the gates.

### How the Book finds bells now (no supper-table leads)
The Bell Book (the Codex's first section, GOALS.md part 1) keeps Heard and Seen and loses Told-by-your-people. Leads
now come from:
- **Heard**: a sleeping bell hums under the world's sound within about 64 blocks; two bearings cross into a shaded area
  on the Map.
- **Seen**: a place that's plainly asleep (a frozen mere in summer, grey reeds, birds that won't land).
- **Sung**: a Chimewren sings a note your song is missing (the Rookery, or your companion).
- **Found**: a letter in an empty village, what a stone person carries, what you dig up where a Snufflet snuffles.
- **Rung**: a holdfast's bell, awake already, filed the first time you hear it from inside its walls.
- **Remembered**: people in Carillon and the holdfasts talking about the homes they left ("Fenwick had a bell, by the
  lake.").

### The Thaw Bell, again (#31, B-rank)
Found in the journal on a stone woman's lap by a cold fire ("bell under the ice, the herd won't cross"); heard twice from the north
road; seen (the mere frozen in summer, a Mossback colony stuck on the far shore). A Rime Wyrm sleeps coiled round the
bell. It isn't the Deep's: it's a keeper that has slept with the bell for eleven years and wakes furious. You can fight
it, or you can calm it (crack the ice it's frozen to, then wait it out at a distance, the Boulderback's lesson). You
strike the bell, the ice goes black, the reeds go green, the herd walks down to drink, and the Mossbacks cross home. At
dusk your tower's gap in that place is gone.

---------------------------------------------------------------------------------------------------------------------

## 5. Faults: when the big moments break the screen

### What's there to build on
The opening already has the look: CARILLON typed out with a two-frame "[ERROR]" in red and cyan, offset like a bad
signal (`OpeningHud.card`); "[ERROR] … candidate found."; the Ritual's black with the System's words typed large; the
tear from black and crushed blacks (Lens texel 13, `LensClient.tear` / `ritual`, looks `awaken:ritual` and
`awaken:void`); System cards that glitch out in three bands (`HudLook`); and the System's own sounds (glass, drain,
heartbeat, tear, void) in `RitualAudio`.

### The idea
The System is the Choir's voice. When the Deep pushes hard, the voice breaks up, and since the System is how you see
the game (the band, the HUD), the game itself seems to break. It's the same failure you saw the morning the bell rang
wrong, so the player learns it once and dreads it after. **Bells are the opposite:** each bell you wake ends on a clean
sweep across the screen and a clear chord, the System mending a little.

### The kit: a Fault
A reusable director every big moment calls with its own words, timing and sounds:
1. **The tell (0.5 to 1 s).** The world's sound drops out, as if the game took a breath; the HUD flickers once; a bell
   note pitched wrong (the opening's motif).
2. **The break (1 to 4 s).** The frame tears into horizontal bands that slide sideways; red and cyan split apart; the
   System's cards glitch out in their three bands; text corrupts mid-word; a hard cut to black.
3. **The words.** In the black, the System types large and broken, as in the Ritual: "[ERROR] … BREACH · 412 BLOCKS
   EAST · COUNT ▮▮▮". Sometimes one line leaks that it shouldn't know, which only makes sense at the Seed ("[ERROR] not
   this one again").
4. **The cut.** Two or three seconds of held camera on what's happening (the gate bursting on the horizon, the hall
   changing), then back to you.
5. **The aftermath.** The screen comes back, but the band's edge keeps a faint flicker until the event is over, and the
   System's words are plain again: "A gate broke to the east. They're coming here."
6. **The mend.** When the event ends, a clean sweep and a clear chord.

**Three sizes.**
- **A flicker** (under a second): a rare find, a Truth revealed, a Gnawed beast in sight for the first time.
- **A Fault** (3 to 5 s): the start of every Big Day that threatens you, and of each Proving after the first.
- **A Great Fault** (10 to 20 s, hand-made each time, never reused): the first Proving, the first raid, the first
  Sovereign, the Halo opening, the Seed's door, the Deep's answer to the Hundred Peal. About eight in the whole game.

**Sound.** Its own suite, made the way the Ritual's was (recorded CC0 sources, processed offline, kept in
`tools/art/sources/`): the sound sucked out, digital tearing laced with a reversed bell, the System's voice as broken
glass tones, a sub drop on the cut, and the clean chord of the mend. Mixed with the Ritual's rules (voice cap, heavy
transients capped, stereo and listener-relative for the System's own sounds).

**Safe to look at.** No more than three flashes a second, ever; the tearing moves, it doesn't strobe. A client setting
**Faults: full / gentle / still** (gentle keeps the sound and the words and drops the tearing; still is a plain card).
`cameraShake` and reduced motion are honoured. Lens off still plays it (the scene look runs for its length, as
`lensInScenes` does).

**With friends.** A Fault plays for every player in the event's reach; a server-wide event plays for all.

### The first raid, frame by frame
Hours 6 to 8, the first Big Day. A D-rank gate opened two days' walk east of your home. You saw its light on the
horizon and the System put it on the Map. If you cleared it, the first raid comes from the next one; the first raid
always comes, it just never says when.
- **The day before.** Wild beasts stream west past your home. Your own beasts are restless: they carry food into their
  nooks, the Thatchbacks shore up your gate, the Pebblepups pile stones on the wall-walk, the Lampwings don't come out
  at dusk. None of them will fight. The System notes it (a warning, never an order): "Your beasts are
  frightened of something to the east."
- **Dusk.** The tower starts your song, and stops mid-note.
- **The Great Fault.** The tell, the tear, the black: "[ERROR] … BREACH · COUNT ▮▮▮". The held camera looks east: the
  gate's light bursting, shapes pouring out across the hills toward your light.
- **Back to you.** "A gate broke to the east. They're coming here." Your beasts run for their nooks. The band's edge
  flickers.
- **The night.** Waves up the land to your walls, and it's you on the wall (and any friends on your server). The
  Lampwings' roosts keep the yard lit, so nothing rises inside it. The raid's Lord comes last. Tuned for one player with
  no help: a level-12 player in D gear who stands and fights can hold it.
- **Dawn, the mend.** A clean sweep. One by one the beasts come out and check on you. The Pebblepup brings you a shiny
  stone. Rewards: the Lord's Heart, a Big Day bell, and the beasts' Kinship (they saw you hold).
- **If you weren't home**, the raid still comes. It goes for your walls, not your beasts, who hide in their nooks. It
  breaks what it can reach and takes what it can carry (a chest's worth from the larder, never your Forge's racks), and
  you come back to damage the Thatchbacks are already mending. Never a lost beast, never a loss you can't put right.

---------------------------------------------------------------------------------------------------------------------

## 6. Big Days, in a world of beasts

Kept from pass two and three: rare (about one every five play-days at most), never in a world's first two hours, never
two in a row, warned by the world's own signs and filed by the System, and you're always free to be elsewhere.
- **Raids** (from your first Great Bell): a gate breaks near home. Each opens on a Fault; the first on a Great Fault.
- **The Migration**: a great herd crosses your land, and a broken gate's ground lies across its path. Lead them round
  it, or clear it. No Fault: it starts with the herd's horns at dawn.
- **The Long Dark**: a night when the Lull thickens and the light goes thin. The Lampwings matter.
- **The Falling Star**: starmetal comes down somewhere out there (S gear needs it).
- **The Bloom**: a rare flowering; Bumblepuffs swarm to it, and so do things that eat Bumblepuffs.
- **A Wandering Lord**: a gate's Lord walks out into the wild.
- **A Sovereign Stirring** (from S): a domain reaches the surface for a day.

---------------------------------------------------------------------------------------------------------------------

## 7. The whole journey, hour by hour

Ranks follow the curve that's built (D at level 10, about 4 h; C 25, 17 h; B 45, 45 h; A 70, 70 h; S 100, 88 h;
Sovereign 135, about 100 h). Each stop says what's in front of you, what you could do right then (always at least four
things, across all three lines), why you'd go into gates and mines then, which beasts and bells arrive, and the big
moment. The Provings are GOALS.md part 2, unchanged except where noted.

### 0:00 to 0:45 · The city (built)
The opening: the bell rings wrong, the run, the Ritual, the Void, the infirmary, the Registry. E-rank, the band, the
first Directive. **Big moment:** the awakening, the original Fault. Bram sends you to Ilse.

### 0:45 to 1:30 · The Last Fire
The north road out of the city, the bells fading behind you, Ilse's fire. Her scene, the coal. Past the ridge, Aldo on
his milestone, the first of the stone people, and two more on the road. The first night: the
Hearthfire, the first Lampwing, the first Kin card. **In front of you:** a home of your own. **The System:** the Hearth
page opens with one need ("Your fire's alone.") and one kind.

### 1:30 to 4:00 · First home (E)
- **Things to do:** find a Burrowbun and a Mossback for the field; clear your first E gate for a Lord's Heart (the
  Anchor); follow the hum of the first sleeping bell near home; walk to the empty hamlet up the old road (two of the stone people sit on the way).
- **Gates, because:** the Anchor needs a Lord's Heart; hide and bone for E gear; gate floors give the most XP.
- **Mining, because:** the Forge needs iron, and D gear needs iron with no alternative (approved).
- **Bells:** the first one, found by ear near home, kept by a Chimewren flock sitting on it. When it rings, the Signal
  says it once: "There were a hundred. Find them. Ring them all and I can show you the bottom." By hour 4, about eight.
- **Beasts:** a Lampwing, a Burrowbun, a Mossback, a Pebblepup; seven kinds met, two Friendly.
- **At hour 4 (where Euan stopped), the System holds:** the Bell Book (8 of 100, three leads), the Hearth page (the
  Ore Shed wants a lode heart; the wet corner is empty; a Thatchback was seen on the river), the Registry ready to
  measure you, a door you saw in Saved for later. Pick any one; the top-right card gives the bearing.

### About 4:00 · The Ringer (the first Proving)
Hand on the Reading Bell; it doesn't stop; the flooded belfry (GOALS.md part 2). **A Great Fault**, the first since the
awakening. Win: the Tether, D-rank, the Great Bell Tenor. **Changed:** at dawn the Tenor is standing in your yard and
your beasts are round it, the Thatchbacks trying to push it upright. That dusk it rings in your song, and the beasts
still bolt for their nooks.

### 4 to 17 h · D: the land opens
- **Things to do:** befriend the Thatchback (works start getting built), find a vent stone for the Smeltery, find a
  lode heart for the Ore Shed, follow bell leads into the Descent's Hanging Gardens and Drowned Choir (strata 2 and 3),
  the first gate-world bells, your first lucid dream (level 12), spirit form (level 15), and maybe the first holdfast,
  if you follow an awake bell's sound far enough (1,500 to 2,500 blocks out).
- **Gates, because:** each gate world has its bell at the Lord; Lord's Hearts for the Anchor's next tiers; patterns for
  your Forge; a gate left near a meadow empties it (protect your kin).
- **Mining, because:** diamond for C gear, the Ore Shed's lode heart, the first sung vein, shiny stones the Pebblepups
  want, and every ingot your Forge uses, because nothing at home mines for you.
- **Beasts:** Thatchback, Emberkit, Totter, Chimewren, Puffwool, Dumplin. Fields and Pens, the Forge, the Ore Shed,
  the Smeltery, the Rookery. By 17 h the farm runs itself; you still carry the ingots to the Forge.
- **Bells:** about thirty by C. Now the beasts only hunker down.
- **Big moment:** the first raid (6 to 8 h), a Great Fault.

### About 17 h · The Examiner (C)
The room you're in becomes the Hall of Reflections; it fights the way you do. A Fault (not Great). Win: your Path.

### 17 to 45 h · C: going deep
- **Things to do:** the Foundry (cast the Singing Deep's bells yourself), the Bone Orchard and the Silent Halls
  (strata 4 and 5), a companion beast that comes with you (a Pebblepup for lodes, a Chimewren for bells), Hives and
  the Loom, three kinds to Kin.
- **Gates, because:** gate worlds at C modifiers carry their bells; the second raid is bigger.
- **Mining, because:** amethyst and Deepglass for B gear; bell bronze for the Foundry; Boulderbacks in the deep caves.
- **Beasts:** Bumblepuff, Loomworm, Pondling, Snufflet, the Boulderback. Kin kinds now come to you in the wild. The
  Totters start carrying between works, so the ingots reach the Forge without you.
- **Bells:** about fifty-five by B. The beasts stop and listen when the tower rings.
- **Big moments:** the Migration; the second raid.

### About 45 h · The Unmade (B)
A colossus you climb on the Broken Span. A Fault. Win: B-rank, the Great Bell Third, and the Unmade's heart, which your
Forge makes into its boss weapon (the arms plan's boss weapons). Nothing rises to fight for you.

### 45 to 70 h · B: up and in
- **Things to do:** the Above (the Scanner, the sky islands, a sky core for the Works); the Cloudcalf's pod; the Veil
  in earnest (spirit bells, heard only in spirit form); the Sleepers' city in the Lucidium (dream bells); the
  Precursor Foundry and the Inverted Sea (strata 6 and 7).
- **Gates, because:** B gate worlds' bells; Lord's Hearts for the Anchor's top tiers; boss patterns for your Forge.
- **Mining, because:** netherite for A gear; machine parts' metals for the Works.
- **Beasts:** Cloudcalf. The Works start running: Emberkits for heat, Totters on the lines.
- **Bells:** about seventy-five by A.

### About 70 h · The Sky Warden (A)
A cathedral breaking apart above the clouds, fought on the wing. A Fault. Win: Skyborn.

### 70 to 88 h · A: the far places
- **Things to do:** the Halo (a Great Fault when it opens), the Dreaming Stratum (8), S gate worlds glimpsed, the
  Ringing Gate (travel between belled places), the Great Belfry's two needs now named on the Hearth page.
- **Gates, because:** the Heart-clapper is in the deepest room of an S-rank gate.
- **Mining, because:** starmetal (the Falling Star) for S gear.
- **Bells:** about ninety by S. Your beasts gather under the tower and wait through the gaps.

### About 88 h · The Keeper's Shadow (S)
A Sovereign's echo at the Court's threshold. A Fault. Win: the Court's doors.

### 88 to 100 h · S: the Court
- **Things to do:** the eight domains in the order you choose, a bell in each (the first Sovereign is a Great Fault);
  the Heart-clapper; a Sovereign's relic; the Great Belfry, built by every beast you have.
- **Bells:** ninety-nine.

### About 100 h · The Hundred Peal
Whenever you hang the hundredth bell (usually here). The clean moment; the beasts answer; Kinship rises across the
land; the Kinsong. Then **a Great Fault, red**: the Deep answers.

### 100 h on · The Last Loop and the Seed
Sovereign rank (level 135): the Last Loop, you as the Signal was (a Fault). The Seed's door (a Great Fault). At the
Seed: Become, Break, or Wake. Wake needs the Hundred Peal.

**After the Wake**, the Lull lifts. Nobody moves out; the city stays the city. But a few days later Ilse walks past the
ridge for the first time in eleven years, follows the sound of your bells, and turns up at your gate. She looks round,
sits by your fire, and says what she said she would: tell me what it's like. It's the only time anyone comes.

### After
The Deep's answer keeps the fighting: harder breaks, the Gnawed, Grieving Sovereigns, the Endless Descent, Ascension.
The beasts have young; your Hearth keeps growing; legendary beasts with wills of their own wait to be won over.
Recasting stays cosmetic.

---------------------------------------------------------------------------------------------------------------------

## 8. How we know the whole playthrough works

- **The guidance test** (pass three) grows into **the journey test**: a GameTest puts a player in each stop's state and
  checks the System holds at least four open goals with guidance across at least two of the three lines (a need, a
  lead, a kind, a door), that each has a place in the world it points at, and that something in the world pulls within
  reach of 40 random spots (a gate, an empty village, a sleeping bell, a kind not yet met, within 200 blocks).
- **Taming can be found:** at every stop, each kind the Hearth page has a rumour for lives where the rumour says (a real
  spawn spot in the named region, within 600 blocks), and every kin card says "Could live with you".
- **Nothing fights for you:** nothing a player owns fights for them except their own powers' short-lived summons and
  decoys; the bind key and Echo records are gone, and an old save with bound Echoes loads clean.
- **Nothing at home makes ore:** a Hearth left alone for ten play-days gains no ore, gems, gate loot, essence or XP
  that wasn't put into it (crops, wool, eggs and honey only).
- **The stone people and holdfasts:** the first three Stilled are on the north road where Ilse says, a Stilled can't be
  broken, none sits within 1,500 blocks of the Last Fire's ridge as a holdfast, and a holdfast's bell files in the
  Book.
- **Nothing ever stalls:** no bell, work or Proving needs something the player can't reach at that rank; every Fault
  hands back control; a raid never leaves the Hearth unable to work.
- **Nothing without a way in:** `journey_ledger` (the bar, above): every item and block has a source, and every Journey
  recipe unlocks when you first hold an ingredient.
- **Never stuck in play:** a player who moves no goal for a play-day gets a plainer rumour, then a narrower bearing.
- **A journey tour on CI** (tools/tour-ci.sh), when Euan asks for one: one shot of the Hearth page, the Bell Book and
  the home at each stop. (Builders themselves run no tours.)
- **Euan plays it through himself** once stages 1 to 3 are in, starting with the first eight hours, because that's
  where he stopped.

---------------------------------------------------------------------------------------------------------------------

## 9. What this changes in what's built or approved

| Today | After this pass |
|---|---|
| Villages, towns, hamlets, wayhouses with citizens (§28 W0, W3, W7) | Still generated, empty and overgrown; letters, chimes, loot; beasts live in them. The rare walled holdfasts (the biggest W7 towns, far out) keep their people |
| Village troubles (W4) | Beast troubles, same rules |
| Vanilla villagers already leave new worlds | Unchanged |
| Settlers move into your Hearth (W1, W6), Oren builds the cottage | Gone. Thatchbacks build the works; nooks take settlers' rooms |
| The Mine head worked by a miner or a Mine Echo (W6) | The Ore Shed: Pebblepups sort what you bring; no ore by itself |
| The Hearth's town NPCs by rank (§10) | Stay in Carillon in their roles |
| **Echoes** (§10: binding fallen elites and bosses with B, the Echo cap from SPI, role pads and the Barracks room, Guard, Haul, Farm, Mine and Train, Echo Duty, the Mine Echo, Echo kills' credit) | **Removed.** Old saves: bound Echoes are let go on load with one System line ("Your Echoes have gone back to the Deep."); role pads stay as plain blocks that drop their materials. SPI's Echo cap becomes +1% Kinship gained per point (to +30%) |
| Things named Echo that aren't allies | Kept: the Echoing tag (creatures that get up once), the Grave Echo (the Descent's death satchel), gates' ghosts, the pick's Echo rung, Echo Palm and Heavy Echo. Echo Swap trades places with your own summons and decoys only |
| The Wayfarer camp, Ilse and Hettie | The Last Fire, Ilse alone; Hettie moves into the city |
| Wayfarers' found scenes outdoors | Beast scenes and the stone people's things |
| Emeralds, the town scribe, trading | Carillon, and the holdfasts' small markets |
| Wildness tamers (§41) | Carillon, its farmland, roads, the holdfasts, your Hearth |
| Hunts (§41) | Wild quarry and the Gnawed only; kin are never quarry |
| Anchor requests, the Daily Regimen | Gone (pass three); the Hearth page's needs replace requests |
| GOALS.md's Hearth Board (pass three), the Echo Hall and Generals | Replaced by the Hearth page; the Echo Hall and Generals are dropped |
| Leads told by your people | Heard, Seen, Sung, Found, Rung, Remembered |
| Vael's beats | In the city and in gates only |

---------------------------------------------------------------------------------------------------------------------

## 10. Order

Euan approved the whole plan on 29 Oct ("Go ahead with the entire plan"), and when three builders were started he
said "Stop them and do all the work yourself". So the director session builds every stage itself, in this order,
straight from this file and SPEC §43 with no new approval page, writing each stage's numbers into §43 as it goes. One
look check stays, as part 2 says: the beasts' style sheet and the Lampwing go to Euan in game before any other kind is
built. No tours: build, test, push for CI, and hand each stage to the orchestrator to merge.
1. **The world rule.** The Lull; the Last Fire and Ilse's scene; the stone people (the first three on the north road,
   then the rare ones); empty settlements and the rare holdfasts; the city taking the people; Echoes removed; the Mine
   head becoming the Ore Shed's frame (its Pebblepups come in stage 2); the wildness changes.
2. **The first beasts.** The style sheet, then one kind judged by Euan (the Lampwing, the first one you meet), then
   the Burrowbun, Mossback and Pebblepup; Kinship, rumours and the watching steps; nooks; the Hearth page; Fields and
   the Ore Shed; the automation ladder's first two rungs.
3. **Faults.** The kit and its sound, then the Ringer's Great Fault and the first raid (tuned for one player alone).
4. **The bells' first thirty** and the tower's song with the beasts' reaction.
5. **The rest of the kin** and the works up to the Foundry, with the Totters' carrying and the Storehouse (Euan's
   addition, part 3).
6. **The second half's bells and beasts** layer by layer (the Above and the Cloudcalf, the Veil, the Lucidium, the Court).
7. **The Hundred Peal, the Kinsong and the Deep's answer**, the Stilled waking at the Wake, and the journey test across
   every stop.

## 11. Decisions (answered 29 Oct 2026)

Euan answered in chat rather than on the page: yes to all ten, with these changes.
1. `j1-world`: yes, plus the stone people, and (his later note the same day) a few rare walled holdfasts with people
   inside; about nine-tenths of the world is wild and empty.
2. `j2-edge`: yes. Ilse is the last person on the road out and the only one who ever comes to your home.
3. `j3-beasts`: changed. Beasts are your Hearth's workers and do only the boring work; Echoes are removed; nothing
   fights for you.
4. `j4-tame`: changed. Taming by want stays, and the System tells you a kind can live with you, gives a rumour of kind
   and region, and says the want after you've watched; never the method.
5. `j5-system` to `j9-journey`: yes.
6. `j10-start`: yes, stages 1 to 3 first.
