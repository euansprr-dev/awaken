# The Far Landing: build plan (approved by Euan 11 Nov 2026)

- Euan's words: "Go ahead with the full plan and recommendations." All 11 decisions on the approval page are yes.
- Page: https://claude.ai/artifact/449tuDbGGVgmuAP2LAHRs2 (copy in `docs/journey/far-landing.html`).
- Branch: `claude/far-landing-ae982f`, in the spires-and-journey worktree. Part 1 lives on `claude/spires-and-journey-ae982f`.
- Session rules:
  - No local Gradle or Java while Euan plays.
  - Checks run as CI tours (`tools/tour-ci.sh`).
  - No sub-agents.
  - Code it all, then test.
  - The manager runs the GameTest suite at merge.

## The one switch: rules 4 (new worlds only)

- `RegionMap.RULES` becomes 4. The region record (`living_regions`) saves `rules`; a world with no record is new and gets 4. Old worlds keep their saved rules (V20 is 3).
- `RegionService.rulesOnDisk` reads it before the overworld loads, which is when the roads open.
- Every Far Landing behaviour asks `TheLull.landing(server) != null`, which is only true when rules ≥ 4 and Carillon stands.
- The GameTest server keeps `TEST_RULES` (2).

## The landing (pure maths in `core.api.FarLanding`, found once per server)

- `FarLanding.find(seed, cityX, cityZ, biomeAt, heightAt)` is pure:
  - Candidates: 48 bearings × radii 5,000 / 5,500 / 6,000 / 6,500 / 7,000 round the city.
  - Each candidate's score:
    - start biome: forest, plains, meadow, birch, flower forest or cherry score high;
    - refused: ocean, river, beach, swamp, mangrove, desert, badlands, peaks, jungle, mushroom, ice;
    - flatness: the height spread over a cross of points 48 apart;
    - water within 300: river or lake biomes at 16 points round it.
  - Ties break on a seed hash, so the result is deterministic. If nothing passes, the best of the rest is taken.
- The result is cached per server: `RoadWorld.open` and `RegionService` both read it. It is saved in the region record as `landing_x` / `landing_z`.
- `TheLull.landing(server)` is nullable, installed by the living module.

## Levels (rules 4, `RegionMap`)

- `cx, cz` stay the city: TheLull, the Commons and its name.
- `lx, lz` (the landing) is the danger origin.
- `wideLevelAt`:
  - city distance `dc` ≤ 700: the Commons ramp;
  - `dc` ≤ 1,000: levels 8–9;
  - otherwise: the region field, with `safe`, the odds and the calm ways measured from the landing;
  - a city ease from 8–9 at 1,000 to the field at 1,600.
- `makeWideLevel`: a heart within 1,300 of the landing, or within 1,000 of the city, is E. Otherwise the odds come from the heart's distance to the landing. calmFar naming measures from the landing.

## Roads and towns

- The grid is unchanged: hearts are absolute and the Commons swallow round the city.
- `FirstTown` and `Holdfasts` measure from the landing under rules 4, and keep ≥ ridge + CLEAR from the city as today.

## The skiff (`dev.awaken.frontier`)

- **The ridge mooring**: at the north road just past Aldo's milestone; placed once a player is near; saved in `FrontierData`.
- **The landing mooring**: placed when its chunk is loaded.
- Each mooring has a mast, a deck, the skiff at rest (block-built) and a helm block. The landing's also has the skiff hold and the Association board.
- The helm starts a flight for the party on the deck.
- The flight:
  1. Lift (camera rises into the clouds).
  2. Crossing: an overlay drawn like the gates' crossing. The server teleports and waits for the chunks, at least 3 s.
  3. Descent.
  - A sneak-use after the first flight skips to the crossing only.
- Safety:
  - a logout or server stop mid-flight puts you at the destination;
  - no damage while flying;
  - overworld only.

## Errands come to you (frontier worlds only)

- **Bram's bells**: rung by wagon, and his words arrive as a letter.
- **Hale's rack**: patterns can be bought at any Armoury door.
- **Hale's board**: at the landing mooring.
- **City people's steps**: the hold calls `Wayfarers.talkAs` for every city person's active deliver or talk step (not Ilse's). Oren's chain is offered by letter.
- **Village bells**: told by letter, with the villages measured from the landing; thanks come by letter.
- **Keepsakes**: sent through the hold; the family's reply comes as a letter and you keep the item.
- **City bells**: rung from afar when due, with a letter inviting you to come and hear it; standing at the spot later replays the bell once.
- **Tasks**: steps for the skiff, the hold, the invitations and the homecoming.

## The first hours

- Ilse: one new line (the skiff) and two changed lines.
- Aldo stays. The other fixed stone people and the stone cells centre on the landing.
- The Ember is unchanged.

## Story visits and the homecoming

- The invitations are optional, and nothing is locked behind them.
- The homecoming, the morning after the Hundred Peal:
  - the skiff comes for you;
  - every city bell rings;
  - Bram, Kestrel, Oren, Hale and Hettie each give their thanks;
  - Ilse comes down from the fire.

## Checks (at the end, on CI)

- Tours: the flight both ways; the landing, its mooring and the levels; the homecoming.
- GameTests (run by the manager): landing fairness on 20 seeds; levels under rules 4 (rules 3 unchanged); flight safety.
- Send Euan the skiff and mooring picture before merge.
