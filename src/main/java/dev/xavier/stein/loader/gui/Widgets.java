/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.loader.gui;

import dev.xavier.stein.loader.api.Option;

import net.minecraft.client.resources.I18n;

import java.util.HashMap;
import java.util.Map;

/**
 * The Stein design system pieces on the Loader screens (the same as the Stein Client's): the card,
 * the title, the option row, the toggle switch, the pill button, "Restore defaults". Colors and
 * sizes come from {@link Theme}.
 *
 * <p>They live together here so that the menu, the Panel and the screen editor look like the same
 * thing to the user — and so that a mod's page, which only describes its options, is born with this
 * look.
 */
public final class Widgets {

    // Opaque: the card sits in front of the game and the grid elements, and nothing may show
    // through it.
    public static final int CARD_BG = Theme.BG;
    public static final int SURFACE = 0x0DE8E1F5;
    public static final int SURFACE_HOVER = Theme.ACCENT_SOFT;
    public static final int TEXT = Theme.INK;
    public static final int TEXT_DIM = Theme.MUTED;
    public static final int ON = Theme.ACCENT;

    /** The toggle switch: a 16 by 8 pill, the same on every screen. */
    public static final int KNOB_W = 16;

    public static final int KNOB_H = 8;

    /** Width of the Option.Slider track, at the right of the row. */
    public static final int TRACK_W = 70;

    /** Height of an option row. */
    public static final int ROW = 22;

    /**
     * Where each option's switch is (0 to 1), by name; this is what makes it slide instead of jump.
     */
    private static final Map<String, Float> knobs = new HashMap<>();

    private Widgets() {}

    /** The background card: the design system's field, with a thin outline around it. */
    public static void card(float x, float y, float w, float h, float alpha) {
        Draw2D.rect(x, y, w, h, Ui.alpha(CARD_BG, alpha));
        Ui.frame(x, y, w, h, Ui.alpha(Theme.RULE_STRONG, alpha));
    }

    /**
     * A surface inside the card (a section, a list item): just one tone; under the cursor, the
     * faint accent.
     */
    public static void surface(float x, float y, float w, float h, boolean hover, float alpha) {
        Draw2D.rect(x, y, w, h, Ui.alpha(hover ? SURFACE_HOVER : SURFACE, alpha));
    }

    public static void title(String text, int x, int y, float alpha) {
        Ui.title(text, x, y, Theme.TITLE, Ui.alpha(Theme.INK, alpha));
    }

    /** Is the mouse over the close X of a card, centered at (cx, cy)? */
    public static boolean closeHit(int mx, int my, int cx, int cy) {
        return Math.abs(mx - cx) <= 8 && Math.abs(my - cy) <= 8;
    }

    /** The place of an option row: left, top, width and height. */
    public record Row(int x, int y, int w, int h) {}

    /** The mouse over a list: its position and whether it is inside the visible part. */
    public record Pointer(int mx, int my, boolean inside) {}

    /**
     * An option row: name on the left, control on the right. A sliding switch for the two-state
     * ones, a value with arrows for the cycling ones. Returns the height used.
     */
    public static int option(Option o, String key, Row row, Pointer ptr, float alpha, long delta) {
        int x = row.x();
        int y = row.y();
        int w = row.w();
        int h = row.h();
        int mx = ptr.mx();
        int my = ptr.my();
        boolean hover = ptr.inside() && mx >= x - 6 && mx < x + w + 6 && my >= y && my < y + h;
        if (o.isRestore()) {
            restore(o, x, y, w, h, hover, alpha);
            return h;
        }
        if (hover) {
            Draw2D.rect(x - 6, y, w + 12, h - 2, Ui.alpha(SURFACE_HOVER, alpha));
        }
        int name = Ui.alpha(hover ? Theme.INK : Theme.INK_SOFT, alpha);
        if (o instanceof Option.Slider s) {
            sliderRow(s, key, row, mx, hover, name, alpha, delta);
            return h;
        }
        Boolean state = o.state();
        int taken = chips(o, row, mx, hover, alpha);
        if (state != null) {
            Ui.text(Ui.trim(o.label, w - 34 - taken), x, y + 7, name);
            knob(key + "|" + o.label, x + w - 20, y + h / 2 - 4, state, alpha, delta);
            return h;
        }
        valueRow(o, row, mx, hover, name, taken, alpha);
        return h;
    }

