"""Snowy v2 (Meshy Mixamo rig): normalise, add root + jaw + IK helpers, flush shader eyes with moods."""
import bpy, sys, math, json, numpy as np
from mathutils import Vector, Matrix
sys.path.insert(0, '/home/claude/duck2/scripts')
from load_duck import load
from PIL import Image
from scipy import ndimage

OUT = '/home/claude/duck2'
rig, body = load()
bpy.context.view_layer.objects.active = rig
for o in bpy.context.scene.objects: o.select_set(o in (rig, body))
rig.animation_data.action = None
bpy.ops.object.transform_apply(location=False, rotation=True, scale=True)
body.name = 'Body'; rig.name = 'Rig'
sc = bpy.context.scene

# ---------- root, jaw, IK helpers ----------
bpy.ops.object.select_all(action='DESELECT'); rig.select_set(True); bpy.context.view_layer.objects.active = rig
bpy.ops.object.mode_set(mode='EDIT'); eb = rig.data.edit_bones
H = 'mixamorig:'
root = eb.new('root'); root.head = (0, 0, 0); root.tail = (0, 0.12, 0); root.use_deform = False
eb[H + 'Hips'].parent = root
head = eb[H + 'Head']
# beak: front of the head, lower half -> jaw pivot at the beak root
jaw = eb.new('jaw'); jaw.parent = head; jaw.use_deform = False
for s, side in (('L', 'Left'), ('R', 'Right')):
    foot = eb[H + side + 'Foot']
    ik = eb.new(f'ik.{s}'); ik.head = foot.head.copy(); ik.tail = foot.head + Vector((0, -0.06, 0)); ik.parent = root; ik.use_deform = False
    knee = eb[H + side + 'Leg']
    pole = eb.new(f'pole.{s}'); pole.head = knee.head + Vector((0, -0.25, 0)); pole.tail = pole.head + Vector((0, -0.05, 0)); pole.parent = root; pole.use_deform = False
bpy.ops.object.mode_set(mode='OBJECT')

# ---------- flat front render to find eyes + beak ----------
mat = body.active_material; nt = mat.node_tree; bsdf = nt.nodes['Principled BSDF']
base_tex = nt.nodes['tex.png']
out_node = nt.nodes['Material Output']
em = nt.nodes.new('ShaderNodeEmission'); nt.links.new(base_tex.outputs['Color'], em.inputs['Color'])
orig_link = out_node.inputs[0].links[0].from_socket
nt.links.new(em.outputs[0], out_node.inputs[0])
sc.render.engine = 'CYCLES'; sc.cycles.samples = 4; sc.cycles.use_denoising = False
sc.render.resolution_x = sc.render.resolution_y = 1000; sc.view_settings.view_transform = 'Standard'
w = bpy.data.worlds.new('w'); sc.world = w; w.use_nodes = True; w.node_tree.nodes['Background'].inputs[1].default_value = 0
cam = bpy.data.cameras.new('c'); cam.type = 'ORTHO'; cam.ortho_scale = 1.2
co = bpy.data.objects.new('c', cam); sc.collection.objects.link(co); sc.camera = co
co.location = (0, -5, 0.5); co.rotation_euler = (math.pi / 2, 0, 0)
rig.data.pose_position = 'REST'; bpy.context.view_layer.update()
sc.render.filepath = f'{OUT}/front.png'; bpy.ops.render.render(write_still=True)
nt.links.new(orig_link, out_node.inputs[0]); nt.nodes.remove(em)
bpy.data.objects.remove(co)
PX = 0.0012
img = np.asarray(Image.open(f'{OUT}/front.png').convert('RGB')).astype(np.float32) / 255
def to_w(px, py): return ((px - 500) * PX, 1.1 - py * PX)
# eyes = the two largest dark blobs in the upper half
obj = ndimage.binary_fill_holes(ndimage.binary_closing(img.sum(axis=2) > 0.06, iterations=3))
obj = ndimage.binary_erosion(obj, iterations=6)
dark = (img.mean(axis=2) < 0.28) & obj
dark[:150] = False; dark[520:] = False
lab, k = ndimage.label(dark); sizes = ndimage.sum(dark, lab, range(1, k + 1))
order = np.argsort(sizes)[::-1][:2]
eyes = []
for idx in order:
    m = ndimage.binary_fill_holes(lab == idx + 1)
    ys, xs = np.nonzero(m)
    cx, cy = xs.mean(), ys.mean(); r = (max(xs.max() - xs.min(), ys.max() - ys.min()) / 2) * 1.3 + 2
    x, z = to_w(cx, cy)
    eyes.append(dict(x=float(x), z=float(z), r=float(r * PX), cx=cx, cy=cy, rp=r))
eyes.sort(key=lambda e: e['x'])
eyes[0]['side'] = 'L'; eyes[1]['side'] = 'R'
print('EYES', [(e['side'], round(e['x'], 3), round(e['z'], 3), round(e['r'], 3)) for e in eyes])
# beak = orange pixels
orange = (img[..., 0] > 0.75) & (img[..., 2] < 0.45) & (img[..., 0] - img[..., 2] > 0.4)
orange[:150] = False; orange[650:] = False
ys, xs = np.nonzero(orange)
bz_top, bz_bot = to_w(0, ys.min())[1], to_w(0, ys.max())[1]
print('BEAK z', round(bz_bot, 3), '..', round(bz_top, 3), 'x', round(to_w(xs.min(), 0)[0], 3), round(to_w(xs.max(), 0)[0], 3))

