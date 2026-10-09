/*
 * Copyright (c) 2026 Breno Xavier. All rights reserved.
 * Proprietary software: see the LICENSE file at the root of this repository.
 */

package dev.xavier.stein.loader.gui;

import dev.xavier.stein.loader.Loader;

import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

import org.lwjgl.input.Mouse;

/**
 * The Stein Loader tab on the right edge of the main menu, the pause menu and the options (like
 * LiteLoader's), at the middle of the screen height. The version lines of the main menu sit in the
 * bottom-left corner, like Forge's (Loader.mainMenuBranding).
 */
public final class Tab {

    static final int SIZE = 24;

    /** Open (mouse over it): the whole icon. */
    private static final int WIDTH = 28;

    /** Hidden: only the edge, half the icon. */
    private static final int PEEK = 11;

    private static final float RADIUS = 6.0F;
    private static final Tab MENU = new Tab();

    private final HoverFade hover = new HoverFade();

    Tab() {}

    private static boolean hasTab(Object screen) {
        return screen instanceof GuiMainMenu
                || screen instanceof GuiIngameMenu
                || screen instanceof GuiOptions;
    }

    /** Top of the tab on a screen of height h: vertically centered. */
    static int top(int h) {
        return h / 2 - SIZE / 2;
    }

    /** After the screen draws itself. */
    public static void afterDrawScreen(Loader loader, Object screen, int mx, int my) {
        if (hasTab(screen)) {
            GuiScreen s = (GuiScreen) screen;
            MENU.draw(s.width, top(s.height), mx, my, I18n.format("steinloader.tab"));
        }
    }

    /**
     * Mouse event of the screen: a click on the tab opens the menu and does not reach the screen.
     */
    public static boolean mouseInput(Loader loader, Object screen) {
        if (!hasTab(screen) || !Mouse.getEventButtonState() || Mouse.getEventButton() != 0) {
            return false;
        }
        GuiScreen gui = (GuiScreen) screen;
        if (!MENU.hit(gui.width, top(gui.height), Ui.mouseX(gui), Ui.mouseY(gui))) {
            return false;
        }
        Ui.click();
        Ui.mc().displayGuiScreen(new LoaderScreen(loader, gui));
        return true;
    }

    /**
     * The tab attached to edge (the right of the screen, or the left of the open menu): almost
     * hidden and transparent; with the mouse over it, it slides out and shows the whole icon.
     */
    void draw(int edge, int top, int mx, int my, String tooltip) {
        boolean over = hit(edge, top, mx, my);
        float e = Ui.ease(hover.update(over));
        float x = edge - shown();
        // The tab comes out from behind the edge: whatever goes past it (right corners, half the
        // icon when hidden) is clipped — at the edge of the open menu, it cannot end up on top of
        // it.
        Ui.clip(edge - WIDTH - 1, top, WIDTH + 1, SIZE);
        // The tab: the design system field, firmer under the cursor, with a rule on the edge.
        Ui.roundRect(
                x,
                top,
                WIDTH + RADIUS,
                SIZE,
                RADIUS,
                Math.round(0x90 + 0x60 * e) << 24 | (Theme.BG & 0xFFFFFF));
        Shapes.stroke(
                x, top, WIDTH + RADIUS, SIZE, RADIUS, 1, Ui.mix(Theme.RULE_STRONG, Theme.GOLD, e));
        Ui.image(Ui.ICON, x + 5.0F, top + 3.0F, 18, 18, 0.5F + 0.5F * e);
        Ui.unclip();
        if (e > 0.85F) {
            Ui.tooltipLeftOf(tooltip, x, top + 4);
        }
    }

    /** Width visible right now (the animation goes from PEEK to WIDTH). */
    private float shown() {
        return PEEK + (WIDTH - PEEK) * Ui.ease(hover.value());
    }

    boolean hit(int edge, int top, int mx, int my) {
        return my >= top && my < top + SIZE && mx >= edge - Math.round(shown()) && mx < edge;
    }
}
