"""Shared bird body language: wing fold/flap, head, tail, legs. Works on the rest pose of any Meshy bird
whose bones are named in birdcfg."""
import math
from mathutils import Vector, Matrix, Quaternion

def basis_rot(s, c, s2, c2):
    """Rotation taking the frame (s, c) onto (s2, c2) (both orthonormalised)."""
    def frame(a, b):
        a = a.normalized(); b = (b - a * a.dot(b)).normalized(); n = a.cross(b)
        return Matrix((a, b, n)).transposed()
    return (frame(s2, c2) @ frame(s, c).transposed()).to_quaternion()

class Wings:
    """Pre-computed fold rotations for both wings (armature space, about the shoulder)."""
    def __init__(self, E, C, fold_dir=(0.14, 1.0, -0.62), chord_up=(0.0, 0.35, 1.0), bend=(0.0, 0.0, 0.0)):
        self.C = C; self.fold = {}; self.flap_axis = {}; self.fold_dir = {}
        for side, sx in (('L', 1), ('R', -1)):
            ch = C['wing' + side]
            h = E.rest[ch[0]].translation; t = E.rest[ch[-1]].translation + (E.rest[ch[-1]].to_3x3() @ Vector((0, E.length[ch[-1]], 0)))
            s = (t - h); c = self.chord(E, ch, h, s)
            fd = Vector((fold_dir[0] * sx, fold_dir[1], fold_dir[2])); cu = Vector((chord_up[0] * sx, chord_up[1], chord_up[2]))
            self.fold[side] = basis_rot(s, c, fd, cu); self.fold_dir[side] = fd.normalized()
            self.flap_axis[side] = Vector((0, 1, 0))
            self.span = s.length
        import json, os
        fj = f"/home/claude/birds/{C['key']}/fold.json" if 'key' in C else None
        if fj and os.path.exists(fj):
            for side, q in json.load(open(fj)).items(): self.fold[side] = Quaternion(q)

def _chord(self, E, ch, h, s):
    """Trailing direction: from the bone line to the wing's feathers (vertices dominated by the chain)."""
    me = E.body.data; idx = {g.index for g in E.body.vertex_groups if g.name in ch}
    sn = s.normalized(); acc = Vector(); n = 0
    for v in me.vertices:
        if not v.groups: continue
        g = max(v.groups, key=lambda q: q.weight)
        if g.group in idx:
            r = v.co - h; r -= sn * r.dot(sn); acc += r; n += 1
    import numpy as np
    pts = []
    for v in me.vertices:
        if not v.groups: continue
        g = max(v.groups, key=lambda q: q.weight)
        if g.group in idx:
            r = v.co - h; r -= sn * r.dot(sn); pts.append(r)
    A = np.array([tuple(p) for p in pts]); A -= A.mean(0)
    w, V = np.linalg.eigh(A.T @ A)
    nrm = Vector(V[:, 1]); c = Vector(V[:, 2]).normalized()     # projected along the span: largest axis = chord
    if c.dot(acc) < 0: c = -c                                  # towards the feathers
    if getattr(self, 'flip', False): c = -c
    print('CHORD', [round(x, 2) for x in c], 'N', [round(x, 2) for x in nrm])
    return c
Wings.chord = _chord


def slerp_id(q, k):
    return Quaternion().slerp(q, max(0.0, min(1.0, k)))


def fold_turns(W, side, k, share=0.5, extra=None):
    """Turns that fold one wing by k (0 spread .. 1 folded): the shoulder takes `share`, the next bone the rest.
    extra: optional list of (bone_index, Quaternion) applied after (in the folded wing's own frame)."""
    ch = W.C['wing' + side]; Rf = slerp_id(W.fold[side], k)
    Ra = slerp_id(Rf, share)
    out = [(ch[0], Ra), (ch[1], Ra.inverted() @ Rf)]
    if extra:
        for i, q in extra: out.append((ch[i], q))
    return out


def fold_pose(E, P, W, side, k, share=0.6, droop=0.0, tuck=0.0):
    """Folds one wing on pose P in place: shoulder basis-rotation (chord orientation), then every later bone is
    aligned to the fold direction so arched wings lie flat. k: 0 spread .. 1 folded."""
    if k <= 1e-4: return P
    ch = W.C['wing' + side]; sx = 1 if side == 'L' else -1
    M = E.fk(P)
    E.turn(P, M, fold_turns(W, side, k, share))
    tgt = W.fold_dir[side].copy()
    for i, b in enumerate(ch[2:], 2):
        M = E.fk(P); m = M[b]; h = m.translation; d = (m.to_3x3() @ Vector((0, 1, 0))).normalized()
        t = (tgt + Vector((-tuck * sx * i, 0, -droop * i))).normalized()
        q = d.rotation_difference(t)
        E.turn(P, M, [(b, slerp_id(q, k))])
    return P
