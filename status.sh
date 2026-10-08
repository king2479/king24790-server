#!/usr/bin/env bash
set -Eeuo pipefail

if ! command -v ss >/dev/null 2>&1; then
  printf 'Cannot check server status: required command "ss" is missing.\n' >&2
  exit 1
fi

healthy=true
for port in 8081 25577 25565; do
  if ss -H -ltn | awk -v port=":$port" '$4 ~ (port "$") { found = 1 } END { exit !found }'; then
    case "$port" in
      8081) printf 'UP   Eaglercraft web/proxy listener (port %s)\n' "$port" ;;
      25577) printf 'UP   Minecraft proxy listener (port %s)\n' "$port" ;;
      25565) printf 'UP   Backend listener (port %s; should be local-only)\n' "$port" ;;
    esac
  else
    printf 'DOWN Expected listener on port %s\n' "$port"
    healthy=false
  fi
done

if command -v tmux >/dev/null 2>&1 && tmux has-session -t server 2>/dev/null; then
  printf 'UP   tmux server session\n'
elif [[ "$healthy" == true ]]; then
  printf 'INFO Server listeners are up outside tmux.\n'
fi

if [[ "$healthy" == true ]]; then
  exit 0
fi
exit 1
