"""The cast profile of every power, as data (docs/power-anims/PLAN.md section 5.1): which gesture it makes in third person
(power_gestures.py) and in first person (fp_gestures.py), the accents that make it this power's, the short wind-up the server
waits (decision D3: 14 heavy powers), the screen moment at the release (powers.client.fx.CastFeel), the Lens look. Edit here;
power_gestures.write_table() writes src/main/resources/data/awaken/powers/casts.json from it (powers.fx.CastProfiles reads it).

Row fields:
  g3 / g1     the third-person / first-person gesture (a function name in power_gestures / fp_gestures), with a3 / a1 accents;
              or raw3p = (begin, hold, end, release tick) naming clips that already exist (the family clips)
  windup      server ticks between the press and the power happening (0: on the press tick; must equal the gesture's release)
  moment      the screen moment at the release tick (CastFeel.MOMENTS), endMoment at a channel's let-go
  lens        a Lens look id (assets/awaken/lens/looks_powers.json) or None
  origin      where a beam or bolt starts: palm (default) or core
  beginSound  a sound played on the press (a wind-up's first beat), "namespace:event"
Powers left out keep their family pose and CastAnim: ground_slam, meteor_dive, hover, flight (already designed; their first-person
parts come later) and the passives.
"""
from collections import OrderedDict

R = OrderedDict()


def row(power, g3=None, g1=None, a3=None, a1=None, raw3p=None, windup=0, moment="none", endMoment="none", lens=None, origin="palm",
        beginSound=None, channel=False, holdSound=None):
    R[power] = dict(g3=g3, g1=g1, a3=a3 or {}, a1=a1 or {}, raw3p=raw3p, windup=windup, moment=moment, endMoment=endMoment, lens=lens,
                    origin=origin, beginSound=beginSound, channel=channel, holdSound=holdSound)


# Kinetic: heavy, low, grounded
row("shockwave", "stomp", "stomp", windup=5, moment="thump_heavy", beginSound="awaken:power.stomp_lift")
row("haymaker", None, "punch_hold", raw3p=("hold_kinetic", None, "cast_kinetic_release", 4), endMoment="thump_light", channel=True)
row("terrain_rip", "heave", "heave", windup=6, moment="thump_light")
row("brace", "guard", "guard", windup=3, moment="thump_light")
row("thunderclap", "clap_hard", "clap", windup=4, moment="thump_heavy")
# leap keeps its family pose: its landing slam is an explicit pose that a profile would replace
# Velocity: sharp
row("dash", None, "dash", raw3p=("cast_velocity", None, None, 3), moment="push")
row("afterimage", None, "dash", {"phantom": True}, raw3p=("cast_velocity", None, None, 3), moment="push")
row("flow", "breath", "breath", moment="breath", lens="awaken:cast_flow")
row("lightning_step", "lightning_snap", "point_snap", moment="push")
# Flight and gravity: floaty
row("gravity_well", "gravity_press", "press_down", moment="lift")
row("telekinesis", "telekinesis", "reach_claw", channel=True, endMoment="thump_light")
row("gravity_flip", "flip_hands", "flip_hands", windup=5, moment="release_big")
# Energy: channelled from the core
row("nova", "nova_burst", "nova_burst", moment="release_big")
row("barrier", "barrier_push", "push2", moment="thump_light")
# Charged Shot: a ball of lightning held up between both hands, pushed out from there (origin "ball": powers.fx.CastPoints)
row("charged_shot", "lightning_ball", "lightning_ball", channel=True, endMoment="recoil_light", origin="ball",
    holdSound="awaken:power.lightning_charge")
row("overload", "overload_open", "overload_open", moment="release_big", lens="awaken:cast_overload")
row("prism_lance", "core_open", "core_open", channel=True, origin="core", moment="recoil_light", lens="awaken:cast_prism")
# Shadow: low and quiet
row("shadow_step", "dash_shadow", "dash", {"low": True}, moment="push")
row("cloak", "cloak_cross", "cross_arms", moment="breath")
row("echo_swap", "clap_hard", "clap", {"twist": 14, "light": True}, moment="push")
row("legion", "legion_rise", "legion_rise", windup=6, moment="release_big", lens="awaken:cast_legion")
# Mind: still
row("dominate", "brow_point", "brow", moment="breath")
row("read_intent", "brow_hold", "brow", {"hold": True}, moment="breath")
row("decoy", "decoy_flick", "flick", moment="none")
row("foresight", "eyes_open", "eyes_open", moment="breath", lens="awaken:cast_foresight")
# Architect: precise
row("wall", "frame_push", "push2", {"frame": True}, moment="thump_light")
row("ramp", "sweep_up", "sweep_low", moment="none")
row("floor", "press_down_hands", "press_floor", moment="thump_light")
row("cone", "roof_hands", "roof", moment="none")
row("rampart", "spread_rise", "spread_rise", windup=6, moment="thump_heavy")
row("spire", "stomp", "stomp", {"arms": "down"}, {"arms": "down"}, windup=5, moment="thump_heavy")
row("collapse", "heave", "heave", {"throw": "far"}, windup=6, moment="thump_light")
row("citadel", "citadel_slam", "slam_wind", windup=6, moment="release_big", lens="awaken:cast_citadel")
# Tech: mechanical
row("grapple", None, "aim_lock", raw3p=("cast_tech", None, None, 4), moment="recoil_light")
row("jet_burst", "kick_back", "dash", {"boots": True}, moment="push")
row("drone", "device_tap", "wrist_raise", moment="none")
row("turret", "kneel_place", "kneel_place", moment="thump_light")
row("railgun", "lock_on", "aim_lock", channel=True, endMoment="thump_light")
row("nanite_skin", "nano_tap", "cross_arms", {"tap": True}, moment="none")
row("drone_swarm", "swarm_up", "wrist_raise", {"both": True}, windup=5, moment="release_big")
# Spirit: open
row("spirit_palm", "palm_strike", "palm_strike", moment="thump_light")
row("mend", "pray_open", "pray_open", moment="lift")
row("third_eye", "brow_finger", "brow", {"third": True}, moment="breath")
row("cord_lash", "lash_flick", "flick", {"lash": True}, moment="recoil_light")
row("bulwark", "dome_open", "spread_rise", {"dome": True}, moment="lift")
row("hundred_palms", None, "flurry", raw3p=("flurry", None, None, 2), moment="push")
row("void_maw", "void_pull", "reach_claw", {"void": True}, windup=6, moment="release_big", lens="awaken:cast_void")
row("waking_dream", "dream_shape", "dream_shape", moment="breath")
row("storm_call", "sky_call", "sky_call", windup=6, moment="release_big")
row("ember_step", "ember_lunge", "dash", {"fire": True}, moment="push")
row("frost_nova", "ground_slap", "ground_slap", windup=5, moment="thump_heavy")
row("ghost_step", "float_dash", "dash", {"float": True}, moment="push")
row("twin_strike", "clap_hard", "clap", {"twin": True}, moment="lift")
# the two beams (profile rows built in phase P0)
row("beam", "core_beam", "beam", channel=True, moment="recoil_light", lens="awaken:cast_beam")
