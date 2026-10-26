async function bgStage(stage, src, o = {}) {
  const img = await loadImg(src);
  stage.style.width = img.width / 2 + 'px'; stage.style.height = img.height / 2 + 'px';
  img.className = 'bg'; img.style.width = img.width / 2 + 'px'; img.style.height = img.height / 2 + 'px';
  if (o.filter) img.style.filter = o.filter;
  stage.appendChild(img);
  return img;
}

async function loadIcons(ids) {
  const out = {};
  for (const id of ids) out[id] = await loadImg('icons/' + id + '.png');
  return out;
}

// ---- one hunter, the same in every scene: level 14, D-rank, mid-errand -------------------------------------------
const HUNTER = {
  name: 'Euan', title: 'Gatebreaker', rank: 'D', level: 14, xp: 3420, need: 5100, next: 'C', nextAt: 25,
  road: 'The Veil', points: 3, stats: [18, 22, 16, 12, 15, 10],
  unread: 2, journalAll: 23,
  context: { t: 'Take the letters to the Wayfarer camp', right: '↗ 181 m', sub: 'The Last Delivery · step 3 of 5' },
  journal: [
    { t: 'New creature · Mossback', l: 'A slow grazer of the Lumen woods. Kept in your Codex.', ago: '4 min' },
    { t: 'A gate is drawing breath · 320 m N', l: 'An E-rank gate. Its floors make you stronger.', ago: '12 min' },
    { t: 'Entered · The Commons', l: 'Creatures here are level 1 to 8.', ago: '31 min' },
    { t: 'Heard of · a Briar Sow wallow', l: 'Somewhere east of the camp.', ago: '40 min' },
  ],
  now: ['Take the letters to the Wayfarer camp ↗ 181 m', 'Spend 3 points · K', 'Level 15: The Veil'],
};

function beforeSections(d, glance) {
  return [
    S.now(d.now),
    S.atlas(glance, 1079),
    S.journal(d.journal.map(e => ({ t: e.t, l: e.l, ago: e.ago + ' ago' })), d.unread),
    S.spine({ name: d.name, rank: d.rank, level: d.level, xp: d.xp, need: d.need, next: d.next, nextAt: d.nextAt, points: d.points, title: d.title,
      opens: 'A sky core’s wings · Gardens, in the Descent', today: 'Today: level 13 → 14 · 41 kills · 2 gates · best: Briar Sow' }),
    S.waiting([{ need: 'C-rank', c: C.RANK.C, what: 'A sky core’s wings', where: '640 m NE' }, { need: 'level 20', c: C.HORIZON, what: 'Gardens, in the Descent', where: '' }]),
    S.stats(d),
    S.road([{ l: 15, t: 'The Veil', x: 'Meditate, then let go of G: step outside.' }, { l: 16, t: 'The Scanner', x: 'Ruins hum back at it.' }]),
    S.directives([{ t: 'Defeat 5 foes bare-handed.', m: 'Reward: 120 XP · fades in 3 play-days', f: 0.4 }]),
    S.generic('THE ASCENT', ['Runner · yours from the start', '◇ The Tether · next', 'A Precursor vault keeps a line for a hunter it opens to.']),
    S.generic('POWERS', ['(the loadout: six keys, 12 powers, the chosen power)']),
  ];
}

