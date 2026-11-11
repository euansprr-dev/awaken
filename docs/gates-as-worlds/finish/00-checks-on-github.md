# Brief 00: checks on GitHub

**Done, 11 Nov, by the gates session** (`tools/check-ci.sh`, hand-start inputs on `.github/workflows/gametests.yml`; a new workflow
file cannot be started by hand until it is on the default branch, so the existing one was extended). What follows is the brief as
written. One thing in it is wrong: Amber Reach cannot be benched on GitHub (the float scan runs out of memory on a 7 GB runner),
and its golden hashes are the preview's, not the bench's. Timing: about eight minutes for a first compile, then about one and a
half minutes a land and seed.

**Why.** Euan is playing on the Mac and wants all of it: "shouldn't run any tests on this mac locally". Every builder still has to
know that its code compiles, that nothing floats and that every leg can be reached. Real-game walks already run on GitHub
(`tools/tour-ci.sh`, `.github/workflows/tours.yml`). The compile, the bench and a single named test do not. You put them there.
Every other brief waits on this one, so keep it small and get it right.

**Done looks like.** From any pushed branch, with no Java on the Mac:

```
tools/check-ci.sh compile
tools/check-ci.sh bench tools/autopilot/realm-stopped_works.txt 507
tools/check-ci.sh bench awaken:catacombs 1 2 144
tools/check-ci.sh tests realm_bag_empty,realm_flag_off
```

each start a run on GitHub's Linux machines, wait for it without hammering the API, print the result, and (for `bench`) leave
`bench.txt`, `sheet.png` and every shot in `run-bench/ci/<name>-<run id>/`.

## What to build

1. **`.github/workflows/check.yml`**, `workflow_dispatch` only, `runs-on: ubuntu-latest`, Java 25 (temurin) and
   `gradle/actions/setup-gradle@v4` exactly as `gametests.yml` does, so the default branch's Gradle cache serves it.
   Inputs: `mode` (`compile`, `bench` or `tests`), `target` (a tour path under `tools/autopilot/`, a blueprint id such as
   `awaken:catacombs`, or a comma list of test name prefixes), `seeds` (space-separated whole numbers, may be empty), `id` (a free
   tag for finding the run). Copy `tours.yml`'s safety: inputs reach the shell only through `env`, and a first step refuses
   anything that is not `[A-Za-z0-9_:,./ -]`.
   - `compile`: `./gradlew --console=plain compileJava`.
   - `tests`: `./gradlew --console=plain runGameTestServer -Ptests=<target>`, then the same summary step `gametests.yml` has.
   - `bench`: whatever `tools/bench/bench.sh once <target> <seed>` does, once per seed, without `slot.sh` and without the SSD's
     `JAVA_HOME` (read `tools/bench/bench.sh` and `tools/gradle.sh`: call `./gradlew` with the same properties and environment the
     script passes). No seeds means the tour's own seed. Upload `run-bench/out/` as the artifact `bench-<id>`.
   - `concurrency`: group on ref, mode and target; do not cancel in progress.
   - `timeout-minutes: 30`.
2. **`tools/check-ci.sh`**, modelled on `tools/tour-ci.sh` (read it first; reuse its way of finding the run it just started).
   It refuses to run if the branch has commits that are not pushed. It dispatches, then looks once a minute (one `gh run view`
   call per look), prints the outcome, and for `bench` downloads the artifact and prints each seed's `floats:`, `settled:`,
   `reach:`, `route:` and `volume:` lines. Exit 0 only if the run succeeded and, for `bench`, every seed has `floats: 0` and
   `reach: all`.
3. **One paragraph each** in `CLAUDE.md` ("Build and test") and `docs/SPEC.md` §13 ("Tests"), saying what the script is for and
   that it is how a session checks while the Mac is in use.

## Check your own work

- `tools/check-ci.sh compile` on your branch: green.
- `tools/check-ci.sh bench tools/autopilot/realm-amber.txt 1 2 3` must print `volume:` lines starting `50cc4328`, `8947ec1e` and
  `bc407d30`. That proves Linux draws the same blocks the Mac does. If the hashes differ, stop and report: something in drawing
  depends on the machine, and that is a real fault, not yours to paper over.
- `tools/check-ci.sh bench tools/autopilot/realm-stopped_works.txt 507`: `floats: 0`, `reach: all 15 sites`, `route: 17 legs`.
- Open the downloaded `sheet.png` and confirm it is a real picture sheet, not blank.
- `tools/check-ci.sh tests realm_bag_empty`: one test, green.
- Time each mode and put the minutes in your hand-back.

## Yours

`.github/workflows/check.yml`, `tools/check-ci.sh`, the two paragraphs. `tools/bench/*` only if the bench cannot run on Linux as
it is, and then the smallest change that keeps the Mac's behaviour identical. Nothing under `src/`.

## Traps

- The bench starts a GameTest server through Gradle with its own arguments; `once` exists so nothing has to stay running. Do not
  try to keep a checker alive on GitHub.
- `tools/gradle.sh` points at a JDK on the external SSD. On GitHub use the runner's Java.
- The picture maker is our own software renderer (`gates/realms/preview`). It needs no display. If it asks for one, say so.
- GitHub's Linux runners are free for this public repo; the macOS ones are scarce. Nothing here may use a macOS runner.
