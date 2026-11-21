"""Snowy v2 clip library. Each clip = a Meshy motion (or a still base pose) + my acting on top:
face track, beak, springy tail / wing tips, squash & stretch and props."""
import math, random
from mathutils import Vector, Matrix, Quaternion
from engine2 import rq, ss, env, Track, Face, FPS

H, S, S1, S2, NK, HD = 'mixamorig:Hips', 'mixamorig:Spine', 'mixamorig:Spine1', 'mixamorig:Spine2', 'mixamorig:Neck', 'mixamorig:Head'
LA, RA, LFA, RFA, LH, RH = 'mixamorig:LeftArm', 'mixamorig:RightArm', 'mixamorig:LeftForeArm', 'mixamorig:RightForeArm', 'mixamorig:LeftHand', 'mixamorig:RightHand'
LU, LL, LF, RU, RL, RF = 'mixamorig:LeftUpLeg', 'mixamorig:LeftLeg', 'mixamorig:LeftFoot', 'mixamorig:RightUpLeg', 'mixamorig:RightLeg', 'mixamorig:RightFoot'
AI_PECK = '_01a1e3df_64c8_76d7_8479_bc5d96532a40'
AI_PREEN = '_01a1e3e1_5d8f_71ae_9f3e_742385ebe013'
STAND = ('Joyful_Dance_with_Hand_Sway', 1)
T = Matrix.Translation
def Sc(s): return Matrix.Diagonal((s, s, s, 1)) if not isinstance(s, tuple) else Matrix.Diagonal((*s, 1))
def Rz(d): return Matrix.Rotation(math.radians(d), 4, 'Z')
def Rx(d): return Matrix.Rotation(math.radians(d), 4, 'X')
def Ry(d): return Matrix.Rotation(math.radians(d), 4, 'Y')
def sinp(t, period, phase=0.0): return math.sin(2 * math.pi * (t / period + phase))


class Ctx:
    def __init__(self, M):
        self.M = M; self.root = Vector(); self.squash = 0.0; self.turns = []; self.jaw = 0.0; self.spin = 0.0
    def turn(self, bone, axis, deg):
        if abs(deg) > 1e-4: self.turns.append((bone, rq(axis, deg)))
    def flap(self, l, r=None):
        r = l if r is None else r
        self.turn(LA, 'Y', -l); self.turn(RA, 'Y', r)
    def sweep(self, l, r=None):           # wings forward (+) / back (-)
        r = l if r is None else r
        self.turn(LA, 'Z', l); self.turn(RA, 'Z', -r)
    def head(self, pitch=0.0, yaw=0.0, roll=0.0, neck=0.35):
        for b, k in ((NK, neck), (HD, 1 - neck)):
            self.turn(b, 'Z', yaw * k); self.turn(b, 'X', pitch * k); self.turn(b, 'Y', roll * k)
    def body(self, pitch=0.0, yaw=0.0, roll=0.0):
        for b in (S, S1, S2):
            self.turn(b, 'X', pitch / 3); self.turn(b, 'Z', yaw / 3); self.turn(b, 'Y', roll / 3)
    def leg(self, side, lift=0.0, knee=None):
        u, l, f = (LU, LL, LF) if side == 'L' else (RU, RL, RF)
        knee = lift * 1.3 if knee is None else knee
        self.turn(u, 'X', -lift); self.turn(l, 'X', knee); self.turn(f, 'X', -max(0, knee - lift) * 0.6)


class Clip:
    loop = False
    lock = 'lock'          # 'lock' hips xy to the centre every frame, 'center' only the first frame, None
    springs = True
    group = 'life'
    def __init__(self, name, secs, src=None, rng=None, speed=1.0, loop=None, seed=None):
        self.name = name; self.src = src; self.rng = rng; self.speed = speed
        self.n = int(round(secs * FPS))
        if loop is not None: self.loop = loop
        self.face = Face(secs, seed=seed if seed is not None else hash(name) % 1000, loop=self.loop)
        self.secs = secs
    def base(self, E, t):
        if self.src is None: return E.sample(*STAND)
        s, e = self.rng if self.rng else E.frames(self.src)
        f = s + t * FPS * self.speed
        if self.loop and f > e: f = s + (f - s) % (e - s)
        return E.sample(self.src, min(f, e))
    def act(self, t, c): pass
    def props(self, t, M): return {}


def jaw_tip(M, E):
    return M['jaw'] @ Vector((0, E.length['jaw'], 0))


# ===================================================================== custom acting clips
class Idle(Clip):
    loop = True; group = 'idle'
    def __init__(self): super().__init__('Idle', 6.0); self.face['happy'].d = 0.12
    def act(self, t, c):
        c.body(pitch=1.6 * sinp(t, 3.0), roll=1.2 * sinp(t, 6.0, 0.2))
        c.turn(H, 'Z', 2.5 * sinp(t, 6.0))
        c.head(pitch=-2 + 2.5 * sinp(t, 3.0, 0.25), roll=5 * sinp(t, 6.0, 0.1),
               yaw=16 * (env(t, 1.6, 2.0, 2.9, 3.3) - env(t, 4.0, 4.3, 5.0, 5.4)))
        c.flap(2.5 + 2.5 * sinp(t, 3.0))
        c.squash = 0.012 * sinp(t, 3.0)
        # one happy little wiggle of the tail in the middle
        c.turn(H, 'Z', 5 * env(t, 2.6, 2.75, 3.2, 3.4) * sinp(t, 0.3))


