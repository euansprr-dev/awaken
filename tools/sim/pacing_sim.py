"""AWAKEN pacing simulator (disposable planning harness, not game code).

What it checks: whether one set of progression numbers can hit the pacing
targets in the plan for different kinds of players, and whether the
"push one more floor or extract" choice inside a gate stays a real decision.

What it does NOT check: whether any of this is fun. Only playtests answer that.

Run:  python3 prototypes/pacing_sim.py            (prints a summary)
      python3 prototypes/pacing_sim.py --json out.json
"""
import json, math, random, statistics, sys

RANKS = ["E", "D", "C", "B", "A", "S", "Sovereign"]
# Level needed to take each rank's Reassessment trial.
RANK_LEVEL = {"D": 10, "C": 25, "B": 45, "A": 70, "S": 100, "Sovereign": 135}
# Level a gate of each rank is tuned around (the middle of its band).
RANK_MID = {"E": 5, "D": 17, "C": 35, "B": 57, "A": 85, "S": 118, "Sovereign": 150}

# Pacing targets (hours of play) the plan commits to. Bands, not points.
TARGETS = {"D": (2.5, 4.5), "C": (8, 13), "B": (18, 28), "A": (32, 48),
           "S": (52, 78), "Sovereign": (85, 120)}

CURVE = {"k": 90, "exp": 1.45, "late": 0.0}
# Share of a level one cleared gate floor pays, by gate rank. Falls with rank
# so later ranks take longer while level-ups keep a steady rhythm.
SHARE = {"E": 0.81, "D": 0.39, "C": 0.25, "B": 0.23, "A": 0.18, "S": 0.12, "Sovereign": 0.12}

def xp_to_next(level):
    # "late" bends the curve upward past level 45 so B..Sovereign take longer
    # without slowing the opening hours.
    bend = 1 + CURVE["late"] * max(0, level - 45) / 45
    return (CURVE["k"] * level ** CURVE["exp"] + 60) * bend

FLOOR_MIN = {"E": (4, 6), "D": (5, 7), "C": (6, 8), "B": (6, 9), "A": (7, 10),
             "S": (7, 11), "Sovereign": (8, 12)}

def gate_floor_xp(rank, floor):
    # Clearing floor f of a gate pays a share of a level at the rank's mid level,
    # growing 40% per floor pushed.
    return SHARE[rank] * xp_to_next(RANK_MID[rank]) * 1.4 ** (floor - 1)

DEATH_EXTRA_MIN = 8  # "Fracture": a short debuff you clear by resting at the Hearth

PLAYERS = {
    # skill: lowers death risk and clear time. push: deepest floor they aim for.
    "careful":  {"skill": 0.85, "push": 2, "daily": 0.5, "build_time": 0.45},
    "balanced": {"skill": 1.00, "push": 3, "daily": 0.65, "build_time": 0.35},
    "greedy":   {"skill": 1.05, "push": 6, "daily": 0.4, "build_time": 0.25},
    "builder":  {"skill": 0.90, "push": 3, "daily": 0.7, "build_time": 0.60},
}

def death_risk(floor, ratio, skill):
    # Per-floor chance of dying. ratio = player level / gate's mid level.
    base = 0.035 * 1.7 ** (floor - 1)
    return min(0.85, base / (max(ratio, 0.3) ** 2.2) / skill)

def simulate(kind, rng):
    p = PLAYERS[kind]
    t = 0.0              # minutes played
    level, xp, rank_i = 1, 0.0, 0
    rank_times, level_times = {}, [0.0]
    deaths = gates = 0
    day_clock = 0.0
    def gain(amount):
        nonlocal level, xp
        xp += amount
        while xp >= xp_to_next(level):
            xp -= xp_to_next(level)
            level += 1
            level_times.append(t)
    while t < 160 * 60 and rank_i < len(RANKS) - 1:
        rank = RANKS[rank_i]
        # 1) Time at home: building, crafting, exploring. Small XP so a build
        #    session never feels like zero progress.
        home = rng.uniform(6, 16) * (1 + p["build_time"])
        t += home; day_clock += home
        gain(0.012 * home * xp_to_next(level))
        # 2) Daily Regimen once per in-game day (20 real minutes), optional.
        if day_clock >= 20:
            day_clock -= 20
            if rng.random() < p["daily"]:
                t += 4
                gain(0.25 * xp_to_next(level))
        # 3) Rank-up trial when eligible.
        nxt = RANKS[rank_i + 1]
        if level >= RANK_LEVEL[nxt]:
            t += 22
            ratio = level / RANK_MID[nxt]
            if rng.random() < min(0.95, 0.55 * ratio * p["skill"] + 0.25):
                rank_i += 1
                rank_times[nxt] = t / 60
            continue
        # 4) A gate run. Mostly at your rank; sometimes one below for a quick run.
        g_rank = rank if rng.random() > 0.15 else RANKS[max(0, rank_i - 1)]
        ratio = level / RANK_MID[g_rank]
        below = g_rank != rank
        banked = unbanked = 0.0
        gates += 1
        for floor in range(1, p["push"] + 1):
            lo, hi = FLOOR_MIN[g_rank]
            t += rng.uniform(lo, hi) / (0.85 + 0.15 * p["skill"])
            if rng.random() < death_risk(floor, ratio, p["skill"]):
                deaths += 1
                unbanked = 0.0      # floor rewards not yet carried out are lost
                t += 4 + DEATH_EXTRA_MIN  # respawn at the Hearth + Fracture recovery
                break
            reward = gate_floor_xp(g_rank, floor) * (0.3 if below else 1.0)
            gain(0.4 * reward)        # kill XP lands as you go
            unbanked += 0.6 * reward  # loot and floor bonus only count if you extract
            # Greedy players keep going; others stop early when it looks bad.
            if kind != "greedy" and death_risk(floor + 1, ratio, p["skill"]) > 0.22:
                break
        gain(unbanked)
    gaps = [b - a for a, b in zip(level_times, level_times[1:])]
    return {"rank_hours": rank_times, "deaths": deaths, "gates": gates,
            "level_gaps": gaps, "level_times": level_times, "final_level": level}

