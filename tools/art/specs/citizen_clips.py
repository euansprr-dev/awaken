"""Carillon's citizens at work: the job clips their bodies play (docs/carillon/briefs/M3-alive.md §4).

    python3 tools/art/build_all.py citizen_clips      assets/awaken/citizen_clips.json
    python3 tools/art/specs/citizen_clips.py build    the same, plus the clipping report
    python3 tools/art/specs/citizen_clips.py preview DIR [clip ...]
                                                      a frame strip per clip (three views) and a sheet of every clip's
                                                      key poses
    python3 tools/art/specs/citizen_clips.py check    the clipping report only

Poses are written in power_casts.py's figure terms (lean, twist, tilt, rise, shift, side, ra/la, rl/ll, head_z, head_x,
ground; see its docstring) and baked with its bake(), so they mean exactly what they mean for the players' clips. The
Player Animation Library only animates players, so citizens play these on their own model
(dev.awaken.citizens.client.CitizenClips/CitizenModel): each key stores 24 channels (the root about the waist, the
torso's twist, the head's tilt added to the look, the arms with their shoulder shift, the legs) in degrees and pixels,
null where the clip leaves vanilla's pose alone (so an arm-only clip plays over the walk). Straight limbs only: no
Bendable Cuboids twin. The clip names are dev.awaken.citizens.Act in lower case. Edit the poses here, never the JSON.
"""
import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
if HERE not in sys.path:
    sys.path.insert(0, HERE)
import power_casts as P  # noqa: E402

ROOT = P.ROOT
OUT_REL = "assets/awaken/citizen_clips.json"
pose, nudge, NEUTRAL = P.pose, P.nudge, P.NEUTRAL

CLIPS = {}


def clip(name, keys, length, loop=True, note=""):
    if name in CLIPS:
        raise ValueError(f"duplicate clip {name}")
    end = None if loop else keys[-1][0]
    CLIPS[name] = P.Clip(name, keys, length, loop, end, note)


def idle(name, base, nudges, period, note):
    """A held pose that breathes: the base, then each nudge in turn, eased, looping."""
    n = len(nudges) + 1
    keys = [(0, base, "linear")] + [(period * (i + 1) / n, j, "easeinoutsine") for i, j in enumerate(nudges)]
    clip(name, keys + [(period, base, "easeinoutsine")], period, note=note)


# ---------------------------------------------------------------------------------------------------------------------
# Standing about. Straight arms can't fold, so "arms crossed" is hands clasped low in front; the weight sits on one leg.

AC = pose(ra=(-36, -42, 2), la=(-36, 42, -2), ll=(3, 0, -4), tilt=-1)
idle("arms_crossed", AC, [nudge(AC, rise=0.15, head_z=1.5), nudge(AC, rise=-0.05, tilt=0.8, head_z=-1)], 90,
     "hands clasped in front, weight on one leg, breathing")

HB = pose(lean=-3, ra=(28, 36, 0), la=(28, -36, 0))
idle("hands_behind", HB, [nudge(HB, rise=0.15), nudge(HB, lean=-0.8, head_z=1.5)], 96,
     "hands behind the back, chest out (plays over the walk too)")

LN = pose(tilt=-5, side=-0.6, ra=(-30, -44, 2), la=(-30, 44, -2), rl=(0, 0, 3), ll=(6, 0, -8), head_z=-4)
idle("lean", LN, [nudge(LN, tilt=-1.5, head_z=-2), nudge(LN, tilt=1, rise=0.1)], 120,
     "leaning in a doorway: hip out, arms folded low, head tipped")

LA = pose(ra=(0, 0, 14), la=(0, 0, -14), rl=(0, 0, 3), ll=(0, 0, -3))
clip("look_around", [(0, LA, "linear"),
                     (30, pose(LA, twist=-14, head_z=-3, tilt=-1.5), "easeinoutsine"),
                     (60, pose(LA, twist=0, rise=0.15), "easeinoutsine"),
                     (95, pose(LA, twist=16, head_z=3, tilt=1.5), "easeinoutsine"),
                     (130, LA, "easeinoutsine")], 130, note="hands loose, turning to look one way and the other")

