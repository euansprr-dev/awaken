# Which tour pictures the page uses (read by build_page.py). Paths are from the repo's root, or absolute for another
# worktree's tour. A name missing from shots/ is simply left out of the page.
J = "/Volumes/MEDIA/Awaken/.claude/worktrees/journey-kin-908d47/run-autopilot/screenshots-ci/"
M = "/Volumes/MEDIA/Awaken/.claude/worktrees/movement-plan-e38dfa/run-autopilot/screenshots-ci/"
TOURS = {
    "belfry": J + "fights-belfry-41447743108",
    "cathedral": J + "fights-cathedral-41493288235",
    "movement": M + "movement-now-42840884113",
}
# page picture -> (tour, shot, crop as fractions (x0, y0, x1, y1) or None)
CUTS = {
    "tether_now": ("belfry", "belfry_06_ringer", None),
    "sky_now": ("cathedral", "cath_09_open", None),
    "sov_now": ("movement", "g_sovereign_back_02", None),
    "wings_now": ("movement", "d_glide_back_04", None),
}
# @@FIG:key@@ -> (picture, caption)
FIGS = {
    "tether_now": ("tether_now", "<b>The D rank fight, the Ringer.</b> When it falls you get the rope, a toast and a text tip."),
    "leap_now": ("leap_now", ""),
    "wings_now": ("wings_now", "<b>Wings today.</b> The picture of the core's gift is in a tour still waiting for a machine."),
    "sky_now": ("sky_now", "<b>The A rank fight once the roof has lifted.</b> Flight is given here, while the Sky Warden is still fighting."),
    "sov_now": ("sov_now", "<b>The Sovereign's form.</b> Given five seconds after an ending."),
}
# @@FIG:key@@ -> [(picture, caption), ...] as a row of three
STRIPS = {
    "vow_strip": [],
    "wings_strip": [],
}
# @@IMG:key@@ -> the picture behind a mock-up
IMGS = {
    "vow_place": "vow_place",
    "leap_place": "leap_place",
    "wings_place": "wings_place",
    "sky_place": "sky_now",
}
TEXT = {
    "VOW_PIN_X": "58%", "VOW_PIN_Y": "42%",
    "BEAM_X": "64%", "BEAM_H": "46%",
}
