// Pulsify — Kiwi, the parrot (Ringneck, Budgie, Cockatiel, Lovebird). GLSL ES 1.00. Shares mochi.vert.
// Character space: facing +x, feet grip the branch at (0, -0.5), standing height about 0.9.
// The branch itself is drawn in screen space across the whole quad.
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif

varying vec2 vUv;

uniform float uTime;
uniform vec2  uPos;        // bird origin in uv (grip point is at char (0,-0.5))
uniform float uSize;
uniform float uPx;
uniform float uFace;       // -1 .. 1, facing direction (|x| < 1 while turning)
uniform float uLean;       // body lean, radians (+ = top forward)
uniform vec4  uHead;       // offset x, offset y, nod angle, head flip (-1 = looking back / tucked)
uniform float uTurn;       // 0 profile .. 1 looking straight at you
uniform float uHang;       // 0 perched .. 1 hanging upside-down under the branch
uniform float uFluff;      // 0 .. 1 puffed feathers
uniform float uCrest;      // 0 down .. 1 raised
uniform vec2  uWing;       // open 0..1, fan spread 0..1
uniform float uBeak;       // 0 closed .. 1 wide open
uniform vec3  uEyeS;       // open 0..1, mode (0 normal, 1 happy, 2 asleep, 3 pinning), look up/down
uniform vec2  uLegs;       // raise near foot, raise far foot (0..1)
uniform float uTailSway;
uniform float uSquash;
uniform float uSpecies;    // 0 ringneck, 1 budgie, 2 cockatiel, 3 lovebird
uniform vec3  uBody;
uniform vec3  uBelly;
uniform vec3  uHeadC;
uniform vec3  uFaceC;
uniform vec3  uWingC;
uniform vec3  uEdgeC;
uniform vec3  uTailC;
uniform vec3  uBeakC;
uniform vec4  uMark;       // marking colour (cheek / ring / rump), strength
uniform vec3  uAmb;        // world light
uniform vec3  uRim;
uniform vec3  uGlow;
uniform vec4  uBranch;     // y at x = 0 (uv), radius, sway, half width of screen
uniform vec4  uSeed;       // x, y (char space), visible, cracked
uniform vec4  uMillet;     // visible, battery level, sway, -
uniform vec4  uFx[6];      // x, y, life, kind: 1 heart, 2 sparkle, 3 note, 4 husk, 5 zzz, 6 "!", 7 feather, 8 "?"
uniform float uDim;

const float PI = 3.14159265;

float sdEllipse(vec2 p, vec2 r) { float k0 = length(p / r); float k1 = length(p / (r * r)); return k0 * (k0 - 1.0) / max(k1, 1e-4); }
float sdRoundBox(vec2 p, vec2 b, float r) { vec2 q = abs(p) - b + r; return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r; }
float sdSeg(vec2 p, vec2 a, vec2 b) { vec2 pa = p - a, ba = b - a; float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0); return length(pa - ba * h); }
float sdCapsule(vec2 p, vec2 a, vec2 b, float ra, float rb) { vec2 pa = p - a, ba = b - a; float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0); return length(pa - ba * h) - mix(ra, rb, h); }
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
float fillSoft(float d) { return clamp(0.5 - d / (uPx * 2.4), 0.0, 1.0); }
float feathery(float d, vec2 p, float amt) {
    if (abs(d) > 0.05) return d;
    float n = vnoise(p * 55.0) * 0.65 + vnoise(p * 120.0) * 0.35;
    return d + (n - 0.45) * (0.012 + 0.02 * uFluff) * amt * smoothstep(0.05, 0.0, abs(d));
}

// Glossy feathers: soft wrap light, a sheen band, fine barbs along `dir`, rim light.
vec3 shade(vec3 base, vec2 p, vec2 c, vec2 r, vec2 dir) {
    vec2 q = (p - c) / r;
    float t = clamp(1.0 - dot(q, q), 0.0, 1.0);
    vec3 n = normalize(vec3(q * 0.85, sqrt(t) + 0.25));
    vec3 L = normalize(vec3(-0.4, 0.7, 0.75));
    float diff = smoothstep(0.0, 1.0, clamp((dot(n, L) + 0.5) / 1.5, 0.0, 1.0));
    vec3 col = mix(base * vec3(0.42, 0.45, 0.55), base, diff);
    float spec = pow(max(dot(reflect(-L, n), vec3(0.0, 0.0, 1.0)), 0.0), 18.0);
    col += vec3(1.0) * spec * 0.18;
    vec2 perp = vec2(-dir.y, dir.x);
    col *= 0.94 + 0.09 * vnoise(vec2(dot(p, perp) * 260.0, dot(p, dir) * 24.0));
    col *= mix(vec3(1.0), uAmb, 0.55);
    col += uRim * pow(1.0 - n.z, 2.6) * 0.5;
    return col;
}

