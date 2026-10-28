# Recorded sources

Recordings the audio kit layers under its own synthesis. Only files a spec actually uses are kept, and every one is
under a licence that allows it in a public, All Rights Reserved mod. Each pack keeps its own licence file.

| Folder | What | Licence | From | Used by |
|---|---|---|---|---|
| `city` | Three CC0 bells (BigSoundBank): a church bell, two small bells; provenance in its README | CC0 1.0 | bigsoundbank.com | `specs/audio_city.py`, `specs/audio_gate_worlds.py` |
| `kenney/impact-sounds` | Impact Sounds (Kenney): metal, plate, wood, glass, mining, punch, soft impacts | CC0 1.0 (`License.txt`) | kenney.nl/assets/impact-sounds | `specs/audio_arms.py`, `specs/audio_gate_worlds.py` |
| `kenney/rpg-audio` | RPG Audio (Kenney): cloth, creaks, leather, knife slices, a chop, footsteps | CC0 1.0 (`License.txt`) | kenney.nl/assets/rpg-audio | `specs/audio_arms.py`, `specs/audio_gate_worlds.py` |

CC0 asks for nothing; Kenney is credited here anyway. A spec that names a new file adds it back from the pack (the
renders are deterministic: pruning the unused files left every rendered sound byte-identical).