    /**
     * Name on the left, value and track on the right: the track is where you drag (TRACK_W wide).
     */
    private static void sliderRow(
            Option.Slider s,
            String key,
            Row row,
            int mx,
            boolean hover,
            int name,
            float alpha,
            long delta) {
        int x = row.x();
        int y = row.y();
        int w = row.w();
        int h = row.h();
        String value = s.value();
        int vw = Ui.monoWidth(value);
        int tx = x + w - TRACK_W;
        Ui.text(Ui.trim(s.label, w - TRACK_W - vw - 16), x, y + 7, name);
        Ui.mono(value, tx - vw - 8, y + 7, Ui.alpha(Theme.GOLD, alpha));
        boolean overTrack = hover && mx >= tx - 4;
        slider(
                key + "|" + s.label,
                tx,
                y + h / 2.0F,
                TRACK_W,
                s.fraction(),
                overTrack,
                sliding == s,
                alpha,
                delta);
    }

    /**
     * The key label and the checkbox of the row, when the option has them; returns the width they
     * take away from the name.
     */
    private static int chips(Option o, Row row, int mx, boolean hover, float alpha) {
        int x = row.x();
        int y = row.y();
        int w = row.w();
        int h = row.h();
        int[] chip = keyChip(o, x, y, w, h);
        if (chip != null) {
            keyChipDraw(o.key(), chip, hover && mx >= chip[0] && mx < chip[0] + chip[2], alpha);
        }
        int taken = chip == null ? 0 : chip[2] + 8;
        int[] box = checkChip(o, x, y, w, h);
        if (box != null) {
            boolean over = hover && mx >= box[0] - 2 && mx < box[0] + box[2] + 2;
            checkChipDraw(o.check(), box, over, alpha);
            taken += box[2] + 6;
            if (over) {
                // The checkbox's hint, to its left, in place of the end of the name
                String hint = o.check().hint();
                int hw = Ui.monoWidth(hint);
                Draw2D.rect(
                        box[0] - hw - 10,
                        box[1] - 1,
                        hw + 6,
                        box[3] + 2,
                        Ui.alpha(Theme.BG_POPUP, alpha));
                Ui.mono(hint, box[0] - hw - 7, box[1] + 1, Ui.alpha(Theme.INK, alpha));
            }
        }
        return taken;
    }

    /** A pick-a-value option: the name, and the value between the arrows. */
    private static void valueRow(
            Option o, Row row, int mx, boolean hover, int name, int taken, float alpha) {
        int x = row.x();
        int y = row.y();
        int w = row.w();
        int h = row.h();
        String value = o.value();
        int vw = Ui.monoWidth(value);
        // Pick-a-value option: the value sits between the two arrows, inside the row (the right one
        // does not leave the edge).
        int right = o.isStepped() ? x + w - ARROW : x + w;
        Ui.text(Ui.trim(o.label, w - vw - 30 - taken), x, y + 7, name);
        // The color already comes with the code in the value itself (Option.color), so the name
        // comes out painted by itself.
        Ui.mono(value, right - vw, y + 7, Ui.alpha(Theme.GOLD, alpha));
        if (hover && o.isStepped()) {
            // Clicking the row advances; the left arrow goes back.
            int[] a = arrows(o, x, w);
            Icons.chevron(
                    a[0],
                    y + h / 2 - 1,
                    7.0F,
                    90.0F,
                    Ui.alpha(mx < a[0] + 6 && mx > a[0] - 6 ? Theme.INK : Theme.MUTED, alpha));
            Icons.chevron(
                    a[1],
                    y + h / 2 - 1,
                    7.0F,
                    -90.0F,
                    Ui.alpha(mx < a[1] + 6 && mx > a[1] - 6 ? Theme.INK : Theme.MUTED, alpha));
        }
    }

    /**
     * Where the key label of an option ({@link Option#withKey}) sits in a row (x, y, w, h): left,
     * top, width and height; null without a key. The same math for drawing and for the click.
     */
    public static int[] keyChip(Option o, int x, int y, int w, int h) {
        Option.Key k = o.key();
        if (k == null) {
            return null;
        }
        // Square (the text line's height); a key with a long name widens, without turning into a
        // pill.
        int cw = Math.max(10, Ui.monoWidth(chipText(k)) + 6);
        // Before the control: the toggle switch (20 from the edge) or the value (its width).
        int right =
                o.state() != null
                        ? x + w - 24
                        : x + w - Ui.monoWidth(o.value()) - 10 - (o.isStepped() ? ARROW : 0);
        // Centered on the row, at the same height as the toggle switch.
        return new int[] {right - cw, y + h / 2 - 5, cw, 10};
    }

    /**
     * Where the checkbox of an option ({@link Option#withCheck}) sits: to the left of the key
     * label, or in its place when there is no key. Left, top, width and height; null without a
     * checkbox. The same math for drawing and clicking.
     */
    public static int[] checkChip(Option o, int x, int y, int w, int h) {
        if (o.check() == null) {
            return null;
        }
        int[] key = keyChip(o, x, y, w, h);
        int right =
                key != null
                        ? key[0] - 4
                        : o.state() != null
                                ? x + w - 24
                                : x
                                        + w
                                        - Ui.monoWidth(o.value())
                                        - 10
                                        - (o.isStepped() ? ARROW : 0);
        return new int[] {right - 10, y + h / 2 - 5, 10, 10};
    }

