# Turbo bridge: STOPPED 2026-10-25

Euan's call in the pack session ("Finish Far Sight, stop the rest"): performance isn't the bottleneck (120 fps at render
distance 16, 100+ at 32 on his Dell), so Turbo and Pixels stop with their work saved. Resume only on his word.
Branch `pack/turbo` (pushed), not merged into build/awaken-v1. Results so far: `REPORT.md`.

## Done (on pack/turbo)
- The Turbo core in `dev.awaken.turbo`, client only: the probe (master switch, version lock MC 26.3 / NeoForge 26.3.0.95,
  Vulkan backend, compute self-test), one `[turbo] on:` / `[turbo] off: <why>` line, the device's extension list.
- The one access transformer (`FrontendGpuDevice.backend`), `awaken.turbo.mixins.json` + `TurboMixinPlugin` (a mixin is
  applied only on a verified build with its switch on), switches in `config/awaken-turbo.properties` / `-Dawaken.turbo`.
- `TurboCompute`: GLSL compute through shaderc on Minecraft's VkDevice, push descriptors, VMA storage buffers wrapped as
  `VulkanGpuBuffer` (RenderPasses draw from them), frame passes through `VulkanCommandEncoder.execute`, async passes on
  the compute queue with a timeline semaphore and `encoder.waitSemaphore`.
- The gate passed: a compute-built 256x256 grid and its indirect args drawn in the same frame after the sky, no hitch
  (~0.24 ms GPU per frame); OpenGL untouched (`[turbo] off: the backend is OpenGL`).
- Launch experiments measured and dropped (no win on the M4): `pipelineCache`, `shaderOpt`, the three MoltenVK settings.
  All default off; their code stays behind the switches.
- For Pixels: switch `metalObjects` (VK_EXT_metal_objects as an optional FeatureSet), `Turbo.vulkanDevice()`,
  `Turbo.vkImage(GpuTexture)`, `TurboCompute.Pass.commandBuffer()`. Pixels has the names (message of 2026-10-24).
- SPEC §19 (Turbo) and the §12 API line; GameTest `turbo_version_lock_current`; `tools/turbo-bench.sh`,
  `tools/autopilot/turbo.txt`, autopilot `TURBO=` / `MVK=`.
- CI: run 40111060966 green (all 377 GameTests) on a7ce3376; the later commits add only client code, docs and the
  Pixels merge, built locally (`build` passes on e3d91c41).

## Not done
- **CLAUDE.md does not carry the Turbo contract** (the pack session asked not to add it). The contract text is in SPEC §19;
  code comments that say "the Turbo contract (CLAUDE.md)" mean that text.
- The `metalObjects` switch is untested in a running game (the light tour `turbo-bench.sh final metal gl` was stopped
  before it ran).
- Far Sight and Pixels were not told the core landed: it hasn't (not merged).

## Exact next step on resume
1. Merge build/awaken-v1 into pack/turbo; `slot.sh build tools/gradle.sh 900 build`.
2. `SLOT_WHO=turbo slot.sh light tools/turbo-bench.sh final metal gl`: the metal run's `[turbo] device extensions` line
   must say `VK_EXT_metal_objects enabled`; the gl run must say `[turbo] off: the backend is OpenGL`.
3. If Euan wants the contract in CLAUDE.md, add it (Code style, with his two quotes), push, watch CI, and send the
   pack session the branch, commit and run id; then message far-sight and pixels with `turbo-api.md` from the shared notes.
