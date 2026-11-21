#!/usr/bin/env python3
"""
Builds a KIT1 model + coat mask for the app from a pet exported by the Blender generator
(text block `petgen`, export_gen -> Documents/Pulsify3D/gen/<Name>.json).

  python3 tools/petkit.py CatA.json app/src/main/assets/models/cat_a

Writes <out>.kit and <out>_mask.png (512 px):
  R = tabby stripes, G = cream / white patches, B = ambient occlusion (1 = open).
Per vertex "pt" byte = siamese points weight (ears, legs, tail, face mask).
"""
import json, math, struct, sys
import numpy as np
from PIL import Image

BONES = ['body', 'head', 'eyeL', 'eyeR', 'earL', 'earR', 'legL', 'legR', 'tail']
RES = 1024


def vnoise(p, seed=0):
    """Smooth 3D value noise in [-1, 1] for (N, 3) points."""
    rng = np.random.RandomState(seed)
    perm = rng.rand(16, 16, 16) * 2 - 1
    q = np.floor(p).astype(int); f = p - q
    f = f * f * (3 - 2 * f)
    out = 0
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                w = (f[:, 0] if dx else 1 - f[:, 0]) * (f[:, 1] if dy else 1 - f[:, 1]) * (f[:, 2] if dz else 1 - f[:, 2])
                out = out + w * perm[(q[:, 0] + dx) % 16, (q[:, 1] + dy) % 16, (q[:, 2] + dz) % 16]
    return out


