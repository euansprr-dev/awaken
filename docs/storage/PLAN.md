# A Place for Everything: sorting, chests and the room you build for your things

**Approved by Euan, 9 Nov 2026: "Go with all recommendations".** All eleven decisions in §12 as recommended, so there is no automatic
"keep my bag tidy". Built in stages; SPEC §56 records what shipped.

Written 9 Nov 2026 on `claude/storage-plan-f52faa`, following
`.claude/PLANNING-PROCESS.md` and `.claude/UI-EXCELLENCE.md`. The approval page has the mock-ups and the clickable prototype:
https://claude.ai/artifact/HJM74AUhQoZwx4TUP7BZFo (private to Euan).

Labels used below: **Measured** was run in a throwaway GameTest on this Mac and then removed. **Read** comes from reading the
code; it has not been run. **Source** is outside research. **Proposed** is new and unproven.

---

## 1. What you asked for, and how we'll know it worked

**The job.** You come home with a bag full of mixed things. You want it put away without thinking, and you want to find it
again. Later in the game you want one room, built by you in whatever shape and place you like, where the putting-away happens
by itself.

**What fails today.** There's no sort button anywhere. A chest is vanilla's screen and vanilla's mess. The one system that does
sort (the Storehouse) is gated behind a keystone from a city granary and a tamed Totter, nothing in the game ever points you at
it, and when you get there it files most of what you own under "Odd things".

**What should happen.**
- One click aligns your bag or any chest, the same way every time, so your eye learns where things go.
- One click sends what a chest already holds into that chest.
- Your hotbar and anything you choose stay put.
- Once you've got a few chests lying about, the Hearth asks you to build a room for them. Any shape, anywhere on your land, even
  down in the rock. You set a Drop in it and tip everything in. It gets filed: at first by your own hand, then by Totters.
- The Ledger shows your own room, what's in it and where, and a Totter brings it to you.

**Your ideas, treated as ideas.** "Build our own, no external mod" stands. Hearth guidance stands. "An automatic system you can
build" I've taken to mean automation of sorting and storing only. The player still builds the room, chooses the look and brings
the things, which keeps to your 9 Nov rule on the chill side.

**Hard constraints.** Mouse Tweaks stays and keeps working. JEI's overlay stays clear. No power key is touched. Server decides
everything. Item counts can never change. Player-facing words are ordinary speech. Sounds come from Minecraft's own recordings by
name, with no synthetic dings. Tasks surfacing belongs to the `tasks-progression` planner; this plan only names the steps.

**What good, weak and unacceptable look like.**

| | Good | Weak | Unacceptable |
|---|---|---|---|
| Tidy | Same item always lands in the same part of the bag; sorts start on their own rows when there's room; you can undo it | Sorted, but the order feels random, or a sort spreads over two screens | An item is lost, duplicated, or leaves the hotbar |
| Stow | Everything the chest holds goes in; what can't fit stays with you and says so | Works but silently leaves things | Sends items to a chest you can't open or don't own |
| The room | A hand-dug cave with a Drop works first time | Needs a specific shape or a rare material you've never heard of | Reads the room wrong with no explanation |
| Filing | A heap clears and you can see where it went | Slow, or the Totter stands about | Items get stuck in a Totter's arms forever (this happens today, see §2) |
| Finding | Search, see it on your own floor plan, it's brought to you | A text list | The Ledger names a chest that doesn't hold it |

**Outcome contract.**

| | Today (Measured or Read) | Target |
|---|---|---|
| Usefulness | No sorting before the Storehouse | Tidy and Stow from minute one |
| Correctness | 63% of real items file as "Odd things" (Measured) | Under 10% of obtainable items; every item on a hand-checked list of the 300 most common is right |
| Correctness | A Tidy or Stow can't lose anything | Conservation check runs on every operation and in a randomised test |
| Effort | Tidy a chest by hand: about 25 clicks for a normal chest | 1 click |
| Responsiveness | n/a | Slots show their final contents within one network round trip; the flight animation is 180 to 260 ms and never blocks input |
| Control | Nothing automatic | Automatic only where you asked for it: the Drop's room, and optionally refilling the hotbar |
| Reliability | A Totter strands itself on a bay it can't reach (Measured) | A Totter that can't finish gives up, says why, and goes back to work |
| Cost | n/a | No per-tick allocation; a Tidy under 1 ms server time on 54 slots (to be measured at build) |

