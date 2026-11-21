"""Blizzard the husky: Meshy quadruped motions + hand-made acting (face, ears, tail, props)."""
import math, random, sys
sys.path.insert(0, '/home/claude/duck2/scripts')
from mathutils import Vector, Matrix, Quaternion
from engine2 import rq, ss, env, Track, Face, FPS

BLEND = '/home/claude/husky/husky.props.blend'
OUT = '/home/claude/husky/baked'
MOVER = 'mover'; HIPS = 'hips'; HIPS_TARGET = (0.0, 0.33)
JAW = ('jaw_01', 'X', 26.0)
SPRINGS = [(['tail1', 'tail2', 'tail4', 'tail_04'], 'tail', 55.0, 5.5),
           (['ear.L_01'], 'earL', 160.0, 9.0), (['ear.R_01'], 'earR', 160.0, 9.0)]
E = None
STAND = ('dog_idle', 1)
SIT = ('dog_beifumo', 97)
T = Matrix.Translation
def Sc(s): return Matrix.Diagonal((s, s, s, 1)) if not isinstance(s, tuple) else Matrix.Diagonal((*s, 1))
def Rz(d): return Matrix.Rotation(math.radians(d), 4, 'Z')
def Rx(d): return Matrix.Rotation(math.radians(d), 4, 'X')
def Ry(d): return Matrix.Rotation(math.radians(d), 4, 'Y')
def sinp(t, period, phase=0.0): return math.sin(2 * math.pi * (t / period + phase))


class Ctx:
    def __init__(self, M):
        self.M = M; self.root = Vector(); self.squash = 0.0; self.turns = []; self.jaw = 0.0; self.spin = 0.0; self.roll = 0.0
    def turn(self, bone, axis, deg):
        if abs(deg) > 1e-4: self.turns.append((bone, rq(axis, deg)))
    def body(self, pitch=0.0, yaw=0.0, roll=0.0):
        for b in ('spine2', 'spine_03'):
            self.turn(b, 'X', pitch / 2); self.turn(b, 'Z', yaw / 2); self.turn(b, 'Y', roll / 2)
    def head(self, pitch=0.0, yaw=0.0, roll=0.0):
        for b, k in (('neck1', 0.3), ('neck2', 0.3), ('head', 0.4)):
            self.turn(b, 'Z', yaw * k); self.turn(b, 'X', pitch * k); self.turn(b, 'Y', roll * k)
    def front(self, side, swing=0.0, bend=0.0):
        self.turn(f'frontUpr.{side}', 'X', -swing); self.turn(f'frontLwr.{side}', 'X', bend)
    def rear(self, side, swing=0.0, bend=0.0):
        self.turn(f'rearUpr.{side}', 'X', -swing); self.turn(f'rearLwr.{side}', 'X', -bend)
    def tail(self, wag=0.0, up=0.0):
        self.turn('tail1', 'Z', wag); self.turn('tail1', 'X', -up); self.turn('tail2', 'Z', wag * 0.6)
    def ears(self, back=0.0, spread=0.0, l=None, r=None):
        self.turn('ear.L_01', 'X', -(back if l is None else l)); self.turn('ear.R_01', 'X', -(back if r is None else r))
        if spread: self.turn('ear.L_01', 'Y', spread); self.turn('ear.R_01', 'Y', -spread)


class Clip:
    loop = False; lock = 'lock'; springs = True; group = 'life'
    def __init__(self, name, secs, src=None, rng=None, speed=1.0, loop=None, seed=None, happy=0.0):
        self.name = name; self.src = src; self.rng = rng; self.speed = speed
        if loop is not None: self.loop = loop
        self.secs = secs; self.n = int(round(secs * FPS))
        self.face = Face(secs, seed=seed if seed is not None else sum(map(ord, name)), loop=self.loop, look_amp=(0.35, 0.18))
        self.face['happy'].d = happy
    def base(self, E, t):
        if self.src is None: return E.sample(*STAND)
        if isinstance(self.src, tuple): return E.sample(*self.src)
        s, e = self.rng if self.rng else E.frames(self.src)
        f = s + t * FPS * self.speed
        if self.loop: f = s + (f - s) % max(1, (e - s))
        return E.sample(self.src, min(f, e))
    def act(self, t, c): pass
    def props(self, t, M): return {}


def mix_src(a, fa, b, fb, w):
    return E.mix(E.sample(a, fa), E.sample(b, fb), ss(w))


# ------------------------------------------------------------------ everyday
class Idle(Clip):
    loop = True; group = 'idle'
    def __init__(self): super().__init__('Idle', 39 / FPS * 4, 'dog_idle', happy=0.15)
    def act(self, t, c):
        c.tail(wag=10 * sinp(t, 0.65) * (0.6 + 0.4 * sinp(t, 5.2)), up=8)
        c.head(roll=5 * sinp(t, 5.2, 0.3), yaw=12 * (env(t, 1.4, 1.8, 2.4, 2.8) - env(t, 3.4, 3.7, 4.3, 4.7)))
        c.ears(back=4 * sinp(t, 2.6))
        c.jaw = 0.12 + 0.08 * sinp(t, 0.9)          # soft panting smile


