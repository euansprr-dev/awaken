"""Gate world recipes after the Bell Crypt (SPEC 38.10 on), written from the tables below so every theme's five worlds share
their moods, blocks, rule and secrets the same way. The Bell Crypt's own recipes are kept by hand.

Run: python3 tools/art/specs/gate_worlds_recipes.py
Each world: kind and shape params (CaveEngine reads tunnel, wobble, roof, tower, sky, lake, sea, hex, square; open lands
also lid, slope, deep and night), its mood
biomes (gate_worlds_biomes.py writes them), hero pieces, life, its sound bed, events, rule, secrets (one leads to the theme's
secret world), its keepsake and, for a wonder, its signature creature. All the words are plain speech.
"""
import json
import os

ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "..", "src", "main", "resources", "data", "awaken", "gate_worlds")

THEMES = {}

# ---- Fungal Hollow ------------------------------------------------------------------------------------------------------
THEMES["fungal_hollow"] = dict(
    rule="spores",
    blocks={"wall": "awaken:fungal_rock", "wall_glow": "awaken:fungal_rock_veined", "floor": "awaken:fungal_loam",
            "floor_glow": "awaken:fungal_moss", "trim": "awaken:fungal_rock_veined", "stem": "awaken:glowcap_stem",
            "cap": "awaken:glowcap_cap", "gills": "awaken:glowcap_gills", "roots": "awaken:lumen_roots",
            "sprouts": "awaken:glowcap_sprouts", "threads": "awaken:mycelium_threads", "fall": "awaken:fungal_glowfall",
            "touch": "awaken:spore_puffball", "fluid": "minecraft:water"},
    moods=[("quiet", "Quiet", 3.0, []), ("bloom", "Bloom", 2.0, ["spores", "moths"]), ("drought", "Drought", 1.5, ["dust"]),
           ("migration", "Migration", 1.5, ["moths", "fireflies"])],
    life=["spores", "drips", "mist", "moths"],
    secret_world="mycelium_mind",
    lead=("the_threads", "The threads in the wall"),
    touch=("old_puffball", "The old puffball"),
    worlds={
        "glowing_deep": dict(name="The Glowing Deep", tier="everyday", rarity="common",
                             line="Glowcaps grow tall round a black lake down here, and the water falling into it glows.",
                             params={"lake": 1, "roof": 12}, hero=["glowfall"], sound="hollow_glow", events=["spore_burst"],
                             secrets=[("behind_the_falls", "Behind the glowing falls")], lead=True,
                             keepsake=("glowfall_vial", "A Vial of the Glowing Falls", "behind_the_falls")),
        "root_warrens": dict(name="The Root Warrens", tier="everyday", rarity="common",
                             line="Tight tunnels through roots that pulse with light. Keep your head down.",
                             params={"tunnel": 1.6, "roof": 7, "wobble": 2.4}, hero=["hanging_roots"], sound="hollow_roots",
                             events=["spore_burst"], secrets=[("root_heart", "The root's heart")], lead=True,
                             keepsake=("lumen_root", "A Lumen Root", "root_heart")),
        "spore_canopy": dict(name="The Spore Canopy", tier="deep", rarity="uncommon",
                             line="A forest of mushrooms forty blocks tall, and all of it underground.",
                             params={"tower": 45, "roof": 14}, hero=["giant_glowcaps"], sound="hollow_glow",
                             events=["spore_burst", "the_wakening"], secrets=[("hollow_stem", "The hollow stem")],
                             keepsake=("glowcap_slice", "A Slice of the Giant Glowcap", "hollow_stem")),
        "inside_the_bloom": dict(name="Inside the Bloom", tier="wonder", rarity="rare",
                                 line="You're inside one enormous fungus. These walls are its gills, and it's about to bloom.",
                                 params={"roof": 14, "tower": 30, "wobble": 1.2}, hero=["gill_fins"], sound="hollow_bloom",
                                 events=["spore_burst", "the_bloom"],
                                 secrets=[("heart_gill", "The heart gill"), ("the_bloom", "The bloom opening")],
                                 keepsake=("bloom_spore", "The Bloom's First Spore", "the_bloom"), signature="spore_mother",
                                 life=["spores", "moths", "mist"]),
        "mycelium_mind": dict(name="The Mycelium Mind", tier="secret", rarity="rare",
                              line="Every thread in this room is joined to every other. Say something and watch them light up.",
                              params={"roof": 9}, hero=["the_threads"], sound="hollow_bloom", events=[],
                              secrets=[("the_mind", "The middle of the mind"), ("the_thought", "A thought it shared with you")],
                              keepsake=("thought_thread", "A Thread That Remembers", "the_thought"), life=["spores", "mist"]),
    })

