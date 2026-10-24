# Holo UI: the System on your wrist

Status: requested by Euan on 2026-10-24. Building it is approved in his words: "Really think through the most PEAK
architecture here and then set someone on building this". Branch `pack/holo-ui`, worktree /Volumes/MEDIA/Awaken-holo.

> Euan: "a style of UI where it looks like a floating screen, so it kinda lags behind the camera just slightly as if
> it was in 3D in the world... Same with the 'K' menu... the character puts his hand/wrist out and keeps it there to
> 'spawn' the K menu. Then possibly even re-work the inventory to look really cool like this too. But yeah the
> always on HUD can't be TOO distracting."

## The experience

You press **K**. Your left forearm comes up into view, wrist turned toward you. A thin band of System light on the
wrist flares, and a panel unfolds out of it: edges first, then a scan line, then the text typing in. It settles in
front of you, a little to the left, tilted toward your eyes, a real object in the scene. Turn your head as it opens
and it swings after you, then settles. Close it and it folds back into the band. The arm lowers.

A friend standing next to you sees your arm come up and a soft glow over your wrist, but not what's on it.

The everyday HUD stays calm. Power slots and energy fade when you're out of combat and full, and drift no more than a
few pixels when you turn sharply. Messages that aren't urgent no longer pop a toast: the band on your wrist pulses
and they wait in the K menu. Only things that matter now (a gate about to break, a choice the story needs) float up as
a small holo card, one at a time.

## Principles

1. **Diegetic in motion, precise at rest.** Panels move like objects while they open, close or follow your view.
   While you read or click, they hold still, and clicks land exactly where you aim.
2. **Write it once, show it anywhere.** Every K-menu section is already written once against Awaken's own `Ui`
   toolkit (signal/client/Ui.java), which measures, draws and handles clicks from the same code. Give `Ui` a canvas it
   draws through, and the same sections render flat in 2D or on a 3D panel. Nothing gets rewritten twice, and the
   flat screen remains the fallback.
3. **Never slow down a frequent action.** The inventory opens in at most 180 ms, and clicks work from the first frame.
   The K menu may take 350–450 ms because it's opened less often; pressing K again skips the animation.
4. **Calm by default.** HUD motion is driven only by how fast the camera turns, never constant. It stays within a few
   pixels and settles in about 200 ms. A setting (Off / Subtle (default) / Full) covers it, and it follows vanilla's
   Screen Effect Scale and View Bobbing.
5. **Pay only for what's on screen.** No allocation per frame, nothing drawn for a closed panel, and at most 0.3 ms a
   frame with two panels visible at 2880×1800.

## Architecture

```
            ┌────────────── holo (new client module, dev.awaken.holo) ──────────────┐
 K key ───▶ │ HoloScreen ── open/close timeline ── Spring (view lag)                │
 E key ───▶ │   │                                   │                              │
            │   ├─ HoloPanel (anchor: WRIST | VIEW | WORLD; size in panel units)    │
            │   │     └─ HoloCanvas implements UiCanvas ── SubmitNodeCollector      │
            │   │           quads (custom pipelines: glass, additive glow)          │
            │   │           text  (Font, SEE_THROUGH), items (submitItem)           │
            │   ├─ HoloInput: mouse ray → panel plane → panel coords → Ui click     │
            │   └─ Wrist: first-person arm and band (RenderHandEvent, HIGH, cancel) │
            │ HudMotion: RenderGuiLayerEvent Pre/Post on awaken:* layers (sway)    │
            │ Net: holo_state C2S → relay S2C to trackers (others see the pose)     │
            └───────────────────────────────────────────────────────────────────────┘
 signal/client/Ui ── draws through UiCanvas ──▶ GuiCanvas (today's 2D, unchanged pixels)
                                           └──▶ HoloCanvas (3D panel)
 lens ◀── Holo.focus() 0..1: Lens dims (Swift) or blurs (Cinematic) the world behind an open panel
```

