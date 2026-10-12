/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.loader.gui;

import dev.xavier.stein.bridge.SteinBridge;
import dev.xavier.stein.bridge.core.SteinLog;
import dev.xavier.stein.loader.Loader;
import dev.xavier.stein.loader.ModInfo;
import dev.xavier.stein.loader.Remote;
import dev.xavier.stein.loader.font.GameText;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.awt.Desktop;
import java.io.File;
import java.util.List;

/**
 * The Stein Loader menu: a panel that slides in from the right over the screen it came from. Mods
 * page (list, details, enable/disable, configure) and About page. Esc, the tab or a click outside
 * the panel close it.
 */
public final class LoaderScreen extends GuiScreen {

    private static final int ANIM_MS = 220;
    private static final int ROW = 28;
    private static final int PAGES_Y = 60;
    private static final int TOP = 80;
    private static final int FOOTER = 24;
    private static final int TOGGLE = 1;
    private static final int CONFIG = 2;
    private static final int OPEN_LOG_DIR = 3;
    private static final int LOG = 1;
    private static final int LOOK = 2;
    private static final int ABOUT = 3;

    /** Height of the Discord button. */
    private static final int DISCORD_H = 20;

    private static final String[] PAGES = {
        "steinloader.page.mods",
        "steinloader.page.log",
        "steinloader.page.look",
        "steinloader.page.about"
    };

    private final Loader loader;
    private final GuiScreen parent;
    private final List<ModInfo> mods;
    private final Tab edge = new Tab();
    private final int[] pageX = new int[PAGES.length];
    private final int[] pageW = new int[PAGES.length];
    private long openedAt;
    private long closedAt;

    /**
     * How much time has passed since the previous frame: the switch slides at the same pace at any
     * frame rate.
     */
    /** Where the Discord button ended up on the About tab (-1 when there is no invite). */
    private int discordX;

    private int discordY = -1;
    private int discordW;
    private long delta;
    private long lastFrame;
    private boolean closing;
    private int page;
    private int selected;
    private int scroll;
    private PillButton toggle;
    private PillButton config;
    private PillButton openLogDir;

    // Geometry of the current frame: the panel slides.
    private int px;
    private int pw;
    private int listX;
    private int listW;
    private int listH;
    private int dx;
    private int dw;

    public LoaderScreen(Loader loader, GuiScreen parent) {
        this.loader = loader;
        this.parent = parent;
        this.mods = loader.mods();
        for (int i = 0; i < mods.size(); i++) {
            if (!mods.get(i).builtIn()) {
                selected = i;
                break;
            }
        }
    }

    @Override
    public void initGui() {
        if (parent != null) {
            parent.setWorldAndResolution(this.mc, this.width, this.height);
        }
        // The button width is protected: it comes from the geometry of the open panel, and only
        // the position follows the slide.
        layout(1.0F);
        int bw = Math.max(40, Math.min(100, (dw - 4) / 2));
        this.buttonList.clear();
        toggle = new PillButton(TOGGLE, 0, 0, bw, 18, "");
        config = new PillButton(CONFIG, 0, 0, bw, 18, I18n.format("steinloader.button.config"));
        openLogDir =
                new PillButton(
                        OPEN_LOG_DIR,
                        0,
                        0,
                        Math.max(80, Math.min(140, pw - 20)),
                        18,
                        I18n.format("steinloader.log.open"));
        this.buttonList.add(toggle);
        this.buttonList.add(config);
        this.buttonList.add(openLogDir);
        if (openedAt == 0L) {
            openedAt = Ui.now();
        }
        closing = false;
        layout(progress());
    }

    private void layout(float p) {
        pw = Math.min(this.width - 40, 420);
        px = this.width - Math.round(pw * p);
        listX = px + 8;
        listW = Math.min(170, pw * 2 / 5);
        listH = this.height - FOOTER - TOP;
        dx = listX + listW + 10;
        dw = px + pw - 10 - dx;
    }

    private float linear() {
        long t = Ui.now();
        float f =
                closing
                        ? 1.0F - (t - closedAt) / (float) ANIM_MS
                        : (t - openedAt) / (float) ANIM_MS;
        return Ui.clamp01(f);
    }

