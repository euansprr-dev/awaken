# writes tools/autopilot/mob-anims-flyclose2.txt
import math
import sys
import os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from tourlib import *  # noqa: E402,F403
L = []
def a(*xs): L.extend(xs)
FLOAT = "NoAI:1b,NoGravity:1b,PersistenceRequired:1b"
header(a, "flyclose2", 783, (
    "Creatures to the Fresh Animations bar (SPEC 23.1, Flight), the rest of the fliers and swimmers (flyclose has the",
    "owl, the moths, the bogwick, the coil, the glimmerfin and the Tidesteed), close up and in half-speed time: each circles a",
    "camera at its own distance, a cruise lap then half a slower one, the camera at its middle height turning with it",
    "from the middle of the circle, so the bank, the beat, the glides and the head held level stay big and whole in",
    "frame; vanilla's beside it under Fresh Animations, a little further round the same circle (a bee, a cod).",
    "Clips: fly_<name>."))
a("cmd gamemode spectator", "wait 5")


def circle(name, mobs, v, r, y, mid):
    """mobs: (entity id, degrees round the circle from the camera's first look): each starts that far round, facing
    along the circle, and flies it at v blocks a tick, r blocks out; the camera at the middle, its eye at the bodies'
    middle (mid over y), turns with them."""
    cx, cz = 0.5, 0.5
    w = v / r * 180.0 / math.pi
    for m, b in mobs:
        t = math.radians(b)
        a(f"cmd summon {m} {cx - r * math.sin(t):.2f} {y} {cz + r * math.cos(t):.2f} "
          f"{{{FLOAT},Tags:[\"flier\"],Rotation:[{90 + b:.1f}f,0f]}}")
    laps = ((v, w, 360), (v * 0.6, w * 0.6, 180))
    total = 40 + sum(int(round(deg / ww)) for _, ww, deg in laps) + 45
    a(f"cmd tp @s {cx} {y + mid - 1.62:.2f} {cz} 0 0", "look 0 0", "wait 30", "cmd tick rate 10", "wait 5",
      f"cmd awaken ascent pilot rec {name} {total + 5} 2",
      "cmd awaken admin stroll @e[tag=flier] 0 40", "wait 40")
    for spd, ww, deg in laps:
        n = int(round(deg / ww))
        a(f"cmd awaken admin stroll @e[tag=flier] {spd:.3f} {n} {ww:.3f}",
          f"turn {ww:.3f} {n}", f"wait {n // 2}", f"shot {name}_{'cruise' if ww == w else 'slow'}", f"wait {n - n // 2}")
    a("cmd awaken admin stroll @e[tag=flier] 0 40", "wait 45", "cmd tick rate 20", "wait 5")
    clear(a)


a("# the Petalwing and a bee")
circle("fly_petal", (("awaken:petalwing", -14), ("minecraft:bee", 14)), 0.07, 2.8, 203.0, 0.35)
a("# the Breathbell, drifting slowly")
circle("fly_bell", (("awaken:breathbell", 0),), 0.05, 3.0, 204.0, 0.5)
a("# the sand swimmers in open air: the Dune Ray, the Dune Wyrm, the Cord Eater")
circle("fly_ray", (("awaken:dune_ray", 0),), 0.12, 4.5, 202.5, 0.3)
circle("sand_wyrm", (("awaken:dune_wyrm", 0),), 0.12, 5.5, 202.0, 0.5)
circle("sand_cord", (("awaken:cord_eater", 0),), 0.1, 3.5, 202.0, 0.3)
a("# the swimmers in a tank: the Lantern Maw and a cod, the eels, the otter",
  "cmd fill -8 201 -8 9 207 9 minecraft:water", "wait 20")
circle("swim_maw", (("awaken:lantern_maw", -12), ("minecraft:cod", 12)), 0.07, 3.0, 203.5, 0.3)
circle("swim_eels", (("awaken:deep_eel", -12), ("awaken:tide_eel", 12)), 0.09, 3.6, 203.5, 0.25)
circle("swim_otter", (("awaken:brook_otter", 0),), 0.09, 2.8, 203.5, 0.25)
a("cmd fill -8 201 -8 9 207 9 minecraft:air", "wait 10", "cmd gamemode creative", "info", "quit")
open(sys.argv[1], "w").write("\n".join(L) + "\n")
