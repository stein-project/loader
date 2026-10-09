/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.loader.hud;

import dev.xavier.stein.loader.LoaderOptions;
import dev.xavier.stein.loader.api.HudElement;
import dev.xavier.stein.loader.api.Option;
import dev.xavier.stein.loader.gui.Draw2D;
import dev.xavier.stein.loader.gui.Icons;
import dev.xavier.stein.loader.gui.KeyCodes;
import dev.xavier.stein.loader.gui.Theme;
import dev.xavier.stein.loader.gui.Ui;
import dev.xavier.stein.loader.gui.Widgets;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Screen editor: everything that belongs to the screen is done here, looking at the screen.
 *
 * <p>Dragging moves, the wheel changes the size, the arrows do fine adjustment and the <b>right
 * button on an element opens its options</b>, right there. The bar has the list of everything that
 * exists (to turn on and off what is not on screen, which could not be clicked), the grid, the
 * snapping and "Reset to default".
 *
 * <p>These options used to live in the Panel, in a list of names: to change the compass you had to
 * leave the screen, find the name in a list and imagine the effect. What belongs to the screen
 * lives on the screen; the Panel kept what does not (Ctrl with this key opens it).
 *
 * <p>When dragging, it snaps to the grid, the edges, the centre and the neighbours (Shift releases
 * the snapping). Nothing sits on top of anything: a move that would touch another element does not
 * happen, and with something touching, Done is locked and leaving goes back to what was there.
 */
final class HudEditor extends GuiScreen {

    private static final int BAR_W = 116;
    private static final int BAR_ROW = 20;
    private static final int FLIP = 18;
    private static final int[] GRIDS = {0, 4, 8, 16};

    /** The options card and the list of elements. */
    private final HudEditorCard card = new HudEditorCard();

    private long lastFrame;

    private HudElement dragging;
    private HudElement selected;
    private int grabX;
    private int grabY;
    private boolean toolbarLeft;
    private boolean sidePicked;
    private int barX;
    private int barY;
    private Set<HudElement> overlaps = Collections.emptySet();

    /** The screen the editor was opened from. */
    private final GuiScreen parent;

    /**
     * The whole-screen "Reset to default", kept so the confirmation question lasts across frames.
     */
    private final Option restoreAll = Option.restore(HudOptions::restoreAll);

    private final List<int[]> barRows = new ArrayList<>();

