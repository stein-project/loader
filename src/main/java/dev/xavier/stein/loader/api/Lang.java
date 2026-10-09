/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Breno Xavier
 *
 * Part of the Stein Loader API, licensed under the MIT License (see LICENSE-API).
 */

package dev.xavier.stein.loader.api;

import dev.xavier.stein.loader.api.spi.Host;

/**
 * Translation: the keys of the {@code assets/<id>/lang/<language>.lang} files that the mod ships in
 * its jar, in the language the player picked. A key missing in their language falls back to {@code
 * en_US}; one missing in both comes back as the key itself.
 *
 * <pre>
 * Lang.tr("mymod.title")                   // "My Mod"
 * Lang.tr("mymod.seconds", 5)              // mymod.seconds=%s seconds  -> "5 seconds"
 * </pre>
 *
 * <p>The arguments follow {@link String#format} ({@code %s}, {@code %d}, {@code %1$s}). Shorter
 * inside the mod: {@link ModContext#tr}.
 */
@Experimental(since = "0.1.16")
public final class Lang {

    private static final Object[] NONE = new Object[0];

    private Lang() {}

    /** The text for the key, without arguments. */
    public static String tr(String key) {
        return Host.INSTANCE.translate(key, NONE);
    }

    /** The text for the key with the arguments in place of the {@code %s}. */
    public static String tr(String key, Object... args) {
        return Host.INSTANCE.translate(key, args);
    }

    /**
     * Whether the key exists in the player's language or in English: to spot keys missing from the
     * .lang.
     */
    public static boolean has(String key) {
        return Host.INSTANCE.hasTranslation(key);
    }

    /** The code of the chosen language, as the game writes it: {@code en_US}, {@code pt_BR}. */
    public static String language() {
        return Host.INSTANCE.language();
    }
}
