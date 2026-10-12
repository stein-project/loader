/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.loader;

import dev.xavier.stein.loader.api.Targeting;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

/**
 * The first block that stops a ray, with the blocks it passes through
 * (Targeting.Query.passThrough).
 *
 * <p>Walks cell by cell along the line (the Amanatides and Woo grid traversal), and only the cells
 * the segment crosses are looked at. In a cell with a colliding block that the filter does not let
 * through, the shape of the block itself decides (the game's collisionRayTrace: slab, stair,
 * fence). Like the game's aiming ray, blocks without a collision box (flower, tall grass) and
 * liquids stop nothing. Without a filter, it gives the same block as the game's aiming.
 */
final class BlockRay {

    /** The block that stopped the ray. */
    static final class Hit {
        final String id;
        final int x;
        final int y;
        final int z;
        final double distance;

        Hit(String id, int x, int y, int z, double distance) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.z = z;
            this.distance = distance;
        }
    }

    private static final int MAX_Y = 255;

    private BlockRay() {}

    static Hit first(World world, Vec3 from, Vec3 to, Targeting.BlockFilter pass) {
        double x0 = from.xCoord;
        double y0 = from.yCoord;
        double z0 = from.zCoord;
        double dx = to.xCoord - x0;
        double dy = to.yCoord - y0;
        double dz = to.zCoord - z0;
        int x = floor(x0);
        int y = floor(y0);
        int z = floor(z0);
        int stepX = dx > 0 ? 1 : dx < 0 ? -1 : 0;
        int stepY = dy > 0 ? 1 : dy < 0 ? -1 : 0;
        int stepZ = dz > 0 ? 1 : dz < 0 ? -1 : 0;
        // At what fraction of the segment (0 to 1) the ray crosses the next cell wall on each axis,
        // and how far it travels between one wall and the next.
        double tMaxX = tMax(stepX, x, x0, dx);
        double tMaxY = tMax(stepY, y, y0, dy);
        double tMaxZ = tMax(stepZ, z, z0, dz);
        double tDeltaX = tDelta(stepX, dx);
        double tDeltaY = tDelta(stepY, dy);
        double tDeltaZ = tDelta(stepZ, dz);
        int budget = (int) (Math.abs(dx) + Math.abs(dy) + Math.abs(dz)) + 4;
        for (int i = 0; i <= budget; i++) {
            Hit h = test(world, x, y, z, from, to, pass);
            if (h != null) {
                return h;
            }
            if (tMaxX < tMaxY && tMaxX < tMaxZ) {
                if (tMaxX > 1) {
                    break;
                }
                x += stepX;
                tMaxX += tDeltaX;
            } else if (tMaxY < tMaxZ) {
                if (tMaxY > 1) {
                    break;
                }
                y += stepY;
                tMaxY += tDeltaY;
            } else {
                if (tMaxZ > 1) {
                    break;
                }
                z += stepZ;
                tMaxZ += tDeltaZ;
            }
        }
        return null;
    }

    private static double tMax(int step, int cell, double origin, double delta) {
        return step == 0
                ? Double.POSITIVE_INFINITY
                : (step > 0 ? cell + 1 - origin : origin - cell) / Math.abs(delta);
    }

    private static double tDelta(int step, double delta) {
        return step == 0 ? Double.POSITIVE_INFINITY : 1.0 / Math.abs(delta);
    }

    private static Hit test(
            World world, int x, int y, int z, Vec3 from, Vec3 to, Targeting.BlockFilter pass) {
        if (y < 0 || y > MAX_Y) {
            return null;
        }
        BlockPos pos = new BlockPos(x, y, z);
        IBlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        if (block.getMaterial() == Material.air
                || !block.canCollideCheck(state, false)
                || block.getCollisionBoundingBox(world, pos, state) == null) {
            return null;
        }
        Object name = Block.blockRegistry.getNameForObject(block);
        String id = name == null ? "" : name.toString();
        if (pass != null && pass.passes(id)) {
            return null;
        }
        MovingObjectPosition m = block.collisionRayTrace(world, pos, from, to);
        return m == null ? null : new Hit(id, x, y, z, from.distanceTo(m.hitVec));
    }

    private static int floor(double v) {
        return (int) Math.floor(v);
    }
}
