const cv = document.getElementById('c'), ctx = cv.getContext('2d');
const TAU = Math.PI * 2, PI = Math.PI;
const clamp = (x, a, b) => x < a ? a : x > b ? b : x, mix = (a, b, t) => a + (b - a) * t;
const sm = x => { x = clamp(x, 0, 1); return x * x * (3 - 2 * x); };
function rgba(hex, a) { const n = parseInt(hex.slice(1), 16); return `rgba(${n >> 16 & 255},${n >> 8 & 255},${n & 255},${a})`; }
function lg(x0, y0, x1, y1, cols) { const g = ctx.createLinearGradient(x0, y0, x1, y1); cols.forEach((c, i) => g.addColorStop(i / (cols.length - 1), c)); return g; }
function rg(x0, y0, r0, x1, y1, r1, cols, stops) { const g = ctx.createRadialGradient(x0, y0, r0, x1, y1, r1); cols.forEach((c, i) => g.addColorStop(stops ? stops[i] : i / (cols.length - 1), c)); return g; }
const LAV = '#CDB4FF', AMB = '#FFB36B', VIO = '#9D4EDD', MINT = '#2DD4BF', PINK = '#FF6FA8', RED = '#FF5A5A';
let W = 600, H = 800, DPR = 2, U = 1, GY = 0, CX = 0;
cv.width = W * DPR; cv.height = H * DPR; U = Math.min(W * .5, H * .3); GY = H * .7; CX = W / 2;
const G = {};
function glyphFor() { return null; }
