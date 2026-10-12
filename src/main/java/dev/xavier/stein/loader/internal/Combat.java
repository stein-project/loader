/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.loader.internal;

import dev.xavier.stein.loader.api.Gameplay;

/**
 * The gameplay switches under their old name, kept only so that mods built before {@link Gameplay}
 * still load. It goes away in a later Loader; mods call {@link Gameplay}.
 *
 * @deprecated use {@link Gameplay}
 */
@Deprecated
public final class Combat {

    private Combat() {}

    /** See {@link Gameplay#setSprintAllDirections}. */
    public static void setSprintAllDirections(boolean on) {
        Gameplay.setSprintAllDirections(on);
    }

    /** See {@link Gameplay#setNoHitDelay}. */
    public static void setNoHitDelay(boolean on) {
        Gameplay.setNoHitDelay(on);
    }

    /** See {@link Gameplay#setSprintOnHit}. */
    public static void setSprintOnHit(boolean on) {
        Gameplay.setSprintOnHit(on);
    }

    /** See {@link Gameplay#setNoHitSlowdown}. */
    public static void setNoHitSlowdown(boolean on) {
        Gameplay.setNoHitSlowdown(on);
    }
}
