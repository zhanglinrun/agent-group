#!/usr/bin/env python3
import argparse
import json
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path
from urllib.parse import urlparse

import requests


HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
        "(KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"
    ),
    "Origin": "https://www.bilibili.com",
}


def main():
    parser = argparse.ArgumentParser(description="Fetch Bilibili metadata, subtitles, cover, and media without yt-dlp.")
    parser.add_argument("url", help="Bilibili video URL or BV id")
    parser.add_argument("--out-dir", default="", help="Output directory. Defaults to bilibili_<BV>.")
    parser.add_argument("--cookies", default="", help="Optional Netscape cookies.txt path.")
    parser.add_argument("--no-media", action="store_true", help="Only fetch metadata, cover, and subtitles.")
    parser.add_argument("--transcribe", action="store_true", help="Run Whisper when no platform subtitle is available.")
    parser.add_argument("--whisper-model", default="base", help="Whisper model for --transcribe, default base.")
    parser.add_argument("--max-quality", type=int, default=64, help="Preferred no-login quality, default 64 (720P).")
    args = parser.parse_args()

    bvid = extract_bvid(args.url)
    if not bvid:
        raise SystemExit("Cannot find BV id in input URL.")

    out_dir = Path(args.out_dir or f"bilibili_{bvid}").resolve()
    out_dir.mkdir(parents=True, exist_ok=True)

    session = requests.Session()
    session.headers.update(HEADERS)
    session.headers["Referer"] = f"https://www.bilibili.com/video/{bvid}/"
    if args.cookies:
        load_netscape_cookies(session, Path(args.cookies))

    view = request_json(session, "https://api.bilibili.com/x/web-interface/view", {"bvid": bvid})["data"]
    cid = view["cid"]
    aid = view["aid"]
    write_json(out_dir / "metadata_api.json", view)
    download_cover(session, view.get("pic"), out_dir / "cover.jpg")

    player = request_json(session, "https://api.bilibili.com/x/player/v2", {"bvid": bvid, "cid": cid})["data"]
    write_json(out_dir / "player_v2.json", player)
    subtitle_paths = fetch_subtitles(session, player, out_dir)

    media = {}
    if not args.no_media:
        playurl = request_json(
            session,
            "https://api.bilibili.com/x/player/playurl",
            {"bvid": bvid, "cid": cid, "qn": args.max_quality, "fnval": 4048, "fourk": 1},
        )["data"]
        write_json(out_dir / "playurl.json", playurl)
        media = fetch_media(session, playurl, out_dir)
        if args.transcribe and not subtitle_paths and media.get("audio_wav"):
            subtitle_paths.append(transcribe_with_whisper(Path(media["audio_wav"]), out_dir, args.whisper_model))

    result = {
        "ok": True,
        "bvid": bvid,
        "aid": aid,
        "cid": cid,
        "title": view.get("title", ""),
        "duration": view.get("duration", 0),
        "cover": str((out_dir / "cover.jpg").resolve()) if (out_dir / "cover.jpg").exists() else "",
        "subtitles": [str(path.resolve()) for path in subtitle_paths],
        "media": media,
        "out_dir": str(out_dir),
    }
    write_json(out_dir / "fetch_result.json", result)
    print(json.dumps(result, ensure_ascii=False, indent=2))


def extract_bvid(text):
    match = re.search(r"(BV[0-9A-Za-z]+)", text or "")
    return match.group(1) if match else None


def request_json(session, url, params):
    response = session.get(url, params=params, timeout=30)
    response.raise_for_status()
    payload = response.json()
    if payload.get("code") != 0:
        raise RuntimeError(f"Bilibili API failed: {payload.get('code')} {payload.get('message')}")
    return payload


def write_json(path, data):
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")


def download_cover(session, url, path):
    if not url:
        return
    if url.startswith("//"):
        url = "https:" + url
    if url.startswith("http://"):
        url = "https://" + url[len("http://"):]
    response = session.get(url, timeout=30)
    response.raise_for_status()
    path.write_bytes(response.content)


def fetch_subtitles(session, player, out_dir):
    subtitle = player.get("subtitle") or {}
    items = subtitle.get("subtitles") or []
    paths = []
    for index, item in enumerate(items, 1):
        raw_url = item.get("subtitle_url") or item.get("url")
        if not raw_url:
            continue
        if raw_url.startswith("//"):
            raw_url = "https:" + raw_url
        lang = sanitize_name(item.get("lan_doc") or item.get("lan") or f"subtitle_{index}")
        data = session.get(raw_url, timeout=30).json()
        srt_path = out_dir / f"{lang}.srt"
        srt_path.write_text(bilibili_subtitle_to_srt(data), encoding="utf-8")
        paths.append(srt_path)
    return paths


def bilibili_subtitle_to_srt(payload):
    body = payload.get("body") if isinstance(payload, dict) else []
    lines = []
    for index, item in enumerate(body or [], 1):
        start = float(item.get("from", 0))
        end = float(item.get("to", start))
        content = str(item.get("content", "")).replace("\r", "").strip()
        if not content:
            continue
        lines.append(str(index))
        lines.append(f"{srt_time(start)} --> {srt_time(end)}")
        lines.append(content)
        lines.append("")
    return "\n".join(lines)


