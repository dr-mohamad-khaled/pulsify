"""Pulsify PET1 exporter: rigged + animated creature (.anim.blend from the Pulsify3D pipeline)
-> assets/pets/<name>.pet (+ <name>.jpg colour texture, <name>_eyes.png eye atlas).

Run headless:  python3 petexport.py -- <anim.blend> <out_dir> <name> <eyes.json>

PET1 layout (little endian)
  'PET1' u32 nverts u32 nidx u32 nbones u32 nclips f32 height f32 fps u32 eyeMode(0 none, 1 eyes, 2 screen)
  f32 fur[3] f32 lash[3]
  vertices, 40 bytes: pos 3f | normal 3 i8 + gloss u8 | uv 2 u16 | eye uv 2 i16 (/4096) | bone 4 u8 | weight 4 u8 | colour rgba8
                      | eye2 uv 2 i16 (eyes: aEye = left eye, aEye2 = right eye; robot: front / back screen; 7.9 = none)
  indices u16
  clips: u8 nameLen, name, u16 frames, u8 loop, then frames x nbones x 12 f16 (skin matrix rows 0..2),
         then frames x 8 f16 controls (blinkL, blinkR, lookXL, lookYL, lookXR, lookYR, expr, phase)
Frames are sampled every other Blender frame (15 fps).
"""
import bpy, sys, json, math, struct, os
import numpy as np
from mathutils import Matrix, Vector

blend, out_dir, name, eyes_json = sys.argv[-4:]
bpy.ops.wm.open_mainfile(filepath=blend)
os.makedirs(out_dir, exist_ok=True)
rig = bpy.data.objects['Rig']
EYES = json.load(open(eyes_json))
STEP = 2

meshes = [o for o in bpy.data.objects if o.type == 'MESH' and (o.name == 'Body' or o.name.startswith('Prop_'))]
# skin bones = deform bones that actually own weights
used = set()
for o in meshes:
    for g in o.vertex_groups: used.add(g.name)
skin = [b.name for b in rig.data.bones if b.use_deform and b.name in used]
assert len(skin) <= 32, f'too many skin bones: {len(skin)}'
bidx = {n: i for i, n in enumerate(skin)}
print('SKIN BONES', len(skin), skin)

# ---------------- geometry ----------------
for tr in rig.animation_data.nla_tracks: tr.mute = True
rig.animation_data.action = None
for pbn in rig.pose.bones:
    pbn.rotation_quaternion = (1, 0, 0, 0); pbn.location = (0, 0, 0); pbn.scale = (1, 1, 1)
bpy.context.view_layer.update()

verts = []; vmap = {}; idx = []
def roughness_of(o):
    m = o.active_material
    if not m or not m.use_nodes: return 0.5
    b = m.node_tree.nodes.get('Principled BSDF')
    if not b: return 0.5
    if b.inputs['Roughness'].links: return 0.6
    return b.inputs['Roughness'].default_value
for o in meshes:
    me = o.data
    body = o.name == 'Body'
    me.calc_loop_triangles()
    uv0 = me.uv_layers[0].data if body else None
    eyeL = me.uv_layers.get('EyeL'); eyeR = me.uv_layers.get('EyeR')
    scF = me.uv_layers.get('ScreenF'); scB = me.uv_layers.get('ScreenB')
    col = me.color_attributes.get('Col')
    gnames = {g.index: g.name for g in o.vertex_groups}
    gloss = int(max(0, min(1, 1 - roughness_of(o))) * 255)
    vw = []
    for v in me.vertices:
        ws = [(bidx[gnames[g.group]], g.weight) for g in v.groups if gnames.get(g.group) in bidx and g.weight > 0.001]
        ws.sort(key=lambda a: -a[1]); ws = ws[:4]
        s = sum(w for _, w in ws) or 1.0
        ws = [(b, w / s) for b, w in ws] + [(0, 0.0)] * (4 - len(ws))
        q = [int(round(w * 255)) for _, w in ws]
        q[0] += 255 - sum(q)
        vw.append(([b for b, _ in ws], q))
    for tri in me.loop_triangles:
        for li in tri.loops:
            vi = me.loops[li].vertex_index
            v = me.vertices[vi]
            uv = tuple(uv0[li].uv) if body else (0.0, 0.0)
            def lay_uv(lay):
                if lay is None: return (7.9, 7.9)
                u = tuple(lay.data[li].uv)
                return u if abs(u[0]) < 7.5 and abs(u[1]) < 7.5 else (7.9, 7.9)
            if eyeL is not None: e, e2 = lay_uv(eyeL), lay_uv(eyeR)
            elif scF is not None: e, e2 = lay_uv(scF), lay_uv(scB)
            else: e, e2 = (7.9, 7.9), (7.9, 7.9)
            eid = (e, e2)
            c = tuple(col.data[vi].color) if col is not None else (0, 0, 0, 0)
            key = (o.name, vi, round(uv[0], 5), round(uv[1], 5))
            if key not in vmap:
                n = v.normal
                cc = [int(max(0, min(1, x ** (1 / 2.2))) * 255) for x in c[:3]]
                verts.append((tuple(v.co), (n.x, n.y, n.z), gloss, uv, None, vw[vi][0], vw[vi][1],
                               (cc[0], cc[1], cc[2], 0 if body else 255), eid))
                vmap[key] = len(verts) - 1
            idx.append(vmap[key])
