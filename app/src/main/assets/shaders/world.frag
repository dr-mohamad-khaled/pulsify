// Pulsify — environments. GLSL ES 1.00.
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

// A sky with sun or moon, stars and clouds; used by every outdoor world and every window.
vec4 skyLayer(vec2 p, float horizonY, float clouds) {
    vec4 c = vec4(skyGrad((p.y - horizonY) / max(uHalf.y - horizonY, 0.1)), 0.0);
    // Stars (static here; the overlay makes them twinkle).
    vec2 g = p * 40.0; vec2 id = floor(g); vec2 f = fract(g) - 0.5;
    float r = h21(id + uSeed);
    if (r > 0.93) { float st = smoothstep(0.08, 0.0, length(f - (vec2(h21(id + 3.1), h21(id + 7.7)) - 0.5) * 0.6)); layE(c, vec3(0.9, 0.92, 1.0), st * wNight * 0.9, 0.6); }
    vec2 sp = sunPos();
    float sunUp = smoothstep(5.0, 7.0, uHour) * (1.0 - smoothstep(18.0, 19.5, uHour));
    float sd = length(p - sp);
    vec3 sunC = mix(vec3(1.0, 0.95, 0.8), vec3(1.0, 0.6, 0.3), wDawn + wDusk);
    c.rgb += sunC * exp(-sd * 6.0) * 0.35 * sunUp;
    layE(c, sunC * 1.1, fill(sd - 0.07) * sunUp, 0.5);
    // Moon (crescent) at night.
    vec2 mp = vec2(uHalf.x * 0.45, uHalf.y - 0.35);
    float md = length(p - mp) - 0.08;
    float bite = length(p - mp - vec2(0.035, 0.025)) - 0.075;
    float moon = max(md, -bite);
    c.rgb += vec3(0.6, 0.7, 1.0) * exp(-max(md, 0.0) * 8.0) * 0.25 * wNight;
    layE(c, vec3(1.0, 0.97, 0.85), fill(moon) * wNight, 1.0);
    // Clouds.
    if (clouds > 0.0) {
        float n = fbm(vec2(p.x * 3.2 + uSeed, p.y * 6.0));
        float band = smoothstep(0.6, 0.78, n) * smoothstep(horizonY + 0.45, horizonY + 0.8, p.y) * (1.0 - smoothstep(uHalf.y - 0.05, uHalf.y + 0.2, p.y));
        vec3 cc = mix(vec3(0.12, 0.13, 0.22), vec3(1.0), wDay) + vec3(1.0, 0.55, 0.35) * (wDawn + wDusk) * 0.8;
        cc *= 0.85 + 0.25 * vnoise(p * 8.0);
        lay(c, cc, band * clouds * (1.0 - wNight * 0.6));
    }
    return c;
}

vec2 rot2(vec2 p, float a) { float c = cos(a), s = sin(a); return vec2(c * p.x - s * p.y, s * p.x + c * p.y); }

float hillMask(vec2 p, float base, float amp, float freq, float seed) {
    float y = base + amp * (0.6 * sin(p.x * freq + seed) + 0.4 * sin(p.x * freq * 2.3 + seed * 1.7));
    return fill(p.y - y);
}

// ---------- worlds ----------

// 1 Cloud Nursery (Mochi): pastel sky, a smiling moon, a floor of soft clouds.
vec4 cloudNursery(vec2 p) {
    float t = clamp((p.y - uFloor) / (uHalf.y - uFloor), 0.0, 1.0);
    vec3 top = mix(vec3(0.33, 0.27, 0.62), vec3(0.06, 0.04, 0.16), wNight);
    vec3 mid = mix(vec3(0.95, 0.66, 0.85), vec3(0.32, 0.18, 0.42), wNight);
    vec3 low = mix(vec3(1.0, 0.85, 0.75), vec3(0.45, 0.3, 0.5), wNight);
    vec4 c = vec4(mix(mix(low, mid, smoothstep(0.0, 0.45, t)), top, smoothstep(0.45, 1.0, t)), 0.0);
    // Sparkly stars, pastel.
    vec2 g = p * 22.0; vec2 id = floor(g); vec2 f = fract(g) - 0.5;
    if (h21(id) > 0.9) {
        float r = 0.04 + 0.05 * h21(id + 2.0);
        float star = min(sdSeg(f, vec2(-r * 2.0, 0.0), vec2(r * 2.0, 0.0)), sdSeg(f, vec2(0.0, -r * 2.0), vec2(0.0, r * 2.0))) - 0.012;
        vec3 sc = 0.6 + 0.4 * cos(6.28 * (h21(id + 5.0) + vec3(0.0, 0.33, 0.67)));
        layE(c, mix(sc, vec3(1.0), 0.5), clamp(0.5 - star * 22.0, 0.0, 1.0) * smoothstep(0.1, 0.4, t), 0.6);
    }
    // Smiling moon.
    vec2 mp = vec2(-uHalf.x * 0.42, uHalf.y - 0.42);
    float md = length(p - mp) - 0.16;
    c.rgb += vec3(1.0, 0.9, 0.7) * exp(-max(md, 0.0) * 6.0) * 0.35;
    layE(c, mix(vec3(1.0, 0.94, 0.75), vec3(1.0, 0.85, 0.6), smoothstep(-0.16, 0.16, p.y - mp.y)), fill(md), 1.0);
    vec2 mq = p - mp;
    float eyes = min(abs(length(mq - vec2(-0.05, 0.03)) - 0.025) + step(0.0, -(mq.y - 0.03)) , abs(length(mq - vec2(0.05, 0.03)) - 0.025) + step(0.0, -(mq.y - 0.03)));
    lay(c, vec3(0.45, 0.3, 0.25), fill(eyes - 0.006) * fill(md));
    float smile = abs(length(mq - vec2(0.0, 0.0)) - 0.06) + step(0.0, mq.y + 0.02);
    lay(c, vec3(0.45, 0.3, 0.25), fill(smile - 0.006) * fill(md));
    lay(c, vec3(1.0, 0.6, 0.6), exp(-dot((mq - vec2(-0.09, -0.03)) * 20.0, (mq - vec2(-0.09, -0.03)) * 20.0)) * 0.6 * fill(md));
    lay(c, vec3(1.0, 0.6, 0.6), exp(-dot((mq - vec2(0.09, -0.03)) * 20.0, (mq - vec2(0.09, -0.03)) * 20.0)) * 0.6 * fill(md));
    // Floating cloud islands.
    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        vec2 cp = vec2((fi - 1.0) * uHalf.x * 0.7 + 0.1 * sin(fi * 4.0), uFloor + 0.55 + 0.25 * h21(vec2(fi, 1.0)));
        float d = min(min(sdEllipse(p - cp, vec2(0.16, 0.05)), length(p - cp - vec2(-0.05, 0.03)) - 0.06), length(p - cp - vec2(0.05, 0.04)) - 0.07);
        vec3 cc = mix(vec3(1.0, 0.92, 0.95), vec3(0.55, 0.45, 0.7), wNight) * (0.85 + 0.15 * smoothstep(-0.05, 0.08, p.y - cp.y));
        lay(c, cc, fill(d) * 0.9);
    }
    // Cloud floor: big soft puffs, front row brightest.
    for (int row = 0; row < 3; row++) {
        float fr = float(row);
        float y0 = uFloor + 0.12 - fr * 0.1;
        float bumps = y0 + 0.07 * (0.5 + 0.5 * sin(p.x * (7.0 + fr * 3.0) + fr * 2.0)) + 0.03 * sin(p.x * 17.0 + fr);
        float d = p.y - bumps;
        vec3 cc = mix(mix(vec3(1.0, 0.86, 0.92), vec3(1.0, 0.97, 0.98), fr / 2.0), mix(vec3(0.42, 0.32, 0.58), vec3(0.6, 0.5, 0.75), fr / 2.0), wNight);
        cc *= 0.82 + 0.18 * smoothstep(-0.12, 0.0, d);
        lay(c, cc, fill(d));
    }
    return c;
}

// 2 Battery Lab (Mochi): dark lab, shelves of glowing jars, a neon battery sign, grid floor.
vec4 batteryLab(vec2 p) {
    vec4 c = vec4(mix(vec3(0.02, 0.025, 0.04), vec3(0.05, 0.07, 0.1), smoothstep(uFloor, uHalf.y, p.y)), 0.0);
    // Wall panels.
    vec2 q = vec2(p.x / 0.32, (p.y - uFloor) / 0.32);
    vec2 cell = fract(q) - 0.5;
    float seam = min(abs(cell.x), abs(cell.y));
    lay(c, vec3(0.08, 0.1, 0.14), smoothstep(0.02, 0.0, abs(seam - 0.48)) * 0.6 * step(uFloor, p.y));
    // Neon battery sign.
    vec2 s = p - vec2(0.0, uHalf.y - 0.45);
    float frame = abs(sdRoundBox(s, vec2(0.26, 0.1), 0.04)) - 0.008;
    float tip = sdRoundBox(s - vec2(0.29, 0.0), vec2(0.018, 0.04), 0.008);
    float neon = min(frame, tip);
    vec3 green = vec3(0.3, 1.0, 0.5);
    c.rgb += green * exp(-max(neon, 0.0) * 30.0) * 0.5;
    layE(c, green * 1.2, fill(neon), 1.0);
    for (int k = 0; k < 4; k++) {
        float bar = sdRoundBox(s - vec2(-0.18 + float(k) * 0.12, 0.0), vec2(0.045, 0.06), 0.012);
        layE(c, green * (0.8 + 0.1 * float(k)), fill(bar) * step(float(k), 2.5), 1.0);
    }
    // Shelves with glowing jars, left and right.
    for (int side = 0; side < 2; side++) {
        float sx = side == 0 ? -1.0 : 1.0;
        for (int row = 0; row < 3; row++) {
            float y = uFloor + 0.35 + float(row) * 0.32;
            float shelf = sdBox(p - vec2(sx * (uHalf.x - 0.2), y), vec2(0.2, 0.012));
            lay(c, vec3(0.25, 0.27, 0.3), fill(shelf));
            for (int j = 0; j < 2; j++) {
                float jx = sx * (uHalf.x - 0.29 + float(j) * 0.16);
                vec2 jp = p - vec2(jx, y + 0.07);
                float jar = sdRoundBox(jp, vec2(0.04, 0.06), 0.015);
                float hue = h21(vec2(float(side * 7 + row * 3 + j), 2.0));
                vec3 jc = 0.55 + 0.45 * cos(6.28 * (hue + vec3(0.0, 0.33, 0.67)));
                float lvl = 0.3 + 0.6 * h21(vec2(float(row), float(j + side * 5)));
                c.rgb += jc * exp(-max(jar, 0.0) * 25.0) * 0.25;
                layE(c, jc * 0.25, fill(jar), 0.2);
                layE(c, jc * 1.2, fill(max(jar + 0.006, jp.y - (-0.06 + 0.12 * lvl))), 1.0);
                lay(c, vec3(0.7), fill(sdRoundBox(jp - vec2(0.0, 0.068), vec2(0.042, 0.01), 0.004)));
            }
        }
    }
    // Hanging cables.
    for (int k = 0; k < 3; k++) {
        float fk = float(k);
        float x0 = (fk - 1.0) * 0.5;
        float y = uHalf.y - 0.05 - 0.12 * (1.0 - pow((p.x - x0) / 0.25, 2.0));
        float d = abs(p.y - y) - 0.006;
        lay(c, vec3(0.12, 0.13, 0.16), fill(d) * step(abs(p.x - x0), 0.25));
    }
    // Grid floor in perspective.
    if (p.y < uFloor) {
        float depth = (uFloor - p.y);
        float z = 0.25 / max(depth, 0.01);
        vec2 gp = vec2(p.x * z, z);
        vec2 gl = abs(fract(gp * 1.5) - 0.5);
        float line = smoothstep(0.03 * z, 0.0, min(gl.x, gl.y) - 0.0) ;
        vec3 floorC = vec3(0.01, 0.015, 0.02);
        c = vec4(floorC, 0.0);
        layE(c, green * 0.5, clamp(line, 0.0, 1.0) * smoothstep(0.0, 0.25, depth) * 0.7, 0.5);
        c.rgb += green * 0.06 * exp(-depth * 4.0);
    }
    return c;
}