### 1. Panels in 3D space
- A `HoloPanel` has a size in panel units (1 unit = 1 GUI pixel at the reference scale, so existing `Ui` layouts fit
  as they are) and an anchor:
  - `VIEW`: camera-relative, behind a spring;
  - `WRIST`: follows the first-person forearm through its open and close animation, then hands over to `VIEW`;
  - `WORLD`: a fixed position, used for the glow other players see over your wrist.
- Drawing happens once a frame through `SubmitCustomGeometryEvent` and SubmitNodeCollector's `submitText`,
  `submitTextBackground` and `submitItem` (all present in 26.3).
- Panels draw over the world with no depth test, so walls never cut them: text uses `Font.DisplayMode.SEE_THROUGH`,
  and quads use custom pipelines registered through `RegisterRenderPipelinesEvent`:
  - "glass": translucent, fullbright, with an edge highlight;
  - "glow": true additive, which Awaken has never had (docs/modules/structures.md notes it as unbuilt).
- Because panels draw before post effects, Lens blooms their glow.
- View-space placement compensates for FOV changes (sprint, cast punches) so panels don't swell. View bobbing does
  not move panels.

### 2. The spring
- A critically damped second-order spring on yaw and pitch: the panel's target follows the camera, and the panel
  trails it.
- Lag stays under 4°, and the panel settles in 150–250 ms.
- It's preallocated, uses plain floats, and has one state per active panel.
- With reduced motion, the spring is replaced by a direct follow.

### 3. One layout, two canvases (the change in signal)
- `Ui` today calls `GuiGraphicsExtractor` directly in DRAW mode. Introduce `UiCanvas`, with fill, text, item and
  sprite calls, plus two implementations:
  - `GuiCanvas`, which keeps today's 2D output pixel for pixel;
  - `HoloCanvas`, which writes the same primitives into the panel's pose.
- MEASURE and CLICK modes don't change. CLICK gets its coordinates from HoloInput in panel units.
- Three call sites use `ui.g()` directly (CoreSections, RequestSection). Move them onto canvas calls.

### 4. Input
- While a HoloScreen is open, the camera holds still. The cursor position becomes a ray through the inverse of
  `Camera.getViewRotationProjectionMatrix`, the ray meets the panel's plane, and the hit becomes panel coordinates.
- Those coordinates feed `Ui.click`, hover and scroll.
- Keyboard: Tab or number keys switch tabs, Esc or K closes.
- A hover tick sound and a press flash give feedback.

### 5. The K menu
- `HoloStatusScreen` hosts the same tabs and sections as StatusScreen (StatusSections' registry) on a WRIST→VIEW
  panel. The flat StatusScreen remains as a client option (`statusStyle = HOLO | FLAT`), and the automatic fallback
  if a holo pipeline fails to compile.
- Timeline:

  | Time | What happens |
  |---|---|
  | 0–120 ms | the arm rises |
  | 80–200 ms | the band flares |
  | 160–360 ms | the panel unfolds (scale on Y, then X; the scan line sweeps) |
  | 300–450 ms | text types in at Signal's speed, ×4 |

  The screen accepts clicks from 250 ms. The close plays the same in reverse in 250 ms.
- `isPauseScreen` stays false, as today.

### 6. The wrist
- **First person:** a `RenderHandEvent` listener at HIGH priority. While a holo menu is open or animating, it
  cancels the vanilla hand and draws the left arm in its own pose, with the System band in the glow pipeline.
  Cancelled events skip lower-priority listeners, so CastAnims (LOW) and Lucidium's reality check (NORMAL) simply
  don't run then, with no changes to either.
- **Third person, and other players:** a custom arm pose through NeoForge's arm-pose extension (verify the 26.3 hook)
  on the vanilla player model. It doesn't need the Player Animation Library. When the library is installed, a clip can
  replace it later through the powers module's single library class.
- **Network:** `awaken:holo_state` carries `{kind: NONE | STATUS | INVENTORY}` from client to server, rate-limited to
  4 a second. The server relays it to players tracking you. Others see the pose and a small, content-free glow over
  the wrist.

### 7. The inventory
- `ScreenEvent.Opening`: a vanilla `InventoryScreen` is swapped for `HoloInventoryScreen extends InventoryScreen`, so
  JEI, Mouse Tweaks, Inventory Profiles and every container behaviour keep working.
- It's a holo-styled 2D screen:
  - a glass background with an edge glow instead of the vanilla texture;
  - slot frames in System blue;
  - the world dimmed behind it through Lens focus.
- Transition: a 3D snapshot panel drawn from the player's real inventory data (the slot grid and item icons through
  `submitItem`) unfolds from the wrist in 160 ms. It lands exactly on the 2D screen's rectangle, which fades in at
  the same moment. The close plays in reverse. Clicks are live from the first frame.
