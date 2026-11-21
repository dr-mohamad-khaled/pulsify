"""Leo the lion cub: Meshy cat/leopard motions + lots of hand-made acting. Personality: a tiny lion who is SURE
he is the king. His roars come out as squeaks, he stalks butterflies and loses, and he loves belly rubs."""
import math, sys
sys.path.insert(0, '/home/claude/duck2/scripts')
from mathutils import Vector, Matrix
from engine2 import rq, ss, env, Face, FPS

BLEND = '/home/claude/cub/cub.props.blend'
OUT = '/home/claude/cub/baked'
MOVER = 'mover'; HIPS = 'hips'; HIPS_TARGET = (0.0, 0.15)
JAW = ('jaw_02', 'X', 28.0)
SPRINGS = [(['tail2', 'tail3', 'tail4', 'tail_06'], 'tail', 50.0, 5.0), (['ear.L_01'], 'earL', 170.0, 11.0), (['ear.R_01'], 'earR', 170.0, 11.0)]
E = None
STAND = ('cat_sajiao', 1)
SIT = ('dog_beifumo', 100)
T = Matrix.Translation
def Sc(s): return Matrix.Diagonal((s, s, s, 1)) if not isinstance(s, tuple) else Matrix.Diagonal((*s, 1))
def Rz(d): return Matrix.Rotation(math.radians(d), 4, 'Z')
def Rx(d): return Matrix.Rotation(math.radians(d), 4, 'X')
def sinp(t, period, phase=0.0): return math.sin(2 * math.pi * (t / period + phase))
def tri(t, period): x = (t / period) % 1.0; return 1 - abs(2 * x - 1)
PS = 2.6      # prop scale


class Ctx:
    def __init__(self, M):
        self.M = M; self.root = Vector(); self.squash = 0.0; self.turns = []; self.jaw = 0.0; self.spin = 0.0; self.roll = 0.0
    def turn(self, bone, axis, deg):
        if abs(deg) > 1e-4: self.turns.append((bone, rq(axis, deg)))
    def body(self, pitch=0.0, yaw=0.0, roll=0.0):
        for b in ('spine2', 'chest'):
            self.turn(b, 'X', pitch / 2); self.turn(b, 'Z', yaw / 2); self.turn(b, 'Y', roll / 2)
    def head(self, pitch=0.0, yaw=0.0, roll=0.0):
        for b, k in (('neck1', 0.45), ('head', 0.55)):
            self.turn(b, 'Z', yaw * k); self.turn(b, 'X', pitch * k); self.turn(b, 'Y', roll * k)
    def front(self, side, swing=0.0, bend=0.0):
        self.turn(f'frontUpr.{side}', 'X', -swing); self.turn(f'frontLwr.{side}', 'X', bend)
    def rear(self, side, swing=0.0, bend=0.0):
        self.turn(f'rearUpr.{side}', 'X', -swing); self.turn(f'rearLwr.{side}', 'X', -bend)
    def tail(self, wag=0.0, up=0.0):
        self.turn('tail1', 'Z', wag); self.turn('tail1', 'X', -up); self.turn('tail2', 'Z', wag * 0.6); self.turn('tail3', 'Z', wag * 0.4)
    def ears(self, back=0.0):
        # the ear bones also carry the mane sides: keep it gentle
        self.turn('ear.L_01', 'X', -back * 0.35); self.turn('ear.R_01', 'X', -back * 0.35)
    def mane(self, puff):
        self.turn('head_05', 'X', -10 * puff); self.turn('head_06', 'X', -6 * puff)


class Clip:
    loop = False; lock = 'lock'; springs = True; group = 'life'
    def __init__(self, name, secs, src=None, rng=None, speed=1.0, loop=None):
        self.name = name; self.src = src; self.rng = rng; self.speed = speed
        if loop is not None: self.loop = loop
        self.secs = secs; self.n = int(round(secs * FPS))
        self.face = Face(secs, seed=sum(map(ord, name)), loop=self.loop, blinks=False, saccades=False)
    def base(self, E, t):
        if self.src is None: return E.sample(*STAND)
        if isinstance(self.src, tuple): return E.sample(*self.src)
        s, e = self.rng if self.rng else E.frames(self.src)
        f = s + t * FPS * self.speed
        if self.loop: f = s + (f - s) % max(1, (e - s))
        return E.sample(self.src, min(f, e))
    def act(self, t, c): pass
    def props(self, t, M): return {}


def breathe(c, t, k=1.0):
    b = sinp(t, 2.6); c.squash += 0.01 * b * k; c.body(pitch=-1.2 * b * k)


def lie(c, k, b=0.0):
    """Sphinx: front legs forward on the floor, hind legs folded under, body down."""
    c.root.z -= 0.2 * k
    c.front('L', 40 * k, -18 * k); c.front('R', 40 * k, -18 * k)
    c.rear('L', 30 * k, 55 * k); c.rear('R', 30 * k, 55 * k)
    c.body(pitch=4 * k + 1.5 * b); c.head(pitch=-8 * k); c.squash += 0.02 * b


