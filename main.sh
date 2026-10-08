#!/bin/bash
# ~~~ EaglercraftX Server
# ~~~ >> smashed together once again by ayunami2000
# ~~~ >> modified by WinRAR

unset DISPLAY

SERVER_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "$SERVER_DIR/codespaces-port.sh"
cd "$SERVER_DIR"

download_cached_file() {
  local url="$1"
  local target="$2"
  local temporary="${target}.part.$$"

  if [ -s "$target" ]; then
    return 0
  fi
  if ! wget -O "$temporary" "$url" || [ ! -s "$temporary" ] || ! mv "$temporary" "$target"; then
    rm -f "$temporary"
    printf 'Could not download or validate %s. Check your network connection and try start again.\n' "$target" >&2
    return 1
  fi
}

if command -v tmux >/dev/null 2>&1; then
  echo "set -g mouse on" > ~/.tmux.conf
  if tmux has-session -t server 2>/dev/null; then
    ensure_codespaces_port_public
    if [ "${KING24790_START_ATTACH:-1}" = "1" ]; then
      exec tmux attach-session -t server
    fi
    printf 'Server is already running in tmux session "server".\n'
    exit 0
  fi
  tmux kill-session -t placeholder 2>/dev/null || true
fi

BASEDIR="$SERVER_DIR"
SERVER_JVM_FLAGS="-Xms2G -Xmx2G -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M -XX:G1ReservePercent=20 -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 -XX:InitiatingHeapOccupancyPercent=15 -XX:G1MixedGCLiveThresholdPercent=90 -XX:SurvivorRatio=32 -XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1"

FORCE1="nah"
SKIP_EAGLERCRAFT_SOURCE_UPDATE=false

export GIT_TERMINAL_PROMPT=0

if [ ! -e "eaglercraftx/.git" ]; then
  if [ -s "web/index.html" ] && [ -s "web/bootstrap.js" ] &&
    [ -s "web/js/classes.js" ] && [ -s "web/js/assets.epk" ] &&
    jar tf "bungee/plugins/EaglercraftX_1.8_EaglerXVelocity.jar" >/dev/null 2>&1; then
    SKIP_EAGLERCRAFT_SOURCE_UPDATE=true
    printf 'Using existing Eaglercraft web and proxy files; source checkout is unavailable, so updates are skipped.\n'
  else
    FORCE1="bruh"
  fi
fi

if ! grep -q "eula=true" "eula.txt"; then
  rm eula.txt
  java -jar LicensePrompt.jar
  echo "eula=true" > eula.txt
fi
# ~~~

# reset stuff
if grep -q "reset=true" "reset.txt"; then
  rm base.repl
  rm -rf server/world
  rm -rf server/world_nether
  rm -rf server/world_the_end
  rm -rf server/logs
  rm -rf server/plugins/PluginMetrics
  rm -f server/usercache.json
  rm -rf cuberite
  rm -rf bungee/logs
  rm -f bungee/eaglercraft_skins_cache.db
  rm -f bungee/eaglercraft_auths.db
  sed -i '/^stats: /d' bungee/config.yml
  sed -i "s/^server_uuid: .*\$/server_uuid: $(cat /proc/sys/kernel/random/uuid)/" bungee/plugins/EaglercraftXBungee/settings.yml
  rm -f /tmp/mcp918.zip
  rm -f /tmp/1.8.8.jar
  rm -f /tmp/1.8.json
  chmod +x selsrv.sh
  ./selsrv.sh
fi

rm -rf /tmp/##EAGLER.TEMP##
rm -rf /tmp/teavm
rm -rf /tmp/output

mkdir -p bungee/plugins
mkdir -p web

if [ "$SKIP_EAGLERCRAFT_SOURCE_UPDATE" != true ]; then
  mkdir -p eaglercraftx

if [ "$FORCE1" != "bruh" ]; then
  if ! git -C eaglercraftx remote update; then
    printf 'Cannot check for Eaglercraft updates. Check your network connection and try start again.\n' >&2
    exit 1
  fi
  if ! LOCALHASH=$(git -C eaglercraftx rev-parse HEAD) ||
    ! REMOTEHASH=$(git -C eaglercraftx rev-parse '@{u}'); then
    printf 'Cannot determine the Eaglercraft checkout version or upstream branch. Check eaglercraftx/ and try start again.\n' >&2
    exit 1
  fi
else
  LOCALHASH=''
  REMOTEHASH=''
fi

