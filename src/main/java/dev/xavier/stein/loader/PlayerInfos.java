/*
 * Copyright (c) 2026 Breno Xavier. All rights reserved.
 * Proprietary software: see the LICENSE file at the root of this repository.
 */

package dev.xavier.stein.loader;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;

import java.util.HashMap;
import java.util.Map;

/**
 * The Tab row of each nick, in an index. The game only finds a player by nick by walking the whole
 * list (NetHandlerPlayClient.getPlayerInfo(String)): with 1700 in the Tab, asking each one's ping,
 * team or head turned into millions of iterations per frame. Here the index is rebuilt at most
 * every 50 ms (one tick), or when someone joins or leaves, and each question is a lookup.
 */
public final class PlayerInfos {

    private static final Map<String, NetworkPlayerInfo> BY_NICK = new HashMap<>();
    private static final long MAX_AGE_NANOS = 50_000_000L;
    private static NetHandlerPlayClient cachedHandler;
    private static int size = -1;
    private static long builtAtNanos;

    private PlayerInfos() {}

    /** The nick's Tab row (case counts, as in the game), or null. */
    public static NetworkPlayerInfo get(String nick) {
        NetHandlerPlayClient n = Minecraft.getMinecraft().getNetHandler();
        if (n == null || nick == null) {
            return null;
        }
        long now = System.nanoTime();
        int s = n.getPlayerInfoMap().size();
        if (n != cachedHandler || s != size || now - builtAtNanos > MAX_AGE_NANOS) {
            BY_NICK.clear();
            for (NetworkPlayerInfo p : n.getPlayerInfoMap()) {
                String name = nickOf(p);
                if (name != null) {
                    BY_NICK.put(name, p);
                }
            }
            cachedHandler = n;
            size = s;
            builtAtNanos = now;
        }
        return BY_NICK.get(nick);
    }

    /** The player's nick, or null if the entry has no profile or no name. */
    static String nickOf(NetworkPlayerInfo p) {
        return p.getGameProfile() == null ? null : p.getGameProfile().getName();
    }
}