class Sniff(Clip):
    def __init__(self):
        super().__init__('LookAround', 168 / FPS, 'dog_idle1', seed=3)
        F = self.face; F['pupil'].keys((2.6, 1.0), (2.9, 1.15), (4.3, 1.15), (4.6, 1.0))
        F['ly'].keys((2.7, 0.0), (3.0, -0.3), (5.2, -0.3), (5.5, 0.0))
    def act(self, t, c): c.tail(wag=6 * sinp(t, 0.8)); c.ears(back=-6 * env(t, 0.3, 0.6, 2.0, 2.4))


class Walk(Clip):
    loop = True; group = 'walk'
    def __init__(self): super().__init__('Walk', 16 / FPS * 4, 'dog_walk', speed=1.0, happy=0.25)
    def act(self, t, c): c.tail(wag=14 * sinp(t, 16 / FPS)); c.jaw = 0.25


class Run(Clip):
    loop = True; group = 'walk'
    def __init__(self):
        super().__init__('Run', 9 / FPS * 6, 'dog_run', happy=0.45); self.face.blinks = []; self.face.looks = []
    def act(self, t, c): c.jaw = 0.55; c.ears(back=18)


class Wag(Clip):
    """The happiest tail on earth: Meshy wag + bouncy front paws + huge grin."""
    def __init__(self):
        super().__init__('Wag', 3.0, 'dog_yaoweiba', loop=False, happy=0.0, seed=5)
        F = self.face; F['happy'].keys((0.2, 0.0), (0.5, 1.0), (2.6, 1.0), (3.0, 0.2))
    def base(self, E, t):
        s, e = E.frames('dog_yaoweiba'); return E.sample('dog_yaoweiba', s + (t * FPS) % (e - s))
    def act(self, t, c):
        k = env(t, 0.2, 0.5, 2.5, 2.9)
        c.tail(wag=k * 38 * sinp(t, 0.22), up=20 * k); c.turn('hips', 'Z', 6 * k * sinp(t, 0.22, 0.5))
        c.front('L', 18 * k * max(0, sinp(t, 0.5))); c.front('R', 18 * k * max(0, sinp(t, 0.5, 0.5)))
        c.root.z += 0.03 * k * abs(sinp(t, 0.5)); c.jaw = 0.5 * k


# ------------------------------------------------------------------ sit / lie / sleep
class SitDown(Clip):
    def __init__(self): super().__init__('SitDown', 48 / FPS, 'dog_beifumo', rng=(1, 49), happy=0.2)
    def act(self, t, c): c.tail(wag=8 * sinp(t, 0.6))


class Sit(Clip):
    loop = True; group = 'idle'
    def __init__(self): super().__init__('Sit', 5.2, SIT, happy=0.3, seed=7)
    def act(self, t, c):
        c.body(pitch=-1.5 * sinp(t, 2.6)); c.squash = 0.012 * sinp(t, 2.6)
        c.head(roll=8 * sinp(t, 5.2, 0.2), yaw=14 * env(t, 1.0, 1.4, 2.4, 2.8))
        c.tail(wag=12 * sinp(t, 0.65) * env(t, 3.0, 3.3, 4.6, 5.0)); c.jaw = 0.25 + 0.1 * sinp(t, 0.85)


class StandUp(Clip):
    def __init__(self): super().__init__('StandUp', 30 / FPS, 'dog_beifumo', rng=(127, 157), happy=0.2)


def lie_pose(c, k, breathe=0.0):
    c.body(pitch=52 * k + 1.5 * breathe)
    c.front('L', 80 * k, -6 * k); c.front('R', 84 * k, -6 * k)
    c.head(pitch=-40 * k)
    c.root.z -= 0.17 * k; c.squash = 0.03 * k + 0.015 * breathe


class LieDown(Clip):
    def __init__(self): super().__init__('LieDown', 1.0, SIT, happy=0.2)
    def act(self, t, c): lie_pose(c, ss(t / 0.9))


class FallAsleep(Clip):
    """sits down (Meshy) -> lies down -> big yawn -> head sinks onto the paws, eyes heavy."""
    def __init__(self):
        super().__init__('FallAsleep', 1.6 + 2.6, seed=8)
        F = self.face; F.blinks = [0.5, 2.8]; F.looks = []
        F['bL'].keys((2.6, 0.0), (3.2, 0.6), (3.5, 0.4), (4.1, 1.0)); F['bR'].k = list(F['bL'].k)
        F.pulse('jaw', 2.5, 0.3, 0.4, 0.4, 1.0)
    def base(self, E, t):
        return E.sample('dog_beifumo', 1 + min(48.0, t * FPS)) if t < 1.6 else E.sample(*SIT)
    def act(self, t, c):
        if t < 1.6: return
        u = t - 1.6
        lie_pose(c, ss(u / 1.0)); c.head(pitch=22 * ss((u - 1.6) / 0.9), roll=8 * ss((u - 1.6) / 0.9))
        c.ears(back=14 * ss((u - 1.4) / 1.0))


