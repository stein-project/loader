/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.loader.gui;

import dev.xavier.stein.bridge.showcase.VideoSettings;

import net.minecraft.client.Minecraft;

import java.util.List;

/**
 * The drawing of {@link VideoScreen}: the panel (header, the row of topics, the list, the hint),
 * the controls over the landscape and the note about the server. Every piece registers where it was
 * drawn in the {@link VideoLayout}, for the clicks.
 */
final class VideoDrawing {

    /**
     * The panel: the field, translucent, so the landscape shows through without losing the text.
     */
    private static final int PANEL = 0xC80A0614;

    /** A slider's value, right-aligned in a column this wide: the track does not move with it. */
    private static final int VALUE_W = 46;

    private VideoDrawing() {}

    static void panel(
            VideoScreen screen,
            VideoLayout l,
            List<VideoSettings.Section> sections,
            int topic,
            int mx,
            int my,
            VideoSettings.Entry dragging,
            double dragAt,
            float alpha,
            long delta) {
        if (alpha <= 0.01F) {
            return;
        }
        Shapes.fill(l.px, l.py, l.pw, l.ph, 8, Ui.alpha(PANEL, alpha));
        Shapes.stroke(l.px, l.py, l.pw, l.ph, 8, 1, Ui.alpha(Theme.RULE, alpha));
        Ui.title(
                VideoScreen.tr("steinloader.video.title"),
                l.contentX,
                l.py + 8,
                12.0F,
                Ui.alpha(Theme.INK, alpha));
        int bottom = topics(l, sections, topic, mx, my, alpha);
        Ui.hair(l.px + 10, bottom + 5, l.pw - 20, Ui.alpha(Theme.RULE, alpha));
        l.listTop = bottom + 9;
        if (topic >= sections.size()) {
            return;
        }
        VideoSettings.Section s = sections.get(topic);
        String hint = list(l, s, mx, my, dragging, dragAt, alpha, delta);
        hint(l, hint, alpha);
    }

    /**
     * The topics in one row of icons, under it the name of the chosen one (or of the one under the
     * mouse). Returns where the name ends.
     */
    private static int topics(
            VideoLayout l,
            List<VideoSettings.Section> sections,
            int topic,
            int mx,
            int my,
            float alpha) {
        int n = Math.max(1, sections.size());
        int step = Math.min(22, l.contentW / n);
        int y = l.py + VideoLayout.HEADER;
        int h = VideoLayout.CHIP_H + 5;
        int shown = topic;
        for (int i = 0; i < sections.size(); i++) {
            int x = l.contentX + i * step;
            boolean hover = mx >= x && mx < x + step && my >= y && my < y + h;
            boolean on = i == topic;
            if (hover) {
                shown = i;
            }
            if (on || hover) {
                Shapes.fill(
                        x + 1,
                        y,
                        step - 2,
                        h,
                        5,
                        Ui.alpha(on ? Theme.ACCENT_SOFT : Theme.FIELD, alpha));
            }
            int color = on ? Theme.ACCENT : hover ? Theme.INK : Theme.MUTED;
            Icons.draw(
                    icon(sections.get(i).id),
                    x + step / 2.0F,
                    y + h / 2.0F,
                    11.0F,
                    Ui.alpha(color, alpha));
            l.add(VideoLayout.Kind.TOPIC, x, y, step, h).index = i;
        }
        if (shown < sections.size()) {
            Ui.label(
                    sections.get(shown).title.get(),
                    l.contentX,
                    y + h + 6,
                    Ui.alpha(shown == topic ? Theme.GOLD : Theme.INK_SOFT, alpha));
        }
        return y + h + 14;
    }

    /** The topic's options, one line each; returns the hint of the one under the mouse. */
    private static String list(
            VideoLayout l,
            VideoSettings.Section s,
            int mx,
            int my,
            VideoSettings.Entry dragging,
            double dragAt,
            float alpha,
            long delta) {
        int top = l.listTop;
        int fit = Math.max(1, (l.listBottom - top) / VideoLayout.ROW);
        l.rows(s.entries.size(), fit);
        String hint = "";
        int y = top;
        int end = Math.min(s.entries.size(), l.scroll() + fit);
        for (int i = l.scroll(); i < end; i++) {
            VideoSettings.Entry e = s.entries.get(i);
            int h = VideoLayout.ROW;
            boolean hover =
                    mx >= l.contentX - 4
                            && mx < l.contentX + l.contentW + 4
                            && my >= y
                            && my < y + h;
            if (hover || e == dragging) {
                hint = e.hint.get();
            }
            row(l, e, y, h, hover, e == dragging, dragAt, alpha, delta);
            y += h;
        }
        // more above or below: a thin bar on the panel's edge says how much and where
        if (s.entries.size() > fit) {
            int track = l.listBottom - top;
            int bar = Math.max(8, track * fit / s.entries.size());
            int at = (track - bar) * l.scroll() / Math.max(1, s.entries.size() - fit);
            Shapes.fill(l.px + l.pw - 5, top, 2, track, 1, Ui.alpha(Theme.RULE, alpha));
            Shapes.fill(l.px + l.pw - 5, top + at, 2, bar, 1, Ui.alpha(Theme.MUTED, alpha));
        }
        return hint;
    }

