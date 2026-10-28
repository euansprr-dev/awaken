"""Power ladders (SPEC 34, docs/power-levels/PLAN.md): the one source for what levels 2-5 of every power add.

One P row per power with four steps, each (name, look, does); level 5 also has body (the new cast gesture) and is the power's
Awakened form. The look is what you and everyone near you see, does the change in play: plain words, this text is what the K
menu, the evolution moment and the Power Core hover show. Below the rows, one M row per step gives what the game runs: the
step's riders (shared mechanics, powers.ladder.Riders), its look layers (powers.client.fx.ladder.LadderFx) and the numbers its
own code reads (c.num). Levels 3 and 5 also bring the family's sound layer, level 5 the awakened palette and gesture.

    python3 tools/art/specs/power_ladder_table.py build   writes src/main/resources/data/awaken/powers/ladders.json and
                                                          src/main/merge/powers_ladder/assets/awaken/lang/en_us.json
    python3 tools/art/specs/power_ladder_table.py md      prints the markdown table (PLAN.md section 6)
"""
import html
import json
import os
import sys
from collections import OrderedDict

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "../../.."))
sys.path.insert(0, HERE)

L = []


def P(pid, name, fam, slot, l2, l3, l4, l5, starter=False, hidden=False, note=None):
    L.append(dict(id=pid, name=name, fam=fam, slot=slot, steps=[l2, l3, l4, l5], starter=starter, hidden=hidden, note=note))


# ---- Kinetic: heavy, low, the ground answers ------------------------------------------------------------------------
P("haymaker", "Haymaker", "Kinetic", "active",
  ("Knuckle Shards", "Shards of amber light orbit the charging fist and close in as the charge fills.", "Full charge comes 20% sooner."),
  ("Shockfist", "A cone of force bursts out of the far side of the target along the punch.", "Foes behind the target take half damage and are pushed back."),
  ("Cannonball", "A launched target trails dust and a ring of force where it lands.", "A fully charged target flies as a projectile and hits whatever it crashes into."),
  ("Meteor Fist", "Your forearm is wrapped in a spiral of embers while you charge. The punch tunnels the air with rings, and a ghost fist lands again a third of a second later.",
   "A second impact for 40% of the first.", "A stepping lunge punch with the off arm thrown back."), starter=True)
P("shockwave", "Shockwave", "Kinetic", "active",
  ("Stone Crest", "The wave carries a curling crest of dust and stone along its front.", "Reaches 15% further."),
  ("Spine", "The wave leaves a crack along its path that erupts half a second later into a row of stone spikes.", "The spikes hit anything still in the line."),
  ("Rolling Thunder", "The wave widens as it travels and kicks up the ground's own blocks.", "Foes are thrown higher and stay down for half a second."),
  ("Landbreaker", "The stamp splits the earth: a rolling wall of upturned slabs rides the crest and a glowing fissure runs behind it, cooling from gold to red.",
   "Foes hit are stunned for half a second and the fissure burns for 2 s.", "The knee comes higher with a hang at the top; both fists drop as the foot plants."), starter=True)
P("ground_slam", "Ground Slam", "Kinetic", "active",
  ("Fault Lines", "Glowing amber cracks run out from the impact and cool from gold to red over 2 s.", "Foes standing on the cracks are slowed for a second."),
  ("Aftershock", "A second, lower ring rolls out half a second later with its own thump.", "The aftershock hits again for half damage."),
  ("Upheaval", "Slabs of the real ground jut up round the impact and drop back.", "Foes in the inner half are popped into the air."),
  ("Worldbreaker", "You hang a beat in the air with light wound round both fists, then a dome of force flattens the dust, the crater rim glows and slabs rise in a ring.",
   "The slam pulls foes inward first, then the aftershock throws them out.", "Both fists raised overhead, a hang, a two-fisted drive into the ground."))
P("leap", "Leap", "Kinetic", "movement",
  ("Comet Arc", "An amber ribbon traces your arc from the feet; a burst of dust at take-off.", "15% further."),
  ("Hard Landing", "Your landing cracks Fault Lines into the ground.", "The landing hits harder and slows foes for a second."),
  ("Rebound", "A ring of force snaps up off the landing.", "Foes are thrown up, and you can't be knocked back for a second after."),
  ("Titanfall", "At the top of the arc, press again to drive straight down: a three-point landing, a glowing crater and a ring of rising slabs.",
   "The plunge hits like a Ground Slam from that height.", "Knee and fist hit the ground together; the head comes up last."),
  note="The plunge is predicted on your client like the leap itself. No step adds an air jump (the Ascent's Leaper owns those).")
P("terrain_rip", "Terrain Rip", "Kinetic", "active",
  ("Molten Seams", "The ripped chunk glows along its cracks and trails dust as it flies.", "A 15% wider splash."),
  ("Shrapnel", "The chunk bursts on impact into three tumbling rocks.", "Each rock hits a different foe nearby."),
  ("Twin Rip", "Your off hand tears out a second chunk a beat after the first.", "A second throw at the next closest foe."),
  ("Mountain Hurl", "You heave a three-block slab overhead with both hands; it tumbles end over end and lands as a rolling line of shock.",
   "The landing becomes a Shockwave along the line of the throw.", "A two-handed overhead heave."))
P("brace", "Brace", "Kinetic", "active",
  ("Bronze Skin", "A bronze sheen flows over you and your feet crack the ground.", "Reflects 10% more back at attackers."),
  ("Counter Shove", "When it ends, a ring of force shoves out from your feet.", "The shove hits for part of what you absorbed."),
  ("Unmoved", "Arrows spark off the sheen and drop.", "Projectiles do half damage while you're braced."),
  ("Mountain Stance", "Stone plates rise and circle you, catching what's thrown at you; when it ends they burst outward.",
   "The plates block three hits and cut the foes near you as they fly.", "Fists crossed at the chest, then slammed apart."))
P("thunderclap", "Thunderclap", "Kinetic", "active",
  ("Static Hands", "Arcs dance between your hands during the wind-up.", "The stun lasts 0.2 s longer."),
  ("Forking", "Each bolt forks to one more foe.", "Every strike chains once."),
  ("Thunderhead", "A storm knot hangs over you for 3 s after the clap.", "It drops one bolt a second on a foe near you."),
  ("Heaven's Clap", "Hands raised overhead, the clap throws a flat disc of lightning and a pillar strikes you.",
   "For 3 s your blows chain lightning to a second foe.", "An overhead clap, the body rising onto the toes."), hidden=True)

