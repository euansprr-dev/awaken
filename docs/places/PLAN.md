# The Long World: a plan for monuments without end (10 Nov 2026)

For Euan's approval. Nothing in it is built except what §2 lists. Process: `.claude/PLANNING-PROCESS.md`.
The page with the pictures: see STATUS.md for the link. The nine new paintings are in `img/`.

## 1. What you asked for, and how we will know

Your words, 10 Nov, on the first three monuments: "This is a massive improvement... This is awesome." Then: "I want to have
so many... maybe we can procedurally make this... maybe making like 100 by hand would be way too difficult and way too long.
But maybe that's what the quality bar would be... the more far you go, you're always going to find new things... it's always
worth going on an adventure... vanilla Minecraft and every other mod pack: you just come across the same boring structures
over and over with the same loot inside and the same setup... you always can discover new things... find interesting loot
and upgrade your levels and find things that truly immerse you into the world and the lore."

- **The job:** make setting out in any direction worth it, for the whole length of the game.
- **The pain:** in every other pack the fifth village is the first village. Same shape, same chest, nothing to learn.
- **What must be true when this is done:**
  1. However far you have gone, the next band of land holds monuments you have not seen.
  2. No two sites look the same, even two of the same monument.
  3. No monument pays "the same loot": each first visit gives something you could get nowhere else, and it makes you
     stronger.
  4. Each one tells you a little more of who lived here, and the pieces add up.
  5. Every monument is at the bar of the three you just saw. None is filler.
  6. It still is not a fairground: quiet land stays quiet.
- **Strong result:** you crest a hill at hour forty and say "what is THAT", and it is a kind of thing you have not met.
- **Weak result:** sixty monuments that are the same three with the numbers changed.
- **Unacceptable:** anything at the level of the thirty places, or of the 144 finds; generic ruins; repeated chests.
- **Not in this plan:** fights and guardians (your call, §7), dungeons under the monuments, automation, new dimensions.

## 2. What exists today (inspected, on branch `claude/finds-over-every-hill`)

| Thing | What it does | Evidence | Keep / change |
|---|---|---|---|
| Three monuments (Stair, Dish, Hall) | The bar | 23,253, 14,784 and 14,546 built blocks; 71, 74 and 63% their own blocks (the thirty places: under 2%); all six tests pass; game pictures on the page | Keep. Each becomes the first of its family |
| Olden stone, 30 blocks | The look | `tools/art/specs/finds_olden.py`, `finds/kit/Olden` | Keep. Two more stones later (§4.2) |
| Monument kit `M` | Coursed masonry, wall relief, piers, columns, giants, one ageing pass | `finds/kit/M`, 313 lines | Keep. This part is already procedural, and it is why three monuments took under an hour |
| The Dish as a generator | One function, six numbers | Two other dishes drawn from it for this plan, no new code (picture on the page) | Extend: every family becomes a generator |
| Relics, carvings, world-answers | A relic with a real use; the System reads carvings; a door that opens when a step is done | `finds/Relics`, `verb/Lore`, `verb/Then` | Extend (§4.4) |
| Levels | A new monument pays about a third of a level, a little more the further out, once per kind | `Reward.one`: `Progression.discover(…, 0.3 + 0.02 × rank)` | Keep; add a whole level for a finished family |
| Placement engine | Biome on the spot, rank by distance, quiet land, no twins near, clear of villages and the city | SPEC §59.2 | Keep the rules; **change how a monument meets the ground** (next row) |
| A count of a real world, three monuments only | 2,025 cells, 17,000 blocks square: 616 cells chose a monument, **12 stand**. 604 found no ground level enough for a footprint 90 to 125 wide. Nearest to the start: Dish 1,547 blocks, Hall 2,193, Stair 4,600 | GitHub run 42835279338 | **The biggest engine job in this plan** (§4.6). If most choosing cells stood, that same world would hold a monument about every 700 blocks |
| Picture maker and tours | All scenes drawn in seconds; game pictures from GitHub | SPEC §59.7 | Keep. It is why building many is safe |
| The thirty places, the 144 finds | Rejected | STATUS.md | Not dealt. A few return as "what later people built" at a monument's foot |

**What it cost.** From your "100%" (20:22) to all three coded with their tests green (21:10): 48 minutes, most of it the
block family and the kit, which are now paid for. Then about an hour of game pictures and fixes, mostly waiting on
GitHub's runners. Estimates below are scaled from that, not from human pace.

