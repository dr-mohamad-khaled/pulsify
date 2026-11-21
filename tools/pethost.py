"""Desktop harness: runs the app's real pet.vert/pet.frag on Mesa (GLES 2 via EGL, surfaceless) with a
.pet model, to check that the shaders compile and that clips look right. Writes a contact sheet PNG.

python3 pethost.py <assets dir> <model> <clip,clip,...> <out.png>
"""
import os, sys, math, ctypes, struct
os.environ.setdefault('PYOPENGL_PLATFORM', 'egl')
os.environ.setdefault('EGL_PLATFORM', 'surfaceless')
import numpy as np
from OpenGL import EGL
from OpenGL import GLES2 as gl
from PIL import Image
sys.path.insert(0, os.path.dirname(__file__))
from petcheck import load

assets, model, clips, out = sys.argv[1:5]
S = 360

# ---------- EGL context ----------
dpy = EGL.eglGetDisplay(EGL.EGL_DEFAULT_DISPLAY)
major, minor = EGL.EGLint(), EGL.EGLint()
assert EGL.eglInitialize(dpy, ctypes.pointer(major), ctypes.pointer(minor))
attrs = (EGL.EGLint * 13)(EGL.EGL_SURFACE_TYPE, EGL.EGL_PBUFFER_BIT, EGL.EGL_RENDERABLE_TYPE, EGL.EGL_OPENGL_ES2_BIT,
                          EGL.EGL_RED_SIZE, 8, EGL.EGL_GREEN_SIZE, 8, EGL.EGL_BLUE_SIZE, 8, EGL.EGL_DEPTH_SIZE, 16, EGL.EGL_NONE)
cfg = EGL.EGLConfig(); n = EGL.EGLint()
assert EGL.eglChooseConfig(dpy, attrs, ctypes.pointer(cfg), 1, ctypes.pointer(n)) and n.value > 0
EGL.eglBindAPI(EGL.EGL_OPENGL_ES_API)
ctx = EGL.eglCreateContext(dpy, cfg, EGL.EGL_NO_CONTEXT, (EGL.EGLint * 3)(EGL.EGL_CONTEXT_CLIENT_VERSION, 2, EGL.EGL_NONE))
surf = EGL.eglCreatePbufferSurface(dpy, cfg, (EGL.EGLint * 5)(EGL.EGL_WIDTH, S, EGL.EGL_HEIGHT, S, EGL.EGL_NONE))
assert EGL.eglMakeCurrent(dpy, surf, surf, ctx)
print('GL', gl.glGetString(gl.GL_RENDERER), gl.glGetString(gl.GL_VERSION))

def sh(kind, src):
    s = gl.glCreateShader(kind); gl.glShaderSource(s, src); gl.glCompileShader(s)
    if not gl.glGetShaderiv(s, gl.GL_COMPILE_STATUS): raise SystemExit('COMPILE ' + gl.glGetShaderInfoLog(s).decode())
    return s
vs = open(os.path.join(assets, 'shaders/pet.vert')).read(); fs = open(os.path.join(assets, 'shaders/pet.frag')).read()
prog = gl.glCreateProgram(); gl.glAttachShader(prog, sh(gl.GL_VERTEX_SHADER, vs)); gl.glAttachShader(prog, sh(gl.GL_FRAGMENT_SHADER, fs))
gl.glBindAttribLocation(prog, 0, 'aPos'); gl.glLinkProgram(prog)
if not gl.glGetProgramiv(prog, gl.GL_LINK_STATUS): raise SystemExit('LINK ' + gl.glGetProgramInfoLog(prog).decode())
print('SHADERS OK')
gl.glUseProgram(prog)
U = lambda n: gl.glGetUniformLocation(prog, n)
A = lambda n: gl.glGetAttribLocation(prog, n)

