"""Adds the expressive eye shader (look, blink, wink, sad/angry tilt, happy ^^, pupil size) to Snowy v2."""
import bpy, json, math

def build(rig, body, face):
    mat = body.active_material; nt = mat.node_tree; N_ = nt.nodes; Lk = nt.links
    bsdf = N_['Principled BSDF']; base = N_['tex.png']
    def node(t, **kw):
        n = N_.new(t)
        for k, v in kw.items(): setattr(n, k, v)
        return n
    def sock(x):
        if isinstance(x, (int, float)):
            v = node('ShaderNodeValue'); v.outputs[0].default_value = x; return v.outputs[0]
        return x
    def M(op, a, b=None):
        n = node('ShaderNodeMath', operation=op); Lk.new(sock(a), n.inputs[0])
        if b is not None: Lk.new(sock(b), n.inputs[1])
        return n.outputs[0]
    def sstep(e0, e1, x):
        n = node('ShaderNodeMapRange', interpolation_type='SMOOTHSTEP'); Lk.new(sock(x), n.inputs['Value'])
        n.inputs['From Min'].default_value = e0; n.inputs['From Max'].default_value = e1; return n.outputs['Result']
    def mixc(f, a, b):
        n = node('ShaderNodeMix', data_type='RGBA'); Lk.new(sock(f), n.inputs['Factor']); Lk.new(a, n.inputs[6]); Lk.new(b, n.inputs[7]); return n.outputs[2]
    def rgb(c):
        n = node('ShaderNodeRGB'); n.outputs[0].default_value = (*c, 1); return n.outputs[0]
    def attr(nm):
        a = node('ShaderNodeAttribute', attribute_type='OBJECT', attribute_name=nm); return a.outputs['Fac']
    lin = lambda c: tuple(((x + 0.055) / 1.055) ** 2.4 if x > 0.04045 else x / 12.92 for x in c)
    FUR = rgb(lin(face['fur'])); LASH = rgb(lin((0.30, 0.24, 0.22)))
    color = base.outputs['Color']
    rough = bsdf.inputs['Roughness'].links[0].from_socket if bsdf.inputs['Roughness'].links else sock(0.6)
    for e in face['eyes']:
        s = e['side']; sg = 1.0 if s == 'L' else -1.0
        uvn = node('ShaderNodeUVMap', uv_map=f'Eye{s}'); sp = node('ShaderNodeSeparateXYZ'); Lk.new(uvn.outputs[0], sp.inputs[0])
        u, v = sp.outputs[0], sp.outputs[1]
        d = M('SQRT', M('ADD', M('MULTIPLY', u, u), M('MULTIPLY', v, v)))
        inside = M('SUBTRACT', 1.0, sstep(0.955, 1.0, d))
        mk = node('ShaderNodeVertexColor', layer_name=f'Mask{s}')
        inside = M('MULTIPLY', inside, M('GREATER_THAN', mk.outputs['Color'], 0.999))
        b = M('MINIMUM', 1.0, attr('bL') if s == 'L' else M('MAXIMUM', attr('bR'), attr('wink')))
        lx = attr('lx'); ly = attr('ly')
        tilt = attr('tilt'); happy = attr('happy'); pupil = M('MAXIMUM', 0.4, attr('pupil'))
        tu = M('ADD', 0.5, M('DIVIDE', M('DIVIDE', M('SUBTRACT', u, lx), pupil), 2.3))
        tv = M('ADD', 0.5, M('DIVIDE', M('DIVIDE', M('SUBTRACT', v, ly), pupil), 2.3))
        cmb = node('ShaderNodeCombineXYZ'); Lk.new(tu, cmb.inputs[0]); Lk.new(tv, cmb.inputs[1])
        et = node('ShaderNodeTexImage'); et.image = bpy.data.images.load(e['png']); et.image.pack(); et.extension = 'EXTEND'
        Lk.new(cmb.outputs[0], et.inputs[0])
        uu = M('MULTIPLY', u, u)
        yup = M('ADD', M('SUBTRACT', 1.06, M('MULTIPLY', b, 1.36)), M('MULTIPLY', uu, M('MULTIPLY', b, 0.30)))
        yup = M('ADD', yup, M('SUBTRACT', M('MULTIPLY', tilt, M('MULTIPLY', u, 0.55 * sg)), M('MULTIPLY', M('ABSOLUTE', tilt), 0.32)))
        ylo = M('ADD', M('ADD', -1.06, M('MULTIPLY', b, 0.84)), M('MULTIPLY', happy, M('SUBTRACT', 1.5, M('MULTIPLY', uu, 0.9))))
        upper = sstep(-0.02, 0.02, M('SUBTRACT', v, yup)); lower = sstep(-0.02, 0.02, M('SUBTRACT', ylo, v))
        lid = M('MAXIMUM', upper, lower)
        lashk = sstep(0.0, 0.12, M('ADD', b, M('ABSOLUTE', tilt)))
        lash = M('MULTIPLY', M('MULTIPLY', upper, M('SUBTRACT', 1.0, sstep(0.07, 0.11, M('SUBTRACT', v, yup)))), lashk)
        eyec = mixc(lid, et.outputs['Color'], mixc(lash, FUR, LASH))
        color = mixc(inside, color, eyec)
        g = M('MULTIPLY', inside, M('SUBTRACT', 1.0, lid))
        rough = M('ADD', M('MULTIPLY', rough, M('SUBTRACT', 1.0, g)), M('MULTIPLY', g, 0.06))
    Lk.new(color, bsdf.inputs['Base Color']); Lk.new(rough, bsdf.inputs['Roughness'])

if __name__ == '__main__':
    OUT = '/home/claude/duck2'
    bpy.ops.wm.open_mainfile(filepath=f'{OUT}/duck2.rig.blend')
    face = json.load(open(f'{OUT}/face.json'))
    build(bpy.data.objects['Rig'], bpy.data.objects['Body'], face)
    bpy.ops.wm.save_as_mainfile(filepath=f'{OUT}/duck2.rig.blend')
    print('SHADER OK')
