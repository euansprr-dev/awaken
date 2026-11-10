#!/usr/bin/env python3
"""The air of every rolled dungeon kind (SPEC 50.9, "Air"): its own biome (fog, what drifts in it, Minecraft's own
ambient loop and music, the colour of its light), its own Lens look, and the small life its blueprint asks the client
for. One table; run from the repo root: python3 tools/realms/kinds_air.py

A kind's biome starts as a copy of its theme's gate biome, so nothing spawns there and water keeps the theme's colour.
Its Lens look starts as the theme's and is pushed: more haze, a mist, a little more glow. Sounds are Minecraft's own
recordings by name (never ours to copy, never synthesised).

The air list is in the order the client takes its turns, and it stops at 260 sprites: what must never be starved
(the dust in the shafts of light) comes first."""
import json, os

ROOT = os.path.join(os.path.dirname(__file__), '..', '..', 'src', 'main', 'resources')
BIOMES = os.path.join(ROOT, 'data', 'awaken', 'worldgen', 'biome')
LOOKS = os.path.join(ROOT, 'assets', 'awaken', 'lens', 'looks_gates.json')
REALMS = os.path.join(ROOT, 'data', 'awaken', 'realms')
DIM = os.path.join(ROOT, 'data', 'awaken', 'dimension', 'realms.json')

def k(theme, name, fog, sky, near, far, drift, loop, mood, plus, music, light, dark, haze, mist, air, warmth=None, saturation=None, exposure=None):
    return dict(theme=theme, name=name, fog=fog, sky=sky, near=near, far=far, drift=drift, loop=loop, mood=mood, plus=plus, music=music, light=light, dark=dark,
                haze=haze, mist=mist, air=air, warmth=warmth, saturation=saturation, exposure=exposure)