**Non-goals.** No storage tiers, cables, drives or settings pages. No auto-crafting. No teleporting your items from anywhere. No
change to how you click in slots. Furniture art belongs to Made by Hand.

---

## 2. What's there today

**Map of the real system.**

| Piece | What it actually does | Evidence | Consequence | Verdict |
|---|---|---|---|---|
| Bag screen | Holo glass over `InventoryScreen`, survival only | `HoloInventoryScreen`, `InventoryLook` | Handsome, no sort | Extend |
| Chest screens | Stay vanilla; holo only replaces `InventoryScreen` | `HoloClient.onScreenOpening` | Your bag is glass, then a chest is a grey box | Extend (decision 3) |
| `Bay.of` | Name matching, then a few tags | `Storehouse.java:64` | 63% to Odd; armour called "bellguard" files under Bells | Replace |
| `bayOf` | First item decides the chest's sort | `Storehouse.java:157` | One stray stone makes a chest of logs a stone bay (Measured) | Replace with majority |
| `bayFor` | First chest of that sort with room, nearest the middle | `Storehouse.java:172` | Birch logs ignore the chest that already holds birch logs (Measured) | Extend: exact item first |
| `WorkRooms.containers` | Scans the room's box; only chest, trapped chest, barrel | `WorkRooms.java:51` | Double chest counts as two rival bays; copper chests ignored (both Measured); a neighbouring room inside an L-shaped room's box is counted (Read) | Extend |
| Drop | Tip by hand, sneak to empty bag; heap drawn as real items | `DropBlock`, `DropRenderer` | The best thing here. Keep it exactly | Keep |
| Recipe gate | Needs a granary keystone | `storehouse_drop.json` | You must visit a far city before storing anything | Change (decision 5) |
| Room reading | Hand-built rooms read the same as plans; underground counts | `RoomScanner`, Measured | A dug room works within 32 below the fire. At 47 below it isn't read (Measured) | Extend (decision 6) |
| Totters | Carry 3 stacks; walk to within √7 of the bay | `KinWorks.near`, `carryAway` | A barrel 4 up the wall strands the Totter forever (Measured, 60 s, still carrying) | Fix |
| Ledger | 300×230 text list, search, Fetch, Show me | `LedgerScreen` | Works, looks like a form, doesn't show the room | Replace the screen, keep the logic |
| Feedback | Grey italic lines in chat | `Storehouse.say` | Lost in chat, reads like debug | Replace with the game's cards |
| Guidance | None. `HearthGoals` has no Storehouse step | Read | You'd never find it | Add |
| Promised in SPEC, unbuilt | "Marked items", shelves that show contents, racks that grow, C rank gate, home crafting from the whole hall | SPEC §43.6, §43.12 vs code | Docs claim more than exists | Build what's useful, correct the SPEC |

**Measured** (throwaway GameTest, 3 tests passed, then removed; no game code changed):

- Every registered item through today's sorter, 2,672 excluding spawn eggs: **Odd things 1,688 (63%)**, Gear 383, Gate loot 184,
  Wood 127, Plants 80, Metal 64, Food 54, Stone 34, Bells 26, Drops 25, Gems 7.
- In "Odd things" today: coal, charcoal, redstone, flint, arrows, torches, lanterns, glass, doors, ladders, buckets, paper, books,
  smooth stone, stone slabs, cobblestone stairs, concrete, terracotta, obsidian, potions, chests, furnaces, mushrooms, moss.
- Misfiled under Bells by the name check: `bellguard_helmet` and the rest of that armour, `bell_warden_maul`, `ember_bellows`.
- Hall 27 below the Anchor, natural stone ceiling: read as `[SHELTER, STOREHOUSE]`. Same hall 47 below: not read at all.
- Double chest, stone in one half and logs in the other: two separate bays, Stone and Wood.
- Chest with 1 cobblestone in the first slot and 26 stacks of logs: a Stone bay.
- Birch log with a nearer chest of oak planks and a farther chest of birch logs: goes to the planks.
- Copper chest in the room: not counted.
- A settled Totter whose only bay is a barrel 4 above the floor: after 1,200 ticks it was standing under the barrel, still
  carrying 32 cobblestone, barrel empty.

What those tests did **not** show: real Tectonic caves, how a Totter gets to a deep room, how any of this feels in hand. They
also ran on the plain test world, with no other mods.

**Reproductions.** Each case traced from input to what you get.