# ---- Ember Mines --------------------------------------------------------------------------------------------------------
THEMES["ember_mines"] = dict(
    rule="embers",
    blocks={"wall": "awaken:ember_basalt", "wall_glow": "awaken:ember_basalt_cracked", "floor": "awaken:ember_ash",
            "slag": "awaken:ember_slag", "trim": "awaken:mine_planks", "timber": "awaken:mine_timber", "planks": "awaken:mine_planks",
            "lamp": "awaken:mine_lamp", "crust": "awaken:magma_crust", "crystal": "awaken:ember_crystal",
            "fall": "awaken:ember_moltenfall", "touch": "awaken:ember_bellows", "fluid": "minecraft:lava"},
    moods=[("banked", "Banked", 3.0, []), ("eruption", "Eruption", 1.5, ["embers", "smoke"]), ("ashfall", "Ashfall", 1.5, ["ash"]),
           ("shift_change", "Shift change", 2.0, ["embers", "dust"])],
    life=["embers", "dust", "smoke", "bats"],
    secret_world="heart_seam",
    lead=("the_warm_wall", "The warm wall"),
    touch=("foremans_bellows", "The foreman's bellows"),
    worlds={
        "working_mine": dict(name="The Working Mine", tier="everyday", rarity="common",
                             line="Timbered tunnels, rails and lamps. Whoever worked here left in a hurry.",
                             params={"tunnel": 2.0, "wobble": 0.8, "roof": 9}, hero=["timbered_galleries"], sound="mine_works",
                             events=["tremor"], secrets=[("old_lift", "The old lift")], lead=True,
                             keepsake=("foremans_lamp", "The Foreman's Lamp", "old_lift"), fluid="minecraft:water"),
        "open_chasm": dict(name="The Open Chasm", tier="everyday", rarity="common",
                           line="A river of lava runs along the bottom of this chasm. Cross where the bridges let you.",
                           params={"lake": 1, "tower": 34, "roof": 14}, hero=["bridges"], sound="mine_chasm", events=["tremor"],
                           secrets=[("the_dropped_lamp", "The dropped lamp")], lead=True,
                           keepsake=("dropped_lamp", "The Dropped Lamp", "the_dropped_lamp")),
        "forge_below": dict(name="The Forge Below", tier="deep", rarity="uncommon",
                            line="Lava pours from the roof here into forges nobody has worked for years.",
                            params={"tower": 36, "roof": 12}, hero=["molten_falls"], sound="mine_chasm",
                            events=["tremor", "the_wakening"], secrets=[("first_anvil", "The first anvil")],
                            keepsake=("first_hammer", "The First Hammer", "first_anvil")),
        "cooling_sea": dict(name="The Cooling Sea", tier="wonder", rarity="rare",
                            line="Islands of crust float on a sea of glowing magma. Something big swims under it.",
                            params={"sea": 1, "sky": 40}, hero=["crust_islands"], sound="mine_sea",
                            events=["tremor", "slag_wyrm"], secrets=[("the_crust_shrine", "The shrine on the crust")],
                            keepsake=("wyrm_scale", "A Slag Wyrm's Scale", "slag_wyrm"), signature="slag_wyrm"),
        "heart_seam": dict(name="The Heart Seam", tier="secret", rarity="rare",
                           line="You're inside a geode of living ember. It's warm, like standing next to a heartbeat.",
                           params={"roof": 10, "wobble": 2.6}, hero=["ember_geode"], sound="mine_sea", events=[],
                           secrets=[("the_heart", "The heart of the seam")],
                           keepsake=("ember_heart", "The Ember Heart", "the_heart"), life=["embers", "smoke"]),
    })

