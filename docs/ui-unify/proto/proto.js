/* AWAKEN · the band's stage (prototype). Isolated: nothing here is game code. Everything shown is simulated from
   data.js; the look, layout, focus, keys, Track, Set aside and Undo behave as proposed in docs/ui-unify/PLAN.md. */
(function () {
  'use strict';
  const D = window.AW_DATA;
  const BASE = window.AW_ASSETS || 'assets/';
  const img = (n, cls) => n ? `<img ${cls ? `class="${cls}" ` : ''}src="${BASE}${n}${n.startsWith('g_') ? '.jpg' : '.png'}" alt="">` : '';
  const url = (n, ext) => `${BASE}${n}.${ext || 'png'}`;
  const esc = s => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;');

  // the six places, with the band's own 12 x 12 icons (holo.client.HoloStatusScreen.ICONS)
  const ICONS = {
    status: ['.....##.....', '....####....', '....####....', '....####....', '.....##.....', '............', '...######...', '..########..', '.##########.', '.##########.', '.##########.', '............'],
    tasks: ['............', '............', '.##..######.', '.##.........', '............', '.##..######.', '.##.........', '............', '.##..######.', '.##.........', '............', '............'],
    hearth: ['.....#......', '....##......', '....###.#...', '...#####....', '..##.####...', '..#...###...', '..#....##...', '...#..##....', '....####....', '..########..', '.#........#.', '............'],
    powers: ['.......###..', '......###...', '.....###....', '....###.....', '...#######..', '.......##...', '......##....', '.....##.....', '....##......', '...##.......', '..##........', '............'],
    journal: ['............', '.####..####.', '#....##....#', '#.##.##.##.#', '#....##....#', '#.##.##.##.#', '#....##....#', '#.##.##.##.#', '#....##....#', '.####..####.', '.....##.....', '............'],
    map: ['....####....', '...######...', '..###..###..', '..##....##..', '..###..###..', '...######...', '....####....', '.....##.....', '.....##.....', '............', '.##########.', '............'],
  };
  const PLACES = [['status', 'Status'], ['tasks', 'Tasks'], ['hearth', 'Hearth'], ['powers', 'Powers'], ['journal', 'Journal'], ['map', 'Map']];
  const svg = rows => `<svg viewBox="0 0 12 12" fill="currentColor">${rows.map((r, y) => [...r].map((c, x) => c === '#' ? `<rect x="${x}" y="${y}" width="1" height="1"/>` : '').join('')).join('')}</svg>`;

  const S = { open: true, place: 'tasks', task: null, choice: null, chosen: null, tracked: null, aside: [], undo: null, seen: {},
    placed: {}, stat: 1, medal: null, room: 'armoury', power: 'haymaker', keys: null, find: 'e_briar_sow', shelf: 'creatures', hall: null, toast: null };
  S.keys = D.powers.keys.map(k => k.slice());
  let root, toastTimer;

  const T = () => D[S.set] || D; // the sample: Euan's moment, 'dense' (C-rank, a lot open) or 'fresh' (minutes in)
  const step = k => T().steps.find(s => s.key === k);
  const shown = () => T().steps.filter(s => !S.aside.includes(s.key));
  const topStep = () => (S.tracked && step(S.tracked) && !S.aside.includes(S.tracked)) ? step(S.tracked) : shown().find(s => s.top) || shown()[0];
  const current = () => (S.task && !S.aside.includes(S.task) && step(S.task)) || topStep();

  // ---- shared pieces --------------------------------------------------------------------------------------------------
  const need = (icon, name, have, n) => `<div class="need ${have >= n ? 'met' : 'short'}"><div class="np">${img(icon)}</div><div class="c">${have}/${n}</div><div class="nm">${esc(name)}</div></div>`;
  const pips = (met, of) => `<span class="pips">${Array.from({ length: of }, (_, i) => `<i class="${i < met ? 'on' : ''}"></i>`).join('')}</span>`;
  const compass = (deg, big) => `<span class="compass${big ? ' big' : ''}"><i style="transform:rotate(${deg}deg)"></i></span>`;
  const recipe = m => `<span class="recipe"><span class="grid" style="grid-template-columns:repeat(${m.w},44px)">${m.cells.map(c => `<i>${img(c)}</i>`).join('')}</span><span class="arrow"></span></span>`;
  const hint = (keys, label, act) => `<span class="hint${act ? ' act' : ''}"${act ? ` data-act="${act}"` : ''}>${keys.map(k => `<kbd${k === 'Hold' ? ' class="hold"' : ''}>${k}</kbd>`).join('')}${esc(label)}</span>`;

  // ---- Tasks -----------------------------------------------------------------------------------------------------------
  function heroPic(s) {
    if (s.kind === 'make') return `<div class="roomline"><b>${esc(s.room)}</b></div>` + s.make.map((m, i) =>
      `<div class="made" style="left:${(s.make.length > 1 ? 60 : 220) + i * 330}px;width:300px"><div class="obj" style="gap:14px;align-items:center">${recipe(m)}${img(m.icon)}</div>
        <div class="nm">${esc(m.name)} <span class="${m.have >= m.need ? 'good' : 'warn'}">${m.have}/${m.need}</span></div></div>`).join('');
    if (s.kind === 'choose') {
      const sel = S.choice || S.chosen;
      return `<div class="floor"></div><div class="choices">` + s.choices.map(c =>
        `<button class="choice${sel === c.id ? ' sel' : ''}" data-act="choice:${c.id}" title="${esc(c.name)}"><div class="suit" style="background-image:url(${url('suit_' + c.id)})"></div>
          <div class="nm">${esc(c.name)}</div><div class="lab">${esc(c.role)} · ${c.armour} armour</div></button>`).join('') + `</div>`;
    }
    if (s.kind === 'pieces') return `<div class="floor"></div><div class="cone"></div><div style="position:absolute;left:0;right:0;top:36px;display:flex;justify-content:center;gap:26px">${s.bigs.map(b => `<img src="${url(b)}" alt="" style="width:128px;height:128px;filter:drop-shadow(0 12px 10px rgba(0,0,0,.6))">`).join('')}</div>
      <div class="lab" style="position:absolute;left:0;right:0;top:196px;text-align:center;font-size:16px">${esc(s.label)}</div>`;
    if (s.kind === 'go') return `<div class="floor"></div><div class="cone"></div>
      <div style="position:absolute;left:0;right:0;top:26px;display:flex;justify-content:center;align-items:center;gap:56px">${compass(s.place[1], true)}
        <div><div class="num" style="font-size:72px">${esc(s.place[0].split(' ')[0])}</div><div class="caps" style="margin-top:10px;font-size:16px">${esc(s.place[0].split(' ').slice(1).join(' '))}</div></div>
        <img src="${url(s.big)}" style="width:176px;height:176px;filter:drop-shadow(0 12px 12px rgba(0,0,0,.6))" alt=""></div>`;
    return `<div class="floor"></div><div class="cone"></div><img src="${url(s.big)}" alt="" style="position:absolute;left:50%;top:44px;margin-left:-96px;width:192px;height:192px;filter:${s.dark ? 'brightness(0) drop-shadow(0 0 14px rgba(127,195,255,.55))' : 'drop-shadow(0 14px 12px rgba(0,0,0,.6))'}">`
      ;
  }

  function tasks() {
    const top = topStep(), s = current();
    if (!s) return `<div class="hero"><div class="h2" style="margin-top:220px">Nothing to do this minute.</div><div class="why lab" style="margin-top:10px;font-size:16px">Look around. What you find brings the next step.</div></div>`;
    const isTop = s === top, tracked = S.tracked === s.key;
    const lane = D.lanes.find(l => l[0] === s.lane);
    const ch = s.kind === 'choose' ? s.choices.find(c => c.id === (S.choice || S.chosen)) : null;
    let row = '';
    if (ch) row = `<div class="needs">${ch.needs.map(n => need(n[0], n[1], n[2], n[3])).join('')}</div>`;
    else if (s.needs) row = `<div class="needs">${s.needs.map(n => need(n[0], n[1], n[2], n[3])).join('')}</div>`;
    else if (s.place && s.kind !== 'go') row = `<span class="place">${compass(s.place[1])}<span>${esc(s.place[0])}</span></span>`;
    const then = s.then && step(s.then) && !S.aside.includes(s.then) ? `<button class="then" data-act="task:${s.then}"><span class="caps">Then</span>${img(step(s.then).pics[0])}<span class="t">${esc(step(s.then).title)}</span></button>` : '';
    let primary = '';
    if (s.kind === 'choose') primary = (ch && S.chosen !== ch.id ? `<button class="btn primary" data-act="choose">Choose ${esc(ch.name)}</button>` : '') + `<button class="btn" data-act="hall:lineup">See them worn</button>`;
    if (s.link) primary = `<button class="btn primary" data-act="go:${s.link}">Open Status</button>`;
    const title = s.kind === 'choose' && S.chosen ? `Make your ${s.choices.find(c => c.id === S.chosen).name} armour` : s.title;
    const hero = `<div class="hero scan" data-k="${s.key}" data-kind="${s.kind}${s.needs ? '' : '-plain'}">
      <div class="eyebrow"><span class="caps ${isTop ? 'amber' : ''}">${tracked ? 'Tracked' : isTop ? 'Do this next' : s.want ? 'When you want' : 'Also open to you'}</span><span class="chip">${img(lane[2])}${esc(lane[1])}</span></div>
      <div class="title h2">${esc(title)}</div>
      <div class="why">${esc(s.why)}</div>
      <div class="pic">${heroPic(s)}</div>
      <div class="row">${row}</div>
      ${then ? `<div class="row" style="margin-top:14px">${then}</div>` : ''}
      <div class="actions">${primary}<button class="btn${tracked ? ' on' : ''}" data-act="track"><kbd>T</kbd>${tracked ? 'Tracked' : 'Track'}</button>
        ${s.top ? '' : `<button class="btn quiet" data-act="aside"><kbd>X</kbd>Set aside</button>`}</div>
    </div>`;
    const lanes = D.lanes.map(l => {
      const rows = shown().filter(x => x.lane === l[0]);
      if (!rows.length) return '';
      const more = T().later[l[0]] || 0;
      return `<div class="lane"><div class="who">${img(l[2])}<span class="caps">${esc(l[1])}</span></div>` + rows.map(x =>
        `<button class="tile${x === s ? ' sel' : ''}${x === top ? ' top' : ''}" data-act="task:${x.key}">
          <span class="pics">${x.pics.map(p => `<img src="${url(p)}"${x.dark ? ' style="filter:brightness(0) drop-shadow(0 0 4px rgba(127,195,255,.7))"' : ''} alt="">`).join('')}${x.of ? pips(x.met, x.of) : ''}</span>
          <span class="tt">${esc(x.kind === 'choose' && S.chosen ? 'Make your ' + x.choices.find(c => c.id === S.chosen).name + ' armour' : x.title)}</span>
          ${x.isNew && !S.seen[x.key] ? '<span class="new"></span>' : ''}${S.tracked === x.key ? '<span class="trk">◆</span>' : ''}</button>`).join('')
        + (more ? `<span class="tile more" title="${more} more, later">+${more}</span>` : '') + `</div>`;
    }).join('');
    return hero + `<div class="lanes">${lanes}</div><div class="foot">${esc(T().foot)}</div>`;
  }

  // ---- Status ----------------------------------------------------------------------------------------------------------
  function status() {
    const y = T().you || D.you, placed = Object.values(S.placed).reduce((a, b) => a + b, 0), left = y.points - placed;
    const st = y.stats[S.stat];
    const medals = Array.from({ length: 32 }, (_, i) => `<button class="medal ${i < y.truths.yours ? 'true' : i < y.truths.yours + 3 ? 'near' : ''}${S.medal === i ? ' sel' : ''}" data-act="medal:${i}" aria-label="Truth ${i + 1}"></button>`).join('');
    let panel;
    if (S.medal != null && S.medal >= y.truths.yours && S.medal < y.truths.yours + 3) {
      const t = y.truths.near[S.medal - y.truths.yours];
      panel = `<div class="caps amber">Closest to true</div><div class="h2" style="margin-top:10px">“${esc(t[0])}”</div><div class="line" style="margin-top:10px">${esc(t[1])}</div>
        <div class="bar"><i style="width:${t[2] * 100}%"></i></div><div class="lab" style="margin-top:14px">Gives</div><div>${esc(t[3])}</div>`;
    } else if (S.medal != null && S.medal < y.truths.yours) {
      panel = `<div class="caps good">True</div><div class="h2" style="margin-top:10px">“${S.medal ? 'I keep going.' : 'I am awake.'}”</div><div class="line" style="margin-top:10px">${S.medal ? 'You got up after your first death.' : 'You woke in the Bell Crypt.'}</div>`;
    } else if (S.medal != null) {
      panel = `<div class="caps">Not yet</div><div class="line" style="margin-top:10px">A Truth comes true by itself when you live it.</div>`;
    } else {
      panel = `<div class="caps">Lately</div><div class="lately">${y.lately.map(l => `<div class="it">${img(l[0])}<span class="t">${esc(l[1])}</span><span class="lab">${l[2]}</span></div>`).join('')}</div>
        <div class="caps" style="margin-top:22px">Deeds</div><div class="deeds">${y.deeds.map(d => `<span class="stat"><span class="h2">${d[0]}</span><span class="caps">${esc(d[1])}</span></span>`).join('')}</div>`;
    }
    return `<div class="figure"><div class="floor"></div><div class="cone" style="width:380px;margin-left:-190px;height:440px"></div>
        <div class="me" style="background-image:url(${url('suit_' + y.wearing)})"></div>
        <div class="call l" style="left:330px;top:120px;--w:50px"><b>Wildhide</b>10 armour</div>
        <div class="call l" style="left:330px;top:330px;--w:60px"><b>Haymaker</b>on Z</div></div>
      <div class="sheet">
        <div style="display:flex;gap:26px;align-items:flex-end"><span class="stat"><span class="num" style="font-size:72px">${y.level}</span><span class="caps">Level</span></span>
          <span class="stat"><span class="num" style="color:var(--rank-${y.rank.toLowerCase()})">${y.rank}</span><span class="caps">Rank</span></span>
          <span style="padding-bottom:22px"><span class="t">${esc(y.name)}</span><br><span class="lab">${esc(y.next || 'D-rank at level 10')}</span></span></div>
        <div class="xpbar">${Array.from({ length: 28 }, (_, i) => `<i class="${i < y.xp * 28 ? 'on' : ''}"></i>`).join('')}</div>
        <div class="caps" style="margin-top:22px">What comes next</div>
        <div class="road">${y.road.map(r => `<div class="stop">${img(r[2])}<span><span class="t">Level ${r[0]}</span><br><span class="lab">${esc(r[1])}</span></span></div>`).join('')}</div>
        <div style="display:flex;align-items:center;gap:14px;margin-top:26px"><span class="caps">Stats</span>${left > 0 ? `<span class="chip amber">${left} to place</span>` : ''}
          ${placed ? `<button class="btn primary hold" data-act="confirm" style="height:32px"><span class="fill"></span><kbd class="hold">Hold</kbd>Confirm</button><button class="btn quiet" data-act="unplace" style="height:32px"><kbd>⌫</kbd>Undo</button>` : ''}</div>
        <div class="stats" style="margin-top:8px">${y.stats.map((s, i) => `<button class="statcol${S.stat === i ? ' sel' : ''}" data-act="stat:${i}"><span class="v">${s[2] + (S.placed[i] || 0)}</span><span class="caps">${s[0]}</span>${S.placed[i] ? `<span class="add">+${S.placed[i]}</span>` : ''}${left > 0 ? `<span class="plus" data-act="plus:${i}"></span>` : ''}</button>`).join('')}</div>
        <div class="statline"><span class="t">${esc(st[1])}.</span> ${esc(st[3])}</div>
        <div class="caps" style="margin-top:14px">Truths · ${y.truths.yours} of 32</div><div class="medals">${medals}</div>
      </div>
      <div class="panel detail scan">${panel}</div>`;
  }

  // ---- Hearth ----------------------------------------------------------------------------------------------------------
  function hearth() {
    const o = T().home, h = o ? Object.assign({}, D.home, o, { rooms: D.home.rooms.map(x => { const c = x.slice(); c[3] = o.built.includes(c[0]) ? 'built' : c[0] === o.next ? 'next' : c[3] === 'unknown' ? 'unknown' : 'open'; c[5] = c[5].map(p => o.built.includes(c[0]) ? [p[0], p[1], p[3], p[3]] : [p[0], p[1], 0, p[3]]); return c; }) }) : D.home;
    const r = h.rooms.find(x => x[0] === S.room) || h.rooms[2];
    const state = { built: 'Built', next: 'Next for your home', started: 'Started', open: 'You can build this', unknown: 'Not found yet' };
    const st = x => x[3] === 'built' ? '✓' : (x[3] === 'started' || x[3] === 'next') && x[5].length ? x[5].filter(p => p[2] >= p[3]).length + '/' + x[5].length : '';
    const panel = r[3] === 'unknown'
      ? `<div class="caps">Not found yet</div><div class="big">${`<img src="${url(r[2])}" style="filter:brightness(0) drop-shadow(0 0 12px rgba(127,195,255,.5))" alt="">`}</div><div class="line">A room for something you haven’t brought home yet.</div>`
      : `<div class="caps ${r[3] === 'built' ? 'good' : r[3] === 'open' ? '' : 'amber'}">${state[r[3]]}</div><div class="h2" style="margin-top:10px">${esc(r[1])}</div>
        <div class="big">${img(r[2])}</div><div class="line">${esc(r[4])}</div>
        ${r[5].length ? `<div class="sec caps">What it takes</div><div class="needs" style="margin-top:12px">${r[5].map(p => need(p[0], p[1], p[2], p[3])).join('')}</div>` : ''}
        <div class="sec lab">In one closed room.</div>
        <div style="position:absolute;left:28px;bottom:0;display:flex;gap:12px"><button class="btn${S.tracked === 'room:' + r[0] ? ' on' : ''}" data-act="trackroom:${r[0]}"><kbd>T</kbd>${S.tracked === 'room:' + r[0] ? 'Tracked' : 'Track'}</button></div>`;
    return `<div class="homehead"><span class="stat"><span class="h2" style="font-size:32px">${esc(h.name)}</span><span class="caps" style="color:var(--lantern)">${esc(h.tier)} · ${esc(h.style)}</span></span>
        <span><span class="caps">Comfort ${h.comfort}/12</span><span class="pipsrow">${Array.from({ length: 12 }, (_, i) => `<i class="${i < h.comfort ? 'on' : ''}"></i>`).join('')}</span></span></div>
      <div class="rooms">${h.rooms.map(x => `<button class="room ${x[3]}${x === r ? ' sel' : ''}" data-act="room:${x[0]}" aria-label="${x[3] === 'unknown' ? 'A room not found yet' : esc(x[1])}">${img(x[2])}<div class="nm">${esc(x[1])}</div><span class="st">${st(x)}</span></button>`).join('')}</div>
      <div class="kin"><div class="caps">Beasts at home · ${h.kin.filter(k => k[1] !== 'dark').length} of ${h.nooks} nooks</div><div class="nooks">${h.kin.map(k => `<div class="nook dark" title="${esc(k[2])}">${img(k[0])}</div>`).join('')}${Array.from({ length: h.nooks - h.kin.length }, () => '<div class="nook">empty<br>nook</div>').join('')}</div></div>
      <div class="panel detail scan">${panel}</div>`;
  }

  // ---- Powers ----------------------------------------------------------------------------------------------------------
  function powers() {
    const p = D.powers, all = S.set === 'dense' ? D.allPowers.map((n, i) => [n, 1 + (i * 7) % 3, '', n.replace(/_/g, ' ').replace(/^./, c => c.toUpperCase()), 10 + (i * 3) % 30, (3 + i % 9) + ' s', '', (i * 37 % 100) / 100]) : null;
    const own = all ? p.own.concat(all.filter(a => !p.own.some(o => o[0] === a[0]))) : p.own, sel = own.find(o => o[0] === S.power) || own[0];
    return `<div style="position:absolute;left:0;top:0;width:1000px">
        <div style="display:flex;align-items:center;gap:16px"><span class="caps">On your keys</span><span class="energy"><i></i></span><span class="lab">${p.energy[0]} energy · ${p.energy[2]} a second</span></div>
        <div class="sockets">${S.keys.map((k, i) => `<button class="socket${i === 4 ? ' gap' : ''}${k[1] ? '' : ' fits'}" data-act="key:${i}"><span class="well">${k[1] ? img('p_' + k[1]) : ''}</span><kbd>${k[0]}</kbd></button>`).join('')}</div>
        <div class="caps" style="margin-top:26px">Your powers · ${own.length}</div>
        <div class="pgrid">${own.map(o => `<button class="pw${o[0] === sel[0] ? ' sel' : ''}" data-act="power:${o[0]}" aria-label="${esc(o[3])}">${img('p_' + o[0])}<span class="lv">${'<i></i>'.repeat(o[1])}</span>${S.keys.some(k => k[1] === o[0]) ? '<span class="on"></span>' : ''}</button>`).join('')}${all ? '' : '<span class="pw" style="opacity:.35"></span>'.repeat(8)}</div>
        <div class="caps" style="margin-top:26px">Your body</div>
        <div class="pgrid">${p.body.map(b => `<span class="pw" title="${esc(b[1])}">${img('p_' + b[0])}</span>`).join('')}</div>
        <button class="btn" data-act="hall:wall" style="margin-top:34px">${img('w_longsword')}<span>The Hall of Arms</span></button>
      </div>
      <div class="panel detail scan"><div class="caps">${esc(sel[2] || 'Power')} · level ${sel[1]}</div><div class="h2" style="margin-top:10px">${esc(sel[3])}</div>
        <div class="big">${`<img src="${url('p_' + sel[0])}" alt="">`}</div><div class="line">${esc(sel[6] || 'One line of what it does goes here.')}</div>
        <div class="nums"><span class="stat"><span class="h2">${sel[4]}</span><span class="caps">Energy</span></span><span class="stat"><span class="h2">${sel[5]}</span><span class="caps">Cooldown</span></span></div>
        <div class="sec caps">Mastery to level ${sel[1] + 1}</div><div class="bar"><i style="width:${sel[7] * 100}%"></i></div>
        <div class="sec amber">Click an empty key above to put it there.</div></div>`;
  }

  // ---- Journal ---------------------------------------------------------------------------------------------------------
  function journal() {
    const shelves = D.shelves.map(s => {
      const [id, name, , found, total, have, dark] = s;
      let body;
      if (id === 'bells') body = `<div class="bellrow">${Array.from({ length: 100 }, (_, i) => `<i class="${i === 6 ? '' : 'dark'}">${img('v_bell')}</i>`).join('')}</div>`;
      else if (id === 'story') body = `<div class="story">${D.story.map(e => `<div class="ev">${img(e[0])}<span class="t">${esc(e[1])}</span><span class="lab">${esc(e[2])}</span></div>`).join('')}</div>`;
      else {
        const wide = id === 'gates';
        body = `<div class="slots">${have.map(f => `<button class="slot found${wide ? ' wide' : ''}${S.find === f ? ' sel' : ''}" data-act="find:${f}" aria-label="${esc((D.finds[f] || [f])[0])}">${img(f)}</button>`).join('')}
          ${dark.map(f => `<span class="slot dark${wide ? ' wide' : ''}">${wide ? '' : img(f)}</span>`).join('')}</div>`;
      }
      return `<div class="shelf" id="shelf-${id}"><div class="hd"><span class="h2" style="font-size:16px;letter-spacing:2px;text-transform:uppercase">${esc(name)}</span><span class="lab">${total ? found + ' of ' + total : found + ' lines'}</span></div>${body}</div>`;
    }).join('');
    const f = D.finds[S.find];
    const panel = f ? `<div class="caps">${esc(f[1])}</div><div class="h2" style="margin-top:10px">${esc(f[0])}</div><div class="big">${S.find.startsWith('g_') ? img(S.find, 'paint') : img(S.find)}</div>
        <div class="line">${esc(f[2])}</div><div class="nums">${f[3].map(n => `<span class="stat"><span class="h2">${esc(n[1])}</span><span class="caps">${esc(n[0])}</span></span>`).join('')}</div>
        ${f[4] ? `<div class="sec caps">It leaves</div><div class="needs" style="margin-top:12px"><div class="need"><div class="np">${img(f[4][0])}</div><div class="nm">${esc(f[4][1])}</div></div></div>` : ''}`
      : `<div class="line">Point at anything you’ve found.</div>`;
    return `<div class="index">${D.shelves.map(s => `<button class="${S.shelf === s[0] ? 'on' : ''}" data-act="shelf:${s[0]}" aria-label="${esc(s[1])}">${img(s[2])}<span>${s[4] ? s[3] + '/' + s[4] : s[3]}</span></button>`).join('')}</div>
      <div class="wall"><div class="inner">${shelves}</div></div><div class="panel detail scan">${panel}</div>`;
  }

  function map() {
    return `<div style="position:absolute;inset:0;background:#0a1220 url(${url('map_scene', 'jpg')}) center/cover no-repeat;box-shadow:inset 0 0 0 1px rgba(127,195,255,.3)"></div>
      <div style="position:absolute;left:24px;bottom:24px;max-width:560px;padding:12px 16px;background:rgba(15,23,36,.9);box-shadow:0 0 0 1px rgba(127,195,255,.4)"><span class="caps amber">A real screenshot</span><br>The map in the world stays as it is. It takes the same hint bar and the same Track key.</div>`;
  }

  const VIEWS = { status, tasks, hearth, powers, journal, map };
  const SUBS = { status: '', tasks: '', hearth: '', powers: '', journal: 'What you’ve found', map: '' };

  function hints() {
    const h = [hint(['Esc'], S.place === 'tasks' && S.task && current() !== topStep() ? 'Back to the next thing' : 'Close', 'esc')];
    if (S.place === 'tasks') h.push(hint(['T'], 'Track', 'track'), hint(['Tab'], 'Next step', 'tab'));
    if (S.place === 'status') h.push(hint(['Space'], 'Place a point'), hint(['Hold', 'Enter'], 'Confirm'));
    if (S.place === 'hearth') h.push(hint(['T'], 'Track a room'), hint(['Tab'], 'Next room', 'tab'));
    if (S.place === 'powers') h.push(hint(['Z', 'X', 'C', 'V'], 'Put it on that key'));
    if (S.place === 'journal') h.push(hint(['Tab'], 'Next find', 'tab'));
    return h.join('') + `<span class="sp"></span>` + hint(['Q', 'E'], 'Place') + hint(['K'], 'Close', 'k');
  }

  function render() {
    const y = T().you || D.you, trackedStep = S.tracked && step(S.tracked);
    const placedAny = Object.keys(S.placed).length > 0;
    root.classList.toggle('closed', !S.open);
    root.innerHTML = `<img class="bg" src="${url(T().bg || 'bg_world', 'jpg')}" alt=""><div class="veil" style="${S.open ? '' : 'opacity:0'}"></div>
      <div class="you"><div class="lv">${y.level}</div><div class="caps" style="color:var(--rank-${y.rank.toLowerCase()})">${y.rank}-rank</div><div class="xp"><i style="width:${y.xp * 100}%"></i></div></div>
      <nav class="rail" aria-label="The band's places">${PLACES.map((p, i) => `<button class="node${S.place === p[0] ? ' on' : ''}" data-act="go:${p[0]}" aria-label="${p[1]}"><span class="key">${i + 1}</span><span class="ic">${svg(ICONS[p[0]])}</span><span>${p[1]}</span>
        ${p[0] === 'status' && y.points && !S.spent ? '<i class="glint amber"></i>' : ''}${p[0] === 'tasks' && shown().some(s => s.isNew && !S.seen[s.key]) ? '<i class="glint"></i>' : ''}</button>`).join('')}</nav>
      <main class="plate"><div class="head"><span class="h1">${PLACES.find(p => p[0] === S.place)[1].toUpperCase()}</span><span class="sub lab">${esc(SUBS[S.place])}</span>
        <span class="right">${S.place === 'hearth' ? '<span class="chip lantern">At your fire you can do more</span>' : ''}${trackedStep || (S.tracked || '').startsWith('room:') ? '<span class="chip amber">◆ Tracking</span>' : ''}</span></div>
        <div class="stage" data-place="${S.place}">${VIEWS[S.place]()}</div></main>
      <footer class="hints">${hints()}</footer>
      <div class="toast${S.toast ? ' show' : ''}">${S.toast ? `<span>${esc(S.toast.text)}</span>${S.toast.undo ? `<button class="btn" data-act="undo" style="height:32px"><kbd>Z</kbd>Undo</button>` : ''}` : ''}</div>
      ${!S.open ? `<div class="hudcard"><div class="caps edge">${trackedStep ? 'Tracked' : 'Next'}</div><div class="t" style="margin-top:4px">${esc(((x) => x.kind === 'choose' && S.chosen ? 'Make your ' + x.choices.find(c => c.id === S.chosen).name + ' armour' : x.title)(trackedStep || topStep() || { title: '' }))}</div></div>
        <div class="hints shut" style="left:28px"><span class="hint act" data-act="k"><kbd>K</kbd>Open the band</span><span class="sp"></span><span class="lab">The world, with the band shut. The card top right is the HUD’s.</span></div>` : ''}
      <div class="hallshot${S.hall ? ' show' : ''}" style="${S.hall ? `background-image:url(${url('hall_' + S.hall, 'jpg')})` : ''}"><span class="note">A real screenshot of the Hall of Arms, as it is today</span><div class="back" data-act="hallback" title="Back"></div>
        ${S.hall === 'lineup' ? [['wildhide', 9], ['bellguard', 29], ['rootwarden', 51], ['amberwing', 72]].map(c => `<div class="hot" data-act="pick:${c[0]}" style="left:${c[1]}%;width:20%" title="Choose this one"></div>`).join('') : ''}</div>`;
    if (S.place === 'journal') scrollShelf(false);
  }

  function scrollShelf() {
    const el = root.querySelector('#shelf-' + S.shelf), inner = root.querySelector('.wall .inner'), wall = root.querySelector('.wall');
    if (!el || !inner) return;
    const max = Math.max(0, inner.scrollHeight - wall.clientHeight);
    inner.style.transform = `translateY(${-Math.min(el.offsetTop, max)}px)`;
  }

  function toast(text, undo) {
    S.toast = { text, undo };
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => { S.toast = null; S.undo = undo ? null : S.undo; render(); }, undo ? 7000 : 2600);
  }

  function act(a) {
    const [name, arg] = a.split(/:(.*)/);
    switch (name) {
      case 'go': S.place = arg; S.task = arg === 'tasks' ? S.task : S.task; break;
      case 'task': S.task = arg; S.choice = null; S.seen[arg] = true; break;
      case 'choice': S.choice = arg; break;
      case 'choose': S.chosen = S.choice; S.choice = null; toast('Chosen. The step now lists what it takes.'); break;
      case 'pick': S.chosen = arg; S.choice = null; S.hall = null; toast('Chosen. The step now lists what it takes.'); break;
      case 'track': {
        if (S.place !== 'tasks') break;
        const s = current(); if (!s) break;
        S.tracked = S.tracked === s.key ? null : s.key;
        toast(S.tracked ? 'Tracked. It stays on your screen and points the way.' : 'No longer tracked.');
        break;
      }
      case 'trackroom': S.tracked = S.tracked === 'room:' + arg ? null : 'room:' + arg; toast(S.tracked ? 'Tracked. It stays on your screen.' : 'No longer tracked.'); break;
      case 'aside': {
        const s = current(); if (!s || s.top) break;
        S.aside.push(s.key); S.undo = s.key; if (S.tracked === s.key) S.tracked = null; S.task = null;
        toast('Set aside: ' + s.title, true);
        break;
      }
      case 'undo': if (S.undo) { S.aside = S.aside.filter(k => k !== S.undo); S.task = S.undo; S.undo = null; S.toast = null; } break;
      case 'tab': tab(1); break;
      case 'esc': back(); break;
      case 'k': S.open = !S.open; break;
      case 'stat': S.stat = +arg; break;
      case 'plus': { const left = (T().you || D.you).points - Object.values(S.placed).reduce((x, y) => x + y, 0); if (left > 0) { S.placed[arg] = (S.placed[arg] || 0) + 1; S.stat = +arg; } break; }
      case 'unplace': { const ks = Object.keys(S.placed); const k = ks[ks.length - 1]; if (k != null) { if (--S.placed[k] <= 0) delete S.placed[k]; } break; }
      case 'confirm': { const y = T().you || D.you; Object.entries(S.placed).forEach(([i, n]) => { y.stats[i][2] += n; y.points -= n; }); S.placed = {}; S.spent = y.points <= 0; } toast('Placed.'); break;
      case 'medal': S.medal = S.medal === +arg ? null : +arg; break;
      case 'room': S.room = arg; break;
      case 'power': S.power = arg; break;
      case 'key': { const i = +arg; S.keys.forEach(k => { if (k[1] === S.power) k[1] = ''; }); S.keys[i][1] = S.power; break; }
      case 'find': S.find = arg; break;
      case 'shelf': S.shelf = arg; render(); return;
      case 'hall': S.hall = arg; break;
      case 'hallback': S.hall = null; break;
    }
    render();
  }

  function back() {
    if (S.hall) { S.hall = null; return; }
    if (!S.open) return;
    if (S.place === 'tasks' && S.task && current() !== topStep()) { S.task = null; S.choice = null; return; }
    if (S.place === 'tasks' && S.choice) { S.choice = null; return; }
    if (S.place === 'status' && S.medal != null) { S.medal = null; return; }
    S.open = false;
  }

  function tab(d) {
    if (S.place === 'tasks') { const l = shown(); const i = l.indexOf(current()); const n = l[(i + d + l.length) % l.length]; S.task = n.key; S.choice = null; S.seen[n.key] = true; }
    if (S.place === 'hearth') { const l = D.home.rooms; const i = l.findIndex(r => r[0] === S.room); S.room = l[(i + d + l.length) % l.length][0]; }
    if (S.place === 'journal') { const l = Object.keys(D.finds); const i = l.indexOf(S.find); S.find = l[(i + d + l.length) % l.length]; }
    if (S.place === 'status') S.stat = (S.stat + d + 6) % 6;
  }

  function key(e) {
    const k = e.key;
    if (k === 'Tab') { e.preventDefault(); tab(e.shiftKey ? -1 : 1); return render(); }
    if (k === 'Escape') { e.preventDefault(); back(); return render(); }
    if (k === 'k' || k === 'K') { S.open = !S.open; S.hall = null; return render(); }
    if (!S.open || S.hall) return;
    if (k >= '1' && k <= '6') { S.place = PLACES[+k - 1][0]; return render(); }
    if (k === 'q' || k === 'Q' || k === 'e' || k === 'E') { const i = PLACES.findIndex(p => p[0] === S.place); S.place = PLACES[(i + (k.toLowerCase() === 'q' ? 5 : 1)) % 6][0]; return render(); }
    if (k === 't' || k === 'T') return act(S.place === 'hearth' ? 'trackroom:' + S.room : 'track');
    if ((k === 'x' || k === 'X') && S.place === 'tasks') return act('aside');
    if ((k === 'z' || k === 'Z') && S.undo) return act('undo');
    if (S.place === 'status' && k === ' ') { e.preventDefault(); return act('plus:' + S.stat); }
    if (S.place === 'status' && k === 'Enter') return act('confirm');
    if (S.place === 'status' && k === 'Backspace') return act('unplace');
    if (S.place === 'powers' && 'zxcv'.includes(k.toLowerCase())) return act('key:' + 'zxcv'.indexOf(k.toLowerCase()));
  }

  window.AW_PROTO = {
    mount(el, opts) {
      root = el; root.classList.add('aw'); root.tabIndex = 0;
      if (opts && opts.still) root.classList.add('still');
      if (opts && opts.hc) root.classList.add('hc');
      Object.assign(S, opts || {});
      root.addEventListener('click', e => { const t = e.target.closest('[data-act]'); if (t) { e.stopPropagation(); act(t.dataset.act); } root.focus({ preventScroll: true }); });
      root.addEventListener('keydown', key);
      render();
    },
    set(o) { Object.assign(S, o); render(); },
    state: S,
  };
})();
