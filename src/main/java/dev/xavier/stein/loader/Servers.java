/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.loader;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The Stein Labs servers ({@link Remote}) in the multiplayer list of whoever uses the Loader.
 *
 * <p>One rule: <b>nothing of the player's is deleted</b>. The published server goes first; if it
 * was already in the list (same address), it moves to the top and takes the current name — whoever
 * added it by hand does not end up with two copies, nor with an old name. Everything else stays in
 * the order it was in, right below.
 *
 * <p>Written through the game's own {@code ServerList}, not by hand into {@code servers.dat}: it is
 * the one that knows the file format, so what we write is exactly what the game writes. If nothing
 * would change, the file is not even rewritten.
 */
final class Servers {

    private Servers() {}

    static void apply(List<Remote.Server> wanted) {
        if (wanted.isEmpty()) {
            return;
        }
        try {
            ServerList list = new ServerList(Minecraft.getMinecraft());
            List<ServerData> rest = new ArrayList<>();
            for (int i = 0; i < list.countServers(); i++) {
                rest.add(list.getServerData(i));
            }
            String before = signature(rest);
            List<ServerData> head = new ArrayList<>();
            for (Remote.Server s : wanted) {
                head.add(take(rest, s));
            }
            head.addAll(rest);
            if (signature(head).equals(before)) {
                return; // already like this: no reason to rewrite the file on every start
            }
            for (int i = list.countServers() - 1; i >= 0; i--) {
                list.removeServerData(i);
            }
            for (ServerData d : head) {
                list.addServerData(d);
            }
            list.saveServerList();
            System.out.println(
                    "[loader] server list: " + wanted.size() + " from Stein Labs on top");
        } catch (Exception | LinkageError e) {
            System.out.println("[loader] server list unchanged: " + e);
        }
    }

    /**
     * Takes the server with the same address out of the list (giving it the current name), or
     * creates a new one.
     */
    private static ServerData take(List<ServerData> rest, Remote.Server s) {
        for (Iterator<ServerData> it = rest.iterator(); it.hasNext(); ) {
            ServerData d = it.next();
            if (same(d.serverIP, s.ip)) {
                it.remove();
                d.serverName = s.name;
                return d;
            }
        }
        return new ServerData(s.name, s.ip, false);
    }

    private static boolean same(String a, String b) {
        return Remote.host(a).equals(Remote.host(b));
    }

    /** Name and address of each one, in order: this is what decides whether anything changed. */
    private static String signature(List<ServerData> list) {
        StringBuilder sb = new StringBuilder();
        for (ServerData d : list) {
            sb.append(d.serverName).append('\u0000').append(d.serverIP).append('\n');
        }
        return sb.toString();
    }
}