TK = pose(lean=3, ra=(-28, -8, 6), la=(-8, 0, -8), head_z=2)
clip("talk", [(0, TK, "linear"),
              (10, pose(TK, ra=(-52, 10, 10), head_z=-2), "easeoutsine"),
              (22, pose(TK, ra=(-38, -16, 4), la=(-20, 10, -6)), "easeinoutsine"),
              (36, pose(TK, ra=(-60, 4, 14), lean=5, head_z=3), "easeoutsine"),
              (50, pose(TK, ra=(-24, -10, 6), rise=0.15), "easeinoutsine"),
              (64, TK, "easeinoutsine")], 64, note="talking with the hands")

# ---------------------------------------------------------------------------------------------------------------------
# The twelve jobs.

# Lamplighter: the pole goes up to the lamp four blocks above (both hands on it, head back), the wick touches, down.
LL_UP = pose(lean=-6, ra=(-158, -6, -6), la=(-128, 22, 4), head_x=None, rl=(0, 0, 4), ll=(4, 0, -4))
clip("lamp_light", [(0, NEUTRAL, "linear"),
                    (9, LL_UP, "easeoutcubic"),
                    (16, nudge(LL_UP, ra=(-6, 0, 0), la=(-4, 0, 0), rise=0.4), "easeinoutsine"),
                    (24, LL_UP, "easeinoutsine"),
                    (30, nudge(LL_UP, ra=(-4, 2, 0), rise=0.3), "easeinoutsine"),
                    (40, pose(ra=(-30, -6, 4), la=(-14, 10, -4)), "easeinoutsine")], 40, loop=False,
     note="raises the pole to the lamp, touches it twice, lowers it")

# Bellringer: both hands high on the rope, then pull it down to the chest with a dip of the knees.
BR_UP = pose(lean=-4, rise=0.6, ra=(-172, -4, -5), la=(-172, 4, 5))
BR_DOWN = pose(lean=12, rise=-1.2, ra=(-62, -18, 0), la=(-62, 18, 0), rl=(-6, 0, 4), ll=(-6, 0, -4))
clip("bell_pull", [(0, BR_UP, "linear"), (11, BR_DOWN, "easeinquad"), (16, nudge(BR_DOWN, rise=-0.3), "easeoutsine"),
                   (34, BR_UP, "easeinoutsine")], 34, note="hands high on the rope, a pull down to the chest")

# Porter: a crate on the back, hands on the straps at the chest, leaning into the load (over the walk).
CB = pose(lean=11, ra=(-46, -30, 0), la=(-46, 30, 0))
idle("carry_back", CB, [nudge(CB, rise=0.2, lean=1)], 16, "hands on the straps, leaning into the load")

SD_LOW = pose(lean=42, ra=(-72, -14, 0), la=(-72, 14, 0), rl=(-8, 0, 6), ll=(-8, 0, -6))
clip("set_down", [(0, CB, "linear"), (10, SD_LOW, "easeinoutsine"), (20, nudge(SD_LOW, lean=4, rise=-0.3), "easeinoutsine"),
                  (32, pose(lean=6, ra=(-20, 0, 6), la=(-20, 0, -6)), "easeinoutsine"),
                  (44, pose(lean=2), "easeinoutsine")], 44, loop=True,
     note="bends from the waist to set a load down, straightens, rests a moment")

# Stallkeeper: shutters pushed up overhead, then serving over the counter.
SH_UP = pose(lean=-4, rise=0.8, ra=(-164, -2, -10), la=(-164, 2, 10))
clip("shutter", [(0, pose(ra=(-80, 0, 0), la=(-80, 0, 0)), "linear"), (8, SH_UP, "easeoutcubic"),
                 (14, nudge(SH_UP, rise=0.4), "easeinoutsine"), (24, pose(ra=(-40, -10, 4), la=(-40, 10, -4)), "easeinoutsine")],
     24, note="pushes the shutter up overhead and lets it settle")

