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

void main() {
    vec2 p = (vUv - uPos) / uSize;
    vec2 sp = p;
    p.y = (p.y + 0.55) / uSquash.y - 0.55;
    p.x = p.x / uSquash.x;
    // Butt wiggle: the bottom shifts, the head stays put.
    p.x -= uShear * (1.0 - clamp((p.y + 0.55) / 0.5, 0.0, 1.0));

    vec4 acc = vec4(0.0);
    float loaf = uLoaf, crouch = uCrouch;
    vec3 fur = uFur, white = uWhite;
    vec3 dark = mix(uStripe, uStripe * 0.6, uPoints);
    float breath = 0.006 * sin(uTime * 1.9);

    // ---- Soft contact shadow on the floor ----
    {
        vec2 q = (sp - vec2(0.02, -0.565)) / vec2(mix(0.34, 0.44, loaf), 0.05);
        over(acc, vec3(0.0), exp(-dot(q, q) * 1.6) * 0.42);
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

    // ---- Tail: thick and fluffy, curling up the right side ----
    {
        vec2 a = vec2(mix(0.17, 0.27, loaf), -0.49);
        float ang = uTail.x;
        float best = 1e5; float bestT = 0.0;
        for (int i = 0; i < 7; i++) {
            float fi = float(i);
            float aa = ang + uTail.y * fi + uTail.w * sin(uTail.z - fi * 0.55);
            vec2 b = a + vec2(sin(aa), cos(aa)) * 0.075;
            float th = (0.05 - 0.002 * fi) * (1.0 + 1.2 * uPuff);
            float d = sdCapsule(p, a, b, th, th * (fi > 5.5 ? 0.75 : 1.0));
            if (d < best) { best = d; bestT = fi + clamp(dot(p - a, b - a) / dot(b - a, b - a), 0.0, 1.0); }
            a = b;
        }
        if (best < 0.08) {
            float d = fluff(best, p, 1.3 + uPuff * 2.0);
            float band = smoothstep(0.25, 0.75, 0.5 + 0.5 * sin(bestT * 3.3 + 1.0));
            vec3 tc = mix(fur, dark, band * 0.75 * (1.0 - uPoints));
            tc = mix(tc, dark, max(smoothstep(5.2, 6.6, bestT) * 0.85, uPoints * smoothstep(1.0, 4.0, bestT)));
            vec2 q = vec2(clamp(-best / 0.05, 0.0, 1.0) * -0.6 + 0.3, 0.2);
            over(acc, lit(tc, q, p), fillFur(d));
        }
    }

    // ---- Hind feet peeking out at the sides ----
    for (int s = 0; s < 2; s++) {
        float side = s == 0 ? -1.0 : 1.0;
        vec2 c = vec2(mix(0.17, 0.26, loaf) * side, -0.515);
        float ft = sdEllipse(p - c, vec2(0.075, 0.042));
        over(acc, lit(mix(white, dark, uPoints), (p - c) / vec2(0.09, 0.06), p), fillFur(fluff(ft, p, 0.5)) * (1.0 - 0.9 * loaf));
    }

    // ---- Body: small pear with soft haunches ----
    vec2 bc = vec2(0.0, mix(-0.31, -0.42, loaf) - crouch * 0.05);
    vec2 br = vec2(mix(0.185, 0.34, loaf), mix(0.235, 0.14, loaf) + breath) * (1.0 + 0.12 * uPuff);
    vec2 hc2 = vec2(0.0, mix(-0.44, -0.45, loaf) + crouch * 0.06);
    vec2 hr2 = vec2(mix(0.25, 0.36, loaf), mix(0.115, 0.11, loaf)) * (1.0 + 0.12 * uPuff);
    float body = smin(sdEllipse(p - bc, br), sdEllipse(p - hc2, hr2), 0.1);
    vec2 hp = vec2(0.0, mix(0.07, -0.16, loaf) + uHeadY - crouch * 0.14);
    if (body < 0.1) {
        float d = fluff(body, p, 1.0);
        vec2 q = (p - vec2(0.0, bc.y - 0.03)) / vec2(br.x * 1.45, br.y * 1.35);
        vec3 col = lit(fur, q, p);
        float st = softStripe(vec2(abs(p.x), p.y), vec2(0.3, 1.0), 30.0, 1.0) * smoothstep(0.07, 0.17, abs(p.x)) * (1.0 - uPoints);
        col = mix(col, lit(dark, q, p), st * 0.6);
        // Shadow under the chin grounds the head on the body.
        col *= 1.0 - 0.38 * smoothstep(0.2, 0.0, length((p - hp - vec2(0.0, -0.24)) / vec2(1.3, 0.8)));
        over(acc, col, fillFur(d));
        // Fluffy white bib.
        float bib = sdEllipse(p - bc - vec2(0.0, br.y * 0.2), vec2(br.x * 0.6, br.y * 0.72));
        vec3 wc = lit(white, q, p) * (1.0 - 0.3 * smoothstep(0.2, 0.0, length((p - hp - vec2(0.0, -0.24)) / vec2(1.3, 0.8))));
        over(acc, wc, fillFur(fluff(bib, p * 1.3, 2.2)) * fill(body) * (1.0 - uPoints * 0.6));
    }

    // ---- Front legs: short and chubby with white mittens ----
    for (int s = 0; s < 2; s++) {
        float side = s == 0 ? -1.0 : 1.0;
        vec2 paw = s == 0 ? uPawL : uPawR;
        vec2 sh = vec2(0.075 * side, mix(-0.26, -0.4, loaf) - crouch * 0.04);
        float len = mix(0.255, 0.1, loaf) * paw.y * (1.0 - 0.35 * crouch);
        vec2 dir = rot(vec2(0.0, -1.0), paw.x * side);
        vec2 foot = sh + dir * len;
        if (loaf > 0.85 && paw.x < 0.05) continue; // tucked in a loaf
        float leg = sdCapsule(p, sh, foot, 0.052, 0.05);
        vec2 lq = vec2(dot(p - sh, vec2(dir.y, -dir.x)) / 0.07, 0.1);
        float sock = smoothstep(0.55, 0.75, dot(p - sh, dir) / max(len, 0.05));
        over(acc, lit(mix(mix(fur, white, 0.25), mix(white, dark, uPoints), sock), lq, p), fillFur(fluff(leg, p, 0.5)));
        vec2 tq = rot(p - foot, paw.x * side);
        float pd = sdEllipse(tq - vec2(0.0, -0.012), vec2(0.064, 0.044));
        over(acc, lit(mix(white, dark, uPoints), tq / vec2(0.075, 0.055), p), fillFur(fluff(pd, p, 0.35)));
        for (int k = 0; k < 2; k++) {
            float tl = sdSeg(tq, vec2((float(k) - 0.5) * 0.034, -0.05), vec2((float(k) - 0.5) * 0.03, -0.028)) - 0.0028;
            over(acc, vec3(0.62, 0.6, 0.7), fill(tl) * 0.55);
        }
        // Pink beans when a paw is raised toward you.
        if (paw.x > 0.9) {
            over(acc, vec3(1.0, 0.62, 0.72), fill(length(tq - vec2(0.0, -0.016)) - 0.02));
            for (int k = 0; k < 3; k++) over(acc, vec3(1.0, 0.62, 0.72), fill(length(tq - vec2((float(k) - 1.0) * 0.026, 0.014 - abs(float(k) - 1.0) * 0.008)) - 0.0095));
        }
    }

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

    // ---- Head (skipped entirely for pixels far from it) ----
    vec2 turn = vec2(uLook.x * 0.045, uLook.y * 0.028);
    vec2 mouthPos = vec2(0.0, -0.118);
    if (length(p - hp) < 0.72) {
    vec2 h = rot(p - hp, -uTilt);
    float skull = sdEllipse(h - vec2(0.0, 0.03), vec2(0.285, 0.245));
    float cheekL = sdEllipse(h - vec2(-0.2, -0.07), vec2(0.13, 0.1) * (1.0 + 0.1 * uPuff));
    float cheekR = sdEllipse(h - vec2(0.2, -0.07), vec2(0.13, 0.1) * (1.0 + 0.1 * uPuff));
    float head = smin(skull, min(cheekL, cheekR), 0.07);

    // Ears behind the head: rounded, with a pink inside and white tufts.
    for (int s = 0; s < 2; s++) {
        float side = s == 0 ? -1.0 : 1.0;
        float sw = (s == 0 ? uEars.x : uEars.y);
        vec2 pivot = vec2(0.17 * side, 0.19);
        vec2 eq = rot(h - pivot, -(sw + uEarsDown * 0.9 * side) - 0.12 * side);
        float tip = 0.2 - 0.08 * uEarsDown;
        float ear = sdTri(eq, vec2(-0.1 * side, -0.05), vec2(0.085 * side, -0.04), vec2(0.03 * side, tip)) - 0.022;
        float inner = sdTri(eq, vec2(-0.055 * side, -0.02), vec2(0.05 * side, -0.015), vec2(0.026 * side, tip - 0.055)) - 0.01;
        vec2 q = eq / vec2(0.12, 0.2);
        over(acc, lit(mix(fur, dark, max(uPoints, 0.12)), q, h), fillFur(fluff(ear, h, 0.5)));
        vec3 pink = mix(vec3(1.0, 0.62, 0.7), vec3(0.85, 0.4, 0.5), smoothstep(0.1, -0.03, eq.y));
        over(acc, pink, fill(inner) * 0.95);
        for (int k = 0; k < 3; k++) {
            float fk = float(k);
            float t = sdCapsule(eq, vec2((-0.03 + fk * 0.028) * side, -0.03), vec2((0.0 + fk * 0.022) * side, 0.05 - fk * 0.012), 0.008, 0.001);
            over(acc, white, fill(t) * 0.85);
        }
    }
    {
        float d = fluff(head, h, 1.25);
        vec2 q = (h - vec2(0.0, 0.0)) / vec2(0.36, 0.3);
        vec3 col = lit(fur, q, h);
        // Soft tabby marks: forehead "M", cheek swipes.
        if (uPoints < 0.5) {
            float m = min(min(sdCapsule(h - turn * 0.5, vec2(-0.075, 0.13), vec2(-0.055, 0.225), 0.014, 0.008), sdCapsule(h - turn * 0.5, vec2(0.0, 0.14), vec2(0.0, 0.245), 0.015, 0.008)), sdCapsule(h - turn * 0.5, vec2(0.075, 0.13), vec2(0.055, 0.225), 0.014, 0.008));
            col = mix(col, lit(dark, q, h), smoothstep(0.012, -0.012, m) * 0.6);
            for (int s = 0; s < 2; s++) {
                float side = s == 0 ? -1.0 : 1.0;
                for (int k = 0; k < 2; k++) {
                    float y = -0.035 - float(k) * 0.05;
                    float cs = sdCapsule(h, vec2(0.215 * side, y + 0.012), vec2(0.31 * side, y + 0.03), 0.012, 0.004);
                    col = mix(col, lit(dark, q, h), smoothstep(0.012, -0.012, cs) * 0.5);
                }
            }
        }
        // Siamese mask.
        col = mix(col, lit(dark, q, h), uPoints * smoothstep(0.22, 0.06, length((h - vec2(0.0, -0.04)) * vec2(1.0, 1.2))) * 0.9);
        over(acc, col, fillFur(d));
    }
    vec2 f = h - turn;
    float face = fill(head + 0.01);
    vec2 fq = h / vec2(0.36, 0.3);

    // White blaze, muzzle and chin.
    float muzzle = smin(sdEllipse(f - vec2(-0.05, -0.105), vec2(0.07, 0.052)), sdEllipse(f - vec2(0.05, -0.105), vec2(0.07, 0.052)), 0.025);
    muzzle = smin(muzzle, sdCapsule(f, vec2(0.0, -0.08), vec2(0.0, 0.07), 0.035, 0.012), 0.04);
    muzzle = smin(muzzle, sdEllipse(f - vec2(0.0, -0.15), vec2(0.06, 0.035)), 0.03);
    over(acc, lit(mix(white, dark * 1.1, uPoints), fq + vec2(0.0, 0.1), f), fillFur(fluff(muzzle, f, 0.6)) * face);

    // Blush.
    for (int s = 0; s < 2; s++) {
        float side = s == 0 ? -1.0 : 1.0;
        vec2 bq = (f - vec2(0.175 * side, -0.075)) / vec2(0.055, 0.032);
        over(acc, vec3(1.0, 0.5, 0.62), exp(-dot(bq, bq) * 1.3) * 0.42 * face * (1.0 - uPoints));
    }

    // Eyes: big, round and glossy.
    for (int s = 0; s < 2; s++) {
        float side = s == 0 ? -1.0 : 1.0;
        vec2 ec = f - vec2(0.118 * side, 0.0);
        ec = rot(ec, 0.1 * side);
        vec2 look = uLook * vec2(0.014, 0.012);
        if (uEyeMode < 0.5 || uEyeMode > 1.5 && uEyeMode < 2.5 || uEyeMode > 3.5) {
            float open = uEyeOpen;
            if (uEyeMode > 1.5 && uEyeMode < 2.5) open = min(open, 0.4);
            if (uEyeMode > 3.5) open = 1.12;
            vec2 er = vec2(0.074, 0.084 * max(open, 0.06));
            float eye = sdEllipse(ec, er);
            // Dark lash line all around, thicker on top.
            over(acc, vec3(0.07, 0.05, 0.1), fill(eye - 0.007 - 0.006 * smoothstep(-0.02, 0.05, ec.y)) * face);
            // Iris: deep at the top, bright and saturated toward the bottom.
            float ir = length(ec - look * 0.6) / 0.075;
            vec3 iris = mix(uEye * 1.25 + vec3(0.12, 0.16, 0.12), uEye * 0.35, smoothstep(-0.05, 0.06, ec.y));
            iris = mix(iris, uEye * 0.22, smoothstep(0.78, 1.0, ir));
            iris += uEye * uEyeGlow * 0.9;
            over(acc, iris, fill(eye) * face);
            // Radial iris fibres.
            float fib = vnoise(vec2(atan(ec.y - look.y, ec.x - look.x) * 9.0, ir * 3.0));
            over(acc, uEye * 1.5, fill(eye) * face * fib * 0.18 * smoothstep(0.3, 0.7, ir));
            // Pupil: round and huge when excited, a slit when focused.
            vec2 pr = vec2(mix(0.012, 0.047, uPupil), mix(0.06, 0.056, uPupil));
            float pupil = sdEllipse(ec - look, pr);
            over(acc, vec3(0.015, 0.01, 0.03), fill(max(pupil, eye)) * face);
            // Lid shadow across the top of the eye.
            over(acc, vec3(0.03, 0.02, 0.08), fill(eye) * face * smoothstep(0.02, 0.085, ec.y + 0.012) * 0.55);
            if (open > 0.3) {
                float hl = sdEllipse(ec - look * 0.4 - vec2(-0.022, 0.03), vec2(0.021, 0.024));
                float hl2 = length(ec - look * 0.4 - vec2(0.026, -0.03)) - 0.01;
                float hl3 = sdEllipse(ec - vec2(0.0, -0.058), vec2(0.035, 0.012));
                over(acc, vec3(1.0), (fill(max(hl, eye)) + fill(hl2) * 0.85) * face);
                over(acc, mix(uEye, vec3(1.0), 0.6), fill(max(hl3, eye)) * face * 0.35);
            }
            if (uEyeGlow > 0.01) over(acc, uEye * 1.4, exp(-max(eye, 0.0) * 50.0) * uEyeGlow * 0.6 * face);
        } else if (uEyeMode < 1.5) {
            float ln = sdArc(ec + vec2(0.0, 0.02), 0.05, 1.15) - 0.0085;
            over(acc, vec3(0.08, 0.06, 0.12), fill(ln) * face);
        } else {
            float ln = sdArc(ec * vec2(1.0, -1.0) + vec2(0.0, 0.022), 0.052, 1.05) - 0.0075;
            over(acc, vec3(0.08, 0.06, 0.12), fill(ln) * face);
            for (int k = 0; k < 2; k++) {
                float fk = float(k);
                float lash = sdSeg(ec, vec2((0.03 + fk * 0.018) * side, -0.022 + fk * 0.008), vec2((0.045 + fk * 0.02) * side, -0.04 + fk * 0.012)) - 0.004;
                over(acc, vec3(0.08, 0.06, 0.12), fill(lash) * face);
            }
        }
    }

    // Nose and mouth.
    {
        vec2 nq = f - vec2(0.0, -0.075);
        float nose = sdTri(nq, vec2(-0.02, 0.01), vec2(0.02, 0.01), vec2(0.0, -0.012)) - 0.009;
        vec3 nc = mix(vec3(1.0, 0.52, 0.62), vec3(0.3, 0.2, 0.22), uPoints);
        over(acc, nc * mix(0.8, 1.1, smoothstep(-0.02, 0.02, nq.y)), fill(nose) * face);
        over(acc, vec3(1.0), fill(sdEllipse(nq - vec2(-0.006, 0.008), vec2(0.008, 0.004))) * face * 0.7);
        vec2 mc = f - mouthPos;
        vec3 line = vec3(0.3, 0.16, 0.2);
        if (uPad.x > 0.5 && uMouth > 3.5) {
            // Sipping: a tiny round mouth around the straw.
            over(acc, vec3(0.3, 0.08, 0.12), fill(length(mc) - 0.017) * face);
        } else if (uMouth < 0.5) {
            over(acc, line, fill(sdSeg(f, vec2(0.0, -0.092), vec2(0.0, -0.108)) - 0.003) * face);
            float w1 = sdArc((mc - vec2(-0.022, 0.012)) * vec2(1.0, -1.0), 0.022, 1.25);
            float w2 = sdArc((mc - vec2(0.022, 0.012)) * vec2(1.0, -1.0), 0.022, 1.25);
            over(acc, line, fill(min(w1, w2) - 0.0035) * face);
        } else {
            float o = uMouth < 1.5 ? 0.03 : (uMouth < 2.5 ? 0.065 : 0.022);
            float dm = sdEllipse(mc + vec2(0.0, o * 0.5), vec2(uMouth < 2.5 ? 0.042 : 0.026, o));
            over(acc, vec3(0.38, 0.08, 0.14), fill(dm) * face);
            float tongue = sdEllipse(mc + vec2(0.0, o * 1.05 + (uMouth > 2.5 ? 0.014 : 0.0)), vec2(0.024, uMouth > 2.5 ? 0.032 : 0.02));
            over(acc, vec3(1.0, 0.5, 0.6), fill(uMouth > 2.5 ? tongue : max(tongue, dm)) * face);
            if (uMouth > 1.5 && uMouth < 2.5) {
                for (int s = 0; s < 2; s++) {
                    float side = s == 0 ? -1.0 : 1.0;
                    float fang = sdTri(mc, vec2(0.02 * side, 0.02), vec2(0.034 * side, 0.02), vec2(0.027 * side, -0.006));
                    over(acc, vec3(1.0), fill(fang) * face);
                }
            }
        }
    }
    // Whiskers: fine, curved, slightly see-through.
    for (int s = 0; s < 2; s++) {
        float side = s == 0 ? -1.0 : 1.0;
        for (int k = 0; k < 3; k++) {
            float fk = float(k);
            vec2 a = vec2(0.09 * side, -0.1 - fk * 0.012);
            vec2 b = vec2((0.4 - fk * 0.025) * side, -0.055 - fk * 0.055 + 0.012 * sin(uTime * 2.0 + fk + side));
            vec2 pa = f - a, ba = b - a;
            float t = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
            float wsk = length(pa - ba * t + vec2(0.0, 0.03 * t * (1.0 - t))) - mix(0.003, 0.0008, t);
            over(acc, vec3(1.0), fill(wsk) * 0.75);
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
