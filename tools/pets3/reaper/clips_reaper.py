"""Gromble the tiny reaper (3D look 'Reaper'): tries so hard to be evil, fails adorably.
Meshy motions + hand-made acting: ember eyes, jaw, cape springs, props."""
import math, random, sys
sys.path.insert(0, '/home/claude/duck2/scripts')
from mathutils import Vector, Matrix
from engine2 import rq, ss, env, Track, Face, FPS

BLEND = '/home/claude/reaper/reaper.props.blend'
OUT = '/home/claude/reaper/baked'
MOVER = 'mover'; HIPS = 'mixamorig:Hips'; HIPS_TARGET = (0.0, -0.12)
JAW = ('Bone_036', 'X', 24.0)
SPRINGS = [(['Bone_030', 'Bone_029', 'Bone_028', 'Bone_027', 'Bone_026'], 'cape', 45.0, 5.0)]
E = None
M_ = 'mixamorig:'
H, S, S1, S2, NK, HD = (M_ + n for n in ('Hips', 'Spine', 'Spine1', 'Spine2', 'Neck', 'Head'))
LA, RA, LFA, RFA = (M_ + n for n in ('LeftArm', 'RightArm', 'LeftForeArm', 'RightForeArm'))
LU, LL, LF, RU, RL, RF = (M_ + n for n in ('LeftUpLeg', 'LeftLeg', 'LeftFoot', 'RightUpLeg', 'RightLeg', 'RightFoot'))
STAND = ('Alert', 1)
T = Matrix.Translation
def Sc(s): return Matrix.Diagonal((s, s, s, 1)) if not isinstance(s, tuple) else Matrix.Diagonal((*s, 1))
def Rz(d): return Matrix.Rotation(math.radians(d), 4, 'Z')
def Ry(d): return Matrix.Rotation(math.radians(d), 4, 'Y')
def sinp(t, period, phase=0.0): return math.sin(2 * math.pi * (t / period + phase))


class Ctx:
    def __init__(self, M):
        self.M = M; self.root = Vector(); self.squash = 0.0; self.turns = []; self.jaw = 0.0; self.spin = 0.0; self.roll = 0.0
    def turn(self, bone, axis, deg):
        if abs(deg) > 1e-4: self.turns.append((bone, rq(axis, deg)))
    def arms(self, l, r=None):          # raise (+) / drop (-)
        r = l if r is None else r; self.turn(LA, 'Y', -l); self.turn(RA, 'Y', r)
    def reach(self, l, r=None):         # forward (+)
        r = l if r is None else r; self.turn(LA, 'Z', l); self.turn(RA, 'Z', -r)
    def elbows(self, l, r=None):
        r = l if r is None else r; self.turn(LFA, 'Y', -l); self.turn(RFA, 'Y', r)
    def head(self, pitch=0.0, yaw=0.0, roll=0.0):
        for b, k in ((NK, 0.35), (HD, 0.65)):
            self.turn(b, 'Z', yaw * k); self.turn(b, 'X', pitch * k); self.turn(b, 'Y', roll * k)
    def body(self, pitch=0.0, yaw=0.0, roll=0.0):
        for b in (S, S1, S2):
            self.turn(b, 'X', pitch / 3); self.turn(b, 'Z', yaw / 3); self.turn(b, 'Y', roll / 3)
    def leg(self, side, lift=0.0, knee=None):
        u, l, f = (LU, LL, LF) if side == 'L' else (RU, RL, RF)
        knee = lift * 1.3 if knee is None else knee
        self.turn(u, 'X', -lift); self.turn(l, 'X', knee)


class Clip:
    loop = False; lock = 'lock'; springs = True; group = 'life'
    def __init__(self, name, secs, src=None, rng=None, speed=1.0, loop=None, seed=None, happy=0.0):
        self.name = name; self.src = src; self.rng = rng; self.speed = speed
        if loop is not None: self.loop = loop
        self.secs = secs; self.n = int(round(secs * FPS))
        self.face = Face(secs, seed=seed if seed is not None else sum(map(ord, name)), loop=self.loop, look_amp=(0.3, 0.15))
        self.face['happy'].d = happy; self.face['tilt'].d = -0.25     # his resting face: trying to look mean
    def base(self, E, t):
        if self.src is None: return E.sample(*STAND)
        if isinstance(self.src, tuple): return E.sample(*self.src)
        s, e = self.rng if self.rng else E.frames(self.src)
        f = s + t * FPS * self.speed
        if self.loop: f = s + (f - s) % max(1, (e - s))
        return E.sample(self.src, min(f, e))
    def act(self, t, c): pass
    def props(self, t, M): return {}


def heart_at(p, s, spin=0.0): return T(p) @ Rz(spin) @ Sc(max(1e-4, s)) if s > 0.01 else None


