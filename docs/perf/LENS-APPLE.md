# Lens on Apple (26-27 Oct 2026)

Euan, playing: Look tiers are the biggest fps lever, but the visual difference between them is small. The brief: make
Lens far cheaper on Apple Silicon, and make each tier earn its cost. SPEC §22 "Lens on Apple" holds the design.

All numbers: the Roost at noon, MacBook at Euan's 4.7 MP world (render scale 0.95 = the Dell at 0.75), Vulkan
(MoltenVK), RD 24, the pack without comfort, a quiet Mac at night. GPU world pass p50 (level + post effects).

## Where Lens's time went (old path, tour lens-passes-perf.txt; probe = one pass group swapped for a constant)

| pass (size) | Cinematic | Ultra |
|---|---|---|
| sunmap (512² / 1024²) | 0.2 ms | 0.7 ms |
| light buffer (960×540 / 1440×810) | 0.55 | 1.6 |
| clouds (560×315 / 800×450) | 0.2 | 0.4 |
| prefilter, bloom down/blur, shafts | ~0 each | ~0 each |
| **lit composite (full size)** | **~4.0** | **~4.0** |
| final copy to main | ~0.2 | ~0.3 |
| one extra full-size pass | ~0.1 | ~0.2 |
| **chain over Lens off** | **3.7** (11.29 vs 7.56) | **5.4** (12.94) |

- Swift costs nothing over Lens off: 7.56 vs 7.56 ms.
- The composite, part by part (tour lens-composite-perf.txt, probe-only `#define`s):
  - its light (lensLight: upsample, normals, cover, wet, water): 1.15 ms;
  - the cloud blend: 0.45 ms;
  - fog glow, sun scatter and the volumetric term: 0.5 ms;
  - the filmic grade: ~0.
- A cloud blend that is one texture sample plus a few parameter reads, costing 0.45 ms, pointed at the parameter reads.
  Every F()/P() value was a texture fetch per pixel per use, about 70 a pixel in the lit composite.

## What changed (no visible change; look tour lens-apple-look.txt, old vs new at the same moment)

- **Tiles** (Turbo): Lens's chains without per-target depth buffers, separate clear passes, or loading/storing on
  every pass.
- **The uniform block:** the 48 frame values and 32 parameters bound once per pass.
- **The composite's volumetric term** reuses the light's upsample.

| Look | old path | Tiles | Tiles + block | frame p50 old → new |
|---|---|---|---|---|
| Swift | 7.71 ms | 7.27-7.36 | 7.13-7.20 | 9.59 → 9.1 ms (105 → 111 fps) |
| Cinematic | 11.51 | 10.96-11.07 | 10.06 | 13.26 → 11.8 ms (75 → 85 fps) |
| Ultra | 13.33 | 12.77 | 11.79-11.86 | 15.04 → 13.65 ms (66 → 73 fps) |

The research (Apple's Metal guidance, MoltenVK's source and issues) agreed on what to do and ruled things out:
- load/store traffic is most of the bandwidth on Apple GPUs, and a clear costs nothing;
- MoltenVK maps the ops one to one and never merges passes or uses tile memory between them;
- GLSL `mediump` does not become half precision through MoltenVK;
- memoryless targets can't help sampled targets.

## What each tier shows (CI tour lens-tiers-look.txt: off, Swift, Cinematic, Ultra at five scenes)

- **Swift** looks almost the same as Lens off: haze, bloom and grade, for no measurable cost.
- **Cinematic's** visible change is mostly the volumetric clouds and the warmer sunset sky. Its shadows and AO barely
  read at noon.
- **Ultra** is hard to tell from Cinematic at any of the five scenes, for 1.7-1.8 ms more.

## For Euan (each changes the look, so each is his call)

1. **Clouds on Swift.** About 0.65 ms (the cloud pass plus the blend) gives Swift Cinematic's biggest visible feature.
2. **Ultra earns its cost or folds into Cinematic.** Either make Ultra clearly richer, or drop the tier's extra cost.
3. **A light composite at half resolution**, with depth-aware upsampling: about 0.6-0.8 ms on Cinematic, with slightly
   softer shadow edges.
