# HoneyBeat

HoneyBeat is a music player and a downloader (Fetch, the old Cave) in one Android app, styled like Cub by NeonBear.

- **Library**: "All music" plus one playlist for every folder in `Music/Cave` (`Music/Cave/Road Trip` is a playlist called Road Trip).
  Songs and playlists share one tab, with album covers. Open a playlist and press **Cover** to give it your own picture.
- **Playing**: cover in the middle, title, seek bar and controls pinned at the bottom. On other tabs a mini player
  has previous / play / next.
- **Fetch**: paste a YouTube Music, Spotify or Apple Music playlist link, or search for a song and press **Get**.
  Files go to `Music/Cave`. Playlists keep their own folder, searched songs go straight into All music.
  Sync only downloads new songs.
- **Options**: playback settings and every Fetch setting: parallel downloads (1, 2, 4, 8 or all at once), audio quality
  (MP3 320 kbps or best original M4A), cover art in downloads, ask on mobile data, auto-update yt-dlp, Update yt-dlp.

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
- YouTube audio is lossy to begin with, so MP3 320 kbps is the highest MP3 setting but not better than the source.
- If downloads start failing, press **Update yt-dlp** in Options. The red "Last error" line and the Log card in Fetch show why a song failed.
- Fonts: Tektur and Outfit, both under the SIL Open Font License (see `licenses/`).
- Only download music you have the right to copy.
