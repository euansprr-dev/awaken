# Audio: music cues, gate and Descent tracks, the System's UI family, ambience beds

Plan v2 P4 ("music respecting vanilla's long silences"). Everything here is synthesised from scratch (no samples) by
`tools/art/audiokit.py` and the specs `tools/art/specs/audio_{music,ui,amb}.py`, mastered per category, encoded to Ogg
Vorbis in `src/main/resources/assets/awaken/sounds/` (`music_*`, `ui_*`, `amb_*`). Code: `dev.awaken.core.audio`
(common) and `dev.awaken.core.audio.client`. Sound events and subtitles: `src/main/merge/audio/`; the five System events
it re-voices stay in `src/main/merge/core/`.

## The sound of AWAKEN

Solo Leveling's tension, held back, next to Minecraft's calm piano and long silences.

- **Glass is the System.** Every System sound and the System's lines in the music are one voice: a struck glass (a few
  split, slightly inharmonic modes and a short bright tick) with a warm sine core and a faint FM glint, in one small
  bright room. UI notes all come from D major pentatonic, so two System sounds never clash; only the warning leaves it.
- **The Signal's sigh** is the one motif: tonic, fifth, the minor sixth falling back to the fifth (D A Bb A). It answers
  upward (D A C D) when you win something, turns major (D A B A) when you rise, inverts when a gate breaks, and turns up
  in most gate tracks in that theme's mode (C G A G in the Clockwork Foundry, F# C# D C# in the Precursor Lab).
- **Felt piano and soft strings** are the player; **a distant choir** only when something is bigger than you;
  **low drums** for danger. Reverbs are synthesised rooms (room, hall, cathedral, cave, air).
- **Silence is the default.** Cues are songs as far as vanilla is concerned (they take a song's place, and vanilla's
  10-20 minute silence follows); tracks sit in the biomes' music attribute with vanilla-length delays.

## Music cues (`Cue`, `AudioCues`, client `CuePlayer`)

