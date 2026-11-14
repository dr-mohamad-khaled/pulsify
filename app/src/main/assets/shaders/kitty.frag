// Pulsify — Kitty, the grey-and-white kitten. GLSL ES 1.00. Shares mochi.vert.
// Character space: y up, paws on the floor at y = -0.55, sitting height about 1.0. Slim build.
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
uniform float uShear;      // butt wiggle (bottom moves, head steady)
uniform vec2  uLook;
uniform float uTilt;
uniform float uHeadY;
uniform float uLoaf;       // 0 sitting .. 1 loaf
uniform float uCrouch;     // 0 .. 1 hunting crouch
uniform float uEyeOpen;
uniform float uEyeMode;    // 0 open, 1 happy ^^, 2 slow blink, 3 asleep, 4 startled
uniform float uPupil;      // 0 slit .. 1 round
uniform float uMouth;      // 0 w, 1 meow, 2 yawn, 3 tongue (grooming)
uniform vec2  uEars;       // swivel left, right (radians)
uniform float uEarsDown;   // airplane ears 0..1
uniform vec4  uTail;       // base angle, curl, sway phase, sway amount
uniform float uPuff;       // startled fur 0..1
uniform vec2  uPawL;       // raise angle, reach
uniform vec2  uPawR;
uniform vec3  uFur;
uniform vec3  uWhite;
uniform vec3  uStripe;
uniform vec3  uEye;
uniform float uPoints;     // siamese dark points 0..1
uniform vec3  uRim;
uniform vec3  uGlow;
uniform float uEyeGlow;    // night eye shine
uniform vec4  uPad;        // visible, level, -, -
uniform vec4  uBox;        // visible, -, -, -
uniform vec4  uCup;        // x, y (sp space), rotation, visible
uniform vec4  uCuke;       // x, y, -, visible
uniform vec4  uDot;        // x, y, -, visible
uniform vec4  uFx[6];      // x, y, life, kind: 1 heart, 2 sparkle, 3 purr, 4 note, 5 zzz, 6 "!", 7 "?"
uniform float uBattIcon;
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
float fill(float d) { return clamp(0.5 - d / (uPx * 1.2), 0.0, 1.0); }
float furEdge(float d, vec2 p, float amt) {
    if (abs(d) > 0.05) return d;
    float n = vnoise(p * 70.0) * 0.6 + vnoise(p * 150.0) * 0.4;
    return d + (n - 0.45) * (0.016 + 0.012 * uPuff) * amt * smoothstep(0.05, 0.0, abs(d));
}
float fillFur(float d) { return clamp(0.5 - d / (uPx * 3.2), 0.0, 1.0); }
// Soft fluffy outline: big lazy tufts plus a little fine fuzz.
float fluff(float d, vec2 p, float amt) {
    if (abs(d) > 0.06) return d;
    // Two rotated octaves of smooth noise: soft tufts, no grid.
    vec2 r1 = vec2(p.x * 0.8 - p.y * 0.6, p.x * 0.6 + p.y * 0.8);
    float n = vnoise(r1 * 17.0) * 0.6 + vnoise(vec2(r1.y, -r1.x) * 41.0 + 3.7) * 0.4;
    return d + (n - 0.5) * (0.016 + 0.02 * uPuff) * amt * smoothstep(0.06, 0.0, abs(d));
}

// Soft "3D render" lighting on a rounded part. q = position inside the part, -1..1.
vec3 lit(vec3 base, vec2 q, vec2 p) {
    float t = clamp(1.0 - dot(q, q), 0.0, 1.0);
    vec3 n = normalize(vec3(q * 0.95, sqrt(t) + 0.3));
    vec3 L = normalize(vec3(-0.42, 0.62, 0.72));
    float ndl = dot(n, L);
    float diff = smoothstep(-0.35, 0.85, ndl);
    // Shadows lean cool and saturated instead of grey.
    vec3 shadow = base * vec3(0.5, 0.5, 0.72);
    vec3 col = mix(shadow, base * 1.04, diff);
    // Broad soft sheen where the light hits.
    col += vec3(1.0, 0.98, 0.95) * pow(max(ndl, 0.0), 6.0) * 0.07;
    // Gentle fur clumps, no streaks.
    col *= 0.975 + 0.045 * vnoise(vec2(p.x * 0.8 - p.y * 0.6, p.x * 0.6 + p.y * 0.8) * 24.0);
    // Rim light wraps the far side.
    float rim = pow(1.0 - n.z, 2.6) * smoothstep(-0.5, 0.7, q.x * 0.7 - q.y * 0.25 + 0.35);
    col += uRim * rim * 0.55;
    return col;
}
vec3 shade(vec3 base, vec2 p, vec2 c, vec2 r, vec2 dir) { return lit(base, (p - c) / r, p); }
float softStripe(vec2 p, vec2 dir, float freq, float seed) {
    float s = sin(dot(p, dir) * freq + vnoise(p * 7.0 + seed) * 2.0);
    return smoothstep(0.35, 0.9, s);
}
float sdBolt(vec2 q) {
    return min(sdTri(q, vec2(0.18, 0.5), vec2(-0.32, -0.06), vec2(0.06, -0.06)), sdTri(q, vec2(-0.18, -0.5), vec2(0.32, 0.06), vec2(-0.06, 0.06)));
}