class Sleep(Clip):
    loop = True; group = 'sleep'
    def __init__(self):
        super().__init__('Sleep', 6.0, SIT, seed=9)
        F = self.face; F.blinks = []; F.looks = []; F['bL'].d = F['bR'].d = 1.0; F['happy'].d = 0.2
    def act(self, t, c):
        b = sinp(t, 3.0); lie_pose(c, 1.0, b)
        dream = env(t, 3.6, 3.8, 4.4, 4.6)      # little dream: paws and ears twitch
        c.head(pitch=22 + 2 * b, roll=8); c.ears(back=14 - 10 * dream * max(0, sinp(t, 0.18)))
        c.front('L', 6 * dream * sinp(t, 0.2)); c.front('R', 6 * dream * sinp(t, 0.2, 0.5))
        c.tail(wag=4 * dream * sinp(t, 0.3))
    def props(self, t, M):
        out = {}; top = M['head'] @ Vector((0, 0.25, 0))
        for i in range(3):
            ph = ((t / 6.0) * 2 + i / 3.0) % 1.0
            s = 3.0 * ss(ph / 0.2) * (1 - ss((ph - 0.75) / 0.25)) * (0.8 + 0.25 * i)
            pos = top + Vector((0.16 + 0.2 * ph + 0.03 * math.sin(ph * 9), -0.12, 0.08 + 0.36 * ph))
            out[f'Z{i}'] = T(pos) @ Ry(-15 + 25 * ph) @ Sc(max(1e-4, s)) if s > 0.01 else None
        return out


class WakeUp(Clip):
    """eyes open, stretch, a squeaky yawn, then up on all fours (Meshy stand-up)."""
    def __init__(self):
        super().__init__('WakeUp', 2.8 + 1.0, seed=10)
        F = self.face; F.blinks = [0.9, 1.15]
        F['bL'].keys((0.0, 1.0), (0.35, 1.0), (0.5, 0.0)); F['bR'].k = list(F['bL'].k)
        F.pulse('jaw', 1.3, 0.25, 0.4, 0.3, 1.1); F['happy'].keys((1.3, 0.0), (1.55, 1.0), (2.2, 1.0), (2.6, 0.3))
    def base(self, E, t):
        return E.sample(*SIT) if t < 2.8 else E.sample('dog_beifumo', 127 + min(30.0, (t - 2.8) * FPS))
    def act(self, t, c):
        k = 1 - ss((t - 0.4) / 0.7)
        lie_pose(c, k); c.head(pitch=22 * (1 - ss((t - 0.2) / 0.3)) - 18 * env(t, 1.3, 1.55, 1.9, 2.2))
        st = env(t, 1.9, 2.1, 2.4, 2.7); c.front('L', 25 * st); c.front('R', 25 * st); c.body(pitch=-10 * st)
        c.tail(wag=20 * env(t, 2.2, 2.4, 3.4, 3.8) * sinp(t, 0.25))


# ------------------------------------------------------------------ voice
class Bark(Clip):
    def __init__(self):
        super().__init__('Bark', 1.7, seed=11)
        F = self.face; F.blinks = [1.45]
        for t0 in (0.35, 0.75): F.pulse('jaw', t0, 0.05, 0.07, 0.1, 1.1)
        F['tilt'].keys((0.2, 0.0), (0.35, -0.25), (1.0, -0.25), (1.3, 0.0)); F['pupil'].d = 1.05
    def act(self, t, c):
        ant = env(t, 0.05, 0.28, 0.3, 0.36)
        for t0 in (0.35, 0.75):
            k = env(t, t0, t0 + 0.05, t0 + 0.1, t0 + 0.25)
            c.head(pitch=-14 * k); c.body(pitch=4 * k); c.squash += 0.08 * k
            c.front('L', 14 * k); c.front('R', 14 * k); c.root.z += 0.025 * k
        c.head(pitch=10 * ant); c.squash -= 0.05 * ant
        c.tail(wag=16 * sinp(t, 0.3), up=18); c.ears(back=-8)


class Howl(Clip):
    """Sits, points its nose at the moon and goes 'awooo'."""
    def __init__(self):
        super().__init__('Howl', 4.4, SIT, seed=12)
        F = self.face; F.blinks = [3.9]; F.looks = []
        F['jaw'].keys((0.6, 0.0), (1.0, 0.7), (2.2, 0.9), (2.9, 0.5), (3.3, 0.0))
        F['bL'].keys((0.6, 0.0), (0.9, 0.85), (3.2, 0.85), (3.5, 0.0)); F['bR'].k = list(F['bL'].k)
    def act(self, t, c):
        k = env(t, 0.3, 0.9, 3.0, 3.6)
        c.head(pitch=-48 * k + 3 * k * sinp(t, 0.45), roll=4 * k * sinp(t, 1.3)); c.body(pitch=-8 * k)
        c.ears(back=16 * k); c.tail(wag=4 * sinp(t, 1.0))
        c.squash = -0.04 * k


class HeadTilt(Clip):
    def __init__(self):
        super().__init__('HeadTilt', 2.2, seed=13)
        F = self.face; F.looks = []; F.blinks = [1.8]
        F['pupil'].keys((0.2, 1.0), (0.45, 1.2), (1.7, 1.2), (2.0, 1.0)); F['tilt'].keys((0.2, 0.0), (0.5, 0.35), (1.6, 0.35), (1.9, 0.0))
    def act(self, t, c):
        k = env(t, 0.15, 0.45, 0.85, 1.05); k2 = env(t, 0.95, 1.2, 1.6, 1.95)
        c.head(roll=26 * k - 24 * k2, pitch=-6 * (k + k2)); c.ears(l=-14 * k + 8 * k2, r=8 * k - 14 * k2)


