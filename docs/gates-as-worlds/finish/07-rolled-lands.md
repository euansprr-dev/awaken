# Brief 07: rolled lands (procedural open worlds)

**Why.** Euan, 11 Nov: "I don't think we should build all the 88 gates by hand... It would take weeks. What about some kind of
really well built procedural worlds?... a few hand built ones, and then based on what we hand built, those procedurally generated
worlds, they all feel very different and still feel just as immersive... I just care about immersion, variety in like the kind of
the different mobs there are, the amount of mobs there are... the atmosphere, the world, the amount of mobs, the path that you
take. Sometimes maybe you have to walk up to the top of a mountain, sometimes you have to do something different... the big
worlds we've built so far, I think they look pretty good, like with the right amount of mobs in them... if we can procedurally
generate some worlds to those levels as well, that could be really cool."

The dungeons already work this way: `GeneratedCrawl` rolls a whole run from a seed and seventeen skins dress it. Nothing does the
same for **open land under a sky**. You build that: `GeneratedLand`, a generator that rolls a whole outdoor world from a seed, and
skins that dress it per theme, so that every gate in the world can be a land nobody has seen before.

**Done looks like.** `/awaken gate realm_enter E awaken:<a rolled land> <any seed>` puts you at the way in of an outdoor world with
a sky: you can see far, there is one great thing on the horizon, a path leads to it through country that rises and falls, packs
stand along the way, a Warden holds a place on it, the Lord waits at the great thing. Another seed is another world. Another skin
is another country. Laid beside Amber Reach's real-game shots, it does not look like the cheap one.

## Read first

- `docs/gates-as-worlds/BAR.md`, and look at the bar itself: `realms/land/AmberReach.java` (the one open land Euan has played and
  called "FANTASTIC"). Read how it makes ground (`ground`, its hills and terraces), cliffs, its spire and plinths, `growth`,
  `shade`, its river and road, where it puts dens, Wardens and the Lord, and its biome and air. That file is your quality target
  and your parts bin: lift its methods into a shared kit rather than re-inventing them. **Never edit that file**; its golden
  hashes must not change.
- `realms/land/QuietBarrow.java` and `SaltcombFlats.java` (two more open lands: a night moor with a ship on a hill, a salt desert
  with a half-buried city) for how different a land can be on the same kit.
- `realms/kit/` : `KitLand`, `Mats`, `Accents`, `N` (noise), `V`, `Road`, `River`, `Build`, `AirPlan`, `Settle`, and the pieces in
  `kit/piece/` (`Hut`, `Stall`, `Pavilion`, `Pylon`, `Tree`, `Boulder`, `Lamp`, `Updraft`, `Find`, `MouthRing`).
- `realms/kit/crawl/GeneratedCrawl.java`, for the pattern to copy: one generator, a `Style` and a `Trim` per skin, legs made as the
  land is laid out (`leg(...)`), packs by herd, the fall catch, the checks.
- `docs/SPEC.md` §50.7 (Realms), §50.8 (the Build Kit), §50.9 (The Run: every gate is a run of legs; "The right amount").

## What to build

**`realms/kit/land/GeneratedLand.java`**: an abstract land a skin extends, as `GeneratedCrawl` is for dungeons.

1. **Landforms**, rolled from the seed. Each is a height function plus where the way goes. Start with these five and make each
   read as itself from the way in:
   - **The Ascent**: one mountain. The way in is at its foot; a path switchbacks up terraces and a cut stair to the summit, where
     the Lord's place is. You see the summit from the first step, and you see where you came from, from above, all the way up.
   - **The Valley**: a river valley between two ridges, the great thing at its head, the path beside the water, side hollows where
     packs stand. (Amber Reach is this, by hand.)
   - **The Basin**: a wide bowl, terraces stepping down to a lake or a floor in the middle with the great thing on an island or a
     rise. You arrive on the rim and see all of it.
   - **The Causeway**: flat marsh, salt or dune with one raised way across it to a ruin on the skyline; islands of firm ground
     beside the way, where the fights are.
   - **The Isles**: four to seven tablelands or islands with bridges between them, each a different height, the last the highest.
   Sizes by rank from `docs/gates-as-worlds/PLAN.md`: a heart about 500 across at E up to 1,300 at S, ended by cliff, sea or storm,
   never an invisible wall. The walk from the way in to the Lord is a run's length: 15 to 25 minutes at E, more with rank.
2. **Three scales of relief** on every landform (BAR.md): the big shape, then ridges, gullies and outcrops at ten to thirty
   blocks, then ground that steps in half blocks, with drifts, stones and growth. No flat patch over five by five on a path's
   edge. No cliff that is one plane.
