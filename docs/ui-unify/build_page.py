#!/usr/bin/env python3
"""Builds docs/ui-unify/page.html from src/page.src.html: inlines the prototype's CSS, data and script, and writes
files.json, the list of pictures the page needs beside it (the Artifact publish takes that list).
Run: python3 docs/ui-unify/build_page.py"""
import json, os, re

HERE = os.path.dirname(os.path.abspath(__file__))
rd = lambda p: open(os.path.join(HERE, p), encoding='utf-8').read()
src = rd('src/page.src.html')
css = rd('proto/proto.css')
js = rd('proto/data.js') + '\n' + rd('proto/proto.js')
page = src.replace('/*__PROTO_CSS__*/', css).replace('/*__PROTO_JS__*/', js)
open(os.path.join(HERE, 'page.html'), 'w', encoding='utf-8').write(page)

# every picture the page or the prototype can ask for
need = set(re.findall(r'shots/[A-Za-z0-9_]+\.jpg', page))
names = set(re.findall(r"'((?:i|v|b|w|p|e|g)_[a-z0-9_]+)'", js)) | set(re.findall(r'"([a-z_0-9]+)"', rd('proto/data.js').split('allPowers:')[1].split(']')[0]))
assets = os.listdir(os.path.join(HERE, 'proto/assets'))
have = {os.path.splitext(a)[0]: a for a in assets}
for n in names:
    for cand in (n, 'p_' + n):
        if cand in have:
            need.add('proto/assets/' + have[cand])
for a in assets:
    if a.startswith(('suit_', 'bg_', 'hall_lineup', 'hall_wall', 'map_scene')):
        need.add('proto/assets/' + a)
missing = [f for f in sorted(need) if not os.path.exists(os.path.join(HERE, f))]
files = [f for f in sorted(need) if f not in missing]
json.dump(files, open(os.path.join(HERE, 'files.json'), 'w'))
size = sum(os.path.getsize(os.path.join(HERE, f)) for f in files)
print(f'page {len(page) // 1024} KB, {len(files)} files, {size // 1024} KB; missing: {missing}')
