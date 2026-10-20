# Finsuma server

Runs next to your music server. Finsuma's YouTube and SoundCloud tabs send it the page you are
on, and it saves the song (or the whole playlist) as a tagged MP3 with square cover art into
your music folder. Finsuma then asks Navidrome to rescan.

## Setup

```sh
cd server
cp .env.example .env                       # set YT_TOKEN, MUSIC_DIR, PUID/PGID
docker compose up -d                       # download service only
docker compose --profile navidrome up -d   # also run Navidrome
```

The first time you press the download button in Finsuma, it asks for the token: paste `YT_TOKEN`.
Finsuma looks for the service on port 3200 of the machine your Navidrome server is on, then
on the address saved in the YouTube page settings.

## What it does

- `POST /download` `{url, mode: "single" | "playlist", artist?, title?, album?}` queues a download
- `GET /preview?url=` returns the cleaned-up artist, title and cover for the popup
- `GET /jobs` lists recent downloads; `GET /health` needs no token
- Singles go to `YouTube/<artist>/<title>.mp3`, playlists to `YouTube/<playlist>/NN <title>.mp3`
- yt-dlp updates itself when the container starts (set `YT_AUTO_UPDATE=0` to turn that off)

It only accepts YouTube and SoundCloud links, and every request except `/health` needs the token.
Keep port 3200 on your home network; it is not meant to face the internet.

## Without Docker

Needs Python 3.11+, ffmpeg, [yt-dlp](https://github.com/yt-dlp/yt-dlp) and
[deno](https://deno.com) on the PATH:

```sh
YT_TOKEN=... YT_MUSIC_ROOT=/path/to/Music/YouTube YT_DLP=yt-dlp python3 yt-download/server.py
```