3. **The great thing**, chosen by the skin from a small set of big builders (a spire, a great tree or cap, a ring of standing
   stones round a pit, a ruined hall with no roof, a stepped temple), sixty blocks tall or wide at least, seen from the way in.
   The Lord's place is in or on it: a level floor at least radius 22, open above or 17 high.
4. **The run** (SPEC §50.9), made as the land is laid out: the first fight within ninety seconds' walk of the way in; a den's
   pack every forty to seventy blocks of path, each in a place it holds (a hollow, a terrace, a ring of stones); two or three
   ambush sites; one or two Wardens with their company on a place that closes the way (a bridge head, a gate in a wall, the foot of
   the stair); one stretch that is about moving (a broken bridge, a cliff stair with gaps, stones across water, an updraft where
   the theme has one) with the fall catch on it; a hidden place off the path that pays; the Lord. More dens and Wardens with rank.
   Packs come from the theme's own monsters, mixed: no two neighbouring dens the same company. The wandering packs of SPEC §50.9
   "The right amount" already fill the country between; leave room for them.
5. **Skins**, one class each in `realms/land/`, as the crawl's skins are: the ground and what is under it, cliff rock, water or
   what stands in for it, what grows (trees or their stand-ins, undergrowth, things on walls), what is left behind (ruins, huts,
   lamps, stalls), the great thing, the sky (its own biome: sky colour, fog far enough to see the great thing, what drifts, ambient
   loop, music) and Lens look, and its small life. Use each theme's existing blocks and plants (`ThemeBlocks`, `DepthBlocks`, the
   gate worlds' `*Dress` classes, the Realm families). First five skins, one per landform so the first five worlds differ in
   everything: **Ember** (The Ascent: a dead volcano, ash and iron), **Overgrown Temple** (The Valley: jungle), **Frost**
   (The Basin: a frozen tarn), **Glass Desert** (The Causeway), **Fungal** (The Isles: caps as tablelands). A skin may be rolled
   on any landform; these are only the first pairings to look at.
6. **Blueprints** for them in `data/awaken/realms/` (form `land`, the theme, every rank from the theme's first to S, `"ready"` left
   out), biomes in `worldgen/biome/realm/` and in `dimension/realms.json`, looks in `looks_gates.json`, a line in `LandPrograms`,
   tours (a camera at the way in looking at the great thing, then leg by leg, in daylight, `wait 200` before each shot), and
   `realm_<id>_walks_to_everything` and `_nothing_floats` tests as every land has.

## Checks (never on the Mac while Euan plays: `BUILDER.md`)

- `tools/check-ci.sh compile` after each sizeable piece.
- `tools/check-ci.sh bench awaken:<land> 1 2 3` : `floats: 0`, `reach: all`, a `route:` line with every leg. The bench also prints
  each shot's light; outdoors by day aim for a middle of 90 to 150.
- One real-game walk per land when the bench is clean (`tools/tour-ci.sh ... LENS=cinematic RD=28`), read shot by shot. Put the
  way-in shot beside Amber Reach's (`run-autopilot/screenshots-ci/realm-amber*`) and say plainly where yours is weaker.
- Hand back **after the first landform and skin is clean and walked** (The Ascent in Ember), before the other four, so the gates
  session and Euan see one before five are built on a pattern nobody has looked at.

## Yours

`realms/kit/land/*` (new), the new skins in `realms/land/`, their blueprints, biomes, looks, tours and tests, lang under
`src/main/merge/gates/`. One line each in `LandPrograms`, `RealmTests`, `dimension/realms.json`, `looks_gates.json`. You may lift code
out of `AmberReach.java` into the kit by copying it; you may not change that file. SPEC §50.9, a new part "Rolled lands".

## Traps

- **Everything is decided by position and seed.** A chunk is drawn alone with a one-block margin; anything that depends on what
  another chunk did will tear at the seam.
- **Nothing floats** (SPEC §50.6): trees, bridges, ruins and anything on a cliff must be held, and a carve must not cut a support.
  `Settle` removes small strays and counts them: fix causes, not counts.
- **Reach**: every leg must be walkable from the way in with a step of one, a drop of four and a sprint jump of three. A path cut
  into a slope steeper than one in one needs steps or a stair. Rivers cut a land in two: every crossing needs a ford or a bridge.
- **The edge**: end the land with a cliff, water or a wall of storm and fog. Draw far enough past the heart that the edge of the
  drawing is never seen from any leg.
- **Height**: the dimension is 384 tall. Keep the lowest water at 62 as the built lands do, and the summit under 300.
- **Cost**: chunk time no worse than Amber Reach's. No per-block allocation; cache a column's height once per chunk.
- **Do not make it a maze and do not make it huge.** Euan: "I don't want a maze", and "It would be way too long to like explore."
  One clear way to one great thing, with country either side that is worth looking at.
