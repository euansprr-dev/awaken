// "The map in the world" (Euan, 2026-10-26): the camera pulls back over your character and the band projects the
// Atlas in front of you as a hologram. Composited over real third-person tour frames (k-menu-map.txt).

// a full-frame canvas over the stage, in frame pixels
function frameCanvas(stage, w, h) {
  const c = document.createElement('canvas');
  c.width = w; c.height = h;
  c.style.cssText = `position:absolute;left:0;top:0;width:${w / 2}px;height:${h / 2}px`;
  stage.appendChild(c);
  return c.getContext('2d');
}

// the projected map: an ellipse cut from the Atlas picture, its rim fading into a ring of light, on a dark plate
function holoDisc(g, img, o) {
  const { cx, cy, rx, ry, src, reveal = 1 } = o;
  // the world under the projection darkens: a soft pool, so the land never sits on raw grass
  g.save();
  g.translate(cx, cy + ry * 0.10); g.scale(1, ry / rx);
  let gr = g.createRadialGradient(0, 0, rx * 0.2, 0, 0, rx * 1.35);
  gr.addColorStop(0, 'rgba(4,8,16,0.62)'); gr.addColorStop(0.7, 'rgba(4,8,16,0.45)'); gr.addColorStop(1, 'rgba(4,8,16,0)');
  g.fillStyle = gr; g.beginPath(); g.arc(0, 0, rx * 1.35, 0, Math.PI * 2); g.fill();
  g.restore();
  // the plate: dark glass with an emissive rim, a little below the land
  g.save();
  g.translate(cx, cy + ry * 0.06); g.scale(1, ry / rx);
  gr = g.createRadialGradient(0, 0, 0, 0, 0, rx * 1.04);
  gr.addColorStop(0, 'rgba(10,18,32,0.80)'); gr.addColorStop(0.92, 'rgba(14,26,46,0.78)'); gr.addColorStop(1, 'rgba(127,195,255,0.0)');
  g.fillStyle = gr; g.beginPath(); g.arc(0, 0, rx * 1.04, 0, Math.PI * 2); g.fill();
  g.restore();
  // the land and its clouds, cut to the ellipse with a feathered rim
  const t = document.createElement('canvas'); t.width = Math.ceil(rx * 2); t.height = Math.ceil(ry * 2);
  const tg = t.getContext('2d');
  tg.drawImage(img, src[0], src[1], src[2], src[3], 0, 0, t.width, t.height);
  // a faint cool cast and scan lines: the same light as the band's glass, not a filter over the picture
  tg.globalCompositeOperation = 'source-atop';
  tg.fillStyle = 'rgba(110,170,255,0.06)'; tg.fillRect(0, 0, t.width, t.height);
  for (let y = 0; y < t.height; y += 6) { tg.fillStyle = 'rgba(160,210,255,0.035)'; tg.fillRect(0, y, t.width, 1); }
  tg.globalCompositeOperation = 'destination-in';
  tg.save(); tg.translate(rx, ry); tg.scale(1, ry / rx);
  const m = tg.createRadialGradient(0, 0, rx * 0.80 * reveal, 0, 0, rx * reveal);
  m.addColorStop(0, 'rgba(0,0,0,1)'); m.addColorStop(1, 'rgba(0,0,0,0)');
  tg.fillStyle = m; tg.beginPath(); tg.arc(0, 0, rx, 0, Math.PI * 2); tg.fill(); tg.restore();
  g.drawImage(t, cx - rx, cy - ry);
  // the rim: a ring of the band's light, brighter on the near side
  g.save();
  g.translate(cx, cy + ry * 0.06); g.scale(1, ry / rx);
  g.globalCompositeOperation = 'lighter';
  g.filter = 'blur(6px)';
  g.strokeStyle = 'rgba(127,195,255,0.55)'; g.lineWidth = 10;
  g.beginPath(); g.arc(0, 0, rx * 1.0 * reveal, 0, Math.PI * 2); g.stroke();
  g.filter = 'none';
  g.strokeStyle = 'rgba(196,230,255,0.85)'; g.lineWidth = 2.5;
  g.beginPath(); g.arc(0, 0, rx * 1.0 * reveal, 0.05 * Math.PI, 0.95 * Math.PI); g.stroke();
  g.strokeStyle = 'rgba(196,230,255,0.35)'; g.lineWidth = 2;
  g.beginPath(); g.arc(0, 0, rx * 1.0 * reveal, 1.05 * Math.PI, 1.95 * Math.PI); g.stroke();
  // tick marks round the rim: north and the compass quarters
  g.strokeStyle = 'rgba(196,230,255,0.7)'; g.lineWidth = 3;
  for (let i = 0; i < 36; i++) {
    const a = i / 36 * Math.PI * 2, r0 = rx * 1.015, r1 = rx * (i % 9 === 0 ? 1.07 : 1.04);
    g.beginPath(); g.moveTo(Math.cos(a) * r0, Math.sin(a) * r0); g.lineTo(Math.cos(a) * r1, Math.sin(a) * r1); g.stroke();
  }
  g.restore();
}

