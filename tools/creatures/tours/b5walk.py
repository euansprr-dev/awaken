# writes tools/autopilot/mob-anims-b5walk.txt
import sys
import os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from tourlib import *  # noqa: E402,F403
L = []
def a(*xs): L.extend(xs)
header(a, "b5walk", 782, (
    "Creatures to the Fresh Animations bar (SPEC 23.1), batch 5: the Mire Mother, the Mycelord, the Tidesteed, the otter's",
    "bound, the marmot, the mudskipper's and the cricket's hops, the snail's crawl, with vanilla's frog under Fresh",
    "Animations first. Each filmed side on at leg height by a camera that rides beside it: a walk, a run, a walk, a stop",
    "and a half turn on the spot, then ten seconds at rest.",
    "Clips: b5_<name>, b5_<name>_idle."))
a("cmd gamemode spectator", "wait 5")
# entity, clip name, body height (blocks), walk pace, trot pace
BODIES = (("minecraft:frog", "fa_frog", 0.5, 0.05, 0.1), ("awaken:mire_mother", "mire_mother", 1.6, 0.045, 0.12),
          ("awaken:mycelord", "mycelord", 3.2, 0.035, 0.08), ("awaken:tidesteed", "tidesteed", 1.5, 0.07, 0.18),
          ("awaken:brook_otter", "otter", 0.45, 0.07, 0.18), ("awaken:scree_marmot", "marmot", 0.5, 0.07, 0.15),
          ("awaken:mudskipper", "mudskipper", 0.3, 0.06, 0.12), ("awaken:chime_cricket", "cricket", 0.25, 0.06, 0.14),
          ("awaken:gloam_snail", "snail", 0.6, 0.012, 0.02))
X0 = -10.0
for ent, name, h, walk, trot in BODIES:
    eye, dist = h * 0.5, max(1.6, h * 2.4 + 0.8)
    course = ((walk, 100), (trot, 50), (walk, 20))
    a(f"# {name}",
      f"cmd summon {ent} {X0} 201 2.0 {{{NOAI},Tags:[\"walker\"],Rotation:[-90f,0f]}}",
      f"cmd tp @s {X0} {201 + eye - 1.62:.2f} {2.0 - dist:.2f} 0 2", "look 0 2", "wait 40",
      f"cmd awaken ascent pilot rec b5_{name} {sum(n for _, n in course) + 100} 2")
    for spd, n in course:
        a(f"cmd awaken admin stroll @e[tag=walker] {spd} {n}", f"glide {spd} 0 0 {n}", f"wait {n}")
    a(f"shot b5_{name}_stop", "wait 30", "cmd awaken admin stroll @e[tag=walker] 0 30 6", "wait 60",
      f"shot b5_{name}_turned", f"cmd awaken ascent pilot rec b5_{name}_idle 200 4", "wait 210")
    clear(a)
a("cmd gamemode creative", "info", "quit")
open(sys.argv[1], "w").write("\n".join(L) + "\n")
