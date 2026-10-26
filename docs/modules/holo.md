# Holo UI: the System on your wrist

Design: docs/proposals/holo-ui.md. Package `dev.awaken.holo` (common: the relay, the sound events),
`dev.awaken.holo.ui` (side-agnostic rules the client follows and GameTests check: timelines, inventory wells, the
hover tick, the HUD fade) and `dev.awaken.holo.client` (everything you see). Built: Phase 1 (panels, pipelines,
spring, HUD motion, demo, tour), Phase 2 (the K menu on the wrist, the third-person pose, the relay, the `UiCanvas`
split), Phase 3 (the inventory) and the first part of Phase 5 (sounds, the hover tick, the tier-1 HUD fade). Not built
yet: System cards as holo panels (P4, waits for the owner), the rest of P5 (accessibility settings beyond reduced
motion, the pack perf tour with panels open).

## What the player sees
- **K** (with `statusStyle = HOLO`): the off-hand forearm comes in from the lower left (the elbow off-screen), the
  inside of the wrist turned to the eye. Around the wrist sits the System's band, flush with the skin: a thin dark strip
  with two emissive lines, small segment nodes with a slow travelling pulse and the System's diamond on the inside of
  the wrist. The band flares; two beams and a faint projection cone open from the diamond toward the panel's lower
  corners (brighter and wider for the first ~200 ms, with scan dots running up them), and the panel grows out of the
  beams' origin into its place, unfolding (height, then width); its frame draws itself, a scan line sweeps down and the
  text types in with a caret. It rests up and to the right of the band, left of the crosshair (about 45% of the
  screen's width), yawed toward the wrist, and trails your view when the camera turns. Esc or K folds it back into the
  band (250 ms). K while it opens skips to open. The panel's wrist edge carries the rail of the band's six pages (SPEC §30:
  Status, Powers, Journal, Truths, Codex, Map; `holo.ui.BandLayout`); 1–6 open a page, Tab and Shift+Tab step through
  them, and M (the Map) closes the band and opens the Atlas table. K always opens on Status. The main hand (or its item) sinks out of view
  while the arm is up, exactly as vanilla lowers it on an item swap.
- **The panel is light, not a slab**: 38–50% glass (denser toward the bottom, where it is projected from), static scan
  lines three units apart, a slow two-wave shimmer (no strobing), a hairline emissive edge with corner brackets and an
  additive halo around it. Text is emitted light, not print: no drop shadow; each line has four faint cyan copies
  half a unit off diagonally under it (20% alpha, a soft halo on every stroke), body text cyan-white, labels pale blue,
  headers System blue with a soft additive glow. Readability comes from one even tint over the whole content area
  (38% near-black on top of the glass, feathered over 14 units toward the rim, no per-line boxes); the panel is 288×228 GUI units (SPEC §30; content beside a 30-unit rail) at
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

Lens focus (`UiHooks.focus()`) eases from 160 to 450 ms. The inventory plays the same choreography 2.5× faster
(`HoloTimeline.INVENTORY_SPEED`: open in 180 ms, closed in 100 ms); 370–450 along it (148–180 ms) is where its 3D
panel has landed and hands over to the flat screen (`HoloTimeline.landed()`).

## Placement and the spring
- A panel's size is chosen in **whole framebuffer pixels per unit** (about 45% of the screen width, at most 80% of the
  height): 1 unit = 1 GUI pixel of layout, so `Ui` sections fit unchanged and text stays crisp (at 3024×1964 the
  288×228 K panel draws at 5 px per unit, 6 at 3840×2160). Recomputed every frame from the level projection, so sprint FOV and cast
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
  (additive) → glass, the content tint and fills → GUI sprites → items → text glow copies → text (vanilla
  `SEE_THROUGH` text, no shadow) → additive top glow (frame, corner brackets, glints, scan line, header glow, carets, press ring). Frame lines keep their on-screen thickness while the unfold squashes the panel.
