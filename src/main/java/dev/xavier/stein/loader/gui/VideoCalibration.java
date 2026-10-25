/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.loader.gui;

import dev.xavier.stein.bridge.showcase.Showcase;
import dev.xavier.stein.bridge.showcase.VideoSettings;
import dev.xavier.stein.gfx.SteinHdr;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;

import java.util.List;

/**
 * The HDR calibration, inside the video settings: a card at the bottom of the screen, three steps
 * in plain words, over the same landscape.
 *
 * <ol>
 *   <li><b>The screen's maximum</b>: a patch at the chosen peak with a symbol far brighter than any
 *       screen on it ({@link SteinHdr#peakTest}); the player drags until the symbol almost
 *       disappears — past the screen's real limit both look the same.
 *   <li><b>The white</b>: a white card drawn by the game (the paper white); the player drags until
 *       it is comfortable, like a sheet of paper in a bright room.
 *   <li><b>Done</b>: the landscape goes to night, to see the lights against the menu.
 * </ol>
 */
final class VideoCalibration {

    static final int STEPS = 3;

    private static final int CARD_W = 280;
    private static final int CARD_H = 124;
    private static final int PATCH = 64;

    private VideoCalibration() {}

    /** The option a step adjusts, or null. */
    static VideoSettings.Entry entry(int step) {
        return switch (step) {
            case 0 -> VideoSettings.find("hdrPeak");
            case 1 -> VideoSettings.find("hdrWhite");
            default -> null;
        };
    }

    /** Starting: HDR on (it is what is being calibrated), the landscape by day. */
    static void begin() {
        VideoSettings.Entry hdr = VideoSettings.find("hdr");
        if (hdr != null && hdr.state != null && !hdr.state.getAsBoolean()) {
            hdr.next.run();
        }
        Showcase.setNight(false);
    }

    /** Each step's arrival: the last one shows the night. */
    static void arrive(int step) {
        Showcase.setNight(step == STEPS - 1);
    }

    /** Leaving: the test off. */
    static void end() {
        SteinHdr.peakTest(false, 0, 0, 0, 0, 0);
    }

    static void draw(
            VideoLayout l,
            int width,
            int height,
            int step,
            int mx,
            int my,
            VideoSettings.Entry dragging,
            double dragAt,
            float alpha,
            long delta) {
        int x = (width - CARD_W) / 2;
        int y = height - VideoLayout.MARGIN - CARD_H - 26;
        peak(step == 0, width, y, alpha);
        if (step == 1) {
            sample(width, y, alpha);
        }
        Shapes.fill(x, y, CARD_W, CARD_H, 8, Ui.alpha(0xD80A0614, alpha));
        Shapes.stroke(x, y, CARD_W, CARD_H, 8, 1, Ui.alpha(Theme.RULE, alpha));
        String key = step == 0 ? "peak" : step == 1 ? "white" : "done";
        Ui.label(
                VideoScreen.tr("steinloader.hdr.step", step + 1, STEPS),
                x + 12,
                y + 9,
                Ui.alpha(Theme.GOLD, alpha));
        Ui.text(
                VideoScreen.tr("steinloader.hdr." + key + ".title"),
                x + 12,
                y + 19,
                Ui.alpha(Theme.INK, alpha));
        // the explanation in the reading font: a sentence, not a label
        List<String> lines =
                Ui.wrap(VideoScreen.tr("steinloader.hdr." + key + ".text"), CARD_W - 24);
        for (int i = 0; i < Math.min(3, lines.size()); i++) {
            Ui.text(lines.get(i), x + 12, y + 35 + i * 11, Ui.alpha(Theme.MUTED, alpha));
        }
        VideoSettings.Entry e = entry(step);
        if (e != null) {
            int tx = x + 12;
            int tw = CARD_W - 24 - 60;
            int ty = y + CARD_H - 34;
            boolean hover = my >= ty - 6 && my < ty + 6 && mx >= tx - 4 && mx < tx + tw + 4;
            double f = e == dragging ? dragAt : e.fraction.getAsDouble();
            Widgets.slider(
                    "hdrcal|" + step, tx, ty, tw, (float) f, hover, e == dragging, alpha, delta);
            Ui.label(e.value.get(), tx + tw + 8, ty - 4, Ui.alpha(Theme.GOLD, alpha));
            VideoLayout.Hit hit = l.add(VideoLayout.Kind.CAL_SLIDER, tx - 4, ty - 6, tw + 8, 12);
            hit.entry = e;
            hit.backEdge = tx; // the track's start: VideoScreen reads it for the drag
            hit.index = tw;
        }
        int bw = 70;
        int by = y + CARD_H - 22;
        String next =
                VideoScreen.tr(
                        step == STEPS - 1 ? "steinloader.hdr.finish" : "steinloader.hdr.next");
        String back = VideoScreen.tr(step == 0 ? "steinloader.hdr.cancel" : "steinloader.hdr.back");
        int nx = x + CARD_W - 12 - bw;
        int bx = x + 12;
        Widgets.primary(next, nx, by, bw, 16, inside(mx, my, nx, by, bw, 16), false, alpha);
        Widgets.button(back, bx, by, bw, 16, inside(mx, my, bx, by, bw, 16), false, alpha);
        l.add(VideoLayout.Kind.CAL_NEXT, nx, by, bw, 16);
        l.add(VideoLayout.Kind.CAL_BACK, bx, by, bw, 16);
    }

