#!/usr/bin/env python3
"""AWAKEN combat simulator (planning harness, not game code). Arms as built (SPEC §25: S0, S1).

What it answers (docs/combat/PLAN.md §6.1): with the Arms combat system as built, how do four player profiles (Tank,
Dodger, Parrier, Expert) fare against every Gate Lord at its home rank? Plus the "diamond sword, Parrier, at D" row and
hits to kill an on-level normal creature.

What it reads: the numbers come from the game's own sources at start (archetypes.json, ArmsRules, ArmsData,
HunterTier, Rank, Theme, Model, BoltKind, Ability, GateBestiary, MobScaling), so it follows the code when they change.
What the code does not settle (how a player plays, where bodies stand, how often a flier is in reach) is ASSUMED below,
each with its reason; docs/combat/sim-2026-10-26.md lists them with their sources.

A kit Lord (SPEC §34, data/awaken/kit) fights as FightRunner does: its fight script picks moves by weight, `when` and
range, each move winds up (held 1 in 3 if HEAVY), strikes with its capsules and fires its ability under the move's
class, and phases enter through their transition. `--override kit.on=0` fights it the old way, to compare.

What it does NOT check: feel, animation, latency, multiplayer, powers, potions, eating mid-fight, gate modifiers.

Run:  python3 tools/sim/combat_sim.py                        (as built, 400 seeds, summary table; ~10 s on 4 cores)
      python3 tools/sim/combat_sim.py --lord bell_warden --seeds 100
      python3 tools/sim/combat_sim.py --item-level on
      python3 tools/sim/combat_sim.py --preset fixes             (the three bugs, as if fixed; see PRESETS)
      python3 tools/sim/combat_sim.py --preset recommended-hp    (every recommended change)
      python3 tools/sim/combat_sim.py --override lord.extra_health=1.85 --override fix.stagger=1
      python3 tools/sim/combat_sim.py --override longsword.strike_1.motion=1.0 --json /tmp/out.json
      python3 tools/sim/combat_sim.py --untold                   (a parrier's guard against blows with no tell)
Overrides: any CONFIG path (see default_config); `longsword.<move>.<field>` reaches the archetype's move rows,
`lords.<id>.<field>` a Lord's base numbers (hp, dmg, uptime), `ability.<lord>.<phase>.<index>.<field>` one ability
(e.g. ability.hive_regent.0.0.heavy=false). Deterministic for a seed; --jobs only changes speed.
"""
import argparse
import copy
import heapq
import json
import math
import os
import random
import re
import time
from concurrent.futures import ProcessPoolExecutor

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))

P_ARCH = "src/main/resources/data/awaken/arms/archetypes.json"
P_RULES = "src/main/java/dev/awaken/arms/ArmsRules.java"
P_WEAPONS = "src/main/java/dev/awaken/arms/ArmsData.java"
P_TIERS = "src/main/java/dev/awaken/gear/HunterTier.java"
P_RANK = "src/main/java/dev/awaken/progression/Rank.java"
P_THEME = "src/main/java/dev/awaken/mobs/def/Theme.java"
P_MODEL = "src/main/java/dev/awaken/mobs/def/Model.java"
P_BOLT = "src/main/java/dev/awaken/mobs/def/BoltKind.java"
P_ABILITY = "src/main/java/dev/awaken/mobs/def/Ability.java"
P_TIER = "src/main/java/dev/awaken/mobs/def/Tier.java"
P_BEST = "src/main/java/dev/awaken/mobs/def/GateBestiary.java"
P_SCALING = "src/main/java/dev/awaken/mobs/MobScaling.java"
P_GOALS = "src/main/java/dev/awaken/mobs/entity/MobGoals.java"
P_KIT = "src/main/resources/data/awaken/kit"  # the Creature Kit's move tables and fight scripts (SPEC §34.2-34.3)

LIGHT, HEAVY, PERILOUS, GRAB, NONE = "LIGHT", "HEAVY", "PERILOUS", "GRAB", "NONE"
RANK_ORDER = ["E", "D", "C", "B", "A", "S"]
PROFILE_ORDER = ["tank", "dodger", "parrier", "expert"]


# ---- reading the game's sources ---------------------------------------------------------------------------------------

def _read(rel):
    with open(os.path.join(ROOT, rel), encoding="utf-8") as f:
        return f.read()


def _num(s):
    s = s.strip().rstrip("FfDdLl")
    return float(s)


def _split_top(s, sep=","):
    """Split at top-level separators, string- and bracket-aware."""
    out, depth, cur, q, i = [], 0, [], False, 0
    while i < len(s):
        c = s[i]
        if q:
            cur.append(c)
            if c == "\\" and i + 1 < len(s):
                cur.append(s[i + 1])
                i += 2
                continue
            if c == '"':
                q = False
        elif c == '"':
            q = True
            cur.append(c)
        elif c in "([{":
            depth += 1
            cur.append(c)
        elif c in ")]}":
            depth -= 1
            cur.append(c)
        elif c == sep and depth == 0:
            out.append("".join(cur).strip())
            cur = []
        else:
            cur.append(c)
        i += 1
    if "".join(cur).strip():
        out.append("".join(cur).strip())
    return out


def _calls(expr):
    """'a(X, "y").b(1).c()' -> [('a', 'X, "y"'), ('b', '1'), ('c', '')]."""
    out, i, n = [], 0, len(expr)
    while i < n:
        while i < n and expr[i] in " .\n\t\r":
            i += 1
        j = i
        while j < n and (expr[j].isalnum() or expr[j] == "_"):
            j += 1
        if j >= n or expr[j] != "(":
            break
        depth, k, q = 0, j, False
        while k < n:
            c = expr[k]
            if q:
                if c == "\\":
                    k += 2
                    continue
                if c == '"':
                    q = False
            elif c == '"':
                q = True
            elif c == "(":
                depth += 1
            elif c == ")":
                depth -= 1
                if depth == 0:
                    break
            k += 1
        out.append((expr[i:j], expr[j + 1:k]))
        i = k + 1
    return out


def parse_archetype():
    d = json.loads(_read(P_ARCH))
    for a in d["archetypes"]:
        if a["id"] == "longsword":
            return a
    raise SystemExit("combat_sim: no longsword row in " + P_ARCH)


def parse_rules():
    """ArmsRules' named numbers (public static final ...)."""
    out = {}
    for m in re.finditer(r"static final (?:int|float|double)\s+([^;]+);", _read(P_RULES)):
        for part in _split_top(m.group(1)):
            mm = re.match(r"(\w+)\s*=\s*(-?[\d.]+)[FfDd]?\s*(?:/\s*([\d.]+)[FfDd]?)?$", part.strip())
            if mm:
                v = float(mm.group(2))
                if mm.group(3):
                    v /= float(mm.group(3))
                out[mm.group(1)] = v
    return out


def parse_weapons():
    out = {}
    for m in re.finditer(r'new Weapon\("(\w+)", "(\w+)", "(\w)", ([\d.]+)F, (-?[\d.]+)F', _read(P_WEAPONS)):
        out[m.group(1)] = {"id": m.group(1), "style": m.group(2), "tier": m.group(3), "damage": float(m.group(4)),
                           "speed": float(m.group(5))}
    return out


def parse_tiers():
    out = {}
    for m in re.finditer(r'(\w)\("(\w+)", "[^"]+", Rank\.\w+, Rarity\.\w+, new Armour\((\d+), (\d+), (\d+), (\d+), (\d+), ([\d.]+)f, ([\d.]+)f',
                         _read(P_TIERS)):
        out[m.group(1)] = {"id": m.group(2), "armor": sum(int(m.group(k)) for k in range(3, 7)),
                           "toughness": 4 * float(m.group(8))}
    return out


def parse_ranks():
    out = {}
    for m in re.finditer(r'(\w+)\("([^"]+)", (\d+), (\d+), ([\d.]+), 0x', _read(P_RANK)):
        out[m.group(1)] = {"level_required": int(m.group(3)), "content": int(m.group(4))}
    return out


def parse_themes():
    out = {}
    for m in re.finditer(r'(\w+)\("awaken:[^"]+", "([^"]+)", Rank\.(\w+), 0x\w+, 0x\w+, BoltKind\.(\w+)\)', _read(P_THEME)):
        out[m.group(1)] = {"display": m.group(2), "home": m.group(3), "bolt": m.group(4)}
    return out


def parse_models():
    out = {}
    for m in re.finditer(r'(\w+)\("[^"]*", Move\.(\w+), ([\d.]+)F, ([\d.]+)F', _read(P_MODEL)):
        out[m.group(1)] = {"move": m.group(2), "width": float(m.group(3)), "height": float(m.group(4))}
    return out


def parse_bolts():
    out = {}
    for m in re.finditer(r'(\w+)\((?:Forces\.Force\.(\w+)|null), Items\.\w+, ParticleTypes\.\w+, (?:MobEffects\.(\w+)|null), (\d+)\)',
                         _read(P_BOLT)):
        out[m.group(1)] = {"force": m.group(2), "effect": m.group(3), "ticks": int(m.group(4))}
    return out


def parse_ability_meta():
    src = _read(P_ABILITY)
    base = {"windup": 20, "cooldown": 100, "count": 1, "range": 6.0, "power": 1.0, "radius": 3.0, "inner": 0.0}
    for decl in re.findall(r"private (?:int|float) ((?:windup|range)[^;]*);", src):
        for part in _split_top(decl):
            mm = re.match(r"(\w+)\s*=\s*([\d.]+)F?$", part.strip())
            if mm:
                base[mm.group(1)] = float(mm.group(2))
    builder = src[src.index("private Builder(Kind kind"):]
    per_kind = {}
    for m in re.finditer(r"case ([A-Z_, ]+) -> \{([^}]*)\}", builder):
        vals = {}
        for k, v in re.findall(r"(\w+) = ([\d.]+)F?;", m.group(2)):
            vals[k] = float(v)
        for kind in m.group(1).split(","):
            per_kind[kind.strip()] = vals
    strike = {}
    for m in re.finditer(r"case ([A-Z_, ]+) -> Strikes\.Kind\.(\w+);", src):
        for kind in m.group(1).split(","):
            strike[kind.strip()] = m.group(2)
    return base, per_kind, strike


def parse_ability(expr, meta):
    base, per_kind, strike_map = meta
    calls = _calls(expr)
    head = _split_top(calls[0][1])
    kind = head[0].strip()
    a = dict(base)
    a.update(per_kind.get(kind, {}))
    a.update({"kind": kind, "name": head[1].strip('"'), "bolt": None, "fx": None, "summon": None, "roots": None, "heavy": False})
    for name, args in calls[1:]:
        if name == "wind":
            a["windup"] = _num(args)
        elif name == "cd":
            a["cooldown"] = _num(args)
        elif name == "n":
            a["count"] = _num(args)
        elif name in ("range", "power", "radius", "inner"):
            a[name] = _num(args)
        elif name == "bolt":
            a["bolt"] = args.split(".")[-1].strip()
        elif name == "fx":
            p = _split_top(args)
            a["fx"] = (p[0].split(".")[-1], int(_num(p[1])), int(_num(p[2])))
        elif name == "summon":
            a["summon"] = args.strip().strip('"')
        elif name == "moving":
            a["roots"] = False
        elif name == "rooted":
            a["roots"] = True
        elif name == "heavy":
            a["heavy"] = True
    if a["roots"] is None:  # Ability.java:145: everything roots but shots, heals and frenzies
        a["roots"] = kind not in ("BOLT", "ARROWS", "HEAL", "FRENZY")
    a["windup"], a["cooldown"], a["count"] = int(a["windup"]), int(a["cooldown"]), int(a["count"])
    a["told"] = PERILOUS if a["heavy"] else strike_map.get(kind, NONE)  # Ability.strike(), Ability.java:74-82
    return a


def parse_bestiary(meta):
    defs = {}
    for m in re.finditer(r"out\.add\((m\(.*?)\.build\(\)\);", _read(P_BEST), re.S):
        calls = _calls(m.group(1))
        a = _split_top(calls[0][1])
        d = {"id": a[0].strip('"'), "name": a[1].strip('"'), "theme": a[2].split(".")[1], "tier": a[3].split(".")[1],
             "model": a[4].split(".")[1], "hp": 20.0, "dmg": 3.0, "spd": 0.25, "armor": 0.0, "scale": 1.0,
             "traits": [], "phases": []}  # MobDef.B defaults, MobDef.java:148-149
        for name, args in calls[1:]:
            if name in ("hp", "dmg", "spd", "armor", "scale"):
                d[name] = _num(args)
            elif name == "traits":
                d["traits"] = [x.strip() for x in args.split(",") if x.strip()]
            elif name == "phase":
                p = _split_top(args)
                rest = p[2:]
                if rest and not rest[0].startswith("a("):
                    rest = rest[1:]  # phase(below, line, speedBonus, abilities...)
                d["phases"].append({"below": _num(p[0]), "line": p[1].strip('"'), "abilities": [parse_ability(x, meta) for x in rest]})
            elif name == "ab":
                d["phases"].append({"below": 1.0, "line": "", "abilities": [parse_ability(x, meta) for x in _split_top(args)]})
        if not d["phases"]:
            d["phases"].append({"below": 1.0, "line": "", "abilities": []})
        defs[d["id"]] = d
    return defs


