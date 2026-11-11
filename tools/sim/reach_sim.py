#!/usr/bin/env python3
"""AWAKEN reach model (planning harness, not game code; docs/fights/PLAN.md §2).

What it answers: for every attack that hurts at range or puts an effect on you, how does it reach you today, what do you
see first, how long before it lands, and does a dash (Arms' dodge) taken when you first see it get you out?

What it reads: every boss move table (data/awaken/kit/moves), every creature's abilities in the bestiaries
(mobs/def/*Bestiary.java, with Ability.Builder's defaults), the dodge from archetypes.json (the Longsword's), and the
executors' geometry as written in Abilities.java, AbilityRunner.java, Hazards.java and AwBolt.java (constants below,
each with its line of code). It does not run the game: positions are a straight model of one player at a set distance.

The player: sees the first cue (a class tell, else the first ring of particles), reacts REACT ticks later, and dashes
sideways (or away from a mark under them). The dash: DODGE_TICKS ticks, DODGE_DIST blocks, front-loaded as
ArmsRules.dodgeSpeed, evading every class on its first EVADE ticks. A second plan, "dash on the hit", dodges so the
strike lands in the evade ticks (perfect reading of the timing, no visual help), which always works for one hit.

Two models. "before" is the game as it was (docs/fights/PLAN.md §2: what the plan measured). "after" (the default) is
stage 1, "See it coming" (SPEC §47.2): everything that hurts at range travels or is drawn first (a shot, a volley, a
wave, a locked line or wedge, a leap's ring, a field), an aimed one locks at least 10 ticks (a shot or ground line 8)
before it lands, and pushes and effects come only with a landed hit. Its question is invariant I4: does a dash taken
at the lock (REACT ticks after it shows) get you out of every carrier, and can every wave be jumped or dashed through?

Run:  python3 tools/sim/reach_sim.py                (summary + every boss move, Markdown; the after model)
      python3 tools/sim/reach_sim.py --all          (also every creature ability)
      python3 tools/sim/reach_sim.py --model before (the game as the plan found it)
"""
import argparse
import collections
import glob
import json
import math
import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))

REACT = 5            # ticks from seeing a cue to pressing (250 ms, a practised player)
DIST = (4.0, 8.0, 14.0)  # blocks from the attacker's edge
PLAYER_HALF = 0.3    # a player's half width
BOLT_HALF = 0.2      # AwBolt is sized 0.4 (MobTypes.BOLT)
BOLT_SPEED = 0.9     # Abilities.bolts: AwBolt.shoot(..., 0.9)
NOVA_SPEED = 0.6     # Abilities.nova: AwBolt.shoot(..., 0.6)
BOLT_SPREAD = 0.14   # Abilities.bolts: spread 0.14 rad between bolts
FANG_STEP = 1.25     # Abilities.fangs: a fang every 1.25 blocks, one tick apart
FANG_BITE = 8        # EvokerFangs: bites 8 ticks after its warm-up
CLOUD_LIFE = 100     # AbilityRunner.execute CLOUD: Hazards.cloud(..., 100, ...), a pulse every 10 ticks
RUNNER_LOCK = 6      # AbilityRunner.tickWindup: tracks while left > 6

# How each kind reaches you (Abilities.java executors)
DELIVERY = {
    "BOLT": "shot", "ARROWS": "shot", "NOVA": "ring of shots",
    "RAIN": "mark under you", "CLOUD": "cloud under you", "ROOT": "mark under you", "LIFT": "mark under you",
    "LEAP": "body lands where you were", "CHARGE": "body rushes a line",
    "BEAM": "instant line", "CONE": "instant cone", "FANGS": "fangs along a line",
    "BURST": "instant ring round it", "SLAM": "instant ring round it", "FANGS_RING": "fangs round it",
    "PULL": "instant pull round it", "GUST": "instant blast round it", "AURA": "pulses round it",
    "DARKNESS": "effect on everyone round it", "BLINK": "teleports behind you",
}
DAMAGING = set(DELIVERY) - {"BLINK"}
# kinds whose push or effect lands before (or without) the hit, so a dodge that evades the damage still gets you
# (Abilities.root/lift/pull/gust add the effect or push first; darkness never hurts at all)
BYPASS = {"ROOT": "slowness 5 before the hit", "LIFT": "levitation before the hit", "PULL": "the pull before the hit",
          "GUST": "the blast before the hit", "DARKNESS": "darkness with no hit at all"}
