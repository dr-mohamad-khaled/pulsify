"""Nutmeg the 3D squirrel (Nutty's 3D look): hand-made twitchy acting on the Meshy mixamo rig + its run.
Tap him again and again and he gets KNOCKED DOWN: flat on his back, stars circling, then shakes it off."""
import math, sys
sys.path.insert(0, '/home/claude/duck2/scripts')
from mathutils import Vector, Matrix
from engine2 import rq, ss, env, Face, FPS

BLEND = '/home/claude/nut/nut.props.blend'
OUT = '/home/claude/nut/baked'
MOVER = 'mover'; HIPS = 'mixamorig:Hips'; HIPS_TARGET = (0.0, -0.05)
JAW = None
TAILB = ['Bone_016', 'Bone_015', 'Bone_014', 'Bone_013', 'Bone_012']
SPRINGS = [(TAILB[2:], 'tail', 40.0, 4.5), (['Bone_028'], 'earL', 150.0, 9.0), (['Bone_030'], 'earR', 150.0, 9.0)]
E = None
M_ = 'mixamorig:'
H, S, S1, S2, NK, HD = (M_ + n for n in ('Hips', 'Spine', 'Spine1', 'Spine2', 'Neck', 'Head'))
LA, RA, LFA, RFA, LH, RH = (M_ + n for n in ('LeftArm', 'RightArm', 'LeftForeArm', 'RightForeArm', 'LeftHand', 'RightHand'))
LU, LL, LF, RU, RL, RF = (M_ + n for n in ('LeftUpLeg', 'LeftLeg', 'LeftFoot', 'RightUpLeg', 'RightLeg', 'RightFoot'))
T = Matrix.Translation
def Sc(s): return Matrix.Diagonal((s, s, s, 1)) if not isinstance(s, tuple) else Matrix.Diagonal((*s, 1))
def Rz(d): return Matrix.Rotation(math.radians(d), 4, 'Z')
def Rx(d): return Matrix.Rotation(math.radians(d), 4, 'X')
def sinp(t, period, phase=0.0): return math.sin(2 * math.pi * (t / period + phase))
def tri(t, period): x = (t / period) % 1.0; return 1 - abs(2 * x - 1)
PS = 1.8


class Ctx:
    def __init__(self, M):
        self.M = M; self.root = Vector(); self.squash = 0.0; self.turns = []; self.jaw = 0.0; self.spin = 0.0; self.roll = 0.0
    def turn(self, bone, axis, deg):
        if abs(deg) > 1e-4: self.turns.append((bone, rq(axis, deg)))
    def arms(self, l, r=None):          # raise sideways (+) / drop (-)
        r = l if r is None else r; self.turn(LA, 'Y', -l); self.turn(RA, 'Y', r)
    def reach(self, l, r=None):         # forward (+)
        r = l if r is None else r; self.turn(LA, 'Z', l); self.turn(RA, 'Z', -r)
    def elbows(self, l, r=None):
        r = l if r is None else r; self.turn(LFA, 'Y', -l); self.turn(RFA, 'Y', r)
    def aim(self, up, fore, t1, t2, k):
        """Points the upper arm along t1 and the forearm along t2 (world directions), blended by k."""
        if k <= 1e-4: return
        from engine2 import Quaternion as _Q
        d1 = (self.M[up].to_3x3() @ Vector((0, 1, 0))).normalized(); d2 = (self.M[fore].to_3x3() @ Vector((0, 1, 0))).normalized()
        q1 = _Q().slerp(d1.rotation_difference(t1.normalized()), k)
        q2 = _Q().slerp(d2.rotation_difference(q1.inverted() @ t2.normalized()), k)
        self.turns.append((up, q1)); self.turns.append((fore, q2))
    def paws_up(self, k, l=None, r=None):
        """Paws brought up in front of the mouth (nibbling / washing)."""
        l = k if l is None else l; r = k if r is None else r
        for s, sx, kk in (('L', 1, l), ('R', -1, r)):
            up, fore = (LA, LFA) if s == 'L' else (RA, RFA)
            self.aim(up, fore, Vector((0.25 * sx, -0.55, -0.8)), Vector((-0.45 * sx, -0.55, 0.7)), min(1.0, kk))
    def head(self, pitch=0.0, yaw=0.0, roll=0.0):
        for b, k in ((NK, 0.35), (HD, 0.65)):
            self.turn(b, 'Z', yaw * k); self.turn(b, 'X', pitch * k); self.turn(b, 'Y', roll * k)
    def body(self, pitch=0.0, yaw=0.0, roll=0.0):
        for b in (S, S1, S2):
            self.turn(b, 'X', pitch / 3); self.turn(b, 'Z', yaw / 3); self.turn(b, 'Y', roll / 3)
    def leg(self, side, lift=0.0, knee=None):
        u, l = (LU, LL) if side == 'L' else (RU, RL)
        knee = lift * 1.3 if knee is None else knee
        self.turn(u, 'X', -lift); self.turn(l, 'X', knee)
    def tail(self, up=0.0, wag=0.0, curl=0.0):
        """up: lifts the whole tail; curl: S-curl tighter (+) / straighter (-); wag: side flick."""
        self.turn(TAILB[0], 'X', -up * 0.5); self.turn(TAILB[1], 'X', -up * 0.5)
        for i, b in enumerate(TAILB[1:], 1): self.turn(b, 'X', -curl * (0.4 + 0.15 * i)); self.turn(b, 'Z', wag * (0.2 + 0.15 * i))
    def ears(self, back=0.0):
        self.turn('Bone_028', 'X', back); self.turn('Bone_030', 'X', back)


