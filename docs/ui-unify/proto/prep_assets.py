#!/usr/bin/env python3
"""Fills docs/ui-unify/proto/assets/ so the prototype and the mock frames can be rendered.

Nothing it writes is committed (see .gitignore): AWAKEN's own textures are copied from the repo, a few of Minecraft's
item textures and block faces from the gitignored reference folder, and renders are cropped from real CI tour shots.
Run: /Volumes/MEDIA/ui-unify-work/venv/bin/python docs/ui-unify/proto/prep_assets.py
"""
import os, shutil, sys
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, '..', '..', '..'))
OUT = os.path.join(HERE, 'assets')
AW = os.path.join(REPO, 'src/main/resources/assets/awaken/textures')
MC = '/Volumes/MEDIA/Awaken/.ref/assets/assets/minecraft/textures'
WT = '/Volumes/MEDIA/Awaken/.claude/worktrees'
HALL = f'{WT}/armour-forge-plan-c98ed9/run-autopilot/screenshots-ci'
OWN = os.path.join(REPO, 'run-autopilot/screenshots-ci')
os.makedirs(OUT, exist_ok=True)


def put(src, name):
    if not os.path.exists(src):
        print('missing', src)
        return
    im = Image.open(src)
    if im.height > im.width:  # an animated strip: its first frame
        im.crop((0, 0, im.width, im.width)).save(os.path.join(OUT, name))
    else:
        shutil.copy(src, os.path.join(OUT, name))


# ---- AWAKEN's own item art -------------------------------------------------------------------------------------------
for n in ('essence_shard lord_heart hearth_ember holo_band briar_tusk antler_tine core_shard deepglass_shard smith_hammer handbell '
          'bell_frame bell_clapper dream_journal hunters_whistle power_core mire_hide trophy village_bell bell_bronze_ingot '
          'wildhide_helmet wildhide_chestplate wildhide_leggings wildhide_boots wildhide_blade precursor_scanner raw_starmetal '
          'crag_horn dusk_feather moon_scale hollow_crystal sky_pearl keepsake lode_map hand_lantern miners_lamp runner_rope '
          'binoculars mallet enchantment_page ledger_book').split():
    put(f'{AW}/item/{n}.png', f'i_{n}.png')
for p_ in ('helmet', 'chestplate', 'leggings', 'boots'):
    put(f'{AW}/item/gatewrought_{p_}.png', f'i_gatewrought_{p_}.png')
for s in ('bellguard', 'rootwarden', 'amberwing'):
    for p in ('helmet', 'chestplate', 'leggings', 'boots'):
        put(f'{AW}/item/armour/{s}_{p}.png', f'i_{s}_{p}.png')
for n in ('longsword katana axe daggers maul spear scythe glaive greatblade shieldblade').split():
    put(f'{AW}/item/arms/beastbound_{n}_icon.png', f'w_{n}.png')
put(f'{AW}/item/arms/beastbound_longsword.png', 'w_longsword_big.png')
put(f'{AW}/item/arms/gatewrought_longsword_icon.png', 'w_gate_longsword.png')
for f in sorted(os.listdir(f'{AW}/gui/sprites/power')):
    if f.endswith('.png'):
        put(f'{AW}/gui/sprites/power/{f}', 'p_' + f)
for n in ('mossback briar_sow crag_ram frost_stag brook_otter dune_ray bog_toad canopy_prowler amberfly chime_cricket '
          'ember_imp moss_shambler dune_wyrm bogwick bell_wisp dart_frog drift_jelly cog_hound').split():
    put(f'{AW}/item/{n}_spawn_egg.png', f'e_{n}.png')
