# Pixels: status

**PAUSED 2026-10-24** on Euan's word ("Pause all for now, I'll lyk when to start these again"). Nothing running:
no Gradle run, no client, no slot.sh lock. Nothing pushed.

## Done
- Branch `pack/pixels` cut from build/awaken-v1 at 8c0c359.
- Read the Far Sight plan (path 4) and the 26.3 render path in `.ref/src`.

## Half-done: where to hook render scale (reading only, no code yet)
Findings so far, from `.ref/src`:
- `GameRenderer.mainRenderTarget` is `private final`, made by `ClientHooks.instantiateMainTarget(...)`.
  `GameRenderer.render()` clears it, runs `renderLevel()`, `blitEntityOutline()`, `applyPostEffects()` (Lens),
  then clears depth and draws the GUI into it.
- `LevelRenderer.render(...)` imports the target as `targets.main = frame.importExternal("main", gameRenderer.mainRenderTarget())`
  and sizes every internal target (OIT, depth bounds) from `gameRenderer.mainRenderTarget().width/height`.
  `FrameGraphSetupEvent` fires after that, but the clear pass, OIT composite, depth-bounds cull and sky renderer
  call `gameRenderer.mainRenderTarget()` directly, so swapping `targets.main` in the event alone is not enough.
- `render3dHud` / `renderItemInHand` / screen effects / `integrate3DHudDepth` use the `mainRenderTarget` field
  directly, not the getter.
- Candidate (unverified): during `renderLevel()` make `GameRenderer.mainRenderTarget()` and the field reads
  return a scaled `TextureTarget`, then upscale into the real target before `applyPostEffects()` or before the GUI.
  Needs a mixin in `dev.awaken.turbo.pixels` (awaken.pixels.mixins.json); check whether an access transformer
  on the field plus a swap around `renderLevel()` is enough first.

## Next step
1. Check `Minecraft.getMainRenderTarget()`, `globalSettingsUniform` (screen size uniform), the viewport/projection
   for the hand (`hudProjection.setupPerspective(... window width/height)`), and every other reader of the main
   target during the level pass (Holo's SubmitCustomGeometryEvent, Lens, DH compat).
2. Pick the hook (least force), write the minimal version check, then the scaled target + bilinear upscale.
