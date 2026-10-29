# Every boss to the Living Forge's bar

Euan, 2026-10-28: "every boss needs to be revamped to this level of standard ... all unique ... Truly let's push this
to the max." And: "Don't use multiple agents, do this all yourself." So this one session (boss-plan, branch
`claude/boss-plan-f38a45`) takes all 41 boss-tier creatures through the same steps as the Forge, one at a time, and
keeps this page current. SPEC §35.11 holds the decisions; this page is the tracker.

**Paused 2026-10-29 with 33 of 41 done.** The Signal's Shadow is half done and the seven Nightmares are left. Whoever finishes reads [HANDOVER.md](HANDOVER.md) first: it has the exact next steps, the workflow, the meter targets and the forms not to repeat.

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
| The Mirror Knight | Lord | Mirror | Duelist | kit | C, the Masked Duelist | ✓ 71bf61a2 | ✓ 71bf61a2 | — |
| The Sleeper | Lord | Nightmare | Brute | kit | B, the Four-Poster | ✓ 6255ae1d | ✓ 6255ae1d | — |
| The Sky Leviathan | Lord | Sky | Serpent | kit | B, the Ray | ✓ ea291205 | ✓ ea291205 | — |
| The Hollow Herald | Lord | Abyss | Caster | kit | C, the Ring-Head | ✓ b91a71b6 | ✓ b91a71b6 | — |
| The Rootmother | Guardian | Rim | Crawler | kit | B, the Long-Legs | ✓ ee462916 | ✓ ee462916 | — |
| The Thornback | Guardian | Gardens | Beast | kit | B, the Thorn Pangolin | ✓ e2e9a90b | ✓ e2e9a90b | — |
| The Choirmaster | Guardian | Choir | Caster | kit | C, the Cantor | ✓ 951aa818 | ✓ 951aa818 | — |
| The Ossuary King | Guardian | Orchard | Brute | kit | A, the Orchard King | ✓ ab74a510 | ✓ ab74a510 | — |
| The Great Listener | Guardian | Silent | Brute | kit | B, the Hush Bat | ✓ af969446 | ✓ af969446 | — |
| The Furnace Engine | Guardian | Foundry | Brute | kit | A, the Iron Horse | ✓ 4193ade1 | ✓ 4193ade1 | — |
| The Drowned Moon | Guardian | Inverted | Flier | kit | A, the Moon Angler | ✓ 1643236a | ✓ 1643236a | — |
| The Architect of Rooms | Guardian | Dreaming | Caster | kit | A, the Hermit House | ✓ cadf2a15 | ✓ cadf2a15 | — |
| The Shadow Regent | Guardian | Court | Duelist | kit | A, the Long Shadow | ✓ 3feaab86 | ✓ 3feaab86 | — |
| The Seed Warden | Guardian | Seed | Caster | kit | A, the Husk That Opens | ✓ a6726d89 | ✓ a6726d89 | — |
| Sovereign of Hunger | Sovereign | Court | Beast | kit | A, the Gorged King | ✓ 7a319255 | ✓ 7a319255 | — |
| Sovereign of Silence | Sovereign | Court | Brute | kit | A, the Stone Ear | ✓ 8559df31 | ✓ 8559df31 | — |
| Sovereign of Sleep | Sovereign | Court | Flier | kit | A, the Night Owl | ✓ 8d12f035 | ✓ 8d12f035 | — |
| Sovereign of Machines | Sovereign | Court | Brute | kit | A, the Armillary | ✓ e30286bc | ✓ e30286bc | — |
| Sovereign of Mirrors | Sovereign | Court | Duelist | kit | A, the Vanity | ✓ 8f309193 | ✓ 8f309193 | — |
| Sovereign of Storms | Sovereign | Court | Flier | kit | A, the Gale Hound | ✓ c7a4c0fd | ✓ c7a4c0fd | — |
| Sovereign of Roots | Sovereign | Court | Caster | kit | A, the Hearthstone Toad | ✓ 7008ed48 | ✓ 7008ed48 | — |
| Sovereign of Ash | Sovereign | Court | Flier | kit | A, the Cinder Wyrm | ✓ d3b188fd | ✓ d3b188fd | — |
| The Signal's Shadow | Final | Seed | Duelist | kit | A, the Signal Cuttlefish | ✓ df6001890 | ✓ df6001890 | — |
| The Long Drop | Nightmare | Fear | Flier | kit | A, the Hand from Above | ✓ cf9d617d | ✓ cf9d617d | — |
| The Pyre | Nightmare | Fear | Flier | kit | A, the Pyre Mare | ✓ 2a699e47 | ✓ 2a699e47 | — |
| The Undertow | Nightmare | Fear | Caster | kit | A, the Old Snapper | ✓ 7063327c | ✓ 7063327c | — |
| The Hollow Crowd | Nightmare | Fear | Brute (a crowd) | kit | A, the Throng | ✓ 43eb6a0c | ✓ 43eb6a0c | — |
| The Thousand Legs | Nightmare | Fear | Crawler | kit | A, the House Centipede | ✓ 5eb9480a | ✓ 5eb9480a | — |
| The Fuse | Nightmare | Fear | Construct | kit | A, the Jack | — | — | — |
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
- **The Rootmother** (kit id `guardian_rim`, as every Guardian's kit takes its mob's id): A the Moss Widow (a heavy
  spider, root legs, a boulder of turf for an abdomen, a glowing brood sac), B the Long-Legs (a harvestman taller than a
  house, a small knotted body held high on eight long bowed root legs, eggs and moss streamers hanging under it), C the
  Turf Mantle (a slab of the Rim with young trees on it, walking on root legs). **Picked B**, 2026-10-29: nothing else
  has its shape, Rootfall is her body dropping down between her legs, and the lances are her legs stabbing. A reads as
  a big vanilla spider; C is one more thing carried on legs (the Bell, the Four-Poster).
