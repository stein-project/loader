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

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
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
 * <p>The rail, the content, the scrollbar and the input share the card geometry and the chosen
 * page, so the Panel is one chain of classes: the state in {@link PanelBase}, the drawing in {@link
 * PanelDrawing}, and here the opening and the input. The text field, the lists of things and the
 * key labels are in {@link TextEditor}, {@link PanelLists} and {@link KeyLabels}.
 */
public final class PanelScreen extends PanelDrawing {

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
        super(pages, parent, only);
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
            // The row's checkbox ticks and unticks without touching the option.
            o.check().toggle();
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
