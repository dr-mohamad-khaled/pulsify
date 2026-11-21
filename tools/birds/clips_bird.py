"""Shared bird library (budgie, cockatiel, ringneck, any Meshy bird in birdcfg): every motion is hand-made
acting on the rest pose. BIRD=<key> python3 bake3.py -- clips_bird.py"""
import math, os, sys, random
sys.path.insert(0, '/home/claude/duck2/scripts'); sys.path.insert(0, '/home/claude/birds/scripts')
from mathutils import Vector, Matrix, Quaternion
from engine2 import rq, ss, env, Track, Face, FPS
from birdcfg import BIRDS
from birdlib import Wings, slerp_id

KEY = os.environ.get('BIRD', 'budgie'); C = BIRDS[KEY]
BLEND = f'/home/claude/birds/{KEY}/{KEY}.props.blend'
OUT = f'/home/claude/birds/{KEY}/baked'
MOVER = 'mover'; HIPS = C['root']
import json as _json
HIPS_TARGET = (0.0, _json.load(open(f'/home/claude/birds/{KEY}/meta.json'))['cy'])
JAW = None
SPRINGS = [(C['tail'][1:], 'tail', 70.0, 7.0)] + ([([C['crest']], 'crest', 90.0, 6.0)] if C['crest'] else [])
E = None
W = None
T = Matrix.Translation
def Sc(s): return Matrix.Diagonal((s, s, s, 1)) if not isinstance(s, tuple) else Matrix.Diagonal((*s, 1))
def Rz(d): return Matrix.Rotation(math.radians(d), 4, 'Z')
def Rx(d): return Matrix.Rotation(math.radians(d), 4, 'X')
def Ry(d): return Matrix.Rotation(math.radians(d), 4, 'Y')
def sinp(t, period, phase=0.0): return math.sin(2 * math.pi * (t / period + phase))
def tri(t, period): x = (t / period) % 1.0; return 1 - abs(2 * x - 1)
NECK, HEAD, TAIL = C['neck'], C['head'], C['tail']
LEGS = {'L': C['legL'], 'R': C['legR']}
PROPSZ = 2.2


def wings():
    global W
    if W is None: W = Wings(E, C)
    return W


class Ctx:
    def __init__(self, M):
        self.M = M; self.root = Vector(); self.squash = 0.0; self.turns = []; self.jaw = 0.0; self.spin = 0.0; self.roll = 0.0
        self.fold = {'L': 1.0, 'R': 1.0}; self.flap = {'L': 0.0, 'R': 0.0}; self.sweep = {'L': 0.0, 'R': 0.0}
        self.lift = {'L': 0.0, 'R': 0.0}; self.tip = {'L': 0.0, 'R': 0.0}; self._wings_done = False
    def turn(self, bone, axis, deg):
        if bone and abs(deg) > 1e-4: self.turns.append((bone, rq(axis, deg)))
    # --- body ------------------------------------------------------------------------------------
    def body(self, pitch=0.0, yaw=0.0, roll=0.0):
        """Leans the whole torso about the hips; the legs keep their world angle so the feet stay planted."""
        b = C['body']
        self.turn(b, 'X', pitch); self.turn(b, 'Z', yaw); self.turn(b, 'Y', roll)
        for s in 'LR':
            th = LEGS[s][0]; self.turn(th, 'X', -pitch); self.turn(th, 'Z', -yaw); self.turn(th, 'Y', -roll)
    def chest(self, pitch=0.0, yaw=0.0, roll=0.0):
        self.turn(C['chest'], 'X', pitch); self.turn(C['chest'], 'Z', yaw); self.turn(C['chest'], 'Y', roll)
    def head(self, pitch=0.0, yaw=0.0, roll=0.0):
        n = len(NECK); ks = [0.45 / n] * n + [0.55]
        for b, k in zip(NECK + [HEAD], ks):
            self.turn(b, 'Z', yaw * k); self.turn(b, 'X', pitch * k); self.turn(b, 'Y', roll * k)
    def neck_stretch(self, up=0.0):
        """Positive: neck long and tall (alert); negative: tucked into the shoulders."""
        if not NECK: return
        self.turn(NECK[0], 'X', -up * 0.6); self.turn(HEAD, 'X', up * 0.6)
    def tail(self, up=0.0, wag=0.0, fan=0.0):
        self.turn(TAIL[0], 'X', up * 0.6); self.turn(TAIL[0], 'Z', wag * 0.6)
        if len(TAIL) > 1: self.turn(TAIL[1], 'X', up * 0.4); self.turn(TAIL[1], 'Z', wag * 0.4)
    def crest(self, v):
        """Cockatiel crest: 1 = fully up (excited), 0 = flat back (calm / scared)."""
        if C['crest']: self.turn(C['crest'], 'X', (1.0 - v) * 38)
    def leg(self, side, lift=0.0, bend=0.0, out=0.0):
        th, sh, ta, ft = LEGS[side]
        self.turn(th, 'X', -lift); self.turn(sh, 'X', bend); self.turn(ta, 'X', -bend * 0.8)
        self.turn(ft, 'X', bend * 0.5 - lift * 0.3)
        if out: self.turn(th, 'Y', out if side == 'L' else -out)
    def crouch(self, k):
        """Bends both legs; the body drops (k 0..1)."""
        for s in 'LR': self.leg(s, 0.0, 34 * k)
        self.root.z -= 0.06 * k
    def tuck_legs(self, k):
        for s in 'LR': self.leg(s, -40 * k, 70 * k)
    # --- wings -----------------------------------------------------------------------------------
    def wings(self, fold=None, flap=0.0, sweep=0.0, side=None, lift=0.0, tip=0.0):
        """fold 1 = tucked on the back, 0 = fully spread; flap = degrees down(+)/up(-) about the body axis;
        sweep = degrees back(+)/forward(-); lift = raise a folded wing off the body; tip = bend the outer wing."""
        for s in ([side] if side else 'LR'):
            if fold is not None: self.fold[s] = fold
            self.flap[s] += flap; self.sweep[s] += sweep; self.lift[s] += lift; self.tip[s] += tip
    def flapping(self, t, period=0.36, amp=95.0, up=10.0, fold=0.0, phase=0.0):
        """A wingbeat cycle: fast downstroke, slower upstroke with the outer wing trailing."""
        x = (t / period + phase) % 1.0
        d = ss(x / 0.45) if x < 0.45 else 1 - ss((x - 0.45) / 0.55)     # 0 top .. 1 bottom
        self.wings(fold=fold, flap=-up + amp * d, tip=-25 * (1 - d) + 10 * d)
        self.root.z += 0.035 * (0.5 - d)
        return d
    def finish(self):
        if self._wings_done: return
        self._wings_done = True; Wg = wings()
        for s, sx in (('L', 1), ('R', -1)):
            ch = C['wing' + s]
            k = max(0.0, min(1.0, self.fold[s]))
            q = slerp_id(Wg.fold[s], k)
            # lift a folded wing away from the body: rotate about the body's long axis
            if self.lift[s]: q = Quaternion((0, 1, 0), math.radians(-self.lift[s] * sx)) @ q
            if self.flap[s]: q = Quaternion((0, 1, 0), math.radians(self.flap[s] * sx)) @ q
            if self.sweep[s]: q = Quaternion((0, 0, 1), math.radians(self.sweep[s] * sx)) @ q
            self.turns.append((ch[0], q))
            # a folded wing's feather fan closes: squeeze the outer wing across its width
            if not hasattr(self, 'scales'): self.scales = []
            nz = 1.0 - 0.55 * k * max(0.0, 1.0 - abs(self.lift[s]) / 25.0)
            self.scales.append((ch[1], (1.0 - 0.25 * k, 1.0, nz)))
            self.scales.append((ch[0], (1.0, 1.0, 1.0 - 0.25 * k)))
            if self.tip[s]:
                for b in ch[2:]: self.turns.append((b, Quaternion((0, 1, 0), math.radians(self.tip[s] * sx / max(1, len(ch) - 2)))))


