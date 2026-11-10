#!/bin/zsh
# local preview of the built page inside the publish skeleton (docs/movement/src/pagetest.html, not committed)
cd "$(dirname "$0")/.."
python3 build_page.py "$@" && python3 - <<'PY'
s=open('page.html').read().replace('shots/','../shots/')
open('src/pagetest.html','w').write('<!doctype html><html><head><meta charset="utf8"><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover"><style>body{margin:0;font:14px system-ui}img{max-width:100%}[hidden]{display:none!important}</style></head><body>'+s+'</body></html>')
PY
