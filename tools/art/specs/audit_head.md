# Sound audit

Every sound event of ours, judged against the best we have (SPEC §66). Written by `python3 tools/art/sound_audit.py audit`
from the tables in `tools/art/specs/audit_*.py`, so it is always the game as built. Do not edit it by hand.

Asked for by Euan on 11 Nov 2026: "double check basically all the custom sounds we have and do a full audit and make
sure they're all on par with the best sounds we have". The same day, on mining: "I hate the ding ding ding when mining
ore, even vanilla sounds better".

**The bar.** The armour sounds (SPEC §44.12) and the Forged Pick (SPEC §49): Minecraft's own recordings, named in
`sounds.json`, layered in code. A sound is at the bar when it is a recording of the real thing, belongs in Minecraft's
world, and has no narrow steady high tone at its peak (`docs/powers-aaa/taster/ding_check.py`). A sound built by
synthesis (sine partials, filtered noise, voiced formants) is below it, however well its loudness and brightness match.

**The count.** {total} events: {bar} at the bar, {fixed} remade in this audit, {left} left as they are (each row says why).

**How to read a row.** *Source before* is where the sound came from before the audit. *Fix, as built* is the recipe the
game plays now: each recording by its name in the game, `×` its volume and `@` its pitch, `+ms` when it starts late.
A recipe's first recording is the event in `sounds.json`; the rest are layered by `core.audio.client.SoundLayers`.
Every remade sound has up to three picks on the listening page (`python3 tools/art/sound_audit.py listen`); the row
shows the one in the game.
