/*
 * Copyright (c) 2026 Breno Xavier. All rights reserved.
 * Proprietary software: see the LICENSE file at the root of this repository.
 */

package dev.xavier.stein.loader.gui;

import dev.xavier.stein.loader.api.Option;
import dev.xavier.stein.loader.api.Page;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The state of the {@link PanelScreen}: sizes and colors, the chosen page and its open sections,
 * the card geometry and the rows drawn in the last frame, which the drawing ({@link PanelDrawing})
 * lays out and the input ({@link PanelScreen}) hit-tests.
 */
abstract class PanelBase extends GuiScreen {

    // ---- sizes (GUI pixels) ----
    static final int CARD_W = 440;
    static final int CARD_H = 300;
    static final int RAIL_W = 112;
    static final int HEAD_H = 30;
    static final int PAD = 10;

    /** Height of an option row and of a card title. */
    static final int ROW = 22;

    static final int TITLE_H = 24;

    static final int FIELD_H = TextEditor.FIELD_H;
    static final int RAIL_ROW = 26;

    static final int ANIM_MS = 180;

    // ---- colors ----
    /**
     * The card lets the world show through: dark enough to read, light enough not to cover the
     * game.
     */
    // The design system colors (Theme). Over the world the card is slightly translucent: change an
    // option and see the effect behind it.
    static final int CARD_BG = 0xEC0A0614;

    static final int CARD_LINE = Theme.RULE_STRONG;
    static final int SURFACE = Widgets.SURFACE;
    static final int SURFACE_HOVER = Widgets.SURFACE_HOVER;
    static final int ACCENT = Theme.ACCENT;
    static final int TEXT = Theme.INK;

    /**
     * Mod chosen last time: someone who opens it in the middle of a fight returns to the same
     * place.
     */
    static String lastPage = "";

    /**
     * Open sections and where the list was, per page: reopening lands exactly where it stopped, not
     * at the top.
     */
    static final Map<String, Set<String>> openByPage = new HashMap<>();

    static final Map<String, Integer> scrollByPage = new HashMap<>();

    /** Sections with "More options" open, per page and section. */
    static final Set<String> moreShown = new HashSet<>();

    final GuiScreen parent;
    final List<Page> pages;

    /** Name of the only page (opened by {@code Panel.openOnly}), or null with all of them. */
    final String only;

    int selected;
    int scroll;
    int contentH;

    /** Section to bring to the top on the next draw (openSection), or null. */
    String reveal;

    // ---- animation ----
    long openedAt;
    long lastFrame;

    /** How open each section is (0 to 1), by name. */
    final Map<String, Float> grow = new HashMap<>();

    /** Position of each option's switch (0 to 1), by the row it is on. */
    final Map<String, Float> knobs = new HashMap<>();

    /** The rail marker: where it is now, in pixels, to slide to the chosen page. */
    float railMark = -1.0F;

    /** The text field being edited. */
    final TextEditor text = new TextEditor();

    /** Where each content row ended up in the drawn frame, for clicks. */
    final List<Row> rows = new ArrayList<>();

    /** Where each rail item ended up. */
    final List<int[]> railRows = new ArrayList<>();

    /** Scrollbar being dragged. */
    boolean dragging;

    /** Option.Slider track being dragged. */
    final SliderDrag slider = new SliderDrag();

    int dragGrab;

    // ---- frame geometry ----
    int cardX;
    int cardY;
    int cardW;
    int cardH;
    int listX;
    int listW;
    int listY;
    int listH;

    /** What a drawn row is. */
    enum Kind {
        SECTION,
        OPTION,
        TEXT,
        ITEMS,
        MORE_ROW,
        RESTORE,
        ROWS
    }

    /** A drawn row: what it is and where it ended up. */
    static final class Row {
        final Kind kind;
        final int index;
        final int y;
        final int h;
        final String section;

        Row(Kind kind, int index, int y, int h, String section) {
            this.kind = kind;
            this.index = index;
            this.y = y;
            this.h = h;
            this.section = section;
        }
    }