def parse_scaling():
    src = _read(P_SCALING)
    hpl = float(re.search(r"healthMultiplier\(int lv\) \{\s*return 1\.0 \+ ([\d.]+) \* lv;", src).group(1))
    dpl = float(re.search(r"damageMultiplier\(int lv\) \{\s*return 1\.0 \+ ([\d.]+) \* lv;", src).group(1))
    apl = 1.0 / float(re.search(r"return lv / ([\d.]+);", src).group(1))
    role_h = float(re.search(r"roleHealth\(.*?case BOSS -> ([\d.]+);", src, re.S).group(1))
    role_d = float(re.search(r"roleDamage\(.*?case BOSS -> ([\d.]+);", src, re.S).group(1))
    lord_extra = float(re.search(r"LORD\(MobRole\.Role\.BOSS, ([\d.]+)\)", _read(P_TIER)).group(1))
    return {"health_per_level": hpl, "damage_per_level": dpl, "armor_per_level": apl, "role_health": role_h,
            "role_damage": role_d, "extra_health": lord_extra}


def parse_told_melee():
    src = _read(P_GOALS)
    boss = int(re.search(r"case BOSS, BREAK, WARDEN, NIGHTMARE -> (\d+);", src).group(1))
    normal = int(re.search(r"default -> (\d+);\s*\};\s*return kind == Strikes\.Kind\.HEAVY", src).group(1))
    heavy_extra = int(re.search(r"Strikes\.Kind\.HEAVY \? base \+ (\d+)", src).group(1))
    every = int(re.search(r"\+\+swings % (\d+) == 0", src).group(1))
    mult = float(re.search(r"HAYMAKER, ([\d.]+),", src).group(1))
    return {"tell_boss": boss, "tell_normal": normal, "heavy_extra": heavy_extra, "heavy_every": every, "heavy_mult": 1 + mult}


def parse_kits():
    """Every kit boss's move table and fight script (FightRunner fights them instead of the AbilityRunner and ToldMelee)."""
    path = os.path.join(ROOT, P_KIT, "index.json")
    if not os.path.exists(path):
        return {}
    out = {}
    for k in json.load(open(path))["kits"]:
        fp = os.path.join(ROOT, P_KIT, "fights", k + ".json")
        if not os.path.exists(fp):
            continue
        moves = {m["id"]: m for m in json.load(open(os.path.join(ROOT, P_KIT, "moves", k + ".json")))["moves"]}
        out[k] = {"moves": moves, "fight": json.load(open(fp))}
    return out


def kit_reach(m, half):
    """How far a move's capsules reach from the body's edge, in front and behind (blocks), its root motion included."""
    caps = [c for tick in m.get("hits", []) for c in tick]
    if not caps:
        return (0.0, 0.0)
    adv = m.get("motion", {}).get("advance", 0.0)
    front = max(max(math.hypot(c[0], c[2]), math.hypot(c[3], c[5])) + c[6] for c in caps) - half + adv
    back = max(max(-c[2], -c[5]) + c[6] for c in caps) - half
    return (front, back)


def load_world():
    meta = parse_ability_meta()
    return {"arch": parse_archetype(), "rules": parse_rules(), "weapons": parse_weapons(), "tiers": parse_tiers(),
            "ranks": parse_ranks(), "themes": parse_themes(), "models": parse_models(), "bolts": parse_bolts(),
            "mobs": parse_bestiary(meta), "scaling": parse_scaling(), "melee": parse_told_melee(), "kits": parse_kits()}


# ---- CONFIG: the game's numbers (parsed) plus the ASSUMED play and geometry ------------------------------------------

def default_config(W):
    lords = {}
    for d in W["mobs"].values():
        if d["tier"] != "LORD":
            continue
        model = W["models"][d["model"]]
        flies = model["move"] == "FLY"
        # ASSUMED reach uptime (share of time the player stands within the blade's reach of it, before knockbacks,
        # charges and dodges take more): ground Lords chase you (ToldMelee), 0.85; fliers orbit at 3.5 + their width,
        # 2-4 blocks up (MobGoals.java:240-241), out of a 3.0-3.4 + 0.3 block reach measured eye to box
        # (ArmsServer.java:250) and its 1.5-above-the-eye filter (ArmsServer.java:263): in reach only around dives, 0.30;
        # the Mother of Spores floats above that filter between dives (a ghast 3 wide), 0.15.
        lords[d["id"]] = {"hp": d["hp"], "dmg": d["dmg"], "uptime": -1.0, "flies": flies}  # uptime < 0: the group's
    return {
        "longsword": copy.deepcopy(W["arch"]),
        "rules": dict(W["rules"]),
        "lord": dict(W["scaling"], **{
            "global_cd": 14,        # AbilityRunner.java:193 (bosses)
            "heavy_windup": 8,      # AbilityRunner.java:125 (a heavy-flagged ability winds up 8 ticks longer)
            "damage_a": 1.0, "damage_b": 0.0,  # what-if: Gate Lord damage x (a + b x level); 1, 0 = as built
            "phase_shield": 30,     # AwakenBoss.java:84 (invulnerable roar on a new phase)
            "melee_cooldown": 20,   # vanilla MeleeAttackGoal.java:136,152 (adjustedTickDelay(20))
            "dive_cd": 30, "dive_cd_rand": 20,  # MobGoals.java:231 (a flier's next dive)
        }),
        "melee": dict(W["melee"]),
        "lords": lords,
        # ASSUMED reach uptime by group (see above) and the mean ticks spent out of reach each time
        "reach": {"ground": 0.85, "flier": 0.30, "mother_of_spores": 0.15, "out_ground": 12, "out_flier": 20},
        # creature numbers the sim needs that are not in a def: vanilla's flat parts, and what-if knobs
        "mobs": {
            "ignite_s": 4,          # AwakenMob.java:595 (IGNITE: a melee hit sets you alight 4 s)
            "fire_bolt_s": 3,       # Abilities.java:77 (any FIRE ability hit: 3 s)
            "fang_flat": 6.0,       # vanilla EvokerFangs bite (Abilities.java:248)
            "fang_scaled": 0,       # 1: the fang bite deals the ability's own scaled damage instead
            "poison_scale": 1.0,    # what-if: poison seconds x this
            "minion_damage": 1.0,   # what-if: a Lord's summons hit x this (they are normal creatures at its level)
            # ASSUMED: the chance a strike aimed at the Lord also takes a minion in its arc (wide arcs; half for
            # narrow ones); ranged minions are hunted down instead (a detour) by everyone but the Tank
            "cleave_ground": 0.5, "cleave_flier": 0.25,
        },
        "player": {
            "base_health": 20.0, "points_per_level": 3,  # SPEC §3: +3 points a level
            # ASSUMED build (PLAN §2.2's): 42% STR, 30% VIT, 18% AGI, 10% PER of the points
            "build": {"STR": 0.42, "VIT": 0.30, "AGI": 0.18, "PER": 0.10},
            "saturation": 12.8,     # ASSUMED: walked in fed (food 20) on a cooked steak's saturation (8 x 0.8 x 2)
            "dodge_reach_keep": 0.6,  # ASSUMED: after a dodge you are still in reach this often (else 10 ticks to close)
        },
        # ASSUMED: the four profiles of PLAN §6.1. cadence: chance a chain press lands in the rhythm window; keep: chance
        # a dodge leaves you in reach; detour: hunts down ranged summons; art: spends a full meter on the Art.
        "profiles": {
            "tank": {"deflect": 0.0, "dodge_any": 0.0, "dodge_perilous": 0.0, "perfect": 1 / 3, "cadence": 0.5, "detour": 0, "keep": 0.6, "art": 1},
            "dodger": {"deflect": 0.0, "dodge_any": 0.70, "dodge_perilous": 0.0, "perfect": 1 / 3, "cadence": 0.5, "detour": 1, "keep": 0.6, "art": 1},
            "parrier": {"deflect": 0.60, "dodge_any": 0.0, "dodge_perilous": 0.0, "perfect": 1 / 3, "cadence": 0.5, "detour": 1, "keep": 0.6, "art": 1},
            "expert": {"deflect": 0.85, "dodge_any": 0.0, "dodge_perilous": 0.90, "perfect": 1 / 3, "cadence": 0.95, "detour": 1, "keep": 0.8, "art": 1},
        },
        # what-ifs for the bugs (0 = as built)
        "fix": {
            "stagger": 0,       # 1: the AbilityRunner starts nothing while its Lord is broken (AbilityRunner.java:87-88)
            "landed_class": 0,  # 1: Force bolts, fangs, auras and clouds land as the class they were told as
            "effects": 0,       # 1: an evaded or deflected ability no longer burns, poisons or weakens (Abilities.java:76-79)
        },
        "item_level": {"on": 0, "per_level": 0.075},  # PowerMath.java:42-44 levelScale, mirrored on the weapon
        # the Creature Kit (SPEC §34.3): a kit Lord fights by its fight script and move table (on: 0 = the old way, to
        # compare). ASSUMED geometry, since the sim has no positions: in reach you stand 0.3-2.0 blocks from its edge
        # and behind it this often; out of reach 3-12 blocks. A kit flier orbits low (MobGoals.FlyHunt.scripted) 1 block
        # from your edge, or 6 when it drifts out (1 retarget in 4): in reach about as often as it sits close.
        "kit": {"on": 1, "near": [0.3, 2.0], "far": [3.0, 12.0], "behind": 0.15, "flier_uptime": 0.6,
                "flier_near": [0.6, 1.4], "flier_far": [4.0, 8.0],
                "damage": 1.0},  # damage: what-if, every move's damage x this
        "abilities": {},  # ability.<lord>.<phase>.<index>.<field>=value (phase and index from 0, as in GateBestiary)
        "diamond_damage": 7.0,  # vanilla diamond sword (gear/GearTests.java:293-296)
        "fight_cap_s": 600,
    }


def apply_override(cfg, path, value):
    keys = path.split(".")
    if keys[0] == "ability":
        if len(keys) != 5:
            raise SystemExit(f"combat_sim: ability overrides are ability.<lord>.<phase>.<index>.<field>, got {path!r}")
        v = value
        if value in ("true", "false", "True", "False"):
            v = value in ("true", "True")
        else:
            try:
                v = float(value)
            except ValueError:
                pass
        cfg["abilities"][path[len("ability."):]] = v
        return
    node = cfg
    if keys[0] == "longsword" and len(keys) >= 3 and keys[1] in cfg["longsword"]["moves"]:
        keys = ["longsword", "moves"] + keys[1:]
    for k in keys[:-1]:
        if k not in node:
            raise SystemExit(f"combat_sim: unknown override path {path!r} (no {k!r})")
        node = node[k]
    last = keys[-1]
    if last not in node:
        raise SystemExit(f"combat_sim: unknown override path {path!r} (no {last!r})")
    old = node[last]
    if isinstance(old, bool):
        node[last] = value not in ("0", "false", "False")
    elif isinstance(old, int) and not isinstance(old, bool):
        node[last] = int(float(value))
    elif isinstance(old, float):
        node[last] = float(value)
    elif isinstance(old, list):
        node[last] = [float(x) for x in value.split(":")]
    else:
        node[last] = value


# ---- the player at a rank ---------------------------------------------------------------------------------------------

def player_at(W, cfg, rank, weapon_damage):
    """An on-level player at a rank: its content level, the build's stats, the rank's full Hunter set."""
    P = cfg["player"]
    level = W["ranks"][rank]["content"]
    pts = P["points_per_level"] * (level - 1)
    st = {k: v * pts for k, v in P["build"].items()}  # points above the starting 5
    tier = W["tiers"][rank]
    hp = P["base_health"] + 0.6 * st["VIT"] + (4.0 if rank == "D" else 0.0)  # Progression.java:297; SetBonus.java:50,192
    return {
        "level": level, "rank": rank, "stats": st,
        "attack": 1.0 + weapon_damage,                      # attack damage over the player's 1 (ArmsData.java:17)
        "str": 0.025 * st["STR"],                           # Progression.java:294 (ADD_MULTIPLIED_BASE)
        "speed": 1.0 + min(0.60, 0.01 * st["AGI"]),         # Progression.java:296
        "resist": min(0.35, 0.005 * st["VIT"]),             # ProgressionModule.java:324-327
        "crit": min(0.35, 0.004 * st["PER"]),               # ProgressionModule.java:347-351 (x1.6)
        "max_hp": hp,
        "armor": tier["armor"], "toughness": tier["toughness"],
        "cornered": min(0.30, 0.15 + 0.01 * (st["STR"] / 2)) if rank == "D" else 0.0,  # SetBonus.java:114-116
        "ward_force": 0.25 if rank == "C" else 0.0,         # SetBonus.java:52,119 (Gatewrought, full set)
        "ward_spirit": 0.30 if rank == "B" else 0.0,        # SetBonus.java:55 (Veilwoven)
        "ward_lord": 0.20 if rank == "A" else 0.0,          # SetBonus.java:59 (Lordscale: Gravity and Charge)
        "flicker": rank == "B",                             # GearCombat.java:236-243 (Veil Flicker, SPI 5: 10%)
        "item_level": cfg["item_level"]["per_level"] * level if cfg["item_level"]["on"] else 0.0,
    }


def armor_after(dmg, armor, toughness):
    """Vanilla 26.3 CombatRules.getDamageAfterAbsorb."""
    t = 2.0 + toughness / 4.0
    real = min(20.0, max(armor * 0.2, armor - dmg / t))
    return dmg * (1.0 - real / 25.0)


# ---- one fight --------------------------------------------------------------------------------------------------------

