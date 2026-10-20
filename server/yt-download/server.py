"""Tiny LAN service that downloads YouTube/SoundCloud audio as tagged MP3s into the Music folder.

Endpoints (all JSON, CORS open; the token is the guard):
  GET  /health                          -> {"ok": true, "ffmpeg": bool}
  GET  /jobs             (X-Token)      -> {"jobs": [...]}  newest first
  GET  /preview?url=...  (X-Token)      -> {"artist", "title", "album", "thumbnail"}
  GET  /prefs            (X-Token)      -> {"playlists": {id: {"rating"?, "pinned"?}}}
  PUT  /prefs/playlists/<id> (X-Token)  {"rating"?: 0-5, "pinned"?: bool}
  POST /download         (X-Token)      {"url", "mode": "single"|"playlist", "artist"?, "title"?, "album"?}

Each song gets a cleaned-up artist/title/album tag and square cover art.
Singles land in <root>/<artist>/<title>.mp3, playlists in <root>/<playlist>/NN <title>.mp3.
Jobs run one at a time so the server isn't hammered.
"""

import json
import os
import queue
import re
import shutil
import subprocess
import threading
import time
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

MUSIC_ROOT = os.environ.get("YT_MUSIC_ROOT", "/mnt/data/nextcloud_files/cam/files/Music/YouTube")
YT_DLP = os.environ.get("YT_DLP", os.path.expanduser("~/.local/bin/yt-dlp"))
TOKEN = os.environ.get("YT_TOKEN", "")
PORT = int(os.environ.get("YT_PORT", "3200"))
MAX_JOBS_KEPT = 50
# Finsuma playlist ratings and pins (Navidrome can't store or return these for playlists).
PREFS_FILE = os.environ.get("YT_PREFS_FILE", os.path.join(MUSIC_ROOT, ".finsuma-prefs.json"))
prefs_lock = threading.Lock()
INCOMING = os.path.join(MUSIC_ROOT, ".incoming")

ALLOWED_HOSTS = {
    "www.youtube.com", "youtube.com", "m.youtube.com", "music.youtube.com", "youtu.be",
    "soundcloud.com", "www.soundcloud.com", "m.soundcloud.com", "on.soundcloud.com",
}

META_FIELDS = "filepath,id,title,track,artist,creator,uploader,channel,album,release_year,upload_date,playlist_title,playlist_index"

COMMON = [
    "--extract-audio",
    "--audio-format", "mp3",
    "--audio-quality", "0",
    "--embed-metadata",
    "--embed-thumbnail",
    "--convert-thumbnails", "jpg",
    # Crop the 16:9 YouTube thumbnail to a square album cover.
    "--ppa", "ThumbnailsConvertor+FFmpeg_o:-c:v mjpeg -vf crop=\"'if(gt(ih,iw),iw,ih)':'if(gt(iw,ih),ih,iw)'\"",
    "--no-overwrites",
    "--newline",
    "--progress",
    "--no-simulate",
    "--progress-template", "download:PROGRESS %(progress._percent_str)s",
    "--print", "before_dl:TITLE %(title)s",
    "--print", f"after_move:META %(.{{{META_FIELDS}}})j",
]

# YouTube needs a JavaScript runtime to solve its playback challenges. yt-dlp uses
# deno by default; fall back to node if that's what the machine has.
JS_RUNTIME_ARGS = [] if shutil.which("deno") or not shutil.which("node") else ["--js-runtimes", "node"]

jobs = {}
jobs_lock = threading.Lock()
work = queue.Queue()


# ---------------------------------------------------------------- metadata

JUNK_WORDS = ("official", "video", "lyric", "audio", "visuali", "hd", "hq", "4k", "mv", "m/v", "full song", "clip")
KEEP_WORDS = ("remix", "feat", "ft.", "live", "version", "edit", "mix", "acoustic", "cover", "remaster", "prod")


