# Spike S5 · The Atlas diorama with clouds (picture-in-picture cost; reuse Diorama or build our own)

Worktree `/Volumes/MEDIA/Awaken-c-atlas`, branch `carillon/spike-atlas` (throwaway; the real one is workstream W6).
Read first: docs/carillon/briefs/RULES.md, docs/SPEC.md §16.8 and §16.10, docs/plan-carillon.html ("The Atlas"; its
mock-up shows the intended look: an angled voxel map viewed from ~50° looking north, sun shading, puffy clouds over
everything unexplored, markers only for what you know).

## The questions
1. Can a 26.3 `PictureInPictureRenderer` (registered through NeoForge's `RegisterPictureInPictureRenderersEvent`;
   verify both in .ref) draw a depth-tested voxel diorama of 512×512 explored blocks inside a Screen, with orbit, zoom
   and tilt? (budget ≤ 2 ms/frame while open, ≤ 150 ms to open)
2. What does it cost to sample and keep height + top colour per column from chunks as the client loads them (per chunk
   µs, memory per 512×512 tile), and to rebuild the mesh in slices across frames?
3. Clouds over unexplored cells: what reads as clouds (sprites, a soft layer, both) and what does it cost?
4. Diorama (MIT, github.com/SunkenInTime/diorama, 0.3.0 for NeoForge 26.3): read its source (WebFetch the files; don't
   clone or run it) and judge whether reusing its baker, with its MIT notice, would be less work than our own, given
   it's 11 days old and changing fast. Don't copy its code into this repo in the spike.

## Build
- Client only, in the spike: a column sampler on chunk load (height via the heightmap, colour via the block's map
  colour), an explored mask (client-side for the spike; the real one is server bits), a Screen with the
  picture-in-picture diorama, clouds over unexplored cells, a couple of fake markers, drag to orbit, scroll to zoom.
- A dev command or key to open it, and an autopilot script `tools/autopilot/carillon-s5.txt`: walk or teleport along a
  path to explore an area, open the Atlas, `perf` while it's open (and the same spot closed as the baseline), shots of
  it at day and night, and a shot of the clouds parting after walking further.

## Hand back (docs/carillon/spikes/S5-atlas.md)
- Frame cost open vs closed, open time, sampler and mesh costs, memory.
- Shots, your honest read against the mock-up, and the reuse-or-build recommendation with reasons.
