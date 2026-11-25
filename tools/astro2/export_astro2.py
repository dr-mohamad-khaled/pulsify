"""Exports the Meshy/Mixamo astronaut FBX to Screenpals PET3 (astro2): geometry, top-4 skin weights,
baked clips at 15 fps with the root motion removed (he floats in space, so falls happen in place)."""
import bpy, struct, os, sys
import numpy as np
from mathutils import Matrix, Vector

SRC = '/tmp/astro_new/Meshy_AI_Astronaut_20261125_animated_mixamo_All_animation/Meshy_AI_Astronaut_20261125_animated_mixamo_All_animation'
OUT = sys.argv[sys.argv.index('--') + 1]
NAME = 'astro2'
STEP = 2; NCTL = 12
# app clip <- FBX action, loop
CLIPS = [('Idle', 'Fall1', True), ('Flail', 'Fall2', True), ('Poke', 'Climb_Attempt_and_Fall_4', False), ('Impact', 'Climb_Attempt_and_Fall_1', False)]

bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.import_scene.fbx(filepath=SRC + '.fbx')
arm = [o for o in bpy.data.objects if o.type == 'ARMATURE'][0]
mesh = [o for o in bpy.data.objects if o.type == 'MESH'][0]
sc = bpy.context.scene

# rest pose for geometry
arm.animation_data.action = None
for pb in arm.pose.bones: pb.matrix_basis = Matrix.Identity(4)
for m in mesh.modifiers: m.show_viewport = False
bpy.context.view_layer.update()
me = mesh.data
MW = mesh.matrix_world.copy(); NW = MW.to_3x3().inverted().transposed()
gnames = {g.index: g.name for g in mesh.vertex_groups}

# bones that actually move skin; fingers fold into the hand so we stay within 40
used = set()
for v in me.vertices:
    for g in v.groups:
        if g.weight > 0.001 and gnames.get(g.group) in arm.data.bones: used.add(gnames[g.group])
def owner(n):
    b = arm.data.bones[n]
    if ('Hand' in n and n[-1].isdigit()) or n.startswith('Bone_0'):
        while b.parent and not b.name.endswith('Hand'): b = b.parent
    return b.name
SKIN = sorted({owner(n) for n in used}, key=lambda n: list(arm.data.bones.keys()).index(n))
print('BONES', len(SKIN), SKIN)
assert len(SKIN) <= 40
bidx = {n: i for i, n in enumerate(SKIN)}

me.calc_loop_triangles()
uv0 = me.uv_layers[0].data
co = np.array([tuple(MW @ v.co) for v in me.vertices], np.float32)
zmin = co[:, 2].min(); zmax = co[:, 2].max()
SCALE = 0.93 / (zmax - zmin)          # same size as the first Cosmo
OFF = Vector((0, 0, -zmin))
nrm = np.array([tuple((NW @ v.normal).normalized()) for v in me.vertices], np.float32)
ed = np.array([tuple(e.vertices) for e in me.edges], np.int64)
for _ in range(2):
    acc = nrm.copy(); np.add.at(acc, ed[:, 0], nrm[ed[:, 1]]); np.add.at(acc, ed[:, 1], nrm[ed[:, 0]])
    nrm = acc / np.maximum(1e-6, np.linalg.norm(acc, axis=1, keepdims=True))
vw = []
for v in me.vertices:
    d = {}
    for g in v.groups:
        n = gnames.get(g.group)
        if n in arm.data.bones and g.weight > 0.001:
            o = bidx[owner(n)]; d[o] = d.get(o, 0) + g.weight
    ws = sorted(d.items(), key=lambda a: -a[1])[:4]
    s = sum(w for _, w in ws) or 1.0
    ws = [(b, w / s) for b, w in ws] + [(0, 0.0)] * (4 - len(ws))
    q = [int(round(w * 255)) for _, w in ws]; q[0] += 255 - sum(q)
    vw.append(([b for b, _ in ws], q))