class Idle(Clip):
    loop = True; group = 'idle'
    def __init__(self): super().__init__('Idle', 6.0)
    def act(self, t, c):
        c.body(pitch=1.5 * sinp(t, 3.0), roll=1.5 * sinp(t, 6.0)); c.squash = 0.012 * sinp(t, 3.0)
        c.head(roll=5 * sinp(t, 6.0, 0.2), yaw=14 * (env(t, 1.4, 1.8, 2.6, 3.0) - env(t, 3.8, 4.1, 4.8, 5.2)))
        c.arms(3 * sinp(t, 3.0)); c.elbows(15)
        c.jaw = 0.1 + 0.08 * sinp(t, 3.0)


class LookAround(Clip):
    def __init__(self):
        super().__init__('LookAround', 120 / FPS, 'Alert', seed=2)
        F = self.face; F['pupil'].keys((0.8, 1.0), (1.0, 1.25), (2.6, 1.25), (2.9, 1.0))


class Walk(Clip):
    loop = True; group = 'walk'
    def __init__(self): super().__init__('Walk', 31 / FPS * 3, 'Walking', happy=0.1); self.face.looks = []


class Run(Clip):
    loop = True; group = 'walk'
    def __init__(self):
        super().__init__('Run', 19 / FPS * 4, 'Running'); self.face.looks = []; self.face.blinks = []
        self.face['jaw'].d = 0.5; self.face['tilt'].d = 0.2


class Sneak(Clip):
    """Tiptoes away when your finger comes too close (he panics)."""
    loop = False; group = 'touch'
    def __init__(self):
        super().__init__('Scared', 35 / FPS * 3, 'Cautious_Crouch_Walk_Left_inplace', loop=False, seed=5)
        F = self.face; F['tilt'].d = 0.75; F['pupil'].d = 0.65
        F.looks = [(0, 0, 0), (0.4, 0.35, 0.0), (1.2, -0.35, 0.0), (2.2, 0.3, 0.05), (3.2, 0.0, 0.0)]
    def base(self, E, t):
        s, e = E.frames('Cautious_Crouch_Walk_Left_inplace'); return E.sample('Cautious_Crouch_Walk_Left_inplace', s + (t * FPS) % (e - s))


class EvilLaugh(Clip):
    """Leans back, arms wide, 'MWAHAHA' with a shaking jaw... then a hiccup."""
    def __init__(self):
        super().__init__('EvilLaugh', 3.4, seed=6)
        F = self.face; F.blinks = [3.1]; F.looks = []
        F['tilt'].keys((0.0, -0.25), (0.3, -0.85), (2.3, -0.85), (2.6, 0.2), (3.4, 0.0))
        F['happy'].keys((0.4, 0.0), (0.6, 0.55), (2.2, 0.55), (2.4, 0.0))
        F.extra.append(lambda t: {'jaw': env(t, 0.4, 0.55, 2.2, 2.4) * (0.45 + 0.55 * max(0.0, sinp(t, 0.16)))})
        F['pupil'].keys((2.5, 1.0), (2.6, 0.6), (2.9, 1.0))
    def act(self, t, c):
        k = env(t, 0.2, 0.55, 2.2, 2.5)
        c.body(pitch=-16 * k + 4 * k * sinp(t, 0.16)); c.head(pitch=-22 * k + 5 * k * sinp(t, 0.16))
        c.arms(95 * k + 6 * k * sinp(t, 0.32)); c.elbows(-20 * k); c.reach(-15 * k)
        c.squash = 0.04 * k * sinp(t, 0.16)
        hic = env(t, 2.55, 2.6, 2.65, 2.85); c.root.z += 0.06 * hic; c.squash -= 0.1 * hic; c.arms(30 * hic)


class SpellCast(Clip):
    """The big dark spell... and out pops a little pink heart. Embarrassed, he hides it behind his cape."""
    def __init__(self):
        super().__init__('SpellCast', 7.4, seed=7)
        F = self.face; F.looks = []
        F['tilt'].keys((0.0, -0.3), (0.5, -0.9), (5.0, -0.9), (5.2, 0.7), (6.6, 0.7), (7.0, 0.0))
        F['pupil'].keys((5.0, 1.0), (5.15, 1.4), (6.0, 1.4), (6.4, 1.0))
        F['ly'].keys((5.2, 0.0), (5.5, 0.3), (6.2, -0.25), (6.8, 0.0))
        F.extra.append(lambda t: {'jaw': 0.6 * env(t, 1.0, 1.3, 4.6, 5.0) * (0.5 + 0.5 * sinp(t, 0.6))})
    def base(self, E, t):
        if t < 5.0: return E.sample('mage_soell_cast_5', 1 + min(300.0, t * FPS * 2.0))
        return E.mix(E.sample('mage_soell_cast_5', 301), E.sample(*STAND), ss((t - 5.0) / 0.5))
    def act(self, t, c):
        sh = env(t, 5.0, 5.2, 5.4, 5.7); c.root.z += 0.05 * sh; c.squash = -0.06 * sh
        hide = env(t, 6.0, 6.3, 6.9, 7.3); c.body(yaw=-30 * hide); c.arms(-30 * hide, 30 * hide); c.reach(40 * hide)
    def props(self, t, M):
        if t < 4.9 or t > 6.3: return {'Heart': None}
        k = t - 4.9; p = M[HD] @ Vector((0, 0.15, 0)) + Vector((0.0, -0.45, 0.25 + 0.05 * k))
        return {'Heart': heart_at(p, 2.2 * ss(k / 0.25) * (1 - ss((k - 1.1) / 0.3)), 15 * math.sin(k * 6))}


