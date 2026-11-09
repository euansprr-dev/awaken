# Gates as Worlds: the Realms plan

Status: **planning only, waiting for Euan's approval (9 Nov 2026).** Nothing here is in production. The proof code lives on the
throwaway branch `claude/gates-worlds-probe` and is never merged. The approval page is `docs/gates-as-worlds/page.html`.

Labels used below: **[seen]** measured or read in this repo, or in Euan's own save. **[research]** an outside source (linked).
**[guess]** a hypothesis still to test. **[plan]** a proposal.

## 1. What Euan asked for

Euan, 9 Nov 2026: "I really want to like push it to the maximum possible level where it feels like each gate is a world of its
own. Right now it just still feels like some like hallways. It doesn't go up, it doesn't go down. It's like it feels like just
linear hallways with, I don't like how you have to like go left and then right and then go back on your steps, and it's more of a
maze. I don't want a maze. I want like immersive worlds with their own like style ... Look at the overworld ... it has twigs, it
has stones, it has like the wild system ... It feels alive ... imagine gates that had their own like village styles, their own
ecosystem inside ... similar to kind of World of Warcraft ... each gate is completely different from the other. Right now there's
not enough gates, and they're not good enough either." And: "the two first gates in the world I went to were exactly the same.
They were both like Beehive." And to the planner: "the most premium, the most immersive, and the most enjoyable level possible."

- **Job:** step through a gate into somewhere you've never been, explore it because you want to, fight what lives there, come
  home with a story. Over the game, gates are the main source of many hours of play.
- **Pain:** walled routes, no height, dead ends you walk back from, nothing alive, too few gates, repeats.
- **Good looks like:** you arrive and can see far; something on the horizon pulls you; you walk any way you like, up and down;
  every world has its own ground, plants, animals, people, houses, sky and rules.
- **Weak:** pretty terrain with nothing to do; the same creatures everywhere; ten skins of one landform.
- **Unacceptable:** hallways dressed as outdoors; an invisible wall; getting stuck; long loads; frame rate collapse.
- **Non-goals:** the overworld; Provings, the Ritual and the story's scripted gates; combat itself; MMO servers; quest lists.

Rules that bind the design: server authoritative and multiplayer-safe; never pre-announce a boss; nothing fights for you; the
System is the only guide; every block and item has a recipe, loot or `Sources.declare`; every progression system registers on the
K menu Tasks page; plain speech; no Mojang assets in the public repo.

## 2. What is true today (audit)

### Measurements [seen]

`WorldShapeProbe` (test `worldshape_all`, branch `claude/gates-worlds-probe`, CI run 42578694514; raw table
`docs/gates-as-worlds/world-shape.tsv`) walked every non-secret gate world floor, 75 worlds, up to 8 layouts each.

| Measure, median of 75 worlds | Today | Rift proof | Amber proof | Real overworld |
|---|---|---|---|---|
| How far you see at eye level, median ray | 6 blocks | 32 | 28 to 40 | 32 |
| Far sight (90th percentile) | 19 | 156 to 312 | 148 to 380 | 156 |
| Sightlines that reach 64+ blocks | 0% | 35 to 39% | 35 to 40% | 40% |
| Land height range, 5th to 95th percentile | 5 | 113 to 150 | 78 to 100 | 46 |
| Height change along the way to the Lord | 3 | n/a | n/a | n/a |
| Walking in passages 7 blocks wide or less | 66% | 0 | 0 | 0 |
| Passage width, median | 5 | | | |
| Walked distance over straight-line distance | 2.5 | | | |
| Doors walked twice to see everything | 21% | | | |
| Plot that is walkable | 6.2% | | | |

Worlds marked "open" (21) are no better than caves (42): median sight 6 against 6, far sight share 1% against 0%. Only isles (5)
and void (1) see far.

Same skeleton: `hive/the_comb` and `overgrown_temple/jungle_courts` on the same seed give the same route to within a few blocks
(pictures on the page). The world never touches the path; only seed and layout do.

Euan's save `New World (11)` [seen, read-only]: gate memory `rolled: 6`, last themes `bell_crypt, hive, overgrown_temple`,
`claimedGates [50, 54]`, `themes_cleared ['hive']`, Atlas `arrivals: 14` across only three worlds (`queens_garden` 5,
`the_comb` 4, `brood_hall` 5). He cleared two Hive gates. The three gates rolled between them were never entered, yet they used up
the memory of the last three. At E, `deep` and `wonder` worlds are still closed, which left him two everyday Hive worlds and the
hidden `queens_garden`. If a world can't be laid out, `FloorBuilder.java:156` logs it and builds "today's rooms instead".