1. *The disappointing case (Euan's).* Home with a full bag. No button. You drag about 25 stacks by hand into chests that are
   themselves mixed. Nothing has happened to help by the time you close the screen.
2. *A strong existing path.* Drop in a planned Storehouse with a Totter, a heap of cobble, logs and ore. It clears and signs
   appear. This works and is charming. It's the part to protect.
3. *Ordinary.* A chest with 15 mixed stacks: only Shift-click and Mouse Tweaks' drag help, both one slot at a time.
4. *Ambiguous.* A hand-dug L-shaped hall next to a pantry: the pantry's chests count as bays (Read), so food gets carried off
   into the wrong room.
5. *Failure.* A hall with a chest up high: the Totter stalls silently and the heap never goes down (Measured).

**Root causes.**

| Symptom | Cause | Evidence | Test for the fix |
|---|---|---|---|
| Nothing's organised early | No sort exists anywhere before the Storehouse | Code | Tidy GameTests |
| Odd things swallows everything | The sorter names 11 sorts for narrow families and has no fallback by block or material | 63% measured | Golden list and full dump |
| Halls file wrongly | First-item bay, no exact-item preference, halves counted apart, box not cells | Measured | `works_*` tests |
| Filing stops | Totter has no timeout and only one way of reaching a bay | Measured | `totter_high_bay` |
| Nobody builds one | Keystone gate, no step, no ask | Read | Tasks steps, Hearth ask test |
| It doesn't feel finished | Chat lines, a text list, a grey chest | Read | Visual review, Euan playing |

A cause I considered and dropped: "the Totter is too slow." Your SPEC figures (a bag a minute with one) are fine, and nothing
I found says speed is what's wrong.

---

## 3. What other games teach (research)

| Claim | Source | Applies here | Limit | What it changes |
|---|---|---|---|---|
| Quick-stack only sends items to chests that already hold them, in a short range, and favourited items are skipped | Terrastorage: modrinth.com/mod/terrastorage; Quick Stack: curseforge.com/minecraft/mc-mods/quick-stack-to-nearby-chests; Stack to Nearby Chests: modpackindex.com/mod/39824 (read Nov 2026) | Direct model for Stow | Mod descriptions; I didn't find an official Terraria page | Stow only goes where the item already lives; 8 block reach matches our Home Crafting |
| A sort that handles tools and weapons oddly generates complaints | Stardew forums: forums.stardewvalley.net/threads/.5155; Steam: discussions/0/351659808489831849 | Tidy's order must make sense for gear too | Player reports, not a study | Gear has its own place and a sub-order |
| Mouse Tweaks only touches slot clicks and drags in container screens, client side, and has an API to ignore slots | modrinth.com/mod/mouse-tweaks; feed-the-mods.com/mouse-tweaks-mod | Buttons in the title row can't collide; Alt isn't one of its modifiers | Description, not tested with our build | Live check with Mouse Tweaks at Stage 1 is a gate |
| JEI uses overlay columns at the sides, and item-hover keys | craftdownunder.co/guides/mods/jei | Keep buttons inside the panel | Exact default keys unconfirmed | Check the Controls list at build; our keys are GUI-only and rebindable |
| Copper golems stick when no destination exists | mojira.dev/MC-306429; nodecraft.com support page on copper golems | Same failure as our Totter | Vanilla bug report | Every carrier needs a give-up rule |

Vanilla facts from `.ref/src`: `ChestBlock.getContainer` joins halves; `AbstractContainerMenu` has `suppressRemoteUpdates` and
`broadcastFullState`; `ServerGamePacketListenerImpl.handleContainerClick` shows the pattern to copy (container id, `stillValid`,
state id, resync on mismatch).

---

## 4. Directions

| | A. Fix what's there | B. Three rungs, one set of sorts (recommended) | C. One inventory from anywhere |
|---|---|---|---|
| How it works | Tidy buttons on the vanilla chest screen, repair the sorter, add a Hearth line | Tidy, Stow and Pins everywhere; the Drop works in any room you build; you file by hand until a Totter moves in; the Ledger shows your own room | All chests on your land become one searchable grid, from any terminal, no carrying |
| Quality | Fixes the worst of it; chests stay grey | Every stage of the game gets a real step up | Fastest to retrieve |
| Friction | Low | Low; one more thing to build, and the Hearth asks for it | Lowest |
| Fits AWAKEN | Partly | Kin do the boring work, you build, Hearth asks | Against it: no kin, no room, no reason to go home |
| Cost and risk | Small | Medium, in stages, each stops working cleanly | Large; a new storage model |
| What would change my mind | If you only ever wanted buttons | | If you play it and walking to a room annoys you more than it delights |

**Recommendation: B.** A loses the room you described. C loses the Totters and makes the Hearth irrelevant, and your 9 Nov note
on the chill side warns against exactly that kind of system. Direction B takes A's repairs as its first stage.

**Things you didn't ask for that earn their place.**
- **Rows that line up.** Each sort starts on a new row when there's room. It's the most direct answer to "aligns things", and I
  can't find it in the sorting tools I looked at.
- **A chest takes the hint.** You design your hall by putting one loaf in a chest. The Totters follow. No menus.
- **Totters toss.** They're small, so a high shelf gets an armful thrown up in an arc. It fixes the stall and looks good.
- **The Drop accepts hoppers, and a Drop above another Drop drops into it.** You can run a chute from your front door to a hall
  in the rock, or a farm into the Drop. Literal, physical, and nothing new to learn. That's the "automatic system you can build".

---

## 5. The design

**In one sentence: everything you pick up has a place, and the game knows where it is.**

### The sorts (one list, used everywhere)

The bag, chests, the Drop's sounds, the bays, the Ledger's tabs and the signs all use the same list, in this order.

| Sort | Takes | How it's decided |
|---|---|---|
| Stone and earth | stone families, slabs, stairs, walls, dirt, sand, gravel, clay, flint | tag `awaken:sort/stone`, block tags |
| Wood | logs, planks, sticks, wooden doors, slabs, stairs, fences, signs | tags |
| Building and light | glass, wool, concrete, terracotta, carpets, torches, lanterns, ladders, beds, chests, workstations, rails, redstone parts | tag, then "any other placeable block" |
| Ore and metal | ores, raw metal, ingots, nuggets, coal, charcoal, redstone dust | tags |
| Gems | diamonds, emeralds, lapis, amethyst, quartz, gate crystals | tags |
| Food | anything edible | food component |
| Plants and seed | seeds, crops, saplings, flowers, leaves, mushrooms, vines, moss | tags |
| Beast and monster drops | bones, string, leather, feathers, slime, pearls, gunpowder, blaze rods, eggs | tags |
| Gate loot | what bosses and gates give | tag `awaken:sort/gate`, generated from the loot tables and checked |
| Gear | tools, weapons, armour, shields, arrows, buckets | damageable, equippable, tag |
| Books and brews | potions, enchanted books, books, paper, bottles, maps | tag |
| Bells and leads | real bells, clappers, letters | tag only; no more name matching |
| Odd things | what's left | fallback |

Two more than SPEC's eleven: everyday building things had nowhere to go and neither did potions and books.
Within a sort: sub-family, then name, then biggest stack, then least worn.

Resolved once into a `Map<Item, Sort>` when tags load, so a lookup is one hash read. New items default sensibly through the
"any placeable block" and component rules, and `journey_ledger` style test flags anything new that lands in Odd.

### Rung 1, from minute one

**Tidy.**
Trigger: the Tidy button in the panel, or the Tidy key (default T, only while a screen is open). It tidies the section the
button belongs to; the key tidies whichever section the cursor is over.
Inputs: the open menu, container id, state id, which section.
Processing, on the server: read the section's slots, skip the hotbar and pinned slots, merge stacks of identical items and
components, sort, lay out with row breaks, check that counts per item and component match exactly and that every target slot
accepts its stack, then write through the slots. If any check fails, nothing changes.
Row breaks: first try every sort on its own row. If that doesn't fit, a sort moves to a new row only when it doesn't fit what's
left of the current row, is at most one row long, and the remaining stacks still fit. Otherwise it continues where the last
sort ended. Same input, same output, and a second Tidy changes nothing.
Response: the server sends where each slot went; the client flies ghost items over the real, already-moved slots for 180 to
260 ms. Clicking during the flight works and skips the animation.
Recovery: a line under the panel says what happened and carries an Undo button, until anything in that section changes. Undo
refuses if the contents differ from what Tidy left. It isn't saved.

**Stow.** In a chest: put away what this chest already holds. In your bag: the same for chests around you, within 8 blocks, on
land you may use, chests you could open by hand. Inside a Storehouse the reach is the whole room. Pinned slots and the hotbar
stay. What can't fit stays with you, shakes, and a short line says how much. Stow never starts a new kind of thing in a chest.

**Pins.** A pinned bag slot stays where it is and is left alone by Tidy, Stow and the Drop's "Empty my bag". You can still move
it by hand, and Mouse Tweaks is unaffected. Alt and click (Option on the Mac), or the Pin button for a mode where any click
pins. Saved on the player.

**The rule, said the same way everywhere:** "Your hotbar and what you're wearing stay with you."

**Refill (decision 4).** When the stack in your hand runs out, the next stack of the same item from your bag moves in. Off by
default for tools that break, so a broken pick is something you notice.

**Chest screens (decision 3).** Chests, barrels, shulkers and copper chests get the bag's holo glass, wells and hairlines, with
the same slot positions as vanilla so nothing moves under your hand. Turning holo off leaves vanilla's look with the same buttons.

**Where it's offered.** Only on menus we've checked: `ChestMenu`, `ShulkerBoxMenu`, `DispenserMenu`, `HopperMenu`, the bag, and
our own. Other mods' screens stay as they are.

### Rung 2, once you've got a home

**The Hearth asks.** When the home's rooms hold six or more chests and barrels and there's no Storehouse, the Hearth says so in
plain speech:

> You've got chests all over the place. Build one room for them, any shape you like, even down in the rock, and set a Drop in
> it. Whatever you tip in there gets put away.

**One Drop, no tiers.** A closed room with a Drop in it is a Storehouse. The Ledger desk adds search and Fetch; it no longer
decides whether the room counts. The Drop's recipe loses the keystone and takes iron instead; the keystone moves to the Ledger,
so the granary trip still means something (decision 5).

**Put it away.** Tip things in as today. Before a Totter moves in, hold use on the Drop with an empty hand for a second: the heap
flies to the bays with each sort's sound, signs go up, and a card says what happened. Up to 24 flights are drawn, however big
the heap.

**How filing chooses a bay.** A chest already holding that exact item; else a chest whose majority is that sort, with room; else
an empty chest, which becomes that sort. If nothing has room, the things stay on the heap and the Drop says what it needs.

**Design it how you want.** Put one loaf in a chest and that chest is the food chest. A chest you never want touched gets "Leave
this one alone" in its screen, shown only inside a Storehouse. Take a sign down and it stays down.

**Deep rooms (decision 6).** A room with a Drop in it is read at any depth under your land, from the Drop outward, with no extra
scanning. If the land limit still applies somewhere, the Drop says so plainly when it's placed. Your land's protection in
multiplayer still ends 32 below the fire, which is a known gap.

**Chute and hoppers (decision 7).** The Drop accepts items from hoppers. A Drop with another Drop straight below and only air
between hands everything down, with a falling sound.

### Rung 3, with kin

A Totter does the filing by itself, an armful at a time, day and night rounds as today. Three changes:
- A shelf out of reach gets an armful tossed up in an arc, up to 6 blocks.
- A Totter that can't finish for 15 seconds gives up, puts the armful back on the heap, leaves that bay alone until it changes,
  and the Hearth page says which bay.
- Each bay gets Tidied when something lands in it, so your hall stays lined up.

**The Ledger, remade.** Full screen. Items by sort in a grid with counts (the heap and a Totter's arms count too, so nothing
seems to vanish mid-carry), search at the top. On the right, the plan of your own
room drawn from the Hearth's real reading of it, chests coloured by sort, where you stand, where the Totters are. Click an item
and its chests light on the plan. Fetch sends a Totter; Show me marks the chest in the world for 8 seconds. Crafting in the room
reaches the whole hall.

### Steps for the Tasks page (`tasks-progression` owns how they show)

All under `Goal.HEARTH`, in the form `claude/tasks-progression-plan-df225b` proposes (one row per thread, three voices). Euan hasn't
answered that plan yet; if it changes, these change with it. Told to that planner 9 Nov.

| Step | Thread, order | Voice | Opens when | Needs | Teaches, opens line |
|---|---|---|---|---|---|
| `storage/room` "Build a room for your things" | `hearth.build`, 35 | Plain | The home's rooms hold 6 or more chests and barrels, and there's no Storehouse. No rank gate | One Drop, and a closed room with the Drop in it, any shape, any depth under your land | `room:storehouse`, `block:awaken:storehouse_drop`. "Tip things in and they get put away" |
| The existing `KinGoals` Totter row | `hearth.beasts` | Lead: "They live on woods' floors" | A Storehouse stands | A Totter at home | No new step; moved up once a Storehouse stands |
| `storage/ledger` "Set up a Ledger desk" | `hearth.build`, 36 | Lead: "Every town's granary keeps one against a ground-floor wall" | A Storehouse stands | A Ledger desk in that room, which takes a keystone | `block:awaken:ledger_desk`. "Look anything up, and send a Totter for it" |

Orders 35 and 36, as the Tasks planner told us 9 Nov: a thread lists only its lowest order and "Add to your home" (`hearth/add`, 40)
stays while any pick is left, so anything at 40 or later would never show. Those two take the row, and Add waits in the Later fold until they're done.

The Storehouse is already a pick in `HearthGoals.OPTIONS`, told at C-rank, with a Granary Keystone as its detail and a Need of "A Totter".
Stage 3 changes that detail and Need to the iron Drop (decision 5, yes) and drops the pick while `storage/room` is open, so it isn't
offered twice. The Totter row needs no change: `KinGoals` already puts a beast first when a standing room waits on it. `Goals.Builder`'s
`.thread`, `.lead`, `.opens` and `.teaches` are on `claude/tasks-progression-plan-df225b` (52364744b), not yet in build, so Stage 3 needs that merged first.

"Build a room" and "Set a Drop in it" were two steps; they're one, because anyone with chests already has a closed room, so a
room-only step would tick itself. Tidy, Stow and Pins are buttons on screens you already open, so they need no step.

### What Made by Hand owns

How furniture looks and is made, including crates, casks and goods shelves. This plan owns what counts as a bay and what Totters
do with it. When Made by Hand's Stock stage ships those as real containers, they join the bay tags and that's all.

---

## 6. Data, life of the data, other players

| Thing | Where | Saved | Notes |
|---|---|---|---|
| Sort of an item | Tags plus `Map<Item,Sort>` | Derived, rebuilt when tags load | Never saved; changing the list can't corrupt anything |
| Pins | Player attachment `awaken:bag_pins` `{schemaVersion, long mask}` | Yes; copied on death | Synced to its owner |
| Undo | Server memory, per player, one step | No | Dropped on close or when contents change |
| "Leave alone" | Block-entity attachment `{schemaVersion, boolean}` | Yes | Chests only |
| Bad bay memory | In the room's runtime state | No | Rebuilt by trying again |
| Bay contents | The chests | Vanilla | The Ledger reads them live; no second copy |
| Floor plan | `Room.cells`, sent on open | Derived | At most 48×48 columns |

Existing worlds need no migration. A Storehouse keeps working; its chests adopt the new rules the next time they're touched.

**Other players.** Tidy and Stow apply to a chest you've got open, as any click would. Stow nearby and the Drop check you could
open or use it, against Hearth membership. Two people in one chest: the server orders the operations, and a client whose state is
stale gets a full resync and no animation. Undo can only be used by the player who tidied, and only if the chest is unchanged.

**No AI in this feature.** Everything is rules.

---

## 7. What can go wrong

| Case | Detect | Contain | You see | Test |
|---|---|---|---|---|
| Any item lost or duplicated by Tidy or Stow | Per-item, per-component count before and after | Abort before writing | Nothing changes; a quiet line; logged | `tidy_conserves` with 2,000 random fills |
| Stale client state | State id mismatch | Refuse, full resync | Slots correct, no animation | `tidy_stale_state` |
| Hopper changes a chest mid-op | Server is single-threaded | n/a | n/a | n/a |
| Menu is not one we support | Exact class check | Buttons hidden; server refuses | No buttons | `tidy_refuses_other_menus` |
| Slot refuses the stack (shulker into shulker) | `mayPlace` check | Abort | Quiet line | `tidy_shulker` |
| Double chest, broken half mid-screen | `stillValid` | Vanilla closes it | Screen closes | Vanilla |
| Full chest on Tidy | n/a | Merges and sorts only | Works | `tidy_full` |
| Full chest on Stow | Remainder | Keeps it | Shake and "Three stacks didn't fit" | `stow_full` |
| Item on your cursor | Carried is out of scope | Ignored | Normal | `tidy_with_carried` |
| Button spam | 4 per second per player | Extra requests ignored | Nothing | `tidy_rate_limit` |
| Stow nearby with a locked chest | `canOpen` and Hearth check | Skipped | Goes elsewhere or stays | `stow_respects_locks` |
| Heap bigger than the bays | `bayFor` null | Stays on heap | "The hall needs another chest" | `works_hall_full` |
| Totter can't reach | 15 s without progress | Puts it back, skips that bay | Line on the Hearth page | `totter_high_bay`, `totter_gives_up` |
| Totter dies carrying | Vanilla drops | Items drop | Normal | `totter_death_drops` |
| Sneaky deletion: chest broken mid-carry | Container null | Back to heap | Normal | `works_bay_gone` |
| Ledger is stale after the room changed | Read live on open | n/a | Current | n/a |
| Fetch when nobody's free or it's night | Existing fallback | Set out on the desk | Plain card | Exists |
| Mouse Tweaks or JEI collision | Live check | Rebind or move | n/a | Stage 1 gate |
| Other mod inserts into the Drop past the cap | `add` returns remainder | Refuses | Hopper waits | `drop_hopper` |
| Chute with a block in the way | Air check | Stays in the top Drop | Top Drop says the shaft's blocked | `drop_chute` |
| A sort tag is missing or malformed | Fallback to rules | Odd | Log | `sort_golden` |

---

## 8. Screen states

| State | Bag | Chest | Drop | Ledger |
|---|---|---|---|---|
| Empty | Tidy dim | Tidy and Stow dim | "Nothing here yet. Tip something in." | "The shelves are bare. Tip things into the Drop." |
| Typical | Normal | Normal | Heap, count | Grid and plan |
| Dense | 27 stacks: no row breaks needed to look right | 54 slots: panel fits at GUI 4 at 1708×960 | Heap draws at most 24 | Scrolls; 2,048 rows max |
| Already tidy | Tidy does nothing; a brief check mark | Same | n/a | n/a |
| Working | Flight 180–260 ms | Same | Flights | Fetch: "A Totter's on its way" |
| Partly done | n/a | "Three stacks didn't fit" | Remainder stays | n/a |
| Refused | Quiet line | Quiet line | Plain reason | Plain reason |
| No search match | n/a | n/a | n/a | "Nothing like that is here." Distinct from empty |
| Offline/server | Client sends; server decides | Same | Same | Same |

---

## 9. Interaction and craft

**Layout.** Bag panel unchanged, 176×166 units. Built: the keys sit in the corner to the right of the recipe book's button (which
stands at x 104 to 124, y 61 to 79, where the prototype had drawn Tidy), in two rows 10 tall: Tidy x 126 (23 wide) and Pin x 151 (19)
at y 58, Stow x 126 (25) at y 70. Those widths fit the words in the game's own font with room each side.
Chest panel: same slots as vanilla; Tidy (x 119, 23 wide) and Stow (x 144, 25) in the chest's title row at y 3; Tidy and Pin in
the same columns in the bag's label row, 72 to 83 on a 3-row chest. Each sits by what it affects. Recipe book and JEI push the
panel; buttons are positioned from it. The result line and Undo sit 6 units under the panel, and on a 6-row chest (which leaves
only 9 units below at GUI 4 on 1708×960) directly under it, as a plate over the frame's corners.