const SCENES = {
  async calib(stage) {
    const bg = await bgStage(stage, 'bg/arm_grass.png');
    const p = new Panel(272, 228, 5);
    drawBefore(p, ['Status', 'Truths', 'Codex', 'Gate Map'], 0, [
      S.now(['Rest Shrine ↓ 12 m', 'Spend 3 points · K', 'Bell Crypt · floor 2']),
      S.spine({ name: 'Dev', rank: 'E', level: 1, xp: 15, need: 150, next: 'D', nextAt: 10 }),
      S.stats({ stats: [5, 5, 5, 5, 5, 5], points: 0 }),
    ]);
    const c = place(stage, p);
    beams(stage, bg.width, bg.height, [c[2], c[3]], 0.25);
  },

  // today's panel, the same hunter, opened (the Status tab at the top)
  async before(stage) {
    const bg = await bgStage(stage, 'bg/arm_grass.png');
    const glance = await loadImg('bg/glance.png');
    const p = new Panel(272, 228, 5);
    drawBefore(p, ['Status', 'Truths', 'Codex', 'Gate Map'], 0, beforeSections(HUNTER, glance));
    place(stage, p);
  },

  // the proposal: the Status page, the cursor on AGI
  async after(stage) {
    const bg = await bgStage(stage, 'bg/arm_grass.png');
    const p = new Panel(A.W, A.H, 5);
    p.material({ wristLight: true, tint: 0.46 });
    rail(p, 0, { status: { n: 3, c: C.AMBER }, journal: { n: 2, c: C.SIGNAL_HOT } });
    statusPage(p, { ...HUNTER, focus: 1 });
    p.frame({});
    place(stage, p);
  },
};

const POWERS_DATA = {
  energy: '120 / 120', regen: '6.0',
  slots: ['haymaker', 'shockwave', 'spirit_palm', 'mend', 'frost_nova', 'dash'],
  selected: 'haymaker',
  owned: [
    { id: 'haymaker', name: 'Haymaker', fam: 'KINETIC', lvl: 3, mastery: 0.34, meta: 'KINETIC · ACTIVE · 18 ENERGY · 5 S',
      desc: 'Hold to wind up, release to punch. A full charge launches the target across the room.' },
    { id: 'shockwave', fam: 'KINETIC', lvl: 2 }, { id: 'ground_slam', fam: 'KINETIC', lvl: 1 },
    { id: 'spirit_palm', fam: 'SPIRIT', lvl: 2 }, { id: 'mend', fam: 'SPIRIT', lvl: 2 }, { id: 'frost_nova', fam: 'SPIRIT', lvl: 1, cooldown: true },
    { id: 'beam', fam: 'ENERGY', lvl: 1 }, { id: 'barrier', fam: 'ENERGY', lvl: 1 }, { id: 'charged_shot', fam: 'ENERGY', lvl: 1 },
    { id: 'dash', fam: 'VELOCITY', lvl: 4 }, { id: 'lightning_step', fam: 'VELOCITY', lvl: 1 }, { id: 'wall_run', fam: 'VELOCITY', lvl: 1 },
    { id: 'grapple', fam: 'TECH', lvl: 2 }, { id: 'cloak', fam: 'SHADOW', lvl: 1 }, { id: 'danger_sense', fam: 'MIND', lvl: 1, passive: true },
  ],
  fits: [0, 1, 2, 3],
};

const JOURNAL_DATA = {
  filter: 0, unread: 2,
  task: { title: 'The Last Delivery', done: 2, steps: [
    { t: 'Leave the city through the High Gate' }, { t: 'Tell Kestrel what you saw at the Roost' },
    { t: 'Take the letters to the Wayfarer camp', where: '↗ 181 m' }, { t: 'Give Ilse the letters' }, { t: 'Place your Ember where you want your home' }] },
  also: [
    { t: 'Defeat 5 foes bare-handed', f: 0.4 },
    { t: 'Today’s regimen: Dash 15 times', r: '9 / 15' },
    { t: 'Marta’s Wheel: eight sticks for her wagon', r: '3 / 8' },
  ],
  saved: [
    { need: 'C-RANK', c: C.RANK.C, what: 'A sky core’s wings', where: '640 m NE' },
    { need: 'LEVEL 20', c: C.HORIZON, what: 'Gardens, in the Descent', where: 'below' },
    { need: 'THE TETHER', c: C.HORIZON, what: 'A Tether Spire’s crown', where: '1.2 km W' },
  ],
  log: [
    { t: 'New creature · Mossback', ago: '4 min' }, { t: 'A gate is drawing breath · 320 m N', ago: '12 min' },
    { t: 'Entered · The Commons', ago: '31 min' }, { t: 'Heard of · a Briar Sow wallow', ago: '40 min' },
    { t: 'Level 14 · 3 points to spend', ago: '52 min' },
  ],
};