# ---- Velocity: streaks, red, the line you leave ---------------------------------------------------------------------
P("dash", "Dash", "Velocity", "movement",
  ("Sonic Ring", "A crisp sonic ring and a pressure cone at the start; the red ribbons double.", "Foes you pass through are shoved aside."),
  ("Momentum", "Your leading fist glows red for a second after a dash.", "Your next blow within a second hits 25% harder."),
  ("Chain Charge", "Each chained air dash adds a red ring that stacks round you.", "From the third dash in a chain, half the energy comes back."),
  ("Redline", "Your path stays drawn in the air as a red line of light that snaps a beat later.",
   "The snap cuts foes along the path for 60%.", "A lower, longer lean with one arm trailing."),
  note="Direction never changes: a dash goes the way you move at every level and in every variant (SPEC section 6).")
P("flow", "Flow", "Velocity", "active",
  ("Ripple", "The slowdown arrives as a shell sweeping out from you; colour drains outside it.", "Lasts half a second longer."),
  ("Afterimages", "You leave red afterimages as you move.", "Each kill during Flow adds half a second (2 s at most)."),
  ("Tempo", "Flow ends with a red snap round you.", "20% faster movement and attacks for 3 s after."),
  ("Red Hour", "Every foe you hit gets a hanging red slash mark; when Flow ends every mark snaps at once.",
   "The marks deal 30% of the damage you did during Flow.", "A breath in with the eyes closed, then a sharp open."))
P("afterimage", "Afterimage", "Velocity", "active",
  ("Vivid", "The copy holds your pose sharply inside a glowing red outline.", "Lasts a second longer."),
  ("Mimic", "The copy swings once at the nearest foe.", "Its blow hits for half of yours."),
  ("Snare", "Red threads hang off the copy.", "Foes that strike it are slowed."),
  ("Phantom Rush", "The copy bursts into a ring of red crescents.", "Press again within 2 s to swap places with it.", "A hard sideways step out of your own body."))
P("wall_run", "Wall Stride", "Velocity", "passive",
  ("Spark Steps", "Red sparks kick off each step on the wall.", "Half a second longer on the wall."),
  ("Kick-off", "Leaving the wall leaves a red ring behind you.", "A burst of speed as you leave."),
  ("Steady", "A thin red line marks your path along the wall.", "You don't sink at all."),
  ("Street Runner", "A red speed ribbon trails you along the wall and you flip off the end.",
   "Each new wall within a second of the last adds a second to the run.", "The flip off the wall's end."),
  note="Agree with the body module first: it owns wall-running (SPEC section 17).")
P("water_run", "Water Run", "Velocity", "passive",
  ("Wake", "The water parts in a V-shaped wake with spray.", "No slowdown as you step onto water."),
  ("Skip", "Each stride leaves a ring of light on the surface.", "A leap off water keeps your speed."),
  ("Still Water", "The water stills in a circle under you.", "You can stand on water for 2 s."),
  ("Tidewalker", "Every step leaves a glowing ring; at full sprint you throw a bow wave.", "The wave pushes foes in the water away.", "Sprint posture leaning into the wave."))
P("lightning_step", "Lightning Step", "Velocity", "movement",
  ("Bright Path", "The path stays lit for a second.", "2 blocks further."),
  ("Static Field", "Where you land, crackle spreads over the ground.", "Foes in it are slowed for 2 s."),
  ("Double Flash", "A spare charge sparks at your heels.", "A second flash within a second costs nothing."),
  ("Thunder Step", "A bolt strikes from the sky at both ends of your path.", "Everything on the path is stunned for half a second.", "A snap of two fingers, then you are gone."), hidden=True)

# ---- Flight and gravity: rising, orbiting, inward -------------------------------------------------------------------
P("hover", "Hover", "Flight", "active",
  ("Wind Rings", "Rings of wind turn under your feet.", "A second longer."),
  ("Updraft", "Wind spirals up round you.", "Hold jump to rise slowly."),
  ("Calm Air", "Arrows bend away as they reach the wind round you.", "Arrows that reach you do half damage."),
  ("Skyhold", "A disc of pale light forms under your feet.", "Allies can stand on it while you hover.", "Arms spread low, palms down."),
  note="No step adds flight or a glide: the Ascent's Wings own those.")
P("flight", "Flight", "Flight", "movement",
  ("Contrails", "White ribbons stream from hands and feet.", "Drains 10% less."),
  ("Sonic Wake", "At top speed a vapour cone forms round you and booms.", "10% faster at the top."),
  ("Long Haul", "The contrails stretch and hold longer.", "Drains another 10% less."),
  ("Jetstream", "A burst with a boom ring and a long contrail.", "Double-tap forward for a 2 s burst of speed.", "A tuck into a dive line."),
  note="Agree with the Ascent first (Skyborn and the Sovereign form are its own).")
P("gravity_well", "Gravity Well", "Flight", "active",
  ("Lens", "A dark core with a bright violet rim; dust spirals inward.", "Pulls 15% harder."),
  ("Debris Ring", "Chunks of the ground orbit the core.", "The chunks hit foes they pass."),
  ("Crush", "The knot visibly tightens as it runs.", "Its damage climbs over its length."),
  ("Black Star", "A tilted disc of violet light spins round the core; at the end it collapses and bursts.",
   "The burst hits for 30% of what the well dealt.", "Both hands press down and twist."))
P("telekinesis", "Telekinesis", "Flight", "active",
  ("Grip", "A ring of violet runes clamps the lifted mob.", "Hold a second longer."),
  ("Pair", "A second ring takes the mob next to it.", "Lifts a second foe near the first."),
  ("Tumble", "Lifted mobs tumble slowly in the air.", "They hit whatever they pass."),
  ("Orbit Throw", "What you lift orbits round you; the throw curves to your aim with a violet trail.",
   "Throws everything you hold at once, each at its own target.", "A two-handed gather, then a swing out like a sling."))
