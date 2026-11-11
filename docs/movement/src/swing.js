// The rope race (docs/movement): today's rule beside the proposed one, side on, in the same place, on the same clock.
// A 2D sketch of the rules for the approval page (the numbers under it come from the game's own bench, proof/run.sh).
// Blocks and ticks, gravity 0.08 a tick as in the game. Trees stand behind the runner's line: only the ground stops it.
(function () {
  const cv = document.getElementById('ropeview'); if (!cv) return;
  // the sling's pull and top speed, set so the sketch's distances sit near the bench's (which has trunks to fly round)
  let SL_PULL = 0.1, SL_TOP = 0.75, BOOST = 1.0;
  const g = cv.getContext('2d'), G = 0.08, HAND = 1.55;
  const reduced = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  let scene = 'wood', running = !reduced, raf = 0;

  function rng(seed) { let s = seed >>> 0; return () => { s = (s * 1664525 + 1013904223) >>> 0; return s / 4294967296; }; }
  function world(kind) {
    const r = rng(kind === 'wood' ? 21 : kind === 'tall' ? 7 : 3), t = [];
    let x = 10;
    while (x < 900) {
      if (kind === 'wood') { t.push({ x, h: 6 + Math.floor(r() * 4), w: 2.5, giant: false }); x += 5 + Math.floor(r() * 4); }
      else if (kind === 'tall') { t.push({ x, h: 26 + Math.floor(r() * 13), w: 6, giant: true }); x += 15 + Math.floor(r() * 5); }
      else { t.push({ x: x + 30, h: 6 + Math.floor(r() * 2), w: 2.5, giant: false }); x += 70 + Math.floor(r() * 30); }
    }
    return t;
  }
  function gauss(v, b, s) { const q = (v - b) / s; return Math.exp(-q * q); }

  function runner(rule) {
    return { rule, x: 0, y: 0, vx: 0.36, vy: 0, ground: true, gt: 0, mode: 'run', ax: 0, ay: 0, len: 0, fit: 0, caught: false, age: 0, cool: 0, trail: [], ropes: 0, t: 0, say: '' };
  }
  // today's planner, side on (AscentSwing.plan): an anchor 4+ over the hand, 30 to 82 degrees up, a rope of 8+ whose
  // low point keeps the feet 2.5 over the ground: so the anchor has to stand 10 or more over the ground
  function findSwing(p, trees) {
    let best = null, bs = 0; const hy = p.y + HAND;
    for (const tr of trees) {
      const dx = tr.x - p.x, dy = tr.h - hy; if (dx < 2 || dx > 44) continue;
      const len = Math.hypot(dx, dy), el = Math.atan2(dy, dx) * 180 / Math.PI;
      if (dy < 4 || len < 8 || len > 44 || el < 30 || el > 82) continue;
      const rad = Math.min(len, tr.h - 2.5 - HAND); if (rad < 6 || rad < len * 0.7) continue;
      const sc = gauss(el, 50, 15) * gauss(rad, 26, 9); if (sc > bs) { bs = sc; best = { x: tr.x, y: tr.h, len, fit: rad }; }
    }
    return best;
  }
  // the proposed sling: a top ahead and above; the rope pulls toward a point over it and lets go before it
  function findSling(p, trees) {
    let best = null, bs = 0; const hy = p.y + HAND;
    for (const tr of trees) {
      const dx = tr.x - p.x, rise = tr.h + 2.4 - hy; if (dx < 6 || dx > 30 || rise < 1 || rise > dx * 1.2) continue;
      const sc = gauss(dx, 16, 9) * (0.5 + 0.5 * gauss(rise, 5, 5)); if (sc > bs) { bs = sc; best = { x: tr.x, y: tr.h + 2.4 }; }
    }
    return best;
  }
  function step(p, trees) {
    p.t++; if (p.cool > 0) p.cool--;
    if (p.mode === 'swing') {
      p.age++;
      let hx = p.x, hy = p.y + HAND;
      p.vy -= G;
      if (p.len > p.fit && p.vy < 0) p.len = Math.max(p.fit, p.len - 0.4);
      let rx = hx - p.ax, ry = hy - p.ay, r = Math.hypot(rx, ry), nx = rx / r, ny = ry / r;
      if (-ny > 0.2) { let d = 0.02 * Math.min(1, (-ny - 0.2) / 0.5); if (Math.hypot(p.vx, p.vy) < 0.9 && p.vx >= 0) d *= 3; p.vx += -ny * d; p.vy += nx * d; }
      p.vx *= 0.998; p.vy *= 0.998;
      hx += p.vx; hy += p.vy; rx = hx - p.ax; ry = hy - p.ay; r = Math.hypot(rx, ry);
      if (r >= p.len) {
        nx = rx / r; ny = ry / r; hx = p.ax + nx * p.len; hy = p.ay + ny * p.len;
        const out = p.vx * nx + p.vy * ny;
        if (out > 0) { const s0 = Math.hypot(p.vx, p.vy); p.vx -= nx * out; p.vy -= ny * out; if (!p.caught) { p.caught = true; const s = Math.hypot(p.vx, p.vy), k = Math.min(s0, s + out * 0.65); if (s > 1e-6) { p.vx *= k / s; p.vy *= k / s; } } }
      }
      p.x = hx; p.y = hy - HAND;
      const ang = Math.acos(Math.max(-1, Math.min(1, -(hy - p.ay) / p.len))) * 180 / Math.PI, past = hx > p.ax;
      // today the rope lets go at the top (the speed spent); proposed: in the window, while it is still fast
      const letGo = p.rule === 'new' ? (p.vy > 0 && past && ang > 58) || (past && (ang > 95 || p.vx < 0.15)) : past && (ang > 95 || p.vx < 0.15 || (p.vy < -0.02 && ang > 8));
      if (letGo || p.age > 140) { if (p.rule === 'new' && p.vy > 0) { p.vx *= BOOST; } p.mode = 'air'; p.cool = 2; }
      if (p.y <= 0) { p.y = 0; p.mode = 'run'; p.ground = true; }
    } else if (p.mode === 'sling') {
      p.age++;
      const hx = p.x, hy = p.y + HAND, dx = p.ax - hx, dy = p.ay - hy, h = Math.abs(dx), d = Math.hypot(dx, dy);
      if (h < 4.5 || p.age > 34 || Math.atan2(dy, h) > 50 * Math.PI / 180) {
        const tt = h / Math.max(0.3, p.vx); p.vy = Math.max(p.vy, Math.min(0.9, dy / Math.max(1, tt) + 0.5 * G * tt)); p.mode = 'air'; p.cool = 2;
      } else {
        p.vx += dx / d * SL_PULL; p.vy += dy / d * SL_PULL - G * 0.4;
        const s = Math.hypot(p.vx, p.vy); if (s > SL_TOP) { p.vx *= SL_TOP / s; p.vy *= SL_TOP / s; }
        p.x += p.vx; p.y += p.vy;
      }
    } else {
      if (p.ground) {
        p.vx = 0.36; p.gt++;
        if (p.gt > 4) { p.vy = 0.42; p.ground = false; p.gt = 0; }
      } else { p.vy = (p.vy - G) * 0.98; p.vx *= 0.988; }
      p.x += p.vx; p.y += p.vy;
      if (p.y <= 0) { p.y = 0; p.vy = 0; p.ground = true; }
      if (!p.ground && p.cool === 0 && p.y > 0.2) {
        const s = findSwing(p, trees);
        if (s) { p.mode = 'swing'; p.ax = s.x; p.ay = s.y; p.len = s.len; p.fit = s.fit; p.caught = false; p.age = 0; p.ropes++; p.vx = Math.max(p.vx, 0.75); }
        else if (p.rule === 'new') { const q = findSling(p, trees); if (q) { p.mode = 'sling'; p.ax = q.x; p.ay = q.y; p.age = 0; p.ropes++; } }
      }
    }
    if (p.t % 2 === 0) { p.trail.push(p.x, p.y, p.mode === 'swing' ? 1 : p.mode === 'sling' ? 2 : 0); if (p.trail.length > 600) p.trail.splice(0, 3); }
  }

  let trees = world(scene), A = runner('old'), B = runner('new');
  const SECS = 15;
  function reset() { trees = world(scene); A = runner('old'); B = runner('new'); out(); }
  function out() {
    const e = document.getElementById('ropestat'); if (!e) return;
    const s = Math.min(SECS, A.t / 20).toFixed(1);
    e.textContent = s + ' s · today ' + Math.round(A.x) + ' blocks, ' + A.ropes + ' ropes · proposed ' + Math.round(B.x) + ' blocks, ' + B.ropes + ' ropes';
    document.querySelectorAll('[data-rope-scene]').forEach(b => b.setAttribute('aria-pressed', b.dataset.ropeScene === scene));
    const pl = document.getElementById('ropeplay'); if (pl) pl.textContent = running ? 'Pause' : 'Play';
  }

  function lane(p, y0, hgt, label, W, px) {
    const tall = scene === 'tall', ground = y0 + hgt - 14, camX = p.x - W / px * 0.3;
    const X = x => (x - camX) * px, Y = y => ground - y * px;
    const sky = g.createLinearGradient(0, y0, 0, y0 + hgt); sky.addColorStop(0, '#9cc4ee'); sky.addColorStop(1, '#dcecf7');
    g.fillStyle = sky; g.fillRect(0, y0, W, hgt);
    g.save(); g.beginPath(); g.rect(0, y0, W, hgt); g.clip();
    for (const tr of trees) {
      const x = X(tr.x); if (x < -80 || x > W + 80) continue;
      const tw = (tr.giant ? 3 : 1) * px;
      g.fillStyle = tr.giant ? '#4a3626' : '#6b4f33'; g.fillRect(Math.round(x - tw / 2), Y(tr.h - 1), Math.ceil(tw), tr.h * px);
      g.fillStyle = tr.giant ? '#2f5d34' : '#4d8a3f'; g.fillRect(Math.round(x - tr.w * px), Y(tr.h), Math.ceil(tr.w * 2 * px), (tr.giant ? 7 : 3) * px);
      g.fillStyle = tr.giant ? '#3d7442' : '#62a552'; g.fillRect(Math.round(x - (tr.w - 1) * px), Y(tr.h + (tr.giant ? 2 : 1)), Math.ceil((tr.w - 1) * 2 * px), (tr.giant ? 2 : 1) * px);
    }
    g.fillStyle = '#5c9a48'; g.fillRect(0, ground, W, 4); g.fillStyle = '#7a5a3c'; g.fillRect(0, ground + 4, W, 10);
    // the path flown: where a rope carried it, the line is the rope's colour
    for (let i = 0; i + 2 < p.trail.length; i += 3) {
      const m = p.trail[i + 2]; g.fillStyle = m === 1 ? 'rgba(20,110,200,0.75)' : m === 2 ? 'rgba(210,120,20,0.85)' : 'rgba(40,50,60,0.3)';
      g.fillRect(Math.round(X(p.trail[i])) - 1, Math.round(Y(p.trail[i + 1] + 0.9)) - 1, m ? 3 : 2, m ? 3 : 2);
    }
    if (p.mode === 'swing' || p.mode === 'sling') {
      g.strokeStyle = p.mode === 'swing' ? '#1d7fe0' : '#e08a1d'; g.lineWidth = 2; g.beginPath(); g.moveTo(X(p.x), Y(p.y + HAND)); g.lineTo(X(p.ax), Y(p.mode === 'sling' ? p.ay - 2.4 : p.ay)); g.stroke();
    }
    const bw = Math.max(3, 0.6 * px), bh = Math.max(7, 1.8 * px);
    g.fillStyle = '#1d2530'; g.fillRect(Math.round(X(p.x) - bw / 2), Math.round(Y(p.y) - bh), Math.round(bw), Math.round(bh));
    g.fillStyle = '#e9c9a4'; g.fillRect(Math.round(X(p.x) - bw / 2), Math.round(Y(p.y) - bh), Math.round(bw), Math.max(2, Math.round(bh * 0.25)));
    g.restore();
    g.fillStyle = 'rgba(15,22,30,0.78)'; g.fillRect(8, y0 + 8, g.measureText(label).width + 16, 22);
    g.fillStyle = '#fff'; g.fillText(label, 16, y0 + 23);
    if (tall) { g.fillStyle = 'rgba(15,22,30,0.7)'; }
  }
  function draw() {
    const dpr = Math.min(2, window.devicePixelRatio || 1), W = cv.clientWidth, H = cv.clientHeight;
    if (cv.width !== Math.round(W * dpr) || cv.height !== Math.round(H * dpr)) { cv.width = Math.round(W * dpr); cv.height = Math.round(H * dpr); }
    g.setTransform(dpr, 0, 0, dpr, 0, 0); g.font = '600 13px system-ui, sans-serif';
    const hgt = (H - 6) / 2, px = scene === 'tall' ? Math.min(4.6, hgt / 48) : Math.min(11, hgt / 16);
    g.clearRect(0, 0, W, H);
    lane(A, 0, hgt, 'Today: ' + (A.mode === 'swing' ? 'swinging' : A.ropes === 0 && A.t > 40 ? 'nothing tall enough to swing from' : 'running'), W, px);
    lane(B, hgt + 6, hgt, 'Proposed: ' + (B.mode === 'swing' ? 'swinging' : B.mode === 'sling' ? 'the sling' : 'between ropes'), W, px);
  }
  let acc = 0, last = 0;
  function frame(now) {
    raf = requestAnimationFrame(frame);
    const dt = Math.min(100, now - (last || now)); last = now;
    if (running) { acc += dt; while (acc >= 50) { acc -= 50; if (A.t < SECS * 20) { step(A, trees); step(B, trees); } else if (A.t++ > SECS * 20 + 50) reset(); } out(); }
    draw();
  }
  document.addEventListener('click', e => {
    const b = e.target.closest('[data-rope-scene]'); if (b) { scene = b.dataset.ropeScene; reset(); if (!running) { for (let i = 0; i < SECS * 20; i++) { step(A, trees); step(B, trees); } out(); draw(); } }
    if (e.target.id === 'ropeplay') { running = !running; out(); }
  });
  if (!running) { for (let i = 0; i < SECS * 20; i++) { step(A, trees); step(B, trees); } }
  out(); raf = requestAnimationFrame(frame);
  window.RopeRace = { tune: (a, b, c) => { SL_PULL = a; SL_TOP = b; BOOST = c; }, finish: () => { reset(); for (let i = 0; i < SECS * 20; i++) { step(A, trees); step(B, trees); } out(); draw(); return [Math.round(A.x), A.ropes, Math.round(B.x), B.ropes]; }, scene: s => { scene = s; reset(); } };
})();
