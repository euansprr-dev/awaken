"""Runs tools/sim/pacing_sim.py with two extra knobs, to check that the regions plan keeps the balanced player's hours.
   ELITES   four shares of kills that are elites by the player's level band (E<10, D<25, C<45, B+); the rest go to hunters and neutrals.
   ZONE_DELTA_LOW / ZONE_DELTA   added to every archetype's zone_fit below / from level 25.
   usage: python3 -I docs/regions/proto/sim_variants.py LABEL ELITES ZONE_DELTA_LOW ZONE_DELTA   (e.g. v1 0.0,0.02,0.04,0.05 0 0)
Planning harness, not game code. The original file is untouched; a patched copy runs from a temp folder."""
import os, shutil, subprocess, sys, tempfile

root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
src = open(os.path.join(root, "tools/sim/pacing_sim.py")).read()
ROLE = "ROLE_MIX = ((0.55, 1.0), (0.30, 0.5), (0.10, 0.15), (0.05, 4.0))"
assert ROLE in src
src = src.replace(ROLE, ROLE + """
import os
ELITES = tuple(float(v) for v in os.environ.get('ELITES', '0.05,0.05,0.05,0.05').split(','))
ZONE_DELTA = float(os.environ.get('ZONE_DELTA', '0'))
ZONE_DELTA_LOW = float(os.environ.get('ZONE_DELTA_LOW', '0'))
def role_mix(lv):
    e = ELITES[0 if lv < 10 else 1 if lv < 25 else 2 if lv < 45 else 3]
    spare = 0.05 - e
    return ((0.55 + spare * 0.55 / 0.85, 1.0), (0.30 + spare * 0.30 / 0.85, 0.5), (0.10, 0.15), (e, 4.0))""")
assert "for share, mult in ROLE_MIX:" in src
src = src.replace("for share, mult in ROLE_MIX:", "for share, mult in role_mix(lv):")
old = 'delta = rng.randint(-2, 2) if rng.random() < p["zone_fit"] else -rng.randint(4, 12)'
assert old in src
src = src.replace(old, 'delta = rng.randint(-2, 2) if rng.random() < p["zone_fit"] + (ZONE_DELTA_LOW if lv < 25 else ZONE_DELTA) else -rng.randint(4, 12)')
args = sys.argv[1:] + [None] * 4
label, elites, low, high = args[0] or "run", args[1] or "0.05,0.05,0.05,0.05", args[2] or "0", args[3] or "0"
with tempfile.TemporaryDirectory() as d:
    open(os.path.join(d, "pacing_sim.py"), "w").write(src)
    shutil.copy(os.path.join(root, "tools/sim/gate_tuning.json"), d)  # the gate model; without it the numbers differ
    env = dict(os.environ, ELITES=elites, ZONE_DELTA_LOW=low, ZONE_DELTA=high)
    out = subprocess.run([sys.executable, "-I", os.path.join(d, "pacing_sim.py")], env=env, capture_output=True, text=True).stdout
print(out)
