/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Breno Xavier
 *
 * Part of the Stein Loader API, licensed under the MIT License (see LICENSE-API).
 */

package dev.xavier.stein.loader.api;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * What a mod offers the player during a game: its page in the Panel (right Shift), filled in {@link
 * SteinMod#onPage}.
 *
 * <p>The mod only describes; the Loader draws — so all mods look the same on screen, nobody writes
 * GUI again and the same page can be shown somewhere else later. The page is built every time the
 * Panel opens, so the values are always fresh.
 *
 * <p>Everything stays on the page itself, with no submenu: {@link #section} (a title), {@link
 * #option} (the option, clicked right there — including {@link Option.Text}, which becomes a text
 * field) and {@link #items} (a list of things you click to remove). Only what is not configuration
 * — the grid editor, which is used by dragging in the world — opens a separate screen, through
 * {@link #screen}.
 */
public final class Page {

    /**
     * One thing in a mod's list: an account, a proxy. It has a name, a detail on the right, whether
     * it is the one in use now, what to do when clicked and (when it can be removed) what to do on
     * a trash can click.
     *
     * <p>It exists because {@link #items} was not enough for what these lists need: there a click
     * does just one thing, and choosing and deleting are two — someone switching accounts must not
     * risk deleting one by mistake.
     */
    public static final class Item {
        public final String title;
        public final String detail;
        public final boolean current;
        public final Runnable use;

        /** What to do to remove it from the list, or null when it cannot be removed. */
        public final Runnable remove;

        public Item(String title, String detail, boolean current, Runnable use, Runnable remove) {
            this.title = title == null ? "" : title;
            this.detail = detail == null ? "" : detail;
            this.current = current;
            this.use = use;
            this.remove = remove;
        }
    }

    /** A page row: a section title, an option, or something that opens a screen. */
    public static final class Entry {
        /** Section title, or null. */
        public final String section;

        /** The option, or null. */
        public final Option option;

        /** Label of what opens a screen, or null. */
        public final String label;

        /**
         * Receives the screen it came from (to go back) and returns the screen to open (GuiScreen),
         * or null.
         */
        public final Function<Object, Object> screen;

        /** Clickable list, or null. */
        public final Supplier<List<String>> items;

        /** What to do with the clicked item, or null. */
        public final Consumer<String> onClick;

        /** Empty list text, or null. */
        public final String empty;

        /**
         * The click removes the item from the list (red under the mouse) instead of choosing it.
         */
        public final boolean removes;

        /** List of {@link Item} (one row per thing, with a trash can), or null. */
        public final Supplier<List<Item>> rows;

        private Entry(
                String section,
                Option option,
                String label,
                Function<Object, Object> screen,
                Supplier<List<String>> items,
                Consumer<String> onClick,
                String empty,
                boolean removes,
                Supplier<List<Item>> rows) {
            this.section = section;
            this.option = option;
            this.label = label;
            this.screen = screen;
            this.items = items;
            this.onClick = onClick;
            this.empty = empty;
            this.removes = removes;
            this.rows = rows;
        }
    }

    private String title;
    private String icon = "";
    private final List<Entry> entries = new ArrayList<>();

    public Page(String title) {
        this.title = title;
    }

    /** Page name, in the left column. By default it is the mod's name. */
    public String title() {
        return title;
    }

    /**
     * Another name for the page, when the mod's name does not describe what is on it — the Stein
     * HUD page, for example, only has the Tab list, and two "HUD" pages in the left list do not say
     * which is which.
     */
    public Page title(String name) {
        if (name != null && !name.isEmpty()) {
            this.title = name;
        }
        return this;
    }

    /**
     * Which drawing represents the page on the Panel rail, among the Stein icons (the same as the
     * Stein Client's, stroke only): {@code "screen"}, {@code "combat"}, {@code "link"}, {@code
     * "list"}, {@code "gear"}, {@code "grid"}, {@code "users"}, {@code "layers"}, {@code "shield"},
     * {@code "clock"}, {@code "check"}, {@code "bolt"}, {@code "lock"}, {@code "key"}, {@code
     * "search"}, {@code "star"}, {@code "heart"}, {@code "pin"}, {@code "ping"}, {@code "folder"},
     * {@code "sword"}, {@code "loop"}. With no icon (or a name that does not exist), the page
     * appears with the name's initial in a circle.
     */
    public Page icon(String name) {
        this.icon = name == null ? "" : name;
        return this;
    }

    public String icon() {
        return icon;
    }

    public List<Entry> entries() {
        return entries;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /** The "Restore defaults" of the current section ({@link Option#restore}), at its end. */
    public Page restore(Runnable run) {
        return option(Option.restore(run));
    }

    /** Title that separates one subject from the next. */
    public Page section(String title) {
        entries.add(new Entry(title, null, null, null, null, null, null, false, null));
        return this;
    }

    /** An option, clicked on its own row (left advances, right goes back). */
    public Page option(Option option) {
        entries.add(new Entry(null, option, null, null, null, null, null, false, null));
        return this;
    }

    /**
     * A list of things the player clicks to remove (allies, shortcuts): they appear in a queue,
     * wrapping lines, and the click calls {@code onClick}. The list is read at draw time, so adding
     * or removing shows up in the same opening.
     */
    public Page items(
            Supplier<List<String>> get, Consumer<String> onClick, String empty, boolean removes) {
        entries.add(new Entry(null, null, null, null, get, onClick, empty, removes, null));
        return this;
    }

    /**
     * A list of the mod's things, one per row: name, detail and the trash can for removing. A click
     * on the row uses that thing; a click on the trash can removes it. This is what suits accounts
     * and proxies, where choosing and deleting must be two different gestures.
     *
     * @param get the current list (read at draw time, so adding and removing shows up in the same
     *     opening)
     * @param empty what to say when there are none
     */
    public Page rows(Supplier<List<Item>> get, String empty) {
        entries.add(new Entry(null, null, null, null, null, null, empty, false, get));
        return this;
    }

    /**
     * Opens a mod screen, for what is not configuration — the grid editor, which is used by
     * dragging in the world. The function receives the screen it came from — keep it to return to
     * it when the player is done.
     */
    public Page screen(String label, Function<Object, Object> open) {
        entries.add(new Entry(null, null, label, open, null, null, null, false, null));
        return this;
    }
}
