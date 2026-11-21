#extension GL_OES_standard_derivatives : enable
// Pulsify 3D pets: the model's own colour texture, soft studio light, flush shader eyes that
// look around and blink, and the robot's glowing screen face. GLSL ES 1.00.
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
uniform sampler2D uTex;
uniform sampler2D uNrm;        // Meshy tangent-space normal map (OpenGL convention)
uniform float uHasNrm;
uniform vec3 uShell;           // x layer 0..1 (0 = skin), y fur length, z droop
uniform float uFurDen;         // hair strands across the texture
float fh(vec2 p) { p = fract(p * vec2(443.897, 441.423)); p += dot(p, p.yx + 19.19); return fract((p.x + p.y) * p.x); }
uniform sampler2D uEyes;       // L | R eye crops (rim colour outside the iris)
uniform float uEyeMode;        // 0 none, 1 painted eyes, 2 robot screen, 3 layered eyes (eye white + movable iris)
uniform vec4 uIris;            // mode 3: iris centre offset in eye units, L.xy R.xy
uniform vec4 uEyeL;            // blink, lookX, lookY, brow tilt (+ sad, - angry)
uniform vec4 uEyeR;
uniform vec2 uFace;            // robot: expression id (0..5, fractional = cross-fade), phase
uniform vec2 uMood;            // happy (^^ lower lid), pupil scale
uniform vec3 uFur;             // eyelid colour
uniform vec3 uLash;
uniform vec3 uLightDir;        // towards the key light (GL space)
uniform float uDim;
uniform float uGlow;
uniform vec3 uScreenHalf;      // robot: half width, half height (model units), -

varying vec3 vN;
varying vec3 vP;
varying vec2 vUv;
varying vec2 vEye;
varying vec4 vCol;
varying vec2 vEye2;
varying float vGloss;

float band(float x, float w) { return 1.0 - smoothstep(w * 0.7, w, abs(x)); }

vec4 eyeShade(vec3 base, vec2 uv, vec4 E, float side, out float glossy) {
    glossy = 0.0;
    float d = length(uv);
    float inside = 1.0 - smoothstep(0.955, 1.0, d);
    if (inside <= 0.0) return vec4(base, 0.0);
    float sg = side < 0.5 ? 1.0 : -1.0;
    float ps = max(0.4, uMood.y);
    vec2 t = vec2(0.5 + (uv.x - E.y) / ps / 2.3, 1.0 - (0.5 + (uv.y - E.z) / ps / 2.3));
    t = clamp(t, 0.002, 0.998);
    vec3 eye = texture2D(uEyes, vec2(t.x * 0.5 + side * 0.5, t.y)).rgb;
    float b = E.x;
    float uu = uv.x * uv.x;
    float yup = 1.06 - b * 1.36 + uu * b * 0.30 + E.w * uv.x * 0.55 * sg - abs(E.w) * 0.32;
    float ylo = -1.06 + b * 0.84 + uMood.x * (1.5 - 0.9 * uu);
    float upper = smoothstep(-0.02, 0.02, uv.y - yup);
    float lower = smoothstep(-0.02, 0.02, ylo - uv.y);
    float lid = max(upper, lower);
    float lash = upper * (1.0 - smoothstep(0.07, 0.11, uv.y - yup)) * smoothstep(0.0, 0.12, b + abs(E.w));
    vec3 lidc = mix(uFur, uLash, lash);
    vec3 c = mix(eye, lidc, lid);
    glossy = inside * (1.0 - lid);
    return vec4(mix(base, c, inside), inside);
}

