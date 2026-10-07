# Gotchas (each one cost real time)

## GSAP on SVG (the big one)
- `svgOrigin` is in the PARENT's coordinate space, not the element's. On a group that has its own `translate()` (every `item`/`svgEl` outer) a scale/rotate with `svgOrigin:"0 0"` pivots around the SCREEN origin: things fly in from the top-left, rotate across the screen, or a swinging man drifts away.
  - Scale/rotate things with default centre origin (omit svgOrigin) - `popIn` does this.
  - Actors: moves on the outer, scale/rotate on the child `#id-w` (origin = feet, safe), jitter on `#id-j`. Never rotate the outer.
  - A flying prop that also spins: outer moves (x,y), inner `#phIn`-style group rotates/scales with svgOrigin "0 0" (it has no translate).
- Never `gsap.set(el,{scale:0})` on SVG groups (kills the parsed x/y): use 0.2-0.3.
- `tl.to(inner, {scale: 1.07})` on a group whose attribute is `scale(3.2)` sets the ABSOLUTE scale (shrinks it to 1.07). Tween to `3.2*1.07`. (The first ad's last second had this bug.)
- Animating `y` on an element that has a `transform="translate()"` attribute replaces that translate. Use `dropIn/riseIn` on `inner(item)`.
- Smoke/anything tweening x,y AND scale together: no svgOrigin.
- The kit patch only pre-sets svgOrigin once; set `el.__so = 1` after your own `gsap.set(el,{svgOrigin})` (the lib does).

## Timeline
- Actors/entrances: anything entering after the scene starts must be hidden until its time (`enterStep` does; a bare `fromTo` with `immediateRender:false` leaves it visible early).
- `pose()` calls must be in chronological order per actor; `talk()` leaves the actor on its first pose.
- Overlapping tween on the same property after a `tl.set` overrides it (the typing dots re-appeared over the TRUST word): keep tweens inside the window of the set.
- Hard cuts: `L.scenes` sets opacity; a scene is invisible outside its window, but its actors are visible for the whole window.

## Rendering / tooling
- `hyperframes snapshot` overwrites `public/snapshots`; read the contact sheet immediately after each run, and `rm -rf public/snapshots` before the final render.
- Black snapshots = JS error. Serve `public/` (`python -m http.server 8765`), open it in the browser pane, `read_console_messages onlyErrors`.
- Lint counts audio tags written inside HTML comments (error `audio_src_not_found`). Keep comments free of `<audio>`.
- `ffmpeg hstack` drops alpha: a sticker contact sheet made that way shows white boxes. Composite with PIL.
- Cut-out model drops white/beige objects on white backgrounds (monitor, tablet): composite props separately.
- Render ~7 min per 40 s at `-q high`: run in the background, then make the share copy with `-preset veryfast` (never `slow`).
- Pin `hyperframes@0.8.105` in every npx call.

## Tools that are NOT available
No direct ElevenLabs tool: use your own account or the Fal.ai route (see voice.md). Fal.ai works via the FAL_KEY env var (see cast-generation.md), no HeyGen CLI (media-use's online SFX catalog needs login), no poppler (use headless Chrome screenshots to proof PDFs).

## Lead-magnet PDF (HTML -> PDF)
Write `guide.html` with `@page{size:A4;margin:0}`, `.page{width:210mm;height:297mm;break-after:page}`, Montserrat via a relative `@font-face`.
`"/c/Program Files/Google/Chrome/Application/chrome.exe" --headless=new --disable-gpu --no-pdf-header-footer --print-to-pdf="C:\\path\\out.pdf" "file:///C:/path/guide.html"`
Proof by screenshotting the HTML at 794 x (1123*pages) px and cropping pages with PIL. Flag placeholders (contact details) in the reply.
