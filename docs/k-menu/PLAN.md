# The K menu: the band's pages (design plan, 2026-10-26)

Euan's ask (2026-10-26): take the Atlas out of K or make it the map, merge the Gate Map and the Atlas into one, put the
Journal right under status and stats ("you have to scroll down a ton just to get to your stats"), and make the whole K
menu look far better: "it just feels like a dashboard... I want it to look really, really cool."

This is the design phase only. Nothing in the game has changed. Mockups were drawn by a small renderer that reproduces
the holo panel from the code's own numbers (panel units, 5 framebuffer px a unit, the game's own font, the glass, tint,
halo and "text as light"), over real tour frames, at the real pixel size of Euan's MacBook. Shot `00_calibration.jpg`
puts the renderer beside the real game with the same content: the layout matches glyph for glyph, so the "after" shots
can be trusted as the real size and density. The renderer lives in `docs/k-menu/mock/` (it reads Minecraft's font from
the local `.ref` folder, which is never committed).

Shots are in `docs/k-menu/shots/`; the approval page is `docs/k-menu/page.html`, published at
https://claude.ai/artifact/49cQxv5y3CzXcJ3SG4jDN9 .

---

## 1. Diagnosis and thesis

### Who opens K, and why
- **First-time player** (just out of the Registry): "what is this band, what am I, what do I do now?"
- **Returning player** (most opens): a glance. Did I level? Do I have points? What's new? Where's the gate I heard of?
- **Power player** (level 40+): spends points, swaps powers between fights, checks gate timers, reads the log.

Main tasks, most frequent first: see level and progress, spend stat points, see what's new (the Journal), see what to do
next, change powers, find a gate or a place, read the Codex and Truths.

### What the K menu is today (from the code and the 26 Oct tour shots)
- One holo panel, 272×228 units, 1360×1140 px on the MacBook (5 px a unit; 6 on the 4K Dell). Because it is narrower
  than `StatusLayout.SIDE_TABS_MIN_W` (440), the tabs sit on top and the content window is **246×192 units, about 17
  lines**.
- Four tabs: Status, Truths, Codex, Gate Map.
- **The Status tab stacks 22 sections** from 11 modules, one column, each with a loud blue title, in this order:
  NOW, THIS ROOM, **ATLAS** (a 246×143 picture), WAITING FOR YOU, **JOURNAL**, STATUS, SAVED FOR LATER, **STATS**,
  THE ROAD, DIRECTIVES, DAILY REGIMEN, THE ASCENT, HEARTH, ANCHOR REQUESTS, JOURNAL · CHAINS, CRAFTS, **POWERS**,
  THE VEIL, THE BODY, DEEP PACK, RECORDS, RECENT MESSAGES.
- The Gate Map tab is a 150-unit radar of dots plus a list; the Atlas (M) is a full-screen voxel diorama of the same
  world. Two maps, two looks, two sets of marks.

### Baseline (task → steps → friction → consequence)
Observed means seen in the code or the shots; hypothesis means likely but not measured with a player.