class Clip:
    loop = False; lock = 'lock'; springs = True; group = 'life'
    def __init__(self, name, secs, loop=None, seed=None):
        self.name = name
        if loop is not None: self.loop = loop
        self.secs = secs; self.n = int(round(secs * FPS))
        self.face = Face(secs, seed=seed if seed is not None else sum(map(ord, name)), loop=self.loop, blinks=False, saccades=False)
    def base(self, E, t): return E.rest_pose()
    def act(self, t, c):
        self.pose(t, c); c.finish()
    def pose(self, t, c): pass
    def props(self, t, M): return {}


def breathe(c, t, k=1.0):
    b = sinp(t, 2.4)
    c.squash += 0.012 * b * k; c.chest(pitch=-1.5 * b * k); c.tail(up=2 * b * k)


def alive(c, t, seed=0, k=1.0):
    """Small, constant bird twitchiness: quick head flicks between holds."""
    rnd = random.Random(seed); y = p = r = 0.0; tt = 0.0; holds = []
    while tt < 30:
        holds.append((tt, rnd.uniform(-28, 28), rnd.uniform(-10, 8), rnd.uniform(-12, 12) if rnd.random() < 0.3 else 0.0))
        tt += rnd.uniform(0.6, 1.5)
    for (t0, a, b, d), (t1, *_ ) in zip(holds, holds[1:]):
        if t0 <= t < t1:
            prev = holds[holds.index((t0, a, b, d)) - 1] if holds.index((t0, a, b, d)) > 0 else (0, 0, 0, 0)
            w = ss((t - t0) / 0.09)
            y = prev[1] + (a - prev[1]) * w; p = prev[2] + (b - prev[2]) * w; r = prev[3] + (d - prev[3]) * w
            break
    c.head(pitch=p * k, yaw=y * k, roll=r * k)


def seed_at(i, x=0.0, y=-0.55):
    return T((x + 0.05 * i, y - 0.04 * (i % 2), 0.01)) @ Rz(40 * i) @ Sc(PROPSZ)


# =================================================================================== everyday
class Idle(Clip):
    loop = True; group = 'idle'
    def __init__(self): super().__init__('Idle', 7.2, loop=True)
    def pose(self, t, c):
        breathe(c, t); alive(c, t, 3, 0.8); c.crest(0.55 + 0.1 * sinp(t, 3.6))
        c.tail(wag=3 * sinp(t, 3.6), up=4 * env(t, 2.0, 2.05, 2.1, 2.3))
        k = env(t, 4.0, 4.15, 4.4, 4.6)              # a tiny wing shuffle
        c.wings(lift=6 * k)
        # weight shifts and little steps: birds never stand on locked legs
        sh = sinp(t, 7.2); c.body(roll=4 * sh); c.leg('L', lift=0, bend=6 * max(0.0, sh)); c.leg('R', lift=0, bend=6 * max(0.0, -sh))
        for side, a in (('L', 1.0), ('R', 1.5), ('L', 5.2)):
            st = env(t, a, a + 0.12, a + 0.2, a + 0.35); c.leg(side, lift=22 * st, bend=35 * st)
            c.body(roll=(-5 if side == 'L' else 5) * st)
        c.crouch(0.08 + 0.05 * sinp(t, 2.4))


class LookAround(Clip):
    def __init__(self): super().__init__('LookAround', 4.0)
    def pose(self, t, c):
        breathe(c, t); c.crest(0.7)
        y = 60 * env(t, 0.3, 0.45, 1.2, 1.35) - 60 * env(t, 1.6, 1.75, 2.6, 2.75)
        c.head(yaw=y, pitch=-8 * env(t, 0.3, 0.5, 2.6, 2.9), roll=14 * env(t, 3.0, 3.2, 3.6, 3.9))
        c.neck_stretch(10 * env(t, 0.2, 0.5, 3.0, 3.4))


class HeadTilt(Clip):
    """The parrot upside-down head turn, 'what IS that?'."""
    def __init__(self): super().__init__('HeadTilt', 3.2)
    def pose(self, t, c):
        breathe(c, t); c.crest(0.9)
        k = env(t, 0.2, 0.5, 1.4, 1.7); k2 = env(t, 1.7, 1.95, 2.6, 2.9)
        c.head(roll=75 * k - 50 * k2, yaw=12 * k - 10 * k2, pitch=-10 * (k + k2))


