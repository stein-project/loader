/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.loader;

import static java.lang.invoke.MethodType.methodType;

import dev.xavier.stein.bridge.engine.Obf;
import dev.xavier.stein.loader.api.Keys;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * The Controls screen builds the key list with one slot per key registered in the options and one
 * per category that some key has already created (the KeyBinding constructor keeps the category in
 * a static set). A key created and not registered — a mod that crashed halfway through
 * initialization — leaves an empty slot, and the screen breaks when drawing (NullPointerException
 * in GuiListExtended). Here: a key created and not registered is entered in the options
 * (Keys.register, shows up in Controls); a category that still ended up without a key is removed
 * from the set.
 */
final class KeyGuard {

    private static final MethodHandle ALL; // avb.a: keybindArray, every KeyBinding created (static)
    private static final MethodHandle CATEGORIES; // avb.c(): getKeybinds, the category set itself
    private static final MethodHandle CATEGORY; // avb.e(): getKeyCategory
    private static final MethodHandle DESCRIPTION; // avb.g(): getKeyDescription
    private static final MethodHandle GET_MC; // ave.A(): getMinecraft
    private static final MethodHandle SETTINGS; // ave.t: gameSettings
    private static final MethodHandle KEYS_GET; // avh.ax: keyBindings

    /** How many keys existed at the last check: it only checks again when a new key appears. */
    private static int seen = -1;

    private static boolean broken;

    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            Class<?> key = Obf.cls("avb");
            Class<?> mc = Obf.cls("ave");
            Class<?> settings = Obf.cls("avh");
            MethodHandles.Lookup k = MethodHandles.privateLookupIn(key, l);
            ALL = k.findStaticGetter(key, "a", List.class);
            CATEGORIES = l.findStatic(key, "c", methodType(Set.class));
            CATEGORY =
                    l.findVirtual(key, "e", methodType(String.class))
                            .asType(methodType(String.class, Object.class));
            DESCRIPTION =
                    l.findVirtual(key, "g", methodType(String.class))
                            .asType(methodType(String.class, Object.class));
            GET_MC = l.findStatic(mc, "A", methodType(mc)).asType(methodType(Object.class));
            SETTINGS =
                    l.findGetter(mc, "t", settings).asType(methodType(Object.class, Object.class));
            KEYS_GET =
                    l.findGetter(settings, "ax", key.arrayType())
                            .asType(methodType(Object[].class, Object.class));
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

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
            if (((List<?>) ALL.invokeExact()).size() != seen) {
                reconcile();
            }
        } catch (Throwable e) {
            broken = true;
            System.out.println("[loader] key check disabled: " + e);
        }
    }

    /** After the mods' afterStartGame, and when a new key appears. */
    static void reconcile() throws Throwable {
        List<?> all = (List<?>) ALL.invokeExact();
        Object settings = SETTINGS.invokeExact(GET_MC.invokeExact());
        Set<Object> registered = Collections.newSetFromMap(new IdentityHashMap<>());
        Collections.addAll(registered, (Object[]) KEYS_GET.invokeExact(settings));
        for (Object k : new ArrayList<>(all)) {
            if (!registered.contains(k)) {
                Keys.register(k);
                System.out.println(
                        "[loader] key created without registration ("
                                + (String) DESCRIPTION.invokeExact(k)
                                + "): registered now so the Controls screen does not break");
            }
        }
        Set<String> used = new HashSet<>();
        for (Object k : (Object[]) KEYS_GET.invokeExact(settings)) {
            used.add((String) CATEGORY.invokeExact(k));
        }
        @SuppressWarnings("unchecked")
        Set<String> categories = (Set<String>) CATEGORIES.invokeExact();
        for (String c : new ArrayList<>(categories)) {
            if (!used.contains(c)) {
                categories.remove(c);
                System.out.println(
                        "[loader] key category without a key ("
                                + c
                                + "): removed so the Controls screen does not break");
            }
        }
        seen = all.size();
    }
}