**What the second game pictures show** (three runs, 14:29 UTC, every frame looked at):
- The Dish and the Stair hold up from every side. The Dish from where its painting stands is the closest match.
- **The Sleepers' Hall shows nothing above ground.** From outside it is a stairwell in a meadow. Inside it is right. A
  monument nobody can see from a hill fails "what is THAT". The plan carries the fix as a rule (§4.6, the skyline rule).
- Three cameras stand in the wrong place (the Stair's painting spot is in a tree outside the clearing, the Dish's inside
  shot is under a lean-to, the Hall's outside shot looks at grass). The monuments are fine; the frames are dropped.

## 3. The choice: by hand, a generator, or families

| | A. A hundred by hand | B. A ruin generator | **C. Families (recommended)** |
|---|---|---|---|
| What it is | Each monument its own idea, painting and code | Rooms, towers and walls assembled by rule, without end | Twelve ideas, each painted, each built as a generator; five named monuments a family; every site aged by its own seed and its land |
| Ideas | 100 | 1, wearing 100 hats | 12 strong ones, 60 monuments |
| Sameness | None | High. This is how vanilla ends up feeling the same | Within a family they are kin; across families nothing is alike |
| Your review | 100 paintings | None, and no bar to hold it to | 12 paintings (all on the page now) |
| Build | About 40 to 50 hours of session time | A day | About 12 to 15 hours of session time |
| Risk | The bar slips by number seventy | "Decoration" again | A family whose five are only sizes (the gate in §6 catches it) |

Why C: what made the three good was one strong idea each, a painting, and a kit that does the masonry. The masonry is
already procedural. The ideas cannot be. So your attention goes on twelve ideas, and numbers make sixty monuments and
uncountable sites out of them. What would change my mind: if the three dishes on the page read to you as "the same
thing three times", then it is A, and fewer of them.

A and C are not exclusive. A one-of-a-kind monument can join the list at any time for the cost of one.

## 4. The design

### 4.1 Twelve families, in the four groups the story already has

The groups are never named in the game (docs/STORY.md: the truth is "never stated outright in one place"). Each family
is one idea, one silhouette, one thing you do with your hands, and one relic. No two share a silhouette or a thing you
do. Three are built; nine are painted. Carvings below are drafts in plain speech; none says what is underneath.

**Before they split** (every land; the first you meet)

| Family | What it is | What you do | Its relic |
|---|---|---|---|
| **The Listening Dish** (built) | A stone bowl sixty across, tilted at the sky on a pylon | Set its fallen stones back; it hums and gives up what it heard | The Listening Stone: leans toward the nearest monument |
| **The Long Road** | A two-tier viaduct striding over a valley, one span down, a waystation on a pier | Get across the gap and into the station that still has its door | The Waystone: on a road or path you tire more slowly |
| **The Archive** | A round house of stone shelves under a broken dome, the tablets thrown about | Put the fallen tablets back in their order; the reading wheel turns | The Reading Glass: every carving teaches you more |

Carvings: "Sea gate, nine days. A bed and a fire at every station. Keep the lamps filled." / "Everything we learned is
here. We made three copies. If you can read this, the other two are gone."

**Those who went up** (high ground, cliffs, coasts)

| Family | What it is | What you do | Its relic |
|---|---|---|---|
| **The Stair That Stops** (built) | A stair on arches that ends in the air | Climb to the end and let the counterweight go; the doors below open | The Counterweight: slows one long fall |
| **The Mooring Mast** | A needle tower ringed with broken gantries, one hull still hanging on its chains | Climb inside to the top gantry and knock out the pin; the hull comes down and breaks open | The Mooring Ring: blasts and blows push you back less |
| **The Orrery** | Three bronze rings as wide as the Dish, tilted round a stone globe, one fallen | Turn the wheel-houses until the rings agree; the markers light | The Pocket Rings: the hour, and the way home |

Carvings: "Last ship. We waited four days for the ones from the valley. We cannot wait any longer." / "The way up opens
when the three rings agree. We have had it wrong twice."

**Those who went in** (forest, swamp, jungle)

| Family | What it is | What you do | Its relic |
|---|---|---|---|
| **The Sleepers' Hall** (built) | A sunken hall of stone beds | Light the lamps in the order they lay down; the round door opens | The Sleeper's Lamp: see in the dark below ground |
| **The Dream Garden** | A walled garden gone wild round a still pool, sleepers standing among flowers that glow | The gate only opens at night. Come back after dark and follow the flowers as they light | The Night Bloom: in the dark you mend when you stand still |
| **The Water Clock** | A stepped mount with a cistern on top and a dry channel down each face. It was counting how long they would sleep, and it stopped | Climb it and open the sluices from the top down; real water runs down the faces again and the wheel turns | The Float: longer under water, and faster through it |

Carvings: "They are not dead. Please do not move them. Water the flowers if you pass." / "It will wake us when the count
is done. If the water stops, start it again. Please."

**Those who went down** (desert, badlands, bare rock)

| Family | What it is | What you do | Its relic |
|---|---|---|---|
| **The Sealed Shaft** | A pit sixty across, a ring of giants round its rim, one fallen in. Every door on the way down is sealed but one | Go down the ledges to the last landing | The Seal Shard: the first touch of fire does not burn you |
| **The Chain Works** | A block like a fortress, three chains with links the size of houses running down a cleft; one has snapped | Climb to the capstan and turn it; far below, something takes up the slack | The Link: what you drop or dig comes to your hand from further off |
| **The Door in the Sand** | A temple front with four seated giants, buried to the chest in dunes | Dig the door out and go in | The Digger's Glass: sand, gravel and earth give way faster |

Carvings: "We closed every door behind us. Do not open them. We mean it kindly." / "Three chains hold. Two would hold.
Check them every year. Do not let it get to one." / "We left this door open for anyone who changed their mind. The sand
has shut it for us."

Changed from my first list after drawing them side by side: the Sky Cradle (too like the Stair) became the Orrery; the
Lantern House (lamps again, like the Hall) became the Water Clock; the Deep Well (a second pit) became the Door in the
Sand. In the game the Door's giants wear the plain masks of the Stair's giants, not the skull-like faces in its painting.

### 4.2 Sixty monuments: five a family, placed by distance

One of the five is a row of numbers and a few pieces of its own. It has its own name, its own page in the Journal and
its own silhouette. The rule each family must pass: put side by side as black shapes, you can tell all five apart.

| | Where | What it is | The Dish, as the example |
|---|---|---|---|
| 1 | From the start (rank E, D) | Small, most broken | The Small Ear: steep, on tall fins (drawn) |
| 2 | Rank D, C | Fallen: you read it lying down | The Fallen Ear: flat, half its rim gone (drawn) |
| 3 | Rank C, B | The one in the painting | The Listening Dish (built) |
| 4 | Rank B, A | Strange: two of it, or drowned, or grown through | The Twin Ears, facing each other |
| 5 | Rank A, S | Whole, and still working | The Ear That Still Hears |

So every band of distance holds monuments that cannot be found nearer home, and the last band holds each family's whole
one, which closes that family's story. Families come in by band too: the first group near home, the works of those who
went down last. Later: a second and a third stone (dark basalt and red bronze for down; white and gold for up), so a
group is known by its stone from a mile off.

### 4.3 No two sites alike

Already true of the three, by the ageing pass; made deliberate. A site's seed decides which parts fell and how far it
has gone. Its land decides how it wears: drifted with sand, capped with snow, hung with vines, standing in water. And
one of five kinds of later people left something at its foot (herders, climbers, pilgrims, a hermit, nobody for a long
time).

### 4.4 What you get, which is never "the same loot"

- **A relic that grows.** One a family, twelve in all. Each new monument of a family you finish raises its relic a step,
  to five. The Counterweight slows one long fall a minute at step one; at five it catches every fall. The Listening
  Stone reaches further each step and at five names what it hears. A site you have already done never pays a second
  relic.
- **The hoard.** Olden stone and bronze to build with (the only way to get them), and by rank the rare things the game
  already has at that distance. Each table is checked against the Journey ledger.
- **Levels.** A new monument is about a third of a level, as now. A finished family is a whole level.
- **The story.** Three carvings a monument. A family's fifteen, read in any order, tell that group's whole account. The
  four groups together are the truth of the world, and no single carving says it.
- **The Journal and the Tasks page.** Sixty silhouettes, dark until found, by family. The two newest leads sit on the
  Tasks page as now.

A relic that would touch another session's system (falling and the movement plan, digging and the Singing Deep) is
agreed with that session before it is coded, through the public API only.

### 4.5 Remains (the land between), if you want them

Pieces of the same world from the same kit: a giant's head in a field, one arch, a row of milestones, a pier with no
bridge. Fifteen kinds, small, common. Each belongs to a monument within about a thousand blocks and faces it, and its one
carving says how far. No relic. You rejected small finds once, for being random objects. These are not random: they are
the edge of something big, and they are how the world tells you where to go without a marker. Still your call (§7).

### 4.6 How it fits the engine

Trigger: a new chunk in a lively cell. Input: seed, biome on the spot, rank by distance. Choice: a monument whose family
suits the land and whose band holds this distance. Output: one site, written chunk by chunk, with a heart that runs its
steps. Kept for each player: which monuments are done (already kept), so relic steps and Journal pages need no new saved
data.

**Meeting the ground (new, and the count says it comes first).** Today a monument asks for land level to a few blocks
across its whole footprint, and 98 cells in 100 have none. Real monuments do not ask; they bring their ground. Each
family says how it stands: on a terrace it builds, stepping down to meet the land (Dish, Archive, Orrery, Water Clock);
striding on piers that reach whatever is under them (Long Road, Stair); cut into a slope or built against a ridge (Door
in the Sand, Chain Works, Hall); on a headland or summit it looks for (Mast); or in water (Dream Garden, Water Clock in
swamp). The search tries more spots in a cell. Gate: at least half the cells that choose a monument get one.

**The skyline rule (new, from the Hall).** Every monument shows above the trees from 150 blocks away. One that is sunk or
hidden by nature carries a mark that does: the Hall gets the ribs of its broken vault and its great trees; the Shaft its
ring of giants; the Garden its wall and leaning trees.

**New things you do.** Six to write, each a small verb like the three that exist: cross, turn, wait for night, open in
order (water), knock loose, dig out.

Must stay true: nothing floats; a scripted player can finish every monument; nothing within its own width of a village
or the city; quiet land stays quiet; two players are each paid once; no fight, no way to be stuck.

Failure cases: no ground even so (the cell is empty: quiet, by design); a monument that cannot be finished (the
walkthrough test fails the build); a relic a player lost (its step is remembered; any monument of the family gives it
back); a night-only gate found by day (the carving at the gate says to come back after dark).

Cost in play: a site is built once per world from its seed (about a tenth of a second) and cached; a chunk writes only
its own share. To be measured in the picture pass: the worst chunk time with a 23,000-block monument.

## 5. Shown, not told

On the page: the nine new paintings; the three built monuments beside their paintings, with what is wrong said plainly;
three dishes from one function; the table of what stands at each band of distance.

Walkthrough, ordinary: hour two, 400 blocks from home, a steep little dish on tall fins. You set its stones back, get
the Listening Stone and use it: "The stone leans north-east. About 900 blocks." Hour nine, 3,000 blocks out: a viaduct
across a valley with one span down, a kind of thing you have not seen. Hour forty, rank A: two dishes facing each other
across a lake, and your stone goes to step four.

Walkthrough, the disappointing case you described: you find a second Small Ear. Different fins standing, drifted with
sand, a hermit's hut under it. It pays stone and bronze and a carving you had not read. It does not pay a second relic.
And two cells on is something that is not a dish at all.

## 6. How it is built and checked

**Order (your rule: code it whole, test once).**
1. The twelve paintings: on the page now. You say yes, or change any.
2. The ground engine, the skyline marks, six verbs, twelve generators with five monuments each, twelve growing relics,
   180 carvings, the Journal's sixty. Coded straight through, no stops.
3. One test and picture pass at the end: every one of the sixty drawn offline and looked at; one of each family toured
   in the game beside its painting; a count of what a real world places.
4. A page of all sixty. You play.

**Gates before I show you anything:** all `finds_` tests green over all sixty; each family's five silhouettes distinct;
at most a third of any monument the game's own blocks (today 26 to 37%: the Hall is over, by its trees); the skyline
rule; the count shows at least half of choosing cells stand and every band holds monuments the band before does not; no
camera blocked.

**Estimate:** 12 to 15 hours of session time for the code; 2 to 3 hours for the pass, mostly waiting on GitHub's picture
runners. Remains, if wanted: 2 more hours. Two more stones: 2 hours each.

**What could change this:** a family that reads poorly in blocks whatever I do (the Orrery's tilted rings are the one I
would watch: I will draw it first and tell you if it fails); a relic that another session says no to.

**Rollback:** it is all on one branch behind the `finds` switch. Nothing reaches your play copy until you merge.

## 7. Decisions for you

1. **Families, not a hundred by hand and not a generator.** Recommended: yes.
2. **How many.** Recommended: twelve families, five each, sixty monuments. (Eight families is forty. Twenty is a hundred.)
3. **The nine new paintings.** Recommended: all nine. Name any to change or drop.
4. **Relics that grow, one a family,** in place of sixty separate ones. Recommended: yes.
5. **Remains between the monuments.** Recommended: yes, small and pointing. You rejected small finds once.
6. **Danger.** Recommended: still none. A climb, a puzzle, the dark. Say if you want guardians at the far bands.
7. **Two more stones** for the up and down groups. Recommended: later, after the sixty stand in olden stone.
8. **How far apart.** Recommended: about one every 700 blocks through lively land, none for long stretches in quiet
   land; I tune it from the count after the build.
