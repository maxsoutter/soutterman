import java.awt.*;
import java.awt.font.*;
import java.awt.geom.*;
import java.awt.image.*;
import java.io.*;
import java.util.*;
import javax.imageio.ImageIO;

/**
 * JUNGLE DASH! — a 20 second cartoon, drawn and scored entirely in Java.
 *
 * A tiny fluffball named Pip flees a lion through the jungle, cannonballs into a
 * river... and it turns out the lion REALLY hates water.
 *
 * Every frame is painted with Java2D and every sound (music + SFX) is synthesized
 * sample-by-sample. Frames are piped to ffmpeg as raw video and muxed with the WAV.
 *
 * Usage: java JungleDash <ffmpeg-binary> <out.mp4> [previewDir]
 */
public class JungleDash {
    static final int W = 1280, H = 720, FPS = 30;
    static final double DUR = 20.0;
    static final int GROUND = 580;        // top of the near jungle floor
    static final int SURF = 598;          // river surface
    static final double EDGE = 660;       // where the river starts once the camera settles
    static final double RUN_SPEED = 620;
    static final double CAM_SLOW = 3.7, CAM_SLOW_LEN = 1.2;
    static final double CAM_END = RUN_SPEED * CAM_SLOW + RUN_SPEED * CAM_SLOW_LEN / 2;
    static final double RIVER_WORLD = CAM_END + EDGE;
    static final double SPLASH_T = 5.8, SPLASH_X = 930;

    static final String FONT = Font.SANS_SERIF;

    // palette
    static final Color OUT = rgb(0x3B2412);
    static final Color LBODY = rgb(0xF2B447), LMANE = rgb(0xC0561B), LMANE2 = rgb(0xE07A2A), LMUZ = rgb(0xFCE6B8);
    static final Color PB = rgb(0xFF9F43), PD = rgb(0xC9651A), PBEL = rgb(0xFFD7A3);
    static final Color WATER_TOP = rgb(0x3DB5E8), WATER_BOT = rgb(0x1E6FB0);

