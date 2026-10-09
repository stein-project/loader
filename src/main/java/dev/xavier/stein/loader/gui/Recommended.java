/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.loader.gui;

import dev.xavier.stein.loader.Remote;

import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiSlot;
import net.minecraft.client.gui.ServerSelectionList;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.resources.I18n;

import java.lang.reflect.Field;

/**
 * The divider of the multiplayer list: on top, what Stein Labs recommends; below, the servers of
 * the player.
 *
 * <p>The list is still the game's own — the recommended servers are in {@code servers.dat} like any
 * other, so joining, editing, moving and deleting work as always, with no shifted index or
 * surprise. All the Loader does here is draw on top: a line in the gap between two rows of the list
 * (the list leaves 4 px between them) and two small badges saying whose each part is.
 *
 * <p>How many are recommended comes from the list itself: it counts from the top down while the
 * address is one of the published ones. If the player moves one of them to the middle, the count
 * stops there — the divider follows what is on screen, not what the public file says.
 */
public final class Recommended {

    /** Height of a list row, and the gap it leaves below (ServerSelectionList: 36 and 4). */
    private static final int ROW = 36;

    private static final int GAP = 4;

    private static Field list;
    private static Field top;
    private static Field bottom;
    private static Field left;
    private static Field width;
    private static Field scrolled;
    private static Field header;

    private Recommended() {}

    /** Gap at the top of the list content, where the top badge fits. */
    private static final int HEAD = 18;

    /**
     * Before the screen draws itself: opens the gap at the top of the list content. It is the same
     * gap the game uses for a list header (GuiSlot.headerPadding), so the dark area stays where it
     * was and it is the rows that start lower — the badge sits inside it, with a margin from the
     * edge, and scrolls along with the rows.
     */
    public static void beforeDrawScreen(Object screen) {
        if (!(screen instanceof GuiMultiplayer)) {
            return;
        }
        GuiMultiplayer gui = (GuiMultiplayer) screen;
        try {
            GuiSlot slot = slot(gui);
            if (slot == null) {
                return;
            }
            int want = count(gui) == 0 ? 0 : HEAD;
            if (header.getInt(slot) != want) {
                header.setInt(slot, want);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            System.out.println("[loader] server list padding: " + e);
        }
    }

    /** After the screen draws itself. */
    public static void afterDrawScreen(Object screen) {
        if (!(screen instanceof GuiMultiplayer)) {
            return;
        }
        GuiMultiplayer gui = (GuiMultiplayer) screen;
        int count = count(gui);
        if (count == 0) {
            return;
        }
        try {
            GuiSlot slot = slot(gui);
            if (slot == null) {
                return;
            }
            int t = top.getInt(slot);
            int b = bottom.getInt(slot);
            int w = width.getInt(slot);
            int lw = ((ServerSelectionList) slot).getListWidth();
            int x = left.getInt(slot) + w / 2 - lw / 2;
            int base = t + GAP - (int) scrolled.getFloat(slot) + HEAD;
            // The top one stays fixed in the gap and does not scroll with the rows: it is the
            // title of the part, and a title does not leave the view. The bottom one follows the
            // rows, because it marks where one part ends and the other begins.
            Ui.clip(0, t, w, b - t);
            chip(I18n.format("steinloader.servers.ours"), x, base - HEAD / 2 - 2, lw, t, b);
            if (count < gui.getServerList().countServers()) {
                chip(I18n.format("steinloader.servers.yours"), x, base + count * ROW - 2, lw, t, b);
            }
            Ui.unclip();
        } catch (ReflectiveOperationException | RuntimeException e) {
            System.out.println("[loader] server list divider: " + e);
        }
    }

    /** How many servers at the top of the list are the recommended ones. */
    private static int count(GuiMultiplayer gui) {
        ServerList servers = gui.getServerList();
        if (servers == null) {
            return 0;
        }
        int n = 0;
        while (n < servers.countServers() && Remote.recommends(servers.getServerData(n).serverIP)) {
            n++;
        }
        return n;
    }

    /**
     * The row with the badge centered: it is what separates one part from the other without taking
     * height from any row.
     */
    private static void chip(String label, int x, int y, int w, int listTop, int listBottom) {
        if (y < listTop - 2 || y > listBottom) {
            return;
        }
        Ui.hair(x, y, w, Theme.RULE_STRONG);
        int tw = Ui.width(label);
        int cx = x + (w - tw) / 2;
        Draw2D.rect(cx - 5, y - 5, tw + 10, 11, Theme.BG_TOOLTIP);
        Ui.label(label, cx, y - 3, Theme.GOLD);
    }

    /**
     * The screen's list, found by type: the field name changes with the mapping, the type does not.
     */
    private static GuiSlot slot(GuiMultiplayer gui) throws ReflectiveOperationException {
        if (list == null) {
            for (Field f : GuiMultiplayer.class.getDeclaredFields()) {
                if (ServerSelectionList.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    list = f;
                    break;
                }
            }
            if (list == null) {
                return null;
            }
            top = field("field_148153_b");
            bottom = field("field_148154_c");
            width = field("field_148155_a");
            left = field("field_148152_e");
            scrolled = field("field_148169_q");
            header = field("field_148160_j"); // headerPadding: the gap of the list header
        }
        return (GuiSlot) list.get(gui);
    }

    private static Field field(String name) throws ReflectiveOperationException {
        Field f = GuiSlot.class.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }
}
