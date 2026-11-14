// Pulsify — Nutty, the red squirrel. GLSL ES 1.00. Same quad + premultiplied alpha as Mochi.
// Character space: y up, feet on the floor at y = -0.55, sitting height about 1.0, the big
// plume of a tail rises behind to about 0.75. Soft "plush render" lighting, fluffy edges.
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
uniform float uShear;
uniform vec2  uLook;       // head turn -1..1
uniform float uTilt;       // head roll
uniform float uHeadY;
uniform float uStand;      // 0 sitting .. 1 standing tall (lookout)
uniform float uEyeOpen;
uniform float uEyeMode;    // 0 open, 1 happy ^^, 2 sad, 3 asleep, 4 startled
uniform float uMouth;      // 0 smile, 1 chewing, 2 "o", 3 toothy grin
uniform float uChew;       // chewing phase 0..1
uniform float uCheeks;     // 0 .. 1 stuffed cheeks
uniform vec2  uEars;       // twitch left, right
uniform vec4  uTail;       // swing angle, curl, sway phase, sway amount
uniform float uFluff;      // 0 .. 1 tail puffed up (startled)
uniform vec2  uHandL;      // hand positions in character space
uniform vec2  uHandR;
uniform vec4  uAcorn;      // x, y, visible, rotation
uniform vec3  uCoat;       // main fur
uniform vec3  uCoat2;      // cream belly / muzzle
uniform vec3  uCoat3;      // dark accents (tufts, tail core)
uniform vec3  uRim;
uniform vec3  uGlow;
uniform vec4  uFx[6];      // x, y, life, kind: 1 heart, 2 sparkle, 3 crumb, 4 note, 5 zzz, 6 "!"
uniform float uBattIcon;
uniform float uBatt;
uniform float uDim;

// ---------- helpers ----------
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
float sdTri(vec2 p, vec2 a, vec2 b, vec2 c) {
    vec2 e0 = b - a, e1 = c - b, e2 = a - c, v0 = p - a, v1 = p - b, v2 = p - c;
    vec2 pq0 = v0 - e0 * clamp(dot(v0, e0) / dot(e0, e0), 0.0, 1.0);
    vec2 pq1 = v1 - e1 * clamp(dot(v1, e1) / dot(e1, e1), 0.0, 1.0);
    vec2 pq2 = v2 - e2 * clamp(dot(v2, e2) / dot(e2, e2), 0.0, 1.0);
    float s = sign(e0.x * e2.y - e0.y * e2.x);
    vec2 d = min(min(vec2(dot(pq0, pq0), s * (v0.x * e0.y - v0.y * e0.x)), vec2(dot(pq1, pq1), s * (v1.x * e1.y - v1.y * e1.x))), vec2(dot(pq2, pq2), s * (v2.x * e2.y - v2.y * e2.x)));
    return -sqrt(d.x) * sign(d.y);
}
float h21(vec2 p) { p = fract(p * vec2(123.34, 456.21)); p += dot(p, p + 45.32); return fract(p.x * p.y); }
float vnoise(vec2 p) {
    vec2 i = floor(p), f = fract(p); f = f * f * (3.0 - 2.0 * f);
    return mix(mix(h21(i), h21(i + vec2(1, 0)), f.x), mix(h21(i + vec2(0, 1)), h21(i + vec2(1, 1)), f.x), f.y);
}
void over(inout vec4 dst, vec3 c, float a) { dst.rgb = dst.rgb * (1.0 - a) + c * a; dst.a = dst.a * (1.0 - a) + a; }
float fill(float d) { return clamp(0.5 - d / (uPx * 1.2), 0.0, 1.0); }
float fillFur(float d) { return clamp(0.5 - d / (uPx * 3.0), 0.0, 1.0); }
// Soft fluffy outline: lazy tufts plus fine fuzz, only near the edge.
float fluff(float d, vec2 p, float amt) {
    if (abs(d) > 0.07) return d;
    vec2 r1 = vec2(p.x * 0.8 - p.y * 0.6, p.x * 0.6 + p.y * 0.8);
    float n = vnoise(r1 * 19.0) * 0.6 + vnoise(vec2(r1.y, -r1.x) * 47.0 + 3.7) * 0.4;
    return d + (n - 0.5) * 0.022 * amt * smoothstep(0.07, 0.0, abs(d));
}
// Plush render lighting on a rounded part; q = position inside the part, about -1..1.
vec3 lit(vec3 base, vec2 q, vec2 p) {
    float t = clamp(1.0 - dot(q, q), 0.0, 1.0);
    vec3 n = normalize(vec3(q * 0.95, sqrt(t) + 0.3));
    vec3 L = normalize(vec3(-0.45, 0.62, 0.7));
    float ndl = dot(n, L);
    float diff = smoothstep(-0.4, 0.85, ndl);
    vec3 shadow = base * vec3(0.48, 0.38, 0.42);           // warm, deep shadows
    vec3 col = mix(shadow, base * 1.05, diff);
    col += vec3(1.0, 0.95, 0.88) * pow(max(ndl, 0.0), 7.0) * 0.08;
    col *= 0.97 + 0.06 * vnoise(vec2(p.x * 0.8 - p.y * 0.6, p.x * 0.6 + p.y * 0.8) * 30.0);
    float rim = pow(1.0 - n.z, 2.4) * smoothstep(-0.5, 0.7, q.x * 0.75 - q.y * 0.2 + 0.35);
    col += uRim * rim * 0.6;
    return col;
}
float sdBolt(vec2 q) {
    return min(sdTri(q, vec2(0.18, 0.5), vec2(-0.32, -0.06), vec2(0.06, -0.06)), sdTri(q, vec2(-0.18, -0.5), vec2(0.32, 0.06), vec2(-0.06, 0.06)));
}