P("meteor_dive", "Meteor Dive", "Flight", "active",
  ("Burning Entry", "A comet trail and a shock cone as you dive.", "Dives 15% faster."),
  ("Crater Rim", "A ring of molten rock where you land.", "It burns for 2 s."),
  ("Rebound", "You bounce once into a second, smaller impact.", "A second hit for half."),
  ("Starfall", "Three smaller meteors fall round the impact a beat after you.", "Each meteor hits the nearest foe.", "A rising spin before the drop."))
P("gravity_flip", "Gravity Flip", "Flight", "active",
  ("Upside Dust", "Dust and pebbles fall upward with the foes.", "Foes hang half a second longer."),
  ("Collision", "Foes in the air drift together.", "They crash into each other."),
  ("Weightless", "A pale shimmer stays on you.", "You float for 2 s after."),
  ("Inverted Sky", "The sky flips twice: up, down, up, then the crash.", "Two drops instead of one.", "Both hands turn over twice."), hidden=True)

# ---- Energy: orbs, straight lines, bloom ----------------------------------------------------------------------------
P("beam", "Beam", "Energy", "active",
  ("Helix", "A bright ribbon of light spirals round the beam and spins; embers peel off along it.",
   "Held on one target, the beam builds up to 20% more damage over 1.5 s, and the spiral tightens as it builds."),
  ("Lance", "A second ribbon turns the other way (a double helix) and a flare bursts at every body it passes through.",
   "Pierces the first foe and carries on to a second at 60%."),
  ("Corona", "Where the beam lands, a molten pool spreads and a ring of light turns round the end.",
   "The pool burns foes standing in it for 2 s."),
  ("Sunspear", "The beam is born from a ring of runes at your palm. Three ribbons, a white-gold core, and on letting go a last pulse runs down the beam and bursts at the end.",
   "The last pulse deals 20% of what the beam dealt.", "A wide braced stance, the off hand flat on the forearm; on letting go, the palm closes."),
  starter=True, note="Prism: the centre beam keeps every layer, the side beams the core and one ribbon. Focused: a tighter spiral.")
P("charged_shot", "Charged Shot", "Energy", "active",
  ("Coil", "A spinning coil of lightning wraps the charge orb.", "Charges 15% faster."),
  ("Arc", "On a hit the bolt jumps to a second foe.", "The jump hits for half."),
  ("Overcharge", "Hold past full and the orb turns white and spits sparks.", "An overcharged bolt pierces."),
  ("Thunder Lance", "A full charge fires a lance that leaves a crackling line for 2 s, and a bolt from the sky strikes the target.",
   "The line shocks foes that cross it.", "The bolt hand pulled back to the ear, then thrown like a spear."), starter=True)
P("nova", "Nova", "Energy", "active",
  ("Orbits", "Two rings of light orbit the shell as it grows.", "10% wider."),
  ("Searing Shell", "The shell holds a second longer.", "Foes touching it burn."),
  ("Flare Arcs", "Plasma arcs leap from the shell to foes past its edge.", "Up to three of them, at half damage."),
  ("Daystar", "You rise a little as a white-gold sun forms at your chest; the release is a shell, a ring on the ground and a pillar of light.",
   "Foes are blinded for a second and the afterglow burns.", "Arms open wide, head back."))
P("barrier", "Barrier", "Energy", "active",
  ("Hex Shell", "Hex cells light up where the shield is struck.", "Lasts 2 s longer."),
  ("Stored Charge", "The shield brightens as it takes damage.", "The break shock grows with what it absorbed."),
  ("Second Skin", "When it breaks, a thinner shell flickers back.", "It re-forms once at a third strength for 3 s."),
  ("Halo Ward", "Three shield plates orbit you and catch arrows; the break is a burst of light.",
   "The plates stop three projectiles.", "Palms pushed out, then drawn round in a circle."))
P("overload", "Overload", "Energy", "ultimate",
  ("Gathering Arcs", "Arcs crawl over you while you gather.", "The afterglow lasts 2 s longer."),
  ("Fork", "The blast throws lightning to foes beyond it.", "Forks to five foes."),
  ("Live Wire", "Your energy hits crackle during the afterglow.", "They jump to a second foe."),
  ("Critical Mass", "You're lifted while you gather; the blast pulls in, then explodes, and leaves a ring of plasma.",
   "Pull then blast, and the ring burns for 3 s.", "Lifted, curled in, then thrown open."))
P("prism_lance", "Prism Lance", "Energy", "ultimate",
  ("Refraction", "Each split throws a flare of split colour.", "The split beams reach 4 blocks further."),
  ("Pierce", "Split beams leave a flare at each body.", "Split beams pierce once."),
  ("Bounce", "Split beams glance off walls.", "Split beams bounce once."),
  ("Spectrum Crown", "A crown of six prisms rises over your head and each fires its own beam.",
   "Six beams find six different foes.", "The chest opened, the head lifted to the crown."), hidden=True)

# ---- Shadow: jagged, ink, the cut ----------------------------------------------------------------------------------
P("shadow_step", "Shadow Step", "Shadow", "active",
  ("Ink Wake", "A trail of ink smoke with violet rims follows the step.", "The backstab window is a second longer."),
  ("Blinding", "A burst of ink at the back of the target's head.", "The backstab blinds for a second."),
  ("Return", "An ink pool stays where you left.", "Press again within 2 s to step back to it."),
  ("Night Cut", "You arrive in a low crouch and a crescent of shadow slashes through the target by itself; tendrils from the pool hold foes nearby.",
   "The crescent lands the backstab for you, and tendrils hold foes near you for a second.", "A low crouch arrival with a hand sweeping across."), starter=True)
P("cloak", "Cloak", "Shadow", "active",
  ("Haze", "A faint shimmer; allies see your outline.", "A second longer."),
  ("Shade Step", "Your hands trail ink while cloaked.", "While cloaked, Shadow Step and Echo Swap cost nothing."),
  ("Ambush Mark", "The first strike leaves a violet crescent on the foe.", "The mark bleeds for 3 s."),
  ("Nightfall", "A dome of dusk falls round you.", "Foes inside lose track of your allies too.", "Arms crossed, then drawn down like a hood."))
