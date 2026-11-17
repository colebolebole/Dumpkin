<p align="center">
  <img src="docs/logo.svg" alt="AnCraft logo" width="128" height="128">
</p>

<h1 align="center">AnCraft</h1>

<p align="center">
  Host <a href="https://pumpkinmc.org/">Pumpkin</a> Minecraft servers from your Android phone.<br>
  A Material 3 Expressive app, currently in <strong>alpha</strong>.
</p>

---

## Features

- **One-tap engine.** Downloads the official `pumpkin-aarch64-android` build (Stable, Canary or Nightly) from Pumpkin's GitHub releases.
- **Multiple servers.** Each has its own folder, port, world and `pumpkin.toml`.
- **Runs in the background.** A foreground service with a wake lock and low-latency Wi-Fi lock keeps servers up, with a "Stop all" button in the notification.
- **Live console.** Send commands and watch players, uptime and RAM use. Tap the command box and the console takes over the whole screen; close the keyboard and everything comes back.
- **Roomy settings.** The status card rolls up as you scroll down the settings and returns as soon as you scroll up.
- **Tuned for phones.** Memory limit, CPU threads, crash auto-restart, and join-speed settings (see below).
- **Settings that edit `pumpkin.toml` in place**, plus a raw editor for everything else.
- **Shows the address** players should connect to on your network.

## Install

1. Download `AnCraft-<version>.apk` from the [Releases](https://github.com/colebolebole/AnCraft/releases) page.
2. Sideload it on an **arm64** phone running Android 8.0 or newer.
3. Open the **Engine** tab and download a Pumpkin build.
4. On the **Servers** tab, tap **New server**, then start it. Players on the same Wi-Fi join with the address shown on the server screen.

> [!NOTE]
> Releases are signed with a debug key for now, so a future build signed differently can't update in place. Uninstalling
> removes server data, so back up worlds first.

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

- **Memory limit.** Pumpkin is native Rust code with no JVM-style `-Xmx` heap, so AnCraft reads the server's resident
  memory (`VmRSS` in `/proc/<pid>/status`) every 2 seconds. If it stays over the limit for two samples in a row, the chosen
  action runs. It is a soft limit, not a hard cap, and needs the process id, which some devices may not expose.
- **CPU threads.** Sets `TOKIO_WORKER_THREADS` and `RAYON_NUM_THREADS` for the server process. Whether Pumpkin honours
  them is unconfirmed.
- **Readiness and player count.** AnCraft uses the standard Minecraft status ping against the local port: every 250 ms
  while starting, so "Online" appears as soon as the port opens, then every 10 seconds.
- **Console.** Output is published in batches instead of rebuilding the log list per line, which matters during the startup burst.
- **Wi-Fi lock.** Uses the low-latency mode on Android 10+ (the old high-performance mode does nothing on Android 14+).
- **Why `targetSdk = 28`.** The engine is downloaded at runtime and run from app storage, which Android 10+ forbids for
  apps targeting API 29+. Like Termux, AnCraft targets 28 and is meant to be sideloaded rather than published on Play. If a
  device still refuses a direct exec, the runtime retries through `/system/bin/linker64`.

## Build

Requires JDK 17+ and the Android SDK (platform 36), or just Android Studio. The Gradle wrapper is checked in.

```bash
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease      # minified, signed with the debug key
./gradlew testDebugUnitTest    # config patcher, profile round-trip, Engine-screen smoke tests
```

Material 3 is pinned to `1.5.0-alpha10`: the Expressive APIs aren't public in 1.4.0 stable, and newer 1.5 builds need
Android Gradle Plugin 9+. Expect some API churn when you bump it.

## Notes

- Pumpkin is under heavy development and not every vanilla mechanic exists yet. **Keep backups of your worlds.**
- If a server won't start after changing settings, use **Reset configuration** (Settings → Advanced). Key names come from
  Pumpkin's docs and may change between versions.
- Pumpkin ignores unknown keys, but a value of the wrong type resets the whole config to defaults, so AnCraft only writes
  documented keys with values inside the documented ranges.

<details>
<summary>Keys AnCraft writes to <code>pumpkin.toml</code></summary>

- Top level: `default_gamemode`, `default_difficulty`, `use_favicon`
- `[networking.java]`: `address`, `online_mode`, `max_players`, `view_distance`, `simulation_distance`, `motd`
- `[networking.java.compression]`: `enabled`, `level`
- `[networking.java.authentication.textures]`: `enabled`
- `[networking.bedrock]`: `enabled`
- `[commands]`: `use_tty = false`
- First run only, all set to `false`: `[telemetry]`, `[networking.query]` and `[networking.management]` `enabled`

</details>

## Credits

AnCraft is an unofficial project and is not affiliated with Pumpkin, Mojang or Microsoft. Pumpkin is licensed GPL-3.0;
AnCraft only downloads and runs its released binary. The app has no license file yet.
