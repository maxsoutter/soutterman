"""Prepare the voiceover for a cutout ad: optional silent gaps for physical gags + word timings.

Usage:
  python assemble_voice.py <voice.mp3|wav> <out_dir> [--cut T:SECONDS ...] [--no-transcribe]
  python assemble_voice.py <voice.mp3|wav> --energy T0 T1        # print loudness every 20 ms to find a clean cut point

--cut 9.97:2.0   splits the audio at 9.97 s and inserts 2.0 s of silence there (repeat for several gaps).
                 Pick T inside real silence (use --energy), e.g. just after a sigh/chuckle and before the next line.
Writes <out_dir>/vo.wav (mono 48 kHz) and, unless --no-transcribe, <out_dir>/words/words.txt via transcribe.py
(whisper word times of the FINAL audio, after the gaps are inserted; use these for scene timing and captions).
Prints the word list as  word@time  so you can lay the plan out straight from it.
"""
import sys, os, subprocess, wave
import numpy as np

here = os.path.dirname(os.path.abspath(__file__))
src = sys.argv[1]


def to_wav(path, out):
    subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", path, "-ar", "48000", "-ac", "1", out], check=True)


if "--energy" in sys.argv:
    i = sys.argv.index("--energy")
    t0, t1 = float(sys.argv[i + 1]), float(sys.argv[i + 2])
    tmp = os.path.join(os.environ.get("TEMP", "."), "_energy.wav")
    to_wav(src, tmp)
    w = wave.open(tmp)
    sr = w.getframerate()
    a = np.frombuffer(w.readframes(w.getnframes()), dtype=np.int16).astype(float)
    hop = int(sr * 0.02)
    t = t0
    while t < t1:
        seg = a[int(t * sr): int(t * sr) + hop]
        print(f"{t:.2f}:{int(np.sqrt(np.mean(seg ** 2)))}", end="  ")
        t += 0.02
    print()
    sys.exit()

out = sys.argv[2]
os.makedirs(out, exist_ok=True)
cuts = []
for i, a in enumerate(sys.argv):
    if a == "--cut":
        t, s = sys.argv[i + 1].split(":")
        cuts.append((float(t), float(s)))
cuts.sort()
base = os.path.join(out, "_base.wav")
to_wav(src, base)
if cuts:
    parts, lst, prev = [], [], 0.0
    for k, (t, s) in enumerate(cuts):
        pa = os.path.join(out, f"_p{k}.wav")
        subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-ss", str(prev), "-t", str(t - prev), "-i", base, pa], check=True)
        gp = os.path.join(out, f"_g{k}.wav")
        subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-f", "lavfi", "-i", "anullsrc=r=48000:cl=mono", "-t", str(s), gp], check=True)
        lst += [pa, gp]
        prev = t
    last = os.path.join(out, "_last.wav")
    subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-ss", str(prev), "-i", base, last], check=True)
    lst.append(last)
    with open(os.path.join(out, "_list.txt"), "w") as f:
        for p in lst:
            f.write("file '" + os.path.abspath(p).replace("\\", "/") + "'\n")
    subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-f", "concat", "-safe", "0", "-i", os.path.join(out, "_list.txt"), "-c:a", "pcm_s16le", os.path.join(out, "vo.wav")], check=True)
else:
    os.replace(base, os.path.join(out, "vo.wav"))
dur = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", os.path.join(out, "vo.wav")], capture_output=True, text=True).stdout.strip()
print("vo.wav duration", dur)
if "--no-transcribe" not in sys.argv:
    v16 = os.path.join(out, "_vo16.wav")
    subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", os.path.join(out, "vo.wav"), "-ar", "16000", "-ac", "1", v16], check=True)
    subprocess.run([sys.executable, os.path.join(here, "transcribe.py"), v16, os.path.join(out, "words")], check=True)
    for line in open(os.path.join(out, "words", "words.txt"), encoding="utf-8"):
        p = line.split()
        if len(p) >= 3:
            print(f"{p[2]}@{p[0]}", end=" ")
    print()