class HeadBob(Clip):
    """Excited budgie bobbing: fast nods with a pumping body."""
    def __init__(self): super().__init__('HeadBob', 2.6)
    def pose(self, t, c):
        k = env(t, 0.1, 0.3, 2.2, 2.5); b = tri(t, 0.22)
        c.head(pitch=26 * b * k); c.body(pitch=6 * b * k); c.crouch(0.25 * b * k); c.crest(0.6 + 0.4 * k)
        c.tail(up=6 * b * k)


class Preen(Clip):
    """Beak into the wing feathers, nibbling, then a fluff."""
    def __init__(self): super().__init__('Preen', 4.6)
    def pose(self, t, c):
        breathe(c, t)
        k = env(t, 0.2, 0.6, 3.3, 3.7); n = sinp(t, 0.25) * env(t, 0.7, 0.9, 3.1, 3.3)
        c.head(yaw=110 * k, pitch=38 * k + 6 * n, roll=-20 * k)
        c.wings(side='L', lift=14 * k)
        c.body(roll=-6 * k, pitch=4 * k)
        f = env(t, 3.7, 3.85, 4.1, 4.5); c.squash += 0.08 * f; c.wings(lift=10 * f); c.crest(0.4 + 0.5 * f)
        c.body(roll=6 * f * sinp(t, 0.12))
    def props(self, t, M):
        if not (2.2 < t < 4.4): return {'Feather': None}
        k = (t - 2.2) / 2.2; p = M[C['chest']].translation
        return {'Feather': T((p.x + 0.35 + 0.2 * k, p.y - 0.1, max(0.02, p.z + 0.2 - 0.6 * k))) @ Ry(40 * math.sin(k * 9)) @ Rz(90 * k) @ Sc(PROPSZ)}


class PreenTail(Clip):
    def __init__(self): super().__init__('PreenTail', 3.6)
    def pose(self, t, c):
        breathe(c, t)
        k = env(t, 0.2, 0.6, 2.8, 3.2); n = sinp(t, 0.3) * env(t, 0.7, 0.9, 2.6, 2.8)
        c.head(yaw=-150 * k, pitch=30 * k + 5 * n, roll=25 * k)
        c.body(yaw=-25 * k); c.tail(wag=-25 * k, up=10 * k)


class Scratch(Clip):
    """Lifts a foot over the wing and scratches its head, eyes half shut in bliss."""
    def __init__(self): super().__init__('Scratch', 3.4)
    def pose(self, t, c):
        k = env(t, 0.3, 0.7, 2.6, 3.0); s = sinp(t, 0.11) * env(t, 0.8, 0.9, 2.4, 2.5)
        c.body(roll=12 * k, pitch=10 * k)
        c.leg('L', lift=135 * k + 10 * s, bend=30 * k, out=30 * k)
        c.wings(side='L', lift=10 * k)
        c.head(roll=35 * k, pitch=45 * k, yaw=25 * k + 5 * s)


class Fluff(Clip):
    """Puffs up into a ball, shakes every feather, sleeks down again."""
    def __init__(self): super().__init__('Fluff', 2.4)
    def pose(self, t, c):
        k = env(t, 0.1, 0.45, 1.2, 1.7); s = sinp(t, 0.09) * env(t, 0.5, 0.6, 1.1, 1.3)
        c.squash += 0.14 * k; c.wings(lift=16 * k); c.tail(fan=1, up=10 * k)
        c.body(roll=7 * s); c.head(roll=12 * s, pitch=-8 * k); c.crest(0.3 + 0.7 * k)
        c.neck_stretch(-14 * k)


class Shake(Clip):
    """Wet-bird shake: head first, then the whole body, wings flicked."""
    def __init__(self): super().__init__('Shake', 2.0)
    def pose(self, t, c):
        h = sinp(t, 0.08) * env(t, 0.1, 0.2, 0.6, 0.7); b = sinp(t, 0.1) * env(t, 0.7, 0.8, 1.4, 1.6)
        c.head(yaw=35 * h, roll=20 * h); c.body(roll=12 * b, yaw=8 * b)
        c.wings(lift=20 * abs(b)); c.tail(wag=18 * b); c.squash += 0.06 * abs(b)
    def props(self, t, M):
        if not (0.7 < t < 1.6): return {'Feather': None}
        k = (t - 0.7) / 0.9; p = M[C['chest']].translation
        return {'Feather': T((p.x - 0.4 * k, p.y - 0.2, p.z + 0.15 - 0.4 * k)) @ Rz(300 * k) @ Sc(PROPSZ)}


class Stretch(Clip):
    """The parrot 'mantle stretch': one wing and the same-side leg pushed down and back, then the other."""
    def __init__(self): super().__init__('Stretch', 4.6)
    def pose(self, t, c):
        for s, a, b in (('L', 0.2, 1.9), ('R', 2.3, 4.2)):
            k = env(t, a, a + 0.5, b - 0.5, b)
            sx = 1 if s == 'L' else -1
            c.wings(side=s, fold=1 - 0.75 * k, flap=40 * k, sweep=55 * k, tip=10 * k)
            c.leg(s, lift=-35 * k, bend=-10 * k)
            c.body(roll=-8 * k * sx); c.head(yaw=-25 * k * sx, roll=6 * k * sx)
            c.tail(wag=-16 * k * sx, fan=k)


class WingStretch(Clip):
    """Both wings lifted high over the back, a little shiver, folded neatly."""
    def __init__(self): super().__init__('WingStretch', 3.0)
    def pose(self, t, c):
        k = env(t, 0.2, 0.7, 1.9, 2.5); s = sinp(t, 0.1) * env(t, 0.9, 1.0, 1.7, 1.9)
        c.wings(fold=1 - 0.9 * k, flap=-35 * k + 4 * s, sweep=10 * k)
        c.body(pitch=12 * k); c.head(pitch=15 * k); c.crest(0.5 + 0.5 * k); c.tail(up=-8 * k)


