#!/bin/zsh
# Runs the dev client on a script and collects its screenshots (run-autopilot/screenshots/).
# Usage: tools/autopilot.sh <script> [timeout-seconds]
# The client gets its own game folder (run-autopilot/), sound muted; it still opens a window (ask the owner first).
cd "$(dirname "$0")/.."
SCRIPT=${1:?script}
T=${2:-900}
mkdir -p run-autopilot
cp "$SCRIPT" run-autopilot/script.txt
rm -f run-autopilot/screenshots/*.png
rm -rf run-autopilot/saves   # every tour starts from a fresh world
tools/gradle.sh "$T" runAutopilot > /dev/null
echo "screenshots:"
ls -1 run-autopilot/screenshots/ 2>/dev/null
LOG=$(ls -t logs/*runAutopilot*.log | head -1)
grep -E "\[autopilot\]|Exception|ERROR" "$LOG" | grep -v "DEBUG" | tail -40
