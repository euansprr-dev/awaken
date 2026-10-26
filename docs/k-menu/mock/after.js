// The proposal: "Pages of the band". One panel, a rail of six pages down its wrist edge, each page composed for its
// job. Type sizes are whole framebuffer pixels per font pixel: body = ppu, small = ppu-1, micro = ppu-2 (at 5 px a
// unit: 5, 4, 3), display = 2 × ppu.

const T = {
  small: (p) => (p.ppu - 1) / p.ppu,
  micro: (p) => Math.max(2, p.ppu - 2) / p.ppu,
  display: 2,
};
const A = { W: 288, H: 228, RAIL: 30, X0: 40, PAD: 10 };
A.X1 = A.W - A.PAD;
A.CW = A.X1 - A.X0;

const ICON = {
  status: ['.....##.....', '....####....', '....####....', '....####....', '.....##.....', '............', '...######...', '..########..', '.##########.', '.##########.', '.##########.', '............'],
  powers: ['.......###..', '......###...', '.....###....', '....###.....', '...#######..', '.......##...', '......##....', '.....##.....', '....##......', '...##.......', '..##........', '............'],
  journal: ['............', '.####..####.', '#....##....#', '#.##.##.##.#', '#....##....#', '#.##.##.##.#', '#....##....#', '#.##.##.##.#', '#....##....#', '.####..####.', '.....##.....', '............'],
  truths: ['.....##.....', '.....##.....', '....####....', '....####....', '..########..', '############', '############', '..########..', '....####....', '....####....', '.....##.....', '.....##.....'],
  codex: ['#####..#####', '#...#..#...#', '#.#.#..#.#.#', '#...#..#...#', '#####..#####', '............', '#####..#####', '#...#..#...#', '#.#.#..#...#', '#...#..#...#', '#####..#####', '............'],
  map: ['....####....', '...######...', '..###..###..', '..##....##..', '..###..###..', '...######...', '....####....', '.....##.....', '.....##.....', '............', '.##########.', '............'],
};
const PAGES = [['status', 'STATUS'], ['powers', 'POWERS'], ['journal', 'JOURNAL'], ['truths', 'TRUTHS'], ['codex', 'CODEX'], ['map', 'MAP']];

// a hunter's face (our own 8×8 art, not a Mojang skin): hair, skin, shade, eyes, mouth
const FACE = { rows: ['hhhhhhhh', 'hhhhhhhh', 'hhshhhsh', 'hsssssss', 'swesswes', 'ssssssss', 'sssmmsss', 'dssssssd'],
  pal: { h: 0x1E2230, s: 0xE0B08A, d: 0xC49270, w: 0xF2F2F2, e: 0x2FB59A, m: 0x9C5B48 } };

function iconPix(p, rows, x, y, colour, alpha = 1, cell = 1) {
  const pal = { '#': colour };
  p.pixels(rows, x, y, cell, pal, alpha);
}

function micro(p, s, x, y, o = {}) {
  return p.text(s, x, y, { scale: T.micro(p), track: 1, c: C.INK_DIM, halo: 0.18, ...o });
}
function small(p, s, x, y, o = {}) {
  return p.text(s, x, y, { scale: T.small(p), c: C.INK, ...o });
}
function microW(p, s) { return Panel.tw(s, T.micro(p), 1); }
function smallW(p, s) { return Font.width(s) * T.small(p); }

// ---- components ----------------------------------------------------------------------------------------------------

function rail(p, active, badges = {}, o = {}) {
  const R = A.RAIL;
  p.gradH(0, 0, R, p.h, C.NAVY, 0.34, C.NAVY, 0.16);
  p.vline(R, 6, p.h - 6, C.SIGNAL, 0.30);
  PAGES.forEach(([key, label], i) => {
    const y0 = 9 + i * 34, on = i === active;
    if (on) {
      p.fill(2, y0, R - 2, 31, C.SIGNAL, 0.15);
      p.fill(0, y0 + 1, 2, 29, C.SIGNAL_HOT, 0.95);
      p.glow(q => q.fill(0, y0 + 1, 2, 29, C.SIGNAL, 1), 2.2, 0.9);
      // the active page's light leaks into the page it opens
      p.gradH(R, y0 - 6, 70, 43, C.SIGNAL, 0.07, C.SIGNAL, 0);
    }
    const ic = on ? C.SIGNAL_HOT : C.INK_DIM;
    iconPix(p, ICON[key], 9, y0 + 4, ic, on ? 1 : 0.8);
    if (on) p.glow(q => iconPix(q, ICON[key], 9, y0 + 4, C.SIGNAL, 1), 1.3, 0.6);
    const lw = microW(p, label);
    micro(p, label, 15 - lw / 2, y0 + 20, { c: on ? C.INK_HI : C.INK_DIM, a: on ? 1 : 0.9 });
    const b = badges[key];
    if (b) {
      const bw = Math.max(7, microW(p, String(b.n)) + 3);
      p.fill(19, y0 + 1, bw, 8, b.c, 0.95);
      micro(p, String(b.n), 19 + 2, y0 + 2.5, { c: C.NAVY, light: false, track: 1 });
    }
  });
  // the close key at the foot of the rail
  if (o.closeHint !== false) {
    keycap(p, 'K', 9, p.h - 15, 12);
  }
}

