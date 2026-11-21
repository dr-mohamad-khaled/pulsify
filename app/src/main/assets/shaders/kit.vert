// Pulsify 3D kitty: one mesh, nine rigid bones and a bendy tail. GLSL ES 1.00.
attribute vec3 aPos;
attribute vec3 aNrm;
attribute vec2 aUv;
attribute vec4 aInfo;          // bone, role, siamese point (0..255), tail position (0..255)

uniform mat4 uVP;
uniform mat4 uBone[9];         // body, head, eyeL, eyeR, earL, earR, legL, legR, tail
uniform vec3 uTailPiv;
uniform vec4 uTail;            // curl (rad), wag (rad), wag phase, droop (rad)
uniform float uOutline;        // 0: colour pass, >0: inverted-hull outline width (model units)
uniform float uPoints;         // 1 for siamese (shows the face mask)
uniform float uPush;           // outline shell pushed back (clip z) so it only shows at the silhouette

varying vec3 vN;
varying vec2 vUv;
varying vec4 vInfo;            // role, siamese point, outline pass, siamese coat

vec3 rotX(vec3 p, float a) { float c = cos(a), s = sin(a); return vec3(p.x, c * p.y - s * p.z, s * p.y + c * p.z); }
vec3 rotZ(vec3 p, float a) { float c = cos(a), s = sin(a); return vec3(c * p.x - s * p.y, s * p.x + c * p.y, p.z); }

void main() {
    int b = int(aInfo.x + 0.5);
    float role = aInfo.y;
    vec3 p = aPos;
    vec3 n = aNrm;
    if (b == 8) {
        // The tail bends a little more at every step along its length: curl lifts it, wag swings it.
        float t = aInfo.w / 255.0;
        float wag = uTail.y * sin(uTail.z - t * 3.2) * t;
        float curl = uTail.x * t * t + uTail.w * t;
        vec3 q = p - uTailPiv;
        q = rotX(q, curl); n = rotX(n, curl);
        q = rotZ(q, wag); n = rotZ(n, wag);
        p = q + uTailPiv;
    }
    mat4 M = uBone[0];
    if (b == 1) M = uBone[1]; else if (b == 2) M = uBone[2]; else if (b == 3) M = uBone[3];
    else if (b == 4) M = uBone[4]; else if (b == 5) M = uBone[5]; else if (b == 6) M = uBone[6];
    else if (b == 7) M = uBone[7]; else if (b == 8) M = uBone[8];
    vec3 wn = normalize(mat3(M[0].xyz, M[1].xyz, M[2].xyz) * n);
    if (uOutline > 0.0) {
        // Only the fur gets a line; face details stay clean.
        if (role > 0.5) { gl_Position = vec4(2.0, 2.0, 2.0, 1.0); return; }
        p += n * uOutline;
    }
    if (role > 7.5 && uPoints < 0.5) { gl_Position = vec4(2.0, 2.0, 2.0, 1.0); return; }
    gl_Position = uVP * M * vec4(p, 1.0);
    if (uOutline > 0.0) gl_Position.z += uPush * gl_Position.w;
    vN = wn;
    vUv = vec2(aUv.x, 1.0 - aUv.y);
    vInfo = vec4(role, aInfo.z / 255.0, uOutline > 0.0 ? 1.0 : 0.0, uPoints);
}
