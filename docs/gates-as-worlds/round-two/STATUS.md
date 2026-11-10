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

## 10 Nov, 14:45 local: look round 1 done, test reported, waiting on Euan

- Look round 1 merged (tip of the three builder branches: Hour Crypt `1b80b6972`, Quiet Barrow `b8a0d1df9`, Wax Galleries
  `2d8a9c7ca`); each took 6 to 7 minutes with 0 failed rounds and cost 0.8M, 1.7M and 0.8M weighted tokens.
- Real-game walks, both sets, all PASS and all inside the Realm, 5 to 7 minutes each: first pass runs 42769275037 (wax),
  42769274848 (barrow), 42769274754 (hour); after the look round 42772347380, 42772347597, 42772347305. Shots are in
  `run-autopilot/screenshots-ci/` in the gates worktree (untracked). `realm_enter` works in a real client.
- The results are on the approval page (version 3): https://claude.ai/artifact/VGSkfcKhLahyckPstoWWvN, figure `fig-test.jpg`.
- Usage: the whole test is about 30M weighted tokens, about 17.5% of a Fable week. The weekly Fable meter read 70% at 14:40 local
  with the reset at 13:00 local on 11 Nov. The three builders are idle; nothing more is to run until Euan answers.
- Asked of Euan: (1) carry on this way with the changes listed on the page? (2) until the reset, stop (my pick) or one more look
  round for the Barrow and the Crypt (about 3%)? (3) after the reset, bring these three to the bar for him to play first (my pick)
  or go on to new gates?

## Known faults to fix in the next look round

- Quiet Barrow: still too blue; the path is a wide straight tiled road (make it 3 wide and winding); no mist in the game;
  the deck shot shows the game's own square sun and clouds (give the gate its own night sky, no vanilla sun in view);
  `old_antler` is a Warden-tier stag and must not be the herd.
- Hour Crypt: one flat blue with pale candle points; `pool` reads 2 against 10. Likely fix: Lens lights (`AirPlan.light`) at every
  lit bay so Lens paints warm pools, and a less blue Lens look for `knell_crypt`.
- Wax Galleries: thick pale drips and brighter arcades; `painting` lum 54 against 60 to 100; one chain still crosses the
  `seat_painting` camera.
- Not run on the merged tree: the full GameTest suite (CI ran it on the pushes; check the Actions tab before merging to build).
- SPEC 50 has none of this written in yet: bench, `realm_enter`, `need realm`, TrueLight numbers, tone rule, Settle, SheetLand,
  the blockout, carved blocks, `RealmClapper`, `RealmHush`, the two new gates.
