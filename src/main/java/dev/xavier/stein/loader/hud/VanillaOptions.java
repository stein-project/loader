/*
 * Copyright (c) 2026 Breno Xavier. All rights reserved.
 * Proprietary software: see the LICENSE file at the root of this repository.
 */

package dev.xavier.stein.loader.hud;

import com.google.gson.JsonObject;

import dev.xavier.stein.bridge.hooks.VanillaHud;

/**
 * Shape of the game's own HUD parts on the grid (config/steinloader-hud.json, "minecraft" section).
 */
final class VanillaOptions {

    /** Action bar: shadow on the text (the game has none). */
    static boolean actionShadow = false;

    /** Hotbar: 0 the game's (with frame), 1 items only. */
    static int hotbarStyle = 0;

    /**
     * Health, hunger and XP: 0 the game's bars, 1 reduced (icon and number); vertical or
     * horizontal, and each part can be turned on or off.
     */
    static int statusStyle = 0;

    static boolean statusVertical = false;
    static boolean statusHealth = true;
    static boolean statusFood = true;
    static boolean statusXp = true;

    /** Boss bar: 0 the game's, 1 thin, 2 text only; name on top; percentage; thin bar color. */
    static int bossStyle = 0;

    static boolean bossName = true;
    static boolean bossPercent = false;
    static String bossColor = "d";

    /**
     * Sidebar scoreboard: red numbers on the right; title; shadow on the text. The background is
     * the grid's standard one.
     */
    static boolean sideNumbers = true;

    static boolean sideTitle = true;
    static boolean sideShadow = false;

    private VanillaOptions() {}

    /** The game's shape (the defaults above are the game's) at the id part. */
    static void restore(String id) {
        switch (id) {
            case "minecraft.hotbar" -> hotbarStyle = 0;
            case "minecraft.status" -> {
                statusStyle = 0;
                statusVertical = false;
                statusHealth = true;
                statusFood = true;
                statusXp = true;
            }
            case "minecraft.actionbar" -> actionShadow = false;
            case "minecraft.bossbar" -> {
                bossStyle = 0;
                bossName = true;
                bossPercent = false;
                bossColor = "d";
            }
            case "minecraft.sidebar" -> {
                sideNumbers = true;
                sideTitle = true;
                sideShadow = false;
            }
            default -> {}
        }
    }

    static void load(JsonObject o) {
        hotbarStyle = HudLayout.clamp(HudPersistence.integer(o, "hotbarStyle", hotbarStyle), 0, 1);
        statusStyle = HudLayout.clamp(HudPersistence.integer(o, "statusStyle", statusStyle), 0, 1);
        statusVertical = HudPersistence.bool(o, "statusVertical", statusVertical);
        statusHealth = HudPersistence.bool(o, "statusHealth", statusHealth);
        statusFood = HudPersistence.bool(o, "statusFood", statusFood);
        statusXp = HudPersistence.bool(o, "statusXp", statusXp);
        actionShadow = HudPersistence.bool(o, "actionShadow", actionShadow);
        bossStyle = HudLayout.clamp(HudPersistence.integer(o, "bossStyle", bossStyle), 0, 2);
        bossName = HudPersistence.bool(o, "bossName", bossName);
        bossPercent = HudPersistence.bool(o, "bossPercent", bossPercent);
        bossColor = HudPersistence.string(o, "bossColor", bossColor);
        sideNumbers = HudPersistence.bool(o, "sideNumbers", sideNumbers);
        sideTitle = HudPersistence.bool(o, "sideTitle", sideTitle);
        sideShadow = HudPersistence.bool(o, "sideShadow", sideShadow);
        apply();
    }

    /**
     * What the bridge needs to know: each chat line's box (the game's) goes away; the background is
     * the grid's standard one.
     */
    static void apply() {
        VanillaHud.setChatBackground(false);
    }

    static void save(JsonObject o) {
        o.addProperty("hotbarStyle", hotbarStyle);
        o.addProperty("statusStyle", statusStyle);
        o.addProperty("statusVertical", statusVertical);
        o.addProperty("statusHealth", statusHealth);
        o.addProperty("statusFood", statusFood);
        o.addProperty("statusXp", statusXp);
        o.addProperty("actionShadow", actionShadow);
        o.addProperty("bossStyle", bossStyle);
        o.addProperty("bossName", bossName);
        o.addProperty("bossPercent", bossPercent);
        o.addProperty("bossColor", bossColor);
        o.addProperty("sideNumbers", sideNumbers);
        o.addProperty("sideTitle", sideTitle);
        o.addProperty("sideShadow", sideShadow);
    }
}
