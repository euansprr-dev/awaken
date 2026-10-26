"""The combat sim's untold-mob scenario (`combat_sim.py --untold`): a parrier's guard against blows with no tell.

A zombie, husk, drowned, spider, silverfish or piglin attacks through vanilla's MeleeAttackGoal: the blow lands the tick
it is in reach and off cooldown (MeleeAttackGoal.java:128-136, swing and damage on one tick, then 20 ticks), so there
is no wind-up to read. Every Awaken creature tells its blows (MobGoals.ToldMelee) and is not in this scenario: the Gate
Lord table of combat_sim.py is untouched by the untold rule.

The model, per blow (whole ticks, as the server counts them):
  lead L = round(aim + e)   the ticks between the guard's raise and the blow, as the player saw it
  L < -grace         a hit (the guard came up after the blow; singleplayer: grace 0)
  0 <= L < window    a deflect (told: the style's deflect; untold: + ArmsRules.UNTOLD_DEFLECT)
  L >= window        a block (the guard was up early: the style's reduction)
  e ~ Normal(0, sigma), sigma = sqrt(skill^2 + cue^2)
skill: the profile's own spread, set so that aiming at a told window's middle lands it as often as the Lord table's
profile does (parrier 0.60, expert 0.85: PLAN §6.1). A told blow itself is played as the Lord table plays it: its tell
lets a miss be early, so a missed deflect blocks and never lands. cue: the extra spread of having no tell, ASSUMED 2 ticks for a
blow that keeps the 20-tick rhythm of the one before (interval timing near 1 s holds to ~5-10%) and 4 for the first
blow on contact or after a knockback (judging when a walking body crosses an unseen reach line); 3 is the blend shown
first, and the table sweeps it. aim: the lead the player raises at. Before, the told habit (the told window's middle);
after, a player who has felt the wider window raises at its middle; the told habit is shown after as well.
"""
import math

ZOMBIE_DAMAGE = 3.0  # vanilla Zombie.createAttributes ATTACK_DAMAGE, Normal difficulty
PLAYER_HP = 20.0
SKILL_HIT = {"parrier": 0.60, "expert": 0.85}  # combat_sim default_config profiles' "deflect"
CUE_MIX = 3.0
CUE_SWEEP = (2.0, 3.0, 4.0, 6.0)


def _phi(x):
    return 0.5 * (1.0 + math.erf(x / math.sqrt(2.0)))


def skill_sigma(told_window, hit):
    """The spread that lands a raise aimed at a told window's middle in it as often as `hit` (whole ticks)."""
    lo, hi = 0.05, 20.0
    for _ in range(100):
        s = (lo + hi) / 2
        if outcome(told_window, told_window / 2 - 0.5, s, 0)["deflect"] > hit:
            lo = s
        else:
            hi = s
    return (lo + hi) / 2


def outcome(window, aim, sigma, grace):
    """Shares of deflect, block and hit for a raise aimed `aim` ticks ahead of the blow with spread `sigma`."""
    def below(k):  # P(L < k), L the rounded lead
        return _phi((k - 0.5 - aim) / sigma)
    hit = below(-grace)
    deflect = below(window) - hit
    return {"deflect": deflect, "block": 1.0 - hit - deflect, "hit": hit}


def row(o, reduction, reward, R):
    per_blow = ZOMBIE_DAMAGE * (o["hit"] + o["block"] * (1 - reduction))
    return {
        **o,
        "damage_per_100": 100 * per_blow,
        "blows_to_fall": PLAYER_HP / per_blow if per_blow > 0 else float("inf"),
        "resonance_per_blow": o["deflect"] * R["RESONANCE_DEFLECT"] * reward,
        "posture_per_blow": o["deflect"] * int(R["DEFLECT_POSTURE_LIGHT"] * reward),
    }


def table(W, cfg):
    R = cfg["rules"]
    g = cfg["longsword"]["guard"]
    told = int(g["deflect"])
    untold = told + int(R["UNTOLD_DEFLECT"])
    red = float(g["reduction"])
    reward = float(R["UNTOLD_REWARD"])
    out = {"told_window": told, "untold_window": untold, "reduction": red, "reward": reward, "rows": []}
    out["rows"].append({"profile": "any", "case": "holds the guard up (no parry)", "cue": 0.0, "skill": 0.0,
                        **row({"deflect": 0.0, "block": 1.0, "hit": 0.0}, red, 1.0, R)})
    for prof, hit in SKILL_HIT.items():
        skill = skill_sigma(told, hit)
        # a told blow as the Lord table plays it: the tell lets a miss be early, so it blocks and never lands
        out["rows"].append({"profile": prof, "case": "told (an Awaken creature)", "cue": 0.0, "skill": skill,
                            **row({"deflect": hit, "block": 1.0 - hit, "hit": 0.0}, red, 1.0, R)})
        for cue in CUE_SWEEP:
            sigma = math.hypot(skill, cue)
            before = row(outcome(told, told / 2 - 0.5, sigma, 0), red, 1.0, R)
            habit = row(outcome(untold, told / 2 - 0.5, sigma, 0), red, reward, R)
            learned = row(outcome(untold, untold / 2 - 0.5, sigma, 0), red, reward, R)
            for case, r in (("before", before), ("after, told habit", habit), ("after, raised at the wider middle", learned)):
                out["rows"].append({"profile": prof, "case": case, "cue": cue, "skill": skill, **r})
    return out


def print_table(t):
    print(f"Untold blows (zombie {ZOMBIE_DAMAGE:g} damage, Normal; longsword guard: told window {t['told_window']} ticks, "
          f"untold {t['untold_window']}, block keeps {1 - t['reduction']:.0%}, untold reward x{t['reward']:g})")
    print(f"{'profile':8} {'cue':>4} {'case':34} {'deflect':>8} {'block':>6} {'hit':>6} {'dmg/100':>8} {'blows':>6} {'res/blow':>9} {'post/blow':>9}")
    for r in t["rows"]:
        print(f"{r['profile']:8} {r['cue']:4.0f} {r['case']:34} {r['deflect']:8.0%} {r['block']:6.0%} {r['hit']:6.0%} "
              f"{r['damage_per_100']:8.0f} {r['blows_to_fall']:6.1f} {r['resonance_per_blow']:9.1f} {r['posture_per_blow']:9.1f}")
