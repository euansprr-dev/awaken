#!/usr/bin/env python3
"""The CC0 shortlist page (world sound, docs/sound/PLAN.md §6.5 and §11 Phase 0): Euan ticks each candidate yes or no
before anything is downloaded.

    python3 -I tools/art/sound_shortlist.py      docs/sound/cc0-shortlist.json -> docs/sound/cc0-shortlist.html

Each candidate links to its Freesound page (listen there); marks and notes stay in the browser (localStorage) and
export as JSON for the session that downloads the yeses. Self-contained page, light and dark, phone width.
"""
import html
import json
import os

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "../.."))
SRC = os.path.join(ROOT, "docs/sound/cc0-shortlist.json")
OUT = os.path.join(ROOT, "docs/sound/cc0-shortlist.html")

PAGE = """<title>Sound Shortlist</title>
<meta name="claude:viewport-fit-sides" content="cover">
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Chakra+Petch:wght@600;700&family=IBM+Plex+Sans:wght@400;500;600&family=IBM+Plex+Mono:wght@400;500&family=Silkscreen&display=swap">
<style>
/* Layout: one column of night glass; one row per recording with its two choices on the right (stacked on phones). */
:root {
  --bg: #0a1019; --glass: #0f1724; --glass-2: #142033; --line: #22344d;
  --fg: #e4f1ff; --muted: #8ea5bf; --accent: #7fc3ff; --yes: #8fdc9a; --no: #ff8a7a; --chip-ink: #0a1019;
  --display: "Chakra Petch", "Avenir Next Condensed", "Arial Narrow", sans-serif;
  --body: "IBM Plex Sans", "Helvetica Neue", Arial, sans-serif;
  --mono: "IBM Plex Mono", ui-monospace, Menlo, monospace;
  --pixel: "Silkscreen", "IBM Plex Mono", ui-monospace, monospace;
  color-scheme: dark;
}
@media (prefers-color-scheme: light) {
  :root:not([data-theme="dark"]) {
    --bg: #eef3f8; --glass: #ffffff; --glass-2: #f4f8fc; --line: #cfdbe8;
    --fg: #0e1a2b; --muted: #4b5f76; --accent: #1f6fb5; --yes: #23884f; --no: #b8402f; --chip-ink: #ffffff;
    color-scheme: light;
  }
}
:root[data-theme="light"] {
  --bg: #eef3f8; --glass: #ffffff; --glass-2: #f4f8fc; --line: #cfdbe8;
  --fg: #0e1a2b; --muted: #4b5f76; --accent: #1f6fb5; --yes: #23884f; --no: #b8402f; --chip-ink: #ffffff;
  color-scheme: light;
}
* { box-sizing: border-box; }
body { background: var(--bg); color: var(--fg); font: 400 15px/1.55 var(--body);
  padding-left: max(16px, env(safe-area-inset-left, 0px)); padding-right: max(16px, env(safe-area-inset-right, 0px));
  padding-block: 0 96px; }
.wrap { max-width: 960px; margin: 0 auto; display: grid; gap: 36px; }
h1, h2 { font-family: var(--display); margin: 0; line-height: 1.15; text-wrap: balance; }
h1 { font-size: clamp(2.2rem, 6vw, 3.4rem); }
h2 { font-size: 1.4rem; }
p { margin: 0; }
a { color: var(--accent); }
.eyebrow { font-family: var(--pixel); font-size: 0.7rem; letter-spacing: 0.12em; text-transform: uppercase; color: var(--accent); }
.muted { color: var(--muted); }
header.top { padding-top: 48px; display: grid; gap: 12px; max-width: 68ch; }
section { display: grid; gap: 12px; }
section > header { display: grid; gap: 4px; }
.list { border-top: 1px solid var(--line); }
.item { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 8px 16px; padding: 12px 0; border-bottom: 1px solid var(--line); }
.item .name { font-weight: 600; overflow-wrap: anywhere; }
.item .meta { font-family: var(--mono); font-size: 0.78rem; color: var(--muted); }
.item .why { font-size: 0.92rem; }
.item .note { font-size: 0.85rem; color: var(--muted); font-style: italic; }
.item textarea { grid-column: 1 / -1; width: 100%; min-height: 2.2em; resize: vertical; font: inherit; font-size: 0.85rem;
  background: var(--glass); color: var(--fg); border: 1px solid var(--line); padding: 6px 8px; }
.marks { display: flex; gap: 6px; align-items: start; }
.marks button { font-family: var(--pixel); font-size: 0.68rem; letter-spacing: 0.08em; text-transform: uppercase;
  padding: 8px 12px; border: 1px solid var(--line); background: var(--glass); color: var(--fg); cursor: pointer; min-width: 64px; }
.marks button[aria-pressed="true"].yes { background: var(--yes); color: var(--chip-ink); border-color: var(--yes); }
.marks button[aria-pressed="true"].no { background: var(--no); color: var(--chip-ink); border-color: var(--no); }
button:focus-visible, a:focus-visible, textarea:focus-visible { outline: 2px solid var(--accent); outline-offset: 2px; }
.bar { position: sticky; top: env(safe-area-inset-top, 0px); z-index: 5; background: var(--bg); border-bottom: 1px solid var(--line);
  display: flex; flex-wrap: wrap; gap: 8px 16px; align-items: center; padding-block: 10px; }
body::before { content: ""; position: fixed; inset: 0 0 auto; height: env(safe-area-inset-top, 0px); z-index: 10; background: var(--bg); }
.bar .count { font-family: var(--mono); font-size: 0.85rem; }
.bar button { font: 600 0.85rem var(--body); padding: 7px 12px; border: 1px solid var(--accent); background: transparent; color: var(--accent); cursor: pointer; }
#out { width: 100%; min-height: 10em; font: 0.8rem var(--mono); background: var(--glass); color: var(--fg); border: 1px solid var(--line); padding: 8px; }
@media (max-width: 560px) { .item { grid-template-columns: 1fr; } }
</style>
<div class="wrap">
<header class="top">
  <p class="eyebrow">AWAKEN · world sound · CC0 shortlist · checked __CHECKED__</p>
  <h1>Sound Shortlist</h1>
  <p>Recordings for what vanilla Minecraft has none of: birds, insects, water, rain on different surfaces. Every one is
    <b>CC0</b> (public domain), checked on its own Freesound page. Nothing is downloaded until you say yes.</p>
  <p class="muted">Open a link to listen on Freesound, then mark it. Unmarked means no. Your marks stay in this browser;
    press Export and hand the text back to the session.</p>
</header>
<div class="bar"><span class="count" id="count"></span><button type="button" id="export">Export</button><button type="button" id="copy">Copy</button><button type="button" id="allyes">Mark all unmarked yes</button></div>
__GROUPS__
<section><header><h2>Export</h2><p class="muted">Paste this back into the session.</p></header><textarea id="out" readonly></textarea></section>
</div>
<script>
const KEY = "awaken-cc0-shortlist-__CHECKED__";
let state = {};
try { state = JSON.parse(localStorage.getItem(KEY) || "{}"); } catch (e) { state = {}; }
function save() { try { localStorage.setItem(KEY, JSON.stringify(state)); } catch (e) {} }
function render() {
  let y = 0, n = 0, total = 0;
  document.querySelectorAll(".item").forEach(it => {
    total++;
    const s = state[it.dataset.id] || {};
    it.querySelectorAll(".marks button").forEach(b => b.setAttribute("aria-pressed", String(s.mark === b.dataset.mark)));
    const ta = it.querySelector("textarea");
    if (document.activeElement !== ta) ta.value = s.note || "";
    if (s.mark === "yes") y++; else if (s.mark === "no") n++;
  });
  document.getElementById("count").textContent = y + " yes · " + n + " no · " + (total - y - n) + " unmarked of " + total;
}
document.querySelectorAll(".item").forEach(it => {
  it.querySelectorAll(".marks button").forEach(b => b.addEventListener("click", () => {
    const s = state[it.dataset.id] || (state[it.dataset.id] = {});
    s.mark = s.mark === b.dataset.mark ? undefined : b.dataset.mark;
    save(); render();
  }));
  it.querySelector("textarea").addEventListener("input", e => {
    (state[it.dataset.id] || (state[it.dataset.id] = {})).note = e.target.value; save(); render();
  });
});
function exported() {
  const out = { shortlist: "__CHECKED__", yes: [], no: [], notes: {} };
  document.querySelectorAll(".item").forEach(it => {
    const s = state[it.dataset.id] || {};
    (s.mark === "yes" ? out.yes : out.no).push(it.dataset.id);
    if (s.note) out.notes[it.dataset.id] = s.note;
  });
  return JSON.stringify(out, null, 1);
}
document.getElementById("export").addEventListener("click", () => { document.getElementById("out").value = exported(); });
document.getElementById("copy").addEventListener("click", () => {
  const t = document.getElementById("out"); t.value = exported();
  navigator.clipboard.writeText(t.value).catch(() => { t.focus(); t.select(); });
});
document.getElementById("allyes").addEventListener("click", () => {
  document.querySelectorAll(".item").forEach(it => { const s = state[it.dataset.id] || (state[it.dataset.id] = {}); if (!s.mark) s.mark = "yes"; });
  save(); render();
});
render();
</script>
"""


