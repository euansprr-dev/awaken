# Swing Studio rounds

Each round: the note it answers, what changed in the feel spec, the report's numbers before and after, and what is
left. Numbers from `python3 tools/art/swing_studio.py report <style>` (SPEC §25 "Swing Studio"). Contact is the blade's
middle on screen at the hit (0 is the crosshair, -1 the bottom edge); "high" the share of the cut's fast motion above
the eye line; drift the slow motion left in view after the hit.

## Round 0 (baseline, 2026-10-28)

Today's swings rebaked through the studio with no feel specs: every key identical to the shipped file. Across the
131 cuts: median contact -0.09, 78 mostly above the eye. Swish: 33 of 67 chain strikes peaked 40-155 ms off the hit.
Combo links jumped the blade up to 2.5 screen units at the earliest press.

## Round 1: the katana (2026-10-28)

Note (Euan): "swings aim too far up", "the katana feels floaty", "each strike starts from rest... it kind of feels like
it glitches or teleports".

Spec `tools/arms/feel/katana.json`: the second-generation cut for every cut (power 3); slashes drawn back 48 degrees so
the set shows at the frame's right edge, through at 16, carried to 58 and out of the frame; the backhand (strike 2)
driven in 0.75 ticks and carried 60 degrees out to the right instead of hanging in view.

| move | contact | high | hit / peak | drift |
|---|---|---|---|---|
| strike 1 (slash) | -0.04 -> -0.23 | 89% -> 22% | 1.00 -> 0.96 | 288 -> 75 ms |
| strike 2 (backhand) | -0.09 -> -0.31 | 20% -> 29% | 0.62 -> 1.00 | 312 -> 75 ms |
| strike 3 (chop) | -0.09 -> -0.22 | 78% -> 25% | 1.00 -> 1.00 | 425 -> 75 ms |

Runtime with it (every style): the chain blend (no jump on a combo link, the view's lean carried too), the bite after
a landed hit, the swish on the blade's clock (0 of 179 moves now off by more than 35 ms), the katana's own cut sound,
two hands on the two-handed weapons.

Left: the stab (strike 4) still drifts 338 ms (thrusts keep the first-generation shape until their round).

## Round 2: every style (2026-10-28)

Note (Euan, same day): "I don't think I need to go through every single one myself... some feel a bit wonky or like
soft, like floaty... Go ahead with the entire plan and everything you recommend."

A feel spec for all 16 styles (round 2), and second-generation shapes for what round 1 left first-generation:

- Thrusts: a beat at full reach, snapped back to the ready line (light weapons in 0.6 of a tick), held there for the
  next press, then home. The katana's stab: drift 338 -> 50 ms; the daggers' 62 -> 50.
- Spins (twin blades, war-staff): fastest on the hit, carried off it in one slowing, then home from below. Drift
  212-375 -> 62 ms.
- The scythe's haul: one quick pull back through the target, crossing it at its fastest. Drift 262-400 -> 50-62 ms.
- The shield bash: held on the foe half a tick, then pulled straight back. Drift 112 -> 62 ms.
- Cuts: the rise home quicker (blade 3 -> 2.5 ticks); the wrist's lead and the drop ride the carry's fall of speed.
  The chops and plunges stop moving once the blade does.
- The longsword, precursor and shieldblade take the katana's recipe; the opening diagonals drive harder (power 3.4).
- The axe's chops land at chest height (contact -0.50 -> -0.36).

| all 179 moves, out of range | round 1's file | round 2 |
|---|---|---|
| contact | 74 | 2 |
| above the eye | 82 | 1 |
| hit below its peak | 24 | 8 |
| drive | 15 | 0 |
| bite | 71 | 0 |
| drift | 172 | 2 |
| swish | 0 | 0 |

Cuts mostly above the eye: 78 of 131 -> 1.

Left for their own rounds:
- The glaive's and war-staff's sweeps (hit/peak 0.64-0.79). The long tip comes nearer the eye just after the hit, so it
  looks fastest a beat late. Moving the contact made it worse. The next try is a shorter reach on the follow-through.
- The axe's sweep (strike 4) and art (0.71, 0.76), the daggers' slash (strike 3, 0.80).
- The maul's uppercut contact (-0.09), the scythe's reap contact (-0.09) and riposte (43% above the eye).
- The chain-blade's backhand lash (drift 88 ms) and the war-staff's backhand (138 ms).
