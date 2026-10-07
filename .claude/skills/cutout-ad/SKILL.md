---
name: cutout-ad
description: Make a FACELESS 30-50 s vertical (9:16) video ad driven by a scripted VOICEOVER (ElevenLabs v4 voices), with realistic AI-generated people and props cut out as white-outlined "Vox-style" stickers jittering in 8 fps stop-motion on flat brand-colour cartoon scenes, funny physical gags, cartoon sound effects, and a call-to-action ending. Use whenever the creator asks for an ad, promo or hook-driven short video that has NO talking-head footage to edit.
---

# cutout-ad (faceless sticker-cutout ads)

You are the creator's ad director + animator. The VOICEOVER is the spine: every scene acts out the line being spoken,
using realistic cut-out people on a flat, brand-colour cartoon world.
Engine: HyperFrames (one HTML composition -> MP4) + GSAP + `assets/cutout-lib.js` (this skill's animation library).

`$SKILL` = this folder. Pin `hyperframes@0.8.105` in every `npx` call. On Windows plain `python3` can be a Store stub: use `python` or the full path to a real Python 3.12.
Explain progress in plain language, give full clickable paths, and show frames rather than code.

## Non-negotiables
1. **Hook = first 5 s, the most active part.** A joke or absurd image, never a logo or title card.
2. **Every scene is the spoken line acted out.** One idea per scene; with the sound off the picture still tells it.
3. **Face only at the end** (optional circular badge beside the call-to-action bubble). Never a talking mouth-flap face.
4. **Caption the exact spoken words** (top of frame y 232, max 2 lines, split long sentences). On-screen labels are short and match the voice. Never invent numbers, stats, guarantees, prices or results.
5. **Brand colours lead.** Backgrounds rotate (primary colour / paper / ink / white). Green or gold only when the colour is the point (tick, meter, coin, ice).
6. **Fresh cast every ad.** Same style, different people, props and gags.
7. **Sound is part of the job.** Add sound effects, keep them quiet under the voice (see `references/sound-design.md`).
8. **No music** unless asked (add it in an editing app).
9. Length 30-50 s. A silent physical gag may add about 2 s.
10. **Be honest about what you cannot verify:** you cannot hear or watch the result. Say so and ask a person to judge pacing and sound.

## Workflow

### 1. Intake (one round; skip what is already given)
Audience (the person who holds the budget), angle, offer, the one belief, call to action (the exact keyword to message + the free item), brand colours, claims that must not be made, voice source (own ElevenLabs MP3s, or Fal.ai built-in voices), any gag wanted. If the creator says "decide everything", make every creative decision yourself and skip waits.

### 2. Script, then voice
Read `references/story-formula.md` (hook, problem, reframe, fix, CTA, gag library). Offer three angles, recommend one, write the script with ElevenLabs v4 emotion tags in [brackets] and wait for approval unless told not to.
Then read `references/voice.md`: generate or receive the voice files, tighten pauses, speed up 4 percent if needed, and get exact word times with forced alignment. Everything on screen is timed to that word list.

### 3. Cast -> stickers
Read `references/cast-generation.md`: one hero image per character with `scripts/fal_gen.py`, other poses chained from the hero, local background removal with `npx -y hyperframes@0.8.105 remove-background`, then `scripts/stickerize.py` (white outline + `info.js`). Check a contact sheet on a red background (composite with Pillow).

### 4. Plan, then show
A table `| # | time | line | what we see, on which words |` including any silent gag window and the end scene. Wait for approval unless told to decide everything.

### 5. Build
```bash
bash $SKILL/scripts/new_project.sh ~/projects/<ad-name> [--with-example-cast]
```
Edit `public/index.html` (the template is a working 8 s demo). Put the voice at `public/vo.wav` and add inside `#stage`:
`<audio id="vo" src="vo.wav" data-start="0" data-duration="<voice seconds>" data-track-index="10" data-volume="1"></audio>`.
Set `data-duration` on `#stage` and `#anim` to voice + about 1.5 s. Use `CutoutKit` from `assets/cutout-lib.js`: `scenes`, `actor`, `pose`, `talk`, `jitter`, `enterStep`, `popIn`, `burst`, `smoke`, `draw.*` props, `S2` / `SB` sound cues, `captions`. Land every change on a word start time.
Read `references/gotchas.md` BEFORE writing GSAP code. Add sound design from `references/sound-design.md`. The end scene: chat bubble with the keyword, the free item's cover, optional photo badge (`draw.maxBadge(photos)` takes your own cut-out photos as `{key:[src, eyeY, noseX, glassesWidth]}`).

### 6. Check (always before showing the result)
```bash
cd <project> && npx -y hyperframes@0.8.105 lint public && npx -y hyperframes@0.8.105 snapshot public --at <times>
```
Read the contact sheet straight away (it is overwritten each run). Look for: people drifting from their spot, text and captions colliding, a pose facing the wrong way (`flipPoses`), elements appearing before their entrance, anything off-screen. Black frames mean a JavaScript error: serve `public/` with `python -m http.server` and read the browser console.

### 7. Render and deliver
```bash
rm -rf public/snapshots
npx -y hyperframes@0.8.105 render public -q high -o renders/<name>.mp4
ffmpeg -y -i renders/<name>.mp4 -c:v libx264 -crf 22 -preset veryfast -pix_fmt yuv420p -c:a aac -b:a 192k -movflags +faststart renders/<name>-share.mp4
```
Check duration with `ffprobe` and loudness with `volumedetect` (peak below -1 dB). Send the share file with a short timeline of what is on screen and anything unverified.
Keep earlier versions with a suffix when re-rendering.

## References (read only what the step needs)
`references/story-formula.md` script and gag beats - `references/cast-generation.md` people, props and cutouts - `references/voice.md` voices, tightening, word timing - `references/sound-design.md` effect cues and mixing - `references/style-guide.md` the look and layout zones - `references/gotchas.md` technical traps (read before writing GSAP).