def esc(s):
    return html.escape(str(s), quote=True)


def main():
    data = json.load(open(SRC))
    groups = []
    for g in data["groups"]:
        rows = []
        for it in g["items"]:
            meta = " · ".join(x for x in (it.get("author"), f'{it["seconds"]:.0f} s' if it.get("seconds") else None,
                                           it.get("format"), it.get("licence"), it.get("where")) if x)
            note = f'<p class="note">{esc(it["note"])}</p>' if it.get("note") else ""
            rows.append(f"""<div class="item" data-id="{esc(it["id"])}">
  <div><p class="name"><a href="{esc(it["url"])}" target="_blank" rel="noopener">{esc(it["name"])}</a></p>
    <p class="meta">{esc(meta)}</p><p class="why">{esc(it.get("why", ""))}</p>{note}</div>
  <div class="marks"><button type="button" class="yes" data-mark="yes" aria-pressed="false">Yes</button><button type="button" class="no" data-mark="no" aria-pressed="false">No</button></div>
  <textarea aria-label="Note on {esc(it["name"])}" placeholder="Note (optional)"></textarea>
</div>""")
        groups.append(f"""<section><header><p class="eyebrow">{len(g["items"])} candidates</p><h2>{esc(g["title"])}</h2>
<p class="muted">{esc(g.get("use", ""))}</p></header><div class="list">
{chr(10).join(rows)}
</div></section>""")
    page = PAGE.replace("__GROUPS__", "\n".join(groups)).replace("__CHECKED__", esc(data.get("checked", "")))
    with open(OUT, "w") as f:
        f.write(page)
    print(f"shortlist: {sum(len(g['items']) for g in data['groups'])} candidates in {len(data['groups'])} groups -> {os.path.relpath(OUT, ROOT)}")


if __name__ == "__main__":
    main()
