# Cast generation (Fal.ai) -> sticker cut-outs

Model: `openai/gpt-image-2` (text-to-image) and `openai/gpt-image-2/edit` (with reference images), through `scripts/fal_gen.py`.
Needs `FAL_KEY` in the environment. Never print it. About 25 images per ad; check Fal's current pricing (roughly $4 for a full cast in testing).

## Run it
```bash
mkdir -p work/cast work/raw && cd work
python $SKILL/scripts/fal_gen.py batch1.json     # heroes + props
python $SKILL/scripts/fal_gen.py batch2.json     # chained poses
```
`fal_gen.py <jobs.json>` runs from a folder containing `cast/`. Each job is `{ "name", "prompt", "refs"?, "size"? }`. It saves `cast/<name>.png` and records every image's URL in `cast/urls.json` so later jobs can use `refs: ["<name>"]`.
Sizes: people `{"width":1024,"height":1536}` (default), props `{"width":1024,"height":1024}`, wide props `{"width":1536,"height":1024}`.
A rate-limit error only fails that item: resubmit only the failed one. Never resubmit images that succeeded.

## Prompt recipe (people)
`Photorealistic full-body studio photo of <age/ethnicity/role detail, clothes with a brand-colour accent, e.g. a red tie>. <pose + expression, exaggerated, comedic>. Isolated on a pure flat white seamless background, soft even lighting, no cast shadow, full body visible head to shoes.`
- Be specific about pose and face (mouth wide open, teeth bared, eyes bulging, tears streaming). AI faces under-act: ask for MORE.
- Say what is in the hands ("gripping a phone", "nothing in his hands") and that it is clearly visible.
- Do NOT put a white or beige object on the white background (monitor, paper): the cut-out drops it. Generate such props separately or on a darker background.

## Consistency: generate one, chain the rest
1. Batch 1: one hero pose per character (independent requests). Look at the sheet; keep the good ones.
2. Batch 2: every other pose with `refs: ["<hero name>"]` and the prompt `The same <man/woman> from the reference image (same face, same outfit, same hair). <new pose>. <the white-background sentence> Hugely exaggerated, comedic pose and expression.`
3. A main character needs about 4-6 poses: talk A, talk B (different mouth/hands for the two-pose flip), react/slump, cheer, plus gag poses (wind-up, follow-through, stomp up and down, kneeling in despair). Supporting characters need 2 (stern -> smiling).
4. Props are 1:1 photos on white: a stack of folders, a billboard model, a mug, a table, a phone. Wide props (a table) 1536x1024.
5. Direction: a throw pose must face the target. If the follow-through points the wrong way, mirror that single pose with `flipPoses:["R_follow"]` in `actor`.

## Cut out and sticker-ise
```bash
cd work
npx -y hyperframes@0.8.105 remove-background cast/Name.png -o raw/raw_Name.png      # local model, no key
cp raw/raw_*.png ../public/img/ && python $SKILL/scripts/stickerize.py ../public/img && mv ../public/img/raw_*.png raw/
```
`stickerize.py` crops, adds the white outline and writes `info.js` (width/height of every image; the page needs it to anchor each pose). Re-run it whenever you add images.
Props the model fragments (dark furniture, glossy objects, white paper on white): run `python $SKILL/scripts/flood_cutout.py <name>` (flood-fills the white background from the border with Pillow, writes `raw/raw_<name>.png`), then stickerize as usual. For objects with enclosed holes (a table with legs), treat every near-white pixel as transparent instead.
Preview: composite all stickers on a red background with Pillow (paste with the alpha mask). An ffmpeg hstack drops alpha and shows white rectangles that are NOT in the files.

## Optional face badge (end scene only)
Take 2-3 photos of yourself with different expressions (neutral, smug, laughing), remove the background, and use `draw.maxBadge(photos)` with `{key:[src, eyeY, noseX, glassesWidth]}` per photo (eye line, nose x and glasses width in the source image, so the badge crops consistently). Swap expressions on the spoken words by toggling opacity with `tl.set`. Jitter the badge at 8 fps like everyone else.
