precision highp float; varying vec2 vUv; uniform float uTime; uniform float uHeat; uniform float uOrb;
float h21(vec2 p){ p = fract(p * vec2(123.34, 456.21)); p += dot(p, p + 45.32); return fract(p.x * p.y); }
float noise(vec2 p){ vec2 i = floor(p), f = fract(p); f = f * f * (3.0 - 2.0 * f);
  return mix(mix(h21(i), h21(i + vec2(1.0, 0.0)), f.x), mix(h21(i + vec2(0.0, 1.0)), h21(i + vec2(1.0, 1.0)), f.x), f.y); }
float fbm(vec2 p){ float n = 0.0, a = 0.5; for (int i = 0; i < 5; i++) { n += a * noise(p); p = p * 2.1 + 1.3; a *= 0.5; } return n; }
void main(){
  vec2 p = vUv * 1.25;                            // -1.25..1.25, fire ball of radius ~0.5 with flames licking upwards
  float r = length(p * vec2(1.0, 0.9) + vec2(0.0, 0.08));
  float n = fbm(p * 2.6 + vec2(0.0, -uTime * 2.2)) + 0.5 * fbm(p * 5.0 + vec2(uTime * 0.7, -uTime * 3.5));
  float flame = clamp(1.0 - r * (1.55 - 0.65 * n) + max(0.0, p.y) * 0.35 * n, 0.0, 1.0);
  float core = smoothstep(0.35, 0.0, r);
  vec3 c = mix(vec3(0.9, 0.12, 0.02), vec3(1.0, 0.55, 0.08), smoothstep(0.1, 0.5, flame));
  c = mix(c, vec3(1.0, 0.95, 0.7), smoothstep(0.55, 0.95, flame) * (0.6 + 0.4 * uHeat));
  c += vec3(1.0, 0.9, 0.6) * core * 0.6;
  float a = smoothstep(0.0, 0.25, flame);
  float glow = exp(-r * r * 3.0) * 0.35;
  if (uOrb > 1.5) { c = mix(vec3(1.0, 0.45, 0.6), vec3(1.0, 0.75, 0.8), smoothstep(0.1, 0.6, flame)); c = mix(c, vec3(1.0, 0.95, 0.85), smoothstep(0.6, 0.95, flame)); c += vec3(1.0, 0.9, 0.9) * core * 0.5; }   // peachy: a warm pink candy glow
  else if (uOrb > 0.5) { c = mix(vec3(0.35, 0.04, 0.75), vec3(0.8, 0.35, 1.0), smoothstep(0.1, 0.5, flame)); c = mix(c, vec3(0.7, 1.0, 0.8), smoothstep(0.6, 0.95, flame) * 0.8); c += vec3(0.6, 0.9, 0.7) * core * 0.4; }   // mischief orb: violet flame, mint core
  float fade = smoothstep(1.0, 0.75, length(p));
  gl_FragColor = vec4(c * (a + glow * vec3(1.0, 0.5, 0.15)) * fade, 1.0); }