Object.assign(SCENES, {
  async powers(stage) {
    await bgStage(stage, 'bg/arm_grass.png');
    const icons = await loadIcons(POWERS_DATA.owned.map(o => o.id));
    const p = new Panel(A.W, A.H, 5);
    p.material({ wristLight: true, tint: 0.46 });
    rail(p, 1, { status: { n: 3, c: C.AMBER }, journal: { n: 2, c: C.SIGNAL_HOT } });
    powersPage(p, POWERS_DATA, icons);
    p.frame({});
    place(stage, p);
  },
  async journal(stage) {
    await bgStage(stage, 'bg/arm_grass.png');
    const p = new Panel(A.W, A.H, 5);
    p.material({ wristLight: true, tint: 0.46 });
    rail(p, 2, { status: { n: 3, c: C.AMBER } });
    journalPage(p, JOURNAL_DATA);
    p.frame({});
    place(stage, p);
  },
});

function placeFlat(stage, panel) {
  const el = panel.canvas;
  el.style.cssText = `position:absolute;left:${-panel.m * panel.ppu / 2}px;top:${-panel.m * panel.ppu / 2}px;width:${el.width / 2}px;height:${el.height / 2}px`;
  stage.appendChild(el);
}

const MAP_DATA = {
  badges: { status: { n: 3, c: C.AMBER }, journal: { n: 2, c: C.SIGNAL_HOT } },
  pins: [
    { x: 262, y: 74, h: 34, rank: 'E', label: 'Bell Crypt', sub: '320 m N · 4 days' },
    { x: 400, y: 160, h: 44, rank: 'D', label: 'Mossdeep Hollow', sub: '860 m E · 2 days', subC: C.AMBER, hot: true, left: true },
    { x: 214, y: 240, h: 26, rank: 'E', label: 'Old Quarry · broken', sub: '1.4 km SW', subC: C.WARN, broken: true },
  ],
  rumours: [{ x: 352, y: 252, t: 'A Briar Sow wallow' }],
  gates: [
    { rank: 'D', name: 'Mossdeep Hollow', where: '860 m E', when: 'breaks in 2 days', whenC: C.AMBER, hot: true, more: 'You saw its light from the High Gate.' },
    { rank: 'E', name: 'Bell Crypt', where: '320 m N', when: 'breaks in 4 days' },
    { rank: 'E', name: 'Old Quarry', where: '1.4 km SW', when: 'broken: its monsters are out', whenC: C.WARN, broken: true },
    { rank: 'C', name: 'Glasswater Deep', where: '1.9 km NE', when: 'breaks in 6 days · above your rank' },
  ],
};

Object.assign(SCENES, {
  async map(stage) {
    await bgStage(stage, 'bg/atlas_drift.png');
    const p = new Panel(480, 300, 6, 0);
    // the old header, legend and footer are part of the captured frame: the new ones go over them
    glassRect(p, 37, 3, 441, 22, 0.97, 0.0);
    glassRect(p, 37, 278, 441, 21, 0.97, 0.0);
    mapPage(p, MAP_DATA);
    placeFlat(stage, p);
  },
});

// the page's content drawn on its own layer, so a transition can slide and fade it without touching the glass
function layer(p, fn, dx, alpha) {
  const q = new Panel(p.w, p.h, p.ppu, p.m);
  fn(q);
  p.ctx.save(); p.ctx.globalAlpha = alpha; p.ctx.drawImage(q.canvas, Math.round(dx * p.ppu), 0); p.ctx.restore();
}