# ---- Hive ---------------------------------------------------------------------------------------------------------------
THEMES["hive"] = dict(
    rule="hum",
    blocks={"wall": "awaken:hive_wax", "rim": "awaken:hive_wax_dark", "wall_glow": "awaken:hive_comb",
            "capped": "awaken:hive_comb_capped", "honey": "awaken:hive_honey_glow", "floor": "awaken:hive_floor",
            "floor_soft": "awaken:hive_pollen", "trim": "awaken:hive_wax_dark", "drip": "awaken:hive_drip",
            "touch": "awaken:hive_drum", "royal": "awaken:hive_royal", "petals": "awaken:hive_petals",
            "candle": "awaken:hive_candle", "fluid": "minecraft:water"},
    moods=[("drowsy", "Drowsy", 3.0, []), ("swarming", "Swarming", 1.5, ["fireflies", "dust"]),
           ("harvest", "Harvest", 2.0, ["drips", "petals"]), ("wax_moon", "Wax moon", 1.5, ["fireflies", "mist"])],
    life=["drips", "dust", "fireflies"],
    secret_world="queens_garden",
    lead=("the_sweet_air", "The sweet air"),
    touch=("humming_drum", "The humming drum"),
    worlds={
        "the_comb": dict(name="The Comb", tier="everyday", rarity="common",
                         line="Six-sided tunnels, and honey dripping from every roof.",
                         params={"hex": 1, "roof": 10, "wobble": 0.4}, hero=["honey_pools"], sound="hive_comb",
                         events=["the_hum"], secrets=[("sealed_cell", "The sealed cell")], lead=True,
                         keepsake=("sealed_honey", "A Jar of Sealed Honey", "sealed_cell")),
        "brood_hall": dict(name="The Brood Hall", tier="everyday", rarity="common",
                           line="A hive so tall you have to climb it, one ledge at a time.",
                           params={"tower": 40, "roof": 14}, hero=["ledges"], sound="hive_brood", events=["the_hum"],
                           secrets=[("the_nursery", "The nursery")], lead=True,
                           keepsake=("nursery_cell", "A Nursery Cell", "the_nursery")),
        "honey_lake": dict(name="The Honey Lake", tier="deep", rarity="uncommon",
                           line="Wax islands on a lake of honey. It's sweeter than it is safe.",
                           params={"sea": 1, "sky": 34}, hero=["honey_sea"], sound="hive_lake",
                           events=["the_hum", "the_wakening"], secrets=[("drowned_comb", "The drowned comb")],
                           keepsake=("drowned_comb", "The Drowned Comb", "drowned_comb")),
        "the_swarm": dict(name="The Swarm", tier="wonder", rarity="rare",
                          line="The walls of comb in here move around you. Listen for the singing.",
                          params={"hex": 1, "roof": 12, "wobble": 0.6}, hero=["moving_walls"], sound="hive_brood",
                          events=["the_hum", "queens_choir"], secrets=[("still_cell", "The still cell")],
                          keepsake=("choir_wing", "A Wing from the Queen's Choir", "queens_choir"), signature="queens_choir"),
        "queens_garden": dict(name="The Queen's Garden", tier="secret", rarity="rare",
                              line="A sunlit meadow of flowers, right in the middle of the hive.",
                              params={"roof": 14}, hero=["honey_skylight"], sound="hive_lake", events=[],
                              secrets=[("the_queens_seat", "The queen's seat")],
                              keepsake=("queens_crown", "The Queen's Wax Crown", "the_queens_seat"), life=["petals", "fireflies"]),
    })

