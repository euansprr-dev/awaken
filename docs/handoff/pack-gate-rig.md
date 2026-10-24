# pack/gate-rig — the Gate Rig and its first three rooms (cloud session, 25 Oct 2026)

The Ascent's plan section 7, "Gate rooms: the room is the puzzle": a staged timeline under the painter, and three
room archetypes built on it. Rules in docs/SPEC.md §17 "The Ascent" → "Gate Rig".

## What's done

- **The Gate Rig** (`gates.world.RigRoom`/`RigRooms`, `gates.run.RigDirector`): groups of blocks swapped between their
  painted and active states, tracks of beats (warn, apply, restore) played by elapsed time with catch-up after lag,
  triggers from a player in a box, a light beam reaching a block, or a fight in the room. Saved in `FloorState.rig`
  (`RigState`, schema 1); rooms saved mid-track finish or put themselves back after a restart; a room nobody is in
  resets. The client draws the motion over the swaps: moving blocks with the game's own models, light with the new
  `awaken_rig_light` render type, the rig's own particles (grit, spark, glint, shard, mote) and 15 synthesised sounds
  (`tools/art/specs/audio_rig.py`).
- **Content version 4:** one rig room per ordinary floor of the themes that have one; version 3 plans build exactly as
  before, and every non-rig room of a version 4 plan is what version 3 built (choices are per-room hashes after the
  v3 build).
- **Collapse Run** (Ember Mines, Glass Desert, Bell Crypt): a pit with one causeway; the floor crumbles behind and
  under you on a beat, bridges drop and knit on a rhythm, gaps ask for a jump; fall and the pit throws you back to the
  start; the causeway knits back for the next try. Solvable both ways by a runner slower than a sprint.
- **Mirror Hall** (Mirror Palace, Precursor Lab): turn one-sided mirrors to route the beam from the lens to the seal;
  the bars on the way on fall when it lands. The beam burns monsters (credit to whoever turned a mirror last). Bigger
  halls hide a mirrored pavilion with a cracked front and a cache: `gate_mirror` and `gate_mirror_cracked` are in
  `awaken:mirror`, so Ghost Step walks through; the glass shatters for everyone once someone is in.
- **Gravity Wells** (Sky Archipelago, Precursor Lab, Nightmare): chasms too wide to jump, a well over each that turns
  gravity over (stratum 7's attribute way); walk the ceiling, drop softly on the far ledge. The room surges while its
  monsters live (everyone, monsters too, falls up for 3 s every 14 s). Upside-down people are drawn feet to the
  ceiling, their eyes hang 1.62 below it, your own flip is predicted on the client and tips the camera within the
  comfort settings.
- **Collapse Timer** finally drops real blocks: the ceiling cracks, falls and stays as rubble round everyone still on
  the floor; what lands on you hurts.
- **Protection:** every painted gate block is protected (only powers' constructs can be broken, tag
  `awaken:gate_breakable`); players can't place blocks, start fires, strip logs or use buckets inside a gate.
- **Tests (one per mechanism):** `gates_rig_collapse_run`, `gates_rig_mirror_hall`, `gates_rig_gravity_wells` (each:
  the pure solvability check on every rig room of 1,000 seeded gates, determinism, version 3 builds none, the server
  running it live in the test level), `gates_rig_protection`.

## Left / for the pack session

- Feel and look need the Mac (no GPU here): the tours below. The client code (beam render type, moving blocks, camera
  tip, upside-down render state) compiles against 26.3 but has not been seen on screen.
- Ghost Step through `gate_mirror` works in every Mirror Palace wall (the promised "Spirit meets Mirror"), so a spirit
  can step through mirror panels into the next room. Intended by plan.html; say if Euan wants it limited to the
  pavilions' cracked mirrors (remove `awaken:gate_mirror` from `src/main/merge/gates/data/awaken/tags/block/mirror.json`).
- Lords changing their arenas (plan section 7) and the other archetypes (Chase, Gauntlet, Tether Vault, Rising Tide,
  Toll Bridges, Lantern Dark) can now be built on the Rig; not started.

## Tours to shoot on the Mac (one per archetype)

Each from a creative test world: open a gate of the theme (`/awaken gate open <rank> <theme>`, e.g.
`/awaken gate open C ember_mines`), wait for it to form, `/awaken gate interior enter`, then
`/awaken gate interior goto rig` (stands you in the rig room's doorway looking down it; if the floor has no rig room
it says so: open another). `/awaken gate rig start` fires the room's trigger, `/awaken gate rig solve` solves it.

1. **Collapse Run** (`ember_mines`, then `bell_crypt`): from the doorway, `/awaken gate rig start`; screenshots at
   0.5 s (the first rows glowing), 2 s (rows falling, tumbling into the magma, grit and sparks), and after the knit;
   third person from the far landing looking back. Check: tiles drawn falling where the blocks vanished (no pop), the
   cracks bloom under Lens, the pit reads as deep (void glass in the Crypt), the `gate.rig.*` sounds play.
2. **Mirror Hall** (`mirror_palace`): from the doorway, the unsolved beam (the light ribbon, flares at each mirror,
   glints along it); use a mirror (the swing, no double block); `/awaken gate rig solve`, then the seal igniting and
   the bars falling. Night look with Lens on and off: the beam's core must bloom. If the hall has a pavilion: Ghost
   Step through the cracked mirror, then the shatter from outside.
3. **Gravity Wells** (`sky_archipelago`, then `precursor_lab` at rank B for the 7-wide chasms): step into a well: the
   fall up, the camera's tip, the ceiling walk in first person (eyes 1.62 below the ceiling) and third person (feet to
   the ceiling), the soft drop on the far ledge; then, with the room's monsters awake, one surge (warning motes,
   everyone up, everyone down). Comfort check: camera shake off = no tip; FOV effects 0 = no FOV breath.
4. **Collapse Timer**: there is no command to force a modifier, so open gates until one rolls Collapse Timer
   (`/awaken gate trace <id>` lists a gate's modifiers) and stay on a floor past the timer: three ceiling blocks
   crack, fall and land as rubble round you (hurting if they land on you).

## CI

Run id: filled in after the push (see the commit message of the last push and the GitHub Actions page for pack/gate-rig).
