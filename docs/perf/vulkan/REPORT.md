# Vulkan check (24 Oct 2026)

Same world, places and moments on OpenGL then Vulkan, back to back, MacBook Pro M4, framebuffer 2880x1658, pack profiles
perf, horizon and feel, Distant Horizons on Swift, Lens on Swift, frame cap lifted (maxFps 260, vsync off). The machine was
busy with other sessions (load 34-39) during both runs, so the absolute numbers run high; the comparison is fair.

| Scene | OpenGL p50 | Vulkan p50 | Change | OpenGL p99 | Vulkan p99 |
|---|---|---|---|---|---|
| Sunset vista | 19.5 ms | 16.7 ms | -15% | 31.7 ms | 28.7 ms |
| Vista west | 18.7 ms | 14.1 ms | -25% | 28.1 ms | 24.5 ms |
| Night | 20.9 ms | 14.0 ms | -33% | 36.1 ms | 26.6 ms |

Looks (contact sheets compared pair by pair):
- Holo: the wrist band, K menu glass, glow text and Lens focus dimming match.
- The Ritual of the Bell: all 36 frames match: hall, candles, eyes, drain, void, tear, first-power cards. Outside the
  tear the frame is 10/255 on Vulkan against pure black on OpenGL.
- Mods: Sodium, Distant Horizons, Player Animation Library, Bendable Cuboids, 3D Skin Layers, Not Enough Animations,
  Camera Overhaul, EntityCulling, ImmediatelyFast, MoreCulling, Lithium and FerriteCore all load and run. The only log
  noise is MoltenVK's "Metal does not support disabling primitive restart" warning.
- Distant Horizons: on Vulkan, Lens reads DH's depth texture as one constant value (OpenGL: cleared to 0). Not chased
  further; Far Sight will replace DH and own its depth.
  Later the same day (pack/look): this run predates e719e4d, the fix that unpacks DH's texture, so Lens was sampling its
  own 1×1 placeholder, created without data (0 on OpenGL, whatever the memory held on Vulkan), not DH's depth. DH's
  real depth on Vulkan is unchecked: until tools/autopilot/lens-dh.txt checks it there, DH shares it with Lens only on
  OpenGL, and Lens trusts a far depth only once it proves itself (SPEC §14, Lens and far terrain).

Verdict: Vulkan is the pack's default from this commit (tools/pack.py install; SPEC §14).

Scripts: tools/vulkan-check.sh (runs a tour on both backends), tools/autopilot/vulkan-check.txt. Shots and full logs
stay in the worktree (docs/perf/vulkan/*/, not committed: about 80 screenshots at 2880x1658).
