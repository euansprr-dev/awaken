#!/bin/zsh
# Runs one autopilot script on OpenGL then on Vulkan, back to back, with the pack's perf, horizon and feel profiles,
# Distant Horizons on Swift, Lens on Swift and the frame cap lifted; keeps each run's shots and log under
# docs/perf/vulkan/<name>-<backend>/. Wrap it in the shared slot: slot.sh tour tools/vulkan-check.sh <script> <name>
cd "$(dirname "$0")/.."
SCRIPT=${1:?script}
NAME=${2:?name}
OUT=docs/perf/vulkan
mkdir -p $OUT run-autopilot
for B in opengl vulkan; do
  OPTS=run-autopilot/options.txt
  touch $OPTS
  sed -i '' '/^maxFps:/d; /^enableVsync:/d' $OPTS
  printf 'maxFps:260\nenableVsync:false\n' >> $OPTS
  BACKEND=$B PACK=perf,horizon,feel DH=swift LENS=swift tools/autopilot.sh "$SCRIPT" 1500
  D=$OUT/$NAME-$B
  rm -rf $D && mkdir -p $D
  cp run-autopilot/screenshots/*.png $D/ 2>/dev/null
  cp "$(ls -t logs/*runAutopilot*.log | head -1)" $D/run.log
  echo "kept $(ls $D/*.png 2>/dev/null | wc -l | tr -d ' ') shots in $D"
done