SV = pose(lean=7, ra=(-44, -6, 0), la=(-44, 6, 0))
clip("serve", [(0, SV, "linear"), (30, nudge(SV, rise=0.15), "easeinoutsine"),
               (44, pose(SV, ra=(-72, -34, 4), twist=-10), "easeoutsine"),
               (56, pose(SV, ra=(-66, 18, 10), twist=8), "easeinoutsine"),
               (70, SV, "easeinoutsine"),
               (100, nudge(SV, lean=2, head_z=2), "easeinoutsine"), (120, SV, "easeinoutsine")], 120,
     note="hands on the counter, a sweep over the wares, a lean in")

# Baker: a basket of loaves carried in front (over the walk).
TR = pose(lean=-2, ra=(-56, -14, 0), la=(-56, 14, 0))
idle("tray", TR, [nudge(TR, rise=0.15)], 16, "a basket of bread held in front")

# Smith: the work held on the anvil with the off hand, the hammer raised high and brought down; the strike is the
# frame at tick 12 (the client throws sparks then).
HM_UP = pose(lean=14, twist=-12, ra=(-158, -8, -4), la=(-52, 26, 0), rl=(-8, 0, 6), ll=(8, 0, -6))
HM_HIT = pose(HM_UP, lean=20, twist=-6, rise=-0.4, ra=(-46, -14, 0))
clip("hammer", [(0, HM_UP, "linear"), (8, nudge(HM_UP, ra=(-10, 0, 0), lean=-2), "easeoutsine"),
                (12, HM_HIT, "easeinquart"), (14, nudge(HM_HIT, ra=(-12, 0, 0), rise=0.2), "easeoutsine"),
                (22, HM_UP, "easeinoutsine")], 22, note="hammer high, down onto the anvil at tick 12, up again")

# Watch: the halberd held upright while walking; at ease at a post.
HW = pose(lean=-2, ra=(-14, -4, 4))
idle("halberd", HW, [nudge(HW, rise=0.1)], 20, "the halberd carried upright (over the walk)")
HS = pose(lean=-3, ra=(-10, -2, 8), la=(26, -34, 0), rl=(0, 0, 5), ll=(0, 0, -5))
idle("halberd_stand", HS, [nudge(HS, rise=0.15), nudge(HS, head_z=2, twist=4)], 110,
     "at ease: halberd planted, off hand behind the back, feet apart")

# Crier: the scroll held up in front, the free arm sweeping out to the street.
PR = pose(lean=-5, la=(-84, 18, 0), ra=(-118, -18, -18), rl=(-10, 0, 4), ll=(6, 0, -4))
clip("proclaim", [(0, PR, "linear"), (14, pose(PR, ra=(-140, 12, -34), lean=-7), "easeoutsine"),
                  (30, pose(PR, ra=(-96, 26, -22), twist=8), "easeinoutsine"),
                  (46, pose(PR, ra=(-150, -8, -28), lean=-6, rise=0.3), "easeoutsine"),
                  (66, PR, "easeinoutsine")], 66, note="the scroll up in front, the other arm sweeping out")

# Washer: bent over the water scrubbing; reaching up to peg washing on the line.
SC = pose(lean=48, ra=(-64, -10, 0), la=(-64, 10, 0), rl=(-6, 0, 6), ll=(-6, 0, -6))
clip("scrub", [(0, SC, "linear"), (6, pose(SC, ra=(-48, -14, 0), la=(-48, 14, 0)), "easeinoutsine"),
               (12, SC, "easeinoutsine"), (18, pose(SC, ra=(-52, 0, 0), la=(-52, 0, 0), lean=50), "easeinoutsine"),
               (24, SC, "easeinoutsine")], 24, note="bent over the water, scrubbing back and forth")
HG = pose(lean=-6, rise=0.7, ra=(-166, -10, -6), la=(-150, 14, 6))
clip("hang", [(0, HG, "linear"), (10, pose(HG, ra=(-150, -14, -4), la=(-168, 8, 8)), "easeinoutsine"),
              (20, HG, "easeinoutsine"), (30, pose(lean=10, ra=(-60, -8, 0), la=(-60, 8, 0)), "easeinoutsine"),
              (40, HG, "easeinoutsine")], 40, note="pegging washing on the line, then down to the basket")

