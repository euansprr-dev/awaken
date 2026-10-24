#!/bin/zsh
# Runs Gradle with the cache on the MEDIA SSD, a watchdog timeout and a log file.
# Usage: tools/gradle.sh <timeout-seconds> <gradle args...>
export GRADLE_USER_HOME=/Volumes/MEDIA/.gradle-home
export JAVA_HOME=/Volumes/MEDIA/.jdks/jdk-25.0.5+8/Contents/Home
cd "$(dirname "$0")/.."
mkdir -p logs
T=$1; shift
LOG="logs/gradle-$(date +%Y%m%d-%H%M%S)-${1//[^a-zA-Z0-9]/_}.log"
( ./gradlew --console=plain "$@" > "$LOG" 2>&1; echo "EXIT $?" >> "$LOG" ) &
PID=$!
SECS=0
while kill -0 $PID 2>/dev/null; do
  sleep 5; SECS=$((SECS+5))
  if [ $SECS -ge $T ]; then
    echo "WATCHDOG: killed after ${T}s" >> "$LOG"; pkill -P $PID; kill $PID 2>/dev/null; break
  fi
done
wait $PID 2>/dev/null
echo "log: $LOG"
tail -n 40 "$LOG"
# a -Ptests filter that matches no GameTest stops the server before it starts: say why under the Gradle failure
grep -m1 -o "TEST FILTER MATCHES NOTHING.*" "$LOG"
true
