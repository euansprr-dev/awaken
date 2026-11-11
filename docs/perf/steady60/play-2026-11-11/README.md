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
- `glasswater.jpg` (SPEC §22 "Glasswater"; `tools/autopilot/steady60-water-ci.txt`): the Air's own water beside the
  game's, on a pool built for it. `wip/glasswater-first-look.jpg` is the first pass he was shown.

Not fixed here: the ground under a slide or a crawl is now lit like any ground (the hand's own depth settled it),
exact light under cover (needs the game's light data in a texture), Lens's shadows on land the game has already
fogged, and the sun's rays coming out hard-edged under a low sun near the cloud layer (seen in the world-moods
planner's dusk shot). (The flat tan mass was first put down to Far Sight's far land; his Far Sight was off, and it was
the game's own fog.)
