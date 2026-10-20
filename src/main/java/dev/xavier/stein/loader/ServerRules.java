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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Features a server does not allow, from {@code servers.json} of the Stein registry: while the
 * player is on a server listed there, the mods leave those features off ({@code Server.allows}).
 * Nothing the player chose is changed or saved: leaving the server brings everything back.
 *
 * <pre>
 * { "schema": 1,
 *   "servers": [ { "name": "Hypixel", "hosts": ["hypixel.net"],
 *                  "disable": ["minimap", "targetInfo", "playerDistance"] } ] }
 * </pre>
 *
 * A host matches the address itself and every subdomain ({@code hypixel.net} covers {@code
 * mc.hypixel.net}). The mods ask on every frame, so the answer is computed once per address.
 */
final class ServerRules {

    /** The registry file. */
    static final String FILE = "servers.json";

    /** One server's rule. */
    private static final class Rule {
        final String name;
        final List<String> hosts;
        final Set<String> disable;

        Rule(String name, List<String> hosts, Set<String> disable) {
            this.name = name;
            this.hosts = hosts;
            this.disable = disable;
        }
    }

    private static volatile List<Rule> rules = Collections.emptyList();

    /** The address the answer below is for, and the rule that applies to it (or null). */
    private static String lastAddress = "";

    private static Rule lastRule;

    private ServerRules() {}

    /** Publishes a copy of the file; one that does not parse changes nothing. */
    static void load(String json) {
        if (json == null) {
            return;
        }
        try {
            JsonElement root = new JsonParser().parse(json);
            if (!root.isJsonObject()) {
                return;
            }
            JsonObject o = root.getAsJsonObject();
            if (o.has("schema") && o.get("schema").getAsInt() != 1) {
                Log.info("loader", "servers.json: unknown schema, keeping what was there");
                return;
            }
            List<Rule> out = new ArrayList<>();
            JsonElement list = o.get("servers");
            if (list != null && list.isJsonArray()) {
                for (JsonElement e : list.getAsJsonArray()) {
                    Rule r = rule(e);
                    if (r != null) {
                        out.add(r);
                    }
                }
            }
            rules = Collections.unmodifiableList(out);
            synchronized (ServerRules.class) {
                lastAddress = null; // the next question recomputes with the new rules
            }
        } catch (RuntimeException e) {
            Log.info("loader", "servers.json unreadable, keeping what was there: " + e);
        }
    }

    private static Rule rule(JsonElement e) {
        if (!e.isJsonObject()) {
            return null;
        }
        JsonObject o = e.getAsJsonObject();
        String name = o.has("name") ? o.get("name").getAsString().trim() : "";
        List<String> hosts = new ArrayList<>();
        Set<String> disable = new HashSet<>();
        if (o.has("hosts") && o.get("hosts").isJsonArray()) {
            for (JsonElement h : o.getAsJsonArray("hosts")) {
                String s = h.getAsString().trim().toLowerCase(Locale.ROOT);
                if (!s.isEmpty()) {
                    hosts.add(s);
                }
            }
        }
        if (o.has("disable") && o.get("disable").isJsonArray()) {
            for (JsonElement d : o.getAsJsonArray("disable")) {
                disable.add(d.getAsString().trim());
            }
        }
        return name.isEmpty() || hosts.isEmpty() ? null : new Rule(name, hosts, disable);
    }

    /** Is the feature allowed on the server the player is on? Always true outside a server. */
    static boolean allows(String address, String feature) {
        Rule r = ruleFor(address);
        return r == null || !r.disable.contains(feature);
    }

    /** The name of the server that turns the feature off here ("Hypixel"), or "". */
    static String restrictedBy(String address, String feature) {
        Rule r = ruleFor(address);
        return r != null && r.disable.contains(feature) ? r.name : "";
    }

    private static synchronized Rule ruleFor(String address) {
        if (address == null || address.isEmpty()) {
            return null;
        }
        if (address.equals(lastAddress)) {
            return lastRule;
        }
        String host = host(address);
        Rule found = null;
        for (Rule r : rules) {
            for (String h : r.hosts) {
                if (host.equals(h) || host.endsWith("." + h)) {
                    found = r;
                    break;
                }
            }
            if (found != null) {
                break;
            }
        }
        lastAddress = address;
        lastRule = found;
        if (found != null) {
            Log.info("loader", "server rules: " + found.name + " turns off " + found.disable);
        }
        return found;
    }

    /** The host of "host" or "host:port", lowercase and without the trailing dot. */
    static String host(String address) {
        String a = address.trim().toLowerCase(Locale.ROOT);
        int colon = a.lastIndexOf(':');
        if (colon > 0 && a.indexOf(':') == colon) {
            a = a.substring(0, colon);
        }
        return a.endsWith(".") ? a.substring(0, a.length() - 1) : a;
    }
}
