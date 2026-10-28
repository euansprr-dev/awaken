# Recorded sources

Recordings the audio kit layers under its own synthesis. Only files a spec actually uses are kept, and every one is
under a licence that allows it in a public, All Rights Reserved mod. Each pack keeps its own licence file.

| Folder | What | Licence | From | Used by |
|---|---|---|---|---|
| `kenney/impact-sounds` | Impact Sounds (Kenney): metal, plate, wood, glass, mining, punch, soft impacts | CC0 1.0 (`License.txt`) | kenney.nl/assets/impact-sounds | `specs/audio_arms.py` |
| `kenney/rpg-audio` | RPG Audio (Kenney): cloth, creaks, leather, knife slices, a chop, footsteps | CC0 1.0 (`License.txt`) | kenney.nl/assets/rpg-audio | `specs/audio_arms.py` |
| `city` | Bells (BigSoundBank: Joseph SARDIN & Axeline T., GlaneurDeSons) | CC0 (`city/README.md`) | bigsoundbank.com | `specs/audio_city.py` |
| `delve` | Cave recordings (BigSoundBank: Joseph Sardin): fall of stone, hammer, anvil, cave air; sources fetched, not committed | CC0 (`delve/README.md`) | bigsoundbank.com | `specs/audio_cave_recorded.py` |

CC0 asks for nothing; Kenney is credited here anyway. A spec that names a new file adds it back from the pack (the
renders are deterministic: pruning the unused files left every rendered sound byte-identical).
