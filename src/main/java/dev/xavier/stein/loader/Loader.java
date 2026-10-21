/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.loader;

import dev.xavier.stein.bridge.SteinBridge;
import dev.xavier.stein.bridge.core.Log;
import dev.xavier.stein.bridge.core.SteinLog;
import dev.xavier.stein.bridge.hooks.HookSink;
import dev.xavier.stein.gfx.SteinEntityDeferred;
import dev.xavier.stein.loader.api.Page;
import dev.xavier.stein.loader.api.SteinMod;
import dev.xavier.stein.loader.font.GameText;
import dev.xavier.stein.loader.gui.Discord;
import dev.xavier.stein.loader.gui.News;
import dev.xavier.stein.loader.gui.Tab;
import dev.xavier.stein.loader.hud.HudGrid;
import dev.xavier.stein.loader.render.LoaderRender;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.network.NetworkManager;

import org.lwjgl.opengl.GL11;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Stein Loader: loads the mods from the game's mods folder and hands them the events that come from
 * the bridge (dev.xavier.stein.bridge.hooks).
 *
 * <p>Enabled mods start in a class loader of their own, a child of the game's; those that bring
 * assets/ join the default resource packs before the first load (as Forge does with each mod's
 * jar). Enabling and disabling is stored in config/steinloader.json and takes effect on the next
 * start: a loaded class does not leave the JVM.
 *
 * <p>Each mod runs isolated: an error in an event is logged (the first few times, with the stack)
 * and the game and the other mods carry on.
 *
 * <p>Written with MCP's readable names (the SDK's); the build translates them to the game's.
 */
public final class Loader implements HookSink {

    public static final String VERSION = "0.1.27";

    /**
     * Minimum bridge version. The Loader uses what the bridge provides (the events, the Graphics,
     * the log), so a bridge older than this does not have everything it calls: instead of breaking
     * in the middle of a session, it loads no mod at all and writes the reason in the list.
     *
     * <p>It is the same agreement the mods have with it: each one asks for the minimum version of
     * what is below it. There is no longer an "API level" separate from the version: it was one
     * more number to remember to bump, and when it was not bumped the new mod got into the old
     * Loader and only broke later, while running.
     */
    public static final String MIN_BRIDGE = "0.1.20";

    /**
     * The version of the Bridge that is running. {@code SteinBridge.VERSION} is a constant, which
     * javac copies into this jar: read directly, it would be the version the Loader was built
     * against. A Bridge older than 0.1.20 has no {@code version()} (and is below {@link
     * #MIN_BRIDGE} anyway): "0".
     */
    public static String bridgeVersion() {
        try {
            return SteinBridge.version();
        } catch (NoSuchMethodError e) {
            return "0";
        }
    }

    private static Loader instance;

    private final List<ModInfo> all = new ArrayList<>();

    private LoaderConfig config;

    private SteinMod[] mods = new SteinMod[0];
    private int failures;

    public Loader() {
        instance = this;
    }

    /** The running Loader, or null if the game started without it. */
    public static Loader get() {
        return instance;
    }

    // ---- dispatch ----

    // The shapes of the per-mod events. The hot ones (every frame, every entity) are called with
    // method references that capture nothing (SteinMod::onOverlay), so a dispatch allocates
    // nothing.

    /** An event with nothing but the mod. */
    @FunctionalInterface
    private interface Call {
        void on(SteinMod m);
    }

    /** A frame event with the partial ticks. */
    @FunctionalInterface
    private interface FrameCall {
        void on(SteinMod m, float partialTicks);
    }

    /** A screen drawing event. */
    @FunctionalInterface
    private interface ScreenCall {
        void on(SteinMod m, Object screen, int mouseX, int mouseY, float partialTicks);
    }

    /** An event a mod can claim: the first one that answers true stops it. */
    @FunctionalInterface
    private interface Claim {
        boolean on(SteinMod m, Object arg);
    }

    /** An event that passes a value through every mod; a null answer keeps the current value. */
    @FunctionalInterface
    private interface Swap {
        Object on(SteinMod m, Object key, Object value);
    }

    /** Every mod, in order; an error is logged and the next mod still gets the event. */
    private void each(String event, Call c) {
        for (SteinMod m : mods) {
            try {
                c.on(m);
            } catch (Throwable e) {
                fail(m, event, e);
            }
        }
    }

