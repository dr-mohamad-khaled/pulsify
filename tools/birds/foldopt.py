"""Find each wing's fold rotation (about the shoulder) so the rigid wing hugs the torso: python3 foldopt.py <bird>
Writes <bird>/fold.json {L:[w,x,y,z], R:[...]} (armature-space quaternions)."""
import sys, json, numpy as np, bpy
sys.path.insert(0, '/home/claude/birds/scripts')
from birdcfg import BIRDS
from mathutils import Quaternion, Vector
key = sys.argv[-1]; C = BIRDS[key]
bpy.ops.wm.open_mainfile(filepath=f'/home/claude/birds/{key}/{key}.rig.blend')
body = bpy.data.objects['Body']; rig = bpy.data.objects['Rig']; me = body.data
gname = {g.index: g.name for g in body.vertex_groups}
co = np.array([v.co[:] for v in me.vertices]); dom = []
for v in me.vertices:
    dom.append(gname[max(v.groups, key=lambda q: q.weight).group] if v.groups else '')
dom = np.array(dom)
torso_b = [C['root'], C['body'], C['chest']] + (C['tail'][:1])
T = co[np.isin(dom, torso_b)]
cen = np.array([0.0, *np.percentile(T[:, 1:], 50, axis=0)])
rad = np.array([np.percentile(np.abs(T[:, 0]), 97), *(np.percentile(T[:, 1:], 97, axis=0) - np.percentile(T[:, 1:], 3, axis=0)) / 2])
print('TORSO', cen.round(2), rad.round(2))
rng = np.random.default_rng(1)
def rand_quats(n):
    u = rng.random((n, 3))
    return np.stack([np.sqrt(1 - u[:, 0]) * np.sin(2 * np.pi * u[:, 1]), np.sqrt(1 - u[:, 0]) * np.cos(2 * np.pi * u[:, 1]),
                     np.sqrt(u[:, 0]) * np.sin(2 * np.pi * u[:, 2]), np.sqrt(u[:, 0]) * np.cos(2 * np.pi * u[:, 2])], 1)  # x y z w
def rotmats(q):
    x, y, z, w = q.T
    return np.stack([np.stack([1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)], -1),
                     np.stack([2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)], -1),
                     np.stack([2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)], -1)], 1)
out = {}
for side, sx in (('L', 1), ('R', -1)):
    ch = C['wing' + side]; h = np.array(rig.data.bones[ch[0]].head_local[:])
    Wv = co[np.isin(dom, ch)]; Wv = Wv[rng.choice(len(Wv), min(700, len(Wv)), replace=False)] - h
    ztop = cen[2] + rad[2] * 0.85
    A = co[np.isin(dom, ch)] - h; Ac = A - A.mean(0); _, V = np.linalg.eigh(Ac.T @ Ac); span = V[:, 2]
    if span @ A.mean(0) < 0: span = -span
    print('WINGAXIS', side, span.round(2))
    tgt = np.array([0.0, 0.86, -0.5]); tgt /= np.linalg.norm(tgt)
    WT = float(sys.argv[-2]) if len(sys.argv) > 2 else 1.5
    def cost(R):
        P = np.einsum('nij,mj->nmi', R, Wv) + h             # (n, m, 3)
        d = (P - cen) / rad; r = np.linalg.norm(d, axis=-1)
        inside = np.clip(1.0 - r, 0, None); outside = np.clip(r - 1.0, 0, None)
        c = 6 * (inside ** 2).mean(1) + (outside ** 2).mean(1)
        c += 4 * (np.clip(P[..., 2] - ztop, 0, None) ** 2).mean(1)            # not above the back
        c += 3 * (np.clip(h[1] - 0.05 - P[..., 1], 0, None) ** 2).mean(1)     # nothing forward of the shoulder
        c += 3 * (np.clip(-P[..., 2] + 0.12, 0, None) ** 2).mean(1)           # not through the floor / legs
        c += 2 * (np.clip(-sx * P[..., 0], 0, None) ** 2).mean(1)             # stays on its own side
        c += WT * (1.0 - np.einsum('nij,j,i->n', R, span, tgt)) ** 2              # tips point back along the body
        return c
    best = None
    Q = rand_quats(20000)
    for i in range(0, len(Q), 2000):
        cs = cost(rotmats(Q[i:i + 2000])); j = cs.argmin()
        if best is None or cs[j] < best[0]: best = (cs[j], Q[i + j])
    for it in range(6):                                        # local refinement
        sig = 0.15 / (it + 1)
        cand = best[1] + rng.normal(0, sig, (3000, 4)); cand /= np.linalg.norm(cand, axis=1, keepdims=True)
        cs = cost(rotmats(cand)); j = cs.argmin()
        if cs[j] < best[0]: best = (cs[j], cand[j])
    x, y, z, w = best[1]; q = Quaternion((w, x, y, z))
    out[side] = list(q); print('FOLD', side, round(float(best[0]), 4), [round(a, 3) for a in q])
json.dump(out, open(f'/home/claude/birds/{key}/fold.json', 'w'))