# ---- Overgrown Temple ---------------------------------------------------------------------------------------------------
THEMES["overgrown_temple"] = dict(
    rule="offerings",
    blocks={"wall": "awaken:temple_stone", "wall_mossy": "awaken:temple_stone_mossy", "carving": "awaken:temple_carving",
            "floor": "awaken:temple_tiles", "trim": "awaken:temple_carving", "root": "awaken:temple_root",
            "vines": "awaken:temple_vines", "moss": "awaken:temple_moss", "touch": "awaken:temple_brazier",
            "idol": "awaken:temple_idol", "fern": "awaken:temple_fern", "bulb": "awaken:temple_lantern_bulb",
            "canopy": "awaken:temple_canopy", "fall": "awaken:temple_waterfall", "fluid": "minecraft:water"},
    moods=[("still", "Still", 3.0, []), ("monsoon", "Monsoon", 1.5, ["rain"]), ("fireflies", "Fireflies", 2.0, ["fireflies"]),
           ("overgrowth", "Overgrowth", 1.5, ["petals", "spores"])],
    life=["drips", "mist", "fireflies", "moths"],
    secret_world="sleeping_idol",
    lead=("the_green_door", "The green door"),
    touch=("first_offering", "The first offering"),
    worlds={
        "jungle_courts": dict(name="The Jungle Courts", tier="everyday", rarity="common",
                              line="Old courtyards under a roof of leaves, with braziers waiting for someone to light them.",
                              params={"square": 1, "wobble": 0.6, "roof": 14}, hero=["colonnade"], sound="temple_courts",
                              events=["the_rains", "the_wakening"], secrets=[("overgrown_shrine", "The overgrown shrine")],
                              lead=True, keepsake=("jade_offering", "A Jade Offering", "overgrown_shrine")),
        "root_halls": dict(name="The Root Halls", tier="everyday", rarity="common",
                           line="Roots as thick as towers have split these halls apart.",
                           params={"roof": 12, "wobble": 1.4}, hero=["great_roots"], sound="temple_halls",
                           events=["the_wakening"], secrets=[("root_cellar", "The root cellar")], lead=True,
                           keepsake=("cellar_seed", "A Seed from the Root Cellar", "root_cellar")),
        "waterfall_temple": dict(name="The Waterfall Temple", tier="deep", rarity="uncommon",
                                 line="A temple built beside a waterfall sixty blocks high.",
                                 params={"tower": 60, "roof": 14}, hero=["the_fall"], sound="temple_falls",
                                 events=["the_rains", "the_wakening"], secrets=[("behind_the_water", "Behind the water")],
                                 keepsake=("falls_pearl", "A Pearl from Behind the Falls", "behind_the_water")),
        "great_tree": dict(name="The Great Tree", tier="wonder", rarity="rare",
                           line="A whole temple built inside one enormous tree. The one who looks after it is still here.",
                           params={"tower": 44, "roof": 14}, hero=["the_trunk"], sound="temple_courts",
                           events=["the_wakening", "root_saint"], secrets=[("heartwood", "The heartwood")],
                           keepsake=("saints_branch", "The Root Saint's Branch", "root_saint"), signature="root_saint"),
        "sleeping_idol": dict(name="The Sleeping Idol", tier="secret", rarity="rare",
                              line="A stone face as big as a hill, asleep, with a doorway where its mouth should be.",
                              params={"roof": 16}, hero=["the_face"], sound="temple_halls", events=[],
                              secrets=[("the_idols_mouth", "Inside the idol's mouth")],
                              keepsake=("idols_eye", "The Idol's Eye", "the_idols_mouth"), life=["mist", "moths"]),
    })


