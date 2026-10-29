"""Generates progression content JSON (Truths, Titles, Latents, Paths, Directives, Drills).
Edit here, then run: python3 tools/content/progression_content.py
Counter ids referenced here must be counted by the owning module (see progression/Counters.java and SPEC)."""
import json, os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "../../src/main/resources/data/awaken/awaken")


def c(counter, value):
    return {"type": "counter", "counter": "awaken:" + counter, "value": value}


def flag(name):
    return {"type": "flag", "flag": "awaken:" + name}


def all_of(*cs):
    return {"type": "all", "of": list(cs)}


def any_of(*cs):
    return {"type": "any", "of": list(cs)}


def perk(pid, amount=1.0):
    return {"id": "awaken:" + pid, "amount": amount}


def write(folder, name, data):
    d = os.path.join(ROOT, folder)
    os.makedirs(d, exist_ok=True)
    with open(os.path.join(d, name + ".json"), "w") as f:
        json.dump(data, f, indent=2)


# ---------------------------------------------------------------------------------------------------------------------
# TRUTHS: statements about who you're becoming. Condition is absolute (lifetime / streak counters).
TRUTHS = {
    "i_do_not_retreat": ("I do not retreat.", "Clear five floors in a row without extracting early.",
                         c("floor_streak_no_early_extract", 5), {"perks": [perk("damage_above_half_hp", 0.15)]}, "E"),
    "i_see_what_others_miss": ("I see what others miss.", "Find ten secrets.", c("secrets_found", 10),
                               {"perks": [perk("secret_glow_range", 6)]}, "E"),
    "i_protect_what_is_mine": ("I protect what's mine.", "Hold your Hearth through three breaks.",
                               c("breaks_held_at_hearth", 3), {"perks": [perk("hearth_shield")]}, "D"),
    "i_move_like_water": ("I move like water.", "Chain ten dashes without touching the ground.",
                          c("dash_chain_airborne_best", 10), {"perks": [perk("flow_duration", 0.3)]}, "E"),
    "nothing_can_touch_me": ("Nothing can touch me.", "Take no damage on a boss floor.", c("boss_floors_no_damage", 1),
                             {"perks": [perk("cheat_death_per_gate")]}, "D"),
    "i_keep_my_word": ("I keep my word.", "Finish every Directive you accept for seven in a row.",
                       c("directives_kept_streak", 7), {"perks": [perk("extra_directive_slot")]}, "E"),
    "i_am_never_caught": ("I am never caught.", "Clear a gate without being hit.", c("gates_no_hit", 1),
                          {"perks": [perk("stealth", 0.4)]}, "C"),
    "my_body_is_a_weapon": ("My body is a weapon.", "Defeat 300 foes bare-handed.", c("kills_unarmed", 300),
                            {"stat": "STR", "stat_amount": 3}, "E"),
    "i_go_deeper": ("I go deeper.", "Reach stratum 5 of the Descent.", c("depth_record", 5),
                    {"perks": [perk("fall_resist", 0.5)]}, "C"),
    "i_am_awake_in_my_dreams": ("I am awake in my dreams.", "Become lucid in five dreams.", c("lucid_dreams", 5),
                                {"perks": [perk("lucidity_start", 20)]}, "E"),
    "i_am_more_than_my_body": ("I am more than my body.", "Spend ten minutes outside your body.", c("veil_ticks", 12000),
                               {"perks": [perk("cord_length", 64)]}, "D"),
    "i_face_my_fears": ("I face my fears.", "Defeat three nightmares.", c("nightmares_beaten", 3),
                        {"stat": "SPI", "stat_amount": 3}, "D"),
    "i_build_what_i_need": ("I build what I need.", "Place 2,000 blocks.", c("blocks_placed", 2000),
                            {"stat_points": 3}, "E"),
    "i_command_the_fallen": ("I command the shadows.", "Use shadow powers two hundred times.", c("power_uses/shadow", 200),
                             {"perks": [perk("summon_strength", 0.15)]}, "C"),
    "i_understand_the_machines": ("I understand the machines.", "Build five Precursor machines.", c("machines_built", 5),
                                  {"stat": "INT", "stat_amount": 3}, "C"),
    "i_am_still": ("I am still.", "Meditate for twenty minutes in total.", c("meditate_ticks", 24000),
                   {"perks": [perk("energy_regen", 0.2)]}, "E"),
    "i_am_fast": ("I am fast.", "Sprint 50 kilometres.", c("sprint_cm", 5_000_000), {"perks": [perk("move_speed", 0.05)]}, "E"),
    "i_endure": ("I endure.", "Take 5,000 damage and keep going.", c("damage_taken", 5000), {"perks": [perk("max_health", 4)]}, "D"),
    "i_strike_first": ("I strike first.", "Defeat 1,000 foes.", c("kills", 1000), {"perks": [perk("crit_chance", 0.05)]}, "D"),
    "i_bend_the_forces": ("I bend the forces.", "Trigger 25 force interactions.", c("force_interactions", 25),
                          {"perks": [perk("force_amp", 0.2)]}, "C"),
    "i_finish_what_i_start": ("I finish what I start.", "Clear 25 gates.", c("gates_cleared", 25), {"stat_points": 5}, "C"),
    "the_deep_does_not_scare_me": ("The deep does not scare me.", "Reach stratum 8.", c("depth_record", 8),
                                   {"perks": [perk("explosion_resist", 0.3)]}, "A"),
    "i_am_the_storm": ("I am the storm.", "Spend fifteen minutes in Flow.", c("flow_ticks", 18000),
                       {"perks": [perk("cooldown_reduction", 0.1)]}, "B"),
    "i_choose_my_path": ("I choose my path.", "Complete 30 Daily Regimens.", c("regimens_done", 30), {"stat_points": 6}, "B"),
}
EMERGENT = {
    "i_never_flee": ("I never flee.", "You've never fled a fight.", c("floor_streak_no_early_extract", 12), c("floor_streak_no_early_extract", 8),
                     {"perks": [perk("damage_above_half_hp", 0.1)], "stat": "VIT", "stat_amount": 2}),
    "i_walk_alone": ("I walk alone.", "You've cleared many gates on your own.", c("gates_solo_no_echo", 15), c("gates_solo_no_echo", 10),
                     {"stat": "AGI", "stat_amount": 4}),
    "i_listen_to_the_dead": ("I listen to the dead.", "You keep stopping to talk to spirits.", c("spirits_spoken", 20), c("spirits_spoken", 12),
                             {"perks": [perk("cord_length", 96)]}),
}
for key, (text, hint, cond, reward, rank) in TRUTHS.items():
    write("truth", key, {"text": text, "hint": hint, "condition": cond, "reward": reward, "min_rank": rank})