assert len(verts) < 65536, len(verts)
height = max(v[0][2] for v in verts if True)
body_h = max(v.co.z for v in bpy.data.objects['Body'].data.vertices)
print('VERTS', len(verts), 'TRIS', len(idx) // 3, 'BODY H', round(body_h, 3))

# ---------------- textures ----------------
from PIL import Image
img = bpy.data.images['Image_0']
W, H = img.size
px = (np.array(img.pixels[:], np.float32).reshape(H, W, 4)[::-1, :, :3] * 255).clip(0, 255).astype(np.uint8)
Image.fromarray(px).resize((1024, 1024), Image.LANCZOS).save(os.path.join(out_dir, f'{name}.jpg'), quality=88)
eye_mode = 0; fur = (0.9, 0.9, 0.9); lash = (0.2, 0.15, 0.14)
if 'L' in EYES and 'png' in EYES['L']:
    eye_mode = 1
    a = Image.new('RGB', (512, 256))
    for i, s in enumerate('LR'):
        a.paste(Image.open(EYES[s]['png']).convert('RGB').resize((256, 256)), (i * 256, 0))
    a.save(os.path.join(out_dir, f'{name}_eyes.png'))
    fur = tuple(EYES['L']['fur'])
    lash = (0.20, 0.15, 0.14) if EYES['L'].get('rim') == 'white' else (0.42, 0.40, 0.43)
elif 'L' in EYES and EYES['L'].get('screen'):
    eye_mode = 2

# ---------------- clips ----------------
pb = rig.pose.bones
inv_rest = {n: rig.data.bones[n].matrix_local.inverted() for n in skin}
def ctrl():
    out = []
    for s in 'LR':
        lid = pb.get(f'lid.{s}'); eye = pb.get(f'eye.{s}')
        out.append(max(0.0, min(1.0, -lid.rotation_quaternion.to_euler('XYZ').x / (math.pi / 2))) if lid else 0.0)
    looks = []
    for s in 'LR':
        eye = pb.get(f'eye.{s}')
        if eye:
            e = eye.rotation_quaternion.to_euler('XYZ'); looks += [-e.z * 1.3, e.x * 1.3]
        else: looks += [0.0, 0.0]
    face = pb.get('face')
    fx = (face.location.x, face.location.y) if face else (0.0, 0.0)
    return [out[0], out[1], looks[0], looks[1], looks[2], looks[3], fx[0], fx[1]]
clips = json.load(open(os.path.join(os.path.dirname(blend), f'{name}_clips.json')))
blob = bytearray()
nclips = 0
for tr in rig.animation_data.nla_tracks:
    act = tr.strips[0].action
    cname = act.name
    if cname not in clips: continue
    rig.animation_data.action = act
    s, e = int(act.frame_range[0]), int(act.frame_range[1])
    frames = list(range(s, e + 1, STEP))
    if frames[-1] != e: frames.append(e)
    mats = np.zeros((len(frames), len(skin), 12), np.float16); cts = np.zeros((len(frames), 8), np.float16)
    for fi, f in enumerate(frames):
        bpy.context.scene.frame_set(f)
        for bi, n in enumerate(skin):
            m = pb[n].matrix @ inv_rest[n]
            mats[fi, bi] = [m[r][c] for r in range(3) for c in range(4)]
        cts[fi] = ctrl()
    nb = cname.encode()
    blob += struct.pack('<B', len(nb)) + nb + struct.pack('<HB', len(frames), 1 if clips[cname][1] else 0)
    blob += mats.tobytes() + cts.tobytes()
    nclips += 1
print('CLIPS', nclips, 'clip bytes', len(blob))

with open(os.path.join(out_dir, f'{name}.pet'), 'wb') as f:
    f.write(b'PET1' + struct.pack('<IIIIffI', len(verts), len(idx), len(skin), nclips, body_h, 30.0 / STEP, eye_mode))
    f.write(struct.pack('<6f', *fur, *lash))
    for (p, n, g, uv, e, bs, ws, c, eid) in verts:
        f.write(struct.pack('<3f', *p))
        f.write(struct.pack('<3bB', *[int(max(-127, min(127, round(x * 127)))) for x in n], g))
        f.write(struct.pack('<2H', *[int(max(0, min(65535, round((x % 1.0 if x != 1.0 else 1.0) * 65535)))) for x in uv]))
        f.write(struct.pack('<2h', *[int(max(-32767, min(32767, round(x * 4096)))) for x in eid[0]]))
        f.write(struct.pack('<4B', *bs)); f.write(struct.pack('<4B', *ws)); f.write(struct.pack('<4B', *c))
        f.write(struct.pack('<2h', *[int(max(-32767, min(32767, round(x * 4096)))) for x in eid[1]]))
    f.write(struct.pack(f'<{len(idx)}H', *idx))
    f.write(blob)
print('WROTE', os.path.getsize(os.path.join(out_dir, f'{name}.pet')))
