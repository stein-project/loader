/*
 * Copyright (c) 2026 Breno Xavier. All rights reserved.
 * Proprietary software: see the LICENSE file at the root of this repository.
 */

package dev.xavier.stein.loader;

import static dev.xavier.stein.loader.GameOps.mc;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.NetworkManager;

import java.net.SocketAddress;
import java.util.Locale;

/**
 * The server connection for the API (Server): the current address and name, the last server joined
 * and connecting to another one.
 */
final class ServerOps {

    private static final String DEFAULT_PORT_SUFFIX = ":25565";

    private ServerOps() {}

    /**
     * The address as the game compares it: lowercase and without the default port ("home.com" and
     * "home.com:25565" are the same).
     */
    static String host(String ip) {
        String s = (ip == null ? "" : ip).trim().toLowerCase(Locale.ROOT);
        return s.endsWith(DEFAULT_PORT_SUFFIX)
                ? s.substring(0, s.length() - DEFAULT_PORT_SUFFIX.length())
                : s;
    }

    static String lastServer = "";

    /** The current server address (lowercase, without the default port), or "" outside a server. */
    static String serverAddress() {
        Minecraft mc = mc();
        if (mc.theWorld == null || mc.isSingleplayer()) {
            return "";
        }
        ServerData d = mc.getCurrentServerData();
        String ip = d == null ? null : d.serverIP;
        if (ip == null) {
            // Joined through the command line (--server does not create the ServerData): the
            // connection's own address.
            NetHandlerPlayClient h = mc.getNetHandler();
            NetworkManager m = h == null ? null : h.getNetworkManager();
            SocketAddress a = m == null ? null : m.getRemoteAddress();
            ip = a == null ? null : a.toString();
            if (ip != null && ip.lastIndexOf('/') >= 0) {
                ip = ip.substring(ip.lastIndexOf('/') + 1);
            }
        }
        return host(ip);
    }

    static String serverName() {
        ServerData d = mc().getCurrentServerData();
        return d == null || d.serverName == null ? "" : d.serverName;
    }

    static void connect(String address) {
        Minecraft mc = mc();
        GameOps.connecting = host(address);
        if (mc.theWorld != null) {
            mc.theWorld.sendQuittingDisconnectingPacket();
            mc.loadWorld(null);
        }
        GuiScreen back = new GuiMultiplayer(new GuiMainMenu());
        mc.displayGuiScreen(new GuiConnecting(back, mc, new ServerData("", address, false)));
    }
}