    // ------------------------------------------------------------------ helpers
    static Color rgb(int hex) { return new Color(hex); }
    static Color alpha(Color c, double a) { return new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) clamp(a * 255, 0, 255)); }
    static Color mix(Color a, Color b, double t) {
        t = clamp(t, 0, 1);
        return new Color((int) lerp(a.getRed(), b.getRed(), t), (int) lerp(a.getGreen(), b.getGreen(), t), (int) lerp(a.getBlue(), b.getBlue(), t));
    }
    static double clamp(double v, double a, double b) { return Math.max(a, Math.min(b, v)); }
    static double lerp(double a, double b, double t) { return a + (b - a) * t; }
    static double seg(double t, double a, double b) { return clamp((t - a) / (b - a), 0, 1); }
    static double easeOut(double x) { return 1 - Math.pow(1 - x, 3); }
    static double easeIn(double x) { return x * x; }
    static double easeInOut(double x) { return x < .5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2; }
    static double easeOutBack(double x) { double c1 = 1.70158, c3 = c1 + 1; return 1 + c3 * Math.pow(x - 1, 3) + c1 * Math.pow(x - 1, 2); }
    static double frac(double x) { return x - Math.floor(x); }
    static boolean in(double t, double a, double b) { return t >= a && t < b; }
    static double hash(double k) { return frac(Math.sin(k * 127.1 + 311.7) * 43758.5453); }
    static Ellipse2D circle(double x, double y, double r) { return new Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r); }
    static Ellipse2D ell(double cx, double cy, double w, double h) { return new Ellipse2D.Double(cx - w / 2, cy - h / 2, w, h); }

    static void fillOut(Graphics2D g, Shape s, Color c) { fillOut(g, s, c, 3); }
    static void fillOut(Graphics2D g, Shape s, Color c, float w) {
        g.setColor(c); g.fill(s);
        g.setColor(OUT); g.setStroke(new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); g.draw(s);
    }
    static void strokeOut(Graphics2D g, Shape s, Color c, float w) {
        g.setStroke(new BasicStroke(w + 5, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); g.setColor(OUT); g.draw(s);
        g.setStroke(new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); g.setColor(c); g.draw(s);
    }
    static void line(Graphics2D g, double x1, double y1, double x2, double y2, Color c, float w) {
        g.setColor(c); g.setStroke(new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(x1, y1, x2, y2));
    }

    // ------------------------------------------------------------------ camera
    static double cam(double t) {
        if (t < CAM_SLOW) return RUN_SPEED * t;
        double p = seg(t, CAM_SLOW, CAM_SLOW + CAM_SLOW_LEN);
        return RUN_SPEED * CAM_SLOW + RUN_SPEED * CAM_SLOW_LEN * (p - p * p / 2);
    }

    // ------------------------------------------------------------------ background
    static void drawBackground(Graphics2D g, double cam, double t) {
        g.setPaint(new GradientPaint(0, 0, rgb(0x7ED6F2), 0, 470, rgb(0xE4F9C8)));
        g.fillRect(0, 0, W, H);

        // sun rays
        for (int i = 0; i < 5; i++) {
            double x = 150 + i * 250 - (cam * 0.05) % 250;
            Path2D ray = new Path2D.Double();
            ray.moveTo(x, 0); ray.lineTo(x + 70, 0); ray.lineTo(x - 40 + 160, 470); ray.lineTo(x - 140, 470); ray.closePath();
            g.setColor(new Color(255, 255, 220, 34)); g.fill(ray);
        }

        // far hills
        tile(g, cam * 0.15, 300, (x, k) -> {
            double h = 130 + 50 * Math.sin(k * 1.7);
            g.setColor(rgb(0x86C98F)); g.fill(new Ellipse2D.Double(x - 60, 470 - h, 420, 2 * h));
        });

        // mid trees
        tile(g, cam * 0.45, 230, (x, k) -> {
            double tx = x + 40 * (hash(k) - .5);
            double top = 140 + 60 * hash(k + 3);
            g.setColor(rgb(0x7A5230)); g.fill(new Rectangle2D.Double(tx - 17, top, 34, 480 - top));
            g.setColor(rgb(0x5E3E22)); g.fill(new Rectangle2D.Double(tx + 4, top, 8, 480 - top));
            Color[] cs = {rgb(0x2F8F4E), rgb(0x3FAF5E), rgb(0x38A055)};
            for (int j = 0; j < 6; j++) {
                double a = j * 1.1 + k;
                g.setColor(cs[j % 3]);
                g.fill(circle(tx + Math.cos(a) * 55, top + Math.sin(a) * 30 - 10, 48 + 16 * hash(k * 7 + j)));
            }
        });

        // canopy ceiling
        tile(g, cam * 0.6, 150, (x, k) -> {
            g.setColor(rgb(0x2A7A43)); g.fill(circle(x, -30 + 20 * hash(k), 95));
            g.setColor(rgb(0x339150)); g.fill(circle(x + 60, -50 + 20 * hash(k + 1), 70));
        });

        // hanging vines
        tile(g, cam * 0.8, 260, (x, k) -> {
            double len = 150 + 110 * hash(k + 9);
            double sway = Math.sin(t * 1.5 + k) * 10;
            Path2D v = new Path2D.Double();
            v.moveTo(x, 0); v.quadTo(x + sway, len * 0.5, x + sway * 1.6, len);
            g.setStroke(new BasicStroke(5, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(rgb(0x2E7D32)); g.draw(v);
            for (int j = 1; j < 6; j++) {
                double f = j / 6.0;
                double lx = x + sway * 1.6 * f * f, ly = len * f;
                AffineTransform s = g.getTransform();
                g.translate(lx, ly); g.rotate(j % 2 == 0 ? 0.6 : -0.6);
                g.setColor(rgb(0x4CAF50)); g.fill(new Ellipse2D.Double(j % 2 == 0 ? 0 : -18, -5, 18, 10));
                g.setTransform(s);
            }
        });

        // distant jungle floor + bushes
        g.setPaint(new GradientPaint(0, 460, rgb(0x5AAE4E), 0, SURF, rgb(0x3F8A3C)));
        g.fillRect(0, 460, W, H - 460);
        tile(g, cam * 0.7, 180, (x, k) -> {
            double r = 34 + 22 * hash(k + 5);
            g.setColor(rgb(0x2F7F3A)); g.fill(circle(x, 500, r));
            g.setColor(rgb(0x3E9A48)); g.fill(circle(x + r * 0.7, 505, r * 0.8));
        });
    }

    interface TileFn { void draw(double x, int k); }
    static void tile(Graphics2D g, double scroll, double spacing, TileFn fn) {
        int first = (int) Math.floor(scroll / spacing) - 2;
        for (int k = first; k < first + (int) (W / spacing) + 5; k++) fn.draw(k * spacing - scroll, k);
    }

    static Path2D waterShape(double riverX, double t, double yOff) {
        Path2D p = new Path2D.Double();
        double x0 = Math.max(riverX, -10);
        p.moveTo(x0, H);
        for (double x = x0; x <= W + 10; x += 8)
            p.lineTo(x, SURF + yOff + 4 * Math.sin(x * 0.03 + t * 3) + 2 * Math.sin(x * 0.07 - t * 2));
        p.lineTo(W + 10, H); p.closePath();
        return p;
    }

    static void drawGroundAndWater(Graphics2D g, double cam, double t) {
        double riverX = RIVER_WORLD - cam;
        // near ground
        double gx = Math.min(W + 20, riverX);
        g.setPaint(new GradientPaint(0, GROUND, rgb(0x9C6B3C), 0, H, rgb(0x6E4523)));
        g.fill(new Rectangle2D.Double(-10, GROUND, gx + 10, H - GROUND));
        // pebbles
        tile(g, cam, 90, (x, k) -> {
            if (x > riverX - 30) return;
            g.setColor(rgb(0x7E5330)); g.fill(ell(x + 30 * hash(k), GROUND + 50 + 90 * hash(k + 2), 16, 9));
        });
        // grass fringe
        Path2D grass = new Path2D.Double();
        grass.moveTo(-10, GROUND + 14);
        int first = (int) Math.floor(cam / 14) - 1;
        for (int k = first; ; k++) {
            double x = k * 14 - cam;
            if (x > gx) break;
            double h = 10 + 14 * hash(k);
            grass.lineTo(x, GROUND + 8); grass.lineTo(x + 7, GROUND - h); grass.lineTo(x + 14, GROUND + 8);
        }
        grass.lineTo(gx, GROUND + 14); grass.closePath();
        g.setColor(rgb(0x5DB33A)); g.fill(grass);
        g.setColor(rgb(0x5DB33A)); g.fill(new Rectangle2D.Double(-10, GROUND + 4, gx + 10, 12));
        // flowers
        tile(g, cam, 170, (x, k) -> {
            double fx = x + 60 * hash(k + 4);
            if (fx > riverX - 30) return;
            Color c = k % 2 == 0 ? rgb(0xFF5C8A) : rgb(0xFFD23F);
            for (int j = 0; j < 5; j++) { double a = j * Math.PI * 2 / 5; g.setColor(c); g.fill(circle(fx + Math.cos(a) * 5, GROUND - 4 + Math.sin(a) * 5, 4)); }
            g.setColor(Color.WHITE); g.fill(circle(fx, GROUND - 4, 3));
        });

        if (riverX < W + 20) {
            // muddy bank lip
            Path2D bank = new Path2D.Double();
            bank.moveTo(riverX - 30, GROUND); bank.quadTo(riverX + 5, GROUND - 2, riverX + 14, SURF + 12);
            bank.lineTo(riverX + 30, H); bank.lineTo(riverX - 30, H); bank.closePath();
            g.setColor(rgb(0x6B4020)); g.fill(bank);
            g.setColor(rgb(0x5DB33A)); g.fill(new Ellipse2D.Double(riverX - 40, GROUND - 4, 40, 14));
            // water body
            Path2D w = waterShape(riverX, t, 0);
            g.setPaint(new GradientPaint(0, SURF, WATER_TOP, 0, H, WATER_BOT)); g.fill(w);
            g.setColor(new Color(255, 255, 255, 170)); g.setStroke(new BasicStroke(4, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int i = 0; i < 9; i++) {
                double hx = riverX + 40 + frac(i * 0.137 + t * 0.03) * (W - riverX), hy = SURF + 30 + (i * 37) % 90;
                if (hx > riverX + 20) g.draw(new Line2D.Double(hx, hy, hx + 26, hy));
            }
        }
    }

    static void drawWaterFront(Graphics2D g, double cam, double t) {
        double riverX = RIVER_WORLD - cam;
        if (riverX > W) return;
        Path2D w = waterShape(riverX, t, 0);
        g.setPaint(new GradientPaint(0, SURF, alpha(WATER_TOP, .72), 0, H, alpha(WATER_BOT, .9)));
        g.fill(w);
        g.setColor(new Color(255, 255, 255, 200)); g.setStroke(new BasicStroke(3));
        Path2D top = new Path2D.Double();
        double x0 = Math.max(riverX + 14, 0);
        top.moveTo(x0, SURF + 4 * Math.sin(x0 * 0.03 + t * 3) + 2 * Math.sin(x0 * 0.07 - t * 2));
        for (double x = x0; x <= W + 10; x += 8) top.lineTo(x, SURF + 4 * Math.sin(x * 0.03 + t * 3) + 2 * Math.sin(x * 0.07 - t * 2));
        g.draw(top);
    }

    static void drawForegroundLeaves(Graphics2D g, double cam, double t) {
        tile(g, cam * 1.3, 520, (x, k) -> {
            if (x + cam * 0 > (RIVER_WORLD - cam) - 80) return;
            AffineTransform s = g.getTransform();
            g.translate(x, H + 20); g.rotate(-0.5 - 0.4 * hash(k) + 0.05 * Math.sin(t * 2 + k));
            Path2D leaf = new Path2D.Double();
            leaf.moveTo(0, 0); leaf.quadTo(-50, -120, 0, -230); leaf.quadTo(50, -120, 0, 0);
            g.setColor(rgb(0x1F6B34)); g.fill(leaf);
            g.setColor(rgb(0x2C8A45)); g.setStroke(new BasicStroke(3)); g.draw(new Line2D.Double(0, 0, 0, -220));
            g.setTransform(s);
        });
    }

    // ------------------------------------------------------------------ lion
    static class Lion {
        boolean sit;
        double x, lift, lean, pivot = 0;
        double phase, legAmp;
        double[] legs;          // explicit angles: backFar, frontFar, backNear, frontNear
        boolean windmill;
        double mouth, frizz, green, shudder;
        boolean tongue, squint, angry, wide, drool, sneaky;
        double reach;           // front near leg reach (tiptoe)
        double rain;
    }

    static Lion lionAt(double t) {
        Lion L = new Lion();
        if (t < 6.2) {
            L.x = t < 4.0 ? 190 : 190 + 280 * seg(t, 4.0, 6.2);
            L.phase = t * 16; L.legAmp = .7;
            L.lift = Math.abs(Math.sin(t * 8)) * 10;
            boolean roar = in(t, 0.3, 1.3) || in(t, 2.9, 3.8);
            L.mouth = roar ? 0.85 + 0.15 * Math.sin(t * 40) : 0.3;
            L.angry = true; L.drool = !roar; L.lean = 0.04;
        } else if (t < 7.0) {                         // SKRRRT
            double p = seg(t, 6.2, 7.0);
            L.x = 470 + 90 * easeOut(p);
            L.legs = new double[]{-0.25, -0.65, -0.35, -0.8};
            L.lean = -0.18 * Math.sin(Math.PI * Math.min(1, p * 1.3));
            L.mouth = 0.6; L.wide = true;
        } else if (t < 8.2) {                         // teetering, arms windmilling
            L.x = 560; L.pivot = -55;
            L.lean = 0.12 + 0.12 * Math.sin((t - 7) * 9);
            L.windmill = true; L.wide = true; L.mouth = 0.5;
            L.legs = new double[]{0.05, 0, -0.05, 0};
        } else if (t < 8.6) {                         // phew
            double p = easeInOut(seg(t, 8.2, 8.5));
            L.x = lerp(560, 540, p); L.pivot = -55;
            L.lean = lerp(0.12, 0, p);
            L.legs = new double[]{0, 0, 0, 0};
            L.mouth = 0;
        } else if (t < 10.2) {                        // EWWW
            double p = seg(t, 8.6, 8.8);
            L.x = 540; L.legs = new double[]{0.1, -0.1, 0.05, -0.15};
            L.lean = -0.1 * p;
            L.green = seg(t, 8.6, 8.9) * (1 - seg(t, 9.9, 10.6));
            L.frizz = 1 - seg(t, 9.4, 10.2) * 0.6;
            L.shudder = t < 9.8 ? 1 : 0;
            L.tongue = true; L.squint = true; L.mouth = 0.35;
        } else if (t < 11.2) {                        // backing away
            double p = easeInOut(seg(t, 10.2, 11.2));
            L.x = lerp(540, 390, p);
            L.phase = -t * 10; L.legAmp = .35;
            L.green = 1 - seg(t, 9.9, 10.6);
            L.frizz = 0.4 * (1 - p);
            L.squint = true;
        } else if (t < 14.8) {                        // sulking
            L.sit = true; L.x = 390; L.rain = seg(t, 12.3, 12.8);
        } else if (t < 16.15) {                       // sneaking in for one toe
            double p = seg(t, 14.8, 15.8);
            L.x = lerp(390, 545, easeInOut(p)); L.pivot = -55;
            L.phase = t * 7; L.legAmp = p < 1 ? .3 : 0;
            double r = easeInOut(seg(t, 15.8, 16.1));
            L.reach = r; L.lean = 0.06 + 0.2 * r; L.sneaky = true;
        } else if (t < 16.9) {                        // NOPE!
            double p = seg(t, 16.15, 16.9);
            L.x = lerp(545, 420, easeOut(p));
            L.lift = 120 * Math.sin(Math.PI * p);
            L.frizz = 1; L.wide = true; L.mouth = 0.9;
            L.legs = new double[]{0.7, -0.7, 0.8, -0.8};
            L.lean = -0.2 * Math.sin(Math.PI * p);
        } else {
            L.sit = true; L.x = 420; L.rain = seg(t, 17.3, 17.8);
            L.frizz = 0.5 * (1 - seg(t, 16.9, 17.6));
        }
        return L;
    }

    static void mane(Graphics2D g, double cx, double cy, double t, double frizz, double rOut, double rIn) {
        for (int layer = 0; layer < 2; layer++) {
            int n = 20;
            double ro = layer == 0 ? rOut : rOut - 14, ri = layer == 0 ? rIn : rIn - 8;
            double off = layer * Math.PI / n;
            Path2D p = new Path2D.Double();
            for (int i = 0; i < 2 * n; i++) {
                double a = off + i * Math.PI / n;
                double r = (i % 2 == 0) ? ro + frizz * (22 + 12 * Math.sin(i * 7.3 + t * 37)) : ri;
                double px = cx + r * Math.cos(a), py = cy + r * Math.sin(a);
                if (i == 0) p.moveTo(px, py); else p.lineTo(px, py);
            }
            p.closePath();
            fillOut(g, p, layer == 0 ? LMANE : LMANE2);
        }
    }

    static void lionLeg(Graphics2D g, double hx, double hy, double ang, double len, Color c) {
        AffineTransform s = g.getTransform();
        g.translate(hx, hy); g.rotate(ang);
        fillOut(g, new RoundRectangle2D.Double(-13, -8, 26, len + 8, 22, 22), c);
        fillOut(g, new Ellipse2D.Double(-16, len - 12, 34, 20), c);
        g.setTransform(s);
    }

    static void drawLionSide(Graphics2D g, Lion L, double t) {
        AffineTransform s = g.getTransform();
        g.translate(L.x + L.shudder * Math.sin(t * 95) * 5, GROUND - L.lift);
        g.translate(L.pivot, 0); g.rotate(L.lean); g.translate(-L.pivot, 0);
        Color face = mix(LBODY, rgb(0xA6D46A), L.green);
        Color dark = rgb(0xD9982E);

        // tail
        double wag = Math.sin(t * 6) * 18;
        Path2D tail = new Path2D.Double();
        tail.moveTo(-88, -115); tail.quadTo(-150, -105, -150 + wag * 0.3, -165 + wag);
        strokeOut(g, tail, LBODY, 9);
        mane(g, -150 + wag * 0.3, -165 + wag, t, L.frizz * 0.4, 16, 10);

        double[] a = new double[4];
        if (L.legs != null) a = L.legs.clone();
        else {
            a[0] = Math.sin(L.phase + 0.5) * L.legAmp;
            a[1] = Math.sin(L.phase + Math.PI + 0.5) * L.legAmp;
            a[2] = Math.sin(L.phase) * L.legAmp;
            a[3] = Math.sin(L.phase + Math.PI) * L.legAmp;
        }
        a[3] -= L.reach * 1.1;
        double spin = t * 16;
        // far legs
        lionLeg(g, -62, -80, a[0], 78, dark);
        if (L.windmill) lionLeg(g, 58, -112, spin + Math.PI, 70, dark);
        else lionLeg(g, 52, -80, a[1], 78, dark);
        // body
        fillOut(g, new Ellipse2D.Double(-102, -152, 212, 102), LBODY);
        g.setColor(LMUZ); g.fill(new Ellipse2D.Double(-60, -100, 140, 42));
        // near legs
        lionLeg(g, -48, -80, a[2], 78, LBODY);
        if (L.windmill) lionLeg(g, 72, -112, spin, 70, LBODY);
        else lionLeg(g, 66, -80, a[3], 78, LBODY);

        // head
        double hx = 88, hy = -150;
        mane(g, hx, hy, t, L.frizz, 80, 62);
        fillOut(g, circle(hx - 30, hy - 42, 14), face);
        fillOut(g, circle(hx + 10, hy - 48, 14), face);
        g.setColor(rgb(0xF4A6A6)); g.fill(circle(hx - 30, hy - 42, 7)); g.fill(circle(hx + 10, hy - 48, 7));
        fillOut(g, circle(hx, hy, 46), face);
        fillOut(g, ell(hx + 28, hy + 16, 58, 40), mix(LMUZ, rgb(0xCDEB9A), L.green));
        // whisker dots
        g.setColor(OUT);
        for (int i = 0; i < 3; i++) g.fill(circle(hx + 18 + i * 7, hy + 16 + (i % 2) * 4, 1.8));
        // nose
        Path2D nose = new Path2D.Double();
        nose.moveTo(hx + 36, hy - 2); nose.lineTo(hx + 56, hy - 2); nose.lineTo(hx + 46, hy + 10); nose.closePath();
        fillOut(g, nose, rgb(0x5A3217));

        // eyes
        double e1x = hx + 2, e2x = hx + 26, ey = hy - 16;
        if (L.squint) {
            g.setColor(OUT); g.setStroke(new BasicStroke(4, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Path2D q = new Path2D.Double();
            q.moveTo(e1x - 9, ey - 7); q.lineTo(e1x + 6, ey); q.lineTo(e1x - 9, ey + 7);
            q.moveTo(e2x + 9, ey - 7); q.lineTo(e2x - 6, ey); q.lineTo(e2x + 9, ey + 7);
            g.draw(q);
        } else {
            double ew = L.wide ? 26 : 20, eh = L.wide ? 30 : 24;
            fillOut(g, ell(e1x, ey, ew, eh), Color.WHITE, 2.5f);
            fillOut(g, ell(e2x, ey - 1, ew * 0.9, eh * 0.95), Color.WHITE, 2.5f);
            double pr = L.wide ? 3.5 : 6;
            double px = L.sneaky ? 5 : 4, py = L.sneaky ? 6 : 1;
            g.setColor(OUT); g.fill(circle(e1x + px, ey + py, pr)); g.fill(circle(e2x + px, ey - 1 + py, pr));
            g.setColor(Color.WHITE); g.fill(circle(e1x + px + 2, ey + py - 2, 1.6)); g.fill(circle(e2x + px + 2, ey + py - 3, 1.6));
            if (L.angry) {
                line(g, e1x - 11, ey - 20, e1x + 9, ey - 12, OUT, 5);
                line(g, e2x - 8, ey - 13, e2x + 12, ey - 19, OUT, 5);
            } else if (L.wide) {
                line(g, e1x - 10, ey - 24, e1x + 8, ey - 28, OUT, 4);
                line(g, e2x - 8, ey - 28, e2x + 10, ey - 25, OUT, 4);
            }
        }

        // mouth
        double mx = hx + 34, my = hy + 30;
        if (L.mouth > 0.05) {
            double mh = 8 + 34 * L.mouth;
            Shape m = ell(mx, my + mh / 2 - 4, 40, mh);
            fillOut(g, m, rgb(0x7A1020));
            if (!L.tongue) {
                g.setColor(Color.WHITE);
                for (int i = 0; i < 4; i++) {
                    double tx = mx - 14 + i * 9;
                    Path2D tooth = new Path2D.Double();
                    tooth.moveTo(tx - 4, my - 3); tooth.lineTo(tx + 4, my - 3); tooth.lineTo(tx, my + 5); tooth.closePath();
                    g.fill(tooth);
                }
                g.setColor(rgb(0xE4576B)); g.fill(ell(mx + 2, my + mh - 8, 22, 9));
            }
        } else {
            Path2D smile = new Path2D.Double();
            smile.moveTo(mx - 14, my - 2); smile.quadTo(mx, my + 8, mx + 14, my - 2);
            g.setColor(OUT); g.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); g.draw(smile);
        }
        if (L.tongue) {
            double wob = Math.sin(t * 30) * 3;
            Shape tg = new RoundRectangle2D.Double(mx - 4 + wob, my + 2, 20, 36, 18, 18);
            fillOut(g, tg, rgb(0xF27A8C));
            line(g, mx + 6 + wob, my + 8, mx + 6 + wob, my + 26, rgb(0xC94A5E), 2);
        }
        if (L.drool) {
            double d = frac(t * 1.5);
            g.setColor(new Color(170, 225, 255, 230));
            g.fill(new RoundRectangle2D.Double(mx + 12, my + 6, 7, 10 + d * 28, 7, 7));
            g.fill(circle(mx + 15.5, my + 16 + d * 28, 5));
        }
        g.setTransform(s);
    }

    static void drawLionSit(Graphics2D g, Lion L, double t) {
        AffineTransform s = g.getTransform();
        g.translate(L.x, GROUND);
        double br = Math.sin(t * 2.2) * 2;

        Path2D tail = new Path2D.Double();
        tail.moveTo(40, -20); tail.quadTo(115, -10, 100, -70 + Math.sin(t * 3) * 6);
        strokeOut(g, tail, LBODY, 9);
        mane(g, 100, -72 + Math.sin(t * 3) * 6, t, 0, 16, 10);

        fillOut(g, new Ellipse2D.Double(-64, -176 + br, 128, 176 - br), LBODY);
        g.setColor(LMUZ); g.fill(new Ellipse2D.Double(-38, -132 + br, 76, 112));
        fillOut(g, new Ellipse2D.Double(-66, -26, 54, 28), LBODY);
        fillOut(g, new Ellipse2D.Double(12, -26, 54, 28), LBODY);
        for (int i = 0; i < 2; i++) { line(g, -48 + i * 10, -14, -48 + i * 10, -4, OUT, 2); line(g, 30 + i * 10, -14, 30 + i * 10, -4, OUT, 2); }

        double hx = 0, hy = -205 + br;
        mane(g, hx, hy, t, L.frizz, 76, 60);
        fillOut(g, circle(hx - 34, hy - 38, 14), LBODY);
        fillOut(g, circle(hx + 34, hy - 38, 14), LBODY);
        g.setColor(rgb(0xF4A6A6)); g.fill(circle(hx - 34, hy - 38, 7)); g.fill(circle(hx + 34, hy - 38, 7));
        fillOut(g, circle(hx, hy, 46), LBODY);
        fillOut(g, ell(hx, hy + 20, 58, 38), LMUZ);
        Path2D nose = new Path2D.Double();
        nose.moveTo(hx - 10, hy + 4); nose.lineTo(hx + 10, hy + 4); nose.lineTo(hx, hy + 15); nose.closePath();
        fillOut(g, nose, rgb(0x5A3217));
        // pout
        Path2D frown = new Path2D.Double();
        frown.moveTo(hx - 13, hy + 33); frown.quadTo(hx, hy + 22, hx + 13, hy + 33);
        g.setColor(OUT); g.setStroke(new BasicStroke(3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); g.draw(frown);
        // grumpy half-lidded eyes glaring at the water
        for (int side = -1; side <= 1; side += 2) {
            double ex = hx + side * 17, ey = hy - 12;
            fillOut(g, ell(ex, ey, 22, 22), Color.WHITE, 2.5f);
            g.setColor(OUT); g.fill(circle(ex + 5, ey + 3, 5));
            g.setColor(LBODY); g.fill(new Arc2D.Double(ex - 12, ey - 12, 24, 24, 0, 180, Arc2D.CHORD));
            line(g, ex - 11, ey, ex + 11, ey, OUT, 3);
            line(g, ex - side * 13, ey - 20 + (side < 0 ? 0 : 0), ex + side * 8, ey - 12, OUT, 4.5f);
        }
        // crossed arms
        for (int side = -1; side <= 1; side += 2) {
            AffineTransform s2 = g.getTransform();
            g.translate(0, -112 + br); g.rotate(side * 0.28);
            fillOut(g, new RoundRectangle2D.Double(-54, -13, 108, 26, 24, 24), LBODY);
            fillOut(g, circle(side * 52, 0, 14), LBODY);
            g.setTransform(s2);
        }
        g.setTransform(s);

        // personal rain cloud
        if (L.rain > 0) {
            double cx = L.x, cy = 175 - 20 * (1 - L.rain);
            Composite c0 = g.getComposite();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) L.rain));
            g.setColor(new Color(170, 200, 230, 200)); g.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int i = 0; i < 12; i++) {
                double rx = cx - 60 + (i * 11) % 120, ry = cy + 20 + frac(t * 2.2 + i * 0.37) * 190;
                g.draw(new Line2D.Double(rx, ry, rx - 4, ry + 16));
            }
            Color cc = rgb(0x6F7C8C);
            for (double[] b : new double[][]{{-45, 10, 34}, {-10, -8, 42}, {35, 4, 36}, {0, 20, 36}, {60, 18, 26}, {-70, 22, 24}}) {
                g.setColor(cc); g.fill(circle(cx + b[0], cy + b[1], b[2]));
            }
            g.setColor(rgb(0x8A97A8)); g.fill(circle(cx - 18, cy - 14, 22));
            g.setComposite(c0);
        }
    }

    // ------------------------------------------------------------------ Pip
    static final int RUN = 0, BALL = 1, EMERGE = 2, FLOAT = 3, LAUGH = 4;

    static class Pip { boolean visible = true; double x, y, rot; int mode; double glasses, coco, ring; }

    static Pip pipAt(double t) {
        Pip p = new Pip();
        if (t < 5.0) {
            p.mode = RUN;
            p.x = t < 4.0 ? 560 : lerp(560, 650, easeInOut(seg(t, 4.0, 5.0)));
            p.y = GROUND - 36 - Math.abs(Math.sin(t * 18)) * 14;
            p.rot = 0.08;
        } else if (t < SPLASH_T) {
            double q = seg(t, 5.0, SPLASH_T);
            p.mode = BALL;
            p.x = lerp(650, SPLASH_X, q);
            p.y = lerp(GROUND - 36, SURF + 10, q) - 250 * 4 * q * (1 - q);
            p.rot = q * Math.PI * 3;
        } else if (t < 9.5) {
            p.visible = false;
        } else if (t < 10.8) {
            p.mode = t < 10.5 ? EMERGE : FLOAT;
            p.x = SPLASH_X;
            double up = easeOutBack(seg(t, 9.5, 9.85));
            p.y = lerp(SURF + 70, SURF - 24, up);
            p.y = lerp(p.y, SURF - 14, easeInOut(seg(t, 10.3, 10.8)));
            p.ring = easeOutBack(seg(t, 10.5, 10.8));
        } else {
            p.mode = in(t, 16.8, 18.3) ? LAUGH : FLOAT;
            p.x = SPLASH_X + 10 * Math.sin((t - 10.8) * 0.9);
            p.y = SURF - 14 + 3 * Math.sin(t * 2.3) + (p.mode == LAUGH ? 3 * Math.sin(t * 45) : 0);
            p.rot = -0.12 + 0.06 * Math.sin(t * 1.3);
            p.ring = 1;
            p.glasses = easeOutBack(seg(t, 11.3, 11.7));
            p.coco = easeOutBack(seg(t, 11.8, 12.1));
        }
        return p;
    }

    static void swimRing(Graphics2D g, Shape e, double start, double extent) {
        Rectangle2D b = e.getBounds2D();
        Arc2D arc = new Arc2D.Double(b, start, extent, Arc2D.OPEN);
        strokeOut(g, arc, rgb(0xFF6FA8), 14);
        g.setColor(Color.WHITE); g.setStroke(new BasicStroke(14, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND,
                10, new float[]{10, 22}, 0));
        g.draw(arc);
    }

    static void foot(Graphics2D g, double x, double y) { fillOut(g, ell(x, y, 18, 11), PB, 2.5f); }

    static void arm(Graphics2D g, double x1, double y1, double x2, double y2) {
        Path2D a = new Path2D.Double(); a.moveTo(x1, y1); a.lineTo(x2, y2);
        strokeOut(g, a, PB, 5);
        fillOut(g, circle(x2, y2, 5.5), PB, 2.5f);
    }

    static void drawPip(Graphics2D g, Pip P, double t) {
        AffineTransform s = g.getTransform();
        g.translate(P.x, P.y); g.rotate(P.rot);
        double r = 34;
        int m = P.mode;

        if (m == RUN) {
            double k = Math.sin(t * 36);
            foot(g, -12 + k * 9, r - 3 - Math.max(0, k) * 9);
            foot(g, 12 - k * 9, r - 3 - Math.max(0, -k) * 9);
        } else if (m == FLOAT || m == LAUGH) {
            foot(g, -4, r + 1); foot(g, 12, r - 3 + Math.sin(t * 4) * 2);
        }

        Shape ringShape = null;
        if (P.ring > 0) {
            ringShape = new Ellipse2D.Double(-50 * P.ring, 18 - 12 * P.ring, 100 * P.ring, 24 * P.ring);
            swimRing(g, ringShape, 0, 180);
        }

        // fluffy body
        g.setColor(PD); g.fill(circle(0, 0, r + 3));
        for (int i = 0; i < 22; i++) { double a = i * Math.PI * 2 / 22; g.fill(circle(Math.cos(a) * (r - 1), Math.sin(a) * (r - 1), 11.5)); }
        g.setColor(PB); g.fill(circle(0, 0, r));
        for (int i = 0; i < 22; i++) { double a = i * Math.PI * 2 / 22; g.fill(circle(Math.cos(a) * (r - 1), Math.sin(a) * (r - 1), 8.5)); }
        g.setColor(PBEL); g.fill(new Ellipse2D.Double(-12, 2, 40, 28));

        // leaf sprout
        Path2D stem = new Path2D.Double();
        stem.moveTo(2, -r - 4); stem.quadTo(0, -r - 14, 6, -r - 20);
        g.setColor(rgb(0x2F7A1C)); g.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); g.draw(stem);
        AffineTransform s2 = g.getTransform();
        g.translate(12, -r - 24); g.rotate(-0.5 + 0.2 * Math.sin(t * 8));
        fillOut(g, ell(0, 0, 20, 10), rgb(0x5BC236), 2);
        g.setTransform(s2);

        // blush
        g.setColor(new Color(255, 110, 130, 120));
        g.fill(ell(-6, 8, 13, 7)); g.fill(ell(32, 6, 10, 6));

        // eyes
        double e1x = 6, e2x = 25, ey = -8;
        if (m == RUN || m == EMERGE) {
            fillOut(g, ell(e1x, ey, 24, 30), Color.WHITE, 2.5f);
            fillOut(g, ell(e2x, ey, 19, 26), Color.WHITE, 2.5f);
            double pr = m == RUN ? 4.5 : 8, jx = m == RUN ? Math.sin(t * 50) * 1.5 : 2;
            g.setColor(OUT); g.fill(circle(e1x + 3 + jx, ey, pr)); g.fill(circle(e2x + 2 + jx, ey, pr * 0.9));
            g.setColor(Color.WHITE); g.fill(circle(e1x + 5 + jx, ey - 3, pr * 0.35)); g.fill(circle(e2x + 4 + jx, ey - 3, pr * 0.3));
            if (m == RUN) {
                line(g, e1x - 10, ey - 22, e1x + 6, ey - 26, OUT, 3);
                line(g, e2x - 4, ey - 26, e2x + 8, ey - 22, OUT, 3);
            }
        } else if (m == BALL) {
            g.setColor(OUT); g.setStroke(new BasicStroke(3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Path2D q = new Path2D.Double();
            q.moveTo(e1x - 8, ey - 7); q.lineTo(e1x + 5, ey); q.lineTo(e1x - 8, ey + 7);
            q.moveTo(e2x + 8, ey - 7); q.lineTo(e2x - 5, ey); q.lineTo(e2x + 8, ey + 7);
            g.draw(q);
        } else {
            g.setColor(OUT); g.setStroke(new BasicStroke(3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new Arc2D.Double(e1x - 8, ey - 4, 16, 14, 20, 140, Arc2D.OPEN));
            g.draw(new Arc2D.Double(e2x - 7, ey - 4, 14, 12, 20, 140, Arc2D.OPEN));
        }

        // mouth
        if (m == RUN) {
            fillOut(g, ell(18, 15, 14, 16 + 4 * Math.sin(t * 28)), rgb(0x5A1A1A), 2);
            g.setColor(rgb(0xF27A8C)); g.fill(ell(18, 20, 8, 5));
        } else if (m == BALL) {
            fillOut(g, circle(18, 14, 3.5), rgb(0x5A1A1A), 2);
        } else if (m == EMERGE) {
            fillOut(g, circle(20, 14, 5), rgb(0x5A1A1A), 2);
            if (in(t, 9.8, 10.4)) {
                g.setColor(new Color(200, 240, 255, 230));
                for (int i = 0; i < 16; i++) {
                    double ph = frac(t * 2.5 + i / 16.0) * 0.6;
                    double dx = 24 + 120 * ph, dy = 14 - 240 * ph + 500 * ph * ph;
                    g.fill(circle(dx, dy, 4));
                }
            }
        } else if (m == FLOAT) {
            g.setColor(OUT); g.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new Arc2D.Double(10, 4, 18, 14, 200, 140, Arc2D.OPEN));
        } else {
            fillOut(g, new Arc2D.Double(6, 2, 26, 24, 180, 180, Arc2D.CHORD), rgb(0x5A1A1A), 2.5f);
            g.setColor(rgb(0xF27A8C)); g.fill(ell(19, 20, 12, 5));
        }

        // sunglasses slide down from the forehead
        if ((m == FLOAT || m == LAUGH) && P.glasses > 0) {
            double off = -34 * (1 - P.glasses);
            Color lens = rgb(0x121212);
            fillOut(g, new RoundRectangle2D.Double(-6, ey - 9 + off, 24, 16, 8, 8), lens, 2);
            fillOut(g, new RoundRectangle2D.Double(18, ey - 9 + off, 20, 15, 8, 8), lens, 2);
            line(g, 16, ey - 4 + off, 20, ey - 4 + off, lens, 3);
            line(g, -6, ey - 4 + off, -24, ey - 8 + off, lens, 3);
            line(g, -1, ey - 5 + off, 5, ey - 1 + off, new Color(255, 255, 255, 200), 2.5f);
            line(g, 22, ey - 5 + off, 27, ey - 1 + off, new Color(255, 255, 255, 200), 2.5f);
        }

        // arms
        if (m == RUN) {
            arm(g, -24, 2, -40, -18 + 10 * Math.sin(t * 30));
            arm(g, 28, 4, 44, -16 + 10 * Math.sin(t * 30 + 2));
        } else if (m == BALL) {
            foot(g, 8, 26); foot(g, 24, 22);
            Path2D hug = new Path2D.Double(); hug.moveTo(-26, 4); hug.quadTo(0, 40, 30, 8);
            strokeOut(g, hug, PB, 5);
        } else if (m == EMERGE) {
            arm(g, -26, -2, -40, -28 + 5 * Math.sin(t * 14));
            arm(g, 30, 0, 44, -26 + 5 * Math.sin(t * 14 + 1));
        } else {
            arm(g, -26, -4, -36, -26);          // hand behind head
            if (m == LAUGH) arm(g, -18, 10, -2, 22);
            if (P.coco > 0) {
                arm(g, 28, 8, 40, -2);
                double cs = P.coco;
                AffineTransform s3 = g.getTransform();
                g.translate(48, -10); g.scale(cs, cs);
                line(g, -2, -10, -22, 16, rgb(0xFF4FA3), 3);      // straw to Pip's mouth
                fillOut(g, circle(0, 0, 13), rgb(0x7B4A21), 2.5f);
                g.setColor(rgb(0xF5EBD7)); g.fill(ell(0, -8, 18, 6));
                line(g, 6, -8, 16, -30, rgb(0x8B5A2B), 2);
                Path2D um = new Path2D.Double(); um.moveTo(4, -30); um.quadTo(16, -44, 28, -30); um.closePath();
                fillOut(g, um, rgb(0xFF6FA8), 2);
                g.setTransform(s3);
            }
        }

        if (ringShape != null) swimRing(g, ringShape, 180, 180);

        // sweat drops while fleeing
        if (m == RUN) {
            g.setColor(new Color(150, 215, 255, 220));
            for (int i = 0; i < 3; i++) {
                double ph = frac(t * 2.2 + i / 3.0);
                double dx = -20 - ph * 45, dy = -r - 4 - 30 * ph + 60 * ph * ph;
                g.fill(new Ellipse2D.Double(dx - 4, dy - 6, 8, 12));
            }
        }
        g.setTransform(s);
    }

    // ------------------------------------------------------------------ effects
    static double[][] SPLASH = new double[70][3];
    static {
        Random r = new Random(7);
        for (double[] d : SPLASH) { d[0] = (r.nextDouble() - .5) * 560; d[1] = -330 - r.nextDouble() * 520; d[2] = 5 + r.nextDouble() * 9; }
    }

    static void drawEffects(Graphics2D g, double t, Lion L, Pip P) {
        // lion running dust
        if (t < 6.2 && !L.sit) dust(g, L.x - 60, t, 3, 1.0);
        if (t < 5.0) dust(g, P.x - 20, t + 0.3, 5, 0.5);
        // skid marks + dust
        if (t >= 6.2 && t < 9) {
            double endX = t < 7.0 ? L.x : 560;
            double fade = 1 - seg(t, 8.0, 9.0);
            g.setColor(alpha(rgb(0x4A2A12), 0.6 * fade));
            g.fill(new RoundRectangle2D.Double(420, GROUND + 4, endX + 60 - 420, 5, 5, 5));
            g.fill(new RoundRectangle2D.Double(400, GROUND + 14, endX + 40 - 400, 5, 5, 5));
            if (t < 7.3) {
                for (int k = 0; k < 8; k++) {
                    double age = frac(t * 3 + k / 8.0);
                    double a = (1 - age) * 0.7 * (1 - seg(t, 7.0, 7.3));
                    g.setColor(alpha(rgb(0xD8C49A), a));
                    g.fill(circle(L.x + 110 - age * 60 + k * 6, GROUND - 10 - age * 50, 10 + age * 30));
                }
            }
        }
        // cannonball splash
        double dt = t - SPLASH_T;
        if (dt >= 0 && dt < 2.5) {
            for (int i = 0; i < 3; i++) {
                double d = dt - i * 0.25;
                if (d < 0 || d > 1.6) continue;
                double rr = 60 + d * 240;
                g.setColor(new Color(255, 255, 255, (int) (200 * (1 - d / 1.6))));
                g.setStroke(new BasicStroke(4));
                g.draw(ell(SPLASH_X, SURF + 4, rr * 2, rr * 0.35));
            }
            if (dt < 0.7) {
                double hgt = 230 * Math.sin(Math.PI * dt / 0.7);
                Path2D col = new Path2D.Double();
                col.moveTo(SPLASH_X - 70, SURF + 5);
                col.quadTo(SPLASH_X - 40, SURF - hgt * 0.6, SPLASH_X - 25, SURF - hgt);
                col.quadTo(SPLASH_X, SURF - hgt * 0.8, SPLASH_X + 25, SURF - hgt);
                col.quadTo(SPLASH_X + 40, SURF - hgt * 0.6, SPLASH_X + 70, SURF + 5);
                col.closePath();
                g.setColor(new Color(170, 225, 250, 220)); g.fill(col);
                g.setColor(new Color(255, 255, 255, 200)); g.setStroke(new BasicStroke(4)); g.draw(col);
            }
            for (double[] d : SPLASH) {
                double x = SPLASH_X + d[0] * dt, y = SURF + d[1] * dt + 0.5 * 1500 * dt * dt;
                if (y > SURF + 6 || dt > 1.4) continue;
                g.setColor(new Color(160, 220, 255, 235)); g.fill(circle(x, y, d[2]));
                g.setColor(new Color(255, 255, 255, 220)); g.fill(circle(x - d[2] * 0.3, y - d[2] * 0.3, d[2] * 0.35));
            }
        }
        // bubbles while Pip is underwater
        if (t > 5.9 && t < 9.6) {
            for (int i = 0; i < 9; i++) {
                double ph = frac(t * 0.9 + i / 9.0);
                double bx = SPLASH_X + 30 * Math.sin(t * 3 + i * 2) + (i % 3 - 1) * 20, by = H - 30 - ph * (H - 30 - SURF);
                g.setColor(new Color(255, 255, 255, 190)); g.setStroke(new BasicStroke(2.5f));
                g.draw(circle(bx, by, 5 + (i % 3) * 3));
            }
        }
        // lazy ripples around floating Pip
        if (t > 9.5) {
            for (int i = 0; i < 2; i++) {
                double d = frac(t * 0.5 + i * 0.5);
                g.setColor(new Color(255, 255, 255, (int) (160 * (1 - d))));
                g.setStroke(new BasicStroke(3));
                g.draw(ell(P.x, SURF + 6, 90 + d * 160, 16 + d * 20));
            }
        }
        // the single evil droplet
        if (t >= 8.0 && t < 8.6) {
            double p = seg(t, 8.0, 8.6);
            double y = lerp(-30, GROUND - 146, easeIn(p));
            g.setColor(new Color(120, 200, 255));
            Path2D drop = new Path2D.Double();
            drop.moveTo(674, y - 18); drop.quadTo(684, y, 674, y + 6); drop.quadTo(664, y, 674, y - 18);
            g.fill(drop);
            g.setColor(Color.WHITE); g.fill(circle(671, y - 2, 2.5));
        }
        if (t >= 8.6 && t < 9.0) {
            double p = seg(t, 8.6, 9.0);
            g.setColor(new Color(160, 220, 255, (int) (255 * (1 - p))));
            for (int i = 0; i < 6; i++) {
                double a = -Math.PI * (0.1 + 0.8 * i / 5.0);
                g.fill(circle(674 + Math.cos(a) * 30 * p, GROUND - 146 + Math.sin(a) * 30 * p, 3));
            }
        }
        // toe dip ripple
        if (t >= 16.1 && t < 17.2) {
            double d = seg(t, 16.1, 17.2);
            g.setColor(new Color(255, 255, 255, (int) (220 * (1 - d))));
            g.setStroke(new BasicStroke(3));
            g.draw(ell(685, SURF + 4, 30 + d * 120, 8 + d * 14));
        }
    }

    static void dust(Graphics2D g, double x, double t, double rate, double sz) {
        for (int k = 0; k < 6; k++) {
            double age = frac(t * rate + k / 6.0);
            g.setColor(alpha(rgb(0xE0CFA5), 0.55 * (1 - age)));
            g.fill(circle(x - age * 120 * sz, GROUND - 6 - age * 25 * sz, (6 + age * 22) * sz));
        }
    }

    static void speedLines(Graphics2D g, double t) {
        double a = 1 - seg(t, 3.8, 4.6);
        if (a <= 0) return;
        g.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < 12; i++) {
            double y = 90 + (i * 97) % 440;
            double x = W - frac(t * 1.1 + i * 0.263) * (W + 400);
            double len = 120 + 100 * hash(i);
            g.setColor(new Color(255, 255, 255, (int) (120 * a)));
            g.draw(new Line2D.Double(x, y, x + len, y));
        }
    }

    // ------------------------------------------------------------------ text
    static Shape textShape(Graphics2D g, String s, Font f, double cx, double cy) {
        TextLayout tl = new TextLayout(s, f, g.getFontRenderContext());
        Rectangle2D b = tl.getBounds();
        return tl.getOutline(AffineTransform.getTranslateInstance(cx - b.getWidth() / 2 - b.getX(), cy - b.getHeight() / 2 - b.getY()));
    }

    static void outlinedText(Graphics2D g, String s, double cx, double cy, Font f, Color fill, Color stroke, float sw) {
        Shape sh = textShape(g, s, f, cx, cy);
        g.setStroke(new BasicStroke(sw, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(stroke); g.draw(sh);
        g.setColor(fill); g.fill(sh);
    }

    static double popScale(double t, double t0, double t1) {
        double in = easeOutBack(seg(t, t0, t0 + 0.25));
        double out = 1 - easeIn(seg(t, t1 - 0.18, t1));
        return in * out;
    }

    static void bubble(Graphics2D g, double t, double t0, double t1, String s, double bx, double by, double tx, double ty, int size) {
        if (t < t0 || t > t1) return;
        double sc = popScale(t, t0, t1);
        if (sc <= 0.01) return;
        AffineTransform s0 = g.getTransform();
        g.translate(bx, by); g.scale(sc, sc); g.translate(-bx, -by);
        Font f = new Font(FONT, Font.BOLD, size);
        TextLayout tl = new TextLayout(s, f, g.getFontRenderContext());
        double bw = tl.getBounds().getWidth() + 44, bh = size + 30;
        Area a = new Area(new RoundRectangle2D.Double(bx - bw / 2, by - bh / 2, bw, bh, 34, 34));
        Path2D tail = new Path2D.Double();
        double base = clamp(tx, bx - bw / 2 + 30, bx + bw / 2 - 30);
        tail.moveTo(base - 14, by + bh / 2 - 6); tail.lineTo(base + 14, by + bh / 2 - 6); tail.lineTo(tx, ty); tail.closePath();
        a.add(new Area(tail));
        fillOut(g, a, Color.WHITE, 4);
        g.setColor(OUT); g.fill(textShape(g, s, f, bx, by));
        g.setTransform(s0);
    }

    static void shout(Graphics2D g, double t, double t0, double t1, String s, double cx, double cy, int size, Color burst, Color fill, double rot) {
        if (t < t0 || t > t1) return;
        double sc = popScale(t, t0, t1) * (1 + 0.04 * Math.sin(t * 30));
        if (sc <= 0.01) return;
        AffineTransform s0 = g.getTransform();
        g.translate(cx, cy); g.rotate(rot + 0.03 * Math.sin(t * 17)); g.scale(sc, sc);
        Font f = new Font(FONT, Font.BOLD, size);
        Rectangle2D b = new TextLayout(s, f, g.getFontRenderContext()).getBounds();
        double rx = b.getWidth() / 2 + 50, ry = b.getHeight() / 2 + 42;
        Path2D star = new Path2D.Double();
        int n = 18;
        for (int i = 0; i < 2 * n; i++) {
            double a = i * Math.PI / n, k = i % 2 == 0 ? 1.0 : 0.72 + 0.08 * hash(i);
            double px = Math.cos(a) * rx * k, py = Math.sin(a) * ry * k;
            if (i == 0) star.moveTo(px, py); else star.lineTo(px, py);
        }
        star.closePath();
        fillOut(g, star, burst, 4);
        outlinedText(g, s, 0, 0, f, fill, OUT, 9);
        g.setTransform(s0);
    }

    // ------------------------------------------------------------------ frame
    static void render(Graphics2D g, double t) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        double cam = cam(t);
        Lion L = lionAt(t);
        Pip P = pipAt(t);

        double shake = 0;
        if (in(t, SPLASH_T, SPLASH_T + 0.35)) shake = 7 * (1 - seg(t, SPLASH_T, SPLASH_T + 0.35));
        if (in(t, 16.15, 16.4)) shake = 4;
        AffineTransform base = g.getTransform();
        g.translate(Math.sin(t * 90) * shake, Math.cos(t * 77) * shake);

        drawBackground(g, cam, t);
        drawGroundAndWater(g, cam, t);
        if (L.sit) drawLionSit(g, L, t); else drawLionSide(g, L, t);
        if (P.visible) drawPip(g, P, t);
        drawWaterFront(g, cam, t);
        drawEffects(g, t, L, P);
        drawForegroundLeaves(g, cam, t);
        g.setTransform(base);

        speedLines(g, t);

        // dialogue & sound words
        double lh = L.x + 88;
        shout(g, t, 0.3, 1.3, "ROAR!", 360, 330, 58, rgb(0xFFB02E), rgb(0xFF4D2E), -0.12);
        shout(g, t, 2.9, 3.8, "ROOOAR!", 380, 320, 58, rgb(0xFFB02E), rgb(0xFF4D2E), -0.08);
        bubble(g, t, 0.9, 2.8, "AAAAAHHH!!", 640, 390, P.x + 10, P.y - 48, 34);
        bubble(g, t, 3.3, 4.8, "NOT TODAY, KITTY!", 700, 390, P.x + 10, P.y - 48, 32);
        shout(g, t, 4.9, 5.85, "CANNONBALL!!!", 820, 130, 50, rgb(0xFFE14D), rgb(0xFF7A1A), 0.06);
        shout(g, t, SPLASH_T, 7.0, "SPLASH!", 980, 330, 76, rgb(0xBDEBFF), rgb(0x1E88E5), -0.05);
        shout(g, t, 6.25, 7.3, "SKRRRRT!", 360, 655, 44, rgb(0xFFE14D), rgb(0xE8541E), 0.04);
        bubble(g, t, 7.1, 8.2, "WHOA WHOA WHOA!", 560, 270, lh + 20, GROUND - 230, 32);
        if (in(t, 8.6, 9.2)) outlinedText(g, "plink.", 740, 405, new Font(FONT, Font.BOLD | Font.ITALIC, 26), rgb(0x9FDBFF), OUT, 6);
        shout(g, t, 8.8, 10.4, "EWWW!! WATER!!", 470, 260, 46, rgb(0xB5EF7A), rgb(0x3E8E1F), -0.05);
        bubble(g, t, 10.8, 12.6, "Come on in, the water's lovely!", 900, 470, P.x - 20, P.y - 36, 28);
        bubble(g, t, 12.9, 14.6, "Hmph. Lions don't do baths.", 700, 330, L.x + 50, GROUND - 230, 28);
        bubble(g, t, 14.9, 16.1, "...maybe just one toe.", 600, 290, L.x + 90, GROUND - 230, 28);
        shout(g, t, 16.25, 17.7, "NOPE! NOPE! NOPE!", 470, 230, 50, rgb(0xFFE14D), rgb(0xE53935), 0.05);
        bubble(g, t, 16.9, 18.3, "hehehe!", 1010, 470, P.x + 10, P.y - 40, 32);

        // title card
        if (t < 2.4) {
            double sc = easeOutBack(seg(t, 0.1, 0.5));
            double out = seg(t, 1.9, 2.4);
            AffineTransform s0 = g.getTransform();
            g.translate(W / 2.0, 120 - out * 260); g.rotate(-0.04); g.scale(sc, sc);
            outlinedText(g, "JUNGLE DASH!", 6, 8, new Font(FONT, Font.BOLD, 100), new Color(0, 0, 0, 90), new Color(0, 0, 0, 0), 1);
            outlinedText(g, "JUNGLE DASH!", 0, 0, new Font(FONT, Font.BOLD, 100), rgb(0xFFE14D), OUT, 14);
            g.setTransform(s0);
        }

        // iris out on Pip, then the end card
        if (t > 18.3) {
            double p = easeInOut(seg(t, 18.3, 19.2));
            double r = lerp(1500, 0, p);
            Area a = new Area(new Rectangle2D.Double(-10, -10, W + 20, H + 20));
            if (r > 0.5) a.subtract(new Area(circle(P.x, P.y - 10, r)));
            g.setColor(Color.BLACK); g.fill(a);
        }
        if (t > 19.1) {
            double a = seg(t, 19.1, 19.5);
            double sc = easeOutBack(seg(t, 19.1, 19.45));
            AffineTransform s0 = g.getTransform();
            g.translate(W / 2.0, H / 2.0 - 30); g.scale(sc, sc);
            outlinedText(g, "THE END", 0, 0, new Font(FONT, Font.BOLD, 110), Color.WHITE, rgb(0x444444), 6);
            g.setTransform(s0);
            Composite c0 = g.getComposite();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) a));
            outlinedText(g, "(lions REALLY hate bath time)", W / 2.0, H / 2.0 + 70, new Font(FONT, Font.BOLD, 36), rgb(0xFFE14D), Color.BLACK, 1);
            g.setComposite(c0);
        }
    }

    // ------------------------------------------------------------------ audio
    static final int SR = 44100;
    static final double[] A = new double[(int) (SR * DUR) + SR];
    static final Random AR = new Random(42);

    static void add(int i, double v) { if (i >= 0 && i < A.length) A[i] += v; }
    static double mtof(double m) { return 440 * Math.pow(2, (m - 69) / 12.0); }
    static double osc(int w, double ph) {
        double f = frac(ph);
        switch (w) {
            case 0: return Math.sin(2 * Math.PI * f);
            case 1: return f < 0.5 ? 1 : -1;
            case 2: return 2 * f - 1;
            default: return 1 - 4 * Math.abs(f - 0.5);
        }
    }
    static final int SIN = 0, SQ = 1, SAW = 2, TRI = 3;

    /** Tone with exponential pitch glide, vibrato, attack/release and optional one-pole lowpass. */
    static void tone(double start, double dur, double f0, double f1, double amp, int w, double atk, double rel, double vibHz, double vibDepth, double lp) {
        int n = (int) (dur * SR), off = (int) (start * SR);
        double ph = 0, y = 0, k = lp > 0 ? 1 - Math.exp(-2 * Math.PI * lp / SR) : 1;
        for (int i = 0; i < n; i++) {
            double tt = i / (double) SR;
            double f = f0 * Math.pow(f1 / f0, tt / dur) * (1 + vibDepth * Math.sin(2 * Math.PI * vibHz * tt));
            ph += f / SR;
            double env = Math.min(1, tt / atk) * Math.min(1, (dur - tt) / rel);
            y += k * (osc(w, ph) - y);
            add(off + i, amp * env * y);
        }
    }
    static void tone(double start, double dur, double f0, double f1, double amp, int w) { tone(start, dur, f0, f1, amp, w, 0.005, 0.03, 0, 0, 0); }

    static void pluck(double start, double f, double amp, double decay, double dur) {
        int n = (int) (dur * SR), off = (int) (start * SR);
        for (int i = 0; i < n; i++) {
            double tt = i / (double) SR;
            double env = Math.exp(-tt * decay) * Math.min(1, tt / 0.003);
            double v = Math.sin(2 * Math.PI * f * tt) + 0.5 * Math.sin(4 * Math.PI * f * tt) * Math.exp(-tt * decay) + 0.25 * Math.sin(6 * Math.PI * f * tt);
            add(off + i, amp * env * v);
        }
    }

    static void noise(double start, double dur, double amp, double lp0, double lp1, double atk, double rel, double amHz) {
        int n = (int) (dur * SR), off = (int) (start * SR);
        double y = 0;
        for (int i = 0; i < n; i++) {
            double tt = i / (double) SR;
            double lp = lp0 * Math.pow(lp1 / lp0, tt / dur);
            double k = 1 - Math.exp(-2 * Math.PI * lp / SR);
            y += k * ((AR.nextDouble() * 2 - 1) - y);
            double env = Math.min(1, tt / atk) * Math.min(1, (dur - tt) / rel);
            double am = amHz > 0 ? 0.55 + 0.45 * Math.sin(2 * Math.PI * amHz * tt) : 1;
            add(off + i, amp * env * am * y);
        }
    }

    static void roar(double start, double dur) {
        noise(start, dur, 0.9, 500, 250, 0.05, 0.3, 27);
        tone(start, dur, 95, 70, 0.28, SAW, 0.05, 0.3, 23, 0.12, 700);
        tone(start, dur, 190, 140, 0.12, SAW, 0.05, 0.3, 17, 0.1, 1200);
    }

    static void kick(double s) { tone(s, 0.14, 130, 45, 0.45, SIN, 0.002, 0.08, 0, 0, 0); }
    static void snare(double s) { noise(s, 0.1, 0.25, 6000, 2500, 0.001, 0.08, 0); }
    static void hat(double s) { noise(s, 0.03, 0.07, 9000, 9000, 0.001, 0.02, 0); }

    static void buildAudio() {
        // --- frantic chase music
        double six = 0.09;
        int[] mel = {0, -1, 0, -1, 0, -1, 0, 3, 2, 1, 2, 1, 2, 1, 2, 5};
        for (int i = 0; i * six < 4.85; i++) {
            double s = i * six;
            int oct = (i / 32) % 2 == 0 ? 0 : 2;
            tone(s, six * 0.85, mtof(69 + oct + mel[i % 16]), mtof(69 + oct + mel[i % 16]), 0.06, SQ, 0.003, 0.02, 0, 0, 3500);
            if (i % 4 == 0) {
                int b = (i / 4) % 2 == 0 ? 45 : 40;
                tone(s, six * 3.5, mtof(b), mtof(b), 0.22, TRI, 0.005, 0.04, 0, 0, 0);
                if ((i / 4) % 2 == 0) kick(s); else snare(s);
            }
            if (i % 2 == 1) hat(s);
        }
        tone(4.86, 0.12, mtof(57), mtof(57), 0.18, SQ, 0.003, 0.05, 0, 0, 2500);   // stab

        // --- footsteps
        for (double s = 0.1; s < 6.2; s += 0.25) tone(s, 0.1, 95, 40, 0.28, SIN, 0.002, 0.07, 0, 0, 0);
        for (double s = 0.0; s < 5.0; s += 0.11) noise(s, 0.025, 0.08, 3000, 3000, 0.001, 0.02, 0);

        // --- roars & screams
        roar(0.3, 1.0);
        roar(2.9, 0.85);
        tone(0.9, 1.8, 950, 1150, 0.09, SIN, 0.05, 0.2, 11, 0.06, 0);
        tone(0.9, 1.8, 1900, 2300, 0.03, TRI, 0.05, 0.2, 11, 0.06, 0);
        tone(3.35, 0.35, 700, 900, 0.08, SQ, 0.02, 0.05, 8, 0.03, 2000);          // "not today!"
        tone(3.75, 0.5, 900, 650, 0.08, SQ, 0.02, 0.1, 8, 0.03, 2000);

        // --- slide whistle up & down, then SPLASH
        tone(4.9, 0.55, 500, 1800, 0.14, SIN, 0.02, 0.05, 6, 0.01, 0);
        tone(5.45, 0.35, 1800, 450, 0.14, SIN, 0.01, 0.05, 6, 0.01, 0);
        noise(SPLASH_T, 1.1, 1.0, 5000, 500, 0.003, 0.9, 0);
        tone(SPLASH_T, 0.3, 220, 70, 0.45, SIN, 0.002, 0.2, 0, 0, 0);

        // --- skid, windmill, phew
        noise(6.2, 0.8, 0.35, 3500, 1800, 0.01, 0.1, 0);
        tone(6.2, 0.8, 900, 600, 0.06, SAW, 0.01, 0.1, 30, 0.05, 2500);
        noise(7.0, 1.2, 0.45, 900, 900, 0.05, 0.15, 7);
        tone(7.0, 1.2, 330, 330, 0.07, TRI, 0.05, 0.1, 7, 0.12, 0);               // wobbly tension
        tone(8.25, 0.35, 500, 350, 0.06, SIN, 0.05, 0.2, 0, 0, 0);                // phew

        // --- bubbles underwater
        for (double s = 6.1; s < 9.4; s += 0.28 + 0.1 * Math.sin(s * 7)) tone(s, 0.07, 350, 1100, 0.09, SIN, 0.003, 0.02, 0, 0, 0);

        // --- plink + EWWW
        pluck(8.6, 1900, 0.18, 18, 0.4);
        pluck(8.6, 2850, 0.08, 25, 0.3);
        tone(8.8, 1.1, 520, 230, 0.12, SIN, 0.03, 0.2, 7, 0.08, 0);
        tone(8.8, 1.1, 130, 100, 0.16, SAW, 0.02, 0.2, 31, 0.25, 900);           // raspberry
        tone(8.85, 0.9, 260, 180, 0.05, SQ, 0.03, 0.2, 9, 0.05, 1200);

        // --- Pip pops up and spits water
        tone(9.52, 0.1, 300, 900, 0.3, SIN, 0.002, 0.05, 0, 0, 0);
        noise(9.5, 0.5, 0.4, 3500, 800, 0.003, 0.4, 0);
        noise(9.8, 0.55, 0.25, 5000, 3000, 0.01, 0.2, 18);

        // --- chill island tune
        int[][] chords = {{60, 64, 67, 72}, {65, 69, 72, 77}, {67, 71, 74, 79}, {60, 64, 67, 72}};
        int[] roots = {48, 53, 55, 48};
        int[] pat = {0, 1, 2, 1, 3, 2, 1, 0};
        double e = 0.25;
        for (int i = 0; 10.7 + i * e < 18.3; i++) {
            double s = 10.7 + i * e;
            int bar = (i / 8) % 4;
            boolean sad = s > 12.9 && s < 14.7;                                       // hush for the sad trombone
            double v = sad ? 0.35 : 1;
            pluck(s, mtof(chords[bar][pat[i % 8]] + 12), 0.07 * v, 6, 0.6);
            if (i % 4 == 0) pluck(s, mtof(roots[bar]), 0.2 * v, 4, 0.9);
            if (i % 4 == 2) for (int c = 0; c < 3; c++) pluck(s + c * 0.012, mtof(chords[bar][c]), 0.035 * v, 9, 0.4);
            if (i % 2 == 1) noise(s, 0.04, 0.05 * v, 7000, 7000, 0.002, 0.03, 0);
        }

        // --- sunglasses ting, rain
        pluck(11.72, 2637, 0.12, 5, 1.0);
        pluck(11.72, 3520, 0.07, 6, 0.8);
        noise(12.3, 2.5, 0.06, 5000, 5000, 0.4, 0.4, 0);
        noise(17.3, 1.0, 0.05, 5000, 5000, 0.3, 0.3, 0);

        // --- sad trombone
        double[] tb = {62, 61, 60, 59};
        double[] td = {0.38, 0.38, 0.38, 1.1};
        double s = 13.0;
        for (int i = 0; i < 4; i++) {
            tone(s, td[i], mtof(tb[i] - 12), mtof(tb[i] - 12), 0.2, SAW, 0.03, 0.08, i == 3 ? 6 : 0, i == 3 ? 0.03 : 0, 1100);
            s += td[i];
        }

        // --- sneaky tiptoe pizzicato
        int[] sneak = {60, 62, 63, 65, 67};
        for (int i = 0; i < 5; i++) pluck(14.85 + i * 0.25, mtof(sneak[i] - 12), 0.14, 14, 0.3);

        // --- toe touch -> BOING + NOPE honks
        pluck(16.12, 1700, 0.12, 20, 0.3);
        tone(16.18, 0.55, 140, 520, 0.28, SIN, 0.003, 0.1, 18, 0.2, 0);
        tone(16.2, 0.35, 800, 1500, 0.07, SAW, 0.01, 0.1, 12, 0.05, 2500);        // yelp
        for (int i = 0; i < 3; i++) tone(16.35 + i * 0.4, 0.2, 330, 300, 0.12, SQ, 0.005, 0.05, 0, 0, 1500);

        // --- hehehe
        for (int i = 0; i < 6; i++) tone(16.95 + i * 0.17, 0.09, 1150, 900, 0.1, SIN, 0.005, 0.03, 0, 0, 0);

        // --- ta-da!
        tone(18.25, 0.12, mtof(67), mtof(67), 0.06, SAW, 0.005, 0.03, 0, 0, 2500);
        for (int m : new int[]{60, 64, 67, 72}) tone(18.4, 1.4, mtof(m), mtof(m), 0.055, SAW, 0.01, 0.8, 5, 0.004, 2500);
        tone(18.4, 1.4, mtof(48), mtof(48), 0.15, TRI, 0.01, 0.8, 0, 0, 0);
        noise(18.4, 1.2, 0.12, 8000, 3000, 0.002, 1.0, 0);                         // cymbal
    }

    static void writeWav(File f) throws IOException {
        double peak = 1e-9;
        for (double v : A) peak = Math.max(peak, Math.abs(v));
        int n = (int) (SR * DUR);
        try (DataOutputStream o = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(f)))) {
            o.writeBytes("RIFF"); o.writeInt(Integer.reverseBytes(36 + n * 2)); o.writeBytes("WAVE");
            o.writeBytes("fmt "); o.writeInt(Integer.reverseBytes(16)); o.writeShort(Short.reverseBytes((short) 1)); o.writeShort(Short.reverseBytes((short) 1));
            o.writeInt(Integer.reverseBytes(SR)); o.writeInt(Integer.reverseBytes(SR * 2)); o.writeShort(Short.reverseBytes((short) 2)); o.writeShort(Short.reverseBytes((short) 16));
            o.writeBytes("data"); o.writeInt(Integer.reverseBytes(n * 2));
            for (int i = 0; i < n; i++) {
                double v = Math.tanh(A[i] / peak * 1.2) * 0.92;
                o.writeShort(Short.reverseBytes((short) (v * 32767)));
            }
        }
    }

    // ------------------------------------------------------------------ main
    public static void main(String[] args) throws Exception {
        String ffmpeg = args[0], out = args[1];
        File previewDir = args.length > 2 ? new File(args[2]) : null;
        File wav = File.createTempFile("jungle", ".wav");
        buildAudio();
        writeWav(wav);

        ProcessBuilder pb = new ProcessBuilder(ffmpeg, "-y", "-loglevel", "error",
                "-f", "rawvideo", "-pix_fmt", "bgr24", "-s", W + "x" + H, "-r", String.valueOf(FPS), "-i", "-",
                "-i", wav.getAbsolutePath(),
                "-c:v", "libx264", "-preset", "medium", "-crf", "20", "-pix_fmt", "yuv420p",
                "-c:a", "aac", "-b:a", "160k", "-shortest", "-movflags", "+faststart", out);
        pb.redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.INHERIT);
        Process proc = pb.start();

        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_3BYTE_BGR);
        byte[] data = ((DataBufferByte) img.getRaster().getDataBuffer()).getData();
        Set<Integer> previews = new HashSet<>();
        for (double pt : new double[]{1.0, 3.5, 5.4, 5.95, 6.6, 7.5, 8.55, 9.2, 10.0, 11.0, 12.2, 13.5, 15.9, 16.5, 17.5, 18.8, 19.8})
            previews.add((int) Math.round(pt * FPS));

        try (OutputStream os = new BufferedOutputStream(proc.getOutputStream(), 1 << 20)) {
            int frames = (int) (DUR * FPS);
            for (int f = 0; f < frames; f++) {
                double t = f / (double) FPS;
                Graphics2D g = img.createGraphics();
                render(g, t);
                g.dispose();
                os.write(data);
                if (previewDir != null && previews.contains(f))
                    ImageIO.write(img, "png", new File(previewDir, String.format("f%05.2f.png", t)));
            }
        }
        int code = proc.waitFor();
        wav.delete();
        if (code != 0) throw new RuntimeException("ffmpeg failed: " + code);
        System.out.println("Wrote " + out);
    }
}