    private float progress() {
        return Ui.ease(linear());
    }

    /** Closes by sliding, from wherever the opening is. */
    private void close() {
        if (!closing) {
            float lin = linear();
            closing = true;
            closedAt = Ui.now() - Math.round((1.0F - lin) * ANIM_MS);
        }
    }

    // ---- drawing ----

    @Override
    public void drawScreen(int mx, int my, float partialTicks) {
        float p = progress();
        if (closing && p <= 0.0F) {
            // The swap frame still needs an image: without it, only the world would show (without
            // the pause menu's darkening) for one frame — the flicker when closing the tab.
            if (parent != null) {
                parent.drawScreen(mx, my, partialTicks);
            } else {
                this.drawDefaultBackground();
            }
            this.mc.displayGuiScreen(parent);
            return;
        }
        long now = Ui.now();
        delta = lastFrame == 0L ? 16L : now - lastFrame;
        lastFrame = now;
        layout(p);
        int w = this.width;
        int h = this.height;
        if (parent != null) {
            parent.drawScreen(-1, -1, partialTicks);
        } else {
            this.drawDefaultBackground();
        }
        Ui.rect(0, 0, w, h, Math.round(0x90 * p) << 24);
        Ui.rect(px, 0, w, h, Theme.PANEL);
        Ui.hairV(px, 0, h, Theme.RULE_STRONG);
        edge.draw(px, Tab.top(h), mx, my, I18n.format("steinloader.close"));
        header();
        pages(mx, my);
        if (page == 0) {
            modsPage(mx, my);
        } else if (page == LOG) {
            logPage(mx, my);
        } else if (page == LOOK) {
            lookPage(mx, my);
        } else {
            aboutPage(h, mx, my);
        }
        if (loader.restartPending()) {
            Ui.text(
                    Ui.trim(I18n.format("steinloader.restart"), pw - 20),
                    px + 10,
                    h - 16,
                    Theme.WARN);
        }
        buttons();
        super.drawScreen(mx, my, partialTicks);
    }

    /** Stein Loader logo in the header, at the texture's aspect ratio (1.41). */
    private static final int LOGO_H = 48;

    private static final int LOGO_W = 67;

    private void header() {
        Ui.image(Ui.LOGO, px + 10, 6, LOGO_W, LOGO_H, 1.0F);
        int x = px + 20 + LOGO_W;
        int tw = pw - 30 - LOGO_W;
        // The name as a title, the versions in golden mono (the design system's numbers), the
        // author as a label.
        String brand = I18n.format("steinloader.brand.loader");
        Ui.title(brand, x, 13, Theme.HEADING, Theme.INK);
        Ui.mono("v" + Loader.VERSION, x + Ui.titleWidth(brand, Theme.HEADING) + 6, 16, Theme.GOLD);
        Ui.label(SteinBridge.NAME, x, 30, Theme.MUTED);
        Ui.mono(
                "v" + Loader.bridgeVersion(),
                x + Ui.labelWidth(SteinBridge.NAME) + 5,
                30,
                Theme.GOLD);
        Ui.label(I18n.format("steinloader.by", SteinBridge.VENDOR), x, 41, Theme.FAINT);
    }

    private void pages(int mx, int my) {
        int x = px + 10;
        for (int i = 0; i < PAGES.length; i++) {
            String s = I18n.format(PAGES[i]) + (i == 0 ? " (" + loader.loadedCount() + ")" : "");
            pageX[i] = x;
            pageW[i] = Ui.labelWidth(s);
            Ui.label(
                    s,
                    x,
                    PAGES_Y,
                    i == page ? Theme.INK : overPage(i, mx, my) ? Theme.GOLD : Theme.MUTED);
            if (i == page) {
                Ui.rect(x, PAGES_Y + 12, x + pageW[i], PAGES_Y + 13, Theme.ACCENT);
            }
            x += pageW[i] + 16;
        }
        Ui.hair(px + 8, PAGES_Y + 13, pw - 16, Theme.RULE_STRONG);
    }