def _strip_junk(text):
    def drop(match):
        inner = match.group(1).lower()
        if any(k in inner for k in KEEP_WORDS):
            return match.group(0)
        if any(re.search(rf"(^|\W){re.escape(w)}", inner) for w in JUNK_WORDS):
            return ""
        return match.group(0)

    text = re.sub(r"\s*[\(\[【]([^\)\]】]*)[\)\]】]", drop, text)
    text = re.sub(r"\s+#\S+", "", text)  # hashtags
    text = re.sub(r"\s*\|.*$", "", text)  # "Song | Channel extras"
    text = re.sub(r"\s+", " ", text).strip(" -–—\"'“”")
    return text


def _clean_channel(name):
    name = re.sub(r"\s*-\s*Topic$", "", name or "")
    name = re.sub(r"(VEVO|Official|OfficialChannel)$", "", name, flags=re.I).strip()
    return name or "Unknown Artist"


def clean_meta(info):
    track = info.get("track")
    artist = info.get("artist") or info.get("creator")
    raw_title = info.get("title") or "Unknown"

    if track and artist:  # YouTube Music gives real tags
        title = track
    else:
        cleaned = _strip_junk(raw_title)
        split = re.match(r"^(.+?)\s+[-–—]\s+(.+)$", cleaned)
        if split:
            artist, title = split.group(1), split.group(2)
        else:
            artist = artist or _clean_channel(info.get("channel") or info.get("uploader"))
            title = cleaned

    title = _strip_junk(title) or raw_title
    artist = _strip_junk(artist) or "Unknown Artist"
    year = str(info.get("release_year") or (info.get("upload_date") or "")[:4] or "")
    return {"artist": artist, "title": title, "album": info.get("album") or title, "year": year}


def _safe(name):
    name = re.sub(r'[\\/:*?"<>|\x00-\x1f]', "", name).strip(" .")
    return name[:120] or "Unknown"


def _unique(path):
    base, ext = os.path.splitext(path)
    n = 2
    while os.path.exists(path):
        path = f"{base} ({n}){ext}"
        n += 1
    return path


def tag_and_place(info, job):
    """Rewrite tags with ffmpeg (audio + cover copied as-is) and move to the final path."""
    src = info["filepath"]
    meta = clean_meta(info)
    tags = {"artist": meta["artist"], "title": meta["title"], "date": meta["year"]}

    if job["mode"] == "playlist":
        index = int(info.get("playlist_index") or 0)
        tags.update(album=info.get("playlist_title") or "YouTube", album_artist="Various Artists",
                    compilation="1", track=str(index))
        dest = os.path.join(os.path.dirname(src), f"{index:02d} {_safe(meta['title'])}.mp3")
    else:
        for key in ("artist", "title", "album"):
            if job.get(f"set_{key}"):
                meta[key] = job[f"set_{key}"]
        tags.update(artist=meta["artist"], title=meta["title"], album=meta["album"], album_artist=meta["artist"])
        dest = os.path.join(MUSIC_ROOT, _safe(meta["artist"]), f"{_safe(meta['title'])}.mp3")

    os.makedirs(os.path.dirname(dest), exist_ok=True)
    dest = _unique(dest) if os.path.abspath(dest) != os.path.abspath(src) else dest
    tmp = src + ".tagging.mp3"
    cmd = ["ffmpeg", "-loglevel", "error", "-y", "-i", src, "-map", "0", "-c", "copy", "-id3v2_version", "3"]
    for key, value in tags.items():
        if value:
            cmd += ["-metadata", f"{key}={value}"]
    # Drop YouTube's video description and category; they're noise in a music library.
    for key in ("description", "synopsis", "genre"):
        cmd += ["-metadata", f"{key}="]
    result = subprocess.run([*cmd, tmp], capture_output=True, text=True)
    if result.returncode == 0:
        os.replace(tmp, src)
    else:
        print(f"[tagging failed] {src}: {result.stderr[-500:]}", flush=True)
        if os.path.exists(tmp):
            os.remove(tmp)
    shutil.move(src, dest)
    return os.path.relpath(dest, MUSIC_ROOT)