class Boo(Clip):
    """Jumps at you with 'BOO!' - then realises it was a bit much and waves sorry."""
    def __init__(self):
        super().__init__('Boo', 3.0, seed=8)
        F = self.face; F.looks = []; F.blinks = [2.4]
        F['jaw'].keys((0.4, 0.0), (0.55, 1.2), (0.9, 1.2), (1.1, 0.1))
        F['pupil'].keys((0.4, 1.0), (0.55, 1.45), (1.0, 1.45), (1.2, 1.0))
        F['tilt'].keys((0.4, -0.3), (0.55, -0.9), (1.0, -0.9), (1.4, 0.6), (2.6, 0.6), (3.0, 0.0))
        F['happy'].keys((1.8, 0.0), (2.1, 0.7), (2.8, 0.7), (3.0, 0.0))
    def act(self, t, c):
        crouch = env(t, 0.05, 0.35, 0.38, 0.45); jump = env(t, 0.4, 0.55, 0.85, 1.1)
        c.squash = 0.15 * crouch - 0.12 * jump; c.root.z += 0.1 * jump; c.root.y -= 0.18 * jump
        c.arms(130 * jump - 10 * crouch); c.elbows(-30 * jump); c.reach(25 * jump); c.body(pitch=12 * jump - 8 * crouch)
        sorry = env(t, 1.6, 1.9, 2.6, 2.9)
        c.turn(RA, 'Y', 70 * sorry); c.turn(RFA, 'Y', 30 * sorry * sinp(t, 0.35)); c.head(roll=-12 * sorry, pitch=10 * sorry)


class CapeSwish(Clip):
    """A dramatic spinning cape swish that ends in a wobbly pose."""
    def __init__(self):
        super().__init__('CapeSwish', 95 / FPS, 'Charged_Upward_Slash', seed=9)
        F = self.face; F['tilt'].keys((0.0, -0.3), (0.5, -0.8), (2.5, -0.8), (2.9, 0.4), (3.2, 0.0))
        F['bL'].keys((2.6, 0.0), (2.75, 0.45), (3.1, 0.0)); F['bR'].k = list(F['bL'].k)
    def act(self, t, c):
        w = env(t, 2.6, 2.8, 2.95, 3.15); c.body(roll=10 * w * sinp(t, 0.25))


class Attack(Clip):
    """Big kick... loses his balance, falls on his bony bum, sits up dazed."""
    def __init__(self):
        super().__init__('Attack', 1.4 + 1.1 + 1.6, seed=10)
        F = self.face; F.blinks = []; F.looks = []
        F['tilt'].keys((0.0, -0.9), (1.3, -0.9), (1.6, 0.6), (3.6, 0.6), (4.1, 0.0))
        F['jaw'].keys((0.55, 0.0), (0.65, 0.9), (0.9, 0.0), (1.5, 0.0), (1.6, 1.0), (2.0, 0.2))
        F['bL'].keys((2.4, 0.0), (2.6, 1.0), (3.3, 1.0), (3.5, 0.0)); F['bR'].k = list(F['bL'].k)
    def base(self, E, t):
        if t < 1.4: return E.sample('Boxing_Guard_Right_Straight_Kick', 1 + t * FPS)
        u = t - 1.4
        fall = E.sample('Climb_Attempt_and_Fall_5', 10 + min(26.0, u * FPS))
        if u < 0.3: return E.mix(E.sample('Boxing_Guard_Right_Straight_Kick', 42), fall, ss(u / 0.3))
        if u < 2.4: return fall
        return E.mix(fall, E.sample(*STAND), ss((u - 2.4) / 0.4))
    def act(self, t, c):
        u = t - 2.6; dz = env(u, 0.0, 0.1, 0.8, 1.0); c.head(roll=14 * dz * sinp(t, 0.5))
    def props(self, t, M):      # dizzy stars = little hearts circling
        u = t - 2.5
        if not (0 < u < 1.0): return {'Heart': None}
        p = M[HD] @ Vector((0, 0.3, 0)) + Vector((0.15 * math.cos(u * 9), -0.1, 0.12 + 0.05 * math.sin(u * 9)))
        return {'Heart': heart_at(p, 1.2 * ss(u / 0.15) * (1 - ss((u - 0.8) / 0.2)))}


