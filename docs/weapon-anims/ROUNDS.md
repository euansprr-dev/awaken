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

## Round 3: the chain (2026-10-28)

Note (Euan, same day): "most of them are still decent, but when you're doing combos, a lot of the time the weapon, the
second slice, is not even visible... Some of them are so fast that when you're clicking fast, it looks like just a
mess... the trail teleports and follows the teleportation... the hammer, the second swing, you barely even see it."
He also said the weight improved: the katana feels fast, the hammer slower, the axe good.

What was wrong: the studio judged each move alone. Round 2 carried every follow-through down out of the frame and held it
there for the next press, so in a chain the next strike wound up from below the view and only appeared on its hit. The
new `tools/art/swing_chain.py` plays the chains the way the game does and showed it: second and third strikes were on
screen for 15-45% of their wind-up and blow, the blade dipping 2-12 screen heights below the frame.

The fix (every style, in the studio's cut and the shared wind-up):
- A strike that leads on ends in view (the carry cut back, at least 40% kept), a low blow is hauled up to a readable
  height, and it holds there through the chain's window.
- The next strike of the same hand winds up from that pose, its draw back kept in view.
- No wind-up flings the tip more than 0.3 of the screen a frame; with no room for a set it goes straight to the drive.

| chain strikes (67), out | round 2, mash | round 3, mash | round 2, mid-window | round 3, mid-window |
|---|---|---|---|---|
| out of view (wind-up and blow under 90% seen) | 32 | 6 | 29 | 4 |
| drive out of view | 30 | 7 | 28 | 5 |
| a step over 0.3 of the screen outside the blow | 42 | 29 | 49 | 31 |
| turns back on itself | 6 | 3 | 1 | 2 |

The single-move report: bite, contact and drive hold; drift 2 -> 27 (the settle after a leading blow is now in view),
seven chained strikes hit at 0.42-0.72 of their peak (shorter draw back).

Left: the scythe's haul, the twin blades' spins (strikes 4-5), the daggers' and twin blades' rhythm (a 1-tick wind-up
every 4 ticks: the idle hand could draw back while the other strikes), and the steps still over 0.3 (mostly first
strikes thrown up from rest).

## Round 4: the idle hand (2026-10-28)

Note (Euan, same day): "do the daggers and twin blades rhythm next: let the idle hand draw back while the other strikes."

The daggers strike one hand at a time, a 1-tick wind-up every 4 ticks, and the hand not striking sat at rest, so each
stab came out of nowhere. Now, from the moment one dagger lands, the other draws back toward the next strike's set and
waits there; the next stab only has the last part of its draw left. The idle dagger stays on its own side of the view
(its tip at least 0.15 from the middle): drawn all the way to the next set, the two blades piled up in the centre.
Strike 4 is both hands crossing, by design, and now starts where the main hand held strike 3.

The twin blades are one double-ended blade held in one hand (every move the main hand's), so there is no idle hand to
draw back. Their round-3 chain is in view; their rhythm needs its own idea.

Chain links that start exactly where their hand already is: 43 -> 46 (the daggers' 1 -> 2, 2 -> 3, 3 -> 4).

## Round 5: the twin blades' figure-eight (2026-10-28)

Note (Euan, same day): "give the twin blades a two-handed hold on the shaft and alternate which end leads each strike,
a figure-eight."

Both hands now hold the shaft, the off hand just below the main. The ends take turns. Strike 1 cuts with the near blade
and does not stop on the hit: it whirls straight on, slowing, half a turn by the moment the next press can come, and the
hands rise as it turns, so the far blade swings up from below into view. Strike 2 cuts back with that far blade along
the exact path the near one would have taken, then whirls on, and the near blade stands up for strike 3. The spins
(strikes 4 and 5) use both ends anyway. If the chain stops after a strike, the blade keeps turning the same way as it
goes home. The trail follows whichever blade leads.

Tried and dropped: starting the turn after the bite (the cut stopped dead, then the turn set off again faster than the
hit) and easing the turn in (a dip after the hit, then a second peak faster than it). From the hit, fast and slowing, the
turn is the blow carrying on.

Numbers: twin blades strikes 2 and 3 stay fully in view (mash jump 0.28 and 0.21, steady 0.11 and 0.21). Every other
style's chain is unchanged (totals as round 4). Strike 1's own blade is in view less (0.88 -> 0.75), because it whirls
off the bottom as the far blade comes up.

## Round 6: the daggers smoothed (2026-10-28)

Note (Euan, same day, playing round 5): "Daggers still feel very glitchy and not smooth imo, it doesn't smoothly switch
hands, sometimes it strikes in unnatural ways, and sometimes it does tiny near glitchy movements."

Traced both hands frame by frame through the chain at 60 fps. Four causes:

- **The switch.** The hand not striking started its track from rest. So the hand that had just stabbed sank back to
  rest, turned round and rose again to its next set, at every change of hands. It now starts where it already is and
  draws straight on.
- **The stutter.** A 1-tick stab was four phases, each a frame long: load, set, push, stop. The tip went 0.37, 0.07,
  0.23, 0.03 a frame. Now each stab is one push from where the hand is, speeding up into the hit (0.11, 0.16, 0.15). The
  hit's speed as a share of the stab's peak: 0.08 -> 0.72, 0.28 -> 0.65, 0.04 -> 0.45 for strikes 1, 2 and 4, and
  0.10 -> 0.95 for the heavy.
- **The fling.** The waiting hand stopped halfway across, so strike 3's backhand flung the rest of its draw in one
  frame (0.69). It now goes all the way unless the two blades would come within 0.35 of each other. Strike 3's hit is
  now its fastest moment (0.52 -> 1.00). Before strike 4 (both hands), the waiting hand mirrors the main one, so the
  two leave together; before, it swung out and back.
- **The odd one.** Strike 5, the off hand's slash, crossed the body and dived three screens below the view before
  coming home. It now carries less and comes straight home, on the screen.

Chain flags for the daggers: 3 -> 1 when mashing, 2 -> 1 at a steady pace. The one left is strike 5's draw out of
rest: 0.33 in a frame against the 0.3 limit. No other style changed.

