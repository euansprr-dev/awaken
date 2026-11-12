# Hands at Work: every block you work, by your own hands

Plan record. **Approved in full by Euan on 10 Nov 2026, after trying the page: "Go with all recommendations and the
full plan".** All ten decisions in §7 are yes as recommended. **Built** on branch `claude/interactions-plan-145244`, all
seven stages in one run, then tested (§8 says what was built, what the look tours showed and what is left). SPEC §65 is
the game as it is. Not merged; not yet played or heard by a person.
Approval page (private): https://claude.ai/artifact/LZUb3jA5f6B3iYcwQNdK2B (his answers are its `answers/d1` to `d10`).

Euan, 10 Nov 2026:

> looking at all the interactive animations with like the crafting tables and the chests and everything like that to
> massively improve the smoothness of all of it. Like, for example, in the crafting table, I think it would be dope if
> when you're like hovering with an item, it actually shows like the actual hand of the player going over the box with
> that item in its hand, for example. And just overall, like for all of the like interactive animations like that, like
> massively level them up. Think about how it acts when you're more at the distance, when you're closer. Think about the
> POV, so it's really optimized to still be like usable and very nice. Same with the furnace. Maybe like the furnace, you
> actually crouch down, so you put stuff in the furnace that way. Think about the animations and from third person as
> well. And like for the audit, basically all of that, and then create a plan to massively improve them and bring them
> up to like AAA level and the quality bar that we keep saying.

## 1. What is being asked, and how we will know it worked

**The job.** Whenever you work a block (lay out a recipe, load a furnace, open a chest, hammer at an anvil), it should
feel like your own hands doing it: smooth from the first frame to the last, from wherever you stand, in first and third
person, and as quick to use as the plain screen.

**What falls short today** (each one checked in the code and filmed; §2 has the evidence):
1. The hand does not carry anything. The stack on your pointer is a flat screen icon; your hands wait at the table's
   corners and tap a square after the click has already happened.
2. The scene is only as good as the spot you happen to stand on. The view is framed from your own eyes, so two blocks
   back a grid square is a 68 by 21 pixel sliver seen from 18 degrees, and four blocks back it is 114 by 18. From a
   corner the grid is a diamond. From two blocks back the hands are not in the frame at all.
3. In third person there is no scene at all: the plain grey screen opens and your character does nothing but vanilla's
   arm swing. Nobody else ever sees you work.
4. At the furnace the view drops to the mouth, but nothing crouches: no body, no bend in the move, no weight.
5. Nine vanilla workstations (anvil, smithing table, grindstone, stonecutter, loom, cartography table, brewing stand,
   lectern, beacon) play a short hand moment and open a grey screen over the top of it in the same tick, so the moment
   is mostly hidden. Four of ours do the same with their own screens (Forge, Hearth anchor, waystone, Spire board).
6. It is silent and it pops. Laying an item makes no sound; items appear and vanish in one frame; a stack on a square
   shows no count.
7. Quick clicks restart the hand from its rest every time, which reads as a twitch.

**His ideas, as ideas to test:** the hand carrying the hovered item over the grid; a real crouch at the furnace; care for
distance, for the point of view and for third person. All three hold up and are in the plan. The audit added the rest.

**Hard constraints** (from his earlier verdicts):
- First-person arms stay one straight vanilla arm per hand. No elbow, no second piece, no sleeve end in view.
- One side decides. The hands follow your pointer and your clicks; a reply from the server never moves them.
- Nothing may read as vanilla or as an add-on.
- Sounds come from Minecraft's own recordings, named in `sounds.json`.
- Nothing is automated and no chore is added. Player-facing text is plain speech.
- **It must stay usable.** Every click lands exactly where vanilla's would, no click ever waits for a hand or a camera,
  and nothing you need to read is hidden.

**Success, in things you can check:**

| | Strong | Weak | Not acceptable |
|---|---|---|---|
| The hand | Your own hand carries the stack over the grid and sets it down | A prettier tap after the click | An elbowed arm, a hand that lags the pointer, a hand that hides a slot you can't read through |
| Distance | The grid is the same size and in the same place on screen every time, from 1 to 4.5 blocks | Looks right only pressed against the table | Slots too thin to click; a view inside a wall |
| Speed | Nine items laid in the time vanilla takes; clicks live on the first frame | A lean-in you learn to wait for | A click that waits, or lands on another slot |
| Third person | Your character bends to the work and others see what you make | An arm swing | Two players seeing different things |