function keycap(p, label, x, y, w) {
  const lw = microW(p, label);
  w = w || Math.max(9, lw + 5);
  p.fill(x, y, w, 9, C.NAVY, 0.6);
  p.outline(x, y, w, 9, C.INK_DIM, 0.55);
  p.fill(x, y + 8, w, 1, C.INK_DIM, 0.55);
  micro(p, label, x + (w - lw) / 2 + 0.5, y + 2, { c: C.INK, track: 1 });
  return w;
}

function header(p, label, y, o = {}) {
  let x = A.X0;
  x += micro(p, label, x, y, { c: o.c || C.INK_DIM });
  if (o.extra) x += micro(p, o.extra, x, y, { c: o.extraC || C.SIGNAL_HOT });
  let right = A.X1;
  if (o.right) {
    const rw = microW(p, o.right);
    if (o.rightChip) {
      p.fill(right - rw - 6, y - 2, rw + 6, 9, o.rightChip, 0.13);
      p.outline(right - rw - 6, y - 2, rw + 6, 9, o.rightChip, 0.65);
      micro(p, o.right, right - rw - 3, y, { c: o.rightChip });
      right -= rw + 10;
    } else {
      micro(p, o.right, right - rw, y, { c: o.rightC || C.SIGNAL });
      right -= rw + 5;
    }
  }
  p.hline(x + 4, right, y + 2.5, C.SIGNAL, 0.22);
}

function segbar(p, x, y, w, h, f, colour, o = {}) {
  const seg = o.seg || 3, gap = 1, n = Math.floor((w + gap) / (seg + gap));
  const filled = f * n;
  for (let i = 0; i < n; i++) {
    const sx = x + i * (seg + gap);
    if (i < Math.floor(filled)) p.fill(sx, y, seg, h, colour, 0.95);
    else if (i < filled) { p.fill(sx, y, seg, h, colour, 0.5); p.fill(sx, y, seg, h, C.SIGNAL_HOT, 0.25); }
    else p.fill(sx, y, seg, h, C.SIGNAL, 0.13);
  }
  p.glow(q => { for (let i = 0; i < Math.floor(filled); i++) q.fill(x + i * (seg + gap), y, seg, h, colour, 1); }, 1.4, 0.45);
}

function diamond(p, cx, cy, r, colour, o = {}) {
  const cells = [];
  for (let i = 0; i <= r; i++) { cells.push([cx - r + i, cy - i], [cx + r - i, cy - i], [cx - r + i, cy + i], [cx + r - i, cy + i]); }
  if (o.fill) for (let i = 0; i < r; i++) p.fill(cx - r + i + 1, cy - i, 2 * (r - i) - 1, 2 * i + 1, colour, o.fill);
  const draw = (q, a) => cells.forEach(([x, y]) => q.fill(x, y, 1, 1, colour, a));
  draw(p, 0.95);
  p.glow(q => draw(q, 1), 1.6, 0.7);
}

function button(p, label, x, y, o = {}) {
  const lw = Font.width(label) * (o.scale || 1);
  const kw = o.key ? microW(p, o.key) + 8 : 0;
  const w = lw + 12 + kw, h = 13, c = o.c || C.SIGNAL;
  p.fill(x, y, w, h, c, o.hot ? 0.24 : 0.10);
  p.outline(x, y, w, h, c, o.hot ? 0.95 : 0.5);
  p.text(label, x + 6, y + 3, { c: o.hot ? C.INK_HI : C.INK, scale: o.scale || 1 });
  if (o.key) keycap(p, o.key, x + 6 + lw + 4, y + 2);
  return w;
}

