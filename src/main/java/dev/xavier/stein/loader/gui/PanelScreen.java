/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.loader.gui;

import dev.xavier.stein.loader.Loader;
import dev.xavier.stein.loader.api.Option;
import dev.xavier.stein.loader.api.Page;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The Panel: what the player adjusts <b>during a game</b>, opened with the right Shift.
 *
 * <p>A card in the middle of the screen, with the world darkened around it — rather than a fully
 * translucent screen, where the text competed for legibility with whatever was happening behind it.
 * On the left, a rail of icons (one page per mod); on the right, the content in cards, one subject
 * per card, one open at a time.
 *
 * <p>The idea behind the design is that someone who has never touched a mod understands it in three
 * seconds: an icon to recognize the page, a switch that slides to turn things on and off, and only
 * the essentials in view — what is for people who already know what they want sits behind "More
 * options", inside the section itself ({@link Option#advanced()}).
 *
 * <p>It is not the Stein Loader menu: there you see what is installed, whether it loaded, and turn
 * it on or off; here you use it. Only the grid editor opens separately, because it is not
 * configuration — it is used by dragging in the world — and that is why it sits at the foot of the
 * rail, not among the options.
 *
 * <p>The pages are built on every open ({@code Loader.pages()}), so the values are always fresh.
 *
 * <p>It stays one class (over 1000 lines) because the rail, the content, the scrollbar and the
 * input all share the card geometry and the chosen page; the text field, the lists of things and
 * the key labels are in {@link TextEditor}, {@link PanelLists} and {@link KeyLabels}.
 */
public final class PanelScreen extends GuiScreen {

    // ---- sizes (GUI pixels) ----
    private static final int CARD_W = 440;
    private static final int CARD_H = 300;
    private static final int RAIL_W = 112;
    private static final int HEAD_H = 30;
    private static final int PAD = 10;

    /** Height of an option row and of a card title. */
    private static final int ROW = 22;

    private static final int TITLE_H = 24;

    private static final int FIELD_H = TextEditor.FIELD_H;
    private static final int RAIL_ROW = 26;

    private static final int ANIM_MS = 180;

    // ---- colors ----
    /**
     * The card lets the world show through: dark enough to read, light enough not to cover the
     * game.
     */
    // The design system colors (Theme). Over the world the card is slightly translucent: change an
    // option and see the effect behind it.
    private static final int CARD_BG = 0xEC0A0614;

    private static final int CARD_LINE = Theme.RULE_STRONG;
    private static final int SURFACE = Widgets.SURFACE;
    private static final int SURFACE_HOVER = Widgets.SURFACE_HOVER;
    private static final int ACCENT = Theme.ACCENT;
    private static final int TEXT = Theme.INK;

    /**
     * Mod chosen last time: someone who opens it in the middle of a fight returns to the same
     * place.
     */
    private static String lastPage = "";

    /**
     * Open sections and where the list was, per page: reopening lands exactly where it stopped, not
     * at the top.
     */
    private static final Map<String, Set<String>> openByPage = new HashMap<>();

    private static final Map<String, Integer> scrollByPage = new HashMap<>();

    /** Sections with "More options" open, per page and section. */
    private static final Set<String> moreShown = new HashSet<>();

    private final GuiScreen parent;
    private final List<Page> pages;

    /** Name of the only page (opened by {@code Panel.openOnly}), or null with all of them. */
    private final String only;

    private int selected;
    private int scroll;
    private int contentH;

    /** Section to bring to the top on the next draw (openSection), or null. */
    private String reveal;

    // ---- animation ----
    private long openedAt;
    private long lastFrame;

    /** How open each section is (0 to 1), by name. */
    private final Map<String, Float> grow = new HashMap<>();

    /** Position of each option's switch (0 to 1), by the row it is on. */
    private final Map<String, Float> knobs = new HashMap<>();

    /** The rail marker: where it is now, in pixels, to slide to the chosen page. */
    private float railMark = -1.0F;