class Yawn(Clip):
    def __init__(self): super().__init__('Yawn', 2.6)
    def pose(self, t, c):
        k = env(t, 0.2, 0.7, 1.4, 1.9)
        c.head(pitch=-30 * k, roll=10 * k); c.neck_stretch(18 * k); c.squash -= 0.04 * k
        c.wings(lift=6 * k); c.crest(0.3 + 0.6 * k)
        c.body(roll=6 * sinp(t, 0.4) * env(t, 1.6, 1.8, 2.2, 2.5))


class Peck(Clip):
    """Pecks at the ground: one curious peck, a look, then two quick ones."""
    def __init__(self): super().__init__('Peck', 3.0)
    def pose(self, t, c):
        p = env(t, 0.2, 0.4, 0.5, 0.75) + env(t, 1.6, 1.72, 1.78, 1.95) + env(t, 2.05, 2.17, 2.23, 2.45)
        c.body(pitch=38 * p); c.head(pitch=45 * p); c.tail(up=18 * p)
        c.head(roll=20 * env(t, 0.9, 1.1, 1.3, 1.5))


class Eat(Clip):
    """Three seeds roll in; it cracks each one, husks flying."""
    def __init__(self): super().__init__('Eat', 5.6)
    def pose(self, t, c):
        c.crest(0.8)
        for i in range(3):
            a = 0.6 + i * 1.6
            p = env(t, a, a + 0.2, a + 0.35, a + 0.6)
            c.body(pitch=40 * p); c.head(pitch=50 * p, yaw=(i - 1) * 12 * p)
            ch = env(t, a + 0.6, a + 0.7, a + 1.3, a + 1.4)
            c.head(roll=8 * ch * sinp(t, 0.12), pitch=-6 * ch)
        c.tail(wag=4 * sinp(t, 0.5))
    def props(self, t, M):
        out = {}
        for i in range(3):
            a = 0.6 + i * 1.6
            out[f'Crumb{i}'] = seed_at(i - 1, 0.0, -0.62) if t < a + 0.35 else None
        return out


class Chirp(Clip):
    """Sings: throat pumping, head thrown up, crest bouncing."""
    def __init__(self): super().__init__('Chirp', 3.0)
    def pose(self, t, c):
        k = env(t, 0.1, 0.3, 2.6, 2.9); n = abs(sinp(t, 0.36))
        c.head(pitch=-22 * k - 6 * n * k, roll=8 * sinp(t, 1.2) * k); c.neck_stretch(12 * k)
        c.squash += 0.03 * n * k; c.crest(0.6 + 0.4 * n * k); c.tail(wag=6 * sinp(t, 0.36) * k)


class Talk(Clip):
    """Budgie chatter: bobbing, head swings, beak at you, very serious about it."""
    def __init__(self): super().__init__('Talk', 4.0)
    def pose(self, t, c):
        k = env(t, 0.1, 0.3, 3.6, 3.9)
        c.head(pitch=12 * tri(t, 0.3) * k - 8 * k, yaw=22 * sinp(t, 1.6) * k, roll=18 * sinp(t, 2.0) * k)
        c.body(pitch=5 * tri(t, 0.3) * k); c.wings(lift=6 * abs(sinp(t, 0.6)) * k); c.crest(0.75 + 0.25 * tri(t, 0.6))


class Notice(Clip):
    """Something caught its eye: freezes tall, sleek, then relaxes."""
    def __init__(self): super().__init__('Notice', 2.4)
    def pose(self, t, c):
        k = env(t, 0.05, 0.2, 1.5, 2.1)
        c.neck_stretch(22 * k); c.squash -= 0.07 * k; c.crest(0.5 + 0.5 * k); c.head(yaw=30 * k, pitch=-10 * k)
        c.tail(up=-8 * k)


# =================================================================================== moving
class Walk(Clip):
    """Parrot waddle: side to side, one foot at a time, head bobbing."""
    loop = True; group = 'walk'
    def __init__(self): super().__init__('Walk', 0.8 * 3, loop=True)
    def pose(self, t, c):
        ph = (t / 0.8) % 1.0; sw = math.sin(2 * math.pi * ph)
        lL = max(0.0, math.sin(2 * math.pi * ph)); lR = max(0.0, -math.sin(2 * math.pi * ph))
        c.leg('L', lift=45 * lL, bend=55 * lL); c.leg('R', lift=45 * lR, bend=55 * lR)
        c.body(roll=11 * sw, yaw=7 * sw); c.head(roll=-6 * sw, pitch=8 * abs(sw)); c.tail(wag=-8 * sw)
        c.root.z += 0.012 * abs(sw)


class Hop(Clip):
    """One hop forward: crouch, jump with a half-open wing flick, land."""
    def __init__(self): super().__init__('Hop', 1.0)
    def pose(self, t, c):
        cr = env(t, 0.0, 0.18, 0.22, 0.3) + env(t, 0.68, 0.78, 0.82, 1.0)
        air = env(t, 0.26, 0.4, 0.55, 0.7)
        c.crouch(0.8 * cr); c.root.z += 0.22 * math.sin(math.pi * max(0, min(1, (t - 0.26) / 0.44))) if 0.26 < t < 0.7 else 0.0
        c.tuck_legs(0.5 * air); c.wings(fold=1 - 0.45 * air, flap=10 * air); c.tail(up=-15 * air + 10 * cr)
        c.body(pitch=-10 * air + 10 * cr)


class Run(Clip):
    """Fast hopping run (birds don't jog)."""
    loop = True; group = 'walk'
    def __init__(self): super().__init__('Run', 0.42 * 4, loop=True)
    def pose(self, t, c):
        ph = (t / 0.42) % 1.0; up = math.sin(math.pi * ph)
        c.root.z += 0.12 * up; c.crouch(0.5 * (1 - up)); c.tuck_legs(0.3 * up)
        c.wings(fold=1 - 0.25 * up, flap=8 * up); c.body(pitch=-12); c.tail(up=-10 * up); c.head(pitch=-6 + 8 * up)


