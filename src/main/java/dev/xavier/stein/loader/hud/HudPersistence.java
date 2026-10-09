/*
 * Copyright (c) 2026 Breno Xavier. All rights reserved.
 * Proprietary software: see the LICENSE file at the root of this repository.
 */

package dev.xavier.stein.loader.hud;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.xavier.stein.bridge.core.Log;
import dev.xavier.stein.bridge.hooks.VanillaHud;
import dev.xavier.stein.loader.api.HudPlacement;
import dev.xavier.stein.loader.gui.Ui;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * The HUD layout file (config/steinloader-hud.json): reads it at start, writes it when something
 * changed (a second after the last change), and the small JSON readers shared with the options. The
 * keys and the file are the player's saved layout: they must not change.
 */
final class HudPersistence {

    private static File file;

    /** Change not yet saved (drag, scale, options): the save happens by itself a second later. */
    private static boolean dirty;

    private static long changedAt;

    private HudPersistence() {}

    /**
     * Marks that there is a change to save; the autosave does the saving, at the end of the tick.
     */
    static void touch() {
        dirty = true;
        changedAt = System.nanoTime();
    }

    /**
     * Saves by itself a second after the last change: the editor only saved on close, and what the
     * player had dragged was lost if the game closed with it open.
     *
     * <p>It also only saved when nothing was on top of anything, waiting for the player to sort it
     * out — but whoever had a full screen lived in that state (the elements are measured by the
     * sample, larger than what shows up in game) and so never saved anything. Now it always saves:
     * the red in the editor warns, and warning is enough.
     */
    static void autosave() {
        if (!dirty || System.nanoTime() - changedAt < 1_000_000_000L) {
            return;
        }
        save();
    }

    /**
     * Stein's default layout, for whoever does not have their own yet: the game's parts and the
     * official mods' elements in the chosen place and format. The elements that are not in it
     * follow the usual rule (the game's enabled, the mods' disabled).
     */
    private static final String DEFAULT_LAYOUT = "/assets/steinloader/default-hud.json";

    /** Reads the saved layout (or Stein's default one) from {@code layoutFile}. */
    static void open(File layoutFile) {
        file = layoutFile;
        load();
    }

    private static void load() {
        if (!file.isFile()) {
            InputStream in = HudPersistence.class.getResourceAsStream(DEFAULT_LAYOUT);
            if (in != null) {
                try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    read(r);
                    Log.info(
                            "loader",
                            "HUD: default layout (" + HudRegistry.placements.size() + " elements)");
                } catch (Exception e) {
                    Log.info("loader", "HUD: default layout unreadable: " + e);
                }
            }
            return;
        }
        try (Reader r = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            read(r);
        } catch (Exception e) {
            Log.info("loader", "HUD: " + file + " unreadable, using defaults: " + e);
        }
    }

    private static void read(Reader r) {
        JsonObject o = new JsonParser().parse(r).getAsJsonObject();
        HudRegistry.grid = HudLayout.clamp(integer(o, "grid", HudRegistry.grid), 0, 32);
        HudRegistry.snap = bool(o, "snap", HudRegistry.snap);
        if (o.has("elements") && o.get("elements").isJsonObject()) {
            for (Map.Entry<String, JsonElement> en : o.getAsJsonObject("elements").entrySet()) {
                JsonObject e = en.getValue().getAsJsonObject();
                Placement p = new Placement();
                p.position(
                        new HudPlacement(
                                integer(e, "anchorX", 0),
                                integer(e, "anchorY", 0),
                                integer(e, "x", 0),
                                integer(e, "y", 0),
                                e.has("scale") ? e.get("scale").getAsFloat() : 1.0F,
                                true,
                                false));
                p.enabled = bool(e, "enabled", true);
                p.showInChat = bool(e, "showInChat", false);
                p.background = backgroundOf(e.get("background"));
                p.gameFont = bool(e, "gameFont", false);
                if ("minecraft.chat".equals(en.getKey())) {
                    VanillaHud.setChatGameFont(p.gameFont);
                }
                HudRegistry.placements.put(en.getKey(), p);
            }
        }
        if (o.has("minecraft") && o.get("minecraft").isJsonObject()) {
            JsonObject mcOptions = o.getAsJsonObject("minecraft");
            VanillaOptions.load(mcOptions);
            // The own backgrounds of older versions (the action bar box, the sidebar and chat ones)
            // became the standard background. These keys are no longer written: the next save drops
            // them.
            if (bool(mcOptions, "actionBackground", false)) {
                legacyBackground("minecraft.actionbar", 0.4F);
            }
            if (mcOptions.has("sideBackground") && integer(mcOptions, "sideBackground", 2) != 2) {
                legacyBackground("minecraft.sidebar", 0.4F);
            }
            if (bool(mcOptions, "chatBackground", false)) {
                legacyBackground("minecraft.chat", 0.4F);
            }
        }
    }

    /**
     * The saved opacity (0 to 1); the tone names of a test version (stein, dark, light, none)
     * become an opacity, and anything else means the default background (-1).
     */
    private static float backgroundOf(JsonElement v) {
        if (v == null || !v.isJsonPrimitive()) {
            return -1.0F;
        }
        if (v.getAsJsonPrimitive().isNumber()) {
            return Ui.clamp01(v.getAsFloat());
        }
        return switch (v.getAsString()) {
            case "stein" -> 0.8F;
            case "dark" -> 0.5F;
            case "light" -> 0.3F;
            case "none" -> 0.0F;
            default -> -1.0F;
        };
    }

    /** Gives an element that still has the default background the opacity of its old one. */
    private static void legacyBackground(String id, float opacity) {
        Placement p = HudRegistry.placements.get(id);
        if (p != null && p.background < 0.0F) {
            p.background = opacity;
        }
    }

    static void save() {
        dirty = false;
        if (file == null) {
            return;
        }
        JsonObject o = new JsonObject();
        o.addProperty("grid", HudRegistry.grid);
        o.addProperty("snap", HudRegistry.snap);
        JsonObject els = new JsonObject();
        for (Map.Entry<String, Placement> en : HudRegistry.placements.entrySet()) {
            Placement p = en.getValue();
            JsonObject e = new JsonObject();
            e.addProperty("enabled", p.enabled);
            e.addProperty("showInChat", p.showInChat);
            e.addProperty("anchorX", p.anchorX);
            e.addProperty("anchorY", p.anchorY);
            e.addProperty("x", p.x);
            e.addProperty("y", p.y);
            e.addProperty("scale", p.scale);
            if (p.gameFont) {
                e.addProperty("gameFont", true);
            }
            if (p.background >= 0.0F) {
                e.addProperty("background", Math.round(p.background * 100.0F) / 100.0F);
            }
            els.add(en.getKey(), e);
        }
        o.add("elements", els);
        JsonObject mcOptions = new JsonObject();
        VanillaOptions.save(mcOptions);
        o.add("minecraft", mcOptions);
        File dir = file.getParentFile();
        if (dir != null) {
            dir.mkdirs();
        }
        try (Writer w =
                new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(o, w);
        } catch (Exception e) {
            Log.info("loader", "HUD: could not write " + file + ": " + e);
        }
    }

    static boolean bool(JsonObject o, String k, boolean def) {
        return o.has(k) ? o.get(k).getAsBoolean() : def;
    }

    static int integer(JsonObject o, String k, int def) {
        return o.has(k) ? o.get(k).getAsInt() : def;
    }

    static String string(JsonObject o, String k, String def) {
        return o.has(k) ? o.get(k).getAsString() : def;
    }
}
