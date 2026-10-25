# Milestone 4 · B2: relief and heights (City Architect, Opus at extra-high effort)

Worktree `/Volumes/MEDIA/Awaken-m4-b2`, branch `carillon/m4-b2` (cut from carillon/main after B1). You are the only
builder running. Report once, at the end. Euan's play-1 word was "flat": this step gives the city its depth.

Read first, in order:
1. /Volumes/MEDIA/Awaken-m4-b2/CLAUDE.md, then docs/carillon/briefs/RULES.md (the lean rules are at the top).
2. docs/SPEC.md §16.0 (decisions 21–28, especially 22: the Terrace at 12, no Quay jetties, Market spokes 5 wide) and
   §16.2 (including "Decided in M4 B1").
3. docs/carillon/m4/look-bible.md:
   - §6 the height plan (your main brief);
   - §3.4 roofs that chain, §3.5 gaps and crossings, §3.2 running faces, §3.10 the dimensions;
   - §5 for each district's relief;
   - §9.1 the plan (tools/city/plan.py, schemaVersion 2, hash re-pinned).
4. docs/carillon/milestones/M4-B1.md, especially "For B2". Every row is relative to a plot's lift, so even lifts keep the
   grid. Nothing is placed in front of a face where the ground stands higher. B1's tests (city_grid_rows,
   city_grid_catchable, city_climb_lines) check whatever relief you build.
5. The code: tools/city/plan.py, src/main/java/dev/awaken/city/ (CityGrammar v2, CityPainter, the writer).

## Goal
Carillon stops being flat. Each quarter has its own ground: Bellwatch on its Knoll, Choir Square a stepped bowl ringed
by its arcade, the High Terrace raised to 12 on its climbable wall, stepped lanes and stairs, canals a level below the
street with quays and bridges, setbacks, and towers that read from far away. Roofs chain naturally almost everywhere,
and there is a line across every avenue.

## Deliver
1. **The height plan in the plan** (plan.py → plan.json schemaVersion 2): per-plot lifts in even numbers only, raised
   quarters on retaining walls, the Knoll, the bowl, the Terrace at 12, stepped lanes and stairs, setbacks, and the
   canals a level down. Re-pin the plan hash on purpose.
2. **Roof chaining and crossings** per §3.4–3.5: roofs step at most one storey (≤ 3.5 up, ≤ 6 down or onto a soft
   landing), and a crossing over every avenue at least every 48 blocks: a bridge of houses, an enclosed walkway or a
   rope. The rope and line blocks are B3's, so mark their sites in the plan for B3 to dress.
3. **Market spokes 5 wide; the Quay's walls kept clear for running**; running faces wherever a roof gap is too wide to
   jump.
4. **Streets, graphs and the opening still work.** The street graph, the roof graph and protection follow the new
   ground. The opening's stage is B5's: if relief breaks a stage route, record where in your report; don't re-route it.
   Citizens' stations and the street route must stay reachable (M1's and M3's graph tests).
5. **GameTests** (one per mechanism, prefix `city_`): every roof is within one storey of a neighbour or a soft landing;
   a crossing over every avenue at least every 48; the existing grid, catch and climb-line tests and the graph and
   reachability tests still pass.
6. **One `slot.sh light` tour** with M1's cameras (before/after pairs), plus three new framings: the Knoll, the bowl and
   the Terrace's wall.

## Not in this step
Street life, props, animals and sound (B3), landmarks' finished shapes (B4: lay out their ground and massing only), the
opening's routes (B5), story and onboarding (B6).

## Model policy
Opus at extra-high effort for the shape of the city. Generator code, data tables and tests may go to one Sonnet subagent
at a time (Agent tool, `model: "sonnet"`), briefed from docs/carillon/briefs/M4-B2-sonnet-<n>.md, while you aren't
building.

## Hand back
docs/carillon/milestones/M4-B2.md, committed and pushed. It covers:
- what changed;
- before/after pairs;
- CI status;
- an honest read against "Carillon isn't flat any more";
- what B3, B4 and B5 need to know (crossing sites to dress, stage routes your relief touched).
Your final message is that report (under ~600 words). Leave nothing uncommitted.
