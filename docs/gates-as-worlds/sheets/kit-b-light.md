# Kit sheet B: true light, textures that can be lit, and a Lens look per gate

Read `BUILDER.md` in this folder first.

## Why

The Hour Crypt is near-black in the real game. Measured cause: the game shows texture x light, and the Knell family was painted as
dark as its painting's shadows (median luminance 53 of 255; wall stone 22 to 38; Amber's family is 122). Kingsroot's Keep family is
69. The preview shades indoor lands with a rough guess, so nobody could see this before a 12-minute walk. You make the preview use
the game's own light, re-tone the two dark families, and give five gates their own Lens look.

## B1. The game's light in the preview

New file `src/main/java/dev/awaken/gates/realms/preview/TrueLight.java`, pure functions, no game objects beyond what `Preview` has.

- Port `/Volumes/MEDIA/Awaken/.ref/assets/assets/minecraft/shaders/core/lightmap.fsh` exactly: block and sky levels 0-15 in, an RGB
  multiplier out. Read `/Volumes/MEDIA/Awaken/.ref/src/net/minecraft/client/renderer/LightmapRenderStateExtractor.java` for what feeds
  it (`BlockFactor` = 1.4 with no flicker, `SkyFactor`, `BrightnessFactor` from the gamma option).
- A `LightEnv` read from the land's first biome JSON `attributes`, falling back to `data/awaken/dimension_type/realms.json`, then to
  the game's defaults (`EnvironmentAttributes.java`): `minecraft:visual/ambient_light_color`, `sky_light_color`, `sky_light_factor`,
  `block_light_tint`. Gamma defaults to 0.5 (what the CI walks use); `AWAKEN_PREVIEW_GAMMA` overrides it.
- Check values, to +-0.01 (default tint `#ffd88c`, gamma 0.5):
  block 7, sky 0, ambient black -> (0.469, 0.397, 0.258);
  block 0, sky 0, ambient `#252e40` -> (0.271, 0.337, 0.468).
  Put these in a GameTest `realm_truelight_matches_the_shader`.
- In `Preview.java`: flood block light and sky light for **every** land, not only `indoors()` ones, and shade each texel as
  texture x `TrueLight` x the face shade and soft corners it already has. Glowing texels and lamps stay as they are. Outdoors keep
  today's sun shadow term, applied only where sky light is 15, so Amber's outdoor sheet doesn't change character. Remove the old
  `indoors` guess (`amb = 0.10`, `curve`).
- Three new numbers per shot in `report.txt`, measured over pixels that aren't sky: `lum` (median luminance 0-255), `dark` (percent
  under 20) and `bright` (percent over 200). A new flag `BLACK` when `lum` is under 25 or `dark` is over 60.
- Don't change how blocks are painted or hashed. Another builder is adding an entry point at the top of `Preview.java` (`run`,
  `runIfAsked`, `shots`): keep your edits to `shade`, the flood condition, the report line and the flags.

## B2. Textures that can be lit

- `tools/art/tone_check.py <prefix>` (for example `knell_`): prints each `assets/awaken/textures/block/<prefix>*.png` with its mean
  luminance and the family median. Exits 1 when the median is outside 95 to 150, or when any structural texture (name containing
  `stone`, `brick`, `flagstone`, `hexstone`, `tile`, `planks`, `timber`, `iron`, `bed`) is under 60. Amber, Salt and Fane must pass
  as they are today.
- Re-tone **Knell** (`tools/art/specs/realm_knell.py`) and **Keep** (`tools/art/specs/realm_keep.py`): lift every ramp by one
  exponent per family on luminance, hue kept, saturation raised by about a tenth, so the family median lands at 100 (Knell about
  0.58, Keep about 0.72: `new = 255 * (old / 255) ^ g`). Then raise any structural ramp still under 62 until it reaches it. Leave
  glow, window and gilt ramps alone. Knell must still read as cold blue-grey cut stone with rust in the joints; Keep as cold wet
  ashlar. Darkness now comes from light, never from the texture.
- Regenerate both families the way each spec's header says, run `tone_check.py` on both, and commit the PNGs.

## B3. A Lens look for each main-line gate

Add these to `src/main/resources/assets/awaken/lens/looks_gates.json`, keyed like Amber's (`biome:awaken:realm/<name>`). Read the
Lens code that loads looks to confirm each field's range and what `mistHeight` and `light` mean; keep my numbers unless one is out
of range, and say which you changed. `mistHeight` is meant as "a little above where you walk": set it from each land's main floor.

