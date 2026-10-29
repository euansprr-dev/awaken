# writes tools/autopilot/mob-anims-b4walk.txt
import sys
import os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from tourlib import *  # noqa: E402,F403
L = []
def a(*xs): L.extend(xs)
header(a, "b4walk", 781, (
    "Creatures to the Fresh Animations bar (SPEC 23.1), batch 4: the hand-built walkers on planted one-bone feet, the",
    "root carrying the body (stag, mossback, basilisk, scarab, shroomlet, Listener) at their own wander and run paces.",
    "Each filmed side on at leg height by a camera that rides beside it: a walk, a run, a walk, a stop and a half turn",
    "on the spot, then ten seconds at rest.",
    "Clips: b4_<name>, b4_<name>_idle."))
a("cmd gamemode spectator", "wait 5")
# entity, clip name, body height (blocks), walk pace, trot pace
BODIES = (("awaken:frost_stag", "stag", 1.6, 0.07, 0.2), ("awaken:mossback", "tortoise", 0.7, 0.015, 0.03),
          ("awaken:crested_basilisk", "basilisk", 0.6, 0.07, 0.16), ("awaken:glass_scarab", "scarab", 0.3, 0.1, 0.18),
          ("awaken:shroomlet", "shroomlet", 0.95, 0.06, 0.11), ("awaken:listener", "listener", 2.4, 0.12, 0.22))
X0 = -10.0
for ent, name, h, walk, trot in BODIES:
    eye, dist = h * 0.5, max(1.6, h * 2.4 + 0.8)
    course = ((walk, 100), (trot, 50), (walk, 20))
    a(f"# {name}",
      f"cmd summon {ent} {X0} 201 2.0 {{{NOAI},Tags:[\"walker\"],Rotation:[-90f,0f]}}",
      f"cmd tp @s {X0} {201 + eye - 1.62:.2f} {2.0 - dist:.2f} 0 2", "look 0 2", "wait 40",
      f"cmd awaken ascent pilot rec b4_{name} {sum(n for _, n in course) + 100} 2")
    for spd, n in course:
        a(f"cmd awaken admin stroll @e[tag=walker] {spd} {n}", f"glide {spd} 0 0 {n}", f"wait {n}")
    a(f"shot b4_{name}_stop", "wait 30", "cmd awaken admin stroll @e[tag=walker] 0 30 6", "wait 60",
      f"shot b4_{name}_turned", f"cmd awaken ascent pilot rec b4_{name}_idle 200 4", "wait 210")
    clear(a)
a("cmd gamemode creative", "info", "quit")
open(sys.argv[1], "w").write("\n".join(L) + "\n")
