/*
 * Copyright (c) 2026 Breno Xavier. All rights reserved.
 * Proprietary software: see the LICENSE file at the root of this repository.
 */

package dev.xavier.stein.loader.hud;

/**
 * Position of an element on screen from its anchor, and the way back. The anchor
 * (left/centre/right, top/middle/bottom) makes the element follow the right edge when the window or
 * the GUI scale changes. With margin m, the anchors at the ends count from it and no box goes past
 * it (the in-game part, in the game's own place, uses m = 0).
 */
final class HudLayout {

    private HudLayout() {}

    /** Left corner of a box of width w on a screen of width screen, with margin m. */
    static int left(Placement p, int w, int screen, int m) {
        int mm = inset(w, screen, m);
        return clamp(edge(p.anchorX, p.x, w, screen, mm), mm, screen - w - mm);
    }

    /** Top of a box of height h on a screen of height screen, with margin m. */
    static int top(Placement p, int h, int screen, int m) {
        int mm = inset(h, screen, m);
        return clamp(edge(p.anchorY, p.y, h, screen, mm), mm, screen - h - mm);
    }

    /** The margin m; smaller when the box does not fit between the two margins. */
    static int inset(int size, int screen, int m) {
        return Math.max(0, Math.min(m, (screen - size) / 2));
    }

    private static int edge(int anchor, int offset, int size, int screen, int m) {
        switch (anchor) {
            case 1:
                return screen / 2 + offset - size / 2;
            case 2:
                return screen - m + offset - size;
            default:
                return m + offset;
        }
    }

    /**
     * Records the box's position: the anchor comes from the third of the screen where the box's
     * centre lies.
     */
    static void store(
            Placement p, int left, int top, int w, int h, int screenW, int screenH, int m) {
        p.anchorX = third(left + w / 2, screenW);
        p.anchorY = third(top + h / 2, screenH);
        p.x = offset(p.anchorX, left, w, screenW, inset(w, screenW, m));
        p.y = offset(p.anchorY, top, h, screenH, inset(h, screenH, m));
    }

    private static int third(int center, int screen) {
        return center < screen / 3 ? 0 : center < screen * 2 / 3 ? 1 : 2;
    }

    private static int offset(int anchor, int pos, int size, int screen, int m) {
        switch (anchor) {
            case 1:
                return pos + size / 2 - screen / 2;
            case 2:
                return pos + size - (screen - m);
            default:
                return pos - m;
        }
    }

    static int clamp(int v, int min, int max) {
        return max < min ? min : Math.max(min, Math.min(max, v));
    }
}
