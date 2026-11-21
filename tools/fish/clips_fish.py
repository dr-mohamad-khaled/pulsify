"""Crimson the betta: hand-made swimming, flaring, bubble-nest and charger acting on the Meshy auto-rig.
The rest pose is curled, so every clip starts from a straightened base pose (computed once)."""
import math, sys
sys.path.insert(0, '/home/claude/duck2/scripts')
from mathutils import Vector, Matrix, Quaternion
from engine2 import rq, ss, env, Face, FPS

BLEND = '/home/claude/fish/betta.props.blend'
OUT = '/home/claude/fish/baked'
MOVER = 'mover'; HIPS = 'Bone_000'; HIPS_TARGET = (0.0, -0.36)
JAW = None
SPRINGS = [(['Bone_027', 'Bone_026', 'Bone_025', 'Bone_024'], 'fanM', 60.0, 6.0),
           (['Bone_031', 'Bone_030', 'Bone_029', 'Bone_028'], 'fanU', 55.0, 6.0),
           (['Bone_035', 'Bone_034', 'Bone_033', 'Bone_032'], 'fanL', 55.0, 6.0),
           (['Bone_020', 'Bone_019', 'Bone_018'], 'dorsal', 70.0, 7.0), (['Bone_023', 'Bone_022', 'Bone_021'], 'anal', 70.0, 7.0)]
E = None
LIFT = 0.24
HEAD, JAWB = 'Bone_012', 'Bone_011'
SPINE = ['Bone_010', 'Bone_009', 'Bone_017', 'Bone_016']           # tail spine, front to back
FRONT = ['Bone_001', 'Bone_002', 'Bone_012', 'Bone_011']             # body to snout
FANS = {'U': ['Bone_031', 'Bone_030', 'Bone_029', 'Bone_028'], 'M': ['Bone_027', 'Bone_026', 'Bone_025', 'Bone_024'],
        'L': ['Bone_035', 'Bone_034', 'Bone_033', 'Bone_032']}
DORSAL, ANAL, VENTRAL = ['Bone_020', 'Bone_019', 'Bone_018'], ['Bone_023', 'Bone_022', 'Bone_021'], ['Bone_015', 'Bone_014', 'Bone_013']
PECT = {'L': ['Bone_008', 'Bone_007', 'Bone_006'], 'R': ['Bone_005', 'Bone_004', 'Bone_003']}
T = Matrix.Translation
def Sc(s): return Matrix.Diagonal((s, s, s, 1))
def sinp(t, period, phase=0.0): return math.sin(2 * math.pi * (t / period + phase))
_BASE = None


def base_pose(E):
    """Straighten the curled rest pose: spine bones re-aimed along the body axis (head -y, tail +y)."""
    global _BASE
    if _BASE is not None: return E.copy(_BASE)
    P = E.rest_pose()
    for chain, d in ((SPINE, Vector((0, 1, 0))), (['Bone_002', 'Bone_012', 'Bone_011'], Vector((0, -1, 0)))):
        for b in chain:
            M = E.fk(P); m = M[b]; cur = (m.to_3x3() @ Vector((0, 1, 0))).normalized()
            tgt = Vector((0.0, d.y, cur.z * 0.6)).normalized()
            E.turn(P, M, [(b, cur.rotation_difference(tgt))])
    _BASE = E.copy(P)
    return E.copy(P)