**Not in this plan:** new recipes, new blocks, any change to what a station does, automatic sorting or crafting, the
War Anvil's, Inscribing Table's and Forge's own scenes (they are already at the bar and only join the shared rules).

## 2. The audit

Tours on CI at his window size (`tools/autopilot/interact-audit-stations.txt`, `-vanilla.txt`, `-awaken.txt`,
`-plates.txt`; runs 42851717030, 42851757952 and the two after them); pictures and frame strips are on the page.
`docs/interactions/make_assets.py` cuts them. Sizes below come from the game's own camera maths
(`StationView.frame`, ported in `docs/interactions/proto/stationgeo.py`) at a 488 by 266 GUI.

### 2.1 What exists

| Piece | What it really does | Consequence | Verdict |
|---|---|---|---|
| `anims.client.WorldBody`, `Hands`, `core.api.FirstPerson` | One owner for first-person limbs; a rigid vanilla arm rising to a fist placed in the world; an item can sit at the fist (`Rig.held`) | Everything needed to draw a carrying hand is already here | Keep, extend |
| `anims.client.station.StationClient` / `StationScreen` / `StationView` | Crafting table and furnace: vanilla's own menu, its station slots drawn on the block, clicks by a ray through the view | Clicks are exactly vanilla's, which is the part to protect | Keep the click path; replace the view and the hands |
| The view (`StationView.frame`) | Turns and zooms from your own eyes until the slots fit above the inventory | The picture depends on where you stand: field of view 50 degrees against the table, 19 at two blocks, 5.7 at four | Replace with a view that belongs to the station |
| The station hands (`StationClient.pose`) | Rest off the near corners; a 9-tick out-and-back tap to a slot after its contents change; a new tap restarts from rest | The hand never holds anything, and quick clicks pop | Replace |
| `StationCamera` (furnace) | A camera entity lowered to the mouth's height above where you stand; your body is hidden and does not move | The view drops with no crouch in it | Keep the mechanism, give it a crouch |
| `FurnaceContents` | The server shows nearby players what is in a furnace's three slots | Already the right pattern for "others see it" | Keep, reuse for tables and open chests |
| `anims.client.Touches` / `Reaches` | 14 one-shot hand moments chosen by block class; the hand goes to the thing in the world; past 2.5 blocks it only reaches toward it | Good bones. No sound of its own, no third person, and a screen that opens in the same tick covers it | Keep, extend |
| The chest's wait (`Touches.LID_WAIT`) | A chest's screen is held 4 ticks so both hands reach the lid | The only touch you can actually see before its screen | Keep, make it the rule |
| `storage.client.GlassChestScreen`, `GlassShulkerScreen`, the bag | Vanilla's slots drawn on the band's glass | Fast and already ours | Keep |
| Anvil, smithing table, grindstone, stonecutter, loom, cartography table, brewing stand, beacon, hopper, dispenser, dropper, crafter | Vanilla's grey screens, unchanged | These read as vanilla | Scenes or glass (§4.8) |
| `core.api.PlayerClips` (third person) | Full-body clips for the Runner, the Ascent, casts, weapons and the War Anvil's smith | The machinery for a body at a station exists; no station uses it | Extend |
| War Anvil, Inscribing Table, Forge Song, Hall of Arms | Their own cameras, tools in the hands, the smith in third person, the burin that follows the pointer | This is the bar, and the Inscribing Table already proves a hand can follow the pointer | Keep; they join the shared rules only |

### 2.2 Every block a player works, as it is today

**Scenes (slots in the world):** crafting table; furnace, smoker, blast furnace.
**A hand moment, then a screen that covers it:** chest, trapped chest, barrel, ender chest, shulker box (4-tick wait,
glass screen); anvil, smithing table (hammer); grindstone (crank); stonecutter, loom, cartography table, brewing stand
(both hands set down); lectern (page); beacon (palm).
**A screen and nothing else:** hopper, dispenser, dropper, crafter.
**A hand moment, no screen:** door, trapdoor, fence gate (push); lever; button; campfire (food laid); berry bush, cave
vines, flowers and herbs (pluck); an animal (stroke); lodestone, respawn anchor (palm).
**Nothing:** bell, jukebox, note block, composter, cauldron, flower pot, chiseled bookshelf, decorated pot, item
frame, armour stand, bed, cake, sign, candle, end portal frame, repeater, comparator, daylight detector.
**Ours, at the bar:** War Anvil, Inscribing Table (a vanilla enchanting table turns into one), the armour Forge's
song, the Hall of Arms.
**Ours, a palm, a lid or a tear:** waystone, conduit, ascent stone, Hearth anchor, rest shrine, Stillpoint shrine, gate
entrance, seal and rune, Spire board, every `*_cache`, the Forge (three hammer blows, also when it only opens an offer).
**Ours, nothing:** Word Stone (its own hint says "hold your hand to it"), Hearthfire, Fitting Stand, Pattern Stand,
trophy mount, foundry crucible, storehouse drop, ledger desk, work plan, bell frame, found bell, sleeping bell, dream
bell, waking pick stone, piton, Precursor log terminal, fabricator, broken machine, Precursor door and door core, return
pad, ghost door and lever, Stillpoint door, left chest, carved token, fallen star, well lift, bedroll.