STRIKE = {  # Ability.strike(): the class an ability lands as when nothing else names it
    "BOLT": "LIGHT", "ARROWS": "LIGHT", "NOVA": "LIGHT",
    "SLAM": "HEAVY", "LEAP": "HEAVY", "CHARGE": "HEAVY", "BLINK": "HEAVY", "CONE": "HEAVY", "FANGS": "HEAVY", "FANGS_RING": "HEAVY",
}
DEFAULTS = {  # Ability.Builder: windup, range, radius, count, inner
    "BOLT": (14, 18, 3, 1, 0), "ARROWS": (14, 18, 3, 1, 0), "NOVA": (24, 12, 3, 10, 0), "RAIN": (34, 24, 2.5, 3, 0),
    "BEAM": (30, 18, 3, 1, 0), "SUMMON": (30, 24, 3, 2, 0), "CHARGE": (18, 14, 3, 1, 0), "LEAP": (16, 12, 2.5, 1, 0),
    "SLAM": (22, 3.5, 3.5, 1, 0), "BURST": (40, 14, 12, 1, 3.5), "CLOUD": (18, 12, 2.5, 1, 0), "CONE": (18, 5, 5, 1, 0),
    "PULL": (24, 10, 10, 1, 0), "GUST": (24, 10, 10, 1, 0), "HEAL": (30, 24, 10, 1, 0), "ROOT": (26, 14, 2.0, 1, 0),
    "FANGS": (20, 14, 3, 10, 0), "FANGS_RING": (20, 5, 3, 8, 0), "DECOY": (24, 16, 3, 2, 0), "GUARD": (8, 6, 3, 1, 0),
    "LIFT": (30, 16, 3.0, 1, 0), "DARKNESS": (26, 16, 16, 1, 0), "AURA": (36, 8, 6, 5, 0), "FRENZY": (20, 16, 3, 1, 0),
    "BLINK": (14, 16, 3, 1, 0),
}
BOLT_EFFECT = {"FROST": "slowness", "SPIRIT": "weakness", "SPORE": "poison", "WATER": "blindness", "CHARGE": "slowness",
               "HONEY": "slowness", "THORN": "poison", "DREAM": "nausea", "GRAVITY": "levitation", "VOID": "darkness",
               "SIGNAL": "glowing", "FIRE": "burning"}


def dodge_offset(t):
    """Blocks travelled t ticks into the dodge (ArmsRules.dodgeSpeed: weights 1 - i/n, summing to the distance)."""
    n = DODGE_TICKS
    s = n * (n + 1) / (2 * n)
    return sum(DODGE_DIST * (1 - i / n) / s for i in range(min(max(0, int(t)), n)))


def read_dodge():
    d = json.load(open(os.path.join(ROOT, "src/main/resources/data/awaken/arms/archetypes.json")))
    arr = d["archetypes"] if isinstance(d, dict) else d
    ls = next(a for a in arr if a.get("id") == "longsword")["dodge"]
    return ls["ticks"], ls["distance"], ls["evade"]


DODGE_TICKS, DODGE_DIST, EVADE = read_dodge()


def parse_abilities():
    """name -> list of ability dicts, from every bestiary (a(KIND, "Name") and its chained builder calls)."""
    out = collections.defaultdict(list)
    for f in sorted(glob.glob(os.path.join(ROOT, "src/main/java/dev/awaken/mobs/def/*Bestiary.java"))):
        s = open(f).read()
        for m in re.finditer(r'out\.add\(m\("([^"]+)",\s*"([^"]+)",\s*Theme\.(\w+),\s*Tier\.(\w+)(.*?)\.build\(\)\);', s, re.S):
            mob, name, theme, tier, body = m.groups()
            for a in re.finditer(r'\ba\((\w+),\s*"([^"]+)"\)((?:\.\w+\((?:[^()]|\([^()]*\))*\))*)', body):
                kind, aname, chain = a.groups()
                if kind not in DEFAULTS:
                    continue
                w, rng, rad, n, inner = DEFAULTS[kind]
                ab = dict(mob=mob, mob_name=name, theme=theme, tier=tier, kind=kind, name=aname, windup=w, range=rng,
                          radius=rad, count=n, inner=inner, heavy=False, bolt=None, fx=None, file=os.path.basename(f))
                for c in re.finditer(r'\.(\w+)\(((?:[^()]|\([^()]*\))*)\)', chain):
                    k, v = c.groups()
                    num = re.sub(r'[F\s]', '', v.split(',')[0]) if v else ''
                    if k == "wind": ab["windup"] = int(num)
                    elif k == "radius": ab["radius"] = float(num)
                    elif k == "inner": ab["inner"] = float(num)
                    elif k == "n": ab["count"] = int(num)
                    elif k == "range": ab["range"] = float(num)
                    elif k == "heavy": ab["heavy"] = True
                    elif k == "bolt": ab["bolt"] = v.split('.')[-1]
                    elif k == "fx": ab["fx"] = v.split(',')[0].split('.')[-1].lower()
                out[aname].append(ab)
    return out