// The plume: a curve of fat capsules; returns distance, where along it (0..1), and which side.
vec3 tailField(vec2 p, out float across) {
    vec2 base = vec2(0.1, -0.44);
    float ang = uTail.x;
    float curl = uTail.y;
    float best = 1e5; float bt = 0.0; across = 0.0;
    float sm = 1e5;
    vec2 prev = base;
    float rPrev = 0.05;
    for (int i = 1; i <= 18; i++) {
        float t = float(i) / 18.0;
        // An S: out to the right, up the back, curling over at the top.
        vec2 c = vec2(0.1 + 0.38 * sin(t * 2.3) - (0.18 + curl * 0.2) * smoothstep(0.5, 1.0, t) * smoothstep(0.35, 1.0, t),
                      -0.44 + 1.18 * t - 0.12 * smoothstep(0.75, 1.0, t) * curl);
        float sway = uTail.w * sin(uTail.z - t * 2.6) * t;
        c = base + rot(c - base, -(ang + sway));
        float r = (0.05 + 0.16 * pow(sin(3.14159 * min(t * 1.05, 1.0)), 0.7)) * (1.0 + 0.25 * uFluff);
        if (i == 18) r = rPrev * 0.8;
        float d = sdCapsule(p, prev, c, rPrev, r);
        sm = smin(sm, d, 0.05);
        if (d < best) {
            best = d;
            vec2 ba = c - prev;
            float h = clamp(dot(p - prev, ba) / dot(ba, ba), 0.0, 1.0);
            bt = (float(i - 1) + h) / 18.0;
            vec2 nrm = normalize(vec2(-ba.y, ba.x));
            across = dot(p - (prev + ba * h), nrm) / max(mix(rPrev, r, h), 0.01);
        }
        prev = c; rPrev = r;
    }
    return vec3(sm, bt, 0.0);
}

