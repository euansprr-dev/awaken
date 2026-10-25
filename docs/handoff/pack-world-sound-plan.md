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
`PlaySoundEvent`), fed by soundscape data per family (16 families cover all 57 vanilla overworld biomes and the six
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