# ------------------------------------------------------------------ play
class PlayBow(Clip):
    """Chest down, butt up, tail going crazy, then a happy bounce."""
    def __init__(self):
        super().__init__('PlayBow', 2.6, seed=14)
        F = self.face; F['happy'].keys((0.2, 0.0), (0.5, 0.9), (2.2, 0.9), (2.6, 0.2))
        F['jaw'].keys((0.2, 0.0), (0.5, 0.55), (2.2, 0.55), (2.5, 0.2))
    def act(self, t, c):
        k = env(t, 0.1, 0.45, 1.6, 1.9)
        c.body(pitch=24 * k); c.front('L', 55 * k, -10 * k); c.front('R', 55 * k, -10 * k)
        c.head(pitch=-26 * k); c.root.z -= 0.06 * k; c.tail(wag=36 * k * sinp(t, 0.2), up=26 * k)
        bo = env(t, 1.85, 2.0, 2.1, 2.4); c.root.z += 0.09 * bo; c.squash = -0.08 * bo + 0.12 * env(t, 2.35, 2.42, 2.45, 2.6)
        c.front('L', 25 * bo); c.front('R', 25 * bo)


class Shake(Clip):
    """Wet-dog shake: head first, the wave runs down to the tail, drops flying everywhere."""
    def __init__(self):
        super().__init__('Shake', 2.0, seed=15)
        F = self.face; F.blinks = []; F.looks = []
        F['bL'].keys((0.2, 0.0), (0.3, 1.0), (1.35, 1.0), (1.5, 0.0)); F['bR'].k = list(F['bL'].k)
        F['happy'].keys((1.45, 0.0), (1.6, 0.9), (2.0, 0.6))
    def act(self, t, c):
        k = env(t, 0.2, 0.3, 1.1, 1.45)
        c.head(yaw=28 * k * sinp(t, 0.14), roll=14 * k * sinp(t, 0.14, 0.2))
        c.body(roll=12 * k * sinp(t, 0.14, 0.1), yaw=8 * k * sinp(t, 0.14, 0.15))
        c.turn('hips', 'Y', 10 * k * sinp(t, 0.14, 0.25)); c.tail(wag=30 * k * sinp(t, 0.14, 0.35))
        c.squash = 0.04 * k * sinp(t, 0.07)
    def props(self, t, M):
        if not (0.3 < t < 1.5): return {'Drops': None}
        k = (t - 0.3) / 1.2; p = M['spine_03'].translation
        return {'Drops': T((p.x, p.y, max(0.0, p.z - 0.35))) @ Rz(240 * k) @ Sc((1.2 + 2.0 * ss(k / 0.5), 1.2 + 2.0 * ss(k / 0.5), max(1e-4, 2.0 * (1 - ss((k - 0.5) / 0.5)))))}


class ChaseTail(Clip):
    """Spins after its own tail, twice, then wobbles dizzy."""
    def __init__(self):
        super().__init__('ChaseTail', 3.6, 'dog_run', seed=16)
        F = self.face; F.looks = []; F['lx'].d = 0.35; F['pupil'].d = 1.1
        F['bL'].keys((2.4, 0.0), (2.6, 0.5), (3.3, 0.5), (3.5, 0.0)); F['bR'].k = list(F['bL'].k)
        F['jaw'].keys((0.0, 0.3), (2.3, 0.6), (2.6, 0.2))
    def base(self, E, t):
        s, e = E.frames('dog_run')
        run = E.sample('dog_run', s + (t * FPS) % (e - s)); st = E.sample(*STAND)
        return E.mix(run, st, ss((t - 2.2) / 0.3))
    def act(self, t, c):
        c.spin = 720 * ss(t / 2.4) ** 1.1
        c.head(yaw=30 * (1 - ss((t - 2.2) / 0.2))); c.body(yaw=20 * (1 - ss((t - 2.2) / 0.2)))
        dz = env(t, 2.4, 2.6, 3.2, 3.6); c.head(roll=16 * dz * sinp(t, 0.6)); c.body(roll=6 * dz * sinp(t, 0.6, 0.2))


class Dig(Clip):
    def __init__(self):
        super().__init__('Dig', 3.0, seed=17)
        F = self.face; F['ly'].d = -0.35; F['tilt'].keys((0.3, 0.0), (0.6, -0.3), (2.4, -0.3), (2.8, 0.0))
        F['happy'].keys((2.5, 0.0), (2.75, 0.9), (3.0, 0.7))
    def act(self, t, c):
        k = env(t, 0.1, 0.35, 2.3, 2.6)
        c.body(pitch=18 * k); c.head(pitch=14 * k); c.root.z -= 0.03 * k
        c.front('L', k * (20 + 40 * max(0, sinp(t, 0.22))), k * 40 * max(0, sinp(t, 0.22))); c.front('R', k * (20 + 40 * max(0, sinp(t, 0.22, 0.5))), k * 40 * max(0, sinp(t, 0.22, 0.5)))
        c.tail(wag=20 * k * sinp(t, 0.3), up=20 * k)
    def props(self, t, M):
        if not (0.4 < t < 2.6): return {'Drops': None, 'DogBone': None}
        k = ((t - 0.4) % 0.44) / 0.44
        bone = T((0.0, -0.75, 0.02)) @ Rz(30) @ Sc(1.6) if t > 2.2 else None
        return {'Drops': T((0.0, -0.55, 0.0)) @ Rz(80 * t) @ Sc((1.0 + k, 1.0 + k, max(1e-4, 1.4 * (1 - k)))), 'DogBone': bone}


