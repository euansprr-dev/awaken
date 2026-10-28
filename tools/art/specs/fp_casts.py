"""First-person view clips for power casts (Hands, SPEC §24; docs/power-anims/PLAN.md section 5.4).

    python3 tools/art/build_all.py fp_casts          assets/awaken/fp_clips/fp_casts.json
    python3 tools/art/specs/fp_casts.py build        the same
    python3 tools/art/specs/fp_casts.py preview DIR [clip ...]
                                                     a frame strip per clip (fp_moves.py's drawing)

Same schema and conventions as fp_moves.py (which this builds on: view space, x right, y up, z toward you, blocks; fists
key where an empty hand is on screen; events are heard through core.api.FirstPerson.listen). A cast clip may also be
"stow": its fists are drawn even when the hand holds something (the weapon is out of view while you cast).
The release tick of each cast is the tick of its CastProfile (powers.fx.CastProfiles), the same as the third-person clip's.
Edit the poses here, never the JSON.
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "../../.."))
sys.path.insert(0, HERE)
import fp_moves as F  # noqa: E402

sys.modules.setdefault("fp_casts", sys.modules[__name__])  # fp_gestures imports this module back, also when it is the script

OUT_REL = "assets/awaken/fp_clips/fp_casts.json"
fist, hand, REST, REST_FIST, OFF_OUT = F.fist, F.hand, F.REST, F.REST_FIST, F.OFF_OUT
CLIPS = {}


def clip(name, length, main=(), off=(), both=False, loop=False, events=(), note="", fists=False, to_stance=False, stow=False):
    """Registers through fp_moves.clip's checks (a one-shot ends at rest; a fist clip gives the hands back)."""
    F.clip(name, length, main=main, off=off, both=both, loop=loop, events=events, note=note, fists=fists, to_stance=to_stance)
    c = F.CLIPS.pop(name)
    c["stow"] = stow
    CLIPS[name] = c


# ---------------------------------------------------------------------------------------------------------------------
# Beam (Energy; CastProfile "beam"). The hands gather at the sternum (the core), draw in, then the main fist goes out
# along the aim at the lower right of the frame with the off fist under its wrist: the beam starts at the main fist
# (PALM, the fist plus 0.10 toward the aim; Muzzles). Ends where fp_beam_hold starts; the game plays the hold the moment
# the gather ends.
AIM_MAIN = fist(0.30, -0.20, -0.95)
AIM_OFF = fist(0.12, -0.42, -0.95)
clip("fp_beam_gather", 5, fists=True, to_stance=True, stow=True,
     main=[(0, REST_FIST, "linear"), (2, fist(0.12, -0.52, -0.62), "easeoutcubic"), (3, fist(0.10, -0.46, -0.55), "easeinoutsine"),
           (5, AIM_MAIN, "easeoutcubic")],
     off=[(0, OFF_OUT, "linear"), (2, fist(-0.12, -0.54, -0.65), "easeoutcubic"), (3, fist(-0.10, -0.48, -0.56), "easeinoutsine"),
          (5, AIM_OFF, "easeoutcubic")],
     events=[(2, "gather"), (3, "core"), (5, "hold")], note="hands gather at the sternum, the main fist goes out along the aim")


def tremble(base, k):
    return fist(base[0] + 0.005 * k, base[1] + 0.004 * k, base[2])


clip("fp_beam_hold", 4, fists=True, loop=True, stow=True,
     main=[(0, AIM_MAIN, "linear"), (1, tremble(AIM_MAIN, 1), "easeinoutsine"), (2, tremble(AIM_MAIN, -1), "easeinoutsine"),
           (3, tremble(AIM_MAIN, 0.5), "easeinoutsine"), (4, AIM_MAIN, "easeinoutsine")],
     off=[(0, AIM_OFF, "linear"), (1, tremble(AIM_OFF, -1), "easeinoutsine"), (2, tremble(AIM_OFF, 1), "easeinoutsine"),
          (3, tremble(AIM_OFF, -0.5), "easeinoutsine"), (4, AIM_OFF, "easeinoutsine")],
     note="the aim held, the hands trembling")

# ---------------------------------------------------------------------------------------------------------------------
# Stomp (Kinetic; CastProfile "shockwave"). Both hands come up into a guard as the knee lifts, flare out for balance at the
# hang, then drop hard with the plant (tick 5, 250 ms) and rebound. The foot itself is the world source in
# powers.client.fx.CastLegs; the hands are offsets, so a held weapon rides along.
UP = hand(0.0, 0.02, 0.0, 10, 0, 0)
FLARE = hand(0.10, 0.04, 0.02, 6, 0, -6)
DROP = hand(-0.02, -0.17, 0.04, -16, 0, 0)
REBOUND = hand(0.0, -0.06, 0.0, -5, 0, 0)
clip("fp_stomp", 15, both=True,
     main=[(0, REST, "linear"), (2, UP, "easeoutquad"), (3, FLARE, "easeoutsine"), (4, FLARE, "linear"), (5, DROP, "easeincubic"),
           (7, REBOUND, "easeoutsine"), (11, REST, "easeinoutsine")],
     off=[(0, REST, "linear"), (2, F.mirror(UP), "easeoutquad"), (3, F.mirror(FLARE), "easeoutsine"), (4, F.mirror(FLARE), "linear"),
          (5, F.mirror(DROP), "easeincubic"), (7, F.mirror(REBOUND), "easeoutsine"), (11, REST, "easeinoutsine")],
     events=[(2, "lift"), (5, "plant")], note="hands up, flare, drop with the plant")


# ---------------------------------------------------------------------------------------------------------------------

def document():
    clips = {}
    saved = dict(F.CLIPS)
    F.CLIPS.clear()
    F.CLIPS.update(CLIPS)
    try:
        base = F.document()
    finally:
        F.CLIPS.clear()
        F.CLIPS.update(saved)
    for name, c in CLIPS.items():
        entry = base["clips"][name]
        entry["stow"] = bool(c.get("stow"))
        clips[name] = entry
    base["clips"] = clips
    base["__comment"] = "GENERATED by tools/art/specs/fp_casts.py: edit the poses there, not this file."
    return base


def write(root=None):
    path = os.path.join(root or os.path.join(ROOT, "src/main/resources"), OUT_REL)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as fh:
        json.dump(document(), fh, separators=(",", ":"))
        fh.write("\n")
    return path


def build(out):
    """build_all.py entry."""
    import fp_gestures  # noqa: F401
    import power_level_gestures  # noqa: F401  (the ladder's level clips, SPEC 36)
    write(out.root)


def main(argv):
    import fp_gestures  # noqa: F401  (registers the per-power clips)
    import power_level_gestures  # noqa: F401  (and the ladder's level clips)
    cmd = argv[0] if argv else "build"
    if cmd == "build":
        print(f"{len(CLIPS)} clips -> {os.path.relpath(write(), ROOT)}")
        return 0
    if cmd == "preview":
        out_dir = argv[1]
        os.makedirs(out_dir, exist_ok=True)
        saved = dict(F.CLIPS)
        F.CLIPS.clear()
        F.CLIPS.update(CLIPS)
        for n in argv[2:] or list(CLIPS):
            print(F.strip(n, out_dir))
        F.CLIPS.clear()
        F.CLIPS.update(saved)
        return 0
    raise SystemExit(__doc__)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
