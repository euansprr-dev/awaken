import sys, os
from PIL import Image
# strip.py out.jpg cols shot1 shot2 ...  : real-game shots side by side, 640 wide each
out, cols, shots = sys.argv[1], int(sys.argv[2]), sys.argv[3:]
W, H = 640, 360
rows = (len(shots) + cols - 1) // cols
sheet = Image.new('RGB', (W * cols + 4 * (cols - 1), H * rows + 4 * (rows - 1)), (12, 12, 14))
for i, f in enumerate(shots):
    im = Image.open(f).convert('RGB').resize((W, H), Image.LANCZOS)
    sheet.paste(im, ((i % cols) * (W + 4), (i // cols) * (H + 4)))
sheet.save(out, quality=80, optimize=True)
print(os.path.basename(out), os.path.getsize(out) // 1024, 'KB')