def mouth(F, t0, up, hold, down, v):
    F.pulse('jaw', t0, up, hold, down, v)


# ------------------------------------------------------------------ everyday
class Idle(Clip):
    loop = True; group = 'idle'
    def __init__(self): super().__init__('Idle', 7.2, loop=True)
    def act(self, t, c):
        breathe(c, t); c.tail(wag=10 * sinp(t, 2.4), up=10 + 6 * sinp(t, 3.6))
        c.head(yaw=18 * env(t, 1.2, 1.5, 2.6, 2.9) - 14 * env(t, 4.0, 4.3, 5.2, 5.5), roll=5 * sinp(t, 7.2))
        c.ears(back=6 * env(t, 3.2, 3.3, 3.4, 3.6) - 8 * env(t, 5.6, 5.65, 5.7, 5.85))
        # never frozen: weight shifting paw to paw, a paw lift, tail tip flicks, sniffing nose bobs
        sh = sinp(t, 7.2); c.body(roll=3 * sh, yaw=4 * sinp(t, 3.6)); c.turn('hips', 'Y', -2.5 * sh)
        p = env(t, 2.2, 2.35, 2.55, 2.75); c.front('L', 18 * p, 40 * p)
        c.tail(wag=14 * env(t, 0.6, 0.7, 0.8, 1.0) * sinp(t, 0.2)); c.head(pitch=4 * env(t % 2.4, 0.0, 0.1, 0.2, 0.3))


class LookAround(Clip):
    def __init__(self): super().__init__('LookAround', 4.0)
    def act(self, t, c):
        breathe(c, t)
        c.head(yaw=45 * env(t, 0.3, 0.6, 1.3, 1.6) - 45 * env(t, 1.8, 2.1, 2.8, 3.1), pitch=-10 * env(t, 3.1, 3.3, 3.6, 3.9))
        c.tail(wag=8 * sinp(t, 1.0))


class HeadTilt(Clip):
    def __init__(self): super().__init__('HeadTilt', 2.4)
    def act(self, t, c):
        k = env(t, 0.15, 0.45, 0.9, 1.1); k2 = env(t, 1.0, 1.3, 1.8, 2.2)
        c.head(roll=30 * k - 28 * k2, pitch=-8 * (k + k2)); c.ears(back=-6 * (k + k2)); c.tail(up=8)


class Yawn(Clip):
    def __init__(self):
        super().__init__('Yawn', 2.6); mouth(self.face, 0.4, 0.4, 0.6, 0.5, 1.15)
    def act(self, t, c):
        k = env(t, 0.3, 0.7, 1.3, 1.9); c.head(pitch=-25 * k, roll=8 * k); c.body(pitch=-4 * k); c.ears(back=10 * k)
        c.squash -= 0.03 * k


class Sneeze(Clip):
    def __init__(self):
        super().__init__('Sneeze', 1.8); mouth(self.face, 0.2, 0.3, 0.2, 0.1, 0.5); mouth(self.face, 0.9, 0.05, 0.05, 0.3, 0.8)
    def act(self, t, c):
        a = env(t, 0.2, 0.7, 0.8, 0.85); s = env(t, 0.85, 0.9, 0.95, 1.3)
        c.head(pitch=-20 * a + 30 * s); c.body(pitch=-5 * a + 8 * s); c.squash += 0.06 * s; c.root.y -= 0.04 * s
        w = env(t, 1.3, 1.4, 1.6, 1.8) * sinp(t, 0.08); c.head(yaw=20 * w)
    def props(self, t, M):
        if not (0.88 < t < 1.4): return {'Drops': None}
        k = (t - 0.88) / 0.52; p = M['head'].translation
        return {'Drops': T((p.x, p.y - 0.35 - 0.2 * k, p.z - 0.1)) @ Rx(90) @ Sc((0.6 + k, 0.6 + k, max(1e-4, 0.8 * (1 - k))))}


class Stretch(Clip):
    """Big cat stretch: play-bow with claws out, then the back legs one at a time."""
    def __init__(self): super().__init__('Stretch', 4.0)
    def act(self, t, c):
        k = env(t, 0.2, 0.7, 1.6, 2.0)
        c.front('L', 55 * k, -25 * k); c.front('R', 55 * k, -25 * k); c.body(pitch=18 * k); c.root.z -= 0.12 * k
        c.head(pitch=-15 * k); c.tail(up=35 * k)
        for side, a in (('L', 2.1), ('R', 2.9)):
            r = env(t, a, a + 0.25, a + 0.5, a + 0.8); c.rear(side, -45 * r, -20 * r); c.body(roll=(5 if side == 'L' else -5) * r)
        self.face['jaw'].keys((0.6, 0.0), (0.9, 0.6), (1.4, 0.6), (1.7, 0.0))


