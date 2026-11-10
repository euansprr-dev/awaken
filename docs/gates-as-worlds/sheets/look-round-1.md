# Look round 1 (10 Nov): what the orchestrator saw, and the exact changes

All three gates passed the bench first or second time. This round is about how they look beside their paintings. Each list is
complete: do those things, bench after each, don't add others. First `git merge --ff-only claude/gates-as-worlds-plan-fcb81b`.

## The Hour Crypt (builder: Hour Crypt)

Seen: you can now see the place, and the carved shapes are there. But it reads as an even blue wash with brown squares scattered
over it, and the painting is near-black stone with gold pools at the candles and one blue haze at the far end.

1. **`worn` is the wrong colour, for the whole Knell family.** In `tools/art/specs/realm_knell.py` the `aworn` ramp is rust brown, and
   mixed one block in four into blue stone it makes a checkerboard. Repaint `aworn` as the same stone a little warmer and greyer:
   hue between the stone's and a neutral grey, saturation under 10%, mean luminance within 10 of `astone`'s. Rust stays in `abrick`
   only. Regenerate, `tone_check.py knell_`. (The Quiet Barrow uses this family too and gets the fix for free.)
2. **Courses, not confetti.** `HourCrypt.course` picks a block per cell. Pick per run: the same block for 2 to 4 cells along a
   course (hash on `floorDiv(u, 3)` with a per-course offset), mix 62% `stoneA`, 20% `worn`, 12% `deep`, 6% `pale`.
3. **Dark base, real pools.** `knell_crypt.json` `ambient_light_color` back down to `#1a2130`. Each lit bay's hidden light goes to
   level 15 and there are two per lit bay: one at the foot of the wall one cell out, one at the middle tier. Lit bays are every
   third bay as now.
4. **A new number, `pool`,** in `preview/Preview.java`'s report beside `bright`: the percent of non-sky pixels with luminance over
   90. Targets for `hour_painting`, `hour_nave`, `hour_lord_floor`: `lum` 20 to 40, `pool` 10 or more, `dark` 55 or less. `bright`
   stays in the report but is no longer a target (you were right about what it counts). Add `_arch|_flute|_rib|_corbel|_link` to the
   `SHAPED` pattern as you suggested.
5. Hand back the three shots' `lum / dark / pool` before and after, and your rounds and minutes.

## The Quiet Barrow (builder: Quiet Barrow)

Seen: from the painter's camera the hill, the terraces, the stones and the ship are all where the painting has them. Close up it
is blocky, and the colours are a bright blue and lavender where the painting is grey-brown stone, dark turf and dusty mauve.

1. **The arrival.** The blockout's `mouth_pad` is now radius 4 (my mistake: it filled the bottom of the arrival view with flat
   paving). Pave only the pad and the five-wide path; heather and turf come right up to both. The Lord site has moved to
   (-59, 104, -50), between the hold and the prow; keep your stairs out of the hold.
2. **Mounds are turf.** A barrow shows stone only at its doorway (jambs, lintel, a kerb course round the foot). Every step's tread
   and riser above that is `barrow_turf` with heather on top, the corners rounded off by one cell, so it reads as a grassed mound.
   The same for the village houses' roofs and the long barrow.
3. **Standing stones are one stone each.** One material per stone (`stoneA` for seven in ten, `pale` for the rest), no bands.
   Heights 5 to 9. A stone taller than 6 is 2x2 for its lower half, then 1x1, then the cap.
4. **Plants.** In `realm_barrow.py`: `barrow_heather` a dusty mauve over dark olive stems (mean luminance about 85, saturation
   about half of now); `barrow_heather_pale` and `barrow_bent` a dry straw, not white (about 120). `barrow_turf` darker and browner.
5. **The rim hills** look like smooth domes. In `draw`, break them up: on every `rim_*` mass add outcrops of bare `stoneA` and `deep`
   where a noise field is over 0.72 (two to four cells proud, footed), and thin the heather to 30% above y 80.
6. **The ship's prow is the Lord painting's hero and yours is a thin post.** Build it four wide at the deck, tapering to two, rising
   26 above the deck in an S (out over the bow, then back over the deck), its head three wide with a `gold` eye each side and a jaw,
   scales suggested with upside-down `stoneStairs` on the outer curve, the `gate_bell` hanging four links under the jaw. The stern
   post the same at half the size. Bow and stern: the top two courses of the hull rise by one every four cells toward each end
   over the last 16, so the sheer line curves.
7. **The deck** is a bare corridor. Add a thwart (a `timber` beam one high, across the deck) every 7 cells with a gap of 3 in the
   middle to walk through, `barrow_shield` along the *inside* top of both gunwales as well, and stand the 24 urns in clusters of
   three at the thwarts' ends. If `barrow_urn` draws as a plain box on the bench, its model has lost its elements: fix the model.
8. The paving and terrace risers lose their brown squares when the Hour Crypt builder repaints Knell's `worn`; nothing for you to do
   there but re-run the bench after you merge again at the end.

## The Wax Galleries (builder: Wax Galleries)

Seen: this is the closest of the three. The dark mouth, the lake, the floe paths and the five combs are the painting. It is darker
and browner than the painting, which is gold and cream.

1. **The combs should glow as wholes.** Three cells in four are `wax_comb_cell_lit`, in vertical drifts (noise stretched 4:1 in y).
   A hidden light of 13 every 6 blocks down each comb's axis.
2. **The wall is wax, not rock.** Every pier, parapet, cornice and arch in the five tiers is faced in `wax_cold`; stone shows only in
   the lowest course and through patches where noise is over 0.82. Lit bays are every second bay. Twice the drips.
3. **Floes, shore and isle are the palest thing in the hall.** They read dark brown now. Make sure their top block is `wax_cold`
   (not `wax_run`, not stone) and that `WarmWaxBlock` gives light 9 so the lake lights them from beside.
4. **The seat** is cream with gold rings in the painting and dark orange here. Pave it in `wax_cold` cut as hexes (the family's
   pale hex block if it has one, else `wax_cold` with `gold` joint lines every 5 cells on a hex grid); keep the three `gold` rings;
   double the candle banks on the rim. Move the `seat_painting` camera's chain out of its way by turning the four chains 20 degrees.
5. **Gallery floors** are a plain pale wax flag, no checker.
6. The blockout's `foot` camera is now two higher, as you asked.
7. Targets on the `painting` shot: `lum` 60 to 100, `dark` under 10. Hand back `lum / dark / bright` before and after, rounds, minutes.
