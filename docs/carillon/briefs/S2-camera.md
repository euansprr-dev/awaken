# Spike S2 · The sky shot (camera entity from the clouds to the player's eyes)

Worktree `/Volumes/MEDIA/Awaken-c-camera`, branch `carillon/spike-camera` (throwaway; the real one is workstream W3).
Read first: docs/carillon/briefs/RULES.md, docs/SPEC.md §16.4, docs/plan-carillon.html ("The opening, beat by beat").

## The questions
1. Can a client-only camera entity driven through `Minecraft.setCameraEntity` fly a smooth spline from ~250 blocks above
   down into the player's eyes, with no mixin? (Verified so far: `Minecraft.setCameraEntity` is public; `Camera` follows
   the camera entity's lerped position; `GameRenderer.update` still takes post effects from `minecraft.player`, so Lens
   should keep working. Unverified: whether chunk culling, section sorting, Distant Horizons and entity rendering behave
   with a camera entity that isn't the player, and whether it must be added to the ClientLevel.)
2. Is it smooth? (frame p50/p99 and frames over 50 ms during the flight, perf profile and `PACK=perf,horizon DH=swift
   LENS=swift`; budget ≥ 60 fps p99, aim 120)
3. Do chunks keep up when the camera moves fast over areas around the player, and does the fallback (moving the held
   player along the path on the server so chunks stream around them) work better?

## Build
- A small client package (e.g. `dev.awaken.opening.client` in the spike) with a camera entity, a Catmull-Rom spline
  through 5–7 control points over ~18 s (a long descending arc), ease-in/out, the hands and HUD hidden, input held (the
  Ritual's empty `ClientInput` trick in story/client/RitualClient), then a blend into first person (and a variant
  ending behind the player in third person), and `setCameraEntity(player)` restored on finish, on disconnect and on
  any error.
- A trigger: a client command or a server command sending a payload, e.g. `/awaken opening skyshot`.
- Lens: confirm the Lens chain still runs during the flight (a lensdebug view shot mid-flight).
- An autopilot script `tools/autopilot/carillon-s2.txt` that runs the flight 3 times in a normal world at dawn
  (`perfstart` / `perfend` around each), with shots at 4 moments (top, mid, near the roofs, the hand-off).

## Hand back (docs/carillon/spikes/S2-camera.md)
- Whether it works without a mixin, and every engine surprise you hit (with .ref file:line).
- Frame numbers per profile, hitch counts, and whether the fallback was needed.
- Shot paths, and the recommended design for W3 (entity type, where it lives, how it cleans up).