class Fetch(Clip):
    """A ball bounces in; it zooms out, grabs it, trots back and drops it at your feet, tail going."""
    def __init__(self):
        super().__init__('Fetch', 6.4, seed=18)
        F = self.face; F.looks = []
        F['pupil'].keys((0.0, 1.0), (0.3, 1.2), (1.2, 1.2), (1.4, 1.0)); F['lx'].keys((0.0, 0.0), (0.3, 0.35), (1.0, 0.35), (1.3, 0.0))
        F['jaw'].keys((0.0, 0.2), (1.9, 0.6), (2.15, 0.35), (4.8, 0.35), (5.0, 0.8), (5.2, 0.2))
        F['happy'].keys((5.1, 0.0), (5.4, 1.0), (6.2, 1.0), (6.4, 0.3))
    def x(self, t):        # where the dog is (model units)
        return 0.6 * ss((t - 1.0) / 0.8) - 0.6 * ss((t - 2.8) / 1.3)
    def base(self, E, t):
        moving = env(t, 0.9, 1.1, 1.9, 2.1) + env(t, 2.7, 2.9, 4.3, 4.5)
        s, e = E.frames('dog_run'); run = E.sample('dog_run', s + (t * FPS) % (e - s))
        st = E.sample(*STAND); return E.mix(st, run, moving)
    def act(self, t, c):
        c.root.x += self.x(t)
        face = -90 * ss((t - 0.9) / 0.2) + 180 * ss((t - 2.3) / 0.4) - 90 * ss((t - 4.3) / 0.25)
        c.spin = face
        c.head(pitch=40 * env(t, 1.85, 2.0, 2.05, 2.25))
        c.tail(wag=32 * sinp(t, 0.2) * (0.4 + 0.6 * env(t, 4.5, 4.8, 6.0, 6.4)), up=20)
        bo = env(t, 5.2, 5.35, 5.45, 5.7); c.root.z += 0.07 * bo; c.front('L', 25 * bo); c.front('R', 25 * bo)
    def props(self, t, M):
        if t < 2.05:                                   # thrown in from the right, bouncing
            k = t / 2.05; x = 1.2 - 0.6 * k; z = 0.065 + 0.5 * abs(math.sin(k * math.pi * 3)) * (1 - k)
            return {'Ball': T((x, -0.45, z)) @ Rx(-700 * k)}
        if t < 4.95:                                   # in the mouth
            m = M['jaw_01'] @ Vector((0, 0.06, -0.02))
            return {'Ball': T(m)}
        k = min(1.0, (t - 4.95) / 0.5)
        return {'Ball': T((0.0, -0.7, 0.065 + 0.25 * (1 - k) * abs(math.cos(k * math.pi * 1.5))))}


class Eat(Clip):
    def __init__(self):
        super().__init__('Eat', 100 / FPS, 'dog_chigouliang', seed=19)
        F = self.face; F['happy'].keys((1.0, 0.0), (1.2, 0.6), (2.9, 0.6), (3.2, 0.9))
        F.extra.append(lambda t: {'jaw': 0.5 * env(t, 1.0, 1.1, 2.8, 2.9) * max(0.0, sinp(t, 0.24))})
    def act(self, t, c): c.tail(wag=16 * sinp(t, 0.4))
    def props(self, t, M): return {'MilkBowl': T((0.0, -0.78, 0.0)) @ Sc(1.5)}


class Chew(Clip):
    """Lies with a bone between the paws, gnawing happily."""
    loop = False
    def __init__(self):
        super().__init__('ChewBone', 5.0, SIT, seed=20)
        F = self.face; F['happy'].d = 0.6; F['ly'].d = -0.3
        F.extra.append(lambda t: {'jaw': 0.45 * max(0.0, sinp(t, 0.3)) * env(t, 0.8, 1.0, 4.4, 4.7)})
    def act(self, t, c):
        lie_pose(c, ss(t / 0.7) * (1 - ss((t - 4.4) / 0.6)))
        c.head(pitch=30 * env(t, 0.6, 0.9, 4.3, 4.7) + 6 * sinp(t, 0.6), roll=12 * sinp(t, 1.2) * env(t, 0.8, 1.0, 4.3, 4.6))
        c.tail(wag=10 * sinp(t, 0.5))
    def props(self, t, M):
        m = M['jaw_01'] @ Vector((0, 0.08, -0.04))
        return {'DogBone': T(m) @ Rz(90) @ Sc(1.3)}


