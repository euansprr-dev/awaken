# Milestone 3 · Alive: Carillon's people (Citizens builder, Opus at extra-high effort)

Worktree `/Volumes/MEDIA/Awaken-alive`, branch `carillon/m3-alive` (cut from carillon/main after M1). You are the only
builder running. Report once, at the end. This milestone runs before the opening (M2) by Euan's decision 20, so it must
not depend on the opening or on the Ascent's new movement.

Read first, in order:
1. /Volumes/MEDIA/Awaken-alive/CLAUDE.md, then docs/carillon/briefs/RULES.md (the lean rules are at the top).
2. docs/SPEC.md §16: 16.0 (decisions, especially 7 and 8), 16.5 (citizens, with the G0 movement rules), 16.6
   (witnessing and greetings only), 16.10; §12 for `core.client.skin` (SkinnedMobRenderer, SkinRenderState) and the
   rule that no non-player entity uses AvatarRenderState.
3. docs/plan-carillon-lean.html, "A city that lives" (the twelve jobs, the day strip, events, recognition).
4. docs/carillon/spikes/G0-notes.md, the S4 section. Then spike S4: /Volumes/MEDIA/Awaken-c-crowd
   (docs/carillon/spikes/S4-crowd.md and src/main/java/dev/awaken/citizens/). Reuse the ledger, the body movement
   (keep-right lanes, reserved spots, return-to-node, early departure, path slots, rescue) and the measurements.
5. The city you build on: docs/carillon/milestones/M1-city.md, `dev.awaken.city` (CityHooks; the street graph and
   plots in SavedData `awaken:city`; data/awaken/city/plan.json: districts, plots, enterable plots, landmarks).
6. What not to reuse: hearth/town/TownNpc (see the G0 notes' list).

## Goal
Walking Carillon at any hour feels like walking a living town. People are where their day says they should be and doing
visible work. Something happens that the city deals with, with or without you. Someone who has seen you around says
your name. This is Euan's second play session: "the city alive: citizens at work, an event, a name said back to you".

## Deliver
1. **The roster as generated data.** A Sonnet-written generator (tools/city/citizens.py) writes
   `data/awaken/city/citizens.json` (schemaVersion 1). It holds 240 citizens with names, a home plot, a job, a workplace,
   a seeded schedule on the vanilla day clock, a household, a workplace group, a tavern, and skin-layer seeds.
   Oren, Mira, Teodor and Sun are named citizens with jobs (smith, Watch scout, archivist, healer at the infirmary),
   per decision 8. Their move to a Hearth comes later with renown.
2. **The ledger** (SavedData `awaken:citizens`, schemaVersion 1): sliced updates, abstract movement on the street
   graph, and memories of players. It loads back identically.
3. **Bodies** on `SkinnedMobRenderer`, with the S4 rules from SPEC §16.5:
   - spawn at 56 blocks and remove past 64;
   - at most 40 per player and 96 per world;
   - never saved.
   Fade in rather than popping into view down a straight street. Seeded skin layers (base, clothes by job, hair or hat,
   colour variants) give at least 24 clearly different looks; four skins reading as clones was S4's biggest look gap.
   Skins come from a generator spec in tools/art/specs: you set the art direction, a Sonnet subagent may run it.
4. **The twelve jobs, each with visible work.** Each has a station, a job clip (Player Animation Library clips through
   tools/art/specs, the same pipeline as body_moves.py) or a held prop, and a world change you can see:
   - The **lamplighter** lights the street lamps at dusk and puts them out at dawn, so the light really changes. A
     broken lamp left dark can spawn mobs, by design.
   - The **bellringer** rings the hours.
   - The **porter** carries crates from the Quay to the stalls.
   - The **stallkeeper** opens shutters at 7 and closes them at dusk.
   - The **baker**'s chimney smokes at dawn (client particles), and the **smith** throws sparks.
   - The **Watch** patrols, the **crier** reads short news lines, and the **washer** fills and empties laundry lines.
   - The **mason** repairs damage, block by block.
   - **Boatmen** may come in Detail if boats on the canals cost too much now; say what you chose.
   - **Rooftop runners** wait for the Ascent's moves.
5. **Events**, budgeted per play-day and seeded:
   - burglary: the thief flees through the streets for now, and the rooftop escape waits for the Ascent's moves;
   - pickpocket;
   - fire in the Works, with a bucket line;
   - runaway cart;
   - gate tremor outside the wall, from D-rank.
   Each one resolves with or without the player. Consequences go into world state only (patent rule): district safety,
   gratitude, stock, repairs. Citizens are hurt, never killed; they recover at the infirmary.
6. **Recognition, first step.** Witnessing: citizens within line of sight and 24 blocks remember a deed. Gossip spreads
   once per play-day through households, workplaces and taverns. The tiers Stranger, Familiar face and Known exist,
   so at Known citizens greet you by name. Lines are short and authored, picked per job and tier, and written as data
   by a generator: no language model, everything seeded. Install `CitizenHooks` and a minimal `RenownHooks`.
7. **GameTests** (prefix `citizens_`, silent breakage only):
   - one simulated play-day in which every citizen reaches every schedule target;
   - every station is reachable;
   - every event resolves, and no thief gets stuck;
   - the ledger round-trips through a save.
   One test per mechanism (Euan); run them locally with `-Ptests=citizens_` and push: CI runs the full suite.
8. **One `slot.sh light` tour (≤ 15 min), "a day in Carillon".** Dawn, noon, dusk and night in each district; the
   lamplighter's street before and after dusk; a stall opening; an event in progress; a citizen greeting the player by
   name. Reuse M1's studio framings where they fit.

## Not in this milestone
The opening and the Association's wake and Registry scenes (M2, the Director), rooftop runners and rooftop chases (after
the Ascent lands), shops, coin, factions and the broadsheet (MMO-lite), Carillon's block kit and textures (M4).

## Budgets
Server: ledger ≤ 0.5 ms/tick, bodies ≤ 1.5 ms/tick (log rolling p95 in game, with load). Frame: 40 citizens ≤ 1.2 ms
(measured quietly at the end of Detail, not now; just avoid obvious waste).

## Model policy
You are Opus at extra-high effort and you decide what people look like and how they behave: bodies, clips, events,
greetings. The roster, schedules, line tables, skin generator runs and GameTests can go to one Sonnet subagent at a time
(Agent tool, `model: "sonnet"`), briefed from a file you write under docs/carillon/briefs/M3-sonnet-<n>.md.

## Hand back
docs/carillon/milestones/M3-alive.md, committed and pushed: what's built, tick costs, CI status, tour shot paths, an
honest read against "walking Carillon feels like walking a living town", and the gaps for M2 and M4. Your final message
is that report (under ~600 words). Leave nothing uncommitted.
