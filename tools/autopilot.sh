#!/bin/zsh
# Runs the dev client on a script and collects its screenshots (run-autopilot/screenshots/).
# Usage: tools/autopilot.sh <script> [timeout-seconds]
# The client gets its own game folder (run-autopilot/), sound muted. Its window opens behind whatever the owner is
# doing: no early loading window, no focus grab, no Dock activation (SDL hints in build.gradle's autopilot run).
# Experiments (all optional; unset means Awaken alone on the game's default renderer):
#   PACK=perf,horizon       install those pack profiles first (tools/pack.py); unset removes any pack mods
#   BACKEND=vulkan|opengl   graphics API for this run
#   XMX=6G  ZGC=1           client heap size and garbage collector
#   LENS=off|swift|cinematic  Awaken's light layer (unset: whatever run-autopilot's client config says)
#   DH=swift|cinematic|default  Distant Horizons settings (pack/dh/); unset leaves them
#   RD=<chunks>             vanilla render distance for this run
#   TECTONIC=<preset>       Tectonic settings from pack/tectonic/<preset>.json (unset: Tectonic's defaults)
#   CITY=on                 ask for the city basin before Carillon exists (terrain's dev switch)
cd "$(dirname "$0")/.."
SCRIPT=${1:?script}
T=${2:-900}
mkdir -p run-autopilot/config
[ -f run-autopilot/config/fml.toml ] && sed -i '' 's/^earlyWindowControl = true/earlyWindowControl = false/' run-autopilot/config/fml.toml
[ -f run-autopilot/config/fml.toml ] || echo 'earlyWindowControl = false' > run-autopilot/config/fml.toml
if [ -n "$PACK" ]; then
  python3 -I tools/pack.py install run-autopilot "$PACK" || exit 1
elif [ -f run-autopilot/mods/.awaken-pack.json ]; then
  python3 -I tools/pack.py remove run-autopilot > /dev/null || exit 1
fi
OPTS=run-autopilot/options.txt
touch $OPTS
sed -i '' '/^preferredGraphicsBackend:/d' $OPTS
echo "preferredGraphicsBackend:\"${BACKEND:-default}\"" >> $OPTS
# RD=<chunks> sets the vanilla render distance for this run (Distant Horizons draws the rest); 16 when unset
sed -i '' '/^renderDistance:/d' $OPTS
echo "renderDistance:${RD:-16}" >> $OPTS
# no tutorial toasts over the shots
sed -i '' '/^tutorialStep:/d' $OPTS
echo "tutorialStep:none" >> $OPTS
# LENS=off|swift|cinematic sets Awaken's light layer for this run (unset leaves the client config as it is)
if [ -n "$LENS" ]; then
  CFG=run-autopilot/config/awaken-client.toml
  touch $CFG
  sed -i '' '/^lens = /d' $CFG
  echo "lens = \"${(U)LENS}\"" >> $CFG
fi
# TECTONIC=<preset> picks the terrain preset; unset restores Tectonic's defaults so a preset never leaks into a later run
if [ -n "$TECTONIC" ]; then
  [ -f "pack/tectonic/$TECTONIC.json" ] || { echo "no preset pack/tectonic/$TECTONIC.json"; exit 1; }
  cp "pack/tectonic/$TECTONIC.json" run-autopilot/config/tectonic.json
elif [ -f run-autopilot/config/tectonic.json ]; then
  cp pack/tectonic/default.json run-autopilot/config/tectonic.json
fi
# CITY=on: terrain's dev switch for the city basin (config/awaken-terrain-dev.properties); unset removes it
if [ "$CITY" = on ]; then echo "city=on" > run-autopilot/config/awaken-terrain-dev.properties
else rm -f run-autopilot/config/awaken-terrain-dev.properties; fi
# DH=swift|cinematic|default rewrites Distant Horizons' settings for this run
[ -n "$DH" ] && { python3 -I tools/pack.py dhconfig run-autopilot "$DH" > /dev/null || exit 1; }
GRADLE_ARGS=()
[ -n "$XMX" ] && GRADLE_ARGS+=("-Pxmx=$XMX")
[ -n "$ZGC" ] && GRADLE_ARGS+=("-Pzgc")
cp "$SCRIPT" run-autopilot/script.txt
rm -f run-autopilot/screenshots/*.png
rm -rf run-autopilot/saves   # every tour starts from a fresh world
tools/gradle.sh "$T" runAutopilot "${GRADLE_ARGS[@]}" > /dev/null
echo "screenshots:"
ls -1 run-autopilot/screenshots/ 2>/dev/null
LOG=$(ls -t logs/*runAutopilot*.log | head -1)
grep -E "\[autopilot\]|Exception|ERROR" "$LOG" | grep -v "DEBUG" | tail -40