class Tumble(Clip):
    """Falls out of the sky (from a failed flight) and lands flat."""
    def __init__(self):
        super().__init__('Tumble', 24 / FPS + 35 / FPS + 1.6, seed=11)
        F = self.face; F.blinks = []; F.looks = []
        F['pupil'].keys((0.0, 1.4), (0.8, 1.4)); F['jaw'].keys((0.0, 1.0), (0.8, 1.0), (1.0, 0.2))
        F['bL'].keys((1.2, 0.0), (1.4, 1.0), (2.4, 1.0), (2.6, 0.0)); F['bR'].k = list(F['bL'].k)
        F['tilt'].keys((2.4, 0.7), (3.2, 0.7), (3.6, 0.0))
    def base(self, E, t):
        a = 24 / FPS
        if t < a: return E.sample('Climb_Attempt_and_Fall_1', 1 + t * FPS)
        u = t - a
        if u < 35 / FPS: return E.sample('Climb_Attempt_and_Fall_5', 1 + u * FPS)
        return E.mix(E.sample('Climb_Attempt_and_Fall_5', 36), E.sample(*STAND), ss((u - 35 / FPS - 0.6) / 0.6))
    def act(self, t, c):
        c.root.z += 0.25 * max(0.0, 1.0 - t / 0.8)


class TryToFly(Clip):
    """Flaps his cape like bat wings, hovers for a heroic second, drops."""
    def __init__(self):
        super().__init__('TryToFly', 4.0, seed=12)
        F = self.face; F['tilt'].keys((0.2, -0.3), (0.5, -0.8), (2.4, -0.8), (2.7, 0.75), (3.6, 0.75), (4.0, 0.0))
        F['jaw'].keys((0.4, 0.0), (0.6, 0.4), (2.3, 0.4), (2.5, 1.0), (2.8, 0.1))
        F['pupil'].keys((2.4, 1.0), (2.55, 1.4), (3.0, 1.0))
    def act(self, t, c):
        fl = env(t, 0.3, 0.6, 2.3, 2.45)
        c.arms(fl * (95 + 40 * sinp(t, 0.22))); c.elbows(-20 * fl)
        c.root.z += 0.12 * fl * (0.6 + 0.4 * sinp(t, 0.44)) + 0.08 * env(t, 1.2, 1.6, 2.0, 2.4)
        c.leg('L', 15 * fl * (0.5 + 0.5 * sinp(t, 0.3))); c.leg('R', 15 * fl * (0.5 - 0.5 * sinp(t, 0.3)))
        land = env(t, 2.45, 2.52, 2.6, 2.95); c.squash = 0.25 * land; c.arms(-30 * land)
        c.head(pitch=18 * env(t, 2.8, 3.1, 3.6, 4.0))


class Pet(Clip):
    """He'd never admit it, but he loves it: melts, sways, a tiny heart."""
    loop = True; group = 'touch'
    def __init__(self):
        super().__init__('Pet', 4.0, seed=13)
        F = self.face; F['happy'].d = 1.0; F['tilt'].d = 0.25; F.blinks = []; F.looks = []
        F.extra.append(lambda t: {'jaw': 0.15 * max(0.0, sinp(t, 2.0))})
    def act(self, t, c):
        c.head(roll=-14 + 4 * sinp(t, 2.0), pitch=-6); c.body(roll=4 * sinp(t, 2.0)); c.turn(H, 'Z', 6 * sinp(t, 1.0))
        c.arms(-5); c.elbows(30); c.reach(20); c.squash = 0.03 * sinp(t, 1.0)
    def props(self, t, M):
        ph = (t % 2.0) / 2.0; s = 1.6 * ss(ph / 0.15) * (1 - ss((ph - 0.7) / 0.3))
        top = M[HD] @ Vector((0, 0.75, 0))
        return {'Heart': heart_at(top + Vector((0.08 * math.sin(ph * 6), -0.12, 0.06 + 0.3 * ph)), s, 12 * math.sin(ph * 7))}


