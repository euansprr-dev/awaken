#!/bin/zsh
# Plays a frozen copy of the mod: a git worktree with its own build and game folder at /Volumes/MEDIA/Awaken-play,
# so development builds in this checkout never touch the running game (a rebuild under a running game crashes it).
# Usage: tools/play.sh [commit or branch]    (default: build/awaken-v1)
set -e
REPO="$(cd "$(dirname "$0")/.." && pwd)"
PLAY=/Volumes/MEDIA/Awaken-play
REF=${1:-build/awaken-v1}
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
echo "Playing $(git -C "$PLAY" log --oneline -1)"
exec "$PLAY/tools/gradle.sh" 43200 runClient
