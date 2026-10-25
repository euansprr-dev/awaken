"""The Living Wilds: XP economy check (planning harness, not game code).

Extends tools/sim/pacing_sim.py (the v2 numbers) with what the Living Wilds plan proposes outside the gates:
zones (creatures at the level of the place, so a player hunting in their zone meets on-level creatures and one hunting
below it is tapered), visible kill XP by the creature's level, crafts (forage, mine, fish, cook, smith) with their own
budgets, Wayfarer quests and Association bounties (one-off claims), camps and nests (a cleared camp pays once per
cycle). Gates keep their floor rewards, trimmed by GATE_TRIM so the balanced player's story still lands near 100 h.

Run:  python3 docs/living-wilds/living_wilds_sim.py
"""
import math, os, random, statistics, sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "tools", "sim"))
import pacing_sim as ps  # noqa: E402

# ---- proposed numbers (PROPOSED: these become JAVA if Euan approves) -------------------------------------------------
GATE_TRIM = 0.76          # gate floor rewards x this (the wilds now carry part of the climb)
BUDGET_V3 = {"kill": 0.10, "ore": 0.06, "build": 0.03, "world": 0.25,
             "forage": 0.012, "mine": 0.012, "fish": 0.012, "cook": 0.007, "smith": 0.007, "camp": 0.10}
KILL_K = 0.03             # share of a level per kill of an on-level hunter-role creature (before role and diff)
ROLE_MIX = ((0.55, 1.0), (0.30, 0.5), (0.10, 0.15), (0.05, 4.0))  # (chance, multiplier): hunter, neutral, ambient, elite
CRAFT_ACTION = 0.008      # share of a level per craft action that pays (a herb, an ore vein, a catch, a meal, a temper)
QUEST_STEP = 0.12         # mean reward of a Wayfarer step or bounty, in levels (a chain's finale pays ~0.8)
CAMP_K = 0.4              # a cleared camp or nest pays CAMP_K x floor 1 of the zone's rank


def diff(delta):
    """Creature level minus player level: above pays up to +40%, below tapers (the v2 taper's curve, a little steeper)."""
    if delta >= 0:
        return 1.0 + 0.08 * min(5, delta)
    return 1.0 / (1.0 + (-delta) / 8.0)


ARCHETYPES = {
    # outside: minutes outside per gate cycle (uniform range); gate_p: chance a cycle ends in a gate.
    # zone_fit: chance the player hunts where creatures are within 2 levels; hunt/gather/quest: share of outside minutes.
    "balanced": {"base": "balanced", "outside": (10, 22), "gate_p": 1.0, "zone_fit": 0.7, "hunt": 0.45, "gather": 0.35, "quest": 1 / 40, "camp": 1 / 45},
    "careful":  {"base": "careful", "outside": (12, 24), "gate_p": 1.0, "zone_fit": 0.6, "hunt": 0.35, "gather": 0.45, "quest": 1 / 43, "camp": 1 / 60},
    "greedy":   {"base": "greedy", "outside": (6, 14), "gate_p": 1.0, "zone_fit": 0.8, "hunt": 0.6, "gather": 0.15, "quest": 1 / 57, "camp": 1 / 35},
    "builder":  {"base": "builder", "outside": (14, 30), "gate_p": 1.0, "zone_fit": 0.5, "hunt": 0.25, "gather": 0.55, "quest": 1 / 50, "camp": 1 / 70},
    # mostly the open world: a gate only when the Director's pull wins (a third of cycles), long stretches outside
    "wanderer": {"base": "balanced", "outside": (35, 70), "gate_p": 0.33, "zone_fit": 0.8, "hunt": 0.5, "gather": 0.35, "quest": 1 / 34, "camp": 1 / 35},
}


class Budget3(ps.Budget):
    def __init__(self):
        self.tally = {k: 0.0 for k in BUDGET_V3}

    def pay(self, kind, raw):
        b, t = BUDGET_V3[kind], self.tally[kind]
        after = t + raw
        self.tally[kind] = after
        if after <= b:
            return raw
        full = max(0.0, b - t)
        return full + b * math.log(after / max(t, b))


