/*
 * Copyright (c) 2026 Breno Xavier. All rights reserved.
 * Proprietary software: see the LICENSE file at the root of this repository.
 */

package dev.xavier.stein.loader.hud;

import dev.xavier.stein.bridge.hooks.VanillaHud;
import dev.xavier.stein.gfx.SteinGlTrack;
import dev.xavier.stein.loader.api.HudPlacement;
import dev.xavier.stein.loader.api.Option;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Health, hunger and experience: a single item, with each part switchable. In the game's format
 * they are the usual bars; in the reduced one they become icon and number (heart 20, drumstick 20,
 * orb 30), horizontal or vertical.
 */
final class StatusPart extends VanillaPart {

    private static final ResourceLocation ORB =
            new ResourceLocation("textures/entity/experience_orb.png");
    private static final Gui GUI = new Gui();

    /** Reduced: 9 px of icon, 2 of gap to the number and 6 between one measure and the next. */
    private static final int ICON = 9;

    private static final int TEXT_GAP = 2;
    private static final int PART_GAP = 6;

    /** Mounted on a horse: the jump bar in place of experience. */
    private boolean jump;

    /** What goes into the health, hunger and experience block in this frame. */
    private boolean health;

    private boolean food;
    private boolean experience;

    /** Reduced format: {measure, value} per line - 0 health, 1 hunger, 2 experience. */
    private final List<int[]> parts = new ArrayList<>();

    StatusPart() {
        super("status");
    }

    /**
     * Health, hunger and experience: what fits in this frame and the box that the three (or just
     * one) occupy.
     */
    @Override
    boolean fit(Minecraft mc, EntityPlayerSP p, PlayerControllerMP pc, int sw, int sh) {
        boolean bars =
                pc.shouldDrawHUD() || preview; // creative and spectator have no health or hunger
        jump = p.isRidingHorse();
        health = VanillaOptions.statusHealth && bars;
        food = VanillaOptions.statusFood && bars;
        experience = VanillaOptions.statusXp && (jump || pc.gameIsSurvivalOrAdventure() || preview);
        if (!health && !food && !experience) {
            return false;
        }
        if (VanillaOptions.statusStyle == 1) {
            return reduced(p, pc);
        }
        int left = Integer.MAX_VALUE;
        int right = Integer.MIN_VALUE;
        int top = Integer.MAX_VALUE;
        int bottom = Integer.MIN_VALUE;
        if (health) {
            // Rows of hearts (max health and absorption) and the armour on top, stacked as the
            // game stacks them.
            int rows =
                    MathHelper.ceiling_float_int(
                            (p.getMaxHealth() + p.getAbsorptionAmount()) / 2.0F / 10.0F);
            int rowH = Math.max(10 - (rows - 2), 3);
            left = sw / 2 - 91;
            right = sw / 2 - 10;
            top = sh - 39 - (rows - 1) * rowH - 10 - 2; // 2 px for the heart that jumps
            bottom = sh - 29;
        }
        if (food) {
            int t = sh - 49; // the air bubbles, above hunger
            if (p.ridingEntity instanceof EntityLivingBase mount) {
                int hearts = Math.min(30, (int) (mount.getMaxHealth() + 0.5F) / 2);
                t = Math.min(t, sh - 39 - ((hearts + 9) / 10 - 1) * 10);
            }
            left = Math.min(left, sw / 2 + 10);
            right = Math.max(right, sw / 2 + 91);
            top = Math.min(top, t - 2);
            bottom = Math.max(bottom, sh - 29);
        }
        if (experience) {
            left = Math.min(left, sw / 2 - 91);
            right = Math.max(right, sw / 2 + 91);
            top = Math.min(top, sh - 35);
            bottom = Math.max(bottom, sh - 24);
        }
        defLeft = left;
        defTop = top;
        w = right - left;
        h = bottom - top;
        return true;
    }

    /** Reduced format: icon and number for each measure that is on. */
    private boolean reduced(EntityPlayerSP p, PlayerControllerMP pc) {
        parts.clear();
        boolean fake = preview && !pc.shouldDrawHUD(); // in the editor in creative, an example
        if (health) {
            parts.add(
                    new int[] {
                        0,
                        fake
                                ? 20
                                : MathHelper.ceiling_float_int(
                                        p.getHealth() + p.getAbsorptionAmount())
                    });
        }
        if (food) {
            parts.add(new int[] {1, fake ? 20 : p.getFoodStats().getFoodLevel()});
        }
        if (experience) {
            parts.add(new int[] {2, fake ? 30 : p.experienceLevel});
        }
        if (parts.isEmpty()) {
            return false;
        }
        FontRenderer fr = Minecraft.getMinecraft().fontRendererObj;
        if (VanillaOptions.statusVertical) {
            int max = 0;
            for (int[] part : parts) {
                max = Math.max(max, ICON + TEXT_GAP + fr.getStringWidth(String.valueOf(part[1])));
            }
            w = max;
            h = parts.size() * 10 - 1;
        } else {
            int total = 0;
            for (int[] part : parts) {
                total += ICON + TEXT_GAP + fr.getStringWidth(String.valueOf(part[1])) + PART_GAP;
            }
            w = Math.max(1, total - PART_GAP);
            h = 10;
        }
        return true;
    }

