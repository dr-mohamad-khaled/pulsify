// Pulsify — Glowy, the anglerfish. GLSL ES 1.00. Shares mochi.vert.
// Character space: the fish faces +x, body centred on the origin, radius about 0.3.
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
uniform float uFace;       // -1 .. 1
uniform float uPitch;      // body rotation (radians)
uniform vec2  uSquash;
uniform float uPuff;       // 0 .. 1 pufferfish mode
uniform float uMouth;      // 0 closed .. 1 wide open
uniform vec4  uEye;        // open, mode (0 open, 1 happy, 2 asleep, 3 wide), look x, look y
uniform vec2  uFin;        // flutter phase, amplitude
uniform vec2  uTail;       // phase, amplitude
uniform vec4  uLure;       // x, y (char space, already flipped), brightness, flash
uniform vec4  uCharge;     // visible, level, -, -
uniform vec3  uBody;
uniform vec3  uBelly;
uniform vec3  uFinC;
uniform vec3  uGlow;
uniform float uSpots;      // spot brightness
uniform float uGhost;      // 0 solid .. 1 see-through ghost variant
uniform vec4  uPrey[4];    // x, y (sp space), direction, visible
uniform vec4  uFx[6];      // x, y, life, kind: 1 bubble, 2 sparkle, 3 heart, 4 zzz, 5 "!", 6 note
uniform float uDim;
uniform float uKind;       // 0 anglerfish, 1 clownfish, 2 betta, 3 pufferfish, 4 koi

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

// A translucent fin with rays.
void fin(inout vec4 acc, vec2 q, float len, float wid, float alpha) {
    float d = sdEllipse(q - vec2(len * 0.5, 0.0), vec2(len * 0.5, wid));
    if (d > 0.02) return;
    float rays = 0.75 + 0.25 * sin(atan(q.y, q.x + 0.05) * 34.0);
    vec3 c = mix(uFinC, uGlow, 0.25 + 0.35 * smoothstep(0.0, len, q.x)) * rays;
    over(acc, c, fill(d) * alpha * (0.55 + 0.45 * smoothstep(len, 0.0, q.x)));
    glowAdd(acc, uGlow, smoothstep(0.012, 0.0, abs(d)) * 0.25 * alpha);
}

