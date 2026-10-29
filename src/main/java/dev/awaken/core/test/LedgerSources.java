package dev.awaken.core.test;

import dev.awaken.core.api.Sources;

/**
 * The reviewed ways in for what was in the game before the Journey (audited 29 Oct 2026, SPEC §43.11): the things that
 * come from code rather than a recipe or a loot table, each with the class that gives it, and the things never meant to
 * be held. New things declare their own way in where they're made (as {@code world.stilled} does).
 */
final class LedgerSources {
    private LedgerSources() {}

    static void register() {
        Sources.declare("Lord-forged: a Gate Lord's kill puts it in the slayer's hand (arms.LordForged)",
                "abbot_of_frost_glaive", "bell_warden_maul", "drowned_archivist_spear", "ember_tyrant_axe", "gearwright_colossus_gauntlets",
                "glass_serpent_rapier", "hive_regent_daggers", "hollow_herald_greatblade", "lantern_broker_chainblade", "mirror_knight_shieldblade",
                "mother_of_spores_scythe", "root_saint_warstaff", "sky_leviathan_twinblades", "the_sleeper_katana", "unit_zero_precursor");
        Sources.declare("drawn at the Forge once its style is known (arms.ArmsForge)",
                "axe_blueprint", "chainblade_blueprint", "daggers_blueprint", "gauntlets_blueprint", "glaive_blueprint", "greatblade_blueprint",
                "katana_blueprint", "maul_blueprint", "precursor_blueprint", "rapier_blueprint", "scythe_blueprint", "shieldblade_blueprint",
                "spear_blueprint", "twinblades_blueprint", "warstaff_blueprint");
        Sources.declare("a ripe wild plant harvested (living.craft.Herbs)", "duskbloom", "emberroot", "frostcap", "moonmoss", "spiritgrass", "sunleaf");
        Sources.declare("picked from a bramble (ground.wild.BrambleBlock)", "blackberries");
        Sources.declare("Ilse's ember at the Last Fire, and a new one at a Hearth (hearth.Embers)", "hearth_ember");
        Sources.declare("handed over at the start (holo.band.BandModule, opening)", "holo_band");
        Sources.declare("the Registry's licence at the start (opening.OpeningRegistry)", "hunters_licence");
        Sources.declare("the inn sells a room (village.Trade)", "inn_key");
        Sources.declare("a taken villager's keepsake, found on the trail (village.trouble.Keepsake)", "keepsake");
        Sources.declare("a gate's reward roll (PowerCoreHooks, powers.PowersModule)", "power_core");
        Sources.declare("a Gate Lord's trophy (hearth.trophy.Trophies)", "trophy");
        Sources.declare("the Waking Pick's verse (delve.WakingPick)", "waking_pick");
        Sources.declare("left behind by a dream (lucidium.dream.Dreams)", "dream_residue");
        Sources.declare("the Delvers' cave finds (delve.cave.CaveItems)", "delvers_log", "delvers_pick");
        Sources.declare("a stolen village bell won back (village.trouble.BellTrouble)", "village_bell");
        Sources.declare("a thief's bag caught in the city (citizens.CityEvents)", "stolen_bag");
        Sources.declare("forged at the Precursor frame forge (above.frame.FrameForge)",
                "precursor_frame_helmet", "precursor_frame_chestplate", "precursor_frame_leggings", "precursor_frame_boots");
        Sources.declare("a quest chain's relic (data awaken/quest: orens_first_steel, the_last_delivery)", "orens_mark", "couriers_knot");
        Sources.declare("Ilse's letters, her first thread (living.FirstThread)", "ilse_letters");

        Sources.neverHeld("a citizen's prop: only ever in their hands (citizens.CitizensContent)",
                "lamplighter_pole", "smith_hammer", "mason_trowel", "crier_scroll", "watch_halberd", "hand_lantern", "bread_basket", "laundry_basket",
                "ledger_book", "courier_satchel", "porter_crate", "carcass_pole", "shepherd_crook");
        Sources.neverHeld("the binoculars are a key (SPEC §39): the item is only what the hands are drawn holding", "binoculars");
        Sources.neverHeld("set in the world by its builder and never dropped: the item is creative's only",
                "anchor_ring", "broken_machine", "halo_return_pad", "precursor_door", "precursor_door_core", "spire_crown", "wing_core", "delver_lamp",
                "veil_cache", "veil_glyph", "veil_stone", "veil_mark", "stillpoint_cloud", "stillpoint_door_in", "stillpoint_door_out", "street_lamp",
                "carved_token", "torn_cloth", "drag_marks", "twigs", "coal_lode_heart", "copper_lode_heart", "deepglass_lode_heart", "diamond_lode_heart",
                "emerald_lode_heart", "gold_lode_heart", "iron_lode_heart", "lapis_lode_heart", "redstone_lode_heart");
        Sources.neverHeld("a skin of water the cave skin lays (SPEC §42): it drops nothing, the item is creative's only", "cave_puddle");
        Sources.neverHeld("the old Hearth role pads: the Journey's beasts do the work (SPEC §43.6)",
                "role_pad_farm", "role_pad_guard", "role_pad_haul", "role_pad_mine", "role_pad_train");
        Sources.neverHeld("Pell's errand is gone; nothing gives it any more", "pells_letter");

        // blocks without an item: how each comes to be in the world
        Sources.declare("built by the city's and the villages' builders, the camps and the routes",
                "arrow_loop", "ashlar", "awning", "banner_cloth", "bench", "brazier", "cart", "cask", "chimney_bricks", "city_bricks", "city_fire",
                "clay_tile_slab", "clay_tile_stairs", "clay_tiles", "crate", "fingerpost", "fishing_net", "flagpole", "goods_shelf", "hang_line",
                "hanging_ladder", "hay_cart", "laundry", "line_pole", "lit_window", "loading_door", "market_table", "milestone", "mooring_ring",
                "notice_board", "planter", "plaster", "portcullis", "produce", "rope", "runner_line", "sack", "scarecrow", "shutter", "sign_bracket",
                "signboard", "slate_tile_slab", "slate_tile_stairs", "slate_tiles", "stall_counter", "stook", "timber", "wall_lantern", "wall_shield",
                "wall_spout", "wares", "wheel_face", "well_windlass", "wayfarer_bedroll", "cargo_crate", "camp_pot");
        Sources.declare("set by the gates as they open, run and close", "gate_core", "gate_exit", "gate_fissure", "gate_inner", "gate_return", "gate_rift",
                "bell_prop");
        Sources.declare("the gate rooms' own wear, laid as a room is built (gates.world.CryptWear, SPEC §38.14)", "crypt_bell_toppled");
        Sources.declare("a power's construct, made by casting", "construct", "dream_construct");
        Sources.declare("an anomaly in the land (world.anomaly)", "anomaly_fallen_star", "anomaly_spirit_cache");
        Sources.declare("a Hearth's fire, lit at its founding (hearth)", "hearthfire");
        Sources.declare("the land's own: trodden grass, a wild rye's top half (ground)", "trampled_grass", "wild_rye_top");
        Sources.declare("the Waking Pick's stone, set by the Delve (delve)", "waking_pick_stone");
    }
}
