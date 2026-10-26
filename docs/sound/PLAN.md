# World Sound: the plan (for Euan's yes)

Status: **approved 2026-10-25** (Euan: "Go ahead with all recommendations": all seven decisions in §12). SPEC
records them under §13 "Plan v2 · world sound", §12 (`Soundscape`), §14 and §15. Branch `pack/world-sound-plan`.
Approval page: `docs/sound/plan.html`. Hand-off: `docs/handoff/pack-world-sound-plan.md`.

**Revised the same day (Euan, after seeing the recordings shortlist):** *"Let's use AmbientSounds, add it to the sound
profile ... we also build the hush and the hums and all of our own custom stuff, and then when that plays, maybe the
ambient sounds play a bit less ... rebuilding from scratch something that already exists very well is not smart."*
And no field recordings of our own: they would sound out of place with Minecraft. So:
- **AmbientSounds plays the world's nature** in every biome (it is in the `sound` profile with CreativeCore). Its
  trade-offs (§4: sound around the head rather than from places; every sound streamed) are accepted.
- **The Conductor plays only what is ours**: hushes, hums, magic, places, reactions (§6.3's places and §7's touches),
  and lowers AmbientSounds while one of our moments sounds (`Soundscape.natureLevel()`, applied by
  `compat.client.AmbientSoundsBridge`). The first one is built: the world holds its breath near a gate.
- **Decision 3 and decision 5 below are superseded**; §6.2's families stay only as the Conductor's fallback when
  AmbientSounds isn't installed (Greenwood is built; no more nature families will be). §6.5's CC0 step is dropped;
  our own sounds are vanilla events and synthesis. The listening batches cover our sounds only.

Euan's brief: *"In every single biome and everything, I want to have like immersive sounds ... maybe we use [existing
mods] instead of reinventing everything from scratch ... the number one goal here is to make it actually immersive,
not just like, oh, mobs here, this there, but actually like truly immersive in terms of how it feels to be inside of
the game."*

## 1. The answer in one page

- **Is it already done?** Partly. AWAKEN already has: room acoustics (Sound Physics Remastered), adaptive gate music,
  the System's UI voice, creature voices, beds for four of the six Awakened biomes, leaf rustle and high wind, a few
  magic beds (rifts, groves, stars, the Well, ruins at night), vanilla-layered one-shots in the gate and Descent biomes,
  and the city soundscape now being built (Carillon B3b). What is missing is the **world itself**: 56 vanilla overworld
  biomes, and two of the six Awakened ones, still sound like vanilla Minecraft, which has no birds, no insects
  (apart from bees), no wind, no surf, no river and one rain sound for every surface. Nothing reacts to you, to danger
  or to the magic.
