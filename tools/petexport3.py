"""Pulsify PET2 exporter for Snowy v2 (Meshy auto-rig + baked acting clips from duck2/scripts/bake2.py).

Run headless:  python3 petexport2.py -- <props.blend> <baked_dir> <face.json> <out_dir> <name>

PET2 = PET1 plus a control count:
  'PET2' u32 nverts u32 nidx u32 nbones u32 nclips f32 height f32 fps u32 eyeMode u32 nctl
  f32 fur[3] f32 lash[3]
  vertices (40 bytes, as PET1), u16 indices
  clips: u8 nameLen, name, u16 frames, u8 loop, frames x nbones x 12 f16, frames x nctl f16
Controls (12): blinkL, blinkR, lookXL, lookYL, lookXR, lookYR, expr, phase, tilt, happy, pupil, spare.
Frames are every other baked frame (15 fps).
"""
import bpy
import sys, json, math, struct, os
from mathutils import Vector
import numpy as np

blend, baked, face_json, out_dir, name, cfg_json = sys.argv[sys.argv.index('--') + 1:]
CFG = json.load(open(cfg_json))
SCALE = CFG.get('scale', 1.0); EYEMODE = CFG.get('eyeMode', 1)
bpy.ops.wm.open_mainfile(filepath=blend)
os.makedirs(out_dir, exist_ok=True)
rig = bpy.data.objects['Rig']
FACE = json.load(open(face_json))
BONES = json.load(open(os.path.join(baked, '_bones.json')))
SKIN = BONES['skin']
assert len(SKIN) <= 40, len(SKIN)
bidx = {n: i for i, n in enumerate(SKIN)}
STEP = 2; NCTL = 12

# clip name in the app <- baked clip, loop flag (life clips must end, so they are one-shots)
EXPORT_DUCK = [
    ('Idle', 'Idle', True), ('LookAround', 'Curious', False), ('HeadTilt', 'HeadTilt', False), ('Poke', 'Poke', False),
    ('Shake', 'Shake', False), ('Spin', 'Spin', False), ('Quack', 'Quack', False), ('TryToFly', 'TryToFly', False),
    ('Notice', 'Notice', False), ('Preen', 'Preen', False), ('Peck', 'Peck', False), ('CarryBread', 'CarryBread', True),
    ('FallAsleep', 'SitDown', False), ('Sleep', 'Sleep', True), ('WakeUp', 'WakeUp', False),
    ('Pet', 'Pet', True), ('Tickle', 'Tickle', False), ('Dangle', 'Dangle', True), ('Startle', 'Startle', False),
    ('Tumble', 'Tumble', False), ('AngryKick', 'AngryKick', False), ('Scared', 'Scared', False),
    ('JumpStart', 'JumpStart', False), ('JumpAir', 'Air', True), ('JumpLand', 'Land', False),
    ('Walk', 'Walk', True), ('Run', 'Run', True),
    ('Splash', 'Splash', False), ('RubberDuck', 'RubberDuck', False), ('ChaseButterfly', 'ChaseButterfly', False),
    ('DanceBubble', 'DanceBubble', False), ('DanceCardio', 'DanceCardio', False), ('DanceJoy', 'DanceJoy', False),
    ('DancePop', 'DancePop', False), ('DanceNight', 'DanceNight', False), ('DanceHipHop', 'DanceHipHop', False),
    ('DanceHipHop2', 'DanceHipHop2', False),
    ('Plugged', 'Plugged', False), ('ChargeRest', 'ChargeRest', True), ('Tired', 'Tired', True), ('Unplugged', 'Unplugged', False),
    ('FullCharge', 'FullCharge', False),
]

EXPORT = [tuple(x) for x in CFG['clips']] if 'clips' in CFG else EXPORT_DUCK

# ---------------- geometry (rest pose) ----------------
meshes = [o for o in bpy.data.objects if o.type == 'MESH' and (o.name == 'Body' or o.name.startswith('Prop_'))]
verts = []; vmap = {}; idx = []
for o in meshes:
    me = o.data; body = o.name == 'Body'
    me.calc_loop_triangles()
    uv0 = me.uv_layers[0].data if body else None
    eyeL = me.uv_layers.get('EyeL') if body else None; eyeR = me.uv_layers.get('EyeR') if body else None
    col = me.color_attributes.get('Col')
    gnames = {g.index: g.name for g in o.vertex_groups}
    gloss = int(CFG.get('gloss', 0.12) * 255) if body else 140
    # smoothed normals: Meshy meshes carry fine geometric noise that sparkles under rim / specular light
    NS = CFG.get('smooth', 3) if body else 0
    nrm = np.array([tuple(v.normal) for v in me.vertices], np.float32)
    if NS:
        ed = np.array([tuple(e.vertices) for e in me.edges], np.int64)
        for _ in range(NS):
            acc = nrm.copy()
            np.add.at(acc, ed[:, 0], nrm[ed[:, 1]]); np.add.at(acc, ed[:, 1], nrm[ed[:, 0]])
            nrm = acc / np.maximum(1e-6, np.linalg.norm(acc, axis=1, keepdims=True))
    vw = []
    for v in me.vertices:
        ws = [(bidx[gnames[g.group]], g.weight) for g in v.groups if gnames.get(g.group) in bidx and g.weight > 0.001]
        ws.sort(key=lambda a: -a[1]); ws = ws[:4]
        s = sum(w for _, w in ws) or 1.0
        ws = [(b, w / s) for b, w in ws] + [(0, 0.0)] * (4 - len(ws))
        q = [int(round(w * 255)) for _, w in ws]; q[0] += 255 - sum(q)
        vw.append(([b for b, _ in ws], q))
    for tri in me.loop_triangles:
        for li in tri.loops:
            vi = me.loops[li].vertex_index; v = me.vertices[vi]
            uv = tuple(uv0[li].uv) if body else (0.0, 0.0)
            def lay_uv(lay):
                if lay is None: return (7.9, 7.9)
                u = tuple(lay.data[li].uv)
                return u if abs(u[0]) < 7.5 and abs(u[1]) < 7.5 else (7.9, 7.9)
            eid = (lay_uv(eyeL), lay_uv(eyeR))
            c = tuple(col.data[vi].color) if col is not None else (0, 0, 0, 0)
            key = (o.name, vi, round(uv[0], 5), round(uv[1], 5), eid)
            if key not in vmap:
                n = Vector(nrm[vi])
                cc = [int(max(0, min(1, x ** (1 / 2.2))) * 255) for x in c[:3]]
                verts.append((tuple(c_ * SCALE for c_ in v.co), (n.x, n.y, n.z), gloss, uv, vw[vi][0], vw[vi][1], (cc[0], cc[1], cc[2], 0 if body else 255), eid))
                vmap[key] = len(verts) - 1
            idx.append(vmap[key])