class Ctx:
    def __init__(self, M):
        self.M = M; self.root = Vector(); self.squash = 0.0; self.turns = []; self.jaw = 0.0; self.spin = 0.0; self.roll = 0.0
    def turn(self, bone, axis, deg):
        if abs(deg) > 1e-4: self.turns.append((bone, rq(axis, deg)))
    def wave(self, t, period=0.8, amp=14.0, head=0.25, phase=0.0):
        """Swimming undulation: a travelling wave down the tail, the head counter-swinging a little."""
        for i, b in enumerate(SPINE):
            self.turn(b, 'Z', amp * (0.5 + 0.35 * i) * math.sin(2 * math.pi * (t / period + phase) - 0.9 * (i + 1)))
        self.turn('Bone_002', 'Z', -amp * head * math.sin(2 * math.pi * (t / period + phase)))
        for k, ch in FANS.items():
            for i, b in enumerate(ch[1:], 1):
                self.turn(b, 'Z', amp * 0.4 * i * math.sin(2 * math.pi * (t / period + phase) - 0.9 * (len(SPINE) + i)))
    def bend(self, deg):
        """C-bend of the whole body (turning); + bends the tail to the fish's left (+x)."""
        for i, b in enumerate(SPINE): self.turn(b, 'Z', deg * (0.4 + 0.2 * i))
        self.turn('Bone_002', 'Z', -deg * 0.4)
    def body(self, pitch=0.0, yaw=0.0, roll=0.0):
        self.turn('Bone_000', 'X', pitch); self.turn('Bone_000', 'Z', yaw); self.turn('Bone_000', 'Y', roll)
    def arch(self, deg):
        """Up(+)/down arch along the spine (a flare arches the back)."""
        for i, b in enumerate(SPINE): self.turn(b, 'X', -deg * (0.3 + 0.15 * i))
        self.turn('Bone_002', 'X', deg * 0.4)
    def fins(self, spread=0.0, flutter=0.0, t=0.0, rate=0.25):
        """spread 1 = full flare (dorsal up, anal and ventrals down, tail lobes fanned); flutter = pectoral beat."""
        for i, b in enumerate(DORSAL): self.turn(b, 'X', -18 * spread * (1 if i == 0 else 0.5))
        for i, b in enumerate(ANAL): self.turn(b, 'X', 16 * spread * (1 if i == 0 else 0.5))
        for i, b in enumerate(VENTRAL): self.turn(b, 'X', 14 * spread)
        self.turn(FANS['U'][0], 'X', -24 * spread); self.turn(FANS['L'][0], 'X', 24 * spread)
        self.turn(FANS['U'][1], 'X', -10 * spread); self.turn(FANS['L'][1], 'X', 10 * spread)
        for s, sx in (('L', 1), ('R', -1)):
            ph = math.sin(2 * math.pi * t / rate + (0 if s == 'L' else 1.6))
            self.turn(PECT[s][0], 'Z', sx * (flutter * 30 * ph + 20 * spread)); self.turn(PECT[s][1], 'Z', sx * flutter * 20 * ph)
    def mouth(self, v):
        self.turn(JAWB, 'X', 22 * v)


class Clip:
    loop = False; lock = 'lock'; springs = True; group = 'life'
    def __init__(self, name, secs, loop=None):
        self.name = name
        if loop is not None: self.loop = loop
        self.secs = secs; self.n = int(round(secs * FPS))
        self.face = Face(secs, seed=sum(map(ord, name)), loop=self.loop, blinks=False, saccades=False)
    def base(self, E, t): return base_pose(E)
    def act(self, t, c): c.root.z += LIFT; self.pose(t, c)
    def pose(self, t, c): pass
    def props(self, t, M): return {}


def bob(c, t, k=1.0):
    c.root.z += 0.03 * sinp(t, 2.4) * k; c.body(pitch=3 * sinp(t, 2.4, 0.25) * k)


# ------------------------------------------------------------------------- swimming
class Idle(Clip):
    """Hovering: slow tail sway, pectorals fanning, gentle bob."""
    loop = True; group = 'idle'
    def __init__(self): super().__init__('Idle', 4.8, loop=True)
    def pose(self, t, c):
        c.wave(t, 2.4, 6); c.fins(0.2, 0.7, t, 0.3); bob(c, t); c.mouth(0.15 + 0.15 * max(0, sinp(t, 1.2)))


class Swim(Clip):
    loop = True; group = 'walk'
    def __init__(self): super().__init__('Swim', 0.8 * 3, loop=True)
    def pose(self, t, c): c.wave(t, 0.8, 16); c.fins(0.05, 0.6, t, 0.2); c.body(pitch=2 * sinp(t, 0.8))


