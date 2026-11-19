/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Breno Xavier
 *
 * Part of the Stein Loader API, licensed under the MIT License (see LICENSE-API).
 */

package dev.xavier.stein.loader.api;

import dev.xavier.stein.loader.api.spi.Host;

import java.util.List;

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

    /**
     * Does the current server allow this feature? The server decides, on the {@code SteinLoader}
     * plugin channel, and the mods leave those features off <b>only while the player is there</b>,
     * without changing what the player chose. Always true outside a server, and on a server that
     * says nothing.
     *
     * <p>A mod declares what it asks about in {@code "features"} in steinmod.json, which is how a
     * server learns the names. Cheap enough to ask on every frame.
     */
    @Experimental(since = "0.1.28")
    public static boolean allows(String feature) {
        return Host.INSTANCE.serverAllows(feature);
    }

    /**
     * The name of the server that turns the feature off here, for the mod to tell the player why an
     * option is off; "" when the feature is allowed.
     */
    @Experimental(since = "0.1.28")
    public static String restrictedBy(String feature) {
        return Host.INSTANCE.serverRestrictedBy(feature);
    }

    /**
     * Is this connection in Stein Sync mode? A Stein Sync proxy in front of the server offered it
     * when the player joined and this client accepted. False outside a server, on a server without
     * Stein Sync and with {@code -Dsteinloader.noSteinSync=true}. Cheap enough to ask on every
     * frame.
     */
    @Experimental(since = "0.1.35")
    public static boolean steinSyncActive() {
        return Host.INSTANCE.serverSteinSyncVersion() > 0;
    }

    /**
     * The Stein Sync protocol version agreed on this connection (the lower of the proxy's and this
     * client's), or 0 when it is not in Stein Sync mode.
     */
    @Experimental(since = "0.1.35")
    public static int steinSyncVersion() {
        return Host.INSTANCE.serverSteinSyncVersion();
    }

    /**
     * The Stein Sync features turned on for this connection, as named by the protocol: the ones the
     * server offered and this client supports. Unmodifiable; empty when Stein Sync is not active.
     * This client knows {@code "hits"} (hit confirmation, attacks judged by the proxy, damage and
     * combat events), {@code "blocks"} (block changes resync, without flicker) and {@code "move"}
     * (one message per tick in place of the vanilla movement packets).
     */
    @Experimental(since = "0.1.35")
    public static List<String> steinSyncFeatures() {
        return Host.INSTANCE.serverSteinSyncFeatures();
    }

    /**
     * Does this connection confirm hits? True when Stein Sync is active with its {@code hits}
     * feature: then {@link SteinMod#onHitConfirmed}, {@link SteinMod#onDamaged} and {@link
     * SteinMod#onCombatSeen} report what the server applied. Cheap enough to ask on every frame.
     */
    @Experimental(since = "0.1.35")
    public static boolean steinSyncHits() {
        return Host.INSTANCE.serverSteinSyncHits();
    }

    /** {@link SteinMod#onDamaged} cause: anything not listed below, or a cause unknown here. */
    @Experimental(since = "0.1.35")
    public static final int DAMAGE_OTHER = 0;

    /** {@link SteinMod#onDamaged} cause: a melee hit. */
    @Experimental(since = "0.1.35")
    public static final int DAMAGE_MELEE = 1;

    /** {@link SteinMod#onDamaged} cause: a projectile. */
    @Experimental(since = "0.1.35")
    public static final int DAMAGE_PROJECTILE = 2;

    /** {@link SteinMod#onDamaged} cause: an explosion. */
    @Experimental(since = "0.1.35")
    public static final int DAMAGE_EXPLOSION = 3;

    /** {@link SteinMod#onDamaged} cause: a fall. */
    @Experimental(since = "0.1.35")
    public static final int DAMAGE_FALL = 4;

    /** {@link SteinMod#onDamaged} cause: fire or lava. */
    @Experimental(since = "0.1.35")
    public static final int DAMAGE_FIRE = 5;

    /** {@link SteinMod#onDamaged} cause: the void. */
    @Experimental(since = "0.1.35")
    public static final int DAMAGE_VOID = 6;

    /** {@link SteinMod#onDamaged} cause: magic or an effect (poison, wither, potions). */
    @Experimental(since = "0.1.35")
    public static final int DAMAGE_MAGIC = 7;

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
