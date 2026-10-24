# Carillon house rules (every builder, every stage)

The plan is docs/plan-carillon.html; the source of truth is docs/SPEC.md §16. The orchestrator is the session
"btw: kind of a side question…" ([8b9253]); it owns SPEC §16, the `core.api` hooks, carillon/main and all merges.

## Where you work
- Only in your own worktree, given as an absolute path in your brief. Start every Bash command with
  `cd <your worktree> &&`, use `git -C <your worktree>`, and absolute paths in file tools. Never call EnterWorktree.
- Never touch /Volumes/MEDIA/Awaken (the main session's tree), /Volumes/MEDIA/Awaken-play (Euan's play copy) or another
  builder's worktree. Never switch, reset or rebase a branch you don't own.
- Only your module's package, its resources (namespaced file names), `src/main/merge/<module>/`, your docs and your
  tools. Anything else (a hook signature, another module, SPEC) goes to the orchestrator first.

## Building and running
- Every Gradle run and every tour goes through the shared lock:
  `/Volumes/MEDIA/Awaken-carillon/tools/carillon/slot.sh build tools/gradle.sh 900 build`
  `/Volumes/MEDIA/Awaken-carillon/tools/carillon/slot.sh build tools/gradle.sh 1200 runGameTestServer`
  `/Volumes/MEDIA/Awaken-carillon/tools/carillon/slot.sh tour tools/autopilot.sh <script> [timeout]`
  Set `SLOT_WHO=<your spike or workstream>`. Run these in the background (`run_in_background`) when they may take
  more than 2 minutes, and wait for the notification rather than polling.
- Never launch the game client yourself (no runClient). Tours (tools/autopilot.sh) are approved: the window opens
  behind, muted. Never edit a script while it is running.
- Before using any Minecraft or NeoForge API: check docs/api/*.md, then grep /Volumes/MEDIA/Awaken/.ref/src,
  /Volumes/MEDIA/Awaken/.ref/neo and /Volumes/MEDIA/Awaken/.ref/assets. 26.3 is not 1.21.1. A compile error costs more
  than a grep.
- GameTests can flake under machine load: re-run once before calling something broken.
- Autopilot traps: docs in the repo and memory say every tour must `cmd awaken admin awaken`, then
  `cmd awaken powers starter 1` + `screen close`, or the first-power screen covers the shots. `quit` saves.

## Quality bar
- Euan judges Awaken against the best modpacks. Vanilla-looking particles, poses or buildings read as "an add-on, not a
  mod". Every visible thing needs its own look; say in your report where yours falls short.
- Performance budgets are in SPEC §16.10. Report numbers as p50/p99 with the machine's load (the perf line carries it),
  and say when a run was taken while something else was busy.
- No language model, no network calls at runtime, everything seeded. Server is authoritative. Codecs with
  `schemaVersion` for everything saved; `StreamCodec` for everything sent. Client code only in `client` packages.

## Commits and reports
- Small commits as you go, each ending with: `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`
- If you must decide something new, write it in your report (spikes) or ask the orchestrator (workstreams). SPEC is
  changed only by the orchestrator.
- Your final report: what you built, the numbers against the budget, shot paths, what surprised you, what you
  recommend for the real workstream, and anything left uncommitted (there should be nothing).
