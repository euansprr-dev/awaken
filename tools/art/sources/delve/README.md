# Recorded cave sounds

Four recordings by Joseph Sardin on BigSoundBank, each page releasing it under CC0 (public domain): free to modify,
use commercially and redistribute, no attribution required (credited here anyway). Licence:
https://bigsoundbank.com/licenses.html and https://creativecommons.org/publicdomain/zero/1.0/ . Approved for the cave
band sounds by Euan on 2026-10-28. No paid asset, AI service or Mojang recording was used.

| Source | Recording | Source page | Use |
| --- | --- | --- | --- |
| 1022.mp3 | Fall of Stone (29 s, about 19 falls) | https://bigsoundbank.com/fall-of-stone-s1022.html | `delve/settle_1..4`: stone settling |
| 1108.mp3 | Hammer fall on concrete (1.3 s) | https://bigsoundbank.com/hammer-fall-on-concrete-s1108.html | the knock in `delve/cave_answers` |
| 3589.mp3 | Anvil #1 (33 s) | https://bigsoundbank.com/anvil-1-s3589.html | its last strike, slowed, is the ring in `delve/cave_answers` |
| 2135.mp3 | Cave #1 (71 s, stereo) | https://bigsoundbank.com/cave-1-s2135.html | `delve/band_room` (the band's room tone), `delve/cave_breath` (its lows) |

The sources are the full-length files each page's own player streams (`https://bigsoundbank.com/UPLOAD/mp3/<id>.mp3`,
256-320 kbps, 48 kHz); they are **not committed** (5 MB together; `.gitignore`). To rebuild, fetch them here:

    for id in 1022 1108 3589 2135; do curl -sO "https://bigsoundbank.com/UPLOAD/mp3/$id.mp3"; done
    python3 ../../specs/audio_cave_recorded.py

`audio_cave_recorded.py` asserts each source's SHA-256, converts to 44.1 kHz mono, removes DC and rumble under 30 Hz,
cuts and crossfades the loops, trims silence round each fall and peak-normalises (decoded true peak under -3 dBTP,
every file under 400 KB). The answer's room is a convolution with the audio kit's synthesised cave impulse; nothing
else is synthesised. `measurements.json` measures the decoded shipping files. Nothing was heard on the owner's Mac:
these checks are technical; the mix still needs a player's ear.
