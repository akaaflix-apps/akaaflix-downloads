#!/usr/bin/env python3
"""
MOCK Xtream Codes server used ONLY by the automated self-test / CI.

It imitates `player_api.php` (with deliberately inconsistent JSON types, like real panels)
and serves tiny generated media files so the real app can be exercised end-to-end:

  /live/test/test/1.ts         -> MPEG-TS stream          (MPEG-TS priority test)
  /live/test/test/2.ts         -> HTTP 404                (forces the HLS fallback)
  /live/test/test/2.m3u8       -> HLS playlist + segments
  /movie/test/test/101.mp4     -> MP4 with two audio tracks (Range requests supported)
  /series/test/test/<id>.mp4   -> short MP4 episodes
  /img/<kind>/<n>.bmp          -> generated artwork (no image files are stored)

No real channels or content are provided. Usage:
  python3 mock_xtream_server.py --port 8765 --fixtures macos/SelfTest/fixtures
"""
import argparse
import base64
import json
import os
import struct
import sys
import threading
import time
import zlib
from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler
from urllib.parse import urlparse, parse_qs

USER, PASS = "test", "test"
NOW = int(time.time())

LIVE_CATEGORIES = [
    ("1", "قنوات مصر"), ("2", "قنوات عربية"), ("3", "قنوات رياضية"), ("4", "قنوات أفلام"),
    ("5", "قنوات مسلسلات"), ("6", "قنوات أطفال"), ("7", "قنوات وثائقية"), ("8", "قنوات أخبار"), ("9", "قنوات أجنبية"),
]
CHANNEL_NAMES = [
    ("Sports One HD", "3"), ("Sports Two HD", "3"), ("Sports Three HD", "3"), ("Sports Four HD", "3"),
    ("Nile Drama HD", "1"), ("Cairo Cinema", "1"), ("Arab News 24", "8"), ("Gulf Series HD", "5"),
    ("Kids Planet", "6"), ("Nature Docs", "7"), ("World Movies FHD", "4"), ("Global News", "9"),
]
VOD_CATEGORIES = [("11", "أفلام جديدة"), ("12", "أفلام أكشن"), ("13", "أفلام مغامرات"), ("14", "أفلام خيال علمي"),
                  ("15", "أفلام رعب"), ("16", "أفلام كوميديا"), ("17", "أفلام دراما")]
MOVIES = [
    ("ظل القلعة - Shadow of the Castle 4K", "12", 8.7), ("المهمة الأخيرة - The Last Mission FHD", "12", 8.4),
    ("الوريثة - The Heiress 4K", "17", 8.9), ("حدود النار - Fire Line FHD", "12", 8.2),
    ("مملكة التنانين - Dragon Kingdom 4K", "14", 8.8), ("المنزل رقم 13 - House No. 13 HD", "15", 7.8),
    ("طريق الرمال - The Sand Path 4K", "13", 8.6), ("البوابة الزرقاء - The Blue Gate HD", "14", 7.8),
    ("عميل الظل - Shadow Agent FHD", "12", 8.6), ("الذكاء الاصطناعي - A.I. Uprising 4K", "14", 8.4),
    ("الكنز المفقود - The Lost Treasure FHD", "13", 8.1), ("أعماق مجهولة - Unknown Depths HD", "15", 7.3),
]
SERIES_CATEGORIES = [("21", "مسلسلات عربية"), ("22", "مسلسلات مصرية"), ("23", "مسلسلات تركية"), ("24", "مسلسلات كورية"),
                     ("25", "مسلسلات أجنبية"), ("26", "مسلسلات وثائقية")]
SERIES = [
    ("قيام الدولة - The Rise of the State 4K", "23", 9.1), ("الوريثة - The Heiress FHD", "21", 8.9),
    ("سرايا الظل - Shadow Palaces 4K", "21", 8.7), ("حارة العمدة - The Mayor's Alley FHD", "22", 9.8),
    ("المؤسس - The Founder 4K", "23", 9.0), ("البروفيسور - The Professor HD", "25", 9.2),
    ("الإخوة - The Brothers 4K", "25", 9.8), ("صراع العروش - Thrones FHD", "25", 9.4),
]


def bmp(width, height, seed, kind):
    """Generates a small 24-bit BMP gradient (CGImageSource decodes BMP natively)."""
    palettes = [((120, 10, 20), (10, 10, 40)), ((10, 40, 120), (5, 5, 20)), ((90, 20, 110), (10, 5, 30)),
                ((140, 60, 10), (20, 10, 5)), ((10, 90, 70), (5, 15, 15))]
    a, b = palettes[seed % len(palettes)]
    row_size = (width * 3 + 3) & ~3
    pixels = bytearray()
    for y in range(height):
        t = y / max(1, height - 1)
        row = bytearray()
        for x in range(width):
            s = x / max(1, width - 1)
            glow = max(0.0, 1.0 - ((s - 0.7) ** 2 + (t - 0.35) ** 2) * 4)
            r = int(a[0] * (1 - t) + b[0] * t + 90 * glow)
            g = int(a[1] * (1 - t) + b[1] * t + 60 * glow)
            bl = int(a[2] * (1 - t) + b[2] * t + 80 * glow)
            row += bytes((min(bl, 255), min(g, 255), min(r, 255)))
        row += b"\x00" * (row_size - width * 3)
        pixels += row
    header = b"BM" + struct.pack("<IHHI", 54 + len(pixels), 0, 0, 54)
    info = struct.pack("<IiiHHIIiiII", 40, width, height, 1, 24, 0, len(pixels), 2835, 2835, 0, 0)
    return header + info + bytes(pixels)


