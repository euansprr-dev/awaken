// K menu mockup engine: draws a holo panel the way HoloCanvas/HoloRender do, in panel units at whole framebuffer
// pixels per unit, with Minecraft's own bitmap font (read locally, never published). Scenes compose panels over
// real tour frames with the panel's real perspective (a homography fitted to a real shot's corners).

const C = {
  // existing palette (signal.client.Glass)
  GLASS: 0x0F1724, GLASS_TOP: 0x16233A, EDGE: 0x7FC3FF, TITLE: 0xFFFFFF, TEXT: 0xD9E4F2, LABEL: 0x8FA7C4,
  AMBER: 0xFFCF7A, WARN: 0xFF8A7A, GOOD: 0x8FF0C0, VIOLET: 0xB48CFF, LANTERN: 0xF2A541, HORIZON: 0x9FE6D6,
  // proposed tokens
  INK_HI: 0xFFFFFF, INK: 0xD9E4F2, INK_DIM: 0xBCCDE3, INK_FAINT: 0x6F86A6, SIGNAL: 0x7FC3FF, SIGNAL_HOT: 0xC4E6FF,
  NAVY: 0x05080F,
  RANK: { E: 0xF4F6FB, D: 0x5FD08F, C: 0x6FB3FF, B: 0xB48CFF, A: 0xF2A541, S: 0xFF5A6A },
  FAMILY: { KINETIC: 0xE08F1F, VELOCITY: 0xFF4A3D, FLIGHT: 0xA7CDE3, ENERGY: 0x7FC3FF, SHADOW: 0x8F6BFF, MIND: 0xD088C2,
    ARCHITECT: 0xD9BA80, TECH: 0x47C6B0, SPIRIT: 0x86E0A0 },
};

function rgba(c, a = 1) {
  return `rgba(${(c >> 16) & 255},${(c >> 8) & 255},${c & 255},${a})`;
}

// ---- font -----------------------------------------------------------------------------------------------------------

const Font = {
  glyphs: null, atlases: {}, tinted: new Map(),
  // glyphs Minecraft takes from unifont (half-pixel 16-row art), drawn here at full font pixels
  custom: {
    '▸': { adv: 5, rows: ['', '#', '##', '###', '##', '#'] },
    '↗': { adv: 7, rows: ['', '..####', '....##', '...#.#', '..#..#', '.#', '#'] },
    '↘': { adv: 7, rows: ['', '#', '.#', '..#..#', '...#.#', '....##', '..####'] },
    '↙': { adv: 7, rows: ['', '.....#', '....#', '#..#', '#.#', '##', '####'] },
    '↖': { adv: 7, rows: ['', '####', '##', '#.#', '#..#', '....#', '.....#'] },
    '↑': { adv: 6, rows: ['', '..#', '.###', '#.#.#', '..#', '..#', '..#'] },
    '✓': { adv: 8, rows: ['', '......#', '.....##', '#...##', '##.##', '.###', '..#'] },
    '⤢': { adv: 8, rows: ['', '....###', '.....##', '....#.#', '#.#', '##', '###'] },
    '▪': { adv: 4, rows: ['', '', '###', '###', '###'] },
    '│': { adv: 2, rows: ['#', '#', '#', '#', '#', '#', '#', '#'] },
  },
  async load(base) {
    this.glyphs = await (await fetch(base + 'font/glyphs.json')).json();
    for (const n of ['ascii', 'nonlatin_european']) {
      const img = new Image();
      img.src = base + 'font/' + n + '.png';
      await img.decode();
      this.atlases[n] = img;
    }
  },
  atlasTint(name, colour) {
    const key = name + ':' + colour;
    let c = this.tinted.get(key);
    if (c) return c;
    const img = this.atlases[name];
    c = document.createElement('canvas');
    c.width = img.width; c.height = img.height;
    const x = c.getContext('2d');
    x.drawImage(img, 0, 0);
    x.globalCompositeOperation = 'source-in';
    x.fillStyle = rgba(colour, 1);
    x.fillRect(0, 0, c.width, c.height);
    this.tinted.set(key, c);
    return c;
  },
  adv(ch) {
    if (ch === ' ') return 4;
    const cu = this.custom[ch];
    if (cu) return cu.adv;
    const g = this.glyphs[ch];
    return g ? g.adv : 6;
  },
  width(s) {
    let w = 0;
    for (const ch of s) w += this.adv(ch);
    return w; // Minecraft counts the trailing advance too
  },
  // draws at device px (x, y) with gp device px per font pixel
  draw(ctx, s, x, y, gp, colour, alpha) {
    ctx.save();
    ctx.globalAlpha = alpha;
    ctx.imageSmoothingEnabled = false;
    let cx = x;
    for (const ch of s) {
      if (ch === ' ') { cx += 4 * gp; continue; }
      const cu = this.custom[ch];
      if (cu) {
        ctx.fillStyle = rgba(colour, 1);
        cu.rows.forEach((row, r) => {
          for (let i = 0; i < row.length; i++) if (row[i] === '#') ctx.fillRect(Math.round(cx + i * gp), Math.round(y + r * gp), Math.ceil(gp), Math.ceil(gp));
        });
        cx += cu.adv * gp;
        continue;
      }
      const g = this.glyphs[ch];
      if (!g) { cx += 6 * gp; continue; }
      const at = this.atlasTint(g.f, colour);
      // glyph cell top sits at baseline - ascent; ascii's ascent is 7 of 8, so y is the cell top
      ctx.drawImage(at, g.sx, g.sy, g.w, g.h, Math.round(cx), Math.round(y + (7 - g.asc) * gp), Math.round(g.w * gp), Math.round(g.h * gp));
      cx += g.adv * gp;
    }
    ctx.restore();
  },
};

