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

## One URL for everything (no IP or port in the apps)

Finsuma apps first try `<your Navidrome URL>/finsuma`, so if that path reaches this service,
nobody has to type an address. Two ways to set it up:

- **Cloudflare Tunnel:** in the tunnel's Public Hostnames, add a rule for your Navidrome hostname
  with path `finsuma` (or `^/finsuma`) pointing at `http://<server-ip>:3200`, and put it above the
  rule that sends the hostname to Navidrome.
- **Built-in proxy:** `docker compose --profile proxy up -d` runs Caddy on port 4534 (change with
  `PROXY_PORT`). It sends `/finsuma/...` here and everything else to Navidrome
  (`NAVIDROME_UPSTREAM`, default the `navidrome` service). Point your domain at it.

The service accepts requests with or without the `/finsuma` prefix. When it is reachable from the
internet, the token is what protects it, so use a long random one.

## What it does

- `POST /download` `{url, mode: "single" | "playlist", artist?, title?, album?}` queues a download
- `GET /preview?url=` returns the cleaned-up artist, title and cover for the popup
- `GET /jobs` lists recent downloads; `GET /health` needs no token
- Singles go to `YouTube/<artist>/<title>.mp3`, playlists to `YouTube/<playlist>/NN <title>.mp3`
- yt-dlp updates itself when the container starts (set `YT_AUTO_UPDATE=0` to turn that off)

It only accepts YouTube and SoundCloud links, and every request except `/health` needs the token.
Without the URL setup above, keep port 3200 on your home network.

## Without Docker

Needs Python 3.11+, ffmpeg, [yt-dlp](https://github.com/yt-dlp/yt-dlp) and
[deno](https://deno.com) on the PATH:

```sh
YT_TOKEN=... YT_MUSIC_ROOT=/path/to/Music/YouTube YT_DLP=yt-dlp python3 yt-download/server.py
```
