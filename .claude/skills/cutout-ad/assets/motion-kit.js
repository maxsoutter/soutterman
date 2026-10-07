/* motion-kit.js — animation kit for HyperFrames.
 *
 * Load order: vendor/gsap.min.js, then vendor/motion-kit.js (plain script tags).
 *   const tl = gsap.timeline({ paused: true });
 *   const K = MotionKit(tl, { stage: "#stage", fps: 30 });
 *
 * Everything is deterministic (no random, no clocks). Sounds are <audio> clips appended to the
 * stage (HyperFrames mixes them); files live in public/sfx/.
 *
 * IMPORTANT GSAP-on-SVG rule (learned the hard way): pivots on nested SVG groups drift when a
 * tween starts from a scaled state. This kit patches tl.to / tl.fromTo so any tween that names
 * `svgOrigin` gets it pre-set ONCE at scale 1 before the tween is built. Name the pivot in the
 * element's own coordinates (e.g. "0 0" for a group drawn around its origin).
 * For things that both travel and spin, use a wrapper (moves with x/y) + inner (spins/squashes).
 */
(function () {
  const INK = "#111111";
  const C = {
    red: "#E3241B", redDark: "#B81A12", redSoft: "#EE4A40",
    ink: INK, white: "#FFFFFF", paper: "#EDEDEB", grey: "#A3A3A0", greyLight: "#D9D9D6", greyDark: "#3A3A3A",
    // meaning-only accents: use ONLY when the colour itself carries the point (sun, traffic light, money…)
    sun: "#FFC42E", go: "#2ED3A0", sky: "#8FB8FF",
  };
  const SFX = {
    pop: ["sfx/pop.mp3", 0.72], click: ["sfx/click-soft.mp3", 0.37], whoosh: ["sfx/whoosh-short.mp3", 0.57],
    zoom: ["sfx/whoosh.mp3", 0.57], thud: ["sfx/impact-bass-1.mp3", 0.9], sparkle: ["sfx/sparkle.mp3", 1.2],
    scribble: ["sfx/scribble-short.mp3", 0.3],
  };
  const NS = "http://www.w3.org/2000/svg";

  // ---------- people: simple geometric business-casual characters ----------
  const HEART = (x, y) => `<path transform="translate(${x},${y}) scale(0.55)" d="M 0 -10 C -8 -24 -30 -20 -28 -2 C -26 12 0 26 0 26 C 0 26 26 12 28 -2 C 30 -20 8 -24 0 -10 Z" fill="${C.red}" stroke="${INK}" stroke-width="7"/>`;
  const SPIRAL = (x, y) => `<path d="M ${x} ${y} m -13 0 a 13 13 0 1 1 13 13 a 9 9 0 1 1 -9 -9 a 5 5 0 1 1 5 5" fill="none" stroke="${INK}" stroke-width="4.5" stroke-linecap="round"/>`;
  // origin (0,0) = base of the neck. Head centre (0,-100) r78. Torso to y +310. Draw people at scale 0.9–3.
  const person = (id, o = {}) => {
    o = Object.assign({ skin: "#B5764A", hair: INK, shirt: C.red, style: "short" }, o);
    const sw = 'stroke-width="7" stroke-linejoin="round"';
    const hair = {
      bun: `<path d="M -80 -96 Q -84 -192 0 -190 Q 84 -192 80 -96 Q 58 -150 0 -152 Q -58 -150 -80 -96 Z" fill="${o.hair}" stroke="${INK}" ${sw}/><circle cx="0" cy="-200" r="34" fill="${o.hair}" stroke="${INK}" stroke-width="7"/>`,
      short: `<path d="M -80 -92 Q -88 -196 0 -192 Q 88 -196 80 -92 Q 66 -140 30 -146 Q 0 -128 -40 -148 Q -70 -136 -80 -92 Z" fill="${o.hair}" stroke="${INK}" ${sw}/>`,
      afro: `<circle cx="0" cy="-118" r="112" fill="${o.hair}" stroke="${INK}" stroke-width="7"/>`,
      long: `<path d="M -92 -100 Q -110 30 -78 70 L 78 70 Q 110 30 92 -100 Q 90 -196 0 -194 Q -90 -196 -92 -100 Z" fill="${o.hair}" stroke="${INK}" ${sw}/>`,
      bald: ``,
    };
    const back = o.style === "afro" || o.style === "long" ? hair[o.style] : "";
    const front = o.style === "afro" || o.style === "bald" ? "" : o.style === "long" ? `<path d="M -80 -96 Q -84 -186 0 -186 Q 84 -186 80 -96 Q 40 -150 -20 -140 Q -60 -132 -80 -96 Z" fill="${o.hair}"/>` : hair[o.style];
    const beard = o.beard ? `<path d="M -66 -84 Q -60 -10 0 -6 Q 60 -10 66 -84 Q 40 -40 0 -44 Q -40 -40 -66 -84 Z" fill="${o.beard}" stroke="${INK}" stroke-width="5" stroke-linejoin="round"/>` : "";
    const glasses = o.glasses ? `<g fill="none" stroke="${INK}" stroke-width="5"><rect x="-58" y="-116" width="46" height="34" rx="10"/><rect x="12" y="-116" width="46" height="34" rx="10"/><path d="M -12 -100 H 12"/></g>` : "";
    const tie = o.tie ? `<path d="M -14 4 L 14 4 L 8 30 L 18 150 L 0 172 L -18 150 L -8 30 Z" fill="${o.tie}" stroke="${INK}" stroke-width="5" stroke-linejoin="round"/>` : "";
    return `<g id="${id}">
      <rect x="-24" y="-40" width="48" height="52" fill="${o.skin}" stroke="${INK}" stroke-width="7"/>
      <path d="M -104 0 Q -122 150 -112 310 L 112 310 Q 122 150 104 0 Q 0 -36 -104 0 Z" fill="${o.shirt}" stroke="${INK}" ${sw}/>${tie}
      <g id="${id}-head">
        ${back}
        <circle id="${id}-face" cx="0" cy="-100" r="78" fill="${o.skin}" stroke="${INK}" stroke-width="7"/>
        ${front}${beard}
        <circle cx="-46" cy="-72" r="13" fill="#FF8A7F" opacity="0.6"/><circle cx="46" cy="-72" r="13" fill="#FF8A7F" opacity="0.6"/>
        <g id="${id}-eN"><ellipse cx="-28" cy="-98" rx="9" ry="13" fill="${INK}"/><ellipse cx="28" cy="-98" rx="9" ry="13" fill="${INK}"/></g>
        <g id="${id}-eU" opacity="0"><ellipse cx="-24" cy="-112" rx="9" ry="13" fill="${INK}"/><ellipse cx="32" cy="-112" rx="9" ry="13" fill="${INK}"/></g>
        <g id="${id}-eL" opacity="0"><ellipse cx="-38" cy="-98" rx="9" ry="13" fill="${INK}"/><ellipse cx="18" cy="-98" rx="9" ry="13" fill="${INK}"/></g>
        <g id="${id}-eR" opacity="0"><ellipse cx="-18" cy="-98" rx="9" ry="13" fill="${INK}"/><ellipse cx="38" cy="-98" rx="9" ry="13" fill="${INK}"/></g>
        <g id="${id}-eH" opacity="0">${HEART(-28, -100)}${HEART(28, -100)}</g>
        <g id="${id}-eS" opacity="0">${SPIRAL(-28, -100)}${SPIRAL(28, -100)}</g>
        <g id="${id}-eD" opacity="0"><path d="M -42 -98 Q -28 -86 -14 -98 M 14 -98 Q 28 -86 42 -98" fill="none" stroke="${INK}" stroke-width="6" stroke-linecap="round"/></g>
        <g id="${id}-eW" opacity="0"><circle cx="-28" cy="-98" r="17" fill="#fff" stroke="${INK}" stroke-width="5"/><circle cx="28" cy="-98" r="17" fill="#fff" stroke="${INK}" stroke-width="5"/><circle cx="-28" cy="-98" r="7" fill="${INK}"/><circle cx="28" cy="-98" r="7" fill="${INK}"/></g>
        ${glasses}
        <g id="${id}-bA" opacity="0"><path d="M -48 -132 L -12 -118 M 48 -132 L 12 -118" stroke="${INK}" stroke-width="8" stroke-linecap="round"/></g>
        <g id="${id}-bW" opacity="0"><path d="M -48 -122 L -12 -134 M 48 -122 L 12 -134" stroke="${INK}" stroke-width="8" stroke-linecap="round"/></g>
        <g id="${id}-mN"><path d="M -20 -62 Q 0 -48 20 -62" fill="none" stroke="${INK}" stroke-width="6" stroke-linecap="round"/></g>
        <g id="${id}-mO" opacity="0"><ellipse cx="0" cy="-58" rx="13" ry="16" fill="${INK}"/></g>
        <g id="${id}-mF" opacity="0"><path d="M -20 -52 Q 0 -66 20 -52" fill="none" stroke="${INK}" stroke-width="6" stroke-linecap="round"/></g>
        <g id="${id}-mS" opacity="0"><path d="M -26 -66 Q 0 -36 26 -66 Z" fill="${INK}"/></g>
        <g id="${id}-mL" opacity="0"><path d="M -22 -58 H 22" stroke="${INK}" stroke-width="6" stroke-linecap="round"/></g>
      </g></g>`;
  };
  // eyes: eN normal, eU up, eL/eR look left/right, eH hearts, eS dizzy, eD content (closed), eW wide/shocked
  // mouths: mN smile, mO open "oh", mF frown, mS big grin, mL flat. brows: "angry" | "worried" | false
  const EYES = ["eN", "eU", "eL", "eR", "eH", "eS", "eD", "eW"], MOUTHS = ["mN", "mO", "mF", "mS", "mL"];

  // ---------- the AI robot (white/black with red antenna). origin = centre of the chest ----------
  const robot = (id) => `<g id="${id}">
      <line x1="0" y1="-200" x2="0" y2="-248" stroke="${INK}" stroke-width="8" stroke-linecap="round"/>
      <circle id="${id}-ball" cx="0" cy="-262" r="18" fill="${C.red}" stroke="${INK}" stroke-width="6"/>
      <rect x="-120" y="-200" width="240" height="170" rx="46" fill="#fff" stroke="${INK}" stroke-width="8"/>
      <rect x="-88" y="-172" width="176" height="114" rx="34" fill="${INK}"/>
      <g id="${id}-eyes"><circle cx="-38" cy="-115" r="16" fill="#fff"/><circle cx="38" cy="-115" r="16" fill="#fff"/></g>
      <rect x="-88" y="-14" width="176" height="150" rx="40" fill="#fff" stroke="${INK}" stroke-width="8"/>
      <rect x="-46" y="20" width="92" height="62" rx="16" fill="${C.red}"/>
      <text x="0" y="66" text-anchor="middle" style="font-family:Montserrat,sans-serif;font-weight:900;font-size:40px" fill="#fff">AI</text>
      <g id="${id}-arm"><path d="M 84 20 Q 150 0 180 -40" fill="none" stroke="${INK}" stroke-width="16" stroke-linecap="round"/><path d="M 180 -40 L 220 -96" stroke="${INK}" stroke-width="8" stroke-linecap="round"/><path d="M 226 -130 l 9 20 21 3 -15 15 4 21 -19 -10 -19 10 4 -21 -15 -15 21 -3 Z" fill="${C.sun}" stroke="${INK}" stroke-width="5" stroke-linejoin="round"/></g>
      <path d="M -84 20 Q -140 60 -150 120" fill="none" stroke="${INK}" stroke-width="16" stroke-linecap="round"/>
    </g>`;

  // ---------- small props (origin = centre) ----------
  const PROPS = {
    heart: () => `<path d="M 0 -10 C -8 -24 -30 -20 -28 -2 C -26 12 0 26 0 26 C 0 26 26 12 28 -2 C 30 -20 8 -24 0 -10 Z" fill="${C.red}" stroke="${INK}" stroke-width="5" stroke-linejoin="round"/>`,
    lips: () => `<path d="M -170 0 C -120 -62 -62 -80 -20 -44 Q 0 -60 20 -44 C 62 -80 120 -62 170 0 C 90 12 -90 12 -170 0 Z" fill="${C.red}" stroke="${INK}" stroke-width="8" stroke-linejoin="round"/><path d="M -170 0 C -90 16 90 16 170 0 C 124 84 -124 84 -170 0 Z" fill="${C.redDark}" stroke="${INK}" stroke-width="8" stroke-linejoin="round"/><ellipse cx="-50" cy="40" rx="34" ry="11" fill="#fff" opacity="0.55"/>`,
    message: () => `<path d="M -70 -60 H 70 Q 90 -60 90 -40 V 30 Q 90 50 70 50 H -10 L -50 84 L -44 50 H -70 Q -90 50 -90 30 V -40 Q -90 -60 -70 -60 Z" fill="#fff" stroke="${INK}" stroke-width="7" stroke-linejoin="round"/><path d="M -56 -24 H 56 M -56 6 H 30" stroke="${INK}" stroke-width="8" stroke-linecap="round"/>`,
    box: () => `<rect x="-80" y="-60" width="160" height="130" rx="10" fill="${C.greyLight}" stroke="${INK}" stroke-width="7"/><rect x="-18" y="-60" width="36" height="130" fill="${C.red}" stroke="${INK}" stroke-width="5"/><path d="M -80 -20 H 80" stroke="${INK}" stroke-width="5"/>`,
    bell: () => `<path d="M -70 40 Q -70 -50 0 -52 Q 70 -50 70 40 Z" fill="${C.grey}" stroke="${INK}" stroke-width="7" stroke-linejoin="round"/><rect x="-90" y="40" width="180" height="24" rx="10" fill="#fff" stroke="${INK}" stroke-width="7"/><circle cx="0" cy="-64" r="12" fill="${INK}"/>`,
    tag: () => `<path d="M -80 -50 H 40 L 90 0 L 40 50 H -80 Z" fill="${C.red}" stroke="${INK}" stroke-width="7" stroke-linejoin="round"/><circle cx="-50" cy="0" r="13" fill="#fff" stroke="${INK}" stroke-width="5"/><text x="12" y="20" text-anchor="middle" style="font-family:Montserrat,sans-serif;font-weight:900;font-size:54px" fill="#fff">%</text>`,
    // phone frame 400x780 centred; screen content goes on top at (−176..176, −364..364)
    phone: () => `<rect x="-200" y="-390" width="400" height="780" rx="60" fill="${INK}"/><rect x="-176" y="-364" width="352" height="728" rx="40" fill="#fff"/><rect x="-60" y="-346" width="120" height="26" rx="13" fill="${INK}"/>`,
    billboard: () => `<rect x="-240" y="210" width="26" height="690" fill="${INK}"/><rect x="214" y="210" width="26" height="690" fill="${INK}"/><rect x="-360" y="-230" width="720" height="460" rx="20" fill="#fff" stroke="${INK}" stroke-width="9"/>`,
    card: (label, fill = "#fff", text = INK) => `<rect x="-130" y="-180" width="260" height="340" rx="34" fill="${fill}" stroke="${INK}" stroke-width="9"/><text x="0" y="70" text-anchor="middle" style="font-family:Montserrat,sans-serif;font-weight:900;font-size:210px" fill="${text}">${label}</text>`,
    // thought bubble centred on (0,0), ~760x560, with tail dots toward (+60,+400)
    thought: () => {
      const cs = [[-180, -40, 170], [0, -130, 190], [180, -40, 170], [-90, 100, 160], [100, 100, 160]];
      return `<g fill="#fff" stroke="${INK}" stroke-width="9">${cs.map(([x, y, r]) => `<circle cx="${x}" cy="${y}" r="${r}"/>`).join("")}<circle cx="50" cy="330" r="40"/><circle cx="80" cy="410" r="24"/></g><g fill="#fff">${cs.map(([x, y, r]) => `<circle cx="${x}" cy="${y}" r="${r - 5}"/>`).join("")}</g>`;
    },
    label: (text, w = 190) => `<rect x="${-w / 2}" y="-26" width="${w}" height="52" rx="26" fill="${INK}"/><text x="0" y="10" text-anchor="middle" style="font-family:Montserrat,sans-serif;font-weight:800;font-size:30px;letter-spacing:.08em" fill="#fff">${text}</text>`,
  };

  window.MotionKit = function (tl, opts = {}) {
    const FPS = opts.fps || 30;
    const q = (t) => Math.round(t * FPS) / FPS;
    const $ = (s) => document.querySelector(s);
    const stage = $(opts.stage || "#stage");

    // --- pivot fix: pre-set svgOrigin once, at scale 1 ---
    const presetOrigin = (target, ...vs) => vs.forEach((v) => {
      if (v && v.svgOrigin) {
        gsap.utils.toArray(target).forEach((el) => { if (!el.__so) { gsap.set(el, { svgOrigin: v.svgOrigin }); el.__so = 1; } });
        delete v.svgOrigin;
      }
    });
    const _to = tl.to.bind(tl), _fromTo = tl.fromTo.bind(tl);
    tl.to = (t, v, p) => { presetOrigin(t, v); return _to(t, v, p); };
    tl.fromTo = (t, a, b, p) => { presetOrigin(t, a, b); return _fromTo(t, a, b, p); };

    let n = 0;
    const sfx = (k, t, vol = 0.3) => {
      const [src, dur] = SFX[k];
      const a = document.createElement("audio");
      a.id = "sfx-" + ++n; a.src = src;
      a.setAttribute("data-start", q(Math.max(0, t)).toFixed(3));
      a.setAttribute("data-duration", String(dur));
      a.setAttribute("data-track-index", String(20 + (n % 6)));
      a.setAttribute("data-volume", String(vol));
      stage.appendChild(a);
    };
    // insert SVG markup into a parent <g>; returns the wrapper <g> (use it to place/move)
    const svgEl = (parent, html, transform) => {
      const g = document.createElementNS(NS, "g");
      g.innerHTML = html;
      if (transform) g.setAttribute("transform", transform);
      (typeof parent === "string" ? $(parent) : parent).appendChild(g);
      return g;
    };

    const K = {
      C, q, $, sfx, svgEl, person, robot, PROPS,
      // ---- character acting ----
      expr: (id, eye, mouth, t, brows = false) => {
        if (eye) EYES.forEach((e) => tl.set(`#${id}-${e}`, { opacity: e === eye ? 1 : 0 }, q(t)));
        if (mouth) MOUTHS.forEach((m) => tl.set(`#${id}-${m}`, { opacity: m === mouth ? 1 : 0 }, q(t)));
        tl.set(`#${id}-bA`, { opacity: brows === "angry" || brows === true ? 1 : 0 }, q(t));
        tl.set(`#${id}-bW`, { opacity: brows === "worried" ? 1 : 0 }, q(t));
      },
      blink: (id, t) => tl.fromTo(`#${id}-eN`, { scaleY: 1 }, { scaleY: 0.1, duration: 0.06, yoyo: true, repeat: 1, svgOrigin: "0 -98", immediateRender: false }, q(t)),
      headTurn: (id, deg, t, d = 0.3) => tl.to(`#${id}-head`, { rotation: deg, duration: d, ease: "back.out(2)", svgOrigin: "0 -30" }, q(t)),
      shakeHead: (id, t) => tl.to(`#${id}-head`, { rotation: -6, duration: 0.18, yoyo: true, repeat: 3, ease: "sine.inOut", svgOrigin: "0 -30" }, q(t)),
      flush: (id, t, color = "#E25C46") => tl.to(`#${id}-face`, { fill: color, duration: 0.35 }, q(t)),
      // gentle sway so nobody is ever frozen (origin = feet of a person drawn by person())
      idle: (sel, t0, t1, amp = 1.2, per = 1.4, org = "0 310") => {
        const reps = Math.max(1, Math.floor((t1 - t0) / per));
        tl.fromTo(sel, { rotation: -amp }, { rotation: amp, duration: per, yoyo: true, repeat: reps, ease: "sine.inOut", svgOrigin: org, immediateRender: false }, q(t0));
      },
      // ---- entrances (settle fast, then hold) ----
      riseIn: (sel, t, dy = 300, d = 0.5) => tl.fromTo(sel, { y: dy }, { y: 0, duration: d, ease: "back.out(1.5)" }, q(t)),
      popIn: (sel, t, org = "0 0", sound = true) => {
        if (sound) sfx("pop", t, 0.22);
        tl.fromTo(sel, { opacity: 0, scale: 0.3 }, { opacity: 1, scale: 1, duration: 0.45, ease: "back.out(2.2)", svgOrigin: org }, q(t));
      },
      // bounce down into place (no squash: squash-from-scaled breaks pivots on nested SVG)
      dropIn: (sel, t, dy = -1300) => { tl.fromTo(sel, { y: dy }, { y: 0, duration: 0.7, ease: "bounce.out" }, q(t)); sfx("pop", t + 0.35, 0.22); },
      // pucker/squash a group drawn around its origin (e.g. lips) and spring back
      squash: (sel, t, sx = 0.62, sy = 1.22) => {
        tl.to(sel, { scaleX: sx, scaleY: sy, duration: 0.2, ease: "power2.out", svgOrigin: "0 0" }, q(t));
        tl.to(sel, { scaleX: 1.08, scaleY: 0.92, duration: 0.12, ease: "power2.out" }, q(t + 0.37));
        tl.to(sel, { scaleX: 1, scaleY: 1, duration: 0.4, ease: "elastic.out(1, 0.4)" }, q(t + 0.49));
      },
      // float little hearts (or any symbol id) up from a point
      floatUp: (parent, x, y, t, count = 3, html = PROPS.heart()) => {
        for (let i = 0; i < count; i++) {
          const g = svgEl(parent, html);
          const dx = [-80, 60, 0, -40, 90][i % 5];
          gsap.set(g, { x, y, opacity: 0 });
          tl.fromTo(g, { x, y, scale: 0.3, opacity: 1 }, { x: x + dx * 3, y: y - 300, scale: 1.4, opacity: 0, rotation: dx / 3, duration: 1.0, ease: "power1.out", immediateRender: false }, q(t + i * 0.08));
        }
      },
      // something flies at a target, bonks off and tumbles away (wrapper moves, inner spins)
      bonk: (wrap, side, startX, hitX, y, t, onHit) => {
        const inner = wrap.firstElementChild;
        gsap.set(wrap, { x: startX, y, opacity: 0 });
        tl.set(wrap, { opacity: 1 }, q(t));
        tl.fromTo(wrap, { x: startX }, { x: hitX, duration: 0.32, ease: "power2.in", immediateRender: false }, q(t));
        tl.fromTo(inner, { rotation: side * -20 }, { rotation: 0, duration: 0.32, ease: "power2.in", transformOrigin: "50% 50%", immediateRender: false }, q(t));
        tl.to(inner, { scaleX: 0.8, scaleY: 1.15, duration: 0.07, yoyo: true, repeat: 1, transformOrigin: "50% 50%" }, q(t + 0.32));
        if (onHit) tl.to(onHit, { x: side * -14, duration: 0.07, yoyo: true, repeat: 1 }, q(t + 0.32));
        tl.to(wrap, { x: hitX + side * -260, y: 2200, duration: 1.0, ease: "power2.in" }, q(t + 0.46));
        tl.to(inner, { rotation: side * -160, duration: 1.0, ease: "power1.in", transformOrigin: "50% 50%" }, q(t + 0.46));
        sfx("pop", t, 0.18); sfx("thud", t + 0.32, 0.16);
      },
      // ---- camera ----
      // slide a wide strip of scenes sideways (scenes laid out at x = 0, 1080, 2160…)
      whip: (strip, toX, t, d = 0.42) => { sfx("whoosh", t - 0.02, 0.4); tl.to(strip, { x: toX, duration: d, ease: "power3.inOut" }, q(t)); },
      // start zoomed into a detail, then pull back to reveal the scene (origin in the cam's own coords)
      zoomTo: (cam, scale, org, t, d = 0.7) => { sfx("zoom", t - 0.02, 0.3); tl.to(cam, { scale, duration: d, ease: "power3.inOut", svgOrigin: org }, q(t)); },
      startZoomed: (cam, scale, org) => gsap.set(cam, { scale, svgOrigin: org }),
      // hard cut between scene groups (all start hidden except the first)
      cut: (from, to, t) => { tl.set(from, { opacity: 0 }, q(t)); tl.set(to, { opacity: 1 }, q(t)); sfx("whoosh", t - 0.06, 0.28); },
      // ---- face ↔ animation ----
      irisOpen: (sel, t, at = "50% 45%") => { sfx("zoom", t - 0.05, 0.4); tl.fromTo(sel, { clipPath: `circle(0% at ${at})` }, { clipPath: `circle(75% at ${at})`, duration: 0.5, ease: "power3.inOut" }, q(t)); },
      irisClose: (sel, t, at = "50% 45%") => { sfx("zoom", t - 0.04, 0.35); tl.to(sel, { clipPath: `circle(0% at ${at})`, duration: 0.45, ease: "power3.inOut" }, q(t)); },
      // animation big, face small: show the face window (a .pip with its own <video>)
      pipIn: (sel, t, left, top) => { gsap.set(sel, { left, top }); sfx("pop", t, 0.2); tl.fromTo(sel, { opacity: 0, scale: 0.5 }, { opacity: 1, scale: 1, duration: 0.4, ease: "back.out(1.8)" }, q(t)); },
      pipMove: (sel, t, left, top) => tl.set(sel, { left, top }, q(t)),
      pipOut: (sel, t) => tl.to(sel, { opacity: 0, scale: 0.6, duration: 0.2, ease: "power2.in" }, q(t)),
      // face big, animation small: shrink the whole animated world into a framed corner card, and back
      toCard: (anim, frame, t, origin = "94% 80%", scale = 0.3) => {
        sfx("whoosh", t - 0.02, 0.25);
        tl.to(anim, { scale, borderRadius: 110, duration: 0.45, ease: "power3.inOut", transformOrigin: origin }, q(t));
        tl.to(frame, { opacity: 1, duration: 0.2 }, q(t + 0.05));
      },
      fromCard: (anim, frame, t, origin = "94% 80%") => {
        sfx("whoosh", t - 0.02, 0.25);
        tl.to(anim, { scale: 1, borderRadius: 0, duration: 0.45, ease: "power3.inOut", transformOrigin: origin }, q(t));
        tl.to(frame, { opacity: 0, duration: 0.2 }, q(t + 0.22));
      },
      // ---- captions: pills over animation, outlined text over his face ----
      captions: (container, list) => list.forEach(([s, e, txt, face], i) => {
        const el = document.createElement("div");
        el.className = "cap" + (face ? " onface" : "");
        el.id = "cap" + i;
        el.textContent = txt;
        $(container).appendChild(el);
        tl.fromTo(el, { opacity: 0, y: 12 }, { opacity: 1, y: 0, duration: 0.18, ease: "power2.out" }, q(s));
        tl.to(el, { opacity: 0, duration: 0.1 }, q(e));
      }),
    };
    return K;
  };
})();
