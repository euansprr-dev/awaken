# One Stage: the band, the bag, the glance and the workshops as one system (design plan, 10 Nov 2026)

Euan's ask (spoken, 10 Nov): go through the whole K menu and unify all the UI. The forge and the Hall of Arms "look amazing" and
can be pushed further, and can stay their own menus because they're workspaces. The K menu has "a lot of text, a lot of tabs";
he wants it at the AAA level in how you interact with it, how good the holographic looks, and "the amount of text versus the
amount of actual things to interact with, and pictures". "I really hate walls of text." The tab menu and every holo thing are in
scope, "so that we've really unified the entire interaction system".

**Approved by Euan on 10 Nov 2026: "This plan is great. You can go ahead with the whole recommendations and everything."** All seven
decisions taken as recommended. Two things he asked for with it:
- On Status the armour stood empty in the mock-up. The real player model stands under the armour, as in the bag, and looks at the
  pointer the way vanilla's inventory figure does.
- The Fitting Stand's icon in the mock-up was vanilla's armour stand. Every icon and every name must be the real item's own. (Its
  name, Fitting Stand, was right.) In the build every icon is the registered item drawn by the game and a plain item's name is the
  item's own; `tasks_stage_pictures` checks it.
- Later the same day, with a screenshot of a stat's line ending "...and you shrug off more h...": nothing on the menu may ever be
  cut off, "so you can actually always read everything that you need to read". It is now a rule of the system (SPEC §57.6): on the
  stage text cannot be shortened (what holds it grows, or scrolls), and the old sections fit their text smaller or wrap it;
  `ui_text_never_cut` checks the fit.

**What is built** (SPEC §57, on this branch; seen on four CI tours, unplayed): Stage 0 (the sheet, the kit, the stage shell, Tasks,
`classicBand`) and, from Stage 1, Status and Powers. Hearth and the Journal show their old sections inside the new shell. Still to
build: Hearth and its second door, the Journal wall, the bag's tip, the Tab glance, the cards, the workshops (which still shorten
text with their own `Stage.fit`), the clean-up; and the tests `ui_word_budget` and `ui_keys_one_meaning`. Decision 6 stands: Euan
plays this before the later stages are built.

What follows is the plan as approved. When it was written, no game code or data had changed. The branch holds this plan, two tour scripts, an isolated
prototype (`docs/ui-unify/proto/`) and rendered frames (`docs/ui-unify/shots/`). The approval page is `docs/ui-unify/page.html`, published (private) at
https://claude.ai/artifact/JKaBMt53QurNkprr7Ub5xs; its answers land in the page's `answers` collection.
It follows `.claude/UI-EXCELLENCE.md` and returns the plan in that order.

## What was inspected, and how

