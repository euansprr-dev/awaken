#!/bin/zsh
# One gate checked in one go (SPEC 50.8): its pictures, anything floating, and the walk to every site, with timings.
#
#   tools/bench/bench.sh once  <tour.txt | blueprint id> [seed]   one Gradle run, about 45 s; needs nothing running
#   tools/bench/bench.sh up    [tour.txt | blueprint id]          start the checker that stays running (one Gradle run to
#                                                                 build, then a GameTest server outside Gradle: no build
#                                                                 slot held). With a land: checks it once to warm up
#   tools/bench/bench.sh check <tour.txt | blueprint id> [seed]   the same report as once, in seconds; starts the checker
#                                                                 if it isn't up
#   tools/bench/bench.sh shot  <blueprint id> <x> <y> <z> <yaw> <pitch> [name]   one picture from one eye, land coordinates
#   tools/bench/bench.sh status | down | restart
#
# check and once exit 0 only when nothing floats and every site is reached. The report is run-bench/out/<name>/bench.txt,
# beside sheet.png and the pictures.
#
# What the running checker picks up by itself: any change under src/main/java/dev/awaken/gates/realms/land/ (rebuilt with
# javac before each request and loaded fresh), the tour file, the blueprint and biome JSON, textures. Anything else (the
# kit, a new block, a new program in another package) needs `bench.sh restart`. It stops by itself after 20 idle minutes.
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
BENCH="$ROOT/run-bench"
SLOT=/Volumes/MEDIA/Awaken-carillon/tools/carillon/slot.sh
LANDS=src/main/java/dev/awaken/gates/realms/land
zmodload zsh/datetime
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
  grep -q '^shot: ' "$1/bench.txt" && return 0 # one picture: nothing was checked
  grep -q '^floats: 0$' "$1/bench.txt" && grep -q '^reach: all ' "$1/bench.txt"
}

