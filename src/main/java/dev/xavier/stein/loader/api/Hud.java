/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Breno Xavier
 *
 * Part of the Stein Loader API, licensed under the MIT License (see LICENSE-API).
 */

package dev.xavier.stein.loader.api;

import dev.xavier.stein.loader.api.spi.Host;

/**
 * The Stein Loader HUD grid: an editor (right Shift, under Controls) and a places file
 * (config/steinloader-hud.json) for everything mods show on screen, so none covers another. The
 * parts of the game's own HUD — action bar, boss bar and sidebar scoreboard — are already on it:
 * the player moves them, changes their format or turns them off.
 */
public final class Hud {

    private Hud() {}

    /**
     * Puts the element on the grid (in afterStartGame). The saved place wins; without one, the
     * mod's old one or the factory default.
     */
    public static void register(HudElement element) {
        Host.INSTANCE.registerHud(element);
    }

    /** Is the element on (the player can turn it off in the editor)? Unknown: true. */
    public static boolean isEnabled(String id) {
        return Host.INSTANCE.hudEnabled(id);
    }

    /**
     * A screen over the game (the Tab list, for example) occupies this area, in GUI pixels, in this
     * frame: in the next frame the grid hides the elements that would end up under it. Call it on
     * every frame in which the screen appears; without the call, they come back.
     */
    @Experimental(since = "0.1.18")
    public static void cover(float x, float y, float w, float h) {
        Host.INSTANCE.hudCover(x, y, w, h);
    }

    /**
     * Hides the whole HUD — the game's (hotbar, health, chat, crosshair) and every mod's — until
     * called again with false. The game draws the HUD beneath any open screen, even with F1; this
     * is for a screen that shows the world clean (a replay). Whoever hides it must show it again on
     * exit.
     */
    @Experimental(since = "0.1.21")
    public static void setHidden(boolean hidden) {
        Host.INSTANCE.hudHidden(hidden);
    }

    /**
     * The mod draws its own crosshair: the game's is left out of the next frame. Call it on every
     * frame while the mod's crosshair is on (from {@link SteinMod#onOverlay}), even in a frame in
     * which the mod chose to show none (third person, a screen open); without the call, the game's
     * crosshair comes back on the following frame, so a mod that is turned off or fails never
     * leaves the player without one.
     */
    @Experimental(since = "0.1.36")
    public static void replaceCrosshair() {
        Host.INSTANCE.hudReplaceCrosshair();
    }

    /**
     * Whether the game shows its crosshair in this frame, mods aside: not with the F3 screen open,
     * and in spectator only over an entity or a container. A mod that draws its own crosshair
     * follows it to keep the game's rules. Valid from {@link SteinMod#onOverlay} on.
     */
    @Experimental(since = "0.1.36")
    public static boolean crosshairAllowed() {
        return Host.INSTANCE.hudCrosshairAllowed();
    }

    /**
     * Whether the player hid the HUD with F1: what a mod draws over the world for the player
     * (markers, hit boxes) hides with it.
     */
    @Experimental(since = "0.1.36")
    public static boolean isHiddenWithF1() {
        return Host.INSTANCE.hudHiddenWithF1();
    }

    /**
     * The grid editor (GuiScreen), returning to {@code parent} (GuiScreen, or null for the game).
     */
    public static Object editorScreen(Object parent) {
        return Host.INSTANCE.hudEditor(parent);
    }
}
