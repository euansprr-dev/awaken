# A body rests on the ground (SPEC §47.12): before and after

Euan's playtest on 11 Nov: a Plumed Coil's carcass had sunk into jungle grass and shimmered. Each sheet has the build tip
before the fix on top (1791215d8, branch claude/sunken-body-before-a4e782) and this branch below. Both are the same tour,
`tools/autopilot/bodies-rest.txt`, on GitHub's macOS runners. Every body is struck down by you: standing, falling, at
the end of its fall, then lying as a carcass.

- Main runs, with carcasses: before 42962473390, after 42962474010.
- First runs, beasts summoned without setup so they left no carcass (only the falls count): before 42960962402, after
  42960960997 (`myco-fall.jpg`, `sow-fall.jpg`).

| sheet | ground | before | after |
|---|---|---|---|
| coil | jungle grass, killed on the wing | dies in the air; its carcass hangs in the sky | falls, and its carcass lies in the grass on the grass block |
| antler | bottom slabs | its antlers dig into the slab | lies on its side, neck along the slab, antlers out |
| sow | snow, three layers | half its body in the snow | lies on top of the snow |
| myco | grass | its cap stands on edge in the ground like a wheel | its cap tips back onto the rim, top up |
| slab | oak slabs | the Thousandfoot curls up on end, both ends in the slab | lies on its side, flat on the slab |
| sheep | carpet | vanilla's flip sinks half the body | lies on top of the carpet |
| cow | dirt path | half sunk | on top |

The levelled form (a pig) vanishes in a puff before it can be seen lying, so that scene shows nothing either way. The
Frost Stag, the Pollen Grazer and the Hushling look the same before and after. None of it has been played on Euan's Mac.