// ---------- 2D vector style ----------
// Every part is a clean shape: a dark outline (a slightly grown copy), a flat fill, a cel
// shadow on the lower right (the part minus a copy nudged up-left) and a soft highlight.
float aa(float d) { return clamp(0.5 - d / (uPx * 1.1), 0.0, 1.0); }
vec3 inkOf(vec3 c) { return c * vec3(0.38, 0.32, 0.4) + vec3(0.03, 0.02, 0.05); }
const float LINE = 0.0105;
void part(inout vec4 acc, float d, float dShift, vec3 base, float shadeAmt) {
    over(acc, inkOf(base), aa(d - LINE));
    vec3 c = base;
    float sh = aa(d) * (1.0 - aa(dShift));
    c = mix(c, base * vec3(0.78, 0.74, 0.86), sh * shadeAmt);
    over(acc, c, aa(d));
}
void rimHi(inout vec4 acc, float d, float dHi, vec3 base) {
    over(acc, mix(base, vec3(1.0), 0.28), aa(d + 0.006) * (1.0 - aa(dHi)) * 0.7);
}
// paper grain so the flat colour feels printed, not plastic
vec3 grain(vec3 c, vec2 p) { return c * (0.985 + 0.03 * vnoise(p * 140.0)); }

vec2 cr(vec2 a, vec2 b, vec2 c, vec2 d, float t) {
    float t2 = t * t, t3 = t2 * t;
    return 0.5 * ((2.0 * b) + (-a + c) * t + (2.0 * a - 5.0 * b + 4.0 * c - d) * t2 + (-a + 3.0 * b - 3.0 * c + d) * t3);
}
vec2 tailCP(int i, float wrap) {
    vec2 up, wr;
    if (i == 0) { up = vec2(0.17, -0.45); wr = vec2(0.22, -0.47); }
    else if (i == 1) { up = vec2(0.33, -0.38); wr = vec2(0.33, -0.535); }
    else if (i == 2) { up = vec2(0.42, -0.14); wr = vec2(0.06, -0.555); }
    else if (i == 3) { up = vec2(0.41, 0.1); wr = vec2(-0.19, -0.53); }
    else { up = vec2(0.31, 0.2); wr = vec2(-0.27, -0.44); }
    return mix(up, wr, wrap);
}
float tailD(vec2 p, out float tt, vec2 shift) {
    float wrap = smoothstep(0.2, 1.6, uTail.x);
    vec2 c0 = tailCP(0, wrap), c1 = tailCP(1, wrap), c2 = tailCP(2, wrap), c3 = tailCP(3, wrap), c4 = tailCP(4, wrap);
    float best = 1e5; tt = 0.0;
    vec2 prev = c0;
    for (int i = 1; i <= 12; i++) {
        float t = float(i) / 12.0;
        float seg = t * 4.0;
        vec2 q;
        if (seg <= 1.0) q = cr(c0 * 2.0 - c1, c0, c1, c2, seg);
        else if (seg <= 2.0) q = cr(c0, c1, c2, c3, seg - 1.0);
        else if (seg <= 3.0) q = cr(c1, c2, c3, c4, seg - 2.0);
        else q = cr(c2, c3, c4, c4 * 2.0 - c3, seg - 3.0);
        q += vec2(1.0 - wrap, wrap) * uTail.w * sin(uTail.z - t * 3.0) * t * 0.35;
        float th = (0.05 - 0.004 * t) * (1.0 + 0.8 * uPuff);
        float d = sdCapsule(p - shift, prev, q, th, th);
        if (d < best) { best = d; tt = (float(i - 1) + clamp(dot(p - prev, q - prev) / max(dot(q - prev, q - prev), 1e-5), 0.0, 1.0)) / 12.0; }
        prev = q;
    }
    return best;
}