_IMAGE_CACHE = {}


class Handler(BaseHTTPRequestHandler):
    fixtures = "."
    base = "http://127.0.0.1:8765/"
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        sys.stderr.write("[mock] " + (fmt % args) + "\n")

    # ---------- helpers ----------
    def send_bytes(self, data, ctype, status=200, extra=None):
        self.send_response(status)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Accept-Ranges", "bytes")
        for k, v in (extra or {}).items():
            self.send_header(k, v)
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(data)

    def send_json(self, obj):
        self.send_bytes(json.dumps(obj, ensure_ascii=False).encode("utf-8"), "application/json; charset=utf-8")

    def not_found(self):
        self.send_bytes(b"not found", "text/plain", 404)

    def send_file(self, path, ctype):
        if not os.path.exists(path):
            return self.not_found()
        size = os.path.getsize(path)
        rng = self.headers.get("Range")
        with open(path, "rb") as f:
            if rng and rng.startswith("bytes="):
                start_s, _, end_s = rng[6:].partition("-")
                start = int(start_s) if start_s else 0
                end = int(end_s) if end_s else size - 1
                end = min(end, size - 1)
                if start >= size:
                    return self.send_bytes(b"", ctype, 416, {"Content-Range": f"bytes */{size}"})
                f.seek(start)
                data = f.read(end - start + 1)
                return self.send_bytes(data, ctype, 206, {"Content-Range": f"bytes {start}-{end}/{size}"})
            data = f.read()
        self.send_bytes(data, ctype)

    def img(self, kind, n):
        return f"{self.base}img/{kind}/{n}.bmp"

    # ---------- routes ----------
    def do_HEAD(self):
        self.do_GET()

    def do_GET(self):
        url = urlparse(self.path)
        parts = [p for p in url.path.split("/") if p]
        if url.path.endswith("player_api.php"):
            return self.api(parse_qs(url.query))
        if parts[:1] == ["img"] and len(parts) == 3:
            key = (parts[1], parts[2])
            if key not in _IMAGE_CACHE:
                n = int(parts[2].split(".")[0]) if parts[2].split(".")[0].isdigit() else 0
                size = {"poster": (200, 300), "backdrop": (480, 270), "logo": (160, 90), "episode": (240, 135)}.get(parts[1], (200, 200))
                _IMAGE_CACHE[key] = bmp(size[0], size[1], n, parts[1])
            return self.send_bytes(_IMAGE_CACHE[key], "image/bmp")
        if len(parts) == 4 and parts[0] in ("live", "movie", "series"):
            if parts[1] != USER or parts[2] != PASS:
                return self.send_bytes(b"forbidden", "text/plain", 403)
            name = parts[3]
            if parts[0] == "live":
                return self.live(name)
            if parts[0] == "movie":
                return self.send_file(os.path.join(self.fixtures, "movie.mp4"), "video/mp4")
            return self.send_file(os.path.join(self.fixtures, "episode.mp4"), "video/mp4")
        if parts[:1] == ["subs"]:
            return self.send_file(os.path.join(self.fixtures, "movie.srt"), "text/plain; charset=utf-8")
        self.not_found()

    def live(self, name):
        stem, _, ext = name.partition(".")
        if name.startswith("hls_seg"):
            return self.send_file(os.path.join(self.fixtures, "hls", name), "video/mp2t")
        if stem == "2" and ext == "ts":
            return self.not_found()              # MPEG-TS deliberately unavailable for channel 2
        if ext == "ts":
            return self.send_file(os.path.join(self.fixtures, "live.ts"), "video/mp2t")
        if ext == "m3u8":
            return self.send_file(os.path.join(self.fixtures, "hls", "index.m3u8"), "application/vnd.apple.mpegurl")
        return self.not_found()

    def api(self, q):
        user = (q.get("username") or [""])[0]
        pw = (q.get("password") or [""])[0]
        action = (q.get("action") or [None])[0]
        if user != USER or pw != PASS:
            return self.send_json({"user_info": {"auth": 0}})
        if action is None:
            return self.send_json({
                "user_info": {"username": USER, "password": PASS, "message": "Mock server", "auth": 1, "status": "Active",
                              "exp_date": str(NOW + 86400 * 120), "is_trial": "0", "active_cons": "0", "created_at": NOW - 86400 * 40,
                              "max_connections": "2", "allowed_output_formats": ["m3u8", "ts"]},
                "server_info": {"url": "127.0.0.1", "port": "8765", "https_port": "", "server_protocol": "http",
                                "rtmp_port": "0", "timezone": "Africa/Cairo", "timestamp_now": NOW, "time_now": time.strftime("%Y-%m-%d %H:%M:%S")},
            })
        if action == "get_live_categories":
            return self.send_json([{"category_id": c, "category_name": n, "parent_id": 0} for c, n in LIVE_CATEGORIES])
        if action == "get_vod_categories":
            return self.send_json([{"category_id": c, "category_name": n, "parent_id": "0"} for c, n in VOD_CATEGORIES])
        if action == "get_series_categories":
            return self.send_json([{"category_id": int(c), "category_name": n} for c, n in SERIES_CATEGORIES])
        if action == "get_live_streams":
            return self.send_json([{"num": i + 1, "name": n, "stream_type": "live", "stream_id": str(i + 1) if i % 2 else i + 1,
                                    "stream_icon": self.img("logo", i), "epg_channel_id": f"ch{i + 1}", "added": str(NOW - i * 3600),
                                    "category_id": cat, "tv_archive": 0, "is_adult": "0"} for i, (n, cat) in enumerate(CHANNEL_NAMES)])
        if action == "get_vod_streams":
            return self.send_json([{"num": i + 1, "name": n, "stream_type": "movie", "stream_id": 101 + i, "stream_icon": self.img("poster", i),
                                    "rating": str(r), "rating_5based": r / 2, "added": str(NOW - i * 86400), "category_id": cat,
                                    "container_extension": "mp4", "year": str(2026 - i % 3)} for i, (n, cat, r) in enumerate(MOVIES)])
        if action == "get_series":
            return self.send_json([{"num": i + 1, "name": n, "series_id": 501 + i, "cover": self.img("poster", i + 3),
                                    "plot": "قصة ملحمية مليئة بالأحداث والتشويق.", "cast": "Actor One, Actor Two, Actor Three",
                                    "director": "Director Name", "genre": "دراما, تاريخي, مغامرة", "releaseDate": f"{2026 - i % 4}-01-01",
                                    "last_modified": str(NOW - i * 86400), "rating": r, "backdrop_path": [self.img("backdrop", i)],
                                    "episode_run_time": "45", "category_id": cat} for i, (n, cat, r) in enumerate(SERIES)])
        if action == "get_vod_info":
            vid = int((q.get("vod_id") or ["101"])[0])
            i = max(0, min(len(MOVIES) - 1, vid - 101))
            return self.send_json({
                "info": {"movie_image": self.img("poster", i), "backdrop_path": [self.img("backdrop", i)],
                         "plot": "في مملكة تعيش على حافة الانهيار، يعود محارب سابق لحماية قلعة أسطورية تحمل سراً قد يغير مصير العالم.",
                         "cast": "Liam Stone, Ana Rivers, Pedro Vale, Tom Hart, Florence Bay", "director": "James Mangold",
                         "genre": "أكشن, مغامرات, دراما", "releasedate": "2026-11-08", "duration_secs": "8076", "duration": "02:14:36",
                         "rating": str(MOVIES[i][2]), "age": "+16", "country": "USA", "youtube_trailer": "dQw4w9WgXcQ"},
                "movie_data": {"stream_id": vid, "name": MOVIES[i][0], "container_extension": "mp4", "category_id": MOVIES[i][1]},
            })
        if action == "get_series_info":
            sid = int((q.get("series_id") or ["501"])[0])
            episodes = {}
            for season in (1, 2):
                episodes[str(season)] = [{"id": str(sid * 100 + season * 10 + e), "episode_num": e, "title": f"الحلقة {e}",
                                          "container_extension": "mp4", "season": season,
                                          "info": {"duration_secs": 2700, "plot": "يستعد البطل لمواجهة خطر جديد يهدد القبيلة...",
                                                   "movie_image": self.img("episode", season * 10 + e)}} for e in (1, 2, 3)]
            return self.send_json({"seasons": [{"season_number": 1, "name": "الموسم 1"}, {"season_number": 2, "name": "الموسم 2"}],
                                   "info": {"name": "Series", "country": "تركيا", "language": "التركية (مترجم عربي)", "age": "+16"},
                                   "episodes": episodes})
        if action == "get_short_epg":
            start = NOW - (NOW % 3600)
            titles = ["الدوري الأوروبي - مباراة مباشرة", "الاستوديو التحليلي", "ملخص مباريات الأسبوع", "برنامج الكرة العالمية"]
            return self.send_json({"epg_listings": [
                {"id": str(i), "title": base64.b64encode(t.encode()).decode(), "description": "", "lang": "ar",
                 "start_timestamp": str(start + i * 3600 - 1800), "stop_timestamp": str(start + (i + 1) * 3600 - 1800)}
                for i, t in enumerate(titles)]})
        return self.send_json([])


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--port", type=int, default=8765)
    ap.add_argument("--fixtures", default=os.path.join(os.path.dirname(__file__), "fixtures"))
    args = ap.parse_args()
    Handler.fixtures = os.path.abspath(args.fixtures)
    Handler.base = f"http://127.0.0.1:{args.port}/"
    server = ThreadingHTTPServer(("127.0.0.1", args.port), Handler)
    print(f"MOCK Xtream server on {Handler.base} (fixtures: {Handler.fixtures})", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