class Dash(Clip):
    loop = True; group = 'walk'
    def __init__(self): super().__init__('Dash', 0.4 * 4, loop=True)
    def pose(self, t, c): c.wave(t, 0.4, 24); c.fins(-0.3, 0.2, t, 0.15)


class Turn(Clip):
    """A quick C-turn (the app flips direction at the middle)."""
    def __init__(self): super().__init__('Turn', 0.9)
    def pose(self, t, c):
        k = env(t, 0.0, 0.3, 0.45, 0.9); c.bend(55 * k); c.fins(0.3 * k, 0.8, t, 0.2); c.body(roll=-15 * k)


class Glide(Clip):
    loop = True; group = 'walk'
    def __init__(self): super().__init__('Glide', 3.6, loop=True)
    def pose(self, t, c):
        c.wave(t, 1.8, 8); c.fins(0.5, 0.3, t, 0.4); c.body(roll=10 * sinp(t, 3.6)); c.root.z += 0.05 * sinp(t, 1.8)


class Flare(Clip):
    """The betta display: everything spread to the max, gills out, body arched, a sideways shimmy."""
    def __init__(self): super().__init__('Flare', 3.2)
    def pose(self, t, c):
        k = env(t, 0.0, 0.35, 2.4, 3.1); s = sinp(t, 0.35) * env(t, 0.5, 0.7, 2.2, 2.5)
        c.fins(1.0 * k, 0.4, t, 0.2); c.arch(10 * k); c.mouth(0.6 * k); c.bend(14 * s); c.body(roll=12 * s, pitch=-6 * k)
        c.squash -= 0.04 * k


class LookAround(Clip):
    def __init__(self): super().__init__('LookAround', 3.6)
    def pose(self, t, c):
        y = 30 * env(t, 0.3, 0.6, 1.2, 1.5) - 30 * env(t, 1.8, 2.1, 2.7, 3.0)
        c.body(yaw=y, pitch=-8 * env(t, 2.9, 3.1, 3.3, 3.6)); c.bend(-y * 0.5); c.wave(t, 2.0, 5); c.fins(0.25, 0.8, t, 0.25)


class Eat(Clip):
    """Snaps three food pellets: lunge, gulp, chew."""
    def __init__(self): super().__init__('Eat', 4.2)
    def pose(self, t, c):
        c.wave(t, 1.6, 6); c.fins(0.3, 0.8, t, 0.2)
        for i in range(3):
            a = 0.4 + i * 1.2
            l = env(t, a, a + 0.15, a + 0.25, a + 0.5); m = env(t, a + 0.05, a + 0.15, a + 0.2, a + 0.35)
            c.root.y -= 0.18 * l; c.body(pitch=(i - 1) * 12 * l); c.mouth(1.0 * m)
            ch = env(t, a + 0.5, a + 0.6, a + 0.9, a + 1.0); c.mouth(0.3 * ch * abs(sinp(t, 0.15)))


class Bubble(Clip):
    """Rises a little, blows a string of bubbles (the app releases them from the snout)."""
    def __init__(self): super().__init__('Bubble', 3.0)
    def pose(self, t, c):
        k = env(t, 0.2, 0.6, 2.4, 3.0); c.root.z += 0.15 * k; c.body(pitch=-20 * k)
        c.mouth(0.5 * k * abs(sinp(t, 0.4))); c.wave(t, 1.8, 6); c.fins(0.3, 1.0, t, 0.2)


class Nest(Clip):
    """Bubble-nest building at the surface: nose up, many little puffs, a proud flare at the end."""
    def __init__(self): super().__init__('Nest', 5.0)
    def pose(self, t, c):
        k = env(t, 0.2, 0.8, 3.6, 4.2); c.root.z += 0.3 * k; c.body(pitch=-30 * k, roll=8 * sinp(t, 1.0) * k)
        c.mouth(0.6 * k * abs(sinp(t, 0.3))); c.wave(t, 1.6, 6); c.fins(0.2, 1.0, t, 0.18)
        f = env(t, 3.8, 4.1, 4.6, 5.0); c.fins(f, 0.0, t); c.arch(8 * f)


