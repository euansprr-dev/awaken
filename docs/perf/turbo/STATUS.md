# Turbo bridge — PAUSED 2026-10-24

Euan paused all Far Sight sessions ("Pause all for now, I'll lyk when to start these again"). Resume only on his word.
Branch `pack/turbo` (from build/awaken-v1 at 8c0c359). Nothing pushed, no build or tour run yet.

## Done
- Read the Far Sight plan (path 2, the Turbo contract) and CLAUDE.md.
- Source survey in `/Volumes/MEDIA/Awaken/.ref/src/com/mojang/renderpearl` (findings below), enough to write the code.

## Half-done
- Nothing coded yet. No CLAUDE.md/SPEC edits, no AT, no mixin config.

## Exact next step
Step 1 and 2 of the brief: add the Turbo contract to CLAUDE.md (Code style) and a Turbo section to docs/SPEC.md; enable
`accessTransformers` in build.gradle (MDK line ~49, check NeoForge 26.3 AT syntax in .ref/neo first) with the one line
opening `com.mojang.renderpearl.frontend.FrontendGpuDevice.backend`; add `awaken.turbo.mixins.json` and its
`[[mixins]]` block in `src/main/templates/META-INF/neoforge.mods.toml`; then `dev.awaken.turbo.Turbo` (probe, version
lock, why-off log line) and build once through slot.sh.

## Source findings (verified, 26.3 / NeoForge 26.3.0.95)
- Door: `FrontendGpuDevice.backend` (private final `GpuDeviceBackend`, line 39) → cast to `VulkanDevice`.
  `FrontendCommandEncoder.backend()` is public → cast to `VulkanCommandEncoder`.
- `VulkanDevice`: public `vkDevice()`, `vma()`, `graphicsQueue()`, `computeQueue()` (falls back to the graphics queue
  when there is no separate family), `transferQueue()`, `instance()`. `getDeviceInfo().underlyingExtensions()`
  holds enabled instance (I) and device (D) extensions; the full available list needs
  `vkEnumerateDeviceExtensionProperties` on `vkDevice().getPhysicalDevice()`.
- `VulkanCommandEncoder` public: `allocateAndBeginTransientCommandBuffer()` (from the encoder's per-frame pool, reset
  two submits later), `execute(VkCommandBuffer)` (ends the current buffer, adds ours to the frame's submission in
  order), `waitSemaphore(sem, value, stageMask)`, `signalSemaphore(...)`, `queueForDestroy(Destroyable)`.
  All throw inside a render pass. Its own barrier after passes: ALL_COMMANDS (0x10000), MEMORY_READ|WRITE (0x18000).
  → Same-frame compute = record into a transient buffer, end with a compute-write → vertex/index/indirect-read
  barrier, `execute()` it before the draw. Async = own submission on `computeQueue()` + timeline semaphore +
  `encoder.waitSemaphore`; buffers CONCURRENT across both families when they differ.
- `VulkanGpuBuffer` is abstract with a public `(long vkBuffer, int usage, long size)` constructor; subclass must
  implement `destroy()`, `isClosed()`, `close()`, `map(...)`. Render passes only cast to `VulkanGpuBuffer` and read
  `vkBuffer()`. Mojang's `bufferUsageToVk` has no STORAGE bit, so Turbo buffers are made with `Vma.vmaCreateBuffer`
  directly (STORAGE|INDIRECT|VERTEX|INDEX|TRANSFER_SRC|DST).
- `RenderPass.drawIndexedIndirect(slice, count)` needs `GpuBuffer.USAGE_INDIRECT_PARAMETERS` (512) on the wrapper's
  usage, offset % 4 == 0, 20 bytes per command.
- Pipeline cache: `VulkanRenderPipeline.compile` calls `VK12.vkCreateGraphicsPipelines(device, 0L, ...)` four times
  (depth, no depth, depth+stencil, stencil; NeoForge-patched) → one `@ModifyArg` (index 1) covers all four.
- Shader opt: `GlslCompiler.createBaseShaderOptions` sets `shaderc_compile_options_set_optimization_level(opts, 0)`
  with debug info on; Vulkan 1.2 target. `SPIRVModule` reflects uniforms by name via spvc, so an optimised compile
  must keep names and unused bindings — check the tour's shots before keeping it.
- MoltenVK env (MVK_CONFIG_*) must reach the client process: add pass-through `environment` lines to build.gradle's
  `autopilot` run from `-P` properties, set by tools/autopilot.sh.
- Measuring: Autopilot already has `perf <label> <seconds>` (p50/p95/p99); launch-to-title and first-world-load
  hitch still need a log line each.
