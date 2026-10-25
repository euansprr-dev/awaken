# Milestone 2 · The opening in blockout (Director, Opus at extra-high effort)

Worktree `/Volumes/MEDIA/Awaken-opening`, branch `carillon/m2-opening` (cut from carillon/main once M3 is merged). You are
the only builder running. Report once, at the end. Euan's first play session follows this milestone: the opening,
start to finish, in blockout, and his question is "is it thrilling?".

Read first, in order:
1. /Volumes/MEDIA/Awaken-opening/CLAUDE.md, then docs/carillon/briefs/RULES.md (the lean rules are at the top).
2. docs/SPEC.md §16: 16.0 (decisions), 16.4 (the opening's beats and the camera rules from spike S2), 16.2 (M1's
   decisions: the Roost, Choir Square, the Bell Bridge marked breakable, the street route and the roof graph), 16.3;
   §17.2–17.3 (the Ascent's Runner moves as built, MovementHooks.wallRunning/hanging, and Routes for golden runs).
3. docs/plan-carillon-lean.html: "The opening, beat by beat" (the storyboard and the route map) and "The Runner's Kit".
4. The Ritual as built: docs/proposals/the-ritual.md, story/ (Prologue, the ritual director, RitualCamera,
   RitualClient's input hold and letterbox), LensClient.override / clearOverride / tear (Carillon's looks go in
   assets/awaken/lens/looks_carillon.json).
5. docs/carillon/spikes/G0-notes.md, the S2 section; spike S2's code in /Volumes/MEDIA/Awaken-c-camera
   (src/main/java/dev/awaken/opening/): the camera entity, per-frame placement, the exit path. Read it, never write to it.
6. docs/carillon/milestones/M1-city.md and M3-alive.md (the city and its people you're staging the opening in).

## Goal
The first eight minutes of a new world. Bells at dawn, then the sky over Carillon, then a long flight down into your
own eyes on the Runners' Roost. The great bell rings wrong and a column of light falls into Choir Square. You run there
over the roofs, the Bell Bridge falls behind you, and you cut through a fleeing market. The gate takes you; the Ritual;
the Void; you wake in the Association's infirmary and leave the Registry with a licence and a Holo on your wrist.
Blockout means plain shapes and no final art. It does NOT mean flat: timing, camera, sound, light and the run's flow
must already feel like a game's opening.

## Deliver
1. **The opening module** (`dev.awaken.opening`). New `StoryProgress` states, appended after the existing ones (players
   already at DONE never see the opening). `OpeningHooks` installed. Relogging at any beat resumes it: the server owns
   the timeline and sends its start age.
2. **The sky shot**, following S2's rules (SPEC §16.4):
   - one `OpeningCamera`, placed every frame, with a single `finish(reason)` path;
   - a designed path over the north-west half looking south-east across the spire, pre-warmed behind the opening card;
   - Lens's Carillon dawn look; the bell; a letterbox better than a plain black bar;
   - skippable by holding jump for 1 s after the first viewing.
   Then "into your eyes": the first-run choice of first or third person, and the View key, which flips first and third
   and skips the front view.
3. **The bell rings wrong.** A light column in Choir Square, visible from everywhere, and the Signal's one line. No
   waypoint.
4. **The run: three acts, three routes each** (rooftop, canal, street), on M1's plan and graphs.
   - Act 2: the Bell Bridge falls when you approach, not on a timer.
   - Act 3: a market rush with citizens fleeing toward you (M3's citizens; post a city event through CitizenHooks).
   - The Runner's eye: a bronze pulse on the next hold after 6 s of hesitation at an edge, shown less the better you
     get, with an option to turn it off. A key glyph appears on a second hesitation, once per move.
   - No death in the opening: falls leave at least one heart. The street route is always ≤ 30 s away.
5. **Arrival.**
   - Solo: the 12 s cinematic, the portal pulls you in, Lens tears to black.
   - Party: hold everyone in the square, count the hunters still running, play the cinematic 3 s after the last
     arrival. Stragglers are pulled 5 min after the first arrival, or after 2 min idle.
   - The gate opens through `GateHooks.openScripted` in the square. This replaces M1's interim three lines in
     story/Prologue.
6. **The Ritual, then the Void** (both existing), then the infirmary wake:
   - Lens goes from blur to focus;
   - Sun and an Association officer say their lines;
   - in a party, the beds are side by side.
   Then the Registry, a stub per the lean plan:
   - walk to the Registry hall; the Status screen reveal (existing);
   - the E-rank licence;
   - the Holo wristband fitted (existing Holo); the first Directive;
   - "The city is yours."
   The Association scenes moved here from M3 by decision 20.
7. **Skip.** A world option "Skip the opening", the hold-to-skip on the sky shot, and `/awaken opening skip`.
8. **Golden runs.** Record one route per act and route with the Ascent's pilot (`route rec <name>`, SPEC §17.3
   "Routes"). The tour plays them back with `route play` and reads "off by d".
9. **GameTests** (prefix `opening_`, one per mechanism):
   - the state machine, with a relog at each beat;
   - the party wait and the straggler pull (fake players);
   - no death during the opening;
   - the skip.
10. **One `slot.sh light` tour** (≤ 15 min):
    - four sky-shot frames and the hand-off;
    - one frame per act, and the arrival;
    - the infirmary and the Registry;
    - the golden-run playbacks.

## Not in this milestone
Carillon's block kit and final art (M4), rooftop runners and the rooftop thief chase (after M2, with the Ascent's moves
in, as part of M4 or a small follow-up), the Atlas (M5), MMO-lite.

## Model policy
You are Opus at extra-high effort: the opening is the first impression. Route recordings, line tables and the
GameTests may go to one Sonnet subagent at a time (Agent tool, `model: "sonnet"`), briefed from a file you write under
docs/carillon/briefs/M2-sonnet-<n>.md.

## Hand back
docs/carillon/milestones/M2-opening.md, committed and pushed: what's built, CI status, the tour's shot paths and the
golden-run results, an honest read against "is it thrilling?", and what Euan should try in his play session. Your final
message is that report (under ~600 words). Leave nothing uncommitted.