def srt_time(seconds):
    millis = int(round(seconds * 1000))
    h, rem = divmod(millis, 3600_000)
    m, rem = divmod(rem, 60_000)
    s, ms = divmod(rem, 1000)
    return f"{h:02d}:{m:02d}:{s:02d},{ms:03d}"


def fetch_media(session, playurl, out_dir):
    dash = playurl.get("dash") or {}
    videos = dash.get("video") or []
    audios = dash.get("audio") or []
    if not videos or not audios:
        raise RuntimeError("No DASH video/audio stream returned by Bilibili.")

    video = max(videos, key=lambda item: (int(item.get("id") or 0), int(item.get("bandwidth") or 0)))
    audio = max(audios, key=lambda item: int(item.get("bandwidth") or 0))
    video_path = out_dir / "video.m4s"
    audio_path = out_dir / "audio.m4s"
    download_stream(session, stream_url(video), video_path)
    download_stream(session, stream_url(audio), audio_path)

    ffmpeg = find_ffmpeg()
    source_mp4 = out_dir / "source.mp4"
    audio_wav = out_dir / "audio.wav"
    run([ffmpeg, "-y", "-i", str(video_path), "-i", str(audio_path), "-c", "copy", str(source_mp4)])
    run([ffmpeg, "-y", "-i", str(source_mp4), "-vn", "-ac", "1", "-ar", "16000", str(audio_wav)])
    return {
        "video_m4s": str(video_path.resolve()),
        "audio_m4s": str(audio_path.resolve()),
        "source_mp4": str(source_mp4.resolve()),
        "audio_wav": str(audio_wav.resolve()),
    }


def transcribe_with_whisper(audio_path, out_dir, model_name):
    ensure_ffmpeg_on_path()
    try:
        import whisper
    except Exception as exc:
        raise RuntimeError("openai-whisper is not installed. Install it or omit --transcribe.") from exc

    model = whisper.load_model(model_name)
    result = model.transcribe(str(audio_path), language="zh", verbose=False)
    segments = result.get("segments") or []
    srt_path = out_dir / f"audio_whisper_{sanitize_name(model_name)}.srt"
    txt_path = out_dir / f"audio_whisper_{sanitize_name(model_name)}.txt"
    srt_lines = []
    txt_lines = []
    for index, segment in enumerate(segments, 1):
        text = str(segment.get("text", "")).strip()
        if not text:
            continue
        start = float(segment.get("start", 0))
        end = float(segment.get("end", start))
        srt_lines.extend([str(index), f"{srt_time(start)} --> {srt_time(end)}", text, ""])
        txt_lines.append(text)
    srt_path.write_text("\n".join(srt_lines), encoding="utf-8")
    txt_path.write_text("\n".join(txt_lines), encoding="utf-8")
    return srt_path


def ensure_ffmpeg_on_path():
    runtime_bin = Path(__file__).resolve().parent / "runtime-bin"
    if runtime_bin.exists():
        current = os.environ.get("PATH", "")
        runtime_text = str(runtime_bin)
        parts = current.split(os.pathsep) if current else []
        if not any(part.lower() == runtime_text.lower() for part in parts):
            os.environ["PATH"] = runtime_text + (os.pathsep + current if current else "")


def stream_url(item):
    return item.get("baseUrl") or item.get("base_url") or item.get("url")


def download_stream(session, url, path):
    if not url:
        raise RuntimeError("Missing media stream URL.")
    headers = dict(HEADERS)
    headers["Referer"] = session.headers.get("Referer", "https://www.bilibili.com/")
    with session.get(url, headers=headers, stream=True, timeout=60) as response:
        response.raise_for_status()
        with path.open("wb") as file:
            for chunk in response.iter_content(chunk_size=1024 * 1024):
                if chunk:
                    file.write(chunk)


def find_ffmpeg():
    candidates = [
        shutil.which("ffmpeg"),
        str(Path(__file__).resolve().parent / "runtime-bin" / ("ffmpeg.cmd" if os.name == "nt" else "ffmpeg")),
        str(Path(__file__).resolve().parent / "runtime-bin" / "ffmpeg.exe"),
    ]
    for candidate in candidates:
        if candidate and Path(candidate).exists():
            return candidate
    return "ffmpeg"


def run(command):
    result = subprocess.run(command, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if result.returncode != 0:
        raise RuntimeError(result.stderr.strip() or result.stdout.strip() or f"Command failed: {command}")


def load_netscape_cookies(session, path):
    if not path.exists():
        raise RuntimeError(f"cookies.txt not found: {path}")
    for line in path.read_text(encoding="utf-8", errors="ignore").splitlines():
        if not line or line.startswith("#"):
            continue
        parts = line.split("\t")
        if len(parts) >= 7:
            domain, _, cookie_path, secure, _, name, value = parts[:7]
            session.cookies.set(name, value, domain=domain.lstrip("."), path=cookie_path, secure=secure.upper() == "TRUE")


def sanitize_name(value):
    value = re.sub(r"[^\w\u4e00-\u9fff.-]+", "_", str(value or "")).strip("_")
    return value or "subtitle"


if __name__ == "__main__":
    main()
