"""rs_view.py OUT [CLIP T ...]: the Root Saint (or BOSS env) from four sides; with clip/time pairs, one row per pose."""
import sys, os, importlib
sys.path.insert(0, "."); sys.path.insert(0, "..")
import kit_bodies as K
import fauna_rig as R
from PIL import Image, ImageDraw
boss = os.environ.get("BOSS", "root_saint")
mod = importlib.import_module("kit.bosses." + boss)
d = mod.design()
skin, glow, _ = K.paint(d)
lit = Image.alpha_composite(skin.image(), glow.image())
rig = d["rig"]
out = sys.argv[1]
poses = [(None, 0)]
a = sys.argv[2:]
if a:
    poses = [(a[i], float(a[i + 1])) for i in range(0, len(a), 2)]
yaws = [float(v) for v in os.environ.get("YAWS", "0,90,180,35").split(",")]
pitch = float(os.environ.get("PITCH", "8"))
W, H = int(os.environ.get("W", "300")), int(os.environ.get("H", "360"))
trees = [rig.tree(rig.sample([(c, t, 1.0)]) if c else None) for c, t in poses]
T = bool(os.environ.get("T"))  # poses across, sides down
sheet = Image.new("RGB", ((W * len(poses), H * len(yaws)) if T else (W * len(yaws), H * len(poses))), (150, 166, 184))
dr = ImageDraw.Draw(sheet)
for ci, yaw in enumerate(yaws):
    b = R.bounds_of(trees, yaw, pitch)
    if os.environ.get("BAND"):
        h0, h1 = (float(v) for v in os.environ["BAND"].split(","))
        cx = (b[0] + b[1]) / 2
        half = (h1 - h0) * W / H / 2
        b = (cx - half, cx + half, 24 - h1, 24 - h0)
    px = min((W - 10) / (b[1] - b[0]), (H - 10) / (b[3] - b[2]))
    org = (W, H, (W - (b[1] - b[0]) * px) / 2 - b[0] * px, H - 6 - b[3] * px)
    for ri, tr in enumerate(trees):
        img = R.render(tr, lit, yaw=yaw, pitch=pitch, px=px, bounds=b, origin=org)
        at = (ri * W, ci * H) if T else (ci * W, ri * H)
        sheet.paste(img, at, img)
        dr.text((at[0] + 4, at[1] + 4), f"{poses[ri][0] or 'rest'} {poses[ri][1]:.2f} yaw {yaw:.0f}", fill=(20, 20, 20))
sheet.save(out)
print(out)
