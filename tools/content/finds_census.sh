#!/bin/zsh
# Counts what a real world places, without a client (SPEC §62.9): a dedicated dev server (headless, seed 4242 unless
# run/server.properties says otherwise) counts every monument within <rings> cells of the start into its log and stops.
# With REAL=1 it also writes about twenty of the standing ones on the generator's own ground into run/finds-real, for
#   python3 tools/content/finds_render.py run/finds-real <out dir>   (drawings: how each meets its land)
# usage: tools/content/finds_census.sh [rings, default 22 = 17 km square]      (about five minutes; one Java)
# Wrap it in the machine's build slot like any Gradle run.
cd "$(dirname "$0")/../.."
mkdir -p run
[ -f run/server.properties ] || printf 'level-seed=4242\nlevel-name=census-4242\nserver-ip=127.0.0.1\nserver-port=25599\nonline-mode=false\nmax-tick-time=-1\nview-distance=4\nsimulation-distance=4\n' > run/server.properties
env AWAKEN_FINDS_CENSUS=${1:-22} ${REAL:+AWAKEN_FINDS_REAL=1} JAVA_TOOL_OPTIONS=-Djava.awt.headless=true tools/gradle.sh 900 runServer --no-daemon > /dev/null 2>&1
L=$(ls -t logs/gradle-*-runServer.log | head -1)
grep -E "\[finds\] (census|dump)" "$L" | sed -E 's/^.*\[finds\] (census )?//'
