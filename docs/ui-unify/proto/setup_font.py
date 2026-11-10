#!/usr/bin/env python3
"""Builds a local TrueType copy of Minecraft's bitmap font so the mockups can be rendered with the game's real glyphs.

Local only: it reads Mojang's font atlas from the gitignored reference folder and writes outside the repo. The font file
is never committed and never published; the published prototype falls back to Pixelify Sans (Google Fonts, OFL).
Run: /Volumes/MEDIA/ui-unify-work/venv/bin/python docs/ui-unify/proto/setup_font.py
"""
import json, os
from PIL import Image
from fontTools.fontBuilder import FontBuilder
from fontTools.pens.ttGlyphPen import TTGlyphPen

REF = '/Volumes/MEDIA/Awaken/.ref/assets/assets/minecraft'
OUT = '/Volumes/MEDIA/ui-unify-work/font'
os.makedirs(OUT, exist_ok=True)
U = 100  # font units per pixel; 8 px to the em

d = json.load(open(f'{REF}/font/include/default.json'))
glyphs, cmap, adv = {}, {}, {}

def square(pen, x, y):
    pen.moveTo((x * U, y * U)); pen.lineTo((x * U, (y + 1) * U)); pen.lineTo(((x + 1) * U, (y + 1) * U)); pen.lineTo(((x + 1) * U, y * U)); pen.closePath()

pen = TTGlyphPen(None); glyphs['.notdef'] = pen.glyph(); adv['.notdef'] = 4 * U
pen = TTGlyphPen(None); glyphs['space'] = pen.glyph(); adv['space'] = 4 * U; cmap[32] = 'space'
for p in d['providers']:
    if p.get('type') != 'bitmap':
        continue
    name = p['file'].split('/')[-1]
    if name not in ('ascii.png', 'nonlatin_european.png', 'accented.png'):
        continue
    im = Image.open(f'{REF}/textures/font/{name}').convert('RGBA')
    rows, cols = len(p['chars']), len(p['chars'][0])
    cw, ch = im.width // cols, im.height // rows
    height, ascent = p.get('height', 8), p['ascent']
    scale = height / ch
    for r, line in enumerate(p['chars']):
        for c, g in enumerate(line):
            cp = ord(g)
            if g in ('\x00', ' ') or cp in cmap:
                continue
            w = 0
            px = []
            for x in range(cw):
                for y in range(ch):
                    if im.getpixel((c * cw + x, r * ch + y))[3] > 0:
                        px.append((x, y)); w = max(w, x + 1)
            if not w:
                continue
            gn = 'u%04X' % cp
            pen = TTGlyphPen(None)
            for (x, y) in px:
                # atlas row 0 is the top; the baseline sits `ascent` rows down
                square(pen, x * scale, (ascent - (y + 1) * scale))
            glyphs[gn] = pen.glyph(); adv[gn] = int((int(0.5 + w * scale) + 1) * U); cmap[cp] = gn

order = list(glyphs)
fb = FontBuilder(8 * U, isTTF=True)
fb.setupGlyphOrder(order)
fb.setupCharacterMap(cmap)
fb.setupGlyf(glyphs)
fb.setupHorizontalMetrics({g: (adv[g], 0) for g in order})
fb.setupHorizontalHeader(ascent=7 * U, descent=-1 * U)
fb.setupNameTable({'familyName': 'MCLocal', 'styleName': 'Regular'})
fb.setupOS2(sTypoAscender=7 * U, sTypoDescender=-1 * U, usWinAscent=8 * U, usWinDescent=2 * U)
fb.setupPost()
fb.save(f'{OUT}/mclocal.ttf')
print('wrote', f'{OUT}/mclocal.ttf', len(order), 'glyphs')