// Layered eyes (mode 3): row 0 of the atlas = the eye white, its alpha = the eye's shape; row 1 = the
// iris + pupil with alpha. The iris slides to look and scales for the pupil mood; lids only cover the eye.
vec4 eyeShade3(vec3 base, vec2 uv, vec4 E, float side, vec2 ioff, out float glossy) {
    glossy = 0.0;
    vec2 t0 = clamp(vec2(0.5 + uv.x * 0.5, 0.5 - uv.y * 0.5), 0.002, 0.998);
    vec4 w = texture2D(uEyes, vec2(t0.x * 0.5 + side * 0.5, t0.y * 0.5));
    if (w.a < 0.01) return vec4(base, 0.0);
    float ps = max(0.4, uMood.y);
    vec2 q = (uv - ioff - vec2(E.y, E.z) * 0.55) / ps;
    vec2 ti = vec2(0.5 + q.x * 0.5, 0.5 - q.y * 0.5);
    vec4 ir = texture2D(uEyes, vec2(clamp(ti.x, 0.002, 0.998) * 0.5 + side * 0.5, 0.5 + clamp(ti.y, 0.002, 0.998) * 0.5));
    ir.a *= step(0.0, ti.x) * step(ti.x, 1.0) * step(0.0, ti.y) * step(ti.y, 1.0);
    vec3 eye = mix(w.rgb, ir.rgb, ir.a);
    float sg = side < 0.5 ? 1.0 : -1.0;
    float b = E.x;
    float uu = uv.x * uv.x;
    float yup = 0.95 - b * 1.25 + uu * b * 0.30 + E.w * uv.x * 0.55 * sg - abs(E.w) * 0.30;
    float ylo = -0.95 + b * 0.75 + uMood.x * (1.35 - 0.8 * uu);
    float upper = smoothstep(-0.03, 0.03, uv.y - yup);
    float lower = smoothstep(-0.03, 0.03, ylo - uv.y);
    float lid = max(upper, lower);
    float lash = upper * (1.0 - smoothstep(0.08, 0.14, uv.y - yup)) * smoothstep(0.0, 0.12, b + abs(E.w));
    vec3 c = mix(eye, mix(uFur, uLash, lash), lid);
    glossy = w.a * (1.0 - lid);
    return vec4(mix(base, c, w.a), w.a);
}

// The robot's face, in model units around the screen centre (x right, y up).
float faceMask(vec2 q) {
    float e = uFace.x;
    float w0 = max(0.0, 1.0 - abs(e - 0.0)), w1 = max(0.0, 1.0 - abs(e - 1.0)), w2 = max(0.0, 1.0 - abs(e - 2.0));
    float w3 = max(0.0, 1.0 - abs(e - 3.0)), w4 = max(0.0, 1.0 - abs(e - 4.0)), w5 = max(0.0, 1.0 - abs(e - 5.0));
    float total = 0.0;
    for (int i = 0; i < 2; i++) {
        float sx = i == 0 ? -1.0 : 1.0;
        vec4 E = i == 0 ? uEyeL : uEyeR;
        float a = q.x - (sx * 0.40 * uScreenHalf.x + E.y * uScreenHalf.x * 0.35);
        float c = q.y - (0.012 + E.z * uScreenHalf.y * 0.35);
        float b = E.x;
        float ry = max(0.004, 0.052 * (1.0 - b));
        float ell = 1.0 - smoothstep(0.85, 1.0, (a / 0.040) * (a / 0.040) + (c / ry) * (c / ry));
        float line = band(c, 0.007) * band(a, 0.040) * smoothstep(0.6, 0.9, b);
        float s0 = max(ell, line);
        float s1 = band(length(vec2(a, c + 0.02)) - 0.036, 0.009) * step(-0.008, c);
        float hx = a / 0.045, hy = c / 0.045 + 0.15;
        float hq = hx * hx + hy * hy - 1.0;
        float s2 = 1.0 - smoothstep(-0.02, 0.02, hq * hq * hq - hx * hx * hy * hy * hy);
        float s3 = band(length(vec2(a, c - 0.022)) - 0.034, 0.008) * step(c, 0.012);
        float s4 = 1.0 - smoothstep(0.054, 0.06, length(vec2(a, c)));
        total = max(total, w0 * s0 + w1 * s1 + w2 * s2 + w3 * s3 + w4 * s4);
    }
    float dots = 0.0;
    for (int i = 0; i < 3; i++) {
        float dx = -0.05 + 0.05 * float(i);
        float dd = 1.0 - smoothstep(0.013, 0.016, length(q - vec2(dx, 0.0)));
        float br = 0.25 + 0.75 * max(0.0, cos((uFace.y - float(i) * 0.33) * 6.2832));
        dots = max(dots, dd * br);
    }
    total += w5 * dots;
    float smile = band(length(q + vec2(0.0, 0.045)) - 0.028, 0.006) * step(q.y, -0.058);
    float oo = band(length(q + vec2(0.0, 0.075)) - 0.016, 0.006);
    return min(1.0, total + (w0 + w1 + w2) * smile + w4 * oo);
}