class Groom(Clip):
    """Licks a paw and washes his face, very seriously."""
    def __init__(self): super().__init__('Groom', 4.4)
    def act(self, t, c):
        k = env(t, 0.2, 0.6, 3.6, 4.1); l = sinp(t, 0.35) * env(t, 0.6, 0.8, 1.8, 2.0); w = sinp(t, 0.5) * env(t, 2.0, 2.2, 3.4, 3.6)
        c.front('L', 60 * k, 95 * k); c.head(pitch=22 * k + 6 * l + 12 * w, yaw=-10 * k, roll=-12 * w)
        c.body(pitch=-8 * k, roll=6 * k); c.jaw += 0.35 * k * max(0.0, l); c.tail(wag=6 * sinp(t, 1.4))


class Sit(Clip):
    loop = True; group = 'idle'
    def __init__(self): super().__init__('Sit', 5.2, SIT, loop=True)
    def act(self, t, c):
        breathe(c, t); c.head(roll=8 * sinp(t, 5.2, 0.2), yaw=14 * env(t, 1.0, 1.4, 2.4, 2.8)); c.tail(wag=8 * sinp(t, 2.6))


class SitDown(Clip):
    def __init__(self): super().__init__('SitDown', 1.4, 'dog_beifumo', rng=(1, 43))


class StandUp(Clip):
    def __init__(self): super().__init__('StandUp', 1.4, 'dog_beifumo', rng=(160, 201))


class LieDown(Clip):
    def __init__(self): super().__init__('LieDown', 1.2)
    def act(self, t, c): lie(c, ss(t / 1.1))


class RollOver(Clip):
    """Flops on his back, paws in the air, wiggles for a belly rub."""
    def __init__(self): super().__init__('RollOver', 3.8)
    def act(self, t, c):
        d = env(t, 0.2, 0.6, 3.0, 3.5); r = ss((t - 0.5) / 0.6) * (1 - ss((t - 2.8) / 0.6))
        lie(c, d); c.roll = 170 * r; c.root.z += 1.1 * r
        w = sinp(t, 0.4) * r
        c.front('L', 30 * r + 20 * w, 60 * r); c.front('R', 30 * r - 20 * w, 60 * r)
        c.rear('L', -30 * r, -30 * r); c.rear('R', -30 * r, -30 * r); c.head(yaw=20 * w, pitch=-20 * r); c.tail(wag=30 * w)
        self.face['jaw'].keys((1.0, 0.0), (1.3, 0.4), (2.6, 0.4), (2.9, 0.0))


class ChaseTail(Clip):
    """Notices his own tail, spins after it twice, falls over dizzy."""
    def __init__(self): super().__init__('ChaseTail', 3.6, 'cat_run')
    def base(self, E, t):
        if t < 0.5 or t > 2.6: return E.sample(*STAND)
        s, e = E.frames('cat_run'); return E.sample('cat_run', s + ((t - 0.5) * FPS) % (e - s))
    def act(self, t, c):
        n = env(t, 0.0, 0.2, 0.4, 0.5); c.head(yaw=60 * n, pitch=10 * n); c.tail(wag=-30 * n)
        if 0.5 < t < 2.6: c.spin = -720 * ss((t - 0.5) / 2.1); c.body(yaw=30); c.head(yaw=30); c.tail(wag=-40)
        d = env(t, 2.6, 2.8, 3.2, 3.6); c.body(roll=12 * d * sinp(t, 0.5)); c.head(roll=-18 * d * sinp(t, 0.5)); c.root.z -= 0.08 * d


class Pounce(Clip):
    """Crouch, butt wiggle, POUNCE (Meshy leopard lunge), proud look back."""
    def __init__(self): super().__init__('Pounce', 3.4)
    def base(self, E, t):
        if 1.1 < t < 1.7:
            s, e = E.frames('leopard_run_fight_forward'); return E.sample('leopard_run_fight_forward', s + (t - 1.1) / 0.6 * (e - s))
        if 1.7 <= t < 2.3:
            s, e = E.frames('leopard_jump_end'); return E.sample('leopard_jump_end', s + (t - 1.7) / 0.6 * (e - s))
        return E.sample(*STAND)
    def act(self, t, c):
        cr = env(t, 0.1, 0.4, 1.0, 1.1); w = sinp(t, 0.18) * env(t, 0.5, 0.6, 0.95, 1.05)
        c.root.z -= 0.15 * cr; c.front('L', 20 * cr, 40 * cr); c.front('R', 20 * cr, 40 * cr); c.rear('L', 20 * cr, 40 * cr); c.rear('R', 20 * cr, 40 * cr)
        c.turn('hips', 'Z', 12 * w); c.tail(wag=25 * w, up=-10 * cr); c.head(pitch=-6 * cr); c.ears(back=10 * cr)
        p = env(t, 2.4, 2.6, 3.0, 3.4); c.head(yaw=35 * p, pitch=-15 * p); c.mane(p)


