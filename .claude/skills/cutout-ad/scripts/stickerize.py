"""Turn transparent cut-outs into Vox-style "stickers" (white outline) and write img/info.js.

Usage:  python stickerize.py <img_dir> [outline_px]

Input : <img_dir>/raw_<Name>.png   transparent PNGs, e.g. from `npx hyperframes remove-background x.png -o raw_Name.png`
Output: <img_dir>/<Name>.png       cropped to the subject + white outline (default 11 px)
        <img_dir>/info.js          window.INFO = {Name:{w,h}}  (the composition needs it to anchor each pose)
Existing <Name>.png files that have no raw_ twin are still listed in info.js.
Move the raw_* files out of public/ afterwards (keep them in work/raw/) so they are not bundled.
"""
import sys, glob, os, json
import numpy as np
from PIL import Image, ImageFilter

d = sys.argv[1]
R = int(sys.argv[2]) if len(sys.argv) > 2 else 11
for f in sorted(glob.glob(os.path.join(d, "raw_*.png"))):
    n = os.path.basename(f)[4:-4]
    im = Image.open(f).convert("RGBA")
    a = np.array(im)[:, :, 3]
    ys, xs = np.where(a > 24)
    im = im.crop((xs.min(), ys.min(), xs.max() + 1, ys.max() + 1))
    pad = R + 4
    canvas = Image.new("RGBA", (im.width + 2 * pad, im.height + 2 * pad), (0, 0, 0, 0))
    canvas.paste(im, (pad, pad))
    al = canvas.split()[3].point(lambda v: 255 if v > 24 else 0)
    grown = al.filter(ImageFilter.MaxFilter(2 * R + 1)).filter(ImageFilter.GaussianBlur(1.2))
    white = Image.new("RGBA", canvas.size, (255, 255, 255, 255))
    white.putalpha(grown)
    Image.alpha_composite(white, canvas).save(os.path.join(d, n + ".png"))
    print("sticker", n)

info = {}
for f in sorted(glob.glob(os.path.join(d, "*.png"))):
    n = os.path.basename(f)[:-4]
    if n.startswith("raw_"):
        continue
    w, h = Image.open(f).size
    info[n] = {"w": w, "h": h}
open(os.path.join(d, "info.js"), "w").write("window.INFO=" + json.dumps(info) + ";")
print("info.js:", len(info), "images")
