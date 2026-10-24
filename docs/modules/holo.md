# Holo UI: the System on your wrist

Design: docs/proposals/holo-ui.md. Package `dev.awaken.holo` (common: the relay) and `dev.awaken.holo.client`
(everything you see). Built: Phase 1 (panels, pipelines, spring, HUD motion, demo, tour) and Phase 2 (the K menu on
the wrist, the third-person pose, the relay, the `UiCanvas` split). Not built yet: the inventory (P3), System cards as
holo panels (P4, waits for the owner), sounds and accessibility polish (P5).

## What the player sees
- **K** (with `statusStyle = HOLO`): the off-hand forearm comes in from the lower left (the elbow off-screen), the
  inside of the wrist turned to the eye. Around the wrist sits the System's band, flush with the skin: a thin dark strip
  with two emissive lines, small segment nodes with a slow travelling pulse and the System's diamond on the inside of
  the wrist. The band flares; two beams and a faint projection cone open from the diamond toward the panel's lower
  corners (brighter and wider for the first ~200 ms, with scan dots running up them), and the panel grows out of the
  beams' origin into its place, unfolding (height, then width); its frame draws itself, a scan line sweeps down and the
  text types in with a caret. It rests up and to the right of the band, left of the crosshair (about 45% of the
  screen's width), yawed toward the wrist, and trails your view when the camera turns. Esc or K folds it back into the
  band (250 ms). K while it opens skips to open. Tab and 1–4 switch tabs. The main hand (or its item) sinks out of view
  while the arm is up, exactly as vanilla lowers it on an item swap.
- **The panel is light, not a slab**: 38–50% glass (denser toward the bottom, where it is projected from), static scan
  lines three units apart, a slow two-wave shimmer (no strobing), a hairline emissive edge with corner brackets and an
  additive halo around it. Readability wins over sheerness: every block of text sits on a feathered dark bed (58% dark
  on top of the glass, 7-unit feather; touching lines merge into one field per paragraph, column or grid), body text
  is white, labels pale blue, headers System blue with a soft glow, all with a shadow; content is 272×228 GUI units at
  5 framebuffer px per unit on the owner's 3024×1964 (25% larger than the flat menu's GUI scale 4). Checked over a
  bright sky, grass and the night with Lens on its default (holo.txt shots 25–27).
- **Other players** see your forearm come up across your chest (a third-person pose on the vanilla model), a brighter
  ring of light on the wrist and a small, content-free hologram card beside your body on the wrist's side, with its
  own beams. Walls hide it (depth-tested). They never see the panel's content.
- **The HUD**: Awaken's GUI layers drift a few pixels against a sharp turn and settle in about 200 ms. Vanilla's
  hotbar, hearts and crosshair never move.

## Timeline (ms along the opening; closing replays it backward at 1.8×)
| ms | |
|---|---|
| 0–120 | the arm rises (ease-out, 4% overshoot) |
| 60–200 | the band flares (peak ×2.5 at 140, settles to ×1) |
| 110–360 | the beams' opening surge (up to ~3× brighter and wider, peaking 160–250); from 260 a faint steady link (35%) |
| 140–370 | the panel grows out of the beams' origin on the wrist (a homothety about it) into its place |
| 160–280 / 220–360 | unfold in Y, then X |
| 170–230 | the frame and corner glints; the glass fills in 230–380 |
| 250–390 | the scan line sweeps top to bottom |
| 300+ | text types in at Signal's speed ×4, lines 12 ms apart, with a caret |
| 250 | clicks land from here |

Lens focus (`UiHooks.focus()`) eases from 160 to 450 ms.

## Placement and the spring
- A panel's size is chosen in **whole framebuffer pixels per unit** (about 45% of the screen width, at most 80% of the
  height): 1 unit = 1 GUI pixel of layout, so `Ui` sections fit unchanged and text stays crisp (at 3024×1964 the
  272×228 K panel draws at 5 px per unit). Recomputed every frame from the level projection, so sprint FOV and cast
  punches never swell it; vanilla's view bob and hurt tilt (folded into the level projection) are undone.
- Rest: centre at (-0.455, +0.19) of the half-screen, 0.62 m ahead, parallel to the screen and then yawed 7° toward the
  wrist and leaned back 3° (the yaw reads as a panel turned to the arm that casts it; text stays crisp). That keeps its
  right edge just left of the screen's centre and its bottom clear of the raised forearm (the hand pass draws over
  everything, so an overlap would cover text). The vanilla crosshair is hidden while the holo K menu is open (the
  cursor works it). In third
  person it sits at least as far as -0.50 and at 80% of the size so the character with its raised wrist stays in view.
- Spring: critically damped, exact per-frame solution, ω = 5.83 / 0.20 s (settles in 200 ms); the shown lag is
  soft-clamped to 4° (`4·tanh(o/4)`), the state to 8°. Turns over 60° in a frame (teleport, respawn) snap.
  `reducedMotion` follows directly and fades in place.

