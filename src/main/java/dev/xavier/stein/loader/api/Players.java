/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Breno Xavier
 *
 * Part of the Stein Loader API, licensed under the MIT License (see LICENSE-API).
 */

package dev.xavier.stein.loader.api;

import dev.xavier.stein.loader.api.spi.Host;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * The server's players as the client knows them: the Tab list, each one's scoreboard team and the
 * group.
 *
 * <p>Applies to whoever is online in the Tab, near or far; whoever is not in the Tab (NPC, player
 * hidden by the server) answers null/empty. Call from the game thread.
 */
@Experimental(since = "0.1.16")
public final class Players {

    private static final Pattern NICK = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final Pattern COLOR_CODE = Pattern.compile("§.");
    private static final Map<String, Function<String, String>> RESOLVERS = new LinkedHashMap<>();
    private static final Map<String, String> GROUPS = new HashMap<>();
    private static long groupsAt;

    private Players() {}

    /** Your nick, or null outside a world. */
    public static String self() {
        return Host.INSTANCE.selfName();
    }

    /** The nicks in the Tab list. */
    public static List<String> online() {
        return Host.INSTANCE.onlinePlayers();
    }

    /** The name of the player's scoreboard team, or null. */
    public static String team(String nick) {
        return Host.INSTANCE.playerTeam(nick);
    }

    /** The team prefix (with the color codes), or "". */
    public static String prefix(String nick) {
        return Host.INSTANCE.playerPrefix(nick);
    }

    /** The team suffix (with the color codes), or "". */
    public static String suffix(String nick) {
        return Host.INSTANCE.playerSuffix(nick);
    }

    /**
     * The name as the Tab shows it (the server's, or prefix + nick + suffix), with the color codes;
     * null if not present.
     */
    public static String tabName(String nick) {
        return Host.INSTANCE.playerTabName(nick);
    }

    /**
     * Only the name the <b>server</b> sends to the Tab (with the color codes), or null when it
     * sends none and the Tab builds the name from the team. That is where many servers put the
     * clan: "[ABC] Nick".
     */
    public static String displayName(String nick) {
        return Host.INSTANCE.playerDisplayName(nick);
    }

    /**
     * The nick of a Tab row (the NetworkPlayerInfo that onPlayerListName and onPlayerList receive),
     * or null.
     */
    public static String nickOf(Object playerInfo) {
        return Host.INSTANCE.playerInfoNick(playerInfo);
    }

    /** The player's ping in the Tab, in ms, or -1. */
    public static int ping(String nick) {
        return Host.INSTANCE.playerPing(nick);
    }

    /**
     * The player's group (clan, team, guild — whatever name the server uses), or null. Whoever
     * knows the server's convention registers a {@link #groupResolver}; with none that answers, the
     * most common way applies: what is between brackets in the team prefix ("§b[ABC] " -> "ABC")
     * or, failing that, in the name the server sends to the Tab ("[ABC] Nick"). Cached for one
     * second: it can be asked per player on every frame without sweeping the Tab.
     */
    public static String group(String nick) {
        if (nick == null) {
            return null;
        }
        long now = System.nanoTime();
        if (now - groupsAt > Relations.CACHE_NANOS) {
            GROUPS.clear();
            groupsAt = now;
        }
        if (GROUPS.containsKey(nick)) {
            return GROUPS.get(nick);
        }
        String tag = null;
        for (Function<String, String> r : RESOLVERS.values()) {
            try {
                tag = r.apply(nick);
            } catch (RuntimeException e) {
                Log.of("players").error("group resolver failed", e);
            }
            if (tag != null) {
                break;
            }
        }
        if (tag != null && tag.isEmpty()) {
            tag = null; // whoever knows the server decided: no group
        } else if (tag == null) {
            tag = bracketTag(prefix(nick));
        }
        if (tag == null) {
            tag = bracketTag(tabName(nick));
        }
        GROUPS.put(nick, tag);
        return tag;
    }

