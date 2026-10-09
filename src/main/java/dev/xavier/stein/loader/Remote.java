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

import net.minecraft.client.Minecraft;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What Stein Labs publishes and the Loader reads at startup: the server list and the Discord
 * invite.
 *
 * <p>A JSON in a public repository (github.com/x4vieer/stein-loader), and not inside the jar: that
 * way a new server or a changed invite takes effect on the next start of anyone who already has the
 * Loader installed, with no new version or new installer.
 *
 * <pre>
 * { "discord": "https://discord.gg/xxxxxxx",
 *   "servers": [ { "name": "Stein Labs", "ip": "play.example.com" } ] }
 * </pre>
 *
 * <p>The fetch runs on another thread with a short timeout — the game starts the same without
 * internet. What arrives is stored in {@code config/steinloader-remoto.json} and is what applies
 * next time, even before the fetch answers: the server stays in the list and the Discord button
 * stays there, offline.
 *
 * <p>{@code -Dsteinloader.remote=<url>} changes the address (empty fetches nothing) and {@code
 * -Dsteinloader.noRemote=true} turns everything off, including the stored copy.
 */
public final class Remote {

    /**
     * The project's public account and repository: <b>the only place</b> where they appear in the
     * code. {@code stein.json} comes from there and so do the versions (CI publishes both in the
     * same repository, {@code .github/workflows/release.yml}).
     *
     * <p>When changing accounts, change it here and publish a new Loader — and <b>leave the old
     * file up</b>, pointing to the new versions, as long as there are people with the earlier
     * Loader installed: it is through it that those people learn there is a new version.
     */
    private static final String REPO = "x4vieer/stein-loader";

    /** The project's public file. */
    private static final String URL =
            System.getProperty(
                    "steinloader.remote",
                    "https://raw.githubusercontent.com/" + REPO + "/main/stein.json");

    /**
     * Cap on what is accepted for reading: it is a handful of lines, and there is no reason to
     * download more than that.
     */
    private static final String DEFAULT_PORT_SUFFIX = ":25565";

    private static final int LIMIT = 64 * 1024;

    /** A server from the public list. */
    static final class Server {
        final String name;
        final String ip;

        Server(String name, String ip) {
            this.name = name;
            this.ip = ip;
        }
    }

    /**
     * A message from the project for the main menu.
     *
     * <p>Title and text can come in a single language (a string, which applies to everyone) or in
     * an object with one entry per game language ({@code {"en_US": "...", "pt_BR": "..."}}).
     * Whoever plays in another language reads the {@code en_US} one — English is what everyone can
     * reach —, and the languages are chosen at draw time, so that changing the game language
     * changes the message without restarting.
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

    /** The published version of the pack and where to download it. */
    public static final class Update {
        public final String version;
        public final String url;

        Update(String version, String url) {
            this.version = version;
            this.url = url;
        }
    }

    private static volatile Update latest;

    /** The pack version the installer left in this .minecraft, or empty (development build). */
    private static volatile String installed = "";

    private static volatile News news;

    private static volatile List<Server> servers = Collections.emptyList();

    /**
     * Whether there is something new to apply on the game thread (changing the server list is its
     * job).
     */
    private static volatile boolean fresh;

    private static File cache;

    private Remote() {}

    /** Reads the last copy and goes after the new one. Called at game start. */
    static void start(File gameDir) {
        if (Boolean.getBoolean("steinloader.noRemote")) {
            return;
        }
        cache = new File(gameDir, "config/steinloader-remoto.json");
        installed = installedVersion(gameDir);

        if (cache.isFile()) {
            try {
                parse(new String(Files.readAllBytes(cache.toPath()), StandardCharsets.UTF_8));
            } catch (Exception e) {
                System.out.println("[loader] stored Stein Labs copy unreadable: " + e);
            }
        }
        if (URL.isEmpty()) {
            return;
        }
        Thread t = new Thread(Remote::fetch, "stein-remote");
        t.setDaemon(true);
        t.start();
    }

    /**
     * The version the installer recorded in this .minecraft ({@code
     * versions/1.8.9-SteinLoader/stein-loader-install.json}). It is what gets compared with what
     * Stein Labs publishes: the release number is the pack's, not that of one of its parts.
     */
    private static String installedVersion(File gameDir) {
        File manifest = new File(gameDir, "versions/1.8.9-SteinLoader/stein-loader-install.json");
        if (!manifest.isFile()) {
            return "";
        }
        try {
            JsonElement root =
                    new JsonParser()
                            .parse(
                                    new String(
                                            Files.readAllBytes(manifest.toPath()),
                                            StandardCharsets.UTF_8));
            return root.isJsonObject() ? text(root.getAsJsonObject(), "version") : "";
        } catch (Exception e) {
            return "";
        }
    }