// the beams from the band's diamond to the disc's near rim
function bandBeams(g, from, cx, cy, rx, ry, alpha = 0.5) {
  g.save();
  g.globalCompositeOperation = 'lighter';
  for (const [a0, w] of [[0.62, 1], [0.38, 1], [0.5, 0.6]]) {
    const ang = a0 * Math.PI, x = cx + Math.cos(ang) * rx, y = cy + ry * 0.06 + Math.sin(ang) * ry;
    const gr = g.createLinearGradient(from[0], from[1], x, y);
    gr.addColorStop(0, `rgba(196,230,255,${alpha * w})`); gr.addColorStop(1, `rgba(127,195,255,${alpha * 0.25 * w})`);
    g.strokeStyle = gr;
    g.filter = 'blur(5px)'; g.lineWidth = 14 * w; g.beginPath(); g.moveTo(from[0], from[1]); g.lineTo(x, y); g.stroke();
    g.filter = 'none'; g.lineWidth = 2.5 * w; g.beginPath(); g.moveTo(from[0], from[1]); g.lineTo(x, y); g.stroke();
  }
  // a soft cone of projected light
  const gr2 = g.createRadialGradient(from[0], from[1], 0, from[0], from[1], 70);
  gr2.addColorStop(0, `rgba(196,230,255,${alpha * 1.2})`); gr2.addColorStop(1, 'rgba(127,195,255,0)');
  g.fillStyle = gr2; g.beginPath(); g.arc(from[0], from[1], 70, 0, Math.PI * 2); g.fill();
  g.restore();
}

// a gate's pillar of light rising out of the map, with its plate hung at the top (a world plate, text as light)
function mapPillar(stage, g, x, y, h, colour, plate) {
  g.save();
  g.globalCompositeOperation = 'lighter';
  g.filter = 'blur(7px)'; g.fillStyle = rgba(colour, 0.9); g.fillRect(x - 5, y - h, 10, h);
  g.filter = 'none';
  const gr = g.createLinearGradient(0, y - h, 0, y);
  gr.addColorStop(0, rgba(0xFFFFFF, 0.2)); gr.addColorStop(1, rgba(colour, 1));
  g.fillStyle = gr; g.fillRect(x - 2, y - h, 4, h);
  const b = g.createRadialGradient(x, y, 0, x, y, 26); b.addColorStop(0, rgba(colour, 1)); b.addColorStop(1, rgba(colour, 0));
  g.fillStyle = b; g.beginPath(); g.ellipse(x, y, 26, 11, 0, 0, Math.PI * 2); g.fill();
  g.restore();
  if (plate) {
    const p = new Panel(plate.w, plate.h, plate.ppu || 4, 8);
    p.material({ tint: 0.5, halo: 0.35 });
    p.fill(0, 0, plate.w, plate.h, C.NAVY, 0.5);
    p.fill(0, 0, 2, plate.h, colour, 1);
    plate.draw(p);
    p.frame({ bracket: 6 });
    const el = p.canvas, m = p.m * p.ppu;
    const px = plate.left ? x - 10 - plate.w * p.ppu : x + 10;
    el.style.cssText = `position:absolute;left:${(px - m) / 2}px;top:${(y - h - plate.h * p.ppu / 2 - m) / 2}px;width:${el.width / 2}px;height:${el.height / 2}px`;
    stage.appendChild(el);
  }
}