    private boolean overPage(int i, int mx, int my) {
        return my >= PAGES_Y - 3
                && my < PAGES_Y + 12
                && mx >= pageX[i] - 3
                && mx < pageX[i] + pageW[i] + 3;
    }

    private void modsPage(int mx, int my) {
        int total = mods.size() * ROW;
        scroll = Math.max(0, Math.min(scroll, total - listH));
        Ui.clip(listX, TOP, listW, listH);
        for (int i = 0; i < mods.size(); i++) {
            int y = TOP + i * ROW - scroll;
            if (y + ROW <= TOP || y >= TOP + listH) {
                continue;
            }
            ModInfo m = mods.get(i);
            if (i == selected) {
                Ui.rect(listX, y, listX + listW, y + ROW - 2, Theme.ACCENT_SOFT);
                Ui.rect(listX, y, listX + 1, y + ROW - 2, Theme.ACCENT);
            } else if (inList(mx, my) && my >= y && my < y + ROW) {
                Ui.rect(listX, y, listX + listW, y + ROW - 2, Widgets.SURFACE);
            }
            Ui.hair(listX, y + ROW - 1, listW, Theme.RULE);
            boolean on = loader.isEnabled(m);
            boolean toggles = !m.builtIn() && !m.rejected();
            int tw = listW - 8 - (toggles ? 22 : 0);
            Ui.title(
                    Ui.trim(m.name, tw),
                    listX + 6,
                    y + 5,
                    Theme.TEXT,
                    m.builtIn()
                            ? Theme.GOLD
                            : m.rejected() ? Theme.FAINT : on ? Theme.INK : Theme.MUTED);
            // The rejected one shows the reason where the others show version and author: it is
            // what matters about it.
            if (m.rejected()) {
                Ui.mono(m.reason(), listX + 6, y + 17, Theme.DANGER);
            } else {
                Ui.mono("v" + m.version, listX + 6, y + 17, Theme.GOLD);
                if (!m.author.isEmpty()) {
                    Ui.mono(
                            m.author,
                            listX + 6 + Ui.monoWidth("v" + m.version) + 6,
                            y + 17,
                            Theme.FAINT);
                }
            }
            if (toggles) {
                knob(
                        "mods|" + m.id,
                        listX + listW - 21,
                        y + 9,
                        on,
                        m.state() == ModInfo.State.FAILED);
            }
        }
        Ui.unclip();
        if (total > listH) {
            int bh = Math.max(10, listH * listH / total);
            int by = TOP + (listH - bh) * scroll / (total - listH);
            Ui.rect(listX + listW + 2, by, listX + listW + 3, by + bh, Theme.RULE_STRONG);
        }
        details(selected());
    }

    /**
     * The chosen mod. The list changes size between runs, and a screen must not crash on a stale
     * index.
     */
    private ModInfo selected() {
        selected = Math.max(0, Math.min(selected, mods.size() - 1));
        return mods.get(selected);
    }

    /**
     * On/off switch: the Widgets one, the same that the Panel and the screen editor use — this
     * screen's rectangle, now sliding instead of jumping. Two screens with the same switch are the
     * same thing in the eyes of the user.
     */
    private void knob(String key, int x, int y, boolean on, boolean failed) {
        Widgets.knob(key, x, y, on, failed ? Theme.DANGER : Theme.ACCENT, 1.0F, delta);
    }

    private void details(ModInfo m) {
        int bottom = TOP + listH - (m.builtIn() || m.rejected() ? 0 : 26);
        int y = TOP;
        Ui.title(Ui.trim(m.name, dw - 8), dx, y + 2, Theme.HEADING, Theme.INK);
        y += 20;
        y = line(I18n.format("steinloader.mod.version", m.version), y, Theme.INK);
        if (!m.author.isEmpty()) {
            y = line(I18n.format("steinloader.mod.author", m.author), y, Theme.INK);
        }
        y = line(I18n.format("steinloader.mod.id", m.id), y, Theme.MUTED);
        if (m.file != null) {
            y = line(I18n.format("steinloader.mod.file", m.file.getName()), y, Theme.MUTED);
        }
        y = line(I18n.format("steinloader.mod.state", state(m)), y, Theme.INK);
        Ui.hair(dx, y + 3, dw, Theme.RULE);
        String d =
                m.builtIn()
                        ? I18n.format(m.description)
                        : m.description.isEmpty()
                                ? I18n.format("steinloader.mod.nodesc")
                                : m.description;
        y = wrap(d, y + 9, bottom, Theme.MUTED);
        String why = m.reason() != null ? m.reason() : m.error();
        if (why != null) {
            wrap(why, y + 4, bottom, Theme.DANGER);
        }
    }