class Blow:
    __slots__ = ("est", "land", "told", "landed", "factor", "flat", "expose", "pos", "posture_ok", "armor", "force",
                 "fx", "name", "src", "alive", "plan", "plan_t", "melee", "fire_s", "knock", "front", "lord", "minion",
                 "heavy_melee", "is_ability", "kit")

    def __init__(self, name, told, landed, est, factor=1.0, expose=1.0, pos=0.0, posture_ok=False, armor=True, force=None,
                 fx=None, src="lord", melee=False, flat=0.0, fire_s=0, knock=0, front=1.0):
        self.name, self.told, self.landed, self.est, self.land = name, told, landed, est, -1
        self.factor, self.flat, self.expose, self.pos, self.posture_ok = factor, flat, expose, pos, posture_ok
        self.armor, self.force, self.fx, self.src, self.alive = armor, force, fx, src, True
        self.plan, self.plan_t, self.melee, self.fire_s, self.knock, self.front = None, -1, melee, fire_s, knock, front
        self.lord = src == "lord"
        self.minion = None
        self.heavy_melee = False
        self.is_ability = False
        self.kit = None  # a kit move's capsules: (front, back) reach from its edge, blocks


class Move:
    __slots__ = ("name", "start", "hit_t", "active_end", "end", "step", "cadence", "next_press", "press_kind", "motion",
                 "posture", "cap", "finisher", "arc", "done", "total")


EXPOSE = {"SLAM": 1.0, "BURST": 0.15, "CHARGE": 0.95, "BOLT": 1.0, "RAIN": 0.8, "BEAM": 1.0, "BLINK": 1.0, "CONE": 1.0,
          "PULL": 1.0, "GUST": 1.0, "ROOT": 0.9, "LIFT": 0.9, "FANGS": 1.0, "FANGS_RING": 0.8, "AURA": 1.0, "CLOUD": 1.0}
# ASSUMED: how often a 2.6-block dodge also carries you out of the blow itself (matters when it lands as NONE, which
# the dodge's evade ticks do not cover): lines, marks and shots yes; areas round the body no.
POSITIONAL = {"BOLT": 0.9, "NOVA": 0.9, "FANGS": 0.85, "FANGS_RING": 0.6, "RAIN": 0.9, "BEAM": 0.9, "CHARGE": 0.8,
              "CONE": 0.6, "ROOT": 0.9, "LIFT": 0.9, "CLOUD": 0.9, "MINION_BOLT": 0.9}
FACTOR = {"PULL": 0.4, "ROOT": 0.5, "LIFT": 0.3}  # Abilities.java:343,382,391
KNOCK = {"SLAM": 6, "CONE": 8, "GUST": 25, "LIFT": 40}  # ASSUMED ticks out of reach after being hit (pushes, lifts)


