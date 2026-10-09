/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Breno Xavier
 *
 * Part of the Stein Loader API, licensed under the MIT License (see LICENSE-API).
 */

package dev.xavier.stein.loader.api;

/**
 * Placement and state of an element on the HUD grid: the screen edge it follows, the offset, the
 * scale, whether it is enabled and whether it shows while the chat is open. The anchor makes the
 * element follow the right edge when the window or the GUI scale changes.
 */
public final class HudPlacement {

    /** 0 left, 1 center, 2 right. */
    public final int anchorX;

    /** 0 top, 1 middle, 2 bottom. */
    public final int anchorY;

    /**
     * Offset from the matching edge of the box (or from its center) to the anchor, in GUI pixels.
     */
    public final int x;

    public final int y;
    public final float scale;

    /**
     * Only for the game's own parts: a mod's element starts disabled, and the player turns it on.
     */
    public final boolean enabled;

    public final boolean showInChat;

    public HudPlacement(
            int anchorX,
            int anchorY,
            int x,
            int y,
            float scale,
            boolean enabled,
            boolean showInChat) {
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.x = x;
        this.y = y;
        this.scale = scale;
        this.enabled = enabled;
        this.showInChat = showInChat;
    }
}
