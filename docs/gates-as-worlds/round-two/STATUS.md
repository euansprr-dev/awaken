# Gates round two: status of the test (kept current by the orchestrator)

Euan, 10 Nov: "Go ahead and test this then. We have three builders... Fable 5.2, but maybe just in high. So you do all the thinking."
And: "Yes, merge them" (each builder's branch into the gates branch once its checks are green; never `build/awaken-v1`).

## Where things are (10 Nov, 13:40 local)

- Gates branch `claude/gates-as-worlds-plan-fcb81b`, pushed at `24b4e6772`, local tip `4dc18ff55` (look round 1 and blockout fixes, not pushed).
- Kit merged: bench (`tools/bench/bench.sh`), straight-in walk (`awaken gate realm_enter`, `need realm`), true light in the preview
  (`lum`, `dark`, `bright`, `BLACK`), `tools/art/tone_check.py`, re-toned Knell and Keep, seven Lens looks, `Settle`, `SheetLand`,
  the blockout (`realms/blockout`, data in `data/awaken/realm_blockouts/`).
- Three gates built and merged, first pass: The Quiet Barrow (`land/QuietBarrow.java`, rule `RealmHush`), The Wax Galleries
  (`land/WaxGalleries.java`), The Hour Crypt remade (carved Knell shapes, `RealmClapper`).
- Builders (same three sessions, renamed): Quiet Barrow in worktree `gates-kit-blockout-185f26` (session `local_15d0fd89-...`),
  Wax Galleries in `gates-kit-bench-b81907` (`local_03f66485-...`), Hour Crypt in `gates-kit-light-47c135` (`local_f19c920c-...`).
  Each is on look round 1 (`sheets/look-round-1.md`). When each hands back: read the diff, merge, bench, look at the pictures.
- Three real-game walks were started on GitHub at 13:33 from `24b4e6772` (first pass, before the look round): logs in the
  orchestrator's scratchpad `ci-wax.log`, `ci-barrow.log`, `ci-hour.log`; shots land in `run-autopilot/screenshots-ci/realm-*`.
  Check each `tour.log` for "not in a Realm" before trusting a shot. `realm_enter` had never run in a real client before these.
- Not run: the full GameTest suite on the merged tree (CI runs it on the push).

## Measured so far

| | Time | Failed bench rounds | Usage (weighted tokens) |
|---|---|---|---|
| Kit A: bench and walk | about 45 min | 1 | 4.6M |
| Kit B: light and textures | 14 min | 0 | 1.7M |
| Kit C: settle and blockout | 17 min | 0 | 1.2M |
| Gate: Quiet Barrow | 30 min | 0 | 2.9M |
| Gate: Wax Galleries | 31 min | 1 | 2.9M |
| Gate: Hour Crypt remade | 29 min | 1 | 3.3M |
| Orchestrator, since "go" | about 2 h | | 8.2M |

Calibration: all Fable sessions on the account used 105.8M weighted tokens while the weekly Fable meter went from 0 to 62%, so one
point of the week is about 1.7M (the plan page's "about 1M" was too low). The gates test so far is about 25M, about 15% of a week.
Each builder ran at 190k to 380k of context per step, and the orchestrator at 650k: that re-reading is most of the bill.

## What the first pass showed

- Speed and soundness: proven. Nothing floated, every site was reached, 0 or 1 failed rounds per piece.
- The blockout caught two composition faults and the bench one floating mast in the orchestrator's own work, in seconds each.
- Look, judged beside the paintings from the bench's pictures: the Wax Galleries is close (dark mouth, lake, floes, five combs);
  the Quiet Barrow is right from the painter's camera and blocky close up; the Hour Crypt is visible now but reads as an even blue
  wash with brown squares, because the sheet's `lum` target made the builder raise the ambient and Knell's `worn` ramp is rust
  brown. Look round 1 addresses each.
- Usage per gate did not fall: about 3M each, the same as the old way's better gates. The cause is context size, not rounds.

## Lessons for the next sheets

- Targets for light must describe pools (a share of pixels over 90), never a median.
- A sheet has to describe the hero piece in sculptural detail (the Barrow's prow was a thin post because the sheet said one line).
- Builders should not open paintings or picture sheets (they don't judge looks) and should get a short API card in place of reading
  the kit's sources: both swell their context.
- The orchestrator must keep its own context small: sheets and status in files, a fresh session per wave.