void main() {
    vec2 p = (vUv - uPos) / uSize;
    vec2 sp = p;
    p.y = (p.y + 0.55) / uSquash.y - 0.55;
    p.x = p.x / uSquash.x;
    p.x -= uShear * (1.0 - clamp((p.y + 0.55) / 0.55, 0.0, 1.0));
    float stand = uStand;
    // Standing tall stretches the torso up.
    float stretch = 1.0 + 0.22 * stand;

    vec4 acc = vec4(0.0);
    vec3 coat = uCoat, cream = uCoat2, dark = uCoat3;
    float breath = 0.005 * sin(uTime * 2.1);

    // ---- Contact shadow ----
    {
        vec2 q = (sp - vec2(0.05, -0.565)) / vec2(0.36, 0.05);
        over(acc, vec3(0.0), exp(-dot(q, q) * 1.6) * 0.45);
    }

    // ---- Tail ----
    float across;
    vec3 tf = tailField(p, across);
    if (tf.x < 0.12) {
        float t = tf.y;
        // Soft lobes plus long hairs that break the outline into a frosted fringe.
        vec2 r1 = vec2(p.x * 0.8 - p.y * 0.6, p.x * 0.6 + p.y * 0.8);
        float lobes = vnoise(r1 * 9.0 + uTime * 0.08) * 0.65 + vnoise(r1 * 21.0) * 0.35;
        float hairs = vnoise(vec2(t * 140.0, 0.5)) * 0.6 + vnoise(vec2(t * 260.0, 3.0)) * 0.4;
        float edgeZone = smoothstep(0.1, 0.0, abs(tf.x));
        float d = tf.x + ((lobes - 0.5) * 0.07 + (hairs - 0.5) * 0.05 * (1.0 + uFluff)) * edgeZone;
        float a = fillFur(d);
        if (a > 0.001) {
            // Plush volume from the smooth distance field: a dome across the plume.
            float ac2; float e = 0.006;
            float dx = tailField(p + vec2(e, 0.0), ac2).x - tailField(p - vec2(e, 0.0), ac2).x;
            float dy = tailField(p + vec2(0.0, e), ac2).x - tailField(p - vec2(0.0, e), ac2).x;
            vec2 g = vec2(dx, dy) / (2.0 * e);
            float depth = clamp(-tf.x / 0.17, 0.0, 1.0);
            vec3 n = normalize(vec3(g * (1.0 - depth) * 1.3, depth + 0.25));
            vec3 L = normalize(vec3(-0.45, 0.62, 0.7));
            float diff = smoothstep(-0.4, 0.9, dot(n, L));
            // Colour: rich core, warm body, frosted cream edge, a darker tip.
            vec3 tc = mix(mix(coat, cream, 0.5), coat, smoothstep(0.0, 0.45, depth));
            tc = mix(tc, dark * 1.4, smoothstep(0.55, 1.0, depth) * 0.35);
            tc = mix(tc, dark * 1.3, smoothstep(0.85, 1.0, t) * 0.35);
            // Strands run along the plume and fan out toward the edge.
            float strands = vnoise(vec2(t * 9.0, depth * 9.0 + dot(g, vec2(1.0, 0.3)) * 3.0)) * 0.5
                          + vnoise(vec2(t * 40.0, depth * 26.0)) * 0.3 + hairs * 0.2;
            tc *= 0.86 + 0.24 * strands;
            vec3 col = mix(tc * vec3(0.5, 0.38, 0.4), tc * 1.05, diff);
            col += uRim * pow(1.0 - n.z, 2.2) * 0.55 * smoothstep(-0.3, 0.6, n.x);
            col += vec3(1.0, 0.92, 0.8) * pow(max(dot(n, L), 0.0), 8.0) * 0.08;
            over(acc, col, a);
        }
    }

    // ---- Haunches and back feet ----
    vec2 bc = vec2(0.0, -0.3 + 0.05 * stand);
    vec2 br = vec2(0.2 - 0.02 * stand, 0.25 * stretch + breath);
    for (int s = 0; s < 2; s++) {
        float side = s == 0 ? -1.0 : 1.0;
        vec2 hc = vec2(0.13 * side, -0.43);
        float hd = sdEllipse(p - hc, vec2(0.14, 0.12 - 0.02 * stand));
        over(acc, lit(coat * 0.96, (p - hc) / vec2(0.16, 0.14), p), fillFur(fluff(hd, p, 0.8)));
    }

    // ---- Body: a soft pear ----
    float body = sdEllipse(p - bc, br);
    if (body < 0.1) {
        vec2 q = (p - bc) / (br * 1.25);
        vec3 col = lit(coat, q, p);
        over(acc, col, fillFur(fluff(body, p, 1.0)));
        // Cream belly that glows a little from inside.
        float belly = sdEllipse(p - bc - vec2(0.0, -0.02), vec2(br.x * 0.62, br.y * 0.78));
        vec3 bcol = lit(cream, q * 0.9, p);
        over(acc, bcol, fillFur(fluff(belly, p * 1.2, 1.4)) * fill(body));
    }

    // Big hind feet in front, with little toes.
    for (int s = 0; s < 2; s++) {
        float side = s == 0 ? -1.0 : 1.0;
        vec2 fc = vec2(0.135 * side, -0.525);
        float ft = sdEllipse(rot(p - fc, -0.15 * side), vec2(0.105, 0.042));
        over(acc, lit(mix(coat, cream, 0.25), (p - fc) / vec2(0.11, 0.05), p), fillFur(fluff(ft, p, 0.4)));
        for (int k = 0; k < 2; k++) {
            float tx = fc.x + side * (0.025 + float(k) * 0.035);
            float tl = sdSeg(p, vec2(tx, -0.545), vec2(tx + side * 0.004, -0.515)) - 0.003;
            over(acc, dark * 0.8, fill(tl) * 0.5);
        }
    }

    // ---- Head ----
    vec2 hp = vec2(0.0, 0.14 + uHeadY + 0.1 * stand);
    vec2 turn = vec2(uLook.x * 0.04, uLook.y * 0.025);
    vec2 mouthPos = vec2(0.0, -0.065);
    if (length(p - hp) < 0.7) {
        vec2 h = rot(p - hp, -uTilt);
        // Ears behind: tall and rounded with dark lynx tufts.
        for (int s = 0; s < 2; s++) {
            float side = s == 0 ? -1.0 : 1.0;
            float tw = s == 0 ? uEars.x : uEars.y;
            vec2 pivot = vec2(0.14 * side, 0.17);
            vec2 eq = rot(h - pivot, -(0.18 + tw) * side);
            float ear = sdEllipse(eq - vec2(0.0, 0.085), vec2(0.06, 0.11));
            over(acc, lit(coat, (eq - vec2(0.0, 0.08)) / vec2(0.07, 0.12), h), fillFur(fluff(ear, h, 0.6)));
            float inner = sdEllipse(eq - vec2(0.006 * side, 0.075), vec2(0.032, 0.07));
            over(acc, mix(vec3(0.95, 0.55, 0.5), vec3(0.75, 0.35, 0.32), smoothstep(0.12, 0.02, eq.y)), fill(inner) * 0.9);
            // Tuft: a flame of dark hair off the tip.
            for (int k = 0; k < 4; k++) {
                float fk = float(k);
                vec2 a = vec2((fk - 1.5) * 0.012, 0.17);
                vec2 b = a + vec2((fk - 1.5) * 0.018 + 0.01 * sin(uTime * 3.0 + fk), 0.075 - abs(fk - 1.5) * 0.015);
                float tuft = sdCapsule(eq, a, b, 0.012, 0.0015);
                over(acc, mix(dark, coat, 0.2), fill(tuft) * 0.95);
            }
        }
        // Skull with puffy cheeks (they swell when stuffed with nuts).
        float skull = sdEllipse(h - vec2(0.0, 0.04), vec2(0.215, 0.19));
        float ck = 1.0 + 0.45 * uCheeks;
        float cheekL = sdEllipse(h - vec2(-0.12 - 0.03 * uCheeks, -0.04), vec2(0.115, 0.095) * ck);
        float cheekR = sdEllipse(h - vec2(0.12 + 0.03 * uCheeks, -0.04), vec2(0.115, 0.095) * ck);
        float head = smin(skull, min(cheekL, cheekR), 0.06);
        {
            float d = fluff(head, h, 1.0);
            vec2 q = h / vec2(0.3, 0.26);
            over(acc, lit(coat, q, h), fillFur(d));
        }
        vec2 f = h - turn;
        float face = fill(head + 0.008);
        // Cream muzzle and cheeks, cream brows above the eyes.
        float muz = smin(sdEllipse(f - vec2(0.0, -0.06), vec2(0.105, 0.075)), smin(sdEllipse(f - vec2(-0.12, -0.065), vec2(0.085, 0.06) * ck), sdEllipse(f - vec2(0.12, -0.065), vec2(0.085, 0.06) * ck), 0.04), 0.05);
        over(acc, lit(cream, (f - vec2(0.0, -0.06)) / vec2(0.26, 0.16), f), fillFur(fluff(muz, f, 0.6)) * face);
        for (int s = 0; s < 2; s++) {
            float side = s == 0 ? -1.0 : 1.0;
            float brow = sdEllipse(rot(f - vec2(0.088 * side, 0.115), 0.25 * side), vec2(0.045, 0.016));
            over(acc, cream, fillFur(fluff(brow, f, 0.3)) * face * 0.8);
            // Blush.
            vec2 bq = (f - vec2(0.15 * side, -0.035)) / vec2(0.05, 0.03);
            over(acc, vec3(1.0, 0.45, 0.45), exp(-dot(bq, bq) * 1.3) * 0.4 * face);
        }
        // Eyes: big, dark and glossy, with a ring of light fur.
        for (int s = 0; s < 2; s++) {
            float side = s == 0 ? -1.0 : 1.0;
            vec2 ec = f - vec2(0.088 * side, 0.03);
            vec2 look = uLook * vec2(0.012, 0.01);
            if (uEyeMode < 0.5 || uEyeMode > 3.5 || (uEyeMode > 1.5 && uEyeMode < 2.5)) {
                float open = uEyeOpen * (uEyeMode > 3.5 ? 1.15 : 1.0);
                vec2 er = vec2(0.056, 0.064 * max(open, 0.06)) * (uEyeMode > 3.5 ? 1.12 : 1.0);
                float eye = sdEllipse(ec, er);
                over(acc, mix(cream, vec3(1.0), 0.3), fill(eye - 0.012) * face * 0.9);
                // Iris: deep brown to black, warm glow at the bottom.
                float ir = length(ec - look) / max(er.x, 0.01);
                vec3 iris = mix(vec3(0.42, 0.2, 0.08), vec3(0.03, 0.015, 0.01), smoothstep(-0.06, 0.04, ec.y - look.y));
                iris = mix(iris, vec3(0.01), smoothstep(0.45, 0.0, ir) * 0.85);
                over(acc, iris, fill(eye) * face);
                // Top lid shade.
                over(acc, vec3(0.0), fill(eye) * face * smoothstep(0.015, 0.07, ec.y) * 0.45);
                if (uEyeMode > 1.5 && uEyeMode < 2.5) {
                    // Sad: lids slanting down to the outside.
                    float lid = dot(ec, normalize(vec2(-0.45 * side, 1.0))) - 0.01;
                    over(acc, coat, fill(-lid) * fill(eye) * face);
                }
                if (open > 0.3) {
                    vec2 hq = ec - look * 0.5;
                    float hl = sdEllipse(hq - vec2(-0.018, 0.024), vec2(0.022, 0.026));
                    float hl2 = length(hq - vec2(0.022, -0.022)) - 0.009;
                    over(acc, vec3(1.0), (fill(max(hl, eye)) + fill(hl2) * 0.8) * face);
                    // A soft reflected glint along the bottom.
                    over(acc, vec3(1.0, 0.8, 0.6), fill(max(sdEllipse(hq - vec2(0.0, -0.045), vec2(0.03, 0.008)), eye)) * face * 0.35);
                }
            } else {
                // Happy ^^ or asleep (a closed curve).
                float ln = uEyeMode < 1.5 ? sdArc(ec + vec2(0.0, 0.015), 0.04, 1.15) : sdArc(ec * vec2(1.0, -1.0) + vec2(0.0, 0.02), 0.042, 1.05);
                over(acc, vec3(0.12, 0.05, 0.03), fill(ln - 0.008) * face);
            }
        }
        // Nose: a little heart-shaped pink button.
        vec2 nq = f - vec2(0.0, -0.02);
        float nose = sdHeart(vec2(nq.x, -nq.y) / 0.032 + vec2(0.0, 0.55)) * 0.032;
        over(acc, mix(vec3(0.55, 0.22, 0.2), vec3(0.95, 0.55, 0.55), smoothstep(-0.02, 0.02, nq.y)), fill(nose) * face);
        over(acc, vec3(1.0), fill(sdEllipse(nq - vec2(-0.007, 0.004), vec2(0.008, 0.004))) * face * 0.7);
        // Mouth.
        vec2 mc = f - mouthPos;
        vec3 line = vec3(0.32, 0.12, 0.08);
        float chew = uMouth > 0.5 && uMouth < 1.5 ? 0.5 + 0.5 * sin(uChew * 6.2832) : 0.0;
        over(acc, line, fill(sdSeg(f, vec2(0.0, -0.035), vec2(0.0, -0.05 - 0.006 * chew)) - 0.003) * face);
        if (uMouth < 0.5 || (uMouth > 0.5 && uMouth < 1.5)) {
            float w1 = sdArc((mc - vec2(-0.02, 0.012 - 0.006 * chew)) * vec2(1.0, -1.0), 0.02, 1.3);
            float w2 = sdArc((mc - vec2(0.02, 0.012 - 0.006 * chew)) * vec2(1.0, -1.0), 0.02, 1.3);
            over(acc, line, fill(min(w1, w2) - 0.0035) * face);
            // Two buck teeth peeking out.
            float teeth = sdRoundBox(mc - vec2(0.0, -0.012 - 0.008 * chew), vec2(0.014, 0.013), 0.004);
            over(acc, vec3(1.0, 0.98, 0.94), fill(teeth) * face);
            over(acc, line, fill(sdSeg(mc, vec2(0.0, -0.003 - 0.008 * chew), vec2(0.0, -0.025 - 0.008 * chew)) - 0.0015) * face * 0.6);
        } else {
            float o = uMouth < 2.5 ? 0.028 : 0.022;
            float dm = sdEllipse(mc + vec2(0.0, 0.005), vec2(uMouth < 2.5 ? 0.025 : 0.05, o));
            over(acc, vec3(0.35, 0.08, 0.1), fill(dm) * face);
            float teeth = sdRoundBox(mc - vec2(0.0, 0.012), vec2(0.016, 0.012), 0.004);
            over(acc, vec3(1.0, 0.98, 0.94), fill(max(teeth, dm - 0.004)) * face);
            float tongue = sdEllipse(mc + vec2(0.0, 0.022), vec2(0.018, 0.012));
            over(acc, vec3(1.0, 0.5, 0.55), fill(max(tongue, dm)) * face);
        }
        // Whiskers.
        for (int s = 0; s < 2; s++) {
            float side = s == 0 ? -1.0 : 1.0;
            for (int k = 0; k < 3; k++) {
                float fk = float(k);
                vec2 a = vec2(0.07 * side, -0.05 - fk * 0.012);
                vec2 b = vec2((0.27 - fk * 0.02) * side, -0.02 - fk * 0.04 + 0.01 * sin(uTime * 2.3 + fk + side));
                vec2 pa = f - a, ba = b - a;
                float t = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
                float wsk = length(pa - ba * t + vec2(0.0, 0.02 * t * (1.0 - t))) - mix(0.0028, 0.0006, t);
                over(acc, vec3(1.0, 0.96, 0.9), fill(wsk) * 0.7);
            }
        }
    }

    // ---- Arms and mitten paws (over the body) ----
    for (int s = 0; s < 2; s++) {
        float side = s == 0 ? -1.0 : 1.0;
        vec2 hand = s == 0 ? uHandL : uHandR;
        vec2 sh = vec2(0.12 * side, -0.12 + 0.08 * stand);
        // Elbow bends outward and down a little.
        vec2 mid = mix(sh, hand, 0.5) + vec2(0.045 * side, -0.035);
        float arm = min(sdCapsule(p, sh, mid, 0.05, 0.042), sdCapsule(p, mid, hand, 0.042, 0.036));
        vec2 dir = normalize(hand - mid);
        over(acc, lit(coat * 0.95, vec2(dot(p - mid, vec2(dir.y, -dir.x)) / 0.06, 0.1), p), fillFur(fluff(arm, p, 0.5)));
        // Mitten: a round palm with three soft finger bumps and a thumb.
        vec2 hq = rot(p - hand, -atan(dir.x, -dir.y));
        float palm = sdEllipse(hq, vec2(0.046, 0.04));
        for (int k = 0; k < 3; k++) {
            float fk = float(k) - 1.0;
            palm = smin(palm, length(hq - vec2(fk * 0.026, -0.036 + abs(fk) * 0.006)) - 0.017, 0.012);
        }
        palm = smin(palm, length(hq - vec2(0.042 * side, 0.004)) - 0.014, 0.01);
        vec3 mcol = lit(mix(coat, cream, 0.35), hq / vec2(0.06, 0.055), p);
        over(acc, mcol, fill(palm));
        for (int k = 0; k < 2; k++) {
            float fk = float(k) - 0.5;
            float crease = sdSeg(hq, vec2(fk * 0.026, -0.05), vec2(fk * 0.024, -0.028)) - 0.002;
            over(acc, dark * 0.7, fill(crease) * 0.45);
        }
    }

    // ---- The acorn (held, eaten, stashed) ----
    if (uAcorn.z > 0.01) {
        vec2 q = rot(sp - uAcorn.xy, -uAcorn.w) / 0.075;
        float nut = sdEllipse(q - vec2(0.0, -0.18), vec2(0.62, 0.72));
        float cap = sdEllipse(q - vec2(0.0, 0.32), vec2(0.75, 0.38));
        float stem = sdCapsule(q, vec2(0.0, 0.55), vec2(0.12, 0.92), 0.12, 0.08);
        vec3 nc = mix(vec3(0.55, 0.28, 0.1), vec3(0.95, 0.62, 0.3), smoothstep(0.5, -0.5, q.x - q.y * 0.3));
        nc += vec3(1.0, 0.9, 0.7) * pow(clamp(1.0 - length((q - vec2(-0.22, -0.05)) / vec2(0.25, 0.35)), 0.0, 1.0), 2.0) * 0.5;
        over(acc, nc, fill(nut * 0.075) * uAcorn.z);
        vec3 cc = mix(vec3(0.28, 0.16, 0.07), vec3(0.55, 0.38, 0.2), smoothstep(0.6, -0.4, q.x - q.y));
        float hatch = step(0.5, fract((q.x + q.y) * 3.5)) * step(0.5, fract((q.x - q.y) * 3.5));
        cc *= 0.85 + 0.2 * hatch;
        over(acc, vec3(0.2, 0.12, 0.05), fill(stem * 0.075) * uAcorn.z);
        over(acc, cc, fill(cap * 0.075) * uAcorn.z);
    }

    // ---- Battery icon (charging) ----
    if (uBattIcon > 0.01) {
        vec2 q = sp - vec2(-0.05, 0.92);
        float frame = abs(sdRoundBox(q, vec2(0.13, 0.055), 0.02)) - 0.006;
        float tip = sdRoundBox(q - vec2(0.15, 0.0), vec2(0.01, 0.02), 0.004);
        float bw = 0.105 * uBatt;
        float bar = sdRoundBox(q - vec2(-0.11 + bw, 0.0), vec2(bw, 0.032), 0.01);
        over(acc, uGlow, (fill(min(frame, tip)) + exp(-max(min(frame, tip), 0.0) * 60.0) * 0.35) * uBattIcon);
        over(acc, uGlow * 0.85, fill(bar) * uBattIcon * 0.9);
        float bolt = sdBolt((q - vec2(-0.005, 0.0)) / 0.05) * 0.05;
        over(acc, vec3(1.0), fill(bolt) * uBattIcon * 0.9);
    }

    // ---- Effects ----
    for (int i = 0; i < 6; i++) {
        vec4 fx = uFx[i];
        if (fx.w < 0.5) continue;
        vec2 q = sp - fx.xy;
        float life = fx.z;
        float fade = smoothstep(0.0, 0.12, life) * smoothstep(1.0, 0.7, life);
        if (fx.w < 1.5) {
            over(acc, vec3(1.0, 0.42, 0.55), fill(sdHeart(q / 0.045 + vec2(0.0, 0.5)) * 0.045) * fade);
        } else if (fx.w < 2.5) {
            float r = 0.04 * (1.0 - abs(life - 0.5));
            float st = min(sdSeg(q, vec2(-r, 0.0), vec2(r, 0.0)), sdSeg(q, vec2(0.0, -r), vec2(0.0, r))) - 0.004;
            over(acc, vec3(1.0, 0.95, 0.75), (fill(st) + exp(-length(q) * 70.0) * 0.6) * fade);
        } else if (fx.w < 3.5) {
            float crumb = sdRoundBox(rot(q, life * 9.0), vec2(0.009, 0.007), 0.003);
            over(acc, vec3(0.75, 0.48, 0.22), fill(crumb) * fade);
        } else if (fx.w < 4.5) {
            float hd = sdEllipse(rot(q, 0.4), vec2(0.02, 0.015));
            float stem = sdSeg(q, vec2(0.017, 0.005), vec2(0.017, 0.065)) - 0.004;
            over(acc, mix(uRim, vec3(1.0), 0.4), fill(min(hd, stem)) * fade);
        } else if (fx.w < 5.5) {
            vec2 z = q / (0.03 + life * 0.02);
            float dz = min(min(sdSeg(z, vec2(-0.5, 0.5), vec2(0.5, 0.5)), sdSeg(z, vec2(0.5, 0.5), vec2(-0.5, -0.5))), sdSeg(z, vec2(-0.5, -0.5), vec2(0.5, -0.5)));
            over(acc, vec3(0.8, 0.85, 1.0), fill(dz * (0.03 + life * 0.02) - 0.004) * fade * 0.8);
        } else {
            float bar = sdRoundBox(q - vec2(0.0, 0.03), vec2(0.008, 0.03), 0.006);
            float dotv = length(q + vec2(0.0, 0.02)) - 0.009;
            over(acc, vec3(1.0, 0.85, 0.3), fill(min(bar, dotv)) * fade);
        }
    }

    acc.rgb *= mix(1.0, 0.55, uDim);
    gl_FragColor = acc;
}
