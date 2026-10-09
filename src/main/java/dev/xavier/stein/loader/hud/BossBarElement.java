/*
 * Copyright (c) 2026 Breno Xavier. All rights reserved.
 * Proprietary software: see the LICENSE file at the root of this repository.
 */

package dev.xavier.stein.loader.hud;

import dev.xavier.stein.bridge.hooks.VanillaHud;
import dev.xavier.stein.loader.api.HudPlacement;
import dev.xavier.stein.loader.api.Option;
import dev.xavier.stein.loader.gui.Ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;

import org.lwjgl.opengl.GL11;

import java.util.List;

/**
 * The boss bar (Wither, Dragon, and the one plugins use for notices) on the grid: move, turn off
 * and the format — the game's (the game's own texture), thin (one line in the chosen color) or text
 * only; name on top and percentage.
 */
final class BossBarElement extends BuiltinElement {

    /** Width of the game's bar. */
    private static final int BAR = 182;

    private final Gui gui = new Gui();
    private String name = "";
    private float health;

    @Override
    public String id() {
        return "minecraft.bossbar";
    }

    @Override
    public String name() {
        return I18n.format("steinloader.hud.bossbar");
    }

    @Override
    public boolean layout(boolean preview, float partialTicks) {
        String n = preview ? I18n.format("steinloader.hud.sample.boss") : VanillaHud.bossName();
        if (n == null) {
            return false;
        }
        name = n;
        health = preview ? 0.7F : Ui.clamp01(VanillaHud.bossHealth());
        return true;
    }

    /** Color code (gray) of the percentage next to the boss name; not translated. */
    private static final String PERCENT_COLOR = "§7";

    private String label() {
        return VanillaOptions.bossPercent
                ? name + " " + PERCENT_COLOR + Math.round(health * 100.0F) + "%"
                : name;
    }

    @Override
    public int width() {
        return VanillaOptions.bossStyle == 2 ? font().getStringWidth(label()) : BAR;
    }

    @Override
    public int height() {
        if (VanillaOptions.bossStyle == 2) {
            return 8;
        }
        return (VanillaOptions.bossName ? 10 : 0) + (VanillaOptions.bossStyle == 0 ? 5 : 3);
    }

    @Override
    public void draw(boolean alignRight, float partialTicks) {
        FontRenderer fr = font();
        String label = label();
        if (VanillaOptions.bossStyle == 2) {
            fr.drawStringWithShadow(label, 0, 0, 0xFFFFFF);
            return;
        }
        int y = VanillaOptions.bossName ? 10 : 0;
        if (VanillaOptions.bossName) {
            fr.drawStringWithShadow(label, BAR / 2 - fr.getStringWidth(label) / 2, 0, 0xFFFFFF);
        }
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        if (VanillaOptions.bossStyle == 0) {
            // Like GuiIngame.renderBossHealth: the background twice and the health on top, from the
            // game's icon texture.
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            Minecraft.getMinecraft().getTextureManager().bindTexture(Gui.icons);
            int filled = (int) (health * (BAR + 1));
            gui.drawTexturedModalRect(0, y, 0, 74, BAR, 5);
            gui.drawTexturedModalRect(0, y, 0, 74, BAR, 5);
            if (filled > 0) {
                gui.drawTexturedModalRect(0, y, 0, 79, filled, 5);
            }
        } else {
            Gui.drawRect(0, y, BAR, y + 3, 0xA0000000);
            int filled = Math.round(health * BAR);
            if (filled > 0) {
                Gui.drawRect(
                        0, y, filled, y + 3, 0xFF000000 | Option.rgb(VanillaOptions.bossColor));
            }
        }
        GlStateManager.disableBlend();
    }

    @Override
    public HudPlacement defaultPlacement() {
        // Where the game draws it: centered at the top, the name at 2 px and the bar at 12.
        return new HudPlacement(1, 0, 0, 2, 1.0F, true, true);
    }

    @Override
    public void options(List<Option> out) {
        String[] styles = {
            I18n.format("steinloader.hud.style.0"),
            I18n.format("steinloader.hud.style.1"),
            I18n.format("steinloader.hud.style.2")
        };
        out.add(
                Option.cycle(
                        I18n.format("steinloader.hud.style"),
                        styles,
                        () -> VanillaOptions.bossStyle,
                        i -> VanillaOptions.bossStyle = i));
        out.add(
                Option.toggle(
                        I18n.format("steinloader.hud.bossName"),
                        () -> VanillaOptions.bossName,
                        v -> VanillaOptions.bossName = v));
        out.add(
                Option.toggle(
                        I18n.format("steinloader.hud.percent"),
                        () -> VanillaOptions.bossPercent,
                        v -> VanillaOptions.bossPercent = v));
        out.add(
                Option.color(
                        I18n.format("steinloader.hud.barColor"),
                        () -> VanillaOptions.bossColor,
                        v -> VanillaOptions.bossColor = v));
    }
}
