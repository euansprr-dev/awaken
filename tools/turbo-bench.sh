#!/bin/zsh
# Turbo's launch-time experiments (SPEC §16): runs tools/autopilot/turbo.txt once per variant, back to back, on the
# same world and seed with the frame cap lifted, and keeps each run's [turbo] and perf lines (and its shots) in
# docs/perf/turbo/<name>/. Wrap it in the shared slot: slot.sh tour tools/turbo-bench.sh <name> <variant>...
# Variants: base (every launch experiment off), cache-clear (pipeline cache from empty: fills it), cache (pipeline
# cache as the last run left it), opt (optimised shader compiles), mvk (the MoltenVK settings below), all, gl (OpenGL:
# Turbo must stay off and the game must look the same).
cd "$(dirname "$0")/.."
NAME=${1:?name}; shift
OUT=docs/perf/turbo/$NAME
mkdir -p $OUT run-autopilot
MVK_SET=USE_METAL_ARGUMENT_BUFFERS=1,SHOULD_MAXIMIZE_CONCURRENT_COMPILATION=1,USE_MTLHEAP=1
OPTS=run-autopilot/options.txt
touch $OPTS
sed -i '' '/^maxFps:/d; /^enableVsync:/d' $OPTS
printf 'maxFps:260\nenableVsync:false\n' >> $OPTS
i=0
for V in "$@"; do
  i=$((i+1))
  T=pipelineCache=off,shaderOpt=off; M=""; B=vulkan
  case $V in
    base) ;;
    cache-clear) rm -rf run-autopilot/awaken-turbo; T=pipelineCache=on,shaderOpt=off ;;
    cache) T=pipelineCache=on,shaderOpt=off ;;
    opt) T=pipelineCache=off,shaderOpt=on ;;
    mvk) M=$MVK_SET ;;
    all) T=pipelineCache=on,shaderOpt=on; M=$MVK_SET ;;
    gl) B=opengl ;;
    *) echo "unknown variant $V"; exit 1 ;;
  esac
  BACKEND=$B TURBO=$T MVK=$M tools/autopilot.sh tools/autopilot/turbo.txt 900 > /dev/null
  LOG=$(ls -t logs/*runAutopilot*.log | head -1)
  grep -E '\[turbo\]|\[autopilot\] (perf|screenshot|info)|Exception|ERROR' "$LOG" | grep -v DEBUG > $OUT/$i-$V.txt
  for f in run-autopilot/screenshots/*.png(N); do cp "$f" "$OUT/$i-$V-${f:t}"; done
  echo "== $i $V"
  grep -E 'bench|\] on:|\] off:|turbo\] demo|perf demo' $OUT/$i-$V.txt
done