class Butterfly(Clip):
    def __init__(self):
        super().__init__('ChaseButterfly', 6.0, seed=21)
        F = self.face; F.looks = []; F.extra.append(self.look)
        F['happy'].keys((4.6, 0.0), (4.9, 1.0), (5.7, 1.0), (6.0, 0.2))
        F['jaw'].keys((3.4, 0.2), (3.5, 1.0), (3.65, 0.1), (4.9, 0.2), (5.0, 0.7), (5.1, 0.2))
    def bpos(self, t):
        if t < 3.5:
            a = t * 1.8; return Vector((0.45 * math.sin(a), -0.85 + 0.15 * math.cos(a), 1.35 + 0.15 * math.sin(a * 2.3)))
        a = 3.5 * 1.8; p0 = Vector((0.45 * math.sin(a), -0.85 + 0.15 * math.cos(a), 1.35 + 0.15 * math.sin(a * 2.3)))
        k = t - 3.5; return p0 + Vector((0.3 * k, -0.1 * k, 0.6 * k * k + 0.2 * k))
    def look(self, t):
        p = self.bpos(t); k = 1 - env(t, 4.4, 4.6, 6.0, 6.1)
        return {'lx': k * max(-0.45, min(0.45, p.x * 0.9)), 'ly': k * max(-0.3, min(0.35, (p.z - 1.15) * 1.2))}
    def act(self, t, c):
        p = self.bpos(t); h = c.M['head'].translation; d = p - h
        yaw = math.degrees(math.atan2(d.x, -d.y)); pitch = -math.degrees(math.atan2(d.z, math.hypot(d.x, d.y)))
        k = 1 - env(t, 4.3, 4.6, 6.0, 6.1)
        snap = env(t, 3.3, 3.45, 3.55, 3.75)
        c.head(yaw=max(-50, min(50, yaw)) * k * 0.7, pitch=max(-35, min(20, pitch)) * k * 0.6 - 10 * snap)
        c.root.z += 0.12 * snap; c.front('L', 40 * snap); c.front('R', 40 * snap); c.body(pitch=-12 * snap)
        c.tail(wag=24 * sinp(t, 0.3) * (0.4 + 0.6 * env(t, 4.7, 4.9, 5.7, 6.0)), up=15)
    def props(self, t, M):
        if t > 5.4: return {'Butterfly': None}
        p = self.bpos(t); v = self.bpos(t + 0.05) - p
        yaw = math.degrees(math.atan2(-v.x, v.y)) if v.length > 1e-5 else 0
        return {'Butterfly': T(p) @ Rz(yaw) @ Sc(2.2), '_wings': 70 * math.sin(t * 2 * math.pi * 7)}


class BellyUp(Clip):
    """Flops over for belly rubs, paws in the air, wiggling."""
    loop = True; group = 'touch'
    def __init__(self):
        super().__init__('BellyUp', 4.0, SIT, seed=22)
        F = self.face; F['happy'].d = 1.0; F.blinks = []; F.looks = []; F['jaw'].d = 0.5
    def act(self, t, c):
        lie_pose(c, 1.0); c.roll = 150 + 6 * sinp(t, 1.0); c.root.z += 0.42; c.root.x += 0.0
        for s, ph in (('L', 0.0), ('R', 0.5)):
            c.front(s, -20 + 22 * sinp(t, 0.5, ph), 50); c.rear(s, -30 + 18 * sinp(t, 0.6, ph), 30)
        c.head(roll=10 * sinp(t, 2.0), pitch=-10); c.tail(wag=20 * sinp(t, 0.35))


# ------------------------------------------------------------------ touch
class Poke(Clip):
    group = 'touch'
    def __init__(self):
        super().__init__('Poke', 1.1, seed=23)
        F = self.face; F.blinks = []; F.looks = []
        F['bL'].keys((0.0, 0.0), (0.05, 0.9), (0.18, 0.9), (0.28, 0.0)); F['bR'].k = list(F['bL'].k)
        F['pupil'].keys((0.25, 1.0), (0.32, 1.2), (0.9, 1.0)); F['tilt'].keys((0.25, 0.0), (0.4, 0.3), (0.85, 0.3), (1.1, 0.0))
    def act(self, t, c):
        fl = env(t, 0.0, 0.06, 0.14, 0.45)
        c.head(pitch=-10 * fl, roll=12 * env(t, 0.35, 0.5, 0.8, 1.05)); c.body(pitch=-5 * fl); c.ears(back=24 * fl)
        c.squash = 0.07 * fl; c.tail(wag=10 * sinp(t, 0.3) * env(t, 0.4, 0.6, 0.9, 1.1))


class Pet(Clip):
    loop = True; group = 'touch'
    def __init__(self):
        super().__init__('Pet', 160 / FPS, 'dog_dog_petting', seed=24)
        F = self.face; F['happy'].d = 1.0; F.blinks = []; F.looks = []
        F.extra.append(lambda t: {'jaw': 0.3 + 0.15 * sinp(t, 0.6)})
    def act(self, t, c):
        c.head(roll=-12 + 5 * sinp(t, 2.0), pitch=-8); c.ears(back=22); c.tail(wag=30 * sinp(t, 0.3), up=10)
        c.turn('hips', 'Z', 5 * sinp(t, 0.3, 0.5))
    def props(self, t, M):
        ph = (t % 2.0) / 2.0; s = 1.9 * ss(ph / 0.15) * (1 - ss((ph - 0.7) / 0.3))
        top = M['head'] @ Vector((0, 0.32, 0))
        return {'Heart': T(top + Vector((0.1 * math.sin(ph * 6), -0.15, 0.08 + 0.35 * ph))) @ Rz(12 * math.sin(ph * 7)) @ Sc(max(1e-4, s)) if s > 0.01 else None}


