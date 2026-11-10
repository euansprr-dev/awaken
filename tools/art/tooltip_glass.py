#!/usr/bin/env python3
"""The band's glass for item tooltips (SPEC 57.9): the two nine-slice sprites the game looks up for the tooltip style
`awaken:glass` (tooltip/glass_background, tooltip/glass_frame). Same margins as vanilla's own (a 9 px clear border), the
plate's navy, a hairline rim that is brightest at the top, and the plate's two corner brackets.
Run from the repo root: python3 tools/art/tooltip_glass.py"""
from PIL import Image

OUT = 'src/main/resources/assets/awaken/textures/gui/sprites/tooltip/'
NAVY = (0x0F, 0x17, 0x24, 246)
EDGE = (0x7F, 0xC3, 0xFF)
HOT = (0xC4, 0xE6, 0xFF)

bg = Image.new('RGBA', (100, 100), (0, 0, 0, 0))
for y in range(8, 92):
    for x in range(8, 92):
        if x in (8, 91) and y in (8, 91):
            continue
        bg.putpixel((x, y), NAVY)
bg.save(OUT + 'glass_background.png')

fr = Image.new('RGBA', (100, 100), (0, 0, 0, 0))
for i in range(9, 91):
    t = (i - 9) / 81
    fr.putpixel((i, 9), EDGE + (230,))
    fr.putpixel((i, 90), EDGE + (110,))
    a = int(230 - 120 * t)
    fr.putpixel((9, i), EDGE + (a,))
    fr.putpixel((90, i), EDGE + (a,))
for k in range(5):
    fr.putpixel((9 + k, 8), HOT + (255,))
    fr.putpixel((8, 9 + k), HOT + (255,))
    fr.putpixel((90 - k, 91), HOT + (200,))
    fr.putpixel((91, 90 - k), HOT + (200,))
fr.save(OUT + 'glass_frame.png')