// ---- a panel ----------------------------------------------------------------------------------------------------------

class Panel {
  constructor(w, h, ppu, margin = 30) {
    this.w = w; this.h = h; this.ppu = ppu; this.m = margin;
    this.canvas = document.createElement('canvas');
    this.canvas.width = (w + 2 * margin) * ppu;
    this.canvas.height = (h + 2 * margin) * ppu;
    this.ctx = this.canvas.getContext('2d');
    this.clipStack = [];
  }
  X(u) { return Math.round((u + this.m) * this.ppu); }
  Y(u) { return Math.round((u + this.m) * this.ppu); }
  fill(x, y, w, h, c, a = 1) {
    const g = this.ctx;
    g.fillStyle = rgba(c, a);
    g.fillRect(this.X(x), this.Y(y), this.X(x + w) - this.X(x), this.Y(y + h) - this.Y(y));
  }
  fillPx(x, y, w, h, c, a) { // device-px sized (hairlines)
    this.ctx.fillStyle = rgba(c, a);
    this.ctx.fillRect(x, y, w, h);
  }
  gradV(x, y, w, h, c0, a0, c1, a1) {
    const g = this.ctx, gr = g.createLinearGradient(0, this.Y(y), 0, this.Y(y + h));
    gr.addColorStop(0, rgba(c0, a0)); gr.addColorStop(1, rgba(c1, a1));
    g.fillStyle = gr; g.fillRect(this.X(x), this.Y(y), this.X(x + w) - this.X(x), this.Y(y + h) - this.Y(y));
  }
  gradH(x, y, w, h, c0, a0, c1, a1) {
    const g = this.ctx, gr = g.createLinearGradient(this.X(x), 0, this.X(x + w), 0);
    gr.addColorStop(0, rgba(c0, a0)); gr.addColorStop(1, rgba(c1, a1));
    g.fillStyle = gr; g.fillRect(this.X(x), this.Y(y), this.X(x + w) - this.X(x), this.Y(y + h) - this.Y(y));
  }
  hline(x0, x1, y, c, a, px = Math.max(1, Math.round(this.ppu * 0.3))) {
    this.fillPx(this.X(x0), this.Y(y), this.X(x1) - this.X(x0), px, c, a);
  }
  vline(x, y0, y1, c, a, px = Math.max(1, Math.round(this.ppu * 0.3))) {
    this.fillPx(this.X(x), this.Y(y0), px, this.Y(y1) - this.Y(y0), c, a);
  }
  outline(x, y, w, h, c, a, px) {
    px = px || Math.max(1, Math.round(this.ppu * 0.3));
    const X0 = this.X(x), Y0 = this.Y(y), X1 = this.X(x + w), Y1 = this.Y(y + h);
    this.fillPx(X0, Y0, X1 - X0, px, c, a); this.fillPx(X0, Y1 - px, X1 - X0, px, c, a);
    this.fillPx(X0, Y0 + px, px, Y1 - Y0 - 2 * px, c, a); this.fillPx(X1 - px, Y0 + px, px, Y1 - Y0 - 2 * px, c, a);
  }
  // additive soft light
  glow(fn, blurUnits, alpha = 1) {
    const tmp = document.createElement('canvas');
    tmp.width = this.canvas.width; tmp.height = this.canvas.height;
    const saved = this.ctx; this.ctx = tmp.getContext('2d');
    fn(this);
    this.ctx = saved;
    const g = this.ctx;
    g.save();
    g.globalCompositeOperation = 'lighter';
    g.globalAlpha = alpha;
    g.filter = `blur(${blurUnits * this.ppu}px)`;
    g.drawImage(tmp, 0, 0);
    g.restore();
  }
  // ---- text: light, not print (four faint cyan copies half a unit off, no shadow) ----
  text(s, x, y, o = {}) {
    const scale = o.scale || 1, c = o.c === undefined ? C.INK : o.c, a = o.a === undefined ? 1 : o.a;
    const gp = this.ppu * scale;
    let tx = x;
    if (o.align === 'right') tx = x - Font.width(s) * scale;
    if (o.align === 'center') tx = x - Font.width(s) * scale / 2;
    if (o.track) return this.tracked(s, tx, y, o);
    const X = this.X(tx), Y = this.Y(y), off = Math.max(1, Math.round(this.ppu * 0.5));
    if (o.light !== false) {
      const ha = (o.halo === undefined ? 0.30 : o.halo) * a;
      for (const [dx, dy] of [[-off, -off], [off, -off], [-off, off], [off, off]]) Font.draw(this.ctx, s, X + dx, Y + dy, gp, C.SIGNAL, ha);
    }
    if (o.glow) this.glow(p => Font.draw(p.ctx, s, X, Y, gp, c, 1), o.glowBlur || 1.6, o.glow);
    Font.draw(this.ctx, s, X, Y, gp, c, a);
    if (o.bold) Font.draw(this.ctx, s, X + Math.round(gp), Y, gp, c, a);
    return Font.width(s) * scale;
  }
  // spaced capitals: one extra font pixel between letters
  tracked(s, x, y, o) {
    const scale = o.scale || 1;
    let cx = x;
    const t = o.track;
    for (const ch of s) {
      this.text(ch, cx, y, { ...o, track: 0, align: undefined });
      cx += (Font.adv(ch) + t) * scale;
    }
    return cx - x;
  }
  static tw(s, scale = 1, track = 0) {
    let w = 0;
    for (const ch of s) w += (Font.adv(ch) + track) * scale;
    return w;
  }
  wrap(s, width, scale = 1) {
    const words = s.split(' '), lines = [];
    let cur = '';
    for (const w of words) {
      const t = cur ? cur + ' ' + w : w;
      if (Font.width(t) * scale > width && cur) { lines.push(cur); cur = w; } else cur = t;
    }
    if (cur) lines.push(cur);
    return lines;
  }
  ellipsize(s, width, scale = 1) {
    if (Font.width(s) * scale <= width) return s;
    while (s.length && Font.width(s + '…') * scale > width) s = s.slice(0, -1);
    return s + '…';
  }
  sprite(img, x, y, w, h, alpha = 1, tint) {
    const g = this.ctx;
    g.save(); g.imageSmoothingEnabled = false; g.globalAlpha = alpha;
    if (tint !== undefined) {
      const t = document.createElement('canvas'); t.width = img.width; t.height = img.height;
      const tc = t.getContext('2d'); tc.drawImage(img, 0, 0); tc.globalCompositeOperation = 'source-in';
      tc.fillStyle = rgba(tint, 1); tc.fillRect(0, 0, t.width, t.height); img = t;
    }
    g.drawImage(img, this.X(x), this.Y(y), this.X(x + w) - this.X(x), this.Y(y + h) - this.Y(y));
    g.restore();
  }
  // pixel art from rows of characters mapped through a palette, one cell = `cell` units
  pixels(rows, x, y, cell, pal, alpha = 1) {
    rows.forEach((row, r) => {
      for (let i = 0; i < row.length; i++) {
        const k = row[i];
        if (k === '.' || k === ' ' || pal[k] === undefined) continue;
        this.fill(x + i * cell, y + r * cell, cell, cell, pal[k], alpha);
      }
    });
  }

