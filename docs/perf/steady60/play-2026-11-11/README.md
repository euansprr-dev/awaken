# Faults Euan found in his first play of the Air (11 Nov 2026), before and after

Each sheet has the code before the fix on the left and after it on the right, from GitHub's Mac runners.

- `edges-before-after.jpg` (SPEC §22 "Reach and cover"; `tools/autopilot/steady60-cover-ci.txt`, runs 42943068330 and
  42943242709): at a 100 and 110 degree view the ground along the bottom of the screen took the game's raw brightness
  back. Gone after.
- `cave-before-after.jpg` (same runs): a stone room with a seven-block roof in its own morning shadow. Before, the
  floor just inside the roof line stood bright against the shade outside; after, the shade carries on in and fades
  over six blocks.
- `torch-before-after.jpg` (SPEC §28.3; `tools/autopilot/heldlight-aloft-ci.txt`): a torch carried two blocks off the
  floor. Before, its pool of light hung in the air at the feet's height and cut a line across the walls; after, it
  lies on the floor.

- `steppe-fog-before-after.jpg` (SPEC §22 "The game's fog under the Air"; `tools/autopilot/steady60-steppe-fog-ci.txt`,
  runs 42959640767 and 42959804603, at his settings: 16 chunks, a 90 degree view, Cinematic, Vivid). The "brown fog
  but low quality" at the start of a dry biome. Each row has the game's own frame and Lens's, before and after: the
  Glass Steppe from above twice, then a fast glide toward the edge of the made land. Before, the game fogged its far
  land sand (the biome's own fog colour) and Lens's frame carried a cream band between the land and the blue sky;
  after, the game's fog is the sky's own horizon colour and the land fades into it. (The storm over the first "after"
  frame is a Rolling Storm that happened by itself in that run.)
- `storm-blocks.jpg` (SPEC §22 "Storm cells", the block storm; `tools/autopilot/steady60-storm-ci.txt`, run
  42959983512): the Rolling Storm in the Air's block clouds, from 460 and 230 blocks, at three hours, with a strike
  lighting it from inside by night, and from under its shelf.
- `lite-beside-swift-and-cinematic.jpg` (SPEC §22 "Lite"; `tools/autopilot/steady60-lite-ci.txt`, run 42960037101):
  the new tier between the two it sits between, the same views: morning, from the ground, dusk, night.

- `spotlight-before-after.jpg` (SPEC §22 "Reach and cover", the hand; `tools/autopilot/steady60-hand-ci.txt`, runs
  42985161194 and 42988235690, a 90 degree view): "When I get close to a door that's full of shade ... it creates this
  spotlight effect"; "Same thing when I get close to a wall ... and for ceilings". A shaded oak wall from 2.5 and 0.8
  blocks, looking up it, and a doorway. Before, a disc of the game's raw light follows the camera; after, the wall is
  shaded right up to the eye and the pickaxe in the hand stays as the game lights it. (A first "after", run
  42985247494, read a checkerboard for the hand: the texture was registered under the wrong name.)
- Glasswater (SPEC §22; `tools/autopilot/steady60-water-ci.txt`): the Air's own water. There is no sheet of it as it
  stands: `wip/glasswater-pixel-grid-rejected.jpg` is the first build, drawn on the block's sixteenth grid, which he
  saw and turned down ("I'm really not a fan of the pixelized reflections ... It looks more glitchy than anything
  else"). It was rebuilt smooth at dd769ccd5 and handed over at his word without waiting for pictures.
  `glasswater-smooth.jpg` is that build, from run 42997552947, which landed after the hand-over: the game's own
  water beside it, with Vivid, down at the bank, golden hour, dusk, from above, night, and on Lite.
- The Air in other worlds (SPEC §22; `tools/autopilot/steady60-realm-sky-ci.txt`): a gate world's own sky and fog
  under the Air. `gate-sky-before-after.jpg`: Amber Reach three ways (the Air off, which is the land as its first
  pictures had it; before, run 42996721809, under the Air's blue; after, run 42996759340, which landed after the
  hand-over). After, the sky, the far fog and the clouds are the land's amber. It is hazier than the land's own
  look: the Air's fog is by the render distance and does not follow the look's `haze`, so the far cliffs fade more
  than in the pictures he liked. Asked whether a gate world's fog should follow its look's `haze`, he said
  "Leave it": it stays.

- Shadows beyond the block map (SPEC §22; `tools/autopilot/steady60-treeshadow-ci.txt`, runs 43001148091 before and
  43001230601 after): "There's an issue here with the tree's shadow ... it moves as I move ... if I move completely
  to the left of the tree, then the entire shadow of the tree basically disappears. We need to double check this is
  fixed across the board for not just trees". A jungle giant on a lawn from 16, 42 and 78 blocks and from the ground.
  `tree-shadow-before-after.jpg`: before, from 78 blocks (past the block map's 46) the crown's shadow is a dark slab
  from the trunk's foot outward, as if the air under the leaves were solid; after, it is the same square with the
  trunk's thin line that it is from 16. `tree-shadow-ground-before-after.jpg`: the same from the ground at 55.
  The stone table beside it (a slab on four legs) still threw a slab's shadow from its feet at 78 blocks in both
  of those runs: a solid roof with open air under it was right only within 46 blocks. Fixed after them
  (a173f772f, run 43018675746): the heightfield now holds a roof's underside and the floor under it.
  `roof-shadow-before-after.jpg`: the table from 78 blocks before (dark walls between its legs, shade under it)
  and after (the slab's own square with the legs' thin lines, as from 16 blocks, below). Not held: a roof thicker
  than six blocks (a floating island), air deeper than 64 under one. Tick time not measured.
- Shade on creatures (SPEC §22; `tools/autopilot/steady60-creature-shade-ci.txt`, runs 43001737613 before and
  43001812108 after): "the shadows on animals are a bit broken ... you can see ... actual artifacts in the shadows,
  where the shadows have jagged edges or like zigzags". `creature-shade-before-after.jpg`: a pillar's shadow across a
  cow, square to the world and turned. Before, the edge on the cow is cut on the world's texel grid, which a
  creature's skin does not lie on: saw teeth on the flank, steps on the back. After, a soft straight edge on the cow;
  the grass under it keeps its texel-cut edge.
- Near lights against the sky (SPEC §22; `tools/autopilot/steady60-firefly-ci.txt`, run 43002556044, after only: his
  own screenshot is the before): "the Firefly lights goes behind clouds. Which shouldn't be the case".
  `fireflies-over-clouds.jpg`: six fireflies against the night sky, brightened 4.5 times so the clouds show; the
  second sits on a band of cloud and is whole. None in the run's sixteen shots is cut by a cloud.

- The windways and the updrafts, rebuilt as gusts (SPEC §17.12 "The look", §17.6, §22 "Light over the Air";
  `tools/autopilot/steady60-windway-ci.txt`, a sky platform and its 667-block lane on seed 4246): "I really don't
  like these wind flows ... they look very bad ... they look glitchy basically, as you can see, like they kind of
  cut off. Near the actual island as well ... rebuild something that kind of looks like wind gusts or something that
  actually look good. And they don't use much performance".
  - Before (run 43006874586): not only by night. At every hour each lane stands as a long hard-edged slab with
    square ends: the game's flat sky blue by day, grey at dusk, black by night; an updraft is a slab column. Their
    quads wrote depth, so Lens took them for something solid and kept the game's own sky behind them. Under Swift,
    which keeps the game's sky, there is no slab, only faint streaks.
  - After (run 43016117197, tip 941dcc7bc): long thin lines of pale air that taper at both ends, some curling over
    themselves, fading out toward the platform; the updraft is the same gusts winding up its column.
    `windways-before-after.jpg`: from 42 blocks to the side, by day, at dusk and by night.
    `windways-start-inside-updraft.jpg`: looking back at the platform a lane leaves, an updraft, and from the
    lane's heart looking along it. Under Lite and Swift by night they are the same (in the run's folder).
  - On the way: run 43007770991 stopped as Lens started (the light layer's clear texel could not be cleared: a
    clear's target must be a copy's destination too). Run 43014005378 was the first to draw: no slabs, but a gust
    close by was a bar across the view and, down the lane's length, the far ones were a litter of ticks round the
    vanishing point; both tuned in 941dcc7bc.
  - Not measured: frame times (the Mac is his while he plays). Not seen: the gusts in motion, and from the ground
    far below a lane. From the rim right above a lane's start its gusts are seen end-on, a few short strokes
    beside the island.

**12 Nov: the tree shadow that changed with where he stood, the real cause** (`tree-shadow-sides-before-after.jpg`;
runs 43060864509 before, 43076043878 after; SPEC §22 "The lit air"). "On one side of the tree the shadow is perfect,
then if I go to the other side, the shadow is gone ... its for ALL trees." The lit air's z reached the shader in a
frame texel the world's projection rewrites every frame, so every surface more than two blocks to the camera's +z was
judged by air some 70 blocks away, found "sure of the sun", and never walked. The sheet is one built tree on a lawn
under a morning sun: before, its shadow is whole looking north, absent looking south, and cut along a straight line
through the camera looking west; after, it is the same shadow from all three. The tree-shadow and roof-shadow fixes
above answered real differences in the far model, but not this, which is what he had been reporting since 11 Nov.
Found in one try once he could switch the parts in play (`/awaken lens rays off` brought every shadow back); the same
old-code shots had shown it a day earlier and were read as foreshortening. He played the fix: "Yeah, this is fixed."
Not measured here: with the shortcut working as designed more shaded pixels walk the block map, and he saw 50 to 55
fps where he had over 60; that is a separate branch's work (`claude/lens-steady-60-perf-1de2f5`).

Not fixed here: the ground under a slide or a crawl is now lit like any ground (the hand's own depth settled it),
exact light under cover (needs the game's light data in a texture), Lens's shadows on land the game has already
fogged, and the sun's rays coming out hard-edged under a low sun near the cloud layer (seen in the world-moods
planner's dusk shot). (The flat tan mass was first put down to Far Sight's far land; his Far Sight was off, and it was
the game's own fog.)