    @Override
    void paint(Minecraft mc, GuiIngame gui, float partialTicks) throws Throwable {
        if (VanillaOptions.statusStyle == 1) {
            reducedDraw(mc);
        } else {
            statusDraw(mc, gui);
        }
    }

    /** The game's bars; with only one of the two on, the other stays outside the box (scissor). */
    private void statusDraw(Minecraft mc, GuiIngame gui) throws Throwable {
        if (health || food) {
            icons(mc);
            GlStateManager.enableBlend();
            boolean clip = health != food;
            int[] b = HudRegistry.box(this);
            int sf = res.getScaleFactor();
            if (clip) {
                SteinGlTrack.scissor(
                        true,
                        b[0] * sf,
                        mc.displayHeight - (b[1] + b[3]) * sf,
                        b[2] * sf,
                        b[3] * sf);
            }
            try {
                VanillaHud.drawPlayerStats(gui, res);
            } finally {
                if (clip) {
                    SteinGlTrack.scissor(false, 0, 0, 0, 0);
                }
                GlStateManager.disableBlend();
            }
        }
        if (experience) {
            icons(mc);
            VanillaHud.drawExperience(gui, res, res.getScaledWidth() / 2 - 91, jump);
        }
    }

    /** Reduced: heart 20, drumstick 20, orb 30 — horizontal or one per line. */
    private void reducedDraw(Minecraft mc) {
        FontRenderer fr = mc.fontRendererObj;
        int x = 0;
        int y = 0;
        for (int[] part : parts) {
            String num = String.valueOf(part[1]);
            icon(mc, part[0], x, y);
            fr.drawStringWithShadow(
                    num, x + ICON + TEXT_GAP, y + 1, part[0] == 2 ? 0x80FF20 : 0xFFFFFF);
            if (VanillaOptions.statusVertical) {
                y += 10;
            } else {
                x += ICON + TEXT_GAP + fr.getStringWidth(num) + PART_GAP;
            }
        }
    }

    private static void icon(Minecraft mc, int part, int x, int y) {
        GlStateManager.enableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        if (part == 2) {
            mc.getTextureManager().bindTexture(ORB);
            Gui.drawScaledCustomSizeModalRect(x, y, 0.0F, 0.0F, 16, 16, ICON, ICON, 64.0F, 64.0F);
        } else {
            mc.getTextureManager().bindTexture(Gui.icons);
            GUI.drawTexturedModalRect(
                    x, y, 52, part == 0 ? 0 : 27, ICON, ICON); // full heart / full drumstick
        }
        GlStateManager.disableBlend();
    }

    private static void icons(Minecraft mc) {
        mc.getTextureManager().bindTexture(Gui.icons);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    public HudPlacement defaultPlacement() {
        return new HudPlacement(1, 2, 0, -24, 1.0F, true, true);
    }

    @Override
    public void options(List<Option> out) {
        out.add(
                Option.cycle(
                        I18n.format("steinloader.hud.style"),
                        new String[] {
                            I18n.format("steinloader.hud.status.0"),
                            I18n.format("steinloader.hud.status.1")
                        },
                        () -> VanillaOptions.statusStyle,
                        i -> VanillaOptions.statusStyle = i));
        out.add(
                Option.cycle(
                        I18n.format("steinloader.hud.layout"),
                        new String[] {
                            I18n.format("steinloader.hud.layout.0"),
                            I18n.format("steinloader.hud.layout.1")
                        },
                        () -> VanillaOptions.statusVertical ? 1 : 0,
                        i -> VanillaOptions.statusVertical = i == 1));
        out.add(
                Option.toggle(
                        I18n.format("steinloader.hud.health"),
                        () -> VanillaOptions.statusHealth,
                        v -> VanillaOptions.statusHealth = v));
        out.add(
                Option.toggle(
                        I18n.format("steinloader.hud.food"),
                        () -> VanillaOptions.statusFood,
                        v -> VanillaOptions.statusFood = v));
        out.add(
                Option.toggle(
                        I18n.format("steinloader.hud.experience"),
                        () -> VanillaOptions.statusXp,
                        v -> VanillaOptions.statusXp = v));
    }
}
