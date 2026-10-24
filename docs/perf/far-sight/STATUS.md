# Far Sight: status

Branch `pack/far-sight`. Plan: https://claude.ai/artifact/LvAoTbYB82o6ggcHS73rX1. Spec: docs/SPEC.md §16.

- **Stage 0 (the proof): done.** The A/B is in REPORT.md. Cheaper than vanilla RD 32 in every scene while drawing to
  16 km; the horizon shows in under a second; no seams; OpenGL works. Look: mixed (see REPORT.md).
- **Stage 1 (make it true): built, CI green, not yet toured.** Generated layer (real chunks summed on the server tick,
  cached on disk), edits re-summed on save, water depth, landmarks (gate pillars through `Gates.near`). Tour:
  `tools/autopilot/farsight-gen.txt` (light).
- Next: the look upgrades named in REPORT.md (cliff walls, terrace edges, block texture, canopies); then stage 2
  (server-made tiles for multiplayer, a client distance setting, Far Sight in the pack's horizon profile, agreed
  with the pack session first).