for n in ('bell_crypt ember_mines frost_monastery fungal_hollow glass_desert drowned_archive overgrown_temple sky_archipelago '
          'clockwork_foundry mirror_palace').split():
    p = f'{AW}/gui/crossing/{n}.png'
    if os.path.exists(p):
        im = Image.open(p).convert('RGB')
        im = im.crop((0, 0, im.width, min(im.height, im.width * 9 // 16))).resize((384, 216), Image.LANCZOS)
        im.save(os.path.join(OUT, f'g_{n}.jpg'), quality=82)

# ---- a few of Minecraft's own (item sprites as they show in any inventory) --------------------------------------------
for n in ('iron_ingot copper_ingot coal diamond amethyst_shard leather string stick bone feather campfire lantern bell oak_door '
          'armor_stand book map bread compass_00 compass_08 compass_16 compass_24 clock_00 arrow torch iron_pickaxe bow '
          'diamond_sword ender_pearl redstone raw_iron wheat spyglass').split():
    put(f'{MC}/item/{n}.png', f'v_{n}.png')


# ---- little isometric blocks, the way the inventory draws a block ------------------------------------------------------
S = 64


def shade(im, k):
    r, g, b, a = im.split()
    f = lambda c: c.point(lambda v: int(v * k))
    return Image.merge('RGBA', (f(r), f(g), f(b), a))


def tex(path):
    im = Image.open(path).convert('RGBA')
    return im.crop((0, 0, 16, 16))


def cube(top, left, right, name):
    out = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    faces = (
        # (texture, shade, affine out->src, polygon)
        (top, 1.0, (16 / S, 32 / S, -8, -16 / S, 32 / S, 8), [(S / 2, 0), (S, S / 4), (S / 2, S / 2), (0, S / 4)]),
        (left, 0.80, (32 / S, 0, 0, -16 / S, 32 / S, -8), [(0, S / 4), (S / 2, S / 2), (S / 2, S), (0, 3 * S / 4)]),
        (right, 0.60, (32 / S, 0, -16, 16 / S, 32 / S, -24), [(S / 2, S / 2), (S, S / 4), (S, 3 * S / 4), (S / 2, S)]),
    )
    for t, k, aff, poly in faces:
        layer = shade(t, k).transform((S, S), Image.AFFINE, aff, resample=Image.NEAREST)
        mask = Image.new('L', (S, S), 0)
        ImageDraw.Draw(mask).polygon(poly, fill=255)
        out.paste(layer, (0, 0), mask)
    out.save(os.path.join(OUT, name))


B = f'{MC}/block'
cube(tex(f'{B}/crafting_table_top.png'), tex(f'{B}/crafting_table_front.png'), tex(f'{B}/crafting_table_side.png'), 'b_crafting_table.png')
cube(tex(f'{B}/oak_planks.png'), tex(f'{B}/oak_planks.png'), tex(f'{B}/oak_planks.png'), 'b_planks.png')
cube(tex(f'{B}/white_wool.png'), tex(f'{B}/white_wool.png'), tex(f'{B}/white_wool.png'), 'b_wool.png')
cube(tex(f'{B}/furnace_top.png'), tex(f'{B}/furnace_front.png'), tex(f'{B}/furnace_side.png'), 'b_furnace.png')
cube(tex(f'{B}/barrel_top.png'), tex(f'{B}/barrel_side.png'), tex(f'{B}/barrel_side.png'), 'b_barrel.png')
cube(tex(f'{B}/oak_planks.png'), tex(f'{B}/bookshelf.png'), tex(f'{B}/bookshelf.png'), 'b_bookshelf.png')
cube(tex(f'{B}/hay_block_top.png'), tex(f'{B}/hay_block_side.png'), tex(f'{B}/hay_block_side.png'), 'b_hay.png')
cube(tex(f'{B}/smithing_table_top.png'), tex(f'{B}/smithing_table_front.png'), tex(f'{B}/smithing_table_side.png'), 'b_smithing.png')
cube(tex(f'{B}/lectern_top.png'), tex(f'{B}/lectern_front.png'), tex(f'{B}/lectern_sides.png'), 'b_lectern.png')
cube(tex(f'{B}/cobblestone.png'), tex(f'{B}/cobblestone.png'), tex(f'{B}/cobblestone.png'), 'b_cobble.png')
cube(tex(f'{B}/diorite.png'), tex(f'{B}/diorite.png'), tex(f'{B}/diorite.png'), 'b_diorite.png')
cube(tex(f'{B}/oak_log_top.png'), tex(f'{B}/oak_log.png'), tex(f'{B}/oak_log.png'), 'b_log.png')
cube(tex(f'{B}/iron_ore.png'), tex(f'{B}/iron_ore.png'), tex(f'{B}/iron_ore.png'), 'b_iron_ore.png')
cube(tex(f'{B}/copper_ore.png'), tex(f'{B}/copper_ore.png'), tex(f'{B}/copper_ore.png'), 'b_copper_ore.png')
cube(tex(f'{B}/coal_ore.png'), tex(f'{B}/coal_ore.png'), tex(f'{B}/coal_ore.png'), 'b_coal_ore.png')
cube(tex(f'{B}/amethyst_block.png'), tex(f'{B}/amethyst_block.png'), tex(f'{B}/amethyst_block.png'), 'b_amethyst.png')
if os.path.exists(f'{AW}/block/forge_top.png'):
    cube(tex(f'{AW}/block/forge_top.png'), tex(f'{AW}/block/frame_forge_front.png'), tex(f'{AW}/block/frame_forge_front.png'), 'b_forge.png')


def proj(x, y, z):
    return (S / 2 + (x - z) * S / 32, (x + z) * S / 64 + (16 - y) * S / 32)


def box(d, x0, y0, z0, w, h, dp, col):
    """A solid box in block sixteenths, drawn top, left (+z) and right (+x) faces."""
    x1, y1, z1 = x0 + w, y0 + h, z0 + dp
    r, g, b = col
    d.polygon([proj(x0, y1, z0), proj(x1, y1, z0), proj(x1, y1, z1), proj(x0, y1, z1)], fill=(r, g, b, 255))
    d.polygon([proj(x0, y1, z1), proj(x1, y1, z1), proj(x1, y0, z1), proj(x0, y0, z1)], fill=(int(r * .8), int(g * .8), int(b * .8), 255))
    d.polygon([proj(x1, y1, z0), proj(x1, y1, z1), proj(x1, y0, z1), proj(x1, y0, z0)], fill=(int(r * .6), int(g * .6), int(b * .6), 255))


def shape(name, boxes):
    im = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    for b in boxes:
        box(d, *b)
    im.save(os.path.join(OUT, name))


shape('b_bed.png', [(0, 0, 2, 16, 3, 12, (120, 86, 52)), (0, 3, 2, 16, 4, 12, (176, 46, 38)), (0, 6, 2, 5, 2, 12, (236, 236, 236))])
shape('b_anvil.png', [(3, 0, 2, 10, 4, 12, (58, 58, 62)), (5, 4, 4, 6, 5, 8, (70, 70, 74)), (2, 9, 0, 12, 6, 16, (84, 84, 90))])
shape('b_chest.png', [(1, 0, 1, 14, 10, 14, (150, 104, 44)), (1, 10, 1, 14, 4, 14, (170, 120, 52)), (15, 7, 7, 1, 4, 2, (210, 210, 210))])
shape('b_war_anvil.png', [(2, 0, 2, 12, 5, 12, (72, 60, 56)), (5, 5, 4, 6, 4, 8, (92, 72, 60)), (1, 9, 0, 14, 6, 16, (112, 84, 62)), (6, 15, 6, 4, 1, 4, (255, 170, 60))])
shape('b_perch.png', [(7, 0, 7, 2, 12, 2, (110, 80, 50)), (2, 12, 7, 12, 2, 2, (130, 96, 60))])

# ---- renders cropped from real Hall of Arms tour shots (3416 x 1858) ----------------------------------------------------
def knock_black(im, thresh=26):
    """Near-black background to transparent, feathered, so a render sits on any glass."""
    im = im.convert('RGBA')
    px = im.load()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            m = max(r, g, b)
            if m < thresh:
                px[x, y] = (r, g, b, 0)
            elif m < thresh * 2:
                px[x, y] = (r, g, b, int(255 * (m - thresh) / thresh))
    return im


def cut_out(im, thresh=30):
    """The dark ground round a render to transparent: only what connects to the picture's edge, so dark armour stays."""
    im = im.convert('RGBA')
    w, h = im.size
    px = im.load()
    dark = lambda x, y: max(px[x, y][:3]) < thresh
    seen = bytearray(w * h)
    stack = [(x, y) for x in range(w) for y in (0, h - 1)] + [(x, y) for y in range(h) for x in (0, w - 1)]
    while stack:
        x, y = stack.pop()
        if x < 0 or y < 0 or x >= w or y >= h or seen[y * w + x] or not dark(x, y):
            continue
        seen[y * w + x] = 1
        stack += [(x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)]
    for y in range(h):
        for x in range(w):
            if seen[y * w + x]:
                px[x, y] = (0, 0, 0, 0)
    return im


lineup = f'{HALL}/hall-lineup-42615804522/lineup.png'
if os.path.exists(lineup):
    im = Image.open(lineup).convert('RGB')
    for name, (x0, x1) in {'wildhide': (300, 940), 'bellguard': (990, 1700), 'rootwarden': (1750, 2400), 'amberwing': (2440, 3120)}.items():
        c = im.crop((x0, 300, x1, 1440))
        c = cut_out(c.resize((c.width // 2, c.height // 2), Image.LANCZOS))
        c.save(os.path.join(OUT, f'suit_{name}.png'))
    im.resize((1708, 929), Image.LANCZOS).save(os.path.join(OUT, 'hall_lineup.jpg'), quality=84)
for src, dst in ((f'{HALL}/hall-suits-42629713841/terr_stand.png', 'hall_terraces.jpg'),
                 (f'{HALL}/hall-suits-42629713841/suit_full.png', 'hall_suit.jpg'),
                 (f'{HALL}/hall-wall-42607335582/wall_forge.png', 'hall_wall.jpg'),
                 (f'{HALL}/hall-wall-42607335582/piece_katana.png', 'hall_piece.jpg'),
                 (f'{HALL}/hall-anvil-42615804495/anvil_temper.png', 'hall_anvil.jpg')):
    if os.path.exists(src):
        Image.open(src).convert('RGB').resize((1708, 929), Image.LANCZOS).save(os.path.join(OUT, dst), quality=84)

base = f'{OWN}/ui-unify-baseline-42754069258'
if os.path.exists(f'{base}/u61_map.png'):
    Image.open(f'{base}/u61_map.png').convert('RGB').resize((1708, 929), Image.LANCZOS).save(os.path.join(OUT, 'map_scene.jpg'), quality=84)
moment = f'{OWN}/ui-unify-moment-42755419130'
for src, dst in ((f'{moment}/e00_band_raised.png', 'bg_world.jpg'), (f'{base}/u04_band_raised.png', 'bg_night.jpg')):
    if os.path.exists(src):
        Image.open(src).convert('RGB').resize((1708, 929), Image.LANCZOS).save(os.path.join(OUT, dst), quality=86)
print('assets:', len(os.listdir(OUT)))
