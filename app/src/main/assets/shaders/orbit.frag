precision highp float;
// Reaper / Mischief worlds, drawn in code and seen from the orbiting camera.
// v2: real 3D props (cones, boxes, rounded tombstones, mushrooms, faceted crystals) placed in the world and lit,
// a bump-lit ground with a key light per world, contact shadows, layered horizons with anti-aliased edges,
// mirror ice, falling embers / fireflies / snow / spores, filmic tone mapping.
uniform vec2 uRes; uniform float uYaw; uniform float uTime; uniform vec3 uCam; uniform float uTanF; uniform float uPitch; uniform float uBass; uniform vec2 uTarget;
uniform float uEnv; uniform vec4 uFire;   // fire: xyz world position, w = radius (0 = none): lights the ground

float h11(float x){ return fract(sin(x * 127.1) * 43758.5453); }
float h21(vec2 p){ p = fract(p * vec2(123.34, 456.21)); p += dot(p, p + 45.32); return fract(p.x * p.y); }
vec2 h22(vec2 p){ return vec2(h21(p), h21(p + 17.17)); }
float noise(vec2 p){ vec2 i = floor(p), f = fract(p); f = f * f * (3.0 - 2.0 * f);
  return mix(mix(h21(i), h21(i + vec2(1.0, 0.0)), f.x), mix(h21(i + vec2(0.0, 1.0)), h21(i + vec2(1.0, 1.0)), f.x), f.y); }
float fbm3(vec2 p){ float n = 0.0, a = 0.5; for (int i = 0; i < 3; i++) { n += a * noise(p); p = p * 2.07 + 3.1; a *= 0.5; } return n / 0.875; }
float fbm(vec2 p){ float n = 0.0, a = 0.5; for (int i = 0; i < 5; i++) { n += a * noise(p); p = p * 2.07 + 3.1; a *= 0.5; } return n / 0.96875; }
float crack0(vec2 p){ vec2 i = floor(p), f = fract(p); float d1 = 8.0, d2 = 8.0;
  for (int y = -1; y <= 1; y++) for (int x = -1; x <= 1; x++) { vec2 g = vec2(float(x), float(y)); vec2 o = h22(i + g); float d = length(g + o - f);
    if (d < d1) { d2 = d1; d1 = d; } else if (d < d2) d2 = d; }
  return d2 - d1; }
float crack(vec2 p){ return crack0(p + vec2(noise(p * 1.7), noise(p * 1.7 + 5.2)) * 0.55); }

int E;
float PX;   // one pixel, in radians
vec3 KEY; vec3 KEYC; vec3 AMB; vec3 HAZE;

// ------------------------------------------------------------------ sky and far horizon
float ridge(float az, float base, float amp, float fr, float seed){ return base + amp * fbm3(vec2(az * fr, seed)) + amp * 0.25 * noise(vec2(az * fr * 8.0, seed + 3.0)); }
float edge(float el, float h){ return smoothstep(h + PX * 1.2, h - PX * 1.2, el); }   // 1 below the line, anti-aliased

