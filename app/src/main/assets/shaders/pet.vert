// Pulsify 3D pets (Meshy models): GPU skinning, up to 40 bones as 3x4 matrices. GLSL ES 1.00.
attribute vec3 aPos;
attribute vec4 aNrm;           // normal (signed, normalised) + gloss
attribute vec2 aUv;
attribute vec2 aEye;           // eye-local / screen-local coordinates (x4096 -> 1)
attribute vec4 aBone;
attribute vec4 aWeight;
attribute vec4 aCol;           // vertex colour (props), a = 1 for props
attribute vec2 aEye2;          // right eye (robot: back screen), same scale

uniform mat4 uVP;              // view-projection (GL space)
uniform mat4 uModel;           // Blender z-up -> GL y-up, facing turn
uniform vec4 uB[120];          // 40 bones x 3 rows (LBS) or 60 bones x 2 (dual quaternion: real, dual)
uniform float uDQ;             // 1 = dual-quaternion skinning (volume-preserving joints, cats v3)
uniform vec4 uBend;            // soft-body bend: x sideways, y towards you, z hang height, w body height
uniform float uSquash;         // + squash / - stretch about the feet
uniform vec3 uShell;           // fur shell: x = 0..1 layer (0 = skin pass), y = fur length (GL units), z = gravity droop

varying highp vec3 vN;
varying highp vec3 vP;
varying highp vec2 vUv;
varying highp vec2 vEye;
varying vec4 vCol;
varying highp vec2 vEye2;
varying float vGloss;

vec3 xf(int b, vec4 p) { return vec3(dot(uB[b * 3], p), dot(uB[b * 3 + 1], p), dot(uB[b * 3 + 2], p)); }
vec3 xn(int b, vec3 n) { return vec3(dot(uB[b * 3].xyz, n), dot(uB[b * 3 + 1].xyz, n), dot(uB[b * 3 + 2].xyz, n)); }

void main() {
    vec4 p = vec4(aPos, 1.0);
    vec3 n = aNrm.xyz;
    vec3 sp = vec3(0.0); vec3 sn = vec3(0.0);
    int b0 = int(aBone.x + 0.5); int b1 = int(aBone.y + 0.5); int b2 = int(aBone.z + 0.5); int b3 = int(aBone.w + 0.5);
    if (uDQ > 0.5) {
        vec4 r0 = uB[b0 * 2]; vec4 R = aWeight.x * r0; vec4 D = aWeight.x * uB[b0 * 2 + 1];
        vec4 r1 = uB[b1 * 2]; float s1 = dot(r0, r1) < 0.0 ? -1.0 : 1.0; R += aWeight.y * s1 * r1; D += aWeight.y * s1 * uB[b1 * 2 + 1];
        vec4 r2 = uB[b2 * 2]; float s2 = dot(r0, r2) < 0.0 ? -1.0 : 1.0; R += aWeight.z * s2 * r2; D += aWeight.z * s2 * uB[b2 * 2 + 1];
        vec4 r3 = uB[b3 * 2]; float s3 = dot(r0, r3) < 0.0 ? -1.0 : 1.0; R += aWeight.w * s3 * r3; D += aWeight.w * s3 * uB[b3 * 2 + 1];
        float L = length(R); R /= L; D /= L;
        vec3 t = 2.0 * (R.w * D.xyz - D.w * R.xyz + cross(R.xyz, D.xyz));
        sp = p.xyz + 2.0 * cross(R.xyz, cross(R.xyz, p.xyz) + R.w * p.xyz) + t;
        sn = n + 2.0 * cross(R.xyz, cross(R.xyz, n) + R.w * n);
    } else {
    sp += aWeight.x * xf(b0, p); sn += aWeight.x * xn(b0, n);
    if (aWeight.y > 0.0) { sp += aWeight.y * xf(b1, p); sn += aWeight.y * xn(b1, n); }
    if (aWeight.z > 0.0) { sp += aWeight.z * xf(b2, p); sn += aWeight.z * xn(b2, n); }
    if (aWeight.w > 0.0) { sp += aWeight.w * xf(b3, p); sn += aWeight.w * xn(b3, n); }
    }
    vec4 wp = uModel * vec4(sp, 1.0);
    // the body below the point it hangs from trails behind like a soft toy
    float bs = clamp((uBend.z - wp.y) / max(0.001, uBend.w), 0.0, 1.0); bs *= bs;
    wp.x += uBend.x * bs; wp.z += uBend.y * bs; wp.y -= 0.35 * (abs(uBend.x) + abs(uBend.y)) * bs;
    wp.y *= 1.0 - uSquash; wp.x *= 1.0 + uSquash * 0.5; wp.z *= 1.0 + uSquash * 0.5;
    vN = normalize(mat3(uModel[0].xyz, uModel[1].xyz, uModel[2].xyz) * sn);
    if (uShell.x > 0.0 && aCol.a < 0.5 && dot(aEye, aEye) > 4096.0 * 4096.0 * 1.6) {
        // fur: each shell sits a little further out along the normal and sags with gravity at the tips
        wp.xyz += vN * uShell.y * uShell.x + vec3(0.0, -1.0, 0.0) * uShell.z * uShell.x * uShell.x;
    }
    gl_Position = uVP * wp;
    vP = wp.xyz;
    vUv = vec2(aUv.x, 1.0 - aUv.y);
    vEye = aEye / 4096.0;
    vEye2 = aEye2 / 4096.0;
    vCol = aCol;
    float g = aNrm.w * 127.0; if (g < -0.5) g += 256.0;
    vGloss = g / 255.0;
}