class Clip:
    loop = False; lock = 'lock'; springs = True; group = 'life'
    def __init__(self, name, secs, src=None, loop=None):
        self.name = name; self.src = src
        if loop is not None: self.loop = loop
        self.secs = secs; self.n = int(round(secs * FPS))
        self.face = Face(secs, seed=sum(map(ord, name)), loop=self.loop, blinks=False, saccades=False)
    def base(self, E, t):
        if self.src:
            s, e = E.frames(self.src); return E.sample(self.src, s + (t * FPS) % (e - s))
        return E.rest_pose()
    def act(self, t, c): pass
    def props(self, t, M): return {}


def twitch(c, t, k=1.0):
    """Squirrel nerves: quick head flicks and tail jerks between freezes."""
    ph = (t / 0.9) % 1.0; idx = int(t / 0.9)
    yaw = [0, 30, -20, 10, -35, 25, 0, -15][idx % 8]; pyaw = [0, 30, -20, 10, -35, 25, 0, -15][(idx - 1) % 8]
    c.head(yaw=(pyaw + (yaw - pyaw) * ss(ph / 0.08)) * k, pitch=-6 * k)
    j = env(ph, 0.0, 0.03, 0.06, 0.2) if idx % 3 == 0 else 0.0
    c.tail(wag=25 * j * k, up=10 * j * k)


def breathe(c, t, k=1.0):
    b = sinp(t, 1.2); c.squash += 0.015 * b * k; c.body(pitch=-1.5 * b * k)


def crouch(c, k):
    c.root.z -= 0.08 * k; c.leg('L', 40 * k, 75 * k); c.leg('R', 40 * k, 75 * k); c.body(pitch=12 * k)


# ------------------------------------------------------------------ everyday
class Idle(Clip):
    loop = True; group = 'idle'
    def __init__(self): super().__init__('Idle', 7.2, loop=True)
    def act(self, t, c):
        breathe(c, t); twitch(c, t); c.paws_up(0.35); c.tail(curl=6 * sinp(t, 3.6))
        c.ears(-8 * env(t % 2.4, 0.0, 0.03, 0.06, 0.15))


class LookAround(Clip):
    def __init__(self): super().__init__('LookAround', 3.6)
    def act(self, t, c):
        breathe(c, t); c.paws_up(0.35)
        c.head(yaw=50 * env(t, 0.2, 0.3, 1.0, 1.1) - 50 * env(t, 1.2, 1.3, 2.0, 2.1), pitch=-12 * env(t, 2.3, 2.4, 3.0, 3.3))
        c.tail(wag=20 * env(t, 1.1, 1.15, 1.2, 1.35))


