# The Run: how to play it, and what to look for

Everything here is on branch `claude/gates-as-worlds-plan-fcb81b`. It is not in `build/awaken-v1` and not in the play copy
until someone merges it. The new gates are behind the `gateRealms` switch (off), so the quickest way in is the command.

## Getting in

Stand anywhere in the overworld, as an admin:

| Gate | Command | What it is |
|---|---|---|
| The Hour Crypt | `/awaken gate realm_enter E awaken:hour_crypt` | The crypt you know, as a 16-leg run |
| The Broodwarren | `/awaken gate realm_enter E awaken:broodwarren` | A short crawl built by hand, fifty blocks down |
| The Lower Combs | `/awaken gate realm_enter E awaken:brood_burrow 1` | A brood nest rolled from the seed |
| The Bone Roads | `/awaken gate realm_enter E awaken:catacombs 1` | Catacombs rolled from the seed |
| The Hollow Hills | `/awaken gate realm_enter E awaken:hollow_hills 1` | Barrow passages rolled from the seed |
| The Root Cellars | `/awaken gate realm_enter E awaken:root_cellars 1` | A temple's undercroft rolled from the seed |
| The Tallow Runs | `/awaken gate realm_enter E awaken:tallow_runs 1` | Wax runs rolled from the seed |

The last five take any whole number as the seed: `... awaken:catacombs 2` is a different dungeon. Change `E` for another rank
to make everything in it stronger. `/awaken gate leave` gets you out. Play in survival: an ambush does not spring for a
creative player, and packs do not notice one.

## What should happen

- **Packs wait where you can see them** and wake when you come within about thirteen blocks in their sight, or five whatever
  is in the way, or when you hurt one.
- **A fight's doors shut** while its pack is awake and you are inside, and open when the pack is dead or you are gone.
- **An ambush** has nothing standing in it. In the Broodwarren it is the larder: walk to the middle and the sacs burst.
- **Traps.** The Crypt's Ossuary gallery has three rows of stone teeth that come up one after another. The Broodwarren's
  gallery has five cells that light and then spit across it. Thin comb on the nursery's ledge gives way under you.
- **A long fall** in the Crypt's tower or a nest's shaft costs a little health and puts you back on the last ledge you stood on.
- **A cache appears** where a herd falls.
- **The HUD** reads "Herds 2 / 5 · Wardens 1 / 3", then that the way on is open.
- **Hidden places.** The Crypt's reliquary is at the south end of the Ossuary's cross aisle; its door stands open only while
  the bell rings, and Hushlings keep the dark aisle in front of it (sneak, or stay in candle light). The Broodwarren's jelly
  cell is behind a curtain of sacs on the larder's east side. Finding one pays into your Satchel.
- **Shortcuts.** Kill the Crypt's Warden among the ropes and a bell rope comes down from the bridge to the floor (bell ropes
  can be climbed now). Clear the Broodwarren's larder and the workers' crawl opens back up to the first chamber.
- **Creatures with their own bodies.** Drip Lurkers in the Broodwarren's larder, Hushlings in the Crypt's south aisle, and the
  Thousandfoot as the Broodwarren's Warden. The rolled dungeons field them too.

## What I could not check, so you are the first to see it

- Whether any of it is fun. Nothing here has been played by a person.
- How hard it is. Packs are five to seven at E rank and Wardens have companies; pressure sends waves after four idle minutes.
- The teeth, the spitters and the fall catch hurting and moving a player: compiled and unit-checked only. The ambush, the
  sealing door, a hidden place, named monsters and the Thousandfoot were seen working in a real-game walk.
- Whether the Thousandfoot, a Lurker or a Hushling fights well as a gate monster. They come with the habits they have in the
  caves.
- Sound. Traps and doors use sounds the mod already had.

## Known rough edges

- Blocks in a gate can be broken by hand, so a door can be dug through. Not changed; it needs a decision.
- The Hour Crypt still looks flatter and bluer than its painting, and The Quiet Barrow is still short of its painting (listed
  in `../round-two/STATUS.md`). This work was about play, not those looks.
- The rolled dungeons are plainer than a gate built by hand from a painting. They are meant to be the short gates in the mix.
- The three skins the builders wrote (Hollow Hills, Root Cellars, Tallow Runs) have been checked on the bench and by test on
  six seeds each, and looked at once.
