"""Generic bake: python3 bake3.py -- <clips module path> [Clip1,Clip2]
The clips module provides BLEND, OUT, MOVER, HIPS, HIPS_TARGET, SPRINGS, JAW, library()."""
import sys, os, json, importlib.util, numpy as np
sys.path.insert(0, '/home/claude/duck2/scripts')
import bpy
from mathutils import Vector
from engine2 import Engine, FPS, rq

args = sys.argv[sys.argv.index('--') + 1:]
spec = importlib.util.spec_from_file_location('clips', args[0]); C = importlib.util.module_from_spec(spec)
sys.path.insert(0, os.path.dirname(args[0])); spec.loader.exec_module(C)
only = args[1].split(',') if len(args) > 1 else None
OUT = C.OUT; os.makedirs(OUT, exist_ok=True)
E = Engine(C.BLEND)
C.E = E
PROPS = [n[2:] for n in E.names if n.startswith('p.') and n[2:] not in ('WingL', 'WingR')]
meshes = [o for o in bpy.data.objects if o.type == 'MESH']
used = set(g.name for o in meshes for g in o.vertex_groups)
SKIN = [n for n in E.names if E.rig.data.bones[n].use_deform and n in used]
json.dump({'skin': SKIN, 'names': E.names}, open(f'{OUT}/_bones.json', 'w'))
print('SKIN', len(SKIN))
MV = C.MOVER


def bake(c):
    if hasattr(c, 'setup'): c.setup(E)
    E.reset_springs()
    off0 = None
    for p in range(2 if c.loop else 1):
        rec = []
        for i in range(c.n):
            t = i / FPS
            P = c.base(E, t)
            M = E.fk(P); hips = M[C.HIPS].translation
            if c.lock == 'lock' or off0 is None:
                off = Vector((C.HIPS_TARGET[0] - hips.x, C.HIPS_TARGET[1] - hips.y, 0.0))
                if off0 is None: off0 = off
            else: off = off0
            c.off = off
            ctx = C.Ctx(M); c.act(t, ctx)
            face = c.face.eval(t)
            P[MV][0] = P[MV][0] + off + ctx.root
            s = ctx.squash
            if abs(s) > 1e-5: P[MV][2] = Vector((1 + s * 0.5, 1 + s * 0.5, 1 - s))
            if ctx.spin: P[MV][1] = rq('Z', ctx.spin) @ P[MV][1]
            if ctx.roll: P[MV][1] = P[MV][1] @ rq('Y', ctx.roll)
            jaw = max(0.0, min(1.2, face['jaw'] + ctx.jaw))
            if C.JAW: ctx.turn(C.JAW[0], C.JAW[1], C.JAW[2] * jaw)
            if ctx.turns:
                M = E.fk(P); E.turn(P, M, ctx.turns)
            for b, sc in getattr(ctx, 'scales', []):
                P[b][2] = Vector((P[b][2][0] * sc[0], P[b][2][1] * sc[1], P[b][2][2] * sc[2]))
            if c.springs:
                for chain, key, k, d in C.SPRINGS: E.spring_step(P, chain, key, k=k, damp=d)
            M = E.fk(P)
            pr = c.props(t, M)
            for nm in PROPS: E.place_prop(P, M, nm, pr.get(nm))
            if 'p.WingL' in P:
                wf = pr.get('_wings', 0.0); P['p.WingL'][1] = rq('Y', wf); P['p.WingR'][1] = rq('Y', -wf)
            M = E.fk(P)
            basis = np.array([[*P[nm][0], *P[nm][1], *P[nm][2]] for nm in E.names], np.float32)
            sk = np.array([[m[r][k] for r in range(3) for k in range(4)] for m in E.skin(M, SKIN)], np.float32)
            fv = np.array([face[k] for k in ('bL', 'bR', 'lx', 'ly', 'tilt', 'happy', 'pupil', 'wink')] + [jaw], np.float32)
            rec.append((basis, sk, fv))
    np.savez_compressed(f'{OUT}/{c.name}.npz', basis=np.stack([r[0] for r in rec]), skin=np.stack([r[1] for r in rec]),
                        face=np.stack([r[2] for r in rec]), loop=np.array(c.loop), group=np.array(c.group))
    print('BAKED', c.name, c.n, 'loop' if c.loop else '', flush=True)


order = []
for c in C.library():
    if only and c.name not in only: continue
    bake(c); order.append([c.name, bool(c.loop), c.group])
if not only: json.dump(order, open(f'{OUT}/_clips.json', 'w'))