**Type.** Game font at GUI scale, labels in `Glass.LABEL`, buttons in `Glass.button`. Sentence case. Labels are single common
words. Tooltip names exactly what will happen, for example "Put away what this chest already holds".

**Colour and material.** Reuse `Glass`: `GLASS`, `EDGE` hairlines, wells `0x040A16` at .58. Sort colours appear only on the Ledger's
plan and tabs, always with the sort's name or icon too.

**Motion.** Flight 220 ms ease-out, each item's start staggered by up to 40 ms across the panel, reversed for Undo. New position
is real immediately. With reduced motion: no flight, target slots flash once. Pinned slots don't move and pulse once on Tidy.

**Sound, all Minecraft recordings by name, played from the client.** Tidy: `item.bundle.insert`, low pitch. Stow: one
`item.bundle.drop_contents` and each chest's own open and close. Stow into a full chest: `item.bundle.insert_fail`. Pin:
`block.chiseled_bookshelf.insert`, quiet. Undo: `item.bundle.remove_one`. Ledger page: `item.book.page_turn`. The Drop keeps its
`works.land_*` sounds. All audition in context before shipping, and pass `ding_check.py`.

**Keys, GUI only and rebindable.** T Tidy, S Stow, Z Undo while the line shows it, Alt-click pin. Off while a text box has focus. No
in-world key changes.

