/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.loader.hud;

import dev.xavier.stein.loader.api.HudElement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

/**
 * Base of the game's own HUD parts on the grid: they stay visible beneath the screens and with F3,
 * and save the layout when their options change.
 */
abstract class BuiltinElement implements HudElement {

    @Override
    public boolean visibleWithScreens() {
        return true; // the game draws the HUD beneath the screens
    }

    @Override
    public boolean hideWithDebug() {
        return false;
    }

    @Override
    public void optionsChanged() {
        HudPersistence.save();
    }

    static FontRenderer font() {
        return Minecraft.getMinecraft().fontRendererObj;
    }
}