    /**
     * Like {@link #each}, while each mod's registrations (a HUD element) are attributed to it, for
     * the per-mod times.
     */
    private void registering(String event, Call c) {
        for (int i = 0; i < mods.length; i++) {
            ModTimes.current = i;
            try {
                c.on(mods[i]);
            } catch (Throwable e) {
                fail(mods[i], event, e);
            }
        }
        ModTimes.current = -1;
    }

    /** Like {@link #each}, adding each mod's time to its own count (ModTimes). */
    private void timed(String event, Call c) {
        for (int i = 0; i < mods.length; i++) {
            long t = System.nanoTime();
            try {
                c.on(mods[i]);
            } catch (Throwable e) {
                fail(mods[i], event, e);
            }
            ModTimes.add(i, System.nanoTime() - t);
        }
    }

    /**
     * A timed drawing event: each mod draws in a render session of its own (LoaderRender), and the
     * last one is closed after the loop.
     */
    private void frame(String event, FrameCall c, float partialTicks) {
        LoaderRender r = LoaderRender.INSTANCE;
        for (int i = 0; i < mods.length; i++) {
            long t = System.nanoTime();
            r.nextSession();
            try {
                c.on(mods[i], partialTicks);
            } catch (Throwable e) {
                fail(mods[i], event, e);
            }
            ModTimes.add(i, System.nanoTime() - t);
        }
        r.nextSession();
    }

    /** A timed screen event. */
    private void screen(
            String event, ScreenCall c, Object screen, int mouseX, int mouseY, float partialTicks) {
        for (int i = 0; i < mods.length; i++) {
            long t = System.nanoTime();
            try {
                c.on(mods[i], screen, mouseX, mouseY, partialTicks);
            } catch (Throwable e) {
                fail(mods[i], event, e);
            }
            ModTimes.add(i, System.nanoTime() - t);
        }
    }

    /** True as soon as one mod claims the event; the mods after it do not see it. */
    private boolean claim(String event, Claim c, Object arg) {
        for (SteinMod m : mods) {
            try {
                if (c.on(m, arg)) {
                    return true;
                }
            } catch (Throwable e) {
                fail(m, event, e);
            }
        }
        return false;
    }

    /** The value after every mod had its say; a mod that answers null leaves it as it was. */
    private Object swap(String event, Swap c, Object key, Object value) {
        Object r = value;
        for (SteinMod m : mods) {
            try {
                Object x = c.on(m, key, r);
                if (x != null) {
                    r = x;
                }
            } catch (Throwable e) {
                fail(m, event, e);
            }
        }
        return r;
    }

    // ---- startup ----

    @Override
    public void beforeStartGame() {
        // Before anything else: the Loader itself logs and reads its options through the Bridge's
        // core.Log and core.Opt, which an older Bridge does not have. Without this the game would
        // die later on a NoClassDefFoundError that says nothing about versions.
        if (!Versions.atLeast(bridgeVersion(), MIN_BRIDGE)) {
            throw new IllegalStateException(
                    "Stein Loader "
                            + VERSION
                            + " needs "
                            + SteinBridge.NAME
                            + " "
                            + MIN_BRIDGE
                            + " or newer (found "
                            + bridgeVersion()
                            + "): reinstall Stein Loader");
        }
        File gameDir = Minecraft.getMinecraft().mcDataDir;
        config = new LoaderConfig(new File(gameDir, "config/steinloader.json"));
        config.read();
        Remote.start(gameDir);

        all.add(
                ModInfo.builtIn(
                        "steinbridge",
                        SteinBridge.NAME,
                        bridgeVersion(),
                        SteinBridge.VENDOR,
                        "steinloader.builtin.bridge"));
        all.add(
                ModInfo.builtIn(
                        "steinloader",
                        "Stein Loader",
                        VERSION,
                        SteinBridge.VENDOR,
                        "steinloader.builtin.loader"));

        File dir = new File(gameDir, "mods");
        dir.mkdirs();
        List<ModInfo> enabled = ModLoading.scanAndFilter(dir, all, config.disabled, VERSION);

        enabled = Dependencies.resolve(enabled, all);
        ModLoading.Loaded loaded = ModLoading.instantiate(enabled, all);
        mods = loaded.instances().toArray(new SteinMod[0]);
        ModTimes.init(loaded.owners().toArray(new ModInfo[0]));
        ModLoading.addResourcePacks(loaded.packs());
        Log.info(
                "loader",
                "Stein Loader "
                        + VERSION
                        + ": "
                        + activeCount()
                        + " of "
                        + loadedCount()
                        + " active mod(s) ("
                        + dir
                        + ")");

        registering("beforeStartGame", SteinMod::beforeStartGame);
    }

