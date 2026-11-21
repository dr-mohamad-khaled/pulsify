"""Merge tiny helper bones into neighbours (bone budget) and append props, one deform bone each under 'mover'.
python3 attach_generic.py -- in.blend out.blend '{"src":"dst",...}' 'Prop1,Prop2,...'"""
import bpy, sys, json
from mathutils import Vector, Matrix
a = sys.argv[sys.argv.index('--') + 1:]
src_blend, out_blend, MERGE, PROPS = a[0], a[1], json.loads(a[2]), a[3].split(',')
bpy.ops.wm.open_mainfile(filepath=src_blend)
rig = bpy.data.objects['Rig']; body = bpy.data.objects['Body']; vg = body.vertex_groups
for src, dst in MERGE.items():
    gs = vg.get(src)
    if not gs: continue
    gd = vg.get(dst) or vg.new(name=dst)
    for v in body.data.vertices:
        w = next((g.weight for g in v.groups if g.group == gs.index), 0.0)
        if w <= 0: continue
        w0 = next((g.weight for g in v.groups if g.group == gd.index), 0.0)
        gd.add([v.index], w0 + w, 'REPLACE')
    vg.remove(gs)
CHILD_OF = {'WingL': 'Butterfly', 'WingR': 'Butterfly'}
with bpy.data.libraries.load('/home/claude/glb/props/pulsify_props.blend', link=False) as (s, d):
    d.objects = [n for n in s.objects if n in PROPS]
objs = {o.name.split('.')[0]: o for o in d.objects}
for o in objs.values(): bpy.context.scene.collection.objects.link(o)
world = {n: o.matrix_world.copy() for n, o in objs.items()}
for n, o in objs.items():
    o.parent = None; o.data.transform(world[n]); o.matrix_world = Matrix()
bpy.context.view_layer.objects.active = rig
for o in bpy.context.scene.objects: o.select_set(False)
rig.select_set(True)
bpy.ops.object.mode_set(mode='EDIT'); eb = rig.data.edit_bones
cent = {n: sum((v.co for v in o.data.vertices), Vector()) / len(o.data.vertices) for n, o in objs.items()}
for n in objs:
    if n in CHILD_OF: continue
    b = eb.new('p.' + n); b.head = cent[n]; b.tail = cent[n] + Vector((0, 0.06, 0)); b.parent = eb['mover']; b.use_deform = True
for n, p in CHILD_OF.items():
    if n not in objs: continue
    c = cent[p]; b = eb.new('p.' + n); b.head = c; b.tail = c + Vector((0, 0.05, 0)); b.parent = eb['p.' + p]; b.use_deform = True
bpy.ops.object.mode_set(mode='OBJECT')
for n, o in objs.items():
    g = o.vertex_groups.new(name='p.' + n); g.add(range(len(o.data.vertices)), 1.0, 'REPLACE')
    o.parent = rig; md = o.modifiers.new('Armature', 'ARMATURE'); md.object = rig; o.name = 'Prop_' + n
    print('PROP', n, [round(x, 3) for x in o.dimensions])
used = set(g.name for o in bpy.data.objects if o.type == 'MESH' for g in o.vertex_groups)
print('SKIN', len([b for b in rig.data.bones if b.use_deform and b.name in used]))
bpy.ops.wm.save_as_mainfile(filepath=out_blend)
