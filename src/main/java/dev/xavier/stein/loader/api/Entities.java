/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Breno Xavier
 *
 * Part of the Stein Loader API, licensed under the MIT License (see LICENSE-API).
 */

package dev.xavier.stein.loader.api;

import dev.xavier.stein.loader.api.spi.Host;

import java.util.List;

/**
 * Health and state of entities, for anyone showing damage, health bars or indicators. The values
 * are the ones the client has: other entities' health is what the server syncs (see {@link
 * SteinMod#onHealthChanged}). An entity that is not alive (a dropped item, an arrow) gives 0. Call
 * from the game thread.
 */
@Experimental(since = "0.1.15")
public final class Entities {

    // Axis codes of Host.entityPos.
    private static final int AXIS_X = 0;
    private static final int AXIS_Y = 1;
    private static final int AXIS_Z = 2;
    private static final int AXIS_YAW = 3;

    private Entities() {}

    /** The player itself (EntityPlayerSP), or null outside a world. */
    public static Object self() {
        return Host.INSTANCE.self();
    }

    /** Is it a living entity (EntityLivingBase: player, mob, animal)? */
    public static boolean isLiving(Object entity) {
        return Host.INSTANCE.isLiving(entity);
    }

    /** Health, in half hearts (20 = ten hearts). */
    public static float health(Object entity) {
        return Host.INSTANCE.health(entity);
    }

    /** The maximum health. */
    public static float maxHealth(Object entity) {
        return Host.INSTANCE.maxHealth(entity);
    }

    /** Absorption (the yellow hearts). For others, only players' reaches the client. */
    public static float absorption(Object entity) {
        return Host.INSTANCE.absorption(entity);
    }

    /** The worn armor points (0-20), from the armor the client sees. */
    public static int armor(Object entity) {
        return Host.INSTANCE.armor(entity);
    }

    /** How much of the damage animation is left, in ticks: 10 right after the hit, 0 when over. */
    public static int hurtTime(Object entity) {
        return Host.INSTANCE.hurtTime(entity);
    }

    /**
     * The players that exist in the world right now (close enough for the server to send), you
     * included; empty outside a world.
     */
    @Experimental(since = "0.1.16")
    public static List<Object> players() {
        return Host.INSTANCE.worldPlayers();
    }

    /**
     * The player with that nickname in the world, or null (in the Tab list but far away, or not
     * there).
     */
    @Experimental(since = "0.1.16")
    public static Object player(String nick) {
        return Host.INSTANCE.playerEntity(nick);
    }

    /** Is it a player (EntityPlayer, you included)? */
    @Experimental(since = "0.1.16")
    public static boolean isPlayer(Object entity) {
        return Host.INSTANCE.isPlayer(entity);
    }

    /**
     * The player's nickname, or the entity's name ("Zombie", or the name given with a name tag);
     * null if it is not an entity.
     */
    @Experimental(since = "0.1.16")
    public static String name(Object entity) {
        return Host.INSTANCE.entityName(entity);
    }

    /** Invisible (potion or command)? */
    @Experimental(since = "0.1.16")
    public static boolean isInvisible(Object entity) {
        return Host.INSTANCE.isInvisible(entity);
    }

    /** Distance between the two, in blocks; {@link Float#MAX_VALUE} if one is not an entity. */
    @Experimental(since = "0.1.16")
    public static float distance(Object a, Object b) {
        return Host.INSTANCE.distance(a, b);
    }

    /** What kind of entity it is, for a radar. */
    @Experimental(since = "0.1.16")
    public enum Kind {
        PLAYER,
        /** Monster: zombie, skeleton, creeper, spider, slime, ghast... */
        HOSTILE,
        /** Animal, fish and squid, bat, villager and golem. */
        PASSIVE,
        /** Dropped item, arrow, minecart, painting, and the rest. */
        OTHER
    }

    private static final Kind[] KINDS = Kind.values();

    /** All entities loaded on the client (read-only, and changes from one tick to the next). */
    @Experimental(since = "0.1.16")
    public static List<Object> all() {
        return Host.INSTANCE.worldEntities();
    }

    @Experimental(since = "0.1.16")
    public static Kind kind(Object entity) {
        return KINDS[Host.INSTANCE.entityKind(entity)];
    }

    /**
     * The type as the game calls it ("Zombie", "Villager", "Item"), or "" for a player and for what
     * is not an entity.
     */
    @Experimental(since = "0.1.16")
    public static String typeName(Object entity) {
        return Host.INSTANCE.entityType(entity);
    }

    /**
     * The position in the frame being drawn: between the previous tick's and the current one's, by
     * {@code partialTicks} (what onOverlay and HudElement receive). With 1, the last tick's.
     */
    @Experimental(since = "0.1.16")
    public static double x(Object entity, float partialTicks) {
        return Host.INSTANCE.entityPos(entity, AXIS_X, partialTicks);
    }

    /** The feet. */
    @Experimental(since = "0.1.16")
    public static double y(Object entity, float partialTicks) {
        return Host.INSTANCE.entityPos(entity, AXIS_Y, partialTicks);
    }

    @Experimental(since = "0.1.16")
    public static double z(Object entity, float partialTicks) {
        return Host.INSTANCE.entityPos(entity, AXIS_Z, partialTicks);
    }

    /**
     * Where it looks, in degrees as the game counts them: 0 = south, 90 = west, 180 = north (no
     * limit on turns).
     */
    @Experimental(since = "0.1.16")
    public static float yaw(Object entity, float partialTicks) {
        return (float) Host.INSTANCE.entityPos(entity, AXIS_YAW, partialTicks);
    }

    /** Truly alive: a living entity that has not died (health above 0 and no death animation). */
    @Experimental(since = "0.1.16")
    public static boolean isAlive(Object entity) {
        return Host.INSTANCE.entityAlive(entity);
    }

    /**
     * The name as the game shows it, with colors (the team's on a player, the name-tag one on a
     * mob). For the name without color, {@link #name}.
     */
    @Experimental(since = "0.1.16")
    public static String displayName(Object entity) {
        return Host.INSTANCE.entityDisplayName(entity);
    }

    /**
     * Where the screen is seen from: the player itself, or the entity it is spectating in spectator
     * mode. Aiming ({@link Targeting}) comes from here.
     */
    @Experimental(since = "0.1.16")
    public static Object camera() {
        return Host.INSTANCE.camera();
    }
}
