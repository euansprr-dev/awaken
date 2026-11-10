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
    "w1": "run-autopilot/screenshots-ci/way-up-42864057722",
    "w2": "run-autopilot/screenshots-ci/way-up-42868121143",
    "w3": "run-autopilot/screenshots-ci/way-up-42874946585",
}
# page picture -> (tour, shot, crop as fractions (x0, y0, x1, y1) or None)
CUTS = {
    "built_rope": ("w3", "way_vow_08", None),
    "built_rope_card": ("w2", "way_vow_13_card", None),
    "built_beam": ("w3", "way_01_vault_told", None),
    "built_leap": ("w3", "way_10_leap_figure", None),
    "built_leap_card": ("w1", "way_12_leap_card", None),
    "built_wings": ("w2", "way_sky_10_pier_first_person", None),
    "built_wings_open": ("w2", "way_sky_12_opened", None),
    "built_sky": ("w2", "way_16_sky_strip", None),
    "built_form": ("w2", "way_18_form_prompt", None),
    "built_k": ("w2", "way_19_k_rows_all", (0.25, 0.50, 0.66, 0.99)),
    "built_tasks": ("w3", "way_03_tasks_rope_row", (0.63, 0.12, 0.99, 0.62)),
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
    "built_rope": ("built_rope", "<b>Built: the vault's mast.</b> Your own key, with \"Throw it at the mast\", sits on the mast until you throw the rope yourself."),
    "built_leap": ("built_leap", "<b>Built: the leap's moment.</b> One plain sentence, and your key under the crosshair until you press it in the air."),
    "built_wings": ("built_wings", "<b>Built: the first flight.</b> A line of rings off the pier, and your key on the way out."),
    "built_sky": ("built_sky", "<b>Built: flight's keys as a strip</b> at the top of the screen, out of the fight's way. (Shown here outside the fight, by a tour command.)"),
    "built_form": ("built_form", "<b>Built: the Sovereign's key,</b> shown the first time the gold line is full."),
    "built_k": ("built_k", "<b>Built: the K menu's rows.</b> Each rung with its keys and its mastery, and \"You haven't used it yet\" until you have."),
    "built_tasks": ("built_tasks", "<b>Built: the one row on Tasks,</b> \"Take the Ringer's chain to the vault that lit up\"."),
    "tether_now": ("tether_now", "<b>2.9 seconds after the Ringer falls, in a real D rank assessment.</b> The dark links on the floor ahead are its chain: this is the moment the rope is given. The only message on screen is Minecraft's own recipe toast."),
    "leap_now": ("leap_now", "<b>The chasm's edge in the Leaper's Gauntlet.</b> \"Your legs remember the air.\" and one line, drawn over the course's clock and another notice."),
    "wings_now": ("wings_now", "<b>A sky core gives wings.</b> A title and one line. Then nothing says how to get down."),
    "sky_now": ("sky_now", "<b>Flight given in a real A rank fight.</b> A title and a line while the Sky Warden winds up its next blow."),
    "sov_now": ("sov_now", "<b>The Sovereign's form.</b> Given five seconds after an ending."),
}
# @@FIG:key@@ -> [(picture, caption), ...] as a row of three
STRIPS = {
    "vow_strip": [
        ("built_beam", "<b>Built: a vault lights up.</b> The thin green line over the roofs is its beam, 450 blocks away. The message was still a toast here and its last words were cut; it is now a panel that shows all of them."),
        ("built_rope_card", "<b>Built: the rope thrown by hand.</b> The prompt has gone and the rope's card of keys shows for four seconds."),
        ("vow_a", "<b>Before: the vault's rope scene, out of reach in a new world.</b> The rope has just pulled the player up the pylon. Its words, \"The line is yours\", are drawn underneath the vault's own discovery card. The tour forced the two together; the scene itself holds nothing back."),
        ("vow_b", "<b>Before: then it asked you to use it,</b> \"Reach the beacon mast\", with a ring of light on the mast at the left."),
        ("tasks_vow", "<b>Before: the Tasks page at D-rank, rope in hand.</b> The card at the bottom right, \"Keep the Tether Vow\", can never be done in a new world: the vow only starts for someone without the rope."),
    ],
    "wings_strip": [
        ("built_wings_open", "<b>Built: wings open on the second press,</b> and the Wings card shows."),
        ("wings_after", "<b>Before: four seconds after the gift.</b> The scene is over and you are standing in the cabin, high in the sky, with wings you have never opened."),
        ("wings_k", "<b>Before: the K menu at C-rank with wings.</b> The next row still says the sky \"calls you to a trial\". That trial can no longer be reached."),
        ("sky_gift", "<b>Before: the Sky Warden falls.</b> \"The sky is yours.\" and four moves in one line of text."),
    ],
    "leap_strip": [
        ("built_leap_card", "<b>Built: the leap's card of keys.</b>"),
        ("spire", "<b>The place: a Tether Spire, from its plaza.</b> There is one a median 160 to 290 blocks from anywhere they grow. Its crown answers only to the rope."),
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
