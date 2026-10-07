import sys
from PIL import Image, ImageDraw, ImageFilter
for n in sys.argv[1:]:
    im=Image.open(f"cast/{n}.png").convert("RGB")
    W,H=im.size
    m=im.copy()
    for pt in [(0,0),(W-1,0),(0,H-1),(W-1,H-1),(W//2,0),(W//2,H-1),(0,H//2),(W-1,H//2)]:
        ImageDraw.floodfill(m,pt,(255,0,255),thresh=22)
    mask=Image.eval(m.convert("RGB").split()[1],lambda g:0 if g==0 else 255)  # magenta has G=0
    # pixels that stayed non-magenta are the subject
    r,g,b=m.split()
    subj=Image.eval(g,lambda v:255 if v!=0 else 0)
    # pure magenta only where flooded; guard against real magenta (none expected)
    subj=subj.filter(ImageFilter.MinFilter(3)).filter(ImageFilter.GaussianBlur(0.8))
    out=im.convert("RGBA"); out.putalpha(subj); out.save(f"raw/raw_{n}.png")
