/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Breno Xavier
 *
 * Part of the Stein Loader API, licensed under the MIT License (see LICENSE-API).
 */

package dev.xavier.stein.loader.api;

import dev.xavier.stein.loader.api.spi.Host;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Aim and attack: who is in the line of aim, and the attack sent by the mod.
 *
 * <p>The pieces combine. <b>Protecting</b> an entity: cancel in {@link SteinMod#onAttackEntity}
 * (and {@link SteinMod#onInteractEntity}). <b>Hitting through</b>: cancel the hit on the ally, look
 * at the {@link #raycast()} and send {@link #attack} to the first one in line that is not an ally.
 * The attack here is the game's ({@code PlayerControllerMP.attackEntity}): the server receives the
 * same packet as a click, and checks the reach.
 *
 * <p>Call from the game thread.
 */
@Experimental(since = "0.1.15")
public final class Targeting {

    private Targeting() {}

    /** An entity crossed by the aim ray. */
    public static final class Hit {
        /** The entity (Entity). */
        public final Object entity;

        /**
         * Distance from the eye to where the ray enters its box; 0 if the eye is inside the box.
         */
        public final double distance;

        /** Where the ray enters the box (the eye, if it is inside). */
        public final double x;

        public final double y;
        public final double z;

        public Hit(Object entity, double distance, double x, double y, double z) {
            this.entity = entity;
            this.distance = distance;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    /** The result of a {@link #raycast}. */
    public static final class Ray {
        /** The entities on the ray, from nearest to farthest (an unmodifiable list). */
        public final List<Hit> hits;

        /**
         * Distance to the first block the ray hits, or {@link Double#POSITIVE_INFINITY} if none
         * within reach. In the game, an entity behind a block is not aimed at: compare {@code
         * hit.distance < blockDistance}.
         */
        public final double blockDistance;

        /** The reach used. */
        public final double reach;

        /**
         * The block that stopped the ray ({@code "minecraft:stone"}), or null if none stopped it
         * within reach.
         */
        @Experimental(since = "0.1.16")
        public final String blockId;

        /** Where that block is (valid only when {@link #blockId} != null). */
        @Experimental(since = "0.1.16")
        public final int blockX;

        @Experimental(since = "0.1.16")
        public final int blockY;

        @Experimental(since = "0.1.16")
        public final int blockZ;

        public Ray(List<Hit> hits, double blockDistance, double reach) {
            this(hits, blockDistance, reach, null, 0, 0, 0);
        }

        @Experimental(since = "0.1.16")
        public Ray(
                List<Hit> hits,
                double blockDistance,
                double reach,
                String blockId,
                int blockX,
                int blockY,
                int blockZ) {
            this.hits = Collections.unmodifiableList(hits);
            this.blockDistance = blockDistance;
            this.reach = reach;
            this.blockId = blockId;
            this.blockX = blockX;
            this.blockY = blockY;
            this.blockZ = blockZ;
        }

        /** The first entity before any block, or null. */
        public Hit first() {
            return hits.isEmpty() || hits.get(0).distance >= blockDistance ? null : hits.get(0);
        }
    }

    /**
     * Which blocks the ray passes through instead of stopping. It receives the block id ({@code
     * "minecraft:leaves"}); combine with {@link #or}. Blocks without collision (flower, tall grass,
     * vine) and liquids never stop the ray, as in the game.
     */
    @Experimental(since = "0.1.16")
    @FunctionalInterface
    public interface BlockFilter {
        boolean passes(String blockId);

        default BlockFilter or(BlockFilter other) {
            return id -> passes(id) || other.passes(id);
        }
    }

    /**
     * Foliage: leaves, bushes, plants, mushrooms and lily pads — what only gets in the way of the
     * view.
     */
    @Experimental(since = "0.1.16")
    public static final BlockFilter FOLIAGE =
            ids(
                    "leaves",
                    "leaves2",
                    "tallgrass",
                    "deadbush",
                    "yellow_flower",
                    "red_flower",
                    "double_plant",
                    "sapling",
                    "vine",
                    "brown_mushroom",
                    "red_mushroom",
                    "waterlily",
                    "wheat",
                    "carrots",
                    "potatoes",
                    "nether_wart",
                    "reeds",
                    "melon_stem",
                    "pumpkin_stem",
                    "web");

    /** Glass: blocks, panes and the coloured versions. */
    @Experimental(since = "0.1.16")
    public static final BlockFilter GLASS =
            ids("glass", "stained_glass", "glass_pane", "stained_glass_pane");

    /** A filter by ids (with or without the "minecraft:"). */
    @Experimental(since = "0.1.16")
    public static BlockFilter ids(String... ids) {
        Set<String> set = new HashSet<>();
        for (String id : ids) {
            set.add(id.indexOf(':') >= 0 ? id : "minecraft:" + id);
        }
        return set::contains;
    }

    /**
     * A search along the line of aim with options, for {@link #raycast(Query)}. Left untouched, it
     * is {@link #raycast(double)} with the attack reach.
     *
     * <pre>
     * Targeting.Hit target = Targeting.raycast(Targeting.query()
     *         .reach(32).living().margin(0.3).passThrough(Targeting.FOLIAGE)).first();
     * </pre>
     */
    @Experimental(since = "0.1.16")
    public static final class Query {
        /** Reach in blocks from the eye; NaN = the game's attack reach. */
        public double reach = Double.NaN;

        /** Only living entities (player, mob, animal) that have not died. */
        public boolean living;

        /** Without the invisible ones. */
        public boolean visibleOnly;

        /**
         * Each entity's box enlarged by this on every side; NaN = the game's collision border
         * (0.1).
         */
        public double margin = Double.NaN;

        /** Blocks the ray passes through; null = none. */
        public BlockFilter passThrough;

        /** Removes from the list whoever is behind the block that stopped the ray. */
        public boolean cutAtBlock;

        /** Never included (the camera is always outside). */
        public final List<Object> excluded = new ArrayList<>();

        public Query reach(double blocks) {
            reach = blocks;
            return this;
        }

        public Query living() {
            living = true;
            return this;
        }

        public Query visibleOnly() {
            visibleOnly = true;
            return this;
        }

        public Query margin(double blocks) {
            margin = blocks;
            return this;
        }

        public Query passThrough(BlockFilter filter) {
            passThrough = passThrough == null ? filter : passThrough.or(filter);
            return this;
        }

        public Query cutAtBlock() {
            cutAtBlock = true;
            return this;
        }

        public Query exclude(Object entity) {
            if (entity != null) {
                excluded.add(entity);
            }
            return this;
        }
    }

    /** A new search, with the game's defaults. */
    @Experimental(since = "0.1.16")
    public static Query query() {
        return new Query();
    }

    /**
     * All the entities on the line of aim up to {@code reach} blocks from the eye, in order of
     * distance — the same ones the game would consider for aiming (those that collide, no
     * spectators; each one's box with the collision border), at the frame's interpolated position.
     * Blocks do not cut the list: see {@link Ray#blockDistance}.
     */
    public static Ray raycast(double reach) {
        return Host.INSTANCE.raycast(reach);
    }

    /**
     * The search with options ({@link Query}): living only, a larger box for distant targets,
     * blocks that do not stop the ray. The ray walks block by block, and each block with collision
     * is tested by its shape (slab, stairs, fence), as in the game.
     */
    @Experimental(since = "0.1.16")
    public static Ray raycast(Query query) {
        return Host.INSTANCE.raycastQuery(query);
    }

    /** {@link #raycast(double)} with the game's attack reach ({@link #attackReach}). */
    public static Ray raycast() {
        return raycast(attackReach());
    }

    /** The game's entity attack reach: 3 blocks, 6 in creative. */
    public static double attackReach() {
        return Host.INSTANCE.attackReach();
    }

    /** The entity the game is aiming at right now (the objectMouseOver), or null. */
    public static Object pointedEntity() {
        return Host.INSTANCE.pointedEntity();
    }

    /**
     * Hits the entity like the game's click, without swinging the arm ({@link #swing}) — the click
     * does both. It does not go through {@link SteinMod#onAttackEntity}: it is for whoever
     * cancelled a hit there and sends another.
     *
     * @return false without a player or without an entity
     */
    public static boolean attack(Object entity) {
        return Host.INSTANCE.attack(entity);
    }

    /**
     * Swings the player's arm (the server sees the animation), as the click does before the hit.
     */
    public static void swing() {
        Host.INSTANCE.swing();
    }

    /**
     * Interacts with the entity like the game's right click (mount, trade...). It does not go
     * through {@link SteinMod#onInteractEntity}.
     *
     * @return what the game returns: true if the interaction happened
     */
    public static boolean interact(Object entity) {
        return Host.INSTANCE.interact(entity);
    }
}
