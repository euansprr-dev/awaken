# K menu mockup renderer

Draws the holo K panel the way `HoloCanvas`/`HoloRender` do (panel units at whole framebuffer pixels a unit,
Minecraft's own font and glyph widths, the glass, tint, halo, brackets, text as light), fitted to the real panel's
perspective, over real tour frames. `00_calibration.jpg` checks it against the game.

    python3 docs/k-menu/mock/setup.py                   # font, icons and backgrounds, local only (gitignored)
    (cd docs/k-menu/mock && python3 -m http.server 8765 --bind 127.0.0.1 &)
    node --experimental-websocket docs/k-menu/mock/shoot.mjs /tmp/k-menu-out after powers journal map:1440:900

Scenes are in `scenes.js` (`before`, `after`, `spend_pending`, `spend_done`, `dense`, `empty`, `powers`, `journal`,
`map`, `switch_a`, `switch_b`, `dirA`, `dirC`, `t_cloud`, `t_grass`, `t_night`, `calib`). The page design is in
`after.js` (rail, Status, Powers, Journal, Map) and today's panel in `before.js`.
