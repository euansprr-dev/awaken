# HEARTH module

Home base from the Ember's first fire to a town, the reading of the rooms you build, trophies, the Echo army, the Training
Grounds and the Reassessment trials. Package `dev.awaken.hearth`
(client code in `dev.awaken.hearth.client`). Feature flag: `AwakenConfig.HEARTH` (off = Anchors refuse to root, no upkeep,
no binding; registrations stay so worlds don't break).

Nothing here needs assigning or babysitting: rooms are recognised from what's in them, the town arrives by itself,
Echoes fill role pads by themselves, and every Y/N choice is optional.

## The carried flame (W1, SPEC §28.2)

The Hearth starts on the first evening, and from then on it reads what you build.

- **The Hearth Ember** (`awaken:hearth_ember`, `ember/EmberItem`, `Embers`): a small iron lantern with a bell-gold coal,
  a 3D item model (vanilla lantern proportions, its cage emissive). Carried in either hand its light is drawn on the
  ground round you and as a halo at the lantern (`client/HomeClient.GLOW`, a `citizens.client.CityGlow` source: no light
  block is ever placed). One per player: a spare goes out, and none is kept once your fire burns (`Embers.checkCarried`);
  kept through death. Given by Ilse at the camp (The Last Delivery's `ilse` step reward and her line); by Ilse or Hettie
  when you have no Ember and no Hearth (registered, and past that step or never on the chain; `PlayerInteractEvent` on a
  `Wayfarer`); on awakening in classic worlds (no city site). Now line `hearth:ember` (66): "Set your Ember where you'd live".
- **Setting it down** (use it on the top of a block): refused, each with a plain reason, on soft or open air, in the city
  reserve (`CityBasin.reserved`), in a Precursor ward, within 64 of a gate, in another Hearth's radius, or when a
  household rule refuses (members). The coal takes: the **Hearthfire** (`awaken:hearthfire`, light 14, a ring of river
  stones with an ash bed, a cracked glowing coal and a crossed animated flame; our own flame tongues and embers; its own
  crackle). **Tier 0**: radius 24, respawn, rest heals, calm land, and every rule that keys off any Hearth (no gates within
  64, no camps within 120, no happenings, wards off). No town, no Spire, no requests, no cottage. The moment, on every
  client within 96: sparks drawn down into the coal, a burst, then the warmth running out over 24 blocks as rings of soft
  glow on the ground's own height (`hearth_warmth`), fireflies if it's dusk, and `hearth.take` (a settling thud, stone
  clicks, a low hearth tone on D, a faint bell chord). 2.5 s later the **YOUR GROUND** card (`client/NameScreen`): a name
  offered from the land's words (`Embers.suggestName`: biome-flavoured first words and home-place endings, "Ashford",
  "Clover Rest"), editable, Enter keeps it. Walking onto your land shows the name as a place caption ("ASHFORD" over
  "your hearth", IMPORTANT, topic `hearth_place`). Fireflies over your land at night.
- **Lifting**: until the first room stands, using the fire empty-handed lifts the coal again (the warmth drawn back in,
  `hearth.lift`). Breaking it (owner only) also gives the coal back. After the first room, using it opens the guide.
- **Tier I**: a Lord's Heart (or the Anchor) used on your Hearthfire, or the guide's button, turns it into the Anchor in
  place (`Embers.raise`): the town, requests, the cottage offer and the furniture recipes arrive as when an Anchor is placed
  (`HearthService.anchorTookRoot`). Existing Anchors in old worlds are Tier I with the reading on.

### The reading (`room/Reading`, `Tally`, `Materials`, `RoomScanner.read`)
Every closed room is read in the pass the scanner already makes over its cells and wall faces (light is one engine read
per floor cell): **closed** (fences, bars and leaves let the wind in), **light** (all the floor at block light 8+ and
windows: 3; all lit: 2; most of it, or windows only: 1), **warmth** (a lit campfire, fire, lava or the Hearthfire 3; a lit
stove or the Forge 2; a cold stove or candles 1), **room to live** (floor against the purpose's target, height 3, or 4 for
halls; 2 high caps it at 1), **made with care** (share of worked blocks in walls, floor and roof: 25% → 1, 60% and two kinds
→ 2, 85% and three kinds → 3; from tags and block names, never a named block), **detail** (trim: stairs, slabs, trapdoors,
posts, chains, lanterns; soft: carpets, banners, paintings and frames, pots, candles, bookshelves; one kind of trim +1,
three +1, one soft +1, two +1; never a stack). **Grade**: average ≥ 2.5 Grand, ≥ 1.75 Fine, ≥ 1.25 Snug, else Bare, and
never more than one step above the weakest reading. **Name**: "the <grade> <material> <purpose>" (the main material
family's word, the most particular kind), or a sign's words inside. **Lift**: the weakest reading's fix ("light the north
end", "windows", "a fire", "something soft", "a higher ceiling"). Rooms now seed from doors, lights, chests, signs,
carpets, banners, pots and the household standing in the radius, not only furniture; solid seeds flood each open side on
its own. Caps: 48 a side, 20,000 cells; fills, readings and explanations all stop at the tick's budget and go on next tick.
Saved without their cells (`HearthRecord` schema 3, `Room.CODEC`): after a restart a room answers by its box until a
rescan, staggered 30 s to 4.5 min.

### Room kinds (`RoomType`, effects in `RoomEffects`, `Breaks`, `Town`, `HomeCrafting`)
Shelter (any closed room; the first one: "First shelter · Rest heals here"), Bedroom (a bed: Rested), Sanctuary, Kitchen
(stove, crafting table, barrel: food crafted or smelted there is home-cooked, `awaken:home_cooked`, and what it gives lasts
×1.5), Forge (today's Armory, id `ARMORY` kept), Study (lectern, six bookshelves; recognised), Greenhouse (soil or planted
pots under a roof at least half glass; recognised), Stable (hay, a fence gate: `awaken:whistle_range` +160 within 512 of
home, read by `world.fauna.Mounts`), Hall (six seats, a table, 48+ floor: a meal eaten in it feeds everyone in it), Bell
tower (a bell, the floor 8+ over the ground outside: rings at dawn and dusk, warns of a break due within two play-days,
Deep Tide's +2 skipped on your land via `HearthHooks.tideCalm`), Lookout (12+ up, glass on three sides: `AtlasHooks.reveal`
256 round home, a second Mira rumour a play-day), Trophy Hall, Workshop (machines pay ×0.75 essence through
`HearthHooks.machineCost` in `above.machine.MachineBlockEntity.draw`), Barracks.

### Comfort, Style, Rested (`Comfort`, `Rested`)
Comfort (0-12, best of each kind): your best bedroom's grade, a warm room, a table with seats, two kinds of soft things,
plants, and the land (a field of 16 farmland, a pen of two farm animals by fences, a garden of a composter and six
flowers, six lights outdoors, a wall of 40 fence or wall blocks). Style: the home's two main material families in words.
Waking in your own bed in a read Bedroom at home: **Rested** for 8 + Comfort minutes, `awaken:energy_regen` +0.25 and
`awaken:craft_rate` +0.25 (perk source on both sides, from the synced view), "◆ RESTED 14m" over the level line.

### The room panel and captions (`client/RoomPanel`, `RoomSection`, `HomeHud`)
The room you stand in at home (synced `hearth_here` attachment): name, grade, six pips, purpose, the lift. Today the K
Status page's first section while you're in one; each room's card in Your Hearth (with Comfort and Style in a HOME section,
kinds you have as chips, at most three suggestions as cards, the rest one dim line each). Entry point for The Way
Forward's glance: `RoomPanel.visible()` / `RoomPanel.build(Ui)`. The first time you walk into a room after it's recognised
or regraded, its name shows once as a quiet caption (AMBIENT, topic `hearth_room`, `ui.hearth_room`), filed to the Journal
when the director is busy.

### Chests at home (`HomeCrafting`)
A crafting table inside a Hearth you belong to opens a `CraftingMenu` subclass whose recipe placement first moves what you
lack from chests, barrels and shulker boxes within 8 blocks of the table into your inventory. No mixin: the client opens the
vanilla screen and always sends the placement, so a recipe the book shows red (the chests hold it) still crafts.

### Fixes (PLAN 4.9)
Caps raised and "too big" says which cap (`Miss.Leak.TOO_BIG` / `TOO_WIDE`); role pads found by a scan join the Hearth
(placed before it, or outside an old radius); Anchors and Embers refused in the city reserve; the town Spire comes as its
Board and Monolith items to place (the Board placed inside your Hearth becomes its board), never stamped into the ground; a
coal per temper at the Forge (Oren brings his own fire).

### Commands (admin)
`/awaken admin ember give|set|raise`, `/awaken admin hearth read` (rescan, then the room you stand in), `/awaken admin hearth
rested`.

## Your village (W6, SPEC §28.8)

People who live with you, the work they do with what you bring home, your own mine, tiers that grow from the home.
Package `dev.awaken.hearth.settle` (and `TierRules`, `stage/HomeStage`, `kit/HomeKit`).

- **Who comes** (`Settlers`): someone you know (the townsfolk by rank, a rank early once Carillon knows you; Ilse, Hettie,
  Wren and Pell when you've finished their chain; someone from a village where you're a Friend, and from the city; the
  grown-ups you brought home from a village's trouble, through W4's `Troubles.rescued`, in the face they wore there), when a spare bedroom of Snug or better stands (not yours: the
  bed you last woke in at home is yours) and their trade has its room (Ilse a Greenhouse, Hettie a field or a pen, a miner
  a Mine head; the townsfolk only a bed). One at a time, up to the tier's cap (1 / 2 / 4 / 8 / 12). Saved in the Hearth's
  `folk` (`Folk`, schema 1): who's known, who lives here, their beds, commissions, brews, what's kept to hand over.
- **The walk in** (`Arrival`): told to the household; with someone near, they walk up the road with a bundle on their back
  (a `TownNpc`, in their own face, `TownNpcRenderer`'s bundle layer) through the door to their bed; the room becomes
  "Ilse's room" (`Settlers.roomName`: the caption, the room panel, the guide).
- **Their day** (`HomePeople`, `HomeWalk`, `Days`): the Hearth runs on the people engine as settlement `hearth:<owner>`; its
  places come from your rooms (bedsides, the anvil, the greenhouse's soil, the lectern, the lookout's window, the hatch,
  the field's edge, the pen's gate, the hall's seats, the fire's ring), its street graph is walked over your blocks
  (budgeted), and their day runs from breakfast to bed. Doors open for them; the hatch for the miner. Faces of their own
  through `RosterBuilder.Person.face`; the household is kin to them (`HomeTalk.HomeVoice`).
- **What they do** (`Work`, `HomeTalk`, `Ropes`, `Letters`): Oren's commissions (the best piece you carry the makings of, by
  morning, tempered I, in the forge's chest), Hettie's harvest and breeding, Ilse's tonics (home-cooked), the miner's
  evening haul, Mira's rumours, Teodor's reading, Sun's mending, Wren's rope lines between your roofs (and a training
  course to lay), Pell's letter and rumour.
- **The Mine head** (`RoomType.MINE_HEAD`, `MineHead`): a hatch over 16+ blocks of climbable shaft; each evening the ore of
  its foot's depth comes up, and the lode's it cut. The Mine Echo works it too (no dice).
- **Tiers** (`TierRules`): II four rooms with a Fine one and a field or a pen; III a Grand room; IV a bell tower and four
  people; V eight (with the old ranks and materials). Radii 48 / 56 / 64 / 80 / 96. The Anchor (a hearthstone brazier, light 15, a warm pool
  of light on the ground: SPEC §28.11) shows the tier in its fire, lip and collar (`HearthAnchorBlock.TIER`). At IV the village is named on the Atlas and in the place caption ("your village").
- **The kit** (`kit/HomeKit`): drainpipe, trellis, window box, wall ivy, hoist beam craftable and dropping when broken
  outside Carillon. **Wiring**: a Study halves the Blueprints' paper; a Greenhouse triples herb ripeness.
- Admin: `/awaken admin hearth people | know | arrive | talk | tier | evening | wren | harvest | stage [settle | view]`.
- Tests: `hearth_settler_moves_in`, `hearth_tier_reading`, `hearth_mine_head`, `hearth_home_kit`.

## How to try it

1. `/awaken admin awaken`, then get a Lord's Heart (`/give @s awaken:lord_heart`) and craft a **Hearth Anchor**
   (copper ingot corners, stone bricks, Lord's Heart in the middle; copper rather than obsidian so your first base needs only stone tools). Place it: radius 48, respawn here, Oren arrives and a small
   Spire (Spire Board + Reassessment Monolith + lanterns) rises on open ground near the Anchor. The recipe book learns the
   Hearth's furniture (Meditation Mat, Forge, Trophy Mount, role pads). A toast says "Home exists"; the Now line says
   "Right-click your campfire" (an Anchor: "Right-click your Anchor"); six seconds later Oren offers a cottage (see *The Hearth guide*).
2. Use the Anchor: the **Your Hearth** screen (below). The radius shows in the world for eight seconds. The upgrade
   button (or Y) builds the next tier. `/awaken hearth guide` opens it from anywhere; the Status screen's HEARTH section
   has a button for it too.
3. Build rooms (they must be closed and roofed, at most 32 wide; doors, trapdoors, panes, bars, fences, walls, slabs and
   stairs close gaps):
   - Sanctuary: a bed + a **Meditation Mat** (wool, an amethyst shard, wool in a row).
   - Armory: an anvil + a **Forge** + an armour stand.
   - Trophy Hall: a **Trophy Mount** with a trophy on it.
   - Workshop: any block in `#awaken:machines` (filled by the Above module).
   - Barracks: any **Role Pad**.
   The screen and the Now tracker say what's missing and why a room isn't recognised. `/awaken admin hearth scan` rescans
   now (and lists every "not a room" with its reason); `/awaken hearth info` lists rooms, town, pads and Echo tallies.
4. Echoes: kill an elite/warden/boss (`MobRole.isBindable()`), tap **B** within 6 blocks during the 10 s it lingers.
   Tap B elsewhere to call/dismiss your field army; hold B for the chooser (march / home / release). `/awaken hearth echoes`.
5. Role pads (shapeless: 2 smooth stone slabs + essence shard + iron sword / chest / wheat / iron pickaxe / target).
6. Training Grounds: place a **Start Gate**, then **Checkpoint Rings** in the order to run them (each shows its number),
   then a **Finish Gate**; add **Target Dummies** for a combat drill. Step on the start to run.
7. Reassessment: reach the rank's level, touch a Monolith, press Y. `/awaken admin trial <D|C|B|A|S|penalty>` starts one
   anywhere (a practice run unless you're actually ready for that rank). `/awaken admin town arrive <npc>` brings someone in.

## What's built

**Anchor and data** (`HearthService`, `HearthData`, `HearthRecord`). One Hearth per player, overworld only, saved in
`data/awaken/hearths.dat`. `HearthHooks` installed (nearest / hearthOf). Respawn: placing or using your Anchor sets
`respawnAtHearth`; `PlayerRespawnPositionEvent` sends you to a safe spot beside it after any fall, anywhere. Sleeping in a bed off your land keeps it and offers that bed
on a choice card once you're up; only Y switches home off (and says so); the respawn's own copy of your old bed, a reset,
`/spawnpoint` and an inn room (lent for one fall through `HearthHooks.lendRespawn`) never do; with no bed elsewhere you wake
at home, never at world spawn (SPEC §28.10). Only the
owner (or an op) can break it. Upkeep runs once a second per Hearth, staggered by owner, and never allocates.

**Fracture heals at home**: stand still out of combat inside your radius for `30 - 4×(tier-1)` seconds (half that in a
Sanctuary; an overlay counts down) or sleep in a Sanctuary → `Progression.clearFracture`. Sun the Healer clears it for free.

**Rooms** (`room/RoomScanner`, `RoomType`, `Furniture`). Budgeted job, one at a time, 12k block reads per tick: a palette
sweep (`LevelChunkSection.maybeHas`) skips every section without furniture, then a bounded flood fill (≤4096 cells, ≤32
wide, must not see the sky, unloaded chunks count as open) from each piece of furniture. Full-block furniture (Forge,
machines) is read from the room's walls (and no longer floods the same room twice); armour stands are counted inside the
room's cells. "Sees the sky" is the motion-blocking heightmap plus a look up to 24 blocks for a slab or trapdoor roof.
Rescans 2 s after a relevant block changes nearby (furniture, full blocks, gap closers, armour stands placed or broken),
when the owner uses the Anchor, and every 5 minutes. New rooms are announced with a toast ("Sanctuary recognised" /
"Sleep here: Fracture heals."; not on the first scan after a restart). `Hearth.isSanctuary(level,pos)` and
`Hearth.inRoom(...)` are exact (cell sets).

The rule lives once: `RoomType.furniture` lists what each room needs; `Room.classify`, the toasts, the Status chips, the
guide's checklists and the Now lines all read it. `Furniture` holds each piece's label, short name, icon and the AWAKEN
recipe the guide shows.

**Why a room isn't recognised** (`room/Miss`, `guide/WhyNot`). When a flood leaks, a second bounded fill of the *covered*
space around the furniture (open sky counts as a wall) says why, and furniture in the same leaky space shares the answer
without another fill (the furniture nearest the Anchor is filled first; at most 24 explanations per scan and 4 per kind,
so a village's beds can't use them up): a covered cell with sky right above it → **it sees the sky above the
bed (no roof)**; sky beside the space inside its footprint or with a wall right behind it → **a hole in the roof at x y
z**; sky beside it at the edge → **an opening at x y z** (the covered cell the space leaks through); bigger than 32 →
**too big (over 32 wide)**. A closed room holding part of a room's furniture → **missing: a Meditation Mat**. The scan
also counts each piece of furniture in the radius (mounted trophies only; armour stands by an entity query at the end)
for the guide's ticks. Cost: one extra bounded fill per leaky space, inside the same 12k-per-tick budget.

**The Hearth guide** (`guide/Guide`, `GuideData`, `client/HearthScreen`, `client/GuideSections`, `client/GuideClient`).
Using your own Anchor opens **Your Hearth** (a `QuietScreen`: toasts wait while it's open) instead of a notice; a client
without the screen still gets the old notice and its Y. `HearthScreen` is only the chrome (Status-screen glass with a
lantern edge, the header, scrolling, clipping); the content is `UiSection`s in `GuideSections` built only from
`signal.client.Ui` primitives (no raw drawing), so a future surface that hosts Ui sections can show them. `Ui` gained
three small primitives for it: `card(accent, body)` (a glass card measured, drawn and clicked in one pass),
`itemAt`/`Flow.item(stack, label, colour)` (12 px item icons with tooltips, in a wrapping flow) and
`recipe(w, h, cells, result, count)` (a crafting grid, an arrow, the result).
- header: YOUR HEARTH, the tier chip, the Anchor's position; then radius, rest healing (and half in a Sanctuary), where
  you respawn;
- Oren's cottage offer on top until it stands (Raise it / Try again / being built);
- ROOMS: the rule in one line (from `RoomScanner.MAX_EXTENT` and the gap closers), then a card per `RoomType`: name and
  status (Built · x y z, Built ×N, Not yet), what it gives (`RoomType.does`), a checklist with item icons (✓ when that
  furniture is anywhere in the radius; the last line "all of it in one room"), the rule in one line ("A closed, roofed
  room: walls, a roof, a door; at most 32 wide."), the real recipe of each
  missing piece of Hearth furniture drawn as a grid (tag slots cycle; read from the server's recipe manager), the reason
  it isn't recognised (amber), and "Oren built this one. Build the rest yourself." on the cottage's Sanctuary;
- TOWN: who lives here, who comes next and at which rank;
- NEXT UPGRADE: the tier's radius and rest time, each part as have/need with its icon (green when you have it), the rank
  discount, and **[Y] Build Tier II** (the button or the accept key; the server re-checks everything, `Upgrades.upgradeAnchor`).
The screen asks for fresh numbers every 2 s and gets them after every scan, upgrade and cottage step. Using the Anchor
shows the radius in the world for 8 s: amber motes along the square edge at ground level (≤24 a tick, client side).
While the screen is open, and 30 s after, a pulsing signal-blue mark sits on each leak (`hearth_ring`, `hearth_leak`).

**Taught in play** (`HearthNow`, one line on the Now tracker, never a list). Placing the Anchor: the toast "Home exists ·
Within 48 blocks: yours."; at home, "Right-click your campfire" (an Anchor: "Right-click your Anchor") (priority 74 for ten minutes, then 56) until the screen
has been opened once; then the next room with live ticks ("Sanctuary: bed ✓ · mat ✗", shortened to fit 148 px) for a
Sanctuary, then an Armory, then a Barracks, Trophy Hall or Workshop once you hold or placed what it's made of; and, when a
room you're building isn't recognised, why ("Sanctuary: no roof", "Armory: wall gap", with a compass to the spot,
priority 62). Village beds and anvils alone never trigger a why-line (only the current goal, mod furniture, or two pieces
of one room). The toast when a room is recognised closes the loop. Every line is checked against the toast (144 px) and
tracker widths (148 px, 100 with a compass) by `GuideTests`.

**Oren's cottage** (`guide/Cottage`, `CottagePlan`). Once per player, six seconds after the Anchor takes root, a choice
card: "A cottage from Oren? · Free: your first Sanctuary." [Y] Raise it · [N] I'll build my own (no only marks it
declined; the screen keeps the offer, moved to its bottom: "Changed your mind?"). Yes starts a search, then a day's build
(SPEC §28.11):
- *Site*: 112 candidate centres 10–22 blocks from the Anchor, the door facing it, ranked by heightmap flatness and
  distance (one tick), then two full checks a tick: every column of the 12 × 13 box must be terrain (dirt, sand, stone,
  terracotta, gravel, clay, snow, mud...) with only plants, snow, vines and natural (non-persistent) leaves above it, up
  to three natural trees (logs whose top touches natural leaves) that get felled, no fluid; the footprint may fill down
  4 or cut up 2, the rim may rise 1; it keeps 2 blocks from the Anchor and 3 from the Spire. Anything a player makes
  (planks, cobble, farmland, paths, placed leaves, chests) rejects the spot. None in 40 tries → a notice ("No room for a
  cottage... clear a patch about 12 blocks across... then ask again in your Hearth") and the screen's Try again.
- *Build*: the site cleared at once, then one in-game day for the cottage (`CottageBuild`, saved on the Hearth as
  `cottage_build`, schema 1): foundation 14%, frame (posts and plate) 14%, walls 24%, roof (ceiling first) 28%, chimney
  5%, furnishing 15% of the day, layer by layer and row by row. The work follows the game ticks or the clock, whichever
  moved more, so it catches up (64 blocks a tick, quietly) after a logout, a restart or the chunks unloading. Each live
  block plays its place sound with embers; Oren walks the site with a mace as a hammer and swings at each one. A block
  never goes into a cell where something stands (held and retried; doors and beds go in whole) and never replaces a
  block that isn't terrain or a plant. On completion: the Anchor chime, "Oren's cottage is finished · Your first
  Sanctuary is ready beside your Anchor.", `HearthRecord.cottage` (a cell inside, saved) and a rescan. The guide's card
  says what he's on and when it'll stand ("It'll be ready by tomorrow evening.").
- *Design* (`CottagePlan`, local frame, turned to face the Anchor): 9 × 9 on a stone plinth that fills down to the
  ground; log corner, door and mid posts; a stone lower course with weathered patches; plank walls four high; seven
  two-tall windows with open trapdoor shutters, flower boxes under the two front ones and a light over the door; a log
  wall plate; a plank ceiling (so the room is closed whatever the roof does); a 45° stair gable roof with a one-block
  overhang, thick gable edges (upside-down stairs under the edge), a slab-capped ridge whose log beam shows at both
  gables, and an attic window in each gable; a chimney on the right gable with a campfire on top and a stove behind the
  wall; two lanterns hanging under the front eave; a step or a dirt path toward the Anchor. Inside (7 × 7 × 4): a bed under
  the back window with a barrel nightstand and a lantern, the Meditation Mat, a 3 × 3 rug, a hanging lantern, a chest, a
  crafting table, a smoker, a bookcase and potted plants. Ten biome palettes (meadow, taiga/snowy/cold, birch, jungle,
  savanna, desert, badlands, swamp/dark forest, mangrove, cherry) set the planks, posts, stone, roof, door, shutters,
  chimney, step and bed and rug colours (the desert: pale sandstone, spruce beams and a clay-red acacia roof).
  Previews: the GameTest dumps every palette to `run/structures/cottage_<palette>.json`;
  `python3 tools/art/specs/structures.py preview DIR cottage` draws them (two angles, day and night).

**Costs that fall** (`Costs`, `Upgrades`). Price ×0.85 per rank you hold above the upgrade's tier (compounding, rounded up,
never below 1). Anchor tiers II-V (D, C, B, A): radius 56/64/80/96 (I is 48), faster rest healing, and (W6) a home that has grown (TierRules); mostly overworld materials plus a
little essence (tier V also a Lord's Heart). Forge tiers II (D) and III (B). Tempering: weapons +1 damage per level, armour
+0.5 armour +0.25 toughness per level, stored in `awaken:temper`; limit 1 anywhere, 2 per Forge tier in an Armory (max VI).
Oren tempers too and brings his own essence.

**Trophies** (`trophy/Trophies`). A player-killed `MobRole` BOSS always drops its first trophy (per boss id), then 20% of the
time; boss id/name/day in the `awaken:trophy` component. Hanging one counts `awaken:trophies_placed` (once per trophy) and
`markNewThing` for a new boss. Trophy Hall bonus per different boss: +1% attack damage, +0.5 max health (cap 20), as
transient modifiers re-applied on login, respawn and scans. The nearest townsperson comments.

**Town** (`town/`). Definitions from `data/awaken/awaken/townsfolk/*.json` (added `vael_rival.json`: arrives at A, leaves at
S with a note). `TownNpc` (player model, generated skins) wanders around the Anchor (`setHomeTo`, opens doors), can't be hurt,
gathers for celebrations, and is respawned if missing (only when the whole home area is loaded; duplicates loaded later
remove themselves). Talking: rank line (or chatter) through `Signal`, plus services: Oren tempers held gear; Mira gives one
rumour per play-day (nearest gate near your rank via `GateHooks.near`, else `#awaken:precursor_ruins`, else a ruined portal)
that becomes a Codex entry (category "Rumours"); Teodor reads your Codex aloud (Path, Truths, Titles, Latents, Gate Lords,
rumours, depth) or identifies the held item; Sun clears Fracture.

**Recognition**. `RankHooks.onRankUp`: new arrivals, then a celebration at the Hearth (now if you're home, otherwise when you
get back): seven fireworks in the rank colour, townsfolk gather to you, a "The town gathers" window with each one's line for
the new rank, the Spire Board refreshes. Rank auras: `awaken:aura` attachment (rank + rank/Path colour) synced to everyone
nearby; the client draws a few motes (more per rank, embers at S+), off when `AwakenConfig.Client.AURA` is false.
Day's End lists a pending celebration.

**Echoes** (`echo/`). Binding: base 55% elite / 35% warden / 20% boss, +1% per SPI over 5, +10% per rank above the fallen
(−15% per rank below), + `Perks.BIND_CHANCE`, clamped 5-95%. Bosses need S-rank or the Shadowbinder Path. One attempt per
lingering Echo. Field cap `3 + SPI/6`; roster cap = field cap + one per role pad. Roster in the player attachment
(`EchoRecord`: entity type, saved entity data minus position/health/UUID/brain, name, level, xp). Summons are faithful copies
of the original type: old goals cleared and replaced (`EchoGoals`: follow owner, strike whatever hurt the owner or what the
owner hit, hunt monsters, melee or ranged), tagged `Forces.Tag.ECHO` and `MobRole` ECHO, scaled by content level, origin and
Echo level, fire-proof, no drops/xp. Allies are enforced three ways: target goals, `LivingChangeTargetEvent` and
`LivingIncomingDamageEvent` (owner, the owner's Echoes, townsfolk and ghosts are never targets or victims). Strikes are
credited to the owner (gate kill XP still counts). Echoes level slowly (kills 1/5/10/25 xp by role, Train pads), fall and
reform after 60 s, follow you across dimensions, leave with you on logout. At most 12 field bodies. Counters:
`awaken:echoes_bound`, `awaken:echo_army_peak`. `Hearth.ownedEchoes(player)` for echo_swap. Clients draw Echoes dark with a
violet outline.

**Duty** (`echo/Duty`, `DutyGoals`). Pads take Echoes that aren't marching (strongest first to Guard pads, then pads in
placement order). Guard: patrols the Hearth, hunts monsters in the radius, goes to hold a break. Haul: picks up loose items
(older than 5 s) and puts them in the chest that already holds that item, else the nearest chest. Farm: harvests ripe crops
within 10 blocks, replants (keeps one seed back), stores the harvest. Mine: every 2 minutes of duty brings ore "from below"
(coal/iron/copper/redstone/lapis/gold, rare diamond; better with Echo level and Anchor tier) into a chest. Train: +1 Echo xp a
minute (×2 in a Barracks). Duty Echoes heal on their pads (faster in a Barracks) and run only while the owner is online and
home is loaded.

**Breaks near home** (`Breaks`). Every 5 s: `GateHooks.near(level, anchor, 256)`; a BROKEN gate is recorded per gate id with
the owner's `breaks_held_at_hearth` value at that moment, Guards go to it. When that gate shows CLEARED and the counter hasn't
moved (the gates module didn't count it), we count it once. `Perks.HEARTH_SHIELD` during an active break: mobs can't grief
and non-player explosions don't break blocks inside the radius.

**Training Grounds** (`training/`). Courses keyed by Start Gate; rings join the nearest Start Gate within 64 blocks in
placement order (renumbered on removal); dummies count when struck or hit by your projectile. The clock holds at zero while
you stand on the start. Laps count `awaken:training_laps`; beating your best counts `awaken:training_records` and
`markNewThing`. Your best run is recorded per tick (≤5 min) and replayed as a translucent ghost the next time you leave the
start. Bests per player; ghosts in `data/awaken/training.dat`.

**Reassessment** (`trial/`). Dimension `awaken:trials` (fixed night, no weather, generator `awaken:trials`): 48×48 walled
arenas on a 512-block grid, even cells are halls (tile and amber lamps), odd cells are the desert-at-night Penalty Zone. One
cell per running trial (`TrialData`). D: three waves. C: two waves then the Second Awakening (`Progression.offerPaths`, a
window listing the Paths that answered). B: a Gate Lord of a theme fitting the rank (`MobHooks.spawnBoss`, falls back to a
themed BOSS, then a scaled zombie). A: five stacked rooms in four minutes; each cleared room's floor opens (slow falling).
S: `awaken:sovereign_mirrors` at half health, with its intro line from the story data. Mobs via `MobHooks.spawnThemed`. Trials
never kill (a lethal hit ends the attempt). Pass → `Progression.passReassessment` and home; fail → home, retry next play-day.
Logging out or leaving fails it; logging in inside the trials dimension sends you home.

**Hardline Penalty Zone**: `Regimen.penaltyHandler` starts it when you're in the overworld and free (otherwise it waits):
survive four minutes of waves in a desert arena; failing Fractures you for 8 minutes.

## Commands
`/awaken hearth info` · `/awaken hearth echoes` · `/awaken hearth guide` · `/awaken admin trial <D|C|B|A|S|penalty>` ·
`/awaken admin town arrive <npc id or first name>` · `/awaken admin hearth scan` ·
`/awaken admin hearth cottage` (raise it now) `| reset` (forget the once) `| offer` `| hurry` (finish the day's work)
`| view <outside|inside|side>`
(camera spots for screenshots).

Autopilot: `tools/autopilot/hearth-guide.txt` (the integrator runs it).

## Tests (`HearthTests`, `guide/GuideTests`, all GameTests)
registry + nearest lookup · cost reduction maths · bind chance maths and caps (and Echo levelling) · Echo ally rules (never
targets or hurts its owner or other Echoes) · role pad assignment · room detection in-world (a closed room with a bed and a
mat is a Sanctuary) · D trial passes → rank D · Penalty Zone lifecycle (waves, pass, cleanup) · trials dimension type/biome/
generator decode · pure rules (break counting, lap comparison, compass, arena blocks, ore table, rest times) ·
`hearth_guide_matches_roomtype` (each card's checklist is `RoomType.furniture`, `classify` needs all of it, every toast and
Now line fits, the guide data round-trips, the site rules) · `hearth_cottage_is_a_sanctuary` (two cottages, one turned and
in the desert palette, both scan as Sanctuaries; the guide says Oren built one) · `hearth_why_not_reasons` (roofless →
sees the sky; a gap → an opening at that block; a hole in the roof → there; no mat → missing: a Meditation Mat; fixed →
a Sanctuary) · `hearth_cottage_offer_once` (one card, never twice, not after a no, the screen still offers it) ·
`hearth_cottage_builds_over_a_day` and `hearth_anchor_light` (`guide/CottageBuildTests`, SPEC §28.11).

## Decisions
- Trials live in their own `awaken:trials` dimension (the builder brief asked for it) rather than the Stillpoint's trial wing
  in SPEC 4.4. SPEC should be updated to match.
- Roster cap = field cap (3 + SPI/6) + role pads, which is how "Echoes on duty don't count against the field army" works.
- Echo strikes credit the owner so gate kill XP and floor kill counts keep working for Shadowbinders.
- Costs fall multiplicatively (×0.85 per rank above), so they never reach zero.
- Trial bosses and wave mobs (gate id `trial:<uuid>`) never drop trophies and can't be bound.
- The SOVEREIGN rank has no Monolith trial: the Monolith says it waits at the Seed (story module).

## Known gaps
- The GameTest server only creates the overworld, so arena generation in the real dimension is covered by decoding the
  dimension JSON and testing the generator's block rules, not by loading a chunk.
- Echo copies of brain-driven mobs (piglins, wardens) keep their brain; the damage filter still stops them hurting you.
- Gate kills by Echoes while you are bare-handed count toward `awaken:kills_unarmed` (progression counts any kill credited
  to an unarmed player).
- Only the town Spire's board refreshes on its own; extra Spire Boards refresh when used.

## SPEC-worthy decisions (for the integrator)
1. Using your own Anchor opens the **Your Hearth** screen (a `QuietScreen`); the old notice remains only for clients
   without the screen. The upgrade is built from the screen's button or the accept key, re-validated on the server.
2. Stairs and slabs close gaps like doors and panes (a stair or slab roof is a roof); slabs and trapdoors up to 24 blocks
   above the heightmap count as a roof.
3. Why a room isn't recognised comes from a second bounded fill of the covered space (sky as a wall), nearest furniture
   first, ≤24 per scan and ≤4 per kind, in the scan's budget: sees the sky (no roof), a hole in the roof, an opening at x y z, too big (over 32 wide), missing: X.
4. The Anchor teaches the Hearth's furniture recipes (Meditation Mat, Forge, Trophy Mount, the five role pads) when it
   takes root, whatever the level.
5. Oren's cottage: offered once per player (a choice card six seconds after the first Anchor), free, never on anything a
   player made: only terrain, plants, natural leaves and up to three natural trees give way. 9 × 9 outside, 7 × 7 × 4
   inside, ten biome palettes, four blocks a tick (~7 s). Its room is a Sanctuary on completion. No paid cottages yet.
6. The Now tracker teaches the Hearth one line at a time: "Right-click your campfire" (an Anchor: "Right-click your Anchor") (74 for ten minutes, then 56) until
   the screen is opened once; then the next room with live ticks (50); a why-line for a room being built (62). Lines are
   chosen server side to fit 148 px (100 with a compass), toasts' first sentences 144 px.
7. Using the Anchor shows the radius (a square, |dx|, |dz| ≤ r) as a ground-level ember ring for 8 s, client side.
8. Saved additions: `HearthRecord.cottage` (optional block pos in `core`), `HearthPlayer.tips` markers `guide_seen`,
   `cottage_offered`, `cottage_declined`, `cottage_built`, `cottage_no_room` (no schema change: both are optional fields /
   free-form tips).
9. Room toasts read "<Room> recognised" with a one-line gain (`RoomType.toast`); the full text (`RoomType.does` and the
   furniture) stays in Recent messages.