void drawTail(inout vec4 acc, vec2 p, vec3 fur, vec3 dark, vec3 white) {
    float tt;
    float d = tailD(p, tt, vec2(0.0));
    if (d > 0.03) return;
    float tt2;
    float ds = tailD(p, tt2, vec2(-0.016, 0.016));
    part(acc, d, ds, grain(fur, p), 1.0);
    // a dark tip with a clean edge, two soft rings
    float tipMask = smoothstep(0.84, 0.845, tt) * aa(d);
    vec3 tipC = mix(dark, white, uPoints > 0.5 ? 0.0 : 0.0);
    over(acc, mix(tipC, tipC * 0.8, aa(d) * (1.0 - aa(ds))), tipMask * (1.0 - uPoints * 0.0));
    float rings = (step(0.52, tt) - step(0.58, tt) + step(0.67, tt) - step(0.73, tt)) * (1.0 - uPoints);
    over(acc, mix(fur, dark, 0.55), rings * aa(d + 0.002) * 0.85);
    if (uPoints > 0.5) over(acc, dark, smoothstep(0.25, 0.6, tt) * aa(d));
}

void main() {
    vec2 p = (vUv - uPos) / uSize;
    vec2 sp = p;
    p.y = (p.y + 0.55) / uSquash.y - 0.55;
    p.x = p.x / uSquash.x;
    p.x -= uShear * (1.0 - clamp((p.y + 0.55) / 0.5, 0.0, 1.0));

    vec4 acc = vec4(0.0);
    float loaf = uLoaf, crouch = uCrouch;
    vec3 fur = uFur, white = uWhite;
    vec3 dark = mix(uStripe, uStripe * 0.8, uPoints);
    float breath = 0.006 * sin(uTime * 1.6);
    float wrap = smoothstep(0.2, 1.6, uTail.x);
    float marks = (1.0 - uPoints);
    vec2 SH = vec2(-0.02, 0.02);   // cel shadow offset

    // ---- Soft floor shadow ----
    {
        vec2 q = (sp - vec2(0.03, -0.56)) / vec2(mix(0.34, 0.44, loaf), 0.04);
        over(acc, vec3(0.0), exp(-dot(q, q) * 1.8) * 0.4);
    }

    // ---- Milk dish (charging): the bowl sits to the left, the straw is drawn last ----
    vec2 bowlC = vec2(-0.44, -0.5);
    if (uPad.x > 0.01) {
        vec2 q = sp - bowlC;
        over(acc, vec3(0.0), exp(-dot((q + vec2(0.0, 0.06)) / vec2(0.2, 0.03), (q + vec2(0.0, 0.06)) / vec2(0.2, 0.03)) * 1.5) * 0.35 * uPad.x);
        float body = max(sdEllipse(q - vec2(0.0, 0.02), vec2(0.17, 0.1)), q.y - 0.02);
        vec3 cer = mix(vec3(0.42, 0.62, 1.0), vec3(0.8, 0.9, 1.0), smoothstep(-0.08, 0.02, q.y));
        cer = mix(cer * vec3(0.6, 0.62, 0.85), cer, smoothstep(0.17, -0.1, q.x));
        over(acc, cer, fill(body) * uPad.x);
        // Paw print on the front.
        float paw = min(length(q - vec2(0.0, -0.035)) - 0.02, min(length(q - vec2(-0.024, -0.008)) - 0.009, min(length(q - vec2(0.0, 0.0)) - 0.009, length(q - vec2(0.024, -0.008)) - 0.009)));
        over(acc, vec3(1.0, 0.6, 0.72), fill(paw) * uPad.x * 0.9);
        // Rim and the milk, which glows softly while it charges.
        float rimD = sdEllipse(q - vec2(0.0, 0.02), vec2(0.17, 0.035));
        over(acc, vec3(0.97, 0.98, 1.0), fill(rimD) * uPad.x);
        float milk = sdEllipse(q - vec2(0.0, 0.02), vec2(0.15, 0.026));
        vec3 mcol = mix(vec3(1.0), uGlow, 0.07 + 0.05 * sin(uTime * 3.0));
        over(acc, mcol, fill(milk) * uPad.x);
        float ripple = abs(length((q - vec2(0.03, 0.02)) / vec2(1.0, 0.2)) - fract(uTime * 0.6) * 0.1) - 0.004;
        over(acc, vec3(0.8, 0.9, 1.0), fill(ripple) * fill(milk) * 0.6 * uPad.x);
        over(acc, uGlow, exp(-max(milk, 0.0) * 30.0) * 0.22 * uPad.x);
    }




    if (wrap < 0.55) drawTail(acc, p, fur, dark, white);

    // ---- Body: one soft bean, sitting, slightly bottom-heavy ----
    vec2 bc = vec2(0.0, mix(-0.3, -0.42, loaf) - crouch * 0.05);
    vec2 br = vec2(mix(0.265, 0.37, loaf), mix(0.24, 0.14, loaf) + breath) * (1.0 + 0.1 * uPuff);
    vec2 hp = vec2(0.0, mix(0.04, -0.17, loaf) + uHeadY - crouch * 0.12);
    float body = smin(sdEllipse(p - bc, br), sdEllipse(p - vec2(0.0, -0.45), vec2(br.x * 1.08, 0.1)), 0.08);
    float bodyS = smin(sdEllipse(p - SH - bc, br), sdEllipse(p - SH - vec2(0.0, -0.45), vec2(br.x * 1.08, 0.1)), 0.08);
    if (body < 0.05) {
        part(acc, body, bodyS, grain(fur, p), 1.0);
        rimHi(acc, body, sdEllipse(p + SH * 0.5 - bc, br), fur);
        // belly patch with a soft wavy top edge
        float belly = sdEllipse(p - bc - vec2(0.0, -0.035), vec2(br.x * 0.56, br.y * 0.78));
        belly += 0.006 * sin(atan(p.y - bc.y, p.x) * 9.0);
        over(acc, grain(mix(white, white * vec3(0.9, 0.88, 0.95), aa(body) * (1.0 - aa(bodyS))), p), aa(belly) * aa(body + 0.004) * (1.0 - uPoints * 0.5));
        // side stripes: three rounded dashes on each flank
        for (int s = 0; s < 2; s++) {
            float side = s == 0 ? -1.0 : 1.0;
            for (int k = 0; k < 3; k++) {
                float fk = float(k);
                vec2 a = vec2((br.x - 0.005) * side, bc.y + 0.1 - fk * 0.075);
                float st = sdCapsule(p, a, a + vec2(-0.075 * side, -0.012), 0.014, 0.004);
                over(acc, dark, aa(st) * aa(body + 0.004) * marks * 0.9);
            }
        }
    }

    // ---- Front paws: two little bread rolls at the bottom ----
    for (int s = 0; s < 2; s++) {
        float side = s == 0 ? -1.0 : 1.0;
        vec2 paw = s == 0 ? uPawL : uPawR;
        if (loaf > 0.85 && paw.x < 0.05) continue;
        vec2 sh = vec2(0.075 * side, mix(-0.3, -0.42, loaf));
        float len = mix(0.215, 0.1, loaf) * paw.y * (1.0 - 0.3 * crouch);
        vec2 dir = rot(vec2(0.0, -1.0), paw.x * side);
        vec2 foot = sh + dir * len;
        if (paw.x > 0.15) {
            // a raised arm
            float arm = sdCapsule(p, sh, foot, 0.045, 0.045);
            float armS = sdCapsule(p - SH * 0.6, sh, foot, 0.045, 0.045);
            part(acc, arm, armS, grain(fur, p), 1.0);
        }
        vec2 tq = rot(p - foot, paw.x * side);
        float pd = sdEllipse(tq - vec2(0.0, -0.006), vec2(0.06, 0.04));
        float pdS = sdEllipse(tq - SH * 0.6 - vec2(0.0, -0.006), vec2(0.06, 0.04));
        part(acc, pd, pdS, grain(mix(white, dark, uPoints), p), 1.0);
        for (int k = 0; k < 2; k++) {
            float tl = sdSeg(tq, vec2((float(k) - 0.5) * 0.03, -0.045), vec2((float(k) - 0.5) * 0.028, -0.026)) - 0.0035;
            over(acc, inkOf(white), aa(tl));
        }
        if (paw.x > 0.9) {
            over(acc, vec3(1.0, 0.62, 0.7), aa(sdEllipse(tq - vec2(0.0, -0.008), vec2(0.022, 0.017))));
            for (int k = 0; k < 3; k++) over(acc, vec3(1.0, 0.62, 0.7), aa(length(tq - vec2((float(k) - 1.0) * 0.026, 0.017 - abs(float(k) - 1.0) * 0.007)) - 0.0095));
        }
    }

    if (wrap >= 0.55) drawTail(acc, p, fur, dark, white);

    // ---- Box (hiding) : front face over the body ----
    if (uBox.x > 0.01) {
        vec2 q = sp - vec2(0.0, -0.35);
        float box = sdRoundBox(q, vec2(0.38, 0.2), 0.012);
        vec3 card = vec3(0.86, 0.66, 0.44) * (0.93 + 0.07 * vnoise(q * vec2(5.0, 50.0)));
        card *= mix(0.72, 1.05, smoothstep(0.38, -0.3, q.x - q.y * 0.4));
        over(acc, card, fill(box) * uBox.x);
        float flapL = sdTri(q, vec2(-0.38, 0.2), vec2(-0.02, 0.2), vec2(-0.5, 0.33));
        float flapR = sdTri(q, vec2(0.38, 0.2), vec2(0.02, 0.2), vec2(0.5, 0.33));
        over(acc, card * 0.82, fill(min(flapL, flapR)) * uBox.x);
        over(acc, vec3(0.55, 0.4, 0.26), fill(abs(q.y - 0.19) - 0.006) * fill(box) * uBox.x);
        over(acc, vec3(0.8, 0.74, 0.6), fill(sdRoundBox(q - vec2(0.0, 0.12), vec2(0.05, 0.08), 0.004)) * uBox.x * 0.7);
        over(acc, vec3(1.0, 0.45, 0.5), fill(sdHeart((q - vec2(-0.2, -0.06)) / 0.06 + vec2(0.0, 0.5)) * 0.06) * uBox.x * 0.8);
    }




    // ---- Head: a wide, soft bread loaf of a head ----
    vec2 turn = vec2(uLook.x * 0.035, uLook.y * 0.022);
    vec2 mouthPos = vec2(0.0, -0.075);
    if (length(p - hp) < 0.6) {
    vec2 h = rot(p - hp, -uTilt);
    vec2 f = h - turn;
    // Ears.
    for (int s = 0; s < 2; s++) {
        float side = s == 0 ? -1.0 : 1.0;
        float sw = (s == 0 ? uEars.x : uEars.y);
        vec2 pivot = vec2(0.165 * side, 0.115);
        vec2 eq = rot(h - pivot, -(sw + uEarsDown * 0.9 * side) - 0.3 * side);
        float tip = 0.15 - 0.06 * uEarsDown;
        float ear = sdTri(eq, vec2(-0.095 * side, -0.03), vec2(0.085 * side, -0.035), vec2(0.0, tip)) - 0.036;
        float earS = sdTri(eq - SH * 0.5, vec2(-0.095 * side, -0.03), vec2(0.085 * side, -0.035), vec2(0.0, tip)) - 0.036;
        part(acc, ear, earS, grain(mix(fur, dark, uPoints), h), 1.0);
        float inner = sdTri(eq, vec2(-0.045 * side, 0.0), vec2(0.04 * side, -0.005), vec2(0.0, tip - 0.06)) - 0.02;
        over(acc, vec3(1.0, 0.7, 0.76), aa(inner));
    }
    float head = smin(sdEllipse(h - vec2(0.0, 0.0), vec2(0.27, 0.2)), sdEllipse(h - vec2(0.0, -0.05), vec2(0.3, 0.15)), 0.08);
    float headS = smin(sdEllipse(h - SH * 0.8, vec2(0.27, 0.2)), sdEllipse(h - SH * 0.8 - vec2(0.0, -0.05), vec2(0.3, 0.15)), 0.08);
    part(acc, head, headS, grain(fur, h), 0.8);
    rimHi(acc, head, smin(sdEllipse(h + SH * 0.5, vec2(0.27, 0.2)), sdEllipse(h + SH * 0.5 - vec2(0.0, -0.05), vec2(0.3, 0.15)), 0.08), fur);
    float face = aa(head + 0.004);
    // Forehead stripes: three rounded wedges.
    for (int k = 0; k < 3; k++) {
        float fk = float(k) - 1.0;
        float st = sdCapsule(h, vec2(fk * 0.045, 0.2), vec2(fk * 0.035, 0.13 - abs(fk) * 0.02), 0.016, 0.006);
        over(acc, dark, aa(st) * face * marks);
    }
    // Cheek stripes.
    for (int s = 0; s < 2; s++) {
        float side = s == 0 ? -1.0 : 1.0;
        for (int k = 0; k < 2; k++) {
            float y = -0.02 - float(k) * 0.045;
            float st = sdCapsule(h, vec2(0.3 * side, y), vec2(0.23 * side, y - 0.005), 0.013, 0.004);
            over(acc, dark, aa(st) * face * marks);
        }
    }
    // Siamese mask.
    over(acc, dark, aa(sdEllipse(f - vec2(0.0, -0.06), vec2(0.15, 0.1))) * face * uPoints * 0.85);
    // Muzzle: a small soft cream cloud.
    float muzzle = smin(sdEllipse(f - vec2(-0.035, -0.078), vec2(0.048, 0.034)), sdEllipse(f - vec2(0.035, -0.078), vec2(0.048, 0.034)), 0.02);
    over(acc, mix(white, white * 0.92, 0.0), aa(muzzle) * face * (1.0 - uPoints * 0.7));

    // Eyes. Relaxed by default: big dark eyes half covered by sleepy lids.
    for (int s = 0; s < 2; s++) {
        float side = s == 0 ? -1.0 : 1.0;
        vec2 ec = f - vec2(0.125 * side, -0.012);
        vec2 look = uLook * vec2(0.01, 0.008);
        if (uEyeMode < 0.5 || uEyeMode > 1.5 && uEyeMode < 2.5 || uEyeMode > 3.5) {
            float open = uEyeOpen;
            if (uEyeMode > 1.5 && uEyeMode < 2.5) open = min(open, 0.45);
            float big = uEyeMode > 3.5 ? 1.18 : 1.0;
            vec2 er = vec2(0.047, 0.052) * big;
            float eye = sdEllipse(ec, er);
            // iris: deep colour with a lighter crescent at the bottom
            vec3 iris = mix(uEye * 0.35 + vec3(0.02), uEye * 0.9 + 0.08, smoothstep(0.01, -0.04, ec.y) * 0.8);
            over(acc, vec3(0.08, 0.06, 0.1), aa(eye));
            over(acc, iris, aa(sdEllipse(ec - look - vec2(0.0, -0.004), er * 0.82)) * aa(eye));
            vec2 pr = vec2(mix(0.008, 0.024, uPupil), 0.03) * big;
            over(acc, vec3(0.04, 0.03, 0.06), aa(sdEllipse(ec - look, pr)) * aa(eye));
            over(acc, vec3(1.0), aa(length(ec - look * 0.5 - vec2(-0.014, 0.018)) - 0.013 * big) * aa(eye));
            over(acc, vec3(1.0), aa(length(ec - look * 0.5 - vec2(0.016, -0.016)) - 0.006 * big) * aa(eye));
            // the lid: a flat shape of fur colour coming down, with a confident lash line
            float lidY = mix(0.06, -0.055, 1.0 - clamp(open * 0.7 + (uEyeMode > 3.5 ? 0.5 : 0.0), 0.0, 1.0));
            float lidE = lidY - 0.05 * side * ec.x - 3.0 * ec.x * ec.x;
            float lid = max(eye - 0.002, -(ec.y - lidE));
            over(acc, mix(fur, dark, uPoints * 0.8), aa(lid) * face);
            float lash = abs(ec.y - lidE) - 0.0065;
            lash = max(lash, eye - 0.004);
            over(acc, vec3(0.1, 0.07, 0.12), aa(lash) * face * step(0.0, lidY - (-0.05)) * step(lidY, 0.046));
            // a little flick at the outer corner
            float flick = sdCapsule(ec, vec2(0.04 * side, lidY - 0.016), vec2(0.06 * side, lidY - 0.008), 0.005, 0.0015);
            over(acc, vec3(0.1, 0.07, 0.12), aa(flick) * face * step(-0.04, lidY));
        } else if (uEyeMode < 1.5) {
            // happy ^^
            float ln = sdArc(ec + vec2(0.0, 0.02), 0.036, 1.2) - 0.0075;
            over(acc, vec3(0.1, 0.07, 0.12), aa(ln) * face);
        } else {
            // asleep: a calm downward curve
            float ln = sdArc(ec * vec2(1.0, -1.0) + vec2(0.0, 0.025), 0.04, 1.0) - 0.0065;
            over(acc, vec3(0.1, 0.07, 0.12), aa(ln) * face);
        }
        // blush
        vec2 bq = (f - vec2(0.18 * side, -0.065)) / vec2(0.045, 0.024);
        over(acc, vec3(1.0, 0.55, 0.62), aa(length(bq) - 1.0) * 0.45 * face * (1.0 - uPoints * 0.5));
        // two tiny whisker dashes on each cheek
        for (int k = 0; k < 2; k++) {
            float y = -0.072 - float(k) * 0.022;
            float wk = sdCapsule(f, vec2(0.235 * side, y), vec2(0.3 * side, y - 0.008 + float(k) * 0.004), 0.0045, 0.003);
            over(acc, inkOf(fur), aa(wk) * 0.85);
        }
    }

    // Nose and mouth.
    {
        vec2 nq = f - vec2(0.0, -0.055);
        float nose = sdTri(nq, vec2(-0.016, 0.008), vec2(0.016, 0.008), vec2(0.0, -0.01)) - 0.006;
        over(acc, mix(vec3(1.0, 0.55, 0.65), vec3(0.32, 0.22, 0.24), uPoints), aa(nose) * face);
        vec2 mc = f - mouthPos;
        vec3 line = vec3(0.12, 0.08, 0.12);
        if (uPad.x > 0.5 && uMouth > 3.5) {
            over(acc, vec3(0.3, 0.08, 0.12), aa(length(mc) - 0.013) * face);
        } else if (uMouth < 0.5) {
            float w1 = sdArc((mc - vec2(-0.016, 0.009)) * vec2(1.0, -1.0), 0.016, 1.3);
            float w2 = sdArc((mc - vec2(0.016, 0.009)) * vec2(1.0, -1.0), 0.016, 1.3);
            over(acc, line, aa(min(w1, w2) - 0.0045) * face);
        } else {
            float o = uMouth < 1.5 ? 0.024 : (uMouth < 2.5 ? 0.05 : 0.018);
            float dm = sdEllipse(mc + vec2(0.0, o * 0.4), vec2(uMouth < 2.5 ? 0.032 : 0.022, o));
            over(acc, line, aa(dm - 0.005) * face);
            over(acc, vec3(0.55, 0.12, 0.2), aa(dm) * face);
            float tongue = sdEllipse(mc + vec2(0.0, o * 0.95 + (uMouth > 2.5 ? 0.012 : 0.0)), vec2(0.019, uMouth > 2.5 ? 0.026 : 0.016));
            over(acc, vec3(1.0, 0.55, 0.62), aa(uMouth > 2.5 ? tongue : max(tongue, dm)) * face);
        }
    }

    }

    // ---- Straw from the milk to the mouth, with a glowing charge badge ----
    if (uPad.x > 0.01) {
        vec2 mo = hp + rot(turn + mouthPos, uTilt);
        vec2 m0 = bowlC + vec2(0.05, 0.03);
        vec2 el = vec2(mix(m0.x, mo.x, 0.35), mo.y - 0.02);
        float s1 = sdCapsule(p, m0, el, 0.017, 0.017);
        float s2 = sdCapsule(p, el, mo, 0.017, 0.015);
        float st = min(s1, s2);
        if (st < 0.12) {
            float along = s1 < s2 ? dot(p - m0, normalize(el - m0)) : length(el - m0) + dot(p - el, normalize(mo - el));
            vec3 sc = mix(vec3(1.0, 0.97, 0.98), vec3(1.0, 0.45, 0.6), step(0.5, fract(along * 14.0 + (s1 < s2 ? p.x : p.y) * 6.0)));
            // Bendy ribs at the elbow.
            sc *= 1.0 - 0.25 * step(0.5, fract(along * 60.0)) * smoothstep(0.05, 0.0, abs(along - length(el - m0)));
            // Milk light pulses travelling up to the mouth.
            float pulse = smoothstep(0.75, 1.0, sin(along * 22.0 - uTime * 7.0));
            sc = mix(sc, uGlow * 1.3, pulse * 0.75);
            sc *= mix(0.75, 1.1, smoothstep(0.017, -0.017, (s1 < s2 ? dot(p - m0, vec2(-normalize(el - m0).y, normalize(el - m0).x)) : p.y - mix(el.y, mo.y, 0.5))));
            over(acc, sc, fill(st) * uPad.x);
            over(acc, uGlow, exp(-max(st, 0.0) * 55.0) * 0.25 * pulse * uPad.x);
            // Badge: a luminous bolt in a ring that fills with the battery.
            vec2 bq = p - mix(m0, el, 0.52);
            float disc = length(bq) - 0.062;
            float beat = 0.6 + 0.4 * sin(uTime * 4.0);
            over(acc, uGlow, exp(-max(disc, 0.0) * 22.0) * 0.5 * beat * uPad.x);
            over(acc, vec3(0.06, 0.07, 0.12), fill(disc) * uPad.x);
            float ang = atan(bq.x, bq.y) / 6.2832 + 0.5;
            float ringD = abs(length(bq) - 0.052) - 0.006;
            over(acc, mix(vec3(0.2, 0.22, 0.3), uGlow * 1.2, step(1.0 - ang, uPad.y)), fill(ringD) * uPad.x);
            float bolt = sdBolt(bq / 0.07) * 0.07;
            over(acc, mix(uGlow, vec3(1.0), 0.45 + 0.3 * beat), fill(bolt) * uPad.x);
            over(acc, uGlow, exp(-max(bolt, 0.0) * 70.0) * 0.5 * beat * uPad.x);
        }
    }

    // ---- Props ----
    if (uCup.w > 0.01) {
        vec2 q = rot(sp - uCup.xy, -uCup.z);
        float mug = sdRoundBox(q, vec2(0.055, 0.065), 0.015);
        float handle = abs(length(q - vec2(0.065, 0.0)) - 0.03) - 0.01;
        vec3 mc = vec3(0.95, 0.5, 0.45);
        over(acc, mc * 0.8, fill(handle) * uCup.w);
        over(acc, mix(mc * 0.75, mc, smoothstep(-0.06, 0.06, -q.x)), fill(mug) * uCup.w);
        over(acc, vec3(1.0), fill(sdHeart((q - vec2(0.0, -0.01)) / 0.03 + vec2(0.0, 0.5)) * 0.03) * uCup.w * 0.9);
    }
    if (uCuke.w > 0.01) {
        vec2 q = sp - uCuke.xy;
        float c = sdRoundBox(rot(q, 0.1), vec2(0.16, 0.035), 0.035);
        vec3 g = mix(vec3(0.12, 0.42, 0.18), vec3(0.3, 0.65, 0.3), smoothstep(-0.03, 0.03, q.y));
        g *= 0.85 + 0.15 * step(0.5, fract(q.x * 30.0 + vnoise(q * 40.0)));
        over(acc, g, fill(c) * uCuke.w);
    }
    if (uDot.w > 0.01) {
        vec2 q = sp - uDot.xy;
        float d = length(q);
        over(acc, vec3(1.0, 0.1, 0.1), (exp(-d * 60.0) * 0.8 + fill(d - 0.012)) * uDot.w);
        over(acc, vec3(1.0, 0.8, 0.8), fill(d - 0.005) * uDot.w);
    }

    // ---- Battery icon ----
    if (uBattIcon > 0.01) {
        vec2 q = sp - vec2(0.0, 0.72);
        float frame = abs(sdRoundBox(q, vec2(0.15, 0.06), 0.022)) - 0.006;
        float tip = sdRoundBox(q - vec2(0.17, 0.0), vec2(0.011, 0.022), 0.005);
        float bw = 0.12 * uPad.y;
        float bar = sdRoundBox(q - vec2(-0.125 + bw, 0.0), vec2(bw, 0.036), 0.01);
        over(acc, uGlow, (fill(min(frame, tip)) + exp(-max(min(frame, tip), 0.0) * 60.0) * 0.35) * uBattIcon);
        over(acc, uGlow * 0.8, fill(bar) * uBattIcon * 0.85);
    }

    // ---- Effects ----
    for (int i = 0; i < 6; i++) {
        vec4 fx = uFx[i];
        if (fx.w < 0.5) continue;
        vec2 q = sp - fx.xy;
        float life = fx.z;
        float fade = smoothstep(0.0, 0.12, life) * smoothstep(1.0, 0.7, life);
        if (fx.w < 1.5) {
            over(acc, vec3(1.0, 0.4, 0.6), fill(sdHeart(q / 0.045 + vec2(0.0, 0.5)) * 0.045) * fade);
        } else if (fx.w < 2.5) {
            float r = 0.04 * (1.0 - abs(life - 0.5));
            float st = min(sdSeg(q, vec2(-r, 0.0), vec2(r, 0.0)), sdSeg(q, vec2(0.0, -r), vec2(0.0, r))) - 0.004;
            over(acc, vec3(1.0, 0.95, 0.75), (fill(st) + exp(-length(q) * 70.0) * 0.6) * fade);
        } else if (fx.w < 3.5) {
            // Purr: soft arcs rippling out from the chest.
            float rr = 0.06 + life * 0.25;
            float arc = sdArc(q, rr, 0.7) - 0.005;
            over(acc, mix(uRim, vec3(1.0), 0.5), fill(arc) * fade * 0.7);
        } else if (fx.w < 4.5) {
            float head = sdEllipse(rot(q, 0.4), vec2(0.02, 0.015));
            float stem = sdSeg(q, vec2(0.017, 0.005), vec2(0.017, 0.065)) - 0.004;
            over(acc, mix(uRim, vec3(1.0), 0.4), fill(min(head, stem)) * fade);
        } else if (fx.w < 5.5) {
            vec2 z = q / (0.03 + life * 0.02);
            float dz = min(min(sdSeg(z, vec2(-0.5, 0.5), vec2(0.5, 0.5)), sdSeg(z, vec2(0.5, 0.5), vec2(-0.5, -0.5))), sdSeg(z, vec2(-0.5, -0.5), vec2(0.5, -0.5)));
            over(acc, vec3(0.8, 0.85, 1.0), fill(dz * (0.03 + life * 0.02) - 0.004) * fade * 0.8);
        } else if (fx.w < 6.5) {
            float bar = sdRoundBox(q - vec2(0.0, 0.03), vec2(0.008, 0.03), 0.006);
            float dotv = length(q + vec2(0.0, 0.02)) - 0.009;
            over(acc, vec3(1.0, 0.85, 0.3), fill(min(bar, dotv)) * fade);
        } else {
            float hook = max(abs(length(q - vec2(0.0, 0.035)) - 0.02) - 0.006, -(q.y - 0.03) * 0.0 - min(q.x + 0.02, 0.0) * 0.0);
            hook = max(hook, -(q.x + q.y * 0.2) - 0.035);
            float stem = sdSeg(q, vec2(0.0, 0.015), vec2(0.0, -0.005)) - 0.006;
            float dotv = length(q + vec2(0.0, 0.025)) - 0.008;
            over(acc, vec3(0.75, 0.85, 1.0), fill(min(min(hook, stem), dotv)) * fade);
        }
    }

    acc.rgb *= mix(1.0, 0.55, uDim);
    gl_FragColor = acc;
}
