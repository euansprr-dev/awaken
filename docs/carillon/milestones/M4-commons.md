# Milestone 4 · the Commons (the land outside the wall)

Branch `carillon/m4-commons` (from build/awaken-v1 74a646d3), worktree `/Volumes/MEDIA/Awaken/.claude/worktrees/walls-74d4bc`.
Built by the Walls builder, 2026-10-26, from Euan's playtest and screenshot (`/Volumes/MEDIA/carillon-ref/commons-road-poles.webp`).
His words: "we need to improve the farms here and just kind of the overall look of walking from the castle all the way
to the thing… too many monsters spawning the farms… we need to keep it low, because… you don't have a sword… the poles
here, they're not connected together or anything, they're a bit broken."

A Sonnet subagent drew the commons kit's art from my brief (`briefs/M4-commons-sonnet-1.md`). The painters, the
monsters, the tests and the look are mine. The plan is unchanged: every rule lives in the painters, so new worlds get it
and built cities keep their old ground.

## What changed

**The roads in (`CityCommons`).** The three roads keep their 8-wide line and gain a laid cross-section:
- the carriageway: setts from the gate through the barbican, then cobble kerbs, gravel shoulders worn to dirt in
  patches, two wheel ruts of coarse dirt with the odd puddle, and a trodden crown with a tuft of grass further out;
- the verges either side: grass with drifts of wild flowers and tall grass;
- from 26 out, a ditch a block down, then a hedge or a spruce fence;
- a field gate over a culvert every 48 along, by turns either side;
- milestones (the kit's) every 64;
- lamps on posts and pennant lines by turns every 14.

**The poles.** The old masts were a pole, a flagpole arm in the next cell and a banner one further. Nothing joined them,
so the arms floated. Each pennant line is now two of the city's line poles, one each side of the road at 5.5, with a
bunting line strung across at row 7 that sags to the middle. A district banner hangs from the line by each pole. The
High Gate yard's four corner masts became two pennant lines down its sides. The masts' code is gone.

**The fields (`CityFarms`).** The fields keep their lattice, so the patchwork from the sky stays. The boundaries are
hedges (oak, azalea and flowering azalea, two high in places), farm tracks or grass balks, chosen per pair of fields.
Each field is one of:
- arable strips five wide: four rows of one crop at the strip's own growth (wheat from green to gold, carrots,
  potatoes, beetroot, a pumpkin patch, ploughed or fallow), single plants lagging a stage, then an irrigation channel
  or a balk. Half of them have a scarecrow;
- reaped, with stubble and stooks every 4;
- an orchard of apple, blossom or cherry trees 5 apart;
- pasture with a stack of hay;
- meadow.

Lone oaks stand in some pastures and meadows, and hedgerow oaks along the roads. The outer ring of fields is more
pasture and meadow than plough, then a band of wild meadow to the reach: the tended land eases into the wild.

**The farmsteads (`CityFarmsteads`).** There are six farms. Three stand by the first field gate on each road, so the
farm's track crosses the ditch on the gate's culvert; three stand out among the fields. Each has:
- a farmhouse from the city's own grammar (Wallring timber and plaster, two storeys, a steep gable). Use "farm" makes
  its faces over open ground street faces;
- a timber barn on a cobble plinth with dark oak posts, its big doors open to the yard, a hay loft door to the road, a
  dark oak roof and hay inside;
- a haystack, a well, a hay cart, a woodpile, crates and sacks;
- a fenced paddock with a hay feeder and a trough, and a kitchen garden;
- a lamp where the track leaves the road.

**The road past the reach (`CityTrail`).** The city paints to 326. The Wayfarers' fire is 450 north, so the last 120
blocks of the walk were raw savanna. The High Gate road now runs on, laid on the ground as it lies at worldgen:
- the same carriageway, verges, lamps and milestones, but no ditch or hedges;
- a plank bridge with rails where it crosses water;
- the road ends at 424 north with a fingerpost (its arm painted with the camp's fire);
- a trodden footpath bends on to 10 short of the fire.

The camp itself is the Wayfarers' (the progression builder's scene).

**Monsters (`CityMonsters`).** Outside the wall, a natural spawn of anything hostile goes ahead by chance. That covers
vanilla's monsters, anything that is an `Enemy`, and Awakened creatures that aren't peaceful (the hunters):
- one in ten to 330 from the centre (the fields and the roads near the wall);
- rising to about two in five at the fire (450);
- 0.45 at 470;
- every one at 640.

To 470 out, a spawn is also refused when three hostiles already stand within 48 of it at about its height, so the
caves beneath and the surface don't count against each other. A few still come, so the first night keeps a little
threat. Inside the wall the lamps decide, as before.

This runs at the natural spawner's position check. 26.3's natural spawner calls `finalizeSpawn` directly and posts no
`FinalizeSpawnEvent`; the first tour showed it, with 30 hostiles round the fields.

How it fits the progression builder's camp (`living.wayfarer.Camp` on pack/progression): their camp cancels every
monster spawn within 32 of the fire and turns wanderers back out. Mine thins the ground around it, so the walk there is
quiet and the camp is the safe spot inside a quiet band. The two don't overlap in code: theirs is a SpawnPlacementCheck,
mine a FinalizeSpawn cancel.

**Kit (4 blocks, `city.kit.CommonsBlocks`/`CommonsKit`, art `tools/art/specs/city_commons.py`):**
- milestone (the bell cut in its face);
- fingerpost (two halves; arms painted with pictographs, to the camp or to a mine, and back to the city);
- scarecrow (two halves);
- wheat stook.

None has light, a block entity or loot.

## Tests (one per mechanism; `-Ptests=city_commons_`)
- `city_commons_poles`: every pennant line across the High Gate road is whole, with a pole under each end to the
  line's row. Every banner hangs from something; no flagpole arm is out in the air.
- `city_commons_road`: the kerbs are whole and the ditch's water runs both sides through the fields. There are culverts
  under the field gates and lamps along the road.
- `city_commons_water_still`: every water cell painted outside the wall has solid ground or water below and beside it,
  so nothing flows. This covers the channels, the ditches and the culverts.
- `city_commons_farms`: each roadside farm's track runs open from its yard over the culvert and through the field gate
  to the road's kerb, and the barn doors are open.
- `city_commons_monsters`: about one in eight over the fields, two in five by the fire, all of them far out; never a
  cow, never inside the wall.
- `city_chunk_alone_matches_context` gains eleven chunks of the commons: the road, each farmstead, the fields, a
  hedgerow, the meadow's edge.

## Tour
`tools/autopilot/commons.txt` on CI (`tools/tour-ci.sh tools/autopilot/commons.txt 1800 PACK=perf,world TECTONIC=awakened`):
- noon: the road out at eye height, a pennant line, the Gatefield farm, the fields from above, the road past the basin
  toward the camp, the road's end at the fingerpost;
- dusk: the road out and past the basin again;
- night: a minute in the fields in creative, then `/awaken city monsters 96`.

Shots are in `docs/carillon/milestones/m4commons/`.

## Not done (follow-ups)
- **Field workers by day.** The citizens' graph stops at the gates, so workers in the fields mean wall-walk-style graph
  nodes out along the roads and field stations in `citizens.json`. That is a citizens change for its own pass.
- **The road on to the first mine.** The fingerpost has a mine arm, but no mine is placed near the camp yet. The road
  can be extended once Between the Gates fixes where the first mine stands.
- **Old worlds keep their old ground.** Generator changes never touch a built city.
