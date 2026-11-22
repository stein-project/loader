/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.loader;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The order the menu shows the mods in: the game's parts, then the mods that come with the Stein
 * Client, then every other mod, each group in file order.
 *
 * <p>Only what the player sees: the mods still start and hear events in dependency order. By file
 * name alone, a Client mod such as Sprint would land after every extra one.
 */
final class ModOrder {

    /** The mods the Stein Client installs for everyone (its registry's default mods). */
    private static final Set<String> CLIENT_MODS =
            Set.of("steinhud", "steinconnect", "steinreplay", "steinsprint");

    private ModOrder() {}

    /** {@code all} in menu order; a new list, {@code all} is left as it is. */
    static List<ModInfo> forMenu(List<ModInfo> all) {
        List<ModInfo> out = new ArrayList<>(all.size());
        for (int group = 0; group < 3; group++) {
            for (ModInfo m : all) {
                if (group(m) == group) {
                    out.add(m);
                }
            }
        }
        return out;
    }

    private static int group(ModInfo m) {
        if (m.builtIn()) {
            return 0;
        }
        return CLIENT_MODS.contains(m.id) ? 1 : 2;
    }
}
