/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.loader;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import dev.xavier.stein.bridge.core.SteinLog;
import dev.xavier.stein.loader.font.GameText;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;

/**
 * The Loader's own configuration file (config/steinloader.json): which mods are disabled, what the
 * player has already seen and which log topics are on. Reading also applies the log topics and the
 * HUD font option; saving writes everything back.
 */
final class LoaderConfig {

    /** Ids disabled in the configuration: what applies on the next start. */
    final Set<String> disabled = new TreeSet<>();

    /**
     * Id of the last Stein Labs notice the player closed: a warning that keeps coming back turns
     * into noise.
     */
    String newsRead = "";

    /** The Panel key already got the new default: it does not insist on later starts. */
    boolean panelKeySet;

    /** The version announced by Stein Labs that the player already closed. */
    String seenUpdate = "";

    private final File configFile;

    LoaderConfig(File configFile) {
        this.configFile = configFile;
    }

    void read() {
        // Measurement: -Dsteinloader.disable=id1,id2 disables mods for
        // this run only.
        String extra = System.getProperty("steinloader.disable");
        if (extra != null) {
            for (String id : extra.split(",")) {
                if (!id.isBlank()) {
                    disabled.add(id.trim());
                }
            }
        }
        if (!configFile.isFile()) {
            return;
        }
        try (Reader r =
                new InputStreamReader(new FileInputStream(configFile), StandardCharsets.UTF_8)) {
            JsonElement root = new JsonParser().parse(r);
            JsonObject o = root.isJsonObject() ? root.getAsJsonObject() : new JsonObject();
            JsonElement list = o.get("disabled");
            JsonElement key = o.get("teclaPainel");
            if (key != null && key.isJsonPrimitive()) {
                panelKeySet = key.getAsBoolean();
            }
            JsonElement seen = o.get("versaoAvisada");
            if (seen != null && seen.isJsonPrimitive()) {
                seenUpdate = seen.getAsString();
            }
            JsonElement read = o.get("noticia");
            if (read != null && read.isJsonPrimitive()) {
                newsRead = read.getAsString();
            }
            // Stein's font in the mods' HUD: starts enabled.
            JsonElement font = o.get("fonte");
            if (font != null && font.isJsonObject()) {
                JsonElement hud = font.getAsJsonObject().get("hud");
                if (hud != null && hud.isJsonPrimitive()) {
                    GameText.hud = hud.getAsBoolean();
                }
            }
            JsonElement log = o.get("registro");
            if (log != null && log.isJsonObject()) {
                JsonObject topics = log.getAsJsonObject();
                for (SteinLog.Topic t : SteinLog.Topic.values()) {
                    JsonElement e = topics.get(t.key);
                    if (e != null && e.isJsonPrimitive()) {
                        SteinLog.enable(t, e.getAsBoolean());
                    }
                }
            }
            if (list != null && list.isJsonArray()) {
                for (JsonElement e : list.getAsJsonArray()) {
                    disabled.add(e.getAsString());
                }
            }
        } catch (IOException | RuntimeException e) {
            System.out.println(
                    "[loader] "
                            + configFile
                            + " unreadable, continuing with all mods enabled: "
                            + e);
        }
    }

    void save() {
        JsonArray list = new JsonArray();
        for (String id : disabled) {
            list.add(new JsonPrimitive(id));
        }
        JsonObject root = new JsonObject();
        root.add("disabled", list);
        JsonObject topics = new JsonObject();
        for (SteinLog.Topic t : SteinLog.Topic.values()) {
            topics.addProperty(t.key, SteinLog.on(t));
        }
        root.add("registro", topics);
        JsonObject font = new JsonObject();
        font.addProperty("hud", GameText.hud);
        root.add("fonte", font);
        root.addProperty("noticia", newsRead);
        root.addProperty("teclaPainel", panelKeySet);
        root.addProperty("versaoAvisada", seenUpdate);
        configFile.getParentFile().mkdirs();
        try (Writer w =
                new OutputStreamWriter(new FileOutputStream(configFile), StandardCharsets.UTF_8)) {
            w.write(new GsonBuilder().setPrettyPrinting().create().toJson(root));
        } catch (IOException e) {
            System.out.println("[loader] could not save " + configFile + ": " + e);
        }
    }
}
