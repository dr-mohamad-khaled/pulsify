precision highp float;
uniform vec2 uRes; uniform float uYaw; uniform float uTime; uniform vec3 uCam; uniform float uTanF; uniform float uPitch; uniform float uBass; uniform vec2 uTarget;
uniform float uEnv; uniform vec4 uFire;   // fire: xyz world position, w = radius (0 = none): lights the ground
float h21(vec2 p){ p = fract(p * vec2(123.34, 456.21)); p += dot(p, p + 45.32); return fract(p.x * p.y); }
vec2 h22(vec2 p){ return vec2(h21(p), h21(p + 17.17)); }
float noise(vec2 p){ vec2 i = floor(p), f = fract(p); f = f * f * (3.0 - 2.0 * f);
  return mix(mix(h21(i), h21(i + vec2(1.0, 0.0)), f.x), mix(h21(i + vec2(0.0, 1.0)), h21(i + vec2(1.0, 1.0)), f.x), f.y); }
float fbm(vec2 p){ float n = 0.0, a = 0.5; for (int i = 0; i < 4; i++) { n += a * noise(p); p = p * 2.07 + 3.1; a *= 0.5; } return n; }
float fbm6(vec2 p){ float n = 0.0, a = 0.5; for (int i = 0; i < 6; i++) { n += a * noise(p); p = p * 2.13 + 1.7; a *= 0.5; } return n; }
float crack(vec2 p){ vec2 i = floor(p), f = fract(p); float d1 = 8.0, d2 = 8.0;
  for (int y = -1; y <= 1; y++) for (int x = -1; x <= 1; x++) { vec2 g = vec2(float(x), float(y)); vec2 o = h22(i + g); float d = length(g + o - f);
    if (d < d1) { d2 = d1; d1 = d; } else if (d < d2) d2 = d; }
  return d2 - d1; }
