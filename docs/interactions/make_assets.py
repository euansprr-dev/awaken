"""Builds the approval page for Hands at Work (docs/interactions/PLAN.md) from the audit tours' shots and clips.

    python3 docs/interactions/make_assets.py <stations dir> <stations clips> <vanilla dir> <vanilla clips> <awaken dir> <plates dir>

Each argument is a tour folder under run-autopilot/ (screenshots-ci/<tour>-<run> or clips-ci/<tour>-<run>). It writes
run-autopilot/interactions-page/ (gitignored): index.html and img/*.jpg. Pictures are real game frames; nothing here
draws on them (the page's own mock-ups are drawn in the browser by proto/carry.js). With BUILT=<the hands-at-work tour's
folder> in the environment it also cuts the "as built" pictures."""
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


def unblue(im, box):
    """Takes a hovered slot's thin blue outline off a plate: each outline pixel takes the colour beside it (the table's
    texels are flat, so nothing else changes). box: pixels (x0, y0, x1, y1)."""
    import numpy as np
    a = np.array(im).astype(int)
    x0, y0, x1, y1 = box
    reg = a[y0:y1, x0:x1]
    mask = (reg[:, :, 2] > reg[:, :, 0] + 25) & (reg[:, :, 2] > 120)
    for _ in range(12):
        if not mask.any():
            break
        for dy, dx in ((0, 1), (0, -1), (1, 0), (-1, 0)):
            src = np.roll(reg, (dy, dx), axis=(0, 1))
            ok = np.roll(~mask, (dy, dx), axis=(0, 1)) & mask
            reg[ok] = src[ok]
            mask = mask & ~ok
    a[y0:y1, x0:x1] = reg
    return Image.fromarray(a.astype('uint8'))


def paste_from(dst, src, frac):
    """A region of one shot replaced by the same region of another taken from the same place (a toast, a chat line)."""
    W, H = dst.size
    box = (int(frac[0] * W), int(frac[1] * H), int(frac[2] * W), int(frac[3] * H))
    dst.paste(src.crop(box), box[:2])
    return dst


def save(im, name, w=1366, q=86):
    im.resize((w, round(w * im.height / im.width)), Image.LANCZOS).save(os.path.join(IMG, name + '.jpg'), quality=q, optimize=True)


def main(stations, sclips, plates, vanilla=None, vclips=None, awaken=None):
    os.makedirs(IMG, exist_ok=True)
    S = lambda n: os.path.join(stations, n + '.png')
    P = lambda n: os.path.join(plates, n + '.png')
    C = lambda n: os.path.join(sclips, n)
    # ---- today, from four places ----
    shot(S('a_table_near_carry'), 'today_near')
    shot(S('a_table_laid'), 'today_mid')
    shot(S('a_table_far_carry'), 'today_far')
    shot(S('a_table_corner'), 'today_corner')
    shot(P('p_table_stand_mid'), 'today_mid_clean')
    shot(P('p_table_pick'), 'bench')
    print('lay', strip(C('a_table_lay'), [0, 2, 3, 4], 'strip_lay', w=510))
    print('fast', strip(C('a_table_fast'), [1, 3, 4], 'strip_fast', w=680))
    shot(S('a_table_3p'), 'today_3p')
    shot(S('a_furnace_3p'), 'today_3p_furnace')
    print('3p', strip(C('a_table_3p_front'), [0, 1, 2], 'strip_3p', w=680, crop=(0.15, 0.0, 0.85, 1.0)))
    print('furnace in', strip(C('a_furnace_in'), [1, 2, 3], 'strip_furnace', w=680))
    # ---- plates the page draws on (hands hidden; a hovered outline, a toast and a chat line taken off) ----
    empty = Image.open(P('p_table_empty')).convert('RGB')
    empty = unblue(empty, (1512, 760, 1903, 1137))
    save(empty, 'p_table_empty', q=88)
    hands = Image.open(P('p_table_hands')).convert('RGB')
    hands = paste_from(hands, Image.open(P('p_table_empty')).convert('RGB'), (0.64, 0.0, 1.0, 0.14))
    hands = paste_from(hands, Image.open(P('p_table_pick_hands')).convert('RGB'), (0.0, 0.79, 0.26, 0.88))
    hands = unblue(hands, (1512, 760, 1903, 1137))
    save(hands, 'p_table_hands', q=88)
    shot(S('a_furnace_lit'), 'p_furnace_lit', q=88)
    shot(P('p_chest_open'), 'p_chest_open', q=88)
    for n, o in (('p_anvil', 'b_anvil'), ('p_brewing_stand', 'b_brewing'), ('p_grindstone', 'b_grindstone')):
        shot(P(n), o, w=910, crop=(0.25, 0.08, 0.75, 0.72))
    # ours that are already at the bar: shots from their own tours (other worktrees), when they are on this machine
    for src, o in (('/Volumes/MEDIA/Awaken/.claude/worktrees/armour-forge-plan-c98ed9/run-autopilot/screenshots-ci/arms-anvil-every-41587690872/blade_strikes.png', 'o_war_anvil'),
                   ('/Volumes/MEDIA/Awaken/.claude/worktrees/enchanting-plan-c9d533/run-autopilot/screenshots-ci/enchant-table-41439843140/enchant_06_the_first_stroke.png', 'o_inscribing')):
        if os.path.exists(src):
            shot(src, o)
    # ---- as built: the look tour of the finished scenes (tools/autopilot/hands-at-work.txt), named in BUILT ----
    built = os.environ.get('BUILT')
    if built:
        B = lambda n: os.path.join(built, n + '.png')
        for n, o in (('h_table_carry_mid', 'built_carry'), ('h_table_result', 'built_result'), ('h_table_pile', 'built_pile'), ('h_table_fast', 'built_fast'),
                     ('h_furnace_carry', 'built_furnace_carry'), ('h_furnace_flare', 'built_furnace_flare'), ('h_anvil_result', 'built_anvil'),
                     ('h_smithing_result', 'built_smithing'), ('h_glass_hopper', 'built_glass'), ('h_body_kneel_side', 'built_kneel')):
            shot(B(n), o)
    built_b = os.environ.get('BUILT_B')  # the short tour's folder (hands-at-work-b)
    if built_b:
        shot(os.path.join(built_b, 'h2_brewing_carry_wart.png'), 'built_brewing')
    built_c = os.environ.get('BUILT_C')  # the run that filmed the chest
    if built_c:
        shot(os.path.join(built_c, 'h2_chest_lid_down.png'), 'built_chest')
    if vanilla:
        V = lambda n: os.path.join(vanilla, n + '.png')
        sheet([V('v_anvil'), V('v_smithing_table'), V('v_grindstone'), V('v_stonecutter'), V('v_loom'), V('v_brewing_stand')], 'today_grey', cols=3)
        shot(V('v_chest'), 'v_chest')
    build_html((488, 266))


if __name__ == '__main__':
    main(*sys.argv[1:])
