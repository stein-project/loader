/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.loader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What each mod requires of the others ({@code depends} and {@code optional} in steinmod.json) and
 * the order in which they start.
 *
 * <p>{@code "depends": {"steincombat": "0.1.12"}} — without Combat 0.1.12 or newer enabled, the mod
 * does not load, with the reason shown in the list (and so do those that depended on it, in
 * cascade). {@code "optional"} has the same format: the mod loads without it, but if it is enabled
 * it must be at that version or newer (an old version that it would call is worse than none), and
 * it starts first.
 *
 * <p>The order applies to everything: constructor, beforeStartGame, afterStartGame and every event
 * — whoever is required comes before whoever requires it. Otherwise, the folder order
 * (alphabetical) is kept, so nothing changes from one start to the next.
 */
final class Dependencies {

    private Dependencies() {}

    /**
     * Rejects those that lack what they require and returns the ones that stay, in start order.
     *
     * @param enabled the enabled mods, in folder order
     * @param all all those in the list (the disabled and the rejected go into the reason)
     */
    static List<ModInfo> resolve(List<ModInfo> enabled, List<ModInfo> all) {
        Map<String, ModInfo> byId = new HashMap<>();
        for (ModInfo m : all) {
            byId.putIfAbsent(m.id, m);
        }
        List<ModInfo> ok = new ArrayList<>(enabled);
        boolean changed = true;
        while (changed) { // cascade: whoever depended on a rejected one drops out on the next pass
            changed = false;
            for (ModInfo m : new ArrayList<>(ok)) {
                if (!accept(m, byId, ok)) {
                    ok.remove(m);
                    changed = true;
                }
            }
        }
        return order(ok);
    }

    private static boolean accept(ModInfo m, Map<String, ModInfo> byId, List<ModInfo> ok) {
        for (Map.Entry<String, String> d : m.depends.entrySet()) {
            ModInfo dep = byId.get(d.getKey());
            if (dep == null || dep.rejected()) {
                m.reject("steinloader.reject.depends.missing", d.getKey(), d.getValue());
                return false;
            }
            if (!ok.contains(dep)) {
                m.reject("steinloader.reject.depends.disabled", dep.name);
                return false;
            }
            if (!Versions.atLeast(dep.version, d.getValue())) {
                m.reject("steinloader.reject.depends.old", dep.name, d.getValue(), dep.version);
                return false;
            }
        }
        for (Map.Entry<String, String> d : m.optional.entrySet()) {
            ModInfo dep = byId.get(d.getKey());
            if (dep != null && ok.contains(dep) && !Versions.atLeast(dep.version, d.getValue())) {
                m.reject("steinloader.reject.depends.old", dep.name, d.getValue(), dep.version);
                return false;
            }
        }
        return true;
    }

    /**
     * Whoever is required comes before whoever requires it; in a cycle, what is left goes in folder
     * order (and the log warns).
     */
    private static List<ModInfo> order(List<ModInfo> mods) {
        Map<String, ModInfo> byId = new HashMap<>();
        for (ModInfo m : mods) {
            byId.put(m.id, m);
        }
        Set<ModInfo> out = new LinkedHashSet<>();
        Set<ModInfo> visiting = new LinkedHashSet<>();
        for (ModInfo m : mods) {
            visit(m, byId, out, visiting);
        }
        return new ArrayList<>(out);
    }

    private static void visit(
            ModInfo m, Map<String, ModInfo> byId, Set<ModInfo> out, Set<ModInfo> visiting) {
        if (out.contains(m)) {
            return;
        }
        if (!visiting.add(m)) {
            System.out.println("[loader] circular dependency in " + m.id + ": keeps folder order");
            return;
        }
        visitAll(m.depends.keySet(), byId, out, visiting);
        visitAll(m.optional.keySet(), byId, out, visiting);
        visiting.remove(m);
        out.add(m);
    }

    private static void visitAll(
            Set<String> ids, Map<String, ModInfo> byId, Set<ModInfo> out, Set<ModInfo> visiting) {
        for (String id : ids) {
            ModInfo d = byId.get(id);
            if (d != null) {
                visit(d, byId, out, visiting);
            }
        }
    }
}
