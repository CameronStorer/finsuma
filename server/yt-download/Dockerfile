FROM python:3.13-slim

# ffmpeg converts to MP3 and tags; deno lets yt-dlp solve YouTube's JS challenges.
RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg curl ca-certificates \
    && rm -rf /var/lib/apt/lists/*
COPY --from=denoland/deno:bin-2.9.7 /deno /usr/local/bin/deno
RUN pip install --no-cache-dir mutagen

# Standalone yt-dlp in a writable folder so the container can update it on start
# (YouTube breaks old versions often).
RUN mkdir -p /opt/yt-dlp \
    && curl -sSLo /opt/yt-dlp/yt-dlp https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp \
    && chmod -R a+rwx /opt/yt-dlp

WORKDIR /app
COPY server.py entrypoint.sh ./

ENV YT_DLP=/opt/yt-dlp/yt-dlp \
    YT_MUSIC_ROOT=/music/YouTube \
    YT_PORT=3200 \
    HOME=/tmp
EXPOSE 3200
ENTRYPOINT ["sh", "/app/entrypoint.sh"]
