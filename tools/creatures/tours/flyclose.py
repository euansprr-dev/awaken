# writes tools/autopilot/mob-anims-flyclose.txt
import math
import sys
import os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from tourlib import *  # noqa: E402,F403
L = []
def a(*xs): L.extend(xs)
FLOAT = "NoAI:1b,NoGravity:1b,PersistenceRequired:1b"
header(a, "flyclosefa", 779, (
    "Creatures to the Fresh Animations bar (SPEC 23.1, Flight), close up and in half-speed time: each flier circles a",
    "camera at its own distance, a cruise lap then half a slower one, the camera at its middle height turning with it",
    "from the middle of the circle, so the bank, the beat, the glides and the head held level stay big and whole in",
    "frame; vanilla's beside it under Fresh Animations, a little further round the same circle (a parrot, a bee, a cod).",
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


a("# the Hollow Owl (the layer's own wingbeat, the body riding the downstroke), a parrot beside it")
circle("fly_owl", (("awaken:hollow_owl", -14), ("minecraft:parrot", 16)), 0.12, 3.6, 204.0, 0.6)
a("# the Empress Moth, a wide flier, further out")
circle("fly_empress", (("awaken:empress_moth", 0),), 0.12, 5.5, 205.0, 0.6)
a("# the Lumen Moth and the Bogwick, and a bee")
circle("fly_small", (("awaken:lumen_moth", -22), ("awaken:bogwick", 0), ("minecraft:bee", 22)), 0.07, 2.8, 203.0, 0.35)
a("# the Plumed Coil (wings and a ten-link spine curving into the turn)")
circle("fly_coil", (("awaken:plumed_coil", 0),), 0.14, 3.6, 205.0, 0.4)
# the swimmers in a tank round the camera (water 201..207)
a("# the swimmers in a tank: the Glimmerfin and a cod, then the Tidesteed",
  "cmd fill -8 201 -8 9 207 9 minecraft:water", "wait 20")
circle("swim_fish", (("awaken:glimmerfin", -12), ("minecraft:cod", 12)), 0.07, 2.6, 203.5, 0.25)
circle("swim_steed", (("awaken:tidesteed", 0),), 0.1, 4.2, 203.5, 0.5)
a("cmd fill -8 201 -8 9 207 9 minecraft:air", "wait 10", "cmd gamemode creative", "info", "quit")
open(sys.argv[1], "w").write("\n".join(L) + "\n")
