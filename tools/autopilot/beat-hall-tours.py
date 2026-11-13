import sys
# beat-hall-tours.py [kind ...]   writes tools/autopilot/beats-<kind>.txt (SPEC 72.8): a kind's beat hall at A, laid in the sky over a
# fresh world, and a shot standing three cells short of each beat it names. "all" after a kind shoots every beat of the kit in that
# kind's blocks (beats-<kind>-all.txt); otherwise its signature and five that every kind has, so the kinds can be set side by side.
SIGNATURE = {
    'frozen_cells': 'SLIDE', 'rime_hollows': 'CHUTE', 'stopped_works': 'TEETH', 'cinder_workings': 'CRUMBLE', 'green_dark': 'PIPE',
    'tallow_runs': 'DROPSLIDE', 'unlit_stair': 'SWITCHBACK', 'silvered_halls': 'GULF', 'brood_burrow': 'SLIDEJUMP', 'hollow_hills': 'LEDGES',
    'sporewells': 'CHIMNEY', 'root_cellars': 'SHIMMY', 'sleepless_ward': 'RUNHANG', 'sunken_stacks': 'BROKEN', 'sealed_wing': 'TEETH',
    'catacombs': 'CHAINFALL', 'glass_warrens': 'CRUMBLE'}
COMMON = ['SLIDE', 'GULF', 'MANTLE', 'SHIMMY', 'PIPE']
ALL = ['SLIDE', 'SLIDEJUMP', 'CHUTE', 'DROPSLIDE', 'TEETH', 'RAIL', 'GULF', 'CHIMNEY', 'SWITCHBACK', 'RUNHANG', 'CORNER', 'MANTLE', 'SHIMMY',
       'DROPHANG', 'JUMPHANG', 'LEDGES', 'BROKEN', 'PIPE', 'CHAINFALL', 'SWING', 'CRUMBLE', 'DROPROLL', 'STRIDE', 'ANCHOR', 'LEAP']

args = sys.argv[1:] or list(SIGNATURE)
i = 0
while i < len(args):
    kind = args[i]
    every = i + 1 < len(args) and args[i + 1] == 'all'
    i += 2 if every else 1
    beats = ALL if every else [SIGNATURE[kind]] + [b for b in COMMON if b != SIGNATURE[kind]]
    name = 'beats-%s%s' % (kind, '-all' if every else '')
    out = ['# %s\'s beat hall at A (SPEC 72.8): %s, each from three cells short of it, eye high, looking along it.' % (kind, 'every beat of the kit' if every else 'its signature beat and five every kind has'),
           '# The hall is laid in the sky over a fresh world, in the kind\'s own blocks, and every move in it is proved again where it stands',
           '# (the log\'s "[beats] hall:" line says what held). Night vision, so the shape reads whatever the light; creative, flying.',
           'world autopilot_%s creative 20261113' % name.replace('-', '_'), 'waitworld', 'gui 0', 'cmd weather clear 1000000', 'cmd time set 6000', 'cmd time pause',
           'cmd awaken admin awaken', 'wait 200', 'cmd awaken powers starter 1', 'wait 20', 'screen close', 'cmd gamerule send_command_feedback false',
           'cmd effect give @s minecraft:night_vision infinite 0 true', 'hud off', '',
           # two and a half million blocks: half a minute before the client has them all, or the first shots are the sky
           'cmd awaken gate beat_hall A awaken:%s' % kind, 'wait 600']
    for k, b in enumerate(beats):
        # five seconds: the chunks round a new place come in, then the shot (the first is far from where the hall was laid: ten)
        out += ['cmd awaken gate beat_at %s 3 0' % b, 'wait %d' % (200 if k == 0 else 100), 'shot %s_%s' % (kind, b.lower())]
    out += ['quit', '']
    open('tools/autopilot/%s.txt' % name, 'w').write('\n'.join(out))
    print('wrote', name, len(beats), 'shots')
