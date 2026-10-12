/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.loader.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;

import dev.xavier.stein.bridge.core.Log;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The skin of the nick's owner, for servers without authentication (offline). There the server
 * sends the profile with no texture at all and everyone becomes Steve; here, with the option on, we
 * ask Mojang for that nick's UUID and hand the profile to the game's own {@code SkinManager}, which
 * fetches the textures, downloads them and stores them on disk as it does for any skin. It came
 * from the HUD's Tab list; it is what Render.playerHead(..., premiumSkin) uses.
 *
 * <p><b>The skin may not belong to that person</b>: on an offline server, the "Notch" there is not
 * Notch. That is why the option starts off — it is useful information for recognising people you
 * know, and misleading for everyone else.
 *
 * <p>Care taken with the Mojang API: names go in batches of ten in a single query, at most one
 * query every three seconds, and what has already been answered (including "this nick does not
 * exist") is not asked again. None of this runs on the game thread.
 */
final class SkinLookup {

    private static final String NAMES = "https://api.mojang.com/profiles/minecraft";
    private static final int BATCH = 10;
    private static final long EVERY_MS = 3000L;

    /** Nick -> skin found; a nick without a premium account goes in {@link #missing}. */
    // Written by the query thread and read by the game thread.
    private final Map<String, ResourceLocation> found = new ConcurrentHashMap<>();

    private final Set<String> missing = ConcurrentHashMap.newKeySet();

    /** Nicks seen on screen and not yet asked about. */
    private final Queue<String> queue = new ConcurrentLinkedQueue<>();

    private final Set<String> queued = ConcurrentHashMap.newKeySet();
    private final Set<String> asking = ConcurrentHashMap.newKeySet();
    private volatile long last;
    private volatile boolean running;
    private HttpClient http;

    /** The nick's skin, or null while it is not known (drawing uses the game's own then). */
    ResourceLocation skin(String nick) {
        ResourceLocation r = found.get(nick);
        if (r != null) {
            return r;
        }
        if (!missing.contains(nick) && !asking.contains(nick) && queued.add(nick)) {
            queue.add(nick);
        }
        return null;
    }

    /** Once per frame, from drawing the list: sends the next batch if the interval has passed. */
    void tick() {
        if (running || queue.isEmpty() || System.currentTimeMillis() - last < EVERY_MS) {
            return;
        }
        List<String> batch = new ArrayList<>(BATCH);
        while (batch.size() < BATCH && !queue.isEmpty()) {
            String nick = queue.poll();
            queued.remove(nick);
            if (nick != null && nick.matches("[A-Za-z0-9_]{3,16}")) {
                batch.add(nick);
                asking.add(nick);
            }
        }
        if (batch.isEmpty()) {
            return;
        }
        running = true;
        last = System.currentTimeMillis();
        Thread t = new Thread(() -> ask(batch), "stein-skins");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Nicks -> UUIDs at Mojang; each one found becomes a profile that the game's SkinManager
     * resolves and downloads.
     */
    private void ask(List<String> batch) {
        try {
            if (http == null) {
                http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
            }
            StringBuilder body = new StringBuilder("[");
            for (int i = 0; i < batch.size(); i++) {
                body.append(i == 0 ? "\"" : ",\"").append(batch.get(i)).append('"');
            }
            body.append(']');
            HttpRequest request =
                    HttpRequest.newBuilder(URI.create(NAMES))
                            .timeout(Duration.ofSeconds(10))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                            .build();
            HttpResponse<String> response =
                    http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                Log.info("loader", "skins by nick: Mojang answered " + response.statusCode());
                free(batch, true);
                return;
            }
            Set<String> answered = new HashSet<>();
            JsonElement parsed = new JsonParser().parse(response.body());
            if (parsed.isJsonArray()) {
                JsonArray array = parsed.getAsJsonArray();
                for (JsonElement e : array) {
                    String id = e.getAsJsonObject().get("id").getAsString();
                    String name = e.getAsJsonObject().get("name").getAsString();
                    answered.add(name.toLowerCase(Locale.ROOT));
                    load(name, uuid(id), batch);
                }
            }
            // Those Mojang does not know do not come back in the response: do not ask again.
            for (String nick : batch) {
                if (!answered.contains(nick.toLowerCase(Locale.ROOT))) {
                    missing.add(nick);
                    asking.remove(nick);
                }
            }
        } catch (Exception e) {
            Log.info("loader", "skins by nick: " + e);
            free(batch, false);
        } finally {
            running = false;
        }
    }

    /**
     * The game's SkinManager fetches the profile's textures, downloads them and signals when the
     * texture is ready.
     */
    private void load(String name, UUID id, List<String> batch) {
        Minecraft mc = Minecraft.getMinecraft();
        GameProfile profile = new GameProfile(id, name);
        String nick = pick(batch, name);
        mc.getSkinManager()
                .loadProfileTextures(
                        profile,
                        (type, location, texture) -> {
                            if (type == MinecraftProfileTexture.Type.SKIN && location != null) {
                                found.put(nick, location);
                                asking.remove(nick);
                            }
                        },
                        false);
    }

    /**
     * The nick as it appears in the server list (Mojang returns it with its own capitalisation).
     */
    private static String pick(List<String> batch, String name) {
        for (String nick : batch) {
            if (nick.equalsIgnoreCase(name)) {
                return nick;
            }
        }
        return name;
    }

    /**
     * Releases the nicks: with retry=true they go back to the queue, otherwise they stay out until
     * the next session.
     */
    private void free(List<String> batch, boolean retry) {
        for (String nick : batch) {
            asking.remove(nick);
            if (retry && queued.add(nick)) {
                queue.add(nick);
            } else if (!retry) {
                missing.add(nick);
            }
        }
    }

    private static UUID uuid(String id) {
        return UUID.fromString(
                id.replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));
    }
}
