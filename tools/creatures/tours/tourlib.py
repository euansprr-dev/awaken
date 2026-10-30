# Shared pieces of the mob-anims tours. Each generator here writes its tools/autopilot/mob-anims-<name>.txt:
#   python3 -I tools/creatures/tours/b4walk.py tools/autopilot/mob-anims-b4walk.txt
NOAI = "NoAI:1b,OnGround:1b,PersistenceRequired:1b"
FEET = "{OnGround:1b,Invulnerable:0b,PersistenceRequired:1b}"


def header(a, world, seed, about):
    a(*["# " + s for s in about],
      "# Clips: run-autopilot/clips/<name> (python3 -I tools/ascent/clip.py <dir> makes an mp4 and a contact sheet).",
      "# ci knobs: PACK=perf,feel",
      f"world {world} creative {seed}", "waitworld", "wait 40",
      "# the opening's sky flight holds the camera: skip it first", "cmd awaken opening skip", "wait 30",
      "# the studio's chunks first: a busy runner's server can fall seconds behind the script",
      "cmd tp @s 0.5 201 0.5 0 0", "cmd forceload add -32 -32 32 48",
      "gui 0",
      "cmd gamerule send_command_feedback false", "cmd gamerule awaken:fauna_spawning false", "cmd gamerule spawn_mobs false",
      "cmd weather clear 1000000", "cmd time set 6000", "cmd awaken admin awaken", "wait 200",
      "cmd awaken powers starter 1", "wait 20", "screen close", "camera first", "hud off",
      "cmd effect give @s minecraft:resistance infinite 4 true", "cmd effect give @s minecraft:regeneration infinite 4 true",
      "cmd effect give @s minecraft:saturation infinite 0 true", "cmd effect give @s minecraft:night_vision infinite 0 true",
      "info", "cmd tp @s 0.5 201 0.5 0 0", "wait 100")
    for _ in range(2):
        a("cmd fill -24 200 -16 24 200 40 minecraft:smooth_stone", "cmd fill -24 201 -16 24 214 40 minecraft:air", "wait 40")
    clear(a)


def clear(a):
    """Every creature gone, then the drops and orbs their deaths leave (they fall a second later)."""
    a("cmd kill @e[type=!minecraft:player,distance=..80]", "wait 25",
      "cmd kill @e[type=minecraft:item]", "cmd kill @e[type=minecraft:experience_orb]", "wait 5")


def perch(a, x, z, h, yaw, pitch):
    """The camera on an invisible block h blocks over the floor (the player would drop to eye height otherwise)."""
    bx, bz = int(x // 1), int(z // 1)
    a(f"cmd fill {bx} 201 {bz} {bx} {200 + h} {bz} minecraft:barrier", f"cmd tp @s {x} {201 + h} {z} {yaw} {pitch}", f"look {yaw} {pitch}")


def unperch(a, x, z, h):
    bx, bz = int(x // 1), int(z // 1)
    a(f"cmd fill {bx} 201 {bz} {bx} {200 + h} {bz} minecraft:air")


# a walk into a run, a slowing, a stop, a half turn on the spot (6 degrees a tick), a creep back; scaled by the body's pace
COURSE = ((0.05, 40, None), (0.09, 16, None), (0.16, 6, None), (0.2, 16, None), (0.1, 4, None), (0.05, 4, None),
          (0, 26, None), (0, 30, 6), (0, 10, None), (0.04, 30, None))


def course_len(pace):
    return sum(round(sp * pace, 3) * n for sp, n, t in COURSE if not t)


def stroll(a, sel, pace=1.0):
    for sp, n, turn in COURSE:
        a(f"cmd awaken admin stroll {sel} {round(sp * pace, 3)} {n}" + (f" {turn}" if turn else ""))


def walk_shots(a, name):
    """Shots through the course: mid walk, the run, the stop, after the turn (the clip covers it all)."""
    a("wait 45", f"shot {name}_walk_mid", "wait 30", f"shot {name}_run", "wait 30", f"shot {name}_stop", "wait 45",
      f"shot {name}_turned", "wait 45")
