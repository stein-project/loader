/*
 * Copyright (c) 2026 Breno Xavier. All rights reserved.
 * Proprietary software: see the LICENSE file at the root of this repository.
 */

package dev.xavier.stein.loader.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

/**
 * The game button restyled with the design system: click, sound and state remain those of GuiButton
 * (the screen that uses it changes nothing), and only the drawing is the pill from {@link Widgets}.
 */
public final class PillButton extends GuiButton {

    /**
     * The screen's primary action (the filled pill, in the accent colour) or a plain one (outline
     * only).
     */
    public boolean primary;

    public PillButton(int id, int x, int y, int w, int h, String label) {
        super(id, x, y, w, h, label);
    }

    @Override
    public void drawButton(Minecraft mc, int mx, int my) {
        if (!this.visible) {
            return;
        }
        this.hovered =
                mx >= this.xPosition
                        && my >= this.yPosition
                        && mx < this.xPosition + this.width
                        && my < this.yPosition + this.height;
        if (primary) {
            Widgets.primary(
                    this.displayString,
                    this.xPosition,
                    this.yPosition,
                    this.width,
                    this.height,
                    this.hovered,
                    !this.enabled,
                    1.0F);
        } else {
            Widgets.button(
                    this.displayString,
                    this.xPosition,
                    this.yPosition,
                    this.width,
                    this.height,
                    this.hovered,
                    !this.enabled,
                    1.0F);
        }
    }
}