CAVE = 'minecraft:ambient.cave'
KINDS = {
    'catacombs': k('bell_crypt', 'The Bone Roads: cold blue-grey stone going off into a haze, candle pools, dust in the air, a wind somewhere below', '#1c2636', '#0c1018', 5, 60,
                   [('white_ash', 0.004)], 'minecraft:ambient.soul_sand_valley.loop', CAVE, ('minecraft:ambient.soul_sand_valley.additions', 0.006), 'minecraft:music.nether.soul_sand_valley',
                   '#ffd9a0', '#1a1c26', 0.56, 0.4, ['shaft_dust', 'mist', 'dust', 'drips', 'bats', 'rats', 'far_toll']),
    'hollow_hills': k('bell_crypt', 'The Hollow Hills: brown earth dark, pale grave-light, motes of soil, the hill settling over you', '#2a2218', '#120e0a', 5, 54,
                      [('mycelium', 0.006), ('white_ash', 0.002)], 'minecraft:ambient.nether_wastes.loop', CAVE, ('minecraft:ambient.nether_wastes.additions', 0.004), 'minecraft:music.overworld.deep_dark',
                      '#cfe0c8', '#1c1a16', 0.56, 0.45, ['shaft_dust', 'mist', 'dust', 'drips', 'bats', 'wind']),
    'root_cellars': k('overgrown_temple', 'The Root Cellars: green-gold gloom under the temple, spores turning in the lamplight, water dripping through the roots', '#2a3a1c', '#10180c', 6, 64,
                      [('spore_blossom_air', 0.012), ('firefly', 0.004)], 'minecraft:ambient.warped_forest.loop', CAVE, ('minecraft:block.pointed_dripstone.drip_water', 0.012), 'minecraft:music.overworld.lush_caves',
                      '#ffe0a0', '#1a2016', 0.52, 0.5, ['shaft_dust', 'mist', 'spores', 'drips', 'moths', 'fireflies', 'petals']),
    'tallow_runs': k('hive', 'The Tallow Runs: warm amber dark, soot hanging in it, a hundred candles guttering', '#3a2a14', '#140e08', 5, 58,
                     [('white_ash', 0.004)], 'minecraft:ambient.nether_wastes.loop', CAVE, ('minecraft:block.candle.ambient', 0.012), 'minecraft:music.overworld.dripstone_caves',
                     '#ffcf8a', '#221a12', 0.54, 0.4, ['shaft_dust', 'mist', 'smoke', 'embers', 'dust', 'drips', 'moths']),
    'cinder_workings': k('ember_mines', 'The Cinder Workings: red dark, ash falling, heat haze over the seams, the mine still burning', '#3a1408', '#160806', 5, 56,
                         [('ash', 0.02), ('white_ash', 0.006)], 'minecraft:ambient.basalt_deltas.loop', 'minecraft:ambient.basalt_deltas.mood', ('minecraft:block.lava.pop', 0.01), 'minecraft:music.nether.basalt_deltas',
                         '#ff9a50', '#241410', 0.56, 0.4, ['shaft_dust', 'smoke', 'ash', 'embers', 'dust', 'steam']),
    'sunken_stacks': k('drowned_archive', 'The Sunken Stacks: deep teal, everything a little under water still, drips and slow motes', '#0c2a30', '#06141a', 5, 52,
                       [('spore_blossom_air', 0.008), ('glow', 0.002)], 'minecraft:ambient.underwater.loop', CAVE, ('minecraft:block.pointed_dripstone.drip_water', 0.02), 'minecraft:music.under_water',
                       '#bfeee0', '#10202a', 0.58, 0.55, ['shaft_dust', 'mist', 'paper', 'drips', 'dust', 'pale_fish']),
    'sporewells': k('fungal_hollow', 'The Sporewells: teal and violet, spores thick in the air, the fungus breathing', '#123038', '#081418', 6, 60,
                    [('warped_spore', 0.02), ('spore_blossom_air', 0.008)], 'minecraft:ambient.warped_forest.loop', 'minecraft:ambient.warped_forest.mood', ('minecraft:ambient.warped_forest.additions', 0.005),
                    'minecraft:music.nether.warped_forest', '#a8f0e0', '#141c24', 0.56, 0.55, ['shaft_dust', 'mist', 'spores', 'fireflies', 'drips', 'moths']),
    'frozen_cells': k('frost_monastery', 'The Frozen Cells: pale cold light, snow in the air indoors, a wind in the cloisters', '#a8c0d8', '#5a708a', 8, 84,
                      [('snowflake', 0.02), ('white_ash', 0.004)], 'minecraft:ambient.soul_sand_valley.loop', CAVE, ('minecraft:block.powder_snow.step', 0.004), 'minecraft:music.overworld.frozen_peaks',
                      '#dcecff', '#2a3440', 0.46, 0.5, ['shaft_dust', 'mist', 'snow', 'wind', 'dust']),
    'glass_warrens': k('glass_desert', 'The Glass Warrens: hot amber, dust in every beam, the glass still giving off its heat', '#5a3a14', '#20140a', 6, 66,
                       [('white_ash', 0.006)], 'minecraft:ambient.nether_wastes.loop', CAVE, None, 'minecraft:music.overworld.desert',
                       '#ffc878', '#2a1e12', 0.52, 0.3, ['shaft_dust', 'sand', 'dust', 'wind']),
    'stopped_works': k('clockwork_foundry', 'The Stopped Works: brass gloom, smoke that never cleared, metal ticking as it cools', '#2a2010', '#100c08', 6, 62,
                       [('ash', 0.006), ('white_smoke', 0.0015)], 'minecraft:ambient.nether_wastes.loop', CAVE, ('minecraft:block.chain.step', 0.004), 'minecraft:music.nether.nether_wastes',
                       '#ffc070', '#1e1810', 0.54, 0.4, ['shaft_dust', 'smoke', 'steam', 'embers', 'dust']),
    'silvered_halls': k('mirror_palace', 'The Silvered Halls: pale and still, light off every mirror, a glint in the air now and then, no sound but yours', '#c8ccd8', '#8088a0', 12, 110,
                        [('end_rod', 0.0015)], None, None, None, 'minecraft:music.overworld.cherry_grove',
                        '#fff4e0', '#40424c', 0.34, 0.2, ['shaft_dust', 'dust', 'stars']),
    'sealed_wing': k('precursor_lab', 'The Sealed Wing: clean cold light, a hum that has never stopped, a spark where something still runs', '#b8d4e0', '#7090a0', 12, 104,
                     [('electric_spark', 0.001), ('end_rod', 0.001)], 'minecraft:block.beacon.ambient', None, ('minecraft:block.conduit.ambient.short', 0.003), 'minecraft:music.end',
                     '#e0f4ff', '#38424a', 0.36, 0.22, ['shaft_dust', 'steam', 'dust']),
    'sleepless_ward': k('nightmare', 'The Sleepless Ward: violet dark between yellow lamps, dust that hangs, a door shutting somewhere', '#2a1c38', '#120c1a', 5, 56,
                        [('mycelium', 0.008)], 'minecraft:ambient.soul_sand_valley.loop', CAVE, ('minecraft:block.wooden_door.close', 0.002), 'minecraft:music.overworld.deep_dark',
                        '#ffe9a0', '#1e1a26', 0.56, 0.45, ['shaft_dust', 'mist', 'dust', 'moths', 'procession']),
    'unlit_stair': k('abyssal_shrine', 'The Unlit Stair: black that eats the far wall, a few pale lights, and no sound at all', '#06060c', '#020206', 3, 42,
                     [('ash', 0.004)], None, None, None, None,
                     '#d8d0ff', '#121018', 0.64, 0.55, ['shaft_dust', 'mist', 'stars', 'dust']),
    'rime_hollows': k('frost_monastery', 'The Rime Hollows: blue ice lit from inside, snow blowing through, the mountain creaking', '#8ab0d0', '#40607a', 7, 74,
                      [('snowflake', 0.03)], 'minecraft:ambient.soul_sand_valley.loop', CAVE, ('minecraft:block.powder_snow.fall', 0.004), 'minecraft:music.overworld.snowy_slopes',
                      '#cfe6ff', '#26323e', 0.48, 0.55, ['shaft_dust', 'mist', 'snow', 'wind', 'drips']),
    'green_dark': k('overgrown_temple', 'The Green Dark: wet green gloom, fireflies, spores falling through the vines, water everywhere you cannot see', '#142a10', '#081408', 5, 54,
                    [('spore_blossom_air', 0.02), ('firefly', 0.01)], None, CAVE, ('minecraft:block.pointed_dripstone.drip_water', 0.03), 'minecraft:music.overworld.lush_caves',
                    '#d8ffb0', '#162014', 0.56, 0.6, ['shaft_dust', 'mist', 'spores', 'fireflies', 'drips', 'moths', 'petals']),
}
# the brood kind keeps the biome the Broodwarren was given
KEEP = {'brood_burrow': ('awaken:realm/brood_warren', ['shaft_dust', 'mist', 'spores', 'dust', 'drips', 'moths'])}