- Pipelines (registered optional; if either fails, K stays flat):
  - `awaken:pipeline/holo_glass`: translucent, full-bright, no depth test, writes depth (later translucent world
    stays behind it, and Lens, which reads depth, sees a panel at its real distance). PARTICLE format; UV0 = panel
    units, UV2 = panel size. The shader draws the glass (alpha 0.38 at the top to 0.50 at the bottom), a top sheen, a
    soft inner edge glow, a glint every nine seconds, static scan lines, a slow shimmer, a ~1.5 framebuffer-pixel
    hairline edge (`fwidth`), corner brackets and a static grain. UV2 = 0 is a flat fill; the content tint is a flat
    fill whose outer 14 units fade to clear.
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
  panel through it unchanged, including the three that draw straight to the vanilla graphics (the gates' run summary, Powers, the
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
  wrist demo) or the holo inventory opens or closes, only to a server that knows the channel. Others see the same
  raised wrist and content-free card for either kind.
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

## The calm HUD, tier 1 (`CalmHud`, `holo.ui.HudFade`)
Awaken's always-on HUD beside the hotbar (`awaken:power_bar`, `awaken:level`) fades to **35%** once nothing has needed
it for **3 s** (60 ticks), over 1.2 s, and comes back to full in **150 ms** the moment something does. "Needed", read
each tick from public APIs only (no module changed):
- combat as the System judges it (`SystemHud.inCombat()`: hurt in the last 5 s, or something hostile near);
- the loadout (`Powers.clientLoadoutOrNull()`): energy below full, a cooldown running, a power active, a slot changed;
- the profile (`Progression.profile`): level, XP or unspent points changed (fresh XP is worth a look).

