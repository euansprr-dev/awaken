# Counters referenced by content

Counters that content in data/awaken/awaken/** reads. Each owning module must count these.

| Counter | Owner | Used by |
|---|---|---|
| `awaken:blocks_placed` | progression | drill/place_100, truth/i_build_what_i_need |
| `awaken:blocks_placed_in_combat` | powers | path/architect, title/architect |
| `awaken:boss_floors_no_damage` | gates | directive/boss_flawless, truth/nothing_can_touch_me |
| `awaken:bosses_killed_unarmed` | mobs (every boss, Gate Lords included) | directive/kill_unarmed_boss |
| `awaken:breaks_held_at_hearth` | hearth | directive/hold_the_line, path/warden, truth/i_protect_what_is_mine |
| `awaken:damage_taken` | progression | directive/warden_guard, drill/absorb_100, path/warden, truth/i_endure |
| `awaken:dash_chain_airborne_best` | powers | truth/i_move_like_water |
| `awaken:dash_chain_airborne_best_session` | powers | directive/dash_chain |
| `awaken:dashes` | powers | drill/dash_15, path/redline |
| `awaken:deaths` | progression | path/hollow, title/persistent |
| `awaken:depth_record` | descent | path/hollow, title/abyssal, title/deep_diver, truth/i_go_deeper, truth/the_deep_does_not_scare_me |
| `awaken:directives_dismissed` | progression | path/wanderer |
| `awaken:directives_kept_streak` | progression | title/keeper_of_promises, truth/i_keep_my_word |
| `awaken:dream_manifests` | lucidium | directive/dream_shape |
| `awaken:dreams` | lucidium | drill/dream_1, path/dreamwalker, title/dreamer |
| `awaken:echo_army_peak` | hearth | directive/shadow_legion, drill/echo_fight |
| `awaken:echoes_bound` | hearth | directive/bind_echo, path/shadowbinder, title/shadow_lord, truth/i_command_the_fallen |
| `awaken:fall_survived_max` | progression | title/fell_further |
| `awaken:floor_streak_no_early_extract` | gates | truth/i_do_not_retreat, truth/i_never_flee |
| `awaken:floors_cleared` | gates | directive/flow_and_fists, directive/three_floors, drill/floor_1 |
| `awaken:floors_pushed_past_comfort` | gates | directive/push_deeper |
| `awaken:flow_ticks` | powers | directive/flow_and_fists, directive/redline_flow, drill/flow_60, path/redline, title/stormcaller, truth/i_am_the_storm |
| `awaken:force_interactions` | powers | directive/force_combo, title/chain_reaction, truth/i_bend_the_forces |
| `awaken:gate_cleared_unscouted_fast` | gates | title/gatebreaker |
| `awaken:gates_cleared` | gates | title/first_blood, truth/i_finish_what_i_start |
| `awaken:gates_no_hit` | gates | directive/clear_gate_no_hit, title/unwitnessed, truth/i_am_never_caught |
| `awaken:gates_scouted` | gates (`Gates.markScouted`, called by Veil scouting) | directive/scout_a_gate |
| `awaken:gates_solo_no_echo` | gates | truth/i_walk_alone |
| `awaken:kills` | progression | drill/kills_20, path/stormbreaker, title/centurion, title/thousand_hands, truth/i_strike_first |
| `awaken:kills_airborne` | powers | directive/prism_sky |
| `awaken:kills_overworld_night` | progression | directive/hunt_at_night |
| `awaken:kills_unarmed` | progression | directive/bare_hands, drill/unarmed_5, path/monk, title/barehanded, truth/my_body_is_a_weapon |
| `awaken:latent/creeper_point_blank_survived` | progression (world events) | latent/blast_skin |
| `awaken:latent/dawn_meditation_above_clouds` | veil | latent/clarity |
| `awaken:latent/double_gate_cleared` | gates | latent/twin_soul |
| `awaken:latent/escaped_freezing` | progression (world events) | latent/frost_blood |
| `awaken:latent/fell_from_height_limit_into_water` | progression (world events) | latent/featherfall |
| `awaken:latent/inverted_landing` | descent | latent/gravity_kiss |
| `awaken:latent/manifest_woke` | lucidium | latent/dream_hands |
| `awaken:latent/near_drowned` | progression (world events) | latent/iron_lungs |
| `awaken:latent/nights_awake_underground` | progression (world events) | latent/night_eyes |
| `awaken:latent/red_gate_cleared` | gates | latent/unbroken |
| `awaken:latent/silent_halls_unheard` | descent | latent/ghost_step |
| `awaken:latent/slept_in_descent` | descent | latent/deep_dreamer |
| `awaken:latent/spirit_named` | veil | latent/echo_whisper |
| `awaken:latent/sprint_starving` | progression (world events) | latent/second_wind |
| `awaken:latent/stared_at_sun` | progression (world events) | latent/sun_eyes |
| `awaken:latent/struck_holding_copper` | progression (world events) | latent/stormcall |
| `awaken:latent/survived_lava` | progression (world events) | latent/ember_heart |
| `awaken:latent/three_twists_row` | gates | latent/lucky_star |
| `awaken:latent/vision_found_same_day` | lucidium | latent/pathfinder |
| `awaken:latent/wolf_died_protecting` | progression (world events) | latent/beast_tongue |
| `awaken:lucid_dreams` | lucidium | directive/dream_lucid, path/dreamwalker, title/lucid, truth/i_am_awake_in_my_dreams |
| `awaken:machines_built` | above | directive/build_machine, path/engineer, truth/i_understand_the_machines |
| `awaken:machines_scanned` | above | directive/scan_machine, path/engineer, title/tinkerer |
| `awaken:meditate_high_ticks` | veil | directive/meditate_peak |
| `awaken:meditate_ticks` | veil | drill/meditate_30, drill/meditate_3min, path/monk, truth/i_am_still |
| `awaken:nightmares_beaten` | lucidium | title/fear_eater, truth/i_face_my_fears |
| `awaken:nights_awake` | progression | directive/night_without_sleep |
| `awaken:ores_mined` | progression | directive/mine_deep, drill/mine_10, title/stonebreaker |
| `awaken:power_uses/architect` | powers | directive/architect_fort, directive/build_wall_in_fight, drill/walls_10, path/architect |
| `awaken:power_uses/energy` | powers | drill/beam_20, path/prism |
| `awaken:power_uses/flight` | powers | path/prism |
| `awaken:power_uses/kinetic` | powers | directive/storm_strikes, drill/kinetic_20, path/stormbreaker |
| `awaken:power_uses/mind` | powers | path/seer |
| `awaken:power_uses/shadow` | powers | path/shadowbinder |
| `awaken:power_uses/spirit` | powers | path/warden |
| `awaken:power_uses/tech` | powers | directive/engineer_drones, drill/tech_10, path/engineer |
| `awaken:reality_checks` | lucidium | directive/reality_checks, drill/reality_3 |
| `awaken:regimens_done` | progression | title/the_regimen, truth/i_choose_my_path |
| `awaken:secrets_found` | veil | directive/seer_secrets, path/seer, truth/i_see_what_others_miss |
| `awaken:slept_in_descent` | descent | path/hollow |
| `awaken:spirits_spoken` | veil | directive/talk_to_spirit, path/seer, title/medium, truth/i_listen_to_the_dead |
| `awaken:sprint_cm` | progression | directive/explore_far, drill/sprint_1k, path/redline, title/marathon, truth/i_am_fast |
| `awaken:training_laps` | hearth | drill/training_lap |
| `awaken:training_records` | hearth | directive/training_lap |
| `awaken:trophies_placed` | hearth | directive/trophy |
| `awaken:twists_seen` | gates | directive/twist_seeker, title/twisted |
| `awaken:veil_ticks` | veil | drill/veil_2min, path/seer, title/untethered, truth/i_am_more_than_my_body |
| `awaken:waystones_lit` | descent | directive/descent_stratum |
| `awaken:wolves_tamed` | progression | title/wolf_friend |

## Also counted (Status → Records, revelations)

| Counter | Owner | Read by |
|---|---|---|
| `awaken:blocks_broken` | progression | (kept for later) |
| `awaken:bosses_killed` | mobs | Status → Records |
| `awaken:dreams_written` | lucidium | (kept for later) |
| `awaken:frames_forged`, `awaken:frame_modules_socketed` | above | (kept for later) |
| `awaken:gate_scans` | above | (kept for later) |
| `awaken:gear_tempered` | hearth | (kept for later) |
| `awaken:guardians_defeated` | descent | Status → Records |
| `awaken:kills_overworld` | progression | (kept for later) |
| `awaken:mentor_duels_won` | lucidium | (kept for later) |
| `awaken:play_ticks` | progression | Status → Records |
| `awaken:shadows_defeated` | descent | Status → Records |
| `awaken:sovereigns_defeated` | story | revelation/first_sovereign, Status → Records |
| `awaken:story/seed_reached` | story | revelation/seed_reached |
