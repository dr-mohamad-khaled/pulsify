// Pulsify — Mochi, the battery-juice bean. GLSL ES 1.00.
// Drawn over the sky with premultiplied alpha on a quad that only covers Mochi and its props.
// Character space: y up, feet at y = -0.55, body about 1.1 tall. One uniform scale, never stretched:
// squash keeps the volume (sx * sy = 1).
precision highp float;

varying vec2 vUv;          // aspect-correct screen space (short side = 1)

uniform float uTime;
uniform vec2  uPos;        // character origin (body centre) in uv
uniform float uSize;       // uv units per character unit
uniform float uPx;         // one screen pixel in character units
uniform vec2  uSquash;     // (sx, sy), sx * sy ~ 1
uniform float uShear;      // jelly wobble, top moves sideways
uniform vec2  uLook;       // gaze -1..1
uniform float uEyeOpen;    // 1 open, 0 closed (blink)
uniform float uEyeMode;    // 0 normal, 1 happy ^^, 2 hearts, 3 sleeping, 4 tired
uniform float uMouth;      // 0 tiny w, 1 open wail, 2 sip o, 3 big smile
uniform float uTears;      // 0..1
uniform float uBlush;      // 0..1 extra blush
uniform vec4  uArmL;       // arm: centre xy, angle, length scale
uniform vec4  uArmR;
uniform vec4  uJar;        // jar centre xy (char space), visible 0..1, liquid level 0..1
uniform float uJarHeld;    // 1 = hugged against the belly (arms over it)
uniform float uSip;        // straw visible / sipping 0..1
uniform vec3  uBase;       // body colour (coat)
uniform vec3  uGlow;       // jar / cheek glow colour (music cycles it)
uniform vec3  uRim;        // rim light colour
uniform float uBeat;
uniform float uPress;      // 0..1 face pressed against the glass
uniform vec4  uFx[6];      // x, y, t (0..1 life), kind: 1 heart, 2 sparkle, 3 bubble, 4 star, 5 zzz, 6 sweat
uniform vec4  uBird;       // x, y, flap phase, visible
uniform float uBattIcon;   // neon battery icon above the head, 0..1
uniform float uDim;

// ---------- SDF helpers ----------
float sdEllipse(vec2 p, vec2 r) {
    // Cheap ellipse approximation, good enough for soft shapes.
    float k0 = length(p / r);
    float k1 = length(p / (r * r));
    return k0 * (k0 - 1.0) / max(k1, 1e-4);
}
float sdRoundBox(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}
float sdSeg(vec2 p, vec2 a, vec2 b) {
    vec2 pa = p - a, ba = b - a;
    float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
    return length(pa - ba * h);
}
float smin(float a, float b, float k) {
    float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
    return mix(b, a, h) - k * h * (1.0 - h);
}
vec2 rot(vec2 p, float a) { float c = cos(a), s = sin(a); return vec2(c * p.x - s * p.y, s * p.x + c * p.y); }
float sdHeart(vec2 p) {
    p.x = abs(p.x);
    if (p.y + p.x > 1.0) return sqrt(dot(p - vec2(0.25, 0.75), p - vec2(0.25, 0.75))) - sqrt(2.0) / 4.0;
    return sqrt(min(dot(p - vec2(0.0, 1.0), p - vec2(0.0, 1.0)), dot(p - 0.5 * max(p.x + p.y, 0.0), p - 0.5 * max(p.x + p.y, 0.0)))) * sign(p.x - p.y);
}
float sdArc(vec2 p, float r, float halfAng) {
    // Upper arc of a circle of radius r centred at origin, spanning +-halfAng from straight up.
    float a = atan(p.x, p.y);
    if (abs(a) < halfAng) return abs(length(p) - r);
    vec2 e = vec2(sin(halfAng) * sign(p.x), cos(halfAng)) * r;
    return length(p - e);
}
float hash(float n) { return fract(sin(n) * 43758.5453); }