for key, (text, hint, cond, emergent, reward) in EMERGENT.items():
    write("truth", key, {"text": text, "hint": hint, "condition": cond, "emergent": emergent, "reward": reward})

# ---------------------------------------------------------------------------------------------------------------------
# TITLES: feats. Hidden by default (never shown before earned).
TITLES = {
    "fell_further": ("Fell Further", "Survived a 200-block fall.", c("fall_survived_max", 200), [perk("fall_resist", 0.3)]),
    "unwitnessed": ("Unwitnessed", "Cleared a gate without taking a hit.", c("gates_no_hit", 1), [perk("stealth", 0.25)]),
    "wolf_friend": ("Wolf Friend", "Tamed ten wolves.", c("wolves_tamed", 10), [perk("wolf_friend")]),
    "gatebreaker": ("Gatebreaker", "Cleared a gate before its omen revealed itself.", flag("gate_cleared_unscouted_fast"), [perk("loot_bonus", 0.1)]),
    "first_blood": ("First Blood", "Cleared your first gate.", c("gates_cleared", 1), [perk("xp_bonus", 0.02)]),
    "centurion": ("Centurion", "Defeated 100 foes.", c("kills", 100), [perk("crit_chance", 0.02)]),
    "thousand_hands": ("A Thousand Hands", "Defeated 1,000 foes.", c("kills", 1000), [perk("crit_chance", 0.04)]),
    "barehanded": ("Barehanded", "Defeated 100 foes without a weapon.", c("kills_unarmed", 100), [perk("damage_above_half_hp", 0.05)]),
    "deep_diver": ("Deep Diver", "Reached stratum 3.", c("depth_record", 3), [perk("fall_resist", 0.2)]),
    "abyssal": ("Abyssal", "Reached stratum 7.", c("depth_record", 7), [perk("explosion_resist", 0.2)]),
    "dreamer": ("Dreamer", "Had ten dreams.", c("dreams", 10), [perk("lucidity_start", 10)]),
    "lucid": ("Lucid", "Became lucid for the first time.", c("lucid_dreams", 1), [perk("lucidity_start", 5)]),
    "fear_eater": ("Fear Eater", "Defeated a nightmare.", c("nightmares_beaten", 1), [perk("max_health", 2)]),
    "untethered": ("Untethered", "Stepped out of your body.", c("veil_ticks", 20), [perk("cord_length", 16)]),
    "medium": ("Medium", "Spoke with ten spirits.", c("spirits_spoken", 10), [perk("secret_glow_range", 3)]),
    "shadow_lord": ("Shadow Lord", "Used shadow powers five hundred times.", c("power_uses/shadow", 500), [perk("summon_strength", 0.1)]),
    "tinkerer": ("Tinkerer", "Scanned five broken machines.", c("machines_scanned", 5), [perk("cooldown_reduction", 0.03)]),
    "architect": ("Architect", "Placed 500 blocks mid-fight.", c("blocks_placed_in_combat", 500), [perk("explosion_resist", 0.15)]),
    "stormcaller": ("Stormcaller", "Spent five minutes in Flow.", c("flow_ticks", 6000), [perk("flow_duration", 0.1)]),
    "chain_reaction": ("Chain Reaction", "Triggered ten force interactions.", c("force_interactions", 10), [perk("force_amp", 0.1)]),
    "twisted": ("Twisted", "Survived five gate twists.", c("twists_seen", 5), [perk("loot_bonus", 0.05)]),
    "marathon": ("Marathon", "Sprinted 42 kilometres.", c("sprint_cm", 4_200_000), [perk("move_speed", 0.03)]),
    "stonebreaker": ("Stonebreaker", "Mined 1,000 ores.", c("ores_mined", 1000), [perk("loot_bonus", 0.05)]),
    "persistent": ("Persistent", "Died twenty times and kept coming back.", c("deaths", 20), [perk("max_health", 2)]),
    "keeper_of_promises": ("Keeper of Promises", "Kept seven Directives in a row.", c("directives_kept_streak", 7), [perk("xp_bonus", 0.03)]),
    "the_regimen": ("The Regimen", "Completed ten Daily Regimens.", c("regimens_done", 10), [perk("energy_regen", 0.1)]),
    "sovereign": ("Sovereign", "Reached the highest rank.", {"type": "rank", "rank": "SOVEREIGN"}, [perk("xp_bonus", 0.1)]),
}
for key, (name, desc, cond, perks) in TITLES.items():
    write("title", key, {"name": name, "description": desc, "condition": cond, "perks": perks})

