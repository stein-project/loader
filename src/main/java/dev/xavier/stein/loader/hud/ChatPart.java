/*
 * Copyright (c) 2026 Breno Xavier. All rights reserved.
 * Proprietary software: see the LICENSE file at the root of this repository.
 */

package dev.xavier.stein.loader.hud;

import dev.xavier.stein.bridge.hooks.VanillaHud;
import dev.xavier.stein.loader.api.HudPlacement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.MathHelper;

/** The chat, drawn by the game and moved to the grid box (with its lines clickable there). */
final class ChatPart extends VanillaPart {

    private float chatH;
    private long chatAt;

    ChatPart() {
        super("chat");
    }

    @Override
    boolean fit(Minecraft mc, EntityPlayerSP p, PlayerControllerMP pc, int sw, int sh) {
        GuiNewChat chat = mc.ingameGUI.getChatGUI();
        float s = chat.getChatScale();
        int l = MathHelper.ceiling_float_int(chat.getChatWidth() / s);
        w = MathHelper.ceiling_float_int((l + 4) * s);
        h = Math.max(9, MathHelper.ceiling_float_int(chat.getLineCount() * 9 * s));
        defLeft = 2;
        defTop = sh - 28 - h;
        return true;
    }

    @Override
    void paint(Minecraft mc, GuiIngame gui, float partialTicks) throws Throwable {
        chat(mc, gui);
    }

    private void chat(Minecraft mc, GuiIngame gui) throws Throwable {
        int[] b = HudRegistry.box(this);
        int in = HudRegistry.inset(this); // the content starts after the background padding
        VanillaHud.chatPlacement(
                new float[] {
                    b[0] + in,
                    b[1] + in,
                    defLeft,
                    defTop,
                    HudRegistry.placement(this).scale,
                    res.getScaleFactor(),
                    res.getScaledHeight()
                });
        if (preview) {
            // In the editor the lines are already gone: the chat's place appears marked.
            Gui.drawRect(defLeft, defTop, defLeft + w, defTop + h, 0x50000000);
            mc.fontRendererObj.drawStringWithShadow(
                    I18n.format("steinloader.hud.sample.chat"),
                    defLeft + 2,
                    defTop + h - 9,
                    0xFFFFFF);
        }
        GlStateManager.translate(0.0F, res.getScaledHeight() - 48, 0.0F);
        VanillaHud.drawChat(gui.getChatGUI(), gui.getUpdateCounter());
    }

    /**
     * How much of the content height is actually occupied, from the base up: the grid's default
     * background covers only that. The chat box has the height of all the lines it can show, but
     * almost always there are only two or three on screen (and none once the messages have faded) -
     * a background the size of the box would be an empty panel in the middle of the game.
     */
    @Override
    float shownHeight() {
        if (preview) {
            return h;
        }
        float s = Minecraft.getMinecraft().ingameGUI.getChatGUI().getChatScale();
        float target = Math.min(h, VanillaHud.chatLinesShown() * 9 * s);
        long now = System.nanoTime();
        float dt = chatAt == 0L ? 1.0F : Math.min(1.0F, (now - chatAt) / 1.0E9F);
        chatAt = now;
        // The height slides to the new one (a line arriving or disappearing does not make the
        // background jump); with no line at all, it stays where it was while the background fades
        // along with the last one.
        if (target > 0.0F) {
            chatH += (target - chatH) * Math.min(1.0F, dt * 14.0F);
        }
        return chatH;
    }

    /**
     * How much of the background shows (0 to 1): in the chat, it follows the most visible line,
     * which fades out over time.
     */
    @Override
    float shownAlpha() {
        return preview ? 1.0F : VanillaHud.chatAlphaShown();
    }

    /** Off or hidden in this frame: the chat has nothing to click. */
    @Override
    void hidden() {
        VanillaHud.chatPlacement(null);
    }

    @Override
    public HudPlacement defaultPlacement() {
        return new HudPlacement(0, 2, 2, -28, 1.0F, true, true);
    }

    @Override
    public boolean hasBackground() {
        return true;
    }
}
