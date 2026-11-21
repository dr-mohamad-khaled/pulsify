"""Render a photo world like the app: imgworld.frag into a cache, overlay.frag on top. Writes a strip."""
import os, sys, ctypes, math, numpy as np
os.environ.setdefault('PYOPENGL_PLATFORM', 'egl'); os.environ.setdefault('EGL_PLATFORM', 'surfaceless')
from OpenGL import EGL
from OpenGL import GLES2 as gl
from PIL import Image
A = '/home/claude/pulsify/app/src/main/assets'
wid, fx, horizon, sky = int(sys.argv[1]), float(sys.argv[2]), float(sys.argv[3]), float(sys.argv[4])
shots = [tuple(map(float, s.split(':'))) for s in sys.argv[5].split(',')]   # hour:par:tiltx:time
out = sys.argv[6]
W, H = 432, 960
dpy = EGL.eglGetDisplay(EGL.EGL_DEFAULT_DISPLAY); EGL.eglInitialize(dpy, None, None)
attrs = (EGL.EGLint * 7)(EGL.EGL_SURFACE_TYPE, EGL.EGL_PBUFFER_BIT, EGL.EGL_RENDERABLE_TYPE, EGL.EGL_OPENGL_ES2_BIT, EGL.EGL_NONE, 0, 0)
cfg = EGL.EGLConfig(); n = EGL.EGLint(); EGL.eglChooseConfig(dpy, attrs, ctypes.pointer(cfg), 1, ctypes.pointer(n))
EGL.eglBindAPI(EGL.EGL_OPENGL_ES_API)
ctx = EGL.eglCreateContext(dpy, cfg, EGL.EGL_NO_CONTEXT, (EGL.EGLint * 3)(EGL.EGL_CONTEXT_CLIENT_VERSION, 2, EGL.EGL_NONE))
surf = EGL.eglCreatePbufferSurface(dpy, cfg, (EGL.EGLint * 5)(EGL.EGL_WIDTH, W, EGL.EGL_HEIGHT, H, EGL.EGL_NONE))
assert surf
EGL.eglMakeCurrent(dpy, surf, surf, ctx)
VS = "attribute vec2 aPos;\nvoid main(){ gl_Position = vec4(aPos, 0.0, 1.0); }\n"
def prog(fs):
    p = gl.glCreateProgram()
    for k, src in ((gl.GL_VERTEX_SHADER, VS), (gl.GL_FRAGMENT_SHADER, open(f'{A}/shaders/{fs}').read())):
        s = gl.glCreateShader(k); gl.glShaderSource(s, src); gl.glCompileShader(s); gl.glAttachShader(p, s)
    gl.glBindAttribLocation(p, 0, 'aPos'); gl.glLinkProgram(p); return p
P1, P2 = prog('imgworld.frag'), prog('overlay.frag')
vbo = gl.glGenBuffers(1); gl.glBindBuffer(gl.GL_ARRAY_BUFFER, vbo)
gl.glBufferData(gl.GL_ARRAY_BUFFER, 24, np.array([-1, -1, 3, -1, -1, 3], np.float32), gl.GL_STATIC_DRAW)
gl.glEnableVertexAttribArray(0); gl.glVertexAttribPointer(0, 2, gl.GL_FLOAT, False, 0, None)
def tex(img):
    t = gl.glGenTextures(1); gl.glBindTexture(gl.GL_TEXTURE_2D, t)
    gl.glTexImage2D(gl.GL_TEXTURE_2D, 0, gl.GL_RGBA, img.width, img.height, 0, gl.GL_RGBA, gl.GL_UNSIGNED_BYTE, img.convert('RGBA').tobytes())
    for p_, v in ((gl.GL_TEXTURE_MIN_FILTER, gl.GL_LINEAR), (gl.GL_TEXTURE_MAG_FILTER, gl.GL_LINEAR), (gl.GL_TEXTURE_WRAP_S, gl.GL_CLAMP_TO_EDGE), (gl.GL_TEXTURE_WRAP_T, gl.GL_CLAMP_TO_EDGE)): gl.glTexParameteri(gl.GL_TEXTURE_2D, p_, v)
    return t