function statusPanel(d, o = {}) {
  const p = new Panel(A.W, A.H, o.ppu || 5);
  p.material({ wristLight: true, tint: 0.46 });
  rail(p, 0, o.badges || { status: d.points ? { n: d.points, c: C.AMBER } : null, journal: d.unread ? { n: d.unread, c: C.SIGNAL_HOT } : null });
  statusPage(p, d);
  p.frame({});
  return p;
}

const DENSE = {
  name: 'Euan', title: 'A Thousand Hands', path: 'Hollow', rank: 'B', level: 47, xp: 61250, need: 88400, next: 'A', nextAt: 70,
  road: 'The Inverted Sea', points: 5, stats: [62, 48, 55, 30, 41, 27], fractured: '6 MIN', reassess: false,
  unread: 9, journalAll: 60,
  context: { t: 'Mossdeep Hollow · floor 3 of 5', sub: 'Satchel 21 / 27 · 34 kills · 4:12 here', colour: C.RANK.D,
    buttons: [{ t: 'Satchel', key: 'V' }] },
  journal: [
    { t: 'Ready now · A sky core’s wings answers you', l: 'C-rank is yours. It stands 640 m NE of your Hearth.', ago: 'just now' },
    { t: 'A Directive faded · Clear a gate without being hit', l: 'It ran out of days. New ones come with the morning.', ago: '2 min' },
    { t: 'Heard of · the Gloam Stag’s rut, in the Bleak Vale', l: 'A Wayfarer said it bellows at dusk.', ago: '6 min' },
  ],
};

const FRESH = {
  name: 'Euan', rank: 'E', level: 1, xp: 15, need: 150, next: 'D', nextAt: 10, road: 'Hunter crafts', points: 0,
  stats: [5, 5, 5, 5, 5, 5], unread: 0,
  context: { t: 'Tell Kestrel what you saw at the Roost', right: '↗ 6 m', sub: 'The Last Delivery · step 1 of 5' },
  journal: [], journalEmpty: 'Nothing kept yet. What you find past the city walls, and what the System tells you, will be kept here.',
  today: 'Your band is new. Points to spend come with each level.',
};

Object.assign(SCENES, {
  async spend_pending(stage) {
    await bgStage(stage, 'bg/arm_grass.png');
    place(stage, statusPanel({ ...HUNTER, focus: null, pending: [1, 2, 0, 0, 0, 0] }));
  },
  async spend_done(stage) {
    await bgStage(stage, 'bg/arm_grass.png');
    place(stage, statusPanel({ ...HUNTER, points: 0, stats: [19, 24, 16, 12, 15, 10], flash: [0, 1], focus: null,
      journal: [{ t: 'You grew · STR 19, AGI 24', l: 'Three points spent. Agility now feeds your Dash.', ago: 'just now' }, ...HUNTER.journal], unread: 3 }));
  },
  async dense(stage) {
    await bgStage(stage, 'bg/arm_grass.png', { filter: 'brightness(0.34) saturate(0.6) hue-rotate(15deg) contrast(1.15)' });
    place(stage, statusPanel(DENSE));
  },
  async empty(stage) {
    await bgStage(stage, 'bg/arm_grass.png');
    place(stage, statusPanel(FRESH, { badges: {} }));
  },
  // a page change, frozen at 0, 90 and 180 ms: the outgoing page slides toward the rail and fades, the new one slides in
  async switch_mid(stage) {
    await bgStage(stage, 'bg/arm_grass.png');
    const icons = await loadIcons(POWERS_DATA.owned.map(o => o.id));
    const p = new Panel(A.W, A.H, 5);
    p.material({ wristLight: true, tint: 0.46 });
    // the rail's light travels between the two nodes
    rail(p, -1, { status: { n: 3, c: C.AMBER }, journal: { n: 2, c: C.SIGNAL_HOT } });
    p.fill(0, 9 + 17, 2, 30, C.SIGNAL_HOT, 0.95);
    p.glow(q => q.fill(0, 9 + 17, 2, 30, C.SIGNAL, 1), 2.4, 1);
    layer(p, q => statusPage(q, { ...HUNTER, focus: null }), -7, 0.30);
    layer(p, q => powersPage(q, POWERS_DATA, icons), 6, 0.55);
    p.frame({});
    place(stage, p);
  },
});

