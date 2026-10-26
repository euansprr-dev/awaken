// node --experimental-websocket cdp-shoot.mjs <outdir> <scene[:w:h]> ...
// Starts headless Chrome once, renders each scene at device scale 2, waits for document.title === 'ready'.
import { spawn } from 'node:child_process';
import { writeFileSync, mkdirSync } from 'node:fs';

const CHROME = '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const [outdir, ...scenes] = process.argv.slice(2);
mkdirSync(outdir, { recursive: true });
const port = 9333;
const chrome = spawn(CHROME, ['--headless=new', '--disable-gpu', '--hide-scrollbars', `--remote-debugging-port=${port}`,
  '--user-data-dir=/tmp/k-menu-mock-chrome', 'about:blank'], { stdio: 'ignore' });
const sleep = (ms) => new Promise(r => setTimeout(r, ms));

let info;
for (let i = 0; i < 50 && !info; i++) {
  await sleep(200);
  try { info = await (await fetch(`http://127.0.0.1:${port}/json/list`)).json(); } catch { }
}
const page = info.find(t => t.type === 'page');
const ws = new WebSocket(page.webSocketDebuggerUrl);
await new Promise(r => ws.addEventListener('open', r));
let id = 0;
const pending = new Map();
ws.addEventListener('message', (e) => {
  const m = JSON.parse(e.data);
  if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id); }
});
const send = (method, params = {}) => new Promise(r => { const i = ++id; pending.set(i, r); ws.send(JSON.stringify({ id: i, method, params })); });

await send('Page.enable');
await send('Runtime.enable');
await send('Network.enable');
await send('Network.setCacheDisabled', { cacheDisabled: true });
for (const spec of scenes) {
  const [scene, w = '1512', h = '949'] = spec.split(':');
  await send('Emulation.setDeviceMetricsOverride', { width: +w, height: +h, deviceScaleFactor: 2, mobile: false });
  await send('Page.navigate', { url: `http://127.0.0.1:8765/index.html?scene=${scene}&t=${Date.now()}` });
  let title = '';
  for (let i = 0; i < 300 && title !== 'ready'; i++) {
    await sleep(100);
    const r = await send('Runtime.evaluate', { expression: 'document.title', returnByValue: true });
    title = r.result?.result?.value;
  }
  const err = await send('Runtime.evaluate', { expression: "document.querySelector('pre') ? document.querySelector('pre').textContent : ''", returnByValue: true });
  if (err.result?.result?.value) console.log(scene, 'ERROR', err.result.result.value);
  await sleep(150);
  const shot = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false });
  writeFileSync(`${outdir}/${scene}.png`, Buffer.from(shot.result.data, 'base64'));
  console.log(scene, title === 'ready' ? 'ok' : 'TIMEOUT');
}
ws.close();
chrome.kill();
process.exit(0);
