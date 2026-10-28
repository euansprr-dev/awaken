#!/usr/bin/env python3
"""Writes the power-level look tours (SPEC 36): every power that casts, at levels 1, 3 and 5, two shots each.

  python3 tools/qa/power_level_tours.py        -> tools/autopilot/power-levels-{a,b,c,d}.txt
  tools/tour-ci.sh tools/autopilot/power-levels-a.txt 2400 PACK=feel   (one per group, all four at once)

The HUD is off (F1) so the chat and hints never cover a cast; one shot a tour shows the bar's tier frames.
Each cast is slowed to a quarter speed (/tick rate 5: the runner renders a few frames a second) and shot twice: from the
front at the release (the level's gesture and the first of its look), then from behind a few ticks on (what it does to
the husks). Charges that fire when the hold runs out are shot at that tick; held beams and toggles mid-way. The arena is
rebuilt and the husks set out again before every cast, so a construct, a crater or a dominated husk never carries over.
"""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CATALOG = ROOT / "src/main/java/dev/awaken/powers/catalog"
CASTS = ROOT / "src/main/resources/data/awaken/powers/casts.json"
OUT = ROOT / "tools/autopilot"

GROUPS = {
    "a": ["KINETIC", "VELOCITY", "FLIGHT"],
    "b": ["ENERGY", "SHADOW", "MIND"],
    "c": ["SPIRIT"],
    "d": ["TECH", "ARCHITECT"],
    # the powers whose looks repeat on a timer (they never drew before 469af568), and Aura standing
    "e": ["beam", "prism_lance", "cloak", "drone", "flight", "aura"],
}
LEVELS = (1, 3, 5)
# what a power needs standing before it casts: a construct to throw, a decoy to swap with
BEFORE = {"collapse": "rampart", "echo_swap": "decoy"}
HUSK = '{NoAI:1b,PersistenceRequired:1b,Rotation:[180f,0f],Health:200f,attributes:[{id:"minecraft:max_health",base:200d}]}'
HUSKS = [(1.8, 1.4), (-1.5, 7.5), (2.5, 11.5)]  # the first in melee reach at your side, out of the front camera's line


def catalog():
    """(id, name, family, slot, mode, max ticks) for every power, in catalog order."""
    out = []
    for f in sorted(CATALOG.glob("*.java")):
        src = f.read_text()
        for m in re.finditer(r'PowerType\.builder\("([a-z_]+)", "([^"]+)", Family\.([A-Z]+), Slot\.([A-Z]+)\)(.*?)\.register\(\)', src, re.S):
            pid, name, fam, slot, body = m.groups()
            mode = re.search(r'\.(channel|toggle)\(\s*[^,]+,\s*(\d+)\)', body)
            out.append((pid, name, fam, slot, mode.group(1) if mode else "instant", int(mode.group(2)) if mode else 0))
    return out


def releases():
    rows = json.loads(CASTS.read_text())["profiles"]
    return {r["power"]: r["release"] for r in rows}


CMD_WAIT = 5  # the autopilot waits this long after every cmd line, the cast's included


HELD = ("beam", "telekinesis")  # channels that hold rather than charge


def timing(pid, mode, max_ticks, release):
    """Ticks after the cast to the front shot, then to the back shot (after the camera's own two)."""
    if mode == "toggle":
        return 10, 12
    if mode == "channel" and pid in HELD:
        return 8, 6
    if mode == "channel":  # a charge: it fires when the hold runs out
        return max_ticks + 1, 3
    return release.get(pid, 3) + 1, 3


def arena():
    return [
        "cmd tp @e[type=minecraft:husk] 0.5 120 0.5",
        "cmd kill @e[type=minecraft:husk]",
        "cmd fill -14 200 -6 14 214 21 minecraft:air",
        "cmd fill -14 199 -6 14 199 30 minecraft:smooth_stone",
        "cmd tp @s 0.5 200 0.5 0 0",
    ] + [f"cmd summon minecraft:husk {x} 200 {z} {HUSK}" for x, z in HUSKS]