    HudEditor(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        barY = 4;
        // Test: -Dsteinloader.diag.editorOptions=<id> opens that element's options directly.
        String diag = LoaderOptions.EDITOR_OPTIONS;
        if (diag != null && !card.showsOptions()) {
            for (HudElement e : HudRegistry.elements) {
                if (e.id().equals(diag)) {
                    selected = e;
                    card.openOptions(e);
                }
            }
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    // ---- drawing ----

    @Override
    public void drawScreen(int mx, int my, float partialTicks) {
        long now = Ui.now();
        long delta = lastFrame == 0L ? 16L : now - lastFrame;
        lastFrame = now;
        int width = this.width;
        int height = this.height;
        Gui.drawRect(0, 0, width, height, 0x600A0614);
        drawGrid(width, height);

        try {
            for (HudElement e : HudRegistry.elements) {
                Placement p = HudRegistry.placement(e);
                if (!p.enabled) {
                    HudRegistry.box(e)[2] = 0;
                    continue;
                }
                HudGrid.draw(e, p, width, height, true, partialTicks);
            }
        } finally {
            HudGrid.resetState();
        }
        overlaps = HudCollision.overlapping();
        if (!sidePicked) {
            sidePicked = true;
            toolbarLeft = covered(false) && !covered(true);
        }

        boolean open = card.isOpen();
        HudElement hover = open ? null : dragging != null ? dragging : pick(mx, my);
        for (HudElement e : HudRegistry.elements) {
            int[] b = HudRegistry.box(e);
            if (b[2] == 0) {
                continue;
            }
            Placement p = HudRegistry.placement(e);
            int r = b[0] + b[2];
            int bottom = b[1] + b[3];
            // Each element's frame, as a wire: the accent on the chosen one, the ink on the one
            // under the cursor.
            int color =
                    overlaps.contains(e)
                            ? Theme.DANGER
                            : e == selected ? Theme.ACCENT : e == hover ? Theme.INK : 0x66E8E1F5;
            if (e == selected) {
                Draw2D.rect(b[0], b[1], b[2], b[3], Theme.ACCENT_SOFT);
            }
            outline(b[0], b[1], r, bottom, color);
            if (e == hover || e == selected || overlaps.contains(e)) {
                String label = e.name() + (p.scale != 1.0F ? "  " + fmtScale(p.scale) : "");
                int ly = b[1] >= 11 ? b[1] - 10 : bottom + 2;
                // The name on a label: the design system's label over the field, to read over the
                // world.
                int lw = Ui.labelWidth(label) + 6;
                Draw2D.rect(b[0] - 1, ly - 1, lw, 10, Theme.BG_POPUP);
                Ui.label(
                        label,
                        b[0] + 2,
                        ly,
                        overlaps.contains(e)
                                ? Theme.DANGER
                                : e == selected ? Theme.GOLD : Theme.INK);
            }
        }
        // The bar and the card go in front of the elements' items (drawn with depth).
        GlStateManager.clear(GL11.GL_DEPTH_BUFFER_BIT);
        toolbar(mx, my, delta);
        if (open) {
            // The elements' items (armour, inventory) are drawn with depth, in front of the
            // interface plane: without clearing, they showed up over the options card.
            GlStateManager.clear(GL11.GL_DEPTH_BUFFER_BIT);
            card.draw(width, height, mx, my, delta);
        }
    }

    /** The bar: the few things that apply to the whole screen. */
    private void toolbar(int mx, int my, long delta) {
        barRows.clear();
        barX = toolbarLeft ? 6 : this.width - BAR_W - 6;
        int rows = 5;
        int h = rows * BAR_ROW + 12;
        Widgets.card(barX, barY, BAR_W, h, 1.0F);
        int y = barY + 6;
        String[] labels = {
            I18n.format("steinloader.hud.elementsTitle"),
            I18n.format("steinloader.hud.grid")
                    + ": "
                    + (HudRegistry.grid == 0 ? I18n.format("gui.no") : HudRegistry.grid + " px"),
            I18n.format("steinloader.hud.snap")
                    + ": "
                    + I18n.format(HudRegistry.snap ? "gui.yes" : "gui.no"),
            null, // restore, drawn separately (it has an icon and a prompt)
            overlaps.isEmpty()
                    ? I18n.format("gui.done")
                    : "§c" + I18n.format("steinloader.hud.overlap"),
        };
        for (int i = 0; i < rows; i++) {
            boolean hover =
                    mx >= barX + 4 && mx < barX + BAR_W - 4 && my >= y && my < y + BAR_ROW - 2;
            if (i == 3) {
                if (hover) {
                    Draw2D.rect(barX + 4, y, BAR_W - 8, BAR_ROW - 2, Widgets.SURFACE_HOVER);
                }
                Widgets.restore(restoreAll, barX + 4, y, BAR_W - 8, BAR_ROW - 2, hover, 1.0F);
            } else {
                Widgets.button(
                        labels[i],
                        barX + 4,
                        y,
                        BAR_W - 8,
                        BAR_ROW - 2,
                        hover,
                        i == 4 && !overlaps.isEmpty(),
                        1.0F);
            }
            barRows.add(new int[] {y, BAR_ROW - 2, i});
            y += BAR_ROW;
        }
        // Flipping sides: an element under the bar would get stuck, because every click there is
        // the bar's.
        int fx = toolbarLeft ? barX + BAR_W + 2 : barX - FLIP - 2;
        boolean fh = mx >= fx && mx < fx + FLIP && my >= barY && my < barY + BAR_ROW;
        Widgets.card(fx, barY, FLIP, BAR_ROW, 1.0F);
        Icons.chevron(
                fx + FLIP / 2.0F,
                barY + BAR_ROW / 2.0F,
                9.0F,
                toolbarLeft ? -90.0F : 90.0F,
                fh ? Theme.GOLD : Theme.MUTED);
        barRows.add(new int[] {barY, BAR_ROW, 5, fx});
    }

    private static String fmtScale(float s) {
        return HudOptions.decimal(String.format(java.util.Locale.ROOT, "%.1fx", s));
    }

    private void drawGrid(int width, int height) {
        int g = HudRegistry.grid;
        if (g > 0) {
            // The grid in true one-pixel wires: thin enough not to fight with what is being
            // arranged.
            for (int x = g; x < width; x += g) {
                Ui.hairV(x, 0, height, 0x24E8E1F5);
            }
            for (int y = g; y < height; y += g) {
                Ui.hair(0, y, width, 0x24E8E1F5);
            }
        }
        Ui.hairV(width / 2.0F, 0, height, 0x807C4DFF);
        Ui.hair(0, height / 2.0F, width, 0x807C4DFF);
        int m = HudGrid.MARGIN;
        outline(m, m, width - m, height - m, 0x60B89CD9);
    }

    private static void outline(int l, int t, int r, int b, int color) {
        Ui.frame(l - 1, t - 1, r - l + 2, b - t + 2, color);
    }

    /** Is any element that is on under the bar, if it ends up on this side? */
    private boolean covered(boolean left) {
        int x = left ? 6 : this.width - BAR_W - 6;
        int right = x + BAR_W + FLIP + 2;
        int bottom = barY + 5 * BAR_ROW + 12;
        for (HudElement e : HudRegistry.elements) {
            int[] b = HudRegistry.box(e);
            if (b[2] > 0
                    && b[0] < right
                    && b[0] + b[2] > x - FLIP - 2
                    && b[1] < bottom
                    && b[1] + b[3] > barY) {
                return true;
            }
        }
        return false;
    }

    private HudElement pick(int mx, int my) {
        for (int i = HudRegistry.elements.size() - 1; i >= 0; i--) {
            HudElement e = HudRegistry.elements.get(i);
            int[] b = HudRegistry.box(e);
            if (b[2] > 0 && mx >= b[0] && my >= b[1] && mx < b[0] + b[2] && my < b[1] + b[3]) {
                return e;
            }
        }
        return null;
    }

    /**
     * Leaves saving, <b>always</b>.
     *
     * <p>Before, with something on top of something, leaving put everything back to how it was when
     * the editor opened — and that is what really happened on the screens of people who use it: the
     * editor measures each element by its <b>example</b> (the armour with all four pieces, effects
     * with effects), which is bigger than what shows up in play, so a tidy screen showed up here
     * with half a dozen elements in red. From then on nothing more was saved: Done stayed locked,
     * the player left with Esc, and every editing session went away whole, without a word. One
     * player spent days rearranging the screen and thinking the Loader did not save.
     *
     * <p>The warning remains — elements that touch turn red and the button says "Overlap" — but as
     * a warning, not as a punishment: two things touching are visible and the player fixes it
     * whenever they want; lost work does not come back.
     */
    private void close() {
        HudPersistence.save();
        this.mc.displayGuiScreen(parent);
    }

    // ---- input ----

    @Override
    protected void mouseClicked(int mx, int my, int button) {
        if (card.isOpen()) {
            HudElement picked = card.click(mx, my, button);
            if (picked != null) {
                selected = picked;
            }
            return;
        }
        for (int[] r : barRows) {
            int x = r.length > 3 ? r[3] : barX + 4;
            int w = r.length > 3 ? FLIP : BAR_W - 8;
            if (my < r[0] || my >= r[0] + r[1] || mx < x || mx >= x + w) {
                continue;
            }
            barClick(r[2]);
            return;
        }
        HudElement e = pick(mx, my);
        selected = e;
        if (e == null) {
            return;
        }
        if (button == 0) {
            dragging = e;
            int[] b = HudRegistry.box(e);
            grabX = mx - b[0];
            grabY = my - b[1];
        } else if (button == 1) {
            dragging = null;
            card.openOptions(e); // the element's options, right here
        }
    }

    private void barClick(int which) {
        Ui.click();
        switch (which) {
            case 0 -> card.openList();
            case 1 -> {
                int i = 0;
                while (i < GRIDS.length && GRIDS[i] != HudRegistry.grid) {
                    i++;
                }
                HudRegistry.grid = GRIDS[(i + 1) % GRIDS.length];
            }
            case 2 -> HudRegistry.snap = !HudRegistry.snap;
            case 3 -> restoreAll.click(true);
            // Closes with or without overlap: the locked button left Esc as the only way out, and
            // Esc was precisely what threw the edit away.
            case 4 -> close();
            default -> toolbarLeft = !toolbarLeft;
        }
    }

    @Override
    protected void mouseClickMove(int mx, int my, int button, long held) {
        if (card.drag(mx)) {
            return;
        }
        if (dragging == null || button != 0 || card.isOpen()) {
            return;
        }
        move(dragging, mx - grabX, my - grabY, HudRegistry.snap && !shift());
    }

    @Override
    protected void mouseReleased(int mx, int my, int state) {
        dragging = null;
        card.release();
    }

    /**
     * Moves the element's box to (left, top), with snapping, and saves through the anchor. Touching
     * another element, it tries without snapping and on a single axis (slides along the neighbour);
     * if nothing works, it stays where it is.
     */
    private void move(HudElement e, int left, int top, boolean snap) {
        int width = this.width;
        int height = this.height;
        int[] b = HudRegistry.box(e);
        int m = HudLayout.inset(b[2], width, HudGrid.MARGIN);
        int mv = HudLayout.inset(b[3], height, HudGrid.MARGIN);
        int[][] tries =
                snap
                        ? new int[][] {
                            {
                                snapAxis(e, left, b[2], width, m, true),
                                snapAxis(e, top, b[3], height, mv, false)
                            },
                            {left, top},
                            {left, b[1]},
                            {b[0], top}
                        }
                        : new int[][] {{left, top}, {left, b[1]}, {b[0], top}};
        for (int[] t : tries) {
            int l = HudLayout.clamp(t[0], m, width - b[2] - m);
            int tp = HudLayout.clamp(t[1], mv, height - b[3] - mv);
            if (HudCollision.blocker(e, new int[] {l, tp, b[2], b[3]}, false) == null) {
                HudLayout.store(
                        HudRegistry.placement(e), l, tp, b[2], b[3], width, height, HudGrid.MARGIN);
                HudPersistence.touch();
                b[0] = l;
                b[1] = tp;
                return;
            }
        }
    }

    /**
     * Grid edges, screen centre and neighbours' edges (within 2 px) attract from close by; outside
     * them, the grid.
     */
    private static int snapAxis(
            HudElement e, int pos, int size, int screen, int m, boolean horizontal) {
        int magnet = Math.max(3, HudRegistry.grid);
        int best = pos;
        int bestDist = magnet + 1;
        int[] fixed = {m, screen - m - size, screen / 2 - size / 2};
        for (int i = 0; i < fixed.length; i++) {
            int d = Math.abs(pos - fixed[i]);
            int limit = i == 2 ? magnet / 2 + 1 : magnet;
            if (d <= limit && d < bestDist) {
                best = fixed[i];
                bestDist = d;
            }
        }
        for (HudElement o : HudRegistry.elements) {
            int[] r = HudRegistry.box(o);
            if (o == e || r[2] <= 0 || !HudRegistry.placement(o).enabled) {
                continue;
            }
            int lo = horizontal ? r[0] : r[1];
            int len = horizontal ? r[2] : r[3];
            int[] near = {lo + len + HudGrid.GAP, lo - HudGrid.GAP - size, lo, lo + len - size};
            for (int c : near) {
                int d = Math.abs(pos - c);
                if (d <= magnet && d < bestDist) {
                    best = c;
                    bestDist = d;
                }
            }
        }
        if (bestDist <= magnet) {
            return best;
        }
        int g = HudRegistry.grid;
        return g > 0 ? Math.round(pos / (float) g) * g : pos;
    }

    private static boolean shift() {
        return Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(KeyCodes.RSHIFT);
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) {
            return;
        }
        if (card.isOpen()) {
            card.scroll(wheel);
            return;
        }
        int mx = Ui.mouseX(this);
        int my = Ui.mouseY(this);
        HudElement e = pick(mx, my);
        if (e == null) {
            e = selected;
        }
        int[] b = e == null ? null : HudRegistry.box(e);
        if (b == null || b[2] == 0) {
            return;
        }
        // Scale in steps of 0.1, keeping the box centre in place; if it touches another, no change.
        Placement p = HudRegistry.placement(e);
        float old = p.scale;
        int[] oldBox = b.clone();
        float s = Math.round((p.scale + (wheel > 0 ? 0.1F : -0.1F)) * 10.0F) / 10.0F;
        s = Math.max(0.5F, Math.min(3.0F, s));
        int cx = b[0] + b[2] / 2;
        int cy = b[1] + b[3] / 2;
        p.scale = s;
        b[2] = Math.max(1, Math.round(e.width() * s));
        b[3] = Math.max(1, Math.round(e.height() * s));
        int margin = HudGrid.MARGIN;
        int left = HudLayout.clamp(cx - b[2] / 2, margin, this.width - b[2] - margin);
        int top = HudLayout.clamp(cy - b[3] / 2, margin, this.height - b[3] - margin);
        if (HudCollision.blocker(e, new int[] {left, top, b[2], b[3]}, false) != null) {
            p.scale = old;
            System.arraycopy(oldBox, 0, b, 0, 4);
            return;
        }
        HudLayout.store(p, left, top, b[2], b[3], this.width, this.height, HudGrid.MARGIN);
        HudPersistence.touch();
        b[0] = left;
        b[1] = top;
    }

    @Override
    protected void keyTyped(char c, int key) {
        // A key option on the card is waiting: it takes the key (Esc gives up, without closing the
        // card).
        if (card.keyPressed(key)) {
            return;
        }
        int editKey = HudGrid.key != null ? HudGrid.key.getKeyCode() : 0;
        if (key == Keyboard.KEY_ESCAPE || (key == editKey && key != 0)) {
            if (card.isOpen()) { // the first Esc closes only the card
                card.close();
                return;
            }
            close();
            return;
        }
        if (!card.isOpen() && selected != null && HudRegistry.box(selected)[2] > 0) {
            int step = shift() ? Math.max(1, HudRegistry.grid) : 1;
            int dx = key == KeyCodes.LEFT ? -step : key == KeyCodes.RIGHT ? step : 0;
            int dy = key == KeyCodes.UP ? -step : key == KeyCodes.DOWN ? step : 0;
            if (dx != 0 || dy != 0) {
                int[] b = HudRegistry.box(selected);
                move(selected, b[0] + dx, b[1] + dy, false);
                return;
            }
        }
        super.keyTyped(c, key);
    }
}
