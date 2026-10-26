# Arms weapon style (for SPEC §25 "Weapons")

The look bar is the owner's reference (`/Volumes/MEDIA/arms-ref/weapon-style-ref.webp`): a Minecraft Dungeons-style
render of a big two-handed hammer. The owner turned down two earlier passes. Plain JSON cubes were "still very, very
blocky". The lofted meshes were "too thin and noisy". These rules hold for every archetype (sword, axe, spear, hammer,
bow...). The toolkit is `tools/art/voxel.py`, worked example `tools/art/specs/arms_weapons.py`, review sheet
`python3 tools/art/specs/arms_weapons.py sheet OUT.png` (it puts the reference beside every weapon).

1. **Density: 1 texel = 1 item unit (1/16 block), the size of a skin pixel.** Every box has whole-texel sizes, and every face
   is its own chart at exactly that density, so texels are square, all the same size and match the player's arm.
   Boxes may sit at half-texel offsets (to centre odd widths), but their sizes never break the grid. Weapons are
   designed in texels, with the grip's middle near the origin and the blade up +y. `to_item` turns the blade onto the
   item's diagonal and does not scale it.

2. **Construction: bold designed parts, not cubes and not staircases.** Build from a few big boxes per part: a blade
   is a thin full-width edge slab with a thick core over its middle, so the section really steps from edge to spine.
   It narrows to its point in whole-texel steps. Guards, grips, pommels and caps are boxes. Anything diagonal is a box
   turned a whole angle (`rbox`, `chain`, `fan`). Never draw a diagonal as a one-texel staircase: that reads as a voxel
   sprite. Stones sit in 45-degree frames (`frame`) and stand proud of what holds them. Two faces must never share a
   plane on different texel grids (rotated over axis-aligned, or offset by half a texel): `check()` fails on it,
   because they z-fight. Give stacked parts different depths, for example chained links 3, 2, 1 deep.

