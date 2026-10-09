/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.loader;

import dev.xavier.stein.bridge.core.SteinLog;
import dev.xavier.stein.bridge.engine.EnchCache;
import dev.xavier.stein.bridge.engine.KeyBindings;
import dev.xavier.stein.bridge.engine.TextHook;
import dev.xavier.stein.bridge.hooks.GameHooks;
import dev.xavier.stein.loader.api.Effects;
import dev.xavier.stein.loader.api.HudElement;
import dev.xavier.stein.loader.api.Net;
import dev.xavier.stein.loader.api.Players;
import dev.xavier.stein.loader.api.Targeting;
import dev.xavier.stein.loader.api.Terrain;
import dev.xavier.stein.loader.api.spi.Host;
import dev.xavier.stein.loader.api.spi.RenderBackend;
import dev.xavier.stein.loader.font.SteinFont;
import dev.xavier.stein.loader.gui.Draw2D;
import dev.xavier.stein.loader.gui.PanelScreen;
import dev.xavier.stein.loader.gui.Surface;
import dev.xavier.stein.loader.gui.Theme;
import dev.xavier.stein.loader.hud.HudGrid;
import dev.xavier.stein.loader.render.LoaderRender;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.C17PacketCustomPayload;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * The mod API wired to the Bridge and the Loader. It is the only place where {@code
 * dev.xavier.stein.loader.api} meets the Bridge: the API calls {@link Host}, and the Host is this
 * class (META-INF/services/Host). It is long because the SPI is one flat interface; every method is
 * a one-line delegate to the game, to GameOps, InventoryOps, TargetingOps, PlayerOps, ServerOps or
 * the other helpers of this package.
 */
public final class LoaderHost implements Host {

    public LoaderHost() {}

    @Override
    public float partialTicks() {
        return GameHooks.partialTicks();
    }

    @Override
    public void setInventoryEffectsHidden(boolean hide) {
        GameHooks.setInventoryEffectsHidden(hide);
    }

    @Override
    public void resetCameraSmoothing() {
        GameHooks.resetCameraSmoothing();
    }

    @Override
    public int enchantmentsVersion() {
        return EnchCache.version();
    }

    @Override
    public Object itemData(Object stack) {
        return GameHooks.itemData(stack);
    }

    @Override
    public void setItemData(Object stack, Object value) {
        GameHooks.setItemData(stack, value);
    }

    @Override
    public void watchBlockModel(Object block, boolean watched) {
        GameHooks.watchBlockModel(block, watched);
    }