// 3 Backyard (Kalboob): sky by time of day, hills, picket fence with string lights, doghouse, grass.
vec4 backyard(vec2 p) {
    float horizon = uFloor + 0.35;
    vec4 c = skyLayer(p, horizon, 1.0);
    vec3 amb = ambient();
    // Hills.
    vec3 farHill = mix(vec3(0.45, 0.62, 0.55), vec3(0.04, 0.06, 0.1), wNight) * mix(vec3(1.0), vec3(1.0, 0.75, 0.7), wDusk + wDawn);
    lay(c, farHill, hillMask(p, horizon + 0.05, 0.06, 2.5, 1.0 + uSeed));
    vec3 nearHill = mix(vec3(0.35, 0.58, 0.32), vec3(0.03, 0.06, 0.05), wNight) * mix(vec3(1.0), vec3(1.0, 0.8, 0.7), wDusk + wDawn);
    lay(c, nearHill, hillMask(p, horizon - 0.04, 0.04, 4.0, 3.0));
    // Picket fence.
    float fy0 = uFloor + 0.06, fy1 = uFloor + 0.3;
    vec2 fq = vec2(mod(p.x + 0.04, 0.11) - 0.055, p.y);
    float plank = max(sdBox(fq - vec2(0.0, (fy0 + fy1) * 0.5), vec2(0.035, (fy1 - fy0) * 0.5)), fq.y - fy1 - 0.035 + abs(fq.x) * 1.0);
    float rail = min(sdBox(p - vec2(0.0, fy0 + 0.06), vec2(9.0, 0.012)), sdBox(p - vec2(0.0, fy1 - 0.05), vec2(9.0, 0.012)));
    vec3 wood = vec3(0.93, 0.9, 0.84) * amb;
    lay(c, wood * 0.82, fill(rail));
    lay(c, wood * (0.9 + 0.1 * vnoise(p * vec2(20.0, 2.0))), fill(plank));
    // String lights along the fence (glow at dusk and night).
    float lightsOn = clamp(wNight + wDusk, 0.0, 1.0);
    float wire = abs(p.y - (fy1 + 0.03 - 0.03 * pow(sin(p.x * 3.0), 2.0))) - 0.003;
    lay(c, vec3(0.1), fill(wire) * 0.7);
    vec2 bq = vec2(mod(p.x, 0.16) - 0.08, p.y - (fy1 + 0.03 - 0.03 * pow(sin(p.x * 3.0), 2.0)) + 0.02);
    float bulb = length(bq * vec2(1.0, 0.8)) - 0.014;
    vec3 bc = 0.6 + 0.4 * cos(6.28 * (h21(vec2(floor(p.x / 0.16), 1.0)) + vec3(0.0, 0.33, 0.67)));
    c.rgb += bc * exp(-max(bulb, 0.0) * 30.0) * 0.6 * lightsOn;
    layE(c, mix(bc * 0.3, bc * 1.3, lightsOn), fill(bulb), lightsOn);
    // Doghouse on the left.
    vec2 dh = p - vec2(-uHalf.x * 0.62, uFloor + 0.02);
    float walls = sdBox(dh - vec2(0.0, 0.14), vec2(0.16, 0.14));
    float roof = sdTri(dh, vec2(-0.21, 0.27), vec2(0.21, 0.27), vec2(0.0, 0.45));
    float door = min(sdBox(dh - vec2(0.0, 0.07), vec2(0.07, 0.07)), length(dh - vec2(0.0, 0.14)) - 0.07);
    vec3 red = vec3(0.78, 0.2, 0.18) * amb;
    lay(c, red * (0.92 + 0.08 * step(0.5, fract(dh.y * 20.0))), fill(walls));
    lay(c, vec3(0.32, 0.22, 0.2) * amb, fill(roof));
    lay(c, vec3(0.04, 0.03, 0.03), fill(door));
    lay(c, vec3(1.0, 0.85, 0.4) * amb, fill(sdRoundBox(dh - vec2(0.0, 0.31), vec2(0.07, 0.022), 0.008)));
    // Grass.
    if (p.y < uFloor + 0.08) {
        float blades = vnoise(vec2(p.x * 90.0, p.y * 6.0));
        float edge = uFloor + 0.06 + 0.025 * vnoise(vec2(p.x * 60.0, 0.0));
        vec3 grass = mix(vec3(0.32, 0.6, 0.22), vec3(0.18, 0.42, 0.14), smoothstep(uFloor, uFloor - 0.6, p.y));
        grass = mix(grass, grass * 0.6, blades * 0.5) * amb;
        lay(c, grass, fill(p.y - edge));
        // Flowers.
        vec2 g = vec2(p.x * 9.0, (p.y - uFloor) * 9.0);
        vec2 id = floor(g); vec2 f = fract(g) - 0.5;
        if (h21(id + 3.0) > 0.72 && p.y < uFloor - 0.02) {
            vec2 o = (vec2(h21(id), h21(id + 1.0)) - 0.5) * 0.5;
            float petals = length(f - o) - 0.09 - 0.03 * cos(atan(f.y - o.y, f.x - o.x) * 5.0);
            vec3 fc = h21(id + 9.0) > 0.5 ? vec3(1.0, 0.95, 0.9) : (h21(id + 4.0) > 0.5 ? vec3(1.0, 0.8, 0.25) : vec3(1.0, 0.5, 0.7));
            lay(c, fc * amb, clamp(0.5 - petals * 40.0, 0.0, 1.0));
            lay(c, vec3(1.0, 0.75, 0.2) * amb, clamp(0.5 - (length(f - o) - 0.04) * 40.0, 0.0, 1.0));
        }
    }
    return c;
}

// 4 Snow Day (Kalboob): winter sky, snowy hills with pines, a snowman, a cabin with a warm window.
vec4 snowDay(vec2 p) {
    float horizon = uFloor + 0.32;
    vec4 c = skyLayer(p, horizon, 0.6);
    c.rgb = mix(c.rgb, c.rgb * 0.8 + vec3(0.12, 0.14, 0.18), 0.4 * (1.0 - wNight));
    vec3 amb = ambient() * vec3(0.95, 0.98, 1.05);
    vec3 snow = vec3(0.93, 0.96, 1.0) * amb;
    lay(c, snow * 0.82, hillMask(p, horizon + 0.07, 0.07, 2.2, 2.0));
    // Pines.
    for (int i = 0; i < 7; i++) {
        float fi = float(i);
        float x = (fi / 6.0 - 0.5) * uHalf.x * 2.2 + 0.05 * sin(fi * 7.0);
        float base = horizon + 0.04 + 0.04 * sin(x * 2.2 + 2.0);
        float hgt = 0.18 + 0.1 * h21(vec2(fi, 4.0));
        vec2 q = p - vec2(x, base);
        float tree = sdTri(q, vec2(-hgt * 0.38, 0.0), vec2(hgt * 0.38, 0.0), vec2(0.0, hgt));
        lay(c, mix(vec3(0.1, 0.3, 0.25), vec3(0.02, 0.06, 0.06), wNight) * amb, fill(tree));
        float cap = max(tree, -(q.y - hgt * 0.62 - 0.02 * sin(q.x * 60.0)));
        lay(c, snow, fill(cap));
    }
    // Cabin with a warm window.
    vec2 cb = p - vec2(-uHalf.x * 0.5, horizon - 0.01);
    float wall = sdBox(cb - vec2(0.0, 0.07), vec2(0.13, 0.07));
    float roof = sdTri(cb, vec2(-0.17, 0.13), vec2(0.17, 0.13), vec2(0.0, 0.26));
    lay(c, vec3(0.45, 0.28, 0.18) * amb, fill(wall));
    lay(c, snow, fill(roof));
    float win = sdBox(cb - vec2(-0.04, 0.08), vec2(0.035, 0.03));
    float warm = clamp(0.35 + wNight + wDusk, 0.0, 1.0);
    c.rgb += vec3(1.0, 0.7, 0.3) * exp(-max(win, 0.0) * 25.0) * 0.5 * warm;
    layE(c, vec3(1.0, 0.75, 0.35) * (0.6 + 0.6 * warm), fill(win), warm);
    float chim = sdBox(cb - vec2(0.08, 0.24), vec2(0.018, 0.04));
    lay(c, vec3(0.35, 0.2, 0.15) * amb, fill(chim));
    // Snowman on the right.
    vec2 sm = p - vec2(uHalf.x * 0.62, uFloor + 0.02);
    float body = min(min(length(sm - vec2(0.0, 0.09)) - 0.1, length(sm - vec2(0.0, 0.24)) - 0.075), length(sm - vec2(0.0, 0.35)) - 0.055);
    vec3 snowShade = snow * (0.8 + 0.2 * smoothstep(-0.1, 0.1, sm.x * -0.5 + (sm.y - 0.2) * 0.3));
    lay(c, snowShade, fill(body));
    lay(c, vec3(0.05), fill(length(sm - vec2(-0.02, 0.37)) - 0.008));
    lay(c, vec3(0.05), fill(length(sm - vec2(0.02, 0.37)) - 0.008));
    lay(c, vec3(1.0, 0.5, 0.1) * amb, fill(sdTri(sm, vec2(0.0, 0.345), vec2(0.0, 0.325), vec2(0.06, 0.335))));
    lay(c, vec3(0.8, 0.15, 0.2) * amb, fill(sdBox(sm - vec2(0.0, 0.3), vec2(0.06, 0.012))));
    lay(c, vec3(0.3, 0.2, 0.12) * amb, fill(sdSeg(sm, vec2(0.07, 0.24), vec2(0.16, 0.3)) - 0.006));
    lay(c, vec3(0.1), fill(sdBox(sm - vec2(0.0, 0.42), vec2(0.05, 0.035))));
    // Snow ground with sparkles.
    if (p.y < uFloor + 0.06) {
        float edge = uFloor + 0.04 + 0.02 * sin(p.x * 5.0) + 0.01 * vnoise(vec2(p.x * 30.0, 0.0));
        vec3 g = snow * mix(1.0, 0.82, smoothstep(uFloor, uFloor - 0.7, p.y)) * (0.96 + 0.04 * vnoise(p * 40.0));
        lay(c, g, fill(p.y - edge));
        float sp = step(0.985, h21(floor(p * 160.0)));
        layE(c, vec3(1.0), sp * 0.8 * step(p.y, edge), 0.7);
    }
    return c;
}

