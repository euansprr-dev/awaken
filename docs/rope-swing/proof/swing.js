// The rope plan's proof (docs/rope-swing). Today's rope ported line for line from AscentRules, AscentAnchors and
// AscentMoves (as of 225dbdf2, 2026-10-28), and the proposed assisted swing, both in 3D over a world of boxes
// (x across, y up, z ahead; blocks and ticks). `node docs/rope-swing/proof/run.js` plays a bot on each scene.
// Left out: vanilla's exact collision (here a 0.6 x 1.8 box moved axis by axis against boxes), the network (the
// server only checks the anchor is a real block in reach and the speed caps), and the Runner (no wall-run hand-off).
(function (root) {
  'use strict';
  const G = 0.08, HAND = 1.55, EYE = 1.62, HALF = 0.3, TALL = 1.8, D2R = Math.PI / 180;

  // ---- a seeded random (scenes are the same every run) ------------------------------------------------------------
  function rng(seed) {
    let s = seed >>> 0;
    return () => { s = (s * 1664525 + 1013904223) >>> 0; return s / 4294967296; };
  }

  // ---- scenes: boxes [x0, y0, z0, x1, y1, z1]; kind 'leaf' boxes are crowns (a rope bites them, as in the game) ----
  function box(x0, y0, z0, x1, y1, z1, kind) { return {a: [x0, y0, z0], b: [x1, y1, z1], kind: kind || 'stone'}; }
  const SCENES = {
    // the sandbox lane (AscentSandbox: 14 giants 17 apart, 6-10 off the lane, alternating sides, 26-38 tall, a crown)
    lane: {name: 'The sandbox lane', start: [0.5, 46, 0.5], launch: true, build() {
      const r = rng(7), bx = [box(-4.5, 0, -4.5, 5.5, 46, 5.5)];
      for (let i = 0; i < 14; i++) {
        const along = 18 + i * 17 + Math.floor(r() * 5) - 2, side = (i % 2 === 0 ? 1 : -1) * (6 + Math.floor(r() * 5));
        const h = 26 + Math.floor(r() * 13);
        bx.push(box(side - 1, 0, along - 1, side + 2, h, along + 2, 'log'));
        bx.push(box(side - 6, h - 3, along - 6, side + 7, h + 5, along + 7, 'leaf'));
      }
      return bx;
    }},
    // a city avenue: towers both sides with alleys, a sky bridge, a tower closing the end
    city: {name: 'City avenue', start: [0.5, 0, 0.5], build() {
      const r = rng(11), bx = [];
      for (const side of [-1, 1]) {
        let z = 8;
        while (z < 230) {
          const len = 14 + Math.floor(r() * 8), h = 22 + Math.floor(r() * 18);
          bx.push(side < 0 ? box(-26, 0, z, -8, h, z + len) : box(8, 0, z, 26, h, z + len));
          z += len + 5 + Math.floor(r() * 3);
        }
      }
      bx.push(box(-8, 15, 96, 8, 18, 100));      // a sky bridge across the avenue
      bx.push(box(-26, 0, 238, 26, 40, 256));   // the tower that closes the avenue
      return bx;
    }},
    // a forest of ordinary big trees (trunks 1 wide, crowns 7 wide), denser and lower than the lane
    forest: {name: 'Forest', start: [0.5, 0, 0.5], build() {
      const r = rng(5), bx = [];
      for (let z = 10; z < 240; z += 7 + Math.floor(r() * 5)) {
        for (let k = 0; k < 2; k++) {
          const x = (r() - 0.5) * 34, h = 13 + Math.floor(r() * 9);
          if (Math.abs(x) < 1.5 && z < 14) continue;
          bx.push(box(Math.floor(x), 0, z, Math.floor(x) + 1, h, z + 1, 'log'));
          bx.push(box(Math.floor(x) - 3, h - 2, z - 3, Math.floor(x) + 4, h + 3, z + 4, 'leaf'));
        }
      }
      return bx;
    }},
    // a cliff ahead: a wall 30 high across the way at z 60, its lip overhanging by 3
    cliff: {name: 'A cliff ahead', start: [0.5, 0, 0.5], build() {
      return [box(-80, 0, 60, 80, 30, 200), box(-80, 25, 57, 80, 30, 60)];
    }},
    // open plains: a boulder or two, nothing above you
    plains: {name: 'Open plains', start: [0.5, 0, 0.5], build() {
      return [box(-3, 0, 70, 1, 3, 74), box(6, 0, 150, 9, 2, 153)];
    }},
    // a lone spire dead ahead, 46 away and 40 tall: what Euan aims at
    spire: {name: 'A spire ahead', start: [0.5, 0, 0.5], build() {
      return [box(-2, 0, 46, 2, 40, 50)];
    }},
  };

  function World(sc) {
    this.boxes = sc.build();
  }
  World.prototype.overlaps = function (x0, y0, z0, x1, y1, z1) {
    if (y0 < 0) return true;
    for (const b of this.boxes) {
      if (x1 > b.a[0] && x0 < b.b[0] && y1 > b.a[1] && y0 < b.b[1] && z1 > b.a[2] && z0 < b.b[2]) return true;
    }
    return false;
  };
  World.prototype.bodyHits = function (x, y, z) {
    return this.overlaps(x - HALF, y + 0.001, z - HALF, x + HALF, y + TALL, z + HALF);
  };
  // a ray against every box (slabs) and the ground: the hit point 0.04 off the face, the face's normal, and its box
  World.prototype.ray = function (o, d, range) {
    let best = range, n = null, hitBox = null;
    if (d[1] < 0) {
      const t = -o[1] / d[1];
      if (t > 0 && t < best) { best = t; n = [0, 1, 0]; hitBox = null; }
    }
    for (const b of this.boxes) {
      let t0 = 0, t1 = best, axis = -1, sign = 0;
      let ok = true;
      for (let i = 0; i < 3; i++) {
        if (Math.abs(d[i]) < 1e-12) {
          if (o[i] < b.a[i] || o[i] > b.b[i]) { ok = false; break; }
          continue;
        }
        let ta = (b.a[i] - o[i]) / d[i], tb = (b.b[i] - o[i]) / d[i], s = -1;
        if (ta > tb) { const q = ta; ta = tb; tb = q; s = 1; }
        if (ta > t0) { t0 = ta; axis = i; sign = s; }
        if (tb < t1) t1 = tb;
        if (t0 > t1) { ok = false; break; }
      }
      if (ok && axis >= 0 && t0 < best && t0 > 1e-6) { best = t0; n = [0, 0, 0]; n[axis] = sign; hitBox = b; }
    }
    if (!n) return null;
    return {x: o[0] + d[0] * best + n[0] * 0.04, y: o[1] + d[1] * best + n[1] * 0.04, z: o[2] + d[2] * best + n[2] * 0.04,
      n, t: best, box: hitBox};
  };
  World.prototype.clear = function (p, q, skipEnd) {
    const d = [q[0] - p[0], q[1] - p[1], q[2] - p[2]], l = Math.hypot(d[0], d[1], d[2]);
    const h = this.ray(p, [d[0] / l, d[1] / l, d[2] / l], l);
    return !h || h.t >= l - (skipEnd || 0.3) || (h.box && h.box.kind === 'leaf');
  };
  // the floor under (x, z) from 'from' down: the highest top under it
  World.prototype.floorUnder = function (x, from, z) {
    let f = 0;
    for (const b of this.boxes) {
      if (x >= b.a[0] - HALF && x <= b.b[0] + HALF && z >= b.a[2] - HALF && z <= b.b[2] + HALF && b.b[1] <= from + 1e-6) f = Math.max(f, b.b[1]);
    }
    return f;
  };

  // ---- the body: feet point, velocity, vanilla-like collision ----------------------------------------------------
  // moves (dx, dy, dz) axis by axis; the speed a wall took (horizontal) is returned, a floor sets onGround
  function move(w, b, dx, dy, dz) {
    const steps = Math.max(1, Math.ceil(Math.max(Math.abs(dx), Math.abs(dy), Math.abs(dz)) / 0.12));
    let hx = false, hy = false, hz = false;
    for (let i = 0; i < steps; i++) {
      if (!hy) { const ny = b.y + dy / steps; if (w.bodyHits(b.x, ny, b.z)) hy = true; else b.y = ny; }
      if (!hx) { const nx = b.x + dx / steps; if (w.bodyHits(nx, b.y, b.z)) hx = true; else b.x = nx; }
      if (!hz) { const nz = b.z + dz / steps; if (w.bodyHits(b.x, b.y, nz)) hz = true; else b.z = nz; }
    }
    const before = Math.hypot(b.vx, b.vz);
    if (hx) b.vx = 0;
    if (hz) b.vz = 0;
    b.onGround = hy && dy < 0;
    if (hy) b.vy = 0;
    return before - Math.hypot(b.vx, b.vz);
  }

  // ---- today's rope ------------------------------------------------------------------------------------------------
  const T = {RANGE: 44, MIN: 3, DRAG: 0.0025, PUMP: 0.016, STEER: 0.012, FIT_REEL: 1.2, YANK: 0.22, CLEAR: 1.8,
    MIN_RISE: 2, MAX: 2.6, AIMED_RISE: 6, ZIP_MIN: 4, ZIP_ACCEL: 0.24, ZIP_SPEED: 1.45, ZIP_ARRIVE: 2.2,
    RELEASE: 1.06, CARRY_H: 0.988, CARRY_V: 0.98, ZIP_UP_MIN_RISE: 6, ZIP_UP_SLOPE: 0.45};
  const fx = (yaw) => -Math.sin(yaw * D2R), fz = (yaw) => Math.cos(yaw * D2R);
  function lookDir(yaw, pitch) {
    const cp = Math.cos(pitch * D2R);
    return [fx(yaw) * cp, -Math.sin(pitch * D2R), fz(yaw) * cp];
  }

  // AscentAnchors.findAimed / score (pitch: Minecraft's, negative looks up)
  function todayAnchor(w, eye, yaw, pitch) {
    const hit = w.ray(eye, lookDir(yaw, pitch), T.RANGE);
    let fallback = null;
    if (hit) {
      const rise = hit.y - eye[1], dist = Math.hypot(hit.x - eye[0], hit.y - eye[1], hit.z - eye[2]);
      if (rise >= T.AIMED_RISE) return {x: hit.x, y: hit.y, z: hit.z, aimed: true};
      if (rise < T.MIN_RISE && dist >= T.ZIP_MIN) return {x: hit.x, y: hit.y, z: hit.z, aimed: true};
      if (dist >= T.ZIP_MIN) fallback = {x: hit.x, y: hit.y, z: hit.z, aimed: true};
    }
    let best = null, bs = 0;
    for (const up of [20, 30, 40, 50, 62]) for (const dy of [0, -14, 14, -28, 28]) {
      const h = w.ray(eye, lookDir(yaw + dy, -up), T.RANGE);
      if (!h) continue;
      const s = todayScore(h, eye, yaw);
      if (s > bs) { bs = s; best = {x: h.x, y: h.y, z: h.z, aimed: false}; }
    }
    return best || fallback;
  }
  function todayScore(a, eye, yaw) {
    const dx = a.x - eye[0], dy = a.y - eye[1], dz = a.z - eye[2], h = Math.hypot(dx, dz);
    if (dy < 4 || h < 3) return 0;
    const ahead = (dx * fx(yaw) + dz * fz(yaw)) / h;
    if (ahead < 0.45) return 0;
    const side = Math.abs(dx * -fz(yaw) + dz * fx(yaw));
    const sideFit = 0.25 + 0.75 * Math.min(1, Math.max(0, (side - 1.5) / 2)) * (1 - Math.min(1, Math.max(0, (side - 9) / 6)));
    return ahead * Math.exp(-(((dy - 14) / 9) ** 2)) * Math.exp(-(((h - 13) / 9) ** 2)) * sideFit;
  }

  function rotate(x, y, z, kx, ky, kz, c, s) {
    const cx = ky * z - kz * y, cy = kz * x - kx * z, cz = kx * y - ky * x, d = kx * x + ky * y + kz * z;
    return [x * c + cx * s + kx * d * (1 - c), y * c + cy * s + ky * d * (1 - c), z * c + cz * s + kz * d * (1 - c)];
  }
  function cap(k, max) {
    const s = Math.hypot(k.vx, k.vy, k.vz);
    if (s > max) { k.vx *= max / s; k.vy *= max / s; k.vz *= max / s; }
  }
  // one taut step along the great circle (AscentRules.tetherStep's tail), shared by both models
  function circle(k, ax, ay, az, len, nx, ny, nz) {
    const s = Math.hypot(k.vx, k.vy, k.vz);
    if (s > 1e-9) {
      const axx = (ny * k.vz - nz * k.vy) / s, axy = (nz * k.vx - nx * k.vz) / s, axz = (nx * k.vy - ny * k.vx) / s;
      const al = Math.hypot(axx, axy, axz) || 1, ang = s / len, c = Math.cos(ang), sn = Math.sin(ang);
      const p = rotate(nx * len, ny * len, nz * len, axx / al, axy / al, axz / al, c, sn);
      k.x = ax + p[0]; k.y = ay + p[1]; k.z = az + p[2];
      const v = rotate(k.vx, k.vy, k.vz, axx / al, axy / al, axz / al, c, sn);
      k.vx = v[0]; k.vy = v[1]; k.vz = v[2];
    } else { k.x = ax + nx * len; k.y = ay + ny * len; k.z = az + nz * len; }
    return s;
  }
  // AscentRules.tetherStep
  function tetherStep(k, ax, ay, az, len, yaw, fwd, right) {
    k.vy -= G;
    const rx = k.x - ax, ry = k.y - ay, rz = k.z - az, r = Math.hypot(rx, ry, rz);
    const nx = rx / r, ny = ry / r, nz = rz / r, below = -ny;
    if (fwd > 0 && below > 0.35) {
      let hx = k.vx, hz = k.vz, h = Math.hypot(hx, hz);
      if (h < 0.08) { hx = fx(yaw); hz = fz(yaw); h = 1; }
      const g = T.PUMP * fwd * Math.min(1, (below - 0.35) / 0.5);
      k.vx += hx / h * g; k.vz += hz / h * g;
    }
    if (right) { k.vx += -fz(yaw) * right * T.STEER; k.vz += fx(yaw) * right * T.STEER; }
    k.vx *= 1 - T.DRAG; k.vy *= 1 - T.DRAG; k.vz *= 1 - T.DRAG;
    const out = k.vx * nx + k.vy * ny + k.vz * nz;
    if (r >= len - 1e-6 && out > 0) {
      k.vx -= nx * out; k.vy -= ny * out; k.vz -= nz * out;
      cap(k, T.MAX);
      const s = circle(k, ax, ay, az, len, nx, ny, nz);
      return out + s * s / len;
    }
    k.x += k.vx; k.y += k.vy; k.z += k.vz;
    return catchOn(k, ax, ay, az, len, 0);
  }
  // a slack rope snapping taut; 'keep' turns that share of the lost outward speed back into speed along the swing
  function catchOn(k, ax, ay, az, len, keep) {
    const dx = k.x - ax, dy = k.y - ay, dz = k.z - az, d = Math.hypot(dx, dy, dz);
    if (d <= len) return 0;
    const ux = dx / d, uy = dy / d, uz = dz / d;
    k.x = ax + ux * len; k.y = ay + uy * len; k.z = az + uz * len;
    const o = k.vx * ux + k.vy * uy + k.vz * uz;
    if (o <= 0) return 0;
    k.vx -= ux * o; k.vy -= uy * o; k.vz -= uz * o;
    if (keep > 0) {
      const s = Math.hypot(k.vx, k.vy, k.vz);
      if (s > 1e-6) { const f = (s + o * keep) / s; k.vx *= f; k.vy *= f; k.vz *= f; }
    }
    return o;
  }
  // AscentRules.reel
  function reel(k, ax, ay, az, len, by) {
    const nl = Math.max(T.MIN, len - by);
    const rx = k.x - ax, ry = k.y - ay, rz = k.z - az, r = Math.hypot(rx, ry, rz);
    if (r < len - 0.05) return nl;
    const ux = rx / r, uy = ry / r, uz = rz / r, out = k.vx * ux + k.vy * uy + k.vz * uz;
    const tx = k.vx - ux * out, ty = k.vy - uy * out, tz = k.vz - uz * out, f = Math.min(len / nl, 1.15);
    k.vx = tx * f - ux * (len - nl) * 0.6; k.vy = ty * f - uy * (len - nl) * 0.6; k.vz = tz * f - uz * (len - nl) * 0.6;
    cap(k, T.MAX);
    return nl;
  }
  function angleFromBottom(k, ax, ay, az) {
    const rx = k.x - ax, ry = k.y - ay, rz = k.z - az, r = Math.hypot(rx, ry, rz);
    return Math.acos(Math.max(-1, Math.min(1, -ry / r))) / D2R;
  }
  // AscentRules.releaseQuality
  function releaseQuality(k, ax, ay, az) {
    const rx = k.x - ax, rz = k.z - az, s = Math.hypot(k.vx, k.vy, k.vz);
    if (s < 0.3 || k.vy <= 0 || rx * k.vx + rz * k.vz <= 0) return 0;
    return Math.max(0, Math.min(1, 1 - Math.abs(angleFromBottom(k, ax, ay, az) - 52) / 30));
  }

  // ---- the assisted swing (the proposal; numbers are the plan's starting tune) -----------------------------------
  const A = {
    RANGE: 44,
    // the search: rays from the hand, 22-78 degrees up, either side of where you're heading
    UPS: [22, 32, 42, 52, 62, 72], YAWS: [0, -12, 12, -24, 24, -36, 36, -50, 50, -66, 66],
    // a good swing: the rope 56 degrees above level (40-74), 18 long (9-32), 3-12 off your line to one side
    ELEV_BEST: 56, ELEV_SPREAD: 15, LEN_BEST: 18, LEN_SPREAD: 9, LEN_MIN: 8, SIDE_BEST: 7,
    // the arc's lowest point keeps the feet this far over whatever is under it
    BOTTOM_CLEAR: 2.5,
    // a face hit within this of its top edge moves the anchor up to that edge (roof lines and lips read as anchors)
    EDGE_SNAP: 5,
    // the catch keeps this share of the speed the rope takes outward (no stop dead)
    CATCH_KEEP: 0.65,
    // the swing's own drive along your heading through the lower half (no key needed), and forward's pump on top
    DRIVE: 0.011, PUMP: 0.012, DRAG: 0.002, MAX: 2.6, ENTRY_FLOOR: 0.75, FLOOR_SPEED: 0.9,
    // the swing keeps you heading where you look: sideways sway damped this much a tick (into forward speed)
    STRAIGHTEN: 0.07, TURN: 2.5,
    // the rope shortens to its fitted length on the way down at most this a tick, speed kept (never a pull in)
    FIT: 0.4,
    // the window: rising, best 62 degrees past the bottom, good within 18
    WIN_BEST: 62, WIN_GOOD: 18, BOOST_PERFECT: 1.14, LIFT_PERFECT: 0.08, BOOST_GOOD: 1.06,
    // the rope lets go on its own past this (you never swing back into the anchor)
    APEX: 95,
    // the look ahead along the arc for walls (ticks)
    LOOKAHEAD: 10,
  };

  // The anchor's sideways offset from the line you're heading along (signed, + to your left) and its point projected
  // onto that line's vertical plane: the swing's pivot. The rope still runs to the real anchor (the server checks it);
  // the body swings in the plane, so an anchor on a tower beside you never swings you into that tower.
  function pivot(k, ax, ay, az, heading, out) {
    const hx = fx(heading), hz = fz(heading), lx = -hz, lz = hx;
    const d = (ax - k.x) * lx + (az - k.z) * lz;
    out.px = ax - lx * d; out.py = ay; out.pz = az - lz * d; out.d = d;
    return out;
  }

  // the swing a candidate anchor would give: its rope fitted over the floor under your path, or null
  function plan(w, hand, v, heading, a) {
    const dx = a.x - hand[0], dy = a.y - hand[1], dz = a.z - hand[2];
    const len = Math.hypot(dx, dy, dz), h = Math.hypot(dx, dz);
    if (dy < 4 || len > A.RANGE || len < A.LEN_MIN) return null;
    const hx = fx(heading), hz = fz(heading);
    const ahead = dx * hx + dz * hz, side = Math.abs(dx * -hz + dz * hx);
    if (ahead < 2 || side > 16) return null;
    // the swing in your plane: its radius and the angle the rope makes over level there
    const elev = Math.atan2(dy, Math.max(0.01, ahead)) / D2R;
    if (elev < 30 || elev > 82) return null;
    const px = hand[0] + hx * ahead, pz = hand[2] + hz * ahead;
    let floor = 0;
    for (const o of [[0, 0], [0, 1.5], [0, -1.5], [0, 3]]) floor = Math.max(floor, w.floorUnder(px + hx * o[1], a.y - 1.2, pz + hz * o[1]));
    // the radius that keeps the feet BOTTOM_CLEAR over that floor, as a rope length to the real anchor
    const rad = Math.min(Math.hypot(dy, ahead), a.y - floor - A.BOTTOM_CLEAR - HAND);
    const fitLen = Math.min(len, Math.hypot(rad, side));
    if (rad < A.LEN_MIN - 2 || fitLen < len * 0.7) return null;
    const s = Math.exp(-(((elev - A.ELEV_BEST) / A.ELEV_SPREAD) ** 2)) * Math.exp(-(((rad - A.LEN_BEST) / A.LEN_SPREAD) ** 2))
      * (0.45 + 0.55 * Math.exp(-(((side - A.SIDE_BEST) / 5) ** 2)));
    return {x: a.x, y: a.y, z: a.z, len, fitLen, score: s, elev, side, aimed: false};
  }

  // the swing's first ticks played forward: false if the body would meet something before the window
  function arcClear(w, hand, v, c, heading) {
    const k = {x: hand[0], y: hand[1], z: hand[2], vx: v[0], vy: v[1], vz: v[2]};
    const rope = {ax: c.x, ay: c.y, az: c.z, len: c.len, fitLen: c.fitLen, caught: false};
    for (let i = 0; i < 40; i++) {
      assistStep(k, rope, heading, 0);
      if (w.bodyHits(k.x, k.y - HAND, k.z)) return false;
      if (windowQuality(k, rope, heading) > 0.5) return true;
    }
    return true;
  }

  function assistAnchor(w, hand, v, heading) {
    const cands = [];
    for (const up of A.UPS) for (const dyaw of A.YAWS) {
      const h = w.ray(hand, lookDir(heading + dyaw, -up), A.RANGE);
      if (!h) continue;
      let a = {x: h.x, y: h.y, z: h.z};
      // a tower's or a trunk's side: up to its top edge if that's close (a roof line, a lip)
      if (h.n[1] === 0 && h.box && h.box.b[1] - h.y < A.EDGE_SNAP && h.box.kind !== 'leaf') a = {x: h.x, y: h.box.b[1] - 0.3, z: h.z};
      const c = plan(w, hand, v, heading, a);
      if (c && w.clear(hand, [a.x, a.y, a.z], 0.5)) cands.push(c);
    }
    cands.sort((p, q) => q.score - p.score);
    for (let i = 0; i < Math.min(4, cands.length); i++) if (arcClear(w, hand, v, cands[i], heading)) return cands[i];
    return null;
  }

  // the fallback when nothing swings: the look's own ray on a face whose top has room over it (a lip, a ledge):
  // a zip up to it and over (today's zip to a top, SPEC §17.6), else nothing
  function zipFallback(w, eye, yaw, pitch) {
    for (let p = Math.min(pitch, -5); p >= -72; p -= 6) {
      const h = w.ray(eye, lookDir(yaw, p), A.RANGE);
      if (!h || !h.box || h.box.kind === 'leaf' || h.n[1] !== 0) continue;
      const top = h.box.b[1];
      if (top - h.y > 6 || top < eye[1] + 2) continue;
      // room over the top just past the lip
      const tx = h.x - h.n[0] * 0.8, tz = h.z - h.n[2] * 0.8;
      if (w.overlaps(tx - 0.3, top + 0.01, tz - 0.3, tx + 0.3, top + 2, tz + 0.3)) continue;
      if (!w.clear(eye, [h.x, top - 0.3, h.z], 0.5)) continue;
      return {x: h.x, y: top - 0.3, z: h.z, top, nx: h.n[0], nz: h.n[2]};
    }
    return null;
  }

  function windowQuality(k, rope, heading) {
    const P = pivot(k, rope.ax, rope.ay, rope.az, heading, PV);
    const rx = k.x - P.px, rz = k.z - P.pz, ry = k.y - P.py, r = Math.hypot(rx, ry, rz);
    if (k.vy <= 0 || rx * k.vx + rz * k.vz <= 0) return 0;
    const ang = Math.acos(Math.max(-1, Math.min(1, -ry / r))) / D2R;
    return Math.max(0, 1 - Math.abs(ang - A.WIN_BEST) / A.WIN_GOOD);
  }
  const PV = {px: 0, py: 0, pz: 0, d: 0};

  // One assisted tick. The body swings in the vertical plane of the heading about the anchor's projection on it
  // (radius from the real rope: sqrt(len^2 - d^2)); the heading turns toward the look; through the lower half the
  // swing drives you on; the rope shortens toward its fitted length on the way down, speed kept, never a pull in.
  function assistStep(k, rope, heading, fwd) {
    const hx = fx(heading), hz = fz(heading), lx = -hz, lz = hx;
    // straighten: the sideways speed goes into speed along the heading (a turn of the look turns the swing)
    const sideV = k.vx * lx + k.vz * lz;
    k.vx -= lx * sideV; k.vz -= lz * sideV;
    const along = k.vx * hx + k.vz * hz;
    if (along >= 0) { k.vx += hx * Math.abs(sideV) * 0.85; k.vz += hz * Math.abs(sideV) * 0.85; }
    k.vy -= G;
    if (rope.len > rope.fitLen + 1e-3 && k.vy < 0) rope.len = Math.max(rope.fitLen, rope.len - A.FIT);
    const P = pivot(k, rope.ax, rope.ay, rope.az, heading, PV);
    const rad = Math.sqrt(Math.max(0.25, rope.len * rope.len - P.d * P.d));
    const rx = k.x - P.px, ry = k.y - P.py, rz = k.z - P.pz, r = Math.hypot(rx, ry, rz);
    const nx = rx / r, ny = ry / r, nz = rz / r, below = -ny;
    if (below > 0.2) {
      const o = hx * nx + hz * nz;
      const tx = hx - nx * o, ty = -ny * o, tz = hz - nz * o, tl = Math.hypot(tx, ty, tz);
      if (tl > 1e-3) {
        let g = (A.DRIVE + A.PUMP * Math.max(0, fwd)) * Math.min(1, (below - 0.2) / 0.5);
        // a swing too slow to reach its window gets more drive (the momentum floor: never a dead hang)
        if (Math.hypot(k.vx, k.vy, k.vz) < A.FLOOR_SPEED && (k.vx * hx + k.vz * hz) >= 0) g *= 3;
        k.vx += tx / tl * g; k.vy += ty / tl * g; k.vz += tz / tl * g;
      }
    }
    k.vx *= 1 - A.DRAG; k.vy *= 1 - A.DRAG; k.vz *= 1 - A.DRAG;
    const out = k.vx * nx + k.vy * ny + k.vz * nz;
    if (r >= rad - 1e-6 && out > 0) {
      const s0 = Math.hypot(k.vx, k.vy, k.vz);
      k.vx -= nx * out; k.vy -= ny * out; k.vz -= nz * out;
      if (!rope.caught) {
        rope.caught = true;
        const s = Math.hypot(k.vx, k.vy, k.vz), keep = Math.min(s0, s + out * A.CATCH_KEEP);
        if (s > 1e-6) { const f = keep / s; k.vx *= f; k.vy *= f; k.vz *= f; }
      }
      cap(k, A.MAX);
      circle(k, P.px, P.py, P.pz, rad, nx, ny, nz);
      return;
    }
    k.x += k.vx; k.y += k.vy; k.z += k.vz;
    if (catchOn(k, P.px, P.py, P.pz, rad, rope.caught ? 0 : A.CATCH_KEEP) > 0) rope.caught = true;
  }

  // a wall in the next LOOKAHEAD ticks of this swing: the tick it would meet it, or -1
  function wallAhead(w, k, rope, heading) {
    const s = {x: k.x, y: k.y, z: k.z, vx: k.vx, vy: k.vy, vz: k.vz};
    const r2 = {ax: rope.ax, ay: rope.ay, az: rope.az, len: rope.len, fitLen: rope.fitLen, caught: true};
    for (let i = 0; i < A.LOOKAHEAD; i++) {
      assistStep(s, r2, heading, 0);
      if (w.bodyHits(s.x, s.y - HAND, s.z)) return i;
    }
    return -1;
  }

  // ---- the sim: one body, one model, stepped by inputs (a player's keys in the page's demo, a bot in the proof) ----
  // input: {rope: held, fwd: -1..1, yaw, pitch}. Today: a press throws (a miss needs a new press), letting go releases.
  // Assisted: held, it bites the first good anchor and keeps looking after each let-go (hold to chain).
  function Sim(sceneKey, model, opts) {
    opts = opts || {};
    const sc = SCENES[sceneKey];
    this.sc = sc; this.scene = sceneKey; this.model = model; this.w = new World(sc);
    this.b = {x: sc.start[0] + (opts.x0 || 0), y: sc.start[1], z: sc.start[2] + (opts.z0 || 0), vx: 0, vy: 0,
      vz: (sc.launch ? 0.4 : 0.36) * (opts.speed || 1), onGround: true};
    this.z0 = this.b.z; this.t = 0; this.state = 'run'; this.rope = null; this.k = null; this.mode = '';
    this.travel = 0; this.ropeStart = 0; this.cooldown = 0; this.groundTicks = 0; this.pull = null; this.wasDown = false;
    this.path = []; this.ropes = []; this.events = []; this.bonks = 0; this.worst = 0; this.swings = 0; this.perfect = 0;
    this.stuck = 0; this.lastZ = this.b.z; this.lastQ = 0; this.marker = null; this.flash = null;
  }
  Sim.prototype.note = function (what) {
    const b = this.b;
    this.events.push({t: this.t, x: +b.x.toFixed(1), y: +b.y.toFixed(1), z: +b.z.toFixed(1), what});
  };
  Sim.prototype.fire = function (inp) {
    const w = this.w, b = this.b, hand = [b.x, b.y + HAND, b.z], v = [b.vx, b.vy, b.vz];
    if (this.model === 'today') {
      const a = todayAnchor(w, [b.x, b.y + EYE, b.z], inp.yaw, inp.pitch);
      if (!a) { this.note('miss'); return false; }
      const dx = a.x - hand[0], dy = a.y - hand[1], dz = a.z - hand[2], dist = Math.hypot(dx, dy, dz);
      const overhead = dy > T.ZIP_UP_MIN_RISE && Math.hypot(dx, dz) < dy * T.ZIP_UP_SLOPE;
      this.mode = dy > T.MIN_RISE && !overhead ? 'swing' : 'zip';
      const rope = {ax: a.x, ay: a.y, az: a.z, len: dist, target: dist};
      if (this.mode === 'swing') {
        const h = Math.hypot(dx, dz), bx = h > 1e-3 ? a.x - dx / h * 1.5 : a.x, bz = h > 1e-3 ? a.z - dz / h * 1.5 : a.z;
        const floor = Math.max(w.floorUnder(hand[0], hand[1], hand[2]), w.floorUnder(bx, a.y, bz));
        rope.target = Math.max(T.MIN, Math.min(dist, a.y - floor - T.CLEAR - HAND));
      }
      this.rope = rope;
      this.travel = Math.max(1, Math.min(3, Math.ceil(dist / 16)));
      this.ropes.push({t: this.t, a: [a.x, a.y, a.z], from: hand, kind: this.mode, aimed: a.aimed, len: dist, fit: rope.target});
      this.note((this.mode === 'zip' ? 'zip ' : 'rope ') + (a.aimed ? 'aimed' : 'assisted') + ' ' + dist.toFixed(0) + ' (fits ' + rope.target.toFixed(0) + ')');
    } else {
      const a = this.marker || assistAnchor(w, hand, v, inp.yaw);
      if (a && a.kind !== 'zip') {
        this.rope = {ax: a.x, ay: a.y, az: a.z, len: a.len, fitLen: a.fitLen, caught: false, bend: 0};
        this.travel = Math.max(1, Math.min(2, Math.ceil(a.len / 22)));
        this.mode = 'swing';
        this.ropes.push({t: this.t, a: [a.x, a.y, a.z], from: hand, kind: 'swing', aimed: false, len: a.len, fit: a.fitLen});
        this.note('rope ' + a.len.toFixed(0) + ' (fits ' + a.fitLen.toFixed(0) + ', ' + a.elev.toFixed(0) + ' deg, ' + a.side.toFixed(0) + ' aside)');
      } else {
        const z = a || zipFallback(w, [b.x, b.y + EYE, b.z], inp.yaw, inp.pitch);
        if (!z) return false;
        this.rope = {ax: z.x, ay: z.y, az: z.z, len: 0, top: z.top, nx: z.nx, nz: z.nz};
        this.travel = 1; this.mode = 'zip';
        this.ropes.push({t: this.t, a: [z.x, z.y, z.z], from: hand, kind: 'zip', aimed: true, len: 0, fit: 0});
        this.note('no swing here: zip to the top');
      }
    }
    this.k = {x: hand[0], y: hand[1], z: hand[2], vx: v[0], vy: v[1], vz: v[2]};
    if (this.model === 'assist' && this.mode === 'swing') {
      // the entry floor: a swing never starts slower than a jog along the heading
      const hx = fx(inp.yaw), hz = fz(inp.yaw), along = this.k.vx * hx + this.k.vz * hz;
      if (along < A.ENTRY_FLOOR) { this.k.vx += hx * (A.ENTRY_FLOOR - along); this.k.vz += hz * (A.ENTRY_FLOOR - along); }
    }
    this.state = 'rope'; this.swings++; this.ropeStart = this.t;
    return true;
  };
  Sim.prototype.letGo = function (why) {
    this.note(why);
    const b = this.b, k = this.k;
    b.vx = k.vx; b.vy = k.vy; b.vz = k.vz;
    this.state = 'run'; this.rope = null; this.cooldown = 2; this.groundTicks = 0;
  };
  Sim.prototype.step = function (inp) {
    const w = this.w, b = this.b, model = this.model, yaw = inp.yaw;
    const pressed = inp.rope && !this.wasDown;
    this.wasDown = inp.rope;
    if (this.cooldown > 0) this.cooldown--;
    this.flash = null;
    if (this.state === 'run') {
      if (b.onGround) {
        // on foot: running along the look at sprint speed (the bot and the demo both hop to throw)
        const hx = fx(yaw), hz = fz(yaw);
        b.vx = hx * 0.36 * Math.max(0.3, inp.fwd); b.vz = hz * 0.36 * Math.max(0.3, inp.fwd);
        if (inp.jump) { b.vy = 0.42; this.note('jump'); }
      } else {
        b.vy -= G; b.vy *= T.CARRY_V; b.vx *= T.CARRY_H; b.vz *= T.CARRY_H;
        // the carry's air control: input bends the path (AscentRules.carryStep's spirit, simplified)
        const s = Math.hypot(b.vx, b.vz);
        if (s > 0.05 && inp.fwd > 0) {
          const tx = fx(yaw) * s, tz = fz(yaw) * s;
          b.vx += (tx - b.vx) * 0.04; b.vz += (tz - b.vz) * 0.04;
        }
        if (this.pull && this.pull.ticks-- > 0) {
          const over = b.y >= this.pull.top - 0.05;
          b.vx = -this.pull.nx * (over ? 0.3 : 0.04); b.vz = -this.pull.nz * (over ? 0.3 : 0.04);
        } else this.pull = null;
      }
      const lost = move(w, b, b.vx, b.vy, b.vz);
      if (lost > 0.45) { this.bonks++; this.worst = Math.max(this.worst, lost); this.note('smash ' + lost.toFixed(2) + ' b/t'); this.flash = 'smash'; }
      if (inp.bot && lost > 0 && b.y - w.floorUnder(b.x, b.y + 0.01, b.z) < 3) {
        // a bot on foot against a trunk or a boulder steps round it, as a player would
        for (const sx of [1.5, -1.5, 3, -3, 4.5, -4.5]) if (!w.bodyHits(b.x + sx, b.y, b.z + 0.5)) { b.x += sx; break; }
      }
      // the marker: where a press would bite now (assisted only; refreshed every other tick)
      if (model === 'assist' && (this.t % 2 === 0 || !this.marker)) {
        const hand = [b.x, b.y + HAND, b.z];
        this.marker = b.onGround && !inp.rope ? null : assistAnchor(w, hand, [b.vx, b.vy, b.vz], yaw);
        if (!this.marker) {
          const z = zipFallback(w, [b.x, b.y + EYE, b.z], yaw, inp.pitch);
          if (z) this.marker = Object.assign({kind: 'zip'}, z);
        }
      }
      const wants = model === 'today' ? pressed : inp.rope;
      if (wants && this.cooldown === 0 && b.y > 0.2) {
        if (!b.onGround || model === 'today') {
          if (!this.fire(inp) && model === 'assist') this.cooldown = 2;
        }
      }
    } else {
      const k = this.k, rope = this.rope;
      if (!inp.rope && this.travel === 0) {
        // let go: the window's boost
        if (model === 'today') {
          const q = this.mode === 'swing' ? releaseQuality(k, rope.ax, rope.ay, rope.az) : 0;
          const f = 1 + (T.RELEASE - 1) * q;
          k.vx *= f; k.vy *= f; k.vz *= f;
          this.lastQ = q;
          this.letGo(q > 0 ? 'let go (window ' + q.toFixed(2) + ')' : 'let go');
        } else {
          const q = this.mode === 'swing' ? windowQuality(k, rope, yaw + (rope.bend || 0)) : 0;
          if (q > 0.6) {
            const f = 1 + (A.BOOST_PERFECT - 1) * Math.min(1, (q - 0.6) / 0.3);
            k.vx *= f; k.vz *= f; k.vy += A.LIFT_PERFECT * Math.min(1, (q - 0.6) / 0.3);
            this.perfect++;
            this.flash = 'window';
          }
          this.lastQ = q;
          this.letGo(q > 0.6 ? 'let go in the window (' + q.toFixed(2) + ')' : 'let go');
        }
        this.path.push([+b.x.toFixed(2), +b.y.toFixed(2), +b.z.toFixed(2), 0]);
        this.t++;
        return;
      }
      let letGo = false, why = '';
      if (this.travel > 0) {
        this.travel--;
        k.vy -= G; k.vy *= T.CARRY_V; k.vx *= T.CARRY_H; k.vz *= T.CARRY_H;
        k.x += k.vx; k.y += k.vy; k.z += k.vz;
        if (this.travel === 0 && this.mode === 'swing') {
          const r = Math.hypot(k.x - rope.ax, k.y - rope.ay, k.z - rope.az);
          rope.len = Math.max(T.MIN, Math.min(rope.len, r));
          if (model === 'today') {
            const lx = fx(yaw), lz = fz(yaw), rx = k.x - rope.ax, ry = k.y - rope.ay, rz = k.z - rope.az;
            const o = (lx * rx + lz * rz) / r, tx = lx - rx / r * o, ty = -ry / r * o, tz = lz - rz / r * o, tl = Math.hypot(tx, ty, tz);
            if (tl > 1e-3) { k.vx += tx / tl * T.YANK; k.vy += ty / tl * T.YANK; k.vz += tz / tl * T.YANK; }
          }
        }
      } else if (this.mode === 'zip') {
        const dx = rope.ax - k.x, dy = rope.ay - k.y, dz = rope.az - k.z, d = Math.hypot(dx, dy, dz);
        if (d < T.ZIP_ARRIVE) {
          letGo = true;
          if (model === 'assist' || rope.top != null) {
            why = 'zip arrived: up and over';
            const top = rope.top != null ? rope.top : rope.ay;
            const rise = top + 0.6 - (k.y - HAND);
            k.vy = Math.min(1.1, Math.sqrt(2 * G * Math.max(0.5, rise)) + 0.05); k.vx *= 0.2; k.vz = 0.02;
            this.pull = {ticks: 16, top, nx: rope.nx || 0, nz: rope.nz != null ? rope.nz : -1};
          } else { why = 'zip arrived: a hop off the face'; k.vx *= 0.45; k.vz *= 0.45; k.vy = Math.max(k.vy * 0.3, 0.48); }
        } else {
          const s = Math.min(T.ZIP_SPEED, Math.hypot(k.vx, k.vy, k.vz) + T.ZIP_ACCEL);
          k.vx += (dx / d * s - k.vx) * 0.55; k.vy += (dy / d * s - k.vy) * 0.55; k.vz += (dz / d * s - k.vz) * 0.55;
          k.x += k.vx; k.y += k.vy; k.z += k.vz;
        }
      } else if (model === 'today') {
        if (rope.len > rope.target + 1e-3) rope.len = reel(k, rope.ax, rope.ay, rope.az, rope.len, Math.min(T.FIT_REEL, rope.len - rope.target));
        tetherStep(k, rope.ax, rope.ay, rope.az, rope.len, yaw, inp.fwd, inp.right || 0);
      } else {
        const hd = yaw + (rope.bend || 0);
        assistStep(k, rope, hd, inp.fwd);
        let ahead = wallAhead(w, k, rope, hd);
        if (ahead >= 0) {
          // something in the swing's way: bend the swing round it (a few degrees either side)
          for (const b2 of [8, -8, 16, -16, 24, -24]) {
            if (wallAhead(w, k, rope, yaw + b2) < 0) { rope.bend = b2; ahead = -1; this.note('the swing bends ' + b2 + ' deg round it'); break; }
          }
        }
        const P = pivot(k, rope.ax, rope.ay, rope.az, hd, PV);
        const ang = angleFromBottom(k, P.px, P.py, P.pz);
        // never swung backward: past the pivot along the heading, the rope lets go once forward speed is spent
        const past = (k.x - P.px) * fx(hd) + (k.z - P.pz) * fz(hd) > 0, fwdSpeed = k.vx * fx(hd) + k.vz * fz(hd);
        if (ahead >= 0 && ahead < 4) { letGo = true; why = 'let go before a wall'; k.vy = Math.max(k.vy, 0) + 0.3; }
        else if (past && (ang > A.APEX || fwdSpeed < 0.15 || k.vy < -0.02 && ang > 8)) { letGo = true; why = 'the top of the swing: let go'; }
        this.lastQ = windowQuality(k, rope, hd);
      }
      b.vx = k.vx; b.vy = k.vy; b.vz = k.vz;
      const lost = move(w, b, k.x - b.x, k.y - HAND - b.y, k.z - b.z);
      if (lost > 0.45) { this.bonks++; this.worst = Math.max(this.worst, lost); this.note('smash ' + lost.toFixed(2) + ' b/t'); this.flash = 'smash'; }
      k.x = b.x; k.y = b.y + HAND; k.z = b.z; k.vx = b.vx; k.vz = b.vz;
      if (b.onGround) { k.vy = Math.max(0, k.vy); this.groundTicks++; } else this.groundTicks = 0;
      if (this.groundTicks >= 6) { letGo = true; why = 'landed on the rope'; }
      if (this.t - this.ropeStart > 140) { letGo = true; why = 'still on the rope after 7 s'; }
      if (letGo) this.letGo(why);
    }
    this.path.push([+b.x.toFixed(2), +b.y.toFixed(2), +b.z.toFixed(2), this.state === 'rope' ? 1 : 0]);
    if (Math.abs(b.z - this.lastZ) < 0.02) this.stuck++; else this.stuck = 0;
    this.lastZ = b.z;
    this.t++;
  };

  // The bots. Both look down the way (steering a little back to its middle) at 'pitch' and hold forward; both hop
  // to start. Today's presses the rope when airborne and falling, holds it, and lets go in the window (the best a
  // player can do); the assisted one holds the rope and lets go in the window.
  function botInput(sim, pitch) {
    const b = sim.b, inp = {fwd: 1, yaw: Math.max(-15, Math.min(15, b.x * 2.5)), pitch, jump: false, rope: false, bot: true};
    if (b.onGround && sim.state === 'run') { sim._ground = (sim._ground || 0) + 1; inp.jump = sim.t < 2 || sim._ground > 4; if (inp.jump) sim._ground = 0; }
    if (sim.state === 'run') {
      if (sim.model === 'today') inp.rope = !b.onGround && b.vy < 0.2 && !sim.wasDown && (sim._missAt == null || sim.t - sim._missAt > 10);
      else inp.rope = !b.onGround;
    } else {
      inp.rope = true;
      if (sim.travel === 0 && sim.mode === 'swing') {
        const q = sim.model === 'today' ? releaseQuality(sim.k, sim.rope.ax, sim.rope.ay, sim.rope.az) : windowQuality(sim.k, sim.rope, inp.yaw + (sim.rope.bend || 0));
        if (q > 0.85) inp.rope = false;
      }
    }
    return inp;
  }

  function run(sceneKey, model, opts) {
    opts = opts || {};
    const sim = new Sim(sceneKey, model, opts), pitch = opts.pitch != null ? opts.pitch : -15, maxT = opts.ticks || 300;
    while (sim.t < maxT) {
      const before = sim.events.length;
      sim.step(botInput(sim, pitch));
      if (sim.model === 'today' && sim.events.length > before && sim.events[sim.events.length - 1].what === 'miss') sim._missAt = sim.t;
      if (sim.stuck > 60) { sim.note('stopped dead against it'); break; }
    }
    return {scene: sceneKey, model, pitch, path: sim.path, ropes: sim.ropes, events: sim.events, ticks: sim.t, swings: sim.swings,
      bonks: sim.bonks, worst: +sim.worst.toFixed(2), distance: +(sim.b.z - sim.z0).toFixed(1), perfect: sim.perfect,
      stuck: sim.stuck > 60, boxes: sim.w.boxes.map(q => [q.a, q.b, q.kind])};
  }

  const api = {SCENES, World, Sim, botInput, move, todayAnchor, assistAnchor, assistStep, tetherStep, reel, releaseQuality, windowQuality,
    wallAhead, zipFallback, pivot, run, T, A, G, HAND, EYE};
  if (typeof module !== 'undefined') module.exports = api; else root.Swing = api;
})(this);
