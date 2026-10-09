/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Breno Xavier
 *
 * Part of the Stein Loader API, licensed under the MIT License (see LICENSE-API).
 */

package dev.xavier.stein.loader.api;

import dev.xavier.stein.loader.api.spi.Host;

/**
 * Where the connection to the server goes out — for a mod that knows how to talk to a proxy. The
 * Loader implements nothing: it only hands the mod the two points that the Bridge opens in the
 * game's connection path.
 *
 * <p>Netty types arrive and leave as Object (the mod compiles with the game, so it can convert
 * them). One mod at a time: registering again replaces the previous one, and null removes it.
 */
public final class Net {

    /** Whoever diverts the connection. */
    public interface Proxy {
        /**
         * Where to actually connect.
         *
         * @param destination the server's InetAddress
         * @param port its port
         * @return the SocketAddress to go through (the proxy), or null to go direct
         */
        Object address(Object destination, int port);

        /**
         * The channel initializer to use: the game's builds the packet stack, and whoever returns
         * another can put handlers before it (a tunnel, for example).
         *
         * @param initializer the game's (ChannelHandler)
         * @return the same one, or another
         */
        Object wrap(Object initializer);
    }

    private Net() {}

    /** Registers (or removes, with null) whoever diverts the connection. */
    public static void set(Proxy p) {
        Host.INSTANCE.setProxy(p);
    }

    /**
     * Sends a plugin packet to the server (C17PacketCustomPayload) on the given channel — the
     * counterpart of {@link SteinMod#onCustomPayload}. Without a connection, does nothing.
     *
     * @return true if it went to the send queue
     */
    @Experimental(since = "0.1.14")
    public static boolean sendPayload(String channel, byte[] data) {
        return Host.INSTANCE.sendPayload(channel, data);
    }
}
