/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Breno Xavier
 *
 * Part of the Stein Loader API, licensed under the MIT License (see LICENSE-API).
 */

package dev.xavier.stein.loader.api;

import dev.xavier.stein.loader.api.spi.Host;

/**
 * The other mods: which are loaded, and in what version.
 *
 * <p>To use another mod, declare it in steinmod.json — {@code "depends": {"id": "version"}} if
 * yours does not work without it, {@code "optional"} if it does. The Loader only starts yours with
 * what it asks for enabled, and in the requested version, and starts the requested one first. The
 * mods share the same class loader: with the other one compiled in (on your build's classpath),
 * yours calls its classes directly. With {@code optional}, check {@link #isLoaded} before touching
 * any of its classes — the class is only looked up when the code that uses it runs.
 */
public final class Mods {

    private Mods() {}

    /** The mod with this id (the "id" in steinmod.json) has started and is running. */
    public static boolean isLoaded(String id) {
        return Host.INSTANCE.modLoaded(id);
    }

    /** The version of the loaded mod with this id, or null if it is not loaded. */
    public static String version(String id) {
        return Host.INSTANCE.modVersion(id);
    }
}
