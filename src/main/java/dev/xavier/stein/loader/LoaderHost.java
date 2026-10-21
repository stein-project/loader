/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.loader;

import dev.xavier.stein.bridge.core.Log;
import dev.xavier.stein.bridge.core.SteinLog;
import dev.xavier.stein.bridge.engine.KeyBindings;
import dev.xavier.stein.bridge.hooks.GameHooks;
import dev.xavier.stein.loader.api.HudElement;
import dev.xavier.stein.loader.api.Net;
import dev.xavier.stein.loader.api.spi.Host;
import dev.xavier.stein.loader.gui.PanelScreen;
import dev.xavier.stein.loader.hud.HudGrid;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.C17PacketCustomPayload;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * The mod API wired to the Bridge and the Loader. It is the only place where {@code
 * dev.xavier.stein.loader.api} meets the Bridge: the API calls {@link Host}, and the Host is this
 * class (META-INF/services/Host). The SPI is one flat interface, so the implementation is split by
 * area along a chain of classes: {@link HostInventory} (inventory, stacks, effects), {@link
 * HostWorld} (targeting, entities, players, terrain), {@link HostRender} (drawing) and this one
 * (the mods, the context, chat, screens, keys, the account and the server). Every method is a
 * one-line delegate to the game, to GameOps, InventoryOps, TargetingOps, PlayerOps, ServerOps or
 * the other helpers of this package.
 */
public final class LoaderHost extends HostRender {

    public LoaderHost() {}

    @Override
    public float partialTicks() {
        return GameHooks.partialTicks();
    }

    @Override
    public void resetCameraSmoothing() {
        GameHooks.resetCameraSmoothing();
    }

    @Override
    public void registerKey(Object binding) {
        KeyBindings.register(binding);
    }

    @Override
    public void setProxy(Net.Proxy p) {
        dev.xavier.stein.bridge.engine.Net.set(
                p == null
                        ? null
                        : new dev.xavier.stein.bridge.engine.Net.Proxy() {
                            @Override
                            public Object address(Object destination, int port) {
                                return p.address(destination, port);
                            }

                            @Override
                            public Object wrap(Object initializer) {
                                return p.wrap(initializer);
                            }
                        });
    }

    @Override
    public boolean sendPayload(String channel, byte[] data) {
        NetHandlerPlayClient handler = Minecraft.getMinecraft().getNetHandler();
        if (handler == null) {
            return false;
        }
        handler.addToSendQueue(
                new C17PacketCustomPayload(
                        channel,
                        new PacketBuffer(io.netty.buffer.Unpooled.wrappedBuffer(data.clone()))));
        return true;
    }

    @Override
    public void registerHud(HudElement element) {
        HudGrid.register(element);
    }

    @Override
    public boolean hudEnabled(String id) {
        return HudGrid.isEnabled(id);
    }

    @Override
    public Object hudEditor(Object parent) {
        return HudGrid.editor(parent);
    }

    @Override
    public void openPanel(Object parent, String page, boolean only) {
        Loader loader = Loader.get();
        if (loader == null) {
            return;
        }
        if (only) {
            if (page != null) {
                PanelScreen.openOnly((GuiScreen) parent, page);
            }
        } else {
            PanelScreen.open(loader.pages(), (GuiScreen) parent, page);
        }
    }

    @Override
    public void closePanel() {
        PanelScreen.close();
    }

    @Override
    public boolean modLoaded(String id) {
        return loaded(id) != null;
    }

    @Override
    public String modVersion(String id) {
        ModInfo m = loaded(id);
        return m == null ? null : m.version;
    }

    @Override
    public File gameDir() {
        return Minecraft.getMinecraft().mcDataDir;
    }

    @Override
    public void log(String modId, int level, String message, Throwable error) {
        // note already writes "[id] ..." to the game log, and to the Stein log when enabled.
        SteinLog.note(
                modId,
                (level == 1 ? "warning: " : level == 2 ? "ERROR: " : "")
                        + message
                        + (error != null ? " (" + error + ")" : ""));
        if (error != null && level == 2) {
            Log.error(modId, "stack trace", error);
        }
    }

    @Override
    public void onGameThread(Runnable task) {
        GameTasks.onGameThread(task);
    }

    @Override
    public void later(int ticks, Runnable task) {
        GameTasks.later(ticks, task);
    }

    @Override
    public String modIdOf(Object modInstance) {
        Loader loader = Loader.get();
        return loader == null || modInstance == null ? null : loader.modIdOf(modInstance);
    }

    @Override
    public byte[] protect(byte[] plain) {
        return Vault.protect(plain);
    }

    @Override
    public byte[] unprotect(byte[] stored) {
        return Vault.unprotect(stored);
    }

    @Override
    public boolean secretsProtected() {
        return Vault.systemProtected();
    }

    @Override
    public <T> T readConfig(File file, Class<T> type, T defaults, String owner) {
        return JsonFiles.readConfig(file, type, defaults, owner);
    }

    @Override
    public void writeJson(File file, Object value, Class<?> type) throws IOException {
        JsonFiles.write(file, value, type);
    }

    @Override
    public Map<String, String> readStrings(File file) {
        return JsonFiles.readStrings(file);
    }

    @Override
    public void writeStrings(File file, Map<String, String> values) throws IOException {
        JsonFiles.writeStrings(file, values);
    }

    @Override
    public String accountName() {
        return Accounts.name();
    }

