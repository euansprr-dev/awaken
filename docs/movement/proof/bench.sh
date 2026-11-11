#!/bin/zsh
# The swing bench as the code stands now, without Minecraft (two small stubs): every place on the old rope, on today's
# with a bot that lets go by hand, and with a bot that only holds the key. A few seconds.
#   docs/movement/proof/bench.sh            the guide on (SPEC 62)
#   docs/movement/proof/bench.sh classic    the guide off (the rope as SPEC 17.31 has it)
#   TUNE=slingPull=0.15,apexHeld=60 docs/movement/proof/bench.sh    numbers to try, by their names in AscentSwing.Tune
set -e
cd "$(dirname "$0")/../../.."
JAVA=${JAVA_HOME:-/Volumes/MEDIA/.jdks/jdk-25.0.5+8/Contents/Home}/bin
T=$(mktemp -d)
mkdir -p $T/dev/awaken/body/ascent
cp src/main/java/dev/awaken/body/ascent/{AscentSwing,AscentRules,SwingSpace,SwingBench}.java $T/dev/awaken/body/ascent/
cp -R docs/movement/proof/stubs/* $T/
$JAVA/javac -d $T/out $(find $T -name '*.java')
$JAVA/java ${TUNE:+-Dtune=$TUNE} -cp $T/out dev.awaken.body.ascent.SwingBench "$@"
rm -rf $T
