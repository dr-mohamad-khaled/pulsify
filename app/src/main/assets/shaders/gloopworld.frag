// Pulsify: the Gloop Gang's simple jelly worlds (ids 28-31), split out of world.frag so only
// this small shader is compiled for them, not every old procedural world. GLSL ES 1.00.
// Rendered into a cached texture only when something changes (world, size, about once a minute
// for the clock), so it can afford rich detail. RGB = colour, A = how much the pixel glows
// (lamps, windows, moon, neon): the cheap per-frame overlay pulses those with the music.
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif

uniform vec2  uRes;      // texture size in pixels
uniform vec2  uHalf;     // half screen size in uv (short side = 1)
uniform float uFloor;    // creature floor line, uv y
uniform float uWorld;    // which environment
uniform float uHour;     // 0..24 local time
uniform float uSeed;

// ---------- helpers ----------
float h21(vec2 p) { p = fract(p * vec2(123.34, 456.21)); p += dot(p, p + 45.32); return fract(p.x * p.y); }
float vnoise(vec2 p) {
    vec2 i = floor(p), f = fract(p); f = f * f * (3.0 - 2.0 * f);
    return mix(mix(h21(i), h21(i + vec2(1, 0)), f.x), mix(h21(i + vec2(0, 1)), h21(i + vec2(1, 1)), f.x), f.y);
}
float fbm(vec2 p) { float v = 0.0, a = 0.5; for (int i = 0; i < 5; i++) { v += a * vnoise(p); p = p * 2.02 + 7.3; a *= 0.5; } return v; }
float sdBox(vec2 p, vec2 b) { vec2 q = abs(p) - b; return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0); }
float sdRoundBox(vec2 p, vec2 b, float r) { return sdBox(p, b - r) - r; }
float sdEllipse(vec2 p, vec2 r) { float k0 = length(p / r); float k1 = length(p / (r * r)); return k0 * (k0 - 1.0) / max(k1, 1e-4); }
float sdSeg(vec2 p, vec2 a, vec2 b) { vec2 pa = p - a, ba = b - a; float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0); return length(pa - ba * h); }
float sdCapsule(vec2 p, vec2 a, vec2 b, float ra, float rb) { vec2 pa = p - a, ba = b - a; float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0); return length(pa - ba * h) - mix(ra, rb, h); }
float sdTri(vec2 p, vec2 a, vec2 b, vec2 c) {
    vec2 e0 = b - a, e1 = c - b, e2 = a - c, v0 = p - a, v1 = p - b, v2 = p - c;
    vec2 pq0 = v0 - e0 * clamp(dot(v0, e0) / dot(e0, e0), 0.0, 1.0);
    vec2 pq1 = v1 - e1 * clamp(dot(v1, e1) / dot(e1, e1), 0.0, 1.0);
    vec2 pq2 = v2 - e2 * clamp(dot(v2, e2) / dot(e2, e2), 0.0, 1.0);
    float s = sign(e0.x * e2.y - e0.y * e2.x);
    vec2 d = min(min(vec2(dot(pq0, pq0), s * (v0.x * e0.y - v0.y * e0.x)), vec2(dot(pq1, pq1), s * (v1.x * e1.y - v1.y * e1.x))), vec2(dot(pq2, pq2), s * (v2.x * e2.y - v2.y * e2.x)));
    return -sqrt(d.x) * sign(d.y);
}
float px;   // one texture pixel in uv
float fill(float d) { return clamp(0.5 - d / (px * 1.5), 0.0, 1.0); }
void lay(inout vec4 c, vec3 col, float a) { c.rgb = mix(c.rgb, col, a); }
void layE(inout vec4 c, vec3 col, float a, float glow) { c.rgb = mix(c.rgb, col, a); c.a = mix(c.a, glow, a); }

