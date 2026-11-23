"""GLB -> Pulsify statue files: <name>.sta + <name>_c/_n/_mr.webp

STA1 (static) little endian:
  'STA1' u32 nv u32 ni f32 min[3] f32 max[3]
  nv * 20: i16 pos[3] (pos / max) | u16 pad | i8 nrm[4] | i8 tan[4] (w = handedness) | u16 uv[2]
  ni * u32 indices
STA2 (skinned) = STA1 header with 'STA2', then
  u32 nb, nb * (i32 parent, f32 head[3]) in model units (after recentring and scaling)
  nv * 28: the 20 bytes above + u8 joint[4] + u8 weight[4]
  ni * u32 indices
Model is recentred so its bbox centre is the origin, y up, front +Z.

usage: glb2sta2.py src.glb name outdir [--tris N] [--fit F] [--bird | --crow]
  --tris N  simplify to about N triangles (meshoptimizer, keeps UV seams)
  --fit F   scale so the largest half-extent is F model units
  --bird    keep the skin, merged to the reduced flying-bird skeleton below (StatueBird)
  --perch NAME  keep the skin, merged to the perched-bird skeleton (StatueCrow) for NAME = tiel | budgie
  --cockatiel keep the skin, merged to the flying-bird skeleton from the Meshy cockatiel rig (tail halves joined)
  --ringneck  keep the skin, merged to the same flying-bird skeleton from a Meshy SmartRig biped bird (StatueBird)
  --crow    keep the skin, merged to the perched-crow skeleton (Meshy SmartRig biped bird, StatueCrow)
"""
import io, json, struct, sys, argparse
import numpy as np
from PIL import Image
import meshoptimizer as mo

ap = argparse.ArgumentParser()
ap.add_argument('src'); ap.add_argument('name'); ap.add_argument('outdir')
ap.add_argument('--tris', type=int, default=0); ap.add_argument('--fit', type=float, default=0)
ap.add_argument('--bird', action='store_true'); ap.add_argument('--crow', action='store_true'); ap.add_argument('--ringneck', action='store_true'); ap.add_argument('--cockatiel', action='store_true'); ap.add_argument('--perch', default=''); ap.add_argument('--tex', type=int, default=2048)
A = ap.parse_args()

b = open(A.src, 'rb').read()
jl = struct.unpack_from('<I', b, 12)[0]; J = json.loads(b[20:20 + jl]); BIN = b[20 + jl + 8:]
CT = {5126: np.float32, 5123: np.uint16, 5125: np.uint32, 5121: np.uint8, 5122: np.int16, 5120: np.int8}
NC = {'SCALAR': 1, 'VEC2': 2, 'VEC3': 3, 'VEC4': 4, 'MAT4': 16}
def acc(i):
    a = J['accessors'][i]; bv = J['bufferViews'][a['bufferView']]
    dt = np.dtype(CT[a['componentType']]); n = NC[a['type']]
    off = bv.get('byteOffset', 0) + a.get('byteOffset', 0); stride = bv.get('byteStride', dt.itemsize * n)
    raw = np.frombuffer(BIN, np.uint8, count=stride * (a['count'] - 1) + dt.itemsize * n, offset=off)
    arr = np.lib.stride_tricks.as_strided(raw, (a['count'], dt.itemsize * n), (stride, 1)).copy().view(dt).reshape(a['count'], n)
    if a.get('normalized'):
        arr = arr.astype(np.float32) / {np.uint16: 65535, np.uint8: 255, np.int16: 32767, np.int8: 127}[CT[a['componentType']]]
    return arr

prim = J['meshes'][0]['primitives'][0]; at = prim['attributes']
P = acc(at['POSITION']).astype(np.float32); N = acc(at['NORMAL']).astype(np.float32); UV = acc(at['TEXCOORD_0']).astype(np.float32)
I = acc(prim['indices']).reshape(-1).astype(np.uint32)
JI = WT = None
if A.bird or A.crow or A.ringneck or A.cockatiel or A.perch:
    JI = acc(at['JOINTS_0']).astype(np.int32); WT = acc(at['WEIGHTS_0']).astype(np.float32)