**Accessibility.** Every button has a tooltip and a key. Pin has a mode as well as Alt. No meaning rests on colour alone. Works at
GUI scale 2 to auto. Controls list groups them under "Storage".

---

## 10. Before and after

Illustrative: these describe proposed behaviour.

**Ordinary.** Home after mining. Before: open chest, drag 12 stacks. After: open chest, Stow. Eleven stacks go in, one new item
stays. Tidy. Done in two clicks.

**The case you described.** Evening 12: chests everywhere. Before: nothing tells you what to do. After: the Hearth asks, you dig a
cave, set a Drop, tip your bag, hold, watch it go. It's all there and there's a sign on every bay.

**Difficult.** A cave with a chest high on a wall and a pantry through an opening. Before: the Totter stalls, food goes to the
wrong room. After: it tosses the armful up, and the pantry isn't part of the room because the reading follows the room's cells.

**Failure.** A hall with no room left. Before: the Totter puts it back and rests; only the Hearth page says why. After: same, and
standing at the Drop tells you, in those words, what it needs.

**System flow, today.** Bag or chest → by hand → chest. Separately: keystone and Totter → Drop → Totter → bay by first item →
Ledger text list.

**System flow, proposed.** Any screen → Tidy or Stow (server, one code path) → slots. Home → Hearth asks → your room → Drop →
hand or Totter or hopper or chute → bay rules → chests, with Tidy → Ledger reads live → Fetch or Show me.

