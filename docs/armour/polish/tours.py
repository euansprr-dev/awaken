#!/usr/bin/env python3
"""Writes the armour polish audit tours (tools/autopilot/armour-audit-*.txt): every set in the same five views, so a
before and an after are the same picture. Run from the repo root: python3 docs/armour/polish/tours.py"""
import os

RANKS = {"E": ["wildhide", "bellguard", "rootwarden", "amberwing"],
         "D": ["beastbound", "cleftrunner", "lanternlight", "bladeward", "hornlord"],
         "C": ["gatewrought", "vaultbreaker", "frostwalker", "sunward", "shardcrown"],
         "B": ["veilwoven", "deepcarapace", "cloudrunner", "tidebreaker", "hollowheart"],
         "A": ["lordscale", "leviathan", "galewing", "halo_choir", "dreamsteel"],
         "S": ["sovereign", "starfall", "riftwalker", "nightcourt", "signals_echo"]}
PIECES = [("head", "helmet"), ("chest", "chestplate"), ("legs", "leggings"), ("feet", "boots")]
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "../../../tools/autopilot")


def head(name, seed, what):
    return [f"# Armour polish audit ({what}). Written by docs/armour/polish/tours.py: edit that, not this.",
            "# A studio at y 200, noon, a mid-grey wall behind, Euan's window size.",
            "# ci knobs: LENS=cinematic",
            f"world {name} creative {seed}", "waitworld", "wait 60", "cmd awaken opening skip", "wait 40",
            "cmd awaken powers starter 1", "screen close", "gui 0", "window 1708 960",
            "cmd gamerule send_command_feedback false", "cmd gamerule spawn_mobs false",
            "cmd gamerule awaken:fauna_spawning false", "cmd gamerule advance_time false",
            "cmd weather clear 1000000", "cmd time set 6000", "screen close", "cmd gamemode creative"]


def wear(s):
    return [f"cmd item replace entity @s armor.{slot} with awaken:{s}_{piece}" for slot, piece in PIECES]


def stand(s, x, z, yaw=200, mastery=0):
    comp = f',components:{{"awaken:forged":{{set:"{s}",mastery:{mastery}}}}}' if mastery else ""
    eq = ",".join(f'{slot}:{{id:"awaken:{s}_{piece}"{comp}}}' for slot, piece in PIECES)
    return f"cmd summon minecraft:armor_stand {x} 200 {z} {{ShowArms:1b,Rotation:[{yaw}f,0f],equipment:{{{eq}}}}}"


def worn(name, seed, sets):
    L = head(name, seed, "worn: front, back, first person, the Status figure")
    L += ["cmd tp @s 0.5 200 0.5 0 0", "cmd forceload add -16 -16 16 16", "wait 300",
          "cmd fill -12 199 -8 12 199 12 minecraft:smooth_stone", "cmd fill -12 200 11 12 208 11 minecraft:light_gray_concrete",
          "cmd item replace entity @s weapon.mainhand with minecraft:air", "wait 20"]
    for s in sets:
        L += wear(s)
        L += ["hud off", "opt fov 40", "cmd tp @s 0.5 200 7.5 180 0", "camera front", "look 180 4", "wait 14", f"shot w_{s}_front",
              "cmd tp @s 0.5 200 5.5 0 0", "camera back", "look 0 8", "wait 14", f"shot w_{s}_back",
              "opt fov 70", "camera first", "hud on", "look 0 10", "wait 14", f"shot w_{s}_fp",
              "screen band status", "wait 40", f"shot w_{s}_status", "screen close", "wait 4"]
    return L + ["quit"]


def stands():
    L = head("armouraudit3", 5153, "on an armour stand and on a Fitting Stand, each set at its own station")
    allsets = [s for r in RANKS.values() for s in r]
    x1 = 6 * len(allsets) + 6
    L += ["cmd tp @s 0.5 200 0.5 0 0", f"cmd forceload add -16 -16 {x1} 16", "wait 300",
          f"cmd fill -6 199 0 {x1} 199 12 minecraft:smooth_stone", f"cmd fill -6 200 11 {x1} 208 11 minecraft:light_gray_concrete", "wait 20"]
    for i, s in enumerate(allsets):
        x = 6 * i
        suit = ",".join(f'{{id:"awaken:{s}_{piece}",count:1}}' for _, piece in PIECES)
        L += [stand(s, x - 0.7, 7.5), f"cmd setblock {x + 1} 200 7 awaken:fitting_stand[facing=north]{{suit:[{suit}]}}"]
    L += ["hud off", "camera first", "opt fov 40", "wait 40"]
    for i, s in enumerate(allsets):
        L += [f"cmd tp @s {6 * i + 0.45} 200 3.9 0 4", "wait 14", f"shot s_{s}"]
    return L + ["quit"]