    private String state(ModInfo m) {
        boolean wanted = loader.isEnabled(m);
        ModInfo.State s = m.state();
        if (s == ModInfo.State.BUILTIN) {
            return I18n.format("steinloader.state.builtin");
        }
        if (s == ModInfo.State.REJECTED) {
            return "§c" + I18n.format("steinloader.state.rejected");
        }
        if (s == ModInfo.State.LOADED) {
            return wanted
                    ? "§a" + I18n.format("steinloader.state.loaded")
                    : "§e" + I18n.format("steinloader.state.disabling");
        }
        if (s == ModInfo.State.DISABLED) {
            return wanted
                    ? "§e" + I18n.format("steinloader.state.enabling")
                    : "§7" + I18n.format("steinloader.state.disabled");
        }
        return wanted
                ? "§c" + I18n.format("steinloader.state.failed")
                : "§e" + I18n.format("steinloader.state.disabling");
    }

    private int line(String s, int y, int rgb) {
        Ui.text(Ui.trim(s, dw), dx, y, rgb);
        return y + 12;
    }

    private int wrap(String s, int y, int bottom, int rgb) {
        // Narrower than one character, the game's listFormattedStringToWidth does not terminate
        // (endless recursion): a tiny window leaves the description out.
        if (dw < 16) {
            return y;
        }
        for (String o : Ui.wrap(s, dw)) {
            if (y + 11 > bottom) {
                break;
            }
            Ui.text(o, dx, y, rgb);
            y += 12;
        }
        return y;
    }

    /**
     * Log tab: the switch for Stein's log and, when on, this session's file and how much has been
     * written so far. It is what the player sends along when reporting a problem; the button opens
     * the folder so they can find the file.
     */
    private void logPage(int mx, int my) {
        int x = px + 10;
        int w = pw - 20;
        SteinLog.Topic[] topics = SteinLog.Topic.values();
        for (int i = 0; i < topics.length; i++) {
            int y = TOP + i * LOG_ROW;
            if (overLogKnob(i, mx, my)) {
                Ui.rect(x - 2, y - 3, x + w + 2, y + 13, Widgets.SURFACE_HOVER);
            }
            Ui.text(
                    Ui.trim(I18n.format("steinloader.log." + topics[i].key), w - 24),
                    x,
                    y,
                    Theme.INK);
            knob("log|" + topics[i].key, x + w - 18, y + 1, SteinLog.on(topics[i]), false);
        }
        int y = TOP + topics.length * LOG_ROW + 6;
        File f = SteinLog.file();
        if (f != null) {
            y = logLine(f.getName(), x, y, w, Theme.INK);
            String lines = I18n.format("steinloader.log.lines", SteinLog.written());
            if (SteinLog.dropped() > 0) {
                lines += "  §c" + I18n.format("steinloader.log.dropped", SteinLog.dropped());
            }
            logLine(lines, x, y, w, Theme.MUTED);
        }
    }

    /**
     * Appearance tab: where Stein's font applies. The Loader screens always use it; here you choose
     * whether the mods' HUD does too, or stays on the Minecraft font. The game's own text never
     * changes.
     */
    private void lookPage(int mx, int my) {
        int x = px + 10;
        int w = pw - 20;
        Ui.label(I18n.format("steinloader.look.font"), x, TOP, Theme.GOLD);
        String[] keys = {"steinloader.look.hud"};
        boolean[] on = {GameText.hud};
        for (int i = 0; i < keys.length; i++) {
            int y = TOP + 18 + i * LOOK_ROW;
            if (overLookKnob(i, mx, my)) {
                Ui.rect(x - 2, y - 3, x + w + 2, y + 23, Widgets.SURFACE_HOVER);
            }
            Ui.text(Ui.trim(I18n.format(keys[i]), w - 24), x, y, Theme.INK);
            Ui.text(Ui.trim(I18n.format(keys[i] + ".hint"), w - 24), x, y + 11, Theme.FAINT);
            knob("font|" + i, x + w - 18, y + 6, on[i], false);
        }
    }