# ---- Drowned Archive (38.11) --------------------------------------------------------------------------------------------
THEMES["drowned_archive"] = dict(
    rule="reading",
    blocks={"wall": "awaken:archive_stone", "wall_glow": "awaken:archive_stone_carved", "floor": "awaken:archive_tiles",
            "trim": "awaken:archive_stone_carved", "shelf": "awaken:archive_shelf", "shelf_lit": "awaken:archive_shelf_lit",
            "planks": "awaken:archive_planks", "lamp": "awaken:archive_lamp", "paper": "awaken:archive_paper",
            "kelp": "awaken:archive_kelp", "glass": "awaken:archive_sea_glass", "fall": "awaken:archive_inkfall",
            "touch": "awaken:archive_lectern", "bridge": "awaken:archive_paper_bridge", "fluid": "minecraft:water"},
    moods=[("quiet", "Quiet", 3.0, []), ("high_tide", "High tide", 1.5, ["drips", "mist"]), ("lamplit", "Lamplit", 2.0, ["dust"]),
           ("paper_storm", "Paper storm", 1.5, ["paper"])],
    life=["paper", "drips", "pale_fish", "dust"],
    secret_world="last_readers_room",
    lead=("the_loose_shelf", "The loose shelf"),
    touch=("the_open_book", "The open book"),
    worlds={
        "stacks": dict(name="The Stacks", tier="everyday", rarity="common",
                       line="Shelves three storeys high, with galleries running round them. Somebody kept reading after the sea came in.",
                       params={"square": 1, "roof": 17, "wobble": 0.6}, hero=["shelf_rows", "galleries"], sound="archive_stacks",
                       events=["page_storm"], secrets=[("the_overdue_shelf", "The overdue shelf")], lead=True,
                       keepsake=("overdue_book", "A Very Overdue Book", "the_overdue_shelf")),
        "atrium": dict(name="The Atrium", tier="everyday", rarity="common",
                       line="A great hall under a dome of sea glass, with clear water on the floor and light coming down through it.",
                       params={"lake": 1, "roof": 16, "wobble": 0.8}, hero=["sea_glass_dome"], sound="archive_atrium",
                       events=["the_tide"], secrets=[("the_pool_coin", "The coin in the pool")], lead=True,
                       keepsake=("pool_coin", "A Coin from the Atrium Pool", "the_pool_coin")),
        "sunken_shelves": dict(name="The Sunken Shelves", tier="deep", rarity="uncommon",
                               line="The bookshelves stand on the sea floor here, and their reading lamps are still burning under the water.",
                               params={"sea": 1, "sky": 30}, hero=["standing_shelves"], sound="archive_atrium",
                               events=["the_tide", "the_wakening"], secrets=[("the_lit_shelf", "The shelf that's still lit")],
                               keepsake=("drowned_lamp", "A Lamp That Burns Under Water", "the_lit_shelf")),
        "inkwell": dict(name="The Inkwell", tier="wonder", rarity="rare",
                        line="A sea of ink, and bridges made of paper that fold out under your feet as you walk.",
                        params={"sea": 1, "sky": 34}, hero=["inkfalls", "paper_bridges"], sound="archive_ink",
                        events=["page_storm", "bookworm"], secrets=[("the_ink_well", "The bottom of the well")],
                        keepsake=("bookworm_tooth", "The Bookworm's Tooth", "bookworm"), signature="bookworm"),
        "last_readers_room": dict(name="The Last Reader's Room", tier="secret", rarity="rare",
                                  line="A dry room in a bubble under the sea. The last reader left a book open on the desk.",
                                  params={"roof": 9}, hero=["sea_glass_skylight"], sound="archive_ink", events=[],
                                  secrets=[("the_last_page", "The last page")],
                                  keepsake=("last_page", "The Last Page", "the_last_page"), life=["paper", "dust"]),
    })

