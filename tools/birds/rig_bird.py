"""python3 rig_bird.py <bird>: import the Meshy GLB, normalise (feet on z=0), merge helper bones, move the
wings onto the chest, take the body sides off the shoulder bones, add 'mover'. Saves <bird>.rig.blend."""
import bpy, sys, json, math
sys.path.insert(0, '/home/claude/birds/scripts')
from birdcfg import BIRDS
from mathutils import Vector
key = sys.argv[-1]; C = BIRDS[key]; OUT = f'/home/claude/birds/{key}'
import os; os.makedirs(OUT, exist_ok=True)
bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.import_scene.gltf(filepath=C['glb'])
for o in list(bpy.data.objects):
    if o.type == 'MESH' and len(o.data.vertices) < 100: bpy.data.objects.remove(o)
rig = next(o for o in bpy.data.objects if o.type == 'ARMATURE'); body = next(o for o in bpy.data.objects if o.type == 'MESH')
rig.name = 'Rig'; body.name = 'Body'
bpy.context.view_layer.objects.active = rig
for o in bpy.data.objects: o.select_set(o in (rig, body))
bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
zmin = min((body.matrix_world @ v.co).z for v in body.data.vertices)
rig.location.z -= zmin; bpy.context.view_layer.update()
bpy.ops.object.transform_apply(location=True, rotation=False, scale=False)
# base colour image -> tex.png
mat = body.active_material
for n in mat.node_tree.nodes:
    if n.type == 'BSDF_PRINCIPLED':
        l = n.inputs['Base Color'].links
        if l: l[0].from_node.image.name = 'tex.png'
assert 'tex.png' in bpy.data.images
vg = body.vertex_groups
def wt(v, g): return next((x.weight for x in v.groups if x.group == g.index), 0.0)
# 1) merge helper bones
for src, dst in C['merge'].items():
    gs = vg.get(src)
    if not gs: continue
    gd = vg.get(dst) or vg.new(name=dst)
    for v in body.data.vertices:
        w = wt(v, gs)
        if w > 0: gd.add([v.index], wt(v, gd) + w, 'REPLACE')
    vg.remove(gs)
# 2) shoulders keep only the wing itself: weight inside the body moves to the chest
arm = rig.data
for side in ('wingL', 'wingR'):
    sh = C[side][0]; hx = abs(arm.bones[sh].head_local.x)
    gs, gc = vg.get(sh), vg.get(C['chest']) or vg.new(name=C['chest'])
    moved = 0
    for v in body.data.vertices:
        w = wt(v, gs)
        if w <= 0: continue
        ax = abs(v.co.x)
        f = min(1.0, max(0.0, (ax - hx * 0.95) / (hx * 0.45 + 0.04))); f = f * f * (3 - 2 * f)
        if f < 1.0:
            gs.add([v.index], w * f, 'REPLACE'); gc.add([v.index], wt(v, gc) + w * (1 - f), 'REPLACE'); moved += 1
    print('SHOULDER', sh, 'softened', moved)
# drop empty / unused groups of deleted bones
bpy.ops.object.mode_set(mode='EDIT') if False else None
bpy.context.view_layer.objects.active = rig; bpy.ops.object.mode_set(mode='EDIT'); eb = arm.edit_bones
for side in ('wingL', 'wingR'): eb[C[side][0]].parent = eb[C['chest']]
for src in C['merge']:
    if src in eb: eb.remove(eb[src])
mv = eb.new('mover'); mv.head = (0, 0, 0); mv.tail = (0, 0.12, 0); mv.roll = 0; mv.use_deform = False
eb[C['root']].parent = mv
bpy.ops.object.mode_set(mode='OBJECT')
# all bones quaternion
for pb in rig.pose.bones: pb.rotation_mode = 'QUATERNION'
used = set(g.name for g in vg)
print('BONES', len(arm.bones), 'SKIN', len([b for b in arm.bones if b.use_deform and b.name in used]))
zs = [v.co.z for v in body.data.vertices]; print('H', round(max(zs), 3))
json.dump({'eyes': [{'side': 'L', 'ioff': [0, 0]}, {'side': 'R', 'ioff': [0, 0]}], 'lid': [0, 0, 0], 'fur': [0, 0, 0]}, open(f'{OUT}/face.json', 'w'))
bpy.ops.wm.save_as_mainfile(filepath=f'{OUT}/{key}.rig.blend')
print('SAVED')
