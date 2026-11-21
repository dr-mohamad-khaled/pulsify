"""Husky (Meshy quadruped rig): normalise (feet on z=0), find eyes, build a 2-layer eye atlas
(sclera base + movable iris) and front-projected eye UVs. Saves reaper.rig.blend + face.json."""
import bpy, sys, math, json, numpy as np
from mathutils import Vector, Matrix
sys.path.insert(0, '/home/claude/reaper/scripts')
from loadpet import load
from PIL import Image
from scipy import ndimage
OUT = '/home/claude/reaper'
rig, body = load(f'{OUT}/work')
rig.animation_data.action = None
bpy.context.view_layer.objects.active = rig
for o in bpy.context.scene.objects: o.select_set(o in (rig, body))
bpy.ops.object.transform_apply(location=False, rotation=True, scale=True)
bpy.context.view_layer.update()
zmin = min(v.co.z for v in body.data.vertices)
# drop everything so the paws stand on z = 0 (rest pose), keep animation offsets intact
rig.location.z -= zmin   # the body is parented to the rig
bpy.ops.object.transform_apply(location=True, rotation=False, scale=False)
body.name = 'Body'; rig.name = 'Rig'
sc = bpy.context.scene
mat = body.active_material; nt = mat.node_tree
out_node = nt.nodes['Material Output']
em = nt.nodes.new('ShaderNodeEmission'); nt.links.new(nt.nodes['tex.png'].outputs['Color'], em.inputs['Color'])
orig = out_node.inputs[0].links[0].from_socket; nt.links.new(em.outputs[0], out_node.inputs[0])
sc.render.engine = 'CYCLES'; sc.cycles.samples = 4; sc.cycles.use_denoising = False
sc.render.resolution_x = sc.render.resolution_y = 1000; sc.view_settings.view_transform = 'Standard'
w = bpy.data.worlds.new('w'); sc.world = w; w.use_nodes = True; w.node_tree.nodes['Background'].inputs[1].default_value = 0
cam = bpy.data.cameras.new('c'); cam.type = 'ORTHO'; cam.ortho_scale = 1.0
co = bpy.data.objects.new('c', cam); sc.collection.objects.link(co); sc.camera = co
CZ = 1.25; PX = 1.0 / 1000
co.location = (0, -5, CZ); co.rotation_euler = (math.pi / 2, 0, 0)
rig.data.pose_position = 'REST'; bpy.context.view_layer.update()
sc.render.filepath = f'{OUT}/front.png'; bpy.ops.render.render(write_still=True)
nt.links.new(orig, out_node.inputs[0]); nt.nodes.remove(em); bpy.data.objects.remove(co)
img = np.asarray(Image.open(f'{OUT}/front.png').convert('RGB')).astype(np.float32) / 255
def to_w(px, py): return ((px - 500) * PX, CZ + (500 - py) * PX)
r_, g_, b_ = img[..., 0], img[..., 1], img[..., 2]
iris = (r_ > 0.55) & (r_ - b_ > 0.35) & (r_ - g_ > 0.15)
iris[:200] = False; iris[700:] = False
lab, k = ndimage.label(ndimage.binary_closing(iris, iterations=2)); sz = ndimage.sum(iris, lab, range(1, k + 1))
eyes = []
for idx in np.argsort(sz)[::-1][:2]:
    m = ndimage.binary_fill_holes(lab == idx + 1); ys, xs = np.nonzero(m)
    cx, cy = xs.mean(), ys.mean(); ir = max(np.ptp(xs), np.ptp(ys)) / 2 * 1.3 + 2
    # the eye white: bright pixels around the iris, connected to it
    yy, xx = np.mgrid[0:1000, 0:1000]
    near = np.hypot(xx - cx, yy - cy) < ir * 5.5
    white = (img.max(axis=2) < 0.32) & near          # the dark eye socket
    eye = ndimage.binary_fill_holes(ndimage.binary_closing(white | ndimage.binary_dilation(m, iterations=2), iterations=3))
    eye = ndimage.binary_opening(eye, iterations=2) | m
    lab2, _ = ndimage.label(eye); eye = lab2 == lab2[int(cy), int(cx)]
    eye = ndimage.binary_fill_holes(eye)
    ys2, xs2 = np.nonzero(eye)
    ex, ey = (xs2.min() + xs2.max()) / 2, (ys2.min() + ys2.max()) / 2
    R = max(np.ptp(xs2), np.ptp(ys2)) / 2 * 1.12 + 3
    x, z = to_w(ex, ey)
    eyes.append(dict(x=float(x), z=float(z), r=float(R * PX), cx=float(ex), cy=float(ey), rp=float(R), icx=float(cx), icy=float(cy), ir=float(ir), mask=eye, imask=m))
