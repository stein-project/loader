/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.loader;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.xavier.stein.bridge.core.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Whether a newer version of each mod is out: a mod that declares {@code "source"} in steinmod.json
 * (the address of its index, the same file the launchers read) is compared with what that index
 * says. Only a notice with a link: updating is the launcher's.
 *
 * <p>Runs once per session, in the background, the first time the player opens a screen that shows
 * it, so starting the game costs no network.
 */
public final class ModUpdates {

    /** The same cap as the registry's files: an index is a handful of lines. */
    private static final int LIMIT = 256 * 1024;

    private static boolean started;

    private ModUpdates() {}

    /** A newer version of a mod. */
    public static final class Update {
        public final String version;

        /** The mod's page, or empty. */
        public final String url;

        /** The Loader the new version asks for, when this one is older; otherwise empty. */
        public final String needsLoader;

        Update(String version, String url, String needsLoader) {
            this.version = version;
            this.url = url;
            this.needsLoader = needsLoader;
        }
    }

    static void check(List<ModInfo> mods) {
        if (started || LoaderOptions.NO_REMOTE) {
            return;
        }
        started = true;
        Map<String, List<ModInfo>> bySource = new LinkedHashMap<>();
        for (ModInfo m : mods) {
            if (m.source.isEmpty() || m.builtIn() || m.rejected()) {
                continue;
            }
            if (m.source.toLowerCase(Locale.ROOT).startsWith("https://")) {
                bySource.computeIfAbsent(m.source, k -> new ArrayList<>()).add(m);
            } else {
                Log.info("loader", "update source of " + m.id + " is not https, ignored");
            }
        }
        if (bySource.isEmpty()) {
            return;
        }
        Thread t = new Thread(() -> fetchAll(bySource), "stein-mod-updates");
        t.setDaemon(true);
        t.start();
    }

    private static void fetchAll(Map<String, List<ModInfo>> bySource) {
        HttpClient http =
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build();
        for (Map.Entry<String, List<ModInfo>> e : bySource.entrySet()) {
            try {
                String json = get(http, e.getKey());
                if (json == null) {
                    continue;
                }
                for (ModInfo m : e.getValue()) {
                    m.update = find(json, m.id, m.version, Loader.VERSION);
                }
            } catch (Exception | LinkageError ex) {
                Log.info("loader", "update source " + e.getKey() + " unavailable: " + ex);
            }
        }
    }

    private static String get(HttpClient http, String url) throws Exception {
        HttpRequest req =
                HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(8))
                        .header("User-Agent", "SteinLoader/" + Loader.VERSION)
                        .GET()
                        .build();
        HttpResponse<InputStream> res = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream in = res.body()) {
            if (res.statusCode() != 200) {
                Log.info("loader", "update source " + url + " answered " + res.statusCode());
                return null;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) {
                if (out.size() + n > LIMIT) {
                    Log.info("loader", "update source " + url + " too large, ignored");
                    return null;
                }
                out.write(buf, 0, n);
            }
            return out.toString(StandardCharsets.UTF_8);
        }
    }

    /**
     * The newer version of the mod in an index, or null: none, not this mod's index, or an index
     * that does not parse.
     */
    static Update find(String json, String modId, String installed, String loaderVersion) {
        JsonElement root;
        try {
            root = new JsonParser().parse(json);
        } catch (RuntimeException e) {
            return null;
        }
        if (root == null || !root.isJsonObject()) {
            return null;
        }
        JsonObject o = root.getAsJsonObject();
        JsonElement list = o.get("mods");
        if (list == null || !list.isJsonArray()) {
            return null;
        }
        for (JsonElement e : list.getAsJsonArray()) {
            if (!e.isJsonObject() || !modId.equals(text(e.getAsJsonObject(), "modId"))) {
                continue;
            }
            JsonObject m = e.getAsJsonObject();
            String version = text(m, "version");
            if (version.isEmpty() || Versions.compare(installed, version) >= 0) {
                return null;
            }
            String needs = text(m, "loader");
            boolean fits = needs.isEmpty() || Versions.atLeast(loaderVersion, needs);
            return new Update(version, page(text(o, "homepage")), fits ? "" : needs);
        }
        return null;
    }

    /** The link only goes to the browser when it is https. */
    private static String page(String url) {
        return url.toLowerCase(Locale.ROOT).startsWith("https://") ? url : "";
    }

    private static String text(JsonObject o, String key) {
        JsonElement v = o.get(key);
        return v != null && v.isJsonPrimitive() ? v.getAsString().trim() : "";
    }
}