// ---------- time of day ----------
float wDay, wDawn, wDusk, wNight;
void dayWeights() {
    float h = uHour;
    wDay = smoothstep(7.0, 9.0, h) * (1.0 - smoothstep(16.5, 18.5, h));
    wDawn = smoothstep(4.5, 6.5, h) * (1.0 - smoothstep(7.5, 9.0, h));
    wDusk = smoothstep(16.5, 18.0, h) * (1.0 - smoothstep(19.0, 20.5, h));
    wNight = clamp(1.0 - wDay - wDawn - wDusk, 0.0, 1.0);
    float s = wDay + wDawn + wDusk + wNight; wDay /= s; wDawn /= s; wDusk /= s; wNight /= s;
}
vec3 skyGrad(float t) { // t: 0 horizon .. 1 top
    vec3 top = vec3(0.0, 0.0, 0.015) * wNight + vec3(0.2, 0.2, 0.48) * wDawn + vec3(0.18, 0.46, 0.9) * wDay + vec3(0.12, 0.08, 0.32) * wDusk;
    vec3 hor = vec3(0.03, 0.035, 0.1) * wNight + vec3(1.0, 0.6, 0.45) * wDawn + vec3(0.7, 0.85, 1.0) * wDay + vec3(1.0, 0.42, 0.22) * wDusk;
    return mix(hor, top, pow(clamp(t, 0.0, 1.0), 0.7));
}
vec3 ambient() { return vec3(0.16, 0.18, 0.32) * wNight + vec3(0.95, 0.8, 0.78) * wDawn + vec3(1.0) * wDay + vec3(0.95, 0.7, 0.6) * wDusk; }
vec2 sunPos() { float a = (uHour - 6.0) / 12.0 * 3.14159; return vec2(-cos(a) * uHalf.x * 0.8, uFloor + 0.35 + sin(a) * (uHalf.y - uFloor - 0.5)); }


vec4 jellyRoom(vec2 p) {
    float t = clamp((p.y - uFloor) / (uHalf.y - uFloor), 0.0, 1.0);
    vec3 wall = mix(mix(vec3(0.93, 0.80, 1.0), vec3(0.78, 0.66, 0.98), t), mix(vec3(0.20, 0.12, 0.34), vec3(0.08, 0.05, 0.16), t), wNight);
    vec4 c = vec4(wall, 0.0);
    // giant soft polka dots
    vec2 g = p * 3.2 + vec2(0.0, 0.5); vec2 id = floor(g); vec2 f = fract(g) - 0.5;
    float r = 0.18 + 0.1 * h21(id);
    float dd = length(f + 0.15 * (vec2(h21(id + 1.0), h21(id + 4.0)) - 0.5)) - r;
    vec3 dot1 = mix(vec3(1.0, 0.86, 0.97), vec3(0.32, 0.2, 0.5), wNight);
    lay(c, dot1, fill(dd * 0.3) * 0.55 * step(uFloor + 0.02, p.y));
    // a window of light
    float win = sdRoundBox(p - vec2(uHalf.x * 0.35, uFloor + 0.95), vec2(0.22, 0.28), 0.2);
    c.rgb += mix(vec3(1.0, 0.95, 0.85), vec3(0.6, 0.65, 1.0), wNight) * exp(-max(win, 0.0) * 9.0) * 0.25;
    layE(c, mix(vec3(1.0, 0.97, 0.9), vec3(0.75, 0.8, 1.0), wNight), fill(win) * 0.9, 0.5 * wNight);
    // glossy jelly floor
    float fl = p.y - (uFloor + 0.03 * sin(p.x * 3.0));
    vec3 floorCol = mix(vec3(0.62, 0.42, 0.95), vec3(0.22, 0.12, 0.42), wNight);
    float below = 1.0 - smoothstep(-px, px, fl);
    lay(c, mix(floorCol, floorCol * 0.6, clamp(-fl * 1.6, 0.0, 1.0)), below);
    lay(c, vec3(1.0), below * exp(-abs(fl + 0.05) * 40.0) * 0.35);
    lay(c, vec3(1.0, 0.9, 1.0), below * exp(-dot((p - vec2(-uHalf.x * 0.3, uFloor - 0.18)) * vec2(2.0, 12.0), (p - vec2(-uHalf.x * 0.3, uFloor - 0.18)) * vec2(2.0, 12.0))) * 0.25);
    return c;
}