class Poke(Clip):
    group = 'touch'
    def __init__(self):
        super().__init__('Poke', 1.1, seed=14)
        F = self.face; F.blinks = []; F.looks = []
        F['bL'].keys((0.0, 0.0), (0.05, 0.9), (0.18, 0.9), (0.28, 0.0)); F['bR'].k = list(F['bL'].k)
        F['tilt'].keys((0.25, -0.25), (0.4, -0.9), (0.85, -0.9), (1.1, -0.25)); F.pulse('jaw', 0.45, 0.05, 0.1, 0.12, 0.8)
    def act(self, t, c):
        fl = env(t, 0.0, 0.06, 0.14, 0.45)
        c.body(pitch=-10 * fl); c.head(pitch=-12 * fl); c.squash = 0.1 * fl; c.arms(25 * env(t, 0.4, 0.5, 0.7, 0.95))


class Tickle(Clip):
    group = 'touch'
    def __init__(self):
        super().__init__('Tickle', 2.6, seed=15)
        F = self.face; F['happy'].keys((0.0, 0.0), (0.15, 1.0), (2.3, 1.0), (2.6, 0.2)); F['tilt'].d = 0.2; F.blinks = []
        F.extra.append(lambda t: {'jaw': env(t, 0.1, 0.2, 2.2, 2.5) * (0.4 + 0.5 * max(0.0, sinp(t, 0.15)))})
    def act(self, t, c):
        k = env(t, 0.0, 0.15, 2.2, 2.6)
        c.turn(H, 'Z', k * 14 * sinp(t, 0.2)); c.body(pitch=k * 6 * sinp(t, 0.15), roll=k * 6 * sinp(t, 0.33))
        c.head(roll=k * 14 * sinp(t, 0.33, 0.25)); c.arms(k * (30 + 20 * sinp(t, 0.15))); c.squash = k * 0.05 * sinp(t, 0.15)


class Dangle(Clip):
    loop = True; group = 'touch'
    def __init__(self):
        super().__init__('Dangle', 2.4, seed=16)
        F = self.face; F['tilt'].d = -0.8; F['pupil'].d = 0.9; F['ly'].d = -0.2
        F.extra.append(lambda t: {'jaw': 0.5 * env(t % 1.2, 0.4, 0.5, 0.6, 0.8)})
    def act(self, t, c):
        c.leg('L', 30 + 30 * sinp(t, 0.35)); c.leg('R', 30 - 30 * sinp(t, 0.35))
        c.arms(40 + 30 * sinp(t, 0.5)); c.body(pitch=-6, roll=5 * sinp(t, 1.2)); c.squash = -0.06


class JumpStart(Clip):
    group = 'jump'
    def __init__(self): super().__init__('JumpStart', 0.4, seed=17); self.face.blinks = []
    def act(self, t, c): k = ss(t / 0.34); c.squash = 0.2 * k; c.arms(-20 * k); c.body(pitch=10 * k)


class Air(Clip):
    loop = True; group = 'jump'
    def __init__(self):
        super().__init__('JumpAir', 0.6, seed=18); self.face.blinks = []; self.face.looks = []; self.face['jaw'].d = 0.6
    def act(self, t, c): c.squash = -0.1; c.arms(40 + 30 * sinp(t, 0.3)); c.leg('L', 25, 50); c.leg('R', 25, 50)


class Land(Clip):
    group = 'jump'
    def __init__(self):
        super().__init__('JumpLand', 0.7, seed=19); self.face.blinks = []
    def act(self, t, c):
        s = 0.28 * math.exp(-t * 7) * math.cos(t * 16) if t > 0.03 else 0.28 * t / 0.03
        c.squash = s; c.arms(40 * math.exp(-t * 5))


class FallAsleep(Clip):
    def __init__(self):
        super().__init__('FallAsleep', 2.6, seed=20)
        F = self.face; F.blinks = [0.5, 1.1]; F.looks = []
        F['bL'].keys((1.0, 0.0), (1.6, 0.6), (1.9, 0.4), (2.5, 1.0)); F['bR'].k = list(F['bL'].k)
        F.pulse('jaw', 0.8, 0.3, 0.4, 0.4, 1.1)
    def act(self, t, c): sit(c, ss(t / 1.2)); c.head(pitch=24 * ss((t - 1.4) / 1.0), roll=10 * ss((t - 1.4) / 1.0))


def sit(c, k, breathe=0.0):
    c.root.z -= 0.2 * k; c.leg('L', 80 * k, 95 * k); c.leg('R', 80 * k, 95 * k)
    c.body(pitch=-6 * k + 1.5 * breathe); c.arms(-8 * k); c.squash = 0.03 * k + 0.02 * breathe