def preview(url):
    result = subprocess.run(
        [YT_DLP, "-J", "--no-playlist", "--skip-download", "--no-warnings", *JS_RUNTIME_ARGS, url],
        capture_output=True, text=True, timeout=60,
    )
    if result.returncode != 0:
        raise RuntimeError((result.stderr.strip().splitlines() or ["couldn't read that video"])[-1])
    info = json.loads(result.stdout)
    meta = clean_meta(info)
    return {**meta, "thumbnail": info.get("thumbnail"), "duration": info.get("duration")}


# ---------------------------------------------------------------- playlist prefs

def load_prefs():
    try:
        with open(PREFS_FILE, encoding="utf-8") as f:
            data = json.load(f)
        return data if isinstance(data, dict) else {}
    except (FileNotFoundError, json.JSONDecodeError):
        return {}


def update_playlist_pref(playlist_id, changes):
    with prefs_lock:
        prefs = load_prefs()
        playlists = prefs.setdefault("playlists", {})
        entry = playlists.get(playlist_id, {})
        if "rating" in changes:
            rating = changes["rating"]
            if rating in (None, 0):
                entry.pop("rating", None)
            elif isinstance(rating, int) and 1 <= rating <= 5:
                entry["rating"] = rating
        if "pinned" in changes:
            if changes["pinned"]:
                entry["pinned"] = True
            else:
                entry.pop("pinned", None)
        if entry:
            playlists[playlist_id] = entry
        else:
            playlists.pop(playlist_id, None)
        tmp = PREFS_FILE + ".tmp"
        with open(tmp, "w", encoding="utf-8") as f:
            json.dump(prefs, f, indent=1)
        os.replace(tmp, PREFS_FILE)
        return entry


# ---------------------------------------------------------------- jobs

def update(job_id, **fields):
    with jobs_lock:
        if job_id in jobs:
            jobs[job_id].update(fields)


def run_job(job):
    job_id, url, mode = job["id"], job["url"], job["mode"]
    if mode == "playlist":
        template = os.path.join(MUSIC_ROOT, "%(playlist_title)s", "%(playlist_index)02d %(id)s.%(ext)s")
        args = ["--yes-playlist"]
    else:
        template = os.path.join(INCOMING, "%(id)s.%(ext)s")
        args = ["--no-playlist"]
    cmd = [YT_DLP, *COMMON, *JS_RUNTIME_ARGS, *args, "-o", template, url]

    update(job_id, status="running", started=time.time())
    files, tail = [], []
    proc = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    for line in proc.stdout:
        line = line.rstrip()
        if line.startswith("PROGRESS "):
            match = re.search(r"([\d.]+)%", line)
            if match:
                update(job_id, progress=float(match.group(1)))
        elif line.startswith("TITLE "):
            update(job_id, title=line[6:], progress=0.0)
        elif line.startswith("META "):
            try:
                files.append(tag_and_place(json.loads(line[5:]), job))
            except Exception as exc:
                tail.append(f"ERROR tagging: {exc}")
            update(job_id, files=list(files))
        else:
            tail = (tail + [line])[-20:]
    proc.wait()

    ok = bool(files) and (proc.returncode == 0 or mode == "playlist")
    errors = [l for l in tail if "ERROR" in l]
    if mode == "single" and files:
        update(job_id, title=os.path.splitext(os.path.basename(files[0]))[0])
    update(
        job_id,
        status="done" if ok else "failed",
        finished=time.time(),
        progress=100.0 if ok else job.get("progress", 0),
        error=None if ok and not errors else "\n".join(errors or tail[-5:])[-1500:],
    )
    print(f"[{'ok' if ok else 'failed'}] {mode} {url} ({len(files)} files)", flush=True)


def worker():
    while True:
        job = work.get()
        try:
            run_job(job)
        except Exception as exc:  # keep the worker alive no matter what
            update(job["id"], status="failed", error=str(exc), finished=time.time())


def valid_url(url):
    try:
        parsed = urlparse(url)
    except ValueError:
        return False
    return parsed.scheme == "https" and parsed.hostname in ALLOWED_HOSTS


# ---------------------------------------------------------------- http

