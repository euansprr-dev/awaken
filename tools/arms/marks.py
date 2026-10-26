"""Writes data/awaken/arms/marks.json: the 48 Marks (SPEC §25 "Marks"), eight triggers by six effects.

Each row: the trigger, the effect, its name, how much (the effect's own unit, below) and, for a rider, its own Force
(an attuned weapon's Force wins). Units: rider = x the fighter's attack; posture = points (BREAK: ticks the Break is
held longer); heal = share of max health; energy = power energy; swift = ticks of Speed I; ward = ticks of Resistance I.
Run: python3 tools/arms/marks.py
"""
import json
import pathlib

EFFECTS = ["rider", "posture", "heal", "energy", "swift", "ward"]
# trigger: [(name, amount, force?) for each effect, in EFFECTS order]
ROWS = {
    "hit": [("Bite", 0.15, None), ("Weight", 2, None), ("Leech", 0.005, None), ("Siphon", 1, None),
            ("Flicker", 20, None), ("Bark", 20, None)],
    "finisher": [("Crescendo", 0.5, "CHARGE"), ("Hammerfall", 8, None), ("Second Wind", 0.02, None),
                 ("Overflow", 4, None), ("Tempo", 40, None), ("Bulwark Chord", 60, None)],
    "deflect": [("Counterspark", 0.8, "CHARGE"), ("Rebuke", 10, None), ("Mercy", 0.04, None), ("Echo Return", 6, None),
                ("Sidestep Grace", 60, None), ("Parry Ward", 60, None)],
    "perfect_dodge": [("Phantom Cut", 0.6, "SPIRIT"), ("Unbalance", 8, None), ("Second Breath", 0.03, None),
                      ("Updraft", 5, None), ("Windrunner", 60, None), ("Veil Step", 50, None)],
    "break": [("Sunder", 1.0, "GRAVITY"), ("Long Fall", 15, None), ("Reaping Gift", 0.05, None), ("Shatterlight", 8, None),
              ("Hunter's Pace", 60, None), ("Spoils of Iron", 80, None)],
    "kill": [("Chain Reaction", 0.6, "HEAT"), ("Dread", 10, None), ("Feast", 0.04, None), ("Harvest", 6, None),
             ("Momentum", 80, None), ("Trophy Shell", 80, None)],
    "guard": [("Thorns", 0.3, None), ("Stonewall", 5, None), ("Steady Breath", 0.015, None), ("Stored Force", 2, None),
              ("Riposte Haste", 30, None), ("Bastion", 30, None)],
    "perfect_heavy": [("Thunderclap", 0.7, "CHARGE"), ("Earthsplitter", 10, None), ("Lifesong", 0.03, None),
                      ("Overcharge", 5, None), ("Follow-through", 50, None), ("Iron Skin", 60, None)],
}


def ident(name):
    return "".join(c if c.isalnum() else "_" for c in name.lower().replace("'", "")).strip("_").replace("__", "_")


def main():
    marks = []
    for trigger, row in ROWS.items():
        assert len(row) == len(EFFECTS), trigger
        for effect, (name, amount, force) in zip(EFFECTS, row):
            m = {"id": ident(name), "name": name, "trigger": trigger, "effect": effect, "amount": amount}
            if force:
                m["force"] = force
            marks.append(m)
    ids = [m["id"] for m in marks]
    assert len(set(ids)) == len(ids) == 48, "48 Marks, each its own id"
    out = pathlib.Path(__file__).resolve().parents[2] / "src/main/resources/data/awaken/arms/marks.json"
    out.write_text(json.dumps({"schemaVersion": 1, "marks": marks}, indent=1) + "\n")
    print(f"{len(marks)} marks -> {out}")


if __name__ == "__main__":
    main()