    // ---- for the menu ----

    /** The game's parts first, then the mods from the folder in file order. */
    public List<ModInfo> mods() {
        return Collections.unmodifiableList(all);
    }

    /** The mod in the list with this id (the first, if repeated), or null. */
    ModInfo byId(String id) {
        return ModLoading.byId(all, id);
    }

    /** Enabled in the configuration (what applies on the next start). */
    public boolean isEnabled(ModInfo m) {
        return m.builtIn() || !config.disabled.contains(m.id);
    }

    public void setEnabled(ModInfo m, boolean on) {
        if (!m.builtIn() && (on ? config.disabled.remove(m.id) : config.disabled.add(m.id))) {
            config.save();
            Log.info("loader", m.id + (on ? " enabled" : " disabled") + " from the next start");
        }
    }

    /**
     * Turns a Stein log subject on or off (Log tab of the menu) and stores the choice. The file
     * opens on the first enabled subject and closes on the last one disabled, so turning on and off
     * during a session leaves one file at a time for which the log was on.
     */
    public void setDebugLog(SteinLog.Topic topic, boolean on) {
        SteinLog.enable(topic, on);
        config.save();
    }

    /**
     * Stein's font in the mods' HUD (Appearance tab of the menu). Disabled, the HUD goes back to
     * Minecraft's font. Applies immediately, and the choice is stored.
     */
    public void setHudFont(boolean on) {
        GameText.hud = on;
        config.save();
        // The chat lines were wrapped measuring with the other font: redo.
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.ingameGUI != null) {
            mc.ingameGUI.getChatGUI().refreshChat();
        }
    }

    /** Did the Panel key already get the new default (Insert)? Once per installation. */
    public boolean panelKeySet() {
        return config.panelKeySet;
    }

    public void panelKeySet(boolean done) {
        config.panelKeySet = done;
        config.save();
    }

    /** The id of the last Stein Labs notice the player closed (empty if none). */
    public String readNews() {
        return config.newsRead;
    }

    /** The announced version the player already closed (empty if none). */
    public String seenUpdate() {
        return config.seenUpdate;
    }

    /** Marks the announced version as seen: the warning does not return until another comes out. */
    public void seenUpdate(String version) {
        config.seenUpdate = version == null ? "" : version;
        config.save();
    }

    /** Marks the notice as read: it does not return on the next starts. */
    public void readNews(String id) {
        config.newsRead = id == null ? "" : id;
        config.save();
    }

    /** Some enable/disable change is waiting for the next start. */
    public boolean restartPending() {
        for (ModInfo m : all) {
            if (!m.builtIn() && isEnabled(m) != (m.state != ModInfo.State.DISABLED)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Mods from the folder that the Loader accepted (enabled or not); the refused ones appear in
     * the list, but do not count.
     */
    public int loadedCount() {
        int n = 0;
        for (ModInfo m : all) {
            n += m.builtIn() || m.rejected() ? 0 : 1;
        }
        return n;
    }

    /** Mods running in this execution. */
    public int activeCount() {
        int n = 0;
        for (ModInfo m : all) {
            n += m.state == ModInfo.State.LOADED ? 1 : 0;
        }
        return n;
    }

    /**
     * The Panel's pages: one per mod that filled in its own. What belongs to the screen does not go
     * in: enabling, moving and configuring an element is done by looking at the screen, in the
     * editor (right Shift), and not in a list of names.
     *
     * <p>Built on every opening: the mod describes on the spot, so the values are fresh.
     */
    public List<Page> pages() {
        List<Page> out = new ArrayList<>();
        for (ModInfo m : all) {
            for (SteinMod instance : m.instances) {
                Page page = new Page(m.name);
                try {
                    instance.onPage(page);
                } catch (Throwable e) {
                    fail(instance, "onPage", e);
                    continue;
                }
                if (!page.isEmpty()) {
                    out.add(page);
                }
            }
        }
        return out;
    }

    /** The mod's configuration screen (GuiScreen), or null. */
    public Object configScreen(ModInfo m, Object parent) {
        try {
            return m.configScreen(parent);
        } catch (Throwable e) {
            failLoader("configScreen of " + m.id, e);
            return null;
        }
    }

    // ---- events ----

    void fail(SteinMod m, String event, Throwable e) {
        failLoader(m.getClass().getName() + "." + event, e);
    }

    void failLoader(String where, Throwable e) {
        if (failures++ < 20) {
            Log.error("loader", "error in " + where, e);
        }
    }

    @Override
    public void afterStartGame() {
        // The network thread group is created on the first connection, without a lock
        // (LazyLoadBase). The server list pings every server at the same time, and all but the
        // first found the group still empty ("Can't connect to server" the first time the list
        // opened). Created here, on the game thread, it already exists. This used to live in the
        // Connect mod, and with that mod disabled the defect came back.
        try {
            NetworkManager.CLIENT_NIO_EVENTLOOP.getValue();
        } catch (Throwable e) {
            failLoader("network", e);
        }
        Accounts.start();
        GpuInfo.write(Minecraft.getMinecraft().mcDataDir);
        // onShutdown: a single shutdown hook (thread) for all the mods; each used to add its own.
        Runtime.getRuntime()
                .addShutdownHook(
                        new Thread(
                                () -> {
                                    for (SteinMod m : mods) {
                                        try {
                                            m.onShutdown();
                                        } catch (Throwable e) {
                                            Log.info(
                                                    "loader",
                                                    m.getClass().getName() + ".onShutdown: " + e);
                                        }
                                    }
                                },
                                "stein-mods-shutdown"));
        // Stein's font in the mods' HUD (the option has already been read from the configuration).
        try {
            GameText.install();
        } catch (Throwable e) {
            failLoader("font", e);
        }
        // The HUD grid before the mods, which register their elements in afterStartGame.
        try {
            HudGrid.start();
        } catch (Throwable e) {
            failLoader("HUD grid", e);
        }
        registering("afterStartGame", SteinMod::afterStartGame);
        // A mod that fell halfway may have left a key created without registration: the Controls
        // screen would break.
        try {
            KeyGuard.reconcile();
        } catch (Throwable e) {
            failLoader("key check", e);
        }
    }

    /**
     * The id of the mod whose entry is this instance's class (works already in its constructor).
     */
    String modIdOf(Object instance) {
        String cls = instance.getClass().getName();
        for (ModInfo m : mods()) {
            if (m.entries != null && m.entries.contains(cls)) {
                return m.id;
            }
        }
        return null;
    }

    @Override
    public void tickEnd() {
        timed("onTickEnd", SteinMod::onTickEnd);
        GameTasks.run(this, mods);
        try {
            HudGrid.tick();
        } catch (Throwable e) {
            failLoader("HUD grid", e);
        }
        try {
            Latency.tick();
        } catch (Throwable e) {
            failLoader("server ping", e);
        }
        KeyGuard.tick();
    }

    @Override
    public void keyInput() {
        each("onKeyInput", SteinMod::onKeyInput);
    }

    @Override
    public void mouseInput() {
        each("onMouseInput", SteinMod::onMouseInput);
    }

    /** Does any mod draw in onOverlayTop? (Seen once per mod list: with none, nothing to clear.) */
    private SteinMod[] overlayTopOf;

    private boolean overlayTop;

    private boolean hasOverlayTop() {
        if (overlayTopOf != mods) {
            overlayTopOf = mods;
            overlayTop = false;
            for (SteinMod m : mods) {
                try {
                    overlayTop |=
                            m.getClass().getMethod("onOverlayTop", float.class).getDeclaringClass()
                                    != SteinMod.class;
                } catch (NoSuchMethodException e) {
                    // does not happen: the method belongs to the interface
                }
            }
        }
        return overlayTop;
    }

    @Override
    public void overlay(float partialTicks) {
        ModTimes.frame();
        frame("onOverlay", SteinMod::onOverlay, partialTicks);
        // The HUD grid: the mods' elements and the game's HUD parts that the Loader takes over.
        HudGrid.render(partialTicks);
        // And, last, what goes over even the grid's elements (the Tab list), truly on top. The
        // grid's items (hotbar, armor, inventory) are drawn with depth, and whatever came after
        // them failed the depth test right where they are: the icons showed through the Tab list.
        // The depth is cleared here, as the game does before drawing a screen.
        if (hasOverlayTop()) {
            GlStateManager.clear(GL11.GL_DEPTH_BUFFER_BIT);
        }
        frame("onOverlayTop", SteinMod::onOverlayTop, partialTicks);
    }

    @Override
    public void beforeDrawScreen(Object screen, int mouseX, int mouseY, float partialTicks) {
        screen(
                "beforeDrawScreen",
                SteinMod::beforeDrawScreen,
                screen,
                mouseX,
                mouseY,
                partialTicks);
    }

    @Override
    public void afterDrawScreen(Object screen, int mouseX, int mouseY, float partialTicks) {
        screen("afterDrawScreen", SteinMod::afterDrawScreen, screen, mouseX, mouseY, partialTicks);
        // The tab, the invite and the mark over everything.
        try {
            Tab.afterDrawScreen(this, screen, mouseX, mouseY);
            Discord.afterDrawScreen(screen, mouseX, mouseY);
            News.afterDrawScreen(screen, mouseX, mouseY);
        } catch (Throwable e) {
            failLoader("menu tab", e);
        }
    }

    @Override
    public boolean guiMouseInput(Object screen) {
        try {
            if (Tab.mouseInput(this, screen)
                    || News.mouseInput(screen)
                    || Discord.mouseInput(screen)) {
                return true;
            }
        } catch (Throwable e) {
            failLoader("menu tab", e);
        }
        return claim("onGuiMouseInput", SteinMod::onGuiMouseInput, screen);
    }

    @Override
    public Object chat(Object message) {
        Object msg = message;
        for (SteinMod m : mods) {
            try {
                Object r = m.onChat(msg);
                if (r == null) {
                    return null;
                }
                msg = r;
            } catch (Throwable e) {
                fail(m, "onChat", e);
            }
        }
        return msg;
    }

    @Override
    public void joinGame() {
        ServerOps.lastServer = ServerOps.serverAddress();
        if (!ServerOps.lastServer.isEmpty()) {
            GameOps.connecting = ServerOps.lastServer; // also when joining through the command line
        }
        // Whoever is on the other side has the right to know what this client has loaded.
        try {
            ServerReport.announce(this);
        } catch (Throwable e) {
            failLoader("server notice", e);
        }
        each("onJoinGame", SteinMod::onJoinGame);
    }

    @Override
    public void guiInit(Object screen, List<Object> buttons) {
        GameOps.guiInit(this, screen);
        each("onGuiInit", m -> m.onGuiInit(screen, buttons));
    }

    /** The disconnect screen opened (GameOps.guiInit, before its onGuiInit). */
    void disconnected(String address, String reason) {
        each("onDisconnected", m -> m.onDisconnected(address, reason));
    }

    @Override
    public boolean guiAction(Object screen, Object button) {
        for (SteinMod m : mods) {
            try {
                if (m.onGuiAction(screen, button)) {
                    return true;
                }
            } catch (Throwable e) {
                fail(m, "onGuiAction", e);
            }
        }
        return false;
    }

    @Override
    public float fov(float fov, float partialTicks, boolean useFovSetting) {
        float f = fov;
        for (SteinMod m : mods) {
            try {
                f = m.onFov(f, partialTicks, useFovSetting);
            } catch (Throwable e) {
                fail(m, "onFov", e);
            }
        }
        return f;
    }

    @Override
    public float fovModifier(float modifier) {
        float f = modifier;
        for (SteinMod m : mods) {
            try {
                f = m.onFovModifier(f);
            } catch (Throwable e) {
                fail(m, "onFovModifier", e);
            }
        }
        return f;
    }

    @Override
    public void textureStitch(Object textureMap) {
        each("onTextureStitch", m -> m.onTextureStitch(textureMap));
    }

    @Override
    public void modelsReloaded() {
        TerrainOps.reset(); // the block colors come from the new textures
        each("onModelsReloaded", SteinMod::onModelsReloaded);
    }

    @Override
    public Object itemModel(Object stack, Object model) {
        return swap("onItemModel", SteinMod::onItemModel, stack, model);
    }

    /** On the chunk build threads, only for marked blocks (Game.watchBlockModel). */
    @Override
    public Object blockModel(Object world, Object state, Object pos, Object model) {
        Object r = model;
        for (SteinMod m : mods) {
            try {
                Object x = m.onBlockModel(world, state, pos, r);
                if (x != null) {
                    r = x;
                }
            } catch (Throwable e) {
                fail(m, "onBlockModel", e);
            }
        }
        return r;
    }

    @Override
    public Object armorTexture(Object stack, int layer, String overlay, Object location) {
        Object r = location;
        for (SteinMod m : mods) {
            try {
                Object x = m.onArmorTexture(stack, layer, overlay, r);
                if (x != null) {
                    r = x;
                }
            } catch (Throwable e) {
                fail(m, "onArmorTexture", e);
            }
        }
        return r;
    }

    @Override
    public boolean attackEntity(Object target) {
        return claim("onAttackEntity", SteinMod::onAttackEntity, target);
    }

    @Override
    public boolean interactEntity(Object target) {
        return claim("onInteractEntity", SteinMod::onInteractEntity, target);
    }

    @Override
    public boolean windowClick(int windowId, int slot, int button, int mode) {
        for (SteinMod m : mods) {
            try {
                if (m.onWindowClick(windowId, slot, button, mode)) {
                    return true;
                }
            } catch (Throwable e) {
                fail(m, "onWindowClick", e);
            }
        }
        return false;
    }

    @Override
    public void slotChanged(int windowId, int slot, Object stack) {
        each("onSlotChanged", m -> m.onSlotChanged(windowId, slot, stack));
    }

    @Override
    public void windowItems(int windowId) {
        each("onWindowItems", m -> m.onWindowItems(windowId));
    }

    @Override
    public boolean entityHurt(Object entity) {
        boolean cancel = false;
        for (SteinMod m : mods) {
            try {
                // Everyone learns of the damage, even after one cancels the effect: it is a notice,
                // not just a request.
                cancel |= m.onEntityHurt(entity);
            } catch (Throwable e) {
                fail(m, "onEntityHurt", e);
            }
        }
        return cancel;
    }

    @Override
    public void entityDeath(Object entity) {
        each("onEntityDeath", m -> m.onEntityDeath(entity));
    }

    @Override
    public void healthChanged(
            Object entity,
            float oldHealth,
            float newHealth,
            float oldAbsorption,
            float newAbsorption) {
        each(
                "onHealthChanged",
                m -> m.onHealthChanged(entity, oldHealth, newHealth, oldAbsorption, newAbsorption));
    }

    @Override
    public boolean hideEntity(Object entity) {
        return claim("onHideEntity", SteinMod::onHideEntity, entity);
    }

    @Override
    public boolean ghostEntity(Object entity) {
        return claim("onGhostEntity", SteinMod::onGhostEntity, entity);
    }

    @Override
    public boolean playerList(Object overlay, int width, Object scoreboard, Object objective) {
        for (SteinMod m : mods) {
            try {
                if (m.onPlayerList(overlay, width, scoreboard, objective)) {
                    return true;
                }
            } catch (Throwable e) {
                fail(m, "onPlayerList", e);
            }
        }
        return false;
    }

    @Override
    public String playerListName(Object playerInfo, String name) {
        return (String)
                swap(
                        "onPlayerListName",
                        (m, info, value) -> m.onPlayerListName(info, (String) value),
                        playerInfo,
                        name);
    }

    @Override
    public Object nametag(Object entity, Object name) {
        return swap("onNametag", SteinMod::onNametag, entity, name);
    }

    @Override
    public void renderWorld(float partialTicks) {
        LoaderRender r = LoaderRender.INSTANCE;
        r.world(true, partialTicks);
        // Under Stein GFX, every mod's world drawing is recorded in one strict-order session (one
        // pass, with the text cache) instead of one pass per draw; a no-op on the fixed pipeline.
        SteinEntityDeferred.worldBegin();
        try {
            frame("onRenderWorld", SteinMod::onRenderWorld, partialTicks);
        } finally {
            SteinEntityDeferred.worldEnd();
            r.world(false, partialTicks);
        }
    }

    @Override
    public void customPayload(String channel, byte[] data) {
        each("onCustomPayload", m -> m.onCustomPayload(channel, data));
    }

    @Override
    public String sendChat(String message) {
        String msg = message;
        for (SteinMod m : mods) {
            try {
                msg = m.onSendChat(msg);
                if (msg == null) {
                    return null;
                }
            } catch (Throwable e) {
                fail(m, "onSendChat", e);
            }
        }
        return msg;
    }

    @Override
    public Object playSound(Object sound) {
        Object s = sound;
        for (SteinMod m : mods) {
            try {
                s = m.onPlaySound(s);
                if (s == null) {
                    return null;
                }
            } catch (Throwable e) {
                fail(m, "onPlaySound", e);
            }
        }
        return s;
    }

    @Override
    public void debugInfoRight(List<String> lines) {
        lines.add("Stein Loader v" + VERSION);
        lines.add(I18n.format("steinloader.mods.count", loadedCount(), activeCount()));
        String times = ModTimes.summary();
        if (!times.isEmpty()) {
            lines.add(I18n.format("steinloader.mods.time", times));
        }
    }

    @Override
    public void mainMenuBranding(List<String> lines) {
        lines.add("Stein Loader v" + VERSION);
    }
}
