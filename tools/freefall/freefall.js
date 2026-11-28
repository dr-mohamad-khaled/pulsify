// Free Fall astronaut: procedural animation on the astro2 skeleton (22 bones, recovered from the skin weights).
// Every joint is driven by springs on rotation angles, so poses change smoothly and the body never shrinks
// (the old version blended whole bone matrices between clips whose body orientation differed by ~90 degrees).
// Port 1:1 to Kotlin (FreeFallRig.kt).
'use strict';
const FF = (() => {
  const PAR = [-1, 0, 1, 2, 3, 4, 3, 6, 7, 8, 3, 10, 11, 12, 0, 14, 15, 16, 0, 18, 19, 20];
  // pivots in model space (Blender axes: x = his left, y = his back, z = up; feet at z = 0)
  const PIV = [
    [0, -0.01, 0.56], [0, -0.01, 0.6], [0, -0.01, 0.675], [0, 0, 0.715], [0, 0, 0.775], [0, -0.005, 0.79],
    [0.045, 0, 0.73], [0.127, -0.025, 0.705], [0.21, -0.03, 0.712], [0.306, -0.07, 0.725],
    [-0.045, 0, 0.73], [-0.13, -0.025, 0.705], [-0.208, -0.03, 0.712], [-0.305, -0.07, 0.725],
    [0.07, -0.03, 0.505], [0.075, -0.045, 0.267], [0.087, -0.04, 0.07], [0.11, -0.1, 0.03],
    [-0.068, -0.03, 0.505], [-0.075, -0.045, 0.267], [-0.087, -0.04, 0.07], [-0.11, -0.1, 0.03],
  ];
  const NB = 22;
  // joint limits (radians) per bone and axis, so no pose ever bends a joint far enough to crumple the suit
  const LIM = [
    [.6, .6, .6], [.35, .3, .35], [.35, .3, .35], [.3, .3, .3], [.3, .25, .45], [.4, .3, .6],
    [.2, .25, .25], [.5, 1.15, 1.1], [.4, 1.35, 1.25], [.4, .5, .5],
    [.2, .25, .25], [.5, 1.15, 1.1], [.4, 1.35, 1.25], [.4, .5, .5],
    [1.2, .5, .35], [1.4, .2, .2], [.5, .2, .2], [.3, .1, .1],
    [1.2, .5, .35], [1.4, .2, .2], [.5, .2, .2], [.3, .1, .1],
  ];
  const L = -1, R = 1;   // side sign: arms/legs on +x are his left
  const ARM = { [L]: [6, 7, 8, 9], [R]: [10, 11, 12, 13] }, LEG = { [L]: [14, 15, 16, 17], [R]: [18, 19, 20, 21] };

  function zero() { return Array.from({ length: NB }, () => [0, 0, 0]); }
  // helpers to fill a pose: angles about model x (pitch), y, z
  function arm(p, s, raise, fwd, elbow, elbowFwd = 0, twist = 0, wrist = 0) {
    const [sh, up, lo, ha] = ARM[s];
    p[sh][1] += s * raise * 0.18; p[sh][2] += s * fwd * 0.15;
    p[up][1] += s * raise; p[up][2] += s * fwd; p[up][0] += twist;
    p[lo][1] += s * elbow; p[lo][2] += s * elbowFwd;
    p[ha][1] += s * wrist;
  }
  function leg(p, s, flex, spread, knee, foot = 0) {
    const [hip, kn, ft] = LEG[s];
    p[hip][0] += -flex; p[hip][1] += s * spread;
    p[kn][0] += knee; p[ft][0] += foot;
  }
  function spine(p, curl, twist = 0, side = 0) { for (const b of [1, 2, 3]) { p[b][0] += curl / 3; p[b][2] += twist / 3; p[b][1] += side / 3; } }
  function head(p, nod, turn, tilt = 0) { p[4][0] += nod * .35; p[4][2] += turn * .35; p[4][1] += tilt * .35; p[5][0] += nod * .65; p[5][2] += turn * .65; p[5][1] += tilt * .65; }

  // ---- the poses (all relative to the T-pose the model was rigged in) ----
  const POSE = {
    // stable belly-down fall ("box position"): arched back, arms out with forearms up, knees bent, legs apart
    arch(p, t) {
      spine(p, -.24 + .03 * Math.sin(t * 1.3)); head(p, -.32, .1 * Math.sin(t * .4));
      const b = .06 * Math.sin(t * 1.7);
      arm(p, L, .12 + b, .22, 1.05 + b, 0, .15); arm(p, R, .12 - b, .22, 1.05 - b, 0, -.15);
      leg(p, L, -.12, .24, .85 + .08 * Math.sin(t * 1.1)); leg(p, R, -.12, .24, .85 + .08 * Math.sin(t * 1.1 + 2));
    },
    // panicked: arms windmill, legs bicycle, head shaking
    flail(p, t) {
      const w = 7.2;
      spine(p, .05 * Math.sin(t * w * .5), .18 * Math.sin(t * w * .5), .08 * Math.sin(t * w * .25));
      head(p, -.15 + .12 * Math.sin(t * w * .7), .35 * Math.sin(t * w * .45), .12 * Math.sin(t * w * .6));
      arm(p, L, .45 + .55 * Math.sin(t * w), .45 * Math.cos(t * w), .45 + .35 * Math.sin(t * w + 1.2), .2);
      arm(p, R, .45 + .55 * Math.sin(t * w + Math.PI), .45 * Math.cos(t * w + Math.PI), .45 + .35 * Math.sin(t * w + 4.3), .2);
      const k = 5.4;
      leg(p, L, .25 + .4 * Math.sin(t * k), .18, .55 + .45 * Math.sin(t * k + 1.3));
      leg(p, R, .25 + .4 * Math.sin(t * k + Math.PI), .18, .55 + .45 * Math.sin(t * k + Math.PI + 1.3));
    },
    // tucked into a ball, tumbling: knees up, arms round the knees, chin down
    curl(p, t) {
      spine(p, .62); head(p, .38, 0);
      arm(p, L, -.35, .95, 0, 1.05, .3); arm(p, R, -.35, .95, 0, 1.05, -.3);
      leg(p, L, 1.0, .08, 1.25); leg(p, R, 1.0, .08, 1.25);
    },
    // reaching up for a star with his left hand, the other arm out for balance, looking at the hand
    reach(p, t) {
      spine(p, -.12, .15, -.1); head(p, -.35, .35, -.15);
      arm(p, L, 1.0 + .06 * Math.sin(t * 3), .35, .15, 0, 0, .2 * Math.sin(t * 5));
      arm(p, R, -.1, .15, .45, 0);
      leg(p, L, .1, .15, .45); leg(p, R, -.05, .2, .8);
    },
    // a big wave with his right hand
    wave(p, t) {
      spine(p, -.08, -.12); head(p, -.1, -.2, .12);
      arm(p, R, .85, .2, .85 + .45 * Math.sin(t * 9), 0, 0, .35 * Math.sin(t * 9 + .6));
      arm(p, L, .05, .2, .5);
      leg(p, L, .05, .18, .6); leg(p, R, .15, .18, .4);
    },
    // flattened against the screen edge: spread-eagle, head thrown back
    splat(p, t) {
      spine(p, -.3); head(p, -.4, 0);
      arm(p, L, .55, -.15, .12); arm(p, R, .55, -.15, .12);
      leg(p, L, -.15, .42, .15); leg(p, R, -.15, .42, .15);
    },
    // a thumbs-up and a little nod: he's fine, actually
    thumbs(p, t) {
      spine(p, -.1); head(p, -.12 + .12 * Math.max(0, Math.sin(t * 6)), .15);
      arm(p, R, .2, .85, .2, .9, -.4); arm(p, L, .1, .25, .6);
      leg(p, L, .2, .15, .7); leg(p, R, .05, .2, .5);
    },
    // swimming: breaststroke arms, frog-kick legs (going nowhere)
    swim(p, t) {
      const w = 3.4, s = Math.sin(t * w), c = Math.cos(t * w);
      spine(p, -.15 + .08 * s); head(p, -.3 + .1 * s, 0);
      arm(p, L, .35 + .35 * c, .5 + .45 * s, .35 + .3 * c, .3); arm(p, R, .35 + .35 * c, .5 + .45 * s, .35 + .3 * c, .3);
      leg(p, L, .35 + .3 * c, .3 + .15 * s, .9 + .4 * c); leg(p, R, .35 + .3 * c, .3 + .15 * s, .9 + .4 * c);
    },
  };

  class Rig {
    constructor() {
      this.ang = zero(); this.vel = zero(); this.tgt = zero();
      this.mats = new Float32Array(NB * 12);
      this.mode = 'arch'; this.modeT = 0; this.t = 0;
      this.stiff = 9;           // spring frequency (rad/s); high = snappy, low = floaty
      this.kick = 0;
    }
    set(mode, stiff = 9) { if (mode !== this.mode) { this.mode = mode; this.modeT = 0; } this.stiff = stiff; }
    // a jolt: every joint gets a random push (impacts, pokes)
    jolt(k) { for (let i = 0; i < NB; i++) for (let a = 0; a < 3; a++) this.vel[i][a] += (Math.random() - .5) * k * (i === 0 ? .3 : 1); }
    update(dt, bass) {
      this.t += dt; this.modeT += dt;
      const p = zero();
      POSE[this.mode](p, this.t);
      // always alive: breathing and a slow drift of the arms and legs
      p[2][0] += .02 * Math.sin(this.t * 1.9); p[3][0] += .02 * Math.sin(this.t * 1.9);
      if (bass > .02) { head(p, .12 * bass, 0); spine(p, .1 * bass); }
      for (let i = 0; i < NB; i++) for (let a = 0; a < 3; a++) {
        const lim = LIM[i][a];
        const g = Math.max(-lim, Math.min(lim, p[i][a]));
        // critically damped spring towards the target angle
        const w = this.stiff * (i >= 14 ? .8 : 1);
        const x = this.ang[i][a], v = this.vel[i][a];
        const acc = w * w * (g - x) - 2 * w * v;
        this.vel[i][a] = v + acc * dt;
        this.ang[i][a] = Math.max(-lim * 1.15, Math.min(lim * 1.15, x + this.vel[i][a] * dt));
      }
      this.compose();
    }
    compose() {
      const W = this.world || (this.world = Array.from({ length: NB }, () => new Float32Array(12)));
      const Lm = new Float32Array(12);
      for (let i = 0; i < NB; i++) {
        const [rx, ry, rz] = this.ang[i];
        const cx = Math.cos(rx), sx = Math.sin(rx), cy = Math.cos(ry), sy = Math.sin(ry), cz = Math.cos(rz), sz = Math.sin(rz);
        // R = Rz * Ry * Rx
        const r0 = cz * cy, r1 = cz * sy * sx - sz * cx, r2 = cz * sy * cx + sz * sx;
        const r3 = sz * cy, r4 = sz * sy * sx + cz * cx, r5 = sz * sy * cx - cz * sx;
        const r6 = -sy, r7 = cy * sx, r8 = cy * cx;
        const [hx, hy, hz] = PIV[i];
        Lm[0] = r0; Lm[1] = r1; Lm[2] = r2; Lm[3] = hx - (r0 * hx + r1 * hy + r2 * hz);
        Lm[4] = r3; Lm[5] = r4; Lm[6] = r5; Lm[7] = hy - (r3 * hx + r4 * hy + r5 * hz);
        Lm[8] = r6; Lm[9] = r7; Lm[10] = r8; Lm[11] = hz - (r6 * hx + r7 * hy + r8 * hz);
        const pa = PAR[i], o = W[i];
        if (pa < 0) o.set(Lm); else {
          const a = W[pa];
          for (let r = 0; r < 3; r++) {
            const a0 = a[r * 4], a1 = a[r * 4 + 1], a2 = a[r * 4 + 2];
            o[r * 4] = a0 * Lm[0] + a1 * Lm[4] + a2 * Lm[8];
            o[r * 4 + 1] = a0 * Lm[1] + a1 * Lm[5] + a2 * Lm[9];
            o[r * 4 + 2] = a0 * Lm[2] + a1 * Lm[6] + a2 * Lm[10];
            o[r * 4 + 3] = a0 * Lm[3] + a1 * Lm[7] + a2 * Lm[11] + a[r * 4 + 3];
          }
        }
        this.mats.set(o, i * 12);
      }
    }
  }
  return { Rig, NB, POSES: Object.keys(POSE) };
})();
