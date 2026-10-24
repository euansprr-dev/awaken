#!/bin/zsh
# Terrain studio run (docs/SPEC.md §15): tools/terrain/studio.sh <preset|vanilla> [survey] [timeout-seconds]
# "survey" runs only the five surveys (tools/autopilot/terrain-survey.txt, no shots), for quick preset comparisons.
# Runs tools/autopilot/terrain-studio.txt with that Tectonic preset (pack/tectonic/<preset>.json) or with no terrain
# mod ("vanilla"), then collects the survey grids and shots into run-studio/<preset>/ and prints the numbers.
# One client at a time on this Mac: the run holds /tmp/awaken-autopilot.lock.
cd "$(dirname "$0")/../.."
P=${1:?preset or vanilla}
TOUR=tools/autopilot/terrain-studio.txt
if [ "$2" = survey ]; then TOUR=tools/autopilot/terrain-survey.txt; shift; fi
T=${2:-1800}
until mkdir /tmp/awaken-autopilot.lock 2>/dev/null; do echo "waiting for another tour..."; sleep 30; done
trap 'rmdir /tmp/awaken-autopilot.lock 2>/dev/null' EXIT
rm -rf run-autopilot/terrain
if [ "$P" = vanilla ]; then
  PACK=perf RD=${RD:-24} tools/autopilot.sh "$TOUR" "$T" > /dev/null
else
  PACK=perf,world TECTONIC=$P RD=${RD:-24} tools/autopilot.sh "$TOUR" "$T" > /dev/null
fi
OUT=run-studio/$P
rm -rf "$OUT"; mkdir -p "$OUT"
cp run-autopilot/terrain/*.json "$OUT"/ 2>/dev/null
cp run-autopilot/screenshots/studio_*.png "$OUT"/ 2>/dev/null
LOG=$(ls -t logs/*runAutopilot*.log | head -1)
grep -E "\[terrain\]|\[autopilot\] info backend" "$LOG" | sed 's/^.*\]: //' > "$OUT/log.txt"
cat "$OUT/log.txt"
python3 -I tools/terrain/survey.py stats "$OUT"/s*.json