    @Override
    public Object spriteAt(float u, float v) {
        return GameHooks.spriteAt(u, v);
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

    // Inventory

    @Override
    public int invWindowId() {
        return InventoryOps.windowId();
    }

    @Override
    public boolean invContainerOpen() {
        return InventoryOps.containerOpen();
    }

    @Override
    public boolean invScreenOpen() {
        return InventoryOps.screenOpen();
    }

    @Override
    public int invSlotCount() {
        return InventoryOps.slotCount();
    }

    @Override
    public Object invSlotStack(int slot) {
        return InventoryOps.slotStack(slot);
    }

    @Override
    public int invSlotOf(int inventoryIndex) {
        return InventoryOps.slotOf(inventoryIndex);
    }

    @Override
    public int invIndexOf(int slot) {
        return InventoryOps.indexOf(slot);
    }

    @Override
    public Object invStack(int inventoryIndex) {
        return InventoryOps.stack(inventoryIndex);
    }

    @Override
    public Object invCursor() {
        return InventoryOps.cursor();
    }

    @Override
    public int invSelected() {
        return InventoryOps.selected();
    }

    @Override
    public int invFirstEmpty() {
        return InventoryOps.firstEmpty();
    }

    @Override
    public int invArmorPiece(Object stack) {
        return InventoryOps.armorPiece(stack);
    }

    @Override
    public int invArmorValue(Object stack) {
        return InventoryOps.armorValue(stack);
    }

    @Override
    public boolean invClick(int slot, int button, int mode) {
        return InventoryOps.click(slot, button, mode);
    }

    @Override
    public boolean invUseHeld() {
        return InventoryOps.useHeld();
    }

    @Override
    public boolean invUsing() {
        return InventoryOps.using();
    }

    @Override
    public void invSelect(int hotbar) {
        InventoryOps.select(hotbar);
    }

    @Override
    public void invOpen() {
        InventoryOps.open();
    }

    @Override
    public void invHideScreen() {
        InventoryOps.hideScreen();
    }

    @Override
    public void invVirtualNext(long millis) {
        InventoryOps.virtualNext(millis);
    }

    @Override
    public void invCloseWindow(int windowId) {
        InventoryOps.closeWindow(windowId);
    }

    @Override
    public boolean invResync() {
        return InventoryOps.resync();
    }

    @Override
    public void invCloseContainer() {
        InventoryOps.closeContainer();
    }

    @Override
    public void invClose() {
        InventoryOps.close();
    }

    @Override
    public int invHovered() {
        return GameHooks.hoveredSlot(Minecraft.getMinecraft().currentScreen);
    }

    @Override
    public void invClickLater(String owner, int slot, int button, int mode) {
        GameTasks.clickLater(
                owner == null ? "" : owner, InventoryOps.windowId(), slot, button, mode);
    }

    @Override
    public int invPending(String owner) {
        return GameTasks.pendingClicks(owner == null ? "" : owner);
    }

    @Override
    public void invClearPending(String owner) {
        GameTasks.clearClicks(owner == null ? "" : owner);
    }

    // Targeting

    @Override
    public Targeting.Ray raycast(double reach) {
        return TargetingOps.raycast(reach);
    }

    @Override
    public double attackReach() {
        return TargetingOps.attackReach();
    }

    @Override
    public Object pointedEntity() {
        return TargetingOps.pointedEntity();
    }

    @Override
    public boolean attack(Object entity) {
        return TargetingOps.attack(entity);
    }

    @Override
    public void swing() {
        TargetingOps.swing();
    }

    @Override
    public boolean interact(Object entity) {
        return TargetingOps.interact(entity);
    }

    // Entities

    @Override
    public Object self() {
        return PlayerOps.self();
    }

    @Override
    public boolean isLiving(Object entity) {
        return entity instanceof EntityLivingBase;
    }

    @Override
    public float health(Object entity) {
        return isLiving(entity) ? ((EntityLivingBase) entity).getHealth() : 0;
    }

    @Override
    public float maxHealth(Object entity) {
        return isLiving(entity) ? ((EntityLivingBase) entity).getMaxHealth() : 0;
    }

    @Override
    public float absorption(Object entity) {
        return isLiving(entity) ? ((EntityLivingBase) entity).getAbsorptionAmount() : 0;
    }

    @Override
    public int armor(Object entity) {
        return isLiving(entity) ? ((EntityLivingBase) entity).getTotalArmorValue() : 0;
    }

    @Override
    public int hurtTime(Object entity) {
        return isLiving(entity) ? ((EntityLivingBase) entity).hurtTime : 0;
    }

    // Context, log, tasks

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
            error.printStackTrace(System.out);
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

    // Players and Relations

    @Override
    public String selfName() {
        return PlayerOps.selfName();
    }

    @Override
    public List<String> onlinePlayers() {
        return PlayerOps.online();
    }

    @Override
    public String playerTeam(String nick) {
        return PlayerOps.teamName(nick);
    }

    @Override
    public String playerPrefix(String nick) {
        return PlayerOps.prefix(nick);
    }

    @Override
    public String playerSuffix(String nick) {
        return PlayerOps.suffix(nick);
    }

    @Override
    public String playerTabName(String nick) {
        return PlayerOps.tabName(nick);
    }

    @Override
    public int playerPing(String nick) {
        return PlayerOps.ping(nick);
    }

    @Override
    public String playerNameOf(Object entity) {
        return PlayerOps.nameOf(entity);
    }

    @Override
    public void relationsChanged() {
        GameTasks.relationsChanged();
    }

    // Render

    @Override
    public void drawRect(float x, float y, float w, float h, int argb) {
        Draw2D.rect(x, y, w, h, argb);
    }

    @Override
    public void drawRoundRect(float x, float y, float w, float h, float r, int argb) {
        Draw2D.roundRect(x, y, w, h, r, argb);
    }

    @Override
    public void drawSurface(float x, float y, float w, float h, float opacity) {
        Surface.draw(x, y, w, h, opacity);
    }

    @Override
    public void withGameFont(Runnable draw) {
        TextHook.gameFontDepth++;
        try {
            draw.run();
        } finally {
            TextHook.gameFontDepth--;
        }
    }

    @Override
    public void hudCover(float x, float y, float w, float h) {
        HudGrid.cover(x, y, w, h);
    }

    @Override
    public void hudHidden(boolean hidden) {
        GameHooks.hideHud = hidden;
    }

    @Override
    public void drawRing(float cx, float cy, float r, float t, int argb) {
        Draw2D.ring(cx, cy, r, t, argb);
    }

    @Override
    public void drawTriangle(float x1, float y1, float x2, float y2, float x3, float y3, int argb) {
        Draw2D.triangle(x1, y1, x2, y2, x3, y3, argb);
    }

    @Override
    public int drawText(String s, float x, float y, int argb, boolean shadow) {
        return Draw2D.text(s, x, y, argb, shadow);
    }

    @Override
    public int textWidth(String s) {
        return s == null ? 0 : Draw2D.textWidth(s);
    }

    @Override
    public int fontHeight() {
        return Draw2D.fontHeight();
    }

    /** The API's faces (Typeface), in its order: title, text, italic, mono. */
    private static final SteinFont.Face[] FACES = {
        SteinFont.Face.DISPLAY, SteinFont.Face.BODY,
        SteinFont.Face.ITALIC, SteinFont.Face.MONO,
    };

    @Override
    public float drawStyledText(String s, float x, float y, int argb, int face, float size) {
        if (s == null || s.isEmpty()) {
            return x;
        }
        SteinFont.Face f = FACES[face];
        if (!SteinFont.ready(f)) {
            // Without the font file: the game's, so the text does not vanish.
            return x + Draw2D.text(s, x, y, argb, false);
        }
        return SteinFont.draw(f, s, x, y, size, argb, 0.0F, Theme.CODES);
    }

    @Override
    public float styledTextWidth(String s, int face, float size) {
        if (s == null) {
            return 0;
        }
        SteinFont.Face f = FACES[face];
        return SteinFont.ready(f) ? SteinFont.width(f, s, size) : Draw2D.textWidth(s);
    }

    @Override
    public float styledTextHeight(int face, float size) {
        return SteinFont.height(FACES[face], size);
    }

    @Override
    public void drawImage(
            String texture,
            float x,
            float y,
            float w,
            float h,
            float u0,
            float v0,
            float u1,
            float v1,
            int argb) {
        Draw2D.image(texture, x, y, w, h, u0, v0, u1, v1, argb);
    }

    @Override
    public void drawItem(Object stack, int x, int y, boolean overlay) {
        Draw2D.item(stack, x, y, overlay);
    }

    @Override
    public void drawPlayerHead(String nick, float x, float y, float size) {
        Draw2D.playerHead(nick, x, y, size);
    }

    @Override
    public void drawPush() {
        Draw2D.push();
    }

    @Override
    public void drawPop() {
        Draw2D.pop();
    }

    @Override
    public void drawTranslate(float x, float y) {
        Draw2D.translate(x, y);
    }

    @Override
    public void drawScale(float s) {
        Draw2D.scale(s);
    }

    @Override
    public void drawRotate(float degrees) {
        Draw2D.rotate(degrees);
    }

    @Override
    public void drawClip(float x, float y, float w, float h) {
        Draw2D.clip(x, y, w, h);
    }

    @Override
    public void drawUnclip() {
        Draw2D.unclip();
    }

    @Override
    public RenderBackend render() {
        return LoaderRender.INSTANCE;
    }

    @Override
    public int textureCreate(int w, int h) {
        return Draw2D.textureCreate(w, h);
    }

    @Override
    public void textureSet(int handle, int x, int y, int w, int h, int[] argb, int offset) {
        Draw2D.textureSet(handle, x, y, w, h, argb, offset);
    }

    @Override
    public void textureDraw(
            int handle,
            float x,
            float y,
            float w,
            float h,
            float u0,
            float v0,
            float u1,
            float v1,
            int argb) {
        Draw2D.textureDraw(handle, x, y, w, h, u0, v0, u1, v1, argb);
    }

    @Override
    public void textureRelease(int handle) {
        Draw2D.textureRelease(handle);
    }

    // Account and Server

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

    // Chat, Screens, Keys, Entities, Players, Inventory and Server: delegated to GameOps

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
    public List<Object> worldPlayers() {
        return GameOps.players();
    }

    @Override
    public Object playerEntity(String nick) {
        return GameOps.player(nick);
    }

    @Override
    public boolean isPlayer(Object entity) {
        return GameOps.isPlayer(entity);
    }

    @Override
    public String entityName(Object entity) {
        return GameOps.name(entity);
    }

    @Override
    public boolean isInvisible(Object entity) {
        return GameOps.invisible(entity);
    }

    @Override
    public float distance(Object a, Object b) {
        return GameOps.distance(a, b);
    }

    @Override
    public String playerDisplayName(String nick) {
        return GameOps.displayName(nick);
    }

    @Override
    public String playerInfoNick(Object playerInfo) {
        return GameOps.nickOf(playerInfo);
    }

    @Override
    public boolean invAccepts(int slot, Object stack) {
        return GameOps.accepts(slot, stack);
    }

    @Override
    public int invRoom(int slot) {
        return GameOps.room(slot);
    }

    @Override
    public boolean invCrafting(int slot) {
        return GameOps.crafting(slot);
    }

    @Override
    public boolean invStacksWith(Object a, Object b) {
        return GameOps.stacksWith(a, b);
    }

    @Override
    public String serverConnecting() {
        return GameOps.connecting;
    }

    // Stacks, Effects, Terrain, and the rest of what the HUD does directly in the game

    @Override
    public Object stackCreate(String id, int count, int damage) {
        return HudOps.create(id, count, damage);
    }

    @Override
    public String stackId(Object stack) {
        return HudOps.id(stack);
    }

    @Override
    public String stackName(Object stack) {
        return HudOps.name(stack);
    }

    @Override
    public int stackCount(Object stack) {
        return HudOps.count(stack);
    }

    @Override
    public int stackMaxCount(Object stack) {
        return HudOps.maxCount(stack);
    }

    @Override
    public boolean stackDamageable(Object stack) {
        return HudOps.damageable(stack);
    }

    @Override
    public int stackDamage(Object stack) {
        return HudOps.damage(stack);
    }

    @Override
    public int stackMaxDamage(Object stack) {
        return HudOps.maxDamage(stack);
    }

    @Override
    public int[] stackPotionEffects(Object stack) {
        return HudOps.potionEffects(stack);
    }

    @Override
    public boolean stackSplash(Object stack) {
        return HudOps.splash(stack);
    }

    @Override
    public boolean stackEnchanted(Object stack) {
        return HudOps.enchanted(stack);
    }

    @Override
    public int stackEnchantLevel(Object stack, int enchantmentId) {
        return HudOps.enchantLevel(stack, enchantmentId);
    }

    @Override
    public boolean stackSameKind(Object a, Object b) {
        return HudOps.sameKind(a, b);
    }

    @Override
    public List<Effects.Effect> effectsOf(Object entity) {
        return HudOps.effects(entity);
    }

    @Override
    public String effectName(int id, int amplifier) {
        return HudOps.effectName(id, amplifier);
    }

    @Override
    public String effectDuration(int ticks, boolean permanent) {
        return HudOps.duration(ticks, permanent);
    }

    @Override
    public boolean effectHasIcon(int id) {
        return HudOps.hasIcon(id);
    }

    @Override
    public Object terrainWorld() {
        return TerrainOps.current();
    }

    @Override
    public boolean terrainLoaded(int chunkX, int chunkZ) {
        return TerrainOps.loaded(chunkX, chunkZ);
    }

    @Override
    public int terrainHeight(int x, int z) {
        return TerrainOps.height(x, z);
    }

    @Override
    public boolean terrainAir(int x, int y, int z) {
        return TerrainOps.air(x, y, z);
    }

    @Override
    public boolean terrainSolid(int x, int y, int z) {
        return TerrainOps.solid(x, y, z);
    }

    @Override
    public boolean terrainWater(int x, int y, int z) {
        return TerrainOps.water(x, y, z);
    }

    @Override
    public int terrainColor(int x, int y, int z) {
        return TerrainOps.color(x, y, z);
    }

    @Override
    public int terrainMapColor(int x, int y, int z) {
        return TerrainOps.mapColor(x, y, z);
    }

    @Override
    public int terrainSkyLight(int x, int y, int z) {
        return TerrainOps.skyLight(x, y, z);
    }

    @Override
    public boolean terrainHasSky() {
        return TerrainOps.hasSky();
    }

    @Override
    public void terrainSpawners(Terrain.Visitor visitor) {
        TerrainOps.spawners(visitor);
    }

    @Override
    public List<Object> worldEntities() {
        return HudOps.entities();
    }

    @Override
    public int entityKind(Object entity) {
        return HudOps.kind(entity);
    }

    @Override
    public String entityType(Object entity) {
        return HudOps.type(entity);
    }

    @Override
    public double entityPos(Object entity, int axis, float partialTicks) {
        return HudOps.pos(entity, axis, partialTicks);
    }

    @Override
    public List<Players.Entry> playerList() {
        return HudOps.list();
    }

    @Override
    public int playerCount() {
        return HudOps.count();
    }

    @Override
    public String tabText(boolean header) {
        return HudOps.tabText(header);
    }

    @Override
    public String listScore(String nick) {
        return HudOps.listScore(nick);
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

    @Override
    public void drawFan(
            float[] xs, float[] ys, float[] us, float[] vs, int count, int argb, int handle) {
        Draw2D.fan(xs, ys, us, vs, count, argb, handle);
    }

    @Override
    public List<String> wrapText(String text, int width) {
        return Draw2D.wrap(text, width);
    }

    @Override
    public int guiSize(boolean width) {
        return Draw2D.guiSize(width);
    }

    @Override
    public void drawItemCount(Object stack, int x, int y, boolean overlay, String count) {
        Draw2D.itemCount(stack, x, y, overlay, count);
    }

    @Override
    public void drawEffectIcon(int id, float x, float y) {
        Draw2D.effectIcon(id, x, y);
    }

    @Override
    public void drawPingIcon(int ms, float x, float y) {
        Draw2D.pingIcon(ms, x, y);
    }

    @Override
    public void drawPlayerHeadPremium(
            String nick, float x, float y, float size, boolean premiumSkin) {
        Draw2D.playerHead(nick, x, y, size, premiumSkin);
    }

    @Override
    public Object playerSkin(String nick) {
        return Draw2D.playerSkin(nick);
    }

    @Override
    public void drawGameTabList(int width) {
        HudOps.drawGameTabList(width);
    }

    @Override
    public int textureCreate(int w, int h, boolean repeat) {
        return Draw2D.textureCreate(w, h, repeat);
    }

    @Override
    public Targeting.Ray raycastQuery(Targeting.Query query) {
        return TargetingOps.raycast(query);
    }

    @Override
    public boolean entityAlive(Object entity) {
        return entity instanceof EntityLivingBase l && l.isEntityAlive();
    }

    @Override
    public String entityDisplayName(Object entity) {
        return entity instanceof Entity e ? e.getDisplayName().getFormattedText() : null;
    }

    @Override
    public Object camera() {
        return Minecraft.getMinecraft().getRenderViewEntity();
    }

    @Override
    public String trimText(String text, int width) {
        return text == null ? "" : Draw2D.trim(text, width);
    }

    @Override
    public String terrainBlockId(int x, int y, int z) {
        return TerrainOps.blockId(x, y, z);
    }
}
