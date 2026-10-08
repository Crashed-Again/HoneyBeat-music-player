# Cub Player

A small music player for Android, styled like Cub by NeonBear: dark panels, toggle cards, blue accent.
It plays the audio files already on the phone (MP3, M4A, FLAC and so on), keeps playing with the
screen off, and has lock-screen and notification controls. Works well with MP3s that Cave puts in `Music/Cave`.

## Build the APK

Same three options as Cave for Android.

Option A, Android Studio: open this folder, wait for the sync, then **Build > Build APK(s)**.
The file ends up in `app/build/outputs/apk/debug/`.

Option B, no Android Studio setup: run `build-apk.bat`. It downloads its own Gradle 8.9 the first time.
It needs a JDK 17 (Android Studio's bundled one is picked up automatically) and the Android SDK.
The result is `dist\CubPlayer.apk`.

Option C, no tools at all: put this folder in a GitHub repository (the `.github` folder must come with it).
Open the repo's **Actions** tab, pick **Build Cub Player APK**, press **Run workflow**, and download
`CubPlayer-apk` from the finished run. The log there also shows any build error.

Install: copy the APK to the phone, open it, allow "install unknown apps".

## Using it

- **Songs**: your library, with search. Tap a song to play it and queue the list you see.
- **Playing**: cover art, seek bar, previous / play / next, shuffle and repeat.
- **Options**: toggles for shuffle, repeat all, Music/Cave only, and cover art. **Rescan** refreshes the list.

First launch asks for permission to read audio (and to show the playback notification on Android 13+).
