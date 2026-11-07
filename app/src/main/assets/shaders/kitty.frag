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
float fillFur(float d) { return clamp(0.5 - d / (uPx * 2.2), 0.0, 1.0); }

// Short, fine fur: soft wrap light, cool subsurface, very fine strands.
vec3 shade(vec3 base, vec2 p, vec2 c, vec2 r, vec2 dir) {
    vec2 q = (p - c) / r;
    float t = clamp(1.0 - dot(q, q), 0.0, 1.0);
    vec3 n = normalize(vec3(q * 0.9, sqrt(t) + 0.2));
    vec3 L = normalize(vec3(-0.45, 0.65, 0.8));
    float diff = smoothstep(0.0, 1.0, clamp((dot(n, L) + 0.55) / 1.55, 0.0, 1.0));
    vec3 col = mix(base * vec3(0.55, 0.55, 0.62), base, diff);
    vec2 perp = vec2(-dir.y, dir.x);
    col *= 0.93 + 0.1 * vnoise(vec2(dot(p, perp) * 380.0, dot(p, dir) * 30.0));
    col += uRim * pow(1.0 - n.z, 2.4) * 0.45;
    return col;
}
// Tabby stripes across a part (stripes run perpendicular to `dir`).
float stripes(vec2 p, vec2 dir, float freq, float seed) {
    float s = sin(dot(p, dir) * freq + vnoise(p * 9.0 + seed) * 2.5);
    return smoothstep(0.55, 0.85, s);
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

    // ---- Warm pad (charging), under everything ----
    if (uPad.x > 0.01) {
        vec2 q = sp - vec2(0.0, -0.56);
        float pad = sdRoundBox(q, vec2(0.42, 0.04), 0.035);
        vec3 warm = mix(vec3(1.0, 0.45, 0.2), vec3(1.0, 0.75, 0.35), uPad.y);
        over(acc, warm, exp(-max(pad, 0.0) * 22.0) * 0.45 * uPad.x);
        over(acc, mix(warm * 0.6, warm * 1.2, smoothstep(-0.04, 0.04, q.y)), fill(pad) * uPad.x);
        float stitch = abs(sdRoundBox(q, vec2(0.37, 0.018), 0.015)) - 0.003;
        over(acc, vec3(1.0, 0.9, 0.7), fill(stitch) * 0.6 * uPad.x);
        // Heat shimmer lines.
        for (int k = 0; k < 3; k++) {
            float fk = float(k);
            float x = (fk - 1.0) * 0.25;
            float y = fract(uTime * 0.4 + fk * 0.33);
            vec2 hq = q - vec2(x + 0.02 * sin(y * 12.0 + uTime * 3.0), 0.05 + y * 0.18);
            float wave = sdSeg(hq, vec2(0.0, -0.03), vec2(0.0, 0.03)) - 0.004;
            over(acc, warm, fill(wave) * (1.0 - y) * 0.5 * uPad.x);
        }
    }

    // ---- Tail: a chain of segments curling up the right side ----
    {
        vec2 a = vec2(mix(0.17, 0.26, loaf), mix(-0.5, -0.5, loaf));
        float ang = uTail.x;
        float thick = 0.032 * (1.0 + 1.3 * uPuff);
        float best = 1e5; float bestI = 0.0;
        for (int i = 0; i < 7; i++) {
            float fi = float(i);
            float aa = ang + uTail.y * fi + uTail.w * sin(uTail.z - fi * 0.55);
            vec2 b = a + vec2(sin(aa), cos(aa)) * 0.075;
            float d = sdCapsule(p, a, b, thick, thick * (fi < 5.5 ? 1.0 : 0.85));
            if (d < best) { best = d; bestI = fi; }
            a = b;
        }
        if (best < 0.06) {
            float d = furEdge(best, p, 1.2 + uPuff * 2.0);
            float band = step(0.5, fract(bestI * 0.5 + 0.25));
            vec3 tc = mix(fur, dark, band * 0.8);
            if (uPoints > 0.5) tc = mix(fur, dark, smoothstep(1.0, 4.0, bestI));
            over(acc, shade(tc, p, vec2(0.25, -0.2), vec2(0.4, 0.5), vec2(0.0, 1.0)), fillFur(d));
        }
    }

    // ---- Body ----
    vec2 bc = vec2(0.0, mix(-0.27, -0.4, loaf) - crouch * 0.05);
    vec2 br = vec2(mix(0.19, 0.33, loaf), mix(0.26, 0.15, loaf)) * (1.0 + 0.12 * uPuff);
    vec2 hc2 = vec2(0.0, mix(-0.45, -0.44, loaf) + crouch * 0.06);
    vec2 hr2 = vec2(mix(0.26, 0.34, loaf), mix(0.1, 0.11, loaf)) * (1.0 + 0.12 * uPuff);
    float body = smin(sdEllipse(p - bc, br), sdEllipse(p - hc2, hr2), 0.08);
    if (body < 0.08) {
        float d = furEdge(body, p, 1.0);
        vec3 col = shade(fur, p, bc, br * 1.2, vec2(0.0, -1.0));
        // Tabby bands on the flanks.
        col = mix(col, shade(dark, p, bc, br * 1.2, vec2(0.0, -1.0)), stripes(vec2(abs(p.x), p.y), vec2(0.35, 1.0), 38.0, 1.0) * smoothstep(0.06, 0.16, abs(p.x)) * (1.0 - uPoints) * 0.75);
        over(acc, col, fillFur(d));
        // White chest and belly.
        float chest = sdEllipse(p - bc - vec2(0.0, br.y * 0.15), vec2(br.x * 0.5, br.y * 0.75));
        over(acc, shade(white, p, bc, br * 1.2, vec2(0.0, -1.0)), fillFur(furEdge(chest, p * 1.4, 1.6)) * fill(body) * (1.0 - uPoints * 0.6));
    }

    // ---- Front legs ----
    for (int s = 0; s < 2; s++) {
        float side = s == 0 ? -1.0 : 1.0;
        vec2 paw = s == 0 ? uPawL : uPawR;
        vec2 sh = vec2(0.075 * side, mix(-0.22, -0.38, loaf) - crouch * 0.04);
        float len = mix(0.3, 0.12, loaf) * paw.y * (1.0 - 0.35 * crouch);
        vec2 dir = rot(vec2(0.0, -1.0), paw.x * side);
        vec2 foot = sh + dir * len;
        if (loaf > 0.85 && paw.x < 0.05) continue; // tucked in a loaf
        float leg = sdCapsule(p, sh, foot, 0.045, 0.04);
        over(acc, shade(mix(fur, white, 0.35), p, sh + dir * len * 0.5, vec2(0.1, 0.2), dir), fillFur(furEdge(leg, p, 0.6)));
        float pd = sdEllipse(rot(p - foot - dir * 0.008, paw.x * side), vec2(0.052, 0.034));
        over(acc, shade(mix(white, dark, uPoints), p, foot, vec2(0.06, 0.05), vec2(1.0, 0.0)), fill(pd));
        vec2 tq = rot(p - foot, paw.x * side);
        for (int k = 0; k < 2; k++) {
            float tl = sdSeg(tq, vec2((float(k) - 0.5) * 0.028, -0.032), vec2((float(k) - 0.5) * 0.028, -0.012)) - 0.002;
            over(acc, white * 0.6, fill(tl) * 0.6);
        }
    }

    // ---- Box (hiding) : front face over the body ----
    if (uBox.x > 0.01) {
        vec2 q = sp - vec2(0.0, -0.35);
        float box = sdRoundBox(q, vec2(0.38, 0.2), 0.01);
        vec3 card = vec3(0.8, 0.62, 0.42) * (0.9 + 0.1 * vnoise(q * vec2(5.0, 50.0)));
        over(acc, card, fill(box) * uBox.x);
        float flapL = sdTri(q, vec2(-0.38, 0.2), vec2(-0.02, 0.2), vec2(-0.5, 0.33));
        float flapR = sdTri(q, vec2(0.38, 0.2), vec2(0.02, 0.2), vec2(0.5, 0.33));
        over(acc, card * 0.85, fill(min(flapL, flapR)) * uBox.x);
        over(acc, vec3(0.6, 0.45, 0.3), fill(abs(q.y - 0.19) - 0.006) * fill(box) * uBox.x);
        over(acc, vec3(0.75, 0.68, 0.55), fill(sdRoundBox(q - vec2(0.0, 0.12), vec2(0.05, 0.08), 0.004)) * uBox.x * 0.7);
    }

    // ---- Head (skipped entirely for pixels far from it) ----
    vec2 hp = vec2(0.0, mix(0.06, -0.17, loaf) + uHeadY - crouch * 0.14);
    if (length(p - hp) < 0.62) {
    // ---- Head ----
    vec2 h = rot(p - hp, -uTilt);
    vec2 turn = vec2(uLook.x * 0.04, uLook.y * 0.025);
    float skull = sdEllipse(h - vec2(0.0, 0.02), vec2(0.22, 0.18));
    float cheeks = sdEllipse(h - vec2(0.0, -0.05), vec2(0.245, 0.12) * (1.0 + 0.08 * uPuff));
    float head = smin(skull, cheeks, 0.06);

    // Ears behind the head.
    for (int s = 0; s < 2; s++) {
        float side = s == 0 ? -1.0 : 1.0;
        float sw = (s == 0 ? uEars.x : uEars.y);
        vec2 pivot = vec2(0.13 * side, 0.13);
        vec2 eq = rot(h - pivot, -(sw + uEarsDown * 0.9 * side) * 1.0);
        float ear = sdTri(eq, vec2(-0.075 * side - 0.0, -0.03), vec2(0.07 * side, -0.02), vec2(0.03 * side, 0.17 - 0.07 * uEarsDown));
        float inner = sdTri(eq, vec2(-0.045 * side, -0.01), vec2(0.045 * side, -0.005), vec2(0.028 * side, 0.12 - 0.06 * uEarsDown));
        over(acc, shade(mix(fur, dark, uPoints), h, pivot, vec2(0.08, 0.15), vec2(0.0, 1.0)), fill(ear - 0.012));
        over(acc, vec3(1.0, 0.68, 0.72) * 0.9, fill(inner) * 0.85);
        // Ear tufts.
        for (int k = 0; k < 2; k++) {
            float t = sdSeg(eq, vec2(0.0 + float(k) * 0.02 * side, 0.0), vec2(0.015 * side + float(k) * 0.025 * side, 0.08)) - 0.003;
            over(acc, vec3(0.96), fill(t) * 0.6);
        }
    }
    {
        float d = furEdge(head, h, 0.8);
        vec3 col = shade(fur, h, vec2(0.0), vec2(0.27, 0.22), normalize(h + vec2(0.0, 0.25)));
        // Siamese mask.
        col = mix(col, shade(dark, h, vec2(0.0), vec2(0.27, 0.22), vec2(0.0, -1.0)), uPoints * smoothstep(0.17, 0.05, length((h - vec2(0.0, -0.03)) * vec2(1.0, 1.2))) * 0.9);
        over(acc, col, fillFur(d));
    }
    vec2 f = h - turn;
    float face = fill(head + 0.008);

    // White muzzle and chin.
    float muzzle = smin(sdEllipse(f - vec2(-0.045, -0.085), vec2(0.06, 0.045)), sdEllipse(f - vec2(0.045, -0.085), vec2(0.06, 0.045)), 0.02);
    muzzle = smin(muzzle, sdEllipse(f - vec2(0.0, -0.035), vec2(0.035, 0.06)), 0.03);
    over(acc, shade(mix(white, dark * 1.1, uPoints), f, vec2(0.0, -0.08), vec2(0.13, 0.08), vec2(sign(f.x), -0.3)), fill(furEdge(muzzle, f, 0.5)) * face);

    // Tabby "M" on the forehead and cheek stripes.
    if (uPoints < 0.5) {
        float m = min(min(sdSeg(f, vec2(-0.06, 0.1), vec2(-0.04, 0.16)), sdSeg(f, vec2(0.0, 0.09), vec2(0.0, 0.17))), sdSeg(f, vec2(0.06, 0.1), vec2(0.04, 0.16))) - 0.008;
        over(acc, dark, fill(m) * 0.7 * face);
        for (int s = 0; s < 2; s++) {
            float side = s == 0 ? -1.0 : 1.0;
            for (int k = 0; k < 2; k++) {
                float y = -0.02 - float(k) * 0.035;
                float cs = sdSeg(f, vec2(0.17 * side, y), vec2(0.23 * side, y + 0.012)) - 0.006;
                over(acc, dark, fill(cs) * 0.6 * face);
            }
        }
    }

    // Eyes: big almond eyes, iris colour, slit pupils that open wide when excited.
    for (int s = 0; s < 2; s++) {
        float side = s == 0 ? -1.0 : 1.0;
        vec2 ec = f - vec2(0.09 * side, 0.015);
        ec = rot(ec, 0.12 * side);
        vec2 look = uLook * vec2(0.012, 0.01);
        if (uEyeMode < 0.5 || uEyeMode > 1.5 && uEyeMode < 2.5 || uEyeMode > 3.5) {
            float open = uEyeOpen;
            if (uEyeMode > 1.5 && uEyeMode < 2.5) open = min(open, 0.35);
            if (uEyeMode > 3.5) open = 1.15;
            // Almond = intersection of two circles.
            float r = 0.075;
            float hgt = 0.042 * max(open, 0.05);
            float off = (r * r - hgt * hgt) / (2.0 * hgt);
            float eye = max(length(ec - vec2(0.0, -off)) - (off + hgt), length(ec + vec2(0.0, -off)) - (off + hgt));
            eye = max(eye, abs(ec.x) - 0.06);
            vec3 iris = mix(uEye * 0.55, uEye * 1.15, smoothstep(-0.04, 0.03, -ec.y + 0.01));
            iris += uEye * uEyeGlow * 0.8;
            over(acc, iris, fill(eye) * face);
            float pw = mix(0.007, 0.03, uPupil);
            float pupil = sdEllipse(ec - look, vec2(pw, 0.04));
            over(acc, vec3(0.01), fill(max(pupil, eye)) * face);
            if (open > 0.3) {
                float hl = length(ec - look - vec2(-0.016, 0.014)) - 0.011;
                float hl2 = length(ec - look - vec2(0.016, -0.012)) - 0.005;
                over(acc, vec3(1.0), (fill(hl) + fill(hl2) * 0.7) * face);
            }
            over(acc, vec3(0.08), fill(abs(eye) - 0.004) * face);
            if (uEyeGlow > 0.01) over(acc, uEye * 1.4, exp(-max(eye, 0.0) * 60.0) * uEyeGlow * 0.6 * face);
        } else if (uEyeMode < 1.5) {
            float ln = sdArc(ec + vec2(0.0, 0.01), 0.035, 1.05) - 0.006;
            over(acc, vec3(0.1), fill(ln) * face);
        } else {
            float ln = sdArc(ec * vec2(1.0, -1.0) + vec2(0.0, 0.015), 0.04, 1.0) - 0.005;
            over(acc, vec3(0.1), fill(ln) * face);
        }
    }

    // Nose and mouth.
    {
        vec2 nq = f - vec2(0.0, -0.06);
        float nose = sdTri(nq, vec2(-0.022, 0.012), vec2(0.022, 0.012), vec2(0.0, -0.012)) - 0.005;
        over(acc, mix(vec3(1.0, 0.55, 0.62), vec3(0.3, 0.2, 0.22), uPoints), fill(nose) * face);
        vec2 mc = f - vec2(0.0, -0.09);
        if (uMouth < 0.5) {
            float w1 = sdArc((mc - vec2(-0.018, 0.0)) * vec2(1.0, -1.0), 0.018, 1.2);
            float w2 = sdArc((mc - vec2(0.018, 0.0)) * vec2(1.0, -1.0), 0.018, 1.2);
            over(acc, vec3(0.25, 0.15, 0.15), fill(min(w1, w2) - 0.003) * face);
        } else {
            float o = uMouth < 1.5 ? 0.028 : (uMouth < 2.5 ? 0.06 : 0.02);
            float dm = sdEllipse(mc + vec2(0.0, o * 0.6), vec2(uMouth < 2.5 ? 0.04 : 0.025, o));
            over(acc, vec3(0.35, 0.08, 0.12), fill(dm) * face);
            float tongue = sdEllipse(mc + vec2(0.0, o * 1.1 + (uMouth > 2.5 ? 0.012 : 0.0)), vec2(0.022, uMouth > 2.5 ? 0.03 : 0.018));
            over(acc, vec3(1.0, 0.5, 0.58), fill(uMouth > 2.5 ? tongue : max(tongue, dm)) * face);
            if (uMouth > 1.5 && uMouth < 2.5) {
                // Little fangs.
                for (int s = 0; s < 2; s++) {
                    float side = s == 0 ? -1.0 : 1.0;
                    float fang = sdTri(mc + vec2(0.0, 0.0), vec2(0.022 * side, -0.004), vec2(0.034 * side, -0.004), vec2(0.028 * side, -0.026));
                    over(acc, vec3(1.0), fill(fang) * face);
                }
            }
        }
    }
    // Whiskers.
    for (int s = 0; s < 2; s++) {
        float side = s == 0 ? -1.0 : 1.0;
        for (int k = 0; k < 3; k++) {
            float fk = float(k);
            vec2 a = vec2(0.075 * side, -0.075 - fk * 0.012);
            vec2 b = vec2((0.36 - fk * 0.02) * side, -0.03 - fk * 0.05 + 0.01 * sin(uTime * 2.0 + fk + side));
            float wsk = sdSeg(f, a, b) - 0.0022;
            over(acc, vec3(0.97), fill(wsk) * 0.8);
        }
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
