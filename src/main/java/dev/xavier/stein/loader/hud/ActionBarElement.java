/*
 * Copyright (c) 2026 Breno Xavier. All rights reserved.
 * Proprietary software: see the LICENSE file at the root of this repository.
 */

package dev.xavier.stein.loader.hud;

import dev.xavier.stein.bridge.hooks.VanillaHud;
import dev.xavier.stein.loader.api.HudPlacement;
import dev.xavier.stein.loader.api.Option;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;

import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.List;

/**
 * The game's action bar (the text above the hotbar: server messages, the record playing, mod
 * notices) in the grid: move, turn off, shadow and a box behind. It fades like the game's, in the
 * last second; with a record playing, rainbow.
 */
final class ActionBarElement extends BuiltinElement {

    private String text = "";
    private int color;
    private int alpha;

    @Override
    public String id() {
        return "minecraft.actionbar";
    }

    @Override
    public String name() {
        return I18n.format("steinloader.hud.actionbar");
    }

    @Override
    public boolean layout(boolean preview, float partialTicks) {
        String t = preview ? I18n.format("steinloader.hud.sample.action") : VanillaHud.actionText();
        if (t == null || t.isEmpty()) {
            return false;
        }
        text = t;
        alpha = preview ? 255 : Math.min(255, (int) (VanillaHud.actionAlpha() * 255.0F));
        color =
                !preview && VanillaHud.actionPlaying()
                        ? Color.HSBtoRGB(
                                        (float) (VanillaHud.actionSeconds() * 20.0 / 50.0),
                                        0.7F,
                                        0.6F)
                                & 0xFFFFFF
                        : 0xFFFFFF;
        return alpha > 8;
    }

    @Override
    public int width() {
        return font().getStringWidth(text);
    }

    @Override
    public int height() {
        return 8;
    }

    @Override
    public void draw(boolean alignRight, float partialTicks) {
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        font().drawString(text, 0, 0, color | alpha << 24, VanillaOptions.actionShadow);
        GlStateManager.disableBlend();
    }

    /**
     * How much the message shows in this frame (0 to 1): the grid background fades along with it.
     */
    float opacity() {
        return alpha / 255.0F;
    }

    @Override
    public HudPlacement defaultPlacement() {
        // Where the game draws: centered, with the text 72 px above the bottom.
        return new HudPlacement(1, 2, 0, -64, 1.0F, true, true);
    }

    @Override
    public boolean hasBackground() {
        return false;
    }

    @Override
    public void options(List<Option> out) {
        out.add(
                Option.toggle(
                        I18n.format("steinloader.hud.shadow"),
                        () -> VanillaOptions.actionShadow,
                        v -> VanillaOptions.actionShadow = v));
    }
}