void main(){
  vec2 q = (gl_FragCoord.xy - 0.5 * uRes) / (0.5 * min(uRes.x, uRes.y));
  float cy = cos(uYaw), sy = sin(uYaw);
  vec3 fw0 = vec3(-sy, 0.0, -cy), rt = vec3(cy, 0.0, -sy);
  float cp = cos(uPitch), sp = sin(uPitch);
  vec3 fw = fw0 * cp - vec3(0.0, sp, 0.0), up = fw0 * sp + vec3(0.0, cp, 0.0);
  vec3 rd = normalize(fw + (q.x * rt + q.y * up) * uTanF), ro = uCam;
  float az = atan(rd.x, rd.z), el = asin(clamp(rd.y, -1.0, 1.0));
  float pulse = 0.85 + 0.15 * sin(uTime * 0.9) + 0.25 * uBass;
  int E = int(uEnv + 0.5);
  vec3 HAZE = E == 0 ? vec3(0.10, 0.025, 0.012) : E == 1 ? vec3(0.05, 0.028, 0.075) : E == 2 ? vec3(0.02, 0.015, 0.05) : E == 3 ? vec3(0.05, 0.09, 0.14) : vec3(0.16, 0.08, 0.16);
  vec3 col;
  // ---------------- sky / far surroundings
  vec2 sg = vec2(az * 60.0, el * 60.0); vec2 si = floor(sg); float st = h21(si); float star = 0.0;
  if (st > 0.94) star = smoothstep(0.15, 0.0, length(sg - si - h22(si))) * (0.6 + 0.4 * sin(uTime * (1.0 + 3.0 * h21(si + 5.0)) + st * 50.0));
  if (E == 0) {          // volcano
    col = mix(vec3(0.30, 0.07, 0.025), vec3(0.012, 0.006, 0.01), smoothstep(-0.02, 0.45, el));
    float cl = fbm6(vec2(az * 2.5 + uTime * 0.01, el * 6.0 - uTime * 0.004));
    col += vec3(0.35, 0.09, 0.03) * smoothstep(0.45, 0.85, cl) * smoothstep(0.55, 0.05, el) * 0.8 + vec3(1.0, 0.9, 0.8) * star * smoothstep(0.15, 0.4, el) * 0.6;
    float da = atan(sin(az + 2.3), cos(az + 2.3));
    float cone = 0.22 - abs(da) * 0.42 + 0.01 * noise(vec2(az * 40.0, 1.0)), ridge = 0.04 + 0.05 * fbm(vec2(az * 3.0, 4.0)) + 0.015 * noise(vec2(az * 30.0, 2.0));
    if (el < max(cone, ridge)) { vec3 rc = vec3(0.025, 0.012, 0.012);
      if (cone > ridge) { float lava = smoothstep(0.03, 0.0, abs(da + 0.05 * sin(el * 40.0)) - 0.004) * smoothstep(0.0, 0.2, el);
        rc += vec3(1.0, 0.3, 0.04) * lava * pulse * 0.9 + vec3(1.0, 0.35, 0.05) * smoothstep(0.03, 0.0, cone - el) * smoothstep(0.08, 0.0, abs(da)) * 1.3; }
      col = rc; }
    col += vec3(1.0, 0.32, 0.06) * exp(-abs(da) * 9.0) * exp(-max(0.0, el - 0.2) * 9.0) * smoothstep(0.18, 0.24, el) * 0.5 * pulse;
  } else if (E == 1) {   // moonlit graveyard
    col = mix(vec3(0.075, 0.035, 0.11), vec3(0.004, 0.003, 0.012), smoothstep(-0.02, 0.55, el)) + vec3(0.85, 0.85, 1.0) * star * smoothstep(0.02, 0.2, el) * 0.8;
    vec3 md3 = normalize(vec3(-0.62, 0.2, -0.76)); float md = acos(clamp(dot(rd, md3), -1.0, 1.0));
    col += vec3(1.0, 0.86, 0.62) * smoothstep(0.046, 0.041, md) * (0.85 + 0.15 * fbm(vec2(az, el) * 40.0)) + vec3(0.55, 0.35, 0.8) * exp(-md * 9.0) * 0.22;
    float hill = 0.035 + 0.05 * fbm(vec2(az * 2.2, 1.0)) + 0.02 * sin(az * 3.0);
    float trees = 0.018 + 0.12 * pow(fbm(vec2(az * 9.0, 7.0)), 3.0) + 0.012 * step(0.82, noise(vec2(az * 40.0, 2.0))) * (1.0 + 2.0 * noise(vec2(az * 400.0, el * 80.0)));
    if (el < hill) col = mix(vec3(0.03, 0.018, 0.05), col, 0.15);
    if (el < trees) col = vec3(0.012, 0.008, 0.02);
  } else if (E == 2) {   // crystal cavern: no sky, a cave dome full of glowing veins
    float w = fbm6(vec2(az * 3.0, el * 5.0)); col = vec3(0.015, 0.012, 0.03) * (0.6 + w);
    float v = crack(vec2(az * 4.0, el * 6.0) + 2.0); col += vec3(0.5, 0.2, 1.0) * smoothstep(0.05, 0.0, v) * (0.35 + 0.35 * sin(uTime * 0.7 + az * 3.0)) * smoothstep(-0.05, 0.3, el);
    col += vec3(0.2, 0.9, 1.0) * star * 0.5 * smoothstep(0.1, 0.5, el);
    float stal = 0.5 - 0.3 * pow(noise(vec2(az * 22.0, 3.0)), 2.0); if (el > stal) col = vec3(0.01, 0.008, 0.02);   // stalactites
    float shards = 0.02 + 0.09 * pow(abs(sin(az * 7.0 + noise(vec2(az * 5.0, 1.0)) * 3.0)), 6.0);
    if (el < shards) col = vec3(0.12, 0.05, 0.3) * (0.6 + 0.6 * el / shards) + vec3(0.3, 0.8, 1.0) * smoothstep(0.004, 0.0, shards - el) * 0.6;
  } else if (E == 4) {   // candy meadow at dusk: pastel sky, puffy clouds, soft hills
    col = mix(vec3(0.95, 0.55, 0.55), vec3(0.42, 0.25, 0.55), smoothstep(-0.02, 0.25, el)); col = mix(col, vec3(0.07, 0.05, 0.16), smoothstep(0.2, 0.75, el));
    col += vec3(1.0, 0.95, 0.9) * star * smoothstep(0.35, 0.6, el) * 0.7;
    vec3 sd = normalize(vec3(0.5, 0.05, -0.85)); float sdd = acos(clamp(dot(rd, sd), -1.0, 1.0));
    col += vec3(1.0, 0.75, 0.55) * (exp(-sdd * 6.0) * 0.45 + smoothstep(0.06, 0.05, sdd) * 0.6);
    float cl = fbm6(vec2(az * 2.0 + uTime * 0.006, el * 7.0)); float band = smoothstep(0.04, 0.12, el) * smoothstep(0.42, 0.18, el);
    col = mix(col, mix(vec3(1.0, 0.72, 0.78), vec3(1.0, 0.92, 0.88), smoothstep(0.55, 0.8, cl)), smoothstep(0.5, 0.68, cl) * band * 0.85);
    float hill = 0.03 + 0.045 * fbm(vec2(az * 1.8, 3.0)) + 0.02 * sin(az * 2.0 + 1.0);
    float hill2 = 0.015 + 0.03 * fbm(vec2(az * 3.4, 8.0));
    if (el < hill) col = mix(vec3(0.42, 0.3, 0.55), vec3(0.6, 0.42, 0.62), smoothstep(hill - 0.03, hill, el));
    if (el < hill2) col = vec3(0.3, 0.42, 0.42) * (0.8 + 0.3 * noise(vec2(az * 60.0, 1.0)));
  } else {               // frozen soul lake under an aurora
    col = mix(vec3(0.05, 0.1, 0.16), vec3(0.003, 0.006, 0.02), smoothstep(-0.02, 0.6, el)) + vec3(0.9, 0.95, 1.0) * star * smoothstep(0.05, 0.3, el);
    float ab = sin(az * 2.0 + fbm(vec2(az * 3.0, uTime * 0.05)) * 4.0 + uTime * 0.1) * 0.06 + 0.3;
    float aur = exp(-pow((el - ab) * 9.0, 2.0)) * (0.5 + 0.5 * fbm(vec2(az * 14.0, uTime * 0.2)));
    col += mix(vec3(0.1, 1.0, 0.6), vec3(0.6, 0.2, 1.0), smoothstep(0.25, 0.4, el)) * aur * 0.45;
    float mnt = 0.05 + 0.1 * pow(fbm(vec2(az * 2.6, 9.0)), 1.6) + 0.02 * noise(vec2(az * 30.0, 1.0));
    if (el < mnt) { float snow = smoothstep(mnt - 0.03, mnt, el); col = mix(vec3(0.03, 0.05, 0.08), vec3(0.45, 0.55, 0.7), snow * 0.7); }
  }
  // ---------------- ground
  if (rd.y < 0.0) {
    float t = -ro.y / rd.y; vec3 p = ro + rd * t; float r = length(p.xz - uTarget);
    float g = fbm6(p.xz * 1.1), det = (noise(p.xz * 40.0) - 0.5) * smoothstep(8.0, 1.5, t) + (noise(p.xz * 120.0) - 0.5) * 0.6 * smoothstep(4.0, 1.0, t);
    vec3 gc;
    if (E == 0) {
      gc = mix(vec3(0.035, 0.028, 0.03), vec3(0.075, 0.058, 0.055), g) * (1.0 + det * 0.7);
      float c1 = crack(p.xz * 0.9 + 3.0), c2 = crack(p.xz * 2.6 + 11.0), w = 0.012 + 0.02 * smoothstep(1.0, 12.0, t), heat = smoothstep(0.55, 0.85, fbm(p.xz * 0.35 + 7.0));
      float lava = smoothstep(w * 3.0, 0.0, c1) * (0.6 + 0.8 * heat) + smoothstep(w * 1.6, 0.0, c2) * 0.45 * heat;
      float flow = 0.75 + 0.25 * sin(uTime * 1.4 + fbm(p.xz * 2.0) * 8.0);
      gc = mix(gc, mix(vec3(0.9, 0.12, 0.02), vec3(1.0, 0.62, 0.15), smoothstep(0.0, w * 0.8, w * 1.2 - c1)) * flow * pulse, clamp(lava, 0.0, 1.0));
      gc += vec3(0.7, 0.16, 0.03) * smoothstep(0.25, 0.0, c1) * 0.12 * (0.5 + heat);
    } else if (E == 1) {
      gc = mix(vec3(0.045, 0.04, 0.06), vec3(0.085, 0.07, 0.1), g) * (1.0 + det * 0.55);
      gc += vec3(1.0, 0.62, 0.3) * exp(-r * r * 0.35) * (0.22 + 0.08 * uBass);
      vec2 gcell = floor(p.xz * 6.0); if (h21(gcell) > 0.86) gc += vec3(0.07, 0.08, 0.1) * smoothstep(0.18, 0.0, length(fract(p.xz * 6.0) - 0.5)) * smoothstep(7.0, 2.0, t);
    } else if (E == 2) {
      gc = mix(vec3(0.02, 0.018, 0.04), vec3(0.05, 0.04, 0.09), g) * (1.0 + det * 0.6);
      float pool = smoothstep(0.62, 0.66, fbm(p.xz * 0.5 + 4.0));
      gc = mix(gc, vec3(0.05, 0.35, 0.45) * (0.7 + 0.3 * sin(uTime * 1.2 + p.x * 3.0)) + vec3(0.2, 0.7, 0.9) * pow(noise(p.xz * 6.0 + uTime * 0.3), 6.0), pool);
      gc += vec3(0.4, 0.15, 0.9) * smoothstep(0.03, 0.0, crack(p.xz * 1.4)) * 0.25;
      gc += vec3(0.5, 0.3, 1.0) * exp(-r * r * 0.4) * 0.08;
    } else if (E == 4) {
      gc = mix(vec3(0.22, 0.42, 0.33), vec3(0.42, 0.62, 0.42), g) * (1.0 + det * 0.5);
      vec2 fc = floor(p.xz * 9.0); float fh = h21(fc);
      if (fh > 0.82) { float fd = length(fract(p.xz * 9.0) - 0.5 - (h22(fc + 3.0) - 0.5) * 0.4);
        vec3 fcol = fh > 0.95 ? vec3(1.0, 0.95, 0.7) : fh > 0.89 ? vec3(1.0, 0.6, 0.75) : vec3(0.85, 0.7, 1.0);
        gc = mix(gc, fcol, smoothstep(0.14, 0.08, fd) * smoothstep(6.0, 1.5, t)); }
      gc += vec3(1.0, 0.7, 0.75) * exp(-r * r * 0.5) * 0.12;
      float path = smoothstep(0.35, 0.3, abs(fbm(p.xz * 0.3) - 0.5)); gc = mix(gc, vec3(0.75, 0.6, 0.55), path * 0.0);
    } else {
      float c1 = crack(p.xz * 0.8 + 1.0);
      gc = mix(vec3(0.12, 0.2, 0.28), vec3(0.3, 0.42, 0.55), g) * (1.0 + det * 0.3);
      gc += vec3(0.4, 0.95, 1.0) * smoothstep(0.02, 0.0, c1) * 0.35 * smoothstep(10.0, 1.0, t);
      vec2 gl = p.xz * 0.35 + vec2(sin(uTime * 0.11), cos(uTime * 0.09)) * 2.0;    // drifting soul lights under the ice
      gc += vec3(0.3, 1.0, 0.85) * pow(smoothstep(0.55, 0.9, fbm(gl)), 2.0) * 0.5;
      gc += vec3(0.6, 0.75, 1.0) * pow(max(0.0, dot(reflect(rd, vec3(0.0, 1.0, 0.0)), normalize(vec3(0.3, 0.5, -1.0)))), 30.0) * 0.4; // sheen
    }
    if (uFire.w > 0.0) { float fd = length(p - uFire.xyz); gc += vec3(1.0, 0.45, 0.1) * uFire.w * 1.6 / (0.3 + fd * fd * 3.0); }
    float fog = 1.0 - exp(-t * 0.07);
    col = mix(gc, mix(HAZE * 1.6, HAZE, smoothstep(8.0, 40.0, t)), fog);
  }
  // ---------------- standing pieces: obsidian slabs / tombstones / crystals / ice shards (real parallax)
  for (int i = 0; i < 9; i++) {
    float fi = float(i); float a = fi * 0.698 + 0.4 + 0.25 * sin(fi * 7.3); float rr = 2.6 + 1.6 * fract(fi * 0.618 + 0.3);
    vec3 c = vec3(sin(a) * rr, 0.0, cos(a) * rr) * 1.6;
    if (dot(c.xz - uTarget, vec2(sin(uYaw), cos(uYaw))) > 0.6 && length(c.xz - uTarget) < 4.5) continue;
    if (length(c.xz - ro.xz) < 3.2) continue;
    vec3 n = normalize(vec3(ro.x - c.x, 0.0, ro.z - c.z)); float dn = dot(rd, n); if (abs(dn) < 1e-3) continue;
    float t = dot(c - ro, n) / dn; if (t <= 0.0) continue; vec3 p = ro + rd * t;
    float ground_t = rd.y < 0.0 ? -ro.y / rd.y : 1e9; if (t > ground_t) continue;
    vec3 side = vec3(n.z, 0.0, -n.x); float lx = dot(p - c, side), ly = p.y;
    float w = 0.3 + 0.2 * fract(fi * 3.7), hgt = 0.45 + 0.6 * fract(fi * 5.1); bool hit; float edge; vec3 sc;
    if (E == 1) { float tw_ = 0.28 + 0.12 * fract(fi * 3.7), th = 0.5 + 0.35 * fract(fi * 5.1); float lx2 = lx + ly * (fract(fi * 9.1) - 0.5) * 0.25;
      float d = ly < th - tw_ ? max(abs(lx2) - tw_, ly - (th - tw_)) : length(vec2(lx2, ly - (th - tw_))) - tw_; hit = ly > 0.0 && d < 0.0; edge = smoothstep(-0.015, 0.0, d);
      sc = vec3(0.34, 0.31, 0.4) * (0.62 + 0.5 * fbm(vec2(lx, ly) * 14.0)) + vec3(0.6, 0.55, 0.85) * edge * 0.3; sc *= 1.0 - 0.5 * smoothstep(0.15, 0.0, ly); }
    else { float prof = E == 0 ? hgt * (1.0 - pow(abs(lx) / w, 1.4)) + 0.08 * (noise(vec2(lx * 9.0, fi)) - 0.5) : (hgt * 1.5) * (1.0 - abs(lx) / (w * 0.7));
      hit = ly > 0.0 && abs(lx) < w && ly < prof; edge = smoothstep(0.05, 0.0, prof - ly) + smoothstep(0.04, 0.0, w - abs(lx));
      if (E == 0) { sc = vec3(0.03, 0.025, 0.03) * (0.7 + 0.6 * fbm(vec2(lx, ly) * 12.0)) + vec3(1.0, 0.3, 0.06) * smoothstep(0.35, 0.0, ly) * 0.22 * pulse + vec3(0.8, 0.35, 0.2) * edge * 0.12; }
      else if (E == 4) { float stem = 0.06 + 0.03 * fract(fi * 2.3), capY = hgt * 0.75, capW = w * 1.2, capH = 0.22 + 0.1 * fract(fi * 4.7);
        bool inStem = abs(lx) < stem && ly < capY; float cd = (lx / capW) * (lx / capW) + ((ly - capY) / capH) * ((ly - capY) / capH); bool inCap = ly > capY - 0.03 && cd < 1.0;
        hit = ly > 0.0 && (inStem || inCap); edge = inCap ? smoothstep(0.8, 1.0, cd) : 0.0;
        vec3 capC = fract(fi * 0.37) > 0.5 ? vec3(1.0, 0.55, 0.65) : vec3(0.75, 0.6, 1.0);
        float dots = smoothstep(0.35, 0.25, length(fract(vec2(lx, ly) * 9.0) - 0.5));
        sc = inCap ? mix(capC * (0.7 + 0.4 * (ly - capY) / capH), vec3(1.0, 0.97, 0.92), dots * 0.8) + vec3(1.0, 0.9, 0.9) * edge * 0.25 : vec3(0.95, 0.88, 0.8) * (0.6 + 0.4 * ly / capY); }
      else if (E == 2) { float facet = step(0.0, lx); sc = mix(vec3(0.25, 0.08, 0.6), vec3(0.45, 0.2, 0.9), facet) * (0.5 + 0.7 * ly / (hgt * 1.5)) + vec3(0.6, 0.9, 1.0) * edge * 0.5; sc *= 0.8 + 0.3 * sin(uTime * 1.3 + fi); }
      else { float facet = step(0.0, lx); sc = mix(vec3(0.35, 0.55, 0.7), vec3(0.6, 0.8, 0.95), facet) * (0.55 + 0.4 * ly / (hgt * 1.5)) + vec3(0.9, 1.0, 1.0) * edge * 0.35; } }
    if (hit) { float fog = 1.0 - exp(-t * 0.07); col = mix(sc, HAZE, fog); }
  }
  // ---------------- air: smoke / mist and particles
  float mist = fbm(vec2(az * 3.0 + uTime * 0.03, el * 12.0 - uTime * 0.04)) * smoothstep(0.12, -0.1, el) * smoothstep(-0.5, -0.04, el);
  col += (E == 0 ? vec3(0.22, 0.08, 0.05) : E == 1 ? vec3(0.18, 0.13, 0.26) : E == 2 ? vec3(0.12, 0.1, 0.3) : E == 3 ? vec3(0.25, 0.32, 0.4) : vec3(0.4, 0.25, 0.35)) * mist * 0.35;
  for (int i = 0; i < 14; i++) { float fi = float(i); float life = fract(uTime * (0.08 + 0.05 * fract(fi * 0.37)) + fi * 0.173);
    float yy = E == 3 ? 2.6 * (1.0 - life) : E == 0 ? life * 2.4 : 0.3 + 0.6 * fract(fi * 0.71) + 0.15 * sin(uTime * 0.8 + fi);
    vec3 fp = vec3(uTarget.x + sin(fi * 2.1 + (E == 0 ? 0.0 : uTime * 0.15)) * (0.8 + fi * 0.25) + 0.3 * sin(uTime * 0.7 + fi), yy, uTarget.y + cos(fi * 1.3 + (E == 0 ? 0.0 : uTime * 0.12)) * (0.8 + fi * 0.22));
    vec3 v = fp - ro; float tt = dot(v, rd); if (tt < 0.0) continue; float dd = length(v - rd * tt) / tt * (E == 3 ? 420.0 : 300.0);
    vec3 pc = E == 0 ? vec3(1.0, 0.45, 0.1) : E == 1 ? vec3(1.0, 0.75, 0.35) : E == 2 ? vec3(0.3, 0.9, 1.0) : E == 3 ? vec3(0.9, 0.95, 1.0) : vec3(1.0, 0.85, 0.6);
    col += pc * exp(-dd * dd * 0.7) * (E == 0 ? 1.0 - life : 0.8) * (0.6 + 0.4 * sin(uTime * (E == 3 ? 1.0 : 9.0) + fi * 3.0)); }
  col += (h21(gl_FragCoord.xy + fract(uTime)) - 0.5) / 255.0;
  gl_FragColor = vec4(col, 1.0); }