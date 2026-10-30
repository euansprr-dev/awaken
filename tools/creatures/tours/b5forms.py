# writes tools/autopilot/mob-anims-b5forms.txt
import sys
import os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from tourlib import *  # noqa: E402,F403
L = []
def a(*xs): L.extend(xs)
header(a, "b5forms", 784, (
    "Creatures to the Fresh Animations bar (SPEC 23.1), batch 5: the walking forms (zombie, skeleton, creeper at rungs 2",
    "and 5) on planted feet, each species after vanilla's own under Fresh Animations. Each filmed side on at leg height",
    "by a camera that rides beside it: a walk, a run, a walk, a stop and a half turn on the spot. At night, so the",
    "undead don't burn (night vision keeps it bright).",
    "Clips: b5f_<species>_<fa|2|5>."))
a("cmd gamemode spectator", "cmd time set 18000", "wait 5")
X0 = -10.0
# species, body height (blocks), walk pace, trot pace
for sp, h, walk, trot in (("zombie", 1.95, 0.05, 0.12), ("skeleton", 1.99, 0.05, 0.12), ("creeper", 1.7, 0.05, 0.11)):
    eye, dist = h * 0.35, max(1.6, h * 1.6 + 0.8)
    course = ((walk, 100), (trot, 50), (walk, 20))
    for who in ("fa", 2, 5):
        a(f"# {sp}: {'vanilla under Fresh Animations' if who == 'fa' else f'ours, rung {who}'}")
        if who == "fa":
            a(f"cmd summon minecraft:{sp} {X0} 201 2.0 {{{NOAI},Tags:[\"walker\"],Rotation:[-90f,0f]}}")
        else:
            a(f"cmd tp @s {X0} 201 0.0 0 0", "wait 3", f"cmd awaken living form {sp} {who}", "wait 5",
              f"cmd tag @e[type=minecraft:{sp},limit=1,sort=nearest] add walker",
              f"cmd tp @e[tag=walker] {X0} 201 2.0 -90 0")
        a(f"cmd execute as @e[tag=walker] run data merge entity @s {{NoAI:1b,OnGround:1b,PersistenceRequired:1b}}",
          f"cmd tp @s {X0} {201 + eye - 1.62:.2f} {2.0 - dist:.2f} 0 2", "look 0 2", "wait 40",
          f"cmd awaken ascent pilot rec b5f_{sp}_{who} {sum(n for _, n in course) + 100} 2")
        for spd, n in course:
            a(f"cmd awaken admin stroll @e[tag=walker] {spd} {n}", f"glide {spd} 0 0 {n}", f"wait {n}")
        a(f"shot b5f_{sp}_{who}_stop", "wait 30", "cmd awaken admin stroll @e[tag=walker] 0 30 6", "wait 60",
          f"shot b5f_{sp}_{who}_turned")
        clear(a)
a("cmd gamemode creative", "info", "quit")
open(sys.argv[1], "w").write("\n".join(L) + "\n")
