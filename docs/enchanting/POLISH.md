# Inscriptions Worth Writing (the enchanting polish plan)

Plan for Euan's request of 2026-11-11, written by the enchanting-polish session on branch
`claude/enchanting-polish-plan-4cb15c`. Status: **approved by Euan on 2026-11-11 ("go ahead with the full plan") with
three changes (§8); the pilot is built on this branch and recorded as SPEC §45.15 to §45.18**. It follows
`.claude/PLANNING-PROCESS.md` and `.claude/UI-EXCELLENCE.md`. Approval page:
https://claude.ai/artifact/GGUiB3qof3qZMVuxQoMAsb (private; source `docs/enchanting/polish/page.src.html`, built by
`docs/enchanting/polish/build_page.py`).
The system it audits is SPEC §45 (the Inscribing Table, merged at 9a1f382a1 on 2 Nov); the first plan is
`docs/enchanting/PLAN.md`.

## 1. Understanding and success contract

### 1.1 What Euan asked for

> "looking at the whole enchanting experience and what we can do to like polish that up as well. Like, is that on par
> with everything else in the game, or is it something else missing... just to really polish up the entire, like,
> enchanting experience, and maybe this enchantments that could be more interesting and a bit more creative, and to make
> the whole thing, again, more satisfying and more immersive overall, and a bit less vanilla while still staying MC."

- **The job.** Enchanting should be something he wants to do with a piece he cares about, at the bar the armour
  forge, the Hall of Arms, weapon feel and the boss fights now set.
- **The pain.** He asked a question ("is it on par?") rather than naming a fault, because he has never used it. The
  audit below answers the question from his own save, his logs and frames of the real game.
- **The desired experience.** More satisfying and immersive, less vanilla, still Minecraft; inscriptions that are more
  interesting to own.
- **His proposed solutions.** "More creative enchantments" is the one idea he names. It is treated as something to
  test, not as the whole answer.
- **Hard constraints.** No local Java or tours on his Mac. Sounds from Minecraft's recordings by name, no high
  synthetic ding. One straight vanilla arm. Weapons voxel on the grid. Helms never change. Plain speech in every line.
  Every step on Tasks, every new block or item in the ledger. Everything fits a 427 by 240 screen. Multiplayer-safe.
  The 20% ward cap against bosses stays.
- **Unknowns, said plainly.** He has never traced a mark, so nobody knows how the trace feels in his hand. No sound
  of enchanting has been heard by him. Frame time at the table on his Mac has never been measured.

### 1.2 What "better" means here, in things you can see

| | Looks like |
|---|---|
| **Strong** | Within one session of his first page he has a table, has written on a piece, and goes looking for a second word. A friend watching him play could tell which inscription is on his weapon and when it does something. Nothing on the table's screen reads as a list of text. |
| **Weak** | A nicer book with the same list in it, the same percentages under new names. |
| **Unacceptable** | A chore on every write. More text. A high ding. Anything that automates play, shows what you couldn't see, or makes a boss easier. An old piece losing what is written on it. |

### 1.3 The contract

| | Today (measured) | Target | How it is checked |
|---|---|---|---|
| Reaches the table | 6 pages given, 4 read, 0 tables, 0 written (two worlds, 8 to 11 Nov) | a table and a first inscription in the session of the first page | his save (`awaken:enchant_known`: `table`, `written`) and new `[enchant] door:` log lines with game time |
| Errands before the first write | a Book (his stats show none crafted or picked up) | nothing a first gate doesn't already give | GameTest: the recipe from a first-gate pack |
| The piece at the table | its 16 pixel icon at half a block | the real weapon or armour piece, about 0.8 of the slate | look tour, same shots as today's |
| The choosing screen | two pages of text, 62% of the screen | no sentence longer than a name except the one on the plate under the pointer | UI probe at 427 by 240, word count per view |
| Marks | 5 templates varied by a hash; three banes read as one arrow | 36 glyphs, each its own, the same sign everywhere it appears | test `enchant_glyphs` (distinct, 2 to 5 strokes), the page sheet |
| At work | a ward's ring and a bane's mark are lines one pixel wide for under a second | every one of ours shows at 0.6 of a block or more for half a second or more | look tour in a fight |
| Ours that are verbs | 0 of 9 (all percentages) | 6 new words that each make something visible happen | per-word GameTests and tour shots |
| Lords carrying a vanilla name | 7 of 15 | 2 of 15 (Swift Sneak, Mending) | `Inscriptions` data test |
| Sound | 17 single vanilla events; the wake is vanilla's own enchanting sound; unheard | each beat picked by him on a local listening page; `ding_check.py` clean | his marks |
| Cut-off text | the first-page line ends "and its L…" | nothing cut anywhere | §57.11's tests on the new views |

**Non-goals.** No change to forge numbers, tiers, tempering, Marks, set abilities, powers or any boss number. No
fight-moment triggers. No information powers. No new menu, no daily list. No mixin.