// 5 Cozy Room (Kalboob): warm wall, a window on the real sky, a floor lamp, a round rug.
vec4 cozyRoom(vec2 p) {
    float lampOn = clamp(0.25 + wNight + wDusk * 0.8, 0.0, 1.0);
    vec3 light = mix(ambient() * 0.65 + vec3(0.08), vec3(1.0, 0.8, 0.6) * 0.5, wNight);
    // Wallpaper.
    vec3 wallC = vec3(0.86, 0.66, 0.5);
    float stripe = step(0.5, fract(p.x * 6.0));
    wallC *= 0.94 + 0.06 * stripe;
    vec2 dots = fract(p * vec2(6.0, 6.0) + vec2(0.25, 0.0)) - 0.5;
    wallC = mix(wallC, wallC * 1.08, smoothstep(0.08, 0.06, length(dots)) * (1.0 - stripe));
    vec4 c = vec4(wallC * light, 0.0);
    // Window with the real sky.
    vec2 wc = vec2(uHalf.x * 0.42, uFloor + 0.95);
    vec2 wq = p - wc;
    float win = sdBox(wq, vec2(0.24, 0.3));
    if (win < 0.0) {
        vec4 sky = skyLayer(p * 1.0 + vec2(0.0, 0.0), wc.y - 0.3, 0.7);
        c = vec4(sky.rgb, sky.a);
        // Rooftops outside.
        float roofs = p.y - (wc.y - 0.3 + 0.05 + 0.04 * step(0.5, fract(p.x * 4.0)) + 0.03 * step(0.6, fract(p.x * 7.0 + 0.3)));
        lay(c, mix(vec3(0.3, 0.32, 0.38), vec3(0.02, 0.02, 0.04), wNight), fill(roofs));
    }
    float frame = abs(win) - 0.014;
    lay(c, vec3(0.95, 0.93, 0.9) * light, fill(frame));
    lay(c, vec3(0.95, 0.93, 0.9) * light, fill(min(abs(wq.x) - 0.008, abs(wq.y) - 0.008)) * step(win, 0.0));
    float sill = sdBox(wq - vec2(0.0, -0.32), vec2(0.28, 0.02));
    lay(c, vec3(0.9, 0.88, 0.85) * light, fill(sill));
    // Curtains.
    for (int s = 0; s < 2; s++) {
        float sx = s == 0 ? -1.0 : 1.0;
        vec2 cq = wq - vec2(sx * 0.28, 0.0);
        float cur = sdBox(cq, vec2(0.06 + 0.02 * (wq.y + 0.3), 0.33));
        float folds = 0.85 + 0.15 * sin(cq.x * 90.0);
        lay(c, vec3(0.55, 0.2, 0.3) * light * folds, fill(cur));
    }
    // Picture frame with a paw.
    vec2 pf = p - vec2(-uHalf.x * 0.45, uFloor + 1.05);
    lay(c, vec3(0.4, 0.26, 0.15) * light, fill(sdBox(pf, vec2(0.13, 0.11))));
    lay(c, vec3(0.95, 0.9, 0.8) * light, fill(sdBox(pf, vec2(0.105, 0.085))));
    float paw = min(min(sdEllipse(pf - vec2(0.0, -0.02), vec2(0.035, 0.028)), length(pf - vec2(-0.035, 0.025)) - 0.014),
                    min(length(pf - vec2(0.0, 0.04)) - 0.014, length(pf - vec2(0.035, 0.025)) - 0.014));
    lay(c, vec3(0.75, 0.45, 0.3) * light, fill(paw));
    // Floor lamp on the left with a warm cone of light.
    vec2 lp = p - vec2(-uHalf.x * 0.72, uFloor);
    lay(c, vec3(0.15) * light, fill(sdSeg(lp, vec2(0.0, 0.0), vec2(0.0, 0.62)) - 0.008));
    lay(c, vec3(0.15) * light, fill(sdEllipse(lp, vec2(0.08, 0.015))));
    float shade = sdTri(lp, vec2(-0.1, 0.6), vec2(0.1, 0.6), vec2(0.0, 0.78));
    shade = max(sdBox(lp - vec2(0.0, 0.66), vec2(0.11 - (lp.y - 0.6) * 0.4, 0.065)), 0.0 - 1.0);
    vec2 cone = lp - vec2(0.0, 0.6);
    float coneM = smoothstep(0.0, -0.5, cone.y) * smoothstep(0.05 + abs(cone.y) * 0.9, 0.0, abs(cone.x)) * smoothstep(-0.7, -0.3, cone.y);
    c.rgb += vec3(1.0, 0.75, 0.4) * coneM * 0.25 * lampOn;
    c.rgb += vec3(1.0, 0.7, 0.4) * exp(-length(lp - vec2(0.0, 0.66)) * 4.0) * 0.35 * lampOn;
    layE(c, mix(vec3(0.8, 0.7, 0.55), vec3(1.0, 0.85, 0.55), lampOn) * light * (1.0 + lampOn), fill(shade), lampOn);
    // Skirting board and wooden floor.
    lay(c, vec3(0.92, 0.9, 0.86) * light, fill(sdBox(p - vec2(0.0, uFloor + 0.07), vec2(9.0, 0.02))));
    if (p.y < uFloor + 0.05) {
        float depth = uFloor + 0.05 - p.y;
        float plank = fract(p.x * 3.0 / (1.0 + depth * 2.0) + floor(depth * 9.0) * 0.37);
        vec3 wood = mix(vec3(0.55, 0.34, 0.2), vec3(0.62, 0.4, 0.24), vnoise(vec2(p.x * 3.0, depth * 60.0)));
        wood *= 0.9 + 0.1 * smoothstep(0.0, 0.03, plank) * smoothstep(1.0, 0.97, plank);
        c = vec4(wood * light, 0.0);
        c.rgb += vec3(1.0, 0.75, 0.4) * 0.15 * lampOn * exp(-length(p - vec2(-uHalf.x * 0.72, uFloor)) * 2.5);
    }
    // Round rug under the creature.
    float rug = sdEllipse(p - vec2(0.0, uFloor - 0.04), vec2(0.62, 0.12));
    float rings = sin(length((p - vec2(0.0, uFloor - 0.04)) / vec2(0.62, 0.12)) * 18.0);
    lay(c, mix(vec3(0.2, 0.45, 0.55), vec3(0.9, 0.8, 0.6), step(0.0, rings) * 0.4) * light, fill(rug));
    return c;
}

// 6 Windowsill (Kitty): a big window on the city, a ledge, a plant, cosy curtains.
vec4 windowsill(vec2 p) {
    vec3 light = mix(ambient() * 0.6 + vec3(0.05), vec3(0.22, 0.2, 0.3), wNight);
    vec3 wall = mix(vec3(0.93, 0.86, 0.8), vec3(0.8, 0.76, 0.84), smoothstep(uFloor, uHalf.y, p.y));
    wall *= 0.96 + 0.05 * vnoise(p * vec2(3.0, 40.0));
    vec4 c = vec4(wall * light, 0.0);
    vec2 wq = p - vec2(0.0, (uFloor + uHalf.y) * 0.5 + 0.05);
    vec2 wr = vec2(uHalf.x - 0.12, (uHalf.y - uFloor) * 0.5 - 0.12);
    float win = sdBox(wq, wr);
    if (win < 0.0) {
        float horizon = uFloor + 0.25;
        vec4 sky = skyLayer(p, horizon, 0.8);
        c = sky;
        // City skyline with lit windows.
        for (int layer = 0; layer < 2; layer++) {
            float fl = float(layer);
            float bw = 0.12 + fl * 0.06;
            float id = floor(p.x / bw + fl * 3.0);
            float hgt = horizon + 0.15 + 0.4 * h21(vec2(id, fl + 1.0)) - fl * 0.12;
            vec3 bc = mix(vec3(0.45, 0.5, 0.6), vec3(0.03, 0.035, 0.06), wNight) * (0.75 + 0.2 * fl) * mix(vec3(1.0), vec3(1.0, 0.75, 0.65), wDusk + wDawn);
            float bld = p.y - hgt;
            if (bld < 0.0) {
                bc *= 0.8 + 0.35 * smoothstep(horizon, hgt, p.y);
                bc = mix(bc, sky.rgb, 0.25 * (1.0 - fl) * (1.0 - wNight));
                c = vec4(bc, 0.0);
                vec2 g = vec2(p.x / 0.025, p.y / 0.035);
                vec2 cell = fract(g); vec2 gid = floor(g);
                float lit = step(0.55, h21(gid + fl * 11.0)) * step(0.2, cell.x) * step(cell.x, 0.75) * step(0.25, cell.y) * step(cell.y, 0.75);
                float on = clamp(wNight + wDusk * 0.6, 0.0, 1.0);
                layE(c, vec3(1.0, 0.82, 0.45), lit * on, on);
            }
        }
    }
    // Soft reflections on the glass.
    if (win < 0.0) {
        float streak = smoothstep(0.06, 0.0, abs(fract((p.x + p.y * 0.6) * 0.9) - 0.5) - 0.06) + 0.5 * smoothstep(0.02, 0.0, abs(fract((p.x + p.y * 0.6) * 0.9 + 0.2) - 0.5) - 0.015);
        c.rgb += vec3(1.0) * streak * 0.07 * (1.0 - 0.5 * wNight);
    }
    // Inner shadow of the frame.
    lay(c, vec3(0.0), smoothstep(0.07, 0.0, -win) * 0.22 * step(win, 0.0));
    // Window frame and mullions.
    lay(c, vec3(0.96, 0.95, 0.93) * light, fill(abs(win) - 0.022));
    lay(c, vec3(0.96, 0.95, 0.93) * light, fill(abs(wq.x) - 0.012) * step(win, 0.0));
    lay(c, vec3(0.96, 0.95, 0.93) * light, fill(abs(wq.y - wr.y * 0.2) - 0.012) * step(win, 0.0));
    // Curtains tied back.
    for (int s = 0; s < 2; s++) {
        float sx = s == 0 ? -1.0 : 1.0;
        vec2 cq = p - vec2(sx * (uHalf.x - 0.06), uHalf.y);
        float tie = uFloor + 0.55;
        float w = mix(0.07, 0.16, smoothstep(tie, uHalf.y, p.y)) + mix(0.0, 0.08, smoothstep(tie, uFloor + 0.2, p.y));
        float cur = (abs(cq.x) - w) * step(uFloor + 0.18, p.y);
        if (p.y < uFloor + 0.18) cur = 1.0;
        float fold = sin(cq.x * 70.0 + 1.5 * sin(p.y * 2.5 + float(s)));
        vec3 cc = mix(vec3(0.16, 0.24, 0.48), vec3(0.42, 0.56, 0.86), 0.5 + 0.5 * fold);
        cc *= 0.8 + 0.3 * smoothstep(w, 0.0, abs(cq.x));
        lay(c, vec3(0.0), smoothstep(0.08, 0.0, cur) * 0.2 * step(0.0, cur) * step(uFloor + 0.18, p.y));
        lay(c, cc * light, fill(cur));
        // Golden tie-back.
        lay(c, vec3(1.0, 0.8, 0.4) * light, fill(sdBox(p - vec2(sx * (uHalf.x - 0.06), tie), vec2(0.085, 0.012))));
    }
    // The sill (the creature sits on it) and the wall below.
    if (p.y < uFloor + 0.02) {
        c = vec4(mix(vec3(0.62, 0.58, 0.68), vec3(0.8, 0.74, 0.76), smoothstep(-uHalf.y, uFloor, p.y)) * light * 0.85, 0.0);
    }
    float sill = sdRoundBox(p - vec2(0.0, uFloor - 0.01), vec2(uHalf.x + 0.1, 0.035), 0.01);
    vec3 wood = mix(vec3(0.78, 0.56, 0.38), vec3(0.93, 0.74, 0.54), smoothstep(uFloor - 0.045, uFloor + 0.02, p.y));
    wood *= 0.92 + 0.1 * vnoise(vec2(p.x * 6.0, p.y * 160.0));
    wood += vec3(1.0, 0.95, 0.85) * smoothstep(0.012, 0.0, abs(p.y - uFloor - 0.012)) * 0.25;
    lay(c, wood * light, fill(sill));
    lay(c, vec3(0.0), smoothstep(0.0, -0.08, p.y - (uFloor - 0.045)) * 0.35 * step(p.y, uFloor - 0.045));
    // Fairy lights draped across the window.
    {
        float sag = wq.y - wr.y + 0.03 + 0.07 * (1.0 - pow(wq.x / wr.x, 2.0));
        lay(c, vec3(0.2, 0.2, 0.25), fill(abs(sag) - 0.003) * step(abs(wq.x), wr.x));
        float bx = floor(wq.x / 0.11 + 0.5) * 0.11;
        vec2 bq = vec2(wq.x - bx, wq.y - wr.y + 0.05 + 0.07 * (1.0 - pow(bx / wr.x, 2.0)));
        float bulb = sdEllipse(bq, vec2(0.014, 0.02));
        vec3 bcol = 0.6 + 0.4 * cos(6.28 * (bx * 3.1 + vec3(0.0, 0.33, 0.67)));
        float on = 0.35 + 0.65 * clamp(wNight + wDusk, 0.0, 1.0);
        if (abs(bx) < wr.x) {
            c.rgb += bcol * exp(-max(bulb, 0.0) * 30.0) * 0.35 * on;
            layE(c, mix(bcol, vec3(1.0), 0.3) * (0.6 + 0.6 * on), fill(bulb), on * 0.8);
        }
    }
    // Potted plant on the left.
    vec2 pp = p - vec2(-uHalf.x * 0.68, uFloor + 0.025);
    float pot = sdTri(pp, vec2(-0.08, 0.13), vec2(0.08, 0.13), vec2(0.0, -0.06));
    pot = max(sdBox(pp - vec2(0.0, 0.065), vec2(0.075 - (0.13 - pp.y) * 0.12, 0.065)), -1.0);
    lay(c, vec3(0.85, 0.45, 0.3) * light, fill(pot));
    for (int k = 0; k < 6; k++) {
        float a = -0.9 + float(k) * 0.36;
        vec2 dir = vec2(sin(a), cos(a));
        vec2 lq = pp - vec2(0.0, 0.13) - dir * 0.11;
        vec2 lr = vec2(dot(lq, vec2(dir.y, -dir.x)), dot(lq, dir));
        float leaf = sdEllipse(lr, vec2(0.03, 0.11));
        lay(c, mix(vec3(0.2, 0.55, 0.3), vec3(0.3, 0.7, 0.35), float(k) / 5.0) * light, fill(leaf));
    }
    return c;
}

