#!/bin/zsh
# Runs the dev client on a script and collects its screenshots (run-autopilot/screenshots/).
# Usage: tools/autopilot.sh <script> [timeout-seconds]
# The client gets its own game folder (run-autopilot/), sound muted. Its window opens behind whatever the owner is
# doing: no early loading window, no focus grab, no Dock activation (SDL hints in build.gradle's autopilot run).
cd "$(dirname "$0")/.."
SCRIPT=${1:?script}
T=${2:-900}
mkdir -p run-autopilot/config
[ -f run-autopilot/config/fml.toml ] && sed -i '' 's/^earlyWindowControl = true/earlyWindowControl = false/' run-autopilot/config/fml.toml
[ -f run-autopilot/config/fml.toml ] || echo 'earlyWindowControl = false' > run-autopilot/config/fml.toml
cp "$SCRIPT" run-autopilot/script.txt
rm -f run-autopilot/screenshots/*.png
rm -rf run-autopilot/saves   # every tour starts from a fresh world
tools/gradle.sh "$T" runAutopilot > /dev/null
echo "screenshots:"
ls -1 run-autopilot/screenshots/ 2>/dev/null
LOG=$(ls -t logs/*runAutopilot*.log | head -1)
grep -E "\[autopilot\]|Exception|ERROR" "$LOG" | grep -v "DEBUG" | tail -40
