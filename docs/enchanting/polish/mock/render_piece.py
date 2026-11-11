"""Renders one of our weapons lying flat, as the Inscribing Table would show it (the polish plan's mock-up art):
the real mesh through tools/art/mesh_render.py's copy of the weapon shader, turned so the blade runs across the picture,
with or without an enchantment's script line. Offline, numpy and PIL only.

    python3 docs/enchanting/polish/mock/render_piece.py OUT.png <weapon id> [palette index] [brightness 0-1] [pixels per texel]
"""
import math
import os
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.abspath(os.path.join(HERE, "../../../../tools/art")))
import mesh_render as MR  # noqa: E402

KEY = np.array([255.0, 0.0, 255.0])


def lying(wid, script=(0, 0.0), scale=10.0, size=(520, 200), roll=-45.0, tilt=18.0, time=0.3):
    m, tex = MR.load(wid)
    r = math.radians(roll)
    R = np.array([[math.cos(r), -math.sin(r), 0], [math.sin(r), math.cos(r), 0], [0, 0, 1]])
    m = dict(m)
    m["pos"] = (m["pos"] - 0.5) @ R.T + 0.5
    m["nrm"] = m["nrm"] @ R.T
    img = MR.render(m, tex, yaw=0, pitch=tilt, scale=scale, size=size, bg=KEY, script=script, time=time)
    a = np.asarray(img).astype(int)
    alpha = (np.abs(a - KEY[None, None]).sum(axis=2) > 6) * 255
    out = np.dstack([a, alpha]).astype(np.uint8)
    return Image.fromarray(out, "RGBA")


if __name__ == "__main__":
    wid = sys.argv[2]
    idx = int(sys.argv[3]) if len(sys.argv) > 3 else 0
    lit = float(sys.argv[4]) if len(sys.argv) > 4 else 0.0
    scale = float(sys.argv[5]) if len(sys.argv) > 5 else 10.0
    im = lying(wid, (idx, lit), scale=scale, size=(int(64 * scale), int(28 * scale)))
    box = im.getbbox()
    im.crop(box).save(sys.argv[1])
    print(sys.argv[1], im.crop(box).size)