class Fight:
    def __init__(self, W, cfg, lord_def, profile, P, rng, weapon_label):
        self.W, self.cfg, self.rng, self.P, self.weapon = W, cfg, rng, P, weapon_label
        self.prof = cfg["profiles"][profile]
        self.profile = profile
        arch = cfg["longsword"]
        self.arch = arch
        self.R = cfg["rules"]
        LC = cfg["lord"]
        lv = P["level"]
        self.lv = lv
        ld = lord_def
        lc = cfg["lords"][ld["id"]]
        self.ld = ld
        model = W["models"][ld["model"]]
        self.flies = model["move"] == "FLY"
        RC = cfg["reach"]
        self.kit = W["kits"].get(ld["id"]) if cfg["kit"]["on"] else None
        if self.kit is not None:
            half = model["width"] * ld["scale"] * 0.5
            for m in self.kit["moves"].values():
                m["_reach"] = kit_reach(m, half)
        if lc["uptime"] >= 0:
            self.uptime = lc["uptime"]
        elif self.kit is not None and self.flies:
            self.uptime = cfg["kit"]["flier_uptime"]
        else:
            self.uptime = RC.get(ld["id"], RC["flier"] if self.flies else RC["ground"])
        self.mean_out = RC["out_flier"] if self.flies else RC["out_ground"]
        self.MC = cfg["mobs"]
        self.l_max = lc["hp"] * (1 + LC["health_per_level"] * lv) * LC["role_health"] * LC["extra_health"]
        self.l_hp = self.l_max
        # damage_a + damage_b x level: a Lord-only damage curve to test (1, 0 = as built)
        self.l_atk = lc["dmg"] * (1 + LC["damage_per_level"] * lv) * LC["role_damage"] * (LC["damage_a"] + LC["damage_b"] * lv)
        self.l_armor = LC["armor_per_level"] * lv
        self.theme_bolt = W["themes"][ld["theme"]]["bolt"]
        self.traits = set(ld["traits"])
        self.haunted = "HAUNTED" in self.traits
        self.ignite = "IGNITE" in self.traits
        # lord state
        self.phase = 0
        self.abilities = ld["phases"][0]["abilities"]
        self.cd = [20 + rng.randrange(40) + i * 15 for i in range(len(self.abilities))]  # AbilityRunner.java:55
        self.gcd = 20
        self.active = None
        self.active_i = -1
        self.left = 0
        self.active_blows = []
        self.active_delays = []
        self.tick_count = rng.randrange(4)
        self.charge_until = -1
        self.stagger = 0
        self.guard = 0
        self.frenzy = 0
        self.shield = 0
        self.slowed_until = -1
        self.decoys = []
        self.minions = []
        self.melee_running = False
        self.melee_cd = 0
        self.tell_left = 0
        self.tell_blow = None
        self.swings = 0
        self.dive_cd = 0
        self.dive_tell = -1
        self.dive_left = 0
        self.dive_blow = None
        self.phase_offset = rng.randrange(140)
        # the fight runner (FightRunner.java): the move playing, its stretched timings, the string, the gap
        self.k_move = None
        self.k_t = self.k_w = self.k_act = self.k_rec = 0
        self.k_string = []
        self.k_gap = 20
        self.k_intro = False
        self.k_pending = -1
        self.k_blows = []
        self.k_ab = None
        self.k_ab_blows = []
        self.k_d, self.k_behind, self.k_d_state = 1.0, False, None
        # posture (ArmsRules.java:26-32)
        self.pmax = self.R["POSTURE_LORD"]
        self.pval = 0.0
        self.p_last = -10 ** 6
        self.broken_until = -1
        self.next_break = -1
        # player state
        self.hp = P["max_hp"]
        self.max_hp = P["max_hp"]
        self.mv = None
        self.hold = None
        self.hold_release = -1
        self.guard_up = False
        self.raised_at = -10 ** 6
        self.lowered_at = -10 ** 6
        self.locked_out = False
        self.raise_deflected = True
        self.guard_cd_until = -1
        self.guard_lower_t = -1
        self.dodge_at = -10 ** 6
        self.dodge_d = arch["dodge"]
        self.charges = int(self.dodge_d["charges"])
        self.recharge_at = -1
        self.deflect_at = -10 ** 6
        self.resonance = 0.0
        self.plans = []
        self.heap = []
        self.seq = 0
        self.last_hurt = 0.0
        self.last_hurt_t = -10 ** 6
        self.burn_until = -1
        self.burn_next = -1
        self.poison_until = -1
        self.poison_next = -1
        self.poison_iv = 25
        self.weak_until = -1
        self.flicker_ready = 0
        self.food, self.sat, self.exh, self.food_timer = 20, cfg["player"]["saturation"], 0.0, 0
        self.forced_out = -1
        self.forced_in = -1
        self.reach_state = rng.random() < self.uptime
        self.reach_switch = 0
        self.detour = None
        self.detour_end = -1
        # moves (recovery scaled by attack speed, ArmsRules.java:94-97)
        g = arch["guard"]
        self.deflect_ticks, self.block_red, self.heavy_red = int(g["deflect"]), g["reduction"], g["heavyReduction"]
        self.guard_break, self.lockout = int(g["breakTicks"]), int(g["lockout"])
        self.moves = {}
        scale = min(1.6, max(0.6, P["speed"]))
        for name, m in arch["moves"].items():
            rec = max(2, int(math.floor(m["recovery"] / scale + 0.5)))
            self.moves[name] = (int(m["windup"]), int(m["active"]), rec, m.get("cadence"), m["motion"], m["posture"],
                                m.get("bossCap", 0.0), bool(m.get("finisher", False)), m.get("arc", 90), m.get("window", 0))
        self.chain = list(arch["chain"])
        self.late = int(arch["late"])
        self.slip_window = self.moves["slipcut"][9]
        self.riposte_window = self.moves["riposte"][9]
        self.charge_min = int(arch["moves"]["heavy"]["charge"]["min"])
        self.charge_max = int(arch["moves"]["heavy"]["charge"]["max"])
        # stats
        self.st = {"in_reach": 0, "ticks": 0, "deflects": 0, "blocks": 0, "breaks_guard": 0, "evades": 0, "perfect": 0,
                   "breaks": 0, "executions": 0, "arts": 0, "ripostes": 0, "slipcuts": 0, "strikes": 0, "whiffs": 0,
                   "dmg_by_move": {}, "taken_by": {}, "death_by": None, "phase_ticks": [0, 0, 0], "broken_ticks": 0,
                   "minions_spawned": 0, "heals": 0, "kit_moves": {},
                   # the kit's cadence (docs/bosses/REWORK.md R0): ticks told or striking, recovering, between moves,
                   # in a NONE move (intro, transition, summon), opened (broken or staggered); the longest spell between
                   # moves; the blows thrown; each phrase's length (a pool pick and the string it carried)
                   "cad": {"threat": 0, "recover": 0, "idle": 0, "show": 0, "opened": 0, "run": 0, "run_max": 0,
                           "blows": 0, "phrases": {}, "phrase": 0}}

    # ---- helpers --------------------------------------------------------------------------------------------------------

    def in_reach(self, t):
        if t < self.forced_out:
            return False
        if t < self.forced_in:
            return True
        return self.reach_state

    def tick_reach(self, t):
        if t >= self.reach_switch:
            if self.uptime >= 0.999:
                self.reach_state, self.reach_switch = True, 10 ** 9
                return
            self.reach_state = not self.reach_state if t > 0 else self.reach_state
            mean_in = self.mean_out * self.uptime / max(1e-6, 1 - self.uptime)
            mean = mean_in if self.reach_state else self.mean_out
            self.reach_switch = t + max(1, int(self.rng.expovariate(1.0 / mean)))

    def lord_attack(self):
        return self.l_atk * (1.3 if self.frenzy > 0 else 1.0)  # AwakenMob.java:511 (Frenzy +30%)

    def bolt_of(self, a):
        return a["bolt"] or self.theme_bolt

    def push(self, b, t):
        self.seq += 1
        b.land = t
        heapq.heappush(self.heap, (t, self.seq, b))

    def new_blow(self, b):
        """A told blow: the profile decides now how to meet it (PLAN §6.1)."""
        self.plan_blow(b)
        return b

    def plan_blow(self, b):
        pr, rng = self.prof, self.rng
        told = b.told
        if told == NONE or b.est < 0:
            return
        dd = self.dodge_d
        evade, perfect = int(dd["evade"]), int(dd["perfect"])
        if told in (LIGHT, HEAVY) and pr["deflect"] > 0:
            b.plan = "guard"
            if rng.random() < pr["deflect"]:
                b.plan_t = b.est - rng.randrange(self.deflect_ticks)
            else:  # raised a little early: it blocks (PLAN §6.1 "holding guard otherwise")
                b.plan_t = b.est - (self.deflect_ticks + 1 + rng.randrange(4))
        elif (pr["dodge_any"] > 0 and rng.random() < pr["dodge_any"]) or \
                (told in (PERILOUS, GRAB) and pr["dodge_perilous"] > 0 and rng.random() < pr["dodge_perilous"]):
            b.plan = "dodge"
            if rng.random() < pr["perfect"]:
                b.plan_t = b.est - rng.randrange(perfect)
            else:
                b.plan_t = b.est - (perfect + rng.randrange(max(1, evade - perfect)))
        else:
            return
        self.plans.append(b)

    def next_defence(self, t):
        best = 10 ** 9
        keep = []
        for b in self.plans:
            if not b.alive or (b.land >= 0 and b.land < t):
                continue
            keep.append(b)
            if b.plan_t >= t and b.plan_t < best:
                best = b.plan_t
        self.plans = keep
        return best

    def cancel(self, blows, t):
        for b in blows:
            if b is not None and b.alive and b.land < 0:
                b.alive = False
                if b.plan == "guard" and self.guard_up:
                    self.guard_lower_t = t

    # ---- the Lord ---------------------------------------------------------------------------------------------------------

    def lord_tick(self, t):
        if t < self.slowed_until and (t & 1):
            # the perfect dodge's beat: the attacker skips every other tick (ArmsServer.java onEntityTick); its pending
            # blows come a tick later and the player sees it
            for b in self.active_blows + [self.tell_blow, self.dive_blow]:
                if b is not None and b.alive and b.land < 0:
                    b.est += 1
                    if b.plan_t > t:
                        b.plan_t += 1
            return
        self.tick_count += 1
        if self.stagger > 0:
            self.stagger -= 1
            self.st["broken_ticks"] += 1
        if self.guard > 0:
            self.guard -= 1
        if self.frenzy > 0:
            self.frenzy -= 1
        if self.shield > 0:
            self.shield -= 1
        if self.decoys:
            self.decoys = [d for d in self.decoys if d > t]
        if self.kit is not None:
            self.kit_tick(t)
            return
        self.runner_tick(t)
        if self.active is None:
            self.check_phase(t)
        if self.flies:
            self.dive_tick(t)
        else:
            self.melee_tick(t)

    def held(self, t):
        return self.stagger > 0 or (self.active is not None and self.active["roots"]) or t < self.charge_until

    def check_phase(self, t):
        frac = self.l_hp / self.l_max
        want = 0
        for i in range(1, len(self.ld["phases"])):
            if frac < self.ld["phases"][i]["below"]:
                want = i
        if want <= self.phase:
            return
        self.phase = want
        self.abilities = self.ld["phases"][want]["abilities"]
        self.cd = [20 + self.rng.randrange(40) + i * 15 for i in range(len(self.abilities))]
        self.gcd = 20
        self.shield = self.cfg["lord"]["phase_shield"]

    def usable(self, a, t):
        k = a["kind"]
        near = self.in_reach(t)
        if k in ("SLAM", "CONE"):
            return near or self.rng.random() < 0.3
        if k in ("FANGS_RING", "GUARD"):
            return near or self.rng.random() < 0.5
        if k == "SUMMON":  # Mobs.java:161-167
            mine = sum(1 for m in self.minions if m["alive"])
            return mine + a["count"] <= max(3, a["count"] * 2)
        if k == "DECOY":
            return len(self.decoys) < 3
        if k == "HEAL":
            return self.l_hp < self.l_max * 0.7  # Abilities.java:332-338
        if k == "LEAP":
            return not near
        return True

    def runner_tick(self, t):
        """AbilityRunner.tick (AbilityRunner.java:75-104)."""
        if self.active is not None:
            self.left -= 1
            if self.left > 0:
                return
            a, i = self.active, self.active_i
            self.active = None
            self.cd[i] = a["cooldown"] + self.rng.randrange(20)
            self.gcd = self.cfg["lord"]["global_cd"]
            self.execute(a, t)
            return
        cd = self.cd
        for i in range(len(cd)):
            if cd[i] > 0:
                cd[i] -= 1
        if self.gcd > 0:
            self.gcd -= 1
            return
        if not self.abilities or (self.tick_count & 3) != 0 or t < self.charge_until:
            return
        if self.cfg["fix"]["stagger"] and self.stagger > 0:
            return
        n = len(self.abilities)
        start = self.rng.randrange(n)
        for k in range(n):
            i = (start + k) % n
            if cd[i] > 0:
                continue
            a = self.abilities[i]
            if self.usable(a, t):
                self.begin_ability(i, a, t)
                return
            if a["kind"] in ("SUMMON", "DECOY", "HEAL"):
                cd[i] = 40

    def ability_blows(self, a, land0, t):
        """The blows an ability will deal, as the player can read them from its tell."""
        k = a["kind"]
        bolt = self.bolt_of(a)
        bk = self.W["bolts"].get(bolt, {"force": None, "effect": None, "ticks": 0})
        force = bk["force"]
        told = a["told"]
        fixed = self.cfg["fix"]["landed_class"]
        fx = a["fx"] or ((bk["effect"], bk["ticks"], 0) if bk["effect"] else None)
        fire = self.MC["fire_bolt_s"] if bolt == "FIRE" else 0  # Abilities.java:77
        name = a["name"]
        out = []
        if k in ("BOLT", "ARROWS", "NOVA"):
            # an AwBolt lands outside Strikes.deliver: a Force bolt's damage type is neither a projectile nor its
            # attacker's own blow, so Strikes.of calls it NONE (Strikes.java:64-75, Abilities.java:64-68)
            landed = told if fixed else (NONE if force else LIGHT)
            expose = min(1.0, a["count"] / 12.0) if k == "NOVA" else 1.0
            out.append((3, Blow(name, told, landed, land0 + 3, factor=a["power"] * (0.42 if k == "ARROWS" else 1.0),
                                expose=expose, pos=POSITIONAL["BOLT"], force=force, fx=fx, fire_s=fire)))
        elif k in ("FANGS", "FANGS_RING"):
            # the hazard top-up lands first (LIGHT: the caster's own blow), the vanilla fang's flat 6 after it as
            # indirect magic: NONE, through armour (Abilities.java:248-250, vanilla EvokerFangs: bite 8 ticks after warm-up)
            d0 = 0 if k == "FANGS" else self.rng.choice((0, 3))
            if self.MC["fang_scaled"]:
                out.append((d0 + 9, Blow(name, told, told, land0 + d0 + 9, factor=a["power"], expose=EXPOSE[k],
                                         pos=POSITIONAL[k], force=force, fx=fx)))
            else:
                flat = self.MC["fang_flat"]
                out.append((d0 + 3, Blow(name + " (top-up)", told, told if fixed else LIGHT, land0 + d0 + 3, factor=a["power"],
                                         flat=-flat, expose=EXPOSE[k], pos=POSITIONAL[k], posture_ok=False, force=force, fx=fx)))
                out.append((d0 + 9, Blow(name, told, told if fixed else NONE, land0 + d0 + 9, factor=0.0, flat=flat,
                                         expose=EXPOSE[k], pos=POSITIONAL[k], armor=False, fx=None)))
        elif k == "CHARGE":
            d = 2 if self.in_reach(t) else 5
            out.append((d, Blow(name, told, told, land0 + d, factor=a["power"], expose=EXPOSE[k], pos=POSITIONAL[k],
                                posture_ok=True, force=force, fx=fx, fire_s=fire)))
        elif k in ("SLAM", "BURST", "RAIN", "BEAM", "BLINK", "CONE", "PULL", "GUST", "ROOT", "LIFT"):
            out.append((0, Blow(name, told, told, land0, factor=a["power"] * FACTOR.get(k, 1.0), expose=EXPOSE[k],
                                pos=POSITIONAL.get(k, 0.1), posture_ok=k in ("SLAM", "BLINK", "CONE"), force=force,
                                fx=fx, fire_s=fire, knock=KNOCK.get(k, 0), front=0.6 if k == "BLINK" else 1.0)))
        for _, b in out:
            b.is_ability = True
        return out

    def begin_ability(self, i, a, t):
        self.active, self.active_i = a, i
        self.left = a["windup"] + (self.cfg["lord"]["heavy_windup"] if a["heavy"] else 0)
        land0 = t + self.left
        self.active_blows = []
        self.active_delays = []
        for d, b in self.ability_blows(a, land0, t):
            self.active_blows.append(self.new_blow(b))
            self.active_delays.append(d)
        if a["roots"]:
            self.stop_melee(t)

    def interrupt(self, t):
        if self.k_move is not None:
            # a posture break ends the move and its string (FightRunner.interrupt)
            self.cancel(self.k_blows + self.k_ab_blows, t)
            self.k_move, self.k_ab, self.k_blows, self.k_ab_blows = None, None, [], []
            self.k_string = []
            self.k_gap = 6 + self.rng.randrange(9)
        if self.active is not None:
            self.cancel(self.active_blows, t)
        self.active = None
        self.active_blows = []
        self.charge_until = -1

    def execute(self, a, t):
        k = a["kind"]
        blows, delays = self.active_blows, self.active_delays
        self.active_blows = []
        for b, d in zip(blows, delays):
            if b.alive:
                self.push(b, t + d)
        LCf = self.cfg["fix"]["landed_class"]
        if k == "CHARGE":
            self.charge_until = t + 14  # AbilityRunner.java:253
            if not self.flies:
                self.forced_out = max(self.forced_out, t + 25)  # ASSUMED: it ends a dash away; 25 ticks to meet again
        elif k == "BLINK":
            self.forced_in = max(self.forced_in, t + 20)
        elif k == "PULL":
            self.forced_in = max(self.forced_in, t + 10)
        elif k == "AURA":
            # pulses every 20 ticks (AwakenMob.java:521-538), outside Strikes.deliver: they land as LIGHT
            bolt = self.bolt_of(a)
            bk = self.W["bolts"].get(bolt, {"force": None, "effect": None, "ticks": 0})
            fx = a["fx"] or ((bk["effect"], bk["ticks"], 0) if bk["effect"] else None)
            for p in range(max(2, a["count"])):
                b = Blow(a["name"] + " pulse", a["told"], a["told"] if LCf else LIGHT, t + 20 * (p + 1), factor=a["power"],
                         expose=1.0, pos=0.1, force=bk["force"], fx=fx, fire_s=self.MC["fire_bolt_s"] if bolt == "FIRE" else 0)
                b.is_ability = True
                self.new_blow(b)
                self.push(b, t + 20 * (p + 1))
        elif k == "CLOUD":
            # a cloud pulsing every 10 ticks for 100 (Hazards.java:52-55, AbilityRunner.java:267): 0.2 x the hit
            first = 10 - (t % 10)
            for p in range(10):
                b = Blow(a["name"] + " pulse", a["told"], a["told"] if LCf else LIGHT, t + first + 10 * p,
                         factor=0.2 * a["power"], expose=1.0 if p == 0 else (0.6 if self.profile == "tank" else 0.15),
                         pos=POSITIONAL["CLOUD"])
                b.is_ability = True
                if p == 0:
                    self.new_blow(b)
                self.push(b, t + first + 10 * p)
        elif k == "SUMMON":
            self.spawn_minions(a, t)
        elif k == "DECOY":
            self.decoys += [t + 200] * a["count"]
        elif k == "GUARD":
            self.guard = 60  # AbilityRunner.java:277
        elif k == "HEAL":
            self.l_hp = min(self.l_max, self.l_hp + self.l_max * a["power"])
            self.st["heals"] += 1
        elif k == "FRENZY":
            self.frenzy = 200

    # ---- the Creature Kit's fight runner (FightRunner.java, SPEC §34.3) -------------------------------------------------

    def kit_speed(self):
        en = self.kit["fight"].get("enrage", {})
        return en.get("speed", 1.0) if self.l_hp < self.l_max * en.get("below", 0.0) else 1.0

    def kit_where(self, t):
        """Where the player stands (ASSUMED, see CONFIG kit): drawn again whenever reach changes."""
        near = self.in_reach(t)
        if self.k_d_state != near:
            K = self.cfg["kit"]
            if self.flies:
                lo, hi = K["flier_near"] if near else K["flier_far"]
            else:
                lo, hi = K["near"] if near else K["far"]
            self.k_d = lo + (hi - lo) * self.rng.random()
            self.k_behind = near and self.rng.random() < K["behind"]
            self.k_d_state = near
        return self.k_d, self.k_behind

    def kit_usable(self, e, d, behind, t):
        m = self.kit["moves"].get(e["move"])
        if m is None or not (m["range"][0] <= d <= m["range"][1]):
            return False
        if m.get("ability"):
            a = self.kit_ability(m["ability"])
            if a is not None and a["kind"] in ("SUMMON", "DECOY", "HEAL") and not self.usable(a, t):
                return False  # AbilityRunner.room
        w = e.get("when", "ANY")
        return {"ANY": True, "NEAR": d <= 4, "FAR": d >= 8, "BEHIND": behind, "AIR": False}.get(w, True)

    def kit_ability(self, name):
        for ph in self.ld["phases"]:
            for a in ph["abilities"]:
                if a["name"] == name:
                    return a
        return None

    def kit_cadence(self, t):
        c = self.st["cad"]
        m = self.k_move
        if m is not None:
            c["run"] = 0
            if m["class"] == NONE:
                c["show"] += 1
            elif self.k_t < self.k_w + self.k_act:
                c["threat"] += 1
            else:
                c["recover"] += 1
        elif self.stagger > 0 or t < self.broken_until:
            c["opened"] += 1
            c["run"] = 0
        else:
            c["idle"] += 1
            c["run"] += 1
            c["run_max"] = max(c["run_max"], c["run"])

    def kit_phrase_end(self):
        c = self.st["cad"]
        if c["phrase"] > 0:
            c["phrases"][c["phrase"]] = c["phrases"].get(c["phrase"], 0) + 1
        c["phrase"] = 0

    def kit_tick(self, t):
        self.kit_cadence(t)
        if self.k_move is not None:
            self.kit_step(t)
            return
        self.kit_phase(t)
        F = self.kit["fight"]
        if not self.k_intro:
            self.k_intro = True
            intro = F.get("intro", {}).get("move")
            if intro and self.kit_begin(intro, t):
                return
        if self.k_pending >= 0:
            p, self.k_pending = self.k_pending, -1
            tr = F["phases"][p].get("transition")
            self.k_string = []
            if tr and self.kit_begin(tr, t):
                self.shield = self.k_w + self.k_act + self.k_rec  # invulnerable while its transition plays
                return
        if self.stagger > 0 or t < self.broken_until:
            self.k_string = []
            return
        d, behind = self.kit_where(t)
        if self.k_string:
            nxt = self.k_string.pop(0)
            m = self.kit["moves"].get(nxt)
            if m is not None and m["range"][0] <= d <= m["range"][1] and self.kit_begin(nxt, t):
                self.st["cad"]["phrase"] += 1
                return
            self.k_string = []
        if self.k_gap > 0:
            self.k_gap -= 1
            return
        pool = [e for e in F["phases"][min(self.phase, len(F["phases"]) - 1)]["pool"] if self.kit_usable(e, d, behind, t)]
        if not pool:
            # it walks in to where its close moves reach and looks again (FightRunner.approach, every 10 ticks)
            if self.in_reach(t):
                self.k_d = min(self.k_d, self.kit_stop())
            self.k_gap = 10
            return
        r = self.rng.randrange(sum(max(1, e.get("weight", 1)) for e in pool))
        for e in pool:
            r -= max(1, e.get("weight", 1))
            if r < 0:
                break
        self.kit_phrase_end()
        if self.kit_begin(e["move"], t) and self.kit["moves"][e["move"]]["class"] != NONE:
            self.st["cad"]["phrase"] = 1
        if e.get("then"):
            self.k_string = list(self.rng.choice(e["then"]))

    def kit_stop(self):
        F = self.kit["fight"]
        reach = 0.0
        for e in F["phases"][min(self.phase, len(F["phases"]) - 1)]["pool"]:
            m = self.kit["moves"].get(e["move"])
            if m is None or m["range"][0] > 0 or e.get("when", "ANY") in ("FAR", "BEHIND"):
                continue
            reach = max(reach, m["range"][1])
        return min(3.5, max(0.5, reach - 0.3)) if reach > 0 else 3.5

    def kit_phase(self, t):
        """AwakenBoss.checkPhase between moves: a crossed line queues that phase's transition (FightRunner.enterPhase)."""
        frac = self.l_hp / self.l_max
        want = 0
        for i in range(1, len(self.ld["phases"])):
            if frac < self.ld["phases"][i]["below"]:
                want = i
        if want <= self.phase:
            return
        self.phase = want
        self.abilities = self.ld["phases"][want]["abilities"]
        self.k_pending = want

    def kit_begin(self, mid, t):
        m = self.kit["moves"].get(mid)
        if m is None:
            return False
        cls, ti = m["class"], m["time"]
        hold = ti.get("hold", 0) if cls == HEAVY and ti.get("hold", 0) > 0 and self.rng.randrange(3) == 0 else 0
        sp = max(1.0, self.kit_speed())
        least = 1 if cls == NONE else {LIGHT: 8, HEAVY: 12, GRAB: 12, PERILOUS: 14}.get(cls, 6)  # KitMoves.minWindup
        w = min(ti["windup"], max(least, int(math.floor(ti["windup"] / sp + 0.5))))
        self.k_move, self.k_t = m, 0
        self.k_w, self.k_act, self.k_rec = w + hold, max(1, ti["active"]), max(1, int(math.floor(ti["recover"] / sp + 0.5)))
        # where a string's next blow links on (KitMoves.played, SPEC §35.10): its link, scaled with the recovery
        ln = ti.get("link", 0)
        self.k_link = self.k_rec if ln <= 0 else min(self.k_rec, max(1, int(math.floor(ln * self.k_rec / max(1, ti["recover"]) + 0.5))))
        self.st["kit_moves"][mid] = self.st["kit_moves"].get(mid, 0) + 1
        if cls != NONE:
            self.st["cad"]["blows"] += 1
        land0 = t + self.k_w + 1  # the first active tick
        K = self.cfg["kit"]
        self.k_blows = []
        first = next((k for k, caps in enumerate(m.get("hits", [])) if caps), -1)
        if first >= 0 and m.get("damage", 0) > 0:
            # its capsules: one blow a move (each target is hit once), told and landed as its class, landing only if
            # you stand where they reach then (a gap-closer travels to you: it lands); ASSUMED 8 ticks out of reach per
            # block of knock
            kind = cls if cls != NONE else HEAVY
            b = Blow(m.get("title") or mid, kind, kind, land0 + first, factor=m["damage"] * K["damage"], posture_ok=True,
                     fire_s=self.MC["ignite_s"] if self.ignite else 0, knock=int(8 * m.get("knock", 0)))
            if "gap" not in m.get("kinds", []):
                b.kit = m["_reach"]
            self.k_blows.append(self.new_blow(b))
        # its ability, aimed now and fired as the move strikes, under the move's own class (AbilityRunner.fire)
        self.k_ab, self.k_ab_blows, self.k_ab_delays = None, [], []
        a = self.kit_ability(m["ability"]) if m.get("ability") else None
        if a is not None:
            if cls != NONE:
                a = dict(a, told=cls)
            self.k_ab = a
            for dd, b in self.ability_blows(a, land0, t):
                self.k_ab_blows.append(self.new_blow(b))
                self.k_ab_delays.append(dd)
        return True

    def kit_step(self, t):
        self.k_t += 1
        if self.k_t <= self.k_w:
            return
        k = self.k_t - self.k_w - 1
        m = self.k_move
        if k < self.k_act:
            if k == 0:
                for b in self.k_blows:
                    if b.alive:
                        first = next(i for i, caps in enumerate(m["hits"]) if caps)
                        self.push(b, t + first)
                if self.k_ab is not None:
                    self.active_blows, self.active_delays = self.k_ab_blows, self.k_ab_delays
                    self.execute(self.k_ab, t)
                if "gap" in m.get("kinds", []):
                    self.forced_in = max(self.forced_in, t + self.k_act + 10)  # it arrives beside you
            return
        if self.k_string and self.k_link < self.k_rec and self.k_t == self.k_w + self.k_act + self.k_link:
            # inside a string the next blow starts from this one's follow-through (FightRunner.link), if it reaches
            nm = self.kit["moves"].get(self.k_string[0])
            d, _ = self.kit_where(t)
            if nm is not None and nm["range"][0] <= d <= nm["range"][1] and self.k_pending < 0:
                self.k_move, self.k_ab, self.k_blows, self.k_ab_blows = None, None, [], []
                self.k_gap = 0
                self.k_string.pop(0)
                if self.kit_begin(nm["id"], t):
                    self.st["cad"]["phrase"] += 1
                    self.st["cad"]["links"] = self.st["cad"].get("links", 0) + 1
                return
        if self.k_t < self.k_w + self.k_act + self.k_rec:
            return
        self.k_move, self.k_ab, self.k_blows, self.k_ab_blows = None, None, [], []
        self.k_gap = 0 if self.k_string else 6 + self.rng.randrange(9)

    def stop_melee(self, t):
        if self.melee_running:
            self.melee_running = False
            if self.tell_left > 0:
                self.cancel([self.tell_blow], t)
            self.tell_left = 0
            self.tell_blow = None

    def melee_tick(self, t):
        """MobGoals.ToldMelee (MobGoals.java:96-141) under the Hold goal (MobGoals.java:25-48)."""
        if self.held(t):
            self.stop_melee(t)
            return
        if not self.melee_running:
            self.melee_running = True
            self.melee_cd = 0  # vanilla MeleeAttackGoal.start
        if self.tell_left > 0:
            self.tell_left -= 1
            if self.tell_left > 0:
                return
            self.melee_cd = self.cfg["lord"]["melee_cooldown"]
            b = self.tell_blow
            self.tell_blow = None
            if b is not None and b.alive:
                self.push(b, t)
            return
        self.melee_cd = max(0, self.melee_cd - 1)
        if self.melee_cd == 0 and self.active is None and self.in_reach(t):
            M = self.cfg["melee"]
            self.swings += 1
            heavy = self.swings % M["heavy_every"] == 0
            kind = HEAVY if heavy else LIGHT
            self.tell_left = M["tell_boss"] + (M["heavy_extra"] if heavy else 0)
            b = Blow("Told melee" + (" (heavy)" if heavy else ""), kind, kind, t + self.tell_left,
                     factor=M["heavy_mult"] if heavy else 1.0, posture_ok=True, melee=True, fire_s=self.MC["ignite_s"] if self.ignite else 0,
                     force=None)
            b.heavy_melee = heavy
            self.tell_blow = self.new_blow(b)

    def dive_tick(self, t):
        """MobGoals.FlyHunt's told dive (MobGoals.java:203-235)."""
        if self.held(t):
            if self.dive_tell >= 0:
                self.cancel([self.dive_blow], t)
                self.dive_blow = None
                self.dive_tell = -1
            return
        if self.dive_cd > 0:
            self.dive_cd -= 1
        if self.dive_cd > 0 or self.stagger > 0:
            return
        if self.dive_tell < 0:
            self.dive_tell = self.cfg["melee"]["tell_boss"]
            self.dive_left = 4 + self.rng.randrange(5)  # ASSUMED: 4-8 ticks to close from the orbit
            b = Blow("Dive", LIGHT, LIGHT, t + self.dive_tell + self.dive_left, posture_ok=True, melee=False,
                     fire_s=self.MC["ignite_s"] if self.ignite else 0)
            self.dive_blow = self.new_blow(b)
            return
        if self.dive_tell > 0:
            self.dive_tell -= 1
            return
        self.dive_left -= 1
        if self.dive_left > 0:
            return
        b = self.dive_blow
        self.dive_blow = None
        if b is not None and b.alive:
            self.push(b, t)
        self.forced_in = max(self.forced_in, t + 8)  # ASSUMED: after a dive it is beside you for a moment
        self.dive_cd = self.cfg["lord"]["dive_cd"] + self.rng.randrange(self.cfg["lord"]["dive_cd_rand"])
        self.dive_tell = -1

    # ---- minions ------------------------------------------------------------------------------------------------------

    def spawn_minions(self, a, t):
        d = self.W["mobs"].get(a["summon"])
        if d is None:
            return
        model = self.W["models"][d["model"]]
        traits = set(d["traits"])
        LC = self.cfg["lord"]
        hp = d["hp"] * (1 + LC["health_per_level"] * self.lv)  # Mobs.java:150-152: normal role at the Lord's level
        dmg = d["dmg"] * (1 + LC["damage_per_level"] * self.lv)
        if "NEUTRAL" in traits:
            kind = "neutral"
        elif "RANGED" in traits:
            kind = "ranged"
        elif model["move"] == "FLY":
            kind = "flier"
        else:
            kind = "ground"
        ab = d["phases"][0]["abilities"][0] if d["phases"][0]["abilities"] else None
        if kind == "ranged" and ab is not None:
            bolt = ab["bolt"] or self.W["themes"][d["theme"]]["bolt"]
            force = self.W["bolts"].get(bolt, {}).get("force")
            interval = ab["windup"] + ab["cooldown"] + 10
            tell = ab["windup"]
            power = ab["power"]
        else:
            bolt, force, power = None, None, 1.0
            interval = 32 if kind == "ground" else 52  # ASSUMED from ToldMelee (8 + 20) and FlyHunt (8 + dive + 30..49)
            tell = self.cfg["melee"]["tell_normal"]
        if "WATCHED_FREEZE" in traits:
            interval *= 2
        for _ in range(a["count"]):
            self.minions.append({"name": d["name"], "kind": kind, "hp": hp, "max": hp, "dmg": dmg * power * self.MC["minion_damage"], "tell": tell,
                                 "interval": interval, "next": t + 10 + self.rng.randrange(20), "alive": True,
                                 "force": force, "bolt": bolt, "armor": d["armor"] + LC["armor_per_level"] * self.lv,
                                 "haunted": "HAUNTED" in traits, "poison": "POISON_TOUCH" in traits,
                                 "ignite": "IGNITE" in traits})
            self.st["minions_spawned"] += 1

    def minions_tick(self, t):
        for m in self.minions:
            if not m["alive"] or m["kind"] == "neutral" or t < m["next"]:
                continue
            m["next"] = t + m["interval"]
            if m["kind"] == "ranged":
                landed = (NONE if m["force"] else LIGHT) if not self.cfg["fix"]["landed_class"] else LIGHT
                b = Blow(m["name"] + " bolt", LIGHT, landed, t + m["tell"] + 3, pos=POSITIONAL["MINION_BOLT"],
                         force=m["force"], src="minion",
                         fx=(lambda bk: (bk["effect"], bk["ticks"], 0) if bk.get("effect") else None)(self.W["bolts"].get(m["bolt"], {})),
                         fire_s=self.MC["fire_bolt_s"] if m["bolt"] == "FIRE" else 0)
            else:
                b = Blow(m["name"], LIGHT, LIGHT, t + m["tell"], expose=0.85 if m["kind"] == "ground" else 0.9,
                         src="minion", melee=True, fx=("POISON", 60, 0) if m["poison"] else None,
                         fire_s=self.MC["ignite_s"] if m["ignite"] else 0)
            b.minion = m
            self.new_blow(b)
            self.push(b, b.est)

    # ---- blows on the player ----------------------------------------------------------------------------------------

    def resolve(self, t):
        heap = self.heap
        while heap and heap[0][0] <= t:
            _, _, b = heapq.heappop(heap)
            if not b.alive:
                continue
            b.alive = False
            if b.plan == "guard":
                self.guard_lower_t = t + 1
            if b.lord and self.l_hp <= 0:
                continue
            if b.minion is not None and not b.minion["alive"]:
                continue
            if b.melee and b.lord and not self.in_reach(t):
                continue  # stepped out: a whiff (MobGoals.java:131)
            if b.kit is not None:
                d, behind = self.kit_where(t)
                if d > (b.kit[1] if behind else b.kit[0]):
                    continue  # not where its capsules are (FightRunner.strike)
            if self.rng.random() >= b.expose:
                continue
            self.land_blow(b, t)
            if self.hp <= 0:
                return

    def land_blow(self, b, t):
        rng = self.rng
        amount = b.factor * (self.lord_attack() if b.lord else b.minion["dmg"] if b.minion else 0.0)
        if b.flat > 0:
            amount = b.flat
        elif b.flat < 0:
            amount = max(0.0, amount + b.flat)  # the fang's top-up: what the hit is worth over the vanilla 6
            if amount <= 0:
                return
        effects_fixed = self.cfg["fix"]["effects"]
        since = t - self.dodge_at
        evade, perfect = int(self.dodge_d["evade"]), int(self.dodge_d["perfect"])
        blocked = 0.0
        outcome = "hit"
        if 0 <= since < evade:
            if b.landed != NONE:
                outcome = "evade"
            elif b.plan == "dodge" and rng.random() < b.pos:
                outcome = "dodged"  # carried out of the line or the mark
        if outcome == "hit" and self.guard_up and b.landed in (LIGHT, HEAVY) and rng.random() < b.front:
            rel = t - self.raised_at
            if not self.locked_out and 0 <= rel < self.deflect_ticks:
                outcome = "deflect"
            elif b.landed == LIGHT:
                outcome = "block"
                blocked = self.block_red
            else:
                outcome = "guard_break"
                blocked = self.heavy_red
        if outcome == "dodged":
            return
        # an ability's burn and touch land whether or not the hit did (Abilities.java:75-80); a melee's only on a hit
        apply_fx = outcome in ("hit", "block", "guard_break") or (b.is_ability and not effects_fixed)
        if outcome == "evade":
            self.st["evades"] += 1
            if since < perfect and (b.lord or b.minion is not None):
                self.st["perfect"] += 1
                self.gain(self.R["RESONANCE_PERFECT_DODGE"])
                if b.lord:
                    self.slowed_until = t + int(self.dodge_d["slow"])
        elif outcome == "deflect":
            self.st["deflects"] += 1
            self.raise_deflected = True
            self.deflect_at = t
            self.gain(self.R["RESONANCE_DEFLECT"])
            if b.lord and b.posture_ok:
                self.add_posture(self.R["DEFLECT_POSTURE_HEAVY"] if b.landed == HEAVY else self.R["DEFLECT_POSTURE_LIGHT"], t)
        if apply_fx:
            self.apply_effects(b, t)
        if outcome in ("evade", "deflect"):
            return
        if outcome == "block":
            self.st["blocks"] += 1
        elif outcome == "guard_break":
            self.st["breaks_guard"] += 1
            self.guard_up = False
            self.lowered_at = t
            self.guard_cd_until = t + self.guard_break
        dealt = self.hurt(amount, t, blocked, armor=b.armor, force=b.force, name=b.name)
        if dealt > 0 and b.knock:
            self.forced_out = max(self.forced_out, t + b.knock)

    def apply_effects(self, b, t):
        if b.fire_s:
            if t >= self.burn_until:
                self.burn_next = t + 20
            self.burn_until = max(self.burn_until, t + b.fire_s * 20)
        fx = b.fx
        if fx is None:
            return
        name, ticks, amp = fx
        if name == "POISON":
            ticks = int(ticks * self.MC["poison_scale"])
            iv = max(1, 25 >> amp)
            if t >= self.poison_until or iv < self.poison_iv:
                self.poison_next = t + iv
            self.poison_iv = iv
            self.poison_until = max(self.poison_until, t + ticks)
        elif name == "WEAKNESS":
            self.weak_until = max(self.weak_until, t + ticks)

    def hurt(self, amount, t, blocked=0.0, armor=True, force=None, name="", dot=False):
        """The damage pipeline on the player: Veil Flicker, VIT, set wards, the block, i-frames, armour."""
        P = self.P
        if P["flicker"] and self.hp < self.max_hp * 0.4 and t >= self.flicker_ready and not dot and self.rng.random() < 0.10:
            self.flicker_ready = t + 160
            return 0.0
        a = amount * max(0.2, 1 - P["resist"])
        if force:
            mul = 1 - P["ward_force"]
            if force in ("GRAVITY", "CHARGE"):
                mul *= 1 - P["ward_lord"]
            if force == "SPIRIT":
                mul *= 1 - P["ward_spirit"]
            a *= mul
        a *= (1 - blocked)
        if t - self.last_hurt_t < 10:  # vanilla i-frames: within 10 ticks only what exceeds the last hit lands
            if a <= self.last_hurt:
                return 0.0
            dealt = a - self.last_hurt
            self.last_hurt = a
        else:
            dealt = a
            self.last_hurt = a
            self.last_hurt_t = t
        if armor:
            dealt = armor_after(dealt, P["armor"], P["toughness"])
        self.hp -= dealt
        self.exh += 0.1
        tb = self.st["taken_by"]
        tb[name] = tb.get(name, 0.0) + dealt
        if self.hp <= 0 and self.st["death_by"] is None:
            self.st["death_by"] = name
        return dealt

    def gain(self, v):
        self.resonance = min(self.R["RESONANCE_MAX"], self.resonance + v)

    # ---- posture (ArmsServer.addPosture, ArmsServer.java:619-645; Posture.java) -----------------------------------

    def add_posture(self, amount, t):
        if amount <= 0 or self.l_hp <= 0:
            return
        R = self.R
        if t - self.p_last > R["POSTURE_IDLE_TICKS"]:
            idle = t - self.p_last - R["POSTURE_IDLE_TICKS"]
            self.pval = max(0.0, self.pval - idle * R["POSTURE_REGEN_LORD"])
            self.p_last = t - R["POSTURE_IDLE_TICKS"]
        if t < self.broken_until:
            return
        self.pval = min(self.pmax, self.pval + amount)
        self.p_last = t
        if self.pval >= self.pmax and t >= self.next_break:
            ticks = int(R["BROKEN_TICKS_LORD"])
            self.broken_until = t + ticks
            self.next_break = t + int(R["BREAK_COOLDOWN"])
            self.pval = 0.0
            self.st["breaks"] += 1
            # Strikes.stagger -> AwakenMob.stagger (AwakenMob.java:313-320): the wind-up ends, the body stands still
            self.interrupt(t)
            self.stagger = max(self.stagger, ticks)
            self.stop_melee(t)
            if self.dive_tell >= 0:
                self.cancel([self.dive_blow], t)
                self.dive_blow = None
                self.dive_tell = -1
            self.gain(R["RESONANCE_BREAK"])

    # ---- the player's offence ---------------------------------------------------------------------------------------

    def attack_value(self, t):
        P = self.P
        base = P["attack"] - (4.0 if t < self.weak_until else 0.0)  # vanilla Weakness: -4 attack damage
        bonus = P["str"] + (P["cornered"] if self.hp < self.max_hp * 0.5 else 0.0)
        v = max(0.0, base * (1 + bonus))
        if P["item_level"]:
            v *= 1 + P["item_level"]
        return v

    def begin(self, name, step, cadence, t):
        w, act, rec, cad, motion, posture, cap, fin, arc, _ = self.moves[name]
        mv = Move()
        mv.name, mv.start, mv.step, mv.cadence = name, t, step, cadence
        mv.hit_t, mv.active_end, mv.end, mv.total = t + w, t + w + act, t + w + act + rec, w + act + rec
        mv.motion = motion * (1 + self.R["CADENCE_DAMAGE"] * cadence)  # ArmsRules.java:83-86
        mv.posture = posture + cadence                                  # ArmsRules.java:88-91
        mv.cap, mv.finisher, mv.arc, mv.done = cap, fin, arc, False
        if cadence == self.R["CADENCE_MAX"] and fin:
            self.gain(self.R["RESONANCE_CADENCE3"])
        if 0 <= step < len(self.chain) - 1 and cad:
            r = self.rng
            if r.random() < self.prof["cadence"]:
                mv.press_kind, mv.next_press = "good", t + cad[0] + r.randrange(cad[1] - cad[0] + 1)
            elif r.random() < 0.5:
                mv.press_kind, mv.next_press = "early", t + cad[0]  # a mash: buffered to the window, no step
            else:
                mv.press_kind, mv.next_press = "late", t + cad[1] + 1 + r.randrange(3)
        else:
            mv.press_kind, mv.next_press = "end", mv.end  # a press just before the end is buffered to it
        self.mv = mv
        self.exh += 0.1
        if name in ("strike_1", "strike_2", "strike_3", "strike_4"):
            self.st["strikes"] += 1
        return mv

    def tap(self, t):
        """ArmsRules.tap: which strike a press starts now (ArmsRules.java:54-72)."""
        mv = self.mv
        if mv is None or t >= mv.start + mv.total + self.late:
            return "strike_1", 0, 0
        chain_strike = mv.step >= 0
        nxt = mv.step + 1 if chain_strike and mv.step + 1 < len(self.chain) else 0
        cad = self.moves[mv.name][3]
        if chain_strike and nxt != 0 and cad:
            if mv.press_kind == "good" and cad[0] <= t - mv.start <= cad[1]:
                return self.chain[nxt], nxt, min(int(self.R["CADENCE_MAX"]), mv.cadence + 1)
            return self.chain[nxt], nxt, 0
        return "strike_1", 0, 0

    def player_hit(self, mv, t):
        """One Arms hit on the Lord: vanilla's attack at full strength, then the move (ArmsServer.java:326-357,359-372)."""
        mv.done = True
        rng = self.rng
        name = mv.name
        art = name == "art"
        if art:
            ok = rng.random() < (0.4 if self.ld["id"] == "mother_of_spores" else 0.6 if self.flies else 0.9)
        else:
            ok = self.in_reach(t)
        if not ok:
            self.st["whiffs"] += 1
            return
        self.cleave(mv, t)
        if self.shield > 0:
            return  # the roar of a new phase: invulnerable (AwakenBoss.java:136-141)
        if self.haunted and ((t + self.phase_offset) % 140) >= 110:
            return  # phased (AwakenMob.java:492-494): no hit, no posture
        if self.decoys and rng.random() < min(0.6, 0.2 * len(self.decoys)):
            self.decoys.pop()
            return  # the blow took a reflection
        dmg = self.attack_value(t)
        if rng.random() < self.P["crit"]:
            dmg *= 1.6
        if self.haunted:
            dmg *= 0.75  # AwakenMob.java:565
        counter = False
        if self.guard > 0:
            dmg *= 0.2  # Perfect Parry (AwakenMob.java:566-574)
            self.guard = 0
            counter = True
        dmg *= mv.motion
        if mv.cap > 0:
            dmg = min(dmg, self.l_max * mv.cap)  # ArmsRules.capExecution
        dmg = armor_after(dmg, self.l_armor, 0.0)
        self.l_hp -= dmg
        dm = self.st["dmg_by_move"]
        dm[name] = dm.get(name, 0.0) + dmg
        self.add_posture(mv.posture, t)
        if mv.finisher:
            self.gain(self.R["RESONANCE_FINISHER"])
        if counter:
            b = Blow("Perfect Parry riposte", NONE, LIGHT, t + 1, posture_ok=False, melee=False)
            self.push(b, t + 1)  # untold: doHurtTarget outside Strikes.deliver

    def cleave(self, mv, t):
        if not self.minions:
            return
        wide = mv.arc >= 100 or mv.name == "art"
        for m in self.minions:
            if not m["alive"] or m["kind"] == "neutral":
                continue
            p = {"ground": self.MC["cleave_ground"], "flier": self.MC["cleave_flier"], "ranged": 0.0}[m["kind"]] * (1.0 if wide else 0.5)
            if mv.name == "art" and m["kind"] != "ranged":
                p = 0.8
            if self.rng.random() < p:
                d = self.attack_value(t) * mv.motion * (0.75 if m["haunted"] else 1.0)
                m["hp"] -= armor_after(d, m["armor"], 0.0)
                if m["hp"] <= 0:
                    m["alive"] = False

    def player_defence(self, t):
        # guard lowering: after the blow it was raised for, unless another guarded one lands in the next 3 ticks
        if self.guard_up and (t >= self.guard_lower_t >= 0 or
                              not any(b.alive and b.plan == "guard" and b.est >= t - 1 for b in self.plans)):
            soon = any(b.alive and b.plan == "guard" and t <= b.est <= t + 3 for b in self.plans)
            if not soon:
                self.guard_up = False
                self.lowered_at = t
                self.guard_lower_t = -1
        for b in self.plans:
            if not b.alive or b.plan_t != t:
                continue
            if b.plan == "guard":
                if self.guard_up:
                    continue
                if t < self.guard_cd_until:  # a broken guard can't rise yet: raise it the moment it can
                    if self.guard_cd_until <= b.est:
                        b.plan_t = self.guard_cd_until
                    continue
                self.hold = None
                # a raise right after one that deflected nothing can only block (ArmsServer.java:528)
                self.locked_out = (not self.raise_deflected) and t - self.lowered_at <= self.lockout
                self.raised_at = t
                self.raise_deflected = False
                self.guard_up = True
                self.guard_lower_t = -1
            elif b.plan == "dodge":
                if not self.try_dodge(t) and t + 1 <= b.est:
                    b.plan_t = t + 1

    def try_dodge(self, t):
        """ArmsServer.onDodge (ArmsServer.java:539-576)."""
        d = self.dodge_d
        if self.charges <= 0 or t - self.dodge_at < int(d["ticks"]):
            return False
        mv = self.mv
        if mv is not None and t < mv.active_end:
            return False  # a wind-up or a blow can't be dodged out of
        if self.charges == int(d["charges"]):
            self.recharge_at = t + int(d["recharge"])
        self.charges -= 1
        self.dodge_at = t
        if self.guard_up:  # the dodge drops the guard (ArmsPredict.java:329)
            self.guard_up = False
            self.lowered_at = t
        self.mv = None
        self.hold = None
        self.exh += 0.1
        if self.in_reach(t) and self.rng.random() >= self.prof["keep"]:
            self.forced_out = max(self.forced_out, t + 10)
        return True

    def player_offence(self, t):
        mv = self.mv
        if mv is not None and not mv.done and t >= mv.hit_t:
            self.player_hit(mv, t)
            if self.l_hp <= 0:
                return
        if t - self.dodge_at < int(self.dodge_d["ticks"]) or self.guard_up:
            return
        if self.hold is not None:
            if t >= self.hold_release:
                self.release(t)
            return
        if mv is not None and (t < mv.active_end or t < mv.next_press):
            return
        if self.detour is not None:
            if t >= self.detour_end:
                self.detour["alive"] = False
                self.detour = None
            return
        if self.prof["detour"] and t >= self.forced_out:
            for m in self.minions:
                if m["alive"] and m["kind"] == "ranged":
                    hits = max(1, math.ceil(m["hp"] / max(0.1, self.attack_value(t) * 1.1)))
                    self.detour = m
                    self.detour_end = t + 15 + 8 * hits  # ASSUMED: 15 ticks to reach it, a strike every 8 ticks
                    self.forced_out = self.detour_end
                    return
        reach = self.in_reach(t)
        if not reach:
            return
        dn = self.next_defence(t)
        if t - self.deflect_at <= self.riposte_window:
            self.try_begin("riposte", -1, 0, t, dn)
            if self.mv is not None and self.mv.name == "riposte" and self.mv.start == t:
                self.deflect_at = -10 ** 6
                self.st["ripostes"] += 1
            return
        if 1 <= t - self.dodge_at <= int(self.dodge_d["ticks"]) + self.slip_window:
            if self.try_begin("slipcut", -1, 0, t, dn):
                self.dodge_at = -10 ** 6  # ArmsServer.strike: the Slipcut spends the dodge
                self.st["slipcuts"] += 1
            return
        name, step, cad = self.tap(t)
        w, act = self.moves[name][0], self.moves[name][1]
        if t + w + act > dn:
            return
        if t < self.broken_until - (self.charge_min + 1) and dn > t + self.charge_min + 5:
            self.begin(name, step, cad, t)
            self.hold, self.hold_release = "execution", t + self.charge_min
            return
        if self.prof["art"] and self.resonance >= self.R["RESONANCE_MAX"] and dn > t + self.charge_max + 3 and t >= self.broken_until:
            self.begin(name, step, cad, t)
            self.hold, self.hold_release = "art", t + self.charge_max - 2  # ArmsRules.artReady: 2 ticks' grace
            return
        self.begin(name, step, cad, t)

    def try_begin(self, name, step, cad, t, dn):
        w, act = self.moves[name][0], self.moves[name][1]
        if t + w + act > dn:
            return False
        self.begin(name, step, cad, t)
        return True

    def release(self, t):
        kind = self.hold
        self.hold = None
        if kind == "execution" and t < self.broken_until and self.in_reach(t):
            self.begin("execution", -1, 0, t)
            self.st["executions"] += 1
        elif kind == "art" and self.resonance >= self.R["RESONANCE_MAX"]:
            self.resonance = 0.0
            self.begin("art", -1, 0, t)
            self.st["arts"] += 1
        else:
            self.begin("heavy", -1, 0, t)

    # ---- over time --------------------------------------------------------------------------------------------------

    def body_tick(self, t):
        # dodge charges (ArmsServer.java onPlayerTick)
        if self.charges < int(self.dodge_d["charges"]) and t >= self.recharge_at:
            self.charges += 1
            self.recharge_at = t + int(self.dodge_d["recharge"])
        if t < self.burn_until and t >= self.burn_next:
            self.burn_next = t + 20
            self.hurt(1.0, t, armor=False, name="Burning", dot=True)  # vanilla on_fire: 1 a second
        if t < self.poison_until and t >= self.poison_next:
            self.poison_next = t + self.poison_iv
            if self.hp > 1.0:
                self.hp -= min(1.0, self.hp - 1.0) * max(0.2, 1 - self.P["resist"])  # poison never kills
        # vanilla FoodData.tick (26.3): fast regen on saturation, slow at food >= 18
        while self.exh >= 4.0:
            self.exh -= 4.0
            if self.sat > 0:
                self.sat = max(0.0, self.sat - 1.0)
            else:
                self.food = max(0, self.food - 1)
        hurt = self.hp < self.max_hp
        if self.sat > 0 and hurt and self.food >= 20:
            self.food_timer += 1
            if self.food_timer >= 10:
                spent = min(self.sat, 6.0)
                self.hp = min(self.max_hp, self.hp + spent / 6.0)
                self.exh += spent
                self.food_timer = 0
        elif self.food >= 18 and hurt:
            self.food_timer += 1
            if self.food_timer >= 80:
                self.hp = min(self.max_hp, self.hp + 1.0)
                self.exh += 6.0
                self.food_timer = 0
        else:
            self.food_timer = 0

    def run(self):
        cap = int(self.cfg["fight_cap_s"] * 20)
        st = self.st
        for t in range(cap):
            self.tick_reach(t)
            self.lord_tick(t)
            if self.minions:
                self.minions_tick(t)
            self.player_defence(t)
            self.resolve(t)
            if self.hp <= 0:
                return self.result(False, t)
            self.player_offence(t)
            if self.l_hp <= 0:
                return self.result(True, t)
            self.body_tick(t)
            if self.hp <= 0:
                return self.result(False, t)
            if self.in_reach(t):
                st["in_reach"] += 1
            st["ticks"] += 1
            st["phase_ticks"][min(2, self.phase)] += 1
        return self.result(False, cap, timeout=True)

    def result(self, win, t, timeout=False):
        return {"win": win, "t": t / 20.0, "hp_left": max(0.0, self.hp) / self.max_hp, "lord_left": max(0.0, self.l_hp) / self.l_max,
                "timeout": timeout, "st": self.st}


