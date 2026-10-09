/*
 * Copyright (c) 2026 Breno Xavier. All rights reserved.
 * Proprietary software: see the LICENSE file at the root of this repository.
 */

package dev.xavier.stein.loader.gui;

import dev.xavier.stein.loader.api.Option;
import dev.xavier.stein.loader.api.Page;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

import java.util.List;
import java.util.Locale;

/**
 * The drawing of the {@link PanelScreen}: the header, the rail of pages, the content in cards and
 * the scrollbar. Each frame it also records where each row ended up ({@link PanelBase#rows}), for
 * the input.
 */
abstract class PanelDrawing extends PanelBase {

    PanelDrawing(List<Page> pages, GuiScreen parent, String only) {
        super(pages, parent, only);
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

    void header(int mx, int my, float p) {
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
    void rail(int mx, int my, float p, long delta) {
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
    void icon(Page page, float cx, float cy, float size, int color) {
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
    void content(int mx, int my, float p, long delta) {
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
    int card(
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
    void sectionTitle(
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
    void moreRow(
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

    /**
     * A row: the name on the left and the control on the right (the same as the screen editor's).
     */
    void row(
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

    void scrollbar(int mx, int my, float p) {
        if (maxScroll() <= 0) {
            return;
        }
        int h = Math.max(20, listH * listH / Math.max(1, contentH));
        int y = listY + (listH - h) * scroll / maxScroll();
        boolean hot = dragging || overBar(mx, my);
        Draw2D.rect(barX() + 1, y, 1.0F, h, Ui.alpha(hot ? Theme.GOLD : Theme.RULE_STRONG, p));
    }
}
