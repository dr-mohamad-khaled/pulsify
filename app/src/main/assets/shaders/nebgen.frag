// Pulsify — paints the Nebula once into a texture (no pictures). Expensive on purpose: it runs
// a single time when the Nebula is chosen, then the sky shader animates the result.
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif

uniform vec2 uRes;
uniform float uSeed;

float h21(vec2 p) { p = fract(p * vec2(123.34, 456.21) + uSeed * 0.013); p += dot(p, p + 45.32); return fract(p.x * p.y); }
float vnoise(vec2 p) {
    vec2 i = floor(p), f = fract(p); vec2 u = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);
    return mix(mix(h21(i), h21(i + vec2(1.0, 0.0)), u.x), mix(h21(i + vec2(0.0, 1.0)), h21(i + vec2(1.0, 1.0)), u.x), u.y);
}
float fbm(vec2 p) {
    float v = 0.0, a = 0.5;
    mat2 r = mat2(0.8, 0.6, -0.6, 0.8);
    for (int i = 0; i < 7; i++) { v += a * vnoise(p); p = r * p * 2.03 + 11.7; a *= 0.5; }
    return v;
}
float ridge(vec2 p) {
    float v = 0.0, a = 0.5;
    mat2 r = mat2(0.8, 0.6, -0.6, 0.8);
    for (int i = 0; i < 6; i++) { float n = 1.0 - abs(vnoise(p) * 2.0 - 1.0); v += a * n * n; p = r * p * 2.1 + 3.1; a *= 0.5; }
    return v;
}

vec3 stars(vec2 p, float dens) {
    vec3 c = vec3(0.0);
    for (int L = 0; L < 3; L++) {
        float fl = float(L);
        float sc = 70.0 + fl * 90.0;
        vec2 g = p * sc; vec2 id = floor(g); vec2 f = fract(g) - 0.5;
        float r = h21(id + fl * 31.0);
        if (r > 0.94 - dens * 0.04) {
            vec2 o = (vec2(h21(id + 3.0 + fl), h21(id + 9.0 + fl)) - 0.5) * 0.7;
            float d = length(f - o);
            vec3 col = mix(vec3(0.7, 0.8, 1.0), vec3(1.0, 0.85, 0.7), h21(id + 5.0));
            c += col * smoothstep(0.12, 0.0, d) * (0.35 + 0.65 * h21(id + 2.0)) / (1.0 + fl * 0.8);
        }
    }
    return c;
}

// A big bright star with a halo and four soft diffraction spikes.
vec3 bigStar(vec2 p, vec2 c, float s, vec3 col) {
    vec2 d = p - c;
    float r = length(d);
    float core = exp(-r * r / (s * s * 0.02)) * 2.2;
    float halo = exp(-r / (s * 0.9)) * 0.35;
    float spikes = (exp(-abs(d.x) / (s * 0.01)) * exp(-abs(d.y) / (s * 0.55)) + exp(-abs(d.y) / (s * 0.01)) * exp(-abs(d.x) / (s * 0.55))) * 0.6;
    return col * (core + halo + spikes);
}

void main() {
    vec2 uv = gl_FragCoord.xy / uRes;
    vec2 p = (gl_FragCoord.xy - 0.5 * uRes) / min(uRes.x, uRes.y);
    vec2 so = vec2(uSeed * 1.7, uSeed * 0.9);

    // Domain-warped gas: two warps for curling, wispy structure.
    vec2 q = vec2(fbm(p * 1.3 + so), fbm(p * 1.3 + so + vec2(5.2, 1.3)));
    vec2 r = vec2(fbm(p * 1.3 + 3.2 * q + vec2(1.7, 9.2)), fbm(p * 1.3 + 3.2 * q + vec2(8.3, 2.8)));
    float gas = fbm(p * 1.6 + 2.6 * r + so);
    float wisps = ridge(p * 2.4 + 1.8 * r);

    // Shape the cloud: dense along a diagonal river, thinner toward the corners.
    float river = exp(-pow(dot(p, normalize(vec2(0.62, -1.0))) * 1.6 + 0.25 * sin(p.y * 2.3), 2.0));
    float shape = clamp(river * 1.25 + 0.05, 0.0, 1.0) * smoothstep(1.25, 0.2, length(p * vec2(1.0, 0.6)));
    float dens = pow(smoothstep(0.38, 0.9, gas), 1.6) * shape * shape;

    // Emission colours: hydrogen magenta, oxygen teal, a warm golden heart.
    vec3 magenta = vec3(0.95, 0.22, 0.55);
    vec3 teal = vec3(0.12, 0.65, 0.85);
    vec3 gold = vec3(1.0, 0.72, 0.38);
    vec3 violet = vec3(0.42, 0.24, 0.95);
    float mixT = smoothstep(0.25, 0.75, q.x);
    vec3 col = mix(teal, magenta, mixT);
    col = mix(col, violet, smoothstep(0.55, 0.9, r.y) * 0.6);
    vec3 neb = col * dens * 1.25;
    neb += mix(magenta, gold, 0.5) * pow(wisps, 4.0) * shape * shape * 0.55;
    // Glowing cores where the gas is densest.
    float core = smoothstep(0.62, 0.95, gas) * shape;
    neb += gold * core * core * 1.6 + vec3(1.0, 0.9, 0.85) * pow(core, 4.0) * 1.2;

    // Dark dust lanes cutting through the light.
    float dust = smoothstep(0.45, 0.8, ridge(p * 3.2 + 2.2 * q + 7.0)) * smoothstep(0.2, 0.7, gas);
    neb *= 1.0 - dust * 0.85;
    // Soft scattered glow so the cloud never ends in a hard edge.
    neb += col * 0.05 * shape * shape;

    // Stars: dense in the bright regions, a few heroes with spikes.
    vec3 st = stars(p, dens) * (1.0 - dust * 0.8);
    st += bigStar(p, vec2(0.18, 0.42), 0.12, vec3(0.85, 0.92, 1.0));
    st += bigStar(p, vec2(-0.3, -0.35), 0.08, vec3(1.0, 0.86, 0.75));
    st += bigStar(p, vec2(0.36, -0.62), 0.06, vec3(0.8, 0.9, 1.0));
    st += bigStar(p, vec2(-0.12, 0.78), 0.05, vec3(1.0, 0.8, 0.9));

    vec3 c = neb + st;
    // Filmic shoulder: bright stays rich, never clips flat.
    c = 1.0 - exp(-c * 1.5);
    c = pow(c, vec3(1.15));
    // AMOLED: the empty sky stays truly black.
    c = max(c - 0.012, 0.0);
    gl_FragColor = vec4(c, 1.0);
}