- **The Thornback** (`guardian_gardens`): A the Hedge Boar (a bramble crest and thorn tusks), B the Thorn Pangolin
  (overlapping thorned leaf scales down an arched back to a long tail, blossoms between them; it rolls up to charge),
  C the Topiary Lion (a clipped hedge lion with a mane of roses). **Picked B**, 2026-10-29: the clearest side view of
  the three, and its charge is a ball of thorns coming down the ledges. A is today's ravager with a hedge on it; C reads
  as cute.
- **The Choirmaster** (`guardian_choir`): A the Conch Siren (a singer rising from a great conch she drags), B the
  Lionfish Diva (a fan of fin spines behind her like a choir collar, a gown of trailing fins), C the Cantor (a great
  pale octopus standing up on its arms, two raised to lead, singing through its siphon). **Picked C**, 2026-10-29: the
  only octopus, it walks the way one does, and every move is an arm. B's fan echoes the Root Saint's sunburst and would
  be a third woman's shape after the Crowned Host and the Empress; A's shell hides her.
- **The Ossuary King** (`guardian_orchard`): A the Orchard King (a giant crowned skeleton with an apple tree growing up
  through his ribs and spreading over his skull, glowing red apples, a rotten cloak, a bone sword), B the Ossuary
  Colossus (a hulk built of stacked skulls), C the Knuckle King (an ape's build, a tree from his back). **Picked A**,
  2026-10-29: it says "the orchard grew from his ribs" at a glance, and his getting up once is a skeleton putting
  itself back together. B reads as a golem with holes in it; C's side view is muddled.
- **The Great Listener** (`guardian_silent`): A the Ear (a gaunt giant whose head is one great ear), B the Hush Bat (a
  blind bat as big as a barn walking on its folded wings, huge ears, eyes stitched shut), C the Many-Eared (a tick-like
  mass grown over with ears). **Picked B**, 2026-10-29: a bat finds you by screaming, which is the fight. A repeats the
  Herald's tall thin build with an object for a head; C is a second leggy crawler beside the Long-Legs.
- **The Furnace Engine** (`guardian_foundry`): A the Iron Horse (a steam locomotive grown into a cart horse: its boiler
  the body, its smokebox the head with the door for a mouth and the lamp for an eye, red driving wheels on its flanks,
  its rods for legs), B the Boiler Titan (a hunched riveted giant with a furnace in its belly and chimneys for horns), C
  the Crucible (a walking ladle of molten iron on spider legs). **Picked A**, 2026-10-29: an engine is what a foundry's
  heart would wake into, its door opening on the fire is the Arc Weld's tell, and nothing else on the roster walks on
  four legs like a horse. B is the Living Forge's and the Gearwright's build again; C's spider legs are the Long-Legs'.
- **The Drowned Moon** (`guardian_inverted`): A the Moon Angler (an anglerfish as big as a house whose lure is the moon
  you see in the sea above: a black head that is nearly all mouth, glassy fangs, the moon on a rod from its brow), B the
  Drowned Moon itself (a cratered sphere trailing a tide of weed), C the Moon Jelly (a bell with a moon inside, trailing
  veils). **Picked A**, 2026-10-29: the intro line becomes a trap, the moon is bait, and the mouth gives it a face and
  real blows. B has no face or limbs to fight with; C drifts like the Sleeper's veils. Nothing else on the roster is a
  fish (the Sky Leviathan is a ray, the Glass Serpent a serpent).
- **The Architect of Rooms** (`guardian_dreaming`): A the Hermit House (a hermit crab as big as a cottage that has made
  its shell of a crooked lit house, looking out of its doorway, one huge claw before its face), B the Doorframe Man (a
  tall figure that is a standing doorframe, a lit room inside it where its chest would be), C the Draughtsman (a hunched
  architect drawing the room in the air with long compass legs). **Picked A**, 2026-10-29: the house makes its doors and
  rooms literal (the Door Step is the crab shutting itself in, Other Rooms its house shaking), and nothing on the roster
  is a crab or carries its home. B is one more tall thin figure beside the Hollow Herald; C's compass legs walk like the
  Long-Legs'.
- **The Shadow Regent** (`guardian_court`): A the Long Shadow (your shadow stood up off the floor and stretched as at
  dusk, crowned, a fan collar and a cape, a long violet-edged sword, standing in its pool with its own cast shadow on
  the floor behind it, a throne rising out of the pool), B the Shadow-Puppet King (a jointed flat puppet king on rods,
  lit from behind), C the Ermine Knight (a courtly knight in an ermine mantle). **Picked A**, 2026-10-29: it is
  literally your shadow, so "It sits the way you sit" and Your Charge (falling flat and sliding over the floor as a
  cast shadow) say what it is, and nothing else on the roster is a shadow figure. B is flat and reads only from the
  front; C is one more knight beside the Mirror Knight, and the Sovereign of Mirrors is still to come.
- **The Seed Warden** (`guardian_seed`): A the Husk That Opens (a seed as big as a house in four dark ridged valves like
  a chestnut's case, a crack of light down its face; its halves part on a door of light, and it breathes like a
  jellyfish), B the Doorkeeper (a kneeling armoured giant whose chest is a door), C the Samara (a winged seed that
  spins). **Picked A**, 2026-10-29: its phase lines ("The Seed Warden unfolds", "The door opens a crack") are what its
  body does, the Seed's last door is the light inside it, and no other boss is a closed pod that opens. B is a knight
  and a door again, after the Mirror Knight and the Architect's doors; C is one shape spinning and reads as a leaf.
- **The Sovereign of Hunger** (`sovereign_hunger`): A the Gorged King (a crowned king grown so fat he crawls on his
  knuckles, his robe split over a belly that is a second mouth, red inside, his own face small and starved under a
  slipping crown), B the Open Jaw (a gaunt crowned beast that is almost all mouth), C the Feast Table (a banquet table
  walking on its carved legs, its cloth the lips of a mouth). **Picked A**, 2026-10-29: "Hunger gone wrong" is a king
  who ate his court and is still hungry, Devour and Gorge are what that belly mouth does, and no other boss is a person
  with a mouth for a stomach. B is one more four-legged beast after the Thornback and the Iron Horse; C is one more
  walking piece of furniture after the Sleeper's bed and the Architect's house.
- **The Sovereign of Silence** (`sovereign_silence`): A the Stone Ear (a stooped giant of pale stone, long in the arm,
  whose whole head is one great ear with a black canal; small ears grow out of its shoulders through a black veil), B
  the Listening Horn (a gaunt courtier whose head is a brass ear trumpet), C the Veiled Mute (a tall veiled figure, its
  mouth stitched shut, a finger to its lips). **Picked A**, 2026-10-29: "It listened until there was nothing left to
  hear" is a body that is all ear, No Sound At All is a finger laid across that ear as across lips, Found You is the
  ear turning square to you, and no other boss has a head that is an organ of sense and nothing else. B is brass again
  after the Bell Warden; C is one more robed figure after the Abbot and the Herald, and it is the Hush Bat's silence
  again.
- **The Sovereign of Sleep** (`sovereign_sleep`): A the Night Owl (a great owl nine blocks across the wings that hangs in
  the air asleep, its eyes shut under heavy lids, the night and its stars on its back), B the Moth (a vast pale moth
  with eyes on its wings that sheds sleep like dust), C the Sleepwalker (a tall figure in a nightgown, arms out, its eyes
  shut, walking on air). **Picked A**, 2026-10-29: "It never let anyone wake up" is a creature of the night that is
  itself asleep, the Lullaby is an owl hooting with its wings wrapped round it, Never Wake is the one time it opens its
  eyes, and no other boss is a bird. B has the Hive Regent's four wings and banded fur again; C is one more robed
  figure, and one more piece of the bedroom after the Sleeper.
- **The Sovereign of Machines** (`sovereign_machines`): A the Armillary (a hulking engine of pale blue enamel on an
  iron frame with long ape arms and ram-driven fists, and in place of a head an armillary sphere: a gold core with one
  lens, three brass rings and two moons), B the Orrery (a planetary orrery walking on three long legs, its planets on
  arms), C the Hollow Engineer (a tall crowned automaton in a long coat of plates). **Picked A**, 2026-10-29: "It built
  until nothing was left to build with" is a machine that is all working parts, the Calculated Lance is its rings
  turning to line up into a sight round the lens, the Shutdown is everything on it stopping and starting again, and no
  other boss has a head that is an instrument. B is a fourth object on legs after the Bell, the Clock Tower and the
  Iron Horse; C is one more crowned figure after the Gorged King, and Unit Zero's ceramic automaton again.
- **The Sovereign of Mirrors** (`sovereign_mirrors`): A the Vanity (a tall slender figure in white satin with six arms,
  five of them holding round hand mirrors turned to its own face and the sixth a long blade of glass), B the Looking
  Glass (a faceless courtier gazing into one hand mirror, its only face the one in the glass), C the Faceted Duelist (a
  figure grown of mirror shards with a cut-diamond head). **Picked A**, 2026-10-29: "It only ever looked at itself" is
  a creature that never stops looking at itself, the Mirrored Lunge is every mirror swinging round to show you
  yourself, Shatter is all five bursting, and no other boss has six arms or carries its own reflections. B is the
  Mirror Knight's mirror mask and sword again; C is one more figure of crystal after the Glass Serpent.
- **The Sovereign of Storms** (`sovereign_storms`): A the Gale Hound (a huge lean storm hound veined with lightning,
  its ruff and long plume of a tail thundercloud), B the Thunder Ray (a sky manta of thundercloud trailing a lightning
  whip of a tail), C the Djinn of the Gale (a figure riding a whirlwind). **Picked A**, 2026-10-29: "It went so fast it
  left itself behind" is an animal that is all speed, its tail is the wind it left behind, the Bolt is a hound's leap,
  Overdrive is a wet dog's shake that throws the storm off, and no other boss is a hound or a wolf. B is a third great
  flat sky animal after the Moon Angler and the Sky Leviathan; C is the vanilla Breeze grown big.
- **The Sovereign of Roots** (`sovereign_roots`): A the Hearthstone Toad (a toad as big as a cottage with an old split
  hearthstone grown into its back, roots hanging off it to the floor and firelight in its mouth), B the Sett Badger (a
  great badger with its sett's roots and stones grown over its back), C the Star-Nosed Mole (a huge blind digger with
  a ring of root-like feelers round its nose). **Picked A**, 2026-10-29: "It made a home and closed every door" is a
  toad that has sat under a hearth so long the hearth has grown into it, Hold Fast is a toad's tongue, the Thorn Halo
  is a toad blowing itself up, and no other boss is a frog or a toad. B is a second big four-legged mammal straight
  after the Gale Hound; C is one more digger after the Thorn Pangolin.
- **The Sovereign of Ash** (`sovereign_ash`): A the Cinder Wyrm (a four-legged dragon nearly burnt out: charcoal hide
  with ember cracks, ash lying on its back, its ribs burnt open on one flank with its heart glowing between them, and
  holed burnt-leather wings), B the Ash Moth (a huge moth shedding ash off its wings), C the Charcoal Giant (a burnt
  giant of charcoal and embers). **Picked A**, 2026-10-29: "It burned everything to keep going" is a dragon burning its
  own body down, the Heart of Ash is the heart you can see between its burnt-open ribs, Ashfall and Burnout are its
  wings and its last fire, and no other boss is a dragon. B is a second big insect flier after the Hive Regent's
  Empress; C is the Living Forge's burning giant again.
- **The Signal's Shadow** (`signals_shadow`): A the Signal Cuttlefish (a cuttlefish as long as a cart, its black skin
  showing the Signal's own windows drifting over it, its two tentacles hidden in its crown of arms), B the Antler Stag
  (a pale stag whose antlers are the Signal's wiring), C the Glass Mantis (an upright mantis of the Signal's glass).
  **Picked A**, 2026-10-29: "It has your voice" is a cuttlefish, the animal that talks with its skin, and this one's
  skin shows the words you have read all game; the Lance is its tentacles, Signal Step its jet and ink, and nothing
  else in the roster swims like it. B is another big four-legged mammal after the Gale Hound; C is the Hive Regent's
  upright insect again, with glass wings.
- **The Long Drop** (`nightmare_falling`): A the Hand from Above (a hand as big as a cart with too-long fingers and
  yellowed nails, hanging out of the dark on an arm that never ends), B the Gibbet Kite (a stretched-skin kite
  trailing a cage), C the Gargoyle (a winged stone gargoyle that drops off its ledge). **Picked A**, 2026-10-29:
  "Lifts you up and lets you fall" is a hand that picks you up and opens, Fall With Me is a hand slamming flat on
  the floor, and no other boss is a hand. B reads less clearly at a glance; C repeats Unit Zero's cathedral statue
  and the owl's wings.
- **The Pyre** (`nightmare_burning`): A the Pyre Mare (a night-black horse whose mane, tail and fetlocks are fire,
  galloping on the air), B the Wicker Effigy (a burning wicker giant), C the Censer (a burning censer swung on chains
  out of the dark). **Picked A**, 2026-10-29: "Fire that follows you" is a horse made of fire running you down, the
  nightmare of the old stories is a black horse, and nothing else on the roster gallops. The Furnace Engine is a horse
  too, but a locomotive that plods; this one is flesh and fire on the air, rearing and striking. B is the Living
  Forge's burning giant again; C hangs and swings out of the dark like the Long Drop just did.
- **The Undertow** (`nightmare_drowning`): A the Old Snapper (an alligator snapping turtle as big as a boat, weed
  grown over its shell, a worm of teal light on its tongue), B the Drowned Bride (a drowned woman hanging in the water,
  her dress and hair floating up), C the Bell Jellyfish (a huge pale jellyfish trailing its stinging threads).
  **Picked A**, 2026-10-29: "Pulls you under and holds you there" is a snapper lying with its mouth open and its lure
  wriggling until you come close, the Undertow is that mouth drawing the water in, and no other boss is a turtle. B is
  one more robed figure after the Pilgrim and the Cantor; C's bell is the Bell Warden's shape again and its threads
  the Signal Cuttlefish's arms.
- **The Hollow Crowd** (`nightmare_undead`): A the Throng (a hunched giant made of people pressed into one body,
  heads standing out of it everywhere and every one the same face), B the Procession (mourners walking as one under a
  coffin), C the Chain (a line of sleepwalkers holding hands). **Picked A**, 2026-10-30: "They all have the same face"
  is a crowd that is one body and turns every face to you at once, it falls down and gets up again the way a crowd
  closes back in, and no other boss is made of people. B is one more robed figure after the Pilgrim and the Cantor; C
  reads as several mobs, not one boss.
- **The Thousand Legs** (`nightmare_arthropod`): A the House Centipede (a house centipede as long as a cart, its ten
  pairs of long banded legs fanned out round a thin striped body, its back legs held up like a second pair of feelers),
  B the Millipede (a black armoured millipede that coils up and rears), C the Web Spider (a pale spider hanging in its
  web). **Picked A**, 2026-10-30: "You can hear them in the walls" is the thing that runs out of the wall on more legs
  than you can count, its leg fan is a shape no other boss has, and A Thousand Legs is every one of them hammering the
  floor round you. B coils like the Cinder Wyrm and rears like the Glass Serpent; C is the Long-Legs' tall spider
  shape again.
- **The Fuse** (`nightmare_explosion`): A the Jack (a jack-in-the-box as big as a cart whose jack is a black iron bomb
  with a painted grin, three lit fuses curling from its crown for a jester's cap, on a long iron spring), B the Sea
  Mine (a hovering horned iron mine trailing its chains), C the Sapper Mole (an iron digging machine with powder kegs
  on its back). **Picked A**, 2026-10-30: the fear of the fuse is the wait for the bang, and a jack-in-the-box is
  that wait made into a toy: the crank ticks and you don't know when it pops. No other boss is a toy, and its box,
  spring and lid give it a shape and moves of its own. B is one more round thing hanging in the air after the
  Moon Angler's moon; C digs like the Thorn Pangolin.