eyes.sort(key=lambda e: e['x']); eyes[0]['side'] = 'L'; eyes[1]['side'] = 'R'
print('EYES', [(e['side'], round(e['x'], 3), round(e['z'], 3), round(e['r'], 3), round(e['ir'] * PX / e['r'], 2)) for e in eyes])

# ---------- 2-layer eye atlas: row 0 = eye white (iris painted out, alpha = eye shape), row 1 = iris (alpha) ----------
S = 256
atlas = np.zeros((512, 512, 4), np.uint8)
for i, e in enumerate(eyes):
    rp = e['rp']; yy, xx = np.mgrid[0:S, 0:S]
    sx = e['cx'] - rp + (xx + 0.5) * 2 * rp / S; sy = e['cy'] - rp + (yy + 0.5) * 2 * rp / S
    ix = np.clip(sx.astype(int), 0, 999); iy = np.clip(sy.astype(int), 0, 999)
    crop = img[iy, ix]
    emask = ndimage.gaussian_filter(e['mask'].astype(np.float32), 1.2)[iy, ix]
    # iris disc (+ pupil + highlight) around the iris centre
    di = np.hypot(sx - e['icx'], sy - e['icy']) / (e['ir'] * 1.05)
    ia = np.clip((1.0 - di / 1.6) / 0.15, 0, 1)
    white = np.median(img[e['mask'] & ~ndimage.binary_dilation(e['imask'], iterations=6)], axis=0)
    base = crop * (1 - ia[..., None]) + white * ia[..., None]
    atlas[0:S, i * S:(i + 1) * S, :3] = (base * 255).clip(0, 255); atlas[0:S, i * S:(i + 1) * S, 3] = (emask * 255).clip(0, 255)
    # iris layer centred in its tile (so look = shifting it)
    sx2 = e['icx'] - rp + (xx + 0.5) * 2 * rp / S; sy2 = e['icy'] - rp + (yy + 0.5) * 2 * rp / S
    icrop = img[np.clip(sy2.astype(int), 0, 999), np.clip(sx2.astype(int), 0, 999)]
    di2 = np.hypot(sx2 - e['icx'], sy2 - e['icy']) / (e['ir'] * 1.05)
    atlas[S:2 * S, i * S:(i + 1) * S, :3] = (icrop * 255).clip(0, 255)
    atlas[S:2 * S, i * S:(i + 1) * S, 3] = (np.clip((1.0 - di2) / 0.08, 0, 1) * 255)
    e['ioff'] = [float((e['icx'] - e['cx']) / rp), float(-(e['icy'] - e['cy']) / rp)]
Image.fromarray(atlas, 'RGBA').save(f'{OUT}/eyes_atlas.png')
# lid colour: the dark rim just outside the eye white
lid = np.median(np.concatenate([img[e['mask'] & ~ndimage.binary_dilation(e['imask'], iterations=6)] for e in eyes]), axis=0)
print('LID', lid.round(3))

# ---------- eye UVs (front projection; 9 = not on the face) ----------
deps = bpy.context.evaluated_depsgraph_get(); bev = body.evaluated_get(deps)
for e in eyes:
    ok, loc, nrm, _ = bev.ray_cast(Vector((e['x'], -3, e['z'])), Vector((0, 1, 0)))
    e['P'] = loc
me = body.data
for e in eyes:
    uvl = me.uv_layers.new(name=f'Eye{e["side"]}')
    for poly in me.polygons:
        for li in poly.loop_indices:
            vi = me.loops[li].vertex_index; c = me.vertices[vi].co; n = me.vertices[vi].normal
            if c.y < e['P'].y + 0.10 and n.y < 0.3:
                uvl.data[li].uv = ((c.x - e['x']) / e['r'], (c.z - e['z']) / e['r'])
            else:
                uvl.data[li].uv = (9.0, 9.0)
me.uv_layers.active_index = 0
for i, l in enumerate(me.uv_layers): l.active_render = (i == 0)
# 'mover': an axis-aligned top bone (x right, y back, z up) that the acting layers translate, squash and spin
bpy.context.view_layer.objects.active = rig; bpy.ops.object.mode_set(mode='EDIT'); eb = rig.data.edit_bones
mv = eb.new('mover'); mv.head = (0, 0, 0); mv.tail = (0, 0.12, 0); mv.roll = 0; mv.use_deform = False
hips = next(b for b in eb if b.name.endswith('Hips'))
hips.parent = mv
bpy.ops.object.mode_set(mode='OBJECT')
rig.data.pose_position = 'POSE'
json.dump(dict(eyes=[{k: v for k, v in e.items() if k not in ('mask', 'imask', 'P')} for e in eyes], lid=[float(x) for x in lid], zshift=float(-zmin)),
          open(f'{OUT}/face.json', 'w'))
bpy.ops.wm.save_as_mainfile(filepath=f'{OUT}/reaper.rig.blend')
print('SAVED')
