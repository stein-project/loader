/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Breno Xavier
 *
 * Part of the Stein Loader API, licensed under the MIT License (see LICENSE-API).
 */

package dev.xavier.stein.loader.api;

import dev.xavier.stein.loader.api.spi.Host;

/** The server the player is on, and joining another one. */
@Experimental(since = "0.1.16")
public final class Server {

    private Server() {}

    /**
     * The current server's address, in comparable form: lowercase and without the default port
     * ("mc.example.com", "1.2.3.4:25570"); "" outside a server (menu, local world). Also holds when
     * the game joined via the command line, without the server list.
     */
    public static String address() {
        return Host.INSTANCE.serverAddress();
    }

    /** The server's name in the server list, or "". */
    public static String name() {
        return Host.INSTANCE.serverName();
    }

    /** On a server (and not in the menu or a local world)? */
    public static boolean isMultiplayer() {
        return !address().isEmpty();
    }

    /**
     * The ping to the current server, in ms, measured by the client itself the way the server list
     * measures it (not the Tab one, which many servers leave at 0); -1 while there is no
     * measurement, or outside a server. The measurement only runs while some mod asks for it: call
     * it on every frame or tick in which you will display it, and it renews itself every 5 s.
     */
    @Experimental(since = "0.1.21")
    public static int latency() {
        return Host.INSTANCE.serverLatency();
    }

    /** The last server joined in this session ("" if none): what a "reconnect" uses. */
    public static String lastAddress() {
        return Host.INSTANCE.serverLast();
    }

    /**
     * The server of the connection in progress or of the last one attempted, even if it never got
     * in ("" if none): it is what the disconnect screen was trying, and where a "reconnect" goes
     * back to.
     */
    public static String connectingAddress() {
        return Host.INSTANCE.serverConnecting();
    }

    /**
     * Joins the server ("host" or "host:port"), like the list's button: leaves the current one, if
     * any.
     */
    public static void connect(String address) {
        Host.INSTANCE.serverConnect(address);
    }
}
