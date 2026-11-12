#!/bin/zsh
# Checks this branch on GitHub's Linux machines instead of this Mac (.github/workflows/gametests.yml, started by hand).
#   tools/check-ci.sh compile                                   compileJava only (about four minutes)
#   tools/check-ci.sh bench <tour.txt | blueprint id>[,more] [seed ...]   the gates' bench for each land at each seed (default 1):
#                                                               anything floating, the walk to every site, the picture sheet
#   tools/check-ci.sh tests <prefix>[,prefix]                   only the GameTests whose names start with a prefix
# It checks the branch as pushed: commit and push first. Reports, sheets and shots land in run-bench/ci/<run id>/.
# Exits non-zero if the run failed (for bench: if anything floats or a site is not reached). Never the whole suite: the
# manager session runs that once, after everything merges.
cd "$(dirname "$0")/.."
REPO=euansprr-dev/awaken
USAGE="usage: tools/check-ci.sh compile | bench <land>[,land] [seed ...] | tests <prefix>[,prefix]"
MODE=$1
[[ $MODE == (compile|bench|tests) ]] || { echo "$USAGE"; exit 2; }
shift
TARGET=""; SEEDS=""
if [[ $MODE != compile ]]; then
  [ -n "$1" ] || { echo "$USAGE"; exit 2; }
  TARGET=$1; shift
  SEEDS="$*"
fi
[[ "$TARGET$SEEDS" =~ '^[A-Za-z0-9_:,./ -]*$' ]] || { echo "only letters, digits and _ : , . / - in a target or a seed"; exit 2; }

BRANCH=$(git branch --show-current)
[ -n "$BRANCH" ] || { echo "detached HEAD: check out your branch first"; exit 2; }
REMOTE=$(git ls-remote origin "refs/heads/$BRANCH" | cut -f1)
[ -n "$REMOTE" ] || { echo "$BRANCH is not on GitHub yet: git push -u origin $BRANCH"; exit 2; }
[ "$REMOTE" = "$(git rev-parse HEAD)" ] || { echo "$BRANCH on GitHub is ${REMOTE[1,8]}, here it is $(git rev-parse --short=8 HEAD): push first"; exit 2; }
git diff --quiet HEAD -- src tools .github || echo "warning: uncommitted changes are not on GitHub"

TAG=$(date +%Y%m%d-%H%M%S)-$$
OUT=$(gh workflow run gametests.yml -R $REPO --ref "$BRANCH" -f mode="$MODE" -f target="$TARGET" -f seeds="$SEEDS" -f id="$TAG" 2>&1) \
  || { echo "$OUT"; exit 1; }
RUN=$(print -r -- "$OUT" | grep -oE 'actions/runs/[0-9]+' | head -1 | cut -d/ -f3)
for i in {1..20}; do
  [ -n "$RUN" ] && break
  sleep 5
  RUN=$(gh run list -R $REPO -w gametests.yml -b "$BRANCH" -e workflow_dispatch -L 30 --json databaseId,displayTitle \
    -q ".[] | select(.displayTitle | endswith(\"$TAG\")) | .databaseId" | head -1)
done
[ -n "$RUN" ] || { echo "dispatched, but found no run tagged $TAG: see https://github.com/$REPO/actions/workflows/gametests.yml"; exit 1; }
echo "check $MODE $TARGET $SEEDS on $BRANCH at ${REMOTE[1,8]}: https://github.com/$REPO/actions/runs/$RUN"

# one status call a minute: many sessions share one API quota
while :; do
  S=$(gh run view $RUN -R $REPO --json status,conclusion -q '.status + " " + .conclusion' 2>/dev/null)
  [[ $S == completed* ]] && break
  sleep 60
done
CONCLUSION=${S#completed }
if [[ $MODE == bench ]]; then
  DIR=run-bench/ci/$RUN
  mkdir -p $DIR
  gh run download $RUN -R $REPO -n bench -D $DIR > /dev/null 2>&1 || echo "no bench artifact"
  for f in $DIR/run-bench/out/*/bench.txt(N); do
    echo "== ${f:h:t}"
    grep -E '^(floats|settled|reach|route|volume|beats)' $f
    grep -q '^floats: 0$' $f && grep -q '^reach: all ' $f || grep -vE '^(BENCH|floats|settled|reach|route|volume|  leg |sheet)' $f | head -30
  done
  echo "pictures: $DIR/run-bench/out/<land>-<seed>/sheet.png"
fi
if [[ $CONCLUSION != success ]]; then
  echo "FAILED ($CONCLUSION)"
  gh run view $RUN -R $REPO --log-failed 2>/dev/null | grep -E 'error:|warning: \[|FAILED|What went wrong|##\[error\]' -A4 | cut -c1-260 | head -60
  exit 1
fi
echo "ok"
