# HoneyBeat CI

HoneyBeat by NeonBear, based on Metrolist (GPL-3.0).

1. Create a new GitHub repo and upload everything in this zip (keep the `.github` folder).
2. Open the **Actions** tab -> "Build HoneyBeat (Metrolist-based)" -> **Run workflow**.
3. When it finishes, download the **HoneyBeat-apk** artifact.

The workflow clones the latest Metrolist, runs `rename_to_honeybeat.sh`, adds
`overlay/HoneyBeatUi.kt` (theme, cards, floating nav, NeonBear footer) and builds a debug APK.
The default app id becomes com.neonbear.honeybeat.music, so it installs next to your existing HoneyBeat.
Metrolist is GPL-3.0: keep this repo public and keep the NOTICE.