def main():
    looks = json.load(open(LOOKS))
    dim = json.load(open(DIM))
    listed = dim['generator']['biome_source']['biomes']
    for kind, c in KINDS.items():
        b = json.load(open(os.path.join(BIOMES, 'gate_%s.json' % c['theme'])))
        a = b['attributes']
        a['minecraft:visual/fog_color'] = c['fog']
        a['minecraft:visual/sky_color'] = c['sky']
        a['minecraft:visual/fog_start_distance'] = float(c['near'])
        a['minecraft:visual/fog_end_distance'] = float(c['far'])
        a['minecraft:visual/ambient_particles'] = {'modifier': 'append', 'argument': [{'particle': {'type': 'minecraft:' + t}, 'probability': p} for t, p in c['drift']]}
        sounds = {}
        if c['loop']: sounds['loop'] = c['loop']
        if c['mood']: sounds['mood'] = {'sound': c['mood'], 'tick_delay': 6000, 'block_search_extent': 8, 'offset': 2.0}
        if c['plus']: sounds['additions'] = {'sound': c['plus'][0], 'tick_chance': c['plus'][1]}
        if sounds: a['minecraft:audio/ambient_sounds'] = sounds
        else: a.pop('minecraft:audio/ambient_sounds', None)
        if c['music']: a['minecraft:audio/background_music'] = {'default': {'sound': c['music'], 'min_delay': 4800, 'max_delay': 14000}}
        else: a.pop('minecraft:audio/background_music', None)
        a['minecraft:visual/block_light_tint'] = c['light']
        a['minecraft:visual/ambient_light_color'] = c['dark']
        os.makedirs(os.path.join(BIOMES, 'realm'), exist_ok=True)
        json.dump(b, open(os.path.join(BIOMES, 'realm', kind + '.json'), 'w'), indent=2)
        open(os.path.join(BIOMES, 'realm', kind + '.json'), 'a').write('\n')
        biome = 'awaken:realm/' + kind
        if biome not in listed: listed.append(biome)
        look = dict(looks['looks']['biome:awaken:gate_' + c['theme']])
        look['_name'] = c['name']
        look['haze'] = c['haze']
        look['mist'] = c['mist']
        look['fogGlow'] = max(look.get('fogGlow', 0.5), 0.6)
        look['bloom'] = round(min(1.1, look.get('bloom', 0.8) + 0.05), 2)
        look['hazeTint'] = [round(int(c['fog'][i:i + 2], 16) / 255 * 1.6 + 0.08, 2) for i in (1, 3, 5)]
        look['hazeTint'] = [min(1.0, v) for v in look['hazeTint']]
        for key in ('warmth', 'saturation', 'exposure'):
            if c[key] is not None: look[key] = c[key]
        looks['looks']['biome:' + biome] = look
        bp_path = os.path.join(REALMS, kind + '.json')
        bp = json.load(open(bp_path))
        bp['sky']['biomes'] = [biome]
        bp['sky']['air'] = c['air']
        json.dump(bp, open(bp_path, 'w'), indent=2)
        open(bp_path, 'a').write('\n')
    for kind, (biome, air) in KEEP.items():
        bp_path = os.path.join(REALMS, kind + '.json')
        bp = json.load(open(bp_path))
        bp['sky']['biomes'] = [biome]
        bp['sky']['air'] = air
        json.dump(bp, open(bp_path, 'w'), indent=2)
        open(bp_path, 'a').write('\n')
    json.dump(looks, open(LOOKS, 'w'), indent=2)
    open(LOOKS, 'a').write('\n')
    json.dump(dim, open(DIM, 'w'), indent=2)
    open(DIM, 'a').write('\n')
    print('wrote', len(KINDS), 'biomes, looks and blueprints')

if __name__ == '__main__':
    main()
