/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Breno Xavier
 *
 * Part of the Stein Loader API, licensed under the MIT License (see LICENSE-API).
 */

package dev.xavier.stein.loader.api;

import dev.xavier.stein.loader.api.spi.Host;

/**
 * Switches that change how the game itself plays: sprinting and the hit rules of 1.8.
 *
 * <p><b>Servers detect these.</b> Each switch changes what the client sends to the server, and
 * anticheats usually punish it. A mod that offers them should say so to the player and start with
 * them off; all of them start off, at the game's behavior.
 *
 * <p>They are global: there is one of each in the game, not one per mod, and the last call wins.
 *
 * <p>Call from the game thread.
 */
@Experimental(since = "0.1.25")
public final class Gameplay {

    private Gameplay() {}

    /**
     * Sprinting in all directions: with the sprint key pressed, the player also sprints backwards
     * and sideways (the game only allows forwards), with the game's other conditions (hunger, item
     * in use, blindness, sneaking, wall).
     *
     * @param on true to allow it
     */
    public static void setSprintAllDirections(boolean on) {
        Host.INSTANCE.setSprintAllDirections(on);
    }

    /**
     * "No Hit Delay": removes the penalty of 1.8 itself for missing a hit — when the click hits
     * nothing, the game locks hits for ten ticks (half a second), and whoever clicks fast spends
     * half the time inside that window (we measured ~230 ms of average wait at 100 clicks per
     * second, 500 ms in the worst case).
     *
     * @param on true to remove the lock
     */
    public static void setNoHitDelay(boolean on) {
        Host.INSTANCE.setNoHitDelay(on);
    }

    /**
     * Sprinting survives the hit: 1.8's "sprint reset" (every hit that knocks back drops sprinting)
     * no longer happens. The client tells the server when it starts and when it stops sprinting, so
     * the server sees hit after hit with the player sprinting.
     *
     * @param on true to keep sprinting through the hit
     */
    public static void setSprintOnHit(boolean on) {
        Host.INSTANCE.setSprintOnHit(on);
    }

    /**
     * Together with {@link #setSprintOnHit}: the hit also does not brake the player (1.8 cuts the
     * horizontal speed to 60% on every hit that knocks back). No effect with sprinting off.
     *
     * @param on true to keep the speed on hits
     */
    public static void setNoHitSlowdown(boolean on) {
        Host.INSTANCE.setNoHitSlowdown(on);
    }
}
