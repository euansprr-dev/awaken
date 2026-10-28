# Recorded city bells

Downloaded 2026-10-28 through BigSoundBank's public WAV download forms. All three
source pages explicitly release their recordings under CC0 and permit modification,
commercial use and redistribution. Licence: https://bigsoundbank.com/licenses.html
and https://creativecommons.org/publicdomain/zero/1.0/ . Attribution is retained here.
No subscription, AI service, Mojang recording or paid asset was used.

| Source WAV | Recording and creator | Source page | Use |
| --- | --- | --- | --- |
| 3446.wav | Bell 1 O'clock, church of La Loupe; Joseph SARDIN & Axeline T. | https://bigsoundbank.com/bell-1-o-clock-s3446.html | Tower/district/alarm strokes; legacy carillon events |
| 0292.wav | Small Bell #1; GlaneurDeSons | https://bigsoundbank.com/small-bell-1-s0292.html | Watch/crier handbell |
| 0294.wav | Small Bell #3; GlaneurDeSons | https://bigsoundbank.com/small-bell-3-s0294.html | Cart harness |

Rebuild: `python3 tools/art/specs/audio_city.py` (Python, NumPy, ffmpeg).
Source hashes are asserted by the generator. It converts to 44.1 kHz mono, removes
DC and rumble below 45 Hz, trims only quiet tails, fades boundaries and normalizes
true peaks with at least 6 dB of headroom after Vorbis decoding. District pitch is
only 6% higher; there is no synthetic oscillator, artificial tail, hard limiter or
RMS boost over the recording. Source files are retained so rebuilds are offline.
`measurements.json` measures the decoded shipping files, not the pre-encode audio.

Validation: waveform/onset/decay, mono, clipping, boundary discontinuity, duration,
source checksum and decoded true peak checked offline. No audible playback or
in-game listening was performed on the owner's Mac. These checks establish technical
quality; the final subjective mix and room acoustics still need player listening.