# Mason: bent to the wall, the trowel working, a brick in the other hand.
TW = pose(lean=30, ra=(-72, -16, 0), la=(-58, 24, 0), rl=(-6, 0, 5), ll=(4, 0, -5))
clip("trowel", [(0, TW, "linear"), (8, pose(TW, ra=(-54, 14, 0)), "easeinoutsine"), (14, TW, "easeinoutsine"),
                (20, pose(TW, ra=(-80, -4, 0), lean=33), "easeinoutsine"), (28, TW, "easeinoutsine")], 28,
     note="spreading mortar, a brick ready in the other hand")

# Runner: the courier's run leans in (vanilla swings the arms and legs).
RN = pose(lean=12)
idle("run", RN, [nudge(RN, lean=1)], 12, "leaning into a run (over the walk)")

# Archivist: reading a book held up, a page turned now and then.
RD = pose(ra=(-58, -16, 0), la=(-58, 26, 0), head_x=None)
clip("read", [(0, RD, "linear"), (60, nudge(RD, rise=0.1), "easeinoutsine"),
              (70, pose(RD, la=(-66, -6, 0)), "easeoutsine"), (78, RD, "easeinoutsine"), (130, RD, "linear")], 130,
     note="reading a book held up, turning a page")

# Boatman, miller: hauling a rope hand over hand, leaning back, braced.
HL_OUT = pose(lean=-8, ra=(-84, -6, 0), la=(-84, 6, 0), rl=(-16, 0, 4), ll=(12, 0, -4))
HL_IN = pose(HL_OUT, lean=-20, ra=(-34, -16, 0), la=(-34, 16, 0))
clip("haul", [(0, HL_OUT, "linear"), (16, HL_IN, "easeinoutsine"), (36, HL_OUT, "easeinoutsine")], 36,
     note="hauling a rope in, leaning back, braced")

# Boatmen at their berths (M4 B3b, Euan: "what are these people supposed to be doing?"): a line coiled over the forearm,
# the boat bailed with a bucket, the nets mended, the boat fended off the quay with a pole.
CO = pose(lean=6, la=(-74, 22, 0), ra=(-82, -18, 0), head_z=3)
clip("coil", [(0, CO, "linear"), (6, pose(CO, ra=(-118, 8, -22)), "easeinoutsine"), (12, pose(CO, ra=(-70, 28, 6)), "easeinoutsine"),
              (18, pose(CO, ra=(-96, -24, 10), rise=0.1), "easeinoutsine"), (24, CO, "easeinoutsine")], 24,
     note="a line coiled loop by loop over the forearm")
BA_DN = pose(lean=52, ra=(-44, -10, 0), la=(-40, 12, 0), rl=(-12, 0, 6), ll=(-12, 0, -6))
BA_UP = pose(lean=24, twist=-30, ra=(-124, -22, -18), la=(-104, 8, -10), rl=(-8, 0, 6), ll=(-8, 0, -6))
clip("bail", [(0, BA_DN, "linear"), (8, nudge(BA_DN, lean=4, ra=(6, 0, 0)), "easeinoutsine"), (16, BA_UP, "easeoutsine"),
              (20, nudge(BA_UP, ra=(-6, 0, 0)), "easeoutsine"), (30, BA_DN, "easeinoutsine")], 30,
     note="bent over the boat, a bucket scooped and flung over the side")
MD = pose(lean=24, ra=(-60, -14, 0), la=(-60, 14, 0), rl=(-4, 0, 4), ll=(4, 0, -4), head_z=2)
clip("mend", [(0, MD, "linear"), (7, pose(MD, ra=(-74, 6, 0), la=(-54, 20, 0)), "easeinoutsine"), (14, MD, "easeinoutsine"),
              (21, pose(MD, ra=(-54, -20, 0), la=(-72, -4, 0), head_z=-2), "easeinoutsine"), (28, MD, "easeinoutsine"),
              (60, nudge(MD, lean=-6, rise=0.1), "easeinoutsine"), (70, MD, "easeinoutsine")], 70,
     note="a net held at the waist, the needle worked through, a stretch now and then")
