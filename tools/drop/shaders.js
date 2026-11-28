// Shared with DropScene.kt (same GLSL ES 1.0).
const DROP_FIELD_VS = `attribute vec4 aPos; uniform vec2 uHalf; varying vec2 vQ;
void main(){ vQ = aPos.zw; gl_Position = vec4(aPos.xy / uHalf, 0.0, 1.0); }`;
// each bead: a compact smooth bump (1 - d^2)^3; beads that touch add up and melt together
const DROP_FIELD_FS = `precision mediump float; varying vec2 vQ;
void main(){ float d = dot(vQ, vQ); float f = d < 1.0 ? (1.0 - d) * (1.0 - d) * (1.0 - d) : 0.0; gl_FragColor = vec4(f * 0.25, 0.0, 0.0, 1.0); }`;
const DROP_VS = `attribute vec2 aPos; varying vec2 vUv; void main(){ vUv = aPos * 0.5 + 0.5; gl_Position = vec4(aPos, 0.0, 1.0); }`;
const DROP_FS = `precision highp float;
varying vec2 vUv;
uniform sampler2D uField; uniform vec2 uTexel; uniform vec2 uHalf; uniform vec2 uRes;
uniform vec3 uBody;           // centre x, y, base radius
uniform float uRad[16];       // outline radii
uniform float uLiq; uniform float uFaceKind; uniform float uTime;
uniform vec4 uEye;            // look x, y, open, big
uniform vec4 uExpr;           // happy, squish, smile, mouth O
uniform vec2 uPct;            // percent, alpha
uniform float uFloor; uniform float uBass;
uniform vec4 uIcon;          // battery icon: centre x, y, size, alpha
uniform vec2 uSlosh;
uniform vec3 uGroove;        // mids, highs, ring age (s)         // its liquid tilt and wave
float h21(vec2 p){ p = fract(p * vec2(123.34, 456.21)); p += dot(p, p + 45.32); return fract(p.x * p.y); }
// value noise from a small tiling random texture (bilinear): smooth on every GPU, also where the shader math runs
// at reduced precision (computed hashes there turned lava and honey into squares)
uniform sampler2D uNoise;
float noise(vec2 p){ return texture2D(uNoise, (p + 0.5) / 64.0).r; }
float fbm(vec2 p){ float n = 0.0, a = 0.5; for (int i = 0; i < 4; i++) { n += a * noise(p); p = p * 2.03 + 1.7; a *= 0.5; } return n; }
float bodyR(float a){
  float s = 0.0, w = 0.0;
  for (int i = 0; i < 16; i++) { float d = a - float(i) * 0.3926991; d = d - 6.2831853 * floor(d / 6.2831853 + 0.5); float k = exp(-d * d * 9.0); s += uRad[i] * k; w += k; }
  return s / w;
}
float smin(float a, float b, float k){ float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0); return mix(b, a, h) - k * h * (1.0 - h); }
// a seven-segment digit (0..9) in a 1 x 2 box centred at 0
float seg(vec2 p, vec2 a, vec2 b){ vec2 pa = p - a, ba = b - a; float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0); return length(pa - ba * h); }
float digit(vec2 p, float n){
  float d = 9.0; float s0, s1, s2, s3, s4, s5, s6;
  // segments: a top, b top-right, c bottom-right, d bottom, e bottom-left, f top-left, g middle
  s0 = (n == 1.0 || n == 4.0) ? 0.0 : 1.0; s1 = (n == 5.0 || n == 6.0) ? 0.0 : 1.0; s2 = (n == 2.0) ? 0.0 : 1.0;
  s3 = (n == 1.0 || n == 4.0 || n == 7.0) ? 0.0 : 1.0; s4 = (n == 0.0 || n == 2.0 || n == 6.0 || n == 8.0) ? 1.0 : 0.0;
  s5 = (n == 1.0 || n == 2.0 || n == 3.0 || n == 7.0) ? 0.0 : 1.0; s6 = (n == 0.0 || n == 1.0 || n == 7.0) ? 0.0 : 1.0;
  if (s0 > 0.5) d = min(d, seg(p, vec2(-0.4, 0.9), vec2(0.4, 0.9)));
  if (s1 > 0.5) d = min(d, seg(p, vec2(0.5, 0.8), vec2(0.5, 0.1)));
  if (s2 > 0.5) d = min(d, seg(p, vec2(0.5, -0.1), vec2(0.5, -0.8)));
  if (s3 > 0.5) d = min(d, seg(p, vec2(-0.4, -0.9), vec2(0.4, -0.9)));
  if (s4 > 0.5) d = min(d, seg(p, vec2(-0.5, -0.1), vec2(-0.5, -0.8)));
  if (s5 > 0.5) d = min(d, seg(p, vec2(-0.5, 0.8), vec2(-0.5, 0.1)));
  if (s6 > 0.5) d = min(d, seg(p, vec2(-0.4, 0.0), vec2(0.4, 0.0)));
  return d;
}
vec4 liquid(vec2 p, vec2 uv, float mirror){
  float px = 2.0 * uHalf.y / uRes.y;                          // one screen pixel in uv units
  float F = texture2D(uField, uv).r * 4.0;
  vec2 c = uBody.xy; float R = uBody.z; vec2 rel = p - c; float rl = length(rel);
  // the floor shadow under the body
  float shadow = 0.0;
  { float hgt = max(0.0, c.y - R * 0.82 - uFloor); vec2 q = (p - vec2(c.x, uFloor)) / vec2(R * (1.1 + hgt * 1.5), R * 0.16);
    shadow = exp(-dot(q, q) * 2.0) * 0.45 / (1.0 + hgt * 6.0) * step(p.y, uFloor + R * 0.1); }
  if (F < 0.02 && rl > R * 2.0) return vec4(0.0, 0.0, 0.0, shadow * 0.85 * (1.0 - mirror));
  // distance to the liquid: the body outline, melted together with the beads
  float dB = rl > R * 2.2 ? rl - R : rl - bodyR(atan(rel.y, rel.x));
  float fx = (texture2D(uField, uv + vec2(uTexel.x, 0.0)).r - texture2D(uField, uv - vec2(uTexel.x, 0.0)).r) * 4.0;
  float fy = (texture2D(uField, uv + vec2(0.0, uTexel.y)).r - texture2D(uField, uv - vec2(0.0, uTexel.y)).r) * 4.0;
  vec2 gF = vec2(fx, fy) / (2.0 * uTexel * 2.0 * uHalf);      // field gradient per uv unit
  float dF = (0.5 - F) / max(length(gF), 1e-3);
  float k = R * 0.35;
  float h = clamp(0.5 + 0.5 * (dF - dB) / k, 0.0, 1.0);
  float D = mix(dF, dB, h) - k * h * (1.0 - h);
  vec2 g = normalize(mix(-gF, rel / max(rl, 1e-4), h) + 1e-5);
  float a = clamp(0.5 - D / px, 0.0, 1.0);
  int L = int(uLiq + 0.5);
  float glow = 0.0;
  if (L == 2 || L == 5) glow = exp(-max(D, 0.0) / (R * 0.18)) * (L == 2 ? 0.55 + 0.35 * uBass : 0.35);
  if (a <= 0.0 && glow < 0.004) return vec4(0.0, 0.0, 0.0, shadow * 0.85 * (1.0 - mirror));
  // a dome: steep at the rim, flat on top
  float thick = mix(clamp(-dF / 0.05, 0.0, 1.0), clamp(-D / (R * 0.75), 0.0, 1.0), h);
  float s = sqrt(1.0 - (1.0 - thick) * (1.0 - thick));
  vec3 N = normalize(vec3(g * (1.0 - s) * 1.6, 0.35 + s));
  vec3 Ld = normalize(vec3(-0.45, 0.65, 0.6)); vec3 V = vec3(0.0, 0.0, 1.0);
  float diff = max(dot(N, Ld), 0.0);
  float spec = pow(max(dot(reflect(-Ld, N), V), 0.0), 70.0);
  float spec2 = pow(max(dot(reflect(-normalize(vec3(0.5, -0.3, 0.8)), N), V), 0.0), 30.0) * 0.25;
  float fres = pow(1.0 - N.z, 2.5);
  vec2 lq = rel / R;                                             // body space
  vec3 col; float alpha;
  if (L == 0) {          // water: clear, blue at the rim, bright glints, a caustic on the floor side
    col = mix(vec3(0.16, 0.56, 1.0), vec3(0.7, 0.95, 1.0), fres) * (0.75 + 0.35 * fres) + vec3(0.5, 0.85, 1.0) * pow(max(0.0, 1.0 - length(lq - vec2(0.12, -0.5)) * 1.9), 2.0) * 0.5 * h;
    col += vec3(0.2, 0.55, 1.0) * (1.0 - s) * 0.25;
    alpha = 0.62 + 0.35 * fres;
  } else if (L == 1) {   // honey: deep amber, glowing inside
    col = mix(vec3(0.75, 0.35, 0.02), vec3(1.0, 0.72, 0.2), s * 0.8 + 0.2 * diff) + vec3(1.0, 0.85, 0.4) * pow(s, 3.0) * 0.25;
    col *= 0.85 + 0.15 * noise(lq * 6.0 + uTime * 0.2); alpha = 0.92;
  } else if (L == 2) {   // lava: a dark crust that cracks over glowing rock
    float n = fbm(lq * 3.2 + vec2(uTime * 0.15, -uTime * 0.1)); float crust = smoothstep(0.42, 0.62, n);
    vec3 hot = mix(vec3(1.0, 0.25, 0.02), vec3(1.0, 0.75, 0.2), smoothstep(0.2, 0.0, abs(n - 0.4)));
    col = mix(hot * (1.2 + 0.4 * uBass), vec3(0.09, 0.03, 0.02) * (0.6 + diff), crust * s);
    alpha = 1.0;
  } else if (L == 3) {   // slime: green goo with bubbles floating up inside
    col = mix(vec3(0.15, 0.55, 0.08), vec3(0.55, 1.0, 0.35), s * 0.6 + diff * 0.4);
    for (int i = 0; i < 6; i++) { float fi = float(i); vec2 bp = vec2(sin(fi * 2.4) * 0.45, mod(uTime * (0.15 + 0.05 * fi) + fi * 0.37, 1.6) - 0.8);
      float br = 0.06 + 0.04 * fract(fi * 0.7); float bd = length(lq - bp); col += vec3(0.8, 1.0, 0.6) * smoothstep(br, br - 0.02, bd) * smoothstep(br - 0.05, br - 0.02, bd) * 0.6 * h; }
    alpha = 0.82 + 0.15 * fres;
  } else if (L == 4) {   // mercury: liquid chrome mirroring a soft studio sky
    vec3 r = reflect(-V, N); float sky = r.y * 0.5 + 0.5;
    col = mix(vec3(0.08, 0.09, 0.11), vec3(0.95, 0.97, 1.0), smoothstep(0.35, 0.75, sky)) + vec3(0.25, 0.3, 0.4) * smoothstep(0.75, 0.3, abs(r.x));
    col *= 0.7 + 0.3 * diff; alpha = 1.0;
  } else {               // galaxy: a little universe inside the drop
    vec2 sp = lq * 9.0 + vec2(uTime * 0.05, 0.0); vec2 si = floor(sp);
    float st = h21(si); float star = st > 0.9 ? smoothstep(0.12, 0.0, length(fract(sp) - 0.5 - (vec2(h21(si + 3.0), h21(si + 7.0)) - 0.5) * 0.6)) * (0.5 + 0.5 * sin(uTime * 3.0 + st * 40.0)) : 0.0;
    float neb = fbm(lq * 2.0 + uTime * 0.03);
    col = vec3(0.04, 0.01, 0.1) + vec3(0.55, 0.15, 0.85) * smoothstep(0.4, 0.8, neb) * 0.6 + vec3(0.1, 0.6, 0.9) * smoothstep(0.55, 0.85, fbm(lq * 3.0 - 4.0)) * 0.35 + vec3(1.0) * star;
    col += vec3(0.6, 0.4, 1.0) * fres * 0.6; alpha = 0.95;
  }
  col += vec3(1.0) * (spec * (L == 4 ? 0.6 : 1.1) + spec2);
  // music: the bass makes it glow from inside, the highs sparkle on its skin
  col *= 1.0 + uBass * 0.3 * s;
  col += vec3(1.0, 0.97, 0.9) * smoothstep(0.82, 0.95, noise(lq * 13.0 + vec2(uTime * 2.3, -uTime * 1.7))) * uGroove.y * 1.4 * (0.3 + fres) * h;
  // reflection of the room: a bright window up and to the left, a soft horizon band; stronger at the rim
  { vec3 rv = reflect(-V, N);
    float win = smoothstep(0.1, 0.0, abs(rv.x + 0.35) - 0.16) * smoothstep(0.1, 0.0, abs(rv.y - 0.45) - 0.12);
    float bar = smoothstep(0.02, 0.0, abs(rv.x + 0.35)) * step(abs(rv.y - 0.45), 0.12);
    float hor = smoothstep(0.25, 0.0, abs(rv.y + 0.05)) * 0.35;
    float refl = (win * (1.0 - bar) * 0.55 + hor * 0.25) * (0.35 + 0.65 * fres) * h;
    col += (L == 2 ? vec3(1.0, 0.8, 0.6) : vec3(0.9, 0.95, 1.0)) * refl * (L == 4 ? 1.4 : 1.0); }
  // the face (body only)
  if (uFaceKind < 2.5 && h > 0.5) {
    float big = uEye.w; vec2 look = uEye.xy * 0.08;
    vec2 fq = lq - vec2(0.0, 0.08) - look * 0.6;
    float er = 0.15 * big; float ink = 0.0; float white = 0.0;
    for (int i = 0; i < 2; i++) {
      float sx = i == 0 ? -1.0 : 1.0; vec2 e = fq - vec2(sx * 0.33, 0.12);
      if (uExpr.x > 0.5) {          // happy: ^ ^
        float d = abs(length(e - vec2(0.0, -0.06)) - 0.11) ; ink = max(ink, smoothstep(0.035, 0.02, d) * step(-0.03, e.y));
      } else if (uExpr.y > 0.5) {   // squished: > <
        vec2 m = vec2(e.x * sx, e.y); float d = min(seg(m, vec2(-0.1, 0.08), vec2(0.08, 0.0)), seg(m, vec2(-0.1, -0.08), vec2(0.08, 0.0)));
        ink = max(ink, smoothstep(0.035, 0.02, d));
      } else {
        float open = max(0.05, uEye.z);
        vec2 ee = e / vec2(1.0, open);
        float w = smoothstep(er, er - 0.02, length(ee));
        white = max(white, w);
        vec2 pp = e - look * 0.5 - uEye.xy * 0.05; float pr = er * 0.62;
        float pup = smoothstep(pr, pr - 0.02, length(pp / vec2(1.0, open))) * w;
        ink = max(ink, pup);
        float hl = smoothstep(0.045, 0.025, length(pp - vec2(-0.04, 0.045))) * pup;
        white = max(white, hl); ink *= 1.0 - hl;
      }
    }
    // mouth
    vec2 mq = fq - vec2(0.0, -0.13);
    float md;
    if (uExpr.w > 0.5) md = abs(length(mq / vec2(1.0, 1.25)) - 0.055) - 0.012;      // "O"
    else { float cv = uExpr.z; float y = cv * (mq.x * mq.x * 6.0 - 0.04); md = max(abs(mq.y - y) - 0.016, abs(mq.x) - 0.11); }
    ink = max(ink, smoothstep(0.012, 0.0, md));
    // blush
    float bl = (smoothstep(0.1, 0.0, length(fq - vec2(-0.5, -0.04))) + smoothstep(0.1, 0.0, length(fq - vec2(0.5, -0.04)))) * (uFaceKind < 0.5 ? 0.35 : 0.0);
    vec3 inkC = L == 4 ? vec3(0.02) : L == 2 ? vec3(0.08, 0.02, 0.0) : vec3(0.02, 0.04, 0.1);
    if (uFaceKind > 0.5 && uFaceKind < 1.5) {   // cool: sunglasses instead of eyes
      vec2 g1 = fq - vec2(-0.3, 0.12), g2 = fq - vec2(0.3, 0.12);
      float gl = min(length(g1 / vec2(1.25, 0.85)) - 0.17, length(g2 / vec2(1.25, 0.85)) - 0.17);
      gl = min(gl, seg(fq, vec2(-0.12, 0.16), vec2(0.12, 0.16)) - 0.02);
      float gm = smoothstep(0.012, 0.0, gl); white *= 0.0; ink = max(ink * step(0.0, gl), gm);
      col = mix(col, vec3(0.9), smoothstep(0.03, 0.0, length(g1 - vec2(-0.06, 0.06))) * 0.0);
      col += vec3(1.0) * smoothstep(0.02, 0.0, abs(g1.x + g1.y * 0.6 + 0.04)) * gm * 0.25;
    }
    col = mix(col, vec3(1.0, 0.98, 0.95), white * 0.95); alpha = max(alpha, white);
    col = mix(col, vec3(1.0, 0.45, 0.55), bl * a);
    col = mix(col, inkC, ink); alpha = max(alpha, ink);
  }
  // the battery percentage, shown when it starts charging
  if (uPct.y > 0.01 && h > 0.5) {
    vec2 q = (lq - vec2(0.0, -0.42)) / 0.13; float n = uPct.x;
    float d1 = digit((q - vec2(-1.3, 0.0)) * vec2(1.0, 1.0), floor(n / 10.0));
    float d2 = digit(q - vec2(0.0, 0.0), mod(n, 10.0));
    float dd = n >= 100.0 ? min(min(digit(q - vec2(-2.6, 0.0), 1.0), digit(q - vec2(-1.3, 0.0), 0.0)), digit(q, 0.0)) : n < 10.0 ? d2 : min(d1, d2);
    float pc = min(min(length(q - vec2(1.0, 0.6)) - 0.15, length(q - vec2(1.8, -0.6)) - 0.15), seg(q, vec2(1.0, -0.8), vec2(1.8, 0.8)) - 0.05);
    float gl2 = smoothstep(0.16, 0.06, min(dd, pc + 0.1));
    col = mix(col, vec3(0.5, 1.0, 0.75), gl2 * uPct.y); alpha = max(alpha, gl2 * uPct.y);
  }
  vec3 gc = L == 2 ? vec3(1.0, 0.35, 0.05) : vec3(0.55, 0.3, 1.0);
  float A = alpha * a;
  vec3 outc = col * A + gc * glow * (1.0 - a);
  return vec4(outc, max(A, shadow * 0.85 * (1.0 - a) * (1.0 - mirror)));
}
// the battery as a little glass of the same liquid, the level = the charge, with the percentage beside it
vec4 batteryIcon(vec2 p){
  if (uIcon.w < 0.01) return vec4(0.0);
  vec2 q = (p - uIcon.xy) / uIcon.z;
  if (abs(q.x - 0.4) > 2.4 || abs(q.y) > 0.8) return vec4(0.0);
  float px = 2.0 * uHalf.y / uRes.y / uIcon.z;
  vec2 b = abs(q - vec2(-0.75, 0.0)) - vec2(0.48, 0.25);
  float box = length(max(b, 0.0)) + min(max(b.x, b.y), 0.0) - 0.07;
  vec2 cb = abs(q - vec2(-0.17, 0.0)) - vec2(0.04, 0.1); float cap = length(max(cb, 0.0)) + min(max(cb.x, cb.y), 0.0) - 0.03;
  float shell = min(abs(box) - 0.035, cap);
  float inside = smoothstep(px, -px, box + 0.06);
  float lvl = uPct.x / 100.0;
  float x01 = (q.x + 1.23) / 0.96;
  float wave = 0.025 * sin(x01 * 9.0 + uTime * 4.0) * (0.4 + uSlosh.y) + uSlosh.x * (x01 - 0.5) * 0.25;
  float fillY = -0.27 + lvl * 0.54 + wave;
  float fill = inside * smoothstep(px, -px, q.y - fillY);
  int L = int(uLiq + 0.5);
  vec3 lc = L == 0 ? vec3(0.25, 0.68, 1.0) : L == 1 ? vec3(1.0, 0.68, 0.15) : L == 2 ? mix(vec3(1.0, 0.3, 0.03), vec3(1.0, 0.75, 0.2), noise(q * 6.0 + uTime)) :
            L == 3 ? vec3(0.45, 1.0, 0.3) : L == 4 ? mix(vec3(0.35, 0.37, 0.42), vec3(0.95), smoothstep(-0.25, 0.2, q.y)) : vec3(0.45, 0.18, 0.85);
  if (L == 5) lc += vec3(1.0) * step(0.93, h21(floor(q * 18.0))) * 0.8;
  lc *= 0.75 + 0.35 * smoothstep(fillY - 0.2, fillY, q.y);
  vec4 c = vec4(lc, 1.0) * fill * (lvl < 0.15 ? 0.7 + 0.3 * sin(uTime * 6.0) : 1.0);
  float sh = smoothstep(px, -px, shell);
  c = vec4(0.92, 0.95, 1.0, 1.0) * sh * 0.85 + c * (1.0 - sh * 0.85);
  c += vec4(1.0) * smoothstep(0.03, 0.0, abs(q.y - 0.16)) * step(abs(q.x + 0.85), 0.25) * inside * 0.35;
  vec2 dq = (q - vec2(0.55, 0.0)) / 0.22; float n = uPct.x;
  float dd = n >= 100.0 ? min(min(digit(dq - vec2(-1.3, 0.0), 1.0), digit(dq, 0.0)), digit(dq - vec2(1.3, 0.0), 0.0))
           : n < 10.0 ? digit(dq + vec2(0.65, 0.0), mod(n, 10.0)) : min(digit(dq, floor(n / 10.0)), digit(dq - vec2(1.3, 0.0), mod(n, 10.0)));
  vec2 pq = dq - vec2(n >= 100.0 ? 2.9 : n < 10.0 ? 1.05 : 2.7, 0.0);
  float pc = min(min(length(pq - vec2(-0.3, 0.55)) - 0.16, length(pq - vec2(0.3, -0.55)) - 0.16), seg(pq, vec2(-0.35, -0.8), vec2(0.35, 0.8)) - 0.02);
  dd = min(dd, pc + 0.12);
  float num = smoothstep(0.17 + px / 0.22, 0.17 - px / 0.22, dd);
  c = vec4(mix(lc, vec3(1.0), 0.45), 1.0) * num + c * (1.0 - num);
  return c * uIcon.w;
}

void main(){
  vec2 p = (vUv - 0.5) * 2.0 * uHalf;                         // uv units, y up
  vec4 c = liquid(p, vUv, 0.0);
  // a glossy floor: under the floor line the drop is mirrored, faint and fading with depth (one more pass of the
  // same function, only for the pixels below the floor that the drop does not cover)
  if (p.y < uFloor && c.a < 0.99) {
    vec2 m = vec2(p.x, 2.0 * uFloor - p.y);
    vec4 r = liquid(m, m / (2.0 * uHalf) + 0.5, 1.0);
    float k = 0.32 * exp(-(uFloor - p.y) / (uBody.z * 0.9));
    c += r * k * (1.0 - c.a);
  }
  // on each beat a ring of light spreads over the floor from under it
  if (uGroove.z < 1.2) {
    float u = uGroove.z / 1.2; vec2 rq = (p - vec2(uBody.x, uFloor)) / vec2(1.0, 0.18);
    float rr = uBody.z * (0.9 + u * 3.0); float ring = exp(-pow((length(rq) - rr) / (uBody.z * 0.12), 2.0)) * (1.0 - u) * (1.0 - u);
    int L2 = int(uLiq + 0.5);
    vec3 rc = L2 == 0 ? vec3(0.4, 0.8, 1.0) : L2 == 1 ? vec3(1.0, 0.75, 0.3) : L2 == 2 ? vec3(1.0, 0.4, 0.1) : L2 == 3 ? vec3(0.5, 1.0, 0.4) : L2 == 4 ? vec3(0.9, 0.95, 1.0) : vec3(0.7, 0.4, 1.0);
    c.rgb += rc * ring * 0.6 * (1.0 - c.a);
  }
  vec4 ic = batteryIcon(p);
  c = ic + c * (1.0 - ic.a);
  gl_FragColor = c;
}`;
