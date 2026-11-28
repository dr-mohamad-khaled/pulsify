// Droppy: a living water droplet. Size = battery level. It follows your finger, splashes when it hits the
// screen edges (losing volume to the splash), and the splashed beads crawl back and merge to restore it.
// While charging, rain falls on it. Six liquids. Mirrors DropScene.kt line for line.
'use strict';
const DROP = (() => {
  const LIQ = [
    // name, stiffness, damping, restitution, splash, crawl, gravity, drag on walls
    { name: 'Water',   k: 140, damp: 9,  rest: .35, splash: 1.0, crawl: .45, g: 2.4, wall: .5 },
    { name: 'Honey',   k: 60,  damp: 15, rest: .08, splash: .45, crawl: .16, g: 1.9, wall: .92 },
    { name: 'Lava',    k: 95,  damp: 11, rest: .18, splash: .7,  crawl: .28, g: 2.2, wall: .75 },
    { name: 'Slime',   k: 48,  damp: 4.5, rest: .62, splash: .55, crawl: .34, g: 2.2, wall: .85 },
    { name: 'Mercury', k: 230, damp: 10, rest: .26, splash: 1.35, crawl: .75, g: 3.0, wall: .2 },
    { name: 'Galaxy',  k: 120, damp: 8,  rest: .4,  splash: 1.0, crawl: .4,  g: 1.6, wall: .5 },
  ];
  const M = 16;                      // outline points
  const MAX = 64;                    // drops
  const TAU = Math.PI * 2;
  const clamp = (x, a, b) => x < a ? a : x > b ? b : x;
  const sm = x => { x = clamp(x, 0, 1); return x * x * (3 - 2 * x); };
  const rnd = () => Math.random();

  class Drop {
    constructor() {
      this.liq = 0; this.face = 0;
      this.halfW = .5; this.halfH = 1; this.floor = -.5;
      this.x = 0; this.y = .2; this.vx = 0; this.vy = 0; this.ax = 0; this.ay = 0;
      this.d = new Float32Array(M); this.dv = new Float32Array(M);
      this.drops = [];              // {x,y,vx,vy,m,age,rain}
      this.battery = .6; this.shown = .6; this.charging = false; this.wasCharging = false; this.bonus = 0;
      this.t = 0; this.held = false; this.fx = 0; this.fy = 0; this.downT = 0; this.moved = 0;
      this.lookX = 0; this.lookY = 0; this.tlx = 0; this.tly = 0; this.lookT = 0;
      this.blink = 0; this.nextBlink = 2; this.expr = 'normal'; this.exprT = 0;
      this.act = null; this.actT = 0; this.nextAct = 4; this.rainT = 0; this.pctT = -9;
      this.ix = 0; this.iy = .5; this.ivx = 0; this.ivy = 0; this.tilt = 0; this.bass = 0; this.music = 0; this.sndT = 0; this.beat = 0; this.squash = 0; this.wob = 0;
    }
    get L() { return LIQ[this.liq]; }
    // total liquid (area units): from the battery, 0% = a small bead, 100% = a big drop
    get total() { const r = .1 + .27 * this.shown; return r * r; }
    get splashed() { let s = 0; for (const p of this.drops) if (!p.rain) s += p.m; return s; }
    get R() { return Math.sqrt(Math.max(this.total * .15, this.total - this.splashed + this.bonus)); }
    resize(halfW, halfH, ground) { this.halfW = halfW; this.halfH = halfH; this.floor = isNaN(ground) ? -halfH * .5 : ground; }

    // ------------------------------------------------------------------ touch (uv, y up)
    down(x, y) { this.held = true; this.fx = x; this.fy = y; this.downT = this.t; this.moved = 0; this.expr = 'surprised'; this.exprT = .6; this.act = null; }
    move(x, y) { this.moved += Math.hypot(x - this.fx, y - this.fy); this.fx = x; this.fy = y; }
    up() {
      if (!this.held) return; this.held = false;
      const quick = this.t - this.downT < .3 && this.moved < .03;
      if (quick && Math.hypot(this.fx - this.x, this.fy - this.y) < this.R * 1.3) { this.tickle(); }
    }
    tickle() { this.expr = 'happy'; this.exprT = 1.2; for (let i = 0; i < M; i++) this.dv[i] += (rnd() - .5) * 3; this.vy += 1.1; }
    swipe(dir) { this.vx += -dir * 1.6; this.vy += .6; this.expr = 'surprised'; this.exprT = .8; }

    trick(n) {
      this.act = n; this.actT = 0;
      if (n === 'hop') { this.vy = 2.0 + rnd() * .4; this.vx += (rnd() - .5) * .8; this.squash = .5; }
      if (n === 'split') { this.spawnSplit(); }
      if (n === 'splat') { this.vy = 2.6; this.vx = (this.x > 0 ? 1 : -1) * 1.8; }
    }
    spawnSplit() {
      // spits out two little droplets that bounce away and come home
      const R = this.R; const m = this.total * .14;
      for (const s of [-1, 1]) this.add(this.x + s * R * .8, this.y + R * .2, s * (1.1 + rnd() * .4), 1.6 + rnd() * .5, m, false);
      this.expr = 'happy'; this.exprT = 1.4;
    }
    add(x, y, vx, vy, m, rain) { if (this.drops.length < MAX) this.drops.push({ x, y, vx, vy, m, age: 0, rain }); }

    // ------------------------------------------------------------------ the splash when it hits an edge
    splash(nx, ny, vn) {
      const L = this.L, R = this.R;
      const hard = clamp((vn - 1.05) / 2.2, 0, 1);
      if (hard <= 0) { this.squash = Math.max(this.squash, clamp(vn * .25, 0, .4)); return; }
      const avail = this.total - this.splashed;
      const lose = avail * clamp(hard * .42 * L.splash, 0, .42);
      const n = Math.max(2, Math.round((3 + hard * 13) * L.splash));
      // where it touches the edge, and the directions along the edge
      const px = this.x - nx * R * .9, py = this.y - ny * R * .9, tx = -ny, ty = nx;
      let left = lose;
      for (let i = 0; i < n && this.drops.length < MAX; i++) {
        const m = i === n - 1 ? left : lose / n * (.4 + rnd() * 1.2); left -= m; if (m <= 1e-6) continue;
        const along = (rnd() - .5) * 2, up = .25 + rnd() * .9;
        const sp = vn * (.35 + rnd() * .55);
        this.add(px + tx * along * R * .6, py + ty * along * R * .6, (nx * up + tx * along * 1.1) * sp, (ny * up + ty * along * 1.1) * sp, m, false);
      }
      this.squash = .9; this.expr = 'squish'; this.exprT = .9 + hard;
      for (let i = 0; i < M; i++) this.dv[i] += (rnd() - .5) * 4 * hard;
    }

    // ------------------------------------------------------------------ update
    update(dt, inp) {
      this.t += dt; const L = this.L;
      this.battery = inp.battery; this.charging = inp.charging;
      // the shown level eases to the battery, a little faster while it rains
      this.shown += (this.battery - this.shown) * Math.min(1, dt * (this.charging ? .8 : .3));
      if (this.charging && !this.wasCharging) { this.pctT = this.t; this.expr = 'happy'; this.exprT = 2; }
      this.wasCharging = this.charging;
      this.bonus *= Math.exp(-dt * .7);
      this.bass += (inp.bass - this.bass) * Math.min(1, dt * 12);
      // sound: look up for the first 3 s, dance after 4 s
      if (inp.music) { this.sndT += dt; } else this.sndT = 0;
      const dancing = this.sndT > 4;

      const R = this.R, fl = this.floor, hw = this.halfW, hh = this.halfH;
      const ox = this.vx, oy = this.vy;
      if (this.held) {
        // it follows your finger like a drop on glass: a soft spring, so it stretches behind
        const kx = 55, dmp = 11;
        this.vx += ((this.fx - this.x) * kx - this.vx * dmp) * dt;
        this.vy += ((this.fy - this.y) * kx - this.vy * dmp) * dt;
      } else {
        this.vy -= L.g * dt;
        if (dancing) { this.beat += dt * 2.2; if (this.y - R * .85 <= fl + .002 && this.bass > .45 && this.vy <= 0) { this.vy = .9 + this.bass * .8; this.squash = .6; } }
      }
      this.x += this.vx * dt; this.y += this.vy * dt;
      // edges: floor, walls, ceiling (the body flattens on them)
      const rest = R * .82;
      const hit = (nx, ny, vn) => { this.splash(nx, ny, vn); };
      if (this.y - rest < fl) { const vn = -this.vy; this.y = fl + rest; if (vn > 0) { if (!this.held) hit(0, 1, vn); this.vy = vn > .25 ? vn * L.rest : 0; this.vx *= Math.pow(.02, dt) } }
      if (this.y + rest > hh) { const vn = this.vy; this.y = hh - rest; if (vn > 0) { hit(0, -1, vn); this.vy = -vn * L.rest; } }
      if (this.x - rest < -hw) { const vn = -this.vx; this.x = -hw + rest; if (vn > 0) { hit(1, 0, vn); this.vx = vn * L.rest; } }
      if (this.x + rest > hw) { const vn = this.vx; this.x = hw - rest; if (vn > 0) { hit(-1, 0, vn); this.vx = -vn * L.rest; } }
      this.ax = (this.vx - ox) / Math.max(dt, 1e-3); this.ay = (this.vy - oy) / Math.max(dt, 1e-3);

      // outline: springs, surface tension between neighbours, inertia; the edges flatten it
      const squash = this.squash; this.squash *= Math.exp(-dt * 5);
      const wob = this.act === 'wobble' ? Math.sin(this.actT * 18) * .25 * (1 - this.actT / 1.6) : 0;
      const dance = dancing ? this.bass * .18 : 0;
      for (let i = 0; i < M; i++) {
        const a = i / M * TAU, cx = Math.cos(a), cy = Math.sin(a);
        const lap = this.d[(i + 1) % M] + this.d[(i + M - 1) % M] - 2 * this.d[i];
        const inert = -(this.ax * cx + this.ay * cy) * .007;
        // moving liquid is never a perfect circle: it stretches along its motion and trails a tail behind
        const sp = Math.hypot(this.vx, this.vy), ux = sp > 1e-3 ? this.vx / sp : 0, uy = sp > 1e-3 ? this.vy / sp : 0;
        const al = cx * ux + cy * uy, st = clamp(sp * .09, 0, .32);
        const flow = st * (al * al - .5) + st * .9 * Math.pow(Math.max(0, -al), 3) - st * .35 * Math.pow(Math.max(0, al), 3)
          + .05 * Math.min(1, sp) * Math.sin(3 * a + this.t * 9) + .035 * Math.min(1, sp) * Math.sin(5 * a - this.t * 13);
        const tgt = squash * .35 * (cx * cx - cy * cy) + wob * Math.cos(2 * a + this.t * 3) + dance * cy * cy + flow;
        const acc = L.k * (tgt - this.d[i]) - L.damp * this.dv[i] + L.k * .6 * lap + inert * L.k;
        this.dv[i] += acc * dt;
      }
      let mean = 0;
      for (let i = 0; i < M; i++) { this.d[i] += this.dv[i] * dt; this.d[i] = clamp(this.d[i], -.55, .85); mean += this.d[i]; }
      mean /= M; for (let i = 0; i < M; i++) this.d[i] -= mean;      // keep its area: squashed here, it bulges there
      for (let i = 0; i < M; i++) {
        const a = i / M * TAU, cx = Math.cos(a), cy = Math.sin(a); const r = R * (1 + this.d[i]);
        let lim = 9;
        if (cy < -.05) lim = Math.min(lim, (this.y - fl) / -cy);
        if (cy > .05) lim = Math.min(lim, (hh - this.y) / cy);
        if (cx < -.05) lim = Math.min(lim, (this.x + hw) / -cx);
        if (cx > .05) lim = Math.min(lim, (hw - this.x) / cx);
        if (r > lim) { this.d[i] = lim / R - 1; if (this.dv[i] > 0) this.dv[i] = 0; }
      }

      // the splashed beads: fly, slide down the walls, crawl back along the floor, merge home
      const ds = this.drops;
      for (let i = ds.length - 1; i >= 0; i--) {
        const p = ds[i]; p.age += dt; const r = Math.sqrt(p.m);
        p.vy -= L.g * dt * (p.rain ? .4 : 1);
        const home = p.age > .7;
        if (home) {
          const dx = this.x - p.x, dy = this.y - p.y, dd = Math.hypot(dx, dy) + 1e-4;
          const pull = sm((p.age - .7) / 1.8) * (this.held || this.y - rest > fl + .05 ? 3.2 : .9);
          p.vx += dx / dd * pull * dt; p.vy += dy / dd * pull * dt * (this.held ? 1 : .3);
        }
        p.x += p.vx * dt; p.y += p.vy * dt;
        const onFloor = p.y - r * .6 <= fl;
        if (onFloor) { p.y = fl + r * .6; if (p.vy < 0) p.vy = -p.vy * .15 * (p.vy < -1 ? 1 : 0);
          // crawl home along the floor
          if (home) { const dir = Math.sign(this.x - p.x); p.vx += (dir * L.crawl * (1 + sm((p.age - .7) / 2)) - p.vx) * Math.min(1, dt * 4); } else p.vx *= Math.pow(.05, dt); }
        if (p.x - r * .6 < -hw) { p.x = -hw + r * .6; p.vx = Math.abs(p.vx) * .1; p.vy *= Math.pow(1 - L.wall, dt * 6); }
        if (p.x + r * .6 > hw) { p.x = hw - r * .6; p.vx = -Math.abs(p.vx) * .1; p.vy *= Math.pow(1 - L.wall, dt * 6); }
        if (p.y + r * .6 > hh) { p.y = hh - r * .6; p.vy = -Math.abs(p.vy) * .2; }
        // merge into the body (not straight after flying off)
        const dd = Math.hypot(p.x - this.x, p.y - this.y);
        if ((p.age > .35 || p.rain) && dd < this.R * .95 + r * .45) {
          if (p.rain) this.bonus += p.m * .6;
          ds.splice(i, 1);
          const a = Math.atan2(p.y - this.y, p.x - this.x); const k = Math.round(((a / TAU) % 1 + 1) % 1 * M) % M;
          this.dv[k] += p.rain ? -.8 : -1.6; this.dv[(k + 1) % M] -= .5; this.dv[(k + M - 1) % M] -= .5;
          continue;
        }
      }
      // beads that touch become one
      for (let i = 0; i < ds.length; i++) for (let j = ds.length - 1; j > i; j--) {
        const a = ds[i], b = ds[j]; const ra = Math.sqrt(a.m), rb = Math.sqrt(b.m);
        if (a.age > .25 && b.age > .25 && Math.hypot(a.x - b.x, a.y - b.y) < (ra + rb) * .5) {
          const m = a.m + b.m; a.x = (a.x * a.m + b.x * b.m) / m; a.y = (a.y * a.m + b.y * b.m) / m; a.vx = (a.vx * a.m + b.vx * b.m) / m; a.vy = (a.vy * a.m + b.vy * b.m) / m;
          a.m = m; a.rain = a.rain && b.rain; a.age = Math.max(a.age, b.age); ds.splice(j, 1);
        }
      }
      // rain while charging
      if (this.charging) {
        this.rainT -= dt;
        if (this.rainT <= 0) { this.rainT = .1 + rnd() * .12; this.add(this.x + (rnd() - .5) * this.R * 2.2, hh + .05, (rnd() - .5) * .05, -1.4 - rnd() * .6, this.total * (.004 + rnd() * .006), true); }
      }

      // the face: eyes look at the finger, up at the rain, around now and then; blinking; expressions
      this.lookT -= dt;
      if (this.held) { this.tlx = this.fx - this.x; this.tly = this.fy - this.y; }
      else if (this.charging) { this.tlx = (rnd() - .5) * .02; this.tly = 1; }
      else if (this.sndT > 0 && this.sndT < 3) { this.tlx = 0; this.tly = 1; }
      else if (this.lookT <= 0) { this.lookT = 1 + rnd() * 2.5; const r0 = rnd(); this.tlx = r0 < .5 ? 0 : (rnd() - .5) * 2; this.tly = r0 < .5 ? 0 : (rnd() - .3); }
      const ll = Math.hypot(this.tlx, this.tly) || 1, lk = Math.min(1, ll / .15);
      this.lookX += (this.tlx / ll * lk - this.lookX) * Math.min(1, dt * 10); this.lookY += (this.tly / ll * lk - this.lookY) * Math.min(1, dt * 10);
      this.nextBlink -= dt; if (this.nextBlink <= 0) { this.blink = 1; this.nextBlink = 2 + rnd() * 3; }
      this.blink = Math.max(0, this.blink - dt * 7);
      this.exprT -= dt;
      if (this.exprT <= 0) this.expr = dancing ? 'happy' : (this.battery < .15 && !this.charging) ? 'sleepy' : Math.hypot(this.vx, this.vy) > 1.6 ? 'surprised' : 'normal';

      // the battery icon floats above its head on a soft spring; the liquid inside tilts as it swings
      { const tx = clamp(this.x - .03, -hw + .16, hw - .3), ty = Math.min(this.y + this.R * 1.2 + .14, hh - .14);
        if (this.t < .05) { this.ix = tx; this.iy = ty; }
        const ax2 = (tx - this.ix) * 40 - this.ivx * 9, ay2 = (ty - this.iy) * 40 - this.ivy * 9;
        this.ivx += ax2 * dt; this.ivy += ay2 * dt; this.ix += this.ivx * dt; this.iy += this.ivy * dt;
        this.tilt += (clamp(-ax2 * .03, -1, 1) - this.tilt) * Math.min(1, dt * 6);
        this.iconA = (this.iconA || 0) + ((this.charging ? 1 : 0) - (this.iconA || 0)) * Math.min(1, dt * 4); }
      // tricks (at least three a minute)
      if (this.act) { this.actT += dt; if (this.actT > 1.6) this.act = null; }
      this.nextAct -= dt;
      if (this.nextAct <= 0 && !this.held && !this.act && !dancing) {
        this.nextAct = 4 + rnd() * 4;
        this.trick(['hop', 'wobble', 'split', 'hop', 'wobble', 'look'][Math.floor(rnd() * 6)]);
      }
    }

    // outline radii (for the shader) and the face parameters
    radii(out) { const R = this.R; for (let i = 0; i < M; i++) out[i] = R * (1 + this.d[i]); }
    faceParams() {
      // eyes: open (0..1), happy arcs (0..1), squish > < (0..1); mouth: curve (+smile/-frown), open (O)
      const e = this.expr; const op = 1 - this.blink;
      return {
        open: e === 'sleepy' ? .35 * op : op, happy: e === 'happy' ? 1 : 0, squish: e === 'squish' ? 1 : 0,
        smile: e === 'happy' ? 1 : e === 'sleepy' ? -.2 : e === 'squish' ? -.4 : .55, mouthO: e === 'surprised' ? 1 : 0,
        big: e === 'surprised' ? 1.25 : 1,
      };
    }
  }
  return { Drop, LIQ, M, MAX };
})();
