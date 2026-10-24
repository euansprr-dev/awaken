# Pixels step 3: MetalFX spatial, the plan (24 Oct 2026)

Starts once pack/turbo is on build/awaken-v1 (Turbo commit 319143fe has what this needs). Verified so far, read only.

## What Turbo provides (turbo-bridge, 24 Oct)
- Switch `metalObjects` (off by default; `TURBO=metalObjects=on` in tours): a FeatureSet with VK_EXT_metal_objects
  in `VulkanFeatureSets.optionalFeatureSets()`, enabled when the device offers it (MoltenVK 1.4.2 does).
- `Turbo.vulkanDevice()` (vkDevice, graphicsQueue, computeQueue, vma), `Turbo.vkImage(GpuTexture)`,
  `Turbo.compute().frame()` (a pass recorded into the frame's command stream), `TurboMixinPlugin`, `TurboVersions`.

## Verified here
- LWJGL 3.4.3 (bundled) has `EXTMetalObjects.vkExportMetalObjectsEXT` and `VkExportMetal{Objects,Device,
  CommandQueue,Texture,SharedEvent}InfoEXT`, `VkExportMetalObjectCreateInfoEXT`.
- MetalFX is on this Mac (macOS 26.6.2). It and libobjc live in the dyld shared cache: no files on disk, but
  `dlopen` of `/System/Library/Frameworks/MetalFX.framework/MetalFX` and `/usr/lib/libobjc.A.dylib` works, and
  `SymbolLookup.libraryLookup(path, arena)` is dlopen. No native library to ship.
- Minecraft's Vulkan frame is one `vkQueueSubmit2` whose `VulkanQueue.Submission` splits into stages: a
  `VulkanCommandEncoder.signalSemaphore` after some command buffers ends a stage, a `waitSemaphore` starts a new one
  (both public, outside render passes). So the GPU can stop mid-frame for work done elsewhere.

## The frame
1. After the post effects (PixelsFrame.end, the low-resolution world is finished): `signalSemaphore(T, n)`, with T
   a timeline VkSemaphore created with `VkExportMetalObjectCreateInfoEXT(SHARED_EVENT)` and exported as an
   MTLSharedEvent.
2. On Pixels' own MTLCommandQueue (made from the exported MTLDevice; a queue apart from MoltenVK's, so a GPU wait
   can't block Minecraft's command buffers): `encodeWaitForEvent:T value:n`, the MTLFXSpatialScaler's
   `encodeToCommandBuffer:`, `encodeSignalEvent:T value:n+1`, `commit`. It's committed on the CPU straight away; the
   GPU wait does the ordering.
3. Back in the frame: `waitSemaphore(T, n+1)`, then the GUI. MetalFX writes the main colour directly if its texture
   meets the scaler's `outputTextureUsage` (Minecraft's targets are sampled + colour attachment: ShaderRead |
   RenderTarget); otherwise into a Turbo-made image with STORAGE, then one copy.

## Through java.lang.foreign
`objc_getClass`, `sel_registerName`, and `objc_msgSend` bound once per call shape (id(id,SEL), void(id,SEL,id),
void(id,SEL,long), id(id,SEL,id), void(id,SEL,id,long), boolean(id,SEL,id)). The scaler: `[[MTLFXSpatialScalerDescriptor
alloc] init]`; set `inputWidth/inputHeight/outputWidth/outputHeight`, `colorTextureFormat`/`outputTextureFormat`
(BGRA8 or RGBA8 Unorm, as exported), `colorProcessingMode` Perceptual (the frame after Lens is display-referred);
`supportsDevice:`; `newSpatialScalerWithDevice:`. Per frame: `setColorTexture:`, `setOutputTexture:`,
`setInputContentWidth:/Height:`, `encodeToCommandBuffer:`. Rebuilt on resize or scale change. Objects released with
`release`; autorelease pools around per-frame calls.

## The bar
Kept only if it beats SHARP in the same interleaved tour: GPU total (world + upscale) below SHARP's at the same
scale (SHARP: 0.24 ms at 2880×1658), and shots at least as crisp as FSR's edges. Falls back to SHARP when the
switch, the extension, the export or `supportsDevice:` says no.

## Open
- Whether MoltenVK exports an MTLTexture for an image created without `VkExportMetalObjectCreateInfoEXT` (it backs
  every VkImage with one, so likely). If not, Pixels' low and output targets become Turbo images with the export info.
- MoltenVK's timeline semaphores are MTLSharedEvents on Apple GPUs (needed for step 1's export). Check at the start.
- GPU timing across the Metal queue: the timestamps bracket the Vulkan waits, so "upscale" includes MetalFX.