  // ---- the material (HoloRender's glass, tint, edge, brackets, halo), with the proposal's wrist light ----
  material(o = {}) {
    const { w, h } = this, g = this.ctx;
    const tint = o.tint === undefined ? 0.38 : o.tint;
    // halo
    this.glow(p => p.outline(0, 0, w, h, C.SIGNAL, 0.9, Math.round(p.ppu * 1.2)), 5, o.halo === undefined ? 0.55 : o.halo);
    // glass: 38% at the top to 50% toward the wrist, a top sheen
    this.gradV(0, 0, w, h, C.GLASS_TOP, 0.38, C.GLASS, 0.50);
    this.gradV(0, 0, w, 16, C.SIGNAL, 0.06, C.SIGNAL, 0);
    // the shader's own light (shimmer, inner glow) reads as a faint blue lift over bright ground
    g.save(); g.globalCompositeOperation = 'lighter'; this.fill(0, 0, w, h, 0x1A3A66, 0.22); g.restore();
    // the content tint: one even feathered layer
    g.save();
    g.filter = `blur(${7 * this.ppu}px)`;
    this.fill(10, 10, w - 20, h - 20, C.NAVY, tint);
    g.restore();
    // static scan lines, three units apart
    for (let y = 1; y < h; y += 3) this.fillPx(this.X(0), this.Y(y), this.X(w) - this.X(0), 1, C.SIGNAL, 0.035);
    // soft inner edge glow
    this.gradV(0, 0, w, 6, C.SIGNAL, 0.10, C.SIGNAL, 0);
    this.gradV(0, h - 6, w, 6, C.SIGNAL, 0, C.SIGNAL, 0.10);
    this.gradH(0, 0, 6, h, C.SIGNAL, 0.10, C.SIGNAL, 0);
    this.gradH(w - 6, 0, 6, h, C.SIGNAL, 0, C.SIGNAL, 0.10);
    if (o.wristLight) {
      // light comes from the band: brightest where the beams meet the glass (lower left)
      const gr = g.createRadialGradient(this.X(0), this.Y(h), 0, this.X(0), this.Y(h), 190 * this.ppu);
      gr.addColorStop(0, rgba(C.SIGNAL, 0.13)); gr.addColorStop(1, rgba(C.SIGNAL, 0));
      g.save(); g.globalCompositeOperation = 'lighter'; g.fillStyle = gr;
      g.fillRect(this.X(0), this.Y(0), this.X(w) - this.X(0), this.Y(h) - this.Y(0)); g.restore();
    }
  }
  frame(o = {}) {
    const { w, h } = this, px = Math.max(2, Math.round(this.ppu * 0.32));
    // hairline edge
    this.outline(0, 0, w, h, C.SIGNAL_HOT, 0.85, px);
    this.glow(p => p.outline(0, 0, w, h, C.SIGNAL, 1, px), 0.8, 0.6);
    // corner brackets just outside the corners
    const L = o.bracket || 18, d = 4, bt = Math.round(this.ppu * 0.6);
    const br = (p) => {
      p.fillPx(p.X(-d), p.Y(-d), p.X(-d + L) - p.X(-d), bt, C.SIGNAL_HOT, 0.75);
      p.fillPx(p.X(-d), p.Y(-d), bt, p.Y(-d + L) - p.Y(-d), C.SIGNAL_HOT, 0.75);
      p.fillPx(p.X(w + d - L), p.Y(-d), p.X(w + d) - p.X(w + d - L), bt, C.SIGNAL_HOT, 0.75);
      p.fillPx(p.X(w + d) - bt, p.Y(-d), bt, p.Y(-d + L) - p.Y(-d), C.SIGNAL_HOT, 0.75);
      p.fillPx(p.X(-d), p.Y(h + d) - bt, p.X(-d + L) - p.X(-d), bt, C.SIGNAL_HOT, 0.75);
      p.fillPx(p.X(-d), p.Y(h + d - L), bt, p.Y(h + d) - p.Y(h + d - L), C.SIGNAL_HOT, 0.75);
      p.fillPx(p.X(w + d - L), p.Y(h + d) - bt, p.X(w + d) - p.X(w + d - L), bt, C.SIGNAL_HOT, 0.75);
      p.fillPx(p.X(w + d) - bt, p.Y(h + d - L), bt, p.Y(h + d) - p.Y(h + d - L), C.SIGNAL_HOT, 0.75);
    };
    br(this);
    this.glow(br, 1.2, 0.7);
  }
  clip(x, y, w, h) {
    this.ctx.save();
    this.ctx.beginPath();
    this.ctx.rect(this.X(x), this.Y(y), this.X(x + w) - this.X(x), this.Y(y + h) - this.Y(y));
    this.ctx.clip();
  }
  unclip() { this.ctx.restore(); }
}

