# Kit sheet C: nothing can float, and the blockout

Read `BUILDER.md` in this folder first.

## Why

Saltcomb's first test found 76 floating blocks, Ash Isles 893, and each builder ran the float test 13 to 16 times per gate. And
builds drifted from their paintings because nothing fixed the big shapes before the detail went on. You add two things to the kit:
a pass that makes small strays impossible, and a blockout, which is a gate's big shapes written as data that the orchestrator can
see from the painter's camera before any detail exists and that the gate's code then reads by name.

## C1. Settle: small strays can't survive

- Make `FloatCheck`'s block kinds usable from the kit: a public `FloatCheck.kindOf(BlockState)` (or move the table to
  `kit/Kinds.java` and have `FloatCheck` use it). Same answers as today.
- `kit/Settle.java`: `Settle.run(V v, Tally t)`, chunk-local, over the 16 x 16 columns of the chunk (it may read the one-block margin).
  In this order:
  1. hanging things (`HANGS`), top down: removed unless the cell above holds or is another hanging thing that stayed;
  2. things that stand (`ON_GROUND`, tall plants as a pair), bottom up: removed unless the cell below holds (`V.holds`);
  3. things that lean (`BESIDE`): removed unless a full block touches a side;
  4. things afloat (`AFLOAT`): removed unless water is below;
  5. specks: a body block with no body block among its 26 neighbours and nothing holding it from below: removed.
  A removed cell becomes air, or water if it sits below the land's `water()` level inside water.
- `Tally` counts removals by block id and by the label of the piece covering the column, thread-safe, readable as one line
  ("settled 14: 9 crypt_candles in nave, 5 keep_vines in gate"). A static `Settle.lastTally(Land)` is enough for the bench to print.
- `kit/SheetLand.java`: `abstract class SheetLand<M extends Mats> extends KitLand<M>` with `public final void paint(V v)` that calls
  `protected abstract void draw(V v)` and then `Settle.run`. **Existing lands keep their own `paint` and are not touched**, so their
  blocks can't move.
- GameTests: `realm_settle_clears_strays` (a probe land with a lantern, a plant, a vine, a lily and a lone block in the air, like
  the `Probe` in `RealmTests`: after `SheetLand.paint`, `FloatCheck.scan` is clean) and `realm_settle_keeps_the_sound` (the same
  things properly held all stay).

The float test stays the proof for anything big. Settle is not a licence to place carelessly: its tally is printed so a builder
fixes the cause.

## C2. The blockout

New package `src/main/java/dev/awaken/gates/realms/blockout/`. Data lives at
`src/main/resources/data/awaken/realm_blockouts/<id>.json` (the blueprint id's path, for example `quiet_barrow`).

```json
{
  "schemaVersion": 1,
  "half": 220,
  "mouthYaw": 0,
  "masses": [
    {"name": "moor",    "op": "add", "shape": "box",      "from": [-220, 0, -220], "to": [220, 63, 220], "role": "ground", "rough": 3},
    {"name": "hill",    "op": "add", "shape": "dome",     "at": [0, 64, 40], "r": 70, "h": 34, "role": "ground", "rough": 2},
    {"name": "ship",    "op": "add", "shape": "box",      "from": [-8, 98, 10], "to": [8, 106, 70], "role": "great"},
    {"name": "passage", "op": "cut", "shape": "box",      "from": [-3, 66, -30], "to": [3, 71, 40]},
    {"name": "stair",   "op": "add", "shape": "wedge",    "from": [-4, 64, -60], "to": [4, 98, 8], "up": "+z", "role": "walk"},
    {"name": "tower",   "op": "add", "shape": "cylinder", "at": [90, 64, -20], "r": 9, "h": 40, "inner": 7, "role": "wall"},
    {"name": "spire",   "op": "add", "shape": "cone",     "at": [90, 104, -20], "r": 10, "r2": 0, "h": 18, "role": "roof"}
  ],
  "sites": [ {"kind": "MOUTH", "at": [0, 64, -190], "radius": 6, "what": "mouth"} ],
  "cameras": { "painting": [0, 78, -205, 0, -4] }
}
```

- Shapes: `box` (`from`, `to`, inclusive), `cylinder` (`at` is the centre of its base; `r` or `rx` and `rz`; `h`; optional `inner`
  radius makes it a ring), `cone` (`r` at the base, `r2` at the top), `dome` (half an ellipsoid standing on `at`), `sphere`,
  `wedge` (a box whose top rises in a straight line along `up`: `+x`, `-x`, `+z` or `-z`; used for ramps, stairs and roofs).
  `rough` on a mass moves its top surface by that many blocks of smooth noise (`kit/N`), seeded from the land's seed.
- `op`: `add` or `cut`, applied in the order listed. Roles: `ground`, `wall`, `floor`, `roof`, `great`, `walk`, `water`, `glow`,
  `foliage`. Coordinates are the Realm's own, y is the world's. Cameras are `[x, y, z, yaw, pitch]` at eye height.
- `Blockout` (immutable, safe on worldgen threads): `load(String id)` from the mod's resources, and `load(Path)` from a file for
  the bench; `solid(x, y, z)`, `role(x, y, z)`, `ground(x, z)` (the top `ground`, `floor` or `walk` cell of a column, or -1),
  `mass(name)`. A `Mass` gives its numbers (`x0 y0 z0 x1 y1 z1`, `cx cz r h`), `contains(x, y, z)`, `top(x, z)` and
  `edge(x, y, z)`, the distance in blocks to its nearest face (positive inside), so detail code can say "the outer two blocks of
  the tower" without its own arithmetic. Reject each chunk against a mass's bounding box before testing cells: painting a chunk
  must stay under 30 ms with 60 masses.
- A parse error names the mass and the field. GameTest `realm_blockout_parses` loads a sample with every shape and checks a few
  cells of each, and `ground` under a `rough` mass on two seeds.
- `BlockoutLand` (a `SheetLand`): draws the blockout itself in plain mid-toned vanilla blocks, one per role and clearly different,
  so light and shape read (no dark blocks). `ground`, `halfSize`, `mouthYaw`, the sites and `biome` 0 come from the data. Register it
  in `LandPrograms` as `awaken:blockout`; it loads the blockout named by the blueprint's id. A blueprint that uses it has
  `"ranks": []` so the game never rolls it.
- `SheetLand` gains a constructor that takes a `Blockout` and fills `ground`, `halfSize`, `mouthYaw` and the sites from it, so a
  gate's own land only writes `draw`, `biome` and what is special to it.
- Ship one sample, `realm_blockouts/sample.json` with `data/awaken/realms/blockout_sample.json` (program `awaken:blockout`,
  `"ranks": []`, biome `awaken:realm/amber_meadow`) and `tools/autopilot/realm-blockout_sample.txt` whose only shots are the
  blockout's cameras, so `tools/preview/preview.sh tools/autopilot/realm-blockout_sample.txt` draws it. Say in your hand-back how
  long that preview's paint and shots took.

## Your files

`realms/kit/Settle.java`, `realms/kit/SheetLand.java`, `realms/kit/Kinds.java` if you make it (new); `realms/check/FloatCheck.java`
(only to expose the kinds); `realms/blockout/**` (new); `realms/LandPrograms.java` (one entry); `realms/RealmTests.java` (your three
tests); the sample data files and tour named above. Nothing under `preview/`, nothing under `land/`.

## Done when

`-Ptests=realm_settle,realm_blockout,realm_float` is green; the sample previews; Amber's three hashes are unchanged; committed.
