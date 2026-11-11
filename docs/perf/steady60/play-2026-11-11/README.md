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
  **Not across the board yet**: the stone table beside it (a slab on four legs) still throws a slab's shadow from
  its feet at 78 blocks, in both rows. A solid roof with open air under it (a bridge, an arch, a market roof) is
  right only within 46 blocks; the heightfield holds a column's top and nothing about what is open beneath it.
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

Not fixed here: the ground under a slide or a crawl is now lit like any ground (the hand's own depth settled it),
exact light under cover (needs the game's light data in a texture), Lens's shadows on land the game has already
fogged, and the sun's rays coming out hard-edged under a low sun near the cloud layer (seen in the world-moods
planner's dusk shot). (The flat tan mass was first put down to Far Sight's far land; his Far Sight was off, and it was
the game's own fog.)
