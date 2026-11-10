"""How each gate monster's fight sounds (SPEC §60.8): two voices for every body, beside the four its theme file gives
it. `wind` plays as it gathers a blow or a move (quietly, under the tell's own sound, which is the same on every
creature so it can be learned); `blow` plays as it lets go. Each is one or two of Minecraft's own recordings by name,
re-pitched (name, volume, pitch): what the body is made of, moving. Nothing here is a creature's roar borrowed whole,
and nothing is a high ring at the moment of the blow.

Kept apart from the theme files so the whole cast's fight can be read, and balanced, in one place."""

FIGHTS = {
    # Bell Crypt: bone, bronze, chain, cloth
    "toll_ghoul": dict(wind=[("block.chain.step", 0.9, 0.6)], blow=[("block.bell.use", 0.6, 0.6)]),
    "crypt_archer": dict(wind=[("block.bone_block.hit", 0.8, 0.7)], blow=[("block.bone_block.break", 0.9, 1.2)]),
    "bell_wisp": dict(wind=[("block.chain.hit", 0.6, 1.3)], blow=[("block.bell.use", 0.4, 0.85)]),
    "crypt_keeper": dict(wind=[("block.deepslate_bricks.hit", 1.0, 0.5)], blow=[("block.deepslate_bricks.break", 1.0, 0.55), ("block.anvil.land", 0.4, 0.6)]),
    "the_sexton": dict(wind=[("block.lantern.hit", 0.8, 0.6)], blow=[("item.shovel.flatten", 1.0, 0.6), ("block.anvil.land", 0.35, 0.5)]),
    # Fungal Hollow: soft, wet, spore
    "sporeling": dict(wind=[("block.fungus.hit", 0.9, 1.3)], blow=[("block.fungus.break", 0.9, 1.4)]),
    "mycelid": dict(wind=[("block.wart_block.hit", 0.9, 0.6)], blow=[("block.wart_block.break", 1.0, 0.6)]),
    "capweaver": dict(wind=[("block.fungus.step", 1.0, 1.5)], blow=[("block.moss.break", 0.9, 1.3)]),
    "puffcap": dict(wind=[("block.spore_blossom.hit", 0.9, 0.8)], blow=[("block.spore_blossom.break", 1.0, 0.7)]),
    "hollow_tender": dict(wind=[("block.roots.hit", 0.9, 0.7)], blow=[("block.roots.break", 0.9, 0.8)]),
    "the_rot": dict(wind=[("block.mud.hit", 1.0, 0.5)], blow=[("block.mud.break", 1.0, 0.5), ("block.wart_block.break", 1.0, 0.45)]),
    # Ember Mines: coal, slag, iron, fire
    "cinder_miner": dict(wind=[("block.basalt.hit", 0.9, 0.8)], blow=[("block.basalt.break", 1.0, 0.7)]),
    "slag_cube": dict(wind=[("block.lava.pop", 1.0, 0.8)], blow=[("item.bucket.empty_lava", 0.9, 0.9)]),
    "ember_imp": dict(wind=[("block.fire.ambient", 1.0, 1.4)], blow=[("item.firecharge.use", 0.8, 1.2)]),
    "scorch_crawler": dict(wind=[("block.basalt.step", 1.0, 1.5)], blow=[("block.basalt.hit", 1.0, 1.4)]),
    "slag_boar": dict(wind=[("entity.hoglin.angry", 0.7, 0.7)], blow=[("block.netherite_block.hit", 1.0, 0.6), ("entity.hoglin.attack", 0.8, 0.7)]),
    "the_overseer": dict(wind=[("block.respawn_anchor.charge", 0.6, 0.6)], blow=[("block.anvil.land", 0.6, 0.5), ("item.firecharge.use", 0.9, 0.6)]),
    # Drowned Archive: ink, paper, brine, old wood
    "ink_drowned": dict(wind=[("item.bottle.fill", 1.0, 0.6)], blow=[("item.ink_sac.use", 1.0, 0.7), ("item.bucket.empty", 0.7, 1.2)]),
    "page_wraith": dict(wind=[("item.book.page_turn", 1.0, 1.3)], blow=[("item.book.put", 1.0, 1.2)]),
    "archive_eye": dict(wind=[("item.spyglass.use", 1.0, 0.7)], blow=[("item.spyglass.stop_using", 1.0, 0.7), ("block.glass.hit", 0.8, 0.9)]),
    "salt_stray": dict(wind=[("item.brush.brushing.generic", 1.0, 1.2)], blow=[("item.trident.throw", 0.5, 1.5)]),
    "drowned_scribe": dict(wind=[("item.book.page_turn", 1.0, 0.7)], blow=[("item.ink_sac.use", 1.0, 0.55), ("item.book.put", 1.0, 0.6)]),
    "the_curator": dict(wind=[("block.barrel.open", 0.9, 0.6)], blow=[("block.barrel.close", 1.0, 0.6), ("item.book.put", 1.0, 0.5)]),
    # Clockwork Foundry: brass, gears, steam
    "cog_hound": dict(wind=[("item.crossbow.loading_middle", 0.9, 1.3)], blow=[("block.iron_trapdoor.close", 0.9, 1.4), ("block.copper.hit", 1.0, 1.3)]),
    "rivet_gunner": dict(wind=[("item.crossbow.loading_start", 1.0, 0.8)], blow=[("block.dispenser.launch", 0.9, 1.1)]),
    "spark_drone": dict(wind=[("block.copper_bulb.turn_on", 1.0, 1.4)], blow=[("block.copper_bulb.turn_off", 1.0, 1.2), ("block.copper.hit", 0.9, 1.5)]),
    "foundry_marshal": dict(wind=[("block.piston.contract", 1.0, 0.6)], blow=[("block.piston.extend", 1.0, 0.55), ("block.anvil.land", 0.6, 0.7)]),
    "the_regulator": dict(wind=[("block.iron_door.open", 0.9, 0.5)], blow=[("block.iron_door.close", 1.0, 0.5), ("block.anvil.land", 0.5, 0.6)]),
    # Glass Desert: glass, sand
    "glass_husk": dict(wind=[("block.glass.step", 1.0, 0.7)], blow=[("block.glass.break", 0.7, 0.8)]),
    "sand_stalker": dict(wind=[("block.sand.hit", 1.0, 0.8)], blow=[("block.sand.break", 1.0, 0.9)]),
    "heat_shimmer": dict(wind=[("block.sand.idle", 1.0, 1.3)], blow=[("block.glass.hit", 0.9, 1.1)]),
    "prism_sentinel": dict(wind=[("block.amethyst_block.resonate", 0.5, 0.6)], blow=[("block.amethyst_cluster.break", 0.8, 0.7)]),
    "the_mirage": dict(wind=[("block.glass.step", 1.0, 0.5)], blow=[("block.glass.break", 0.9, 0.5)]),
    # Hive: wax, honey, shell
    "drone_bee": dict(wind=[("entity.bee.loop_aggressive", 0.7, 1.2)], blow=[("entity.bee.sting", 1.0, 0.9)]),
    "hive_larva": dict(wind=[("block.honey_block.step", 0.9, 1.3)], blow=[("block.honey_block.hit", 1.0, 1.2)]),
    "honey_slime": dict(wind=[("block.honey_block.slide", 0.9, 0.8)], blow=[("block.honey_block.break", 1.0, 0.8)]),
    "hive_guard": dict(wind=[("block.resin_bricks.hit", 1.0, 0.6)], blow=[("block.resin_bricks.break", 1.0, 0.6)]),
    "the_swarm_mind": dict(wind=[("entity.bee.loop_aggressive", 1.0, 0.6)], blow=[("block.honey_block.break", 1.0, 0.5), ("entity.bee.sting", 1.0, 0.6)]),
    # Overgrown Temple: wood, vine, thorn, censer
    "vine_creaker": dict(wind=[("entity.creaking.sway", 0.9, 0.8)], blow=[("entity.creaking.attack", 0.9, 0.8), ("block.roots.break", 1.0, 0.7)]),
    "thorn_archer": dict(wind=[("block.vine.hit", 1.0, 0.8)], blow=[("block.sweet_berry_bush.break", 1.0, 1.1)]),
    "thorn_hound": dict(wind=[("block.azalea_leaves.hit", 1.0, 0.8)], blow=[("block.azalea_leaves.break", 1.0, 0.9), ("block.vine.break", 1.0, 0.8)]),
    "incense_priest": dict(wind=[("block.chain.hit", 1.0, 0.8)], blow=[("block.lantern.hit", 1.0, 0.7), ("block.chain.break", 0.8, 0.8)]),
    "the_overgrowth": dict(wind=[("block.muddy_mangrove_roots.hit", 1.0, 0.5)], blow=[("block.muddy_mangrove_roots.break", 1.0, 0.5), ("block.roots.break", 1.0, 0.45)]),
    # Lantern Market: paper, cloth, lamp, oil
    "lantern_moth": dict(wind=[("block.wool.hit", 0.9, 1.5)], blow=[("block.lantern.hit", 0.6, 1.3)]),
    "pickpocket": dict(wind=[("item.bundle.remove_one", 1.0, 1.2)], blow=[("item.bundle.insert", 1.0, 1.3)]),
    "wick_slime": dict(wind=[("block.lantern.step", 1.0, 1.1)], blow=[("block.lantern.fall", 1.0, 1.0)]),
    "masked_duelist": dict(wind=[("item.armor.equip_elytra", 0.9, 1.3)], blow=[("item.book.page_turn", 1.0, 0.7), ("entity.player.attack.sweep", 0.6, 1.3)]),
    "the_lamplighter": dict(wind=[("block.lantern.hit", 1.0, 0.5)], blow=[("block.lantern.break", 1.0, 0.5), ("item.flintandsteel.use", 0.9, 0.6)]),
    # Frost Monastery: snow, ice, horn
    "rime_acolyte": dict(wind=[("block.powder_snow.step", 1.0, 0.7)], blow=[("block.glass.hit", 0.8, 0.6), ("block.snow.break", 1.0, 0.7)]),
    "hail_stray": dict(wind=[("block.snow.hit", 1.0, 1.1)], blow=[("entity.snowball.throw", 1.0, 0.8)]),
    "frost_wisp": dict(wind=[("block.powder_snow.hit", 1.0, 1.3)], blow=[("block.powder_snow.break", 1.0, 1.2)]),
    "ice_hound": dict(wind=[("entity.baby_wolf.growl", 0.7, 0.55)], blow=[("entity.fox.bite", 0.9, 0.7), ("block.glass.hit", 0.8, 0.8)]),
    "chant_master": dict(wind=[("entity.goat.prepare_ram", 0.7, 0.5)], blow=[("block.snow.fall", 1.0, 0.6), ("entity.goat.horn_break", 0.5, 0.6)]),
    "the_yeti": dict(wind=[("entity.polar_bear.warning", 0.9, 0.6)], blow=[("block.snow.break", 1.0, 0.5), ("block.powder_snow.break", 1.0, 0.5)]),
    # Mirror Palace: silver, glass
    "silver_knight": dict(wind=[("item.armor.equip_iron", 1.0, 0.7)], blow=[("item.armor.equip_chain", 1.0, 0.6), ("block.chain.break", 0.8, 0.7)]),
    "glint_mite": dict(wind=[("block.glass.step", 1.0, 1.5)], blow=[("block.glass.hit", 0.9, 1.3)]),
    "glass_duelist": dict(wind=[("block.glass.place", 0.9, 1.1)], blow=[("block.glass.hit", 1.0, 0.9), ("item.trident.hit", 0.5, 1.2)]),
    "the_unblinking": dict(wind=[("block.conduit.ambient.short", 0.9, 0.6)], blow=[("block.conduit.attack.target", 0.9, 0.6), ("block.glass.break", 0.7, 0.5)]),
    # Precursor Lab: shutters, coils, gel
    "test_subject": dict(wind=[("block.vault.insert_item_fail", 0.8, 0.7)], blow=[("block.vault.hit", 1.0, 0.7)]),
    "security_unit": dict(wind=[("block.vault.open_shutter", 1.0, 0.9)], blow=[("block.vault.close_shutter", 1.0, 0.8)]),
    "coil_unit": dict(wind=[("block.trial_spawner.about_to_spawn_item", 0.8, 1.2)], blow=[("block.trial_spawner.eject_item", 0.9, 1.1)]),
    "gel_sample": dict(wind=[("block.slime_block.step", 1.0, 0.7)], blow=[("block.slime_block.break", 1.0, 0.7)]),
    "the_sweeper": dict(wind=[("block.trial_spawner.open_shutter", 1.0, 0.5)], blow=[("block.trial_spawner.close_shutter", 1.0, 0.5), ("block.heavy_core.hit", 1.0, 0.6)]),
    # Nightmare: cloth, breath, a door
    "sleepwalker": dict(wind=[("block.wool.hit", 1.0, 0.6)], blow=[("block.wool.break", 1.0, 0.6), ("item.armor.equip_leather", 1.0, 0.6)]),
    "night_terror": dict(wind=[("entity.phantom.swoop", 0.7, 0.7)], blow=[("entity.phantom.bite", 0.9, 0.7)]),
    "many_eyes": dict(wind=[("block.eyeblossom.open", 1.0, 0.7)], blow=[("block.eyeblossom.close", 1.0, 0.7)]),
    "the_tall_one": dict(wind=[("block.wooden_door.open", 0.8, 0.5)], blow=[("block.wooden_door.close", 1.0, 0.5)]),
    "the_waking": dict(wind=[("entity.warden.heartbeat", 1.0, 0.8)], blow=[("block.wool.fall", 1.0, 0.45), ("block.wool.break", 1.0, 0.45)]),
    # Sky Archipelago: canvas, wind, timber, storm
    "gale_harpy": dict(wind=[("item.armor.equip_elytra", 1.0, 1.1)], blow=[("entity.phantom.flap", 1.0, 1.3)]),
    "cloud_breeze": dict(wind=[("entity.breeze.inhale", 0.7, 1.2)], blow=[("entity.breeze.shoot", 0.7, 1.2)]),
    "sky_raider": dict(wind=[("item.crossbow.loading_middle", 1.0, 1.0)], blow=[("item.crossbow.shoot", 0.9, 1.0)]),
    "thunderhead": dict(wind=[("entity.breeze.charge", 0.7, 0.7)], blow=[("item.trident.thunder", 0.3, 1.3)]),
    "the_gale": dict(wind=[("entity.breeze.inhale", 1.0, 0.6)], blow=[("entity.breeze.wind_burst", 0.9, 0.6), ("block.bamboo_wood.break", 1.0, 0.6)]),
    # Abyssal Shrine: the dark, and very little else
    "abyss_watcher": dict(wind=[("block.sculk_sensor.clicking", 0.8, 0.6)], blow=[("block.sculk_sensor.clicking_stop", 0.9, 0.6)]),
    "silent_stalker": dict(wind=[("block.sculk.step", 0.3, 0.6)], blow=[("block.sculk.hit", 0.5, 0.6)]),
    "void_spawn": dict(wind=[("block.sculk_vein.hit", 1.0, 1.2)], blow=[("block.sculk_vein.break", 1.0, 1.2)]),
    "hollow_priest": dict(wind=[("block.sculk.charge", 0.8, 0.6)], blow=[("block.sculk_catalyst.bloom", 0.8, 0.6)]),
    "the_nothing": dict(wind=[("block.respawn_anchor.deplete", 0.5, 0.5)], blow=[("block.sculk_shrieker.break", 1.0, 0.5), ("block.sculk.spread", 1.0, 0.5)]),
}


def voices(mid):
    """{"wind": [...], "blow": [...]} for a monster, or {} if it has none here."""
    return FIGHTS.get(mid, {})