# ---------------------------------------------------------------------------------------------------------------------
# LATENTS: hidden abilities from unusual actions. Flags are set by the owning module when the moment happens.
LATENTS = {
    "second_wind": ("Second Wind", "Sprinting on an empty stomach taught your body to find more.",
                    flag("latent/sprint_starving"), {"perks": [perk("second_wind")]}),
    "clarity": ("Clarity", "You meditated above the clouds at dawn. Things are sharper now.",
                flag("latent/dawn_meditation_above_clouds"), {"stat": "PER", "stat_amount": 5}),
    "stormcall": ("Stormcall", "Lightning found the copper in your hand, and you kept it.",
                  flag("latent/struck_holding_copper"), {"power": "awaken:storm_call"}),
    "deep_dreamer": ("Deep Dreamer", "You slept in the Deep and dreamed of what lies below.",
                     flag("latent/slept_in_descent"), {"perks": [perk("lucidity_start", 15)]}),
    "blast_skin": ("Blast Skin", "A creeper went off in your face at full health. You got up.",
                   flag("latent/creeper_point_blank_survived"), {"perks": [perk("explosion_resist", 0.25)]}),
    "night_eyes": ("Night Eyes", "Three nights without sleep in the dark taught your eyes.",
                   c("latent/nights_awake_underground", 3), {"stat": "PER", "stat_amount": 3}),
    "featherfall": ("Featherfall", "You fell from the sky into water from the build limit.",
                    flag("latent/fell_from_height_limit_into_water"), {"perks": [perk("fall_resist", 0.5)]}),
    "beast_tongue": ("Beast Tongue", "A tamed wolf died protecting you. Animals know you now.",
                     flag("latent/wolf_died_protecting"), {"perks": [perk("wolf_friend")]}),
    "iron_lungs": ("Iron Lungs", "You stayed under water until you nearly drowned, three times.",
                   c("latent/near_drowned", 3), {"stat": "VIT", "stat_amount": 4}),
    "ember_heart": ("Ember Heart", "You walked through lava and lived.", flag("latent/survived_lava"),
                    {"power": "awaken:ember_step"}),
    "frost_blood": ("Frost Blood", "You froze nearly solid in powder snow and climbed out.", flag("latent/escaped_freezing"),
                    {"power": "awaken:frost_nova"}),
    "echo_whisper": ("Echo Whisper", "A spirit told you its name, and you remembered it.", flag("latent/spirit_named"),
                     {"perks": [perk("cord_length", 64)]}),
    "lucky_star": ("Lucky Star", "You found three twists in three gates in a row.", flag("latent/three_twists_row"),
                   {"perks": [perk("loot_bonus", 0.15)]}),
    "ghost_step": ("Ghost Step", "You walked the Silent Halls without a sound.", flag("latent/silent_halls_unheard"),
                   {"power": "awaken:ghost_step"}),
    "dream_hands": ("Dream Hands", "You manifested something in a dream and it stayed for a moment when you woke.",
                    flag("latent/manifest_woke"), {"perks": [perk("lucidity_start", 10)]}),
    "pathfinder": ("Pathfinder", "You found a vision's place on the first night.", flag("latent/vision_found_same_day"),
                   {"stat": "SPI", "stat_amount": 3}),
    "unbroken": ("Unbroken", "You cleared a Red Gate.", flag("latent/red_gate_cleared"), {"stat_points": 5}),
    "twin_soul": ("Twin Soul", "You survived a Double Gate.", flag("latent/double_gate_cleared"), {"power": "awaken:twin_strike"}),
    "gravity_kiss": ("Gravity's Kiss", "You fell up in the Inverted Sea and landed on your feet.", flag("latent/inverted_landing"),
                     {"power": "awaken:gravity_flip"}),
    "sun_eyes": ("Sun Eyes", "You stared at the noon sun from a mountaintop for a full minute.", flag("latent/stared_at_sun"),
                 {"perks": [perk("crit_chance", 0.03)]}),
}
for key, (name, desc, cond, reward) in LATENTS.items():
    write("latent", key, {"name": name, "description": desc, "condition": cond, "reward": reward})

