/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.loader.gui;

import dev.xavier.stein.loader.Loader;
import dev.xavier.stein.loader.Remote;

import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.List;

/**
 * Stein Labs notices in the corner of the main menu: the <b>update</b>, when a new version is out,
 * and the <b>news</b> published in the project file.
 *
 * <p>Both come from the same public file ({@link Remote}), so announcing news — or warning about a
 * version — needs no new Loader. Each has its own "already seen": the notice stores its {@code id}
 * and the update stores the announced version (in {@code config/steinloader.json}), because a
 * warning that reappears on every start turns into noise, and noise gets learned to be ignored.
 * Closing one does not hide the other.
 *
 * <p>With an address, the whole bubble opens the browser; the address goes through {@link Remote}'s
 * check: https, and only for the project's places.
 */
public final class News {

    private static final int W = 176;
    private static final int PAD = 7;

    /** Slides up when the screen opens. */
    private static final int ANIM_MS = 260;

    /** Color of the left strip: the update's draws more attention than the notice's. */
    private static final int MARK_UPDATE = Theme.GOLD;

    private static long shownAt;
    private static String showing = "";

    /** Where each bubble ended up in the drawn frame: {x, y, height, close X in x, X in y}. */
    private static int[] updateBox;

    private static int[] newsBox;

    private News() {}

    /** The update to announce, or null (there is none, or the player already closed this one). */
    private static Remote.Update update() {
        Remote.Update u = Remote.update();
        Loader loader = Loader.get();
        return u == null || loader == null || u.version.equals(loader.seenUpdate()) ? null : u;
    }

    /** The notice to show, or null. */
    private static Remote.News news() {
        Remote.News n = Remote.news();
        Loader loader = Loader.get();
        return n == null || loader == null || n.id.equals(loader.readNews()) ? null : n;
    }

    /** After the screen draws itself; main menu only. */
    public static void afterDrawScreen(Object screen, int mx, int my) {
        updateBox = null;
        newsBox = null;
        if (!(screen instanceof GuiMainMenu)) {
            return;
        }
        Remote.Update update = update();
        Remote.News news = news();
        if (update == null && news == null) {
            return;
        }
        // The animation restarts when what is being shown changes.
        String now = (update == null ? "" : update.version) + "|" + (news == null ? "" : news.id);
        if (!now.equals(showing)) {
            showing = now;
            shownAt = Ui.now();
        }
        GuiScreen gui = (GuiScreen) screen;
        float p = Ui.ease(Math.min(1.0F, (Ui.now() - shownAt) / (float) ANIM_MS));
        // Top corner, on the right: the menu buttons are at the bottom, and a bubble over them
        // would get in the
        // way of clicks from someone who just wants to play.
        int x = gui.width - W - 6;
        int y = 6 - Math.round((1.0F - p) * 12);
        if (update != null) {
            updateBox =
                    balloon(
                            x,
                            y,
                            I18n.format("steinloader.update.title"),
                            I18n.format(
                                    "steinloader.update.text", update.version, Remote.installed()),
                            MARK_UPDATE,
                            true,
                            mx,
                            my,
                            p);
            y += updateBox[2] + 6;
        }
        if (news != null) {
            newsBox =
                    balloon(
                            x,
                            y,
                            news.title(),
                            news.text(),
                            Discord.BLURPLE,
                            !news.url.isEmpty(),
                            mx,
                            my,
                            p);
        }
    }

    /**
     * A bubble: strip, title, up to three lines and the close X. Returns {x, y, height, closeX,
     * closeY}.
     */
    private static int[] balloon(
            int x,
            int y,
            String title,
            String text,
            int mark,
            boolean clickable,
            int mx,
            int my,
            float p) {
        List<String> lines = wrap(text, W - PAD * 2);
        int h = PAD * 2 + 10 + lines.size() * 10;
        boolean hover = clickable && mx >= x && mx < x + W && my >= y && my < y + h;
        // The bubble: the design system's card, with the notice mark on the left.
        Draw2D.rect(x, y, W, h, Ui.alpha(hover ? 0xFA120B22 : 0xF20A0614, p));
        Ui.frame(x, y, W, h, Ui.alpha(hover ? Theme.GOLD : Theme.RULE_STRONG, p));
        Draw2D.rect(x, y, 1.5F, h, Ui.alpha(mark, p));
        Ui.title(
                Ui.trim(title, W - PAD * 2 - 8),
                x + PAD,
                y + PAD,
                Theme.TEXT,
                Ui.alpha(Theme.INK, p));
        int ty = y + PAD + 12;
        for (String line : lines) {
            Ui.text(line, x + PAD, ty, Ui.alpha(hover ? Theme.INK : Theme.MUTED, p));
            ty += 10;
        }
        int cx = x + W - 8;
        int cy = y + 8;
        boolean overClose = Math.abs(mx - cx) <= 5 && Math.abs(my - cy) <= 5;
        Icons.close(cx, cy, 6.0F, Ui.alpha(overClose ? Theme.INK : Theme.FAINT, p));
        return new int[] {x, y, h, cx, cy};
    }

    /** Mouse event of the screen: close at the X, or open the address by clicking the bubble. */
    public static boolean mouseInput(Object screen) {
        if (!(screen instanceof GuiMainMenu)
                || !Mouse.getEventButtonState()
                || Mouse.getEventButton() != 0) {
            return false;
        }
        GuiScreen gui = (GuiScreen) screen;
        int mx = Ui.mouseX(gui);
        int my = Ui.mouseY(gui);
        Remote.Update update = update();
        if (update != null && hit(updateBox, mx, my)) {
            Ui.click();
            if (!closing(updateBox, mx, my)) {
                Discord.open(update.url);
            }
            Loader.get().seenUpdate(update.version);
            return true;
        }
        Remote.News news = news();
        if (news != null && hit(newsBox, mx, my)) {
            Ui.click();
            if (!closing(newsBox, mx, my) && !news.url.isEmpty()) {
                Discord.open(news.url);
            }
            Loader.get().readNews(news.id);
            return true;
        }
        return false;
    }

    /** Did the click land on this bubble (on it or on its X)? */
    private static boolean hit(int[] box, int mx, int my) {
        return box != null
                && (closing(box, mx, my)
                        || mx >= box[0] && mx < box[0] + W && my >= box[1] && my < box[1] + box[2]);
    }

    private static boolean closing(int[] box, int mx, int my) {
        return box != null && Math.abs(mx - box[3]) <= 6 && Math.abs(my - box[4]) <= 6;
    }

    /** Wraps the text to the bubble's width, without cutting a word in the middle. */
    private static List<String> wrap(String text, int w) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split("\\s+")) {
            String next = line.length() == 0 ? word : line + " " + word;
            if (Ui.width(next) > w && line.length() > 0) {
                out.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(next);
            }
            if (out.size() >= 3) {
                break; // three lines are enough: it is a notice, not a text
            }
        }
        if (out.size() < 3 && line.length() > 0) {
            out.add(line.toString());
        }
        return out;
    }
}
