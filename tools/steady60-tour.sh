#!/bin/zsh
# Measurement only (the look-steady-60 plan): a tour at Euan's own settings. His play copy is only read: its config
# files, video options and (by default) a copy of his latest world go into this worktree's run-autopilot/, the tour
# runs alone through slot.sh, and the frame traces, shots and log are kept under run-autopilot/results/<name>/.
# Usage: tools/steady60-tour.sh <script> [name] [timeout-seconds]
#   WORLD="<save folder name>"  his world to copy (default: New Beginnings); WORLD=none: the script makes its own
#   FOREGROUND=1                 an ordinary front app (for "display DELL fullscreen" scripts); default here
#   NOVFX=1                      take ImmersiveVFX's jar out after the pack install (the cost of the mod asleep)
#   JFR=1                        record the client with Java Flight Recorder (profile settings)
#   SLOT=light                   a shots-only tour (slot.sh light: one build may run beside it); default: tour
cd "$(dirname "$0")/.."
PLAY=/Volumes/MEDIA/Awaken-play/run-pack
SCRIPT=${1:?script}
NAME=${2:-${SCRIPT:t:r}-$(date +%m%d-%H%M)}
T=${3:-1500}
OUT=$PWD/run-autopilot/results/$NAME
mkdir -p run-autopilot/config $OUT
rsync -a --exclude '*.bak' --exclude 'awaken-immersivevfx-kept.json' $PLAY/config/ run-autopilot/config/
rm -f run-autopilot/config/awaken-immersivevfx-kept.json
# his video options, but windowed until the script says where to go
sed 's/^fullscreen:true/fullscreen:false/' $PLAY/options.txt > run-autopilot/options.txt
rm -rf run-autopilot/perf-traces
export XMX=8G ZGC=1 PACK=perf,feel,sound,comfort,world RD=${RD:-16} TECTONIC=awakened FOREGROUND=${FOREGROUND-1}
[ "${WORLD:-New Beginnings}" != none ] && export WORLD_FROM="$PLAY/saves/${WORLD:-New Beginnings}"
[ -n "$JFR" ] && export JAVA_TOOL_OPTIONS="-XX:StartFlightRecording=settings=profile,maxsize=400m,filename=$OUT/p-%p.jfr"
uptime > $OUT/load-before.txt
ps -axo pcpu,rss,command | sort -rn | head -15 | cut -c1-160 >> $OUT/load-before.txt
if [ -n "$NOVFX" ]; then
  # the pack install happens inside autopilot.sh; a wrapper around gradle would be heavier than this small hook
  export AWAKEN_STEADY60_NOVFX=1
fi
SLOT_WHO=look-steady-60 /Volumes/MEDIA/Awaken-carillon/tools/carillon/slot.sh ${SLOT:-tour} tools/autopilot.sh "$SCRIPT" "$T" > $OUT/tour.txt 2>&1
uptime > $OUT/load-after.txt
cp -R run-autopilot/perf-traces $OUT/ 2>/dev/null
cp run-autopilot/screenshots/*.png $OUT/ 2>/dev/null
LOG=$(ls -t logs/*runAutopilot*.log | head -1)
cp "$LOG" $OUT/client.log
grep -E "\[autopilot\] (perf|info|vfx|display|pixels|look|settle)|\[pixels\] gpu|\[look\]" "$LOG" > $OUT/summary.txt
echo "results: $OUT"
tail -5 $OUT/tour.txt
