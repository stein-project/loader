/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Breno Xavier
 *
 * Part of the Stein Loader API, licensed under the MIT License (see LICENSE-API).
 */

package dev.xavier.stein.loader.api;

import dev.xavier.stein.loader.api.spi.Host;

/**
 * The account the game uses to join servers. The mod switches and queries, but <b>never sees the
 * token</b>: the launcher account is kept by the Loader, the Microsoft verification belongs to the
 * Loader, and the token does not leave it (not even to the log).
 */
@Experimental(since = "0.1.16")
public final class Account {

    /** The current account's type. */
    public enum Kind {
        /**
         * No account (offline nick, or launcher without an account): only servers without
         * authentication.
         */
        OFFLINE,
        /** Checking with Microsoft (the first query triggers the check, off the game thread). */
        CHECKING,
        /** A Microsoft account that owns the game, and it is this session's. */
        MICROSOFT,
        /** The service rejected the token: it is not a Microsoft account that owns the game. */
        NOT_MICROSOFT,
        /** Could not check (no internet, service down): asks again after 30 s. */
        UNVERIFIED
    }

    private static final Kind[] KINDS = Kind.values();

    private Account() {}

    /** The current session's nick. */
    public static String name() {
        return Host.INSTANCE.accountName();
    }

    /** The nick of the account the launcher handed over. */
    public static String launcherName() {
        return Host.INSTANCE.accountLauncherName();
    }

    /** Is the current session the launcher's? */
    public static boolean isLauncher() {
        return Host.INSTANCE.accountIsLauncher();
    }

    /**
     * The current session's type; the first query of each session starts the check (see {@link
     * Kind#CHECKING}).
     */
    public static Kind kind() {
        return KINDS[Host.INSTANCE.accountKind()];
    }

    /**
     * The launcher account's type, even with another session active (the account list shows both).
     */
    public static Kind launcherKind() {
        return KINDS[Host.INSTANCE.accountLauncherKind()];
    }

    /**
     * Starts joining as the nick, with no account (the offline UUID that a server without
     * authentication gives the nick).
     */
    public static void useOffline(String nick) {
        if (!Players.isValidNick(nick)) {
            throw new IllegalArgumentException("invalid nick: " + nick);
        }
        Host.INSTANCE.accountUseOffline(nick);
    }

    /** Goes back to the launcher account. */
    public static void restoreLauncher() {
        Host.INSTANCE.accountRestore();
    }
}
