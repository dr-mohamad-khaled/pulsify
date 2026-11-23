"""python3 grade_times.py <morning.jpg> <outdir> [--water]
Makes noon / sunset / night stand-ins from one Flow morning painting by colour grading, so a new world can
ship before Flow has repainted it at the other times of day. Writes <outdir>/{1,2,3,4}.jpg (morning first),
ready for make_world.py. Lights, lanterns and glowing things keep their glow at night.
"""
import sys, numpy as np
from PIL import Image

src, out = sys.argv[1], sys.argv[2]
water = '--water' in sys.argv
im = Image.open(src).convert('RGB')
a = np.asarray(im).astype(np.float32) / 255
H, W, _ = a.shape
y = np.linspace(0, 1, H)[:, None, None]          # 0 top .. 1 bottom
lum = a @ np.array([0.299, 0.587, 0.114], np.float32)
sat = a.max(-1) - a.min(-1)

def sm(e0, e1, x): t = np.clip((x - e0) / (e1 - e0), 0, 1); return t * t * (3 - 2 * t)
def save(x, n): Image.fromarray((np.clip(x, 0, 1) * 255 + 0.5).astype(np.uint8)).save(f'{out}/{n}.jpg', quality=95)

save(a, 1)

# noon: neutral, a bit crisper and more saturated
l = lum[..., None]
noon = (a * np.array([0.97, 1.0, 1.04], np.float32) - l) * 1.1 + l
noon = (noon - 0.5) * 1.06 + 0.5 + 0.02
save(noon, 2)

# sunset: warm grade, an orange-pink sky band, the ground a little darker
warm = a * np.array([1.14, 0.9, 0.7], np.float32) + np.array([0.04, 0.01, 0.0], np.float32) * l
sky = (1 - sm(0.15, 0.55, y)) * (0 if water else 1)
band = np.array([1.0, 0.55, 0.45], np.float32) * (0.55 + 0.45 * l)
sunset = warm * (1 - 0.22 * sky) + band * 0.22 * sky
sunset *= 1 - 0.12 * sm(0.5, 1.0, y)
save(sunset, 3)

# night: moonlit blue from the brightness, glowing things kept (warm lights, saturated bright spots)
if water:
    night = lum[..., None] * np.array([0.08, 0.2, 0.42], np.float32) * 0.85
else:
    night = lum[..., None] * np.array([0.26, 0.34, 0.6], np.float32) * 0.78 + a * np.array([0.04, 0.05, 0.09], np.float32)
glow = np.clip(sm(0.62, 0.92, lum) * sm(0.25, 0.55, sat) + sm(0.85, 0.98, lum) * 0.6, 0, 1)[..., None]
night = night * (1 - glow) + a * glow * 0.95
save(night ** 1.08, 4)
print('graded', src)
