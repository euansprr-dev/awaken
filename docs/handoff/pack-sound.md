# pack/sound: hand-off (cloud session, 2026-10-24)

Stage 4 of the Awakened Pack, "The Sound": adaptive gate music. Built locally before the plan window ran out
(four phase-aligned stems for each of the 14 gate themes, the server-driven intensity mixer, the Sound Physics
Remastered dry list); this session finished it in the cloud VM.

## Done

- **Merged `origin/build/awaken-v1`** (2e05a76: lands/slice and pack/holo-ui) into the branch; no conflicts.
- **The WIP commit (393a28b) is finished and built.** It meant to stop the layers waiting on a vanilla song that a
  teleport had already silenced, but it cached a flag from `SelectMusicEvent`, and 26.3's `MusicManager.tick` returns
  before firing that event while it fades its gain, which is exactly when the stale song lingers, so the flag stayed
  `true`. `CuePlayer` now keeps the instance the event last named and asks the sound engine live (a song started
  after it, under another key, counts as sounding). The layers skip the 1.5 s vanilla fade for a dead song, and an
  ordinary cue no longer waits on one (and misses its window) after a teleport. Checked against the decompiled 26.3
  `MusicManager`, `SoundEngine.isActive` and `Minecraft.tick` (music ticks before `ClientTickEvent.Post`).
- **Listening sheet** `docs/audio/listening-2026-10-24.html`, its 70 MP3s next to it in `docs/audio/listening-2026-10-24/`
  (27 MB; unchanged, the stems haven't changed since they were rendered). Nothing published; open it from a checkout
  (file://, any browser, Safari included).
  - 14 entries **mixed**: a 70 s journey per theme through calm, a hunt, a fight, the Lord, its fall and calm again,
    mixed with the game's own gains and fades. Each now carries **its own line on intent** (the theme's aim, kept
    with the theme in `tools/art/specs/audio_layers.py`), not one generic sentence.
  - 56 entries **alone**: every new sound in the stage (each stem, 20 s), each under its stem's intent and fade times.
  - **keep / remake / cut** and a note on every entry. A stem left unmarked takes its mix's verdict (shown dashed,
    exported `"from": "mix"`), so 14 marks decide all 70 within the 15 minutes; the mixes alone take about 16 minutes,
    and the strip under each mix shows the level playing and jumps to one on a click, so a theme can be cut short.
  - Export as JSON or text. Choices live in the browser's localStorage under `awaken-listening-2026-10-24`.
  - Checked in headless Chromium (light, dark, phone width): marks, inheritance, the seek to the Lord at 0:38, export;
    no console errors.
- **SPEC** (Plan v2 · audio): thresholds, holds, gains, per-stem fades and the intensity signals were already there;
  the contexts bullet is now the contract: the manifest (schemaVersion 1, `source` = the slider, 1-8 equal-length
  stems, gains for levels 0-3), what each slot does, how providers win and what they may set (pressure, a pinned level,
  an arrival). Ids: `awaken:gate/<theme>` in use; **reserved** `awaken:descent/stratum_<n>` (music) and
  **`awaken:city/<district>`** (ambience slot, `source` ambient, its four levels the district's crowd, pinned by the
  city's provider from its density, answering only inside the city site). §12 names the `Signals` a provider may set.
  `docs/modules/audio.md` matches.
- **Build and tests in the VM**: `./gradlew build` green; `runGameTestServer`: **all 330 required tests passed**
  (the 324 of before, plus the merge's).
- **CI**: see "CI" below.

## Left

- **Euan's listening session**: open the sheet, mark the 14 mixes (and any stem that bothers him), Export, hand the
  JSON back. Remakes go into `tools/art/specs/audio_layers.py`, then `python3 tools/art/audio_build.py layer_<theme>`,
  `audio_mix.py`, and `audio_listen.py 2026-10-24` (or a new date) to refresh the sheet. `--page` rewrites only the
  page.
- Nobody has heard the layers in game yet; the tours below are the proof the logic runs, the sheet is the proof of
  taste.
- Not built (by design, reserved in SPEC): the Descent's and the city's contexts.

## Tours for the Mac (they need its GPU)

- `tools/autopilot/sound.txt`: three gates (Bell Crypt through every level and a Lord's fall; Ember Mines' long calm
  so the bed rests and returns; Frost Monastery's Lord and a death inside). Grep `[audio] layers`. New with this
  session's fix: after each `gate hop` the first `[audio] layers music` phase line should be `playing: start at ...`,
  never `fading_vanilla: a song is playing: it fades out first` (the teleport has already silenced any song).
- `PACK=sound tools/autopilot.sh tools/autopilot/sound-spr.txt`: Sound Physics Remastered's own logs; the System, UI,
  music and the Ritual's voices dry, everything else in the room.

## CI

GameTests workflow on `pack/sound`: run id and result are filled in below once the run finishes.
