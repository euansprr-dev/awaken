#!/usr/bin/env python3
"""Gear row of the pacing sim (docs/arms-progression/PLAN.md §3.7): how many Gate Lords a fighter kills for one weapon.

The rates are read from data/awaken/arms/style_homes.json (Spoils by rank, the weapon pity) and LordForged.java's own
constants (35%, certain by the third), so this follows the game when they change. Monte Carlo over the two rolls and both
pities, seeded.

    python3 tools/sim/gear_sim.py            the table
"""
import json
import os
import random
import re

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
HOMES = json.load(open(os.path.join(ROOT, "src/main/resources/data/awaken/arms/style_homes.json")))
LF = open(os.path.join(ROOT, "src/main/java/dev/awaken/arms/LordForged.java")).read()
LF_CHANCE = float(re.search(r"CHANCE = ([0-9.]+)F", LF).group(1))
LF_PITY = int(re.search(r"PITY = (\d+)", LF).group(1))
SPOILS, SPOILS_PITY = HOMES["spoils"]["chance"], HOMES["spoils"]["pity"]
# Lord-forged weapons exist for the Lords of E to A (SPEC §25); S has none until the Sovereign weapons are built
LORD_FORGED_RANKS = {"E", "D", "C", "B", "A"}


def one(rank, rng):
    """Lords killed until the first weapon of any kind, for one fighter."""
    forged_without = 0
    without = 0
    n = 0
    while True:
        n += 1
        forged = False
        if rank in LORD_FORGED_RANKS:
            forged = forged_without + 1 >= LF_PITY or rng.random() < LF_CHANCE
            forged_without = 0 if forged else forged_without + 1
        if forged:
            return n, "lord-forged"
        if without + 1 >= SPOILS_PITY or rng.random() < SPOILS[rank]:
            return n, "spoils"
        without += 1


def main():
    rng = random.Random(27)
    print(f"Lords killed for one weapon (Lord-forged {LF_CHANCE:.0%}, certain by the {LF_PITY}rd; Spoils pity {SPOILS_PITY}):")
    print(f"{'rank':>4} {'Spoils':>7} {'mean':>6} {'median':>7} {'p90':>5} {'worst':>6}  {'lord-forged share':>18}")
    for rank in ("E", "D", "C", "B", "A", "S"):
        runs = [one(rank, rng) for _ in range(40000)]
        ns = sorted(r[0] for r in runs)
        share = sum(1 for r in runs if r[1] == "lord-forged") / len(runs)
        print(f"{rank:>4} {SPOILS[rank]:>7.0%} {sum(ns) / len(ns):>6.2f} {ns[len(ns) // 2]:>7d} {ns[int(len(ns) * 0.9)]:>5d} {ns[-1]:>6d}  {share:>18.0%}")
    mk = HOMES["marked"]
    print(f"Marked: elite {mk['elite']:.0%}, Warden {mk['warden']:.0%}, camp heart {mk['campHeart']:.0%}; certain by the {mk['pity']}th kill without one; the first Warden or heart is certain.")


if __name__ == "__main__":
    main()
