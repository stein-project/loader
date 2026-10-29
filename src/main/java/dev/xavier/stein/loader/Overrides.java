/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.loader;

import dev.xavier.stein.loader.api.SteinMod;

import java.util.ArrayList;
import java.util.List;

/**
 * Which mods override an event of {@link SteinMod}, worked out once when the mods load.
 *
 * <p>The hot events (every entity, every item, several times a frame) are dispatched only to the
 * mods in these lists: a mod that keeps the interface's default answer changes nothing, so asking
 * it is pure cost — a megamorphic call and a try block per mod, per entity, per frame. With no mod
 * left, the hook returns at once.
 */
final class Overrides {

    private Overrides() {}

    /**
     * The mods, in their order, whose class declares {@code name(params)} itself instead of
     * inheriting the default from {@link SteinMod}.
     */
    static SteinMod[] of(SteinMod[] mods, String name, Class<?>... params) {
        List<SteinMod> out = new ArrayList<>();
        for (SteinMod m : mods) {
            if (overrides(m, name, params)) {
                out.add(m);
            }
        }
        return out.toArray(new SteinMod[0]);
    }

    private static boolean overrides(SteinMod m, String name, Class<?>... params) {
        try {
            return m.getClass().getMethod(name, params).getDeclaringClass() != SteinMod.class;
        } catch (Throwable e) {
            // A class whose methods cannot be listed (a missing type in a signature): ask it, as
            // before, and let the dispatch report what goes wrong.
            return true;
        }
    }
}
