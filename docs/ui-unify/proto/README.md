# The band's stage: prototype and mock renderer

Isolated HTML. Nothing here is game code and nothing here is loaded by the mod.

    /Volumes/MEDIA/ui-unify-work/venv/bin/python docs/ui-unify/proto/setup_font.py    # the game's font as a local TTF (never committed)
    /Volumes/MEDIA/ui-unify-work/venv/bin/python docs/ui-unify/proto/prep_assets.py   # item art, cubes, crops of tour shots (gitignored)
    docs/ui-unify/proto/shoot.sh out.png '{"place":"tasks","set":"dense"}'            # one state of the prototype, 1708 x 960
    docs/ui-unify/proto/shootx.sh out.png kit                                         # extras.html: kit, glance_before, glance_after, hall_after, dirA
    python3 docs/ui-unify/build_page.py                                               # page.html and files.json from src/page.src.html

- `proto.css`, `proto.js`, `data.js`: the stage. State comes from the hash as JSON (`place`, `task`, `choice`, `chosen`, `tracked`,
  `set` = `dense` | `fresh`, `hc`, `open`, `room`, `power`, `find`, `shelf`, `medal`, `placed`).
- `assets/` is rebuilt by `prep_assets.py` from the repo's own textures, the gitignored `.ref` folder and CI tour shots; it is
  published with the approval page but not committed. The font is used for the rendered frames only and is never published.
- The tours behind the real shots: `tools/autopilot/ui-unify-baseline.txt` and `ui-unify-moment.txt` (run with `tools/tour-ci.sh`).