def segment(pid, name, mode, max_ticks, level, release, passive=False):
    front, back = timing(pid, mode, max_ticks, release)
    lines = [f"# ---- {name} {level}"] + arena()
    if passive:  # a passive's look is always on: stand in it
        return lines + [f"cmd awaken admin power showcase {pid} {level}", "camera front", "look 0 4", "wait 40",
                        f"shotnow pl_{pid}_{level}_front", "camera back", "wait 8", f"shotnow pl_{pid}_{level}_back", ""]
    if pid in BEFORE:
        lines += [f"cmd awaken admin power showcase {BEFORE[pid]} 1", f"cmd awaken admin power cast {BEFORE[pid]}", "wait 12"]
    lines += [
        f"cmd awaken admin power showcase {pid} {level}",
        "camera front",
        "look 0 4",
        "wait 14",
        "cmd tick rate 5",
        "wait 2",
        f"cmd awaken admin power cast {pid}",
        f"wait {max(0, front - CMD_WAIT)}",
        f"shotnow pl_{pid}_{level}_front",
        "camera back",
        f"wait {back}",
        f"shotnow pl_{pid}_{level}_back",
    ]
    if mode == "toggle":
        lines.append(f"cmd awaken admin power cast {pid}")
    # a held channel runs its full length (nobody lets go): wait it out, or the next level's cast is "busy"
    held = max_ticks if mode == "channel" and pid in HELD else 0
    lines += ["cmd tick rate 20", f"wait {30 + held}", ""]
    return lines


def tour(group, families, powers, release):
    title = ", ".join(f.replace("_", " ").title() for f in families)
    head = [
        f"# Power levels (SPEC 36), {title}: every power that casts at levels 1, 3 and 5, from the front at the release and",
        "# from behind a few ticks on, at a quarter speed. Generated by tools/qa/power_level_tours.py: edit that, not this.",
        f"# Look tour: tools/tour-ci.sh tools/autopilot/power-levels-{group}.txt 2400 PACK=feel",
        f"world powerlevels{group} survival 4262",
        "waitworld",
        "wait 60",
        "cmd awaken opening skip",
        "wait 40",
        "window 1440 900",
        "gui 0",
        "hud off",
        "cmd gamerule awaken:fauna_spawning false",
        "cmd gamerule show_advancement_messages false",
        "cmd gamerule send_command_feedback false",
        "cmd gamerule spawn_mobs false",
        "cmd gamerule advance_time false",
        "cmd gamerule fall_damage false",
        "cmd weather clear 100000",
        "cmd time set 5000",
        "cmd awaken admin awaken",
        "wait 200",
        "cmd awaken powers starter 1",
        "wait 20",
        "screen close",
        "cmd effect give @s minecraft:saturation 100000 10 true",
        "cmd effect give @s minecraft:resistance 100000 4 true",
        "cmd effect give @s minecraft:slow_falling 60 1 true",
        "cmd tp @s 0.5 201 0.5 0 12",
        "wait 400",
        "cmd fill -14 199 -6 14 199 30 minecraft:smooth_stone",
        "cmd fill -14 200 -6 14 214 30 minecraft:air",
        "cmd fill -14 200 22 14 210 22 minecraft:deepslate_tiles",
        "wait 40",
        "",
    ]
    body = []
    for pid, name, fam, slot, mode, max_ticks in powers:
        named = pid in families  # a group of powers by id rather than families
        if not named and (fam not in families or slot == "PASSIVE"):
            continue
        for lv in LEVELS:
            body += segment(pid, name, mode, max_ticks, lv, release, named and slot == "PASSIVE")
    return head + body + hud_shot(group, families, powers) + ["quit", ""]


def hud_shot(group, families, powers):
    """The bar with the group's first three powers at level 5 (their tier frames), chat cleared by "shot"."""
    picks = [(pid, slot) for pid, _, fam, slot, _, _ in powers if (pid in families or fam in families) and slot != "PASSIVE"]
    lines = ["# ---- the HUD's tier frames at level 5"] + arena()
    seen = set()
    for pid, slot in picks:
        if slot in seen:
            continue
        seen.add(slot)
        lines.append(f"cmd awaken admin power showcase {pid} 5")
    return lines + ["camera back", "look 0 4", "hud on", "wait 20", f"shot pl_hud_{group}", "hud off", ""]


def main():
    powers = catalog()
    release = releases()
    for group, families in GROUPS.items():
        lines = tour(group, families, powers, release)
        (OUT / f"power-levels-{group}.txt").write_text("\n".join(lines))
        n = sum(1 for l in lines if l.startswith("shotnow"))
        print(f"power-levels-{group}.txt: {n} shots")


if __name__ == "__main__":
    main()
