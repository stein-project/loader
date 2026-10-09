/*
 * Copyright (c) 2026 Breno Xavier. All rights reserved.
 * Proprietary software: see the LICENSE file at the root of this repository.
 */

package dev.xavier.stein.loader;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import dev.xavier.stein.bridge.SteinBridge;
import dev.xavier.stein.bridge.core.Log;
import dev.xavier.stein.bridge.core.SteinLog;

import io.netty.buffer.Unpooled;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.C17PacketCustomPayload;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * What this client has loaded, told to the server on joining — the way Forge sends its list in
 * FML|HS and LiteLoader announces itself in LM|Init.
 *
 * <p>The idea is not to be a client the server knows nothing about: whoever runs it can see the
 * bridge version, the Loader version and each mod with its version and state, and decide what to
 * accept. It goes on a regular 1.8 plugin channel, so any plugin can read it with nothing special:
 *
 * <pre>
 * REGISTER      "SteinLoader"                    (the channel this client listens on)
 * SteinLoader   byte format (1) + string JSON    (PacketBuffer.writeString, length-prefixed)
 *
 * {"loader":"0.1.3","bridge":"0.1.2",
 * "mods":[{"id":"steinhud","name":"HUD","version":"0.1.3","state":"loaded"}]}
 * </pre>
 *
 * The states are the same as in the mod list, in English for whoever reads on the other side:
 * {@code loaded}, {@code disabled}, {@code failed}, {@code rejected}. A server only gets what the
 * client says — this reports what is there, it proves nothing.
 *
 * <p>Nothing is sent in a local world, and {@code -Dsteinloader.noAnnounce=true} turns sending off.
 */
final class ServerReport {

    /** The 1.8 channel (free-form name, up to 20 letters), the same name shown in the menu. */
    static final String CHANNEL = "SteinLoader";

    /** Format of the content; goes up if what is inside changes shape. */
    private static final int FORMAT = 1;

    /**
     * The 1.8 plugin packet stops being valid at around 32 KB; the list is nowhere near that, but
     * the limit is written down.
     */
    private static final int LIMIT = 30000;

    private ServerReport() {}

    static void announce(Loader loader) {
        Minecraft mc = Minecraft.getMinecraft();
        NetHandlerPlayClient net = mc.getNetHandler();
        if (net == null || mc.isSingleplayer() || LoaderOptions.NO_ANNOUNCE) {
            return;
        }
        String json = json(loader);
        if (json.length() > LIMIT) {
            Log.info(
                    "loader",
                    "mod list too large for the server (" + json.length() + "): not sent");
            return;
        }
        net.addToSendQueue(
                new C17PacketCustomPayload(
                        "REGISTER",
                        new PacketBuffer(
                                Unpooled.wrappedBuffer(CHANNEL.getBytes(StandardCharsets.UTF_8)))));
        PacketBuffer data = new PacketBuffer(Unpooled.buffer());
        data.writeByte(FORMAT);
        data.writeString(json);
        net.addToSendQueue(new C17PacketCustomPayload(CHANNEL, data));
        SteinLog.debug(SteinLog.Topic.NET, "loader", "mod list sent to the server: " + json);
    }

    /** The same list as the menu: bridge, Loader and each mod in the folder, with its run state. */
    private static String json(Loader loader) {
        JsonObject root = new JsonObject();
        root.addProperty("loader", Loader.VERSION);
        root.addProperty("bridge", SteinBridge.VERSION);
        JsonArray mods = new JsonArray();
        for (ModInfo m : loader.mods()) {
            if (m.builtIn() || !m.announce) {
                // The Bridge and the Loader are already on top; "announce": false mods are not
                // sent.
                continue;
            }
            JsonObject o = new JsonObject();
            o.addProperty("id", m.id);
            o.addProperty("name", m.name);
            o.addProperty("version", m.version);
            o.addProperty("state", m.state().name().toLowerCase(Locale.ROOT));
            mods.add(o);
        }
        root.add("mods", mods);
        return new Gson().toJson(root);
    }
}
