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

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;

/** The name of the held item, drawn as the game does (with an example in the editor). */
final class ItemNamePart extends VanillaPart {

    /** Editor example (item name, no item switched right now). */
    private String sample;

    ItemNamePart() {
        super("itemname");
    }

    @Override
    boolean fit(Minecraft mc, EntityPlayerSP p, PlayerControllerMP pc, int sw, int sh) {
        sample = null;
        GuiIngame gui = mc.ingameGUI;
        ItemStack st = (ItemStack) VanillaHud.highlightStack(gui);
        boolean real =
                VanillaHud.highlightTicks(gui) > 0
                        && st != null
                        && mc.gameSettings.heldItemTooltips
                        && !pc.isSpectator();
        if (!real && !preview) {
            return false;
        }
        String s = real ? st.getDisplayName() : I18n.format("item.swordDiamond.name");
        if (!real) {
            sample = s;
        }
        int tw = mc.fontRendererObj.getStringWidth(s);
        // The game moves the name down 14 px without the bars (creative): the box covers both.
        defLeft = (sw - tw) / 2;
        defTop = sh - 59;
        w = tw + 1;
        h = 23;
        return true;
    }

    @Override
    void paint(Minecraft mc, GuiIngame gui, float partialTicks) throws Throwable {
        if (sample != null) {
            mc.fontRendererObj.drawStringWithShadow(sample, defLeft, defTop, 0xFFFFFF);
        } else {
            VanillaHud.drawSelectedItem(gui, res);
        }
    }

    @Override
    public HudPlacement defaultPlacement() {
        return new HudPlacement(1, 2, 0, -36, 1.0F, true, true);
    }
}
