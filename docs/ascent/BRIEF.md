# The Ascent: brief for the builders (approved 2026-10-24)

The plan: docs/plan-ascent.html (published at https://claude.ai/artifact/CSkkigJNJ5k8obPTsYm3rZ). The verified audit
behind it: docs/ascent/AUDIT.md. This file is the contract: Euan's intent, his decisions, the quality bar, and how the
work fits beside the other sessions.

## Euan's intent, in his words (24 Oct 2026)

- "I'd like to make the movement a bit more dynamic over time, and you can like unlock more and more movement, and
  possibly even some really cool flight and stuff with like awesome animations, where you truly like by the end game
  you become kind of an overlord basically. Like, you know, that kind of godlike animation, as if you were an angel
  flying through the sky, wreaking havoc over any enemy."
- "the gates are cool and everything, but I don't think they should be the core … I really want this modpack to be
  much more than just that. I guess exploration is a big one as well."
- "really go deep into the magical feeling … I didn't even know this was possible in this. Like, it should feel like
  magical. Like the light reflection rooms, for example, like mirror rooms or gravity flips or collapsing stuff."
- "The chases, that sounds awesome. Parkour gauntlet sounds cool. Maybe we make, as you unlock more stuff, it could
  also lead to different gates that you can go into. For example, like a grapple sounds really cool as well. Like if
  you can grapple on trees and everything, that would be cool. Basically spiderman style movement with the grapple."
- "I definitely want the movement to feel like a reward in itself. Like it should feel like a really, really big part
  of the whole mod pack. Like for example, the sonic boom and everything sounds awesome … Not just with FOV, but we
  should really think about how do games may really make it feel extremely satisfying just to do the movement alone.
  That way going in between gates and everything feels like a big part of it in itself."
- "if a ton of parts of the map require different things, like require the movement … obviously, you can just put
  blocks wherever you want if you just want to get there without the movement. Really plan the ULTIMATE plan here."
- On approval: "All your recommendations for the plan … I really want it to feel magical, and I want it to be aware
  that this is supposed to feel genuinely magical."

## The bar: "genuinely magical"

This is the whole point. A feature that works but feels ordinary is not done. Before you call anything finished, it
must pass these checks, judged from clips or frame sequences in first and third person, never from code alone:

1. **You'd take the long way on purpose.** Crossing 300 blocks by sprint → slide → swing → release → glide beats any
   shortcut, with no HUD needed. If a tower or a walk is more fun, the move isn't done.
2. **Every move has anticipation, action and follow-through.** A wind-up, the motion, then a recovery: a roll, a wing
   fold. PAL clips with bends for each, generated from specs (tools/art/specs, like body_moves.py), and visible to
   other players.
3. **Speed is felt, not shown.** FOV with speed; wind that rises in pitch and volume; light streaks and contrails; a
   faint tremor at the top speeds; a Lens motion look at the frame edges. All of it is predicted, so it starts on
   your input and never waits a round trip.
4. **Sound carries the weight.** Synthesised sounds for each state, from the tools/art audio pipeline: rope creak and
   snap, cloth and wind, the wings' whoomph, the sonic boom (silence, then the crack), landings by surface. Never
   remapped vanilla events.
5. **It looks like Awaken, never vanilla.** Own particles and render types, no reused vanilla particles. Euan rejected
   "vanilla-looking particles/animations" as reading like an add-on (memory: visual-quality-bar).
6. **First times are scenes.** The first swing, first wings and first flight are staged moments (an Ascension trial
   staged like the Ritual of the Bell), not a toast.
7. **Soft failure, real skill.** Chaining never hurts; a missed swing drops into a glide or a roll. Skill shows through
   Flow, near-miss flashes, route records and your own ghost.
8. **120 fps holds** on Euan's M4 at 4K on the Dell (render distance 16, Vulkan), with wings and trails on.
9. **Comfort is a setting:** camera roll 0–7.5° (5° default), the FOV-effects slider, reduced motion, shake off.

## Decisions (all approved as recommended)

1. **Option B:** one movement system with states and momentum, climbed as a ladder; the world gets a traversal grammar;
   gates get a rig for living rooms.
2. **The ladder:**
   - Runner from the first second (Carillon's approved rule: ranks improve moves rather than unlock them; Wall Run
     becomes Wall Stride).
   - Above it, each tier earned by a deed or trial: Tether (E–D), Leaper (D–C), Wings (C–B), Skyborn (A),
     Sovereign (story end).
3. **Precursor wards:** blocks you place crumble after 3 s near Precursor sites only (ruins, spires, sky platforms,
   hero-tree caches), and drop back to you. Rank-door walls become unbreakable inside a ward. The Hearth and the open
   world stay untouched.
4. **Carillon's Runner workstream folds into the Ascent.** Movement is built once, here. Carillon (paused) builds the
   city on top, using MovementHooks.
5. **Three 10-minute feel sessions with Euan:** swing (Phase 0), glide (Phase 1–2) and flight (Phase 4). Ask him
   directly when a build is ready; feel is the one thing screenshots can't judge.
6. **Camera Overhaul stays in the pack.** Its roll pauses while an Ascent state owns the camera.
7. **Phase 0 runs on the Mac** (feel work). Phases 1–4 code can run in cloud sessions on the "Awaken" environment
   (Euan's cloud credit), with tours and feel sessions on the Mac.

## Rules of the house

- Read /Volumes/MEDIA/Awaken/CLAUDE.md and docs/SPEC.md first. Verify every 26.3 API in /Volumes/MEDIA/Awaken/.ref
  (in cloud VMs, read the NeoForge sources jar after a first build; see docs/HANDOFF-cloud.md). Never guess a
  signature. No mixins and no reflection; the Turbo contract allows internals only in dev.awaken.turbo.
- Record every rule you decide in docs/SPEC.md in the same commit, in a new §17 "The Ascent".
- Local builds and tours go through `/Volumes/MEDIA/Awaken-carillon/tools/carillon/slot.sh build|tour|light <cmd>`.
  Tours run alone; use `light` for screenshot-only tours, kept under 15 minutes. Never launch runClient. Never build
  in /Volumes/MEDIA/Awaken-play: Euan plays there.
- GameTests: iterate with `-Ptests=<prefix>`. The full suite runs on GitHub Actions when you push a pack/** branch
  (repo euansprr-dev/awaken), and CI green is the gate. Never push build/awaken-v1.
- **Merging:** hand each finished branch to the pack session (local_95821df4-6279-41f4-bfb8-78a208d4dbb9) with its CI
  run id; it merges into build/awaken-v1. Merge build/awaken-v1 into your branch before handing off.
- Tours default to Vulkan; until pack/autopilot-display lands, a tour window may come up minimized at 10 fps, so
  check `info` (display=, minimized=) before trusting any timing.

## Who else touches nearby code

| Area | Owner | Rule |
|---|---|---|
| Lens (`dev.awaken.lens`), `compat.client.FarTerrain` | pack session | Use `LensClient.override(look, fade)` and add looks in your own `assets/awaken/lens/looks_ascent.json`. Ask before editing Lens shaders. |
| Far Sight (`dev.awaken.farsight`) | far-sight session | Its far terrain is what makes high flight worth it; coordinate speed caps and chunk loading. |
| Holo UI (`dev.awaken.holo`, `core.api.UiHooks`, `signal.client.HudLook`) | pack session (Holo built) | Draw the Ascent panel and Flow meter through HudLook and holo APIs; don't edit holo internals. |
| Carillon (`city`, `citizens`, `opening`, …) | paused | Don't touch. MovementHooks stays stable for it. |
| Hearth training (checkpoint rings, ghost) | existing | Reuse it for route records and ghosts. |
