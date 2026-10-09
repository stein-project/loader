/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.loader;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.xavier.stein.bridge.hooks.GameHooks;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Session;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The game account behind api.Account. The launcher's session is stored at startup, the swap goes
 * through the Bridge (the field is final), and the check with Microsoft is done here: the token
 * never leaves this class — not to a mod, not to a log, not to a file.
 */
final class Accounts {

    // The indices of api.Account.Kind.
    static final int OFFLINE = 0;
    static final int CHECKING = 1;
    static final int MICROSOFT = 2;
    static final int NOT_MICROSOFT = 3;
    static final int UNVERIFIED = 4;
    private static final int MIN_TOKEN_LENGTH = 20;
    private static final long RETRY_AFTER_NANOS = 30_000_000_000L;
    private static final int TIMEOUT_MS = 8000;

    private static Session launcher;

    /** Answer per token; the token is only a key in here. */
    private static final Map<String, Integer> VERDICTS = new ConcurrentHashMap<>();

    private static final Map<String, Long> FAILED_AT = new ConcurrentHashMap<>();

    private Accounts() {}

    /** At game start: the session the launcher handed over. */
    static void start() {
        if (launcher == null) {
            launcher = Minecraft.getMinecraft().getSession();
        }
    }

    private static Session current() {
        return Minecraft.getMinecraft().getSession();
    }

    static String name() {
        Session s = current();
        return s == null ? "" : s.getUsername();
    }

    static String launcherName() {
        return launcher == null ? name() : launcher.getUsername();
    }

    static boolean isLauncher() {
        return launcher == null || current() == launcher;
    }

    static void useOffline(String nick) {
        start();
        String id =
                UUID.nameUUIDFromBytes(("OfflinePlayer:" + nick).getBytes(StandardCharsets.UTF_8))
                        .toString()
                        .replace("-", "");
        set(new Session(nick, id, "0", "legacy"));
    }

    static void restore() {
        if (launcher != null) {
            set(launcher);
        }
    }

    private static void set(Session s) {
        try {
            GameHooks.setSession(s);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("could not swap the session", e);
        }
    }

    /**
     * The kind of the current session. It only says Microsoft after checking: having a token is not
     * enough (a launcher without an account sends an arbitrary one). The check asks Mojang whose
     * token it is and requires that account to be this session's.
     */
    static int kind() {
        return kind(current());
    }

    /** The kind of the launcher's account, whatever the current session is. */
    static int launcherKind() {
        return kind(launcher == null ? current() : launcher);
    }

    private static int kind(Session s) {
        String t = s == null ? null : s.getToken();
        if (t == null || t.length() < MIN_TOKEN_LENGTH || "0".equals(t) || "-".equals(t)) {
            return OFFLINE;
        }
        Long failed = FAILED_AT.get(t);
        if (failed != null && System.nanoTime() - failed > RETRY_AFTER_NANOS) {
            FAILED_AT.remove(t);
            VERDICTS.remove(t);
        }
        return VERDICTS.computeIfAbsent(
                t,
                k -> {
                    verify(s);
                    return CHECKING;
                });
    }

    private static void verify(Session s) {
        String token = s.getToken();
        String id = s.getPlayerID() == null ? "" : s.getPlayerID().replace("-", "");
        String name = s.getUsername();
        Thread t =
                new Thread(
                        () -> {
                            int v = fetchVerdict(token, id, name);
                            if (v == UNVERIFIED) {
                                FAILED_AT.put(token, System.nanoTime());
                            }
                            VERDICTS.put(token, v);
                            log(name, v);
                        },
                        "stein-account-check");
        t.setDaemon(true);
        t.start();
    }

    /** Asks Mojang whose token this is and returns MICROSOFT, NOT_MICROSOFT or UNVERIFIED. */
    private static int fetchVerdict(String token, String id, String name) {
        try {
            HttpURLConnection c =
                    (HttpURLConnection)
                            URI.create("https://api.minecraftservices.com/minecraft/profile")
                                    .toURL()
                                    .openConnection();
            c.setConnectTimeout(TIMEOUT_MS);
            c.setReadTimeout(TIMEOUT_MS);
            c.setRequestProperty("Authorization", "Bearer " + token);
            int code = c.getResponseCode();
            int v;
            if (code == 200) {
                try (Reader r = new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8)) {
                    JsonObject o = new JsonParser().parse(r).getAsJsonObject();
                    String gotId = o.has("id") ? o.get("id").getAsString() : "";
                    String gotName = o.has("name") ? o.get("name").getAsString() : "";
                    v =
                            gotId.equalsIgnoreCase(id)
                                            || id.isEmpty() && gotName.equalsIgnoreCase(name)
                                    ? MICROSOFT
                                    : NOT_MICROSOFT;
                }
            } else if (code == 401 || code == 403 || code == 404) {
                v = NOT_MICROSOFT;
            } else {
                v = UNVERIFIED;
            }
            c.disconnect();
            return v;
        } catch (IOException | RuntimeException e) {
            return UNVERIFIED;
        }
    }

    private static void log(String name, int verdict) {
        System.out.println(
                "[loader] account "
                        + name
                        + ": "
                        + (verdict == MICROSOFT
                                ? "Microsoft"
                                : verdict == NOT_MICROSOFT ? "not Microsoft" : "could not verify"));
    }
}