- **Seen in the game** (GitHub macOS runners, real client, 10 Nov, branch at build `8496b610`):
  - `ui-unify-baseline` (run 42754069258, 32 shots, 1708 x 960 points, Euan's window size): the first-power cards, the HUD at rest
    and in a fight, every K page top to bottom at C-rank with all 63 powers, the map in the world, the Look screen.
  - `ui-unify-moment` (E-rank, the fire set and a bed built, by day, same window size): Tasks, Hearth, Journal, Status.
  - `tasks-pages` (run 42752866836) and `holo-inventory` (run 42752866673), both in a half-size window.
  - Shots from 9 Nov made by other sessions: the Hall of Arms (wall, suits, close-ups, anvil, line-up, three window sizes),
    the storage bag and chests, the Inscribing Table, The Next Thing's Tasks page.
- **Read in the code only** (not seen): the Tab glance (no tour can hold Tab), the Hearth guide at the Anchor, the Notice Board,
  the Ledger, the Dream Journal, the Waystone list, the Precursor panels, the Forge Song, world plates, toasts in motion,
  narration, sound.
- **Not done:** nothing ran on Euan's Mac. No frame times were measured. Nobody has played the prototype but me.
- One of five tours failed: the old `k-menu-final` script with the full pack crashed world generation in
  `village.road.ClearOfSettlements` (run 42752866599). That is not a UI matter; it is noted for the manager.

---

## 1. Diagnosis and design thesis

### Who opens what, and why
- **A new hunter** (hour one): "what is this band, what do I do now?" Opens K a lot, reads everything.
- **A returning player** (most opens): one question each time. What's next? Did I level? What does this take? Where is it?
- **A late player** (C-rank and up): spends points, swaps powers between fights, checks what a suit takes, looks up a find.
- The quick check while moving (health, level, the step, the way) is the **Tab glance**'s job since 9 Nov. So K is for a
  deliberate visit, and can afford to be a place.

### Baseline: task, steps today, friction, consequence
"Observed" was seen in a tour shot at Euan's window size; "code" was read; "hypothesis" is a judgement not tested with a player.

| Task | Today | Friction | Consequence |
|---|---|---|---|
| What do I do next? | K, Tasks. One card: 76 words, two 12 px icons (observed, moment tour) | The recipe for each piece is a sentence ("Craft it from two sticks, a copper ingot, a block of wool and three planks"); the other eight open steps are behind six tabs and a scroll | Reads as a wall of text; you see one step of nine |
| What else is open? | Click each of NOW, HEARTH, ARMOUR, WEAPONS, BELLS, GATES | Six inner tabs inside the six pages; every row repeats Pin and Set aside (observed) | "A lot of tabs"; twelve open steps take 6 clicks and 5 scrolls to see |
| What does my home have? | K, Hearth, scroll four screens | 13 of 13 room tiles cut their one line off with "…" ("rest heals…", "armour mad…"); "None lives within a long walk of here." is printed four times (observed) | Text you can't read, and text that says nothing |
| Who am I now? | K, Status: good first screen, then about four screens | Truths, the road, titles and latents are sentences in a column ("None yet. A Truth comes true by itself when you live it; the closest are below.") (observed) | The character sheet turns into a document |
| Change a power | K, Powers | Good: sockets and a grid. But with nothing under the pointer the detail card is an **empty box as tall as the panel** (observed, u32), and the body's moves, crafts and the Veil are paragraphs below it | The best page ends in the same wall |
| Look up a find | K, Journal | 12 category chips take the top third of the panel; Creatures is 44 numbered boxes holding "?"; Bells is a grid of 100 numbers (observed) | A spreadsheet of question marks; nothing to look at |
| Glance while moving | Hold Tab | Ten rows of words and bars: "Health", "Food", "Armour", a three-line task (code) | The one place your eyes go is mostly labels |
| Know what a key does | - | Tab is the next page in K, the next gate on the map and the next piece in the Hall; Q/E are goal tabs, journal categories, Hall wings and map turns (code) | Nothing learned in one screen carries to the next |
| Put a thing on my screen | - | It is "Pin" on Tasks, "Track" on the Hearth's beasts, "Track it" in the Hall and "Set as destination" on the map; "Pin" in the bag means something else (observed) | Four words for one idea |
| Read an item's name in the bag | Hover | Minecraft's purple tooltip inside the band's blue glass (observed, storage tour) | The most-seen words in the game are off-system |

### The root cause (observed)
At Euan's window size the K panel is 288 x 228 units: **47% of the width and 62% of the height, about 29% of the screen**. The rest
of the screen shows nothing of the band. Inside the panel the letters are about a third larger than the Hall's (2.7 against 2.0
sheet pixels per font pixel), so a screen holds about eleven lines of forty characters. Every module was given a column in that
slab and a text-first toolkit (`Ui.text`, `pair`, `card`), so every module wrote sentences. Forty-five sections from fifteen
modules stack in five scrolling columns.

The Hall of Arms did the opposite on 9 Nov and is the bar: a full sheet (1708 x 960), the thing itself in the middle, its siblings
down the left, one panel that speaks only about the thing in focus, big numbers with small captions, real items with have/need,
unfound things as black shapes, one line of keys at the foot.

### What is already excellent (keep)
The band on the wrist and its unfold, sound and glass. The world never pausing. Status's first screen and place-then-confirm
points. The Powers sockets and grid. The Tasks **model** (steps derived from the world, the cap, threads, places that hold, Set
aside with Undo): all of it stays. The first-power cards. The map in the world. The Hall, the War Anvil with no window, the
line-up. Narration, reduced motion, Band size.