**Must not regress.** Everything `EnchantTests` checks today: the rule (what can be written), room and depth, the
session's safety (one writer, nothing duplicated, refunds), the fold, the doors, Tasks, Word Stones. Old enchanted items
keep what they carry. What a player has learned stays learned. Wards stay capped at 20% against bosses.

## 2. Verified audit and diagnosis

Sources: his play copy's saves and logs (read only), SPEC §45 and the code in `dev.awaken.enchant`, and two look tours
on GitHub run from this branch at build 10d421719: `enchant-table` (run 42988802906, 22 shots) and a new
`enchant-audit` (run 42989477152, 53 shots; `tools/autopilot/enchant-audit.txt`). Both pass. Frames are in
`docs/enchanting/polish/img/`.

### 2.1 What Euan did with it (verified)

- **World of 8 Nov:** the Hive Regent's page of Bane of Crawlers at 21:20, read at 21:25. A page of Bane of Beasts at
  21:37 from Terror of Willowford: still unread in his pack in that save.
- **New World V20 (11 Nov, 103 minutes played, level 25, rank D):** a page of Fortune from a lode's heart at 09:22
  (never read; not in his pack now), Bane of Crawlers at 10:06 (read 10:09), Bane of Beasts at 10:16 (read 10:18),
  Bane of the Dead at 13:48 (read 13:49).
- His knowledge in that save: three learned (all banes), `table` false, `written` 0, nothing in memory.
- His Tasks state has seen `enchant/page/weapons`, `enchant/page/armour`, `enchant/read` and `enchant/table`; the two
  enchanting lines hold places on his Weapons and Armour tabs now.
- His pack: a Beastbound Maul, a Wildhide Longsword he forged, Wildhide armour (two pieces Masterwork), 10 essence
  shards, 21 planks. His stats: 39 lapis picked up, **no book** crafted or picked up.
- So in two worlds he has been handed six pages, read four, and never built the table. Everything the first build put
  its craft into (the scene, the trace, the wake) sits behind a door he has not opened.

### 2.2 The map

| Part | What it does today | Evidence | Consequence | Verdict |
|---|---|---|---|---|
| Getting a page | goes into the pack with one chat line | `Pages.give`; his logs | the first touch of the system is a line of chat among others | change: a find you see |
| The page item | one icon for all thirty; held, it is a blank sheet with a blue blot | `enchantment_page.png`; frame `today_page_hand` | thirty pages are indistinguishable in the pack and in the Journal (frame `today_journal`: one icon thirty times) | change: its own sign on each |
| Reading | vanilla's use swing, a wash of colour at your feet, a four-line chat message | frame `today_page_read` | learning a word reads as a system message | change: a beat in the hand |
| The System's one line | a toast cut off: "Every kind of gate keeps its own enchantment, and its L…" | frame `today_toast_cut` | the one piece of direction is unreadable | fix |
| Tasks | "Make an Inscribing Table" second on Weapons, under the War Anvil | frame `today_tasks`; his `seen` list | he saw it and did not act | keep, and make it worth acting on |
| The recipe | lapis, **book**, lapis / planks, shard, planks / stone, lapis, stone | SPEC 45.5; his stats | an errand (leather, sugar cane) before a first try | change |
| The block | five boxes: a foot, a body, a slab, a small brass bracket; a book floating over it | `inscribing_table.json`; frame `today_table_close` | in the room it reads as an enchanting table in other colours | change: its own shape |
| The piece on the slate | the item's FIXED model at half scale: the 16 pixel icon, for sculpted weapons and armour too | `InscribingTableRenderer:99,125`; frames `today_book`, `today_armour_book` | the thing you came to work on is a cut-out of its inventory icon | change: the real piece |
| The view | steps you to the desk and leans in toward each beat | SPEC 45.10; frames | this is the strongest thing here: a scene in the world, not a window | keep |
| The book | two parchment pages over the lower 62% of the screen: name, rank, a sentence about room, a list of rows, a sentence about the Study, a sentence about what to do | `InscribeScreen`; frame `today_book` | the pattern he rejected at the War Anvil on 9 Nov ("just a block of text... not magical") | replace |
| A full piece | every row greyed with the same sentence | frame `today_no_room` | three lines that say one thing | replace: one sign |
| Marks | `Marks.build`: five family templates varied by a hash of the number | code; frames `today_book` (three banes), `today_armour_book` (three wards) | within a family the marks read as one | replace: designed glyphs |
| The trace | 3 to 5 strokes drawn from a glowing end; the cut follows the pen; tuned on a live hand | SPEC 45.10; frames `today_trace_*` | built with care, never tried by him; the hint is drawn over the book's cream pages and can't be read | keep the rules; fix the hint; new glyphs |
| The wake | the icon lifts, turns, a band of light; then a card of four lines and two buttons | frames `today_wake_*`, `today_card` | the payoff is a paragraph | change: the Hall's moment |
| A converted vanilla table | turns in place with a flare and opens | frames `today_fold_flare`, `today_fold_side` | **defect:** it opened seen from the table's side, the piece standing on end and cut by the screen's top | fix (likely cause in §2.4) |
| The script line | our weapons and armour take the enchantment's colour on what glows | frame `today_script_line` | the blade's inlay turned from cyan to pink: you can tell it is enchanted, not with what | keep; add the glyph where it can be read (table, tooltip, Hall) |
| At work | a ward's ring, a bane's mark: lines about a pixel wide, 16 to 18 ticks | frames `today_ward`, `today_bane` | lost under vanilla's red hurt flash and the damage number | redraw at fight size |
| Word Stones | a dark stele, its letters lifting off; a hand held to it | frames `today_word_stone`, `today_word_learn` | has a look of its own; the learning line is chat again | keep; same reading beat; the stone shows its word's glyph |
| The thirty | 21 are vanilla enchantments by vanilla names; our 9 are 5 wards (2.5% a level a piece), 4 banes (8% a level, never a Lord), Clear Blood, Harvest, Insight | `Inscriptions.ALL`, `EnchantEffects` | none of ours makes anything happen that you could watch | extend |
| Lords' pages | 7 of 15 Lords carry a vanilla name (Unbreaking, Respiration, Projectile and Blast Protection, Looting, Swift Sneak, Mending) | `Inscriptions` | "every kind of gate keeps its own enchantment" is half true | change five |
| Sounds | 17 events, each one vanilla recording since the sound audit (§66); wake, learn and drift are all `block.enchantment_table.use`; the running cut is a one-shot played as a loop; stroke notes are amethyst chimes pitched up | `merge/enchant/.../sounds.json`, `EngraveLoop` | the signature moment sounds like the vanilla table; unheard by anyone | re-pick with him |
| Safety | the piece never leaves the hand; one writer a table; server judges every stroke | `Inscribe`, `EnchantTests` | sound | keep untouched |