# ---- scenarios, runs and targets ---------------------------------------------------------------------------------------

def lords_of(W, only=None):
    out = []
    for d in W["mobs"].values():
        if d["tier"] == "LORD" and (only is None or d["id"] == only):
            out.append(d)
    out.sort(key=lambda d: (RANK_ORDER.index(W["themes"][d["theme"]]["home"]), d["id"]))
    return out


def forged_for(W, rank):
    for w in W["weapons"].values():
        if w["tier"] == rank and w["style"] == "longsword":
            return w
    raise SystemExit("combat_sim: no forged longsword for rank " + rank)


_W_CACHE = {}


def with_ability_overrides(lord, cfg):
    """A Lord's def with the run's ability.<lord>.<phase>.<index>.<field> overrides applied (heavy re-derives the class)."""
    mine = {k: v for k, v in cfg["abilities"].items() if k.split(".")[0] == lord["id"]}
    if not mine:
        return lord
    lord = copy.deepcopy(lord)
    for k, v in mine.items():
        _, ph, idx, field = k.split(".")
        a = lord["phases"][int(ph)]["abilities"][int(idx)]
        if field not in a:
            raise SystemExit(f"combat_sim: ability {k!r}: no field {field!r}")
        a[field] = type(a[field])(v) if a[field] is not None and not isinstance(a[field], bool) else v
        if field == "heavy":
            a["told"] = PERILOUS if v else _strike_class(a["kind"])
    return lord