    /** Height of a row (name and explanation) on the Appearance tab. */
    private static final int LOOK_ROW = 30;

    private boolean overLookKnob(int i, int mx, int my) {
        int y = TOP + 18 + i * LOOK_ROW;
        return page == LOOK && my >= y - 3 && my < y + 23 && mx >= px + 10 && mx < px + pw - 10;
    }

    /** Height of a switch row on the Log tab. */
    private static final int LOG_ROW = 20;

    private boolean overLogKnob(int i, int mx, int my) {
        int y = TOP + i * LOG_ROW;
        return page == LOG && my >= y - 3 && my < y + 13 && mx >= px + 10 && mx < px + pw - 10;
    }

    private int logLine(String s, int x, int y, int w, int rgb) {
        Ui.text(Ui.trim(s, w), x, y, rgb);
        return y + 12;
    }

    private void aboutPage(int h, int mx, int my) {
        int cx = px + pw / 2;
        String[] lines = {
            "§lStein Loader v" + Loader.VERSION,
            I18n.format("steinloader.about.loader"),
            "",
            "§l" + SteinBridge.NAME + " v" + Loader.bridgeVersion(),
            I18n.format("steinloader.about.bridge"),
            "Java " + System.getProperty("java.version"),
            "",
            I18n.format("steinloader.mods.count", loader.loadedCount(), loader.activeCount()),
        };
        int y = TOP + 8;
        for (String s : lines) {
            if (y + 9 > h - FOOTER) {
                break;
            }
            if (!s.isEmpty()) {
                if (s.charAt(0) == '\u00a7') {
                    String t = s.substring(2);
                    Ui.title(t, cx - Ui.titleWidth(t, Theme.TITLE) / 2, y, Theme.TITLE, Theme.INK);
                } else {
                    Ui.centered(Ui.trim(s, pw - 20), cx, y, Theme.MUTED);
                }
            }
            y += s.isEmpty() ? 8 : 13;
        }
        discordButton(y + 10, cx, mx, my);
    }

    /**
     * The Stein Labs Discord invite, on the About tab: where whoever is looking for the project
     * (and not a mod) lands. It only appears when there is an invite — the address comes from the
     * public file, not from the jar.
     */
    private void discordButton(int y, int cx, int mx, int my) {
        if (Remote.discord().isEmpty()) {
            discordY = -1;
            return;
        }
        // The project's name, not "Discord": the icon already says what the button opens.
        String label = I18n.format("steinloader.brand.labs");
        int w = Math.min(pw - 40, Ui.labelWidth(label) + 46);
        int x = cx - w / 2;
        boolean hover = overDiscord(x, y, w, mx, my);
        float rr = DISCORD_H / 2.0F;
        if (hover) {
            Shapes.fill(x, y, w, DISCORD_H, rr, Theme.ACCENT);
        } else {
            Shapes.stroke(x, y, w, DISCORD_H, rr, 1, Theme.RULE_STRONG);
        }
        Ui.tinted(
                Discord.MARK,
                x + 10,
                y + (DISCORD_H - 16) / 2.0F,
                16,
                16,
                hover ? Theme.ON_ACCENT : Theme.GOLD);
        Ui.label(label, x + 31, y + (DISCORD_H - 8) / 2, hover ? Theme.ON_ACCENT : Theme.INK);
        discordX = x;
        discordY = y;
        discordW = w;
    }

    private boolean overDiscord(int x, int y, int w, int mx, int my) {
        return mx >= x && mx < x + w && my >= y && my < y + DISCORD_H;
    }