vec3 sky(vec3 rd){
  float az = atan(rd.x, rd.z), el = asin(clamp(rd.y, -1.0, 1.0));
  float pulse = 0.85 + 0.15 * sin(uTime * 0.9) + 0.25 * uBass;
  vec2 sg = vec2(az * 70.0, el * 70.0); vec2 si = floor(sg); float st = h21(si);
  float star = st > 0.93 ? smoothstep(0.12, 0.0, length(sg - si - h22(si))) * (0.55 + 0.45 * sin(uTime * (1.0 + 3.0 * h21(si + 5.0)) + st * 50.0)) * (0.4 + 0.6 * h21(si + 9.0)) : 0.0;
  vec3 col;
  if (E == 0) {           // volcano: ash sky lit red from below, an erupting cone, glowing ridge lines
    col = mix(vec3(0.34, 0.08, 0.03), vec3(0.015, 0.006, 0.01), smoothstep(-0.02, 0.5, el));
    float cl = fbm(vec2(az * 2.4 + uTime * 0.012, el * 5.0 - uTime * 0.006));
    col += vec3(0.5, 0.12, 0.04) * smoothstep(0.4, 0.85, cl) * smoothstep(0.6, 0.02, el) * 0.7;
    col += vec3(1.0, 0.9, 0.8) * star * smoothstep(0.25, 0.5, el) * 0.5;
    float da = atan(sin(az + 2.3), cos(az + 2.3));
    // plume: a column of lit ash rising from the crater
    float pl = smoothstep(0.24, 0.3, el) * exp(-pow(da * (3.0 - el * 2.5), 2.0) * 6.0) * smoothstep(1.2, 0.3, el);
    col = mix(col, vec3(0.16, 0.05, 0.035) * (0.6 + 0.8 * fbm(vec2(da * 8.0, el * 6.0 - uTime * 0.08))), pl * 0.85);
    col += vec3(1.0, 0.35, 0.08) * pl * exp(-(el - 0.26) * 6.0) * 0.6 * pulse;
    // far range, then the volcano cone, then a nearer range
    float r1 = ridge(az, 0.035, 0.06, 2.0, 4.0);
    float cone = 0.26 - abs(da) * 0.48 + 0.012 * noise(vec2(az * 40.0, 1.0));
    float r2 = ridge(az, 0.01, 0.04, 3.5, 9.0);
    float b1 = edge(el, max(r1, cone));
    vec3 rc = vec3(0.03, 0.014, 0.014) + vec3(0.25, 0.05, 0.02) * smoothstep(0.08, 0.0, max(r1, cone) - el) * 0.5;
    float lava = smoothstep(0.02, 0.0, abs(da + 0.04 * sin(el * 50.0)) - 0.003) * smoothstep(0.0, 0.22, el) * step(r1, cone);
    rc += vec3(1.0, 0.32, 0.05) * lava * pulse + vec3(1.0, 0.45, 0.08) * smoothstep(0.025, 0.0, cone - el) * smoothstep(0.07, 0.0, abs(da)) * 1.5 * pulse;
    col = mix(col, rc, b1);
    col = mix(col, vec3(0.012, 0.007, 0.008), edge(el, r2));
    col += vec3(1.0, 0.3, 0.06) * exp(-abs(da) * 8.0) * exp(-max(0.0, el - 0.2) * 8.0) * smoothstep(0.17, 0.25, el) * 0.45 * pulse;
  } else if (E == 1) {    // moonlit graveyard: big moon with a halo, drifting clouds, dead trees and a church on the hill
    col = mix(vec3(0.09, 0.05, 0.14), vec3(0.006, 0.004, 0.016), smoothstep(-0.02, 0.6, el)) + vec3(0.85, 0.85, 1.0) * star * smoothstep(0.03, 0.25, el) * 0.8;
    vec3 md3 = normalize(vec3(-0.62, 0.26, -0.76)); float md = acos(clamp(dot(rd, md3), -1.0, 1.0));
    float disc = smoothstep(0.06 + PX, 0.06 - PX, md);
    vec2 mq = vec2(atan(rd.x - md3.x, rd.z - md3.z), rd.y - md3.y) * 30.0;
    col = mix(col, vec3(1.0, 0.9, 0.72) * (0.78 + 0.22 * fbm3(mq * 1.5) - 0.18 * smoothstep(0.55, 0.75, fbm3(mq * 0.9 + 4.0))), disc);
    col += vec3(0.6, 0.45, 0.85) * exp(-md * 7.0) * 0.3 + vec3(0.9, 0.8, 1.0) * exp(-md * 25.0) * 0.12;
    float cl = fbm(vec2(az * 2.0 + uTime * 0.01, el * 9.0));
    float cb = smoothstep(0.55, 0.8, cl) * smoothstep(0.05, 0.25, el) * smoothstep(0.6, 0.3, el);
    col = mix(col, vec3(0.08, 0.06, 0.12) + vec3(0.5, 0.42, 0.6) * exp(-md * 4.0) * 0.6, cb * 0.85);
    float hill = ridge(az, 0.03, 0.05, 2.2, 1.0);
    col = mix(col, vec3(0.03, 0.02, 0.055), edge(el, hill));
    // dead trees: thin trunks with forked tops, scattered along the hill
    float ta = az * 14.0; float ti = floor(ta); float tf = fract(ta) - 0.5; float th = h21(vec2(ti, 3.0));
    if (th > 0.55) { float tall = hill + 0.05 + 0.08 * h21(vec2(ti, 7.0)); float y = (el - hill) / (tall - hill);
      float w = (0.06 - 0.04 * y) * 0.25 + abs(sin(y * 9.0 + ti)) * 0.08 * smoothstep(0.45, 1.0, y);
      float trunk = smoothstep(w + PX * 6.0, w, abs(tf + 0.15 * sin(y * 5.0 + ti) * y)) * step(hill - 0.01, el) * step(el, tall);
      col = mix(col, vec3(0.012, 0.008, 0.02), trunk); }
    // a little church with a lit window
    float ca = atan(sin(az - 0.9), cos(az - 0.9)) * 30.0;
    float church = step(abs(ca), 0.9) * step(el, hill + 0.035 + max(0.0, 0.03 - abs(ca) * 0.03)) + step(abs(ca - 0.7), 0.16) * step(el, hill + 0.085 - abs(ca - 0.7) * 0.25);
    col = mix(col, vec3(0.015, 0.01, 0.025), clamp(church, 0.0, 1.0) * step(hill - 0.02, el));
    col += vec3(1.0, 0.7, 0.3) * step(abs(ca + 0.2), 0.12) * step(abs(el - hill - 0.018), 0.008) * (0.8 + 0.2 * sin(uTime * 3.0));
  } else if (E == 2) {    // crystal cavern: a dome of rock, glowing veins, giant crystals growing from the walls
    float w = fbm(vec2(az * 3.0, el * 5.0)); col = vec3(0.018, 0.014, 0.035) * (0.6 + w);
    float v = crack(vec2(az * 4.0, el * 6.0) + 2.0);
    col += vec3(0.55, 0.2, 1.0) * smoothstep(0.04, 0.0, v) * (0.35 + 0.3 * sin(uTime * 0.7 + az * 3.0)) * smoothstep(-0.05, 0.3, el);
    col += vec3(0.3, 0.1, 0.6) * smoothstep(0.15, 0.0, v) * 0.08;
    col += vec3(0.2, 0.9, 1.0) * star * 0.35 * smoothstep(0.1, 0.5, el);
    float stal = 0.55 - 0.32 * pow(noise(vec2(az * 22.0, 3.0)), 2.0) - 0.1 * pow(noise(vec2(az * 60.0, 5.0)), 3.0);
    col = mix(col, vec3(0.012, 0.009, 0.024), smoothstep(stal - PX, stal + PX, el));
    // wall crystals: faceted spikes leaning in from the far wall
    float ca = az * 6.0; float ci = floor(ca); float cf = fract(ca) - 0.5; float ch = h21(vec2(ci, 2.0));
    float tall = 0.06 + 0.22 * ch; float lean = (h21(vec2(ci, 4.0)) - 0.5) * 0.6;
    float wdt = 0.22 * (1.0 - el / tall); float cx = cf - lean * el / tall;
    if (el > 0.0 && el < tall && abs(cx) < wdt) {
      float facet = step(0.0, cx); vec3 cc = mix(vec3(0.3, 0.1, 0.75), vec3(0.55, 0.3, 1.0), facet) * (0.4 + 0.9 * el / tall);
      cc += vec3(0.6, 0.95, 1.0) * smoothstep(0.02, 0.0, wdt - abs(cx)) * 0.5 + vec3(0.4, 0.8, 1.0) * smoothstep(0.03, 0.0, abs(cx)) * 0.3;
      col = mix(col, cc * (0.85 + 0.25 * sin(uTime * 1.1 + ci)), smoothstep(0.0, 0.01, wdt - abs(cx))); }
    float r1 = ridge(az, 0.0, 0.05, 4.0, 8.0);
    col = mix(col, vec3(0.03, 0.02, 0.06), edge(el, r1));
  } else if (E == 4) {    // candy meadow at dusk: pastel sky, a big low sun, cotton-candy clouds, lollipop trees
    col = mix(vec3(1.0, 0.6, 0.55), vec3(0.5, 0.3, 0.62), smoothstep(-0.02, 0.22, el)); col = mix(col, vec3(0.1, 0.07, 0.22), smoothstep(0.2, 0.8, el));
    col += vec3(1.0, 0.95, 0.9) * star * smoothstep(0.4, 0.7, el) * 0.7;
    vec3 sd = normalize(vec3(0.5, 0.06, -0.85)); float sdd = acos(clamp(dot(rd, sd), -1.0, 1.0));
    col += vec3(1.0, 0.72, 0.5) * exp(-sdd * 5.0) * 0.5; col = mix(col, vec3(1.0, 0.9, 0.7), smoothstep(0.07 + PX, 0.07 - PX, sdd));
    float cl = fbm(vec2(az * 1.8 + uTime * 0.006, el * 6.0)); float band = smoothstep(0.03, 0.12, el) * smoothstep(0.5, 0.2, el);
    float cm = smoothstep(0.5, 0.62, cl) * band;
    col = mix(col, mix(vec3(1.0, 0.68, 0.78), vec3(1.0, 0.95, 0.92), smoothstep(0.6, 0.85, cl)) + vec3(1.0, 0.7, 0.5) * exp(-sdd * 3.0) * 0.3, cm * 0.9);
    float hill = ridge(az, 0.03, 0.05, 1.8, 3.0);
    col = mix(col, mix(vec3(0.5, 0.35, 0.62), vec3(0.72, 0.5, 0.7), smoothstep(hill - 0.03, hill, el)), edge(el, hill));
    // lollipop trees on the hills
    float ta = az * 10.0; float ti = floor(ta); float tf = fract(ta) - 0.5;
    if (h21(vec2(ti, 6.0)) > 0.5) { float base = hill - 0.005, top = base + 0.05 + 0.04 * h21(vec2(ti, 1.0)); float rr = 0.022 + 0.01 * h21(vec2(ti, 2.0));
      float stick = step(abs(tf), 0.03) * step(base, el) * step(el, top);
      float ball = smoothstep(rr + PX * 2.0, rr - PX, length(vec2(tf * 0.1, el - top - rr * 0.6)));
      vec3 bc = h21(vec2(ti, 9.0)) > 0.5 ? vec3(1.0, 0.45, 0.6) : vec3(0.6, 0.85, 0.75);
      col = mix(col, vec3(0.95, 0.9, 0.88), stick); col = mix(col, bc * (0.8 + 0.4 * smoothstep(-rr, rr, el - top)), ball); }
    float hill2 = ridge(az, 0.012, 0.03, 3.4, 8.0);
    col = mix(col, mix(vec3(0.45, 0.55, 0.55), vec3(0.6, 0.48, 0.62), 0.5) * (0.92 + 0.12 * noise(vec2(az * 30.0, 1.0))), edge(el, hill2));
  } else {                // frozen soul lake: an aurora in curtains, snowy peaks
    col = mix(vec3(0.06, 0.11, 0.18), vec3(0.003, 0.006, 0.02), smoothstep(-0.02, 0.6, el)) + vec3(0.9, 0.95, 1.0) * star * smoothstep(0.05, 0.3, el);
    float ab = sin(az * 2.0 + fbm3(vec2(az * 3.0, uTime * 0.05)) * 4.0 + uTime * 0.1) * 0.06 + 0.3;
    float rays = 0.55 + 0.45 * noise(vec2(az * 60.0 + uTime * 0.3, 1.0));
    float aur = exp(-pow((el - ab) * 7.0, 2.0)) * smoothstep(ab - 0.12, ab, el) * rays * (0.5 + 0.5 * fbm3(vec2(az * 12.0, uTime * 0.2)));
    float aur2 = exp(-pow((el - ab - 0.12) * 5.0, 2.0)) * 0.4 * rays;
    col += mix(vec3(0.1, 1.0, 0.6), vec3(0.6, 0.25, 1.0), smoothstep(0.25, 0.45, el)) * (aur + aur2 * 0.6) * 0.5;
    float mnt = 0.05 + 0.13 * pow(fbm3(vec2(az * 2.6, 9.0)), 1.5) + 0.02 * noise(vec2(az * 30.0, 1.0));
    float shade = smoothstep(0.35, 0.65, noise(vec2(az * 22.0, el * 30.0)));
    vec3 mc = mix(vec3(0.03, 0.05, 0.09), vec3(0.5, 0.6, 0.78) * (0.55 + 0.45 * shade), smoothstep(mnt - 0.045, mnt - 0.005, el));
    col = mix(col, mc, edge(el, mnt));
    float m2 = 0.02 + 0.04 * fbm3(vec2(az * 5.0, 4.0));
    col = mix(col, vec3(0.04, 0.07, 0.11), edge(el, m2));
  }
  return col;
}