class Lookout(Clip):
    """Stands up as tall as possible, paw over the eyes, scanning the horizon."""
    def __init__(self): super().__init__('Lookout', 3.4)
    def act(self, t, c):
        k = env(t, 0.1, 0.4, 2.8, 3.3); c.squash -= 0.1 * k; c.root.z += 0.04 * k; c.body(pitch=-10 * k)
        c.paws_up(0.2 * k, l=0.2 * k, r=0.0); self_r = k
        c.turn(RA, 'X', -95 * self_r); c.turn(RFA, 'X', -60 * self_r); c.turn(RA, 'Z', -25 * self_r)
        c.head(yaw=35 * sinp(t, 2.2) * k, pitch=-15 * k); c.tail(up=15 * k)


class Nibble(Clip):
    """Holds a nut in both paws and nibbles it at top speed."""
    def __init__(self): super().__init__('Nibble', 4.0)
    def act(self, t, c):
        k = env(t, 0.1, 0.4, 3.4, 3.9); n = sinp(t, 0.09) * env(t, 0.5, 0.6, 3.3, 3.4)
        c.paws_up(k); c.head(pitch=12 * k + 4 * n); c.body(pitch=6 * k); c.tail(curl=8 * k)
        c.turn(LH, 'X', 6 * n); c.turn(RH, 'X', 6 * n)
    def props(self, t, M):
        if t > 3.5: return {'Nut': None, 'Crumb0': None, 'Crumb1': None}
        a, b = M[LH].translation, M[RH].translation; p = (a + b) / 2 + Vector((0, -0.04, 0.03))
        out = {'Nut': T(p) @ Rx(90) @ Sc(PS * (1 - 0.4 * ss((t - 0.5) / 3.0)))}
        for i in range(2):
            ph = ((t - 0.6) / 0.7 + i * 0.5) % 1.0
            out[f'Crumb{i}'] = T(p + Vector((0.06 * (i - 0.5), -0.05, -0.3 * ph))) @ Sc(1.4) if t > 0.6 else None
        return out


class Stash(Clip):
    """Digs a little hole, drops the nut in, pats it flat, looks around guiltily."""
    def __init__(self): super().__init__('Stash', 5.0)
    def act(self, t, c):
        d = env(t, 0.3, 0.5, 2.4, 2.6); a = sinp(t, 0.2) * env(t, 0.6, 0.7, 2.2, 2.4)
        crouch(c, d); c.body(pitch=25 * d); c.reach(40 * d + 20 * a, 40 * d - 20 * a); c.head(pitch=25 * d)
        p = env(t, 2.8, 2.9, 3.4, 3.5) * abs(sinp(t, 0.25)); crouch(c, 0.6 * p); c.reach(40 * p)
        g = env(t, 3.7, 3.9, 4.6, 5.0); c.head(yaw=40 * sinp(t, 0.8) * g, pitch=-8 * g); c.paws_up(0.4 * g); c.tail(wag=15 * g * sinp(t, 0.4))
    def props(self, t, M):
        if t < 1.5: return {'Nut': T((0.05, -0.32, 0.02)) @ Sc(PS), 'Drops': None}
        if t < 2.6: return {'Nut': T((0.05, -0.32, 0.02 - 0.05 * ss((t - 1.5) / 1.0))) @ Sc(PS), 'Drops': T((0.0, -0.32, 0.0)) @ Rz(80 * t) @ Sc((1.0, 1.0, 0.6))}
        return {'Nut': None, 'Drops': None}


class TailFlick(Clip):
    def __init__(self): super().__init__('TailFlick', 2.0)
    def act(self, t, c):
        for a in (0.2, 0.6, 1.0, 1.15):
            k = env(t, a, a + 0.04, a + 0.08, a + 0.25); c.tail(up=25 * k, wag=30 * k, curl=-10 * k); c.squash += 0.03 * k
        c.paws_up(0.35); twitch(c, t)


