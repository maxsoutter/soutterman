# Voice

The voiceover defines the timeline. Get it final BEFORE planning scenes.

## Option A: ElevenLabs v4 file from the creator's own account
Generate in ElevenLabs with the Eleven v4 model, emotion/audio tags in square brackets, and download the MP3. One file per speaking character.
Tags that worked: `[excited]`, `[frustrated sigh]`, `[brief chuckling]`, `[dramatic sigh]`, `[dry]`, `[sympathetic]`, `[serious]`, `[calm]`, `[friendly]`, `[furious]`, `[warm, polite smile]`. Tags are not spoken.
Compare the spoken file with the script: captions follow the FILE, not the draft. To change a line, regenerate and re-supply it.

## Option B: Fal.ai (no ElevenLabs account)
`elevenlabs/tts/eleven-v4` via `FAL_KEY`. POST `https://fal.run/elevenlabs/tts/eleven-v4` with `{text, voice, stability (0.4), output_format ("mp3_44100_192")}`; the response has `audio.url`. Only built-in names work (for example "Brian" deep warm male, "Matilda" female); a library voice name is rejected with "Invalid voice ID". Two characters = two calls.

## Build the final audio
1. Convert to wav (`ffmpeg -i x.mp3 -ar 48000 -ac 1 x.wav`), join characters with 0.3 s of silence.
2. Tighten: shorten silent gaps longer than 0.45 s to 0.3 s (measure loudness every 10 ms; digital silence is near zero), keeping a little silence each side. Then `ffmpeg -i in.wav -af atempo=1.04 out.wav` (4 percent faster, pitch unchanged).
3. Silent gag: find a true-silent point with `python scripts/assemble_voice.py voice.wav --energy T0 T1` (loudness every 20 ms), then `python scripts/assemble_voice.py voice.wav out_dir --cut T:2.0` inserts a 2 s silent beat and prints `word@time` for the new audio. Everything after the cut shifts by the gap.
4. Swap one sentence later: keep audio up to a true-silence point just before it, append the new sentence (trim its leading silence, apply the same speed), then re-run the word timing.

## Exact word timings (forced alignment on Fal)
POST `https://fal.run/fal-ai/elevenlabs/forced-alignment` with `{text: <exact spoken words, no tags>, audio_url: "data:audio/mpeg;base64,<mp3 as base64>"}`. The response `words[]` has `text`, `start`, `end` (skip entries that are only whitespace). Tiny cost, accurate to about 0.1 s.
Local alternative: `python scripts/transcribe.py file.wav out_dir` (faster-whisper; writes words.txt and phrases.json). It can drift by about 0.4 s after long silences: verify against loudness before trusting it.
Spell numbers as words in the script ("ten") and give the aligner the same spelling.

## Timing
The voice file defines the video length; hold the last frame about 1.5 s. Plan from the `word@time` list.
