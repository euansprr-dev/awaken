#!/usr/bin/env python3
"""How many landed blows a whole boss fight takes, against how many the rank's weapon lasts (planning harness).

Runs tools/sim/combat_sim.py (the game's own numbers) for every Gate Lord and Proving with three players (the hunter,
the dodger, the parrier) and writes docs/boss-experience/data/durability.json: per boss, the blows a winning fight
lands (landed a minute x the median time to win) and the share of a fresh weapon that is, today and as proposed.
One landed blow costs one use (vanilla's Weapon component; the axe preset costs two, read from ArmsItems).

Run:  python3 docs/boss-experience/harness/durability.py [--seeds 40] [--jobs 2]
"""
import argparse, json, os, re, subprocess, sys, tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", "..", ".."))
SIM = os.path.join(ROOT, "tools", "sim", "combat_sim.py")
PROVINGS = ["proving_ringer", "proving_examiner", "proving_unmade", "proving_sky_warden", "proving_keepers_shadow", "proving_last_loop"]
PROPOSED = {"E": 900, "D": 1300}  # the plan's change; C and up stay


def uses_today():
    src = open(os.path.join(ROOT, "src/main/java/dev/awaken/arms/ArmsData.java"), encoding="utf-8").read()
    out = {}
    for tier, uses in re.findall(r'new Weapon\("\w+_longsword", "longsword", "(\w)", [\d.F]+, -[\d.F]+, (\d+),', src):
        out[tier] = int(uses)
    return out


def sim(args, lord, seeds, jobs):
    with tempfile.NamedTemporaryFile(suffix=".json", delete=False) as f:
        path = f.name
    cmd = [sys.executable, SIM, "--seeds", str(seeds), "--jobs", str(jobs), "--profiles", "hunter,dodger,parrier",
           "--json", path, "--no-diamond", "--quiet"] + (["--lord", lord] if lord else [])
    subprocess.run(cmd, check=True, stdout=subprocess.DEVNULL)
    cells = json.load(open(path))["cells"]
    os.unlink(path)
    return cells


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--seeds", type=int, default=40)
    ap.add_argument("--jobs", type=int, default=2)
    a = ap.parse_args()
    today = uses_today()
    cells = sim(a, None, a.seeds, a.jobs)
    for lord in PROVINGS:
        cells += sim(a, lord, a.seeds, a.jobs)
    rows = {}
    for c in cells:
        r = rows.setdefault(c["lord"], {"id": c["lord"], "rank": c["rank"], "weapon": c["weapon"], "blows": {}, "taken_per_min": {}, "ttk": {}})
        ttk = c["ttk"] or 0
        r["blows"][c["profile"]] = round(c["landed_per_min"] * ttk / 60)
        r["ttk"][c["profile"]] = round(ttk)
        r["taken_per_min"][c["profile"]] = round(c.get("taken_per_min") or 0, 1)
    out = []
    for r in rows.values():
        worst = max(r["blows"].values())
        r["uses_today"] = today[r["rank"]]
        r["uses_proposed"] = PROPOSED.get(r["rank"], today[r["rank"]])
        r["worst_blows"] = worst
        r["share_today"] = round(worst / r["uses_today"], 3)
        r["share_proposed"] = round(worst / r["uses_proposed"], 3)
        out.append(r)
    dest = os.path.join(ROOT, "docs", "boss-experience", "data", "durability.json")
    json.dump({"seeds": a.seeds, "uses_today": today, "proposed": PROPOSED, "bosses": out}, open(dest, "w"), indent=1)
    print(f"{'boss':24} rk  blows (hunter/dodger/parrier)  of today's uses   of proposed")
    for r in out:
        b = r["blows"]
        print(f"{r['id']:24} {r['rank']}   {b.get('hunter', 0):4} {b.get('dodger', 0):4} {b.get('parrier', 0):4}"
              f"            {100 * r['share_today']:4.0f}% of {r['uses_today']:<5}  {100 * r['share_proposed']:4.0f}% of {r['uses_proposed']}")


if __name__ == "__main__":
    main()