# ---- Clockwork Foundry --------------------------------------------------------------------------------------------------
THEMES["clockwork_foundry"] = dict(
    rule="steam",
    blocks={"wall": "awaken:foundry_iron", "floor": "awaken:foundry_brick", "brick": "awaken:foundry_brick",
            "trim": "awaken:foundry_brass", "gear": "awaken:foundry_gear", "grate": "awaken:foundry_grille",
            "pipe": "awaken:foundry_pipe", "wall_glow": "awaken:foundry_furnace", "lamp": "awaken:foundry_bulb",
            "fall": "awaken:foundry_steam", "sparks": "awaken:foundry_sparks", "cables": "awaken:foundry_cables",
            "touch": "awaken:foundry_valve", "fluid": "minecraft:water"},
    moods=[("idle", "Idle", 3.0, []), ("overdrive", "Overdrive", 1.5, ["embers", "steam"]), ("steam", "Steam", 1.5, ["steam", "mist"]),
           ("power_cut", "Power cut", 1.0, ["dust"])],
    life=["steam", "dust", "embers", "rats"],
    secret_world="orrery",
    lead=("the_warm_pipe", "The warm pipe"),
    touch=("the_old_valve", "The old valve"),
    worlds={
        "works_floor": dict(name="The Works Floor", tier="everyday", rarity="common",
                            line="Catwalks over pits full of turning gears. The machines are still running, and nobody's minding them.",
                            params={"square": 1, "roof": 12, "wobble": 0.5}, hero=["machine_pits"], sound="foundry_works",
                            events=["overdrive"], secrets=[("the_spare_cog", "The spare cog")], lead=True,
                            keepsake=("spare_cog", "A Spare Cog", "the_spare_cog")),
        "pipe_maze": dict(name="The Pipe Maze", tier="everyday", rarity="common",
                          line="Ducts and pipes all the way up, and steam coming out of every joint. Follow the warm ones.",
                          params={"tunnel": 1.8, "roof": 8, "wobble": 1.2}, hero=["pipe_runs", "steam_curtains"],
                          sound="foundry_pipes", events=["the_power_cut"], secrets=[("the_duct_map", "The duct map")],
                          lead=True, keepsake=("duct_map", "A Map of the Ducts", "the_duct_map")),
        "great_engine": dict(name="The Great Engine", tier="deep", rarity="uncommon",
                             line="A gear twenty blocks across turns over your head. The whole room shakes with it.",
                             params={"tower": 40, "roof": 14}, hero=["the_great_gear"], sound="foundry_engine",
                             events=["overdrive", "the_wakening"], secrets=[("the_governor", "The governor")],
                             keepsake=("brass_governor", "The Engine's Brass Governor", "the_governor")),
        "stopped_hour": dict(name="The Stopped Hour", tier="wonder", rarity="rare",
                             line="Something exploded in here, and then time stopped. The pieces are still hanging in the air.",
                             params={"tower": 34, "roof": 14}, hero=["frozen_debris"], sound="foundry_engine",
                             events=["the_power_cut", "clock_keeper"], secrets=[("the_stopped_clock", "The stopped clock")],
                             keepsake=("keepers_key", "The Clock-Keeper's Key", "clock_keeper"), signature="clock_keeper"),
        "orrery": dict(name="The Orrery", tier="secret", rarity="rare",
                       line="Brass planets turning round a little sun in the dark. One of them is small enough to hold.",
                       params={"roof": 10}, hero=["brass_planets"], sound="foundry_engine", events=[],
                       secrets=[("the_little_planet", "The little planet")],
                       keepsake=("little_planet", "The Little Brass Planet", "the_little_planet"), life=["dust", "fireflies"]),
    })

