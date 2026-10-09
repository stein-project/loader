/*
 * Copyright (c) 2026 Breno Xavier. All rights reserved.
 * Proprietary software: see the LICENSE file at the root of this repository.
 */

package dev.xavier.stein.loader.hud;

import dev.xavier.stein.bridge.core.Log;
import dev.xavier.stein.bridge.hooks.VanillaHud;
import dev.xavier.stein.loader.Loader;
import dev.xavier.stein.loader.ModTimes;
import dev.xavier.stein.loader.api.HudElement;
import dev.xavier.stein.loader.api.Keys;
import dev.xavier.stein.loader.gui.KeyCodes;
import dev.xavier.stein.loader.gui.PanelScreen;
import dev.xavier.stein.loader.gui.Surface;
import dev.xavier.stein.loader.render.LoaderRender;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.settings.KeyBinding;

import org.lwjgl.opengl.GL11;

import java.io.File;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * The HUD grid: everything that stays on screen during the game — the game's own parts (hotbar,
 * health and hunger, experience, item name, chat, action bar, boss bar and scoreboard) and the
 * mods' elements —, each one's place in config/steinloader-hud.json, the drawing every frame and
 * the editor (right Shift), which is the only place to turn on and off what shows up.
 *
 * <p>Fixed rules: a 2 px border around the screen and 2 px between elements, nothing on top of
 * anything; mod elements start disabled and disappear with the F3 screen. A game part in the game's
 * place counts as in the game (flush against the border and the other parts), and "restore default"
 * leaves only those, as in the game without mods.
 */
public final class HudGrid {

    /**
     * Free border around the screen and minimum distance between two elements, in GUI pixels.
     * Fixed.
     */
    static final int MARGIN = 2;

    static final int GAP = 2;

    /** LWJGL key codes of the editor key (right Shift) and the Panel key (Insert). */
    private static final int KEY_RSHIFT = KeyCodes.RSHIFT;

    private static final int KEY_INSERT = KeyCodes.INSERT;

    private static final Map<HudElement, Boolean> failed = new IdentityHashMap<>();

    /** Opens the screen editor (right Shift by default); with Ctrl, opens the Panel. */
    static KeyBinding key;

    /** Opens the Panel directly; no key by default (Ctrl with the one above already does it). */
    static KeyBinding panelKey;

    private static boolean started;
    private static boolean broken;

    private HudGrid() {}

    /** In the Loader's afterStartGame, before the mods: file, key and the game HUD's parts. */
    public static void start() {
        if (started) {
            return;
        }
        started = true;
        Minecraft mc = Minecraft.getMinecraft();
        HudPersistence.open(new File(mc.mcDataDir, "config/steinloader-hud.json"));
        // The usual key ("key.steinloader.hud", right Shift by default) opens the screen editor:
        // it is what the player uses the most, and where the things on screen are. The Panel (the
        // mods' options, which are not on screen) has its own key, on Insert: both are in the
        // controls menu, and one does not depend on the other.
        key = new KeyBinding("key.steinloader.hud", KEY_RSHIFT, "key.categories.steinloader");
        Keys.register(key);
        panelKey =
                new KeyBinding("key.steinloader.panel", KEY_INSERT, "key.categories.steinloader");
        Keys.register(panelKey);
        panelKeyOnce();

        HudRegistry.addBuiltin(new HotbarPart());
        HudRegistry.addBuiltin(new StatusPart());
        HudRegistry.addBuiltin(new ItemNamePart());
        HudRegistry.addBuiltin(new ChatPart());
        HudRegistry.addBuiltin(new ActionBarElement());
        HudRegistry.addBuiltin(new BossBarElement());
        HudRegistry.addBuiltin(new SidebarElement());
        VanillaHud.setTakeover(true, true, true);
        VanillaHud.setTakeoverParts(true, true, true, true, true);
    }

    /** Registers a mod's element; it starts disabled. */
    public static void register(HudElement e) {
        HudRegistry.register(e);
    }

    public static boolean isEnabled(String id) {
        return HudRegistry.isEnabled(id);
    }

