// Pulsify — Kalboob, the golden retriever puppy. GLSL ES 1.00.
// Same quad + premultiplied-alpha setup as Mochi (mochi.vert). Character space: y up,
// paws on the floor at y = -0.55, sitting puppy about 1.15 tall. Uniform scale only.
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
uniform vec2  uSquash;     // (sx, sy)
uniform float uShear;      // happy wiggle
uniform vec2  uLook;       // head turn -1..1
uniform float uTilt;       // head roll (radians)
uniform float uHeadY;      // head bob
uniform float uLie;        // 0 sitting .. 1 lying down
uniform float uEyeOpen;
uniform float uEyeMode;    // 0 normal, 1 happy ^^, 2 sad, 3 asleep, 4 bliss
uniform float uMouth;      // 0 smile, 1 panting tongue, 2 bark, 3 howl, 4 yawn, 5 holding
uniform vec2  uEars;       // flop angle left, right
uniform float uTail;       // tail angle
uniform vec2  uPawL;       // raise angle, reach
uniform vec2  uPawR;
uniform float uKick;       // back-leg kick (belly rub)
uniform vec3  uCoat;       // main fur
uniform vec3  uCoat2;      // light fur (muzzle, chest, brows)
uniform vec3  uCollar;
uniform vec3  uRim;
uniform vec3  uGlow;
uniform vec4  uBone;       // x, y, visible, battery level (char space)
uniform vec4  uBall;       // x, y (char space), visible, spin
uniform vec4  uFx[6];      // x, y, life, kind: 1 heart, 2 sparkle, 3 dirt, 4 note, 5 zzz, 6 "!", 7 sniff
uniform vec4  uFly;        // butterfly x, y, flap phase, visible
uniform float uBattIcon;
uniform float uDim;

// ---------- helpers ----------
float sdEllipse(vec2 p, vec2 r) { float k0 = length(p / r); float k1 = length(p / (r * r)); return k0 * (k0 - 1.0) / max(k1, 1e-4); }
float sdRoundBox(vec2 p, vec2 b, float r) { vec2 q = abs(p) - b + r; return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r; }
float sdSeg(vec2 p, vec2 a, vec2 b) { vec2 pa = p - a, ba = b - a; float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0); return length(pa - ba * h); }
float sdCapsule(vec2 p, vec2 a, vec2 b, float ra, float rb) {
    vec2 pa = p - a, ba = b - a; float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
    return length(pa - ba * h) - mix(ra, rb, h);
}
float smin(float a, float b, float k) { float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0); return mix(b, a, h) - k * h * (1.0 - h); }
vec2 rot(vec2 p, float a) { float c = cos(a), s = sin(a); return vec2(c * p.x - s * p.y, s * p.x + c * p.y); }
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
// Fur: the silhouette breaks into soft tufts; alpha falls off over a few pixels.
float furD(float d, vec2 p, float amt) {
    if (abs(d) > 0.06) return d; // fur noise only near the silhouette
    float n = vnoise(p * 55.0) * 0.6 + vnoise(p * 120.0) * 0.4;
    return d + (n - 0.45) * 0.03 * amt * smoothstep(0.06, 0.0, abs(d));
}
float fillFur(float d) { return clamp(0.5 - d / (uPx * 2.6), 0.0, 1.0); }

vec3 domeN(float d, vec2 g, float r) { float t = sqrt(clamp(-d / r, 0.0, 1.0)); return normalize(vec3(g * (1.0 - t) * 1.2, t + 0.15)); }

// Fur shading: soft wrap light, warm subsurface, strands running along `dir`.
vec3 furShade(vec3 n, vec3 base, vec2 p, vec2 dir, float ao, float glowAmt) {
    vec3 L = normalize(vec3(-0.45, 0.65, 0.8));
    float diff = smoothstep(0.0, 1.0, clamp((dot(n, L) + 0.6) / 1.6, 0.0, 1.0));
    vec3 col = mix(base * vec3(0.62, 0.45, 0.32), base, diff);
    vec2 perp = vec2(-dir.y, dir.x);
    float strands = vnoise(vec2(dot(p, perp) * 320.0, dot(p, dir) * 26.0)) * 0.6 + vnoise(vec2(dot(p, perp) * 150.0, dot(p, dir) * 12.0)) * 0.4;
    float clump = vnoise(p * 22.0);
    col *= 0.9 + 0.13 * strands + 0.07 * clump;
    float fres = pow(1.0 - clamp(n.z, 0.0, 1.0), 2.2);
    col += uRim * fres * 0.45;
    col += uGlow * glowAmt;
    return col * ao;
}

