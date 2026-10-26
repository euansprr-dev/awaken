#!/bin/zsh
# Runs one autopilot tour on GitHub's macOS runners (.github/workflows/tours.yml) instead of this Mac, and fetches it.
# Usage: tools/tour-ci.sh <script> [timeout-seconds] [KEY=VAL ...]
#   tools/tour-ci.sh tools/autopilot/farsight2-city.txt 1500 PACK=perf,world TECTONIC=awakened LENS=cinematic
# The knobs are tools/autopilot.sh's: PACK BACKEND LENS RD XMX ZGC CITY TECTONIC DH. The runner starts from a fresh game
# folder, so an unset knob means the default (Lens Swift, Vulkan, render distance 16), not what this worktree's
# run-autopilot last had; Far Sight is always off there. It tours the branch as pushed: commit and push first.
# Shots, logs and summary.txt land in run-autopilot/screenshots-ci/<script>-<run id>/; exits non-zero if the tour
# failed. Several can run at once, from any worktrees (no shared state). Look tours only: perf and frame-time tours
# stay on the Mac (slot.sh tour tools/autopilot.sh ...).
cd "$(dirname "$0")/.."
REPO=euansprr-dev/awaken
USAGE="usage: tools/tour-ci.sh <tools/autopilot/name.txt> [timeout-seconds] [KEY=VAL ...]"
[ $# -gt 0 ] || { echo "$USAGE"; exit 2; }
SCRIPT=$1
shift
T=1500
if [[ $# -gt 0 && $1 == <-> ]]; then T=$1; shift; fi
for kv in "$@"; do
  [[ $kv =~ '^(PACK|BACKEND|LENS|RD|XMX|ZGC|CITY|TECTONIC|DH)=[A-Za-z0-9,._-]+$' ]] \
    || { echo "not a knob: $kv (PACK BACKEND LENS RD XMX ZGC CITY TECTONIC DH)"; exit 2; }
done
[[ $SCRIPT =~ '^tools/autopilot/[A-Za-z0-9._-]+\.txt$' && -f $SCRIPT ]] || { echo "no tour $SCRIPT"; echo "$USAGE"; exit 2; }
(( T <= 3600 )) || { echo "timeout at most 3600 s"; exit 2; }
# the runner's GPU scrambles Far Sight's far mesh, so CI tours run without it and Far Sight's own tours stay on the Mac
grep -q '^farsight on' $SCRIPT && { echo "$SCRIPT turns Far Sight on: run it on the Mac (slot.sh tour tools/autopilot.sh ...)"; exit 2; }
# awakened without a starter pick: on the slower runner the first-power screen can open after the last "screen close"
grep -qE '^cmd awaken (opening skip|admin awaken)' $SCRIPT && ! grep -q '^cmd awaken powers starter' $SCRIPT \
  && echo "warning: $SCRIPT awakens without 'cmd awaken powers starter 1': the first-power screen may cover the shots"

# the runner checks out the branch on GitHub: it must hold exactly what this worktree would tour
BRANCH=$(git branch --show-current)
[ -n "$BRANCH" ] || { echo "detached HEAD: check out your branch first"; exit 2; }
REMOTE=$(git ls-remote origin "refs/heads/$BRANCH" | cut -f1)
[ -n "$REMOTE" ] || { echo "$BRANCH is not on GitHub yet: git push -u origin $BRANCH"; exit 2; }
[ "$REMOTE" = "$(git rev-parse HEAD)" ] || { echo "$BRANCH on GitHub is ${REMOTE[1,8]}, here it is $(git rev-parse --short=8 HEAD): push (or pull) first"; exit 2; }
git diff --quiet HEAD -- src "$SCRIPT" || echo "warning: uncommitted changes in src/ or the script are not on the runner"

NAME=${${SCRIPT:t}%.txt}
TAG=$(date +%Y%m%d-%H%M%S)-$$
STARTED=$(date +%s)
OUT=$(gh workflow run tours.yml -R $REPO --ref "$BRANCH" -f script="$SCRIPT" -f timeout="$T" -f knobs="$*" -f id="$TAG" 2>&1) \
  || { echo "$OUT"; exit 1; }
RUN=$(print -r -- "$OUT" | grep -oE 'actions/runs/[0-9]+' | head -1 | cut -d/ -f3)
# a gh that prints no run URL: find the run by the tag in its name
for i in {1..20}; do
  [ -n "$RUN" ] && break
  sleep 5
  RUN=$(gh run list -R $REPO -w tours.yml -b "$BRANCH" -e workflow_dispatch -L 30 --json databaseId,displayTitle \
    -q ".[] | select(.displayTitle | endswith(\" $TAG\")) | .databaseId" | head -1)
done
[ -n "$RUN" ] || { echo "dispatched, but found no run tagged $TAG: see https://github.com/$REPO/actions/workflows/tours.yml"; exit 1; }
URL=https://github.com/$REPO/actions/runs/$RUN
echo "tour $NAME on $BRANCH at ${REMOTE[1,8]}: $URL"

# one status call every 30 s (gh run watch polls every 3 s, and ten builders share one API quota)
while :; do
  S=$(gh run view $RUN -R $REPO --json status,conclusion -q '.status + " " + .conclusion' 2>/dev/null)
  [[ $S == completed* ]] && break
  sleep 30
done
CONCLUSION=${S#completed }
DIR=run-autopilot/screenshots-ci/$NAME-$RUN
mkdir -p $DIR
gh run download $RUN -R $REPO -n tour-$NAME -D $DIR > /dev/null 2>&1 \
  || { rmdir $DIR 2>/dev/null; echo "no artifact: the run stopped before the tour"
         gh run view $RUN -R $REPO --log-failed 2>/dev/null | grep -m5 '##\[error\]' | sed 's/.*##\[error\]/  /'; }
[ -f $DIR/summary.txt ] && cat $DIR/summary.txt
SHOTS=($DIR/*.png(N))
(( ${#SHOTS} )) && print -l -- $SHOTS
gh api "repos/$REPO/actions/runs/$RUN/artifacts" -q '.artifacts[].name' 2>/dev/null | grep -qx "tour-$NAME-clips" \
  && echo "clips: gh run download $RUN -R $REPO -n tour-$NAME-clips -D run-autopilot/clips-ci/$NAME-$RUN"
echo "$CONCLUSION after $(( ($(date +%s) - STARTED) / 60 )) min: ${#SHOTS} shots in $DIR"
echo "run: $URL"
[ "$CONCLUSION" = success ]