// 7 Rooftop (Kitty): tiled roof, chimney and antenna, the city glowing below.
vec4 rooftop(vec2 p) {
    float horizon = uFloor + 0.1;
    vec4 c = skyLayer(p, horizon, 0.7);
    vec3 amb = ambient();
    // Far city.
    float id = floor(p.x / 0.09);
    float hgt = horizon + 0.05 + 0.3 * h21(vec2(id, 3.0)) * h21(vec2(id, 9.0));
    if (p.y < hgt) {
        c = vec4(mix(vec3(0.35, 0.4, 0.5), vec3(0.04, 0.04, 0.08), wNight) * mix(vec3(1.0), vec3(1.0, 0.7, 0.6), wDusk), 0.0);
        vec2 g = vec2(p.x / 0.02, p.y / 0.03);
        float lit = step(0.6, h21(floor(g) + 3.0)) * step(0.25, fract(g.x)) * step(fract(g.x), 0.7) * step(0.3, fract(g.y)) * step(fract(g.y), 0.7);
        float on = clamp(wNight + wDusk * 0.6, 0.0, 1.0);
        layE(c, vec3(1.0, 0.85, 0.5), lit * on, on);
    }
    // Chimney and antenna.
    vec2 ch = p - vec2(uHalf.x * 0.55, uFloor);
    lay(c, vec3(0.55, 0.25, 0.2) * amb, fill(sdBox(ch - vec2(0.0, 0.2), vec2(0.07, 0.2))));
    lay(c, vec3(0.45, 0.2, 0.17) * amb, fill(sdBox(ch - vec2(0.0, 0.41), vec2(0.085, 0.02))));
    vec2 an = p - vec2(-uHalf.x * 0.5, uFloor);
    float ant = min(sdSeg(an, vec2(0.0), vec2(0.0, 0.55)) - 0.006, min(sdSeg(an, vec2(-0.12, 0.45), vec2(0.12, 0.45)), min(sdSeg(an, vec2(-0.09, 0.38), vec2(0.09, 0.38)), sdSeg(an, vec2(-0.06, 0.31), vec2(0.06, 0.31)))) - 0.004);
    lay(c, vec3(0.25) * amb, fill(ant));
    float beacon = length(an - vec2(0.0, 0.56)) - 0.014;
    c.rgb += vec3(1.0, 0.2, 0.2) * exp(-max(beacon, 0.0) * 30.0) * 0.6 * (wNight + wDusk);
    layE(c, vec3(1.0, 0.25, 0.2), fill(beacon), wNight + wDusk);
    // Roof tiles.
    if (p.y < uFloor + 0.03) {
        vec2 t = vec2(p.x / 0.09 + step(0.5, fract((p.y - uFloor) / 0.06 * 0.5)) * 0.5, (p.y - uFloor) / 0.06);
        vec2 f = fract(t);
        vec3 tile = vec3(0.55, 0.28, 0.22) * (0.8 + 0.2 * f.y) * (0.9 + 0.1 * h21(floor(t)));
        tile *= smoothstep(0.0, 0.06, f.x) * smoothstep(1.0, 0.94, f.x) * 0.25 + 0.75;
        c = vec4(tile * amb * mix(1.0, 0.7, smoothstep(uFloor, uFloor - 0.6, p.y)), 0.0);
    }
    return c;
}

// 8 Box Fort (Kitty): stacked cardboard boxes, warm light, a ball of yarn.
vec4 boxFort(vec2 p) {
    vec3 light = mix(vec3(1.0, 0.92, 0.8), vec3(0.45, 0.38, 0.4), wNight);
    vec4 c = vec4(mix(vec3(0.95, 0.88, 0.78), vec3(0.85, 0.75, 0.65), smoothstep(uFloor, uHalf.y, p.y)) * light * 0.8, 0.0);
    // Warm wall light.
    c.rgb += vec3(1.0, 0.7, 0.4) * exp(-length(p - vec2(0.0, uHalf.y - 0.2)) * 1.6) * 0.25;
    // Boxes.
    for (int i = 0; i < 7; i++) {
        float fi = float(i);
        vec2 bp; vec2 bs;
        if (i < 3) { bp = vec2((fi - 1.0) * uHalf.x * 0.75, uFloor + 0.17); bs = vec2(0.17, 0.17); }
        else if (i < 5) { bp = vec2((fi - 3.5) * uHalf.x * 0.75, uFloor + 0.48); bs = vec2(0.15, 0.14); }
        else if (i == 5) { bp = vec2(-uHalf.x * 0.8, uFloor + 0.75); bs = vec2(0.12, 0.12); }
        else { bp = vec2(uHalf.x * 0.75, uFloor + 0.78); bs = vec2(0.13, 0.13); }
        if (i == 1) continue; // the creature sits here
        vec2 q = p - bp;
        float box = sdBox(q, bs);
        vec3 card = mix(vec3(0.78, 0.6, 0.4), vec3(0.85, 0.66, 0.44), h21(vec2(fi, 2.0))) * light;
        card *= 0.9 + 0.1 * vnoise(q * vec2(4.0, 40.0));
        lay(c, vec3(0.0), smoothstep(0.04, 0.0, box) * 0.25);
        lay(c, card, fill(box));
        lay(c, card * 0.8, fill(abs(q.x) - 0.004) * step(box, 0.0) * step(0.0, q.y));
        lay(c, vec3(0.75, 0.68, 0.55) * light, fill(sdBox(q - vec2(0.0, bs.y * 0.55), vec2(0.03, bs.y * 0.45))) * 0.6);
        if (i == 3) {
            // A cut-out window with a heart.
            float hole = sdRoundBox(q, vec2(0.06, 0.05), 0.02);
            lay(c, vec3(0.15, 0.1, 0.06), fill(hole));
        }
    }
    // Floor.
    if (p.y < uFloor) {
        c = vec4(mix(vec3(0.7, 0.55, 0.42), vec3(0.6, 0.45, 0.34), smoothstep(uFloor, uFloor - 0.6, p.y)) * light, 0.0);
    }
    // Yarn ball.
    vec2 y = p - vec2(uHalf.x * 0.62, uFloor + 0.06);
    float ball = length(y) - 0.07;
    vec3 yc = vec3(0.95, 0.35, 0.5) * light;
    float strands = abs(sin(dot(y, vec2(0.7, 0.7)) * 120.0)) * 0.3 + abs(sin(dot(y, vec2(-0.6, 0.8)) * 100.0)) * 0.2;
    lay(c, yc * (1.0 - strands * 0.4), fill(ball));
    lay(c, yc, fill(sdSeg(p, vec2(uHalf.x * 0.55, uFloor + 0.01), vec2(uHalf.x * 0.2, uFloor - 0.04)) - 0.004));
    return c;
}

// 9 Rainforest (Kiwi): layered jungle in haze, light shafts by day, hanging vines, glowing fungi at night.
vec4 rainforest(vec2 p) {
    vec3 amb = ambient();
    float top = (p.y + uHalf.y) / (2.0 * uHalf.y);
    // Canopy light at the top, deep green haze below.
    vec3 hazeDay = mix(vec3(0.18, 0.42, 0.3), vec3(0.62, 0.85, 0.6), smoothstep(0.2, 1.0, top));
    vec3 hazeNight = mix(vec3(0.0, 0.02, 0.03), vec3(0.02, 0.07, 0.1), top);
    vec3 haze = mix(hazeNight, hazeDay * mix(vec3(1.0), vec3(1.0, 0.75, 0.55), wDawn + wDusk), 1.0 - wNight);
    vec4 c = vec4(haze, 0.0);
    // Sky peeks through gaps in the canopy.
    float gaps = smoothstep(0.62, 0.72, fbm(p * vec2(3.0, 5.0) + 2.0)) * smoothstep(uHalf.y - 0.7, uHalf.y - 0.1, p.y);
    vec4 sky = skyLayer(p, uFloor + 0.3, 0.0);
    c.rgb = mix(c.rgb, sky.rgb, gaps * 0.8); c.a = mix(c.a, sky.a, gaps);
    // Three layers of trunks and foliage, farther = paler.
    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        float depth = 1.0 - fi / 3.0;
        vec3 col = mix(haze, mix(vec3(0.02, 0.08, 0.05), vec3(0.08, 0.22, 0.12) * amb, 1.0 - wNight), 0.35 + 0.3 * fi);
        float spacing = 0.55 - fi * 0.1;
        float cell = floor((p.x + fi * 0.37) / spacing);
        float tx = (cell + 0.5) * spacing - fi * 0.37 + (h21(vec2(cell, fi)) - 0.5) * spacing * 0.5;
        float w = (0.025 + 0.02 * fi) * (0.7 + 0.6 * h21(vec2(cell, fi + 3.0)));
        float trunk = abs(p.x - tx - 0.03 * sin(p.y * 3.0 + cell)) - w;
        lay(c, col * (0.85 + 0.2 * vnoise(vec2(p.x * 80.0, p.y * 8.0))), fill(trunk) * step(0.25, h21(vec2(cell, fi + 7.0))));
        float foliage = fbm(vec2(p.x * (2.0 + fi), p.y * 3.0 + fi * 5.0)) - 0.15 * (p.y - uHalf.y + 0.4) * (1.0 + fi);
        lay(c, col * (0.8 + 0.4 * vnoise(p * 30.0)), smoothstep(0.62 - 0.04 * fi, 0.66 - 0.04 * fi, foliage) * smoothstep(-0.2, 0.4, p.y - uFloor));
        // Fog between layers.
        c.rgb = mix(c.rgb, haze, 0.18 * depth);
    }
    // God rays.
    float rays = pow(max(0.0, sin((p.x * 0.8 + p.y * 0.35) * 9.0 + 1.3)), 6.0) * smoothstep(uFloor - 0.4, uHalf.y, p.y);
    c.rgb += vec3(1.0, 0.95, 0.75) * rays * 0.16 * (wDay + 0.6 * wDawn + 0.6 * wDusk);
    // Hanging vines.
    for (int i = 0; i < 5; i++) {
        float fi = float(i);
        float vx = (h21(vec2(fi, 11.0)) * 2.0 - 1.0) * uHalf.x;
        float len = 0.4 + 0.6 * h21(vec2(fi, 13.0));
        float vy = uHalf.y - p.y;
        float vine = abs(p.x - vx - 0.02 * sin(vy * 8.0 + fi)) - 0.005;
        float on = step(vy, len);
        vec3 vc = mix(vec3(0.02, 0.06, 0.04), vec3(0.15, 0.35, 0.15) * amb, 1.0 - wNight);
        lay(c, vc, fill(vine) * on);
        vec2 lq = vec2(p.x - vx - 0.02 * sin(vy * 8.0 + fi), mod(vy, 0.07) - 0.035);
        float leaf = length(lq * vec2(1.0, 1.6) - vec2(0.018 * sign(sin(floor(vy / 0.07) * 2.0 + fi)), 0.0)) - 0.016;
        lay(c, vc * 1.3, fill(leaf) * on);
    }
    // Glowing fungi on the forest floor at night.
    for (int i = 0; i < 6; i++) {
        float fi = float(i);
        vec2 mp = vec2((h21(vec2(fi, 21.0)) * 2.0 - 1.0) * uHalf.x * 0.9, -uHalf.y + 0.12 + 0.12 * h21(vec2(fi, 23.0)));
        float cap = sdEllipse(p - mp, vec2(0.03, 0.015));
        float stem = sdBox(p - mp + vec2(0.0, 0.022), vec2(0.006, 0.02));
        vec3 gc = mix(vec3(0.5, 0.9, 1.0), vec3(0.75, 0.6, 1.0), h21(vec2(fi, 2.0)));
        c.rgb += gc * exp(-max(cap, 0.0) * 25.0) * 0.35 * wNight;
        layE(c, mix(vec3(0.85, 0.8, 0.7) * amb, gc, wNight), fill(min(cap, stem)), wNight * 0.9);
    }
    // Big leaves in the bottom corners.
    for (int s = 0; s < 2; s++) {
        float side = s == 0 ? -1.0 : 1.0;
        vec2 lq = p - vec2(side * uHalf.x, -uHalf.y + 0.05);
        lq = vec2(lq.x * side, lq.y);
        vec2 r = vec2(cos(0.8), sin(0.8));
        vec2 l = vec2(dot(lq, r), dot(lq, vec2(-r.y, r.x)));
        float leaf = sdEllipse(l - vec2(-0.25, 0.0), vec2(0.38, 0.14));
        float slits = step(0.85, sin(l.x * 30.0)) * step(0.04, abs(l.y)) * step(-0.5, l.x);
        vec3 lc = mix(vec3(0.01, 0.04, 0.02), vec3(0.05, 0.25, 0.1) * amb, 1.0 - wNight * 0.8);
        lay(c, lc * (0.8 + 0.3 * smoothstep(0.1, -0.1, l.y)), fill(leaf) * (1.0 - slits));
        lay(c, lc * 1.8, fill(abs(l.y) - 0.004) * step(leaf, 0.0));
    }
    return c;
}