### Component map

| Component | What it really does | Evidence | Consequence | Verdict |
|---|---|---|---|---|
| `Director.roll` | rank, then theme not in the last 3 **rolled**, layout | `Director.java:77,142`; `GateMemory.java:33` | gates never entered eat the memory | fix first |
| `Theme` | 15 themes, 5 open at E | `Theme.java` | a small pool | keep as *families* |
| `FloorBuilder.programme` | a tree of rooms, dead-ends as caches and keys | `labyrinth()` = spanning tree, `deadEnds()` | walking back is built in | keep for Delves, with loops |
| `Plots` | 256 x 256 per floor | `Plots.java` | everything fits a small plot | replaced in Realms |
| `ChainLayout`, `CaveEngine` | places joined by one-slot links; 55 of 90 recipes are caves | recipe tally | one skeleton, many skins | keep for Delves |
| `CaveEngine.lid` | an invisible barrier 16 over the floor | §38.11 | "open" lands are roofed trenches | not used in Realms |
| `WorldCheck` tests | flat, plumb, detail per place | `DepthBaseline.java` | green tests, disappointing play | extended |
| `GateChunkGenerator` | no features: `applyBiomeDecoration` is empty | `GateChunkGenerator.java:139` | no overworld scatter can reach a gate | new generator |
| `Wildness` | overworld only | `Wildness.java:65` | no wild system in gates | add a Realm provider |
| `living/population` Ledger | saved on and read from `server.overworld()` | `Ledger.java:177,363` | no herds in gates | per-Realm ledger |
| Monsters in gates | room companies, or zones in caverns | `Rooms.java` | nothing lives outside a fight | ecology layer |
| `village/plan` | 4,185 lines of planners and painters | `village/plan/*` | villages exist, overworld only | reuse with kits |
| `citizens`, `CitizenBody`, `TownNpc` | Carillon's people | 10,973 lines | a person system exists | reuse for folk |
| Far Sight | needs `FarView.allowed` and the overworld skybox | `FarCollector.java:99` | no far land in gates | extend after pilots |
| Lens looks, Gate Air | per biome | `looks_gates.json` | scale with Realms | reuse |
| Break clock, Satchel and Spoils, bells, Goals, ledger | work on `GateRecord` and items | SPEC §7, §43.16, `Spoils` | independent of the shape | keep |

### Why the two earlier rounds fell short

Gate Worlds (§38, 28 Oct) changed what the rooms are made of. §38.5 says it directly: "a world floor first lays out today's rooms
for that floor exactly as a rooms floor would". Gate Depth (§38.14, 29 Oct) changed the surfaces of those rooms: ground, walls,
roofs, overgrowth. Its gates were flat, plumb and detail. Neither round measured sightlines, passage width, height of the journey or
retracing, so every test went green while the thing Euan disliked stayed exactly the same. Both changed the skin. This plan
changes the skeleton, and its tests measure the skeleton.

### Root causes

| Symptom | Cause | For | Against | Smallest test | If fixed |
|---|---|---|---|---|---|
| "Hallways" | places joined by one-slot links in a small plot | 66% of the way is 7 wide or less | void and plain worlds are wide, and still a programme of rooms | the probe | big |
| "Doesn't go up or down" | one floor line, no-jump rule on the way | route height change median 3 | one world (drop) reaches 72 | the probe | big |
| "Go back on your steps" | tree layouts with dead-end caches | 21% of doors twice | a hub layout shortens it but keeps the tree | the probe | medium |
| "Maze" | nothing to look at from where you are, so no reason for a direction | sight 6, far 0% | isles see far and were never called a maze [guess] | play a Realm | big |
| "Not alive" | no decoration, no population outside fights | `applyBiomeDecoration` empty | moths and such exist, client side | proof shots | big |
| Two Hives | memory counts rolls, not entries; pool of five | Euan's save | none | bag simulation | certain |

Still a guess: whether space alone makes a world feel like a world. Only playing a pilot can answer that, which is why the pilot
stops for Euan before anything else is built.

## 3. What outside work says [research]

