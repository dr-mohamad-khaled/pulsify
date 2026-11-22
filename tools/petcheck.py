"""Reads a .pet and draws skinned vertex clouds for a few clip frames (front view), to check the export."""
import struct, sys, numpy as np
from PIL import Image, ImageDraw

def load(path):
    b = open(path, 'rb').read()
    assert b[:4] in (b'PET1', b'PET2', b'PET3')
    nv, ni, nb, nc, height, fps, eye_mode = struct.unpack_from('<IIIIffI', b, 4)
    v = b[3] - 48
    nctl = struct.unpack_from('<I', b, 32)[0] if v >= 2 else 8
    flags = struct.unpack_from('<I', b, 36)[0] if v >= 3 else 0
    ib = 4 if flags & 1 else 2
    o = 4 + 28 + (4 if v >= 2 else 0) + (4 if v >= 3 else 0) + 24 + (16 if eye_mode == 3 else 0)
    V = np.frombuffer(b, np.uint8, nv * 40, o).reshape(nv, 40); o += nv * 40
    pos = V[:, 0:12].copy().view(np.float32).reshape(nv, 3)
    bones = V[:, 24:28].astype(int); w = V[:, 28:32].astype(np.float32) / 255
    col = V[:, 32:36]; eid = None
    o += ni * ib
    clips = {}
    for _ in range(nc):
        L = b[o]; o += 1; name = b[o:o + L].decode(); o += L
        fr, loop = struct.unpack_from('<HB', b, o); o += 3
        m = np.frombuffer(b, np.float16, fr * nb * 12, o).astype(np.float32).reshape(fr, nb, 3, 4); o += fr * nb * 12 * 2
        c = np.frombuffer(b, np.float16, fr * nctl, o).astype(np.float32).reshape(fr, nctl); o += fr * nctl * 2
        clips[name] = (m, c, loop)
    return dict(pos=pos, bones=bones, w=w, col=col, eid=eid, clips=clips, nb=nb, height=height, eye=eye_mode)

def skin(P, mats):
    p4 = np.concatenate([P['pos'], np.ones((len(P['pos']), 1), np.float32)], 1)
    out = np.zeros_like(P['pos'])
    for k in range(4):
        M = mats[P['bones'][:, k]]                     # (n,3,4)
        out += P['w'][:, k:k + 1] * np.einsum('nij,nj->ni', M, p4)
    return out

if __name__ == '__main__':
    P = load(sys.argv[1]); names = sys.argv[2].split(','); out = sys.argv[3]
    S = 260; img = Image.new('RGB', (S * 4, S * len(names)), (12, 12, 18)); d = ImageDraw.Draw(img)
    for r, nm in enumerate(names):
        m, c, loop = P['clips'][nm]
        for i, f in enumerate(np.linspace(0, len(m) - 1, 4).astype(int)):
            q = skin(P, m[f])
            vis = np.ones(len(q), bool)
            for (x, y, z), col, ok in zip(q[::3], P['col'][::3], vis[::3]):
                px = i * S + S / 2 + x * S * 0.45; py = r * S + S * 0.9 - z * S * 0.55
                d.point((px, py), fill=(200, 200, 210) if col[3] == 0 else tuple(int(v) for v in col[:3]))
            d.text((i * S + 4, r * S + 4), f'{nm} f{f} ctl {np.round(c[f][:2], 2)} e{c[f][6]:.0f}', fill=(255, 200, 120))
    img.save(out)
    print('clips', list(P['clips'].keys()))