// 10 Palm Beach (Kiwi): sea and sky by time of day, the sun's path on the water, a palm, sand.
vec4 palmBeach(vec2 p) {
    float horizon = uFloor + 0.42;
    vec4 c = skyLayer(p, horizon, 1.0);
    vec3 amb = ambient();
    vec2 sp = sunPos();
    float sunUp = smoothstep(5.0, 7.0, uHour) * (1.0 - smoothstep(18.0, 19.5, uHour));
    // Distant island.
    float isl = horizon + 0.05 * exp(-pow((p.x - uHalf.x * 0.35) * 5.0, 2.0)) + 0.025 * exp(-pow((p.x - uHalf.x * 0.5) * 9.0, 2.0));
    lay(c, mix(vec3(0.25, 0.4, 0.45), vec3(0.02, 0.03, 0.06), wNight) * mix(vec3(1.0), vec3(1.0, 0.6, 0.5), wDusk + wDawn), fill(p.y - isl));
    // Sea.
    float sandY = -uHalf.y + 0.3 + 0.03 * sin(p.x * 3.0);
    if (p.y < horizon) {
        float dd = (horizon - p.y) / max(horizon - sandY, 0.1);
        vec3 deep = vec3(0.02, 0.2, 0.38) * wDay + vec3(0.25, 0.18, 0.3) * (wDawn + wDusk) + vec3(0.0, 0.02, 0.06) * wNight;
        vec3 shallow = vec3(0.1, 0.65, 0.7) * wDay + vec3(0.7, 0.4, 0.35) * (wDawn + wDusk) + vec3(0.01, 0.05, 0.09) * wNight;
        vec3 sea = mix(deep, shallow, smoothstep(0.3, 1.0, dd));
        // Wave lines.
        float wv = sin(p.x * (20.0 + 30.0 * dd) + vnoise(p * vec2(4.0, 30.0)) * 4.0 + p.y * 60.0);
        sea *= 0.9 + 0.12 * smoothstep(0.7, 1.0, wv);
        lay(c, sea, fill(p.y - horizon) * 1.0);
        // Sun or moon path on the water.
        vec2 lp = sunUp > 0.1 ? sp : vec2(uHalf.x * 0.45, uHalf.y - 0.35);
        float path = exp(-pow((p.x - lp.x) * (6.0 + 10.0 * (1.0 - dd)), 2.0)) * (0.5 + 0.5 * smoothstep(0.3, 0.9, vnoise(p * vec2(20.0, 80.0))));
        vec3 pc = mix(vec3(0.75, 0.8, 1.0) * 0.5, mix(vec3(1.0, 0.95, 0.8), vec3(1.0, 0.55, 0.25), wDawn + wDusk), sunUp);
        c.rgb += pc * path * 0.55 * (sunUp + wNight * 0.6);
        c.a = max(c.a, path * 0.5 * smoothstep(0.6, 0.9, vnoise(p * vec2(30.0, 90.0))));
        // Foam at the shore.
        float foam = abs(p.y - sandY - 0.025 - 0.01 * sin(p.x * 9.0 + 1.0)) - 0.006;
        lay(c, vec3(0.95) * amb, fill(foam) * 0.8);
    }
    // Sand.
    vec3 sand = mix(vec3(0.95, 0.85, 0.65), vec3(0.85, 0.72, 0.52), smoothstep(sandY, -uHalf.y, p.y)) * amb;
    sand *= 0.92 + 0.1 * vnoise(p * 120.0);
    lay(c, sand, fill(p.y - sandY));
    // Shells and a starfish.
    vec2 st = p - vec2(-uHalf.x * 0.55, -uHalf.y + 0.12);
    float ang = atan(st.y, st.x);
    float star = length(st) - 0.035 - 0.018 * cos(ang * 5.0);
    lay(c, vec3(1.0, 0.5, 0.35) * amb, fill(star));
    // Palm on the right: curved trunk with rings, fronds at the top.
    vec2 base = vec2(uHalf.x * 0.95, -uHalf.y + 0.2);
    float tt = clamp((p.y - base.y) / (uHalf.y * 1.6), 0.0, 1.0);
    float tx = base.x - 0.25 * tt * tt;
    float trunk = abs(p.x - tx) - mix(0.05, 0.028, tt);
    float tOn = step(p.y, base.y + uHalf.y * 1.6) * step(base.y, p.y);
    vec3 tc = mix(vec3(0.35, 0.25, 0.15), vec3(0.05, 0.04, 0.04), wNight) * (0.75 + 0.35 * step(0.5, fract(p.y * 22.0)));
    lay(c, tc * mix(vec3(1.0), amb, 0.5), fill(trunk) * tOn);
    vec2 crown = vec2(base.x - 0.25, base.y + uHalf.y * 1.6);
    for (int i = 0; i < 7; i++) {
        float fi = float(i);
        float a = -2.6 + fi * 0.62;
        vec2 dir = vec2(cos(a), sin(a));
        vec2 q = p - crown;
        float along = dot(q, dir);
        float droop = 0.25 * along * along;
        float off = dot(q, vec2(-dir.y, dir.x)) + droop;
        float frond = abs(off) - 0.035 * (1.0 - along / 0.45) * step(0.0, along);
        frond = max(frond, along - 0.45);
        float lobes = 0.85 + 0.15 * step(0.4, fract(along * 30.0));
        vec3 fc = mix(vec3(0.1, 0.35, 0.15), vec3(0.01, 0.03, 0.02), wNight) * mix(vec3(1.0), amb, 0.4);
        lay(c, fc * lobes, fill(frond) * step(0.0, along));
    }
    return c;
}

// 11 Blossom Garden (Kiwi): a meadow under cherry trees, blossom canopy above, a lantern that glows at night.
vec4 blossomGarden(vec2 p) {
    float horizon = uFloor + 0.38;
    vec4 c = skyLayer(p, horizon, 0.5);
    vec3 amb = ambient();
    c.rgb = mix(c.rgb, c.rgb * vec3(1.05, 0.86, 0.96) + vec3(0.07, 0.02, 0.05), 0.5 * (1.0 - wNight));
    // Distant hills dotted with pink trees.
    vec3 hc = mix(vec3(0.55, 0.62, 0.62), vec3(0.03, 0.04, 0.07), wNight) * mix(vec3(1.0), vec3(1.0, 0.78, 0.78), wDusk + wDawn);
    lay(c, hc, hillMask(p, horizon + 0.03, 0.035, 2.2, 4.0));
    vec2 tg = vec2(p.x * 14.0, (p.y - horizon) * 14.0);
    vec2 tid = floor(tg); vec2 tf = fract(tg) - 0.5;
    if (p.y > horizon - 0.05 && p.y < horizon + 0.09 && h21(tid + 2.0) > 0.55) {
        float tree = length(tf * vec2(1.0, 1.3)) - 0.38;
        vec3 tcol = mix(vec3(0.95, 0.68, 0.8), vec3(0.15, 0.08, 0.15), wNight) * mix(vec3(1.0), vec3(1.0, 0.8, 0.8), wDusk);
        lay(c, tcol, clamp(0.5 - tree * 6.0, 0.0, 1.0) * 0.85);
    }
    // Meadow from the hills to the bottom, darker and richer closer to you.
    float gy = horizon - 0.02 + 0.015 * sin(p.x * 3.0);
    float near = smoothstep(gy, -uHalf.y, p.y);
    vec3 grass = mix(vec3(0.55, 0.72, 0.42), vec3(0.22, 0.45, 0.2), near);
    grass = mix(grass, vec3(0.02, 0.05, 0.04), wNight * 0.9) * mix(vec3(1.0), vec3(1.0, 0.82, 0.75), wDusk + wDawn);
    grass *= 0.88 + 0.18 * vnoise(vec2(p.x * (40.0 + 80.0 * near), p.y * 10.0));
    lay(c, grass, fill(p.y - gy));
    // Fallen petals, bigger in front.
    float ps = mix(60.0, 18.0, near);
    vec2 g = vec2(p.x, p.y * 1.6) * ps; vec2 id = floor(g); vec2 f = fract(g) - 0.5;
    if (p.y < gy && h21(id + 5.0) > 0.72) {
        float pet = length(rot2(f, h21(id) * 6.0) * vec2(1.0, 1.7)) - 0.22;
        lay(c, mix(vec3(1.0, 0.78, 0.88), vec3(0.25, 0.15, 0.22), wNight) * amb, clamp(0.5 - pet * 12.0, 0.0, 1.0) * 0.9);
    }
    // Stone lantern.
    vec2 lq = p - vec2(uHalf.x * 0.62, -uHalf.y + 0.32);
    float ln = min(min(sdBox(lq - vec2(0.0, 0.08), vec2(0.018, 0.08)), sdBox(lq - vec2(0.0, 0.2), vec2(0.045, 0.04))), sdTri(lq, vec2(-0.075, 0.24), vec2(0.075, 0.24), vec2(0.0, 0.3)));
    ln = min(ln, sdBox(lq - vec2(0.0, 0.0), vec2(0.05, 0.015)));
    lay(c, mix(vec3(0.62, 0.62, 0.6), vec3(0.1, 0.1, 0.13), wNight) * amb * (0.85 + 0.2 * vnoise(lq * 90.0)), fill(ln));
    float lamp = sdBox(lq - vec2(0.0, 0.2), vec2(0.022, 0.022));
    float lit = clamp(wNight + wDusk, 0.0, 1.0);
    c.rgb += vec3(1.0, 0.7, 0.35) * exp(-max(lamp, 0.0) * 14.0) * 0.5 * lit;
    layE(c, mix(vec3(0.2), vec3(1.0, 0.8, 0.45), lit), fill(lamp), lit);
    // Blossom canopy hanging from the top edge: soft clumps with flower detail, dark branches inside.
    float edge = uHalf.y - 0.22 - 0.12 * fbm(vec2(p.x * 2.5, 1.0)) + 0.08 * sin(p.x * 1.7);
    float clump = fbm(p * vec2(6.0, 8.0) + 3.0);
    float canopy = smoothstep(-0.02, 0.02, p.y - edge + 0.12 * (clump - 0.5));
    // A few clumps hang lower.
    for (int i = 0; i < 4; i++) {
        float fi = float(i);
        vec2 cp = vec2((h21(vec2(fi, 41.0)) * 2.0 - 1.0) * uHalf.x, uHalf.y - 0.35 - 0.12 * h21(vec2(fi, 43.0)));
        canopy = max(canopy, smoothstep(0.02, -0.02, length((p - cp) * vec2(1.0, 1.4)) - 0.08 - 0.05 * clump));
    }
    float br = abs(p.y - (uHalf.y - 0.12 - 0.06 * sin(p.x * 2.2))) - 0.012;
    br = min(br, sdSeg(p, vec2(-uHalf.x, uHalf.y - 0.3), vec2(-uHalf.x * 0.2, uHalf.y - 0.14)) - 0.009);
    lay(c, mix(vec3(0.3, 0.18, 0.16), vec3(0.03), wNight), fill(br));
    vec3 pink = mix(vec3(0.98, 0.66, 0.8), vec3(1.0, 0.9, 0.95), smoothstep(0.3, 0.8, clump));
    pink *= mix(vec3(1.0), amb, 0.7);
    pink = mix(pink, vec3(0.18, 0.1, 0.18), wNight * 0.7);
    vec2 bg = p * 20.0; vec2 bid = floor(bg); vec2 bf = fract(bg) - 0.5;
    float flower = length(bf) - 0.25 - 0.1 * cos(atan(bf.y, bf.x) * 5.0);
    float fl = step(0.5, h21(bid)) * smoothstep(0.06, -0.06, flower);
    lay(c, pink * (0.85 + 0.25 * fl), canopy);
    lay(c, mix(vec3(1.0, 0.92, 0.6), vec3(0.4, 0.3, 0.3), wNight) * amb, canopy * fl * clamp(0.5 - (length(bf) - 0.06) * 30.0, 0.0, 1.0));
    // Bokeh glow in the air.
    for (int i = 0; i < 6; i++) {
        float fi = float(i);
        vec2 bp = vec2((h21(vec2(fi, 31.0)) * 2.0 - 1.0) * uHalf.x, horizon + 0.1 + h21(vec2(fi, 33.0)) * (uHalf.y - horizon - 0.4));
        float bd = length(p - bp) - 0.05 - 0.04 * h21(vec2(fi, 35.0));
        c.rgb += mix(vec3(1.0, 0.8, 0.9), vec3(1.0, 0.85, 0.5), wNight) * smoothstep(0.02, -0.02, bd) * 0.07;
    }
    return c;
}

