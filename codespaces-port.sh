#!/usr/bin/env bash

ensure_codespaces_port_public() {
  [[ -n "${CODESPACE_NAME:-}" ]] || return 0

  if ! command -v gh >/dev/null 2>&1; then
    printf 'Codespaces CLI is missing; port 8081 could not be made public.\n' >&2
    return 0
  fi

  if ! gh auth status >/dev/null 2>&1; then
    printf 'GitHub CLI is not authenticated. Run "gh auth login -s codespace", then run start again to make port 8081 public.\n' >&2
    return 0
  fi

  local last_error=''
  for attempt in {1..15}; do
    if last_error=$(gh codespace ports visibility 8081:public --codespace "$CODESPACE_NAME" 2>&1); then
      printf 'Codespaces port 8081 is public.\n'
      return 0
    fi

    case "$last_error" in
      *"HTTP 401"*|*"HTTP 403"*|*"not authorized"*|*"not logged in"*|*"required scope"*)
        break
        ;;
    esac
    sleep 2
  done

  printf 'Could not make Codespaces port 8081 public. Run "gh auth login -s codespace" if needed, then retry: gh codespace ports visibility 8081:public --codespace %s\n' "$CODESPACE_NAME" >&2
  if [[ -n "$last_error" ]]; then
    printf 'GitHub CLI error: %s\n' "$last_error" >&2
  fi
  return 0
}