// ---------- body shapes (all take the posed point) ----------
float headSdf(vec2 h) {
    float skull = sdEllipse(h - vec2(0.0, 0.03), vec2(0.27, 0.235));
    float cheeks = sdEllipse(h - vec2(0.0, -0.07), vec2(0.25, 0.15));
    return smin(skull, cheeks, 0.08);
}
float muzzleSdf(vec2 h) { return sdEllipse(h - vec2(0.0, -0.1), vec2(0.13, 0.095)); }
float earSdf(vec2 h, float side, float flop) {
    vec2 pivot = vec2(0.2 * side, 0.15);
    vec2 q = rot(h - pivot, -(0.25 + flop) * side);
    return sdEllipse(q - vec2(0.045 * side, -0.13), vec2(0.085, 0.165));
}

float sdTri(vec2 p, vec2 a, vec2 b, vec2 c) {
    vec2 e0 = b - a, e1 = c - b, e2 = a - c, v0 = p - a, v1 = p - b, v2 = p - c;
    vec2 pq0 = v0 - e0 * clamp(dot(v0, e0) / dot(e0, e0), 0.0, 1.0);
    vec2 pq1 = v1 - e1 * clamp(dot(v1, e1) / dot(e1, e1), 0.0, 1.0);
    vec2 pq2 = v2 - e2 * clamp(dot(v2, e2) / dot(e2, e2), 0.0, 1.0);
    float s = sign(e0.x * e2.y - e0.y * e2.x);
    vec2 d = min(min(vec2(dot(pq0, pq0), s * (v0.x * e0.y - v0.y * e0.x)), vec2(dot(pq1, pq1), s * (v1.x * e1.y - v1.y * e1.x))), vec2(dot(pq2, pq2), s * (v2.x * e2.y - v2.y * e2.x)));
    return -sqrt(d.x) * sign(d.y);
}
void main() {
    vec2 p = (vUv - uPos) / uSize;
    vec2 sp = p;
    p.y = (p.y + 0.55) / uSquash.y - 0.55;
    p.x = p.x / uSquash.x;
    float hgt = clamp((p.y + 0.55) / 1.15, 0.0, 1.2);
    p.x -= uShear * hgt * hgt;

    float lie = uLie;
    vec4 acc = vec4(0.0);
    vec3 coat = uCoat, light = uCoat2;
    vec2 e = vec2(0.004, 0.0);

    // Floor shadow / glow pool.
    if (uBone.z > 0.01) {
        float pool = exp(-pow(length((p - vec2(uBone.x, -0.56)) * vec2(1.0, 5.0)), 2.0) * 7.0) * uBone.z;
        over(acc, uGlow, pool * 0.3);
    }

    // ---- Tail (behind everything): a plume swinging from the rump ----
    {
        vec2 base = vec2(0.17, mix(-0.44, -0.47, lie));
        float a = uTail;
        vec2 t1 = base + rot(vec2(0.13, 0.05), a * 0.6);
        vec2 t2 = t1 + rot(vec2(0.12, 0.12), a);
        float d = min(sdCapsule(p, base, t1, 0.06, 0.07), sdCapsule(p, t1, t2, 0.07, 0.03));
        d = furD(d, p + vec2(uTime * 0.0), 1.6);
        vec2 g = normalize(t2 - base);
        over(acc, furShade(vec3(0.0, 0.0, 1.0) * 0.6 + vec3(0.2, 0.3, 0.7), mix(coat, light, 0.35), p, g, 0.95, 0.0), fillFur(d));
    }

    // ---- Haunches and back legs ----
    vec2 hc = vec2(0.0, mix(-0.4, -0.44, lie));
    float haunch = sdEllipse(p - hc, vec2(mix(0.34, 0.4, lie), mix(0.16, 0.12, lie)));
    float kickA = uKick;
    float legR = sdEllipse(rot(p - vec2(0.27, -0.46) - vec2(0.0, 0.05 * max(kickA, 0.0)), -0.5 * kickA) , vec2(0.12, 0.1));
    float legL = sdEllipse(p - vec2(-0.27, -0.46), vec2(0.12, 0.1));
    float back = smin(haunch, min(legL, legR), 0.06);
    {
        float d = furD(back, p, 1.0);
        vec2 g = normalize(vec2(sdEllipse(p + e.xy - hc, vec2(0.34, 0.16)) - sdEllipse(p - e.xy - hc, vec2(0.34, 0.16)),
                                sdEllipse(p + e.yx - hc, vec2(0.34, 0.16)) - sdEllipse(p - e.yx - hc, vec2(0.34, 0.16))) + 1e-5);
        over(acc, furShade(domeN(back, g, 0.12), coat * 0.92, p, vec2(0.0, -1.0), 0.88, 0.0), fillFur(d));
        // Back paws (lighter toes).
        for (int s = 0; s < 2; s++) {
            float side = s == 0 ? -1.0 : 1.0;
            vec2 pc = vec2(0.31 * side, -0.52) + (s == 1 ? vec2(0.03, 0.08) * max(kickA, 0.0) : vec2(0.0));
            float pd = sdEllipse(p - pc, vec2(0.08, 0.04));
            over(acc, furShade(vec3(0, 0.3, 0.95), mix(coat, light, 0.4), p, vec2(1, 0), 0.92, 0.0), fill(furD(pd, p, 0.5)));
        }
    }

    // ---- Torso and chest ----
    vec2 tc = vec2(0.0, mix(-0.2, -0.36, lie));
    vec2 tr = vec2(mix(0.25, 0.32, lie), mix(0.29, 0.16, lie));
    float torso = sdEllipse(p - tc, tr);
    {
        float d = furD(torso, p, 1.0);
        vec2 g = normalize(vec2(sdEllipse(p + e.xy - tc, tr) - sdEllipse(p - e.xy - tc, tr), sdEllipse(p + e.yx - tc, tr) - sdEllipse(p - e.yx - tc, tr)) + 1e-5);
        vec3 n = domeN(torso, g, 0.2);
        float glowAmt = uBone.z * 0.12 * smoothstep(0.35, 0.0, length(p - uBone.xy));
        over(acc, furShade(n, coat, p, vec2(0.0, -1.0), 1.0, glowAmt), fillFur(d));
        // Fluffy light chest.
        float chest = sdEllipse(p - tc - vec2(0.0, tr.y * 0.25), vec2(tr.x * 0.55, tr.y * 0.6));
        float cd = furD(chest, p * 1.3, 1.6);
        over(acc, furShade(n, light, p, vec2(0.0, -1.0), 1.0, glowAmt), fillFur(cd) * 0.85 * fill(torso));
    }

    // ---- Front legs ----
    for (int s = 0; s < 2; s++) {
        float side = s == 0 ? -1.0 : 1.0;
        vec2 paw = s == 0 ? uPawL : uPawR;
        vec2 sh = vec2(0.11 * side, mix(-0.2, -0.33, lie));
        float len = mix(0.3, 0.14, lie) * paw.y;
        // Raise angle swings the leg up and outward (wave / give paw).
        vec2 dir = rot(vec2(0.0, -1.0), paw.x * side);
        vec2 foot = sh + dir * len;
        float leg = sdCapsule(p, sh, foot, 0.065, 0.058);
        float d = furD(leg, p, 0.7);
        over(acc, furShade(vec3(-0.2 * side, 0.1, 0.95), coat * 0.97, p, dir, 0.97, 0.0), fillFur(d));
        float pd = sdEllipse(rot(p - foot - dir * 0.01, paw.x * side), vec2(0.072, 0.048));
        vec3 pawC = furShade(vec3(0, 0.3, 0.95), mix(coat, light, 0.45), p, vec2(1, 0), 1.0, 0.0);
        over(acc, pawC, fill(furD(pd, p, 0.4)));
        // Toe lines.
        for (int k = 0; k < 2; k++) {
            vec2 tq = rot(p - foot, paw.x * side);
            float tl = sdSeg(tq, vec2((float(k) - 0.5) * 0.04, -0.045), vec2((float(k) - 0.5) * 0.04, -0.015)) - 0.003;
            over(acc, coat * 0.45, fill(tl) * 0.5);
        }
    }

    // ---- Collar ----
    vec2 hp0 = vec2(0.0, mix(0.2, -0.08, lie) + uHeadY);
    {
        vec2 cc = vec2(0.0, hp0.y - 0.235);
        float band = abs(sdEllipse(p - cc, vec2(0.16, 0.045))) - 0.016;
        float lower = max(band, p.y - cc.y - 0.005);
        over(acc, mix(uCollar * 0.6, uCollar, smoothstep(cc.y - 0.06, cc.y, p.y)), fill(lower));
        float tag = length(p - cc - vec2(0.0, -0.075)) - 0.03;
        vec3 gold = vec3(1.0, 0.82, 0.35);
        over(acc, mix(gold * 0.6, gold, smoothstep(-0.03, 0.03, p.x - cc.x + p.y - cc.y + 0.075)) + uGlow * uBone.z * 0.3, fill(tag));
    }

    // ---- Head (skipped entirely for pixels far from it) ----
    if (length(p - hp0) < 0.62) {
    // ---- Head ----
    vec2 h = rot(p - hp0, -uTilt);
    vec2 turn = vec2(uLook.x * 0.045, uLook.y * 0.03);
    float hd = headSdf(h);
    {
        vec2 g = normalize(vec2(headSdf(h + e.xy) - headSdf(h - e.xy), headSdf(h + e.yx) - headSdf(h - e.yx)) + 1e-5);
        vec3 n = domeN(hd, g, 0.2);
        over(acc, furShade(n, coat, h, normalize(h + vec2(0.0, 0.3)), 1.0, 0.0), fillFur(furD(hd, h, 0.8)));
    }
    vec2 f = h - turn;
    float face = fill(hd + 0.01);

    // Light blaze between the eyes and the muzzle.
    float blaze = sdEllipse(f - vec2(0.0, 0.0), vec2(0.06, 0.14));
    over(acc, light * 0.98, fill(furD(blaze, f, 1.5) + 0.01) * 0.55 * face);

    // Eyebrow tufts (lighter, angle with mood).
    for (int s = 0; s < 2; s++) {
        float side = s == 0 ? -1.0 : 1.0;
        float sad = uEyeMode > 1.5 && uEyeMode < 2.5 ? 1.0 : 0.0;
        vec2 bq = rot(f - vec2(0.1 * side, 0.115), (0.25 * side) * (0.3 - sad * 2.0));
        float brow = sdEllipse(bq, vec2(0.035, 0.016));
        over(acc, light, fill(brow) * 0.8 * face);
    }

    // Eyes: deep brown, glossy.
    for (int s = 0; s < 2; s++) {
        float side = s == 0 ? -1.0 : 1.0;
        vec2 ec = f - vec2(0.105 * side, 0.055);
        vec2 look = uLook * vec2(0.01, 0.008);
        if (uEyeMode < 0.5 || (uEyeMode > 1.5 && uEyeMode < 2.5)) {
            float open = uEyeOpen;
            if (uEyeMode > 1.5) open = min(open, 0.75);
            float de = sdEllipse(ec, vec2(0.046, 0.05 * max(open, 0.08)));
            vec3 iris = mix(vec3(0.05, 0.025, 0.015), vec3(0.32, 0.17, 0.07), smoothstep(0.03, -0.04, ec.y));
            over(acc, iris, fill(de) * face);
            float pupil = length(ec - look) - 0.024;
            over(acc, vec3(0.01), fill(max(pupil, de)) * face);
            if (open > 0.4) {
                over(acc, vec3(1.0), (fill(length(ec - look - vec2(-0.016, 0.018)) - 0.014) + fill(length(ec - look - vec2(0.016, -0.016)) - 0.006) * 0.8) * face);
            }
            // Dark rim around the eye.
            over(acc, coat * 0.35, fill(abs(de) - 0.004) * 0.6 * face);
            if (uEyeMode > 1.5) {
                float lid = sdSeg(rot(ec, 0.35 * side), vec2(-0.05, 0.03), vec2(0.05, 0.03)) - 0.012;
                over(acc, coat * 0.9, fill(lid) * face);
            }
        } else if (uEyeMode < 1.5 || uEyeMode > 3.5) {
            float ln = sdArc(ec + vec2(0.0, 0.012), 0.038, 1.0) - 0.007;
            over(acc, vec3(0.08, 0.04, 0.02), fill(ln) * face);
        } else {
            float ln = sdArc(ec * vec2(1.0, -1.0) + vec2(0.0, 0.02), 0.04, 0.95) - 0.006;
            over(acc, vec3(0.08, 0.04, 0.02), fill(ln) * face);
        }
    }

    // Muzzle.
    float md = muzzleSdf(f);
    {
        vec2 g = normalize(vec2(muzzleSdf(f + e.xy) - muzzleSdf(f - e.xy), muzzleSdf(f + e.yx) - muzzleSdf(f - e.yx)) + 1e-5);
        over(acc, furShade(domeN(md, g, 0.08), light, f, vec2(sign(f.x), -0.3), 1.0, 0.0), fill(furD(md, f, 0.6)) * face);
    }
    // Mouth.
    vec2 mc = f - vec2(0.0, -0.135);
    vec3 mouthDark = vec3(0.18, 0.06, 0.06);
    float open = 0.0;
    if (uMouth > 0.5 && uMouth < 1.5) open = 0.035;
    else if (uMouth > 1.5 && uMouth < 2.5) open = 0.05;
    else if (uMouth > 2.5 && uMouth < 3.5) open = 0.04;
    else if (uMouth > 3.5 && uMouth < 4.5) open = 0.075;
    else if (uMouth > 4.5) open = 0.02;
    if (open > 0.0) {
        vec2 mr = uMouth > 2.5 && uMouth < 3.5 ? vec2(0.03, open) : vec2(0.06, open);
        float dm = sdEllipse(mc + vec2(0.0, open * 0.5), mr);
        over(acc, vec3(0.3, 0.08, 0.1), fill(dm) * face);
        if (uMouth < 1.5 || (uMouth > 3.5 && uMouth < 4.5)) {
            float tongue = sdEllipse(mc + vec2(0.0, open * 0.9 + 0.02 * step(uMouth, 1.5)), vec2(0.035, 0.045));
            float tonguePant = tongue + 0.004 * sin(uTime * 14.0);
            over(acc, mix(vec3(0.95, 0.38, 0.45), vec3(1.0, 0.6, 0.65), smoothstep(-0.02, 0.02, -mc.y - open)), fill(uMouth < 1.5 ? tonguePant : max(tongue, dm)) * face);
            float groove = sdSeg(mc, vec2(0.0, -open * 0.6), vec2(0.0, -open * 1.6)) - 0.002;
            over(acc, vec3(0.75, 0.25, 0.3), fill(groove) * face * step(uMouth, 1.5));
        }
    } else {
        float w1 = sdArc((mc - vec2(-0.025, 0.0)) * vec2(1.0, -1.0), 0.025, 1.2);
        float w2 = sdArc((mc - vec2(0.025, 0.0)) * vec2(1.0, -1.0), 0.025, 1.2);
        over(acc, mouthDark, fill(min(w1, w2) - 0.004) * face);
    }
    float philtrum = sdSeg(f, vec2(0.0, -0.085), vec2(0.0, -0.125)) - 0.003;
    over(acc, mouthDark, fill(philtrum) * face);

    // Nose: glossy black rounded triangle with a highlight.
    {
        vec2 nq = f - vec2(0.0, -0.06);
        float nose = max(sdEllipse(nq, vec2(0.05, 0.036)), -nq.y - 0.03 + abs(nq.x) * 0.5);
        vec3 nc = mix(vec3(0.02), vec3(0.16, 0.14, 0.15), smoothstep(-0.03, 0.03, nq.y));
        over(acc, nc, fill(nose) * face);
        over(acc, vec3(1.0), fill(sdEllipse(nq - vec2(-0.015, 0.016), vec2(0.016, 0.008))) * 0.7 * face);
    }

    // Ears (over the head edges, floppy).
    for (int s = 0; s < 2; s++) {
        float side = s == 0 ? -1.0 : 1.0;
        float flop = s == 0 ? uEars.x : uEars.y;
        float ed = earSdf(h, side, flop);
        vec2 g = normalize(vec2(earSdf(h + e.xy, side, flop) - earSdf(h - e.xy, side, flop), earSdf(h + e.yx, side, flop) - earSdf(h - e.yx, side, flop)) + 1e-5);
        vec2 dir = normalize(rot(vec2(0.0, -1.0), -(0.25 + flop) * side));
        over(acc, furShade(domeN(ed, g, 0.06), coat * 0.86, h, dir, 0.95, 0.0), fillFur(furD(ed, h, 1.4)));
    }

    }

    // ---- Glowing bone (charging), held in the paws ----
    if (uBone.z > 0.01) {
        // A chunky bone that fills with light as the battery charges, with a luminous bolt badge.
        vec2 q = (p - uBone.xy) / 1.25;
        float shaft = sdRoundBox(q, vec2(0.11, 0.024), 0.022);
        float knobs = min(min(length(q - vec2(-0.12, 0.027)), length(q - vec2(-0.12, -0.027))), min(length(q - vec2(0.12, 0.027)), length(q - vec2(0.12, -0.027)))) - 0.034;
        float bone = smin(shaft, knobs, 0.015) * 1.25;
        float lvl = uBone.w;
        float beat = 0.6 + 0.4 * sin(uTime * 4.0);
        vec3 ivory = vec3(1.0, 0.97, 0.88) * (0.78 + 0.22 * smoothstep(-0.03, 0.03, q.y));
        // Charge fills the bone from the left; the leading edge shimmers.
        float fillX = -0.155 + 0.31 * lvl;
        float charged = smoothstep(0.012, -0.012, q.x - fillX + 0.006 * sin(q.y * 80.0 + uTime * 6.0));
        vec3 boneC = mix(ivory, mix(uGlow, vec3(1.0), 0.25) * (0.9 + 0.2 * beat), charged * 0.8);
        boneC += vec3(1.0) * smoothstep(0.008, 0.0, abs(q.y - 0.012)) * 0.25;
        over(acc, uGlow, exp(-max(bone, 0.0) * 30.0) * (0.3 + 0.25 * beat) * uBone.z);
        over(acc, boneC, fill(bone) * uBone.z);
        over(acc, vec3(1.0), fill(bone) * smoothstep(0.01, 0.0, abs(q.x - fillX)) * 0.6 * uBone.z);
        // Bite marks.
        for (int k = 0; k < 2; k++) {
            float bm = length(q - vec2(0.06 + float(k) * 0.035, 0.022)) - 0.007;
            over(acc, ivory * 0.7, fill(bm * 1.25) * uBone.z * 0.7);
        }
        // Badge with the bolt.
        float disc = length(q) - 0.042;
        over(acc, vec3(0.05, 0.06, 0.1), fill(disc * 1.25) * uBone.z);
        over(acc, uGlow, fill((abs(disc) - 0.004) * 1.25) * uBone.z);
        vec2 bq = q / 0.055;
        float bolt = min(sdTri(bq, vec2(0.18, 0.5), vec2(-0.32, -0.06), vec2(0.06, -0.06)), sdTri(bq, vec2(-0.18, -0.5), vec2(0.32, 0.06), vec2(-0.06, 0.06))) * 0.055 * 1.25;
        over(acc, mix(uGlow, vec3(1.0), 0.4 + 0.35 * beat), fill(bolt) * uBone.z);
        over(acc, uGlow, exp(-max(bolt, 0.0) * 60.0) * 0.55 * beat * uBone.z);
        // Sparks hopping off the charged end.
        for (int k = 0; k < 3; k++) {
            float fk = float(k);
            float t = fract(uTime * 0.9 + fk * 0.37);
            vec2 sq = q - vec2(fillX + (fk - 1.0) * 0.03, 0.03 + t * 0.09);
            over(acc, mix(uGlow, vec3(1.0), 0.5), exp(-length(sq) * 120.0) * (1.0 - t) * uBone.z);
        }
    }

    // ---- Ball ----
    if (uBall.z > 0.01) {
        vec2 q = sp - uBall.xy;
        float r = 0.07;
        float b = length(q) - r;
        vec3 n = normalize(vec3(q / r, sqrt(max(0.0, 1.0 - dot(q / r, q / r)))));
        vec3 bc = vec3(0.85, 1.0, 0.25);
        float seam = abs(length(rot(q, uBall.w) - vec2(r * 1.2, 0.0)) - r * 0.9) - 0.006;
        vec3 col = bc * (0.45 + 0.55 * clamp(dot(n, normalize(vec3(-0.4, 0.6, 0.7))), 0.0, 1.0));
        col = mix(col, vec3(1.0), fill(seam) * 0.9);
        col += vec3(1.0) * pow(max(dot(n, normalize(vec3(-0.3, 0.5, 0.8))), 0.0), 20.0) * 0.3;
        over(acc, col, fill(b) * uBall.z);
    }

    // ---- Butterfly ----
    if (uFly.w > 0.01) {
        vec2 q = sp - uFly.xy;
        float flap = abs(sin(uFly.z));
        vec2 wq = vec2(abs(q.x) / (0.2 + 0.8 * flap), q.y);
        float wingT = sdEllipse(wq - vec2(0.035, 0.018), vec2(0.035, 0.03));
        float wingB = sdEllipse(wq - vec2(0.028, -0.02), vec2(0.024, 0.02));
        float wing = min(wingT, wingB);
        vec3 wc = mix(vec3(0.3, 0.6, 1.0), vec3(0.75, 0.45, 1.0), clamp(q.y * 20.0 + 0.5, 0.0, 1.0));
        over(acc, wc, exp(-max(wing, 0.0) * 60.0) * 0.4 * uFly.w);
        over(acc, wc, fill(wing) * uFly.w);
        over(acc, vec3(0.08), fill(sdSeg(q, vec2(0.0, -0.025), vec2(0.0, 0.025)) - 0.004) * uFly.w);
    }

    // ---- Battery icon (charging) ----
    if (uBattIcon > 0.01) {
        vec2 q = sp - vec2(0.0, 0.82);
        float frame = abs(sdRoundBox(q, vec2(0.15, 0.06), 0.022)) - 0.006;
        float tip = sdRoundBox(q - vec2(0.17, 0.0), vec2(0.011, 0.022), 0.005);
        float bw = 0.12 * uBone.w;
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
            float s = 0.05;
            over(acc, vec3(1.0, 0.35, 0.55), fill(sdHeart(q / s + vec2(0.0, 0.5)) * s) * fade);
        } else if (fx.w < 2.5) {
            float r = 0.04 * (1.0 - abs(life - 0.5));
            float star = min(sdSeg(q, vec2(-r, 0.0), vec2(r, 0.0)), sdSeg(q, vec2(0.0, -r), vec2(0.0, r))) - 0.004;
            over(acc, vec3(1.0, 0.95, 0.7), (fill(star) + exp(-length(q) * 70.0) * 0.6) * fade);
        } else if (fx.w < 3.5) {
            // Dirt clods flying from digging.
            for (int k = 0; k < 4; k++) {
                float fk = float(k);
                vec2 v = vec2((h21(vec2(fk, fx.x)) - 0.5) * 0.5, 0.35 + 0.2 * h21(vec2(fk, 3.0)));
                vec2 cp = v * life - vec2(0.0, 0.9 * life * life);
                float clod = length(q - cp) - 0.016 * (1.0 - life * 0.5);
                over(acc, vec3(0.42, 0.28, 0.16), fill(clod) * fade);
            }
        } else if (fx.w < 4.5) {
            // Music note (howl).
            vec2 nq = q - vec2(0.0, 0.0);
            float head = sdEllipse(rot(nq, 0.4), vec2(0.022, 0.016));
            float stem = sdSeg(nq, vec2(0.018, 0.005), vec2(0.018, 0.07)) - 0.004;
            float flag = sdSeg(nq, vec2(0.018, 0.07), vec2(0.04, 0.05)) - 0.004;
            over(acc, mix(uRim, vec3(1.0), 0.4), fill(min(min(head, stem), flag)) * fade);
        } else if (fx.w < 5.5) {
            vec2 z = q / (0.035 + life * 0.02);
            float dz = min(min(sdSeg(z, vec2(-0.5, 0.5), vec2(0.5, 0.5)), sdSeg(z, vec2(0.5, 0.5), vec2(-0.5, -0.5))), sdSeg(z, vec2(-0.5, -0.5), vec2(0.5, -0.5)));
            over(acc, vec3(0.8, 0.85, 1.0), fill(dz * (0.035 + life * 0.02) - 0.004) * fade * 0.8);
        } else if (fx.w < 6.5) {
            float bar = sdRoundBox(q - vec2(0.0, 0.03), vec2(0.008, 0.03), 0.006);
            float dotv = length(q + vec2(0.0, 0.02)) - 0.009;
            over(acc, vec3(1.0, 0.85, 0.3), fill(min(bar, dotv)) * fade);
        } else {
            // Sniff puffs.
            float puff = abs(length(q) - 0.02 - life * 0.04) - 0.004;
            over(acc, vec3(0.85, 0.9, 1.0), fill(puff) * fade * 0.6);
        }
    }

    acc.rgb *= mix(1.0, 0.55, uDim);
    gl_FragColor = acc;
}