class Quack(Clip):
    group = 'life'
    def __init__(self):
        super().__init__('Quack', 2.0, seed=4)
        F = self.face
        for t0, a in ((0.42, 1.0), (0.86, 0.8)):
            F.pulse('jaw', t0, 0.06, 0.08, 0.16, a)
        F['happy'].keys((0.3, 0.0), (0.42, 0.85), (1.2, 0.85), (1.6, 0.2))
        F.blinks = [1.75]
    def act(self, t, c):
        ant = env(t, 0.05, 0.32, 0.36, 0.44)
        q1 = env(t, 0.38, 0.46, 0.56, 0.72); q2 = env(t, 0.82, 0.9, 0.98, 1.16)
        c.head(pitch=-16 * ant + 18 * q1 + 13 * q2, roll=-6 * q1 + 6 * q2)
        c.body(pitch=-7 * ant + 8 * q1 + 6 * q2)
        c.flap(4 + 30 * q1 * (0.6 + 0.4 * sinp(t, 0.12)) + 24 * q2 * (0.6 + 0.4 * sinp(t, 0.12)))
        c.squash = -0.06 * ant + 0.10 * q1 + 0.07 * q2
        c.root.z += 0.035 * env(t, 0.4, 0.48, 0.5, 0.6)


class TryToFly(Clip):
    def __init__(self):
        super().__init__('TryToFly', 4.6, seed=7)
        F = self.face
        F['tilt'].keys((0.2, 0.0), (0.5, -0.55), (2.6, -0.55), (2.9, 0.75), (4.2, 0.6), (4.6, 0.0))
        F['pupil'].keys((2.7, 1.0), (2.85, 0.75), (3.4, 1.0))
        F['jaw'].keys((0.6, 0.0), (0.8, 0.25), (2.4, 0.25), (2.6, 0.0))
        F['ly'].keys((2.9, 0.0), (3.2, -0.35), (4.3, -0.35), (4.6, 0.0))
        F['bL'].keys((3.6, 0.0), (3.9, 0.45), (4.3, 0.45), (4.6, 0.0)); F['bR'].k = list(F['bL'].k)
    def act(self, t, c):
        crouch = env(t, 0.1, 0.45, 0.5, 0.65)
        fl = env(t, 0.5, 0.7, 2.4, 2.7)
        c.squash = 0.18 * crouch - 0.08 * fl + 0.22 * env(t, 2.62, 2.7, 2.75, 3.05)
        c.body(pitch=12 * crouch - 6 * fl + 14 * env(t, 2.7, 3.0, 4.0, 4.5))
        c.flap(-15 * crouch + fl * (35 + 42 * sinp(t, 0.14)))
        hop = fl * max(0.0, math.sin(2 * math.pi * (t - 0.5) / 0.45)) ** 1.5
        c.root.z += 0.07 * hop + 0.05 * env(t, 1.6, 1.9, 2.2, 2.6)
        c.leg('L', 18 * fl * (0.5 + 0.5 * sinp(t, 0.3))); c.leg('R', 18 * fl * (0.5 - 0.5 * sinp(t, 0.3)))
        c.head(pitch=-12 * fl + 22 * env(t, 2.8, 3.2, 4.1, 4.6), roll=8 * env(t, 3.0, 3.3, 4.1, 4.6))
        c.flap(-8 * env(t, 2.8, 3.1, 4.1, 4.6))


class SitDown(Clip):
    def __init__(self):
        super().__init__('SitDown', 1.2, seed=9); self.face['happy'].keys((0.0, 0.1), (1.0, 0.35))
    def act(self, t, c): sit_pose(c, ss(t / 1.1), t)


def sit_pose(c, k, t, breathe=0.0):
    c.root.z -= 0.105 * k
    c.leg('L', 70 * k, 100 * k); c.leg('R', 70 * k, 100 * k)
    c.body(pitch=-4 * k + 1.5 * breathe)
    c.flap(-6 * k + 2 * breathe)
    c.squash = 0.06 * k + 0.02 * breathe


class Sleep(Clip):
    loop = True; group = 'sleep'
    def __init__(self):
        super().__init__('Sleep', 6.0, seed=10)
        F = self.face; F.blinks = []; F.looks = []
        F['bL'].d = F['bR'].d = 1.0; F['happy'].d = 0.25
        F.extra.append(lambda t: {'jaw': 0.12 * max(0.0, sinp(t, 3.0, -0.1))})
    def act(self, t, c):
        b = sinp(t, 3.0)
        sit_pose(c, 1.0, t, b)
        nod = env(t, 3.4, 4.2, 4.5, 4.9)
        c.head(pitch=20 + 3 * b + 14 * nod - 16 * env(t, 4.7, 4.85, 5.0, 5.4), roll=12 * (1 - nod) + 4 * sinp(t, 6.0))
    def props(self, t, M):
        out = {}
        top = M[HD] @ Vector((0, 0.35, 0))
        for i in range(3):
            ph = ((t / 6.0) * 2 + i / 3.0) % 1.0
            s = 2.4 * ss(ph / 0.2) * (1 - ss((ph - 0.75) / 0.25)) * (0.8 + 0.25 * i)
            pos = top + Vector((0.14 + 0.18 * ph + 0.03 * math.sin(ph * 9), -0.1, 0.05 + 0.32 * ph))
            out[f'Z{i}'] = T(pos) @ Ry(-15 + 25 * ph) @ Sc(max(1e-4, s)) if s > 0.01 else None
        return out


class WakeUp(Clip):
    def __init__(self):
        super().__init__('WakeUp', 2.6, seed=11)
        F = self.face; F.blinks = [0.85, 1.12]
        F['bL'].keys((0.0, 1.0), (0.35, 1.0), (0.45, 0.0)); F['bR'].k = list(F['bL'].k)
        F['pupil'].keys((0.35, 1.0), (0.45, 0.7), (1.0, 0.95))
        F['jaw'].keys((1.4, 0.0), (1.6, 1.0), (2.0, 1.0), (2.25, 0.0))
        F['happy'].keys((1.4, 0.0), (1.6, 1.0), (2.1, 1.0), (2.4, 0.2))
    def act(self, t, c):
        k = 1 - ss((t - 1.9) / 0.6)
        sit_pose(c, k, t)
        c.head(pitch=20 * (1 - ss((t - 0.3) / 0.15)) - 14 * env(t, 1.4, 1.6, 2.0, 2.3), roll=12 * (1 - ss((t - 0.3) / 0.15)))
        st = env(t, 1.3, 1.6, 2.0, 2.4)
        c.flap(55 * st + 6 * st * sinp(t, 0.25)); c.body(pitch=-10 * st); c.squash -= 0.07 * st