if [ "$LOCALHASH" != "$REMOTEHASH" ] || [ "$FORCE1" = "bruh" ]; then
  if ! CLONE_STAGE="$(mktemp -d "$SERVER_DIR/.eaglercraftx-clone.XXXXXX")"; then
    printf 'Could not create a temporary directory for the Eaglercraft update.\n' >&2
    exit 1
  fi
  if ! git clone https://github.com/WINRARisyou/EaglercraftX "$CLONE_STAGE/source" --depth 1; then
    rm -rf "$CLONE_STAGE"
    printf 'Could not download Eaglercraft sources. Check your network connection and try start again.\n' >&2
    exit 1
  fi
  OLD_CHECKOUT="$SERVER_DIR/.eaglercraftx-old.$$"
  if [ -e "$SERVER_DIR/eaglercraftx" ] && ! mv "$SERVER_DIR/eaglercraftx" "$OLD_CHECKOUT"; then
    rm -rf "$CLONE_STAGE"
    printf 'Could not stage the existing Eaglercraft checkout for update.\n' >&2
    exit 1
  fi
  if ! mv "$CLONE_STAGE/source" "$SERVER_DIR/eaglercraftx"; then
    if [ -e "$OLD_CHECKOUT" ]; then
      if ! mv "$OLD_CHECKOUT" "$SERVER_DIR/eaglercraftx"; then
        printf 'Could not restore the previous Eaglercraft checkout; it remains at %s.\n' "$OLD_CHECKOUT" >&2
        rm -rf "$CLONE_STAGE"
        exit 1
      fi
    fi
    rm -rf "$CLONE_STAGE"
    printf 'Could not install the Eaglercraft source checkout. Existing server data was not changed.\n' >&2
    exit 1
  fi
  rm -rf "$OLD_CHECKOUT"
  rmdir "$CLONE_STAGE"
  mkdir -p "$SERVER_DIR/eaglercraftx/eaglercraftx"
  cd "$SERVER_DIR/eaglercraftx/eaglercraftx"
else
  cd "$SERVER_DIR/eaglercraftx"
fi
if [ -f "client_version" ] && [ -f "gateway_version" ]; then
  if ! cmp -s "../client_version" "client_version"; then
    for tool in javac wget jq; do
      if ! command -v "$tool" >/dev/null 2>&1; then
        printf 'Cannot update the Eaglercraft client: required command "%s" is missing.\n' "$tool" >&2
        exit 1
      fi
    done
    JAVA11="$(command -v javac)"
    JAVA11="${JAVA11%?}"
    rm -f ../buildconf.json
    sed "s#BASEDIR#$BASEDIR#" ../buildconf_template.json > ../buildconf.json
    download_cached_file "http://www.modcoderpack.com/files/mcp918.zip" /tmp/mcp918.zip || exit 1
    download_cached_file "https://launcher.mojang.com/v1/objects/0983f08be6a4e624f5d85689d1aca869ed99c738/client.jar" /tmp/1.8.8.jar || exit 1
    download_cached_file "https://launchermeta.mojang.com/v1/packages/f6ad102bcaa53b1a58358f16e376d548d44933ec/1.8.json" /tmp/1.8.json || exit 1
    if ! jar tf /tmp/mcp918.zip >/dev/null 2>&1 || ! jar tf /tmp/1.8.8.jar >/dev/null 2>&1; then
      if ! jar tf /tmp/mcp918.zip >/dev/null 2>&1; then
        rm -f /tmp/mcp918.zip
      fi
      if ! jar tf /tmp/1.8.8.jar >/dev/null 2>&1; then
        rm -f /tmp/1.8.8.jar
      fi
      printf 'A cached Eaglercraft build archive was invalid and has been removed. Run start again to download it again.\n' >&2
      exit 1
    fi
    if ! jq empty /tmp/1.8.json >/dev/null 2>&1; then
      rm -f /tmp/1.8.json
      printf 'The cached Minecraft version manifest was invalid and has been removed. Run start again to download it again.\n' >&2
      exit 1
    fi
    cd ..
    if command -v tmux >/dev/null 2>&1; then
      tmux new -d -s placeholder "java -Xmx128M PlaceHTTPer 8080 Compiling the latest client.... Please wait!"
    fi
    cd eaglercraftx
    "$JAVA11" -Xmx512M -cp "buildtools/BuildTools.jar" net.lax1dude.eaglercraft.v1_8.buildtools.gui.headless.CompileLatestClientHeadless -y ../buildconf.json
    retVal=$?
    if command -v tmux >/dev/null 2>&1; then
      tmux kill-session -t placeholder 2>/dev/null || true
    fi
    if [ "$retVal" -ne 0 ]; then
      printf 'Eaglercraft client build failed (exit %s); server startup was cancelled. Run start again to retry.\n' "$retVal" >&2
      exit "$retVal"
    fi
    if [ ! -d /tmp/output ] || [ -z "$(find /tmp/output -mindepth 1 -maxdepth 1 -print -quit)" ]; then
      printf 'Eaglercraft client build produced no files; server startup was cancelled. Run start again to retry.\n' >&2
      exit 1
    fi
    if ! cp -r /tmp/output/. ../web/; then
      printf 'Could not install the built Eaglercraft client into web/; server startup was cancelled.\n' >&2
      exit 1
    fi
    if ! cp client_version ../client_version; then
      printf 'Could not record the built Eaglercraft client version; server startup was cancelled.\n' >&2
      exit 1
    fi
    rm -rf /tmp/##EAGLER.TEMP##
    rm -rf /tmp/teavm
    rm -rf /tmp/output
  fi
  if ! cmp -s "../gateway_version" "gateway_version"; then
    gateway_jar="gateway/EaglercraftXBungee/EaglerXBungee-Latest.jar"
    if [ ! -s "$gateway_jar" ] || ! jar tf "$gateway_jar" >/dev/null 2>&1; then
      printf 'Eaglercraft gateway version changed but its plugin jar is missing or invalid; server startup was cancelled.\n' >&2
      exit 1
    fi
    if ! cp "$gateway_jar" ../bungee/plugins/EaglercraftXBungee.jar ||
      ! cp gateway_version ../gateway_version; then
      printf 'Could not install the Eaglercraft gateway update; server startup was cancelled.\n' >&2
      exit 1
    fi
  fi
