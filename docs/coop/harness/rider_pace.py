#!/usr/bin/env python3
"""How far behind does a rider fall? (docs/coop/PLAN.md, section 4.4)

A fighter's hours to each level come from progression/Rank.java's own comment (tools/sim/pacing_sim.py, SPEC section 2):
level 10 at about 3.5 h, 25 at 17 h, 50 at 56 h, the story's end (about level 135) near 100 h. Between those points the
hours are interpolated on a straight line, which is rough but enough to compare pay rules with each other.

A rider earns a share of what a fighter earns, so they reach a level when the fighter's clock times 1/share says so.
This prints, for each pay rule, the hour the rider reaches each rank's level and the rider's level at the hours the
fighter ranks up. Pure arithmetic: no game code is run.
"""

ANCHORS = [(1, 0.0), (10, 3.5), (25, 17.0), (50, 56.0), (135, 100.0)]   # (level, fighter hours)
RANKS = [("D", 10), ("C", 25), ("B", 45), ("A", 70), ("S", 100), ("Sovereign", 135)]


def hours_at(level: float) -> float:
    for (l0, h0), (l1, h1) in zip(ANCHORS, ANCHORS[1:]):
        if level <= l1:
            return h0 + (h1 - h0) * (level - l0) / (l1 - l0)
    return ANCHORS[-1][1]


def level_at(hours: float) -> float:
    for (l0, h0), (l1, h1) in zip(ANCHORS, ANCHORS[1:]):
        if hours <= h1:
            return l0 + (l1 - l0) * (hours - h0) / (h1 - h0)
    return ANCHORS[-1][0]


def main() -> None:
    rules = [("fighter", 1.0), ("rider, half (today)", 0.5), ("rider, five-eighths", 0.625), ("rider, three-quarters", 0.75)]
    print("Hours of play to reach each rank's level")
    print(f"{'':24}" + "".join(f"{name:>11}" for name, _ in RANKS))
    for label, share in rules:
        print(f"{label:24}" + "".join(f"{hours_at(lv) / share:>10.0f}h" for _, lv in RANKS))
    print()
    print("The rider's level at the hour the fighter reaches each rank's level")
    print(f"{'':24}" + "".join(f"{name + ' (' + str(lv) + ')':>15}" for name, lv in RANKS))
    for label, share in rules[1:]:
        print(f"{label:24}" + "".join(f"{level_at(hours_at(lv) * share):>15.0f}" for _, lv in RANKS))


if __name__ == "__main__":
    main()
