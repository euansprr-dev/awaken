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


## Result

Done. Everything is generated and deterministic (two builds are byte-identical); nothing in `src/main/java`, `citizens.json`, `plan.json`, `citizens.py` (the roster generator) or `sounds.json` was touched.

**Files**
- `tools/art/specs/citizens.py` (the spec; imports hearth's `tex/col/h01/_faces/EYE_WHITE` and `pixelart`). `python3 tools/art/build_all.py citizens` writes 280 textures; `python3 tools/art/specs/citizens.py preview DIR` writes the three sheets and runs the checks (`... check` runs only the checks; they also compare the files on disk with the roster once built).
- `tools/art/specs/audio_city.py` (numpy only, seeded; `python3 tools/art/specs/audio_city.py` renders, `... sheet OUT.png` adds a spectrogram sheet). It writes only the four `.ogg` files and no JSON.
- Textures under `src/main/resources/assets/awaken/textures/`: `entity/citizen/c000..c239.png` minus the four named (236) and `t0..t7.png` (244 skins); blocks `street_lamp_frame`, `street_lamp_glass_lit/out/broken`, `laundry_line`, `laundry_1..6`, `cargo_crate`, `cargo_crate_top`, and `city_fire` (32 frames, 16x512, `frametime 1`, **`interpolate: false`**, because blending frames would smear its dark tips); items `lamplighter_pole watch_halberd smith_hammer mason_trowel crier_scroll hand_lantern bread_basket laundry_basket ledger_book courier_satchel stolen_bag`; particles `chimney_smoke_0..3 forge_spark_0..1 lamp_flare_0..1 fire_ember_0..2` (partial alpha, white/grey, 16x16 smoke and flares, 8x8 sparks and embers).
- Sounds `assets/awaken/sounds/city/`: `bell_hour` (8.6 s, hum D2, prime D3, a bronze partial series with beating pairs and a dull clapper thump under it), `bell_district` (4.4 s, prime A3), `bell_alarm` (1.05 s, prime F6, tight), `handbell` (1.15 s, a double ring, brass partials plus clapper chatter). Mono, mastered to sfx loudness: RMS -19.0 / -20.1 / -20.5 / -22.5 dBFS, true peak under -1.8; `audio_mix.py` reports all four `ok`. I judged them by spectrogram and levels only (I cannot listen).
- Contact sheets: `build/art/citizens/citizens_front.png`, `citizens_back.png`, `citizens_things.png` (blocks, items, particles and six fire frames at 8x on grey and dark). The two figure sheets are also in `docs/carillon/milestones/m3/citizens_front.jpg` and `citizens_back.jpg` (1492 px wide, so no downscale was needed).

**Variety numbers** (`citizens.py check`)
- 244 skins, no two identical (hash check).
- Distinct (job, outfit, hairStyle, hairColour, tone) tuples among 40 random citizens (4,000 draws): min 39, mean 40.0; threshold 24. 235 distinct tuples among the 236 non-thief citizens.
- Image level: mean absolute RGB difference (both skins laid over mid grey, averaged over the pixels either skin uses) between any two same-job, different-household skins: **min 6.6 (c080 vs c083, two Watch), median 28.1; threshold 6.0**. The minima per job: watch 6.6, thief 6.8, porter 7.8, tavernkeeper 8.0, lamplighter 9.2, crier 9.9, child 9.3, shopkeeper 11.3, bellringer 12.1, stallkeeper 12.3, runner 12.7, clerk 12.9, resident 14.2, boatman 14.4, baker 16.9, mason 17.6, washer 18.1, smith 21.9, miller 24.6. The Watch is one livery on purpose, so its floor is the lowest; I kept the threshold at what a uniform can honestly clear instead of faking it.
- Every non-thief skin carries bronze or brass somewhere (a button, buckle, bell, pin) and the checks assert it.

**What I chose where the brief left room**
- Gender is read from the given name (the roster's two name lists plus the extra names); beards, skirts and earrings follow it. Face 0-7 sets eye colour, brows, mouth and one trait (1 freckles, 2 a pale scar, 3 stubble or a beauty mark, 4 rosy cheeks, 5 a full beard on men or a mole, 6 a moustache on men, 7 eye bags); faces on elders get lines, on children round cheeks and bigger eyes. Bald (style 7) is only worn by grown men; anyone else with 7 gets a close crop. Spectacles: all clerks with accent 2-3, about 30% of elders.
- Slim arms use vanilla's slim layout (3 px; the faces shift left, the 2 spare columns stay transparent); one grid per part (outer edge first) paints both builds.
- Accents: stallkeeper awning colour = (outfit + 2 * (accent >> 1)) mod 6 over red, blue, yellow, green, orange, purple, headwear = accent & 1 (kerchief or flat cap); bellringer accent 3 = hood up; watch accent 0-1 kettle helmet, 2-3 blue cap; smith accent 1 headband, 2 goggles pushed up, 3 leather cap; clerk accent 2-3 spectacles; tavernkeeper, baker and miller get their faces dusted or flushed.
- Individuality on top of the look data: each person's cloth is nudged in hue and value by their own seed, 40% have a mend on one elbow, 35% on a knee, 28% on the hem, some wear the Association's bell pin, a watch sergeant has one or two bronze sleeve bands and a belt pouch, porters a neckerchief, thieves a baldric, scarf end, dagger or patch by accent.
- Fire: deeper orange core, brown-black smoky tips that break up, rising embers; it reaches the top rows so the template's crossed planes read tall. The crate's bell is stencilled between two riveted iron bands.

**Where it still falls short**
- Seen only as flat unfolds (front and back sheets): I never saw a skin on the real model, so the overlay volume (hair, hat, jackets) is judged from the layout, not in 3D.
- Children differ from adults by scale (the builder's), bright clothes, round cheeks and bare knees; the head is the same size, so they are "small people", not chibi.
- The stalls' own awning colours (`stalls[].colour`, a dye-like 0..7 index) are not matched to their keepers' aprons; the roster's colour index is not the six-awning list in the brief, so I did not guess.
- Crier's hat is a hat-layer cap with a band, a plume and a brim row, not a really broad brim (the layer cannot grow past the head box). Thieves' hair is hidden by the hood, so they differ by cloak, scarf and kit only.
- The smoke puffs are small in their 16 px frames (the game scales them); the lamp glass textures leave the unused 10 columns filled with plain glass colour.
- The bells are untested by ear. If one is too thin or too bright on speakers, change the `bright=` and `t60` arguments at the top of each function in `audio_city.py`.
