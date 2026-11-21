// 3D kitty compositing and little effects. GLSL ES 1.00.
precision mediump float;
uniform sampler2D uTex;
uniform float uMode;           // 0 sprite, 1 shadow, 2 heart, 3 star, 4 z, 5 note, 6 bolt, 7 cable, 8 swirl, 9 sparkle, 10 bubble, 11 pellet
uniform vec4 uColor;           // rgb, alpha
uniform vec4 uCable[6];        // cable control points in quad uv (xy) — 12 points packed 2 per vec4
varying vec2 vUv;

float dot2(vec2 v) { return dot(v, v); }
float sdSeg(vec2 p, vec2 a, vec2 b) { vec2 pa = p - a, ba = b - a; float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0); return length(pa - ba * h); }

void main() {
    vec2 p = vUv * 2.0 - 1.0;
    float a = 0.0;
    vec3 col = uColor.rgb;
    if (uMode < 0.5) {
        gl_FragColor = texture2D(uTex, vUv) * uColor.a;
        return;
    } else if (uMode < 1.5) {
        a = exp(-dot(p, p) * 2.6) * 0.55;
        col = vec3(0.0);
    } else if (uMode < 2.5) {
        vec2 q = vec2(abs(p.x) * 0.62, (p.y + 0.95) * 0.56);
        float d;
        if (q.y + q.x > 1.0) d = length(q - vec2(0.25, 0.75)) - 0.35355;
        else d = sqrt(min(dot(q - vec2(0.0, 1.0), q - vec2(0.0, 1.0)), dot(q - 0.5 * max(q.x + q.y, 0.0), q - 0.5 * max(q.x + q.y, 0.0)))) * sign(q.x - q.y);
        a = smoothstep(0.03, -0.01, d);
        col = mix(col, vec3(1.0), smoothstep(0.22, 0.0, length(p - vec2(-0.35, 0.35))) * 0.55);
    } else if (uMode < 3.5) {
        float ang = atan(p.y, p.x);
        float r = length(p);
        float star = 0.55 + 0.3 * cos(ang * 5.0);
        a = smoothstep(star + 0.05, star - 0.05, r) * smoothstep(1.0, 0.85, r);
        col = mix(col, vec3(1.0), smoothstep(0.4, 0.0, r));
    } else if (uMode < 4.5) {
        // a chunky "z"
        float d = min(min(sdSeg(p, vec2(-0.5, 0.5), vec2(0.5, 0.5)), sdSeg(p, vec2(0.5, 0.5), vec2(-0.5, -0.5))), sdSeg(p, vec2(-0.5, -0.5), vec2(0.5, -0.5)));
        a = smoothstep(0.2, 0.12, d);
    } else if (uMode < 5.5) {
        // a music note
        float head = length((p - vec2(-0.25, -0.45)) * vec2(1.0, 1.35)) - 0.3;
        float stem = sdSeg(p, vec2(0.03, -0.45), vec2(0.03, 0.65)) - 0.08;
        float flag = sdSeg(p, vec2(0.03, 0.65), vec2(0.45, 0.35)) - 0.09;
        a = smoothstep(0.04, -0.02, min(min(head, stem), flag));
    } else if (uMode < 6.5) {
        // a lightning bolt
        vec2 q = p;
        float d = min(sdSeg(q, vec2(0.15, 0.85), vec2(-0.25, 0.0)), sdSeg(q, vec2(-0.25, 0.0), vec2(0.25, 0.05)));
        d = min(d, sdSeg(q, vec2(0.25, 0.05), vec2(-0.15, -0.85)));
        a = smoothstep(0.17, 0.1, d);
        col = mix(col, vec3(1.0, 1.0, 0.85), smoothstep(0.12, 0.0, d));
    } else if (uMode < 7.5) {
        // the charging cable: a chain of capsules through the control points
        float d = 1e5;
        vec2 prev = uCable[0].xy;
        for (int i = 0; i < 6; i++) {
            vec2 c0 = uCable[i].xy, c1 = uCable[i].zw;
            if (i > 0) d = min(d, sdSeg(vUv, prev, c0));
            d = min(d, sdSeg(vUv, c0, c1));
            prev = c1;
        }
        float w = uColor.a;                                    // thickness in uv
        a = smoothstep(w, w * 0.7, d);
        float core = smoothstep(w * 0.6, 0.0, d);
        col = mix(vec3(0.08, 0.08, 0.1), vec3(0.35, 0.35, 0.4), core * 0.6);
        // plug head at the last point
        vec2 tip = uCable[5].zw;
        vec2 pd = abs(vUv - tip) - vec2(w * 1.6, w * 2.4);
        float plug = length(max(pd, 0.0)) + min(max(pd.x, pd.y), 0.0) - w * 0.6;
        a = max(a, smoothstep(0.002, -0.002, plug));
        col = mix(col, vec3(0.92, 0.92, 0.95), smoothstep(0.002, -0.002, plug));
        gl_FragColor = vec4(col * a, a);
        return;
    } else if (uMode < 8.5) {
        // dizzy swirl
        float r = length(p), ang = atan(p.y, p.x);
        float s = fract((ang / 6.2832) + r * 1.6);
        a = smoothstep(0.12, 0.0, abs(s - 0.5) - 0.18) * smoothstep(1.0, 0.8, r) * smoothstep(0.0, 0.15, r);
    } else if (uMode < 9.5) {
        // four-point sparkle
        vec2 q = abs(p);
        float d = pow(q.x, 0.5) + pow(q.y, 0.5);
        a = smoothstep(1.0, 0.75, d);
        col = mix(col, vec3(1.0), smoothstep(0.7, 0.2, d));
    } else if (uMode < 10.5) {
        // soap-film bubble: bright rim, clear middle, a highlight
        float r = length(p);
        a = smoothstep(1.0, 0.9, r) * (0.18 + 0.82 * smoothstep(0.55, 0.95, r));
        float h = smoothstep(0.3, 0.0, length(p - vec2(-0.38, 0.4)));
        a = max(a, h * 0.9);
        col = mix(col, vec3(1.0), h + 0.3 * smoothstep(0.7, 1.0, r));
    } else {
        // food pellet: a soft round crumb
        float r = length(p);
        a = smoothstep(1.0, 0.75, r);
        col *= 0.75 + 0.35 * smoothstep(0.6, -0.4, dot(p, vec2(0.6, -0.6)));
    }
    a *= uColor.a;
    gl_FragColor = vec4(col * a, a);
}
