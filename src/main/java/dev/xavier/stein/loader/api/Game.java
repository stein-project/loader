/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Breno Xavier
 *
 * Part of the Stein Loader API, licensed under the MIT License (see LICENSE-API).
 */

package dev.xavier.stein.loader.api;

import dev.xavier.stein.loader.api.spi.Host;

/** What mods ask of the game, through the Loader. */
public final class Game {

    private Game() {}

    /**
     * Removes the effects list from the inventory (and the screen's sideways shift) — for whoever
     * shows the effects on the HUD.
     */
    public static void setInventoryEffectsHidden(boolean hide) {
        Host.INSTANCE.setInventoryEffectsHidden(hide);
    }

    /** Fraction of the current tick, for whoever draws interpolated. */
    public static float partialTicks() {
        return Host.INSTANCE.partialTicks();
    }

    /**
     * Resets the game's smooth camera filters: whoever turns smooth camera on for a while (a zoom,
     * for example) and then off leaves no leftover movement for the next time.
     */
    public static void resetCameraSmoothing() {
        Host.INSTANCE.resetCameraSmoothing();
    }

    /**
     * Version of the enchantments: goes up after each write or removal of the "ench" key in any
     * NBT. Whoever stores something derived from an ItemStack's enchantments checks it, and the
     * stack's NBT (the same object), to know whether it is still valid.
     */
    public static int enchantmentsVersion() {
        return Host.INSTANCE.enchantmentsVersion();
    }

    /**
     * A slot in each ItemStack for the mod to store what it computed about it (a field created by
     * the patcher): reading and writing costs one field, on an object the game just read in order
     * to draw — an identity map cost off-cache memory on every lookup. A single slot for all mods:
     * check that the object is yours (instanceof) before using it, because another mod may have
     * stored its own.
     *
     * @return the stored object (from any mod), or null
     */
    public static Object itemData(Object stack) {
        return Host.INSTANCE.itemData(stack);
    }

    /** Stores the mod's object in the ItemStack (see {@link #itemData}). */
    public static void setItemData(Object stack, Object value) {
        Host.INSTANCE.setItemData(stack, value);
    }

    /**
     * Marks a block (Block) for {@link SteinMod#onBlockModel}: only the marked ones go through the
     * mods when chunks are built, the others cost the read of one field. One mark per block, shared
     * by all mods; changing the mark applies to chunks built from then on.
     */
    public static void watchBlockModel(Object block, boolean watched) {
        Host.INSTANCE.watchBlockModel(block, watched);
    }

    /**
     * The texture (TextureAtlasSprite) of the block and item atlas that contains the atlas
     * coordinate (u, v), or null — 1.8.9's BakedQuad does not keep the texture, only the
     * coordinates. Index rebuilt on every resource reload, before {@link
     * SteinMod#onModelsReloaded}; can be called from any thread.
     */
    public static Object spriteAt(float u, float v) {
        return Host.INSTANCE.spriteAt(u, v);
    }

    /** Frames per second, the F3 number (measured every second). */
    @Experimental(since = "0.1.16")
    public static int fps() {
        return Host.INSTANCE.gameFps();
    }

    /** The frame limit chosen in the options, or 0 for no limit. */
    @Experimental(since = "0.1.16")
    public static int fpsLimit() {
        return Host.INSTANCE.gameFpsLimit();
    }

    /** The monitor refresh rate in Hz (60, 144...), or 0 if the system does not say. */
    @Experimental(since = "0.1.16")
    public static int refreshRate() {
        return Host.INSTANCE.gameRefreshRate();
    }
}