P("echo_swap", "Echo Swap", "Shadow", "active",
  ("Ink Line", "An ink thread draws between the two places.", "Reaches 4 blocks further."),
  ("Hobble", "Ink clings to the foes struck.", "Struck foes are slowed."),
  ("Double Swap", "The thread hangs a moment.", "Swap back within 2 s for free."),
  ("Left Behind", "A shadow copy stays where you were.", "It draws attacks for 3 s.", "A clap that leaves your outline standing."))
P("legion", "Legion", "Shadow", "ultimate",
  ("Rim Light", "Soldiers rise from ink pools with violet eyes and lit edges.", "One more soldier."),
  ("Charge", "Soldiers rise already running.", "They open with a lunge."),
  ("Ink Death", "Fallen soldiers spill ink.", "The ink slows foes."),
  ("The Risen Guard", "A circle of runes spreads under you and the soldiers rise in a ring round a captain twice their size.",
   "The captain is a knight that lasts the whole time.", "One hand raised, palm up, then closed."), hidden=True)

# ---- Mind: concentric, still, gold -----------------------------------------------------------------------------------
P("danger_sense", "Danger Sense", "Mind", "passive",
  ("Edge Arc", "A warning arc on the edge of your screen points to the threat.", "You know where it's coming from."),
  ("Sooner", "The red glow starts brighter.", "Warns 0.2 s earlier."),
  ("Read the Shot", "Arrows aimed at you show as golden lines before they fly.", "You see a shot's line."),
  ("Sixth Sense", "A gold ripple when you dodge a warned blow.", "The attacker is slowed for a second.", "None (a passive)."))
P("dominate", "Dominate", "Mind", "active",
  ("Crown", "A gold crown glyph turns over the puppet's head.", "A second longer."),
  ("Marked Prey", "What the puppet hits glows pink.", "Those foes take 15% more from you."),
  ("Last Order", "The puppet's end is a psychic burst.", "The burst hits foes round it."),
  ("Puppet King", "Gold threads run from your fingers to the puppet.", "The puppet hits half again as hard, and an elite can be bent for the full time.",
   "Fingers raised like a puppeteer's."))
P("read_intent", "Read Intent", "Mind", "active",
  ("Rings", "Read foes get pink rings at their feet.", "Reads 4 blocks further."),
  ("Tells", "A red flash a moment before a read foe attacks.", "You see their next blow coming."),
  ("Through Walls", "The rings show through walls.", "Reads through walls."),
  ("Open Book", "A read foe's weak point glows.", "Hits on it are critical.", "Fingers to the temple, then a slow point."))
P("decoy", "Decoy", "Mind", "active",
  ("Crisp", "The decoy has a pink rim and taunts.", "A second longer."),
  ("Echo Cast", "The decoy mouths your casts.", "It repeats your casts at 30%."),
  ("Confusion", "The burst leaves spinning pink runes over foes.", "Foes strike each other for 2 s."),
  ("Hall of Mirrors", "Three decoys in a triangle that run together and burst at once.", "Three times the burst.", "A flick of the hand three ways."))
P("foresight", "Foresight", "Mind", "active",
  ("Ghost Lines", "Faint lines show where foes are about to move.", "You see their paths."),
  ("Longer", "The slowdown fades in more gently.", "Half a second longer."),
  ("Counter", "The blow that misses meets a gold flash.", "The attacker is staggered."),
  ("A Heartbeat Ahead", "Ghosts of foes show where they'll be in a second.", "The first two blows miss, not one.", "Eyes closed, then open wide."), hidden=True)

# ---- Architect: grids, seams, the made thing -------------------------------------------------------------------------
P("wall", "Wall", "Architect", "active",
  ("Blueprint", "The wall is drawn in glowing blueprint lines first, then fills in block by block.", "Builds twice as fast and lasts 5 s longer."),
  ("Ricochet", "Arrows strike sparks off it.", "Arrows that hit it fly back toward the shooter."),
  ("Crystal", "The hard light sets into crystal facets.", "Twice as tough."),
  ("Mirror Wall", "A wall of polished light that throws back what hits it.", "Reflects projectiles and beams.", "Both palms pushed out flat."))
P("ramp", "Ramp", "Architect", "active",
  ("Blueprint", "Drawn in blueprint lines, then filled.", "Builds twice as fast and lasts 5 s longer."),
  ("Rush", "Glowing chevrons run up the surface.", "Running up it gives a burst of speed."),
  ("Rails", "Rails of light along both sides.", "Foes that step on it are pushed off the side."),
  ("Launch Ramp", "The end flips up as you reach it.", "It throws you in a long arc.", "A sweeping upward palm."))
P("floor", "Floor", "Architect", "active",
  ("Blueprint", "Drawn in blueprint lines, then filled.", "Builds twice as fast and lasts 5 s longer."),
  ("Spring", "The surface ripples when stepped on.", "Landing on it bounces you up."),
  ("Hold", "Thicker seams.", "Lasts twice as long."),
  ("Sky Bridge", "The floor keeps building ahead as you walk.", "It extends for 6 s.", "Hands sliding apart along a line."))
P("cone", "Cone", "Architect", "active",
  ("Blueprint", "Drawn in blueprint lines, then filled.", "Builds twice as fast and lasts 5 s longer."),
  ("Lantern", "Light drips from the roof.", "You heal slowly under it."),
  ("Shatterproof", "Arrows shatter on the eaves.", "Takes no damage from arrows."),
  ("Pagoda", "A three-tier roof with lit eaves.", "Heals and pushes foes out from under it.", "Hands drawing the roof's tiers."))
P("rampart", "Rampart", "Architect", "active",
  ("Blueprint", "Drawn in blueprint lines, then filled.", "Builds twice as fast and lasts 5 s longer."),
  ("Arrow Slits", "Battlements glow.", "They fire bolts at foes."),
  ("Gatehouse", "The door glows for friends.", "The door opens only for you and allies."),
  ("Keep", "A corner tower rises with a beacon on top.", "The beacon slows foes nearby.", "Arms raised to frame a tower."))
