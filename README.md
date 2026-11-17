# AnCraft

A Material 3 Expressive Android app that hosts [Pumpkin](https://pumpkinmc.org/) Minecraft servers on your phone.

## Features

- Download the official `pumpkin-aarch64-android` build (Stable / Canary / Nightly) straight from Pumpkin's GitHub releases
- Create several servers, each with its own folder, port and world
- Start/stop from a foreground service (wake lock + Wi-Fi lock, "Stop all" notification action)
- Live console with command input, player count, uptime and live RAM use
- Settings form that edits `pumpkin.toml` in place (plus a raw editor)
- Shows the LAN address players should connect to

## Server settings

Open a server and switch to the **Settings** tab. Changes to a running server take effect after a restart, except the
memory limit, which applies as soon as you save.

| Section | Setting | What it does |
| --- | --- | --- |
| General | Name, MOTD, port, max players | Port must be 1024-65535 and unique across your servers |
| Gameplay | Game mode, difficulty | Written to `default_gamemode` / `default_difficulty` |
| Performance | **Quick pick**: Fast join (6/4), Balanced (8/6), Detailed (12/8) | Sets view and simulation distance together. New servers start on Fast join |
| | View distance (2-24), simulation distance (2-16) | Joining sends every chunk in view distance, so lower values mean much faster joins |
| Resources | **Limit memory** | Soft RAM cap, slider from 256 MB up to about 75% of the phone's RAM |
| | **When the limit is exceeded** | *Warn only* logs a warning; *Restart server* stops and restarts the server to free memory |
| | **CPU threads** | Auto, or 1 to your core count |
| | **Restart after a crash** | Retries up to 3 times within 5 minutes, then gives up |
| Network | **Packet compression**: Off / Fast / Balanced | Compressing chunk packets is the main CPU cost of a join. *Fast* (default) is level 1, *Balanced* is Pumpkin's default level 4, *Off* is fastest on Wi-Fi but uses more bandwidth |
| | Online mode | Verify players with Microsoft accounts. Each join waits on a Mojang lookup; offline mode skips it but lets anyone use any name |
| | **Fetch skins and capes** | On by default. Turn off to trim join work; players may show default skins |
| | **Lightweight server-list ping** | On by default. Leaves the server icon out of the multiplayer-list response (`use_favicon = false`) |
| | Bedrock support (experimental) | Also listens on UDP 19132 |
| Engine | Build to use | "Newest" or a specific installed build |

The server screen shows current RAM use, with a progress bar against the limit when one is set.

### How the resource settings work

- **Memory limit.** Pumpkin is native Rust code, so it has no JVM-style `-Xmx` heap setting. AnCraft instead reads the
  server process's resident memory (`VmRSS` in `/proc/<pid>/status`) every 2 seconds. If it stays above the limit for two
  samples in a row, AnCraft applies the chosen action. It is a soft limit, not a hard cap, and it needs the process id,
  which some devices may not expose (the console then says memory usage isn't available).
- **CPU threads.** Sets the `TOKIO_WORKER_THREADS` and `RAYON_NUM_THREADS` environment variables for the server process.
  Whether Pumpkin honours them has not been confirmed.
- **Restart after a crash.** Only applies to unexpected exits, never to a stop you requested.

## Engine tab

- **Installed** and **Available on GitHub** lists, with download progress and delete. Servers can pin a build or follow the newest.
- **Keep servers alive.** Shortcut to Android's "ignore battery optimisation" request so the server isn't frozen when the screen is off.
- **Ping and performance.**
  - *Keep screen on while hosting* holds the display on while any server is running. Android only grants apps its
    low-latency Wi-Fi mode while the screen is on, so this gives players the lowest ping at the cost of battery. Plug the phone in.
  - Tips: use 5 GHz Wi-Fi, keep the phone cool, lower view distance. Nightly builds are fully optimised; Canary skips
    link-time optimisation and runs slower.

### Keyboard and layout

While the keyboard is open, the large status card collapses into a one-line status (state, players, address, start/stop)
so the console and its command box always have room.

### Startup and ping behaviour

- Console output is published in batches instead of rebuilding the log list for every line, which matters most during the startup burst.
- While a server is starting, AnCraft checks the port every 250 ms so "Online" appears as soon as it opens. Once running,
  it checks every 10 seconds to read the player count.
- New servers get telemetry, the query listener and the management server switched off in `pumpkin.toml`.
- The Wi-Fi lock uses the low-latency mode on Android 10+ (the old high-performance mode does nothing on Android 14+).

## Build

Requires JDK 17+, the Android SDK (platform 36) and Gradle 8.13 (or Android Studio, which handles all of it). The
wrapper is checked in.

```
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # config patcher, profile round-trip and Engine-screen smoke tests
```

Sideload the APK onto an **arm64** phone.

Material 3 is pinned to `1.5.0-alpha10`: the Expressive APIs are not public in 1.4.0 stable, and newer 1.5 builds require
Android Gradle Plugin 9+. Expect some API churn when you bump it.

## Why `targetSdk = 28`

The engine is downloaded at runtime and executed from the app's private storage. Android 10+ forbids that for apps
targeting API 29+, so, like Termux, AnCraft targets 28 and is meant to be sideloaded rather than published on Play.
If a device still refuses the direct exec, the runtime retries through `/system/bin/linker64`.

## Notes

- Pumpkin is under heavy development; keep backups of worlds.
- Telemetry is switched off in the `pumpkin.toml` AnCraft generates for new servers.
- Bedrock support is Pumpkin-experimental; only one server at a time can use UDP 19132.
- Keys written to `pumpkin.toml`: `default_gamemode`, `default_difficulty`, `use_favicon`, `[networking.java]`
  (`address`, `online_mode`, `max_players`, `view_distance`, `simulation_distance`, `motd`),
  `[networking.java.compression]` (`enabled`, `level`), `[networking.java.authentication.textures] enabled`,
  `[networking.bedrock] enabled`, `[commands] use_tty = false`. On first run also `[telemetry] enabled`,
  `[networking.query] enabled` and `[networking.management] enabled`, all `false`.
- Pumpkin ignores unknown keys, but a value of the wrong type resets the whole config to defaults, so AnCraft only writes
  documented keys with values inside the documented ranges.
- If a server won't start after changing settings, use **Reset configuration** (Settings → Advanced); the key names come
  from Pumpkin's docs and may change between versions.