# ---------------------------------------------------------------------------------------------------------------------
# PATHS (Second Awakening). Scoring weights per counter (normalised per hour). Evidence uses %d.
PATHS = {
    "shadowbinder": ("Shadowbinder", "You command shadows. The soldiers you raise hit harder and stay longer.", 0x7a5cff,
                     {"awaken:power_uses/shadow": 3.0},
                     {"awaken:power_uses/shadow": "You used shadow powers %d times"},
                     "awaken:legion", [perk("summon_strength", 0.25)], None, True),
    "redline": ("Redline", "Speed is your power. Flow lasts longer and the world gets slower.", 0xff4a3d,
                {"awaken:flow_ticks": 0.05, "awaken:dashes": 1.0, "awaken:sprint_cm": 0.0005},
                {"awaken:flow_ticks": "You spent %d ticks in Flow", "awaken:dashes": "You dashed %d times"},
                "awaken:lightning_step", [perk("flow_duration", 0.5), perk("move_speed", 0.08)], None, True),
    "prism": ("Prism", "Light obeys you. Beams split, burn and carry you into the sky.", 0x7fc3ff,
              {"awaken:power_uses/energy": 2.0, "awaken:power_uses/flight": 2.0},
              {"awaken:power_uses/energy": "You fired energy powers %d times", "awaken:power_uses/flight": "You took to the air %d times"},
              "awaken:prism_lance", [perk("force_amp", 0.2)], None, True),
    "architect": ("Architect", "You build under fire. Walls rise as fast as you think them.", 0xd9bb80,
                  {"awaken:blocks_placed_in_combat": 1.0, "awaken:power_uses/architect": 2.0},
                  {"awaken:blocks_placed_in_combat": "You placed %d blocks mid-fight", "awaken:power_uses/architect": "You raised %d walls in combat"},
                  "awaken:citadel", [perk("explosion_resist", 0.3)], None, True),
    "seer": ("Seer", "You see before it happens. The Veil and the future open to you.", 0xc3cbff,
             {"awaken:veil_ticks": 0.02, "awaken:spirits_spoken": 10.0, "awaken:power_uses/mind": 2.0, "awaken:secrets_found": 10.0},
             {"awaken:veil_ticks": "You spent %d ticks outside your body", "awaken:secrets_found": "You found %d secrets",
              "awaken:spirits_spoken": "You spoke with %d spirits"},
             "awaken:foresight", [perk("cord_length", 128), perk("secret_glow_range", 8)], None, True),
    "warden": ("Warden", "You stand between harm and everyone else.", 0x5fd08f,
               {"awaken:damage_taken": 0.05, "awaken:breaks_held_at_hearth": 20.0, "awaken:power_uses/spirit": 1.0},
               {"awaken:damage_taken": "You took %d damage and kept standing", "awaken:breaks_held_at_hearth": "You held your home through %d breaks"},
               "awaken:bulwark", [perk("max_health", 10), perk("hearth_shield")], None, True),
    "stormbreaker": ("Stormbreaker", "You hit like thunder and fly like it too.", 0xf2d24a,
                     {"awaken:power_uses/kinetic": 2.0, "awaken:kills": 0.5},
                     {"awaken:power_uses/kinetic": "You threw %d kinetic strikes", "awaken:kills": "You defeated %d foes"},
                     "awaken:thunderclap", [perk("damage_above_half_hp", 0.2)], None, True),
    "dreamwalker": ("Dreamwalker", "What you shape in dreams follows you into the waking world.", 0xd088c2,
                    {"awaken:lucid_dreams": 40.0, "awaken:dreams": 10.0},
                    {"awaken:lucid_dreams": "You woke up inside %d dreams", "awaken:dreams": "You dreamed %d times"},
                    "awaken:waking_dream", [perk("lucidity_start", 30)], None, True),
    "engineer": ("Engineer", "Machines are an extension of you. Drones fight at your side.", 0x47627a,
                 {"awaken:machines_built": 30.0, "awaken:power_uses/tech": 2.0, "awaken:machines_scanned": 10.0},
                 {"awaken:machines_built": "You built %d machines", "awaken:power_uses/tech": "You used tech %d times"},
                 "awaken:drone_swarm", [perk("cooldown_reduction", 0.15)], None, True),
    "monk": ("Monk", "Empty hands, quiet mind. You need nothing else.", 0xffd28a, {}, {
        "awaken:kills_unarmed": "You defeated %d foes bare-handed", "awaken:meditate_ticks": "You meditated for %d ticks"},
             "awaken:hundred_palms", [perk("damage_above_half_hp", 0.25), perk("energy_regen", 0.4)],
             all_of(c("kills_unarmed", 400), c("meditate_ticks", 36000)), True),
    "hollow": ("Hollow", "You took power from the Deep. It took something back.", 0x33255a, {}, {
        "awaken:depth_record": "You reached stratum %d", "awaken:deaths": "You died %d times and returned"},
               "awaken:void_maw", [perk("force_amp", 0.35), perk("max_health", -4)],
               all_of(c("depth_record", 6), c("slept_in_descent", 3)), True),
    "wanderer": ("Wanderer", "You refused the Signal's choices. You'll take a little of everything.", 0xa19fa8, {}, {
        "awaken:directives_dismissed": "You turned the Signal down %d times"},
                 None, [perk("xp_bonus", 0.1), perk("move_speed", 0.04), perk("cooldown_reduction", 0.05)],
                 c("directives_dismissed", 25), True),
}
for key, (name, desc, colour, scoring, evidence, sig, perks, hidden, available) in PATHS.items():
    data = {"name": name, "description": desc, "colour": colour, "scoring": scoring, "evidence": evidence,
            "perks": perks, "available": available}
    if sig:
        data["signature_power"] = sig
    if hidden:
        data["hidden"] = hidden
    write("path", key, data)

