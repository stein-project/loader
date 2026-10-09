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

    /** Vertical offset to the anchor, in GUI pixels (see {@link #x}). */
    public final int y;

    /** Size factor of the element (1 = its natural size). */
    public final float scale;

    /**
     * Only for the game's own parts: a mod's element starts disabled, and the player turns it on.
     */
    public final boolean enabled;

    /** Whether the element also stays on screen while the chat is open. */
    public final boolean showInChat;

    /**
     * A placement.
     *
     * @param anchorX 0 left, 1 center, 2 right
     * @param anchorY 0 top, 1 middle, 2 bottom
     * @param x horizontal offset to the anchor, in GUI pixels
     * @param y vertical offset to the anchor, in GUI pixels
     * @param scale size factor (1 = natural size)
     * @param enabled whether it starts shown (only the game's own parts)
     * @param showInChat whether it stays on screen while the chat is open
     */
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
