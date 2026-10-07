"""Word-level transcript from audio with local Whisper (faster-whisper). No API key, runs offline
after the first model download.

Usage:  python transcribe.py <audio.wav|video.mp4> <outDir> [model]
        model default: small.en  (base.en = faster, less accurate)

Writes the same files as srt-to-words.mjs:
  words.json    [{text, start, end}]   real word times from the audio
  words.txt     "  12.34   12.61  word"
  phrases.json  [{start, end, text, words}]  caption phrases (<=4 words, split at punctuation/pauses)

Whisper can mishear names and jargon: compare against your script and fix the TEXT only
(keep the times) before using the words on screen.
"""
import json
import os
import re
import sys

from faster_whisper import WhisperModel

src, out = sys.argv[1], sys.argv[2]
model_name = sys.argv[3] if len(sys.argv) > 3 else "small.en"
os.makedirs(out, exist_ok=True)

model = WhisperModel(model_name, device="cpu", compute_type="int8")
segments, _ = model.transcribe(src, word_timestamps=True, vad_filter=True, beam_size=5)

words = []
for seg in segments:
    for w in seg.words or []:
        t = w.word.strip()
        if t:
            words.append({"text": t, "start": round(w.start, 3), "end": round(w.end, 3)})

phrases, cur = [], []


def flush():
    global cur
    if cur:
        phrases.append({
            "start": cur[0]["start"],
            "end": cur[-1]["end"],
            "text": " ".join(w["text"] for w in cur),
            "words": [w["text"] for w in cur],
        })
    cur = []


for i, w in enumerate(words):
    cur.append(w)
    nxt = words[i + 1] if i + 1 < len(words) else None
    punct = re.search(r"[.,!?;:…]$", w["text"]) is not None
    gap = (nxt["start"] - w["end"] > 0.3) if nxt else True
    if punct or gap or len(cur) >= 4:
        flush()
flush()

with open(os.path.join(out, "words.json"), "w", encoding="utf-8") as f:
    json.dump(words, f, indent=1)
with open(os.path.join(out, "words.txt"), "w", encoding="utf-8") as f:
    f.write("\n".join(f"{w['start']:7.2f} {w['end']:7.2f}  {w['text']}" for w in words))
with open(os.path.join(out, "phrases.json"), "w", encoding="utf-8") as f:
    json.dump(phrases, f, indent=1)
print(f"{len(words)} words, {len(phrases)} caption phrases -> {out}")
