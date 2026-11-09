# writes tools/autopilot/mob-anims-amber.txt
import sys
import os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from tourlib import *  # noqa: E402,F403
L = []
def a(*xs): L.extend(xs)
header(a, "amberlife", 783, (
    "Creatures to the Fresh Animations bar (SPEC 23.1), Amber Reach's life: the Pollen Grazer after vanilla's cow, the",
    "Wax-folk after a villager, both under Fresh Animations, then the amberfly circling beside a bee. Each walker filmed",
    "side on at leg height by a camera riding beside it: a walk, a run, a walk, a stop and a half turn on the spot, ten",
    "seconds at rest, then a blow and a death. Clips: amb_<name>, amb_<name>_idle, amb_<name>_end, amb_fly."))
a("cmd gamemode spectator", "wait 5")
BODIES = (("minecraft:cow", "fa_cow", 1.4, 0.05, 0.11), ("awaken:pollen_grazer", "grazer", 1.4, 0.045, 0.1),
          ("minecraft:villager", "fa_villager", 1.9, 0.04, 0.08), ("awaken:wax_folk", "folk", 1.6, 0.03, 0.07))
X0 = -10.0
for ent, name, h, walk, trot in BODIES:
    eye, dist = h * 0.5, max(1.6, h * 2.4 + 0.8)
    course = ((walk, 100), (trot, 50), (walk, 20))
    a(f"# {name}",
      f"cmd summon {ent} {X0} 201 2.0 {{{NOAI},Tags:[\"walker\"],Rotation:[-90f,0f]}}",
      f"cmd tp @s {X0} {201 + eye - 1.62:.2f} {2.0 - dist:.2f} 0 2", "look 0 2", "wait 40",
      f"cmd awaken ascent pilot rec amb_{name} {sum(n for _, n in course) + 100} 2")
    for spd, n in course:
        a(f"cmd awaken admin stroll @e[tag=walker] {spd} {n}", f"glide {spd} 0 0 {n}", f"wait {n}")
    x1 = X0 + sum(s * n for s, n in course)
    a(f"shot amb_{name}_stop", "wait 30", "cmd awaken admin stroll @e[tag=walker] 0 30 6", "wait 60",
      f"shot amb_{name}_turned", f"cmd awaken ascent pilot rec amb_{name}_idle 200 4", "wait 210",
      f"cmd tp @s {x1:.2f} {201 + eye - 1.62:.2f} {2.0 - dist:.2f} 0 2", "look 0 2", "wait 10",
      f"cmd awaken ascent pilot rec amb_{name}_end 100 2", "wait 10",
      "cmd execute as @e[tag=walker] run damage @s 2 minecraft:player_attack", "wait 3", f"shot amb_{name}_hit", "wait 32",
      "cmd kill @e[tag=walker]", "wait 12", f"shot amb_{name}_falling", "wait 20", f"shot amb_{name}_dead", "wait 30")
    clear(a)
# the fly and a bee, round a camera in half time
FLOAT = "NoAI:1b,NoGravity:1b,PersistenceRequired:1b"
import math
cx, cz, r, v, y = 0.5, 0.5, 1.6, 0.09, 202.0
w = v / r * 180.0 / math.pi
for m, b in (("awaken:amberfly", 0.0), ("minecraft:bee", 40.0)):
    t = math.radians(b)
    a(f"cmd summon {m} {cx - r * math.sin(t):.2f} {y} {cz + r * math.cos(t):.2f} {{{FLOAT},Tags:[\"flier\"],Rotation:[{90 + b:.1f}f,0f]}}")
n = int(round(360 / w))
a(f"cmd tp @s {cx} {y + 0.1 - 1.62:.2f} {cz} 0 0", "look 0 0", "wait 30", "cmd tick rate 10", "wait 5",
  f"cmd awaken ascent pilot rec amb_fly {n + 90} 2", "cmd awaken admin stroll @e[tag=flier] 0 40", "wait 40",
  f"cmd awaken admin stroll @e[tag=flier] {v:.3f} {n} {w:.3f}", f"turn {w:.3f} {n}", f"wait {n // 2}", "shot amb_fly_cruise", f"wait {n - n // 2}",
  "cmd awaken admin stroll @e[tag=flier] 0 40", "wait 45", "cmd tick rate 20", "wait 5")
clear(a)
a("cmd gamemode creative", "info", "quit")
open(sys.argv[1], "w").write("\n".join(L) + "\n")
