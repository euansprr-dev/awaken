/* The carrying hand, as a mock-up (docs/interactions/PLAN.md). Planning tool only: nothing in the game reads it.
 * It draws over a real screenshot of the crafting table's scene, in the game's own camera (a port of
 * dev.awaken.anims.station.StationView), so what you see is where the hand would be on that screen. */
(function () {
  'use strict';
  var CELL = 3 / 16, GRID_HALF = 1.5 * CELL, RESULT_IN = 0.41, RESULT_HALF = 0.085, MARGIN = 1.06;
  function add(a, b) { return [a[0] + b[0], a[1] + b[1], a[2] + b[2]]; }
  function sub(a, b) { return [a[0] - b[0], a[1] - b[1], a[2] - b[2]]; }
  function mul(a, k) { return [a[0] * k, a[1] * k, a[2] * k]; }
  function dot(a, b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }
  function cross(a, b) { return [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]]; }
  function norm(a) { var l = Math.hypot(a[0], a[1], a[2]) || 1; return [a[0] / l, a[1] / l, a[2] / l]; }
  function lerp(a, b, t) { return a + (b - a) * t; }
  function clamp(x, a, b) { return x < a ? a : x > b ? b : x; }
  function smooth(t) { t = clamp(t, 0, 1); return t * t * (3 - 2 * t); }
  function outCubic(t) { t = clamp(t, 0, 1); return 1 - Math.pow(1 - t, 3); }

  function rot(yaw, pitch) {
    var y = yaw * Math.PI / 180, p = pitch * Math.PI / 180;
    var f = [-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p)];
    var r = norm(cross(f, [0, 1, 0]));
    return { f: f, r: r, u: cross(r, f) };
  }
  function layout(gw, gh, hintLines) {
    var left = Math.floor((gw - 176) / 2), top = gh - 165;
    var px0 = left - 4, py0 = top + 80, px1 = left + 176 + 4, py1 = top + 162;
    var hintY = py0 - 7 - 10 * hintLines, half = Math.floor(gw / 2) - 8;
    return { px0: px0, py0: py0, px1: px1, py1: py1, hintY: hintY, rx0: Math.floor(gw / 2) - half, ry0: 6, rx1: Math.floor(gw / 2) + half, ry1: hintY - 4 };
  }
  /* The table sits at the origin: its top is y = 1, the way in is +z, across to your right is -x. */
  var RESULT_SIDE = 0.39;
  function spotToday(slot) {
    if (slot === 0) return [0.5, 1.012, 0.5 - RESULT_IN];
    return spot(slot);
  }
  /* The plan's slots: the grid where it is, the result on the right border (vanilla's side), never under a reaching arm. */
  function spot(slot) {
    if (slot === 0) return [0.5 - RESULT_SIDE, 1.012, 0.5];
    var row = Math.floor((slot - 1) / 3), col = (slot - 1) % 3;
    return [0.5 - (col - 1) * CELL, 1.01, 0.5 + (1 - row) * CELL];
  }
  function reach(slot, axis) { return axis === 1 ? 0.03 : slot === 0 ? RESULT_HALF : CELL / 2; }
  function frame(eye, lay, gw, gh, aspect) {
    var i, c, lo = [1e9, 1e9, 1e9], hi = [-1e9, -1e9, -1e9];
    for (i = 0; i < 10; i++) {
      var p = spotToday(i), m = reach(i, 0), mu = reach(i, 1);
      lo = [Math.min(lo[0], p[0] - m), Math.min(lo[1], p[1] - mu), Math.min(lo[2], p[2] - m)];
      hi = [Math.max(hi[0], p[0] + m), Math.max(hi[1], p[1] + mu), Math.max(hi[2], p[2] + m)];
    }
    var a = sub(mul(add(lo, hi), 0.5), eye);
    var yaw = Math.atan2(-a[0], a[2]) * 180 / Math.PI, pitch = -Math.atan2(a[1], Math.hypot(a[0], a[2])) * 180 / Math.PI;
    var nx0 = lay.rx0 * 2 / gw - 1, nx1 = lay.rx1 * 2 / gw - 1, ny0 = 1 - lay.ry0 * 2 / gh, ny1 = 1 - lay.ry1 * 2 / gh;
    var hx = (nx1 - nx0) / 2, hy = (ny0 - ny1) / 2, ncx = (nx0 + nx1) / 2, ncy = (ny0 + ny1) / 2;
    var tanMax = Math.tan(50 * Math.PI / 180), tan = tanMax;
    for (var it = 0; it < 4; it++) {
      var R = rot(yaw, pitch), b = [1e9, -1e9, 1e9, -1e9];
      for (i = 0; i < 10; i++) {
        var q = spotToday(i), ma = reach(i, 0), mv = reach(i, 1);
        for (c = 0; c < 8; c++) {
          var w = sub([q[0] + ((c & 1) ? ma : -ma), q[1] + ((c & 2) ? mv : -mv), q[2] + ((c & 4) ? ma : -ma)], eye);
          var z = Math.max(0.05, dot(w, R.f)), tx = dot(w, R.r) / z, ty = dot(w, R.u) / z;
          b = [Math.min(b[0], tx), Math.max(b[1], tx), Math.min(b[2], ty), Math.max(b[3], ty)];
        }
      }
      tan = clamp(Math.max((b[1] - b[0]) / (2 * aspect * hx), (b[3] - b[2]) / (2 * hy)) * MARGIN, 0.05, tanMax);
      yaw += (Math.atan((b[0] + b[1]) / 2) - Math.atan(ncx * tan * aspect)) * 180 / Math.PI;
      pitch += (Math.atan(ncy * tan) - Math.atan((b[2] + b[3]) / 2)) * 180 / Math.PI;
    }
    return { yaw: yaw, pitch: pitch, tan: tan };
  }
  /* The same framing for any station: boxes = [[x, y, z, halfAcross, halfUp, halfIn], ...], the way in +z. */
  function frameBoxes(boxes, eye, lay, gw, gh, aspect) {
    var i, c, lo = [1e9, 1e9, 1e9], hi = [-1e9, -1e9, -1e9];
    for (i = 0; i < boxes.length; i++) {
      var p = boxes[i], m = Math.max(p[3], p[5]);
      lo = [Math.min(lo[0], p[0] - m), Math.min(lo[1], p[1] - p[4]), Math.min(lo[2], p[2] - m)];
      hi = [Math.max(hi[0], p[0] + m), Math.max(hi[1], p[1] + p[4]), Math.max(hi[2], p[2] + m)];
    }
    var a = sub(mul(add(lo, hi), 0.5), eye);
    var yaw = Math.atan2(-a[0], a[2]) * 180 / Math.PI, pitch = -Math.atan2(a[1], Math.hypot(a[0], a[2])) * 180 / Math.PI;
    var nx0 = lay.rx0 * 2 / gw - 1, nx1 = lay.rx1 * 2 / gw - 1, ny0 = 1 - lay.ry0 * 2 / gh, ny1 = 1 - lay.ry1 * 2 / gh;
    var hx = (nx1 - nx0) / 2, hy = (ny0 - ny1) / 2, ncx = (nx0 + nx1) / 2, ncy = (ny0 + ny1) / 2;
    var tanMax = Math.tan(50 * Math.PI / 180), tan = tanMax;
    for (var it = 0; it < 4; it++) {
      var R = rot(yaw, pitch), b = [1e9, -1e9, 1e9, -1e9];
      for (i = 0; i < boxes.length; i++) {
        var q = boxes[i];
        for (c = 0; c < 8; c++) {
          var w = sub([q[0] + ((c & 1) ? q[3] : -q[3]), q[1] + ((c & 2) ? q[4] : -q[4]), q[2] + ((c & 4) ? q[5] : -q[5])], eye);
          var z = Math.max(0.05, dot(w, R.f)), tx = dot(w, R.r) / z, ty = dot(w, R.u) / z;
          b = [Math.min(b[0], tx), Math.max(b[1], tx), Math.min(b[2], ty), Math.max(b[3], ty)];
        }
      }
      tan = clamp(Math.max((b[1] - b[0]) / (2 * aspect * hx), (b[3] - b[2]) / (2 * hy)) * MARGIN, 0.05, tanMax);
      yaw += (Math.atan((b[0] + b[1]) / 2) - Math.atan(ncx * tan * aspect)) * 180 / Math.PI;
      pitch += (Math.atan(ncy * tan) - Math.atan((b[2] + b[3]) / 2)) * 180 / Math.PI;
    }
    return { yaw: yaw, pitch: pitch, tan: tan };
  }
  function Camera(eye, yaw, pitch, tan, aspect) {
    var R = rot(yaw, pitch);
    this.eye = eye; this.r = R.r; this.u = R.u; this.f = R.f; this.tan = tan; this.aspect = aspect;
  }
  /* world -> camera space (x right, y up, z ahead) */
  Camera.prototype.toCam = function (p) { var d = sub(p, this.eye); return [dot(d, this.r), dot(d, this.u), dot(d, this.f)]; };
  Camera.prototype.fromCam = function (c) { return add(this.eye, add(add(mul(this.r, c[0]), mul(this.u, c[1])), mul(this.f, c[2]))); };
  /* camera space -> the frame, 0..1 both ways, y down */
  Camera.prototype.screen = function (c) { var z = Math.max(c[2], 0.02); return [(c[0] / z / (this.tan * this.aspect) + 1) / 2, (1 - c[1] / z / this.tan) / 2]; };
  Camera.prototype.project = function (p) { return this.screen(this.toCam(p)); };
  Camera.prototype.rayDir = function (sx, sy) {
    var nx = sx * 2 - 1, ny = 1 - sy * 2;
    return norm(add(this.f, add(mul(this.r, nx * this.tan * this.aspect), mul(this.u, ny * this.tan))));
  };
  Camera.prototype.onPlane = function (sx, sy, y) {
    var d = this.rayDir(sx, sy);
    if (Math.abs(d[1]) < 1e-5) return null;
    var t = (y - this.eye[1]) / d[1];
    return t > 0 ? add(this.eye, mul(d, t)) : null;
  };

  // ---- pixel art of our own (16 x 16; a letter a colour, '.' clear). Stand-ins: in the game it is the real item. ----
  var PAL = { a: '#b8945f', b: '#9c7a48', c: '#7d6037', d: '#c9a56c', e: '#5e4526', s: '#6b4a25', t: '#8a6232', h: '#a0a4a8', i: '#cfd3d6', j: '#73777b', r: '#8f2f2a', q: '#d0544a', w: '#f1c9c0', m: '#4a2b1b', n: '#8a5232', o: '#b57a4c', k: '#1c1c20', l: '#34343b' };
  var ART = {
    plank: ['................', '.dddddddddddddd.', '.daaaaaaaabaaac.', '.daabaaaaaaaaac.', '.cccccccccccccc.', '.daaaaaabaaaaac.', '.dabaaaaaaaabac.', '.daaaaaaaaaaaac.',
      '.cccccccccccccc.', '.daaabaaaaaaaac.', '.daaaaaaaabaaac.', '.cccccccccccccc.', '.dabaaaaaaaaaac.', '.daaaaaabaaaabc.', '.eeeeeeeeeeeeee.', '................'],
    stick: ['................', '............ts..', '...........tts..', '..........tts...', '.........tts....', '........tts.....', '.......tts......', '......tts.......',
      '.....tts........', '....tts.........', '...tts..........', '..tts...........', '..ts............', '................', '................', '................'],
    beef: ['................', '......rrrr......', '....rrqqqqrr....', '...rqqqqqqqqr...', '..rqqwqqqqqqqr..', '..rqqqqqqwqqqr..', '.rqqqqqqqqqqqqr.', '.rqqwqqqqqqqqqr.',
      '.rqqqqqqqqwqqqr.', '.rqqqqqqqqqqqqr.', '..rqqqqwqqqqqr..', '..rrqqqqqqqqr...', '....rrqqqqrr....', '......rrrr......', '................', '................'],
    steak: ['................', '......mmmm......', '....mmnnnnmm....', '...mnnnnnnnnm...', '..mnnonnnnnnnm..', '..mnnnnnnonnnm..', '.mnnnnnnnnnnnnm.', '.mnnonnnnnnnnnm.',
      '.mnnnnnnnnonnnm.', '.mnnnnnnnnnnnnm.', '..mnnnnonnnnnm..', '..mmnnnnnnnnm...', '....mmnnnnmm....', '......mmmm......', '................', '................'],
    coal: ['................', '................', '.....kkkkk......', '....kllllkk.....', '...kllkllllk....', '..kllllllklk....', '..klklllllllk...', '..kllllkllllk...',
      '..klllllllklk...', '...kllkllllk....', '...kllllllk.....', '....kkllkk......', '......kk........', '................', '................', '................'],
    pick: ['................', '....bbbbbbb.....', '...bdaaaaadb....', '..bda.....tab...', '..ba.....tts.ab.', '..b.....tts...b.', '.......tts......', '......tts.......',
      '.....tts........', '....tts.........', '...tts..........', '..tts...........', '..ts............', '................', '................', '................']
  };

  function quad(ctx, W, H, p, fill) {
    ctx.beginPath();
    ctx.moveTo(p[0][0] * W, p[0][1] * H); ctx.lineTo(p[1][0] * W, p[1][1] * H); ctx.lineTo(p[2][0] * W, p[2][1] * H); ctx.lineTo(p[3][0] * W, p[3][1] * H);
    ctx.closePath(); ctx.fillStyle = fill; ctx.fill();
  }
  function shade(hex, k) {
    var n = parseInt(hex.slice(1), 16), r = n >> 16, g = (n >> 8) & 255, b = n & 255;
    return 'rgb(' + Math.round(clamp(r * k, 0, 255)) + ',' + Math.round(clamp(g * k, 0, 255)) + ',' + Math.round(clamp(b * k, 0, 255)) + ')';
  }
  /* A flat item: 16 x 16 cells on the plane through `o` spanned by `ax` (its right) and `ay` (its top), `size` across. */
  function drawItem(ctx, W, H, cam, art, o, ax, ay, size, light, alpha) {
    var rows = ART[art], px = size / 16, x, y;
    ctx.globalAlpha = alpha == null ? 1 : alpha;
    for (y = 0; y < 16; y++) for (x = 0; x < 16; x++) {
      var ch = rows[y][x];
      if (ch === '.') continue;
      var x0 = (x - 8) * px, x1 = x0 + px * 1.04, y0 = (8 - y) * px, y1 = y0 - px * 1.04;
      var c = [add(o, add(mul(ax, x0), mul(ay, y0))), add(o, add(mul(ax, x1), mul(ay, y0))), add(o, add(mul(ax, x1), mul(ay, y1))), add(o, add(mul(ax, x0), mul(ay, y1)))];
      quad(ctx, W, H, [cam.project(c[0]), cam.project(c[1]), cam.project(c[2]), cam.project(c[3])], shade(PAL[ch], light));
    }
    ctx.globalAlpha = 1;
  }
  /* One straight arm (a box 4 x 4 texels across), from its hand end at camera-space `e` along unit `a` toward the frame's
   * foot, `w` blocks across; skin for four texels from the hand, then the sleeve. Only faces that look at the eye are drawn. */
  function drawArm(ctx, W, H, cam, e, a, w, skin, sleeve, light, up, endY) {
    var b = norm(cross(a, up)), c = cross(b, a), t = w / 4, n, i, j;
    // long enough to pass under the inventory strip (the game's GUI is drawn over the arm), never a sleeve end in view
    var len = 0.2;
    for (n = 0; n < 80; n++) { var q = add(e, mul(a, len)), s = cam.screen(q); if (q[2] < 0.22 || s[1] > 1.12 || s[0] > 1.2 || s[0] < -0.2 || (s[1] > endY[0] && s[0] > endY[1] + 0.03 && s[0] < endY[2] - 0.03)) break; len += t; }
    var rowsN = Math.ceil(len / t);
    function P(i, j, face) { // a corner on a long face: i across 0..4, j along 0..rowsN
      var off = mul(a, j * t), h = w / 2, d;
      if (face === 0) d = add(mul(b, -h + i * t), mul(c, h));
      else if (face === 1) d = add(mul(b, -h + i * t), mul(c, -h));
      else if (face === 2) d = add(mul(c, -h + i * t), mul(b, h));
      else d = add(mul(c, -h + i * t), mul(b, -h));
      return add(e, add(off, d));
    }
    var normals = [c, mul(c, -1), b, mul(b, -1)], shades = [1.0, 0.62, 0.84, 0.72];
    for (var f = 0; f < 4; f++) {
      var mid = add(e, add(mul(a, len / 2), mul(normals[f], w / 2)));
      if (dot(normals[f], mul(mid, -1)) <= 0) continue;
      for (j = 0; j < rowsN; j++) for (i = 0; i < 4; i++) {
        var base = j < 4 ? skin : sleeve, hsh = ((i * 7 + j * 13 + f * 5) % 9) / 9;
        var k = light * shades[f] * (0.94 + 0.1 * hsh) * (j === 4 ? 0.82 : 1);
        quad(ctx, W, H, [cam.screen(P(i, j, f)), cam.screen(P(i + 1.03, j, f)), cam.screen(P(i + 1.03, j + 1.03, f)), cam.screen(P(i, j + 1.03, f))], shade(base, k));
      }
    }
    // the hand's end face
    if (dot(mul(a, -1), mul(e, -1)) > 0) {
      for (j = 0; j < 4; j++) for (i = 0; i < 4; i++) {
        var o = add(e, add(mul(b, -w / 2 + i * t), mul(c, -w / 2 + j * t)));
        quad(ctx, W, H, [cam.screen(o), cam.screen(add(o, mul(b, t * 1.03))), cam.screen(add(o, add(mul(b, t * 1.03), mul(c, t * 1.03)))), cam.screen(add(o, mul(c, t * 1.03)))],
          shade(skin, light * 0.9 * (0.95 + 0.08 * ((i + j) % 3) / 3)));
      }
    }
  }

  /**
   * mount(canvas, opts): opts.plate (the empty table, hands hidden), opts.plateHands (the same with today's hands), both
   * Image objects already loaded; opts.eye relative to the table's corner; opts.gui [w, h]; opts.onState(text).
   */
  function mount(canvas, opts) {
    var ctx = canvas.getContext('2d'), W = canvas.width, H = canvas.height, aspect = W / H;
    var gw = opts.gui[0], gh = opts.gui[1], lay = layout(gw, gh, 1);
    var fr = frame(opts.eye, lay, gw, gh, aspect), cam = new Camera(opts.eye, fr.yaw, fr.pitch, fr.tan, aspect);
    var S = {
      mode: 'new', grid: [null, null, null, null, null, null, null, null, null], carried: { art: 'plank', n: 6 }, result: null,
      ptr: [0.5, 0.3], vel: [0, 0], live: false, t: 0, dip: -1, dipSlot: -1, lift: 0, handIn: 1, drops: [], lastMove: 0, covered: 0, hand: opts.left ? -1 : 1
    };
    var strip = [lay.px0 / gw, lay.py0 / gh, lay.px1 / gw, lay.py1 / gh];

    function slotAt(sx, sy) {
      if (sy * gh >= lay.ry1) return -1;
      var p = cam.onPlane(sx, sy, 1.0);
      if (!p) return -1;
      var u = -(p[0] - 0.5), v = p[2] - 0.5;
      if (S.mode === 'new' ? Math.abs(u - RESULT_SIDE) <= RESULT_HALF + 0.02 && Math.abs(v) <= RESULT_HALF + 0.02
        : Math.abs(u) <= RESULT_HALF + 0.02 && Math.abs(v + RESULT_IN) <= RESULT_HALF + 0.02) return 0;
      if (Math.abs(u) > GRID_HALF || Math.abs(v) > GRID_HALF) return -1;
      var col = clamp(Math.floor((u + GRID_HALF) / CELL), 0, 2), row = clamp(Math.floor((GRID_HALF - v) / CELL), 0, 2);
      return 1 + row * 3 + col;
    }
    function recipe() {
      var g = S.grid, k;
      S.result = null;
      for (k = 0; k < 6; k++) if (g[k] && g[k + 3] && g[k].art === 'plank' && g[k + 3].art === 'plank') {
        var others = 0;
        for (var m = 0; m < 9; m++) if (g[m] && m !== k && m !== k + 3) others++;
        if (!others) S.result = { art: 'stick', n: 4 };
      }
    }
    function click(sx, sy, right) {
      var s = slotAt(sx, sy);
      if (s < 0) return;
      if (s === 0) {
        if (!S.result || (S.carried && S.carried.art !== S.result.art)) return;
        S.carried = { art: S.result.art, n: (S.carried ? S.carried.n : 0) + S.result.n };
        for (var m = 0; m < 9; m++) if (S.grid[m] && --S.grid[m].n <= 0) S.grid[m] = null;
        S.lift = S.t; recipe(); return;
      }
      var g = S.grid[s - 1];
      if (S.carried) {
        if (g && g.art !== S.carried.art) { S.grid[s - 1] = S.carried; S.carried = g; S.lift = S.t; }
        else {
          var n = right ? 1 : S.carried.n;
          S.grid[s - 1] = { art: S.carried.art, n: (g ? g.n : 0) + n };
          S.carried.n -= n;
          if (S.carried.n <= 0) S.carried = null;
          S.dip = S.t; S.dipSlot = s; S.drops.push({ slot: s, t: S.t });
        }
      } else if (g) {
        if (right && g.n > 1) { var half = Math.ceil(g.n / 2); S.carried = { art: g.art, n: half }; g.n -= half; }
        else { S.carried = g; S.grid[s - 1] = null; }
        S.lift = S.t;
      }
      recipe();
    }

    function drawSquareOutline(s, colour, lw) {
      var p = S.mode === 'new' ? spot(s) : spotToday(s), h = s === 0 ? RESULT_HALF : CELL / 2 - 0.012, y = 1.012;
      var c = [cam.project([p[0] - h, y, p[2] - h]), cam.project([p[0] + h, y, p[2] - h]), cam.project([p[0] + h, y, p[2] + h]), cam.project([p[0] - h, y, p[2] + h])];
      ctx.beginPath(); ctx.moveTo(c[0][0] * W, c[0][1] * H);
      for (var i = 1; i < 4; i++) ctx.lineTo(c[i][0] * W, c[i][1] * H);
      ctx.closePath(); ctx.strokeStyle = colour; ctx.lineWidth = lw; ctx.stroke();
    }
    function lying(st, s, lift, alpha) { // an item lying on a square, as the game lays it: flat, 0.16 across; a stack reads as a small pile
      var p = S.mode === 'new' ? spot(s) : spotToday(s), n = st.n > 32 ? 4 : st.n > 5 ? 3 : st.n > 1 ? 2 : 1, i;
      for (i = 0; i < n; i++) drawItem(ctx, W, H, cam, st.art, [p[0] + i * 0.012, 1.012 + lift + i * 0.006, p[2] - i * 0.012], [-1, 0, 0], [0, 0, 1], s === 0 ? 0.17 : 0.15, 1 - (n - 1 - i) * 0.1, alpha);
      if (st.n > 1) tag(cam.project([p[0] - 0.062, 1.03, p[2] - 0.062]), String(st.n));
    }
    function tag(sp, text) { // a count on glass, in the scene's own blue
      var x = sp[0] * W, y = sp[1] * H, f = Math.round(H * 0.026);
      ctx.font = '600 ' + f + 'px "JetBrains Mono", ui-monospace, monospace';
      var w = ctx.measureText(text).width + f * 0.6;
      ctx.fillStyle = 'rgba(15,23,36,0.82)'; ctx.fillRect(x - w / 2, y - f * 0.62, w, f * 1.18);
      ctx.strokeStyle = 'rgba(127,195,255,0.9)'; ctx.lineWidth = 1; ctx.strokeRect(x - w / 2 + 0.5, y - f * 0.62 + 0.5, w - 1, f * 1.18 - 1);
      ctx.fillStyle = '#e8f3ff'; ctx.textAlign = 'center'; ctx.textBaseline = 'middle'; ctx.fillText(text, x, y + 1);
    }

    function draw(now) {
      var dt = Math.min(0.05, (now - (S.last || now)) / 1000); S.last = now; S.t += dt;
      // the pointer: yours once you move in, else a slow demonstration
      if (!S.live) demo();
      S.vel[0] = lerp(S.vel[0], (S.ptr[0] - (S.pp ? S.pp[0] : S.ptr[0])) / Math.max(dt, 1e-3), 0.25);
      S.vel[1] = lerp(S.vel[1], (S.ptr[1] - (S.pp ? S.pp[1] : S.ptr[1])) / Math.max(dt, 1e-3), 0.25);
      S.pp = [S.ptr[0], S.ptr[1]];
      var isNew = S.mode === 'new', plate = isNew ? opts.plate : (opts.plateHands || opts.plate);
      ctx.clearRect(0, 0, W, H);
      ctx.drawImage(plate, 0, 0, W, H);
      var hov = slotAt(S.ptr[0], S.ptr[1]), i;
      var inRoom = S.ptr[1] * gh < lay.ry1 + 2;
      // what lies on the table
      for (i = 0; i < 9; i++) if (S.grid[i]) {
        var lift = 0, al = 1;
        for (var d = 0; d < S.drops.length; d++) if (S.drops[d].slot === i + 1) { var u = (S.t - S.drops[d].t) / 0.11; if (u < 1 && isNew) lift = 0.06 * (1 - outCubic(u)); }
        lying(S.grid[i], i + 1, lift, al);
      }
      if (S.result) { var rs = isNew ? outCubic((S.t - (S.resultAt || 0)) / 0.18) : 1; lying(S.result, 0, 0.03 * (1 - rs), 0.35 + 0.65 * rs); }
      if (hov >= 0) drawSquareOutline(hov, 'rgba(127,195,255,0.95)', Math.max(1.5, W / 700));
      S.covered = 0;
      if (isNew) drawHand(hov, inRoom);
      // the inventory strip is the game's own GUI, drawn over the world and the arm
      ctx.save(); ctx.globalAlpha = 0.9;
      ctx.drawImage(plate, strip[0] * plate.width, strip[1] * plate.height, (strip[2] - strip[0]) * plate.width, (strip[3] - strip[1]) * plate.height,
        strip[0] * W, strip[1] * H, (strip[2] - strip[0]) * W, (strip[3] - strip[1]) * H);
      ctx.restore();
      // the stack on the pointer as a flat icon: always today; in the plan only while the pointer is over the strip
      if (S.carried && (!isNew || !inRoom)) {
        var sz = 16 / gw * W, x0 = S.ptr[0] * W - sz / 2, y0 = S.ptr[1] * H - sz / 2, rows = ART[S.carried.art], px = sz / 16, x, y;
        for (y = 0; y < 16; y++) for (x = 0; x < 16; x++) if (rows[y][x] !== '.') { ctx.fillStyle = PAL[rows[y][x]]; ctx.fillRect(x0 + x * px, y0 + y * px, px + 0.5, px + 0.5); }
        if (S.carried.n > 1) { ctx.font = '700 ' + Math.round(sz * 0.55) + 'px "JetBrains Mono", ui-monospace, monospace'; ctx.textAlign = 'right'; ctx.textBaseline = 'alphabetic'; ctx.fillStyle = '#3b3b3b'; ctx.fillText(S.carried.n, x0 + sz + 2, y0 + sz + 2); ctx.fillStyle = '#fff'; ctx.fillText(S.carried.n, x0 + sz + 1, y0 + sz + 1); }
      }
      if (opts.onState) opts.onState(S, hov);
      requestAnimationFrame(draw);
    }

    function drawHand(hov, inRoom) {
      // where the stack is held: on the pointer's own line, a little over the table (exactly on the pointer, never behind it)
      var carrying = !!S.carried && inRoom;
      S.handIn = lerp(S.handIn, carrying ? 1 : 0, 0.22);
      var hover = 0.07, dipU = (S.t - S.dip) / 0.16, dip = dipU >= 0 && dipU < 1 ? Math.sin(dipU * Math.PI) * 0.045 : 0;
      var liftU = (S.t - S.lift) / 0.14, lifted = liftU < 1 ? 0.03 * (1 - outCubic(liftU)) : 0;
      var py = clamp(S.ptr[1], 0.02, lay.ry1 / gh);
      var P = cam.onPlane(S.ptr[0], py, 1.0 + hover - dip - lifted) || [0.5, 1.07, 0.5];
      var pc = cam.toCam(P);
      // empty, the hand waits at the near edge under the pointer's column, off the grid
      var restW = [clamp(P[0], 0.22, 0.78), 1.05, 0.06], rc = cam.toCam(restW);
      var k = smooth(S.handIn), e0 = [lerp(rc[0], pc[0], k), lerp(rc[1], pc[1], k), lerp(rc[2], pc[2], k)];
      // the arm lies back over the table toward you, as an arm reaching across a table does: toward the near edge, a little
      // to its own side and rising, leaning with the pointer's speed; it ends under the inventory strip
      var here = cam.fromCam(e0), lean = S.hand * 0.1 - clamp(S.vel[0] * 0.12, -0.08, 0.08);
      var rootW = [here[0] - lean, 1.3, -0.45], a = norm(sub(cam.toCam(rootW), e0)), w = CELL * 0.8;
      var e = add(e0, mul(a, 0.085)); // the hand's end sits just short of the stack it pinches
      var upC = [cam.r[1], cam.u[1], cam.f[1]];
      // squares the sleeve crosses: what lies on them shows through as a glass ghost, so nothing is ever hidden
      var hidden = [];
      var a2 = cam.screen(e), b2 = cam.screen(add(e, mul(a, 0.4)));
      var dx = b2[0] - a2[0], dy = (b2[1] - a2[1]) / aspect, L = Math.hypot(dx, dy) || 1;
      var halfW = Math.abs(cam.screen([pc[0] + w / 2, pc[1], pc[2]])[0] - cam.screen([pc[0] - w / 2, pc[1], pc[2]])[0]) / 2;
      for (var s = 0; s < 10; s++) {
        if (s === hov) continue;
        var holds = s === 0 ? S.result : S.grid[s - 1];
        if (!holds) continue;
        var sp = cam.project(spot(s)), qx = sp[0] - a2[0], qy = (sp[1] - a2[1]) / aspect;
        var along = (qx * dx + qy * dy) / L, off = Math.abs(qx * dy - qy * dx) / L;
        if (along > -halfW && off < halfW * 1.6 && k > 0.5) hidden.push(s);
      }
      S.covered = hidden.length;
      drawArm(ctx, W, H, cam, e, a, w, opts.skin || '#c9906a', opts.sleeve || '#2e4a5e', 1.0, upC, [(lay.py0 + 12) / gh, lay.px0 / gw, lay.px1 / gw]);
      for (var h = 0; h < hidden.length; h++) {
        var hs = hidden[h], st = hs === 0 ? S.result : S.grid[hs - 1], hp = spot(hs);
        drawItem(ctx, W, H, cam, st.art, [hp[0], 1.2, hp[2] - 0.11], [-1, 0, 0], [0, 0, 1], 0.15, 1.5, 0.42);
        drawSquareOutline(hs, 'rgba(127,195,255,0.75)', Math.max(1.2, W / 900));
      }
      if (carrying || k > 0.6 && S.carried) {
        // the stack itself: flat as it will lie, tipped a little to the eye and with its own weight as it moves
        var tilt = clamp(S.vel[0] * 0.25, -0.35, 0.35), ax = norm([-1, tilt, 0]), ay = norm([0, 0.34, 1]);
        var n = S.carried.n > 32 ? 4 : S.carried.n > 5 ? 3 : S.carried.n > 1 ? 2 : 1;
        for (var q = 0; q < n; q++) drawItem(ctx, W, H, cam, S.carried.art, [P[0] + q * 0.012, P[1] + q * 0.007, P[2] - q * 0.012], ax, ay, 0.15, 1 - (n - 1 - q) * 0.1, 1);
        if (S.carried.n > 1) tag(cam.project([P[0] - 0.07, P[1] + 0.02, P[2] - 0.07]), String(S.carried.n));
      }
    }

    // ---- a slow demonstration until the pointer comes in: lay two planks, take the sticks ----
    var DEMO = [[2, 0.9, 0], [2, 0.25, 1], [5, 0.7, 0], [5, 0.25, 1], [0, 0.9, 0], [0, 0.3, 2], [9, 1.2, 0], [9, 0.5, 3]];
    function demo() {
      var T = 0, i, tt = S.t % 6.2, from = S.demoFrom || [0.62, 0.42];
      for (i = 0; i < DEMO.length; i++) {
        var st = DEMO[i], target = cam.project(add(spot(st[0]), [0, 0.07, 0]));
        if (tt < T + st[1]) {
          var u = smooth((tt - T) / st[1]), prev = i ? cam.project(add(spot(DEMO[i - 1][0]), [0, 0.07, 0])) : from;
          S.ptr = [lerp(prev[0], target[0], u), lerp(prev[1], target[1], u)];
          if (st[2] && S.demoStep !== i && (tt - T) > st[1] * 0.5) {
            S.demoStep = i;
            if (st[2] === 1) click(target[0], target[1], true);
            else if (st[2] === 2) { S.resultAt = S.t; click(target[0], target[1], false); }
            else if (st[2] === 3) reset();
          }
          return;
        }
        T += st[1];
      }
    }
    function reset() { S.grid = [null, null, null, null, null, null, null, null, null]; S.carried = { art: 'plank', n: 6 }; S.result = null; S.drops = []; S.demoStep = -1; }
    var prevResult = null;
    setInterval(function () { if (S.result && !prevResult) S.resultAt = S.t; prevResult = S.result; }, 30);

    function at(ev) { var r = canvas.getBoundingClientRect(); return [(ev.clientX - r.left) / r.width, (ev.clientY - r.top) / r.height]; }
    canvas.addEventListener('pointermove', function (ev) { if (!S.live) { S.live = true; reset(); } S.ptr = at(ev); });
    canvas.addEventListener('pointerdown', function (ev) { S.live = true; S.ptr = at(ev); click(S.ptr[0], S.ptr[1], ev.button === 2 || ev.shiftKey); ev.preventDefault(); });
    canvas.addEventListener('contextmenu', function (ev) { ev.preventDefault(); });
    canvas.addEventListener('pointerleave', function () { S.live = false; S.demoStep = -1; S.demoFrom = [S.ptr[0], S.ptr[1]]; S.t = 0; reset(); });
    requestAnimationFrame(draw);
    return {
      setMode: function (m) { S.mode = m; }, give: function (art) { S.carried = { art: art, n: art === 'plank' ? 6 : 2 }; }, reset: reset,
      setHand: function (h) { S.hand = h; }, cam: cam, layout: lay, spot: spot
    };
  }
  window.AwakenCarry = { mount: mount, frame: frame, frameBoxes: frameBoxes, layout: layout, Camera: Camera, spot: spot, spotToday: spotToday, drawArm: drawArm, drawItem: drawItem, quad: quad, shade: shade, ART: ART, PAL: PAL, v: { add: add, sub: sub, mul: mul, norm: norm, cross: cross, dot: dot, lerp: lerp, clamp: clamp, smooth: smooth, outCubic: outCubic } };
})();
