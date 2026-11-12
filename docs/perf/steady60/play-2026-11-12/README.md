# Far tall grass (12 Nov 2026)

Euan, playing at 4K on the Dell over a grassland of tall grass and flowers: "all the grass and the shadows and
everything just becomes one big shimmering block of weirdness ... because you have that breathing movement, especially
with your camera"; "I like the shadow in the grass, but at the same time ... when there's a lot of tall grass like this
at distance, it creates this weird effect where it's just like there's so much going on in such a small area of view
... can we change the logic of how shadow is on swaying grass so that it just makes it look less shimmery? Because
like right now I'm on native 4K and it still just looks very shimmery."

**What it was.** Tall grass is open air to the block map, so its shade is the light pass's small shade: contact
shadows marched through the depth buffer and the ambient shade, both worked out in the 960x540 light buffer from one
depth sample a texel. Measured on a built meadow (`tools/autopilot/steady60-meadow-ci.txt`, read by
`tools/lens/meadow.py`):

- The **contact shadows** are the flecks: about 40% more local contrast in the grass at every distance, and with the
  camera drifting half a pixel a frame nearly twice the pixels flip between frames (12 to 13% against 7%).
- The **ambient shade** is not: it darkens grass evenly (about 14%) and adds no flecks.
- Part of the shimmer is **the game's own grass**: with none of Lens's light, 9 to 12% of its pixels flip 11 to 53
  blocks off under the same drift. Cutout blades thinner than a pixel; the temporal upscaler he is holding for later
  is what would reach that.

**What changed.** The contact shadows are kept while the light buffer can still see what casts them and let go as it
can't: whole where a light texel is up to 0.6 of one of the art's pixels wide, gone by 2.2, which at a 90 degree view
is 10 to 37 blocks. Near grass keeps its shade. The ambient shade, and every tree's, hill's and block's shadow, is as
it was. Past the fade the contact march is skipped, so it can only save frame time (not measured: his Mac was in use).

| about | flipping pixels as it was | with the fix | with no contact shadows at all |
|---|---|---|---|
| 5 to 11 blocks | 8.7% | 8.5% | 4.8% |
| 11 to 23 | 12.9% | 10.2% | 7.1% |
| 23 to 53 | 12.2% | 7.3% | 6.8% |
| beyond | 3.5% | 2.4% | 2.3% |

(Run 43173148812, the camera drifting, the plants held still, two bursts each. With the plants swaying in a held
breeze too: 19.2% to 13.4% at 11 to 23 blocks, 10.0% to 5.4% at 23 to 53. Full table in SPEC §22, "The small shade's
reach".)

- `far-grass-before-after.jpg`: the meadow from eight blocks up and from three, as it was and with the fix, and the
  grass 15 to 60 blocks off at full size. Far grass is calmer and about 12% lighter; tall clumps and flowers read.
- `far-grass-drift.gif`: that far grass over five frames of the camera's drift, as it was beside the fix.

To judge it in play: `/awaken lens small 0 0 0 0` is the picture as it was, `/awaken lens small setting` the fix, and
`/awaken lens small 1 3 0 0` a later, longer fade (17 to 51 blocks) if the shade should last farther.

Runs: 43166685979 (stills by part; no Sodium, so nothing swayed), 43168733918 (the real wind and Camera Overhaul:
too gusty to rank anything), 43170794805 (a first reach of 1 to 3), 43173148812 (the reach as built).