PO_A = pose(lean=18, twist=-22, ra=(-62, -12, 0), la=(-94, 22, 0), rl=(-22, 0, 4), ll=(16, 0, -4))
PO_B = pose(PO_A, lean=34, rise=-0.3, ra=(-42, -8, 0), la=(-72, 26, 0))
clip("pole", [(0, PO_A, "linear"), (14, PO_B, "easeinoutsine"), (20, nudge(PO_B, lean=2), "easeinoutsine"), (36, PO_A, "easeinoutsine")],
     36, note="leaning on a pole to fend the boat off the quay, or push it out")

# Children at play: hopping, arms flung up.
PL_A = pose(rise=0, ra=(-30, 0, 14), la=(-30, 0, -14))
PL_B = pose(rise=2.6, ra=(-160, 0, -34), la=(-150, 0, 34), rl=(-24, 0, 6), ll=(12, 0, -6))
PL_C = pose(rise=2.4, ra=(-150, 0, -30), la=(-160, 0, 30), rl=(12, 0, 6), ll=(-24, 0, -6))
clip("play", [(0, PL_A, "linear"), (5, PL_B, "easeoutquad"), (10, PL_A, "easeinquad"), (15, PL_C, "easeoutquad"),
              (20, PL_A, "easeinquad"), (40, pose(PL_A, twist=12), "easeinoutsine")], 40,
     note="hopping with arms flung up, then a breath")

# ---------------------------------------------------------------------------------------------------------------------
# Reactions and events.

WV = pose(ra=(-162, 0, -36), head_z=-4)
clip("wave", [(0, NEUTRAL, "linear"), (6, WV, "easeoutcubic"), (10, nudge(WV, ra=(0, 0, 18)), "easeinoutsine"),
              (14, WV, "easeinoutsine"), (18, nudge(WV, ra=(0, 0, 18)), "easeinoutsine"), (22, WV, "easeinoutsine"),
              (32, NEUTRAL, "easeinoutsine")], 32, loop=False, note="a hand raised and waved twice")
clip("nod", [(0, NEUTRAL, "linear"), (5, pose(lean=4, head_x=None, head_z=2), "easeoutsine"),
             (8, NEUTRAL, "easeinoutsine"), (18, NEUTRAL, "linear")], 18, loop=False, note="a nod")
# The head tilt for the nod: a lean plus the head's own tilt (head_x stays the look)
PS = pose(lean=8, ra=(-96, -6, 0), la=(-120, 30, -14), rl=(-10, 0, 4), ll=(8, 0, -4))
clip("point_shout", [(0, PS, "linear"), (8, nudge(PS, lean=4, rise=0.3), "easeoutsine"), (16, PS, "easeinoutsine"),
                     (30, nudge(PS, ra=(-6, 4, 0)), "easeinoutsine"), (40, PS, "easeinoutsine")], 40,
     note="pointing after someone, a hand to the mouth, shouting")
BP_L = pose(lean=10, twist=-38, ra=(-58, -24, 0), la=(-58, 24, 0), rl=(0, 0, 6), ll=(0, 0, -6))
BP_R = pose(BP_L, twist=38)
clip("bucket_pass", [(0, BP_L, "linear"), (14, BP_R, "easeinoutsine"), (20, nudge(BP_R, lean=4), "easeinoutsine"),
                     (34, BP_L, "easeinoutsine"), (40, BP_L, "linear")], 40, note="a bucket taken from one side, passed to the other")
HU = pose(lean=14, tilt=-4, ra=(-32, -46, 0), la=(6, 0, -6), head_z=-5)
idle("hurt", HU, [nudge(HU, lean=2, rise=-0.2)], 30, "hunched over, a hand to the side (over the walk)")
CH = pose(rise=1.6, ra=(-170, 0, -26), la=(-170, 0, 26), lean=-4)
clip("cheer", [(0, pose(ra=(-60, 0, -10), la=(-60, 0, 10)), "linear"), (6, CH, "easeoutquad"),
               (12, pose(CH, rise=0.2), "easeinquad"), (18, CH, "easeoutquad"), (24, pose(ra=(-60, 0, -10), la=(-60, 0, 10)), "easeinquad")],
     24, note="both arms up, jumping")
