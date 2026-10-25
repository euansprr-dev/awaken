# Milestone 4 · B1: the facade grammar and the core kit (City Architect, Opus at extra-high effort)

Worktree `/Volumes/MEDIA/Awaken-m4-b1`, branch `carillon/m4-b1` (cut from carillon/main). You are the only builder
running. Report once, at the end. This is the first of seven Phase B steps (SPEC §16.0 decision 25), and the biggest
visual lift: every street changes at once.

Euan approved the Phase A package on 2026-10-25 ("Go ahead with the full plan and all recommendations"). His play-1
verdict on today's city: "a bit boring … not as alive as I want … everything is a bit flat … every building facade has
something you can climb … it doesn't look like it was built to be there. You just know that you can do it."

Read first, in order:
1. /Volumes/MEDIA/Awaken-m4-b1/CLAUDE.md, then docs/carillon/briefs/RULES.md (the lean rules are at the top).
2. docs/SPEC.md §16.0 (decisions 21–26), §16.2 (the city, M1's decisions), §16.10.
3. **docs/carillon/m4/look-bible.md**, your source of truth. All of it, but especially:
   - §2 the movement envelope;
   - §3 the city-wide rules: the climbing grid, running faces, the "no" language, the dimensions table in §3.10;
   - §4 the affordance catalogue;
   - §5 the districts;
   - §8 the kit;
   - §9.2 the grammar and painter;
   - §9.3 the tests.
   Also docs/carillon/m4/facade-grid.svg and the concept images in docs/carillon/m4/concept/. They are mood and language;
   where an image differs from the bible, the bible wins.
4. docs/carillon/milestones/M1-city.md and the city you're changing: src/main/java/dev/awaken/city/ (the grammar,
   painter and section writer) and tools/city/plan.py.

## Goal
Every building in Carillon reads as a real building that you just know you can climb. The climbing grid runs on all
779 facades (odd rows hold, even rows breathe; courses line up along whole streets). Each district has its own window
vocabulary, facades and roofs. Running faces are kept clear. From the sky, the roofscape stops being one flat mass.

## Deliver
1. **The climbing grid in the grammar**, on every building: a projecting lip every 2 blocks from 3 up (shop cornice,
   window boxes and hoods, floor courses, the eave). Courses line up along whole street fronts. Climb lines (drainpipes,
   ivy, trellis) at least every 12 blocks of street face. Running faces kept clear per §3.2, and the "no" language per
   §3.8. Rules, not per-building work: each rule lifts every building.
2. **Facades per district** (§5): window vocabulary, bays, jetties where the bible allows them (none on the Quay), timber
   framing, plaster and brick, and shutters. Break the one-window-in-a-grid look.
3. **Roofs**: ridge caps, dormers, chimney tops, crow-stepped gables, and Bellwatch's slate and clay roof mix.
   Roofs chain one storey at a time where the plan already allows it. Height and relief changes are B2's, not yours.
4. **The core kit**: the subset of the bible's ~35 kit blocks that facades and roofs need (for example slate and clay
   tiles, flower boxes, drainpipes, lit window panes, trim). Textures come from a generator spec in tools/art/specs, the
   same pipeline the fauna and citizens use: you set the art direction, and a Sonnet subagent may run the generator.
   Street-life kit (wares, awnings, carts, lines) is B3's. Keep within SPEC §16.2's drawing rules: no block entities in
   bulk, solid cores except enterable plots, section writes.
5. **GameTests** (prefix `city_`, one per mechanism, per §9.3), at least: every street face has a climb line within 12;
   the grid's lips sit on their rows (a sampled check); a chunk painted alone still equals the same chunk in context.
   Re-pin the plan hash only if you change the plan.
6. **One `slot.sh light` tour**, using M1's studio framings (tools/autopilot/carillon-m1.txt cameras) so the shots compare
   before and after. Add one facade close-up per district and the sky view.

## Not in this step
Relief and heights (B2), street life and citizens (B3), landmarks (B4), the opening's routes (B5), story and onboarding
(B6), perf (B7). Don't touch the opening's stage; B5 retires it.

## Model policy
You are Opus at extra-high effort and you decide the look. The kit texture generator runs, data tables and tests may go
to one Sonnet subagent at a time (Agent tool, `model: "sonnet"`), briefed from a file you write under
docs/carillon/briefs/M4-B1-sonnet-<n>.md, while you aren't building.

## Hand back
docs/carillon/milestones/M4-B1.md, committed and pushed. It covers:
- what changed;
- before/after shot pairs from the same cameras;
- CI status;
- an honest read against "every facade looks built to be there, and you just know you can climb it";
- what B2 and B3 should know.
Your final message is that report (under ~600 words). Leave nothing uncommitted.
