import re, base64, sys, os
# build9.py <page dir> <pushed tip> : after build8.py, puts "Depth and air" at the head of the results page.
# Pictures: every d-*.jpg in the page dir, in name order, each with its caption in d-*.txt beside it.
P, TIP = sys.argv[1:3]
F = P + '/the-run-built.html'
s = open(F).read()

def img(name, alt, cap):
    b = base64.b64encode(open(P + '/' + name, 'rb').read()).decode()
    return ('  <div class="wide"><figure><img src="data:image/jpeg;base64,%s" alt="%s"><figcaption><span class="proof game">real game</span> %s</figcaption></figure></div>\n' % (b, alt, cap))

def sub(pattern, repl):
    global s
    s, n = re.subn(pattern, lambda m: repl, s, count=1, flags=re.S)
    assert n == 1, pattern[:60]

sub(r'<p class="eyebrow">AWAKEN · Gates as Worlds · built 10 Nov 2026[^<]*</p>', '<p class="eyebrow">AWAKEN · Gates as Worlds · built 11 Nov 2026 · not yet played · every kind walked in the real game</p>')
sub(r'<p class="lede">.*?</p>', '<p class="lede">There are nineteen gates you can walk into by command. Two were made by hand. Seventeen are kinds of dungeon rolled from a seed. You said those seventeen had decent rooms now but no depth and no atmosphere. Every one of them now has ground in half blocks, shaped walls, things growing, its own air, light coming down, and its own sound and music. Nothing here has been played by a person yet. You are the first.</p>')
sub(r'<h3>Where it is</h3>\s*<p>.*?</p>', '<h3>Where it is</h3>\n    <p>The nineteen gates and the variety pass are in <code>build/awaken-v1</code>. Depth and air are on branch <code>claude/gates-as-worlds-plan-fcb81b</code> at <code>%s</code>, handed to the manager session to merge. It compiles and every kind was walked in the real game on it. Per your rule no test suite was run here; one runs after the merge. The new gates stay behind their switch, so the old gates are untouched.</p>' % TIP)

figs = sorted(f for f in os.listdir(P) if re.fullmatch(r'd-[1-4]\.jpg', f))
assert figs, 'no d-*.jpg pictures'
pics = ''.join(img(f, 'Real-game shots of the rolled dungeons with depth and air', open(P + '/' + f[:-4] + '.txt').read().strip()) for f in figs)
tries = img('d-5.jpg', 'Two tries at a beam of light, both taken out', open(P + '/d-5.txt').read().strip())

new = '''<section>
  <div class="col">
    <p class="eyebrow">After your second note</p>
    <h2>Depth and air</h2>
    <p>You said the rooms and setups were decent but the atmosphere was missing: flat floors, flat walls, everything a whole block, no half blocks, nothing growing, nothing in the air, and the same for sound and music. All of that is in now, in every one of the seventeen kinds.</p>
    <ul>
      <li><strong>Floors in half blocks.</strong> The way through a room is worn level. Off it the ground rises and falls in half steps and banks up against the walls. Ways have a half step along each side.</li>
      <li><strong>Walls and roofs with shape.</strong> Built places have pilasters on their walls and beams under their roofs. Dug places have roofs that swell, dip and come down in lumps. The long walls of crossings and stairwells are cut into bays between piers, or scooped out where the place was dug.</li>
      <li><strong>Small things everywhere.</strong> Stairs at the foot of walls and where a roof steps. Drifts along the walls. Plants in clumps that thicken away from the path: mushrooms, ferns, moss, vines. Bits underfoot. Things hanging from high roofs.</li>
      <li><strong>Its own air for every kind.</strong> Its own fog, standing close enough to see indoors. Its own things drifting: ash, spores, snow, fireflies, sparks. Its own colour of light and its own Lens look.</li>
      <li><strong>Light comes down.</strong> Every room has a slot in its roof with light falling through it. It is real light: a pool on the floor, with dust turning in it. A Lord's hall has two.</li>
      <li><strong>Mist</strong> over the water in every crossing.</li>
      <li><strong>Sound and music for every kind.</strong> An ambient loop, a mood sound, an occasional sound and a music track each. All are Minecraft's own recordings. The Unlit Stair is silent on purpose.</li>
      <li><strong>The new monsters are in.</strong> All 78 gate monsters have their own bodies now, and these dungeons use them with no change needed here.</li>
    </ul>
  </div>
''' + pics + '''  <div class="col">
    <h3>What I could not check</h3>
    <ul>
      <li><strong>Sound and music.</strong> I chose them by name. Nobody has heard them in place.</li>
      <li><strong>Anything that moves.</strong> These are stills. I have not seen the drifting things in motion.</li>
      <li><strong>Play.</strong> Nobody has fought, jumped or been ambushed in any of it.</li>
      <li><strong>The darkest crossings.</strong> The Hollow Hills, The Green Dark and The Unlit Stair have very dim gulfs and stairwells. What you stand on is lit. Tell me if it is too dark to play.</li>
    </ul>
    <h3>The shader, and the beams that are not there</h3>
    <p>You asked how the new shader could serve these gates. Each kind has its own Lens look: its haze, its glow and its tint. But I found that Lens does not read a gate's light shafts or mist at all yet, though the plan says it should. So I tried to draw a beam down each well, and mist on the floors, as sprites. I tried three ways and walked each in the real game. The game would not draw the faint one at all. The second looked like sheets of glass. The third looked like static. I took all three out. The picture below shows the last two.</p>
    <p>What is in the build is what works: the lit slot, the pool of real light on the floor, and dust turning between them. A true beam needs Lens to read what the gates already send it, or a small renderer of the gates' own. That is the next piece if you want it.</p>
  </div>
''' + tries + '''  <div class="col">
    <h3>Yours to decide</h3>
    <ul>
      <li><strong>True beams and floor mist.</strong> Lens reading the gates' air, or a renderer of the gates' own. Say which, or neither.</li>
      <li><strong>More monsters?</strong> Eleven themes have three ordinary monsters each. Say if you want more made for them.</li>
      <li><strong>How hard they hit.</strong> A bug had stopped ordinary monsters landing plain hits. It is fixed, so they hit harder than in any play so far. Keep it or soften it.</li>
      <li><strong>Which kinds still look weak.</strong> Name them and I will redo their looks first.</li>
    </ul>
  </div>
</section>

'''
anchor = '<section>\n  <div class="col">\n    <p class="eyebrow">After your note</p>'
assert s.count(anchor) == 1
s = s.replace(anchor, new + '<section>\n  <div class="col">\n    <p class="eyebrow">After your first note</p>')

sub(r'<li><strong>The monsters in these pictures are the old ones\.</strong>.*?</li>', '<li><strong>The monsters in the pictures of this part are the old ones.</strong> The new bodies are in the pictures above.</li>')
sub(r'<li><strong>The looks you called rushed\.</strong>.*?</li>', '<li><strong>The looks.</strong> You said afterwards it was less about a theme being rushed and more about depth and atmosphere. That is the part above.</li>')
open(F, 'w').write(s)
print('ok', len(s) // 1024, 'KB', s.count('<figure>'), 'figures')