class Sleep(Clip):
    loop = True; group = 'sleep'
    def __init__(self):
        super().__init__('Sleep', 6.0, seed=21)
        F = self.face; F.blinks = []; F.looks = []; F['bL'].d = F['bR'].d = 1.0; F['tilt'].d = 0.0; F['happy'].d = 0.2
        F.extra.append(lambda t: {'jaw': 0.15 * max(0.0, sinp(t, 3.0, -0.1))})
    def act(self, t, c):
        b = sinp(t, 3.0); sit(c, 1.0, b); nod = env(t, 3.4, 4.2, 4.5, 4.9)
        c.head(pitch=24 + 3 * b + 14 * nod - 18 * env(t, 4.7, 4.85, 5.0, 5.4), roll=10)
    def props(self, t, M):
        out = {}; top = M[HD] @ Vector((0, 0.45, 0))
        for i in range(3):
            ph = ((t / 6.0) * 2 + i / 3.0) % 1.0
            s = 2.6 * ss(ph / 0.2) * (1 - ss((ph - 0.75) / 0.25)) * (0.8 + 0.25 * i)
            out[f'Z{i}'] = T(top + Vector((0.16 + 0.2 * ph, -0.1, 0.06 + 0.34 * ph))) @ Ry(-15 + 25 * ph) @ Sc(max(1e-4, s)) if s > 0.01 else None
        return out


class WakeUp(Clip):
    def __init__(self):
        super().__init__('WakeUp', 2.6, seed=22)
        F = self.face; F.blinks = [0.85, 1.1]
        F['bL'].keys((0.0, 1.0), (0.35, 1.0), (0.45, 0.0)); F['bR'].k = list(F['bL'].k)
        F['pupil'].keys((0.35, 1.0), (0.45, 1.4), (1.0, 1.0)); F.pulse('jaw', 1.3, 0.2, 0.4, 0.3, 1.1)
    def act(self, t, c):
        sit(c, 1 - ss((t - 1.9) / 0.6)); c.head(pitch=24 * (1 - ss((t - 0.3) / 0.15)))
        st = env(t, 1.3, 1.6, 2.0, 2.4); c.arms(140 * st); c.body(pitch=-10 * st)


class HeadTilt(Clip):
    """Plotting: hand on chin, head tilts, ember eyes narrow... then a 'got it!' hop."""
    def __init__(self):
        super().__init__('HeadTilt', 2.6, seed=23)
        F = self.face; F.looks = []; F.blinks = [2.3]
        F['tilt'].keys((0.2, -0.3), (0.5, -0.7), (1.6, -0.7), (1.8, 0.3), (2.4, 0.0))
        F['bL'].keys((0.4, 0.0), (0.6, 0.4), (1.5, 0.4), (1.7, 0.0)); F['bR'].k = list(F['bL'].k)
        F['lx'].keys((0.4, 0.0), (0.6, 0.3), (1.5, 0.3), (1.7, 0.0)); F.pulse('jaw', 1.8, 0.06, 0.25, 0.15, 0.8)
    def act(self, t, c):
        k = env(t, 0.15, 0.45, 1.55, 1.8)
        c.head(roll=18 * k, pitch=-4 * k); c.turn(RA, 'Y', 40 * k); c.turn(RA, 'Z', -45 * k); c.turn(RFA, 'Z', -80 * k)
        hop = env(t, 1.75, 1.85, 1.9, 2.1); c.root.z += 0.06 * hop; c.arms(80 * hop)


class Shake(Clip):
    """Rattles every bone, from skull to toes."""
    def __init__(self):
        super().__init__('Shake', 1.6, seed=24)
        F = self.face; F.blinks = []; F.looks = []
        F['bL'].keys((0.1, 0.0), (0.2, 1.0), (1.05, 1.0), (1.2, 0.0)); F['bR'].k = list(F['bL'].k)
        F.extra.append(lambda t: {'jaw': 0.6 * env(t, 0.2, 0.3, 1.0, 1.15) * max(0.0, sinp(t, 0.09))})
    def act(self, t, c):
        k = env(t, 0.1, 0.2, 0.9, 1.15)
        c.turn(H, 'Z', 14 * k * sinp(t, 0.12)); c.head(yaw=-22 * k * sinp(t, 0.12, 0.15), roll=10 * k * sinp(t, 0.12))
        c.arms(k * (25 * sinp(t, 0.12, 0.3))); c.squash = 0.05 * k * sinp(t, 0.06)


class Spin(Clip):
    def __init__(self):
        super().__init__('Spin', 1.6, seed=25)
        F = self.face; F.blinks = []; F.looks = []; F['tilt'].keys((0.0, -0.3), (0.3, -0.8), (1.3, -0.8), (1.6, -0.3))
        F.pulse('jaw', 1.15, 0.06, 0.15, 0.2, 1.0)
    def act(self, t, c):
        c.spin = 360 * ss((t - 0.2) / 0.9); hop = env(t, 0.25, 0.5, 0.7, 1.05)
        c.root.z += 0.07 * hop; c.arms(90 * hop); c.squash = 0.12 * env(t, 1.05, 1.1, 1.15, 1.35)