class Handler(BaseHTTPRequestHandler):
    def _reply(self, code, payload):
        body = json.dumps(payload).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self._cors()
        self.end_headers()
        self.wfile.write(body)

    def _cors(self):
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, PUT, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type, X-Token")

    def _authed(self):
        return bool(TOKEN) and self.headers.get("X-Token") == TOKEN

    def do_OPTIONS(self):
        self.send_response(204)
        self._cors()
        self.end_headers()

    def do_GET(self):
        path = urlparse(self.path)
        if path.path == "/health":
            return self._reply(200, {"ok": True, "ffmpeg": bool(shutil.which("ffmpeg"))})
        if not self._authed():
            return self._reply(401, {"error": "bad token"})
        if path.path == "/prefs":
            with prefs_lock:
                prefs = load_prefs()
            return self._reply(200, {"playlists": prefs.get("playlists", {})})
        if path.path == "/jobs":
            with jobs_lock:
                listed = sorted(jobs.values(), key=lambda j: j["created"], reverse=True)
            return self._reply(200, {"jobs": listed})
        if path.path == "/preview":
            url = (parse_qs(path.query).get("url") or [""])[0]
            if not valid_url(url):
                return self._reply(400, {"error": "only https YouTube or SoundCloud links are accepted"})
            try:
                return self._reply(200, preview(url))
            except Exception as exc:
                return self._reply(502, {"error": str(exc)})
        return self._reply(404, {"error": "not found"})

    def do_PUT(self):
        match = re.fullmatch(r"/prefs/playlists/([A-Za-z0-9_-]{1,64})", urlparse(self.path).path)
        if not match:
            return self._reply(404, {"error": "not found"})
        if not self._authed():
            return self._reply(401, {"error": "bad token"})
        length = int(self.headers.get("Content-Length", "0"))
        try:
            changes = json.loads(self.rfile.read(length) or b"{}")
        except json.JSONDecodeError:
            return self._reply(400, {"error": "invalid JSON"})
        if not isinstance(changes, dict):
            return self._reply(400, {"error": "expected an object"})
        return self._reply(200, update_playlist_pref(match.group(1), changes))

    def do_POST(self):
        if self.path != "/download":
            return self._reply(404, {"error": "not found"})
        if not self._authed():
            return self._reply(401, {"error": "bad token"})
        if not shutil.which("ffmpeg"):
            return self._reply(500, {"error": "ffmpeg is not installed on the server"})

        length = int(self.headers.get("Content-Length", "0"))
        try:
            data = json.loads(self.rfile.read(length) or b"{}")
        except json.JSONDecodeError:
            return self._reply(400, {"error": "invalid JSON"})

        url = str(data.get("url", "")).strip()
        mode = data.get("mode", "single")
        if not valid_url(url):
            return self._reply(400, {"error": "only https YouTube or SoundCloud links are accepted"})
        if mode not in ("single", "playlist"):
            return self._reply(400, {"error": "mode must be single or playlist"})

        job = {
            "id": uuid.uuid4().hex[:12],
            "url": url,
            "mode": mode,
            "status": "queued",
            "title": None,
            "progress": 0.0,
            "files": [],
            "error": None,
            "created": time.time(),
        }
        for key in ("artist", "title", "album"):
            value = data.get(key)
            if isinstance(value, str) and value.strip():
                job[f"set_{key}"] = value.strip()[:200]
        with jobs_lock:
            jobs[job["id"]] = job
            for old in sorted(jobs.values(), key=lambda j: j["created"])[:-MAX_JOBS_KEPT]:
                if old["status"] in ("done", "failed"):
                    del jobs[old["id"]]
        work.put(job)
        return self._reply(202, job)

    def log_message(self, fmt, *args):
        if "/jobs" not in (args[0] if args else ""):
            print(fmt % args, flush=True)


if __name__ == "__main__":
    os.umask(0o002)  # keep files group-readable for Navidrome (www-data)
    os.makedirs(INCOMING, exist_ok=True)
    threading.Thread(target=worker, daemon=True).start()
    print(f"yt-download listening on :{PORT}, writing to {MUSIC_ROOT}", flush=True)
    ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
