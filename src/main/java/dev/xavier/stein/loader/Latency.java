/*
 * Copyright (c) 2026 Breno Xavier. All rights reserved.
 * Proprietary software: see the LICENSE file at the root of this repository.
 */

package dev.xavier.stein.loader;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.network.OldServerPinger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The current ping to the server, measured by the client itself ({@code Server.latency}).
 *
 * <p>The Tab one is whatever the server writes in the list, and many servers (BungeeCord, Tab
 * plugins) write 0 for everyone. Here the measurement is the game's server list one: a status query
 * (OldServerPinger) to the same address, which sends a ping and times the pong. It goes through the
 * same connection path as the game, so through the Connect proxy when it is on, and the time is
 * only the network's (the pong is handled on the Netty thread, it does not wait for the tick).
 *
 * <p>It only measures while someone asks for it (a HUD element with the ping), once every 5 s: to
 * the server this looks like a player viewing the server list. The connection (DNS and connect,
 * which block) happens on a separate thread; the tick only collects the result.
 */
final class Latency {

    /** One measurement every 5 s. */
    private static final long EVERY_MS = 5000;

    /** Nobody asked in the last 3 s: stop measuring. */
    private static final long WANTED_MS = 3000;

    /** With no pong in 10 s, the measurement is considered lost. */
    private static final long TIMEOUT_MS = 10000;

    private static final OldServerPinger PINGER = new OldServerPinger();
    private static final ExecutorService PING_EXECUTOR =
            Executors.newSingleThreadExecutor(
                    r -> {
                        Thread t = new Thread(r, "Stein ping");
                        t.setDaemon(true);
                        return t;
                    });

    private static volatile long wantedAt;
    private static volatile int last = -1;

    /** The server of the last measurement: when switching servers, the earlier one is void. */
    private static String address = "";

    private static ServerData probe;
    private static long sentAt;
    private static long nextAt;

    private Latency() {}

    /**
     * The last ping measured, in ms; -1 with no measurement (not measured yet, or outside a
     * server).
     */
    static int get() {
        wantedAt = System.currentTimeMillis();
        return last;
    }

    /**
     * At the end of the tick: collects the measurement in progress and starts the next, if it is
     * time.
     */
    static void tick() {
        long now = System.currentTimeMillis();
        String current = ServerOps.serverAddress();
        if (!current.equals(address)) {
            address = current;
            last = -1;
            probe = null;
            nextAt = 0;
        }
        PINGER.pingPendingNetworks();
        if (probe != null) {
            if (probe.pingToServer >= 0) {
                last = (int) probe.pingToServer;
                probe = null;
            } else if (now - sentAt > TIMEOUT_MS) {
                probe = null;
            }
        }
        if (probe != null || current.isEmpty() || now - wantedAt > WANTED_MS || now < nextAt) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        ServerData d = mc.getCurrentServerData();
        ServerData p =
                new ServerData("", d != null && d.serverIP != null ? d.serverIP : current, false);
        // ServerData starts with 0, which would pass as a measurement before the thread starts.
        p.pingToServer = -1;
        probe = p;
        sentAt = now;
        nextAt = now + EVERY_MS;
        PING_EXECUTOR.execute(
                () -> {
                    try {
                        PINGER.ping(p);
                    } catch (Exception e) {
                        p.pingToServer = -2; // did not connect: the tick just waits for the timeout
                    }
                });
    }
}