// ---- perspective: a homography from panel units to screen px -----------------------------------------------------

function solve(A, b) { // Gaussian elimination
  const n = b.length;
  for (let i = 0; i < n; i++) {
    let p = i;
    for (let r = i + 1; r < n; r++) if (Math.abs(A[r][i]) > Math.abs(A[p][i])) p = r;
    [A[i], A[p]] = [A[p], A[i]]; [b[i], b[p]] = [b[p], b[i]];
    for (let r = i + 1; r < n; r++) {
      const f = A[r][i] / A[i][i];
      for (let c = i; c < n; c++) A[r][c] -= f * A[i][c];
      b[r] -= f * b[i];
    }
  }
  const x = new Array(n).fill(0);
  for (let i = n - 1; i >= 0; i--) {
    let s = b[i];
    for (let c = i + 1; c < n; c++) s -= A[i][c] * x[c];
    x[i] = s / A[i][i];
  }
  return x;
}

function homography(src, dst) {
  const A = [], b = [];
  for (let i = 0; i < 4; i++) {
    const [x, y] = src[i], [u, v] = dst[i];
    A.push([x, y, 1, 0, 0, 0, -u * x, -u * y]); b.push(u);
    A.push([0, 0, 0, x, y, 1, -v * x, -v * y]); b.push(v);
  }
  const h = solve(A, b);
  return [[h[0], h[1], h[2]], [h[3], h[4], h[5]], [h[6], h[7], 1]];
}