class Stalk(Clip):
    """Low, slow, deadly serious hunting walk (the app moves him)."""
    loop = True; group = 'walk'
    def __init__(self): super().__init__('Stalk', 35 / FPS * 1.6 * 3, 'cat_walk', speed=0.62, loop=True)
    def act(self, t, c):
        c.root.z -= 0.13; c.front('L', 12, 30); c.front('R', 12, 30); c.rear('L', 12, 30); c.rear('R', 12, 30)
        c.head(pitch=-4); c.ears(back=8); c.tail(up=-12, wag=8 * sinp(t, 1.2))


class TinyRoar(Clip):
    """Rears up for the king's roar (Meshy roar), mane puffed... a squeak comes out. He looks around, embarrassed."""
    def __init__(self):
        super().__init__('TinyRoar', 4.4)
        self.face['jaw'].keys((0.6, 0.0), (0.9, 1.15), (1.9, 1.15), (2.2, 0.0), (3.0, 0.0), (3.15, 0.35), (3.3, 0.0))
    def base(self, E, t):
        if t < 0.7: s, e = E.frames('leopard_skill_baoyin_start'); return E.sample('leopard_skill_baoyin_start', s + t / 0.7 * (e - s))
        if t < 2.0: s, e = E.frames('leopard_skill_baoyin_loop'); return E.sample('leopard_skill_baoyin_loop', s + ((t - 0.7) / 1.3) * (e - s))
        if t < 2.7: s, e = E.frames('leopard_skill_baoyin_end'); return E.sample('leopard_skill_baoyin_end', s + (t - 2.0) / 0.7 * (e - s))
        return E.sample(*STAND)
    def act(self, t, c):
        c.mane(env(t, 0.5, 0.8, 1.9, 2.2)); c.squash += 0.04 * env(t, 0.8, 0.9, 1.8, 2.0)
        e = env(t, 2.8, 3.0, 3.9, 4.3); c.head(yaw=30 * e * sinp(t, 1.2), pitch=10 * e); c.ears(back=14 * e); c.tail(up=-10 * e, wag=8 * e)
    def props(self, t, M):
        if not (1.0 < t < 2.0): return {'Drops': None}
        k = (t - 1.0); p = M['head'].translation
        return {'Drops': T((p.x, p.y - 0.4, p.z)) @ Rx(90) @ Sc((0.4 + 0.8 * k, 0.4 + 0.8 * k, max(1e-4, 0.5 * (1 - k))))}


class BigRoar(Clip):
    """The real deal (the first time it works, he is as surprised as you)."""
    def __init__(self):
        super().__init__('BigRoar', 3.4); self.face['jaw'].keys((0.6, 0.0), (0.85, 1.2), (2.0, 1.2), (2.3, 0.0))
    def base(self, E, t): return TinyRoar.base(self, E, t)
    def act(self, t, c):
        k = env(t, 0.5, 0.8, 2.0, 2.3); c.mane(1.6 * k); c.squash += 0.06 * k; c.head(roll=4 * sinp(t, 0.1) * k)
        s = env(t, 2.4, 2.6, 3.0, 3.4); c.head(pitch=-10 * s, yaw=-20 * s); c.tail(wag=20 * s * sinp(t, 0.3), up=20 * s)


class Beg(Clip):
    """Stands up on his hind legs and paws at you (Meshy 'sajiao')."""
    def __init__(self): super().__init__('Beg', 200 / FPS, 'cat_sajiao')
    def act(self, t, c): c.tail(wag=10 * sinp(t, 0.6)); self.face['jaw'].d = 0.15


class PlayBall(Clip):
    """A ball rolls in; he bats it, chases it a little, pins it, looks proud."""
    def __init__(self): super().__init__('PlayBall', 5.0)
    def ball(self, t):
        if t < 1.0: return Vector((0.9 - 0.75 * t / 1.0, -0.75, 0.07)), -500 * t
        if t < 1.6: return Vector((0.15, -0.75, 0.07)), -500
        if t < 2.6: k = (t - 1.6) / 1.0; return Vector((0.15 - 0.6 * k, -0.75 - 0.1 * k, 0.07 + 0.2 * math.sin(math.pi * k))), -500 + 600 * k
        return Vector((-0.45, -0.85, 0.07)), 100
    def act(self, t, c):
        b, _ = self.ball(t)
        c.head(yaw=max(-50, min(50, math.degrees(math.atan2(b.x, 0.8)))), pitch=18)
        bat = env(t, 1.5, 1.65, 1.75, 1.95); c.front('L', 55 * bat, 30 * bat); c.body(roll=6 * bat)
        cr = env(t, 0.8, 1.1, 1.4, 1.5); c.root.z -= 0.1 * cr; c.tail(wag=20 * sinp(t, 0.3) * cr)
        pin = env(t, 2.8, 3.1, 4.2, 4.6); c.body(yaw=-25 * pin); c.front('R', 50 * pin, 20 * pin); c.head(pitch=-10 * pin); c.mane(0.6 * pin)
        c.tail(up=20 * pin)
    def props(self, t, M):
        b, r = self.ball(t); return {'Ball': T(b) @ Rx(r) @ Sc(1.4)}