# ---- optional simplification ----
if A.tris and len(I) // 3 > A.tris:
    attrs = np.hstack([N, UV]).astype(np.float32)
    wts = np.array([0.5, 0.5, 0.5, 2.0, 2.0], np.float32)
    dst = np.zeros(len(I), np.uint32)
    import ctypes
    from meshoptimizer.simplifier import lib
    fp = lambda a: a.ctypes.data_as(ctypes.POINTER(ctypes.c_float))
    I = np.ascontiguousarray(I, np.uint32); P = np.ascontiguousarray(P, np.float32); attrs = np.ascontiguousarray(attrs)
    lib.meshopt_simplifyWithAttributes.restype = ctypes.c_size_t
    k = lib.meshopt_simplifyWithAttributes(dst.ctypes.data_as(ctypes.POINTER(ctypes.c_uint)), I.ctypes.data_as(ctypes.POINTER(ctypes.c_uint)),
        ctypes.c_size_t(len(I)), fp(P), ctypes.c_size_t(len(P)), ctypes.c_size_t(12), fp(attrs), ctypes.c_size_t(20), fp(wts), ctypes.c_size_t(5),
        ctypes.POINTER(ctypes.c_ubyte)(), ctypes.c_size_t(A.tris * 3), ctypes.c_float(0.02), ctypes.c_uint(0), ctypes.POINTER(ctypes.c_float)())
    I = dst[:k]
    used = np.unique(I); remap = np.full(len(P), -1, np.int64); remap[used] = np.arange(len(used))
    P, N, UV = P[used], N[used], UV[used]
    if JI is not None: JI, WT = JI[used], WT[used]
    I = remap[I].astype(np.uint32)
    print('simplified to', len(I) // 3, 'tris', len(P), 'verts')

# ---- recentre and scale ----
mn, mx = P.min(0), P.max(0); c = (mn + mx) / 2; P = P - c
fit = 1.0
if A.fit: fit = A.fit / np.abs(P).max(); P = P * fit
mn, mx = P.min(0), P.max(0)
N /= np.maximum(np.linalg.norm(N, axis=1, keepdims=True), 1e-8)

# ---- tangents ----
T = np.zeros_like(P); B = np.zeros_like(P); tri = I.reshape(-1, 3)
p0, p1, p2 = P[tri[:, 0]], P[tri[:, 1]], P[tri[:, 2]]; u0, u1, u2 = UV[tri[:, 0]], UV[tri[:, 1]], UV[tri[:, 2]]
e1, e2 = p1 - p0, p2 - p0; d1, d2 = u1 - u0, u2 - u0
r = d1[:, 0] * d2[:, 1] - d2[:, 0] * d1[:, 1]; r = np.where(np.abs(r) < 1e-12, 1e-12, r)
t = (e1 * d2[:, 1:2] - e2 * d1[:, 1:2]) / r[:, None]; bt = (e2 * d1[:, 0:1] - e1 * d2[:, 0:1]) / r[:, None]
for k in range(3): np.add.at(T, tri[:, k], t); np.add.at(B, tri[:, k], bt)
T = T - N * np.sum(N * T, 1, keepdims=True); T /= np.maximum(np.linalg.norm(T, axis=1, keepdims=True), 1e-8)
w = np.where(np.sum(np.cross(N, T) * B, 1) < 0, -1.0, 1.0)

S = np.maximum(np.abs(mn), np.abs(mx))
q8 = lambda v: np.round(np.clip(v, -1, 1) * 127).astype(np.int8)
nv = len(P)
fields = [('p', '<i2', 3), ('pad', '<u2'), ('n', 'i1', 4), ('t', 'i1', 4), ('uv', '<u2', 2)]
bones = None
if A.bird or A.crow or A.ringneck or A.cockatiel or A.perch:
    # ---- reduced bird skeleton: each original joint folds into one of these ----
    skin = J['skins'][0]; joints = skin['joints']
    names = [J['nodes'][j]['name'] for j in joints]
    ibm = acc(skin['inverseBindMatrices']).reshape(-1, 4, 4)
    heads = np.array([np.linalg.inv(m.T)[:3, 3] for m in ibm], np.float32)
    heads = (heads - c) * fit
    def nums(*ids): return ['Bone_%03d' % i for i in ids]
    R = [  # (name, parent index in this list, original bones folded in; the first one gives the pivot)
        ('body', -1, nums(0, 1, 3, 2, 7, 6, 9, 8)),
        ('tail1', 0, nums(26)), ('tail2', 1, nums(25)), ('tail3', 2, nums(24)), ('tail4', 3, nums(23, 22)),
        ('chest', 0, nums(5, 4)), ('neck1', 5, nums(33)), ('neck2', 6, nums(32)),
        ('head', 7, nums(31, 47, 46, 52, 51, 50, 49, 48)),
        ('shL', 5, nums(28, 27)), ('armL', 9, nums(36)), ('foreL', 10, nums(35)), ('handL', 11, nums(34, 55, 54, 53)),
        ('tipL', 12, nums(57, 56, 64, 63, 66, 65, 68, 67)), ('innerL', 9, nums(39, 38, 37)),
        ('shR', 5, nums(30, 29)), ('armR', 15, nums(42)), ('foreR', 16, nums(41)), ('handR', 17, nums(40, 60, 59, 58)),
        ('tipR', 18, nums(62, 61, 70, 69, 72, 71, 74, 73)), ('innerR', 15, nums(45, 44, 43)),
        ('legL', 0, nums(15, 14, 13, 12, 11, 10)), ('legR', 0, nums(21, 20, 19, 18, 17, 16)),
    ]
    if A.crow:  # a perched bird: no wing bones (the wings stay folded), a jaw for the caw
        R = [
            ('body', -1, nums(0, 1)), ('spine', 0, nums(4)), ('chest', 1, nums(3, 2)), ('breast', 2, nums(23, 22)),
            ('neck1', 2, nums(21)), ('neck2', 4, nums(20)), ('head', 5, nums(19)), ('jaw', 6, nums(18, 17)),
            ('tail1', 0, nums(16)), ('tail2', 8, nums(15)), ('tail3', 9, nums(14, 13)),
            ('thighL', 0, nums(8)), ('shinL', 11, nums(7)), ('footL', 12, nums(6, 5, 27, 26, 25, 24, 31, 30, 29, 28, 35, 34, 33, 32)),
            ('thighR', 0, nums(12)), ('shinR', 14, nums(11)), ('footR', 15, nums(10, 9, 39, 38, 37, 36, 43, 42, 41, 40, 47, 46, 45, 44)),
        ]
    if A.ringneck:  # SmartRig biped bird: same 23 bird bones; '@Bone_x' = pivot only (no skin of its own)
        R = [
            ('body', -1, nums(0)),
            ('tail1', 0, nums(1)), ('tail2', 1, nums(3)), ('tail3', 2, nums(2)), ('tail4', 3, ['@Bone_002']),
            ('chest', 0, nums(12)), ('neck1', 5, nums(11)), ('neck2', 6, ['@Bone_010']),
            ('head', 7, nums(10, 24, 23)),
            ('shL', 5, nums(28)), ('armL', 9, nums(27)), ('foreL', 10, nums(26)), ('handL', 11, nums(25)),
            ('tipL', 12, ['@Bone_025']), ('innerL', 9, ['@Bone_028']),
            ('shR', 5, nums(32)), ('armR', 15, nums(31)), ('foreR', 16, nums(30)), ('handR', 17, nums(29)),
            ('tipR', 18, ['@Bone_029']), ('innerR', 15, ['@Bone_032']),
            ('legL', 0, nums(6, 5, 4, 14, 13, 17, 16, 15)), ('legR', 0, nums(9, 8, 7, 19, 18, 22, 21, 20)),
        ]
    if A.cockatiel:  # 21 bones: the tail is two halves (L/R), no leg bones; '@mean' = pivot at the mean of the members
        R = [
            ('body', -1, nums(0)),
            ('tail1', 0, ['@mean'] + nums(1, 3, 5)), ('tail2', 1, ['@mean'] + nums(2, 4)), ('tail3', 2, ['@Bone_002']), ('tail4', 3, ['@Bone_002']),
            ('chest', 0, nums(8, 7)), ('neck1', 5, nums(6)), ('neck2', 6, ['@Bone_010']),
            ('head', 7, nums(10, 9)),
            ('shL', 5, nums(15)), ('armL', 9, nums(14)), ('foreL', 10, nums(13)), ('handL', 11, nums(12)),
            ('tipL', 12, nums(11)), ('innerL', 9, ['@Bone_015']),
            ('shR', 5, nums(20)), ('armR', 15, nums(19)), ('foreR', 16, nums(18)), ('handR', 17, nums(17)),
            ('tipR', 18, nums(16)), ('innerR', 15, ['@Bone_020']),
            ('legL', 0, ['@Bone_000']), ('legR', 0, ['@Bone_000']),
        ]
    # Perched birds use the crow's 17-bone layout (body, spine, chest, breast, neck1, neck2, head, jaw, tail1-3, legs).
    if A.perch == 'tiel':      # Meshy cockatiel sitting (55 bones: toes and crest folded into feet and head)
        R = [
            ('body', -1, nums(1, 0)), ('spine', 0, nums(14)), ('chest', 1, nums(13)), ('breast', 2, ['@Bone_013']),
            ('neck1', 2, nums(12)), ('neck2', 4, nums(11)), ('head', 5, nums(10, 44, 43, 50, 49, 48, 47, 54, 53, 52, 51)), ('jaw', 6, nums(46, 45)),
            ('tail1', 0, nums(18)), ('tail2', 8, nums(17)), ('tail3', 9, nums(16, 15)),
            ('thighL', 0, nums(9)), ('shinL', 11, nums(8)), ('footL', 12, nums(7, 6, *range(31, 43))),
            ('thighR', 0, nums(5)), ('shinR', 14, nums(4)), ('footR', 15, nums(3, 2, *range(19, 31))),
        ]
    elif A.perch == 'budgie':  # Meshy budgie (14 bones, no jaw: the jaw is a still pivot)
        R = [
            ('body', -1, nums(1, 0)), ('spine', 0, ['@Bone_001']), ('chest', 1, ['@Bone_010']), ('breast', 2, ['@Bone_010']),
            ('neck1', 2, nums(10)), ('neck2', 4, nums(9)), ('head', 5, nums(8)), ('jaw', 6, ['@Bone_008']),
            ('tail1', 0, nums(13)), ('tail2', 8, nums(12)), ('tail3', 9, nums(11)),
            ('thighL', 0, nums(7)), ('shinL', 11, nums(6)), ('footL', 12, nums(5)),
            ('thighR', 0, nums(4)), ('shinR', 14, nums(3)), ('footR', 15, nums(2)),
        ]
    fold = np.full(len(joints), -1, np.int32); pivots = []
    for ri, (nm, par, members) in enumerate(R):
        real = [m for m in members if not m.startswith('@')]
        for m in real: fold[names.index(m)] = ri
        if members[0] == '@mean': pivots.append(np.mean([heads[names.index(m)] for m in real], axis=0))
        else: pivots.append(heads[names.index(members[0].lstrip('@'))])
    assert (fold >= 0).all(), [names[i] for i in np.where(fold < 0)[0]]
    nb = len(R)
    acc_w = np.zeros((nv, nb), np.float32)
    for k in range(4): np.add.at(acc_w, (np.arange(nv), fold[JI[:, k]]), WT[:, k])
    top = np.argsort(-acc_w, 1)[:, :4]; tw = np.take_along_axis(acc_w, top, 1)
    tw /= np.maximum(tw.sum(1, keepdims=True), 1e-8)
    qw = np.round(tw * 255).astype(np.int32); qw[:, 0] += 255 - qw.sum(1)
    bones = (R, np.array(pivots, np.float32), top.astype(np.uint8), np.clip(qw, 0, 255).astype(np.uint8))
    fields += [('j', 'u1', 4), ('w', 'u1', 4)]

rec = np.zeros(nv, dtype=fields)
rec['p'] = np.round(P / S * 32767).astype(np.int16); rec['n'][:, :3] = q8(N); rec['t'][:, :3] = q8(T)
rec['t'][:, 3] = w.astype(np.int8) * 127; rec['uv'] = np.round(np.clip(UV, 0, 1) * 65535).astype(np.uint16)
with open(f'{A.outdir}/{A.name}.sta', 'wb') as f:
    f.write(b'STA2' if bones else b'STA1'); f.write(struct.pack('<II', nv, len(I)))
    f.write(struct.pack('<3f', *(-S))); f.write(struct.pack('<3f', *S))
    if bones:
        R, piv, top, qw = bones
        f.write(struct.pack('<I', len(R)))
        for (nm, par, _), h in zip(R, piv): f.write(struct.pack('<i3f', par, *h))
        rec['j'] = top; rec['w'] = qw
    f.write(rec.tobytes()); f.write(I.astype('<u4').tobytes())

mat = J['materials'][0]; pbr = mat['pbrMetallicRoughness']
def img(ti, out, size, q):
    tx = J['textures'][ti]; im = J['images'][tx['source']]; bv = J['bufferViews'][im['bufferView']]
    data = BIN[bv.get('byteOffset', 0): bv.get('byteOffset', 0) + bv['byteLength']]
    Image.open(io.BytesIO(data)).convert('RGB').resize((size, size), Image.LANCZOS).save(f'{A.outdir}/{A.name}_{out}.webp', 'WEBP', quality=q, method=6)
img(pbr['baseColorTexture']['index'], 'c', A.tex, 90)
img(mat['normalTexture']['index'], 'n', A.tex, 92)
img(pbr['metallicRoughnessTexture']['index'], 'mr', 1024, 90)
print(A.name, nv, len(I) // 3, 'tris; half extents', S, 'fit', fit)
if bones:
    for i, ((nm, par, _), h) in enumerate(zip(bones[0], bones[1])): print(i, nm, par, np.round(h, 3))