def theme_bolts():
    s = open(os.path.join(ROOT, "src/main/java/dev/awaken/mobs/def/Theme.java")).read()
    return {m.group(1): m.group(2) for m in re.finditer(r'^\s*(\w+)\("[^"]+",\s*"[^"]+",\s*Rank\.\w+,\s*0x\w+,\s*0x\w+,\s*BoltKind\.(\w+)\)', s, re.M)}


def effect_of(ab, themes):
    if ab["fx"]:
        return ab["fx"]
    b = ab["bolt"] or themes.get(ab["theme"])
    return BOLT_EFFECT.get(b)


def judge(kind, cls, windup, lock, radius, inner, count, dist, told):
    """One attack against a player `dist` blocks from the attacker's edge. Returns (travel ticks, warning ticks,
    seen-travelling, dash-at-cue escapes?, why)."""
    strike = windup + 1                       # the first active tick
    cue = 0 if told else 2                    # a class tell starts with the wind-up; else the first ring of dust
    go = cue + REACT                          # the dash starts here
    def evaded(at):
        return go <= at < go + EVADE
    if kind in ("BOLT", "ARROWS"):
        travel = dist / (BOLT_SPEED if kind == "BOLT" else 1.6)
        arrive = strike + travel
        if lock > go + DODGE_TICKS:  # aim still follows you after the dash
            return travel, arrive, True, evaded(arrive), "the aim follows the dash until it locks"
        off = dodge_offset(arrive - go)
        lines = [dist * math.tan((i - (count - 1) / 2) * BOLT_SPREAD) for i in range(count)]
        clear = all(abs(off - l) > PLAYER_HALF + BOLT_HALF for l in lines)
        return travel, arrive, True, clear or evaded(arrive), "clear of the fan" if clear else "lands on another bolt of the fan"
    if kind == "NOVA":
        travel = dist / NOVA_SPEED
        arrive = strike + travel
        gap = 2 * math.pi * (dist + 1) / max(4, count)
        p_hit = min(1.0, (2 * (PLAYER_HALF + BOLT_HALF)) / gap)
        return travel, arrive, True, p_hit < 0.5 or evaded(arrive), f"a gap {gap:.1f} wide between shots ({p_hit:.0%} to stand in one's line)"
    if kind == "RAIN":  # marks placed on you when the wind-up starts (AbilityRunner.prepare/rainMarks), never moved
        out = dodge_offset(DODGE_TICKS) > radius
        return 0, strike, False, out or evaded(strike), f"the dash ({DODGE_DIST}) {'clears' if out else 'stays inside'} the {radius}-block mark"
    if kind in ("CLOUD", "ROOT", "LIFT", "LEAP"):  # aimed at you until it locks, then lands there
        if lock - go >= DODGE_TICKS:
            out = False
            why = "the aim follows the dash until it locks; nothing shows the lock"
        else:
            out = dodge_offset(strike - go) > radius
            why = "locked before the dash ends"
        lingering = kind == "CLOUD" and DODGE_DIST < radius
        if lingering:
            why += f"; the cloud ({radius}) is wider than the dash and pulses for {CLOUD_LIFE // 20} s"
        return 0, strike, False, (out or evaded(strike)) and not lingering, why
    if kind == "BEAM":  # aimed at your chest until it locks, then a thin line, instant (the line brightens at the lock)
        side_time = 2  # ticks of dash to move 0.6 blocks
        ok = lock + REACT + side_time <= strike
        return 0, strike, False, ok, f"{strike - lock} ticks from the lock (the line brightens) to the hit; {REACT + side_time} needed"
    if kind in ("BURST",):  # Abilities.area: everyone inside radius but outside inner, at once
        inside = dist - DODGE_DIST < inner
        out = dist + DODGE_DIST > radius
        return 0, strike, False, inside or out or evaded(strike), (f"step inside {inner} (a dash from {dist:.0f} reaches {max(0, dist - DODGE_DIST):.1f})"
                                                                   + ("" if not out else f" or out of {radius}"))
    if kind in ("SLAM", "AURA", "PULL", "GUST", "DARKNESS", "FANGS_RING"):
        out = dist + DODGE_DIST > radius
        return 0, strike, False, out or evaded(strike), f"{'outside' if dist > radius else 'inside'} its {radius}-block reach"
    if kind == "CONE":
        out = dodge_offset(DODGE_TICKS) > dist * math.tan(math.acos(0.7))
        return 0, strike, False, out or dist > radius, "a sideways dash leaves the 45° cone" if out else "the cone is wider than the dash"
    if kind == "FANGS":
        travel = dist / FANG_STEP + FANG_BITE
        return travel, strike + travel, True, True, "fangs rise one by one along the line you can step off"
    if kind == "CHARGE":
        return dist / 0.85, strike + dist / 0.85, True, True, "the body comes at you; a sideways dash clears it"
    return 0, strike, False, False, ""