class ChaseButterfly(Clip):
    """Head follows a butterfly, he swats at it twice, sits back puzzled when it lands on his nose."""
    def __init__(self): super().__init__('ChaseButterfly', 5.0)
    def bfly(self, t):
        a = t * 2.4
        if t < 3.6: return Vector((0.55 * math.cos(a), -0.75 + 0.2 * math.sin(a), 0.95 + 0.15 * math.sin(a * 2)))
        if t < 4.4: return Vector((0.0, -0.95, 0.9))
        k = (t - 4.4) / 0.6; return Vector((0.5 * k, -0.95, 0.9 + 0.5 * k))
    def act(self, t, c):
        p = self.bfly(t); y = max(-60, min(60, math.degrees(math.atan2(p.x, 0.75))))
        c.head(yaw=y, pitch=-20 - 10 * env(t, 3.6, 3.8, 4.4, 4.6), roll=0.3 * y)
        for a in (1.4, 2.6):
            s = env(t, a, a + 0.1, a + 0.2, a + 0.45); c.front('L' if a < 2 else 'R', 75 * s, 25 * s); c.root.z += 0.06 * s; c.body(pitch=-12 * s)
        x = env(t, 3.6, 3.8, 4.4, 4.6); c.head(roll=15 * x); c.ears(back=-8 * x); c.tail(wag=15 * sinp(t, 0.6))
    def props(self, t, M):
        return {'Butterfly': T(self.bfly(t)) @ Rz(t * 140) @ Sc(PS), '_wings': math.radians(70 * abs(math.sin(t * 24)))}


# ------------------------------------------------------------------ moving
class Walk(Clip):
    loop = True; group = 'walk'
    def __init__(self): super().__init__('Walk', 35 / FPS * 3, 'cat_walk', loop=True)
    def act(self, t, c): c.tail(up=12, wag=8 * sinp(t, 35 / FPS))


class Run(Clip):
    loop = True; group = 'walk'
    def __init__(self): super().__init__('Run', 23 / FPS * 4, 'cat_run', loop=True)
    def act(self, t, c): c.ears(back=8); c.tail(up=10)


class JumpStart(Clip):
    def __init__(self): super().__init__('JumpStart', 0.5)
    def act(self, t, c):
        k = env(t, 0.0, 0.3, 0.38, 0.5); c.root.z -= 0.14 * k
        for s in 'LR': c.front(s, 15 * k, 35 * k); c.rear(s, 15 * k, 40 * k)
        c.tail(up=-15 * k); c.body(pitch=6 * k)


class JumpAir(Clip):
    loop = True; group = 'air'
    def __init__(self): super().__init__('JumpAir', 1.0, loop=True)
    def act(self, t, c):
        for s in 'LR': c.front(s, 45, -10); c.rear(s, -40, -10)
        c.body(pitch=-8); c.tail(up=25, wag=10 * sinp(t, 0.5)); c.ears(back=10)


class JumpLand(Clip):
    def __init__(self): super().__init__('JumpLand', 27 / FPS, 'leopard_jump_end', rng=(9, 27))


# ------------------------------------------------------------------ touch
class Poke(Clip):
    group = 'touch'
    def __init__(self): super().__init__('Poke', 1.2)
    def act(self, t, c):
        k = env(t, 0.0, 0.06, 0.2, 0.7); c.root.z += 0.1 * env(t, 0.0, 0.08, 0.12, 0.35); c.squash -= 0.06 * k
        c.head(pitch=-15 * k); c.ears(back=-10 * k); c.tail(up=30 * k)
        f = env(t, 0.6, 0.75, 0.9, 1.2); c.head(roll=15 * f * sinp(t, 0.1))


class Pet(Clip):
    """Melts into your hand (Meshy 'being petted', sitting)."""
    loop = True; group = 'touch'
    def __init__(self): super().__init__('Pet', 3.6, 'dog_beifumo', rng=(55, 145), loop=True)
    def base(self, E, t): return E.sample('dog_beifumo', 55 + (t / 3.6) * 90)
    def act(self, t, c): c.tail(wag=12 * sinp(t, 1.2)); c.head(roll=10 * sinp(t, 3.6)); self.face['jaw'].d = 0.12
    def props(self, t, M):
        ph = (t / 1.8) % 1.0; h = M['head'].translation
        return {'Heart': T((h.x + 0.25, h.y - 0.1, h.z + 0.35 + 0.35 * ph)) @ Sc(PS * ss(ph / 0.2) * (1 - ss((ph - 0.7) / 0.3)) + 1e-4)}