---

## 11. Testing and stages

**Baselines** are in §2. Every number above in "Measured" gets a test that must flip.

**Golden sets.** A hand-written list of the 300 most common items with the right sort. A full dump of the sorter on every run,
diffed against the last accepted one. Randomised conservation test.

**Stages**, each ending with a green CI run. Euan plays at the two marked points.

| Stage | What | Gate to move on | Size |
|---|---|---|---|
| 0 | The sorts; bay rules (majority, exact item, double chest, cells, copper); new tests | Odd under 10%; golden list 100%; Measured failures flip | Small |
| 1 | Tidy, Undo, Pins, holo chests, flights, sounds, keys | Conservation test; live check beside Mouse Tweaks and JEI; **Euan plays** | Large |
| 2 | Stow, Stow nearby, Refill | Tests; no surprises in play | Small |
| 3 | Drop without keystone, Drop-only room, Hearth ask, hand filing, cards, deep rooms, Tasks steps | A hand-dug cave works from nothing in one sitting; **Euan plays** | Medium |
| 4 | Totter toss and give-up, bays kept tidy, the Ledger remade, crafting reach | Totter tests; Ledger review | Large |
| 5 | Hopper input, chute | Tests | Small |

Rough size, at this project's pace: stages 0 to 2 are about an evening of builder time and 3 to 5 about the same again. That's a
guess from similar jobs in this repo. The holo chest screens are the biggest unknown.

