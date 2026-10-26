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
import dev.xavier.stein.loader.LoaderOptions;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.resources.I18n;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.List;

/**
 * The video settings, Stein's way: a narrow translucent column on the right, over a landscape of
 * Stein's own ({@link Showcase}) that shows each change as it is made. The game's options and
 * Stein's are in one compact list per topic ({@link VideoSettings}), one line each, the topics
 * chosen by name at the top; under the list, what the option under the mouse does, in plain words.
 *
 * <p>On the top left, day or night for the landscape (the clock runs through the sunset or the
 * dawn) and a button that hides the menu to see the whole picture — a click anywhere, or Esc,
 * brings it back. Inside a server, a small note at the bottom says the player is still there, and
 * turns red while they take damage.
 *
 * <p>Without a world (the main menu) there is no landscape: the panel sits over the game's own
 * background. Without the Bridge's options (nothing provided them) the game's screen stays.
 */
public final class VideoScreen extends GuiScreen {

    private static final long OPEN_MS = 320L;

    /** H (the game's key code; the input shim has no constant for it). */
    private static final int KEY_H = 35;

    private static final long VEIL_MS = 600L;

    /** The topic shown, kept between openings. */
    private static int topic;

    private final GuiScreen parent;
    private List<VideoSettings.Section> sections;
    private final VideoLayout layout = new VideoLayout();

    private long openedAt;
    private long last;
    private boolean hidden;
    private float hide;

    /** The slider being dragged and where it is (applied on release for the heavy ones). */
    private VideoSettings.Entry dragging;

    private double dragAt;

    /** The dragged slider's track: where it starts and how wide it is. */
    private int dragX;

    private int dragW = 1;
    private boolean landscape;

    /** The HDR calibration's step ({@link VideoCalibration}); -1 when not calibrating. */
    private int calibrating = -1;

    private float calibrate;

    public VideoScreen(GuiScreen parent) {
        this.parent = parent;
    }

    /** Whether the Stein video settings can replace the game's: the options were provided. */
    public static boolean available() {
        return VideoSettings.source() != null;
    }

    @Override
    public void initGui() {
        if (sections == null) {
            sections = VideoSettings.sections();
            openedAt = Ui.now();
            last = openedAt;
            Showcase.open();
            landscape = Showcase.active();
            topic = Math.max(0, Math.min(topic, sections.size() - 1));
            // (diagnostics: also without HDR, to see the steps' layout)
            if (LoaderOptions.DIAG_HDR_STEP >= 0 && landscape) {
                calibrating = Math.min(LoaderOptions.DIAG_HDR_STEP, VideoCalibration.STEPS - 1);
                VideoCalibration.begin();
                VideoCalibration.arrive(calibrating);
            }
        }
        Keyboard.enableRepeatEvents(true);
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        VideoCalibration.end();
        Showcase.close();
        VideoSettings.Source s = VideoSettings.source();
        if (s != null) {
            s.closed();
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return true;
    }

    // ---- drawing ----

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        followScale();
        long now = Ui.now();
        long delta = Math.min(100L, now - last);
        last = now;
        if (!landscape) {
            drawDefaultBackground();
        }
        float open = Ui.clamp01((now - openedAt) / (float) OPEN_MS);
        open = open * open * (3 - 2 * open);
        hide = Ui.towards(hide, hidden || calibrating >= 0 ? 1.0F : 0.0F, 9.0F, delta);
        calibrate = Ui.towards(calibrate, calibrating >= 0 ? 1.0F : 0.0F, 9.0F, delta);
        if (dragging != null && Mouse.isButtonDown(0)) {
            dragAt = fraction(mouseX);
            if (!dragging.onRelease) {
                apply(() -> dragging.setFraction.accept(dragAt));
            }
        }
        float alpha = open * (1.0F - hide);
        layout.place(width, height, layout.panelWidth(width) * (1.0F - open) + 40 * hide);
        VideoDrawing.panel(
                this, layout, sections, topic, mouseX, mouseY, dragging, dragAt, alpha, delta);
        VideoDrawing.controls(layout, mouseX, mouseY, Showcase.night(), landscape, alpha);
        if (landscape) {
            VideoCalibration.pill(layout, width, mouseX, mouseY, alpha);
        }
        VideoDrawing.server(mc, layout, open, now);
        if (calibrating >= 0 || calibrate > 0.01F) {
            VideoCalibration.draw(
                    layout,
                    width,
                    height,
                    Math.max(0, calibrating),
                    mouseX,
                    mouseY,
                    dragging,
                    dragAt,
                    calibrate,
                    delta);
        } else if (hide > 0.01F) {
            VideoDrawing.hiddenNote(width, height, hide, now);
        }
        if (landscape) {
            // the landscape fades in from black: the switch from the real world is not a cut
            float veil = 1.0F - Ui.clamp01((now - openedAt) / (float) VEIL_MS);
            if (veil > 0.0F) {
                Ui.rect(0, 0, width, height, Ui.alpha(0xFF000000, veil * veil));
            }
        }
    }

