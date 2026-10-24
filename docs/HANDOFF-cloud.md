# Hand-off to cloud sessions (2026-10-24)

Euan's 5-hour plan window ran out, so the remaining work moves to Claude Code cloud sessions on the repo
`euansprr-dev/awaken`. Each task below continues on its own branch; its work so far is pushed there (WIP commits
are unfinished and unbuilt).

## Rules for every cloud task

**Token budget first (Euan, 25 Oct):** "keep tests and verifications low for the cloud, make it focus on building and
coding to avoid using millions and millions of tokens. It should still do an excellent job though."
- Write only the GameTests that guard the core rules: one per mechanism, not one per case.
- Run the full suite in the VM at most once, at the end. GitHub CI runs it on every push anyway.
- No re-runs and no long investigations of known flakes (timing budgets go through AwakenTests.cpuBudget).
- Spend the effort on the build itself: the code, the look and the feel.
- Read CLAUDE.md and docs/SPEC.md first. Ignore Mac-only paths in them (/Volumes/MEDIA/..., tools/gradle.sh's
  JAVA_HOME, slot.sh): in the cloud VM, run `./gradlew --console=plain build` directly.
- The `.ref/` reference sources aren't in git. After a first `./gradlew build`, read the decompiled Minecraft and
  NeoForge sources from the moddev artifacts: `find ~/.gradle build -name '*neoforge*sources*.jar'`, then unzip that
  jar. Never guess a 26.3 API; it isn't 1.21.
- The build needs these hosts (the environment's Custom network list): maven.neoforged.net, api.modrinth.com,
  cdn.modrinth.com, piston-meta.mojang.com, piston-data.mojang.com, launchermeta.mojang.com, libraries.minecraft.net,
  resources.download.minecraft.net, api.foojay.io. If they're blocked, write the code carefully and let GitHub CI
  build it (push, then read the run with `gh api repos/euansprr-dev/awaken/actions/runs?branch=<branch>`).
- First merge `origin/build/awaken-v1` into your branch. Commit small, each message ending with
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Push your branch; the GameTests workflow tests every
  push to pack/** and lands/**. CI green is the gate. Never push build/awaken-v1; the main session merges.
- Known timing flakes, until pack/throughput lands: fauna_p3_caves_spawn, fauna_p3_talents, anomaly_* platform
  waits, gates_interior_build_budget. Re-run once (`gh run rerun <id> --failed`) before blaming your change.
- No screenshot tours in the cloud: they need the Mac's GPU. List the tours your work needs in your final report;
  the main session runs them on the Mac later.
- Finish with a short report in docs/handoff/<branch-name>.md: what's done, what's left, the tours to run, the CI
  run id.

## Tasks
| Branch | Task | Left to do |
|---|---|---|
| pack/throughput | Test speed | Finish the `-Ptests=<prefixes>` GameTest filter (AwakenTests + build.gradle + CLAUDE.md). Fix the timing flakes at the cause: real light checks instead of fixed tick waits (fauna_p3_*), a chunk ticket or forced chunk for anomaly platform waits, a robust statistic for gates_interior_build_budget, own the shared spot in gates_surface_respects_builds. Skip the template-world part (it needs the Mac). |
| pack/sound | Adaptive gate music | Layered stems for 14 themes and the mixer are done, and all 324 tests passed locally. Left: the listening sheet (docs/audio/listening-*.html), the SPEC rules, then CI. Tour for the Mac: tools/autopilot/sound.txt. |
| pack/look | Look switch + Lens far depth | The screen and DH real depth are done on OpenGL. Left: on Vulkan, DH depth reads as a constant, so detect it and fall back to no DH haze there (never a ring). The Ultra tier's disabled reason should say it's OpenGL-only. Keep FarTerrain generic (Far Sight will plug in). SPEC §14, then CI. Tours for the Mac: tools/autopilot/look.txt, lens-dh.txt. |
| pack/comfort | JEI/Jade plugins, spoiler guard, Xaero off in instanced dimensions | In progress (adf1f00). Left: finish the plugins and the spoiler rule, GameTests for the rule, SPEC §14, CI. Tour for the Mac: tools/autopilot/comfort.txt. |
| pack/together | Multiplayer audit (docs/multiplayer-audit.md) | **Paused by Euan; don't start until he says so.** Mostly built; WIP ebf8841 is untested. |
| pack/holo-ui | Holo notification cards polish | The Holo session's branch; it pushes its own WIP. Left: the Vulkan check of the cards (a Mac tour), merge build, CI. |
| lands/slice, lands/biomes-2 | Elderwood and Lumen polish; Glass Steppe and Shattered Highlands | The terrain session's branches; it pushes its own WIP. Left: polish, CI. Tours on the Mac. |