    /**
     * Registers (or, with null, removes) whoever works out a player's group on this server — a mod
     * that knows its convention (a team with a special name, a tag rule). Returns the group; "" for
     * "no group" (decided: the common way is not even tried); null to leave it to the others — in
     * registration order, the first that answers wins, and only with none does the common way apply
     * ({@link #group}).
     */
    public static void groupResolver(String source, Function<String, String> resolver) {
        if (resolver == null) {
            RESOLVERS.remove(source);
        } else {
            RESOLVERS.put(source, resolver);
        }
        GROUPS.clear();
        Relations.changed();
    }

    /**
     * The bracketed tag of a text, without colors and spaces ("§b[ ABC ] Nick" -> "ABC"), or null.
     */
    public static String bracketTag(String text) {
        if (text == null) {
            return null;
        }
        String clean = COLOR_CODE.matcher(text).replaceAll("").trim();
        int open = clean.indexOf('[');
        int close = clean.indexOf(']', open + 1);
        if (open < 0 || close <= open + 1) {
            return null;
        }
        String t = clean.substring(open + 1, close).trim();
        return t.isEmpty() ? null : t;
    }

    /** A possible Minecraft nick (letters, digits and _, 1 to 16)? */
    public static boolean isValidNick(String s) {
        return s != null && NICK.matcher(s).matches();
    }

    /** A row of the Tab list, read in one go ({@link #list}). */
    public static final class Entry {
        /** The player's nick. */
        public final String nick;

        /**
         * As the Tab shows it (the server's name, or the team's prefix + nick + suffix), with
         * colors.
         */
        public final String tabName;

        /** The scoreboard team's name, or "". */
        public final String team;

        /** Ms; 0 while the server has not measured. */
        public final int ping;

        /** In spectator mode. */
        public final boolean spectator;

        /**
         * The server sent a skin (otherwise the game shows the default: server without
         * authentication).
         */
        public final boolean hasSkin;

        /**
         * A row of the Tab list.
         *
         * @param nick the nick
         * @param tabName the name as the Tab shows it, with colors
         * @param team the scoreboard team name, or ""
         * @param ping the ping in ms (0 = not measured yet)
         * @param spectator in spectator mode
         * @param hasSkin the server sent a skin
         */
        public Entry(
                String nick,
                String tabName,
                String team,
                int ping,
                boolean spectator,
                boolean hasSkin) {
            this.nick = nick;
            this.tabName = tabName;
            this.team = team;
            this.ping = ping;
            this.spectator = spectator;
            this.hasSkin = hasSkin;
        }
    }

    /**
     * The whole Tab list in a single pass, for whoever draws or sorts it. With hundreds of players,
     * asking {@link #tabName}, {@link #ping} and {@link #team} nick by nick costs one lookup per
     * player; here it is a new list per call — keep it and rebuild it per tick, not per frame.
     */
    @Experimental(since = "0.1.16")
    public static List<Entry> list() {
        return Host.INSTANCE.playerList();
    }

    /**
     * How many are in the Tab list (without building the list): to know when to rebuild what was
     * built with {@link #list}.
     */
    @Experimental(since = "0.1.16")
    public static int count() {
        return Host.INSTANCE.playerCount();
    }

    /** The text the server puts above the Tab list (with colors), or null. */
    @Experimental(since = "0.1.16")
    public static String tabHeader() {
        return Host.INSTANCE.tabText(true);
    }

    /** The text below the Tab list, or null. */
    @Experimental(since = "0.1.16")
    public static String tabFooter() {
        return Host.INSTANCE.tabText(false);
    }

    /**
     * The Tab list scoreboard (the number column, like health or kills) of that player, or null
     * without a scoreboard in the list.
     */
    @Experimental(since = "0.1.16")
    public static String listScore(String nick) {
        return Host.INSTANCE.listScore(nick);
    }
}
