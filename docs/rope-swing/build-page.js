// Builds docs/rope-swing/plan.html (the approval page) from plan.src.html, the sim and the runs it plotted.
// `node docs/rope-swing/proof/run.js && node docs/rope-swing/build-page.js`
const fs = require('fs'), path = require('path');
const here = __dirname;
const src = fs.readFileSync(path.join(here, 'plan.src.html'), 'utf8');
const swing = fs.readFileSync(path.join(here, 'proof', 'swing.js'), 'utf8');
const runs = fs.readFileSync(path.join(here, 'proof', 'runs.json'), 'utf8');
const out = src.replace('/*@SWING@*/', () => swing).replace('/*@RUNS@*/', () => runs.trim());
fs.writeFileSync(path.join(here, 'plan.html'), out);
console.log('plan.html', out.length, 'bytes');