class TakeOff(Clip):
    """Crouch, wings open, two powerful beats and it's up."""
    def __init__(self): super().__init__('TakeOff', 1.2)
    def pose(self, t, c):
        cr = env(t, 0.0, 0.25, 0.3, 0.45); o = ss((t - 0.15) / 0.3)
        c.crouch(0.9 * cr); c.body(pitch=-10 * o + 15 * cr)
        if t > 0.35: c.flapping(t - 0.35, 0.38, 60, 12, fold=0.0)
        else: c.wings(fold=1 - o, flap=-20 * o)
        c.root.z += 0.25 * ss((t - 0.4) / 0.8); c.tuck_legs(ss((t - 0.5) / 0.5)); c.tail(fan=1, up=-10 * o)


class Fly(Clip):
    loop = True; group = 'fly'
    def __init__(self): super().__init__('Fly', 0.38 * 4, loop=True)
    def pose(self, t, c):
        d = c.flapping(t, 0.38, 100, 12); c.tuck_legs(1.0); c.body(pitch=-14 + 3 * d); c.head(pitch=10)
        c.tail(up=-8 + 4 * d)


class Hover(Clip):
    """Hovering in place: fast, shallow beats, body upright, tail fanned."""
    loop = True; group = 'fly'
    def __init__(self): super().__init__('Hover', 0.26 * 6, loop=True)
    def pose(self, t, c):
        c.flapping(t, 0.26, 45, 30); c.tuck_legs(0.6); c.body(pitch=12); c.head(pitch=-12); c.tail(up=-12, fan=1)


class Glide(Clip):
    loop = True; group = 'fly'
    def __init__(self): super().__init__('Glide', 3.2, loop=True)
    def pose(self, t, c):
        c.wings(fold=0.0, flap=8 + 4 * sinp(t, 3.2), tip=6 * sinp(t, 1.6)); c.tuck_legs(1.0)
        c.body(pitch=-10, roll=8 * sinp(t, 3.2)); c.head(roll=-6 * sinp(t, 3.2)); c.root.z += 0.03 * sinp(t, 1.6)


class Land(Clip):
    def __init__(self): super().__init__('Land', 1.1)
    def pose(self, t, c):
        k = ss(t / 0.55); o = 1 - ss((t - 0.55) / 0.4)
        if t < 0.55: c.flapping(t, 0.27, 45, 25, fold=0.0)
        else: c.wings(fold=1 - o, flap=-15 * o)
        c.tuck_legs(1 - k); c.body(pitch=18 * (1 - k)); c.root.z += 0.2 * (1 - k)
        c.crouch(0.8 * env(t, 0.5, 0.6, 0.7, 1.0)); c.tail(fan=1, up=12 * o)


# =================================================================================== touch
class Poke(Clip):
    group = 'touch'
    def __init__(self): super().__init__('Poke', 1.3)
    def pose(self, t, c):
        k = env(t, 0.0, 0.05, 0.25, 0.9)
        c.squash -= 0.1 * k; c.neck_stretch(20 * k); c.wings(fold=1 - 0.35 * k, flap=-15 * k); c.crest(0.4 + 0.6 * k)
        c.root.z += 0.08 * env(t, 0.0, 0.08, 0.12, 0.35); c.body(pitch=-8 * k); c.head(pitch=-12 * k)
        f = env(t, 0.7, 0.85, 1.0, 1.3); c.squash += 0.08 * f; c.body(roll=6 * f * sinp(t, 0.1))


class Pet(Clip):
    """Leans into your finger, head bowed for scritches, feathers fluffed, eyes melting."""
    loop = True; group = 'touch'
    def __init__(self): super().__init__('Pet', 3.2, loop=True)
    def pose(self, t, c):
        k = 1.0
        c.head(pitch=40 * k, roll=18 * sinp(t, 3.2), yaw=12 * sinp(t, 1.6)); c.squash += 0.08 * k
        c.wings(lift=8 * k); c.crest(0.25); c.body(pitch=10 * k, roll=4 * sinp(t, 3.2))
    def props(self, t, M):
        ph = (t / 1.6) % 1.0; h = M[HEAD].translation
        return {'Heart': T((h.x + 0.25, h.y - 0.1, h.z + 0.25 + 0.3 * ph)) @ Sc(PROPSZ * ss(ph / 0.2) * (1 - ss((ph - 0.7) / 0.3)) + 1e-4)}


class Tickle(Clip):
    group = 'touch'
    def __init__(self): super().__init__('Tickle', 2.2)
    def pose(self, t, c):
        k = env(t, 0.0, 0.1, 1.8, 2.2); w = sinp(t, 0.14)
        c.body(roll=10 * w * k, pitch=-4 * k); c.head(roll=-18 * w * k, pitch=-14 * k)
        c.wings(lift=18 * abs(w) * k); c.squash += 0.05 * abs(w) * k; c.crest(1.0); c.tail(wag=15 * w * k)
        c.crouch(0.2 * abs(w) * k)


class Nibble(Clip):
    """Gently beaks your finger: lunge, nibble nibble, look."""
    group = 'touch'
    def __init__(self): super().__init__('Nibble', 2.0)
    def pose(self, t, c):
        k = env(t, 0.1, 0.35, 1.3, 1.6); n = sinp(t, 0.16) * env(t, 0.4, 0.5, 1.2, 1.3)
        c.body(pitch=14 * k); c.head(pitch=-20 * k + 8 * n, yaw=6 * n); c.neck_stretch(16 * k); c.crest(0.8)


class Scared(Clip):
    """Sleek, tall and thin, crest flat, ready to bolt; little nervous steps back."""
    group = 'touch'
    def __init__(self): super().__init__('Scared', 2.4)
    def pose(self, t, c):
        k = env(t, 0.0, 0.12, 1.8, 2.4)
        c.squash -= 0.12 * k; c.neck_stretch(25 * k); c.crest(1.0 - 0.9 * k); c.wings(lift=-4 * k)
        c.body(pitch=-12 * k); c.head(yaw=35 * sinp(t, 0.6) * k)
        step = env(t, 0.5, 0.6, 0.7, 0.8) + env(t, 0.9, 1.0, 1.1, 1.2)
        c.leg('L', lift=20 * env(t, 0.5, 0.6, 0.7, 0.8), bend=20 * env(t, 0.5, 0.6, 0.7, 0.8))
        c.leg('R', lift=20 * env(t, 0.9, 1.0, 1.1, 1.2), bend=20 * env(t, 0.9, 1.0, 1.1, 1.2))
        c.root.y += 0.08 * ss((t - 0.5) / 0.7) * (1 - ss((t - 1.8) / 0.6)); c.tail(up=-6 * step)