P("spire", "Spire", "Architect", "active",
  ("Seams", "Seams of light run up the column as it rises.", "The throw is 15% higher."),
  ("Spiked Rim", "Spikes ring the base.", "Foes thrown take damage when they land."),
  ("Tall", "The column rises higher.", "A bigger launch."),
  ("Spiral Tower", "A spiral of steps winds up round the spire.", "You can climb it after the launch; it stands for 10 s.", "A spiralling upward sweep."))
P("collapse", "Collapse", "Architect", "active",
  ("Gather", "Blocks lift and circle you before they fly.", "Pulls in from 25% further."),
  ("Seek", "Each block leaves a blueprint line in the air.", "Each block picks a different foe."),
  ("Shatter", "Blocks burst into glowing shards on impact.", "Shards hit foes nearby."),
  ("Avalanche of Light", "Every block becomes a glowing comet trailing blueprint lines.", "Every block stuns.", "Both arms pulled down like a rope."))
P("citadel", "Citadel", "Architect", "ultimate",
  ("Runes", "Runes turn across the dome.", "A second longer."),
  ("Spires", "Four spires rise at the edge.", "They fire bolts at foes."),
  ("Sanctuary", "The ground inside glows.", "Heals more."),
  ("Crown Citadel", "A crown of spires and a beacon of light.", "Foes inside burn; allies inside are healed.", "Arms raised in a slow arc."), hidden=True)

# ---- Tech: precise, thin, holographic ------------------------------------------------------------------------------
P("grapple", "Snap Cable", "Tech", "movement",
  ("Holo Cable", "The cable shows bright teal segments and a reticle where it'll land.", "2 blocks further."),
  ("Stun Hook", "A hooked mob sparks.", "It's stunned for half a second."),
  ("Quick Reel", "The cable hums tighter.", "Reels 20% faster."),
  ("Reel Kick", "You arrive at a hooked mob feet first, with a teal shock ring.", "A flying kick on arrival.", "A side-on cast, then a tuck."))
P("jet_burst", "Jet Burst", "Tech", "movement",
  ("Shock Diamonds", "A blue-cored exhaust with bright shock diamonds.", "10% faster."),
  ("Scorch", "The exhaust lingers as a burning streak.", "Burns longer."),
  ("Hang", "The jets sputter on for a moment.", "A moment of hover after the burst."),
  ("Afterburner Boom", "A sonic boom ring and a scorching trail line.", "The boom pushes foes away.", "A crouch, then a launch."))
P("drone", "Drone", "Tech", "active",
  ("Holo Wings", "The drones wear holographic wings and reticles.", "A second longer."),
  ("Marks", "Their targets get a reticle.", "Marked foes take 10% more from you."),
  ("Burst Fire", "Three-shot bursts.", "Fires in bursts."),
  ("Overwatch", "Laser threads link the drones into a triangle.", "The threads cut what crosses them.", "A tap on the wrist, then a flat palm."))
P("turret", "Turret", "Tech", "active",
  ("Scan Cone", "A holographic scan cone sweeps.", "Sees 4 blocks further."),
  ("Slowing Shots", "Its shots leave frost-blue hex marks.", "Shots slow."),
  ("Shield", "A hex shield round its base.", "It takes half damage."),
  ("Sentinel", "A long barrel unfolds and charges.", "Fires a charged rail every 5 s.", "A kneel to set it down."))
P("railgun", "Railgun", "Tech", "active",
  ("Coils", "Coil rings spin along your arm as it charges.", "Charges 15% faster."),
  ("Vacuum Tunnel", "Rings hang along the line of fire.", "Foes in the line are knocked back."),
  ("Quick Charge", "The coils glow white.", "Charges another 15% faster."),
  ("Gauss Lance", "The line stays as a blazing teal thread that explodes along its length half a second later.",
   "The second blast hits everything in the line.", "Braced side-on, the recoil throwing the shoulder back."))
P("nanite_skin", "Nanite Skin", "Tech", "active",
  ("Hex Scales", "Hex scales ripple over you.", "A second longer."),
  ("Arc Shock", "The shock jumps.", "A melee hit's shock jumps to a second foe."),
  ("Repair", "Scales flow over your armour.", "Repairs your armour as it runs."),
  ("Living Armour", "Plates form on your shoulders and forearms; at the end they burst into a swarm.",
   "The swarm attacks foes near you.", "Arms crossed, the plates closing over them."))
P("drone_swarm", "Drone Swarm", "Tech", "ultimate",
  ("Holo Swarm", "Holographic trails behind every drone.", "A second longer."),
  ("Laser Lines", "Drones fire visible laser lines.", "Their shots pierce."),
  ("Retarget", "A reticle jumps to the next foe.", "Drones switch targets the moment one dies."),
  ("Swarm Lance", "At the end the drones form a ring and fire one combined beam.", "The beam hits everything in a line.", "A hand raised to the ring."), hidden=True)

# ---- Spirit: soft, echoing, jade ------------------------------------------------------------------------------------
P("spirit_palm", "Spirit Palm", "Spirit", "active",
  ("Lotus", "The palm print is ringed with jade lotus petals.", "Pushes 15% harder."),
  ("Through", "The print passes on through the target.", "It hits the foe behind too."),
  ("Heavy Echo", "The echo arrives as a deeper blue print.", "The echo slows."),
  ("Guardian Palm", "A giant translucent jade hand slams forward behind your strike.", "The giant hand hits everything in front of you.",
   "A full-body step into the palm."), starter=True)
P("aura", "Aura", "Spirit", "passive",
  ("Wisps", "Jade wisps circle your feet in a fight.", "Reaches a block further."),
  ("Calm", "Your aura brightens out of combat.", "Heals faster out of combat."),
  ("Drag", "The pulse ripples outward.", "The pulse slows foes."),
  ("Soul Guard", "Wisps orbit you and dart at what hits you.", "They strike back at attackers.", "None (a passive)."))
P("mend", "Mend", "Spirit", "active",
  ("Lotus Ring", "A ring of lotus light opens under you.", "Heals 10% more."),
  ("Ward", "A thin jade shell holds after.", "3 s of damage resistance after."),
  ("Shared", "The ring spreads to allies.", "Allies heal as much as you."),
  ("Bloom", "A jade lotus opens under you and petals rise.", "Everyone on it heals for 4 s.", "Palms together, then opened like a flower."))
