/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Breno Xavier
 *
 * Part of the Stein Loader API, licensed under the MIT License (see LICENSE-API).
 */

package dev.xavier.stein.loader.api;

import java.util.List;

/**
 * A block of information on screen that the player positions on the Stein Loader HUD grid (right
 * Shift opens the editor). The Loader stores the placement, the scale, whether it is enabled and
 * whether it shows with the chat open, and draws it at the right time; the mod only gives the size
 * and draws. Register with {@link Hud#register}, in afterStartGame. Everything on the game thread.
 */
public interface HudElement {

    /** Unique across all mods: "&lt;mod&gt;.&lt;name&gt;". It is the key of the saved placement. */
    String id();

    /** Name in the editor (already translated). */
    String name();

    /**
     * Builds this frame's content and defines {@link #width()} and {@link #height()}.
     *
     * @param preview in the editor: no real data, show an example
     * @return false when there is nothing to show
     */
    boolean layout(boolean preview, float partialTicks);

    /** Content width, in GUI pixels and without the player's scale. */
    int width();

    /** Content height, in GUI pixels and without the player's scale. */
    int height();

    /**
     * Draws the content from (0, 0) to ({@link #width}, {@link #height}): the Loader has already
     * translated and applied the scale, and the element stays where the player chose, whatever the
     * edge. <b>Do not offset the drawing</b> (no negative x when pinned to the right): what falls
     * outside the box shows up out of place.
     *
     * @param alignRight the element is pinned to the right edge: whoever has lines of different
     *     widths aligns each one to the right of the box ({@code x = width() - lineWidth}), so the
     *     text grows toward the inside of the screen
     */
    void draw(boolean alignRight, float partialTicks);

    /** Factory placement (also the one used by "Position: restore" in the options). */
    HudPlacement defaultPlacement();

    /**
     * Placement from the mod's own old configuration. The Loader no longer uses it: a new element
     * starts disabled, at the factory placement, and the player turns it on from the editor's
     * element list.
     */
    default HudPlacement savedPlacement() {
        return null;
    }

    /**
     * The background opacity the element starts with (and the one used by "Restore defaults"), from
     * 0 (no background) to 1. There is a single background, Stein's (rounded corners), and the
     * Loader draws it: the player adjusts the transparency in the element's option, and <b>the mod
     * does not draw its own background</b>. With a background, the element's box gets some padding
     * around the content.
     */
    @Experimental(since = "0.1.18")
    default float defaultBackground() {
        return 0.0F;
    }

    /**
     * Does the element accept the standard background? False for what is already drawn as a game
     * object (the armor with the items, the health bars): it gets neither the transparency option
     * nor any background.
     */
    @Experimental(since = "0.1.18")
    default boolean hasBackground() {
        return true;
    }

    /** Also shows with other screens open (the game's own HUD parts). */
    default boolean visibleWithScreens() {
        return false;
    }

    /**
     * No effect for mods: with the F3 screen open, everything that is not part of the game itself
     * disappears, always. Only the game's parts on the grid (hotbar, health, chat...) remain, as in
     * the game.
     */
    default boolean hideWithDebug() {
        return true;
    }

    /**
     * The element's options, after the common ones (enabled, with chat, scale, position), on the
     * editor's options screen.
     */
    default void options(List<Option> out) {}

    /**
     * Resets the element's own options (those of {@link #options}) to the default. The element's
     * "Restore defaults" and the one on the Screen page call this after restoring placement and
     * scale; without it, restoring left the options as they were.
     */
    default void restoreOptions() {}

    /**
     * An option changed (the element's or one of the common ones, such as enabled): the mod saves
     * its configuration.
     */
    default void optionsChanged() {}
}