### 2.3 The disappointment, end to end

1. **Ordinary (his own).** The Hive Regent falls → a chat line → a page in the pack → use it → a chat paragraph ending
   "It never works on a Lord. Write it onto your gear at an Inscribing Table." → Tasks: "Make an Inscribing Table: Book
   0/1" → nothing. The useful thing first disappears at the reward: what he learned is 8% against crawlers that does
   not touch the fights he cares about, and the next step is an errand for a book.
2. **Strong today.** Standing at the table as the view leans in and the burin follows the line: nothing vanilla does
   this. The Word Stone's letters. These stay.
3. **Disappointing.** The book: 62% of the screen is parchment with sentences on it, and the weapon above it is its
   own icon.
4. **Ambiguous.** A full piece: the screen answers with the same refusal on every row instead of showing the one thing
   you can do (scrape).
5. **Failure.** A vanilla table used from the west opens framed from its side (frame `today_fold_side`). The System's
   line is cut. The trace's hint can't be read against the book.

### 2.4 Root causes

| Symptom | Cause | Evidence for | Against / unknown | Smallest test | If fixed |
|---|---|---|---|---|---|
| Never built the table | the reward is weak: all he knows is "+8%, never on a Lord" | his three learned; every learn line ends on the negative | he may simply not have got round to it in 103 minutes | the door log lines on his next play | he wants the table |
| | the recipe asks for a book | no book crafted or picked up in his stats; shards and planks in hand, 39 lapis picked up | he could have made one | recipe without it | one errand fewer |
| | the first touch is chat | logs: every step is a chat line | none | frames after | the page is noticed |
| The table reads as a window with an icon | the slate copy is drawn with `ItemDisplayContext.FIXED` at 0.5 | renderer lines 99 and 125 | none | draw the in-hand model | the piece is the hero |
| | the screen was built as a book of rows | `InscribeScreen` | it predates the Hall (9 Nov) and One Stage (10 Nov) | mock-up | no list |
| Marks look alike | generated from five templates | `Marks.build` | none | glyph sheet | each word has a face |
| Effects are lost in a fight | drawn as thin ribbons for under a second | frames | never seen at real speed | redraw, film | visible |
| Inscriptions feel vanilla | 21 of 30 are; ours copy their shape (a percent a level) | list | wards and banes do have a job (preparing for a gate) | his pick of the new words | words worth finding |
| Side-on view after a fold | **likely:** the Open message is handled before the client has the new block state, and the scene falls back to facing north (`InscribeScene.place`, line 555) | the frame; the code path | not proven | send the facing with Open | fixed |
| Cut toast | the line is sent as a toast, which holds two short lines | frame | none | send it as a caption | whole |

Not the cause: the rule itself ("your forge decides how strong a thing is; enchantments add what the forge can't"),
the room and depth links to the forge, or the session's safety. They are sound and stay.

## 3. Alternatives and the recommendation