// ------------------------------------------------------------------ props: analytic shapes placed in the world
// hit record
float T; vec3 N; vec3 SC; vec3 EM;
void hitCone(vec3 ro, vec3 rd, vec2 c, float r, float hgt, float facets, float id){
  float k = r / hgt; vec3 o = vec3(ro.x - c.x, ro.y, ro.z - c.y);
  float oy = r - k * o.y, dy = -k * rd.y;
  float a = rd.x * rd.x + rd.z * rd.z - dy * dy, b = 2.0 * (o.x * rd.x + o.z * rd.z - oy * dy), cc = o.x * o.x + o.z * o.z - oy * oy;
  float disc = b * b - 4.0 * a * cc; if (disc < 0.0) return;
  float sq = sqrt(disc); float t1 = (-b - sq) / (2.0 * a), t2 = (-b + sq) / (2.0 * a);
  float t = min(t1, t2); float y = o.y + rd.y * t;
  if (t < 0.0 || y < 0.0 || y > hgt) { t = max(t1, t2); y = o.y + rd.y * t; }
  if (t < 0.0 || y < 0.0 || y > hgt || t > T) return;
  vec3 p = o + rd * t; vec2 d = p.xz;
  if (facets > 0.0) { float an = atan(d.y, d.x) + id; float s = 6.2832 / facets; an = (floor(an / s) + 0.5) * s - id; d = vec2(cos(an), sin(an)) * length(d); }
  T = t; N = normalize(vec3(d.x, k * (r - k * y), d.y)); SC = vec3(y / hgt, id, 0.0);
}
void hitBox(vec3 ro, vec3 rd, vec2 c, float ang, vec3 b, float id){
  float ca = cos(ang), sa = sin(ang);
  vec3 o = vec3(ro.x - c.x, ro.y - b.y, ro.z - c.y); o = vec3(ca * o.x - sa * o.z, o.y, sa * o.x + ca * o.z);
  vec3 d = vec3(ca * rd.x - sa * rd.z, rd.y, sa * rd.x + ca * rd.z);
  vec3 m = 1.0 / d; vec3 n = m * o; vec3 k = abs(m) * b; vec3 t1 = -n - k, t2 = -n + k;
  float tn = max(max(t1.x, t1.y), t1.z), tf = min(min(t2.x, t2.y), t2.z);
  if (tn > tf || tf < 0.0 || tn > T || tn < 0.0) return;
  vec3 nl = -sign(d) * step(t1.yzx, t1.xyz) * step(t1.zxy, t1.xyz);
  T = tn; N = vec3(ca * nl.x + sa * nl.z, nl.y, -sa * nl.x + ca * nl.z); vec3 p = o + d * tn; SC = vec3((p.y + b.y) / (2.0 * b.y), id, p.x / b.x);
}
// a horizontal cylinder (the rounded top of a tombstone) along the stone's thin axis
void hitHCyl(vec3 ro, vec3 rd, vec2 c, float ang, float yc, float r, float th, float id){
  float ca = cos(ang), sa = sin(ang);
  vec3 o = vec3(ro.x - c.x, ro.y - yc, ro.z - c.y); o = vec3(ca * o.x - sa * o.z, o.y, sa * o.x + ca * o.z);
  vec3 d = vec3(ca * rd.x - sa * rd.z, rd.y, sa * rd.x + ca * rd.z);
  float a = d.x * d.x + d.y * d.y, b = 2.0 * (o.x * d.x + o.y * d.y), cc = o.x * o.x + o.y * o.y - r * r;
  float disc = b * b - 4.0 * a * cc;
  if (disc > 0.0) { float t = (-b - sqrt(disc)) / (2.0 * a); vec3 p = o + d * t;
    if (t > 0.0 && t < T && abs(p.z) < th && p.y > 0.0) { T = t; vec3 nl = vec3(p.x, p.y, 0.0) / r; N = vec3(ca * nl.x + sa * nl.z, nl.y, -sa * nl.x + ca * nl.z); SC = vec3(1.0 + p.y, id, p.x / r); return; } }
  // the flat faces of the round top
  if (abs(d.z) > 1e-4) { float t = (sign(d.z) * -th - o.z) / d.z; vec3 p = o + d * t;
    if (t > 0.0 && t < T && p.y > 0.0 && dot(p.xy, p.xy) < r * r) { T = t; vec3 nl = vec3(0.0, 0.0, -sign(d.z)); N = vec3(ca * nl.x + sa * nl.z, nl.y, -sa * nl.x + ca * nl.z); SC = vec3(1.0 + p.y, id, p.x / r); } }
}
void hitSphere(vec3 ro, vec3 rd, vec3 c, float r, float ycut, float id){
  vec3 o = ro - c; float b = dot(o, rd), cc = dot(o, o) - r * r, disc = b * b - cc; if (disc < 0.0) return;
  float t = -b - sqrt(disc); vec3 p = o + rd * t;
  if (p.y < ycut) { t = -b + sqrt(disc); p = o + rd * t; if (p.y < ycut) return; }
  if (t < 0.0 || t > T) return; T = t; N = p / r; SC = vec3(p.y / r, id, 2.0);
}
void hitCyl(vec3 ro, vec3 rd, vec2 c, float r, float hgt, float id){
  vec2 o = ro.xz - c; float a = dot(rd.xz, rd.xz), b = 2.0 * dot(o, rd.xz), cc = dot(o, o) - r * r, disc = b * b - 4.0 * a * cc; if (disc < 0.0) return;
  float t = (-b - sqrt(disc)) / (2.0 * a); float y = ro.y + rd.y * t;
  if (t < 0.0 || t > T || y < 0.0 || y > hgt) return; T = t; vec2 pp = o + rd.xz * t; N = normalize(vec3(pp.x, 0.0, pp.y)); SC = vec3(y / hgt, id, 3.0);
}

