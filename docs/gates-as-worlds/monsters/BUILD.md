# Monsters of the Gates: the build

Built 10 November 2026 on `claude/great-goldwasser-72ae9f`, after Euan approved option B of the plan (`PLAN.md`): a body
of its own for each of the 78 gate monsters that wore a Minecraft mob's body. He asked for it to be coded in one batch
and tested at the end. The rules are in `docs/SPEC.md` §60. Nothing here has been seen in game yet.

## What was built

| Rank | Theme | File | Bodies |
|---|---|---|---|
| E | Bell Crypt | `tools/art/specs/gm/bell.py` | The Toller, The Mourner, The Chime, The Pallbearer, The Sexton |
| E | Hive | `gm/hive.py` | The Stinger, The Grub, The Honeypot, The Door, The Swarm Mind |
| E | Fungal Hollow | `gm/fungal.py` | The Sporeling, The Shelf, The Capweaver, The Puffcap, The Tender, The Rot |
| E | Ember Mines | `gm/ember.py` | The Pickhead, The Slag Pot, The Bellows, The Coal Tick, The Slag Boar, The Overseer |
| E | Overgrown Temple | `gm/temple.py` | The Creaker, The Thorn Bud, The Bramble Hound, The Censer, The Overgrowth |
| D | Drowned Archive | `gm/drowned.py` | The Blotter, The Paper Crane, The Reading Glass, The Quillback, The Scribe, The Curator |
| D | Clockwork Foundry | `gm/clockwork.py` | The Cog Hound, The Riveter, The Governor, The Steam Hammer, The Regulator |
| D | Glass Desert | `gm/glass.py` | The Shardwalker, The Sand Stalker, The Shimmer, The Prism, The Mirage |
| D | Lantern Night Market | `gm/lantern.py` | The Lantern Moth, The Magpie, The Hopping Lantern, The Fan Dancer, The Lamplighter |
| C | Frost Monastery | `gm/frost.py` | The Frozen Monk, The Hail Slinger, The Snow Star, The Ice Hound, The Long Horn, The Yeti |
| C | Mirror Palace | `gm/mirror.py` | The Empty Suit, The Glint, The Glass Duelist, The Unblinking (the Mirror Shade stays) |
| C | Precursor Lab | `gm/precursor.py` | The Test Subject, The Security Unit, The Coil, The Gel Sample, The Sweeper |
| C | Nightmare | `gm/nightmare.py` | The Sheet, The Night Terror, Many Eyes, The Tall One, The Waking |
| B | Sky Archipelago | `gm/sky.py` | The Kite, The Puffer, The Balloonist, The Thunderhead, The Gale |
| A | Abyssal Shrine | `gm/abyss.py` | The Watcher, The Silent One, The Void Spawn, The Hollow Priest, The Nothing |

In the game: `mobs/entity/GateMonster` (the cue and the fight rules), `RigBody`, the rule flags on `MobDef`, the
OPEN and STUCK stages in `WildRenderer`, and `GateMonsterTests`.

## How to look at one without the game

```bash
python3 tools/art/specs/gate_monsters.py preview /tmp/gm drowned
```

That writes one sheet per theme (`drowned_keys.png`): each body at rest, in its room's colour, as an outline, and at
the key frame of every clip. A single id (`... preview /tmp/gm the_curator`) writes every clip frame by frame.

## How to check and rebuild

```bash
python3 tools/art/specs/gate_monsters.py check
```

```bash
python3 tools/art/build_all.py gate_monsters
```

The check needs no game. It refuses a body that breaks its bone budget, lacks a clip, goes more than 2 px under the
floor in any clip, starts a strike somewhere other than where its wind-up ended, would stride faster than 3.6 times a
second at full chase, has a quadruped's knees folding in under its belly, names a sound Minecraft does not have, does
not act an ability the bestiary gives its monster, or dies without ending on the floor.

## Decisions taken while building

- Euan answered the plan's first decision (option B). The other nine were taken as recommended: Bell Crypt first,
  things not people, a new name where the idea changed, the six fight rules built with the bodies, Wardens on the same
  path, the old bodies kept as a silent fallback, each monster its own voice from Minecraft's recordings, and the
  Mirror Shade left alone.
- Euan saw two early bodies and said the front knees looked inverted. They were: a quadruped's joints both folded in
  under its belly. Forelegs now bend knee-forward and hind legs hock-back on every four-legged body, and the check
  refuses the old shape.
- The glass bodies first came out glowing white on pale sand. They were repainted dark, lit only along their edges, as
  the roster asked.
- A scan of every death's last frame found about twenty bodies left propped up in the air on an arm or a foot. The kit
  now lays a fallen body down (it folds whichever limb holds the trunk off the floor), and the check refuses a death
  that ends more than 12 px up.
- Every move that can leave a monster open has an open pose. Where a secondary move was authored without one, the body
  eases into its own spent pose.
- Six bodies changed how they move: the Shimmer, the Prism, the Snow Star, the Puffer, the Balloonist and the Gale fly
  (their old bodies hopped or walked); the Nothing is on the ground now (the Wither flew).
- Sizes: entity scales were dropped so each body is the size it was drawn, except the Gel Sample (1.6).

## What is not done

- Nothing has been played. The look tours on CI and Euan's own eye are the next check.
- No new particles or sounds per move: each ability keeps the effects it had.
- Small rule gaps are listed in SPEC §60.6.
