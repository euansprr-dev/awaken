# writes tools/autopilot/mob-anims-beastclose.txt
import sys
import os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from tourlib import *  # noqa: E402,F403
L = []
def a(*xs): L.extend(xs)
header(a, "beastclosefa", 779, (
    "Creatures to the Fresh Animations bar (SPEC 23.1), close up: the hoofed beasts on rigid legs (the Briar Sow, the Old",
    "Antler, the Stonehorn) filmed side on at leg height by a camera that rides beside each, on the close tour's course: a",
    "walk, a trot, a walk, a stop and a half turn on the spot, then half a minute at rest. Vanilla's pig under Fresh",
    "Animations walks the same course first, for the sow. Clips: bclose_<name>, bclose_<name>_idle."))
a("cmd gamemode spectator", "wait 5")
# entity, clip name, eye height over the floor top (201), camera distance, walk pace, trot pace
BEASTS = (("minecraft:pig", "fapig", 0.55, 2.0, 0.05, 0.12), ("awaken:briar_sow", "sow", 0.9, 3.4, 0.05, 0.12),
          ("awaken:old_antler", "antler", 1.2, 4.2, 0.06, 0.14), ("awaken:stonehorn", "ram", 1.2, 4.6, 0.05, 0.12))
X0 = -10.0
for ent, name, eye, dist, walk, trot in BEASTS:
    course = ((walk, 100), (trot, 50), (walk, 20))
    a(f"# {name}",
      f"cmd summon {ent} {X0} 201 2.0 {{{NOAI},Tags:[\"walker\"],Rotation:[-90f,0f]}}",
      f"cmd tp @s {X0} {201 + eye - 1.62:.2f} {2.0 - dist} 0 2", "look 0 2", "wait 40",
      f"cmd awaken ascent pilot rec bclose_{name} {sum(n for _, n in course) + 100} 2")
    for spd, n in course:
        a(f"cmd awaken admin stroll @e[tag=walker] {spd} {n}", f"glide {spd} 0 0 {n}", f"wait {n}")
    a(f"shot bclose_{name}_stop", "wait 30", "cmd awaken admin stroll @e[tag=walker] 0 30 6", "wait 60",
      f"shot bclose_{name}_turned", f"cmd awaken ascent pilot rec bclose_{name}_idle 600 4", "wait 610")
    clear(a)
a("cmd gamemode creative", "info", "quit")
open(sys.argv[1], "w").write("\n".join(L) + "\n")
