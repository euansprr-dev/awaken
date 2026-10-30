# pack/sound: hand-off (cloud session, 2026-10-24)

Stage 4 of the Awakened Pack, "The Sound": adaptive gate music. Built locally before the plan window ran out
(four phase-aligned stems for each of the 14 gate themes, the server-driven intensity mixer, the Sound Physics
Remastered dry list); this session finished it in the cloud VM.

## Done

- **Merged `origin/build/awaken-v1`** (2e05a76: lands/slice and pack/holo-ui) into the branch; no conflicts.
- **Merged `origin/pack/throughput`** (e1138f3) too, to port its flake fixes (see CI). It is a merge, not
  cherry-picks, so it is a no-op once build/awaken-v1 carries pack/throughput, and the two land in either order.
  pack/sound therefore contains pack/throughput: merge that one first or with this one.
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
- **`audio_layers_loudness` runs in a batch of its own.** It decodes all 56 stems on a worker thread (a minute of CPU
  and hundreds of MB of heap) while its batch runs; after the merge that batch holds build/awaken-v1's
  `terrain_hero_blueprints` (the Elder Colossus's fastest of 8 seeds under 8 ms), which failed twice on CI at
  11.9-15.4 ms against 5.7 ms on the base. Alone, its numbers are back to normal (VM: 23.4 ms avg / 146 worst before,
  4.6 / 6.6 after; CI: 7.6 ms fastest).
- **Build and tests in the VM** (on the final code, 18ecf02): `./gradlew build` green; `runGameTestServer`: **all 330
  required tests passed**, and again on two cores each shared with a busy loop (pack/throughput's CI stand-in).

## Left

- **Euan's listening session**: open the sheet, mark the 14 mixes (and any stem that bothers him), Export, hand the
  JSON back. Remakes go into `tools/art/specs/audio_layers.py`, then `python3 tools/art/audio_build.py layer_<theme>`,
  `audio_mix.py`, and `audio_listen.py 2026-10-24` (or a new date) to refresh the sheet. `--page` rewrites only the
  page.
- Nobody has heard the layers in game yet; the tours below are the proof the logic runs, the sheet is the proof of
  taste.
- Not built (by design, reserved in SPEC): the Descent's and the city's contexts.
- Not this branch's, still open: `hearth_why_not_reasons` flakes on CI now and then (see CI).

## Tours for the Mac (they need its GPU)

- `tools/autopilot/sound.txt`: three gates (Bell Crypt through every level and a Lord's fall; Ember Mines' long calm
  so the bed rests and returns; Frost Monastery's Lord and a death inside). Grep `[audio] layers`. New with this
  session's fix: after each `gate hop` the first `[audio] layers music` phase line should be `playing: start at ...`,
  never `fading_vanilla: a song is playing: it fades out first` (the teleport has already silenced any song).
- `PACK=sound tools/autopilot.sh tools/autopilot/sound-spr.txt`: Sound Physics Remastered's own logs; the System, UI,
  music and the Ritual's voices dry, everything else in the room.

## CI

- **Green: [40104662443](https://github.com/euansprr-dev/awaken/actions/runs/40104662443) on 18ecf02 (the final
  code), attempt 2: all 330 required tests passed.** Attempt 1 failed only on `hearth_why_not_reasons` (an opening
  at y 126 taken for a roof hole): the flake pack/throughput's hand-off lists under Left, cause not established,
  seen on lands/slice too, in no code this branch touches; the Elder Colossus measured 4.45 ms there.
- Before it, for the record:
  - [40100497841](https://github.com/euansprr-dev/awaken/actions/runs/40100497841) (743102f), two attempts:
    `terrain_hero_blueprints` both times, this branch's (above), plus the known `gates_surface_respects_builds` and
    `fauna_p3_caves_spawn`.
  - [40102394688](https://github.com/euansprr-dev/awaken/actions/runs/40102394688) (4eb5188, the hero test fixed: 7.63 ms),
    two attempts, a different known flake each: `anomaly_grove_bends_the_rules`, then `fauna_p3_caves_spawn`.
    build/awaken-v1 is red the same way (its last three runs; last green 6e6f44e). pack/throughput fixes these at
    their cause, hence the merge.
- The VM can't fetch Actions logs (the network policy blocks GitHub's log storage); the GitHub connector's job-log
  tool reads them.
- For terrain (not touched here): `terrain_hero_blueprints` measures wall time, 5.7-7.6 ms on an undisturbed CI
  runner against its 8 ms budget, and 6.4 ms on two contended cores. pack/throughput's note stands: if it flakes,
  time it with thread CPU time.
