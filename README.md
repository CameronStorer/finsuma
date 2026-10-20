<img src="assets/icons/icon.png" alt="Finsuma logo" title="Finsuma" align="right" height="72px" width="72px" />

# Finsuma

A playlist-first music player for [Navidrome](https://www.navidrome.org/) and other Subsonic
servers. Finsuma is a fork of [Feishin](https://github.com/jeffvli/feishin): it keeps Feishin's
player, queue logic and Auto DJ, and builds around the way you actually listen, starting from your
playlists.

This repository holds three parts:

| Folder | What it is |
| --- | --- |
| `/` (root) | **Finsuma desktop** (Electron, React). Fork of Feishin. |
| [`server/`](server/) | **Finsuma server**: a small service that runs next to your music server. It downloads YouTube/SoundCloud audio as tagged MP3s and stores playlist ratings and pins. |
| [`mobile/`](mobile/) | **Finsuma for Android**. Fork of [Tempus](https://github.com/eddyizm/tempus), kept as a `git subtree`. |

## What Finsuma adds

- **Playlists first.** Playlists lead the sidebar and the home page; rarely used sections are
  hidden by default.
- **YouTube and SoundCloud tabs** (desktop). Browse either site inside the app and press the
  download button to save the current song or playlist to your music server as an MP3, with a
  cleaned-up artist and title and square cover art. Navidrome rescans automatically.
- **Playlist covers.** Generate a three-colour gradient with 1-4 letters, use a photo of the
  playlist's top artist, the cover of its most-played song or any song you pick, or go back to the
  usual 4-album grid. A batch mode updates many playlists at once, in the background.
- **Playlist ratings and pins.** Rate playlists, sort by rating, and pin favourites to the top.
  (Navidrome can't store these for playlists, so the Finsuma server keeps them.)
- **Android app** with the Finsuma name and icon. A Finsuma-style home page, library and Auto DJ
  are in progress.

Everything else (MPV and web playback, Auto DJ, lyrics, smart playlist editor, scrobbling,
themes) comes from Feishin.

## Getting started

### Desktop

There are no Finsuma releases yet, so run it from source (Node 22+ and
[pnpm](https://pnpm.io/)):

```sh
pnpm install
pnpm run dev       # desktop app with hot reload
```

`pnpm run package:win`, `package:mac` and `package:linux` build installers. The rest of the
scripts are the same as Feishin's; see its [README](https://github.com/jeffvli/feishin#readme).

On first launch, add your server (Navidrome URL, user and password).

### Finsuma server (optional)

Needed for the YouTube/SoundCloud download button and for playlist ratings and pins. See
[`server/README.md`](server/README.md). In short:

```sh
cd server
cp .env.example .env      # set YT_TOKEN, MUSIC_DIR, PUID/PGID
docker compose up -d
```

The first time you download something, Finsuma asks for the token. It finds the service on port
3200 of your music server's machine. If it runs somewhere else, set it in the YouTube page
settings or add `VITE_FINSUMA_SERVICE_URL=http://host:3200` to a `.env.local` file (not committed).

### Android

```sh
cd mobile
./gradlew assembleDegoogledDebug   # APK in app/build/outputs/apk/
```

Needs the Android SDK (`local.properties` with `sdk.dir=...`) and JDK 21, which Gradle downloads
for you. See [`mobile/README.md`](mobile/README.md) for Tempus's own documentation.

## Keeping up with upstream

```sh
# Desktop (Feishin)
git remote add upstream https://github.com/jeffvli/feishin.git
git fetch upstream && git merge upstream/development

# Android (Tempus)
git subtree pull --prefix=mobile https://github.com/eddyizm/tempus.git main
```

## Credits

- [Feishin](https://github.com/jeffvli/feishin) by jeffvli and contributors: the desktop app this
  is built on.
- [Tempus](https://github.com/eddyizm/tempus) by eddyizm and contributors (originally
  [Tempo](https://github.com/CappielloAntonio/tempo)): the Android app.
- [yt-dlp](https://github.com/yt-dlp/yt-dlp) and [FFmpeg](https://ffmpeg.org/) power the
  downloader.

Translations for the shared parts happen upstream:
[Feishin on Weblate](https://hosted.weblate.org/projects/feishin/).

## License

[GNU General Public License v3.0](LICENSE), the same as Feishin and Tempus.