# ---- Glass Desert -------------------------------------------------------------------------------------------------------
THEMES["glass_desert"] = dict(
    rule="sun",
    blocks={"floor": "awaken:desert_sand", "floor_rippled": "awaken:desert_sand_rippled", "wall": "awaken:desert_sandstone",
            "wall_glow": "awaken:desert_sandstone_carved", "trim": "awaken:desert_sandstone_carved", "brick": "awaken:desert_brick",
            "tile": "awaken:desert_tile", "column": "awaken:desert_column", "glass": "awaken:desert_glass",
            "glass_glow": "awaken:desert_glass_glowing", "crystal": "awaken:desert_crystal", "grass": "awaken:desert_grass",
            "fall": "awaken:desert_sandfall", "touch": "awaken:desert_mirror", "fluid": "minecraft:water"},
    moods=[("high_sun", "High sun", 3.0, []), ("golden_hour", "Golden hour", 2.0, ["sand"]),
           ("sandstorm", "Sandstorm", 1.5, ["sand", "dust"]), ("night", "Night", 1.5, ["fireflies"])],
    life=["sand", "dust", "moths"],
    secret_world="hourglass",
    lead=("the_singing_sand", "The singing sand"),
    touch=("the_first_mirror", "The first mirror"),
    worlds={
        "buried_colossus": dict(name="The Buried Colossus", tier="everyday", rarity="common", kind="open", tall=True,
                                line="Dunes under a real sun, a line of old columns, and a stone head as big as a hill looking out of the sand.",
                                params={"lid": 16, "slope": 0.7, "lake": 1, "tunnel": 2.6}, hero=["the_head", "colonnade"],
                                sound="desert_wind", events=["sandstorm"], secrets=[("the_colossus_eye", "The colossus's eye")],
                                lead=True, keepsake=("colossus_eye", "A Shard of the Colossus's Eye", "the_colossus_eye")),
        "glass_caves": dict(name="The Glass Caves", tier="everyday", rarity="common",
                            line="Caves under the desert where the sand turned to glass, and the light comes through the walls.",
                            params={"roof": 12, "wobble": 1.4}, hero=["glass_walls", "sandfalls"], sound="desert_glass",
                            events=["the_mirage"], secrets=[("the_sunstone", "The sunstone")], lead=True,
                            keepsake=("sunstone", "A Sunstone", "the_sunstone"), life=["dust", "drips", "shaft_dust"]),
        "sunken_city": dict(name="The Sunken City", tier="deep", rarity="uncommon", kind="open", tall=True,
                            line="The rooftops of a whole city poke out of a basin of sand. Most of it is still down there.",
                            params={"lid": 18, "slope": 0.2, "square": 1}, hero=["rooftops", "fallen_columns"],
                            sound="desert_city", events=["sandstorm", "the_wakening"],
                            secrets=[("the_buried_door", "The buried door")],
                            keepsake=("door_key", "The Key to the Buried Door", "the_buried_door")),
        "mirage": dict(name="The Mirage", tier="wonder", rarity="rare", kind="open", tall=True,
                       line="There's a palace on the far side of the sand. However long you walk, it never gets any closer.",
                       params={"lid": 18, "slope": 0.5, "lake": 1}, hero=["the_palace"], sound="desert_wind",
                       events=["the_mirage", "sand_walker"], secrets=[("the_mirror_pool", "The mirror pool")],
                       keepsake=("walkers_glass", "The Sand Walker's Glass Heart", "sand_walker"), signature="sand_walker"),
        "hourglass": dict(name="The Hourglass", tier="secret", rarity="rare",
                          line="A little city at the bottom of an hourglass, with sand falling on it from the neck above.",
                          params={"roof": 16}, hero=["the_neck"], sound="desert_city", events=[],
                          secrets=[("the_neck", "Under the neck")],
                          keepsake=("hourglass_sand", "A Pinch of the Hourglass's Sand", "the_neck"), life=["sand", "dust"]),
    })