class Angry(Clip):
    """Poked too many times: wings out, crouched, beak open and lunging, hissing."""
    group = 'touch'
    def __init__(self): super().__init__('Angry', 2.6)
    def pose(self, t, c):
        k = env(t, 0.0, 0.2, 2.1, 2.6); l = env(t, 0.9, 1.0, 1.1, 1.35) + env(t, 1.5, 1.6, 1.7, 1.95)
        c.wings(fold=1 - 0.45 * k, flap=25 * k, sweep=-10 * k); c.crouch(0.5 * k); c.squash += 0.1 * k
        c.body(pitch=18 * k + 15 * l); c.head(pitch=-10 * k + 10 * l); c.neck_stretch(15 * l); c.crest(1.0)
        c.tail(fan=1, up=12 * k, wag=10 * sinp(t, 0.2) * k)


class Dangle(Clip):
    """Carried by the finger: wings flapping wildly, legs hanging straight down and kicking."""
    loop = True; group = 'touch'
    def __init__(self): super().__init__('Dangle', 0.3 * 6, loop=True)
    def pose(self, t, c):
        c.flapping(t, 0.3, 75, 20); c.leg('L', lift=-25 + 12 * sinp(t, 0.6), bend=-10); c.leg('R', lift=-25 - 12 * sinp(t, 0.6), bend=-10)
        c.tail(up=-25)
        c.body(pitch=5 * sinp(t, 0.6)); c.head(yaw=15 * sinp(t, 0.9)); c.crest(1.0); c.tail(fan=1, wag=10 * sinp(t, 0.3))


class Tumble(Clip):
    """Dropped: a clumsy flapping fall, lands on its bottom, shakes it off."""
    def __init__(self): super().__init__('Tumble', 2.2)
    def pose(self, t, c):
        if t < 0.7: c.flapping(t, 0.2, 70, 30)
        else:
            k = env(t, 0.7, 0.8, 1.4, 1.9); c.crouch(0.9 * k); c.squash += 0.15 * k * (1 - ss((t - 0.8) / 0.4))
            c.wings(fold=1 - 0.5 * k, flap=30 * k); c.body(pitch=-20 * k)
        s = sinp(t, 0.1) * env(t, 1.6, 1.7, 2.0, 2.2); c.body(roll=12 * s); c.head(yaw=20 * s)
        c.spin = 30 * math.sin(t * 9) * (1 - ss(t / 0.7)) if t < 0.7 else 0.0


# =================================================================================== sleep
class FallAsleep(Clip):
    """Fluffs up, sinks onto one leg, turns its head back and tucks the beak into its back feathers."""
    def __init__(self): super().__init__('FallAsleep', 3.4)
    def pose(self, t, c):
        f = ss(t / 1.0); h = ss((t - 1.0) / 1.6); l = ss((t - 0.6) / 1.0)
        c.squash += 0.12 * f; c.crest(0.5 - 0.4 * f); c.wings(lift=6 * f)
        c.leg('L', lift=28 * l, bend=70 * l); c.crouch(0.35 * l)
        c.head(yaw=150 * h, pitch=35 * h, roll=-20 * h); c.neck_stretch(-15 * h)


class Sleep(Clip):
    loop = True; group = 'idle'
    def __init__(self): super().__init__('Sleep', 4.8, loop=True)
    def pose(self, t, c):
        b = sinp(t, 2.4)
        c.squash += 0.12 + 0.02 * b; c.crest(0.1); c.wings(lift=6 + 2 * b)
        c.leg('L', lift=28, bend=70); c.crouch(0.35); c.head(yaw=150, pitch=35 + 2 * b, roll=-20); c.neck_stretch(-15)
    def props(self, t, M):
        out = {}; top = M[HEAD].translation
        for i in range(3):
            ph = ((t / 4.8) * 2 + i / 3.0) % 1.0
            s = PROPSZ * ss(ph / 0.2) * (1 - ss((ph - 0.75) / 0.25)) * (0.8 + 0.25 * i)
            pos = top + Vector((0.2 + 0.25 * ph, -0.1, 0.25 + 0.4 * ph))
            out[f'Z{i}'] = T(pos) @ Ry(-15 + 25 * ph) @ Sc(max(1e-4, s)) if s > 0.01 else None
        return out


class WakeUp(Clip):
    def __init__(self): super().__init__('WakeUp', 2.8)
    def pose(self, t, c):
        h = 1 - ss((t - 0.2) / 0.7); l = 1 - ss((t - 0.8) / 0.6)
        c.squash += 0.12 * h; c.leg('L', lift=28 * l, bend=70 * l); c.crouch(0.35 * l)
        c.head(yaw=150 * h, pitch=35 * h, roll=-20 * h); c.neck_stretch(-15 * h)
        s = env(t, 1.4, 1.8, 2.1, 2.6); c.wings(fold=1 - 0.8 * s, flap=-30 * s); c.crest(0.2 + 0.6 * s)
        c.head(pitch=-15 * s)


# =================================================================================== music
class DanceBob(Clip):
    """Head-banging to the beat (cockatiels are famous for it)."""
    def __init__(self): super().__init__('DanceBob', 4.0)
    def pose(self, t, c):
        b = tri(t, 0.5); c.head(pitch=30 * b - 10); c.body(pitch=8 * b); c.crouch(0.3 * b)
        c.crest(0.5 + 0.5 * b); c.tail(up=10 * b); c.body(roll=6 * sinp(t, 2.0))


class DanceWings(Clip):
    """Wing pumping: wings flash open on every beat."""
    def __init__(self): super().__init__('DanceWings', 4.0)
    def pose(self, t, c):
        b = tri(t, 0.5); o = env((t % 1.0), 0.0, 0.12, 0.3, 0.5)
        c.wings(fold=1 - 0.7 * o, flap=-10 * o, sweep=-10 * o); c.head(pitch=20 * b - 8, roll=10 * sinp(t, 2.0))
        c.crouch(0.25 * b); c.crest(0.6 + 0.4 * o); c.tail(fan=o, up=6 * b)


