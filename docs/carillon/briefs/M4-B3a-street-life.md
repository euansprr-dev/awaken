# Milestone 4 · B3a: street life (Opus at extra-high effort)

Worktree `/Volumes/MEDIA/Awaken-m4-b3a`, branch `carillon/m4-b3a` (cut from carillon/main after B2). You are the only
builder running. Report once, at the end. Euan's play-1 words: "not as alive as I want it to feel … it just doesn't feel
like a live city." B1 gave the city climbable facades and B2 gave it depth. This step fills its streets with life.
Animals and sound are B3b's, next.

Read first, in order:
1. /Volumes/MEDIA/Awaken-m4-b3a/CLAUDE.md, then docs/carillon/briefs/RULES.md (the lean rules are at the top).
2. docs/SPEC.md §16.0 (decisions 21–29, especially 26 and 27), §16.2 (including "Decided in M4 B1/B2"), §16.5 (citizens)
   and §16.9 (the runner_* tags).
3. docs/carillon/m4/look-bible.md: §7 the street-life layer (your main brief), §3.6 ways down, §3.7 lines, brackets and
   poles, §4 the affordance catalogue, §5 each district's street life, §8 the kit.
4. docs/carillon/milestones/M4-B1.md ("For B3") and M4-B2.md ("For B3"): awnings replace B1's dark-oak canopies at row 3;
   sign brackets reach past a plot's margin, so they are street pieces; the lit-window state is ready but everything is
   placed unlit; the 13 line sites are in plan.json "crossings" (ends a/b and an anchor); stalls go on the bowl's −2
   ring; the lower quays keep raster lift 0 though they're painted at −4.
5. docs/carillon/milestones/M3-alive.md (the citizens) and docs/carillon/m4/creature-life-handover.md (the CreatureLife
   API, SPEC §12/§23).

## Deliver
1. **The street-life layer** (look-bible §7), as rules and generators, not per-street work:
   - stalls with real wares; carts, benches, wells, planters;
   - worn and mossy paving that varies by street rank;
   - lamps at every junction, banners and bunting;
   - awnings replacing B1's canopies: soft landings in `runner_soft`, which bounce;
   - sign brackets as street pieces, in `runner_swings`;
   - laundry, bunting and ropes hung on the 13 line sites, plus lines across lanes, in `runner_lines`;
   - hay carts as soft landings.
   Decision 26: no vanilla banners, signs or barrels in the city's fabric (block entities); the kit carries the look.
2. **The kit grows toward ~60** (decision 27): tables, shelves of goods, sacks, crates, market produce, shop signs with
   icons, awnings, cloth and laundry. Art comes from the tools/art/specs pipeline: you set the direction, and a Sonnet
   subagent may run it. No block entities; emissive layers for anything that glows.
3. **Movement for free** (decision 27): waving banners and laundry, flickering lanterns and turning water wheels through
   animated textures; chimney smoke as client particles; lit windows at night through the lit-window state, switched by
   time of day. All cheap, no ticking block entities.
4. **Citizens**:
   - the roster grows to 400–480 (tools/city/citizens.py);
   - they cluster where life is: the market and the bowl by day, the taverns by night, a few porters and the Watch
     in between, rather than spread thin;
   - wire Creature life's layer (breathing, blinking, head tracking, idles) into CitizenModel and CitizenRenderer, on
     the agreed terms: client-only, about 0.1 ms or less for a crowd, job clips win, head tracking only near the player
     or a talking partner.
   Keep SPEC §16.5's caps (40 bodies a player, 96 a world).
5. **Small fixes**: clear the tree that grew in the High Gate yard (the yard must be clear ground for the Living Wilds'
   Hale and post).
6. **GameTests** (one per mechanism): `runner_lines` and `runner_soft` are filled and present on the line sites; the line
   sites are hung; lit windows switch with time of day (a state check); the larger roster's schedules still check out.
7. **One `slot.sh light` tour**: M1's cameras for before/after pairs, plus the bowl at noon, a market street at noon, a
   lane at dusk with lit windows, and a laundry lane.

## Not in this step
Animals and the soundscape (B3b), the landmarks' finished shapes and interiors (B4), the opening's routes (B5), story and
onboarding (B6), the quiet perf pass (B7: still keep an eye on light sources, at most 4–5 per chunk).

## Model policy
Opus at extra-high effort for how the streets look and feel. Kit art generator runs, roster and data tables and tests may
go to one Sonnet subagent at a time (Agent tool, `model: "sonnet"`), briefed from docs/carillon/briefs/M4-B3a-sonnet-<n>.md,
while you aren't building.

## Hand back
docs/carillon/milestones/M4-B3a.md, committed and pushed. It covers:
- what changed;
- before/after pairs;
- CI status;
- an honest read against "it feels like a live city";
- the runner_lines and runner_soft contents (for the Ascent);
- what B3b and B4 need to know.
Your final message is that report (under ~600 words). Leave nothing uncommitted.
