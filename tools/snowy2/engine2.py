"""Snowy v2 animation engine: evaluates Meshy actions straight from their F-curves, runs my own FK,
and layers acting on top (world-space bone turns, root offsets, squash & stretch, jaw, springy
tail / wing tips, props). Everything is pure maths, so baking for the app and the preview render
share one code path."""
import bpy, math, random
from mathutils import Matrix, Vector, Quaternion
from bpy_extras import anim_utils

OUT = '/home/claude/duck2'
FPS = 30


class Engine:
    def __init__(self, blend=f'{OUT}/duck2.props.blend'):
        bpy.ops.wm.open_mainfile(filepath=blend)
        self.rig = rig = bpy.data.objects['Rig']
        self.body = bpy.data.objects['Body']
        bones = rig.data.bones
        self.names = [b.name for b in bones]
        depth = lambda b: 0 if b.parent is None else 1 + depth(b.parent)
        self.order = sorted(self.names, key=lambda n: depth(bones[n]))
        self.parent = {b.name: (b.parent.name if b.parent else None) for b in bones}
        self.rest = {b.name: b.matrix_local.copy() for b in bones}
        self.rrel = {n: (self.rest[p].inverted() @ self.rest[n] if p else self.rest[n]) for n, p in self.parent.items()}
        self.length = {b.name: b.length for b in bones}
        self.children = {n: [c for c, p in self.parent.items() if p == n] for n in self.names}
        self.curves = {}
        for a in bpy.data.actions:
            short = a.name.split('|')[1] if '|' in a.name else a.name
            cb = anim_utils.action_get_channelbag_for_slot(a, a.slots[0])
            chans = {}
            for fc in cb.fcurves:
                bn = fc.data_path.split('"')[1]; prop = fc.data_path.split('.')[-1]
                chans.setdefault(bn, {}).setdefault(prop, {})[fc.array_index] = fc
            self.curves[short] = (chans, int(a.frame_range[0]), int(a.frame_range[1]))
        self.springs = {}

    # ---------------- poses ----------------
    def rest_pose(self):
        return {n: [Vector(), Quaternion(), Vector((1, 1, 1))] for n in self.names}

    def frames(self, act):
        c = self.curves[act]; return c[1], c[2]

    def sample(self, act, f):
        chans, s, e = self.curves[act]
        f = max(s, min(e, f))
        P = self.rest_pose()
        for bn, props in chans.items():
            if bn not in P: continue
            if 'location' in props: P[bn][0] = Vector([props['location'][i].evaluate(f) for i in range(3)])
            if 'rotation_quaternion' in props:
                q = Quaternion([props['rotation_quaternion'][i].evaluate(f) for i in range(4)]); q.normalize(); P[bn][1] = q
            if 'scale' in props: P[bn][2] = Vector([props['scale'][i].evaluate(f) for i in range(3)])
        return P

    @staticmethod
    def mix(A, B, w):
        if w <= 0: return A
        if w >= 1: return B
        out = {}
        for n in A:
            a, b = A[n], B[n]
            out[n] = [a[0].lerp(b[0], w), a[1].slerp(b[1], w), a[2].lerp(b[2], w)]
        return out

    @staticmethod
    def copy(P):
        return {n: [v[0].copy(), v[1].copy(), v[2].copy()] for n, v in P.items()}

    def fk(self, P):
        M = {}
        for n in self.order:
            loc, q, s = P[n]
            basis = Matrix.LocRotScale(loc, q, s)
            p = self.parent[n]
            M[n] = (M[p] if p else Matrix()) @ self.rrel[n] @ basis
        return M

    # ---------------- layers ----------------
    def turn(self, P, M, deltas):
        """deltas: list of (bone, Quaternion in armature space). Rotates each bone about its own head."""
        for n, D in deltas:
            if n not in P: continue
            Pm = M[n]; h = Pm.translation
            Dh = Matrix.Translation(h) @ D.to_matrix().to_4x4() @ Matrix.Translation(-h)
            X = Pm.inverted() @ Dh @ Pm
            q = X.to_quaternion(); q.normalize()
            P[n][1] = P[n][1] @ q
        return P

    def spring_step(self, P, chain, key, k=110.0, damp=9.0, dt=1 / FPS, gravity=0.0, gain=1.0):
        """Lagging tip for a chain of bones (parents first). Keeps per-key state across frames."""
        for n in chain:
            M = self.fk(P)
            Pm = M[n]; H = Pm.translation.copy(); L = self.length[n]
            T = (Pm @ Vector((0, L, 0)))
            st = self.springs.get((key, n))
            if st is None:
                st = [T.copy(), Vector()]
                self.springs[(key, n)] = st
            p, v = st
            sub = 4
            for _ in range(sub):
                h = dt / sub
                acc = (T - p) * k - v * damp + Vector((0, 0, -gravity))
                v += acc * h; p += v * h
                d = p - H
                if d.length > 1e-6: p[:] = H + d.normalized() * L
            a = (T - H); b = (p - H)
            if a.length < 1e-6 or b.length < 1e-6: continue
            R = a.rotation_difference(b)
            if gain != 1.0: R = Quaternion().slerp(R, gain)
            self.turn(P, M, [(n, R)])
        return P

    def reset_springs(self, key=None):
        if key is None: self.springs = {}
        else: self.springs = {k: v for k, v in self.springs.items() if k[0] != key}

    def place_prop(self, P, M, name, W):
        """W: armature-space matrix applied to the prop mesh (as modelled), or None to hide."""
        b = 'p.' + name
        if b not in P: return
        par = self.parent[b]
        if W is None:
            P[b] = [Vector(), Quaternion(), Vector((0, 0, 0))]; return
        Pt = W @ self.rest[b]
        basis = (M[par] @ self.rrel[b]).inverted() @ Pt
        loc, q, s = basis.decompose()
        P[b] = [loc, q, s]

    def skin(self, M, bones):
        return [M[n] @ self.rest[n].inverted() for n in bones]

    def apply_to_rig(self, P):
        for n, (loc, q, s) in P.items():
            pbn = self.rig.pose.bones[n]
            pbn.location = loc; pbn.rotation_quaternion = q; pbn.scale = s