function focusTicks(p, x, y, w, h, c = C.SIGNAL_HOT) {
  const L = 4;
  for (const [cx, cy, sx, sy] of [[x - 1, y - 1, 1, 1], [x + w + 1, y - 1, -1, 1], [x - 1, y + h + 1, 1, -1], [x + w + 1, y + h + 1, -1, -1]]) {
    p.fill(sx > 0 ? cx : cx - L, sy > 0 ? cy : cy - 1, L, 1, c, 1);
    p.fill(sx > 0 ? cx : cx - 1, sy > 0 ? cy : cy - L, 1, L, c, 1);
  }
}

// ---- the Status page ------------------------------------------------------------------------------------------------

const STAT = ['STR', 'AGI', 'VIT', 'INT', 'PER', 'SPI'];
const STAT_NAME = ['STRENGTH', 'AGILITY', 'VITALITY', 'INTELLIGENCE', 'PERCEPTION', 'SPIRIT'];
const STAT_SAYS = ['Hits harder, and powers Kinetic skills.', 'Moves faster, and builds Flow sooner.',
  'More health, and shrugs off more harm.', 'More energy for Energy and Tech powers.',
  'More critical hits, and you notice secrets.', 'Stronger in the Veil, in dreams and with Echoes.'];

function plusNode(p, cx, cy, colour, lit) {
  const r = 4;
  if (lit) for (let i = 0; i < r; i++) p.fill(cx - r + i + 1, cy - i, 2 * (r - i) - 1, 2 * i + 1, colour, 0.22);
  const cells = [];
  for (let i = 0; i <= r; i++) cells.push([cx - r + i, cy - i], [cx + r - i, cy - i], [cx - r + i, cy + i], [cx + r - i, cy + i]);
  cells.forEach(([x, y]) => p.fill(x, y, 1, 1, colour, lit ? 1 : 0.6));
  p.fill(cx - 1, cy, 3, 1, lit ? C.INK_HI : colour, lit ? 1 : 0.8);
  p.fill(cx, cy - 1, 1, 3, lit ? C.INK_HI : colour, lit ? 1 : 0.8);
  if (lit) p.glow(q => cells.forEach(([x, y]) => q.fill(x, y, 1, 1, colour, 1)), 1.4, 0.8);
}