The layers themselves are untouched. In `HudMotion.pre` (after its translate) a faded tier-1 layer's event is
cancelled and the layer is drawn through `FadeGraphics`, a `GuiGraphicsExtractor` on the frame's own GUI render state
(from the real graphics' pose) that multiplies the alpha of every fill, gradient, text, sprite and blit; then its
`Post` is fired as the layer manager would (which pops the translate). Item icons can't fade (none in tier 1). The gate
run HUD is not tier 1: inside a gate the run is the context, and its collapse timer must never dim. Off with
`hudFade = false`.

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

## External textures on a panel (`UiCanvas.texture`)
`void texture(Identifier texture, int x, int y, int w, int h, float u0, float v0, float u1, float v1, int argb)` on
`UiCanvas` (and `HoloCanvas.textureF(...)` with float coordinates): part of a whole texture, tinted, as one quad in
the panel's display list (at most 16 a frame, drawn with the sprites; no allocation per frame; a submit is made only
when a slot's texture changes). The texture is any Identifier the TextureManager knows: a `DynamicTexture` or a
registered render target, updated by its owner only when its picture changes. `GuiCanvas` draws the same texture and
UVs with a plain blit, so the flat fallback shows it too; `HoloGraphics` records it like any other call.
```java
// in a section's render, on whichever canvas it was given (ui.canvas())
canvas.texture(ATLAS_DIORAMA, x, y, 96, 64, 0f, 0f, 1f, 1f, 0xFFFFFFFF);
```
Tour: holo.txt `holo tex on` shows a map's parchment on the demo panel (holo_07_demo_texture).

## Focus (`core.api.UiHooks`)
`UiHooks.setFocus(Object owner, float value)` reports one source's focus (0..1), `UiHooks.clearFocus(Object owner)`
drops it, and `UiHooks.focus()` is the largest of all sources (Lens dims the world by it). Owners are compared by
identity and take one of 8 slots the first time they report above 0, so reporting every frame allocates nothing.
Holo reports one value each frame (the K menu, the wrist demo, an open reading screen). Toasts, cards and the Now line
never report. `installFocus(DoubleSupplier)` still works and counts as one more source.

## World plates (`HoloWorld`, `holo.ui.PlateScale`; SPEC §12)
- Small panels at a world point, facing the camera: the living module's creature plates first. Each is a real
  `HoloPanel` (anchor WORLD, layers 3–8) with its own `HoloCanvas`, drawn by `HoloRender.submitPlate`, which is
  `submitPanel` unchanged: the same glass shader, halo, edges and brackets, scan line and see-through text with its glow.
  No panel, card or HUD output changed when plates arrived (only additions: `submitPlate`, one more submit listener).
- Queue during `ExtractLevelRenderStateEvent` (`plate(spec, content)`, at most 6); `HoloWorld.submit` places them in the
  same frame's level pass from the camera's position and orientation (as vanilla's name tags: no bob undo, they sit in
  the world) and empties the queue.
- Size: `PlateScale.unit` = max(0.021 m, 2.1 px × fbH/1080 × metres per pixel at that distance): world-sized up close,
  constant on screen from about 10 blocks out, so 9-unit text is never tiny. Tested by `holo_plate_scale`.
- Off with `reducedMotion` (`allowed()`), or without the world pipelines (`on()`); never reports `UiHooks.focus`.

## System cards (`signal.client.HudLook`, `HoloGuiLook`)
The System's cards keep their flat layout, positions, sizes and timing (SignalLayout, ChoiceDeck, ToastBurst, the
vanilla ToastManager) and only change how they draw. The holo module installs `HoloGuiLook` into signal's `HudLook`; it
draws in the GUI pass with the panel's own `holo_glass` / `holo_glow` shaders on two GUI pipelines
(`awaken:pipeline/holo_gui_glass`, `holo_gui_glow`, PARTICLE format through `submitGuiElementRenderState`), so a card
has the panel's glass, hairline edge, static scan lines and shimmer, one even 40% tint, a faint halo (brighter while it
scans in) and text as light (four cyan copies half a GUI pixel off at 30%, no shadow). Styles (`HudLook.FRAME`,
`DRIFT`, `EMITTER`): FRAME adds the K panel's own frame, its additive rim lines and corner brackets just outside the
card (the bottom ones once the scan has reached them); DRIFT a soft interference band, 10 units tall with a bright line and two
faint ones trailing it, that drifts down through the card once every 6.5 s (still, a third of the way down, with reduced motion); EMITTER the edge the card is
projected from, a bright line with a soft skirt that flares as the card arrives. Notices (choice and Accept card, gate
panel, story line) are FRAME + DRIFT, toasts add EMITTER on their screen side, the Now line is FRAME only (no band, no
flicker: the calm HUD), the first-power panel FRAME and its cards plain tiles.
- Toast (`SignalToast`, TOAST = FRAME + DRIFT + EMITTER): same 160-wide slot; no slide (`xPos` fixed): vanilla's 600 ms show becomes a scan-in done by
  270 ms (the text appears as the line passes), its hide a glitch-out in 300 ms. Quiet screens still hide toasts.
- Choice card and gate panel (`SystemHud`): scan in over the 4 ticks they used to fade in; the gate panel glitches out
  over its 6-tick close. The fold timer line and the keys' arming (8 ticks) are unchanged.
- Story lines: a projected strip behind the words, scanned in with the first page, glitched out with the fade.
- The Now line (`TrackerHud`): a still card, never animated beyond its own fade on change.
- The first-power choice (`StarterScreen`, a `ReadingScreen`): the panel scans in over 320 ms, then the three cards one
  after another (70 ms apart); the hovered or picked card lights in its family colour. Clicks and 1 / 2 / 3 are as
  before. Lens focus eases to 1 while it is open (300 ms each way).
- Reduced motion: scan-ins and glitches become fades. `statusStyle = FLAT`, or GUI shaders that fail, keeps every
  card exactly as it was. Autopilot: `holo style holo|flat|auto`, `shotnow <name>` (a shot this tick, for the 6-tick
  glitch-outs); under the autopilot (only) a picked first power stays on screen 12 ticks so the tour can shoot it. Tour
  `tools/autopilot/holo-cards.txt` (starter scan, open, picked; toast scan, sky, grass, glitch; a real gate panel put
  away with N; choice; story; night; flat).

