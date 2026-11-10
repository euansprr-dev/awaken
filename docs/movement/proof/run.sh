#!/bin/zsh
# The movement plan's bench trial (docs/movement/PLAN.md, "What I measured"): the game's own swing code and swing bench,
# compiled without Minecraft (two small stubs), with real-sized woods added and three trials behind switches (low anchors, a sling, when a held rope lets go).
# Nothing here is the game's code path: the copies live in a temp folder and the repo's sources are only read.
#   docs/movement/proof/run.sh            the rope as it is in every place, then the trials
set -e
cd "$(dirname "$0")/../../.."
JAVA=${JAVA_HOME:-/Volumes/MEDIA/.jdks/jdk-25.0.5+8/Contents/Home}/bin
T=$(mktemp -d)
mkdir -p $T/dev/awaken/body/ascent
# the sources as they were when the audit ran (the build has since changed them)
for f in AscentSwing AscentRules SwingSpace SwingBench; do git show a651866b3:src/main/java/dev/awaken/body/ascent/$f.java > $T/dev/awaken/body/ascent/$f.java; done
cp -R docs/movement/proof/stubs/* $T/
(cd $T/dev/awaken/body/ascent && patch -s -p0 AscentSwing.java < /dev/stdin) < <(sed -n '/AscentSwing.java/,/^--- a\/src.*SwingBench/p' docs/movement/proof/trial.patch | sed '$d')
(cd $T/dev/awaken/body/ascent && patch -s -p0 SwingBench.java < /dev/stdin) < <(sed -n '/^--- a\/src.*SwingBench/,$p' docs/movement/proof/trial.patch)
$JAVA/javac -d $T/out $(find $T -name '*.java')
P="lane city forest oak taiga plains"
run() { $JAVA/java "$@" -cp $T/out dev.awaken.body.ascent.SwingBench ${=P} | grep assist; }
echo "== the bench as it is: before 28 Oct (today) and the assisted rope with a bot that lets go by hand in the window (assist)"
$JAVA/java -cp $T/out dev.awaken.body.ascent.SwingBench ${=P}
echo "== a player who only holds the key (the rope lets go by itself at 95 degrees): the rope as it plays"; run -Dhold=1
echo "== trial 1, holding: low anchors may swing (shortest rope 4.5)"; run -Dhold=1 -Dlow=4.5
echo "== trial 2, holding: the sling, with a look-ahead"; run -Dhold=1 -Dsling=1
echo "== trial 3, holding: the rope lets go by itself at 75, 62, 55, 50, 40 degrees"
for a in 75 62 55 50 40; do echo "-- $a"; run -Dhold=1 -Dapex=$a; done
echo "== the proposal, holding: lets go at 55 degrees, with the sling"; run -Dhold=1 -Dapex=55 -Dsling=1
rm -rf $T
