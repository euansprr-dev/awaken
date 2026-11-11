# Finishing the gates: what is left, and who builds it

Euan, 11 Nov 2026: "Why does it only cover two ranks? Let's keep building all the rest... you making the briefs and then us
handing it off to Opus 5.5 extra high agents, but you make really in-depth briefs... So like all ranks have everything they need,
and we can actually finish the whole gates in the entire plan from earlier... all ranks have all their dungeons and everything is
actually... nothing broken... fully featured."

This folder is that. The gates session (branch `claude/gates-as-worlds-plan-fcb81b`) writes the briefs, looks at every picture and
merges; builder sessions (Opus 5.5, xhigh) each take one brief. Every builder reads `BUILDER.md` first, then its own brief.

## Why a kind covers two ranks today

A choice of mine, not a limit. A theme opens at one rank and stays open at every rank above it (`ROSTER.md`: Hive from E, Glass
Desert from D, Frost Monastery from C, Sky Archipelago from B, Abyssal Shrine from A). When I wrote the seventeen rolled kinds I gave
each the two ranks where its theme first appears, copying Amber Reach's `"ranks": ["E", "D"]`. So a Hive gate at C, B, A or S, and
every gate of the Lantern Night Market or the Sky Archipelago, is still the old kind. Nothing in the generator reads the rank
either: a run's length and shape come from the seed alone, and only the monsters' levels and the pay follow the rank. Brief 01 fixes
both.

## 11 Nov, later the same day: the plan changes

Euan read the briefs and said: "I don't think we should build all the 88 gates by hand... It would take weeks. What about some
kind of really well built procedural worlds?... a few hand built ones, and then based on what we hand built, those procedurally
generated worlds, they all feel very different and still feel just as immersive... the big worlds we've built so far... I don't
see why we couldn't use them right now... they're still ready enough... come up with a creative way to build very high quality
worlds and gates without having to take weeks."

So:

- **The hand-built wave stops at what exists.** No D wave, no C to S roster. Briefs 05 and 06 are not started unless he asks.
- **Done the same day by the gates session** (tip `3f2d4ecf3`): every finished Realm opened to every rank its theme reaches; the
  five part-built worlds marked ready; eight wave stands on the rolled kinds' skins; wandering packs in the open lands; and brief
  00 itself (`tools/check-ci.sh`, on the GameTests workflow).
- **The new piece is brief 07, rolled lands**: one generator that rolls a whole outdoor world from a seed, as `GeneratedCrawl`
  does for dungeons, with a skin per theme. That is how every rank gets many different worlds without weeks of hand work.
- Briefs 01 to 04 still stand (rank changes the run and two new kinds; the way in from the world; light in the air; polish).
- **Who builds 07.** Euan: "why would an Opus agent do it lol? Fable 5.2... would be better". A design-heavy generator goes to
  a Fable 5.2 session at xhigh, not Opus. Started 11 Nov: session `rolled-lands`, branch `claude/rolled-lands-95b4e3`, worktree
  `.claude/worktrees/rolled-lands-95b4e3`. It hands back after the first world (The Ascent, in Ember).

## What "finished" means

1. **Every gate you can find is a new one.** For every rank from E to S and every theme open at that rank there are at least three
   different new gates, so the bag never falls back to the old floors and never shows the same place twice in a row.
2. **A mix of forms at every rank** (the plan's nine: Land, Stronghold, The Deep, Dungeon, Sky, Desert and Ruin, Drowned, Climb,
   Wonder), each gate a run of legs (fights, ambushes, movement, traps, Wardens, a Lord) and not a place to wander.
3. **A higher rank is a bigger thing**, not the same run with stronger monsters: longer, more Wardens, harder crossings, more ways.
4. **Nothing broken:** nothing floats, every leg can be reached with the moves of that rank, no fight room too small, no dark jump,
   no camera-in-rock picture passed off as a check, every switch and twist either works in a new gate or is plainly out.
