# What the tours showed, in the page's words. Read by build_page.py.
# Runs: 42840884113 (movement-now: the lane, the street, the wings) and the forest run (movement-wood), if it is in.
ROPE = [
    ("rope_wood", "WOOD:w_tp_05", None, "<b>A real oak forest.</b> @@WOOD_A@@"),
    ("rope_lane", "b_lane_tp_05", None, "<b>Giant trees, rope held the whole time.</b> One rope went out, hopped off after two ticks, and the runner landed. Then seven seconds of running with the key still down."),
    ("rope_crown", "b_lane_fp_05", None, "<b>The same lane, through your own eyes.</b> One swing, then the rope pulled 41 blocks into a tree's crown, hit it hard and left the runner standing in the leaves."),
    ("rope_fist", "b_lane_fp_00", None, "<b>On the rope in first person.</b> The fist sits in the middle of the view, over where you are going."),
    ("rope_street", "c_street_tp_01", None, "<b>A street of towers.</b> The rope at its best: fitted swings that bend round the walls. After each let-go the runner hung in the air for one to two seconds."),
]
WING = [
    ("wing_back", "d_glide_back_03", (0.18, 0.28, 0.82, 0.78), "<b>Gliding, from behind.</b> A flat sheet seen edge-on is a line."),
    ("wing_front", "e_soar_front_02", (0.16, 0.26, 0.84, 0.82), "<b>Flying, from the front.</b> One see-through sheet a side."),
    ("wing_hover", "e_soar_front_07", (0.18, 0.2, 0.82, 0.86), "<b>Hovering.</b> The same sheet, stood up in a V."),
    ("wing_fp", "e_soar_fp_04", None, "<b>Flying, through your own eyes.</b> An arm, whatever you hold, and a corner of pale blue."),
    ("wing_sovereign", "g_sovereign_front_01", (0.02, 0.16, 0.72, 0.9), "<b>The Sovereign's form.</b> The same sheet, tinted gold."),
]
TEXT = {
    "WOOD_A": "Nothing here is tall enough to swing from. Of nine presses, five missed and four pulled the runner up onto a treetop and left them standing on it.",
    "ROPE3_EV": "Measured: a player who only holds the key covers 120 blocks in 15 seconds among giant trees, 166 in the city. The 28 Oct test passed at 190 and 243 because its bot let go by hand at the right moment. In the tour, after each let-go the runner moved forward slower than a walk for one to two seconds.",
    "ROPE4_HEAD": "<b>A landing ends the chain.</b> With the key still held, nothing more is thrown until you let go and press again. In the lane of giant trees the runner threw one rope and then ran for seven seconds.",
    "ROPE4_EV": "Read in code (the held rope throws again only in the air) and seen in the tour.",
    "WING2_HEAD": "<b>A beat doesn't push down.</b> The flap turns the wing toward your head and back. Standing, that looks like a beat. Flying belly down, the wings scissor forward and never press on the air.",
    "WING2_EV": "Read in code. Press Flying in the model below with Today selected: it runs the game's own numbers.",
}

# The game as built: pictures from the game itself. The wings are from the first tour (movement-built, run 42862885263),
# the rope and the takeoff from the second (run 42872444094: forward and the rope held, no jump pressed), the Sovereign
# from the tour of its own (movement-sovereign). In the second tour a rolling storm of the Living Wilds stood behind the
# wings' studio, so its wing pictures are not used.
R2 = "RUN:movement-built-42872444094:"
BUILT = [
    ("built_wood_sling", R2 + "a_wood_tp_06", None, "<b>The rope in a real forest.</b> Only forward and the rope are held. The line is a sling: it throws the runner up and over the next treetop."),
    ("built_wood_water", R2 + "a_wood_tp_14", None, "<b>The same run, twelve seconds on.</b> Still in the air, the chain unbroken through every landing."),
    ("built_lane", R2 + "b_lane_tp_01", None, "<b>Giant trees.</b> A swing thrown from the ground with the key held. In the first tour the rope never threw again after a landing."),
    ("built_hover_back", "BUILT:s_wings_hover_back", None, "<b>The first wings, in the game.</b> Held open in a hover with <code>/awaken wings show hover</code>, at dusk, from behind. 231 boxes a wing."),
    ("built_day_open", "BUILT:s_day_open_back", None, "<b>By day.</b> The same wings, open."),
    ("built_skyborn_hover", "BUILT:e_soar_front_07", None, "<b>Skyborn, hovering in real flight.</b> A larger pair and a smaller pair under it, seen from the front."),
    ("built_glide_front", "BUILT:d_glide_front_02", None, "<b>A real glide, from the front.</b> The body lies on the air and the wings carry it."),
    ("built_skyborn_open", "BUILT:s_skyborn_open_back", None, "<b>Skyborn's two pairs, from behind.</b>"),
    ("built_takeoff", R2 + "h_takeoff_03", None, "<b>The takeoff.</b> Look up, hold sneak, jump: the wings beat you off the ground."),
    ("built_offline_rungs", "OFFLINE", None, "<b>The Sovereign's six gold wings and halo</b> (bottom row), drawn by the game's wing code outside the game: in the first tour they came out broken on the test machine's graphics, and the fix has not been pictured yet."),
]