# ---- the after model (stage 1, SPEC §47.2); constants as written in mobs/entity/Carriers.java and Abilities.java ---------

LOCK = 10            # Carriers.LOCK: a line, wedge or leap stops following this long before it lands
LOCK_SHOT = 8        # Carriers.LOCK_SHOT: a shot, a ground line or a lob
SHOT_SPEED = {"LIGHT": 0.9, "HEAVY": 0.7, "PERILOUS": 0.6, "GRAB": 0.6}   # Abilities.shotSpeed
VOLLEY_RING = 2.2    # Carriers.VOLLEY_RING
VOLLEY_FALL = 16     # Carriers.VOLLEY_FALL
WAVE_SPEED = 0.55    # Carriers.WAVE_SPEED
GROUND_SPEED, GROUND_WIDTH = 0.7, 0.7   # Carriers.GROUND_SPEED, GROUND_WIDTH
CONE_DOT = 0.8       # Abilities.CONE_DOT: the widest a cone gets (a long one is narrower: Abilities.coneDot)
LOB_RING = 1.0       # Carriers.lob: a 1-block ring where the globe falls
REACH_PAD = 0.3      # Carriers.volleyLands: enemiesNear(point, ring + 0.3)
SLAM_CORE = 1.5      # AbilityRunner.SLAM_CORE: the blow itself (a melee answer), its reach beyond a wave
WAVES = {"SLAM", "BURST", "PULL", "GUST", "DARKNESS", "AURA"}
CARRIER = {  # how each kind reaches you in the after model
    "BOLT": "shot", "ARROWS": "arrow", "NOVA": "ring of shots", "RAIN": "volley", "LIFT": "volley", "CLOUD": "lob and field",
    "ROOT": "ground line", "FANGS": "fangs along a locked line", "LEAP": "leap on a shown ring", "CHARGE": "charge down a locked line",
    "BEAM": "locked line", "CONE": "locked wedge", "BURST": "wave", "SLAM": "blow and wave", "PULL": "pull then wave",
    "GUST": "wave", "AURA": "waves", "DARKNESS": "wave", "FANGS_RING": "fangs round it", "BLINK": "step and told cut",
}


def cone_dot(radius):
    """Abilities.coneDot: as wide as CONE_DOT, narrower for a long cone, so a dash aside always leaves it where a dash
    back doesn't (at its worst spot, a dash short of its end, its half width is 2.3 blocks)."""
    return max(CONE_DOT, math.cos(math.atan(2.3 / max(0.1, radius - 2.6))))


def lead(kind):
    return LOCK if kind in ("BEAM", "CHARGE", "CONE", "LEAP") else LOCK_SHOT if kind in ("BOLT", "ARROWS", "FANGS", "ROOT", "CLOUD") else 0


