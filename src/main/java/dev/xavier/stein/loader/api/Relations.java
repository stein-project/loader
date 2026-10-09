/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Breno Xavier
 *
 * Part of the Stein Loader API, licensed under the MIT License (see LICENSE-API).
 */

package dev.xavier.stein.loader.api;

import dev.xavier.stein.loader.api.spi.Host;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Who is an ally and who is an enemy, in one place, for all mods. Only data and notices: nothing
 * here protects, hides or paints anyone — that is up to whoever queries it.
 *
 * <p>Three ways to state the relation, each with a source ({@code source}, usually the mod id), so
 * each can erase only what is its own:
 *
 * <ul>
 *   <li><b>Player</b>: {@link #setPlayer}.
 *   <li><b>Group</b>: {@link #setGroup} — applies to everyone in that group ({@link
 *       Players#group}): clan, team, guild, whatever the name. {@link Relation#GROUP} marks your
 *       own group.
 *   <li><b>Provider</b>: {@link #register} — a mod that decides on its own (URL lists, a server's
 *       rules).
 * </ul>
 *
 * <p>Order of precedence in {@link #of}: yourself ({@link Relation#SELF}); the registered player
 * (across sources, enemy beats ally, which beats neutral); the providers, in registration order,
 * the first one to answer; the registered group; and, if there is any registration or provider,
 * {@link Relation#NEUTRAL} — with nothing at all, {@link Relation#NONE}.
 *
 * <p>It can be queried per player every frame: the answer is cached until the next {@link #version}
 * (or one second, because the group comes from the scoreboard). When something changes, the mods
 * receive {@link SteinMod#onRelationsChanged}.
 */
@Experimental(since = "0.1.16")
public final class Relations {

    /** A mod that decides relations on its own. */
    @FunctionalInterface
    public interface Provider {
        /**
         * The relation this mod sees with the player.
         *
         * @param nick the player
         * @param group the player's group ({@link Players#group}), or null
         * @return the relation, or null to leave it to the others
         */
        Relation relationOf(String nick, String group);
    }

    /** How long a computed answer is reused (also by Players). */
    static final long CACHE_NANOS = 1_000_000_000L;

    private static final int WHITE = 0xFFFFFF;

    private static final Map<String, Map<String, Relation>> PLAYERS =
            new HashMap<>(); // lowercase nick -> source -> r
    private static final Map<String, Map<String, Relation>> GROUPS =
            new HashMap<>(); // uppercase group name -> source -> r
    private static final Map<String, Provider> PROVIDERS = new LinkedHashMap<>();
    private static final Map<Relation, Integer> COLORS = new EnumMap<>(Relation.class);
    private static final Map<String, Relation> CACHE = new HashMap<>();
    private static int version;
    private static int cacheVersion = -1;
    private static long cacheAt;

    static {
        COLORS.put(Relation.NONE, WHITE);
        COLORS.put(Relation.SELF, WHITE);
        COLORS.put(Relation.GROUP, 0x55FF55);
        COLORS.put(Relation.ALLY, 0x5555FF);
        COLORS.put(Relation.ENEMY, 0xFF5555);
        COLORS.put(Relation.NEUTRAL, WHITE);
    }

    private Relations() {}

    // --- querying ---

    /** The relation with the player. */
    public static Relation of(String nick) {
        if (nick == null) {
            return Relation.NONE;
        }
        long now = System.nanoTime();
        if (cacheVersion != version || now - cacheAt > CACHE_NANOS) {
            CACHE.clear();
            cacheVersion = version;
            cacheAt = now;
        }
        Relation r = CACHE.get(nick);
        if (r == null) {
            r = resolve(nick);
            CACHE.put(nick, r);
        }
        return r;
    }

    /** The relation with the entity: a player by nick; any other entity, {@link Relation#NONE}. */
    public static Relation of(Object entity) {
        return of(Host.INSTANCE.playerNameOf(entity));
    }

    /** The color chosen for the relation (RGB, no alpha). */
    public static int color(Relation r) {
        return COLORS.getOrDefault(r, WHITE);
    }

    /**
     * Goes up on every change (registration, provider, color, {@link #changed}): whoever caches
     * something checks against it.
     */
    public static int version() {
        return version;
    }

    /** The players registered with this relation (by any source), in lowercase. */
    public static List<String> players(Relation r) {
        List<String> out = new ArrayList<>();
        PLAYERS.forEach(
                (nick, by) -> {
                    if (strongest(by) == r) {
                        out.add(nick);
                    }
                });
        return out;
    }

    /** The groups registered with this relation, in uppercase. */
    public static List<String> groups(Relation r) {
        List<String> out = new ArrayList<>();
        GROUPS.forEach(
                (tag, by) -> {
                    if (strongest(by) == r) {
                        out.add(tag);
                    }
                });
        return out;
    }

    // --- stating ---

    /** Registers (or, with null, removes) the relation with the player, by source. */
    public static void setPlayer(String source, String nick, Relation r) {
        if (nick != null && put(PLAYERS, nick.toLowerCase(Locale.ROOT), source, r)) {
            changed();
        }
    }

    /**
     * Registers (or, with null, removes) the relation with the group (free-form name, case
     * insensitive), by source. {@link Relation#GROUP} = your own group.
     */
    public static void setGroup(String source, String tag, Relation r) {
        if (tag != null && put(GROUPS, tag.trim().toUpperCase(Locale.ROOT), source, r)) {
            changed();
        }
    }

    /** Removes everything the source registered (players and groups). */
    public static void clear(String source) {
        boolean any = removeSource(PLAYERS, source) | removeSource(GROUPS, source);
        if (any) {
            changed();
        }
    }

    /** Registers (or replaces) the source's provider. */
    public static void register(String source, Provider provider) {
        PROVIDERS.put(source, provider);
        changed();
    }

    /**
     * Removes the source's provider ({@link #register}).
     *
     * @param source the name given to {@link #register}
     */
    public static void unregister(String source) {
        if (PROVIDERS.remove(source) != null) {
            changed();
        }
    }

    /** Changes the color of a relation (whoever lets the player choose calls this). */
    public static void setColor(Relation r, int rgb) {
        Integer old = COLORS.put(r, rgb & 0xFFFFFF);
        if (old == null || old != (rgb & 0xFFFFFF)) {
            changed();
        }
    }

    /**
     * Signals that a provider's answers changed (a list was downloaded, an option was toggled): the
     * cached answer is dropped and the mods receive {@link SteinMod#onRelationsChanged} at the end
     * of the tick.
     */
    public static void changed() {
        version++;
        Host.INSTANCE.relationsChanged();
    }

    // --- internals ---

    private static Relation resolve(String nick) {
        String self = Players.self();
        if (self != null && self.equalsIgnoreCase(nick)) {
            return Relation.SELF;
        }
        Map<String, Relation> by = PLAYERS.get(nick.toLowerCase(Locale.ROOT));
        if (by != null && !by.isEmpty()) {
            return strongest(by);
        }
        String group = Players.group(nick);
        for (Provider p : PROVIDERS.values()) {
            try {
                Relation r = p.relationOf(nick, group);
                if (r != null) {
                    return r;
                }
            } catch (RuntimeException e) {
                Log.of("relations").error("relation provider failed", e);
            }
        }
        if (group != null) {
            Map<String, Relation> c = GROUPS.get(group.toUpperCase(Locale.ROOT));
            if (c != null && !c.isEmpty()) {
                return strongest(c);
            }
        }
        return PLAYERS.isEmpty() && GROUPS.isEmpty() && PROVIDERS.isEmpty()
                ? Relation.NONE
                : Relation.NEUTRAL;
    }

    /** Across sources: your group, then enemy, ally, neutral. */
    private static Relation strongest(Map<String, Relation> by) {
        Relation best = null;
        for (Relation r : by.values()) {
            if (best == null || rank(r) > rank(best)) {
                best = r;
            }
        }
        return best == null ? Relation.NONE : best;
    }

    private static int rank(Relation r) {
        return switch (r) {
            case SELF -> 5;
            case GROUP -> 4;
            case ENEMY -> 3;
            case ALLY -> 2;
            case NEUTRAL -> 1;
            case NONE -> 0;
        };
    }

    private static boolean put(
            Map<String, Map<String, Relation>> map, String key, String source, Relation r) {
        if (r == null) {
            Map<String, Relation> by = map.get(key);
            if (by == null || by.remove(source) == null) {
                return false;
            }
            if (by.isEmpty()) {
                map.remove(key);
            }
            return true;
        }
        return map.computeIfAbsent(key, k -> new HashMap<>()).put(source, r) != r;
    }

    private static boolean removeSource(Map<String, Map<String, Relation>> map, String source) {
        boolean any = false;
        for (var it = map.values().iterator(); it.hasNext(); ) {
            Map<String, Relation> by = it.next();
            any |= by.remove(source) != null;
            if (by.isEmpty()) {
                it.remove();
            }
        }
        return any;
    }
}