class Wash(Clip):
    """Licks paws and rubs his face, ears and all."""
    def __init__(self): super().__init__('Wash', 4.0)
    def act(self, t, c):
        k = env(t, 0.2, 0.5, 3.4, 3.9); r = sinp(t, 0.35) * env(t, 0.7, 0.9, 3.2, 3.4)
        c.paws_up(k + 0.15 * r, l=k + 0.15 * r, r=k - 0.15 * r); c.head(pitch=20 * k + 8 * r, roll=12 * r); c.ears(15 * k * max(0.0, r))


class Scratch(Clip):
    """Sits and scratches behind the ear with a hind foot, super fast."""
    def __init__(self): super().__init__('Scratch', 3.0)
    def act(self, t, c):
        k = env(t, 0.2, 0.5, 2.4, 2.8); s = sinp(t, 0.07) * env(t, 0.6, 0.7, 2.2, 2.4)
        c.root.z -= 0.05 * k; c.body(roll=12 * k); c.leg('R', 95 * k + 8 * s, 50 * k); c.turn(RU, 'Y', -40 * k)
        c.head(roll=25 * k, pitch=10 * k); c.ears(-10 * k); c.tail(wag=-15 * k)


class Sneeze(Clip):
    def __init__(self): super().__init__('Sneeze', 1.6)
    def act(self, t, c):
        a = env(t, 0.2, 0.6, 0.7, 0.75); s = env(t, 0.75, 0.8, 0.85, 1.2)
        c.head(pitch=-20 * a + 30 * s); c.body(pitch=-6 * a + 10 * s); c.squash += 0.08 * s; c.tail(up=30 * s); c.paws_up(0.35)


class Zoomies(Clip):
    """Runs a frantic little circle (the Meshy run), tail streaming."""
    def __init__(self): super().__init__('Zoomies', 2.8, 'Running')
    def act(self, t, c): c.spin = 720 * ss(t / 2.6); c.tail(up=-10)


class Catch(Clip):
    """A nut drops from above; he catches it with a jump and hugs it."""
    def __init__(self): super().__init__('Catch', 3.0)
    def nut(self, t):
        if t < 1.0: return Vector((0.0, -0.25, 1.4 - 0.9 * t))
        return None
    def act(self, t, c):
        w = env(t, 0.0, 0.2, 0.7, 0.8); c.head(pitch=-35 * w); c.arms(60 * w); c.elbows(30 * w)
        j = env(t, 0.75, 0.85, 0.95, 1.15); c.root.z += 0.12 * j; c.arms(80 * j)
        h = env(t, 1.15, 1.3, 2.6, 3.0); c.paws_up(h); c.squash += 0.06 * h; c.tail(curl=10 * h, wag=10 * sinp(t, 0.5) * h)
    def props(self, t, M):
        p = self.nut(t)
        if p is not None: return {'Nut': T(p) @ Rx(300 * t) @ Sc(PS)}
        if t < 2.8: a, b = M[LH].translation, M[RH].translation; return {'Nut': T((a + b) / 2) @ Sc(PS)}
        return {'Nut': None}


class Drum(Clip):
    """Foot-drum solo: stomps a hind foot like a rabbit, paws drumming on his belly."""
    def __init__(self): super().__init__('Drum', 3.4)
    def act(self, t, c):
        k = env(t, 0.1, 0.3, 3.0, 3.4); d = max(0.0, sinp(t, 0.18)) * k
        c.leg('R', 30 * d, 40 * d); c.paws_up(0.3 * k, l=0.3 * k + 0.2 * max(0.0, sinp(t, 0.24)), r=0.3 * k + 0.2 * max(0.0, -sinp(t, 0.24)))
        c.head(pitch=10 * tri(t, 0.36) * k); c.tail(wag=10 * sinp(t, 0.36) * k)


class HeadTilt(Clip):
    def __init__(self): super().__init__('HeadTilt', 2.2)
    def act(self, t, c):
        k = env(t, 0.1, 0.3, 0.8, 1.0); k2 = env(t, 0.9, 1.1, 1.6, 1.9)
        c.head(roll=32 * k - 30 * k2, pitch=-6 * (k + k2)); c.paws_up(0.4); c.ears(-6 * (k + k2))


