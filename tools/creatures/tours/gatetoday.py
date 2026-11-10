# writes tools/autopilot/monsters-today.txt
# Evidence for docs/gates-as-worlds/monsters/PLAN.md: what a gate's monsters look like today, close up.
import sys
import os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from tourlib import *  # noqa: E402,F403
L = []
def a(*xs): L.extend(xs)
header(a, "monsterstoday", 811, (
    "Gate monsters today (plan docs/gates-as-worlds/monsters/PLAN.md): each Bell Crypt monster beside the vanilla mob whose",
    "body it wears, then the Drip Lurker and the Hushling (bodies of our own) alone, then a Bell Crypt row and a Hive row",
    "seen from 8 and from 20 blocks. Stills only; nothing here is new, it films what is already in the game."))
a("cmd gamemode spectator", "cmd time set 13000", "wait 20")
FREEZE = "NoAI:1b,PersistenceRequired:1b,Silent:1b"


def put(ent, x, z, yaw, tag, fly=False):
    """Summoned plain (so it gets its own gear), then frozen and turned."""
    y = 202.2 if fly else 201
    a(f"cmd summon {ent} {x} {y} {z}", "wait 3",
      f"cmd data merge entity @e[type={ent},limit=1,sort=nearest,x={x},y={y},z={z},distance=..2] "
      f"{{{FREEZE}{',NoGravity:1b' if fly else ''},Tags:[\"{tag}\"],Rotation:[{yaw}f,0f]}}")


def cam(x, eye, z, pitch=4):
    a(f"cmd tp @s {x} {201 + eye - 1.62:.2f} {z:.2f} 0 {pitch}", f"look 0 {pitch}", "wait 40")


# 1. each Bell Crypt monster (left) beside the vanilla mob it is drawn on (right)
PAIRS = (("toll_ghoul", "zombie", 1.95, False), ("crypt_archer", "skeleton", 1.99, False), ("bell_wisp", "vex", 0.8, True),
         ("crypt_keeper", "wither_skeleton", 2.4, False), ("the_sexton", "warden", 2.9, False))
for ours, van, h, fly in PAIRS:
    a(f"# {ours} beside minecraft:{van}")
    put(f"awaken:{ours}", 1.1, 6.0, 165, "p", fly)
    put(f"minecraft:{van}", -1.1, 6.0, 195, "p", fly)
    cam(0.0, h * 0.55 + (1.2 if fly else 0), 6.0 - max(2.6, h * 1.5 + 1.0))
    a(f"shot pair_{ours}", "wait 10")
    clear(a)

# 2. the bar: two bodies of our own, alone and close
for ours, h in (("drip_lurker", 1.1), ("hushling", 0.9)):
    put(f"awaken:{ours}", 0.0, 6.0, 150, "p")
    cam(0.0, h * 0.7, 6.0 - 2.6, 8)
    a(f"shot bar_{ours}", "wait 10")
    clear(a)

# 3. rows, from 8 and from 20 blocks: what reads at fighting distance
ROWS = (("bell", ("toll_ghoul", "crypt_archer", "bell_wisp", "crypt_keeper", "the_sexton", "drip_lurker", "hushling")),
        ("hive", ("drone_bee", "hive_larva", "honey_slime", "hive_guard", "the_swarm_mind", "drip_lurker", "thousandfoot")))
FLY = {"bell_wisp", "drone_bee", "the_swarm_mind"}
for row, ids in ROWS:
    a(f"# the {row} row")
    for k, mid in enumerate(ids):
        put(f"awaken:{mid}", round((k - (len(ids) - 1) / 2) * 2.6, 2), 26.0, 180, "r", mid in FLY)
    cam(0.0, 1.6, 18.0, 2)
    a(f"shot row_{row}_8", "wait 10")
    cam(0.0, 1.6, 6.0, 0)
    a(f"shot row_{row}_20", "wait 10")
    clear(a)
a("cmd gamemode creative", "info", "quit")
open(sys.argv[1], "w").write("\n".join(L) + "\n")