void main() {
    vec4 acc = vec4(0.0);
    vec2 sp = (vUv - uPos) / uSize;
    vec2 p = sp;
    float face = abs(uFace) < 0.15 ? 0.15 * (uFace < 0.0 ? -1.0 : 1.0) : uFace;
    p.x /= face;
    p = rot(p, -uPitch);
    p /= uSquash;
    float puff = uPuff;
    float R = 0.3 * (1.0 + 0.3 * puff);
    vec2 L = uLure.xy;
    float lureOn = uLure.z;
    float ghost = uGhost;
    float kind = uKind;
    float angler = step(kind, 0.5);
    float clown = step(0.5, kind) * step(kind, 1.5);
    float betta = step(1.5, kind) * step(kind, 2.5);
    float puffer = step(2.5, kind) * step(kind, 3.5);
    float koi = step(3.5, kind);
    lureOn *= angler;
    puff = max(puff, puffer * 0.35);
    R = 0.3 * (1.0 + 0.3 * puff);
    // Body proportions per fish.
    vec2 bodyR = vec2(R * 1.06, R * 0.95);
    if (betta > 0.5) bodyR = vec2(R * 1.05, R * 0.66);
    if (koi > 0.5) bodyR = vec2(R * 1.22, R * 0.62);
    if (clown > 0.5) bodyR = vec2(R * 1.1, R * 0.78);

    // ---- Lure halo behind everything ----
    {
        float dl = length(p - L);
        glowAdd(acc, uGlow, (exp(-dl * 5.5) * 0.5 + exp(-dl * 16.0) * 0.5) * lureOn * smoothstep(1.3, 0.8, length(p)));
        // The other fish glow softly from inside instead.
        glowAdd(acc, uGlow, exp(-length(p) * 4.0) * 0.18 * (1.0 - angler));
    }

    if (length(p) < 1.1) {
    // ---- Tail fin ----
    {
        vec2 q = p - vec2(-bodyR.x * 0.82, 0.0);
        q = rot(q, -sin(uTail.x) * uTail.y);
        q.x = -q.x;
        float wob = sin(uTail.x - q.x * 9.0) * uTail.y * 0.25 * q.x * 3.0 * (1.0 + betta * 1.5);
        q.y += wob;
        float tf = max(sdEllipse(q - vec2(0.12, 0.0), vec2(0.17, 0.15)), -(sdEllipse(q - vec2(0.3, 0.0), vec2(0.1, 0.09))));
        if (clown + puffer > 0.5) tf = sdEllipse(q - vec2(0.09, 0.0), vec2(0.11, 0.1 + 0.02 * puffer));
        if (betta > 0.5) {
            // A huge flowing veil tail with a rippling edge.
            // A wide fan that flares out from the body and ripples at its edge.
            vec2 fq = q + vec2(0.03, 0.0);
            float ra = length(fq), an = atan(fq.y, fq.x);
            float edge = 0.03 * sin(an * 7.0 + uTime * 3.0) + 0.015 * sin(an * 17.0 - uTime * 4.0);
            tf = max(ra - (0.4 + edge), (abs(an) - 1.15) * ra);
        }
        if (koi > 0.5) tf = max(sdEllipse(q - vec2(0.14, 0.0), vec2(0.18, 0.17)), -(sdEllipse(q - vec2(0.36, 0.0), vec2(0.13, 0.11))));
        tf = max(tf, -q.x);
        if (tf < 0.02) {
            float rays = 0.75 + 0.25 * sin(atan(q.y, q.x) * 30.0);
            vec3 tc = mix(uFinC, uGlow, 0.3 * smoothstep(0.0, 0.25, q.x)) * rays;
            if (betta > 0.5) tc = mix(uFinC, uBody, smoothstep(0.45, 0.05, q.x)) * (0.8 + 0.3 * rays) + uGlow * 0.15 * sin(q.x * 40.0 + uTime * 2.0);
            if (clown > 0.5) tc = mix(uFinC, vec3(0.02), smoothstep(-0.03, -0.005, tf));
            over(acc, tc, fill(tf) * (0.8 - 0.3 * ghost + 0.1 * betta));
            glowAdd(acc, uGlow, smoothstep(0.012, 0.0, abs(tf)) * 0.2);
        }
    }
    // ---- Dorsal spines and belly fin ----
    if (clown + koi > 0.5) fin(acc, rot(p - vec2(-0.04, bodyR.y * 0.85), 2.6 + 0.08 * sin(uTime * 3.0)), 0.16 + 0.03 * koi, 0.06, 0.85);
    if (betta > 0.5) {
        // Long flowing dorsal and anal fins.
        fin(acc, rot(p - vec2(-0.02, bodyR.y * 0.8), 2.75 + 0.12 * sin(uTime * 2.2)), 0.3, 0.1, 0.8);
        fin(acc, rot(p - vec2(-0.02, -bodyR.y * 0.8), -2.75 - 0.12 * sin(uTime * 2.2 + 1.0)), 0.3, 0.11, 0.8);
    }
    for (int i = 0; i < 4; i++) {
        if (angler + puffer < 0.5) break;
        float fi = float(i);
        float a = 2.1 + fi * 0.27;
        vec2 base = vec2(cos(a), sin(a)) * R * 0.92;
        vec2 tip = base + vec2(cos(a + 0.5), sin(a + 0.5)) * (0.1 - fi * 0.012 + 0.05 * puff) + vec2(0.0, 0.01 * sin(uTime * 2.0 + fi));
        float sd = sdCapsule(p, base, tip, 0.022, 0.004);
        over(acc, mix(uFinC, uGlow, 0.3), fill(sd) * 0.85);
        glowAdd(acc, uGlow, exp(-length(p - tip) * 60.0) * 0.5 * uSpots);
    }
    if (betta < 0.5) fin(acc, rot(p - vec2(-0.05, -bodyR.y * 0.88), 1.9 + 0.2 * sin(uFin.x * 0.7)), 0.14, 0.05, 0.7);

    // ---- Body ----
    float ang = atan(p.y, p.x);
    float spikes = pow(max(0.0, cos(ang * 9.0 + 0.4)), 6.0) * 0.07 * smoothstep(0.25, 1.0, puff);
    spikes += puffer * pow(max(0.0, cos(ang * 14.0)), 10.0) * 0.035;
    float body = sdEllipse(p, bodyR) - spikes;
    // Koi: barbels at the mouth.
    if (koi > 0.5) {
        float bar = min(sdCapsule(p, vec2(bodyR.x * 0.92, -0.03), vec2(bodyR.x * 1.12, -0.09 + 0.01 * sin(uTime * 3.0)), 0.006, 0.002),
                        sdCapsule(p, vec2(bodyR.x * 0.9, -0.04), vec2(bodyR.x * 1.02, -0.11), 0.005, 0.002));
        over(acc, uBelly * 0.85, fill(bar));
    }
    // Jaw geometry (hinge near the middle, mouth opens to the front).
    vec2 hinge = vec2(R * 0.12, -R * 0.22);
    float open = uMouth;
    float a0 = -0.08 + 0.12 * open;
    float a1 = a0 - 0.95 * open - 0.001;
    vec2 mq = p - hinge;
    float ma = atan(mq.y, mq.x);
    float mr = length(mq);
    float inMouth = step(a1, ma) * step(ma, a0) * step(0.02, mr);
    if (body < 0.03) {
        vec2 q = p / (bodyR * 1.1);
        float t = clamp(1.0 - dot(q, q), 0.0, 1.0);
        vec3 n = normalize(vec3(q, sqrt(t) + 0.25));
        float diff = smoothstep(-0.4, 0.9, dot(n, normalize(vec3(-0.3, 0.7, 0.65))));
        vec3 base = mix(uBelly, uBody, smoothstep(-0.55, 0.25, q.y + 0.25 * q.x));
        if (clown > 0.5) {
            // Three white bands with dark outlines.
            float bx = p.x / bodyR.x;
            float bands = min(min(abs(bx - 0.55) - 0.11, abs(bx + 0.02) - 0.14), abs(bx + 0.62) - 0.09);
            bands += 0.04 * sin(q.y * 6.0);
            base = mix(base, vec3(0.06, 0.03, 0.04), smoothstep(0.05, 0.0, abs(bands)) * 0.9);
            base = mix(base, vec3(1.0, 0.98, 0.95), step(bands, -0.02));
        }
        if (koi > 0.5) {
            // White koi with orange patches and a few black flecks.
            float n = vnoise(p * 7.0 + 3.1) + 0.5 * vnoise(p * 15.0);
            base = mix(uBelly, uBody, smoothstep(0.62, 0.7, n));
            base = mix(base, vec3(0.05), smoothstep(1.08, 1.12, n + 0.35 * vnoise(p * 21.0 + 8.0)) * 0.8);
        }
        if (puffer > 0.5) {
            // Leopard spots on the back.
            vec2 g = p * 26.0; vec2 id = floor(g); vec2 f = fract(g) - 0.5;
            float sp = length(f - (vec2(h21(id), h21(id + 4.0)) - 0.5) * 0.5) - 0.18;
            base = mix(base, uFinC * 0.45, fill(sp / 26.0) * smoothstep(-0.2, 0.3, q.y) * 0.8);
        }
        vec3 col = mix(base * vec3(0.35, 0.38, 0.6), base * 1.05, diff);
        // Lit by its own lure.
        float dl = length(p - L);
        col += uGlow * exp(-dl * 3.2) * 0.75 * lureOn;
        // Glossy wet highlight.
        col += vec3(1.0) * smoothstep(0.035, 0.0, sdEllipse(rot(p - vec2(-0.06, R * 0.55), 0.25), vec2(0.11, 0.028))) * 0.28;
        // Inner glow: you can almost see through it.
        col += uGlow * exp(-length(p - vec2(-0.03, -0.05)) * 6.0) * (0.12 + 0.3 * ghost);
        // Rim.
        col += mix(uGlow, vec3(0.6, 0.8, 1.0), 0.5) * pow(1.0 - n.z, 2.5) * 0.55;
        // Scales shimmer (betta, koi).
        if (betta + koi > 0.5) {
            vec2 sc = p * vec2(30.0, 34.0);
            sc.x += 0.5 * mod(floor(sc.y), 2.0);
            vec2 sf = fract(sc) - 0.5;
            float scale = smoothstep(0.5, 0.2, length(sf));
            col += mix(uGlow, vec3(1.0), 0.5) * scale * 0.12 * (0.5 + 0.5 * sin(uTime * 2.0 + p.x * 20.0 + p.y * 9.0));
        }
        // Glowing freckles along the side (the anglerfish's signature).
        for (int i = 0; i < 7; i++) {
            if (angler < 0.5) break;
            float fi = float(i);
            vec2 c = vec2(-0.2 + fi * 0.055, -0.02 + 0.07 * sin(fi * 1.9)) * (R / 0.3);
            float tw = 0.5 + 0.5 * sin(uTime * 2.5 + fi * 1.7);
            float d = length(p - c);
            col += uGlow * (smoothstep(0.014, 0.006, d) + exp(-d * 45.0) * 0.6) * (0.35 + 0.65 * tw) * uSpots;
        }
        // Gill curve.
        float gill = sdArc(rot(p - vec2(-0.01, -0.01), -1.57), 0.11, 0.9) - 0.004;
        col = mix(col, col * 0.55, fill(gill) * 0.6);
        float alpha = fill(body) * mix(0.96, 0.62, ghost);
        over(acc, col, alpha * (1.0 - inMouth * step(body, 0.0) * step(0.03, open)));

        // ---- Mouth ----
        if (open > 0.03) {
            float inside = fill(body + 0.012) * inMouth;
            vec3 mc = mix(vec3(0.35, 0.05, 0.12), vec3(0.08, 0.01, 0.05), smoothstep(0.05, 0.3, mr));
            over(acc, mc, inside);
            float tongue = sdEllipse(rot(mq, -(a0 + a1) * 0.5 + 0.25) - vec2(0.12, 0.0), vec2(0.07, 0.03 * open + 0.004));
            over(acc, vec3(1.0, 0.45, 0.55), fill(tongue) * inside);
            // Tiny needle teeth on both jaws.
            float tp = abs(fract(mr * 16.0) - 0.5) * 2.0;
            float toothLen = 0.13 * (1.0 - tp) * smoothstep(0.03, 0.08, mr);
            float top = step(a0 - toothLen, ma) * step(ma, a0);
            float bot = step(ma, a1 + toothLen) * step(a1, ma);
            over(acc, vec3(1.0, 0.98, 0.9), max(top, bot) * fill(body + 0.02) * step(0.05, mr));
        } else {
            // Closed: a long grumpy-cute lip line with two teeth poking up from the underbite.
            vec2 lip0 = hinge, lip1 = hinge + vec2(cos(a0), sin(a0)) * R * 0.9;
            float lip = sdSeg(p, lip0, lip1) - 0.005;
            if (angler < 0.5) {
                vec2 mp = p - vec2(bodyR.x * 0.82, -bodyR.y * 0.18);
                lip = sdArc(mp * vec2(1.0, -1.0) + vec2(0.0, 0.03), 0.045, 0.9) - 0.006;
                if (puffer > 0.5) lip = length(mp) - 0.022;
            }
            over(acc, vec3(0.08, 0.03, 0.1), fill(lip) * fill(body + 0.01));
            for (int k = 0; k < 2; k++) {
                if (angler < 0.5) break;
                vec2 tb = mix(lip0, lip1, 0.55 + float(k) * 0.25);
                float tooth = sdTri(p, tb + vec2(-0.016, -0.004), tb + vec2(0.016, -0.004), tb + vec2(0.0, 0.035));
                over(acc, vec3(1.0, 0.98, 0.9), fill(tooth) * fill(body + 0.01));
            }
        }

        // ---- Eye ----
        {
            vec2 ec = p - vec2(bodyR.x * 0.48, bodyR.y * 0.3);
            float r = 0.078 * (1.0 + 0.2 * puff) * (1.0 - 0.18 * (betta + koi + clown) + 0.1 * puffer);
            float eyeD = length(ec) - r;
            float mode = uEye.y;
            if (mode < 0.5 || mode > 2.5) {
                float op = mode > 2.5 ? 1.0 : uEye.x;
                float lid = ec.y - mix(-r - 0.01, r + 0.01, op);
                float inEye = max(eyeD, lid);
                over(acc, vec3(0.03, 0.02, 0.08), fill(eyeD - 0.008));
                over(acc, vec3(0.98, 0.99, 1.0), fill(inEye));
                vec2 look = uEye.zw * 0.022;
                float irisR = mode > 2.5 ? 0.03 : 0.052;
                float iris = length(ec - look) - irisR;
                vec3 ic = mix(uGlow * 0.5, uGlow * 0.15, smoothstep(-0.03, 0.04, ec.y - look.y));
                over(acc, ic, fill(max(iris, inEye)));
                over(acc, vec3(0.01, 0.0, 0.03), fill(max(iris + irisR * 0.45, inEye)));
                over(acc, vec3(1.0), fill(max(length(ec - look - vec2(-0.02, 0.022)) - 0.017, inEye)));
                over(acc, vec3(1.0), fill(max(length(ec - look - vec2(0.02, -0.02)) - 0.007, inEye)) * 0.8);
                // Reflection of the lure in the eye.
                glowAdd(acc, uGlow, fill(inEye) * exp(-length(ec - look - normalize(L - p + 0.001) * 0.03) * 60.0) * 0.5 * lureOn);
                if (op < 0.95) over(acc, uBody * 0.8, fill(max(eyeD, -lid)));
            } else if (mode < 1.5) {
                over(acc, vec3(0.03, 0.02, 0.08), fill(sdArc(ec + vec2(0.0, 0.02), 0.05, 1.15) - 0.009));
            } else {
                over(acc, vec3(0.03, 0.02, 0.08), fill(sdArc(ec * vec2(1.0, -1.0) + vec2(0.0, 0.02), 0.05, 1.05) - 0.008));
            }
            // Blush.
            vec2 bq = (p - vec2(R * 0.5, R * 0.02)) / vec2(0.05, 0.028);
            over(acc, vec3(1.0, 0.45, 0.6), exp(-dot(bq, bq) * 1.4) * 0.4 * fill(body));
        }
    }
    // ---- Pectoral fin (near side), fluttering ----
    fin(acc, rot(p - vec2(-0.03, -0.07), 2.75 + sin(uFin.x) * uFin.y), 0.17, 0.06, 0.85);

    // ---- Stalk and lure (anglerfish only) ----
    if (angler > 0.5) {
        vec2 a = vec2(R * 0.32, R * 0.86);
        vec2 ctrl = mix(a, L, 0.5) + vec2(-0.06, 0.16);
        float st = 1e5; vec2 prev = a;
        for (int i = 1; i <= 7; i++) {
            float t = float(i) / 7.0;
            vec2 b = mix(mix(a, ctrl, t), mix(ctrl, L, t), t);
            st = min(st, sdCapsule(p, prev, b, 0.014 - 0.008 * t, 0.014 - 0.008 * t));
            prev = b;
        }
        over(acc, mix(uBody * 0.8, uGlow, 0.35), fill(st));
        vec2 lq = p - L;
        float ch = uCharge.x;
        float br = mix(0.045, 0.085, ch);
        float bulb = length(lq) - br;
        vec3 core = mix(uGlow, vec3(1.0), 0.55);
        over(acc, mix(core, vec3(0.05, 0.06, 0.1), ch * 0.85), fill(bulb));
        glowAdd(acc, uGlow, exp(-max(bulb, 0.0) * 28.0) * 0.8 * lureOn * (1.0 - 0.5 * ch));
        over(acc, vec3(1.0), fill(length(lq - vec2(-0.3, 0.35) * br) - br * 0.22) * 0.9);
        if (ch > 0.01) {
            // Charging: the lure becomes a little glass bulb with a bolt and a ring that fills.
            float beat = 0.6 + 0.4 * sin(uTime * 4.0);
            float la = atan(lq.x, lq.y) / 6.2832 + 0.5;
            float ringD = abs(length(lq) - br * 0.82) - 0.006;
            over(acc, mix(vec3(0.2, 0.22, 0.3), uGlow * 1.2, step(1.0 - la, uCharge.y)), fill(ringD) * ch);
            float bolt = sdBolt(lq / (br * 1.1)) * br * 1.1;
            over(acc, mix(uGlow, vec3(1.0), 0.4 + 0.35 * beat), fill(bolt) * ch);
            glowAdd(acc, uGlow, exp(-max(bolt, 0.0) * 60.0) * 0.5 * beat * ch);
        }
        // Flash: star rays.
        if (uLure.w > 0.01) {
            float la = atan(lq.y, lq.x);
            float rays = pow(abs(cos(la * 4.0 + uTime * 2.0)), 14.0);
            glowAdd(acc, mix(uGlow, vec3(1.0), 0.6), (exp(-length(lq) * (9.0 - 5.0 * rays)) + exp(-length(lq) * 3.0) * 0.4) * uLure.w * smoothstep(1.08, 0.55, length(p)));
        }
    }
    }

    // ---- Little fish that gather at your finger ----
    for (int i = 0; i < 4; i++) {
        vec4 pr = uPrey[i];
        if (pr.w < 0.01) continue;
        vec2 q = sp - pr.xy;
        if (length(q) > 0.15) continue;
        q.x *= pr.z;
        q.y += 0.006 * sin(uTime * 14.0 + float(i) * 2.0);
        float bodyD = sdEllipse(q, vec2(0.045, 0.022));
        float tl = sdTri(q, vec2(-0.035, 0.0), vec2(-0.075, 0.025 + 0.01 * sin(uTime * 18.0 + float(i))), vec2(-0.075, -0.025 - 0.01 * sin(uTime * 18.0 + float(i))));
        vec3 pc = mix(vec3(0.55, 0.9, 1.0), vec3(1.0, 0.8, 0.5), mod(float(i), 2.0));
        over(acc, pc * (0.7 + 0.4 * smoothstep(-0.02, 0.02, q.y)), fill(min(bodyD, tl)) * pr.w);
        over(acc, vec3(0.02), fill(length(q - vec2(0.024, 0.005)) - 0.006) * pr.w);
        glowAdd(acc, pc, exp(-length(q) * 40.0) * 0.25 * pr.w);
    }

    // ---- Effects ----
    for (int i = 0; i < 6; i++) {
        vec4 fx = uFx[i];
        if (fx.w < 0.5) continue;
        vec2 q = sp - fx.xy;
        float life = fx.z;
        float fade = smoothstep(0.0, 0.1, life) * smoothstep(1.0, 0.75, life);
        if (fx.w < 1.5) {
            float r = 0.018 + 0.03 * life + 0.012 * h21(vec2(float(i), 3.0));
            float ring = abs(length(q) - r) - 0.003;
            over(acc, vec3(0.75, 0.92, 1.0), fill(ring) * fade * 0.9);
            over(acc, vec3(0.6, 0.85, 1.0), fill(length(q) - r) * fade * 0.12);
            over(acc, vec3(1.0), fill(length(q - vec2(-0.35, 0.4) * r) - r * 0.22) * fade);
        } else if (fx.w < 2.5) {
            float r = 0.045 * (1.0 - abs(life - 0.5));
            float st = min(sdSeg(q, vec2(-r, 0.0), vec2(r, 0.0)), sdSeg(q, vec2(0.0, -r), vec2(0.0, r))) - 0.004;
            glowAdd(acc, mix(uGlow, vec3(1.0), 0.6), (fill(st) + exp(-length(q) * 70.0) * 0.6) * fade);
        } else if (fx.w < 3.5) {
            over(acc, vec3(1.0, 0.4, 0.6), fill(sdHeart(q / 0.05 + vec2(0.0, 0.5)) * 0.05) * fade);
        } else if (fx.w < 4.5) {
            vec2 z = q / (0.03 + life * 0.02);
            float dz = min(min(sdSeg(z, vec2(-0.5, 0.5), vec2(0.5, 0.5)), sdSeg(z, vec2(0.5, 0.5), vec2(-0.5, -0.5))), sdSeg(z, vec2(-0.5, -0.5), vec2(0.5, -0.5)));
            over(acc, vec3(0.8, 0.9, 1.0), fill(dz * (0.03 + life * 0.02) - 0.004) * fade * 0.8);
        } else if (fx.w < 5.5) {
            float bar = sdRoundBox(q - vec2(0.0, 0.035), vec2(0.01, 0.035), 0.008);
            float dotv = length(q + vec2(0.0, 0.025)) - 0.011;
            over(acc, vec3(1.0, 0.85, 0.3), fill(min(bar, dotv)) * fade);
        } else {
            float head = sdEllipse(rot(q, 0.4), vec2(0.022, 0.016));
            float stem = sdSeg(q, vec2(0.019, 0.005), vec2(0.019, 0.07)) - 0.004;
            glowAdd(acc, mix(uGlow, vec3(1.0), 0.4), fill(min(head, stem)) * fade);
        }
    }

    acc.rgb *= mix(1.0, 0.55, uDim);
    gl_FragColor = vec4(acc.rgb, clamp(acc.a, 0.0, 1.0));
}
