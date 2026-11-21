"""python3 make_world.py <id> <morning> <noon> <sunset> <night|-> : writes assets/worlds/p<id>_1..4.webp"""
import sys, numpy as np
from PIL import Image, ImageFilter
wid = sys.argv[1]; srcs = sys.argv[2:6]
OUT = '/home/claude/pulsify/app/src/main/assets/worlds'
W, H = 1536, 2752
def load(p):
    im = Image.open(p).convert('RGB')
    if im.size != (W, H): im = im.resize((W, H), Image.LANCZOS)
    return im
ims = [load(p) if p != '-' else None for p in srcs]
if ims[3] is None:     # stand-in night: blue moonlit grade of noon until the real one arrives
    a = np.asarray(ims[1]).astype(np.float32) / 255
    l = a @ np.array([0.3, 0.55, 0.15])
    n = np.stack([l * 0.30, l * 0.38, l * 0.62], -1) * 0.75 + a * np.array([0.05, 0.06, 0.1])
    ims[3] = Image.fromarray((np.clip(n, 0, 1) ** 1.1 * 255).astype(np.uint8))
for i, im in enumerate(ims):
    im = im.filter(ImageFilter.UnsharpMask(radius=1.2, percent=40, threshold=2))
    im.save(f'{OUT}/p{wid}_{i + 1}.webp', 'WEBP', quality=78, method=6)
import os
print(wid, [os.path.getsize(f'{OUT}/p{wid}_{i + 1}.webp') // 1024 for i in range(4)], 'KB')
