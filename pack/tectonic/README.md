# Tectonic presets (Awakened Lands, docs/SPEC.md §15)

Each file is a complete `config/tectonic.json` for Tectonic 3.0.31. An autopilot run picks one with
`TECTONIC=<preset>` (tools/autopilot.sh copies it into the game folder before the client starts; Tectonic reads its
config once at startup). `tools/terrain/studio.sh <preset>` surveys five seeds and shoots a vista with it.

| Preset | What it changes from Tectonic's defaults |
|---|---|
| `default` | nothing (the baseline) |
| `awakened` | vertical scale 1.35, elevation boost 0.25, erosion scale 0.2, ridge scale 0.22, continents scale 0.11, flat-terrain skew 0: taller, thicker ranges, larger continents, more plateaus |
| `grand` | vertical scale 1.6, elevation boost 0.5, erosion scale 0.18, ridge scale 0.2, continents scale 0.11, flat-terrain skew -0.1: the most dramatic that still fits vanilla height (checks where peaks cap at 320) |

Height stays vanilla (−64 to 320) in every preset: a taller world was the biggest generation slowdown in user reports.