    /** The GUI scale option rebuilds the screen at its new size, as the game's screen does. */
    private void followScale() {
        ScaledResolution sr = new ScaledResolution(mc);
        if (sr.getScaledWidth() != width || sr.getScaledHeight() != height) {
            setWorldAndResolution(mc, sr.getScaledWidth(), sr.getScaledHeight());
        }
    }

    // ---- input ----

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (hidden && calibrating < 0) {
            hidden = false;
            return;
        }
        VideoLayout.Hit hit = layout.hit(mouseX, mouseY);
        if (hit == null) {
            return;
        }
        switch (hit.kind) {
            case TOPIC -> {
                if (hit.index != topic) {
                    topic = hit.index;
                    layout.resetScroll();
                    click();
                }
            }
            case DAY -> {
                Showcase.setNight(false);
                click();
            }
            case NIGHT -> {
                Showcase.setNight(true);
                click();
            }
            case HIDE -> {
                hidden = true;
                click();
            }
            case ENTRY -> entry(hit, mouseX, button);
            case CALIBRATE -> {
                calibrating = 0;
                VideoCalibration.begin();
                click();
            }
            case CAL_SLIDER -> drag(hit.entry, hit.trackX, hit.trackW, mouseX);
            case CAL_NEXT -> {
                calibrating = calibrating + 1 >= VideoCalibration.STEPS ? -1 : calibrating + 1;
                calibrated();
                click();
            }
            case CAL_BACK -> {
                calibrating--;
                calibrated();
                click();
            }
            default -> {}
        }
    }

    /** The calibration moved to another step, or ended. */
    private void calibrated() {
        if (calibrating < 0) {
            VideoCalibration.end();
        } else {
            VideoCalibration.arrive(calibrating);
        }
    }

    /** Starts dragging {@code e} along a track at {@code x}, {@code w} wide. */
    private void drag(VideoSettings.Entry e, int x, int w, int mouseX) {
        if (e == null || !e.enabled.getAsBoolean()) {
            return;
        }
        dragging = e;
        dragX = x;
        dragW = Math.max(1, w);
        dragAt = fraction(mouseX);
        if (!e.onRelease) {
            apply(() -> e.setFraction.accept(dragAt));
        }
    }

    private double fraction(int mouseX) {
        return Math.max(0, Math.min(1, (mouseX - dragX) / (double) dragW));
    }

    private void entry(VideoLayout.Hit hit, int mouseX, int button) {
        VideoSettings.Entry e = hit.entry;
        if (!e.enabled.getAsBoolean()) {
            return;
        }
        if (e.kind == VideoSettings.Kind.SLIDER) {
            // only the track (and a little around it) grabs: a click on the name changes nothing
            if (mouseX >= hit.trackX - 6) {
                drag(e, hit.trackX, hit.trackW, mouseX);
            }
            return;
        }
        if (e.kind != VideoSettings.Kind.CHOICE) {
            return;
        }
        // the left arrow, or a right click, goes back
        boolean back = button == 1 || hit.back;
        apply(back ? e.previous : e.next);
        click();
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        if (dragging != null) {
            VideoSettings.Entry e = dragging;
            dragging = null;
            double at = dragAt;
            apply(() -> e.setFraction.accept(at));
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0 && !hidden) {
            layout.scroll(wheel > 0 ? -1 : 1);
        }
    }

    @Override
    protected void keyTyped(char c, int key) {
        if (key == Keyboard.KEY_ESCAPE) {
            if (calibrating >= 0) {
                calibrating = -1;
                calibrated();
                return;
            }
            if (hidden) {
                hidden = false;
                return;
            }
            mc.displayGuiScreen(parent);
            return;
        }
        if (key == KEY_H && !hidden && calibrating < 0) {
            hidden = true;
        }
    }

    /** A change: applied, then saved by the owner of the options. */
    private void apply(Runnable change) {
        if (change == null) {
            return;
        }
        change.run();
        VideoSettings.Source s = VideoSettings.source();
        if (s != null) {
            s.changed();
        }
    }

    private void click() {
        mc.getSoundHandler()
                .playSound(
                        net.minecraft.client.audio.PositionedSoundRecord.create(
                                new net.minecraft.util.ResourceLocation("gui.button.press"), 1.0F));
    }

    static String tr(String key, Object... args) {
        return I18n.format(key, args);
    }

    /** The topics now (the drawing reads them). */
    List<VideoSettings.Section> sections() {
        return sections == null ? new ArrayList<>() : sections;
    }
}