def _strike_class(kind):
    return parse_ability_meta()[2].get(kind, NONE)


def run_job(job):
    """One (Lord, profile, weapon) cell over its seeds. Runs in a worker process."""
    key = "W"
    if key not in _W_CACHE:
        _W_CACHE[key] = load_world()
    W = _W_CACHE[key]
    cfg = job["cfg"]
    lord = with_ability_overrides(W["mobs"][job["lord"]], cfg)
    rank = W["themes"][lord["theme"]]["home"]
    P = player_at(W, cfg, rank, job["weapon_damage"])
    res = []
    for i in range(job["seeds"]):
        rng = random.Random(f"{job['seed']}:{job['lord']}:{job['profile']}:{job['weapon']}:{i}")
        res.append(Fight(W, cfg, lord, job["profile"], P, rng, job["weapon"]).run())
    return job, summarise(res, P)


def summarise(res, P):
    wins = [r for r in res if r["win"]]
    n = len(res)
    out = {"n": n, "win": len(wins) / n if n else 0.0, "timeouts": sum(1 for r in res if r["timeout"]) / n if n else 0.0}
    out["ttk"] = float(np.median([r["t"] for r in wins])) if wins else None
    out["ttk_p25"] = float(np.percentile([r["t"] for r in wins], 25)) if wins else None
    out["ttk_p75"] = float(np.percentile([r["t"] for r in wins], 75)) if wins else None
    out["hp_left"] = float(np.median([r["hp_left"] for r in wins])) if wins else None
    out["death_t"] = float(np.median([r["t"] for r in res if not r["win"]])) if len(wins) < n else None
    out["lord_left_on_loss"] = float(np.median([r["lord_left"] for r in res if not r["win"]])) if len(wins) < n else None
    tick = sum(r["st"]["ticks"] for r in res) or 1
    out["uptime"] = sum(r["st"]["in_reach"] for r in res) / tick
    out["broken_share"] = sum(r["st"]["broken_ticks"] for r in res) / tick
    per_min = lambda k: sum(r["st"][k] for r in res) / (tick / 1200.0)
    for k in ("deflects", "blocks", "breaks_guard", "evades", "perfect", "breaks", "executions", "arts", "ripostes", "slipcuts", "strikes", "whiffs", "heals"):
        out[k + "_per_min"] = per_min(k)
    dm, tb, deaths = {}, {}, {}
    for r in res:
        for k, v in r["st"]["dmg_by_move"].items():
            dm[k] = dm.get(k, 0.0) + v
        for k, v in r["st"]["taken_by"].items():
            tb[k] = tb.get(k, 0.0) + v
        if r["st"]["death_by"]:
            deaths[r["st"]["death_by"]] = deaths.get(r["st"]["death_by"], 0) + 1
    tot = sum(dm.values()) or 1.0
    out["dmg_share"] = {k: v / tot for k, v in sorted(dm.items(), key=lambda kv: -kv[1])}
    tt = sum(tb.values()) or 1.0
    out["taken_share"] = {k: v / tt for k, v in sorted(tb.items(), key=lambda kv: -kv[1])[:6]}
    out["taken_per_min"] = tt / (tick / 1200.0)
    cads = [r["st"]["cad"] for r in res if r["st"]["cad"]["threat"] + r["st"]["cad"]["idle"] > 0]
    if cads:
        busy = sum(c["threat"] + c["recover"] + c["idle"] + c["show"] + c["opened"] for c in cads) or 1
        ph = {}
        for c in cads:
            for k, v in c["phrases"].items():
                ph[k] = ph.get(k, 0) + v
        out["cadence"] = {k + "_share": sum(c[k] for c in cads) / busy for k in ("threat", "recover", "idle", "show", "opened")}
        out["cadence"]["blows_per_min"] = sum(c["blows"] for c in cads) / (busy / 1200.0)
        out["cadence"]["longest_idle_s"] = float(np.median([c["run_max"] for c in cads])) / 20
        out["cadence"]["longest_idle_p90_s"] = float(np.percentile([c["run_max"] for c in cads], 90)) / 20
        n = sum(ph.values()) or 1
        out["cadence"]["phrases"] = {k: v / n for k, v in sorted(ph.items())}
    out["deaths"] = dict(sorted(deaths.items(), key=lambda kv: -kv[1])[:4])
    return out