### 2.3 Measured

| What | Today | Evidence |
|---|---|---|
| The centre square, pressed against the table (1.05 blocks) | 42 by 22 GUI pixels, seen from 30 degrees | `stationgeo.py`; shot `a_table_near` |
| The centre square at two blocks | 68 by 21, seen from 18 degrees, field of view 19 | shot `a_table_mid` |
| The centre square at four blocks | 114 by 18, seen from 9 degrees, field of view 5.7 | shot `a_table_far` |
| The same square from the view this plan proposes | 31 by 26, seen from 54 degrees, from any distance | plate `p_table_empty` |
| Time before the grid holds still after you use the table | 12 ticks (0.6 s); 6 if you come back within 12 s | `StationClient.weight`; clip `a_table_in` |
| The hand's answer to a click | Starts after the slot has changed, 9 ticks out and back, restarts from rest on the next click | `StationClient.tick`, `pose` (read in the code; the CI runner filmed a frame about every 10 ticks, too coarse to show a 9-tick tap) |
| The hands at two blocks | Out of the frame through a whole lay (0, 12, 22, 32 ticks); one reaches it after three quick clicks | clips `a_table_lay`, `a_table_fast` |
| From a corner | The grid is drawn turned 45 degrees, a diamond | shot `a_table_corner` |
| Your character in third person | One vanilla arm swing | clip `a_table_3p_front` |
| Sounds for laying, lifting, the result | None | No sound call in `anims.client.station` |
| A stack's count on a square | Not shown | `StationClient.onSubmit` draws the item only |
| Third person | The plain vanilla screen; no scene | `StationClient.on()`; shots `a_table_3p`, `a_furnace_3p` |
| What others see | Nothing at a table; the furnace's three items | `FurnaceContents` |

## 3. Why it falls short

| Symptom | Cause | Evidence | Fix in |
|---|---|---|---|
| The hand doesn't carry | The hands were built as a reaction to a changed slot, not as the pointer | `tick()` compares slots, then taps | §4.2 |
| Depends on where you stand | The view is framed from your eyes with zoom doing all the work | The numbers in §2.3 | §4.1 |
| Nothing in third person | The scene is gated on the first-person camera, and no body clip exists for a station | `on()` | §4.5 |
| The furnace doesn't feel like crouching | Only the camera's height changes, on a plain ease | `StationCamera.place` | §4.6 |
| Hand moments hidden | A touch and its screen start in the same tick; only the chest waits | `Touches.screenOpening` | §4.9 |
| Pops | Items are drawn from the menu's state with no life of their own; the tap restarts from rest | `onSubmit`, `pose` | §4.2, §4.3 |

One thing is *not* a cause and stays as it is: the click path. The scene screen is vanilla's own container screen and
the server checks every click. Every change below is drawn around that path and none of them touches it.

## 4. The design

### Three directions, and the one recommended

| | What it is | Why not, or why |
|---|---|---|
| A. Repair | Keep today's scenes; add the carrying hand at today's view; fix the pops; add sounds | The view stays a sliver from two blocks back, and third person stays a grey screen. It answers one sentence of the brief |
| **B. One stage for every station (recommended)** | A view that belongs to the station, the carrying hand, items with weight and sound, a body for others to see, applied family by family | Fixes each cause once, and every later station gets all of it by describing its slots |
| C. Everything in the world | Chests and every other screen become objects you handle one by one, with no slots on screen | Twenty-seven chest slots drawn in a chest are a few pixels each. It would be slower than vanilla on the thing players do most |

### 4.1 The bench view

Each scene station has **one view of its own**, fixed to the station: for the crafting table, over its near edge on the
side you came from, 0.81 back from its middle and 1.12 above its top, looking down on the grid at 54 degrees. Whatever
spot you clicked it from, the view glides there. The grid is then the same size and in the same place on screen every
time, so your hands learn it.