// a page prints in behind a scan line: content above `upTo` shows, a bright line sits at the edge
function printLayer(p, fn, upTo, alpha = 1) {
  const q = new Panel(p.w, p.h, p.ppu, p.m);
  fn(q);
  p.ctx.save();
  p.ctx.beginPath(); p.ctx.rect(p.X(A.RAIL + 1), p.Y(0), p.X(p.w) - p.X(A.RAIL + 1), p.Y(upTo) - p.Y(0)); p.ctx.clip();
  p.ctx.globalAlpha = alpha; p.ctx.drawImage(q.canvas, 0, 0);
  p.ctx.restore();
  p.gradV(A.RAIL + 1, upTo - 10, p.w - A.RAIL - 2, 10, C.SIGNAL, 0, C.SIGNAL, 0.16);
  p.hline(A.RAIL + 1, p.w - 1, upTo, C.SIGNAL_HOT, 0.95, Math.round(p.ppu * 0.5));
  p.glow(q2 => q2.hline(A.RAIL + 1, p.w - 1, upTo, C.SIGNAL, 1, Math.round(q2.ppu * 0.8)), 2, 0.9);
}

function railBead(p, fromI, toI, f) {
  const y = 9 + (fromI + (toI - fromI) * f) * 34;
  p.fill(0, y + 1, 2, 29, C.SIGNAL_HOT, 0.95);
  p.glow(q => q.fill(0, y + 1, 2, 29, C.SIGNAL, 1), 2.4, 1);
  p.glow(q => q.fill(0, y + 8, 3, 14, C.SIGNAL_HOT, 1), 4, 0.8);
}

Object.assign(SCENES, {
  async switch_a(stage) { // 40 ms: the bead leaves Status, the old page dims
    await bgStage(stage, 'bg/arm_grass.png');
    const p = new Panel(A.W, A.H, 5);
    p.material({ wristLight: true, tint: 0.46 });
    rail(p, -1, { status: { n: 3, c: C.AMBER }, journal: { n: 2, c: C.SIGNAL_HOT } });
    railBead(p, 0, 1, 0.35);
    layer(p, q => statusPage(q, { ...HUNTER, focus: null }), -3, 0.35);
    p.frame({});
    place(stage, p);
  },
  async switch_b(stage) { // 110 ms: Powers prints in behind the scan line
    await bgStage(stage, 'bg/arm_grass.png');
    const icons = await loadIcons(POWERS_DATA.owned.map(o => o.id));
    const p = new Panel(A.W, A.H, 5);
    p.material({ wristLight: true, tint: 0.46 });
    rail(p, -1, { status: { n: 3, c: C.AMBER }, journal: { n: 2, c: C.SIGNAL_HOT } });
    railBead(p, 0, 1, 0.92);
    printLayer(p, q => powersPage(q, POWERS_DATA, icons), 118);
    p.frame({});
    place(stage, p);
  },
});

async function flatOver(stage, src) {
  const img = await loadImg(src);
  stage.style.width = '870px'; stage.style.height = '720px';
  img.className = 'bg'; img.style.width = '870px'; img.style.height = '720px';
  stage.appendChild(img);
  const p = statusPanel({ ...HUNTER, focus: 1 });
  placeFlat(stage, p);
  p.canvas.style.left = '0px'; p.canvas.style.top = '0px';
}