# ---------------------------------------------------------------------------------------------------------------------
# DIRECTIVES (relative conditions: progress since accepted). "requires" (v2): never offered until the player can do it
# (progression/Requirements.java: power:<id>, dash, movement, active, forces, family:<f>, unlock:<flag>, hearth, level:N).
# The E-rank pool without requirements is sized for the first hour (plan v2 §4.6).
FAMILIES = ("kinetic", "velocity", "flight", "energy", "shadow", "mind", "architect", "tech", "spirit")


def any_power_use(n):
    return any_of(*[c("power_uses/" + f, n) for f in FAMILIES])


def d(text, cond, reward, days=2, min_rank="E", max_rank="SOVEREIGN", path=None, quick=False, weight=10, requires=()):
    out = {"text": text, "condition": cond, "reward": reward, "expires_days": days, "min_rank": min_rank,
           "max_rank": max_rank, "quick": quick, "weight": weight}
    if path:
        out["path"] = "awaken:" + path
    if requires:
        out["requires"] = list(requires)
    return out


DIRECTIVES = {
    # first hour: doable at level 2 with one power and no Hearth
    "first_sightings": d("Find three creatures or places you've never seen.", c("discoveries", 3), {"levels": 0.15}, quick=True, weight=14),
    "use_your_power": d("Use your powers ten times in a fight.", any_power_use(10), {"stat_points": 1}, quick=True, weight=14, requires=("active",)),
    "first_floor": d("Clear a floor of any gate.", c("floors_cleared", 1), {"levels": 0.15}, quick=True, weight=12),
    "dash_ten": d("Dash ten times.", c("dashes", 10), {"stat": "AGI"}, quick=True, requires=("dash",)),
    "three_floors": d("Clear three gate floors.", c("floors_cleared", 3), {"levels": 0.2}),
    "push_deeper": d("Push one floor deeper than you'd normally dare.", c("floors_pushed_past_comfort", 1), {"levels": 0.3, "stat_points": 1}),
    "hunt_at_night": d("Defeat 8 hostiles under the night sky.", c("kills_overworld_night", 8), {"levels": 0.15}, quick=True),
    "bare_hands": d("Defeat 5 foes bare-handed.", c("kills_unarmed", 5), {"stat": "STR"}, quick=True),
    "mine_deep": d("Mine 12 ores.", c("ores_mined", 12), {"levels": 0.1}, quick=True),
    "explore_far": d("Sprint a kilometre.", c("sprint_cm", 100000), {"stat": "AGI"}, quick=True),
    # need something you get later
    "flow_and_fists": d("Clear a floor using only Flow and your fists.", all_of(c("floors_cleared", 1), c("flow_ticks", 200)), {"stat": "AGI"}, quick=True, requires=("power:awaken:flow",)),
    "dash_chain": d("Chain five dashes without landing.", c("dash_chain_airborne_best_session", 5), {"stat": "AGI"}, quick=True, requires=("dash",)),
    "build_wall_in_fight": d("Raise ten walls mid-fight.", c("power_uses/architect", 10), {"stat_points": 1}, quick=True, requires=("family:architect",)),
    "force_combo": d("Make two forces meet.", c("force_interactions", 1), {"levels": 0.1, "stat": "INT"}, quick=True, requires=("forces",)),
    "meditate_peak": d("Meditate for a minute somewhere high.", c("meditate_high_ticks", 1200), {"stat": "SPI"}, quick=True, requires=("unlock:awaken:meditation",)),
    "dream_lucid": d("Become lucid in your next dream.", c("lucid_dreams", 1), {"stat": "SPI", "levels": 0.1}, days=3, requires=("unlock:awaken:dreams",)),
    "reality_checks": d("Do five reality checks while awake.", c("reality_checks", 5), {"stat": "SPI"}, quick=True, requires=("unlock:awaken:dreams",)),
    "scout_a_gate": d("Scout a gate from the Veil before entering it.", c("gates_scouted", 1), {"stat": "PER"}, min_rank="D", quick=True, requires=("unlock:awaken:veil",)),
    "talk_to_spirit": d("Talk to a spirit.", c("spirits_spoken", 1), {"stat": "SPI"}, min_rank="D", quick=True, requires=("unlock:awaken:veil",)),
    "clear_gate_no_hit": d("Clear a gate without being hit.", c("gates_no_hit", 1), {"levels": 0.5, "stat_points": 2}, days=3, min_rank="D"),
    "descent_stratum": d("Light a new Waystone in the Descent.", c("waystones_lit", 1), {"levels": 0.3}, days=3, min_rank="D", requires=("unlock:awaken:descent",)),
    "scan_machine": d("Scan a broken machine.", c("machines_scanned", 1), {"stat": "INT"}, min_rank="D", quick=True),
    "build_machine": d("Build a Precursor machine.", c("machines_built", 1), {"levels": 0.25, "stat": "INT"}, days=3, min_rank="C"),
    "hold_the_line": d("Defend your Hearth from a break without losing a structure.", c("breaks_held_at_hearth", 1), {"stat_points": 2}, days=3, min_rank="D", requires=("hearth",)),
    "boss_flawless": d("Defeat a Gate Lord without taking damage.", c("boss_floors_no_damage", 1), {"levels": 0.5, "stat": "VIT"}, days=3, min_rank="C"),
    "twist_seeker": d("Survive a twisted gate.", c("twists_seen", 1), {"levels": 0.3}, days=4, weight=5),
    "training_lap": d("Beat your best lap at the Training Grounds.", c("training_records", 1), {"stat": "AGI"}, min_rank="D", quick=True, requires=("hearth",)),
    "trophy": d("Hang a new trophy in your Hall.", c("trophies_placed", 1), {"stat_points": 1}, quick=True, requires=("hearth",)),
    "kill_unarmed_boss": d("Defeat a Gate Lord bare-handed.", c("bosses_killed_unarmed", 1), {"stat": "STR", "stat_amount": 2}, days=4, min_rank="C", weight=4),
    "night_without_sleep": d("Stay awake through a whole night.", c("nights_awake", 1), {"stat": "PER"}, quick=True, weight=5),
    # Path directives
    "redline_flow": d("Spend two minutes in Flow today.", c("flow_ticks", 2400), {"stat": "AGI", "stat_amount": 2}, path="redline", quick=True, requires=("power:awaken:flow",)),
    "shadow_legion": d("Use shadow powers twenty times.", c("power_uses/shadow", 20), {"stat": "SPI", "stat_amount": 2}, path="shadowbinder"),
    "prism_sky": d("Defeat ten foes while airborne.", c("kills_airborne", 10), {"stat": "INT", "stat_amount": 2}, path="prism"),
    "architect_fort": d("Win a fight inside a fortress you raised.", c("power_uses/architect", 20), {"stat": "VIT", "stat_amount": 2}, path="architect", requires=("family:architect",)),
    "seer_secrets": d("Find three secrets.", c("secrets_found", 3), {"stat": "PER", "stat_amount": 2}, path="seer"),
    "warden_guard": d("Take 200 damage holding the line.", c("damage_taken", 200), {"stat": "VIT", "stat_amount": 2}, path="warden"),
    "storm_strikes": d("Land 30 kinetic strikes.", c("power_uses/kinetic", 30), {"stat": "STR", "stat_amount": 2}, path="stormbreaker", requires=("family:kinetic",)),
    "dream_shape": d("Manifest three things in a dream.", c("dream_manifests", 3), {"stat": "SPI", "stat_amount": 2}, path="dreamwalker", requires=("unlock:awaken:dreams",)),
    "engineer_drones": d("Deploy drones ten times.", c("power_uses/tech", 10), {"stat": "INT", "stat_amount": 2}, path="engineer", requires=("family:tech",)),
}
for key, data in DIRECTIVES.items():
    write("directive", key, data)