## Drawing
- Panels draw in the main pass's **translucent custom geometry** (`submitSpecial` into
  `SubmitNodeCollection.translucentCustomGeometry`): after the world's opaque parts and its other translucent features,
  before water, particles, clouds and weather, which the glass's depth write then keeps behind the panel. No extra
  render pass. With Improved Transparency (OIT) on, that phase is the OIT one (our pipelines have no OIT variants), so
  panels go to the always-on-top phase instead (`alwaysOnTopGizmos`, which NeoForge renders whenever it has submits);
  that costs two full-screen passes (its own depth, then the merge), ~1.5 ms at 3024×1898 on the owner's Mac, which is
  why it isn't the default. One submit order per layer keeps them in sequence: beams, cone, scan dots and halo
  (additive) → glass, text beds and fills → GUI sprites → items → text (vanilla `SEE_THROUGH` text, always with a
  shadow) → additive top glow (frame, corner brackets, glints, scan line, header glow, carets, press ring). Frame lines keep their on-screen thickness while the unfold squashes the panel.
- Pipelines (registered optional; if either fails, K stays flat):
  - `awaken:pipeline/holo_glass`: translucent, full-bright, no depth test, writes depth (later translucent world
    stays behind it, and Lens, which reads depth, sees a panel at its real distance). PARTICLE format; UV0 = panel
    units, UV2 = panel size. The shader draws the glass (alpha 0.38 at the top to 0.50 at the bottom), a top sheen, a
    soft inner edge glow, a glint every nine seconds, static scan lines, a slow shimmer, a ~1.5 framebuffer-pixel
    hairline edge (`fwidth`), corner brackets and a static grain. UV2 = 0 is a flat fill; text beds are flat fills
    whose outer 7 units fade to clear.
  - `awaken:pipeline/holo_glow`: true additive (ONE, ONE; alpha untouched), no depth. Shapes by UV2: flat, halo
    around a w×h rectangle, round blob, line.
- Items: drawn through `OnTopCollector`, which routes an item's submits into the same phase (flattened and lifted 10
  units off the glass so its depth never hides them). They light exactly like an inventory slot: the level keeps its
  own two lights, and the item normals go through the transpose of vanilla's GUI light pose (`Lighting` ITEMS_FLAT /
  ITEMS_3D), since dot(R·L, n) = dot(L, Rᵀ·n). (In the Nether the second level light differs; 3D items shade a little
  off there.)
- `HoloCanvas` is a display list (fills, text, items, sprites, clip stack), preallocated; recording allocates nothing.
  Clips cut fills and fade text and items over 7 units at the top and bottom edges. `HoloRender` reuses its submit
  records; a text line's record is rebuilt only when its position, colour or scale changed.

## One layout, two canvases (signal)
- `Ui` draws through `UiCanvas` (fill, gradient, text, item, sized item, sprite, tooltips; lines and outline). The
  build's later `Ui.card` / `itemAt` / `recipe` / `Flow.item` draw through it too (`itemSized` makes GuiCanvas's same
  pose push, translate, scale and `item` calls as the code it replaced). `GuiCanvas` forwards
  every call to the same `GuiGraphicsExtractor` method as before. Pixel check: autopilot `screen uiprobe` (every
  `Ui`/`Glass` primitive and the Truths tab on black, nothing world-dependent) shot on the old and the new build is
  identical at GUI scale 0, 2 and 3, except a toast whose text differs because the starter power is random. (The
  system.txt and map.txt tours can't be compared pixel for pixel: the spawn point, gate suggestions and timers differ
  from run to run; where their panels are opaque the diff is black.) `Glass` has canvas overloads;
  the old `GuiGraphicsExtractor` ones delegate. `Ui.fill` / `Ui.item` / `Ui.canvas()` replace the three `ui.g()` uses.
- `HoloGraphics` is a `GuiGraphicsExtractor` that records onto a panel (fills, gradients, lines, outlines, text,
  sprites, items and their decorations, scissors, tooltips, pose translate and scale). Every Status section runs on the
  panel through it unchanged, including the three that draw straight to the vanilla graphics (the Gate Map, Powers, the
  Descent ledger). Calls it doesn't record go to a private render state that is never drawn.

## Input
While the K menu is open the camera holds still. The cursor is a ray through the inverse of
`Camera.getViewRotationProjectionMatrix`, met with the panel's plane as last drawn; the hit is panel units for
`Ui` hover and click. A click shows a ring of light where it landed. Tooltips appear at the real cursor.