Object.assign(SCENES, {
  t_cloud: (s) => flatOver(s, 'bg/t_cloud.png'),
  t_grass: (s) => flatOver(s, 'bg/t_grass.png'),
  t_night: (s) => flatOver(s, 'bg/t_night.png'),

  // Direction A: the same panel and tabs, reordered and lightly refined
  async dirA(stage) {
    await bgStage(stage, 'bg/arm_grass.png');
    const p = new Panel(272, 228, 5);
    const d = HUNTER;
    drawBefore(p, ['Status', 'Truths', 'Codex', 'Map'], 0, [
      S.spine({ name: d.name, rank: d.rank, level: d.level, xp: d.xp, need: d.need, next: d.next, nextAt: d.nextAt, points: 0, title: d.title }),
      S.stats(d),
      S.journal(d.journal.map(e => ({ t: e.t, l: e.l, ago: e.ago + ' ago' })), d.unread),
      S.now(d.now),
    ], 0, 'K to close', { refined: true });
    place(stage, p);
  },

  // Direction C: the Orrery, pages as panes in an arc round the wrist
  async dirC(stage) {
    const bg = await bgStage(stage, 'bg/arm_grass.png');
    const icons = await loadIcons(POWERS_DATA.owned.map(o => o.id));
    const pj = new Panel(A.W, A.H, 5);
    pj.material({ tint: 0.42 }); rail(pj, 2, {}); journalPage(pj, JOURNAL_DATA); pj.frame({});
    const pw = new Panel(A.W, A.H, 5);
    pw.material({ tint: 0.42 }); rail(pw, 1, {}); powersPage(pw, POWERS_DATA, icons); pw.frame({});
    // the satellites: planes turned away from you, further round the arc
    const H2 = homography([[0, 0], [A.W, 0], [0, A.H], [A.W, A.H]], [[2240, 420], [2700, 470], [2240, 1190], [2700, 1120]]);
    const H1 = homography([[0, 0], [A.W, 0], [0, A.H], [A.W, A.H]], [[1600, 300], [2250, 380], [1600, 1300], [2250, 1210]]);
    place(stage, pj, 0, 0, H2).forEach(() => {}); pj.canvas.style.opacity = '0.38';
    place(stage, pw, 0, 0, H1); pw.canvas.style.opacity = '0.62';
    const p = statusPanel({ ...HUNTER, focus: null });
    place(stage, p);
    micro;
  },
});

async function glassOver(stage, src, tint) {
  const img = await loadImg(src);
  stage.style.width = '870px'; stage.style.height = '720px';
  img.className = 'bg'; img.style.width = '870px'; img.style.height = '720px';
  stage.appendChild(img);
  const p = new Panel(A.W, A.H, 5);
  p.material({ wristLight: true, tint });
  placeFlat(stage, p);
  p.canvas.style.left = '0px'; p.canvas.style.top = '0px';
}
Object.assign(SCENES, {
  g_cloud: (s) => glassOver(s, 'bg/t_cloud.png', 0.42), g_grass: (s) => glassOver(s, 'bg/t_grass.png', 0.42),
  g_night: (s) => glassOver(s, 'bg/t_night.png', 0.42), g_cloud_old: (s) => glassOver(s, 'bg/t_cloud.png', 0.38),
});

// ---- the map in the world: placement per base frame (frame px, 2880×1800) ----------------------------------------
const MS = {
  day: { region: [880, 318], bg: 'bg/ms_day.png', disc: { cx: 1450, cy: 505, rx: 600, ry: 265 }, wrist: [1528, 846], legend: { x: 2130, y: 250 }, dim: 0.30 },
  night: { atlas: 'bg/atlas_night.jpg', src: [0, 84, 960, 432], bg: 'bg/ms_night.png', disc: { cx: 1450, cy: 545, rx: 600, ry: 265 }, wrist: [1530, 890], legend: { x: 2130, y: 290 }, dim: 0.18 },
  mid: { bg: 'bg/ms_mid.png', disc: { cx: 1450, cy: 610, rx: 520, ry: 180 }, wrist: [1515, 870], dim: 0.14, reveal: 0.55 },
  others: { bg: 'bg/ms_front.png', disc: { cx: 1440, cy: 1190, rx: 380, ry: 115 }, wrist: [1470, 930] },
};
// the Atlas picture used for the disc: the city in its clouds (the zoomed-out table)
const ATLAS_SRC = [410, 630, 2000, 900];

