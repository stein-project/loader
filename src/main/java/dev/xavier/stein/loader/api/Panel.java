/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Breno Xavier
 *
 * Part of the Stein Loader API, licensed under the MIT License (see LICENSE-API).
 */

package dev.xavier.stein.loader.api;

import dev.xavier.stein.loader.api.spi.Host;

/**
 * Opens the Panel (what the player adjusts during a match) from inside a mod — so a button on a
 * game screen can lead straight to the mod's page, instead of asking for the key.
 */
public final class Panel {

    private Panel() {}

    /**
     * Opens the Panel over the given screen (GuiScreen), on the page named {@code page} (the mod's
     * name); null opens the last page that was open.
     */
    public static void open(Object parent, String page) {
        Host.INSTANCE.openPanel(parent, page, false);
    }

    /**
     * Opens the Panel with only the given page, without the list of the others: whoever arrives
     * through a mod's button (the account, in the server list) sees only what belongs to that mod,
     * and Esc goes back to the screen they came from.
     */
    public static void openOnly(Object parent, String page) {
        Host.INSTANCE.openPanel(parent, page, true);
    }

    /**
     * Closes the Panel and returns to the screen it came from. It is what the mod calls when what
     * the player chose has already done what they came for — picking the account in the server
     * list, for example: they chose, went back to the list, and are not stuck on a screen that has
     * served its purpose.
     */
    public static void close() {
        Host.INSTANCE.closePanel();
    }

    /** Opens the Panel on the last page that was open. */
    public static void open(Object parent) {
        open(parent, null);
    }
}
