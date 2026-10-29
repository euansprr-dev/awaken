#!/usr/bin/env python3
"""The Living Deep's data pack (SPEC §42): writes src/main/resources/datapacks/awakened_caves from the tables below.

The region rules mirror dev.awaken.caves.CaveRegion (band, priority, field slot, climate box); the GameTest
caves_region_shares holds the injectors written here to that class. Run from the repo root:
    python3 tools/content/caves_content.py
"""
import json
import os
import sys

ROOT = os.path.join(os.path.dirname(__file__), '..', '..')
PACK = os.path.join(ROOT, 'src/main/resources/datapacks/awakened_caves')
REF = '/Volumes/MEDIA/Awaken/.ref/assets/data/minecraft'

# CaveRegion.Fields: field values at quantiles 0, 0.1 .. 1 per band, and each band's cover
QUANTILES = {
    'shallows': [-9, -0.360, -0.240, -0.151, -0.078, -0.007, 0.064, 0.141, 0.230, 0.349, 9],
    'middle': [-9, -0.354, -0.238, -0.149, -0.070, 0.003, 0.078, 0.156, 0.250, 0.371, 9],
    'deep': [-9, -0.374, -0.256, -0.168, -0.087, -0.018, 0.058, 0.137, 0.231, 0.364, 9],
}
COVER = {'shallows': 0.66, 'middle': 0.66, 'deep': 0.5}
MIN_DEPTH, SHALLOWS_TO, MAX_Y = 0.16, 0.34, 59


def box(humidity=None, temperature=None, continentalness=None, erosion=None):
    """A climate box (CaveRegion.Box) as Lithostitched parameters: each axis (lo, hi), None for open."""
    out = {}
    for k, v in (('humidity', humidity), ('temperature', temperature), ('continentalness', continentalness), ('erosion', erosion)):
        if v is None: continue
        r = {}
        if v[0] is not None: r['min_inclusive'] = v[0]
        if v[1] is not None: r['max_inclusive'] = v[1]
        out[k] = r
    return out


# CaveRegion.Box
ROOTED = box(humidity=(-0.1, None), temperature=(-0.45, 0.55), continentalness=(-0.11, None))
TEMPERATE = box(temperature=(-0.45, 0.2), continentalness=(-0.19, None))
WARM_WET = box(humidity=(-0.1, None), temperature=(0.2, 0.55), continentalness=(-0.19, None))

# id: band, priority, slot (quantiles of the cover), land (any of these boxes), archetype, water colour, music
REGIONS = {
    'rootways': dict(band='shallows', priority=-10, slot=(0, 1), boxes=[ROOTED],
                     archetype='lush_caves', water='#3b6f62', music='minecraft:music.overworld.lush_caves'),
    'ochre_deep': dict(band='middle', priority=-10, slot=(0, 1), boxes=[TEMPERATE, WARM_WET],
                       archetype='dripstone_caves', water='#a8622c', music='minecraft:music.overworld.dripstone_caves'),
}
# vanilla tags that list its cave biomes, so a structure starting underground (mineshafts, trial chambers, ruined
# portals) or a stronghold ring still finds a region biome good
STRUCTURE_TAGS = ['has_structure/mineshaft', 'has_structure/trial_chambers', 'has_structure/ruined_portal_standard',
                  'stronghold_biased_to']

CAVE_BIOMES = {'minecraft:deep_dark', 'minecraft:dripstone_caves', 'minecraft:lush_caves', 'minecraft:sulfur_caves'}


def value(band, q):
    table = QUANTILES[band]
    t = min(max(q * COVER[band], 0), 1) * 10
    i = min(9, int(t))
    return round(table[i] + (table[i + 1] - table[i]) * (t - i), 4)