// "Over" compositing with premultiplied colour.
void over(inout vec4 dst, vec3 c, float a) {
    dst.rgb = dst.rgb * (1.0 - a) + c * a;
    dst.a = dst.a * (1.0 - a) + a;
}
float fill(float d) { return clamp(0.5 - d / (uPx * 1.2), 0.0, 1.0); }
const float JS = 1.45; // jar scale
float fillJ(float d) { return fill(d * JS); }

// ---------- Shapes ----------
float bodySdf(vec2 p) {
    float head = sdEllipse(p - vec2(0.0, 0.15), vec2(0.43, 0.345));
    float belly = sdEllipse(p - vec2(0.0, -0.2), vec2(0.47, 0.345));
    return smin(head, belly, 0.2);
}
float armSdf(vec2 p, vec4 a) {
    vec2 q = rot(p - a.xy, -a.z);
    return sdEllipse(q, vec2(0.092, 0.125 * a.w));
}
float footSdf(vec2 p, float side) {
    return sdEllipse(p - vec2(0.225 * side, -0.49), vec2(0.165, 0.1));
}

// Dome normal from an SDF: flat in the middle, turning sideways at the silhouette.
vec3 domeNormal(float d, vec2 g, float radius) {
    float t = sqrt(clamp(-d / radius, 0.0, 1.0));
    return normalize(vec3(g * (1.0 - t) * 1.25, t + 0.12));
}

vec3 jellyShade(vec3 n, vec3 base, float ao, vec2 sp, float jarLight) {
    vec3 L = normalize(vec3(-0.45, 0.6, 0.85));
    float wrap = (dot(n, L) + 0.7) / 1.7;
    float diff = smoothstep(0.0, 1.0, clamp(wrap, 0.0, 1.0));
    // Subsurface: shadows stay warm and soft instead of going grey.
    vec3 sss = base * vec3(1.0, 0.84, 0.80) * 0.6;
    vec3 col = mix(sss, base, diff);
    float fres = pow(1.0 - clamp(n.z, 0.0, 1.0), 2.4);
    col += uRim * fres * 0.6;
    // Soft specular sheen.
    vec3 H = normalize(L + vec3(0.0, 0.0, 1.0));
    col += vec3(1.0) * pow(max(dot(n, H), 0.0), 14.0) * 0.1;
    // Light from the battery jar spills onto the body.
    col += uGlow * jarLight;
    return col * ao;
}