| Task | Steps today | Friction | Consequence |
|---|---|---|---|
| See my stats | K, scroll past NOW, the Atlas picture, the Journal, STATUS, SAVED FOR LATER | Stats start **365-650 units down: 1.9 to 3.4 screens, 21 to 36 wheel notches** (18 units a notch), depending on how full the Journal is (observed, from the sections' layout code) | The first screen is the Atlas picture (CI shot 02/06). Stats feel buried (Euan's words) |
| Spend points | Scroll to STATS, click + per point | Each click spends at once; no undo (observed) | A misclick is permanent (hypothesis: rare but felt) |
| Change powers | Scroll to POWERS | **1,000–1,350 units down: 5 to 7 screens** (observed, same method) | The most hands-on part of K is the hardest to reach |
| What's new | The Journal is near the top | Fine, but it sits above who you are (observed) | The order reads as a feed, not as a sheet about you |
| Find a gate | Gate Map tab: dots on a radar; or M: the Atlas, which has no gates (observed) | Two maps; gates aren't on the good one | "Merge them" (Euan) |
| Know what's what | 22 sections with equal-weight blue titles, one size of text, mostly lines of text (observed) | Nothing leads; everything is a row | "Feels like a dashboard" (Euan) |
| Read it over bright sky | 38% tint behind text | Labels (#8FA7C4) measure **2.6:1** over overcast sky in the real `read_sky` shot (observed); white text 6.4:1 | Labels and hints wash out by day |

### What makes it a dashboard
A dashboard is many independent widgets of equal weight, stacked, each in its own idiom, with nothing leading. That is
exactly the Status tab: 22 blocks, all titled the same way, all the same text size, in one long column. The panel also
treats its content as print on a slab (text rows) rather than light on glass, so the holo look stops at the frame.

### Thesis
**The band is the System's page about you: who you are now, and what's next for you.** It should read like a
character sheet in a game, not a feed:
- **What commands attention:** your rank, your level and how far to the next, your six stats, and anything that waits
  on you (points to spend, a decision, a gate breaking).
- **What sits close by:** what just happened (the Journal) and the one thing you're doing (the tracked task).
- **What recedes until asked:** everything else, each on its own page, one keypress away: powers, the long list of
  goals and records, truths, the codex, the map.
- **One page, one job, no scrolling for the main job.** Status fits on one screen. Powers puts your six keys first.
  The Map is the Atlas.
- **Light, not print.** Hierarchy comes from scale, light and position, not from boxes and titles.

---

## 2. Keep, change, consolidate, remove (every capability traced)

Pages in the recommendation: **Status · Powers · Journal · Truths · Codex · Map**.

| Today (tab › section, order) | Goes to | How |
|---|---|---|
| Status › STATUS (Spine: name, rank, title, path, level, XP, to next, next rank, "C opens…", fractured, today) | **Status** hero | Kept, recomposed: face, name, title, LEVEL 14 large, XP bar, "Level 15 opens The Veil"; the rank as a sigil with "C at level 25" under it; focusing the sigil shows what the next rank opens (revealed in context); Fractured moves to the Stats header (it weakens stats); "Today: …" shows on the stats' focus line when nothing else needs it |
| Status › STATS (+ per stat) | **Status**, one line of six | Kept; spending becomes place then confirm (section 5) |
| Status › JOURNAL (filed lines, −10) | **Status**, right under the stats (newest 3-4) + **Journal** page (all) | Relocated below the stats, as Euan asked |
| Status › NOW (−30) | Absorbed | Its lines already have homes: the tracked task with its compass is the Journal's first row; "Spend 3 points" is the points chip; "Level 15: …" is the hero's road line. The full list is on the Journal page |
| Status › WAITING FOR YOU (decisions) | **Status** context row (first priority) + Journal page | A waiting decision takes the row above the Journal, with its Y and N keys |
| Status › THIS ROOM (hearth) | **Status** context row | Shows only while you stand in a room of your Hearth |
| Status › DEEP PACK (descent) | **Status** context row | While in the Descent; its key opens the 27 slots in place |
| Status › ATLAS (the glance) | **Removed** | The Map page is the Atlas itself. Saves ~187 units of scroll and a 480×280 texture painted up to once a second |
| Status › SAVED FOR LATER (promises) | **Journal** page + Map legend "Saved" | Kept |
| Status › THE ROAD | **Truths** page ("how you grow") + hero line | Next unlock on the hero; the road list beside Truths, Titles and Latents |
| Status › DIRECTIVES | **Journal** › Also on; an offered one is a decision (context row) | Kept |
| Status › DAILY REGIMEN | **Journal** › Also on | Kept |
| Status › JOURNAL · CHAINS (living) | **Journal** › Tracking + Also on | The tracked chain with its steps; the same data as the progression builder's top-right task card |
| Status › HEARTH, ANCHOR REQUESTS | **Journal** › Home | Kept |
| Status › POWERS | **Powers** page | Kept; the six keys become the page's hero |
| Status › THE ASCENT, THE BODY | **Powers** › Movement | Kept (what you can do) |
| Status › CRAFTS (living) | **Powers** › Crafts | Kept |
| Status › THE VEIL | **Powers** › The Veil | Kept |
| Status › RECORDS | **Journal** › Records (end) | Kept |
| Status › RECENT MESSAGES | **Journal** log (filter "Messages") | Consolidated with the filed lines: both are "what the System said" |
| Truths tab (path choice, truths, titles, latents) | **Truths** page | Kept, restyled; gains The Road |
| Codex tab (the board, happenings witnessed) | **Codex** page | Kept, restyled |
| Gate Map tab › GATE MAP (radar, list, run summary, View Satchel) | **Map** page (gates as pillars + a Gates list); the run → **Status** context row | Consolidated: one map |
| Gate Map tab › GATE SCANNER · LAST READING (above) | **Map** page (pins marked "last reading") | Consolidated |
| Atlas on M (diorama, legend, pins, share, clear cache) | **Map** page | M opens the band on its Map page. "Share with party" shows only in a party; "Clear Atlas cache" moves to the settings screen (upkeep, not play) |
| "K to close" | A K keycap at the foot of the rail | Kept |

Nothing a player can do today is lost; one thing is removed (the glance), because the map it previews is one key away.

---

## 3. Directions

References, and what each one solves (sources at the end; "from play" marks things I know from the games, not from a
cited source):
- **Solo Leveling's status window** (the System AWAKEN is built on): the status window is short. Name, level, job,
  title, a list of stats and the points left to spend. Nothing else. The page about you is small.
- **Destiny 2's character screen:** your Guardian and your loadout are the screen, with the details of whatever the
  cursor rests on shown beside it. The cursor carries the layout, and small animated glints mark what's new
  (GamesRadar on its menus; Hud & Guis on the cursor and glints). We take: one hero composition, details on focus.
- **Death Stranding:** a soft blue scheme with minimal animation and long, thin lines of information (Fonts In Use),
  and a map that turns topographic for planning a route (PlayStation Blog). From play: Sam's map is a 3D hologram you
  plan on. We take: thin light lines instead of boxes; the map is a projected model.
- **Breath of the Wild:** the Sheikah Slate is a device with pages (map, inventory, compendium) you flip between, and
  the HUD only shows what is needed (analysis cited below). We take: pages on a device; the map is its own mode.
- **Elden Ring** (from play): leveling at a Site of Grace places points first, previews the result, then asks you to
  confirm. We take: place, preview, confirm.
- **Persona 5:** the menus treat interface as expression, and each menu gets its own look; the same stylised type was
  called "impossible to read at first" by its own director and a readability problem by critics. We take: each page
  composed for its own job; we refuse: style that costs legibility.
- **Cyberpunk 2077:** a quest journal with a tracked quest; players called the interface cluttered and its text hard to
  size (Steam threads, Kotaku on subtitles). A warning about density and small text.
- **Crimson Desert** (2026): reviews called its launch UI clunky; patches cut clutter and added options to hide HUD
  parts; players asked for larger text and better contrast (GamingBolt). The lesson is the same as Cyberpunk's: readable
  size and contrast first.

### A. Reorder and refine (restrained) — `12_direction_a.jpg`
Same panel and top tabs (Status · Truths · Codex · Map). The Status stack becomes STATUS, STATS, JOURNAL, NOW, then the
rest; the Atlas glance goes; the Map tab opens the Atlas; quiet spaced labels replace the loud blue titles.
- For: smallest change (about one builder hand-back), every module keeps its section, nothing to learn.
- Against: still one long feed of 21 sections; the Journal is below the first screen; Powers is still 4 to 6 screens down;
  it would still read as a dashboard, only tidier.

### B. The band's pages (structural) — **recommended** — `02`–`11`
The rail of pages runs down the panel's wrist edge, like the band's own nodes. Six pages, each composed for its job.
Status is one screen: you, your stats, then the Journal. Powers leads with your six keys. The Map page is the Atlas
with the gates on it, and M opens it.
- For: does everything Euan asked, and fixes the reason it feels like a dashboard (one hero per page, no feed); the
  main tasks take zero scrolling; it keeps the band's look and choreography and builds on what exists (StatusSections,
  HoloPanel, the Atlas).