## The wrist
- First person: `RenderHandEvent` at **HIGH**, only while a wrist panel is opening or open: cancels both hands, so
  the Awakened Body's hand poses (NORMAL), Lucidium's reality check (NORMAL) and CastAnims (LOW) don't run (NeoForge's
  bus skips cancelled events for listeners that didn't register with `receiveCanceled`). Draws the forearm with
  `AvatarRenderer.renderLeftHand/RightHand` (the player's skin, sleeve, wide or slim model) at 0.64 scale, posed by its
  own basis (elbow → hand along model +Y, running in from beyond the lower-left edge; the inside of the wrist toward the
  eye). ModelParts render in blocks (their cubes divide by 16); the band is built in model units on top: the strip in
  the glass pipeline, lines, nodes, pulse, diamond and flare in the glow pipeline, only the faces that look at the eye
  (the hand pass gives us no depth to sort them).
- **Closing gives the hands back at once.** When the close begins both hands are set to vanilla's lowered re-equip
  height (`LocalPlayer.itemUsed`, exactly where the menu left the main hand); from the next client tick the hand events
  go through untouched, so vanilla raises the hands and every other hook (a cast's arm, a slide's braced hands, the
  reality check) draws as usual while only the lowering forearm is still drawn (in push/pop, so the pose stack the
  hooks share is never changed). The off-hand item stays down until the forearm has gone. Tour:
  `tools/autopilot/holo-hands.txt` (a cast under way as the menu closes, a cast and a slide right after it).
- Third person: `AWAKEN_HOLO_WRIST` (`HumanoidModel.ArmPose` enum extension, `IArmPoseTransformer`): the forearm
  across the chest, blended by the raise. A render-state modifier sets it on the off-hand arm; a player render layer
  draws the band, a brighter wrist glow toward the viewer and, for other players, the content-free hologram card
  beside the body on the wrist's side with its beams (vanilla lightning blend, depth-tested).
- The wrist is the off-hand arm (left for right-handed players).

## Network: `awaken:holo_state`
- C2S `{kind}` (NONE 0, STATUS 1, INVENTORY 2) as a raw VarInt; unknown ids are refused. Sent when the K menu (or the
  wrist demo) opens or closes, only to a server that knows the channel.
- Server: at most 4 changes per 20 ticks per player; a change over the limit waits and goes out when the window frees
  (the last state always arrives). Relayed as `awaken:holo_state_relay {entityId, kind}` to players tracking the sender
  (never the sender, never players without the channel), and to a player who starts tracking someone with a panel open.
  Reset on respawn and dimension change, forgotten on logout. Nothing is saved.

## HUD motion
`RenderGuiLayerEvent.Pre` (LOWEST) pushes a translate, `Post` (HIGHEST) pops it, around every `awaken:*` layer except
`power_fx_screen`, `fauna_reveal_pulse` and `veil` (full-screen or crosshair-locked). The offset is a spring's trailing
offset (driven only by turn speed) × 0.34 px/°, soft-limited to 2 GUI px (SUBTLE, default) or 4 (FULL), × Screen Effect
Scale, 0 with View Bobbing off, snapped to framebuffer pixels. A layer cancelled after the push is popped at the end of
the frame.

## Cost (autopilot `perf`, 3024×1898, quiet Mac, load ~4–5, window visible)
| Scene | p50 | p99 |
|---|---|---|
| nothing open | 8.51–8.80 ms | 10.56–11.25 ms |
| flat K menu | 8.74–9.03 ms | 11.37–11.41 ms |
| holo K menu | 8.80–9.28 ms | 11.00–12.98 ms |
| demo panel, no screen | 8.60–8.82 ms | 11.20–11.92 ms |

The holo K menu costs 0.06–0.25 ms more than the flat one (the layout is the same; the rest is the panel), within the
0.3 ms budget. Two panels at once were not measured.

## P4 seam (not built)
Urgent System cards as holo panels would be a `HoloPanel` with `Anchor.VIEW` recorded through `Ui.draw(UiCanvas, ...)`
(`HoloCanvas` already takes any `Ui` content), with the System v2 rules (SPEC §13) deciding when one shows.

## Settings (`awaken-holo-client.toml`)
`statusStyle` HOLO | FLAT · `hudMotion` OFF | SUBTLE | FULL · `reducedMotion` false.

## Tests and tours
- GameTests: `holo_state_rejects_unknown_kind`, `holo_state_rate_limited`, `holo_state_relays_only_to_trackers`.
- `tools/autopilot/holo.txt`: the demo panel, the HUD still and in a sharp turn (Subtle / Off / Full), the K menu
  frozen at 60, 120, 200, 280, 360 and 450 ms, a tab clicked through the real picking path, the menu over a bright
  sky, grass and the night, wide and slim arms, third person back and front, what others see (`holo peek`), then
  `perf holo_open` / `perf holo_none`.
- `tools/autopilot/holo-hands.txt`: a cast and a slide right after the menu closes, beside the same with no menu.
- Autopilot lines added: `turn <deg/tick> <ticks>`, `click <x> <y>`, `screen holo [map|codex|truths]`,
  `screen uiprobe`, `holo demo|at|run|close|hud|arm|peek|clicktab|log`.