| | A. Repair | **B. Finish it (recommended)** | C. Replace |
|---|---|---|---|
| What | fix the door (recipe, toast, fold), enlarge the piece, re-pick sounds; same book, same thirty | A, and: the real piece on the slate, choosing without a book, one designed script, effects at fight size, six new words in the Lords' hands, a desk with its own shape | drop vanilla's enchantment engine for our own "inscription" component; every effect ours; a new mini-game per family |
| Answers "less vanilla" | barely | yes, in what you see, hold and hear | yes |
| Answers "more creative" | no | yes, where a Lord's reward was a vanilla name | yes, at the cost of everything vanilla gets right |
| Keeps what works | all | the view, the trace, the rules, the safety, every old item | little |
| Risk | tiny | medium, mostly drawing | large; tooltips, JEI, Jade and old items break |

**B.** A leaves the two things he would judge (the screen and the words) as they are. C spends its effort re-making
effects that already work. Within B, two narrower choices:

- **Choosing:** (1) keep the book and tidy it, (2) **your pages lying along the desk's near edge, the mark shown on
  the piece as you point**, (3) a wall of every inscription like the Hall's. (2): it is the Hall's rule (the thing is
  the hero, real things for icons, actions on the thing) at the size of a desk; (3) would show thirty-six sockets for a
  piece that takes four of them.
- **The trace:** keep it as built, with the new glyphs. It was tuned on a live hand and he has not tried it; changing it
  on a guess would repeat the Swing Studio's mistake. If he finds it a chore in the pilot, the fallback is one held
  press for every write, which is a small change.

What would change the recommendation: if he plays the pilot and the desk itself (not the words) is what he doesn't
care for, the words ship alone on the old table.

## 4. Behaviour and architecture

Everything stays in `dev.awaken.enchant`, its resources and `src/main/merge/enchant/`. Where another module's line is
needed it is named. At build this is recorded as SPEC §45.15 onward.

### 4.1 The door

