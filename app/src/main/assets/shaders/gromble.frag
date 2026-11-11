// Pulsify — Gromble, the wannabe-evil imp. GLSL ES 1.00. Shares mochi.vert.
// Character space: y up, feet on the floor at y = -0.55, a fuzzy ball about 0.7 tall with horns.
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif

varying vec2 vUv;

uniform float uTime;
uniform vec2  uPos;
uniform float uSize;
uniform float uPx;
uniform vec2  uSquash;
uniform float uLean;       // rotation around the feet (face-plant = 1.5)
uniform float uLift;       // hover height while "flying"
uniform vec2  uLook;
uniform vec4  uEye;        // open, mode (0 open, 1 happy, 2 asleep, 3 scared, 4 dizzy), brow (-1 angry .. 1 worried), blush
uniform float uMouth;      // 0 smirk, 1 laugh, 2 "o", 3 wavy, 4 cough, 5 roar
uniform vec4  uArms;       // left raise, right raise, cover face, cross
uniform float uWing;       // 0 folded .. 1 up
uniform vec2  uTail;       // phase, amount
uniform float uPuff;       // zapped fur
uniform vec4  uCape;       // visible, swish, tangle, -
uniform vec4  uProp;       // kind (1 scroll, 2 fireball, 3 teddy, 4 jar), a, b, visible
uniform vec3  uFur;
uniform vec3  uBelly;
uniform vec3  uHorn;
uniform vec3  uEyeC;
uniform float uRainbow;    // rainbow horns
uniform float uHornGlow;
uniform vec3  uRim;
uniform vec3  uGlow;
uniform float uBatt;
uniform vec4  uFx[6];      // x, y, life, kind: 1 smoke, 2 star, 3 sweat, 4 "!", 5 note, 6 spark, 7 heart, 8 zzz
uniform float uDim;

float sdEllipse(vec2 p, vec2 r) { float k0 = length(p / r); float k1 = length(p / (r * r)); return k0 * (k0 - 1.0) / max(k1, 1e-4); }
float sdRoundBox(vec2 p, vec2 b, float r) { vec2 q = abs(p) - b + r; return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r; }
float sdSeg(vec2 p, vec2 a, vec2 b) { vec2 pa = p - a, ba = b - a; float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0); return length(pa - ba * h); }
float sdCapsule(vec2 p, vec2 a, vec2 b, float ra, float rb) { vec2 pa = p - a, ba = b - a; float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0); return length(pa - ba * h) - mix(ra, rb, h); }
float smin(float a, float b, float k) { float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0); return mix(b, a, h) - k * h * (1.0 - h); }
vec2 rot(vec2 p, float a) { float c = cos(a), s = sin(a); return vec2(c * p.x - s * p.y, s * p.x + c * p.y); }
float sdTri(vec2 p, vec2 a, vec2 b, vec2 c) {
    vec2 e0 = b - a, e1 = c - b, e2 = a - c, v0 = p - a, v1 = p - b, v2 = p - c;
    vec2 pq0 = v0 - e0 * clamp(dot(v0, e0) / dot(e0, e0), 0.0, 1.0);
    vec2 pq1 = v1 - e1 * clamp(dot(v1, e1) / dot(e1, e1), 0.0, 1.0);
    vec2 pq2 = v2 - e2 * clamp(dot(v2, e2) / dot(e2, e2), 0.0, 1.0);
    float s = sign(e0.x * e2.y - e0.y * e2.x);
    vec2 d = min(min(vec2(dot(pq0, pq0), s * (v0.x * e0.y - v0.y * e0.x)), vec2(dot(pq1, pq1), s * (v1.x * e1.y - v1.y * e1.x))), vec2(dot(pq2, pq2), s * (v2.x * e2.y - v2.y * e2.x)));
    return -sqrt(d.x) * sign(d.y);
}
float sdArc(vec2 p, float r, float halfAng) {
    float a = atan(p.x, p.y);
    if (abs(a) < halfAng) return abs(length(p) - r);
    vec2 e = vec2(sin(halfAng) * sign(p.x), cos(halfAng)) * r; return length(p - e);
}
float sdHeart(vec2 p) {
    p.x = abs(p.x);
    if (p.y + p.x > 1.0) return sqrt(dot(p - vec2(0.25, 0.75), p - vec2(0.25, 0.75))) - sqrt(2.0) / 4.0;
    return sqrt(min(dot(p - vec2(0.0, 1.0), p - vec2(0.0, 1.0)), dot(p - 0.5 * max(p.x + p.y, 0.0), p - 0.5 * max(p.x + p.y, 0.0)))) * sign(p.x - p.y);
}
float h21(vec2 p) { p = fract(p * vec2(123.34, 456.21)); p += dot(p, p + 45.32); return fract(p.x * p.y); }
float vnoise(vec2 p) {
    vec2 i = floor(p), f = fract(p); f = f * f * (3.0 - 2.0 * f);
    return mix(mix(h21(i), h21(i + vec2(1, 0)), f.x), mix(h21(i + vec2(0, 1)), h21(i + vec2(1, 1)), f.x), f.y);
}
void over(inout vec4 dst, vec3 c, float a) { dst.rgb = dst.rgb * (1.0 - a) + c * a; dst.a = dst.a * (1.0 - a) + a; }
void glowAdd(inout vec4 dst, vec3 c, float a) { dst.rgb += c * a; dst.a = min(1.0, dst.a + a * 0.6); }
float fill(float d) { return clamp(0.5 - d / (uPx * 1.3), 0.0, 1.0); }
float sdBolt(vec2 q) {
    return min(sdTri(q, vec2(0.18, 0.5), vec2(-0.32, -0.06), vec2(0.06, -0.06)), sdTri(q, vec2(-0.18, -0.5), vec2(0.32, 0.06), vec2(-0.06, 0.06)));
}