class DanceSide(Clip):
    """Side-step shuffle, left two, right two, with a swaying body."""
    def __init__(self): super().__init__('DanceSide', 4.0)
    def pose(self, t, c):
        ph = (t / 0.5) % 1.0; s = 1 if (t // 1.0) % 2 == 0 else -1
        lL = math.sin(math.pi * ph) if int(t / 0.5) % 2 == 0 else 0.0; lR = math.sin(math.pi * ph) if int(t / 0.5) % 2 == 1 else 0.0
        c.leg('L', lift=25 * lL, bend=30 * lL); c.leg('R', lift=25 * lR, bend=30 * lR)
        c.root.x += 0.08 * s * ss(ph) - 0.04 * s; c.body(roll=12 * sinp(t, 1.0)); c.head(roll=-14 * sinp(t, 1.0), pitch=10 * tri(t, 0.5))
        c.tail(wag=-15 * sinp(t, 1.0)); c.crest(0.8)


class DanceSpin(Clip):
    """Turns a full circle with tiny hops, then a big wing flourish."""
    def __init__(self): super().__init__('DanceSpin', 3.6)
    def pose(self, t, c):
        k = ss(t / 2.4); h = abs(math.sin(t * math.pi / 0.3)) * (1 if t < 2.4 else 0)
        c.spin = 360 * k; c.root.z += 0.05 * h; c.crouch(0.2 * (1 - h) * (1 if t < 2.4 else 0))
        f = env(t, 2.4, 2.6, 3.1, 3.5); c.wings(fold=1 - 0.95 * f, flap=-25 * f); c.head(pitch=-20 * f); c.crest(0.6 + 0.4 * f)
        c.tail(fan=f, up=-10 * f)


class DanceHeadbang(Clip):
    """Full head-banger: big circles of the head, wings half open, stomping."""
    def __init__(self): super().__init__('DanceHeadbang', 4.0)
    def pose(self, t, c):
        a = 2 * math.pi * t / 0.5
        c.head(pitch=25 * math.sin(a), roll=20 * math.cos(a)); c.body(pitch=6 * math.sin(a))
        c.wings(fold=0.6, flap=10 * math.sin(a)); c.crest(0.6 + 0.4 * abs(math.sin(a)))
        st = max(0.0, math.sin(a / 2)); c.leg('L', lift=20 * st, bend=25 * st); c.leg('R', lift=20 * max(0.0, -math.sin(a / 2)), bend=25 * max(0.0, -math.sin(a / 2)))


# =================================================================================== charger
class Plugged(Clip):
    """Charger in: double take, crest up, a joyful flap-hop and a happy bob."""
    def __init__(self): super().__init__('Plugged', 2.8)
    def pose(self, t, c):
        k = env(t, 0.0, 0.1, 0.35, 0.5); c.neck_stretch(20 * k); c.squash -= 0.06 * k; c.crest(0.4 + 0.6 * ss(t / 0.2))
        h = env(t, 0.5, 0.6, 1.1, 1.3); c.root.z += 0.18 * math.sin(math.pi * max(0, min(1, (t - 0.55) / 0.6))) if 0.55 < t < 1.15 else 0.0
        if 0.5 < t < 1.3: c.flapping(t, 0.25, 50, 20, fold=0.2)
        c.tuck_legs(0.5 * h); b = tri(t, 0.3) * env(t, 1.4, 1.5, 2.5, 2.8); c.head(pitch=25 * b); c.crouch(0.25 * b)
    def props(self, t, M):
        if t < 1.1: return {'Heart': None}
        ph = (t - 1.1) / 1.7; h = M[HEAD].translation
        return {'Heart': T((h.x - 0.2, h.y - 0.1, h.z + 0.3 + 0.35 * ph)) @ Sc(PROPSZ * 1.2 * ss(ph / 0.2) * (1 - ss((ph - 0.7) / 0.3)) + 1e-4)}


class ChargeRest(Clip):
    """While charging: settled low and fluffed on one leg, content, slow blinks of the head."""
    loop = True; group = 'idle'
    def __init__(self): super().__init__('ChargeRest', 6.0, loop=True)
    def pose(self, t, c):
        b = sinp(t, 3.0)
        c.squash += 0.1 + 0.015 * b; c.crouch(0.3); c.leg('L', lift=20, bend=60); c.wings(lift=5 + 2 * b)
        c.head(pitch=12 + 3 * b, roll=10 * sinp(t, 6.0), yaw=15 * env(t, 2.0, 2.4, 3.4, 3.8)); c.crest(0.35)
        c.tail(up=2 * b)


class Tired(Clip):
    """Low battery: wings drooping off the back, head heavy, slow sway, yawns."""
    loop = True; group = 'idle'
    def __init__(self): super().__init__('Tired', 6.0, loop=True)
    def pose(self, t, c):
        s = sinp(t, 3.0)
        c.wings(fold=0.82, flap=18, sweep=8); c.head(pitch=30 + 6 * s, roll=10 * s); c.neck_stretch(-12)
        c.body(roll=5 * s, pitch=8); c.crouch(0.35); c.crest(0.15); c.tail(up=-10)
        y = env(t, 3.4, 3.8, 4.3, 4.8); c.head(pitch=-45 * y); c.neck_stretch(12 * y)


class Unplugged(Clip):
    """Charger out too early: startled flinch, looks at where the cable was, a little huff."""
    def __init__(self): super().__init__('Unplugged', 2.4)
    def pose(self, t, c):
        k = env(t, 0.0, 0.06, 0.3, 0.6); c.squash -= 0.08 * k; c.neck_stretch(18 * k); c.crest(0.4 + 0.6 * k)
        c.wings(fold=1 - 0.3 * k, flap=-10 * k)
        l = env(t, 0.6, 0.9, 1.5, 1.8); c.head(pitch=40 * l, yaw=25 * l, roll=15 * l); c.body(pitch=12 * l)
        f = env(t, 1.8, 1.95, 2.1, 2.4); c.squash += 0.1 * f; c.body(roll=6 * f * sinp(t, 0.1))


class FullCharge(Clip):
    """100%: victory! wings wide, a spin-hop and a big head bob."""
    def __init__(self): super().__init__('FullCharge', 3.6)
    def pose(self, t, c):
        o = env(t, 0.1, 0.4, 1.6, 2.0); c.wings(fold=1 - 0.95 * o, flap=-20 * o + 8 * sinp(t, 0.3) * o); c.crest(1.0)
        c.head(pitch=-20 * o); c.neck_stretch(15 * o); c.tail(fan=o, up=-10 * o)
        sp = ss((t - 0.6) / 1.0); c.spin = 360 * sp if t > 0.6 else 0.0
        c.root.z += 0.12 * abs(math.sin(math.pi * (t - 0.6) / 0.5)) if 0.6 < t < 1.6 else 0.0
        b = tri(t, 0.3) * env(t, 2.0, 2.1, 3.3, 3.6); c.head(pitch=25 * b); c.crouch(0.3 * b)
    def props(self, t, M):
        out = {}; h = M[HEAD].translation
        for i in range(3):
            a = 0.3 + 0.35 * i
            if t < a or t > a + 1.6: out[f'Z{i}'] = None; continue
            continue
        ph = ((t - 0.3) / 2.5) % 1.0
        out['Heart'] = T((h.x + 0.3 * math.sin(ph * 6), h.y - 0.15, h.z + 0.25 + 0.4 * ph)) @ Sc(PROPSZ * ss(ph / 0.2) * (1 - ss((ph - 0.7) / 0.3)) + 1e-4)
        return out


# =================================================================================== play
class ChaseButterfly(Clip):
    """Watches a butterfly circle, head tracking it, then lunges and it flies off."""
    def __init__(self): super().__init__('ChaseButterfly', 4.4)
    def bfly(self, t):
        a = t * 2.2
        if t < 3.2: return Vector((0.5 * math.cos(a), -0.55 + 0.25 * math.sin(a), 1.2 + 0.15 * math.sin(a * 2)))
        k = (t - 3.2) / 1.2; return Vector((0.5 * math.cos(a) + 0.6 * k, -0.55, 1.2 + 0.7 * k))
    def pose(self, t, c):
        p = self.bfly(t); yaw = math.degrees(math.atan2(p.x, -(p.y + 0.6)))
        k = env(t, 0.0, 0.3, 3.0, 3.6)
        c.head(yaw=max(-70, min(70, yaw)) * k, pitch=-25 * k, roll=0.4 * max(-70, min(70, yaw)) * k); c.crest(0.9)
        l = env(t, 2.9, 3.05, 3.2, 3.6); c.body(pitch=-20 * l); c.neck_stretch(25 * l); c.root.z += 0.15 * l
        c.wings(fold=1 - 0.5 * l, flap=-20 * l); c.tuck_legs(0.4 * l)
    def props(self, t, M):
        p = self.bfly(t); f = 70 * abs(math.sin(t * 25))
        return {'Butterfly': T(p) @ Rz(t * 120) @ Sc(PROPSZ), '_wings': math.radians(f)}


class Mirror(Clip):
    """Admires itself: head bobs, a kiss-peck, a proud fluff."""
    def __init__(self): super().__init__('Mirror', 3.8)
    def pose(self, t, c):
        b = tri(t, 0.35) * env(t, 0.1, 0.3, 1.5, 1.7); c.head(pitch=20 * b, roll=15 * sinp(t, 1.4))
        k = env(t, 1.8, 1.95, 2.1, 2.3); c.neck_stretch(20 * k); c.body(pitch=12 * k); c.head(pitch=-10 * k)
        f = env(t, 2.5, 2.8, 3.3, 3.8); c.squash += 0.1 * f; c.crest(0.6 + 0.4 * f); c.wings(lift=12 * f); c.head(pitch=-20 * f)
    def props(self, t, M):
        if not (2.0 < t < 3.3): return {'Heart': None}
        ph = (t - 2.0) / 1.3; h = M[HEAD].translation
        return {'Heart': T((h.x, h.y - 0.3, h.z + 0.15 + 0.3 * ph)) @ Sc(PROPSZ * ss(ph / 0.2) * (1 - ss((ph - 0.7) / 0.3)) + 1e-4)}


class Swing(Clip):
    """Rocks back and forth on its feet like on a perch swing, wings balancing."""
    def __init__(self): super().__init__('Swing', 4.0)
    def pose(self, t, c):
        s = sinp(t, 1.0) * env(t, 0.1, 0.5, 3.5, 4.0)
        c.body(pitch=14 * s); c.head(pitch=-12 * s); c.tail(up=-12 * s); c.wings(lift=6 * abs(s), flap=6 * s)


class Upside(Clip):
    """Silly parrot trick: leans way forward, head down between its feet, peeks at you upside down."""
    def __init__(self): super().__init__('Upside', 3.6)
    def pose(self, t, c):
        k = env(t, 0.2, 0.8, 2.6, 3.2)
        c.body(pitch=55 * k); c.head(pitch=70 * k, roll=40 * k * sinp(t, 2.4)); c.tail(up=35 * k); c.crouch(0.3 * k)
        c.wings(fold=1 - 0.25 * k, flap=10 * k); c.crest(0.8)


def library():
    return [Idle(), LookAround(), HeadTilt(), HeadBob(), Preen(), PreenTail(), Scratch(), Fluff(), Shake(), Stretch(), WingStretch(),
            Yawn(), Peck(), Eat(), Chirp(), Talk(), Notice(),
            Walk(), Hop(), Run(), TakeOff(), Fly(), Hover(), Glide(), Land(),
            Poke(), Pet(), Tickle(), Nibble(), Scared(), Angry(), Dangle(), Tumble(),
            FallAsleep(), Sleep(), WakeUp(),
            DanceBob(), DanceWings(), DanceSide(), DanceSpin(), DanceHeadbang(),
            Plugged(), ChargeRest(), Tired(), Unplugged(), FullCharge(),
            ChaseButterfly(), Mirror(), Swing(), Upside()]