- **Trigger:** using the station (not sneaking) within reach. **Glide:** 5 ticks from beside it up to 9 from 4.5 blocks,
  4 if you were here in the last 12 seconds; out in the same time. With reduced motion it cuts (the existing setting).
- **Your body never moves** and your own look is untouched (his rule from 25 Oct: walking you there would take control).
- **Clicks are live on the first frame.** The slot under the pointer is always found by a ray through the view as drawn,
  as now. The inventory strip never moves at all.
- **Still while you aim.** The view's small breath stops while the pointer is over the station.
- **When the place is taken** (a block where the view would sit, or in the way): try 0.25 lower and nearer, then your
  own eyes turned and zoomed as today, then the plain screen. Checked when it opens and every tick, as the furnace does.
- **In third person** the same view is used: the camera comes over your character's shoulder down to the bench, and goes
  back to your third-person view when you leave. (The Inscribing Table already forces first person for its scene.)
- **Invariant:** the view never passes through a block, and the scene never opens somewhere it cannot frame every slot.

### 4.2 The carrying hand

While a stack is on your pointer and the pointer is over the station, **your main hand holds that stack**.

- **The stack is exactly on the pointer.** It is drawn where the pointer's own line meets the air 0.07 over the surface,
  with no lag at all. What you click is what it is over.
- **The hand is under it and the arm lies back across the table toward you**, the way an arm reaches over a table: one
  straight vanilla arm, 0.8 of a square wide, leaning a little to its own side, its far end under the inventory strip
  (the strip is drawn over the arm, so the sleeve end is never in view). It may be drawn up to 1.6 times longer for the
  far row (the same rule as a high fist in §32.N).
- **Life without lag:** the arm leans against the pointer's speed and the stack tips with it; nothing trails behind.
- **Setting down:** the hand dips 0.045 and comes back over 3 ticks while the item drops the last 0.06 onto its square.
  Laying one of many (right click) keeps the rest in the hand. A drag sows one on each square it passes.
- **Picking up:** the item lifts at once (the click is never held); the hand reaches it within 2 ticks from wherever it is.
- **Empty-handed** the hand waits at the near edge under the pointer's column, off the grid.
- **Over the inventory strip** the stack is the screen icon it is today; as the pointer crosses the strip's top edge the
  hand comes up from under the strip with it.
- **Nothing is hidden.** The arm can lie over the squares nearer to you in the same column. Whatever lies on a square
  under the arm shows through on the sleeve as a glass ghost with its outline. The **result moves to the table's right
  border** (vanilla's own side), where a reaching arm never crosses it.
- **Quick hands:** a new target bends the hand from where it is; it never goes back to rest first.
- **Shift-click and number keys** (no stack on the pointer): the item is on its square at once, as now, and the nearest
  hand makes one short pass to it. With more than two changes in 4 ticks the hands stay out of the way.
- **Left-handed** players get the mirror. With reduced motion the hand still carries, without the lean.
- **Invariants:** the hand is decided by your pointer and clicks alone; a server reply only changes what lies on a
  square, as it does on any container screen. The arm never covers the result or the hovered square.

### 4.3 Things with weight

- An item set down drops and settles (2 ticks). A stack lies as a small pile (2 for 2 to 5, 3 up to 32, 4 above), as
  dropped stacks do, with **its count on a small glass tag** at the square's corner.
- The result comes together on its border over 4 ticks instead of appearing. Taken, each ingredient gives a little
  (0.02 down, 2 ticks) and a few flecks of its own texture fall, as the Mallet's chips do (§52.7).
- A furnace's food changes colour toward the cooked item through the last third of its cooking, and the fire on the
  coals stands as high as the fuel that is left. The two thin lines stay, in the band's kit (§57.1).

### 4.4 Sound

Minecraft's own recordings, named in `sounds.json`, played by the client for your own actions and relayed for others.
First picks (his ear decides after it is built, on a local listening page as the armour and weapon passes used):

| Moment | First pick | Why |
|---|---|---|
| Laid on wood | the item frame's `add_item`, low | A small thing set on a board |
| A block laid | that block's own place sound, quiet | It sounds like what it is |
| Metal, a gem | the chain's or amethyst's place sound, quiet | By the item's sort (§56) |
| Lifted | the item frame's `remove_item` | The pair of the first |
| The result comes together | the bundle's `insert` | Soft and whole, no ding |
| Taken | the item pickup pop, lower | Familiar |
| Fuel onto coals | gravel hit, then the fire charge, quiet | Lumps, then the flare |
| Kneeling | a leather equip, low | Cloth moving |
| The lid, the hatch | vanilla's own chest, barrel and shulker sounds | Already right; timed to the hands |

No synthetic tones, nothing above 800 Hz at a peak (`ding_check.py`).

### 4.5 Your body, and what others see

- **Presence.** While a player has a station's menu open the server marks them (`station`: kind, block, the side they
  work from), transient, synced to whoever tracks them, cleared when the menu shuts, on death, on logout, on a new
  dimension. Nothing is saved.
