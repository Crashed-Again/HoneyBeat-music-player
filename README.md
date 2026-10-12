# HoneyBeat CI

HoneyBeat by NeonBear, based on Metrolist (GPL-3.0).

1. Create a new GitHub repo and upload everything in this zip (keep the `.github` folder).
2. Actions tab -> "Build HoneyBeat (Metrolist-based)" -> Run workflow.
3. Download the **HoneyBeat-apk** artifact.

The workflow clones the latest Metrolist, renames the app (name + application id
`com.neonbear.honeybeat.ytm`, so it installs next to your other HoneyBeat), swaps in the
HoneyBeat logo from `overlay/res`, adds `overlay/HoneyBeatUi.kt`, creates a debug keystore
and builds a debug APK. Keep this repo public (GPL-3.0).
