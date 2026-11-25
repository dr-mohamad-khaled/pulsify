function eyeOn(e, u, v, side, lx, ly, open, t, glyph) {
  const x = u - lx, y = v - ly;
  switch (e) {
    case 'happy': return Math.abs(v - ly * .4 - (1.1 - .36 * x * x)) < .85 && Math.abs(x) <= 3.2;
    case 'heart': { const X = u / 2.6, Y = (v + .4) / 2.6, a = X * X + Y * Y - 1; return a * a * a - X * X * Y * Y * Y <= 0; }
    case 'star': { const r = Math.hypot(u, v), a = Math.atan2(v, u) + PI / 2; return r <= 1.2 + 2 * Math.pow(Math.abs(Math.cos(a * 2.5)), 3); }
    case 'wink': if (side > 0) return Math.abs(v - (1.1 - .36 * u * u)) < .85 && Math.abs(u) <= 3.2;
    // falls through
    default: { if (open < .2) return Math.round(y) === 0 && Math.abs(x) <= 2.2; return (x / 2.25) ** 2 + (y / (3.05 * open)) ** 2 <= 1; }
  }
}
function ledEyes(cx, cy, c, gap, col, n = 2) {
  const on = new Path2D(), off = new Path2D(), r = c * .38, sides = n === 2 ? [-1, 1] : [0];
  const lx = clamp(s.look.x, -1, 1) * 1.2, ly = clamp(s.look.y, -1, 1) * 1.1, open = 1 - s.blink;
  for (const side of sides) {
    const ex = cx + side * gap, gl = glyphFor(s.expr, side);
    for (let v = -3; v <= 3; v++) for (let u = -3; u <= 3; u++) {
      const x = ex + u * c, y = cy + v * c, p = eyeOn(s.expr, u, v, side || 1, lx, ly, open, time, gl) ? on : off;
      p.moveTo(x + r, y); p.arc(x, y, r, 0, TAU);
    }
  }
  ctx.fillStyle = 'rgba(255,255,255,.045)'; ctx.fill(off);
  ctx.save(); ctx.shadowColor = col; ctx.shadowBlur = Math.max(4, c * U * 1.2) * DPR; ctx.fillStyle = col; ctx.fill(on); ctx.restore();
  ctx.fillStyle = 'rgba(255,255,255,.28)'; ctx.fill(on);
}
function mouthShape() { const e = s.expr; if (e === 'happy' || e === 'heart' || e === 'wink' || e === 'star') return 'smile'; return 'small'; }
function ledMouth(cx, cy, c, col) {
  const on = new Path2D(), off = new Path2D(), r = c * .38, sh = mouthShape();
  for (let v = -1; v <= 1; v++) for (let u = -3; u <= 3; u++) {
    let lit = false;
    switch (sh) {
      case 'smile': lit = v === Math.round(clamp(-1 + .2 * u * u, -1, 1)); break;
      default: lit = v === 0 && Math.abs(u) <= 1;
    }
    const x = cx + u * c, y = cy + v * c, p = lit ? on : off; p.moveTo(x + r, y); p.arc(x, y, r, 0, TAU);
  }
  ctx.fillStyle = 'rgba(255,255,255,.035)'; ctx.fill(off);
  ctx.save(); ctx.shadowColor = col; ctx.shadowBlur = Math.max(3, c * U) * DPR; ctx.fillStyle = col; ctx.fill(on); ctx.restore();
}
function circle(x, y, r) { ctx.beginPath(); ctx.arc(x, y, r, 0, TAU); }
function rrect(x, y, w, h, r) { ctx.beginPath(); ctx.roundRect(x - w / 2, y - h / 2, w, h, r); }
function rim(w) { ctx.lineWidth = .011; ctx.strokeStyle = lg(-w, 0, w, 0, [rgba(VIO, .9), rgba(VIO, .0), rgba(AMB, .0), rgba(AMB, .9)]); ctx.stroke(); }
function glow(x, y, r, col, a) { ctx.save(); ctx.globalCompositeOperation = 'lighter'; ctx.fillStyle = rg(x, y, 0, x, y, r, [rgba(col, a), rgba(col, a * .35), rgba(col, 0)], [0, .4, 1]); circle(x, y, r); ctx.fill(); ctx.restore(); }
function seg(x0, y0, x1, y1, w, col) { ctx.beginPath(); ctx.moveTo(x0, y0); ctx.lineTo(x1, y1); ctx.lineWidth = w; ctx.lineCap = 'round'; ctx.strokeStyle = col; ctx.stroke(); }
function eyeCol(base) { if (s.expr === 'heart') return PINK; return base; }
function arm2(sx, sy, side, a, b, l1, l2, w, col, hand) {
  const a1 = -PI / 2 + side * a, ex = sx + Math.cos(a1) * l1, ey = sy + Math.sin(a1) * l1;
  const a2 = a1 + side * b, hx = ex + Math.cos(a2) * l2, hy = ey + Math.sin(a2) * l2;
  seg(sx, sy, ex, ey, w, col); seg(ex, ey, hx, hy, w * .9, col);
  seg(sx, sy, ex, ey, w * .25, 'rgba(255,255,255,.08)');
  circle(ex, ey, w * .62); ctx.fillStyle = '#3A3150'; ctx.fill(); circle(ex, ey, w * .22); ctx.fillStyle = AMB; ctx.fill();
  if (hand === 'claw') {
    const o = .45 + .25 * s.grip;
    for (const k of [-1, 1]) { const ha = a2 + k * o; seg(hx, hy, hx + Math.cos(ha) * w * 1.25, hy + Math.sin(ha) * w * 1.25, w * .5, col); }
    circle(hx, hy, w * .55); ctx.fillStyle = '#4A4062'; ctx.fill();
  } else if (hand === 'three') {
    for (const k of [-1, 0, 1]) { const ha = a2 + k * .5; seg(hx, hy, hx + Math.cos(ha) * w * 1.1, hy + Math.sin(ha) * w * 1.1, w * .38, col); }
    circle(hx, hy, w * .6); ctx.fillStyle = '#3A3150'; ctx.fill();
  } else {
    circle(hx, hy, w * .85); ctx.fillStyle = rg(hx - w * .3, hy + w * .3, 0, hx, hy, w, ['#5B4F78', '#2A2338']); ctx.fill();
  }
  return [hx, hy];
}
function ik(hx, hy, fx, fy, l1, l2, bend) {
  let dx = fx - hx, dy = fy - hy, d = Math.hypot(dx, dy); const mx = l1 + l2 - 1e-4;
  if (d > mx) { dx *= mx / d; dy *= mx / d; d = mx; }
  const a = Math.atan2(dy, dx), b = Math.acos(clamp((l1 * l1 + d * d - l2 * l2) / (2 * l1 * d), -1, 1)), k = a + bend * b;
  return [hx + Math.cos(k) * l1, hy + Math.sin(k) * l1, hx + dx, hy + dy];
}
function drawOrbi(B) {
  const cy = .5 + s.hov + s.bob;
  const th = .55 + .45 * s.thrust + .4 * aud.bass;
  ctx.save(); ctx.globalCompositeOperation = 'lighter';
  ctx.fillStyle = rg(0, cy - .3, 0, 0, cy - .3, .26 * th, [rgba(MINT, .55), rgba(VIO, .22), rgba(VIO, 0)]); circle(0, cy - .3, .26 * th); ctx.fill();
  ctx.beginPath(); ctx.moveTo(-.075, cy - .27); ctx.quadraticCurveTo(0, cy - .27 - .3 * th, .075, cy - .27); ctx.closePath();
  ctx.fillStyle = lg(0, cy - .27, 0, cy - .27 - .3 * th, [rgba('#DFFFF8', .9), rgba(MINT, .5), rgba(MINT, 0)]); ctx.fill();
  ctx.restore();
  for (const sd of [-1, 1]) {
    ctx.beginPath(); ctx.ellipse(sd * .305, cy + .02, .05, .1, 0, 0, TAU); ctx.fillStyle = lg(sd * .26, 0, sd * .36, 0, ['#2A2338', '#120F1B']); ctx.fill();
    ctx.beginPath(); ctx.ellipse(sd * .315, cy + .02, .032, .07, 0, 0, TAU); ctx.lineWidth = .01; ctx.strokeStyle = rgba(LAV, .55 + .45 * aud.bass); ctx.stroke();
    glow(sd * .32, cy + .02, .07, LAV, .25 + .4 * aud.bass);
  }
  circle(0, cy, .3); ctx.fillStyle = rg(-.1, cy + .13, .02, 0, cy, .32, ['#4E4370', '#231C35', '#0B0912'], [0, .55, 1]); ctx.fill(); rim(.3);
  ctx.save(); circle(0, cy, .3); ctx.clip();
  ctx.beginPath(); ctx.ellipse(0, cy - .2, .3, .055, 0, 0, TAU); ctx.lineWidth = .008; ctx.strokeStyle = 'rgba(205,180,255,.18)'; ctx.stroke();
  ctx.restore();
  ctx.beginPath(); ctx.ellipse(0, cy + .02, .225, .158, 0, 0, TAU); ctx.fillStyle = lg(0, cy + .18, 0, cy - .14, ['#100D1A', '#020205']); ctx.fill();
  ctx.lineWidth = .009; ctx.strokeStyle = rgba(LAV, .3); ctx.stroke();
  const col = eyeCol(B.eye);
  ledEyes(0, cy + .04, .02, .085, col); ledMouth(0, cy - .088, .016, col);
  ctx.beginPath(); ctx.ellipse(-.08, cy + .12, .11, .032, -.25, 0, TAU); ctx.fillStyle = 'rgba(255,255,255,.07)'; ctx.fill();
  ctx.beginPath(); ctx.ellipse(-.14, cy + .19, .06, .03, .7, 0, TAU); ctx.fillStyle = 'rgba(255,255,255,.14)'; ctx.fill();
  const ab = s.ant.x, tx = Math.sin(ab) * .15, ty = cy + .3 + Math.cos(ab) * .14;
  ctx.beginPath(); ctx.moveTo(0, cy + .29); ctx.quadraticCurveTo(tx * .2, cy + .37, tx, ty); ctx.lineWidth = .014; ctx.strokeStyle = '#3A3150'; ctx.lineCap = 'round'; ctx.stroke();
  circle(tx, ty, .028); ctx.fillStyle = AMB; ctx.fill(); glow(tx, ty, .09, AMB, .5 + .5 * aud.treble);
  for (const sd of [-1, 1]) {
    const a = sd < 0 ? s.aL.x : s.aR.x, an = -PI / 2 + sd * a;
    const hx = sd * .34 + Math.cos(an) * .17, hy = cy - .04 + Math.sin(an) * .17;
    circle(hx, hy, .058); ctx.fillStyle = rg(hx - .02, hy + .02, 0, hx, hy, .06, ['#5D5180', '#1E1830']); ctx.fill();
    ctx.lineWidth = .008; ctx.strokeStyle = rgba(LAV, .6); ctx.stroke(); glow(hx, hy, .08, MINT, .18 + .3 * aud.bass);
    circle(hx, hy, .014); ctx.fillStyle = MINT; ctx.fill();
  }
}
function drawPixel(B) {
  const by = .62 + s.bob, w = s.wheel;
  circle(0, .13, .13); ctx.fillStyle = '#0B0910'; ctx.fill(); ctx.lineWidth = .028; ctx.strokeStyle = '#2B2439'; ctx.stroke();
  for (let i = 0; i < 10; i++) { const a = w + i / 10 * TAU; seg(Math.cos(a) * .1, .13 + Math.sin(a) * .1, Math.cos(a) * .128, .13 + Math.sin(a) * .128, .016, '#3B3350'); }
  circle(0, .13, .05); ctx.fillStyle = rg(-.015, .145, 0, 0, .13, .05, ['#6A5D8C', '#231C33']); ctx.fill();
  circle(0, .13, .032); ctx.lineWidth = .008; ctx.strokeStyle = rgba(MINT, .8); ctx.stroke(); glow(0, .13, .09, MINT, .25 + .4 * aud.bass);
  const top = by - .2;
  for (const sd of [-1, 1]) seg(sd * .06, .13, sd * .085, top, .022, '#3A3150');
  ctx.beginPath(); const n = 6;
  for (let i = 0; i <= n; i++) { const y = mix(.2, top, i / n); ctx.lineTo((i % 2 ? .03 : -.03), y); }
  ctx.lineWidth = .01; ctx.strokeStyle = AMB; ctx.stroke();
  rrect(0, by, .52, .46, .16); ctx.fillStyle = lg(-.26, by + .2, .26, by - .2, ['#463C61', '#221C33', '#100D18']); ctx.fill(); rim(.26);
  rrect(0, by - .2, .26, .02, .01); ctx.fillStyle = AMB; ctx.fill(); glow(0, by - .2, .12, AMB, .25);
  for (const sd of [-1, 1]) {
    circle(sd * .275, by + .02, .07); ctx.fillStyle = rg(sd * .26, by + .04, 0, sd * .275, by + .02, .07, ['#4B4066', '#15111F']); ctx.fill();
    circle(sd * .275, by + .02, .042); ctx.lineWidth = .009; ctx.strokeStyle = rgba(MINT, .6 + .4 * aud.bass); ctx.stroke();
  }
  rrect(0, by + .02, .38, .29, .085); ctx.fillStyle = '#020105'; ctx.fill(); ctx.lineWidth = .007; ctx.strokeStyle = rgba(AMB, .35); ctx.stroke();
  ctx.save(); rrect(0, by + .02, .38, .29, .085); ctx.clip();
  const col = eyeCol(B.eye);
  ledEyes(0, by + .05, .018, .075, col); ledMouth(0, by - .078, .015, col);
  ctx.beginPath(); ctx.moveTo(-.2, by + .1); ctx.lineTo(-.05, by + .17); ctx.lineTo(.0, by + .17); ctx.lineTo(-.15, by + .1); ctx.fillStyle = 'rgba(255,255,255,.06)'; ctx.fill();
  ctx.restore();
  const ab = s.ant.x, ax = .13 + Math.sin(ab) * .1, ay = by + .23 + Math.cos(ab) * .1;
  seg(.12, by + .22, ax, ay, .012, '#3A3150');
  ctx.beginPath(); ctx.ellipse(ax, ay, .05, .018, ab + .3, 0, TAU); ctx.fillStyle = '#4A4062'; ctx.fill(); circle(ax, ay + .01, .012); ctx.fillStyle = AMB; ctx.fill(); glow(ax, ay, .07, AMB, .4 * (.5 + aud.treble));
  arm2(-.27, by - .1, -1, s.aL.x, s.eL.x, .14, .12, .036, '#4A4062', 'claw');
  arm2(.27, by - .1, 1, s.aR.x, s.eR.x, .14, .12, .036, '#4A4062', 'claw');
}
function drawSentinel(B) {
  const hy = .42 + s.bob;
  for (const sd of [-1, 1]) {
    const f = sd < 0 ? s.footL : s.footR, [kx, ky, fx, fy] = ik(sd * .075, hy, sd * .1 + f[0], f[1] + .03, .22, .22, sd);
    seg(sd * .075, hy, kx, ky, .065, '#2B2440'); seg(kx, ky, fx, fy, .055, '#2B2440');
    seg(sd * .075, hy, kx, ky, .016, 'rgba(205,180,255,.12)');
    circle(kx, ky, .036); ctx.fillStyle = '#3B3254'; ctx.fill(); circle(kx, ky, .012); ctx.fillStyle = AMB; ctx.fill();
    rrect(fx + sd * .01, fy - .012, .13, .04, .02); ctx.fillStyle = '#1C1729'; ctx.fill(); rim(.07);
  }
  rrect(0, hy + .03, .21, .08, .03); ctx.fillStyle = '#231D33'; ctx.fill();
  ctx.beginPath(); ctx.moveTo(-.1, hy + .06); ctx.lineTo(-.215, hy + .33); ctx.quadraticCurveTo(0, hy + .41, .215, hy + .33); ctx.lineTo(.1, hy + .06); ctx.quadraticCurveTo(0, hy + .03, -.1, hy + .06); ctx.closePath();
  ctx.fillStyle = lg(-.2, hy + .35, .2, hy + .05, ['#4A3F66', '#221C33', '#0E0B16']); ctx.fill(); rim(.21);
  seg(0, hy + .09, 0, hy + .16, .006, 'rgba(205,180,255,.2)');
  const cz = hy + .24, pul = .6 + .4 * Math.sin(time * 3) + 1.2 * aud.bass;
  circle(0, cz, .062); ctx.fillStyle = '#15111F'; ctx.fill(); ctx.lineWidth = .01; ctx.strokeStyle = '#4A4062'; ctx.stroke();
  circle(0, cz, .044); ctx.fillStyle = rg(0, cz, 0, 0, cz, .044, ['#FFFFFF', LAV, VIO]); ctx.fill();
  ctx.save(); ctx.globalCompositeOperation = 'lighter'; ctx.lineWidth = .008; ctx.strokeStyle = rgba('#FFFFFF', .5);
  for (let i = 0; i < 3; i++) { ctx.beginPath(); ctx.arc(0, cz, .03, time * 2 + i * TAU / 3, time * 2 + i * TAU / 3 + .9); ctx.stroke(); }
  ctx.restore(); glow(0, cz, .22 * pul, VIO, .45); glow(0, cz, .09, LAV, .5);
  for (const sd of [-1, 1]) { circle(sd * .23, hy + .34, .056); ctx.fillStyle = rg(sd * .22, hy + .36, 0, sd * .23, hy + .34, .056, ['#5B4F78', '#1E1830']); ctx.fill(); rim(.05); }
  arm2(-.24, hy + .32, -1, s.aL.x, s.eL.x, .19, .18, .045, '#2E2742', 'three');
  arm2(.24, hy + .32, 1, s.aR.x, s.eR.x, .19, .18, .045, '#2E2742', 'three');
  rrect(0, hy + .41, .05, .07, .02); ctx.fillStyle = '#1C1729'; ctx.fill();
  const hcy = hy + .53;
  ctx.save(); ctx.translate(0, hcy); ctx.rotate(s.headTilt); ctx.translate(0, -hcy);
  for (const sd of [-1, 1]) { ctx.beginPath(); ctx.moveTo(sd * .12, hcy + .02); ctx.lineTo(sd * .19, hcy + .1); ctx.lineTo(sd * .13, hcy - .03); ctx.closePath(); ctx.fillStyle = '#2A2338'; ctx.fill(); }
  ctx.beginPath(); ctx.ellipse(0, hcy, .145, .122, 0, 0, TAU); ctx.fillStyle = rg(-.05, hcy + .06, .01, 0, hcy, .15, ['#53477A', '#241D37', '#0D0A15']); ctx.fill(); rim(.14);
  seg(0, hcy + .122, 0, hcy + .155, .014, LAV); glow(0, hcy + .15, .05, LAV, .4);
  rrect(0, hcy, .24, .1, .05); ctx.fillStyle = '#030206'; ctx.fill(); ctx.lineWidth = .006; ctx.strokeStyle = rgba(AMB, .4); ctx.stroke();
  const col = eyeCol(B.eye); ledEyes(0, hcy + .002, .0115, .056, col);
  for (let i = -2; i <= 2; i++) seg(i * .018, hcy - .07, i * .018, hcy - .09, .006, 'rgba(205,180,255,.25)');
  ctx.restore();
}
function drawBubble(B) {
  const by = .34 + s.bob;
  { const f = s.footB, [kx, ky, fx, fy] = ik(0, by - .03, .03 + f[0], f[1] + .05, .2, .22, 1);
    seg(0, by - .03, kx, ky, .03, '#1A1526'); seg(kx, ky, fx, fy, .026, '#1A1526'); }
  for (const sd of [-1, 1]) {
    const f = sd < 0 ? s.footL : s.footR, hx = sd * .16, [kx, ky, fx, fy] = ik(hx, by - .02, sd * .34 + f[0], f[1] + .01, .2, .25, sd);
    seg(hx, by - .02, kx, ky, .036, '#2E2742'); seg(kx, ky, fx, fy, .03, '#2E2742');
    circle(kx, ky, .026); ctx.fillStyle = '#4A4062'; ctx.fill(); circle(kx, ky, .01); ctx.fillStyle = LAV; ctx.fill(); glow(kx, ky, .05, LAV, .3);
    circle(fx, fy, .02); ctx.fillStyle = '#4A4062'; ctx.fill();
  }
  const dy = by + .25;
  circle(0, dy, .245); ctx.fillStyle = 'rgba(30,22,52,.55)'; ctx.fill();
  ctx.beginPath();
  for (let i = 0; i <= 40; i++) { const a = i / 40 * TAU, r = .155 + .012 * Math.sin(a * 7 + time * 1.5) + .008 * Math.sin(a * 11 - time * 2) + .02 * aud.bass; ctx.lineTo(Math.cos(a) * r * 1.05, dy - .02 + Math.sin(a) * r * .92); }
  ctx.closePath(); ctx.fillStyle = rg(-.04, dy + .04, .01, 0, dy - .02, .17, ['#D9C2FF', '#9A6BEA', '#4B2389']); ctx.fill();
  ctx.save(); ctx.clip(); ctx.globalCompositeOperation = 'lighter';
  for (let i = 0; i < 6; i++) {
    const a = i * 1.7 + time * .3, fl = .5 + .5 * Math.sin(time * (3 + i) + i * 2);
    ctx.beginPath(); ctx.moveTo(Math.cos(a) * .15, dy + Math.sin(a) * .13); ctx.bezierCurveTo(Math.cos(a + 1) * .05, dy + .05, Math.cos(a + 2) * .08, dy - .06, Math.cos(a + 3) * .15, dy - .02 + Math.sin(a + 3) * .12);
    ctx.lineWidth = .006; ctx.strokeStyle = `rgba(255,240,255,${.12 + .3 * fl * (s.think + .3)})`; ctx.stroke();
  }
  ctx.restore();
  const col = eyeCol(B.eye);
  rrect(0, dy - .015, .2, .16, .06); ctx.fillStyle = 'rgba(10,4,24,.72)'; ctx.fill(); ctx.lineWidth = .005; ctx.strokeStyle = rgba(LAV, .35); ctx.stroke();
  ledEyes(0, dy + .015, .0155, .056, col); ledMouth(0, dy - .085, .011, col);
  glow(0, dy, .3, VIO, .18 + .3 * aud.bass);
  circle(0, dy, .245); ctx.fillStyle = rg(0, dy, .12, 0, dy, .25, ['rgba(205,180,255,0)', 'rgba(205,180,255,.16)']); ctx.fill();
  ctx.lineWidth = .009; ctx.strokeStyle = rgba(LAV, .45); ctx.stroke();
  ctx.beginPath(); ctx.arc(0, dy, .21, 1.9, 2.7); ctx.lineWidth = .02; ctx.strokeStyle = 'rgba(255,255,255,.22)'; ctx.lineCap = 'round'; ctx.stroke();
  circle(-.12, dy + .14, .018); ctx.fillStyle = 'rgba(255,255,255,.35)'; ctx.fill();
  circle(0, dy + .245, .02); ctx.fillStyle = '#4A4062'; ctx.fill(); glow(0, dy + .25, .05, AMB, .5);
  ctx.beginPath(); ctx.ellipse(0, by, .22, .065, 0, 0, TAU); ctx.fillStyle = lg(0, by + .06, 0, by - .06, ['#5A4E7A', '#2A2338', '#120F1B']); ctx.fill(); rim(.22);
  for (let i = 0; i < 7; i++) { const a = PI + (i + .5) / 7 * PI, on = i === 3; circle(Math.cos(a) * .19, by + Math.sin(a) * .05, .011); ctx.fillStyle = on ? AMB : '#3A3150'; ctx.fill(); }
  arm2(-.2, by + .01, -1, s.aL.x, s.eL.x, .1, .09, .026, '#3A3150', 'claw');
  arm2(.2, by + .01, 1, s.aR.x, s.eR.x, .1, .09, .026, '#3A3150', 'claw');
}
function drawTink(B) {
  const by = .42 + s.bob;
  for (const sd of [-1, 1]) {
    const f = sd < 0 ? s.footL : s.footR, x0 = sd * .085, y0 = by - .16, x1 = sd * .1 + f[0], y1 = f[1] + .03;
    ctx.beginPath(); ctx.moveTo(x0, y0); const n = 9;
    for (let i = 1; i < n; i++) { const t = i / n; ctx.lineTo(mix(x0, x1, t) + (i % 2 ? .035 : -.035), mix(y0, y1, t)); }
    ctx.lineTo(x1, y1); ctx.lineWidth = .013; ctx.strokeStyle = '#9C92B8'; ctx.lineJoin = 'round'; ctx.stroke();
    ctx.beginPath(); ctx.ellipse(x1 + sd * .01, y1 - .012, .065, .028, 0, 0, TAU); ctx.fillStyle = '#231D33'; ctx.fill(); rim(.06);
    seg(x1 - .04 + sd * .01, y1 - .035, x1 + .04 + sd * .01, y1 - .035, .006, rgba(AMB, .7));
  }
  for (const sd of [-1, 1]) {
    const ab = s.ant.x * (sd < 0 ? 1 : 1.3) + sd * .25, bx = sd * .07, bY = by + .19, tx = bx + Math.sin(ab) * .2, ty = bY + Math.cos(ab) * .17;
    ctx.beginPath(); ctx.moveTo(bx, bY); ctx.quadraticCurveTo(bx + sd * .02, bY + .12, tx, ty); ctx.lineWidth = .01; ctx.strokeStyle = '#4A4062'; ctx.stroke();
    circle(tx, ty, .026); ctx.fillStyle = sd < 0 ? LAV : AMB; ctx.fill(); glow(tx, ty, .08, sd < 0 ? LAV : AMB, .45 + .4 * aud.treble);
  }
  ctx.beginPath(); ctx.ellipse(0, by, .205, .225, 0, 0, TAU); ctx.fillStyle = rg(-.07, by + .1, .01, 0, by, .24, ['#53477A', '#241D37', '#0B0912'], [0, .5, 1]); ctx.fill(); rim(.2);
  for (const sd of [-1, 1]) for (let i = 0; i < 3; i++) seg(sd * .15, by - .07 - i * .025, sd * .175, by - .07 - i * .025, .007, 'rgba(205,180,255,.2)');
  const ly = by + .02;
  circle(0, ly, .135); ctx.fillStyle = lg(-.13, ly + .13, .13, ly - .13, ['#6C6090', '#2A2338', '#0D0A14']); ctx.fill();
  const nseg = 16;
  for (let i = 0; i < nseg; i++) {
    const a = PI / 2 - i / nseg * TAU, on = (i % nseg < 3);
    ctx.beginPath(); ctx.arc(0, ly, .122, a - .14, a - .03 + 0); ctx.lineWidth = .012;
    ctx.strokeStyle = on ? MINT : 'rgba(255,255,255,.07)'; ctx.stroke();
  }
  circle(0, ly, .104); ctx.fillStyle = '#020105'; ctx.fill();
  const col = eyeCol(B.eye); ledEyes(0, ly, .021, 0, col, 1);
  ctx.beginPath(); ctx.ellipse(-.04, ly + .055, .045, .02, -.5, 0, TAU); ctx.fillStyle = 'rgba(255,255,255,.12)'; ctx.fill();
  arm2(-.19, by - .02, -1, s.aL.x, s.eL.x, .07, .065, .036, '#3A3150', 'mitt');
  arm2(.19, by - .02, 1, s.aR.x, s.eR.x, .07, .065, .036, '#3A3150', 'mitt');
}
const BOTS = [
  { loco: 'hover', eye: '#5EF0D8', draw: drawOrbi },
  { loco: 'wheel', eye: '#FFB36B', draw: drawPixel },
  { loco: 'walk', eye: '#FF9A6B', draw: drawSentinel },
  { loco: 'tripod', eye: '#FFFFFF', draw: drawBubble },
  { loco: 'hop', eye: '#7CFFB0', draw: drawTink },
];
const P = new URLSearchParams(location.search);
let cur = +(P.get('bot') || 0);
let time = 1.3;
const sp = x => ({ x, v: 0 });
const s = {
  x: 0, air: 0, hov: .1, bob: 0, thrust: .2, wheel: .4, tilt: sp(0), sq: sp(1),
  aL: sp(+(P.get('aL') || .5)), aR: sp(+(P.get('aR') || 2.4)), eL: sp(.35), eR: sp(.6), ant: sp(.15),
  footL: [0, 0], footR: [0, 0], footB: [0, 0], headTilt: .08, grip: .5, think: .6,
  look: { x: .15, y: .1 }, expr: P.get('expr') || 'happy', blink: 0,
};
const aud = { bass: .25, treble: .4 };
function drawFloor() {
  ctx.fillStyle = '#000'; ctx.fillRect(0, 0, W, H);
  const gg = ctx.createRadialGradient(CX, GY, 0, CX, GY, W * .8);
  gg.addColorStop(0, `rgba(157,78,221,${.16 + .2 * aud.bass})`); gg.addColorStop(.5, 'rgba(60,30,90,.05)'); gg.addColorStop(1, 'rgba(0,0,0,0)');
  ctx.fillStyle = gg; ctx.fillRect(0, GY - H * .4, W, H * .8);
  ctx.save(); ctx.beginPath(); ctx.rect(0, GY, W, H - GY); ctx.clip();
  ctx.lineWidth = 1;
  for (let i = -12; i <= 12; i++) { const x = CX + i * U * .35; ctx.beginPath(); ctx.moveTo(CX + (x - CX) * .25, GY); ctx.lineTo(x * 1 + (x - CX) * 1.6, H); ctx.strokeStyle = `rgba(45,212,191,${.07 * (1 - Math.abs(i) / 13)})`; ctx.stroke(); }
  for (let j = 1; j < 9; j++) { const y = GY + Math.pow(j / 9, 1.8) * (H - GY); ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(W, y); ctx.strokeStyle = `rgba(45,212,191,${.06 * (1 - j / 9) + .02 * aud.bass})`; ctx.stroke(); }
  ctx.restore();
  ctx.fillStyle = lg(0, 0, W, 0, ['rgba(205,180,255,0)', 'rgba(205,180,255,.25)', 'rgba(205,180,255,0)']); ctx.fillRect(0, GY - .5, W, 1);
}
function drawBot(reflect) {
  const B = BOTS[cur], X = CX + s.x * U, Y = GY - s.air * U;
  ctx.save(); ctx.translate(X, reflect ? GY + s.air * U : Y); ctx.scale(U, reflect ? U : -U);
  B.draw(B);
  ctx.restore();
}
function drawShadow() {
  const B = BOTS[cur], lift = s.air + (B.loco === 'hover' ? s.hov : 0), k = 1 / (1 + lift * 2.5), X = CX + s.x * U;
  ctx.save(); ctx.translate(X, GY); ctx.scale(1, .2);
  ctx.fillStyle = rg(0, 0, 0, 0, 0, U * .4 * k, ['rgba(0,0,0,.85)', 'rgba(0,0,0,0)']); circle(0, 0, U * .4 * k); ctx.fill();
  if (B.loco === 'hover') { ctx.globalCompositeOperation = 'lighter'; ctx.fillStyle = rg(0, 0, 0, 0, 0, U * .35 * k, [rgba(MINT, .25 * (.5 + s.thrust)), rgba(MINT, 0)]); circle(0, 0, U * .35 * k); ctx.fill(); }
  ctx.restore();
}
// card framing: the bot large, floor low
U = +(P.get('u') || 300); GY = H * +(P.get('gy') || .74);
ctx.setTransform(DPR, 0, 0, DPR, 0, 0);
drawFloor();
ctx.save(); ctx.beginPath(); ctx.rect(0, GY, W, H - GY); ctx.clip(); ctx.globalAlpha = .13; drawBot(true); ctx.restore();
drawShadow();
drawBot(false);
document.title = 'done';