class Spin(Clip):
    """A happy barrel roll."""
    def __init__(self): super().__init__('Spin', 1.6)
    def pose(self, t, c):
        c.roll = 360 * ss(t / 1.3); c.wave(t, 0.5, 18); c.fins(0.5, 0.5, t, 0.15)


class Loop(Clip):
    """A loop-the-loop."""
    def __init__(self): super().__init__('Loop', 2.0)
    def pose(self, t, c):
        k = ss(t / 1.8); c.body(pitch=-360 * k); c.root.z += 0.25 * math.sin(math.pi * k); c.wave(t, 0.45, 18); c.fins(0.4, 0.3, t, 0.15)


class Yawn(Clip):
    def __init__(self): super().__init__('Yawn', 2.4)
    def pose(self, t, c):
        k = env(t, 0.3, 0.8, 1.3, 1.9); c.mouth(1.2 * k); c.fins(0.9 * k, 0.2, t); c.arch(6 * k); c.wave(t, 2.4, 4)
        c.body(pitch=-8 * k)


class Wiggle(Clip):
    """Happy shimmy: a fast sideways wiggle with the fins flicking."""
    def __init__(self): super().__init__('Wiggle', 1.8)
    def pose(self, t, c):
        k = env(t, 0.0, 0.2, 1.4, 1.8); c.bend(22 * sinp(t, 0.22) * k); c.fins(0.6 * k, 1.0, t, 0.12); c.body(roll=8 * sinp(t, 0.44) * k)


# ------------------------------------------------------------------------- touch
class Poke(Clip):
    """C-start startle: snaps into a C and darts, then flares at you."""
    group = 'touch'
    def __init__(self): super().__init__('Poke', 1.6)
    def pose(self, t, c):
        k = env(t, 0.0, 0.08, 0.15, 0.4); c.bend(70 * k); c.body(yaw=-30 * k)
        f = env(t, 0.5, 0.7, 1.2, 1.6); c.fins(f, 0.4, t); c.mouth(0.5 * f); c.arch(6 * f); c.wave(t, 0.5, 10 * (1 - f))


class Pet(Clip):
    """Nuzzles your finger: slow happy wiggle, fins soft, rolling into the touch."""
    loop = True; group = 'touch'
    def __init__(self): super().__init__('Pet', 3.0, loop=True)
    def pose(self, t, c):
        c.body(roll=15 * sinp(t, 3.0), pitch=-6); c.bend(10 * sinp(t, 1.5)); c.fins(0.4, 0.5, t, 0.3); c.mouth(0.25)
    def props(self, t, M):
        ph = (t / 1.5) % 1.0; h = M['Bone_012'].translation
        return {'Heart': T((h.x + 0.2, h.y, h.z + 0.25 + 0.3 * ph)) @ Sc(2.4 * ss(ph / 0.2) * (1 - ss((ph - 0.7) / 0.3)) + 1e-4)}


class Tickle(Clip):
    group = 'touch'
    def __init__(self): super().__init__('Tickle', 2.0)
    def pose(self, t, c):
        k = env(t, 0.0, 0.1, 1.6, 2.0); c.bend(25 * sinp(t, 0.16) * k); c.body(roll=20 * sinp(t, 0.32) * k)
        c.fins(0.8 * k, 1.0, t, 0.1); c.mouth(0.5 * k)


class Angry(Clip):
    """Poked too much: full flare, charges at the glass twice."""
    group = 'touch'
    def __init__(self): super().__init__('Angry', 2.6)
    def pose(self, t, c):
        k = env(t, 0.0, 0.2, 2.2, 2.6); c.fins(k, 0.5, t, 0.15); c.arch(12 * k); c.mouth(0.7 * k)
        l = env(t, 0.6, 0.75, 0.85, 1.1) + env(t, 1.4, 1.55, 1.65, 1.9)
        c.root.y -= 0.3 * l; c.wave(t, 0.3, 12 * l + 4)