function statusPage(p, d) {
  const X0 = A.X0, X1 = A.X1;
  const rc = C.RANK[d.rank];
  // ---- hero: who you are ----
  const fx = X0 + 1, fy = 11;
  p.fill(fx - 1, fy - 1, 34, 34, C.NAVY, 0.5);
  p.pixels(FACE.rows, fx, fy, 4, FACE.pal, 0.97);
  focusTicks(p, fx - 1, fy - 1, 34, 34, rc);
  const nx = X0 + 44;
  let tx = nx + p.text(d.name, nx, 11, { c: C.INK_HI });
  if (d.title) small(p, '“' + d.title + '”', tx + 5, 11.5, { c: C.AMBER });
  micro(p, d.path ? 'LEVEL  ·  PATH OF THE ' + d.path.toUpperCase() : 'LEVEL', nx, 24, d.path ? { c: C.INK_DIM } : {});
  const lvW = p.text(String(d.level), nx, 30, { scale: 2, c: C.INK_HI, glow: 0.35, glowBlur: 2.5 });
  const lx = nx + lvW + 7;
  small(p, d.xp.toLocaleString('en') + ' / ' + d.need.toLocaleString('en') + ' XP', lx, 31, { c: C.INK });
  if (d.road) {
    const pre = 'Level ' + (d.level + 1) + ' opens ';
    const w0 = small(p, pre, lx, 39.5, { c: C.INK_DIM });
    small(p, p.ellipsize(d.road, X1 - 40 - lx - w0, T.small(p)), lx + w0, 39.5, { c: C.HORIZON });
  }
  segbar(p, nx, 49, 138, 3, d.xp / d.need, C.SIGNAL);

  // rank sigil, with a faint field of its own light behind it
  const sx = X1 - 18, sy = 20;
  p.clip(A.RAIL + 1, 1, p.w - A.RAIL - 2, 56);
  for (const r of [26, 36, 47, 59]) {
    const cells = [];
    for (let i = 0; i <= r; i++) cells.push([sx - r + i, sy - i], [sx + r - i, sy - i], [sx - r + i, sy + i], [sx + r - i, sy + i]);
    cells.forEach(([x, y]) => p.fill(x, y, 1, 1, rc, 0.07 - r * 0.0007));
  }
  p.unclip();
  diamond(p, sx, sy, 14, rc, { fill: 0.08 });
  if (d.reassess) diamond(p, sx, sy, 17, C.AMBER, {});
  p.text(d.rank, sx - 5, sy - 7, { scale: 2, c: C.INK_HI, glow: 0.9, glowBlur: 3, halo: 0.10 });
  p.glow(q => q.text(d.rank, sx - 5, sy - 7, { scale: 2, c: rc, light: false }), 3.5, 0.9);
  const rk = d.rank + '-RANK', rw = microW(p, rk);
  micro(p, rk, sx - rw / 2, sy + 18, { c: rc });
  const nt = d.reassess ? 'TRIAL READY' : d.next ? d.next + ' AT LEVEL ' + d.nextAt : 'THE HIGHEST', ntw = microW(p, nt);
  micro(p, nt, sx - ntw / 2, sy + 25, { c: d.reassess ? C.AMBER : C.INK_DIM });

  // ---- stats: one line of six ----
  const hy = 60;
  header(p, 'STATS', hy, d.points ? { right: '◆ ' + d.points + ' POINT' + (d.points > 1 ? 'S' : '') + ' TO SPEND', rightChip: C.AMBER } : { right: 'NO POINTS TO SPEND', rightC: C.INK_FAINT });
  if (d.fractured) {
    // a fracture weakens every stat, so it is told where the stats are
    const t = '! FRACTURED · ' + d.fractured + ' · 15% WEAKER', tw3 = microW(p, t);
    const fx2 = X1 - (d.points ? microW(p, '◆ ' + d.points + ' POINTS TO SPEND') + 10 : 0) - tw3 - 10;
    p.fill(fx2, hy - 2, tw3 + 6, 9, C.WARN, 0.16); p.outline(fx2, hy - 2, tw3 + 6, 9, C.WARN, 0.75);
    micro(p, t, fx2 + 3, hy, { c: C.WARN });
  }
  const colW = A.CW / 6, top = hy + 9;
  STAT.forEach((s, i) => {
    const x = X0 + i * colW, cx = x + colW / 2;
    const hot = d.focus === i, pend = d.pending && d.pending[i];
    if (hot) {
      p.gradV(x + 1, top, colW - 2, 40, C.SIGNAL, 0.20, C.SIGNAL, 0.02);
      p.fill(x + 1, top, colW - 2, 1, C.SIGNAL_HOT, 0.9);
    }
    if (i > 0) p.vline(x, top + 6, top + 26, C.SIGNAL, 0.14);
    const lw = microW(p, s);
    micro(p, s, cx - lw / 2, top + 4, { c: hot ? C.INK_HI : C.INK_DIM });
    const v = String(d.stats[i] + (pend || 0)), vw = Font.width(v) * 2 - 2;
    const flash = d.flash && d.flash.includes(i);
    if (flash) p.gradV(x + 1, top, colW - 2, 40, C.AMBER, 0.22, C.AMBER, 0.0);
    p.text(v, cx - vw / 2, top + 12, { scale: 2, c: pend ? C.AMBER : C.INK_HI, glow: hot || pend ? 0.45 : flash ? 1.0 : 0.18, glowBlur: flash ? 3.5 : 2 });
    if (pend) small(p, '+' + pend, cx + vw / 2 + 1, top + 11, { c: C.AMBER });
    if (d.points) plusNode(p, Math.round(cx), top + 33, hot || pend ? C.AMBER : C.SIGNAL, hot || pend);
  });
  // the focus line: what the stat under the cursor does
  const fyl = top + 43;
  if (d.focus !== undefined && d.focus !== null) {
    const w0 = micro(p, STAT_NAME[d.focus], X0, fyl + 1, { c: C.SIGNAL_HOT });
    small(p, STAT_SAYS[d.focus], X0 + w0 + 5, fyl, { c: C.INK });
  } else if (d.pending) {
    const n = d.pending.reduce((a, b) => a + (b || 0), 0);
    small(p, n + ' of ' + d.points + ' points placed.', X0, fyl, { c: C.AMBER });
    let bx = X1 - 4;
    const w2 = Font.width('Undo') + 12 + microW(p, 'BKSP') + 8;
    const w1 = Font.width('Confirm') + 12 + microW(p, 'ENTER') + 8;
    button(p, 'Confirm', bx - w2 - 4 - w1, fyl - 3, { c: C.AMBER, hot: true, key: 'ENTER' });
    button(p, 'Undo', bx - w2, fyl - 3, { key: 'BKSP' });
  } else if (d.points) {
    small(p, 'Press + on a stat to raise it, then confirm.', X0, fyl, { c: C.INK_DIM });
  } else if (d.today) {
    small(p, d.today, X0, fyl, { c: C.INK_DIM });
  }

  // ---- the journal, right under the stats ----
  const jy = fyl + 13;
  header(p, 'JOURNAL', jy, { extra: d.unread ? '  · ' + d.unread + ' NEW' : '', right: d.journalAll ? 'ALL ' + d.journalAll + ' ›' : undefined });
  let y = jy + 9;
  if (d.context) y = contextRow(p, d.context, y) + 3;
  const rows = d.journal || [];
  if (!rows.length) {
    for (const line of p.wrap(d.journalEmpty || 'Nothing yet. What you find out there, and what the System tells you, will be kept here.', A.CW, T.small(p))) {
      small(p, line, X0, y + 2, { c: C.INK_DIM }); y += 9;
    }
  }
  for (let i = 0; i < rows.length; i++) {
    const e = rows[i];
    if (y + (e.l ? 18 : 9) > p.h - 3) break;
    const un = i < (d.unread || 0);
    p.text(un ? '◆' : '◇', X0, y, { c: un ? C.SIGNAL_HOT : C.INK_FAINT, glow: un ? 0.5 : 0 });
    const ago = e.ago.toUpperCase(), aw = microW(p, ago);
    p.text(p.ellipsize(e.t, A.CW - 12 - aw - 6), X0 + 9, y, { c: un ? C.INK_HI : C.INK });
    micro(p, ago, X1 - aw, y + 1.5, { c: C.INK_DIM });
    if (e.l) small(p, p.ellipsize(e.l, A.CW - 9, T.small(p)), X0 + 9, y + 9.5, { c: C.INK_DIM });
    y += e.l ? 20 : 11;
  }
}

