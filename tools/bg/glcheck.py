import os, sys, ctypes
os.environ.setdefault('PYOPENGL_PLATFORM', 'egl'); os.environ.setdefault('EGL_PLATFORM', 'surfaceless')
from OpenGL import EGL
from OpenGL import GLES2 as gl
dpy = EGL.eglGetDisplay(EGL.EGL_DEFAULT_DISPLAY); EGL.eglInitialize(dpy, None, None)
attrs = (EGL.EGLint * 7)(EGL.EGL_SURFACE_TYPE, EGL.EGL_PBUFFER_BIT, EGL.EGL_RENDERABLE_TYPE, EGL.EGL_OPENGL_ES2_BIT, EGL.EGL_NONE, 0, 0)
cfg = EGL.EGLConfig(); n = EGL.EGLint(); EGL.eglChooseConfig(dpy, attrs, ctypes.pointer(cfg), 1, ctypes.pointer(n))
EGL.eglBindAPI(EGL.EGL_OPENGL_ES_API)
ctx = EGL.eglCreateContext(dpy, cfg, EGL.EGL_NO_CONTEXT, (EGL.EGLint * 3)(EGL.EGL_CONTEXT_CLIENT_VERSION, 2, EGL.EGL_NONE))
surf = EGL.eglCreatePbufferSurface(dpy, cfg, (EGL.EGLint * 5)(EGL.EGL_WIDTH, 8, EGL.EGL_HEIGHT, 8, EGL.EGL_NONE))
EGL.eglMakeCurrent(dpy, surf, surf, ctx)
for f in sys.argv[1:]:
    s = gl.glCreateShader(gl.GL_FRAGMENT_SHADER); gl.glShaderSource(s, open(f).read()); gl.glCompileShader(s)
    print(f, 'OK' if gl.glGetShaderiv(s, gl.GL_COMPILE_STATUS) else gl.glGetShaderInfoLog(s).decode())
