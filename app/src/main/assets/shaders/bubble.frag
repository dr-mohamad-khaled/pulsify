// Pulsify — Kiwi's speech bubble. GLSL ES 1.00. Shares mochi.vert. Text comes from a texture
// (white text on transparent, rendered on the CPU only when the words change).
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif

varying vec2 vUv;
uniform sampler2D uTex;
uniform vec4  uBox;    // centre x, y, half width, half height (uv)
uniform vec2  uTip;    // where the tail points (uv)
uniform float uPop;    // scale 0..1.1
uniform float uPx;     // one pixel in uv
uniform float uTexW;   // used width of the texture (0..1)
uniform vec3  uRim;
uniform float uAlpha;

float sdRoundBox(vec2 p, vec2 b, float r) { vec2 q = abs(p) - b + r; return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r; }
float sdSeg(vec2 p, vec2 a, vec2 b) { vec2 pa = p - a, ba = b - a; float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0); return length(pa - ba * h); }
float smin(float a, float b, float k) { float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0); return mix(b, a, h) - k * h * (1.0 - h); }

void main() {
    // Scale around the tail tip so the bubble grows out of Kiwi's beak.
    vec2 q = uTip + (vUv - uTip) / max(uPop, 0.01);
    vec2 c = uBox.xy;
    vec2 hb = uBox.zw;
    float r = hb.y * 0.95;
    float box = sdRoundBox(q - c, hb, r);
    // Tail: a tapered wedge from the bubble's bottom toward the tip.
    vec2 base = vec2(clamp(uTip.x, c.x - hb.x + r, c.x + hb.x - r), c.y - hb.y + 0.01);
    vec2 tip = mix(base, uTip, 0.75);
    vec2 pa = q - base, ba = tip - base;
    float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
    float tail = length(pa - ba * h) - mix(hb.y * 0.35, 0.002, h);
    float d = smin(box, tail, hb.y * 0.2);
    float px = uPx * 1.2 / max(uPop, 0.05);
    float a = clamp(0.5 - d / px, 0.0, 1.0);
    vec4 col = vec4(0.0);
    // Soft glow behind.
    float g = exp(-max(d, 0.0) / (hb.y * 0.35)) * 0.35;
    col = vec4(uRim * g, g) * 0.6;
    vec3 fillC = mix(vec3(0.99, 0.98, 1.0), vec3(0.93, 0.9, 1.0), smoothstep(c.y + hb.y, c.y - hb.y, q.y));
    col = col * (1.0 - a) + vec4(fillC, 1.0) * a;
    // Rim line.
    float line = clamp(0.5 - (abs(d + px * 1.2) - px * 0.9) / px, 0.0, 1.0);
    col.rgb = mix(col.rgb, uRim * 0.75, line * a * 0.8);
    // Text.
    vec2 t = (q - (c - vec2(hb.x - hb.y * 0.5, hb.y))) / vec2(2.0 * (hb.x - hb.y * 0.5), 2.0 * hb.y);
    if (t.x > 0.0 && t.x < 1.0 && t.y > 0.0 && t.y < 1.0) {
        float ta = texture2D(uTex, vec2(t.x * uTexW, 1.0 - t.y)).a;
        col.rgb = mix(col.rgb, vec3(0.1, 0.07, 0.16), ta * a);
    }
    gl_FragColor = col * uAlpha;
}
