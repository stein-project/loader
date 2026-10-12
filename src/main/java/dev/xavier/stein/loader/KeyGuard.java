/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.loader;

import dev.xavier.stein.bridge.core.Log;
import dev.xavier.stein.loader.api.Keys;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * The Controls screen builds the key list with one slot per key registered in the options and one
 * per category that some key has already created (the KeyBinding constructor keeps the category in
 * a static set). A key created and not registered (a mod that crashed halfway through
 * initialization) leaves an empty slot, and the screen breaks when drawing (NullPointerException in
 * GuiListExtended). Here: a key created and not registered is entered in the options
 * (Keys.register, shows up in Controls); a category that still ended up without a key is removed
 * from the set.
 */
final class KeyGuard {

    /** How many keys existed at the last check: it only checks again when a new key appears. */
    private static int seen = -1;

    private static boolean broken;

    private KeyGuard() {}

    /**
     * End of each tick: checks only when the number of created keys changed (a mod that registers
     * late).
     */
    static void tick() {
        if (broken) {
            return;
        }
        try {
            // KeyBinding.keybindArray (private): every KeyBinding created.
            if (KeyBinding.keybindArray.size() != seen) {
                reconcile();
            }
        } catch (RuntimeException | LinkageError e) {
            broken = true;
            Log.info("loader", "key check disabled: " + e);
        }
    }

    /** After the mods' afterStartGame, and when a new key appears. */
    static void reconcile() {
        List<?> all = KeyBinding.keybindArray;
        KeyBinding[] registeredKeys = Minecraft.getMinecraft().gameSettings.keyBindings;
        Set<Object> registered = Collections.newSetFromMap(new IdentityHashMap<>());
        Collections.addAll(registered, (Object[]) registeredKeys);
        for (Object k : new ArrayList<>(all)) {
            if (!registered.contains(k)) {
                Keys.register(k);
                Log.info(
                        "loader",
                        "key created without registration ("
                                + ((KeyBinding) k).getKeyDescription()
                                + "): registered now so the Controls screen does not break");
            }
        }
        Set<String> used = new HashSet<>();
        for (KeyBinding k : Minecraft.getMinecraft().gameSettings.keyBindings) {
            used.add(k.getKeyCategory());
        }
        // The category set itself (KeyBinding.keybindSet), not a copy.
        Set<String> categories = KeyBinding.getKeybinds();
        for (String c : new ArrayList<>(categories)) {
            if (!used.contains(c)) {
                categories.remove(c);
                Log.info(
                        "loader",
                        "key category without a key ("
                                + c
                                + "): removed so the Controls screen does not break");
            }
        }
        seen = all.size();
    }
}