    /** Buttons of the chosen mod, at the foot of the details; they follow the panel. */
    private void buttons() {
        ModInfo m = selected();
        boolean show = page == 0 && !m.builtIn() && !m.rejected();
        toggle.visible = show;
        config.visible = show;
        openLogDir.visible = page == LOG;
        if (openLogDir.visible) {
            openLogDir.xPosition = px + 10;
            openLogDir.yPosition = TOP + listH - 22;
        }
        if (!show) {
            return;
        }
        int by = TOP + listH - 20;
        toggle.primary =
                !loader.isEnabled(m); // enabling is the calling action; disabling is discreet
        toggle.xPosition = dx;
        toggle.yPosition = by;
        toggle.displayString =
                I18n.format(
                        loader.isEnabled(m)
                                ? "steinloader.button.disable"
                                : "steinloader.button.enable");
        config.xPosition = dx + (dw - 4) / 2 + 4 > dx + 104 ? dx + 104 : dx + (dw - 4) / 2 + 4;
        config.yPosition = by;
        config.enabled = m.state() == ModInfo.State.LOADED && m.hasConfig();
    }

    /**
     * Opens the folder in the file manager, on another thread: Desktop may take a while to respond.
     */
    private static void openDir(File dir) {
        dir.mkdirs();
        Ui.openDesktop("stein-open-folder", dir, () -> Desktop.getDesktop().open(dir));
    }

    // ---- input ----

    private boolean inList(int mx, int my) {
        return mx >= listX && mx < listX + listW && my >= TOP && my < TOP + listH;
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) {
        if (closing) {
            return;
        }
        if (mx < px) { // outside the panel, or on the tab
            if (button == 0) {
                Ui.click();
                close();
            }
            return;
        }
        if (button == 0) {
            for (int i = 0; i < PAGES.length; i++) {
                if (overPage(i, mx, my)) {
                    if (page != i) {
                        page = i;
                        Ui.click();
                    }
                    return;
                }
            }
            if (page == ABOUT
                    && discordY >= 0
                    && overDiscord(discordX, discordY, discordW, mx, my)) {
                Ui.click();
                Discord.open();
                return;
            }
            if (page == LOOK) {
                if (overLookKnob(0, mx, my)) {
                    loader.setHudFont(!GameText.hud);
                    Ui.click();
                    return;
                }
            }
            if (page == LOG) {
                SteinLog.Topic[] topics = SteinLog.Topic.values();
                for (int i = 0; i < topics.length; i++) {
                    if (overLogKnob(i, mx, my)) {
                        loader.setDebugLog(topics[i], !SteinLog.on(topics[i]));
                        Ui.click();
                        return;
                    }
                }
            }
            if (page == 0 && inList(mx, my)) {
                int i = (my - TOP + scroll) / ROW;
                if (i >= 0 && i < mods.size()) {
                    ModInfo m = mods.get(i);
                    if (!m.builtIn() && !m.rejected() && mx >= listX + listW - 24) {
                        loader.setEnabled(m, !loader.isEnabled(m));
                    }
                    selected = i;
                    Ui.click();
                }
                return;
            }
        }
        super.mouseClicked(mx, my, button);
    }

    @Override
    protected void actionPerformed(GuiButton b) {
        ModInfo m = selected();
        if (b.id == OPEN_LOG_DIR) {
            openDir(SteinLog.dir());
            return;
        }
        if (b.id == TOGGLE) {
            loader.setEnabled(m, !loader.isEnabled(m));
        } else if (b.id == CONFIG) {
            Object s = loader.configScreen(m, this);
            if (s instanceof GuiScreen) {
                this.mc.displayGuiScreen((GuiScreen) s);
            }
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int d = Mouse.getEventDWheel();
        if (d != 0 && page == 0) {
            scroll += d > 0 ? -ROW : ROW;
        }
    }

    @Override
    protected void keyTyped(char c, int key) {
        if (key == Keyboard.KEY_ESCAPE) {
            close();
        }
    }

    @Override
    public void updateScreen() {
        if (parent != null) {
            parent.updateScreen();
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return parent != null && parent.doesGuiPauseGame();
    }
}
