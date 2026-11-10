#!/bin/zsh
# Renders one state of the prototype to a PNG at the sheet's own size, twice the pixels (the Mac's Retina):
#   docs/ui-unify/proto/shoot.sh <out.png> '<state json>'
cd "$(dirname "$0")"
OUT=$1; STATE=${2:-'{}'}
ENC=$(python3 -c "import urllib.parse,sys; print(urllib.parse.quote(sys.argv[1]))" "$STATE")
"/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" --headless=new --disable-gpu --hide-scrollbars --allow-file-access-from-files \
  --force-device-scale-factor=${SCALE:-1} --window-size=1708,960 --virtual-time-budget=2500 --screenshot="$OUT" "file://$PWD/index.html?still#$ENC" 2>/dev/null | tail -1
