"""python3 sheet.py <bird> <out.png> Clip1,Clip2,... [frames=5]: contact sheet from baked npz (3/4 front view)."""
import bpy, sys, os, math, numpy as np
from mathutils import Vector, Quaternion
key, out, clips = sys.argv[-4:-1] if len(sys.argv) > 4 and sys.argv[-1].isdigit() else sys.argv[-3:]
NF = int(sys.argv[-1]) if sys.argv[-1].isdigit() else 5
clips = clips.split(',')
bpy.ops.wm.open_mainfile(filepath=f'/home/claude/nut/nut.props.blend')
rig = bpy.data.objects['Rig']; names = [b.name for b in rig.data.bones]
import json; bj = json.load(open(f'/home/claude/nut/baked/_bones.json')); order = bj['names']
for pb in rig.pose.bones: pb.rotation_mode = 'QUATERNION'
sc = bpy.context.scene; sc.render.engine = 'BLENDER_WORKBENCH'; sc.display.shading.light = 'STUDIO'; sc.display.shading.color_type = 'TEXTURE'
S = 260; sc.render.resolution_x = sc.render.resolution_y = S; sc.render.film_transparent = True
cam = bpy.data.objects.new('c', bpy.data.cameras.new('c')); sc.collection.objects.link(cam); sc.camera = cam; cam.data.type = 'ORTHO'; cam.data.ortho_scale = 1.7
d = Vector((1, -0.25, 0.15) if os.environ.get('VIEW') == 'side' else (0.55, -1, 0.3)).normalized(); cam.location = Vector((0, 0, 0.45)) + d * 10; cam.rotation_euler = d.to_track_quat('Z', 'Y').to_euler()
from PIL import Image, ImageDraw
sheet = Image.new('RGB', (S * NF, S * len(clips)), (235, 235, 240)); dr = ImageDraw.Draw(sheet)
for r, cn in enumerate(clips):
    D = np.load(f'/home/claude/nut/baked/{cn}.npz'); B = D['basis']; n = len(B)
    for i in range(NF):
        f = min(n - 1, int(round(i * (n - 1) / max(1, NF - 1))))
        for j, nm in enumerate(order):
            pb = rig.pose.bones[nm]; v = B[f, j]
            pb.location = v[0:3]; pb.rotation_quaternion = Quaternion(v[3:7]); pb.scale = v[7:10]
        bpy.context.view_layer.update()
        sc.render.filepath = '/tmp/_sh.png'; bpy.ops.render.render(write_still=True)
        im = Image.open('/tmp/_sh.png'); sheet.paste(im, (i * S, r * S), im)
    dr.text((4, r * S + 4), cn, fill=(0, 0, 0))
sheet.save(out); print('SHEET', out)
