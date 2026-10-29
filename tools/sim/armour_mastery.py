#!/usr/bin/env python3
"""How long a set takes to reach each mastery step (SPEC §44.5, "Mastery"), read from the Java.

A whole set earns XP_BLOW for every blow dealt or taken, XP_STRONG for an elite's or a warden's kill and XP_LORD for a
gate Lord's; the steps are Forged.MASTERY. The fight rates below are assumptions, kept beside the pacing sim's own
(tools/sim/pacing_sim.py: KILLS_PER_COMBAT_MIN 1.5, elites 5% of kills from B land). Run: python3 tools/sim/armour_mastery.py
"""
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "src/main/java/dev/awaken/armour"


def const(file, name):
    m = re.search(rf"\b{name} = (\d+)", (JAVA / file).read_text())
    return int(m.group(1))


XP_BLOW = const("ArmourAbilities.java", "XP_BLOW")
XP_STRONG = const("ArmourAbilities.java", "XP_STRONG")
XP_LORD = const("ArmourAbilities.java", "XP_LORD")
STEPS = [int(x) for x in re.search(r"MASTERY = \{([\d, ]+)\}", (JAVA / "Forged.java").read_text()).group(1).split(", ")]

# a minute of fighting: blows landed (melee and powers' hits the arms count), blows taken, kills and the elite share
HITS_PER_MIN, TAKEN_PER_MIN, KILLS_PER_MIN, ELITE_SHARE = 18, 5, 1.5, 0.05
# a gate Lord felled every this many minutes of fighting (a gate's floors and its Lord)
MIN_PER_LORD = 20


def per_minute(lords):
    xp = (HITS_PER_MIN + TAKEN_PER_MIN) * XP_BLOW + KILLS_PER_MIN * ELITE_SHARE * XP_STRONG
    return xp + (XP_LORD / MIN_PER_LORD if lords else 0)


def main():
    print(f"XP: blow {XP_BLOW}, elite or warden {XP_STRONG}, gate Lord {XP_LORD}; steps {STEPS}")
    for lords, label in ((False, "hunting the wilds"), (True, "running gates")):
        rate = per_minute(lords)
        cells = ", ".join(f"{['I', 'II', 'III', 'IV', 'V'][i]} {STEPS[i] / rate / 60:.1f} h" for i in range(1, len(STEPS)))
        print(f"{label:18} {rate:5.1f} xp a combat minute: {cells}")


if __name__ == "__main__":
    main()