P("third_eye", "Third Eye", "Spirit", "active",
  ("Eye Mark", "A glowing eye opens on your brow (others see it).", "A second longer."),
  ("Exposed", "Seen foes glow brighter.", "Seen foes take 10% more from you."),
  ("Secrets", "Hidden things glow too.", "Shows hidden doors, chests and traps."),
  ("All-Seeing", "An eye opens in the sky above you.", "Sees 64 blocks.", "Fingers to the brow, head tipped back."))
P("cord_lash", "Cord Lash", "Spirit", "active",
  ("Braided", "The cord braids with soul light.", "2 blocks further."),
  ("Twin Lash", "The cord splits in two.", "Hauls two foes."),
  ("Dazed", "The foe arrives with stars of soul light.", "Dazed for longer."),
  ("Soul Chain", "The hauled foe is chained to another nearby.", "They're pulled into each other.", "A two-handed whip and pull."))
P("bulwark", "Bulwark", "Spirit", "active",
  ("Runes", "Runes turn round the dome.", "A second longer."),
  ("Repel", "The dome's edge ripples outward.", "Pushes foes out harder."),
  ("Rest", "The ground inside glows jade.", "Allies inside heal slowly."),
  ("Lotus Dome", "The dome forms as overlapping petals that open outward at the end.", "The opening throws foes away.", "Arms raised and opened like petals."), hidden=True)
P("hundred_palms", "Hundred Palms", "Spirit", "ultimate",
  ("Prints", "More palm prints, layered.", "10% more palms."),
  ("Seeking", "The prints curve toward foes.", "Palms home in."),
  ("Last Palm", "The final palm is twice the size.", "The finisher launches further."),
  ("Thousand Arms", "A spirit figure rises behind you and strikes with its own arms beside yours.", "The figure's palms add 30%.", "A rooted stance, palms in a blur."), hidden=True)
P("void_maw", "Void Maw", "Spirit", "ultimate",
  ("Fangs", "A ring of shadow fangs round the maw.", "Pulls 15% harder."),
  ("Hunger", "The throat glows deeper.", "Swallows foes at a little more health."),
  ("Chew", "The maw spits debris.", "The debris hits foes round it."),
  ("The Hollow Mouth", "The maw closes with a visible bite.", "The bite heals you for what it swallows.", "An arm thrust out, the hand clenching shut."), hidden=True)
P("waking_dream", "Waking Dream", "Spirit", "active",
  ("Shimmer", "What you shape shimmers like a dream.", "Shapes 15% faster."),
  ("Bigger", "Doors and bridges come wider.", "Shapes bigger."),
  ("Lasting", "The shimmer fades slower.", "Lasts twice as long."),
  ("Lucid Architecture", "Stairs spiral, bridges arch, with rails of light.", "Shapes follow the land's lines.", "Hands shaping a curve in the air."), hidden=True)
P("storm_call", "Storm Call", "Spirit", "active",
  ("Clouds", "A dark cloud gathers before the strikes.", "Strikes 10% harder."),
  ("Chain", "Each strike jumps once.", "Strikes chain to a second foe."),
  ("More", "More forks in every bolt.", "One more strike."),
  ("Eye of the Storm", "The storm follows you for 6 s.", "It keeps striking foes near you.", "Arms raised, then pulled down."), hidden=True)
P("ember_step", "Ember Step", "Spirit", "movement",
  ("Flame Ribbons", "Ribbons of flame stream from your heels.", "The trail is 20% longer."),
  ("Lasting", "The trail burns longer.", "Burns 2 s longer."),
  ("Flare", "The trail flares when foes cross it.", "Embers burst on them."),
  ("Phoenix Step", "A firebird of flame flies the path with you.", "The firebird burns everything it passes.", "A low spinning step."), hidden=True)
P("frost_nova", "Frost Nova", "Spirit", "active",
  ("Crystals", "Ice crystals spike up round the ring.", "10% wider."),
  ("Shatter", "Frozen foes crack when hit.", "Hitting a frozen foe bursts the frost."),
  ("Frost Field", "Frost lingers on the ground.", "Slows for 3 s."),
  ("Winter Bloom", "A huge ice flower blooms round you and shatters.", "The shatter hits again.", "A stamp, then the hands opening upward."), hidden=True)
P("ghost_step", "Ghost Step", "Spirit", "movement",
  ("Soul Trail", "A pale trail hangs along the step.", "2 blocks further."),
  ("Phase Chill", "Foes you pass through frost over.", "They're slowed."),
  ("Further", "The trail stretches.", "2 more blocks."),
  ("Through the Veil", "A glowing doorway stays where you went in.", "Allies can follow through it for 3 s.", "A step through, the hand trailing."), hidden=True)
P("twin_strike", "Twin Strike", "Spirit", "active",
  ("Visible Twin", "A soul-blue outline of you repeats each blow.", "A second longer."),
  ("Stronger", "The twin's outline sharpens.", "The echo hits for 70%."),
  ("Longer", "The twin lingers.", "2 s longer."),
  ("Twin Souls", "The twin becomes a full figure beside you.", "It repeats your power casts too, at half.", "A step that splits into two."), hidden=True)


# ---- what the game runs ----------------------------------------------------------------------------------------------
# Riders: the shared mechanics (SPEC 34.3). Shares are of the hit that started them; ticks are game ticks (20 a second).
RIDERS = {"extend", "chain", "echo", "aftershock", "linger", "status", "push", "refund", "pierce", "split", "ramp", "guard"}
LINGERS = {"burn", "slow", "frost", "void", "hold", "heal", "shock"}
STATUSES = {"burn", "slow", "weak", "stun", "mark", "blind", "root", "lift"}


def extend(radius=0.0, range=0.0, duration=0.0, charge=0.0):
    """Bigger, further, longer: fractions added (0.15 = 15% more); charge is a fraction off the time to full charge."""
    return dict(k="extend", radius=radius, range=range, duration=duration, charge=charge)


def chain(n, share, reach=6.0):
    """Each hit arcs on to n more foes within reach for a share of it."""
    return dict(k="chain", n=n, share=share, reach=reach)


def echo(share, delay):
    """The hit lands again on the same foe after delay ticks, for a share."""
    return dict(k="echo", share=share, delay=delay)


