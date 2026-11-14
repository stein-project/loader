/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */
package dev.xavier.stein.loader.gui;

import dev.xavier.stein.loader.api.Option;
import dev.xavier.stein.ui.Draw2D;
import dev.xavier.stein.ui.Icons;
import dev.xavier.stein.ui.Shapes;
import dev.xavier.stein.ui.Theme;
import dev.xavier.stein.ui.Ui;
import dev.xavier.stein.ui.Widgets;

import net.minecraft.client.resources.I18n;

/**
 * The row of a mod's {@link Option} on the Loader's screens (the menu, the Panel, the screen
 * editor): name on the left, control on the right, and the key label, the checkbox and "Restore
 * defaults" when the option has them. Drawn with the design system's pieces ({@link Widgets}).
 */
public final class OptionRows {

    private OptionRows() {}

    /**
     * An option row: name on the left, control on the right. A sliding switch for the two-state
     * ones, a value with arrows for the cycling ones. Returns the height used.
     */
    public static int option(
            Option o, String key, Widgets.Row row, Widgets.Pointer ptr, float alpha, long delta) {
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
            Draw2D.rect(x - 6, y, w + 12, h - 2, Ui.alpha(Widgets.SURFACE_HOVER, alpha));
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
            Widgets.knob(key + "|" + o.label, x + w - 20, y + h / 2 - 4, state, alpha, delta);
            return h;
        }
        valueRow(o, row, mx, hover, name, taken, alpha);
        return h;
    }

    /**
     * Name on the left, value and track on the right: the track is where you drag (Widgets.TRACK_W
     * wide).
     */
    private static void sliderRow(
            Option.Slider s,
            String key,
            Widgets.Row row,
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
        int tx = x + w - Widgets.TRACK_W;
        Ui.text(Ui.trim(s.label, w - Widgets.TRACK_W - vw - 16), x, y + 7, name);
        Ui.mono(value, tx - vw - 8, y + 7, Ui.alpha(Theme.GOLD, alpha));
        boolean overTrack = hover && mx >= tx - 4;
        Widgets.slider(
                key + "|" + s.label,
                tx,
                y + h / 2.0F,
                Widgets.TRACK_W,
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
    private static int chips(Option o, Widgets.Row row, int mx, boolean hover, float alpha) {
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
            Option o, Widgets.Row row, int mx, boolean hover, int name, int taken, float alpha) {
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
                Ui.alpha(
                        on ? Theme.ACCENT_SOFT : hover ? Widgets.SURFACE_HOVER : Theme.FIELD,
                        alpha));
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
                r[0],
                r[1],
                r[2],
                r[3],
                2.0F,
                Ui.alpha(hot ? Widgets.SURFACE_HOVER : Theme.FIELD, alpha));
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
}
