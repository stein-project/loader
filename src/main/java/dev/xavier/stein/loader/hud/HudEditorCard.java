/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.loader.hud;

import dev.xavier.stein.bridge.core.Log;
import dev.xavier.stein.loader.api.HudElement;
import dev.xavier.stein.loader.api.Option;
import dev.xavier.stein.loader.gui.Draw2D;
import dev.xavier.stein.loader.gui.Icons;
import dev.xavier.stein.loader.gui.SliderDrag;
import dev.xavier.stein.loader.gui.Theme;
import dev.xavier.stein.loader.gui.Ui;
import dev.xavier.stein.loader.gui.Widgets;

import net.minecraft.client.resources.I18n;

import java.util.ArrayList;
import java.util.List;

/**
 * The card in the middle of the {@link HudEditor}: an element's options (right-click on it), or the
 * list of every element, to turn on and off what is not on screen. The editor draws it over the
 * elements and hands it the input while it is open.
 */
final class HudEditorCard {

    /** In the middle of the screen, like the Panel. */
    private static final int CARD_W = 300;

    private static final int CARD_H = 260;
    private static final int ROW = Widgets.ROW;
    private static final int HEAD_H = 26;
    private static final int ANIM_MS = 160;

    /** The element whose options are open, or null. */
    private HudElement element;

    /** Whether the list of every element is open. */
    private boolean listing;

    private List<Option> optionRows = new ArrayList<>();

    /** The slider being dragged in the element's options. */
    private final SliderDrag slider = new SliderDrag();

    private int scroll;
    private long openedAt;

    /** Where each card line ended up, for clicks: y, height and the option index (or -1). */
    private final List<int[]> rows = new ArrayList<>();

    /** Whether the card is open (an element's options or the list). */
    boolean isOpen() {
        return element != null || listing;
    }

    /** Whether an element's options are open. */
    boolean showsOptions() {
        return element != null;
    }

    void openOptions(HudElement e) {
        element = e;
        listing = false;
        optionRows = HudOptions.options(e);
        scroll = 0;
        openedAt = Ui.now();
    }

    void openList() {
        listing = true;
        element = null;
        scroll = 0;
        openedAt = Ui.now();
    }

    void close() {
        element = null;
        listing = false;
        optionRows = new ArrayList<>();
    }

    /** Draws the card on a screen of this size. */
    void draw(int width, int height, int mx, int my, long delta) {
        rows.clear();
        float p = Ui.ease(Math.min(1.0F, (Ui.now() - openedAt) / (float) ANIM_MS));
        int w = Math.min(width - 20, CARD_W);
        int h = Math.min(height - 20, CARD_H);
        int x = (width - w) / 2;
        int y = (height - h) / 2 + Math.round((1.0F - p) * 8);
        Ui.rect(0, 0, width, height, Math.round(0x50 * p) << 24);
        Widgets.card(x, y, w, h, p);
        String title = listing ? I18n.format("steinloader.hud.elementsTitle") : element.name();
        Widgets.title(Ui.trim(title, w - 40), x + 10, y + 9, p);
        boolean closeHover = Widgets.closeHit(mx, my, x + w - 14, y + 13);
        if (closeHover) {
            Ui.circle(x + w - 14, y + 13, 9.0F, Widgets.SURFACE_HOVER);
        }
        Icons.close(x + w - 14, y + 13, 9.0F, Ui.alpha(closeHover ? Theme.INK : Theme.MUTED, p));

        int listY = y + HEAD_H;
        int listH = h - HEAD_H - 8;
        Ui.clip(x, listY, w, listH);
        int ry = listY - scroll;
        if (listing) {
            for (int i = 0; i < HudRegistry.elements.size(); i++) {
                HudElement e = HudRegistry.elements.get(i);
                boolean on = HudRegistry.placement(e).enabled;
                boolean hover =
                        mx >= x + 8
                                && mx < x + w - 8
                                && my >= ry
                                && my < ry + ROW
                                && my >= listY
                                && my < listY + listH;
                if (hover) {
                    Draw2D.rect(x + 6, ry, w - 12, ROW - 2, Ui.alpha(Widgets.SURFACE_HOVER, p));
                }
                Ui.text(
                        Ui.trim(e.name(), w - 70),
                        x + 10,
                        ry + 7,
                        Ui.alpha(on ? Widgets.TEXT : Widgets.TEXT_DIM, p));
                Widgets.knob("list|" + e.id(), x + w - 30, ry + ROW / 2 - 4, on, p, delta);
                rows.add(new int[] {ry, ROW, i});
                ry += ROW;
            }
        } else {
            for (int i = 0; i < optionRows.size(); i++) {
                Option o = optionRows.get(i);
                Widgets.option(
                        o,
                        "editor|" + element.id(),
                        new Widgets.Row(x + 10, ry, w - 20, ROW),
                        new Widgets.Pointer(mx, my, my >= listY && my < listY + listH),
                        p,
                        delta);
                rows.add(new int[] {ry, ROW, i});
                ry += ROW;
            }
        }
        Ui.unclip();
        int contentH = ry + scroll - listY;
        int max = Math.max(0, contentH - listH);
        scroll = Math.max(0, Math.min(max, scroll));
        if (max > 0) {
            int bh = Math.max(20, listH * listH / contentH);
            Draw2D.rect(
                    x + w - 4,
                    listY + (listH - bh) * scroll / max,
                    1.0F,
                    bh,
                    Ui.alpha(Theme.RULE_STRONG, p));
        }
        rows.add(new int[] {y, h, -1, x, w}); // the whole box, to tell what is a click outside
    }

