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

Not fixed here: the flat tan mass at the start of a dry biome (far land drawn by Far Sight, which the runners cannot
run), the ground under a slide or a crawl (needs the hand's own depth), and exact light under cover (needs the game's
light data in a texture).
