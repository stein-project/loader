/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.loader.gui;

import dev.xavier.stein.loader.api.Page;

import java.util.List;

/**
 * The two kinds of list a mod's page can hold besides options: rows of things (accounts, proxies:
 * name, detail and a trash can) and chips (small clickable labels that wrap). Drawing, heights and
 * clicks for the {@link PanelScreen}.
 */
final class PanelLists {

    /** Height of a mod list row (an account, a proxy). */
    static final int ITEM_H = 20;

    static final int CHIP_H = 16;

    private PanelLists() {}

    /**
     * The list of the mod's things (accounts, proxies): one row per thing, with the name on the
     * left, the detail on the right and the trash can at the end. The whole row selects; only the
     * trash can removes — two gestures, not a single click that might delete what you wanted to
     * use.
     */
    static void items(Page.Entry e, int x, int y, int w, int mx, int my, float p, boolean inList) {
        List<Page.Item> list = e.rows.get();
        if (list.isEmpty()) {
            Ui.text(
                    Ui.trim(e.empty == null ? "" : e.empty, w),
                    x + 8,
                    y + 6,
                    Ui.alpha(Theme.FAINT, p));
            return;
        }
        int iy = y;
        for (Page.Item it : list) {
            boolean hover = inList && mx >= x && mx < x + w && my >= iy && my < iy + ITEM_H - 2;
            boolean onTrash = it.remove != null && hover && mx >= x + w - 20;
            Widgets.surface(x, iy, w, ITEM_H - 2, hover && !onTrash, p);
            if (it.current) {
                // The one in use gets the left marker, like the chosen page on the rail.
                Draw2D.rect(x, iy, 1.5F, ITEM_H - 2, Ui.alpha(Theme.ACCENT, p));
            }
            int room = w - 16 - (it.remove != null ? 14 : 0);
            int dw = it.detail.isEmpty() ? 0 : Ui.width(it.detail) + 8;
            Ui.text(
                    Ui.trim(it.title, room - dw),
                    x + 8,
                    iy + 5,
                    Ui.alpha(it.current ? Theme.GOLD : hover ? Theme.INK : Theme.INK_SOFT, p));
            if (dw > 0) {
                Ui.text(it.detail, x + 8 + room - dw + 8, iy + 5, Ui.alpha(Theme.FAINT, p));
            }
            if (it.remove != null) {
                Icons.trash(
                        x + w - 11,
                        iy + ITEM_H / 2.0F - 1,
                        10.0F,
                        Ui.alpha(onTrash ? Theme.DANGER : Theme.FAINT, p));
            }
            iy += ITEM_H;
        }
    }

    /**
     * A click on a list of the mod's things: the trash can removes, the rest of the row selects.
     */
    static void clickRow(Page.Entry e, int x, int y, int w, int mx, int my) {
        List<Page.Item> list = e.rows.get();
        int i = (my - y) / ITEM_H;
        if (i < 0 || i >= list.size()) {
            return;
        }
        Page.Item it = list.get(i);
        if (it.remove != null && mx >= x + w - 20) {
            it.remove.run();
        } else if (it.use != null) {
            it.use.run();
        }
        Ui.click();
    }

    static void chips(Page.Entry e, int x, int y, int w, int mx, int my, float p, boolean inList) {
        List<String> items = e.items.get();
        if (items.isEmpty()) {
            return;
        }
        int cx = x;
        int cy = y + 2;
        for (String s : items) {
            int cw = Ui.width(s) + 12;
            if (cx > x && cx + cw > x + w) {
                cx = x;
                cy += CHIP_H + 2;
            }
            boolean hover = mx >= cx && mx < cx + cw && my >= cy && my < cy + CHIP_H && inList;
            int color = e.removes ? Theme.DANGER : Theme.GOLD;
            float cr = CHIP_H / 2.0F;
            if (hover) {
                Shapes.fill(cx, cy, cw, CHIP_H, cr, Ui.alpha(color & 0x00FFFFFF | 0x24000000, p));
            }
            Shapes.stroke(
                    cx, cy, cw, CHIP_H, cr, 1, Ui.alpha(hover ? color : Theme.RULE_STRONG, p));
            Ui.text(s, cx + 6, cy + 4, Ui.alpha(hover ? color : Theme.INK_SOFT, p));
            cx += cw + 3;
        }
    }

    static int chipLines(List<String> items, int w) {
        int n = 1;
        int x = 0;
        for (String s : items) {
            int cw = Ui.width(s) + 12;
            if (x > 0 && x + cw > w) {
                n++;
                x = 0;
            }
            x += cw + 3;
        }
        return n;
    }

    static void clickItem(Page.Entry e, int x, int y, int w, int mx, int my) {
        int cx = x;
        int cy = y + 2;
        for (String s : e.items.get()) {
            int cw = Ui.width(s) + 12;
            if (cx > x && cx + cw > x + w) {
                cx = x;
                cy += CHIP_H + 2;
            }
            if (mx >= cx && mx < cx + cw && my >= cy && my < cy + CHIP_H) {
                e.onClick.accept(s);
                Ui.click();
                return;
            }
            cx += cw + 3;
        }
    }
}
