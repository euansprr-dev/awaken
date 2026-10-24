# M3 Sonnet task 2 · Carillon's people and their things, as pixel art and sound (tools/art/specs/citizens.py)

You are a Sonnet subagent for the M3 (Alive) Citizens builder in the AWAKEN repo (a Minecraft mod). Work ONLY in
`/Volumes/MEDIA/Awaken-alive` (branch `carillon/m3-alive`). Start every Bash command with `cd /Volumes/MEDIA/Awaken-alive &&`
and use absolute paths. Never call EnterWorktree. Never run Gradle, never launch the game, never touch any other
worktree, never message other sessions. Python only (PIL and numpy are available). Commit your own files when done
(explicit `git add <paths>`, never stash/reset/checkout), message ending with
`Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Do not push. Another agent edits Java at the same time:
never touch `src/main/java`.

The art direction below is the builder's (Opus) decision. Follow it closely; where it leaves room, choose in its spirit
and note what you chose in the Result section.

## The bar
Euan, the owner, judges Awaken against the best modpacks: anything that looks like vanilla Minecraft with an add-on is
a failure. Carillon is a walled city of pale stone, bronze trim and copper roofs, built round bells. Its people must
read as *one town* (a shared costume language: bronze buttons and bell motifs, warm wool, leather, linen) and as *240
different people* (faces, hair, build, palette). Each job must be recognisable from 20 blocks by silhouette and
colour alone. The existing bar for a painted skin is `tools/art/specs/hearth.py` (`oren()`, `mira()` and the helpers
`tex`, `hair`, `standard_head`, `make_skin`, `finish_skin`, `PARTS`): cloth with fold lines and specks, shaded ramps,
edges on aprons, faces with eye whites and a mouth. Read it first and reuse its helpers (import them, don't copy).
Also read `tools/art/pixelart.py` (Canvas, RAMPS, hex_rgba, Out) and `tools/art/build_all.py`.

## What to make
One spec, `tools/art/specs/citizens.py`, with `build(out)` (so `python3 tools/art/build_all.py citizens` runs it) and
`python3 tools/art/specs/citizens.py preview DIR` for contact sheets. It reads
`src/main/resources/data/awaken/city/citizens.json` (already generated; see `docs/carillon/briefs/M3-sonnet-1.md` for
its format: `citizens.rows` with fields `job`, `build`, `age`, `look` = [tone, hairStyle, hairColour, outfit, accent,
face], `named`, `household`; `extras` = 8 thieves) and writes:

### 1. Skins: `textures/entity/citizen/c000.png` … `c239.png` and `t0.png` … `t7.png`
64x64 player-format skins (classic layout: base parts opaque, overlay parts (hat, jacket, sleeves, pants) transparent
where unused; never partial alpha). `build` 1 = slim (Alex, 3-pixel arms: paint arms 3 wide, the 4th column of the
arm faces stays transparent as in vanilla slim skins), 0 = wide. Skip the 4 named citizens (`named` non-empty: they
have hand-made skins already) but still write nothing for them.

Compose each skin from seeded layers, deterministically from the citizen's `look` and id:
- **Base**: skin tone (6 tones from deep brown to fair, each a 5-step ramp), the face (`face` 0-7: eye colour and
  eye shape, brows, mouth, and for some: freckles, a scar, stubble, a beard, rosy cheeks, spectacles for clerks and
  some elders). Old citizens (age 3): lines at the eyes, greyer brows. Children (age 0): rounder face, bigger eyes,
  no beards.
- **Hair** (`hairStyle` 0-11): short crop, side part, long straight, long tied back, bun, braid over one shoulder,
  curly, bald (with a fringe of hair at the sides for old men), buzz, ponytail, bob, wavy. Hair uses the hat layer for
  volume where it stands proud (curls, a bun, a braid down the back). `hairColour` 0-7: black, dark brown, chestnut,
  auburn, copper red, blonde, grey, white.
- **Clothes by job** (`outfit` 0-3 picks the palette variant; `accent` 0-3 picks the small detail or the headwear
  where the job has a choice). The silhouettes and palettes:
  - **lamplighter**: a long charcoal-blue greatcoat to mid-shin (coat tails painted on the upper legs' pants layer),
    two rows of brass buttons, an amber or ochre scarf, a short-brimmed cap; soot on one sleeve.
  - **bellringer**: a heavy undyed brown-grey robe to the shins with a rope belt, a bronze bell emblem on the chest,
    a hood down on the shoulders (accent: hood up).
  - **porter**: rolled sleeves (forearms bare), a rust or tan work shirt, two leather straps crossing on the back
    (they carry crates there) with a brass buckle, sturdy brown trousers, a flat cloth cap.
  - **stallkeeper**: a shirt and a striped apron in one of the awning colours (red, blue, yellow, green, orange,
    purple; outfit picks), a headscarf or a flat cap (accent), sleeves pushed up.
  - **baker**: white linen shirt and long white apron dusted with flour (pale specks), a white cap or kerchief, flour
    on the forearms and a smear on the cheek.
  - **smith**: a dark leather apron with scorch marks, bare forearms, heavy gloves, soot specks, trousers tucked into
    boots (like Oren but every smith different).
  - **watch**: the city's livery and the most recognisable outfit of all: a deep blue tabard over grey mail sleeves,
    a bronze bell crest on the chest, a brown leather belt, dark trousers, boots; a kettle helmet (iron with a bronze
    rim) or a blue cap with a brass badge (accent). Outfit varies the blue only slightly (the Watch must read as one).
  - **crier**: a scarlet coat with gold trim and a white collar, a broad black hat with a feather (hat layer), a
    brass handbell at the belt. The loudest colour in the city.
  - **boatman**: a blue-and-white striped jersey, oilskin trousers (dark olive), a knitted cap, rope-burned hands.
  - **washer**: a pale smock or dress with a damp grey apron, sleeves rolled, a headscarf, reddened forearms.
  - **mason**: a dust-grey smock, stone dust in the hair, a leather tool belt with a trowel, knee pads.
  - **runner** (Runners' Guild courier): a close green tunic with an orange guild sash across the chest, a
    headband, light boots, fingerless gloves.
  - **shopkeeper**: a waistcoat over a shirt (varied colours: plum, teal, mustard, bottle green), sleeve garters,
    neat trousers or a long skirt.
  - **tavernkeeper**: an apron over a waistcoat, rolled sleeves, a towel over one shoulder, ruddy cheeks.
  - **clerk**: the Association's grey-blue coat with bronze trim at the cuffs and collar (accent: spectacles).
  - **archivist** (the non-named one): a dark green scholar's robe with ink stains on the fingers.
  - **healer** (3 nurses): pale blue with a white apron and a small bronze bell-cross badge, a white cap.
  - **miller**: flour-white shirt and cap, a brown waistcoat, flour everywhere.
  - **child**: bright simple tunics or dresses (sky blue, rose, leaf green, mustard), shorts or a skirt, bare knees,
    scuffed shoes, no hats except some knitted caps; patched elbows.
  - **resident** (elders and the rest): muted wools (heather, moss, oatmeal, slate), shawls for some, waistcoats for
    others, a walking-stick hand is not needed.
  - **thief** (t0-t7): a dark hooded cloak (hood up, on the hat layer), a scarf over the nose and mouth, dark
    leather, soft boots; charcoal, oxblood, deep green, brown variants. Menacing but not silly.
- Every skin gets the town's shared details somewhere: bronze or brass buttons/buckles, a bell motif on crests and
  badges, wool texture (the `tex` folds) on cloth, leather a little glossy at the edges.

Variety requirement (check it): no two citizens' skins identical; at least 24 clearly different looks among any 40
citizens picked at random (count distinct (job, outfit, hairStyle, hairColour, tone) tuples, and also an image-level
check: mean absolute pixel difference between any two non-household skins of the same job ≥ a threshold you pick and
report). Children must look like children, elders like elders.

### 2. Block textures (16x16 unless said)
- `block/street_lamp_frame`: Carillon's lamp metal: dark bronze with verdigris in the recesses, a highlight edge
  (used for the base plate, hood, cap and finial; see `models/block/street_lamp_*.json` for which UV areas are used).
- `block/street_lamp_glass_lit` (warm, glowing amber-white glass with a tiny flame shape and the bronze frame bars at
  its edges: x 0-5 and y 0-8 is the side face used), `block/street_lamp_glass_out` (the same glass cold: smoky grey,
  sooty, a dead wick), `block/street_lamp_glass_broken` (cracked panes, a hole, jagged edges, transparent where
  broken).
- `block/laundry_line` (a twisted hemp line, 1 px tall rows used at the top), `block/laundry_1` … `laundry_6`: six
  pieces of washing hanging from the line on the 16x16 face (rows 1-12 used): a linen shirt, a bedsheet with a
  stripe, an apron, a pair of stockings, a towel, a child's dress; clothes-pegs at the top; soft colours with slight
  damp shading; transparent around the shapes.
- `block/city_fire`: an animated flame strip (16x16 frames, 16-32 frames, `out.block(name, frames, frametime=1-2)`)
  for a house fire: not vanilla's fire (deeper orange core, smoky dark tips, embers), transparent background.
- `block/cargo_crate` (side: rough planks with a rope or iron band and a stencilled bell mark) and
  `block/cargo_crate_top`.

### 3. Item textures (props citizens hold; 16x16, transparent background, vanilla item style but our palette)
`item/lamplighter_pole` (a long brass-tipped pole with a hook and a small wick at the top; drawn corner to corner like
a tool), `item/watch_halberd` (an iron axe-blade and spike on a dark ash shaft with a bronze collar; corner to
corner), `item/smith_hammer`, `item/mason_trowel`, `item/crier_scroll` (an unrolled parchment with red seal),
`item/hand_lantern` (a small iron lantern with a warm glow), `item/bread_basket` (a wicker basket of loaves),
`item/laundry_basket` (wicker with folded linen), `item/ledger_book` (Teodor's archive ledger, green leather,
brass corners), `item/courier_satchel` (orange guild satchel), `item/stolen_bag` (a small dark cloth sack tied with
cord; a coin or a glint showing).

### 4. Particles (`particle/<name>_<i>`, 8x8 or 16x16, white/grey shapes: the game tints them)
`chimney_smoke_0..3` (soft round puffs, growing and breaking up; greyscale with soft alpha edges, no hard pixels
outlines: these must not look like vanilla's smoke), `forge_spark_0..1` (a tiny bright streak and a dot),
`lamp_flare_0..1` (a soft star / glow), `fire_ember_0..2` (glowing specks).
Partial alpha IS allowed for particles (unlike skins).

### 5. Sounds (Ogg Vorbis, mono, `assets/awaken/sounds/city/<name>.ogg`)
Read `tools/art/audiokit.py`, `tools/art/audio_build.py` and one existing audio spec (e.g.
`tools/art/specs/audio_body.py`) and write `tools/art/specs/audio_city.py` in the same pattern (seeded, numpy-only):
- `bell_hour`: the Choir's great bronze bell, one stroke: a deep inharmonic bell (hum, prime, tierce, quint,
  nominal partials, the hum around D2-D3), long decay 6-8 s, a little shimmer; dignified, not a doorbell.
- `bell_district`: a district tower bell, one stroke, higher (around A3), shorter (3-4 s).
- `bell_alarm`: a small fast alarm bell stroke, bright and urgent, ~1 s (it is played rapidly in sequence).
- `handbell`: the crier's brass handbell, a quick double ring, ~1 s.
Normalise so they sit with the mod's other sounds (audio_mix.py measures loudness; match the ambience/UI levels the
other specs use). Register nothing in Java and do not edit sounds.json (the builder already wrote
`src/main/merge/citizens/assets/awaken/sounds.json` with these four names).

## Preview and checks
`python3 tools/art/specs/citizens.py preview DIR` writes:
- `DIR/citizens_front.png`: every citizen as a standing figure seen from the front (unfold head, body, arms, legs from
  the skin, with the overlay layers on top), 4x scale, in a grid grouped by job with the job name and id under each.
- `DIR/citizens_back.png`: the same from the back.
- `DIR/citizens_things.png`: the blocks, items and particles at 8x scale on a mid-grey and on a dark background.
Checks (fail loudly): base parts fully opaque, no partial alpha in skins, slim skins' unused arm column transparent,
every citizen has a skin, uniqueness and the variety numbers above, every item/block/particle texture written.
Look at your own contact sheets (Read the PNG files) and fix what looks wrong before you finish: you are judged on
the look.

## Finally
Write a "## Result" section at the end of this brief (what you made, the variety numbers, what you chose where the
brief left room, where it still falls short), commit, and make your final message the result plus the paths of the
contact sheets (put them under `build/art/citizens/`; build/ is gitignored, so also copy the two figure sheets,
downscaled to ≤ 1600 px wide JPGs, to `docs/carillon/milestones/m3/` and commit those).