    /** One option in one line: its name on the left, its control on the right. */
    private static void row(
            VideoLayout l,
            VideoSettings.Entry e,
            int y,
            int h,
            boolean hover,
            boolean active,
            double dragAt,
            float alpha,
            long delta) {
        boolean enabled = e.enabled.getAsBoolean();
        if (hover && enabled && e.kind != VideoSettings.Kind.INFO) {
            Shapes.fill(l.contentX - 4, y, l.contentW + 8, h, 4, Ui.alpha(Theme.FIELD, alpha));
        }
        int ink = enabled || e.kind == VideoSettings.Kind.INFO ? Theme.INK_SOFT : Theme.FAINT;
        int right = l.contentX + l.contentW;
        int ty = y + 4;
        String key = "video|" + System.identityHashCode(e);
        VideoLayout.Hit hit = l.add(VideoLayout.Kind.ENTRY, l.contentX - 4, y, l.contentW + 8, h);
        hit.entry = e;
        String label = e.label.get();
        if (e.kind == VideoSettings.Kind.SLIDER) {
            double f = active ? dragAt : e.fraction.getAsDouble();
            String value = Ui.trim(e.value.get(), VALUE_W);
            int tw = Math.min(56, l.contentW / 4);
            int tx = right - VALUE_W - 6 - tw;
            Ui.text(Ui.trim(label, tx - 8 - l.contentX), l.contentX, ty, Ui.alpha(ink, alpha));
            Widgets.slider(
                    key,
                    tx,
                    y + h / 2.0F,
                    tw,
                    (float) f,
                    hover,
                    active,
                    alpha * (enabled ? 1.0F : 0.4F),
                    delta);
            Ui.text(
                    value,
                    right - Ui.width(value),
                    ty,
                    Ui.alpha(enabled ? Theme.GOLD : Theme.FAINT, alpha));
            hit.trackX = tx;
            hit.trackW = tw;
            return;
        }
        if (e.state != null) {
            Ui.text(
                    Ui.trim(label, l.contentW - Widgets.KNOB_W - 8),
                    l.contentX,
                    ty,
                    Ui.alpha(ink, alpha));
            Widgets.knob(
                    key,
                    right - Widgets.KNOB_W,
                    y + (h - Widgets.KNOB_H) / 2.0F,
                    e.state.getAsBoolean(),
                    alpha * (enabled ? 1.0F : 0.4F),
                    delta);
            return;
        }
        String value = e.value.get();
        if (e.kind == VideoSettings.Kind.INFO) {
            Ui.text(
                    Ui.trim(label, l.contentW - Ui.width(value) - 8),
                    l.contentX,
                    ty,
                    Ui.alpha(ink, alpha));
            Ui.text(value, right - Ui.width(value), ty, Ui.alpha(Theme.MUTED, alpha));
            return;
        }
        // a choice: "< value >", the arrows step back and forward
        int vw = Ui.width(value);
        int valueX = right - 10 - vw;
        int arrowL = valueX - 9;
        Ui.text(Ui.trim(label, arrowL - l.contentX - 6), l.contentX, ty, Ui.alpha(ink, alpha));
        int color = !enabled ? Theme.FAINT : hover ? Theme.GOLD : Theme.INK;
        Icons.chevron(
                arrowL + 3,
                y + h / 2.0F,
                7.0F,
                90.0F,
                Ui.alpha(enabled ? Theme.MUTED : Theme.FAINT, alpha));
        Ui.text(value, valueX, ty, Ui.alpha(color, alpha));
        Icons.chevron(
                right - 4,
                y + h / 2.0F,
                7.0F,
                -90.0F,
                Ui.alpha(enabled ? Theme.MUTED : Theme.FAINT, alpha));
        hit.backEdge = valueX - 2;
    }

    /** What the option under the mouse does, under the list. */
    private static void hint(VideoLayout l, String hint, float alpha) {
        int top = l.listBottom + 4;
        Ui.hair(l.px + 10, top, l.pw - 20, Ui.alpha(Theme.RULE, alpha));
        if (hint == null || hint.isEmpty()) {
            List<String> idle = Ui.wrap(VideoScreen.tr("steinloader.video.hintIdle"), l.contentW);
            for (int i = 0; i < Math.min(2, idle.size()); i++) {
                Ui.text(idle.get(i), l.contentX, top + 5 + i * 10, Ui.alpha(Theme.FAINT, alpha));
            }
            return;
        }
        // the hint in the reading font: a sentence, not a label
        List<String> lines = Ui.wrap(hint, l.contentW);
        for (int i = 0; i < Math.min(3, lines.size()); i++) {
            Ui.text(lines.get(i), l.contentX, top + 5 + i * 10, Ui.alpha(Theme.MUTED, alpha));
        }
    }