assert len(verts) < 65536, len(verts)
body_h = CFG.get('height') or max(v.co.z for v in bpy.data.objects['Body'].data.vertices) * SCALE
print('VERTS', len(verts), 'TRIS', len(idx) // 3, 'BODY H', round(body_h, 3))

# ---------------- textures ----------------
from PIL import Image
img = bpy.data.images['tex.png']
W, H = img.size
px = (np.array(img.pixels[:], np.float32).reshape(H, W, 4)[::-1, :, :3] * 255).clip(0, 255).astype(np.uint8)
Image.fromarray(px).save(os.path.join(out_dir, f'{name}.jpg'), quality=92)
eyes = {e['side']: e for e in FACE['eyes']}
if EYEMODE == 0:
    fur = (0.0, 0.0, 0.0); lash = (0.0, 0.0, 0.0); iris = None
elif EYEMODE == 3:
    Image.open(CFG['atlas']).save(os.path.join(out_dir, f'{name}_eyes.png'))
    fur = tuple(FACE['lid']); lash = tuple(CFG.get('lash', (0.08, 0.07, 0.07)))
    iris = [*eyes['L']['ioff'], *eyes['R']['ioff']]
else:
    a = Image.new('RGB', (512, 256))
    for i, s in enumerate('LR'):
        a.paste(Image.open(eyes[s]['png']).convert('RGB').resize((256, 256), Image.LANCZOS), (i * 256, 0))
    a.save(os.path.join(out_dir, f'{name}_eyes.png'))
    fur = tuple(FACE['fur']); lash = (0.30, 0.24, 0.22); iris = None

# ---------------- clips ----------------
blob = bytearray(); n = 0
for app_name, src, loop in EXPORT:
    p = os.path.join(baked, f'{src}.npz')
    if not os.path.exists(p): print('MISSING', src); continue
    d = np.load(p)
    sk = d['skin']; fc = d['face']
    fr = list(range(0, len(sk), STEP))
    if not loop and fr[-1] != len(sk) - 1: fr.append(len(sk) - 1)
    sk2 = sk[fr].copy().reshape(len(fr), -1, 3, 4); sk2[..., 3] *= SCALE
    mats = sk2.reshape(len(fr), -1).astype(np.float16)
    ctl = np.zeros((len(fr), NCTL), np.float32)
    f = fc[fr]
    ctl[:, 0] = f[:, 0]; ctl[:, 1] = np.maximum(f[:, 1], f[:, 7])
    ctl[:, 2] = f[:, 2]; ctl[:, 3] = f[:, 3]; ctl[:, 4] = f[:, 2]; ctl[:, 5] = f[:, 3]
    ctl[:, 8] = f[:, 4]; ctl[:, 9] = f[:, 5]; ctl[:, 10] = f[:, 6]
    nb = app_name.encode()
    blob += struct.pack('<B', len(nb)) + nb + struct.pack('<HB', len(fr), 1 if loop else 0)
    blob += mats.tobytes() + ctl.astype(np.float16).tobytes()
    n += 1
print('CLIPS', n, 'bytes', len(blob))

with open(os.path.join(out_dir, f'{name}.pet'), 'wb') as f:
    f.write(b'PET2' + struct.pack('<IIIIffII', len(verts), len(idx), len(SKIN), n, body_h, 30.0 / STEP, EYEMODE, NCTL))
    f.write(struct.pack('<6f', *fur, *lash))
    if EYEMODE == 3: f.write(struct.pack('<4f', *iris))
    for (p, nn, g, uv, bs, ws, c, eid) in verts:
        f.write(struct.pack('<3f', *p))
        f.write(struct.pack('<3bB', *[int(max(-127, min(127, round(x * 127)))) for x in nn], g))
        f.write(struct.pack('<2H', *[int(max(0, min(65535, round((x % 1.0 if x != 1.0 else 1.0) * 65535)))) for x in uv]))
        f.write(struct.pack('<2h', *[int(max(-32767, min(32767, round(x * 4096)))) for x in eid[0]]))
        f.write(struct.pack('<4B', *bs)); f.write(struct.pack('<4B', *ws)); f.write(struct.pack('<4B', *c))
        f.write(struct.pack('<2h', *[int(max(-32767, min(32767, round(x * 4096)))) for x in eid[1]]))
    f.write(struct.pack(f'<{len(idx)}H', *idx))
    f.write(blob)
print('WROTE', os.path.getsize(os.path.join(out_dir, f'{name}.pet')))