void main() {
    // Into character space; undo squash about the feet and add the jelly shear.
    vec2 p = (vUv - uPos) / uSize;
    float press = uPress;
    p /= (1.0 + press * 0.35);
    vec2 sp = p;
    p.y = (p.y + 0.55) / uSquash.y - 0.55;
    p.x = p.x / uSquash.x;
    float hgt = clamp((p.y + 0.55) / 1.1, 0.0, 1.2);
    p.x -= uShear * hgt * hgt;

    vec4 acc = vec4(0.0);

    // Jar position (when held, it rides with the body).
    vec2 jp = uJar.xy;
    vec2 jsp = uJarHeld > 0.5 ? p : sp; // a loose jar is not squashed with the body
    float jarVis = uJar.z;
    float level = uJar.w;
    // Soft glow pool under the jar / body (on black this reads as light on the floor).
    float pool = exp(-pow(length(((uJarHeld > 0.5 ? p : sp) - vec2(uJar.x, -0.58)) * vec2(1.0, 4.0)), 2.0) * 6.0) * uJar.z;
    over(acc, uGlow * 0.9, pool * 0.35);

    float jarDist = sdRoundBox((jsp - jp) / JS, vec2(0.105, 0.17), 0.045) * JS;
    float jarLight = jarVis * (0.08 + 0.32 * level) * exp(-max(length(p - jp) - 0.17, 0.0) * 4.0) * 0.6;

    // ---- Body ----
    float db = bodySdf(p);
    vec2 e = vec2(0.004, 0.0);
    vec2 gb = normalize(vec2(bodySdf(p + e.xy) - bodySdf(p - e.xy), bodySdf(p + e.yx) - bodySdf(p - e.yx)) + 1e-5);
    vec3 nb = domeNormal(db, gb, 0.32);
    // Pressed against the glass: the front flattens.
    float flatten = press * smoothstep(0.3, 0.0, length(p - vec2(0.0, 0.1)));
    nb = normalize(mix(nb, vec3(0.0, 0.0, 1.0), flatten));
    vec3 cream = uBase;
    float dArmL = armSdf(p, uArmL), dArmR = armSdf(p, uArmR);
    float dFeet = min(footSdf(p, -1.0), footSdf(p, 1.0));
    float ao = 0.72 + 0.28 * smoothstep(0.0, 0.07, min(min(dArmL, dArmR), dFeet));
    ao *= 0.8 + 0.2 * smoothstep(-0.55, -0.35, p.y);
    if (jarVis > 0.01 && uJarHeld > 0.5) ao *= 0.86 + 0.14 * smoothstep(0.0, 0.08, jarDist);
    vec3 bodyCol = jellyShade(nb, cream, ao, p, jarLight);
    over(acc, bodyCol, fill(db));

    // ---- Face (moves a little with the gaze, like the head turning) ----
    vec2 f = p - vec2(uLook.x * 0.035, uLook.y * 0.02);
    float faceMask = fill(db + 0.02);

    // Blush with tiny hatch marks.
    for (int s = 0; s < 2; s++) {
        float side = s == 0 ? -1.0 : 1.0;
        vec2 bc = f - vec2(0.29 * side, 0.04);
        float bl = exp(-dot(bc / vec2(0.08, 0.045), bc / vec2(0.08, 0.045)));
        vec3 blushC = mix(vec3(1.0, 0.45, 0.48), uGlow, 0.15 * uBeat);
        over(acc, blushC, bl * (0.55 + 0.35 * uBlush) * faceMask);
        for (int k = 0; k < 3; k++) {
            float x0 = 0.29 * side + float(k - 1) * 0.022;
            float hd = sdSeg(f, vec2(x0 - 0.005, 0.03), vec2(x0 + 0.005, 0.052));
            over(acc, vec3(0.93, 0.36, 0.42), fill(hd - 0.003) * 0.3 * uBlush * faceMask);
        }
    }

    // Eyes.
    for (int s = 0; s < 2; s++) {
        float side = s == 0 ? -1.0 : 1.0;
        vec2 ec = f - vec2(0.165 * side, 0.15);
        vec2 look = uLook * vec2(0.012, 0.010);
        if (uEyeMode < 0.5 || uEyeMode > 3.5) {
            float open = uEyeOpen;
            if (uEyeMode > 3.5) open = min(open, 0.45); // tired
            vec2 r = vec2(0.06, 0.068 * max(open, 0.06));
            float de = sdEllipse(ec - vec2(0.0, (1.0 - open) * -0.02), r);
            // Glossy black with a deep-blue bottom bounce.
            vec3 eyeC = mix(vec3(0.02, 0.02, 0.035), vec3(0.12, 0.13, 0.22), smoothstep(0.02, -0.06, ec.y));
            over(acc, eyeC, fill(de) * faceMask);
            if (open > 0.35) {
                float h1 = length(ec - look - vec2(-0.02, 0.024)) - 0.021;
                float h2 = length(ec - look - vec2(0.021, -0.024)) - 0.009;
                over(acc, vec3(1.0), (fill(h1) + fill(h2) * 0.85) * faceMask * smoothstep(0.35, 0.6, open));
                if (uTears > 0.01) {
                    // Watery shine along the bottom lid.
                    float wl = sdEllipse(ec - vec2(0.0, -0.045), vec2(0.05, 0.012));
                    over(acc, vec3(0.75, 0.9, 1.0), fill(wl) * 0.7 * uTears * faceMask);
                }
            } else {
                // Closed: a soft dark line.
                float ln = sdArc(ec * vec2(1.0, -1.0) + vec2(0.0, 0.03), 0.05, 0.9) - 0.006;
                over(acc, vec3(0.08, 0.07, 0.1), fill(ln) * faceMask);
            }
            if (uEyeMode > 3.5) {
                // Heavy lids for tired.
                float lid = sdSeg(ec, vec2(-0.06, 0.008), vec2(0.06, 0.008)) - 0.007;
                over(acc, vec3(0.86, 0.8, 0.78), fill(lid) * faceMask);
            }
        } else if (uEyeMode < 1.5) {
            float ln = sdArc(ec + vec2(0.0, 0.01), 0.045, 1.05) - 0.009; // ^ ^
            over(acc, vec3(0.06, 0.05, 0.08), fill(ln) * faceMask);
        } else if (uEyeMode < 2.5) {
            float pulse = 1.0 + 0.12 * sin(uTime * 9.0);
            float dh = sdHeart((ec * vec2(1.0, 1.0) + vec2(0.0, 0.05)) / (0.085 * pulse)) * 0.085 * pulse;
            over(acc, vec3(1.0, 0.28, 0.5), fill(dh) * faceMask);
            float hl = length(ec - vec2(-0.02, 0.012)) - 0.012;
            over(acc, vec3(1.0, 0.85, 0.9), fill(hl) * faceMask);
        } else {
            float ln = sdArc(ec * vec2(1.0, -1.0) + vec2(0.0, 0.025), 0.05, 0.95) - 0.006; // sleeping
            over(acc, vec3(0.08, 0.07, 0.1), fill(ln) * faceMask);
        }
    }

    // Mouth.
    vec2 mc = f - vec2(0.0, 0.075);
    vec3 mouthC = vec3(0.16, 0.08, 0.1);
    if (uMouth < 0.5) {
        // Tiny cat "w".
        float w1 = sdArc((mc - vec2(-0.015, 0.006)) * vec2(1.0, -1.0), 0.016, 1.3);
        float w2 = sdArc((mc - vec2(0.015, 0.006)) * vec2(1.0, -1.0), 0.016, 1.3);
        over(acc, mouthC, fill(min(w1, w2) - 0.0045) * faceMask);
    } else if (uMouth < 1.5) {
        float wob = 0.004 * sin(uTime * 30.0);
        float dm = sdEllipse(mc + vec2(0.0, 0.012), vec2(0.04 + wob, 0.03));
        over(acc, vec3(0.32, 0.08, 0.12), fill(dm) * faceMask);
        float tongue = sdEllipse(mc + vec2(0.0, 0.03), vec2(0.022, 0.01));
        over(acc, vec3(1.0, 0.45, 0.5), fill(max(tongue, dm)) * faceMask);
    } else if (uMouth < 2.5) {
        float dm = sdEllipse(mc, vec2(0.014, 0.012 + 0.004 * sin(uTime * 8.0)));
        over(acc, mouthC, fill(dm) * faceMask);
    } else {
        float dm = max(sdEllipse(mc + vec2(0.0, 0.004), vec2(0.05, 0.04)), mc.y - 0.004);
        over(acc, vec3(0.32, 0.08, 0.12), fill(dm) * faceMask);
        float tongue = sdEllipse(mc + vec2(0.0, 0.03), vec2(0.024, 0.012));
        over(acc, vec3(1.0, 0.45, 0.5), fill(max(tongue, dm)) * faceMask);
    }

    // Tears: streams from the outer eye corners, drops falling off the cheeks.
    if (uTears > 0.01) {
        for (int s = 0; s < 2; s++) {
            float side = s == 0 ? -1.0 : 1.0;
            vec2 tc = f - vec2(0.205 * side, 0.11);
            float stream = sdSeg(tc, vec2(0.0), vec2(0.03 * side, -0.16)) - 0.012 * (1.0 + 0.3 * sin(uTime * 12.0 + float(s)));
            over(acc, vec3(0.62, 0.86, 1.0), fill(stream) * 0.8 * uTears);
            for (int k = 0; k < 3; k++) {
                float ph = fract(uTime * 1.6 + float(k) * 0.33 + float(s) * 0.17);
                vec2 dp = vec2(0.035 * side + side * ph * 0.06, -0.17 - ph * 0.35);
                float drop = length((tc - dp) * vec2(1.0, 0.75)) - 0.014 * (1.0 - ph * 0.5);
                over(acc, vec3(0.7, 0.9, 1.0), fill(drop) * uTears * (1.0 - ph));
            }
        }
    }

    // ---- Feet (in front of the belly) ----
    for (int s = 0; s < 2; s++) {
        float side = s == 0 ? -1.0 : 1.0;
        float df = footSdf(p, side);
        vec2 gf = normalize(vec2(footSdf(p + e.xy, side) - footSdf(p - e.xy, side), footSdf(p + e.yx, side) - footSdf(p - e.yx, side)) + 1e-5);
        over(acc, jellyShade(domeNormal(df, gf, 0.08), cream, 0.94, p, jarLight), fill(df));
    }

    // ---- Battery jar ----
    if (jarVis > 0.01) {
        vec2 q = (jsp - jp) / JS;
        // Glass.
        float glass = sdRoundBox(q, vec2(0.1, 0.13), 0.03);
        float edge = smoothstep(0.012, 0.0, abs(glass) - 0.004);
        // Liquid.
        float top = -0.12 + level * 0.235 + 0.006 * sin(uTime * 3.0 + q.x * 30.0) + 0.012 * uBeat;
        float liquid = max(sdRoundBox(q, vec2(0.09, 0.12), 0.025), q.y - top);
        vec3 lowC = level < 0.2 ? vec3(1.0, 0.25, 0.2) : uGlow;
        vec3 liqC = mix(lowC * 0.7, mix(lowC, vec3(1.0, 1.0, 0.6), 0.35), smoothstep(-0.12, top, q.y));
        liqC *= 1.25 + 0.4 * uBeat;
        float inner = fillJ(glass);
        over(acc, vec3(0.6, 0.85, 0.75), inner * 0.16 * jarVis);
        over(acc, liqC, fillJ(liquid) * jarVis);
        // Bubbles rising in the liquid.
        for (int k = 0; k < 5; k++) {
            float fk = float(k);
            float ph = fract(uTime * (0.35 + 0.1 * hash(fk)) + hash(fk + 3.0));
            vec2 bp = vec2((hash(fk + 7.0) - 0.5) * 0.13, -0.11 + ph * (top + 0.11));
            float bub = abs(length(q - bp) - 0.009) - 0.0025;
            over(acc, vec3(1.0), fillJ(bub) * 0.7 * jarVis * step(bp.y, top));
        }
        // Lightning bolt and plus/minus marks.
        vec2 bq = q - vec2(0.0, -0.02);
        float bolt = min(sdSeg(bq, vec2(0.02, 0.06), vec2(-0.02, 0.0)), min(sdSeg(bq, vec2(-0.02, 0.0), vec2(0.02, 0.0)), sdSeg(bq, vec2(0.02, 0.0), vec2(-0.02, -0.06)))) - 0.009;
        over(acc, vec3(1.0, 0.96, 0.55), fillJ(bolt) * jarVis * 0.95);
        float plus = min(sdSeg(q, vec2(-0.016, 0.085), vec2(0.016, 0.085)), sdSeg(q, vec2(0.0, 0.069), vec2(0.0, 0.101))) - 0.005;
        over(acc, vec3(1.0, 0.95, 0.5), fillJ(plus) * jarVis * 0.9);
        float minus = sdSeg(q, vec2(-0.014, -0.1), vec2(0.014, -0.1)) - 0.004;
        over(acc, vec3(0.2, 0.3, 0.2), fillJ(minus) * jarVis * 0.7);
        // Glass edges and a vertical reflection streak.
        over(acc, vec3(0.85, 1.0, 0.95), edge * 0.5 * jarVis);
        float streak = sdRoundBox(q - vec2(-0.062, 0.01), vec2(0.008, 0.08), 0.008);
        over(acc, vec3(1.0), fillJ(streak) * 0.35 * jarVis);
        // Metal caps.
        for (int c = 0; c < 2; c++) {
            float cy = c == 0 ? 0.148 : -0.148;
            float cap = sdRoundBox(q - vec2(0.0, cy), vec2(0.108, 0.026), 0.012);
            float g = clamp((q.y - cy) / 0.026 * 0.5 + 0.5, 0.0, 1.0);
            vec3 metal = mix(vec3(0.38, 0.4, 0.44), vec3(0.92, 0.94, 0.97), g);
            metal += vec3(1.0) * smoothstep(0.02, 0.0, abs(q.x + 0.05)) * 0.25;
            over(acc, metal, fillJ(cap) * jarVis);
        }
        float nub = sdRoundBox(q - vec2(0.0, 0.185), vec2(0.03, 0.014), 0.006);
        over(acc, vec3(0.75, 0.77, 0.8), fillJ(nub) * jarVis);
    }

    // Straw from the jar to the mouth.
    if (uSip > 0.01 && jarVis > 0.01) {
        vec2 a = jp + vec2(0.0, 0.195 * JS);
        vec2 b = vec2(0.0, 0.075) + vec2(uLook.x * 0.035, uLook.y * 0.02) - vec2(0.0, 0.01);
        vec2 mid = mix(a, b, 0.5) + vec2(0.025, 0.0);
        float ds = min(sdSeg(p, a, mid), sdSeg(p, mid, b));
        over(acc, vec3(0.9, 1.0, 0.97), fill(ds - 0.011) * 0.55 * uSip);
        // Juice travelling up the straw.
        float flow = fract(uTime * 1.4);
        float juice = min(sdSeg(p, a, mix(a, mid, min(flow * 2.0, 1.0))), flow > 0.5 ? sdSeg(p, mid, mix(mid, b, flow * 2.0 - 1.0)) : 1.0);
        over(acc, uGlow * 1.2, fill(juice - 0.006) * 0.85 * uSip);
    }

    // ---- Arms (over the jar when hugging) ----
    for (int s = 0; s < 2; s++) {
        vec4 arm = s == 0 ? uArmL : uArmR;
        float da = armSdf(p, arm);
        vec2 ga = normalize(vec2(armSdf(p + e.xy, arm) - armSdf(p - e.xy, arm), armSdf(p + e.yx, arm) - armSdf(p - e.yx, arm)) + 1e-5);
        over(acc, jellyShade(domeNormal(da, ga, 0.07), cream, 0.97, p, jarLight * 1.6), fill(da));
    }

    // Glass press: a flat reflection where the face touches the screen.
    if (press > 0.01) {
        float smear = exp(-dot((p - vec2(-0.05, 0.2)) / vec2(0.3, 0.2), (p - vec2(-0.05, 0.2)) / vec2(0.3, 0.2)));
        over(acc, vec3(1.0), smear * 0.12 * press * fill(db));
    }

    // ---- Neon battery icon above the head (charging) ----
    if (uBattIcon > 0.01) {
        vec2 q = sp - vec2(0.0, 0.78);
        float frame = abs(sdRoundBox(q, vec2(0.16, 0.065), 0.025)) - 0.007;
        float tip = sdRoundBox(q - vec2(0.18, 0.0), vec2(0.012, 0.025), 0.006);
        float barW = 0.13 * level;
        float bar = sdRoundBox(q - vec2(-0.135 + barW, 0.0), vec2(barW, 0.04), 0.012);
        float glow = exp(-max(min(frame, tip), 0.0) * 60.0);
        over(acc, uGlow, (fill(min(frame, tip)) + glow * 0.35) * uBattIcon);
        over(acc, uGlow * 0.8, fill(bar) * uBattIcon * 0.85);
    }

    // ---- Bird on the antenna-less head ----
    if (uBird.w > 0.01) {
        vec2 q = sp - uBird.xy;
        float bodyB = sdEllipse(q, vec2(0.065, 0.055));
        float headB = length(q - vec2(0.045, 0.045)) - 0.04;
        float b = smin(bodyB, headB, 0.02);
        vec3 bc = vec3(0.35, 0.65, 1.0);
        over(acc, mix(bc * 0.6, bc, clamp(q.y * 8.0 + 0.6, 0.0, 1.0)), fill(b) * uBird.w);
        over(acc, vec3(1.0, 0.95, 0.85), fill(sdEllipse(q - vec2(0.01, -0.012), vec2(0.035, 0.028))) * uBird.w);
        float wing = sdEllipse(rot(q - vec2(-0.02, 0.01), 0.5 + 0.7 * sin(uBird.z)), vec2(0.045, 0.022));
        over(acc, bc * 0.75, fill(wing) * uBird.w);
        over(acc, vec3(0.02), fill(length(q - vec2(0.058, 0.052)) - 0.008) * uBird.w);
        float beak = sdSeg(q, vec2(0.08, 0.04), vec2(0.1, 0.035)) - 0.006;
        over(acc, vec3(1.0, 0.7, 0.2), fill(beak) * uBird.w);
    }

    // ---- Effects ----
    for (int i = 0; i < 6; i++) {
        vec4 fx = uFx[i];
        if (fx.w < 0.5) continue;
        vec2 q = sp - fx.xy;
        float life = fx.z;
        float fade = smoothstep(0.0, 0.12, life) * smoothstep(1.0, 0.7, life);
        if (fx.w < 1.5) {
            float s = 0.05 * (0.8 + 0.4 * life);
            float d = sdHeart(q / s + vec2(0.0, 0.5)) * s;
            over(acc, vec3(1.0, 0.35, 0.55), fill(d) * fade);
        } else if (fx.w < 2.5) {
            float r = 0.04 * (1.0 - abs(life - 0.5));
            float star = min(sdSeg(q, vec2(-r, 0.0), vec2(r, 0.0)), sdSeg(q, vec2(0.0, -r), vec2(0.0, r))) - 0.004;
            over(acc, mix(uGlow, vec3(1.0), 0.6), (fill(star) + exp(-length(q) * 70.0) * 0.6) * fade);
        } else if (fx.w < 3.5) {
            // Bubble from the mouth: grows, then pops on the face.
            float r = 0.02 + 0.14 * smoothstep(0.0, 0.8, life);
            float ring = abs(length(q) - r) - 0.004;
            float popped = step(0.92, life);
            vec3 irid = 0.6 + 0.4 * cos(vec3(0.0, 2.0, 4.0) + atan(q.y, q.x) * 2.0 + uTime);
            over(acc, irid, fill(ring) * 0.6 * (1.0 - popped));
            over(acc, vec3(1.0), fill(length(q - vec2(-r * 0.4, r * 0.4)) - r * 0.15) * 0.6 * (1.0 - popped));
            over(acc, vec3(0.9, 0.95, 1.0), fill(length(q) - r * 1.2) * 0.25 * popped * (1.0 - smoothstep(0.92, 1.0, life)) * 4.0);
        } else if (fx.w < 4.5) {
            // Falling star with a tail.
            float body = length(q) - 0.025;
            float tail = sdSeg(q, vec2(0.0), vec2(0.12, 0.2)) - 0.006;
            over(acc, vec3(1.0, 0.9, 0.5), (fill(body) + exp(-length(q) * 25.0) * 0.6 + fill(tail) * 0.4) * fade);
        } else if (fx.w < 5.5) {
            // Z
            vec2 z = q / (0.035 + life * 0.02);
            float dz = min(min(sdSeg(z, vec2(-0.5, 0.5), vec2(0.5, 0.5)), sdSeg(z, vec2(0.5, 0.5), vec2(-0.5, -0.5))), sdSeg(z, vec2(-0.5, -0.5), vec2(0.5, -0.5)));
            over(acc, vec3(0.8, 0.85, 1.0), fill(dz * (0.035 + life * 0.02) - 0.004) * fade * 0.8);
        } else {
            // Sweat drop.
            float d = length((q + vec2(0.0, 0.01)) * vec2(1.0, 0.8)) - 0.022;
            d = smin(d, sdSeg(q, vec2(0.0, 0.0), vec2(0.0, 0.035)) - 0.004, 0.015);
            over(acc, vec3(0.6, 0.85, 1.0), fill(d) * fade);
        }
    }

    acc.rgb *= mix(1.0, 0.55, uDim);
    gl_FragColor = acc;
}