    /**
     * The checkbox: the same square as the key label; checked, in the accent and with the check
     * mark.
     */
    private static void checkChipDraw(Option.Check c, int[] r, boolean hover, float alpha) {
        boolean on = c.isOn();
        int border = on ? Theme.ACCENT : hover ? Theme.GOLD : Theme.RULE_STRONG;
        Shapes.fill(
                r[0],
                r[1],
                r[2],
                r[3],
                2.0F,
                Ui.alpha(on ? Theme.ACCENT_SOFT : hover ? SURFACE_HOVER : Theme.FIELD, alpha));
        Shapes.stroke(r[0], r[1], r[2], r[3], 2.0F, 1, Ui.alpha(border, alpha));
        if (on) {
            Icons.draw(
                    "tick",
                    r[0] + r[2] / 2.0F,
                    r[1] + r[3] / 2.0F,
                    8.0F,
                    Ui.alpha(Theme.INK, alpha));
        }
    }

    /** What the label shows: the key, "..." while waiting, or "+" with no key. */
    private static String chipText(Option.Key k) {
        return !k.isListening() && k.value().startsWith("§7") ? "+" : keyText(k);
    }

    private static String keyText(Option.Key k) {
        if (!k.isListening() && k.code() < 0) {
            return "M" + (k.code() + 101); // mouse button, short on the label
        }
        String v = k.value();
        // The key without the value's color codes ("§7None", "§e> ... <"): the label has its own
        // colors.
        v = v.replaceAll("§.", "");
        if (k.isListening()) {
            return "...";
        }
        return v;
    }

    /**
     * The label: square, with an outline and a corner of 2; with no key, a discreet "+"; waiting,
     * in the accent.
     */
    private static void keyChipDraw(Option.Key k, int[] r, boolean hover, float alpha) {
        boolean none = !k.isListening() && k.value().startsWith("§7");
        boolean hot = k.isListening() || hover;
        int border = k.isListening() ? Theme.ACCENT : hover ? Theme.GOLD : Theme.RULE_STRONG;
        Shapes.fill(
                r[0], r[1], r[2], r[3], 2.0F, Ui.alpha(hot ? SURFACE_HOVER : Theme.FIELD, alpha));
        Shapes.stroke(r[0], r[1], r[2], r[3], 2.0F, 1, Ui.alpha(border, alpha));
        String t = chipText(k);
        int color =
                k.isListening() ? Theme.INK : none ? Theme.FAINT : hover ? Theme.GOLD : Theme.MUTED;
        Ui.monoCentered(t, r[0] + r[2] / 2.0F, r[1] + r[3] / 2.0F, Ui.alpha(color, alpha));
    }

    /** Space reserved at the right of the value for the advance arrow. */
    private static final int ARROW = 10;

    /** The center of the back and advance arrows of a pick-a-value option, in a row (x, w). */
    public static int[] arrows(Option o, int x, int w) {
        int vw = Ui.monoWidth(o.value());
        return new int[] {x + w - ARROW - vw - 7, x + w - ARROW / 2};
    }

    /** Did the click land on the back arrow? (the rest of the row advances) */
    public static boolean backArrow(Option o, int x, int w, int mx) {
        return o.isStepped() && Math.abs(mx - arrows(o, x, w)[0]) <= 6;
    }

    /**
     * The Option.Slider being dragged right now (the screen doing the dragging sets and clears it):
     * its knob is highlighted.
     */
    public static Option.Slider sliding;

    /**
     * The design system slider, the same on every screen: a thin pill track, the part up to the
     * value in the accent and the round knob on top. Under the cursor the knob grows and, while
     * dragging, gets the accent halo. The knob slides to the new value (a click on the track does
     * not make it jump).
     *
     * @param x start of the track; {@code cy} its middle line; {@code w} the width
     * @param fraction where the value is, from 0 to 1
     */
    public static void slider(
            String key,
            float x,
            float cy,
            float w,
            float fraction,
            boolean hover,
            boolean active,
            float alpha,
            long delta) {
        float f = Ui.clamp01(fraction);
        float at = knobs.getOrDefault(key, f);
        // While dragging, the knob follows the mouse right away; on a click, it slides.
        at = active ? f : Ui.towards(at, f, 14.0F, delta);
        knobs.put(key, at);
        String hotKey = key + "|hot";
        float hot = knobs.getOrDefault(hotKey, 0.0F);
        hot = Ui.towards(hot, active ? 1.0F : hover ? 0.6F : 0.0F, 10.0F, delta);
        knobs.put(hotKey, hot);

        float th = 3.0F;
        float ty = cy - th / 2;
        Shapes.fill(x, ty, w, th, th / 2, Ui.alpha(Theme.RULE_STRONG, alpha));
        float kx = x + w * at;
        if (kx - x >= th) {
            Shapes.fill(x, ty, kx - x, th, th / 2, Ui.alpha(Theme.ACCENT, alpha));
        }
        if (hot > 0.01F) {
            Ui.circle(kx, cy, 5.0F + 3.0F * hot, Ui.alpha(Theme.ACCENT_SOFT, alpha * hot * 2.0F));
        }
        float r = 3.5F + 1.0F * hot;
        Ui.circle(kx, cy, r, Ui.alpha(Theme.INK, alpha));
        Ui.ring(
                kx,
                cy,
                r,
                1.0F / Ui.scale(),
                Ui.alpha(Theme.ACCENT, alpha * (0.35F + 0.65F * hot)));
    }