class Pet(Clip):
    loop = True; group = 'touch'
    def __init__(self):
        super().__init__('Pet', 4.0, seed=12)
        F = self.face; F['happy'].d = 1.0; F.blinks = []; F.looks = []
        F.extra.append(lambda t: {'jaw': 0.18 * max(0.0, sinp(t, 2.0, 0.1)) ** 2})
    def act(self, t, c):
        c.head(roll=-14 + 4 * sinp(t, 2.0), pitch=-7 + 2 * sinp(t, 1.0), yaw=4 * sinp(t, 4.0))
        c.turn(H, 'Z', 7 * sinp(t, 0.67)); c.body(roll=-3 + 2 * sinp(t, 2.0))
        c.flap(8 + 6 * sinp(t, 0.33)); c.squash = 0.03 * sinp(t, 1.0)
    def props(self, t, M):
        ph = (t % 2.0) / 2.0
        s = 1.4 * ss(ph / 0.15) * (1 - ss((ph - 0.7) / 0.3))
        top = M[HD] @ Vector((0, 0.42, 0))
        return {'Heart': T(top + Vector((0.08 * math.sin(ph * 6), -0.12, 0.06 + 0.3 * ph))) @ Rz(12 * math.sin(ph * 7)) @ Sc(max(1e-4, s)) if s > 0.01 else None}


class Tickle(Clip):
    group = 'touch'
    def __init__(self):
        super().__init__('Tickle', 2.6, seed=13)
        F = self.face; F['happy'].keys((0.0, 0.0), (0.15, 1.0), (2.3, 1.0), (2.6, 0.2)); F.blinks = []
        F.extra.append(lambda t: {'jaw': env(t, 0.1, 0.2, 2.2, 2.5) * (0.35 + 0.45 * max(0.0, sinp(t, 0.17)))})
    def act(self, t, c):
        k = env(t, 0.0, 0.15, 2.2, 2.6)
        c.turn(H, 'Z', k * (12 * sinp(t, 0.2) + 4 * sinp(t, 0.13)))
        c.body(pitch=k * (-6 + 5 * sinp(t, 0.17)), roll=k * 6 * sinp(t, 0.33))
        c.head(roll=k * 12 * sinp(t, 0.33, 0.25), pitch=-8 * k)
        c.flap(k * (25 + 25 * sinp(t, 0.15))); c.squash = k * 0.06 * sinp(t, 0.17)
        c.root.z += k * 0.02 * abs(sinp(t, 0.34))


class Dangle(Clip):
    loop = True; group = 'touch'
    def __init__(self):
        super().__init__('Dangle', 2.4, seed=14)
        F = self.face; F['pupil'].d = 0.8; F['tilt'].d = 0.5; F['ly'].d = -0.25
        F.extra.append(lambda t: {'jaw': 0.6 * env(t % 1.2, 0.5, 0.56, 0.62, 0.75)})
    def act(self, t, c):
        c.leg('L', 30 + 28 * sinp(t, 0.4)); c.leg('R', 30 - 28 * sinp(t, 0.4))
        c.flap(30 + 28 * sinp(t, 0.3)); c.body(pitch=-6, roll=4 * sinp(t, 1.2))
        c.head(pitch=14, roll=6 * sinp(t, 2.4)); c.squash = -0.08


class JumpStart(Clip):
    group = 'jump'
    def __init__(self):
        super().__init__('JumpStart', 0.4, seed=15); self.face['happy'].d = 0.3; self.face.blinks = []
    def act(self, t, c):
        k = ss(t / 0.34); c.squash = 0.24 * k; c.flap(-22 * k); c.sweep(-12 * k); c.body(pitch=12 * k); c.head(pitch=-8 * k)


class Air(Clip):
    loop = True; group = 'jump'
    def __init__(self):
        super().__init__('Air', 0.6, seed=16)
        F = self.face; F['happy'].d = 0.55; F['jaw'].d = 0.3; F.blinks = []; F.looks = []
    def act(self, t, c):
        c.squash = -0.12; c.flap(40 + 38 * sinp(t, 0.3)); c.leg('L', 22, 50); c.leg('R', 22, 50); c.body(pitch=-4); c.head(pitch=-6)


class Land(Clip):
    group = 'jump'
    def __init__(self):
        super().__init__('Land', 0.7, seed=17)
        F = self.face; F.blinks = []; F['bL'].keys((0.0, 0.0), (0.06, 0.7), (0.25, 0.0)); F['bR'].k = list(F['bL'].k)
        F['happy'].keys((0.0, 0.3), (0.5, 0.5))
    def act(self, t, c):
        s = 0.3 * math.exp(-t * 7) * math.cos(t * 16) if t > 0.03 else 0.3 * t / 0.03
        c.squash = s; c.flap(30 * math.exp(-t * 5)); c.body(pitch=10 * max(0.0, s / 0.3)); c.head(pitch=-5 * s / 0.3)


