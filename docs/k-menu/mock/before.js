// The K menu as it is today (HoloStatusScreen + StatusLayout + Ui sections), redrawn from the code's own numbers:
// 272×228 units, tabs on top (the panel is under StatusLayout.SIDE_TABS_MIN_W), content 246×192 at (10, 28),
// section titles in the System's blue, Ui.LINE 10, a 12-unit gap between sections.

const BEFORE = { W: 272, H: 228, CX: 10, CY: 28, CW: 246, CH: 192, GAP: 12 };

class UiCursor { // a small Ui: wrapped text, pairs, bars, chip/button rows
  constructor(p, x, y, w, clipTop, clipBottom) { this.p = p; this.x = x; this.y = y; this.w = w; this.top = clipTop; this.bottom = clipBottom; }
  vis(h) { return this.y + h > this.top - 2 && this.y < this.bottom + 2; }
  text(s, c, indent = 0) {
    for (const line of this.p.wrap(s, this.w - indent)) {
      if (this.vis(10)) this.p.text(line, this.x + indent, this.y, { c });
      this.y += 10;
    }
  }
  pair(l, r, cl, cr) {
    if (Font.width(l) + 8 + Font.width(r) <= this.w) {
      if (this.vis(10)) { this.p.text(l, this.x, this.y, { c: cl }); this.p.text(r, this.x + this.w, this.y, { c: cr, align: 'right' }); }
      this.y += 10;
    } else { this.text(l, cl); this.text(r, cr, 8); }
  }
  bar(f, c) {
    if (this.vis(6)) {
      this.p.fill(this.x, this.y, this.w, 4, 0x0A1020, 0.9);
      this.p.gradH(this.x, this.y, Math.round(this.w * f), 4, c, 1, c, 0.75);
      this.p.outline(this.x, this.y, this.w, 4, C.EDGE, 0.25);
    }
    this.y += 6;
  }
  chips(items) { // [{t, c, button}]
    let fx = this.x, h = 0;
    for (const it of items) {
      const w = it.button ? Font.width(it.t) + 12 : Font.width(it.t) + 8, hh = it.button ? 13 : 11;
      if (fx > this.x && fx + w > this.x + this.w) { this.y += h + 3; fx = this.x; h = 0; }
      h = Math.max(h, hh);
      if (this.vis(hh)) {
        if (it.text) this.p.text(it.t, fx, this.y + 2, { c: it.c });
        else if (it.button) {
          this.p.fill(fx, this.y, w, 13, 0x1B2A44, 0.85); this.p.outline(fx, this.y, w, 13, it.c, 0.6);
          this.p.text(it.t, fx + 6, this.y + 3, { c: C.TEXT });
        } else {
          this.p.fill(fx, this.y, w, 11, it.c, 0.12); this.p.outline(fx, this.y, w, 11, it.c, 0.7);
          this.p.text(it.t, fx + 4, this.y + 2, { c: it.c });
        }
      }
      fx += w + (it.text ? 6 : 4);
    }
    this.y += h + 3;
  }
  gap(n) { this.y += n; }
}

function drawBefore(p, tabs, active, sections, scroll = 0, closeHint = 'K to close', o = {}) {
  const { W, H, CX, CY, CW, CH, GAP } = BEFORE;
  p.material({});
  // tabs in a row on top
  let x = 8;
  tabs.forEach((t, i) => {
    const tw = Font.width(t) + 16, on = i === active;
    if (on) { p.fill(x, 6, tw, 16, C.EDGE, 0.16); p.hline(x, x + tw, 21, C.EDGE, 1); }
    p.text(t, x + 8, 6 + 4, { c: on ? C.TITLE : C.LABEL });
    x += tw + 4;
  });
  const cw = Font.width(closeHint);
  if (x + 8 + cw <= W - 8) p.text(closeHint, W - 8 - cw, 10, { c: C.LABEL, a: 0.7 });
  p.hline(8, W - 8, 23, C.EDGE, 0.18);
  // the stack
  p.clip(CX, CY, CW + 6, CH);
  const ui = new UiCursor(p, CX, CY - scroll, CW, CY, CY + CH);
  let total = 0;
  for (const s of sections) {
    if (o.refined) {
      // the refined direction: quiet spaced labels and a hairline instead of loud blue headings
      if (ui.vis(10)) {
        const w = p.text(s.title, CX, ui.y + 2, { scale: 0.6, track: 1, c: C.INK_DIM, halo: 0.18 });
        p.hline(CX + w + 4, CX + CW, ui.y + 4.5, C.EDGE, 0.22);
      }
      ui.y += 10;
    } else {
      if (ui.vis(10)) p.text(s.title, CX, ui.y, { c: C.EDGE, a: 0.85, glow: 0.35 });
      ui.y += 10 + 2;
    }
    s.build(ui, p);
    ui.y += GAP;
  }
  p.unclip();
  total = ui.y + scroll - CY;
  // the scroll bar
  if (total > CH) {
    const barH = Math.max(20, CH * CH / total), barY = CY + (CH - barH) * scroll / Math.max(1, total - CH);
    p.fill(CX + CW + 3, barY, 2, barH, C.EDGE, 0.5);
  }
  p.frame({});
  return total;
}