5. **Fully featured:** twists, modifiers, bells, the Satchel, pay, the Atlas and the Tasks page work in a new gate as in an old one;
   light can be seen in the air; every kind has its sound; every block has a recipe, loot or a declared source.
6. **On for everyone:** `gateRealms` on by default, with a full suite green.

## Where it stands (11 Nov, build `1791215d8`, this branch `2f0d9c481`)

| | Have | Missing |
|---|---|---|
| Rolled dungeon kinds | 17, each at two ranks, 13 themes | every higher rank; Lantern Night Market; Sky Archipelago; a rank that changes the run |
| Hand-built, ready | Amber Reach, The Hour Crypt, The Broodwarren | |
| Hand-built, part-built, in build | The Vine Seat, Kingsroot Keep, Saltcomb Flats, The Quiet Barrow, The Wax Galleries | a route of legs, the bar, a real walk each |
| Hand-built, on old branches only | Glowroot Hollow, Sporefall (`origin/claude/gates-builder-a`); Cinder Stair, The Long Shaft, The Ash Isles (`origin/claude/gates-builder-b`) | porting to today's kit, then the same |
| E wave, not started | The Flooded Ossuary | everything |
| D wave | 16 designed in `ROSTER.md` | everything |
| C to S | nothing designed | 58 gates |
| From the world | switch off; a ready flag (this branch) | proof on GitHub; default on; twists, bells, Atlas, Tasks checked in a new gate |
| Air | fog, drift, light colour, sound and Lens look per kind; wells of real light | a beam you can see; mist on a dry floor |

## The briefs

| # | Brief | Gives | Needs first | Size |
|---|---|---|---|---|
| 00 | `00-checks-on-github.md` | builders can compile and check floats, reach and pictures without Euan's Mac | nothing | small |
| 01 | `01-every-rank.md` | every rank from E to S has its rolled dungeons; two new kinds; rank changes the run | 00 | large |
| 02 | `02-from-the-world.md` | a gate found in the world leads in; twists, bells, Atlas, Tasks proven; switch on by default | 00 | medium |
| 03 | `03-light-in-the-air.md` | beams and floor mist drawn by a renderer of the gates' own | 00 | medium, risky |
| 04 | `04-rolled-polish.md` | spitters' own spray, stairs for two kinds, honest tour cameras, lit crossings | 00 | medium |
| 05 | `05-e-wave-in-build.md` | five part-built E gates become runs at the bar | 00 | large |
| 06 | `06-e-wave-old-branches.md` | five E gates ported from the old branches, and The Flooded Ossuary | 00, and 05's first hand-back | large |
| 07 | `07-rolled-lands.md` | procedural open worlds: five landforms, a skin per theme, a run through each | 00 (done) | large |

After these, written by the gates session wave by wave, because each sheet has to describe its great thing in sculptural detail and
be written after looking at the last wave's pictures:

- **D wave:** sixteen gate sheets from `ROSTER.md` rows 15 to 30, four builders of four gates each.
- **C to S:** the roster itself (58 rows: form, theme, rule, great thing, Lord's place), then their sheets in waves of sixteen.
- **Old worlds into Dungeons and acts**, Far Sight in a Realm, and the pool from 88 toward 128 (the plan's last phase).

## Order, and what can run side by side

`00` first and alone (an hour). Then `01`, `02`, `03` and `04` together: they own different files (see each brief's list). `05` can
start with them. `06` starts when `05` has handed back once, so the porting follows the pattern `05` set. No two builders ever edit
the same land class. Shared one-line files (`LandPrograms`, `RealmLive`, `RealmTests`, `dimension/realms.json`, `looks_gates.json`,
lang merges, `docs/SPEC.md`) are touched as each brief says and merged by the gates session.

## How work comes back

A builder commits on its own branch, pushes it, and ends with the hand-back block from `BUILDER.md`. The gates session reads the
diff, runs the real-game walks on GitHub, looks at every picture, sends back one look sheet if needed, merges the branch into the
gates branch, and hands the gates branch to the manager session, which alone merges into `build/awaken-v1` and runs the one full
suite.
