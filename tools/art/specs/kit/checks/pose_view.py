"""pose_view.py OUT 'kw' 'kw' ...: BOSS's _pose(r, **kw) for each kw (a Python dict literal), YAWS sides down."""
import sys, os, importlib
sys.path.insert(0, "."); sys.path.insert(0, "..")
import kit_bodies as K
import fauna_rig as R
from kit import core
from PIL import Image, ImageDraw
mod = importlib.import_module("kit.bosses." + os.environ["BOSS"])
d = mod.design()
skin, glow, _ = K.paint(d)
lit = Image.alpha_composite(skin.image(), glow.image())
rig = d["rig"]
out = sys.argv[1]
kws = sys.argv[2:]
trees = [rig.tree(core.as_sample(mod._pose(rig, **eval(k)))) for k in kws]
yaws = [float(v) for v in os.environ.get("YAWS", "35,90,0").split(",")]
pitch = float(os.environ.get("PITCH", "8"))
W, H = int(os.environ.get("W", "300")), int(os.environ.get("H", "330"))
sheet = Image.new("RGB", (W * len(kws), H * len(yaws)), (150, 166, 184))
dr = ImageDraw.Draw(sheet)
for ci, yaw in enumerate(yaws):
    b = R.bounds_of(trees, yaw, pitch)
    px = min((W - 10) / (b[1] - b[0]), (H - 10) / (b[3] - b[2]))
    org = (W, H, (W - (b[1] - b[0]) * px) / 2 - b[0] * px, H - 6 - b[3] * px)
    for ri, tr in enumerate(trees):
        img = R.render(tr, lit, yaw=yaw, pitch=pitch, px=px, bounds=b, origin=org)
        at = (ri * W, ci * H)
        sheet.paste(img, at, img)
        dr.text((at[0] + 4, at[1] + 4), f"{ri} yaw {yaw:.0f}", fill=(20, 20, 20))
sheet.save(out)
print(out)
