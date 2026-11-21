"""Per-bird bone roles. L = the bird's left = +x (it faces -y)."""
def toes(*names): return list(names)
BIRDS = {
 'budgie': dict(
    glb="/mnt/user-data/uploads/aa8d503a-Meshy_AI_Emerald_Wings_20261116_character.glb",
    root='Bone_000', body='Bone_001', chest='Bone_012', neck=['Bone_011', 'Bone_010'], head='Bone_044', beak=None, crest=None,
    tail=['Bone_018', 'Bone_017', 'Bone_016', 'Bone_015'],
    wingL=['Bone_054', 'Bone_053', 'Bone_052', 'Bone_051', 'Bone_050'], wingR=['Bone_049', 'Bone_048', 'Bone_047', 'Bone_046', 'Bone_045'],
    legL=['Bone_005', 'Bone_004', 'Bone_003', 'Bone_002'], legR=['Bone_009', 'Bone_008', 'Bone_007', 'Bone_006'],
    merge={**{b: 'Bone_002' for b in ['Bone_021','Bone_020','Bone_019','Bone_024','Bone_023','Bone_022','Bone_027','Bone_026','Bone_025','Bone_030','Bone_029','Bone_028']},
           **{b: 'Bone_006' for b in ['Bone_033','Bone_032','Bone_031','Bone_036','Bone_035','Bone_034','Bone_039','Bone_038','Bone_037','Bone_042','Bone_041','Bone_040']},
           'Bone_014': 'Bone_015', 'Bone_013': 'Bone_015', 'Bone_043': 'Bone_044'}),
 'cockatiel': dict(
    glb="/mnt/user-data/uploads/860f8898-Meshy_AI_Sunlit_Cockatiel_Dance_20261116_character.glb",
    root='Bone_000', body='Bone_001', chest='Bone_003', neck=['Bone_002'], head='Bone_018', beak='Bone_042', crest='Bone_017',
    tail=['Bone_016', 'Bone_015', 'Bone_014'],
    wingL=['Bone_026', 'Bone_025', 'Bone_024', 'Bone_023'], wingR=['Bone_022', 'Bone_021', 'Bone_020', 'Bone_019'],
    legL=['Bone_007', 'Bone_006', 'Bone_005', 'Bone_004'], legR=['Bone_011', 'Bone_010', 'Bone_009', 'Bone_008'],
    merge={**{b: 'Bone_004' for b in ['Bone_028','Bone_027','Bone_031','Bone_030','Bone_029']},
           **{b: 'Bone_008' for b in ['Bone_033','Bone_032','Bone_036','Bone_035','Bone_034']},
           'Bone_038': 'Bone_018', 'Bone_037': 'Bone_018', 'Bone_040': 'Bone_018', 'Bone_039': 'Bone_018',
           'Bone_041': 'Bone_042', 'Bone_013': 'Bone_014', 'Bone_012': 'Bone_014'}),
 'ringneck': dict(
    glb="/mnt/user-data/uploads/ed1b5e21-Meshy_AI_Emerald_Wing_Majesty_20261116_character.glb",
    root='Bone_000', body='Bone_001', chest='Bone_003', neck=['Bone_002', 'Bone_024', 'Bone_023'], head='Bone_022', beak='Bone_021', crest=None,
    tail=['Bone_020', 'Bone_019', 'Bone_018', 'Bone_017', 'Bone_016'],
    wingL=['Bone_032', 'Bone_031', 'Bone_030', 'Bone_029'], wingR=['Bone_028', 'Bone_027', 'Bone_026', 'Bone_025'],
    legL=['Bone_008', 'Bone_007', 'Bone_006', 'Bone_004'], legR=['Bone_013', 'Bone_012', 'Bone_011', 'Bone_009'],
    merge={**{b: 'Bone_004' for b in ['Bone_005','Bone_035','Bone_034','Bone_033','Bone_038','Bone_037','Bone_036']},
           **{b: 'Bone_009' for b in ['Bone_010','Bone_041','Bone_040','Bone_039','Bone_044','Bone_043','Bone_042','Bone_046','Bone_045','Bone_049','Bone_048','Bone_047']},
           'Bone_015': 'Bone_016', 'Bone_014': 'Bone_016'}),
}

for _k, _v in BIRDS.items(): _v['key'] = _k
