"""AWAKEN pacing simulator (planning harness, not game code). v2: "The Awakened World".

What it checks: whether one set of progression numbers hits the plan-v2 pacing targets (level 5 in 45-60 min,
level 10 in 3-4 h, level 25 in 15-20 h, level 50 in 55-60 h of normal play) for different kinds of players, and
whether the "push one more floor or extract" choice inside a gate stays a real decision.

What it does NOT check: whether any of this is fun. Only playtests answer that.

The numbers marked JAVA must match the game (progression/Curve.java, Rank.java, XpBudget.java, Regimen.java,
story/Prologue.java). Numbers marked ASSUMED are what other modules are expected to pay (anomalies, discoveries);
docs/modules/onboarding.md lists them so their builders can match them.

Run:  python3 tools/sim/pacing_sim.py                     (prints a summary)
      python3 tools/sim/pacing_sim.py --json tools/sim/pacing_results.json
"""
import json, math, random, statistics, sys

RANKS = ["E", "D", "C", "B", "A", "S", "Sovereign"]
# JAVA Rank.levelRequired: level needed to take each rank's Reassessment trial.
RANK_LEVEL = {"D": 10, "C": 25, "B": 45, "A": 70, "S": 100, "Sovereign": 135}
# JAVA Rank.contentLevel: level a gate of each rank is tuned around.
RANK_MID = {"E": 5, "D": 17, "C": 35, "B": 57, "A": 85, "S": 118, "Sovereign": 150}
# JAVA Rank.share: share of a level (at RANK_MID) one cleared floor 1 pays. v2 slows D..B so level 50 lands at ~57 h.
SHARE = {"E": 0.28, "D": 0.16, "C": 0.08, "B": 0.07, "A": 0.3, "S": 0.25, "Sovereign": 0.25}

# Plan v2 targets: hours of normal play to reach a level (balanced player's median).
LEVEL_TARGETS = {5: (0.75, 1.0), 10: (3.0, 4.0), 25: (15.0, 20.0), 50: (55.0, 60.0)}
# The first-hour slice (docs/v2/BRIEF.md): level at 30 and 60 minutes.
FIRST_HOUR = {30: (3, 4.99), 60: (5, 6.99)}


def xp_to_next(level):  # JAVA Curve.xpToNext (unchanged since v1)
    return 90 * level ** 1.45 + 60


FLOOR_GROWTH, KILL_SHARE, BELOW_RANK = 1.4, 0.4, 0.3  # JAVA Curve


def gate_floor_xp(rank, floor):  # JAVA Curve.floorReward
    return SHARE[rank] * xp_to_next(RANK_MID[rank]) * FLOOR_GROWTH ** (floor - 1)


FLOOR_MIN = {"E": (4, 6), "D": (5, 7), "C": (6, 8), "B": (6, 9), "A": (7, 10),
             "S": (7, 11), "Sovereign": (8, 12)}
DEATH_EXTRA_MIN = 8  # Fracture: a short debuff you clear by resting

# ---- world XP with diminishing returns (JAVA XpBudget) ------------------------------------------------------------
# Every repeatable world source has a budget per ~10 minutes (a tally that decays with a 10-minute time constant).
# Below the budget it pays in full; above it each extra bit pays budget/tally, so grinding one thing levels off at about
# one budget per 10 minutes while varied play is never cut.
TAU_MIN = 10.0
BUDGET = {"kill": 0.15, "ore": 0.06, "build": 0.03, "world": 0.25}
KILL_FRAC = 0.04      # JAVA: share of a level per overworld kill of a 20-HP mob (x min(2, hp/20))
ORE_FRAC = 0.01       # JAVA: per ore
BLOCK_FRAC = 0.0005   # JAVA: per block placed
OUTLEVEL = 10.0       # JAVA: world kills taper 1/(1 + levels above the mob's content level / 10)


class Budget:
    def __init__(self):
        self.tally = {k: 0.0 for k in BUDGET}

    def decay(self, minutes):
        f = math.exp(-minutes / TAU_MIN)
        for k in self.tally:
            self.tally[k] *= f

    def pay(self, kind, raw):
        """Diminishing returns: full up to the budget, then budget * ln(after/before) (the integral of B/t)."""
        b, t = BUDGET[kind], self.tally[kind]
        after = t + raw
        self.tally[kind] = after
        if after <= b:
            return raw
        full = max(0.0, b - t)
        return full + b * math.log(after / max(t, b))


def taper(level, content_level=0):  # JAVA XpBudget.taper (applied to what the budget pays)
    return 1.0 / (1.0 + max(0, level - 1 - content_level) / OUTLEVEL)


