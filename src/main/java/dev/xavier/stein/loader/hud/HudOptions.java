/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.loader.hud;

import dev.xavier.stein.bridge.hooks.VanillaHud;
import dev.xavier.stein.loader.api.HudElement;
import dev.xavier.stein.loader.api.HudPlacement;
import dev.xavier.stein.loader.api.Option;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.resources.I18n;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * An element's option list in the Panel and the editor (the common options plus the element's own)
 * and the restore of an element, or of all of them, to the defaults.
 */
final class HudOptions {

    private HudOptions() {}

    /**
     * An element's options: the common ones (with chat open, scale, factory position) and the
     * element's own. Used by the Panel and by the editor's options screen, so they are the same in
     * both places.
     */
    static List<Option> options(HudElement e) {
        List<Option> out = new ArrayList<>();
        Placement p = HudRegistry.placement(e);
        out.add(
                Option.toggle(
                        tr("steinloader.hud.showInChat"),
                        () -> p.showInChat,
                        v -> {
                            p.showInChat = v;
                            HudPersistence.touch();
                        }));
        out.add(
                Option.slider(
                        tr("steinloader.hud.scale"),
                        0.5,
                        3.0,
                        0.05,
                        () -> p.scale,
                        v -> {
                            float old = p.scale;
                            p.scale = (float) v;
                            ScaledResolution r = new ScaledResolution(Minecraft.getMinecraft());
                            if (HudCollision.collides(e, r.getScaledWidth(), r.getScaledHeight())) {
                                p.scale = old; // it would touch another element
                            }
                            HudPersistence.touch();
                        },
                        HudOptions::scaleName));
        out.add(
                Option.cycle(
                        tr("steinloader.hud.font"),
                        new String[] {
                            tr("steinloader.hud.font.stein"), tr("steinloader.hud.font.game")
                        },
                        () -> p.gameFont ? 1 : 0,
                        i -> {
                            p.gameFont = i == 1;
                            fontChanged(e, p);
                            ScaledResolution r = new ScaledResolution(Minecraft.getMinecraft());
                            // The other font has a different width: if it touches another element,
                            // it goes to the
                            // nearest free place.
                            if (HudCollision.collides(e, r.getScaledWidth(), r.getScaledHeight())) {
                                HudCollision.findFree(e, r.getScaledWidth(), r.getScaledHeight());
                            }
                            HudPersistence.touch();
                        }));
        // Stein's default background, the same on every element that accepts a background: the
        // player picks only the transparency
        // (100% = no background).
        if (e.hasBackground()) {
            out.add(
                    Option.slider(
                            tr("steinloader.hud.transparency"),
                            0,
                            100,
                            5,
                            () -> Math.round((1.0F - HudRegistry.background(e, p)) * 100.0F),
                            v -> {
                                float old = p.background;
                                p.background = 1.0F - (float) v / 100.0F;
                                ScaledResolution r = new ScaledResolution(Minecraft.getMinecraft());
                                // With a background the box grows (the gap): if it touches another
                                // element, it goes to the nearest free place.
                                int sw = r.getScaledWidth();
                                int sh = r.getScaledHeight();
                                if (HudCollision.collides(e, sw, sh)
                                        && !HudCollision.findFree(e, sw, sh)) {
                                    p.background = old;
                                }
                                HudPersistence.touch();
                            },
                            v -> Math.round(v) + "%"));
        }
        e.options(out);
        // At the end, after the element's options: it is the restore of everything above it.
        out.add(
                Option.restore(
                        () -> {
                            ScaledResolution r = new ScaledResolution(Minecraft.getMinecraft());
                            restore(e, r.getScaledWidth(), r.getScaledHeight());
                        }));
        return out;
    }

    /**
     * The chat measures its lines in the bridge (wrapping and click): it needs to know its font,
     * and the lines are rebuilt.
     */
    static void fontChanged(HudElement e, Placement p) {
        if (!"minecraft.chat".equals(e.id())) {
            return;
        }
        VanillaHud.setChatGameFont(p.gameFont);
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.ingameGUI != null) {
            mc.ingameGUI.getChatGUI().refreshChat();
        }
    }

    /** "1x", "1.25x", "0.5x". */
    private static String scaleName(double v) {
        String s =
                String.format(Locale.ROOT, "%.2f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
        return decimal(s) + "x";
    }

    /**
     * Decimal separator of the game's language: comma for Portuguese and Spanish, dot otherwise.
     */
    static String decimal(String number) {
        String code = "";
        try {
            code =
                    Minecraft.getMinecraft()
                            .getLanguageManager()
                            .getCurrentLanguage()
                            .getLanguageCode();
        } catch (RuntimeException ignored) {
            // language not loaded yet
        }
        return code.startsWith("pt") || code.startsWith("es") ? number.replace('.', ',') : number;
    }

    private static String tr(String key) {
        return I18n.format(key);
    }

    // ---- restore ----

    /**
     * Everything as in the game without mods: the game's parts in the game's place and format, the
     * rest disabled.
     */
    static void restoreAll() {
        for (HudElement e : HudRegistry.elements) {
            Placement p = HudRegistry.placement(e);
            HudPlacement d = e.defaultPlacement();
            p.position(d);
            p.showInChat = d.showInChat;
            p.background = -1.0F;
            p.gameFont = false;
            fontChanged(e, p);
            p.enabled = HudRegistry.isBuiltin(e);
            if (HudRegistry.isBuiltin(e)) {
                VanillaOptions.restore(e.id());
            }
            HudRegistry.box(e)[2] = 0;
            restoreOptions(e);
        }
        HudPersistence.save();
    }

    /** The element's own options (the mod's) back to the default, and the mod saves. */
    private static void restoreOptions(HudElement e) {
        try {
            e.restoreOptions();
            e.optionsChanged();
        } catch (Throwable t) {
            System.out.println("[loader] HUD: " + e.id() + ": restoring options failed: " + t);
        }
    }

    /**
     * An element: place, scale and (game part) factory format; if it touches another, the nearest
     * free place.
     */
    static void restore(HudElement e, int sw, int sh) {
        Placement p = HudRegistry.placement(e);
        HudPlacement d = e.defaultPlacement();
        p.position(d);
        p.showInChat = d.showInChat;
        p.background = -1.0F;
        p.gameFont = false;
        fontChanged(e, p);
        if (HudRegistry.isBuiltin(e)) {
            VanillaOptions.restore(e.id());
        }
        restoreOptions(e);
        if (HudCollision.collides(e, sw, sh)) {
            HudCollision.findFree(e, sw, sh);
        }
        HudPersistence.save();
    }
}
