# Tectonic presets (Awakened Lands, docs/SPEC.md §15)

Each file is a complete `config/tectonic.json` for Tectonic 3.0.31. An autopilot run picks one with
`TECTONIC=<preset>` (tools/autopilot.sh copies it into the game folder before the client starts; Tectonic reads its
config once at startup). `tools/terrain/studio.sh <preset> [survey]` surveys five seeds (and shoots a vista without
`survey`).

| Preset | What it changes from Tectonic's defaults |
|---|---|
| `default` | nothing (the baseline) |
| `awakened` | **the pack's preset.** vertical scale 1.35, elevation boost 0.25, erosion scale 0.2, ridge scale 0.22, continents scale 0.08, ocean offset -0.55, flat-terrain skew 0: taller, thicker ranges on larger continents with less sea |

Height stays vanilla (−64 to 320): a taller world was the biggest generation slowdown in user reports, and the
studio found at most 5% of land within 20 blocks of the ceiling.

## Studio results, 2026-10-24 (5 seeds, 6 km around spawn; tools/terrain/survey.py)

| Preset | Land | Relief p50 / p90 (512-block windows) | Land above y 160 | Landscapes per km | Land at ≥ y 300 |
|---|---|---|---|---|---|
| vanilla (no Tectonic) | 54–65% | 77–82 / 106–119 | 0–1% | 2.4–3.6 | 0% |
| default | 28–46% | 69–114 / 134–194 | 1–12% | 2.3–3.9 | 0% |
| first `awakened` try (scale 1.35, continents 0.11, ocean −0.8) | 20–53% | 70–106 / 142–172 | 4–22% | 2.5–4.2 | 0–1.7% |
| `grand` (scale 1.6, boost 0.5) | 20–52% | 69–106 / 152–193 | 8–24% | 2.3–4.0 | 0.2–5.8% |
| land_a (ocean −0.65, continents 0.10) | 25–62% | 65–106 / 117–212 | 9–34% | 2.8–3.5 | 0.3–2.2% |
| **land_b → `awakened`** (ocean −0.55, continents 0.08) | 28–73% | 64–95 / 127–223 | 6–45% | 2.8–3.3 | 0–4.8% |

Tectonic's defaults make an archipelago around spawn (a third land); `awakened` gives continents with ranges and
still some coast. Maps: docs/terrain/studio-2026-10-24-maps.jpg (vanilla, default, awakened on seeds 777 and 4242).