raw = open(os.path.join(assets, f'pets/{model}.pet'), 'rb').read()
nv, ni, nb, nc, height, fps, eye_mode = struct.unpack_from('<IIIIffI', raw, 4)
ex = 4 if raw[:4] == b'PET2' else 0
fur = struct.unpack_from('<3f', raw, 32 + ex); lash = struct.unpack_from('<3f', raw, 44 + ex)
vo = 56 + ex
iris = struct.unpack_from('<4f', raw, vo) if eye_mode == 3 else (0, 0, 0, 0)
if eye_mode == 3: vo += 16
vbo = gl.glGenBuffers(1); gl.glBindBuffer(gl.GL_ARRAY_BUFFER, vbo)
gl.glBufferData(gl.GL_ARRAY_BUFFER, nv * 40, raw[vo:vo + nv * 40], gl.GL_STATIC_DRAW)
ibo = gl.glGenBuffers(1); gl.glBindBuffer(gl.GL_ELEMENT_ARRAY_BUFFER, ibo)
gl.glBufferData(gl.GL_ELEMENT_ARRAY_BUFFER, ni * 2, raw[vo + nv * 40: vo + nv * 40 + ni * 2], gl.GL_STATIC_DRAW)
P = load(os.path.join(assets, f'pets/{model}.pet'))

def tex(path):
    im = Image.open(path).convert('RGBA'); t = gl.glGenTextures(1)
    gl.glBindTexture(gl.GL_TEXTURE_2D, t)
    gl.glTexImage2D(gl.GL_TEXTURE_2D, 0, gl.GL_RGBA, im.width, im.height, 0, gl.GL_RGBA, gl.GL_UNSIGNED_BYTE, im.tobytes())
    gl.glTexParameteri(gl.GL_TEXTURE_2D, gl.GL_TEXTURE_MIN_FILTER, gl.GL_LINEAR); gl.glTexParameteri(gl.GL_TEXTURE_2D, gl.GL_TEXTURE_MAG_FILTER, gl.GL_LINEAR)
    gl.glTexParameteri(gl.GL_TEXTURE_2D, gl.GL_TEXTURE_WRAP_S, gl.GL_CLAMP_TO_EDGE); gl.glTexParameteri(gl.GL_TEXTURE_2D, gl.GL_TEXTURE_WRAP_T, gl.GL_CLAMP_TO_EDGE)
    return t
t0 = tex(os.path.join(assets, f'pets/{model}.jpg'))
ep = os.path.join(assets, f'pets/{model}_eyes.png')
t1 = tex(ep) if os.path.exists(ep) else t0

def persp(fovy, asp, n, f):
    t = 1 / math.tan(math.radians(fovy) / 2)
    return np.array([[t / asp, 0, 0, 0], [0, t, 0, 0], [0, 0, (f + n) / (n - f), 2 * f * n / (n - f)], [0, 0, -1, 0]], np.float32)
def look(eye, at, up):
    eye, at, up = map(np.array, (eye, at, up)); f = at - eye; f /= np.linalg.norm(f); s = np.cross(f, up); s /= np.linalg.norm(s); u = np.cross(s, f)
    M = np.eye(4, dtype=np.float32); M[0, :3] = s; M[1, :3] = u; M[2, :3] = -f; M[:3, 3] = -M[:3, :3] @ eye; return M
boxR, boxCz, dist = 0.9, 0.62, 9.0
VP = persp(math.degrees(2 * math.atan(boxR / dist)), 1, dist - boxR * 3, dist + boxR * 3) @ look((0, boxCz + dist * 0.12, dist), (0, boxCz, 0), (0, 1, 0))
def model_m(yaw):
    c, s = math.cos(yaw), math.sin(yaw)
    Ry = np.array([[c, 0, s, 0], [0, 1, 0, 0], [-s, 0, c, 0], [0, 0, 0, 1]], np.float32)
    conv = np.array([[1, 0, 0, 0], [0, 0, 1, 0], [0, -1, 0, 0], [0, 0, 0, 1]], np.float32)
    return Ry @ conv

