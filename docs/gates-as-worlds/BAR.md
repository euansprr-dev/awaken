# The bar every gate must reach

Euan, 10 Nov 2026, looking at the first pass of The Vine Seat, gate 3: "It's semi-right in composition, but it's still like very blocky
and very like not at all on par with the bell or with the previous thing... we need to really think of depth and everything, like make
sure it's not just kind of vanilla blocky like this. Like it should never be vanilla blocky... there's no atmosphere, there's no
environment, there's no nothing that makes this world feel like truly alive here."

The bar is **Amber Reach** and **The Hour Crypt**. Before you build anything, open `run-preview/realm-amber/sheet.png` and
`run-preview/realm-hour/sheet.png` (or run the preview on `tools/autopilot/realm-amber.txt` and `realm-hour.txt`), then read
`land/AmberReach.java` (`Heart`, `Spire`, `Plinth`, `growth`, `shade`) and `land/HourCrypt.java` (`Nave.column/vault/dress`, `Drum`,
`GreatBell`, `Hall.dress`, `lay`). Those are what "good" means, more than this page is. The first pass of The Vine Seat is what falling short looks like:
good layout, big flat plain faces of cubes, pale and even light, a bare room with four lanterns, a bare lagoon, nothing moving.

## What the first two have, and a gate must have

**1. One colour world, committed to.** Amber is all honey and dark timber; the Crypt is blue-black and candle gold. Pick a palette of two
or three hues and grade everything to it: fog, sky, sky light, block light tint, ambient light, water, grass, foliage. Fog is part of the
picture: start 18 to 64 blocks, end 230 to 620 (enclosed places 230 or less). Distance dissolves in layers. Never a pale neutral sky with
thin fog; never push fog out to show more.

**2. Light with contrast.** Something in every view glows against something dark: honey panels against black timber, a thousand candles
against blue stone. Thousands of small sources, or large emissive surfaces, placed on a rule, never a few lanterns. A view with even
light and nothing bright fails. Daylight gates need hard dark shadow and warm lit surfaces.

**3. Depth in every surface. It must never read as vanilla blocks.** No wall plane wider than five cells without something stepping in or
out by at least a block: pilasters, niches, recessed panels with stair-cut reveals, plinths at the base, cornices of upside-down stairs,
slab lips, ribs, buttresses, galleries, balconies. Stairs, slabs, walls, bars, chains, trapdoors, candles and plants in the facade, not only
in the furniture. Every giant form has human-sized detail on it, and every room has at least three levels of scale: the mass, the
architectural rhythm, the small things on it. A big plain block texture is never the detail. Rooms are never a box with a flat ceiling:
vaults, ribs, piers, upper galleries, things hanging, things in the corners.

**4. Composition in layers.** One enormous thing in sight from arrival; something close to frame it; layered silhouettes dissolving behind.
Every shot in your tour should work as a picture. If a camera shows a wall or a bare floor, the problem is usually the build, not the camera.

**5. No bare ground.** Every walked surface is dressed: grass, moss, petals, bones, candles, rubble, roots, puddles, cracks, wear lines,
a pattern set into the paving. Water has reeds, lilies, foam at its edges, stones. Terrain has bands and material change; rock has strata.

**6. It's alive and it moves.**
- `sky.air` in the blueprint (client air and small life from SPEC 38.9, the same as world floors): pick from `mist shaft_dust smoke rain lightning procession far_toll ash snow petals paper sand spores embers fireflies steam wind stars rain_up` and `dust drips moths bats pale_fish rats`. At least four.
- Override `air()` in the land with `kit/AirPlan`: mist layers at real heights, shafts of light where light really comes in, the great lights, still water. Lens reads this in game.
- `life.herds`, `life.predators`, `life.ambient`: open lands are never empty. Existing creatures include `pollen_grazer amberfly drone_bee honey_slime bloom_sprite bog_toad briar_sow brook_otter chime_cricket crag_feather dart_frog drip_lurker dune_ray empress_moth frost_stag glass_scarab gloam_snail hollow_owl honey_bear lumen_moth lantern_maw mire_mother old_antler peak_kite plumed_coil scree_marmot sporeback_snail tide_scale wisp_hound` (check the id exists before using it; say so plainly if a land needs a creature nobody has built).
- Biome `ambient_particles` of its own, `audio/ambient_sounds` with `additions` built from named vanilla recordings (see the sound rules below), music for the theme.
- Folk in the village, with their own speech. Something physically moving in the build where the rule allows it: falling water, swinging chains, curtains, tolling.

## The preview report is a gate, not a suggestion

`tools/preview/preview.sh <tour> ` now prints for each shot `relief` (planes crossed per thousand neighbouring pixels), `wall` (share of the view on the largest
upright plane), `shaped` (share of the view that isn't a full cube) and `contrast`, and flags FLAT, PLAIN and WASHED. Reference figures: Amber's outdoors 200-390 relief, shaped 10-16,
contrast 130-160; Amber's halls relief 50-75, shaped 7-23; the Crypt relief 55-137, shaped 5-10. The first Vine Seat had interiors at relief 6-13, shaped 0, which is what
Euan called blocky. Fix every flagged shot unless it's plainly deliberate (a closed door). Clearing the flags is the floor. The bar is the picture beside Amber's and the Crypt's sheet, viewed at full size.

## The rest of the standard

Every entry in `ROSTER.md` is one authored place: its painting first (`docs/gates-as-worlds/img/paint-<id>.jpg`), its one physical rule that
changes how you move or fight (its own `Realm<Rule>.java`, one line in `RealmLive.wake`), its great thing, its Lord's place by the eight rules,
Waystones in bigger lands, finds, wardens, a village of non-human folk who are not allies and give no directions. Look at the painting and the sheet
together and fix what falls short; be honest in your report about what still does.

Material families are per theme, shared by that theme's gates (Knell for Bell Crypt, amber for Hive, Fane for Overgrown Temple), each at least 20 own blocks. A
family that repeats an existing one's look fails. Vanilla share of a view at most 30%.

Nothing floats and everything is reachable: every land has `realm_<id>_walks_to_everything` and `realm_<id>_nothing_floats` (see `RealmTests`). Amber's golden hashes must not change (kit edits must keep them).

Text is natural speech, as a person would say it aloud, never poetic riddles. The System is the only guide, never pre-announce a boss. New blocks and items need a recipe, loot, or `Sources.declare` (test `journey_ledger`).
Sounds start from vanilla recordings named in `sounds.json`; no synthetic dings; nothing that makes static. Public repo: no Mojang or Fresh Animations files.
