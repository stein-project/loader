/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.loader;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * What a server asks to turn off, on the {@code SteinLoader} plugin channel: the same format byte
 * and length-prefixed string as {@link ServerReport}, the other way round.
 *
 * <pre>
 * {"disable":[{"mod":"steinhud","feature":"minimap"},{"mod":"steincombat"}]}
 * </pre>
 *
 * An entry without {@code feature} is the whole mod. Every packet is the complete list, so an empty
 * one gives everything back. Anything that is not a valid entry is ignored without dropping the
 * rest; only JSON that does not parse is refused, leaving what was in effect.
 */
final class ServerRequest {

    /** Nothing turned off. */
    static final ServerRequest NONE =
            new ServerRequest(Collections.emptySet(), Collections.emptyMap());

    /** Far more than any mod list; a server that sends more gets the first ones. */
    private static final int MAX_ENTRIES = 256;

    /** Mods turned off whole, by id. */
    final Set<String> mods;

    /** Mod id -> the features turned off in it. */
    final Map<String, Set<String>> features;

    private ServerRequest(Set<String> mods, Map<String, Set<String>> features) {
        this.mods = mods;
        this.features = features;
    }

    /** The request in the JSON, or null when it is not a JSON object. */
    static ServerRequest parse(String json) {
        JsonElement root;
        try {
            root = new JsonParser().parse(json);
        } catch (RuntimeException e) {
            return null;
        }
        if (root == null || !root.isJsonObject()) {
            return null;
        }
        JsonElement list = root.getAsJsonObject().get("disable");
        if (list == null || !list.isJsonArray()) {
            return NONE;
        }
        JsonArray entries = list.getAsJsonArray();
        Set<String> mods = new LinkedHashSet<>();
        Map<String, Set<String>> features = new LinkedHashMap<>();
        for (int i = 0; i < Math.min(entries.size(), MAX_ENTRIES); i++) {
            JsonElement e = entries.get(i);
            if (!e.isJsonObject()) {
                continue;
            }
            JsonObject o = e.getAsJsonObject();
            String mod = text(o, "mod");
            String feature = text(o, "feature");
            if (mod.isEmpty()) {
                continue;
            }
            if (feature.isEmpty()) {
                mods.add(mod);
            } else {
                features.computeIfAbsent(mod, k -> new LinkedHashSet<>()).add(feature);
            }
        }
        return new ServerRequest(mods, features);
    }

    private static String text(JsonObject o, String key) {
        JsonElement v = o.get(key);
        return v != null && v.isJsonPrimitive() ? v.getAsString().trim() : "";
    }
}
