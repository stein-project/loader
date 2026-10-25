/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.loader;

import dev.xavier.stein.bridge.core.Input;
import dev.xavier.stein.bridge.core.Log;
import dev.xavier.stein.bridge.engine.KeyBindings;
import dev.xavier.stein.loader.api.Screens;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiDisconnected;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSelectWorld;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.inventory.SlotCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.Collections;
import java.util.List;

/**
 * Chat, screens, keys, world players and slots of the API (Chat, Screens, Keys, Entities, Players,
 * Inventory) against the game. All on the game thread.
 */
final class GameOps {

    private GameOps() {}

    static Minecraft mc() {
        return Minecraft.getMinecraft();
    }

    // --- Chat ---

    static void print(String text) {
        EntityPlayerSP p = mc().thePlayer;
        if (p != null && text != null) {
            p.addChatMessage(new ChatComponentText(text));
        }
    }

    static void actionBar(String text) {
        if (mc().ingameGUI != null && text != null) {
            mc().ingameGUI.setRecordPlaying(text, false);
        }
    }

    /**
     * Through the same path as what the player types: mods see it in onSendChat and can replace or
     * hold it.
     */
    static void send(String message) {
        EntityPlayerSP p = mc().thePlayer;
        Loader loader = Loader.get();
        String msg = message == null || loader == null ? message : loader.sendChat(message);
        if (p != null && msg != null && !msg.isEmpty()) {
            p.sendChatMessage(msg);
        }
    }

    static Object text(String text) {
        return new ChatComponentText(text == null ? "" : text);
    }

    static String plain(Object component) {
        // getUnformattedText only strips the styles' formatting: the "§a" written inside the text
        // stay, and are sent here.
        return component instanceof IChatComponent c
                ? EnumChatFormatting.getTextWithoutFormattingCodes(c.getUnformattedText())
                : "";
    }

    static String formatted(Object component) {
        if (!(component instanceof IChatComponent c)) {
            return "";
        }
        // A plain text without siblings (what mods build, and the game's names): the game's
        // getFormattedText walks an iterator over the component itself only and appends its style's
        // code, its text and the reset code — the same String, without the iterator, the array and
        // the builder it allocates (in a crowd, the game thread's biggest allocation: a mod paints
        // every name every frame). Anything else goes through the game's method.
        if (c.getClass() == ChatComponentText.class && c.getSiblings().isEmpty()) {
            return c.getChatStyle().getFormattingCode()
                    + c.getUnformattedTextForChat()
                    + EnumChatFormatting.RESET;
        }
        return c.getFormattedText();
    }

    // --- Screens ---

    static Object screen() {
        return mc().currentScreen;
    }

    static int kind(Object s) {
        Screens.Kind k;
        if (s == null) {
            k = Screens.Kind.NONE;
        } else if (s.getClass().getName().startsWith("dev.xavier.stein.loader.")) {
            k = Screens.Kind.STEIN;
        } else if (s instanceof GuiMainMenu) {
            k = Screens.Kind.MAIN_MENU;
        } else if (s instanceof GuiSelectWorld) {
            k = Screens.Kind.SINGLEPLAYER;
        } else if (s instanceof GuiMultiplayer) {
            k = Screens.Kind.MULTIPLAYER;
        } else if (s instanceof GuiConnecting) {
            k = Screens.Kind.CONNECTING;
        } else if (s instanceof GuiDisconnected) {
            k = Screens.Kind.DISCONNECTED;
        } else if (s instanceof GuiIngameMenu) {
            k = Screens.Kind.PAUSE;
        } else if (s instanceof GuiOptions) {
            k = Screens.Kind.OPTIONS;
        } else if (s instanceof GuiChat) {
            k = Screens.Kind.CHAT;
        } else if (s instanceof GuiInventory || s instanceof GuiContainerCreative) {
            k = Screens.Kind.INVENTORY;
        } else if (s instanceof GuiContainer) {
            k = Screens.Kind.CONTAINER;
        } else {
            k = Screens.Kind.OTHER;
        }
        return k.ordinal();
    }

