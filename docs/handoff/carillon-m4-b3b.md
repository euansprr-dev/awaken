# carillon/m4-b3b: hand-off (cloud session, 2026-10-25)

Carillon M4 B3b: animals, the soundscape and the city at night. The Mac builder stopped at 979148e6 (compiled, no tests,
tour or CI) and the cloud session finished it. Full report: docs/carillon/milestones/M4-B3b.md. Nothing was merged:
the orchestrator merges. Nothing was force-pushed.

## Done
- **GameTests, one per mechanism** (`citizens/CityLifeTests`): `citizens_lanterns_drawn` (isolated),
  `citizens_soundscape_beds`, `citizens_fauna_rigs`. All pass locally (JDK 25 in the VM, `-Ptests=citizens_,audio_events,
  audio_layers_manifests`) and on CI.
- **Fixes**: the 17 new `city.*` sound events are registered (979148e6's CI was red; the unregistered events are what
  `audio_events_resolve` rejects, though I didn't open that run's test log to confirm); the carillon is mono and comes
  from the spire; the Choir strikes the hour after its tune at dawn, noon and dusk, not over it; no per-tick or
  per-frame allocations in the carts' clock or the glow pass; the ambience provider at its reserved priority 100.
- **Boats** (the "if there's time" item): ~21 moored along the quays, two rowed on the river, one round the canal, bow
  lanterns at night. New rig and four paints in tools/art/specs/city_fauna.py.
- **The listening page**: `docs/audio/carillon-b3b/index.html` with its 71 MP3s (23 MB). Seven districts at 06:00, noon
  and 22:00, before and after; every sound alone; keep/remake/cut and a JSON export (localStorage key
  `awaken-listening-carillon-b3b`); recommendations; what AmbientSounds adds. Checked in headless
  Chromium at 1100 px (light) and 390 px (dark): no horizontal scroll. Rebuild with
  `python3 tools/art/audio_listen_city.py` (`--page` keeps the MP3s).
- **Docs**: SPEC §16.0 decision 30 (and the reserved `awaken:city/*` line notes it's in use), the milestone report, this file.
- **The tour script**: `tools/autopilot/carillon-m4b3b.txt` (below).

## CI (GameTests workflow, branch carillon/m4-b3b)
| Run | Commit | Result |
|---|---|---|
| 40196485904 | 979148e6 (Mac WIP) | failure (see above) |
| 40197683663 | 4c611862 (tests + registration) | **success: all 474 required tests passed** |
| 40198394383, 40198528452, 40198834469 | 2860d25c, 77f78ecd, 56248d6a | cancelled by the next push |
| 40199359967 | 6bb77eeb (boats) | cancelled by the next push |
| **40199679593** | **ea175081** (boats, docs, the boatman's drift) | **success** |
| 40202671540 | 09c18b5b (the CC0 import, since removed) | success |
| **40204316490** | **82dfc622** (AmbientSounds; on top of the Mac's 26131b06) | **success** |

Locally, the full suite on ea175081 (JDK 25 in the cloud VM): all 474 required tests passed. The commit after
ea175081 only fills in this table (docs).

## Run the tour (on the Mac; the cloud has no client)
In the B3b worktree, or any checkout of the branch:
```
git fetch origin && git checkout carillon/m4-b3b && git merge --ff-only origin/carillon/m4-b3b
SLOT_WHO=M4-B3b /Volumes/MEDIA/Awaken-carillon/tools/carillon/slot.sh build tools/gradle.sh 900 build
PACK=perf,world,horizon,sound TECTONIC=awakened DH=swift RD=20 SLOT_WHO=M4-B3b \
  /Volumes/MEDIA/Awaken-carillon/tools/carillon/slot.sh tour tools/autopilot.sh tools/autopilot/carillon-m4b3b.txt 900
```
It measures frame times, so it needs the `tour` slot and the machine to itself. About 6 minutes of script, plus world load. The world is
`carillonM4b3a` (B3a's), so the framings pair with the b3a_* shots. What to look at:
- shots `b3b_*`: noon animals (bowl, square, quay, canal, Market, roofs, a bell tower's flock), the laundry lane at noon,
  dusk and night, the festoons from dusk, cats' eyes and boats' lanterns at night, the Watch in glow and in blocks;
- log lines `[citizens] animals near you: …` (every 30 s), `[citizens] lanterns glow|blocks: N light-block writes`
  after each half of the A/B, the ambience slot's `status: mixing …` lines, and `perf lanterns_glow`,
  `perf lanterns_blocks`, `perf lanterns_glow_2` (p50/p99 frame times).

## Nature: AmbientSounds (Euan, 25 Oct 2026)
Euan first approved all fifteen CC0 recordings, but Freesound refuses this environment (a login or an API key with an
approval wait), so he chose AmbientSounds instead. The Freesound import is gone; git history keeps it (f0289cc2).
- **The pack**: `ambientsounds` 6.3.11 in the sound profile, with its dependency CreativeCore 2.14.24 (both client-only).
  They were added to pack/lock.json alone; nothing else was re-resolved. Install as usual (`tools/pack.py install <dir> sound,...`).
- **No doubling**: at login the client tells the server whether it runs the mod (`CitizensNet.Ears`), and such a player
  hears `awaken:city/as/*`: the beds without our synthetic birds and crickets, since AmbientSounds plays those for real
  wherever there's greenery. They're generated beside the beds by `audio_city_life.py`; `citizens_soundscape_beds` checks
  they never drift.
- **The city's own zones** from its recordings by id (`citizens/client/CityNature`, a no-op without the mod): gulls
  over the river by day (ours stand down), a gull chorus on the quays, doves over the quiet districts, owls by the wall
  and the towers at night. Its engine already does wind on high roofs and rain.
- **To hear on the tour**: install the sound profile in run-autopilot, then listen in the Garden by day and night,
  on the quay, on the wall walk at night. The page can't play its recordings (they're its files).

## What's left
1. **Euan's listening session** on the page (keep / remake / cut), and a listen in game with AmbientSounds installed.
2. **The tour**, and a look-and-feel pass on whatever it shows: animal scale and poses, flock flight, carts on corners,
   boats at the waterline and under the canal's bridges, the glow on stone, the festoons' sag.
3. Market criers: AmbientSounds has none and synthesis can't make a human call, so the Market has none yet.
4. Rain on the city itself (dripping awnings, running gutters): not built; AmbientSounds plays the rain.