    /** Over the landscape, top left (the panel is on the right): day, night, and hide the menu. */
    static void controls(
            VideoLayout l, int mx, int my, boolean night, boolean landscape, float alpha) {
        if (!landscape || alpha <= 0.01F) {
            return;
        }
        int y = VideoLayout.MARGIN;
        int x = VideoLayout.MARGIN;
        int dayW = pillWidth("sun", VideoScreen.tr("steinloader.video.day"));
        pill(
                l,
                VideoLayout.Kind.DAY,
                "sun",
                VideoScreen.tr("steinloader.video.day"),
                x,
                y,
                dayW,
                !night,
                mx,
                my,
                alpha);
        x += dayW + 4;
        int nightW = pillWidth("moon", VideoScreen.tr("steinloader.video.night"));
        pill(
                l,
                VideoLayout.Kind.NIGHT,
                "moon",
                VideoScreen.tr("steinloader.video.night"),
                x,
                y,
                nightW,
                night,
                mx,
                my,
                alpha);
        x += nightW + 6;
        int hideW = pillWidth("eyeOff", VideoScreen.tr("steinloader.video.hide"));
        pill(
                l,
                VideoLayout.Kind.HIDE,
                "eyeOff",
                VideoScreen.tr("steinloader.video.hide"),
                x,
                y,
                hideW,
                false,
                mx,
                my,
                alpha);
    }

    private static int pillWidth(String icon, String label) {
        return 24 + Ui.labelWidth(label) + 10;
    }

    private static void pill(
            VideoLayout l,
            VideoLayout.Kind kind,
            String icon,
            String label,
            int x,
            int y,
            int w,
            boolean on,
            int mx,
            int my,
            float alpha) {
        int h = 18;
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + h;
        Shapes.fill(x, y, w, h, h / 2.0F, Ui.alpha(PANEL, alpha));
        if (on) {
            Shapes.fill(x, y, w, h, h / 2.0F, Ui.alpha(Theme.ACCENT_SOFT, alpha));
        }
        int border = on ? Theme.ACCENT : hover ? Theme.GOLD : Theme.RULE_STRONG;
        Shapes.stroke(x, y, w, h, h / 2.0F, 1, Ui.alpha(border, alpha));
        int ink = on ? Theme.INK : hover ? Theme.GOLD : Theme.INK_SOFT;
        Icons.draw(icon, x + 12, y + h / 2.0F, 10.0F, Ui.alpha(on ? Theme.ACCENT : ink, alpha));
        Ui.label(label, x + 21, y + 5, Ui.alpha(ink, alpha));
        l.add(kind, x, y, w, h);
    }

    /**
     * Inside a server: the player is still there (the game does not pause), and while they take
     * damage the note says so in red.
     */
    static void server(Minecraft mc, VideoLayout l, float alpha, long now) {
        if (mc.theWorld == null || mc.isSingleplayer() || mc.thePlayer == null || alpha <= 0.01F) {
            return;
        }
        boolean hurt = mc.thePlayer.hurtTime > 0;
        String text = VideoScreen.tr(hurt ? "steinloader.video.hurt" : "steinloader.video.server");
        int w = Ui.labelWidth(text) + 30;
        int h = 18;
        int sh = mc.currentScreen == null ? 0 : mc.currentScreen.height;
        int x = VideoLayout.MARGIN;
        int y = sh - VideoLayout.MARGIN - h;
        float pulse = hurt ? 0.6F + 0.4F * (float) Math.sin(now / 90.0) : 1.0F;
        Shapes.fill(x, y, w, h, h / 2.0F, Ui.alpha(PANEL, alpha));
        Shapes.stroke(
                x,
                y,
                w,
                h,
                h / 2.0F,
                1,
                Ui.alpha(hurt ? Theme.DANGER : Theme.RULE_STRONG, alpha * pulse));
        Icons.draw(
                hurt ? "heart" : "ping",
                x + 12,
                y + h / 2.0F,
                10.0F,
                Ui.alpha(hurt ? Theme.DANGER : Theme.GOLD, alpha));
        Ui.label(text, x + 21, y + 5, Ui.alpha(hurt ? Theme.DANGER : Theme.INK_SOFT, alpha));
    }

    /** With the menu hidden: how to bring it back, fading to a whisper after a few seconds. */
    static void hiddenNote(int width, int height, float hide, long now) {
        String text = VideoScreen.tr("steinloader.video.show");
        int w = Ui.labelWidth(text) + 20;
        int h = 16;
        int x = (width - w) / 2;
        int y = height - VideoLayout.MARGIN - h;
        float a = hide * 0.75F;
        Shapes.fill(x, y, w, h, h / 2.0F, Ui.alpha(PANEL, a));
        Ui.label(text, x + 10, y + 4, Ui.alpha(Theme.INK_SOFT, a));
    }

    /** The icon of each topic. */
    private static String icon(String id) {
        return switch (id) {
            case "general" -> "sliders";
            case "camera" -> "camera";
            case "light" -> "bulb";
            case "image" -> "sparkle";
            case "sky" -> "cloud";
            case "textures" -> "grid";
            case "players" -> "users";
            case "animations" -> "sword";
            case "performance" -> "gauge";
            default -> "gear";
        };
    }
}
