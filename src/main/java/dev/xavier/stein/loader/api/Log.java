/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Breno Xavier
 *
 * Part of the Stein Loader API, licensed under the MIT License (see LICENSE-API).
 */

package dev.xavier.stein.loader.api;

import dev.xavier.stein.loader.api.spi.Host;

/**
 * A mod's log, prefixed with its id: {@code [mymod] message}. It goes to the game log
 * (logs/latest.log) and, with Stein's log enabled, to that as well.
 *
 * <p>{@link #debug} only writes when the mod's debugging is on: {@code -Dstein.debug=mymod} (or
 * {@code =all}, or a comma-separated list) on the game's command line.
 */
@Experimental(since = "0.1.16")
public final class Log {

    // Levels of Host.log.
    static final int INFO = 0;
    static final int WARN = 1;
    static final int ERROR = 2;

    private final String id;
    private final boolean debug;

    private Log(String id) {
        this.id = id;
        String on = System.getProperty("stein.debug", "");
        boolean d = false;
        for (String s : on.split(",")) {
            String name = s.trim();
            d |= name.equals(id) || name.equals("all");
        }
        this.debug = d;
    }

    /** The log of the mod with this id. */
    public static Log of(String modId) {
        return new Log(modId);
    }

    public void info(String message) {
        Host.INSTANCE.log(id, INFO, message, null);
    }

    public void warn(String message) {
        Host.INSTANCE.log(id, WARN, message, null);
    }

    public void error(String message, Throwable error) {
        Host.INSTANCE.log(id, ERROR, message, error);
    }

    /** Only with the mod's debugging on (see the class). */
    public void debug(String message) {
        if (debug) {
            Host.INSTANCE.log(id, INFO, "(debug) " + message, null);
        }
    }

    public boolean isDebug() {
        return debug;
    }
}
