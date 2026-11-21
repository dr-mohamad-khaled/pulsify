import sys, math, bpy
sys.path.insert(0, '/home/claude/birds/scripts'); sys.path.insert(0, '/home/claude/duck2/scripts')
from birdcfg import BIRDS
from birdlib import Wings, fold_turns, fold_pose
from engine2 import Engine, rq
from mathutils import Vector, Quaternion
key = sys.argv[-2]; mode = sys.argv[-1]
C = BIRDS[key]; E = Engine(f'/home/claude/birds/{key}/{key}.rig.blend')
import json; fd, cu = json.loads(mode)[:2]
W = Wings(E, C, fd, cu)
P = E.rest_pose(); M = E.fk(P)
import json as J; sh = J.loads(sys.argv[-1])[2] if len(J.loads(sys.argv[-1])) > 2 else 1.0
ex = J.loads(sys.argv[-1]); dr = ex[3] if len(ex) > 3 else 0.0; tk = ex[4] if len(ex) > 4 else 0.0
if len(ex) > 5 and ex[5] == 'basis':
    E.turn(P, M, fold_turns(W, 'L', 1, sh) + fold_turns(W, 'R', 1, sh))
else:
    fold_pose(E, P, W, 'L', 1, sh, dr, tk); fold_pose(E, P, W, 'R', 1, sh, dr, tk)
E.apply_to_rig(P)
sc = bpy.context.scene; sc.render.engine = 'BLENDER_WORKBENCH'; sc.display.shading.light = 'STUDIO'; sc.display.shading.color_type = 'TEXTURE'
sc.render.resolution_x = sc.render.resolution_y = 400; sc.render.film_transparent = True
cam = bpy.data.objects.new('c', bpy.data.cameras.new('c')); sc.collection.objects.link(cam); sc.camera = cam; cam.data.type = 'ORTHO'; cam.data.ortho_scale = 2.4
import mathutils
for i, (d, nm) in enumerate([((0, -1, 0.15), 'front'), ((1, 0, 0.1), 'side'), ((0.7, 0.7, 0.4), 'back3q'), ((0, 0.3, 1), 'top')]):
    d = Vector(d).normalized(); cam.location = Vector((0, 0, 0.75)) + d * 10
    cam.rotation_euler = d.to_track_quat('Z', 'Y').to_euler()
    sc.render.filepath = f'/tmp/fold_{key}_{sys.argv[-3]}_{i}.png'; bpy.ops.render.render(write_still=True)