# ---------------------------------------------------------------------------------------------------------------------
# REGIMEN DRILLS (relative). "requires" as for Directives: three drills a morning, every one doable today.
def drill(text, cond, paths=(), min_rank="E", requires=()):
    out = {"text": text, "condition": cond, "paths": ["awaken:" + p for p in paths], "min_rank": min_rank}
    if requires:
        out["requires"] = list(requires)
    return out


DRILLS = {
    "sprint_1k": drill("Sprint 1,000 blocks", c("sprint_cm", 100000)),
    "kills_20": drill("Defeat 10 foes", c("kills", 10)),
    "mine_10": drill("Mine 10 ores", c("ores_mined", 10)),
    "powers_10": drill("Use your powers 10 times", any_power_use(10), requires=("active",)),
    "discover_2": drill("Find two things you've never seen", c("discoveries", 2)),
    "meditate_30": drill("Meditate for 30 seconds", c("meditate_ticks", 600), requires=("unlock:awaken:meditation",)),
    "dash_15": drill("Dash 15 times", c("dashes", 15), requires=("dash",)),
    "place_100": drill("Place 100 blocks", c("blocks_placed", 100)),
    "floor_1": drill("Clear a gate floor", c("floors_cleared", 1)),
    "reality_3": drill("Do 3 reality checks", c("reality_checks", 3), requires=("unlock:awaken:dreams",)),
    "unarmed_5": drill("Defeat 5 foes bare-handed", c("kills_unarmed", 5)),
    "training_lap": drill("Run a lap of the Training Grounds", c("training_laps", 1), min_rank="D", requires=("hearth",)),
    "flow_60": drill("Spend a minute in Flow", c("flow_ticks", 1200), paths=("redline",), requires=("power:awaken:flow",)),
    "shadow_drill": drill("Use shadow powers ten times", c("power_uses/shadow", 10), paths=("shadowbinder",)),
    "beam_20": drill("Fire 20 energy powers", c("power_uses/energy", 20), paths=("prism",), requires=("family:energy",)),
    "walls_10": drill("Raise 10 walls", c("power_uses/architect", 10), paths=("architect",), requires=("family:architect",)),
    "veil_2min": drill("Spend two minutes in the Veil", c("veil_ticks", 2400), paths=("seer",), requires=("unlock:awaken:veil",)),
    "absorb_100": drill("Take 100 damage and keep standing", c("damage_taken", 100), paths=("warden",)),
    "kinetic_20": drill("Land 20 kinetic strikes", c("power_uses/kinetic", 20), paths=("stormbreaker",), requires=("family:kinetic",)),
    "dream_1": drill("Dream once", c("dreams", 1), paths=("dreamwalker",)),
    "tech_10": drill("Use tech 10 times", c("power_uses/tech", 10), paths=("engineer",), requires=("family:tech",)),
    "meditate_3min": drill("Meditate for three minutes", c("meditate_ticks", 3600), paths=("monk",), requires=("unlock:awaken:meditation",)),
}
for key, data in DRILLS.items():
    write("drill", key, data)