def aftershock(share, delay, radius):
    """A pulse round the hit after delay ticks: every foe within radius takes a share (once per cast every 10 ticks)."""
    return dict(k="aftershock", share=share, delay=delay, radius=radius)


def linger(kind, ticks, radius, dps=0.0):
    """A ground patch at the hit that burns, slows, freezes, pulls, holds, heals allies or shocks, for ticks."""
    assert kind in LINGERS, kind
    return dict(k="linger", kind=kind, ticks=ticks, radius=radius, dps=dps)


def status(effect, ticks, amp=0):
    """A state on the foe hit: burn, slow, weak, stun, mark (glows, takes 10% more from you), blind, root, lift."""
    assert effect in STATUSES, effect
    return dict(k="status", effect=effect, ticks=ticks, amp=amp)


def push(force, lift=0.0):
    return dict(k="push", force=force, lift=lift)


def refund(energy, kill=False):
    """Energy back on a hit (or only on a kill)."""
    return dict(k="refund", energy=energy, kill=kill)


def pierce(n, share=1.0):
    """The power's own code: goes through n more foes, each for a share."""
    return dict(k="pierce", n=n, share=share)


def split(n, share):
    """The power's own code: splits into n for a share each."""
    return dict(k="split", n=n, share=share)


def ramp(share, ticks):
    """The power's own code: a held power's damage climbs by share over ticks."""
    return dict(k="ramp", share=share, ticks=ticks)


def guard(ticks, share):
    """Damage you take is cut by share for ticks after the cast."""
    return dict(k="guard", ticks=ticks, share=share)


# Look layers (SPEC 34.5): each rides one or more event kinds the power sends ("on"), at a place ("at": origin, end, hand,
# offhand, body, feet, head, back, ground, target), in one of the style's colours ("col": core, mid, edge, accent).
LOOKS = {"helix", "orbit", "sigil", "fissure", "rings", "burst", "column", "forks", "dome", "trail", "bloom", "ghost", "grow"}
SPRITES = {"glow", "spark", "flare", "bolt", "wisp", "ember", "ring", "rune", "hex", "mote", "shard", "slash", "palm", "wind",
           "smoke", "dust", "halo", "petal", "feather", "plate", "sigil"}
KINDS = {"CAST", "CHARGE", "CHARGE_END", "IMPACT", "SLAM", "RING", "NOVA", "CONE", "BEAM", "BOLT", "LIGHTNING", "STRIKE", "DASH",
         "BLINK", "TRAIL", "AFTERIMAGE", "SMOKE", "DOME", "VORTEX", "TETHER", "AURA", "PALM", "ECHO", "FLURRY", "HEAL", "FROST",
         "FIRE_TRAIL", "EXHAUST", "CONSTRUCT", "PILLAR", "HOLO", "RAIL", "SUMMON", "PSI", "GLYPH", "METEOR", "LIFT"}
PLACES = {"origin", "end", "hand", "offhand", "body", "feet", "head", "back", "ground", "target"}
COLS = {"core", "mid", "edge", "accent"}


def _look(kind, on, **kw):
    on = [on] if isinstance(on, str) else list(on)
    for o in on:
        assert o in KINDS, o
    assert kw.get("at", "origin") in PLACES, kw.get("at")
    assert kw.get("col", "mid") in COLS, kw.get("col")
    assert kw.get("sprite", "glow") in SPRITES, kw.get("sprite")
    return dict(k=kind, on=on, **kw)


def helix(on, strands=1, turns=1.0, radius=0.22, width=0.05, spin=0.25, col="mid"):
    """Strands winding round a beam, shot or rail: turns per block, spin in turns a tick."""
    return _look("helix", on, strands=strands, turns=turns, radius=radius, width=width, spin=spin, col=col)


def orbit(on, sprite="shard", n=6, r0=0.8, r1=0.3, spin=0.25, size=0.16, at="hand", tilt=0.0, life=0, col="mid"):
    """n sprites circling a place, from radius r0 to r1 over their life (life 0: the event's own length)."""
    return _look("orbit", on, sprite=sprite, n=n, r0=r0, r1=r1, spin=spin, size=size, at=at, tilt=tilt, life=life, col=col)


def sigil(on, at="hand", size=0.7, spin=0.04, face="aim", life=0, col="mid"):
    """The family's rune disc, facing the aim (a palm) or lying flat (face up)."""
    assert face in ("aim", "up")
    return _look("sigil", on, at=at, size=size, spin=spin, face=face, life=life, col=col)


def fissure(on, n=3, length=5.0, width=0.16, cool=40, along="radial", at="ground", col="mid"):
    """Glowing cracks in the ground that cool from gold to red: radial from the place, or along the event's direction."""
    assert along in ("radial", "dir")
    return _look("fissure", on, n=n, length=length, width=width, cool=cool, along=along, at=at, col=col)


def rings(on, n=1, delay=0, gap=4, r0=0.3, r1=3.0, width=0.3, life=10, at="origin", face="up", col="mid"):
    """n expanding rings, the first after delay ticks, then every gap ticks; flat (up) or facing the event's direction (dir)."""
    assert face in ("up", "dir")
    return _look("rings", on, n=n, delay=delay, gap=gap, r0=r0, r1=r1, width=width, life=life, at=at, face=face, col=col)


def burst(on, sprite, n=10, speed=0.3, life=14, size=0.2, grav=0.0, at="origin", col="mid", delay=0):
    return _look("burst", on, sprite=sprite, n=n, speed=speed, life=life, size=size, grav=grav, at=at, col=col, delay=delay)


def column(on, height=6.0, width=0.6, life=14, at="origin", col="mid", delay=0):
    return _look("column", on, height=height, width=width, life=life, at=at, col=col, delay=delay)


def forks(on, n=3, reach=3.0, life=6, at="origin", col="core"):
    """Lightning forks from the place to n points within reach (seeded, so every viewer sees the same)."""
    return _look("forks", on, n=n, reach=reach, life=life, at=at, col=col)


def dome(on, radius=2.0, life=14, at="origin", col="mid", delay=0):
    return _look("dome", on, radius=radius, life=life, at=at, col=col, delay=delay)


def trail(on, width=0.3, life=14, at="body", col="mid"):
    """A ribbon following the caster (or the place's entity) for life ticks."""
    return _look("trail", on, width=width, life=life, at=at, col=col)