// where the props stand: a few in a near ring (they sink away when the camera comes round in front of the creature) and a far ring
const int NP = 14;
bool isNear(float i){ return i < 9.5; }
vec3 propAt(float i){
  bool nr = isNear(i);
  float a = nr ? i * 0.6283 + 0.3 * sin(i * 7.3) : (i - 10.0) * 1.571 + 0.7;
  float r = nr ? 3.1 + 1.9 * fract(i * 0.618 + 0.2) : 10.0 + 3.0 * fract(i * 0.618 + 0.3);
  vec2 c = vec2(sin(a), cos(a)) * r;
  // the ring props on the camera's side of the creature sink into the ground (they would hide it)
  vec2 cam = normalize(uCam.xz - uTarget); vec2 dir = normalize(c - uTarget);
  float sink = nr ? smoothstep(0.1, 0.45, dot(cam, dir)) : 0.0;
  return vec3(c, 1.0 - sink);
}
float propSize(float i){ return (0.75 + 0.55 * h11(i + 3.0)) * (isNear(i) ? 1.0 : 2.4); }

void props(vec3 ro, vec3 rd){
  for (int ii = 0; ii < NP; ii++) {
    float i = float(ii); vec3 pa = propAt(i); vec2 c = pa.xy; float s = pa.z; if (s < 0.02) continue;
    float sz = propSize(i) * s * (E == 1 ? 1.35 : 1.0);
    if (E == 0) {          // obsidian spires, some leaning in pairs
      hitCone(ro, rd, c, 0.28 * sz, 1.3 * sz, 7.0, i);
      if (h11(i) > 0.5) hitCone(ro, rd, c + vec2(0.35, 0.2) * sz, 0.18 * sz, 0.75 * sz, 6.0, i + 0.5);
    } else if (E == 1) {   // tombstones (rounded) and crosses
      float ang = (h11(i + 1.0) - 0.5) * 0.9 + atan(c.x, c.y);
      if (h11(i + 2.0) > 0.35) { float w = 0.2 * sz, hh = 0.32 * sz, th = 0.06 * sz;
        hitBox(ro, rd, c, ang, vec3(w, hh * 0.5, th), i); hitHCyl(ro, rd, c, ang, hh, w, th, i);
        hitBox(ro, rd, c, ang, vec3(w * 1.25, 0.025 * sz, th * 2.2), i + 0.5); }
      else { hitBox(ro, rd, c, ang, vec3(0.035 * sz, 0.32 * sz, 0.035 * sz), i); hitBox(ro, rd, c + vec2(0.0, 0.0), ang, vec3(0.15 * sz, 0.032 * sz, 0.034 * sz) + vec3(0.0, 0.0, 0.0), i);
        // (the cross arm sits high: shift it up by testing a box whose centre is raised)
        hitBox(ro - vec3(0.0, 0.42 * sz, 0.0), rd, c, ang, vec3(0.15 * sz, 0.032 * sz, 0.034 * sz), i); }
    } else if (E == 2) {   // crystal clusters: faceted, glowing from inside
      hitCone(ro, rd, c, 0.22 * sz, 1.4 * sz, 6.0, i);
      hitCone(ro, rd, c + vec2(0.24, -0.1) * sz, 0.13 * sz, 0.8 * sz, 6.0, i + 0.3);
      hitCone(ro, rd, c + vec2(-0.2, 0.15) * sz, 0.1 * sz, 0.55 * sz, 6.0, i + 0.6);
    } else if (E == 4) {   // toadstools and lollipops
      if (h11(i + 5.0) > 0.4) { hitCyl(ro, rd, c, 0.07 * sz, 0.55 * sz, i); hitSphere(ro, rd, vec3(c.x, 0.5 * sz, c.y), 0.32 * sz, 0.02 * sz, i); }
      else { hitCyl(ro, rd, c, 0.025 * sz, 0.8 * sz, i); hitSphere(ro, rd, vec3(c.x, 0.95 * sz, c.y), 0.2 * sz, -1.0, i + 0.5); }
    } else {               // ice shards
      hitCone(ro, rd, c, 0.24 * sz, 1.5 * sz, 5.0, i);
      hitCone(ro, rd, c + vec2(-0.28, 0.12) * sz, 0.15 * sz, 0.9 * sz, 5.0, i + 0.4);
    }
  }
}

