/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Breno Xavier
 *
 * Part of the Stein Loader API, licensed under the MIT License (see LICENSE-API).
 */

package dev.xavier.stein.loader.api;

import dev.xavier.stein.loader.api.spi.Host;

/**
 * Chat and the text above the hotbar. Texts accept the game's color codes ({@code §a}, {@code §l}).
 * Call from the game thread.
 *
 * <p>The game's text components (what {@link SteinMod#onChat} and {@link SteinMod#onNametag}
 * receive and return) pass as Object: {@link #text} creates one, {@link #plain} and {@link
 * #formatted} read them.
 */
@Experimental(since = "0.1.16")
public final class Chat {

    private Chat() {}

    /**
     * A line in chat, for the player only (it does not go to the server). Outside a world, nothing.
     */
    public static void print(String text) {
        Host.INSTANCE.chatPrint(text);
    }

    /**
     * The text above the hotbar, the same place as the record's "Now playing"; it disappears by
     * itself in a few seconds.
     */
    public static void actionBar(String text) {
        Host.INSTANCE.chatActionBar(text);
    }

    /**
     * Sends a message (or a command, with "/") to the server, as if the player typed it. Goes
     * through onSendChat.
     */
    public static void send(String message) {
        Host.INSTANCE.chatSend(message);
    }

    /** A game text component with that text. */
    public static Object text(String text) {
        return Host.INSTANCE.chatText(text);
    }

    /** A component's text, without the color codes; "" if it is not a component. */
    public static String plain(Object component) {
        return Host.INSTANCE.chatPlain(component);
    }

    /** A component's text with the color codes; "" if it is not a component. */
    public static String formatted(Object component) {
        return Host.INSTANCE.chatFormatted(component);
    }
}
