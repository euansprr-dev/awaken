#!/bin/zsh
# A gate's real blocks, seen from its tour's own cameras, without starting the game (SPEC 50.8). About three minutes.
# Usage: tools/preview/preview.sh tools/autopilot/realm-amber.txt [seeds, default 1; e.g. 1,2,3]
# Pictures, report.txt and sheet.png land in run-preview/<tour>/. volume.sha holds a hash of every block per seed: when
# a change to the kit shouldn't change a land, the hash must not move.
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
NAME="$(basename "$1" .txt)"
export AWAKEN_PREVIEW="$ROOT/$1"
export AWAKEN_PREVIEW_OUT="$ROOT/run-preview/$NAME"
export AWAKEN_PREVIEW_ROOT="$ROOT"
export AWAKEN_PREVIEW_SEEDS="${2:-1}"
cd "$ROOT"
rm -rf "$AWAKEN_PREVIEW_OUT"
/Volumes/MEDIA/Awaken-carillon/tools/carillon/slot.sh build tools/gradle.sh 560 runGameTestServer -Ptests=realm_preview | tail -n 3
if [ ! -f "$AWAKEN_PREVIEW_OUT/report.txt" ]; then
  echo "no preview came out; the Gradle log in logs/ says why"
  exit 1
fi
python3 "$ROOT/tools/preview/sheet.py" "$AWAKEN_PREVIEW_OUT"
cat "$AWAKEN_PREVIEW_OUT/report.txt"
echo "volume: $(cat "$AWAKEN_PREVIEW_OUT/volume.sha" | tr '\n' ' ')"
echo "sheet: $AWAKEN_PREVIEW_OUT/sheet.png"
