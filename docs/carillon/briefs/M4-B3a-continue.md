# M4 · B3a, continued: finish street life (Opus at extra-high effort)

The first B3a builder handed back early: its session limit came while its art subagent was still reviewing. Its work is
committed on carillon/m4-b3a (head b5e7a4c9 plus one uncommitted file, probably the art run's last change: inspect it,
then keep or drop it). **Nothing has been compiled, tested, toured or pushed yet.** You finish the step. The original
brief, docs/carillon/briefs/M4-B3a-street-life.md, still defines "done"; read it, RULES.md (note: the repo is PUBLIC)
and SPEC §16.0 decisions 21–29.

## What's already committed (from the first builder's interim report)
- **The street-life layer as plan data.** tools/city/props.py is called from plan.py; plan.json gains "props"/
  "propFields" and the crossings gain "hung"; the hash is re-pinned in CityTests. There are 840 props: 34 stalls (24 on
  the bowl's −2 ring, 10 on plazas), 2 fountains, 17 wells, 203 lamps (at most 3 a chunk), 16 hay carts, 61 Market
  displays, 60 benches, 53 carts, 196 door stacks, 86 planters, 8 notice boards, 30 Works heaps, 10 nets and 6 tavern
  tables. There are 261 lines (laundry, bunting, lanterns, rope) and all 13 crossing sites are hung. Props keep clear of
  the keep-right lanes.
- **Choir Square's graph:** street ends join the r30 ring path, so the eight winds are the only aisles.
- **Java (city):**
  - StreetBlocks/StreetKit (24 kit blocks plus wheel_face; awnings bounce 0.75 and halve the fall; hay carts ×0.2);
  - CityProps paints props into open air only;
  - CityFacadeLife: awnings replace B1's canopies; signs hang on brackets three out (the plot margin is now 3); banners
    on the Terrace, flagpoles in the Market, emissive wall lanterns;
  - CityLights switches lit windows by time of day;
  - CityYard clears the High Gate yard;
  - paving wear and mooring rings;
  - turning mill wheels (tools/art/specs/city_wheel.py).
- **Tags:** runner_lines hang_line; runner_soft hay_block, awning, hay_cart; runner_swings hoist_beam, sign_bracket,
  flagpole; runner_ledges + awning; runner_climbs/climbable + line_pole.
- **Citizens:** stalls are prop-based; Creature life is wired (head look only within 6 blocks in front or while speaking;
  blink swap when _blink skins exist; clips win); fountain spray; more house smoke.
- **Tests and tour:** city_street_lines and city_lit_windows; tools/autopilot/carillon-m4b3a.txt (53 shots).
- **Art (Sonnet 1):** tools/art/specs/city_street.py and its assets are committed as work in progress (its brief:
  docs/carillon/briefs/M4-B3a-sonnet-1.md). Its mock, build/art/city_street_mock.png, looked good, but the run hadn't
  finished its own review.

## Still to do, in order
1. **Finish the art.** Check the art's own review, or re-run city_street.py. Check that no texture copies or lightly edits
   a vanilla one (the repo is public; compare against /Volumes/MEDIA/Awaken/.ref/assets). Add "Wheel Face" to lang.
   Make the props' Java and the art agree on every blockstate. Commit.
2. **Build and fix compile errors:** `slot.sh build tools/gradle.sh 900 build` (nothing has been compiled yet).
3. **Run Sonnet 2** from docs/carillon/briefs/M4-B3a-sonnet-2.md:
   - the roster to ~450, clustered from the props;
   - named citizens Kestrel (the Roost) and Bram (the Association), with skins;
   - blink skins;
   - stalls in the props format.
   Then update the Java Roster reader to match.
4. **Test:** `-Ptests=city_,citizens_`. Watch city_grid_rows and city_grid_catchable against awnings, props and banners;
   fix the city, not the tests, unless a test is wrong.
5. **CityLights:** it finds a house's windows by painting the plot alone, about 0.5 ms a tick for the first ~40 s.
   Spread it or bake it into the plan if that's cheap; otherwise report it.
6. **Ship it:**
   - one `slot.sh light` tour;
   - the B2 → B3a before/after pairs;
   - docs/carillon/milestones/M4-B3a.md;
   - push, then check CI.

## Hand back
The report the original brief asks for: what changed, before/after pairs, CI status, an honest read against "it feels
like a live city", the runner_lines and runner_soft contents, and what B3b and B4 need to know. Also list the SPEC
decisions to propose. The first builder named these: the props layer, the square graph, plot margin 3, awnings one block
out, emissive door lanterns, the lit-window schedule, and wall stacks never two full blocks high. Final message under
~600 words. Leave nothing uncommitted.
