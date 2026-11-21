"""Snowy v2: merge leaf helper bones into their parents (bone budget), append the duck props from
pulsify_props.blend, one deform bone each (parent root). Saves duck2.props.blend."""
import bpy
from mathutils import Vector, Matrix
OUT = '/home/claude/duck2'
bpy.ops.wm.open_mainfile(filepath=f'{OUT}/duck2.rig.blend')
rig = bpy.data.objects['Rig']; body = bpy.data.objects['Body']
MERGE = {'mixamorig:HeadTop_End': 'mixamorig:Head', 'headfront': 'mixamorig:Head',
         'mixamorig:LeftToe_End': 'mixamorig:LeftToeBase', 'mixamorig:RightToe_End': 'mixamorig:RightToeBase'}
vg = body.vertex_groups
for src, dst in MERGE.items():
    gs = vg.get(src)
    if not gs: continue
    gd = vg.get(dst) or vg.new(name=dst)
    for v in body.data.vertices:
        for g in v.groups:
            if g.group == gs.index and g.weight > 0:
                w0 = 0.0
                for g2 in v.groups:
                    if g2.group == gd.index: w0 = g2.weight
                gd.add([v.index], w0 + g.weight, 'REPLACE')
    vg.remove(gs)
    rig.data.bones[src].use_deform = False
PROPS = ['Bread', 'Crumb0', 'Crumb1', 'Crumb2', 'Puddle', 'Drops', 'RubberDuck', 'Heart', 'Z0', 'Z1', 'Z2', 'Butterfly', 'WingL', 'WingR', 'Feather']
CHILD_OF = {'WingL': 'Butterfly', 'WingR': 'Butterfly'}
with bpy.data.libraries.load('/home/claude/glb/props/pulsify_props.blend', link=False) as (src, dst):
    dst.objects = [n for n in src.objects if n in PROPS]
objs = {o.name.split('.')[0]: o for o in dst.objects}
for o in objs.values(): bpy.context.scene.collection.objects.link(o)
world = {n: o.matrix_world.copy() for n, o in objs.items()}
for n, o in objs.items():
    o.parent = None; o.data.transform(world[n]); o.matrix_world = Matrix()
bpy.context.view_layer.objects.active = rig
for o in bpy.context.scene.objects: o.select_set(False)
rig.select_set(True)
bpy.ops.object.mode_set(mode='EDIT'); eb = rig.data.edit_bones
cent = {}
for n, o in objs.items():
    vs = [v.co for v in o.data.vertices]; c = sum(vs, Vector()) / len(vs); cent[n] = c
for n in objs:
    par = 'p.' + CHILD_OF[n] if n in CHILD_OF else 'root'
    if n in CHILD_OF: continue
    b = eb.new('p.' + n); b.head = cent[n]; b.tail = cent[n] + Vector((0, 0.06, 0)); b.parent = eb['root']; b.use_deform = True
for n, p in CHILD_OF.items():
    c = cent[p]; b = eb.new('p.' + n); b.head = c; b.tail = c + Vector((0, 0.05, 0)); b.parent = eb['p.' + p]; b.use_deform = True
bpy.ops.object.mode_set(mode='OBJECT')
for n, o in objs.items():
    g = o.vertex_groups.new(name='p.' + n); g.add(range(len(o.data.vertices)), 1.0, 'REPLACE')
    o.parent = rig; md = o.modifiers.new('Armature', 'ARMATURE'); md.object = rig
    o.name = 'Prop_' + n
    print('PROP', n, [round(x, 3) for x in cent[n]], [round(x, 3) for x in o.dimensions], len(o.data.vertices))
skin = [b.name for b in rig.data.bones if b.use_deform]
print('DEFORM', len(skin))
bpy.ops.wm.save_as_mainfile(filepath=f'{OUT}/duck2.props.blend')
