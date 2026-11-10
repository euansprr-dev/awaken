# Grades: the pictures behind Vivid (SPEC §22 "Grades")

Each sheet has the look as built (Awaken) on the left and Vivid on the right, in the game itself, from
`tools/autopilot/steady60-grade-ci.txt` on GitHub's Mac runners.

- `vivid-1..3`: run 42865865221 at commit 00547a97b (build 66e0688df merged in), Vivid as handed over: morning, the
  sky, noon; dusk, night, and Swift under both grades; the flowers from above, golden hour, rain. Every picture in
  this run was also drawn through straight out with the steady pass on.
- `first-build-night-rain.jpg`: run 42853594714 at 3da61b546, Vivid as first built, kept for the fault it shows: the
  purple allium goes blue by moonlight. Two steps on the azure guard put that right (684fd4725, 3913eb455): red over
  blue on that flower is 0.34 as built, was 0.21 at first, and is 0.26 now.
- `look-page.jpg`: the Look page with its COLOUR GRADE row, LENS OVER FRAMES gone while the Air is on.

Euan's three reference pictures were pasted into the session and are not files here. To try a change to a grade
before a tour: `tools/lens/grade_preview.py`.