// one row that is about where you are right now: a decision, a gate run, a room, else the task you track
function contextRow(p, c, y) {
  const X0 = A.X0, X1 = A.X1, h = 21;
  const col = c.colour || C.SIGNAL_HOT;
  p.gradH(X0, y, A.CW, h, col, 0.10, col, 0.02);
  p.fill(X0, y, 2, h, col, 0.95);
  p.glow(q => q.fill(X0, y, 2, h, col, 1), 1.5, 0.7);
  let x = X0 + 6;
  if (c.label) x += micro(p, c.label, x, y + 4, { c: col }) + 5;
  p.text(p.ellipsize(c.t, X1 - x - 4), x, y + 3, { c: C.INK_HI });
  if (c.right) small(p, c.right, X1 - 4, y + 12.5, { c: C.SIGNAL_HOT, align: 'right' });
  if (c.sub) small(p, c.sub, X0 + 6, y + 12.5, { c: C.INK_DIM });
  if (c.buttons) {
    let bx = X1 - 4;
    for (const b of [...c.buttons].reverse()) {
      const w = Font.width(b.t) * T.small(p) + 6 + microW(p, b.key) + 7;
      bx -= w;
      p.fill(bx, y + 11, w, 10, b.c || C.SIGNAL, 0.14); p.outline(bx, y + 11, w, 10, b.c || C.SIGNAL, 0.7);
      small(p, b.t, bx + 3, y + 12.5, { c: C.INK_HI });
      keycap(p, b.key, bx + 3 + Font.width(b.t) * T.small(p) + 3, y + 11.5);
      bx -= 4;
    }
  }
  return y + h;
}

// ---- the Powers page ------------------------------------------------------------------------------------------------

