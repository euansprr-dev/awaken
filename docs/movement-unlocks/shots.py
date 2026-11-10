# Which tour pictures the page uses (read by build_page.py). Paths are from the repo's root, or absolute for another
# worktree's tour. A name missing from shots/ is simply left out of the page.
J = "/Volumes/MEDIA/Awaken/.claude/worktrees/journey-kin-908d47/run-autopilot/screenshots-ci/"
M = "/Volumes/MEDIA/Awaken/.claude/worktrees/movement-plan-e38dfa/run-autopilot/screenshots-ci/"
TOURS = {
    "belfry": J + "fights-belfry-41447743108",
    "cathedral": J + "fights-cathedral-41493288235",
    "movement": M + "movement-now-42840884113",
    "vow": "run-autopilot/screenshots-ci/unlock-vow-42851970427",
    "tether": "run-autopilot/screenshots-ci/unlock-tether-42850633106",
    "tether2": "run-autopilot/screenshots-ci/unlock-tether-42860766756",
    "leap": "run-autopilot/screenshots-ci/leaper-trial-42850633738",
    "wings": "run-autopilot/screenshots-ci/unlock-wings-42860879977",
    "sky": "run-autopilot/screenshots-ci/unlock-sky-42860942691",
}
# page picture -> (tour, shot, crop as fractions (x0, y0, x1, y1) or None)
CUTS = {
    "tether_now": ("tether2", "tether_04_chain", None),
    "leap_now": ("leap", "trial_03_across", None),
    "leap_place": ("leap", "trial_02_course", None),
    "wings_k": ("wings", "wings_20_powers", (0.25, 0.52, 0.66, 0.99)),
    "tasks_vow": ("tether", "tether_13_tasks", (0.63, 0.12, 0.99, 0.62)),
    "sky_now": ("sky", "sky_04_edge", None),
    "sky_gift": ("sky", "sky_11_gift", None),
    "sov_now": ("movement", "g_sovereign_back_02", None),
    "wings_now": ("wings", "wings_12", None),
    "wings_after": ("wings", "wings_16", None),
    "vow_a": ("vow", "vow_06", None),
    "vow_b": ("vow", "vow_12", None),
    "spire": ("vow", "spire_01_up", None),
}
# @@FIG:key@@ -> (picture, caption)
FIGS = {
    "tether_now": ("tether_now", "<b>2.9 seconds after the Ringer falls, in a real D rank assessment.</b> The dark links on the floor ahead are its chain: this is the moment the rope is given. The only message on screen is Minecraft's own recipe toast."),
    "leap_now": ("leap_now", "<b>The chasm's edge in the Leaper's Gauntlet.</b> \"Your legs remember the air.\" and one line, drawn over the course's clock and another notice."),
    "wings_now": ("wings_now", "<b>A sky core gives wings.</b> A title and one line. Then nothing says how to get down."),
    "sky_now": ("sky_now", "<b>Flight given in a real A rank fight.</b> A title and a line while the Sky Warden winds up its next blow."),
    "sov_now": ("sov_now", "<b>The Sovereign's form.</b> Given five seconds after an ending."),
}
# @@FIG:key@@ -> [(picture, caption), ...] as a row of three
STRIPS = {
    "vow_strip": [
        ("vow_a", "<b>The vault's rope scene: built, tested, and out of reach in a new world.</b> The rope has just pulled the player up the pylon. Its words, \"The line is yours\", are drawn underneath the vault's own discovery card. The tour forced the two together; the scene itself holds nothing back."),
        ("vow_b", "<b>Then it makes you use it.</b> \"Reach the beacon mast\", with a ring of light on the mast at the left."),
        ("tasks_vow", "<b>Today's Tasks page at D-rank, rope in hand.</b> The card at the bottom right, \"Keep the Tether Vow\", can never be done in a new world: the vow only starts for someone without the rope."),
    ],
    "wings_strip": [
        ("wings_after", "<b>Four seconds later.</b> The scene is over and you are standing in the cabin, high in the sky, with wings you have never opened."),
        ("wings_k", "<b>The K menu at C-rank with wings.</b> The next row still says the sky \"calls you to a trial\". That trial can no longer be reached."),
        ("sky_gift", "<b>The Sky Warden falls.</b> \"The sky is yours.\" and four moves in one line of text."),
    ],
    "leap_strip": [
        ("spire", "<b>A Tether Spire, from its plaza.</b> There is one a median 160 to 290 blocks from anywhere they grow. Its crown answers only to the rope."),
    ],
}
# @@IMG:key@@ -> the picture behind a mock-up
IMGS = {
    "vow_place": "vow_b",
    "leap_place": "leap_place",
    "wings_place": "wings_place",
    "sky_place": "sky_now",
}
TEXT = {
    "VOW_PIN_X": "28%", "VOW_PIN_Y": "25%",
    "BEAM_X": "64%", "BEAM_H": "46%",
}