class Butterfly(Clip):
    """A butterfly lands on his skull. He tries to scare it. It is not scared. He gives up and smiles."""
    def __init__(self):
        super().__init__('ChaseButterfly', 6.0, seed=26)
        F = self.face; F.looks = []; F.extra.append(self.look)
        F['tilt'].keys((0.0, -0.3), (2.0, -0.3), (2.3, -0.9), (3.6, -0.9), (4.2, 0.4), (6.0, 0.2))
        F['jaw'].keys((2.4, 0.0), (2.5, 1.1), (2.9, 1.1), (3.0, 0.0), (3.2, 0.0), (3.3, 1.1), (3.6, 0.0))
        F['happy'].keys((4.4, 0.0), (4.7, 1.0), (5.8, 1.0), (6.0, 0.3))
    def bpos(self, t, M=None):
        if t < 2.0:
            k = t / 2.0; return Vector((0.6 - 0.62 * ss(k), -0.5 + 0.2 * ss(k), 1.0 + 0.6 * ss(k) + 0.08 * math.sin(t * 7)))
        if t < 4.4: return Vector((-0.02, -0.3, 1.6 + 0.01 * math.sin(t * 3)))
        k = t - 4.4; return Vector((-0.02 + 0.3 * k, -0.3 - 0.1 * k, 1.6 + 0.4 * k * k))
    def look(self, t):
        p = self.bpos(t); k = 1 - env(t, 4.8, 5.0, 6.0, 6.1)
        return {'lx': k * max(-0.4, min(0.4, p.x * 0.8)), 'ly': k * max(-0.2, min(0.4, (p.z - 1.3) * 1.2))}
    def act(self, t, c):
        up = env(t, 1.6, 2.0, 4.4, 4.8); c.head(pitch=-20 * up)
        boo = env(t, 2.3, 2.45, 2.9, 3.0) + env(t, 3.2, 3.3, 3.55, 3.7); c.arms(120 * boo); c.root.z += 0.05 * boo
        sh = env(t, 3.8, 4.0, 4.3, 4.5); c.arms(15 * sh); c.head(roll=15 * sh)
    def props(self, t, M):
        if t > 5.6: return {'Butterfly': None}
        p = self.bpos(t); v = self.bpos(t + 0.05) - p
        yaw = math.degrees(math.atan2(-v.x, v.y)) if v.length > 1e-5 else 0
        f = 70 * math.sin(t * 2 * math.pi * 7) if not (2.0 < t < 4.4) else 30 + 25 * math.sin(t * 4)
        return {'Butterfly': T(p) @ Rz(yaw) @ Sc(2.0), '_wings': f}


class Dance(Clip):
    group = 'dance'
    def __init__(self):
        super().__init__('Dance', 245 / FPS, 'All_Night_Dance', seed=27)
        F = self.face; F['tilt'].d = 0.0; F['happy'].keys((0.0, 0.3), (1.0, 0.9), (7.0, 0.9), (8.1, 0.4))
        t = 0.0
        while t < 8.0: F.pulse('jaw', t, 0.05, 0.05, 0.12, 0.7); t += 0.5


class AngryKick(Clip):
    group = 'touch'
    def __init__(self):
        super().__init__('AngryKick', 41 / FPS, 'Boxing_Guard_Right_Straight_Kick', seed=28)
        F = self.face; F['tilt'].d = -1.0; F['pupil'].d = 0.8; F['jaw'].keys((0.55, 0.0), (0.65, 1.0), (0.85, 0.0))


def library():
    return [Idle(), LookAround(), Walk(), Run(), Sneak(), EvilLaugh(), SpellCast(), Boo(), CapeSwish(), Attack(), Tumble(), TryToFly(),
            Pet(), Poke(), Tickle(), Dangle(), JumpStart(), Air(), Land(), FallAsleep(), Sleep(), WakeUp(), HeadTilt(), Shake(), Spin(),
            Butterfly(), Dance(), AngryKick()]


# ------------------------------------------------------------------ charger
class Plugged(Clip):
    """Charger in: 'YES! POWER!' arms up, evil laugh of pure joy, a little spin."""
    def __init__(self):
        super().__init__('Plugged', 2.8, seed=50)
        F = self.face; F['happy'].keys((0.2, 0.0), (0.4, 0.8), (2.4, 0.8), (2.8, 0.2))
        F.extra.append(lambda t: {'jaw': env(t, 0.5, 0.6, 1.8, 2.0) * (0.4 + 0.5 * max(0.0, sinp(t, 0.16)))})
    def act(self, t, c):
        k = env(t, 0.1, 0.35, 2.2, 2.6); c.arms(110 * k + 10 * sinp(t, 0.3) * k); c.elbows(-15 * k); c.head(pitch=-20 * k); c.body(pitch=-10 * k)
        c.spin = 360 * ss((t - 1.0) / 1.0) if t > 1.0 else 0.0; c.root.z += 0.06 * env(t, 1.0, 1.2, 1.8, 2.0)