### Thesis
**The band shows you things, and speaks only about the one you point at.**
One sentence for the experience: open the band and see your world as objects (you, your next step, your home, your powers, what
you've found), with words only for the thing in focus, and the same keys, the same parts and the same manners in the workshops.

The few changes that carry it:
1. **One stage.** K fills the view with the band's glass on the Hall's own sheet, instead of a slab.
2. **One grammar.** The thing in the middle, its neighbours beside it, one panel of words about it, one bar of keys under it. On
   K, in the Hall, in the bag.
3. **Pictures before sentences.** One kit: the real item with have/need, a recipe drawn as a recipe, a number with its caption, a
   needle and a distance, state told by light.
4. **No tabs inside tabs.** Goals become lanes on one view, journal categories become shelves on one wall, the body's moves
   become a strip of icons.
5. **One word, one key.** Track. Esc back, Tab the next thing, Q/E the view beside this one, hold to commit.
6. **A word budget**, checked by a test, so the wall cannot grow back.

---

## 2. Keep, change, consolidate, remove

Every capability a player has today, and where it goes. Nothing is lost; two duplicates are folded.

### The band's shell
| Today | Goes to | How |
|---|---|---|
| Six pages on a rail: Status, Tasks, Hearth, Powers, Journal, Map; keys 1 to 6 | **Kept**, same names, same order, same icons | The rail is larger and is the only navigation |
| K opens on Status; a link can name a page and a section | Kept | A link names a place and a thing (`openBand(place, focus)`) |
| Tab / Shift+Tab step through pages | **Changed**: Tab is the next thing on the stage; Q / E step through places | Same meaning in the Hall and on the map |
| Badges "9+" on the rail | Changed to a glint (amber when it waits on you) | The count shows on the place itself |
| Scroll bar, 18 units a notch | Removed from four places; the Journal wall still scrolls by shelf | Nothing else is taller than the stage |
| The flat fallback (`StatusScreen`) | Kept as the same stage without the glass shaders | One layout, two materials |
| Band size Small / Normal / Large | Kept | Steps the sheet's text sizes |
| What others see (a content-free glow) | Kept unchanged | |

### Status
| Today (order) | Goes to | How |
|---|---|---|
| The hero: face, name, title, level, XP, rank (0) | Status: your figure wearing what you wear, level and rank as numbers | The face becomes the whole of you |
| Six stats, place then confirm, undo (10) | Status, unchanged in behaviour | Confirm is a hold |
| Now row "2 more on the Tasks page ›" (18) | **Removed** | The Tasks glint on the rail says it |
| Waiting for you: a decision (14) | Status panel, first, with Y and N | Only while one waits |
| This gate, Deep Pack, This room, Riding (15 to 17) | A chip in the header while it applies; click it for its panel | Shown in context, never as a standing section |
| Journal, four newest lines (20) | "Lately", three lines with their icons; All goes to the Journal | |
| The path choice (700) | A card moment, like the first power | Only while the choice is open |
| Truths, yours and the three closest (710, 720) | 32 marks: true ones lit, the closest three amber; the panel speaks for the one in focus | Was three sentences each |
| The road (735) | "What comes next": the next three stops as pictures; All opens the strip | Was a list |
| Titles, Latents (740, 750) | Marks beside the Truths | Unfound ones are dark, unnamed |
| Records (on the Journal today) | **Moved here** as Deeds: three numbers, All for the rest | They are about you |

### Tasks
| Today | Goes to | How |
|---|---|---|
| Tabs NOW, HEARTH, ARMOUR, WEAPONS, BELLS, GATES | **Removed.** Six lanes on one view | A lane shows only once it has opened, as a tab does today |
| Do this next, in full | The step in focus: its picture, name, one line, what it takes | The step on top is in focus when you arrive |
| Each need with a sentence of where it comes from | The real item with have/need; a made thing shows its recipe as a grid; the sentence is the tip on the item | `Ui.recipe` exists; the Hearth guide already sends recipes |
| A place as "About 1220 blocks west." | A needle and the number | |
| Also open to you, Out there, When you want | The lanes' tiles; trails carry "when you want" in the eyebrow | |
| Later, folded | A "+n" tile at the end of a lane; opens the list in the focus area | |
| Pin | **Renamed Track**, key T | One word everywhere; "Pin" is left to the bag |
| Set aside, Undo | Kept; Undo is Z for seven seconds, as in the bag | Never on the step on top |
| Choose (a set), See them worn | The suits stand in the focus area; See them worn opens the Hall's line-up as today | |
| NEW chip, READY chip, dots on tabs | A glint; the have/need colours | |
| Foot line ("At D-rank your next armour opens") | Kept, one line | |
| Every system's steps through `core.api.Goals` | **Kept exactly.** One optional field is added (a picture) | The rule "new systems show on Tasks" holds |

### Hearth
| Today | Goes to | How |
|---|---|---|
| Banner: tier, name, comfort, style, rested, town, trophies | The header: name, tier, comfort as pips | |
| Next for your home (a copy of the Tasks card) | **Folded**: the amber room tile, with its panel | It was the same card twice |
| "Everything for your home (10 more) ›" | The Hearth lane on Tasks | |
| Rooms: 19 tiles, name and a cut-off line | Rooms as their own signature block, lit by state; the line is read whole in the panel | Built, started, next, open, not found |
| A room's pieces, where each comes from, the closed room | The panel: pieces as real items with have/need, the recipe on request | |
| Make it look good: grade, six readings, one thing that lifts it | The built room's panel | Was a section of its own |
| Beasts and nooks, the kin list, rumours, wants | A strip of nooks with the beasts in them; each one's panel | Unmet kinds are dark shapes |
| "Rooms and upgrades ›" and the separate guide at the Anchor | **Folded: one Hearth, two doors.** K looks and tracks; at the Anchor the same place also builds the next tier | The Hall's own rule |

### Powers
| Today | Goes to | How |
|---|---|---|
| On your keys, energy | Kept; energy is a bar | |
| Your powers, the grid, pick then press a key | Kept | 13 across, so 63 fit without scrolling |
| The power's card | The panel; it shows the power on your first key when nothing is in focus | No empty box |
| The Ascent, The Body, Crafts, The Veil (paragraphs) | Strips of icons under the grid; the panel says how each is done | A move you don't have is a dark shape |
| Armoury link | A door: "The Hall of Arms" | |

### Journal
| Today | Goes to | How |
|---|---|---|
| 12 category chips (four rows) | An index of pictures with counts down the left; it jumps the wall | Not a mode: every shelf is on one wall |
| Creatures, Beasts, Ores, Cave regions, Enchantments as numbered "?" boxes | Shelves of the things themselves; unfound ordinary things are black shapes with no name | Bosses, Lords and story finds stay sealed: no shape, no name |
| Gate worlds | A shelf of their paintings | The art exists (`gui/crossing`) |
| Bells: a grid of 100 numbers | A shelf of 100 bells, held ones lit in their rank's colour, leads amber | Its panel keeps the lead and Track |
| Lore, Happenings, Lands, Places | Shelves, same rule | |
| Tracking and Also on (chains, bounties) | **Moved to Tasks**, the Out there lane; finished ones stay in Your story | They are things to do |
| Log, Recent messages | One timeline, Your story | Two lists of the same kind today |
| What waits, Saved for later | A strip at the head of Your story | |

### Map
Unchanged. It takes the shared hint bar. "Set as destination" keeps its words and its key, T.

### The other holo surfaces
| Surface | Change |
|---|---|
| The bag and the storage chests | Kept. Item names show in the band's glass (`RenderTooltipEvent.Texture`, checked in the 26.3 sources). Tidy, Pin and Undo move into the hint bar's manner |
| The Tab glance ("the tab menu", as I read it) | Same facts, same width, a third shorter: labels become marks, the step is one line with what it takes as items |
| Toasts, choice cards, story lines, the Now line | Kept. They take the shared key caps and the word budget |
| Creature plates, vitals, the power bar | Kept as they are |
| The first-power, weapon-issue and view cards | Kept: already the right pattern |
| The Look screen | Kept; takes the hint bar |

### The workshops
| Workshop | Change |
|---|---|
| Hall of Arms: wall, wing, close-up, line-up | Kept. "What makes it different" becomes marks with numbers instead of two paragraphs; the foot becomes the shared hint bar; Track takes T; its colours merge with the band's (two copies drift today: `hall.client.Stage` and `signal.client.Glass`) |
| War Anvil | Kept. "At most it can take Temper VI…" becomes six pips with the number at the end |
| Inscribing Table | Kept as a book. "6 levels and 2 lapis" becomes the two items with counts; shared keys |
| Forge Song, the anvil's song | Untouched: they are played, not read |
| Notice Board, Ledger, Dream Journal, Waystones, Precursor panels | Each stays its own object. They take the kit in the last stage |

**Removed outright:** the Now row on Status, the second copy of the Hearth card, the Hearth guide as a separate screen, the inner
tab rows, the repeated Pin / Set aside on every row, the empty power card.

---

## 3. Directions and recommendation

### What was borrowed, and what each thing solves
From play, not from cited sources. No claim here is a measured fact about another game.
- **The Hall of Arms (ours):** thing, siblings, one panel, keys. It already passed Euan's bar. The cheapest thing to borrow.
- **Minecraft's own recipe book:** a recipe is a grid of items. Players already read that picture; we write it as a sentence.
- **Collection walls** (a Pokédex, a compendium): unfound entries as dark shapes make you want to fill them. The Hall does this.
- **Character screens with the figure in the middle** (Destiny, Elden Ring): you see what you are wearing, and details come from
  what the pointer rests on.
- **Where it would not fit:** a radial wheel for the six places (the number keys already do it); a quest log with objectives and
  markers (Euan ruled out daily lists and named bosses; the lanes show only what has opened).

### Three directions, different in how the work happens

| | A. Tidy the band | **B. One stage (recommended)** | C. The band in the world |
|---|---|---|---|
| What it is | The slab stays. Inner tabs go, rows get pictures, shared keys | K fills the view with the band's glass on the Hall's sheet. Six places, no inner tabs, the kit everywhere | K always pulls the camera over your shoulder, as the map does, and every page is a projection in front of your character |
| Next step visible | One step and three names | The step in focus and every other open step | As B, at an angle |
| Words for one step | About 40 | About 30, with both recipes drawn | As B |
| Discoverability | Low change | Lanes and shelves show everything that has opened | High, and striking |
| Holographic look | As today | Layers of glass, things projected on a lit floor | Strongest |
| Learning cost | None | Tab and Q/E change; Pin becomes Track | The same, plus a camera move on every open |
| Risk | Low. The slab still can't hold a home, a wall of finds or a figure | Middle. Fifteen modules' sections are re-set; the world is dimmer behind it | High. Needs room behind you, falls back in caves and fights, 320 ms on every open, text at an angle |
| Cost to build | Small | Middle; staged, with the old band behind a setting | Large |

**Recommendation: B.** A cannot reach the Hall's bar, because the slab is the limit, not its dressing. C is the most striking but
pays its cost on every one of the most frequent opens and needs two fallbacks; it is worth one experiment later (Status as your
real character where you stand), not the whole band.

**The tradeoff to accept with B:** the band covers the view (the world shows dimly through it, as with the map's card), where
today it leaves the right half clear. The map's fight rule comes with it: hit, or something hostile within 8 blocks, and it folds
in 150 ms.

### Opportunities found on the way (essential, promising, not justified)
- **Essential:** recipes as recipes; one Hearth with two doors; chains moving from the Journal to Tasks; the word budget test.
- **Promising, as experiments:** found creatures drawn as themselves on the Journal wall (cost unknown; eggs until measured); a
  weapon's Art played on the model in the Hall when you point at the mark; Status as your real character in the world (direction C,
  one page).
- **Not justified:** a new font, new sounds, a controller map, a search box (nothing is long enough to need one once the wall has
  an index).

---

## 4. Visual proposal

All in `docs/ui-unify/shots/` and on the page. Real shots are named `real_*`; everything named `mock_*` is a drawing rendered by
the prototype with the game's own font, over a real frame of the same view.

- Before and after, same view and content: Tasks at Euan's moment (E-rank, by day); Tasks at C-rank with twelve steps open;
  Hearth; Journal; Status; Powers with all 63.
- The central journey, frame by frame, and the clickable prototype of it.
- States: a new hunter with one step; the dense case; nothing to do; higher contrast with no transparency; over a bright sky.
- The Tab glance, today (rebuilt from the code's own sizes, labelled as such) and proposed.
- The Hall's close-up pushed further, over the real shot.
- The kit: every shared part on one sheet.
- Direction A and direction C as compact studies.

The prototype (`proto/index.html`) is isolated HTML. It is not game code. What works in it: the six places, focus by click or Tab,
Track, Set aside and Undo, choosing a set and seeing the line-up (a real Hall screenshot), placing and confirming points, picking
a room, putting a power on a key, the Journal's index, Esc as one step back, K to shut and open. What is simulated: all data,
the hold (a click stands in for it), sound, the unfold.

---

## 5. Interaction and craft specification

### The sheet
One sheet of 1708 x 960, the Hall's (`hall.client.Stage`, promoted to shared), fitted to the window and letterboxed, so the
band no longer depends on the GUI scale. Text sizes snap to whole real pixels as the Hall's do.

| Region | Sheet rectangle | Holds |
|---|---|---|
| You | 26, 40, 104 x 70 | Level, rank, XP line |
| Rail | 26, 124, 104 wide, six nodes of 96 | The six places |
| Plate | 152, 40, 1528 x 844 | The band's glass |
| Head | plate + 40, 28; 56 tall | The place's name; chips at the right |
| Stage | plate inset 40; from 104 down | The things |
| Panel | the stage's right 440 | Words about the thing in focus |
| Hint bar | 152 to 1680, y 900, 40 tall | The keys that work now |

Smaller windows: the sheet scales as the Hall's does (seen at 1024 and 1280 wide in the Hall's own tour). Under 1024 wide the
panel drops below the stage rather than shrinking the type.

### Type (the game's own font; blackletter stays the workshops')
| Role | Sheet px per font px | Used for |
|---|---|---|
| Numeral | 6 | Level, a stat, a distance |
| Display | 5 | The place's name |
| Title | 3 (4 for the step in focus) | A thing's name |
| Body | 2 | One line about it. The Hall's body size |
| Label | 1.5, spaced capitals | Captions under numbers, lane names |

Sentence case. Plain speech. Numbers are numerals. A name never truncates with "…": it wraps to three lines on a tile or the
tile is not used.

### The word budget (new, enforced)
- On the stage: names and numbers only. No sentences.
- In the panel: one sentence of at most 14 words up front. Anything longer is a tip on the thing it explains.
- A step's `why` and a room's line are written to that length at the source (`Goals.Step.why`, `RoomType`), and a GameTest fails
  a longer one.
- The level road's lines (up to 60 words today) are cut to a name and one line; the long form moves to the Journal.

### Colour and material (existing colours, named by job)
`signal.client.Glass` stays the one palette; the Hall's copies are deleted in favour of it.
| Job | Colour | Note |
|---|---|---|
| The System, anything you can act on | EDGE 7FC3FF, HOT C4E6FF when lit | |
| Waits on you; not yet | AMBER FFCF7A | Never the only signal: a glint or a count goes with it |
| Have, ready, true | GOOD 8FF0C0 | With a tick or a full count |
| Short, danger | WARN FF8A7A | With the count |
| Home and the workshops | LANTERN F2A541 | |
| Ranks E to S | The Hall's six | One list for the band, the Hall, gates |
| Text | TITLE, TEXT, LABEL BCCDE3 | LABEL was already lifted to hold over sky |

Two materials, one grammar. **Glass** is what you wear: the band, the bag, the glance, cards. **Firelight** is where you stand:
the Hall, the anvil, the table. The material tells you which.
- The plate: 82 to 86% navy (the map's card is 78%), a hairline rim, two corner brackets, faint scan lines, light from the wrist
  corner. One plate; tiles are wells in it, not boxes on it. No glass on glass.
- Things stand on a lit floor disc under a cone from the band when brought into focus.
- Reduced transparency / the game's High Contrast: the plate goes solid and LABEL lifts to TEXT (the Hall already does this).

### The kit (shared parts, each with its states)
| Part | States | Replaces |
|---|---|---|
| Thing (a well with the real item or model) | not found (black shape), known (dim), ready (lit), yours (warm), new (glint), waits (amber rim), in focus (bright rim), pressed | Text rows, "?" boxes, numbered cells |
| Need (item, have/need, name) | met (green), short (red), tip: where it comes from | "1 of 5 materials ready", where-from sentences |
| Recipe (grid, arrow, result) | have / short per cell | "Craft it from two sticks…" |
| Number with caption; before ▸ after | | pair rows |
| Where (needle, distance, direction word) | here, elsewhere, unknown (no needle) | "About 310 blocks north-east." |
| Key cap and hint bar | plain, hold | Each screen's own hint line |
| Button | primary, plain, on (tracked), quiet, hold with a fill, disabled | |
| Chip | signal, amber, lantern, good | Chips of four kinds today |
| Tip (a name under the pointer) | | The purple tooltip |
| Panel | resting (says what the place is for in one line), in focus, loading ("Reading…"), failed ("The band couldn't read that. Close it and open it again.") | Sections |

Hit targets are at least 40 sheet pixels. Focus is a bright rim, never colour alone.

### The verbs, the same everywhere
| Verb | Mouse | Key | In the Hall today | On the map today |
|---|---|---|---|---|
| Light it, name it | hover | arrows | the lantern | hover |
| Bring it into focus | click | Tab / Shift+Tab, Enter | click, Tab (pieces) | click, Tab (gates) |
| Back one step | - | Esc | Esc | Esc |
| The view beside this one | click the rail | Q / E; 1 to 6 on the band | Q / E (wings) | (Q / E turn the map: it is a camera, and keeps them) |
| Track | button | T | "Track it", no key | T, "Set as destination" |
| Commit what can't be undone | hold | hold Space | hold | - |
| Take back | button | Z | - | - |
| Shut it | - | the key that opened it | | |

Each action as trigger, response, effect, recovery, return:
- **Track.** T or the button; the button turns amber and reads Tracked, a line says "Tracked. It stays on your screen and points
  the way."; the server pins it (`tasks_act PIN`, as today); T again untracks; the HUD card leads with it when the band shuts.
- **Set aside.** X on a step that isn't on top; the tile leaves its lane and a line offers Undo for seven seconds; `tasks_act`
  as today; Z puts it back in the same place and in focus; the lane does not reflow under the pointer until the pointer leaves.
- **Choose a set.** Click a suit: it is marked, and what it takes shows; "Choose X" commits (`CHOOSE`); the step renames itself
  and lists what that set takes; "See them worn" opens the line-up and Esc returns to the same step.
- **Place points.** Space or +; the number shows +1 in amber at once and nothing is sent; hold Enter confirms all (`spend_many`);
  Backspace takes the last back; placed points survive shutting the band, as today.
- **Put a power on a key.** Pick it; the keys it fits light; press that key or click the socket.
- **Leave and come back.** Each place remembers its focus for the session. The band opens on Status unless a link names a place
  and a thing. Esc from another screen opened from the band returns to the place and focus you left.

### Motion and sound
- Opening keeps today's unfold from the wrist; the plate lands on the sheet as the bag's does. Changing place prints the stage in
  behind the scan line (220 ms). Focus moves the rim in 120 ms; the panel prints in. Nothing reflows under the pointer.
- Every motion is interruptible; reduced motion makes each a plain cut (as today). No waiting for an animation to act.
- Sounds: the band's existing ticks only. Nothing new, nothing pitched (the no-dings rule).

### States
| | What shows | What you can do |
|---|---|---|
| New hunter, one step | One lane, one tile, the step in focus | Track |
| Nothing open | "Nothing to do this minute." and one line | Leave |
| Dense (12 open, long names) | Three tiles a lane, "+n" for the rest | Everything |
| The list hasn't arrived | The plate with "Reading…" in the panel; the rail works | Change place, shut |
| The server refuses (out of range, a stale choice) | The line under the button says why, in the server's words | Try again |
| A step finishes while you look at it | Its tile shows a tick for a second; the next step of its thread takes its place, in focus if it was | Carry on |
| In a fight | Opens; folds when hit or a hostile comes within 8 blocks | - |
| No band on the wrist | K does nothing, as today | - |
| Shaders unavailable | The same sheet, solid, no glow | Everything |
| Two players | Each sees their own; others see the content-free glow | - |

### Accessibility and adaptation
Checked by reading and by rendering, not by a user with a screen reader.
- Keyboard only: every action above has a key; the prototype's central journey was done with keys alone.
- Narrator: one plain sentence when focus lands (the band and the Hall already do this); the sentence is the panel's first line.
- Text size: Band size steps the whole sheet's type. Contrast: body text on the plate measures above 7:1 against the plate's
  darkest and lightest tint in the mock; the plate over a bright sky was rendered and read. **Not measured in game.**
- Never colour alone: counts, ticks, glints and rims carry state. Never hover alone: a click or Tab brings the same panel.
- Honours the game's own options the way the Hall does: High Contrast, Distortion Effects, Hide Lightning Flashes, Narrator.
- Long words and other languages: names wrap to three lines on a tile; the panel is 440 wide with wrapping. Right-to-left was
  not tested.

### Intelligent behaviour
Not applicable. Nothing here infers or recommends beyond the Tasks model's existing "step on top", which is unchanged.

---

## 6. Implementation and validation plan

### What it touches
- **New, shared (client):** `core.client.ui`: `Sheet` (the Hall's `Stage`, promoted), `Kit` (the parts above), `HintBar`, `Focus`.
  Built on `UiCanvas` so one drawing serves glass and firelight.
- **The band:** a new `holo.client.BandStage` screen beside `HoloStatusScreen`, which stays behind `classicBand` for one build.
  `HoloPanel`, `HoloRender`, the wrist, the timeline and the sounds are reused.
- **Places:** each module that adds sections today (`StatusSections.add`, 45 of them in 15 modules) registers things for a place
  instead. A legacy section can sit in a place's panel through an adapter until its module moves, so nothing disappears mid-way.
- **Server:** no behaviour changes. Two optional payload fields: a picture for a `Goals.Step`, and recipes for the things a step
  makes (the Hearth guide already sends them). Old clients ignore them.
- **Rules kept:** every progression system still registers its steps through `core.api.Goals` and shows on Tasks. Bosses are
  never named, shown or shaped in advance. The server stays the judge. Content-free projection for other players.

### Stages (each ends in green tests and CI tour shots; builders run no Mac tours)
0. **The proof, then stop for Euan to play.** The sheet, the kit, the shell, and Tasks. `classicBand` keeps today's band.
1. Status and Powers.
2. Hearth (and the Anchor as its second door) and the Journal wall.
3. The bag's tip, the Tab glance, cards and the Look screen take the kit.
4. The workshops: the Hall's panel and hint bar, the anvil's pips, the table's costs; then boards and books.
5. Delete the classic band, the old sections and the Hall's copy of the colours; SPEC §30, §27.13, §40.7, §52 brought up to date.

### Performance
- Budget: with the band open, no more than 0.5 ms of client work a frame and no allocation per frame (the panel's display list
  already works this way). At most about 120 item draws and one figure. Models drawn to textures once, as the Hall does.
- The risk is fill: a near full-view glass pass at 4K on Cinematic. It must cost no more than the Hall's wall does today. That
  needs one timed run on Euan's Mac in a quiet moment, with his say-so. **Not measured yet.**

### Tests and gates
- `ui_word_budget`: every registered step, room, power and find, at every state, within the budget.
- `band_stage_layout`: nothing overlaps or leaves the plate at 1708, 1280 and 1024 wide and at three Band sizes.
- `ui_keys_one_meaning`: Esc, Tab, Q/E, T and hold do what the table says in the band, the Hall, the map and the bag.
- `tasks_caps`, `tasks_slots_stay`, `holo_band_layout` and the Hall's tests keep passing unchanged.
- A parity tour: the same save, classic band and stage, every capability in section 2 reached on both.
- Gate for stage 0: Euan plays it. Gate for deleting the classic band: one build after he has.

### Rollback
`classicBand = true` brings today's band back at any stage. No save data changes, so there is nothing to migrate back.

### A critical review, from four seats (structured, not user testing)
- **A new hunter:** one thing in focus and its recipe drawn is easier than 76 words. Risk: lanes with one tile look empty at
  first. Answer: lanes that haven't opened are not drawn at all.
- **A frequent expert:** twelve steps on one view and Tab through them is faster than six tabs. Risk: Tab's meaning changes.
  Answer: Q/E and the numbers carry the old habit; the hint bar always shows both.
- **Someone who needs contrast, keys or a narrator:** better than today in keys, the same in narration. Risk: black shapes on
  dark glass. Answer: a hairline round every unfound shape; solid plate under High Contrast.
- **The engineer:** the risk is fifteen modules moving at once. Answer: the adapter, the setting, and stages that each stand.

---

## 7. Open decisions (only what needs Euan)

1. **The direction.** B, one stage (recommended), A, or C.
2. **"The tab menu".** I read it as the panel you get while holding Tab, and redesigned that. If you meant the tabs inside K, they
   are gone in B either way. Say if you meant something else.
3. **One word and one set of keys.** Track everywhere (Pin stays the bag's word), T for it; Tab becomes "the next thing" and Q/E
   step through the six places.
4. **One Hearth, two doors.** Your Anchor opens the same Hearth place the band does, with building the next tier added, and the
   separate guide window goes.
5. **Creatures as themselves in the Journal.** Try drawing found creatures as their own models; fall back to their eggs if it
   costs frames.
6. **Stage 0 first, then you play.** Tasks and the shell only, the old band one setting away, before anything else is built.
7. **One timed run on your Mac.** The stage against today's band at Cinematic on your 4K screen, when the Mac is quiet. It
   launches the game client, so it needs your OK.

Still uncertain after this plan: the real frame cost; how the black-shape rule feels for creatures (it may tease too much or too
little); whether the three-line tile holds in other languages; everything about sound and feel, which no mock can show.
