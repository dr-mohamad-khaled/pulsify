// Pulsify — a painted world from images: day and night pictures, blended by the real clock,
// with a warm grade at dawn and dusk. Written into the world cache; alpha 0 means "no glow".
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
uniform sampler2D uDay;
uniform sampler2D uNight;
uniform vec2  uRes;
uniform vec4  uMap;      // cover mapping: scale x, scale y, offset x, offset y (screen uv -> image uv)
uniform float uNightK;   // 0 day .. 1 night
uniform float uDusk;     // 0 .. 1 warm golden-hour grade
uniform float uHasNight;

void main() {
    vec2 uv = gl_FragCoord.xy / uRes;
    vec2 iv = uv * uMap.xy + uMap.zw;
    vec2 tc = vec2(iv.x, 1.0 - iv.y);
    vec3 day = texture2D(uDay, tc).rgb;
    vec3 night = uHasNight > 0.5 ? texture2D(uNight, tc).rgb : day * vec3(0.22, 0.28, 0.5);
    vec3 col = mix(day, night, uNightK);
    // Golden hour: warm the highlights, deepen the shadows a touch.
    float l = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(col, col * vec3(1.12, 0.9, 0.72) + vec3(0.05, 0.015, 0.0) * l, uDusk * 0.7);
    gl_FragColor = vec4(col, 0.0);
}