    /**
     * A click while the card is open. Returns the element to select when the click opened its
     * options from the list, or null.
     */
    HudElement click(int mx, int my, int button) {
        int[] box = rows.isEmpty() ? null : rows.get(rows.size() - 1);
        if (box == null || box[2] != -1) {
            return null;
        }
        int x = box[3];
        int w = box[4];
        if (mx < x || mx >= x + w || my < box[0] || my >= box[0] + box[1]) {
            close(); // a click outside closes it
            return null;
        }
        if (Widgets.closeHit(mx, my, x + w - 14, box[0] + 13)) {
            Ui.click();
            close();
            return null;
        }
        for (int i = 0; i < rows.size() - 1; i++) {
            int[] r = rows.get(i);
            if (my < r[0] || my >= r[0] + r[1]) {
                continue;
            }
            Ui.click();
            if (listing) {
                HudElement e = HudRegistry.elements.get(r[2]);
                boolean on = HudRegistry.placement(e).enabled;
                // Clicking the switch turns it on and off; clicking the name opens the element's
                // options.
                if (mx >= x + w - 40) {
                    HudGrid.toggle(e, !on);
                } else if (on) {
                    openOptions(e);
                    return e;
                }
            } else {
                Option o = optionRows.get(r[2]);
                // On the track: the value goes where the mouse is, and follows the drag; saves on
                // release.
                if (slider.press(o, button, mx, x + 10, w - 20)) {
                    return null;
                }
                boolean back = button == 1 || Widgets.backArrow(o, x + 10, w - 20, mx);
                o.click(!back);
                changedOptions();
            }
            return null;
        }
        return null;
    }

    /** The slider being dragged follows the mouse; false when none is. */
    boolean drag(int mx) {
        return slider.drag(mx);
    }

    /** Mouse released: a dragged slider saves its value. */
    void release() {
        if (slider.release()) {
            changedOptions();
        }
    }

    /** The mouse wheel scrolls the card. */
    void scroll(int wheel) {
        scroll = Math.max(0, scroll + (wheel > 0 ? -20 : 20));
    }

    /** A key option on the card waiting for a key takes it; true when one did. */
    boolean keyPressed(int key) {
        if (element != null) {
            for (Option o : optionRows) {
                if (o instanceof Option.Key k && k.keyPressed(key)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Tells the element's owner that one of its options changed, so it can save the configuration.
     *
     * <p>This was missing: the Loader only notified on "restore defaults", so <b>every</b> mod
     * element option changed here — armour laid out horizontally, the minimap style, what the Tab
     * list shows — held until the game closed and then vanished. Whoever reported it thought it was
     * only the armour; it was everyone, and the mod's file never even came to exist.
     *
     * <p>It also applies to the common options (scale, with chat): they already save themselves in
     * the grid file, and notifying again only makes the mod rewrite what was already right.
     */
    private void changedOptions() {
        if (element == null) {
            return;
        }
        try {
            element.optionsChanged();
        } catch (Throwable t) {
            Log.info("loader", "HUD: " + element.id() + " could not save options: " + t);
        }
    }
}
