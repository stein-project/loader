/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.loader;

import dev.xavier.stein.bridge.bench.LiveZones;
import dev.xavier.stein.bridge.core.Log;
import dev.xavier.stein.loader.api.Profiler;
import dev.xavier.stein.loader.api.SteinMod;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The detailed per-mod measurement behind {@link Profiler}: each mod's time by kind of event, per
 * frame, with its average and 99th percentile over a window.
 *
 * <p>It only runs while a mod asks for it ({@link #request}); the dispatch checks {@link #active}
 * (a plain static field) and, off, does exactly what it did without this class. On, each frame's
 * time per mod and kind goes into a log-scale histogram (8 steps per doubling, about 6% apart), so
 * the percentile needs no per-frame storage and no sorting; the averages come from exact sums.
 *
 * <p>All on the game thread, which is also the render thread in 1.8.9.
 */
public final class ModProfile {

    static final int TICK = Profiler.Phase.TICK.ordinal();
    static final int HUD = Profiler.Phase.HUD.ordinal();
    static final int HUD_ELEMENTS = Profiler.Phase.HUD_ELEMENTS.ordinal();
    static final int WORLD = Profiler.Phase.WORLD.ordinal();
    static final int SCREEN = Profiler.Phase.SCREEN.ordinal();
    static final int INPUT = Profiler.Phase.INPUT.ordinal();
    static final int EVENTS = Profiler.Phase.EVENTS.ordinal();
    static final int HOOKS = Profiler.Phase.HOOKS.ordinal();

    private static final int PHASES = Profiler.Phase.values().length;

    /** Per mod: one histogram per phase and one for all of them together. */
    private static final int SLOTS = PHASES + 1;

    /** Units of 128 ns, up to 2^33 of them (about 18 minutes, far past any frame). */
    private static final int UNIT_SHIFT = 7;

    private static final int BUCKETS = 1 + 34 * 8;

    private static final int MIN_WINDOW_MS = 250;
    private static final int MAX_WINDOW_MS = 10_000;

    /** Read on every dispatch: true while some mod asks for the measurement. */
    public static boolean active;

    /**
     * The instance (index in the Loader's array) whose drawing is running, for the draw counts; -1
     * when it is nobody's.
     */
    public static int drawOwner = -1;

    private static final Set<String> REQUESTS = new HashSet<>();

    /** The mod (index in {@link #modList}) of each instance. */
    private static int[] modOf = new int[0];

    private static ModInfo[] modList = new ModInfo[0];

    /** The instance index of each instance object, for the hooks that loop over a subset. */
    private static final Map<SteinMod, Integer> INSTANCES = new IdentityHashMap<>();

    // Allocated on the first activation.
    private static long[] frame;
    private static long[] sum;
    private static int[][] hist;
    private static long[] draws;
    private static long[] vertices;
    private static final int[] FRAME_HIST = new int[BUCKETS];

    private static long frameSum;
    private static long loaderSpent;
    private static int frames;
    private static long lastFrameAt;
    private static long windowStart;
    private static long windowNanos = 1_000_000_000L;

    /** The Bridge's zones: null when the running Bridge does not time them (older). */
    private static long[] zones;

    private static volatile Profiler.Snapshot last;

    private ModProfile() {}

    /** The mods of the Loader's instances, in its order (called once, at start). */
    static void init(SteinMod[] instances, ModInfo[] owners) {
        List<ModInfo> distinct = new ArrayList<>();
        modOf = new int[owners.length];
        INSTANCES.clear();
        for (int i = 0; i < owners.length; i++) {
            int m = distinct.indexOf(owners[i]);
            if (m < 0) {
                m = distinct.size();
                distinct.add(owners[i]);
            }
            modOf[i] = m;
            INSTANCES.put(instances[i], i);
        }
        modList = distinct.toArray(new ModInfo[0]);
    }

    /** A mod asks for the measurement, or stops asking. */
    static void request(String modId, boolean on) {
        boolean changed = on ? REQUESTS.add(modId) : REQUESTS.remove(modId);
        boolean want = !REQUESTS.isEmpty();
        if (!changed || want == active) {
            return;
        }
        if (want) {
            allocate();
            reset(System.nanoTime());
            last = null;
            zones(true);
            active = true;
            Log.info("loader", "per-mod profiler on (asked by " + modId + ")");
        } else {
            active = false;
            zones(false);
            last = null;
            Log.info("loader", "per-mod profiler off");
        }
    }

    /** How long each window lasts. */
    static void window(int millis) {
        int ms = Math.max(MIN_WINDOW_MS, Math.min(MAX_WINDOW_MS, millis));
        windowNanos = ms * 1_000_000L;
    }

    static Profiler.Snapshot snapshot() {
        return active ? last : null;
    }

    private static void allocate() {
        int n = modList.length;
        if (frame == null || frame.length != n * PHASES) {
            frame = new long[n * PHASES];
            sum = new long[n * PHASES];
            hist = new int[n * SLOTS][BUCKETS];
            draws = new long[n];
            vertices = new long[n];
        }
    }

    private static void zones(boolean on) {
        try {
            LiveZones.set(on);
            zones = on ? new long[LiveZones.count()] : null;
        } catch (LinkageError e) {
            zones = null; // a Bridge without the live zones: the mods only
        }
    }

    // ---- measurement (hot while active) ----

    /** Time spent by the instance at index i (the Loader's array) in this kind of event. */
    static void add(int instance, int phase, long nanos) {
        if (instance >= 0) {
            frame[modOf[instance] * PHASES + phase] += nanos;
        }
    }

    /** The index of this instance in the Loader's array, or -1. */
    static int instanceOf(SteinMod m) {
        Integer i = INSTANCES.get(m);
        return i == null ? -1 : i;
    }

    /** Time of the Loader's own work (its HUD parts, its tick work). */
    public static void loader(long nanos) {
        loaderSpent += nanos;
    }

    /** One drawing call of {@link #drawOwner}, with this many vertices. */
    public static void draw(int vertexCount) {
        int i = drawOwner;
        if (i >= 0) {
            int m = modOf[i];
            draws[m]++;
            vertices[m] += vertexCount;
        }
    }

    /** At the end of every frame (the Bridge's frameEnd): the frame's times go to the window. */
    static void frame() {
        long now = System.nanoTime();
        if (lastFrameAt == 0L) {
            Arrays.fill(frame, 0L);
            lastFrameAt = now;
            return;
        }
        long d = now - lastFrameAt;
        lastFrameAt = now;
        frameSum += d;
        FRAME_HIST[bucket(d)]++;
        frames++;
        long[] f = frame;
        for (int m = 0; m < modList.length; m++) {
            long total = 0L;
            int base = m * PHASES;
            for (int p = 0; p < PHASES; p++) {
                long v = f[base + p];
                f[base + p] = 0L;
                sum[base + p] += v;
                hist[m * SLOTS + p][bucket(v)]++;
                total += v;
            }
            hist[m * SLOTS + PHASES][bucket(total)]++;
        }
        if (now - windowStart >= windowNanos) {
            close(now);
        }
    }

    private static void reset(long now) {
        Arrays.fill(frame, 0L);
        Arrays.fill(sum, 0L);
        for (int[] h : hist) {
            Arrays.fill(h, 0);
        }
        Arrays.fill(draws, 0L);
        Arrays.fill(vertices, 0L);
        Arrays.fill(FRAME_HIST, 0);
        frameSum = 0L;
        loaderSpent = 0L;
        frames = 0;
        lastFrameAt = 0L;
        windowStart = now;
    }

    /** Closes the window into a snapshot (once per window: allocating here is fine). */
    private static void close(long now) {
        int n = frames;
        List<Profiler.ModCost> mods = new ArrayList<>(modList.length);
        for (int m = 0; m < modList.length; m++) {
            double[] avg = new double[PHASES];
            double[] p99 = new double[PHASES];
            double total = 0;
            for (int p = 0; p < PHASES; p++) {
                avg[p] = sum[m * PHASES + p] / 1e6 / n;
                p99[p] = percentile(hist[m * SLOTS + p], n);
                total += avg[p];
            }
            ModInfo info = modList[m];
            mods.add(
                    new Profiler.ModCost(
                            info.id,
                            info.name,
                            avg,
                            p99,
                            total,
                            percentile(hist[m * SLOTS + PHASES], n),
                            (double) draws[m] / n,
                            (double) vertices[m] / n));
        }
        last =
                new Profiler.Snapshot(
                        System.currentTimeMillis(),
                        (now - windowStart) / 1e9,
                        n,
                        frameSum / 1e6 / n,
                        percentile(FRAME_HIST, n),
                        loaderSpent / 1e6 / n,
                        mods,
                        takeZones());
        long keepLast = lastFrameAt;
        reset(now);
        lastFrameAt = keepLast; // the next frame's time still counts from this one's end
    }

    private static List<Profiler.Zone> takeZones() {
        long[] z = zones;
        if (z == null) {
            return List.of();
        }
        int n = LiveZones.take(z);
        if (n == 0) {
            return List.of();
        }
        List<Profiler.Zone> out = new ArrayList<>(z.length);
        for (int i = 0; i < z.length; i++) {
            int parent = LiveZones.parent(i);
            out.add(
                    new Profiler.Zone(
                            LiveZones.name(i),
                            parent < 0 ? null : LiveZones.name(parent),
                            z[i] / 1e6 / n));
        }
        return out;
    }

    // ---- histogram ----

    /** The bucket of a duration: 0 for under 128 ns, then 8 per doubling. */
    static int bucket(long nanos) {
        long u = nanos >>> UNIT_SHIFT;
        if (u == 0L) {
            return 0;
        }
        int e = 63 - Long.numberOfLeadingZeros(u);
        int sub = (int) (e >= 3 ? u >>> (e - 3) : u << (3 - e)) & 7;
        return Math.min(BUCKETS - 1, 1 + e * 8 + sub);
    }

    /** The middle of a bucket, in milliseconds. */
    static double bucketMs(int b) {
        if (b == 0) {
            return 0;
        }
        int e = (b - 1) / 8;
        int sub = (b - 1) % 8;
        double units = (8.5 + sub) * Math.pow(2, e) / 8;
        return units * (1 << UNIT_SHIFT) / 1e6;
    }

    /** The 99th percentile of n counted values. */
    static double percentile(int[] h, int n) {
        long target = Math.max(1, (long) Math.ceil(n * 0.99));
        long seen = 0;
        for (int b = 0; b < h.length; b++) {
            seen += h[b];
            if (seen >= target) {
                return bucketMs(b);
            }
        }
        return bucketMs(h.length - 1);
    }
}