    public static Object editor(Object parent) {
        return new HudEditor((GuiScreen) parent);
    }

    /** The Panel with the mods' pages (what belongs to the screen is now in the editor itself). */
    private static PanelScreen panel() {
        return new PanelScreen(Loader.get().pages(), null);
    }

    public static void tick() {
        HudPersistence.autosave();
        if (key == null) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        // isPressed only accumulates with the game in the foreground (no screen open).
        while (key.isPressed()) {
            if (mc.currentScreen == null) {
                mc.displayGuiScreen(new HudEditor(null));
            }
        }
        while (panelKey != null && panelKey.isPressed()) {
            if (mc.currentScreen == null) {
                mc.displayGuiScreen(panel());
            }
        }
    }

    /**
     * The Panel key was born with no key at all, and whoever already played has that "no key" saved
     * in options.txt — changing the default to Insert would not reach those people. So, <b>once per
     * installation</b>, Insert is written for whoever has no key and the mark stays in the Loader's
     * configuration: whoever removes the key later, or picks another, does not see it come back on
     * the next start.
     */
    private static void panelKeyOnce() {
        Loader loader = Loader.get();
        if (loader == null || loader.panelKeySet()) {
            return;
        }
        loader.panelKeySet(true);
        if (panelKey.getKeyCode() != 0) {
            return; // it already had a chosen key
        }
        panelKey.setKeyCode(KEY_INSERT);
        KeyBinding.resetKeyBindingArrayAndHash();
        Minecraft.getMinecraft()
                .gameSettings
                .saveOptions(); // saved, otherwise the next start reads the previous one
        Log.info(
                "loader",
                "Panel key: Insert (first run; change it in Controls if you want" + " another)");
    }

    /** Turns an element on (finding a free place) or off, as the editor's list does. */
    static void toggle(HudElement e, boolean on) {
        Placement p = HudRegistry.placement(e);
        if (!on) {
            p.enabled = false;
            HudRegistry.box(e)[2] = 0;
            HudPersistence.save();
            return;
        }
        ScaledResolution r = new ScaledResolution(Minecraft.getMinecraft());
        HudCollision.enable(e, r.getScaledWidth(), r.getScaledHeight());
    }

    /**
     * The areas covered by a screen over the game (the Tab): the previous frame's apply to this
     * one.
     */
    private static final List<float[]> covers = new ArrayList<>();

    private static final List<float[]> coversNext = new ArrayList<>();

    /** {@link dev.xavier.stein.loader.api.Hud#cover}: applies from the next frame. */
    public static void cover(float x, float y, float w, float h) {
        if (w > 0 && h > 0) {
            coversNext.add(new float[] {x, y, w, h});
        }
    }

    /** Does the element's box (the previous frame's) lie under a covered area? */
    private static boolean covered(HudElement e) {
        if (covers.isEmpty()) {
            return false;
        }
        int[] b = HudRegistry.box(e);
        if (b[2] <= 0) {
            return false;
        }
        for (float[] c : covers) {
            if (b[0] < c[0] + c[2]
                    && c[0] < b[0] + b[2]
                    && b[1] < c[1] + c[3]
                    && c[1] < b[1] + b[3]) {
                return true;
            }
        }
        return false;
    }