def simulate(kind, rng, v3=True):
    a = ARCHETYPES[kind]
    p = ps.PLAYERS[a["base"]]
    st = {"t": 0.0, "level": 1, "xp": 0.0}
    level_times = {1: 0.0}
    budget = Budget3()
    found = [0]
    src = {"gate": 0.0, "hunt": 0.0, "craft": 0.0, "quest": 0.0, "camp": 0.0, "other": 0.0}
    rank_i = 0
    gates = 0

    def gain(amount, where="other"):
        src[where] += amount
        st["xp"] += amount
        while st["xp"] >= ps.xp_to_next(st["level"]):
            st["xp"] -= ps.xp_to_next(st["level"])
            st["level"] += 1
            level_times.setdefault(st["level"], st["t"])

    def levels(frac, where="other"):
        gain(frac * ps.xp_to_next(st["level"]), where)

    def overworld(minutes):
        for _ in range(int(round(minutes))):
            st["t"] += 1
            budget.decay(1)
            lv = st["level"]
            xpn = ps.xp_to_next(lv)
            night = (st["t"] % 20) >= 13
            if v3:
                r = rng.random()
                if r < a["hunt"] + (0.15 if night else 0.0):
                    for _k in range(rng.choice((1, 1, 2, 2, 3))):
                        delta = rng.randint(-2, 2) if rng.random() < a["zone_fit"] else -rng.randint(4, 12)
                        role = 1.0
                        x = rng.random()
                        for ch, m in ROLE_MIX:
                            x -= ch
                            if x <= 0:
                                role = m
                                break
                        gain(budget.pay("kill", KILL_K * role) * diff(delta) * xpn, "hunt")  # like the game: taper after the budget
                elif r < a["hunt"] + a["gather"]:
                    for craft in rng.sample(("forage", "mine", "fish", "cook", "smith"), 2):
                        gain(budget.pay(craft, CRAFT_ACTION * rng.randint(1, 3)) * xpn, "craft")
                gain(budget.pay("build", ps.BLOCK_FRAC * 12 * (1 + 2 * p["build_time"])) * xpn)
                if rng.random() < a["quest"]:
                    st["t"] += rng.uniform(2, 6)
                    levels(QUEST_STEP * rng.uniform(0.5, 1.6), "quest")
                if rng.random() < a["camp"]:
                    st["t"] += rng.uniform(3, 6)
                    gain(budget.pay("camp", CAMP_K * ps.gate_floor_xp(ps.RANKS[rank_i], 1) / xpn) * xpn, "camp")
            else:
                kills = rng.random() < (p["night_kills"] if night else 0.15)
                hp_factor = rng.choice((1.0, 1.0, 1.0, 1.3, 2.0))
                if kills:
                    gain(budget.pay("kill", ps.KILL_FRAC * hp_factor) * ps.taper(lv) * xpn, "hunt")
                if rng.random() < 0.25 * p["ores"]:
                    gain(budget.pay("ore", ps.ORE_FRAC * rng.randint(1, 4)) * ps.taper(lv) * xpn, "craft")
                gain(budget.pay("build", ps.BLOCK_FRAC * 12 * (1 + 2 * p["build_time"])) * ps.taper(lv) * xpn)
            hours = st["t"] / 60
            rate = ps.DISCOVERY_FIRST_HOUR / 60 * math.exp(-hours / 6.0)
            if found[0] < ps.DISCOVERY_TOTAL and rng.random() < rate:
                found[0] += 1
                levels(ps.DISCOVERY_FRAC)
            if rng.random() < p["engage"] / ps.ANOMALY_EVERY_MIN:
                st["t"] += 2
                gain(ps.ANOMALY_K * ps.gate_floor_xp(ps.RANKS[rank_i], 1))

    # the prologue, as in pacing_sim
    st["t"] = 2.0
    bank = (ps.DISCOVERY_FRAC * ps.xp_to_next(1) if rng.random() < 0.5 else 0.0)
    bank += rng.randint(1, 6) * ps.gate_floor_xp("E", 1) * ps.KILL_SHARE / ps.BELL_CRYPT_PLANNED_MOBS
    st["t"] = 6.0
    gain(bank)
    if st["level"] < ps.AWAKEN_FLOOR_LEVEL:
        gain(ps.xp_to_next(1) - st["xp"])
    st["t"] = 8.0
    day_clock = 8.0
    last_t = 8.0
    crypt = False
    trim = GATE_TRIM if v3 else 1.0
    while st["t"] < 260 * 60 and st["level"] < 140:
        rank = ps.RANKS[rank_i]
        if v3:
            home = rng.uniform(*a["outside"]) * (1 + 0.5 * p["build_time"])
        else:
            home = rng.uniform(6, 16) * (1 + p["build_time"])
        if not crypt:
            home = rng.uniform(10, 14) if not v3 else rng.uniform(14, 22)  # v3: the Commons hour before the first gate
        overworld(home)
        # in-game days pass in gates too (pacing_sim counts only outdoor minutes, which flatters a player who stays out)
        day_clock += st["t"] - last_t
        last_t = st["t"]
        while day_clock >= 20:
            day_clock -= 20
            if st["level"] >= 2 and rng.random() < p["daily"]:
                st["t"] += 4
                levels(ps.REGIMEN_LEVELS)
            if rng.random() < ps.DIRECTIVE_PER_DAY[a["base"]]:
                levels(ps.DIRECTIVE_LEVELS)
            if rng.random() < ps.DREAM_PER_DAY:
                levels(ps.DREAM_LEVELS)
        if rank_i + 1 < len(ps.RANKS):
            nxt = ps.RANKS[rank_i + 1]
            if st["level"] >= ps.RANK_LEVEL[nxt]:
                st["t"] += 22
                ratio = st["level"] / ps.RANK_MID[nxt]
                if rng.random() < min(0.95, 0.55 * ratio * p["skill"] + 0.25):
                    rank_i += 1
                continue
        if crypt and rng.random() > a["gate_p"]:
            continue
        # a gate (simplified from pacing_sim: on-rank or one below, push to the archetype's plan, the v2 risk model)
        if not crypt:
            g_rank, floors, cl = "E", 3, 3.0
        else:
            g_rank = rank if rng.random() > 0.15 else ps.RANKS[max(0, rank_i - 1)]
            floors, cl = p["push"], ps.RANK_MID[g_rank]
        if "first_gate" not in st:
            st["first_gate"] = (st["level"], st["t"])
        ratio = st["level"] / cl
        below = g_rank != rank
        unbanked = 0.0
        gates += 1
        cleared = 0
        st["t"] += 3 if not crypt else rng.uniform(3, 8)
        for floor in range(1, floors + 1):
            lo, hi = ps.FLOOR_MIN[g_rank]
            st["t"] += rng.uniform(lo, hi) / (0.85 + 0.15 * p["skill"])
            if rng.random() < ps.death_risk(floor, ratio, p["skill"]):
                unbanked = 0.0
                st["t"] += 4 + ps.DEATH_EXTRA_MIN
                break
            reward = ps.gate_floor_xp(g_rank, floor) * (ps.BELOW_RANK if below else 1.0) * (trim if crypt else 1.0)
            gain(ps.KILL_SHARE * reward, "gate")
            unbanked += (1 - ps.KILL_SHARE) * reward
            cleared = floor
        gain(unbanked, "gate")
        if not crypt:
            crypt = cleared >= 2 or rng.random() < 0.3
    tot = sum(src.values())
    return {"first_gate": st.get("first_gate", (0, 0)), "level_times": level_times, "share": {k: v / tot for k, v in src.items()}, "gates": gates}


def hours(rs, lv):
    hs = [r["level_times"][lv] / 60 for r in rs if lv in r["level_times"]]
    return round(statistics.median(hs), 1) if hs else None


def minutes(rs, lv):
    hs = [r["level_times"][lv] for r in rs if lv in r["level_times"]]
    return round(statistics.median(hs)) if hs else None


def main():
    out = {}
    for v3 in (False, True):
        for kind in ARCHETYPES:
            if not v3 and kind == "wanderer":
                continue
            rng = random.Random(42)
            rs = [simulate(kind, rng, v3) for _ in range(200)]
            share = {k: round(statistics.mean(r["share"][k] for r in rs), 2) for k in rs[0]["share"]}
            row = {"min_to_3": minutes(rs, 3), "min_to_5": minutes(rs, 5),
                   **{f"h_to_{lv}": hours(rs, lv) for lv in (10, 25, 50, 100, 135)}, "xp_share": share}
            out[("v3 " if v3 else "now ") + kind] = row
            print(("v3  " if v3 else "now ") + f"{kind:9s}", row)
    return out


if __name__ == "__main__":
    main()