# ---- the prologue (JAVA story/Prologue + Progression bank) --------------------------------------------------------
BELL_CRYPT_PLANNED_MOBS = 8   # floor 1 of the scripted Bell Crypt
AWAKEN_FLOOR_LEVEL = 2        # JAVA Prologue: Accept tops you up to level 2 if the bank fell short

# ---- other sources -------------------------------------------------------------------------------------------------
REGIMEN_LEVELS = 0.1          # JAVA Regimen: a tenth of a level and +1 point for all three drills
DIRECTIVE_LEVELS = 0.18       # mean reward of an E..C directive (data/awaken/awaken/directive), in levels
DIRECTIVE_PER_DAY = {"careful": 0.35, "balanced": 0.4, "greedy": 0.3, "builder": 0.45}
# ASSUMED (world/anomaly builders): first sightings pay DISCOVERY_FRAC of a level through Progression.discover.
DISCOVERY_FRAC = 0.12
DISCOVERY_TOTAL = 140         # creatures, biomes, landmarks, anomaly kinds, strata... over the whole game
DISCOVERY_FIRST_HOUR = 10     # the slice's target: >= 6 creature types in 30 minutes, plus biomes and an anomaly
# ASSUMED (anomaly builder): an anomaly every ~9 overworld minutes; a rift tear pays ANOMALY_K x floor 1 of your rank.
ANOMALY_EVERY_MIN = 9.0
ANOMALY_K = 0.3
# Existing lucidium code (Dreams.trainingXp, nightmares, mentors): roughly a tenth of a level on a night you sleep and dream.
DREAM_PER_DAY = 0.35
DREAM_LEVELS = 0.1

PLAYERS = {
    # skill: lowers death risk and clear time. push: deepest floor they aim for. daily: chance to finish the Regimen.
    # build_time: share of extra overworld time. engage: chance to take on an anomaly. night_kills: kills per night minute.
    "careful":  {"skill": 0.85, "push": 2, "daily": 0.5, "build_time": 0.45, "engage": 0.6, "night_kills": 0.6, "ores": 0.6},
    "balanced": {"skill": 1.00, "push": 3, "daily": 0.65, "build_time": 0.35, "engage": 0.7, "night_kills": 0.8, "ores": 0.8},
    "greedy":   {"skill": 1.05, "push": 6, "daily": 0.4, "build_time": 0.25, "engage": 0.9, "night_kills": 1.2, "ores": 0.6},
    "builder":  {"skill": 0.90, "push": 3, "daily": 0.7, "build_time": 0.60, "engage": 0.4, "night_kills": 0.4, "ores": 1.2},
}


def death_risk(floor, ratio, skill):
    # Per-floor chance of dying. ratio = player level / gate's content level.
    base = 0.035 * 1.7 ** (floor - 1)
    return min(0.85, base / (max(ratio, 0.3) ** 2.2) / skill)


