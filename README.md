<p align="center">
  <img src="docs/logo.svg" alt="Dumpkin logo" width="128" height="128">
</p>

<h1 align="center">Dumpkin</h1>

<p align="center">
  Host <a href="https://pumpkinmc.org/">Pumpkin</a> Minecraft servers from your Android phone, and manage them from any browser.<br>
  A Material 3 Expressive app, currently in <strong>alpha</strong>. Formerly called AnCraft.
</p>

---

## Features

- **One-tap engine.** Downloads the official `pumpkin-aarch64-android` build (Stable, Canary or Nightly) from Pumpkin's GitHub releases.
- **Web dashboard.** Turn it on and create, start, stop and manage servers from a browser on your PC or another phone: console, every setting, the config file and engine downloads. Password protected (see [Web dashboard](#web-dashboard)).
- **Multiple servers.** Each has its own folder, port, world and `pumpkin.toml`.
- **Runs in the background.** A foreground service with a wake lock and low-latency Wi-Fi lock keeps servers up, with a "Stop all" button in the notification.
- **Live console.** Send commands and watch players, uptime and RAM use. Tap the command box and the console takes over the whole screen; close the keyboard and everything comes back.
- **Roomy settings.** The status card rolls up as you scroll down the settings and returns as soon as you scroll up.
- **Tuned for phones.** Memory limit, CPU threads, crash auto-restart, and join-speed settings (see below).
- **Settings that edit `pumpkin.toml` in place**, plus a raw editor for everything else.
- **Shows the address** players should connect to on your network.

## Install

1. Download `Dumpkin-<version>.apk` from the [Releases](https://github.com/colebolebole/Dumpkin/releases) page.
2. Sideload it on an **arm64** phone running Android 8.0 or newer.
3. Open the **Engine** tab and download a Pumpkin build.
4. On the **Servers** tab, tap **New server**, then start it. Players on the same Wi-Fi join with the address shown on the server screen.

> [!NOTE]
> **Coming from AnCraft?** The app was renamed and its Android package changed from `app.ancraft` to `app.dumpkin`, so
> Dumpkin installs *next to* AnCraft instead of updating it, and worlds are not migrated. Keep AnCraft until you've
> finished with its servers, then uninstall it. The `0.1.0` and `0.2.0` AnCraft releases were signed with a throwaway
> debug key; Dumpkin releases use a proper release key.

## Web dashboard

Open the **Dashboard** tab in the app and turn the switch on. Dumpkin starts a small web server on your phone (port
`8080` by default). It shows its address, for example `http://192.168.0.83:8080`; open that in a browser on any device on
the same Wi-Fi, sign in with the password from the same tab, and you can:

- create, start, stop and delete servers, with a live status for each
- watch the console and send commands
- change every setting from the table below, and edit `pumpkin.toml` directly
- download, update and remove engine builds

It works fully offline (nothing is loaded from the internet), follows your light or dark theme, and is laid out for
phones as well as desktops.

**Security.** The dashboard is plain HTTP meant for your home network, protected by a password:

- A random password is generated for you (`XXXX-XXXX-XXXX`). Change it, or make a new one, from the Dashboard tab; either signs every browser out.
- After 5 wrong passwords an address is locked out for a minute, doubling each time up to 15 minutes.
- Sessions use random 256-bit tokens in `HttpOnly`, `SameSite=Strict` cookies and last a week, or until the app restarts.
- State-changing requests must carry a custom header and a matching `Origin`, and the `Host` header must be an address or local name. These block cross-site requests and DNS rebinding.
- **Don't forward the port to the internet.** Anyone on your Wi-Fi who learns the password can start, stop and delete your servers.
- While the dashboard is on, Dumpkin keeps running in the background (a notification shows it), which uses some battery.

<details>
<summary>JSON API</summary>

Everything the page does goes through a JSON API, so you can script it. Sign in with `POST /api/login`
(`{"password": "..."}`), keep the `dk_session` cookie, and send `X-Requested-With: Dumpkin` on every non-GET request.

| Method and path | What it does |
| --- | --- |
| `GET /api/state` | Phone info, engine builds and every server with its status |
| `POST /api/servers` | Create a server (`name` plus any setting) |
| `GET`, `PATCH`, `DELETE /api/servers/{id}` | Read, change or delete a server (delete only while stopped) |
| `POST /api/servers/{id}/start`, `/stop` | Start or stop it |
| `POST /api/servers/{id}/command` | Send a console command (`{"command": "list"}`) |
| `GET /api/servers/{id}/logs?after=N` | Console lines after line `N`; `reset` is true if you must start over |
| `GET`, `PUT /api/servers/{id}/config`, `POST .../config/reset` | Read, write or reset `pumpkin.toml` |
| `GET /api/engine`, `POST /api/engine/refresh`, `/download`, `DELETE /api/engine/{id}` | Manage engine builds |

</details>

## Server settings

Open a server and switch to the **Settings** tab. Changes to a running server apply after a restart, except the memory
limit, which applies as soon as you save.

| Section | Setting | What it does |
| --- | --- | --- |
| General | Name, MOTD, port, max players | Port must be 1024-65535 and unique across your servers |
| Gameplay | Game mode, difficulty | Written to `default_gamemode` / `default_difficulty` |
| Performance | **Quick pick**: Fast join (6/4), Balanced (8/6), Detailed (12/8) | Sets view and simulation distance together. New servers start on Fast join |
| | View distance (2-24), simulation distance (2-16) | Joining sends every chunk in view distance, so lower values join much faster |
| Resources | **Limit memory** | Soft RAM cap, from 256 MB up to about 75% of the phone's RAM |
| | **When exceeded** | *Warn only* logs a warning; *Restart server* restarts it to free memory |
| | **CPU threads** | Auto, or 1 up to your core count |
| | **Restart after a crash** | Retries up to 3 times within 5 minutes, then gives up |
| Network | **Packet compression** | *Off* (fastest on Wi-Fi, more bandwidth), *Fast* (level 1, default) or *Balanced* (Pumpkin's level 4) |
| | Online mode | Verifies players with Microsoft accounts. Each join waits on a Mojang lookup; offline mode skips it but lets anyone use any name |
| | **Fetch skins and capes** | On by default. Turn off to trim join work; players may show default skins |
| | **Lightweight server-list ping** | On by default. Leaves the server icon out of the multiplayer-list response |
| | Bedrock support | Experimental. Also listens on UDP 19132, which only one server can use |
| Engine | Build to use | "Newest", or pin a specific installed build |

## Engine tab

- **Installed** and **Available on GitHub** builds, with download progress and delete.
- **Keep servers alive.** Shortcut to Android's "ignore battery optimisation" prompt so servers aren't frozen when the screen is off.
- **Keep screen on while hosting.** Android only grants its low-latency Wi-Fi mode while the screen is on, so this gives
  players the lowest ping at the cost of battery. Plug the phone in.
- **Tips.** Use 5 GHz Wi-Fi, keep the phone cool and lower view distance. Nightly builds are fully optimised; Canary skips
  link-time optimisation and runs slower.

## How it works

- **Memory limit.** Pumpkin is native Rust code with no JVM-style `-Xmx` heap, so Dumpkin reads the server's resident
  memory (`VmRSS` in `/proc/<pid>/status`) every 2 seconds. If it stays over the limit for two samples in a row, the chosen
  action runs. It is a soft limit, not a hard cap, and needs the process id, which some devices may not expose.
- **CPU threads.** Sets `TOKIO_WORKER_THREADS` and `RAYON_NUM_THREADS` for the server process. Whether Pumpkin honours
  them is unconfirmed.
- **Readiness and player count.** Dumpkin uses the standard Minecraft status ping against the local port: every 250 ms
  while starting, so "Online" appears as soon as the port opens, then every 10 seconds.
- **Console.** Output is published in batches instead of rebuilding the log list per line, which matters during the startup burst.
- **Wi-Fi lock.** Uses the low-latency mode on Android 10+ (the old high-performance mode does nothing on Android 14+).
- **Why `targetSdk = 28`.** The engine is downloaded at runtime and run from app storage, which Android 10+ forbids for
  apps targeting API 29+. Like Termux, Dumpkin targets 28 and is meant to be sideloaded rather than published on Play. If a
  device still refuses a direct exec, the runtime retries through `/system/bin/linker64`.

## Build

Requires JDK 17+ and the Android SDK (platform 36), or just Android Studio. The Gradle wrapper is checked in.

```bash
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease      # minified; signed with the release key if configured (see below)
./gradlew testDebugUnitTest    # config patcher, validation, web server and API, login security, UI smoke tests
```

The web server is tested end to end over real sockets against a fake backend, so no phone is needed. To look at the
dashboard UI the same way, run the preview (it serves the real server and web files for 20 minutes) and open
`http://127.0.0.1:18080`, password `preview-1234`:

```bash
DUMPKIN_PREVIEW=1 ./gradlew testDebugUnitTest --tests '*WebPreview*' -i
```

The dashboard's HTML, CSS and JS live in `app/src/main/assets/web/` and are plain files with no build step.

### Release signing

Release builds are signed with a private key that is **not** in this repository.

1. Create a keystore once (keep it outside the repo):

   ```bash
   keytool -genkeypair -keystore ~/.dumpkin/dumpkin-release.jks -storetype PKCS12 -alias dumpkin \
     -keyalg RSA -keysize 4096 -validity 10950 -dname "CN=Dumpkin"
   ```

2. Copy `keystore.properties.example` to `keystore.properties` (git-ignored) and fill in the path and passwords, or set the
   `DUMPKIN_KEYSTORE_FILE`, `DUMPKIN_KEYSTORE_PASSWORD`, `DUMPKIN_KEY_ALIAS` and `DUMPKIN_KEY_PASSWORD` environment variables.
3. Build with `./gradlew assembleRelease -PrequireReleaseKey`. That flag makes the build fail if the key is missing,
   instead of quietly falling back to the debug key (which is what happens without it, with a warning, so contributors
   can still build an installable APK). Never publish a fallback build.
4. Check the result with `apksigner verify --print-certs app-release.apk`: the signer should be `CN=Dumpkin`.

> [!IMPORTANT]
> **Back up the keystore file and its passwords** (for example in a password manager). Android only installs an update
> if it is signed with the same key as the installed app, so if the key is lost, every user has to uninstall and
> reinstall, losing their servers. Don't commit the key or `keystore.properties`.

Material 3 is pinned to `1.5.0-alpha10`: the Expressive APIs aren't public in 1.4.0 stable, and newer 1.5 builds need
Android Gradle Plugin 9+. Expect some API churn when you bump it.

## Notes

- Pumpkin is under heavy development and not every vanilla mechanic exists yet. **Keep backups of your worlds.**
- If a server won't start after changing settings, use **Reset configuration** (Settings → Advanced). Key names come from
  Pumpkin's docs and may change between versions.
- Pumpkin ignores unknown keys, but a value of the wrong type resets the whole config to defaults, so Dumpkin only writes
  documented keys with values inside the documented ranges.

<details>
<summary>Keys Dumpkin writes to <code>pumpkin.toml</code></summary>

- Top level: `default_gamemode`, `default_difficulty`, `use_favicon`
- `[networking.java]`: `address`, `online_mode`, `max_players`, `view_distance`, `simulation_distance`, `motd`
- `[networking.java.compression]`: `enabled`, `level`
- `[networking.java.authentication.textures]`: `enabled`
- `[networking.bedrock]`: `enabled`
- `[commands]`: `use_tty = false`
- First run only, all set to `false`: `[telemetry]`, `[networking.query]` and `[networking.management]` `enabled`

</details>

## Credits

Dumpkin is an unofficial project and is not affiliated with Pumpkin, Mojang or Microsoft. Pumpkin is licensed GPL-3.0;
Dumpkin only downloads and runs its released binary. The app has no license file yet.