fi

cd "$SERVER_DIR"
fi

# run it!!
if ! jar tf bungee/bungee.jar >/dev/null 2>&1; then
  printf 'Cannot start: bungee/bungee.jar is missing or invalid. Restore the Velocity jar before retrying.\n' >&2
  exit 1
fi

if [ -f "server/server.jar" ]; then
  if ! jar tf server/server.jar >/dev/null 2>&1; then
    printf 'Cannot start: server/server.jar is invalid. Restore the backend jar before retrying.\n' >&2
    exit 1
  fi
elif [ ! -x "cuberite/Cuberite" ]; then
  printf 'Cannot start: server/server.jar and executable cuberite/Cuberite are both missing.\n' >&2
  exit 1
fi

if command -v tmux >/dev/null 2>&1; then
  cd bungee
  tmux new -d -s server "java -Xmx128M -jar bungee.jar; tmux kill-session -t server"
  cd ../server
  if [ ! -f "server.jar" ] && [ -d "../cuberite" ]; then
    cd ../cuberite
    tmux splitw -t server -v "BIND_ADDR=127.0.0.1 LD_PRELOAD=../bindmod.so ./Cuberite; tmux kill-session -t server"
  else
    tmux splitw -t server -v "java -Djline.terminal=jline.UnsupportedTerminal $SERVER_JVM_FLAGS -jar server.jar nogui; tmux kill-session -t server"
  fi
  cd ..
else
  (cd bungee && exec java -Xmx128M -jar bungee.jar) &
  PROXY_PID=$!
  if [ ! -f "server/server.jar" ] && [ -d "cuberite" ]; then
    (cd cuberite && exec env BIND_ADDR=127.0.0.1 LD_PRELOAD=../bindmod.so ./Cuberite) &
  else
    (cd server && exec java -Djline.terminal=jline.UnsupportedTerminal $SERVER_JVM_FLAGS -jar server.jar nogui) &
  fi
  BACKEND_PID=$!
  cleanup_server_processes() {
    trap - INT TERM EXIT
    kill "$BACKEND_PID" "$PROXY_PID" 2>/dev/null || true
    wait "$BACKEND_PID" "$PROXY_PID" 2>/dev/null || true
  }
  trap cleanup_server_processes EXIT
  trap 'exit 130' INT
  trap 'exit 143' TERM
fi

ensure_codespaces_port_public

if command -v tmux >/dev/null 2>&1; then
  if [ "${KING24790_START_ATTACH:-1}" != "1" ]; then
    printf 'Server started in tmux session "server". Use ./start --attach for its console.\n'
    exit 0
  fi
  while tmux has-session -t server
  do
    tmux a -t server
  done
  printf 'The server tmux session has stopped.\n' >&2
  exit 1
else
  wait -n "$PROXY_PID" "$BACKEND_PID"
  SERVER_EXIT=$?
  if [ "$SERVER_EXIT" -eq 0 ]; then
    SERVER_EXIT=1
  fi
  printf 'A server process stopped (exit %s).\n' "$SERVER_EXIT" >&2
  exit "$SERVER_EXIT"
fi

echo 'you might need to agree to the EULA in the server folder'