## The inventory (P3: `HoloInventoryScreen`, `InventoryLook`, `holo.ui.InventoryWells`)
- **What opens.** E (or anything that opens vanilla's survival `InventoryScreen`) gets `HoloInventoryScreen`, a
  subclass swapped in on `ScreenEvent.Opening` when `inventoryStyle = HOLO`, the projected look is drawing
  (`statusStyle = HOLO`, GUI holo shaders compiled) and the player isn't creative. Only the exact vanilla class is
  swapped (another mod's inventory screen stays theirs); creative keeps its own screen. Underneath it is vanilla's
  screen, so every click, drag-spread, shift-click, double-click, number key, the off-hand key, the recipe book, the
  creative switch and every other mod's container hooks (JEI, Mouse Tweaks) behave exactly as vanilla, from the first
  frame: the slots are where they always are, whatever is drawn.
- **The unfold.** The wrist rises and a 3D panel (the third `HoloPanel`, `onScreen`: a plane on which one unit is one
  GUI pixel, parallel to the screen, never trailing) grows out of the band along the beams, unfolding, and lands on the
  flat screen's rectangle pixel for pixel. At 148–180 ms the flat face crossfades in as the 3D panel's light goes out;
  items, your figure and the title (which can't fade) switch at the halfway point, where both faces show them in the
  same place. Closing hands back: the 3D panel shows again with what the inventory holds now (recorded at close) and
  folds into the band in 100 ms while the hands are vanilla's again. With reduced motion, or without the world
  pipelines, there is no 3D panel: the flat face fades in place. A faint steady link stays from the wrist to the flat
  panel's lower corners while it is open.
- **The look** (one drawing for both faces, `InventoryLook`, through `GuiCanvas` flat and `HoloGraphics` in 3D): the K
  panel's glass, tint, halo, rim and 18-unit brackets (`HoloGuiLook.panel` draws HoloRender's settled panel unit for unit
  in the GUI pass); in place of vanilla's printed slot squares a projected well under every active slot (18×18, the
  crafting result 26×26 with corner marks; dark glass at 58%, a hairline rim at 22%, the hotbar's at 32%, the hand's
  slot at 85% with a glow); your figure in a framed alcove standing on a projected floor (a soft blob of light); an
  arrow of light from the grid to the result, which glows while it holds something; two faint dividers (equipment /
  pack / hotbar); the crafting title as light; a soft glow under the slot the cursor is on; the recipe button a glass
  key with an open book traced in light (`HoloBookButton`: vanilla's button stays the one pressed, the key follows it).
  No dim and no blur: the world stays in view and Lens dims it (`UiHooks.focus`, eased with the timeline).
- **Not restyled (yet):** the recipe book's own panel, the status-effect list beside the inventory, vanilla's white
  hover highlight (private in `AbstractContainerScreen`; our glow sits under it).
- **Fallback:** `inventoryStyle = VANILLA`, `statusStyle = FLAT` or GUI shaders that fail: vanilla's `InventoryScreen`,
  untouched.

## Sounds (P5: `HoloSounds`, `HoloAudio`; `tools/art/specs/audio_holo.py`)
One glass voice with the System's UI family (`audio_ui.glass_note`, its small bright room and air), every note from
D major pentatonic, so nothing here clashes with a toast or a card; what is new is a breath of filtered air sweeping as
light leaves the wrist and drawn back as it folds. Mastered to the UI loudness (−24 ± 3 dBFS RMS, true peak ≤ −3),
stereo, Interface slider, non-positional.

| Event | File | When | Sound |
|---|---|---|---|
| `holo.open` | `ui_holo_open` | the K menu opens (vol 0.5) | air rising with the arm, a glint as the band flares (140 ms), D5 F#5 A5 D6 as the panel grows and unfolds, a faint B6 with the scan line, D-A left open |
| `holo.open_quick` | `ui_holo_open_quick` | the inventory opens (0.3) | the same gesture at 2.5×, lighter (it opens often) |
| `holo.close` | `ui_holo_close` | either folds back (0.4 / 0.22) | D6 A5 F#5 D5 falling, the air drawn in, a soft low knock as it meets the band |
| `holo.hover` | `ui_holo_hover_1/2` | the cursor comes onto something (0.18) | one tiny glass tick, A6 or D7 |
| `holo.card` | `ui_holo_card` | a System card arrives while projected | `ui_card`'s own D5 A5 E6 with the scan sweeping down through it and a small fizz at its edge |

- **Card arrival:** `signal.open` (the signal module plays it as a choice card or gate panel arrives) is re-voiced on
  `PlaySoundEvent` as `holo.card` while the projected look draws, so a card sounds the way it looks and never twice;
  flat, it is `signal.open` as before. Toasts keep their chime.
- **Hover tick** (`holo.ui.HoverTick`): once as the cursor comes onto a new target (a K-menu tab; an inventory slot with
  something in it, or the recipe key), at most every 55 ms (a sweep across the hotbar ticks 3-4 times, not nine), never
  while the panel still unfolds (the K menu before it takes clicks, the inventory before its face is up) or while a drag
  spreads items.
- Opening sounds only on a real open (not a re-layout on resize); closing only when it was open.
- Rebuild: `python3 tools/art/audio_build.py ui_holo_`, then `audio_mix.py`; spectrograms with
  `python3 tools/art/audio_sheet.py out.png ui_holo_ --cols 2`.

## Settings (`awaken-holo-client.toml`)
`statusStyle` HOLO | FLAT · `inventoryStyle` HOLO | VANILLA · `hudMotion` OFF | SUBTLE | FULL · `hudFade` true ·
`reducedMotion` false.

## Tests and tours
- GameTests: `holo_state_rejects_unknown_kind`, `holo_state_rate_limited`, `holo_state_relays_only_to_trackers`;
  `holo_inventory_timeline` (the K menu keeps 450/250 ms; the inventory opens in ≤ 180 ms and closes in ≤ 100 ms at
  60 fps; the flat face only comes up once the 3D panel is in place), `holo_inventory_wells` (on a real player's menu:
  every slot has its kind, every well surrounds its item, stays inside the 176×166 panel, clears the figure and never
  overlaps another; exactly one well is the hand's), `holo_hover_tick`, `holo_hud_fade`. The sound events are checked by
  `audio_events_resolve` (registered, in sounds.json, subtitled, files at 44.1 kHz).
- `tools/autopilot/holo-inventory.txt`: the inventory frozen along its unfold (both faces, the crossfade), at rest over
  sky, grass and night, an item carried and dropped into the crafting grid (the result lit), the recipe book open and
  closed, the fold, third person, the vanilla style and the flat look; the HUD faded and woken; `perf` with it open.
- `tools/autopilot/holo.txt`: the demo panel, the HUD still and in a sharp turn (Subtle / Off / Full), the K menu
  frozen at 60, 120, 200, 280, 360 and 450 ms, a tab clicked through the real picking path, the menu over a bright
  sky, grass and the night, wide and slim arms, third person back and front, what others see (`holo peek`), then
  `perf holo_open` / `perf holo_none`.
- `tools/autopilot/holo-hands.txt`: a cast and a slide right after the menu closes, beside the same with no menu.
- Autopilot lines added: `turn <deg/tick> <ticks>`, `click <x> <y>`, `screen holo [map|codex|truths]`,
  `screen uiprobe`, `holo demo|at|run|close|hud|arm|peek|clicktab|log`; `holo inv open|click <slot> [shift]|book|close|style holo|vanilla|auto`,
  `holo fade on|off|auto|idle` (`holo at`, `run`, `close` and `log` act on the inventory while it is the open panel;
  its `log` adds where the landed panel's corners project against the flat rectangle).
