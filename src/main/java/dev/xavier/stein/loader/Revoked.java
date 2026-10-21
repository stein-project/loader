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

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Mods that must not load, by the sha256 of the file, from {@code revoked.json} of the Stein
 * registry: a third-party mod found to be malicious or broken can be stopped on every machine
 * without a new Loader. The mod still shows in the list, refused, with the reason.
 *
 * <pre>
 * { "schema": 1, "mods": [ { "sha256": "...", "reason": "..." } ] }
 * </pre>
 *
 * The list in effect is the stored copy at startup; a newer one fetched during the session applies
 * on the next start (mods are already loaded by then).
 */
final class Revoked {

    /** The registry file. */
    static final String FILE = "revoked.json";

    /** sha256 (lowercase hex) to the reason. */
    private static volatile Map<String, String> bySha = Collections.emptyMap();

    private Revoked() {}

    /**
     * Publishes a copy of the file and says whether it was taken; one that does not parse, or has a
     * schema this version does not know, changes nothing.
     */
    static boolean load(String json) {
        if (json == null) {
            return false;
        }
        try {
            JsonElement root = new JsonParser().parse(json);
            if (!root.isJsonObject()) {
                return false;
            }
            JsonObject r = root.getAsJsonObject();
            if (r.has("schema") && r.get("schema").getAsInt() != 1) {
                Log.info("loader", "revoked.json: unknown schema, keeping what was there");
                return false;
            }
            Map<String, String> out = new HashMap<>();
            JsonElement list = r.get("mods");
            if (list != null && list.isJsonArray()) {
                for (JsonElement e : list.getAsJsonArray()) {
                    if (!e.isJsonObject()) {
                        continue;
                    }
                    JsonObject o = e.getAsJsonObject();
                    String sha = o.has("sha256") ? o.get("sha256").getAsString().trim() : "";
                    String reason = o.has("reason") ? o.get("reason").getAsString().trim() : "";
                    if (sha.length() == 64) {
                        out.put(sha.toLowerCase(Locale.ROOT), reason);
                    }
                }
            }
            bySha = Collections.unmodifiableMap(out);
            return true;
        } catch (RuntimeException e) {
            Log.info("loader", "revoked.json unreadable, keeping what was there: " + e);
            return false;
        }
    }

    /** The reason this file is revoked, or null when it is not (or with an empty list). */
    static String reason(File file) {
        Map<String, String> map = bySha;
        if (map.isEmpty() || file == null || !file.isFile()) {
            return null;
        }
        try (InputStream in = Files.newInputStream(file.toPath())) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[1 << 16];
            for (int n; (n = in.read(buf)) > 0; ) {
                md.update(buf, 0, n);
            }
            StringBuilder hex = new StringBuilder(64);
            for (byte b : md.digest()) {
                hex.append(String.format(Locale.ROOT, "%02x", b));
            }
            return map.get(hex.toString());
        } catch (Exception e) {
            return null;
        }
    }
}
