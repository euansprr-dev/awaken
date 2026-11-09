# The gates, in building order

Euan, 9 Nov 2026: "keep going with all 88 yourself". The stops no longer wait on him: each gate is built, previewed, walked once or
twice, and shown in batches; he vetoes what he doesn't like. Launch mix (SPEC §50): Land 20, Stronghold 14, Deep 12, Dungeon 14, Sky 8,
Desert and Ruin 8, Drowned 4, Climb 4, Wonder 4.

Theme limits by rank: E has Bell Crypt, Fungal Hollow, Ember Mines, Hive and Overgrown Temple. D adds Drowned Archive, Clockwork Foundry,
Glass Desert and Lantern Night Market. C adds Frost Monastery, Mirror Palace, Precursor Lab and Nightmare. B adds Sky Archipelago. A adds Abyssal Shrine.

Two rules for every entry: its great build is seen from the arrival, and its one physical rule changes how you move or fight there, not
only how it looks. No two gates in a wave share a form and a rule.

| Status | Key |
|---|---|
| done | built, previewed, walked |
| next | being built |
| | not started |

The bar is `BAR.md`: read it first. Who builds what (Euan, 10 Nov: two sub-agents alongside the main session):

- Main session: 3 The Vine Seat (remake to the bar), 10 Kingsroot Keep, 7 Saltcomb Flats; the arena kit; merging.
- Builder A: 4 Glowroot Hollow, 9 Sporefall, 11 The Wax Galleries, 14 The Broodwarren.
- Builder B: 5 Cinder Stair, 8 The Long Shaft, 12 The Ash Isles, 6 The Quiet Barrow, 13 The Flooded Ossuary.

## E rank (14)

| # | Status | Gate | Form | Theme | Rule | The great thing | Lord's place |
|---|---|---|---|---|---|---|---|
| 1 | done | Amber Reach | Land | Hive | Warm air lifts you up the cliffs | A spired citadel at the valley's end | Comb-domed hall in the great spire |
| 2 | done | The Hour Crypt | Dungeon | Bell Crypt | A bell tolls every minute; doors stand open only between tolls | A bell the size of a house, hung over the whole crypt | Under the bell, on the floor it shakes |
| 3 | done | The Vine Seat | Stronghold | Overgrown Temple | Roots grow over cleared paths behind you unless you keep moving | A stepped temple split by one enormous tree | Root bowl at the tree's foot |
| 4 | | Glowroot Hollow | Deep | Fungal Hollow | Spore clouds carry you across gaps; breathing them slows you | A cavern with a mushroom as tall as a tower | Ring over a glowing drop |
| 5 | | Cinder Stair | Climb | Ember Mines | The lava rises while you're on the way up | A mine dug straight up a dead volcano | Broken summit in the crater |
| 6 | | The Quiet Barrow | Land | Bell Crypt | Sound carries: running wakes what sleeps under the mounds | A burial hill with a stone ship on top | The ship's deck |
| 7 | done | Saltcomb Flats | Desert and Ruin | Hive | The salt sets hard in the cool wind and goes soft when the sun comes back | A dry hive-city half out of the sand | Sinking court |
| 8 | | The Long Shaft | Dungeon | Ember Mines | Cart rails carry you, and whatever's chasing you | A pit mine circled by a single rail | Forge floor at the bottom |
| 9 | | Sporefall | Land | Fungal Hollow | Puffball fields burst and launch you when you run over them | A mushroom forest under a split cliff | Root bowl under the largest cap |
| 10 | done | Kingsroot Keep | Stronghold | Overgrown Temple | Walls can be climbed where roots have broken them | A castle the jungle has eaten | Great hall with no roof left |
| 11 | | The Wax Galleries | Deep | Hive | Warm wax slows you; cold wax holds your weight | A hollow lit by a slowly dripping comb | Hanging comb over a drop |
| 12 | | The Ash Isles | Sky | Ember Mines | Columns of hot ash lift you between islands | Floating slag islands round a burning core | Long bridge to the core |
| 13 | | The Flooded Ossuary | Drowned | Bell Crypt | The water rises and falls with the bell | A drowned cathedral of bones | Ribcage nave |
| 14 | | The Broodwarren | Dungeon | Hive | Rooms open only while their larvae are asleep | A nursery of hanging cells | Brood chamber |

