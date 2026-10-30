/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.loader;

import dev.xavier.stein.bridge.core.Log;
import dev.xavier.stein.loader.gui.PanelScreen;
import dev.xavier.stein.loader.hud.HudGrid;

import io.netty.buffer.Unpooled;

import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketBuffer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the current server turned off ({@link ServerRequest}). The decision is the server's: the
 * project keeps no list of its own, and it lasts only for the connection, without touching what the
 * player chose.
 *
 * <p>A feature is the mod's to leave off ({@code Server.allows}). A whole mod is the Loader's: it
 * stops the mod's events and takes its HUD elements, its Panel page and its keys out of the game
 * ({@link Loader#suspend}), so that even a mod that does nothing about it goes quiet. What it does
 * on threads of its own, the Loader cannot stop.
 *
 * <p>Like {@link ServerReport}, this rests on the client's word: a changed client can ignore it.
 */
public final class ServerControl {

    /** A server cannot make the mod lists be rebuilt more often than this. */
    private static final long MIN_INTERVAL_MS = 1000;

    /** Every feature turned off, for the question the mods ask on every frame. */
    private static volatile Set<String> features = Collections.emptySet();

    private static Map<String, Set<String>> featuresByMod = Collections.emptyMap();
    private static Set<String> suspended = Collections.emptySet();

    /** The server the state above is for. */
    private static String server = "";

    private static String waiting;
    private static long appliedAt;

    private ServerControl() {}

    /** A plugin packet, on the game thread: only the Loader's channel matters here. */
    static void received(String channel, byte[] data) {
        if (!ServerReport.CHANNEL.equals(channel) || Minecraft.getMinecraft().isSingleplayer()) {
            return;
        }
        try {
            PacketBuffer in = new PacketBuffer(Unpooled.wrappedBuffer(data));
            if (in.readByte() == ServerReport.FORMAT) {
                waiting = in.readStringFromBuffer(ServerReport.LIMIT);
            }
        } catch (RuntimeException e) {
            Log.info("loader", "server request unreadable: " + e);
        }
    }

    /** End of each tick: applies what arrived, and lets go when the connection is gone. */
    static void tick(Loader loader) {
        if (Minecraft.getMinecraft().getNetHandler() == null) {
            waiting = null;
            if (active()) {
                apply(loader, ServerRequest.NONE);
            }
            return;
        }
        if (waiting != null && System.currentTimeMillis() - appliedAt >= MIN_INTERVAL_MS) {
            String json = waiting;
            waiting = null;
            ServerRequest request = ServerRequest.parse(json);
            if (request == null) {
                Log.info("loader", "server request is not a JSON object, ignored");
            } else {
                apply(loader, request);
            }
        }
    }

    /** A join on another address: what the last server asked does not carry over. */
    static void joined(Loader loader, String address) {
        if (active() && !address.equals(server)) {
            waiting = null;
            apply(loader, ServerRequest.NONE);
        }
        server = address;
    }

    private static boolean active() {
        return !suspended.isEmpty() || !featuresByMod.isEmpty();
    }

    private static void apply(Loader loader, ServerRequest request) {
        appliedAt = System.currentTimeMillis();
        Set<String> mods = new HashSet<>();
        Map<String, Set<String>> byMod = new LinkedHashMap<>();
        Set<String> all = new HashSet<>();
        // The game's own parts are not the server's to turn off, and an id that is not here means
        // nothing.
        for (ModInfo m : loader.mods()) {
            if (m.builtIn() || m.state != ModInfo.State.LOADED) {
                continue;
            }
            if (request.mods.contains(m.id)) {
                mods.add(m.id);
            }
            Set<String> asked = request.features.get(m.id);
            if (asked != null) {
                byMod.put(m.id, asked);
                all.addAll(asked);
            }
        }
        boolean modsChanged = !mods.equals(suspended);
        if (!modsChanged && byMod.equals(featuresByMod)) {
            return;
        }
        suspended = mods;
        featuresByMod = byMod;
        features = all;
        if (modsChanged) {
            loader.suspend(mods);
            closeOpenScreen();
        }
        Log.info("loader", "server request applied: mods " + mods + ", features " + byMod);
        ServerReport.update(loader);
    }

    /** A Panel or an editor already open would keep showing what just left. */
    private static void closeOpenScreen() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen instanceof PanelScreen || HudGrid.isEditor(mc.currentScreen)) {
            mc.displayGuiScreen(null);
        }
    }

    /** Does the current server allow the feature? Cheap: the mods ask on every frame. */
    static boolean allows(String feature) {
        Set<String> off = features;
        return off.isEmpty() || !off.contains(feature);
    }

    /** The name of the server that turned the feature off, or "". */
    static String restrictedBy(String feature) {
        if (allows(feature)) {
            return "";
        }
        String name = ServerOps.serverName();
        return name.isEmpty() ? server : name;
    }

    /** Is the whole mod off on this server? */
    public static boolean isSuspended(ModInfo m) {
        return suspended.contains(m.id);
    }

    /** The features of this mod turned off on this server. */
    public static List<String> disabledFeatures(ModInfo m) {
        Set<String> f = featuresByMod.get(m.id);
        return f == null ? Collections.emptyList() : new ArrayList<>(f);
    }
}