class Splash(Clip):
    def __init__(self):
        super().__init__('Splash', 4.0, seed=18)
        F = self.face; F['happy'].keys((0.4, 0.0), (0.7, 0.9), (3.6, 0.9), (4.0, 0.2))
        self.stomps = [0.8, 1.35, 1.9, 2.45, 3.0]
        for i, s in enumerate(self.stomps): F.pulse('jaw', s, 0.05, 0.1, 0.15, 0.7)
        F['ly'].keys((0.0, 0.0), (0.4, -0.35), (0.7, -0.1))
    def act(self, t, c):
        for i, s in enumerate(self.stomps):
            side = 'L' if i % 2 == 0 else 'R'
            up = env(t, s - 0.32, s - 0.12, s - 0.1, s)
            c.leg(side, 38 * up); c.body(roll=(6 if side == 'L' else -6) * up)
            hit = env(t, s, s + 0.04, s + 0.06, s + 0.3)
            c.squash += 0.16 * hit; c.flap(28 * hit); c.root.z += 0.03 * up
        c.head(pitch=12 * env(t, 0.1, 0.4, 0.6, 0.8) - 6 * env(t, 0.8, 1.0, 3.2, 3.6))
    def props(self, t, M):
        out = {'Puddle': T((0.0, -0.06, 0.002)) @ Sc((1.25, 1.25, 1.0))}
        best = None
        for i, s in enumerate(self.stomps):
            if s <= t < s + 0.5: best = (t - s, i)
        if best:
            d, i = best; k = d / 0.5
            x = 0.09 if i % 2 == 0 else -0.09
            out['Drops'] = T((x, -0.08, 0.0)) @ Rz(40 * i) @ Sc((0.6 + 1.0 * ss(k / 0.4), 0.6 + 1.0 * ss(k / 0.4), max(1e-4, 1.6 * (1 - ss((k - 0.3) / 0.7)))))
        else: out['Drops'] = None
        return out


class RubberDuck(Clip):
    def __init__(self):
        super().__init__('RubberDuck', 5.0, seed=19)
        F = self.face
        F['lx'].keys((0.0, 0.0), (0.3, 0.3), (3.1, 0.3), (3.4, 0.0)); F['ly'].keys((0.0, 0.0), (0.3, -0.25), (2.0, -0.25), (2.3, 0.0))
        F['tilt'].keys((0.3, 0.0), (0.7, 0.35), (1.4, 0.35), (1.6, 0.0))
        F['pupil'].keys((2.0, 1.0), (2.08, 0.62), (2.7, 0.62), (3.0, 1.0))
        F['jaw'].keys((1.85, 0.0), (1.95, 0.5), (2.05, 0.0), (3.5, 0.0), (3.6, 1.0), (3.75, 0.0), (3.95, 1.0), (4.1, 0.0))
        F['happy'].keys((3.1, 0.0), (3.4, 1.0), (4.6, 1.0), (5.0, 0.2))
        F.blinks = [0.9, 4.75]
    def act(self, t, c):
        look = env(t, 0.2, 0.6, 1.5, 1.7); poke = env(t, 1.6, 1.85, 1.95, 2.1)
        jump = env(t, 2.05, 2.15, 2.35, 2.7)
        c.head(yaw=-22 * look - 26 * poke * 0.6 + 0 * jump, pitch=14 * look + 30 * poke - 15 * jump, roll=14 * look)
        c.body(pitch=12 * poke + 10 * look * 0.5 - 8 * jump, yaw=-12 * (look + poke * 0.5))
        c.root.z += 0.11 * env(t, 2.06, 2.18, 2.22, 2.4); c.squash = -0.1 * env(t, 2.06, 2.15, 2.2, 2.4) + 0.15 * env(t, 2.35, 2.42, 2.45, 2.7)
        c.flap(50 * jump)
        hap = env(t, 3.3, 3.5, 4.4, 4.9)
        c.flap(hap * (30 + 25 * sinp(t, 0.2))); c.turn(H, 'Z', 10 * hap * sinp(t, 0.5)); c.head(roll=10 * hap * sinp(t, 1.0))
        c.squash += 0.07 * env(t, 3.55, 3.62, 3.68, 3.8) + 0.07 * env(t, 3.9, 3.97, 4.03, 4.15)
    def props(self, t, M):
        sq = env(t, 1.9, 1.95, 2.0, 2.2)
        hop = 0.05 * env(t, 3.55, 3.65, 3.7, 3.85)
        return {'RubberDuck': T((0.26, -0.44, hop)) @ Rz(140 + 10 * sinp(t, 2.5)) @ Sc((2.3 * (1 + 0.2 * sq), 2.3 * (1 + 0.2 * sq), 2.3 * (1 - 0.35 * sq)))}


class Chase(Clip):
    """A butterfly circles Snowy; she tracks it with head + eyes, snaps at it, it escapes, she giggles."""
    def __init__(self):
        super().__init__('ChaseButterfly', 6.0, seed=20)
        F = self.face; F.looks = []
        F['happy'].keys((4.6, 0.0), (4.9, 1.0), (5.7, 1.0), (6.0, 0.1))
        F['pupil'].keys((0.0, 1.0), (0.5, 1.15), (3.6, 1.15), (3.8, 0.7), (4.4, 0.95))
        F['jaw'].keys((3.45, 0.0), (3.55, 0.9), (3.7, 0.0), (4.9, 0.0), (5.0, 0.6), (5.1, 0.0), (5.25, 0.6), (5.35, 0.0))
        F.extra.append(self.look)
    def bpos(self, t):
        if t < 3.5:
            a = t * 1.9
            return Vector((0.42 * math.sin(a), -0.38 + 0.18 * math.cos(a), 0.95 + 0.12 * math.sin(a * 2.3)))
        k = (t - 3.5)
        a = 3.5 * 1.9
        p0 = Vector((0.42 * math.sin(a), -0.38 + 0.18 * math.cos(a), 0.95 + 0.12 * math.sin(a * 2.3)))
        return p0 + Vector((0.25 * k, -0.15 * k, 0.55 * k * k + 0.2 * k))
    def look(self, t):
        p = self.bpos(t); k = 1 - env(t, 4.4, 4.6, 6.0, 6.1)
        return {'lx': k * max(-0.4, min(0.4, p.x * 0.8)), 'ly': k * max(-0.3, min(0.3, (p.z - 0.72) * 0.9))}
    def act(self, t, c):
        p = self.bpos(t); h = c.M[HD].translation
        d = p - h
        yaw = math.degrees(math.atan2(d.x, -d.y)); pitch = -math.degrees(math.atan2(d.z - 0.25, math.hypot(d.x, d.y)))
        k = 1 - env(t, 4.3, 4.6, 6.0, 6.1)
        yaw = max(-45, min(45, yaw)) * k * 0.7; pitch = max(-30, min(20, pitch)) * k * 0.6
        snap = env(t, 3.3, 3.45, 3.55, 3.75)
        c.head(yaw=yaw, pitch=pitch + 16 * snap); c.body(yaw=yaw * 0.35, pitch=8 * snap)
        c.root.z += 0.06 * env(t, 3.35, 3.45, 3.5, 3.7); c.flap(35 * snap)
        gig = env(t, 4.7, 4.9, 5.6, 6.0)
        c.turn(H, 'Z', 8 * gig * sinp(t, 0.4)); c.flap(gig * (12 + 10 * sinp(t, 0.2))); c.squash = 0.05 * gig * sinp(t, 0.25)
    def props(self, t, M):
        if t > 5.4: return {'Butterfly': None}
        p = self.bpos(t); v = self.bpos(t + 0.05) - p
        yaw = math.degrees(math.atan2(-v.x, v.y)) if v.length > 1e-5 else 0
        f = 70 * math.sin(t * 2 * math.pi * 7)
        return {'Butterfly': T(p) @ Rz(yaw) @ Sc(1.7), '_wings': f}