TR_RUN = pose(lean=24, ra=(42, 0, 14), la=(42, 0, -14))
idle("thief_run", TR_RUN, [nudge(TR_RUN, lean=2, rise=0.2)], 10, "a low dash, arms back (over the run)")
SU = pose(lean=6, ra=(-170, 0, -22), la=(-170, 0, 22), head_x=None)
idle("surrender", SU, [nudge(SU, rise=-0.2, ra=(4, 0, 0), la=(4, 0, 0))], 30, "hands up")
TP = pose(lean=-4, ra=(-170, 0, -24), la=(-150, 0, 30))
clip("trapped", [(0, TP, "linear"), (5, pose(TP, ra=(-150, 0, -32), la=(-172, 0, 22)), "easeinoutsine"),
                 (10, TP, "easeinoutsine")], 10, note="waving frantically with both arms")

# (SPEC §28.7) A village's troubles and festivals: the ring dance round the fire, the reunion, a parent's grief.
DA_A = pose(rise=0.3, twist=-5, ra=(-28, 0, 66), la=(-28, 0, -66), rl=(-22, 0, 4), ll=(8, 0, -2), lean=-2)
DA_B = pose(rise=1.5, twist=0, ra=(-40, 0, 72), la=(-40, 0, -72), rl=(2, 0, 5), ll=(2, 0, -5), lean=-4)
DA_C = pose(rise=0.3, twist=5, ra=(-28, 0, 66), la=(-28, 0, -66), rl=(8, 0, 2), ll=(-22, 0, -4), lean=-2)
clip("dance", [(0, DA_A, "linear"), (5, DA_B, "easeoutquad"), (10, DA_C, "easeinquad"), (15, DA_B, "easeoutquad"),
               (20, DA_A, "easeinquad")], 20, note="the ring dance: hands out to the neighbours', a hop and a kick each beat")
EM = pose(lean=9, ra=(-82, -38, 0), la=(-82, 38, 0), head_z=-5)
idle("embrace", EM, [nudge(EM, lean=2, rise=0.1, twist=-5), nudge(EM, lean=1, twist=5)], 44,
     "holding someone close: arms round them, leaning in, rocking a little")
WP = pose(lean=14, ra=(-148, -34, 0), la=(-148, 34, 0), rl=(0, 0, 2), ll=(4, 0, -3))
clip("weep", [(0, WP, "linear"), (4, nudge(WP, rise=-0.25, lean=2), "easeinoutsine"), (8, WP, "easeinoutsine"),
              (12, nudge(WP, rise=-0.25, lean=2), "easeinoutsine"), (16, WP, "easeinoutsine"),
              (40, nudge(WP, lean=-4, ra=(18, 0, 0), la=(18, 0, 0)), "easeinoutsine"), (52, WP, "easeinoutsine")], 52,
     note="hands over the face, shoulders shaking, then a breath")


# ---------------------------------------------------------------------------------------------------------------------
# Export

CHANNELS = 24
MAP = {  # (bone, target, axis) -> channel
    ("body", "rotation", 0): 0, ("body", "rotation", 2): 1,
    ("body", "position", 0): 2, ("body", "position", 1): 3, ("body", "position", 2): 4,
    ("torso", "rotation", 1): 5,
    ("head", "rotation", 0): 6, ("head", "rotation", 2): 7,
    ("right_arm", "rotation", 0): 8, ("right_arm", "rotation", 1): 9, ("right_arm", "rotation", 2): 10,
    ("right_arm", "position", 0): 11, ("right_arm", "position", 2): 12,
    ("left_arm", "rotation", 0): 13, ("left_arm", "rotation", 1): 14, ("left_arm", "rotation", 2): 15,
    ("left_arm", "position", 0): 16, ("left_arm", "position", 2): 17,
    ("right_leg", "rotation", 0): 18, ("right_leg", "rotation", 1): 19, ("right_leg", "rotation", 2): 20,
    ("left_leg", "rotation", 0): 21, ("left_leg", "rotation", 1): 22, ("left_leg", "rotation", 2): 23,
}