// the world dims while you read the map (Lens follows UiHooks.focus; without Lens, a dim pass)
function sceneDim(g, w, h, a = 0.34) {
  g.save();
  g.fillStyle = `rgba(4,8,14,${a})`; g.fillRect(0, 0, w, h);
  const v = g.createRadialGradient(w * 0.5, h * 0.45, h * 0.3, w * 0.5, h * 0.5, h * 0.95);
  v.addColorStop(0, 'rgba(0,0,0,0)'); v.addColorStop(1, 'rgba(0,0,0,0.38)');
  g.fillStyle = v; g.fillRect(0, 0, w, h);
  g.restore();
}

// the legend as a panel of the band, beside the map: the rail on its wrist edge, the gates nearest first
function legendPanel(d) {
  const W = 176, H = 150;
  const p = new Panel(W, H, 5, 24);
  p.material({ wristLight: true, tint: 0.5 });
  // a narrow rail: the band's pages, Map lit
  p.gradH(0, 0, 24, H, C.NAVY, 0.34, C.NAVY, 0.16);
  p.vline(24, 4, H - 4, C.SIGNAL, 0.3);
  PAGES.forEach(([key], i) => {
    const y0 = 6 + i * 23, on = key === 'map';
    if (on) { p.fill(2, y0, 21, 21, C.SIGNAL, 0.16); p.fill(0, y0 + 1, 2, 19, C.SIGNAL_HOT, 0.95); }
    iconPix(p, ICON[key], 6, y0 + 4, on ? C.SIGNAL_HOT : C.INK_DIM, on ? 1 : 0.75);
    if (d.badges && d.badges[key]) { p.fill(16, y0 + 1, 6, 6, d.badges[key], 0.95); }
  });
  const x0 = 31, x1 = W - 7;
  micro(p, 'MAP', x0, 7, { c: C.SIGNAL_HOT });
  p.text('The Commons', x0 + 18, 5.5, { c: C.INK_HI, glow: 0.25 });
  let tx = x0;
  ['GATES · 4', 'LANDS', 'SAVED · 3'].forEach((t, i) => {
    const w = microW(p, t) + 6, on = i === 0;
    p.fill(tx, 17, w, 9, C.SIGNAL, on ? 0.22 : 0.06); p.outline(tx, 17, w, 9, C.SIGNAL, on ? 0.9 : 0.3);
    micro(p, t, tx + 3, 19.2, { c: on ? C.INK_HI : C.INK_DIM });
    tx += w + 3;
  });
  let y = 31;
  for (const g of d.gates) {
    const c = g.broken ? C.WARN : C.RANK[g.rank], hot = g.hot;
    if (hot) { p.fill(x0 - 3, y - 2, x1 - x0 + 5, 41, C.SIGNAL, 0.14); p.fill(x0 - 3, y - 2, 2, 41, C.SIGNAL_HOT, 1); }
    const chip = g.broken ? '!' : g.rank;
    p.fill(x0, y, 9, 9, c, 0.18); p.outline(x0, y, 9, 9, c, 0.9);
    p.text(chip, x0 + (9 - Font.width(chip)) / 2 + 0.5, y + 1, { c });
    p.text(p.ellipsize(g.name, 80), x0 + 12, y, { c: hot ? C.INK_HI : C.INK });
    small(p, g.where, x1, y + 0.5, { c: C.INK_DIM, align: 'right' });
    small(p, g.when, x0 + 12, y + 9, { c: g.whenC || C.INK_DIM });
    if (hot) {
      small(p, g.more, x0 + 12, y + 17.5, { c: C.INK_DIM });
      button(p, 'Point me there', x0 + 12, y + 25, { key: 'T', hot: true });
    }
    y += hot ? 43 : 20;
  }
  p.frame({ bracket: 12 });
  return p;
}

// the key line under the scene: what the mouse and keys do, in the player's bindings
function sceneKeys(d) {
  const p = new Panel(462, 14, 5, 6);
  p.fill(0, 0, 462, 14, C.NAVY, 0.55);
  let fx = 4;
  for (const [k, t] of d) {
    fx += keycap(p, k, fx, 2.5) + 3;
    fx += small(p, t, fx, 3.5, { c: C.INK }) + 8;
  }
  p.outline(0, 0, 462, 14, C.SIGNAL, 0.35);
  return p;
}
