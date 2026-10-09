/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.loader;

import dev.xavier.stein.bridge.SteinBridge;
import dev.xavier.stein.bridge.core.SteinLog;
import dev.xavier.stein.bridge.hooks.HookSink;
import dev.xavier.stein.gfx.SteinEntityDeferred;
import dev.xavier.stein.loader.api.Page;
import dev.xavier.stein.loader.api.SteinMod;
import dev.xavier.stein.loader.font.GameText;
import dev.xavier.stein.loader.gui.Discord;
import dev.xavier.stein.loader.gui.News;
import dev.xavier.stein.loader.gui.Recommended;
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

    public static final String VERSION = "0.1.23";

    /**
     * Minimum bridge version. The Loader uses what the bridge provides (the events, the Graphics,
     * the log), so a bridge older than this does not have everything it calls: instead of breaking
     * in the middle of a session, it loads no mod at all and writes the reason in the list.
     *
     * <p>It is the same agreement the mods have with it: each one asks for the minimum version of
     * what is below it. There is no longer an "API level" separate from the version — it was one
     * more number to remember to bump, and when it was not bumped the new mod got into the old
     * Loader and only broke later, while running.
     */
    public static final String MIN_BRIDGE = "0.1.12";

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

    // ---- startup ----

    @Override
    public void beforeStartGame() {
        File gameDir = Minecraft.getMinecraft().mcDataDir;
        config = new LoaderConfig(new File(gameDir, "config/steinloader.json"));
        config.read();
        Remote.start(gameDir);

        all.add(
                ModInfo.builtIn(
                        "steinbridge",
                        SteinBridge.NAME,
                        SteinBridge.VERSION,
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
        System.out.println(
                "[loader] Stein Loader "
                        + VERSION
                        + ": "
                        + activeCount()
                        + " of "
                        + loadedCount()
                        + " active mod(s) ("
                        + dir
                        + ")");

        for (int i = 0; i < mods.length; i++) {
            ModTimes.current = i; // whatever it registers here (HUD element) is its own
            try {
                mods[i].beforeStartGame();
            } catch (Throwable e) {
                fail(mods[i], "beforeStartGame", e);
            }
        }
        ModTimes.current = -1;
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
            System.out.println(
                    "[loader] " + m.id + (on ? " enabled" : " disabled") + " from the next start");
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
     * in — enabling, moving and configuring an element is done by looking at the screen, in the
     * editor (right Shift), and not in a list of names.
     *
     * <p>Built on every opening — the mod describes on the spot, so the values are fresh.
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
            System.out.println("[loader] error in " + where + ": " + e);
            e.printStackTrace(System.out);
        }
    }

    @Override
    public void afterStartGame() {
        // The network thread group is born on the first connection, without a lock (LazyLoadBase):
        // the server list sends the
        // ping of all of them at the same time, and all but the first got the group still empty
        // ("Can't connect to
        // server" the first time the list opens). Created here, on the game thread, it already
        // exists. It used to live in Connect:
        // with it disabled, the defect came back.
        try {
            NetworkManager.CLIENT_NIO_EVENTLOOP.getValue();
        } catch (Throwable e) {
            failLoader("network", e);
        }
        Accounts.start();
        // onShutdown: a single hook, for all the mods (each one used to open its own). On a
        // shutdown thread.
        Runtime.getRuntime()
                .addShutdownHook(
                        new Thread(
                                () -> {
                                    for (SteinMod m : mods) {
                                        try {
                                            m.onShutdown();
                                        } catch (Throwable e) {
                                            System.out.println(
                                                    "[loader] "
                                                            + m.getClass().getName()
                                                            + ".onShutdown: "
                                                            + e);
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
        for (int i = 0; i < mods.length; i++) {
            ModTimes.current = i;
            try {
                mods[i].afterStartGame();
            } catch (Throwable e) {
                fail(mods[i], "afterStartGame", e);
            }
        }
        ModTimes.current = -1;
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
        for (int i = 0; i < mods.length; i++) {
            long t = System.nanoTime();
            try {
                mods[i].onTickEnd();
            } catch (Throwable e) {
                fail(mods[i], "onTickEnd", e);
            }
            ModTimes.add(i, System.nanoTime() - t);
        }
        GameTasks.run(this, mods);
        try {
            HudGrid.tick();
        } catch (Throwable e) {
            failLoader("HUD grid", e);
        }
        try {
            Remote.tick();
        } catch (Throwable e) {
            failLoader("Stein Labs data", e);
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
        for (SteinMod m : mods) {
            try {
                m.onKeyInput();
            } catch (Throwable e) {
                fail(m, "onKeyInput", e);
            }
        }
    }

    @Override
    public void mouseInput() {
        for (SteinMod m : mods) {
            try {
                m.onMouseInput();
            } catch (Throwable e) {
                fail(m, "onMouseInput", e);
            }
        }
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
        for (int i = 0; i < mods.length; i++) {
            long t = System.nanoTime();
            LoaderRender.INSTANCE.nextSession();
            try {
                mods[i].onOverlay(partialTicks);
            } catch (Throwable e) {
                fail(mods[i], "onOverlay", e);
            }
            ModTimes.add(i, System.nanoTime() - t);
        }
        LoaderRender.INSTANCE.nextSession();
        // The HUD grid: the mods' elements and the game's HUD parts that the Loader takes over.
        HudGrid.render(partialTicks);
        // And, last, what goes over even the grid's elements (the Tab list). Truly on top: the
        // grid's
        // items
        // (hotbar, armor, inventory) are drawn in front, with depth, and whatever came after
        // lost the depth test right where they are — the icons showed up sharp
        // over the Tab. The
        // depth restarts here, as the game does before drawing a screen.
        if (hasOverlayTop()) {
            GlStateManager.clear(GL11.GL_DEPTH_BUFFER_BIT);
        }
        for (int i = 0; i < mods.length; i++) {
            long t = System.nanoTime();
            LoaderRender.INSTANCE.nextSession();
            try {
                mods[i].onOverlayTop(partialTicks);
            } catch (Throwable e) {
                fail(mods[i], "onOverlayTop", e);
            }
            ModTimes.add(i, System.nanoTime() - t);
        }
        LoaderRender.INSTANCE.nextSession();
    }

    @Override
    public void beforeDrawScreen(Object screen, int mouseX, int mouseY, float partialTicks) {
        try {
            Recommended.beforeDrawScreen(screen);
        } catch (Throwable e) {
            failLoader("server list slack", e);
        }
        for (int i = 0; i < mods.length; i++) {
            long t = System.nanoTime();
            try {
                mods[i].beforeDrawScreen(screen, mouseX, mouseY, partialTicks);
            } catch (Throwable e) {
                fail(mods[i], "beforeDrawScreen", e);
            }
            ModTimes.add(i, System.nanoTime() - t);
        }
    }

    @Override
    public void afterDrawScreen(Object screen, int mouseX, int mouseY, float partialTicks) {
        for (int i = 0; i < mods.length; i++) {
            long t = System.nanoTime();
            try {
                mods[i].afterDrawScreen(screen, mouseX, mouseY, partialTicks);
            } catch (Throwable e) {
                fail(mods[i], "afterDrawScreen", e);
            }
            ModTimes.add(i, System.nanoTime() - t);
        }
        // The tab, the invite and the mark over everything.
        try {
            Tab.afterDrawScreen(this, screen, mouseX, mouseY);
            Discord.afterDrawScreen(screen, mouseX, mouseY);
            News.afterDrawScreen(screen, mouseX, mouseY);
            Recommended.afterDrawScreen(screen);
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
        for (SteinMod m : mods) {
            try {
                if (m.onGuiMouseInput(screen)) {
                    return true;
                }
            } catch (Throwable e) {
                fail(m, "onGuiMouseInput", e);
            }
        }
        return false;
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
        for (SteinMod m : mods) {
            try {
                m.onJoinGame();
            } catch (Throwable e) {
                fail(m, "onJoinGame", e);
            }
        }
    }

    @Override
    public void guiInit(Object screen, List<Object> buttons) {
        GameOps.guiInit(this, screen);
        for (SteinMod m : mods) {
            try {
                m.onGuiInit(screen, buttons);
            } catch (Throwable e) {
                fail(m, "onGuiInit", e);
            }
        }
    }

    /** The disconnect screen opened (GameOps.guiInit, before its onGuiInit). */
    void disconnected(String address, String reason) {
        for (SteinMod m : mods) {
            try {
                m.onDisconnected(address, reason);
            } catch (Throwable e) {
                fail(m, "onDisconnected", e);
            }
        }
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
        for (SteinMod m : mods) {
            try {
                m.onTextureStitch(textureMap);
            } catch (Throwable e) {
                fail(m, "onTextureStitch", e);
            }
        }
    }

    @Override
    public void modelsReloaded() {
        TerrainOps.reset(); // the block colors come from the new textures
        for (SteinMod m : mods) {
            try {
                m.onModelsReloaded();
            } catch (Throwable e) {
                fail(m, "onModelsReloaded", e);
            }
        }
    }

    @Override
    public Object itemModel(Object stack, Object model) {
        Object r = model;
        for (SteinMod m : mods) {
            try {
                Object x = m.onItemModel(stack, r);
                if (x != null) {
                    r = x;
                }
            } catch (Throwable e) {
                fail(m, "onItemModel", e);
            }
        }
        return r;
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
        for (SteinMod m : mods) {
            try {
                if (m.onAttackEntity(target)) {
                    return true;
                }
            } catch (Throwable e) {
                fail(m, "onAttackEntity", e);
            }
        }
        return false;
    }

    @Override
    public boolean interactEntity(Object target) {
        for (SteinMod m : mods) {
            try {
                if (m.onInteractEntity(target)) {
                    return true;
                }
            } catch (Throwable e) {
                fail(m, "onInteractEntity", e);
            }
        }
        return false;
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
        for (SteinMod m : mods) {
            try {
                m.onSlotChanged(windowId, slot, stack);
            } catch (Throwable e) {
                fail(m, "onSlotChanged", e);
            }
        }
    }

    @Override
    public void windowItems(int windowId) {
        for (SteinMod m : mods) {
            try {
                m.onWindowItems(windowId);
            } catch (Throwable e) {
                fail(m, "onWindowItems", e);
            }
        }
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
        for (SteinMod m : mods) {
            try {
                m.onEntityDeath(entity);
            } catch (Throwable e) {
                fail(m, "onEntityDeath", e);
            }
        }
    }

    @Override
    public void healthChanged(
            Object entity,
            float oldHealth,
            float newHealth,
            float oldAbsorption,
            float newAbsorption) {
        for (SteinMod m : mods) {
            try {
                m.onHealthChanged(entity, oldHealth, newHealth, oldAbsorption, newAbsorption);
            } catch (Throwable e) {
                fail(m, "onHealthChanged", e);
            }
        }
    }

    @Override
    public boolean hideEntity(Object entity) {
        for (SteinMod m : mods) {
            try {
                if (m.onHideEntity(entity)) {
                    return true;
                }
            } catch (Throwable e) {
                fail(m, "onHideEntity", e);
            }
        }
        return false;
    }

    @Override
    public boolean ghostEntity(Object entity) {
        for (SteinMod m : mods) {
            try {
                if (m.onGhostEntity(entity)) {
                    return true;
                }
            } catch (Throwable e) {
                fail(m, "onGhostEntity", e);
            }
        }
        return false;
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
        String r = name;
        for (SteinMod m : mods) {
            try {
                String x = m.onPlayerListName(playerInfo, r);
                if (x != null) {
                    r = x;
                }
            } catch (Throwable e) {
                fail(m, "onPlayerListName", e);
            }
        }
        return r;
    }

    @Override
    public Object nametag(Object entity, Object name) {
        Object r = name;
        for (SteinMod m : mods) {
            try {
                Object x = m.onNametag(entity, r);
                if (x != null) {
                    r = x;
                }
            } catch (Throwable e) {
                fail(m, "onNametag", e);
            }
        }
        return r;
    }

    @Override
    public void renderWorld(float partialTicks) {
        LoaderRender r = LoaderRender.INSTANCE;
        r.world(true, partialTicks);
        // Under Stein GFX, every mod's world drawing is recorded in one strict-order session (one
        // pass, with the text cache) instead of one pass per draw; a no-op on the fixed pipeline.
        SteinEntityDeferred.worldBegin();
        try {
            for (int i = 0; i < mods.length; i++) {
                long t = System.nanoTime();
                r.nextSession();
                try {
                    mods[i].onRenderWorld(partialTicks);
                } catch (Throwable e) {
                    fail(mods[i], "onRenderWorld", e);
                }
                ModTimes.add(i, System.nanoTime() - t);
            }
            r.nextSession();
        } finally {
            SteinEntityDeferred.worldEnd();
            r.world(false, partialTicks);
        }
    }

    @Override
    public void customPayload(String channel, byte[] data) {
        for (SteinMod m : mods) {
            try {
                m.onCustomPayload(channel, data);
            } catch (Throwable e) {
                fail(m, "onCustomPayload", e);
            }
        }
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
