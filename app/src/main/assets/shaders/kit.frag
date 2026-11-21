// Pulsify 3D pets: soft three-tone toon shading, baked occlusion, glossy eyes. GLSL ES 1.00.
precision mediump float;

uniform sampler2D uMask;       // r: tabby stripes, g: cream patches, b: ambient occlusion
uniform vec3 uFur, uStripe, uCream, uPoint, uIris, uInk;
uniform vec3 uLight;           // direction to the key light (model view space)
uniform float uDim;            // low battery / sleepy dimming
uniform float uGlow;           // a warm flush (petting, happy)

varying vec3 vN;
varying vec2 vUv;
varying vec4 vInfo;            // role, siamese point, outline pass, siamese coat

void main() {
    float uPoints = vInfo.w;
    if (vInfo.z > 0.5) { gl_FragColor = vec4(mix(uInk, uFur * 0.3, 0.55) * (1.0 - 0.3 * uDim), 1.0); return; }
    float role = vInfo.x;
    vec3 n = normalize(vN);
    vec3 c;
    float ao = 1.0;
    float gloss = 0.0;
    bool lit = true;
    if (role < 0.5) {
        vec3 m = texture2D(uMask, vUv).rgb;
        c = mix(uFur, uStripe, smoothstep(0.38, 0.62, m.r));
        c = mix(c, uCream, smoothstep(0.4, 0.6, m.g));
        c = mix(c, uPoint, uPoints * vInfo.y * (1.0 - 0.75 * smoothstep(0.4, 0.6, m.g)));
        ao = m.b;
    } else if (role < 1.5) { c = vec3(1.0, 0.66, 0.72); ao = 0.8; }      // inner ear, tongue
    else if (role < 2.5) c = vec3(1.0, 0.56, 0.62);                       // blush
    else if (role < 3.5) { c = vec3(0.93, 0.47, 0.55); gloss = 0.5; }     // nose
    else if (role < 4.5) { c = vec3(0.09, 0.07, 0.12); gloss = 1.0; }     // eye, pupil
    else if (role < 5.5) {
        // iris: deep at the top, glowing at the bottom, a darker ring at the edge
        vec2 q = vUv * 2.0 - 1.0;
        float r = length(q);
        c = uIris * mix(0.5, 1.3, smoothstep(0.9, -0.7, q.y));
        c = mix(c, uIris * 0.35, smoothstep(0.72, 0.98, r));
        c += vec3(1.0) * 0.12 * smoothstep(0.5, 0.0, length(q - vec2(0.0, -0.55)));
        gloss = 1.0;
    }
    else if (role < 6.5) { c = vec3(1.0); lit = false; }                 // eye shine
    else if (role < 7.5) { c = mix(uInk, vec3(0.92, 0.84, 0.8), uPoints); lit = false; } // mouth
    else c = uPoint;                                                       // old siamese mask part
    if (lit) {
        float d = dot(n, uLight);
        // three tones with soft steps: shadow, mid, light
        float k1 = smoothstep(-0.25, -0.05, d);
        float k2 = smoothstep(0.35, 0.55, d);
        vec3 shadow = vec3(0.68, 0.58, 0.8);          // cool lilac shadow keeps colours rich
        vec3 shade = mix(shadow, vec3(0.93, 0.9, 0.95), k1);
        shade = mix(shade, vec3(1.06, 1.04, 1.0), k2);
        // ambient occlusion in the creases, tinted warm so it never goes grey
        shade *= mix(vec3(0.62, 0.5, 0.6), vec3(1.0), ao);
        // sky fill from above, a thin warm rim around the silhouette
        shade += 0.05 * max(n.y, 0.0);
        float rim = pow(1.0 - max(n.z, 0.0), 3.0) * smoothstep(-0.2, 0.4, n.y + 0.3);
        c = c * shade + rim * 0.16 * vec3(1.0, 0.93, 0.85);
        if (gloss > 0.0) {
            float spec = pow(max(dot(reflect(-uLight, n), vec3(0.0, 0.0, 1.0)), 0.0), 36.0);
            c += spec * 0.45 * gloss;
        }
    }
    c += uGlow * vec3(0.12, 0.04, 0.06);
    c *= 1.0 - 0.35 * uDim;
    gl_FragColor = vec4(c, 1.0);
}