- Against: six pages instead of four to learn (mitigated: labelled rail, keys 1-6, K always opens on Status); every
  module re-registers its section to a page (one line each); more build than A (about four builder hand-backs).

### C. The Orrery (ambitious) — `13_direction_c.jpg`
The same pages as separate panes arranged in an arc round the wrist; the one you look at comes forward, the others
turn away into the world; the map is a hologram diorama hovering over the forearm.
- For: the most "magical" moment of the three; strong sense of a device in the world.
- Against: several panels drawn at once (each with its own text records: more frame time), panes over the world hurt
  readability, more picking and spring code, and the satellites mostly show blurred content you can't use. About 2-3×
  the build of B, with more risk. B's page model is the same model C needs, so C can come later as a presentation of
  B's pages if B lands well.

**Recommendation: B.** It is the smallest change that removes the dashboard (structure, not paint), it puts stats,
then the Journal, on the first screen, and it makes the two maps one.

---

## 4. The visual system (B)

### Composition and density
- Panel **288×228 units** (was 272×228): 1440×1140 px at 5 px a unit on the MacBook (2880×1800 and 3024×1964), 1728×1368
  at 6 on the 4K Dell. `HoloPanel.choosePpu` keeps the same px per unit on both (checked: 4.5 → 5, 6.0 → 6). The right
  edge moves about 70 px past the screen's centre while K is open (the crosshair is already hidden then).