class Dangle(Clip):
    """Scooped up by the finger: wriggling."""
    loop = True; group = 'touch'
    def __init__(self): super().__init__('Dangle', 0.6 * 3, loop=True)
    def pose(self, t, c): c.wave(t, 0.6, 26); c.fins(0.6, 1.0, t, 0.12); c.mouth(0.4 * abs(sinp(t, 0.3)))


# ------------------------------------------------------------------------- sleep
class FallAsleep(Clip):
    def __init__(self): super().__init__('FallAsleep', 3.0)
    def pose(self, t, c):
        k = ss(t / 2.6); c.root.z -= 0.35 * k; c.body(roll=18 * k, pitch=8 * k); c.fins(-0.3 * k, 0.6 * (1 - k), t, 0.4)
        c.wave(t, 2.4, 5 * (1 - k)); c.mouth(0.5 * env(t, 0.5, 0.9, 1.2, 1.6))


class Sleep(Clip):
    loop = True; group = 'idle'
    def __init__(self): super().__init__('Sleep', 5.0, loop=True)
    def pose(self, t, c):
        b = sinp(t, 5.0); c.root.z -= 0.35 - 0.02 * b; c.body(roll=18 + 3 * b, pitch=8); c.fins(-0.3, 0.15, t, 0.8); c.bend(3 * b)
    def props(self, t, M):
        out = {}; top = M['Bone_012'].translation
        for i in range(3):
            ph = ((t / 5.0) * 2 + i / 3.0) % 1.0
            s = 2.4 * ss(ph / 0.2) * (1 - ss((ph - 0.75) / 0.25)) * (0.8 + 0.25 * i)
            out[f'Z{i}'] = T(top + Vector((0.15 + 0.2 * ph, 0, 0.3 + 0.4 * ph))) @ Sc(max(1e-4, s)) if s > 0.01 else None
        return out


class WakeUp(Clip):
    def __init__(self): super().__init__('WakeUp', 2.0)
    def pose(self, t, c):
        k = 1 - ss(t / 1.2); c.root.z -= 0.35 * k; c.body(roll=18 * k, pitch=8 * k)
        f = env(t, 1.0, 1.3, 1.6, 2.0); c.fins(f, 0.6, t); c.mouth(0.8 * f); c.wave(t, 1.2, 6)


# ------------------------------------------------------------------------- music
class DanceWave(Clip):
    def __init__(self): super().__init__('DanceWave', 4.0)
    def pose(self, t, c):
        c.wave(t, 0.5, 22); c.root.z += 0.08 * abs(sinp(t, 1.0)); c.fins(0.5 + 0.5 * abs(sinp(t, 0.5)), 0.8, t, 0.15)
        c.body(roll=12 * sinp(t, 2.0))


class DanceFlare(Clip):
    """Flares on every beat like a light show."""
    def __init__(self): super().__init__('DanceFlare', 4.0)
    def pose(self, t, c):
        b = env(t % 0.5, 0.0, 0.06, 0.15, 0.45); c.fins(b, 0.5, t, 0.2); c.arch(8 * b); c.bend(12 * sinp(t, 1.0)); c.mouth(0.4 * b)
        c.root.z += 0.05 * b


class DanceSpin(Clip):
    def __init__(self): super().__init__('DanceSpin', 3.0)
    def pose(self, t, c):
        c.spin = 360 * ss(t / 2.2); c.wave(t, 0.4, 20); c.fins(0.7, 0.8, t, 0.12); c.root.z += 0.1 * math.sin(math.pi * min(1, t / 2.2))