class Tickle(Clip):
    group = 'touch'
    def __init__(self):
        super().__init__('Tickle', 2.2); self.face['jaw'].keys((0.1, 0.0), (0.3, 0.5), (1.8, 0.5), (2.2, 0.0))
    def act(self, t, c):
        k = env(t, 0.0, 0.15, 1.8, 2.2); w = sinp(t, 0.16)
        c.body(roll=10 * w * k); c.head(roll=-15 * w * k, pitch=-10 * k); c.front('L', 30 * k * max(0, w), 40 * k); c.front('R', 30 * k * max(0, -w), 40 * k)
        c.tail(wag=25 * w * k); c.root.z -= 0.06 * k


class Angry(Clip):
    """Too many pokes: ears flat, mane up, a hiss and a swat with the claws."""
    group = 'touch'
    def __init__(self):
        super().__init__('Angry', 2.4); self.face['jaw'].keys((0.2, 0.0), (0.4, 0.9), (1.8, 0.9), (2.2, 0.0))
    def act(self, t, c):
        k = env(t, 0.0, 0.25, 1.9, 2.4); s = env(t, 1.0, 1.1, 1.2, 1.45)
        c.mane(1.5 * k); c.squash += 0.08 * k; c.ears(back=20 * k); c.root.z -= 0.08 * k; c.body(pitch=8 * k)
        c.front('R', 70 * s, -10 * s); c.body(roll=-10 * s); c.tail(up=25 * k, wag=10 * sinp(t, 0.15) * k)


class Scared(Clip):
    group = 'touch'
    def __init__(self): super().__init__('Scared', 2.2)
    def act(self, t, c):
        k = env(t, 0.0, 0.15, 1.6, 2.2); c.root.z -= 0.15 * k; c.ears(back=22 * k); c.tail(up=-30 * k, wag=-20 * k)
        for s in 'LR': c.front(s, 10 * k, 35 * k); c.rear(s, 10 * k, 45 * k)
        c.head(pitch=12 * k, yaw=20 * sinp(t, 0.8) * k); c.root.y += 0.08 * k


class Dangle(Clip):
    """Carried by the scruff: legs and tail hanging, a resigned face."""
    loop = True; group = 'touch'
    def __init__(self): super().__init__('Dangle', 2.4, loop=True)
    def act(self, t, c):
        s = sinp(t, 1.2)
        for side in 'LR': c.front(side, -15 + 5 * s, 10); c.rear(side, -25 + 5 * s, -10)
        c.tail(up=-50, wag=10 * s); c.head(pitch=10, roll=6 * s); c.body(pitch=-10)


class Tumble(Clip):
    def __init__(self): super().__init__('Tumble', 2.0)
    def act(self, t, c):
        r = ss(t / 0.8); c.roll = 360 * r if t < 0.8 else 0.0; c.root.z -= 0.1 * env(t, 0.7, 0.8, 1.0, 1.3)
        d = env(t, 1.0, 1.2, 1.6, 2.0); c.head(roll=18 * d * sinp(t, 0.4)); c.body(roll=8 * d * sinp(t, 0.4))


# ------------------------------------------------------------------ sleep
class FallAsleep(Clip):
    def __init__(self):
        super().__init__('FallAsleep', 3.2); mouth(self.face, 1.4, 0.35, 0.5, 0.4, 1.1)
    def act(self, t, c):
        lie(c, ss(t / 1.2)); h = ss((t - 2.4) / 0.7); c.head(pitch=25 * h - 20 * env(t, 1.3, 1.6, 2.1, 2.4), roll=10 * h); c.ears(back=10 * h)
        c.tail(wag=-25 * h)


class Sleep(Clip):
    loop = True; group = 'idle'
    def __init__(self): super().__init__('Sleep', 6.0, loop=True)
    def act(self, t, c):
        b = sinp(t, 3.0); lie(c, 1.0, b); c.head(pitch=25 + 2 * b, roll=10); c.ears(back=10); c.tail(wag=-25)
        d = env(t, 3.6, 3.8, 4.4, 4.6); c.front('L', 5 * d * sinp(t, 0.2))
    def props(self, t, M):
        out = {}; top = M['head'].translation
        for i in range(3):
            ph = ((t / 6.0) * 2 + i / 3.0) % 1.0
            s = PS * ss(ph / 0.2) * (1 - ss((ph - 0.75) / 0.25)) * (0.8 + 0.25 * i)
            out[f'Z{i}'] = T(top + Vector((0.2 + 0.2 * ph, -0.1, 0.3 + 0.4 * ph))) @ Sc(max(1e-4, s)) if s > 0.01 else None
        return out


