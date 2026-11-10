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
}
# page picture -> (tour, shot, crop as fractions (x0, y0, x1, y1) or None)
CUTS = {
    "tether_now": ("tether", "tether_02_ringer", None),
    "tasks_vow": ("tether", "tether_13_tasks", (0.63, 0.12, 0.99, 0.62)),
    "sky_now": ("cathedral", "cath_09_open", None),
    "sov_now": ("movement", "g_sovereign_back_02", None),
    "wings_now": ("movement", "d_glide_back_04", None),
    "vow_a": ("vow", "vow_06", None),
    "vow_b": ("vow", "vow_12", None),
    "spire": ("vow", "spire_01_up", None),
}
# @@FIG:key@@ -> (picture, caption)
FIGS = {
    "tether_now": ("tether_now", "<b>The D rank fight, the Ringer, in a real rank assessment.</b> When it falls you get the rope, a toast and a text tip."),
    "leap_now": ("leap_now", ""),
    "wings_now": ("wings_now", "<b>Wings today.</b> The picture of the core's gift is in a tour still waiting for a machine."),
    "sky_now": ("sky_now", "<b>The A rank fight once the roof has lifted.</b> Flight is given here, while the Sky Warden is still fighting."),
    "sov_now": ("sov_now", "<b>The Sovereign's form.</b> Given five seconds after an ending."),
}
# @@FIG:key@@ -> [(picture, caption), ...] as a row of three
STRIPS = {
    "vow_strip": [
        ("vow_a", "<b>The vault's rope scene: built, tested, and out of reach in a new world.</b> The rope has just pulled the player up the pylon. Its words, \"The line is yours\", are drawn underneath the vault's own discovery card. The tour forced the two together; the scene itself holds nothing back."),
        ("vow_b", "<b>Then it makes you use it.</b> \"Reach the beacon mast\", with a ring of light on the mast at the left."),
        ("tasks_vow", "<b>Today's Tasks page at D-rank, rope in hand.</b> The card at the bottom right, \"Keep the Tether Vow\", can never be done in a new world: the vow only starts for someone without the rope."),
    ],
    "leap_strip": [
        ("spire", "<b>A Tether Spire, from its plaza.</b> There is one a median 160 to 290 blocks from anywhere they grow. Its crown answers only to the rope."),
    ],
    "wings_strip": [],
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
