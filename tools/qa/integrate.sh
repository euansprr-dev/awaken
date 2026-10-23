#!/bin/zsh
# Merge one builder branch into the current branch, then build, run every GameTest and check assets.
# Usage: tools/qa/integrate.sh <branch>
set -u
cd "$(dirname "$0")/../.."
BR=$1
echo "== merging $BR"
git merge --no-ff --no-edit "$BR" || { echo "MERGE CONFLICT: resolve, then rerun the checks"; exit 1; }
echo "== build"
tools/gradle.sh 1200 build | grep -E "error:|BUILD|EXIT" | head -40
grep -q "EXIT 0" "$(ls -t logs/*.log | head -1)" || { echo "BUILD FAILED"; exit 1; }
echo "== gametests"
tools/gradle.sh 1800 runGameTestServer | grep -E "required tests|  - |EXIT|Exception" | head -60
grep -q "EXIT 0" "$(ls -t logs/*.log | head -1)" || { echo "TESTS FAILED"; exit 1; }
echo "== assets"
python3 tools/qa/check_assets.py || { echo "ASSET PROBLEMS"; exit 1; }
echo "== OK"
