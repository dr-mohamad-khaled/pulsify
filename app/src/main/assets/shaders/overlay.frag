// Pulsify — per-frame layer over a cached environment. GLSL ES 1.00. Kept deliberately cheap:
// one texture read, a twinkle on glowing pixels, a handful of particles, touch bursts.
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif

uniform sampler2D uTex;
uniform vec2  uRes;
uniform vec2  uHalf;
uniform float uFloor;
uniform float uTime;
uniform float uWorld;
uniform float uNight;     // 0 day .. 1 night
uniform float uBass;
uniform float uTreble;
uniform float uBeat;
uniform float uPar;       // home-screen parallax -1..1
uniform vec4  uBurst[3];  // x, y, age (s), strength
uniform float uBright;
uniform float uDim;

float h11(float n) { return fract(sin(n * 127.1) * 43758.5453); }
float h21(vec2 p) { p = fract(p * vec2(123.34, 456.21)); p += dot(p, p + 45.32); return fract(p.x * p.y); }

// A soft round particle that drifts on a loop. dir = travel per second (uv), wrap inside the screen.
float mote(vec2 p, float i, vec2 dir, float size, float seed) {
    vec2 start = vec2(h11(i + seed) * 2.0 - 1.0, h11(i * 3.7 + seed) * 2.0 - 1.0) * uHalf;
    vec2 span = uHalf * 2.0 + 0.2;
    vec2 pos = mod(start + dir * uTime + uHalf + 0.1, span) - uHalf - 0.1;
    pos.x += 0.03 * sin(uTime * (0.7 + h11(i)) + i);
    float d = length(p - pos);
    return exp(-d * d / (size * size));
}