// ---------------- Glowy's sea ----------------
vec4 seaBase(vec2 p, vec3 top, vec3 bot, float rayAmt) {
    float t = clamp((p.y + uHalf.y) / (2.0 * uHalf.y), 0.0, 1.0);
    float dayL = 1.0 - 0.8 * wNight;
    vec3 col = mix(bot, top, pow(t, 1.6)) * mix(0.3, 1.0, dayL);
    col *= mix(vec3(1.0), vec3(1.05, 0.8, 0.75), (wDawn + wDusk) * t);
    float rays = pow(max(0.0, sin((p.x + p.y * 0.32) * 6.5 + 1.6 * sin(p.x * 1.7 + 0.5))), 5.0) * smoothstep(-uHalf.y * 0.4, uHalf.y, p.y);
    col += vec3(0.55, 0.85, 1.0) * rays * 0.22 * rayAmt * dayL;
    // Moonbeam at night.
    col += vec3(0.4, 0.5, 0.9) * exp(-pow((p.x - 0.1 - p.y * 0.2) * 3.0, 2.0)) * 0.1 * wNight * t;
    return vec4(col, 0.0);
}
float rockEdge(vec2 p, float side, float reach, float seed) {
    float e = reach * (0.55 + 0.6 * fbm(vec2(p.y * 2.2 + seed, seed)) + 0.12 * vnoise(vec2(p.y * 14.0, seed)));
    return (uHalf.x - e) - p.x * side;
}

// 12 Midnight Trench: near-black water, cliffs, glowing tube worms, drifting jellyfish.
vec4 trench(vec2 p) {
    vec4 c = seaBase(p, vec3(0.02, 0.1, 0.24), vec3(0.0, 0.0, 0.015), 0.7);
    // Plankton specks (they twinkle in the overlay).
    vec2 g = p * 26.0; vec2 id = floor(g); vec2 f = fract(g) - 0.5;
    vec2 o = vec2(h21(id), h21(id + 7.0)) - 0.5;
    float sp = step(0.86, h21(id + 3.0)) * smoothstep(0.09, 0.0, length(f - o * 0.7));
    layE(c, mix(vec3(0.4, 0.9, 1.0), vec3(0.7, 0.6, 1.0), h21(id + 1.0)), sp * 0.9, 0.6);
    // Jellyfish far away.
    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        vec2 jp = vec2((h21(vec2(fi, 51.0)) * 2.0 - 1.0) * uHalf.x * 0.7, (h21(vec2(fi, 53.0)) - 0.2) * uHalf.y);
        vec2 q = p - jp;
        float bell = max(sdEllipse(q, vec2(0.07, 0.055)), -q.y - 0.01);
        vec3 jc = mix(vec3(0.5, 0.4, 1.0), vec3(0.3, 0.9, 1.0), h21(vec2(fi, 2.0)));
        c.rgb += jc * exp(-max(bell, 0.0) * 18.0) * 0.16;
        lay(c, jc * 0.75, fill(bell) * 0.55);
        for (int k = 0; k < 4; k++) {
            float fk = float(k);
            float tx = (fk - 1.5) * 0.03;
            float tent = abs(q.x - tx - 0.012 * sin(q.y * 40.0 + fk)) - 0.004;
            lay(c, jc * 0.6, fill(tent) * step(q.y, -0.005) * step(-0.17 - 0.03 * fk, q.y) * 0.5);
        }
    }
    // Cliffs on both sides and a ridge along the bottom.
    for (int s = 0; s < 2; s++) {
        float side = s == 0 ? -1.0 : 1.0;
        float e = rockEdge(p, side, uHalf.x * 0.42, 3.0 + float(s) * 9.0);
        vec3 rc = vec3(0.035, 0.05, 0.095) * (0.5 + 0.9 * fbm(p * vec2(5.0, 3.0))) * (1.0 - 0.5 * wNight);
        rc += vec3(0.05, 0.16, 0.28) * smoothstep(0.04, 0.0, -e) * (1.0 - 0.5 * wNight);
        lay(c, rc, fill(e));
        // Tube worms glowing on the ledges.
        for (int k = 0; k < 4; k++) {
            float fk = float(k);
            float wy = -uHalf.y + 0.3 + fk * (uHalf.y * 0.42) + 0.1 * h21(vec2(fk, float(s)));
            float ex = (uHalf.x - uHalf.x * 0.42 * (0.55 + 0.6 * fbm(vec2(wy * 2.2 + 3.0 + float(s) * 9.0, 3.0 + float(s) * 9.0)))) * side;
            vec2 q = p - vec2(ex, wy);
            float tube = sdCapsule(q, vec2(0.0), vec2(-0.05 * side, 0.03), 0.008, 0.006);
            float tip = length(q - vec2(-0.055 * side, 0.034)) - 0.014;
            vec3 tc = mix(vec3(1.0, 0.35, 0.6), vec3(0.3, 1.0, 0.8), mod(fk + float(s), 2.0));
            lay(c, vec3(0.08, 0.1, 0.16), fill(tube));
            c.rgb += tc * exp(-max(tip, 0.0) * 30.0) * 0.4;
            layE(c, tc, fill(tip), 0.9);
        }
    }
    float ridge = -uHalf.y + 0.14 + 0.1 * fbm(vec2(p.x * 2.5, 4.0)) - p.y;
    lay(c, vec3(0.008, 0.014, 0.03) * (0.6 + 0.8 * vnoise(p * 12.0)), smoothstep(-0.01, 0.01, ridge));
    return c;
}

// 13 Coral Garden: bright shallows, sand, corals and sea fans in candy colours, kelp.
vec4 coralGarden(vec2 p) {
    vec4 c = seaBase(p, vec3(0.1, 0.62, 0.78), vec3(0.02, 0.18, 0.38), 1.2);
    // Far reef silhouettes.
    float far = -uHalf.y * 0.25 + 0.12 * fbm(vec2(p.x * 2.0, 7.0)) - p.y;
    lay(c, mix(c.rgb, vec3(0.03, 0.2, 0.34) * (1.0 - 0.7 * wNight), 0.6), smoothstep(-0.01, 0.02, far));
    // Kelp swaying shapes.
    for (int i = 0; i < 5; i++) {
        float fi = float(i);
        float kx = (h21(vec2(fi, 61.0)) * 2.0 - 1.0) * uHalf.x;
        float top = -uHalf.y + 0.5 + 0.7 * h21(vec2(fi, 63.0));
        float kelp = abs(p.x - kx - 0.04 * sin(p.y * 5.0 + fi * 2.0)) - 0.016 * smoothstep(top, top - 0.4, p.y);
        vec3 kc = mix(vec3(0.05, 0.3, 0.22), vec3(0.2, 0.55, 0.3), smoothstep(-uHalf.y, top, p.y)) * (1.0 - 0.75 * wNight);
        lay(c, kc, fill(kelp) * step(p.y, top));
    }
    // Sand.
    float sandY = -uHalf.y + 0.24 + 0.03 * sin(p.x * 4.0 + 1.0);
    vec3 sand = mix(vec3(0.95, 0.86, 0.66), vec3(0.75, 0.7, 0.62), smoothstep(sandY, -uHalf.y, p.y)) * ambient();
    sand *= 0.9 + 0.12 * vnoise(vec2(p.x * 30.0, p.y * 90.0));
    sand = mix(sand, sand * vec3(0.5, 0.8, 1.0), 0.35);
    // Corals standing on the sand.
    for (int i = 0; i < 6; i++) {
        float fi = float(i);
        vec2 base = vec2((fi / 5.0 * 2.0 - 1.0) * uHalf.x * 0.92 + 0.05 * sin(fi * 5.0), sandY - 0.01);
        vec3 cc = 0.55 + 0.45 * cos(6.28 * (fi * 0.19 + vec3(0.0, 0.35, 0.6)));
        cc = mix(cc, vec3(1.0, 0.5, 0.6), 0.25) * mix(vec3(1.0), ambient(), 0.7);
        vec2 q = p - base;
        float d = 1e5;
        if (mod(fi, 3.0) < 0.5) {
            // Branching coral.
            for (int k = 0; k < 5; k++) {
                float a = (float(k) - 2.0) * 0.38;
                vec2 tip = vec2(sin(a), cos(a)) * (0.2 + 0.05 * h21(vec2(fi, float(k))));
                d = min(d, sdCapsule(q, vec2(0.0), tip, 0.022, 0.012));
                d = min(d, sdCapsule(q, tip * 0.6, tip * 0.6 + vec2(cos(a) * 0.06, 0.06), 0.012, 0.008));
            }
        } else if (mod(fi, 3.0) < 1.5) {
            // Brain coral dome.
            d = max(sdEllipse(q - vec2(0.0, 0.02), vec2(0.13, 0.1)), -q.y);
            cc *= 0.8 + 0.25 * sin(length(q) * 90.0 + sin(atan(q.y, q.x) * 6.0) * 2.0);
        } else {
            // Sea fan.
            d = max(sdEllipse(q - vec2(0.0, 0.14), vec2(0.12, 0.15)), -q.y);
            cc *= 0.65 + 0.4 * step(0.5, fract(atan(q.x, q.y + 0.02) * 9.0)) + 0.2 * step(0.6, fract(length(q) * 28.0));
        }
        lay(c, cc * (0.75 + 0.35 * smoothstep(0.0, 0.25, q.y)), fill(d));
        c.rgb += cc * exp(-max(d, 0.0) * 25.0) * 0.1 * wNight;
    }
    lay(c, sand, fill(p.y - sandY));
    // Shells and pebbles.
    vec2 g = p * vec2(14.0, 30.0); vec2 id = floor(g); vec2 f = fract(g) - 0.5;
    if (p.y < sandY - 0.03 && h21(id + 9.0) > 0.8) lay(c, mix(vec3(1.0, 0.8, 0.85), vec3(0.6, 0.65, 0.75), h21(id)) * ambient(), clamp(0.5 - (length(f * vec2(1.0, 1.6)) - 0.2) * 12.0, 0.0, 1.0));
    return c;
}