float fluff(float d, vec2 p, float amt) {
    if (abs(d) > 0.08) return d;
    vec2 r1 = vec2(p.x * 0.8 - p.y * 0.6, p.x * 0.6 + p.y * 0.8);
    float n = vnoise(r1 * 19.0) * 0.6 + vnoise(vec2(r1.y, -r1.x) * 44.0 + 3.7) * 0.4;
    float spike = pow(abs(sin(atan(p.y + 0.2, p.x) * 13.0)), 3.0) * uPuff * 0.05;
    return d + (n - 0.5) * 0.028 * amt * smoothstep(0.08, 0.0, abs(d)) - spike;
}
float fillFur(float d) { return clamp(0.5 - d / (uPx * 3.2), 0.0, 1.0); }
vec3 lit(vec3 base, vec2 q, vec2 p) {
    float t = clamp(1.0 - dot(q, q), 0.0, 1.0);
    vec3 n = normalize(vec3(q * 0.95, sqrt(t) + 0.3));
    float ndl = dot(n, normalize(vec3(-0.42, 0.62, 0.72)));
    float diff = smoothstep(-0.35, 0.85, ndl);
    vec3 col = mix(base * vec3(0.45, 0.4, 0.7), base * 1.08, diff);
    col += vec3(1.0) * pow(max(ndl, 0.0), 7.0) * 0.08;
    col *= 0.96 + 0.07 * vnoise(vec2(p.x * 0.8 - p.y * 0.6, p.x * 0.6 + p.y * 0.8) * 26.0);
    col += uRim * pow(1.0 - n.z, 2.4) * smoothstep(-0.5, 0.7, q.x * 0.7 - q.y * 0.25 + 0.35) * 0.75;
    return col;
}
vec3 rainbow(float t) { return 0.55 + 0.45 * cos(6.2832 * (t + vec3(0.0, 0.33, 0.67))); }