| Cue | When | Length | Cooldown | Kind |
|---|---|---|---|---|
| `awakening` "Arise" | the player awakens (progression's `onAwakened`), 2 s after arriving back | 34 s | once | stage |
| `rank_up` "Reassessed" | a Reassessment passed (`RankHooks.onRankUp`), 2 s after the rank-up chime | 28 s | 5 min | stage |
| `gate_broke` "The Break" | a gate breaks open within 640 blocks | 23 s | 10 min | stage |
| `beast` "Something Holds This Ground" | first sighting of a named beast | 19 s | 15 min | ordinary |
| `gate_forming` "A Gate Forms" | a gate (Director's or wandering) finishes forming within 640 blocks | 19 s | 20 min | ordinary |
| `shrine` "Rest" | a Rest Shrine reached (floor cleared, first time) | 24 s | 8 min | ordinary |
| `lair` "Den" | a named beast's lair found | 19 s | 15 min | ordinary |

Rules (server, per player, in memory): a cue plays only for an awakened player (the awakening excepted), its own
cooldown holds, and between any two cues there are at least 3 minutes (stage cues ignore that gap). The server sends
`audio_cue`; the client (`CuePlayer`) then waits for its delay, for the level to have been loaded 2 s (a teleport stops
every sound), and for the music slot: an ordinary cue waits up to 10-15 s for a song to end and otherwise lets the
moment pass; a stage cue stops the song. It plays through `MusicManager.startPlaying`, so: the Music slider applies, the
Now Playing toast names it ("AWAKEN - Arise"), the place's music volume applies (nothing in the Abyssal Shrine), and
afterwards the next song waits its full delay. While a cue plays somewhere with no situational music, `SelectMusicEvent`
keeps it from being cut.

Named-beast sightings and lairs are noticed without touching the fauna module: `AudioWatch` looks at the player's
`awaken:discoveries` counter once a second and, when it moves, at the new `awaken:discovered/creature/<beast>` and
`.../lair/<beast>` keys (what was known before the session is a baseline, never news).

## Tracks

Each about two minutes: intro on the tonic, the theme, a breath where the melody rests, the theme again with something
added, an outro that thins to nothing; the mix itself breathes (automation -9 dB in, -7 dB in the breath, 0 dB at the
second theme). Every gate theme's biome plays `awaken:music.gate.<theme>` (3-10 min between songs); each event is our
track (weight 3) or the vanilla music the biome had before (weight 1). Every stratum plays `awaken:music.descent.<n>`:
the Descent track (weight 2) or the stratum's old vanilla music (weight 1), 5-15 min apart. The **Abyssal Shrine** keeps
its music volume 0: "no sound at all".

| Track | Mode, tempo | Voices |
|---|---|---|
| Bell Crypt | D Phrygian, 54 | tolled bells play the sigh, monks' "o", a buried drone; glass takes the tune the second time |
| Fungal Hollow | F Lydian, 66 (3/4) | marimba arpeggios, a breathy flute on the raised fourth, warm pad, spores popping |
| Ember Mines | E Hijaz, 60 | an oud-like pluck (E F G# A G# F E), dark strings, far-off anvils, embers, a horn |
| Drowned Archive | A Aeolian, 58 | damped felt piano in broken chords, a sinking tune, drips and bubbles, murk |
| Clockwork Foundry | C Dorian, 72 | ticking escapement, a music-box ostinato, pizzicato bass, a soft horn (C G A G) |
| Glass Desert | E Phrygian, 50 | glass harmonica fifths and tune, an oud ostinato, ringing glass bowls, sand wind |
| Hive | F Aeolian, 76 | wings (a fluttering drone), a hexagonal pizzicato pattern, marimba, honey drips |
| Overgrown Temple | G Dorian, 60 | wooden flute, kalimba, a hand drum in the second theme, fireflies, forest air |
| Lantern Night Market | D major, 84 | the friendly one: guzheng-like koto, felt piano, frame drum and shaker |
| Frost Monastery | B Aeolian, 50 | a chant in unison over a hummed fifth, ice bells, cold wind, a huge stone room |
| Mirror Palace | C Lydian, 64 | glass phrases that read the same both ways, each with a reflection an octave up on the other side, reversed swells |
| Precursor Lab | F# Aeolian, 90 | a soft FM arpeggiator, sine pulse, the sigh in signal-blue glass, a mains hum |
| Nightmare | C# Aeolian, 52 | a detuned music box with one wrong note, cluster strings, reversed piano, whispers, a heartbeat |
| Sky Archipelago | D Lydian, 66 | airy strings and fifths, high flute, glass sparkle, wind |
| The Long Way Down (Descent) | A Dorian, 60 | calm but uneasy: sparse felt piano, a tritone that leans in and lets go, a far glass, cave tone |

## The System's UI family

| Event | File | Where | Sound |
|---|---|---|---|
| `signal.chime` (core) | `ui_toast_1/2` | every System toast | two-note glass "tink" (A5 D6 / F#5 A5) |
| `signal.open` (core) | `ui_card` | a choice card or gate card opens | D5 A5 E6 rising and left open, a breath of air |
| `signal.warning` (core) | `ui_warning` | warnings | two low pulses, each a minor second, the second a step lower (mono) |
| `level_up` (core) | `ui_level_up` | level up (played in the world) | D major arpeggio, a glint, a warm low bloom (mono) |
| `rank_up` (core) | `ui_rank_up` | rank up, a Truth made true (pitch 1.4) | a reversed swell into a chord bloom, a climbing glint, choir, sub (mono) |
| `ui.answer_yes` / `ui.answer_no` | | you answer a card | a bright fifth / a soft falling third |
| `ui.story` | | the Signal begins a line | a reversed glass swell opening into a pure fifth |
| `ui.now` | | the Now tracker's top line changes | one quiet glass tick |
| `ui.quest_done` | | a Directive or the Daily Regimen complete | A5 B5 D6, held |
| `ui.request_delivered` | | a request delivered (hearth: see Requests) | wooden bars, then glass and a soft bell |

Rules (`AudioUi`): the toast chime is dropped when a specific System sound (level up, rank up, quest done, request
delivered, warning, an answer) played in the last 2.5 s, so a moment never sounds twice; the Now tick plays only for a
clear next step (priority 50+), never in the first 10 s in a place, at most every 30 s. Server-asked UI sounds go to one
player as `audio_ui` and play non-positional on the Interface slider.

## Ambience beds (`Ambience`, all mono, seamless loops, the Ambient/Environment slider)

| Event | Source | Range | Sound |
|---|---|---|---|
| `amb.rift` | each rift tear (entity) | 32 | crackle and sparks over a rough, tearing drone, a glass-stress whine |
| `amb.grove` | each shimmer grove (entity) | 24 | glitter grains in D pentatonic, a breath of air, a held fifth |
| `amb.star_whistle` | a falling star, following it down | 96 | a whistle dropping faster as it comes, rushing air, rumble |
| `amb.star_cooling` | a landed meteor while sealed | 20 | steam hiss, metal ticking as it cools, a creak, a sizzle |
| `amb.star_cooled` | the moment it opens (unsealed) | 24 | a settling ring and a last breath of steam |
| `amb.well` | the Well's shaft | 40 | a deep drone, wind through the shaft's resonances, one far groan |
| `amb.ruin_night` | Precursor ruins, at night | 32 | old machines: a mains hum that breathes, a slow beacon tone, data chirps |

Entities are tracked from join/leave events; the Well, ruins and cooling meteors come from the server as `audio_emitters`
(within 96 blocks, sent only when the set changes, every 5 s). The gate hum is the gates module's own (`GateHum`).

## Mix

`python3 tools/art/audio_mix.py` measures every mod `.ogg` (gated RMS, BS.1770 LUFS, true peak) against its category,
`--fix` re-encodes outliers, `--ref` measures vanilla files for calibration. Targets were set from vanilla's own (its
music sits around -16 to -20 dBFS RMS, UI and level-up around -23 to -26, block and cave ambience -28 to -32), ours a
little under it:

| Category | RMS target | True peak | Vorbis |
|---|---|---|---|
| music (`music_*`) | -19 ± 2 dBFS | ≤ -1 dBFS | q3 cues, q2 tracks |
| ui (`ui_*`) | -24 ± 3 | ≤ -3 | q5 |
| amb (`amb_*`) | -28 ± 3 | ≤ -6 | q4 |

The generator masters to the target (toasts and the Now tick a little lower, rank up a little higher) and leaves
0.7 dB of true-peak room for the encoder. Last report: 41 files, all within target (music -19.0..-19.1 dBFS, max true
peak -1.3; UI -26.6..-22.0, max -3.6; ambience -29.1..-26.0, max -7.5); 25 MB in all (tracks Vorbis q2, ~1.2 MB each).

Checked by eye on spectrogram sheets (`audio_sheet.py`): no folded (aliased) partials, no clipping, no clicks except
the strikes meant to be there, tails that end before the file does, seamless loops. Band balance measured against
vanilla tracks (aerie, os_piano, komorebi, so_below, ancestry): the same shape (most energy under 500 Hz, -20 to -30 dB
from 2 to 8 kHz, almost nothing above 8 kHz), 9-20 dB of dynamics per piece like vanilla's 10-23. Positional sounds are mono (ambience, and the level-up, rank-up and warning
the server plays in the world); music and the other UI sounds are stereo. Every event has a subtitle.

## Try it

- `/awaken audio why` (anyone): each cue's cooldown for you, ambience near you, the last audio decisions.
- Game masters: `/awaken audio cue <id> [checked]` (plays now, or obeying the cooldowns), `/awaken audio ui <quest_done|
  request_delivered>`, `/awaken audio emitters` (resend).
- Autopilot: `tools/autopilot/audio.txt` (cues one by one, the UI family, the beds); the client logs `[audio] cue ...`.
- Rebuild: `python3 tools/art/audio_build.py [prefix...]` (all ~6 min), then `audio_mix.py` and
  `python3 tools/art/audio_sheet.py out.png music_cue_` (spectrogram contact sheet).

## Trigger points outside this package (one line each)

- `Awaken` / `AwakenClient`: `AudioModule.init`, `AudioClient.init`.
- `gates/GateService` (a gate becomes READY): `AudioCues.playNear(overworld, pos, 640, GATE_FORMING)`.
- `gates/Breaks.breakGate`: `AudioCues.playNear(over, pos, 640, GATE_BROKE)`.
- `gates/run/Runs.floorCleared` (Rest Shrine reached): `AudioCues.play(p, SHRINE)`.
- `progression/Directives.evaluate`, `progression/Regimen.evaluate` (complete): `AudioCues.ui(player, QUEST_DONE)`.
- `signal/client/SystemHud`: the answer, toast and story sounds go through `AudioUi` (three lines).
- `signal/client/NowClient.accept`: `AudioUi.nowChanged(priority)`.
- Biomes `gate_*.json` and `stratum_*.json`: only their `background_music` attribute (written by `audio_build.py`).

## Tests

`audio_events_resolve` (every awaken sound event is in sounds.json and registered, has a subtitle with a lang line,
every `type: event` reference exists, every file of ours exists and is Ogg Vorbis at 44.1 kHz, positional ones mono),
`audio_cues_have_subtitles` (each cue: event, own file, subtitle, Now Playing name, registered), `audio_biome_music`
(14 gate themes and 10 strata point at our events with delays of minutes; the Abyssal Shrine stays silent),
`audio_cue_cooldowns` (cooldowns, the gap, stage cues, the awakening once), `audio_cue_triggers` (nothing before
awakening, the awakening hook, the per-player clock, a named beast's first sighting and a lair through the discovery
watch, ordinary creatures silent), `audio_payloads_round_trip`.

## Requests

- **gates** (`tools/content/gates_content.py`): its `music(...)` lines should become
  `music("awaken:music.gate.<theme>", 3600, 12000)`, or a rerun puts vanilla music back in the gate biomes.
- **descent** (`tools/content/descent_assets.py`): `background_music` should become `awaken:music.descent.<n>`
  (6000-18000), same reason.
- **hearth** (requests, in progress on another branch): on a completed delivery call
  `AudioCues.ui(player, UiSound.REQUEST_DELIVERED)`, or point `hearth.request_complete` at
  `{"name": "awaken:ui.request_delivered", "type": "event"}`.

## Known gaps

- Nobody has listened yet: judged by spectrogram, band balance and loudness against vanilla, not by ear.
- Cue cooldowns live in memory (a restart forgets them; the awakening can only happen once anyway).
- A cue whose window closes while a long vanilla song plays is simply skipped (by design: never on top).
- Ruin positions for the night hum are the ruin locator's (start chunk centre, surface height), not the exact building.

## SPEC-worthy decisions

1. Music cues: awakening (once), rank-up (5 min), gate broke (10 min) are stage cues; beast (15), gate forming (20),
   shrine (8), lair (15) are ordinary; 3 min between any two (stage cues excepted); ordinary cues never play over a song.
2. Cues play through vanilla's music manager (Music slider, Now Playing toast, place music volume, vanilla's delay after).
3. Gate tracks: our track 3 : 1 the biome's old vanilla music, 3-10 min apart; Descent 2 : 1, 5-15 min; Abyssal Shrine
   silent.
4. Loudness targets: music -19 ± 2 dBFS RMS / -1 dBTP, UI -24 ± 3 / -3, ambience -28 ± 3 / -6.
5. The System's sounds share one glass voice in D major pentatonic; the toast chime yields to specific System sounds.
