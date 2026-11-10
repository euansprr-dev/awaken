import sys
# tours.py <kind>=<seed> ...   writes tools/autopilot/realm-<kind>.txt for each: a walk of that seed, leg by leg
K = {  # id: (name, rank)
 'brood_burrow': ('The Lower Combs', 'E'), 'catacombs': ('The Bone Roads', 'E'), 'hollow_hills': ('The Hollow Hills', 'E'), 'root_cellars': ('The Root Cellars', 'E'),
 'tallow_runs': ('The Tallow Runs', 'E'), 'cinder_workings': ('The Cinder Workings', 'E'), 'sunken_stacks': ('The Sunken Stacks', 'D'), 'sporewells': ('The Sporewells', 'E'),
 'frozen_cells': ('The Frozen Cells', 'C'), 'glass_warrens': ('The Glass Warrens', 'D'), 'stopped_works': ('The Stopped Works', 'D'), 'silvered_halls': ('The Silvered Halls', 'C'),
 'sealed_wing': ('The Sealed Wing', 'C'), 'sleepless_ward': ('The Sleepless Ward', 'C'), 'unlit_stair': ('The Unlit Stair', 'A'), 'rime_hollows': ('The Rime Hollows', 'C'),
 'green_dark': ('The Green Dark', 'E')}
SHOTS = [('first_fight', 'FIGHT 0 7 2'), ('second_fight', 'FIGHT 1 7 2'), ('ambush', 'AMBUSH 0 8 2'), ('warden', 'WARDEN 0 9 3'), ('lord', 'LORD 0 9 4'), ('lord_back', 'LORD 0 -9 5'),
         ('move1', 'MOVEMENT 0 0 2'), ('move2', 'MOVEMENT 1 0 2'), ('move3', 'MOVEMENT 2 0 2'), ('trap', 'TRAP 0 8 2')]
for arg in sys.argv[1:]:
    kind, seed = arg.split('=')
    name, rank = K[kind]
    out = ['# %s (SPEC 50.9): one rolled run of it (seed %s), leg by leg. Each camera stands where you would walk into the leg, whatever' % (name, seed),
           '# the leg is cut like on this seed. Difficulty is normal; the camera is in creative, so nothing wakes.',
           'world autopilot_realm_%s creative 20261113' % kind, 'waitworld', 'gui 0', 'cmd weather clear 1000000', 'cmd time set 18000', 'cmd time pause', 'cmd awaken admin awaken', 'wait 200',
           'cmd awaken powers starter 1', 'wait 20', 'screen close', 'cmd difficulty normal', 'cmd gamerule send_command_feedback false', '',
           '# straight in at the Mouth: no gate has to form in the overworld first. Not in the Realm, no tour.',
           'cmd awaken gate realm_enter %s awaken:%s %s' % (rank, kind, seed), 'need realm', 'hud off', 'wait 30', 'wait 300', 'look 0 -8',
           'cmd awaken gate realm_at MOUTH 0 0 0 3', 'look 0 10', 'wait 80', 'shot %s_mouth' % kind]
    for shot, leg in SHOTS:
        out += ['cmd awaken gate realm_leg ' + leg, 'wait 80', 'shot %s_%s' % (kind, shot)]
    out += ['quit', '']
    open('tools/autopilot/realm-%s.txt' % kind, 'w').write('\n'.join(out))
    print('wrote', kind, seed)