# ===================================================================== Meshy clips + my acting
class Meshy(Clip):
    def __init__(self, name, src, loop=False, lock='lock', group='life', speed=1.0, seed=None, rng=None, secs=None, happy=0.0):
        self._E = None
        self.loop = loop; self.lock = lock; self.group = group
        self._src = src; self._rng = rng; self._speed = speed; self._seed = seed; self._secs = secs; self._happy = happy
        self.name = name
    def setup(self, E):
        s, e = self._rng if self._rng else E.frames(self._src)
        secs = self._secs or (e - s) / FPS / self._speed
        Clip.__init__(self, self.name, secs, self._src, self._rng, self._speed, self.loop, self._seed)
        self.face['happy'].d = self._happy
        self.extra(E)
    def extra(self, E): pass


def beats_from(E, src, rng=None, bone=H, thresh=0.006):
    """frames where the hips bottom out (dance beats)."""
    s, e = rng if rng else E.frames(src)
    zs = []
    for f in range(s, e + 1):
        zs.append(E.fk(E.sample(src, f))[bone].translation.z)
    out = []
    for i in range(2, len(zs) - 2):
        if zs[i] <= min(zs[i - 2:i + 3]) and max(zs[max(0, i - 8):i + 9]) - zs[i] > thresh: out.append((i) / FPS)
    return out


class Dance(Meshy):
    def __init__(self, name, src, seed, **kw):
        super().__init__(name, src, loop=True, group='dance', seed=seed, happy=0.5, **kw)
    def extra(self, E):
        F = self.face; rnd = random.Random(self._seed)
        beats = beats_from(E, self._src, self._rng)
        for i, b in enumerate(beats):
            if rnd.random() < 0.55: F.pulse('jaw', b - 0.05, 0.05, 0.05, 0.12, rnd.uniform(0.35, 0.8))
        # happy-squint waves and the odd wink
        t = 1.0
        while t < self.secs - 1.2:
            F['happy'].keys((t, 0.5), (t + 0.25, 1.0), (t + 1.2, 1.0), (t + 1.5, 0.5))
            if rnd.random() < 0.5: F.pulse('wink', t + 1.8, 0.08, 0.25, 0.12, 1.0)
            t += rnd.uniform(3.0, 5.0)