    /** The text field being edited. */
    private final TextEditor text = new TextEditor();

    /** Where each content row ended up in the drawn frame, for clicks. */
    private final List<Row> rows = new ArrayList<>();

    /** Where each rail item ended up. */
    private final List<int[]> railRows = new ArrayList<>();

    /** Scrollbar being dragged. */
    private boolean dragging;

    /** Option.Slider track being dragged. */
    private final SliderDrag slider = new SliderDrag();

    private int dragGrab;

    // ---- frame geometry ----
    private int cardX;
    private int cardY;
    private int cardW;
    private int cardH;
    private int listX;
    private int listW;
    private int listY;
    private int listH;

    /** What a drawn row is. */
    private enum Kind {
        SECTION,
        OPTION,
        TEXT,
        ITEMS,
        MORE_ROW,
        RESTORE,
        ROWS
    }

    /** A drawn row: what it is and where it ended up. */
    private static final class Row {
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

    // ---- opening ----

    public static void open(List<Page> pages, GuiScreen parent, String page) {
        if (page != null) {
            lastPage = page;
        }
        Minecraft.getMinecraft().displayGuiScreen(new PanelScreen(pages, parent));
    }

    public PanelScreen(List<Page> pages, GuiScreen parent) {
        this(pages, parent, null);
    }

    /**
     * Opens the Panel on a page with a single section open and scrolled to the top: right-clicking
     * an element in the grid editor lands directly on its options, and Esc returns to the editor.
     */
    public static void openSection(GuiScreen parent, String page, String section) {
        lastPage = page;
        Set<String> open = new LinkedHashSet<>();
        open.add(section);
        openByPage.put(page, open);
        PanelScreen s = new PanelScreen(Loader.get().pages(), parent);
        s.scroll = 0;
        s.reveal = section;
        Minecraft.getMinecraft().displayGuiScreen(s);
    }