def write(rel, data):
    path = os.path.join(PACK, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        json.dump(data, f, indent=2)
        f.write('\n')


def expand(tag):
    d = json.load(open(f'{REF}/tags/worldgen/biome/{tag}.json'))
    out = []
    for v in d['values']:
        v = v if isinstance(v, str) else v['id']
        out += expand(v[1:].split(':')[1]) if v.startswith('#') else [v]
    return out


def biome(rid, r):
    """A cave biome from vanilla's dripstone caves: the same ores, lakes, rooms, springs and carvers, none of the
    dripstone or the surface plants (a cave biome never reaches the surface), the region's water and music."""
    d = json.load(open(f'{REF}/worldgen/biome/dripstone_caves.json'))
    drop = {'minecraft:large_dripstone', 'minecraft:dripstone_cluster', 'minecraft:pointed_dripstone',
            'minecraft:patch_tall_grass_2', 'minecraft:trees_plains', 'minecraft:flower_plains', 'minecraft:patch_grass_plain',
            'minecraft:brown_mushroom_normal', 'minecraft:red_mushroom_normal', 'minecraft:patch_pumpkin'}
    d['features'] = [[f for f in step if f not in drop] for step in d['features']]
    d['effects'] = {'water_color': r['water']}
    d['attributes']['minecraft:audio/background_music'] = {'default': {'max_delay': 24000, 'min_delay': 12000, 'sound': r['music']}}
    return d


def main():
    write('pack.mcmeta', {'features': {'enabled': ['awaken:awakened_caves']},
                          'pack': {'description': 'Awakened Caves: the Living Deep, cave regions with their own shapes, rock and life (new worlds only)',
                                   'max_format': 121, 'min_format': 121}})
    # the bands' region fields (about 500 blocks) and the shapes' noises
    for band in ('shallows', 'middle', 'deep'):
        write(f'data/awaken/worldgen/noise/caves/{band}.json',
              {'amplitude_modifiers': [1.0, 0.5], 'base_amplitude': 1.0, 'base_octave': -9, 'octave_count': 2})
        write(f'data/awaken/worldgen/density_function/caves/{band}.json',
              {'type': 'minecraft:cache', 'input': {'type': 'minecraft:noise', 'noise': f'awaken:caves/{band}',
                                                    'shift_x': 'minecraft:shift_x', 'shift_z': 'minecraft:shift_z',
                                                    'xz_scale': 1.0, 'y_scale': 0.0}})
    for n, octave in (('shape_a', -7), ('shape_b', -7), ('shape_flat', -8)):
        write(f'data/awaken/worldgen/noise/caves/{n}.json',
              {'amplitude_modifiers': [1.0, 0.4], 'base_amplitude': 1.0, 'base_octave': octave, 'octave_count': 2})
    write('data/awaken/worldgen/density_function/caves/y.json',
          {'type': 'minecraft:gradient', 'axis': 'y', 'from_coordinate': -64, 'to_coordinate': 320, 'from_value': -64.0, 'to_value': 320.0})

    # the hosts: every vanilla overworld biome but the vanilla cave biomes (the deep dark keeps its ancient cities)
    hosts = sorted(set(expand('is_overworld')) - CAVE_BIOMES)
    write('data/awaken/tags/worldgen/biome/caves/hosts.json', {'replace': False, 'values': hosts})
    write('data/awaken/tags/worldgen/biome/caves/regions.json', {'replace': False, 'values': [f'awaken:{r}' for r in REGIONS]})
    # in the overworld (lodes, Deepglass, lamps, ore thinning and the Hollows reach them), not #c:is_underground
    write('data/minecraft/tags/worldgen/biome/is_overworld.json', {'replace': False, 'values': [f'awaken:{r}' for r in REGIONS]})
    for tag in STRUCTURE_TAGS:
        write(f'data/minecraft/tags/worldgen/biome/{tag}.json', {'replace': False, 'values': [f'awaken:{r}' for r in REGIONS]})
    by_arch = {}
    for rid, r in REGIONS.items():
        by_arch.setdefault(r['archetype'], []).append(f'awaken:{rid}')
    for arch, ids in by_arch.items():
        write(f'data/awaken/tags/worldgen/biome/archetype/{arch}.json', {'replace': False, 'values': ids})

    old = os.path.join(PACK, 'data/awaken/lithostitched/biome_injector/caves')
    for f in os.listdir(old) if os.path.isdir(old) else []:
        os.remove(os.path.join(old, f))
    for rid, r in REGIONS.items():
        band = r['band']
        params = {}
        if band == 'deep':
            params['awaken:caves/y'] = {'max_inclusive': -0.5}
            params['depth'] = {'min_inclusive': MIN_DEPTH}
        else:
            params['awaken:caves/y'] = {'min_inclusive': 0.0, 'max_inclusive': float(MAX_Y)}
            # the Shallows end just short of SHALLOWS_TO: the Middle starts there (CaveRegion.Band.at)
            params['depth'] = {'min_inclusive': MIN_DEPTH, 'max_inclusive': round(SHALLOWS_TO - 0.0001, 4)} if band == 'shallows' else {'min_inclusive': SHALLOWS_TO}
        lo, hi = r['slot']
        field = {}
        if lo > 0: field['min_inclusive'] = value(band, lo)
        field['max_inclusive'] = value(band, hi)
        params[f'awaken:caves/{band}'] = field
        # one injector per box of land (Lithostitched tries them in turn; the first that holds wins)
        for i, b in enumerate(r['boxes']):
            name = rid if len(r['boxes']) == 1 else f'{rid}_{i + 1}'
            write(f'data/awaken/lithostitched/biome_injector/caves/{name}.json',
                  {'type': 'lithostitched:replace_partially', 'dimension': 'minecraft:overworld', 'priority': r['priority'],
                   'targets': '#awaken:caves/hosts', 'replacement': f'awaken:{rid}', 'parameters': {**params, **b}})
        write(f'data/awaken/worldgen/biome/{rid}.json', biome(rid, r))

    # the shapes, in Tectonic's caves (inside its depth seal and interpolation) or, without Tectonic, vanilla's entrances
    shape = {'type': 'awaken:cave_shape', 'input': {'type': 'lithostitched:wrapped_marker'},
             'depth': 'minecraft:overworld/depth', 'shallows': 'awaken:caves/shallows', 'middle': 'awaken:caves/middle',
             'deep': 'awaken:caves/deep', 'humidity': 'minecraft:overworld/vegetation', 'temperature': 'minecraft:overworld/temperature',
             'continents': 'minecraft:overworld/continents', 'erosion': 'minecraft:overworld/erosion',
             'noise_a': 'awaken:caves/shape_a', 'noise_b': 'awaken:caves/shape_b', 'flat': 'awaken:caves/shape_flat'}
    tectonic = {'type': 'neoforge:mod_loaded', 'modid': 'tectonic'}
    write('data/awaken/lithostitched/worldgen_modifier/caves/shape_tectonic.json',
          {'neoforge:conditions': [tectonic], 'type': 'lithostitched:wrap_density_function',
           'target_function': 'tectonic:caves', 'wrapper_function': shape})
    write('data/awaken/lithostitched/worldgen_modifier/caves/shape_vanilla.json',
          {'neoforge:conditions': [{'type': 'neoforge:not', 'value': tectonic}], 'type': 'lithostitched:wrap_density_function',
           'target_function': 'minecraft:overworld/caves/entrances', 'wrapper_function': shape})
    print('wrote', PACK)


if __name__ == '__main__':
    sys.exit(main())
