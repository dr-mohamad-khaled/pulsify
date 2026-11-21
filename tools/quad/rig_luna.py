import bpy, sys, json
sys.path.insert(0, '/home/claude/husky/scripts')
from loadpet import load
rig, body = load('/home/claude/luna/work')
rig.animation_data.action = None
bpy.context.view_layer.objects.active = rig
for o in bpy.context.scene.objects: o.select_set(o in (rig, body))
bpy.ops.object.transform_apply(location=False, rotation=True, scale=True)
bpy.context.view_layer.update()
zmin = min(v.co.z for v in body.data.vertices)
rig.location.z -= zmin
bpy.ops.object.transform_apply(location=True, rotation=False, scale=False)
body.name = 'Body'; rig.name = 'Rig'
used = set()
for v in body.data.vertices:
    for g in v.groups:
        if g.weight > 0.001: used.add(body.vertex_groups[g.group].name)
for n in [g.name for g in body.vertex_groups if g.name not in used]: body.vertex_groups.remove(body.vertex_groups[n])
print('GROUPS', len(body.vertex_groups))
bpy.context.view_layer.objects.active = rig; bpy.ops.object.mode_set(mode='EDIT'); eb = rig.data.edit_bones
mv = eb.new('mover'); mv.head = (0, 0, 0); mv.tail = (0, 0.12, 0); mv.roll = 0; mv.use_deform = False
eb['root'].parent = mv
bpy.ops.object.mode_set(mode='OBJECT')
zs = [v.co.z for v in body.data.vertices]; print('H', max(zs), 'ZSHIFT', -zmin)
json.dump({'eyes': [{'side': 'L', 'ioff': [0, 0]}, {'side': 'R', 'ioff': [0, 0]}], 'lid': [0, 0, 0], 'fur': [0, 0, 0], 'zshift': float(-zmin)}, open('/home/claude/luna/face.json', 'w'))
bpy.ops.wm.save_as_mainfile(filepath='/home/claude/luna/luna.rig.blend'); print('SAVED')
