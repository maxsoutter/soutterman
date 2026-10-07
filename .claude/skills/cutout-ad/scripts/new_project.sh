#!/usr/bin/env bash
# Scaffold a cutout-ad project.   usage: new_project.sh <project_dir> [--with-example-cast]
# Creates <project_dir>/{public,work,renders}; public/ gets the template, vendor libs, fonts, SFX (bundled + synthesised).
# Run from anywhere; SKILL is resolved from this script's location.
set -e
SKILL="$(cd "$(dirname "$0")/.." && pwd)"
P="$1"; [ -z "$P" ] && { echo "usage: new_project.sh <project_dir> [--with-example-cast]"; exit 1; }
mkdir -p "$P/public/vendor" "$P/public/fonts" "$P/public/sfx" "$P/public/sfx2" "$P/public/img" "$P/work/raw" "$P/renders"
cp "$SKILL/assets/gsap.min.js" "$SKILL/assets/motion-kit.js" "$SKILL/assets/cutout-lib.js" "$P/public/vendor/"
cp "$SKILL/assets/fonts/"* "$P/public/fonts/"
cp "$SKILL/assets/sfx/"*.mp3 "$P/public/sfx/"
cp "$SKILL/assets/sfx2/"* "$P/public/sfx2/"
cp "$SKILL/templates/index.html" "$P/public/index.html"
if [ "$2" = "--with-example-cast" ]; then
  cp "$SKILL/assets/example-cast/"*.png "$P/public/img/"
  echo 'window.INFO={"R_talk1":{"w":434,"h":983},"R_talk2":{"w":448,"h":972},"R_slump":{"w":340,"h":983},"E_stern":{"w":307,"h":1029}};' > "$P/public/img/info.js"
else
  echo 'window.INFO={};' > "$P/public/img/info.js"
fi
echo "project ready: $P  (edit public/index.html; put the voiceover at public/vo.wav)"
