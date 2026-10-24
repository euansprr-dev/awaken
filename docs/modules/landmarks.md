# Landmarks: worth finding, and findable (plan v2 §4.3 / P2)

The audit: the overworld's own landmarks existed but nobody found them. Five Precursor ruin types were rare and
unmarked, the Well (the Descent's entrance) stood 400–700 m from spawn with no sign, and the best ruin loot sat behind
rank doors. This part makes them part of the journey without changing their behaviour.

Built in `world/landmark/**` (client: `world/landmark/client/**`), wired by one line each in `world/WorldModule` and
`world/client/WorldClient`. Small additive hooks in `above/` (listed below). Textures `misc/landmark_{shaft,glow}.png`
from `tools/art/specs/anomaly.py`. Autopilot: `tools/autopilot/anomalies2.txt`.

## The landmark kinds (`LandmarkKind`)

| Kind | What | A reason to go from | Discovery key |
|---|---|---|---|
| The Well | the Descent's entrance (`Descent.wellPos`) | level 10 (stratum 1 plays at content level 17) | `landmark/well` |
| Sealed Vault | `precursor_vault` and the spawn vault | level 4; the spawn vault from awakening (its antechamber holds a Scanner) | `landmark/vault` |
| Monolith | `precursor_monolith` | level 4 | `landmark/monolith` |
| Ocean Lab | `precursor_ocean_lab` | level 6 | `landmark/ocean_lab` |
| Sky Platform | `precursor_sky_platform` (Y 262+) | level 20 (you need a way up) | `landmark/sky_platform` |
| Gate (per theme) | any gate of a theme you've never stood at | — | `landmark/gate/<theme>` |

Ruins are identified by their start chunk (`ruin:<x>,<z>`), the Well as `well`.

## Discovery (`LandmarkWatch`)

- Once a second per awakened player in the overworld (spread by UUID), and never in GameTests' background:
  - **the Well**: within 20 blocks of its centre (±24 in height);
  - **ruins**: the chunk you stand in is asked for its structure references (a map lookup; almost always empty). A
    Precursor ruin's start, if its chunk is loaded, gives its bounds; inside them (+10 blocks) you've reached it. No
    structure searches;
  - **gates**: every fifth check, `GateHooks.near` (20 blocks of a gate's mouth).
- Reached: remembered in the player's attachment `awaken:landmark_memory` (schema 2, kept on death, synced to its owner:
  reached ids, rumours told, the last whisper, and the places of every landmark reached or heard of).
- On the Gate Map: a landmark is placed when you reach it, Mira tells of it, a spirit whispers it or the Now tracker
  first points at it. Reached ones are filled diamonds, heard-of ones outlined, in the colour of their light (Well and
  ocean lab cyan, sky platform pale blue, vault and monolith amber), and they're listed beside the gates and lairs. The first of each kind is `Progression.discover(player, "landmark/<kind>", 0.15)` with
  `markNewThing` and a toast ("New landmark · Sealed Vault / Something waits in the antechamber · +XP"); gates per theme
  ("New gate · Bell Crypt / E-rank · +XP"). Every reached landmark counts `awaken:landmarks_reached`.
- Nothing before awakening (the world reveals itself at Accept).

## Hints

- **The Scanner** (`above/scan/Scanning`): its sky sweep now hears the Well too ("Something deep calls from below."),
  merged with the ruins and sorted nearest first (the four nearest), and it skips ruins you have already walked into.
  Beeps rise as you face the one you're pointing at; the omen line names it with a rough distance and a compass point.
- **The Horizon line** (`LandmarkHorizon`, SPEC §17; it was a Now line at priority 30, which gates and requests
  outranked): every landmark you haven't reached and have a reason to visit is a site (ruins within 700 m, the Well within
  1,500); the line shows the nearest one you can reach. A sky platform needs a way up (`HorizonHooks.WINGS`) instead of
  level 20 and is only teased until you have one. Never at home inside a Hearth (`HearthHooks.inside`), never in a gate;
  cached per player (10 s or 48 blocks). A landmark a shimmer grove's spirit whispered about is raised for 10 minutes:
  "Whispered: Sealed Vault".
