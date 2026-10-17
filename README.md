# HoneyBeat

YouTube Music client for Android. Search songs, stream them, and download them into
`Music/HoneyBeat/<playlist>` - every folder there shows up as a playlist in the Library.

## Build on GitHub
1. Create a repo and upload everything in this folder (keep the `.github` folder).
2. Open the **Actions** tab -> **Build HoneyBeat** -> wait for the green check.
3. Download the `HoneyBeat-apk` artifact, unzip, install `app-release.apk`.

No Gradle wrapper needed: the workflow installs Gradle 8.9 and downloads the Outfit/Oxanium fonts.

## If search or playback breaks
YouTube changes often. Bump `newPipeExtractorVersion` in `app/build.gradle.kts` to the newest
release from https://github.com/TeamNewPipe/NewPipeExtractor/releases and push again.

## Credits
Powered by [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor) (GPL-3.0), so this
project is GPL-3.0 as well. By NeonBear.