// sections as the code builds them --------------------------------------------------------------------------------

const S = {
  now: (lines) => ({ title: 'NOW', build: (ui) => lines.forEach((l, i) => ui.text('◇ ' + l, i === 0 ? C.TITLE : C.TEXT)) }),
  spine: (d) => ({ title: 'STATUS', build: (ui) => {
    const chips = [{ t: d.name, c: C.TITLE, text: true }, { t: d.rank + '-rank', c: C.RANK[d.rank] }];
    if (d.path) chips.push({ t: d.path, c: C.VIOLET });
    if (d.title) chips.push({ t: '“' + d.title + '”', c: C.AMBER });
    ui.chips(chips);
    ui.pair('Level ' + d.level, d.xp.toLocaleString('en') + ' / ' + d.need.toLocaleString('en') + ' XP', C.TEXT, C.LABEL);
    ui.bar(d.xp / d.need, C.EDGE);
    ui.text('◇ ' + (d.need - d.xp).toLocaleString('en') + ' XP to level ' + (d.level + 1) + '.', C.TEXT);
    if (d.points) ui.text('◇ ' + d.points + ' stat point' + (d.points === 1 ? '' : 's') + ' to spend: press + next to a stat below.', C.LANTERN);
    ui.text('◇ Next rank: ' + d.next + ' at level ' + d.nextAt + ' (' + (d.nextAt - d.level) + ' levels to go), then pass its Reassessment.', C.TEXT);
    if (d.opens) ui.text('◇ ' + d.next + '-rank opens what waits for you: ' + d.opens + '.', C.HORIZON);
    if (d.today) ui.text(d.today, C.LABEL);
  } }),
  stats: (d) => ({ title: 'STATS', build: (ui, p) => {
    const HINT = ['melee, Kinetic', 'speed, Flow', 'health, resistance', 'energy, Energy/Tech', 'crits, secrets, omens', 'Veil, dreams, Echoes'];
    ['STR', 'AGI', 'VIT', 'INT', 'PER', 'SPI'].forEach((s, i) => {
      const sy = ui.y + i * 16;
      if (ui.vis(16)) {
        p.text(s, ui.x, sy + 3, { c: C.LABEL });
        p.text(String(d.stats[i]), ui.x + 26, sy + 3, { c: C.TITLE });
        p.text(HINT[i], ui.x + 52, sy + 3, { c: C.LABEL, a: 0.65 });
        if (d.points) {
          const bx = ui.x + ui.w - 20;
          p.fill(bx, sy, 14, 13, 0x1B2A44, 0.85); p.outline(bx, sy, 14, 13, C.EDGE, 0.6); p.text('+', bx + 4, sy + 3, { c: C.TEXT });
        }
      }
    });
    ui.gap(6 * 16);
    ui.text(d.points ? d.points + ' points to spend' : 'No points to spend', d.points ? C.LANTERN : C.LABEL);
  } }),
  atlas: (img, places) => ({ title: 'ATLAS', build: (ui, p) => {
    const w = ui.w, h = Math.round(w * 280 / 480);
    if (ui.vis(h)) {
      p.clip(BEFORE.CX, BEFORE.CY, BEFORE.CW + 6, BEFORE.CH);
      p.sprite(img, ui.x, ui.y, w, h, 1);
      p.unclip();
      p.outline(ui.x - 1, ui.y - 1, w + 2, h + 2, C.EDGE, 0.35);
    }
    ui.gap(h + 4);
    ui.chips([{ t: 'Open the table  ·  M', c: C.EDGE, button: true }, { t: places + ' places known', c: C.LABEL, text: true }]);
  } }),
  journal: (entries, unread) => ({ title: unread ? 'JOURNAL · ' + unread + ' NEW' : 'JOURNAL', build: (ui) => {
    entries.forEach((e, i) => {
      ui.pair((i < unread ? '◆ ' : '◇ ') + e.t, e.ago, e.c || C.TITLE, C.LABEL);
      if (e.l) ui.text(e.l, C.TEXT, 6);
      ui.gap(2);
    });
  } }),
  waiting: (rows) => ({ title: 'SAVED FOR LATER', build: (ui) => rows.forEach(r => ui.chips([{ t: r.need, c: r.c }, { t: r.what + '  ' + r.where, c: C.TEXT, text: true }])) }),
  road: (rows) => ({ title: 'THE ROAD', build: (ui) => rows.forEach(r => { ui.pair('Level ' + r.l + ' · ' + r.t, '', C.TITLE, C.LABEL); ui.text(r.x, C.LABEL, 6); }) }),
  directives: (rows) => ({ title: 'DIRECTIVES', build: (ui) => rows.forEach(r => { ui.text(r.t, C.TITLE); ui.text(r.m, C.LABEL, 4); ui.gap(1); ui.bar(r.f, C.GOOD); }) }),
  generic: (title, lines) => ({ title, build: (ui) => lines.forEach(l => ui.text(l, C.TEXT)) }),
};