vec3 shadeProp(vec3 ro, vec3 rd, vec3 p, float pulse){
  vec3 n = N; float k = max(dot(n, KEY), 0.0); float fr = pow(1.0 - max(dot(n, -rd), 0.0), 3.0);
  float y = SC.x; float id = SC.y;
  vec3 c;
  if (E == 0) {
    float vein = smoothstep(0.035, 0.0, crack(vec2(atan(n.z, n.x) * 2.0 + id, y * 5.0)));
    c = vec3(0.025, 0.022, 0.03) * (AMB * 2.0 + KEYC * k) + vec3(0.9, 0.95, 1.0) * pow(max(dot(reflect(rd, n), KEY), 0.0), 30.0) * 0.35;
    c += vec3(1.0, 0.3, 0.05) * (smoothstep(0.35, 0.0, y) * 0.35 + vein * smoothstep(0.9, 0.2, y) * 0.9) * pulse;
  } else if (E == 1) {
    float moss = smoothstep(0.55, 0.8, fbm3(vec2(SC.z * 3.0 + id * 7.0, y * 4.0)));
    vec3 stone = mix(vec3(0.4, 0.38, 0.45), vec3(0.18, 0.22, 0.16), moss * smoothstep(0.6, 0.0, y)) * (0.7 + 0.4 * noise(vec2(SC.z, y) * 30.0));
    c = stone * (AMB * 2.2 + KEYC * (0.25 + k)) + vec3(0.65, 0.55, 1.0) * fr * 0.4;
    c *= 1.0 - 0.5 * smoothstep(0.12, 0.0, y);
  } else if (E == 2) {
    vec3 base = mix(vec3(0.35, 0.12, 0.85), vec3(0.15, 0.6, 0.95), h11(floor(id) + 1.0) * 0.6);
    c = base * (0.25 + 0.6 * k + 0.8 * y) * (0.85 + 0.25 * sin(uTime * 1.3 + id * 3.0)) + vec3(0.7, 0.95, 1.0) * (fr * 0.9 + pow(max(dot(reflect(rd, n), KEY), 0.0), 40.0));
  } else if (E == 4) {
    if (SC.z > 2.5) c = vec3(0.98, 0.92, 0.85) * (AMB * 1.6 + KEYC * k);   // stems and sticks
    else { vec3 capC = fract(id) > 0.25 ? vec3(1.0, 0.45, 0.62) : (h11(floor(id)) > 0.5 ? vec3(0.62, 0.5, 1.0) : vec3(1.0, 0.5, 0.4));
      vec3 pp = p; float dots = smoothstep(0.35, 0.25, length(fract(vec2(atan(n.z, n.x) * 2.0, n.y * 3.0)) - 0.5)) * step(0.1, n.y);
      if (fract(id) > 0.25) capC = mix(vec3(1.0, 0.5, 0.65), vec3(0.6, 0.9, 1.0), step(0.5, fract(atan(n.z, n.x) * 1.27 + n.y * 2.0)));
      c = mix(capC, vec3(1.0, 0.98, 0.94), dots * 0.85) * (AMB * 1.4 + KEYC * k) + vec3(1.0) * pow(max(dot(reflect(rd, n), KEY), 0.0), 24.0) * 0.4; }
    c += vec3(1.0, 0.8, 0.9) * fr * 0.2;
  } else {
    c = mix(vec3(0.25, 0.45, 0.62), vec3(0.65, 0.85, 1.0), k) * (0.45 + 0.6 * y) + vec3(0.9, 1.0, 1.0) * (fr * 0.7 + pow(max(dot(reflect(rd, n), KEY), 0.0), 50.0) * 0.8);
    c += vec3(0.3, 1.0, 0.8) * smoothstep(0.3, 0.0, y) * 0.12;
  }
  return c;
}