class Spin(Clip):
    def __init__(self): super().__init__('Spin', 1.6)
    def act(self, t, c):
        c.spin = 360 * ss(t / 1.3); c.root.z += 0.08 * math.sin(math.pi * min(1.0, t / 1.3)); c.arms(40 * math.sin(math.pi * min(1.0, t / 1.3)))
        c.tail(wag=-25 * math.sin(math.pi * min(1.0, t / 1.3)))


class ChaseButterfly(Clip):
    def __init__(self): super().__init__('ChaseButterfly', 4.4)
    def bfly(self, t):
        a = t * 2.4
        if t < 3.2: return Vector((0.4 * math.cos(a), -0.4 + 0.15 * math.sin(a), 0.9 + 0.12 * math.sin(a * 2)))
        k = (t - 3.2) / 1.2; return Vector((0.4 * math.cos(a) + 0.5 * k, -0.4, 0.9 + 0.6 * k))
    def act(self, t, c):
        p = self.bfly(t); y = max(-60, min(60, math.degrees(math.atan2(p.x, 0.5))))
        c.head(yaw=y, pitch=-25, roll=0.3 * y); c.paws_up(0.4)
        j = env(t, 2.8, 2.95, 3.1, 3.4); c.root.z += 0.2 * j; c.arms(90 * j); c.elbows(-20 * j); c.tail(up=-20 * j)
    def props(self, t, M):
        return {'Butterfly': T(self.bfly(t)) @ Rz(t * 140) @ Sc(PS), '_wings': math.radians(70 * abs(math.sin(t * 24)))}


# ------------------------------------------------------------------ moving
class Walk(Clip):
    """Bouncy hops (squirrels bound)."""
    loop = True; group = 'walk'
    def __init__(self): super().__init__('Walk', 0.45 * 4, loop=True)
    def act(self, t, c):
        ph = (t / 0.45) % 1.0; u = math.sin(math.pi * ph)
        c.root.z += 0.08 * u; crouch(c, 0.5 * (1 - u)); c.leg('L', -20 * u, 10 * u); c.leg('R', -20 * u, 10 * u)
        c.body(pitch=10); c.paws_up(0.3); c.tail(up=-8 * u, curl=-8 * u)


class Run(Clip):
    loop = True; group = 'walk'
    def __init__(self): super().__init__('Run', 19 / FPS * 4, 'Running', loop=True)
    def act(self, t, c): c.tail(up=-10)


class JumpStart(Clip):
    def __init__(self): super().__init__('JumpStart', 0.4)
    def act(self, t, c): k = ss(t / 0.34); crouch(c, k); c.arms(-15 * k)


class JumpAir(Clip):
    loop = True; group = 'jump'
    def __init__(self): super().__init__('JumpAir', 0.6, loop=True)
    def act(self, t, c):
        c.arms(60 + 15 * sinp(t, 0.3)); c.leg('L', -20, 10); c.leg('R', -20, 10); c.tail(up=-20, curl=-15); c.squash -= 0.06


class JumpLand(Clip):
    def __init__(self): super().__init__('JumpLand', 0.6)
    def act(self, t, c):
        k = env(t, 0.0, 0.06, 0.12, 0.5); crouch(c, k); c.squash += 0.1 * k * math.exp(-t * 5)


# ------------------------------------------------------------------ touch
class Poke(Clip):
    """Freezes and puffs up."""
    group = 'touch'
    def __init__(self): super().__init__('Poke', 1.3)
    def act(self, t, c):
        k = env(t, 0.0, 0.05, 0.6, 1.1); c.squash += 0.1 * k; c.tail(up=30 * k, curl=-20 * k); c.root.z += 0.06 * env(t, 0.0, 0.06, 0.1, 0.3)
        c.arms(25 * k); c.ears(-15 * k); c.head(pitch=-10 * k)


