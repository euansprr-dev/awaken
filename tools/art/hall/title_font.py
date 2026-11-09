"""Bakes the Hall's title type into a bitmap font: Jacquard 24 by Sarah Cadigan-Fried (SIL Open Font License 1.1; the
licence travels beside the bitmap as assets/awaken/font/hall_title_OFL.txt). The font file itself is not in the repo:
python3 tools/art/hall/title_font.py /path/to/Jacquard24-Regular.ttf"""
import json, os, sys
from PIL import Image, ImageDraw, ImageFont
R = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', '..'))
A = R + '/src/main/resources/assets/awaken'
SIZE, CW, CH, BASE = 24, 36, 34, 25  # the font's own pixel grid is 24 to the em
CHARS = [chr(c) for c in range(33, 127)] + ['’']
COLS = 16
font = ImageFont.truetype(sys.argv[1], SIZE)
rows = [CHARS[i:i + COLS] for i in range(0, len(CHARS), COLS)]
sheet = Image.new('RGBA', (CW * COLS, CH * len(rows)), (0, 0, 0, 0))
for r, row in enumerate(rows):
    for c, ch in enumerate(row):
        cell = Image.new('L', (CW, CH), 0)
        d = ImageDraw.Draw(cell)
        d.fontmode = '1'  # hard pixels: this is a pixel face
        box = font.getbbox(ch, anchor='ls')
        d.text((1 - min(box[0], 0), BASE), ch, fill=255, font=font, anchor='ls')
        sheet.paste((255, 255, 255, 255), (c * CW, r * CH), cell)
    rows[r] = ''.join(row).ljust(COLS, '\0')
sheet.save(A + '/textures/font/hall_title.png', optimize=True)
json.dump({'providers': [
    {'type': 'space', 'advances': {' ': 8}},
    {'type': 'bitmap', 'file': 'awaken:font/hall_title.png', 'height': CH, 'ascent': BASE, 'chars': rows}]},
    open(A + '/font/hall_title.json', 'w'), indent=1, ensure_ascii=True)