function powersPage(p, d, icons) {
  const X0 = A.X0, X1 = A.X1;
  header(p, 'ON YOUR KEYS', 10, { right: 'ENERGY ' + d.energy + ' · ' + d.regen + ' A SECOND', rightC: C.SIGNAL });
  // six sockets: four actives, the ultimate, the movement
  const keys = ['Z', 'X', 'C', 'V', 'R', 'ALT'];
  let x = X0;
  keys.forEach((k, i) => {
    if (i === 4) x += 8;
    const s = 32, y = 20;
    const pw = d.slots[i] && d.owned.find(o => o.id === d.slots[i]);
    const col = pw ? C.FAMILY[pw.fam] : C.SIGNAL;
    const hot = d.selected && d.fits && d.fits.includes(i);
    p.fill(x, y, s, s, C.NAVY, 0.55);
    p.outline(x, y, s, s, hot ? C.AMBER : col, hot ? 1 : pw ? 0.75 : 0.25, Math.round(p.ppu * 0.4));
    if (i === 4) { p.fill(x + 1, y + 1, s - 2, 2, C.LANTERN, 0.8); }
    if (pw) { p.sprite(icons[pw.id], x + 4, y + 4, 24, 24); }
    else micro(p, i === 4 ? 'ULTIMATE' : 'EMPTY', x + s / 2 - microW(p, i === 4 ? 'ULTIMATE' : 'EMPTY') / 2, y + 14, { c: C.INK_FAINT });
    if (hot) focusTicks(p, x, y, s, s, C.AMBER);
    keycap(p, k, x + s / 2 - Math.max(9, microW(p, k) + 5) / 2, y + s + 3);
    x += s + 6;
  });
  // what you own
  const oy = 70;
  header(p, 'YOUR POWERS', oy, { extra: '  · ' + d.owned.length });
  const cell = 24, gap = 2, cols = Math.floor((A.CW + gap) / (cell + gap));
  d.owned.forEach((o, i) => {
    const cx = X0 + (i % cols) * (cell + gap), cy = oy + 9 + Math.floor(i / cols) * (cell + gap);
    const sel = o.id === d.selected;
    p.fill(cx, cy, cell, cell, sel ? C.AMBER : C.NAVY, sel ? 0.16 : 0.40);
    p.outline(cx, cy, cell, cell, sel ? C.AMBER : C.FAMILY[o.fam], sel ? 1 : 0.55);
    p.sprite(icons[o.id], cx + 2, cy + 2, 20, 20, o.cooldown ? 0.45 : 1);
    for (let k = 0; k < o.lvl; k++) p.fill(cx + 2 + k * 3, cy + cell - 3, 2, 2, C.AMBER, 1);
    if (d.slots.includes(o.id)) p.fill(cx + cell - 4, cy + 1, 3, 3, C.SIGNAL_HOT, 1);
    if (o.passive) p.fill(cx + cell - 4, cy + 1, 3, 3, C.GOOD, 1);
    if (sel) focusTicks(p, cx, cy, cell, cell, C.AMBER);
  });
  // the chosen power, in full
  const rowsUsed = Math.ceil(d.owned.length / cols);
  const ky = oy + 9 + rowsUsed * (cell + gap) + 6;
  const o = d.owned.find(q => q.id === d.selected);
  if (o) {
    p.fill(X0, ky, A.CW, p.h - 8 - ky, C.NAVY, 0.30);
    p.outline(X0, ky, A.CW, p.h - 8 - ky, C.FAMILY[o.fam], 0.35);
    p.fill(X0, ky, 2, p.h - 8 - ky, C.FAMILY[o.fam], 0.95);
    p.sprite(icons[o.id], X0 + 6, ky + 5, 28, 28);
    p.text(o.name, X0 + 40, ky + 5, { c: C.FAMILY[o.fam], glow: 0.25 });
    micro(p, o.meta, X0 + 40, ky + 15, { c: C.INK_DIM });
    const mw = 80;
    segbar(p, X0 + 40, ky + 24, mw, 3, o.mastery, C.AMBER);
    small(p, 'Mastery ' + Math.round(o.mastery * 100) + '% to level ' + (o.lvl + 1), X0 + 40 + mw + 5, ky + 22.5, { c: C.INK_DIM });
    let ly = ky + 37;
    for (const line of p.wrap(o.desc, A.CW - 12, T.small(p))) { small(p, line, X0 + 6, ly, { c: C.INK }); ly += 9; }
    small(p, 'Press Z, X, C or V to put it on that key.', X0 + 6, ly + 3, { c: C.AMBER });
  }
}

// ---- the Journal page -----------------------------------------------------------------------------------------------