// Overlapping feather rows: returns 0 inside a feather, 1 on its dark edge.
float scallops(vec2 q, float scale) {
    q *= scale;
    float row = floor(q.y);
    vec2 g = vec2(q.x + 0.5 * mod(row, 2.0), q.y);
    vec2 f = fract(g) - vec2(0.5, 1.0);
    float l = length(f * vec2(1.0, 0.9));
    return smoothstep(0.4, 0.47, l) * (1.0 - smoothstep(0.5, 0.58, l));
}

// ---------------- the branch (screen space) ----------------
float branchY(float x) { return uBranch.x + 0.022 * sin(x * 2.3 + 0.6) + 0.035 * x + uBranch.z; }
float branchR(float x) { return uBranch.y * (1.0 - 0.18 * clamp(x / max(uBranch.w, 0.1), -1.0, 1.0)); }

void drawLeaf(inout vec4 acc, vec2 q, float ang, float len) {
    vec2 l = rot(q, -ang);
    float leaf = sdEllipse(l - vec2(len * 0.5, 0.0), vec2(len * 0.5, len * 0.18));
    if (leaf > 0.02) return;
    vec3 g = mix(vec3(0.1, 0.32, 0.12), vec3(0.3, 0.6, 0.22), smoothstep(-len * 0.18, len * 0.18, l.y));
    g = mix(g * uAmb, g, 0.15);
    float vein = abs(l.y) - 0.0025;
    over(acc, g, clamp(0.5 - leaf / (uPx * uSize * 1.2), 0.0, 1.0));
    over(acc, g * 1.4, clamp(0.5 - vein / (uPx * uSize), 0.0, 1.0) * step(leaf, 0.0) * 0.6);
}

void drawBranch(inout vec4 acc, vec2 uv) {
    float x = uv.x;
    float y0 = branchY(x), r = branchR(x);
    float d = abs(uv.y - y0) - r;
    vec2 pxw = vec2(uPx * uSize);
    // A twig forks up to the right, another droops on the left.
    vec2 fa = vec2(uBranch.w * 0.45, branchY(uBranch.w * 0.45));
    float twig = sdCapsule(uv, fa, fa + vec2(0.28, 0.22), r * 0.7, r * 0.25);
    vec2 fb = vec2(-uBranch.w * 0.55, branchY(-uBranch.w * 0.55));
    float twig2 = sdCapsule(uv, fb, fb + vec2(-0.2, -0.16), r * 0.6, r * 0.2);
    float all = min(min(d, twig), twig2);
    if (all < 0.06) {
        // Bark: lit from above, rough grooves along the branch, a knot.
        float t = clamp((uv.y - y0) / r, -1.0, 1.0);
        vec3 bark = vec3(0.36, 0.25, 0.17);
        vec3 col = mix(bark * 0.35, bark * 1.15, smoothstep(-1.0, 0.7, t));
        float grooves = vnoise(vec2(uv.x * 26.0, uv.y * 140.0)) * 0.6 + vnoise(vec2(uv.x * 70.0, uv.y * 400.0)) * 0.4;
        col *= 0.75 + 0.45 * grooves;
        float knot = length((uv - vec2(-uBranch.w * 0.2, y0)) * vec2(1.0, 1.8)) - r * 0.55;
        col = mix(col, bark * 0.35, smoothstep(0.004, -0.004, abs(knot) - 0.004) * 0.8);
        // Moss on the top.
        float moss = smoothstep(0.55, 0.75, vnoise(vec2(uv.x * 9.0, 3.0))) * smoothstep(0.3, 0.9, t);
        col = mix(col, vec3(0.35, 0.5, 0.18) * (0.7 + 0.5 * vnoise(uv * 300.0)), moss * 0.85);
        col *= mix(vec3(1.0), uAmb, 0.7);
        col += uRim * pow(max(t, 0.0), 6.0) * 0.12;
        float a = clamp(0.5 - all / (pxw.x * 1.2), 0.0, 1.0);
        over(acc, col, a);
    }
    // Leaves near the twigs.
    for (int i = 0; i < 4; i++) {
        float fi = float(i);
        vec2 base = fa + vec2(0.28, 0.22) * (0.35 + 0.22 * fi);
        float sway = 0.12 * sin(uTime * 1.3 + fi * 1.7) + uBranch.z * 3.0;
        drawLeaf(acc, uv - base, (mod(fi, 2.0) < 0.5 ? 0.9 : -0.5) + sway, 0.13 - 0.015 * fi);
    }
    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        vec2 base = fb + vec2(-0.2, -0.16) * (0.4 + 0.25 * fi);
        float sway = 0.1 * sin(uTime * 1.1 + fi * 2.3);
        drawLeaf(acc, uv - base, (mod(fi, 2.0) < 0.5 ? 3.6 : 4.4) + sway, 0.12);
    }
}

