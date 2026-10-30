# pack/holo-next: the holo inventory (P3), holo sounds and the calm HUD's tier 1 (P5)

Cloud session, 2026-10-24, branched from `build/awaken-v1` at 2e05a76 (and carrying pack/throughput's flake fixes, see
CI). Built and tested in the VM (`./gradlew build`, `runGameTestServer`: all 328 GameTests pass, 4 of them new). CI
green: run 40105621182. Nothing here has been seen on a
screen yet: every screenshot tour below needs the Mac's GPU.

## What's done
- **The inventory as a holo panel** (docs/modules/holo.md "The inventory", SPEC §13 Holo UI). E opens
  `HoloInventoryScreen`, a subclass of vanilla's `InventoryScreen` swapped in on `ScreenEvent.Opening`, so every click,
  drag, shift-click, number key, the recipe book and other mods' container hooks stay vanilla's, live from the first
  frame. It plays the K menu's choreography at 2.5× (open 180 ms, closed 100 ms): the wrist rises, a 3D panel grows out
  of the band and lands on the flat screen's rectangle pixel for pixel, and the flat face crossfades in (148–180 ms).
  Closing, the 3D panel folds back into the band holding what the inventory holds. The face: the K panel's glass,
  tint, halo, rim and brackets drawn unit for unit in the GUI pass, projected wells instead of slot squares, the figure
  in an alcove on a projected floor, an arrow of light to the result (lit when it holds something), the title as light,
  the recipe button as a glass key; no dim or blur, Lens dims through `UiHooks.focus`. Others see the wrist
  (`holo_state` INVENTORY). Flat fallback: `inventoryStyle = VANILLA`, `statusStyle = FLAT` or failed GUI shaders give
  vanilla's screen untouched; creative is never swapped. Reduced motion: the flat face fades in place.
- **Sounds** (holo.md "Sounds"): `holo.open`, `holo.open_quick`, `holo.close`, `holo.hover`, `holo.card`, synthesised by
  `tools/art/specs/audio_holo.py` (added to `audio_build.py`'s specs) in the System's glass voice, D major pentatonic,
  at UI loudness (all six files "ok" in `audio_mix.py`: −24.5 to −26.5 dBFS RMS, true peak −7.7 or lower). A card's
  `signal.open` is re-voiced as `holo.card` while the projected look draws. Hover ticks on K-menu tabs, inventory slots
  holding something and the recipe key, at most every 55 ms, never while unfolding or during a drag.
- **The calm HUD's tier 1** (holo.md "The calm HUD, tier 1"): the power bar and the level readout fade to 35% after 3 s
  with nothing needing them, return in 150 ms; read from public APIs only, drawn through an alpha-multiplying graphics
  around the unchanged layers. The gate run HUD never fades (a decision, in SPEC). Setting `hudFade`.
- `HoloTimeline` moved to `dev.awaken.holo.ui` (side-agnostic, with a speed and `landed()`), beside `InventoryWells`,
  `HoverTick` and `HudFade`, which the new GameTests check: `holo_inventory_timeline`, `holo_inventory_wells`,
  `holo_hover_tick`, `holo_hud_fade`.
- No other module's code changed by this work (pack/throughput's merged test fixes aside). Touched outside
  `dev.awaken.holo`: `tools/art/audio_build.py` (one spec added to its
  list), `docs/modules/audio.md` (one paragraph pointing here), the SPEC, and `src/main/merge/holo/`.

## Tours to run on the Mac
1. **`tools/autopilot/holo-inventory.txt`** (new). Check, in order:
   - `inv_00`–`inv_07`: the unfold reads as the K menu's, fast; at `inv_05_landed` / `inv_06_handover` / `inv_07_open`
     the 3D panel and the flat face are the same rectangle (the `holo log` lines print the 3D corners against the flat
     rectangle: they should agree within a GUI pixel). Any jump at the handover is a bug in `HoloPanel.placeOnScreen`.
   - `inv_10`–`inv_12`: readable over sky, grass and night (wells, counts, the title); nothing looks printed or vanilla.
   - `inv_20`–`inv_22`: a carried stack, the result well lit, the shift-click result (the log line says what moved).
   - `inv_30`/`inv_31`: the recipe book open (its own panel is still vanilla's: see "Left") and the inventory moved.
   - `inv_40`: the fold in 2 ticks; `inv_50`–`inv_52`: third person and the peek.
   - `inv_60`/`inv_61`: identical to vanilla's inventory.
   - `inv_70`–`inv_72`: the power bar and level at full, faded to 35%, woken by a hit.
   - `perf holo_inventory_open` vs `holo_inventory_none`: the open inventory should cost about what vanilla's does
     (only the beams remain in 3D once landed).
2. **`tools/autopilot/holo.txt`** and **`holo-hands.txt`** again: the K menu is unchanged apart from the hover tick,
   and the timeline moved package; the frozen frames should match the last run.
3. **`tools/autopilot/holo-cards.txt`**: unchanged visually (the card sound changed; sound is muted in tours).
4. **By hand, with sound on** (the autopilot mutes it): open and close the K menu and the inventory a few times
   (the unfold, the quick one, the fold); sweep the cursor across the hotbar and the K tabs (the tick, not too busy);
   raise a gate card or a choice (`/awaken signal demo choice`) in the holo look (`holo.card`) and in the flat look
   (`signal.open` as before). The spectrograms: `python3 tools/art/audio_sheet.py out.png ui_holo_ --cols 2`.
5. **With JEI installed** (the pack's comfort profile): JEI's panel and item list beside the holo inventory, its
   recipe click-through, bookmarks; Mouse Tweaks' drag and wheel moves if it is in the pack.

## Left
- The recipe book's own panel and the status-effect list beside the inventory are still vanilla's look (and vanilla's
  white hover highlight sits over our glow; it is private in `AbstractContainerScreen`).
- Other containers (chests, furnaces, crafting tables) are untouched by design; a holo look for them would be the next
  step on the same pieces (`InventoryWells` places a well under any slot).
- P4 (System cards as holo panels) still waits for Euan; the rest of P5 (accessibility settings beyond reduced motion,
  the pack perf tour with panels open).

## CI
- **Green: run 40105621182** on ae25126 (https://github.com/euansprr-dev/awaken/actions/runs/40105621182). Locally, all
  328 GameTests pass on every commit pushed.
- Before it, the runs on this branch failed only on the rotating timing flakes the cloud hand-off lists for
  pack/throughput, never on a holo test: 40102755874 (gates_surface_respects_builds, twice; then also
  gates_interior_build_budget), 40104258373 (fauna_p3_caves_spawn and fauna_p3_talents, then terrain_hero_blueprints on
  its re-run). The same tests fail on build/awaken-v1 and the other pack branches today.
- So this branch **merges pack/throughput** (ae25126, its exact commits, green on its own CI three times), which fixes
  them at the cause; its two gates fixes were cherry-picked first and merge as the same change. When the main session
  merges both branches the second merge of those commits is a no-op. If pack/throughput should land later than this
  branch, merge pack/throughput first.