TARGETS = {  # PLAN §6.1 (PROPOSAL values)
    "tank": "win <= 10%",
    "dodger": "win >= 60%, TTK 80-120 s",
    "parrier": "win >= 80%, TTK 80-120 s",
    "expert": "win >= 98%, TTK <= 75 s, >= 50% health left",
    "diamond": "win >= 70%, TTK <= 140 s",
}


def judge(profile, s):
    w, ttk, hp = s["win"], s["ttk"], s["hp_left"]
    if profile == "tank":
        return w <= 0.10, "win" if w > 0.10 else ""
    if profile == "dodger":
        bad = []
        if w < 0.60:
            bad.append("win")
        if ttk is None or not (80 <= ttk <= 120):
            bad.append("ttk")
        return not bad, ",".join(bad)
    if profile == "parrier":
        bad = []
        if w < 0.80:
            bad.append("win")
        if ttk is None or not (80 <= ttk <= 120):
            bad.append("ttk")
        return not bad, ",".join(bad)
    if profile == "expert":
        bad = []
        if w < 0.98:
            bad.append("win")
        if ttk is None or ttk > 75:
            bad.append("ttk")
        if hp is None or hp < 0.5:
            bad.append("hp")
        return not bad, ",".join(bad)
    if profile == "diamond":
        bad = []
        if w < 0.70:
            bad.append("win")
        if ttk is None or ttk > 140:
            bad.append("ttk")
        return not bad, ",".join(bad)
    return True, ""