    /** Closes the open Panel and returns to the screen it came from (Panel.close). */
    public static void close() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen instanceof PanelScreen open) {
            mc.displayGuiScreen(open.parent);
        }
    }

    /**
     * Only the given page, without the rail of the others: this is what a mod button opens outside
     * a game (the account, in the server list). The player sees only what belongs to that mod, and
     * Esc returns to the screen it came from.
     */
    public static void openOnly(GuiScreen parent, String page) {
        Minecraft.getMinecraft()
                .displayGuiScreen(new PanelScreen(only(Loader.get().pages(), page), parent, page));
    }

    private static List<Page> only(List<Page> all, String page) {
        List<Page> out = new ArrayList<>();
        for (Page p : all) {
            if (p.title().equals(page)) {
                out.add(p);
            }
        }
        return out;
    }

    private PanelScreen(List<Page> pages, GuiScreen parent, String only) {
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

    /** The game keeps running: an option's effect shows up behind while clicking. */
    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void initGui() {
        if (openedAt == 0L) {
            openedAt = Ui.now();
        }
    }

    @Override
    public void onGuiClosed() {
        scrollByPage.put(pageKey(), scroll);
    }

    private Page page() {
        return selected >= 0 && selected < pages.size() ? pages.get(selected) : null;
    }

    private String pageKey() {
        Page p = page();
        return p == null ? "" : p.title();
    }

    /**
     * The page's open sections. On a page that was never opened, the first section comes already
     * open: a page with only closed titles does not show what is inside, and someone who has never
     * touched it does not know they open.
     */
    private Set<String> open() {
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

    private boolean isOpen(String section) {
        return open().contains(section);
    }

    /**
     * One section open at a time: opening one closes the other. With all of them open, the page
     * turned back into the same wall of text this design came to undo.
     */
    private void toggleSection(String section) {
        Set<String> open = open();
        boolean was = open.contains(section);
        open.clear();
        if (!was) {
            open.add(section);
        }
        text.blur();
    }

    // ---- drawing ----

    @Override
    public void drawScreen(int mx, int my, float partialTicks) {
        long now = Ui.now();
        long delta = lastFrame == 0L ? 16L : now - lastFrame;
        lastFrame = now;
        float p = Ui.ease(Math.min(1.0F, (now - openedAt) / (float) ANIM_MS));

        cardW = Math.min(this.width - 20, CARD_W);
        cardH = Math.min(this.height - 20, CARD_H);
        cardX = (this.width - cardW) / 2;
        cardY = (this.height - cardH) / 2 + Math.round((1.0F - p) * 10);

        // Background: the world stays in view, only darker, so the card is legible without covering
        // the game. Outside a game (the Panel opened by the server list button) there is no world
        // behind: so the screen it came from stays drawn, as in the Loader menu.
        boolean inWorld = Ui.mc().theWorld != null;
        if (!inWorld) {
            if (parent != null) {
                parent.drawScreen(-1, -1, partialTicks);
            } else {
                this.drawDefaultBackground();
            }
        }
        // Over the world the card is translucent on purpose (change an option and see the effect
        // behind it). Over another screen there is no effect to see, just text fighting text: so it
        // closes up.
        Ui.rect(0, 0, this.width, this.height, Math.round((inWorld ? 0x66 : 0xB4) * p) << 24);

        Draw2D.rect(cardX, cardY, cardW, cardH, Ui.alpha(inWorld ? CARD_BG : Theme.BG, p));
        Ui.frame(cardX, cardY, cardW, cardH, Ui.alpha(CARD_LINE, p));
        Ui.hair(cardX, cardY + HEAD_H - 4, cardW, Ui.alpha(Theme.RULE, p));

        header(mx, my, p);
        boolean rail = only == null && pages.size() > 1;
        listX = cardX + (rail ? RAIL_W : 0) + PAD;
        listW = cardW - (rail ? RAIL_W : 0) - PAD * 2 - 4;
        listY = cardY + HEAD_H;
        listH = cardH - HEAD_H - PAD;
        if (rail) {
            rail(mx, my, p, delta);
            Ui.hairV(
                    cardX + RAIL_W,
                    cardY + HEAD_H - 4,
                    cardH - HEAD_H + 4,
                    Ui.alpha(Theme.RULE, p));
        }
        content(mx, my, p, delta);
    }

    private void header(int mx, int my, float p) {
        Page page = page();
        Ui.image(Ui.ICON, cardX + PAD, cardY + 7, 16, 16, p);
        String title =
                only != null && page != null
                        ? page.title()
                        : I18n.format("steinloader.panel.title");
        Ui.title(
                Ui.trim(title, cardW - 90),
                cardX + PAD + 22,
                cardY + 11,
                Theme.TITLE,
                Ui.alpha(TEXT, p));
        // Close: the X in the corner, which is what people who do not know Esc closes look for
        // first.
        int cx = cardX + cardW - PAD - 6;
        int cy = cardY + 15;
        boolean hover = Widgets.closeHit(mx, my, cx, cy);
        if (hover) {
            Ui.circle(cx, cy, 9.0F, SURFACE_HOVER);
        }
        Icons.close(cx, cy, 9.0F, hover ? Theme.INK : Theme.MUTED);
    }

    /**
     * The rail: one page per row, with an icon, and at the foot what opens a screen of its own (the
     * grid editor).
     */
    private void rail(int mx, int my, float p, long delta) {
        railRows.clear();
        int y = cardY + HEAD_H + 6;
        int target = y;
        for (int i = 0; i < pages.size(); i++) {
            int top = y + i * RAIL_ROW;
            railRows.add(new int[] {top, RAIL_ROW - 2});
            boolean on = i == selected;
            boolean hover =
                    mx >= cardX + 6
                            && mx < cardX + RAIL_W - 6
                            && my >= top
                            && my < top + RAIL_ROW - 2;
            if (on) {
                target = top;
            }
            if (hover && !on) {
                Draw2D.rect(cardX + 6, top, RAIL_W - 12, RAIL_ROW - 2, Ui.alpha(SURFACE, p));
            }
            if (on) {
                Draw2D.rect(
                        cardX + 6, top, RAIL_W - 12, RAIL_ROW - 2, Ui.alpha(Theme.ACCENT_SOFT, p));
            }
            int color = on ? Theme.INK : hover ? Theme.GOLD : Theme.MUTED;
            icon(pages.get(i), cardX + 20, top + (RAIL_ROW - 2) / 2, 13.0F, Ui.alpha(color, p));
            Ui.text(
                    Ui.trim(pages.get(i).title(), RAIL_W - 44),
                    cardX + 32,
                    top + 6,
                    Ui.alpha(color, p));
        }
        // The marker slides to the chosen page instead of jumping: that is what shows one turned
        // into the other.
        railMark =
                railMark < 0
                        ? target
                        : Ui.towards(
                                railMark, target, Math.abs(railMark - target) * 12 + 60, delta);
        Draw2D.rect(cardX + 6, railMark, 1.5F, RAIL_ROW - 2, Ui.alpha(ACCENT, p));

        Page page = page();
        if (page == null) {
            return;
        }
        int by = cardY + cardH - PAD - 20;
        for (int i = page.entries().size() - 1; i >= 0; i--) {
            Page.Entry e = page.entries().get(i);
            if (e.screen == null) {
                continue;
            }
            boolean hover = mx >= cardX + 6 && mx < cardX + RAIL_W - 6 && my >= by && my < by + 20;
            // What opens a screen of its own (the grid editor): a pill-shaped button, with the
            // pencil.
            if (hover) {
                Shapes.fill(cardX + 6, by, RAIL_W - 12, 20, 10.0F, Ui.alpha(Theme.GOLD_FAINT, p));
            }
            Shapes.stroke(
                    cardX + 6,
                    by,
                    RAIL_W - 12,
                    20,
                    10.0F,
                    1,
                    Ui.alpha(hover ? Theme.GOLD : Theme.RULE_STRONG, p));
            Icons.pencil(cardX + 19, by + 10, 9.0F, Ui.alpha(hover ? Theme.GOLD : Theme.INK, p));
            Ui.label(e.label, cardX + 30, by + 6, Ui.alpha(hover ? Theme.GOLD : Theme.INK, p));
            railRows.add(new int[] {by, 20, i}); // three fields: it is a screen button, not a page
            by -= 24;
        }
    }

    /**
     * The page's drawing on the rail: the one requested by the mod, or the name's initial in a
     * circle.
     */
    private void icon(Page page, float cx, float cy, float size, int color) {
        if (Icons.has(page.icon())) {
            Icons.draw(page.icon(), cx, cy, size, color);
            return;
        }
        Ui.ring(cx, cy, size / 2, 1.0F / Ui.scale(), color);
        String initial =
                page.title().isEmpty()
                        ? "?"
                        : page.title().substring(0, 1).toUpperCase(Locale.ROOT);
        Ui.mono(initial, Math.round(cx) - Ui.monoWidth(initial) / 2, Math.round(cy) - 4, color);
    }

    /** The content: one card per section, the open one showing the options. */
    private void content(int mx, int my, float p, long delta) {
        rows.clear();
        Page page = page();
        if (page == null) {
            return;
        }
        Ui.clip(listX, listY, listW + 6, listH);
        int y = listY + 4 - scroll;
        int start = y;
        List<Page.Entry> all = page.entries();
        int i = 0;
        while (i < all.size()) {
            Page.Entry e = all.get(i);
            if (e.screen != null) { // goes at the foot of the rail
                i++;
                continue;
            }
            if (e.section == null) {
                // Before the first section: a card with no title, always open.
                int from = i;
                while (i < all.size() && all.get(i).section == null) {
                    i++;
                }
                y = card(page, null, from, i, y, mx, my, p, delta, 1.0F);
                continue;
            }
            String section = e.section;
            int from = ++i;
            while (i < all.size() && all.get(i).section == null) {
                i++;
            }
            float g = grow.getOrDefault(section, isOpen(section) ? 1.0F : 0.0F);
            g = Ui.towards(g, isOpen(section) ? 1.0F : 0.0F, 6.0F, delta);
            grow.put(section, g);
            y = card(page, section, from, i, y, mx, my, p, delta, g);
        }
        contentH = y - start + 4;
        Ui.unclip();
        scrollbar(mx, my, p);
        if (reveal != null) {
            // openSection: the requested section moves to the top as soon as it is known where it
            // ended up.
            for (Row r : rows) {
                if (r.kind == Kind.SECTION && reveal.equals(r.section)) {
                    scroll = Math.max(0, Math.min(maxScroll(), scroll + r.y - listY - 4));
                    break;
                }
            }
            reveal = null;
        }
        scroll = Math.max(0, Math.min(maxScroll(), scroll));
    }

    /**
     * A card. Returns the y just below it. With {@code growth} between 0 and 1 the body grows and
     * fades — that is what gives the impression that the section opens, instead of the whole page
     * jumping in size.
     */
    private int card(
            Page page,
            String section,
            int from,
            int to,
            int y,
            int mx,
            int my,
            float p,
            long delta,
            float growth) {
        List<Page.Entry> all = page.entries();
        int bodyH = 0;
        int shown = 0;
        int advanced = 0;
        boolean more = moreShown.contains(pageKey() + "|" + section);
        for (int i = from; i < to; i++) {
            Page.Entry e = all.get(i);
            if (e.option != null && !e.option.isVisible()) {
                continue;
            }
            if (e.option != null && e.option.isAdvanced() && !more) {
                advanced++;
                continue;
            }
            bodyH += rowHeight(e);
            shown++;
        }
        int moreH = advanced > 0 || (more && hasAdvanced(all, from, to)) ? ROW : 0;
        int headH = section == null ? 0 : TITLE_H;
        int full = headH + Math.round((bodyH + moreH + (shown > 0 ? 6 : 0)) * growth);
        if (y + full < listY - 8 || y > listY + listH + 8) { // out of view: only takes up the space
            if (section != null) {
                rows.add(new Row(Kind.SECTION, -1, y, headH, section));
            }
            return y + full + 6;
        }
        // The card shows even when closed: it is what separates one subject from another. Open, a
        // bit lighter.
        Draw2D.rect(listX, y, listW, full, Ui.alpha(Ui.mix(0x08E8E1F5, 0x10E8E1F5, growth), p));
        Ui.hair(listX, y, listW, Ui.alpha(Theme.RULE_STRONG, p));
        int inner = y;
        if (section != null) {
            sectionTitle(all, section, from, to, y, mx, my, p, growth);
            inner = y + TITLE_H;
        }
        if (growth <= 0.01F) {
            return y + full + 6;
        }
        int ry = inner + Math.round(3 * growth);
        for (int i = from; i < to; i++) {
            Page.Entry e = all.get(i);
            if (e.option != null && (!e.option.isVisible() || e.option.isAdvanced() && !more)) {
                continue;
            }
            int h = rowHeight(e);
            if (growth > 0.98F) { // only receives clicks once the section has finished opening
                rows.add(new Row(kindOf(e), i, ry, h, section));
            }
            row(e, i, listX + 10, ry, listW - 20, h, mx, my, p * growth, delta);
            ry += h;
        }
        if (moreH > 0 && growth > 0.5F) {
            int n = more ? countAdvanced(all, from, to) : advanced;
            moreRow(section, n, more, ry, mx, my, p, growth);
        }
        return y + full + 6;
    }

    /** The title line of a section's card: name, chevron and the summary when closed. */
    private void sectionTitle(
            List<Page.Entry> all,
            String section,
            int from,
            int to,
            int y,
            int mx,
            int my,
            float p,
            float growth) {
        boolean hover =
                mx >= listX && mx < listX + listW && my >= y && my < y + TITLE_H && inList(my);
        if (hover) {
            Draw2D.rect(listX, y, listW, TITLE_H, Ui.alpha(SURFACE, p));
        }
        Ui.label(section, listX + 10, y + 8, Ui.alpha(hover ? Theme.INK : Theme.GOLD, p));
        Icons.chevron(
                listX + listW - 14,
                y + TITLE_H / 2 - 1,
                8.0F,
                growth * 180.0F,
                Ui.alpha(hover ? Theme.INK : Theme.MUTED, p));
        String summary = summary(all, from, to);
        if (!summary.isEmpty() && growth < 0.5F) {
            Ui.mono(
                    summary,
                    listX + listW - 26 - Ui.monoWidth(summary),
                    y + 8,
                    Ui.alpha(Theme.FAINT, p * (1.0F - growth * 2)));
        }
        rows.add(new Row(Kind.SECTION, -1, y, TITLE_H, section));
    }

    /** The "More options" / "Fewer options" line at the foot of a section's card. */
    private void moreRow(
            String section, int n, boolean more, int ry, int mx, int my, float p, float growth) {
        String label = I18n.format(more ? "steinloader.panel.less" : "steinloader.panel.more", n);
        boolean hover =
                mx >= listX && mx < listX + listW && my >= ry && my < ry + ROW && inList(my);
        Icons.chevron(
                listX + 16,
                ry + ROW / 2 - 1,
                8.0F,
                more ? 180.0F : 0.0F,
                Ui.alpha(hover ? Theme.GOLD : Theme.MUTED, p * growth));
        Ui.label(label, listX + 26, ry + 6, Ui.alpha(hover ? Theme.GOLD : Theme.MUTED, p * growth));
        if (growth > 0.98F) {
            rows.add(new Row(Kind.MORE_ROW, -1, ry, ROW, section));
        }
    }

    private static boolean hasAdvanced(List<Page.Entry> all, int from, int to) {
        return countAdvanced(all, from, to) > 0;
    }

    private static int countAdvanced(List<Page.Entry> all, int from, int to) {
        int n = 0;
        for (int i = from; i < to; i++) {
            Page.Entry e = all.get(i);
            n += e.option != null && e.option.isAdvanced() ? 1 : 0;
        }
        return n;
    }

    private static Kind kindOf(Page.Entry e) {
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

    private int rowHeight(Page.Entry e) {
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

    /**
     * A row: the name on the left and the control on the right (the same as the screen editor's).
     */
    private void row(
            Page.Entry e,
            int index,
            int x,
            int y,
            int w,
            int h,
            int mx,
            int my,
            float p,
            long delta) {
        if (e.rows != null) {
            PanelLists.items(e, x, y, w, mx, my, p, inList(my));
            return;
        }
        if (e.items != null) {
            PanelLists.chips(e, x, y, w, mx, my, p, inList(my));
            return;
        }
        if (e.option instanceof Option.Text t) {
            text.draw(t, index, x, y, w, p);
            return;
        }
        if (e.option != null) {
            Widgets.option(
                    e.option,
                    pageKey(),
                    new Widgets.Row(x, y, w, h),
                    new Widgets.Pointer(mx, my, inList(my)),
                    p,
                    delta);
        }
    }

    /** Summary of a closed section: how many switches are on, or the value of the single option. */
    private String summary(List<Page.Entry> all, int from, int to) {
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

    private void scrollbar(int mx, int my, float p) {
        if (maxScroll() <= 0) {
            return;
        }
        int h = Math.max(20, listH * listH / Math.max(1, contentH));
        int y = listY + (listH - h) * scroll / maxScroll();
        boolean hot = dragging || overBar(mx, my);
        Draw2D.rect(barX() + 1, y, 1.0F, h, Ui.alpha(hot ? Theme.GOLD : Theme.RULE_STRONG, p));
    }

    private int barX() {
        return listX + listW + 2;
    }

    private boolean overBar(int mx, int my) {
        return mx >= barX() - 3 && mx < barX() + 6 && my >= listY && my < listY + listH;
    }

    private boolean inList(int my) {
        return my >= listY && my < listY + listH;
    }

    private int maxScroll() {
        return Math.max(0, contentH - listH);
    }

    // ---- input ----

    @Override
    protected void mouseClicked(int mx, int my, int button) {
        // A key label waiting: the mouse button (right, middle, side) becomes the key; the left one
        // gives up.
        if (KeyLabels.mousePressed(page(), button)) {
            Ui.click();
            return;
        }
        // Outside the card closes: that is what one expects of a window in the middle of the
        // screen.
        if (mx < cardX || mx >= cardX + cardW || my < cardY || my >= cardY + cardH) {
            this.mc.displayGuiScreen(parent);
            return;
        }
        int closeX = cardX + cardW - PAD - 6;
        if (Widgets.closeHit(mx, my, closeX, cardY + 15)) {
            Ui.click();
            this.mc.displayGuiScreen(parent);
            return;
        }
        if (only == null && mx < cardX + RAIL_W) {
            railClick(mx, my);
            return;
        }
        if (overBar(mx, my)) {
            int barH = Math.max(20, listH * listH / Math.max(1, contentH));
            int barY = listY + (listH - barH) * scroll / Math.max(1, maxScroll());
            dragging = true;
            dragGrab = my >= barY && my < barY + barH ? my - barY : barH / 2;
            scrollTo(my);
            return;
        }
        Page page = page();
        if (page == null || !inList(my)) {
            return;
        }
        for (Row r : rows) {
            if (my < r.y || my >= r.y + r.h) {
                continue;
            }
            click(page, r, mx, my, button);
            return;
        }
    }

    private void railClick(int mx, int my) {
        for (int i = 0; i < railRows.size(); i++) {
            int[] r = railRows.get(i);
            if (my < r[0] || my >= r[0] + r[1]) {
                continue;
            }
            if (r.length == 3) { // own-screen button (the grid editor)
                Page page = page();
                Page.Entry e = page == null ? null : page.entries().get(r[2]);
                if (e != null && e.screen != null) {
                    Ui.click();
                    Object s = e.screen.apply(this);
                    if (s instanceof GuiScreen gui) {
                        this.mc.displayGuiScreen(gui);
                    }
                }
                return;
            }
            if (i != selected && i < pages.size()) {
                scrollByPage.put(pageKey(), scroll);
                selected = i;
                lastPage = pages.get(i).title();
                scroll = scrollByPage.getOrDefault(lastPage, 0);
                text.blur();
                Ui.click();
            }
            return;
        }
    }

    private void click(Page page, Row r, int mx, int my, int button) {
        List<Page.Entry> all = page.entries();
        switch (r.kind) {
            case SECTION -> {
                toggleSection(r.section);
                Ui.click();
            }
            case MORE_ROW -> {
                String key = pageKey() + "|" + r.section;
                if (!moreShown.remove(key)) {
                    moreShown.add(key);
                }
                Ui.click();
            }
            case ROWS -> {
                PanelLists.clickRow(all.get(r.index), listX + 10, r.y, listW - 20, mx, my);
                text.blur();
            }
            case ITEMS -> {
                PanelLists.clickItem(all.get(r.index), listX + 10, r.y, listW - 20, mx, my);
                text.blur();
            }
            case TEXT ->
                    text.press(
                            (Option.Text) all.get(r.index).option,
                            r.index,
                            r.y,
                            listX + 16,
                            listW - 32,
                            mx,
                            my);
            default -> clickOption(all.get(r.index).option, r, mx, my, button);
        }
    }

    /** A click on an option row: its checkbox, its key label, its slider, or the option itself. */
    private void clickOption(Option o, Row r, int mx, int my, int button) {
        if (o == null) {
            return;
        }
        text.blur();
        int[] box = Widgets.checkChip(o, listX + 10, r.y, listW - 20, r.h);
        if (box != null && mx >= box[0] - 2 && mx < box[0] + box[2] + 2) {
            o.check().toggle(); // the row's checkbox: ticks and unticks, without
            // touching the option
            Ui.click();
            return;
        }
        int[] chip = Widgets.keyChip(o, listX + 10, r.y, listW - 20, r.h);
        if (chip != null && mx >= chip[0] - 2 && mx < chip[0] + chip[2] + 2) {
            // On the key label: waits for the next key (left) or clears (right).
            KeyLabels.stopListening(page(), o.key());
            o.key().click(button != 1);
            Ui.click();
            return;
        }
        // On the track: the value goes where the mouse is, and follows the drag.
        if (!slider.press(o, button, mx, listX + 10, listW - 20)) {
            boolean back = button == 1 || Widgets.backArrow(o, listX + 10, listW - 20, mx);
            o.click(!back);
        }
        Ui.click();
    }

    /** The field being edited, or null. */
    private Option.Text editing() {
        Page page = page();
        if (text.focused < 0 || page == null || text.focused >= page.entries().size()) {
            return null;
        }
        return page.entries().get(text.focused).option instanceof Option.Text t ? t : null;
    }

    /**
     * The cursor driving the scroll: where it is minus where the bar was grabbed, on the scale of
     * the whole list.
     */
    private void scrollTo(int my) {
        int barH = Math.max(20, listH * listH / Math.max(1, contentH));
        int room = listH - barH;
        scroll =
                room <= 0
                        ? 0
                        : Math.max(
                                0,
                                Math.min(
                                        maxScroll(), (my - dragGrab - listY) * maxScroll() / room));
    }

    @Override
    protected void mouseClickMove(int mx, int my, int button, long held) {
        if (dragging) {
            scrollTo(my);
            return;
        }
        if (slider.drag(mx)) {
            return;
        }
        Option.Text t = editing();
        if (text.selecting && t != null) {
            text.caret = text.indexAt(t, mx);
            return;
        }
        super.mouseClickMove(mx, my, button, held);
    }

    @Override
    protected void mouseReleased(int mx, int my, int button) {
        dragging = false;
        text.selecting = false;
        slider.release();
        super.mouseReleased(mx, my, button);
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll + (wheel > 0 ? -24 : 24)));
        }
    }

    @Override
    protected void keyTyped(char c, int key) {
        if (KeyLabels.keyPressed(page(), key)) {
            return;
        }
        if (key == Keyboard.KEY_ESCAPE) {
            if (editing()
                    != null) { // Esc with a field open releases the field; only the second closes
                // the Panel
                text.blur();
                return;
            }
            this.mc.displayGuiScreen(parent);
            return;
        }
        if (text.focused < 0 && keyScroll(key)) {
            return;
        }
        Page page = page();
        if (text.focused < 0
                || page == null
                || text.focused >= page.entries().size()
                || !(page.entries().get(text.focused).option instanceof Option.Text t)) {
            super.keyTyped(c, key);
            return;
        }
        text.keyEdit(t, c, key);
    }

    /** Arrows and page keys scroll the list (with no field open). True if the key was one. */
    private boolean keyScroll(int key) {
        int step = key == KeyCodes.NEXT || key == KeyCodes.PRIOR ? listH - 20 : 24;
        if (key == KeyCodes.UP || key == KeyCodes.PRIOR) {
            scroll = Math.max(0, scroll - step);
            return true;
        }
        if (key == KeyCodes.DOWN || key == KeyCodes.NEXT) {
            scroll = Math.min(maxScroll(), scroll + step);
            return true;
        }
        return false;
    }
}
