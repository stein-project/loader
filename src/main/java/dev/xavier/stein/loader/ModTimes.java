/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.loader;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * How much each mod costs per frame, for F3: whoever plays sees which mod weighs, and whoever
 * writes a mod sees their own.
 *
 * <p>It only counts what runs once per frame or per tick — overlay, top overlay, world, screens,
 * tick — and the drawing of each mod's HUD elements (attributed to whoever registered them). In
 * these, measuring costs two System.nanoTime per mod per event: nothing close to a frame. The hooks
 * that run per entity or per item (onHideEntity, onItemModel, onBlockModel...) are left out on
 * purpose: there the measurement would weigh, and measuring the engine has its own rule (A/B).
 *
 * <p>All on the game thread. The average is over the last second's window: time spent / frames.
 */
public final class ModTimes {

    private static final long WINDOW_NANOS = 1_000_000_000L;
    private static final double MIN_REPORTED_MS = 0.01;

    private static ModInfo[] owners = new ModInfo[0];
    private static long[] spent = new long[0];
    private static double[] perFrame = new double[0];
    private static int frames;

    /**
     * The HUD elements separately: with F3 open the grid hides them, and a window with none drawn
     * would hold zero exactly when F3 is opened to look. The last measurement taken with them on
     * screen stays.
     */
    private static long[] elementSpent = new long[0];

    private static double[] elementPerFrame = new double[0];
    private static int elementFrames;
    private static boolean elementDrawn;
    private static long windowStart = System.nanoTime();

    /**
     * The mod whose event is running (index in owners), or -1: the HUD element registered right now
     * is its.
     */
    static int current = -1;

    private static final Map<Object, Integer> ELEMENTS = new IdentityHashMap<>();

    private ModTimes() {}

    /** The owners of each instance, in the order of the Loader's mod array. */
    static void init(ModInfo[] byInstance) {
        owners = byInstance;
        spent = new long[byInstance.length];
        perFrame = new double[byInstance.length];
        elementSpent = new long[byInstance.length];
        elementPerFrame = new double[byInstance.length];
    }

    /** Time spent by the mod at index i (the Loader's mod array). */
    static void add(int i, long nanos) {
        spent[i] += nanos;
    }

    /**
     * A HUD element registered now belongs to whoever is running (Hud.register in afterStartGame,
     * usually).
     */
    public static void element(Object element) {
        if (current >= 0) {
            ELEMENTS.put(element, current);
        }
    }

    /** Layout and draw time of a HUD element, for the mod that registered it. */
    public static void elementTime(Object element, long nanos) {
        Integer i = ELEMENTS.get(element);
        if (i != null) {
            elementSpent[i] += nanos;
            elementDrawn = true;
        }
    }

    /** One more frame; every second, closes the window. */
    static void frame() {
        frames++;
        if (elementDrawn) {
            elementFrames++;
            elementDrawn = false;
        }
        long now = System.nanoTime();
        if (now - windowStart >= WINDOW_NANOS) {
            for (int i = 0; i < spent.length; i++) {
                perFrame[i] = frames == 0 ? 0 : spent[i] / 1e6 / frames;
                spent[i] = 0;
                if (elementFrames > 0) {
                    elementPerFrame[i] = elementSpent[i] / 1e6 / elementFrames;
                }
                elementSpent[i] = 0;
            }
            frames = 0;
            elementFrames = 0;
            windowStart = now;
        }
    }

    /**
     * "HUD 0.21 ms, Replay 0.04 ms": those above 0.01 ms per frame, summing each mod's instances.
     */
    static String summary() {
        Map<ModInfo, Double> byMod = new LinkedHashMap<>();
        for (int i = 0; i < owners.length; i++) {
            byMod.merge(owners[i], perFrame[i] + elementPerFrame[i], Double::sum);
        }
        StringBuilder sb = new StringBuilder();
        byMod.entrySet().stream()
                .filter(e -> e.getValue() >= MIN_REPORTED_MS)
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .forEach(
                        e ->
                                sb.append(sb.length() == 0 ? "" : ", ")
                                        .append(e.getKey().name)
                                        .append(
                                                String.format(
                                                        Locale.ROOT, " %.2f ms", e.getValue())));
        return sb.toString();
    }
}
