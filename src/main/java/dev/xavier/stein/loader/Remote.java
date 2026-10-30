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

import net.minecraft.client.Minecraft;

import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * What the project publishes and the Loader reads at startup: the Discord invite and a message for
 * the main menu.
 *
 * <p>The file is {@code stein.json} of the Stein registry ({@link Registry}), signed, and not
 * inside the jar: a changed invite or a new message takes effect on the next start of anyone who
 * already has the Loader installed, with no new version or new installer. {@code discord} and
 * {@code news} are edited by hand in the registry.
 *
 * <pre>
 * { "schema": 1, "discord": "https://discord.gg/xxxxxxx",
 *   "news": { "id": "discord", "title": { "en_US": "..." }, "text": { "en_US": "..." },
 *             "url": "https://discord.gg/xxxxxxx" } }
 * </pre>
 *
 * <p>The fetch runs on another thread with a short timeout: the game starts the same without
 * internet. The signed copy is stored by the registry and is what applies next time, even before
 * the fetch answers; a copy that does not arrive, or arrives without a valid signature, keeps the
 * stored one, so the Discord button and the message stay there, offline too. Keys this version does
 * not read ({@code latest}, {@code sources}, {@code defaultMods}, the launcher's) are ignored.
 *
 * <p>Loaders up to 0.1.27 read {@code stein.json} of the installer's latest release, which its
 * release workflow keeps publishing for them; the copy those Loaders stored ({@code
 * config/steinloader-remoto.json}) is read once here when the registry has none yet.
 */
public final class Remote {

    /** The registry file. */
    static final String FILE = "stein.json";

    /** The copy that Loaders up to 0.1.27 stored, read while the registry has none. */
    private static final String OLD_CACHE = "config/steinloader-remoto.json";

    /**
     * A message from the project for the main menu.
     *
     * <p>Title and text can come in a single language (a string, which applies to everyone) or in
     * an object with one entry per game language ({@code {"en_US": "...", "pt_BR": "..."}}).
     * Whoever plays in another language reads the {@code en_US} one (English is what everyone can
     * reach), and the language is chosen at draw time, so that changing the game language changes
     * the message without restarting.
     */
    public static final class News {
        public final String id;

        /** Address to open on click, or empty. */
        public final String url;

        private final Map<String, String> titles;
        private final Map<String, String> texts;

        News(String id, Map<String, String> titles, Map<String, String> texts, String url) {
            this.id = id;
            this.titles = titles;
            this.texts = texts;
            this.url = url;
        }

        public String title() {
            return pick(titles, "Stein Labs");
        }

        public String text() {
            return pick(texts, "");
        }

        /**
         * The game's language, then the language without the country, then English, then whatever
         * exists.
         */
        private static String pick(Map<String, String> byLang, String fallback) {
            if (byLang.isEmpty()) {
                return fallback;
            }
            String plain = byLang.get("");
            if (plain != null) {
                return plain;
            }
            String code = language();
            String s = byLang.get(code);
            if (s == null && code.indexOf('_') > 0) {
                s = byLang.get(code.substring(0, code.indexOf('_')));
            }
            if (s == null) {
                s = byLang.get("en_us");
            }
            if (s == null) {
                s = byLang.get("en");
            }
            return s == null ? byLang.values().iterator().next() : s;
        }

        private static String language() {
            try {
                String code = Minecraft.getMinecraft().gameSettings.language;
                return code == null ? "en_us" : code.toLowerCase(Locale.ROOT);
            } catch (RuntimeException | LinkageError e) {
                return "en_us";
            }
        }
    }

    private static volatile String discord = "";

    private static volatile News news;

    private Remote() {}

    /**
     * Reads the last copy and goes after the new one, together with the registry's other files.
     * Called at game start.
     */
    static void start(File gameDir) {
        if (LoaderOptions.NO_REMOTE) {
            return;
        }
        Registry.start(gameDir);
        String stored = Registry.cached(FILE);
        if (stored == null) {
            stored = readOld(new File(gameDir, OLD_CACHE));
        }
        apply(stored);
        Registry.refresh(new Registry.Entry(FILE, Remote::apply));
    }

    /**
     * Publishes a copy of the file and says whether it was taken; one that does not parse, or has a
     * schema this version does not know, changes nothing.
     */
    private static boolean apply(String json) {
        if (json == null) {
            return false;
        }
        try {
            if (!parse(json)) {
                Log.info("loader", "Stein Labs: not a schema 1 object, keeping what was there");
                return false;
            }
            return true;
        } catch (RuntimeException e) {
            Log.info("loader", "Stein Labs copy unreadable: " + e);
            return false;
        }
    }

    private static String readOld(File f) {
        try {
            return f.isFile()
                    ? new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8)
                    : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** The project's Discord invite, or empty: it decides whether the button appears. */
    public static String discord() {
        return discord;
    }

    /** The current message, or null. The id is what says whether the player already closed it. */
    public static News news() {
        return news;
    }

    /**
     * Reads the file and publishes what it says. Returns false, changing nothing, when the root is
     * not a JSON object or its schema is not 1 (no schema counts as 1, as in the copies Loaders up
     * to 0.1.27 stored); throws on JSON that does not parse, also changing nothing.
     */
    private static boolean parse(String json) {
        JsonElement root = new JsonParser().parse(json);
        if (!root.isJsonObject()) {
            return false;
        }
        JsonObject o = root.getAsJsonObject();
        if (o.has("schema") && o.get("schema").getAsInt() != 1) {
            return false;
        }
        // Only the keys below are read: older files also had "servers" and "mods", ignored now.
        String newDiscord = link(text(o, "discord"), "discord.gg", "discord.com", "discordapp.com");
        News newNews = news(o.get("news"));

        // Everything parsed: only now publish, so bad JSON never leaves half-applied state.
        discord = newDiscord;
        news = newNews;
        return true;
    }

    /**
     * The message from the file, or null when there is none (or when it came without an id, so that
     * what the player already closed would not apply).
     */
    private static News news(JsonElement raw) {
        if (raw == null || !raw.isJsonObject()) {
            return null;
        }
        JsonObject o = raw.getAsJsonObject();
        String id = text(o, "id").trim();
        Map<String, String> texts = byLanguage(o.get("text"));
        if (id.isEmpty() || texts.isEmpty()) {
            return null;
        }
        return new News(
                id,
                byLanguage(o.get("title")),
                texts,
                link(text(o, "url"), "discord.gg", "discord.com", "discordapp.com", "github.com"));
    }

    /**
     * The field in a single language (string) or one language per entry (object). The string
     * becomes the entry with an empty key: it is the one that applies to everyone, with no language
     * choice.
     */
    private static Map<String, String> byLanguage(JsonElement raw) {
        Map<String, String> out = new LinkedHashMap<>();
        if (raw == null || raw.isJsonNull()) {
            return out;
        }
        if (raw.isJsonPrimitive()) {
            String s = raw.getAsString().trim();
            if (!s.isEmpty()) {
                out.put("", s);
            }
            return out;
        }
        if (raw.isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : raw.getAsJsonObject().entrySet()) {
                if (e.getValue().isJsonPrimitive()
                        && !e.getValue().getAsString().trim().isEmpty()) {
                    out.put(
                            e.getKey().trim().toLowerCase(Locale.ROOT),
                            e.getValue().getAsString().trim());
                }
            }
        }
        return out;
    }

    /**
     * An address the Loader accepts to open: https, and only for the project's places that the
     * caller lists. The file lives outside the game, and a button that opens the browser must not
     * lead to just any place that appears there, neither by our mistake nor by a hijacked account.
     */
    private static String link(String url, String... hosts) {
        if (url == null || url.trim().isEmpty()) {
            return "";
        }
        try {
            URI u = URI.create(url.trim());
            String host = u.getHost() == null ? "" : u.getHost().toLowerCase(Locale.ROOT);
            if (host.startsWith("www.")) {
                host = host.substring(4);
            }
            for (String known : hosts) {
                if (host.equals(known) && "https".equalsIgnoreCase(u.getScheme())) {
                    return u.toString();
                }
            }
            Log.info(
                    "loader",
                    "address refused (https only, and only for the project's own hosts): " + url);
        } catch (RuntimeException e) {
            Log.info("loader", "unreadable address: " + url);
        }
        return "";
    }

    private static String text(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || !e.isJsonPrimitive() ? "" : e.getAsString();
    }
}
