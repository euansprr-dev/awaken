# Over Every Hill: finds (plan and working notes)

Approval page: https://claude.ai/artifact/G9fPfsJmTjj8xbaRnrScSw. Euan approved the full plan and every recommendation on
10 Nov 2026, with one condition: "make sure it doesn't feel like a fairground... you should still have stretches where you
don't find much. It should feel very natural." Branch `claude/finds-over-every-hill`, from build 8496b6105. One builder, no
sub-agents, no client on the Mac, look tours on GitHub's runners allowed.

The catalogue of 144 (set, verb, name, size, rank, where, made, see, do, get) is `docs/finds/catalogue.txt`. The brief for
each find is in `docs/finds/BOOK.md`. SPEC section: §58 (to write at hand-over).

## What it is

144 kinds of small thing to find in the overworld: 97 curios (a few blocks, under a minute) and 47 finds (a small scene, a few
minutes), in 18 sets of 8, ranks E to A. Each is something you do, built on one of eighteen verbs: stack, carry, light, ring,
turn, offer, follow, dig, pull, pour, shoot, run, reach, wait, mend, read, break, sneak. No fights, no people (the Stilled are
stone), beasts the game already has, nothing automated, loot per player. Sets fill numbered slots in the Journal's Places
shelf. About one find in ten gives a lead: a direction to a rarer one further out, never a marker.

## The bar (checked for every kind by test, then by eye)

Stands on ground that suits it (anchor). The ground changes round it for six blocks or more and fades (path, trampled grass,
pebbles, twigs, litter, soft steps). A third or more of what you see is not a full block (slabs, cuts, props). Aged (moss
north, lean, fallen, overgrown). Moves and sounds (wind, moths, drips; vanilla recordings by name). Something lives there.
Nothing floats, every part is reachable, it can always be finished or restarted, never kills from full health.

## Engine (package `dev.awaken.finds`)

- `Kind`: id, name, set, verb, size, rank 0..4, lands, anchor, made, the lines the player reads, reach, a `SceneBuilder`, a
  `Script` factory, loot table, optional lead. Kinds are Java, one file per set in `finds/sets/` (data in spirit, checked by
  the compiler). `Kinds.all()` is the registry.
- `Site`: kind, heart x/y/z, seed, quarter turn. A pure function of the world seed and its cell. `Sites.near(...)`.
- `Scene`: what a kind builds, relative to the heart: blocks (with `followGround` so builds sit in the land), named parts
  (relative positions), spawns, a tell, a keep-clear box. Cached per site.
- `FindsFeature`: one worldgen feature, late decoration step, every overworld biome. Writes each site's blocks chunk by chunk
  and puts the heart block at the heart.
- `FindHeartBlock` + `FindHeartBlockEntity`: invisible, unbreakable, no collision. Holds the site's shared state (stage,
  vars, timers), who has claimed it, and runs the kind's script while a player is within 48 blocks.
- Parts are positions, not special blocks. Global listeners (right click, left click, break, place, projectile impact) look
  up the site for a position and hand the event to its heart. So any block, vanilla or prop, can be a part.
- Ward: a site's own blocks can't be broken or built on by survival players, except where its script says so.
- `Script` library in `finds/verb/`: LightRun, Sequence, Sockets, Turn, Course, Guide, Quiet, When, Targets, Seal, Dig,
  Haul, Pour, Stack, Read, plus `Steps` to chain them and `Reward` to pay out.
- Carried things are the props' own block items. Pick a loose prop up by using it; set it down by using it on its socket.
  Heavy ones (tag `#awaken:finds/heavy`) slow you while held.
- Per player (attachment `finds`, copied on death, synced to the owner): kinds seen, kinds done with counts, site keys
  claimed, leads.
- Loot: `awaken:finds/<tier>` tables by rank and size plus per-kind extras, popped out of a cache part or handed over. Once
  per player per site. Every new item is declared for `journey_ledger`.

## Placement (the part that answers "not a fairground")

- Curio cells 96 blocks, find cells 256, each site jittered inside its cell.
- A slow richness field over the world (two octaves, about 1,100 and 420 blocks). Below a threshold the land is quiet:
  no curios and few finds. Above it things gather. Roughly a third of the land is quiet. The simulation reports the long
  gaps as well as the mean.
- Rank from `RegionMap` (distance), land from the region's land group and the biome. A kind is eligible from its own rank
  outward, thinning by 0.45 per rank beyond, so far land is mostly new kinds.
- A cell's kind is a hash pick among eligible kinds, moved on if a neighbour cell within two rings would pick the same.
- Anchors: FLAT, HILLTOP, LINE (old road), SHORE, WATER, CLIFF, SLOPE, PEAK, SNOW. A cell with no fitting ground stays empty.
- Keeps clear of structures, roads, villages, the city and gates. New chunks only. Config switch `finds` (default on).
- The plainest kind of each verb is favoured within 400 blocks of spawn.