def bloom(on, size=1.0, at="origin", col="core", delay=0):
    """A white-hot flash and halo."""
    return _look("bloom", on, size=size, at=at, col=col, delay=delay)


def ghost(on, delay=6, scale=0.6):
    """The event's own effect again, delay ticks later, smaller and fainter (a second impact you can see)."""
    return _look("ghost", on, delay=delay, scale=scale)


def grow(on, k=1.15):
    """The event's own meshes drawn k times bigger."""
    return _look("grow", on, scale=k)


def every(ticks, look):
    """At most once every `ticks` per caster: for layers on events a held power repeats (a beam re-sends five times a second)."""
    look["every"] = ticks
    return look


M = OrderedDict()  # power -> {level: dict(riders, looks, num, code)}


def S(pid, level, riders=(), looks=(), code=False, **num):
    """What step `level` of a power runs: riders, look layers, and the numbers its own code reads (code=True: its play change is code)."""
    assert 2 <= level <= 5
    for r in riders:
        assert r["k"] in RIDERS, r
    for lk in looks:
        assert lk["k"] in LOOKS, lk
    M.setdefault(pid, {})[level] = dict(riders=list(riders), looks=list(looks), num=num, code=bool(code or num))


import power_ladder_steps  # noqa: E402,F401  (the M rows, family by family)


def build(root=None):
    root = root or ROOT
    ids = [p["id"] for p in L]
    for pid in M:
        assert pid in ids, f"steps for an unknown power {pid}"
    powers = OrderedDict()
    lang = OrderedDict()
    for p in L:
        steps = []
        for i, s in enumerate(p["steps"]):
            lvl = i + 2
            m = M.get(p["id"], {}).get(lvl, dict(riders=[], looks=[], num={}, code=False))
            steps.append(OrderedDict(level=lvl, riders=m["riders"], looks=m["looks"], num=m["num"], code=m["code"]))
            key = f"awaken.ladder.{p['id']}.{lvl}"
            lang[key + ".name"] = s[0]
            lang[key + ".look"] = s[1]
            lang[key + ".does"] = s[2]
            if lvl == 5:
                lang[key + ".body"] = s[3]
        powers[p["id"]] = OrderedDict(family=p["fam"], starter=p["starter"], hidden=p["hidden"], steps=steps)
    jp = os.path.join(root, "src/main/resources/data/awaken/powers/ladders.json")
    with open(jp, "w") as fh:
        json.dump({"schemaVersion": 1, "__comment": "GENERATED by tools/art/specs/power_ladder_table.py: edit that, not this file.",
                   "powers": powers}, fh, indent=1)
        fh.write("\n")
    lp = os.path.join(root, "src/main/merge/powers_ladder/assets/awaken/lang/en_us.json")
    os.makedirs(os.path.dirname(lp), exist_ok=True)
    with open(lp, "w") as fh:
        json.dump(lang, fh, indent=1, ensure_ascii=False)
        fh.write("\n")
    done = sum(1 for p in L for lv in range(2, 6) if M.get(p["id"], {}).get(lv, {}).get("looks"))
    print(f"ladders.json: {len(powers)} powers, {done}/{len(L) * 4} steps with looks; lang: {len(lang)} lines")
    return jp, lp


# ---- output --------------------------------------------------------------------------------------------------------
FAMS = ["Kinetic", "Velocity", "Flight", "Energy", "Shadow", "Mind", "Architect", "Tech", "Spirit"]


def md():
    out = []
    for fam in FAMS:
        out.append(f"\n### {fam}\n")
        out.append("| Power | 2 | 3 | 4 | 5 (Awakened) |")
        out.append("|---|---|---|---|---|")
        for p in L:
            if p["fam"] != fam:
                continue
            tag = " (starter)" if p["starter"] else " (hidden)" if p["hidden"] else ""
            cells = []
            for i, s in enumerate(p["steps"]):
                c = f"**{s[0]}**: {s[1]} *{s[2]}*"
                if i == 3:
                    c += f" Body: {s[3]}"
                cells.append(c)
            out.append(f"| {p['name']}{tag} | " + " | ".join(cells) + " |")
            if p["note"]:
                out.append(f"| | *{p['note']}* | | | |")
    return "\n".join(out)


def html_rows():
    e = html.escape
    out = []
    for fam in FAMS:
        rows = [p for p in L if p["fam"] == fam]
        opened = " open" if any(p["starter"] for p in rows) else ""
        out.append(f'<details class="fam"{opened}><summary><span class="fdot {fam.lower()}"></span>{fam} <small>{len(rows)} powers</small></summary>')
        out.append('<div class="tw"><table class="ladder"><thead><tr><th>Power</th><th>Level 2</th><th>Level 3</th><th>Level 4</th><th>Level 5 · Awakened</th></tr></thead><tbody>')
        for p in rows:
            tag = '<span class="tag s">starter</span>' if p["starter"] else '<span class="tag h">hidden</span>' if p["hidden"] else ""
            tds = []
            for i, s in enumerate(p["steps"]):
                body = f'<span class="body">Body: {e(s[3])}</span>' if i == 3 and s[3] else ""
                tds.append(f'<td><b>{e(s[0])}</b><span class="look">{e(s[1])}</span><span class="does">{e(s[2])}</span>{body}</td>')
            note = f'<div class="note">{e(p["note"])}</div>' if p["note"] else ""
            out.append(f'<tr><th>{e(p["name"])} {tag}{note}</th>{"".join(tds)}</tr>')
        out.append("</tbody></table></div></details>")
    return "\n".join(out)


if __name__ == "__main__":
    # run through the module itself, so the step rows (power_ladder_steps imports this module) land in the same table
    import power_ladder_table as T
    assert len(T.L) == 63, len(T.L)
    assert len({p["id"] for p in T.L}) == 63
    for p in T.L:
        assert len(p["steps"]) == 4 and all(len(s) == 3 for s in p["steps"][:3]) and len(p["steps"][3]) == 4, p["id"]
    mode = sys.argv[1] if len(sys.argv) > 1 else "md"
    if mode == "build":
        T.build()
    else:
        print(T.md() if mode == "md" else T.html_rows())