    /** After the game's HUD and the mods' onOverlay (Loader.overlay). */
    public static void render(float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        if (broken || HudRegistry.elements.isEmpty() || mc.thePlayer == null) {
            return;
        }
        GuiScreen screen = mc.currentScreen;
        if (screen instanceof HudEditor) {
            return; // the editor draws its own
        }
        boolean chat = screen instanceof GuiChat;
        boolean debug = mc.gameSettings.showDebugInfo;
        ScaledResolution sr = new ScaledResolution(mc);
        int w = sr.getScaledWidth();
        int h = sr.getScaledHeight();
        HudText.reset();
        covers.clear();
        covers.addAll(coversNext);
        coversNext.clear();
        try {
            for (HudElement e : HudRegistry.elements) {
                Placement p = HudRegistry.placement(e);
                // With F3, only the game's parts (which the game shows together).
                if (!p.enabled
                        || screen != null && !e.visibleWithScreens() && !(chat && p.showInChat)
                        || debug && !HudRegistry.isBuiltin(e)) {
                    HudRegistry.box(e)[2] = 0;
                    if (e instanceof VanillaPart v) {
                        v.hidden();
                    }
                    continue;
                }
                if (covered(e)) {
                    // Under the Tab list: not drawn. The box stays as of the last frame, so the
                    // element comes back in place.
                    continue;
                }
                long t = System.nanoTime();
                draw(e, p, w, h, false, partialTicks);
                ModTimes.elementTime(e, System.nanoTime() - t);
            }
        } catch (Throwable t) {
            broken = true;
            // The game goes back to drawing its parts.
            VanillaHud.setTakeover(false, false, false);
            VanillaHud.setTakeoverParts(false, false, false, false, false);
            Log.error("loader", "HUD disabled", t);
        } finally {
            resetState();
        }
    }

    /**
     * Builds, positions by the anchor and draws an element. An error in a mod element: only it is
     * dropped.
     */
    static boolean draw(
            HudElement e,
            Placement p,
            int screenW,
            int screenH,
            boolean preview,
            float partialTicks) {
        int[] box = HudRegistry.box(e);
        if (failed.containsKey(e)) {
            box[2] = 0;
            return false;
        }
        // Everything the grid draws uses the HUD font (Stein's, with the option on): the mods'
        // elements and the game parts that live in it (chat, scoreboard, boss bar, item name).
        // Outside the grid, the game's text stays in its font.
        int gameFont = p.gameFont ? 1 : 0;
        HudText.enter(gameFont);
        try {
            if (!e.layout(preview, partialTicks)) {
                box[2] = 0;
                box[3] = 0;
                return false;
            }
            float s = p.scale;
            HudRegistry.place(e, p, box, screenW, screenH);
            int in = HudRegistry.inset(e);
            if (HudRegistry.background(e, p) > 0.0F) {
                // The default background, behind the whole box and outside the scale: the line gets
                // a real pixel.
                float a = HudRegistry.background(e, p);
                if (e instanceof ActionBarElement bar) {
                    a *= bar.opacity(); // fades together with the message
                }
                // A game part that does not fill the box (chat with few lines): the background
                // covers only what is occupied, and fades together with the lines.
                float shown = box[3] - in * 2;
                if (e instanceof VanillaPart part) {
                    shown = part.shownHeight() * s;
                    a *= part.shownAlpha();
                }
                if (shown > 0.5F) {
                    float bh = Math.min(box[3], shown + in * 2);
                    Surface.draw(box[0], box[1] + box[3] - bh, box[2], bh, a);
                }
            }
            GlStateManager.pushMatrix();
            GlStateManager.translate(box[0] + in, box[1] + in, 0.0F);
            if (s != 1.0F) {
                GlStateManager.scale(s, s, 1.0F);
            }
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            LoaderRender.INSTANCE.nextSession();
            try {
                e.draw(p.anchorX == 2, partialTicks);
            } finally {
                // What the element left (an unmatched push, a state) goes before the grid pops.
                LoaderRender.INSTANCE.nextSession();
                GlStateManager.popMatrix();
            }
            return true;
        } catch (RuntimeException | LinkageError ex) {
            if (HudRegistry.isBuiltin(e)) {
                throw ex; // game part: the whole grid gives the HUD back to the game
            }
            failed.put(e, Boolean.TRUE);
            box[2] = 0;
            Log.error("loader", "HUD: element " + e.id() + " disabled", ex);
            return false;
        } finally {
            HudText.exit(gameFont);
        }
    }

    /** Default HUD state after drawing (items leave lighting and normal enabled). */
    static void resetState() {
        RenderHelper.disableStandardItemLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
        GlStateManager.disableBlend();
        GlStateManager.enableDepth();
        GlStateManager.disableLighting();
        GlStateManager.disableRescaleNormal();
    }
}
