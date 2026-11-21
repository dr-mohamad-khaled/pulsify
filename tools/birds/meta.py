import bpy, sys, json
sys.path.insert(0, '/home/claude/birds/scripts')
from birdcfg import BIRDS
key = sys.argv[-1]; C = BIRDS[key]
bpy.ops.wm.open_mainfile(filepath=f'/home/claude/birds/{key}/{key}.rig.blend')
body = bpy.data.objects['Body']; rig = bpy.data.objects['Rig']
g = {x.index: x.name for x in body.vertex_groups}; wing = set(C['wingL'] + C['wingR'])
ys = [v.co.y for v in body.data.vertices if v.groups and g[max(v.groups, key=lambda q: q.weight).group] not in wing]
cy = (min(ys) + max(ys)) / 2; ry = rig.data.bones[C['root']].head_local.y
json.dump({'cy': ry - cy, 'ymin': min(ys), 'ymax': max(ys)}, open(f'/home/claude/birds/{key}/meta.json', 'w')); print(key, ry - cy, min(ys), max(ys))