async function mapScene(stage, k, o = {}) {
  const m = MS[k];
  const bg = await bgStage(stage, m.bg);
  const atlas = await loadImg(m.atlas || 'bg/atlas_drift.png');
  const W = bg.naturalWidth, H = bg.naturalHeight;
  const g = frameCanvas(stage, W, H);
  sceneDim(g, W, H, m.dim);
  const d = { ...m.disc, src: m.src || ATLAS_SRC, reveal: m.reveal || 1 };
  bandBeams(g, m.wrist, d.cx, d.cy, d.rx, d.ry, m.reveal ? 0.55 : 0.42);
  holoDisc(g, atlas, d);
  if (o.pins !== false && !m.reveal) {
    // pillars at places on the land: the D gate east of the city, the Bell Crypt north, a broken one south-west
    const at = (u, v) => [d.cx + u * d.rx, d.cy + v * d.ry];
    let [x, y] = at(0.55, -0.05);
    mapPillar(stage, g, x, y, 190, C.RANK.D, { w: 96, h: 22, left: true, draw: (p) => { p.text('Mossdeep Hollow', 6, 3, { c: C.INK_HI }); small(p, 'D · 860 m E · 2 days', 6, 12, { c: C.AMBER }); } });
    [x, y] = at(-0.05, -0.62);
    mapPillar(stage, g, x, y, 150, C.RANK.E, { w: 90, h: 22, left: true, draw: (p) => { p.text('Bell Crypt', 6, 3, { c: C.INK_HI }); small(p, 'E · 320 m N · 4 days', 6, 12, { c: C.INK_DIM }); } });
    [x, y] = at(-0.62, 0.42);
    mapPillar(stage, g, x, y, 120, C.WARN, { w: 88, h: 22, left: true, draw: (p) => { p.text('Old Quarry', 6, 3, { c: C.INK_HI }); small(p, 'broken · 1.4 km SW', 6, 12, { c: C.WARN }); } });
    // the region's name as a world plate (in the scene, the Atlas's zone chips become plates)
    if (m.region) {
      const rp = new Panel(106, 21, 4, 8);
      rp.material({ tint: 0.55, halo: 0.3 });
      rp.fill(0, 0, rp.w, rp.h, C.NAVY, 0.9);
      rp.fill(0, 0, 2, 21, 0x5FD08F, 1);
      rp.text('The Commons', 6, 2.5, { c: C.INK_HI });
      small(rp, 'creatures level 1 to 8', 6, 11.5, { c: 0x8FF0C0 });
      rp.frame({ bracket: 6 });
      rp.canvas.style.cssText = `position:absolute;left:${(m.region[0] - 32) / 2}px;top:${(m.region[1] - 32) / 2}px;width:${rp.canvas.width / 2}px;height:${rp.canvas.height / 2}px`;
      stage.appendChild(rp.canvas);
    }
    // a rumour in the cloud, and your own pin joined to you by a thread of light
    [x, y] = at(0.48, 0.55);
    const q = new Panel(70, 12, 4, 6);
    q.text('?', 0, 1, { scale: 1.4, c: C.AMBER, glow: 0.9, glowBlur: 2 });
    small(q, 'A Briar Sow wallow', 9, 2.5, { c: C.AMBER });
    q.canvas.style.cssText = `position:absolute;left:${(x - 24) / 2}px;top:${(y - 24) / 2}px;width:${q.canvas.width / 2}px;height:${q.canvas.height / 2}px`;
    stage.appendChild(q.canvas);
    const you = at(0.0, 0.08);
    g.save(); g.globalCompositeOperation = 'lighter';
    g.setLineDash([6, 8]); g.strokeStyle = 'rgba(196,230,255,0.45)'; g.lineWidth = 2;
    g.beginPath(); g.moveTo(you[0], you[1]); g.quadraticCurveTo((you[0] + m.wrist[0]) / 2 - 60, (you[1] + m.wrist[1]) / 2, m.wrist[0], m.wrist[1]); g.stroke();
    g.restore();
  }
  if (m.legend && o.legend !== false) {
    const lp = legendPanel({ gates: MAP_DATA.gates, badges: { status: C.AMBER, journal: C.SIGNAL_HOT } });
    // a panel turned a little toward you, beside the map
    const L = m.legend, pw = lp.w, ph = lp.h;
    const Hm = homography([[0, 0], [pw, 0], [0, ph], [pw, ph]], [[L.x, L.y], [L.x + 630, L.y + 36], [L.x, L.y + 540], [L.x + 630, L.y + 512]]);
    place(stage, lp, 0, 0, Hm);
  }
  if (o.keys !== false && !m.reveal) {
    const kp = sceneKeys([['DRAG', 'turn'], ['RIGHT-DRAG', 'move'], ['SCROLL', 'zoom'], ['TAB', 'next gate'], ['T', 'point me there'], ['SHIFT+M', 'flat map'], ['M', 'close']]);
    kp.canvas.style.cssText = `position:absolute;left:${(W / 2 - (462 + 12) * 5 / 2) / 2}px;top:${(H - 150) / 2}px;width:${kp.canvas.width / 2}px;height:${kp.canvas.height / 2}px`;
    stage.appendChild(kp.canvas);
  }
}

