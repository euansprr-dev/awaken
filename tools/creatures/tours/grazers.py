# writes tools/autopilot/mob-anims-grazers.txt
import sys
import os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from tourlib import *  # noqa: E402,F403
L = []
def a(*xs): L.extend(xs)
header(a, "grazersfa", 779, (
    "Creatures to the Fresh Animations bar (SPEC 23.1): the forms' grazers (pig, cow, sheep, chicken; rungs 2 to 5 on",
    "our bodies) beside vanilla's under Fresh Animations (rungs 0 and 1). Each row at rest (feet planted, weight drifting,",
    "a re-grip now and then), then rung 3 and vanilla's side by side through a walk into a trot, a stop and a turn on the",
    "spot (knees break forward, hocks back, the head nods with the forefeet), then a blow and a death."))
# 1. the rows
for sp, rec in (("cow", 160), ("pig", 120), ("sheep", 100), ("chicken", 100)):
    a(f"# 1. the {sp}s at rest: 0 and 1 vanilla's under Fresh Animations, 2 to 5 ours",
      "cmd tp @s 0.5 201 -6.0 0 0", "wait 5", f"cmd awaken living formrow {sp}", "wait 5",
      f"cmd execute as @e[type=minecraft:{sp}] run data merge entity @s {FEET}")
    perch(a, 0.5, -7.5, 1, 0, 12)
    a("wait 40", f"shot graze_{sp}_row", f"cmd awaken ascent pilot rec graze_{sp}_row {rec} 2", f"wait {rec + 5}")
    unperch(a, 0.5, -7.5, 1)
    clear(a)
# 2. the walks: rung 3 in front, vanilla's (Fresh Animations) behind
for sp, pace, cam, far in (("cow", 0.8, -4.5, 5.0), ("pig", 0.9, -3.5, 4.5), ("sheep", 0.85, -3.5, 4.5), ("chicken", 0.6, -2.5, 4.0)):
    h = 1 if sp == "chicken" else 2
    x0 = round(-course_len(pace) / 2 - 0.3, 1)
    a(f"# 2. a {sp} walk into a trot, a stop, a half turn on the spot, a creep back: rung 3 in front, vanilla's behind",
      f"cmd tp @s {x0} 201 -2.5 0 0", "wait 5", f"cmd awaken living form {sp} 3", "wait 5",
      f"cmd tag @e[type=minecraft:{sp},limit=1,sort=nearest] add walker",
      f"cmd tp @e[tag=walker] {x0} 201 1.5 -90 0",
      f"cmd summon minecraft:{sp} {x0} 201 {far} {{{NOAI},Tags:[\"walker\"],Rotation:[-90f,0f]}}",
      f"cmd execute as @e[tag=walker] run data merge entity @s {FEET}")
    perch(a, 0.5, cam, h, 0, 26 if sp == "cow" else 28)
    a("wait 30", f"cmd awaken ascent pilot rec graze_{sp}_walk 200 2")
    stroll(a, "@e[tag=walker]", pace)
    walk_shots(a, f"graze_{sp}")
    unperch(a, 0.5, cam, h)
    clear(a)
# 3. a blow and a death: rung 3 cow and pig beside vanilla's
a("# 3. a blow (the body jerks over its planted feet), a heavier one, then the deaths",
  "cmd tp @s 0.5 201 -3.0 0 0", "wait 5")
for sp, x in (("cow", -3.0), ("pig", 1.5)):
    a(f"cmd tp @s {x} 201 -2.5 0 0", f"cmd awaken living form {sp} 3", "wait 5",
      f"cmd tag @e[type=minecraft:{sp},limit=1,sort=nearest] add ours",
      f"cmd tp @e[type=minecraft:{sp},tag=ours] {x} 201 2.0 180 0",
      f"cmd summon minecraft:{sp} {x + 2.4} 201 2.2 {{{NOAI},Tags:[\"theirs\"],Rotation:[180f,0f]}}")
a(f"cmd execute as @e[tag=ours] run data merge entity @s {FEET}", f"cmd execute as @e[tag=theirs] run data merge entity @s {FEET}")
perch(a, 0.5, -4.5, 2, 0, 26)
a("wait 30", "cmd awaken ascent pilot rec graze_hit 90 2",
  "cmd execute as @e[tag=ours] run damage @s 1 minecraft:player_attack by @p",
  "cmd execute as @e[tag=theirs] run damage @s 1 minecraft:player_attack by @p", "wait 3", "shot graze_hit", "wait 37",
  "cmd execute as @e[tag=ours] run damage @s 2 minecraft:player_attack by @p",
  "cmd execute as @e[tag=theirs] run damage @s 2 minecraft:player_attack by @p", "wait 50",
  "cmd awaken ascent pilot rec graze_death 50 2", "cmd kill @e[tag=ours]", "cmd kill @e[tag=theirs]", "wait 12",
  "shot graze_death_mid", "wait 40")
unperch(a, 0.5, -4.5, 2)
clear(a)
a("info", "quit")
open(sys.argv[1], "w").write("\n".join(L) + "\n")