- Creative mode keeps its own screen. Option: `inventoryStyle = HOLO | INSTANT | VANILLA`.
- A fully 3D inventory you click in space comes later and stays an experiment behind a toggle, because it would break
  mods that draw on container screens.

### 8. The always-on HUD
| Tier | Elements | Behaviour |
|---|---|---|
| 0 | Vanilla hotbar, hearts, hunger, crosshair | Unchanged and still |
| 1 | Awaken's combat HUD (power slots, energy, gate-run HUD) | Fades to 35% when out of combat and full. Subtle sway, at most 4 px, from turn speed only |
| 2 | Urgent System cards (gate breaking, story choices) | VIEW-anchored holo cards, one at a time, upper left. They respect the existing quiet queue |
| 3 | Everything else the System says | A pulse on the wrist band, then the K menu's log |

- HudMotion wraps `awaken:*` GUI layers in `RenderGuiLayerEvent.Pre/Post` (push a translate, then pop), so it changes
  no module.
- Fading by context needs each layer's state, so modules opt in through a small `HudMotion.fade(layer, alpha)` call.

## Phases (each one ships on its own)
- **P1 Foundation (holo module only, no shared files):**
  - Spring, HoloPanel, HoloCanvas primitives and the glass and glow pipelines;
  - a `/awaken holo demo` panel;
  - HudMotion sway (default Subtle) with its client options;
  - an autopilot tour with screenshots.
- **P2 The K menu:**
  - the `UiCanvas` refactor in signal (integrator approval needed);
  - HoloStatusScreen and HoloInput;
  - the wrist in first and third person, the holo_state relay, and Lens focus.
- **P3 The inventory:** HoloInventoryScreen, the snapshot transition, and a compatibility check with the pack's
  comfort mods (JEI, Mouse Tweaks).
- **P4 The System's voice:** urgent cards as holo panels, and everything else to the wrist pulse and the log (signal
  module, integrator approval needed).
- **P5 Polish:**
  - sounds through the audio pipeline (unfold, fold, hover, press);
  - scan line and edge shader;
  - accessibility settings;
  - a frame-time check with the pack's perf tour (`perf` lines with the K menu open).

## Tests and acceptance
- Client rendering has no GameTests. The tour `tools/autopilot/holo.txt` covers it: K open at 0, 120, 250 and 450 ms,
  first and third person, front camera; inventory open and closed; a sharp turn with sway on and off.
- GameTests cover the holo_state payload: rate limit, relay only to tracking players, rejection of an unknown kind.
- Acceptance:
  - every StatusScreen section renders on the panel with nothing clipped;
  - clicks hit the right button (the tour clicks known button positions with a new autopilot `click` command);
  - the flat fallback still works;
  - pack perf tour with the K menu open: +0.3 ms or less;
  - the inventory with JEI installed shows JEI's panel and items, and works.

## Open questions for Euan (defaults chosen; building continues)
1. Left wrist (default) or right?
2. Should messages that aren't urgent leave the HUD entirely and go to the wrist? Default: yes, once P4 lands.