void main(){
  vec2 q = (gl_FragCoord.xy - 0.5 * uRes) / (0.5 * min(uRes.x, uRes.y));
  PX = uTanF * 2.0 / min(uRes.x, uRes.y);
  float cy = cos(uYaw), sy = sin(uYaw);
  vec3 fw0 = vec3(-sy, 0.0, -cy), rt = vec3(cy, 0.0, -sy);
  float cp = cos(uPitch), sp = sin(uPitch);
  vec3 fw = fw0 * cp - vec3(0.0, sp, 0.0), up = fw0 * sp + vec3(0.0, cp, 0.0);
  vec3 rd = normalize(fw + (q.x * rt + q.y * up) * uTanF), ro = uCam;
  float pulse = 0.85 + 0.15 * sin(uTime * 0.9) + 0.25 * uBass;
  E = int(uEnv + 0.5);
  if (E == 0)      { KEY = normalize(vec3(-0.75, 0.35, -0.55)); KEYC = vec3(1.1, 0.45, 0.2);  AMB = vec3(0.12, 0.06, 0.06); HAZE = vec3(0.12, 0.03, 0.015); }
  else if (E == 1) { KEY = normalize(vec3(-0.62, 0.35, -0.76)); KEYC = vec3(1.0, 0.95, 1.2);   AMB = vec3(0.13, 0.11, 0.2);  HAZE = vec3(0.08, 0.06, 0.13); }
  else if (E == 2) { KEY = normalize(vec3(0.3, 0.9, 0.2));      KEYC = vec3(0.6, 0.5, 1.0);   AMB = vec3(0.06, 0.05, 0.12); HAZE = vec3(0.025, 0.02, 0.06); }
  else if (E == 4) { KEY = normalize(vec3(0.5, 0.25, -0.85));   KEYC = vec3(1.2, 0.85, 0.7);  AMB = vec3(0.3, 0.25, 0.38);  HAZE = vec3(0.5, 0.36, 0.5); }
  else             { KEY = normalize(vec3(0.3, 0.45, -1.0));    KEYC = vec3(0.7, 0.85, 1.1);  AMB = vec3(0.12, 0.17, 0.25); HAZE = vec3(0.06, 0.1, 0.15); }

  vec3 col = sky(rd);
  float tg = rd.y < 0.0 ? -ro.y / rd.y : 1e9;
  // ---------------- ground
  if (rd.y < 0.0) {
    float t = tg; vec3 p = ro + rd * t; float r = length(p.xz - uTarget);
    float fade = smoothstep(14.0, 2.0, t);
    // bump: height from noise, normal from its slope (detail fades with distance so it never shimmers)
    float e = 0.04;
    float hs = 0.18 + 0.1 * fade;
    float h0 = fbm3(p.xz * 2.2), hx = fbm3((p.xz + vec2(e, 0.0)) * 2.2), hz = fbm3((p.xz + vec2(0.0, e)) * 2.2);
    vec3 n = normalize(vec3(-(hx - h0) / e * hs, 1.0, -(hz - h0) / e * hs));
    float g = fbm(p.xz * 0.9), det = (noise(p.xz * 30.0) - 0.5) * fade;
    float k = max(dot(n, KEY), 0.0);
    vec3 alb; vec3 emit = vec3(0.0); float spec = 0.0;
    if (E == 0) {
      alb = mix(vec3(0.05, 0.04, 0.042), vec3(0.11, 0.085, 0.075), g) * (1.0 + det * 0.8);
      float c1 = crack(p.xz * 1.3 + 3.0), c2 = crack(p.xz * 3.4 + 11.0), w = 0.012 + 0.02 * smoothstep(1.0, 12.0, t), heat = smoothstep(0.5, 0.85, fbm3(p.xz * 0.35 + 7.0));
      float lava = smoothstep(w * 3.0, 0.0, c1) * (0.6 + 0.8 * heat) + smoothstep(w * 1.6, 0.0, c2) * 0.45 * heat;
      float flow = 0.75 + 0.25 * sin(uTime * 1.4 + fbm3(p.xz * 2.0) * 8.0);
      emit = mix(vec3(0.9, 0.12, 0.02), vec3(1.0, 0.65, 0.18), smoothstep(0.0, w * 0.8, w * 1.2 - c1)) * flow * pulse * clamp(lava, 0.0, 1.0) * 1.2;
      emit += vec3(0.7, 0.16, 0.03) * smoothstep(0.3, 0.0, c1) * 0.16 * (0.5 + heat);
      alb *= 1.0 - clamp(lava, 0.0, 1.0); spec = 0.25;
    } else if (E == 1) {
      alb = mix(vec3(0.1, 0.09, 0.12), vec3(0.2, 0.17, 0.21), g) * (1.0 + det * 0.6);
      float grass = smoothstep(0.45, 0.75, fbm3(p.xz * 1.3 + 5.0));
      alb = mix(alb, vec3(0.06, 0.09, 0.06), grass * 0.7);
      emit = vec3(1.0, 0.62, 0.3) * exp(-r * r * 0.4) * (0.12 + 0.06 * uBass);   // lantern light around the creature
    } else if (E == 2) {
      alb = mix(vec3(0.03, 0.025, 0.055), vec3(0.07, 0.055, 0.12), g) * (1.0 + det * 0.6);
      float pool = smoothstep(0.66, 0.69, fbm3(p.xz * 0.6 + 4.0));
      emit += vec3(0.03, 0.22, 0.3) * pool * (0.7 + 0.3 * sin(uTime * 1.2 + p.x * 3.0)) + vec3(0.2, 0.7, 0.9) * pool * pow(noise(p.xz * 6.0 + uTime * 0.3), 6.0);
      emit += vec3(0.45, 0.15, 0.95) * smoothstep(0.025, 0.0, crack(p.xz * 1.4)) * 0.35 * fade;
      alb *= 1.0 - pool * 0.8; spec = pool;
    } else if (E == 4) {
      n = normalize(mix(vec3(0.0, 1.0, 0.0), n, 0.12));     // soft, even grass (no dark blotches)
      k = 0.55 + 0.45 * max(dot(n, KEY), 0.0);
      alb = mix(vec3(0.4, 0.66, 0.46), vec3(0.6, 0.8, 0.55), g) * (1.0 + det * 0.15);
      vec2 fc = floor(p.xz * 8.0); float fh = h21(fc);
      if (fh > 0.8) { float fd = length(fract(p.xz * 8.0) - 0.5 - (h22(fc + 3.0) - 0.5) * 0.4);
        vec3 fcol = fh > 0.95 ? vec3(1.0, 0.95, 0.7) : fh > 0.88 ? vec3(1.0, 0.6, 0.75) : vec3(0.85, 0.7, 1.0);
        alb = mix(alb, fcol, smoothstep(0.14, 0.08, fd) * fade); }
      float path = smoothstep(0.08, 0.03, abs(fbm3(p.xz * 0.25) - 0.5)); alb = mix(alb, vec3(0.95, 0.8, 0.72), path * 0.8);
    } else {
      float c1 = crack(p.xz * 1.6 + 1.0);
      alb = mix(vec3(0.07, 0.12, 0.18), vec3(0.16, 0.24, 0.32), g);
      n = normalize(mix(vec3(0.0, 1.0, 0.0), n, 0.25));       // ice is nearly flat: a mirror
      vec2 gl = p.xz * 0.35 + vec2(sin(uTime * 0.11), cos(uTime * 0.09)) * 2.0;
      emit = vec3(0.3, 1.0, 0.85) * pow(smoothstep(0.55, 0.9, fbm3(gl)), 2.0) * 0.45 + vec3(0.4, 0.95, 1.0) * smoothstep(0.02, 0.0, c1) * 0.3 * fade;
      spec = 1.0;
    }
    vec3 gc = alb * (AMB + KEYC * k) + emit;
    // reflections: lava glass, crystal pools and the frozen lake mirror the sky
    if (spec > 0.0) { vec3 rr = reflect(rd, n); float fres = 0.04 + 0.96 * pow(1.0 - max(dot(-rd, n), 0.0), 5.0);
      gc += sky(rr) * fres * spec * (E == 3 ? 0.9 : 0.35); }
    // contact shadows of the props, cast away from the key light
    for (int ii = 0; ii < NP; ii++) { float i = float(ii); vec3 pa = propAt(i); if (pa.z < 0.02) continue;
      float sz = propSize(i) * pa.z;
      vec2 sc = pa.xy - normalize(KEY.xz) * sz * 0.35; float d = length(p.xz - sc) / (sz * 0.45);
      gc *= 1.0 - 0.55 * exp(-d * d) * (E == 2 || E == 3 ? 0.6 : 1.0); }
    // soft shadow under the creature
    gc *= 1.0 - 0.35 * exp(-r * r * 6.0);
    if (E == 1) gc += vec3(0.16, 0.13, 0.24) * smoothstep(0.5, 0.8, fbm3(p.xz * 0.4 + uTime * 0.05)) * 0.6;   // ground fog
    if (uFire.w > 0.0) { float fd = length(p - uFire.xyz); gc += vec3(1.0, 0.45, 0.1) * uFire.w * 1.6 / (0.3 + fd * fd * 3.0); }
    float fog = 1.0 - exp(-t * (E == 4 ? 0.022 : 0.05));
    col = mix(gc, HAZE * (1.0 + 0.6 * smoothstep(30.0, 6.0, t)), fog);
  }
  // ---------------- props
  T = tg;
  props(ro, rd);
  if (T < tg - 1e-4) { vec3 p = ro + rd * T; float fog = 1.0 - exp(-T * (E == 4 ? 0.022 : 0.05)); col = mix(shadeProp(ro, rd, p, pulse), HAZE * 1.3, fog); }
  // ---------------- air: low mist and drifting particles
  float az = atan(rd.x, rd.z), el = asin(clamp(rd.y, -1.0, 1.0));
  float mist = fbm3(vec2(az * 3.0 + uTime * 0.03, el * 12.0 - uTime * 0.04)) * smoothstep(0.1, -0.08, el) * smoothstep(-0.45, -0.03, el);
  col += (E == 0 ? vec3(0.25, 0.08, 0.04) : E == 1 ? vec3(0.2, 0.15, 0.3) : E == 2 ? vec3(0.12, 0.1, 0.32) : E == 3 ? vec3(0.3, 0.38, 0.46) : vec3(0.5, 0.32, 0.42)) * mist * 0.4;
  for (int i = 0; i < 18; i++) { float fi = float(i); float life = fract(uTime * (0.07 + 0.05 * fract(fi * 0.37)) + fi * 0.173);
    float yy = E == 3 ? 2.8 * (1.0 - life) : E == 0 ? life * 2.6 : 0.25 + 0.7 * fract(fi * 0.71) + 0.15 * sin(uTime * 0.8 + fi);
    float sw = E == 3 ? 0.25 * sin(uTime * 0.9 + fi) : 0.0;
    vec3 fp = vec3(uTarget.x + sin(fi * 2.1 + (E == 0 ? 0.0 : uTime * 0.12)) * (0.8 + fi * 0.28) + 0.3 * sin(uTime * 0.7 + fi) + sw, yy, uTarget.y + cos(fi * 1.3 + (E == 0 ? 0.0 : uTime * 0.1)) * (0.8 + fi * 0.25));
    vec3 v = fp - ro; float tt = dot(v, rd); if (tt < 0.0 || tt > T) continue; float dd = length(v - rd * tt) / tt / PX;
    float sz = E == 3 ? 1.3 : 1.8;
    vec3 pc = E == 0 ? vec3(1.0, 0.45, 0.1) : E == 1 ? vec3(0.8, 1.0, 0.45) : E == 2 ? vec3(0.3, 0.9, 1.0) : E == 3 ? vec3(0.9, 0.95, 1.0) : vec3(1.0, 0.85, 0.6);
    float fl = E == 1 ? smoothstep(0.2, 0.8, sin(uTime * 1.7 + fi * 3.0)) : 0.6 + 0.4 * sin(uTime * (E == 3 ? 1.0 : 9.0) + fi * 3.0);
    col += pc * (exp(-dd * dd / (sz * sz)) + 0.15 * exp(-dd / (sz * 4.0))) * (E == 0 ? 1.0 - life : 0.9) * fl; }
  // filmic curve, a gentle vignette and dither
  col = col * 1.15;
  col = (col * (2.51 * col + 0.03)) / (col * (2.43 * col + 0.59) + 0.14);
  col *= 1.0 - 0.18 * dot(q * vec2(0.9, 0.45), q * vec2(0.9, 0.45));
  col += (h21(gl_FragCoord.xy + fract(uTime)) - 0.5) / 255.0;
  gl_FragColor = vec4(col, 1.0); }
