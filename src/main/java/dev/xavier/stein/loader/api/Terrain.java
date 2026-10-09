/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Breno Xavier
 *
 * Part of the Stein Loader API, licensed under the MIT License (see LICENSE-API).
 */

package dev.xavier.stein.loader.api;

import dev.xavier.stein.loader.api.spi.Host;

/**
 * The blocks of the world loaded on the client, for maps and radars: height, what is in each place,
 * light and the color of each block as a minimap paints it. Only what the server sent: a chunk that
 * did not arrive gives "nothing" (air, height 0). Call from the game thread.
 *
 * <pre>
 * if (Terrain.isLoaded(x &gt;&gt; 4, z &gt;&gt; 4)) {
 *     int y = Terrain.height(x, z) - 1;
 *     int argb = Terrain.color(x, y, z);   // the block's texture with the biome tint
 * }
 * </pre>
 */
@Experimental(since = "0.1.16")
public final class Terrain {

    /** Receives each position of a search ({@link #forEachSpawner}). */
    @FunctionalInterface
    public interface Visitor {
        /**
         * One position found.
         *
         * @param x block x
         * @param y block y
         * @param z block z
         */
        void at(int x, int y, int z);
    }

    private Terrain() {}

    /**
     * The current world (a game type), or null outside it: compare to know whether the player
     * changed worlds.
     */
    public static Object current() {
        return Host.INSTANCE.terrainWorld();
    }

    /** Did the chunk (chunk x and z, block &gt;&gt; 4) arrive and does it have blocks? */
    public static boolean isLoaded(int chunkX, int chunkZ) {
        return Host.INSTANCE.terrainLoaded(chunkX, chunkZ);
    }

    /**
     * The column's height: the first y above the highest block that stops sky light (the game's
     * height map — flowers, thin snow and glass do not count). 0 without a chunk.
     */
    public static int height(int x, int z) {
        return Host.INSTANCE.terrainHeight(x, z);
    }

    /**
     * Whether there is no block at the position (also true with no chunk).
     *
     * @param x block x
     * @param y block y
     * @param z block z
     * @return true for air
     */
    public static boolean isAir(int x, int y, int z) {
        return Host.INSTANCE.terrainAir(x, y, z);
    }

    /**
     * The block id ({@code "minecraft:leaves"}; {@code "minecraft:air"} with no block or no chunk).
     */
    public static String blockId(int x, int y, int z) {
        return Host.INSTANCE.terrainBlockId(x, y, z);
    }

    /** Solid material (stone, wood, glass; not plants, thin snow or liquids). */
    public static boolean isSolid(int x, int y, int z) {
        return Host.INSTANCE.terrainSolid(x, y, z);
    }

    /**
     * Whether the block is water (still or flowing).
     *
     * @param x block x
     * @param y block y
     * @param z block z
     * @return true for water
     */
    public static boolean isWater(int x, int y, int z) {
        return Host.INSTANCE.terrainWater(x, y, z);
    }

    /**
     * The block's color as a detailed minimap paints it (opaque ARGB): the average of the top
     * texture, tinted by the biome where the game tints (grass, leaves, water). Recalculated by
     * itself when the texture pack changes.
     */
    public static int color(int x, int y, int z) {
        return Host.INSTANCE.terrainColor(x, y, z);
    }

    /**
     * The color of the game's map item for the block (just the material's, without texture or
     * biome), opaque ARGB.
     */
    public static int mapColor(int x, int y, int z) {
        return Host.INSTANCE.terrainMapColor(x, y, z);
    }

    /** The sky light that reaches there (0 to 15): little light around the player means a cave. */
    public static int skyLight(int x, int y, int z) {
        return Host.INSTANCE.terrainSkyLight(x, y, z);
    }

    /** Does the world have a sky? (The Nether does not.) */
    public static boolean hasSky() {
        return Host.INSTANCE.terrainHasSky();
    }

    /** Every loaded monster cage (spawner). */
    public static void forEachSpawner(Visitor visitor) {
        Host.INSTANCE.terrainSpawners(visitor);
    }
}
