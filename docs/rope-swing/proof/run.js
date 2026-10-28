// The proof's numbers: each scene, 40 runs per model from different spots, looks and speeds (seeded), 15 s each.
// Today's bot looks 10-35 degrees up (on what's ahead, or at the sky); the assisted bot the same. Prints medians and
// writes runs.json (the table, and one typical run per scene and model for the page's plots).
const S = require('./swing.js');
let seed = 1;
const rnd = () => { seed = (seed * 1664525 + 1013904223) >>> 0; return seed / 4294967296; };
const med = (a) => { const b = a.slice().sort((p, q) => p - q); return b[Math.floor(b.length / 2)]; };
const out = {table: [], plots: {}};
for (const sc of Object.keys(S.SCENES)) {
  for (const m of ['today', 'assist']) {
    seed = 99;
    const runs = [];
    for (let i = 0; i < 40; i++) {
      const opts = {x0: (rnd() - 0.5) * 8, z0: rnd() * 6, pitch: -(10 + rnd() * 25), speed: 0.85 + rnd() * 0.4, ticks: 300};
      const r = S.run(sc, m, opts);
      r.opts = opts;
      runs.push(r);
    }
    const dist = runs.map(r => r.distance), sm = runs.map(r => r.bonks), worst = runs.map(r => r.worst);
    const smashRuns = runs.filter(r => r.worst >= 1.0).length, stuck = runs.filter(r => r.stuck).length;
    const ropes = runs.reduce((a, r) => a + r.swings, 0), win = runs.reduce((a, r) => a + r.perfect, 0);
    const row = {scene: sc, name: S.SCENES[sc].name, model: m, medDist: med(dist), p10: dist.slice().sort((a, b) => a - b)[4],
      smashesPerMin: +(sm.reduce((a, c) => a + c, 0) / runs.reduce((a, r) => a + r.ticks / 1200, 0)).toFixed(1),
      hardSmashRuns: smashRuns, stuckRuns: stuck, worstMed: med(worst), ropesPerRun: +(ropes / runs.length).toFixed(1),
      windowShare: ropes ? +(win / ropes).toFixed(2) : 0, runs: runs.length};
    out.table.push(row);
    // the typical run: nearest the median distance
    const typical = runs.slice().sort((a, b) => Math.abs(a.distance - row.medDist) - Math.abs(b.distance - row.medDist))[0];
    out.plots[sc + ':' + m] = {path: typical.path.filter((_, i) => i % 2 === 0), ropes: typical.ropes, events: typical.events,
      boxes: typical.boxes, distance: typical.distance, bonks: typical.bonks, worst: typical.worst, opts: typical.opts};
    console.log(`${sc.padEnd(7)} ${m.padEnd(7)} median ${String(row.medDist).padStart(6)} (p10 ${String(row.p10).padStart(6)})  smashes/min ${String(row.smashesPerMin).padStart(4)}  runs with a hard smash ${String(smashRuns).padStart(2)}/40  stopped dead ${String(stuck).padStart(2)}/40  ropes/run ${row.ropesPerRun}  in window ${row.windowShare}`);
  }
}
require('fs').writeFileSync(__dirname + '/runs.json', JSON.stringify(out));
