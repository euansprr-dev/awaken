#!/bin/zsh
# Plays a frozen copy of the mod: a git worktree with its own build and game folder at /Volumes/MEDIA/Awaken-play,
# so development builds in this checkout never touch the running game (a rebuild under a running game crashes it).
# Usage: tools/play.sh [commit or branch] [--pack]    (default: build/awaken-v1)
#   --pack  plays the Awakened Pack (docs/plan-pack.html) from its own game folder, run-pack, with every pack profile
#           including new terrain (Tectonic). Worlds in run/ are never opened with it, so they keep vanilla terrain.
#   --together  adds the multiplayer profile (e4mc join links, Simple Voice Chat). Off by default: voice chat opens the
#           microphone, which drops Bluetooth headphones into call quality (mono, low bitrate) for every game sound.
set -e
REPO="$(cd "$(dirname "$0")/.." && pwd)"
PLAY=/Volumes/MEDIA/Awaken-play
PACK_MODE=0
ARGS=()
TOGETHER=""
for a in "$@"; do
  if [ "$a" = "--pack" ]; then PACK_MODE=1; elif [ "$a" = "--together" ]; then TOGETHER=",together"; else ARGS+=("$a"); fi
done
REF=${ARGS[1]:-build/awaken-v1}
if [ ! -e "$PLAY/.git" ]; then
  git -C "$REPO" worktree add --detach "$PLAY" "$REF"
else
  if [ -n "$(git -C "$PLAY" status --porcelain --untracked-files=no)" ]; then
    echo "The play copy has local changes; not touching it." >&2
    exit 1
  fi
  git -C "$PLAY" checkout --quiet --detach "$REF"
fi
# the first time, bring over your worlds and settings from the development game folder
mkdir -p "$PLAY/run"
if [ ! -d "$PLAY/run/saves" ] && [ -d "$REPO/run/saves" ]; then cp -R "$REPO/run/saves" "$PLAY/run/saves"; fi
if [ ! -f "$PLAY/run/options.txt" ] && [ -f "$REPO/run/options.txt" ]; then cp "$REPO/run/options.txt" "$PLAY/run/options.txt"; fi
if [ $PACK_MODE = 1 ]; then
  mkdir -p "$PLAY/run-pack"
  if [ ! -f "$PLAY/run-pack/options.txt" ] && [ -f "$PLAY/run/options.txt" ]; then cp "$PLAY/run/options.txt" "$PLAY/run-pack/options.txt"; fi
  python3 -I "$PLAY/tools/pack.py" install "$PLAY/run-pack" perf,horizon,feel,sound,comfort${TOGETHER},world
  echo "Playing the Awakened Pack: $(git -C "$PLAY" log --oneline -1) (game folder run-pack)"
  exec "$PLAY/tools/gradle.sh" 43200 runClient -PgameDir=run-pack
fi
echo "Playing $(git -C "$PLAY" log --oneline -1)"
exec "$PLAY/tools/gradle.sh" 43200 runClient
