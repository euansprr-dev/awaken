# Milestone 4 · B3b: animals, the soundscape and the city at night

Branch `carillon/m4-b3b` (from carillon/main 32a28bc3, M5 the Atlas merged in). Started on the owner's Mac (four commits
up to 979148e6: lanterns, animals, soundscape, festoons; compiled, untested), finished in a cloud session on
2026-10-25 after the Mac's usage ran low. The cloud has a JDK 25 and the headless GameTest server, but no game client and
no speakers: **nothing in this step has been seen or heard in the game yet.** The tour (below) is the first look.

## What's built

**Drawn light, no light blocks** (`citizens/Lanterns`, `client/CityGlow`). The Watch's hand lanterns used to move up to
12 light blocks every 5 ticks, and each move relit 8-18 sections and re-meshed 60-100 client sections (docs/perf/
CITY-FPS.md). Now the client draws them: a hot core, a halo, and a warm pool on the stones round the bearer's feet that
moves with them. It's one additive pass (the lightning shaders: depth-tested, no depth written) that nothing touches a block for, and
each lit street lamp throws a wider pool (7.5 blocks) under it. Bearers are lit by their own lantern (block light ≥ 13
in the renderer). Old saves' light blocks are cleared as their chunks load. `/awaken citizens lanterns blocks|glow`
brings the old path back for an A/B, and `/awaken citizens lanterns` reports light-block writes since the last call.

**Choir Square's festoons** (`client/CityFestoons`): paper lanterns in five colours on rope strung round the bowl's
eight lamp posts, with four strings crossing over the Bell Stone. They're lit 12200-19500, with a flame and halo on every
lantern and a broad pool under every sixth. Client-drawn, no blocks, above the run's lines.

**Animals, client-only** (`citizens.CityCritter` with `client/CityCritters`, `CritterRenderer`, `CritterModel`;
rigs and coats from `tools/art/specs/city_fauna.py`). The server pays nothing for them: they're made near the player and
dropped beyond reach, and never saved.

| Animal | Where | Life | Reach · cap |
|---|---|---|---|
| pigeons (slate, white, brown) | 4 flocks on the square, both fountains, ~70% of the 17 wells, every district's bell tower | peck, walk, hop aside from citizens; burst up when you come close or run past (staggered, away from you), wheel round, land back or on the highest roof nearby; tower flocks lift when their bell rings | 56 · 40 |
| gulls | the 10 nets (2-3 each), flocks over the river every 70 blocks | squabble on the nets; ride the air, mostly gliding, calling | 72 · 16 |
| cats (ginger, grey, black, calico) | one house in five in Bellwatch, one in twelve elsewhere: ridge, eave corners, doorstep | sleep, loaf, groom, watch you within 9; slip away when you come within 2.4 or run past; walk and leap between storeys at night; eyes shine in the dark (glow layer by darkness) | 40 · 10 |
| dogs (terrier, collie, hound) | one keeper in seven (residents, children, the Watch, porters, washers, shopkeepers, millers) | trot at the owner's heel, sniff, sit, lie down; now and then come over to you, sit and pant, a bark | 48 · 8 |
| carthorses (bay, grey, chestnut, black) with carts (hay, crates, casks; a hooded carter) | 6 roads chained from the avenues, ~600 blocks each | on the world clock (every client agrees), keeping right; stop for a player in the road; four-beat hooves, a toss of the head with harness bells; a lantern at dusk | 96 · 4 |
| **boats** (tar, blue, green, ochre) | ~21 moored along both quays inside the wall (one every 24 blocks, a quarter empty, none within 6 of a bridge); 2 rowed up and down the river (410 blocks), 1 round the canal and back down the river (~200) | moored: rocking on the swell, a load of crates or nets or none, a third with the lantern lit at night; rowed: the boatman pulls (a 2.4 s stroke, blades clearing on the recovery, buried on the drive), oarlocks creak, rests on his oars as the boat comes round at the river's ends, bow lantern after dusk | 88 · 8 |

Every animal is a hero rig (bones, clips, crossfades) with the creature-life layer (breath, blinks, springy ears and tails,
glances) on top. Pigeons and gulls are designed larger and drawn at 0.5-0.6 so they don't read as four-pixel blobs. Boats skip the life layer and cast no
shadow. 7 rigs, 43 textures (coats, blink copies, cats' and boats' glow layers). The voices are vanilla's cat, wolf and
horse events, referenced by id. Ours: pigeon coo and flutter, gull call, cart roll, harness bells, oarlocks (the boat
creak, pitched up).

**The soundscape** (`citizens/Soundscape` on the server, `client/CitySound` on the client; everything rendered by
`tools/art/specs/audio_city_life.py`).
- **Beds** on core audio's ambience slot. By day it's `awaken:city/<district>` (7), after dark `awaken:city/night`
  (12500-23300). That's 10 stereo stems, 48 s long and exactly 2,116,800 frames each so they loop together, mixed per
  district by its manifest. The server picks the level every 100 ticks from the ledger: the district's citizens outdoors
  against its busiest hour (calm < 15%, light < 45%, busy < 80%, else packed), one lower in the rain. The night bed is
  busy until 20:30, light until 22:30, then calm.
