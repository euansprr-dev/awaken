# pack/comfort: hand-off (cloud session, 2026-10-24)

Stage 5 ("Comfort") of the Awakened Pack: JEI and Jade read as part of Awaken, and recipe lookup never spoils
content. Awaken plays exactly as before without JEI, Jade or Xaero's World Map: each integration loads only when its mod
is present, and the spoiler record is plain Awaken state. SPEC §14 has the rules.

## Done
- **Merged** `origin/build/awaken-v1` (2e05a76) into the branch.
- **JEI plugin** (`compat.client.jei`, from the local session, built and checked against JEI 31.14.0.72's classes):
  Awaken Crafting, Fabricator, Frame Forge, Tempering, Hearth Upgrades, Echo Duty, Essence, Core Fusing and Codex
  info pages, all drawn in the System's glass; Awaken's crafting-table recipes move out of JEI's grey tab.
- **Jade plugin** (`compat.jade`, `compat.client.jade`, checked against Jade 26.3.5): gate anchors (what the gate card
  shows at your PER), Precursor machines and ruins, the Hearth, role pads, Trophy Mounts, creatures and townsfolk, in
  the `awaken:system` theme.
- **One spoiler rule for every viewer** (this session). `compat.SpoilerRule` makes every decision (item shown, recipe
  shown, creature details, disguises never named) for any player, so the server can test it; `compat.Reveal` keeps a
  viewer's hidden set in step and reports only what changed. JEI's guard and Jade now only translate those answers
  into their APIs. The JEI guard also no longer hides everything for a tick when the client player is recreated
  (respawn, entering a gate) before the server re-sends the record. The record's recipe index is now dropped when the
  server stops (`forget()` existed but had no caller, so it kept the previous world's recipe manager).
- **Xaero's World Map shut in instanced dimensions** (this session, `compat.WorldMaps`). Xaero 1.47.1 has no setting
  to skip a dimension. I read its jar and xaerolib 1.8.3: the ignore list is hard-coded, "load/update chunks" are
  global per profile, and per-player server profiles need a permissions mod. So Awaken uses the control Xaero supports
  for servers, its own `xaeroworldmap:no_world_map` effect: on while you're in an instanced dimension (no particles,
  no HUD icon), off when you leave, and an operator's effect is never touched. Xaero still writes those chunks to that
  dimension's own cache; you just can't open the map there. A dedicated server needs Xaero installed for this
  (server-optional). "Instanced" is one definition now (`compat.Instanced`), shared with far view.
- **GameTests**: `compat_spoiler_rule` (the record), `compat_spoiler_guard` (the rule; the reveal as the record grows;
  creative, spectator and survival; the Codex; disguises), `compat_world_map_rule` (the effect's upkeep, with a vanilla
  stand-in since tests never load Xaero), `compat_instanced_dimensions` (was `compat_far_view_policy`), plus the
  look-at tests. Full suite in the VM: see the results below.
- **SPEC §14** updated: instanced dimensions, the rule and the reveal, the respawn wait, Xaero's decision.

## Left
- The comfort tour on the Mac (below). Nothing in the cloud can draw JEI, Jade or Xaero.
- Dedicated-server packs: put Xaero's World Map in the server profile too, or the map stays open in gates there.
- Optional: Xaero's map screen says "The map is disabled with a potion effect." Awaken could override that line
  only by shipping Xaero's lang key, and which one wins depends on resource order, so I left it.

## Tours for the Mac
- `PACK=comfort tools/autopilot.sh tools/autopilot/comfort.txt`. Check shots 01 to 04 (Awaken items hidden on a fresh
  player, then appearing after pick-up), 05 to 13 (each category in the System's glass), 14 to 20 (Jade on each
  target) and 21 to 22 (the map open in the overworld, shut inside the gate). In the log, the `[compat]` lines should
  read "world map open in minecraft:overworld", then "shut in awaken:gates", then "open" again after leaving.

## CI
- Cloud VM, final tree (5bf2109): **all 330 required GameTests passed**. An earlier VM run failed
  `powers_hits_ground_slam_lineup` once ("ravager did not move"); it passed in the baseline run and in the final one.
- GitHub run [40101234996](https://github.com/euansprr-dev/awaken/actions/runs/40101234996) on c2daae2: **not green**.
  Attempt 1 failed `fauna_p2_talents_dark` ("the rooms are dark") and `fauna_p3_talents` ("the grotto is dark");
  the one re-run (attempt 2) failed only `fauna_p3_talents`, on another light check ("in torchlight the player is
  safe"). Both are the known light-wait flakes (fixed tick waits for the light engine). They don't touch compat code,
  `build/awaken-v1` is red on the same kind (runs 40098866370 and 40098403960, `fauna_p3_caves_spawn`), and
  pack/throughput fixes them at the cause in 74bf971 (green there, run 40101221504). I didn't port that fix: it is
  four commits of test infrastructure still being finished on its own branch, and the main session had already
  merged this branch (dc42a65). Once pack/throughput lands, this branch's suite should be green as it is.
