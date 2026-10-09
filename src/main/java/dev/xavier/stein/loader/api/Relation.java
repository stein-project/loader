/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Breno Xavier
 *
 * Part of the Stein Loader API, licensed under the MIT License (see LICENSE-API).
 */

package dev.xavier.stein.loader.api;

/** What a player is to you ({@link Relations}). */
@Experimental(since = "0.1.16")
public enum Relation {
    /** Nothing was registered and there is no provider: unknown. */
    NONE,
    /** You. */
    SELF,
    /**
     * In your group (clan, team, guild — the name is up to whoever registers: {@link
     * Relations#setGroup}).
     */
    GROUP,
    ALLY,
    ENEMY,
    /** Known to be neither ally nor enemy. */
    NEUTRAL;

    /** Is it on your side (you, your group, an ally)? */
    public boolean friendly() {
        return this == SELF || this == GROUP || this == ALLY;
    }
}