def sstep(a, b, x):
    t = np.clip((x - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


def tail_param(pts, P):
    """Arc-length position (0 root .. 1 tip) of points along the tail's control polyline."""
    C = np.array(P, float)
    # densify the polyline with Catmull-Rom so it follows the bevelled bezier closely
    dense = []
    for i in range(len(C) - 1):
        p0 = C[max(i - 1, 0)]; p1 = C[i]; p2 = C[i + 1]; p3 = C[min(i + 2, len(C) - 1)]
        for t in np.linspace(0, 1, 12, endpoint=False):
            t2, t3 = t * t, t * t * t
            dense.append(0.5 * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3))
    dense.append(C[-1]); D = np.array(dense)
    seg = np.linalg.norm(np.diff(D, axis=0), axis=1); acc = np.concatenate([[0], np.cumsum(seg)]); L = acc[-1]
    a = D[:-1]; b = D[1:]; ab = b - a
    out = np.zeros(len(pts))
    for i in range(0, len(pts), 2048):
        p = pts[i:i + 2048, None, :]
        t = np.clip(((p - a) * ab).sum(-1) / (ab * ab).sum(-1), 0, 1)
        d = np.linalg.norm(p - (a + ab * t[..., None]), axis=-1)
        j = d.argmin(1)
        out[i:i + 2048] = (acc[j] + seg[j] * t[np.arange(len(j)), j]) / L
    return out


def patterns(P, bone, pos, nrm, kind, tailt=None):
    """Stripes, cream and siamese-points weights for points on one part."""
    x, y, z = pos[:, 0], pos[:, 1], pos[:, 2]
    HC = np.array(P['headC']); HR = np.array(P['headR'])
    n = vnoise(pos * 6.0, 3) * 0.5 + vnoise(pos * 13.0, 5) * 0.25
    stripes = np.zeros(len(pos)); cream = np.zeros(len(pos)); pts = np.zeros(len(pos))
    band = lambda v, w=0.5: sstep(w + 0.12, w + 0.22, v)
    if bone == 'head':
        rx = (x - HC[0]) / HR[0]; ry = (y - HC[1]) / HR[1]; rz = (z - HC[2]) / HR[2]
        top = sstep(0.18, 0.5, rz) * sstep(0.3, -0.15, ry)          # forehead and crown
        m = np.maximum.reduce([sstep(0.05, 0.028, np.abs(rx - c + n * 0.025)) * (1 - np.abs(c) * 0.5) for c in (-0.2, 0.0, 0.2)])
        cheek = np.maximum.reduce([sstep(0.035, 0.018, np.abs(rz - c - rx * 0.0 + n * 0.02)) for c in (-0.08, 0.1)]) * sstep(0.62, 0.8, np.abs(rx)) * sstep(0.2, -0.3, ry)
        stripes = np.maximum(m * top, cheek)
        # muzzle, chin and a soft blaze up the nose
        mz = P['muzzle']
        mc = HC + np.array([0, mz['y'], mz['z']])
        d = np.sqrt(((x - mc[0]) / (mz['spread'] + mz['r'][0] * 1.35)) ** 2 + ((z - mc[2] + 0.02) / (mz['r'][2] * 1.9)) ** 2)
        cream = sstep(1.05, 0.85, d + n * 0.08) * sstep(-0.05, -0.35, ry)
        # siamese face mask: dark centre of the face fading out to the cheeks
        fd = np.sqrt((rx / 0.62) ** 2 + ((rz + 0.25) / 0.7) ** 2)
        pts = sstep(1.0, 0.45, fd) * sstep(0.0, -0.45, ry)
    elif bone == 'body':
        back = sstep(-0.12, 0.08, y)
        s = np.sin((z * 17.0 + np.abs(x) * 4.0 + n * 1.2)) * 0.5 + 0.5
        stripes = band(s, 0.5) * back * sstep(0.08, 0.2, z)
        hn = P['body']['haunch'][0]
        hs = np.sin((np.hypot(np.abs(x) - hn[0], y - hn[1]) * 26.0 + n)) * 0.5 + 0.5
        stripes = np.maximum(stripes, band(hs, 0.55) * sstep(0.13, 0.2, np.abs(x)) * sstep(0.32, 0.18, z) * sstep(-0.1, 0.05, y))
        ch = P['body']['chest']
        d = np.sqrt(((x - ch[0][0]) / (ch[1][0] * 0.95)) ** 2 + ((z - ch[0][2] + 0.05) / (ch[1][2] * 1.35)) ** 2)
        cream = sstep(1.0, 0.82, d + n * 0.1) * sstep(-0.02, -0.12, y)
        cream = np.maximum(cream, sstep(0.11, 0.07, z) * sstep(-0.05, -0.12, y))   # hind toes
    elif bone.startswith('leg'):
        top = max(p[2] for p in P['leg']['pts'])
        s = np.sin(z * 26.0 + n) * 0.5 + 0.5
        stripes = band(s, 0.55) * sstep(-0.35, 0.0, -nrm[:, 1] * -1) * sstep(0.12, 0.2, z)
        cream = sstep(0.14, 0.1, z + n * 0.02)
        pts = sstep(top - 0.05, 0.12, z) * 0.6 + 0.4
    elif bone == 'tail':
        t = tailt
        stripes = band(np.sin(t * 34.0 + n * 0.6) * 0.5 + 0.5, 0.5) * sstep(0.1, 0.2, t)
        if kind == 'dog':
            stripes *= 0.0; cream = sstep(0.82, 0.92, t)
        pts = sstep(0.0, 0.5, t) * 0.7 + 0.3
    elif bone.startswith('ear'):
        pts = np.ones(len(pos)); stripes = np.zeros(len(pos))
    if kind == 'dog':
        stripes *= 0.0
    return np.clip(stripes, 0, 1), np.clip(cream, 0, 1), np.clip(pts, 0, 1)


def ambient(V, N, F):
    """Per-vertex ambient occlusion from every other triangle (disc approximation) and the floor."""
    A = V[F]; c = A.mean(1)
    cr = np.cross(A[:, 1] - A[:, 0], A[:, 2] - A[:, 0]); area = np.linalg.norm(cr, axis=1) * 0.5
    fn = cr / np.maximum(1e-9, 2 * area[:, None])
    occ = np.zeros(len(V))
    for i in range(0, len(V), 512):
        p = V[i:i + 512, None, :]; n = N[i:i + 512, None, :]
        d = c[None] - p; r2 = (d * d).sum(-1) + 1e-6; r = np.sqrt(r2)
        cr_ = np.clip((d * n).sum(-1) / r, 0, 1)
        ce = np.clip(np.abs((d * fn[None]).sum(-1)) / r, 0, 1)
        occ[i:i + 512] = (area[None] * cr_ * ce / (np.pi * r2 + area[None])).sum(1)
    ao = np.clip(1 - occ * 1.15, 0.25, 1)
    ao *= 1 - 0.35 * sstep(0.16, 0.0, V[:, 2]) * np.clip(0.5 - N[:, 2], 0, 1)   # contact with the floor
    return ao


def raster(UV, F, vals, res=RES):
    """Fills a (res, res, k) image from per-vertex values over UV triangles, with a dilated margin."""
    k = vals.shape[1]
    img = np.zeros((res, res, k)); cov = np.zeros((res, res), bool)
    P = UV * res
    for tri in F:
        a, b, c = P[tri]
        x0 = int(max(0, np.floor(min(a[0], b[0], c[0])))); x1 = int(min(res - 1, np.ceil(max(a[0], b[0], c[0]))))
        y0 = int(max(0, np.floor(min(a[1], b[1], c[1])))); y1 = int(min(res - 1, np.ceil(max(a[1], b[1], c[1]))))
        if x1 < x0 or y1 < y0: continue
        gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
        den = (b[1] - c[1]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[1] - c[1])
        if abs(den) < 1e-12: continue
        w0 = ((b[1] - c[1]) * (gx - c[0]) + (c[0] - b[0]) * (gy - c[1])) / den
        w1 = ((c[1] - a[1]) * (gx - c[0]) + (a[0] - c[0]) * (gy - c[1])) / den
        w2 = 1 - w0 - w1
        m = (w0 >= -0.02) & (w1 >= -0.02) & (w2 >= -0.02)
        if not m.any(): continue
        v = w0[..., None] * vals[tri[0]] + w1[..., None] * vals[tri[1]] + w2[..., None] * vals[tri[2]]
        sub = img[y0:y1 + 1, x0:x1 + 1]; sc = cov[y0:y1 + 1, x0:x1 + 1]
        sub[m] = v[m]; sc[m] = True
    # grow islands outward so filtering never reads the empty background
    for _ in range(6):
        nb = np.zeros_like(img); cnt = np.zeros(cov.shape)
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            sh = np.roll(np.roll(img, dy, 0), dx, 1); sc = np.roll(np.roll(cov, dy, 0), dx, 1)
            nb[sc] += sh[sc]; cnt[sc] += 1
        grow = (~cov) & (cnt > 0)
        img[grow] = nb[grow] / cnt[grow][:, None]; cov |= grow
    return img, cov


def build(js, out):
    D = json.load(open(js)); P = D['meta']; kind = P.get('kind', 'cat')
    parts = D['parts']
    allv = np.concatenate([np.array(p['v'], float) for p in parts])
    floorZ = float(allv[:, 2].min())
    # pivots
    def pv(bone):
        return np.concatenate([np.array(p['v'], float) for p in parts if p['bone'] == bone])
    piv = {}
    hv = pv('head'); piv['head'] = np.array([0.0, hv[:, 1].mean() + 0.05, hv[:, 2].min() + 0.08])
    piv['body'] = np.array([0.0, 0.0, floorZ])
    for sd in 'LR':
        e = np.concatenate([np.array(p['v'], float) for p in parts if p['bone'] == 'eye' + sd and p['role'] == 4 and p['name'].startswith('Eye')])
        piv['eye' + sd] = (e.min(0) + e.max(0)) / 2
        ear = pv('ear' + sd); zmin, zmax = ear[:, 2].min(), ear[:, 2].max()
        if P['ear']['type'] == 'flop':
            sel = ear[ear[:, 2] > zmax - (zmax - zmin) * 0.2]
        else:
            sel = ear[ear[:, 2] < zmin + (zmax - zmin) * 0.25]
        piv['ear' + sd] = sel.mean(0)
        lg = pv('leg' + sd); zt = lg[:, 2].max(); piv['leg' + sd] = lg[lg[:, 2] > zt - 0.06].mean(0)
    piv['tail'] = np.array(P['tail']['pts'][0], float)
    # gather vertices
    VB = []; IB = []; coat = []          # coat: (global index, pos, nrm, uv) for the mask
    base = 0
    allN = []
    for p in parts:
        V = np.array(p['v'], float); N = np.array(p['n'], float); U = np.array(p['uv'], float); T = np.array(p['t'], int)
        bi = BONES.index(p['bone']); role = p['role']
        tailt = tail_param(V, P['tail']['pts']) if p['bone'] == 'tail' else np.zeros(len(V))
        st, cr, pt = patterns(P, p['bone'], V, N, kind, tailt)
        if role != 0: pt = np.zeros(len(V))
        if role == 5:      # iris: planar coordinates across the disc for the shader's gradient
            mn, mx = V.min(0), V.max(0)
            U = np.stack([(V[:, 0] - mn[0]) / max(1e-6, mx[0] - mn[0]), (V[:, 2] - mn[2]) / max(1e-6, mx[2] - mn[2])], 1)
            U[:, 1] = 1 - U[:, 1]          # the shader flips V back
        VB.append((V, N, U, bi, role, pt, tailt))
        IB.append(T + base)
        if role == 0: coat.append((base, len(V), T, V, N, U, st, cr, p['bone'], tailt))
        base += len(V)
    V = np.concatenate([b[0] for b in VB]); N = np.concatenate([b[1] for b in VB]); F = np.concatenate(IB)
    print(f'{len(V)} verts, {len(F)} tris; computing ambient occlusion...')
    ao = ambient(V, N, F)
    # coat mask: rasterize position, normal and occlusion per part, then evaluate the patterns per texel
    img = np.zeros((RES, RES, 3)); covered = np.zeros((RES, RES), bool)
    for (b0, n, T, Vp, Np, U, st, cr, bone, tl) in coat:
        vals = np.concatenate([Vp, Np, ao[b0:b0 + n, None], tl[:, None]], 1)
        R, cov = raster(U, T, vals)
        m = cov & ~covered
        if not m.any(): continue
        pos = R[m][:, 0:3]; nrm = R[m][:, 3:6]; a_ = R[m][:, 6]; t_ = R[m][:, 7]
        st2, cr2, _ = patterns(P, bone, pos, nrm, kind, t_)
        img[m] = np.stack([st2, cr2, a_], 1); covered |= m
    img = np.flipud(img)                       # UV v=0 is the bottom row
    im = Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8), 'RGB').resize((512, 512), Image.LANCZOS)
    im.save(out + '_mask.png', optimize=True)
    # KIT1
    vb = bytearray()
    for (Vp, Np, U, bi, role, pt, tailt) in VB:
        nn = Np / np.maximum(1e-9, np.linalg.norm(Np, axis=1))[:, None]
        for i in range(len(Vp)):
            vb += struct.pack('<3f4b2H4B', Vp[i, 0], Vp[i, 1], Vp[i, 2], int(nn[i, 0] * 127), int(nn[i, 1] * 127), int(nn[i, 2] * 127), 0,
                              int(np.clip(U[i, 0], 0, 1) * 65535), int(np.clip(U[i, 1], 0, 1) * 65535), bi, role, int(np.clip(pt[i], 0, 1) * 255), int(np.clip(tailt[i], 0, 1) * 255))
    assert len(V) < 65536
    with open(out + '.kit', 'wb') as f:
        f.write(b'KIT1'); f.write(struct.pack('<IIf', len(V), len(F) * 3, floorZ))
        for b in BONES: f.write(struct.pack('<3f', *piv[b]))
        f.write(vb); f.write(F.astype('<u2').tobytes())
    print('wrote', out, 'floorZ', round(floorZ, 3), 'height', round(float(allv[:, 2].max()) - floorZ, 3))


if __name__ == '__main__':
    build(sys.argv[1], sys.argv[2])