function applyH(H, x, y) {
  const w = H[2][0] * x + H[2][1] * y + H[2][2];
  return [(H[0][0] * x + H[0][1] * y + H[0][2]) / w, (H[1][0] * x + H[1][1] * y + H[1][2]) / w];
}

// The real K panel in holo_17 (3024×1898, 5 px a unit, 272×228 units): its corners on screen.
const REAL = { src: [[0, 0], [272, 0], [0, 228], [272, 228]], dst: [[90, 170], [1495, 256], [31, 1371], [1502, 1323]] };
const PLANE = homography(REAL.src, REAL.dst);

// Places a panel's canvas on the stage so that unit (x, y) lands where the real plane maps it (stage px = screen/2).
function place(stage, panel, ox = 0, oy = 0, H = PLANE, extra = '') {
  const m = panel.m, p = panel.ppu;
  // canvas px -> units: u = cx/p - m ; then units -> screen via H ; screen -> css px /2
  const src = [[0, 0], [panel.canvas.width, 0], [0, panel.canvas.height], [panel.canvas.width, panel.canvas.height]];
  const dst = src.map(([cx, cy]) => {
    const [sx, sy] = applyH(H, cx / p - m + ox, cy / p - m + oy);
    return [sx / 2, sy / 2];
  });
  const h = homography(src, dst);
  const el = panel.canvas;
  el.style.position = 'absolute'; el.style.left = '0'; el.style.top = '0';
  el.style.width = panel.canvas.width + 'px'; el.style.height = panel.canvas.height + 'px';
  el.style.transformOrigin = '0 0';
  el.style.transform = `matrix3d(${h[0][0]},${h[1][0]},0,${h[2][0]},${h[0][1]},${h[1][1]},0,${h[2][1]},0,0,1,0,${h[0][2]},${h[1][2]},0,${h[2][2]})` + extra;
  stage.appendChild(el);
  return dst;
}

// the band's diamond (the beams' origin) in the holo frames
const EMITTER = [532, 1617];

function beams(stage, W, Hh, corners, alpha = 0.35) {
  const c = document.createElement('canvas');
  c.width = W; c.height = Hh;
  c.style.cssText = `position:absolute;left:0;top:0;width:${W / 2}px;height:${Hh / 2}px;mix-blend-mode:screen`;
  const g = c.getContext('2d');
  g.filter = 'blur(3px)';
  for (const [x, y] of corners) {
    const gr = g.createLinearGradient(EMITTER[0], EMITTER[1], x * 2, y * 2);
    gr.addColorStop(0, rgba(C.SIGNAL_HOT, alpha)); gr.addColorStop(1, rgba(C.SIGNAL, alpha * 0.25));
    g.strokeStyle = gr; g.lineWidth = 5;
    g.beginPath(); g.moveTo(EMITTER[0], EMITTER[1]); g.lineTo(x * 2, y * 2); g.stroke();
  }
  stage.appendChild(c);
}

async function loadImg(src) {
  const i = new Image();
  i.src = src;
  await i.decode();
  return i;
}
