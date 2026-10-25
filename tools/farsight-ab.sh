#!/bin/zsh
# Far Sight's decisive A/B (docs/perf/far-sight/REPORT.md): one far-terrain setup per call, each its own tour of
# tools/autopilot/farsight-ab.txt through the machine's shared lock (Carillon's slot.sh), on Vulkan with Lens on.
# Usage: tools/farsight-ab.sh vanilla16|vanilla32|dh16|farsight16|farsight-gl
#   farsight-gl is the OpenGL run that only confirms Far Sight works there (shots, no frame times worth comparing).
# Writes docs/perf/far-sight/<setup>.txt (perf, info and Far Sight lines) and half-size JPEG shots in
# docs/perf/far-sight/shots/<setup>/.
cd "$(dirname "$0")/.."
SETUP=${1:?vanilla16|vanilla32|dh16|farsight16|farsight-gl}
SLOT=/Volumes/MEDIA/Awaken-carillon/tools/carillon/slot.sh
TMP=$(mktemp -d)
cp tools/autopilot/farsight-ab.txt $TMP/ab.txt
MODE=tour
case $SETUP in
  vanilla16) RUN=(PACK=perf LENS=swift RD=16) ;;
  vanilla32) RUN=(PACK=perf LENS=swift RD=32) ;;
  dh16) RUN=(PACK=perf,horizon DH=swift LENS=swift RD=16) ;;
  farsight16) RUN=(PACK=perf LENS=swift RD=16); sed -i '' 's/^farsight off$/farsight on/' $TMP/ab.txt ;;
  farsight-gl) RUN=(PACK=perf LENS=swift RD=16 BACKEND=opengl); sed -i '' 's/^farsight off$/farsight on/' $TMP/ab.txt; MODE=light ;;
  *) echo "unknown setup $SETUP"; exit 1 ;;
esac
env SLOT_WHO=far-sight $RUN $SLOT $MODE tools/autopilot.sh $TMP/ab.txt 900
LOG=$(ls -t logs/*runAutopilot*.log | head -1)
OUT=docs/perf/far-sight
mkdir -p $OUT/shots/$SETUP
rm -f $OUT/shots/$SETUP/*.jpg
grep -E '\[autopilot\] (perf|info|farsight)|\[farsight\]|\[lens\]' $LOG | sed -E 's/^.*\] \[[^]]*\/[A-Z]+\] \[[^]]*\]: //' > $OUT/$SETUP.txt
for f in run-autopilot/screenshots/*.png(N); do
  sips -s format jpeg -s formatOptions 82 -Z 1440 "$f" --out "$OUT/shots/$SETUP/${f:t:r}.jpg" > /dev/null
done
echo "$SETUP: $(grep -c 'perf ' $OUT/$SETUP.txt) perf lines, $(ls $OUT/shots/$SETUP | wc -l | tr -d ' ') shots"
rm -rf $TMP