# ---------------------------------------------------------------------------------------------------------------------
# THE LEVEL ROAD, 1 -> 50 (plan v2 §4.4). One file per step; the file name is the unlock id saved in the profile, so
# never rename one. Every level also gives +3 points. Toasts are small: keep titles under ~24 characters and the text
# to one short line. Flags are read by other modules with Progression.hasFlag(player, "awaken:<flag>").
def step(level, title, text, *grants, quiet=False):
    return {"schema": 1, "level": level, "title": title, "text": text, "grants": list(grants), "quiet": quiet}


def power(pid):
    return {"type": "power", "id": "awaken:" + pid}


def recipes(*ids):
    return {"type": "recipe", "ids": ["awaken:" + i for i in ids]}


def item(iid, count=1):
    return {"type": "item", "id": "awaken:" + iid, "count": count}


def core(count=1):
    return {"type": "power_core", "count": count}


def road_flag(fid):
    return {"type": "flag", "id": "awaken:" + fid}


LEVEL_ROAD = {
    # L1-5 · Awakening
    # waking already puts you at level 2 (story/Prologue WAKE_LEVEL), so level 2's step lands beside the first-power
    # cards; Dash waits for level 3 (~30 min) so it is its own moment
    "first_power": step(1, "Your first power", "Choose one of three.", quiet=True),
    "hunter_crafts": step(2, "Hunter crafts", "Forge, Target Dummy, Trophy Mount: recipe book.",
                          recipes("forge", "target_dummy", "trophy_mount")),
    "dash": step(3, "Dash", "Left Alt: burst forward, even in mid-air.", power("dash")),
    "second_power": step(4, "A Power Core", "Use it to learn a second power.", core()),
    "truths": step(5, "Truths", "Who are you becoming? Rewards shown · K", road_flag("truths")),
    # L6-10 · First gates
    "hearth_anchor": step(6, "Hearth Anchor", "Craft it from a Lord's Heart: claim a home.",
                          recipes("hearth_anchor"), road_flag("hearth")),
    "what_they_carried": step(7, "What they carried", "The stone people still hold things. A key names the town its chest is in.", road_flag("stilled_told")),
    "essence": step(8, "Essence", "8 shards for your Hearth and machines.", item("essence_shard", 8)),
    "third_power": step(9, "Another Power Core", "A third power, or a stronger one.", core()),
    "rank_trials": step(10, "Reassessment", "Build a Monolith; take the D-rank trial.",
                        recipes("reassessment_monolith"), road_flag("rank_trials")),
    # L11-20 · The Veil
    "meditation": step(11, "Meditation", "Sneak and hold G: energy flows back faster.",
                       recipes("meditation_mat"), road_flag("meditation")),
    "lucid_dreams": step(12, "Lucid dreams", "H checks if you're dreaming. Keep a journal.",
                         recipes("dream_journal"), road_flag("dreams")),
    "the_veil": step(15, "The Veil", "Meditate, then let go of G: step outside.",
                     recipes("stillpoint_shrine"), road_flag("veil")),
    "the_well": step(20, "The Well", "The Descent waits below. A Power Core.", road_flag("descent"), core()),
    # L21-30 · C-rank
    "second_awakening": step(25, "Second Awakening", "Pass the C-rank trial to find your Path.", road_flag("path")),
    "the_far_towns": step(30, "The far towns", "A few walled towns kept their bells and their people. Listen for one ringing on the hour.",
                          road_flag("holdfast_told")),
    # L31-45 · Deep
    "ultimate": step(35, "Overload", "R: gather everything, then explode.", power("overload")),
    "the_deep": step(40, "The deep calls", "Strata 4 and 5 are open. A Power Core.", road_flag("deep"), core()),
    "rank_b_trial": step(45, "B-rank trial", "A duel with a Lord: Monolith or Spire.", road_flag("rank_b")),
    # L46-50 · B-rank
    "hollow_court": step(50, "The Hollow Court stirs", "Something below noticed you. Two Cores.",
                         road_flag("hollow_court"), core(2)),
}
for key, data in LEVEL_ROAD.items():
    write("level_road", key, data)

print("truths", len(TRUTHS) + len(EMERGENT), "titles", len(TITLES), "latents", len(LATENTS), "paths", len(PATHS),
      "directives", len(DIRECTIVES), "drills", len(DRILLS), "level road", len(LEVEL_ROAD))
