# Brief: The Wilds (the Ascent, Phase 2) — branch `pack/ascent-wilds`

You build Phase 2 of the Ascent (docs/plan-ascent.html §6, "The Wilds: a world built to be crossed") for AWAKEN, a
NeoForge 26.3 mod. Read first, in order: docs/ascent/BRIEF.md (Euan's words and the 9-point "genuinely magical" bar:
this is the contract), docs/plan-ascent.html §6 and §8, docs/ascent/AUDIT.md "The overworld today", CLAUDE.md,
docs/SPEC.md (§12 API, §17 the Ascent so far), docs/HANDOFF-cloud.md (cloud rules, including Euan's token budget:
few tests, build first). Start from `origin/pack/ascent` (the movement work this builds on), then merge
`origin/build/awaken-v1`.

## What exists (the movement session owns it; don't edit `dev.awaken.body.*`)
- The ladder: Runner (everyone) → Tether → Leaper → Wings → Skyborn → Sovereign. Public API
  `dev.awaken.body.ascent.Ascent`: `has(Player, Tier)`, `highest`, `grant(ServerPlayer, Tier, source)` (grants that tier
  and all under it), `onGranted(listener)`, `state(ServerPlayer)`, and `arrivedByMovement(ServerPlayer, seconds)`: true
  when a third of the last stretch was spent swinging, gliding, flying, wall-running or hanging and no block was placed
  (a tower never counts). Ropes bite any solid block within 44; wings glide ~1.15 b/t at 6° with a 9:1 ratio; a dive
  booms at 2.2 b/t; flight cruises 1.25, boosts 2.25.
- If you need something more from the movement module (an updraft force on the body, a "windway" push), ask in your
  hand-off note; don't reach into its internals. Updrafts and windways can be server-side forces through a small
  public hook the movement session will add; design yours against `core.api` and say exactly what you need.

## What to build (plan §6; numbers you decide go into SPEC §17.4 in the same commit)
1. **Precursor wards.** Around Precursor sites only (ruins incl. sky platforms, Tether Spires, hero-tree caches): a block
   a player places within the ward crumbles after 3 s and drops back to them (own crumble look and sound, never a
   vanilla particle); rank-door plating is unbreakable inside a ward (above/AboveBlocks.java:37-39, the pickaxe tag in
   src/main/merge/above/...). Hearths and the open world untouched. Existing ruins get wards on load. A ward is
   data on the site (structure bounds + radius), queried cheaply (no per-tick scans of the world). Pattern for
   cancelling place/break: veil/VeilEvents.java:220-231. Feature flag `wards` (default on).
2. **Sky platforms rebuilt** (above/ruin/PrecursorRuinStructure.java:26,56; RuinBuilder.sky() :695): raise decks to
   150+ above the ground under them; chains of anchor blocks hang from the keel down to grappling height (swingable
   with the rope from the ground); updraft pylons beside them; the deck becomes a reward room with a Precursor core that
   grants **Wings** (`Ascent.grant(p, WINGS, "sky platform")`) the first time a player reaches it by movement (a scene
   hook; the Wingborn trial itself is Phase 3's); afterwards sky-only loot (feathers of light, Aether cells) that ground
   vaults can't drop; the best roll and the route record pay only for `arrivedByMovement`. "Reached" today:
   world/landmark/LandmarkWatch.java:73,113. The rumour "Bring wings." (LandmarkKind.java:23-25) changes to say a rope
   will do.
3. **Tether Spires** (new, a `wilds` package): Precursor pylons with anchor rings in forests, ravines and on cliffs; a
   swing route climbs to a cache at the top, with a race line (start/finish rings: reuse hearth/training's checkpoint
   rings and ghost) and a record. Rare enough to feel found (target: one within ~600 blocks of anywhere in forested
   biomes).
4. **Hero trees** (terrain/hero/ElderColossus, LumenSpire via HeroTree.java:23,51): a crown cache and a vista point on
   the Colossus reached by swinging its branches; a glowing night route up the Lumen Spire. Shard Drift
   (terrain/landmark/ShardDrift.java): a stepping-stone course to a crystal at the top. The terrain session owns those
   blueprints: add your reward points through their placement data, or ask (note in your hand-off).
5. **Windways**: visible streams of air between landmarks; gliding into one carries you (the force via the hook above).
   Their look is light and moving air, own render types; perf budget: one draw each, nothing per tick per block.
6. **The Horizon line**: a second HUD line just for exploration under the Now line (signal/client/TrackerHud.java:66-69
   draws only the top suggestion today): the nearest site you can reach with your current tier and, now and then, a
   tease of one you can't ("needs Wings"). Gates and requests no longer hide it. Draw it through `signal.client.HudLook`
   (the Holo look), never raw fills.
7. **Directives and discoveries**: exploration directives (data files in data/awaken/awaken/directive/, conditions on
   counters) that finally read the counters nobody reads (landmarks_reached LandmarkWatch:121, precursor_doors_opened,
   precursor_logs_read, ruins_found, anomalies_seen, rifts_closed, stars_opened, groves_visited, spirit_caches...);
   biome discoveries (promised in plan-v2, never built): first time in each Awakened biome = a discovery line.
8. **Telemetry**: XP by source and time by activity (none exists: Progression.grantXp :100-110), logged per play-day,
   so Phase 5 can tune pacing.

## The bar (BRIEF.md, short)
Every site must make a player say "I can get up there now" and want to go; arriving by movement must beat a tower
(timed, rewarded); own looks and synthesised sounds (tools/art/specs, tools/art/audiokit.py); 120 fps on the M4 at RD 16;
old worlds keep working (new sites in new chunks; wards on existing ruins on load). No mixins, no reflection.

## Rules
Commit small (end each message with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`), push
`pack/ascent-wilds` (CI runs the GameTests; green is the gate), one GameTest per mechanism (wards crumble placed blocks
and leave the Hearth alone; arrivedByMovement gates the best roll; the Horizon line's pick). Tours need the Mac: list
them in docs/handoff/pack-ascent-wilds.md with what's done and left, and the CI run id. Never push build/awaken-v1.
