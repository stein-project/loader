/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Breno Xavier
 *
 * Part of the Stein Loader API, licensed under the MIT License (see LICENSE-API).
 */

package dev.xavier.stein.loader.api;

import dev.xavier.stein.loader.api.spi.Host;

import java.util.List;

/**
 * The game's screens and their buttons, for whoever puts a button on a game screen ({@link
 * SteinMod#onGuiInit}) or needs to know where the player is. Screens and buttons are game types and
 * pass as Object. Call from the game thread.
 *
 * <pre>
 * public void onGuiInit(Object screen, List&lt;Object&gt; buttons) {
 *     if (Screens.kind(screen) == Screens.Kind.MULTIPLAYER) {
 *         buttons.add(Screens.button(MY_ID, Screens.width(screen) - 124, 4, 120, 20, "My button"));
 *     }
 * }
 * public boolean onGuiAction(Object screen, Object button) {
 *     return Screens.buttonId(button) == MY_ID &amp;&amp; doSomething();
 * }
 * </pre>
 *
 * <p>Button ids: use numbers far from the game's (which stay below 1000), such as {@code 0x5E000 +
 * n}.
 */
@Experimental(since = "0.1.16")
public final class Screens {

    /** Which screen it is. A mod screen or a game one that is not here: {@link #OTHER}. */
    public enum Kind {
        /** No screen: the player is in the world. */
        NONE,
        /** The game's title screen. */
        MAIN_MENU,
        /** The singleplayer world list. */
        SINGLEPLAYER,
        /** The multiplayer server list. */
        MULTIPLAYER,
        /** "Connecting to server..." (the address: {@link Server#connectingAddress}). */
        CONNECTING,
        /** The disconnect screen, with the reason (see {@link SteinMod#onDisconnected}). */
        DISCONNECTED,
        /** The Esc menu. */
        PAUSE,
        /** The game's Options screen (and its subscreens are {@link #OTHER}). */
        OPTIONS,
        /** The chat (typing). */
        CHAT,
        /** The player's inventory (the E one) or the creative one. */
        INVENTORY,
        /** Chest, furnace, crafting table and every other screen with slots. */
        CONTAINER,
        /** The Panel and the Stein Loader screens. */
        STEIN,
        /** Any other screen: a mod's, or a game screen not listed here. */
        OTHER
    }

    private static final Kind[] KINDS = Kind.values();

    private Screens() {}

    /** The open screen, or null in the world. */
    public static Object current() {
        return Host.INSTANCE.screenCurrent();
    }

    /** Which screen this is (null = {@link Kind#NONE}). */
    public static Kind kind(Object screen) {
        return KINDS[Host.INSTANCE.screenKind(screen)];
    }

    /** Which screen is open. */
    public static Kind currentKind() {
        return kind(current());
    }

    /** Is there an open screen (any one, even chat)? */
    public static boolean isOpen() {
        return current() != null;
    }

    /** Screen width, in the interface scale (the same as the buttons'). */
    public static int width(Object screen) {
        return Host.INSTANCE.screenWidth(screen);
    }

    /** Screen height, in the interface scale. */
    public static int height(Object screen) {
        return Host.INSTANCE.screenHeight(screen);
    }

    /** Opens a screen (null closes the current one and returns to the world). */
    public static void open(Object screen) {
        Host.INSTANCE.screenOpen(screen);
    }

    /** A game button, for the {@link SteinMod#onGuiInit} list. */
    public static Object button(int id, int x, int y, int width, int height, String label) {
        return Host.INSTANCE.buttonNew(id, x, y, width, height, label);
    }

    /** The button's id, or -1 if it is not a button. */
    public static int buttonId(Object button) {
        return Host.INSTANCE.buttonId(button);
    }

    /** The button in this list with that id, or null. */
    public static Object findButton(List<Object> buttons, int id) {
        for (Object b : buttons) {
            if (buttonId(b) == id) {
                return b;
            }
        }
        return null;
    }

    /**
     * The button's x, in GUI coordinates.
     *
     * @param button a GuiButton (game type, as Object)
     * @return its left edge, or 0 when it is not a button
     */
    public static int buttonX(Object button) {
        return Host.INSTANCE.buttonX(button);
    }

    /**
     * The button's y, in GUI coordinates.
     *
     * @param button a GuiButton (game type, as Object)
     * @return its top edge, or 0 when it is not a button
     */
    public static int buttonY(Object button) {
        return Host.INSTANCE.buttonY(button);
    }

    /** Changes the button's text (a count, a state). */
    public static void setLabel(Object button, String label) {
        Host.INSTANCE.buttonLabel(button, label);
    }

    /** Enables or disables the button (disabled turns gray and does not respond). */
    public static void setEnabled(Object button, boolean enabled) {
        Host.INSTANCE.buttonEnabled(button, enabled);
    }
}
