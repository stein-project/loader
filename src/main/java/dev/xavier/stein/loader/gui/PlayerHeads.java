/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */
package dev.xavier.stein.loader.gui;

import dev.xavier.stein.loader.PlayerInfos;
import dev.xavier.stein.ui.Draw2D;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.util.ResourceLocation;

/**
 * Players' faces and skins for the mods' drawing: the server's skin, or the original account's with
 * that nick ({@link SkinLookup}), or the default. It lives in the Loader because it reads the tab
 * list the Loader keeps ({@link PlayerInfos}); the drawing itself is the design system's ({@link
 * Draw2D}).
 */
public final class PlayerHeads {

    private static final SkinLookup SKINS = new SkinLookup();

    private PlayerHeads() {}

    public static void playerHead(String nick, float x, float y, float size) {
        NetworkPlayerInfo info = PlayerInfos.get(nick);
        head(
                info != null ? info.getLocationSkin() : DefaultPlayerSkin.getDefaultSkinLegacy(),
                x,
                y,
                size);
    }

    /**
     * The player's face; with premium, and without a skin sent by the server, the original
     * account's with that nick (SkinLookup).
     */
    public static void playerHead(String nick, float x, float y, float size, boolean premium) {
        if (!premium) {
            playerHead(nick, x, y, size);
            return;
        }
        SKINS.tick();
        NetworkPlayerInfo info = PlayerInfos.get(nick);
        ResourceLocation skin = null;
        if (info == null || !info.hasLocationSkin()) {
            skin = nick == null ? null : SKINS.skin(nick);
        }
        if (skin == null) {
            skin = info != null ? info.getLocationSkin() : DefaultPlayerSkin.getDefaultSkinLegacy();
        }
        head(skin, x, y, size);
    }

    /**
     * The skin for the body: the server's; without it, the original account's with that nick
     * ({@link SkinLookup}); otherwise the default.
     */
    public static ResourceLocation playerSkin(String nick) {
        SKINS.tick();
        NetworkPlayerInfo info = PlayerInfos.get(nick);
        if (info != null && info.hasLocationSkin()) {
            return info.getLocationSkin();
        }
        ResourceLocation skin = nick == null ? null : SKINS.skin(nick);
        if (skin != null) {
            return skin;
        }
        return info != null ? info.getLocationSkin() : DefaultPlayerSkin.getDefaultSkinLegacy();
    }

    private static void head(ResourceLocation skin, float x, float y, float size) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(skin);
        float p = 1.0F / 64;
        Draw2D.texQuad(x, y, size, size, 8 * p, 8 * p, 16 * p, 16 * p, 0xFFFFFFFF); // face
        Draw2D.texQuad(x, y, size, size, 40 * p, 8 * p, 48 * p, 16 * p, 0xFFFFFFFF); // hat
    }
}
