import bpy, sys
sys.path.insert(0, '/home/claude/duck2/scripts')
from engine2 import Engine
from mathutils import Vector
from PIL import Image, ImageDraw
E = Engine('/home/claude/nut/nut.props.blend')
sc = bpy.context.scene; sc.render.engine = 'BLENDER_WORKBENCH'; sc.display.shading.light = 'STUDIO'; sc.display.shading.color_type = 'TEXTURE'
S = 240; sc.render.resolution_x = sc.render.resolution_y = S; sc.render.film_transparent = True
cam = bpy.data.objects.new('c', bpy.data.cameras.new('c')); sc.collection.objects.link(cam); sc.camera = cam; cam.data.type = 'ORTHO'; cam.data.ortho_scale = 1.6
d = Vector((1, -0.6, 0.3)).normalized(); cam.location = Vector((0, 0, 0.45)) + d * 10; cam.rotation_euler = d.to_track_quat('Z', 'Y').to_euler()
for pb in E.rig.pose.bones: pb.rotation_mode = 'QUATERNION'
acts = list(E.curves); NF = 6
sheet = Image.new('RGB', (S * NF, S * len(acts)), (235, 235, 240)); dr = ImageDraw.Draw(sheet)
for r, a in enumerate(acts):
    s, e = E.frames(a)
    for i in range(NF):
        P = E.sample(a, s + (e - s) * i / (NF - 1)); E.apply_to_rig(P); bpy.context.view_layer.update()
        sc.render.filepath = '/tmp/_m.png'; bpy.ops.render.render(write_still=True)
        im = Image.open('/tmp/_m.png'); sheet.paste(im, (i * S, r * S), im)
    dr.text((4, r * S + 4), a, fill=(0, 0, 0))
sheet.save('/tmp/nut_meshy.png')
