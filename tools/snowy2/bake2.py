"""Bake every Snowy v2 clip to /home/claude/duck2/baked/<clip>.npz (30 fps): bone bases (preview),
skin matrices (PET export) and face controls."""
import sys, os, json, numpy as np
sys.path.insert(0, '/home/claude/duck2/scripts')
import bpy
from mathutils import Vector
from engine2 import Engine, FPS, rq
from clips2 import library, Ctx, H

OUT = '/home/claude/duck2/baked'
os.makedirs(OUT, exist_ok=True)
E = Engine()
only = sys.argv[sys.argv.index('--') + 1].split(',') if '--' in sys.argv and len(sys.argv) > sys.argv.index('--') + 1 else None
PROPS = [n[2:] for n in E.names if n.startswith('p.') and n[2:] not in ('WingL', 'WingR')]
meshes = [o for o in bpy.data.objects if o.type == 'MESH']
used = set(g.name for o in meshes for g in o.vertex_groups)
SKIN = [n for n in E.names if E.rig.data.bones[n].use_deform and n in used]
json.dump({'skin': SKIN, 'names': E.names}, open(f'{OUT}/_bones.json', 'w'))
print('SKIN', len(SKIN))
TAIL = ['Bone_013', 'Bone_012']; TIPS = ['Bone_022', 'Bone_023']


def bake(c):
    if hasattr(c, 'setup') and hasattr(c, '_src'): c.setup(E)
    E.reset_springs()
    n = c.n
    rec = []
    off0 = None
    for p in range(2 if c.loop else 1):
        rec = []
        for i in range(n):
            t = i / FPS
            P = c.base(E, t)
            M = E.fk(P); hips = M[H].translation
            if c.lock == 'lock' or off0 is None:
                off = Vector((-hips.x, -(hips.y + 0.04), 0.0))
                if off0 is None: off0 = off
            else: off = off0
            c.off = off
            ctx = Ctx(M); c.act(t, ctx)
            face = c.face.eval(t)
            P['root'][0] = P['root'][0] + off + ctx.root
            s = ctx.squash
            if abs(s) > 1e-5: P['root'][2] = Vector((1 + s * 0.5, 1 + s * 0.5, 1 - s))
            if ctx.spin: P['root'][1] = rq('Z', ctx.spin) @ P['root'][1]
            if ctx.turns:
                M = E.fk(P); E.turn(P, M, ctx.turns)
            jaw = max(0.0, min(1.2, face['jaw'] + ctx.jaw))
            P['jaw'][1] = rq('X', 18 * jaw)
            if c.springs:
                E.spring_step(P, TAIL, 'tail', k=60.0, damp=6.0)
                E.spring_step(P, [TIPS[0]], 'tipL', k=140.0, damp=9.0)
                E.spring_step(P, [TIPS[1]], 'tipR', k=140.0, damp=9.0)
            M = E.fk(P)
            pr = c.props(t, M)
            for nm in PROPS: E.place_prop(P, M, nm, pr.get(nm))
            wf = pr.get('_wings', 0.0)
            P['p.WingL'][1] = rq('Y', wf); P['p.WingR'][1] = rq('Y', -wf)
            M = E.fk(P)
            basis = np.array([[*P[nm][0], *P[nm][1], *P[nm][2]] for nm in E.names], np.float32)
            sk = np.array([[m[r][k] for r in range(3) for k in range(4)] for m in E.skin(M, SKIN)], np.float32)
            fv = np.array([face[k] for k in ('bL', 'bR', 'lx', 'ly', 'tilt', 'happy', 'pupil', 'wink')] + [jaw], np.float32)
            rec.append((basis, sk, fv))
    np.savez_compressed(f'{OUT}/{c.name}.npz', basis=np.stack([r[0] for r in rec]), skin=np.stack([r[1] for r in rec]),
                        face=np.stack([r[2] for r in rec]), loop=np.array(c.loop), group=np.array(c.group))
    print('BAKED', c.name, n, 'loop' if c.loop else '', c.group, flush=True)


lib = library()
order = []
for c in lib:
    if only and c.name not in only: continue
    bake(c); order.append([c.name, bool(c.loop), c.group])
if not only: json.dump(order, open(f'{OUT}/_clips.json', 'w'))