class Knockdown(Clip):
    """Too many taps: BONK. Falls flat on his back, stars spin (app), legs twitch, shakes it off and gets up wobbly."""
    group = 'touch'
    def __init__(self): super().__init__('Knockdown', 4.6)
    def act(self, t, c):
        f = ss(t / 0.45); u = 1 - ss((t - 3.2) / 0.7)
        k = f * u
        c.roll = 0.0; c.body(pitch=-12 * k)
        c.turn(H, 'X', -82 * k); c.root.z += 0.05 * k; c.root.y += 0.35 * k
        c.arms(70 * k + 8 * sinp(t, 0.3) * env(t, 1.0, 1.2, 2.6, 2.8)); c.elbows(30 * k)
        tw = env(t, 1.6, 1.7, 2.3, 2.5); c.leg('L', 40 * k + 15 * tw * sinp(t, 0.12), 30 * k); c.leg('R', 30 * k - 15 * tw * sinp(t, 0.12), 20 * k)
        c.head(roll=20 * sinp(t, 0.8) * env(t, 0.6, 0.8, 3.0, 3.2), pitch=-10 * k); c.tail(curl=-25 * k, up=-25 * k)
        w = env(t, 3.6, 3.8, 4.2, 4.6); c.head(yaw=30 * w * sinp(t, 0.12)); c.body(roll=8 * w * sinp(t, 0.24))
        c.squash += 0.1 * env(t, 0.42, 0.45, 0.5, 0.65)


class Pet(Clip):
    """Melts: eyes squeezed, leans into the finger, tail wrapping round."""
    loop = True; group = 'touch'
    def __init__(self): super().__init__('Pet', 3.0, loop=True)
    def act(self, t, c):
        c.head(roll=18 * sinp(t, 3.0), pitch=15); c.paws_up(0.5); c.body(roll=6 * sinp(t, 3.0)); c.squash += 0.04; c.tail(curl=15)
        c.ears(12)
    def props(self, t, M):
        ph = (t / 1.5) % 1.0; h = M[HD].translation
        return {'Heart': T((h.x + 0.15, h.y - 0.05, h.z + 0.32 + 0.25 * ph)) @ Sc(PS * ss(ph / 0.2) * (1 - ss((ph - 0.7) / 0.3)) + 1e-4)}


class Tickle(Clip):
    group = 'touch'
    def __init__(self): super().__init__('Tickle', 2.2)
    def act(self, t, c):
        k = env(t, 0.0, 0.1, 1.8, 2.2); w = sinp(t, 0.14)
        c.body(roll=12 * w * k); c.head(roll=-18 * w * k, pitch=-10 * k); c.arms(30 * abs(w) * k); c.tail(wag=30 * w * k); c.squash += 0.05 * abs(w) * k


class Scared(Clip):
    group = 'touch'
    def __init__(self): super().__init__('Scared', 2.0)
    def act(self, t, c):
        k = env(t, 0.0, 0.1, 1.5, 2.0); c.squash -= 0.1 * k; c.paws_up(0.7 * k); c.tail(up=40 * k, curl=20 * k); c.ears(-20 * k)
        c.head(yaw=30 * sinp(t, 0.3) * k); c.root.y += 0.08 * k


class Dangle(Clip):
    loop = True; group = 'touch'
    def __init__(self): super().__init__('Dangle', 1.2, loop=True)
    def act(self, t, c):
        # held up under the arms: arms raised, legs and tail hanging straight down, little kicks
        c.arms(95 + 5 * sinp(t, 1.2)); c.elbows(-10); c.head(pitch=8, roll=6 * sinp(t, 2.4))
        k = env(t % 1.2, 0.0, 0.1, 0.2, 0.4)
        c.leg('L', -8 + 18 * k * (1 if int(t / 1.2) % 2 == 0 else 0), 6); c.leg('R', -8 + 18 * k * (1 if int(t / 1.2) % 2 else 0), 6)
        c.tail(wag=10 * sinp(t, 1.2), up=-45, curl=-20); c.root.z -= 0.04


class Tumble(Clip):
    def __init__(self): super().__init__('Tumble', 1.8)
    def act(self, t, c):
        c.roll = 360 * ss(t / 0.8) if t < 0.8 else 0.0
        d = env(t, 0.9, 1.0, 1.4, 1.8); c.head(roll=18 * d * sinp(t, 0.4)); crouch(c, 0.4 * d)