class WakeUp(Clip):
    def __init__(self):
        super().__init__('WakeUp', 3.0); mouth(self.face, 0.9, 0.3, 0.4, 0.3, 1.1)
    def act(self, t, c):
        k = 1 - ss((t - 1.6) / 0.9); lie(c, k); c.head(pitch=25 * (1 - ss(t / 0.5)) - 20 * env(t, 0.8, 1.0, 1.4, 1.7))
        s = env(t, 2.0, 2.3, 2.6, 3.0); c.body(pitch=10 * s); c.front('L', 30 * s); c.front('R', 30 * s); c.tail(up=25 * s)


# ------------------------------------------------------------------ music
class DanceBob(Clip):
    def __init__(self): super().__init__('DanceBob', 4.0, SIT)
    def act(self, t, c):
        b = tri(t, 0.5); c.head(pitch=18 * b - 6, roll=10 * sinp(t, 2.0)); c.tail(wag=20 * sinp(t, 1.0)); c.squash += 0.03 * b
        c.front('L', 25 * max(0.0, sinp(t, 1.0)), 30); c.front('R', 25 * max(0.0, -sinp(t, 1.0)), 30)


class DanceStomp(Clip):
    """Front paws stomping on the beat, butt wiggle, mane bouncing."""
    def __init__(self): super().__init__('DanceStomp', 4.0)
    def act(self, t, c):
        ph = (t / 0.5) % 1.0; up = math.sin(math.pi * ph); side = 'L' if int(t / 0.5) % 2 == 0 else 'R'
        c.front(side, 40 * up, 50 * up); c.turn('hips', 'Z', 14 * sinp(t, 1.0)); c.tail(wag=25 * sinp(t, 0.5), up=20)
        c.head(pitch=12 * tri(t, 0.5), roll=8 * sinp(t, 1.0)); c.mane(0.4 * tri(t, 0.5))


class DanceRoar(Clip):
    """Roars on the drop like a rock star."""
    def __init__(self):
        super().__init__('DanceRoar', 4.0)
        for a in (0.5, 2.5): self.face.pulse('jaw', a, 0.1, 0.6, 0.2, 1.1)
    def act(self, t, c):
        for a in (0.5, 2.5):
            k = env(t, a, a + 0.1, a + 0.7, a + 0.9); c.head(pitch=-25 * k, roll=6 * k * sinp(t, 0.1)); c.mane(1.4 * k); c.body(pitch=-10 * k)
            c.front('L', 25 * k); c.front('R', 25 * k)
        b = tri(t, 0.5); c.head(pitch=10 * b); c.tail(wag=20 * sinp(t, 1.0), up=15)


class DanceSpin(Clip):
    def __init__(self): super().__init__('DanceSpin', 3.0)
    def act(self, t, c):
        c.spin = 360 * ss(t / 2.2); h = abs(math.sin(t * math.pi / 0.35)) * (1 if t < 2.2 else 0); c.root.z += 0.08 * h
        f = env(t, 2.2, 2.4, 2.7, 3.0); c.head(pitch=-20 * f); c.tail(up=30 * f); c.mane(f)
        self.face['jaw'].keys((2.2, 0.0), (2.4, 0.8), (2.7, 0.8), (3.0, 0.0))


# ------------------------------------------------------------------ charger
class Plugged(Clip):
    """Charger in: ears up, bouncy happy hops, a joyful mini-roar."""
    def __init__(self):
        super().__init__('Plugged', 3.0); self.face.pulse('jaw', 1.8, 0.1, 0.5, 0.2, 1.0)
    def act(self, t, c):
        k = env(t, 0.0, 0.1, 0.3, 0.5); c.head(pitch=-15 * k); c.ears(back=-10 * k); c.squash -= 0.05 * k
        for i, a in enumerate((0.5, 0.95, 1.4)):
            h = math.sin(math.pi * max(0.0, min(1.0, (t - a) / 0.38))) if a < t < a + 0.38 else 0.0
            c.root.z += 0.16 * h; c.body(pitch=-10 * h)
            for s in 'LR': c.front(s, 30 * h, -10 * h); c.rear(s, -25 * h)
        r = env(t, 1.8, 1.9, 2.4, 2.6); c.mane(1.2 * r); c.head(pitch=-20 * r); c.tail(up=30, wag=20 * sinp(t, 0.3))
    def props(self, t, M):
        if t < 1.0: return {'Heart': None}
        ph = (t - 1.0) / 2.0; h = M['head'].translation
        return {'Heart': T((h.x - 0.25, h.y - 0.1, h.z + 0.35 + 0.4 * ph)) @ Sc(PS * 1.2 * ss(ph / 0.2) * (1 - ss((ph - 0.7) / 0.3)) + 1e-4)}