| key | `_name` | exposure | contrast | saturation | warmth | shadowTint | highlightTint | splitTone | vignette | bloom | bloomThreshold | skyBloom | shafts | haze | hazeStart | hazeTint | hazeTintMix | hazeDesaturate | sunsetWarmth | sunGlow | ao | filmic | mist | fogGlow |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| knell_crypt | The Hour Crypt: blue-black stone under a moon, every candle a pool of gold, shafts down through the roof | 1.22 | 1.06 | 1.10 | 0.0 | .34 .42 .64 | 1.0 .80 .48 | 0.30 | 0.32 | 1.10 | 0.52 | 0.0 | 0.85 | 0.40 | 0.14 | .30 .44 .70 | 0.6 | 0.05 | 0.0 | 0.0 | 0.9 | 0.8 | 0.30 | 0.9 |
| fane_jungle | The Vine Seat: wet green stone in white jungle mist, pale light through the canopy | 1.04 | 1.10 | 1.06 | 0.02 | .30 .42 .40 | .98 1.0 .86 | 0.18 | 0.18 | 0.80 | 0.66 | 0.3 | 0.95 | 0.48 | 0.22 | .80 .92 .86 | 0.6 | 0.12 | 0.2 | 0.5 | 0.9 | 1.0 | 0.55 | 0.5 |
| fane_bowl | Under the tree: deep green shade, gold lamps, light falling down the trunk | 1.15 | 1.10 | 1.10 | 0.06 | .24 .38 .30 | 1.0 .86 .50 | 0.24 | 0.34 | 1.00 | 0.54 | 0.0 | 0.70 | 0.42 | 0.14 | .42 .62 .44 | 0.6 | 0.05 | 0.0 | 0.0 | 0.9 | 0.85 | 0.20 | 0.8 |
| keep_rain | Kingsroot Keep: storm blue over wet grey stone, torches burning warm in the rain | 1.10 | 1.12 | 0.96 | 0.0 | .34 .40 .56 | 1.0 .84 .60 | 0.26 | 0.30 | 0.90 | 0.58 | 0.1 | 0.25 | 0.46 | 0.20 | .50 .58 .72 | 0.6 | 0.15 | 0.0 | 0.1 | 0.9 | 0.9 | 0.45 | 0.6 |
| keep_hall | The roofless hall: fire on wet flagstones, broken arches against the storm | 1.18 | 1.12 | 1.06 | 0.08 | .30 .34 .48 | 1.0 .78 .44 | 0.30 | 0.34 | 1.05 | 0.52 | 0.05 | 0.30 | 0.40 | 0.14 | .44 .50 .64 | 0.55 | 0.10 | 0.0 | 0.0 | 0.9 | 0.85 | 0.20 | 0.9 |
| salt_flats | Saltcomb Flats: white salt under a hard sun, blue in every shadow, the comb city gold in the heat | 0.94 | 1.14 | 1.10 | 0.10 | .38 .46 .66 | 1.0 .95 .80 | 0.22 | 0.16 | 0.65 | 0.82 | 0.3 | 0.60 | 0.30 | 0.35 | .98 .86 .60 | 0.5 | 0.08 | 0.4 | 0.9 | 0.85 | 1.0 | 0.12 | 0.4 |
| salt_court | The Sinking Court: a pit of comb, one shaft of sun through the roof, gold dust in it | 1.08 | 1.14 | 1.10 | 0.14 | .36 .30 .24 | 1.0 .88 .56 | 0.24 | 0.32 | 1.00 | 0.60 | 0.2 | 1.00 | 0.44 | 0.14 | .92 .72 .36 | 0.6 | 0.05 | 0.0 | 0.4 | 0.9 | 0.9 | 0.15 | 0.8 |

`light` and `skyGlow` are 0.0 in all seven, as in Amber's.

## B4. The Crypt's borrowed sounds

`data/awaken/worldgen/biome/realm/knell_crypt.json` plays `awaken:music.gate.hive` and `awaken:realm.amber.hall`, copied from Amber.
Look in the merged `sounds.json` sources (`src/main/merge/*/assets/awaken/sounds.json`) for the Bell Crypt theme's own music and a
crypt ambience that already exist, and use them. If none exist, leave the file and say so. Check the other four main-line gates'
biomes for the same copy and fix them the same way. Don't make new sounds.

## Your files

`preview/TrueLight.java` (new); `preview/Preview.java` (shading, flood, report numbers and flags only); `tools/art/tone_check.py`
(new); `tools/art/specs/realm_knell.py`, `tools/art/specs/realm_keep.py` and a helper in `tools/art/specs/_gw_kit.py` if you need
one; the regenerated `assets/awaken/textures/block/knell_*` and `keep_*` PNGs; `assets/awaken/lens/looks_gates.json`; the biome
JSONs under `data/awaken/worldgen/biome/realm/` for sounds; one registration line for your GameTest in `RealmTests.java`.

## Done when

`tools/preview/preview.sh tools/autopilot/realm-hour.txt` prints the three new numbers, and The Hour Crypt's `lum` roughly doubles
against the same run before your re-tone (say both numbers in your hand-back); `tone_check.py` passes for knell, keep, amber, salt
and fane; `realm_truelight_matches_the_shader` is green; Amber's three hashes are unchanged; committed.
