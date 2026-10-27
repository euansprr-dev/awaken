"""The audit table of docs/power-anims/PLAN.md as data: one row per castable power, scored 0-3 from reading the code.

    python3 docs/power-anims/proof/audit.py            prints the markdown table (worst first) and the family means
    python3 docs/power-anims/proof/audit.py json OUT   writes the rows

Scores are a code read, not a playtest (no client was run). 0 missing or wrong, 1 placeholder or partial, 2 fits,
3 designed for this power. The rules are in PLAN.md section 2. Pose and clip columns come from PowerFxDefs and
CastClips: every cast plays its FAMILY's clip (or the shared dash, slam, flurry, hover), so most rows share one.
Columns: body (the 3P clip and the 1P arm offset, the lower of the two), origin (where the effect starts), timing
(effect, sound and camera against the body's release frame), camera, sound, look.
"""
import json
import sys

# id, family, how, pose (release[/wind]), 3P clip played, (B,O,T,C,S,L), what is wrong
ROWS = [
    ("ground_slam", "Kinetic", "press", "SLAM / SLAM_WIND", "slam_wind, slam", (2, 3, 3, 2, 2, 2), "Best cast. 1P is arms lifted then dropped: no crouch, no legs, no hands on the ground"),
    ("leap", "Kinetic", "move", "DASH", "cast_velocity", (1, 3, 2, 2, 1, 2), "A jump plays the Velocity sprint lunge; landing slam is good"),
    ("haymaker", "Kinetic", "hold", "PUNCH_WIND / PUNCH", "hold_kinetic, cast_kinetic_release", (2, 1, 2, 2, 2, 2), "Charge orb sits on the constant hand anchor, not the fist"),
    ("terrain_rip", "Kinetic", "press", "THROW", "cast_kinetic (a punch)", (1, 2, 1, 1, 1, 2), "Rips the ground with a punch; no crouch-and-heave; blocks do not come from the hands"),
    ("shockwave", "Kinetic", "press", "STOMP", "cast_kinetic (a punch)", (0, 1, 1, 1, 0, 2), "EUAN: no foot, no thump. 3P is a punch, 1P arm dips 0.12, wave starts at tick 0, sound is wind"),
    ("brace", "Kinetic", "press", "BRACE", "cast_kinetic (a punch)", (0, 3, 1, 1, 2, 1), "A guard plays a punch; the end burst is a good slam"),
    ("thunderclap", "Kinetic", "press", "CLAP", "cast_kinetic (a punch)", (1, 3, 1, 2, 2, 2), "Clap in 1P, punch in 3P; the strike does not wait for the hands to meet"),
    ("dash", "Velocity", "move", "DASH", "cast_velocity", (2, 3, 2, 2, 1, 2), "Fits. Sound is one random whoosh"),
    ("flow", "Velocity", "press", "STOMP", "cast_velocity (a sprint lunge)", (0, 3, 1, 0, 2, 1), "Time slows to a quarter and the body lunges; no client cue at all for the slow (no look, no sound ramp)"),
    ("afterimage", "Velocity", "press", "DASH", "cast_velocity", (2, 3, 1, 0, 1, 2), "No camera; clones appear with no body beat"),
    ("lightning_step", "Velocity", "move", "DASH", "cast_velocity", (2, 3, 2, 2, 2, 2), "Fits"),
    ("hover", "Flight", "toggle", "HOVER", "hover (loop)", (1, 3, 2, 0, 2, 1), "3P clip is right; 1P is a 0.03 bob, no legs"),
    ("flight", "Flight", "toggle", "SKYWARD", "cast_flight", (1, 3, 2, 1, 1, 1), "Launch sweep fits; 1P arm up only"),
    ("gravity_well", "Flight", "press", "AIM", "cast_flight (a launch sweep)", (1, 2, 1, 2, 2, 2), "Aims a well but plays a lift-off; the well appears at the target without leaving a hand"),
    ("telekinesis", "Flight", "hold", "AIM_HOLD / THROW", "hold_flight, cast_flight_release", (1, 1, 2, 1, 2, 2), "Tether starts at the constant hand anchor"),
    ("meteor_dive", "Flight", "press", "SKYWARD+SLAM_WIND / SLAM", "cast_flight, slam", (1, 3, 2, 2, 2, 2), "Strong landing; 1P has no dive pose beyond arms up"),
    ("gravity_flip", "Flight", "press", "SKYWARD", "cast_flight", (1, 3, 1, 0, 1, 1), "No camera, no body beat for the flip"),
    ("beam", "Energy", "hold", "AIM_HOLD (+AIM)", "hold_energy (hands cupped at the hip) the whole time", (0, 0, 0, 0, 1, 2), "EUAN: starts 0.5 block behind the palm (3P) and 0.7 of the half-screen below the fist (1P); 3P holds the charge pose while firing; no camera"),
    ("nova", "Energy", "press", "SKYWARD / CHARGE2", "hold_energy, cast_energy_release", (1, 2, 2, 2, 2, 2), "Arms-up burst plays a forward palm thrust; orb on the hand anchor"),
    ("barrier", "Energy", "press", "PUSH2", "cast_energy", (2, 3, 1, 0, 2, 2), "Fits; no camera"),
    ("charged_shot", "Energy", "hold", "AIM_HOLD / AIM", "hold_energy, cast_energy_release", (1, 1, 2, 1, 2, 2), "Bolt leaves the server formula for a hand, which is not the palm"),
    ("overload", "Energy", "press", "SKYWARD / CHARGE2", "hold_energy, cast_energy_release", (1, 2, 2, 2, 2, 2), "The ultimate of the core is gathered at a hand, released as a palm thrust"),
    ("prism_lance", "Energy", "hold", "AIM_HOLD (+AIM)", "hold_energy the whole time", (0, 0, 0, 1, 1, 2), "Same as the beam, on an ultimate"),
    ("shadow_step", "Shadow", "move", "DASH", "cast_velocity (not Shadow's own)", (1, 3, 2, 2, 2, 2), "Every dash plays Velocity's sprint; Shadow's crouch sweep is never used for it"),
    ("cloak", "Shadow", "press", "CONJURE", "cast_shadow", (1, 3, 1, 0, 1, 1), "Pose fits; no body beat at the vanish"),
    ("echo_swap", "Shadow", "press", "CLAP", "cast_shadow", (1, 3, 1, 2, 2, 2), "Clap in 1P only"),
    ("legion", "Shadow", "press", "CONJURE", "cast_shadow", (1, 3, 1, 2, 1, 2), "One sound for an army"),
    ("dominate", "Mind", "press", "TEMPLE", "cast_mind", (2, 3, 1, 0, 1, 2), "Ripples leave tick 0, the finger point lands at tick 9"),
    ("read_intent", "Mind", "press", "TEMPLE", "cast_mind", (2, 3, 1, 0, 1, 2), "Fits; no camera"),
    ("decoy", "Mind", "press", "THROW", "cast_mind (temple, point)", (1, 2, 1, 0, 1, 2), "A throw plays a temple-and-point"),
    ("foresight", "Mind", "press", "TEMPLE", "cast_mind", (2, 3, 1, 0, 2, 1), "A big slow-sight moment with no look and no camera"),
    ("wall", "Architect", "press", "PUSH2", "cast_architect", (2, 2, 1, 0, 1, 2), "Frame-and-set fits; blocks do not come from the hands"),
    ("ramp", "Architect", "press", "CONJURE", "cast_architect", (1, 2, 1, 0, 1, 2), "As wall; 1P one hand low"),
    ("floor", "Architect", "press", "CONJURE", "cast_architect", (1, 2, 1, 0, 1, 2), "As ramp"),
    ("cone", "Architect", "press", "RAISE", "cast_architect", (1, 2, 1, 0, 1, 2), "As ramp"),
    ("rampart", "Architect", "press", "SKYWARD", "cast_architect", (1, 2, 1, 0, 1, 2), "A fort rises with no ground beat"),
    ("spire", "Architect", "press", "STOMP", "cast_architect (frame-draw)", (0, 3, 1, 0, 2, 2), "A pillar throws you up with no stomp, no kick, no camera at all"),
    ("collapse", "Architect", "press", "THROW", "cast_architect", (1, 2, 1, 1, 2, 2), "Pose is a frame, the power is a heave"),
    ("citadel", "Architect", "press", "SKYWARD", "cast_architect", (1, 2, 1, 2, 2, 2), "Ultimate beat is good; the body only sets a frame"),
    ("grapple", "Tech", "move", "AIM", "cast_tech", (1, 1, 2, 0, 2, 1), "Cable starts at the constant hand anchor, not the wrist device"),
    ("jet_burst", "Tech", "move", "DASH", "cast_velocity", (1, 3, 2, 2, 1, 2), "Boots fire; the body plays a sprint lunge"),
    ("drone", "Tech", "press", "RAISE", "cast_tech", (1, 2, 1, 0, 1, 1), "No device beat on the wrist"),
    ("turret", "Tech", "press", "CONJURE", "cast_tech", (1, 2, 1, 0, 1, 1), "Same"),
    ("railgun", "Tech", "hold", "AIM_HOLD / AIM", "hold_tech, cast_tech_release", (1, 1, 2, 2, 2, 2), "Slug starts at the constant hand anchor"),
    ("nanite_skin", "Tech", "press", "BRACE", "cast_tech", (1, 3, 1, 0, 1, 1), "A tap-and-lock for a skin"),
    ("drone_swarm", "Tech", "press", "SKYWARD", "cast_tech", (1, 2, 1, 2, 1, 1), "Fine beat, generic body"),
    ("spirit_palm", "Spirit", "press", "PALM", "cast_spirit (palms together, arms open)", (0, 1, 1, 2, 2, 2), "A palm strike plays a prayer; the print starts at eye plus 0.6, not the palm"),
    ("mend", "Spirit", "press", "CLAP", "cast_spirit", (2, 3, 1, 0, 1, 2), "Fits"),
    ("third_eye", "Spirit", "press", "TEMPLE", "cast_spirit", (1, 3, 1, 0, 1, 1), "A prayer for a brow gesture"),
    ("cord_lash", "Spirit", "press", "THROW", "cast_spirit", (1, 1, 1, 0, 1, 1), "A lash plays a prayer; cord starts at the hand anchor"),
    ("bulwark", "Spirit", "press", "SKYWARD", "cast_spirit", (1, 3, 1, 0, 1, 2), "Fits loosely; no camera"),
    ("hundred_palms", "Spirit", "press", "FLURRY", "flurry", (2, 2, 2, 2, 2, 2), "Fits; the palms are drawn at the eye, not from the hands"),
    ("void_maw", "Spirit", "press", "AIM", "cast_spirit", (1, 2, 1, 2, 2, 2), "Prayer-and-rise for an aimed ultimate"),
    ("waking_dream", "Spirit", "press", "CONJURE / PUSH2", "cast_spirit", (1, 2, 1, 0, 2, 1), "Prayer for shaping the world"),
    ("storm_call", "Spirit", "press", "RAISE", "cast_spirit", (1, 3, 1, 1, 1, 2), "Fits loosely"),
    ("ember_step", "Spirit", "move", "DASH", "cast_velocity", (2, 3, 2, 2, 1, 2), "Fits"),
    ("frost_nova", "Spirit", "press", "STOMP", "cast_spirit (prayer)", (0, 3, 1, 1, 1, 2), "A frost burst from the ground, with a prayer and no foot"),
    ("ghost_step", "Spirit", "move", "DASH", "cast_velocity", (2, 3, 2, 2, 2, 1), "Fits"),
    ("twin_strike", "Spirit", "press", "CLAP", "cast_spirit", (1, 3, 1, 0, 1, 1), "A buff with a clap in 1P only"),
]
PASSIVE = ["danger_sense", "aura", "wall_run", "water_run"]
COLS = ("Body", "Origin", "Timing", "Camera", "Sound", "Look")


