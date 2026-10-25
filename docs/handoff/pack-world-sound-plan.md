# pack/world-sound-plan: hand-off (cloud session, 2026-10-25)

Euan's brief: immersive sound "in every single biome and everything", using existing mods rather than reinventing,
so that it feels like being inside the world. This session's job was a plan for his yes, not code.

## Delivered (branch `pack/world-sound-plan`, from `build/awaken-v1` at 5d5a38a1)

- `docs/sound/PLAN.md`: the plan (audit, mod research, three approaches, the design, every place, magic, budgets,
  listening batches, phases, risks, decisions, SPEC lines to add).
- `docs/sound/plan.html`: the approval page (inline CSS and SVG, light and dark, phone width; fonts from Google Fonts
  with system fallbacks, like the other plan pages; checked headless at 1280 and 400 px, no overflow or errors). Not
  published from here: the orchestrator publishes it for Euan.
- This file.

No production code, no SPEC change, no new mods in `pack/mods.json`. Nothing downloaded into the repo.

## The recommendation in short

Approach **C, the hybrid**: a world ambience layer of our own in `core/audio` (the **Conductor**: one streamed air
bed mixed and filtered in Java, ≤ 6 positional textures, ≤ 10 positional events, reactions and re-voicing through
`PlaySoundEvent`), fed by soundscape data per family (16 families cover all 56 vanilla overworld biomes and the six
Awakened ones through biome tags from `tools/terrain/biomes.py`) and per place (gates, strata, the Veil, the
Stillpoint, Lucidium, Above and the Halo, interiors). Mods for what they do best: Sound Physics Remastered (kept),
Tread Lightly for footsteps (after a one-hour spike on the Mac). AmbientSounds (26.3 NeoForge build exists, LGPL,
takes data from our jar) is the benchmark in the first listening batch, not shipped: it plays everything head-relative
(Sound Physics skips it) and streams every sound. Raise Sound Limit Simplified is out: its asynchronous playback
breaks what `CuePlayer` and `LayerPlayer` assume, and our budget fits the vanilla pool. Sounds (IMB11) can't load on
26.3 (its YACL dependency declares `[26.2,26.3)`). Content:
vanilla first, CC0 from Freesound for birds, insects, water and rain on surfaces (shortlist page, Euan ticks each),
synthesis for wind, tones and every magic sound. About 28 builder-hours in five phases, each gated by a listening
batch of ten minutes or less.

## Seven decisions waiting on Euan (PLAN.md §12)

1. Approach C. 2. Tread Lightly for footsteps (spike first). 3. AmbientSounds as benchmark only, plus inert data in
our jar that mutes our dimensions for players who add it. 4. No RSLS. 5. CC0 recordings from Freesound (needs an API
key for originals). 6. Five short listening batches, unmarked = keep. 7. The six magic touches. All recommended.

## What was checked, and how

- The code: `core/audio` (Ambience, LayerPlayer, StemMixer, MusicLayers), every module's `sounds.json` under
  `src/main/merge/`, the Awakened and gate/strata biome JSON (`minecraft:audio/ambient_sounds`), `terrain/client/LeafRustle`,
  `pack/mods.json`, `pack/lock.json`, `pack/spr/sound_rates.properties`, `docs/modules/audio.md`, `lands.md`,
  `docs/handoff/pack-sound.md`, SPEC §8-§10, §12, §13 (audio), §14, §15, §16.0 decision 28, §21.6.
- Vanilla 26.3's sound events: Mojang's version manifest → 26.3 → asset index 34 → `minecraft/sounds.json`
  (1,991 events), fetched 2026-10-25. Confirms: no songbirds, insects (but bees), wind, surf, river or surface rain.
- Mods: the Modrinth API (project and version lists filtered to NeoForge + 26.3, licences, dependencies), the mods'
  GitHub repos and pages; details and dates in PLAN.md §4.

## Not verified (do these first in Phase 0)

- 26.3's sound engine numbers (static and streaming pool sizes in `com.mojang.blaze3d.audio.Library`, the decoded
  buffer cache in `SoundBufferLibrary`): `.ref/` is local to the Mac. PLAN.md §10 states the 1.21 values as
  assumptions.
- How AmbientSounds, Tread Lightly and Immersive Thunder Reforged behave next to Sound Physics Remastered in our
  pack: the one-hour spike on the Mac (needs the game client, so Euan's go-ahead first).
- Sound Physics' rules the design leans on (relative sounds, rain, thunder and `<ns>:ambient.*` skipped by default; a
  positioned loop evaluated once at its start) come from reading its source (commit 87abb11, 2026-10-21), not a run.
- Nobody has heard anything yet; the plan's sound descriptions are design intent.

## Next, after Euan's yes

1. SPEC: add PLAN.md §13's lines in the same commit as the first code.
2. Phase 0 (PLAN.md §11): the Mac spike (1 h) and the CC0 shortlist page.
3. Phase 1: the Conductor and the Greenwood family, then listening batch 1.
4. Tell the Carillon session (B3b) about the yield contract before Phase 5: the city owns its walls; the Conductor
   asks `Soundscape.yieldWeight` and offers weather, enclosure and rain re-voicing.

## After Euan's yes (2026-10-25, same session): "Go ahead with all recommendations"

All seven decisions approved. Built on `pack/world-sound-plan`:

- **SPEC** (ad1a7f89): §13 "Plan v2 · world sound", §12 `Soundscape`, §14 the sound profile, §15 the biome table
  column. PLAN.md marked approved.