- **Places that sound**, all mono so they come from their place. Loops eased in and out (fountains, the quays every 16
  blocks, the mill race, taverns 11000-18500). One-shots on their own clocks: forges 900-10800 (bellows now and then),
  stalls 1000-11500, boats creaking, songbirds (a dawn chorus three times as often), gulls over the river, the Watch's
  handbell at night. Then the Choir's carillon at dawn, noon and dusk (three tunes), heard across the city.
- 35 one-shot files in 10 events, 4 place loops, 3 tunes: 17 new sound events (21 `city.*` with M3's bells), all registered.

**Tour fixes**: the laundry-lane view moved to the Bellwatch lane with twelve lines (B3a's framed a wall).
**Quests**: named citizens (Kestrel, Bram, Oren, Mira, Teodor, Sun) answer a Wayfarer chain first (`Quests.talk`).

## What this session fixed and added (after 979148e6)
- **The new sound events weren't registered.** They were in sounds.json but not in the registry, which
  `audio_events_resolve` rejects. CI on 979148e6 failed, consistent with this; I didn't pull that run's test log to confirm
  it. All 17 are registered in `CitizensContent` now, and the client uses the holders.
- **The carillon was stereo**: OpenAL doesn't place stereo sounds, so it played at full volume anywhere within 420 blocks.
  It's mono now, comes from the spire and fades over its 320 blocks.
- **The Choir struck the hour over its own carillon**: at 06:00, noon and 18:00 both started on the same tick (noon is
  twelve strokes over 28 s). Now the tune plays first and the hour is struck 300 ticks later, as a clock tower does
  (`CitizenWork.AFTER_TUNE`).
- **Per-tick and per-frame allocations**: the carts' clock made a `double[]` per call (several per cart per tick) and the
  glow pass a capturing lambda per frame. Both use reused state now.
- **The ambience provider registered at priority 60**; the SPEC reserves 100 for the city's ids, so it's 100 now.
- **Boats** (the list above): the rig, four paints, the moored and rowed brains, the water finder (first water under the
  street, so bridges don't matter), bow lanterns through CityGlow, tour shots.
- `CityCritters.counts()` was dead code. Tours now log "[citizens] animals near you: …" every 30 s. The unused
  `homes(kind)` is gone. The plan paths `CityPlan` doesn't keep (canal, mill race) are read through one helper
  (`CityLife.path`); before, CitySound parsed the mill race by hand.

## Tests (one per mechanism, `citizens_` prefix, `citizens/CityLifeTests`)
| Test | What breaks silently without it | Result here |
|---|---|---|
| `citizens_lanterns_drawn` (isolated) | a lantern bearer walked 200 ticks writes no light block, holds and saves none; the A/B path writes exactly one and drawn light takes it away; an old save's light is cleared | pass |
| `citizens_soundscape_beds` | every district key matches the plan; 8 beds parse as ambient contexts; every stem is exactly the loop's frames; crowd stems never fall as the level rises; every `city.*` sound is mono; empty is calm and the busiest hour packed, never stepping down; each level's mix -37..-26 dBFS, ≤ -6 dBTP (decoded and measured off-thread) | pass |
| `citizens_fauna_rigs` | all 7 rigs parse without problems and carry every clip the brain plays by name (a missing one freezes the animal); every coat, blink and glow texture exists | pass |

Locally (JDK 25 in the cloud VM): the three plus `audio_events_resolve` and `audio_layers_manifests` pass. CI ran the
full suite on 4c611862: **run 40197683663, all 474 required tests passed.** Later runs are listed in the handoff
(docs/handoff/carillon-m4-b3b.md).

## Numbers
The beds' mixes, decoded from the shipped files (gated RMS / true peak, dBFS):

| Bed | calm | light | busy | packed |
|---|---|---|---|---|
| market | -34.2 / -19.0 | -31.8 / -16.7 | -29.7 / -13.1 | -27.8 / -10.5 |
| bellwatch | -34.5 / -18.8 | -33.3 / -18.3 | -31.8 / -16.2 | -30.4 / -14.0 |
| quay | -32.3 / -18.9 | -31.4 / -15.9 | -30.5 / -13.6 | -29.4 / -12.2 |
| terrace | -34.3 / -18.7 | -33.8 / -18.7 | -33.1 / -17.4 | -32.0 / -15.4 |
| works | -35.5 / -20.9 | -33.1 / -17.7 | -31.1 / -15.9 | -30.6 / -14.4 |
| garden | -34.2 / -17.9 | -33.3 / -16.6 | -32.8 / -16.3 | -32.6 / -16.4 |
| wallring | -34.6 / -18.6 | -34.2 / -18.7 | -33.7 / -18.8 | -33.4 / -18.7 |
| night | -34.7 / -15.8 | -34.3 / -17.3 | -33.3 / -17.7 | -32.7 / -17.7 |

The levels the roster gives (B3a's outdoor counts against each district's busiest hour): at 06:00, the Market, Quay and Wallring are
calm and the other four light. At noon, Bellwatch is busy and the rest packed (the Garden at 80%, on the edge). At
night it's the night bed. Lantern light-block writes: 0 in drawn mode (test). Before it was up to 12 blocks every 5
ticks while the Watch walked near a player.

**Not measured yet: frame cost.** Nothing here ran a client. To watch on the tour:
- The glow pass is about 100 quads per lamp in range (four rings of 20 plus three 8-quad sprites), ~40 lamps within 88 blocks: about 4,000 additive quads.
- Up to 86 critter entities, each a hero model with the life layer.
- The cart, boat and flock brains every tick.
The tour's lantern A/B (`perf lanterns_glow` and `lanterns_blocks`) gives the before/after the city-fps session asked for.

## Listening page
`docs/audio/carillon-b3b/index.html` (23 MB, 71 MP3s; rebuild with `python3 tools/art/audio_listen_city.py`). It has
seven districts at 06:00, noon and 22:00, after against before (before = the Choir striking the hour, otherwise
silence), each mixed offline the way the game would: the bed at the roster's level, places and one-shots at their hours,
rates and volumes with Minecraft's linear falloff. Every bed stem and place is also there alone. Keep / remake / cut with
a JSON export, my recommendations, and a CC0 shortlist (13 picks, licences read on each Freesound page; the longer list
is below). Nothing was downloaded.

CC0 candidates beyond the page's picks (all "Creative Commons 0" on their pages, 25 Oct 2026):
- distant crowd: Orsoo 757829 (a phone recording), leonelmail 427832 (a train station: announcements?)
- square: SamuelGremaud 544338 (Sault market), YannSauvin 777275 (the Tunis medina), bassimat 861594 (a piazza, 40 s),
  kyles 450590 (an 11-minute mono market walla)
- calls: tomhannen 698089 (a bazaar, binaural), jenniferelradhi 456702 (Athens traders, 64 kbps), guanescape 639443
  (a herb peddler's chant), AhmadAiuby 468099, SUPERORI 158419, AlbertinaVonStade 514623 (clear English)
- tavern: domasmo 376456, ivolipa 326313, kernoklepirate 322120 (staged, French), JWS24 790756, Zac_Gist 457286
- carts: Edulacava 453401 (8 channels), Garuda1982 631829, bruno.auzet 538438 (dirt, not cobbles)
- water: krakenkraft 730929 (Venice, maybe engines), Biofonik 851906, rj13 570955; harbour creaks soundandmelodies 846394
- fountain: Juno24 683330, urupin 198420, Nox_Sound 676173
- forge: ldezem 386393 (has a power hammer), Emmaproductions 254370, hobonski 703642 (festival music), scholzn 112291
- gulls: bruno.auzet 829207, etienne.leplumey 450529, richwise 806018 (ship hum?), ivolipa 328807, yaya1995 562741
- pigeons: nicotep 741563, NickPeeters 415019, TRP 616623
- excluded: craigsmith's 1930s-60s studio transfers (the right to waive them is unclear), bolkmar 424790 (asks for
  share-alike), tferrino 640625 (asks for no redistribution), Elenalostale 648593 (looks like a re-upload of Kinoton 468080).

## Risks
- **Unseen in a client.** Animal poses, scale against citizens, flock flight, cats' leaps, the carts' axle on corners,
  the festoons' sag, the glow's look on stone, boats on the water (the waterline is a guess from the rig: the keel two
  pixels under). Any of these may need a pass after the tour.
- **Unheard.** The page approximates the game (no Sound Physics, simple panning). The synthetic crowd is the weakest
  sound, and the night crickets' regular chirp may be heard as a loop.
- **Boats**: they pass under the canal's bridges at the water line; a low deck could clip through a boatman. Moored
  boats sit 7.2 blocks off the river's centre line, which assumes the quay walls stand at its 9-block half width.
- **Decision 28 said vanilla first.** This build synthesises nearly everything (decision 30 says why). Euan may want
  vanilla's water and fire under the fountains and forges.
- **Dogs** follow only citizens with bodies, so none far off; a keeper's dog vanishes with its body at the edge of reach.

## An honest read
The structure is right and cheap: no server cost for any of the life, no blocks for the light, beds that follow real
crowds, sounds that come from places, and a set of bugs fixed that would have been heard on day one (the stereo carillon,
the hour struck over the tune, sounds the registry didn't know). Against Euan's bar it's unproven. The animals and boats
have rigs, coats and behaviour at the level of the citizens, but nobody has looked at them in motion. The crowd murmur
is synthesis doing something synthesis is bad at; the CC0 crowd and criers are what would make the Market at noon feel
real. My guess: the night (lamp pools, festoons, lanterns on the boats, cats' eyes, taverns through walls) lands first;
the noon Market needs the recordings.

## Decisions (SPEC §16.0 decision 30)
Client-only animals and boats with their reaches and caps; drawn lantern and lamp light; the beds' contexts, levels and
loudness window; mono places; the carillon before the hour; synthesis with vanilla animal voices, CC0 after Euan's yes.
