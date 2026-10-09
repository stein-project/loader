/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.loader;

import static dev.xavier.stein.loader.GameOps.mc;
import static dev.xavier.stein.loader.PlayerOps.player;

import dev.xavier.stein.bridge.hooks.GameHooks;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C0DPacketCloseWindow;
import net.minecraft.network.play.client.C0EPacketClickWindow;
import net.minecraft.network.play.client.C16PacketClientStatus;

import java.util.List;

/**
 * The API's inventory (Inventory) against the game: windows, slots, held item and virtual
 * containers. Clicks go through {@link GameHooks#windowClick}, the same path as the game's own
 * clicks after the patch: mods see them in onWindowClick.
 */
final class InventoryOps {

    private InventoryOps() {}

    /** The current window: the open container, or the player's inventory one. */
    private static Container window() {
        EntityPlayerSP p = player();
        return p == null ? null : p.openContainer;
    }

    static int windowId() {
        Container c = window();
        return c == null ? -1 : c.windowId;
    }

    static boolean containerOpen() {
        EntityPlayerSP p = player();
        return p != null && p.openContainer != p.inventoryContainer;
    }

    static boolean screenOpen() {
        return mc().currentScreen instanceof GuiContainer;
    }

    static int slotCount() {
        Container c = window();
        return c == null ? 0 : c.inventorySlots.size();
    }

    static Object slotStack(int slot) {
        Container c = window();
        if (c == null || slot < 0 || slot >= c.inventorySlots.size()) {
            return null;
        }
        return c.getSlot(slot).getStack();
    }

    static int slotOf(int index) {
        EntityPlayerSP p = player();
        if (p == null || index < 0) {
            return -1;
        }
        List<Slot> slots = p.openContainer.inventorySlots;
        for (int i = 0; i < slots.size(); i++) {
            if (slots.get(i).isHere(p.inventory, index)) {
                return i;
            }
        }
        return -1;
    }

    static int indexOf(int slot) {
        EntityPlayerSP p = player();
        if (p == null || slot < 0 || slot >= p.openContainer.inventorySlots.size()) {
            return -1;
        }
        Slot s = p.openContainer.getSlot(slot);
        if (s.inventory != p.inventory) {
            return -1;
        }
        for (int i = 0; i < 40; i++) {
            if (s.isHere(p.inventory, i)) {
                return i;
            }
        }
        return -1;
    }

    static Object stack(int index) {
        EntityPlayerSP p = player();
        if (p == null || index < 0 || index >= 40) {
            return null;
        }
        return p.inventory.getStackInSlot(index);
    }

    static Object cursor() {
        EntityPlayerSP p = player();
        return p == null ? null : p.inventory.getItemStack();
    }

    static int selected() {
        EntityPlayerSP p = player();
        return p == null ? 0 : p.inventory.currentItem;
    }

    static int firstEmpty() {
        EntityPlayerSP p = player();
        return p == null ? -1 : p.inventory.getFirstEmptyStack();
    }

    static int armorPiece(Object stack) {
        if (!(stack instanceof ItemStack)
                || !(((ItemStack) stack).getItem() instanceof ItemArmor)) {
            return -1;
        }
        // the game's armorType: 0 helmet ... 3 boots; the API's piece follows the inventory index
        // (0 boots ... 3 helmet).
        return 3 - ((ItemArmor) ((ItemStack) stack).getItem()).armorType;
    }

    static int armorValue(Object stack) {
        if (!(stack instanceof ItemStack)
                || !(((ItemStack) stack).getItem() instanceof ItemArmor)) {
            return 0;
        }
        return ((ItemArmor) ((ItemStack) stack).getItem()).damageReduceAmount;
    }