# ---------- eye crops ----------
fur = []
for e in eyes:
    rp = e['rp']; h = rp * 1.15; S = 256
    yy, xx = np.mgrid[0:S, 0:S]
    sx = e['cx'] - h + (xx + 0.5) * 2 * h / S; sy = e['cy'] - h + (yy + 0.5) * 2 * h / S
    crop = img[np.clip(sy.astype(int), 0, 999), np.clip(sx.astype(int), 0, 999)] * 255
    d = np.hypot(sx - e['cx'], sy - e['cy']) / rp
    ring = crop[(d > 0.86) & (d < 0.97)]
    rim = np.median(ring, axis=0)
    a = np.clip((d - 0.95) / 0.05, 0, 1)[..., None]
    tex = crop * (1 - a) + rim * a
    p = f'{OUT}/eye_{e["side"]}.png'; Image.fromarray(tex.astype(np.uint8)).save(p); e['png'] = p
    for t in np.linspace(0, 2 * math.pi, 40):
        fur.append(img[int(e['cy'] + math.sin(t) * rp * 1.5), int(e['cx'] + math.cos(t) * rp * 1.5)])
fur = np.median(np.array(fur), axis=0)
print('FUR', fur.round(3))

# ---------- raycast: eye surface points, beak geometry ----------
deps = bpy.context.evaluated_depsgraph_get()
bev = body.evaluated_get(deps)
for e in eyes:
    ok, loc, nrm, _ = bev.ray_cast(Vector((e['x'], -3, e['z'])), Vector((0, 1, 0)))
    e['P'] = loc; e['N'] = nrm
me = body.data
# jaw weights: beak vertices below the beak mid-line
beak_mid = bz_bot + (bz_top - bz_bot) * 0.42
img_tex = bpy.data.images['tex.png']; TW, TH = img_tex.size
tpx = np.array(img_tex.pixels[:], np.float32).reshape(TH, TW, 4)
uv0 = me.uv_layers[0].data; vuv = {}
for l in me.loops: vuv[l.vertex_index] = uv0[l.index].uv[:]
ymin = min(v.co.y for v in me.vertices)
jaw_verts = []
for v in me.vertices:
    if v.co.y > ymin + 0.22 or v.co.z > beak_mid or v.co.z < bz_bot - 0.03: continue
    u_, w_ = vuv[v.index]; c = tpx[int((w_ % 1) * (TH - 1)), int((u_ % 1) * (TW - 1))]
    if c[0] > 0.55 and c[2] < 0.35 and c[0] - c[2] > 0.35: jaw_verts.append(v.index)
beak_front = min((me.vertices[i].co.y for i in jaw_verts), default=ymin)
beak_back = max((me.vertices[i].co.y for i in jaw_verts), default=ymin + 0.1)
print('JAW verts', len(jaw_verts), 'beak y', round(beak_front, 3), round(beak_back, 3), 'mid z', round(beak_mid, 3))
bpy.ops.object.mode_set(mode='OBJECT')
bpy.context.view_layer.objects.active = rig; bpy.ops.object.mode_set(mode='EDIT')
jb = rig.data.edit_bones['jaw']
jb.head = Vector((0, beak_back + 0.01, beak_mid + 0.005)); jb.tail = Vector((0, beak_front, beak_mid - 0.01)); jb.roll = 0
bpy.ops.object.mode_set(mode='OBJECT')
jg = body.vertex_groups.get('jaw') or body.vertex_groups.new(name='jaw')
for i in jaw_verts:
    for g in me.vertices[i].groups: g.weight = 0.0
    jg.add([i], 1.0, 'REPLACE')
# smooth the hinge a little: beak verts just above the line share head/jaw
rig.data.bones['jaw'].use_deform = True

# ---------- eye UV maps (front projection, 7.9 = none) ----------
for e in eyes:
    uvl = me.uv_layers.new(name=f'Eye{e["side"]}')
    for poly in me.polygons:
        for li in poly.loop_indices:
            vi = me.loops[li].vertex_index; c = me.vertices[vi].co; n = me.vertices[vi].normal
            if c.y < e['P'].y + 0.10 and n.y < 0.25:
                uvl.data[li].uv = ((c.x - e['x']) / e['r'], (c.z - e['z']) / e['r'])
            else:
                uvl.data[li].uv = (9.0, 9.0)
me.uv_layers.active_index = 0
for i, l in enumerate(me.uv_layers): l.active_render = (i == 0)
# eye masks: 1 only on vertices close to the eye, so triangles that straddle the head never show an eye
for e in eyes:
    ca = me.color_attributes.new(f'Mask{e["side"]}', 'FLOAT_COLOR', 'POINT')
    lay = me.uv_layers[f'Eye{e["side"]}']
    near = set()
    for li, l in enumerate(me.loops):
        u = lay.data[li].uv
        if abs(u[0]) < 2.2 and abs(u[1]) < 2.2: near.add(l.vertex_index)
    for v in me.vertices: ca.data[v.index].color = (1.0, 1.0, 1.0, 1.0) if v.index in near else (0.0, 0.0, 0.0, 1.0)

# ---------- face controls: custom properties on the rig (keyed per clip) ----------
FACE = ['bL', 'bR', 'lx', 'ly', 'tilt', 'happy', 'pupil', 'wink']
for k in FACE: body[k] = 0.0
body['pupil'] = 1.0

json.dump(dict(eyes=[{k: (v if not isinstance(v, Vector) else list(v)) for k, v in e.items()} for e in eyes],
               fur=[float(x) for x in fur], beak=dict(mid=beak_mid, top=bz_top, bot=bz_bot)),
          open(f'{OUT}/face.json', 'w'), default=float)
rig.data.pose_position = 'POSE'
bpy.ops.wm.save_as_mainfile(filepath=f'{OUT}/duck2.rig.blend')
print('SAVED')