- **The town's rumours** (`Landmarks.rumour(player)`): one line per landmark kind in Mira's voice ("A sealed bunker to the
  northwest, about 400 blocks. The door hums for some people. Not for me. The front room's open, though."), for the
  nearest landmark you haven't reached, have a reason to visit and haven't been told about; marks it told. **Needs the
  hearth module to call it** (request below).
- **A grove's spirit** (`Landmarks.whisper(player)`, used by the whispering shimmer grove): "“Something sealed, 420 m NW”".

## Visibility (`LandmarkMarkers` → `LandmarkClient`)

- Every 5 s per awakened player, the server lists the landmarks within 288 blocks whose chunks are loaded (exact bounds
  from the structure start; beyond that you couldn't see them anyway) and sends `landmark_markers` only when the set
  changed.
- The client draws, with `ExtractLevelRenderStateEvent` + `SubmitCustomGeometryEvent`, a few camera-facing quads on the
  beacon-beam pipeline (soft, fogged, no depth write):
  - **Ruins:** a soft light shaft (44 blocks; amber over vaults and monoliths, sea-blue over ocean labs rising out of the
    water, a pale shaft falling from a sky platform's underside). It shows **at dusk** (full from 12000 to 13600) and
    lingers at 30% through the night; nothing by day.
  - **The Well:** its four pillar tops glow at night with a faint 16-block shaft above each.
  - Peak opacity 45–55%: hints at the edge of sight, not beacons (gates already have those). Within 20 blocks the
    shafts fade out (you're there); the pillar glows stay.
- `MarkerLight` holds the curves (pure, tested).

## Loot

Every ruin now has an **outer cache** (`chests/landmark_outer`) outside its sealed part: the vault's antechamber (beside
the spawn vault's Scanner chest), the sky platform's deck before the cabin, the ocean lab's dome by the airlock, the
monolith's plaza beside the ladder. It always holds 2–4 essence shards, then 3–5 of iron, bread, mutton, arrows,
torches, gold, XP bottles, amethyst, golden carrots, lapis, a little scrap, an enchanted book, rarely a golden apple, a
diamond or a name tag, and half the time an enchanted iron sword, chestplate, helmet, bow or a shield. The rank doors
and the sealed rooms are unchanged.

## Hooks added outside this package (additive, no behaviour change otherwise)

- `above/scan/Scanning`: `Echo`/`EchoSource`/`addEchoSource` (other landmarks the sweep can hear), `setVisitedRuins`
  (skip ruins the player walked into), `Heard` + public `heard(player)` (the list the sweep uses; was a private list of
  ruins). The sweep's behaviour for ruins is unchanged; it asks the locator for 8 instead of 4 so filtering still
  leaves four.
- `above/ruin/RuinBuilder`: `outerCache(...)` places one chest per ruin at a fixed spot with no random draws, so ruins
  already half-generated keep matching their other chunks. Ruins generated before this change have no outer cache.

## Commands

- `/awaken landmark why` (anyone): the landmarks around you, which you've reached, which have a reason now, the Now line.
- Game masters: `near` (the list within 1,500 blocks with ids), `check` (look around now), `markers` (resend), `goto
  <well|vault|monolith|ocean_lab|sky_platform>` (stand 48 blocks south of the nearest, facing it, for screenshots),
  `forget` (forget reached landmarks; discoveries stay paid).

## Performance

Per player: one chunk-reference lookup and a distance a second, a gate lookup every 5 s, a marker list every 5 s (the
ruin locator's placement maths, ≤ 4 uncached terrain samples), a Now pick every 10 s (cached), all on the server
thread. No per-tick work, no structure searches, no chunk loads. Client: ≤ 16 markers, a handful of quads a frame, and
nothing at all by day.

## Tests (GameTests)

`landmark_discovery_fires_once` (the Well, a gate theme, and a ruin through the real chunk-reference path with a
structure start registered on the test chunk: each pays once), `landmark_scanner_points_at_nearest_unknown` (the Well
is heard, nearest first, and goes quiet once reached; a visited ruin goes quiet), `landmark_horizon_line` (no Well
site at level 9, "The Well" at 10, the rumour told once, the whisper raised, gone once reached; the pick and its teases),
`landmark_markers_and_light` (the light curves, markers only after awakening and within range, payload and memory
round trips), `landmark_outer_caches` (every ruin kind has one outer cache, vaults' outside the door; the table always
gives essence and a few useful things).

## Requests

- ~~hearth: Mira's rumours~~ done (integrator): `Rumours.give` asks `core.api.WorldHooks.landmarkRumour` when no gate is
  worth a rumour; `LandmarkModule` installs it.
- ~~core: `features.landmarks`~~ done: switches discovery, Now hints, rumours and markers off.
- ~~gates: landmarks on the Gate Map~~ done (integrator) through `core.api.MapMarks`, from the synced memory's places.

## SPEC-worthy decisions (for the integrator)

1. New §: Landmarks. Kinds Well / vault (incl. spawn vault) / monolith / ocean lab / sky platform / gate theme; reached
   = Well within 20, ruin bounds +10, gate mouth within 20; first of each kind `discover("landmark/<kind>", 0.15)`, gates
   per theme; counter `awaken:landmarks_reached`; attachment `awaken:landmark_memory` schema 2 (copy on death, synced to its owner, places for the Gate Map).
2. Reason-to-go levels: Well 10, vault 4 (spawn vault 2), monolith 4, ocean lab 6, sky platform 20. Now line priority
   30 (whispered 38 for 10 min); ruins ≤ 700 m, Well ≤ 1,500 m; never inside a Hearth or a gate.
3. Markers: within 288 blocks; ruin shafts at dusk (11000–15000, peak 12000–13600) and 30% at night; Well pillar glow at
   night (12000–23500); peak opacity ≤ 55%; shafts fade within 20–40 blocks.
4. Outer cache per ruin, loot table `chests/landmark_outer` (2–4 essence guaranteed).
5. The Scanner's sky sweep hears the Well and skips ruins already reached.
