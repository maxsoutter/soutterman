# Jungle Dash!

A 20-second cartoon made entirely in Java. Pip, a tiny fluffball, is chased through the jungle by a lion. Pip cannonballs into a river, and it turns out the lion **really** hates water.

- All visuals are drawn frame by frame with Java2D.
- All music and sound effects are synthesized in code.
- ffmpeg encodes the frames and audio into `jungle-dash.mp4`.

## Rebuild

```bash
pip install imageio-ffmpeg          # provides an ffmpeg binary
javac -d build JungleDash.java
java -Djava.awt.headless=true -cp build JungleDash \
  "$(python3 -c 'import imageio_ffmpeg;print(imageio_ffmpeg.get_ffmpeg_exe())')" jungle-dash.mp4
```