    /** The peak test's patch, centered above the card; the encode draws inside it. */
    private static void peak(boolean on, int width, int cardY, float alpha) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!on) {
            SteinHdr.peakTest(false, 0, 0, 0, 0, 0);
            return;
        }
        int px = (width - PATCH) / 2;
        int py = Math.max(VideoLayout.MARGIN + 30, cardY - PATCH - 30);
        Shapes.stroke(
                px - 3, py - 3, PATCH + 6, PATCH + 6, 4, 1, Ui.alpha(Theme.RULE_STRONG, alpha));
        int s = new ScaledResolution(mc).getScaleFactor();
        SteinHdr.peakTest(true, px * s, py * s, PATCH * s, PATCH * s, mc.displayHeight);
    }

    /** The white card of the second step: the game's white is the paper white. */
    private static void sample(int width, int cardY, float alpha) {
        int w = 120;
        int h = 56;
        int x = (width - w) / 2;
        int y = Math.max(VideoLayout.MARGIN + 30, cardY - h - 30);
        Shapes.fill(x, y, w, h, 6, Ui.alpha(0xFFFFFFFF, alpha));
        String s = VideoScreen.tr("steinloader.hdr.sample");
        Ui.label(s, x + (w - Ui.labelWidth(s)) / 2, y + h / 2 - 4, Ui.alpha(0xFF0A0614, alpha));
    }

    /** The pill that starts it, under the day and night ones; only where HDR can be shown. */
    static void pill(VideoLayout l, int width, int mx, int my, float alpha) {
        if (!SteinHdr.available() || alpha <= 0.01F) {
            return;
        }
        String label = VideoScreen.tr("steinloader.hdr.calibrate");
        int w = Ui.labelWidth(label) + 34;
        int h = 18;
        int x = width - VideoLayout.MARGIN - w;
        int y = VideoLayout.MARGIN + 24;
        boolean hover = inside(mx, my, x, y, w, h);
        Shapes.fill(x, y, w, h, h / 2.0F, Ui.alpha(0xC80A0614, alpha));
        Shapes.stroke(
                x, y, w, h, h / 2.0F, 1, Ui.alpha(hover ? Theme.GOLD : Theme.RULE_STRONG, alpha));
        Icons.draw("sparkle", x + 12, y + h / 2.0F, 10.0F, Ui.alpha(Theme.ACCENT, alpha));
        Ui.label(label, x + 21, y + 5, Ui.alpha(hover ? Theme.GOLD : Theme.INK_SOFT, alpha));
        l.add(VideoLayout.Kind.CALIBRATE, x, y, w, h);
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
