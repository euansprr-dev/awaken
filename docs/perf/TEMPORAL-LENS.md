# Temporal Lens 2 (build B of docs/perf/IDEAS-26OCT.md, 26 Oct 2026)

Branch: pack/temporal-lens. Setting: `lensTemporal` (default on). Autopilot: `lens temporal on|off|setting`.

- **What:** clouds march one pixel in four per frame and the light buffer one in two, in 8×8-pixel tiles. The rest
  keep their reprojected history. A march blends in at 1-(1-f)^4 or 1-(1-f)^2, so the response over time is unchanged.
  No history, off screen, a new surface or lightning: full march. Off is exactly the old path.
- **Tours:**
  - Mac perf tour `tools/autopilot/lens-temporal-perf.txt`: MacBook at Euan's 4.7 MP world, the Roost at noon, a
    quiet machine.
  - CI look tour `tools/autopilot/lens-temporal-look.txt`.

| GPU world pass p50 | temporal off | temporal on |
|---|---|---|
| Ultra | 17.02 ms | **15.12 ms** (frame p50 19.4 → 17.3 ms, 51 → 58 fps) |
| Cinematic (paired windows) | 13.95, 13.53 ms | 13.70, 13.13 ms (−0.25 to −0.4) |
| Lens Swift (reference) | 9.29 ms | |

- **The first, per-pixel pattern saved nothing** (13.22 off vs 13.25 on). Apple's GPU runs pixels in lock-step SIMD
  groups, and a marching pixel in every group kept them all busy. Whole 8×8 tiles fixed it.
- **Look:** no smear and no tile blocks, at rest or in a 180° turn, on Cinematic and Ultra; light and shadows at dusk
  match.
- **Cinematic's Lens cost (~4.2 ms over Swift) is mostly not the marches.** They run at 0.17-0.5 MP; the full-size
  composite runs at 4.7 MP. Next step: per-pass GPU timing of the chain.
- **MDI off vs on:** 13.97/13.90 in one run, 13.38/12.06 in the other (drift). Turning it off gains nothing; it stays on.
- **On a quiet machine, Cinematic at the Roost runs 15.1-16.2 ms per frame (62-66 fps), not 21.** The afternoon
  tour's load of 20 inflated its GPU timings, which include the GPU waiting on the CPU.