uniform vec4 uMouth;     // glow face: grin centre line c0 + c2 x^2, half height h0 (1 - h2 x^2)
uniform float uMouthOpen; // 1 = as painted, 0 = shut
// mode 4: glowing painted eyes redrawn in code (the painted shape stays, the iris moves, lids blink)
vec3 glowEye(vec3 base, vec2 e, vec4 E, float sg, inout float shut, inout float lit) {
    float mx = max(base.r, max(base.g, base.b));
    // painted sclera (bright) plus the painted iris in the middle (dark) both get redrawn
    float m = max(smoothstep(0.30, 0.45, mx), 1.0 - smoothstep(0.62, 0.74, length(e * vec2(1.0, 1.05)))) * (1.0 - smoothstep(1.0, 1.25, length(e)));
    if (m <= 0.0) return base;
    vec2 pc = vec2(E.y, E.z) * vec2(0.42, 0.32);
    float d = length((e - pc) * vec2(1.0, 1.15));
    vec3 c = mix(vec3(1.0, 0.86, 0.42), vec3(1.0, 0.62, 0.18), smoothstep(0.0, 1.0, length(e)));
    c = mix(c, vec3(1.0, 0.42, 0.04), smoothstep(0.47, 0.42, d));              // iris
    c = mix(c, vec3(0.16, 0.03, 0.0), smoothstep(0.24, 0.19, d * (1.0 + 0.4 * (1.0 - uMood.y)))); // pupil (scales with surprise)
    c += vec3(1.0) * smoothstep(0.09, 0.05, length(e - pc - vec2(-0.13, 0.15))); // catch-light
    // eyelid: the black skin slides down from the top (with the angry slant), and up from below a little
    float lidY = 1.15 - 2.5 * E.x + E.w * e.x * sg * 0.6;
    float lid = smoothstep(-0.05, 0.05, e.y - lidY) + smoothstep(0.05, -0.05, e.y + 1.15 - 1.2 * E.x);
    shut = max(shut, clamp(lid, 0.0, 1.0) * m); lit = max(lit, m * (1.0 - clamp(lid, 0.0, 1.0)));
    c = mix(c, vec3(0.02, 0.02, 0.03), clamp(lid, 0.0, 1.0));
    return mix(base, c, m);
}
// mode 5: big cute eyes redrawn in code over the painted ones: iris and pupil follow, skin lids blink,
// happy turns them into ^ ^ arcs. uFur = iris colour, uLash = skin colour.
vec3 cuteEye(vec3 base, vec2 e, vec4 E, inout float g, inout vec3 em) {
    float r = length(e);
    float m = 1.0 - smoothstep(0.93, 1.0, r);
    if (m <= 0.0) return base;
    vec2 pc = vec2(E.y * 0.2, E.z * 0.16);
    vec2 q = e - pc; float d = length(q);
    vec3 c = vec3(0.97, 0.96, 0.95) * (0.82 + 0.18 * smoothstep(1.0, 0.3, length(e - vec2(0.15, -0.25))));
    float ir = 0.80;
    vec3 iris = mix(uFur * 0.45, uFur * 1.9 + vec3(0.05, 0.04, 0.0), smoothstep(0.1, 0.85, -q.y / ir * 0.7 + 0.35));
    iris *= 0.85 + 0.15 * sin(atan(q.y, q.x) * 23.0) * smoothstep(0.35, 0.7, d / ir);       // fine radial streaks
    iris = mix(iris, uFur * 0.25, smoothstep(0.7, 0.98, d / ir));                           // dark limbal ring
    c = mix(c, iris, smoothstep(ir + 0.03, ir - 0.03, d));
    float pr = 0.40 * clamp(uMood.y, 0.6, 1.5);
    c = mix(c, vec3(0.02, 0.02, 0.02), smoothstep(pr + 0.03, pr - 0.03, d));
    float h1 = smoothstep(0.2, 0.15, length(q - vec2(-0.30, 0.33))), h2 = smoothstep(0.085, 0.055, length(q - vec2(0.28, -0.32)));
    float h3 = smoothstep(0.05, 0.03, length(q - vec2(-0.06, 0.5)));
    float hl = max(max(h1, h2 * 0.9), h3 * 0.8);
    g = max(g, m * 0.95);
    // lids: skin slides down from the top (and a little up from below), with a soft dark lash line on its edge
    float blink = E.x, hap = uMood.x;
    float lidY = mix(1.05, -1.1, blink) - 0.1 * e.x * e.x;
    float lo = mix(-1.1, -0.35, hap * 0.0);
    float lid = smoothstep(lidY - 0.02, lidY + 0.02, e.y) + smoothstep(lo + 0.02, lo - 0.02, e.y);
    lid = clamp(lid, 0.0, 1.0);
    vec3 skin = uLash * (0.9 - 0.15 * smoothstep(0.25, 0.0, abs(e.y - lidY)));
    float lash = (1.0 - smoothstep(0.035, 0.09, abs(e.y - lidY))) * smoothstep(0.08, 0.25, blink);
    c = mix(c, skin, lid); c = mix(c, vec3(0.16, 0.08, 0.07), lash * m);
    hl *= 1.0 - lid;
    // happy: the whole eye becomes skin with a smiling ^ arc
    float arcY = -0.25 + 0.55 * (1.0 - e.x * e.x);
    float arc = (1.0 - smoothstep(0.06, 0.12, abs(e.y - arcY))) * (1.0 - smoothstep(0.75, 0.9, abs(e.x)));
    vec3 hc = mix(uLash * 0.92, vec3(0.16, 0.08, 0.07), arc);
    c = mix(c, hc, hap); hl *= 1.0 - hap; g = mix(g, 0.3, hap);
    em += vec3(1.0) * hl * m * 0.9;
    return mix(base, mix(c, vec3(1.0), hl), m);
}
uniform vec2 uDither;   // crossfade between two models: x = amount, y = +1 keep d>=x, -1 keep d<x, 0 off
void main() {
    if (uDither.y != 0.0) {
        float d = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
        if (uDither.y > 0.0 ? d < uDither.x : d >= uDither.x) discard;
    }
    vec3 base = vCol.a > 0.5 ? vCol.rgb : texture2D(uTex, vUv).rgb;
    float gloss = vGloss;
    float furK = 0.0;
    if (uShell.x > 0.0) {
        // fur shell: only skin grows hair (not props, eyes, nose, inner ears)
        if (vCol.a > 0.5 || dot(vEye, vEye) < 1.6 || dot(vEye2, vEye2) < 1.6) discard;
        float mx = max(base.r, max(base.g, base.b)), mn = min(base.r, min(base.g, base.b));
        float sat = (mx - mn) / max(0.001, mx);
        if (mx < 0.2) discard;                                                // pupils, nostrils
        if (sat > 0.1 && (base.g > base.r * 1.03 || base.b > base.r * 1.03)) discard; // irises
        if (sat > 0.25 && base.r > base.g * 1.25 && base.b > base.g * 1.02) discard;   // pink nose / inner ear
        vec2 g = vUv * uFurDen;
        vec2 id = floor(g); vec2 fr = fract(g) - 0.5;
        float h = 0.45 + 0.55 * fh(id);                                        // this strand's length
        vec2 c = (vec2(fh(id + 3.7), fh(id + 9.1)) - 0.5) * 0.5;
        float r = length(fr - c);
        float t = uShell.x / h;
        if (t > 1.0 || r > 0.5 * (1.0 - t * t)) discard;                       // tapered strand
        furK = uShell.x;
        base *= mix(0.62, 1.12, t);                                            // dark roots, bright tips
    }
    vec3 emit = vec3(0.0);
    float shut = 0.0, eyeLit = 0.0;
    if (uEyeMode > 4.5) {
        float g5 = 0.0;
        if (dot(vEye, vEye) < 1.2) base = cuteEye(base, vEye, uEyeL, g5, emit);
        if (dot(vEye2, vEye2) < 1.2) base = cuteEye(base, vEye2, uEyeR, g5, emit);
        gloss = max(gloss, g5);
    } else if (uEyeMode > 3.5) {
        if (dot(vEye, vEye) < 1.6) base = glowEye(base, vEye, uEyeL, 1.0, shut, eyeLit);
        if (dot(vEye2, vEye2) < 1.6) base = glowEye(base, vEye2, uEyeR, -1.0, shut, eyeLit);
        if (vCol.a > 0.2 && vCol.a < 0.5 && vCol.g > 0.001 && dot(vEye, vEye) > 1.6 && dot(vEye2, vEye2) > 1.6) {             // the grin closes towards its centre line
            vec2 mo = vec2(vCol.g * 2.6 - 1.3, vCol.b * 3.4 - 1.7);
            float cy = uMouth.x + uMouth.y * mo.x * mo.x, h = uMouth.z * max(0.0, 1.0 - uMouth.w * mo.x * mo.x);
            float cl = smoothstep(-0.03, 0.03, abs(mo.y - cy) - uMouthOpen * h - 0.02);
            float mx = max(base.r, max(base.g, base.b));
            base = mix(base, vec3(0.02, 0.02, 0.03), cl * smoothstep(0.2, 0.35, mx)); shut = max(shut, cl * smoothstep(0.2, 0.35, mx));
        }
    } else if (uEyeMode > 0.5 && uEyeMode < 1.5) {
        float g = 0.0;
        if (dot(vEye, vEye) < 1.1) { vec4 r = eyeShade(base, vEye, uEyeL, 0.0, g); base = r.rgb; gloss = max(gloss, g * 0.95); }
        if (dot(vEye2, vEye2) < 1.1) { vec4 r = eyeShade(base, vEye2, uEyeR, 1.0, g); base = r.rgb; gloss = max(gloss, g * 0.95); }
    } else if (uEyeMode > 2.5) {
        float g = 0.0;
        if (dot(vEye, vEye) < 1.2) { vec4 r = eyeShade3(base, vEye, uEyeL, 0.0, uIris.xy, g); base = r.rgb; gloss = max(gloss, g * 0.95); }
        if (dot(vEye2, vEye2) < 1.2) { vec4 r = eyeShade3(base, vEye2, uEyeR, 1.0, uIris.zw, g); base = r.rgb; gloss = max(gloss, g * 0.95); }
    } else if (uEyeMode > 1.5) {
        for (int k = 0; k < 2; k++) {
            vec2 sv = k == 0 ? vEye : vEye2;
            if (abs(sv.x) > 1.2 || abs(sv.y) > 1.2) continue;
            float r4 = pow(pow(abs(sv.x), 4.0) + pow(abs(sv.y), 4.0), 0.25);
            float glass = 1.0 - smoothstep(0.90, 0.95, r4);
            base = mix(base, vec3(0.012, 0.014, 0.03), glass);
            gloss = mix(gloss, 0.8, glass);
            if (k == 0) {
                float f = faceMask(sv * uScreenHalf.xy) * glass * (1.0 - smoothstep(0.80, 0.88, r4));
                emit = vec3(0.62, 0.48, 1.0) * f * 1.6;
            }
        }
    }
    vec3 N = normalize(vN);
    if (uHasNrm > 0.5 && vCol.a < 0.5 && gloss < 0.9 && uShell.x <= 0.0) {
        // tangent frame from screen derivatives (no tangents needed in the vertex data)
        vec3 dp1 = dFdx(vP), dp2 = dFdy(vP);
        vec2 du1 = dFdx(vUv), du2 = dFdy(vUv);
        vec3 p2 = cross(dp2, N), p1 = cross(N, dp1);
        vec3 T = p2 * du1.x + p1 * du2.x;
        vec3 B = p2 * du1.y + p1 * du2.y;
        float im = inversesqrt(max(1e-12, max(dot(T, T), dot(B, B))));
        vec3 t = texture2D(uNrm, vUv).xyz * 2.0 - 1.0;
        vec3 Nm = normalize(T * im * t.x + B * im * t.y + N * max(0.2, t.z));
        N = normalize(mix(N, Nm, 0.9));
    }
    vec3 V = vec3(0.0, 0.1, 1.0);
    vec3 L = normalize(uLightDir);
    float wrap = clamp((dot(N, L) + 0.35) / 1.35, 0.0, 1.0);
    float fill = clamp(dot(N, normalize(vec3(-0.7, 0.2, 0.6))) * 0.5 + 0.5, 0.0, 1.0);
    float sky = N.y * 0.5 + 0.5;
    vec3 light = vec3(1.0, 0.97, 0.92) * wrap * 0.86 + vec3(0.75, 0.82, 1.0) * fill * 0.26 + vec3(0.34, 0.33, 0.36) * sky;
    float rim = smoothstep(0.55, 1.0, 1.0 - clamp(dot(N, normalize(V)), 0.0, 1.0)) * 0.7;
    vec3 H = normalize(L + normalize(V));
    float spec = pow(clamp(dot(N, H), 0.0, 1.0), mix(8.0, 90.0, gloss)) * mix(0.04, 0.9, gloss * gloss);
    vec3 col = base * light + vec3(1.0, 0.93, 0.85) * rim * (0.28 + 0.5 * furK) + vec3(spec) * (1.0 - furK);
    col += emit;
    col = mix(col, base * (0.75 + 0.35 * uGlow) + vec3(spec) * 0.6, eyeLit);   // eyes shine by themselves
    if (vCol.a > 0.2 && vCol.a < 0.5) col += (1.0 - shut) * base * vCol.r * (0.35 + 0.45 * uGlow) * vec3(1.0, 0.75, 0.45) + vec3(1.0, 0.45, 0.1) * vCol.r * 0.08 * uGlow;   // emissive (glowing eyes)
    col *= 1.0 - uDim;
    gl_FragColor = vec4(col, 1.0);
}
