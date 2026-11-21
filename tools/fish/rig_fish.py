import bpy, json
bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.import_scene.gltf(filepath="/mnt/user-data/uploads/eb3c269e-Meshy_AI_Crimson_Veil_Betta_20261116_character.glb")
for o in list(bpy.data.objects):
    if o.type == 'MESH' and len(o.data.vertices) < 100: bpy.data.objects.remove(o)
rig = next(o for o in bpy.data.objects if o.type == 'ARMATURE'); body = next(o for o in bpy.data.objects if o.type == 'MESH')
rig.name = 'Rig'; body.name = 'Body'
bpy.context.view_layer.objects.active = rig
for o in bpy.data.objects: o.select_set(o in (rig, body))
bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
for n in body.active_material.node_tree.nodes:
    if n.type == 'BSDF_PRINCIPLED' and n.inputs['Base Color'].links: n.inputs['Base Color'].links[0].from_node.image.name = 'tex.png'
bpy.ops.object.mode_set(mode='EDIT'); eb = rig.data.edit_bones
mv = eb.new('mover'); mv.head = (0, 0, 0); mv.tail = (0, 0.12, 0); mv.use_deform = False
eb['Bone_000'].parent = mv
bpy.ops.object.mode_set(mode='OBJECT')
for pb in rig.pose.bones: pb.rotation_mode = 'QUATERNION'
json.dump({'eyes': [{'side': 'L', 'ioff': [0, 0]}, {'side': 'R', 'ioff': [0, 0]}], 'lid': [0, 0, 0], 'fur': [0, 0, 0]}, open('/home/claude/fish/face.json', 'w'))
bpy.ops.wm.save_as_mainfile(filepath='/home/claude/fish/betta.rig.blend'); print('SAVED')
