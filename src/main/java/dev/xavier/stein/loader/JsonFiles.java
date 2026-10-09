/*
 * Copyright (c) 2026 Breno Xavier. All rights reserved.
 * Proprietary software: see the LICENSE file at the root of this repository.
 */

package dev.xavier.stein.loader;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.xavier.stein.bridge.core.SteinLog;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.TreeMap;

/**
 * The JSON behind api.ModConfig and api.Secrets: the API does not mention Gson (a game library,
 * which may change), so reading and writing live here.
 */
final class JsonFiles {

    private static final Gson GSON =
            new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private JsonFiles() {}

    /**
     * Reads field by field on top of {@code fresh} (the defaults): what is missing stays, and what
     * cannot be read goes back to the default for that field only. A file that is not JSON goes to
     * {@code .bad} and the defaults remain.
     */
    static <T> T readConfig(File f, Class<T> type, T fresh, String owner) {
        if (!f.isFile()) {
            return fresh;
        }
        JsonObject o;
        try (Reader r = Files.newBufferedReader(f.toPath(), StandardCharsets.UTF_8)) {
            o = new JsonParser().parse(r).getAsJsonObject();
        } catch (Exception e) {
            File bad = new File(f.getPath() + ".bad");
            bad.delete();
            f.renameTo(bad);
            SteinLog.note(
                    owner,
                    "warning: "
                            + f.getName()
                            + " unreadable ("
                            + e.getMessage()
                            + "): kept as "
                            + bad.getName()
                            + ", starting from defaults");
            return fresh;
        }
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                int mod = field.getModifiers();
                if (Modifier.isStatic(mod)
                        || Modifier.isTransient(mod)
                        || !o.has(field.getName())) {
                    continue;
                }
                JsonElement e = o.get(field.getName());
                try {
                    field.setAccessible(true);
                    Object v = GSON.fromJson(e, field.getGenericType());
                    if (v != null || !field.getType().isPrimitive()) {
                        field.set(fresh, v);
                    }
                } catch (Exception bad) {
                    SteinLog.note(
                            owner,
                            "warning: "
                                    + f.getName()
                                    + ": \""
                                    + field.getName()
                                    + "\" unreadable, keeping the default");
                }
            }
        }
        return fresh;
    }

    /**
     * Writes to a temporary file and swaps in one step: a crash midway leaves no half-written JSON.
     */
    static void write(File f, Object value, Class<?> type) throws IOException {
        f.getParentFile().mkdirs();
        File tmp = new File(f.getPath() + ".tmp");
        try (Writer w = Files.newBufferedWriter(tmp.toPath(), StandardCharsets.UTF_8)) {
            GSON.toJson(value, type, w);
        }
        try {
            Files.move(
                    tmp.toPath(),
                    f.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicFailed) {
            Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static Map<String, String> readStrings(File f) {
        Map<String, String> out = new TreeMap<>();
        if (!f.isFile()) {
            return out;
        }
        try (Reader r = Files.newBufferedReader(f.toPath(), StandardCharsets.UTF_8)) {
            JsonObject o = new JsonParser().parse(r).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                out.put(e.getKey(), e.getValue().getAsString());
            }
        } catch (Exception e) {
            SteinLog.note("loader", "warning: " + f.getName() + " unreadable");
        }
        return out;
    }

    static void writeStrings(File f, Map<String, String> all) throws IOException {
        JsonObject o = new JsonObject();
        all.forEach(o::addProperty);
        write(f, o, JsonObject.class);
    }
}