# ------------------------------------------------------------------------- charger
class Plugged(Clip):
    """Charger in: zips up, a celebratory spin and flare."""
    def __init__(self): super().__init__('Plugged', 2.6)
    def pose(self, t, c):
        u = env(t, 0.0, 0.3, 0.6, 1.0); c.root.z += 0.25 * u; c.wave(t, 0.35, 20 * u + 6)
        c.roll = 360 * ss((t - 0.4) / 0.8) if t > 0.4 else 0.0
        f = env(t, 1.3, 1.6, 2.2, 2.6); c.fins(f, 0.6, t); c.arch(8 * f); c.mouth(0.5 * f)
    def props(self, t, M):
        if t < 1.2: return {'Heart': None}
        ph = (t - 1.2) / 1.4; h = M['Bone_012'].translation
        return {'Heart': T((h.x, h.y, h.z + 0.3 + 0.35 * ph)) @ Sc(2.6 * ss(ph / 0.2) * (1 - ss((ph - 0.7) / 0.3)) + 1e-4)}


class ChargeRest(Clip):
    """While charging: resting low in the water, fins softly spread, content."""
    loop = True; group = 'idle'
    def __init__(self): super().__init__('ChargeRest', 6.0, loop=True)
    def pose(self, t, c):
        b = sinp(t, 3.0); c.root.z -= 0.15 - 0.03 * b; c.fins(0.5, 0.4, t, 0.5); c.wave(t, 3.0, 4); c.body(roll=6 * b, pitch=4)
        c.mouth(0.2 + 0.1 * b)


class Tired(Clip):
    """Low battery: drifting, fins clamped, tail drooping, slow."""
    loop = True; group = 'idle'
    def __init__(self): super().__init__('Tired', 6.0, loop=True)
    def pose(self, t, c):
        b = sinp(t, 6.0); c.root.z -= 0.25 + 0.04 * b; c.fins(-0.5, 0.25, t, 0.8); c.arch(-8); c.body(pitch=10, roll=10 * b)
        c.wave(t, 3.0, 4); y = env(t, 3.2, 3.6, 4.0, 4.5); c.mouth(1.0 * y)


class Unplugged(Clip):
    def __init__(self): super().__init__('Unplugged', 2.2)
    def pose(self, t, c):
        k = env(t, 0.0, 0.06, 0.2, 0.5); c.bend(50 * k)
        l = env(t, 0.5, 0.8, 1.4, 1.8); c.body(pitch=25 * l, yaw=20 * l); c.fins(0.3, 0.9, t, 0.2); c.wave(t, 1.0, 6)
        f = env(t, 1.7, 1.85, 2.0, 2.2); c.fins(0.8 * f, 0, t); c.mouth(0.5 * f)


class FullCharge(Clip):
    """100%: loop-the-loop and the biggest flare of the day."""
    def __init__(self): super().__init__('FullCharge', 3.4)
    def pose(self, t, c):
        k = ss(t / 1.5); c.body(pitch=-360 * k if t < 1.5 else -360); c.root.z += 0.25 * math.sin(math.pi * min(1.0, t / 1.5))
        c.wave(t, 0.4, 18 * (1 - ss((t - 1.4) / 0.4)) + 4)
        f = env(t, 1.6, 1.9, 2.9, 3.4); c.fins(f, 0.6, t); c.arch(12 * f); c.mouth(0.6 * f)
    def props(self, t, M):
        ph = (t / 1.7) % 1.0; h = M['Bone_012'].translation
        return {'Heart': T((h.x + 0.2 * math.sin(ph * 6), h.y, h.z + 0.3 + 0.4 * ph)) @ Sc(2.6 * ss(ph / 0.2) * (1 - ss((ph - 0.7) / 0.3)) + 1e-4)}


def library():
    return [Idle(), Swim(), Dash(), Turn(), Glide(), Flare(), LookAround(), Eat(), Bubble(), Nest(), Spin(), Loop(), Yawn(), Wiggle(),
            Poke(), Pet(), Tickle(), Angry(), Dangle(), FallAsleep(), Sleep(), WakeUp(), DanceWave(), DanceFlare(), DanceSpin(),
            Plugged(), ChargeRest(), Tired(), Unplugged(), FullCharge()]