- **Use existing mods?** Yes, where a mod is the best at its job and plays well with our world: **Sound Physics
  Remastered** stays (space) and **Tread Lightly** does footsteps, if a one-hour spike passes. **AmbientSounds**, the
  best general ambience mod, has a 26.3 NeoForge build and is the bar we measure against, but it isn't shipped: it
  plays every sound around your head rather than from a place (so Sound Physics skips it), streams every sound
  (competing with our music for the engine's 8 streaming channels), and can't see gates, happenings or the magic
  (§4, §5). Two popular picks are out: **Sounds** can't load on 26.3, and **Raise Sound Limit Simplified** changes
  how playback reports itself in ways our music player depends on.
- **Recommendation: approach C, the hybrid.** A world ambience layer of our own in `core/audio` (we call it the
  **Conductor**), because nothing on the market knows AWAKEN's biomes, dimensions, gates, happenings or magic, and
  because the reactions Euan wants (birds falling quiet near a predator, the world holding its breath before a gate
  breaks) need our game state. Mods do the jobs they already do best (space and footsteps). Content follows Carillon's rule: vanilla first,
  CC0 recordings where vanilla has nothing (birds, insects, water, rain on surfaces), synthesis for the magic.
- **Effort at AI pace:** about 28 builder-hours in five phases (two builders in parallel: about two days of wall
  clock). The critical path is Euan's ears: five listening batches of about ten minutes each.
- **Seven decisions** for Euan are in §12. Each comes with a recommendation.

## 2. What "immersive" means here, as things you can check

Each behaviour is observable in game and is checked by a listening batch (§9) or a test.

| # | Behaviour | How we know |
|---|---|---|
| 1 | **No audible loops.** You can stand in one place for ten minutes and never hear the same moment twice. | Beds are 3 layers of co-prime length (e.g. 37 s, 41 s, 53 s: the mix repeats only after 22 hours); every salient sound is a one-shot from 6+ variants with pitch, volume and timing spread. Test: `audio_world_loops`. Listening: "spot the seam" in batch 1. |
| 2 | **Sounds come from places.** Birds sing from the trees you can see, the river from the river, frogs from the water, wind from the open side of a ridge, surf from the shore. | Positional mono emitters anchored to real blocks found around the listener; Sound Physics gives them the room and the occlusion. Only the diffuse air of a place is a non-positional bed. |
| 3 | **Transitions you don't notice.** Walking from forest into plains, dusk into night, clear into rain: nothing switches, things just change. | Biome weights from a ring of 16 samples, eased over 8 to 15 s; time of day as continuous curves (dawn chorus peaks at sunrise and fades by mid-morning); weather crossfades over 6 to 20 s. |
| 4 | **Silence used on purpose.** Lulls where the forest goes still for half a minute; deep caves almost silent; the Mirror Marsh deliberately still; nothing at all in the Abyssal Shrine. | Scheduled lulls (density near zero for 20 to 40 s every few minutes); per-place silence floors in data. |
| 5 | **The world reacts.** Birds fall quiet when a predator or a hostile is near and come back after it leaves; crickets stop around your feet and start again behind you; birds flush when you sprint through brush; rain sounds different on leaves, stone, water and a roof over your head; drips go on falling from the leaves for a minute after the rain stops; thunder arrives after the flash, later the farther the strike; wind rises with height and exposure; footsteps follow the surface and your armour. | The Conductor's reactions (§6.4); batch 3 plays each one before and after. |
| 6 | **The world is AWAKEN's.** Near an open gate the wildlife goes quiet; before a Break the hush spreads; the Awakened lands carry a faint harmony in the System's key; a perceptive hunter hears a lair breathing before seeing it. | §7, batch 5. |
| 7 | **It never fights the music or the city.** | §8: the ambience slot never touches the music channel; the Conductor ducks under combat music and yields inside Carillon. |

## 3. What already exists (audited in the code, 2026-10-25)

| Piece | Where | State |
|---|---|---|
| Sound Physics Remastered 1.5.2 (reverb, occlusion) | `pack/mods.json` `sound` profile, `pack/spr/sound_rates.properties` (66 lines: System, UI, music, Ritual voices dry) | In the pack, beta build for 26.3 (2026-10-21). |
| Layered music, two slots (music, ambience) | `core/audio`: `MusicLayers`, `LayerPlayer`, `StemMixer` (Java mixer over vanilla `JOrbisAudioStream`), `LayerManifest` | Gates in use (`awaken:gate/<theme>`, 14 × 4 stems); `awaken:descent/stratum_<n>` and `awaken:city/<district>` reserved. Listening sheet 2026-10-24 never marked. |
| Magic beds: rift, grove, falling star, cooling meteor, the Well, ruins at night | `core/audio/client/Ambience` (entity join/leave, server emitter list every 5 s) | Built, synthesised. |
| Awakened biome beds | `amb.glass_steppe`, `amb.shattered_highlands`, `amb.frost_spires`, `amb.mirror_marsh`, played as the biomes' `minecraft:audio/ambient_sounds` `loop` | Four of six. **Elderwood and Lumen Forest have none** (`docs/modules/lands.md`: "the ambience beds are not in yet"). |
| Leaf rustle, high wind | `terrain/client/LeafRustle`, `amb.lands_rustle`, `amb.lands_wind_high`, driven by the shared deterministic `terrain.Wind` | Built; setting `leaf_rustle`. |
| Gate and Descent biome ambience | `data/awaken/worldgen/biome/gate_*.json`, `stratum_*.json`: vanilla loops and `additions` (bells, gears, chant, buzz, wind, Listener clicks...) | Built from vanilla events. |
| Other hums | `gate.hum` (beacon), `above.door_hum`, `veil.hum`, `anomaly.rift_hum` | Built, vanilla-layered. |
| Creature voices | 47 fauna species, 5-11 sounds each (`assets/awaken/sounds/fauna/`) | Built. |
| Flight wind | `ascent.wind` (`body/ascent`), the rush follows the body's speed (SPEC §17.15) | Built. |
| City soundscape | SPEC §16.0 decision 28, Carillon B3b (not pushed yet; B3a on `carillon/m4-b3a`) | Being built on the Mac. Out of scope here: we plug into it. |
| Re-voicing a vanilla sound | NeoForge `PlaySoundEvent` (`holo/client/HoloAudio.onPlaySound` already swaps `signal.open`) | Proven in our code: the route for surface-aware rain and delayed thunder, no mixins. |
| Tools | `tools/art/audiokit.py` (synthesis, rooms, mastering), `audio_build.py`, `audio_mix.py` (loudness per category), `audio_listen.py` (listening pages), `audio_score.py`, `audio_sheet.py` | Built and proven on 70 stems. |
| Disk | `assets/awaken/sounds`: 52 MB (gate stems 26 MB) | Budget in §10. |

**Vanilla 26.3's own palette**, read from Mojang's 26.3 asset index (index 34, 1,991 sound events, fetched 2026-10-25):
nether loops and additions (5 biomes), the cave mood (23 variants), underwater loop and additions, `weather.rain` (8)
and `weather.rain.above` (4), thunder, block ambience (`block.sand.idle` 23, `block.dry_grass.ambient` 12,
`block.deadbush.idle` 11, `block.firefly_bush.idle` 11, `block.pale_hanging_moss.idle` 15, `block.eyeblossom.idle`,
`block.creaking_heart.idle`, `block.poplar_leaves.ambient`, `block.pointed_dripstone.drip_water` 15, water, lava,
fire, the sulfur caves' geysers), and creature calls we can borrow at a distance (`entity.frog.ambient` 8,
`entity.parrot.ambient` 6, `entity.wolf.*`, `entity.fox.screech`, `entity.bat.ambient`, `entity.bee.loop`,
`entity.dolphin.ambient`). **Vanilla has no songbirds, crickets, cicadas, wind, surf, flowing river, gulls or rain on
different surfaces.** Those are the gaps CC0 recordings fill. (New since 1.21: `dappled_forest`, `sulfur_caves` and
poplar leaves; the biome table already has rows for the first two.)

## 4. Research: the existing mods

Checked 2026-10-25: the Modrinth API (NeoForge builds for 26.3, licences, dependencies), each mod's source at a
named branch or commit, its issue tracker and its jar's `neoforge.mods.toml`. Nothing was launched; Phase 0's spike
does that. The repo is public, so we never copy another mod's code or sounds into it: "use theirs" means a pack
dependency, or data and config we write for it.

| Mod | What it does | NeoForge 26.3 | Licence, modpack | Performance | Can we feed it our data? | Verdict |
|---|---|---|---|---|---|---|
| **Sound Physics Remastered** (henkelmax) | Reverb, occlusion, air absorption, underwater filter, sound redirection | `neoforge-1.5.2+26.3`, beta, 2026-10-21 (already pinned; ≥ 1.5.2 needed for OpenAL Soft 1.22-1.23 reverb) | GPL-3.0; in the pack | Ray tracing when each sound starts (16 occlusion rays, 32 × 4 environment rays) on the sound thread | Config only (`sound_rates.properties`, reflectivity) | **Keep.** Our positional voices get the room |
| **AmbientSounds** (CreativeMD) | The leading biome-ambience engine: regions by biome, height, light, sky, room size, rain, time, nearby block groups, nearby entities | **6.3.11, release, 2026-10-19**; needs CreativeCore 2.14.24 (2026-10-23) | LGPL-3.0 (the author: "go ahead" for packs, #214); 120 recordings (~82 MB), sources credited only in general (naturesoundsfor.me, ambient-mixer), per-file licences unclear | Scan every 40 ticks (7×7 biome grid, air-pocket flood fill on a new thread, up to 50,000 blocks), allocation-heavy; "not by much" (author, #264) | **Yes**: every file but `engine.json` loads from every pack and jar (`listResourceStacks`), so our jar could add regions and dimensions; biomes match by id substring or `#tag` (Elderwood would get forest sounds through `#minecraft:is_forest`) | **Benchmark, not shipped.** It plays every sound head-relative with no attenuation, so it never comes from a place and Sound Physics skips it; every sound streams (88 stream entries, two per crossfade), competing with our music for vanilla's ~8 streaming channels; it can't see gates, strata, happenings or powers; an unknown dimension falls into its overworld `surface` profile (birds in a gate). Crashed with RSLS in 2025 (#16, #18) |
| **Tread Lightly** (aspctt) | Footsteps per foot by surface, foliage brushing, a wet layer in rain and water, a boots layer, jump, land, ladder and swim; steps for mobs (filterable); suppresses vanilla steps | **1.1.0+26.3, 2026-09-23**, NeoForge only, client only; needs NeoForge ≥ 26.3.0.12 | LGPL-3.0+; forked from Presence Footsteps' MIT-era code (before PF's licence change of 2026-06-18) | PF's engine with a 50-entity cap; every step is ray-traced by Sound Physics | **Yes**: `assets/treadlightly/config/blockmap.json` merges from every pack, so our jar maps our blocks; per-entity maps; `/treadlightly report` lists unmapped blocks | **Adopt after the spike.** Young (first commit 2026-09-03, 1.5k downloads, one author, 0 issues); its 898 sounds come from Presence Footsteps with no provenance trail (fine as a pack dependency, never in our repo) |
| **Presence Footsteps** (Sollace) | The original material-footsteps mod (41.8M downloads) | **No**: Fabric and Quilt only; the author has "no intention of working with neoforge" (#383); Sinytra Connector stops at 26.2 | PolyForm Shield 1.0 since 2026-06-18 | 50-entity cap after a Sound Physics lag report (#324) | Yes, on Fabric | **Out**: not on our loader |
| **Raise Sound Limit Simplified** (ishland, RelativityMC) | Raises OpenAL sources to 4,095 and moves sound work off the render thread | 1.4.0+neoforge, 2026-09-28 | MIT | Takes sound calls off the render thread | Config only | **Out.** Playback becomes asynchronous (`play` always reports started, `isActive` is true while a sound is still queued, a sound queued over 1 s is dropped, tickable sounds tick a frame late): our `CuePlayer` and `LayerPlayer` ask the engine live. Open "music missing" bugs (#20, #28, #33). Our budget (§10) fits the vanilla pool, so we don't need the headroom |
| **Sounds** (IMB11) | ~170 recorded effects for UI, chat, status effects, blocks | Its 26.3 jar needs YACL ≥ 3.9.7+26.3; the only NeoForge YACL declares `[26.2,26.3)`: **it can't load** | All rights reserved, EULA (Zapsplat recordings); linking from a pack is allowed, bundling is not | – | Pack-defined block, item and screen sounds | **Out**: can't load; re-voices the UI and status effects, which are the System's |
| **Dynamic Surroundings** (OreCruncher) | Biome loops, footsteps, thunder, waterfalls, wind, aurora; a classic | Last release 1.21.1 (2026-10-21); a 26.2 port branch is in progress; **no 26.3** | MIT code; sounds mix CC0, CC-BY and **CC-BY-NC** | – | Yes (`dsconfigs/biomes.json`, biome traits by tag) | **Out for now**; worth a look if a 26.3 build ships |
| Smaller ones | **Immersive Thunder Reforged** (MIT, 1.3.8+26.3: thunder delayed by distance), Drip Sounds (LGPL, 26.3 beta), Extreme Sound Muffler, Sound Controller, Sounds Be Gone (per-sound volume tools), Falling Wind (elytra wind), Particular Reforged (visual) | 26.3 builds exist | open licences | – | – | Thunder Reforged joins the spike (if clean, it replaces our thunder re-voicing); the rest overlap things we have or are player tools |
| Not on 26.3 NeoForge | MAtmos (all rights reserved, up to 26.2), Resonance (LGPL, up to 1.21.11), Charmonium and Euphonium (Fabric), Tense Ambience (Fabric and Forge, all rights reserved), Auditory (discontinued) | – | – | – | – | Out |

**What this means for the design.**
- Sound Physics skips relative sounds at the origin, rain, thunder and any event named `<ns>:ambient.*` by default,
  and evaluates a positioned loop only when it starts. So: the AirBed is relative (we filter it ourselves); our
  positional events and textures keep `amb.*`/`world.*` names (never `ambient.*`) so they get the room; textures are
  re-anchored with a short crossfade when the enclosure changes and every ~12 s, so walking indoors updates their
  occlusion without turning on Sound Physics' global `update_moving_sounds`; our re-voiced rain gets a new event id,
  which Sound Physics processes (rain heard through the door).
- Vanilla's pool is about 248 static and 8 streaming sources on OpenAL Soft's default 256 (the 255 static cap is confirmed
  by RSLS's 26.3 patch; the streaming formula, clamp(√n, 2, 8), is to confirm in `.ref`). AmbientSounds alone would
  want several streams; our whole Conductor wants one.
- If a player adds AmbientSounds themselves, it would play overworld birds in our gates. Our jar can ship inert data
  for it (`assets/ambientsounds/basic/dimensions/awaken_*.json`, sorted before its `surface` profile, `"mute": true`
  for instanced dimensions), and the Conductor drops its own nature events while AmbientSounds is loaded. That puts
  files in another mod's namespace, a SPEC decision (decision 3).

## 5. Three approaches, compared

| | **A. Adopt** (AmbientSounds plus data for our places) | **B. Our own layer only** | **C. Hybrid** (recommended) |
|---|---|---|---|
| What ships | AmbientSounds + CreativeCore in the `sound` profile, its data for our biomes and dimensions from our jar | The Conductor in `core/audio`; our own footsteps; no new mods | The Conductor for biomes, places, reactions and magic; Sound Physics for space; Tread Lightly for footsteps |
| Knows our biomes | By id substring or tag, and our own region files (§4) | Yes (biome table tags) | Yes |
| Knows our dimensions (gates, strata, Veil, Stillpoint, Lucidium, Halo) | Only through a dimension file each; otherwise its overworld profile | Yes | Yes |
| Reacts to gates, predators, happenings, powers, PER | Predators partly (entity conditions); nothing else of our game state | Yes | Yes |
| Sounds come from places | No: every sound head-relative, no reverb or occlusion | Yes | Yes |
| Fits `core/audio`, the music and the city | A second engine with its own volumes and timing; streams every sound (8-channel pool) | Yes | Yes |
| AWAKEN's own voice | Its recordings are realistic nature; no magic | Yes | Yes |
| Footsteps | No | We build them (a week of content we don't need to make) | Tread Lightly, data for our blocks |
| Risk if the mod lags | A 26.4 update could break the pack's main ambience | None | Small: footsteps fall back to vanilla, then to ours |
| Builder effort | ~10 h (data and config) | ~34 h | ~28 h |
| Quality ceiling | Good realistic nature, generic | Highest, all content ours | Highest |

**Why C.** Euan asked for the feeling of being inside AWAKEN's world, and the parts that make that feeling are the
parts no mod can know: the hush before a Break, the Awakened lands' harmony, a happening you hear before you see, the
world muffled in the Veil, clarity rising with lucidity in a dream. Writing those as conditions into another mod's
engine would leave the ambience split between two engines that both decide what plays, with two volume systems, two
schedulers and one pool of streaming channels. The parts of the job that are about physics rather than AWAKEN
(how a hall reverberates, which block your boot lands on) are exactly where
mods are mature, so we take theirs.

**What would change my mind:**
- AmbientSounds: two of the three facts are already in. It **does** take data from our jar; it does **not** play from
  places. The third is Euan's ears: if it **beats our first forest** in batch 1's blind A/B, the gap is content, and we
  either raise ours (more and better CC0 recordings) or adopt it for vanilla nature beds only (the Conductor keeps
  events, places, reactions and magic, and we cap its streams in its config). That would save about 8 builder-hours
  of nature content.
- If Immersive Thunder Reforged passes the same spike, it replaces our thunder re-voicing (−1 h).
- If Tread Lightly fails the spike (crashes, fights Sound Physics, or sounds worse than vanilla), footsteps stay
  vanilla in Phase 1 and the Conductor re-voices player steps by surface and armour through `PlaySoundEvent` in
  Phase 3 (+3 h).
- If our voice budget ever proves too tight on the vanilla pool (it shouldn't: §10), RSLS comes back for a second
  look, with our music player made safe for asynchronous playback first.

## 6. The design (approach C)

### 6.1 Shape

```
                   ┌────────────────────────────── core/audio (common) ──────────────────────────────┐
 biome table ───►  │ #awaken:soundscape/<family> biome tags   SoundscapeDef (codec, schemaVersion 1)  │
 (terrain)         │ assets/<ns>/soundscape/<id>.json          Soundscape API (§6.6)                  │
                   └──────────────────────────────────────────────────────────────────────────────────┘
                   ┌──────────────────────────── core/audio/client/world ────────────────────────────┐
                   │ Listener   biome ring (16 pts), enclosure, openings, altitude, exposure, water,  │
                   │            surface, time, moon, weather, music level        (staggered, 4 Hz)    │
                   │ Conductor  targets per layer, hushes, lulls, voice budget and stealing           │
                   │   ├─ AirBed     ONE stream: co-prime bed layers mixed in Java, crossfades and    │
                   │   │             a one-pole filter (indoors, underwater, Veil, snow, lucidity)    │
                   │   ├─ Textures   ≤ 6 positional loops at real blocks (canopy, water, surf, rain)  │
                   │   ├─ Events     ≤ 10 positional one-shots (birds in trees, frogs, owls, creaks)  │
                   │   └─ Revoice    PlaySoundEvent: rain by surface, thunder by distance, steps*     │
                   │ Sources    other modules' emitters and hushes (Ambience, gates, living, city)    │
                   └──────────────────────────────────────────────────────────────────────────────────┘
                      ▲ Sound Physics Remastered processes the positional voices (room, occlusion)
                      ▲ Tread Lightly owns footsteps (*our fallback only if it fails the spike)
```

Four layers, each doing one job:

1. **Air** (the AirBed): the diffuse sound of a place: the hush of a forest, the air of a cave, the empty high wind
   of a peak. One streamed, stereo, non-positional sound for the whole Conductor. Inside it, a Java mixer (the same
   decoder path `StemMixer` uses; the shared part moves into one class so the two never duplicate) plays up to six
   bed layers: two families crossfading, each with up to three layers of co-prime length. Because the mixer is ours,
   it can filter: a one-pole low-pass closes when you step indoors, go underwater, project into the Veil, stand in
   falling snow, or dream with low lucidity. Vanilla's sound engine cannot filter without mixins; our own stream can.
2. **Textures**: continuous sounds that belong to a spot: leaves overhead, the river, surf on the shore, rain on the
   stone around you, insects in the grass. Positional mono loops anchored to real blocks, at most six, moved as you
   move. Sound Physics gives them occlusion, so outdoor textures come in through doors and windows on their own
   (it evaluates a loop when it starts, so a texture is re-anchored with a short crossfade when the enclosure changes
   and every ~12 s).
3. **Events**: the salient one-shots: a wren in that oak, an answer from the next tree, a frog plop, an owl, an ice
   creak, a falling pebble on a cliff, a wolf far off at full moon. At most ten at once, at most four new per second.
   Poisson timing with per-family density curves, call-and-answer pairs, lulls.
4. **Reactions and re-voicing**: hushes (predators, gates, powers, happenings), flushes, the cricket ring, after-rain
   drips, thunder delayed by distance, rain re-voiced by the surface it falls on.

### 6.2 Every biome: soundscape families

Every biome gets a soundscape through a biome tag `#awaken:soundscape/<family>` written by `tools/terrain/biomes.py`
(a new `soundscape` column, so the table stays the one source). The census test fails if any overworld biome, vanilla
or Awakened, is in no family or in two. A biome from another mod with no tag falls back by climate (temperature,
downfall, height) to the nearest family, so nothing is ever silent by accident. Awakened biomes are their family plus
a **variant** (its own bed layer and its magic, §7).

| Family | Biomes (56 vanilla + 6 Awakened) | Air (bed) | Textures | Events by day / by night | Weather and other |
|---|---|---|---|---|---|
| **Greenwood** | forest, birch forest, old growth birch, dappled forest; **Elderwood** | soft broadleaf hush, far wood | canopy rustle (existing), stream if near | songbirds (dawn chorus), woodpecker, wood pigeon, squirrel chatter / owls (our Hollow Owl), crickets at edges, a fox screech | rain on leaves; drips for 60-90 s after; poplar leaves' own ambience where they grow |
| **Blossom** | cherry grove, flower forest | warm light air | petals and leaves, bees (vanilla loop) near flowers | finches, bees, a cuckoo far off / crickets, fireflies' faint ticks (vanilla firefly bush) | soft rain |
| **Duskwood** | dark forest, pale garden; **Lumen Forest** | low still air, a held breath | heavy canopy creak | few birds, a raven / owls, creaks, pale garden almost silent (vanilla pale moss, eyeblossom, creaking heart) | rain loud on the roof of leaves, dark under it |
| **Conifer** | taiga, snowy taiga, old growth pine and spruce, windswept forest | wind through needles | needle hiss in gusts | crossbills, tits, woodpecker / owl, a wolf far off | snow variant muffles |
| **Open grass** | plains, sunflower plains, meadow | open breeze over grass | grass hiss in gusts | larks high up, a kestrel cry, bees / crickets in the grass (the ring, §6.4), a field cricket chorus | rain on grass, distant thunder carries |
| **Dry warmth** | savanna, savanna plateau, windswept savanna; **Glass Steppe** | warm dry wind | dry grass (vanilla `dry_grass.ambient`) | cicadas by heat, weaver birds, distant herd calls / nightjar churr, crickets | rare rain, wind gusts |
| **Desert** | desert, badlands, eroded and wooded badlands | dry wind, deep quiet | sand hiss (vanilla `sand.idle`), dead bush (vanilla) | almost nothing: a lizard scuttle, one bird / cold wind, a far howl | sandstorm wind on rain, no rain sound |
| **Canopy** | jungle, sparse jungle, bamboo jungle | humid dense air | insect wall, drips | tropical birds, parrots (vanilla, far), monkeys none / frogs and katydids, a big cat far off (our Canopy Prowler) | heavy rain on big leaves, after-drips long |
| **Mire** | swamp, mangrove swamp; **Mirror Marsh** | wet low air | water lapping, insects | frogs (vanilla), herons, dragonfly buzz / frog chorus, bog bubbles, our Bog Toad | rain on still water; Mirror Marsh stays still (its existing bed) |
| **Heights** | windswept hills, windswept gravelly hills, stony peaks, jagged peaks, frozen peaks; **Shattered Highlands** | wind, rising with height | gusts across rock, the open side louder | ravens, a peak kite cry, marmot whistles (our Scree Marmot), pebble falls / wind only, stars-quiet | storms loud, thunder echoing off rock (Sound Physics) |
| **Snowfield** | snowy plains, ice spikes, grove, snowy slopes; **Frost Spires** | thin cold air, near silent | snow hiss in gusts | snow bunting, a branch dropping snow, ice creaks / ice settling, an owl | falling snow filters the AirBed, steps crunch |
| **Coast** | beach, snowy beach, stony shore | sea air | **surf at the real shoreline**, pebbles drawn back on stony shore | gulls, terns, wave slaps / quieter surf, a seal-like call none (we don't invent) | storm surf louder |
| **Open sea** | every ocean (warm, lukewarm, cold, frozen, deep) | wind over water | swell around the boat or swimmer | gulls only near land / nothing | underwater: vanilla underwater loop and additions, our Tidewater creatures |
| **River** | river, frozen river (plus lakes inside any biome, by water emitters) | the family around it | **flowing water at the real current**, a weir where water falls | kingfisher, dragonflies, frogs / frogs, a fish jump | frozen: ice ticks, muffled flow under ice |
| **Sporewild** | mushroom fields | a soft, padded hush | spore puffs, wet pops (synthesised) | our Shroomlet and Sporeback calls | rain on caps, a dull drum |
| **Hollows** | dripstone caves, lush caves, deep dark, sulfur caves, and any biome deep underground | air tone by cavity size and depth (bigger and deeper = lower) | drips (vanilla dripstone, at real dripstone), lush water, sulfur geysers (vanilla) | far rumbles, a stone settling, our Chime Cricket / same | deep dark: near silence, sculk clicks (vanilla) only; the vanilla cave mood stays |
| Nether (5) and the End (5) | vanilla | **vanilla's loops stay** (they are excellent) | lava at real lava (vanilla `lava.ambient`), fire | vanilla additions | the End: vanilla, plus one far void wind |

Day and night are curves, not switches: the dawn chorus starts 20 minutes of game time before sunrise, peaks, and
thins to day song by mid-morning; dusk brings a short second chorus; night brings insects (temperature-scaled) and
owls; the **moon** matters (full moon: more night calls, wolves far off; new moon: darker, sparser).

### 6.3 Places beyond the biomes

| Place | Palette | Sources | AWAKEN's touch |
|---|---|---|---|
| **Weather** | Rain re-voiced by what it falls on: leaves, stone and wood, water, sand and soil, a roof over your head (drumming), glass. After the rain: drips from leaves and eaves for 60-90 s. Thunder: the crack replaced by a version delayed by distance (343 blocks a second) with a rolling tail; close strikes hard and bright, far ones low. Wind textures follow `terrain.Wind` (steady, gusts, storms). | `PlaySoundEvent` swaps vanilla `weather.rain` at the spot vanilla chose; CC0 rain-on-surface recordings; vanilla thunder layered with a synthesised tail | Storms over the Shattered Highlands make the faults hum louder. |
| **Day and night** | Curves per family (above). | | The System's glass never plays at night unless it must. |
| **Water** | Rivers flow at the real current (emitters on flowing water with sky access), lakes lap, waterfalls roar by height, rain on water hisses. Underwater: vanilla's loop, the AirBed closed to a muffle. | CC0 water; vanilla underwater | Glimmerfin shoals ticking under the surface. |
| **Wind at altitude** | Wind rises with height above sea level and with exposure (open sky, the open side of a ridge). Above the cloud layer (y ≥ 192) the world below drops away: the AirBed thins to a high thin air and the land's events stop. Flight and Wings: the Ascent's own speed rush (`ascent.wind`) stays, and the Conductor's wind gives way to it so the two never stack. | Synthesised wind (audiokit is good at wind); existing `amb.lands_wind_high` becomes a Conductor texture | "The gust you see is the gust you hear" (§7). |
| **Caves and depth** | Air tone by cavity size (from the enclosure probe) and depth; drips at real dripstone; far rumbles; the deeper, the fewer events. Vanilla cave mood stays. | Vanilla dripstone, cave mood; synthesised air tones | The Hollows' Chime Crickets answer each other across a cavern. |
| **The Descent** (10 strata, the Endless, the Well) | Each stratum's rule is heard before it is felt: 2 wind gusts are telegraphed by a rising gust from their source 1.5 s ahead; 3 rising water is a roar that grows in the minute before each rise; 4 bones shift under the floor before the dead rise; 5 the stratum is nearly silent so the player hears their own steps (and so do the Listeners); 6 machines hum and clank awake; 7 a tidal swell before gravity flips; 8 stone grinds behind you where you aren't looking; 9 your own footsteps come back a beat late; 10 the Seed's slow heartbeat under everything. Music stays the reserved `awaken:descent/stratum_<n>` context; the Conductor plays the place. | Vanilla (existing `additions` move into the stratum's soundscape file), synthesis | Gameplay you can hear. |
| **Gates** (14 themes) | The room under the music: positional set-piece emitters (braziers, water in the Drowned Archive, gears in the Foundry, bells in the Crypt, market chatter in the Night Market), each theme's existing `additions` moved into its soundscape. **The hush before a Lord**: the room's sounds drain as you near the arena doors. The Abyssal Shrine stays silent. | Vanilla (existing), synthesis | The Lord's arena is the one quiet room in the gate. |
| **The Veil** (spirit projection) | The world through glass: the AirBed's filter closes, birds and insects fade out, a soft choral hum rises, your Body's heartbeat louder as the cord stretches, spirits whisper from where they stand, secrets within Awareness shimmer faintly. | Synthesis (the System's glass family) | Awareness is power, by ear. |
| **The Stillpoint** | Cloud-sea wind, temple chimes stirred by it, one far bell every 40 s; everything slow, long tails, nothing hurried (time feels ×10 slower). | Synthesis | The calmest place in the game. |
| **Lucidium** (dreams) | The dream's bed is the soundscape of the snapshot it was built from, pitched down, with reversed tails; **clarity follows lucidity**: at 0 the AirBed is muffled and smeared, at 50 it opens, at 80 it is crisp and bright. | Our own families, filtered; synthesis | You hear yourself become lucid. |
| **Above** | Precursor ruins: the existing night hum, plus machine ticks by day; sky platforms: high wind, cables singing; ocean labs: underwater muffle, hull creaks, pumps; the **Halo**: station hum, airlock hiss, and outside the windows nothing at all (space has no air). | Existing beds, synthesis | Silence outside the Halo's glass. |
| **The Hearth and interiors** | Any interior (enclosure probe): outdoor beds come through the openings, rain drums on the roof, the fire crackles (vanilla). Hearth rooms: Sanctuary hushed with a soft hearth bed, Trophy Hall reverberant (Sound Physics), Workshop machine hum, Barracks Echoes' breathing. | Vanilla, synthesis | A home sounds like shelter when it rains. |
| **The Living Wilds** | Happenings you hear before you see, with direction: the Great Migration's lowing and hoof-rumble, a Stampede's rising thunder, a Murmuration's wing waves, the Rolling Storm's thunder moving with the cell, the Wild Hunt's horn and baying under a full moon (and everything else falls silent for 200 blocks), the Deep Tide's fog swallowing sound, a Caravan's llama bells and cart creak, a Beast Duel's roars. Camps crackle and mutter; nests chitter. Higher-level regions sound wilder: fewer songbirds, more far calls of hunters. | Our creature voices, vanilla, synthesis; tells come from the existing `living_happenings` payload (no new packet) | Rumours you can follow by ear. |
| **Carillon** | **Not planned here.** B3b owns the city's beds, one-shots and crowd. The Conductor yields inside the city by the weight the city reports, and offers it weather, enclosure and rain re-voicing as services (if B3b built its own rain, the city's wins inside the walls). | | |

### 6.4 Reactions

| Reaction | Rule (first draft; numbers are tuned in batch 3) |
|---|---|
| **Predator hush** | A hostile (same definition as the layered music: AWAKEN monster role or vanilla monster, never Echoes or peaceful fauna) within 24 blocks hushes songbirds and insects within 32 of it: their density falls to 10% over 3 s; it recovers over 20 s after the hostile leaves or dies. Our night hunters (Canopy Prowler, Gloomstalker) hush harder. Client-side: the entities are already in the client level. |
| **The cricket ring** | Insects within 6 blocks of the player fall silent while you move and resume 6-10 s after you stop or leave, so the chorus always sounds a little ahead of and behind you. |
| **Flush** | Sprinting, sliding or landing hard within 8 blocks of leaves or tall grass: a wing flutter (3-5 birds) bursts from the nearest leaves, and that tree is silent for 15 s. In the city the pigeons are B3b's. |
| **After the rain** | When rain stops: drips from leaves and eaves for 60-90 s, thinning; birds return first at the edges. |
| **Thunder by distance** | Delay = distance / 343 blocks per second; far strikes lose the crack and keep the roll. |
| **Wind by height and exposure** | Wind gain = exposure × (0.12 + 0.88 × height factor) × wind strength (the formula `LeafRustle` already uses, moved into the Conductor). |
| **Footsteps** | Surface, wetness and armour layers: Tread Lightly's job (§4). Our Hunter gear gets armour layers through its data. |
| **Powers** | A cast within 16 blocks flushes nearby birds and hushes the area for 10 s. |
| **Combat** | Under layered combat or Lord music, events thin to half and beds dip 4 dB (§8). |

### 6.5 Content sources (the Carillon rule, applied to the world)

1. **Vanilla events first**, referenced by event id in our `sounds.json` (`type: event`), layered with pitch, volume
   and combinations; never copied (the repo is public and holds no Mojang files).
2. **CC0 recordings** only for what vanilla lacks: songbirds by family, woodpecker, owls (where our Hollow Owl doesn't
   fit), gulls and terns, crickets, cicadas, katydids, frogs at night (where vanilla's frog is too cute), surf, rivers
   and streams, waterfalls, rain on leaves, stone, roof, water and glass, drips. **Freesound, CC0 filter only**,
   uploaders with a long record; each file listed in `docs/sound/SOURCES.md` with its URL, author, licence and the
   date we checked, **downloaded only after Euan's yes**, then cut, cleaned, mastered (mono, 44.1 kHz, loudness
   category) and shipped under `assets/awaken/sounds/world/`. Shortlist first: about 70 files (§11 Phase 0).
   Not used: BBC Sound Effects (non-commercial licence), Sonniss bundles (no redistribution of raw files, and our repo
   is public), any CC-BY or CC-NC file, any other mod's sounds.
3. **Synthesis** (`tools/art/audiokit.py`, specs `tools/art/specs/audio_world*.py`) for wind, air tones, cave tones,
   ice and glass, every magic sound, the Veil, the Stillpoint, Lucidium, the Halo, and the Descent's rule tells.

### 6.6 Data and API (for builders)

- **Data:** `assets/<ns>/soundscape/<id>.json` (schemaVersion 1; codec `SoundscapeDef`): `air` (bed layers: file,
  length, gain curves over time of day, weather and moon), `textures` (sound, anchor block tags, range, gain rules),
  `events` (sound event, anchor `#tags` or `far`, time windows, density per minute, call-and-answer, hushable,
  altitude band), `variant` magic (Awakened only), `silence_floor`, `lulls`. Families for the overworld; one file per
  dimension or place for the rest (gate themes, strata, the Veil, the Stillpoint, Lucidium, the Halo, interiors).
  Any module adds its own file; nobody edits another module's.
- **Biome mapping:** tags `#awaken:soundscape/<family>`, generated by `tools/terrain/biomes.py`; biome tags are
  synced to clients.
- **API** (SPEC §12, new, client): `dev.awaken.core.audio.client.Soundscape`:
  `hush(Object owner, Vec3 at, float radius, float depth, int ticks)`, `register(Source)` (a module's positional
  emitters, counted in the voice budget), `yieldWeight(ToDoubleFunction<Vec3>)` (the city reports how much of the
  world bed to give way), `level()` (the Conductor's state for debug and tests). `core/audio/client/Ambience`, the
  gate hum, the Above and Veil hums become `Source`s, so one budget covers every ambient voice.
- **Folded in, so nothing is duplicated:** `terrain/client/LeafRustle` becomes the Conductor's canopy and high-wind
  textures (the setting `leaf_rustle` keeps working); the four Awakened biomes' `ambient_sounds` loops move into
  their soundscape variants (the attribute keeps only `mood`, so nothing doubles); gate and strata `additions` move
  into their soundscape files. The vanilla cave mood and the Nether's loops stay as they are.
- **Server:** nothing new for the overworld. Gates' and happenings' positions already reach the client; a gate's
  break clock, if not already synced, rides the existing gate sync.
- **Debug:** `/awaken audio world` (anyone): family weights, enclosure, exposure, voices in use, hushes and why;
  autopilot `tools/autopilot/world-sound.txt` for the Mac (frame time, voices, a storm, a cave, a gate, the Veil).

## 7. AWAKEN's own magic

What makes this ours, not a nature pack:

1. **The world holds its breath.** Within 48-96 blocks of an open gate (by rank) birdsong and insects thin out and an
   inward breath of wind leans toward it; the gate's hum takes over. As a Break's clock runs down, the hush spreads
   wider, the wind pulls harder, and in the last minute only the hum is left.
2. **The gust you see is the gust you hear.** `terrain.Wind` is deterministic and shared by every client: gust fronts
   roll downwind at 7 blocks a second. The Conductor plays each gust from the upwind trees first, then over you, then
   away, in step with the waving leaves and falling leaves. Two players hear the same gust.
3. **The Awakened lands sing in the System's key.** Each Awakened biome carries a faint harmony in D major pentatonic,
   the System's own scale, so the land and the System sound related: Glass Steppe's glass rings as the sun warms it;
   Shattered Highlands' faults beat slowly (louder in storms); Frost Spires' aurora hums at night; Lumen Forest's
   specks chime when you step near them; Elderwood's colossi groan in big gusts; Mirror Marsh **answers once**: a
   sound you make returns a moment later from across the water, as if from your reflection.
4. **Awareness is power, by ear.** PER widens what you hear: a lair's breathing from its direction, a hidden cache's
   faint shimmer, a spirit's whisper in the Veil. A perceptive hunter finds things by ear first.
5. **Clarity is lucidity.** In Lucidium the world's sound sharpens as you become lucid.
6. **Rumours you can follow by ear.** Living Wilds happenings are heard from far off, with direction.

## 8. How it fits `core/audio`, the music and the city

- The Conductor is ambience: it plays on the Ambient/Environment slider and **never touches the music channel**.
- It is not a `MusicLayers` context (contexts are equal-length stems at four levels for music and the city's crowd);
  it sits beside the ambience slot. Where the ambience slot names a context (the city's district beds), the city's
  provider wins and the Conductor yields by the city's weight.
- **Ducking:** layered music at combat or Lord: beds −4 dB, events thinned to half; a stage cue: beds −3 dB for its
  length; vanilla's calm songs and our composed tracks: no ducking (Minecraft's piano over nature is the classic
  pairing). Nothing is ever muted by music, only thinned.
- **The System stays dry and on top.** The magic harmony uses the System's scale but never its glass timbre, so a
  System sound is never mistaken for the land.
- **Sound Physics:** positional textures and events are processed (room, occlusion: indoor/outdoor comes for free);
  the AirBed is non-positional and filtered by us; the System, UI and music stay dry as today.

## 9. The quality gate: Euan's listening pages

Built with the existing `audio_listen.py` pattern (keep / remake / cut, notes, JSON export, phone width), plus a new
offline renderer, `tools/art/soundscape_render.py`, that plays the same soundscape files and the same scheduler rules
(seeded) as the game, so the page is what the game does. A parity test keeps the Python and Java schedulers in step.
Each batch is **ten minutes or less**, and an unmarked entry counts as keep after Euan has opened the page (decision
6). Recordings of other mods (the AmbientSounds A/B) are made on the Mac and never committed.

| Batch | Phase | Contents | Length |
|---|---|---|---|
| 1 | P1 | "A day in the forest": Greenwood at dawn, noon, dusk, night, rain, after rain; vanilla before and ours after; "spot the seam" (5 minutes in one spot); the AmbientSounds A/B (local only) | 9 min |
| 2 | P2 | "Every land": each family at its best hour (30 s each) and the Awakened variants | 10 min |
| 3 | P3 | "The world reacts": predator hush, cricket ring, flush, rain on leaves, stone, water and roof, thunder near and far, a gust passing, a climb to the peak; each before and after | 8 min |
| 4 | P4 | "Below and beyond": caves by depth, the ten strata's tells, the Veil, the Stillpoint, Lucidium at three lucidities, the Halo, a gate room and its Lord's hush, a home in the rain | 10 min |
| 5 | P5 | "The magic": a gate's hush and Break, the Awakened songs, the hunter's ear, happenings heard from afar; then one play session in game | 8 min |

The CC0 shortlist is its own page (Phase 0): each candidate's Freesound link, author, licence, length and why; Euan
ticks yes or no before anything is downloaded.

## 10. Budgets

Engine numbers below are **verified in 26.3's decompiled source** (2026-10-25, `com.mojang.blaze3d.audio.Library`:
streaming = clamp(√sources, 2, 8), static = clamp(sources − streaming, 8, 255), so 248 static and 8 streaming on
OpenAL Soft's 256; `SoundBufferLibrary` caches every decoded static sound until `clear()` on a resource reload).

| Budget | Limit | Why |
|---|---|---|
| Streaming channels | **1** for the whole Conductor (the AirBed mixes in Java) | The streaming pool is ~8 and already carries vanilla music, our layered stems, the ambience slot and jukeboxes. |
| Static voices | **≤ 16** ambient at once (textures 6, events 10), plus registered Sources inside the same cap; quietest and farthest stolen first | Leaves 220+ of the static pool to gameplay. |
| New one-shots | ≤ 4 a second | Caps Sound Physics' ray tracing (it traces each sound when it starts). |
| Client main thread | ≤ 0.10 ms a tick average, ≤ 0.4 ms worst; listener surveys staggered (biome ring every 20 ticks, enclosure every 10, emitter anchors spread over 40); **no allocation per tick** (pooled tickable instances, reused buffers) | SPEC header and §16.10 frame budgets. |
| Audio thread | AirBed decode, mix and filter ≤ 1.5% of one core (two Vorbis streams plus a one-pole filter) | Measured the same way as `StemMixer`. |
| Decoded memory | ≤ **48 MB** of static world ambience (one-shots and texture loops; native OpenAL buffers, cached until reload); beds stream and cost ~1 MB of buffers | Euan's M4 runs an 8 GB heap plus Lens; native audio memory is outside the heap but still counts. |
| Disk | ≤ **60 MB** of new Ogg (beds Vorbis q2 mono or stereo; one-shots q4 mono) | Today 52 MB; the co-prime layering keeps beds short. |
| Loudness | beds and textures −28 ± 3 dBFS RMS, ≤ −6 dBTP (the existing ambience rule); events −26 ± 3; magic layers −32 ± 3 | `audio_mix.py` gains a `world` category. |
| Perf proof | the Mac tour: forest at dawn in a storm, the city edge, a cave, a gate: p50 within 2% of the Conductor off | One tour per phase gate. |

## 11. Phases and gates

Scaled from the git history: all of AWAKEN took about 30 hours of wall clock with several builders; Stage 4's
adaptive music (14 themes × 4 stems, the mixer, the intensity machine, tests and the listening sheet) took one
builder about four to five hours. Estimates are builder-hours.

| Phase | What | Builder-hours | Gate |
|---|---|---|---|
| **P0 Spikes and shortlist** | On the Mac, 1 h (with Euan's go-ahead to launch the game): AmbientSounds alone (how it sounds in a forest, a cave, a storm; a local recording for batch 1's A/B), Tread Lightly and Immersive Thunder Reforged beside Sound Physics (crash, logs, a cave and a hall, our blocks), the 26.3 engine numbers from `.ref`. In the cloud: the CC0 shortlist page (~70 candidates, links only). | 3 | Spike report; Euan's ticks on the shortlist; decisions 2-3 confirmed |
| **P1 Engine and the first forest** | Listener, Conductor, AirBed (shared decoder with `StemMixer`), textures, events, voice budget, the soundscape codec, biome tags and census, `/awaken audio world`, LeafRustle folded in; Greenwood complete with day, night and rain; offline renderer; tests | 7 | **Batch 1** + first perf tour |
| **P2 Every land** | The other 15 families, Elderwood's and Lumen Forest's missing beds, the four Awakened beds moved in, weather re-voicing, thunder, wind by height and exposure, the climate fallback | 7 | **Batch 2** |
| **P3 The world reacts** | Predator hush, cricket ring, flush, after-rain, powers, combat ducking; Tread Lightly data for our blocks and Hunter gear (or our fallback steps) | 4 | **Batch 3** |
| **P4 Below and beyond** | Caves by depth, the ten strata and the Endless, gate rooms and the Lord's hush, the Veil, the Stillpoint, Lucidium clarity, Above and the Halo, the Hearth and interiors | 5 | **Batch 4** + perf tour |
| **P5 The magic and the seams** | Gate hush and Break, the Awakened songs, the hunter's ear, Living Wilds tells and regions, the city seam with Carillon, the final mix pass | 2 | **Batch 5** + Euan plays |
| | **Total** | **~28** | |

Two builders can run in parallel from P1 (one on the engine and reactions, one on content and synthesis). Model
split per the hand-off rules: Opus at extra high effort for the Conductor, the mix and the magic (taste); Sonnet for
data, the biome table column, tests and docs. One test per mechanism; the full GameTest suite on CI.

**Tests** (GameTests, headless, like the existing audio tests): `audio_world_census` (every overworld biome in
exactly one family; every soundscape file valid; every event exists; every recorded file listed in SOURCES.md with a
CC0 licence), `audio_world_loops` (bed layers loop seamlessly; layer lengths repeat no sooner than 30 minutes
together), `audio_world_loudness`, `audio_world_scheduler` (densities, lulls, call-and-answer, the voice cap never
exceeded on a stress seed; pure Java, no client), `audio_world_schedule_parity` (Python and Java schedules match on
golden seeds).

## 12. Decisions for Euan

1. **Approach C, the hybrid**: our own Conductor for biomes, places, reactions and magic; mods for space and
   footsteps. *Recommended.* (A would be faster but generic and blind to AWAKEN; B rebuilds footsteps for nothing.)
2. **Footsteps: Tread Lightly** in the `sound` profile, if the Phase 0 spike passes; otherwise our own re-voiced
   steps in P3. *Recommended.*
3. *(Superseded: AmbientSounds is in the sound profile and plays the nature.)* **AmbientSounds: the benchmark, not shipped.** We A/B against it in batch 1 and adopt it for vanilla nature beds
   only if it wins on your ears (§5). Our jar also ships inert data for it that mutes our instanced dimensions, so a
   player who adds it never hears birds in a gate. *Recommended.*
4. **No Raise Sound Limit Simplified.** Its asynchronous playback breaks assumptions our music player relies on, it has
   open "music missing" bugs, and our budget fits the vanilla engine. *Recommended: leave it out.*
5. *(Superseded: no recordings.)* **CC0 recordings from Freesound** for birds, insects, water and rain on surfaces: about 70 files from a shortlist
   page you tick, nothing downloaded before your yes. Downloads of originals need a free Freesound account's API key
   (yours, or a project account). *Recommended.*
6. **Listening: five batches of ten minutes or less**, unmarked = keep once you've opened the page. (The gate music
   sheet from 2026-10-24 is still unmarked; this plan doesn't wait on it.) *Recommended.*
7. **The magic**: the hush around gates and Breaks, the Awakened lands in the System's key, the hunter's ear (PER),
   Mirror Marsh's answer, lucid clarity. *Recommended: all six touches in §7.*

Not recommended, for the record: **Sounds** (IMB11; can't load on 26.3, all rights reserved, and it re-voices the
UI, which is the System's), **Dynamic Surroundings** (no build past 1.21.1; a 26.2 port is in progress),
**Presence Footsteps** (Fabric only, PolyForm Shield licence).

## 13. SPEC lines to add on approval

- Plan v2 · world sound (new block under §13, "details in docs/sound/PLAN.md"): the Conductor's four layers; one
  stream; the voice, memory, disk and CPU budgets of §10; the soundscape data format and biome tags; the census rule;
  the reactions' first numbers; the ducking rules; the city yield.
- §12: `dev.awaken.core.audio.client.Soundscape` (client API, §6.6).
- §14: the `sound` profile gains Tread Lightly (and Immersive Thunder Reforged) if the spike passes; AmbientSounds
  and RSLS stay out; `compat` ships inert AmbientSounds dimension data that mutes our instanced dimensions.
- §15: the biome table's `soundscape` column; Awakened biomes' `ambient_sounds` keep only `mood`.

## 14. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Euan's listening time (the gate sheet is still unmarked) | High | Batches ≤ 10 min, phone-friendly, unmarked = keep; builders keep building between batches on the next phase |
| Synthesised nature sounds fake | Medium | Nature is recorded (CC0); synthesis only for wind, tones and magic, where it is strong |
| A CC0 file is mislabelled on Freesound | Low | Long-standing uploaders only; licence and page snapshot date in SOURCES.md; anything doubtful is cut |
| Too busy, then annoying (birds every second) | Medium | Density curves, lulls, silence floors; batch 1's ten-minute "spot the seam" |
| Channel or memory exhaustion | Low | One stream, 16-voice cap with stealing, 48 MB decoded cap checked by a test of the file list |
| Sound Physics cost with many positional sounds | Low | ≤ 4 new one-shots a second; textures are few and long-lived (re-anchored every ~12 s); footsteps capped by Tread Lightly's 50 entities |
| Tread Lightly is young (first 26.3 build 2026-09-23, ~1.5k downloads) | Medium | Spike first; our fallback is planned and costed |
| Double ambience (vanilla biome loops, or a player adding AmbientSounds) | Medium | Our biomes' attribute loops move into the Conductor; inert AmbientSounds data mutes our instanced dimensions; with AmbientSounds loaded the Conductor drops its nature events (keeps magic and places) |
| The city seam (B3b is being built now) | Medium | The city owns its walls; the Conductor yields by the city's weight; a short contract note goes to the Carillon session before P5 |
| 26.3 API drift | Medium | Every signature checked in `docs/api/` and `.ref/` first (CLAUDE.md) |
| Footstep and surface data for our own blocks drifts as blocks are added | Low | A census test over our block tags once Tread Lightly is in |