    @Override
    public String accountLauncherName() {
        return Accounts.launcherName();
    }

    @Override
    public boolean accountIsLauncher() {
        return Accounts.isLauncher();
    }

    @Override
    public int accountKind() {
        return Accounts.kind();
    }

    @Override
    public int accountLauncherKind() {
        return Accounts.launcherKind();
    }

    @Override
    public void accountUseOffline(String nick) {
        Accounts.useOffline(nick);
    }

    @Override
    public void accountRestore() {
        Accounts.restore();
    }

    @Override
    public String serverAddress() {
        return ServerOps.serverAddress();
    }

    @Override
    public boolean serverAllows(String feature) {
        return ServerRules.allows(ServerOps.serverAddress(), feature);
    }

    @Override
    public String serverRestrictedBy(String feature) {
        return ServerRules.restrictedBy(ServerOps.serverAddress(), feature);
    }

    @Override
    public String serverName() {
        return ServerOps.serverName();
    }

    @Override
    public int serverLatency() {
        return Latency.get();
    }

    @Override
    public String serverLast() {
        return ServerOps.lastServer;
    }

    @Override
    public void serverConnect(String address) {
        ServerOps.connect(address);
    }

    @Override
    public String translate(String key, Object[] args) {
        return I18n.format(key, args);
    }

    @Override
    public String keyName(int code) {
        return GameSettings.getKeyDisplayString(code);
    }

    @Override
    public boolean hasTranslation(String key) {
        // 1.8.9's I18n returns the key itself when it is missing in the chosen language and in
        // en_US (the fallback).
        return key != null && !key.equals(I18n.format(key));
    }

    @Override
    public String language() {
        return Minecraft.getMinecraft().getLanguageManager().getCurrentLanguage().getLanguageCode();
    }

    private static ModInfo loaded(String id) {
        Loader loader = Loader.get();
        ModInfo m = loader == null ? null : loader.byId(id);
        return m != null && m.state() == ModInfo.State.LOADED ? m : null;
    }

    @Override
    public void chatPrint(String text) {
        GameOps.print(text);
    }

    @Override
    public void chatActionBar(String text) {
        GameOps.actionBar(text);
    }

    @Override
    public void chatSend(String message) {
        GameOps.send(message);
    }

    @Override
    public Object chatText(String text) {
        return GameOps.text(text);
    }

    @Override
    public String chatPlain(Object component) {
        return GameOps.plain(component);
    }

    @Override
    public String chatFormatted(Object component) {
        return GameOps.formatted(component);
    }

    @Override
    public Object screenCurrent() {
        return GameOps.screen();
    }

    @Override
    public int screenKind(Object screen) {
        return GameOps.kind(screen);
    }

    @Override
    public int screenWidth(Object screen) {
        return GameOps.width(screen);
    }

    @Override
    public int screenHeight(Object screen) {
        return GameOps.height(screen);
    }

    @Override
    public void screenOpen(Object screen) {
        GameOps.open(screen);
    }

    @Override
    public Object buttonNew(int id, int x, int y, int width, int height, String label) {
        return GameOps.button(id, x, y, width, height, label);
    }

    @Override
    public int buttonId(Object button) {
        return GameOps.buttonId(button);
    }

    @Override
    public int buttonX(Object button) {
        return GameOps.buttonX(button);
    }

    @Override
    public int buttonY(Object button) {
        return GameOps.buttonY(button);
    }

    @Override
    public void buttonLabel(Object button, String label) {
        GameOps.buttonLabel(button, label);
    }

    @Override
    public void buttonEnabled(Object button, boolean enabled) {
        GameOps.buttonEnabled(button, enabled);
    }

    @Override
    public Object keyBind(String name, int defaultKey, String category) {
        return GameOps.bind(name, defaultKey, category);
    }

    @Override
    public Object keyGame(int which) {
        return GameOps.game(which);
    }

    @Override
    public int keyCode(Object binding) {
        return GameOps.keyCode(binding);
    }

    @Override
    public void keySet(Object binding, int key) {
        GameOps.keySet(binding, key);
    }

    @Override
    public boolean keyIsDown(Object binding) {
        return GameOps.keyIsDown(binding);
    }

    @Override
    public boolean keyConsume(Object binding) {
        return GameOps.keyConsume(binding);
    }

    @Override
    public void keyHold(Object binding, boolean held) {
        GameOps.keyHold(binding, held);
    }

    @Override
    public boolean keyPhysical(int key) {
        return GameOps.physical(key);
    }

    @Override
    public boolean ctrlDown() {
        return GameOps.ctrlDown();
    }

    @Override
    public void mouseClick(int button) {
        GameOps.mouseClick(button);
    }

    @Override
    public boolean mouseDown(int button) {
        return GameOps.mouseDown(button);
    }

    @Override
    public int mouseEventButton() {
        return GameOps.mouseButton();
    }

    @Override
    public boolean mouseEventPressed() {
        return GameOps.mousePressed();
    }

    @Override
    public int mouseEventWheel() {
        return GameOps.mouseWheel();
    }

    @Override
    public String serverConnecting() {
        return GameOps.connecting;
    }

    @Override
    public int gameFps() {
        return HudOps.fps();
    }

    @Override
    public int gameFpsLimit() {
        return HudOps.fpsLimit();
    }

    @Override
    public int gameRefreshRate() {
        return HudOps.refreshRate();
    }
}