def export(c):
    """One clip: every key's 24 channels; channels the clip never moves from neutral stay null (vanilla's pose)."""
    moving = c.channels()
    keys = []
    for i, (t, baked, e) in enumerate(c.baked()):
        v = [None] * CHANNELS
        for bone, targets in moving.items():
            for target in targets:
                vec = baked[bone][target]
                for axis in range(3):
                    ch = MAP.get((bone, target, axis))
                    if ch is None or vec[axis].off:
                        continue
                    v[ch] = round(vec[axis].c, 3)
        key = {"t": round(t, 3), "v": v}
        if i > 0 and e != "linear":
            key["e"] = e
        keys.append(key)
    return {"length": c.length, "loop": c.loop, "keys": keys}


def document():
    return {"format": 1, "__comment": "generated by tools/art/specs/citizen_clips.py; edit the poses there",
            "clips": {name: export(c) for name, c in CLIPS.items()}}


def write(root=None):
    path = os.path.join(root or os.path.join(ROOT, "src/main/resources"), OUT_REL)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(document(), f, separators=(",", ":"))
        f.write("\n")
    return path


def build(out):
    write(out.root)


def check(limit=1.25, quiet=False):
    """Clips whose limbs pass through the body (the same penetration test as the players' clips)."""
    bad = []
    for name, c in CLIPS.items():
        worst, at, where = 0.0, 0.0, ""
        t = 0.0
        while t <= c.length + 1e-6:
            d, w = P.penetration(c.sample(t))
            if d > worst:
                worst, at, where = d, t, w
            t += 1.0
        if worst > limit:
            bad.append(f"{name}: {where} by {worst:.2f} px at tick {at:g}")
        elif not quiet:
            print(f"{name}: ok ({worst:.2f} px {where})")
    return bad


def sheet(out_dir, px=4.0):
    from PIL import Image, ImageDraw
    views = (("front", -35.0, 8.0), ("side", -90.0, 6.0))
    rows = []
    names = list(CLIPS)
    for vname, yaw, pitch in views:
        bounds = P.strip_bounds([CLIPS[n] for n in names], yaw, pitch)
        cells = []
        for n in names:
            c = CLIPS[n]
            ts = sorted({0.0, round(c.length * 0.33), round(c.length * 0.66)})
            for t in ts:
                cells.append((f"{n} t{t:g}", P.frame_image(c, t, yaw, pitch, px, bounds)))
        rows.append((vname, cells))
    w = max(im.width for _, r in rows for _, im in r)
    h = max(im.height for _, r in rows for _, im in r)
    per = 12
    lines = []
    for label, cells in rows:
        for k in range(0, len(cells), per):
            lines.append((label, cells[k:k + per]))
    img = Image.new("RGBA", (70 + per * w, len(lines) * (h + 14)), (20, 22, 28, 255))
    dr = ImageDraw.Draw(img)
    for j, (label, cells) in enumerate(lines):
        y = j * (h + 14)
        dr.text((6, y + h // 2), label, fill=(230, 232, 240, 255))
        for i, (cap, im) in enumerate(cells):
            img.paste(im, (70 + i * w, y + 14))
            dr.text((70 + i * w + 2, y + 1), cap[:22], fill=(200, 205, 215, 255))
    path = os.path.join(out_dir, "citizen_clips.png")
    img.save(path)
    return path


def main(argv):
    cmd = argv[0] if argv else "build"
    if cmd == "build":
        path = write()
        print(f"{len(CLIPS)} clips -> {os.path.relpath(path, ROOT)}")
        for b in check(quiet=True):
            print("CLIPPING", b)
        return 0
    if cmd == "check":
        bad = check()
        print("ok" if not bad else f"{len(bad)} clips clip")
        return 1 if bad else 0
    if cmd == "preview":
        out_dir = argv[1]
        os.makedirs(out_dir, exist_ok=True)
        for n in argv[2:] or []:
            print(P.strip(CLIPS[n], out_dir))
        print(sheet(out_dir))
        return 0
    raise SystemExit(__doc__)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
