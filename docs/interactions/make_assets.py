"""Builds the approval page for Hands at Work (docs/interactions/PLAN.md) from the audit tours' shots and clips.

    python3 docs/interactions/make_assets.py <stations dir> <stations clips> <vanilla dir> <vanilla clips> <awaken dir> <plates dir>

Each argument is a tour folder under run-autopilot/ (screenshots-ci/<tour>-<run> or clips-ci/<tour>-<run>). It writes
run-autopilot/interactions-page/ (gitignored): index.html and img/*.jpg. Pictures are real game frames; nothing here
draws on them (the page's own mock-ups are drawn in the browser by proto/carry.js)."""
import os
import sys
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, 'run-autopilot', 'interactions-page')
IMG = os.path.join(OUT, 'img')


def shot(src, name, w=1366, crop=None, q=84):
    im = Image.open(src).convert('RGB')
    if crop:
        W, H = im.size
        im = im.crop((int(crop[0] * W), int(crop[1] * H), int(crop[2] * W), int(crop[3] * H)))
    h = round(w * im.height / im.width)
    im.resize((w, h), Image.LANCZOS).save(os.path.join(IMG, name + '.jpg'), quality=q, optimize=True)
    return w, h


def frames(clip):
    fs = sorted(f for f in os.listdir(clip) if f.startswith('f') and f.endswith('.png'))
    times = {}
    tp = os.path.join(clip, 'times.txt')
    if os.path.exists(tp):
        for line in open(tp):
            a = line.split()
            if len(a) == 2:
                times[int(a[0])] = float(a[1])
    return fs, times


def strip(clip, picks, name, w=340, crop=None, gap=3, q=82):
    """A row of frames from a clip. picks: frame indexes, or None for an even spread of six."""
    fs, times = frames(clip)
    if picks is None:
        picks = [round(i * (len(fs) - 1) / 5) for i in range(6)]
    picks = [min(p, len(fs) - 1) for p in picks]
    ims = []
    for p in picks:
        im = Image.open(os.path.join(clip, fs[p])).convert('RGB')
        if crop:
            W, H = im.size
            im = im.crop((int(crop[0] * W), int(crop[1] * H), int(crop[2] * W), int(crop[3] * H)))
        ims.append(im.resize((w, round(w * im.height / im.width)), Image.LANCZOS))
    h = ims[0].height
    S = Image.new('RGB', (len(ims) * w + (len(ims) - 1) * gap, h), (14, 19, 27))
    for i, im in enumerate(ims):
        S.paste(im, (i * (w + gap), 0))
    S.save(os.path.join(IMG, name + '.jpg'), quality=q, optimize=True)
    t0 = times.get(picks[0], 0)
    return [round(times.get(p, 0) - t0, 1) for p in picks]


def sheet(srcs, name, cols=3, w=455, crop=None, gap=3, q=82):
    ims = []
    for s in srcs:
        im = Image.open(s).convert('RGB')
        if crop:
            W, H = im.size
            im = im.crop((int(crop[0] * W), int(crop[1] * H), int(crop[2] * W), int(crop[3] * H)))
        ims.append(im.resize((w, round(w * im.height / im.width)), Image.LANCZOS))
    h = ims[0].height
    rows = (len(ims) + cols - 1) // cols
    S = Image.new('RGB', (cols * w + (cols - 1) * gap, rows * h + (rows - 1) * gap), (14, 19, 27))
    for i, im in enumerate(ims):
        S.paste(im, ((i % cols) * (w + gap), (i // cols) * (h + gap)))
    S.save(os.path.join(IMG, name + '.jpg'), quality=q, optimize=True)


def build_html(gui):
    src = open(os.path.join(ROOT, 'docs', 'interactions', 'page.src.html')).read()
    js = open(os.path.join(ROOT, 'docs', 'interactions', 'proto', 'carry.js')).read()
    src = src.replace('/*CARRY*/', js)
    a, _, c = src.partition('/*GUI*/')
    _, _, c = c.partition('/*GUI*/')
    src = a + '[%d, %d]' % gui + c
    open(os.path.join(OUT, 'index.html'), 'w').write(src)
    # the same page inside the skeleton the Artifact tool adds, for a local look before publishing
    open(os.path.join(OUT, 'preview.html'), 'w').write('<!doctype html><html><head><meta charset="utf8"><meta name="viewport" '
        'content="width=device-width,initial-scale=1,viewport-fit=cover"><style>body{margin:0;font:14px system-ui}img{max-width:100%}'
        '[hidden]{display:none!important}</style></head><body>' + src + '</body></html>')


if __name__ == '__main__':
    os.makedirs(IMG, exist_ok=True)
    print('use the functions from a session; see PLAN.md for which shots the page shows')
