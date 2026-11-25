import bpy, math
bpy.ops.wm.read_factory_settings(use_empty=True)
SRC='/tmp/astro_new/Meshy_AI_Astronaut_20261125_animated_mixamo_All_animation/Meshy_AI_Astronaut_20261125_animated_mixamo_All_animation'
bpy.ops.import_scene.fbx(filepath=SRC+'.fbx')
arm=[o for o in bpy.data.objects if o.type=='ARMATURE'][0]
mesh=[o for o in bpy.data.objects if o.type=='MESH'][0]
act=next(a for a in bpy.data.actions if a.name.split('|')[1]=='Fall2')
arm.animation_data.action=act
try: arm.animation_data.action_slot=act.slots[0]
except Exception: pass
sc=bpy.context.scene; sc.frame_set(8)
# texture material
mat=bpy.data.materials.new('m'); mat.use_nodes=True; nt=mat.node_tree; bsdf=nt.nodes['Principled BSDF']
tex=nt.nodes.new('ShaderNodeTexImage'); tex.image=bpy.data.images.load(SRC+'.png'); nt.links.new(tex.outputs[0],bsdf.inputs['Base Color'])
bsdf.inputs['Roughness'].default_value=0.45
mesh.data.materials.clear(); mesh.data.materials.append(mat)
bpy.context.view_layer.update()
import mathutils
dg=bpy.context.evaluated_depsgraph_get(); ev=mesh.evaluated_get(dg)
pts=[ev.matrix_world@v.co for v in ev.data.vertices]
mn=mathutils.Vector([min(p[i] for p in pts) for i in range(3)]); mx=mathutils.Vector([max(p[i] for p in pts) for i in range(3)])
c=(mn+mx)/2; size=max(mx-mn)
cam=bpy.data.objects.new('cam',bpy.data.cameras.new('cam')); sc.collection.objects.link(cam); sc.camera=cam
cam.data.lens=60
d=size*3.2
cam.location=c+mathutils.Vector((-0.35*d,-0.9*d,0.15*d)); 
cam.rotation_euler=(c-cam.location).to_track_quat('-Z','Y').to_euler()
def light(loc,e,col,sz=1):
    l=bpy.data.objects.new('l',bpy.data.lights.new('l','AREA')); l.data.energy=e; l.data.color=col; l.data.size=sz; l.location=loc
    l.rotation_euler=(c-l.location).to_track_quat('-Z','Y').to_euler(); sc.collection.objects.link(l)
light(c+mathutils.Vector((-3,-3,2)),900,(1,.92,.85),2)
light(c+mathutils.Vector((3,2,1.5)),1400,(1,.7,.42),1)   # amber rim
light(c+mathutils.Vector((-2,3,-1)),900,(.8,.7,1),1)     # lavender rim
sc.world=bpy.data.worlds.new('w'); sc.world.use_nodes=True; sc.world.node_tree.nodes['Background'].inputs[1].default_value=0.02
sc.render.engine='CYCLES'; sc.cycles.samples=96; sc.cycles.device='CPU'
sc.render.resolution_x=600; sc.render.resolution_y=800; sc.render.film_transparent=True
sc.render.filepath='/tmp/astro_new/card_fg.png'; bpy.ops.render.render(write_still=True)