# ------------------------------------------------------------------ sleep
def curl(c, k, b=0.0):
    """Curled up asleep, wrapped in his own tail like a blanket."""
    crouch(c, k); c.body(pitch=30 * k + 2 * b); c.head(pitch=40 * k, roll=10 * k); c.paws_up(0.7 * k); c.root.z -= 0.05 * k
    c.tail(up=-25 * k, curl=20 * k); c.squash += 0.04 * k + 0.02 * b


class FallAsleep(Clip):
    def __init__(self): super().__init__('FallAsleep', 2.6)
    def act(self, t, c):
        y = env(t, 0.3, 0.6, 0.9, 1.2); c.head(pitch=-20 * y); c.squash -= 0.04 * y; curl(c, ss((t - 1.0) / 1.4))


class Sleep(Clip):
    loop = True; group = 'idle'
    def __init__(self): super().__init__('Sleep', 4.8, loop=True)
    def act(self, t, c): curl(c, 1.0, sinp(t, 2.4)); c.ears(10 * env(t, 2.6, 2.65, 2.7, 2.8))
    def props(self, t, M):
        out = {}; top = M[HD].translation
        for i in range(3):
            ph = ((t / 4.8) * 2 + i / 3.0) % 1.0
            s = PS * ss(ph / 0.2) * (1 - ss((ph - 0.75) / 0.25)) * (0.8 + 0.25 * i)
            out[f'Z{i}'] = T(top + Vector((0.12 + 0.15 * ph, -0.05, 0.25 + 0.3 * ph))) @ Sc(max(1e-4, s)) if s > 0.01 else None
        return out


class WakeUp(Clip):
    def __init__(self): super().__init__('WakeUp', 2.4)
    def act(self, t, c):
        curl(c, 1 - ss((t - 0.2) / 1.0)); s = env(t, 1.3, 1.6, 1.9, 2.4); c.arms(70 * s); c.head(pitch=-25 * s); c.squash -= 0.05 * s
        c.tail(up=20 * s)


# ------------------------------------------------------------------ music
class DanceTail(Clip):
    """Tail-wave dance: the tail swings like a flag, hips sway."""
    def __init__(self): super().__init__('DanceTail', 4.0)
    def act(self, t, c):
        c.tail(wag=35 * sinp(t, 1.0), up=10 * tri(t, 0.5)); c.body(roll=10 * sinp(t, 1.0, 0.25)); c.head(roll=-10 * sinp(t, 1.0), pitch=8 * tri(t, 0.5))
        c.arms(30 + 30 * max(0.0, sinp(t, 1.0)), 30 + 30 * max(0.0, -sinp(t, 1.0))); c.root.z += 0.03 * tri(t, 0.5)


class DanceDrum(Clip):
    def __init__(self): super().__init__('DanceDrum', 4.0)
    def act(self, t, c):
        Drum.act(self, t, c); c.root.z += 0.04 * tri(t, 0.5)


class DanceHop(Clip):
    def __init__(self): super().__init__('DanceHop', 4.0)
    def act(self, t, c):
        ph = (t / 0.5) % 1.0; u = math.sin(math.pi * ph); c.root.z += 0.1 * u; crouch(c, 0.4 * (1 - u))
        c.arms(50 * u); c.elbows(30 * u); c.tail(up=-15 * u, wag=20 * sinp(t, 1.0)); c.spin = 35 * sinp(t, 2.0)


# ------------------------------------------------------------------ charger
class Plugged(Clip):
    """Charger in: a jolt (fur on end), then happy bouncing."""
    def __init__(self): super().__init__('Plugged', 2.6)
    def act(self, t, c):
        k = env(t, 0.0, 0.05, 0.3, 0.5); c.squash += 0.12 * k; c.tail(up=40 * k, curl=-25 * k); c.arms(60 * k); c.ears(-20 * k)
        for a in (0.6, 1.05, 1.5):
            h = math.sin(math.pi * max(0.0, min(1.0, (t - a) / 0.35))) if a < t < a + 0.35 else 0.0
            c.root.z += 0.12 * h; c.arms(50 * h)
        c.paws_up(0.4 * env(t, 1.9, 2.1, 2.4, 2.6))
    def props(self, t, M):
        if t < 0.9: return {'Heart': None}
        ph = (t - 0.9) / 1.7; h = M[HD].translation
        return {'Heart': T((h.x - 0.15, h.y - 0.05, h.z + 0.3 + 0.3 * ph)) @ Sc(PS * 1.2 * ss(ph / 0.2) * (1 - ss((ph - 0.7) / 0.3)) + 1e-4)}