    Page page() {
        return selected >= 0 && selected < pages.size() ? pages.get(selected) : null;
    }

    String pageKey() {
        Page p = page();
        return p == null ? "" : p.title();
    }

    /**
     * The page's open sections. On a page that was never opened, the first section comes already
     * open: a page with only closed titles does not show what is inside, and someone who has never
     * touched it does not know they open.
     */
    Set<String> open() {
        String key = pageKey();
        Set<String> open = openByPage.get(key);
        if (open == null) {
            open = new LinkedHashSet<>();
            Page page = page();
            if (page != null) {
                for (Page.Entry e : page.entries()) {
                    if (e.section != null) {
                        open.add(e.section);
                        break;
                    }
                }
            }
            openByPage.put(key, open);
        }
        return open;
    }

    boolean isOpen(String section) {
        return open().contains(section);
    }

    /**
     * One section open at a time: opening one closes the other. With all of them open, the page
     * turned back into the same wall of text this design came to undo.
     */
    void toggleSection(String section) {
        Set<String> open = open();
        boolean was = open.contains(section);
        open.clear();
        if (!was) {
            open.add(section);
        }
        text.blur();
    }

    static boolean hasAdvanced(List<Page.Entry> all, int from, int to) {
        return countAdvanced(all, from, to) > 0;
    }

    static int countAdvanced(List<Page.Entry> all, int from, int to) {
        int n = 0;
        for (int i = from; i < to; i++) {
            Page.Entry e = all.get(i);
            n += e.option != null && e.option.isAdvanced() ? 1 : 0;
        }
        return n;
    }

    static Kind kindOf(Page.Entry e) {
        if (e.rows != null) {
            return Kind.ROWS;
        }
        if (e.items != null) {
            return Kind.ITEMS;
        }
        if (e.option instanceof Option.Text) {
            return Kind.TEXT;
        }
        if (e.option != null && e.option.isRestore()) {
            return Kind.RESTORE;
        }
        return Kind.OPTION;
    }

    int rowHeight(Page.Entry e) {
        if (e.rows != null) {
            int n = e.rows.get().size();
            return n == 0 ? PanelLists.ITEM_H : n * PanelLists.ITEM_H + 2;
        }
        if (e.items != null) {
            List<String> items = e.items.get();
            if (items.isEmpty()) {
                return 0;
            }
            return PanelLists.chipLines(items, listW - 20) * (PanelLists.CHIP_H + 2) + 4;
        }
        if (e.option instanceof Option.Text) {
            return FIELD_H + 16;
        }
        return ROW;
    }

    /** Summary of a closed section: how many switches are on, or the value of the single option. */
    String summary(List<Page.Entry> all, int from, int to) {
        int on = 0;
        int total = 0;
        String single = null;
        for (int i = from; i < to; i++) {
            Option o = all.get(i).option;
            if (o == null || o.isRestore()) {
                continue;
            }
            Boolean state = o.state();
            if (state != null) {
                total++;
                on += state ? 1 : 0;
            } else if (single == null) {
                single = o.value();
            }
        }
        if (total >= 2) {
            return I18n.format("steinloader.panel.count", on, total);
        }
        return single == null ? "" : Ui.trim(single, 90);
    }

    int barX() {
        return listX + listW + 2;
    }

    boolean overBar(int mx, int my) {
        return mx >= barX() - 3 && mx < barX() + 6 && my >= listY && my < listY + listH;
    }

    boolean inList(int my) {
        return my >= listY && my < listY + listH;
    }

    int maxScroll() {
        return Math.max(0, contentH - listH);
    }

    PanelBase(List<Page> pages, GuiScreen parent, String only) {
        this.pages = pages;
        this.parent = parent;
        this.only = only;
        String want = only != null ? only : lastPage;
        for (int i = 0; i < pages.size(); i++) {
            if (pages.get(i).title().equals(want)) {
                selected = i;
            }
        }
        scroll = scrollByPage.getOrDefault(pageKey(), 0);
    }
}
