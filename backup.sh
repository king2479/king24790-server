#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

SERVER_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
BACKUP_DIR="$SERVER_DIR/backups"

if ! command -v tar >/dev/null 2>&1 || ! command -v gzip >/dev/null 2>&1; then
  printf 'Cannot create a backup: required commands "tar" and "gzip" must be installed.\n' >&2
  exit 1
fi

if ! command -v ss >/dev/null 2>&1; then
  printf 'Cannot safely create a backup: required command "ss" is missing, so the server state cannot be checked.\n' >&2
  exit 1
fi

for port in 8081 25577 25565; do
  if ss -H -ltn | awk -v port=":$port" '$4 ~ (port "$") { found = 1 } END { exit !found }'; then
    printf 'Refusing to back up while port %s is listening. Save and stop the server first, then run ./backup.sh again.\n' "$port" >&2
    exit 1
  fi
done

if command -v tmux >/dev/null 2>&1 && tmux has-session -t server 2>/dev/null; then
  printf 'Refusing to back up while the tmux server session is running. Save and stop the server first, then run ./backup.sh again.\n' >&2
  exit 1
fi

paths=()
for path in \
  server/world server/world_nether server/world_the_end \
  server/ops.json server/whitelist.json server/banned-players.json server/banned-ips.json \
  server/server.properties server/paper.yml server/spigot.yml server/bukkit.yml \
  bungee/eaglercraft_auths.db bungee/eaglercraft_skins_cache.db \
  bungee/config.yml bungee/velocity.toml bungee/forwarding.secret \
  bungee/plugins/EaglercraftXBungee/authservice.yml \
  bungee/plugins/EaglercraftXBungee/listeners.yml \
  bungee/plugins/EaglercraftXBungee/settings.yml \
  bungee/plugins/eaglerxvelocity/authservice.yml \
  bungee/plugins/eaglerxvelocity/listeners.yml \
  bungee/plugins/eaglerxvelocity/settings.yml \
  server/plugins/AuthMe/authme.db \
  server/plugins/AuthMe/config.yml; do
  if [[ -e "$SERVER_DIR/$path" ]]; then
    paths+=("$path")
  fi
done

if [[ "${#paths[@]}" -eq 0 ]]; then
  printf 'Cannot create a backup: no world or server configuration data was found.\n' >&2
  exit 1
fi

mkdir -p "$BACKUP_DIR"
chmod 700 "$BACKUP_DIR"
timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
archive="$BACKUP_DIR/server-backup-$timestamp.tar.gz"
temporary="$archive.part.$$"

if [[ -e "$archive" ]]; then
  printf 'Backup already exists: %s\n' "$archive" >&2
  exit 1
fi

if ! tar -czf "$temporary" -C "$SERVER_DIR" "${paths[@]}"; then
  rm -f "$temporary"
  printf 'Backup creation failed; the incomplete archive was removed.\n' >&2
  exit 1
fi
if ! gzip -t "$temporary"; then
  rm -f "$temporary"
  printf 'Backup verification failed; the incomplete archive was removed.\n' >&2
  exit 1
fi
if ! mv "$temporary" "$archive"; then
  rm -f "$temporary"
  printf 'Could not finalize the backup archive.\n' >&2
  exit 1
fi
chmod 600 "$archive"
printf 'Verified backup created: %s\n' "$archive"
