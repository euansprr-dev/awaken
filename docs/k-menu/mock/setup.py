#!/usr/bin/env python3
"""Fills docs/k-menu/mock/{font,icons,bg} from local sources so the mockups can be re-rendered.

Nothing it writes is committed (see .gitignore): Minecraft's font and its char tables are Mojang's, read from the
gitignored reference folder; the backgrounds are tour frames from local runs.
Run from anywhere: python3 docs/k-menu/mock/setup.py
"""
import json, os, shutil
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, '..', '..', '..'))
REF = '/Volumes/MEDIA/Awaken/.ref/assets/assets/minecraft'
HOLO = '/Volumes/MEDIA/Awaken-holo/run-autopilot/holo-final'
ATLAS = '/Volumes/MEDIA/Awaken/.claude/worktrees/m5-atlas-0d7ff7/run-autopilot/screenshots'
NIGHT = os.path.expanduser('~/Library/Application Support/Claude/side-session-notes/'
                           'local_460b18dc-28ec-4e59-8e59-ed73535cf750/wild-life-shots/fireflies_night.jpg')

for d in ('font', 'icons', 'bg'):
    os.makedirs(os.path.join(HERE, d), exist_ok=True)

# the font: ascii and nonlatin_european atlases, and each glyph's rect and advance as Minecraft computes it
d = json.load(open(f'{REF}/font/include/default.json'))
glyphs = {}
for p in d['providers']:
    name = p['file'].split('/')[-1].replace('.png', '')
    if name not in ('ascii', 'nonlatin_european'):
        continue
    shutil.copy(f'{REF}/textures/font/{name}.png', os.path.join(HERE, 'font', name + '.png'))
    im = Image.open(f'{REF}/textures/font/{name}.png').convert('RGBA')
    rows, cols = len(p['chars']), len(p['chars'][0])
    cw, ch = im.width // cols, im.height // rows
    scale = p.get('height', 8) / ch
    for r, line in enumerate(p['chars']):
        for c, g in enumerate(line):
            if g in ('\x00', ' ') or g in glyphs:
                continue
            w = 0
            for x in range(cw - 1, -1, -1):
                if any(im.getpixel((c * cw + x, r * ch + y))[3] > 0 for y in range(ch)):
                    w = x + 1
                    break
            if w:
                glyphs[g] = {'f': name, 'sx': c * cw, 'sy': r * ch, 'w': w, 'h': ch, 'adv': int(0.5 + w * scale) + 1, 'asc': p['ascent']}
json.dump(glyphs, open(os.path.join(HERE, 'font', 'glyphs.json'), 'w'), ensure_ascii=False)

# AWAKEN's own power icons
src = os.path.join(REPO, 'src/main/resources/assets/awaken/textures/gui/sprites/power')
for f in os.listdir(src):
    shutil.copy(os.path.join(src, f), os.path.join(HERE, 'icons', f))

# backgrounds: the band raised before its panel appears, the real panel (calibration), the Atlas
bg = os.path.join(HERE, 'bg')
shutil.copy(f'{HOLO}/holo_11_k_120_band.png', f'{bg}/arm_grass.png')
shutil.copy(f'{HOLO}/holo_17_k_status.png', f'{bg}/real_status.png')
shutil.copy(f'{ATLAS}/m5_drift_a.png', f'{bg}/atlas_drift.png')
shutil.copy(f'{ATLAS}/m5_after_orbit.png', f'{bg}/atlas_city.png')
Image.open(f'{bg}/atlas_drift.png').convert('RGB').crop((1050, 300, 2450, 1117)).resize((480, 280), Image.LANCZOS).save(f'{bg}/glance.png')
W, H = 1740, 1440
Image.open(f'{bg}/atlas_drift.png').convert('RGB').crop((1950, 950, 2870, 1710)).resize((W, H), Image.LANCZOS).save(f'{bg}/t_cloud.png')
Image.open(f'{bg}/arm_grass.png').convert('RGB').crop((1150, 420, 1150 + W, 420 + H)).save(f'{bg}/t_grass.png')
Image.open(NIGHT).convert('RGB').resize((2880, 1800), Image.LANCZOS).crop((300, 200, 300 + W, 200 + H)).save(f'{bg}/t_night.png')
print('ready:', len(glyphs), 'glyphs')
