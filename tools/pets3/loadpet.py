"""Shared: import a Meshy FBX whose textures were copied as tex*.png next to it, relink them."""
import bpy, os
def load(work, fbx='anim.fbx'):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    bpy.ops.import_scene.fbx(filepath=os.path.join(work, fbx), automatic_bone_orientation=False)
    rig = next(o for o in bpy.context.scene.objects if o.type == 'ARMATURE')
    me = next(o for o in bpy.context.scene.objects if o.type == 'MESH')
    roles = {'tex.png': 'Base Color', 'tex_roughness.png': 'Roughness', 'tex_metallic.png': 'Metallic', 'tex_normal.png': 'Normal'}
    mat = me.active_material; nt = mat.node_tree
    b = nt.nodes.get('Principled BSDF')
    for n in list(nt.nodes):
        if n.type == 'TEX_IMAGE': nt.nodes.remove(n)
    for fn, sock in roles.items():
        t = nt.nodes.new('ShaderNodeTexImage'); t.image = bpy.data.images.load(os.path.join(work, fn)); t.name = fn
        if sock == 'Base Color': nt.links.new(t.outputs['Color'], b.inputs['Base Color'])
        elif sock == 'Normal':
            t.image.colorspace_settings.name = 'Non-Color'
            nm = nt.nodes.new('ShaderNodeNormalMap'); nt.links.new(t.outputs['Color'], nm.inputs['Color']); nt.links.new(nm.outputs['Normal'], b.inputs['Normal'])
        else:
            t.image.colorspace_settings.name = 'Non-Color'; nt.links.new(t.outputs['Color'], b.inputs[sock])
    return rig, me