    /** On the game thread: applies what arrived. */
    static void tick() {
        if (!fresh) {
            return;
        }
        fresh = false;
        Servers.apply(servers);
    }

    /** Stein Labs' Discord invite, or empty — it decides whether the button appears. */
    public static String discord() {
        return discord;
    }

    /**
     * The update to announce, or null: there is only something to announce when this .minecraft was
     * installed by the installer (and its version can be known) and what Stein Labs published is
     * greater. Without the installer's file — development build, manual installation — nothing is
     * said: warning about a version without knowing which one is installed would be a guess.
     */
    public static Update update() {
        Update u = latest;
        if (u == null || installed.isEmpty() || Versions.compare(installed, u.version) >= 0) {
            return null;
        }
        return u;
    }

    /**
     * The installed version, for whoever wants to show it ("you have 0.1.7"). Empty without the
     * installer's file.
     */
    public static String installed() {
        return installed;
    }

    /** The current message, or null. The id is what says whether the player already closed it. */
    public static News news() {
        return news;
    }

    /**
     * Is the address a server published by Stein Labs? The multiplayer screen uses it to know where
     * the recommended part of the list ends and the player's own begins.
     */
    public static boolean recommends(String ip) {
        String h = host(ip);
        for (Server s : servers) {
            if (host(s.ip).equals(h)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The address as the game compares it: lowercase and without the default port ("home.com" and
     * "home.com:25565" are the same).
     */
    static String host(String ip) {
        String s = (ip == null ? "" : ip).trim().toLowerCase(Locale.ROOT);
        return s.endsWith(DEFAULT_PORT_SUFFIX)
                ? s.substring(0, s.length() - DEFAULT_PORT_SUFFIX.length())
                : s;
    }

    private static void fetch() {
        try {
            HttpClient http =
                    HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(5))
                            .followRedirects(HttpClient.Redirect.NORMAL)
                            .build();
            HttpRequest req =
                    HttpRequest.newBuilder(URI.create(URL))
                            .timeout(Duration.ofSeconds(8))
                            .header("User-Agent", "SteinLoader/" + Loader.VERSION)
                            .GET()
                            .build();
            HttpResponse<String> res =
                    http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() != 200) {
                System.out.println(
                        "[loader] Stein Labs answered "
                                + res.statusCode()
                                + ": keeping the stored copy");
                return;
            }
            String body = res.body();
            if (body.length() > LIMIT) {
                System.out.println(
                        "[loader] Stein Labs: response too large (" + body.length() + "), ignored");
                return;
            }
            parse(body);
            if (cache != null) {
                cache.getParentFile().mkdirs();
                Files.write(cache.toPath(), body.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception | LinkageError e) {
            // No network, DNS down, proxy: not the player's error, and the stored copy is already
            // in effect.
            System.out.println("[loader] Stein Labs unavailable (keeping the stored copy): " + e);
        }
    }

    private static void parse(String json) {
        JsonElement root = new JsonParser().parse(json);
        if (!root.isJsonObject()) {
            return;
        }
        JsonObject o = root.getAsJsonObject();
        String newDiscord = link(text(o, "discord"), "discord.gg", "discord.com", "discordapp.com");
        List<Server> list = new ArrayList<>();
        JsonElement raw = o.get("servers");
        if (raw != null && raw.isJsonArray()) {
            for (JsonElement e : raw.getAsJsonArray()) {
                if (!e.isJsonObject()) {
                    continue;
                }
                JsonObject s = e.getAsJsonObject();
                String ip = text(s, "ip").trim();
                String name = text(s, "name").trim();
                if (!ip.isEmpty()) {
                    list.add(new Server(name.isEmpty() ? ip : name, ip));
                }
            }
        }
        Update newLatest = null;
        JsonElement pub = o.get("latest");
        if (pub != null && pub.isJsonObject()) {
            JsonObject l = pub.getAsJsonObject();
            String version = text(l, "version").trim();
            String url = link(text(l, "url"), "github.com", "discord.gg", "discord.com");
            newLatest = version.isEmpty() || url.isEmpty() ? null : new Update(version, url);
        }
        News newNews = news(o.get("news"));

        // Everything parsed: only now publish, so bad JSON never leaves half-applied state.
        discord = newDiscord;
        servers = Collections.unmodifiableList(list);
        latest = newLatest;
        news = newNews;
        fresh = true;
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
     * lead to just any place that appears there — neither by our mistake nor by a hijacked account.
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
            System.out.println(
                    "[loader] address refused (https only, and only for the project's own hosts): "
                            + url);
        } catch (RuntimeException e) {
            System.out.println("[loader] unreadable address: " + url);
        }
        return "";
    }

    private static String text(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || !e.isJsonPrimitive() ? "" : e.getAsString();
    }
}