class ChargeRest(Clip):
    """Charging: sitting cosy with a nut, nibbling now and then, tail over his head like an umbrella."""
    loop = True; group = 'idle'
    def __init__(self): super().__init__('ChargeRest', 6.0, loop=True)
    def act(self, t, c):
        b = sinp(t, 2.4); crouch(c, 0.6); c.paws_up(0.9); c.tail(curl=18, up=10); c.squash += 0.03 + 0.012 * b
        n = env(t, 2.0, 2.2, 3.4, 3.6) * sinp(t, 0.1); c.head(pitch=14 + 4 * n, roll=6 * sinp(t, 6.0))
    def props(self, t, M):
        a, b = M[LH].translation, M[RH].translation; return {'Nut': T((a + b) / 2 + Vector((0, -0.04, 0.03))) @ Rx(90) @ Sc(PS)}


class Tired(Clip):
    """Low battery: slumped, tail dragging, slow blinks of the head, a yawn."""
    loop = True; group = 'idle'
    def __init__(self): super().__init__('Tired', 6.0, loop=True)
    def act(self, t, c):
        s = sinp(t, 3.0); crouch(c, 0.5); c.body(pitch=18, roll=5 * s); c.head(pitch=25 + 5 * s); c.arms(-10); c.tail(up=-35, curl=-15)
        c.ears(18); y = env(t, 3.4, 3.8, 4.2, 4.7); c.head(pitch=-40 * y); c.squash -= 0.04 * y


class Unplugged(Clip):
    def __init__(self): super().__init__('Unplugged', 2.2)
    def act(self, t, c):
        k = env(t, 0.0, 0.05, 0.2, 0.5); c.squash += 0.1 * k; c.tail(up=30 * k)
        l = env(t, 0.5, 0.7, 1.4, 1.7); c.head(yaw=45 * l, pitch=25 * l); c.paws_up(0.6 * l)
        f = env(t, 1.7, 1.85, 2.0, 2.2); c.head(roll=15 * f * sinp(t, 0.1))


class FullCharge(Clip):
    """100%: victory zoomies, then a proud pose with the nut held high."""
    def __init__(self): super().__init__('FullCharge', 4.4, 'Running')
    def base(self, E, t):
        if t < 2.4: return Clip.base(self, E, t)
        return E.rest_pose()
    def act(self, t, c):
        if t < 2.4: c.spin = 720 * ss(t / 2.4); c.tail(up=-10)
        else:
            k = env(t, 2.4, 2.7, 4.0, 4.4); c.turn(RA, 'X', -150 * k); c.turn(RFA, 'X', -20 * k); c.squash -= 0.06 * k; c.tail(up=25 * k, curl=10 * k)
            c.head(pitch=-20 * k)
    def props(self, t, M):
        if t < 2.5: return {'Nut': None}
        return {'Nut': T(M[RH].translation + Vector((0, 0, 0.05))) @ Sc(PS)}


def library():
    return [Idle(), LookAround(), Lookout(), Nibble(), Stash(), TailFlick(), Wash(), Scratch(), Sneeze(), Zoomies(), Catch(), Drum(),
            HeadTilt(), Spin(), ChaseButterfly(), Walk(), Run(), JumpStart(), JumpAir(), JumpLand(),
            Poke(), Knockdown(), Pet(), Tickle(), Scared(), Dangle(), Tumble(), FallAsleep(), Sleep(), WakeUp(),
            DanceTail(), DanceDrum(), DanceHop(), Plugged(), ChargeRest(), Tired(), Unplugged(), FullCharge()]