3. **Proportions: a longsword is about 1.2 to 1.3 times vanilla's sword (22.6 units on the diagonal), about 28
   texels, and two thirds of that is blade.** Blades are 3 to 5 texels wide. The thin edge is 1 texel deep and the
   core 3. Grips are 3 by 3 and 4 or 5 long. Guards and pommels get bolder with the tier: the low tiers use bone,
   antler and a plain collar, the high tiers wings, crowns and big framed stones. The silhouette must say what the
   weapon is from its outline alone, and no two weapons may share one. The other lines:
   - **Axe:** a 3×3 haft about 23 long, the bit flaring from the eye to its edge in 2-column steps (`bit`), a spike,
     poll or wing on the back, and metal langets down the haft.
   - **Maul** (the reference's own): a 26-long haft and a block head made of a core, flared caps with a painted
     trim field (`panel`) and a raised 45° frame with its stone.
   - **Spear:** a slender 2×2 shaft about 31 long, so it reads as reach, and a head about 10 long (leaf, lugs,
     fork, wave, wings or spire).
   - **Greatblade:** about 38 long, 9–11 wide, an 8-long two-hand grip, a narrow banded ricasso ending in parrying
     lugs, and each tier's own outline: a slab, a hook, a window, a flamberge, a fork or a crown.
   - **Rapier:** a 3-wide needle stepping to a 1-wide point, about 33 long, with a swept, cup or ring hilt.
   - **Daggers:** about 16 long, a wide-backed or curved blade about 9 long, and a knuckle guard.
   - **Katana:** a curved blade of whole-angle turned links (`chain` with a stagger), a hamon line of the tier's
     accent, a disc tsuba, and a 9-long tsuka in diamond wrap.
   - **Twinblades:** two blades from a central grip with a hub. The far blade is `spin`-turned half round so its edge
     faces the other way, and a guard at each end keeps it from reading as a staff.

   - **Glaive:** a 3×3 pole about 30 and a 5-wide curved blade of turned links, edge on the outside of the curve,
     with a fluke on its back.
   - **Scythe:** a 2×2 snath with a hand peg and a blade at right angles off its top, edge along the inside
     (`flip`), glowing on B, A and S because it cuts spirits.
   - **Gauntlets:** design +y is the punch and +z the back of the hand: a fist with three knuckle plates, curled
     fingers, the stone on the back and a cuff. Its hold turns it to be worn, knuckles into the view and the back
     of the hand up.
   - **Chainblade:** an S of links alternating face-on and edge-on, coiled at the grip, and a short head set at the
     chain's last angle (`turned`).
   - **Shieldblade:** the face fills the outline (round, or kite: a squared top over a 45° point), with a rim, a
     raised boss with the stone and a short blade on top.
   - **Warstaff:** about 30, two wrapped grips, weighted caps and a glowing focus at the head.
   - **Precursor Arm:** a cased hilt, an emitter, and hard light (the blade is the emissive) in three narrowing
     stages that pulse in turn. Each tier is its own technology.

   A swapped-in weapon rises over its attack delay (20 ÷ attack speed ticks: a maul 29, daggers 9). Tours wait
   that long before a first-person shot.

   Links of one depth that overlap at a joint (a curve, a bow) take `stagger=0.05`: every other link sits a
   twentieth of a texel proud, which is unseen and keeps them out of one plane.

   Each style has its own first-person hold (`HOLDS`). `check()` keeps the outer half of every weapon's reach on
   screen, inside the tuned Longsword's margins.

4. **Painted bevels, big and clean.** Each face has a flat base step from its material's ramp. Every outer edge gets
   a band one texel wide. The band is lit (+2 steps) where the edge faces the painted light (up the blade and toward
   its back, the item's upper left), +1 across the light, and dark (-1 or -2) where it faces away. Faces turned to
   the light sit a step up, faces turned away a step down. A face one texel across gets no bevel that way, and its
   tone comes from the way it faces. Top and bottom faces take at most ±1. Where a face runs into another part, a
   contact shadow (base -3) sits along the join. Within one part, steps take no shadow.

5. **Palette and contrast: hand-picked hue-shifted ramps (`RAMPS`), three or four materials per weapon, one of them
   dark and one warm.** For example dark flint with cream antler and orange amber, or black scale with gold and
   molten ember. Metals keep metal 0.3 to 0.55 in the material map so the albedo carries the colour, because the
   shader's studio reflection turns heavy metal muddy. Lighter metals (gold, brass, bronze) take base step 5.

6. **Detail budget: a few deliberate marks per part, never per-texel noise.** The allowed kinds are: a broken
   highlight streak on steel's lit edge, wrap bands of a second material on a grip, studs, a groove line, growth
   bands, scales in offset rows, knapped facets in three-row chevrons, an inlaid circuit or rune channel down a
   blade's middle column, and gold tips on feathers. Use at most one or two dither patterns, and only where they are
   the material (fur strands, the Veil sheen's trailing edge). Patterns are painted by design-space position, so they
   run unbroken across the boxes that make one surface.

7. **Stones and what glows.** A gem's face is concentric rings: the heart a step or two under the ramp's top, the
   rim darker, and one sparkle texel toward the light. Its sides are darker still. Gem albedo stays two or more steps
   under the top, because the shader adds the glow (albedo × emissive × 1.6) and a light albedo glows white instead
   of red, green or blue. Emissive 0.4 to 0.55 suits stones, about 0.6 to 1 suits runes, circuits and edges. Anything
   that pulses gets a flow (0..1) that rises up the weapon, so the light runs hilt to tip. Each stone carries an
   anchor (halo). Streams (embers, wisps, motes) stay on the B, A and S tiers only, and their points lie on the
   weapon.

8. **Check every look on the sheet before writing.** Look at upright three-quarter, other side and back views, the
   hilt close-up, first person at rest and in vanilla's hold, night (only what glows carries) and the icon at 4x and
   1x, all beside the reference. Ask of each weapon: is it chunky, are the bevels big and clean, is there material
   contrast, does it read at a glance, is it more than plain cubes, is it free of noise. Then run `build`. Its checks
   repeat `ArmsTests.assets`: budget, item box, normals, uvs in the albedo half, glows sized, streams on the weapon and
   only on B, A and S, plus no plane fights.
