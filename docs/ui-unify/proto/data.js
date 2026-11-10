/* Sample data for the prototype: Euan's moment (SPEC 27.13). E-rank, level 7, the fire set, a bed built, back home:
   what next? Titles, names and numbers are the game's own, read from tour shots and data files on 10 Nov 2026.
   Everything here is simulated: the real page reads it from the server (tasks_view, HearthView, the profile). */
window.AW_DATA = {
  you: { name: 'Euan', level: 7, rank: 'E', xp: 0.38, points: 3, title: '',
    stats: [['STR', 'Strength', 6, 'Your blows hit harder.'], ['AGI', 'Agility', 7, 'You move faster, and Flow builds sooner.'],
      ['VIT', 'Vitality', 5, 'More health, and you mend faster.'], ['INT', 'Intellect', 5, 'More energy, and powers come back sooner.'],
      ['PER', 'Perception', 5, 'You see danger and hidden things from further off.'], ['SPI', 'Spirit', 5, 'The Veil holds you longer.']],
    road: [[8, 'Essence', 'i_essence_shard'], [9, 'A Power Core', 'i_power_core'], [10, 'The D-rank trial', 'i_trophy']],
    wearing: 'wildhide',
    truths: { yours: 2, near: [
      ['I am fast.', 'Sprint 5,000 blocks.', 0.62, 'You run a little faster for good.'],
      ['I am awake in my dreams.', 'Become lucid in five dreams.', 0.2, 'You start with 20 more Lucidity.'],
      ['I am never caught.', 'Dodge 100 blows.', 0.41, 'Your dodge comes back sooner.']] },
    lately: [['i_handbell', 'Bell #07 · The Ember Bell', '1 min'], ['b_bed', 'Bedroom recognised', '1 min'], ['v_campfire', 'First shelter', '1 min']],
    deeds: [[38, 'Monsters slain'], [1, 'Bells held'], [0, 'Gates cleared']] },

  lanes: [['hearth', 'Hearth', 'v_campfire'], ['armour', 'Armour', 'i_wildhide_chestplate'], ['weapons', 'Weapons', 'w_longsword'],
    ['bells', 'Bells', 'v_bell'], ['gates', 'Gates', 'i_core_shard'], ['out', 'Out there', 'v_compass_00']],

  steps: [
    { key: 'hearth/armoury', lane: 'hearth', top: true, kind: 'make', title: 'Build your Armoury', why: 'Your first armour is made here, before your first gate.',
      pics: ['v_armor_stand', 'b_crafting_table'], room: 'One closed room',
      make: [
        { name: 'Fitting Stand', icon: 'v_armor_stand', have: 0, need: 1, w: 3, cells: ['v_stick', 'v_copper_ingot', 'v_stick', '', 'b_wool', '', 'b_planks', 'b_planks', 'b_planks'] },
        { name: 'Crafting table', icon: 'b_crafting_table', have: 0, need: 1, w: 2, cells: ['b_planks', 'b_planks', 'b_planks', 'b_planks'] }],
      place: ['Close by', 35], then: 'armour/first', met: 0, of: 3 },
    { key: 'hearth/guide', lane: 'hearth', kind: 'thing', title: 'See what your home needs', why: 'Right-click your campfire.', pics: ['v_campfire'], big: 'v_campfire', place: ['Close by', 35], met: 0, of: 1 },
    { key: 'kin/light', lane: 'hearth', kind: 'lead', title: 'Nothing lights your yard at night', why: 'Something out there glows. Look along the edges of woods at dusk.', pics: ['v_lantern'], big: 'v_lantern', dark: true, isNew: true },
    { key: 'armour/first', lane: 'armour', kind: 'choose', title: 'Choose your first armour', why: 'Made at a Fitting Stand. Wear it before your first gate.',
      pics: ['i_wildhide_helmet', 'i_bellguard_helmet', 'i_rootwarden_helmet', 'i_amberwing_helmet'],
      choices: [
        { id: 'wildhide', name: 'Wildhide', role: 'Strider', armour: 10, needs: [['v_leather', 'Hide', 2, 6], ['v_bone', 'Bone', 1, 2], ['i_essence_shard', 'Essence Shard', 1, 1]] },
        { id: 'bellguard', name: 'Bellguard', role: 'Bulwark', armour: 12, needs: [['i_bell_bronze_ingot', 'Bell bronze', 0, 4], ['v_leather', 'Hide', 2, 4], ['i_essence_shard', 'Essence Shard', 1, 1]] },
        { id: 'rootwarden', name: 'Rootwarden', role: 'Channeler', armour: 9, needs: [['b_log', 'Old wood', 0, 6], ['i_antler_tine', 'Antler Tine', 0, 1], ['v_string', 'String', 0, 3], ['v_feather', 'Feather', 0, 2], ['i_essence_shard', 'Essence Shard', 1, 1]] },
        { id: 'amberwing', name: 'Amberwing', role: 'Duelist', armour: 10, needs: [['i_dusk_feather', 'Dusk Feather', 0, 4], ['v_leather', 'Hide', 2, 4], ['v_string', 'String', 0, 2], ['i_essence_shard', 'Essence Shard', 1, 1]] }],
      met: 1, of: 3 },
    { key: 'weapons/hale', lane: 'weapons', kind: 'go', title: 'Talk to Hale', why: 'He has a free weapon for you.', pics: ['w_longsword'], big: 'w_longsword', place: ['1,220 blocks west', 270], isNew: true, met: 0, of: 1 },
    { key: 'bells/lead', lane: 'bells', kind: 'lead', title: 'Find where that bell’s note came from', why: 'You heard it from your fire, somewhere to the north.', pics: ['v_bell'], big: 'v_bell', dark: true, place: ['North of here', 0], isNew: true },
    { key: 'rank/points', lane: 'gates', kind: 'thing', title: 'Spend your 3 points', why: 'Points you haven’t spent make you no stronger.', pics: ['i_power_core'], big: 'i_power_core', link: 'status', met: 0, of: 1 },
    { key: 'trail/hunter', lane: 'gates', kind: 'thing', title: 'Hold sneak as you land to roll', why: 'A roll takes the sting out of a long fall.', pics: ['i_runner_rope'], big: 'i_runner_rope', want: true, met: 0, of: 5 },
    { key: 'out/gate', lane: 'out', kind: 'go', title: 'A gate is drawing breath', why: 'An E-rank gate. Its floors make you stronger.', pics: ['i_core_shard'], big: 'i_core_shard', place: ['320 blocks north', 350] },
  ],
  later: { hearth: 6, armour: 0, weapons: 1, bells: 2, gates: 3 },
  foot: 'At D-rank your home can grow, and your next armour opens.',

  // ---- the same page with a lot on it: C-rank, as the 10 Nov tour had it (run 42754069258) ----
  dense: {
    steps: [
      { key: 'armour/rank', lane: 'armour', top: true, kind: 'pieces', title: 'Make your C-rank armour', why: 'Forged at a Fitting Stand, with an anvil and the Forge near it.',
        pics: ['i_gatewrought_helmet', 'i_gatewrought_chestplate', 'i_gatewrought_leggings', 'i_gatewrought_boots'], bigs: ['i_gatewrought_helmet', 'i_gatewrought_chestplate', 'i_gatewrought_leggings', 'i_gatewrought_boots'],
        label: 'Gatewrought · Duelist · 20 armour', needs: [['v_diamond', 'Diamond', 0, 14], ['i_keepsake', 'A D-rank Lord’s relic', 0, 1], ['v_leather', 'Hide', 3, 8], ['v_string', 'String', 0, 4], ['i_essence_shard', 'Essence Shard', 2, 2]],
        then: 'weapons/anvil', met: 1, of: 5 },
      { key: 'hearth/guide', lane: 'hearth', kind: 'thing', title: 'See what your home needs', why: 'Right-click your Anchor.', pics: ['v_campfire'], big: 'v_campfire', place: ['Close by', 35], met: 0, of: 1 },
      { key: 'kin/light', lane: 'hearth', kind: 'lead', title: 'Nothing lights your yard at night', why: 'Something out there glows. Look along the edges of woods at dusk.', pics: ['v_lantern'], big: 'v_lantern', dark: true },
      { key: 'hearth/add', lane: 'hearth', kind: 'thing', title: 'Add to your home', why: 'Three things your home could have next. Pick one.', pics: ['b_chest', 'v_bell', 'b_bookshelf'], big: 'b_chest', isNew: true, met: 0, of: 4 },
      { key: 'weapons/hale', lane: 'weapons', kind: 'go', title: 'Talk to Hale', why: 'He has a free weapon for you.', pics: ['w_longsword'], big: 'w_longsword', place: ['1,220 blocks west', 270], met: 0, of: 1 },
      { key: 'weapons/anvil', lane: 'weapons', kind: 'make', title: 'Set up a War Anvil', why: 'It makes stronger weapons than a crafting table can.',
        pics: ['b_war_anvil'], room: 'Within 6 blocks of your Forge',
        make: [{ name: 'War Anvil', icon: 'b_war_anvil', have: 0, need: 1, w: 3, cells: ['b_cobble', 'b_cobble', 'b_cobble', '', 'v_copper_ingot', '', '', 'b_log', ''] }],
        needs: [['b_cobble', 'Cobblestone', 0, 3], ['v_copper_ingot', 'Copper Ingot', 0, 1], ['b_log', 'Logs', 0, 1]], isNew: true, met: 0, of: 3 },
      { key: 'bells/lead', lane: 'bells', kind: 'lead', title: 'Find where that bell’s note came from', why: 'You heard it from your fire, somewhere to the north.', pics: ['v_bell'], big: 'v_bell', dark: true, place: ['North of here', 0], isNew: true },
      { key: 'bells/lead2', lane: 'bells', kind: 'lead', title: 'Find the Barrow Bell', why: 'A letter spoke of a bell kept under a hill.', pics: ['v_bell'], big: 'v_bell', dark: true },
      { key: 'rank/points', lane: 'gates', kind: 'thing', title: 'Spend your 81 points', why: 'Points you haven’t spent make you no stronger.', pics: ['i_power_core'], big: 'i_power_core', link: 'status', met: 0, of: 1 },
      { key: 'gates/twist', lane: 'gates', kind: 'thing', title: 'Clear a gate with a twist', why: 'Some gates change the rules. Their spoils are better.', pics: ['i_core_shard'], big: 'i_core_shard', isNew: true, met: 0, of: 1 },
      { key: 'trail/hunter', lane: 'gates', kind: 'thing', title: 'Hold sneak as you land to roll', why: 'A roll takes the sting out of a long fall.', pics: ['i_runner_rope'], big: 'i_runner_rope', want: true, met: 0, of: 5 },
      { key: 'out/veil', lane: 'out', kind: 'thing', title: 'Meditate for 10 seconds, then let go of G', why: 'You step out of your body.', pics: ['p_third_eye'], big: 'p_third_eye' },
    ],
    later: { hearth: 10, armour: 0, weapons: 2, bells: 4, gates: 2 }, foot: 'At B-rank your next armour opens.', bg: 'bg_night',
    you: { name: 'Dev', level: 28, rank: 'C', xp: 0.04, points: 81, next: 'B-rank at level 45',
      stats: [['STR', 'Strength', 5, 'Your blows hit harder.'], ['AGI', 'Agility', 5, 'You move faster, and Flow builds sooner.'], ['VIT', 'Vitality', 5, 'More health, and you mend faster.'],
        ['INT', 'Intellect', 5, 'More energy, and powers come back sooner.'], ['PER', 'Perception', 5, 'You see danger and hidden things from further off.'], ['SPI', 'Spirit', 5, 'The Veil holds you longer.']],
      road: [[29, 'Your mentor', 'i_lord_heart'], [30, 'The far towns', 'v_map'], [31, 'The Drowned Choir', 'v_bell']], wearing: 'wildhide',
      truths: { yours: 0, near: [['I am awake in my dreams.', 'Become lucid in five dreams.', 0, 'You start with 20 more Lucidity.'], ['I am fast.', 'Sprint 50 kilometres.', 0.02, 'You move 5% faster.'], ['I am more than my body.', 'Spend ten minutes outside your body.', 0, 'Your cord reaches 64 blocks further.']] },
      lately: [['v_bell', 'Bell #60 · The Second', 'just now'], ['v_campfire', 'The Hearth burns brighter', 'just now'], ['i_gatewrought_chestplate', 'Gatewrought gear is open', 'just now']],
      deeds: [[0, 'Monsters slain'], [3, 'Bells held'], [0, 'Gates cleared']] },
    home: { name: 'Wadecote', tier: 'Tier I · Homestead', comfort: 0, style: 'Forge I', nooks: 4, built: ['shelter', 'bedroom', 'armoury', 'forge'], next: 'kitchen', kin: [] } },

  // ---- a new hunter, minutes in: one thing to do, nothing listed that hasn't opened ----
  fresh: {
    steps: [
      { key: 'hearth/fire', lane: 'hearth', top: true, kind: 'go', title: 'Set your fire down', why: 'Carry a coal from Ilse’s fire up the north road, and set it down where you’d live.', pics: ['v_campfire'], big: 'v_campfire', place: ['410 blocks north', 5], met: 0, of: 1 }],
    later: {}, foot: '' },

  home: { name: 'Shellburn', tier: 'Hearthfire', comfort: 1, style: 'Timber', nooks: 3,
    rooms: [
      ['shelter', 'Shelter', 'v_oak_door', 'built', 'Rest heals here.', []],
      ['bedroom', 'Bedroom', 'b_bed', 'built', 'You wake Rested.', [['b_bed', 'Bed', 1, 1]]],
      ['armoury', 'Armoury', 'v_armor_stand', 'next', 'Armour made here lasts a quarter longer.', [['v_armor_stand', 'Fitting Stand', 0, 1], ['b_crafting_table', 'Crafting table', 0, 1]]],
      ['sanctuary', 'Sanctuary', 'b_wool', 'started', 'Better dreams, and you heal faster.', [['b_bed', 'Bed', 1, 1], ['b_wool', 'Meditation Mat', 0, 1]]],
      ['kitchen', 'Kitchen', 'b_furnace', 'open', 'Home-cooked meals last longer.', [['b_furnace', 'A cooker', 0, 1], ['b_crafting_table', 'Crafting table', 0, 1], ['b_barrel', 'Barrel', 0, 1]]],
      ['forge', 'Forge', 'b_anvil', 'open', 'The Forge can temper higher.', [['b_anvil', 'Anvil', 0, 1], ['b_forge', 'The Forge', 0, 1], ['v_armor_stand', 'Armour stand', 0, 1]]],
      ['study', 'Study', 'b_bookshelf', 'open', 'Enchantments written here go deeper.', [['b_lectern', 'Lectern', 0, 1], ['b_bookshelf', 'Bookshelf', 0, 1]]],
      ['greenhouse', 'Greenhouse', 'v_wheat', 'open', 'A place for herbs, under a glass roof.', [['v_wheat', 'Something growing', 0, 1]]],
      ['stable', 'Stable', 'b_hay', 'open', 'Your whistle reaches twice as far.', [['b_hay', 'Hay bale', 0, 1], ['v_oak_door', 'A gate', 0, 1]]],
      ['hall', 'Hall', 'v_bread', 'open', 'A meal here feeds the whole hall.', [['b_planks', 'A seat', 0, 1], ['b_crafting_table', 'A table', 0, 1]]],
      ['bell_tower', 'Bell tower', 'v_bell', 'open', 'You hear of a break two days sooner.', [['v_bell', 'A bell', 0, 1]]],
      ['lookout', 'Lookout', 'v_spyglass', 'open', 'The Atlas fills in round your home.', []],
      ['trophy', 'Trophy Hall', 'i_trophy', 'unknown', '', []],
      ['workshop', 'Workshop', 'i_precursor_scanner', 'unknown', '', []],
      ['ore_shed', 'Ore Shed', 'b_iron_ore', 'unknown', '', []],
      ['smeltery', 'Smeltery', 'b_furnace', 'unknown', '', []],
      ['rookery', 'Rookery', 'b_perch', 'unknown', '', []],
      ['foundry', 'Foundry', 'v_bell', 'unknown', '', []],
      ['storehouse', 'Storehouse', 'b_chest', 'unknown', '', []]],
    kin: [['e_mossback', 'dark', 'Something has been watching your fire.']] },

  powers: { keys: [['Z', 'haymaker'], ['X', ''], ['C', ''], ['V', ''], ['R', ''], ['Alt', 'dash']],
    own: [['haymaker', 2, 'Kinetic', 'Haymaker', 18, '5 s', 'Hold to wind up, let go to punch.', 0.34],
      ['dash', 1, 'Velocity', 'Dash', 10, '3 s', 'A burst the way you’re moving, even in the air.', 0.1]],
    body: [['wall_run', 'Wall run'], ['leap', 'Roll'], ['grapple', 'Rope'], ['ramp', 'Slide']],
    energy: [120, 120, '6.0'] },

  allPowers: ["afterimage", "aura", "barrier", "beam", "brace", "bulwark", "charged_shot", "citadel", "cloak", "collapse", "cone", "cord_lash", "danger_sense", "dash", "decoy", "dominate", "drone", "drone_swarm", "echo_swap", "ember_step", "flight", "floor", "flow", "foresight", "frost_nova", "ghost_step", "grapple", "gravity_flip", "gravity_well", "ground_slam", "haymaker", "hover", "hundred_palms", "jet_burst", "leap", "legion", "lightning_step", "mend", "meteor_dive", "nanite_skin", "nova", "overload", "prism_lance", "railgun", "ramp", "rampart", "read_intent", "shadow_step", "shockwave", "spire", "spirit_palm", "storm_call", "telekinesis", "terrain_rip", "third_eye", "thunderclap", "turret", "twin_strike", "void_maw", "waking_dream", "wall", "wall_run", "water_run"],

  shelves: [
    ['creatures', 'Creatures', 'e_briar_sow', 3, 44, ['e_briar_sow', 'e_brook_otter', 'e_crag_ram'], ['e_frost_stag', 'e_dune_ray', 'e_bog_toad', 'e_canopy_prowler', 'e_amberfly', 'e_chime_cricket', 'e_ember_imp', 'e_moss_shambler', 'e_dune_wyrm', 'e_bogwick', 'e_bell_wisp', 'e_dart_frog', 'e_drift_jelly', 'e_cog_hound', 'e_frost_stag', 'e_dune_ray', 'e_bog_toad']],
    ['beasts', 'Beasts', 'e_mossback', 0, 16, [], ['e_mossback', 'e_brook_otter', 'e_amberfly', 'e_crag_ram', 'e_chime_cricket', 'e_dart_frog', 'e_bog_toad', 'e_drift_jelly']],
    ['gates', 'Gate worlds', 'i_core_shard', 1, 90, ['g_bell_crypt'], ['g_ember_mines', 'g_frost_monastery', 'g_fungal_hollow', 'g_glass_desert', 'g_drowned_archive']],
    ['bells', 'Bells', 'v_bell', 1, 100, [], []],
    ['ores', 'Ores', 'b_iron_ore', 3, 12, ['b_coal_ore', 'b_copper_ore', 'b_iron_ore'], ['b_amethyst', 'b_cobble', 'b_diorite', 'b_amethyst', 'b_cobble', 'b_diorite', 'b_amethyst', 'b_cobble', 'b_diorite']],
    ['story', 'Your story', 'v_book', 36, 0, [], []]],
  finds: {
    e_briar_sow: ['Briar Sow', 'Creature · the Lumen woods', 'A thorn-backed sow. It charges when you come between it and its young.', [['First seen', 'Day 2'], ['Slain', '4']], ['i_briar_tusk', 'Briar Tusk']],
    e_brook_otter: ['Brook Otter', 'Creature · rivers', 'It keeps to the water and leaves you alone.', [['First seen', 'Day 1'], ['Slain', '0']], null],
    e_crag_ram: ['Crag Ram', 'Creature · high rock', 'It holds its ground on a ledge and butts you off it.', [['First seen', 'Day 3'], ['Slain', '1']], ['i_crag_horn', 'Crag Horn']],
    g_bell_crypt: ['The Bell Crypt', 'Gate world · E-rank', 'Where you first woke. Its bells are still down there.', [['Entered', '1'], ['Cleared', '0']], null],
    b_coal_ore: ['Coal', 'Ore · near the surface', 'It burns in your Forge.', [['Mined', '17']], null],
    b_copper_ore: ['Copper', 'Ore · caves and riverbanks', 'Your Fitting Stand and War Anvil both take it.', [['Mined', '9']], null],
    b_iron_ore: ['Iron', 'Ore · caves', 'D-rank gear is bound in it.', [['Mined', '4']], null] },
  story: [['i_handbell', 'Bell #07 · The Ember Bell', '1 min ago'], ['b_bed', 'Bedroom recognised', '1 min ago'], ['v_campfire', 'First shelter', '1 min ago'],
    ['i_hearth_ember', 'You set your fire down at Shellburn', '6 min ago'], ['p_haymaker', 'New power · Haymaker', 'Day 1'], ['i_holo_band', 'The band went on your wrist', 'Day 1']],
};