class Tickle(Clip):
    group = 'touch'
    def __init__(self):
        super().__init__('Tickle', 2.6, seed=25)
        F = self.face; F['happy'].keys((0.0, 0.0), (0.15, 1.0), (2.3, 1.0), (2.6, 0.2)); F.blinks = []
        F.extra.append(lambda t: {'jaw': env(t, 0.1, 0.2, 2.2, 2.5) * (0.4 + 0.5 * max(0.0, sinp(t, 0.16)))})
    def act(self, t, c):
        k = env(t, 0.0, 0.15, 2.2, 2.6)
        c.turn('hips', 'Z', k * 10 * sinp(t, 0.18)); c.body(roll=k * 8 * sinp(t, 0.3), yaw=k * 6 * sinp(t, 0.2))
        c.head(roll=k * 14 * sinp(t, 0.3, 0.25), pitch=-8 * k); c.tail(wag=k * 36 * sinp(t, 0.16))
        c.rear('L', k * 30 * max(0, sinp(t, 0.2)), k * 30 * max(0, sinp(t, 0.2)))    # the leg-kick
        c.squash = k * 0.04 * sinp(t, 0.16)


class Dangle(Clip):
    loop = True; group = 'touch'
    def __init__(self):
        super().__init__('Dangle', 2.4, seed=26)
        F = self.face; F['pupil'].d = 1.15; F['tilt'].d = 0.45; F['ly'].d = -0.25
    def act(self, t, c):
        for s, ph in (('L', 0.0), ('R', 0.5)):
            c.front(s, 20 + 25 * sinp(t, 0.45, ph), 30); c.rear(s, -10 + 25 * sinp(t, 0.45, ph + 0.25), 20)
        c.body(pitch=-10, roll=4 * sinp(t, 1.2)); c.head(pitch=10); c.ears(back=10); c.tail(wag=8 * sinp(t, 0.6), up=-20)
        c.squash = -0.06


class JumpStart(Clip):
    group = 'jump'
    def __init__(self): super().__init__('JumpStart', 0.4, seed=27, happy=0.3); self.face.blinks = []
    def act(self, t, c):
        k = ss(t / 0.34); c.squash = 0.18 * k; c.body(pitch=-10 * k); c.head(pitch=-8 * k)
        c.rear('L', -10 * k, 20 * k); c.rear('R', -10 * k, 20 * k)


class Air(Clip):
    loop = True; group = 'jump'
    def __init__(self):
        super().__init__('JumpAir', 0.6, seed=28, happy=0.6); self.face.blinks = []; self.face.looks = []; self.face['jaw'].d = 0.5
    def act(self, t, c):
        c.squash = -0.1; c.front('L', 45, 60); c.front('R', 45, 60); c.rear('L', -40, -20); c.rear('R', -40, -20)
        c.body(pitch=-6); c.ears(back=26); c.tail(up=-10)


class Land(Clip):
    group = 'jump'
    def __init__(self):
        super().__init__('JumpLand', 0.7, seed=29, happy=0.5)
        F = self.face; F.blinks = []; F['bL'].keys((0.0, 0.0), (0.06, 0.6), (0.25, 0.0)); F['bR'].k = list(F['bL'].k)
    def act(self, t, c):
        s = 0.25 * math.exp(-t * 7) * math.cos(t * 16) if t > 0.03 else 0.25 * t / 0.03
        c.squash = s; c.body(pitch=8 * max(0.0, s / 0.25)); c.ears(back=-12 * s / 0.25)


class Dance(Clip):
    """Music: bouncy hop-step, head bob on the beat, paws in the air, tail like a metronome."""
    group = 'dance'
    def __init__(self, name='Dance', bpm=118, seed=30):
        super().__init__(name, 8.0, seed=seed, happy=0.6)
        self.beat = 60.0 / bpm
        F = self.face
        t = 0.0
        while t < 8.0:
            F.pulse('jaw', t, 0.04, 0.05, 0.12, 0.6); t += self.beat * 2
        F.pulse('wink', 3.2, 0.08, 0.3, 0.12, 1.0); F['happy'].keys((0.0, 0.5), (1.0, 1.0), (7.0, 1.0), (8.0, 0.5))
    def act(self, t, c):
        b = self.beat; ph = (t % b) / b
        bounce = abs(math.sin(math.pi * ph))
        c.root.z += 0.05 * bounce; c.squash = 0.06 * (1 - bounce) - 0.03
        c.head(pitch=-10 * bounce + 6, roll=10 * sinp(t, b * 4)); c.body(roll=6 * sinp(t, b * 2))
        side = 'L' if int(t / b) % 2 == 0 else 'R'
        c.front(side, 35 * bounce, 40 * bounce)
        c.tail(wag=30 * sinp(t, b), up=20)
        if 4.0 < t < 6.0:        # the big move: up on the hind legs, paws waving
            k = env(t, 4.0, 4.3, 5.7, 6.0)
            c.body(pitch=-38 * k); c.head(pitch=20 * k); c.root.z += 0.12 * k
            c.front('L', 60 * k + 20 * k * sinp(t, b), 60 * k); c.front('R', 60 * k + 20 * k * sinp(t, b, 0.5), 60 * k)


