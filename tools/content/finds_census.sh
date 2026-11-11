#!/bin/zsh
# Counts what a real world places, without a client (SPEC §64.9): a dedicated dev server (headless, seed 4242 unless
# run/server.properties says otherwise) counts every monument within <rings> cells of the start into its log and stops.
#
# With REAL=1 it also writes about twenty of the standing ones on the generator's own ground into run/finds-real:
#   python3 tools/content/finds_render.py run/finds-real <out dir>      (drawings: how each meets its land)
#
# With WORLD=1 it makes the land under each of them instead and writes it as the game has it, trees, snow and whatever
# else stands there, into run/finds-world, and says what is wrong where each stands (holes in its ground, bare earth,
# how near a settlement or another landmark is):
#   FINDS_VIEWS=se,nw FINDS_JOBS=3 python3 tools/content/finds_render.py run/finds-world <out dir>
# ONLY=<kind ids, commas> takes the nearest standing one of each of those kinds instead of the sample.
#
# usage: [REAL=1 | WORLD=1] [ONLY=clock,mast] tools/content/finds_census.sh [rings, default 22 = 17 km square]
# About five minutes (ten with WORLD=1); one Java. Wrap it in the machine's build slot like any Gradle run.
cd "$(dirname "$0")/../.."
mkdir -p run
[ -f run/server.properties ] || printf 'level-seed=4242\nlevel-name=census-4242\nserver-ip=127.0.0.1\nserver-port=25599\nonline-mode=false\nmax-tick-time=-1\nview-distance=4\nsimulation-distance=4\n' > run/server.properties
MODE=${WORLD:+world}
MODE=${MODE:-${REAL:+1}}
if [ -n "$WORLD" ]; then
  # land made by an earlier run would be read back as it was made then: a change to the feature needs new chunks
  LEVEL=$(sed -n 's/^level-name=//p' run/server.properties)
  rm -rf "run/${LEVEL:-world}/dimensions/minecraft/overworld/region" "run/${LEVEL:-world}/dimensions/minecraft/overworld/entities" "run/${LEVEL:-world}/dimensions/minecraft/overworld/poi" run/finds-world
fi
env AWAKEN_FINDS_CENSUS=${1:-22} ${MODE:+AWAKEN_FINDS_REAL=$MODE} ${ONLY:+AWAKEN_FINDS_ONLY=$ONLY} JAVA_TOOL_OPTIONS=-Djava.awt.headless=true tools/gradle.sh 1700 runServer --no-daemon > /dev/null 2>&1
L=$(ls -t logs/gradle-*-runServer.log | head -1)
grep -E "\[finds\] (census|dump)" "$L" | sed -E 's/^.*\[finds\] (census )?//'
