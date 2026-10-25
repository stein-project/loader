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
 * The drawing of {@link VideoScreen}: the panel (header, rail of topics, the list, the hint), the
 * controls over the landscape and the note about the server. Every piece registers where it was
 * drawn in the {@link VideoLayout}, for the clicks.
 */
final class VideoDrawing {

    /**
     * The panel: the field, translucent, so the landscape shows through without losing the text.
     */
    private static final int PANEL = 0xC80A0614;

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
                l.px + 14,
                l.py + 12,
                13.0F,
                Ui.alpha(Theme.INK, alpha));
        Ui.label(
                VideoScreen.tr("steinloader.video.subtitle"),
                l.px + 14,
                l.py + 30,
                Ui.alpha(Theme.MUTED, alpha));
        Ui.hair(l.px + 10, l.py + VideoLayout.HEADER + 6, l.pw - 20, Ui.alpha(Theme.RULE, alpha));
        rail(l, sections, topic, mx, my, alpha);
        if (topic >= sections.size()) {
            return;
        }
        VideoSettings.Section s = sections.get(topic);
        String hint = list(l, s, mx, my, dragging, dragAt, alpha, delta);
        hint(l, hint, alpha);
    }

    /** The topics, one icon each; the chosen one in the accent. */
    private static void rail(
            VideoLayout l,
            List<VideoSettings.Section> sections,
            int topic,
            int mx,
            int my,
            float alpha) {
        int y = l.listTop - 4;
        // as tall as the list allows: every topic stays in view
        int step = Math.max(18, Math.min(26, (l.listBottom - y) / Math.max(1, sections.size())));
        int half = Math.min(11, step / 2);
        for (int i = 0; i < sections.size(); i++) {
            int cy = y + i * step + step / 2;
            int cx = l.railX + VideoLayout.RAIL / 2;
            boolean hover =
                    mx >= l.railX
                            && mx < l.railX + VideoLayout.RAIL
                            && my >= cy - half
                            && my < cy + half;
            boolean on = i == topic;
            if (on || hover) {
                Shapes.fill(
                        l.railX,
                        cy - half,
                        VideoLayout.RAIL,
                        2 * half,
                        6,
                        Ui.alpha(on ? Theme.ACCENT_SOFT : Theme.FIELD, alpha));
            }
            int color = on ? Theme.ACCENT : hover ? Theme.INK : Theme.MUTED;
            Icons.draw(icon(sections.get(i).id), cx, cy, 12.0F, Ui.alpha(color, alpha));
            l.add(VideoLayout.Kind.TOPIC, l.railX, cy - half, VideoLayout.RAIL, 2 * half).index = i;
        }
        Ui.hairV(
                l.contentX - 6,
                l.listTop - 4,
                l.listBottom - l.listTop,
                Ui.alpha(Theme.RULE, alpha));
    }

    /** The topic's options; returns the hint of the one under the mouse. */
    private static String list(
            VideoLayout l,
            VideoSettings.Section s,
            int mx,
            int my,
            VideoSettings.Entry dragging,
            double dragAt,
            float alpha,
            long delta) {
        Ui.label(
                s.title.get().toUpperCase(java.util.Locale.ROOT),
                l.contentX,
                l.listTop - 2,
                Ui.alpha(Theme.GOLD, alpha));
        int top = l.listTop + 12;
        int fit = 0;
        for (int h = 0;
                fit < s.entries.size() && top + h + VideoLayout.SLIDER_ROW <= l.listBottom;
                fit++) {
            h += height(s.entries.get(fit));
        }
        l.rows(s.entries.size(), Math.max(1, fit));
        String hint = "";
        int y = top;
        for (int i = l.scroll(); i < s.entries.size(); i++) {
            VideoSettings.Entry e = s.entries.get(i);
            int h = height(e);
            if (y + h > l.listBottom) {
                break;
            }
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
        if (l.scroll() > 0 || y < top + rowsHeight(s)) {
            // there is more above or below: a faint edge says so
            if (l.scroll() > 0) {
                Ui.hair(l.contentX, top - 2, l.contentW, Ui.alpha(Theme.RULE_STRONG, alpha));
            }
            if (y < l.listBottom && l.scroll() + countShown(l, s, top) < s.entries.size()) {
                Ui.hair(
                        l.contentX,
                        l.listBottom - 2,
                        l.contentW,
                        Ui.alpha(Theme.RULE_STRONG, alpha));
            }
        }
        return hint;
    }

    private static int countShown(VideoLayout l, VideoSettings.Section s, int top) {
        int y = top;
        int n = 0;
        for (int i = l.scroll(); i < s.entries.size(); i++) {
            int h = height(s.entries.get(i));
            if (y + h > l.listBottom) {
                break;
            }
            y += h;
            n++;
        }
        return n;
    }

    private static int rowsHeight(VideoSettings.Section s) {
        int h = 0;
        for (VideoSettings.Entry e : s.entries) {
            h += height(e);
        }
        return h;
    }

    private static int height(VideoSettings.Entry e) {
        return e.kind == VideoSettings.Kind.SLIDER ? VideoLayout.SLIDER_ROW : VideoLayout.ROW;
    }

    /** One option: its name on the left, its control on the right (or under it, for a track). */
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
            Shapes.fill(
                    l.contentX - 4, y + 1, l.contentW + 8, h - 2, 5, Ui.alpha(Theme.FIELD, alpha));
        }
        int ink = enabled || e.kind == VideoSettings.Kind.INFO ? Theme.INK_SOFT : Theme.FAINT;
        int right = l.contentX + l.contentW;
        String key = "video|" + System.identityHashCode(e);
        VideoLayout.Hit hit = l.add(VideoLayout.Kind.ENTRY, l.contentX - 4, y, l.contentW + 8, h);
        hit.entry = e;
        String label = e.label.get();
        if (e.kind == VideoSettings.Kind.SLIDER) {
            double f = active ? dragAt : e.fraction.getAsDouble();
            String value = e.value.get();
            Ui.text(
                    Ui.trim(label, l.contentW - Ui.width(value) - 8),
                    l.contentX,
                    y + 4,
                    Ui.alpha(ink, alpha));
            Ui.text(
                    value,
                    right - Ui.width(value),
                    y + 4,
                    Ui.alpha(enabled ? Theme.GOLD : Theme.FAINT, alpha));
            Widgets.slider(
                    key,
                    l.contentX,
                    y + h - 7,
                    l.contentW,
                    (float) f,
                    hover,
                    active,
                    alpha * (enabled ? 1.0F : 0.4F),
                    delta);
            return;
        }
        if (e.state != null) {
            Ui.text(
                    Ui.trim(label, l.contentW - Widgets.KNOB_W - 8),
                    l.contentX,
                    y + 6,
                    Ui.alpha(ink, alpha));
            Widgets.knob(
                    key,
                    right - Widgets.KNOB_W,
                    y + 6,
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
                    y + 6,
                    Ui.alpha(ink, alpha));
            Ui.text(value, right - Ui.width(value), y + 6, Ui.alpha(Theme.MUTED, alpha));
            return;
        }
        // a choice: "< value >", the arrows step back and forward
        int vw = Ui.width(value);
        int valueX = right - 10 - vw;
        int arrowL = valueX - 9;
        Ui.text(Ui.trim(label, arrowL - l.contentX - 6), l.contentX, y + 6, Ui.alpha(ink, alpha));
        int color = !enabled ? Theme.FAINT : hover ? Theme.GOLD : Theme.INK;
        Icons.chevron(
                arrowL + 3,
                y + h / 2.0F,
                7.0F,
                90.0F,
                Ui.alpha(enabled ? Theme.MUTED : Theme.FAINT, alpha));
        Ui.text(value, valueX, y + 6, Ui.alpha(color, alpha));
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
            Ui.label(
                    VideoScreen.tr("steinloader.video.hintIdle"),
                    l.px + 14,
                    top + 8,
                    Ui.alpha(Theme.FAINT, alpha));
            return;
        }
        List<String> lines = Ui.wrap(hint, l.pw - 28);
        for (int i = 0; i < Math.min(3, lines.size()); i++) {
            Ui.label(lines.get(i), l.px + 14, top + 7 + i * 9, Ui.alpha(Theme.MUTED, alpha));
        }
    }

    /** Over the landscape, top right: day, night, and hide the menu. */
    static void controls(
            VideoLayout l, int mx, int my, boolean night, boolean landscape, float alpha) {
        if (!landscape || alpha <= 0.01F) {
            return;
        }
        int w = Ui.mc().currentScreen == null ? 0 : Ui.mc().currentScreen.width;
        int y = VideoLayout.MARGIN;
        int hideW = pillWidth("eyeOff", VideoScreen.tr("steinloader.video.hide"));
        int x = w - VideoLayout.MARGIN - hideW;
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
        int nightW = pillWidth("moon", VideoScreen.tr("steinloader.video.night"));
        x -= 6 + nightW;
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
        int dayW = pillWidth("sun", VideoScreen.tr("steinloader.video.day"));
        x -= 4 + dayW;
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
        int sw = mc.currentScreen == null ? 0 : mc.currentScreen.width;
        int sh = mc.currentScreen == null ? 0 : mc.currentScreen.height;
        int x = sw - VideoLayout.MARGIN - w;
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
