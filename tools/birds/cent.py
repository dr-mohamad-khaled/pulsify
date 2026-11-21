import bpy, sys
from mathutils import Vector
from collections import defaultdict
files={'budgie':"/mnt/user-data/uploads/aa8d503a-Meshy_AI_Emerald_Wings_20261116_character.glb",
'cockatiel':"/mnt/user-data/uploads/860f8898-Meshy_AI_Sunlit_Cockatiel_Dance_20261116_character.glb",
'ringneck':"/mnt/user-data/uploads/ed1b5e21-Meshy_AI_Emerald_Wing_Majesty_20261116_character.glb"}
for k,P in files.items():
    bpy.ops.wm.read_factory_settings(use_empty=True); bpy.ops.import_scene.gltf(filepath=P)
    me=bpy.data.objects['output_unwrapped']; mw=me.matrix_world; g={x.index:x.name for x in me.vertex_groups}
    acc=defaultdict(lambda:[Vector(),0])
    for v in me.data.vertices:
        if not v.groups: continue
        b=max(v.groups,key=lambda q:q.weight); a=acc[g[b.group]]; a[0]+=mw@v.co; a[1]+=1
    print('==',k, 'meshrot', [round(x,2) for x in me.rotation_euler], 'armrot',[round(x,2) for x in bpy.data.objects['SmartRigArmature'].rotation_euler], [round(x,2) for x in bpy.data.objects['SmartRigArmature'].scale])
    for n,(s,c) in sorted(acc.items(), key=lambda x:int(x[0][5:])):
        s/=c; print('  ',n,c,[round(x,2) for x in s])
