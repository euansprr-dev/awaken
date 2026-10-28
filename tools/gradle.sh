#!/bin/zsh
# Runs Gradle with the cache on the MEDIA SSD, a watchdog timeout and a log file.
# Usage: tools/gradle.sh <timeout-seconds> <gradle args...>
# on GitHub's runners (.github/workflows/tours.yml) the workflow sets up Java and the Gradle home itself
if [ -z "$GITHUB_ACTIONS" ]; then
  export GRADLE_USER_HOME=/Volumes/MEDIA/.gradle-home
  export JAVA_HOME=/Volumes/MEDIA/.jdks/jdk-25.0.5+8/Contents/Home
fi
cd "$(dirname "$0")/.."
mkdir -p logs
T=$1; shift
LOG="logs/gradle-$(date +%Y%m%d-%H%M%S)-${1//[^a-zA-Z0-9]/_}.log"
( ./gradlew --console=plain "$@" > "$LOG" 2>&1; echo "EXIT $?" >> "$LOG" ) &
PID=$!
# before the kill, this checkout's JVMs but Gradle's own (the game's client, its server and their workers) dump their
# threads into the log, so a hang names what it waits for; a minute at most each
dump() {
  local bin=${JAVA_HOME:+$JAVA_HOME/bin/} pid main rest jp w
  "${bin}jps" -lv 2>/dev/null | while read -r pid main rest; do
    case "$main" in ""|*Gradle*|*gradle-wrapper*|*Jps*|*KotlinCompileDaemon*) continue ;; esac
    case "$rest" in *"$PWD/"*) ;; *) continue ;; esac
    echo "==== WATCHDOG thread dump of $pid $main"
    "${bin}jstack" -l "$pid" < /dev/null &
    jp=$!; w=0
    while kill -0 $jp 2>/dev/null && [ $w -lt 60 ]; do sleep 1; w=$((w+1)); done
    kill $jp 2>/dev/null
  done
}
SECS=0
while kill -0 $PID 2>/dev/null; do
  sleep 5; SECS=$((SECS+5))
  if [ $SECS -ge $T ]; then
    dump >> "$LOG" 2>&1
    echo "WATCHDOG: killed after ${T}s" >> "$LOG"; pkill -P $PID; kill $PID 2>/dev/null; break
  fi
done
wait $PID 2>/dev/null
echo "log: $LOG"
tail -n 40 "$LOG"
# a -Ptests filter that matches no GameTest stops the server before it starts: say why under the Gradle failure
grep -m1 -o "TEST FILTER MATCHES NOTHING.*" "$LOG"
true
