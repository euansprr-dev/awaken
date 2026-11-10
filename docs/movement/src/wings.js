// The wing studio (docs/movement): the proposed wings, in boxes only, beside today's wing rebuilt from the numbers in
// body/ascent/client/WingLayer.java. A sketch of shape and motion for the approval page, not the game's renderer.
// Units are texels (1/16 of a block); the figure faces away from the first camera (-z), its back toward +z.
(function () {
  const host = document.getElementById('wingview');
  if (!host || !window.THREE) { if (host) host.classList.add('nogl'); return; }
  const T = THREE, D = Math.PI / 180;
  let renderer;
  try { renderer = new T.WebGLRenderer({ antialias: true, alpha: false, powerPreference: 'low-power' }); }
  catch (e) { host.classList.add('nogl'); return; }
  renderer.setPixelRatio(Math.min(2, window.devicePixelRatio || 1));
  host.appendChild(renderer.domElement);
  const scene = new T.Scene();
  const cam = new T.PerspectiveCamera(34, 1, 1, 2000);

  // ---- sky: a dusk gradient (the wings are light, so they are shown against a dim sky) ----
  function skyTex(top, mid, low) {
    const c = document.createElement('canvas'); c.width = 4; c.height = 256;
    const g = c.getContext('2d'), gr = g.createLinearGradient(0, 0, 0, 256);
    gr.addColorStop(0, top); gr.addColorStop(0.55, mid); gr.addColorStop(1, low);
    g.fillStyle = gr; g.fillRect(0, 0, 4, 256);
    return new T.CanvasTexture(c);
  }
  const SKY = { cool: skyTex('#0b1524', '#1d3350', '#5b6f86'), gold: skyTex('#140f1c', '#3a2a35', '#8a6a4a') };
  scene.background = SKY.cool;

  // ---- boxes with baked face shading (flat colour, no lights: the wings are emissive in the game) ----
  const MAT = new T.MeshBasicMaterial({ vertexColors: true });
  const FACE = [0.80, 0.80, 1.0, 0.62, 0.93, 0.72];
  function box(w, h, d, hex, x, y, z) {
    const g = new T.BoxGeometry(w, h, d), col = new T.Color(hex), a = new Float32Array(72);
    for (let f = 0; f < 6; f++) for (let v = 0; v < 4; v++) {
      const i = (f * 4 + v) * 3; a[i] = col.r * FACE[f]; a[i + 1] = col.g * FACE[f]; a[i + 2] = col.b * FACE[f];
    }
    g.setAttribute('color', new T.BufferAttribute(a, 3));
    const m = new T.Mesh(g, MAT); m.position.set(x || 0, y || 0, z || 0); return m;
  }

  // ---- the figure: a plain blocky body (not a skin), 32 texels tall ----
  function makeFigure() {
    const root = new T.Group(), body = new T.Group();
    root.add(body);
    const C = { suit: 0x39424f, trim: 0x566273, skin: 0xb9a48c, dark: 0x262c36 };
    body.add(box(8, 12, 4, C.suit, 0, 18, 0));
    body.add(box(8, 2, 4.2, C.trim, 0, 13, 0));
    const head = box(8, 8, 8, C.skin, 0, 28, 0); body.add(head);
    body.add(box(8.3, 3, 8.3, C.dark, 0, 31, 0));
    const arms = [], legs = [];
    for (const s of [-1, 1]) {
      const a = new T.Group(); a.position.set(s * 6, 23, 0); a.add(box(4, 12, 4, C.suit, 0, -5, 0)); a.add(box(4, 3, 4.1, C.skin, 0, -10.5, 0));
      body.add(a); arms.push(a);
      const l = new T.Group(); l.position.set(s * 2, 12, 0); l.add(box(4, 12, 4, C.dark, 0, -6, 0)); l.add(box(4.1, 3, 4.1, C.trim, 0, -10.5, 0));
      body.add(l); legs.push(l);
    }
    // the mount between the shoulder blades (the wing core's mark): dark plate, a lit core
    body.add(box(5, 5, 1.2, 0x1c222b, 0, 20.5, 2.5));
    const core = box(1.6, 1.6, 0.6, 0xdff8ff, 0, 20.5, 3.3); body.add(core);
    return { root, body, arms, legs, core };
  }

  // ---- palettes ----
  const PAL = {
    light: { root: 0xf4fdff, mid: 0xc4ecff, outer: 0x86cdff, tip: 0xeefcff, shaft: 0xffffff, cov: 0xe2f7ff, cov2: 0xf6feff, spar: 0xeafaff, halo: '120,196,255' },
    sky: { root: 0xf6fbff, mid: 0xb8dcff, outer: 0x7fa8ff, tip: 0xf0f6ff, shaft: 0xffffff, cov: 0xdcecff, cov2: 0xf4f9ff, spar: 0xe8f2ff, halo: '128,170,255' },
    gold: { root: 0xfffbea, mid: 0xffe9a8, outer: 0xffc85a, tip: 0xfff7d6, shaft: 0xffffff, cov: 0xfff1c4, cov2: 0xfffbe6, spar: 0xfff4cf, halo: '255,196,96' }
  };

  function haloTex(rgb) {
    const c = document.createElement('canvas'); c.width = c.height = 64;
    const g = c.getContext('2d'), gr = g.createRadialGradient(32, 32, 0, 32, 32, 32);
    gr.addColorStop(0, 'rgba(' + rgb + ',0.9)'); gr.addColorStop(0.35, 'rgba(' + rgb + ',0.32)'); gr.addColorStop(1, 'rgba(' + rgb + ',0)');
    g.fillStyle = gr; g.fillRect(0, 0, 64, 64);
    return new T.CanvasTexture(c);
  }
  const HALO = {};
  for (const k in PAL) HALO[k] = haloTex(PAL[k].halo);

  // ---- one feather: a slab one texel thick whose point is steps, a raised shaft, colour by length ----
  function feather(L, W, pal) {
    const g = new T.Group(); g.matrixAutoUpdate = false;
    const a = Math.max(2, Math.round(L * 0.42)), b = Math.max(a + 1, L - 4);
    g.add(box(W, a, 1, pal.root, 0, a / 2, 0));
    g.add(box(W, b - a, 1, pal.mid, 0, a + (b - a) / 2, 0));
    if (L - 1 > b) g.add(box(Math.max(1, W - 1), L - 1 - b, 1, pal.outer, 0, b + (L - 1 - b) / 2, 0));
    g.add(box(Math.max(1, W - 2), 1, 1, pal.tip, 0, L - 0.5, 0));
    g.add(box(0.6, Math.max(1, L - 4), 0.3, pal.shaft, 0, (L - 4) / 2 + 0.5, 0.62));
    g.userData.L = L;
    return g;
  }

  const BONE = [9, 13, 14];
  const PRIM = [21, 24, 26, 28, 28, 27, 25, 22, 18], SEC = [15, 16, 17, 18, 19, 19, 20, 20], TER = [10, 12, 13, 14, 15];

  function makeWing(side, cfg) {
    const pal = PAL[cfg.pal], g = new T.Group();
    g.scale.setScalar(cfg.k);
    g.rotation.z = -side * cfg.roll * D;
    const w = { side, cfg, g, spars: [], knuckles: [], prim: [], sec: [], ter: [], cov: [], cov2: [], halos: [] };
    for (let i = 0; i < 3; i++) {
      const s = new T.Group(); s.matrixAutoUpdate = false; s.add(box(2, BONE[i] + 1, 2, pal.spar, 0, BONE[i] / 2, 0)); g.add(s); w.spars.push(s);
    }
    for (let i = 0; i < 2; i++) { const k = box(3, 3, 3, pal.cov2); g.add(k); w.knuckles.push(k); }
    for (let i = 0; i < TER.length; i++) { const f = feather(TER[i], 3, pal); g.add(f); w.ter.push(f); }
    for (let i = 0; i < SEC.length; i++) { const f = feather(SEC[i], 3, pal); g.add(f); w.sec.push(f); }
    for (let i = 0; i < PRIM.length; i++) { const f = feather(PRIM[i], 3, pal); g.add(f); w.prim.push(f); }
    const covPal = { root: pal.cov2, mid: pal.cov, outer: pal.cov, tip: pal.mid, shaft: pal.shaft };
    for (let i = 0; i < 13; i++) { const f = feather(i < 4 ? 6 : i < 9 ? 8 : 9, 3, covPal); g.add(f); w.cov.push(f); }
    const cov2Pal = { root: pal.cov2, mid: pal.cov2, outer: pal.cov, tip: pal.cov, shaft: pal.shaft };
    for (let i = 0; i < 11; i++) { const f = feather(i < 4 ? 4 : 5, 3, cov2Pal); g.add(f); w.cov2.push(f); }
    for (let i = 0; i < 3; i++) {
      const sp = new T.Sprite(new T.SpriteMaterial({ map: HALO[cfg.pal], blending: T.AdditiveBlending, depthWrite: false, transparent: true, opacity: 0.2 }));
      g.add(sp); w.halos.push(sp);
    }
    return w;
  }

  // ---- posing: three bones in the wing's own plane, flapped about the body's long axis, feathers hung from them ----
  const J = [new T.Vector3(), new T.Vector3(), new T.Vector3(), new T.Vector3()];
  const dv = new T.Vector3(), nv = new T.Vector3(), wv = new T.Vector3(), fv = new T.Vector3(), pv = new T.Vector3(), M = new T.Matrix4();
  function rotY(v, ang) { const c = Math.cos(ang), s = Math.sin(ang), x = v.x * c + v.z * s, z = -v.x * s + v.z * c; v.x = x; v.z = z; return v; }
  function ease(x) { x = Math.max(0, Math.min(1, x)); return x * x * (3 - 2 * x); }
  function lerp(a, b, t) { return a + (b - a) * t; }

  function place(f, side, px, py, pz, ang, phi, off, louvre, lenK) {
    // length along the feather's own direction in the wing plane (ang), turned with its bone's flap (phi)
    fv.set(side * Math.cos(ang), Math.sin(ang), 0); rotY(fv, -side * phi);
    nv.set(0, 0, 1); rotY(nv, -side * phi);
    wv.crossVectors(fv, nv);
    if (louvre) { // the upstroke: each feather turns about its own shaft and lets the air through
      const c = Math.cos(louvre), s = Math.sin(louvre), wx = wv.x * c + nv.x * s, wy = wv.y * c + nv.y * s, wz = wv.z * c + nv.z * s;
      nv.set(nv.x * c - wv.x * s, nv.y * c - wv.y * s, nv.z * c - wv.z * s); wv.set(wx, wy, wz);
    }
    M.makeBasis(wv, fv, nv);
    M.elements[4] *= lenK; M.elements[5] *= lenK; M.elements[6] *= lenK;
    M.setPosition(px + nv.x * off, py + nv.y * off, pz + nv.z * off);
    f.matrix.copy(M);
  }

  function poseWing(w, P, glow) {
    const s = w.side, ph = [P.p0, P.p0 + P.p1, P.p0 + P.p1 + P.p2], an = [P.a0 * D, P.a1 * D, P.a2 * D];
    J[0].set(0, 0, 0);
    for (let i = 0; i < 3; i++) {
      dv.set(s * Math.cos(an[i]), Math.sin(an[i]), 0); rotY(dv, -s * ph[i] * D);
      J[i + 1].copy(J[i]).addScaledVector(dv, BONE[i]);
      nv.set(0, 0, 1); rotY(nv, -s * ph[i] * D); wv.crossVectors(dv, nv);
      M.makeBasis(wv, dv, nv); M.setPosition(J[i].x, J[i].y, J[i].z); w.spars[i].matrix.copy(M);
    }
    w.knuckles[0].position.copy(J[1]); w.knuckles[1].position.copy(J[2]);
    const c = P.casc;
    const along = (f, a, b) => pv.copy(a).lerp(b, f);
    for (let i = 0; i < w.ter.length; i++) {
      const t = i / (w.ter.length - 1), ci = ease((c - 0.05 * t) / 0.6);
      along(0.12 + 0.86 * t, J[0], J[1]);
      place(w.ter[i], s, pv.x, pv.y, pv.z, lerp(-90, P.ter + (t - 0.5) * 8, ci) * D, ph[0] * D, i * 0.16 - 0.4, 0, lerp(0.8, 1, ci));
    }
    for (let i = 0; i < w.sec.length; i++) {
      const t = i / (w.sec.length - 1), ci = ease((c - 0.1 - 0.15 * t) / 0.6);
      along(0.04 + 0.94 * t, J[1], J[2]);
      place(w.sec[i], s, pv.x, pv.y, pv.z, lerp(-90, P.sec + t * (P.fanIn - P.sec) * 0.55, ci) * D, ph[1] * D, 0.5 + i * 0.16, P.louvre * 0.5 * D, lerp(0.8, 1, ci));
    }
    for (let i = 0; i < w.prim.length; i++) {
      const t = i / (w.prim.length - 1), ci = ease((c - 0.25 - 0.3 * t) / 0.45);
      along(0.08 + 0.92 * t, J[2], J[3]);
      const open = lerp(P.fanIn, P.fanOut, Math.pow(t, 0.85));
      place(w.prim[i], s, pv.x, pv.y, pv.z, lerp(-97 + 9 * t, open, ci) * D, ph[2] * D, 1.9 + i * 0.16, P.louvre * D, lerp(0.66, 1, ci));
    }
    for (let i = 0; i < w.cov.length; i++) {
      const t = i / (w.cov.length - 1), ci = ease((c - 0.2 * t) / 0.6);
      const b = t < 0.3 ? 0 : t < 0.68 ? 1 : 2, f = b === 0 ? t / 0.3 : b === 1 ? (t - 0.3) / 0.38 : (t - 0.68) / 0.32;
      along(0.06 + 0.9 * f, J[b], J[b + 1]);
      const base = b === 0 ? P.ter : b === 1 ? P.sec : lerp(P.fanIn, P.fanOut, f * 0.7);
      place(w.cov[i], s, pv.x, pv.y, pv.z, lerp(-90, base, ci) * D, ph[b] * D, 3.6 + (i % 2) * 0.2, 0, 1);
    }
    for (let i = 0; i < w.cov2.length; i++) {
      const t = i / (w.cov2.length - 1), ci = ease((c - 0.2 * t) / 0.6);
      const b = t < 0.3 ? 0 : t < 0.68 ? 1 : 2, f = b === 0 ? t / 0.3 : b === 1 ? (t - 0.3) / 0.38 : (t - 0.68) / 0.32;
      along(0.1 + 0.86 * f, J[b], J[b + 1]);
      const base = b === 0 ? P.ter : b === 1 ? P.sec : lerp(P.fanIn, P.fanOut, f * 0.6);
      place(w.cov2[i], s, pv.x, pv.y, pv.z, lerp(-90, base, ci) * D, ph[b] * D, 4.7 + (i % 2) * 0.2, 0, 1);
    }
    for (let i = 0; i < 3; i++) {
      const h = w.halos[i]; h.position.copy(J[i + 1]).addScaledVector(J[i], 0.0); h.position.y -= 7 * c;
      const sc = (30 + i * 10) * (0.5 + 0.5 * c); h.scale.set(sc, sc * 1.25, 1); h.material.opacity = (0.1 + 0.14 * glow) * (0.3 + 0.7 * c);
    }
  }

  // ---- today's wing, from WingLayer's own numbers (model space: x to the left, y down, z behind; a unit is 16 texels) ----
  const W_FOLD = [[0.35, -0.45, 0.75], [0.12, 0.9, 0.35], [0.06, 1, 0.25]], W_OPEN = [[0.92, -0.38, 0.12], [1, -0.04, 0.1], [0.9, 0.22, 0.3]];
  const W_SWEEP = [0, 0.3, 0.55], W_BONE = [0.55, 0.78, 0.92], W_FEATHER = [0.55, 1.05, 1.2, 0.55], W_U = [0, 0.3, 0.66, 1], STRIPS = 14;
  function oldFeather(u) { for (let i = 0; i < 3; i++) if (u <= W_U[i + 1]) return lerp(W_FEATHER[i], W_FEATHER[i + 1], (u - W_U[i]) / (W_U[i + 1] - W_U[i])); return W_FEATHER[3]; }
  function makeOld() {
    const g = new T.Group(), n = STRIPS * 2 * 4, pos = new Float32Array(n * 3), uv = new Float32Array(n * 2), idx = [];
    for (let q = 0; q < STRIPS * 2; q++) idx.push(q * 4, q * 4 + 1, q * 4 + 2, q * 4, q * 4 + 2, q * 4 + 3);
    const geo = new T.BufferGeometry();
    geo.setAttribute('position', new T.BufferAttribute(pos, 3)); geo.setAttribute('uv', new T.BufferAttribute(uv, 2)); geo.setIndex(idx);
    const tex = new T.Texture(); const img = new Image();
    img.onload = () => { tex.image = img; tex.needsUpdate = true; }; img.src = window.WING_TODAY_PNG || '';
    tex.magFilter = T.LinearFilter;
    const body = new T.Mesh(geo, new T.MeshBasicMaterial({ map: tex, transparent: true, opacity: 0.58, color: 0xa8dcff, side: T.DoubleSide, depthWrite: false }));
    const glow = new T.Mesh(geo, new T.MeshBasicMaterial({ map: tex, transparent: true, opacity: 0.24, color: 0x7fc0ff, side: T.DoubleSide, depthWrite: false, blending: T.AdditiveBlending }));
    glow.scale.setScalar(1.07);
    g.add(body); g.add(glow); body.frustumCulled = glow.frustumCulled = false;
    g.position.set(0, 24, 0);
    return { g, pos, uv, geo };
  }
  const LX = [0, 0, 0, 0], LY = [0, 0, 0, 0], LZ = [0, 0, 0, 0];
  function poseOld(o, spread, beat, sweep) {
    let q = 0; const P = o.pos, UV = o.uv;
    for (const side of [1, -1]) {
      LX[0] = side * 0.1; LY[0] = 0.1; LZ[0] = 0.17;
      for (let b = 0; b < 3; b++) {
        let dx = lerp(W_FOLD[b][0], W_OPEN[b][0], spread), dy = lerp(W_FOLD[b][1], W_OPEN[b][1], spread);
        const dz = lerp(W_FOLD[b][2], W_OPEN[b][2] + W_SWEEP[b] * sweep, spread);
        const a = beat * (0.55 + 0.25 * b) * spread, c = Math.cos(a), sn = Math.sin(a), rx = dx * c - dy * sn, ry = dx * sn + dy * c;
        dx = rx; dy = ry;
        const l = Math.sqrt(dx * dx + dy * dy + dz * dz);
        LX[b + 1] = LX[b] + side * dx / l * W_BONE[b]; LY[b + 1] = LY[b] + dy / l * W_BONE[b]; LZ[b + 1] = LZ[b] + dz / l * W_BONE[b];
      }
      let fy = lerp(0.3, 0.95, spread), fz = lerp(1, 0.32, spread); const fl = Math.sqrt(fy * fy + fz * fz); fy /= fl; fz /= fl;
      let px = 0, py = 0, pz = 0, tx0 = 0, ty0 = 0, tz0 = 0, pu = 0;
      for (let i = 0; i <= STRIPS; i++) {
        const u = i / STRIPS, bu = u * 3, b = Math.min(2, Math.floor(bu)), f = bu - b;
        const x = lerp(LX[b], LX[b + 1], f), y = lerp(LY[b], LY[b + 1], f), z = lerp(LZ[b], LZ[b + 1], f);
        let len = oldFeather(u) * lerp(0.55, 1, spread); if (u > 0.68 && (i & 1) === 1) len *= 0.8;
        const tx = x + side * 0.05 * u, ty = y + fy * len, tz = z + fz * len;
        if (i > 0) {
          const u0 = side < 0 ? 1 - pu : pu, u1 = side < 0 ? 1 - u : u, v = [px, py, pz, u0, 0, x, y, z, u1, 0, tx, ty, tz, u1, 1, tx0, ty0, tz0, u0, 1];
          for (let k = 0; k < 4; k++) {
            const o3 = (q * 4 + k) * 3, o2 = (q * 4 + k) * 2;
            P[o3] = -v[k * 5] * 16; P[o3 + 1] = -v[k * 5 + 1] * 16; P[o3 + 2] = v[k * 5 + 2] * 16; UV[o2] = v[k * 5 + 3]; UV[o2 + 1] = 1 - v[k * 5 + 4];
          }
          q++;
        }
        px = x; py = y; pz = z; tx0 = tx; ty0 = ty; tz0 = tz; pu = u;
      }
    }
    o.geo.attributes.position.needsUpdate = true; o.geo.attributes.uv.needsUpdate = true;
    o.g.visible = spread > 0.01;
  }
  function oldBeat(t) { if (t < 4) return -Math.sin(t / 4 * Math.PI / 2); if (t >= 12) return 0; const u = (t - 4) / 8; return -Math.cos(u * Math.PI / 2) + 0.35 * Math.sin(u * Math.PI); }

  // ---- states: what the bones and feathers do, as a function of time in the state (seconds) ----
  const FOLD = { a0: 92, a1: 38, a2: -101, p0: 16, p1: 10, p2: 8, fanIn: -92, fanOut: -89, sec: -90, ter: -92, casc: 0, louvre: 0 };
  const OPEN = { a0: 66, a1: 16, a2: -18, p0: 8, p1: 0, p2: 0, fanIn: -72, fanOut: -8, sec: -84, ter: -97, casc: 1, louvre: 0 };
  const GLIDE = { a0: 50, a1: 6, a2: -15, p0: 6, p1: 5, p2: 7, fanIn: -70, fanOut: -13, sec: -86, ter: -95, casc: 1, louvre: 0 };
  const DIVE = { a0: 26, a1: -128, a2: -64, p0: 22, p1: 2, p2: 4, fanIn: -84, fanOut: -70, sec: -100, ter: -98, casc: 1, louvre: 0 };
  const FLARE = { a0: 64, a1: 18, a2: -6, p0: -34, p1: -12, p2: -10, fanIn: -78, fanOut: 2, sec: -84, ter: -96, casc: 1, louvre: 0 };
  function mix(A, B, t) { const o = {}; for (const k in A) o[k] = lerp(A[k], B[k], t); return o; }
  function backOut(x) { x = Math.max(0, Math.min(1, x)); const c = 1.9; return 1 + (c + 1) * Math.pow(x - 1, 3) + c * Math.pow(x - 1, 2); }
  // a beat: down hard, the hand lagging; back up with the wrist folded and the feathers parted
  function beat(base, t, period, amp) {
    const u = (t % period) / period, o = Object.assign({}, base);
    const down = 0.34;
    if (u < down) {
      const k = ease(u / down);
      o.p0 = base.p0 + amp * (0.62 - 1.5 * k); o.p1 = base.p1 + amp * 0.34 * (1 - k); o.p2 = base.p2 + amp * 0.5 * (1 - k);
      o.fanOut = base.fanOut + 5; o.louvre = 0;
    } else {
      const k = ease((u - down) / (1 - down)), bend = Math.sin(k * Math.PI);
      o.p0 = base.p0 + amp * (-0.88 + 1.5 * k); o.p1 = base.p1 - amp * 0.34 * bend + amp * 0.34 * k * 0; o.p2 = base.p2 - amp * 0.7 * bend;
      o.a1 = base.a1 - 14 * bend; o.a2 = base.a2 - 26 * bend; o.louvre = 34 * bend; o.fanOut = base.fanOut - 10 * bend;
    }
    return o;
  }
  const STATES = {
    folded: { body: { pitch: 0 }, f: () => FOLD, old: () => [0, 0, 0], glow: 0.2, label: 'Folded on your back' },
    unfold: {
      body: { pitch: 0 }, glow: 1, label: 'Opening',
      f: t => {
        if (t < 0.22) { const k = ease(t / 0.22); const o = mix(FOLD, FOLD, 0); o.a0 = FOLD.a0 + 6 * k; o.p0 = FOLD.p0 + 18 * k; o.a1 = FOLD.a1 + 14 * k; return o; }
        const k = backOut((t - 0.22) / 0.42), A = mix(FOLD, OPEN, k); A.casc = Math.min(1, (t - 0.22) / 0.5 * 1.25);
        A.p0 = lerp(FOLD.p0 + 18, OPEN.p0, k); if (t > 0.64) { const s = Math.exp(-(t - 0.64) * 6) * Math.sin((t - 0.64) * 14); A.fanOut += 7 * s; A.p2 += 6 * s; A.casc = 1; }
        return A;
      },
      old: t => { const ticks = t * 20; return [1 - Math.pow(1 - 0.32, ticks), oldBeat(ticks), 0]; }
    },
    glide: {
      body: { pitch: -80 }, glow: 0.6, label: 'Gliding', speed: 1,
      f: t => { const o = Object.assign({}, GLIDE), w = Math.sin(t * 9) * 0.6 + Math.sin(t * 23) * 0.4; o.p2 += w * 1.6; o.fanOut += w * 1.2; o.p0 += Math.sin(t * 1.3) * 1.5; return o; },
      old: t => [1, 0.06 * Math.sin(t * 20 * 0.21), 0.35]
    },
    beat: {
      body: { pitch: -74 }, glow: 0.9, label: 'Flying', speed: 1.4,
      f: t => beat(GLIDE, t, 0.9, 46),
      old: t => [1, oldBeat((t * 20) % 18), 0.5]
    },
    dive: {
      body: { pitch: -128 }, glow: 0.5, label: 'Diving', speed: 2.4,
      f: t => { const o = Object.assign({}, DIVE), w = Math.sin(t * 31) * 0.5 + Math.sin(t * 47) * 0.5; o.fanOut += w * 1.5; o.p2 += w; return o; },
      old: t => [1, 0.06 * Math.sin(t * 20 * 0.21), 1]
    },
    hover: {
      body: { pitch: -8 }, glow: 1, label: 'Hovering',
      f: t => beat(mix(OPEN, GLIDE, 0.3), t, 1.5, 40),
      old: t => [1, oldBeat((t * 20) % 18), 0]
    },
    land: {
      body: { pitch: -26 }, glow: 1, label: 'Landing',
      f: t => { const k = ease(t / 0.3), o = mix(GLIDE, FLARE, k); if (t > 0.3) { const s = Math.exp(-(t - 0.3) * 4) * Math.sin((t - 0.3) * 11); o.p0 += 8 * s; o.fanOut += 5 * s; } return o; },
      old: t => [1, oldBeat(Math.min(12, t * 20)), 0]
    }
  };

  // ---- tiers: the wings grow with you: one pair, two, then three and a halo ----
  const TIERS = {
    wings: { sky: 'cool', pairs: [{ k: 0.94, roll: 0, pal: 'light', lag: 0 }] },
    skyborn: { sky: 'cool', pairs: [{ k: 1.18, roll: -6, pal: 'sky', lag: 0 }, { k: 0.6, roll: 44, pal: 'sky', lag: 0.12 }] },
    sovereign: { sky: 'gold', halo: true, pairs: [{ k: 0.82, roll: -50, pal: 'gold', lag: 0 }, { k: 1.26, roll: -2, pal: 'gold', lag: 0.1 }, { k: 0.86, roll: 40, pal: 'gold', lag: 0.2 }] }
  };

  const rigNew = makeFigure(), rigOld = makeFigure();
  scene.add(rigNew.root); scene.add(rigOld.root);
  const oldWing = makeOld(); rigOld.body.add(oldWing.g);
  const wingsHolder = new T.Group(); wingsHolder.position.set(0, 21, 2.6); rigNew.body.add(wingsHolder);
  let wings = [], haloRing = null;
  function buildTier(name) {
    while (wingsHolder.children.length) wingsHolder.remove(wingsHolder.children[0]);
    wings = [];
    const tier = TIERS[name];
    tier.pairs.forEach((cfg, pi) => {
      for (const s of [1, -1]) { const w = makeWing(s, cfg); w.g.position.set(s * 2.4, 0, pi * 0.9); wingsHolder.add(w.g); wings.push(w); }
    });
    if (haloRing) { rigNew.body.remove(haloRing); haloRing = null; }
    if (tier.halo) {
      haloRing = new T.Group(); haloRing.position.set(0, 38.5, 0);
      for (let i = 0; i < 14; i++) { const a = i / 14 * Math.PI * 2; const b = box(2, 1, 1.4, i % 2 ? 0xffe9a8 : 0xfffbea, Math.cos(a) * 7, 0, Math.sin(a) * 7); b.rotation.y = -a + Math.PI / 2; haloRing.add(b); }
      const sp = new T.Sprite(new T.SpriteMaterial({ map: HALO.gold, blending: T.AdditiveBlending, depthWrite: false, transparent: true, opacity: 0.35 })); sp.scale.set(30, 12, 1); haloRing.add(sp);
      rigNew.body.add(haloRing);
    }
    scene.background = SKY[tier.sky];
  }

  // motes shed from the wingtips on a beat (square, never a soft sprite)
  const MOTES = []; const moteMat = new T.MeshBasicMaterial({ color: 0xeafaff, transparent: true, opacity: 0.9, blending: T.AdditiveBlending, depthWrite: false });
  for (let i = 0; i < 48; i++) { const m = new T.Mesh(new T.BoxGeometry(1, 1, 1), moteMat); m.visible = false; scene.add(m); MOTES.push({ m, life: 0, vx: 0, vy: 0, vz: 0 }); }
  let moteAt = 0;
  function shed(x, y, z, vy) {
    const o = MOTES[moteAt++ % MOTES.length]; o.life = 0.5 + Math.random() * 0.4; o.m.visible = true; o.m.position.set(x, y, z);
    o.vx = (Math.random() - 0.5) * 8; o.vy = vy + (Math.random() - 0.5) * 6; o.vz = 8 + Math.random() * 14;
  }

  // ---- view state ----
  let state = 'hover', tier = 'wings', which = 'both', tState = 0.25, fromP = null, blend = 1, lastP = Object.assign({}, OPEN);
  let az = 0.5, el = 0.12, drag = null, auto = true, pitchNow = -8, speedNow = 0;
  const reduced = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  if (reduced) auto = false;

  function setState(s) { if (!STATES[s]) return; fromP = Object.assign({}, lastP); blend = 0; state = s; tState = 0; sync(); }
  function setTier(t) { if (!TIERS[t]) return; tier = t; buildTier(t); sync(); }
  function setWhich(w) { which = w; layout(); sync(); }
  function sync() {
    document.querySelectorAll('[data-wing-state]').forEach(b => b.setAttribute('aria-pressed', b.dataset.wingState === state));
    document.querySelectorAll('[data-wing-tier]').forEach(b => b.setAttribute('aria-pressed', b.dataset.wingTier === tier));
    document.querySelectorAll('[data-wing-which]').forEach(b => b.setAttribute('aria-pressed', b.dataset.wingWhich === which));
    const cap = document.getElementById('wingcap');
    if (cap) cap.textContent = STATES[state].label + (which === 'both' ? ' · left: today · right: proposed' : which === 'old' ? ' · today' : ' · proposed');
  }
  function layout() {
    const gap = which === 'both' ? 46 : 0;
    rigOld.root.visible = which !== 'new'; rigNew.root.visible = which !== 'old';
    rigOld.root.position.x = which === 'both' ? -gap : 0; rigNew.root.position.x = which === 'both' ? gap : 0;
  }
  document.addEventListener('click', e => {
    const b = e.target.closest('[data-wing-state],[data-wing-tier],[data-wing-which]'); if (!b) return;
    if (b.dataset.wingState) setState(b.dataset.wingState);
    if (b.dataset.wingTier) setTier(b.dataset.wingTier);
    if (b.dataset.wingWhich) setWhich(b.dataset.wingWhich);
  });
  const el0 = renderer.domElement;
  el0.style.touchAction = 'pan-y'; el0.style.cursor = 'grab'; el0.style.width = '100%'; el0.style.height = '100%'; el0.style.display = 'block';
  el0.addEventListener('pointerdown', e => { drag = { x: e.clientX, y: e.clientY, az, el }; auto = false; el0.setPointerCapture(e.pointerId); el0.style.cursor = 'grabbing'; });
  el0.addEventListener('pointermove', e => { if (!drag) return; az = drag.az - (e.clientX - drag.x) * 0.01; el = Math.max(-0.6, Math.min(1.2, drag.el + (e.clientY - drag.y) * 0.008)); });
  const up = () => { drag = null; el0.style.cursor = 'grab'; };
  el0.addEventListener('pointerup', up); el0.addEventListener('pointercancel', up);

  function resize() {
    const w = host.clientWidth, h = host.clientHeight; if (!w || !h) return;
    renderer.setSize(w, h, false); cam.aspect = w / h; cam.updateProjectionMatrix();
  }
  if (window.ResizeObserver) new ResizeObserver(resize).observe(host); window.addEventListener('resize', resize);

  function poseBody(rig, pitch, t, moving) {
    rig.body.rotation.x = pitch * D;
    // the body turns about its chest so the figure stays in frame
    rig.body.position.set(0, 0, 0);
    const prone = Math.min(1, Math.abs(pitch) / 80);
    rig.root.position.y = lerp(0, 18, prone) + (moving ? Math.sin(t * 2.1) * 0.8 : 0);
    rig.root.position.z = lerp(0, -14, prone);
    for (let i = 0; i < 2; i++) {
      const s = i ? 1 : -1;
      rig.arms[i].rotation.set(lerp(0.05, 0.35, prone), 0, s * lerp(0.08, 0.32, prone));
      rig.legs[i].rotation.set(lerp(0, 0.12, prone) + (prone < 0.3 ? 0 : s * 0.05), 0, s * 0.02);
    }
  }

  let last = performance.now(), visible = true;
  if (window.IntersectionObserver) new IntersectionObserver(es => { visible = es[0].isIntersecting; }).observe(host);
  function frame(now) {
    requestAnimationFrame(frame);
    const dt = Math.min(0.05, (now - last) / 1000); last = now;
    if (!visible) return;
    draw(dt, now);
  }
  function draw(dt, now) {
    tState += dt; blend = Math.min(1, blend + dt / 0.32);
    const S = STATES[state];
    let P = S.f(tState);
    if (fromP && blend < 1 && state !== 'unfold') P = mix(fromP, P, ease(blend));
    lastP = P;
    pitchNow += (S.body.pitch - pitchNow) * Math.min(1, dt * 7);
    speedNow += ((S.speed || 0) - speedNow) * Math.min(1, dt * 3);
    const moving = !!S.speed;
    poseBody(rigNew, pitchNow, tState, moving); poseBody(rigOld, pitchNow, tState, moving);
    const prev = wings.length ? wings[0]._p0 : 0;
    for (const w of wings) {
      const lag = w.cfg.lag;
      let Pw = P;
      if (lag) { Pw = S.f(Math.max(0, tState - lag)); if (fromP && blend < 1 && state !== 'unfold') Pw = mix(fromP, Pw, ease(blend)); }
      poseWing(w, Pw, S.glow);
      w._p0 = Pw.p0;
    }
    // a hard downstroke sheds a few motes of light from the tips
    if (dt > 0 && wings.length && which !== 'old' && prev - wings[0]._p0 > 110 * dt && Math.random() < 0.5) {
      for (const w of wings) if (w.cfg.k > 0.9) { J[3].set(0, 0, 0); w.g.updateWorldMatrix(true, false); pv.set(w.side * 34, -6, 0).applyMatrix4(w.g.matrixWorld); shed(pv.x, pv.y, pv.z, -4); }
    }
    for (const o of MOTES) { if (o.life <= 0) continue; o.life -= dt; o.m.position.x += o.vx * dt; o.m.position.y += o.vy * dt; o.m.position.z += o.vz * dt * (0.4 + speedNow); const k = Math.max(0, o.life); o.m.scale.setScalar(0.35 + 0.7 * k); if (o.life <= 0) o.m.visible = false; }
    rigNew.core.scale.setScalar(1 + 0.25 * Math.sin(now / 400));
    if (haloRing) haloRing.rotation.y += dt * 0.5;
    const od = S.old(tState); poseOld(oldWing, od[0], od[1], od[2]);
    if (auto) az += dt * 0.22;
    const R = (which === 'both' ? 205 : 150) * Math.max(1, 1.45 / cam.aspect), ty = 26;
    cam.position.set(Math.sin(az) * Math.cos(el) * R, ty + Math.sin(el) * R, Math.cos(az) * Math.cos(el) * R);
    cam.lookAt(0, ty, 2);
    renderer.render(scene, cam);
  }
  buildTier(tier);
  if (host.clientWidth < 560) which = 'new';
  layout(); sync(); resize();
  requestAnimationFrame(frame);
  window.WingStudio = {
    setState, setTier, setWhich, setView: (a, e) => { az = a; el = e; auto = false; },
    // a still of a state at a time (for checking the page's pictures)
    still: (s, t, a, e) => { state = s; tState = t; blend = 1; fromP = null; az = a; el = e; auto = false; pitchNow = STATES[s].body.pitch; speedNow = STATES[s].speed || 0; sync(); draw(0, performance.now()); }
  };
})();
