// Screen-space quad for the 3D kitty's sprite, shadow, effects and charging cable.
attribute vec2 aPos;           // 0..1
uniform vec4 uRect;            // clip-space x0, y0, x1, y1
uniform float uRot;
uniform float uAspect;         // viewport width / height
varying vec2 vUv;
void main() {
    vUv = aPos;
    vec2 c = 0.5 * (uRect.xy + uRect.zw);
    vec2 q = (aPos * 2.0 - 1.0) * 0.5 * (uRect.zw - uRect.xy);
    q.x *= uAspect;
    float cs = cos(uRot), sn = sin(uRot);
    q = vec2(cs * q.x - sn * q.y, sn * q.x + cs * q.y);
    q.x /= uAspect;
    gl_Position = vec4(c + q, 0.0, 1.0);
}
