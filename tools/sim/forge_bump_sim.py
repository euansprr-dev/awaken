"""combat_sim with the weapon forge's decision 14 (SPEC §46.5): BUMP=1 adds +1 damage to every forged weapon and +2 to
the eight slower styles before the fights; BUMP=0 (or unset) is the sim as built. Report: docs/weapon-forge/sim-2026-11-02.md.

    BUMP=1 python3 tools/sim/forge_bump_sim.py --seeds 200 --jobs 1 --preset recommended-hp

Run with --jobs 1: worker processes import combat_sim afresh and would not see the bump."""
import os, sys
sys.path.insert(0, os.path.join(os.getcwd(), "tools", "sim"))
import combat_sim as cs

HEAVY = {"axe", "scythe", "greatblade", "maul", "glaive", "spear", "warstaff", "chainblade"}
_orig = cs.load_world


def load_world():
    W = _orig()
    if os.environ.get("BUMP") == "1" and not W.get("_bumped"):
        for w in W["weapons"].values():
            w["damage"] += 2.0 if w["style"] in HEAVY else 1.0
        W["_bumped"] = True
    return W


cs.load_world = load_world
cs.main(sys.argv[1:])