vec4 gumPool(vec2 p) {
    float t = clamp((p.y - uFloor) / (uHalf.y - uFloor), 0.0, 1.0);
    vec3 sky = mix(mix(vec3(1.0, 0.78, 0.86), vec3(0.6, 0.85, 1.0), t), mix(vec3(0.16, 0.10, 0.25), vec3(0.03, 0.04, 0.12), t), wNight);
    vec4 c = vec4(sky, 0.0);
    // floating soft bubbles
    for (int i = 0; i < 7; i++) {
        float fi = float(i);
        vec2 bp = vec2((h21(vec2(fi, 2.0)) - 0.5) * uHalf.x * 1.8, uFloor + 0.3 + h21(vec2(fi, 7.0)) * (uHalf.y - uFloor - 0.4));
        float br = 0.04 + 0.07 * h21(vec2(fi, 9.0));
        float bd = length(p - bp) - br;
        lay(c, mix(vec3(1.0), vec3(0.6, 0.7, 1.0), wNight), fill(abs(bd) - 0.004) * 0.6);
        lay(c, vec3(1.0), fill(length(p - bp + br * vec2(0.35, -0.35)) - br * 0.18) * 0.7);
    }
    // the gum pool: mint goo with ripple rings
    float fl = p.y - (uFloor + 0.02 * sin(p.x * 5.0 + 1.0));
    float below = 1.0 - smoothstep(-px, px, fl);
    vec3 goo = mix(vec3(0.45, 0.95, 0.78), vec3(0.08, 0.35, 0.32), wNight);
    vec2 q = (p - vec2(0.0, uFloor - 0.25)) * vec2(1.0, 3.2);
    float rings = 0.5 + 0.5 * sin(length(q) * 26.0);
    lay(c, mix(goo * 0.75, goo, rings * 0.35 + 0.65 * smoothstep(-0.6, 0.0, fl)), below);
    lay(c, vec3(1.0), below * exp(-abs(fl + 0.025) * 60.0) * 0.45);
    return c;
}

vec4 moonJelly(vec2 p) {
    float t = clamp((p.y - uFloor) / (uHalf.y - uFloor), 0.0, 1.0);
    vec3 sky = mix(vec3(0.10, 0.08, 0.28), vec3(0.02, 0.01, 0.07), t);
    sky = mix(sky, sky + vec3(0.12, 0.06, 0.2), wDay * 0.6);
    vec4 c = vec4(sky, 0.0);
    vec2 g = p * 26.0; vec2 id = floor(g); vec2 f = fract(g) - 0.5;
    if (h21(id) > 0.92) layE(c, vec3(1.0, 0.95, 1.0), fill(length(f) * 0.04 - 0.002), 0.8);
    // big pastel moon
    vec2 mp = vec2(uHalf.x * 0.25, uHalf.y - 0.55);
    float md = length(p - mp) - 0.26;
    c.rgb += vec3(0.85, 0.7, 1.0) * exp(-max(md, 0.0) * 4.0) * 0.35;
    layE(c, mix(vec3(1.0, 0.92, 0.98), vec3(0.95, 0.8, 1.0), smoothstep(-0.2, 0.2, p.x - mp.x)), fill(md), 1.0);
    lay(c, vec3(0.88, 0.78, 0.95), fill(length(p - mp - vec2(-0.08, 0.05)) - 0.05) * fill(md));
    lay(c, vec3(0.88, 0.78, 0.95), fill(length(p - mp - vec2(0.09, -0.08)) - 0.035) * fill(md));
    // purple jelly ground with drips
    float drip = 0.0;
    for (int i = 0; i < 5; i++) { float fi = float(i); float dx = p.x - (fi - 2.0) * uHalf.x * 0.45 - 0.07 * sin(fi * 3.0);
        drip = max(drip, (0.05 + 0.04 * h21(vec2(fi, 1.0))) * exp(-dx * dx * 140.0)); }
    float fl = p.y - (uFloor + 0.025 * sin(p.x * 4.0)) + drip;
    float below = 1.0 - smoothstep(-px, px, fl);
    vec3 jel = vec3(0.62, 0.36, 0.95);
    lay(c, mix(jel * 0.55, jel, smoothstep(-0.5, 0.0, fl)), below);
    layE(c, vec3(0.9, 0.75, 1.0), below * exp(-abs(fl + 0.02) * 70.0) * 0.6, 0.6);
    return c;
}

