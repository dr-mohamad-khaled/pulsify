// Pulsify — Night Sky world. GLSL ES 1.00, shared by the Android engine and the web preview.
// Everything starts from pure #000000 so idle pixels stay off on AMOLED.
precision highp float;

uniform vec2  uRes;       // surface size in pixels
uniform float uTime;
uniform float uBass;      // 0..1
uniform float uMid;
uniform float uTreble;
uniform float uBeat;      // decaying beat pulse 0..1
uniform vec3  uA1;        // accent 1
uniform vec3  uA2;        // accent 2
uniform float uVariant;   // 0 night sky, 1 aurora, 2 nebula
uniform vec2  uPar;       // parallax from home-screen pages
uniform vec4  uRip[4];    // ripple: centre (uv space), age s, strength
uniform vec4  uShoot;     // shooting star head (uv), fade, unused
uniform vec2  uShootDir;  // direction of travel (uv space, normalised)
uniform vec3  uTouch;     // finger (uv), strength
uniform float uDim;       // 0 awake .. 1 asleep
uniform float uBright;    // user brightness 0.6..1.4
uniform vec2  uCharge;    // x: battery level 0..1, y: charging 0/1
uniform float uWish;      // long-press wish glow 0..1

float h21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = h21(i);
    float b = h21(i + vec2(1.0, 0.0));
    float c = h21(i + vec2(0.0, 1.0));
    float d = h21(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += a * vnoise(p);
        p = p * 2.03 + 17.1;
        a *= 0.5;
    }
    return v;
}

vec3 starLayer(vec2 uv, float scale, float seed, float twinkleGain) {
    vec2 g = uv * scale;
    vec2 id = floor(g);
    vec2 f = fract(g) - 0.5;
    float r = h21(id + seed);
    if (r < 0.86) return vec3(0.0);
    vec2 off = vec2(h21(id + seed + 3.1), h21(id + seed + 7.7)) - 0.5;
    float d = length(f - off * 0.7);
    float size = mix(0.018, 0.06, h21(id + seed + 11.0));
    float tw = 0.55 + 0.45 * sin(uTime * (1.2 + 3.0 * h21(id + 9.0)) + r * 40.0);
    tw = mix(tw, 1.0, 0.25) + twinkleGain * uTreble * h21(id + 4.0);
    float core = smoothstep(size, 0.0, d);
    float glint = smoothstep(size * 3.5, 0.0, d) * 0.12;
    vec3 col = mix(vec3(0.74, 0.80, 1.0), vec3(1.0, 0.86, 0.72), h21(id + 21.0));
    return col * (core + glint) * tw;
}

void main() {
    vec2 fc = gl_FragCoord.xy;
    float mn = min(uRes.x, uRes.y);
    vec2 uv = (fc - 0.5 * uRes) / mn;
    vec2 suv = fc / uRes;

    // Ripples bend the sky like a lens and leave a soft ring.
    vec2 disp = vec2(0.0);
    float ring = 0.0;
    for (int i = 0; i < 4; i++) {
        vec4 r = uRip[i];
        if (r.w > 0.0) {
            vec2 dv = uv - r.xy;
            float d = length(dv);
            float rad = r.z * 0.85;
            float w = exp(-pow((d - rad) * 16.0, 2.0)) * r.w * exp(-r.z * 1.7);
            disp += (dv / (d + 1e-4)) * w * 0.018;
            ring += w;
        }
    }
    vec2 p = uv + disp;

    vec3 col = vec3(0.0);
    col += starLayer(p + uPar * 0.015 + vec2(uTime * 0.0015, 0.0), 30.0, 1.0, 0.6) * 0.45;
    col += starLayer(p + uPar * 0.04 + vec2(uTime * 0.003, 0.0), 15.0, 7.0, 1.0) * 0.75;
    col += starLayer(p + uPar * 0.08, 8.0, 13.0, 1.5) * (1.0 + uBeat * 0.35);

    if (uVariant < 0.5) {
        // Faint milky band.
        float band = exp(-pow(dot(p + uPar * 0.03, normalize(vec2(0.55, -1.0))) * 2.6, 2.0));
        float n = fbm(p * 3.0 + vec2(uTime * 0.008, 0.0));
        col += mix(uA2, uA1, n) * band * n * n * 0.16 * (1.0 + uBass * 0.7);
    } else if (uVariant < 1.5) {
        // Aurora curtains in the upper half.
        float y = p.y;
        float n = fbm(vec2(p.x * 1.4 + uTime * 0.04 + uPar.x * 0.1, uTime * 0.025));
        float h = 0.05 + n * 0.32;
        float curtain = smoothstep(h - 0.16, h + 0.015, y) * smoothstep(h - 0.16, h - 0.02, y) * exp(-max(y - h, 0.0) * 3.4);
        float streak = 0.55 + 0.45 * vnoise(vec2(p.x * 38.0 + n * 9.0, uTime * 0.35));
        vec3 ac = mix(uA1, uA2, clamp((y - h) * 2.2, 0.0, 1.0));
        col += ac * curtain * streak * (0.22 + 0.45 * uMid + 0.2 * uBeat);
    } else {
        // Nebula clouds.
        float n1 = fbm(p * 1.7 + vec2(uTime * 0.01, 0.0) + uPar * 0.05);
        float n2 = fbm(p * 3.4 - n1 * 1.6 + uTime * 0.012);
        float dens = pow(n2, 3.0) * 1.7;
        float vig = smoothstep(1.1, 0.15, length(uv));
        col += mix(uA1, uA2, n1) * dens * vig * (0.22 + 0.4 * uBass);
    }

    // Shooting star with a tapering tail.
    if (uShoot.z > 0.0) {
        vec2 head = uShoot.xy;
        vec2 tailEnd = head - uShootDir * 0.28;
        vec2 pa = p - tailEnd;
        vec2 ba = head - tailEnd;
        float t = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
        float d = length(pa - ba * t);
        float w = mix(0.0008, 0.005, t);
        float s = smoothstep(w, 0.0, d) * t * t;
        float glow = smoothstep(0.03, 0.0, length(p - head)) * 0.35;
        col += vec3(1.0, 0.95, 0.88) * (s + glow) * uShoot.z;
    }

    vec3 mixA = mix(uA1, uA2, 0.5);
    col += mixA * ring * 0.22;
    col += uA1 * exp(-length(uv - uTouch.xy) * 14.0) * 0.22 * uTouch.z;
    col += uA2 * exp(-length(uv - uTouch.xy) * 4.0) * 0.18 * uWish;
    col += uA1 * uBeat * 0.035 * smoothstep(0.0, 0.9, 1.0 - suv.y);

    // Charging: a soft green horizon that rises with the battery level.
    if (uCharge.y > 0.5) {
        float top = 0.04 + uCharge.x * 0.16;
        float glowY = exp(-max(suv.y, 0.0) / top) * 0.16;
        float wave = 0.75 + 0.25 * sin(suv.x * 9.0 - uTime * 2.0);
        col += vec3(0.29, 0.87, 0.5) * glowY * wave;
    }

    col *= mix(1.0, 0.42, uDim) * uBright;
    // AMOLED black floor: anything barely lit is switched fully off.
    col = max(col - 0.006, 0.0) * 1.006;
    gl_FragColor = vec4(col, 1.0);
}