# ---------- small helpers ----------
def rq(axis, deg):
    ax = {'X': (1, 0, 0), 'Y': (0, 1, 0), 'Z': (0, 0, 1)}[axis] if isinstance(axis, str) else axis
    return Quaternion(ax, math.radians(deg))


def ss(x):
    x = max(0.0, min(1.0, x)); return x * x * (3 - 2 * x)


def env(t, a, b, c, d):
    """0 before a, ramps up a..b, holds, ramps down c..d."""
    if t <= a or t >= d: return 0.0
    if t < b: return ss((t - a) / max(1e-6, b - a))
    if t <= c: return 1.0
    return 1 - ss((t - c) / max(1e-6, d - c))


class Track:
    def __init__(self, default=0.0):
        self.k = []; self.d = default

    def key(self, t, v):
        self.k.append((t, v)); self.k.sort(); return self

    def keys(self, *tv):
        for t, v in tv: self.key(t, v)
        return self

    def __call__(self, t):
        k = self.k
        if not k: return self.d
        if t <= k[0][0]: return k[0][1]
        if t >= k[-1][0]: return k[-1][1]
        for (t0, v0), (t1, v1) in zip(k, k[1:]):
            if t0 <= t <= t1:
                return v0 + (v1 - v0) * ss((t - t0) / max(1e-6, t1 - t0))
        return k[-1][1]


class Face:
    CH = ['bL', 'bR', 'lx', 'ly', 'tilt', 'happy', 'pupil', 'wink', 'jaw']

    def __init__(self, dur, seed=1, blinks=True, saccades=True, rate=(2.2, 4.2), look_amp=(0.22, 0.10), loop=False):
        self.dur = dur
        self.t = {c: Track(1.0 if c == 'pupil' else 0.0) for c in self.CH}
        self.extra = []      # callables (t) -> dict of additive values
        rnd = random.Random(seed)
        self.blinks = []
        if blinks:
            t = rnd.uniform(0.4, 1.4)
            while t < dur - 0.3:
                self.blinks.append(t)
                if rnd.random() < 0.18: self.blinks.append(t + 0.32)   # double blink
                t += rnd.uniform(*rate)
        self.looks = []
        if saccades:
            t = rnd.uniform(0.3, 1.0); x, y = 0.0, 0.0
            pts = [(0.0, 0.0, 0.0)]
            while t < dur - 0.5:
                x = rnd.uniform(-look_amp[0], look_amp[0]); y = rnd.uniform(-look_amp[1], look_amp[1]) * 0.8
                if rnd.random() < 0.3: x, y = 0.0, 0.0
                pts.append((t, x, y)); t += rnd.uniform(0.7, 2.0)
            if loop: pts.append((dur - 0.25, 0.0, 0.0))
            self.looks = pts
        self.suppress_blink = Track(0.0)

    def __getitem__(self, c):
        return self.t[c]

    def pulse(self, c, t0, up, hold, down, v, base=None):
        tr = self.t[c]; b = tr(t0) if base is None else base
        tr.keys((t0, b), (t0 + up, v), (t0 + up + hold, v), (t0 + up + hold + down, b))

    def eval(self, t):
        out = {c: self.t[c](t) for c in self.CH}
        bk = 0.0
        for b in self.blinks:
            d = t - b
            if 0 <= d < 0.07: bk = max(bk, d / 0.07)
            elif 0.07 <= d < 0.11: bk = 1.0
            elif 0.11 <= d < 0.22: bk = max(bk, 1 - (d - 0.11) / 0.11)
        bk *= 1 - self.suppress_blink(t)
        out['bL'] = max(out['bL'], bk); out['bR'] = max(out['bR'], bk)
        if self.looks:
            lx = ly = 0.0
            for (t0, x0, y0), (t1, x1, y1) in zip(self.looks, self.looks[1:] + [(1e9, self.looks[-1][1], self.looks[-1][2])]):
                if t0 <= t < t1:
                    a = ss((t - t0) / 0.07) if t - t0 < 0.07 else 1.0
                    px, py = (0.0, 0.0)
                    i = self.looks.index((t0, x0, y0))
                    if i > 0: px, py = self.looks[i - 1][1], self.looks[i - 1][2]
                    lx = px + (x0 - px) * a; ly = py + (y0 - py) * a
                    break
            out['lx'] += lx; out['ly'] += ly
        for fn in self.extra:
            for k, v in fn(t).items(): out[k] = out[k] + v
        out['bL'] = min(1.0, max(0.0, out['bL'])); out['bR'] = min(1.0, max(0.0, out['bR']))
        return out