| Claim | Source | How it applies here | Limits | What it means for us |
|---|---|---|---|---|
| Variety only counts if players perceive it and it changes what they do ("10,000 bowls of oatmeal") | [Compton](https://gamedeveloper.com/design/practical-procedural-generation-for-everyone-), [Rabii and Cook, FDG 2023](https://arxiv.org/pdf/2305.02131v1.pdf) | why 90 recipes felt the same | about generators generally | six-axis difference test, not a list of skins |
| Landmarks pull a player across open land; triangles hide and reveal goals | [BotW CEDEC 2017, translated](https://gist.github.com/idbrii/e39fe96279aa1670319bfa521d907399), [GMTK](https://gmtk.substack.com/p/how-nintendo-solved-zeldas-open-world) | a horizon goal instead of a route | hand-built world | landmark and sightlines are hard requirements |
| Cycles and shortcuts beat trees for dungeons | [Dormans on Unexplored](https://www.gamedeveloper.com/design/unexplored-s-secret-cyclic-dungeon-generation-), [Boris the Brave](https://www.boristhebrave.com/2021/04/10/dungeon-generation-in-unexplored/) | ends walking back in Delves | 2D games | loops are a rule for Delves |
| One seamless land with its own food chain makes hunting grounds feel alive | [Tokuda on MH World](https://www.invenglobal.com/articles/6549/creating-a-dense-open-world-a-lecture-from-yuya-tokuda-the-director-for-monster-hunter-world) | ecology layer | huge studio | small roster, each animal with habits |
| A boss-progress dimension you enter, leave and return to works in Minecraft | [Twilight Forest](https://blog.curseforge.com/best-minecraft-boss-mods/) | leaving and coming back | one dimension, one look | Mouth and Waystones |
| Zones feel lived in through settlements with their own look | [Warcraft zones](https://warcraft.wiki.gg/wiki/Zone) | Euan's own comparison | MMO scale | village kits |

Each claim above is checked here by the tests in section 6 or by Euan playing a pilot. All were read on 9 Nov 2026.

## 4. Options

| | A. Repair the floors | **B. Realms (recommended)** | C. Vanilla terrain, one dimension per gate | D. Hand-built maps |
|---|---|---|---|---|
| What | loops, wider links, more recipes, repeat fix | one continuous land per gate, own generator | noise settings per gate, a new level for each | saved structures |
| Feels like a world | no: still rooms | yes | partly: natural but generic | yes, best per world |
| Completely different worlds | no | yes: landform programs and kits | no: one engine's look | yes |
| Up and down, far views | small | full | full | full |
| Reaches 60 to 100 | yes, as skins | yes, in waves | yes, all alike | no |
| Mac cost | same as today | measured, near overworld | each level ticks, memory per level | small |
| Risk | low | medium | high: dimensions at runtime are not supported by NeoForge | scope |
| Reuse | everything | blocks, fights, loot, Lens, ground, villages, old worlds as Delves | little | little |

Recommendation: **B**, with A's repeat fix shipped first because it is cheap and independent. The gate loop (rank, Break clock,
Satchel, Lord, bells) stays. Floors stop being the shape of a gate and survive as Delves. What would change this: a pilot that
doesn't feel like a world to Euan, chunk cost well above the overworld's, or a Realm taking too long to make at the bar.

## 5. The design [plan]

A **Realm** is one gate: one continuous land with its own sky, or its own rock overhead. A Realm is authored as a *blueprint* and
differs from another by six things: landform, sky and light, living things, people and buildings, a physical law, and the place at
its heart. Any two Realms must differ on at least four of the six, and a test fails the build if they don't.

- **Arrival:** you step out at the **Mouth**, somewhere with a view. The Mouth is always the way home.
- **Pull:** one great landmark can be seen from the Mouth. Nothing names it.
- **Free land:** no route. Settlements, ruins, dens, caves, hidden finds. Walk, climb, drop.
- **Wardens:** three to five great creatures hold the land, in any order. Beating enough opens the heart. Never announced.
- **Lord:** in the landmark, in a real arena, as today's Lord (bell, loot, Satchel).
- **Delves:** the interiors, built with today's engines but in loops with shortcuts, carved into the same land with no loading.
- **Waystones:** at settlements and the Mouth; they carry you between places you have reached, and they bank your Satchel.
- **Sizes by rank:** E 800 across, D 1,000, C 1,300, B 1,600, A 2,000, S 2,400, Sovereign 3,000.
- **Time inside:** E about an hour to see most of it; S several. A Realm stands until its Lord falls or its gate breaks; you may
  leave and come back.

### Blueprint (data) and instance

```json
{ "schemaVersion": 1, "id": "awaken:amber_reach", "name": "Amber Reach", "family": "hive", "ranks": ["E", "D"],
  "land": { "program": "awaken:honey_highlands", "water": 62, "params": { "mesas": 0.4 } },
  "sky": { "biomes": ["amber_meadow", "comb_heights"], "lens": "amber_reach", "sun": 60 },
  "ground": { "kit": "soft_land_amber", "wild_max": 0.9 },
  "life": { "herds": ["pollen_grazer"], "predators": ["stinger_swarm"], "ambient": ["amberfly"] },
  "folk": { "kit": "wax_folk", "villages": [1, 3] },
  "law": { "kind": "updrafts", "honey_slows": 0.6 },
  "sites": { "wardens": [3, 4], "delves": 2, "finds": 40 },
  "heart": { "lord": "hive_regent", "seat": "mother_comb" },
  "moods": ["late_harvest", "swarming", "wax_moon"] }
```

`RealmPlan.of(blueprint, rank, seed, realmVersion)` is a pure function. It places the Mouth, rivers, settlements, sites and hero
pieces, and it is immutable, so worldgen threads can read it safely.

Contract: **trigger** a player enters a Realm gate → **input** gate record (blueprint, rank, seed, mood, version) → **processing**
`RealmPlan`, then chunks on demand (heights by the landform, set pieces clipped per chunk as the proof does, then the game's real
feature pipeline) → **output** ordinary chunks → **actions** enter, leave by Mouth or Waystone, die, clear → **saved** `RealmState`
per gate (schemaVersion; sites taken, Wardens down, settlement state, ecology ledger).

Invariants: there is always a way home; all land is reachable with the movement you have at that rank (tested); nothing is
announced; no repeat until every Realm open to your rank has been seen; old gates keep working as they were; one Satchel per player;
the same Realm for everyone in a party.

### Storage and lifecycle

- `GateRecord` gains an optional `realm` (blueprint, seed, version). Absent means the old floors.
- `RealmState` is saved with `schemaVersion`, kept with the gate, deleted when the gate is released.
- `GateMemory` gains `seen` and `reserved` Realm lists (schema bump, backfilled empty).
- Two new dimensions, 384 tall: `awaken:realms` (sky) and `awaken:realms_dark` (no sky).
- Realm cells at a pitch of 8,192 blocks; `RegionRecycler` erases released cells as it does today.
- Content version 10. Gates entered before stay on their version. Unentered older gates are rebuilt at start, as today.
- Server flag `gateRealms`, default off until Phase 4. Off: new gates roll as they do now; standing Realms keep working.

### The repeat fix

Per owner: a shuffle bag of the Realms open to the rank. Rolling a gate **reserves** one, so two standing gates are never the same;
entering it marks it **seen**; a reserved gate that closes unentered goes back in the bag. Nothing repeats until the bag is empty,
then it refills oldest first with a different mood. Phase 0 applies the same rule to today's themes, and to worlds inside a theme,
before any Realm exists.

### Integrations

| System | What happens |
|---|---|
| Break clock | unchanged; already deferred while a player is inside |
| Satchel and Spoils | unchanged; Waystones can bank it (decision 8) |
| Bells | a Lord's own bell and swallowed bells unchanged; each old world bell maps to a Realm or Delve |
| Provings, Ritual, story gates | stay on the built rooms |
| Twists | Double → an inner Realm; Red → Mouth seals until the Lord dies; Hollow, Treasure, Friendly, Echo, Within → events at sites |
| Modifiers | apply to spawns as they do; Low Gravity, Flooding, Lightless become strong fits for certain Laws |
| Fights and 41 bosses | Lords and Wardens reuse them; their arenas are set pieces |
| Lens, Gate Air | one look per Realm biome |
| Far Sight | Realms' heights are analytic, cheap to read; added after pilots |
| Atlas, Journal | a Realm page, drawn as you find places |
| Goals (Tasks) | "Go into a gate" stays; Realm steps register there |
| journey_ledger | every new block and item declared |
| Multiplayer | one Realm per gate, party shares it; chunks only where players are |

### Failure register

| Case | Detection | Containment | What you see | Recovery | Test |
|---|---|---|---|---|---|
| Blueprint fails to parse | registry load | that Realm disabled, logged | others roll | fixed in data | `realm_blueprints_parse` |
| No Realm open at this rank | bag empty | roll a floors gate | a normal gate | add Realms | `realm_bag_empty` |
| Chunk paint throws | try/catch, log | leave chunk as built | a hole, walkable round | bug report | `realm_no_holes` |
| Mouth in water or a wall | plan check | move to nearest good place | nothing | | `realm_mouth_safe` |
| Land cut off | flood fill at plan time | add a ford, bridge or stairs | a path | | `realm_all_reachable` |
| Party splits, one leaves | as today | Realm stands for the others | | | `realm_party_split` |
| Dies far from home | respawn as today | Satchel stake as today | | | existing `gates_spoils_*` |
| Save written mid-change | versions and flag | old gates untouched | | flag off | `realm_flag_off_rolls_floors` |
| Too much disk | size on release | cell erased | | | region test |
| Weak Mac | fps budget, render distance | Far Sight off, fog closer | shorter view | settings | tour perf |

### Performance and cost

Budget: chunk generation no worse than the overworld's on the same machine; frame rate no worse than the overworld at the same
settings (SPEC header gate-fight target stands); disk per fully walked E Realm under 40 MB and erased when the gate closes. Real
numbers from the proof: `over a 1,296-chunk sample on GitHub's macOS runner, the real overworld took 55 to 93 ms per chunk and 7 to 11 MB of region files; Amber took 12 ms and 4.5 MB (terrain 1.3 ms, decoration 0.6 ms); Cold Rift 14 ms and 5.5 MB (2.1 ms, 1.6 ms). The proof has no structures or mobs yet, so finished Realms will cost more`.

## 5b. The proof (disposable)

Branch `claude/gates-worlds-probe`, never merged. `RealmPocGenerator` and `RealmPocTerrain` make two 2,048-block lands (Amber Reach and
Cold Rift) in a throwaway dimension, by height functions and set pieces clipped per chunk, then the real decoration pipeline with
vanilla plants and the Soft Land features. Shots: tour run 42583676498. Numbers: run 42581257023. Today's shots: 42582719181.

It shows: one whole land works in the real game; the chunk cost above; the real ground and plant pipeline runs in our own dimension;
views, height and size clear the targets; and the reach test finds real faults (Amber 27% reachable without swimming because rivers
cut it; Cold Rift 0.7% with 1-block steps, 99.8% with 2).
It does not show: that a Realm is fun; that fights work in the open; that villages, animals or a Law feel good; that the look reaches
the bar; frame rate on Euan's 4K screen with Lens on.

## 6. Delivery [plan]

| Phase | What | Pace (from git history) | Gate |
|---|---|---|---|
| 0 | Repeat fix on today's gates | hours | bag simulation: no repeats in 10,000 runs |
| 1 | Foundation behind the flag: generator, plan, dimensions, Mouth, ground, wildness, tests | about a day | proof targets pass; old gates untouched |
| 2 | Amber Reach at full bar: land, ground, life, wax-folk, Wardens, Law, Lord, sound, Lens | one to two days | **Euan plays it, stop** |
| 3 | Cold Rift and Hollow Reach | one to two days | **Euan plays them, stop** |
| 4 | E pool to 12, flag on for new worlds | two to three days | E acceptance; Euan samples |
| 5 | D to S waves to about 60 | one to two weeks | each wave passes the difference test |
| 6 | Delves from old worlds with loops; Far Sight; 100+ later | ongoing | |

Pace reference: Gate Worlds took about 34 commits over 28 to 30 Oct for 90 worlds and about 230 blocks. All 41 bosses went to the Forge
bar in about two days. All of Awaken was built from the MDK in about 30 hours. These are machine hours; Euan's playing is the slow
part, and it is the part that matters.

Rollback: `gateRealms` off; new gates roll floors. Stop if a pilot doesn't feel like a world to Euan, if chunks cost more than 1.5
times the overworld's, or if fps in a Realm falls more than 10% below the overworld's at equal settings.

### Acceptance tests (before any Realm counts)

Land shape: median sight at least 30, far sight (90th) at least 150, at least 30% of rays past 64, height range at least 60, at
most 15% of outdoor walking in passages 7 or narrower. Reach: 100% of land from the Mouth with the movement of that rank. Delves:
each has a loop and a shortcut, at most 5% of doors walked twice. Variety: any two Realms differ on four of six axes (test).
Repeats: none until the bag is empty (simulated, 10,000 players). Ground: pebbles, twigs, boulders and wild growth per 100 columns
at least 80% of the overworld's. Life: animals in view within 60 seconds from any spot, on a tour. Regression: Provings, Ritual,
story gates, old gate saves, bells, Satchel tests all pass. The human gate: Euan plays the pilot.

## 7. Challenging this plan

- Real frustration or a metric? The metrics are his words made measurable; the last gate is still him playing.
- Does it reduce effort? No route to remember, Waystones replace walking back, repeats vanish.
- Could something simpler do most of it? The repeat fix can, and ships first. Nothing simpler gives a world, as two rounds showed.
- What would prove this wrong? A pilot that still feels small. A Realm that looks good but has nothing to do. Mac frame rates.
- When unsure or failing? The Realm falls back to a floors gate and says nothing odd; old gates never change.
- Inspect, correct, trust? Each stage ends with shots and numbers; he plays each pilot; the flag undoes it.
- Prove improvement without losing what works? The same probe on the same measures, and the whole gate test suite must stay green.
