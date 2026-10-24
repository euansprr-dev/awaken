#!/bin/zsh
# Shares the machine between the sessions building Awaken: at most 3 Gradle builds at once, and a tour (autopilot
# client) runs alone, with no build beside it, so its frame times mean something.
# Usage (any working directory; the command runs where you are):
#   /Volumes/MEDIA/Awaken-carillon/tools/carillon/slot.sh build <command...>   waits for one of 3 build slots
#   /Volumes/MEDIA/Awaken-carillon/tools/carillon/slot.sh tour  <command...>   waits for the tour lock, then all 3 slots
# Locks are directories in /Volumes/MEDIA/.carillon-locks (mkdir is atomic): build-1..3 and tour. Each holds an
# "owner" file: "<pid> <who> <start time>". A lock whose pid is gone is stale and gets taken over. Waits give up after
# SLOT_WAIT seconds (default 7200). Set SLOT_WHO to name yourself in the owner file.
# Fairness: a waiting build leaves a want-build.<pid> marker; between tours, builds go first (a tour waiter holds off
# while a live build is waiting, for at most SLOT_YIELD seconds, default 1800), so tours back to back can't starve them.
LOCKS=${SLOT_LOCKS:-/Volumes/MEDIA/.carillon-locks}
mkdir -p $LOCKS
MODE=${1:?build or tour}; shift
WHO=${SLOT_WHO:-${PWD:t}}
WAIT=${SLOT_WAIT:-7200}
YIELD=${SLOT_YIELD:-1800}
HELD=()
WANT=""

take() { # take <lock dir>: 0 when we got it
  local d=$LOCKS/$1
  if mkdir "$d" 2>/dev/null; then
    echo "$$ $WHO $(date +%Y-%m-%dT%H:%M:%S)" > "$d/owner"; HELD+=("$d"); return 0
  fi
  local pid=$(cut -d' ' -f1 "$d/owner" 2>/dev/null)
  if [ -n "$pid" ] && ! kill -0 "$pid" 2>/dev/null; then rm -rf "$d"; take "$1"; return $?; fi
  return 1
}
release() { for d in $HELD; do rm -rf "$d"; done; HELD=(); [ -n "$WANT" ] && rm -f "$WANT"; }
trap release EXIT INT TERM

builds_waiting() { # 0 when a live build is waiting for a slot
  local f pid
  for f in $LOCKS/want-build.*(N); do
    pid=$(cut -d' ' -f1 "$f" 2>/dev/null)
    if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then return 0; fi
    rm -f "$f"
  done
  return 1
}

waited=0
waitfor() { # waitfor <lock dir>
  until take "$1"; do
    if [ $waited -ge $WAIT ]; then echo "slot.sh: gave up waiting for $1 after ${WAIT}s ($(cat $LOCKS/$1/owner 2>/dev/null))" >&2; exit 75; fi
    /bin/sleep 5; waited=$((waited + 5))
  done
}

case $MODE in
  build)
    WANT=$LOCKS/want-build.$$
    echo "$$ $WHO $(date +%Y-%m-%dT%H:%M:%S)" > $WANT
    # a tour in progress means no builds; then the first free slot
    until [ ! -d $LOCKS/tour ] && { take build-1 || take build-2 || take build-3; }; do
      if [ -d $LOCKS/tour ]; then pid=$(cut -d' ' -f1 $LOCKS/tour/owner 2>/dev/null); [ -n "$pid" ] && ! kill -0 "$pid" 2>/dev/null && rm -rf $LOCKS/tour; fi
      if [ $waited -ge $WAIT ]; then echo "slot.sh: gave up waiting for a build slot after ${WAIT}s" >&2; exit 75; fi
      /bin/sleep 5; waited=$((waited + 5))
    done
    rm -f $WANT; WANT="" ;;
  tour)
    # builds go first between tours (bounded, so a stream of builds can't starve tours either)
    yielded=0
    until ! builds_waiting && take tour; do
      if [ $yielded -ge $YIELD ]; then waitfor tour; break; fi
      /bin/sleep 5; yielded=$((yielded + 5)); waited=$((waited + 5))
      if [ $waited -ge $WAIT ]; then echo "slot.sh: gave up waiting for the tour lock after ${WAIT}s" >&2; exit 75; fi
    done
    waitfor build-1; waitfor build-2; waitfor build-3 ;;
  *) echo "slot.sh: mode must be build or tour" >&2; exit 64 ;;
esac
echo "slot.sh: $MODE slot taken by $WHO after ${waited}s"
"$@"
