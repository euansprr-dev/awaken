# Trails (`dev.awaken.trails`)

Optional guidance for every part of the game (plan `docs/trails/PLAN.md`, SPEC §31). A trail is a short list of things to try. It opens
at a moment, waits in K > Journal > TRAILS, and reaches the task card and the Now line only when the player follows it.

## Writing a trail

One JSON file in `src/main/resources/data/awaken/awaken/trail/` (a module with its own data puts it under its namespace). The
format and every field are in SPEC §31. Rules the test `trail_defs_valid` enforces: a title of at most 28 characters, a blurb of
at most 120, a step line of at most 76 and a hint of at most 140; unique step ids; every token known; a step with no `done` is `manual`;
every `where` is a place some module registered; `after` names real trails and never loops. Write in plain speech and read it aloud.

A step is a condition on the world, so prefer a counter the game already keeps (`docs/COUNTERS.md`): `"done": ["counter:awaken:trophies_placed"]`
or `"count:awaken:kills:3"`. Where nothing counts the thing, add one `Progression.count(p, "awaken:...")` at the place it happens, list it in
COUNTERS.md, and use it. `a|b` makes alternatives. `show` hides a step until its tokens hold, so a long trail reveals itself.

## Adding words (tokens and places)

```java
TrailsApi.condition("room", (player, rest) -> ...);   // "room:sanctuary" -> test(player, "sanctuary")
TrailsApi.place("hearth", player -> blockPosOrNull);  // a step's "where": "hearth"
```
The Hearth's are in `hearth/HearthTrails` (read from the home the player lives at), Arms' in `arms/ArmsTrails`. Register at module init.

## Where things are

- `TrailDef`, `Trails` (the reload listener, catalogue order by weight then id), `TrailState` (attachment `awaken:trails`), `Conditions`.
- `TrailEngine`: the two-second look, opening, latching, finishing, the screen's copy, the buttons, the task card and Now line sources.
- `TrailChecks`: the shipped-trail checks. `TrailsCommands`: `/awaken admin trails ...`. `TrailsTests`: GameTests.
- `net/TrailPayloads`: `View` (server to client) and `Act` (client to server: follow, unfollow, hide, unhide, mark).
- `client/TrailSection` (the Journal's section and its browse view), `TrailCache`, `TrailsClient`.
- Seams outside the module: `progression/Checklist.registerLeading`, `progression/Advice`, `core.api.TrailsApi`, the autopilot's `trails` command,
  `ArmsArmoury` (Track calls `TrailsApi.unfollow`), `hearth/HearthModule`, and the counters listed in COUNTERS.md.

## Tests and tours

`tools/gradle.sh 900 runGameTestServer -Ptests=trail_`. Tour: `tools/tour-ci.sh tools/autopilot/trails-journal.txt 1200 PACK=perf,world TECTONIC=awakened`.