gl.glEnable(gl.GL_DEPTH_TEST); gl.glClearColor(0.07, 0.07, 0.1, 1)
def attr(name, n, typ, norm, off):
    a = A(name)
    if a < 0: return
    gl.glEnableVertexAttribArray(a); gl.glVertexAttribPointer(a, n, typ, norm, 40, ctypes.c_void_p(off))
gl.glEnableVertexAttribArray(0); gl.glVertexAttribPointer(0, 3, gl.GL_FLOAT, False, 40, ctypes.c_void_p(0))
attr('aNrm', 4, gl.GL_BYTE, True, 12); attr('aUv', 2, gl.GL_UNSIGNED_SHORT, True, 16); attr('aEye', 2, gl.GL_SHORT, False, 20)
attr('aBone', 4, gl.GL_UNSIGNED_BYTE, False, 24); attr('aWeight', 4, gl.GL_UNSIGNED_BYTE, True, 28)
attr('aCol', 4, gl.GL_UNSIGNED_BYTE, True, 32); attr('aEye2', 2, gl.GL_SHORT, False, 36)
gl.glUniform1i(U('uTex'), 0); gl.glUniform1i(U('uEyes'), 1)
gl.glActiveTexture(gl.GL_TEXTURE0); gl.glBindTexture(gl.GL_TEXTURE_2D, t0)
gl.glActiveTexture(gl.GL_TEXTURE1); gl.glBindTexture(gl.GL_TEXTURE_2D, t1)
gl.glUniform1f(U('uEyeMode'), float(eye_mode)); gl.glUniform4f(U('uIris'), *iris); gl.glUniform3f(U('uFur'), *fur); gl.glUniform3f(U('uLash'), *lash)
gl.glUniform3f(U('uLightDir'), 0.45, 0.65, 0.62); gl.glUniform1f(U('uDim'), 0); gl.glUniform1f(U('uGlow'), 0)
gl.glUniform3f(U('uScreenHalf'), 0.244, 0.142, 0)
gl.glUniformMatrix4fv(U('uVP'), 1, True, VP)
ub = U('uB[0]') if U('uB[0]') >= 0 else U('uB')
names = clips.split(',')
sheet = Image.new('RGB', (S * 4, S * len(names)))
for r, nm in enumerate(names):
    m, c, loop = P['clips'][nm]
    for i, f in enumerate(np.linspace(0, len(m) - 1, 4).astype(int)):
        gl.glClear(gl.GL_COLOR_BUFFER_BIT | gl.GL_DEPTH_BUFFER_BIT)
        gl.glUniformMatrix4fv(U('uModel'), 1, True, model_m(0.35))
        gl.glUniform4fv(ub, nb * 3, m[f].reshape(-1).astype(np.float32))
        tilt = c[f][8] if len(c[f]) > 8 else 0.0
        gl.glUniform4f(U('uEyeL'), c[f][0], c[f][2], c[f][3], tilt); gl.glUniform4f(U('uEyeR'), c[f][1], c[f][4], c[f][5], tilt)
        gl.glUniform2f(U('uMood'), c[f][9] if len(c[f]) > 9 else 0.0, c[f][10] if len(c[f]) > 10 and c[f][10] > 0.05 else 1.0)
        gl.glUniform2f(U('uFace'), c[f][6], c[f][7])
        gl.glDrawElements(gl.GL_TRIANGLES, ni, gl.GL_UNSIGNED_SHORT, None)
        buf = (ctypes.c_ubyte * (S * S * 4))()
        gl.glReadPixels(0, 0, S, S, gl.GL_RGBA, gl.GL_UNSIGNED_BYTE, buf)
        im = Image.frombytes('RGBA', (S, S), bytes(buf)).convert('RGB').transpose(Image.FLIP_TOP_BOTTOM)
        sheet.paste(im, (i * S, r * S))
sheet.save(out); print('WROTE', out)