    static boolean click(int slot, int button, int mode) {
        EntityPlayerSP p = player();
        PlayerControllerMP pc = mc().playerController;
        if (p == null || pc == null) {
            return false;
        }
        Container c = p.openContainer;
        if (slot != -999 && (slot < 0 || slot >= c.inventorySlots.size())) {
            return false;
        }
        try {
            GameHooks.windowClick(pc, c.windowId, slot, button, mode, p);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
        return true;
    }

    /**
     * Right click in the air with the held item: PlayerControllerMP sends the hotbar position and
     * the use.
     */
    static boolean useHeld() {
        Minecraft mc = mc();
        EntityPlayerSP p = player();
        if (p == null
                || mc.playerController == null
                || mc.theWorld == null
                || p.getHeldItem() == null) {
            return false;
        }
        return mc.playerController.sendUseItem(p, mc.theWorld, p.getHeldItem());
    }

    static boolean using() {
        EntityPlayerSP p = player();
        return p != null && p.isUsingItem();
    }

    static void select(int hotbar) {
        EntityPlayerSP p = player();
        if (p != null && hotbar >= 0 && hotbar < 9) {
            p.inventory.currentItem =
                    hotbar; // PlayerControllerMP sends it to the server on the next tick
        }
    }

    static void open() {
        Minecraft mc = mc();
        EntityPlayerSP p = player();
        if (p == null) {
            return;
        }
        // Like the E key: the achievement notice to the server and the screen.
        mc.getNetHandler()
                .addToSendQueue(
                        new C16PacketClientStatus(
                                C16PacketClientStatus.EnumState.OPEN_INVENTORY_ACHIEVEMENT));
        mc.displayGuiScreen(new GuiInventory(p));
    }

    static void close() {
        EntityPlayerSP p = player();
        if (p != null && mc().currentScreen instanceof GuiContainer) {
            p.closeScreen();
        }
    }

    /**
     * The next chest the server opens becomes a virtual container (no screen), until this instant
     * (nanos).
     */
    private static long virtualUntil;

    static {
        GameHooks.chestScreen =
                screen -> {
                    if (Boolean.getBoolean("steinloader.debugChest")) {
                        System.out.println(
                                "[chest] the server opened a chest; virtual requested: "
                                        + (virtualUntil != 0L && System.nanoTime() <= virtualUntil)
                                        + ", screen "
                                        + screen.getClass().getSimpleName());
                    }
                    if (virtualUntil == 0L
                            || System.nanoTime() > virtualUntil
                            || !(screen instanceof GuiContainer gc)) {
                        return false;
                    }
                    virtualUntil = 0L;
                    EntityPlayerSP p = player();
                    if (p == null) {
                        return false;
                    }
                    // What GuiContainer.initGui would do: the container becomes the open one. The
                    // game sets its windowId right afterwards, and the item and click packets go
                    // through it — there is just no screen, and no loose cursor.
                    p.openContainer = gc.inventorySlots;
                    return true;
                };
    }

    /**
     * The next chest opened by the server, within the next {@code millis}, becomes a virtual
     * container.
     */
    static void virtualNext(long millis) {
        virtualUntil = millis <= 0 ? 0L : System.nanoTime() + millis * 1_000_000L;
    }

    /**
     * The screen vanishes and the game returns to focus; the container (player.openContainer) stays
     * open.
     */
    static void hideScreen() {
        Minecraft mc = mc();
        if (mc.currentScreen instanceof GuiContainer) {
            mc.currentScreen = null;
            mc.setIngameFocus();
        }
    }

    /**
     * Only the notice to the server that the window closed (the client keeps whatever screen it
     * has).
     */
    static void closeWindow(int windowId) {
        EntityPlayerSP p = player();
        if (p != null && windowId > 0) {
            p.sendQueue.addToSendQueue(new C0DPacketCloseWindow(windowId));
        }
    }

    /**
     * Inventory.resync: a drag click (mode 5) outside the window with the "end" button (2) without
     * having started — on the server Container.slotClick only resets the drag, with or without an
     * item on the cursor, and returns nothing. The packet says the result was a barrier: it does
     * not match, and the server refuses (S32) and sends the whole window (S30). The action number
     * is the client's next one, as in a real click, and the refusal is answered by the game itself
     * (C0F).
     */
    static boolean resync() {
        EntityPlayerSP p = player();
        if (p == null || p.sendQueue == null) {
            return false;
        }
        Container c = p.openContainer;
        p.sendQueue.addToSendQueue(
                new C0EPacketClickWindow(
                        c.windowId,
                        -999,
                        2,
                        5,
                        new ItemStack(Blocks.barrier),
                        c.getNextTransactionID(p.inventory)));
        return true;
    }

    /** Closes the open container, with the screen in view or hidden. */
    static void closeContainer() {
        EntityPlayerSP p = player();
        if (p == null || p.openContainer == p.inventoryContainer) {
            return;
        }
        if (mc().currentScreen instanceof GuiContainer) {
            p.closeScreen();
            return;
        }
        // Without its screen (virtual or hidden): notifies the server and returns to the inventory,
        // without touching the screen the player has open (the chat, Esc).
        p.sendQueue.addToSendQueue(new C0DPacketCloseWindow(p.openContainer.windowId));
        p.openContainer.onContainerClosed(p);
        p.openContainer = p.inventoryContainer;
    }
}