# ---- Lantern Night Market (friendly: no fights) -------------------------------------------------------------------------
THEMES["lantern_night_market"] = dict(
    rule="wishes",
    blocks={"floor": "awaken:market_cobbles", "boards": "awaken:market_boards", "wall": "awaken:market_plaster",
            "trim": "awaken:market_timber", "timber": "awaken:market_timber", "roof": "awaken:market_roof",
            "wall_glow": "awaken:market_paper_wall", "lantern": "awaken:market_lantern", "lights": "awaken:market_lights",
            "cloth": "awaken:market_cloth", "goods": "awaken:market_goods", "boat_lamp": "awaken:market_boat_lamp",
            "touch": "awaken:market_wish_lantern", "fluid": "minecraft:water"},
    moods=[("market_night", "Market night", 3.0, []), ("fireworks", "Fireworks", 1.5, ["fireflies"]),
           ("rain", "Rain", 1.5, ["rain"]), ("ghost_market", "Ghost market", 1.0, ["procession", "mist"])],
    life=["fireflies", "moths", "smoke"],
    secret_world="brokers_vault",
    lead=("the_red_door", "The red door"),
    touch=("the_first_wish", "The first wish"),
    worlds={
        "lantern_street": dict(name="The Lantern Street", tier="everyday", rarity="common", kind="open", tall=True,
                               line="Stalls and canals under strings of lanterns, and somebody haggling on every corner.",
                               params={"night": 1, "lid": 16, "tunnel": 3.2, "slope": 0.1}, hero=["canals", "facades"],
                               sound="market_street", events=["lantern_release"],
                               secrets=[("the_canal_shrine", "The canal shrine")], lead=True,
                               keepsake=("shrine_bell", "A Bell from the Canal Shrine", "the_canal_shrine")),
        "rooftops": dict(name="The Rooftops", tier="everyday", rarity="common", kind="open", tall=True,
                         line="The market's moved up onto the roofs. Bridges join them, and the streets are a long way down.",
                         params={"night": 1, "lid": 18, "deep": 14}, hero=["streets", "the_pavilion"], sound="market_street",
                         events=["fireworks"], secrets=[("the_highest_roof", "The highest roof")], lead=True,
                         keepsake=("weathervane", "The Market's Weathervane", "the_highest_roof")),
        "floating_market": dict(name="The Floating Market", tier="deep", rarity="uncommon", kind="open", tall=True,
                                line="Boats tied up together on a lagoon, every one of them a stall with a lamp in it.",
                                params={"night": 1, "lid": 16, "sea": 1}, hero=["boats"], sound="market_water",
                                events=["lantern_release", "fireworks"], secrets=[("the_lost_boat", "The lost boat")],
                                keepsake=("paper_boat", "A Paper Boat", "the_lost_boat")),
        "festival": dict(name="The Festival", tier="wonder", rarity="rare", kind="open", tall=True,
                         line="Tonight's the festival. When the big lantern goes up, a thousand more go up with it.",
                         params={"night": 1, "lid": 20, "square": 1}, hero=["the_great_lantern"], sound="market_festival",
                         events=["fireworks", "the_festival"],
                         secrets=[("the_wish_tree", "The wish tree"), ("the_festival", "The lanterns going up")],
                         keepsake=("spirit_lantern", "A Lantern Spirit's Lantern", "the_festival"), signature="lantern_spirits"),
        "brokers_vault": dict(name="The Broker's Vault", tier="secret", rarity="rare",
                              line="Where the Broker keeps every wish anyone ever paid for, each one in its own lantern.",
                              params={"roof": 10}, hero=["wish_lanterns"], sound="market_festival", events=[],
                              secrets=[("the_wish_ledger", "The wish ledger")],
                              keepsake=("wish_ledger", "A Page from the Wish Ledger", "the_wish_ledger"), life=["fireflies", "dust"]),
    })

def recipe(theme, t, wid, w):
    secret = w["tier"] == "secret"
    secrets = []
    if not secret:
        secrets.append({"id": t["touch"][0], "name": t["touch"][1]})
    for sid, name in w["secrets"]:
        secrets.append({"id": sid, "name": name})
    if w.get("lead"):
        secrets.append({"id": t["lead"][0], "name": t["lead"][1], "leads_to": f"awaken:{theme}/{t['secret_world']}"})
    blocks = dict(t["blocks"])
    if "fluid" in w:
        blocks["fluid"] = w["fluid"]
    out = {
        "schema": 1,
        "theme": theme,
        "name": w["name"],
        "line": w["line"],
        "tier": w["tier"],
        "rarity": w["rarity"],
        "kind": w.get("kind", "caves"),
        "roles": ["floor"] if secret else ["floor", "boss"],
        "params": w["params"],
        "biome": f"awaken:gate_world/{wid}",
        "moods": [] if secret else [
            dict({"id": mid, "name": name, "weight": weight, "biome": "" if i == 0 else f"awaken:gate_world/{wid}_{mid}"},
                 **({"life": life} if life else {}))
            for i, (mid, name, weight, life) in enumerate(t["moods"])],
        "blocks": blocks,
        "hero": w["hero"],
        "life": w.get("life", t["life"]),
        "sound": "awaken:gate.world." + w["sound"],
        "events": w["events"],
        "rule": t["rule"],
        "secrets": secrets,
        "keepsake": w["keepsake"][0],
        "keepsake_name": w["keepsake"][1],
        "keepsake_from": w["keepsake"][2],
    }
    if w.get("tall"):
        out["tall"] = True
    if w.get("signature"):
        out["signature"] = w["signature"]
    return out


def main():
    n = 0
    for theme, t in THEMES.items():
        os.makedirs(os.path.join(ROOT, theme), exist_ok=True)
        for wid, w in t["worlds"].items():
            with open(os.path.join(ROOT, theme, wid + ".json"), "w") as f:
                json.dump(recipe(theme, t, wid, w), f, indent=2)
                f.write("\n")
            n += 1
    print(n, "recipes")


if __name__ == "__main__":
    main()
