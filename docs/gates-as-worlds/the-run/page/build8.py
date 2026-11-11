import re, base64, sys, os
# build8.py <page dir> <docs dir> <pushed tip> : the results page with the variety pass at its head
P, D, TIP = sys.argv[1:4]
s = open(D + '/built.html').read()
s = re.sub(r'src="(built-[0-9]+\.jpg)"', lambda m: 'src="data:image/jpeg;base64,' + base64.b64encode(open(D + '/' + m.group(1), 'rb').read()).decode() + '"', s)

def img(name, alt, cap):
    b = base64.b64encode(open(P + '/' + name, 'rb').read()).decode()
    return ('  <div class="wide"><figure><img src="data:image/jpeg;base64,%s" alt="%s"><figcaption><span class="proof game">real game</span> %s</figcaption></figure></div>\n' % (b, alt, cap))

def rep(a, b, count=1):
    global s
    assert s.count(a) == count, (a[:70], s.count(a))
    s = s.replace(a, b)

def sub(pattern, repl):
    global s
    s, n = re.subn(pattern, lambda m: repl, s, count=1, flags=re.S)
    assert n == 1, pattern[:60]

sub(r'<p class="eyebrow">AWAKEN · Gates as Worlds · built 10 Nov 2026[^<]*</p>', '<p class="eyebrow">AWAKEN · Gates as Worlds · built 10 Nov 2026 · not yet played · every kind walked in the real game</p>')
sub(r'<p class="lede">.*?</p>', '<p class="lede">There are nineteen gates you can walk into by command. Two were made by hand. Seventeen are kinds of dungeon rolled from a seed. You told me those seventeen all looked alike, and they did, so every rolled dungeon is now cut differently leg by leg. Nothing here has been played by a person yet. You are the first.</p>')
sub(r'<h3>Where it is</h3>\s*<p>.*?</p>', '<h3>Where it is</h3>\n    <p>The nineteen gates are in <code>build/awaken-v1</code> since <code>b63585d74</code>, after a full test run of 1,357 tests. The work on this page that answers your note is on branch <code>claude/gates-as-worlds-plan-fcb81b</code> at <code>%s</code>, with its own full test run going now, and follows into build when that is green. The new gates stay behind their switch, so the old gates are untouched.</p>' % TIP)