## D rank (16)

E left 74 to build: Land 17, Stronghold 12, Deep 10, Dungeon 11, Sky 7, Desert and Ruin 7, Drowned 3, Climb 3, Wonder 4. This wave takes Land 4, Stronghold 3, Deep 2, Dungeon 3,
Sky 1, Desert and Ruin 1, Drowned 1, Wonder 1, and it's where the four new themes come in. Each rule uses what already works (`change(moment)` block flips like the toll and the salt,
or a push on the player like an updraft). Check the Lord's fight exists in `data/awaken/kit/fights` before building its place.

| # | Status | Gate | Form | Theme | Rule | The great thing | Lord's place |
|---|---|---|---|---|---|---|---|
| 15 | | The Sliding Sea | Land | Glass Desert | Glass dunes are slick: going downhill you keep your speed and can't stop | A burnt-glass sun-tower in a bowl of glass dunes | The lens floor, where the sun comes to a point (glass_serpent) |
| 16 | | Paper Moon Fen | Land | Lantern Night Market | The mud only holds your weight where a lantern's light falls | A paper moon the size of a hill over a market on stilts | The auction barge (lantern_broker) |
| 17 | | The Turning Valley | Land | Clockwork Foundry | The valley floor sits on great turntables that move a quarter turn every minute, so the roads line up differently | A clock face a mile across laid into the ground | On the hour hand (gearwright_colossus) |
| 18 | | The Smoke Orchard | Land | Ember Mines | Hot smoke drifts along the ground and cooks you; the high branches are clear | A forest of iron chimney trees round one still burning | The furnace in the root bowl (ember_tyrant) |
| 19 | | Gearhold | Stronghold | Clockwork Foundry | Everything's on counterweights: stand on a plate and something else, somewhere, rises | A fortress hung from one enormous chain | The counterweight pit (gearwright_colossus) |
| 20 | | The Inkfall Citadel | Stronghold | Drowned Archive | Wet ink hides you from the watchers on the towers, until it dries | A citadel built like a stack of stone books, black ink falling down its sides | The top reading room (drowned_archivist) |
| 21 | | Glasshold | Stronghold | Glass Desert | Sunlight through the glass walls burns where it falls, and the great mirrors keep turning | A fortress of black glass with mirrors on its towers | The burning court (glass_serpent) |
| 22 | | The Undergear | Deep | Clockwork Foundry | Gear lifts rise and fall on a beat; miss it and you wait for the next | A cavern with one half-buried gear as wide as a town | On the gear itself, which turns during the fight (gearwright_colossus) |
| 23 | | Lanternroot | Deep | Fungal Hollow | Spores stick to you and make you glow: you can see, and everything can see you, until water washes them off | A lake of light under a roof of caps | An island in the lit lake (mother_of_spores) |
| 24 | | The Ledger Vaults | Dungeon | Drowned Archive | A sluice drains one room by flooding another | A stair of sluice gates going down through the whole vault | The drained ledger floor (drowned_archivist) |
| 25 | | The Escapement | Dungeon | Clockwork Foundry | Passages slide along by one place on every tick of the great clock | A pendulum swinging down through every floor | The pendulum's foot (gearwright_colossus) |
| 26 | | The Bazaar Cellars | Dungeon | Lantern Night Market | The lamps go out row by row behind you, and what lives in the dark follows | A shaft hung with a thousand lanterns | The Broker's strongroom (lantern_broker) |
| 27 | | The Hollow Sun | Sky | Glass Desert | Bridges of light hold only while a mirror's turned on them | A ring of mirrors round a burning glass orb | The orb's rim (glass_serpent) |
| 28 | | The Long Kiln | Desert and Ruin | Ember Mines | Ash storms blow through every few minutes: get behind a wall or be carried along | A wrecked kiln city a mile long | Inside the great kiln (ember_tyrant) |
| 29 | | The Sunken Reading Room | Drowned | Drowned Archive | When the current runs, the shelves float up and you can ride them | A cathedral-sized library lying on its side under the sea | The reading dome (drowned_archivist) |
| 30 | | The Upturned Market | Wonder | Lantern Night Market | Step past a railing and you fall upward, to the other market | Two markets facing each other, one hung over the other | A platform in the air between them (lantern_broker) |

## C to S (58)

Written wave by wave as each one starts.