- **The body.** Others, and you on the way in and out of the bench view, see your character at work: bent over a table
  (`station_bench`), down on one knee at a furnace (`station_kneel`), bent to a box with a hand on its lid
  (`station_box`). Full-body clips through `core.api.PlayerClips`, written the way the smith's are
  (`tools/art/specs/anvil_moves.py`). Without the animation library, vanilla's arm swing as today.
- **Beats.** Each click you make on a station slot reaches the server as it always has. The server passes a beat (which
  slot) to players within 32 blocks, at most six a second for one player, and their copy of you reaches to that slot.
- **What lies there.** Others see what is on your table (nine squares and the result), as they already see a furnace's
  three items, and a few of the stacks in a chest whose lid is up (the six largest). Sent on change, dropped on close.
  It shows no more than standing behind someone would.
- If your character stands more than 2.5 blocks from the station, the body reaches toward it and does not pretend to
  touch it (the existing rule).

### 4.6 The furnace: a real crouch

- **The view** goes down the way a body does: a dip and a nod forward over the first 3 ticks (the knees), then down and
  in to the mouth's own view, 1.05 in front of it and 0.10 above its middle, in 8 ticks; a small settle at the end.
  It is the furnace's bench view, the same from any spot in front of it.
- **The body** kneels on one knee (`station_kneel`) and its arm goes to the mouth on each beat.
- **The carrying hand** takes food in on the left and brings the cooked thing out on the right. Fuel is tossed onto the
  coals from just outside the mouth, and the fire answers: a flare, embers of its own (not vanilla's particles), a
  whoosh. A lit furnace lights the skin of your arm (`ArmLayers`, as the powers do).
- From the side or the back, or where the mouth cannot be seen, the plain screen opens, as today.
- Smoker and blast furnace are the same scene with their own mouths, fires and sounds.

### 4.7 Boxes: chest, barrel, ender chest, shulker box, our caches

The glass screen stays, because nothing is faster for twenty-seven slots. What changes is everything around it:
- **The lid is in your hands.** Both hands lift it as it really opens (they follow the lid's own edge each frame), and
  let it down again when you leave. A barrel's hatch and a shulker's shell get their own one-handed and two-handed lifts.
- **You look in.** The view tips 4 degrees toward the open box and narrows a touch while the screen is up.
- **The glass rises out of the box:** a glow travels from the chest's mouth to the panel over 4 ticks. Slots are in
  their final place and clickable from the first frame.
- **You can see what's inside.** With the lid up, the six largest stacks lie in the box, for you and for anyone near.
- **Living slots**, on every glass container screen and the bag: a stack sent by shift-click travels to its slot
  (120 ms, never delaying the click); the stack on the pointer lifts, tips with the pointer's speed and casts a soft
  shadow; a count rolls to its new number.
- In third person the body bends to the box with a hand on the lid.

### 4.8 The other workstations

**Scenes** (slots in the world, the bench view, the carrying hand), each one a short description of its slots:

| Station | Its slots in the world | Its own moment |
|---|---|---|
| Brewing stand | Three bottles on the stand's arms, the ingredient on top, blaze powder at the foot | Bottles colour as they brew; bubbles of its own |
| Anvil | The piece on the face, the material beside it; the name on a glass line under it | Taking the result is three hammer blows with sparks |
| Smithing table | Template, piece and material laid on the top | One blow as it is taken |
| Grindstone | The piece held to the wheel | The wheel turns under the hand; sparks; the levels come back as orbs |
| Stonecutter | The block on the bed; the choices on a glass rail | The blade spins up as you take it |
| Cartography table | The map and the paper on the top | The paper slides under the map |

**Glass and a visible moment** (slots stay on screen, on the band's glass, with living slots, and the touch plays before
the screen as the chest's does): loom, beacon, hopper, dispenser, dropper, crafter, lectern.

### 4.9 The quick touches

- **Seen, not buried:** a touch that leads to a screen shows its first contact before the screen, which is never later
  than 4 ticks.
- **Every touch has a body:** others see you push the door, pull the lever, pluck the flower (six shared clips by what
  the hand does, each at three heights).
- **New touches for what has none:** bell, jukebox, note block, composter, cauldron, flower pot, chiseled bookshelf,
  decorated pot, item frame, armour stand, cake, sign, candle, end portal frame, repeater and comparator, bed; and ours:
  Word Stone, Hearthfire, Fitting Stand, Pattern Stand, trophy mount, crucible, storehouse drop, ledger desk, work
  plan, the bells, pick stone, piton, log terminal, fabricator, broken machine, the Precursor, ghost and Stillpoint doors,
  ghost lever, left chest, carved token, fallen star, well lift, bedroll.
- **Closing has a hand too:** a lid let down, a hatch shut.
- The Forge keeps its three blows for a temper and gets a palm for an offer.

### 4.10 Ours that are already at the bar

The War Anvil, the Inscribing Table, the Forge's song and the Hall of Arms keep their scenes. They take the shared
rules where they don't have them yet: the place-is-taken fallback, the third-person way in and out, presence for others.

### 4.11 Staying quick

- Sneak and use still opens the plain screen. `stationScenes` still turns scenes off.
- No click waits on anything drawn. A test lays nine items in nine ticks and takes the result on the tenth.
- A second use within 12 seconds shortens every move.

### 4.12 When things go wrong

| Case | What happens |
|---|---|
| The bench view's place is inside a block, or something is in the way | Lower and nearer, then your own eyes, then the plain screen |
| A block is placed into the view while you work | The view goes back to your eyes and the screen stays open |
| Both hands full | They are put away as at any station (§24 "Put away") and come back when you leave |
| The server refuses a click | The item goes back, as on any screen. The hand does not replay |
| You are hit, die, fall, change dimension, log out | The scene ends at once and presence is cleared |
| The table is broken while you work | The menu closes as vanilla's does; the view goes home |
| Another mod's screen for the same block, or a model we don't know | The plain screen, as today |
| JEI is loaded | The room is the middle half of the screen, as today; the bench view frames into it |
| No animation library | First person is unchanged; third person shows vanilla's swing |
| A very wide or very small window | The view frames every slot or the scene does not open (test, seven sizes) |

### 4.13 Data and network

Nothing is saved. New transient state: `station` presence (an attachment like `body_stowed`), table contents and open
chest contents for nearby players (the `FurnaceContents` pattern: read-only copies, changes only, 16 blocks), and
beats (a slot number, capped at six a second a player). The server stays the only judge of every item.

### 4.14 Cost while it runs

Two arms and at most fourteen small items a frame. No allocation per tick or per frame in the hands. Presence and
contents only on change. A budget of 0.15 ms a frame for a scene, measured on his Mac at hand-off.

## 5. Shown

The page shows today's stations in real pictures and frame strips, the proposed view in real pictures taken from that
place, and a mock-up of the carrying hand that follows your pointer over a real picture of the table. The mock-up is
drawn in the game's own camera, so positions and sizes are true. It is still a mock-up: the arm's look, the lighting
and the timing in the game will be the game's.

## 6. Building and proving it

**Tests** (GameTests, headless, in the `anims` module unless said):
- `stations_bench_view`: from twenty standing places the framed view is the same; every slot is inside the room at seven
  window sizes, with and without JEI.
- `stations_bench_fallback`: a ceiling, a wall and a placed block each fall back in order, never into a block.
- `stations_hand_never_hides`: for every slot, seven window sizes and both main hands, the arm never covers the result
  or the hovered square, its far end is under the strip or off the frame, and every covered square is ghosted.
- `stations_click_parity`: the existing click tests, run against the new view.
- `stations_fast_hands`: nine clicks in nine ticks all land, and the hand never returns to rest between them.
- `stations_presence`: a second player gets presence, beats and contents, and loses them on close, range and logout.
- `stations_beats_capped`: a flood of clicks sends at most six beats a second.
- `touch_every_block`: every block in §2.2 resolves to a touch.
- The existing `anims_station_*` and `anims_furnace_*` tests stay green.

**Stages** (all built in one run after approval, then tested once; his rule of 10 Nov):

| Stage | What | Hours at AI pace |
|---|---|---|
| 1 | The stage: bench view, carrying hand, weight, counts, the result's move; the crafting table on it | 2 |
| 2 | Furnace, smoker, blast furnace: the crouch, the mouth, the fire | 1.5 |
| 3 | The body and others: presence, clips, beats, contents; third person in and out | 2 |
| 4 | Boxes: the lid in your hands, looking in, contents, living slots | 1.5 |
| 5 | Six workstation scenes | 2.5 |
| 6 | Glass for seven screens; the quick touches, old and new, with bodies | 2 |
| 7 | Sounds, one look tour, the full suite on CI, fixes | 1.5 |
| | | **about 13** |

Scaled from this repo's history: all of Hands P1 to P6, both scenes and fourteen touches included, took two hours on
25 Oct; the Inscribing Table's scene took three on 2 Nov. Add CI queues (20 to 40 minutes a run today).

**Rollback:** `stationScenes` off gives today's plain screens. A new client setting, `stationView` (bench or eyes), keeps
today's framing one switch away until he has played the new one.

**What could change the size:** the six workstation scenes are the least certain (each has one flourish that needs a
look); third-person clips need a film to judge.

## 7. Decisions for Euan

1. **The carrying hand as shown:** 0.8 of a square wide, the arm back across the table, a ghost of anything under the
   sleeve, the result on the right border. *Recommended: yes.*
2. **One view per station from any distance; your body stays where it is.** *Recommended: yes.*
3. **In third person, the scene still takes the bench view, and your character does the work for others to see.**
   *Recommended: yes.*
4. **A real crouch at the furnace.** *Recommended: yes.*
5. **Chests keep the fast glass screen and gain the lid, the look in, visible contents and living slots.** *Recommended: yes.*
6. **Six more workstations become scenes; seven screens go onto glass.** *Recommended: all of them.*
7. **Others can see what is on your table and in your open chest.** *Recommended: yes.*
8. **Sounds from Minecraft's recordings, first picks as listed, marked by ear after the build.** *Recommended: yes.*
9. **Doors that swing with your hand** would need a renderer mixin under the Turbo contract. *Recommended: not now.*
10. **Build all seven stages in one run, then test.** *Recommended: yes.*

## 8. As built (10 to 11 Nov 2026)

All seven stages were coded in one run and are on the branch; SPEC §65 describes each part. This section is the honest
ledger against §4.

**Built as planned:** the bench view from any standing place and from third person (§4.1); the carrying hand on the
pointer with a world arm lying back under the strip (§4.2); piles, counts on glass, the settle, the result coming
together on the right border, the give and the flecks as it is taken, counts that roll (§4.3); sounds from Minecraft's
own events, named in `sounds.json` (§4.4); presence, bodies at a station and what lies there for others (§4.5); the
furnace's crouch, the hand going in, the fire's answer (§4.6); the lid in your hands, looking in, the glass rising,
what is inside, living slots (§4.7); six workstation scenes with the anvil's name line and cost, the stonecutter's
rail, blows and sparks as work is taken, bubbles in brewing bottles (§4.8); every quick touch named once for both
sides, new touches, a body clip for each (§4.9).

**Different from the plan, and why:**
- The pointer's stack tips with its speed only in a station scene. On a chest's screen the icon is vanilla's own and
  cannot be turned without replacing how every container screen draws it.
- A furnace's food draws in and trembles through the last third of its cooking; it does not change colour. An item's
  model cannot be tinted without a second model for every food.
- Hopper, dispenser, dropper, crafter and beacon are on glass. **The loom and the lectern keep vanilla's screens**: a
  loom's screen is its pattern list and banner as much as its slots, a lectern shows a book.
- A grindstone's wheel does not turn and a stonecutter's blade does not spin up (both are vanilla's block models); the
  cartography table's paper does not slide under the map. The blow, the sparks and vanilla's own sounds are there.
- The hint over the strip now leaves after 3.5 s (it sat over the table's near edge in the new view).
- No local listening page yet: the sounds are vanilla events with a volume and a pitch, heard in the game.

**Tests.** `anims_`, `stations_`, `touch_` and `audio_`: 32 of 32. The names differ a little from §6: click parity is the
old `anims_station_*` tests run against the new view (they pass); fast hands and the beat cap are inside
`stations_presence` and the tour's fast-crafter clip; the fallback order is in `stations_bench_view` and
`stations_mouth_view`.

**What the look tours showed** (`tools/autopilot/hands-at-work.txt`, on GitHub's runner):
- Run 42879591037 (first): opening a brewing stand **crashed the client** (a kind with no result slot indexed slot -1).
  Fixed. Its 36 shots before the crash showed the bench view, the arm, piles, counts, the result on the border, the
  crouch, embers and the workstations all drawing, and three faults: the table's view turned a few degrees by its
  result, the workstations framed so close that three slots filled the screen, and the game's own hand in the corner
  before a furnace. All fixed (SPEC §65.1, §65.6) and guarded by tests (square on; the whole top in the room).
- Run 42883680457 (second, all 69 shots): the fixes hold (square views, whole workstations, no stray hand, the hint
  gone); the glass screens, the lever and bell, and the body clips (a lunge before a furnace, a bend to a chest, arms
  to a table) all draw. It showed three more faults, fixed: the stacks that should lie in an open chest were drawn
  inside its solid base and never seen; at a brewing stand the hand never met the carried stack and two resting arms
  lay across the frame; an empty main hand waited under the strip's glass as a dark shape. It could not show the lid
  in your hands (the script held a block; the lift needs both hands free) and its film is a frame every ten ticks,
  too slow to judge a five-tick glide or a seven-tick reach.
  It also showed the game's own hand still in the corner before a furnace: the first fix set a flag nothing read on
  each side (`WorldBody.armShown`). Fixed.
- `tools/autopilot/hands-at-work-b.txt` films those scenes again (chest, brewing stand, furnace, table at rest). Its
  first two runs were void (the script built on ground that was not loaded, then used a seed whose teleport landed
  late). Run 42901587111 confirmed three: at the brewing stand the hand carries the bottle and the wart and nothing
  rests in the frame; before a furnace the game's own hand is gone; at the table both empty hands rest off their
  corners. It showed one more fault, fixed: the carried stack was drawn a frame behind the hand that holds it (seen on
  a machine that draws two frames a second). The chest was not filmed in it (the script looked at the floor).
- Run 42906598736 filmed the chest: both arms take the lid up as the screen opens and bring it down as it shuts, the
  largest stacks lie in the chest's mouth and show through the screen's glass, and before the furnace the carried
  stack is at the fist. At 1.4 blocks the two arms fill much of the frame for the half second they hold the lid: a
  thing for Euan's eye.

**Still unseen by any run:** motion (the runner draws about two frames a second). **Section number:** this plan was
SPEC §63 while it was built; it became §64 on 11 Nov when The Way Up took 63 in build, and §65 at the merge, when The Long World took 64.

## 9. After Euan played it (11 Nov 2026)

His words: "the hands are like really glitchy in most cinematics, where they're like trembling hands, and you can see
the edge of them as well ... when you open the chest, it like opens the hands way too high and for way too long ...
when you close the chest and you look away fast, like the hands glitch all over the place", and, of the first fix,
"Arms are way too stretched and don't look like the actual arms anymore". SPEC §65.2, §65.7 and §65.9 are rewritten;
each rule there names what the played build did. The causes, as found:

| What he saw | Cause | Now |
|---|---|---|
| Trembling hands in every station scene | Camera Overhaul sways the view idly whenever you stand still, and a scene is standing still: the table slid under a stack and a hand pinned to the pointer (found by the pose log: eye fixed, look wandering a third of a degree) | A scene holds the view; the Feel Director pauses Camera Overhaul for it (`FirstPerson.viewHeld`) |
| | The arm leaned by the pointer's speed (noise from a real mouse), its length moved in twentieths, the stack rolled with that speed | The arm is a pure function of where the stack is held; the stack lies steady |
| | Vanilla's hand sway was in the hand pass under every world arm | Undone for world arms |
| Their edges and backs in view | Both hands rested at the near corners; the arm's far end showed where the strip was far (the room beside JEI's panels) | No hand at rest; the far end always under the strip, which is drawn nearly solid |
| Stretched, not real arms | A small fist drawn up to 1.8 times its length | The vanilla arm at its own size, never longer than it is |
| Chest: too high, too long | Both hands rode the lid to the top for 13 ticks, the lid read with a stale partial tick | One hand to the lip where it lies shut, a lift of 0.09, 7 ticks |
| Chest shut, look away: hands everywhere | A hand let the lid down for 11 ticks and followed it as the view turned | Shutting plays no hand; any touch sends its hands home in 120 ms once its thing leaves the middle of the view |

**Proof.** `tools/autopilot/hands-smooth.txt` with the pose log on, read by `tools/anims/handslog.py`. Run 43012365190,
1,106 frames: in every stretch with the pointer left on a slot (table, anvil and furnace, his layout and the wide one,
up to 166 frames over 4 s) the fist, the arm's line, the eye and the look each moved 0.00000; in the sweeps the arm's
line turned at most one degree in a frame and never went back on itself; a chest's hand was in the view under 0.4 s
and never above the middle of the frame. Tests `stations_hand_never_hides` and `stations_hand_steady` cover every scene
and both layouts. **Not judged:** how it feels under a real mouse, which only he can say.

**Left for his eye:** a chest's lid is now one hand, mostly behind the screen that opens at once; shutting a box plays
no hand; the brewing stand has no arm at his GUI size (its view is too wide for an arm to end under the strip).

