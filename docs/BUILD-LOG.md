# AWAKEN build log

Newest first. Each entry: what landed, how it was verified, what's next.

## 2026-10-23

### Foundation (main branch `build/awaken-v1`)
- Project from the official NeoForge 26.3 MDK (NeoForge 26.3.0.95, Java 25 on the MEDIA SSD, Gradle cache on MEDIA).
- `tools/gradle.sh` watchdog builds; per-module JSON merging (lang, sounds, tags) at build time.
- API cheat sheets for 26.3 written from the real sources: `docs/api/{core,client,worldgen,entities}.md`.
- Core: module skeletons, config (SYNCED gameplay + CLIENT prefs), play-day clock, Five Forces damage types + entity tags,
  key mappings, `/awaken` command root, Director log, safe networking, GameTest harness, shared hooks (MobHooks with a
  vanilla fallback, PowerCoreHooks, HearthHooks), shared items (essence shard, lord heart), playtest mode + report script.
- Signal: server API, payloads, client HUD (glass windows that type in, Y/N actions, combat toasts, sounds).
- Progression: profile attachment (synced, versioned codec), XP curve matching the simulator, stats → attributes,
  counters, Truths / Titles / Latents / Paths / Directives / Daily Regimen engines, Day's End card, world-event Latents,
  data content (27 Truths, 27 Titles, 20 Latents, 12 Paths, 36 Directives, 20 drills), Status screen (Status, Truths,
  Codex board, Gate Map) and level readout.
- Verified: compiles; 4/4 GameTests pass headless (curve matches simulator, conditions, profile codec round-trip,
  level-up grants points and VIT raises max health).

### Wave 1 (parallel worktrees): powers, mobs, gates — in progress
### Wave 2a (parallel worktrees): descent, lucidium — in progress
