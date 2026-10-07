"""Synthesise the cartoon SFX set for cutout ads with ffmpeg only (no downloads, no accounts).

Usage:  python gen_sfx.py <out_dir>        (the assets/sfx2 folder is already generated; re-run only to tweak)
Writes  <out_dir>/<name>.wav  (mono 48 kHz, peak-normalised to -3 dB) and <out_dir>/meta.js  window.SFX2={name:seconds}

Names: ring ring_blip scratch slide_down boing bonk slap stomp thump smash smash_noise crack clink drip
       icecrack ding kaching tick sweep_up rumble sadtrombone
Level them all to the same peak so the per-cue volume numbers in the composition mean the same thing.
"""
import subprocess, os, json, random, re, sys

out = sys.argv[1] if len(sys.argv) > 1 else "sfx2"
os.makedirs(out, exist_ok=True)
SR = 48000


def ff(name, args):
    p = os.path.join(out, name + ".wav")
    r = subprocess.run(["ffmpeg", "-loglevel", "error", "-y"] + args + ["-ar", str(SR), "-ac", "1", p], capture_output=True, text=True)
    if r.returncode:
        print("FAIL", name, r.stderr[:300])


def ae(expr, d, extra=""):
    return ["-f", "lavfi", "-i", f"aevalsrc='{expr}':s={SR}:d={d}"] + (["-af", extra] if extra else [])


def noise(color, d, af, amp=0.9):
    return ["-f", "lavfi", "-i", f"anoisesrc=d={d}:c={color}:r={SR}:a={amp}", "-af", af]


ff("ring", ae("0.35*(sin(2*PI*1200*t)+sin(2*PI*1500*t))*gt(sin(2*PI*22*t),0)*exp(-0.8*t)", 0.9, "afade=t=out:st=0.7:d=0.2"))
ff("ring_blip", ae("0.3*(sin(2*PI*1200*t)+sin(2*PI*1500*t))*gt(sin(2*PI*22*t),0)", 0.2, "afade=t=out:st=0.12:d=0.08"))
ff("scratch", noise("white", 0.5, "bandpass=f=2800:w=2200,vibrato=f=22:d=0.9,afade=t=in:d=0.03,afade=t=out:st=0.25:d=0.25,volume=2.5", 0.7))
ff("slide_down", ae("0.4*sin(2*PI*(900*t-650*t*t))*exp(-1.5*t)", 0.5, "afade=t=out:st=0.4:d=0.1"))
ff("boing", ae("0.45*sin(2*PI*320*t+4*sin(2*PI*9*t))*exp(-5*t)", 0.5, "afade=t=out:st=0.4:d=0.1"))
ff("bonk", ae("0.7*sin(2*PI*(180*t+520*(1-exp(-18*t))/18))*exp(-14*t)", 0.22, "afade=t=out:st=0.18:d=0.04"))
ff("slap", noise("white", 0.14, "bandpass=f=1400:w=1800,afade=t=out:st=0.03:d=0.11,volume=2.2"))
ff("stomp", noise("brown", 0.25, "lowpass=f=500,afade=t=out:st=0.05:d=0.2,volume=5"))
ff("thump", ae("0.9*sin(2*PI*(60+90*exp(-25*t))*t)*exp(-9*t)", 0.3, "afade=t=out:st=0.25:d=0.05"))
random.seed(4)
tink = "+".join(f"0.25*sin(2*PI*{random.randint(3000, 7000)}*(t-{d:.2f}))*exp(-30*(t-{d:.2f}))*gt(t,{d:.2f})" for d in [0.02, 0.07, 0.12, 0.19, 0.26, 0.33, 0.41])
ff("smash", ae(tink, 0.7, "volume=1.4"))
ff("smash_noise", noise("white", 0.5, "highpass=f=1800,afade=t=out:st=0.05:d=0.45,volume=1.8", 0.8))
ff("crack", noise("pink", 0.4, "bandpass=f=900:w=1200,tremolo=f=28:d=1,afade=t=out:st=0.2:d=0.2,volume=3"))
ff("clink", ae("0.4*(sin(2*PI*2600*t)+0.6*sin(2*PI*3900*t))*exp(-35*t)", 0.2))
ff("drip", ae("0.5*sin(2*PI*(1800*t-9000*t*t))*exp(-18*t)", 0.15, "afade=t=out:st=0.1:d=0.05"))
ff("icecrack", noise("white", 0.6, "highpass=f=2500,tremolo=f=14:d=1,afade=t=out:st=0.3:d=0.3,volume=1.5", 0.7))
ff("ding", ae("0.5*(sin(2*PI*1568*t)+0.4*sin(2*PI*3136*t))*exp(-7*t)", 0.7, "afade=t=out:st=0.6:d=0.1"))
ff("kaching", ae("0.45*(sin(2*PI*1318*t)+0.4*sin(2*PI*2636*t))*exp(-6*t)+0.45*(sin(2*PI*1760*(t-0.11))+0.4*sin(2*PI*3520*(t-0.11)))*exp(-5*(t-0.11))*gt(t,0.11)", 1.0, "afade=t=out:st=0.85:d=0.15"))
ff("tick", ae("0.45*(sin(2*PI*1568*t)+sin(2*PI*2093*(t-0.07))*gt(t,0.07))*exp(-9*t)", 0.3, "afade=t=out:st=0.25:d=0.05"))
ff("sweep_up", ae("0.22*sin(2*PI*(380*t+270*t*t))*(0.5+0.5*sin(2*PI*14*t))", 2.4, "afade=t=in:d=0.2,afade=t=out:st=2.2:d=0.2"))
ff("rumble", noise("brown", 0.5, "lowpass=f=300,afade=t=in:d=0.45,afade=t=out:st=0.45:d=0.05,volume=6"))
tr = "(sin(2*PI*f*t)+0.5*sin(4*PI*f*t)+0.33*sin(6*PI*f*t)+0.25*sin(8*PI*f*t))"
f = "if(lt(t,0.38),233,if(lt(t,0.76),220,if(lt(t,1.14),208,196)))"
env = "(0.5+0.5*gt(t,0))*exp(-3*mod(t,0.38))*lt(t,1.14)+exp(-1.5*(t-1.14))*gte(t,1.14)"
ff("sadtrombone", ae(f"0.28*{tr.replace('f', f)}*({env})", 2.0, "lowpass=f=1100,vibrato=f=6:d=0.15,afade=t=out:st=1.7:d=0.3"))

meta = {}
for fn in sorted(os.listdir(out)):
    if not fn.endswith(".wav"):
        continue
    p = os.path.join(out, fn)
    r = subprocess.run(["ffmpeg", "-hide_banner", "-i", p, "-af", "volumedetect", "-f", "null", "-"], capture_output=True, text=True)
    mx = float(re.search(r"max_volume: (-?[\d.]+) dB", r.stderr).group(1))
    tmp = os.path.join(out, "n_" + fn)
    subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", p, "-af", f"volume={-3.0 - mx}dB,alimiter=limit=0.7", "-ar", "48000", tmp], check=True)
    os.replace(tmp, p)
    dur = float(subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", p], capture_output=True, text=True).stdout)
    meta[fn[:-4]] = round(dur, 3)
open(os.path.join(out, "meta.js"), "w").write("window.SFX2=" + json.dumps(meta) + ";")
print("done", len(meta), "effects")
