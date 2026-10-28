# Every boss to the Living Forge's bar

Euan, 2026-10-28: "every boss needs to be revamped to this level of standard ... all unique ... Truly let's push this
to the max." And: "Don't use multiple agents, do this all yourself." So this one session (boss-plan, branch
`claude/boss-plan-f38a45`) takes all 41 boss-tier creatures through the same steps as the Forge, one at a time, and
keeps this page current. SPEC §35.11 holds the decisions; this page is the tracker.

Pick page (one tap per boss, saved to the page's store): https://claude.ai/artifact/MUoruSXr8xmWNVk4JyAAbc

Euan, 2026-10-29: "pick the most unique and interesting forms of each that avoid too much repetition and are CLEAR high
quality ones. I trust you to keep going until fully finished and all bosses have: 1. Full bodies at the quality bar we
set 2. Animations on par with Fresh Animations". So from batch 4 on this session picks each form itself (the clearest
shape, the least like the bosses already built) and keeps building to the end without waiting between batches. The
picks page holds batches 1-3.

## The bar (what "up to the Forge" means)

1. **Silhouette round.** Three shapes that don't look alike, massing blocks at true size beside a player, front and
   side in black plus a value pass (`kit_bodies.py silhouettes DIR BOSS`, options in `kit/sil/<boss>.py`). No round
   shapes. Euan picks one.
2. **Body.** The pick built from `kit/parts.py` at true size, on its family's plan so the shared moves, foot planting
   and look bind. Its own palette and one light story, a clear value order, roughly 80-160 cubes.
3. **Motion (R2, SPEC §35.10).** One clip a move with link poses, the transition spring, secondary springs. The
   ground rule (nothing through the floor; the build fails past 2 px) and, for blades, the edge rule. The meter's
   targets: body kinks outside the strike about 5.5° a frame or less, held under 15%, dips under 40%. Sound and feel
   sit on the clip's marks.
4. **Fight (R3).** Phrases, not summons. A readable tell for every blow, and the hit volume travels with the limb that
   strikes. Its `kit_` GameTests (the hit lands where the limb is, strings link).

## Order

Batches of four, so a silhouette round is always waiting for Euan while I build the last pick. Lords first (every gate
ends on one), then the Descent's guardians, the Sovereigns and the Shadow, then the Nightmares. New families (Caster,
Serpent, Construct, Beast, Crawler) get their plan the first time one of their bosses is built.

| Batch | Bosses |
|---|---|
| 1 | Unit Zero, Mother of Spores |
| 2 | Gearwright Colossus, Root Saint, Bell Warden, Glass Serpent |
| 3 | Hive Regent, Drowned Archivist, Lantern Broker, Abbot of Frost |
| 4 | Mirror Knight, the Sleeper, Sky Leviathan, Hollow Herald |
| 5 | Rootmother, Thornback, Choirmaster, Ossuary King, Great Listener |
| 6 | Furnace Engine, Drowned Moon, Architect of Rooms, Shadow Regent, Seed Warden |
| 7 | Sovereigns of Hunger, Silence, Sleep and Machines |
| 8 | Sovereigns of Mirrors, Storms, Roots and Ash; the Signal's Shadow |
| 9 | The Long Drop, the Pyre, the Undertow, the Hollow Crowd |
| 10 | The Thousand Legs, the Fuse, the Faceless |

Out of this list until Euan says: the 15 gate Wardens and the 12 named beasts (the beasts already have hero rigs).

## Tracker

Round: drawn / the pick. Body, motion, fight: — not started, ✓ built (commit).

