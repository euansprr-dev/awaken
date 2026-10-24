# The Ritual of the Bell — the awakening as a cinematic

Status: **requested by Euan on 2026-10-24.** In his words: "make the awakening more dramatic, similar to the actual
anime where some INSANE GODLIKE boss basically puts you into a ritual and kills you. And if you are in multiplayer, then
it waits for every player to enter this first room before this scene starts. Like REALLY MAGICAL EXPERIENCE SUPER
CINEMATIC FEELING. If this ties well with the shaders that would be DOPE too. Also when you get the option to accept,
lets make that black effect stay the whole time, so you basically can't really see far."

The same day he decided: **the story starts together, and friends share one Hearth as members, which can later grow
into several.** And then: "I never make it past the first few mobs LOL because I don't have any armor or anything yet. So
I always awaken from dying in the first room." So **the hall is the Bell Crypt's first and only room, and nothing in it
can kill you until the End.**

It is an original homage, not a copy. Awaken's own words, rules, god and hall; nothing lifted from the anime.

## Where it sits in today's prologue

Today (`story/Prologue.java`):
1. About 2 minutes in, the Signal glitches, a pillar rises, and the scripted gate `awaken:bell_crypt` opens 55 blocks
   ahead.
2. At half health the Lord calls four statues ("The statues move").
3. A killing blow is cancelled, 3 s of Blindness plays, and you're taken to the Stillpoint for "Will you accept?".

The Ritual replaces the crypt itself: the story's gate is one floor, a small antechamber you arrive in and, through its
doors, the hall. No ordinary monsters, no corridors, nothing before it. From the moment you are inside until the Seated
One's hand closes, every hit leaves you at least half a heart (it still hurts, throws you and flashes); only the hand is
the death, for everyone at once. Old worlds in the middle of the prologue keep their three-floor crypt and today's death.
The acceptance keeps its words, now in darkness that lasts the whole prompt.

## Storyboard (about 2 minutes)

| Beat | What you see and hear | What you do |
|---|---|---|
| **0 · The Hall** | You arrive in the antechamber; its doors open on a cathedral: black stone, aged gold, twelve kneeling statues lining the nave, a rune ring around an altar, a great bronze bell hanging above it. At the far end, carved into the wall, **the Seated One**: a colossal statue (about 24 blocks seated), its face serene and its eyes closed. | Walk in. **Multiplayer:** players gather inside the doors; nothing starts until every unawakened player in the story is inside, or 90 s after the first one came in. The Now line reads "Waiting for 2 hunters…". |
| **1 · The Doors** | When the last player crosses, the doors grind shut. Every flame dies and it's black. Then candles light down the nave one by one toward the throne. Lens turns to the **Ritual** look: crushed blacks, gold highlights, heavy vignette, flames blooming. | Movement is free, and the camera is drawn gently toward the throne. |
| **2 · The Rules** | The bell tolls once. Words carve themselves into the wall in light, one at a time, in the System's type: **"BOW."** · **"RING."** · **"BE JUDGED."** | **BOW:** crouch within 6 s. Anyone standing is struck by the nearest statue, a terrifying but non-lethal hit, and its eyes flash red. **RING:** three plates around the altar glow in turn as the bell sounds; stand on each in time, or the statues turn their heads toward you. |
| **3 · The Waking** | The Seated One's eyes **open**: two blazing points with enormous bloom. Its head turns slowly to the nearest player. The hall shakes, dust falls, and the twelve statues stand. | Brace. |
| **4 · The Judgement** (about 40 s) | Its gaze sweeps the floor as a red line that fills, then fires. Statues advance with telegraphed swings. Segments of the rune ring open into pits. | Survive: read the telegraphs and move. A new player has no armour and no powers, so it is about reading and dodging, never fighting, and nothing here can kill. It teaches the combat language. Escape is impossible; the doors are sealed. |
| **5 · The End** | It raises one hand. The hall's light drains into its palm until everything is white. "[ERROR]" flickers. The hand closes. Black, and one bell toll. | You die, scripted: no item loss, as today. |
| **6 · The Void** | Black, and **it stays black the whole prompt** (Euan's ask). The game holds you blind and Lens adds the **Void** look: nothing visible past arm's length, a slow heartbeat, a few motes of blue light drifting. The System card: "You have met the conditions. Will you accept?" Both buttons accept. | Accept. **Multiplayer:** each player answers alone. Nobody waits for anybody here. |
| **7 · The Awakening** | A blue flash. The black **tears open from the centre** (a Lens transition, about 1.5 s) and you wake at the Crypt's mouth in the overworld. The existing reveal plays (the Awakened layer steps out), and far away the bell sounds once. | Play. |

## How it's built

- **The hall:** a hand-built set piece, the prologue gate's first and only room (behind a small antechamber where you
  arrive). It is built in code, like the gate rooms (Kit/Pieces), so it can be versioned and tested. The plan is marked
  by its boss (`awaken:the_seated_one`) on a one-floor scripted Bell Crypt, never by a layout or a twist, so nothing else
  can roll it.
- **The Seated One:** a new immobile, invulnerable boss entity with a colossal custom model, made in the hero-rig
  pipeline (`tools/art/specs`). Animations: the eyes open (glow layer), the head turns, the hand rises, the light
  gathers, the hand closes. It needs a large render box and a long tracking range.
- **The ritual director:** a server-side timeline per hall: waiting, beats, the rule checks (crouch, plates),
  telegraphed attacks, and the scripted death for everyone at once.
  - It reuses the gate run's telegraph and the Bell Crypt's statue roster.
  - It is saved (schemaVersion), so a relog in the middle resumes the right beat.
- **Client cinematics:**
  - title words in the System font, camera shake and sounds
  - a gentle camera pull toward the throne at the doors
  - **Lens overrides:** a small API, `LensClient.override(look, fadeTicks)`, driven by one ritual payload. New looks
    `awaken:ritual` and `awaken:void`, plus a **tear** transition (a radial reveal from black) driven by a new parameter
    texel.
- **The black effect:** Blindness held for the whole of ACCEPTING (re-applied while the prompt is open, removed on
  Accept), with the Lens Void look on top. This part is small and ships first.
- **Multiplayer: the story starts together.**
  - The first unawakened player to trigger the pillar opens one Bell Crypt for every unawakened player online.
  - The hall waits for all of them. Someone who disconnects is no longer waited for.
  - Someone who joins after the ritual started gets their own Crypt.
- **Sounds,** synthesised with the audio pipeline: the bell toll, stone doors, the candle line, the eyes-open drone,
  the gaze charge and fire, the heartbeat, the tear.

## Tests

- **GameTests:**
  - waiting with two players (one inside, one outside, then both inside)
  - a disconnect while waiting
  - each beat advances
  - the rule checks (crouch, plates)
  - the scripted death sends everyone to acceptance
  - Blindness is held until Accept
  - Accept restores you and returns you to the overworld
  - a relog mid-ritual resumes the right beat
- **Tour:** `tools/autopilot/ritual.txt` photographs every beat, first and third person, then a Lens look check of
  Ritual, Void and the tear.
- **Frame time:** the hall with the Seated One and twelve statues must hold the Swift budget. Measured with
  `perf-pack`.

## Ownership and order

The story, gates and mobs modules belong to the v2 integrator; the build follows its rules, as Awakened Body does. The
order:
1. The black effect held through Accept, together with the Lens Void look.
2. The hall and the director with placeholder statues, multiplayer waiting included.
3. The Seated One's model and animations.
4. Cinematics, sounds and the Lens Ritual look and tear.
5. Polish and the tour.

About 4–7 days.