pid_up() { # prints the checker's pid when it is running
  local pid="$(cat "$BENCH/pid" 2>/dev/null)"
  [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null && echo "$pid"
}

up() {
  if [ -n "$(pid_up)" ]; then
    echo "the bench is already up (pid $(pid_up))"
    return 0
  fi
  local t0=$EPOCHREALTIME
  mkdir -p "$BENCH/game" "$BENCH/req" "$BENCH/out"
  rm -rf "$BENCH/classes" "$BENCH/stop" "$BENCH/ready" "$BENCH/req"/*(N)
  # compile, and write the run's classpath and arguments (build/bench/, build/moddev/gameTestServerRun*.txt)
  local built="$($SLOT build tools/gradle.sh 560 benchClasspath -Ptests=realm_bench)"
  if [ ! -s build/bench/run.classpath ] || ! echo "$built" | grep -q '^EXIT 0$'; then
    echo "$built" | grep -E "^log: |error:|FAILED|WATCHDOG|What went wrong" -A3
    echo "the build failed: the bench was not started"
    return 1
  fi
  touch "$BENCH/up.stamp"
  mkdir -p "$BENCH/classes"
  touch "$BENCH/classes/.stamp"
  # our own copy of the JVM arguments: another Gradle run in this worktree rewrites the original with its own test filter
  sed -e 's/^-Dawaken\.tests=.*/-Dawaken.tests=realm_bench/' build/moddev/gameTestServerRunVmArgs.txt > "$BENCH/vmargs.txt"
  cp build/moddev/gameTestServerRunProgramArgs.txt "$BENCH/programargs.txt"
  local -a jvm
  jvm=("${(@f)$(grep -v '^@' build/bench/run.jvmargs)}")
  (
    cd "$BENCH/game"
    export AWAKEN_BENCH_SERVE="$BENCH" AWAKEN_BENCH_ROOT="$ROOT"
    nohup "$(cat "$ROOT/build/bench/run.java")" -Xmx1536m "${jvm[@]}" "@$BENCH/vmargs.txt" -cp "$(cat "$ROOT/build/bench/run.classpath")" \
      "$(cat "$ROOT/build/bench/run.main")" "@$BENCH/programargs.txt" > "$BENCH/log.txt" 2>&1 &
    echo $! > "$BENCH/pid"
  )
  local waited=0
  while [ ! -f "$BENCH/ready" ]; do
    if [ -z "$(pid_up)" ]; then
      echo "the bench's server stopped while starting; the end of run-bench/log.txt:"
      tail -n 25 "$BENCH/log.txt"
      return 1
    fi
    sleep 0.5
    waited=$((waited + 1))
    if [ $waited -gt 360 ]; then
      echo "the bench's server didn't come up in 3 minutes (run-bench/log.txt); stopping it"
      kill "$(pid_up)" 2>/dev/null
      return 1
    fi
  done
  printf "the bench is up (pid %s) after %.0f s\n" "$(cat "$BENCH/pid")" $((EPOCHREALTIME - t0))
}

down() {
  local pid="$(pid_up)"
  if [ -z "$pid" ]; then
    echo "the bench isn't running"
    rm -f "$BENCH/pid" "$BENCH/ready"
    return 0
  fi
  touch "$BENCH/stop"
  local waited=0
  while kill -0 "$pid" 2>/dev/null && [ $waited -lt 40 ]; do sleep 0.5; waited=$((waited + 1)); done
  if kill -0 "$pid" 2>/dev/null; then kill "$pid" 2>/dev/null; sleep 1; kill -9 "$pid" 2>/dev/null; fi
  rm -f "$BENCH/pid" "$BENCH/ready" "$BENCH/stop"
  echo "the bench is down"
}

lands() { # rebuild the gates' lands when any changed since the last build; 1 when they don't compile
  local stamp="$BENCH/classes/.stamp"
  JAVAC_MS=0
  [ -n "$(find $LANDS -name '*.java' -newer "$stamp" 2>/dev/null | head -1)" ] || return 0
  local t0=$EPOCHREALTIME
  touch "$BENCH/classes/.building"
  rm -rf "$BENCH/classes/dev"
  local javac="$(dirname "$(cat build/bench/run.java)")/javac"
  if ! "$javac" -d "$BENCH/classes" -cp "$(cat build/bench/compile.classpath)" -proc:none -nowarn -Xlint:none -encoding UTF-8 $LANDS/*.java > "$BENCH/javac.txt" 2>&1; then
    cat "$BENCH/javac.txt"
    echo "the lands don't compile: nothing was checked"
    rm -rf "$BENCH/classes/dev"
    return 1
  fi
  mv "$BENCH/classes/.building" "$stamp"
  JAVAC_MS=$(( (EPOCHREALTIME - t0) * 1000 ))
}

ask() { # ask <name> <json>: hand the running checker a request and wait for its answer
  local name="$1" t0=$EPOCHREALTIME
  [ -n "$(pid_up)" ] || up || return 2
  lands || return 1
  local stale="$(find src/main/java/dev/awaken/gates/realms -name '*.java' -newer "$BENCH/up.stamp" ! -path "*/realms/land/*" 2>/dev/null | head -3)"
  rm -rf "$BENCH/out/$name" "$BENCH/out/$name.done"
  echo "$2" > "$BENCH/req/$name.tmp" && mv "$BENCH/req/$name.tmp" "$BENCH/req/$name.json"
  while [ ! -f "$BENCH/out/$name.done" ]; do
    if [ -z "$(pid_up)" ]; then
      echo "the bench stopped before it answered; the end of run-bench/log.txt:"
      tail -n 25 "$BENCH/log.txt"
      return 2
    fi
    sleep 0.05
  done
  report "$BENCH/out/$name"
  local code=$?
  printf "bench.sh: %.1f s in all (javac %.0f ms)\n" $((EPOCHREALTIME - t0)) $JAVAC_MS
  [ -n "$stale" ] && echo "note: code outside the lands changed since the bench started (${stale//$'\n'/, }): bench.sh restart to check against it"
  return $code
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
  up)
    up || exit 1
    [ -z "$2" ] || ask "$(name_of "$2")" "{\"what\": \"$2\", \"seed\": 1}"
    ;;
  check)
    [ -n "$2" ] || { echo "usage: bench.sh check <tour.txt | blueprint id> [seed]"; exit 2; }
    ask "$(name_of "$2")" "{\"what\": \"$2\", \"seed\": ${3:-1}}"
    ;;
  shot)
    [ -n "$7" ] || { echo "usage: bench.sh shot <blueprint id> <x> <y> <z> <yaw> <pitch> [name]"; exit 2; }
    SHOT="${8:-shot}"
    ask "$(name_of "$2")-$SHOT" "{\"what\": \"$2\", \"seed\": 1, \"shot\": \"$SHOT\", \"eye\": [$3, $4, $5, $6, $7]}"
    ;;
  status)
    PID="$(pid_up)"
    if [ -n "$PID" ]; then
      echo "the bench is up: pid $PID, $(( $(ps -o rss= -p $PID) / 1024 )) MB in memory, $([ -f "$BENCH/ready" ] && echo serving || echo starting)"
    else
      echo "the bench isn't running"
    fi
    ;;
  down) down ;;
  restart) down; up ;;
  *)
    sed -n '2,13p' "$0" | sed 's/^# \{0,1\}//'
    exit 2
    ;;
esac