| Boss | Tier | Gate | Family | Today | Round | Body | Motion | Fight |
|---|---|---|---|---|---|---|---|---|
| The Ember Tyrant (the Living Forge) | Lord | Ember | Brute | kit | C | ✓ 3488e8d5 | ✓ c1e3c8ed | — |
| Unit Zero | Lord | Precursor | Duelist | kit (phase 1) | A, the Sentinel | ✓ b94e7457 | ✓ b94e7457 | — |
| Mother of Spores | Lord | Fungal | Flier | kit (phase 1) | C, the Crowned Host | ✓ 64932c3a | ✓ 64932c3a | — |
| The Gearwright Colossus | Lord | Clockwork | Brute | kit | C, the Clock Tower | ✓ 41eee64c | ✓ 41eee64c | — |
| The Root Saint | Lord | Temple | Caster | kit | A, the Rooted Saint | ✓ 6c3f815b | ✓ 6c3f815b | — |
| The Bell Warden | Lord | Bell | Construct | kit | A, the Bell That Walks | ✓ 72f262a4 | ✓ 72f262a4 | — |
| The Glass Serpent | Lord | Glass | Serpent | kit | B, the Hourglass | ✓ ec80f180 | ✓ ec80f180 | — |
| The Hive Regent | Lord | Hive | Flier | kit | A, the Empress | ✓ 3b132661 | ✓ 3b132661 | — |
| The Drowned Archivist | Lord | Drowned | Caster | kit | A, the Lectern | ✓ faedcbea | ✓ faedcbea | — |
| The Lantern Broker | Lord | Lantern | Construct | kit | A, the Pedlar | ✓ b01fd174 | ✓ b01fd174 | — |
| The Abbot of Frost | Lord | Frost | Caster | kit | B, the Pilgrim | ✓ 869d54b2 | ✓ 869d54b2 | — |
| The Mirror Knight | Lord | Mirror | Duelist | player ×1.4 | C, the Masked Duelist | — | — | — |
| The Sleeper | Lord | Nightmare | Brute | zombie ×2.2 | B, the Four-Poster | — | — | — |
| The Sky Leviathan | Lord | Sky | Serpent | cube serpent ×3.2 | B, the Ray | — | — | — |
| The Hollow Herald | Lord | Abyss | Caster | cube shade ×1.4 | C, the Ring-Head | — | — | — |
| The Rootmother | Guardian | Rim | Crawler | spider ×2.4 | — | — | — | — |
| The Thornback | Guardian | Gardens | Beast | ravager | — | — | — | — |
| The Choirmaster | Guardian | Choir | Caster | witch ×2 | — | — | — | — |
| The Ossuary King | Guardian | Orchard | Brute | wither skeleton ×2 | — | — | — | — |
| The Great Listener | Guardian | Silent | Brute | cube listener ×1.8 | — | — | — | — |
| The Furnace Engine | Guardian | Foundry | Brute | iron golem ×1.8 | — | — | — | — |
| The Drowned Moon | Guardian | Inverted | Flier | elder guardian ×1.6 | — | — | — | — |
| The Architect of Rooms | Guardian | Dreaming | Caster | evoker ×1.8 | — | — | — | — |
| The Shadow Regent | Guardian | Court | Duelist | player ×1.6 | — | — | — | — |
| The Seed Warden | Guardian | Seed | Caster | cube shade ×1.6 | — | — | — | — |
| Sovereign of Hunger | Sovereign | Court | Beast | ravager ×1.8 | — | — | — | — |
| Sovereign of Silence | Sovereign | Court | Brute | cube listener ×1.9 | — | — | — | — |
| Sovereign of Sleep | Sovereign | Court | Flier | phantom ×3.2 | — | — | — | — |
| Sovereign of Machines | Sovereign | Court | Brute | iron golem ×1.9 | — | — | — | — |
| Sovereign of Mirrors | Sovereign | Court | Duelist | player ×1.8 | — | — | — | — |
| Sovereign of Storms | Sovereign | Court | Flier | breeze ×2.4 | — | — | — | — |
| Sovereign of Roots | Sovereign | Court | Caster | creaking ×1.9 | — | — | — | — |
| Sovereign of Ash | Sovereign | Court | Flier | blaze ×2.4 | — | — | — | — |
| The Signal's Shadow | Final | Seed | Duelist | cube shade ×2 | — | — | — | — |
| The Long Drop | Nightmare | Fear | Flier | phantom ×2.4 | — | — | — | — |
| The Pyre | Nightmare | Fear | Flier | blaze ×2 | — | — | — | — |
| The Undertow | Nightmare | Fear | Caster | drowned ×2 | — | — | — | — |
| The Hollow Crowd | Nightmare | Fear | Brute (a crowd) | zombie ×1.7 | — | — | — | — |
| The Thousand Legs | Nightmare | Fear | Crawler | spider ×2.6 | — | — | — | — |
| The Fuse | Nightmare | Fear | Construct | creeper ×2 | — | — | — | — |
| The Faceless | Nightmare | Fear | Duelist | player | — | — | — | — |

## The rounds so far

- **Unit Zero:** A the Sentinel (a cathedral statue that moves, a great square halo on its point), B the Unbound (a
  wire frame with its plates floating off it), C the Hunter (bent-back legs, mantis blades, a tail blade). **Picked
  A**, 2026-10-28.
- **Mother of Spores:** A the Veiled Lady (a thimble cap over a lattice veil to the floor), B the Shelf Mother (a
  floating dead tree of shelf fungus), C the Crowned Host (a woman held up by a fanned crown of glowing fungus
  stalks, her legs one root tail). **Picked C**, 2026-10-28.