- **Phase 0, cloud part** (4fe90534, e1b7910e):
  - the pack's `soundspike` profile (Tread Lightly 1.1.0+26.3, Immersive Thunder Reforged 1.3.8+26.3, AmbientSounds
    6.3.11 + CreativeCore 2.14.24), appended to the lock without re-resolving any other mod; never installed by
    `tools/play.sh`;
  - the Mac spike tour `tools/autopilot/sound-spike.txt` (pass criteria in its header);
  - inert AmbientSounds data `assets/ambientsounds/basic/dimensions/awaken_dimensions.json` muting `awaken:` dimensions;
  - the CC0 shortlist: 90 Freesound candidates in 11 groups (`docs/sound/cc0-shortlist.json`, licence read on each
    page; page `docs/sound/cc0-shortlist.html` by `tools/art/sound_shortlist.py`); `docs/sound/SOURCES.md` empty until
    the downloads. Freesound is blocked for curl in the cloud environment (proxy 403); web fetch reads it, and the
    downloads need Euan's Freesound API key or his Mac.
- **Phase 1, the engine** (473f8207): the Conductor as planned (details in `docs/modules/audio.md` "World sound"):
  data format and day curves, the event clock, the 24-column listener, the air stream with its filter, positional
  textures re-anchored for Sound Physics, events on real spots, the 16-voice budget, hushes, ducking, the city yield,
  `/awaken audio world [off|on]`. `terrain/client/LeafRustle` is gone: its two textures play from `awaken:world`.
  `biomes.py` has the SOUNDSCAPE column (56 vanilla rows + 6 Awakened; Frost Spires moves to snowfield). Greenwood
  has its three synthesised air layers (37/41/53 s), owls answering at night, a far fox and lulls. 26.3's engine
  numbers are now checked in its decompiled source (248 static, 8 streaming; decoded sounds cached until reload).
  GameTests `audio_world_census` and `audio_world_clock` pass locally (with every `audio_*`, `terrain_biome_table*`
  and `compat_spr*` test).

## Waiting on Euan

1. **Mark the shortlist** (published as an artifact, or open `docs/sound/cc0-shortlist.html`), Export, hand back the
   JSON. Then: a Freesound API key (or the downloads done on the Mac), so the yeses can be fetched, cut and mastered.
2. **The Mac runs** (game client, so his go-ahead): `PACK=sound,soundspike tools/autopilot.sh
   tools/autopilot/sound-spike.txt` (decisions 2-3) and `PACK=sound tools/autopilot.sh tools/autopilot/world-sound.txt`
   (Phase 1: the Conductor in a forest through a day, rain, a roof, underwater, a gate, and its frame-time A/B).

## Next for a builder

- Greenwood's birds and insects from the yeses, then **listening batch 1** (`tools/art/soundscape_render.py`, the
  offline renderer with a schedule-parity test, is still to write).
- Phase 2: the other 15 families, the four Awakened beds moved out of their biomes' `ambient_sounds` (keep `mood`),
  rain re-voiced by surface, thunder by distance (or Immersive Thunder Reforged if the spike passes), the climate
  fallback for other mods' biomes.
- Known: `tools/terrain/biomes.py check` reports one stale file that predates this work and isn't this module's
  (`datapacks/awakened_lands/.../has_structure/precursor_tether_spire.json`, the Ascent's); left as it was.
- The vanilla tags `biomes.py` reads come from the decompiled sources in the cloud: point `AWAKEN_REF` at a folder
  with `assets/data` (the Minecraft sources jar's `data`) and `neo` (NeoForge's sources jar).

## Revised (Euan, same day): AmbientSounds plays the nature, we build only what's ours

*"Let's use AmbientSounds, add it to the sound profile ... we also build the hush and the hums and all of our own
custom stuff, and then when that plays, maybe the ambient sounds play a bit less ... rebuilding from scratch something
that already exists very well is not smart."* No field recordings (they'd sound out of place with Minecraft).

Done:
- **Pack**: AmbientSounds 6.3.11 + CreativeCore 2.14.24 moved into the `sound` profile (lock entries re-profiled only);
  `soundspike` keeps Tread Lightly and Immersive Thunder Reforged. `build.gradle` compiles against both as
  `compileOnly` (Modrinth version ids from the lock).
- **The nature level** `Soundscape.natureLevel()` and `compat.client.AmbientSoundsBridge`: AmbientSounds' master volume
  = the player's own × the level; the player's value comes back on shutdown.
- **The gate hush** (`gates.client.GateHum` → `Soundscape.hush`): the world holds its breath near a gate, by rank and
  state (numbers in SPEC §13 "Plan v2 · world sound").
- With AmbientSounds installed, the Conductor's own nature (world rustle and wind, Greenwood) is off; it stays as the
  fallback without it.
- AmbientSounds data in our jar: our dimensions muted; our Awakened ground and plants added to its block groups.
- The CC0 shortlist, its page, its tool and SOURCES.md are removed (git history has them). The published shortlist
  artifact is stale: delete it if Euan wants.
- Test `audio_world_ambientsounds`; `audio_*`, `terrain_biome_table*` and `compat_*` (28) pass locally.

Superseded above: "Waiting on Euan" item 1 (the shortlist) and "Next for a builder" items about birds, insects and
Phase 2's nature families. Still waiting on Euan: the Mac runs (the spike now checks Tread Lightly and the thunder
mod; AmbientSounds plays in every `PACK=sound` run, so listen there, and watch that the gate music never drops out:
AmbientSounds streams its sounds and the engine has 8 streaming channels).

Next for a builder (all ours, PLAN.md §6.3 and §7): the hums and places (the Veil muffle, the Stillpoint, Lucidium's
clarity, the Halo, gate rooms and the Lord's hush, the Descent's tells), the Awakened lands in the System's key,
Living Wilds happenings heard from afar, the hunter's ear; each one also lowers the nature through the Conductor.