imgs = {i: tex(Image.open(f'{A}/worlds/p{wid}_{i}.webp')) for i in range(1, 5)}
iw, ih = Image.open(f'{A}/worlds/p{wid}_1.webp').size
cache = gl.glGenTextures(1); gl.glBindTexture(gl.GL_TEXTURE_2D, cache)
gl.glTexImage2D(gl.GL_TEXTURE_2D, 0, gl.GL_RGBA, W, H, 0, gl.GL_RGBA, gl.GL_UNSIGNED_BYTE, None)
for p_, v in ((gl.GL_TEXTURE_MIN_FILTER, gl.GL_LINEAR), (gl.GL_TEXTURE_MAG_FILTER, gl.GL_LINEAR), (gl.GL_TEXTURE_WRAP_S, gl.GL_CLAMP_TO_EDGE), (gl.GL_TEXTURE_WRAP_T, gl.GL_CLAMP_TO_EDGE)): gl.glTexParameteri(gl.GL_TEXTURE_2D, p_, v)
fbo = gl.glGenFramebuffers(1); gl.glBindFramebuffer(gl.GL_FRAMEBUFFER, fbo); gl.glFramebufferTexture2D(gl.GL_FRAMEBUFFER, gl.GL_COLOR_ATTACHMENT0, gl.GL_TEXTURE_2D, cache, 0)
def sm(a, b, x): t = min(1, max(0, (x - a) / (b - a))); return t * t * (3 - 2 * t)
def pair(h):
    if h < 5: return 4, 4, 0
    if h < 7.5: return 4, 1, sm(5, 7.5, h)
    if h < 9: return 1, 1, 0
    if h < 11.5: return 1, 2, sm(9, 11.5, h)
    if h < 15.5: return 2, 2, 0
    if h < 17.8: return 2, 3, sm(15.5, 17.8, h)
    if h < 18.8: return 3, 3, 0
    if h < 20.5: return 3, 4, sm(18.8, 20.5, h)
    return 4, 4, 0
night = lambda h: 1 - sm(5.5, 8, h) * (1 - sm(17.5, 20, h))
dusk = lambda h: max(sm(5, 6.5, h) * (1 - sm(7.5, 9, h)), sm(16.5, 18, h) * (1 - sm(19, 20.5, h)))
ia, sa = iw / ih, W / H
mp = (sa / ia, 1, (1 - sa / ia) / 2, 0) if sa < ia else (1, ia / sa, 0, (1 - ia / sa) * 0.25)
Z = 0.94; m = min(W, H)
vS = ((1 - horizon - mp[3]) / mp[1] - 0.5) / Z + 0.5; hz = (vS - 0.5) * 2 * (0.5 * H / m)
strip = Image.new('RGB', (W * len(shots), H))
for k, (hour, par, tx, tm) in enumerate(shots):
    a, b, f = pair(hour)
    gl.glBindFramebuffer(gl.GL_FRAMEBUFFER, fbo); gl.glViewport(0, 0, W, H); gl.glUseProgram(P1)
    U = lambda p, n: gl.glGetUniformLocation(p, n)
    gl.glActiveTexture(gl.GL_TEXTURE0); gl.glBindTexture(gl.GL_TEXTURE_2D, imgs[a]); gl.glUniform1i(U(P1, 'uDay'), 0)
    gl.glActiveTexture(gl.GL_TEXTURE1); gl.glBindTexture(gl.GL_TEXTURE_2D, imgs[b]); gl.glUniform1i(U(P1, 'uNight'), 1)
    gl.glUniform2f(U(P1, 'uRes'), W, H); gl.glUniform4f(U(P1, 'uMap'), *mp); gl.glUniform1f(U(P1, 'uNightK'), f)
    gl.glUniform1f(U(P1, 'uDusk'), 0); gl.glUniform1f(U(P1, 'uHasNight'), 1)
    gl.glDrawArrays(gl.GL_TRIANGLES, 0, 3)
    gl.glBindFramebuffer(gl.GL_FRAMEBUFFER, 0); gl.glUseProgram(P2)
    gl.glActiveTexture(gl.GL_TEXTURE0); gl.glBindTexture(gl.GL_TEXTURE_2D, cache); gl.glUniform1i(U(P2, 'uTex'), 0)
    for nm, v in (('uTime', tm), ('uWorld', wid), ('uNight', night(hour)), ('uBass', 0), ('uTreble', 0), ('uBeat', 0), ('uPar', par), ('uBright', 1), ('uDim', 0),
                  ('uFx', fx), ('uHorizon', hz), ('uZoom', Z), ('uRays', (dusk(hour) * (1 - night(hour) * 0.8)) if sky else 0), ('uSky', sky), ('uFloor', -0.6)):
        gl.glUniform1f(U(P2, nm), v)
    gl.glUniform2f(U(P2, 'uRes'), W, H); gl.glUniform2f(U(P2, 'uHalf'), 0.5 * W / m, 0.5 * H / m); gl.glUniform2f(U(P2, 'uTilt'), tx, 0)
    gl.glUniform4fv(U(P2, 'uBurst'), 3, np.zeros(12, np.float32))
    gl.glDrawArrays(gl.GL_TRIANGLES, 0, 3)
    buf = (ctypes.c_ubyte * (W * H * 4))()
    gl.glReadPixels(0, 0, W, H, gl.GL_RGBA, gl.GL_UNSIGNED_BYTE, buf)
    strip.paste(Image.frombytes('RGBA', (W, H), bytes(buf)).convert('RGB').transpose(Image.FLIP_TOP_BOTTOM), (k * W, 0))
strip.save(out); print('WROTE', out)