def table():
    rows = sorted(ROWS, key=lambda r: (sum(r[5]), r[0]))
    out = ["| # | Power | Family | Cast | Pose (3P clip) | B | O | T | C | S | L | Sum | What is wrong |", "|---|---|---|---|---|---|---|---|---|---|---|---|---|"]
    for i, (pid, fam, how, pose, clip, sc, note) in enumerate(rows, 1):
        out.append(f"| {i} | `{pid}` | {fam} | {how} | {pose} ({clip}) | " + " | ".join(map(str, sc)) + f" | **{sum(sc)}** | {note} |")
    return "\n".join(out)


def family_means():
    fams = {}
    for _, fam, _, _, _, sc, _ in ROWS:
        fams.setdefault(fam, []).append(sc)
    out = ["| Family | Powers | Body | Origin | Timing | Camera | Sound | Look | Sum |", "|---|---|---|---|---|---|---|---|---|"]
    for fam, rows in fams.items():
        m = [sum(r[i] for r in rows) / len(rows) for i in range(6)]
        out.append(f"| {fam} | {len(rows)} | " + " | ".join(f"{x:.1f}" for x in m) + f" | {sum(m):.1f} |")
    allm = [sum(r[5][i] for r in ROWS) / len(ROWS) for i in range(6)]
    out.append(f"| **All** | {len(ROWS)} | " + " | ".join(f"{x:.1f}" for x in allm) + f" | {sum(allm):.1f} |")
    return "\n".join(out)


def histogram():
    out = []
    for i, c in enumerate(COLS):
        n = [sum(1 for r in ROWS if r[5][i] == v) for v in range(4)]
        out.append((c, n))
    return out


if __name__ == "__main__":
    if len(sys.argv) > 2 and sys.argv[1] == "json":
        json.dump([dict(id=r[0], family=r[1], how=r[2], pose=r[3], clip=r[4], scores=r[5], note=r[6]) for r in ROWS], open(sys.argv[2], "w"), indent=1)
    else:
        print(len(ROWS), "castable powers;", len(PASSIVE), "passives (no cast):", ", ".join(PASSIVE))
        print(table())
        print()
        print(family_means())
        print()
        for c, n in histogram():
            print(c, "0/1/2/3 =", n)