- **Rail** 30 units on the wrist edge: six pages, a 12×12 pixel icon and a spaced label each, badges for points
  (amber) and unread (light), the K key at its foot.
- **Content** x 40–278 (238 units), 10-unit page padding.
- **Status page, top to bottom (no scroll):** hero (face, name, title, LEVEL 14, XP bar, next unlock, rank sigil) ·
  STATS (one line of six, the points chip, a focus line that explains the stat under the cursor) · JOURNAL (the context
  row, then the newest three or four entries).
- One hero per page: the level and the rank on Status, the six keys on Powers, the tracked task on Journal, the land on
  Map.

### Typography and language
Minecraft's own font, always on whole framebuffer pixels (crisp, native, localisable). Sizes are font-pixel multiples
tied to px per unit (ppu):

| Role | Scale | At 5 px a unit | At 6 | Use |
|---|---|---|---|---|
| Display | 2 | 10 px a font pixel | 12 | level, stat values, rank letter |
| Body | 1 | 5 | 6 | names, entry titles, buttons |
| Small | (ppu−1)/ppu | 4 | 5 | second lines, hints, distances |
| Micro | max(2, ppu−2)/ppu, +1 font px tracking, capitals | 3 | 4 | labels, rail, chips |

Line heights 18 / 11 / 9 / 7 units. Never under 2 framebuffer px a font pixel (1080p: micro clamps to 2).
Language: plain, as a person would say it (memory `plain-dialogue`): "Level 15 opens The Veil", "Press + on a stat to
raise it, then confirm", "Nothing kept yet. What you find past the city walls, and what the System tells you, will be
kept here." Labels are nouns ("STATS", "JOURNAL"), never slogans.

### Colour, light, materials, depth
- **Roles:** the System's blue is structure (rail, rules, focus); white is you (values, names); **amber means it
  waits on you** (points, pending, a timer running out); green is done or true; red is danger; violet the uncanny;
  rank colours only on rank things. Each role always also has a shape or word (◆ new, ◇ read, ✓ done, ▸ current,
  ! danger, the rank letter), so colour is never alone.
- **Glass** unchanged (38% to 50% toward the wrist, scan lines, shimmer, hairline, brackets, halo): it is AWAKEN's
  identity.
- **Content tint 0.46** (was 0.38), and **adaptive** up to 0.60 when the scene behind the panel is bright (mean
  luminance over 0.35, sampled from a 16×16 downsample every 10 frames): the glass darkens like a photochromic lens in
  bright sky and stays sheer at night.
- **Light comes from the wrist:** a soft additive glow from the lower-left corner where the beams meet the glass (13%,
  190 units), so the panel reads as projected, not printed.
- **The active page leaks light:** its rail node glows and a faint band of its light runs into the page.
- **Depth** only where it means something: the rank sigil sits in a faint field of its own concentric light; the
  focused stat has a column of light behind it. No drop shadows (text is light).

