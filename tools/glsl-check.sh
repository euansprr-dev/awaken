#!/bin/zsh
# Checks Awaken's shaders without the game (Far Sight 2): inlines #include <minecraft:...> from the vanilla assets in
# /Volumes/MEDIA/Awaken/.ref/assets and <awaken:...> from this checkout, then compiles each with glslangValidator for
# Vulkan 1.2 (bindings and locations auto-mapped, as the game's own compiler maps them).
# Usage: tools/glsl-check.sh src/main/resources/assets/awaken/shaders/core/farsight_terrain.vsh [more...]
cd "$(dirname "$0")/.."
TMP=$(mktemp -d)
ok=0
for f in "$@"; do
  out=$TMP/${f:t}
  python3 -I - "$f" "$out" <<'PY'
import re, sys, os
src, dst = sys.argv[1], sys.argv[2]
roots = {"minecraft": "/Volumes/MEDIA/Awaken/.ref/assets/assets/minecraft/shaders/include",
         "awaken": "src/main/resources/assets/awaken/shaders/include"}
seen = set()
def expand(text):
    def rep(m):
        ns, name = m.group(1), m.group(2)
        key = ns + ":" + name
        if key in seen: return ""
        seen.add(key)
        return expand(open(os.path.join(roots[ns], name)).read())
    return re.sub(r'#include\s*<([a-z_]+):([^>]+)>', rep, text)
open(dst, "w").write(expand(open(src).read()))
PY
  stage=${f:e}; [[ $stage == vsh ]] && stage=vert || stage=frag
  if glslangValidator -V --target-env vulkan1.2 --auto-map-bindings --auto-map-locations -S $stage -o /dev/null $out > $TMP/log 2>&1; then
    echo "ok   $f"
  else
    echo "FAIL $f"; grep -E "ERROR|error" $TMP/log | head -20; ok=1
  fi
done
rm -rf $TMP
exit $ok
