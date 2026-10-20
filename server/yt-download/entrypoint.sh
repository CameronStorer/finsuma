#!/bin/sh
set -e
if [ "${YT_AUTO_UPDATE:-1}" = "1" ]; then
    "$YT_DLP" -U || echo "yt-dlp update failed; using the bundled version"
fi
exec python /app/server.py