- **A page is a find.** Where a page is given today (`Pages.give`, `Doors`), it still goes into the pack (it can't be
  lost or taken), and the player also sees it: the page flies from the fallen Lord (or the lode's heart, or the beast)
  to the hand along a ribbon in its colour over 14 ticks, and a card shows the page with its sign, its name and "Use it
  to learn it" (through the Herald at IMPORTANT, so it waits for the fight's end and is never cut). Chat keeps one short
  line as the record. Everyone who fought gets their own, as now.
- **Reading is a beat in the hand** (about 1.6 s, never blocking): the hand holds the page up; its glyph lifts off
  the paper stroke by stroke, in the order you will trace it, and runs into the back of the hand; the page falls to
  motes. Then a card: the glyph, the name, its line, and when something you carry fits it, "It fits your Beastbound
  Maul." Chat keeps "You learned Bane of Crawlers." Old books and Word Stones use the same beat.
- **The System's line** on the first page is shown whole, as a caption.
- **The recipe** loses the book: lapis, stick, lapis / planks, essence shard, planks / stone, lapis, stone. The Tasks
  row follows the recipe as it does now.
- **The fold** sends the table's facing with the Open message, so the scene never frames before the block arrives.
- **Door log** (development runs): `[enchant] door: page given|read|table set|opened|chosen|written` with the
  enchantment and game time.

### 4.2 The table

- **The desk** (the block's model, `tools/art/specs/enchant.py`): two trestle ends and a stretcher, an apron with a
  drawer, the slate with its lapis channel, a rail along the back, the book lying open on a brass rest at the writer's
  left, an inkwell of lapis at the right with the burin standing in it. About eighteen boxes at 16 pixels a block. The
  book still turns its pages toward whoever is near and takes the Study's letters; it no longer floats. Shape and
  outline follow the model. Draft: `docs/enchanting/polish/mock/desk_preview.py`.
- **The piece itself lies on the slate.** Our weapons: the item's own in-hand model, laid flat by the table's
  renderer (no new API). Our armour: one draw call asked of the armour module (it already draws a piece alone on the
  Fitting Stand); until it exists armour keeps its icon, larger. Vanilla items: their icon is their look. Every piece
  is scaled by its bounds to span about 0.8 of the slate.
- **The room steps back.** While you write, the world outside the desk dims (`UiHooks.setFocus`, which Lens already
  reads) and the slate is lit from its channel. Hints sit on a plate at the foot in the band's key caps.
- **Choosing.** No book window. Along the desk's near edge lie the pages of what you know that fits this piece, each
  the real page item with its own sign. Pointing at one lifts it, burns its glyph faintly on the piece where it will
  sit, and shows one plate: name, one line, depth as pips (lit: this table can; a bookshelf on those that need your
  Study), and the price as the real things with have and need. A click takes it to the piece and the trace begins. A
  page in memory wears a lapis pin: a click writes it from memory; Shift and click traces it by hand for the refund.
  What can't go on (no room, can't go with what's there, as deep as it goes) lies face down, and its plate says why once.
- **What's on the piece** shows as glyphs on the piece and as chips in its sockets under its name: a socket for each
  enchantment it has room for, filled or empty. Pointing at a chip flares its glyph and shows its plate with **Hold to
  scrape it off** (0.8 s; it replaces the second click).
- **The trace** keeps every rule and number of SPEC 45.5 and 45.10. The mark is the inscription's glyph: straight
  cuts between points of a 7 by 7 grid, 2 to 5 strokes, laid on the real piece.
- **The wake.** As the last stroke seals, the piece lifts and turns as now, its script line runs it for the first
  time, and `HallMoment.show(name, "A clean hand" | "Written" | "From memory", colour)` gives the Hall's hit of light and
  the name in blackletter. The foot shows levels flying back as bottles, the page's new pin when its mark went into
  memory, and key caps: Space to write another, Esc to step away. No card of text.
- **Settings.** `classicTable` in `awaken-enchant-client.toml` keeps the old book and card for one build, as the Hall
  did; it is the rollback.

### 4.3 The words

- **One script.** `Marks` becomes a table of 36 designed glyphs (draft: `docs/enchanting/polish/mock/glyphs.py`): a
  frame for the family (wards a shield, banes a spear, spoils a cup, carrying a gable, keeping a knot) and the word's own
  sign inside it. The same glyph is on the page item (36 icons from the art pipeline: the sign on a sheet with a band in
  the family's colour), on the desk, in the trace, in the sockets, in the Journal, on a Word Stone's face, in every
  at-work moment, and among the letters that drift in a Study (signs of words you know).
- **At work, at fight size.** Each of ours is redrawn as a lit shape, never a line: 0.6 of a block or more, 10 ticks
  or more, full-bright, still at most 48 at once.
  - *Ward:* the glyph stands as a plate of light between you and the blow; what it took off flies away as sparks of the
    Force's colour.
  - *Bane:* the glyph brands the creature where the blow landed and glows on it for two seconds; a creature a bane
    kills goes out in the bane's red.
  - *Harvest, Looting, Fortune:* the extra drop rides a gold thread you can see from across a room.
  - *Clear Blood:* each time it burns poison off, a green pulse runs the piece's script line.
  - *Insight:* the extra experience arrives as an orb with a gold edge.
- **Lines that say what a word does for you.** A bane's line leads with where it helps ("Your hits land 8% harder for
  each level on spiders, bees and the creatures of a Hive."); that Lords shrug it off moves to the Journal's entry.
- **Six new words.** Each makes something happen that you can watch. Numbers are first values.

  | Word | Goes on | What it does | What you see | Found |
  |---|---|---|---|---|
  | **Lantern** I-III | helmets | lights the dark around you (light 8, 10, 12 where it is darker than that) | a warm pool of light that walks with you; it catches with a small flare as you step into the dark | the Lantern Night Market's Lord (who carried Looting) |
  | **Regrowth** I-III | anything that wears | under open sky by day, or in rain, it mends one use every 12, 8 or 5 seconds; never with Mending on the same piece | a green stitch running a short way along its script line | the Overgrown Temple's Lord (who carried Unbreaking) |
  | **Remembrance** | anything | it keeps the names of the Lords and named beasts it has brought down (armour: was worn against), twelve at most | a notch of light on its script line for each; the names in its tooltip; "Your Beastbound Maul remembers the Bell Warden." | the Drowned Archive's Lord (who carried Respiration) |
  | **Gather** I-II | weapons and tools | what you break and kill with it comes to your pack from 8 or 12 blocks, as spoils do in a gate | the drops flying to you along a gold thread | the Precursor Lab's Lord (who carried Blast Protection) |
  | **Wayfarer** I-III | boots | on roads and paths you run 8, 14 or 20% faster and running there costs no hunger | motes at your heels while it works | the Glass Desert's Lord (who carried Projectile Protection) |
  | **Long Arm** I-II | chestplates | you reach one or two blocks further to break and place (never to strike) | nothing: a builder's quiet word | Word Stones of the sky platforms |

  The five vanilla enchantments those Lords carried stay writable; they move to the market, old books and the Word
  Stones' sets. The list becomes 36 (`Inscriptions.ALL`, numbers 31 to 36; the Journal's shelf follows it).
- **How each new word is built** (all in `dev.awaken.enchant` unless said):
  - *Lantern:* the server keeps one `awaken:lantern_glow` (no model, no collision, replaceable, light by level) at the
    wearer's head where the block is air or water and the light there is below the word's own, moved when they have
    gone two blocks, cleared when they leave or take the helmet off; a glow with no wearer within three blocks clears
    itself on its tick. Half strength in a Lightless gate (`GateHooks`, if it can tell; else full).
  - *Regrowth:* `PlayerTickEvent`, every 20 ticks, worn and held pieces; `canSeeSky` and day, or rain.
  - *Remembrance:* item component `awaken:remembered` (schemaVersion 1: a list of names, twelve at most), written on
    the death of anything `EnchantEffects.spared` covers, on the killer's main hand piece and each worn piece that
    carries the word.
  - *Gather:* `BlockDropsEvent` and `LivingDropsEvent`: the drops are given to the player (what doesn't fit falls at
    their feet) and one `enchant_fx` thread is sent for each.
  - *Wayfarer:* a transient movement speed modifier while the block under the feet is a path or
    `RoadHooks.nearestRoad` is within a block; checked every 5 ticks.
  - *Long Arm:* data only (`attributes` on `minecraft:block_interaction_range`).

### 4.4 Sound

Every beat is one or two of Minecraft's own recordings, named in `sounds.json`, layered in code where two are needed,
and picked by Euan on a local listening page (`tools/art/enchant_listen.py`, the pattern of `weapon_listen.py`; never a
hosted page). `ding_check.py` runs on every pick. First picks, to be replaced by his:

| Beat | Today | First pick |
|---|---|---|
| A page reaches your hand | none | `item.book.page_turn` over `entity.item.pickup` low |
| Reading | `block.enchantment_table.use` | `item.book.page_turn`, then `block.respawn_anchor.charge` low |
| The burin lifts from the inkwell | none | `item.bottle.fill` low |
| The cut | `item.axe.wax_off` looped | grains of `item.axe.scrape` and `item.brush.brushing.generic` by distance drawn (no loop, so no seam) |
| A stroke sealed | amethyst chime pitched up a step each stroke | `block.end_portal_frame.fill`, a step up each stroke |
| The wake | `block.enchantment_table.use` | `block.respawn_anchor.set_spawn` with `block.bell.resonate` low under it |
| A ward takes a blow | `block.amethyst_block.resonate` | `item.shield.block` low with `block.beacon.deactivate` |
| A bane bites | `block.amethyst_block.hit` at 1.3 | `entity.zombie.attack_iron_door` soft with `block.sculk_shrieker.break` (to be judged; the one most at risk of a ding) |
| Lantern catches | new | `block.lantern.place` low |

### 4.5 Data, state, lifecycle

- `awaken:enchant_known` stays schemaVersion 1: new words are more keys in the same maps.
- New item component `awaken:remembered` (schemaVersion 1), saved and synced with the item.
- `Marks` is common code read by both sides; the stroke count of a mark changes with its glyph, so server and client
  ship together (they always do). A mark already in memory stays in memory.
- `awaken:word_stone`'s `word` property grows from 1-30 to 1-36; stones already placed keep their value.
- One new block, `awaken:lantern_glow` (`Sources.neverHeld`). No new items: the 36 page icons are one item's models.
- The desk's new model changes the block's outline; tables already placed keep their place and facing.

### 4.6 Failure register (the ones that matter)

| Case | What happens |
|---|---|
| Pack full when a page is given | as now: it falls at your feet; the card still shows |
| A fight is on when a page is given or read | the card waits (Herald); the hand beat is skipped under attack and the word is learned at once |
| Esc, walking off, logging out, the table broken mid-write | as now: the session ends and a choice not yet written is refunded |
| Two players at one table | as now: "Someone is writing here."; watchers see the real piece and the glyph |
| A piece with no room | pages lie face down; the plate says "No room left. Scrape one off first." once |
| The armour module's draw call isn't there | the armour piece shows as its icon, larger; nothing else changes |
| Lantern: logout, death, a crash | the glow clears itself on its tick when no wearer is near |
| Lantern: under water, in a doorway, in a one-block gap | the glow sits in water as a waterlogged light; where the head's block isn't free there is no glow that step |
| Gather with a full pack | what doesn't fit lands at your feet |
| Regrowth and a piece worn out (the boss plan's "worn out at half strength") | it mends it like any wear; the first use back takes it out of worn out |
| Reduced motion, High Contrast, the narrator | the wake arrives without the turn; quiet text brightens; the narrator reads the plate on a change (the Hall's rules) |
| GUI scale 9 (427 by 240) | the page row holds 15 at 18 pixels; a 16th starts a second row; every text is fitted, never cut |

### 4.7 Multiplayer, performance, cost

- Server authority is unchanged. Each new word is judged on the server; Lantern's light is a real block, so everyone
  sees it. Co-op's Full or Gentle setting (SPEC §70) touches none of it.
- Budgets: at most 48 effects at once (kept). The real piece on the slate is one mesh more. Lantern moves one light
  every two blocks walked; **its frame time must be measured on the Mac** (a perf tour, with his OK) before it is on by
  default, because each move rebuilds a chunk section.
- Nothing here costs money. Checks run on GitHub.

## 5. The same moments, before and after

1. **His own (ordinary).** *Today:* the Hive Regent falls, a chat line, a blank-looking page, a chat paragraph, a task
   that wants a book; he stops. *Proposed:* the page flies to his hand with its sign on it; reading it lifts the sign
   off the paper into his hand and says "It fits your Beastbound Maul."; the table takes a stick, three lapis and
   things he is carrying; at the desk his Maul lies on the slate, three pages by his hand.
2. **Disappointing.** *Today:* a book over 62% of the screen and an icon above it. *Proposed:* the Maul across the
   slate, the pages along the edge, one plate under the pointer.
3. **Ambiguous (a full piece).** *Today:* three greyed rows with one sentence each. *Proposed:* its sockets full,
   pages face down, and the chips themselves offering "Hold to scrape it off".
4. **Failure.** *Today:* a converted table framed from the side; a cut line. *Proposed:* framed as any table; whole.

The approval page carries these as pictures and a mock-up you can click, built at 427 by 240.

## 6. Evaluation and delivery

**Baseline (kept as fixtures):** the two tours' frames; his save's numbers in §2.1; `EnchantTests` (12 tests).

**Tests** (one per mechanism; the suite once at the end, on GitHub):
`enchant_glyphs` (36, distinct, 2 to 5 strokes, on the grid), `enchant_recipe` (no book; craftable from a first-gate
pack), `enchant_fold_facing`, `enchant_words` (each new word's rule: Lantern places and clears, Regrowth mends only
under sky, Remembrance keeps twelve, Gather gives and spills, Wayfarer only on a road, Long Arm's attribute), the
existing twelve unchanged, `journey_ledger` (the glow block declared), §57.11's word and cut tests on the new views.

**Look tours on GitHub:** `enchant-table` and `enchant-audit` re-shot, the same shots as today's; a fight shot of each
at-work moment; the new screen at 427 by 240.

**Acceptance gates:** the twelve old tests pass untouched; every frame of the new screen has no cut text; each at-work
moment can be pointed at in a still frame of a fight; he has marked the listening page; Lantern's frame time is
measured.

**Order** (coded whole, then tested once, as he asked on 10 Nov):
0. The door: the find, the reading beat, the whole line, the recipe, the fold, the door log.
1. The table: the desk, the real piece, choosing by pages, sockets and scrape, the glyphs, the wake.
2. The words: effects at fight size, the lines, the six new words and where they are found.
3. Sound: the listening page, then his picks.

**Pilot before the rest:** stages 0 and 1 with four words (the three banes he knows and Lantern), on his Maul, his
Longsword and his Wildhide set. He plays that. The other words and the other 32 glyphs' final art follow his verdict.

**Size.** The first build (stages 0 to 3, 16 commits) took one day on 2 Nov. This is about the same again, most of
it drawing; the pilot is roughly half.

**Rollback.** `classicTable` restores the old book and card. Each new word is its own JSON and listener and can be
left out of every source without touching the others.

**Risks.**
- The trace may not be fun in his hand. Nobody knows yet. The fallback is a held press.
- Lantern makes dark places easier. It is the word most likely to be wanted and the one that changes the most.
- Armour on the slate needs a line from the armour module.
- The Journal's sentence "The thirty enchantments are kept here" lives in progression: one string for the manager.
- The boss branch's `Lasting` and Regrowth touch the same durability; whichever merges second fits to the first.

## 7. Decisions for Euan

Seventeen, on the page (https://claude.ai/artifact/GGUiB3qof3qZMVuxQoMAsb), each with a recommendation: fifteen yes,
the glyph engraved on the held weapon later, running on water no. Three change something he approved on 2 Nov: the recipe (it had a
book), the book's two pages (replaced by the pages on the desk), and the floating book (it lies on a rest). One changes
the list he approved: thirty become thirty-six, and five Lords carry a different page.

## 8. Approved, his three changes, and the pilot as built (11 Nov 2026)

Euan, in the planning session: "go ahead with the full plan." All seventeen as recommended, and three changes of his own:

1. **No menu.** "Let's not make it a menu for enchanting. It should stay 3D inside of the actual world, like similar to
   how it is now, maybe just better, like more cinematic and everything, like better structures." So decision 6 is built
   with no window at all: the pages are real things hanging over the desk in the world, pointed at there, with one
   small label by the pointer. The mock-up's page row, its head with the piece's name and its purse are not built.
2. **The look on gear.** "They shouldn't distract from the actual armor set or anything, but they should still maybe give
   a nice shine or give something that makes it actually like really a bit more unique, or maybe like a small ore or
   something... something that really looks good." Read as a shine and a small aura. Today's script line recoloured what
   glows on the piece, which is the distraction he means: that is gone, and an inscription is a band of light crossing
   the piece and its colour held faintly at the piece's edges.
3. **A page must say how it is used.** "When you find a page and you actually save that page, something should actually
   tell you that like you can use it in an enchantment... to apply it to something, you need to apply it on the table.
   Because nothing says that now. So I thought the pages were like being automatically applied... I don't want them to be
   automatically applied, but I wanted to actually say like... you enchant this onto a weapon... by doing XYZ." So the
   page's own lines, the line when one is given and the card after reading all say that reading only teaches it, and how
   it gets onto a piece.

**Built (the pilot: the door, the desk, his three banes and Lantern), coded whole and compiled on GitHub at each step:**

| Part | Built | Where |
|---|---|---|
| The door | a page's own lines, the given line, a card when a page reaches you and a card when you read it (each a title and three lines, the room a panel has), all of it in chat, a page read at the table, the recipe's stick, the fold's facing, the door log, a page's flight and the reading beat | `Pages`, `PageItem`, `Doors`, `Inscribe`, `EnchantFx`, `EnchantFxClient` |
| One script | 36 glyphs in `Marks` from `tools/art/specs/_enchant_glyphs.py`; 36 page icons chosen by `awaken:page` | `Marks`, `PageSelect`, the art spec |
| The desk | the scribe's desk model, the book on its rest, the burin in the inkwell until you choose, the real piece on the slate laid by its shape (armour's measured from its meshes), the glyph on the piece's top | `InscribingTableRenderer`, `InscribingTableBlock`, the art spec |
| Choosing | pages over the near edge in the world, the label over the row beside the piece, sockets down the slate's left side, hold to scrape, the wheel for the level, key caps, the wake's blackletter and no card; `classicTable` keeps the old book | `Desk`, `InscribeScreen`, `InscribeScene`, `EnchantClientConfig` |
| The words | Lantern (block of light, the rule, the Lantern Night Market's Lord; Looting to the market and old books); banes' lines; then, the same day, the other five: Wayfarer, Gather, Regrowth, Remembrance and Long Arm, four of them carried by Lords in place of a vanilla name (SPEC §45.16) | `Lantern`, `LanternGlowBlock`, `Words`, `Inscriptions` |
| At work | a ward's plate, a bane's brand for two seconds, a larger glint, Clear Blood's green pulse, a gold glint on experience with Insight in the hand | `EnchantFxClient`, `EnchantFx` |
| On the piece | the shine (a band of light every five seconds, the script in its wake) and a faint aura in both shaders | `arms_weapon.fsh`, `armour.fsh`, `mesh_render.py` |
| Sound | first picks in `sounds.json` and the code; the listening page | `tools/art/enchant_listen.py` |

**Differs from the page, and why:**
- No slot row, no head, no purse: his change 1.
- The wake draws its own blackletter and light instead of calling the Hall's moment: that one plays an anvil landing.
- The wake's chord of pitched family notes is gone (§45.18).
- The trace's rules are untouched, but its marks are now the glyphs: straight cuts with corners.

**The rest of the plan, built the same day** at his word that every approved plan be finished (relayed by the
manager, 11 Nov): the five words above, Clear Blood's pulse and Insight's glint. They follow §4.3's table, with these
choices made in the building: Gather never takes another player's pack or what a Lord or a named beast leaves (that
lies there for everyone who fought); Remembrance names a Lord as the game says it ("the Bell Warden"); Long Arm sits
twice among the sky platforms' six words, being found nowhere else.

**Not built, and why:**
- *The hold-to-write fallback.* The plan builds it only if he finds the trace a chore (§6); nobody has traced yet.
- *Small at-work touches of §4.3:* sparks flying off a ward's plate, a gold thread on Looting's, Fortune's and Harvest's
  extra drop (it has the larger glint), a creature a bane kills going out in red (it carries the brand from the killing
  blow), the signs of words you know among a Study's drifting letters, and the notch of light on a piece for each name
  Remembrance keeps (the names are in its tooltip and in chat).
- *Lantern at half strength in a Lightless gate:* gates offer no way to ask; it is full strength everywhere.

**What filming it showed, and what changed** (three runs of `tools/autopilot/enchant-polish.txt` on GitHub, frames in
`run-autopilot/screenshots-ci/enchant-polish-<run>/`):

| Seen | Cause | Now |
|---|---|---|
| The game would not start | the table's renderer made an item stack as its class loaded, before items exist | made at first need |
| The sword in the hand striped pink from hilt to tip | the letters and the aura were lit by how far a face turns from the eye, and in the hand every face of a blade does | the script shows only in the band's wake, the aura is a tenth, and between crossings the piece is itself |
| The fist over the right-hand pages, and a third of the view over a helm | the burin hand rested at the slate's near corner | the burin stands in the inkwell and the arm stays out of the view until you choose |
| The label over the row, and over the page it named | it sat beside the page | over the row, on the page's side of the piece |
| The one socket under the sword's point | a row behind the piece | down the slate's left side, framed |
| A breastplate floating a third of a block up, the view to its mark nearly flat | a sculpted piece's item says only "a block" | measured from its meshes; it lies on the slate at its real size |
| The mark of a page pointed at could not be seen | drawn for a book's close view | brighter, with a soft light under it |
| Neither card | paced notices wait out a new world's first five minutes | CRITICAL until the first enchantment is written |
| "Press K to see 2 more" where the how-to should be | a panel holds a title and three lines | both cards are three short lines; chat has all of it |
| The burin most of a block tall in its inkwell | drawn at 0.42 | the size it is in the hand |

**Not verified:** nobody has heard a sound; the trace has not been tried by a hand since the glyphs changed (the
tour's pilot traces it); Lantern's frame time on the Mac is unmeasured; only one set's breastplate and helm have been
seen on the slate (dreamsteel), though every set is measured the same way. Of the five later words, Gather and
Wayfarer were filmed; Regrowth, Remembrance and Long Arm are tested in their rules but not seen in the game, and
Wayfarer has not been walked on a laid road (only on a path).