ENTRY_MIN = 3  # walk to the gate, gear check, entry

def push_ev(rank="C", level=35, skill=1.0, max_floor=7, trials=20000, seed=7):
    """Levels per hour if you always plan to stop at floor N (time included)."""
    rng = random.Random(seed)
    out = []
    lo, hi = FLOOR_MIN[rank]
    for stop in range(1, max_floor + 1):
        total = minutes = 0.0
        died = 0
        for _ in range(trials):
            banked = unbanked = 0.0
            minutes += ENTRY_MIN
            for floor in range(1, stop + 1):
                minutes += rng.uniform(lo, hi)
                if rng.random() < death_risk(floor, level / RANK_MID[rank], skill):
                    unbanked = 0.0; died += 1; minutes += 4 + DEATH_EXTRA_MIN; break
                r = gate_floor_xp(rank, floor)
                banked += 0.4 * r; unbanked += 0.6 * r
            total += banked + unbanked
        out.append({"stop_at": stop,
                    "levels_per_hour": round(total / xp_to_next(level) / (minutes / 60), 2),
                    "death_rate": round(died / trials, 3)})
    best = max(o["levels_per_hour"] for o in out)
    for o in out: o["vs_best"] = round(o["levels_per_hour"] / best, 2)
    return out

def main():
    rng = random.Random(42)
    runs = {k: [simulate(k, rng) for _ in range(300)] for k in PLAYERS}
    summary = {}
    for kind, rs in runs.items():
        row = {}
        for r in RANKS[1:]:
            hrs = [x["rank_hours"][r] for x in rs if r in x["rank_hours"]]
            if hrs:
                q = statistics.quantiles(hrs, n=10)
                row[r] = {"median": round(statistics.median(hrs), 1),
                          "p10": round(q[0], 1), "p90": round(q[-1], 1),
                          "reached": len(hrs) / len(rs)}
        # Level-up gap by rank band (minutes), median across players.
        bands = {}
        for r_from, r_to in zip(RANKS, RANKS[1:]):
            gaps = []
            for x in rs:
                start = 0 if r_from == "E" else x["rank_hours"].get(r_from)
                end = x["rank_hours"].get(r_to)
                if start is None or end is None: continue
                ts = [t / 60 for t in x["level_times"]]
                moments = sorted(set(round(v, 4) for v in ts))
                for a, b in zip(moments, moments[1:]):
                    if start <= a < end: gaps.append((b - a) * 60)
            if gaps: bands[r_from] = round(statistics.median(gaps), 1)
        row["level_gap_min_by_band"] = bands
        row["deaths_per_10_gates"] = round(10 * sum(x["deaths"] for x in rs) / max(1, sum(x["gates"] for x in rs)), 2)
        summary[kind] = row
    checks = []
    for r, (lo, hi) in TARGETS.items():
        med = summary["balanced"].get(r, {}).get("median")
        checks.append({"rank": r, "target": [lo, hi], "balanced_median": med,
                       "pass": med is not None and lo <= med <= hi})
    result = {"summary": summary, "target_checks": checks,
              "push_ev_C_on_level": push_ev(),
              "push_ev_C_underlevelled": push_ev(level=28),
              "xp_curve": {L: round(xp_to_next(L)) for L in (1, 5, 10, 25, 45, 70, 100, 135)}}
    if "--json" in sys.argv:
        with open(sys.argv[sys.argv.index("--json") + 1], "w") as f:
            json.dump(result, f, indent=1)
    for kind, row in summary.items():
        print(f"\n{kind}:  deaths/10 gates={row['deaths_per_10_gates']}  level gap (min) by band={row['level_gap_min_by_band']}")
        for r in RANKS[1:]:
            if r in row: print(f"   {r:>9}: median {row[r]['median']:>5}h  (p10 {row[r]['p10']}, p90 {row[r]['p90']}, reached {row[r]['reached']:.0%})")
    print("\nTarget checks (balanced player):")
    for c in checks: print("  ", c)
    print("\nPush EV, C-rank gate, on-level player:")
    for e in result["push_ev_C_on_level"]: print("  ", e)
    print("\nPush EV, C-rank gate, under-levelled player (L28):")
    for e in result["push_ev_C_underlevelled"]: print("  ", e)

if __name__ == "__main__":
    main()
