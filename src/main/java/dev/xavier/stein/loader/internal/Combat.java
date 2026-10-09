/*
 * Copyright (c) 2026 Breno Xavier. All rights reserved.
 * Proprietary software: see the LICENSE file at the root of this repository.
 */

package dev.xavier.stein.loader.internal;

import dev.xavier.stein.bridge.engine.Sprint;
import dev.xavier.stein.bridge.hooks.GameHooks;

/**
 * Engine combat switches, for Combat.
 *
 * <p><b>Outside the public API:</b> the {@code internal} package does not go in the public API jar.
 * These are things that servers detect and punish; they stay with the official Stein mods, which
 * know how to warn the player, and do not become a contract with third-party mods.
 */
public final class Combat {

    private Combat() {}

    /**
     * Sprinting in all directions: with the sprint key pressed, the player also sprints backwards
     * and sideways (the game only allows forwards), with the game's other conditions (hunger, item
     * in use, blindness, sneaking, wall). On a server, anticheats usually punish this.
     */
    public static void setSprintAllDirections(boolean on) {
        Sprint.setAllDirections(on);
    }

    /**
     * "No Hit Delay": removes the penalty of 1.8 itself for missing a hit — when the click hits
     * nothing, the game locks hits for ten ticks (half a second), and whoever clicks fast spends
     * half the time inside that window (we measured ~230 ms of average wait at 100 clicks per
     * second, 500 ms in the worst case). Starts off, at the game's behavior; on a server, it is
     * detectable.
     */
    public static void setNoHitDelay(boolean on) {
        GameHooks.setMissPenalty(!on);
    }

    /**
     * Sprinting survives the hit: when on, 1.8's "sprint reset" (every hit that knocks back drops
     * sprinting) no longer happens. When off — the default —, the game does as it always did.
     *
     * <p>On a server it is detectable: the client tells the server when it starts and when it stops
     * sprinting, and by not stopping, the server sees hit after hit with the player sprinting.
     * Whoever turns it on should know this.
     */
    public static void setSprintOnHit(boolean on) {
        Sprint.setOnHit(on);
    }

    /**
     * Together with sprinting through the hit: the hit also does not brake the player (1.8 cuts the
     * horizontal speed to 60% on every hit that knocks back). No effect with sprinting off. On a
     * server, detectable.
     */
    public static void setNoHitSlowdown(boolean on) {
        Sprint.setNoSlow(on);
    }
}
