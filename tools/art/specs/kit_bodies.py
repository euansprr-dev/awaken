"""The Creature Kit's bodies (SPEC §34.2): rigs, clips, move tables, fight scripts and textures for every kit boss.

    python3 tools/art/build_all.py kit_bodies          everything, through build_all
    python3 tools/art/specs/kit_bodies.py build [ids]   the same (all ids, or the ones named)
    python3 tools/art/specs/kit_bodies.py lab DIR [ids] the Creature Lab's sheets (kit/lab.py)
    python3 tools/art/specs/kit_bodies.py motion DIR [ids]  every move and string as a GIF, and the motion meter (kit/motion.py)
    python3 tools/art/specs/kit_bodies.py silhouettes DIR [BOSS]   a boss's silhouette round (kit/silhouettes.py, kit/sil/)

Each boss is a module in kit/bosses/<id>.py with design() -> {rig, plan, moves, fight, paint, scale, half} (half: its
hitbox's half width in blocks, which the move ranges are measured from). The build is
deterministic and stops on any of the lab's checks (kit.core.check). It writes:
  assets/awaken/kit/rigs/<id>.rig          the rig and its clips (the hero rig text format, read by HeroRig.parse)
  data/awaken/kit/moves/<id>.json          the move table (KitMoves.Table)
  data/awaken/kit/fights/<id>.json         the fight script (FightScripts.Fight)
  data/awaken/kit/index.json               the kit ids
  textures/entity/kit/<id>[_glow][_blink].png
"""
import importlib
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ART = os.path.abspath(os.path.join(HERE, ".."))
ROOT = os.path.abspath(os.path.join(HERE, "../../.."))
RES = os.path.join(ROOT, "src/main/resources")
sys.path.insert(0, HERE)
sys.path.insert(0, ART)
import fauna_rig as R  # noqa: E402
from kit import core  # noqa: E402

BOSSES = ["ember_tyrant", "unit_zero", "mother_of_spores", "gearwright_colossus", "root_saint", "bell_warden", "glass_serpent", "hive_regent", "drowned_archivist", "lantern_broker"]


def designs(only=None):
    out = []
    for bid in BOSSES:
        if only and bid not in only:
            continue
        mod = importlib.import_module("kit.bosses." + bid)
        out.append(mod.design())
    return out


def paint(d):
    P = R.Painter(d["rig"], "kit|" + d["rig"].id)
    skin, glow = d["paint"](d["rig"], P)
    return skin, glow, P.blink()


def build(out=None, only=None):
    from pixelart import Out
    out = out or Out(RES)
    ids = []
    failed = []
    for d in designs(only):
        rig, mv = d["rig"], d["moves"]
        for m in mv:
            m.capsules = core.capsules(rig, m) if m.hit else []
            core.fit_range(m, d["half"])
            m.marks = core.marks(rig, m)
        problems = core.check(rig, mv)
        if problems:
            failed += problems
            continue
        skin, glow, blink = paint(d)
        pairs = [(rig.id, skin), (rig.id + "_glow", glow)]
        if blink is not None:
            pairs += [(rig.id + "_blink", blink[0]), (rig.id + "_glow_blink", blink[1])]
        for name, canvas in pairs:
            out.entity("kit/" + name, canvas)
        rp = os.path.join(RES, "assets/awaken/kit/rigs", rig.id + ".rig")
        os.makedirs(os.path.dirname(rp), exist_ok=True)
        with open(rp, "w") as f:
            f.write(core.rig_text(rig))
        table = {"schemaVersion": 1, "id": rig.id, "moves": [core.move_row(m) for m in mv]}
        for m, row in zip(mv, table["moves"]):  # a move's own sets for its moments (SPEC §35.8)
            if m.id in d.get("move_sounds", {}):
                row["sounds"] = d["move_sounds"][m.id]
        if d.get("sounds"):
            table["sounds"] = {k: [{"sound": s, "volume": v, "pitch": p} for s, v, p in layers] for k, layers in d["sounds"].items()}
        core.write_json(os.path.join(RES, "data/awaken/kit/moves", rig.id + ".json"), table)
        if d.get("fight"):
            fight = {"schemaVersion": 1, "id": rig.id, "moves": rig.id}
            fight.update(d["fight"])
            for ph in fight["phases"]:  # the Java enums are upper case (Codecs.lenientEnum falls back silently)
                for e in ph["pool"]:
                    if "when" in e:
                        e["when"] = e["when"].upper()
            core.write_json(os.path.join(RES, "data/awaken/kit/fights", rig.id + ".json"), fight)
        ids.append(rig.id)
    if failed:
        raise SystemExit("kit checks failed:\n  " + "\n  ".join(failed))
    if not only:
        core.write_json(os.path.join(RES, "data/awaken/kit/index.json"), {"schemaVersion": 1, "kits": ids})
    print(f"kit_bodies: {len(ids)} bodies ({', '.join(ids)})")


def lab(outdir, only=None):
    from kit import lab as L
    os.makedirs(outdir, exist_ok=True)
    for d in designs(only):
        skin, glow, _ = paint(d)
        print(L.sheet(outdir, d, skin, glow))


def motion(outdir, only=None, gifs=True):
    from kit import motion as M
    for d in designs(only):
        skin, glow, _ = paint(d)
        rows = M.run(outdir, d, skin, glow, gifs)
        print(f"{d['rig'].id}: {len(rows)} sequences")
        for r in rows:
            dip = "" if r["dip"] is None else f"  dip {100 * r['dip']:.0f}%"
            print(f"  {'-'.join(r['seq']):34} {r['seconds']:5.2f} s  still {r['still_s']:4.2f} s  kinks {r['kinks']:3d} (worst {r['worst_kink']:4.1f}, strike {r['strike_snap']:4.1f})"
                  f"  held {100 * r['held']:3.0f}%  peak {r['peak_deg_s']:5d}/s{dip}")


if __name__ == "__main__":
    a = sys.argv[1:]
    if a and a[0] == "lab":
        lab(a[1], a[2:] or None)
    elif a and a[0] == "motion":
        motion(a[1], [x for x in a[2:] if x != "--no-gif"] or None, "--no-gif" not in a)
    elif a and a[0] == "silhouettes":
        from kit import silhouettes as S
        S.sheet(a[1], a[2] if len(a) > 2 else "ember_tyrant")
    elif a and a[0] == "build":
        build(None, a[1:] or None)
    else:
        print(__doc__)