void main() {
    vec4 acc = vec4(0.0);
    vec2 sp = (vUv - uPos) / uSize;
    vec2 feet = vec2(0.0, -0.55);
    // Shadow shrinks when it hovers.
    {
        vec2 q = (sp - vec2(0.02, -0.565)) / vec2(0.36 - 0.1 * uLift, 0.05);
        over(acc, vec3(0.0), exp(-dot(q, q) * 1.6) * 0.45 * (1.0 - 0.5 * uLift));
    }
    vec2 p = sp - vec2(0.0, uLift * 0.3);
    p = rot(p - feet, uLean) + feet;
    p.y = (p.y + 0.55) / uSquash.y - 0.55;
    p.x /= uSquash.x;
    vec2 bc = vec2(0.0, -0.2);
    vec2 br = vec2(0.31, 0.335);
    float fall = smoothstep(0.3, 1.2, abs(uLean));

    if (length(p - bc) < 1.0) {
    // ---- Cape behind ----
    if (uCape.x > 0.01) {
        vec2 q = p - vec2(0.0, 0.0);
        float sway = uCape.y * (0.0 - q.y) * 0.5;
        float wHalf = mix(0.2, 0.42, clamp((0.02 - q.y) / 0.5, 0.0, 1.0));
        float hem = -0.5 + 0.025 * sin((q.x - sway) * 30.0 + uTime * 3.0);
        float cape = max(abs(q.x - sway) - wHalf, max(q.y - 0.02, hem - q.y));
        vec3 cc = mix(vec3(0.75, 0.08, 0.15), vec3(0.4, 0.02, 0.12), 0.5 + 0.5 * sin((q.x - sway) * 26.0));
        over(acc, cc, fill(cape) * uCape.x);
        // Tall collar.
        for (int s = 0; s < 2; s++) {
            float side = s == 0 ? -1.0 : 1.0;
            float col = sdTri(p, vec2(0.16 * side, -0.02), vec2(0.34 * side, 0.0), vec2(0.33 * side, 0.24));
            over(acc, vec3(0.6, 0.05, 0.14), fill(col) * uCape.x);
        }
    }
    // ---- Tail with an arrow tip ----
    {
        vec2 a = vec2(0.22, -0.42);
        float best = 1e5;
        for (int i = 0; i < 6; i++) {
            float fi = float(i);
            float aa = 1.9 - fi * 0.42 + uTail.y * sin(uTail.x - fi * 0.6);
            vec2 b = a + vec2(sin(aa), cos(aa)) * 0.07;
            best = min(best, sdCapsule(p, a, b, 0.018, 0.014));
            a = b;
        }
        float aa = 1.9 - 6.0 * 0.42 + uTail.y * sin(uTail.x - 3.6);
        vec2 d = vec2(sin(aa), cos(aa)), n = vec2(-d.y, d.x);
        float tip = sdTri(p, a + n * 0.045, a - n * 0.045, a + d * 0.085);
        over(acc, uFur * 0.75 + uRim * 0.12, fill(best));
        over(acc, uHorn, fill(tip));
    }
    // ---- Bat wings ----
    for (int s = 0; s < 2; s++) {
        float side = s == 0 ? -1.0 : 1.0;
        vec2 q = p - vec2(0.23 * side, -0.02);
        q.x *= side;
        q = rot(q, -(0.2 + uWing * 1.0));
        float wing = sdTri(q, vec2(0.0, 0.03), vec2(0.0, -0.08), vec2(0.27, 0.08));
        wing = min(wing, sdTri(q, vec2(0.0, 0.03), vec2(0.27, 0.08), vec2(0.12, 0.2)));
        // Scalloped lower edge.
        wing = max(wing, -(length(q - vec2(0.09, -0.07)) - 0.07));
        wing = max(wing, -(length(q - vec2(0.22, -0.01)) - 0.07));
        vec3 wc = mix(uHorn * 0.55, uFur * 1.3 + 0.05, 0.55);
        over(acc, wc * (0.8 + 0.3 * smoothstep(0.0, 0.25, q.x)), fill(wing));
        over(acc, uFur * 0.6, fill(min(sdSeg(q, vec2(0.0, 0.03), vec2(0.27, 0.08)), sdSeg(q, vec2(0.0, 0.03), vec2(0.12, 0.2))) - 0.007));
    }
    // ---- Horns ----
    for (int s = 0; s < 2; s++) {
        float side = s == 0 ? -1.0 : 1.0;
        vec2 a = vec2(0.15 * side, 0.07), b = vec2(0.25 * side, 0.2), c = vec2(0.21 * side, 0.36);
        float h1 = sdCapsule(p, a, b, 0.055, 0.04), h2 = sdCapsule(p, b, c, 0.04, 0.008);
        float horn = smin(h1, h2, 0.03);
        if (horn < 0.12) {
            float along = clamp((p.y - 0.05) / 0.32, 0.0, 1.0);
            vec3 hc = mix(uHorn, rainbow(along * 0.9 - uTime * 0.35 + float(s) * 0.5), uRainbow);
            hc *= 0.8 + 0.3 * step(0.5, fract(along * 5.0));
            hc = mix(hc * vec3(0.5, 0.45, 0.7), hc * 1.1, smoothstep(0.03, -0.03, (p.x - mix(a.x, c.x, along)) * 1.0 + 0.01));
            over(acc, hc + hc * uHornGlow * 0.6, fill(horn));
            glowAdd(acc, mix(uHorn, rainbow(along - uTime * 0.35), uRainbow), exp(-max(horn, 0.0) * 22.0) * 0.6 * uHornGlow);
        }
    }
    // ---- Feet ----
    for (int s = 0; s < 2; s++) {
        float side = s == 0 ? -1.0 : 1.0;
        vec2 c = vec2(0.13 * side, -0.525);
        float ft = sdEllipse(p - c, vec2(0.085, 0.04));
        over(acc, lit(uFur * 0.8, (p - c) / vec2(0.1, 0.06), p), fillFur(fluff(ft, p, 0.4)));
        for (int k = 0; k < 3; k++) {
            vec2 t = c + vec2((float(k) - 1.0) * 0.045 + 0.02 * side, -0.02);
            over(acc, vec3(0.95, 0.92, 0.85), fill(sdTri(p, t + vec2(-0.012, 0.012), t + vec2(0.012, 0.012), t + vec2(0.0, -0.02))));
        }
    }
    // ---- Body: one fuzzy ball ----
    float body = sdEllipse(p - bc, br);
    {
        float d = fluff(body, p, 1.5);
        vec2 q = (p - bc) / (br * 1.2);
        vec3 col = lit(uFur, q, p);
        float belly = sdEllipse(p - bc - vec2(0.0, -0.17), vec2(0.17, 0.13));
        col = mix(col, lit(uBelly, q, p), smoothstep(0.03, -0.03, fluff(belly, p * 1.3, 1.2)) * 0.9);
        over(acc, col, fillFur(d));
    }
    float inBody = fill(body + 0.01);
    vec2 f = p - vec2(uLook.x * 0.035, uLook.y * 0.022);

    // ---- Face ----
    float mode = uEye.y;
    for (int s = 0; s < 2; s++) {
        float side = s == 0 ? -1.0 : 1.0;
        vec2 ec = f - vec2(0.115 * side, -0.1);
        float big = mode > 2.5 && mode < 3.5 ? 1.15 : 1.0;
        vec2 er = vec2(0.08, 0.092 * max(uEye.x, 0.06)) * big;
        float eye = sdEllipse(ec, er);
        if (mode < 0.5 || (mode > 2.5 && mode < 3.5)) {
            over(acc, vec3(0.03, 0.01, 0.06), fill(eye - 0.01) * inBody);
            vec3 sc = mix(uEyeC * 1.15, uEyeC * 0.6, smoothstep(-0.06, 0.08, ec.y));
            over(acc, sc, fill(eye) * inBody);
            glowAdd(acc, uEyeC, exp(-max(eye, 0.0) * 30.0) * 0.25 * inBody);
            vec2 look = uLook * vec2(0.02, 0.016);
            float pr = mode > 2.5 ? 0.016 : 0.036;
            float pupil = sdEllipse(ec - look, vec2(pr * (mode > 2.5 ? 1.0 : 0.62), pr * 1.3));
            over(acc, vec3(0.02, 0.0, 0.04), fill(max(pupil, eye)) * inBody);
            over(acc, vec3(1.0), fill(max(length(ec - look * 0.5 - vec2(-0.024, 0.03)) - 0.016, eye)) * inBody);
            over(acc, vec3(1.0), fill(max(length(ec - look * 0.5 - vec2(0.02, -0.03)) - 0.007, eye)) * inBody * 0.8);
        } else if (mode < 1.5) {
            over(acc, vec3(0.03, 0.01, 0.06), fill(sdArc(ec + vec2(0.0, 0.025), 0.055, 1.15) - 0.011) * inBody);
        } else if (mode < 2.5) {
            over(acc, vec3(0.03, 0.01, 0.06), fill(sdArc(ec * vec2(1.0, -1.0) + vec2(0.0, 0.02), 0.055, 1.05) - 0.009) * inBody);
        } else {
            // Dizzy: spirals.
            float a = atan(ec.y, ec.x), r = length(ec);
            float spiral = abs(fract(r * 14.0 - a / 6.2832 - uTime * 2.0 * side) - 0.5) - 0.2;
            over(acc, vec3(0.98), fill(length(ec) - 0.075) * inBody);
            over(acc, vec3(0.03, 0.01, 0.06), fill(max(spiral / 14.0, length(ec) - 0.072)) * inBody);
        }
        // Brow: a thick fuzzy bar. Angry tilts in, worried tilts out.
        vec2 bq = rot(f - vec2(0.115 * side, 0.02 + 0.02 * max(uEye.z, 0.0) + (mode > 2.5 && mode < 3.5 ? 0.03 : 0.0)), uEye.z * 0.5 * side);
        float brow = sdRoundBox(bq, vec2(0.085, 0.024), 0.02);
        over(acc, uFur * 0.55, fill(brow) * inBody);
    }
    // Blush.
    for (int s = 0; s < 2; s++) {
        float side = s == 0 ? -1.0 : 1.0;
        vec2 bq = (f - vec2(0.2 * side, -0.2)) / vec2(0.06, 0.035);
        over(acc, vec3(1.0, 0.35, 0.5), exp(-dot(bq, bq) * 1.3) * 0.6 * uEye.w * inBody);
    }
    // Mouth.
    {
        vec2 mc = f - vec2(0.0, -0.27);
        vec3 dark = vec3(0.25, 0.03, 0.1);
        if (uMouth < 0.5) {
            // Lopsided evil smirk with one fang.
            float sm = sdArc(rot(mc, 0.12) * vec2(1.0, -1.0) + vec2(0.0, 0.07), 0.11, 0.75) - 0.008;
            over(acc, vec3(0.05, 0.0, 0.05), fill(sm) * inBody);
            over(acc, vec3(1.0), fill(sdTri(mc, vec2(0.045, -0.012), vec2(0.075, -0.004), vec2(0.062, -0.045))) * inBody);
            over(acc, vec3(1.0), fill(sdTri(mc, vec2(-0.07, -0.006), vec2(-0.04, -0.014), vec2(-0.058, -0.04))) * inBody);
        } else if (uMouth < 1.5 || uMouth > 4.5) {
            float w = uMouth > 4.5 ? 0.15 : 0.13, hgt = uMouth > 4.5 ? 0.1 : 0.07 + 0.02 * sin(uTime * 22.0);
            float m = max(sdEllipse(mc - vec2(0.0, 0.01), vec2(w, hgt)), mc.y - 0.02);
            over(acc, dark, fill(m) * inBody);
            over(acc, vec3(1.0, 0.4, 0.5), fill(max(sdEllipse(mc - vec2(0.0, -0.06), vec2(0.07, 0.04)), m)) * inBody);
            for (int k = 0; k < 2; k++) {
                float sx = k == 0 ? -1.0 : 1.0;
                over(acc, vec3(1.0), fill(sdTri(mc, vec2(0.05 * sx, 0.022), vec2(0.095 * sx, 0.022), vec2(0.072 * sx, -0.03))) * inBody);
            }
        } else if (uMouth < 2.5) {
            over(acc, dark, fill(sdEllipse(mc, vec2(0.035, 0.045))) * inBody);
        } else if (uMouth < 3.5) {
            float wv = abs(mc.y - 0.012 * sin(mc.x * 60.0)) - 0.007;
            over(acc, vec3(0.05, 0.0, 0.05), fill(max(wv, abs(mc.x) - 0.08)) * inBody);
        } else {
            over(acc, dark, fill(sdEllipse(mc, vec2(0.055, 0.04 + 0.02 * sin(uTime * 30.0)))) * inBody);
        }
    }
    // ---- Arms: stubby, with claws ----
    for (int s = 0; s < 2; s++) {
        float side = s == 0 ? -1.0 : 1.0;
        float raise = s == 0 ? uArms.x : uArms.y;
        vec2 sh = vec2(0.26 * side, -0.24);
        vec2 hand = sh + rot(vec2(0.0, -0.15), (0.35 + raise * 2.3) * side);
        // Covering the face: hands come to the eyes. Crossed: hands on the belly.
        hand = mix(hand, vec2(0.11 * side, -0.1), uArms.z);
        hand = mix(hand, vec2(-0.06 * side, -0.33), uArms.w);
        float arm = sdCapsule(p, sh, hand, 0.05, 0.048);
        over(acc, lit(uFur * 0.9, (p - mix(sh, hand, 0.5)) / vec2(0.12, 0.12), p), fillFur(fluff(arm, p, 0.6)));
        float paw = length(p - hand) - 0.058;
        over(acc, lit(uFur * 1.1 + 0.02, (p - hand) / vec2(0.07), p), fillFur(fluff(paw, p, 0.5)));
        vec2 dir = normalize(hand - sh + vec2(0.0001));
        for (int k = 0; k < 3; k++) {
            vec2 n = rot(dir, (float(k) - 1.0) * 0.55);
            vec2 t = hand + n * 0.05;
            over(acc, vec3(0.95, 0.92, 0.85), fill(sdCapsule(p, t, t + n * 0.03, 0.01, 0.002)));
        }
    }
    // Cape tangled over the face.
    if (uCape.x * uCape.z > 0.01) {
        float wrap = sdRoundBox(p - vec2(0.02 * sin(uTime * 9.0), -0.08), vec2(0.36, 0.2), 0.12);
        vec3 cc = mix(vec3(0.75, 0.08, 0.15), vec3(0.45, 0.02, 0.12), 0.5 + 0.5 * sin(p.x * 30.0 + p.y * 14.0));
        over(acc, cc, fill(wrap) * uCape.x * smoothstep(0.3, 0.8, uCape.z));
    }

    // ---- Props held in front ----
    if (uProp.w > 0.01) {
        if (uProp.x < 1.5) {
            // "EVIL PLAN" scroll that splits down the middle.
            for (int s = 0; s < 2; s++) {
                float side = s == 0 ? -1.0 : 1.0;
                vec2 q = (p - vec2(0.0, -0.36)) / 0.85;
                q.x -= side * uProp.y * 0.12; q.y += uProp.y * uProp.y * 0.28;
                q = rot(q, side * uProp.y * 0.7);
                float half_ = max(sdRoundBox(q, vec2(0.2, 0.15), 0.01), -q.x * side - 0.003 * sin(q.y * 70.0) * 2.0);
                vec3 pc = vec3(0.96, 0.9, 0.74) * (0.9 + 0.1 * vnoise(q * 30.0));
                over(acc, pc, fill(half_) * uProp.w);
                float inP = fill(half_ + 0.012) * uProp.w;
                // Doodles: a skull, an arrow, squiggles.
                float skull = min(length(q - vec2(-0.1, 0.04)) - 0.04, sdRoundBox(q - vec2(-0.1, -0.005), vec2(0.022, 0.02), 0.005));
                over(acc, vec3(0.2, 0.1, 0.12), fill(abs(skull) - 0.004) * inP);
                over(acc, vec3(0.2, 0.1, 0.12), fill(min(length(q - vec2(-0.115, 0.045)), length(q - vec2(-0.085, 0.045))) - 0.008) * inP);
                for (int k = 0; k < 3; k++) {
                    float ln = abs(q.y - 0.07 + float(k) * 0.045 - 0.006 * sin(q.x * 90.0 + float(k))) - 0.004;
                    over(acc, vec3(0.6, 0.1, 0.15), fill(max(ln, max(-q.x - 0.02, q.x - 0.16))) * inP);
                }
                over(acc, vec3(0.7, 0.1, 0.15), fill(min(sdSeg(q, vec2(-0.14, -0.08), vec2(0.12, -0.08)), min(sdSeg(q, vec2(0.12, -0.08), vec2(0.09, -0.06)), sdSeg(q, vec2(0.12, -0.08), vec2(0.09, -0.1)))) - 0.005) * inP);
                float roll = sdRoundBox(q - vec2(0.0, 0.15), vec2(0.215, 0.018), 0.018);
                over(acc, vec3(0.8, 0.7, 0.5), fill(max(roll, -q.x * side)) * uProp.w);
            }
        } else if (uProp.x < 2.5) {
            // Fireball over the right hand: grows proudly, fizzles into a sad puff.
            vec2 q = p - vec2(0.34, 0.12 + 0.02 * sin(uTime * 6.0));
            float r = 0.13 * uProp.y * (1.0 - 0.85 * uProp.z);
            float flick = vnoise(vec2(atan(q.y, q.x) * 3.0, uTime * 6.0)) * 0.04 * uProp.y;
            float fire = length(q * vec2(1.0, 0.85) - vec2(0.0, 0.02)) - r - flick * step(0.0, q.y);
            vec3 fc = mix(vec3(1.0, 0.95, 0.5), vec3(1.0, 0.3, 0.05), smoothstep(-r, 0.0, fire));
            fc = mix(fc, vec3(0.4), uProp.z);
            over(acc, fc, fill(fire) * uProp.w);
            glowAdd(acc, vec3(1.0, 0.5, 0.1), exp(-max(fire, 0.0) * 12.0) * 0.6 * uProp.y * (1.0 - uProp.z) * uProp.w);
        } else if (uProp.x < 3.5) {
            // Secret teddy.
            vec2 q = p - vec2(0.0, -0.36);
            vec3 tc = vec3(0.8, 0.58, 0.38);
            float teddy = min(min(sdEllipse(q, vec2(0.075, 0.085)), length(q - vec2(0.0, 0.1)) - 0.06), min(length(q - vec2(-0.05, 0.15)) - 0.024, length(q - vec2(0.05, 0.15)) - 0.024));
            over(acc, tc * (0.8 + 0.3 * smoothstep(-0.05, 0.1, q.y - q.x * 0.3)), fill(teddy) * uProp.w);
            over(acc, vec3(0.95, 0.85, 0.7), fill(sdEllipse(q - vec2(0.0, 0.085), vec2(0.028, 0.02))) * uProp.w);
            over(acc, vec3(0.1), fill(min(min(length(q - vec2(-0.022, 0.115)), length(q - vec2(0.022, 0.115))) - 0.007, length(q - vec2(0.0, 0.092)) - 0.007)) * uProp.w);
            over(acc, vec3(1.0, 0.4, 0.5), fill(sdHeart((q - vec2(0.0, -0.02)) / 0.035 + vec2(0.0, 0.5)) * 0.035) * uProp.w);
        } else {
            // Lightning in a jar: it fills with the battery and zaps.
            vec2 q = p - vec2(0.0, -0.36);
            float jar = sdRoundBox(q, vec2(0.085, 0.105), 0.03);
            float neck = sdRoundBox(q - vec2(0.0, 0.115), vec2(0.06, 0.018), 0.008);
            float beat = 0.6 + 0.4 * sin(uTime * 4.0);
            glowAdd(acc, uGlow, exp(-max(jar, 0.0) * 14.0) * (0.35 + 0.5 * uProp.z) * uProp.w);
            over(acc, vec3(0.05, 0.08, 0.12), fill(jar) * uProp.w * 0.85);
            float lvl = -0.1 + 0.2 * uProp.y;
            over(acc, uGlow * (0.45 + 0.2 * beat), fill(max(jar + 0.012, q.y - lvl - 0.006 * sin(q.x * 40.0 + uTime * 5.0))) * uProp.w);
            float bolt = sdBolt(q / 0.11) * 0.11;
            over(acc, mix(uGlow, vec3(1.0), 0.5 + 0.3 * beat), fill(bolt) * uProp.w);
            glowAdd(acc, uGlow, exp(-max(bolt, 0.0) * 50.0) * 0.5 * beat * uProp.w);
            over(acc, vec3(0.7, 0.85, 1.0), fill(abs(jar) - 0.005) * uProp.w * 0.8);
            over(acc, vec3(1.0), fill(sdCapsule(q, vec2(-0.055, 0.06), vec2(-0.055, -0.02), 0.007, 0.004)) * uProp.w * 0.6);
            over(acc, vec3(0.6, 0.45, 0.3), fill(neck) * uProp.w);
            // Zap arcs.
            if (uProp.z > 0.05) {
                for (int k = 0; k < 4; k++) {
                    float a = float(k) * 1.57 + uTime * 9.0;
                    vec2 e = vec2(cos(a), sin(a)) * 0.3;
                    float arc = sdSeg(q + 0.012 * sin(q.yx * 60.0 + uTime * 40.0), vec2(cos(a), sin(a)) * 0.1, e) - 0.004;
                    glowAdd(acc, mix(uGlow, vec3(1.0), 0.6), fill(arc) * uProp.z * uProp.w);
                }
            }
        }
    }
    }

    // ---- Effects ----
    for (int i = 0; i < 6; i++) {
        vec4 fx = uFx[i];
        if (fx.w < 0.5) continue;
        vec2 q = sp - fx.xy;
        float life = fx.z;
        float fade = smoothstep(0.0, 0.1, life) * smoothstep(1.0, 0.7, life);
        if (fx.w < 1.5) {
            float r = 0.035 + 0.06 * life;
            float sm = min(min(length(q) - r, length(q - vec2(r * 0.8, r * 0.3)) - r * 0.7), length(q - vec2(-r * 0.7, r * 0.4)) - r * 0.6);
            over(acc, vec3(0.6, 0.58, 0.65), fill(sm) * fade * 0.7);
        } else if (fx.w < 2.5) {
            float a = atan(q.y, q.x) + life * 6.0, r = length(q);
            float star = r - 0.03 - 0.014 * cos(a * 5.0);
            over(acc, vec3(1.0, 0.9, 0.35), fill(star) * fade);
        } else if (fx.w < 3.5) {
            float drop = min(length(q) - 0.016, sdTri(q, vec2(-0.014, 0.006), vec2(0.014, 0.006), vec2(0.0, 0.04)));
            over(acc, vec3(0.6, 0.85, 1.0), fill(drop) * fade);
        } else if (fx.w < 4.5) {
            float bar = sdRoundBox(q - vec2(0.0, 0.035), vec2(0.01, 0.035), 0.008);
            float dotv = length(q + vec2(0.0, 0.025)) - 0.011;
            over(acc, vec3(1.0, 0.85, 0.3), fill(min(bar, dotv)) * fade);
        } else if (fx.w < 5.5) {
            float head = sdEllipse(rot(q, 0.4), vec2(0.022, 0.016));
            float stem = sdSeg(q, vec2(0.019, 0.005), vec2(0.019, 0.07)) - 0.004;
            over(acc, mix(uRim, vec3(1.0), 0.4), fill(min(head, stem)) * fade);
        } else if (fx.w < 6.5) {
            float r = 0.05 * (1.0 - abs(life - 0.5));
            float st = min(sdSeg(q, vec2(-r, 0.0), vec2(r, 0.0)), sdSeg(q, vec2(0.0, -r), vec2(0.0, r))) - 0.004;
            glowAdd(acc, mix(uGlow, vec3(1.0), 0.5), (fill(st) + exp(-length(q) * 60.0) * 0.6) * fade);
        } else if (fx.w < 7.5) {
            over(acc, vec3(1.0, 0.4, 0.6), fill(sdHeart(q / 0.05 + vec2(0.0, 0.5)) * 0.05) * fade);
        } else {
            vec2 z = q / (0.03 + life * 0.02);
            float dz = min(min(sdSeg(z, vec2(-0.5, 0.5), vec2(0.5, 0.5)), sdSeg(z, vec2(0.5, 0.5), vec2(-0.5, -0.5))), sdSeg(z, vec2(-0.5, -0.5), vec2(0.5, -0.5)));
            over(acc, vec3(0.8, 0.85, 1.0), fill(dz * (0.03 + life * 0.02) - 0.004) * fade * 0.8);
        }
    }

    // Battery icon while charging.
    if (uBatt > 0.01) {
        vec2 q = sp - vec2(0.0, 0.62);
        float frame = abs(sdRoundBox(q, vec2(0.15, 0.06), 0.022)) - 0.006;
        float tipb = sdRoundBox(q - vec2(0.17, 0.0), vec2(0.011, 0.022), 0.005);
        float bw = 0.12 * uProp.y;
        float bar = sdRoundBox(q - vec2(-0.125 + bw, 0.0), vec2(bw, 0.036), 0.01);
        glowAdd(acc, uGlow, (fill(min(frame, tipb)) + exp(-max(min(frame, tipb), 0.0) * 60.0) * 0.35) * uBatt);
        over(acc, uGlow * 0.8, fill(bar) * uBatt * 0.85);
    }

    acc.rgb *= mix(1.0, 0.55, uDim);
    gl_FragColor = vec4(acc.rgb, clamp(acc.a, 0.0, 1.0));
}