verts = []; vmap = {}; idx = []
for tri in me.loop_triangles:
    for li in tri.loops:
        vi = me.loops[li].vertex_index; uv = tuple(uv0[li].uv)
        key = (vi, round(uv[0], 5), round(uv[1], 5))
        if key not in vmap:
            p = (Vector(co[vi]) + OFF) * SCALE
            verts.append((tuple(p), tuple(nrm[vi]), uv, vw[vi][0], vw[vi][1])); vmap[key] = len(verts) - 1
        idx.append(vmap[key])
print('VERTS', len(verts), 'TRIS', len(idx) // 3)

# textures
from PIL import Image
Image.open(SRC + '.png').convert('RGB').save(os.path.join(OUT, f'{NAME}.jpg'), quality=92)
Image.open(SRC + '_normal.png').convert('RGB').resize((1024, 1024), Image.LANCZOS).save(os.path.join(OUT, f'{NAME}_n.jpg'), quality=92)

# clips: world-space skin = A * pose * rest^-1 * A^-1, then into model space (offset + scale), root motion removed
AW = arm.matrix_world.copy(); AWi = AW.inverted()
S = Matrix.Diagonal((SCALE, SCALE, SCALE, 1)) @ Matrix.Translation(OFF)
Si = S.inverted()
rest = {n: arm.data.bones[n].matrix_local.copy() for n in SKIN}
hips = 'mixamorig:Hips'
hip_rest = (AW @ arm.data.bones[hips].head_local)
blob = bytearray(); n = 0
for app, src, loop in CLIPS:
    act = next(a for a in bpy.data.actions if a.name.split('|')[1] == src)
    arm.animation_data.action = act
    try: arm.animation_data.action_slot = act.slots[0]
    except Exception: pass
    f0, f1 = int(act.frame_range[0]), int(act.frame_range[1])
    frames = list(range(f0, f1 + 1, STEP))
    if not loop and frames[-1] != f1: frames.append(f1)
    mats = []
    for f in frames:
        sc.frame_set(f)
        hp = AW @ arm.pose.bones[hips].head
        fix = Matrix.Translation(hip_rest - hp)
        for b in SKIN:
            M = S @ fix @ AW @ arm.pose.bones[b].matrix @ rest[b].inverted() @ AWi @ Si
            mats.extend([M[r][k] for r in range(3) for k in range(4)])
    mats = np.array(mats, np.float32).astype(np.float16)
    ctl = np.zeros((len(frames), NCTL), np.float16)
    nb = app.encode()
    blob += struct.pack('<B', len(nb)) + nb + struct.pack('<HB', len(frames), 1 if loop else 0) + mats.tobytes() + ctl.tobytes()
    n += 1; print('CLIP', app, src, len(frames))

body_h = 0.93
with open(os.path.join(OUT, f'{NAME}.pet'), 'wb') as f:
    f.write(b'PET3' + struct.pack('<IIIIffIII', len(verts), len(idx), len(SKIN), n, body_h, 30.0 / STEP, 0, NCTL, 1))
    f.write(struct.pack('<6f', 0, 0, 0, 0, 0, 0))
    for (p, nn, uv, bs, ws) in verts:
        f.write(struct.pack('<3f', *p))
        f.write(struct.pack('<3bB', *[int(max(-127, min(127, round(x * 127)))) for x in nn], 40))
        f.write(struct.pack('<2H', *[int(max(0, min(65535, round((x % 1.0 if x != 1.0 else 1.0) * 65535)))) for x in uv]))
        f.write(struct.pack('<2h', 32360, 32360))
        f.write(struct.pack('<4B', *bs)); f.write(struct.pack('<4B', *ws)); f.write(struct.pack('<4B', 0, 0, 0, 0))
        f.write(struct.pack('<2h', 32360, 32360))
    f.write(struct.pack(f'<{len(idx)}I', *idx))
    f.write(blob)
print('WROTE', os.path.getsize(os.path.join(OUT, f'{NAME}.pet')))