    static int width(Object s) {
        return s instanceof GuiScreen g ? g.width : 0;
    }

    static int height(Object s) {
        return s instanceof GuiScreen g ? g.height : 0;
    }

    static void open(Object s) {
        mc().displayGuiScreen((GuiScreen) s);
    }

    static Object button(int id, int x, int y, int w, int h, String label) {
        return new GuiButton(id, x, y, w, h, label == null ? "" : label);
    }

    static int buttonId(Object b) {
        return b instanceof GuiButton g ? g.id : -1;
    }

    static int buttonX(Object b) {
        return b instanceof GuiButton g ? g.xPosition : 0;
    }

    static int buttonY(Object b) {
        return b instanceof GuiButton g ? g.yPosition : 0;
    }

    static void buttonLabel(Object b, String label) {
        if (b instanceof GuiButton g) {
            g.displayString = label == null ? "" : label;
        }
    }

    static void buttonEnabled(Object b, boolean on) {
        if (b instanceof GuiButton g) {
            g.enabled = on;
        }
    }

    // --- Connection and disconnection ---

    /**
     * The connection in progress or the last one tried, as {@link PlayerOps#serverAddress} writes
     * it.
     */
    static String connecting = "";

    /**
     * The disconnect screen already announced (once per screen, and not on every window resize).
     */
    private static Object disconnectedSeen;

    /**
     * Before the mods' onGuiInit: saves the server from the connect screen and announces the
     * disconnect one.
     */
    static void guiInit(Loader loader, Object screen) {
        if (screen instanceof GuiConnecting) {
            ServerData d = mc().getCurrentServerData();
            if (d != null && d.serverIP != null) {
                connecting = ServerOps.host(d.serverIP);
            }
        } else if (screen instanceof GuiDisconnected g && g != disconnectedSeen) {
            disconnectedSeen = g;
            loader.disconnected(connecting, reason(g));
        }
    }

    /** The disconnect screen's reason (GuiDisconnected.message, private in the game). */
    private static String reason(GuiDisconnected g) {
        try {
            IChatComponent m = g.message;
            return m == null ? "" : m.getUnformattedText();
        } catch (RuntimeException | LinkageError e) {
            return "";
        }
    }

    // --- Keys ---

    static Object bind(String name, int key, String category) {
        KeyBinding b = new KeyBinding(name, key, category);
        KeyBindings.register(b);
        return b;
    }

    static Object game(int which) {
        GameSettings s = mc().gameSettings;
        KeyBinding[] all = {
            s.keyBindForward,
            s.keyBindBack,
            s.keyBindLeft,
            s.keyBindRight,
            s.keyBindJump,
            s.keyBindSneak,
            s.keyBindSprint,
            s.keyBindAttack,
            s.keyBindUseItem,
            s.keyBindPickBlock,
            s.keyBindDrop,
            s.keyBindInventory,
            s.keyBindChat,
            s.keyBindCommand,
            s.keyBindPlayerList,
            s.keyBindTogglePerspective,
            s.keyBindScreenshot
        };
        return all[which];
    }

    static int keyCode(Object b) {
        return ((KeyBinding) b).getKeyCode();
    }

    /**
     * The new key, as the Controls screen records it: the key table rebuilt and options.txt saved.
     */
    static void keySet(Object b, int key) {
        KeyBinding k = (KeyBinding) b;
        if (k.getKeyCode() == key) {
            return;
        }
        k.setKeyCode(key);
        KeyBinding.resetKeyBindingArrayAndHash();
        mc().gameSettings.saveOptions();
    }

    static boolean keyIsDown(Object b) {
        return ((KeyBinding) b).isKeyDown();
    }

    static boolean keyConsume(Object b) {
        return ((KeyBinding) b).isPressed();
    }

    static void keyHold(Object b, boolean held) {
        int code = ((KeyBinding) b).getKeyCode();
        if (code != 0) {
            KeyBinding.setKeyBindState(code, held || physical(code));
        }
        if (held && b == mc().gameSettings.keyBindUseItem) {
            // Holding use is for keeping the item in use (eating, blocking). Without this, the game
            // would also repeat the right click every 4 ticks with use pressed and nothing in use
            // — and would open the chest, or touch the block, that was under the crosshair.
            setRightClickDelay(4);
        }
    }

