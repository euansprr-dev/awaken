#!/bin/zsh
# Renders one scene of extras.html: docs/ui-unify/proto/shootx.sh <out.png> <scene>
cd "$(dirname "$0")"
"/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" --headless=new --disable-gpu --hide-scrollbars --allow-file-access-from-files \
  --force-device-scale-factor=${SCALE:-1} --window-size=1708,960 --virtual-time-budget=2500 --screenshot="$1" "file://$PWD/extras.html#$2" 2>/dev/null | tail -1
