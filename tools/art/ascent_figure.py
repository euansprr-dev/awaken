"""The Lesson's figure of light (SPEC §71): a 64x64 player-layout texture of pale gold, drawn emissive and see-through by
body.ascent.client.LessonFigureRenderer. One colour a body part, a little brighter toward the head, so the figure reads as a
shape of light and its limbs can still be told apart. The second layers (hat, jacket, sleeves, trousers) are empty.

    python3 tools/art/ascent_figure.py
"""
import os
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "src/main/resources/assets/awaken/textures/entity/ascent/lesson_figure.png")
# (x0, y0, x1, y1) of each first-layer island, and its gold
PARTS = [
    ((0, 0, 32, 16), (255, 244, 204)),    # head
    ((16, 16, 40, 32), (255, 232, 166)),  # body
    ((40, 16, 56, 32), (255, 228, 156)),  # right arm
    ((32, 48, 48, 64), (255, 228, 156)),  # left arm
    ((0, 16, 16, 32), (252, 214, 132)),   # right leg
    ((16, 48, 32, 64), (252, 214, 132)),  # left leg
]
im = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
for (x0, y0, x1, y1), (r, g, b) in PARTS:
    for y in range(y0, y1):
        # a slow brightening up each part (the island's side faces run top to bottom in its lower rows)
        k = 1.0 - 0.10 * (y - y0) / max(1, y1 - y0 - 1)
        for x in range(x0, x1):
            im.putpixel((x, y), (round(r * k), round(g * k), round(b * k), 255))
os.makedirs(os.path.dirname(OUT), exist_ok=True)
im.save(OUT)
print(OUT)