class ChargeRest(Clip):
    """While charging: lying comfy like a little sphinx, tail curled, slow content blinks of the head."""
    loop = True; group = 'idle'
    def __init__(self): super().__init__('ChargeRest', 6.0, loop=True)
    def act(self, t, c):
        b = sinp(t, 3.0); lie(c, 1.0, b); c.head(pitch=4 + 2 * b, yaw=15 * env(t, 2.0, 2.5, 3.5, 4.0), roll=6 * sinp(t, 6.0))
        c.tail(wag=-20 + 6 * sinp(t, 2.0))


class Tired(Clip):
    """Low battery: flat on his tummy, chin on paws, heavy sighs and a yawn."""
    loop = True; group = 'idle'
    def __init__(self):
        super().__init__('Tired', 6.0, loop=True); mouth(self.face, 3.5, 0.4, 0.5, 0.4, 1.0)
    def act(self, t, c):
        b = sinp(t, 3.0); lie(c, 1.0, b); c.head(pitch=28 + 2 * b - 30 * env(t, 3.4, 3.8, 4.3, 4.7)); c.ears(back=16); c.tail(wag=-25)
        s = env(t, 1.0, 1.4, 1.8, 2.4); c.squash += 0.04 * s


class Unplugged(Clip):
    def __init__(self): super().__init__('Unplugged', 2.4)
    def act(self, t, c):
        k = env(t, 0.0, 0.06, 0.25, 0.6); c.root.z += 0.12 * env(t, 0.0, 0.08, 0.12, 0.3); c.squash -= 0.06 * k; c.tail(up=35 * k)
        l = env(t, 0.6, 0.9, 1.5, 1.8); c.head(yaw=40 * l, pitch=20 * l); c.ears(back=10 * l)
        f = env(t, 1.8, 1.95, 2.1, 2.4); c.head(roll=12 * f * sinp(t, 0.12)); c.body(roll=6 * f * sinp(t, 0.12))


class FullCharge(Clip):
    """100%: the king stands tall and lets out his biggest roar, then struts in a circle."""
    def __init__(self):
        super().__init__('FullCharge', 4.6); self.face['jaw'].keys((0.6, 0.0), (0.85, 1.2), (2.0, 1.2), (2.3, 0.0))
    def base(self, E, t):
        if t < 2.7: return TinyRoar.base(self, E, t)
        s, e = E.frames('cat_walk'); return E.sample('cat_walk', s + ((t - 2.7) * FPS) % (e - s))
    def act(self, t, c):
        k = env(t, 0.5, 0.8, 2.0, 2.3); c.mane(1.8 * k); c.squash += 0.06 * k
        if t > 2.7: c.spin = 360 * ss((t - 2.7) / 1.9); c.tail(up=25); c.head(pitch=-10)
    def props(self, t, M):
        ph = ((t - 0.5) / 2.0) % 1.0; h = M['head'].translation
        return {'Heart': T((h.x + 0.3 * math.sin(ph * 6), h.y - 0.15, h.z + 0.35 + 0.45 * ph)) @ Sc(PS * ss(ph / 0.2) * (1 - ss((ph - 0.7) / 0.3)) + 1e-4)}



def hang(c, t, k=1.0):
    """Held up by the scruff: the body hangs from the finger, legs and tail straight down, swaying limp."""
    c.turn('hips', 'X', -62 * k); c.root.z -= 0.12 * k
    for s, ph in (('L', 0.0), ('R', 0.5)):
        c.front(s, -62 * k + 7 * sinp(t, 1.6, ph), 6); c.rear(s, -62 * k + 7 * sinp(t, 1.6, ph + 0.3), 4)
    c.tail(up=-55 * k, wag=6 * sinp(t, 1.6)); c.head(pitch=44 * k, roll=5 * sinp(t, 3.2))


class Dangle(Clip):
    """Lifted by the finger: hangs limp under gravity, paws dangling, a resigned little sway."""
    loop = True; group = 'touch'
    def __init__(self): super().__init__('Dangle', 3.2, loop=True)
    def act(self, t, c): hang(c, t)

def library():
    return [Idle(), LookAround(), HeadTilt(), Yawn(), Sneeze(), Stretch(), Groom(), Sit(), SitDown(), StandUp(), LieDown(), RollOver(),
            ChaseTail(), Pounce(), Stalk(), TinyRoar(), BigRoar(), Beg(), PlayBall(), ChaseButterfly(),
            Walk(), Run(), JumpStart(), JumpAir(), JumpLand(),
            Poke(), Pet(), Tickle(), Angry(), Scared(), Dangle(), Tumble(),
            FallAsleep(), Sleep(), WakeUp(),
            DanceBob(), DanceStomp(), DanceRoar(), DanceSpin(),
            Plugged(), ChargeRest(), Tired(), Unplugged(), FullCharge()]