### Components
Rail node · section label (micro capitals + a hairline to the edge + an optional right chip or link) · segmented bar
(3-unit segments, 1-unit gaps, the partial segment half lit) · stat column (label, display value, a diamond "+" node
when points wait) · focus line · context row (a 2-unit light bar, title, a second line, a compass or keycaps) ·
journal row (◆/◇, title, age, one line) · keycap (the player's own binding) · glass key button (hairline rim, label,
keycap) · focus ticks (four 4-unit corner ticks) · rank sigil (pixel diamond with the letter in light) · gate pillar
(a beam of the gate's rank colour through the cloud, its name hung at the top).

### Iconography and imagery
- Page icons are 12×12 pixel glyphs drawn one unit a pixel (person, bolt, open book, four-point star, cards, map pin):
  native pixel art, one colour, lit when active.
- Power icons are AWAKEN's own 32×32 sprites.
- **Your face** on the Status page: the 8×8 face of your own skin (with the hat layer), 4 units a pixel, framed by your
  rank colour. It makes the sheet yours. (The mockup uses an 8×8 face drawn for it, not a Mojang skin.)

### Motion (interruptible; reduced-motion alternatives)
Rule: **motion reports change.** Nothing animates on every open; only what changed since you last looked.

| Moment | Timing | Reduced motion |
|---|---|---|
| Open / close | unchanged (450 ms / 250 ms band choreography); content types in hero 300–360 ms, stat columns 340–420 (12 ms apart), journal 400–450 | fade in place (unchanged rule) |
| XP gained since last look | segments fill left to right, 250 ms | jump |
| New journal entries | print in behind a line, 120 ms; ◆ breathes (0.6–1.0, 2.4 s) until seen | appear, no breathe |
| Page change | rail light travels 0–120 ms; old page dims 0–60 ms; the new page prints top to bottom behind a scan line 40–180 ms (`05`, `06`) | 120 ms crossfade |
| Focus | rim and column light 90 ms ease-out | instant |
| Place a point | value turns amber and ticks, a "+N" appears, 80 ms | instant |
| Confirm | values flash amber 200 ms; the chip dissolves 150 ms; "You grew" prints into the Journal | instant |
| Map | the panel grows into the table 300 ms (the inventory's landing, at map size); back 250 ms | crossfade |
| Rank sigil idle | a glint runs round the diamond every 6 s | off |

Any input during a transition finishes it at once and starts the next (no queue), like K while opening.

### Sound (one glass voice, D major pentatonic, UI loudness −24 ± 3 dBFS, Interface slider)
New: `holo.page` (a soft glass sweep A5→D6, 0.16, at the scan's start), `holo.spend` (one note per placed point,
rising D5 E5 F♯5 A5 B5 D6, 0.2), `holo.confirm` (D5 A5 D6 with a shimmer, 0.3), `holo.undo` (B5→F♯5, 0.16). Reused:
`holo.hover` (rail nodes, stats, rows; at most every 55 ms), `holo.open`, `holo.close`, `holo.open_quick` (pitched down
for the map). No information is only in a sound.

### Tokens
| Token | Value |
|---|---|
| panel | 288 × 228 units; rail 30; content x 40–278; padding 10; label-to-content 9; block gap 6; all positions whole units |
| type | display 2.0 · body 1.0 · small (ppu−1)/ppu · micro max(2, ppu−2)/ppu, tracking +1 font px |
| line height | 18 / 11 / 9 / 7 units |
| ink.hi / ink / ink.dim / ink.faint | #FFFFFF / #D9E4F2 / **#BCCDE3** (was #8FA7C4) / #6F86A6 (decoration only) |
| signal / signal.hot | #7FC3FF / #C4E6FF |
| amber / lantern / good / warn / violet / horizon | #FFCF7A / #F2A541 / #8FF0C0 / #FF8A7A / #B48CFF / #9FE6D6 |
| rank E D C B A S | #F4F6FB #5FD08F #6FB3FF #B48CFF #F2A541 #FF5A6A |
| glass | #16233A → #0F1724, α 0.38 → 0.50 (unchanged) |
| content tint | #05080F α 0.46, adaptive to 0.60 over bright backdrops, feathered 14 units |
| wrist light | additive #7FC3FF 13%, radius 190 units from the lower-left corner |
| scan lines | every 3 units, #7FC3FF 3.5% |
| segmented bar | 3-unit segments, 1-unit gap, 3 units tall |
| context row / journal row | 21 / 20 units |
| keycap | 9 units tall, label micro |
| motion | page 180 ms · focus 90 ms · place 80 ms · confirm 200 ms · map 300 ms in / 250 ms out |

### Measured contrast (WCAG ratio against the backdrop's 95th-percentile luminance)
| Backdrop | white | ink | ink.dim (new) | label (old) |
|---|---|---|---|---|
| Real game, today's tint, overcast sky (`read_sky`) | 6.4 | 5.0 | 3.5 | **2.6** |
| Mockup, tint 0.46, white cloud | 9.0 | 7.0 | 4.9 | 3.6 |
| Mockup, grass | 13.5 | 10.5 | 7.3 | 5.5 |
| Mockup, night | 15.9 | 12.3 | 8.6 | 6.4 |
My glass renders a little darker than the real shader over sky, so the mockup's sky numbers flatter it. That is why
the plan adds the adaptive tint and the brighter label colour: with both, labels reach about 4.7:1 even over the real
overcast backdrop (estimate: tint 0.50 there, labels #BCCDE3). The builder verifies this on a CI tour over bright sky.

---

## 5. Interactions

Keys: **1–6** pages · **Tab / Shift+Tab** next / previous page · **Q / E** sub-tabs inside a page (Journal filters,
Map legend) · **arrows** move focus · **Enter / Space** activate · **Backspace** undo the last placed point ·
**M** the Map page (and closes it from the Map) · **K / Esc** close (Esc first clears a selection or a pending choice).

1. **Open.** K (band worn) → the wrist rises and the panel unfolds (unchanged) → **always on Status**, unless a link
   opens another page (a "Show me" card, M) → only what changed since you last looked animates → K again closes; K
   while opening skips to open.
2. **Change page.** Click a rail node, 1–6, Tab → the rail's light travels, the page prints in behind a scan line →
   the new page, scrolled where you left it this session → a second key mid-change finishes the first at once →
   Tab back.
3. **Read a stat.** Hover or arrow onto a column → its column lights, the focus line says what it does in one plain
   sentence → leave it and the line returns to its idle text.
4. **Spend points (place, then confirm).** + on a column, or Enter on a focused one → the value turns amber with
   "+N", a rising note, "2 of 3 points placed" with **Confirm (Enter)** and **Undo (Backspace)** (`03`) → Confirm sends
   one request; the server checks the total against your unspent points, applies them, and the values flash (`04`);
   "You grew · STR 19, AGI 24" is filed → Undo takes back the last placed point; closing K with points placed keeps
   them unspent and the next open restores the placing (nothing is lost) → focus returns to the first column.
5. **The context row** (one row above the Journal, the first that applies): a decision waiting (Y / N) → a gate run
   (floor, Satchel, kills, time; **V** opens the Satchel) → the Deep Pack in the Descent → the room you stand in at your
   Hearth → the task you track (its step and a compass). Its key acts; the Journal page holds the rest.
6. **A Journal entry.** Click or Enter → it opens in place with its full text and what you can do with it ("Show on
   map" for a place → the Map flies there; "Open in the Codex" for a find) → Esc or click again folds it → entries count
   as read once K closes after showing them (unchanged).
7. **Map.** Rail, 6 or M → the panel grows into the table (300 ms), the rail stays on its edge → drag to turn,
   right-drag to move, scroll to zoom, C back to you (unchanged); the legend's **Gates** tab lists gates nearest first
   with rank, distance and days to break; click one → the table flies to it and opens its row: "Point me there" (T)
   sets your compass on it → 1, Tab or the Status node → the table folds back into the panel (250 ms). Inside a gate
   the Map says "The Atlas can't see inside a gate" and shows the run.
8. **Powers.** Click a power → it lights amber and the keys it fits light → press Z X C V (or R, Alt), or click a
   socket → it goes on that key → Esc clears the selection first, then closes.
9. **Close.** K or Esc → the panel folds into the band (250 ms) → hands back at once (unchanged).

**Controller** (when a controller mod is in the pack; none is today): LB / RB pages, d-pad or left stick focus, A
activate / +, B undo then back then close, X the context row's action, Y confirm, right stick scrolls or turns the map.
The keyboard focus model above is the contract it maps onto.

### State matrix
| Page | Empty | Sparse | Dense | Long content | Loading | Failed |
|---|---|---|---|---|---|---|
| Status | Band not worn: K opens nothing (unchanged line). Fresh world: level 1, six 5s, "Your band is new…", the first task tracked, the empty Journal line (`11`) | 1–2 entries, no points: the focus line shows today's tally | 5 points, fractured, B-rank, a gate run in the context row, 9 new, long titles (`10`) | Name up to 16 characters, the longest title, long entries end in "…" and open in full; translations: labels ellipsize, the focus line wraps to 2 lines | First second after joining: name and dim placeholder segments until the profile arrives, then values fade in | Holo pipelines fail: the flat screen with the same pages and rail; no skin: the face shows your initial in rank colour |
| Powers | Before the first power K opens the first-power choice (unchanged) | 1–3 powers | 40+: the grid scrolls under the fixed keys | Long descriptions wrap in the card | — | Missing icon: the family glyph |
| Journal | "Nothing kept yet…" | — | 60 kept (unchanged cap), filters | Wrap | — | — |
| Map | No gates known: "You haven't seen a gate yet. Their light shows from far off: look for a pillar in the sky." | — | 9 gates: nearest first, the rest "+ 4 further out" | Long names ellipsize | Land prints in under clouds (unchanged) | Inside a gate or a roofed dimension: the Atlas's own messages (unchanged), the run card |

### Accessibility
- **Keyboard only:** every action above has a key; focus is always visible (corner ticks and a lit rim).
- **Text size:** a new client setting, **Band size** Small / Normal / Large (0.40 / 0.45 / 0.52 of the screen width),
  which steps px per unit and so every type size by whole pixels.
- **Contrast:** as measured above; ink.faint is only for things that carry no meaning (a read ◇, a disabled key).
- **Never colour alone:** see Colour roles.
- **Reduced motion:** the table in Motion; the existing `reducedMotion` setting drives it.
- **Narration:** each focused element gives the vanilla narrator one sentence ("Agility 22. Moves faster, and builds
  Flow sooner. 3 points to spend.").

---

## 6. Implementation and validation

### Effects across the app (implementation boundary)
- **holo** (owner of the panel): `HoloStatusScreen` becomes the page screen: the rail, page changes (content drawn on a
  layer moved by one transform and revealed by a scissor line, so text records aren't rebuilt per frame), panel 288
  units, the map growth (the inventory's landing, aimed at the Atlas table's rectangle), the adaptive tint in the glass
  shader, Band size, new sounds.
- **progression** (owner of `StatusSections`, SPEC §12): the API gains `Page` {STATUS, POWERS, JOURNAL, TRUTHS, CODEX,
  MAP} and `context(provider, priority)` for the context row; `Tab` stays as a deprecated alias for one release. The
  Status page's hero, stats and journal block are composed by progression (not a stack). New payload
  `StatusAction("spend", "STR:1,AGI:2")` validated server-side. The flat `StatusScreen` uses the same pages.
- **One-line re-registrations** in: powers (Powers), body (Powers › Movement), veil, living (Crafts → Powers; chains →
  Journal), signal (Journal, Now, Waiting → context, Recent → Journal log), hearth (Home → Journal; This room →
  context), descent (Deep Pack → context), progression's own sections (Road → Truths; Directives, Regimen, Saved,
  Records → Journal).
- **atlas:** `AtlasScreen` hosts the rail when opened in the band; legend tabs Gates / Lands / Saved; the glance and its
  texture removed; "Clear Atlas cache" to the settings screen.
- **gates, above:** gate beacons and scanner readings become marks on the Atlas (`MapMarks` gains a gate kind with rank
  and days to break); `GateMapSection` removed; the run summary becomes a context provider.
- **opening, tips:** Bram's line "Status, Truths, the Codex, the gate map. Same key closes it." becomes plain words for
  the new pages (for example "That's your band. Your status and stats are on the first page, and the map is on the
  last. Press K again to close it."); the `band` tip text likewise.
- **wilds:** "Show me" opens Powers › Movement (was Status scrolled to THE ASCENT).
- **autopilot:** `screen holo status|powers|journal|truths|codex|map`, `holo page <n>`.
- **Concurrent work, not touched here:** the progression builder's top-right task card (`TaskHud`, `Checklist`, SPEC
  §27.10) is the HUD half of the Journal page's Tracking card; both read the same task payload, so the band builder
  starts after it merges. The discovery builder's Journal filing (`Journal.entries/unread`, §26) is read as is.

### Build phases (one builder, Opus at extra high effort, `pack/k-menu`, one CI look tour per hand-back)
1. **Structure (Euan's literal asks, shippable alone):** pages and rail; Status order hero → stats → Journal; the
   glance removed; the Map page opens the Atlas from the band; gates on the Atlas; the flat fallback; autopilot lines.
2. **The Status sheet:** hero (face, sigil, level, segmented XP), the six columns and focus line, place-and-confirm with
   its payload, the context row and its providers.
3. **The look and motion:** tokens, type ramp, contrast (tint and adaptive), wrist light, the scan-line page change,
   motion that reports change, the four new sounds, reduced motion. One Mac perf tour.
4. **The map:** legend tabs, gate pillars through the cloud, Point me there, the growth into the table, scanner marks.
5. **Powers and Journal pages composed; Truths and Codex restyled;** narration; Band size.

### Performance risks
- Text records rebuild when a line moves: page changes animate a layer transform and a scissor, never per-line
  positions.
- No allocation per frame (the existing rule): formatted strings ("3,420 / 5,100 XP") are cached on change.
- Status draws 3 composed blocks instead of measuring and drawing up to 22 sections each frame: cheaper than today.
- The glance paint (0.23 ms, up to once a second) and its texture go away.
- The Map page costs what the Atlas costs (+1.5–2.0 ms while open, measured in M5); during the 300 ms growth both the
  panel and the table draw (+~0.3 ms).
- Budget unchanged: the holo K menu within 0.25 ms of the flat one (SPEC); the Map within the Atlas budget (≤ 2 ms).

### Validation from four perspectives
- **New player:** first K after the Registry shows the face, level 1, six stats, the first task with a compass and a
  plain empty-Journal line. Check: finds "what next" without scrolling, and Bram's line names the pages.
- **Expert (level 47):** spends 5 points in 6 inputs (+ ×5, Enter) with no scroll; puts a power on a key in 2 clicks;
  reaches the nearest gate's details in 2 inputs (6, then the row).
- **Accessibility:** contrast measured over cloud, grass and night on the CI tour; a keyboard-only pass of every page;
  a reduced-motion pass; a never-colour-alone audit of every state.
- **Engineer:** layout GameTests at 3, 4, 5, 6 and 7 px a unit (nothing overlaps, the hero fits the longest name and
  title, Status never scrolls); every registered section appears on exactly one page; the context row's priority; the
  spend payload can't spend more than you have, ignores unknown stats and is rate-limited; no per-frame allocation.

### Regression checks
The flat style (`statusStyle = FLAT`), the inventory's holo (unchanged), `holo_hover_tick` on rail nodes, the
first-power choice on K, the band off the wrist (K opens nothing), M from anywhere, "Show me" links, the Atlas reveal
after the opening, toasts held while K is open (QuietScreen).

### Release gates
CI GameTests green · one CI look tour reviewed (Status typical, dense and empty; each page; the page change frozen at
40 and 110 ms; the map; bright sky, grass and night) · a Mac perf tour within budget · Euan's yes on the look in game.

---

## 7. Decisions for Euan
1. **Direction B** (the band's pages), A (reorder only) or C (the orrery)?
2. **Six pages:** Status · Powers · Journal · Truths · Codex · Map. Are the names right?
3. **K always opens on Status** (recommended), or on the last page you used?
4. **Stat points: place, then confirm** (recommended), or spend on each click as today?
5. **The panel 6% wider** (272 → 288 units; its right edge passes the screen's centre by about 70 px while open)?
6. **M opens the band's Map page** (one map, two keys; recommended), or does M stay a separate screen?
7. **A denser tint by day** (0.46, rising to 0.60 over bright sky) so labels pass contrast: the glass looks a little
   less sheer in daylight.
8. **Your face** on the Status page, from your skin?
9. **"Clear Atlas cache"** moves to the settings screen?
10. **Build order:** phase 1 first (your literal asks, one hand-back), then 2–5?

---

### Sources
- Masayoshi Suto and Katsura Hashino on Persona's menus: https://www.dtnext.in/amp/story/edit/exuberant-video-game-menus-designed-with-da-vinci-in-mind-807823 ,
  https://www.pushsquare.com/news/2024/10/making-metaphor-refantazio-personas-immaculate-menus-is-really-annoying-says-director ,
  https://inverse.com/gaming/persona-5-tactica-user-interface
- Destiny 2's menus and cursor: https://www.gamesradar.com/destiny-2-menu , https://www.hudsandguis.com/home/2015/5/25/destiny-ui
- Death Stranding's interface and map: https://fontsinuse.com/uses/67648/death-stranding-video-game ,
  https://blog.playstation.com/2019/09/12/death-stranding-new-tgs-gameplay-video/
- Breath of the Wild's Slate and HUD: https://www.thefamicast.com/2017/12/part-2-immersive-impressionable-ui.html ,
  https://uxdesign.cc/tears-of-the-kingdom-how-nintendo-improved-and-ignored-ui-issues-843f094b14b2
- Cyberpunk 2077's text and clutter: https://kotaku.com/phew-cyberpunk-2077-will-let-you-make-its-tiny-subtitl-1844828513 ,
  https://steamcommunity.com/app/1091500/discussions/0/3383914261430242165
- Crimson Desert's UI after launch: https://gamingbolt.com/crimson-desert-is-better-than-before-yet-these-15-issues-still-hurt-it ,
  https://vulkk.com/2026/07/04/how-to-hide-the-minimap-and-hp-bar-in-crimson-desert/
- Elden Ring's level up and Solo Leveling's status window: from play and reading, not a cited source.
