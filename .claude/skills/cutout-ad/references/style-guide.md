# The look

One line: **realistic sticker cut-outs (white outline, hard drop shadow) jittering at 8 fps on flat brand-colour cartoon scenes, hard-cut like a collage; funny, fast, clear.**

- Canvas 1080x1920. Scenes = full-bleed flat colour: brand primary (example red #E3241B), paper #EDEDEB, ink #111, white. Rotate them so each cut reads. Add a sunburst (14 rays, a darker shade of the primary at 50 percent) behind big title words.
- People = AI photos, full-body, anchored bottom-centre, scale 0.6-1.0 (head top about y 900-1100 so the upper half is free for graphics). Two or three people per frame at most. The bottom 380 px is the app-button zone: figures may overlap it, text must not.
- Stop-motion: `jitter` (x/y +-3-5 px, rotation +-1.3-1.8 deg, 8 fps) on every actor all the time; steps() easing for moves (steps(4..10)); entrances from off-screen with `enterStep`; pop-ins with `popStep`. Never smooth eases on stickers; `back.out` is fine on flat props.
- Talking = flip between two poses every 0.14-0.2 s (not a mouth flap).
- Flat props: solid fills, dark outline 7-9 px, round joins, no gradients, glows or shadows.
- Captions: white pill, 5 px dark border, Montserrat 800 50 px, at the TOP (y 232), 1-2 lines (split anything over about 36 characters), curly quotes, the spoken words exactly, including "!" and "&".
- Text on graphics: Montserrat 900, few words, the same words the voice says.
- Cartoon FX are drawn, not photographic: smoke puffs, starbursts, cracks, shards, ticks, STOP octagon, coin, meter, ice, confetti. One focal prop per scene.
- End scene: primary-colour background, big white chat bubble with the keyword (dots typing, then the word), optional circular photo badge (white ring, ink ring, paper fill) with a name pill, supporting characters either side, optional FREE starburst + free-item cover.
- Fonts: Montserrat variable (in assets/fonts, SIL Open Font License). Grain overlay 10 percent multiply.
- No AI video and no AI-generated backgrounds: backgrounds are code.
