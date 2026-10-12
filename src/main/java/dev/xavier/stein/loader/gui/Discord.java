/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.loader.gui;

import dev.xavier.stein.loader.Remote;

import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.input.Mouse;

import java.awt.Desktop;
import java.net.URI;

/**
 * The Stein Labs Discord invite: a round button at the bottom right of the main menu, above the
 * game's copyright line, and another in the About tab of the Loader menu.
 *
 * <p>The address comes from the project's public file ({@link Remote}), not from the jar — the
 * invite can be changed without a new version. Without an address (the first launch without
 * internet, or the field empty in the file), the button simply does not appear: better not to exist
 * than to exist and lead nowhere.
 */
public final class Discord {

    /** The Discord mark, white: whoever uses it picks the color. */
    public static final ResourceLocation MARK =
            new ResourceLocation("steinloader", "textures/gui/discord.png");

    /** Color of the Discord mark. */
    public static final int BLURPLE = 0xFF5865F2;

    /** Radius of the main menu button. */
    private static final int RADIUS = 10;

    private static final HoverFade hover = new HoverFade();

    private Discord() {}

    private static boolean hasInvite() {
        return !Remote.discord().isEmpty();
    }

    /** Center of the button on a w by h screen: bottom right, above "Copyright Mojang". */
    private static int centerX(int w) {
        return w - RADIUS - 6;
    }

    private static int centerY(int h) {
        return h - RADIUS - 16;
    }

    /** After the screen draws itself. */
    public static void afterDrawScreen(Object screen, int mx, int my) {
        if (!(screen instanceof GuiMainMenu) || !hasInvite()) {
            return;
        }
        GuiScreen gui = (GuiScreen) screen;
        int cx = centerX(gui.width);
        int cy = centerY(gui.height);
        boolean over = hit(cx, cy, mx, my);
        float e = Ui.ease(hover.update(over));
        // At rest it is barely there (a corner mark, not a menu button): just the mark, faded, with
        // no background. With the mouse over it, the Discord circle appears behind and the mark
        // turns white.
        Ui.circle(cx, cy, RADIUS, Ui.alpha(BLURPLE, e));
        // 16 GUI points, the texture size: each of its pixels becomes a whole pixel on screen.
        Ui.tinted(MARK, cx - 8, cy - 8, 16, 16, Ui.mix(0x7FE6E8F5, 0xFFFFFFFF, e));
        if (e > 0.85F) {
            String label = I18n.format("steinloader.discord");
            Ui.tooltipLeftOf(label, cx - RADIUS, cy - 8);
        }
    }

    /**
     * Mouse event of the screen: clicking the button opens the invite and does not reach the
     * screen.
     */
    public static boolean mouseInput(Object screen) {
        if (!(screen instanceof GuiMainMenu)
                || !hasInvite()
                || !Mouse.getEventButtonState()
                || Mouse.getEventButton() != 0) {
            return false;
        }
        GuiScreen gui = (GuiScreen) screen;
        if (!hit(centerX(gui.width), centerY(gui.height), Ui.mouseX(gui), Ui.mouseY(gui))) {
            return false;
        }
        Ui.click();
        open();
        return true;
    }

    private static boolean hit(int cx, int cy, int mx, int my) {
        int dx = mx - cx;
        int dy = my - cy;
        return dx * dx + dy * dy <= RADIUS * RADIUS;
    }

    /**
     * Opens the invite in the browser, on another thread: Desktop may take a while to respond, and
     * a second standing still with the game window frozen looks like a freeze. The address has
     * already been checked by {@link Remote}.
     */
    public static void open() {
        open(Remote.discord());
    }

    /** The same, for another already-checked address (the main menu's notice). */
    public static void open(String url) {
        if (url == null || url.isEmpty()) {
            return;
        }
        Ui.openDesktop("stein-open-invite", url, () -> Desktop.getDesktop().browse(new URI(url)));
    }
}
