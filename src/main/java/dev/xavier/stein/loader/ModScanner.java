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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The mods in the mods folder of the game directory.
 *
 * <p>Each mod is a {@code .steinmod} file — a jar with another extension: old Forge tries to load
 * every {@code .jar} in the mods folder and used to break on ours. It describes the mod in a {@code
 * steinmod.json} at the root:
 *
 * <pre>
 * { "id": "steinhud", "name": "HUD", "version": "1.0.0",
 *   "author": "X4vieer", "description": "...",
 *   "entry": "dev.xavier.stein.hud.SteinHud", "loader": "0.1.3" }
 * </pre>
 *
 * {@code entry} is the class (or list of classes) that implements {@code
 * dev.xavier.stein.loader.api.SteinMod}; {@code loader} is the <b>minimum Stein Loader version</b>
 * — the same one that shows in the mod list, not a separate API level (and, from {@link
 * #RUNTIME_PATCH} on, it also says that the mod comes without the Bridge rules, which the Loader
 * applies); {@code author} also accepts the list {@code authors}. {@code access} (optional) lists
 * the protected game methods that the mod calls from outside their hierarchy
 * ("net/minecraft/.../Class.methodName"): it only matters at compile time (the SDK's OpenJar opens
 * those in the compile jar); in the game the Loader decides by itself, by the JVM rule, which
 * access to bind (ModPatch). Private fields and methods need no list. A file without a manifest,
 * but with META-INF/services/dev.xavier.stein.loader.api.SteinMod, is also valid (the id comes from
 * the file name).
 *
 * <p>Nothing disappears silently: what does not get in comes back as a rejected {@link ModInfo},
 * with the reason, for the mod list to show. Only what is ours is looked at — {@code .steinmod},
 * and {@code .jar} only if it has {@code steinmod.json} inside. A Forge mod or one from another
 * loader is none of our business and does not appear in the list.
 */
final class ModScanner {

    /** Extension of the mods. */
    static final String EXT = ".steinmod";

    /**
     * Since this Loader version, the mod arrives as it came out of javac and the Loader adapts it
     * to the Bridge ({@link ModPatch}). Whoever asks for an older Loader (or none) was adapted at
     * build time and enters as is: applying the rules again would put the batch twice before each
     * GL call.
     */
    static final String RUNTIME_PATCH = "0.1.14";

    private static final String SERVICES = "META-INF/services/dev.xavier.stein.loader.api.SteinMod";

    private ModScanner() {}

    /**
     * Reads the folder.
     *
     * @param dir the mods folder of the game directory
     * @param loaderVersion this Loader's version, compared with the one each mod asks for
     */
    static List<ModInfo> scan(File dir, String loaderVersion) {
        File[] files =
                dir.listFiles(
                        (d, n) -> {
                            String s = n.toLowerCase(Locale.ROOT);
                            return s.endsWith(EXT) || s.endsWith(".jar");
                        });
        if (files == null) {
            return Collections.emptyList();
        }
        Arrays.sort(files); // stable order between starts
        List<ModInfo> out = new ArrayList<>();
        for (File f : files) {
            boolean legacy = f.getName().toLowerCase(Locale.ROOT).endsWith(".jar");
            try (ZipFile z = new ZipFile(f)) {
                if (legacy) {
                    // Stein Loader mod still as a .jar (up to 0.0.9). A jar from another loader
                    // passes by.
                    if (z.getEntry("steinmod.json") != null) {
                        out.add(ModInfo.rejected(f, base(f), "steinloader.reject.jar", EXT));
                    }
                    continue;
                }
                out.add(read(f, z, loaderVersion));
            } catch (IOException | RuntimeException e) {
                if (!legacy) {
                    out.add(
                            ModInfo.rejected(
                                    f, base(f), "steinloader.reject.unreadable", e.toString()));
                }
            }
        }
        for (ModInfo m : out) {
            if (m.state() == ModInfo.State.REJECTED) {
                System.out.println(
                        "[loader] " + m.file.getName() + " was not accepted: " + m.reason());
            }
        }
        return out;
    }

    private static String base(File f) {
        String n = f.getName();
        int dot = n.lastIndexOf('.');
        return dot > 0 ? n.substring(0, dot) : n;
    }

    private static ModInfo read(File f, ZipFile z, String loaderVersion) throws IOException {
        boolean assets = z.stream().anyMatch(e -> e.getName().startsWith("assets/"));
        String base = base(f);
        ZipEntry manifest = z.getEntry("steinmod.json");
        if (manifest != null) {
            JsonObject o;
            try (Reader r =
                    new InputStreamReader(z.getInputStream(manifest), StandardCharsets.UTF_8)) {
                o = new JsonParser().parse(r).getAsJsonObject();
            }
            String id = text(o, "id", base);
            String name = text(o, "name", id);
            String version = text(o, "version", "?");
            String description = text(o, "description", "");
            ModInfo no = refuse(f, o, id, loaderVersion);
            if (no != null) {
                return no.describe(name, version, author(o), description);
            }
            ModInfo m =
                    new ModInfo(
                            id,
                            name,
                            version,
                            author(o),
                            description,
                            f,
                            list(o.get("entry")),
                            assets);
            String needs = text(o, "loader", "").trim();
            m.patch = needs.indexOf('.') > 0 && Versions.atLeast(needs, RUNTIME_PATCH);
            versions(o.get("depends"), m.depends);
            versions(o.get("optional"), m.optional);
            m.announce = !o.has("announce") || o.get("announce").getAsBoolean();
            return m;
        }
        ZipEntry services = z.getEntry(SERVICES);
        if (services != null) {
            List<String> entries = new ArrayList<>();
            try (BufferedReader r =
                    new BufferedReader(
                            new InputStreamReader(
                                    z.getInputStream(services), StandardCharsets.UTF_8))) {
                for (String line; (line = r.readLine()) != null; ) {
                    String s = line.replaceAll("#.*", "").trim();
                    if (!s.isEmpty()) {
                        entries.add(s);
                    }
                }
            }
            return new ModInfo(base, base, "?", "", "", f, entries, assets);
        }
        return ModInfo.rejected(f, base, "steinloader.reject.manifest");
    }

    /**
     * Does the mod fit this Loader? Returns null if it fits, or the already-rejected mod, with the
     * reason.
     *
     * <p>{@code "loader"} is the minimum Stein Loader version ("0.1.3"); empty means any.
     */
    private static ModInfo refuse(File f, JsonObject o, String id, String loaderVersion) {
        String needs = text(o, "loader", "").trim();
        if (needs.isEmpty()) {
            return null;
        }
        return Versions.atLeast(loaderVersion, needs)
                ? null
                : ModInfo.rejected(f, id, "steinloader.reject.loader", needs, loaderVersion);
    }

    private static String author(JsonObject o) {
        if (o.has("author")) {
            return text(o, "author", "");
        }
        return String.join(", ", list(o.get("authors")));
    }

    /** {"id": "minimum version", ...}; a list of ids is also valid (any version). */
    private static void versions(JsonElement e, Map<String, String> out) {
        if (e != null && e.isJsonObject()) {
            for (Map.Entry<String, JsonElement> d : e.getAsJsonObject().entrySet()) {
                out.put(d.getKey(), d.getValue().isJsonNull() ? "0" : d.getValue().getAsString());
            }
        } else {
            for (String id : list(e)) {
                out.put(id, "0");
            }
        }
    }

    /** A string or a list of strings. */
    private static List<String> list(JsonElement e) {
        List<String> out = new ArrayList<>();
        if (e != null && e.isJsonArray()) {
            for (JsonElement x : e.getAsJsonArray()) {
                out.add(x.getAsString());
            }
        } else if (e != null && !e.isJsonNull()) {
            out.add(e.getAsString());
        }
        return out;
    }

    private static String text(JsonObject o, String key, String fallback) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? fallback : e.getAsString();
    }
}
