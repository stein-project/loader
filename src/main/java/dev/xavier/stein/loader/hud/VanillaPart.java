/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.loader.hud;

import dev.xavier.stein.loader.api.HudPlacement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;

/**
 * A part of the game's own HUD on the grid: the common frame of the hotbar, the health, hunger and
 * experience block, the held item name and the chat ({@link HotbarPart}, {@link StatusPart}, {@link
 * ItemNamePart}, {@link ChatPart}). The drawing is mostly the game's (hooks/VanillaHud): the part
 * comes out where the game would put it, shifted to the grid box, so the look is identical to the
 * original.
 */
abstract class VanillaPart extends BuiltinElement {

    private final String key;

    /**
     * The part's box in the game's place, on this screen (GUI pixels); in the reduced format, 0
     * (the drawing is ours).
     */
    int defLeft;

    int defTop;
    int w;
    int h;

    boolean preview;
    ScaledResolution res;

    VanillaPart(String key) {
        this.key = key;
    }

    @Override
    public String id() {
        return "minecraft." + key;
    }

    @Override
    public String name() {
        return I18n.format("steinloader.hud." + key);
    }

    @Override
    public boolean layout(boolean preview, float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP p = mc.thePlayer;
        PlayerControllerMP pc = mc.playerController;
        if (p == null || pc == null) {
            return false;
        }
        this.preview = preview;
        res = new ScaledResolution(mc);
        int sw = res.getScaledWidth();
        int sh = res.getScaledHeight();
        defLeft = 0;
        defTop = 0;
        return fit(mc, p, pc, sw, sh);
    }

    /** Measures the part for this frame (sets the box in the game's place); false = not shown. */
    abstract boolean fit(Minecraft mc, EntityPlayerSP p, PlayerControllerMP pc, int sw, int sh);

    @Override
    public int width() {
        return w;
    }

    @Override
    public int height() {
        return h;
    }

    @Override
    public void draw(boolean alignRight, float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        GuiIngame gui = mc.ingameGUI;
        GlStateManager.pushMatrix();
        GlStateManager.translate(-defLeft, -defTop, 0.0F);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        try {
            paint(mc, gui, partialTicks);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        } finally {
            GlStateManager.popMatrix();
        }
    }

    /** Draws the part in the game's place (the translation to the grid box is already applied). */
    abstract void paint(Minecraft mc, GuiIngame gui, float partialTicks) throws Throwable;

    /**
     * How much of the content height is actually occupied, from the base up: the grid's default
     * background covers only that.
     */
    float shownHeight() {
        return h;
    }

    /** How much of the background shows (0 to 1). */
    float shownAlpha() {
        return 1.0F;
    }

    /** Off or hidden in this frame. */
    void hidden() {}

    /** The hotbar does not change size with the background: it goes inside its box. */
    boolean backgroundInside() {
        return false;
    }

    @Override
    public boolean hasBackground() {
        return false;
    }

    // Placement of the part in the game's place: the bottom centre or the bottom left corner, with
    // each box's base where the game puts it.
    static HudPlacement bottomCentre(int y) {
        return new HudPlacement(1, 2, 0, y, 1.0F, true, true);
    }
}
