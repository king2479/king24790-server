# EaglerCraftX Server

## Quick start
From the repository directory, run `./start` (or `start` if it is on your shell's `PATH`). The launcher starts the proxy and backend together, checks Eaglercraft files, and keeps the server console available in `tmux` when installed. Do not launch `bungee.jar` and `server.jar` separately.

In Codespaces, the launcher tries to make only port `8081` public. If it reports that GitHub CLI is not authenticated, run `gh auth login -s codespace`, then run `./start` again. You can also set the port visibility from the Codespaces Ports panel. Keep the terminal open while the server runs.

The first startup may take longer while sources and build dependencies are fetched. Failed downloads or client builds stop startup with an error; retry `./start` after correcting the network or dependency problem. `./starty` can be used to attach to the proxy and backend panes and requires `tmux`.

Paper's verbose timings detail is disabled to reduce diagnostic overhead while basic timings remain enabled. This configuration change takes effect after the next server restart.

## Health checks and backups
Run `./status.sh` to check the Eaglercraft listener, Minecraft proxy, and backend listener without changing server state. The backend should remain local-only behind the proxy.

For a manual backup, first save and stop the server, then run `./backup.sh`. It refuses to copy worlds while server ports or the managed tmux session are active. Verified archives are written under `backups/` with private file permissions and are excluded from Git. Keep an additional copy outside this Codespace; its local files may not survive deleting the Codespace.

## Homes and warps
Build the bundled 1.8.8-compatible plugin with `./build-homes-warps.sh`; it installs the jar for the next server restart and does not interrupt a running server. Players use `/sethome` and `/home` for one personal home. Use `/warps` to list public warps; only operators can use `/setwarp <name>` and `/delwarp <name>`. Data is stored separately under `server/plugins/HomesWarps/`.

## Credits
Original Project: Lax1Dude
<br>
1.12 Project: PeytonPlayz595
<br>
Original Server Fork: EcoliEater87
<br> 
Adapted to Canary Craft (ADSCRAFT): QuizzityMC
<br>

## Connecting
Open the web client from the Codespaces forwarded URL for port `8081`. In the client, add a server using that URL with `https://` changed to `wss://`. For a separately hosted server, forward the proxy's configured Minecraft port; keep backend port `25565` private behind the proxy.