## Kit (`finds/kit/`)

- `Props`: about 120 small prop blocks from one table (stones, pots, baskets, rope, pegs, lamps, candles, bones, shells,
  nets, tools, wheels, cloth, carved stones, bowls, nests, eggs, statues' parts). One block class, facing and a small state
  property. Models, blockstates, items, lang and loot come from `tools/content/finds_content.py`. Vanilla textures by name.
- Flat marks (moss, soot, cracks, tracks) and the game's own litter, moss carpet, pebbles, twigs, trampled grass.
- Scene helpers: cairn, post, ring of stones, road trace, skirt, scatter, footing, stilled figure, old tree, hut stub.

## Journal, binoculars, Atlas

- `Catalogue` fixed slots on the Places shelf: 18 sets of 8, unfound = "?" and its number only.
- `Spotting.Source`: holding the glass on a site marks its kind seen (outline until done).
- Done sites are Atlas marks. Leads are a line in the Journal with a compass direction, nothing on the map.
- `Promises` for kinds that need a move or a power you may lack.
- The look of all this belongs to the K-menu "One Stage" work; finds only feed it.

## Checks

- `/awaken finds gallery`: every kind in a row on flat ground. `/awaken finds near`, `/awaken finds tp <kind>`.
- GameTests: every kind builds with nothing floating; every part can be reached; every kind's scripted play-through
  completes, pays once and fills its slot; placement keeps quiet land quiet and twins apart; ledger.
- One look tour of the gallery on GitHub's runners.

## Builder's notes (how the code is laid out; read before writing a kind)

- A kind is written in `finds/sets/<Set>.java` with `Kind.of(FSet.X, number, "id", "Name")...add()`. One file per set, eight
  kinds each when done. `AllSets.init()` calls every set. Number 1 of a set is its plainest and is favoured near spawn.
- A scene (`Kind.Maker`) writes in the kind's own coordinates, the kind facing south (+z); `Scene` turns it. `s.g(x,z)` is the
  ground relative to the heart; `s.on(x,z,state)` stands a block on a column's ground, `s.up(x,z,k,state)` k above that,
  `s.floor(x,z,state)` replaces the ground block, `s.put(x,dy,z,state)` is relative to the heart's ground (dy 0).
  `s.partOn(name,x,z,k[,state])` names a part. `s.spawn(type,x,z,flags,role)` puts life there on first wake.
  Never build at (0,-2,0): the heart is there. Everything must be connected to the ground (test `finds_every_scene_stands`),
  inside `reach`, and at least a third of the blocks above ground must not be plain cubes.
- Props: `Props.X.get().of(state)` or `.front(state, direction)` (a model's own front is north). They come from
  `tools/content/finds_content.py`; add a prop there and run it (it rewrites `kit/Props.java`).
- `Mats.of(s.site.group())` gives the land's stone and wood. `Dress.skirt/road/cairn/scatter/moss` dress the ground.
- Scripts (`finds/verb/`): `Light`, `Notes`, `Sockets`, `Fit`, `Turn`, `Guide`, `Dig`, `Haul`, `Pour`, `Marks`, `Course`,
  `Prize`, `When` (`stay` and `only`), `Seal`, `Quiet`, `Read`, chained with `Steps.of(...)` (four links at most). A kind may
  write its own small `Script` in its set file (the Sundial does). A script that sets a facing block must use `c.turned(state)`.
- A part named `cache` is where loot pops out; a prop cache that can stand open (FOOTING, CAP_STONE, BUNDLE, PLUG) opens.
- Every kind has `.play(t -> ...)`: the walkthrough a scripted player runs in `finds_every_kind_plays` (`Trial`: `hold`,
  `use`, `hit`, `shoot`, `dig`, `stand`, `ticks`, `pass`, `hour`). `.after(c -> ...)` runs at the moment of finishing.
- Loot: tier tables `awaken:finds/<e|d|c|b|a>_<small|big>` from the content script; `.loot("name")` for a kind's own.
- Not done on purpose in the first pass: hand clips per verb (vanilla swing only), own particles for tells (vanilla ones),
  the Stilled's own renderer (the kit has simple stone figures), repeat gifts by game day, powers as blows on seals.

## Build order and state

1. [x] Engine core: Kind, Site, Scene, feature, heart, listeners, ward, player data, loot, commands. (written; compiling)
2. [x] Kit: 54 props + generator, Mats, Dress.
3. [x] Verb scripts (sixteen classes).
4. [~] First eighteen kinds written (number 1 of each set), gallery command, tests written; not yet green.
5. [ ] The other 126.
6. [ ] Journal, Spotting, Atlas, leads, Promises.
7. [ ] Full test run on CI, gallery tour, fix pass.
8. [ ] SPEC §58, module doc, hand-over note.
