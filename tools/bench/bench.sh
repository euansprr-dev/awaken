#!/bin/zsh
# One gate checked in one go (SPEC 50.8): its pictures, anything floating, and the walk to every site, with timings.
#   tools/bench/bench.sh once <tour.txt | blueprint id> [seed]   one Gradle run, about 45 s; exits 0 only when clean
# The report is run-bench/out/<name>/bench.txt, beside sheet.png and the pictures.
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
BENCH="$ROOT/run-bench"
SLOT=/Volumes/MEDIA/Awaken-carillon/tools/carillon/slot.sh
cd "$ROOT"

name_of() { # a tour's file name, or a blueprint id's path
  local n="${1:t:r}"
  echo "${n##*:}"
}

report() { # report <out dir>: print it, and say whether the land is clean
  if [ ! -f "$1/bench.txt" ]; then
    echo "no report came out"
    return 2
  fi
  cat "$1/bench.txt"
  grep -q '^floats: 0$' "$1/bench.txt" && grep -q '^reach: all ' "$1/bench.txt"
}

case "$1" in
  once)
    [ -n "$2" ] || { echo "usage: bench.sh once <tour.txt | blueprint id> [seed]"; exit 2; }
    OUT="$BENCH/out/$(name_of "$2")"
    rm -rf "$OUT"
    export AWAKEN_BENCH="$2" AWAKEN_BENCH_OUT="$OUT" AWAKEN_BENCH_ROOT="$ROOT" AWAKEN_BENCH_SEED="${3:-1}"
    $SLOT build tools/gradle.sh 560 runGameTestServer -Ptests=realm_bench | grep -E "^log: |BUILD FAILED|WATCHDOG|error:"
    report "$OUT"
    ;;
  *)
    echo "usage: bench.sh once <tour.txt | blueprint id> [seed]"
    exit 2
    ;;
esac