function journalPage(p, d) {
  const X0 = A.X0, X1 = A.X1;
  // filters
  let fx = X0;
  ['EVERYTHING', 'TASKS', 'FINDS', 'MESSAGES'].forEach((f, i) => {
    const w = microW(p, f) + 8, on = i === (d.filter || 0);
    p.fill(fx, 9, w, 10, C.SIGNAL, on ? 0.20 : 0.06);
    p.outline(fx, 9, w, 10, C.SIGNAL, on ? 0.9 : 0.3);
    micro(p, f, fx + 4, 11.5, { c: on ? C.INK_HI : C.INK_DIM });
    fx += w + 4;
  });
  micro(p, 'Q / E', X1 - microW(p, 'Q / E'), 11.5, { c: C.INK_FAINT });
  // the task you track
  let y = 26;
  header(p, 'TRACKING', y, { right: 'CHANGE ›' });
  y += 9;
  const t = d.task;
  p.fill(X0, y, A.CW, 14 + t.steps.length * 9, C.SIGNAL, 0.07);
  p.fill(X0, y, 2, 14 + t.steps.length * 9, C.SIGNAL_HOT, 0.95);
  p.text(t.title, X0 + 6, y + 3, { c: C.INK_HI });
  micro(p, t.done + ' / ' + t.steps.length, X1 - 4 - microW(p, t.done + ' / ' + t.steps.length), y + 4.5, { c: C.INK_DIM });
  let sy = y + 13;
  t.steps.forEach((s, i) => {
    const done = i < t.done, cur = i === t.done;
    small(p, done ? '✓' : cur ? '▸' : '·', X0 + 7, sy, { c: done ? C.GOOD : cur ? C.SIGNAL_HOT : C.INK_FAINT });
    small(p, s.t, X0 + 15, sy, { c: done ? C.INK_DIM : cur ? C.INK_HI : C.INK_DIM });
    if (cur && s.where) small(p, s.where, X1 - 4, sy, { c: C.SIGNAL_HOT, align: 'right' });
    sy += 9;
  });
  y = sy + 6;
  // other things you've taken on
  header(p, 'ALSO ON', y);
  y += 9;
  for (const o of d.also) {
    small(p, o.t, X0 + 2, y, { c: C.INK });
    if (o.f !== undefined) { segbar(p, X1 - 44, y + 2, 44, 3, o.f, C.GOOD); }
    else if (o.r) small(p, o.r, X1, y, { c: o.rc || C.INK_DIM, align: 'right' });
    y += 9.5;
  }
  y += 5;
  header(p, 'SAVED FOR LATER', y, { extra: '  · ' + d.saved.length });
  y += 9;
  for (const s of d.saved) {
    const cw = microW(p, s.need) + 6;
    p.fill(X0 + 2, y - 1, cw, 8, s.c, 0.14); p.outline(X0 + 2, y - 1, cw, 8, s.c, 0.7);
    micro(p, s.need, X0 + 5, y + 0.5, { c: s.c });
    small(p, s.what, X0 + cw + 6, y, { c: C.INK });
    small(p, s.where, X1, y, { c: C.INK_DIM, align: 'right' });
    y += 10;
  }
  y += 5;
  header(p, 'LOG', y, { extra: '  · ' + d.unread + ' NEW', right: 'TODAY' });
  y += 9;
  d.log.forEach((e, i) => {
    if (y + 9 > p.h - 4) return;
    const un = i < d.unread;
    small(p, un ? '◆' : '◇', X0 + 2, y, { c: un ? C.SIGNAL_HOT : C.INK_FAINT });
    small(p, e.t, X0 + 10, y, { c: un ? C.INK_HI : C.INK });
    micro(p, e.ago.toUpperCase(), X1 - microW(p, e.ago.toUpperCase()), y + 1, { c: C.INK_DIM });
    y += 10;
  });
  // the page scrolls: a thin track on the right
  p.fill(X1 + 4, 26, 1, p.h - 34, C.SIGNAL, 0.12);
  p.fill(X1 + 4, 26, 1, 70, C.SIGNAL_HOT, 0.6);
}


// ---- the Map page: the Atlas table inside the band ------------------------------------------------------------------

function glassRect(p, x, y, w, h, a = 0.74, edge = 0.45) {
  p.gradV(x, y, w, h, C.GLASS_TOP, a, C.GLASS, a + 0.06);
  for (let yy = y + 1; yy < y + h; yy += 3) p.fillPx(p.X(x), p.Y(yy), p.X(x + w) - p.X(x), 1, C.SIGNAL, 0.03);
  p.outline(x, y, w, h, C.SIGNAL, edge);
}

function gatePin(p, x, y, h, rank, label, sub, o = {}) {
  const c = o.broken ? C.WARN : C.RANK[rank];
  // a pillar of the gate's own light rising out of the cloud, as it stands in the world
  p.fill(x - 2, y - h, 4, h, C.NAVY, 0.55);
  p.glow(q => q.fill(x - 1, y - h, 2, h, c, 1), 3.5, 1);
  p.gradV(x - 1, y - h, 2, h, C.INK_HI, 0.5, c, 1);
  p.glow(q => { q.fill(x - 5, y - 1, 10, 2, c, 1); q.fill(x - 3, y - 2, 6, 4, c, 1); }, 3, 1);
  p.fill(x - 2, y - 1, 4, 2, C.INK_HI, 0.9);
  const lw = Font.width(label), sw = sub ? smallW(p, sub) : 0, cw = Math.max(lw, sw) + 12;
  const cx = o.left ? x - 4 - cw : x + 4, cy = y - h - 2;
  p.fill(cx, cy, cw, sub ? 20 : 12, C.NAVY, 0.84);
  p.fill(o.left ? cx + cw - 2 : cx, cy, 2, sub ? 20 : 12, c, 1);
  if (o.hot) { p.outline(cx, cy, cw, 20, c, 0.9); focusTicks(p, cx, cy, cw, 20, c); }
  p.text(label, cx + 6, cy + 2.5, { c: C.INK_HI });
  if (sub) small(p, sub, cx + 6, cy + 11.5, { c: o.subC || C.INK_DIM });
}

