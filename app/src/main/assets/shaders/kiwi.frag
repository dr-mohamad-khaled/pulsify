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
uniform vec2  uWingPull;   // left / right wing pulled open by a finger (0..1)
uniform float uBeak;       // 0 closed .. 1 wide open
uniform vec3  uEyeS;       // open 0..1, mode (0 normal, 1 happy, 2 asleep, 3 pinning), look up/down
uniform vec2  uLegs;       // raise near foot, raise far foot (0..1)
uniform vec2  uFeet;       // sideways offset of near / far foot (char space) while stepping
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
float fillSoft(float d) { return clamp(0.5 - d / (uPx * 2.4), 0.0, 1.0); }
float feathery(float d, vec2 p, float amt) {
    if (abs(d) > 0.05) return d;
    float n = vnoise(p * 55.0) * 0.65 + vnoise(p * 120.0) * 0.35;
    return d + (n - 0.45) * (0.011 + 0.006 * uFluff) * amt * smoothstep(0.05, 0.0, abs(d));
}

// Glossy feathers: soft wrap light, a sheen band, fine barbs along `dir`, rim light.
vec3 shade(vec3 base, vec2 p, vec2 c, vec2 r, vec2 dir) {
    vec2 q = (p - c) / r;
    float t = clamp(1.0 - dot(q, q), 0.0, 1.0);
    vec3 n = normalize(vec3(q * 0.85, sqrt(t) + 0.25));
    vec3 L = normalize(vec3(-0.4, 0.7, 0.75));
    float diff = smoothstep(0.0, 1.0, clamp((dot(n, L) + 0.5) / 1.5, 0.0, 1.0));
    vec3 col = mix(base * vec3(0.48, 0.52, 0.72), base * 1.03, diff);
    float spec = pow(max(dot(reflect(-L, n), vec3(0.0, 0.0, 1.0)), 0.0), 18.0);
    col += vec3(1.0) * spec * 0.18;
    vec2 perp = vec2(-dir.y, dir.x);
    col *= 0.96 + 0.06 * vnoise(vec2(dot(p, perp) * 60.0, dot(p, dir) * 14.0));
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

// ---------------- 3D helpers ----------------
// Head rotation: cs = (cos yaw, sin yaw, cos pitch, sin pitch). Head space: +z beak, +y up.
vec3 toView(vec3 v, vec4 cs) {
    vec3 a = vec3(v.x, v.y * cs.z - v.z * cs.w, v.y * cs.w + v.z * cs.z);
    return vec3(a.x * cs.x + a.z * cs.y, a.y, -a.x * cs.y + a.z * cs.x);
}
vec3 toHead(vec3 n, vec4 cs) {
    vec3 t = vec3(n.x * cs.x - n.z * cs.y, n.y, n.x * cs.y + n.z * cs.x);
    return vec3(t.x, t.y * cs.z + t.z * cs.w, -t.y * cs.w + t.z * cs.z);
}
// Lighting from a real surface normal: soft key light, cool shadows, sheen, rim.
vec3 shade3(vec3 base, vec3 n, vec2 p, float gloss) {
    vec3 L = normalize(vec3(-0.45, 0.62, 0.64));
    float ndl = dot(n, L);
    float diff = smoothstep(-0.45, 0.9, ndl);
    vec3 col = mix(base * vec3(0.4, 0.45, 0.68), base * 1.07, diff);
    col += base * 0.1 * max(-n.y, 0.0);                                   // bounce from below
    vec3 H = normalize(L + vec3(0.0, 0.0, 1.0));
    col += vec3(1.0, 0.98, 0.94) * pow(max(dot(n, H), 0.0), mix(14.0, 60.0, gloss)) * mix(0.1, 0.75, gloss);
    col *= 0.97 + 0.05 * vnoise(p * 34.0) * (1.0 - gloss);
    col *= mix(vec3(1.0), uAmb, 0.55);
    col += uRim * pow(1.0 - n.z, 3.0) * 0.5 * smoothstep(-0.6, 0.4, n.x * 0.6 + n.y);
    return col;
}
vec3 sphN(vec2 q) { float r2 = dot(q, q); if (r2 > 0.97) q *= sqrt(0.97 / r2); return vec3(q, sqrt(max(1.0 - dot(q, q), 0.0))); }

// The beak, modelled like the real thing: a thick arc that leaves the forehead, bulges forward and
// hooks down to a point, made of spheres along a curve in the head's mid-plane. Small lower mandible.
void drawBeak(inout vec4 acc, vec2 q, vec4 cs, float bs, float ring, float budgie, float aa, vec2 p) {
    float open = uBeak;
    vec3 A = vec3(0.0, -0.2, 0.7);                    // centre of the arc, just inside the face
    float rho = 0.3 * bs;
    float up = 1e5;
    vec2 tipXY = vec2(0.0); vec2 midXY = vec2(0.0);
    for (int j = 0; j < 6; j++) {
        float t = float(j) / 5.0;
        float phi = mix(1.25, -1.45, t) - 0.12 * open;
        vec3 c = toView(A + rho * vec3(0.0, sin(phi), cos(phi)), cs);
        float r = mix(0.25, 0.04, pow(t, 1.25)) * bs;
        float d = length(q - c.xy) - r;
        up = j == 0 ? d : smin(up, d, 0.09 * bs);
        if (j == 2) midXY = c.xy;
        if (j == 5) tipXY = c.xy;
    }
    vec3 cl = toView(vec3(0.0, -0.44 - 0.14 * open, 0.76), cs);
    float dl = length((q - cl.xy) * vec2(1.0, 1.2)) - 0.13 * bs;
    if (min(up, dl) > 0.05) return;
    // Lower mandible (dark on a ringneck), the mouth, then the upper mandible over them.
    vec3 lowC = mix(uBeakC * 0.62, vec3(0.2, 0.08, 0.08), ring * 0.8);
    over(acc, shade3(lowC, sphN((q - cl.xy) / (0.14 * bs)), p, 0.4), clamp(0.5 - dl / aa, 0.0, 1.0));
    if (open > 0.08) {
        vec3 cm = toView(vec3(0.0, -0.36 - 0.06 * open, 0.84), cs);
        float dm = length((q - cm.xy) * vec2(1.0, 1.0 / (0.3 + open))) - 0.11 * bs;
        over(acc, vec3(0.28, 0.07, 0.1), clamp(0.5 - dm / aa, 0.0, 1.0));
        over(acc, vec3(0.9, 0.48, 0.52), clamp(0.5 - (length(q - cm.xy + vec2(0.0, 0.05 * open)) - 0.05 * bs) / aa, 0.0, 1.0) * open);
    }
    vec3 n = sphN((q - midXY) / vec2(0.36 * bs, 0.46 * bs));
    // Coral red, warmer orange near the face, darker at the hooked tip.
    vec3 base = mix(uBeakC, uBeakC * vec3(1.05, 1.45, 1.1) + vec3(0.05, 0.08, 0.0), smoothstep(0.25 * bs, 0.6 * bs, length(q - tipXY)) * 0.45);
    vec3 bc = shade3(base, n, p, 0.5);
    bc = mix(bc, bc * vec3(0.42, 0.3, 0.32), smoothstep(0.2 * bs, 0.03 * bs, length(q - tipXY)) * 0.75);
    bc *= 0.88 + 0.12 * smoothstep(0.0, -0.07, up);
    over(acc, bc, clamp(0.5 - up / aa, 0.0, 1.0));
    // Cere: pale skin over the beak (blue on a budgie) with the nostril in it.
    vec3 cc = toView(vec3(0.0, 0.1, 0.97), cs);
    float dc = length((q - cc.xy) * vec2(1.0, 1.6)) - 0.1;
    over(acc, shade3(vec3(0.42, 0.6, 1.0), sphN((q - cc.xy) / 0.12), p, 0.8), clamp(0.5 - dc / aa, 0.0, 1.0) * budgie);
    vec3 nv = toView(vec3(0.08, 0.07, 0.99), cs);
    over(acc, vec3(0.2, 0.08, 0.08), clamp(0.5 - (length(q - nv.xy) - 0.02) / aa, 0.0, 1.0) * step(0.2, nv.z) * (1.0 - budgie));
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
    float face = uFace < 0.0 ? -1.0 : 1.0;
    p.x *= face;
    p.y = (p.y - G.y) / uSquash + G.y;
    p.x *= sqrt(uSquash);
    // Body lean around the hip.
    vec2 hip = vec2(0.0, -0.4);
    vec2 b = rot(p - hip, (uLean - 0.12) * 0.18) + hip;
    b.y += max(uLean - 0.12, 0.0) * 0.12 * smoothstep(-0.45, 0.1, b.y);   // leaning in = head and chest dip
    float fl = 1.0 + 0.16 * uFluff;
    bool near = length(b - vec2(0.0, -0.15)) < 1.25;

    // ---- Tail: behind the branch ----
    float tailLen = 0.86 * ring + 0.5 * budgie + 0.62 * tiel + 0.22 * love;
    if (near) {
        vec2 tb = vec2(0.0, -0.4);
        vec2 q = rot(b - tb, -uTailSway * 0.6);
        vec2 dir = vec2(0.0, -1.0);
        vec2 tip = dir * tailLen;
        float r0 = 0.075 + 0.02 * love, r1 = mix(0.014, 0.065, love) + 0.02 * tiel;
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
    // Front view: a small head on a sleek upright body, wings folded down both sides.
    float wo = uWing.x;
    vec2 br = (vec2(0.165, 0.29) * ring + vec2(0.165, 0.25) * budgie + vec2(0.165, 0.265) * tiel + vec2(0.18, 0.24) * love) * fl;
    vec2 bc = vec2(0.0, -0.46 + br.y / fl);
    float R = (0.14 * ring + 0.145 * budgie + 0.14 * tiel + 0.155 * love) * (1.0 + 0.1 * uFluff);
    float tiltB = 0.0;
    vec2 hc = vec2(0.0, bc.y + br.y / fl * 0.98 + R * 0.45) + uHead.xy;
    // Head rotation: facing you, glancing side to side; all the way round when tucked or preening.
    float fwd = smoothstep(0.0, 1.0, uHead.w * 0.5 + 0.5);
    float glance = 0.32 * sin(uTime * 0.37) * smoothstep(0.3, 0.9, sin(uTime * 0.11) * 0.5 + 0.5);
    float yaw = mix(2.6, glance * (1.0 - uTurn), fwd);
    float pitch = (uHead.z * 0.85 - 0.06) * mix(-0.3, 1.0, fwd);
    vec4 cs = vec4(cos(yaw), sin(yaw), cos(pitch), sin(pitch));
    float bs = 1.3 * ring + 0.8 * budgie + 0.9 * tiel + 1.15 * love;
    vec2 hq = (b - hc) / R;
    float aaH = 1.6 * uPx / R;
    bool beakBehind = toView(vec3(0.0, -0.1, 0.9), cs).z < 0.15;
    if (beakBehind) drawBeak(acc, hq, cs, bs, ring, budgie, aaH, b);

    // ---- Wings: one down each side of the body, behind its front edge. They lift and fan out to fly. ----
    for (int k = 0; k < 2; k++) {
        float s = k == 0 ? -1.0 : 1.0;
        vec2 sh = vec2(s * 0.1, bc.y + br.y * 0.55);
        vec2 q = b - sh; q.x *= s;                       // local space: +x points away from the body
        float pl = k == 0 ? uWingPull.x : uWingPull.y;   // a finger pulling this wing open
        q = rot(q, -2.1 * max(wo, pl * 0.8));            // raise the wing up and out
        q.y /= 1.0 + 0.35 * pl;                          // and stretch it toward the finger
        float spread = max(uWing.y, pl);
        float wl = br.y / 0.29;
        vec2 wcen = vec2(0.035, -0.17 * wl), wr = vec2(0.085, 0.22 * wl);
        float wing = sdEllipse(rot(q - wcen, -0.1), wr);
        float prim = sdCapsule(q, vec2(0.02, -0.25 * wl), vec2(-0.05, -0.56 * wl), 0.05, 0.01);
        float fan = 1e5;
        if (spread > 0.05) {
            for (int i = 0; i < 5; i++) {
                float fi = float(i);
                vec2 dir = rot(vec2(0.15, -1.0), (fi - 2.0) * 0.22 * spread);
                fan = min(fan, sdCapsule(q, vec2(0.03, -0.18), vec2(0.03, -0.18) + normalize(dir) * (0.42 - abs(fi - 2.0) * 0.03), 0.04, 0.012));
            }
        }
        float w = min(min(wing, prim), fan);
        if (w < 0.05) {
            vec3 n = sphN(rot(q - wcen - vec2(0.0, -0.06), -0.1) / vec2(0.13, 0.36 * wl));
            n = normalize(n + vec3(0.35, 0.0, 0.0)); n.x *= s;
            float coverts = smoothstep(-0.3 * wl, -0.18 * wl, q.y);
            vec3 base = mix(uWingC, uBody * 1.05, smoothstep(-0.12, 0.02, q.y) * 0.45 * (1.0 - budgie));
            float sc = scallops(q - vec2(0.0, 0.02), 24.0);
            base = mix(base, uEdgeC, sc * coverts * (0.3 + 0.6 * budgie));
            vec3 flight = mix(uWingC * 0.72, uTailC * 0.8, 0.55);
            float strips = smoothstep(0.6, 0.95, sin(dot(q, vec2(1.0, 0.25)) * 105.0));
            flight = mix(flight, flight * 0.55, strips * 0.6);
            base = mix(flight, base, coverts);
            base = mix(base, base * 0.5, smoothstep(-0.42 * wl, -0.56 * wl, q.y) * 0.55);
            float bar = smoothstep(0.02, 0.0, abs(q.x - 0.06) - 0.016) * smoothstep(-0.26, -0.08, q.y) * smoothstep(0.02, -0.06, q.y);
            base = mix(base, vec3(0.96), bar * tiel);
            over(acc, shade3(base, n, q, 0.12), fillSoft(feathery(w, q, 0.4)));
        }
    }

    // ---- Crest (cockatiel) or a small raised tuft, rooted on top of the head ----
    {
        vec3 root = toView(vec3(0.0, 0.97, -0.12), cs);
        vec2 a = root.xy;
        float back = -cs.y + 0.35;                 // from the front it sweeps up and a little to one side
        float up = mix(0.25, 1.0, uCrest);
        float best = 1e5;
        if (tiel > 0.5) {
            for (int k = 0; k < 4; k++) {
                float fk = float(k);
                vec2 aa2 = a + vec2(back * 0.06 * fk - 0.08 + 0.05 * fk, -0.03 * fk);
                vec2 dir = normalize(vec2(back * (1.25 - up) * 1.1 + back * 0.12 * fk, up + 0.1));
                vec2 c1 = aa2 + dir * (0.6 + 0.1 * fk);
                vec2 c2 = c1 + normalize(dir + vec2(back * 0.9, -0.1)) * 0.45;
                best = min(best, min(sdCapsule(hq, aa2, c1, 0.1, 0.08), sdCapsule(hq, c1, c2, 0.08, 0.025)));
            }
            over(acc, shade3(uFaceC, sphN(vec2(0.0, 0.3)), b, 0.0) * (0.9 + 0.2 * vnoise(hq * 30.0)), clamp(0.5 - best / (aaH * 1.5), 0.0, 1.0));
        } else if (uCrest > 0.08) {
            for (int k = 0; k < 3; k++) {
                float fk = float(k);
                vec2 aa2 = a + vec2((fk - 1.0) * 0.18, -0.03 - 0.03 * abs(fk - 1.0));
                vec2 dir = normalize(vec2((fk - 1.0) * 0.5 + back * 0.3, 1.0));
                best = min(best, sdCapsule(hq, aa2, aa2 + dir * 0.22 * uCrest, 0.09, 0.02));
            }
            over(acc, shade3(mix(uHeadC, uBody, love), sphN(vec2(0.0, 0.4)), b, 0.0), clamp(0.5 - best / (aaH * 1.5), 0.0, 1.0));
        }
    }

    // ---- Body, neck and head as one smooth form ----
    vec3 hN = vec3(0.0); vec3 hH = vec3(0.0); float inHead = 0.0; vec3 headBase = uHeadC;
    {
        vec2 bl = b - bc;
        float bd = sdEllipse(bl, br);
        float hdd = (length(hq) - 1.0) * R;
        float kk = 0.085;
        float hmix = clamp(0.5 + 0.5 * (bd - hdd) / kk, 0.0, 1.0);          // 1 = head, 0 = body
        float d = mix(bd, hdd, hmix) - kk * hmix * (1.0 - hmix);
        if (d < 0.05) {
            vec3 nB = sphN(bl / br);
            hN = sphN(hq);
            vec3 n = normalize(mix(nB, hN, smoothstep(0.15, 0.85, hmix)));
            // Front of the body: a lighter, yellower breast down the middle, feather rows across it.
            vec3 base = mix(uBody, uBelly, smoothstep(0.35, 0.85, nB.z) * 0.85);
            float rows = scallops(vec2(nB.x * 1.6, nB.y * 1.3 + 0.3), 5.0);
            base *= 1.0 - 0.045 * rows * smoothstep(0.75, 0.3, hmix) * (0.6 + 0.4 * vnoise(bl * 40.0));
            // Head colouring in head-fixed coordinates.
            hH = toHead(hN, cs);
            vec3 h = hH;
            headBase = uHeadC;
            headBase = mix(headBase, mix(uBody, uFaceC, smoothstep(-0.5, -0.05, h.z)), tiel);
            headBase = mix(headBase, mix(mix(uBody, uHeadC, smoothstep(-0.75, -0.2, h.z)), uFaceC, smoothstep(0.0, 0.55, h.z + 0.15 * h.y)), love);
            headBase = mix(headBase, uHeadC * vec3(1.06, 1.08, 0.9), ring * smoothstep(0.1, 0.7, h.z) * 0.5);
            float bars = smoothstep(0.35, 0.8, sin(h.y * 30.0 + 2.5 * sin(h.x * 9.0))) * smoothstep(0.25, -0.15, h.z - 0.5 * h.y + 0.25);
            headBase = mix(headBase, uEdgeC, bars * budgie * 0.9);
            inHead = smoothstep(1.02, 0.9, length(hq));
            float hw = smoothstep(0.35, 0.75, hmix) * mix(inHead, 1.0, ring);
            base = mix(base, headBase, hw);
            vec3 col = shade3(base, n, b, 0.0);
            over(acc, col, fillSoft(feathery(d, b, 0.55 + 0.6 * uFluff)));
        }
    }

    // ---- Head markings and eyes, painted on the sphere ----
    if (inHead > 0.01) {
        vec3 h = hH; vec3 n = hN;
        vec3 col = acc.rgb; float a0 = inHead;
        // Cheek patches (cockatiel orange, budgie violet).
        if (tiel + budgie > 0.5) {
            for (int s = 0; s < 2; s++) {
                float sd = s == 0 ? 1.0 : -1.0;
                float dch = length(h - normalize(vec3(sd * 0.8, -0.28, 0.52)));
                float rad = 0.26 * tiel + 0.13 * budgie;
                col = mix(col, uMark.rgb * mix(vec3(1.0), uAmb, 0.4) * (0.75 + 0.3 * n.z), smoothstep(rad, rad - 0.1, dch) * 0.95 * a0);
            }
        }
        if (budgie > 0.5) {
            for (int k = 0; k < 4; k++) {
                float fk = float(k) - 1.5;
                float dsp = length(h - normalize(vec3(fk * 0.3, -0.66, 0.68)));
                col = mix(col, vec3(0.04), clamp(0.5 - (dsp - 0.055) / aaH, 0.0, 1.0) * a0);
            }
        }
        // Ringneck: a thin black line from the chin round the neck, a rose band behind it on the nape.
        if (ring > 0.5) {
            float lineY = -0.62 - 0.18 * smoothstep(0.2, 1.0, h.z);          // dips under the chin
            float blk = max(abs(h.y - lineY) - 0.045, -h.z - 0.75);
            float chin = length(vec2(h.x * 1.3, h.y + 0.6)) - 0.13 + step(h.z, 0.45);
            float rose = max(abs(h.y - lineY - 0.1) - 0.05, h.z - 0.15);
            col = mix(col, uMark.rgb * mix(vec3(1.0), uAmb, 0.4) * (0.7 + 0.35 * n.z), clamp(0.5 - rose / aaH, 0.0, 1.0) * uMark.a * a0 * smoothstep(0.2, -0.3, h.z));
            col = mix(col, vec3(0.05, 0.05, 0.06), clamp(0.5 - min(blk, chin) / aaH, 0.0, 1.0) * uMark.a * a0);
        }
        // Eyes: round, glossy, on the sides of the head, close to the beak.
        float mode = uEyeS.y;
        for (int s = 0; s < 2; s++) {
            float sd = s == 0 ? 1.0 : -1.0;
            vec3 e = normalize(vec3(sd * 0.72, 0.2, 0.66));
            if (toView(e, cs).z < 0.2) continue;
            vec3 rt = normalize(cross(vec3(0.0, 1.0, 0.0), e));
            vec3 upv = cross(e, rt);
            vec3 dv = h - e;
            vec2 l = vec2(dot(dv, rt), dot(dv, upv));
            float de = length(l);
            float er = 0.235 + 0.02 * love + 0.02 * budgie + 0.02 * tiel;
            if (de > er + 0.14) continue;
            float ringW = 0.085 * love + 0.035 * ring + 0.03 * budgie + 0.02 * tiel;
            vec3 ringC = mix(mix(vec3(0.95, 0.45, 0.2), vec3(0.97), love + budgie), vec3(0.3, 0.3, 0.33), tiel);
            if (mode < 0.5 || mode > 2.5) {
                col = mix(col, ringC * mix(vec3(1.0), uAmb, 0.5), clamp(0.5 - (de - er - ringW) / aaH, 0.0, 1.0) * a0);
                float inEye = clamp(0.5 - (de - er) / aaH, 0.0, 1.0);
                vec2 lk = vec2(0.0, 0.04 * uEyeS.z);
                float pin = step(2.5, mode);
                float pr = mix(0.15, 0.08, pin) + 0.02 * (love + tiel);
                float dp = length(l - lk);
                // Pale iris with a dark pupil, as on the real bird.
                vec3 iris = mix(mix(vec3(0.95, 0.85, 0.55), vec3(0.97, 0.95, 0.9), budgie), vec3(0.35, 0.2, 0.1), love + tiel);
                iris *= 0.8 + 0.4 * smoothstep(0.15, -0.2, l.y);
                vec3 eye = mix(iris, vec3(0.02, 0.015, 0.03), clamp(0.5 - (dp - pr) / aaH, 0.0, 1.0));
                eye = mix(eye, vec3(0.05, 0.03, 0.03), smoothstep(er - 0.04, er, de));
                vec3 ev = toView(e, cs);
                vec2 hv = hq - ev.xy;
                eye = mix(eye, vec3(1.0), clamp(0.5 - (length(hv - vec2(-0.05, 0.065)) - 0.045) / aaH, 0.0, 1.0));
                eye = mix(eye, vec3(1.0), clamp(0.5 - (length(hv - vec2(0.055, -0.06)) - 0.02) / aaH, 0.0, 1.0) * 0.8);
                float lidY = mix(-er - 0.04, er + 0.04, uEyeS.x);
                float lid = clamp(0.5 - (lidY - l.y) / aaH, 0.0, 1.0);
                eye = mix(eye, shade3(headBase * 0.9, n, b, 0.0), lid);
                eye = mix(eye, vec3(0.05), clamp(0.5 - (abs(l.y - lidY) - 0.018) / aaH, 0.0, 1.0) * step(uEyeS.x, 0.95));
                col = mix(col, eye, inEye * a0);
            } else if (mode < 1.5) {
                float arc = max(abs(length(l + vec2(0.0, 0.08)) - 0.15) - 0.035, -l.y - 0.02);
                col = mix(col, vec3(0.05, 0.04, 0.06), clamp(0.5 - arc / aaH, 0.0, 1.0) * a0);
            } else {
                float arc = max(abs(length(l - vec2(0.0, 0.1)) - 0.16) - 0.03, l.y - 0.03);
                col = mix(col, vec3(0.05, 0.04, 0.06), clamp(0.5 - arc / aaH, 0.0, 1.0) * a0);
            }
        }
        acc.rgb = col;
    }
    if (!beakBehind) drawBeak(acc, hq, cs, bs, ring, budgie, aaH, b);

    // ---- Feet gripping the branch (in front of it) ----
    for (int s = 0; s < 2; s++) {
        float fs = float(s);
        float raise = s == 0 ? uLegs.x : uLegs.y;
        // A lifted foot leaves the branch a little and moves sideways with the step.
        vec2 fp = vec2((s == 0 ? 0.06 : -0.06) + (s == 0 ? uFeet.x : uFeet.y), -0.5 + 0.065 * raise);
        vec3 skin = mix(vec3(0.5, 0.5, 0.54), vec3(0.75, 0.6, 0.56), tiel) * mix(vec3(1.0), uAmb, 0.5);
        // Feather trousers over the top of the leg.
        float thigh = sdEllipse(p - fp - vec2(-0.012, 0.085 + 0.03 * raise), vec2(0.034, 0.034));
        if (raise < 0.98) {
            // The leg runs from under the feathers to the foot; toes curl up while lifted.
            float leg = sdCapsule(p, fp + vec2(-0.005, 0.05 - 0.02 * raise), fp + vec2(0.0, 0.012), 0.022, 0.02);
            over(acc, skin * 0.8, fill(leg));
            float curl = raise;
            for (int k = 0; k < 3; k++) {
                float fk = float(k) - 1.0;
                vec2 a = fp + vec2(0.0, 0.008);
                vec2 t1 = a + vec2(fk * 0.034 * (1.0 - 0.45 * curl), -0.016 + 0.004 * curl);
                vec2 t2 = t1 + vec2((fk * 0.012 + 0.006) * (1.0 - 0.6 * curl) - 0.01 * curl * fk, -0.04 * (1.0 - 0.55 * curl));
                float toe = min(sdCapsule(p, a, t1, 0.017, 0.015), sdCapsule(p, t1, t2, 0.015, 0.011));
                vec3 n = sphN(vec2((p.x - t1.x) / 0.02, 0.2));
                vec3 sc = shade3(skin / mix(vec3(1.0), uAmb, 0.5), n, p, 0.5) * (0.9 + 0.15 * step(0.5, fract(p.y * 95.0)));
                over(acc, sc, fill(toe));
                over(acc, vec3(0.16, 0.13, 0.14), fill(length((p - t2 - vec2(0.0, -0.01)) * vec2(1.3, 1.0)) - 0.008));
            }
        }
        over(acc, shade3(mix(uBody, uBelly, 0.85), sphN((p - fp - vec2(-0.012, 0.11)) / vec2(0.075, 0.075)), p, 0.0), fillSoft(feathery(thigh, p, 1.0)) * (1.0 - uHang));
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