// what another player sees: the wrist raised, a content-free projection
async function othersScene(stage) {
  const m = MS.others;
  const bg = await bgStage(stage, m.bg);
  const W = bg.naturalWidth, H = bg.naturalHeight;
  const g = frameCanvas(stage, W, H);
  const { cx, cy, rx, ry } = m.disc;
  bandBeams(g, m.wrist, cx, cy, rx, ry, 0.35);
  g.save(); g.translate(cx, cy); g.scale(1, ry / rx);
  let gr = g.createRadialGradient(0, 0, 0, 0, 0, rx);
  gr.addColorStop(0, 'rgba(14,26,46,0.55)'); gr.addColorStop(1, 'rgba(14,26,46,0.2)');
  g.fillStyle = gr; g.beginPath(); g.arc(0, 0, rx, 0, Math.PI * 2); g.fill();
  g.globalCompositeOperation = 'lighter';
  // drifting wisps: soft blobs of light, no land
  for (let i = 0; i < 14; i++) {
    const a = i * 2.39, r = rx * (0.15 + 0.7 * ((i * 37) % 10) / 10), x = Math.cos(a) * r, y = Math.sin(a) * r;
    const b = g.createRadialGradient(x, y, 0, x, y, rx * 0.22);
    b.addColorStop(0, 'rgba(200,225,255,0.22)'); b.addColorStop(1, 'rgba(200,225,255,0)');
    g.fillStyle = b; g.beginPath(); g.arc(x, y, rx * 0.22, 0, Math.PI * 2); g.fill();
  }
  for (let i = 0; i < 5; i++) { const a = i * 1.7 + 0.4, r = rx * 0.5; g.fillStyle = 'rgba(196,230,255,0.9)'; g.beginPath(); g.arc(Math.cos(a) * r, Math.sin(a) * r, 5, 0, Math.PI * 2); g.fill(); }
  g.filter = 'blur(5px)'; g.strokeStyle = 'rgba(127,195,255,0.6)'; g.lineWidth = 9; g.beginPath(); g.arc(0, 0, rx, 0, Math.PI * 2); g.stroke();
  g.filter = 'none'; g.strokeStyle = 'rgba(196,230,255,0.85)'; g.lineWidth = 2.5; g.beginPath(); g.arc(0, 0, rx, 0, Math.PI * 2); g.stroke();
  g.restore();
}

Object.assign(SCENES, {
  ms_day: (s) => mapScene(s, 'day'),
  ms_night: (s) => mapScene(s, 'night'),
  ms_mid: (s) => mapScene(s, 'mid'),
  ms_others: (s) => othersScene(s),
});
