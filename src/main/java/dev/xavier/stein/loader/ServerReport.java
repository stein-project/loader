/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.loader;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

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
 * "mods":[{"id":"steinminimap","name":"Minimap","version":"0.1.0","state":"loaded",
 *          "features":["minimap"]}],
 * "disabled":[{"mod":"steinminimap","feature":"minimap"},{"mod":"steincombat"}]}
 * </pre>
 *
 * The states are the same as in the mod list, in English for whoever reads on the other side:
 * {@code loaded}, {@code disabled}, {@code failed}, {@code rejected}. {@code features} is what a
 * server can ask to turn off ({@link ServerRequest}) and {@code disabled} what is off now because
 * of it, without a feature for the whole mod: a mod the player disabled is a state, one the server
 * turned off is in this list. The list goes again whenever that changes. A server only gets what
 * the client says — this reports what is there, it proves nothing.
 *
 * <p>Nothing is sent in a local world, and {@code -Dsteinloader.noAnnounce=true} turns sending off.
 */
final class ServerReport {

    /** The 1.8 channel (free-form name, up to 20 letters), the same name shown in the menu. */
    static final String CHANNEL = "SteinLoader";

    /** Format of the content; goes up if what is inside changes shape. */
    static final int FORMAT = 1;

    /**
     * The 1.8 plugin packet stops being valid at around 32 KB; the list is nowhere near that, but
     * the limit is written down.
     */
    static final int LIMIT = 30000;

    private ServerReport() {}

    static void announce(Loader loader) {
        Minecraft mc = Minecraft.getMinecraft();
        NetHandlerPlayClient net = mc.getNetHandler();
        if (net == null || mc.isSingleplayer() || LoaderOptions.NO_ANNOUNCE) {
            return;
        }
        net.addToSendQueue(
                new C17PacketCustomPayload(
                        "REGISTER",
                        new PacketBuffer(
                                Unpooled.wrappedBuffer(CHANNEL.getBytes(StandardCharsets.UTF_8)))));
        update(loader);
    }

    /** The list again, on the channel already registered: what is off changed. */
    static void update(Loader loader) {
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
        root.addProperty("bridge", Loader.bridgeVersion());
        JsonArray mods = new JsonArray();
        JsonArray disabled = new JsonArray();
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
            if (!m.features.isEmpty()) {
                JsonArray features = new JsonArray();
                for (String f : m.features) {
                    features.add(new JsonPrimitive(f));
                }
                o.add("features", features);
            }
            mods.add(o);
            addDisabled(disabled, m);
        }
        root.add("mods", mods);
        root.add("disabled", disabled);
        return new Gson().toJson(root);
    }

    private static void addDisabled(JsonArray out, ModInfo m) {
        if (ServerControl.isSuspended(m)) {
            JsonObject o = new JsonObject();
            o.addProperty("mod", m.id);
            out.add(o);
        }
        for (String f : ServerControl.disabledFeatures(m)) {
            JsonObject o = new JsonObject();
            o.addProperty("mod", m.id);
            o.addProperty("feature", f);
            out.add(o);
        }
    }
}