function mapPage(p, d) {
  // the band's rail stays on the table's edge: the map is one of the band's pages
  glassRect(p, 4, 6, 32, p.h - 12, 0.95, 0.5);
  p.ctx.save(); p.ctx.translate(p.X(4) - p.X(0), p.Y(6) - p.Y(0));
  const hh = p.h; p.h = hh - 12;
  rail(p, 5, d.badges || {});
  p.h = hh;
  p.ctx.restore();
  const hx = 46;
  micro(p, 'MAP', hx, 13, { c: C.SIGNAL_HOT });
  p.text('The Commons', hx + 22, 11, { c: C.INK_HI, glow: 0.3 });
  small(p, '·  Carillon, Choir Square  ·  creatures level 1 to 8', hx + 22 + Font.width('The Commons') + 4, 11.5, { c: C.INK_DIM });
  small(p, '1,079 places known', 470, 11.5, { c: C.INK_DIM, align: 'right' });

  for (const g of d.pins) gatePin(p, g.x, g.y, g.h, g.rank, g.label, g.sub, g);
  for (const r of d.rumours) {
    p.text('?', r.x - 10, r.y - 5, { scale: 2, c: C.AMBER, glow: 0.8, glowBlur: 2.5 });
    small(p, r.t, r.x + 6, r.y, { c: C.AMBER });
  }

  // the legend: gates first, then the lands, then what's saved for later
  const lx = 39, ly = 32, lw = 172;
  let total = 24;
  for (const g of d.gates) total += g.hot ? 45 : 21;
  glassRect(p, lx, ly, lw, total + 14, 0.95, 0.4);
  let tx = lx + 6;
  ['GATES · ' + d.gates.length, 'LANDS', 'SAVED · 3'].forEach((t, i) => {
    const w = microW(p, t) + 8, on = i === 0;
    p.fill(tx, ly + 6, w, 10, C.SIGNAL, on ? 0.22 : 0.06);
    p.outline(tx, ly + 6, w, 10, C.SIGNAL, on ? 0.9 : 0.3);
    micro(p, t, tx + 4, ly + 8.5, { c: on ? C.INK_HI : C.INK_DIM });
    tx += w + 4;
  });
  let y = ly + 22;
  for (const g of d.gates) {
    const c = g.broken ? C.WARN : C.RANK[g.rank];
    const hot = g.hot, rh = hot ? 43 : 19;
    if (hot) { p.fill(lx + 3, y - 2, lw - 6, rh, C.SIGNAL, 0.14); p.fill(lx + 3, y - 2, 2, rh, C.SIGNAL_HOT, 1); }
    const chip = g.broken ? '!' : g.rank, cw = 10;
    p.fill(lx + 8, y, cw, 10, c, 0.18); p.outline(lx + 8, y, cw, 10, c, 0.9);
    p.text(chip, lx + 8 + (cw - Font.width(chip)) / 2 + 0.5, y + 1.5, { c });
    p.text(g.name, lx + 22, y, { c: hot ? C.INK_HI : C.INK });
    small(p, g.where, lx + lw - 6, y + 0.5, { c: C.INK_DIM, align: 'right' });
    small(p, g.when, lx + 22, y + 9.5, { c: g.whenC || C.INK_DIM });
    if (hot) {
      small(p, g.more, lx + 22, y + 18.5, { c: C.INK_DIM });
      button(p, 'Point me there', lx + 22, y + 27, { key: 'T', hot: true });
    }
    y += hot ? 45 : 21;
  }
  small(p, 'Click a gate to fly to it.', lx + 8, y + 1, { c: C.INK_FAINT });

  const fy = p.h - 15;
  let fx = 46;
  for (const [k, t] of [['DRAG', 'turn'], ['RIGHT-DRAG', 'move'], ['SCROLL', 'zoom'], ['C', 'back to you'], ['1-6', 'pages'], ['K', 'close']]) {
    fx += keycap(p, k, fx, fy) + 3;
    fx += small(p, t, fx, fy + 1, { c: C.INK_DIM }) + 9;
  }
}
