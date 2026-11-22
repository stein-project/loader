/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.loader.api;

import dev.xavier.stein.loader.api.spi.Host;

import java.util.List;

/**
 * Read-only view of what each mod costs per frame: the processor time the Loader spends inside each
 * mod's events, by kind of event, with the game's and the Bridge's share of the frame for context.
 *
 * <p>Nothing is measured until some mod asks with {@link #measure}: off, the Loader's dispatch is
 * the same as without this class. On, the Loader times every event it hands to a mod (two {@code
 * System.nanoTime} per mod per event, the per-entity hooks included) and closes a {@link Snapshot}
 * every {@link #window} milliseconds.
 *
 * <p>Only the processor (CPU) time on the game thread: the drawing a mod asks for is recorded and
 * sent to the graphics card together with everyone else's, so the card's time cannot be split by
 * mod; {@link ModCost#drawsPerFrame} and {@link ModCost#verticesPerFrame} say how much each one
 * asked to draw. The block model hook ({@link SteinMod#onBlockModel}) runs on the chunk building
 * threads and is not timed.
 */
@Experimental(since = "0.1.36")
public final class Profiler {

    /** The kinds of event, in the order of {@link ModCost#avg} and {@link ModCost#p99}. */
    public enum Phase {
        /** {@link SteinMod#onTickEnd}. */
        TICK,
        /** {@link SteinMod#onOverlay} and {@link SteinMod#onOverlayTop}. */
        HUD,
        /** Layout and drawing of the mod's HUD grid elements ({@link Hud#register}). */
        HUD_ELEMENTS,
        /** {@link SteinMod#onRenderWorld}. */
        WORLD,
        /** {@link SteinMod#beforeDrawScreen} and {@link SteinMod#afterDrawScreen}. */
        SCREEN,
        /** Keyboard and mouse events. */
        INPUT,
        /** The other events (chat, packets, inventory, joining, screens opening...). */
        EVENTS,
        /**
         * The per-entity, per-item and per-frame hooks (hide, color, nametag, item model, FOV...).
         */
        HOOKS
    }

    private Profiler() {}

    /**
     * Asks for the measurement (or stops asking). It runs while at least one mod asks; each mod's
     * request is separate, so one mod stopping does not stop another's.
     *
     * @param owner the asking mod's context
     * @param on true to ask, false to stop asking
     */
    public static void measure(ModContext owner, boolean on) {
        Host.INSTANCE.profilerMeasure(owner.id(), on);
    }

    /** Whether the Loader is measuring now (some mod asked). */
    public static boolean measuring() {
        return Host.INSTANCE.profilerMeasuring();
    }

    /**
     * How long each snapshot covers, in milliseconds (250 to 10000; 1000 by default). The last call
     * wins; it applies from the next window on.
     */
    public static void window(int millis) {
        Host.INSTANCE.profilerWindow(millis);
    }

    /** The last closed window, or null before the first one closes (or with nobody measuring). */
    public static Snapshot snapshot() {
        return Host.INSTANCE.profilerSnapshot();
    }

    /** One closed window: every number is per frame, averaged over the window's frames. */
    public static final class Snapshot {
        /** When the window closed (System.currentTimeMillis). */
        public final long closedAt;

        /** How long the window lasted, in seconds. */
        public final double seconds;

        /** Frames drawn in the window (the HUD drawn once, or the menu screen without a world). */
        public final int frames;

        /** Time from one frame to the next, average, in milliseconds. */
        public final double frameAvgMs;

        /** Time from one frame to the next, 99th percentile, in milliseconds. */
        public final double frameP99Ms;

        /** The Loader's own work per frame (the game's HUD parts it draws, its tick work), ms. */
        public final double loaderMs;

        /** Every running mod, in the Loader's order. */
        public final List<ModCost> mods;

        /**
         * The game's and the Bridge's phases of the frame (world, terrain, entities, HUD, swap,
         * waiting for the graphics card, frame limiter...), or empty when the Bridge cannot time
         * them.
         */
        public final List<Zone> zones;

        /** A closed window (the Loader builds it; the fields say what each value is). */
        public Snapshot(
                long closedAt,
                double seconds,
                int frames,
                double frameAvgMs,
                double frameP99Ms,
                double loaderMs,
                List<ModCost> mods,
                List<Zone> zones) {
            this.closedAt = closedAt;
            this.seconds = seconds;
            this.frames = frames;
            this.frameAvgMs = frameAvgMs;
            this.frameP99Ms = frameP99Ms;
            this.loaderMs = loaderMs;
            this.mods = List.copyOf(mods);
            this.zones = List.copyOf(zones);
        }

        /** The sum of every mod's average, in milliseconds per frame. */
        public double modsMs() {
            double sum = 0;
            for (ModCost m : mods) {
                sum += m.totalAvgMs;
            }
            return sum;
        }
    }

    /** What one mod cost in the window (all its entry instances together). */
    public static final class ModCost {
        /** The mod's id, from steinmod.json. */
        public final String id;

        /** The mod's display name, from steinmod.json. */
        public final String name;

        private final double[] avg;
        private final double[] p99;

        /** All its events together: average of the per-frame sum, in ms. */
        public final double totalAvgMs;

        /** All its events together: 99th percentile of the per-frame sum, in ms. */
        public final double totalP99Ms;

        /** Drawing calls it made through {@link Render} and {@link Mesh}, per frame. */
        public final double drawsPerFrame;

        /**
         * The vertices of those calls, per frame: an estimate (4 per rectangle, image or glyph, a
         * mesh's own count); items, heads and rings count as calls only.
         */
        public final double verticesPerFrame;

        /**
         * One mod's costs (the Loader builds it).
         *
         * @param avg average ms per frame, one per {@link Phase} (in its order)
         * @param p99 99th percentile ms per frame, one per {@link Phase}
         */
        public ModCost(
                String id,
                String name,
                double[] avg,
                double[] p99,
                double totalAvgMs,
                double totalP99Ms,
                double drawsPerFrame,
                double verticesPerFrame) {
            if (avg.length != Phase.values().length || p99.length != avg.length) {
                throw new IllegalArgumentException("one value per phase");
            }
            this.id = id;
            this.name = name;
            this.avg = avg.clone();
            this.p99 = p99.clone();
            this.totalAvgMs = totalAvgMs;
            this.totalP99Ms = totalP99Ms;
            this.drawsPerFrame = drawsPerFrame;
            this.verticesPerFrame = verticesPerFrame;
        }

        /** Average ms per frame in this kind of event. */
        public double avg(Phase phase) {
            return avg[phase.ordinal()];
        }

        /** 99th percentile of the ms per frame in this kind of event. */
        public double p99(Phase phase) {
            return p99[phase.ordinal()];
        }
    }

    /** One phase of the frame outside the mods, timed by the Bridge. */
    public static final class Zone {
        /** The phase's name ("world", "terrain", "entities", "hud", "swap"...), not translated. */
        public final String name;

        /** The phase it is part of (its time is inside that one's), or null at the top. */
        public final String parent;

        /** Average ms per frame. */
        public final double avgMs;

        /** One phase's average (the Loader builds it). */
        public Zone(String name, String parent, double avgMs) {
            this.name = name;
            this.parent = parent;
            this.avgMs = avgMs;
        }
    }
}
