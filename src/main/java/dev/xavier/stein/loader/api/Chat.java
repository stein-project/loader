/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Breno Xavier
 *
 * Part of the Stein Loader API, licensed under the MIT License (see LICENSE-API).
 */

package dev.xavier.stein.loader.api;

import dev.xavier.stein.loader.api.spi.Host;

import java.util.function.Predicate;

/**
 * Chat and the text above the hotbar. Texts accept the game's color codes ({@code §a}, {@code §l}).
 * Call from the game thread.
 *
 * <p>The game's text components (what {@link SteinMod#onChat} and {@link SteinMod#onNametag}
 * receive and return) pass as Object: {@link #text} creates one, {@link #plain} and {@link
 * #formatted} read them.
 *
 * <p>Since 0.1.36 the chat's messages themselves are reachable: read them ({@link #message}, {@link
 * #messageTime}), take one out, add old ones back, highlight one, show only some ({@link
 * #setFilter}), and find the one under the mouse. A message is identified by its component object
 * itself (the one {@link SteinMod#onChatLine} returned), not by an equal text. Nothing here leaves
 * the player's machine.
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

    // ---- the messages in the chat (0.1.36) ----

    /** The game's cap on chat messages; {@link #setLimit} goes up to {@link #MAX_LIMIT}. */
    @Experimental(since = "0.1.36")
    public static final int VANILLA_LIMIT = 100;

    /** The most messages {@link #setLimit} keeps. */
    @Experimental(since = "0.1.36")
    public static final int MAX_LIMIT = 5000;

    /** Messages in the chat now (each wrapped message counts once). */
    @Experimental(since = "0.1.36")
    public static int messageCount() {
        return Host.INSTANCE.chatMessageCount();
    }

    /**
     * A message in the chat (IChatComponent): 0 is the newest, {@link #messageCount} - 1 the
     * oldest. Null outside that range.
     */
    @Experimental(since = "0.1.36")
    public static Object message(int index) {
        return Host.INSTANCE.chatMessage(index);
    }

    /**
     * When a message entered the chat (System.currentTimeMillis), or 0 if unknown (one put there by
     * another way).
     *
     * @param message the message itself, as {@link #message} or {@link SteinMod#onChatLine} gave it
     */
    @Experimental(since = "0.1.36")
    public static long messageTime(Object message) {
        return Host.INSTANCE.chatMessageTime(message);
    }

    /**
     * Takes a message out of the chat, with all its rows.
     *
     * @param message the message itself
     * @return false if it was no longer in the chat
     */
    @Experimental(since = "0.1.36")
    public static boolean removeMessage(Object message) {
        return Host.INSTANCE.chatRemove(message);
    }

    /**
     * Adds a message as already read: it goes in as the newest, appears only with the chat open
     * (not on the closed chat), and does not pass through {@link SteinMod#onChatLine}. For
     * restoring a saved history.
     *
     * @param message the message (IChatComponent)
     * @param time when it first arrived (System.currentTimeMillis), for {@link #messageTime}
     */
    @Experimental(since = "0.1.36")
    public static void addOld(Object message, long time) {
        Host.INSTANCE.chatAddOld(message, time);
    }

    /** Empties the chat. What the player typed before (the up arrow) stays. */
    @Experimental(since = "0.1.36")
    public static void clear() {
        Host.INSTANCE.chatClear();
    }

    /**
     * A color behind a message's rows, as strong as its alpha (ARGB; 0 removes it). From {@link
     * SteinMod#onChatLine}, for the message being returned, it applies as the message enters.
     */
    @Experimental(since = "0.1.36")
    public static void setHighlight(Object message, int argb) {
        Host.INSTANCE.chatHighlight(message, argb);
    }

    /**
     * How many messages the chat keeps, from {@link #VANILLA_LIMIT} (the game's) to {@link
     * #MAX_LIMIT}. Shared by every mod: the last call wins.
     */
    @Experimental(since = "0.1.36")
    public static void setLimit(int messages) {
        Host.INSTANCE.chatLimit(messages);
    }

    /**
     * Shows the chat as if it were open (every message at full strength, the open height, the
     * scroll bar) without opening the chat screen; false goes back to the game's.
     */
    @Experimental(since = "0.1.36")
    public static void setPeek(boolean on) {
        Host.INSTANCE.chatPeek(on);
    }

    /**
     * Shows only the messages {@code test} accepts (it receives each message, IChatComponent); null
     * shows all again. Asked again whenever messages change, not every frame. The messages hidden
     * are still there.
     */
    @Experimental(since = "0.1.36")
    public static void setFilter(Predicate<Object> test) {
        Host.INSTANCE.chatFilter(test);
    }

    /**
     * The message under the mouse in the open chat, or null; the chat moved on the screen grid
     * counts too.
     */
    @Experimental(since = "0.1.36")
    public static Object hoveredMessage() {
        return Host.INSTANCE.chatHovered();
    }

    /**
     * Where across its row the mouse was at the last {@link #hoveredMessage}, in the units of
     * {@link SteinMod#onChatRowDrawn} (the row's text starts near 0 and its box ends at {@link
     * #rowWidth}; up to 16 past it count too); -1 if over no message.
     */
    @Experimental(since = "0.1.36")
    public static int hoveredColumn() {
        return Host.INSTANCE.chatHoveredColumn();
    }

    /** The width of a chat row's box, in the units of {@link SteinMod#onChatRowDrawn}. */
    @Experimental(since = "0.1.36")
    public static int rowWidth() {
        return Host.INSTANCE.chatRowWidth();
    }

    /**
     * The text under the mouse in the open chat (the part of a message, IChatComponent, with its
     * click and hover events), as the game finds it for a click; null if none.
     */
    @Experimental(since = "0.1.36")
    public static Object hoveredText() {
        return Host.INSTANCE.chatHoveredText();
    }

    /**
     * What clicking a text does: "open_url", "open_file", "run_command", "suggest_command",
     * "change_page", or "" if nothing.
     */
    @Experimental(since = "0.1.36")
    public static String clickAction(Object component) {
        return Host.INSTANCE.chatClickAction(component);
    }

    /** The address or command of a text's click ({@link #clickAction}), "" if none. */
    @Experimental(since = "0.1.36")
    public static String clickValue(Object component) {
        return Host.INSTANCE.chatClickValue(component);
    }

    /**
     * Runs a text's click as the game would on {@code screen} (the chat screen it came from),
     * without {@link SteinMod#onChatClick}: for after the player confirmed it.
     *
     * @return false if the text had nothing to run
     */
    @Experimental(since = "0.1.36")
    public static boolean runClick(Object screen, Object component) {
        return Host.INSTANCE.chatRunClick(screen, component);
    }

    /**
     * A new component: a copy of {@code component} followed by {@code tail} (each keeps its own
     * colors and click events). Either may be a String, taken as {@link #text}.
     */
    @Experimental(since = "0.1.36")
    public static Object append(Object component, Object tail) {
        return Host.INSTANCE.chatAppend(component, tail);
    }

    /** A component as the game's JSON (colors, click and hover events kept); "" if none. */
    @Experimental(since = "0.1.36")
    public static String toJson(Object component) {
        return Host.INSTANCE.chatToJson(component);
    }

    /** A component from {@link #toJson}; null if the text is not one. */
    @Experimental(since = "0.1.36")
    public static Object fromJson(String json) {
        return Host.INSTANCE.chatFromJson(json);
    }

    /** Is the chat screen open? */
    @Experimental(since = "0.1.36")
    public static boolean isOpen() {
        return Host.INSTANCE.chatIsOpen();
    }

    /** What is typed in the open chat; "" with no chat open. */
    @Experimental(since = "0.1.36")
    public static String inputText() {
        return Host.INSTANCE.chatInputText();
    }

    /** Replaces what is typed in the open chat. */
    @Experimental(since = "0.1.36")
    public static void setInputText(String text) {
        Host.INSTANCE.chatSetInputText(text);
    }

    /**
     * Sends what is typed in the open chat, as Enter does (into the up-arrow history, through
     * {@link SteinMod#onSendChat}).
     *
     * @param keepOpen true to leave the chat open and empty; false closes it, as the game does
     */
    @Experimental(since = "0.1.36")
    public static void submitInput(boolean keepOpen) {
        Host.INSTANCE.chatSubmit(keepOpen);
    }
}
