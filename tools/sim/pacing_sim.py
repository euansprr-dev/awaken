"""AWAKEN pacing simulator (planning harness, not game code). v3: "The Living Wilds" (SPEC section 21.3).

What it checks: whether one set of progression numbers hits the plan-v2 pacing targets (level 5 in 45-60 min,
level 10 in 3-4 h, level 25 in 15-20 h, level 50 in 55-60 h of normal play) for different kinds of players, and
whether the "push one more floor or extract" choice inside a gate stays a real decision.
v3 swaps the overworld for the Living Wilds model (creatures by level and role, crafts, Wayfarer quests, camps) and
trims gate floor rewards by WILDS_TRIM so the balanced player's length still lands on the plan's targets.

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
# JAVA Rank.share: share of a level (at RANK_MID) one cleared floor 1 pays. v2 slows D and C so level 50 lands at ~56 h;
# B pays well again (and A and above more) so the climb from 50 to 135 takes ~45 h and the story ends near 100 h.
SHARE = {"E": 0.28, "D": 0.16, "C": 0.065, "B": 0.22, "A": 0.6, "S": 0.55, "Sovereign": 0.5}

# Plan v2 targets: hours of normal play to reach a level (balanced player's median).
# Level 50 moved from 55-60 to 50-60 with the Living Wilds (SPEC §21.3): the open world now pays through the D-C
# stretch, so the balanced player reaches 50 about 5 h sooner while level 135 (the story's length) holds.
LEVEL_TARGETS = {5: (0.75, 1.0), 10: (3.0, 4.0), 25: (15.0, 20.0), 50: (50.0, 60.0), 135: (95.0, 105.0)}
# The first-hour slice (docs/v2/BRIEF.md): level at 30 and 60 minutes.
FIRST_HOUR = {30: (3, 4.99), 60: (5, 6.99)}


def xp_to_next(level):  # JAVA Curve.xpToNext (unchanged since v1)
    return 90 * level ** 1.45 + 60


FLOOR_GROWTH, KILL_SHARE, BELOW_RANK = 1.4, 0.4, 0.3  # JAVA Curve


# JAVA Curve.WILDS_TRIM: gate floor rewards (and everything priced from floor 1 of a rank: anomalies, camps) x this, because the
# wilds now carry part of the climb. The only number retuned for v3. 0.80 is the middle of the window (0.78-0.82) that keeps
# level 5, 10, 25 and 135 and the first hour on target; level 50 cannot be reached by the trim alone (the wilds carry the 25-50 climb).
WILDS_TRIM = 0.80
# JAVA LivingModule.WILDS_RANK: the open world pays by rank the way gates do (C is the long climb), applied after the
# budget to hunting, crafts and camps (quests and discoveries are authored one-offs and pay in full).
WILDS_RANK = {"E": 1.0, "D": 1.0, "C": 0.55, "B": 1.0, "A": 1.0, "S": 1.0, "Sovereign": 1.0}


def gate_floor_xp(rank, floor):  # JAVA Curve.floorReward
    return WILDS_TRIM * SHARE[rank] * xp_to_next(RANK_MID[rank]) * FLOOR_GROWTH ** (floor - 1)


FLOOR_MIN = {"E": (4, 6), "D": (5, 7), "C": (6, 8), "B": (6, 9), "A": (7, 10),
             "S": (7, 11), "Sovereign": (8, 12)}
DEATH_EXTRA_MIN = 8  # Fracture: a short debuff you clear by resting

# ---- world XP with diminishing returns (JAVA XpBudget) ------------------------------------------------------------
# Every repeatable world source has a budget per ~10 minutes (a tally that decays with a 10-minute time constant).
# Below the budget it pays in full; above it each extra bit pays budget/tally, so grinding one thing levels off at about
# one budget per 10 minutes while varied play is never cut.
TAU_MIN = 10.0
# v3: "kill" became "hunt" (0.10) and the crafts and camps got budgets of their own (SPEC 21.3, XpBudget.register).
BUDGET = {"hunt": 0.10, "ore": 0.06, "build": 0.03, "world": 0.25,                 # JAVA
          "forage": 0.012, "mine": 0.012, "fish": 0.012, "cook": 0.007, "smith": 0.007, "camp": 0.10}  # JAVA
KILL_K = 0.03         # JAVA: share of a level per kill of an on-level hunter-role creature (before role and diff)
ROLE_MIX = ((0.55, 1.0), (0.30, 0.5), (0.10, 0.15), (0.05, 4.0))  # JAVA roles: (share of kills, multiplier): hunter, neutral, ambient, elite
CRAFT_ACTION = 0.008  # JAVA: share of a level per craft action that pays (a herb, an ore vein, a catch, a meal, a temper)
QUEST_STEP = 0.12     # JAVA: mean reward of a Wayfarer step or bounty, in levels (a chain's finale pays ~0.8)
CAMP_K = 0.4          # JAVA: a cleared camp or nest pays CAMP_K x floor 1 of the zone's rank (through the camp budget)
ORE_FRAC = 0.01       # JAVA: per ore

# ---- the deep's metal (SPEC §28.3, "Between the Gates" W2): gates no longer pay raw ore, so D and C gear wait on mining.
# A separate random stream (MINE_SEED), so the XP pacing above is untouched. Minutes of mining are the minutes the sim
# already spends on ore (the "ores" roll) or on the Mining craft in a gathering minute. ASSUMED rates, tuned to vanilla:
# a shallow mining minute (caves and hillsides) brings 1-3 raw iron and sometimes gold; a deep minute (below y 0, once you
# have an iron pick and are level 8+) a diamond 7% of the time. Lodes (PLAN 7.3): a mining minute finds one 1.2% of the
# time (0.8% deep), three times that once the Tuning Fork is in hand (the plan's 2-5 h: from hour 3); a lode's blocks left
# after caves cut it are 55% of its size, plus its heart's lump.
MINE_SEED = [0]
MINE_IRON = (1, 3)              # ASSUMED: raw iron per shallow mining minute
MINE_GOLD_P = 0.3               # ASSUMED: chance of 1-2 raw gold per mining minute
MINE_DIAMOND_P = 0.07           # ASSUMED: chance of a diamond per deep mining minute (1-2)
MINE_DEEP_SHARE = 0.4           # ASSUMED: share of mining minutes spent below y 0 once you can
MINE_DEEP_LEVEL = 8             # ASSUMED: from this level (and an iron pick) a player goes deep
LODE_P, LODE_P_DEEP, FORK_X, FORK_HOUR = 0.012, 0.008, 3.0, 3.0
LODE_LEFT = 0.55
IRON_LODE = (40, 100)           # JAVA delve.Ore: IRON 40-100 blocks, heart 4-7 raw iron
DIAMOND_LODE = (20, 40)         # JAVA delve.Ore: DIAMOND 20-40 blocks, heart 2-4 diamonds
# what a tier's kit needs (JAVA tools/art/specs/gear.py RECIPES: 13 of the metal over seven pieces, a forged weapon one
# more; the focus's gold): D 14 iron + 1 gold, C 14 diamonds + 2 gold
KIT_NEEDS = {"D": {"iron": 14, "gold": 1}, "C": {"diamond": 14, "gold": 2}}
KIT_LEVEL = {"D": 13, "C": 22}  # JAVA level road: hunter_gear_d 13, hunter_gear_c 22
# before W2, for comparison: what a gate paid in raw metal (gates/run/Loot.java's old table and the market's diamonds)
OLD_GATE_IRON, OLD_GATE_GOLD, OLD_GATE_DIAMOND_C = 3.0, 2.0, 0.6
BLOCK_FRAC = 0.0005   # JAVA: per block placed
OUTLEVEL = 10.0       # JAVA: ore and building taper 1/(1 + levels above content level 0 / 10)


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


def taper(level, content_level=0):  # JAVA XpBudget.taper (applied to what the budget pays; ores and building only in v3)
    return 1.0 / (1.0 + max(0, level - 1 - content_level) / OUTLEVEL)


def diff(delta):
    """JAVA: creature level minus player level. Above pays up to +40%, below tapers (applied after the budget)."""
    if delta >= 0:
        return 1.0 + 0.08 * min(5, delta)   # JAVA
    return 1.0 / (1.0 + (-delta) / 8.0)     # JAVA


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
    # build_time: share of extra overworld time. engage: chance to take on an anomaly. ores: ore pace.
    # v3 (the Living Wilds): outside: minutes outside per gate cycle (uniform range, x (1 + 0.5 build_time)).
    # zone_fit: chance the player hunts where creatures are within 2 levels of them (else far below). hunt, gather: share of
    # outside minutes spent hunting / crafting (hunting adds 0.15 at night). quest, camp: per-minute chance of a Wayfarer step / a camp.
    "careful":  {"skill": 0.85, "push": 2, "daily": 0.5, "build_time": 0.45, "engage": 0.6, "ores": 0.6,
                 "outside": (12, 24), "zone_fit": 0.6, "hunt": 0.35, "gather": 0.45, "quest": 1 / 43, "camp": 1 / 60},
    "balanced": {"skill": 1.00, "push": 3, "daily": 0.65, "build_time": 0.35, "engage": 0.7, "ores": 0.8,
                 "outside": (10, 22), "zone_fit": 0.7, "hunt": 0.45, "gather": 0.35, "quest": 1 / 40, "camp": 1 / 45},
    "greedy":   {"skill": 1.05, "push": 6, "daily": 0.4, "build_time": 0.25, "engage": 0.9, "ores": 0.6,
                 "outside": (6, 14), "zone_fit": 0.8, "hunt": 0.6, "gather": 0.15, "quest": 1 / 57, "camp": 1 / 35},
    "builder":  {"skill": 0.90, "push": 3, "daily": 0.7, "build_time": 0.60, "engage": 0.4, "ores": 1.2,
                 "outside": (14, 30), "zone_fit": 0.5, "hunt": 0.25, "gather": 0.55, "quest": 1 / 50, "camp": 1 / 70},
}


# ---- power mastery (JAVA powers/Mastery) --------------------------------------------------
# A power gains mastery points (MP) from real use: the energy of a cast that lands a hit on a living target pays 1 MP
# per energy point, energy spent in a fight that hit nothing pays FIGHT_MP, kills pay KILL_MP (elites and wardens
# ELITE_MP, bosses BOSS_MP). Energy spent at the air, out of a fight, pays nothing. A full bar levels the power up
# exactly like a duplicate Power Core (cores stay a shortcut). The sim tracks the player's MAIN power only, from the
# moment the first power is chosen, with no duplicate cores (a core is a random roll, rarely your main).
MASTERY_NEED = {1: 7000, 2: 13000, 3: 32000, 4: 50000}  # JAVA Mastery.need(level): MP from level to level + 1
FIGHT_MP = 0.5                                          # JAVA Mastery.FIGHT_SHARE
KILL_MP, ELITE_MP, BOSS_MP = 6, 18, 60                  # JAVA Mastery.KILL / ELITE / BOSS
# ASSUMED play style (not game code): how a main power is used in a fight.
MAIN_SHARE = 0.45             # share of the energy you spend that goes into your main power
UTILISATION = 0.7             # share of energy regen you actually spend while fighting
LANDED = 0.85                 # share of the main power's energy whose cast lands a hit (the rest is spent in the fight)
KILLS_PER_COMBAT_MIN = 1.5    # kills per minute of fighting (gates, nights, rift tears)
INT_PER_LEVEL = 0.5           # 3 points a level over six stats
# combat minutes per event: share of a gate floor's minutes, an outdoor minute spent hunting, a rift tear, a rank trial
COMBAT_MIN = {"floor": 0.55, "hunt_minute": 0.5, "rift": 1.5, "trial": 12.0}
ELITES_PER_FLOOR = 0.4


def regen_per_s(level):  # JAVA PowerMath.regenPerSecond at a balanced INT
    return 4.0 + 0.08 * (5 + INT_PER_LEVEL * (level - 1))


def mastery_per_combat_minute(level):
    energy = 60 * regen_per_s(level) * UTILISATION * MAIN_SHARE
    return energy * (LANDED + (1 - LANDED) * FIGHT_MP) + KILLS_PER_COMBAT_MIN * MAIN_SHARE * KILL_MP


def death_risk(floor, ratio, skill):
    # Per-floor chance of dying. ratio = player level / gate's content level. (The prologue's Bell Crypt; real gates
    # use gate_floor_risk below, built on what the gates actually contain.)
    base = 0.035 * 1.7 ** (floor - 1)
    return min(0.85, base / (max(ratio, 0.3) ** 2.2) / skill)


# ---- the gates themselves (P3: D-B tuning; docs/modules/gates.md "Tuning") ----------------------------------------
# JAVA, measured: the GameTest gates_tuning_export rolls 240 gates per rank through the real Director and FloorBuilder
# and writes run/gate_tuning.json (copied to tools/sim/gate_tuning.json): floors per gate, modifier and twist
# frequencies, and per floor the monsters on the critical path and in the side rooms (an elite counts 3).
import os as _os

_HERE = _os.path.dirname(_os.path.abspath(__file__))
COMP_FILE = _os.path.join(_HERE, "gate_tuning.json")
COMP = json.load(open(COMP_FILE)) if _os.path.exists(COMP_FILE) else None
CONTENT = _os.environ.get("AWAKEN_SIM_CONTENT", "v2")  # which interiors: "v1" = before the P3 tuning, "v2" = now

# ASSUMED (a combat model, not game code): what makes a floor dangerous.
FLOOR1_RISK = 0.035       # an on-level balanced player dying on the reference floor (v1, C rank, floor 1)
THREAT_EXP = 0.7          # danger grows slower than the monster count: you fight them a few at a time
ATTRITION = 1.3           # each floor deeper: health, food, durability and cooldowns you carry in
LORD_RISK = 0.06          # an on-level balanced player dying to a Lord on a gate's first floor depth (x ATTRITION^depth)
LORD_XP = 1.0             # the Lord's floor pays a floor reward like any other (the hoard is on top: cores, hearts)
SIDE_SHARE = {"careful": 0.3, "balanced": 0.45, "greedy": 0.75, "builder": 0.4}  # side rooms a player clears
STOP_RISK = {"careful": 0.14, "balanced": 0.2, "greedy": 0.45, "builder": 0.18}   # won't enter a floor riskier than this
LORD_TEMPT = {"careful": 0.10, "balanced": 0.16, "greedy": 0.4, "builder": 0.12}  # a Lord one floor past the plan, if this safe
# modifiers whose danger the monster counts don't already carry (Swarm, Fortune, Elite Pack are in the composition)
MOD_DANGER = {"GLASS": 1.45, "NO_REGEN": 1.2, "MIRROR": 1.2, "COLLAPSE_TIMER": 1.15, "SILENCE": 1.1, "VOLATILE": 1.1,
              "ECHOING": 1.15, "HAUNTED": 1.1, "LIGHTLESS": 1.12, "FLOODING": 1.05, "LOW_GRAVITY": 1.0, "SWARM": 1.05,
              "FORTUNE": 1.0, "ELITE_PACK": 1.05}
TWIST_DANGER = {"NONE": 1.0, "RED_GATE": 1.2, "HOLLOW_GATE": 0.5, "FRIENDLY": 0.0, "TREASURE_GATE": 1.0, "INVERTED": 0.85,
                "ECHO_GATE": 1.0, "DOUBLE_GATE": 1.0, "GATE_WITHIN": 1.0}


GATE_DEATHS = (1.0, 3.0)  # P3 target: balanced player's deaths per 10 gates at D, C and B
PUSH_PEAK = (3, 5)        # P3 target: the floor you should plan to stop after, by levels per hour


def _ref_units():
    c = COMP["ranks"]["C"]["v1"]
    return c["path"][0] + SIDE_SHARE["balanced"] * c["side"][0]


def floor_units(rank, floor, side_share, content=None):
    c = COMP["ranks"][rank][content or CONTENT]
    i = min(floor, len(c["path"])) - 1
    while c["path"][i] is None:
        i -= 1
    return c["path"][i] + side_share * c["side"][i]


def gate_floor_risk(rank, floor, nfloors, ratio, skill, side_share=0.45, danger=1.0, content=None):
    """Chance of dying on one floor of a real gate (the last floor is the Lord's)."""
    if danger <= 0:
        return 0.0
    if floor >= nfloors:
        base = LORD_RISK * ATTRITION ** (floor - 1)
    else:
        base = FLOOR1_RISK * ATTRITION ** (floor - 1) * (floor_units(rank, floor, side_share, content) / _ref_units()) ** THREAT_EXP
    return min(0.85, base * danger / (max(ratio, 0.3) ** 2.2) / skill)


LORD_MIN = 4.0            # ASSUMED: minutes a Lord fight adds to its floor
TIME_EXP = 0.6            # ASSUMED: a floor with more monsters takes longer, a little less than in proportion


def floor_minutes_factor(rank, floor, nfloors, side_share=0.45):
    """How much longer a floor takes than the gate's first (more monsters deeper); the Lord's floor adds the fight."""
    if floor >= nfloors:
        return 1.0 + LORD_MIN / sum(FLOOR_MIN[rank]) * 2
    return (floor_units(rank, floor, side_share) / floor_units(rank, 1, side_share)) ** TIME_EXP


def roll_gate(rank, rng):
    """Floors, modifier danger and twist of one gate, drawn from what the Director rolled (COMP)."""
    c = COMP["ranks"][rank]
    total = sum(c["floors"].values())
    x = rng.random() * total
    nfloors = 3
    for k, v in sorted(c["floors"].items(), key=lambda kv: int(kv[0])):
        x -= v
        if x <= 0:
            nfloors = int(k)
            break
    danger = 1.0
    for m, cnt in c["mods"].items():
        if rng.random() < cnt / COMP["samples"]:
            danger *= MOD_DANGER.get(m, 1.0)
    tw = "NONE"
    x = rng.random() * COMP["samples"]
    for t, cnt in c["twists"].items():
        x -= cnt
        if x <= 0:
            tw = t
            break
    return nfloors, danger * TWIST_DANGER.get(tw, 1.0), tw


def simulate(kind, rng):
    p = PLAYERS[kind]
    st = {"t": 0.0, "level": 1, "xp": 0.0}
    level_times = {1: 0.0}
    budget = Budget()
    found = [0]
    rank_i = 0
    rank_times = {}
    deaths = gates = 0
    by_rank = {}  # gate rank -> [gates, deaths, floors cleared, Lords killed]
    mastery = {"on": False, "mp": 0.0, "level": 1, "at": {1: (1, 0.0)}, "combat_min": 0.0}
    MINE_SEED[0] += 1
    mrng = random.Random(9000 + MINE_SEED[0])
    metal = {"iron": 0.0, "gold": 0.0, "diamond": 0.0}
    old = {"iron": 0.0, "gold": 0.0, "diamond": 0.0}
    kit_at, old_kit_at = {}, {}
    pick = [False]

    def kit_check():
        for tier, need in KIT_NEEDS.items():
            if tier not in kit_at and all(metal[k] >= v for k, v in need.items()):
                kit_at[tier] = st["t"] / 60
            if tier not in old_kit_at and all(metal[k] + old[k] >= v for k, v in need.items()):
                old_kit_at[tier] = st["t"] / 60

    def mine_minute():
        """One minute of mining: ore by depth, and maybe a lode (more often with the Tuning Fork)."""
        hours = st["t"] / 60
        fork = FORK_X if hours >= FORK_HOUR else 1.0
        if not pick[0] and metal["iron"] >= 3:
            pick[0] = True
            metal["iron"] -= 3                      # the iron pick
        deep = pick[0] and st["level"] >= MINE_DEEP_LEVEL and mrng.random() < MINE_DEEP_SHARE
        if deep:
            metal["iron"] += 1
            if mrng.random() < MINE_DIAMOND_P:
                metal["diamond"] += mrng.randint(1, 2)
            if mrng.random() < LODE_P_DEEP * fork and mrng.random() < 0.35:
                metal["diamond"] += round(mrng.randint(*DIAMOND_LODE) * LODE_LEFT) + mrng.randint(2, 4)
        else:
            metal["iron"] += mrng.randint(*MINE_IRON)
            if mrng.random() < LODE_P * fork and mrng.random() < 0.45:
                metal["iron"] += round(mrng.randint(*IRON_LODE) * LODE_LEFT) + mrng.randint(4, 7)
        if mrng.random() < MINE_GOLD_P:
            metal["gold"] += mrng.randint(1, 2)
        kit_check()

    def fight(minutes, extra_mp=0.0):
        """Minutes of fighting with the main power. Deterministic, so the XP pacing's random stream is untouched."""
        if not mastery["on"]:
            return
        mastery["combat_min"] += minutes
        mastery["mp"] += minutes * mastery_per_combat_minute(st["level"]) + extra_mp
        while mastery["level"] < 5 and mastery["mp"] >= MASTERY_NEED[mastery["level"]]:
            mastery["mp"] -= MASTERY_NEED[mastery["level"]]
            mastery["level"] += 1
            mastery["at"][mastery["level"]] = (st["level"], st["t"] / 60)

    def gain(amount):
        st["xp"] += amount
        while st["xp"] >= xp_to_next(st["level"]):
            st["xp"] -= xp_to_next(st["level"])
            st["level"] += 1
            level_times.setdefault(st["level"], st["t"])

    def levels(frac):  # a fraction of the current level
        gain(frac * xp_to_next(st["level"]))

    def overworld(minutes, awake=True):
        """Minute by minute outside (the Living Wilds, SPEC 21.3): kills by creature level and role, crafts, ores, building,
        Wayfarer quest steps, camps, first sightings, anomalies."""
        for _ in range(int(round(minutes))):
            st["t"] += 1
            budget.decay(1)
            lv = st["level"]
            xpn = xp_to_next(lv)
            night = (st["t"] % 20) >= 13           # 7 of every 20 minutes are night
            r = rng.random()
            mined = False
            if r < p["hunt"] + (0.15 if night else 0.0):
                for _k in range(rng.choice((1, 1, 2, 2, 3))):  # a fight of one to three creatures
                    # in your zone the creatures are within 2 levels of you; out of it, far below (taper)
                    delta = rng.randint(-2, 2) if rng.random() < p["zone_fit"] else -rng.randint(4, 12)
                    role = 1.0
                    x = rng.random()
                    for share, mult in ROLE_MIX:
                        x -= share
                        if x <= 0:
                            role = mult
                            break
                    # the budget first, then the level difference (taper after the budget, like XpBudget in the game)
                    gain(budget.pay("hunt", KILL_K * role) * diff(delta) * WILDS_RANK[RANKS[rank_i]] * xpn)
                fight(COMBAT_MIN["hunt_minute"])
            elif r < p["hunt"] + p["gather"]:
                for craft in rng.sample(("forage", "mine", "fish", "cook", "smith"), 2):  # two crafts a gathering minute
                    gain(budget.pay(craft, CRAFT_ACTION * rng.randint(1, 3)) * WILDS_RANK[RANKS[rank_i]] * xpn)
                    mined = mined or craft == "mine"
            if rng.random() < 0.25 * p["ores"]:
                gain(budget.pay("ore", ORE_FRAC * rng.randint(1, 4)) * taper(lv) * xpn)
                mined = True
            if mined:
                mine_minute()
            gain(budget.pay("build", BLOCK_FRAC * 12 * (1 + 2 * p["build_time"])) * taper(lv) * xpn)
            if rng.random() < p["quest"]:
                st["t"] += rng.uniform(2, 6)       # a Wayfarer step: talk, walk, do the thing
                levels(QUEST_STEP * rng.uniform(0.5, 1.6))
            if rng.random() < p["camp"]:
                st["t"] += rng.uniform(3, 6)       # a camp or nest cleared (once per cycle per camp)
                gain(budget.pay("camp", CAMP_K * gate_floor_xp(RANKS[rank_i], 1) / xpn) * WILDS_RANK[RANKS[rank_i]] * xpn)
            # first sightings: a fast start, then rarer as the world gets known
            hours = st["t"] / 60
            rate = DISCOVERY_FIRST_HOUR / 60 * math.exp(-hours / 6.0)
            if found[0] < DISCOVERY_TOTAL and rng.random() < rate:
                found[0] += 1
                levels(DISCOVERY_FRAC)
            if awake and rng.random() < p["engage"] / ANOMALY_EVERY_MIN:
                st["t"] += 2  # a rift tear takes a couple of minutes
                gain(ANOMALY_K * gate_floor_xp(RANKS[rank_i], 1))
                fight(COMBAT_MIN["rift"])

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
    mastery["on"] = True

    day_clock = 8.0
    bell_crypt_done = False
    while st["t"] < 200 * 60 and st["level"] < 140:
        rank = RANKS[rank_i]
        # 1) Time outside: hunting, crafting, building, quests, exploring, the odd anomaly.
        home = rng.uniform(*p["outside"]) * (1 + 0.5 * p["build_time"])
        if not bell_crypt_done:
            home = rng.uniform(14, 22)  # the first hour (the Commons): try the power, meet creatures, a rift tear, then go in
        before = st["t"]
        overworld(home)
        # Known quirk: in-game days advance only outdoors here; in the real game they also pass in gates (a flattering
        # simplification for a player who stays outside; docs/living-wilds/living_wilds_sim.py counts both).
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
                fight(COMBAT_MIN["trial"])
                ratio = st["level"] / RANK_MID[nxt]
                if rng.random() < min(0.95, 0.55 * ratio * p["skill"] + 0.25):
                    rank_i += 1
                    rank_times[nxt] = st["t"] / 60
                continue
        # 4) A gate run. The first is the Bell Crypt (E, three floors, content level 0, never breaks).
        real = bell_crypt_done and COMP is not None and rank in COMP["ranks"] and RANKS[max(0, rank_i - 1)] in COMP["ranks"]
        if not bell_crypt_done:
            g_rank, floors, cl = "E", 3, 3.0
            nfloors, danger, twist = 3, 1.0, "NONE"
        else:
            g_rank = rank if rng.random() > 0.15 else RANKS[max(0, rank_i - 1)]
            floors, cl = p["push"], RANK_MID[g_rank]
            nfloors, danger, twist = roll_gate(g_rank, rng) if real else (floors, 1.0, "NONE")
            if real:
                # the plan: push floors, one more if it is the Lord and safe enough; a Red Gate won't let you out early
                floors = min(nfloors, p["push"])
                if twist == "RED_GATE":
                    floors = nfloors
                elif floors + 1 == nfloors and gate_floor_risk(g_rank, nfloors, nfloors, st["level"] / cl, p["skill"], SIDE_SHARE[kind], danger) < LORD_TEMPT[kind]:
                    floors = nfloors
        ratio = st["level"] / cl
        below = g_rank != rank
        unbanked = 0.0
        gates += 1
        by_rank.setdefault(g_rank, [0, 0, 0, 0])[0] += 1
        cleared = 0
        st["t"] += 3 if not bell_crypt_done else rng.uniform(3, 8)  # walk to it (the Director keeps gates 300-1500 m out)

        def risk(f):
            if real:
                return gate_floor_risk(g_rank, f, nfloors, ratio, p["skill"], SIDE_SHARE[kind], danger)
            return death_risk(f, ratio, p["skill"])

        for floor in range(1, floors + 1):
            lo, hi = FLOOR_MIN[g_rank]
            floor_min = rng.uniform(lo, hi) / (0.85 + 0.15 * p["skill"])
            if real:
                floor_min *= floor_minutes_factor(g_rank, floor, nfloors, SIDE_SHARE[kind])
            st["t"] += floor_min
            fight(COMBAT_MIN["floor"] * floor_min, ELITES_PER_FLOOR * MAIN_SHARE * ELITE_MP)
            if rng.random() < risk(floor):
                deaths += 1
                by_rank[g_rank][1] += 1
                unbanked = 0.0
                st["t"] += 4 + DEATH_EXTRA_MIN
                break
            reward = gate_floor_xp(g_rank, floor) * (BELOW_RANK if below else 1.0)
            gain(KILL_SHARE * reward)
            unbanked += (1 - KILL_SHARE) * reward
            cleared = floor
            if bell_crypt_done and floor < floors and twist != "RED_GATE" and risk(floor + 1) > (STOP_RISK[kind] if real else 0.22) \
                    and (real or kind != "greedy"):
                break
        gain(unbanked)
        if cleared:
            # before W2 a gate also paid raw metal (kept apart: the comparison only)
            old["iron"] += OLD_GATE_IRON
            old["gold"] += OLD_GATE_GOLD
            if RANKS.index(g_rank) >= 2:
                old["diamond"] += OLD_GATE_DIAMOND_C
            kit_check()
        by_rank[g_rank][2] += cleared
        if bell_crypt_done and cleared == (nfloors if real else floors):
            by_rank[g_rank][3] += 1
            fight(0, MAIN_SHARE * BOSS_MP)  # the Lord waiting on the last floor
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

    return {"rank_hours": rank_times, "deaths": deaths, "gates": gates, "level_times": level_times, "by_rank": by_rank,
            "level_at": {m: level_at(m) for m in (10, 30, 60, 120)}, "first_power_min": first_power_min,
            "final_level": st["level"], "discoveries": found[0],
            "mastery_at": mastery["at"], "combat_minutes": mastery["combat_min"], "minutes": st["t"],
            "kit_at": kit_at, "old_kit_at": old_kit_at}


# Power mastery targets (P3 brief): your main power reaches level 3 around player level 20 and level 5 around 45.
MASTERY_TARGETS = {3: (17, 23), 5: (41, 49)}


def mastery_farm_check():
    """Nothing but fighting (a farm, a gate held open): hours of pure combat to take one power from level 1 to 5."""
    out = {}
    for lv in (10, 25, 45):
        need = sum(MASTERY_NEED.values())
        out[lv] = round(need / (60 * mastery_per_combat_minute(lv)), 1)
    return out


ENTRY_MIN = 3  # walk to the gate, gear check, entry


def push_ev_gate(rank, level=None, skill=1.0, kind="balanced", trials=20000, seed=7):
    """Levels per hour if you always plan to stop after floor N of a real gate of this rank (its most common floor
    count; the last floor is the Lord's). Red Gates won't let you out early. Time includes entry and deaths."""
    rng = random.Random(seed)
    level = level or RANK_MID[rank]
    fl = COMP["ranks"][rank]["floors"]
    nfl = int(max(fl.items(), key=lambda kv: kv[1])[0])
    out = []
    lo, hi = FLOOR_MIN[rank]
    for stop in range(1, nfl + 1):
        total = minutes = 0.0
        died = lords = 0
        for _ in range(trials):
            _, danger, twist = roll_gate(rank, rng)
            last = nfl if twist == "RED_GATE" else stop
            banked = unbanked = 0.0
            minutes += ENTRY_MIN
            for floor in range(1, last + 1):
                minutes += rng.uniform(lo, hi) * floor_minutes_factor(rank, floor, nfl, SIDE_SHARE[kind])
                if rng.random() < gate_floor_risk(rank, floor, nfl, level / RANK_MID[rank], skill, SIDE_SHARE[kind], danger):
                    unbanked = 0.0
                    died += 1
                    minutes += 4 + DEATH_EXTRA_MIN
                    break
                r = gate_floor_xp(rank, floor)
                banked += KILL_SHARE * r
                unbanked += (1 - KILL_SHARE) * r
                if floor == nfl:
                    lords += 1
            total += banked + unbanked
        out.append({"stop_at": stop, "levels_per_hour": round(total / xp_to_next(level) / (minutes / 60), 3),
                    "death_rate": round(died / trials, 3), "lord_rate": round(lords / trials, 3)})
    best = max(o["levels_per_hour"] for o in out)
    for o in out:
        o["vs_best"] = round(o["levels_per_hour"] / best, 2)
    return {"floors": nfl, "peak": max(out, key=lambda o: o["levels_per_hour"])["stop_at"], "by_stop": out}


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


def farm_check(delta=0):
    """A mob farm (60 hunter-role kills every 10 minutes, nothing else) at a few levels: levels per hour from kills alone.
    delta = creature level minus yours: 0 is the best case (a farm in your own region); -10 is a farm you have outgrown."""
    out = {}
    for lv in (3, 10, 25, 50):
        b = Budget()
        paid = 0.0
        for minute in range(600):
            b.decay(1)
            for _ in range(6):
                paid += b.pay("hunt", KILL_K) * diff(delta)
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
        row["by_gate_rank"] = {}
        for gr in RANKS:
            g = sum(x["by_rank"].get(gr, [0, 0, 0, 0])[0] for x in rs)
            if g:
                d = sum(x["by_rank"][gr][1] for x in rs if gr in x["by_rank"])
                fl = sum(x["by_rank"][gr][2] for x in rs if gr in x["by_rank"])
                lk = sum(x["by_rank"][gr][3] for x in rs if gr in x["by_rank"])
                row["by_gate_rank"][gr] = {"gates": g, "deaths_per_10": round(10 * d / g, 2), "floors_per_gate": round(fl / g, 2),
                                           "lords_per_10": round(10 * lk / g, 2)}
        row["first_power_minute"] = statistics.median(x["first_power_min"] for x in rs)
        row["discoveries_median"] = statistics.median(x["discoveries"] for x in rs)
        # main power mastery: the player level (and hours) when it reached each power level
        row["mastery_player_level"] = {}
        for ml in (2, 3, 4, 5):
            hit = [x["mastery_at"][ml] for x in rs if ml in x["mastery_at"]]
            if hit:
                row["mastery_player_level"][ml] = quantiles([h[0] for h in hit]) | {
                    "hours_median": round(statistics.median(h[1] for h in hit), 1), "reached": round(len(hit) / len(rs), 2)}
        # share of play time spent fighting (the sim's estimate; mastery comes only from these minutes)
        row["combat_share_median"] = round(statistics.median(x["combat_minutes"] / max(1.0, x["minutes"]) for x in rs), 3)
        # the deep's metal: when a tier's kit has its metal in hand, against the level its recipes open at
        row["metal"] = {}
        for tier, lv in KIT_LEVEL.items():
            road = [x["level_times"][lv] / 60 for x in rs if lv in x["level_times"]]
            got = [x["kit_at"][tier] for x in rs if tier in x["kit_at"]]
            was = [x["old_kit_at"][tier] for x in rs if tier in x["old_kit_at"]]
            ready = [max(x["level_times"][lv] / 60, x["kit_at"].get(tier, 1e9)) - x["level_times"][lv] / 60 for x in rs if lv in x["level_times"]]
            row["metal"][tier] = {"level_hours": round(statistics.median(road), 2) if road else None,
                                  "metal_hours": round(statistics.median(got), 2) if got else None,
                                  "metal_reached": round(len(got) / len(rs), 2),
                                  "wait_after_level_median": round(statistics.median(ready), 2) if ready else None,
                                  "wait_p90": round(statistics.quantiles(ready, n=10)[-1], 2) if len(ready) > 1 else None,
                                  "before_w2_metal_hours": round(statistics.median(was), 2) if was else None}
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
    for ml, (lo, hi) in MASTERY_TARGETS.items():
        med = bal["mastery_player_level"].get(ml, {}).get("median")
        checks.append({"main_power_level": ml, "target_player_level": [lo, hi], "balanced_median": med,
                       "pass": med is not None and lo <= med <= hi})
    # P3 gate targets (docs/modules/gates.md "Tuning"): the balanced player dies 1-3 times in ten gates at D, C and B,
    # and planning to go one floor deeper pays best somewhere between floors 3 and 5
    gate_checks = []
    evs = {r: push_ev_gate(r) for r in ("D", "C", "B")} if COMP else {}
    for r in ("D", "C", "B"):
        v = bal["by_gate_rank"].get(r)
        d = v["deaths_per_10"] if v else None
        gate_checks.append({"gate_rank": r, "deaths_per_10_gates": d, "target": list(GATE_DEATHS),
                            "pass": d is not None and GATE_DEATHS[0] <= d <= GATE_DEATHS[1]})
        if r in evs:
            gate_checks.append({"gate_rank": r, "push_ev_peak_floor": evs[r]["peak"], "target": list(PUSH_PEAK),
                                "pass": PUSH_PEAK[0] <= evs[r]["peak"] <= PUSH_PEAK[1]})
    checks += gate_checks
    result = {"summary": summary, "target_checks": checks, "gate_checks": gate_checks, "gate_content": CONTENT,
              "mastery_need": MASTERY_NEED,
              "mastery_pure_combat_hours_1_to_5": mastery_farm_check(),
              "push_ev_C_on_level": push_ev(),
              "push_ev_real_gates": evs,
              "push_ev_C_underlevelled": push_ev(level=28),
              "mob_farm_levels_per_hour": farm_check(),
              "mob_farm_outgrown_levels_per_hour": farm_check(-10),
              "wilds_trim": WILDS_TRIM,
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
        print("   the deep's metal (kit's metal in hand vs its level; wait after the level, median / p90; before W2):",
              {t: (v["level_hours"], v["metal_hours"], v["wait_after_level_median"], v["wait_p90"], v["before_w2_metal_hours"])
               for t, v in row["metal"].items()})
        print("   main power level -> player level:", {ml: (v["median"], v["hours_median"]) for ml, v in row["mastery_player_level"].items()},
              " combat share", row["combat_share_median"])
    print("\nTarget checks (balanced player):")
    for c in checks: print("  ", c)
    print("\nWILDS_TRIM =", WILDS_TRIM)
    print("Mob farm, kills only (levels per hour), on-level / ten levels below you:",
          result["mob_farm_levels_per_hour"], result["mob_farm_outgrown_levels_per_hour"])
    print("Mastery, nothing but fighting: hours to take one power from 1 to 5, by player level:",
          result["mastery_pure_combat_hours_1_to_5"])
    print("\nPush EV, C-rank gate, on-level player:")
    for e in result["push_ev_C_on_level"]: print("  ", e)
    for r, ev in result["push_ev_real_gates"].items():
        print(f"\nPush EV on real {r}-rank gates ({ev['floors']} floors, the last the Lord's), on-level balanced player: peak at {ev['peak']}")
        for e in ev["by_stop"]: print("  ", e)
    print("\nDeaths per 10 gates by gate rank:")
    for kind, row in summary.items():
        print("  ", kind, {r: (v["deaths_per_10"], v["floors_per_gate"], v["lords_per_10"]) for r, v in row["by_gate_rank"].items()})


if __name__ == "__main__":
    main()