vec4 sherbetHills(vec2 p) {
    float t = clamp((p.y - uFloor) / (uHalf.y - uFloor), 0.0, 1.0);
    vec3 sky = mix(mix(vec3(1.0, 0.86, 0.68), vec3(1.0, 0.68, 0.74), t), mix(vec3(0.24, 0.12, 0.26), vec3(0.06, 0.04, 0.14), t), wNight);
    vec4 c = vec4(sky, 0.0);
    vec2 sp = vec2(-uHalf.x * 0.3, uFloor + 0.75);
    float sd = length(p - sp) - 0.18;
    c.rgb += mix(vec3(1.0, 0.8, 0.5), vec3(0.7, 0.7, 1.0), wNight) * exp(-max(sd, 0.0) * 5.0) * 0.4;
    layE(c, mix(vec3(1.0, 0.93, 0.7), vec3(0.95, 0.95, 1.0), wNight), fill(sd), wNight);
    // layered soft hills, back to front
    for (int i = 0; i < 4; i++) {
        float fi = float(i);
        float y0 = uFloor + 0.42 - fi * 0.13;
        float hill = y0 + 0.08 * sin(p.x * (2.2 + fi * 0.7) + fi * 1.9) + 0.03 * sin(p.x * 6.0 + fi);
        vec3 col = mix(vec3(1.0, 0.72 - fi * 0.06, 0.62 + fi * 0.05), vec3(0.95 - fi * 0.05, 0.55, 0.75), fi / 3.0);
        col = mix(col, col * vec3(0.3, 0.25, 0.45), wNight * 0.8);
        float d = p.y - hill;
        lay(c, col, 1.0 - smoothstep(-px, px, d));
        lay(c, vec3(1.0), (1.0 - smoothstep(-px, px, d)) * exp(-abs(d + 0.015) * 60.0) * 0.18);
    }
    float fl = p.y - uFloor;
    lay(c, mix(vec3(1.0, 0.62, 0.66), vec3(0.3, 0.16, 0.32), wNight), 1.0 - smoothstep(-px, px, fl));
    return c;
}

void main() {
    px = 2.0 * uHalf.y / uRes.y;
    vec2 p = (gl_FragCoord.xy / uRes - 0.5) * uHalf * 2.0;
    dayWeights();
    vec4 c;
    if (uWorld > 30.5) c = sherbetHills(p);
    else if (uWorld > 29.5) c = moonJelly(p);
    else if (uWorld > 28.5) c = gumPool(p);
    else c = jellyRoom(p);
    float v = smoothstep(1.4, 0.4, length(p / uHalf));
    c.rgb *= mix(0.55, 1.0, v);
    c.rgb += (h21(gl_FragCoord.xy * 0.37 + uSeed) - 0.5) * 0.018;
    gl_FragColor = vec4(clamp(c.rgb, 0.0, 1.0), clamp(c.a, 0.0, 1.0));
}