    /** Minecraft.rightClickDelayTimer is private in the game: if binding it fails, no retry. */
    private static boolean rightClickDelayMissing;

    private static void setRightClickDelay(int ticks) {
        if (rightClickDelayMissing) {
            return;
        }
        try {
            Minecraft mc = mc();
            mc.rightClickDelayTimer = Math.max(ticks, mc.rightClickDelayTimer);
        } catch (RuntimeException | LinkageError e) {
            rightClickDelayMissing = true;
            Log.info("loader", "rightClickDelayTimer: " + e);
        }
    }

    static boolean physical(int code) {
        if (code < 0) {
            return Mouse.isButtonDown(code + 100);
        }
        return code > 0 && Keyboard.isKeyDown(code);
    }

    /**
     * A button click in the mouse event queue, like the physical mouse (the Bridge puts it there).
     */
    static void mouseClick(int button) {
        Input.syntheticClick(button);
    }

    static boolean ctrlDown() {
        return GuiScreen.isCtrlKeyDown();
    }

    static boolean mouseDown(int button) {
        return Mouse.isButtonDown(button);
    }

    static int mouseButton() {
        return Mouse.getEventButton();
    }

    static boolean mousePressed() {
        return Mouse.getEventButtonState();
    }

    static int mouseWheel() {
        return Mouse.getEventDWheel();
    }

    // --- Entities and Players ---

    static List<Object> players() {
        return mc().theWorld == null
                ? List.of()
                : Collections.unmodifiableList(mc().theWorld.playerEntities);
    }

    static Object player(String nick) {
        return mc().theWorld == null || nick == null
                ? null
                : mc().theWorld.getPlayerEntityByName(nick);
    }

    static boolean isPlayer(Object e) {
        return e instanceof EntityPlayer;
    }

    static String name(Object e) {
        return e instanceof Entity en ? en.getName() : null;
    }

    static boolean invisible(Object e) {
        return e instanceof Entity en && en.isInvisible();
    }

    static float distance(Object a, Object b) {
        return a instanceof Entity ea && b instanceof Entity eb
                ? ea.getDistanceToEntity(eb)
                : Float.MAX_VALUE;
    }

    static String displayName(String nick) {
        NetworkPlayerInfo info = PlayerInfos.get(nick);
        IChatComponent shown = info == null ? null : info.getDisplayName();
        return shown == null ? null : shown.getFormattedText();
    }

    static String nickOf(Object info) {
        return info instanceof NetworkPlayerInfo i && i.getGameProfile() != null
                ? i.getGameProfile().getName()
                : null;
    }

    // --- Inventory ---

    private static Slot slot(int slot) {
        EntityPlayerSP p = mc().thePlayer;
        Container c = p == null ? null : p.openContainer;
        return c == null || slot < 0 || slot >= c.inventorySlots.size() ? null : c.getSlot(slot);
    }

    static boolean accepts(int slot, Object stack) {
        Slot s = slot(slot);
        return s != null && stack instanceof ItemStack st && s.isItemValid(st);
    }

    static int room(int slot) {
        Slot s = slot(slot);
        if (s == null) {
            return 0;
        }
        ItemStack there = s.getStack();
        if (there == null) {
            return s.getSlotStackLimit();
        }
        return Math.max(
                0, Math.min(s.getSlotStackLimit(), there.getMaxStackSize()) - there.stackSize);
    }

    static boolean crafting(int slot) {
        Slot s = slot(slot);
        return s != null && (s instanceof SlotCrafting || s.inventory instanceof InventoryCrafting);
    }

    static boolean stacksWith(Object a, Object b) {
        return a instanceof ItemStack x
                && b instanceof ItemStack y
                && x.getItem() == y.getItem()
                && x.getMetadata() == y.getMetadata()
                && ItemStack.areItemStackTagsEqual(x, y);
    }
}
