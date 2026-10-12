/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.loader;

import static dev.xavier.stein.loader.GameOps.mc;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.scoreboard.ScorePlayerTeam;

import java.util.ArrayList;
import java.util.List;

/** The API's players (Players) against the game: the own player, the tab list, teams and pings. */
final class PlayerOps {

    private PlayerOps() {}

    static EntityPlayerSP player() {
        return mc().thePlayer;
    }

    static Object self() {
        return player();
    }

    static String selfName() {
        EntityPlayerSP p = player();
        return p == null ? null : p.getName();
    }

    private static NetworkPlayerInfo info(String nick) {
        return PlayerInfos.get(nick); // index by nick: the game's search walks the whole list
    }

    private static ScorePlayerTeam team(String nick) {
        Minecraft mc = mc();
        return mc.theWorld == null || nick == null
                ? null
                : mc.theWorld.getScoreboard().getPlayersTeam(nick);
    }

    static List<String> online() {
        NetHandlerPlayClient net = mc().getNetHandler();
        List<String> out = new ArrayList<>();
        if (net != null) {
            for (NetworkPlayerInfo i : net.getPlayerInfoMap()) {
                out.add(i.getGameProfile().getName());
            }
        }
        return out;
    }

    static String teamName(String nick) {
        ScorePlayerTeam t = team(nick);
        return t == null ? null : t.getRegisteredName();
    }

    static String prefix(String nick) {
        ScorePlayerTeam t = team(nick);
        return t == null ? "" : t.getColorPrefix();
    }

    static String suffix(String nick) {
        ScorePlayerTeam t = team(nick);
        return t == null ? "" : t.getColorSuffix();
    }

    static String tabName(String nick) {
        NetworkPlayerInfo i = info(nick);
        return i == null ? null : mc().ingameGUI.getTabList().getPlayerName(i);
    }

    static int ping(String nick) {
        NetworkPlayerInfo i = info(nick);
        return i == null ? -1 : i.getResponseTime();
    }

    static String nameOf(Object entity) {
        return entity instanceof EntityPlayer ep ? ep.getName() : null;
    }
}