def judge_after(kind, cls, windup, lock, radius, inner, count, dist, told):
    """One attack in the after model: (travel ticks, warning ticks, seen-travelling, a dash at the lock escapes?, why)."""
    strike = windup + 1
    lock_at = min(lock, windup - lead(kind)) if lead(kind) else lock
    shown = max(0, lock_at)                   # the lock shows (a line's flash, a ring) on this tick
    go = shown + REACT                        # the dash starts here
    def evaded(at):
        return go <= at < go + EVADE
    if kind in ("BOLT", "ARROWS"):
        speed = SHOT_SPEED.get(cls, 0.9) if kind == "BOLT" else 1.6
        travel = dist / speed
        arrive = strike + travel
        off = dodge_offset(arrive - go)
        lines = [dist * math.tan((i - (count - 1) / 2) * BOLT_SPREAD) for i in range(count)]
        # dash to whichever side clears the fan; a fan wider than a dash is seen flying, and a dash timed on it evades it
        clear = any(all(abs(sgn * off - l) > PLAYER_HALF + BOLT_HALF for l in lines) for sgn in (1, -1))
        timed = travel >= REACT
        return travel, arrive, True, clear or evaded(arrive) or timed, ("the aim locked; the dash clears the line" if clear
                                                                         else f"seen flying {travel / 20:.2f} s: a dash timed on it")
    if kind == "NOVA":
        speed = SHOT_SPEED.get(cls, 0.9)
        travel = dist / speed
        arrive = strike + travel
        gap = 2 * math.pi * (dist + 1) / max(4, count)
        p_hit = min(1.0, (2 * (PLAYER_HALF + BOLT_HALF)) / gap)
        # seen leaving the body: a dash timed to its arrival evades it, or stand in a gap; close in, it arrives inside the
        # evade of a dash on its release (the told wind-up's end, with a flash)
        timed = travel >= REACT or (told and travel < EVADE)
        return travel, arrive, True, p_hit < 0.5 or timed, f"seen from its release, {travel / 20:.2f} s out; gaps {gap:.1f} wide"
    if kind in ("RAIN", "LIFT"):  # a volley: thrown at the strike, ringed where you stand, landing VOLLEY_FALL later
        ring = min(VOLLEY_RING, radius) + REACH_PAD
        land = strike + VOLLEY_FALL
        go = strike + REACT
        out = dodge_offset(land - go) > ring
        return VOLLEY_FALL, land, True, out or evaded(land), f"a {min(VOLLEY_RING, radius):.1f} ring shown {VOLLEY_FALL / 20:.1f} s before it lands; the dash ({DODGE_DIST}) clears it"
    if kind == "CLOUD":  # a lob locked on you: a 1-block ring, then a field <= 2 harmless for half a second
        ring = LOB_RING + REACH_PAD
        land = strike + VOLLEY_FALL
        out = dodge_offset(land - go) > ring
        return VOLLEY_FALL, land, True, out or evaded(land), "a lob's ring; the field it leaves waits half a second, small enough to walk out of"
    if kind == "LEAP":  # its landing ring shows at the lock; it lands at least LOCK later
        ring = min(VOLLEY_RING + 0.3, radius)
        out = dodge_offset(strike - go + 8) > ring  # the body flies a few ticks after it leaves (8 at least)
        return 0, strike, True, out or evaded(strike), f"the landing ring ({ring:.1f}) shows {(strike - shown) / 20:.2f} s before it leaves"
    if kind == "ROOT":  # a ground line racing from the body, locked
        arrive = strike + dist / GROUND_SPEED
        out = dodge_offset(arrive - go) > GROUND_WIDTH + PLAYER_HALF
        return dist / GROUND_SPEED, arrive, True, out or evaded(arrive), "a root line runs at you along the ground; step off it"
    if kind == "FANGS":
        arrive = strike + dist / (FANG_STEP / 2) + FANG_BITE
        out = dodge_offset(arrive - go) > 0.6 + PLAYER_HALF
        return arrive - strike, arrive, True, out or evaded(arrive), "fangs rise along the locked line, a fang every two ticks"
    if kind == "BEAM":
        ok = dodge_offset(strike - go) > PLAYER_HALF + 0.05 or evaded(strike)
        return 0, strike, True, ok, f"the line locks {(strike - shown) / 20:.2f} s before it fires, with a flash and a click"
    if kind == "CHARGE":
        arrive = strike + dist / 0.85
        ok = dodge_offset(arrive - go) > 1.0 + PLAYER_HALF or evaded(arrive)
        return dist / 0.85, arrive, True, ok, "the charge's line shows at its lock; a sideways dash clears it"
    if kind == "CONE":
        if dist > radius:
            return 0, strike, True, True, "outside its reach"
        back = dist + dodge_offset(strike - go) > radius
        side = dodge_offset(strike - go) > dist * math.tan(math.acos(cone_dot(radius))) + PLAYER_HALF
        return 0, strike, True, back or side or evaded(strike), "the wedge shows at its lock; dash out of it (back, or aside when close)"
    if kind in WAVES:
        start = SLAM_CORE if kind == "SLAM" else max(1.0, inner) if kind == "BURST" else 1.0
        if kind == "SLAM" and dist <= SLAM_CORE:
            return 0, strike, False, True, "the slam's blow itself: answered as a melee blow"
        if dist > radius:
            return 0, strike, True, True, "outside its reach"
        delay = 20 if kind == "PULL" else 0
        arrive = strike + delay + max(0.0, dist - start) / WAVE_SPEED
        # a wave can't be outrun: it is jumped (its crest is lower than a jump for 10 ticks) or dashed through, timed on the
        # crest you see rolling at you (and a step inside its safe ring when it starts out from a ring)
        return arrive - strike, arrive, True, True, f"a wave rolls out ({WAVE_SPEED} blocks a tick): jump it or dash through"
    if kind == "FANGS_RING":
        return 0, strike, True, True, "fangs rise round it, each seen before it bites"
    return 0, strike, False, False, ""


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--all", action="store_true", help="every creature ability too")
    ap.add_argument("--json")
    ap.add_argument("--model", choices=("before", "after"), default="after")
    args = ap.parse_args()
    after = args.model == "after"
    judge_fn = judge_after if after else judge
    abil = parse_abilities()
    themes = theme_bolts()
    rows = []
    for f in sorted(glob.glob(os.path.join(ROOT, "src/main/resources/data/awaken/kit/moves/*.json"))):
        d = json.load(open(f))
        for mv in d["moves"]:
            ab = mv.get("ability")
            if not ab or ab not in abil:
                continue
            a = next((x for x in abil[ab] if x["mob"] == d["id"]), abil[ab][0])
            if a["kind"] not in DAMAGING:
                continue
            t = mv["time"]
            cls = mv.get("class", "NONE")
            lands = cls if cls != "NONE" else ("PERILOUS" if a["heavy"] else STRIKE.get(a["kind"], "PERILOUS"))
            told = cls != "NONE" or after  # FightRunner.told: a move that hurts is told by what it does
            w = t["windup"] + t.get("hold", 0)
            lock = t.get("lock", 0)
            res = [judge_fn(a["kind"], lands, w, lock, a["radius"], a["inner"], a["count"], dd, told) for dd in DIST]
            fx = effect_of(a, themes) if not after or mv.get("signature") else None  # I6: a boss's effect on its signature only
            rows.append(dict(src="boss", who=d["id"], move=mv["id"], ability=ab, kind=a["kind"], told=told, lands=lands,
                             windup=w, lock=lock, effect=fx, results=res))
    if args.all:
        for name, lst in abil.items():
            for a in lst:
                if a["kind"] not in DAMAGING or a["tier"] in ("LORD", "GUARDIAN", "SOVEREIGN", "FINAL", "NIGHTMARE"):
                    continue
                w = a["windup"] + (8 if a["heavy"] else 0)
                lands = "PERILOUS" if a["heavy"] else STRIKE.get(a["kind"], "PERILOUS")
                runner_lock = w - max(RUNNER_LOCK, lead(a["kind"])) if after else w - RUNNER_LOCK
                res = [judge_fn(a["kind"], lands, w, runner_lock, a["radius"], a["inner"], a["count"], dd, True) for dd in DIST]
                rows.append(dict(src=a["tier"].lower(), who=a["mob"], move=name, ability=name, kind=a["kind"], told=True,
                                 lands=lands, windup=w, lock=w - RUNNER_LOCK, effect=effect_of(a, themes), results=res))

    def summary(sel, label):
        n = len(sel)
        if not n:
            return
        by = collections.Counter((CARRIER if after else DELIVERY)[r["kind"]] for r in sel)
        travelling = sum(1 for r in sel if r["results"][1][2])
        untold = sum(1 for r in sel if not r["told"])
        unparry = sum(1 for r in sel if r["lands"] in ("PERILOUS", "GRAB"))
        esc = [sum(1 for r in sel if r["results"][i][3]) for i in range(len(DIST))]
        eff = sum(1 for r in sel if r["effect"])
        byp = 0 if after else sum(1 for r in sel if r["kind"] in BYPASS)
        # before: every shot looked alike (an item sprite); after, a shot's core is its class's colour
        unparry_shots = 0 if after else sum(1 for r in sel if r["kind"] in ("BOLT", "NOVA") and r["lands"] not in ("LIGHT",))
        print(f"\n### {label}: {n} attacks that hurt at range or put an effect on you\n")
        print(f"- Seen before it lands (it travels, or its lock, ring or crest is drawn): {travelling} ({travelling / n:.0%}).")
        print(f"- No class tell at all (the move's class is NONE; only coloured dust shows): {untold} ({untold / n:.0%}).")
        print(f"- Can't be parried (they land PERILOUS): {unparry} ({unparry / n:.0%}); of them shots that look like parryable ones: {unparry_shots}.")
        print(f"- Carry an effect on a hit (poison, blindness, slowness and the like): {eff} ({eff / n:.0%}).")
        print(f"- Put their push or effect on you even when a dodge evades the damage: {byp}.")
        print(("- A dash at the lock (a wave: jumped or dashed through) gets you out: " if after else "- A dash taken at the first cue gets you out: ")
              + ", ".join(f"{e} ({e / n:.0%}) at {d:.0f} blocks" for e, d in zip(esc, DIST)) + ".")
        if after:
            bad = [r for r in sel if not all(x[3] for x in r["results"])]
            for r in bad[:12]:
                print(f"  - not escaped: {r['who']} {r['move']} ({r['kind']}): " + "; ".join(x[4] for x in r["results"] if not x[3]))
        timed = len(sel) if after else sum(1 for r in sel if r["kind"] not in BYPASS and r["kind"] not in ("CLOUD", "AURA"))
        print(f"- A dodge pressed so the hit lands in its {EVADE} evade ticks (perfect timing, nothing on screen marks that tick): evades {timed} ({timed / n:.0%}); "
              + ("every one: nothing lands without its hit." if after else "the rest are clouds and auras that pulse again, or pushes and effects that land anyway."))
        print("- How they reach you: " + ", ".join(f"{k} {v}" for k, v in by.most_common()) + ".")

    summary([r for r in rows if r["src"] == "boss"], "Bosses (41 kit move tables)")
    if args.all:
        summary([r for r in rows if r["src"] != "boss"], "Every other creature (gate monsters, wardens, elites, named beasts, the wild)")

    print("\n### Every boss move\n")
    print("| Boss | Move | How it reaches you | Told | Lands as | Warning (s) | Locks on you (s before) | Effect | Dash at the cue, 8 blocks | Why |")
    print("|---|---|---|---|---|---|---|---|---|---|")
    for r in rows:
        if r["src"] != "boss":
            continue
        tr, arrive, seen, ok, why = r["results"][1]
        print(f"| {r['who']} | {r['move']} ({r['ability']}) | {(CARRIER if after else DELIVERY)[r['kind']]} | {'yes' if r['told'] else 'no'} | {r['lands']} | "
              f"{arrive / 20:.2f} | {max(0, r['windup'] + 1 - r['lock']) / 20:.2f} | {r['effect'] or ''}{' · ' + BYPASS[r['kind']] if r['kind'] in BYPASS else ''} | "
              f"{'yes' if ok else 'no'} | {why} |")
    if after:
        escaped = sum(1 for r in rows if all(x[3] for x in r["results"]))
        print(f"\nI4 (after model): {escaped} of {len(rows)} attacks escaped at every distance ({escaped / max(1, len(rows)):.0%}).")
    if args.json:
        json.dump(rows, open(args.json, "w"), indent=1)


if __name__ == "__main__":
    main()
