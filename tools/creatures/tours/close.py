# writes tools/autopilot/mob-anims-close.txt
import sys
import os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from tourlib import *  # noqa: E402,F403
L = []
def a(*xs): L.extend(xs)
header(a, "closefa", 779, (
    "Creatures to the Fresh Animations bar (SPEC 23.1), close up: each grazer and the spider filmed side on at leg height",
    "by a camera that rides beside it, first vanilla's under Fresh Animations, then ours (rung 3) on the same course: a",
    "walk, a trot, a stop and a half turn on the spot. Then the two stand side by side for half a minute (their idles).",
    "Clips: close_<species>_fa, close_<species>_ours, close_<species>_idle."))
a("cmd gamemode spectator", "wait 5")
# species, eye height over the floor top (201), camera distance, walk pace, trot pace, spacing at rest
SPECIES = (("cow", 0.8, 2.5, 0.05, 0.11, 3.0), ("pig", 0.55, 2.0, 0.05, 0.12, 2.4), ("sheep", 0.65, 2.1, 0.05, 0.11, 2.6),
           ("chicken", 0.45, 1.6, 0.04, 0.09, 1.6), ("spider", 0.5, 2.6, 0.06, 0.14, 3.2))
X0 = -10.0
for sp, eye, dist, walk, trot, gap in SPECIES:
    course = ((walk, 100), (trot, 50), (walk, 20))
    for who in ("fa", "ours"):
        a(f"# {sp}: {'vanilla under Fresh Animations' if who == 'fa' else 'ours, rung 3'}")
        if who == "fa":
            a(f"cmd summon minecraft:{sp} {X0} 201 2.0 {{{NOAI},Tags:[\"walker\"],Rotation:[-90f,0f]}}")
        else:
            a(f"cmd tp @s {X0} 201 0.0 0 0", "wait 3", f"cmd awaken living form {sp} 3", "wait 5",
              f"cmd tag @e[type=minecraft:{sp},limit=1,sort=nearest] add walker",
              f"cmd tp @e[tag=walker] {X0} 201 2.0 -90 0")
        a(f"cmd execute as @e[tag=walker] run data merge entity @s {{NoAI:1b,OnGround:1b,PersistenceRequired:1b}}",
          f"cmd tp @s {X0} {201 + eye - 1.62:.2f} {2.0 - dist} 0 2", "look 0 2", "wait 40",
          f"cmd awaken ascent pilot rec close_{sp}_{who} {sum(n for _, n in course) + 100} 2")
        for spd, n in course:
            a(f"cmd awaken admin stroll @e[tag=walker] {spd} {n}", f"glide {spd} 0 0 {n}", f"wait {n}")
        a(f"shot close_{sp}_{who}_stop", "wait 30", f"cmd awaken admin stroll @e[tag=walker] 0 30 6", "wait 60",
          f"shot close_{sp}_{who}_turned")
        clear(a)
    # the two at rest side by side: theirs on the left, ours on the right, both facing the camera's left
    a(f"# {sp}: the two at rest, half a minute (ours first, so the tag can't take theirs)",
      f"cmd tp @s 0.5 201 0.0 0 0", "wait 3", f"cmd awaken living form {sp} 3", "wait 5",
      f"cmd tag @e[type=minecraft:{sp},limit=1,sort=nearest] add ours",
      f"cmd tp @e[tag=ours] {gap / 2:.2f} 201 2.0 90 0",
      f"cmd summon minecraft:{sp} {-gap / 2:.2f} 201 2.0 {{{NOAI},Rotation:[90f,0f]}}",
      f"cmd execute as @e[type=minecraft:{sp}] run data merge entity @s {{NoAI:1b,OnGround:1b,PersistenceRequired:1b}}",
      f"cmd tp @s 0.0 {201 + eye - 1.62 + 0.15:.2f} {2.0 - dist * 1.6:.2f} 0 4", "look 0 4", "wait 40",
      f"shot close_{sp}_idle", f"cmd awaken ascent pilot rec close_{sp}_idle 600 4", "wait 610")
    clear(a)
a("cmd gamemode creative", "info", "quit")
open(sys.argv[1], "w").write("\n".join(L) + "\n")
