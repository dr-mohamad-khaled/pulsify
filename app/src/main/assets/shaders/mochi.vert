// Quad that covers only Mochi and its props. aPos is 0..1 on both axes.
attribute vec2 aPos;
uniform vec4 uQuad;   // min.xy, max.xy in aspect-correct uv space
uniform vec2 uRes;
varying vec2 vUv;
void main() {
    vUv = mix(uQuad.xy, uQuad.zw, aPos);
    float mn = min(uRes.x, uRes.y);
    gl_Position = vec4(vUv * 2.0 * mn / uRes, 0.0, 1.0);
}