cross = [f for f in ['v-cross.jpg', 'v-cross2.jpg'] if os.path.exists(P + '/' + f)]
rooms = [f for f in ['v-rooms.jpg', 'v-rooms2.jpg'] if os.path.exists(P + '/' + f)]
CAPS = {
 'v-cross.jpg': 'Six crossings, six runs. Top: a deck that gives way under you in The Stopped Works; rafters one block wide and a platform hung on a chain in The Silvered Halls. Bottom: a ledge along each wall with a pillar between them, over the honey, in The Lower Combs; a square stairwell with the bell ropes hanging down it in The Bone Roads; hung platforms in The Lower Combs.',
 'v-cross2.jpg': open(P + '/v-cross2.txt').read().strip() if os.path.exists(P + '/v-cross2.txt') else '',
 'v-rooms.jpg': 'Six rooms, four kinds. Top: a den in a casting pit and a den among piers in The Stopped Works; a Lord&rsquo;s nave in The Silvered Halls. Bottom: a square Warden&rsquo;s room in The Bone Roads; a den with a raised bank along one side in The Green Dark; a long gallery where the ambush is in The Silvered Halls.',
 'v-rooms2.jpg': open(P + '/v-rooms2.txt').read().strip() if os.path.exists(P + '/v-rooms2.txt') else '',
}
new = '''<section>
  <div class="col">
    <p class="eyebrow">After your note</p>
    <h2>No two alike</h2>
    <p>You said the pictures all looked the same with different themes, and asked for more variation, in the parkour above all. You were right. The generator knew one room, one Lord's hall, one shaft and one chasm, and a kind only changed the blocks. I stopped adding kinds and widened what it can build. Every leg of a run now rolls what it is cut like, and each kind has its own share.</p>
    <ul>
      <li><strong>Eight ways across a gulf.</strong> Pillars, a broken bridge, rafters one block wide, a ledge along each wall in turn, platforms hung on chains, great steps down, stones in shallow water, and a deck that gives way under you.</li>
      <li><strong>Eight kinds of room.</strong> An oval, a long hall, a cross, a ring round a core of rock, a hall of piers, a room with a terrace along one side, a pit with a walk round it, and a tall rift whose floor steps up.</li>
      <li><strong>Six kinds of Warden's room and Lord's hall.</strong> A ring of pillars, a nave, a cross, a rotunda, a round floor with tiers of steps, and a square hall on four great piers. The middle is always open and level, because the Lords were built for a level floor.</li>
      <li><strong>Round shafts and square stairwells.</strong></li>
      <li><strong>More of it.</strong> Every standard and great run has a crossing. About four runs in ten have a second one down a side way, with a vault and two caches beyond it. Nobody has to take that one.</li>
      <li><strong>Each kind has its own set.</strong> A palace gets galleries, naves and colonnades. A nest gets round cells, pits and things hung over drops. So two kinds differ in shape now, not only in blocks.</li>
    </ul>
    <p>Tested on 136 rolled dungeons: every place can be walked to, nothing floats, every fight has room, and every one of these shapes turned up. Every picture below is from the real game, each kind on a seed of its own.</p>
  </div>
''' + ''.join(img(f, 'Real-game shots of crossings', CAPS[f]) for f in cross) + ''.join(img(f, 'Real-game shots of rooms', CAPS[f]) for f in rooms) + '''  <div class="col">
    <h3>One run, leg by leg</h3>
    <p>The Stopped Works on seed 507, as it came out: a den among piers, a gallery of spitters, a deck that gives way, a square stairwell, a long hall where the ambush is, a square Warden's room, a den in a casting pit, a walk along the rafters, a den with a gantry along one side, a second ambush, a round Warden's room, and the Lord's nave. Off the third den a side way leads over hung platforms to a vault. Seventeen legs, and no two cut the same.</p>
    <h3>What this does not fix</h3>
    <ul>
      <li><strong>The looks you called rushed.</strong> I changed shapes, not blocks. My own weakest list is The Hollow Hills, The Bone Roads, The Tallow Runs, The Sporewells and The Glass Warrens. Tell me if yours is different.</li>
      <li><strong>Nobody has jumped any of it.</strong> The checker proves every jump is a sprint over one to three blocks, level or down. Whether they are fun is yours to say.</li>
      <li><strong>Crossings are dim in the darkest kinds.</strong> What you stand on is lit; the walls round it are not.</li>
      <li><strong>The monsters in these pictures are the old ones.</strong> Their own bodies are built on another branch and not in these gates yet.</li>
    </ul>
  </div>
</section>

'''
anchor = '<section>\n  <div class="col">\n    <p class="eyebrow">Play</p>'
assert s.count(anchor) == 1
s = s.replace(anchor, new + anchor)

# the commands show a seed that is worth walking
SEEDS = {'brood_burrow': 103, 'catacombs': 144, 'hollow_hills': 184, 'root_cellars': 227, 'tallow_runs': 1, 'sunken_stacks': 306, 'sporewells': 347, 'cinder_workings': 383, 'frozen_cells': 427,
         'glass_warrens': 463, 'stopped_works': 507, 'silvered_halls': 544, 'sealed_wing': 585, 'sleepless_ward': 623, 'unlit_stair': 1, 'rime_hollows': 705, 'green_dark': 741}
for k, seed in SEEDS.items():
    s, n = re.subn(r'(realm_enter [A-Z] awaken:%s) \d+</code>' % k, lambda m: '%s %d</code>' % (m.group(1), seed), s)
    assert n == 1, k
sub(r'<h2>Nineteen gates, one command each</h2>\s*<p>.*?</p>', '<h2>Nineteen gates, one command each</h2>\n    <p>Stand anywhere in the overworld and type the command. Play in survival: packs ignore a creative player and an ambush will not spring. For the seventeen rolled kinds the number at the end is the seed. The ones here are the runs in the pictures; any other whole number is a different dungeon. The letter is the rank. <code>/awaken gate leave</code> takes you out.</p>')
rep('<h2>Seventeen kinds, three sizes, four shapes</h2>', '<h2>Seventeen kinds, three sizes, four shapes</h2>\n    <p class="muted small">The pictures in this part were taken before the shapes changed. They still show what each kind is made of, not how its rooms are cut now.</p>')
sub(r'<li><strong>What comes next here\.</strong>.*?</li>', '<li><strong>Which kinds look rushed to you.</strong> Name them and I will redo their looks first. After that, my pick is to wait for what you miss when you play, and add that.</li>')
open(P + '/the-run-built.html', 'w').write(s)
print('ok', len(s) // 1024, 'KB', s.count('<figure>'), 'figures')
