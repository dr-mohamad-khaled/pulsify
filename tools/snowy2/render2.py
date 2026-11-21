"""Preview frames of baked clips: python3 render2.py -- Clip1,Clip2 [step] [res] [maxsecs]"""
import sys, os, json, numpy as np
sys.path.insert(0, '/home/claude/duck2/scripts'); sys.path.insert(0, '/home/claude/scripts')
import bpy
from mathutils import Vector, Quaternion
from scene_setup import setup, set_cam
a = sys.argv[sys.argv.index('--') + 1:]
clips = a[0].split(','); step = int(a[1]) if len(a) > 1 else 2; res = int(a[2]) if len(a) > 2 else 360; maxs = float(a[3]) if len(a) > 3 else 99
bpy.ops.wm.open_mainfile(filepath='/home/claude/duck2/duck2.props.blend')
rig = bpy.data.objects['Rig']; body = bpy.data.objects['Body']
names = json.load(open('/home/claude/duck2/baked/_bones.json'))['names']
setup(res=res, samples=int(os.environ.get('SAMP', 8))); set_cam(18, 2.5, 0.2, 0.5)
bpy.context.scene.cycles.use_denoising = True
FK = ('bL', 'bR', 'lx', 'ly', 'tilt', 'happy', 'pupil', 'wink')
for cn in clips:
    d = np.load(f'/home/claude/duck2/baked/{cn}.npz')
    B, F = d['basis'], d['face']
    os.makedirs(f'/home/claude/duck2/frames/{cn}', exist_ok=True)
    nf = min(len(B), int(maxs * 30))
    for i in range(0, nf, step):
        for j, n in enumerate(names):
            pb = rig.pose.bones[n]; v = B[i, j]
            pb.location = v[0:3]; pb.rotation_quaternion = Quaternion(v[3:7]); pb.scale = v[7:10]
        for k, nm in enumerate(FK): body[nm] = float(F[i, k])
        body.update_tag(); bpy.context.view_layer.update()
        bpy.context.scene.render.filepath = f'/home/claude/duck2/frames/{cn}/{i // step:04d}.png'
        bpy.ops.render.render(write_still=True)
    print('RENDERED', cn, flush=True)