def far():
    L = head("armouraudit4", 5154, "at a distance by day and by night, rank by rank; the night smear; a cast in first person")
    L += ["cmd tp @s 0.5 200 0.5 0 0", "cmd forceload add -32 -64 32 32", "wait 300",
          "cmd fill -14 199 -48 14 199 0 minecraft:smooth_stone", "cmd fill -14 199 1 14 199 14 minecraft:smooth_stone",
          "cmd fill -14 200 13 14 210 13 minecraft:light_gray_concrete", "hud off", "camera first", "wait 20"]
    for r, sets in RANKS.items():
        L += ["cmd kill @e[type=minecraft:armor_stand]", "cmd time set 6000"]
        L += [stand(s, round((len(sets) - 1) - 2.0 * i + 0.5, 1), 8.5, 180) for i, s in enumerate(sets)]
        L += ["cmd tp @s 0.5 200 -3.5 0 2", "wait 30", f"shot f_{r}_12", "cmd tp @s 0.5 200 -15.5 0 0", "wait 20", f"shot f_{r}_24",
              "cmd tp @s 0.5 200 -39.5 0 0", "wait 20", f"shot f_{r}_48",
              "cmd time set 18000", "cmd tp @s 0.5 200 -3.5 0 2", "wait 60", f"shot f_{r}_12_night",
              "cmd tp @s 0.5 200 -15.5 0 0", "wait 20", f"shot f_{r}_24_night"]
    # the smear two earlier tours caught in night rows of sets at mastery V (their auras on): A's row again at V
    L += ["cmd kill @e[type=minecraft:armor_stand]", "cmd time set 6000"]
    L += [stand(s, round(4 - 2.0 * i + 0.5, 1), 8.5, 180, 6000) for i, s in enumerate(RANKS["A"])]
    L += ["cmd tp @s 0.5 200 -3.5 0 2", "wait 30", "shot v_A_day", "cmd time set 18000", "wait 30", "shot v_A_night_a", "wait 60",
          "shot v_A_night_b", "cmd kill @e[type=minecraft:armor_stand]"]
    L += [stand(s, round(4 - 2.0 * i + 0.5, 1), 8.5, 180, 6000) for i, s in enumerate(RANKS["B"])]
    L += ["wait 30", "shot v_B_night_a", "cmd tp @s 0.5 200 1.5 0 2", "wait 20", "shot v_B_night_close",
          "cmd kill @e[type=minecraft:armor_stand]", "cmd time set 6000", "wait 20"]
    # first person while one of our own arm drawers has the arm: a power cast, slowed, in Lordscale
    L += wear("lordscale")
    L += ["cmd item replace entity @s weapon.mainhand with minecraft:air", "cmd gamemode survival", "cmd awaken admin power learn beam",
          "cmd awaken admin energy full", "cmd tp @s 0.5 200 0.5 0 0", "hud on", "look 0 6", "wait 30", "shot c_rest",
          "cmd tick rate 4", "wait 4", "cmd awaken admin power cast beam", "wait 5", "shotnow c_cast_a", "wait 5", "shotnow c_cast_b",
          "wait 6", "shotnow c_cast_c", "wait 8", "shotnow c_cast_d", "cmd tick rate 20", "wait 40"]
    return L + ["quit"]


def write(name, lines):
    with open(os.path.join(OUT, name + ".txt"), "w") as f:
        f.write("\n".join(lines) + "\n")
    print(name, sum(1 for l in lines if l.startswith("shot")), "shots")


if __name__ == "__main__":
    write("armour-audit-worn-1", worn("armouraudit1", 5151, RANKS["E"] + RANKS["D"] + RANKS["C"]))
    write("armour-audit-worn-2", worn("armouraudit2", 5152, RANKS["B"] + RANKS["A"] + RANKS["S"]))
    write("armour-audit-stands", stands())
    write("armour-audit-far", far())
