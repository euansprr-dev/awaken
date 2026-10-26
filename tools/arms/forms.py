"""Each style's combo as the weapon is really used (SPEC §25 "Forms"; Euan 2026-10-26: "a dagger should feel like
you're actually stabbing forwards... a hammer... from up to down, or side to side... the big thing first"): writes every
move's form (and its screen direction or thrust variant) into data/awaken/arms/archetypes.json's swing rows, and fits the
server's blow to it: a stab or a jab strikes narrow (arc at most 30 degrees) with the thrust's whoosh, a cut wide.
tools/art/anim_lab.py bakes the first-person swings from these; tools/art/specs/arms_moves.py picks each move's body.
Run: python3 tools/arms/forms.py
"""
import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[2]
TABLE = ROOT / "src/main/resources/data/awaken/arms/archetypes.json"

L2R, R2L = [1.0, 0.15], [-1.0, -0.2]          # level cuts: backhand rising a little, forehand falling a little
DIAG, DIAG_UP = [-0.9, -0.55], [0.9, 0.45]    # a forehand diagonal down, a backhand up
DOWN = [-0.22, -1.0]

# form, dir (a cut's travel on the screen) or variant (a thrust's line), and for a weapon in each hand which hand strikes
FORMS = {
    "longsword": {"strike_1": ("slash", DIAG), "strike_2": ("slash", DIAG_UP), "strike_3": ("chop", DOWN),
                  "strike_4": ("stab", "line"), "heavy": ("rise", None)},
    # a dagger in each hand: right, left, right, both, left (the off hand's moves are the main hand's, mirrored)
    "daggers": {"strike_1": ("stab", "line", "main"), "strike_2": ("stab", "low", "off"), "strike_3": ("slash", L2R, "main"),
                "strike_4": ("stab", "reverse", "both"), "strike_5": ("slash", R2L, "off"), "heavy": ("stab", "line", "both")},
    "maul": {"strike_1": ("slam", DOWN), "strike_2": ("swing", R2L), "strike_3": ("slam", [0.0, -1.0]),
             "strike_4": ("rise", None), "heavy": ("slam", [0.0, -1.0])},
    "axe": {"strike_1": ("chop", [-0.5, -1.0]), "strike_2": ("swing", L2R), "strike_3": ("chop", DOWN),
            "strike_4": ("swing", R2L), "heavy": ("chop", [0.0, -1.0])},
    "greatblade": {"strike_1": ("slash", DIAG), "strike_2": ("swing", L2R), "strike_3": ("whirl", R2L),
                   "strike_4": ("chop", DOWN), "heavy": ("rise", None)},
    "spear": {"strike_1": ("stab", "line"), "strike_2": ("stab", "low"), "strike_3": ("swing", R2L),
              "strike_4": ("stab", "line"), "heavy": ("stab", "line")},
    "rapier": {"strike_1": ("stab", "line"), "strike_2": ("stab", "low"), "strike_3": ("slash", DIAG),
               "strike_4": ("stab", "line"), "heavy": ("stab", "line")},
    "katana": {"strike_1": ("slash", R2L), "strike_2": ("slash", DIAG_UP), "strike_3": ("chop", DOWN),
               "strike_4": ("stab", "line"), "heavy": ("slash", R2L)},
    "twinblades": {"strike_1": ("slash", DIAG), "strike_2": ("slash", DIAG_UP), "strike_3": ("slash", R2L),
                   "strike_4": ("spin", R2L), "strike_5": ("spin", L2R), "heavy": ("spin", R2L)},
    "glaive": {"strike_1": ("swing", R2L), "strike_2": ("swing", L2R), "strike_3": ("whirl", R2L),
               "strike_4": ("stab", "line"), "heavy": ("whirl", R2L)},
    "scythe": {"strike_1": ("reap", R2L), "strike_2": ("reap", L2R), "strike_3": ("chop", DOWN),
               "strike_4": ("whirl", R2L), "heavy": ("chop", DOWN)},
    # worn on both fists: a jab, the other fist's cross, a hook, the other's uppercut, a straight (heavy: both)
    "gauntlets": {"strike_1": ("jab", None, "main"), "strike_2": ("jab", None, "off"), "strike_3": ("hook", None, "main"),
                  "strike_4": ("upper", None, "off"), "strike_5": ("jab", None, "main"), "heavy": ("jab", None, "both")},
    "chainblade": {"strike_1": ("lash", DIAG), "strike_2": ("lash", DIAG_UP), "strike_3": ("whirl", R2L),
                   "strike_4": ("stab", "line"), "heavy": ("stab", "line")},
    "shieldblade": {"strike_1": ("slash", DIAG), "strike_2": ("bash", None), "strike_3": ("chop", DOWN),
                    "strike_4": ("stab", "line"), "heavy": ("bash", None)},
    "warstaff": {"strike_1": ("swing", R2L), "strike_2": ("swing", L2R), "strike_3": ("chop", DOWN),
                 "strike_4": ("spin", R2L), "heavy": ("spin", R2L)},
    "precursor": {"strike_1": ("slash", DIAG), "strike_2": ("slash", DIAG_UP), "strike_3": ("slash", R2L),
                  "strike_4": ("stab", "line"), "heavy": ("slash", R2L)},
}
POINT = ("stab", "jab", "bash")


def main():
    d = json.loads(TABLE.read_text())
    changed = []
    for a in d["archetypes"]:
        for mv, spec in FORMS.get(a["id"], {}).items():
            form, extra = spec[0], spec[1]
            m = a["moves"][mv]
            sw = m["swing"]
            sw["form"] = form
            sw.pop("dir", None)
            sw.pop("variant", None)
            sw.pop("hand", None)
            if len(spec) > 2 and spec[2] != "main":
                sw["hand"] = spec[2]
            if isinstance(extra, list):
                sw["dir"] = extra
            elif isinstance(extra, str):
                sw["variant"] = extra
            if form in POINT:
                if m["arc"] > 30:
                    changed.append(f"{a['id']} {mv}: arc {m['arc']} -> 30 ({form})")
                    m["arc"] = 30
                m["swish"] = "thrust" if form != "bash" else "heavy"
            elif m.get("swish") == "thrust":
                m["swish"] = "heavy" if form in ("slam", "swing", "whirl", "chop") else "light"
                if m["arc"] < 70:
                    changed.append(f"{a['id']} {mv}: arc {m['arc']} -> 90 ({form})")
                    m["arc"] = 90
    TABLE.write_text(json.dumps(d, indent=2) + "\n")
    print("\n".join(changed) or "no arcs changed")


if __name__ == "__main__":
    main()
