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
    vec4 c = vec4(vec3(0.75, 0.78, 0.82) * light, 0.0);
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
                c = vec4(bc, 0.0);
                vec2 g = vec2(p.x / 0.025, p.y / 0.035);
                vec2 cell = fract(g); vec2 gid = floor(g);
                float lit = step(0.55, h21(gid + fl * 11.0)) * step(0.2, cell.x) * step(cell.x, 0.75) * step(0.25, cell.y) * step(cell.y, 0.75);
                float on = clamp(wNight + wDusk * 0.6, 0.0, 1.0);
                layE(c, vec3(1.0, 0.82, 0.45), lit * on, on);
            }
        }
    }
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
        lay(c, vec3(0.3, 0.42, 0.62) * light * (0.85 + 0.15 * sin(cq.x * 80.0)), fill(cur));
    }
    // The sill (the creature sits on it) and the wall below.
    if (p.y < uFloor + 0.02) {
        c = vec4(vec3(0.7, 0.72, 0.76) * light * 0.8, 0.0);
    }
    float sill = sdRoundBox(p - vec2(0.0, uFloor - 0.01), vec2(uHalf.x + 0.1, 0.035), 0.01);
    lay(c, vec3(0.97, 0.96, 0.94) * light, fill(sill));
    lay(c, vec3(0.0), smoothstep(0.0, -0.08, p.y - (uFloor - 0.045)) * 0.35 * step(p.y, uFloor - 0.045));
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
    else c = boxFort(p);
    // Gentle vignette keeps the edges dark on AMOLED.
    float v = smoothstep(1.4, 0.4, length(p / uHalf));
    c.rgb *= mix(0.55, 1.0, v);
    gl_FragColor = vec4(clamp(c.rgb, 0.0, 1.0), clamp(c.a, 0.0, 1.0));
}