class Peck(Meshy):
    """Meshy AI 'pecking' motion: three bread crumbs, gobbled one per peck, chewing smile."""
    def __init__(self): super().__init__('Peck', AI_PECK, loop=False, group='eat', seed=21, lock='center')
    def extra(self, E):
        F = self.face; s, e = E.frames(self._src)
        tips = []; zs = []
        for f in range(s, e + 1):
            M = E.fk(E.sample(self._src, f)); p = jaw_tip(M, E); zs.append(p.z); tips.append(p)
        pk = [i for i in range(3, len(zs) - 3) if zs[i] <= min(zs[i - 3:i + 4]) and zs[i] < 0.08]
        self.pecks = []
        for i in pk:
            if not self.pecks or i - self.pecks[-1][0] > 12: self.pecks.append((i, tips[i].copy()))
        print('PECKS', [(i, round(p.x, 3), round(p.y, 3), round(p.z, 3)) for i, p in self.pecks])
        for i, p in self.pecks:
            t = i / FPS
            F.pulse('jaw', t - 0.16, 0.1, 0.03, 0.06, 0.9)
            F['happy'].keys((t + 0.15, 0.0), (t + 0.3, 0.8), (t + 0.9, 0.8), (t + 1.2, 0.1))
            F.extra.append(lambda tt, t=t: {'jaw': 0.25 * env(tt, t + 0.2, t + 0.25, t + 0.9, t + 1.0) * max(0.0, sinp(tt - t, 0.18))})
        F['ly'].d = -0.2
    def props(self, t, M):
        out = {}
        for k in range(3):
            if k < len(self.pecks):
                i, p = self.pecks[k * max(1, len(self.pecks) // 3)] if k * max(1, len(self.pecks) // 3) < len(self.pecks) else self.pecks[-1]
                gone = t > i / FPS + 0.02
                out[f'Crumb{k}'] = None if gone else T((p.x + self.off.x, p.y + self.off.y, 0.004)) @ Rz(40 * k) @ Sc(3.2)
            else: out[f'Crumb{k}'] = None
        return out


class Preen(Meshy):
    def __init__(self): super().__init__('Preen', AI_PREEN, loop=False, group='life', seed=22, happy=0.25, lock='center')
    def extra(self, E):
        F = self.face
        F['bL'].keys((1.0, 0.0), (1.5, 0.55), (4.0, 0.55), (4.3, 0.0), (6.0, 0.0), (6.4, 0.6), (8.6, 0.6), (9.0, 0.0)); F['bR'].k = list(F['bL'].k)
        F['happy'].keys((4.4, 0.25), (4.6, 0.9), (5.4, 0.9), (5.8, 0.25))
        F['pupil'].keys((4.9, 1.0), (5.05, 1.2), (6.0, 1.0))
    def props(self, t, M):
        t0 = 4.6
        if t < t0: return {'Feather': None}
        k = t - t0
        if k > 5: return {'Feather': None}
        land = min(1.0, k / 4.0)
        p = Vector((0.24 + 0.08 * math.sin(k * 2.2), -0.18 - 0.05 * k, max(0.006, 0.62 * (1 - ss(land)))))
        return {'Feather': T(p) @ Rz(30 * math.sin(k * 2.0)) @ Rx(20 * math.sin(k * 3.1) * (1 - land)) @ Sc(1.4)}


class Carry(Meshy):
    def __init__(self): super().__init__('CarryBread', 'Carry_Heavy_Object_Walk_inplace', loop=True, group='eat', seed=23)
    def extra(self, E):
        F = self.face; F['tilt'].d = -0.3; F['bL'].d = F['bR'].d = 0.22; F['pupil'].d = 1.05
        F.extra.append(lambda t: {'jaw': 0.15 * max(0.0, sinp(t, 1.1))})
    def props(self, t, M):
        a = M[LH].translation; b = M[RH].translation; m = (a + b) / 2
        x = (a - b); ang = math.degrees(math.atan2(x.z, x.x))
        return {'Bread': T(m + Vector((0, -0.13, 0.02))) @ Ry(-ang) @ Rx(75) @ Sc(2.4)}


class Startle(Meshy):
    def __init__(self): super().__init__('Startle', 'Crouch_and_Step_Back', group='touch', seed=24)
    def extra(self, E):
        F = self.face; F.blinks = [1.6, 2.2]
        F['pupil'].keys((0.0, 1.0), (0.12, 0.55), (1.2, 0.6), (1.8, 1.0))
        F['jaw'].keys((0.0, 0.0), (0.1, 1.0), (0.35, 1.0), (0.55, 0.0))
        F['tilt'].keys((0.4, 0.0), (0.7, 0.5), (2.0, 0.5), (2.6, 0.0))
    def act(self, t, c):
        c.flap(45 * env(t, 0.0, 0.08, 0.3, 0.6)); c.root.z += 0.05 * env(t, 0.0, 0.08, 0.12, 0.3)


class Tumble(Meshy):
    """BeHit_FlyUp -> dizzy on the floor (head wobble, eyes shut) -> scrambles up with a hop, shakes it off."""
    def __init__(self): super().__init__('Tumble', 'BeHit_FlyUp', group='touch', seed=25, lock='lock')
    def setup(self, E):
        a = E.frames('BeHit_FlyUp')
        self.la = (a[1] - a[0]) / FPS; self.hold = 1.2; self.up = 0.7
        Clip.__init__(self, self.name, self.la + self.hold + self.up + 1.4, None, None, 1.0, False, 25)
        t1 = self.la + self.hold; t2 = t1 + self.up
        F = self.face; F.blinks = [t2 + 0.9]; F.looks = []
        F['pupil'].keys((0.0, 1.0), (0.1, 0.55), (0.9, 0.55), (1.0, 1.0))
        F['jaw'].keys((0.0, 0.0), (0.08, 1.0), (0.5, 0.6), (0.9, 0.0))
        F['bL'].keys((0.9, 0.0), (1.1, 1.0), (t1 + 0.1, 1.0), (t1 + 0.3, 0.0)); F['bR'].k = list(F['bL'].k)
        F['tilt'].keys((t1, 0.0), (t1 + 0.3, 0.6), (t2 + 0.4, 0.6), (t2 + 0.8, 0.0))
        F['happy'].keys((t2 + 0.5, 0.0), (t2 + 0.8, 1.0), (self.secs - 0.1, 0.6))
        F['lx'].keys((t1 + 0.3, 0.0), (t1 + 0.5, 0.3), (t1 + 0.7, -0.3), (t1 + 0.9, 0.0))
    def base(self, E, t):
        end = E.frames('BeHit_FlyUp')[1]
        if t < self.la: return E.sample('BeHit_FlyUp', 1 + t * FPS)
        A = E.sample('BeHit_FlyUp', end)
        t1 = self.la + self.hold
        if t < t1: return A
        return E.mix(A, E.sample(*STAND), ss((t - t1) / self.up))
    def act(self, t, c):
        t1 = self.la + self.hold; t2 = t1 + self.up
        d = env(t, self.la, self.la + 0.1, t1 - 0.1, t1)
        c.head(roll=12 * d * sinp(t, 0.6), yaw=8 * d * sinp(t, 0.9))
        c.root.z += 0.09 * env(t, t1 + 0.15, t1 + 0.45, t1 + 0.5, t2 + 0.05)
        c.squash = 0.2 * env(t, t2, t2 + 0.06, t2 + 0.1, t2 + 0.35)
        sh = env(t, t2 + 0.35, t2 + 0.45, t2 + 0.85, t2 + 1.0)
        c.head(yaw=18 * sh * sinp(t, 0.16)); c.flap(30 * sh * (0.5 + 0.5 * sinp(t, 0.16)))


class Kick(Meshy):
    def __init__(self): super().__init__('AngryKick', 'Boxing_Guard_Right_Straight_Kick', group='touch', seed=26)
    def extra(self, E):
        F = self.face; F['tilt'].keys((0.0, -0.3), (0.2, -0.95), (1.2, -0.95), (1.4, -0.4))
        F['pupil'].d = 0.85; F['jaw'].keys((0.55, 0.0), (0.65, 0.9), (0.85, 0.0))


class Scared(Meshy):
    def __init__(self): super().__init__('Scared', 'Cautious_Crouch_Walk_Backward_inplace', loop=True, group='touch', seed=27)
    def extra(self, E):
        F = self.face; F['tilt'].d = 0.7; F['pupil'].d = 0.7
        F.looks = [(0, 0, 0), (0.3, 0.3, 0.0), (0.7, -0.3, 0.0), (1.1, 0.0, -0.1)]


class Walk(Meshy):
    def __init__(self): super().__init__('Walk', 'Walking', loop=True, group='walk', seed=28, happy=0.2)
    def extra(self, E): self.face.looks = []; self.face.blinks = [0.4]


class Run(Meshy):
    def __init__(self): super().__init__('Run', 'Running', loop=True, group='walk', seed=29)
    def extra(self, E):
        F = self.face; F.looks = []; F.blinks = []; F['tilt'].d = -0.25; F['jaw'].d = 0.25; F['happy'].d = 0.3
    def act(self, t, c): c.flap(10 + 12 * sinp(t, 20 / FPS))


class Curious(Meshy):
    def __init__(self): super().__init__('Curious', 'Alert', loop=True, group='idle', seed=30)
    def extra(self, E):
        F = self.face; F['pupil'].d = 1.12; F['tilt'].keys((0.6, 0.0), (1.0, 0.3), (2.6, 0.3), (3.0, 0.0))
        F.looks = [(0.0, 0.0, 0.0), (0.9, -0.3, 0.05), (2.2, 0.3, 0.05), (3.4, 0.0, 0.0)]


class Notice(Meshy):
    def __init__(self): super().__init__('Notice', 'Alert_Quick_Turn_Right', group='life', seed=31, rng=(1, 40), secs=2.0)
    def base(self, E, t):
        # turn and look (frames 1..40), then hold and come back to centre
        f = 1 + min(39.0, t * FPS * 1.4) if t < 1.2 else 40 - min(39.0, (t - 1.2) * FPS * 1.6)
        return E.sample('Alert_Quick_Turn_Right', f)
    def extra(self, E):
        F = self.face; F['pupil'].keys((0.1, 1.0), (0.25, 0.8), (1.0, 1.1)); F['lx'].keys((0.1, 0.0), (0.3, 0.35), (1.2, 0.35), (1.5, 0.0))
        F['tilt'].keys((0.4, 0.0), (0.7, 0.35), (1.3, 0.35), (1.7, 0.0))


class Poke(Clip):
    """A quick flinch when tapped: squeeze-blink, wide eyes, a puzzled little 'qua?'"""
    group = 'touch'
    def __init__(self):
        super().__init__('Poke', 1.1, seed=50)
        F = self.face; F.blinks = []; F.looks = []
        F['bL'].keys((0.0, 0.0), (0.05, 0.9), (0.18, 0.9), (0.28, 0.0)); F['bR'].k = list(F['bL'].k)
        F['pupil'].keys((0.25, 1.0), (0.32, 0.8), (0.9, 1.0))
        F['tilt'].keys((0.25, 0.0), (0.4, 0.35), (0.85, 0.35), (1.1, 0.0))
        F.pulse('jaw', 0.42, 0.05, 0.08, 0.12, 0.55)
    def act(self, t, c):
        fl = env(t, 0.0, 0.06, 0.14, 0.45)
        c.body(pitch=-10 * fl); c.head(pitch=-12 * fl, roll=10 * env(t, 0.35, 0.5, 0.8, 1.05))
        c.squash = 0.12 * fl - 0.05 * env(t, 0.38, 0.45, 0.5, 0.6); c.flap(20 * fl)


class HeadTilt(Clip):
    def __init__(self):
        super().__init__('HeadTilt', 1.9, seed=51)
        F = self.face; F.looks = []; F.blinks = [1.45]
        F['pupil'].keys((0.2, 1.0), (0.5, 1.15), (1.5, 1.15), (1.8, 1.0))
        F['tilt'].keys((0.2, 0.0), (0.5, 0.3), (1.4, 0.3), (1.7, 0.0))
        F['ly'].keys((0.2, 0.0), (0.5, 0.1), (1.4, 0.1), (1.7, 0.0))
        F.pulse('jaw', 0.9, 0.06, 0.06, 0.1, 0.25)
    def act(self, t, c):
        k = env(t, 0.15, 0.5, 1.35, 1.8)
        c.head(roll=24 * k + 3 * k * sinp(t, 0.9), pitch=-8 * k, yaw=6 * k); c.body(roll=5 * k)


class Shake(Clip):
    """Wet-duck shake from beak to tail, water drops flying."""
    def __init__(self):
        super().__init__('Shake', 1.6, seed=52)
        F = self.face; F.blinks = []; F.looks = []
        F['bL'].keys((0.1, 0.0), (0.2, 1.0), (1.05, 1.0), (1.2, 0.0)); F['bR'].k = list(F['bL'].k)
        F['happy'].keys((1.1, 0.0), (1.25, 0.8), (1.6, 0.4))
    def act(self, t, c):
        k = env(t, 0.1, 0.2, 0.8, 1.15)
        c.turn(H, 'Z', 16 * k * sinp(t, 0.13)); c.head(yaw=-26 * k * sinp(t, 0.13, 0.15), roll=8 * k * sinp(t, 0.13))
        c.body(roll=6 * k * sinp(t, 0.13, 0.1)); c.flap(k * (25 + 15 * sinp(t, 0.065))); c.squash = 0.05 * k * sinp(t, 0.065)
        c.squash += 0.08 * env(t, 1.15, 1.22, 1.26, 1.45)
    def props(self, t, M):
        if not (0.25 < t < 1.25): return {'Drops': None}
        k = (t - 0.25) / 1.0
        c = M[S1].translation
        return {'Drops': T((c.x, c.y, c.z - 0.12)) @ Rz(200 * k) @ Sc((0.9 + 1.3 * ss(k / 0.5), 0.9 + 1.3 * ss(k / 0.5), max(1e-4, 1.4 * (1 - ss((k - 0.5) / 0.5)))))}


class Spin(Clip):
    """A happy pirouette with a hop and flappy wings."""
    def __init__(self):
        super().__init__('Spin', 1.5, seed=53)
        F = self.face; F.blinks = []; F.looks = []
        F['happy'].keys((0.1, 0.0), (0.3, 1.0), (1.2, 1.0), (1.5, 0.3)); F.pulse('jaw', 1.05, 0.06, 0.1, 0.15, 0.7)
    def act(self, t, c):
        c.spin = 360 * ss((t - 0.2) / 0.8)
        hop = env(t, 0.25, 0.5, 0.6, 0.95)
        c.root.z += 0.08 * hop; c.squash = 0.15 * env(t, 0.1, 0.22, 0.24, 0.32) - 0.08 * hop + 0.12 * env(t, 0.95, 1.0, 1.05, 1.3)
        c.flap(hop * (35 + 25 * sinp(t, 0.18))); c.leg('L', 20 * hop); c.leg('R', 20 * hop)


# ===================================================================== charger
class Plugged(Clip):
    """Charger in: a jolt, happy flaps, two hops and a proud quack."""
    def __init__(self):
        super().__init__('Plugged', 2.6, seed=60)
        F = self.face; F['happy'].keys((0.2, 0.0), (0.4, 1.0), (2.2, 1.0), (2.6, 0.2)); F.pulse('jaw', 1.8, 0.05, 0.15, 0.1, 1.0)
    def act(self, t, c):
        k = env(t, 0.0, 0.05, 0.25, 0.45); c.squash = -0.08 * k; c.head(pitch=-15 * k)
        f = env(t, 0.3, 0.4, 1.5, 1.7); c.flap(55 * f + 30 * sinp(t, 0.18) * f)
        for a in (0.5, 1.0):
            h = math.sin(math.pi * max(0.0, min(1.0, (t - a) / 0.35))) if a < t < a + 0.35 else 0.0
            c.root.z += 0.1 * h
        q = env(t, 1.75, 1.8, 2.0, 2.2); c.head(pitch=-20 * q); c.body(pitch=-6 * q)


class ChargeRest(Clip):
    """Charging: sitting fluffed like a little loaf, eyes half shut, content."""
    loop = True; group = 'sleep'
    def __init__(self):
        super().__init__('ChargeRest', 6.0, seed=61)
        F = self.face; F['bL'].d = F['bR'].d = 0.4; F['happy'].d = 0.6; F.looks = []; F.blinks = [1.6, 4.4]
    def act(self, t, c):
        b = sinp(t, 3.0); sit_pose(c, 1.0, t, b); c.head(pitch=6 + 2 * b, roll=6 * sinp(t, 6.0), yaw=12 * env(t, 2.0, 2.4, 3.4, 3.8))


class Tired(Clip):
    """Low battery: sagging, wings drooping, head nodding off, a big yawn."""
    loop = True; group = 'sleep'
    def __init__(self):
        super().__init__('Tired', 6.0, seed=62)
        F = self.face; F['bL'].d = F['bR'].d = 0.6; F.looks = []; F.blinks = [1.0, 2.8, 5.0]; F.pulse('jaw', 3.6, 0.35, 0.5, 0.4, 1.1)
    def act(self, t, c):
        b = sinp(t, 3.0); sit_pose(c, 1.0, t, b); c.flap(-14); c.body(pitch=10)
        c.head(pitch=22 + 3 * b - 30 * env(t, 3.5, 3.8, 4.3, 4.7), roll=10 * sinp(t, 6.0))


class Unplugged(Clip):
    """Cable out: 'QUACK?!' feathers up, looks at the plug, a sulky huff."""
    def __init__(self):
        super().__init__('Unplugged', 2.2, seed=63)
        F = self.face; F['pupil'].keys((0.0, 1.0), (0.1, 1.3), (1.2, 1.3), (1.5, 1.0)); F.pulse('jaw', 0.1, 0.05, 0.15, 0.1, 1.0)
    def act(self, t, c):
        k = env(t, 0.0, 0.05, 0.25, 0.55); c.squash = 0.1 * k; c.flap(40 * k); c.root.z += 0.06 * env(t, 0.0, 0.06, 0.1, 0.3)
        l = env(t, 0.6, 0.9, 1.5, 1.8); c.head(yaw=40 * l, pitch=25 * l)


class FullCharge(Clip):
    """100%: joyful dance and a happy spin."""
    def __init__(self):
        super().__init__('FullCharge', 3.4, 'Joyful_Dance_with_Hand_Sway', seed=64)
        self.face['happy'].d = 1.0
    def act(self, t, c):
        c.spin = 360 * ss((t - 1.8) / 1.2) if t > 1.8 else 0.0


def library():
    return [Plugged(), ChargeRest(), Tired(), Unplugged(), FullCharge(), 
        Idle(), Curious(), Poke(), HeadTilt(), Shake(), Spin(), Quack(), TryToFly(), Notice(), Preen(), Peck(), Carry(),
        SitDown(), Sleep(), WakeUp(),
        Pet(), Tickle(), Dangle(), Startle(), Tumble(), Kick(), Scared(),
        JumpStart(), Air(), Land(), Walk(), Run(),
        Splash(), RubberDuck(), Chase(),
        Dance('DanceBubble', 'Bubble_Dance', 40), Dance('DanceCardio', 'Cardio_Dance', 41), Dance('DanceJoy', 'Joyful_Dance_with_Hand_Sway', 42),
        Dance('DancePop', 'Superlove_Pop_Dance', 43), Dance('DanceNight', 'All_Night_Dance', 44), Dance('DanceHipHop', 'Hip_Hop_Dance', 45),
        Dance('DanceHipHop2', 'Hip_Hop_Dance_2', 46),
    ]