def simulate(kind, rng):
    p = PLAYERS[kind]
    st = {"t": 0.0, "level": 1, "xp": 0.0}
    level_times = {1: 0.0}
    budget = Budget()
    found = [0]
    rank_i = 0
    rank_times = {}
    deaths = gates = 0

    def gain(amount):
        st["xp"] += amount
        while st["xp"] >= xp_to_next(st["level"]):
            st["xp"] -= xp_to_next(st["level"])
            st["level"] += 1
            level_times.setdefault(st["level"], st["t"])

    def levels(frac):  # a fraction of the current level
        gain(frac * xp_to_next(st["level"]))

    def overworld(minutes, awake=True):
        """Minute by minute outside: kills at night, ores, building, first sightings, anomalies."""
        for _ in range(int(round(minutes))):
            st["t"] += 1
            budget.decay(1)
            lv = st["level"]
            night = (st["t"] % 20) >= 13           # 7 of every 20 minutes are night
            kills = rng.random() < (p["night_kills"] if night else 0.15)
            hp_factor = rng.choice((1.0, 1.0, 1.0, 1.3, 2.0))
            if kills:
                gain(budget.pay("kill", KILL_FRAC * hp_factor) * taper(lv) * xp_to_next(lv))
            if rng.random() < 0.25 * p["ores"]:
                gain(budget.pay("ore", ORE_FRAC * rng.randint(1, 4)) * taper(lv) * xp_to_next(lv))
            gain(budget.pay("build", BLOCK_FRAC * 12 * (1 + 2 * p["build_time"])) * taper(lv) * xp_to_next(lv))
            # first sightings: a fast start, then rarer as the world gets known
            hours = st["t"] / 60
            rate = DISCOVERY_FIRST_HOUR / 60 * math.exp(-hours / 6.0)
            if found[0] < DISCOVERY_TOTAL and rng.random() < rate:
                found[0] += 1
                levels(DISCOVERY_FRAC)
            if awake and rng.random() < p["engage"] / ANOMALY_EVERY_MIN:
                st["t"] += 2  # a rift tear takes a couple of minutes
                gain(ANOMALY_K * gate_floor_xp(RANKS[rank_i], 1))

    # 0-2 min: vanilla (a sighting may already bank), 2-6 min: the Bell Crypt prologue, banked.
    bank = 0.0
    st["t"] = 2.0
    if rng.random() < 0.5:
        bank += DISCOVERY_FRAC * xp_to_next(1)
    floor1 = gate_floor_xp("E", 1)
    bank += rng.randint(1, 6) * floor1 * KILL_SHARE / BELL_CRYPT_PLANNED_MOBS
    st["t"] = 6.0
    gain(bank)                                    # Accept: the bank is paid
    if st["level"] < AWAKEN_FLOOR_LEVEL:          # and tops you up to level 2
        gain(xp_to_next(1) - st["xp"])
    st["t"] = 8.0                                 # choose the first power
    first_power_min = st["t"]

    day_clock = 8.0
    bell_crypt_done = False
    while st["t"] < 200 * 60 and st["level"] < 140:
        rank = RANKS[rank_i]
        # 1) Time outside: building, crafting, exploring, the odd anomaly.
        home = rng.uniform(6, 16) * (1 + p["build_time"])
        if not bell_crypt_done:
            home = rng.uniform(10, 14)  # the first hour: try the power, meet creatures, a rift tear, then go back in
        before = st["t"]
        overworld(home)
        day_clock += st["t"] - before
        # 2) Each in-game day (20 real minutes): the Regimen (optional) and maybe a Directive kept.
        while day_clock >= 20:
            day_clock -= 20
            if st["level"] >= 2 and rng.random() < p["daily"]:
                st["t"] += 4
                levels(REGIMEN_LEVELS)
            if rng.random() < DIRECTIVE_PER_DAY[kind]:
                levels(DIRECTIVE_LEVELS)
            if rng.random() < DREAM_PER_DAY:
                levels(DREAM_LEVELS)
        # 3) Rank-up trial when eligible.
        if rank_i + 1 < len(RANKS):
            nxt = RANKS[rank_i + 1]
            if st["level"] >= RANK_LEVEL[nxt]:
                st["t"] += 22
                ratio = st["level"] / RANK_MID[nxt]
                if rng.random() < min(0.95, 0.55 * ratio * p["skill"] + 0.25):
                    rank_i += 1
                    rank_times[nxt] = st["t"] / 60
                continue
        # 4) A gate run. The first is the Bell Crypt (E, three floors, content level 0, never breaks).
        if not bell_crypt_done:
            g_rank, floors, cl = "E", 3, 3.0
        else:
            g_rank = rank if rng.random() > 0.15 else RANKS[max(0, rank_i - 1)]
            floors, cl = p["push"], RANK_MID[g_rank]
        ratio = st["level"] / cl
        below = g_rank != rank
        unbanked = 0.0
        gates += 1
        cleared = 0
        st["t"] += 3 if not bell_crypt_done else rng.uniform(3, 8)  # walk to it (the Director keeps gates 300-1500 m out)
        for floor in range(1, floors + 1):
            lo, hi = FLOOR_MIN[g_rank]
            st["t"] += rng.uniform(lo, hi) / (0.85 + 0.15 * p["skill"])
            if rng.random() < death_risk(floor, ratio, p["skill"]):
                deaths += 1
                unbanked = 0.0
                st["t"] += 4 + DEATH_EXTRA_MIN
                break
            reward = gate_floor_xp(g_rank, floor) * (BELOW_RANK if below else 1.0)
            gain(KILL_SHARE * reward)
            unbanked += (1 - KILL_SHARE) * reward
            cleared = floor
            if bell_crypt_done and kind != "greedy" and death_risk(floor + 1, ratio, p["skill"]) > 0.22:
                break
        gain(unbanked)
        if not bell_crypt_done and cleared >= 2:
            bell_crypt_done = cleared == 3 or rng.random() < 0.5
        elif not bell_crypt_done:
            bell_crypt_done = rng.random() < 0.3  # gives up on it for now and plays the open world

    def level_at(minutes):
        best = 1
        for lv, t in level_times.items():
            if t <= minutes:
                best = max(best, lv)
        return best

    return {"rank_hours": rank_times, "deaths": deaths, "gates": gates, "level_times": level_times,
            "level_at": {m: level_at(m) for m in (10, 30, 60, 120)}, "first_power_min": first_power_min,
            "final_level": st["level"], "discoveries": found[0]}


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
                banked += KILL_SHARE * r; unbanked += (1 - KILL_SHARE) * r
            total += banked + unbanked
        out.append({"stop_at": stop,
                    "levels_per_hour": round(total / xp_to_next(level) / (minutes / 60), 2),
                    "death_rate": round(died / trials, 3)})
    best = max(o["levels_per_hour"] for o in out)
    for o in out: o["vs_best"] = round(o["levels_per_hour"] / best, 2)
    return out


