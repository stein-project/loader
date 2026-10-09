/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.loader.hud;

import dev.xavier.stein.loader.ModTimes;
import dev.xavier.stein.loader.api.HudElement;
import dev.xavier.stein.loader.api.HudPlacement;
import dev.xavier.stein.loader.gui.Surface;
import dev.xavier.stein.loader.gui.Ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The HUD grid's registry: the elements (the game's own parts and the mods'), each one's saved
 * {@link Placement} and the box it was last drawn in, plus the box geometry (background gap,
 * anchor, margin).
 */
final class HudRegistry {

    static final List<HudElement> elements = new ArrayList<>();

    /** The game's own parts (the Loader's). */
    private static final Set<HudElement> builtinElements =
            Collections.newSetFromMap(new IdentityHashMap<>());

    static final Map<String, Placement> placements = new LinkedHashMap<>();

    /**
     * Box drawn last (left, top, width, height in GUI pixels, with scale): the editor drags by it.
     */
    private static final Map<HudElement, int[]> boxes = new IdentityHashMap<>();

    /** Editor grid, in GUI pixels; 0 = no grid. */
    static int grid = 8;

    /** Snap to the grid, the borders, the center and the neighbors when dragging. */
    static boolean snap = true;

    private HudRegistry() {}

    static void addBuiltin(HudElement e) {
        builtinElements.add(e);
        add(e, true);
    }

    static void register(HudElement e) {
        add(e, false);
        ModTimes.element(e); // its time counts for the mod that registered it
    }

    private static void add(HudElement e, boolean builtin) {
        for (HudElement o : elements) {
            if (o.id().equals(e.id())) {
                System.out.println("[loader] HUD: duplicate element ignored: " + e.id());
                return;
            }
        }
        elements.add(e);
        if (!placements.containsKey(e.id())) {
            // The game as the game; the rest starts disabled, and the player enables it in the
            // editor's list.
            Placement p = Placement.of(e.defaultPlacement());
            p.enabled = builtin;
            placements.put(e.id(), p);
            HudPersistence.save();
        }
    }

    public static boolean isEnabled(String id) {
        Placement p = placements.get(id);
        return p == null || p.enabled;
    }

    static Placement placement(HudElement e) {
        return placements.computeIfAbsent(
                e.id(),
                k -> {
                    Placement p = Placement.of(e.defaultPlacement());
                    p.enabled = builtinElements.contains(e);
                    return p;
                });
    }

    /** The element's background: what the player chose, or the factory one. */
    static float background(HudElement e, Placement p) {
        if (!e.hasBackground()) {
            return 0.0F;
        }
        float o = p.background >= 0.0F ? p.background : e.defaultBackground();
        return Ui.clamp01(o);
    }

    /**
     * The gap between the box and the content (the background around it), in GUI pixels already
     * with the scale; 0 without a background.
     */
    static int inset(HudElement e) {
        Placement p = placement(e);
        // The hotbar stays the same size: the background goes inside its box (the game's frame
        // already has a margin).
        if (background(e, p) <= 0.0F || e instanceof VanillaPart part && part.backgroundInside()) {
            return 0;
        }
        return Math.max(1, Math.round(Surface.PAD * p.scale));
    }

    /**
     * Measures the box (the content at the player's scale and the background gap) and puts it in
     * place by the anchor.
     */
    static void place(HudElement e, Placement p, int[] box, int screenW, int screenH) {
        int in = inset(e);
        box[2] = Math.max(1, Math.round(e.width() * p.scale)) + in * 2;
        box[3] = Math.max(1, Math.round(e.height() * p.scale)) + in * 2;
        int m = margin(e, p);
        box[0] = HudLayout.left(p, box[2], screenW, m);
        box[1] = HudLayout.top(p, box[3], screenH, m);
    }

    /** The element's box (left, top, width, height); width 0 = not drawn. */
    static int[] box(HudElement e) {
        return boxes.computeIfAbsent(e, k -> new int[4]);
    }

    /**
     * Game part in its place and at the factory scale (counts as in the game: flush against the
     * border and the other parts).
     */
    static boolean atDefault(HudElement e, Placement p) {
        if (!builtinElements.contains(e)) {
            return false;
        }
        HudPlacement d = e.defaultPlacement();
        return p.anchorX == d.anchorX
                && p.anchorY == d.anchorY
                && p.x == d.x
                && p.y == d.y
                && p.scale == d.scale;
    }

    /** Screen border for the element: the fixed one, except for a game part in the game's place. */
    static int margin(HudElement e, Placement p) {
        return atDefault(e, p) ? 0 : HudGrid.MARGIN;
    }

    /** Is this one of the game's own parts (the Loader's)? */
    static boolean isBuiltin(HudElement e) {
        return builtinElements.contains(e);
    }
}
