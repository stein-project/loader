/*
 * Copyright (c) 2026 Breno Xavier. All rights reserved.
 * Proprietary software: see the LICENSE file at the root of this repository.
 */

package dev.xavier.stein.loader;

import dev.xavier.stein.loader.api.Terrain;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityMobSpawner;
import net.minecraft.util.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

/**
 * The world's blocks for the API (Terrain). A map reads column by column, many times in each chunk:
 * the chunk of the last read is kept, and the lookup in the game's chunk map only happens when the
 * read moves to another one.
 */
final class TerrainOps {

    private static final int OPAQUE_BLACK = 0xFF000000;

    private static final BlockColors COLORS = new BlockColors();
    private static World world;
    private static Chunk chunk;
    private static int chunkX;
    private static int chunkZ;

    private TerrainOps() {}

    /** New resource pack: the colours are computed again. */
    static void reset() {
        COLORS.reset();
    }

    private static World world() {
        World w = Minecraft.getMinecraft().theWorld;
        if (w != world) {
            world = w;
            chunk = null;
        }
        return w;
    }

    /** The chunk of the block column (x, z), or null if it has not arrived. */
    private static Chunk chunk(int x, int z) {
        return chunkAt(x >> 4, z >> 4);
    }

    private static Chunk chunkAt(int cx, int cz) {
        World w = world();
        if (w == null) {
            return null;
        }
        // Only what has arrived and is still loaded is kept: an empty one (not arrived yet) and an
        // unloaded one are looked up again.
        if (chunk == null || chunkX != cx || chunkZ != cz || !chunk.isLoaded()) {
            Chunk c = w.getChunkFromChunkCoords(cx, cz);
            if (c == null || c.isEmpty()) {
                chunk = null;
                return null;
            }
            chunk = c;
            chunkX = cx;
            chunkZ = cz;
        }
        return chunk;
    }

    private static IBlockState state(int x, int y, int z) {
        Chunk c = y < 0 || y > 255 ? null : chunk(x, z);
        return c == null ? null : c.getBlockState(new BlockPos(x, y, z));
    }

    static Object current() {
        return world();
    }

    static boolean loaded(int cx, int cz) {
        return chunkAt(cx, cz) != null;
    }

    static int height(int x, int z) {
        Chunk c = chunk(x, z);
        return c == null ? 0 : c.getHeightValue(x & 15, z & 15);
    }

    static boolean air(int x, int y, int z) {
        IBlockState s = state(x, y, z);
        return s == null || s.getBlock().getMaterial() == Material.air;
    }

    static String blockId(int x, int y, int z) {
        IBlockState s = state(x, y, z);
        Object name =
                s == null
                        ? null
                        : net.minecraft.block.Block.blockRegistry.getNameForObject(s.getBlock());
        return name == null ? "minecraft:air" : name.toString();
    }

    static boolean solid(int x, int y, int z) {
        IBlockState s = state(x, y, z);
        return s != null && s.getBlock().getMaterial().isSolid();
    }

    static boolean water(int x, int y, int z) {
        IBlockState s = state(x, y, z);
        return s != null && s.getBlock().getMaterial() == Material.water;
    }

    static int color(int x, int y, int z) {
        IBlockState s = state(x, y, z);
        return s == null ? OPAQUE_BLACK : COLORS.color(world, s, new BlockPos(x, y, z));
    }

    static int mapColor(int x, int y, int z) {
        IBlockState s = state(x, y, z);
        return s == null ? OPAQUE_BLACK : COLORS.mapColor(s);
    }

    static int skyLight(int x, int y, int z) {
        World w = world();
        return w == null ? 0 : w.getLightFor(EnumSkyBlock.SKY, new BlockPos(x, y, z));
    }

    static boolean hasSky() {
        World w = world();
        return w != null && !w.provider.getHasNoSky();
    }

    static void spawners(Terrain.Visitor visitor) {
        World w = world();
        if (w == null) {
            return;
        }
        for (TileEntity t : w.loadedTileEntityList) {
            if (t instanceof TileEntityMobSpawner) {
                BlockPos p = t.getPos();
                visitor.at(p.getX(), p.getY(), p.getZ());
            }
        }
    }
}