def library():
    return [Idle(), Sniff(), Walk(), Run(), Wag(), SitDown(), Sit(), StandUp(), LieDown(), FallAsleep(), Sleep(), WakeUp(),
            Bark(), Howl(), HeadTilt(), PlayBow(), Shake(), ChaseTail(), Dig(), Fetch(), Eat(), Chew(), Butterfly(),
            Poke(), Pet(), Tickle(), Dangle(), JumpStart(), Air(), Land(), Dance('Dance', 118, 30), Dance('Dance2', 96, 31)]


# ------------------------------------------------------------------ charger
class ChargeRest(Clip):
    """On the charger: lying cosy, chin up, slow content blinks, tail thumping now and then."""
    loop = True; group = 'idle'
    def __init__(self):
        super().__init__('ChargeRest', 6.0, SIT, seed=40, happy=0.5)
        F = self.face; F['bL'].d = F['bR'].d = 0.3; F.blinks = [1.5, 4.2]
    def act(self, t, c):
        b = sinp(t, 3.0); lie_pose(c, 1.0, b); c.head(pitch=4 + 2 * b, yaw=14 * env(t, 2.0, 2.4, 3.4, 3.8))
        c.tail(wag=14 * sinp(t, 0.5) * env(t, 4.0, 4.2, 5.0, 5.3)); c.jaw = 0.15 + 0.05 * b


class Tired(Clip):
    """Low battery: flat out, chin on paws, ears drooping, a huge sigh and a yawn."""
    loop = True; group = 'idle'
    def __init__(self):
        super().__init__('Tired', 6.0, SIT, seed=41)
        F = self.face; F['bL'].d = F['bR'].d = 0.55; F.blinks = [1.0, 3.0, 5.0]; F.pulse('jaw', 3.6, 0.35, 0.5, 0.4, 1.1)
    def act(self, t, c):
        b = sinp(t, 3.0); lie_pose(c, 1.0, b); c.head(pitch=26 + 2 * b - 30 * env(t, 3.5, 3.8, 4.3, 4.7)); c.ears(back=18)
        c.squash += 0.04 * env(t, 1.0, 1.4, 1.8, 2.4)


class Unplugged(Clip):
    """Cable pulled: ears shoot up, a little jump, stares at where the charger was, a sad 'hmph'."""
    def __init__(self):
        super().__init__('Unplugged', 2.4, seed=42)
        F = self.face; F['pupil'].keys((0.0, 1.0), (0.1, 1.3), (1.0, 1.3), (1.4, 1.0)); F.pulse('jaw', 1.9, 0.1, 0.15, 0.2, 0.4)
    def act(self, t, c):
        k = env(t, 0.0, 0.06, 0.25, 0.6); c.root.z += 0.08 * env(t, 0.0, 0.08, 0.12, 0.35); c.ears(back=-14 * k); c.tail(up=20 * k)
        l = env(t, 0.6, 0.9, 1.5, 1.8); c.head(yaw=40 * l, pitch=20 * l); c.ears(back=10 * l); c.tail(wag=-10 * l)


class FullCharge(Clip):
    """100%: ZOOMIES! two circles at full speed, then a proud happy bark."""
    def __init__(self):
        super().__init__('FullCharge', 3.6, 'dog_run', seed=43, happy=1.0)
        self.face.blinks = []; self.face.pulse('jaw', 2.9, 0.05, 0.1, 0.1, 1.1)
    def base(self, E, t):
        if t > 2.6: return E.sample(*STAND)
        s, e = E.frames('dog_run'); return E.sample('dog_run', s + (t * FPS) % (e - s))
    def act(self, t, c):
        if t < 2.6: c.spin = 720 * ss(t / 2.6); c.jaw = 0.6; c.ears(back=18)
        c.tail(wag=25 * sinp(t, 0.25), up=20)



def hang(c, t, k=1.0):
    """Held up by the scruff: the body hangs from the finger, legs and tail straight down, swaying limp."""
    c.turn('hips', 'X', -62 * k); c.root.z -= 0.12 * k
    for s, ph in (('L', 0.0), ('R', 0.5)):
        c.front(s, -62 * k + 7 * sinp(t, 1.6, ph), 6); c.rear(s, -62 * k + 7 * sinp(t, 1.6, ph + 0.3), 4)
    c.tail(up=-55 * k, wag=6 * sinp(t, 1.6)); c.head(pitch=44 * k, roll=5 * sinp(t, 3.2)); c.ears(back=12)


class Dangle(Clip):
    """Lifted by the finger: hangs limp under gravity, paws dangling, a resigned little sway."""
    loop = True; group = 'touch'
    def __init__(self):
        super().__init__('Dangle', 3.2, loop=True, seed=26)
        F = self.face; F['pupil'].d = 1.15; F['tilt'].d = 0.45
    def act(self, t, c): hang(c, t)

def library():
    return [Idle(), Sniff(), Walk(), Run(), Wag(), SitDown(), Sit(), StandUp(), LieDown(), FallAsleep(), Sleep(), WakeUp(),
            Bark(), Howl(), HeadTilt(), PlayBow(), Shake(), ChaseTail(), Dig(), Fetch(), Eat(), Chew(), Butterfly(),
            Poke(), Pet(), Tickle(), Dangle(), JumpStart(), Air(), Land(), Dance('Dance', 118, 30), Dance('Dance2', 96, 31),
            ChargeRest(), Tired(), Unplugged(), FullCharge()]