def normal_hits(W, cfg, item_level_on):
    """Hits to kill each on-level normal gate creature with the rank's forged Longsword (a 0.5-cadence player)."""
    arch = cfg["longsword"]
    R = cfg["rules"]
    p = cfg["profiles"]["parrier"]["cadence"]
    out = {}
    for rank in RANK_ORDER:
        themes = [k for k, v in W["themes"].items() if v["home"] == rank and k in {d["theme"] for d in W["mobs"].values() if d["tier"] == "LORD"}]
        mobs = [d for d in W["mobs"].values() if d["tier"] == "NORMAL" and d["theme"] in themes]
        if not mobs:
            continue
        w = forged_for(W, rank)
        P = player_at(W, cfg, rank, w["damage"])
        lv = P["level"]
        LC = cfg["lord"]
        base = P["attack"] * (1 + P["str"]) * (1 + P["item_level"]) * (1 + 0.6 * P["crit"])
        # expected motion per chain strike with rhythm p: strike 2 at E[c]=p, strike 3 at E[c]=p(1+p)
        cad = [0.0, p, p * (1 + p)]
        motions = [arch["moves"][m]["motion"] * (1 + R["CADENCE_DAMAGE"] * cad[i]) for i, m in enumerate(arch["chain"])]
        hits = []
        for d in mobs:
            hp = d["hp"] * (1 + LC["health_per_level"] * lv)
            armor = d["armor"] + LC["armor_per_level"] * lv
            k = 0
            while hp > 0 and k < 200:
                dmg = base * motions[k % len(motions)] * (0.75 if "HAUNTED" in d["traits"] else 1.0)
                hp -= armor_after(dmg, armor, 0.0)
                k += 1
            hits.append((k, d["id"]))
        hits.sort()
        ks = [h[0] for h in hits]
        out[rank] = {"level": lv, "weapon": w["id"], "min": ks[0], "median": float(np.median(ks)), "max": ks[-1],
                     "min_mob": hits[0][1], "max_mob": hits[-1][1], "in_target": sum(1 for k in ks if 3 <= k <= 6) / len(ks),
                     "n": len(ks)}
    return out


def build_jobs(W, cfg, args):
    jobs = []
    lords = lords_of(W, args.lord)
    if not lords:
        raise SystemExit(f"combat_sim: no Gate Lord {args.lord!r}")
    profiles = [p.strip() for p in args.profiles.split(",")] if args.profiles else PROFILE_ORDER
    for d in lords:
        rank = W["themes"][d["theme"]]["home"]
        w = forged_for(W, rank)
        for prof in profiles:
            jobs.append({"lord": d["id"], "profile": prof, "weapon": w["id"], "weapon_damage": w["damage"],
                         "seeds": args.seeds, "seed": args.seed, "cfg": cfg, "row": "forged"})
        if rank == "D" and not args.no_diamond and "parrier" in profiles:
            jobs.append({"lord": d["id"], "profile": "parrier", "weapon": "diamond_sword",
                         "weapon_damage": cfg["diamond_damage"] - 1.0, "seeds": args.seeds, "seed": args.seed, "cfg": cfg,
                         "row": "diamond"})
    return jobs


def fmt(v, kind):
    if v is None:
        return "-"
    if kind == "pct":
        return f"{100 * v:.0f}%"
    if kind == "s":
        return f"{v:.0f}"
    return str(v)


# Named bundles of overrides: the recommendations in docs/combat/sim-2026-10-26.md, each re-runnable with --preset.
PRESETS = {
    # the three bugs, as if fixed (code, not tuning)
    "fixes": ["fix.landed_class=1", "fix.effects=1", "fix.stagger=1"],
}
PRESETS["recommended"] = PRESETS["fixes"] + [
    "lord.damage_a=0.21", "lord.damage_b=0.0035",       # Gate Lord damage x (0.21 + 0.0035 x level): ~6% of you a told blow
    "lord.extra_health=1.85",                           # Tier.LORD extraHealth 1.0 -> 1.85
    "reach.flier=0.55", "reach.mother_of_spores=0.45",  # flying Lords orbit within reach (MobGoals.FlyHunt)
    "mobs.ignite_s=1", "mobs.fire_bolt_s=1",            # burns 4 s / 3 s -> 1 s
    "mobs.fang_scaled=1",                               # the fang bite carries the ability's own damage
    "mobs.minion_damage=0.5",                           # a Lord's summons hit half as hard
    "lords.the_sleeper.hp=20",                          # HAUNTED x0.75 and phased 30 of 140 ticks
    "longsword.slipcut.motion=2.5", "longsword.art.motion=7",
]
# per-Lord health so the Parrier's median lands near 82 s (the only place both TTK windows hold), and the Hive Regent's
# opening Royal Dive no longer heavy (a Parrier has no answer to a PERILOUS opener)
PRESETS["recommended-hp"] = PRESETS["recommended"] + [
    "lords.bell_warden.hp=28.5", "lords.ember_tyrant.hp=30", "lords.hive_regent.hp=18.5", "lords.mother_of_spores.hp=22",
    "lords.root_saint.hp=34.5", "lords.drowned_archivist.hp=33", "lords.gearwright_colossus.hp=32.5",
    "lords.glass_serpent.hp=25", "lords.lantern_broker.hp=34", "lords.abbot_of_frost.hp=32", "lords.mirror_knight.hp=27.5",
    "lords.unit_zero.hp=32.5", "lords.sky_leviathan.hp=23.5", "lords.hollow_herald.hp=28.5",
    "ability.hive_regent.0.0.heavy=false",
    # the three kit Lords fight by their move tables now (SPEC §34.3; docs/bosses/sim-2026-10-28.md): their own health
    "lords.ember_tyrant.hp=30.5", "lords.unit_zero.hp=30", "lords.mother_of_spores.hp=22.5",
]


def main(argv=None):
    ap = argparse.ArgumentParser(description="AWAKEN Arms combat simulator (PLAN §6.1)")
    ap.add_argument("--seeds", type=int, default=400)
    ap.add_argument("--seed", type=int, default=20261026)
    ap.add_argument("--lord", default=None, help="one Gate Lord id (e.g. bell_warden)")
    ap.add_argument("--profiles", default=None, help="comma list of tank,dodger,parrier,expert")
    ap.add_argument("--item-level", choices=("off", "on"), default="off",
                    help="on: melee x (1 + 0.075 x item level), the item level = the Lord's content level (PowerMath.levelScale)")
    ap.add_argument("--override", action="append", default=[], metavar="PATH=VALUE")
    ap.add_argument("--preset", choices=sorted(PRESETS), default=None, help="a named bundle of overrides (applied before --override)")
    ap.add_argument("--json", default=None, help="write every cell's numbers here")
    ap.add_argument("--jobs", type=int, default=min(4, os.cpu_count() or 1), help="worker processes (results don't depend on it)")
    ap.add_argument("--no-diamond", action="store_true")
    ap.add_argument("--quiet", action="store_true")
    ap.add_argument("--cadence", action="store_true", help="the kit Lords' fight rhythm: how much of the fight it threatens, waits, recovers (REWORK R0)")
    ap.add_argument("--untold", action="store_true", help="the untold-mob guard table instead of the Lords (tools/sim/untold.py)")
    args = ap.parse_args(argv)

    t0 = time.time()
    W = load_world()
    cfg = default_config(W)
    if args.item_level == "on":
        cfg["item_level"]["on"] = 1
    for o in (PRESETS[args.preset] if args.preset else []) + args.override:
        if "=" not in o:
            raise SystemExit(f"combat_sim: --override wants PATH=VALUE, got {o!r}")
        k, v = o.split("=", 1)
        apply_override(cfg, k.strip(), v.strip())
    if args.untold:
        import untold
        t = untold.table(W, cfg)
        untold.print_table(t)
        if args.json:
            with open(args.json, "w", encoding="utf-8") as f:
                json.dump(t, f, indent=1)
        return

    if args.profiles:
        bad = [p for p in args.profiles.split(",") if p.strip() not in PROFILE_ORDER]
        if bad:
            raise SystemExit(f"combat_sim: unknown profile(s) {bad}; pick from {PROFILE_ORDER}")
    jobs = build_jobs(W, cfg, args)
    results = []
    if args.jobs > 1 and len(jobs) > 1:
        with ProcessPoolExecutor(max_workers=args.jobs) as ex:
            for job, s in ex.map(run_job, jobs):
                results.append((job, s))
    else:
        for job in jobs:
            results.append(run_job(job))
    normals = normal_hits(W, cfg, cfg["item_level"]["on"])
    elapsed = time.time() - t0

    cells = {}
    for job, s in results:
        cells[(job["lord"], job["profile"], job["row"])] = s

    lords = lords_of(W, args.lord)
    profiles = [p.strip() for p in args.profiles.split(",")] if args.profiles else PROFILE_ORDER
    lines = []
    head = f"AWAKEN combat sim: Arms as built, {args.seeds} seeds a cell, item level {args.item_level}"
    if args.preset:
        head += f", preset {args.preset}"
    if args.override:
        head += ", overrides: " + " ".join(args.override)
    lines.append(head)
    lines.append("")
    hdr = f"{'Lord':24} {'rk':2} {'lv':>3} {'HP':>6} {'up':>4} |"
    for p in profiles:
        hdr += f" {p[:7]:>7} {'TTK':>4} {'hp':>4} |" if p != "tank" else f" {'tank':>5} {'dies':>4} |"
    lines.append(hdr)
    lines.append("-" * len(hdr))
    passes = {p: 0 for p in profiles}
    passes["diamond"] = 0
    n_diamond = 0
    for d in lords:
        rank = W["themes"][d["theme"]]["home"]
        lv = W["ranks"][rank]["content"]
        LC = cfg["lord"]
        lc = cfg["lords"][d["id"]]
        lhp = lc["hp"] * (1 + LC["health_per_level"] * lv) * LC["role_health"] * LC["extra_health"]
        row = f"{d['name'][:24]:24} {rank:2} {lv:>3} {lhp:>6.0f} "
        up = cells.get((d["id"], profiles[0], "forged"), {}).get("uptime")
        row += f"{fmt(up, 'pct'):>4} |"
        for p in profiles:
            s = cells.get((d["id"], p, "forged"))
            if s is None:
                row += " " * 19 + "|"
                continue
            ok, why = judge(p, s)
            passes[p] += ok
            mark = "" if ok else "*"
            if p == "tank":
                row += f" {fmt(s['win'], 'pct') + mark:>5} {fmt(s['death_t'], 's'):>4} |"
            else:
                row += f" {fmt(s['win'], 'pct') + mark:>7} {fmt(s['ttk'], 's'):>4} {fmt(s['hp_left'], 'pct'):>4} |"
        lines.append(row)
        sd = cells.get((d["id"], "parrier", "diamond"))
        if sd is not None:
            n_diamond += 1
            ok, _ = judge("diamond", sd)
            passes["diamond"] += ok
    lines.append("")
    lines.append("win% (median TTK s over wins, median health left over wins); tank: win% and median seconds to die; * = misses its target")
    if n_diamond:
        lines.append("")
        lines.append("Diamond sword (7), Parrier, D-rank Lords (target: " + TARGETS["diamond"] + "):")
        for d in lords:
            sd = cells.get((d["id"], "parrier", "diamond"))
            sf = cells.get((d["id"], "parrier", "forged"))
            if sd is None:
                continue
            ok, why = judge("diamond", sd)
            lines.append(f"  {d['name'][:24]:24} diamond {fmt(sd['win'], 'pct'):>4} TTK {fmt(sd['ttk'], 's'):>4}  "
                         f"(forged {fmt(sf['win'] if sf else None, 'pct')} TTK {fmt(sf['ttk'] if sf else None, 's')})  "
                         f"{'PASS' if ok else 'FAIL ' + why}")
    lines.append("")
    lines.append("Normal creatures, hits to kill on level with the rank's forged Longsword (target 3-6):")
    for rank, v in normals.items():
        lines.append(f"  {rank} (lv {v['level']:>3}, {v['weapon']}): median {v['median']:.0f}, range {v['min']}-{v['max']} "
                     f"({v['min_mob']} .. {v['max_mob']}), {100 * v['in_target']:.0f}% of {v['n']} in 3-6")
    lines.append("")
    lines.append("Targets (PLAN §6.1), Lords passing:")
    for p in profiles:
        lines.append(f"  {p:8} {TARGETS[p]:46} {passes[p]:>2}/{len(lords)}")
    if n_diamond:
        lines.append(f"  {'diamond':8} {TARGETS['diamond']:46} {passes['diamond']:>2}/{n_diamond}")
    if args.cadence:
        lines.append("")
        lines.append("Cadence of the kit Lords (share of the fight: told or striking / recovering / between moves / NONE moves / opened;")
        lines.append("blows a minute; the longest spell between moves, median and 90th percentile; phrase lengths):")
        for d in lords:
            for p in profiles:
                s = cells.get((d["id"], p, "forged"))
                if not s or "cadence" not in s:
                    continue
                c = s["cadence"]
                ph = " ".join(f"{k}:{100 * v:.0f}%" for k, v in c["phrases"].items())
                lines.append(f"  {d['name'][:22]:22} {p:8} threat {100 * c['threat_share']:3.0f}%  recover {100 * c['recover_share']:3.0f}%  "
                             f"idle {100 * c['idle_share']:3.0f}%  show {100 * c['show_share']:3.0f}%  opened {100 * c['opened_share']:3.0f}%  "
                             f"{c['blows_per_min']:4.1f} blows/min  still {c['longest_idle_s']:.1f}/{c['longest_idle_p90_s']:.1f} s  phrases {ph}")
    lines.append("")
    lines.append(f"({len(jobs)} cells, {sum(j['seeds'] for j in jobs)} fights, {elapsed:.1f} s)")
    if not args.quiet:
        print("\n".join(lines))
    if args.json:
        out = {"args": vars(args), "config": cfg, "elapsed_s": elapsed, "normals": normals, "cells": []}
        for job, s in results:
            out["cells"].append({"lord": job["lord"], "profile": job["profile"], "row": job["row"], "weapon": job["weapon"],
                                 "rank": W["themes"][W["mobs"][job["lord"]]["theme"]]["home"], **s,
                                 "pass": judge("diamond" if job["row"] == "diamond" else job["profile"], s)[0]})
        with open(args.json, "w") as f:
            json.dump(out, f, indent=1, default=str)
    return cells, normals


if __name__ == "__main__":
    main()
