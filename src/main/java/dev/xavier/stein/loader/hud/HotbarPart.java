/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.loader.hud;

import dev.xavier.stein.bridge.hooks.VanillaHud;
import dev.xavier.stein.loader.api.HudPlacement;
import dev.xavier.stein.loader.api.Option;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;

import org.lwjgl.opengl.GL11;

import java.util.List;

/** The hotbar: the game's, or just the items, without the frame (the selected slot is marked). */
final class HotbarPart extends VanillaPart {

    HotbarPart() {
        super("hotbar");
    }

    @Override
    boolean fit(Minecraft mc, EntityPlayerSP p, PlayerControllerMP pc, int sw, int sh) {
        if (pc.isSpectator() && !preview) {
            return false; // spectator: the spectator menu belongs to the game
        }
        defLeft = sw / 2 - 92;
        defTop = sh - 23;
        w = 184;
        h = 23;
        return true;
    }

    @Override
    void paint(Minecraft mc, GuiIngame gui, float partialTicks) throws Throwable {
        hotbar(mc, gui, partialTicks);
    }

    /** The game's hotbar, or just its items, with the selected slot marked. */
    private void hotbar(Minecraft mc, GuiIngame gui, float partialTicks) throws Throwable {
        if (VanillaOptions.hotbarStyle == 0) {
            VanillaHud.drawHotbar(gui, res, partialTicks);
            return;
        }
        int x = res.getScaledWidth() / 2;
        int y = res.getScaledHeight();
        int sel = mc.thePlayer.inventory.currentItem;
        // Without the frame, the selected slot sits between two small dashes.
        Gui.drawRect(x - 90 + sel * 20, y - 22, x - 70 + sel * 20, y - 21, 0x60FFFFFF);
        Gui.drawRect(x - 90 + sel * 20, y - 2, x - 70 + sel * 20, y - 1, 0x60FFFFFF);
        GlStateManager.enableRescaleNormal();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        RenderHelper.enableGUIStandardItemLighting();
        for (int i = 0; i < 9; i++) {
            VanillaHud.drawHotbarItem(gui, i, x - 88 + i * 20, y - 19, partialTicks, mc.thePlayer);
        }
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableRescaleNormal();
        GlStateManager.disableBlend();
    }

    @Override
    public HudPlacement defaultPlacement() {
        // The game's place: bottom centre, with the box's base where the game puts it.
        return new HudPlacement(1, 2, 0, 0, 1.0F, true, true);
    }

    @Override
    public boolean hasBackground() {
        return true;
    }

    /** The hotbar does not change size with the background: it goes inside its box. */
    @Override
    boolean backgroundInside() {
        return true;
    }

    @Override
    public void options(List<Option> out) {
        out.add(
                Option.cycle(
                        I18n.format("steinloader.hud.variation"),
                        new String[] {
                            I18n.format("steinloader.hud.hotbar.0"),
                            I18n.format("steinloader.hud.hotbar.1")
                        },
                        () -> VanillaOptions.hotbarStyle,
                        i -> VanillaOptions.hotbarStyle = i));
    }
}
