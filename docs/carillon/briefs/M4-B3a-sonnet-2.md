# M4-B3a · Sonnet 2: the roster grows to ~450 and gathers where life is

You are a Sonnet subagent of the M4-B3a street-life builder. You change the citizens' roster generator
(`tools/city/citizens.py` → `src/main/resources/data/awaken/city/citizens.json`), its lines file if needed
(`tools/city/lines.py`), and the citizens' skin generator (`tools/art/specs/citizens.py`). You do not build, run Gradle,
run tours or touch Java: the builder changes the Java reader to the format below.

## Where you work
- Worktree `/Volumes/MEDIA/Awaken-m4-b3a` only (branch carillon/m4-b3a). Start every Bash command with
  `cd /Volumes/MEDIA/Awaken-m4-b3a &&`; absolute paths in file tools. Never touch /Volumes/MEDIA/Awaken,
  /Volumes/MEDIA/Awaken-play, /Volumes/MEDIA/Awaken-carillon or another worktree.
- Never run Gradle, `tools/gradle.sh`, `slot.sh` or the game. Python 3 + numpy + PIL (`python3 -I ...`).
- Only write: `tools/city/citizens.py`, `tools/city/lines.py`, `tools/art/specs/citizens.py`, `citizens.json`, and the
  textures that generator writes under `src/main/resources/assets/awaken/textures/entity/citizen/` and
  `.../entity/townsfolk/kestrel.png`, `.../townsfolk/bram.png`. Never edit `tools/city/plan.py`, `tools/city/props.py`
  or `plan.json` (the builder owns the plan; it is already regenerated with the street-life props). Do NOT commit and
  never run git add/commit/reset/checkout/stash.

