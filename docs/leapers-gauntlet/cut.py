"""Cuts the approval page's pictures from tour shots into docs/leapers-gauntlet/shots/ (1280 wide, JPEG).

    python3 docs/leapers-gauntlet/cut.py

Sources: this worktree's CI tour of today's Gauntlet (tools/autopilot/gauntlet-now.txt), the gates worktree's tour of the
Stopped Works kind (backdrops for the mock-ups), and two of The Way Up's page pictures. A missing source is skipped."""
import os
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
NOW = os.path.join(ROOT, "run-autopilot/screenshots-ci/gauntlet-now-43008682852")
SW = "/Volumes/MEDIA/Awaken/.claude/worktrees/gates-as-worlds-plan-fcb81b/run-autopilot/screenshots-ci/realm-stopped_works-42912041379"
WAY = os.path.join(ROOT, "docs/movement-unlocks/shots")
# name -> (source file, crop as fractions (x0, y0, x1, y1) or None)
CUTS = {
    "now_arrival": (NOW + "/now_04_arrival.png", None),
    "now_course": (NOW + "/now_06_course_from_doorway.png", None),
    "now_clock": (NOW + "/now_08_clock_started.png", None),
    "now_runup": (NOW + "/now_10_runup.png", None),
    "now_gift": (NOW + "/now_11g_edge.png", None),
    "now_gift_pit": (NOW + "/now_11k_edge.png", None),
    "now_thrown": (NOW + "/now_12_thrown_back.png", None),
    "now_xp": (NOW + "/now_13c_leap.png", (0.0, 0.55, 0.5, 1.0)),
    "now_end": (NOW + "/now_14e_far_landing.png", None),
    "now_far": (NOW + "/now_14b_far_landing.png", None),
    "now_out": (NOW + "/now_16_way_out.png", None),
    "sw_hall": (SW + "/stopped_works_lord.png", None),
    "sw_mouth": (SW + "/stopped_works_mouth.png", None),
    "sw_long": (SW + "/stopped_works_move1.png", None),
    "sw_pit": (SW + "/stopped_works_move3.png", None),
    "sw_floor": (SW + "/stopped_works_warden.png", None),
    "sw_room": (SW + "/stopped_works_ambush.png", None),
    "way_spire": (WAY + "/spire.jpg", None),
    "way_cabin": (WAY + "/wings_after.jpg", None),
    "gulf": (NOW + "/now_10_runup.png", (0.11, 0.22, 0.89, 1.0)),
}
os.makedirs(os.path.join(HERE, "shots"), exist_ok=True)
for name, (src, crop) in CUTS.items():
    if not os.path.exists(src):
        print("missing", src)
        continue
    im = Image.open(src).convert("RGB")
    if crop:
        w, h = im.size
        im = im.crop((int(crop[0] * w), int(crop[1] * h), int(crop[2] * w), int(crop[3] * h)))
    w, h = im.size
    if w > 1280:
        im = im.resize((1280, round(h * 1280 / w)), Image.LANCZOS)
    im.save(os.path.join(HERE, "shots", name + ".jpg"), quality=78, optimize=True)
print(len(os.listdir(os.path.join(HERE, "shots"))), "pictures")
