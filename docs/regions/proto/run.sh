#!/bin/zsh
# Rebuilds the numbers in docs/regions/PLAN.md section 7. Planning harness only: nothing here is game code.
# usage: docs/regions/proto/run.sh [Compare|Annuli|Cliffs|EuanWorld|MapOut|Bench] [args...]
# The prototype sits beside a copy of the real RegionMap/Names (copied fresh from src, so "current" is always the build's rules).
set -e
HERE=${0:A:h}; ROOT=${HERE:h:h:h}
JAVA=${JAVA_HOME:-/Volumes/MEDIA/.jdks/jdk-25.0.5+8/Contents/Home}/bin
OUT=${TMPDIR:-/tmp}/regions-proto; rm -rf $OUT; mkdir -p $OUT/src/dev/awaken/living/region $OUT/out
cp $ROOT/src/main/java/dev/awaken/living/region/RegionMap.java $ROOT/src/main/java/dev/awaken/living/region/Names.java $OUT/src/dev/awaken/living/region/
for f in $HERE/*.java; do cp $f $OUT/src/dev/awaken/living/region/; done
$JAVA/javac -d $OUT/out $OUT/src/dev/awaken/living/region/*.java
cd $OUT && $JAVA/java -cp out dev.awaken.living.region.${1:-Compare} "${@:2}"