## Read first
1. `docs/carillon/milestones/M3-alive.md` (what the roster is), the docstring and structure of `tools/city/citizens.py`.
2. `docs/carillon/m4/look-bible.md` §7, last bullet ("Citizens cluster where life is").
3. `plan.json`'s new fields: `propFields` = [kind, x0, z0, x1, z1, ry, facing, v, s] and `props` (written by
   `tools/city/props.py`: read its docstring and the painter rules there). Prop kinds: `stall`, `bench`, `well`,
   `fountain`, `tavern_table`, `cart`, `hay_cart`, `stack`, `display`, `planter`, `notice`, `heap`, `net`, `line`. `ry` is
   the ground a prop stands on (a line's is its rope row). For a `line`, only its pole cells stand on the ground: the
   end cell at x0/z0 when facing contains "a", the end at x1/z1 when it contains "b". The landmark `stalls` is gone:
   the square's stalls are props now.

## What to build

### 1. Places from the props (the street life is where people gather)
- **Stalls** (`stall` props, in plan order; 34 of them: 24 on Choir Square's -2 ring, the rest on district plazas).
  Each is 4 along its counter × 2 deep, `facing` its customers. Along the front edge (side `facing`), cells 0..3; the
  front row of the box (depth 0) has posts at 0 and 3 and the counter at 1 and 2; the back row (depth 1) has posts at 0
  and 3 and the keeper's two cells at 1 and 2. `stall_keeper` place: the two keeper cells, yaw facing the counter (the
  `facing` direction). `stall_front` place: the 4 cells just outside the front row, yaw facing the stall. The stall's
  good is the prop's `s`, its colour the prop's `v`. Output each stall as
  `{"part": <index of the prop in plan.props>, "box": [x0, z0, x1, z1], "facing": "N|E|S|W", "colour": v, "keeper":
  <place>, "front": <place>, "good": s}` (the Java reads exactly these keys).
- **Benches** → `sit` places: cap 2, the bench's own two cells (a sitter stands on the bench), yaw the bench's facing.
  Keep M3's wall-row sit places only where no bench is within 20.
- **Wells** (3×3) → `stand` places, cap 3, cells round the ring facing the well's middle.
- **Fountains** (5×5) → `sit` places, cap 4, cells just outside the rim facing in.
- **Tavern tables** → each tavern's `tavern_out` place gets the table's four bench cells (yaw toward the table) plus the
  two cells beside the door it has today (cap 6). Taverns without a table keep today's place.
- **Notice boards** → `stand`, cap 2, the cells in front (yaw facing the board).
- **The square's steps**: 3–4 `sit` places of cap 3 on the bowl's ring steps in the south half (stair cells of the
  -1 and -2 rings, yaw toward the Bell Stone / the Choir), so a dozen sit in the bowl at noon.
- **Every street-level prop cell** (all kinds except `line`; for a `line` only its pole cells) is an obstacle: no
  place's spot stands in one (except the spots defined above that are on them), and `edge_lanes` treats each prop cell
  centre as an obstacle like a lamp post (the lane offset shrinks until it is 0.8 clear). Checks: no spot inside a prop
  cell except those, no keep-right lane line within 0.8 of a prop cell centre.

### 2. The roster: ~450 records (SPEC §16.5 asks 400–480), clustered
Grow `make_roster` (counts are a guide; keep the checks green and the total in 430–470):
lamplighter ⌈lamps / 10⌉ (the plan has ~200 lamps now; every lamp must be on a round and lit in the dusk window, as
M3's check demands), bellringer 7, porter 26, stallkeeper one opener per stall + 14 helpers on the square's stalls,
baker 10, smith 8, watch 32 (more night watch), crier 7, boatman 10, washer 14, mason 12, runner 10, shopkeeper 30,
tavernkeeper 14 + 14 tavern hands (the same job, a second role: at the tavern's table place from 11500 to 17000),
clerk 10, archivist 2, healer 4, miller 4, child ~40, resident ~150, plus the two named records below.

Residents and children are the crowd. Give them days that put them where life is, so the market fills by day, the
taverns by night and Bellwatch's courts at dusk (bible §7):
- **market-goers** (about half the residents): home → a wander over the Market's stall fronts, square benches and
  fountains (a new errand-like route through those places, several routes so they spread) from ~1500 to ~6500 with a
  sit at noon (square benches, fountain rims, the bowl's steps) → errands → a tavern 12000–15500 → home;
- **neighbours** (about a third): a well or bench near home mornings and at dusk (10500–12500), the market at midday,
  some evenings at the nearest tavern;
- **elders** (the rest): long sits on a bench near home or in the square, a short walk, home early;
- **children**: play at the fountains, the square's floor and the district plazas by day (M3's play places plus the
  fountains and wells), home at dusk.
- Spread the homes over all districts (M3 left Bellwatch, the Garden and the Wallring nearly empty); the clustering
  comes from where they go, not where they live.
- Keep M3's checks (reach, feasibility worst case, caps, structure, lamps) and add: every stall has its opener; every
  sit/stand place from props is used by someone; at noon (tod 6000) at least 120 records are in the Market district and
  at 20:00 (tod 14000) at least 70 records stand at tavern places; print those two numbers in the stats.

### 3. Two named citizens (SPEC §16, story.md: their voices are B6's)
- **`kestrel`**, Kestrel, the Roostmaster of the Runners' Guild: lives and works at the Runners' Roost (the landmark
  `roost`): by day (600–12500) she stands at a `stand` place of cap 1 on the Roost's terrace side / its street door (the
  nearest standing cell to the Roost's landmark node), at home (inside the Roost) otherwise. Job `runner`, named
  "kestrel", given "Kestrel", family "" (as Oren and the others are named).
- **`bram`**, Bram, a Hunter Association officer: job `clerk`, named "bram", given "Bram", works inside the Association
  (the landmark `association`) by day with a spell at the Association's forecourt notice board place, lives in a house
  near the Terrace.
- Their skins: add two looks to `tools/art/specs/citizens.py` written to `textures/entity/townsfolk/kestrel.png` and
  `bram.png` (64×64 player-layout skins like the others): Kestrel a wiry runner in her fifties, weathered, short grey hair,
  a dark leather runner's jerkin with the guild's rust-and-cream sash, wrapped forearms; Bram a tall, tidy Association
  officer in navy and bronze with a high collar and a ledger satchel, short dark beard. Look at
  `docs/carillon/milestones/m3/citizens_front.jpg` for the style.

### 4. Skins for everyone, and blinks
- Regenerate the citizens' skins for the whole new roster (`cNNN.png` for every citizen record, `tK.png` for the
  thieves) with `tools/art/specs/citizens.py` as M3 did (`python3 -I tools/art/build_all.py citizens` or its own
  command; read its docstring).
- New: a closed-eyes copy of every skin, `cNNN_blink.png`, `tK_blink.png`, `kestrel_blink.png` (beside the townsfolk
  skin) and `bram_blink.png`: the same image with the eyes shut: the eye pixels (the face's eye row, y 4 of the head's
  front face, as `face_features` paints it) painted with the lid colour (the skin tone of the row above, one step darker)
  and a 1 px darker lash line along their bottom; nothing else changes. The renderer draws it while a citizen blinks.

### 5. Hand back
Run `python3 -I tools/city/citizens.py check` until every check passes, then generate (`python3 -I tools/city/citizens.py`
and `... preview`). Report: the counts by job and district (homes, and where people are at 06:00, 12:00, 18:00 and 22:00),
the noon-Market and 20:00-tavern numbers, the places added by kind, any check you had to relax and why, the
`citizens.json` keys you changed or added (the Java reader is updated by the builder from your report), and every file
you wrote.
