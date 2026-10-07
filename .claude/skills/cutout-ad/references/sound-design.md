# Sound design

Why: the first render of ad 2 had real silence under the smash; a silent render felt dead, and effects at full level hid the voice.
Result: effects at ~0.7x under speech, full level only in silent gags.

## Sources (all offline, free, no accounts)
- `assets/sfx/*.mp3` bundled (pop, whoosh, whoosh-short, impact-bass-1/2, ping, chime, notification, typing, sparkle, click-soft, key-press, error).
- `assets/sfx2/*.wav` synthesised by `scripts/gen_sfx.py` (ffmpeg only): ring, ring_blip, scratch, slide_down, boing, bonk, slap, stomp, thump, smash, smash_noise, crack, clink, drip, icecrack, ding, kaching, tick, sweep_up, rumble, sadtrombone. All peak at -3 dB so the volume numbers are comparable.
- HeyGen's catalog needs an account login (not set up). Do not download sounds from the web without asking.

## Cue map (volumes before ducking)
| Moment | Cues |
|---|---|
| handsets flying / calls | `ring_blip` 0.12 each, `ring` 0.5 for the real call |
| bell ping / green tick | `ping` 0.28 / `tick` 0.35 |
| chart at zero, sag, "?" | `scratch` 0.55, `slide_down` 0.3, `boing` 0.4 |
| wind-up | `rumble` 0.35, grunt-less |
| throw | `whoosh` 0.7 |
| wall hit | `impact-bass-2` 0.7 + `smash` 0.75 + `smash_noise` 0.4 + `crack` 0.5 |
| ricochet / floor | `clink` x3 0.35 + `thud` |
| each stamp | `stomp` 0.7 + `smash` 0.3 |
| paddle goes up / SOLD | `slap` 0.8 (+ `impact-bass-1` 0.5), SOLD adds `kaching` 0.5 + `ding` |
| speech bubble bonks | `bonk` 0.6 on the HIT (start + 0.32) |
| rep slumps | `sadtrombone` 0.45 |
| title word slams | `impact-bass-1` 0.55 / `thump` 0.4 / `impact-bass-2` 0.65 |
| ad cards fly | `whoosh-short` 0.22 |
| ice melts | `icecrack` 0.4, `drip` x3 0.35, `chime` 0.3 |
| meter fills | `sweep_up` 0.28 (2.4 s) |
| CTA bubble | `typing` 0.35, `notification` 0.4, `ding` on the free-test cover |
Cuts: whoosh 0.22 automatically from `L.scenes`; `popIn`/`enterStep` add a quiet pop.

## Mix rules
- `CutoutKit(tl,K,{silent:[[t0,t1]], duck:0.7})`: cues inside `silent` windows play at full level, everything else x0.7.
- Voice stays at 1.0. Final peak should be under -1 dB (`ffmpeg -af volumedetect`). Mean ~ -23 dB was fine.
- Audio elements must be within `#stage`, unique ids, tracks 30-43 rotate. Do NOT put a literal audio tag inside an HTML comment: the linter counts it.
- To verify without ears: compare `volumedetect` max in the gag window before/after (it went from -91 dB to -4 dB).
- You cannot hear the result: tell the creator so and ask them to judge balance; adjust the global `duck` or single cues.
