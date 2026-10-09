# HoneyBeat

HoneyBeat is a music player and Cave in one Android app, styled like Cub by NeonBear.

- **Library**: "All music" plus one playlist for every folder in `Music/Cave` (so `Music/Cave/Road Trip` is a playlist called Road Trip).
  Songs and playlists live in the same tab, with album covers.
- **Playing**: centered cover, seek bar, previous / play / next, shuffle and repeat. When something is playing and you are
  on another tab, a mini player at the bottom has previous / play / next too.
- **Cave**: paste a YouTube Music, Spotify or Apple Music playlist link and get MP3s in `Music/Cave/<playlist name>`.
  It remembers recent playlists; **Sync** only downloads new songs. yt-dlp and ffmpeg run inside the app.
- **Options**: shuffle, repeat, cover art, Cave cover art, ask on mobile data, Rescan.

Needs Android 10 or newer.

## Build the APK on GitHub

1. Upload the contents of this folder (including the hidden `.github` folder) to a new GitHub repo.
2. Open the **Actions** tab. The build starts by itself, or press **Run workflow** on **Build HoneyBeat APK**.
3. When it finishes, download `HoneyBeat-apk` from the run's Artifacts, unzip it, and install `HoneyBeat.apk` on the phone.
   The APK is large (about 100 MB) because it carries yt-dlp, Python and ffmpeg.

Other ways: Android Studio (Build > Build APK(s)), or `build-apk.bat` (result in `dist\HoneyBeat.apk`).

## Notes

- Playlists must be public. Spotify's public page only exposes about 100 tracks.
- Spotify and Apple Music songs are searched on YouTube, so the match can occasionally be a wrong version.
- If downloads start failing, open Cave and press **Update yt-dlp**.
- The red "Last error" line and the Log card in Cave show why a song failed.
- Only download music you have the right to copy.