// 14 Sunken Ship: a wreck in green murk, portholes that glow at night, a chest of gold.
vec4 sunkenShip(vec2 p) {
    vec4 c = seaBase(p, vec3(0.06, 0.4, 0.45), vec3(0.01, 0.07, 0.12), 1.0);
    float sandY = -uHalf.y + 0.26 + 0.04 * sin(p.x * 3.0 + 2.0);
    // The wreck: tilted hull, cabin, broken mast.
    vec2 q = p - vec2(uHalf.x * 0.15, sandY + 0.16);
    float ca = cos(-0.16), sa = sin(-0.16);
    q = vec2(ca * q.x - sa * q.y, sa * q.x + ca * q.y);
    float hull = max(sdEllipse(q - vec2(0.0, 0.1), vec2(0.62, 0.3)), q.y - 0.1);
    float cabin = sdBox(q - vec2(-0.18, 0.2), vec2(0.17, 0.1));
    float mast = min(sdBox(q - vec2(0.1, 0.42), vec2(0.014, 0.33)), sdBox(q - vec2(0.1, 0.55), vec2(0.16, 0.01)));
    float ship = min(min(hull, cabin), mast);
    vec3 wood = mix(vec3(0.05, 0.12, 0.12), vec3(0.16, 0.22, 0.18), smoothstep(-0.2, 0.3, q.y)) * (1.0 - 0.6 * wNight);
    wood *= 0.8 + 0.3 * step(0.5, fract(q.y * 22.0)) * step(q.y, 0.1) + 0.15 * vnoise(q * vec2(8.0, 60.0));
    lay(c, wood, fill(ship));
    // A hole in the hull.
    lay(c, vec3(0.0, 0.01, 0.02), fill(sdEllipse(q - vec2(0.3, -0.03), vec2(0.07, 0.05) * (0.8 + 0.4 * vnoise(q * 20.0)))));
    // Portholes.
    float lit = clamp(wNight + wDusk, 0.0, 1.0);
    for (int i = 0; i < 4; i++) {
        vec2 pc = i < 2 ? vec2(-0.26 + float(i) * 0.16, 0.21) : vec2(-0.3 + float(i - 2) * 0.3, -0.0);
        float ph = length(q - pc) - 0.032;
        lay(c, vec3(0.3, 0.26, 0.15), fill(ph - 0.01));
        c.rgb += vec3(1.0, 0.75, 0.35) * exp(-max(ph, 0.0) * 18.0) * 0.35 * lit;
        layE(c, mix(vec3(0.02, 0.08, 0.1), vec3(1.0, 0.82, 0.45), lit), fill(ph), lit);
    }
    // Tattered sail.
    float sail = sdTri(q, vec2(0.1, 0.54), vec2(0.1, 0.3), vec2(0.24 + 0.02 * sin(q.y * 30.0), 0.5));
    lay(c, vec3(0.5, 0.55, 0.5) * (1.0 - 0.6 * wNight), fill(sail) * 0.7);
    // Sand, chest and anchor.
    vec3 sand = mix(vec3(0.6, 0.62, 0.48), vec3(0.3, 0.36, 0.32), smoothstep(sandY, -uHalf.y, p.y)) * ambient() * vec3(0.6, 0.9, 0.9);
    sand *= 0.9 + 0.12 * vnoise(vec2(p.x * 30.0, p.y * 90.0));
    lay(c, sand, fill(p.y - sandY));
    vec2 cq = p - vec2(-uHalf.x * 0.55, sandY - 0.02);
    float chest = sdRoundBox(cq - vec2(0.0, 0.03), vec2(0.09, 0.055), 0.01);
    float lid = max(sdEllipse(cq - vec2(0.0, 0.085), vec2(0.095, 0.06)), -(cq.y - 0.085));
    lay(c, vec3(0.4, 0.22, 0.1) * mix(vec3(1.0), ambient(), 0.6), fill(chest));
    lay(c, vec3(0.5, 0.28, 0.12) * mix(vec3(1.0), ambient(), 0.6), fill(lid) * step(0.12, cq.y - 0.02 + 0.1));
    lay(c, vec3(0.95, 0.75, 0.25), fill(abs(cq.x) - 0.008) * fill(chest));
    // Gold spilling out; the coins twinkle.
    for (int i = 0; i < 5; i++) {
        float fi = float(i);
        vec2 gp = cq - vec2(-0.07 + fi * 0.035, 0.09 + 0.012 * sin(fi * 2.0));
        float coin = length(gp) - 0.016;
        layE(c, vec3(1.0, 0.85, 0.35), fill(coin), 0.6);
    }
    c.rgb += vec3(1.0, 0.8, 0.3) * exp(-length(cq - vec2(0.0, 0.1)) * 9.0) * 0.18;
    // Kelp in front.
    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        float kx = (-0.9 + fi * 0.85) * uHalf.x + 0.05;
        float top = -uHalf.y + 0.45 + 0.3 * h21(vec2(fi, 73.0));
        float kelp = abs(p.x - kx - 0.035 * sin(p.y * 6.0 + fi * 2.0)) - 0.02 * smoothstep(top, top - 0.35, p.y);
        lay(c, vec3(0.03, 0.2, 0.13) * (1.0 - 0.7 * wNight), fill(kelp) * step(p.y, top));
    }
    return c;
}

// ---------------- Gromble's lairs ----------------
// 15 Lava Lair: a cave with a river of lava, stalactites, and a throne that is far too big for him.
vec4 lavaLair(vec2 p) {
    float t = clamp((p.y + uHalf.y) / (2.0 * uHalf.y), 0.0, 1.0);
    float heat = 0.75 + 0.25 * (1.0 - wNight);
    vec4 c = vec4(mix(vec3(0.16, 0.03, 0.03), vec3(0.03, 0.01, 0.05), t) * heat, 0.0);
    // Back wall rock with glowing cracks.
    float rock = fbm(p * vec2(3.0, 2.0) + 4.0);
    c.rgb *= 0.6 + 0.9 * rock;
    float crack = smoothstep(0.02, 0.0, abs(fbm(p * 2.2 + 11.0) - 0.5) - 0.006) * smoothstep(uFloor, uFloor + 0.9, p.y);
    c.rgb += vec3(1.0, 0.35, 0.05) * crack * 0.5;
    c.a = max(c.a, crack * 0.5);
    // Stalactites.
    for (int i = 0; i < 7; i++) {
        float fi = float(i);
        float sx = (fi / 6.0 * 2.0 - 1.0) * uHalf.x + 0.06 * sin(fi * 7.0);
        float len = 0.2 + 0.45 * h21(vec2(fi, 81.0));
        float st = abs(p.x - sx) - 0.07 * clamp((p.y - (uHalf.y - len)) / len, 0.0, 1.0);
        vec3 rc = vec3(0.06, 0.03, 0.05) + vec3(0.25, 0.07, 0.02) * smoothstep(0.03, 0.0, -st) * 0.6;
        lay(c, rc, fill(st) * step(uHalf.y - len, p.y));
    }
    // Lava river behind the floor ledge.
    float lavaY = uFloor + 0.16 + 0.015 * sin(p.x * 6.0);
    if (p.y < lavaY) {
        float flow = fbm(vec2(p.x * 3.0, p.y * 9.0) + 2.0);
        vec3 lava = mix(vec3(1.0, 0.25, 0.02), vec3(1.0, 0.85, 0.3), smoothstep(0.35, 0.7, flow));
        lava = mix(lava, vec3(0.25, 0.04, 0.02), smoothstep(0.55, 0.3, flow) * 0.7);
        c = vec4(lava, 0.35 + 0.4 * smoothstep(0.5, 0.75, flow));
    }
    c.rgb += vec3(1.0, 0.35, 0.05) * exp(-max(p.y - lavaY, 0.0) * 5.0) * 0.35;
    // Throne on the right.
    vec2 q = p - vec2(uHalf.x * 0.55, uFloor + 0.02);
    float throne = min(sdBox(q - vec2(0.0, 0.3), vec2(0.13, 0.3)), sdBox(q - vec2(0.0, 0.08), vec2(0.17, 0.08)));
    throne = min(throne, min(sdTri(q, vec2(-0.13, 0.6), vec2(-0.05, 0.6), vec2(-0.1, 0.74)), sdTri(q, vec2(0.13, 0.6), vec2(0.05, 0.6), vec2(0.1, 0.74))));
    throne = min(throne, sdTri(q, vec2(-0.04, 0.6), vec2(0.04, 0.6), vec2(0.0, 0.8)));
    lay(c, vec3(0.1, 0.04, 0.09) + vec3(0.4, 0.1, 0.03) * smoothstep(0.03, 0.0, -throne) * 0.5, fill(throne));
    lay(c, vec3(0.5, 0.05, 0.12), fill(sdBox(q - vec2(0.0, 0.32), vec2(0.09, 0.2))));
    // Rock ledge he stands on.
    float ledge = uFloor + 0.02 + 0.012 * vnoise(vec2(p.x * 9.0, 2.0)) - p.y;
    vec3 lc = mix(vec3(0.16, 0.07, 0.08), vec3(0.04, 0.02, 0.04), smoothstep(uFloor, -uHalf.y, p.y)) * (0.75 + 0.5 * vnoise(p * 14.0));
    lc += vec3(0.9, 0.3, 0.05) * smoothstep(0.03, 0.0, ledge) * 0.4;
    lay(c, lc, smoothstep(-0.005, 0.005, ledge));
    return c;
}