- **The Gearwright Colossus:** A the Piston Titan, B the Unfinished (half scaffolding, a crane for an arm), C the Clock
  Tower (its arms the clock's hands, the pendulum its weapon). **Picked C**, 2026-10-28.
- **The Root Saint:** A the Rooted Saint (a robed statue on roots, a stone sunburst), B the Reliquary Tree (a walking
  tree with the saint's bones in a lit hollow), C the Kneeling Giant (a shrine built on his back). **Picked A**, 2026-10-28.
- **The Bell Warden:** A the Bell That Walks (four crab legs from under the rim), B the Bellringer, C the Hollow Bell
  (a knight cast in one piece with a bell, gliding on its rim). **Picked A**, 2026-10-28.
- **The Glass Serpent:** A the Shard Wyrm (rearing, a fin of shards), B the Hourglass (hollow glass frames with sand
  running through, coiled), C the Mirror Eel (a flat ribbon of mirror, a mirror for a face). **Picked B**, 2026-10-28.
- **The Hive Regent:** A the Empress (an upright queen, her banded abdomen a train behind her, four tall glass wings,
  two amber stinger daggers), B the Living Hive (a flying stepped hive held up by rows of wings, the queen rising out of
  its top, a cloud of drones), C the Hornet (long and low, wings swept back, the stinger curled forward under it).
  **Picked A**, 2026-10-28.
- **The Drowned Archivist:** A the Lectern (grown into his reading stand, the sea pouring off the open book, a quill
  like a spear), B the Diver (an old diving suit, his face against a window full of water, a case of chained books on
  his back), C the Tide of Pages (held up inside a column of sea water, rings of books circling it). **Picked A**,
  2026-10-28.
- **The Lantern Broker:** A the Pedlar (a pack frame taller than him hung with his whole shop of lanterns), B the
  Lantern Head (a gentleman whose head is a lantern in a top hat, a cape of moth wings), C the Walking Stall (a curtained
  booth on four heron legs, four long arms reaching out of it). **Picked A**, 2026-10-28.
- **The Abbot of Frost:** A the Frozen Saint (tall and thin, robes frozen in layers, a mitre of ice, a censer), B the
  Pilgrim (hunched under a glacier with the monastery's heart frozen in it), C the Snow Owl (a cloak of frost feathers,
  an owl's hood with ear tufts and pale eyes). **Picked B**, 2026-10-28.
- **The Mirror Knight:** A the Silvered Knight (mirror plate, a mirror for a face, a cape of cracked mirror panels, a kite
  shield), B the Reflection (dark silvered glass, stepping out of a gilded frame with its back foot still in the glass),
  C the Masked Duelist (a blue coat over a skirt of mirror panels, a wide plumed hat, an oval mirror mask, a rapier en
  garde and a parrying dagger). **Picked C**, 2026-10-29: the only fencer in the roster (Unit Zero cuts with its hands,
  the Shadow Regent and the Sovereign of Mirrors are still to come and can't both be knights), a silhouette nobody else
  has (the hat brim, the long blade), and fencing moves that read at a glance. A is a knight anyone has seen; B's frame
  pins it to one spot, which a duelist can't be.
- **The Sleeper:** A the Sleepwalker (a giant in a nightgown, arms out, a candle swinging from one hand), B the
  Four-Poster (the bed walks on its posts, the Sleeper asleep under the quilt, one arm dragging its fist on the floor,
  torn curtains, a dreamcatcher), C the Bear (a giant patchwork teddy, a button eye and a stitched X, stuffing glowing out
  of a split seam). **Picked B**, 2026-10-29: nothing else in the game is shaped like it, it says "asleep" from across
  the room, and each move has its own body part (the fist, the curtains closing for Heavy Eyelids, the bed rearing to
  wake). A reads as another robed giant; C is a toy, cuter than the gate.
- **The Sky Leviathan:** A the Cloud Dragon (a wingless sky dragon a dozen blocks long, a cloud mane, antlers, a pearl
  in one claw), B the Ray (a ray ten blocks across, storm slate veined with lightning on top and pale beneath, horns
  curling either side of its mouth, a whip tail ending in forked lightning), C the Sky Ruin (a serpent carrying a ruined
  temple on two humps, a bell tower, chains). **Picked B**, 2026-10-29: the Glass Serpent is already a long coiled body,
  and A and C would be two more; the Ray is the clearest flying shape there is, its Wingbeat and Dive are the body's own
  moves, and it isn't a whale (the Gate Worlds have those).
- **The Hollow Herald:** A the Empty Robe (a floating robe with nothing in the hood but black ringed in light, a black
  glass halo, a herald's trumpet and an empty banner), B the Black Sun (a gaunt figure holding a black sphere overhead in
  a tilted ring of light, its robes streaming up into it), C the Ring-Head (very tall and thin in a long blue-black coat,
  a ring of violet-black glass where its head should be with nothing inside it, arms to the floor, a blank scroll).
  **Picked C**, 2026-10-29: the clearest of the three from far off (you see straight through its head), not another robe
  (the Root Saint, the Pilgrim and the Archivist all wear one), and its long limbs give it a walk and reach of its own.
