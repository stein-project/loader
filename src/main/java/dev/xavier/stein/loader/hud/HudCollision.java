/*
 * Copyright (c) 2026 Breno Xavier. All rights reserved.
 * Proprietary software: see the LICENSE file at the root of this repository.
 */

package dev.xavier.stein.loader.hud;

import dev.xavier.stein.loader.api.HudElement;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Keeps the enabled elements apart: the minimum distance between boxes, who blocks whom, measuring
 * an element at its saved place, and finding the nearest free place.
 */
final class HudCollision {

    private HudCollision() {}

    /** Do the boxes touch, counting the minimum distance between them? */
    static boolean touch(int[] a, int[] b) {
        return a[0] < b[0] + b[2] + HudGrid.GAP
                && b[0] < a[0] + a[2] + HudGrid.GAP
                && a[1] < b[1] + b[3] + HudGrid.GAP
                && b[1] < a[1] + a[3] + HudGrid.GAP;
    }

    /**
     * Another enabled (and drawn) element that box r of e would touch, or null. With eAtDefault (e
     * is a game part in the game's place), the other parts in the game's place do not count: the
     * game puts them flush against each other.
     */
    static HudElement blocker(HudElement e, int[] r, boolean eAtDefault) {
        for (HudElement o : HudRegistry.elements) {
            if (o == e) {
                continue;
            }
            Placement po = HudRegistry.placement(o);
            int[] ro = HudRegistry.box(o);
            if (!po.enabled || ro[2] <= 0 || eAtDefault && HudRegistry.atDefault(o, po)) {
                continue;
            }
            if (touch(r, ro)) {
                return o;
            }
        }
        return null;
    }

    /** The enabled elements that touch another (the editor does not let it save like that). */
    static Set<HudElement> overlapping() {
        Set<HudElement> out = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int i = 0; i < HudRegistry.elements.size(); i++) {
            HudElement a = HudRegistry.elements.get(i);
            Placement pa = HudRegistry.placement(a);
            int[] ra = HudRegistry.box(a);
            if (!pa.enabled || ra[2] <= 0) {
                continue;
            }
            for (int j = i + 1; j < HudRegistry.elements.size(); j++) {
                HudElement b = HudRegistry.elements.get(j);
                Placement pb = HudRegistry.placement(b);
                int[] rb = HudRegistry.box(b);
                if (!pb.enabled
                        || rb[2] <= 0
                        || HudRegistry.atDefault(a, pa) && HudRegistry.atDefault(b, pb)) {
                    continue;
                }
                if (touch(ra, rb)) {
                    out.add(a);
                    out.add(b);
                }
            }
        }
        return out;
    }

    /**
     * Measures the element's box (with the editor's sample) at the saved place; false if there is
     * nothing to show.
     */
    static boolean measure(HudElement e, Placement p, int sw, int sh) {
        int[] b = HudRegistry.box(e);
        int gameFont = p.gameFont ? 1 : 0;
        HudText.enter(gameFont);
        try {
            if (!e.layout(true, 0.0F)) {
                b[2] = 0;
                return false;
            }
        } catch (RuntimeException | LinkageError ex) {
            b[2] = 0;
            return false;
        } finally {
            HudText.exit(gameFont);
        }
        HudRegistry.place(e, p, b, sw, sh);
        return true;
    }

    /** Does the element, at the saved place, touch another? */
    static boolean collides(HudElement e, int sw, int sh) {
        Placement p = HudRegistry.placement(e);
        return p.enabled
                && measure(e, p, sw, sh)
                && blocker(e, HudRegistry.box(e), HudRegistry.atDefault(e, p)) != null;
    }

    /**
     * Enables the element at its place; if it touches another, at the nearest free place. false
     * with no free place (stays disabled).
     */
    static boolean enable(HudElement e, int sw, int sh) {
        Placement p = HudRegistry.placement(e);
        p.enabled = true;
        if (!collides(e, sw, sh) || findFree(e, sw, sh)) {
            HudPersistence.save();
            return true;
        }
        p.enabled = false;
        HudRegistry.box(e)[2] = 0;
        return false;
    }

    /**
     * Takes the element (already measured) to the free place nearest to the current one, in steps
     * of 2 px, inside the border.
     */
    static boolean findFree(HudElement e, int sw, int sh) {
        int[] b = HudRegistry.box(e);
        int[] r = {0, 0, b[2], b[3]};
        int x0 = b[0];
        int y0 = b[1];
        int max = Math.max(sw, sh);
        for (int d = 0; d <= max; d += 2) {
            for (int k = -d; k <= d; k += 2) {
                if (free(e, r, x0 + k, y0 - d, sw, sh)
                        || free(e, r, x0 + k, y0 + d, sw, sh)
                        || free(e, r, x0 - d, y0 + k, sw, sh)
                        || free(e, r, x0 + d, y0 + k, sw, sh)) {
                    HudLayout.store(
                            HudRegistry.placement(e),
                            r[0],
                            r[1],
                            r[2],
                            r[3],
                            sw,
                            sh,
                            HudGrid.MARGIN);
                    b[0] = r[0];
                    b[1] = r[1];
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean free(HudElement e, int[] r, int x, int y, int sw, int sh) {
        int m = HudGrid.MARGIN;
        if (x < m || y < m || x + r[2] > sw - m || y + r[3] > sh - m) {
            return false;
        }
        r[0] = x;
        r[1] = y;
        return blocker(e, r, false) == null;
    }
}
