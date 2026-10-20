/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Breno Xavier
 *
 * Part of the Stein Loader API, licensed under the MIT License (see LICENSE-API).
 */

package dev.xavier.stein.loader.api.spi;

import dev.xavier.stein.loader.api.Effects;
import dev.xavier.stein.loader.api.HudElement;
import dev.xavier.stein.loader.api.Net;
import dev.xavier.stein.loader.api.Players;
import dev.xavier.stein.loader.api.Targeting;
import dev.xavier.stein.loader.api.Terrain;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * Whoever does the work behind the API: the Stein Loader.
 *
 * <p><b>Not for mods.</b> Mods call {@code Game}, {@code Keys}, {@code Net}, {@code Hud} and {@code
 * Panel}; those classes relay to here. This way the API names no Bridge or Loader class, and the
 * jar the developer receives to compile against (stein-api.jar) says nothing about how the game is
 * built inside.
 *
 * <p>Cost: {@link #INSTANCE} is a constant ({@code static final}) and there is only one
 * implementation, so the JIT replaces the call with the Loader's and inlines it — the same as
 * calling the Loader directly.
 */
public interface Host {

    /**
     * The Loader's implementation, found through ServiceLoader
     * (META-INF/services/dev.xavier.stein.loader.api.spi.Host).
     */
    Host INSTANCE = load();

    private static Host load() {
        for (Host h : ServiceLoader.load(Host.class, Host.class.getClassLoader())) {
            return h;
        }
        throw new IllegalStateException(
                "Stein Loader not found: the API only works inside the game");
    }

    // Game

    float partialTicks();

    void setInventoryEffectsHidden(boolean hide);

    void resetCameraSmoothing();

    int enchantmentsVersion();

    Object itemData(Object stack);

    void setItemData(Object stack, Object value);

    void watchBlockModel(Object block, boolean watched);

    Object spriteAt(float u, float v);

    // Keys

    void registerKey(Object binding);

    // Net

    void setProxy(Net.Proxy proxy);

    boolean sendPayload(String channel, byte[] data);

    // Hud

    void registerHud(HudElement element);

    boolean hudEnabled(String id);

    Object hudEditor(Object parent);

    // Panel

    /**
     * Opens the Panel over parent; {@code only} shows just the given page, without the list of the
     * others.
     */
    void openPanel(Object parent, String page, boolean only);

    void closePanel();

    // Mods

    boolean modLoaded(String id);

    String modVersion(String id);

    // Inventory (0.1.15)

    int invWindowId();

    boolean invContainerOpen();

    boolean invScreenOpen();

    int invSlotCount();

    Object invSlotStack(int slot);

    int invSlotOf(int inventoryIndex);

    int invIndexOf(int slot);

    Object invStack(int inventoryIndex);

    Object invCursor();

    int invSelected();

    int invFirstEmpty();

    int invArmorPiece(Object stack);

    int invArmorValue(Object stack);

    boolean invClick(int slot, int button, int mode);

    void invSelect(int hotbar);

    boolean invUseHeld();

    boolean invUsing();

    void invOpen();

    void invClose();

    void invHideScreen();

    void invCloseContainer();

    void invCloseWindow(int windowId);

    /** Asks the server for the real inventory (0.1.21). */
    boolean invResync();

    void invVirtualNext(long millis);

    int invHovered();

    void invClickLater(String owner, int slot, int button, int mode);

    int invPending(String owner);

    void invClearPending(String owner);

    // Targeting (0.1.15)

    Targeting.Ray raycast(double reach);

    double attackReach();

    Object pointedEntity();

    boolean attack(Object entity);

    void swing();

    boolean interact(Object entity);

    // Entities (0.1.15)

    Object self();

    boolean isLiving(Object entity);

    float health(Object entity);

    float maxHealth(Object entity);

    float absorption(Object entity);

    int armor(Object entity);

    int hurtTime(Object entity);

    // Context, log, tasks (0.1.16)

    File gameDir();

    /** level 0 info, 1 warning, 2 error. */
    void log(String modId, int level, String message, Throwable error);

    void onGameThread(Runnable task);

    void later(int ticks, Runnable task);

    /** The id of the mod whose entry is this instance (or its class), or null. */
    String modIdOf(Object modInstance);

    byte[] protect(byte[] plain);

    byte[] unprotect(byte[] stored);

    boolean secretsProtected();

    <T> T readConfig(File file, Class<T> type, T defaults, String owner);

    void writeJson(File file, Object value, Class<?> type) throws IOException;

    Map<String, String> readStrings(File file);

    void writeStrings(File file, Map<String, String> values) throws IOException;

    // Players and Relations (0.1.16)

    String selfName();

    List<String> onlinePlayers();

    String playerTeam(String nick);

    String playerPrefix(String nick);

    String playerSuffix(String nick);

    String playerTabName(String nick);

    int playerPing(String nick);

    /** The nick of a player entity, or null. */
    String playerNameOf(Object entity);

    void relationsChanged();

    /** The drawing behind Render, WorldRender and Mesh (0.1.24). */
    RenderBackend render();

    // Render (0.1.16)

    void drawRect(float x, float y, float w, float h, int argb);

    void drawRoundRect(float x, float y, float w, float h, float r, int argb);

    void drawSurface(float x, float y, float w, float h, float opacity);

    void withGameFont(Runnable draw);

    void keySet(Object binding, int key);

    void hudCover(float x, float y, float w, float h);

    /** The whole HUD (the game's and the mods') disappears or comes back. */
    default void hudHidden(boolean hidden) {}

    void drawRing(float cx, float cy, float r, float t, int argb);

    void drawTriangle(float x1, float y1, float x2, float y2, float x3, float y3, int argb);

    int drawText(String s, float x, float y, int argb, boolean shadow);

    int textWidth(String s);

    int fontHeight();

    /** Render.text with Typeface: the face by ordinal (TITLE, TEXT, ITALIC, MONO). */
    float drawStyledText(String s, float x, float y, int argb, int face, float size);

    float styledTextWidth(String s, int face, float size);

    float styledTextHeight(int face, float size);

    void drawImage(
            String texture,
            float x,
            float y,
            float w,
            float h,
            float u0,
            float v0,
            float u1,
            float v1,
            int argb);

    void drawItem(Object stack, int x, int y, boolean overlay);

    void drawPlayerHead(String nick, float x, float y, float size);

    void drawPush();

    void drawPop();

    void drawTranslate(float x, float y);

    void drawScale(float s);

    void drawRotate(float degrees);

    void drawClip(float x, float y, float w, float h);

    void drawUnclip();

    int textureCreate(int w, int h);

    void textureSet(int handle, int x, int y, int w, int h, int[] argb, int offset);

    void textureDraw(
            int handle,
            float x,
            float y,
            float w,
            float h,
            float u0,
            float v0,
            float u1,
            float v1,
            int argb);

    void textureRelease(int handle);

    // Account and Server (0.1.16)

    String accountName();

    String accountLauncherName();

    boolean accountIsLauncher();

    int accountKind();

    int accountLauncherKind();

    void accountUseOffline(String nick);

    void accountRestore();

    String serverAddress();

    String serverName();

    /** The ping measured by the client, in ms, or -1 (0.1.21). */
    int serverLatency();

    String serverLast();

    void serverConnect(String address);

    /** Is the feature allowed on the current server (the registry's server rules; 0.1.28)? */
    default boolean serverAllows(String feature) {
        return true;
    }

    /** The server that turns the feature off here, or "" (0.1.28). */
    default String serverRestrictedBy(String feature) {
        return "";
    }

    // Lang (0.1.16)

    String translate(String key, Object[] args);

    boolean hasTranslation(String key);

    String language();

    /** The game's display name of a keyboard key code (as in the Controls screen) (0.1.22). */
    String keyName(int code);

    // Chat (0.1.16)

    void chatPrint(String text);

    void chatActionBar(String text);

    void chatSend(String message);

    Object chatText(String text);

    String chatPlain(Object component);

    String chatFormatted(Object component);

    // Screens (0.1.16)

    Object screenCurrent();

    int screenKind(Object screen);

    int screenWidth(Object screen);

    int screenHeight(Object screen);

    void screenOpen(Object screen);

    Object buttonNew(int id, int x, int y, int width, int height, String label);

    int buttonId(Object button);

    int buttonX(Object button);

    int buttonY(Object button);

    void buttonLabel(Object button, String label);

    void buttonEnabled(Object button, boolean enabled);

    // Keys (0.1.16)

    Object keyBind(String name, int defaultKey, String category);

    Object keyGame(int which);

    int keyCode(Object binding);

    boolean keyIsDown(Object binding);

    boolean keyConsume(Object binding);

    void keyHold(Object binding, boolean held);

    boolean keyPhysical(int key);

    boolean ctrlDown();

    void mouseClick(int button);

    boolean mouseDown(int button);

    int mouseEventButton();

    boolean mouseEventPressed();

    int mouseEventWheel();

    // Entities, Players, Inventory and Server (0.1.16)

    List<Object> worldPlayers();

    Object playerEntity(String nick);

    boolean isPlayer(Object entity);

    String entityName(Object entity);

    boolean isInvisible(Object entity);

    float distance(Object a, Object b);

    String playerDisplayName(String nick);

    String playerInfoNick(Object playerInfo);

    boolean invAccepts(int slot, Object stack);

    int invRoom(int slot);

    boolean invCrafting(int slot);

    boolean invStacksWith(Object a, Object b);

    String serverConnecting();

    // Stacks, Effects and Terrain (0.1.16)

    Object stackCreate(String id, int count, int damage);

    String stackId(Object stack);

    String stackName(Object stack);

    int stackCount(Object stack);

    int stackMaxCount(Object stack);

    boolean stackDamageable(Object stack);

    int stackDamage(Object stack);

    int stackMaxDamage(Object stack);

    boolean stackEnchanted(Object stack);

    int[] stackPotionEffects(Object stack);

    boolean stackSplash(Object stack);

    int stackEnchantLevel(Object stack, int enchantmentId);

    boolean stackSameKind(Object a, Object b);

    List<Effects.Effect> effectsOf(Object entity);

    String effectName(int id, int amplifier);

    String effectDuration(int ticks, boolean permanent);

    boolean effectHasIcon(int id);

    Object terrainWorld();

    boolean terrainLoaded(int chunkX, int chunkZ);

    int terrainHeight(int x, int z);

    boolean terrainAir(int x, int y, int z);

    boolean terrainSolid(int x, int y, int z);

    boolean terrainWater(int x, int y, int z);

    int terrainColor(int x, int y, int z);

    int terrainMapColor(int x, int y, int z);

    int terrainSkyLight(int x, int y, int z);

    boolean terrainHasSky();

    void terrainSpawners(Terrain.Visitor visitor);

    // Entities, Players, Game and Render of the HUD (0.1.16)

    List<Object> worldEntities();

    int entityKind(Object entity);

    String entityType(Object entity);

    /** axis: 0 x, 1 y, 2 z, 3 yaw. */
    double entityPos(Object entity, int axis, float partialTicks);

    List<Players.Entry> playerList();

    int playerCount();

    String tabText(boolean header);

    String listScore(String nick);

    int gameFps();

    int gameFpsLimit();

    int gameRefreshRate();

    /** handle 0: fan of a single color; otherwise, with the mod's texture (us, vs). */
    void drawFan(float[] xs, float[] ys, float[] us, float[] vs, int count, int argb, int handle);

    List<String> wrapText(String text, int width);

    int guiSize(boolean width);

    void drawItemCount(Object stack, int x, int y, boolean overlay, String count);

    void drawEffectIcon(int id, float x, float y);

    void drawPingIcon(int ms, float x, float y);

    void drawPlayerHeadPremium(String nick, float x, float y, float size, boolean premiumSkin);

    Object playerSkin(String nick);

    int textureCreate(int w, int h, boolean repeat);

    void drawGameTabList(int width);

    // Aiming with options and what a damage indicator used to do directly in the game (0.1.16)

    Targeting.Ray raycastQuery(Targeting.Query query);

    boolean entityAlive(Object entity);

    String entityDisplayName(Object entity);

    Object camera();

    String trimText(String text, int width);

    String terrainBlockId(int x, int y, int z);

    // Gameplay switches (0.1.25)

    void setSprintAllDirections(boolean on);

    void setNoHitDelay(boolean on);

    void setSprintOnHit(boolean on);

    void setNoHitSlowdown(boolean on);
}
