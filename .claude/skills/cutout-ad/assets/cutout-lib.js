/* cutout-lib.js — helpers for the faceless "cutout ad" style (Vox-style realistic sticker cut-outs on flat-vector scenes,
 * 8 fps stop-motion jitter, hard-cut scenes, cartoon SFX under a voiceover).
 *
 * Load order in index.html:  vendor/gsap.min.js, vendor/motion-kit.js, vendor/cutout-lib.js, img/info.js, sfx2/meta.js
 *   const tl = gsap.timeline({ paused: true });
 *   const K  = MotionKit(tl, { stage: "#stage", fps: 30 });
 *   const L  = CutoutKit(tl, K, { silent: [[9.95, 11.97]] });      // silent = windows where there is NO voice (SFX at full level)
 *
 * GOTCHA (cost us hours): GSAP's svgOrigin is in the PARENT's coordinate space. Never tween scale/rotation with svgOrigin
 * on a group that carries its own translate(). Moves go on the outer group, scale/rotate on a child wrapper
 * (actor "-w", jitter "-j") whose origin is the feet, or use the default centre origin (popIn). See references/gotchas.md.
 */
(function () {
  window.CutoutKit = function (tl, K, opts = {}) {
    const { C, PROPS, svgEl, q } = K;
    const INK = "#111111";
    const INFO = window.INFO || {};
    const stage = document.querySelector(opts.stage || "#stage");
    const world = document.querySelector(opts.world || "#world");
    const NS = "http://www.w3.org/2000/svg";

    const T = (s, x, y, size, fill = INK, extra = "") =>
      `<text x="${x}" y="${y}" text-anchor="middle" style="font-family:Montserrat,sans-serif;font-weight:900;font-size:${size}px" fill="${fill}" ${extra}>${s}</text>`;
    // item: outer = translate (movers, popIn target), inner = scale (riseIn/dropIn target)
    const item = (parent, html, x, y, s = 1) => svgEl(parent, `<g transform="scale(${s})">${html}</g>`, `translate(${x},${y})`);
    const inner = (o) => o.firstElementChild;
    // centre-pivot pop
    const popIn = (sel, t, sound = true) => {
      if (sound) K.sfx("pop", t, 0.22);
      tl.fromTo(sel, { opacity: 0, scale: 0.3 }, { opacity: 1, scale: 1, duration: 0.45, ease: "back.out(2.2)" }, q(t));
    };
    const rng = (seed) => { let s = seed * 9301 + 49297; return () => { s = (s * 9301 + 49297) % 233280; return s / 233280 - 0.5; }; };

    // ---------------- scenes: hard cuts, each a full-bleed flat colour ----------------
    // list = [[id, start, end, bgColour], ...]; creates <g id> groups inside #world; first scene starts visible.
    const scenes = (list) => {
      list.forEach(([id, , , bg]) => {
        const g = document.createElementNS(NS, "g");
        g.setAttribute("id", id);
        g.innerHTML = `<rect id="${id}bg" x="-20" y="-20" width="1120" height="1960" fill="${bg}"/>`;
        world.appendChild(g);
      });
      gsap.set(list.map((s) => "#" + s[0]), { opacity: 0 });
      list.forEach(([id, a, b], i) => {
        tl.set("#" + id, { opacity: 1 }, q(a));
        if (i < list.length - 1) tl.set("#" + id, { opacity: 0 }, q(b));
        if (i > 0) K.sfx("whoosh", a - 0.05, 0.22);
      });
    };

    // ---------------- sticker actors ----------------
    // outer(translate) > -w (rotate/scale, origin = feet) > scale > -j (jitter) > <image>s anchored bottom-centre at (0,0)
    // poses = names of img/<name>.png (must exist in INFO). flip mirrors the whole actor; flipPoses mirrors single poses.
    const actors = {};
    const actor = (parent, id, poses, x, y, s, first, flip = false, flipPoses = []) => {
      const outer = svgEl(
        parent,
        `<g id="${id}-w"><g transform="scale(${flip ? -s : s},${s})"><g id="${id}-j"><g style="filter:drop-shadow(8px 10px 0 rgba(17,17,17,0.28))">${poses
          .map((p) => { const d = INFO[p]; return `<image id="${id}-${p}" href="img/${p}.png" x="${-d.w / 2}" y="${-d.h}" width="${d.w}" height="${d.h}" opacity="${p === first ? 1 : 0}"${flipPoses.includes(p) ? ' transform="scale(-1,1)"' : ""}/>`; })
          .join("")}</g></g></g></g>`,
        `translate(${x},${y})`
      );
      actors[id] = { outer, cur: first, s, x, y };
      ["-j", "-w"].forEach((k) => { gsap.set("#" + id + k, { svgOrigin: "0 0" }); document.getElementById(id + k).__so = 1; });
      return outer;
    };
    // pose swaps must be called in chronological order per actor
    const pose = (id, name, t) => { const a = actors[id]; tl.set("#" + id + "-" + a.cur, { opacity: 0 }, q(t)); tl.set("#" + id + "-" + name, { opacity: 1 }, q(t)); a.cur = name; };
    // "talking": flip between two poses every `period` seconds, ending on pose a
    const talk = (id, a, b, t0, t1, period = 0.16) => { let flag = 1; for (let t = t0; t < t1; t += period) { pose(id, flag ? b : a, t); flag = 1 - flag; } if (actors[id].cur !== a) pose(id, a, t1); };
    // stop-motion boil: 8 "frames" a second of small random x/y/rotation on the actor's jitter group
    const jitter = (id, t0, t1, amp = 3, rot = 1.3, seed = 1, fps = 8) => {
      const r = rng(seed + id.length * 7);
      for (let t = t0; t < t1; t += 1 / fps) tl.set("#" + id + "-j", { x: r() * amp * 2, y: r() * amp * 2, rotation: r() * rot * 2 }, q(t));
    };
    // stepped entrance from an offset; actor stays hidden until t
    const enterStep = (id, t, dx = 0, dy = 0, d = 0.5, steps = 6) => {
      const a = actors[id];
      gsap.set(a.outer, { opacity: 0 });
      tl.set(a.outer, { opacity: 1 }, q(t));
      tl.fromTo(a.outer, { x: a.x + dx, y: a.y + dy }, { x: a.x, y: a.y, duration: d, ease: `steps(${steps})`, immediateRender: false }, q(t));
      K.sfx("pop", t, 0.2);
    };
    // pop an actor in by its wrapper (pivot = feet)
    const popStep = (id, t, d = 0.35) => {
      gsap.set("#" + id + "-w", { opacity: 0 });
      tl.fromTo("#" + id + "-w", { scale: 0.55, opacity: 0 }, { scale: 1, opacity: 1, duration: d, ease: "steps(4)", immediateRender: false }, q(t));
      K.sfx("pop", t, 0.22);
    };
    // comic impact starburst (white by default)
    const burst = (parent, x, y, t, fill = "#fff", size = 1) => {
      const pts = Array.from({ length: 16 }, (_, i) => { const a = (Math.PI / 8) * i; const rr = i % 2 ? 70 : 150; return `${(Math.cos(a) * rr).toFixed(1)},${(Math.sin(a) * rr).toFixed(1)}`; }).join(" ");
      const b = item(parent, `<polygon points="${pts}" fill="${fill}" stroke="${INK}" stroke-width="8" stroke-linejoin="round"/>`, x, y, size);
      popIn(b, t, false);
      tl.to(b, { opacity: 0, scale: 1.5, duration: 0.2, ease: "steps(3)" }, q(t + 0.22));
      K.sfx("thud", t, 0.3);
      return b;
    };
    // cartoon smoke puffs rising from points (e.g. ears): no svgOrigin when x/y and scale tween together
    const smoke = (parent, earL, earR, t0, t1, seed = 3) => {
      const r = rng(seed);
      for (let t = t0; t < t1; t += 0.3) {
        [earL, earR].forEach(([ex, ey], side) => {
          const g = item(parent, `<g fill="#fff" stroke="${INK}" stroke-width="8"><circle r="38"/><circle cx="-34" cy="18" r="28"/><circle cx="34" cy="20" r="30"/></g>`, ex, ey, 1);
          gsap.set(g, { opacity: 0 });
          const dir = side === 0 ? -1 : 1;
          tl.fromTo(g, { x: ex, y: ey, scale: 0.4, opacity: 1 }, { x: ex + dir * (90 + r() * 90), y: ey - 260 - r() * 90, scale: 1.7, opacity: 0, duration: 0.95, ease: "steps(8)", immediateRender: false }, q(t + side * 0.15));
        });
      }
    };
    // typed-title words that slam in and then boil (used on the title card)
    const boilText = (g, t0, t1, seed = 5) => { const r = rng(seed); for (let t = t0; t < t1; t += 1 / 8) tl.set(inner(g), { x: r() * 8, y: r() * 8, rotation: r() * 3 }, q(t)); };

    // ---------------- flat-vector drawings (origin = centre unless noted) ----------------
    const draw = {
      handset: () => `<path d="M -80 10 C -80 -50 80 -50 80 10 L 80 30 Q 80 40 70 40 H 35 Q 25 40 25 30 V 10 Q 25 0 15 0 H -15 Q -25 0 -25 10 V 30 Q -25 40 -35 40 H -70 Q -80 40 -80 30 Z" fill="${INK}" stroke="#fff" stroke-width="6" stroke-linejoin="round"/>`,
      tick: () => `<circle r="42" fill="${C.go}" stroke="${INK}" stroke-width="7"/><path d="M -18 2 L -4 18 L 22 -14" fill="none" stroke="#fff" stroke-width="12" stroke-linecap="round" stroke-linejoin="round"/>`,
      star: `<path d="M 0 -34 l 9 20 21 3 -15 15 4 21 -19 -10 -19 10 4 -21 -15 -15 21 -3 Z" fill="${C.sun}" stroke="${INK}" stroke-width="5" stroke-linejoin="round"/>`,
      coin: () => `<circle r="62" fill="${C.sun}" stroke="${INK}" stroke-width="8"/><circle r="44" fill="none" stroke="${INK}" stroke-width="4"/>${T("$", 0, 20, 56)}`,
      envelope: () => `<rect x="-80" y="-56" width="160" height="112" rx="14" fill="#fff" stroke="${INK}" stroke-width="7"/><path d="M -80 -48 L 0 18 L 80 -48" fill="none" stroke="${INK}" stroke-width="7" stroke-linejoin="round"/>`,
      cart: (col = INK, fill = "none") => `<path d="M -120 -90 H -76 L -44 42 H 82 L 104 -44 H -66" fill="none" stroke="${col}" stroke-width="12" stroke-linecap="round" stroke-linejoin="round"/><path d="M -66 -44 H 104 L 82 42 H -44 Z" fill="${fill}" stroke="${col}" stroke-width="10" stroke-linejoin="round"/><circle cx="-18" cy="86" r="15" fill="${col}"/><circle cx="62" cy="86" r="15" fill="${col}"/>`,
      eye: () => `<path d="M -110 0 Q 0 -96 110 0 Q 0 96 -110 0 Z" fill="#fff" stroke="${INK}" stroke-width="8" stroke-linejoin="round"/><circle r="44" fill="${C.red}" stroke="${INK}" stroke-width="6"/><circle r="19" fill="${INK}"/><circle cx="12" cy="-14" r="7" fill="#fff"/><g stroke="${INK}" stroke-width="8" stroke-linecap="round"><path d="M 0 -118 V -158"/><path d="M -70 -98 L -96 -132"/><path d="M 70 -98 L 96 -132"/></g>`,
      stop: () => { const pts = Array.from({ length: 8 }, (_, i) => { const a = (Math.PI / 4) * i + Math.PI / 8; return `${(Math.cos(a) * 150).toFixed(1)},${(Math.sin(a) * 150).toFixed(1)}`; }).join(" "); return `<polygon points="${pts}" fill="${C.red}" stroke="#fff" stroke-width="12" stroke-linejoin="round"/>${T("STOP", 0, 26, 76, "#fff")}`; },
      // TRUST meter: fill rect id = `id`; animate with tl.fromTo("#id",{scaleX:.04},{scaleX:1,svgOrigin:`${-w/2+10} 0`}) (the fill's parent has no translate: the meter's outer item does, so use svgOrigin in the OUTER's local x)
      meter: (id, w, h, label = "TRUST") => `<rect x="${-w / 2}" y="${-h / 2}" width="${w}" height="${h}" rx="${h / 2}" fill="#fff" stroke="${INK}" stroke-width="9"/><rect id="${id}" x="${-w / 2 + 10}" y="${-h / 2 + 10}" width="${w - 20}" height="${h - 20}" rx="${(h - 20) / 2}" fill="${C.go}"/><g transform="translate(0,${-h / 2 - 56})">${PROPS.label(label, 200)}</g>`,
      adCard: (label1 = "YOUR", label2 = "BUSINESS", w = 240, h = 340) => `<rect x="${-w / 2}" y="${-h / 2}" width="${w}" height="${h}" rx="26" fill="${C.greyLight}" stroke="${INK}" stroke-width="7"/><g transform="translate(0,-22) scale(0.9)">${PROPS.box()}</g>${T(label1, 0, h / 2 - 62, 28)}${T(label2, 0, h / 2 - 30, 28)}<rect x="${-w / 2 + 14}" y="${-h / 2 + 14}" width="56" height="34" rx="17" fill="${C.red}"/>${T("AD", -w / 2 + 42, -h / 2 + 39, 22, "#fff")}`,
      speech: () => `<path d="M -70 -60 H 70 Q 90 -60 90 -40 V 30 Q 90 50 70 50 H -10 L -50 84 L -44 50 H -70 Q -90 50 -90 30 V -40 Q -90 -60 -70 -60 Z" fill="#fff" stroke="${INK}" stroke-width="6" stroke-linejoin="round"/><circle cx="-28" cy="-5" r="8" fill="${INK}"/><circle cx="0" cy="-5" r="8" fill="${INK}"/><circle cx="28" cy="-5" r="8" fill="${INK}"/>`,
      // game-show paddle: text on a coloured board, stick below (origin = board centre)
      paddle: (txt, col) => `<rect x="-14" y="60" width="28" height="260" rx="8" fill="${C.greyDark}" stroke="${INK}" stroke-width="7"/><rect x="-170" y="-130" width="340" height="210" rx="30" fill="${col}" stroke="${INK}" stroke-width="10"/>${T(txt, 0, 38, txt.length > 3 ? 100 : 150, "#fff")}`,
      desk: (x, y) => `<rect x="${x - 170}" y="${y}" width="340" height="30" rx="8" fill="${C.greyDark}" stroke="${INK}" stroke-width="7"/><rect x="${x - 150}" y="${y + 30}" width="26" height="300" fill="${INK}"/><rect x="${x + 124}" y="${y + 30}" width="26" height="300" fill="${INK}"/><rect x="${x - 60}" y="${y - 60}" width="120" height="60" rx="8" fill="#fff" stroke="${INK}" stroke-width="6"/>`,
      // brick wall (brand red) from x0..x0+480, y 360..1800, plus a dark floor band
      wall: (x0 = 620) => { const b = []; for (let r = 0; r < 14; r++) for (let c = 0; c < 5; c++) b.push(`<rect x="${x0 + 20 + c * 100 - (r % 2) * 50}" y="${380 + r * 100}" width="96" height="48" rx="4" fill="${C.red}" stroke="${INK}" stroke-width="5"/>`); return `<rect x="${x0}" y="360" width="480" height="1440" fill="${C.redDark}"/>${b.join("")}<rect x="-20" y="1790" width="1120" height="200" fill="${INK}"/>`; },
      // Chat bubble used for the CTA "TRUST" bubble: scale ~3.2, id dots + trustTxt are animated by the caller
      ctaBubble: (word = "TRUST") => `<path d="M -70 -60 H 70 Q 90 -60 90 -40 V 30 Q 90 50 70 50 H -10 L -50 84 L -44 50 H -70 Q -90 50 -90 30 V -40 Q -90 -60 -70 -60 Z" fill="#fff" stroke="${INK}" stroke-width="3.2" stroke-linejoin="round"/><g id="dots"><circle cx="-28" cy="-5" r="7" fill="${INK}"/><circle cx="0" cy="-5" r="7" fill="${INK}"/><circle cx="28" cy="-5" r="7" fill="${INK}"/></g><g id="trustTxt" opacity="0">${T(word, 0, 12, 32, C.red)}</g>`,
      // End-of-ad badge: circle with the creator's cut-out photos (ids mx-n / mx-s / mx-l; swap with tl.set opacity). photos = {n:[src,eyeY,noseX,glassesW],...}
      maxBadge: (photos, first = "l") => { const imgs = Object.entries(photos).map(([k, [src, eyeY, noseX, gw]]) => { const s = 340 / gw; return `<image id="mx-${k}" href="${src}" x="${(-noseX * s).toFixed(1)}" y="${(-95 - eyeY * s).toFixed(1)}" width="${(577 * s).toFixed(1)}" height="${(1280 * s).toFixed(1)}" preserveAspectRatio="none" opacity="${k === first ? 1 : 0}"/>`; }).join(""); return `<g id="mx-j"><circle r="318" fill="#fff"/><circle r="300" fill="${C.paper}"/><g clip-path="url(#mxClip)">${imgs}</g><circle r="300" fill="none" stroke="${INK}" stroke-width="12"/></g>`; },
    };

    // ---------------- sound design ----------------
    // Custom cartoon SFX (sfx2/*.wav + meta.js) and bundled library (sfx/*.mp3) sit UNDER the voice:
    // `duck` x volume wherever there is speech; full volume inside opts.silent windows (the silent gags).
    let fxN = 0;
    const duckLevel = opts.duck == null ? 0.7 : opts.duck;
    const silent = opts.silent || [];
    const fx = (src, dur, t, vol) => {
      const a = document.createElement("audio");
      a.id = "fx" + ++fxN; a.src = src;
      a.setAttribute("data-start", q(Math.max(0, t)).toFixed(3));
      a.setAttribute("data-duration", String(dur));
      a.setAttribute("data-track-index", String(30 + (fxN % 14)));
      const inSilence = silent.some(([a0, a1]) => t > a0 && t < a1);
      a.setAttribute("data-volume", String(+(vol * (inSilence ? 1 : duckLevel)).toFixed(3)));
      stage.appendChild(a);
    };
    const S2 = (name, t, vol = 0.5, dur) => fx("sfx2/" + name + ".wav", dur || (window.SFX2 || {})[name] || 1, t, vol);
    const SB = (file, dur, t, vol) => fx("sfx/" + file + ".mp3", dur, t, vol);

    // ---------------- captions (his words, verbatim). [start,end,text] ----------------
    const captions = (list) => K.captions("#caps", list);

    return { T, item, inner, popIn, rng, scenes, actor, actors, pose, talk, jitter, enterStep, popStep, burst, smoke, boilText, draw, fx, S2, SB, captions };
  };
})();