void main() {
    vec4 acc = vec4(0.0);
    float S = uSpecies;
    float ring = 1.0 - step(0.5, S);                 // ringneck
    float budgie = step(0.5, S) * (1.0 - step(1.5, S));
    float tiel = step(1.5, S) * (1.0 - step(2.5, S));
    float love = step(2.5, S);

    // Character space: hang (swing around the grip), facing flip, squash from the feet.
    vec2 sp = (vUv - uPos) / uSize;
    vec2 G = vec2(0.0, -0.5);
    vec2 p = sp;
    vec2 pivot = G - vec2(0.0, uBranch.y / uSize);
    p = rot(p - pivot, PI * uHang) + pivot;
    float face = abs(uFace) < 0.15 ? 0.15 * (uFace < 0.0 ? -1.0 : 1.0) : uFace;
    p.x /= face;
    p.y = (p.y - G.y) / uSquash + G.y;
    p.x *= sqrt(uSquash);
    // Body lean around the hip.
    vec2 hip = vec2(0.0, -0.4);
    vec2 b = rot(p - hip, uLean) + hip;
    float fl = 1.0 + 0.16 * uFluff;
    bool near = length(b - vec2(0.0, -0.15)) < 1.25;

    // ---- Tail: behind the branch ----
    float tailLen = 0.86 * ring + 0.5 * budgie + 0.62 * tiel + 0.22 * love;
    if (near) {
        vec2 tb = vec2(-0.09, -0.43);
        vec2 q = rot(b - tb, -uTailSway);
        vec2 dir = normalize(vec2(-0.22, -1.0));
        vec2 tip = dir * tailLen;
        float r0 = 0.065 * (1.0 - love * 0.0), r1 = mix(0.012, 0.055, love) + 0.02 * tiel;
        float tail = sdCapsule(q, vec2(0.0), tip, r0, r1);
        if (tail < 0.04) {
            float along = clamp(dot(q, dir) / tailLen, 0.0, 1.0);
            vec3 tc = mix(uWingC, uTailC, smoothstep(0.1, 0.5, along));
            // Central feathers and a darker shaft.
            float shaft = abs(dot(q, vec2(-dir.y, dir.x))) - 0.004;
            tc = mix(tc, tc * 0.55, smoothstep(0.004, -0.004, shaft) * 0.7);
            tc = mix(tc, uEdgeC, budgie * 0.4 * step(0.5, fract(along * 7.0)));
            over(acc, shade(tc, q, tip * 0.5, vec2(0.08, tailLen * 0.55), dir), fillSoft(feathery(tail, q, 0.6)));
        }
    }

    // ---- Branch ----
    drawBranch(acc, vUv);

    // ---- Charging: a glowing millet spray hanging from the branch ----
    if (uMillet.x > 0.01) {
        vec2 mq = sp - vec2(0.45, -0.52);
        mq = rot(mq, -uMillet.z);
        float stalk = sdSeg(mq, vec2(0.0, 0.0), vec2(0.0, -0.42)) - 0.007;
        over(acc, vec3(0.55, 0.45, 0.25) * uAmb, fill(stalk) * uMillet.x);
        for (int i = 0; i < 9; i++) {
            float fi = float(i);
            vec2 c = vec2(0.03 * sin(fi * 2.3), -0.08 - fi * 0.038);
            float lit = step(fi / 9.0, uMillet.y);
            float seed = sdEllipse(mq - c, vec2(0.03 - fi * 0.0012, 0.022));
            vec3 sc = mix(vec3(0.85, 0.75, 0.4) * uAmb, uGlow * 1.2, lit * 0.8);
            over(acc, sc * (0.8 + 0.3 * vnoise(mq * 200.0)), fill(seed) * uMillet.x);
            over(acc, uGlow, exp(-max(seed, 0.0) * 40.0) * 0.25 * lit * uMillet.x);
        }
    }

    if (near) {
    // ---- Wing behind (far side), visible only when flapping ----
    vec2 sh = vec2(-0.02, 0.02);
    float wo = uWing.x;
    if (wo > 0.05) {
        vec2 q = rot(b - sh, 2.35 * wo - 0.25);
        q.x = -q.x;
        float far = sdEllipse(q - vec2(-0.02, -0.2), vec2(0.11, 0.26));
        over(acc, shade(uWingC * 0.7, q, vec2(0.0, -0.2), vec2(0.13, 0.3), vec2(0.0, -1.0)), fillSoft(feathery(far, q, 0.8)) * smoothstep(0.05, 0.3, wo));
    }

    // ---- Legs + body ----
    float legL = uLegs.x, legR = uLegs.y;
    vec2 bc = vec2(-0.03, -0.19);
    vec2 br = vec2(0.185, 0.27) * fl;
    br.x *= 1.0 + 0.08 * love;
    float body = sdEllipse(rot(b - bc, -0.12), br);
    // Head position (with nod/bob offsets), neck blends into the body.
    vec2 hc = vec2(0.055, 0.17) + uHead.xy;
    float hr = 0.172 * (1.0 + 0.08 * uFluff) * (1.0 + 0.06 * love);
    float headD = sdEllipse(b - hc, vec2(hr * 1.02, hr * 0.95));
    float trunk = smin(body, headD, 0.09);
    if (trunk < 0.1) {
        float d = feathery(trunk, b, 1.2);
        vec3 col = shade(uBody, b, bc + vec2(0.0, 0.08), br * 1.25, vec2(0.0, -1.0));
        // Lighter belly on the front.
        float belly = smoothstep(-0.02, 0.12, b.x - bc.x + 0.05 * (b.y - bc.y));
        col = mix(col, shade(uBelly, b, bc, br * 1.25, vec2(0.0, -1.0)), belly * 0.65);
        // Lovebird: blue rump above the tail.
        float rump = smoothstep(0.08, 0.0, length((b - vec2(-0.15, -0.33)) * vec2(1.2, 1.0)) - 0.05);
        col = mix(col, shade(uMark.rgb, b, bc, br * 1.25, vec2(0.0, -1.0)), rump * love * uMark.a);
        // Budgie barring on the back of the head and neck.
        float bars = smoothstep(0.55, 0.85, sin((b.y + 0.02 * sin(b.x * 40.0)) * 150.0)) * smoothstep(0.0, -0.08, b.x - hc.x + 0.02) * smoothstep(-0.02, 0.06, b.y);
        col = mix(col, uEdgeC * 0.5, bars * budgie * 0.85);
        over(acc, col, fillSoft(d));
    }

    // ---- Wing (near side) ----
    {
        vec2 q = rot(b - sh, 2.35 * wo);
        float spread = uWing.y;
        float wing = sdEllipse(rot(q - vec2(-0.06, -0.17), 0.22), vec2(0.115, 0.25));
        float prim = sdCapsule(q, vec2(-0.08, -0.25), vec2(-0.16, -0.55), 0.06, 0.012);
        // Fanned primaries when open.
        float fan = 1e5;
        if (spread > 0.05) {
            for (int i = 0; i < 5; i++) {
                float fi = float(i);
                vec2 dir = rot(vec2(-0.25, -1.0), (fi - 2.0) * 0.18 * spread);
                fan = min(fan, sdCapsule(q, vec2(-0.06, -0.2), vec2(-0.06, -0.2) + normalize(dir) * (0.42 - abs(fi - 2.0) * 0.03), 0.04, 0.012));
            }
        }
        float w = min(min(wing, prim), fan);
        if (w < 0.05) {
            float d = feathery(w, q, 0.5);
            vec3 col = shade(uWingC, q, vec2(-0.06, -0.2), vec2(0.15, 0.35), vec2(-0.25, -1.0));
            // Feather rows: coverts scalloped, flight feathers in long strips.
            float sc = scallops(q - vec2(0.0, 0.03), 22.0);
            float coverts = smoothstep(-0.32, -0.22, q.y);
            col = mix(col, uEdgeC * mix(vec3(1.0), uAmb, 0.5), sc * coverts * (0.35 + 0.55 * budgie));
            float strips = smoothstep(0.75, 0.95, sin(dot(q, vec2(1.0, -0.25)) * 90.0));
            col = mix(col, col * 0.6, strips * (1.0 - coverts) * 0.6);
            // Cockatiel white wing bar.
            float bar = smoothstep(0.03, 0.0, abs(q.x + 0.01 + 0.15 * (q.y + 0.1)) - 0.02) * smoothstep(-0.32, -0.12, q.y) * smoothstep(0.02, -0.06, q.y);
            col = mix(col, vec3(0.95) * mix(vec3(1.0), uAmb, 0.5), bar * tiel);
            // Dark flight feather tips.
            col = mix(col, col * 0.45, smoothstep(-0.38, -0.5, q.y) * 0.6);
            over(acc, col, fillSoft(d));
        }
    }

    // ---- Head ----
    if (length(b - hc) < 0.42) {
        float flip = uHead.w;
        vec2 h = b - hc;
        h = rot(h, -uHead.z);
        h.x *= flip;
        float turn = uTurn;
        // Fluffed head feathers.
        float hd = sdEllipse(h, vec2(hr * 1.02, hr * 0.95));
        float crown = hd + (vnoise(h * 60.0) - 0.4) * 0.03 * smoothstep(0.0, 0.12, h.y) * max(uCrest * (1.0 - tiel), uFluff);
        // Cockatiel crest: a few long curved feathers.
        if (tiel > 0.5) {
            vec2 a = vec2(-0.01, hr * 0.85);
            float ang = mix(-1.25, -0.15, uCrest);
            float cbest = 1e5;
            for (int k = 0; k < 4; k++) {
                float fk = float(k);
                vec2 aa = a + vec2(-0.02 * fk, -0.01 * fk);
                vec2 c1 = aa + rot(vec2(0.0, 0.12 + 0.02 * fk), ang - 0.12 * fk);
                vec2 c2 = c1 + rot(vec2(0.0, 0.1), ang - 0.12 * fk + 0.45);
                cbest = min(cbest, min(sdCapsule(h, aa, c1, 0.016, 0.012), sdCapsule(h, c1, c2, 0.012, 0.004)));
            }
            over(acc, shade(uFaceC, h, vec2(0.0, 0.2), vec2(0.15, 0.2), vec2(0.0, 1.0)), fillSoft(cbest));
        }
        // Small head crest raise for the others.
        if (tiel < 0.5 && uCrest > 0.05) {
            for (int k = 0; k < 3; k++) {
                float fk = float(k);
                vec2 aa = vec2(-0.03 - 0.035 * fk, hr * 0.85 - 0.02 * fk);
                float t = sdCapsule(h, aa, aa + rot(vec2(0.0, 0.07), -0.5 - 0.25 * fk) * uCrest, 0.018, 0.004);
                over(acc, shade(uHeadC, h, vec2(0.0), vec2(0.17), vec2(0.0, 1.0)), fillSoft(t));
            }
        }
        {
            vec3 col = shade(uHeadC, h, vec2(0.02, 0.02), vec2(hr * 1.2), normalize(h + vec2(0.001)));
            // Face colour: lovebird red face fading down the neck, cockatiel/budgie yellow face.
            float faceMask = smoothstep(-0.06, 0.06, h.x + 0.05 * turn + 0.4 * (h.y + 0.02) * (1.0 - turn));
            faceMask = mix(faceMask, smoothstep(0.17, 0.05, length(h * vec2(1.0, 1.1))), turn);
            col = mix(col, shade(uFaceC, h, vec2(0.02, 0.02), vec2(hr * 1.2), vec2(0.0, -1.0)), faceMask * (1.0 - ring) );
            over(acc, col, fillSoft(feathery(crown, h, 1.0)));
        }
        float inHead = fill(hd + 0.006);

        // Cheek patch (cockatiel orange, budgie violet).
        {
            vec2 cp = mix(vec2(0.035, -0.045), vec2(0.075, -0.05), turn);
            float ch = sdEllipse(h - cp, vec2(0.042, 0.038) * (1.0 - 0.3 * budgie));
            if (tiel + budgie > 0.5) over(acc, uMark.rgb * mix(vec3(1.0), uAmb, 0.4), fillSoft(ch) * inHead * uMark.a);
            if (turn > 0.3 && tiel + budgie > 0.5) {
                float ch2 = sdEllipse(h - vec2(-cp.x, cp.y), vec2(0.042, 0.038) * (1.0 - 0.3 * budgie));
                over(acc, uMark.rgb * mix(vec3(1.0), uAmb, 0.4), fillSoft(ch2) * inHead * uMark.a * smoothstep(0.3, 0.7, turn));
            }
        }
        // Budgie throat spots.
        if (budgie > 0.5) {
            for (int k = 0; k < 3; k++) {
                float fk = float(k);
                vec2 c = mix(vec2(0.1 - fk * 0.035, -0.125 + fk * 0.008), vec2(-0.05 + fk * 0.05, -0.135), turn);
                over(acc, vec3(0.05), fill(length(h - c) - 0.011) * inHead);
            }
        }
        // Ringneck: black chin stripe and rose neck ring.
        if (ring > 0.5) {
            // Angles from straight down: + towards the beak. Black runs chin -> throat -> nape, rose collar behind it.
            float ang = atan(h.x, -h.y);
            float rr = length(h);
            float blackC = mix(0.15, 0.0, turn);
            float blackW = mix(1.05, 0.9, turn);
            float r1 = max(abs(rr - hr * 0.98) - 0.011, (abs(ang - blackC) - blackW) * 0.15);
            float r2 = max(abs(rr - hr * 1.1) - 0.012, (abs(ang + 1.05) - 0.6) * 0.15);
            over(acc, vec3(0.03), fill(r1) * uMark.a);
            over(acc, uMark.rgb * mix(vec3(1.0), uAmb, 0.4), fill(r2) * uMark.a * (1.0 - turn));
        }

        // Eyes: one in profile, both when turned to you.
        for (int s = 0; s < 2; s++) {
            float side = s == 0 ? 1.0 : -1.0;
            float vis = s == 0 ? 1.0 : smoothstep(0.35, 0.75, turn);
            if (vis < 0.01) continue;
            vec2 ec = s == 0 ? mix(vec2(0.065, 0.03), vec2(0.075, 0.03), turn) : vec2(-0.075, 0.03);
            vec2 e = h - ec;
            float r = 0.046 + 0.004 * love;
            float eyeD = length(e) - r;
            // Eye ring: lovebird big white, ringneck orange, budgie pale.
            float ringW = 0.014 * love + 0.006 * ring + 0.004 * budgie;
            vec3 ringC = mix(mix(vec3(1.0, 0.65, 0.3), vec3(0.95), love + budgie), vec3(0.95), love);
            if (ringW > 0.0) over(acc, ringC * mix(vec3(1.0), uAmb, 0.5), fill(eyeD - ringW) * inHead * vis);
            float mode = uEyeS.y;
            if (mode < 0.5 || mode > 2.5) {
                float open = uEyeS.x;
                // Eyelid closes from the top.
                float lid = e.y - mix(-r - 0.01, r + 0.01, open);
                float eyeIn = max(eyeD, lid);
                vec3 iris = mix(vec3(0.02), vec3(0.95, 0.85, 0.6) * 0.8, budgie * 0.6);
                over(acc, vec3(0.03, 0.02, 0.03), fill(eyeIn) * inHead * vis);
                // Pupil and iris ring (pinning shrinks the pupil).
                float pupR = mix(0.026, 0.012, step(2.5, mode));
                vec2 look = vec2(0.006 * (1.0 - turn), 0.008 * uEyeS.z);
                float irisRing = abs(length(e - look) - pupR - 0.006) - 0.004;
                over(acc, mix(vec3(0.35, 0.2, 0.08), vec3(0.95, 0.9, 0.75), budgie + step(2.5, mode) * 0.6), fill(max(irisRing, eyeIn)) * inHead * vis * 0.9);
                if (open > 0.35) {
                    float hl = length(e - look - vec2(-0.013, 0.014)) - 0.012;
                    float hl2 = length(e - look - vec2(0.012, -0.012)) - 0.005;
                    over(acc, vec3(1.0), (fill(hl) + fill(hl2) * 0.7) * inHead * vis);
                }
                // Eyelid line.
                if (open < 0.95) over(acc, shade(uHeadC, h, vec2(0.0), vec2(0.17), vec2(0.0, 1.0)), fill(max(eyeD, -lid)) * inHead * vis);
            } else if (mode < 1.5) {
                float ln = sdArc(e + vec2(0.0, 0.012), 0.03, 1.1) - 0.006;
                over(acc, vec3(0.05), fill(ln) * inHead * vis);
            } else {
                float ln = sdArc(e * vec2(1.0, -1.0) + vec2(0.0, 0.016), 0.032, 1.0) - 0.005;
                over(acc, vec3(0.05), fill(ln) * inHead * vis);
            }
        }

        // Beak: hooked upper mandible, lower mandible hinges open.
        {
            float bs = 1.15 * ring + 0.72 * budgie + 0.82 * tiel + 1.0 * love;
            vec2 bpos = mix(vec2(0.14, -0.025), vec2(0.0, -0.05), turn);
            vec2 q = (h - bpos) / bs;
            q.x = mix(q.x, q.x * 1.25, turn);
            float open = uBeak;
            // Lower mandible.
            vec2 lq = rot(q - vec2(-0.03, -0.025), 0.55 * open) + vec2(-0.03, -0.025);
            float lower = sdEllipse(lq - vec2(-0.005, -0.045), vec2(0.042, 0.03));
            // Mouth inside.
            if (open > 0.05) {
                float mouth = sdEllipse(q - vec2(0.0, -0.035 - 0.02 * open), vec2(0.04, 0.03 * open + 0.005));
                over(acc, vec3(0.25, 0.1, 0.12), fill(mouth) * inHead);
                over(acc, vec3(0.3, 0.25, 0.28), fill(sdEllipse(q - vec2(0.0, -0.045 - 0.01 * open), vec2(0.022, 0.012))) * inHead);
            }
            vec3 lowC = mix(uBeakC * 0.75, vec3(0.15), ring * 0.7);
            over(acc, shade(lowC, q, vec2(0.0, -0.04), vec2(0.06), vec2(1.0, 0.0)), fill(lower));
            // Upper: a crescent. Outer disc for the curved top, an inner disc carves the underside,
            // which leaves a hook that curls down in front of the lower mandible.
            vec2 uq = rot(q - vec2(-0.03, -0.01), -0.12 * open) + vec2(-0.03, -0.01);
            float outer = length(uq - vec2(-0.025, -0.045)) - 0.085;
            float inner = length(uq - vec2(-0.045, -0.095)) - 0.075;
            float upper = max(max(outer, -inner), -(uq.x + 0.045));
            upper = max(upper, uq.y - 0.04 - 0.02 * love);
            upper = smin(upper, sdEllipse(uq - vec2(-0.02, 0.0), vec2(0.035, 0.035)), 0.02);
            vec3 bc2 = shade(uBeakC, uq, vec2(-0.01, 0.0), vec2(0.08, 0.07), vec2(1.0, -0.6));
            bc2 = mix(bc2, bc2 * 0.55, smoothstep(0.03, 0.07, uq.x) * 0.5);
            over(acc, bc2, fill(upper));
            over(acc, vec3(1.0), fill(sdSeg(uq, vec2(-0.015, 0.025), vec2(0.02, 0.018)) - 0.004) * 0.45);
            // Cere (budgie) or nostril.
            float cere = sdEllipse(uq - vec2(-0.03, 0.035), vec2(0.03, 0.016));
            over(acc, mix(uMark.rgb, vec3(0.45, 0.6, 1.0), budgie) * mix(vec3(1.0), uAmb, 0.4), fill(cere) * budgie);
            over(acc, vec3(0.1), fill(length(uq - vec2(-0.02, 0.03)) - 0.005) * (1.0 - budgie) * (1.0 - turn));
        }
    }

    // ---- Feet gripping the branch (in front of it) ----
    for (int s = 0; s < 2; s++) {
        float fs = float(s);
        float raise = s == 0 ? legL : legR;
        vec2 fp = vec2(s == 0 ? 0.05 : -0.045, -0.5);
        vec3 skin = mix(vec3(0.62, 0.55, 0.58), vec3(0.75, 0.6, 0.55), tiel) * mix(vec3(1.0), uAmb, 0.5);
        if (raise > 0.5) {
            // Tucked up in the belly feathers: a little curled foot.
            vec2 c = fp + vec2(0.01, 0.16);
            float ball = length(p - c) - 0.028;
            over(acc, skin * 0.9, fill(ball) * (1.0 - fs * 0.4));
            continue;
        }
        float leg = sdCapsule(p, fp + vec2(-0.005, 0.07), fp + vec2(0.0, 0.01), 0.024, 0.02);
        over(acc, skin * 0.85, fill(leg) * (1.0 - fs * 0.35));
        for (int k = 0; k < 2; k++) {
            float fk = float(k);
            vec2 a = fp + vec2(0.0, 0.005);
            vec2 t1 = a + vec2((fk - 0.5) * 0.05, -0.02);
            vec2 t2 = t1 + vec2((fk - 0.5) * 0.02 + 0.01, -0.045);
            float toe = min(sdCapsule(p, a, t1, 0.017, 0.014), sdCapsule(p, t1, t2, 0.014, 0.009));
            vec3 sc = skin * (0.85 + 0.25 * step(0.5, fract(dot(p, vec2(0.3, 1.0)) * 90.0)));
            over(acc, sc * (1.0 - fs * 0.25), fill(toe));
            over(acc, vec3(0.12), fill(length(p - t2 - vec2(0.0, -0.008)) - 0.006));
        }
    }
    }

    // ---- Seed (offered by a long press) ----
    if (uSeed.z > 0.01) {
        vec2 q = rot(sp - uSeed.xy, 0.5);
        if (uSeed.w < 0.5) {
            float sd = sdEllipse(q, vec2(0.045, 0.026)) - 0.01 * max(q.x, 0.0);
            vec3 sc = mix(vec3(0.08), vec3(0.95, 0.93, 0.85), step(0.5, fract(q.y * 45.0 + 0.3)));
            over(acc, sc, fill(sd) * uSeed.z);
            over(acc, uGlow, exp(-max(sd, 0.0) * 30.0) * 0.4 * uSeed.z);
        } else {
            float k = sdEllipse(q, vec2(0.03, 0.017));
            over(acc, vec3(0.95, 0.88, 0.7), fill(k) * uSeed.z);
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
            over(acc, vec3(1.0, 0.4, 0.6), fill(sdHeart(q / 0.05 + vec2(0.0, 0.5)) * 0.05) * fade);
        } else if (fx.w < 2.5) {
            float r = 0.045 * (1.0 - abs(life - 0.5));
            float st = min(sdSeg(q, vec2(-r, 0.0), vec2(r, 0.0)), sdSeg(q, vec2(0.0, -r), vec2(0.0, r))) - 0.004;
            over(acc, vec3(1.0, 0.95, 0.75), (fill(st) + exp(-length(q) * 70.0) * 0.6) * fade);
        } else if (fx.w < 3.5) {
            vec2 nq = rot(q, 0.25 * sin(life * 12.0));
            float head = sdEllipse(rot(nq, 0.4), vec2(0.022, 0.016));
            float stem = sdSeg(nq, vec2(0.019, 0.005), vec2(0.019, 0.07)) - 0.004;
            float flag = sdSeg(nq, vec2(0.019, 0.07), vec2(0.045, 0.045)) - 0.005;
            vec3 nc = 0.65 + 0.35 * cos(6.28 * (float(i) * 0.17 + vec3(0.0, 0.33, 0.67)));
            over(acc, nc, fill(min(min(head, stem), flag)) * fade);
            over(acc, nc, exp(-length(q) * 40.0) * 0.3 * fade);
        } else if (fx.w < 4.5) {
            // Seed husk bits flying off.
            for (int k = 0; k < 3; k++) {
                float fk = float(k);
                vec2 o = vec2((fk - 1.0) * 0.08, 0.04 * fk) * life * 2.0 + vec2(0.0, -0.3 * life * life);
                float bit = sdEllipse(rot(q - o, life * 9.0 + fk), vec2(0.016, 0.008));
                over(acc, mix(vec3(0.1), vec3(0.9), mod(fk, 2.0)), fill(bit) * fade);
            }
        } else if (fx.w < 5.5) {
            vec2 z = q / (0.03 + life * 0.02);
            float dz = min(min(sdSeg(z, vec2(-0.5, 0.5), vec2(0.5, 0.5)), sdSeg(z, vec2(0.5, 0.5), vec2(-0.5, -0.5))), sdSeg(z, vec2(-0.5, -0.5), vec2(0.5, -0.5)));
            over(acc, vec3(0.8, 0.85, 1.0), fill(dz * (0.03 + life * 0.02) - 0.004) * fade * 0.8);
        } else if (fx.w < 6.5) {
            float bar = sdRoundBox(q - vec2(0.0, 0.035), vec2(0.01, 0.035), 0.008);
            float dotv = length(q + vec2(0.0, 0.025)) - 0.011;
            over(acc, vec3(1.0, 0.85, 0.3), fill(min(bar, dotv)) * fade);
        } else if (fx.w < 7.5) {
            // A small feather seesawing down.
            vec2 fq = rot(q, 0.6 * sin(life * 8.0));
            float fe = sdEllipse(fq, vec2(0.012, 0.04));
            float qu = sdSeg(fq, vec2(0.0, -0.05), vec2(0.0, 0.035)) - 0.002;
            over(acc, uWingC * 1.2, fill(fe) * fade);
            over(acc, vec3(0.9), fill(qu) * fade * 0.7);
        } else {
            float hook = abs(length(q - vec2(0.0, 0.035)) - 0.02) - 0.006;
            hook = max(hook, -(q.x + q.y * 0.2) - 0.035);
            float stem = sdSeg(q, vec2(0.0, 0.015), vec2(0.0, -0.005)) - 0.006;
            float dotv = length(q + vec2(0.0, 0.025)) - 0.008;
            over(acc, vec3(0.75, 0.85, 1.0), fill(min(min(hook, stem), dotv)) * fade);
        }
    }

    acc.rgb *= mix(1.0, 0.55, uDim);
    gl_FragColor = acc;
}