    /**
     * The on/off switch, the same on every screen: the design system pill, with the dot moving from
     * one side to the other. Off, just the outline; on, the accent. The dot slides instead of
     * jumping — the movement is what shows that this click changed that.
     */
    public static void knob(String key, float x, float y, boolean on, float alpha, long delta) {
        knob(key, x, y, on, ON, alpha, delta);
    }

    /**
     * The same switch, with another "on" color: the Loader menu paints a mod that is enabled but
     * did not load in red, so the row does not say "active" in the same color as one that is fine.
     */
    public static void knob(
            String key, float x, float y, boolean on, int onColor, float alpha, long delta) {
        float at = knobs.getOrDefault(key, on ? 1.0F : 0.0F);
        at = Ui.towards(at, on ? 1.0F : 0.0F, 8.0F, delta);
        knobs.put(key, at);
        float r = KNOB_H / 2.0F;
        Shapes.fill(x, y, KNOB_W, KNOB_H, r, Ui.alpha(onColor, alpha * 0.18F * at));
        int border = Ui.mix(Theme.RULE_STRONG | 0xFF000000, onColor, at);
        Shapes.stroke(x, y, KNOB_W, KNOB_H, r, 1, Ui.alpha(border, alpha * (0.4F + 0.6F * at)));
        int dot = Ui.mix(Theme.MUTED | 0xFF000000, onColor, at);
        Ui.circle(x + r + at * (KNOB_W - KNOB_H), y + r, r - 2.0F, Ui.alpha(dot, alpha));
    }

    /**
     * The "Restore defaults": icon and name, discreet. The first click asks (the {@link
     * Option#restore} itself returns the question as a value), the second does it.
     */
    public static void restore(Option o, int x, int y, int w, int h, boolean hover, float alpha) {
        String value = o.value();
        boolean asking = !value.isEmpty();
        int color = asking ? Theme.WARN : hover ? Theme.GOLD : Theme.MUTED;
        Icons.restore(x + 6, y + h / 2 - 1, 10.0F, Ui.alpha(color, alpha));
        String label = asking ? value : I18n.format("steinloader.restore");
        Ui.label(label, x + 16, y + 7, Ui.alpha(color, alpha));
    }

    /** The design system button: a thin-outlined pill, with the label in capitals. */
    public static void button(
            String label, int x, int y, int w, int h, boolean hover, boolean off, float alpha) {
        pill(label, x, y, w, h, hover, off, false, alpha);
    }

    /** The screen's main action button: the filled pill, in the accent. */
    public static void primary(
            String label, int x, int y, int w, int h, boolean hover, boolean off, float alpha) {
        pill(label, x, y, w, h, hover, off, true, alpha);
    }

    private static void pill(
            String label,
            int x,
            int y,
            int w,
            int h,
            boolean hover,
            boolean off,
            boolean primary,
            float alpha) {
        float r = h / 2.0F;
        int ink;
        if (primary && !off) {
            Ui.roundRect(x, y, w, h, r, Ui.alpha(hover ? 0xFF8D63FF : Theme.ACCENT, alpha));
            ink = Theme.ON_ACCENT;
        } else {
            int border = off ? Theme.RULE : hover ? Theme.GOLD : Theme.RULE_STRONG;
            if (hover && !off) {
                Shapes.fill(x, y, w, h, r, Ui.alpha(Theme.GOLD_FAINT, alpha));
            }
            Shapes.stroke(x, y, w, h, r, 1, Ui.alpha(border, alpha));
            ink = off ? Theme.FAINT : hover ? Theme.GOLD : Theme.INK;
        }
        String s = trimLabel(label, w - 12);
        Ui.label(s, x + (w - Ui.labelWidth(s)) / 2, y + (h - 8) / 2, Ui.alpha(ink, alpha));
    }

    private static String trimLabel(String s, int w) {
        while (s.length() > 1 && Ui.labelWidth(s) > w) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }
}