def farm_check():
    """A mob farm (60 kills every 10 minutes, nothing else) at a few levels: levels per hour from kills alone."""
    out = {}
    for lv in (3, 10, 25, 50):
        b = Budget()
        paid = 0.0
        for minute in range(600):
            b.decay(1)
            for _ in range(6):
                paid += b.pay("kill", KILL_FRAC) * taper(lv)
        out[lv] = round(paid / 10, 2)  # levels per hour over 10 hours
    return out


def quantiles(xs):
    xs = sorted(xs)
    q = statistics.quantiles(xs, n=10) if len(xs) >= 2 else [xs[0]] * 9
    return {"median": round(statistics.median(xs), 2), "p10": round(q[0], 2), "p90": round(q[-1], 2)}


def main():
    rng = random.Random(42)
    runs = {k: [simulate(k, rng) for _ in range(300)] for k in PLAYERS}
    summary = {}
    for kind, rs in runs.items():
        row = {"level_hours": {}, "rank_hours": {}, "level_at_minutes": {}}
        for lv in (3, 5, 10, 15, 20, 25, 30, 40, 45, 50, 70, 100, 135):
            hrs = [x["level_times"][lv] / 60 for x in rs if lv in x["level_times"]]
            if hrs:
                row["level_hours"][lv] = quantiles(hrs) | {"reached": round(len(hrs) / len(rs), 2)}
        for r in RANKS[1:]:
            hrs = [x["rank_hours"][r] for x in rs if r in x["rank_hours"]]
            if hrs:
                row["rank_hours"][r] = quantiles(hrs) | {"reached": round(len(hrs) / len(rs), 2)}
        for m in (10, 30, 60, 120):
            row["level_at_minutes"][m] = quantiles([x["level_at"][m] for x in rs])
        row["deaths_per_10_gates"] = round(10 * sum(x["deaths"] for x in rs) / max(1, sum(x["gates"] for x in rs)), 2)
        row["first_power_minute"] = statistics.median(x["first_power_min"] for x in rs)
        row["discoveries_median"] = statistics.median(x["discoveries"] for x in rs)
        summary[kind] = row
    checks = []
    bal = summary["balanced"]
    for lv, (lo, hi) in LEVEL_TARGETS.items():
        med = bal["level_hours"].get(lv, {}).get("median")
        checks.append({"level": lv, "target_hours": [lo, hi], "balanced_median": med,
                       "pass": med is not None and lo <= med <= hi})
    for m, (lo, hi) in FIRST_HOUR.items():
        med = bal["level_at_minutes"][m]["median"]
        checks.append({"minute": m, "target_level": [lo, int(hi)], "balanced_median": med, "pass": lo <= med <= hi})
    result = {"summary": summary, "target_checks": checks,
              "push_ev_C_on_level": push_ev(),
              "push_ev_C_underlevelled": push_ev(level=28),
              "mob_farm_levels_per_hour": farm_check(),
              "xp_curve": {L: round(xp_to_next(L)) for L in (1, 5, 10, 25, 45, 70, 100, 135)},
              "share": SHARE}
    if "--json" in sys.argv:
        with open(sys.argv[sys.argv.index("--json") + 1], "w") as f:
            json.dump(result, f, indent=1)
    for kind, row in summary.items():
        print(f"\n{kind}:  deaths/10 gates={row['deaths_per_10_gates']}  first power at {row['first_power_minute']} min"
              f"  discoveries {row['discoveries_median']}")
        print("   level at minute:", {m: v["median"] for m, v in row["level_at_minutes"].items()})
        print("   hours to level: ", {lv: v["median"] for lv, v in row["level_hours"].items()})
        print("   hours to rank:  ", {r: v["median"] for r, v in row["rank_hours"].items()})
    print("\nTarget checks (balanced player):")
    for c in checks: print("  ", c)
    print("\nMob farm, kills only (levels per hour):", result["mob_farm_levels_per_hour"])
    print("\nPush EV, C-rank gate, on-level player:")
    for e in result["push_ev_C_on_level"]: print("  ", e)


if __name__ == "__main__":
    main()