// 16 Haunted Hill: a crooked castle under a big moon, a dead tree, wonky tombstones.
vec4 hauntedHill(vec2 p) {
    float horizon = uFloor + 0.3;
    vec4 c = skyLayer(p, horizon, 0.7);
    // Purple wash: spooky by night, pastel-silly by day.
    c.rgb = mix(c.rgb, c.rgb * vec3(0.85, 0.7, 1.1) + vec3(0.05, 0.0, 0.08), 0.6);
    vec3 amb = ambient();
    vec3 sil = mix(vec3(0.3, 0.24, 0.42), vec3(0.03, 0.02, 0.07), wNight) * mix(vec3(1.0), vec3(1.0, 0.7, 0.75), wDusk + wDawn);
    // Hill with the castle.
    float hill = horizon + 0.22 * exp(-pow((p.x + uHalf.x * 0.25) * 2.2, 2.0)) + 0.03 * sin(p.x * 5.0);
    vec2 q = p - vec2(-uHalf.x * 0.25, horizon + 0.2);
    q.x += q.y * 0.12;
    float castle = min(sdBox(q - vec2(0.0, 0.1), vec2(0.12, 0.12)), min(sdBox(q - vec2(-0.13, 0.17), vec2(0.04, 0.19)), sdBox(q - vec2(0.13, 0.14), vec2(0.04, 0.16))));
    castle = min(castle, min(sdTri(q, vec2(-0.185, 0.36), vec2(-0.075, 0.36), vec2(-0.13, 0.5)), sdTri(q, vec2(0.075, 0.3), vec2(0.185, 0.3), vec2(0.13, 0.43))));
    castle = min(castle, sdTri(q, vec2(-0.06, 0.22), vec2(0.06, 0.22), vec2(0.0, 0.34)));
    lay(c, sil * 0.8, fill(castle));
    float lit = clamp(wNight + wDusk, 0.0, 1.0);
    for (int i = 0; i < 3; i++) {
        vec2 wp = i == 0 ? vec2(-0.13, 0.26) : (i == 1 ? vec2(0.13, 0.2) : vec2(0.0, 0.1));
        float win = sdRoundBox(q - wp, vec2(0.014, 0.024), 0.012);
        c.rgb += vec3(1.0, 0.6, 0.2) * exp(-max(win, 0.0) * 30.0) * 0.3 * lit;
        layE(c, mix(sil * 0.4, vec3(1.0, 0.75, 0.3), lit), fill(win), lit);
    }
    lay(c, sil, fill(p.y - hill));
    // Bats.
    for (int i = 0; i < 4; i++) {
        float fi = float(i);
        vec2 bp = p - vec2((h21(vec2(fi, 91.0)) * 2.0 - 1.0) * uHalf.x * 0.8, horizon + 0.5 + h21(vec2(fi, 93.0)) * (uHalf.y - horizon - 0.7));
        float bat = min(sdTri(bp, vec2(0.0, 0.0), vec2(-0.045, 0.02), vec2(-0.02, -0.012)), sdTri(bp, vec2(0.0, 0.0), vec2(0.045, 0.02), vec2(0.02, -0.012)));
        lay(c, sil * 0.5, fill(bat));
    }
    // Dead tree on the right.
    vec2 tq = p - vec2(uHalf.x * 0.72, uFloor);
    float tree = sdCapsule(tq, vec2(0.0, -0.05), vec2(-0.02, 0.5), 0.04, 0.018);
    tree = min(tree, sdCapsule(tq, vec2(-0.015, 0.3), vec2(-0.2, 0.52), 0.016, 0.005));
    tree = min(tree, sdCapsule(tq, vec2(-0.02, 0.42), vec2(0.13, 0.62), 0.014, 0.004));
    tree = min(tree, sdCapsule(tq, vec2(-0.12, 0.43), vec2(-0.14, 0.6), 0.008, 0.003));
    lay(c, sil * 0.45, fill(tree));
    // Ground and tombstones.
    float gy = uFloor + 0.03 + 0.015 * sin(p.x * 5.0);
    vec3 ground = mix(vec3(0.3, 0.42, 0.34), vec3(0.03, 0.04, 0.07), wNight * 0.9) * (0.85 + 0.25 * vnoise(vec2(p.x * 50.0, p.y * 8.0)));
    ground = mix(ground, ground * 0.5, smoothstep(gy, -uHalf.y, p.y) * 0.5);
    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        vec2 sq = p - vec2((-0.75 + fi * 0.5) * uHalf.x + 0.05, gy + 0.07);
        float a = (h21(vec2(fi, 97.0)) - 0.5) * 0.4;
        sq = vec2(cos(a) * sq.x - sin(a) * sq.y, sin(a) * sq.x + cos(a) * sq.y);
        float stone = min(sdBox(sq, vec2(0.05, 0.07)), length(sq - vec2(0.0, 0.07)) - 0.05);
        vec3 sc = mix(vec3(0.55, 0.56, 0.62), vec3(0.12, 0.12, 0.2), wNight) * (0.85 + 0.2 * vnoise(sq * 60.0));
        lay(c, sc, fill(stone));
        lay(c, sc * 0.55, fill(min(abs(sq.x) - 0.004, abs(sq.y - 0.03) - 0.004)) * fill(max(abs(sq.x) - 0.025, abs(sq.y - 0.02) - 0.04)));
    }
    lay(c, ground, fill(p.y - gy));
    // Low fog.
    c.rgb = mix(c.rgb, mix(vec3(0.75, 0.7, 0.9), vec3(0.2, 0.18, 0.35), wNight), smoothstep(0.25, 0.0, abs(p.y - gy - 0.03)) * 0.35 * (0.5 + 0.5 * fbm(vec2(p.x * 3.0, 5.0))));
    return c;
}

// 17 Evil HQ: his bedroom. A "world domination" corkboard, a lava lamp, a tiny bed.
vec4 evilHq(vec2 p) {
    vec3 light = mix(ambient() * 0.65 + vec3(0.06), vec3(0.2, 0.17, 0.3), wNight);
    vec3 wall = mix(vec3(0.42, 0.3, 0.55), vec3(0.3, 0.2, 0.45), smoothstep(uFloor, uHalf.y, p.y));
    // Striped wallpaper with tiny skulls.
    wall *= 0.92 + 0.08 * step(0.5, fract(p.x * 4.0));
    vec2 g = p * 7.0; vec2 id = floor(g); vec2 f = fract(g) - 0.5;
    if (mod(id.x + id.y, 2.0) < 0.5) wall = mix(wall, wall * 1.25, clamp(0.5 - (length(f * vec2(1.0, 1.15) - vec2(0.0, 0.03)) - 0.11) * 20.0, 0.0, 1.0));
    vec4 c = vec4(wall * light, 0.0);
    // Corkboard with a world map, pins and red string.
    vec2 q = p - vec2(-uHalf.x * 0.25, uFloor + 0.95);
    float board = sdRoundBox(q, vec2(0.32, 0.2), 0.01);
    lay(c, vec3(0.45, 0.28, 0.14) * light, fill(board - 0.018));
    lay(c, vec3(0.78, 0.6, 0.38) * (0.9 + 0.12 * vnoise(q * 60.0)) * light, fill(board));
    float land = smoothstep(0.5, 0.56, fbm(q * 7.0 + 3.0));
    lay(c, vec3(0.45, 0.68, 0.5) * light, land * fill(sdRoundBox(q, vec2(0.26, 0.15), 0.01)));
    for (int i = 0; i < 4; i++) {
        float fi = float(i);
        vec2 pin = vec2(-0.2 + fi * 0.13, 0.08 * sin(fi * 2.4));
        vec2 nxt = vec2(-0.2 + (fi + 1.0) * 0.13, 0.08 * sin((fi + 1.0) * 2.4));
        if (i < 3) lay(c, vec3(0.9, 0.1, 0.15), fill(sdSeg(q, pin, nxt) - 0.003));
        lay(c, vec3(1.0, 0.85, 0.2), fill(length(q - pin) - 0.012));
    }
    lay(c, vec3(0.9, 0.1, 0.15), fill(min(sdSeg(q, vec2(0.2, -0.1), vec2(0.27, -0.03)), sdSeg(q, vec2(0.2, -0.03), vec2(0.27, -0.1))) - 0.006));
    // "Keep out" pennant.
    vec2 pq = p - vec2(uHalf.x * 0.5, uFloor + 1.15);
    lay(c, vec3(0.85, 0.15, 0.2) * light, fill(sdTri(pq, vec2(-0.12, 0.05), vec2(-0.12, -0.05), vec2(0.12, 0.0))));
    // Lava lamp on a shelf: it glows.
    vec2 lq = p - vec2(uHalf.x * 0.6, uFloor + 0.62);
    lay(c, vec3(0.3, 0.2, 0.15) * light, fill(sdBox(lq - vec2(0.0, -0.02), vec2(0.16, 0.012))));
    float lamp = sdRoundBox(lq - vec2(0.0, 0.11), vec2(0.035, 0.1), 0.03);
    float blob = min(length(lq - vec2(0.005, 0.07)) - 0.022, length(lq - vec2(-0.008, 0.15)) - 0.016);
    c.rgb += vec3(0.5, 1.0, 0.3) * exp(-max(lamp, 0.0) * 9.0) * (0.15 + 0.3 * wNight);
    layE(c, vec3(0.15, 0.4, 0.12), fill(lamp), 0.3);
    layE(c, vec3(0.6, 1.0, 0.35), fill(max(blob, lamp)), 0.9);
    lay(c, vec3(0.25, 0.25, 0.3) * light, fill(min(sdBox(lq - vec2(0.0, 0.0), vec2(0.045, 0.015)), sdBox(lq - vec2(0.0, 0.215), vec2(0.03, 0.012)))));
    // Skirting board, floor and a rug.
    if (p.y < uFloor + 0.06) c = vec4(vec3(0.22, 0.15, 0.3) * light, 0.0);
    if (p.y < uFloor + 0.02) {
        vec3 wood = mix(vec3(0.42, 0.28, 0.22), vec3(0.3, 0.19, 0.16), step(0.5, fract(p.x * 2.5 + floor((uFloor - p.y) * 9.0) * 0.5)));
        wood *= 0.9 + 0.12 * vnoise(vec2(p.x * 8.0, p.y * 120.0));
        c = vec4(wood * light * mix(1.0, 0.6, smoothstep(uFloor, -uHalf.y, p.y)), 0.0);
        float rug = sdEllipse(p - vec2(0.0, uFloor - 0.07), vec2(uHalf.x * 0.8, 0.07));
        vec3 rc = mix(vec3(0.75, 0.12, 0.2), vec3(0.2, 0.05, 0.12), step(0.5, fract(length((p - vec2(0.0, uFloor - 0.07)) / vec2(uHalf.x * 0.8, 0.07)) * 3.0)));
        lay(c, rc * light, fill(rug));
    }
    // Tiny bed on the left edge.
    vec2 bq = p - vec2(-uHalf.x * 0.95, uFloor + 0.08);
    lay(c, vec3(0.2, 0.12, 0.25) * light, fill(sdRoundBox(bq - vec2(0.0, 0.1), vec2(0.03, 0.2), 0.02)));
    lay(c, vec3(0.6, 0.15, 0.25) * light, fill(sdRoundBox(bq - vec2(0.15, 0.0), vec2(0.17, 0.07), 0.03)));
    lay(c, vec3(0.95, 0.92, 0.98) * light, fill(sdRoundBox(bq - vec2(0.07, 0.07), vec2(0.06, 0.035), 0.03)));
    return c;
}

void main() {
    px = 2.0 * uHalf.y / uRes.y;
    vec2 p = (gl_FragCoord.xy / uRes - 0.5) * uHalf * 2.0;
    dayWeights();
    vec4 c;
    if (uWorld < 1.5) c = cloudNursery(p);
    else if (uWorld < 2.5) c = batteryLab(p);
    else if (uWorld < 3.5) c = backyard(p);
    else if (uWorld < 4.5) c = snowDay(p);
    else if (uWorld < 5.5) c = cozyRoom(p);
    else if (uWorld < 6.5) c = windowsill(p);
    else if (uWorld < 7.5) c = rooftop(p);
    else if (uWorld < 8.5) c = boxFort(p);
    else if (uWorld < 9.5) c = rainforest(p);
    else if (uWorld < 10.5) c = palmBeach(p);
    else if (uWorld < 11.5) c = blossomGarden(p);
    else if (uWorld < 12.5) c = trench(p);
    else if (uWorld < 13.5) c = coralGarden(p);
    else if (uWorld < 14.5) c = sunkenShip(p);
    else if (uWorld < 15.5) c = lavaLair(p);
    else if (uWorld < 16.5) c = hauntedHill(p);
    else c = evilHq(p);
    // Gentle vignette keeps the edges dark on AMOLED.
    float v = smoothstep(1.4, 0.4, length(p / uHalf));
    c.rgb *= mix(0.55, 1.0, v);
    // Finishing: a soft vignette and a breath of grain so nothing looks flat.
    {
        c.rgb += (h21(gl_FragCoord.xy * 0.37 + uSeed) - 0.5) * 0.018;
    }
    gl_FragColor = vec4(clamp(c.rgb, 0.0, 1.0), clamp(c.a, 0.0, 1.0));
}
