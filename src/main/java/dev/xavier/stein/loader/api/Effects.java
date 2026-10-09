/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Breno Xavier
 *
 * Part of the Stein Loader API, licensed under the MIT License (see LICENSE-API).
 */

package dev.xavier.stein.loader.api;

import dev.xavier.stein.loader.api.spi.Host;

import java.util.List;

/**
 * Potion effects active on an entity (the player's own; for others, the server almost never sends
 * them). {@link Effect} belongs to the API, not the game: you can create one to show as an example.
 *
 * <pre>
 * for (Effects.Effect e : Effects.of(Entities.self())) {
 *     Render.effectIcon(e.id, x, y);
 * Render.text(Effects.name(e) + " " + Effects.durationText(e), x + 20, y + 5, 0xFFFFFFFF, true);
 * }
 * </pre>
 */
@Experimental(since = "0.1.16")
public final class Effects {

    /**
     * An effect: the game id (1 speed, 5 strength, 12 fire resistance...), the level and the time
     * remaining.
     */
    public static final class Effect {
        public final int id;

        /** 0 = level I. */
        public final int amplifier;

        /** Ticks remaining (20 per second). */
        public final int duration;

        /**
         * Endless (from a beacon, or given by command without a duration): the game shows "**:**".
         */
        public final boolean permanent;

        public Effect(int id, int amplifier, int duration, boolean permanent) {
            this.id = id;
            this.amplifier = amplifier;
            this.duration = duration;
            this.permanent = permanent;
        }
    }

    private Effects() {}

    /** The active effects, in the game's order; empty if none or if it is not a living entity. */
    public static List<Effect> of(Object entity) {
        return Host.INSTANCE.effectsOf(entity);
    }

    /**
     * The name in the game's language, with the level in Roman numerals as in the inventory ("Speed
     * II").
     */
    public static String name(Effect e) {
        return Host.INSTANCE.effectName(e.id, e.amplifier);
    }

    /** The remaining time as the game writes it ("1:30"; "**:**" if endless). */
    public static String durationText(Effect e) {
        return Host.INSTANCE.effectDuration(e.duration, e.permanent);
    }

    /** Does it have an inventory icon? (Some, like instant health, do not.) */
    public static boolean hasIcon(int id) {
        return Host.INSTANCE.effectHasIcon(id);
    }
}