**Rollback.** Tidy and Stow have a client setting that hides them. Bay rules are one class. The Ledger keeps the old screen behind
a setting for one build. No save format changes that can't be ignored.

**What would stop us.** If Mouse Tweaks or JEI conflict in a way we can't fix by moving a button, we stop Stage 1 and ask you.

**Still unproven.** How the flight feels. Whether T and S clash with anything in your real pack. How a Totter gets to a deep room
it can't walk to; the fallback is it arrives directly. Frame cost of a large Ledger plan. Real Tectonic caves.

**Reviewed from four views (a structured review, not real testing).** A new player: "Tidy" is obvious, Pin isn't, so it gets a
tooltip and a first-time hint. A frequent player: T and S are quicker than the mouse; Stow nearby is the one they'll use most. An
accessibility user: nothing relies on colour alone; hold-to-file has a setting for a single click. Reliability: one code path,
server only, abort before writing.

---

## 12. Decisions

Say "all recommendations" or pick them off by number.

1. **The direction.** B, the three rungs. *Recommended.*
2. **Rows that line up.** Each sort on its own row when there's room. *Recommended: yes.*
3. **Chests in holo glass**, to match the bag. *Recommended: yes.*
4. **Refill the hotbar** when a stack runs out. *Recommended: yes, with tools off by default.*
5. **The keystone moves** from the Drop to the Ledger desk; the Drop takes iron. *Recommended: yes.*
6. **Any depth** under your land for a room with a Drop. *Recommended: yes.*
7. **Hoppers and the chute** into the Drop. *Recommended: yes, last stage.*
8. **Filing by hand** before you've got a Totter, by holding use on the Drop. *Recommended: yes.*
9. **"Keep my bag tidy"** as an automatic option. *Recommended: no. Things moving by themselves is the worst way to learn where
   they are.*
10. **Totters keep each bay tidy** as they file. *Recommended: yes.*
11. **Two new sorts**, Building and light, Books and brews. *Recommended: yes.*