void main() {
    vec2 uv = gl_FragCoord.xy / uRes;
    vec2 p = (uv - 0.5) * uHalf * 2.0;
    vec4 base = texture2D(uTex, clamp(uv + vec2(uPar * 0.006, 0.0), 0.001, 0.999));
    vec3 col = base.rgb;
    float glow = base.a;

    // Glowing pixels breathe, twinkle and kick with the bass.
    // Stars are painted with glow 0.6 exactly; only they twinkle. Every glowing pixel breathes with the music.
    vec2 cell = floor(p * 40.0);
    float star = 1.0 - smoothstep(0.02, 0.06, abs(glow - 0.6));
    float tw = 0.75 + 0.25 * sin(uTime * (1.3 + 3.0 * h21(cell)) + h21(cell + 5.0) * 40.0);
    col += col * glow * (0.35 * uBass + 0.3 * uBeat + 0.06 * sin(uTime * 1.7));
    col += col * star * (tw - 0.85) * uNight * 1.5;

    vec3 add = vec3(0.0);
    if (uWorld < 1.5) {
        // Cloud Nursery: pastel sparkles floating up.
        for (int i = 0; i < 8; i++) {
            float fi = float(i);
            float m = mote(p, fi, vec2(0.0, 0.05 + 0.03 * h11(fi)), 0.012, 3.0);
            add += (0.6 + 0.4 * cos(6.28 * (h11(fi + 9.0) + vec3(0.0, 0.33, 0.67)))) * m * (0.6 + 0.6 * uTreble);
        }
    } else if (uWorld < 2.5) {
        // Battery Lab: energy motes rising.
        for (int i = 0; i < 9; i++) {
            float fi = float(i);
            float m = mote(p, fi, vec2(0.0, 0.08 + 0.05 * h11(fi)), 0.01, 7.0);
            add += vec3(0.3, 1.0, 0.5) * m * (0.7 + uBass);
        }
    } else if (uWorld < 3.5) {
        // Backyard: petals by day, fireflies at night.
        for (int i = 0; i < 10; i++) {
            float fi = float(i);
            float m = mote(p, fi, vec2(0.04, -0.03 - 0.02 * h11(fi)), 0.011, 1.0);
            add += vec3(1.0, 0.75, 0.85) * m * 0.7 * (1.0 - uNight);
            float f = mote(p * vec2(1.0, 1.6) + vec2(0.0, -uFloor * 0.6), fi + 20.0, vec2(0.02 * sin(fi), 0.015 * cos(fi * 1.3)), 0.014, 5.0);
            float blink = smoothstep(0.2, 1.0, sin(uTime * (1.0 + h11(fi)) + fi * 2.0));
            add += vec3(0.8, 1.0, 0.35) * f * blink * uNight * (1.2 + uTreble);
        }
    } else if (uWorld < 4.5) {
        // Snow Day: two layers of falling snow.
        for (int layer = 0; layer < 2; layer++) {
            float fl = float(layer);
            float dens = 9.0 + fl * 9.0;
            vec2 q = p * dens + vec2(sin(uTime * 0.3 + fl) * 0.6, uTime * (1.2 + fl * 0.8));
            vec2 id = floor(q); vec2 f = fract(q) - 0.5;
            vec2 o = (vec2(h21(id), h21(id + 7.0)) - 0.5) * 0.6;
            float r = (0.12 - fl * 0.04) * (0.6 + 0.4 * h21(id + 3.0));
            float flake = smoothstep(r, r * 0.3, length(f - o)) * step(0.35, h21(id + 11.0));
            add += vec3(1.0) * flake * (0.75 - fl * 0.25);
        }
    } else if (uWorld > 8.5 && uWorld < 9.5) {
        // Rainforest: mist motes by day, fireflies at night.
        for (int i = 0; i < 10; i++) {
            float fi = float(i);
            float m = mote(p, fi, vec2(0.015 * sin(fi), 0.012), 0.012, 17.0);
            add += vec3(0.85, 1.0, 0.85) * m * 0.25 * (1.0 - uNight);
            float f = mote(p, fi + 30.0, vec2(0.03 * sin(fi * 1.7), 0.02 * cos(fi)), 0.012, 19.0);
            float blink = smoothstep(0.2, 1.0, sin(uTime * (1.2 + h11(fi)) + fi * 2.0));
            add += vec3(0.7, 1.0, 0.4) * f * blink * uNight * (1.3 + uTreble);
        }
    } else if (uWorld > 9.5 && uWorld < 10.5) {
        // Palm Beach: sun glints dancing on the water.
        if (p.y < uFloor + 0.42) {
            vec2 q = p * vec2(30.0, 90.0) + vec2(uTime * 0.3, 0.0);
            vec2 id = floor(q); vec2 f = fract(q) - 0.5;
            float gl = step(0.93, h21(id)) * smoothstep(0.25, 0.0, length(f * vec2(1.0, 2.0)));
            float tw2 = 0.5 + 0.5 * sin(uTime * 5.0 + h21(id + 3.0) * 30.0);
            add += vec3(1.0, 0.95, 0.85) * gl * tw2 * (0.5 + uTreble) * (1.0 - 0.6 * uNight) * smoothstep(-0.2, 0.2, base.a + 0.2);
        }
    } else if (uWorld > 10.5) {
        // Blossom Garden: petals spinning down.
        for (int i = 0; i < 12; i++) {
            float fi = float(i);
            vec2 start = vec2(h11(fi + 40.0) * 2.0 - 1.0, h11(fi * 3.1 + 40.0) * 2.0 - 1.0) * uHalf;
            vec2 span = uHalf * 2.0 + 0.2;
            vec2 pos = mod(start + vec2(0.035, -0.06 - 0.03 * h11(fi)) * uTime + uHalf + 0.1, span) - uHalf - 0.1;
            pos.x += 0.05 * sin(uTime * (0.8 + h11(fi)) + fi);
            vec2 q = p - pos;
            float a = uTime * (1.5 + h11(fi + 2.0)) + fi;
            q = vec2(cos(a) * q.x - sin(a) * q.y, sin(a) * q.x + cos(a) * q.y);
            q.x *= 1.0 + 0.8 * abs(sin(a * 0.7));
            float pet = length(q * vec2(1.0, 1.7)) - 0.011;
            add += mix(vec3(1.0, 0.7, 0.82), vec3(0.5, 0.35, 0.5), uNight) * smoothstep(0.004, -0.004, pet) * 0.9;
        }
    } else if (uWorld < 5.5 || uWorld > 7.5) {
        // Cozy Room / Box Fort: dust motes drifting in warm light.
        for (int i = 0; i < 7; i++) {
            float fi = float(i);
            float m = mote(p, fi, vec2(0.01 * sin(fi), 0.01), 0.006, 11.0);
            add += vec3(1.0, 0.85, 0.6) * m * 0.45;
        }
    } else if (uWorld < 6.5) {
        // Windowsill: raindrops sliding down the glass on rainy nights; dust motes by day.
        for (int i = 0; i < 8; i++) {
            float fi = float(i);
            float m = mote(p, fi, vec2(0.0, -0.06 - 0.06 * h11(fi)), 0.008, 13.0);
            add += mix(vec3(1.0, 0.9, 0.7) * 0.35, vec3(0.7, 0.8, 1.0) * 0.5, uNight) * m;
        }
    } else {
        // Rooftop: a little plane blinking across the sky, and drifting embers of city light.
        float t = mod(uTime * 0.05, 1.0);
        vec2 plane = vec2(mix(-uHalf.x - 0.2, uHalf.x + 0.2, t), uHalf.y - 0.25);
        float blink = step(0.5, fract(uTime * 1.2));
        add += vec3(1.0, 0.3, 0.3) * exp(-length(p - plane) * 260.0) * 2.0 * blink;
        add += vec3(1.0) * exp(-length(p - plane - vec2(0.01, 0.0)) * 300.0) * 1.5 * (1.0 - blink);
    }

    // Touch bursts: a ring of sparks.
    for (int b = 0; b < 3; b++) {
        vec4 br = uBurst[b];
        if (br.w <= 0.0) continue;
        vec2 q = p - br.xy;
        float age = br.z;
        float r = age * 0.5;
        float ring = exp(-pow((length(q) - r) * 30.0, 2.0)) * exp(-age * 2.5);
        float ang = atan(q.y, q.x);
        float sparks = pow(max(0.0, cos(ang * 8.0 + br.x * 10.0)), 12.0);
        vec3 bc = uWorld > 3.5 && uWorld < 4.5 ? vec3(0.85, 0.95, 1.0) : vec3(1.0, 0.85, 0.6);
        add += bc * ring * (0.4 + sparks) * br.w;
    }

    col += add;
    col *= uBright * mix(1.0, 0.45, uDim);
    gl_FragColor = vec4(col, 1.0);
}
