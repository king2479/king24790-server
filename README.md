# EaglerCraftX Server

## Quick start
From the repository directory, run `./start` (or `start` if it is on your shell's `PATH`). The launcher starts the proxy and backend together, checks Eaglercraft files, and keeps the server console available in `tmux` when installed. Do not launch `bungee.jar` and `server.jar` separately.

In Codespaces, the launcher tries to make only port `8081` public. If it reports that GitHub CLI is not authenticated, run `gh auth login -s codespace`, then run `./start` again. You can also set the port visibility from the Codespaces Ports panel. Keep the terminal open while the server runs.

The first startup may take longer while sources and build dependencies are fetched. If the Eaglercraft source checkout is unavailable but the existing web client and proxy plugin are intact, the server starts with those deployed files and skips Eaglercraft updates. Failed downloads or client builds stop startup with an error; retry `./start` after correcting the network or dependency problem. `./starty` can be used to attach to the proxy and backend panes and requires `tmux`.

Paper's verbose timings detail is disabled to reduce diagnostic overhead while basic timings remain enabled. This configuration change takes effect after the next server restart.
The backend launcher uses a fixed 2 GB heap and the requested G1 garbage-collection tuning; this takes effect after restart. It deliberately avoids `sudo` so the server stays under the current user account.

## Health checks and backups
Run `./status.sh` to check the Eaglercraft listener, Minecraft proxy, and backend listener without changing server state. The backend should remain local-only behind the proxy.

For a manual backup, first save and stop the server, then run `./backup.sh`. It refuses to copy worlds while server ports or the managed tmux session are active. Verified archives are written under `backups/` with private file permissions and are excluded from Git. Keep an additional copy outside this Codespace; its local files may not survive deleting the Codespace.

## Homes and warps
Build the bundled 1.8.8-compatible plugin with `./build-homes-warps.sh`; it installs the jar for the next server restart and does not interrupt a running server. Players use `/sethome` and `/home` for one personal home. Use `/warps` to list public warps; only operators can use `/setwarp <name>` and `/delwarp <name>`. The shopping district is north of Times Square; use `/shopping` to go there. Data is stored separately under `server/plugins/HomesWarps/`.

Operators can run `/citylights` to add neon signs and colorful window panels to intact city towers; occupied building spaces are left unchanged. Re-running the command is safe and extends the night-time Times Square lighting around the existing skyline.

Operators can run `/citylights` to add colorful glowstone-and-stained-glass neon panels and colored signs to the two existing Times Square towers. It only places blocks in empty locations after checking that the expected tower facades are still present; occupied, changed, or unrecognized locations are left untouched. It is safe to run again and never rebuilds or clears the city. The command is available after restarting the server with a newly built HomesWarps plugin.

## Player registration and login
This server runs Minecraft 1.12.2. AuthMeReloaded 5.6.0's legacy jar is installed for Minecraft 1.8–1.17; the EaglerXVelocity built-in authentication setting is disabled so players are not asked to log in twice. New and returning players have two minutes to authenticate: register in chat with `/register <password> <password>` or log in with `/login <password>`. Use a unique password of at least 10 characters. AuthMe stores hashed credentials in `server/plugins/AuthMe/authme.db`; the old Eaglercraft auth database is preserved but is not automatically migrated, so existing Eagler accounts must register with AuthMe.

The backend is behind a proxy that does not forward player IPs, so AuthMe cannot apply a meaningful per-IP registration cap; registration is unlimited by IP. AuthMe also reports that its optional inventory protection needs ProtocolLib, which is not installed. Its movement and unauthenticated-command restrictions remain enabled, but the inventory protection feature is unavailable.

Run `./backup.sh` while the server is stopped to include the AuthMe database in your backup. Back up the archive somewhere outside the Codespace too.

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