class ChargeRest(Clip):
    """Charging: sitting smugly, 'plotting', fingers tapping, eyes half shut."""
    loop = True; group = 'idle'
    def __init__(self):
        super().__init__('ChargeRest', 6.0, seed=51)
        F = self.face; F['bL'].d = F['bR'].d = 0.35; F['tilt'].d = -0.6; F.blinks = [2.0, 4.5]
    def act(self, t, c):
        b = sinp(t, 3.0); sit(c, 1.0, b); c.head(pitch=6 + 2 * b, yaw=12 * env(t, 2.0, 2.4, 3.4, 3.8))
        c.reach(35); c.elbows(70 + 8 * max(0.0, sinp(t, 0.25)))


class Tired(Clip):
    """Low battery: slumped, arms dangling, head drooping, a long sigh."""
    loop = True; group = 'idle'
    def __init__(self):
        super().__init__('Tired', 6.0, seed=52)
        F = self.face; F['bL'].d = F['bR'].d = 0.6; F['tilt'].d = 0.3; F.blinks = [1.2, 3.4, 5.2]; F.pulse('jaw', 3.6, 0.3, 0.6, 0.4, 0.9)
    def act(self, t, c):
        b = sinp(t, 3.0); sit(c, 1.0, b); c.head(pitch=28 + 3 * b - 30 * env(t, 3.5, 3.8, 4.4, 4.8), roll=10 * sinp(t, 6.0)); c.arms(-15); c.body(pitch=14)


class Unplugged(Clip):
    """'HEY! I was USING that!' - stomps and shakes a tiny fist."""
    def __init__(self):
        super().__init__('Unplugged', 2.4, seed=53)
        F = self.face; F['tilt'].keys((0.0, 0.0), (0.2, -0.9), (2.0, -0.9), (2.4, -0.25)); F.pulse('jaw', 0.4, 0.1, 0.8, 0.2, 0.8)
    def act(self, t, c):
        k = env(t, 0.0, 0.1, 2.0, 2.4); c.root.z += 0.05 * env(t, 0.0, 0.08, 0.12, 0.3)
        c.turn(RA, 'Y', 120 * k); c.turn(RFA, 'Y', 40 * k + 25 * sinp(t, 0.18) * k); c.body(pitch=8 * k)
        st = env(t, 0.6, 0.7, 0.8, 0.95) + env(t, 1.2, 1.3, 1.4, 1.55); c.leg('L', 25 * st); c.squash += 0.06 * st


class FullCharge(Clip):
    """100%: fully powered villain. Cape flare, the BIGGEST evil laugh, spell sparkles."""
    def __init__(self):
        super().__init__('FullCharge', 3.6, seed=54)
        F = self.face; F['tilt'].keys((0.2, -0.3), (0.5, -0.9), (2.9, -0.9), (3.4, 0.0)); F['happy'].keys((0.4, 0.0), (0.7, 0.8), (3.0, 0.8), (3.4, 0.0))
        F.extra.append(lambda t: {'jaw': env(t, 0.6, 0.8, 2.8, 3.0) * (0.5 + 0.5 * max(0.0, sinp(t, 0.14)))})
    def act(self, t, c):
        k = env(t, 0.2, 0.6, 2.9, 3.4); c.arms(110 * k + 8 * sinp(t, 0.28) * k); c.elbows(-25 * k); c.body(pitch=-18 * k + 4 * sinp(t, 0.14) * k)
        c.head(pitch=-26 * k); c.root.z += 0.1 * k; c.spin = 30 * sinp(t, 1.2) * k
    def props(self, t, M):
        ph = (t / 1.2) % 1.0; h = M[HD].translation
        return {'Heart': heart_at(h + Vector((0.3 * math.sin(ph * 6), -0.15, 0.4 + 0.4 * ph)), 3.0 * ss(ph / 0.2) * (1 - ss((ph - 0.7) / 0.3)))}


def library():
    return [Idle(), LookAround(), Walk(), Run(), Sneak(), EvilLaugh(), SpellCast(), Boo(), CapeSwish(), Attack(), Tumble(), TryToFly(),
            Pet(), Poke(), Tickle(), Dangle(), JumpStart(), Air(), Land(), FallAsleep(), Sleep(), WakeUp(), HeadTilt(), Shake(), Spin(),
            Butterfly(), Dance(), AngryKick(), Plugged(), ChargeRest(), Tired(), Unplugged(), FullCharge()]
