/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Breno Xavier
 *
 * Part of the Stein Loader API, licensed under the MIT License (see LICENSE-API).
 */

package dev.xavier.stein.loader.api;

import dev.xavier.stein.loader.api.spi.Host;

import java.util.function.Predicate;

/**
 * The player's inventory and the open window: read it, and act on it through the same clicks a
 * player makes.
 *
 * <p><b>Two ways to point at a place:</b>
 *
 * <ul>
 *   <li><b>slot</b>: the slot number in the <b>current window</b> — the chest's, the furnace's, or,
 *       with none open, the player inventory's own (window 0, which exists even with the inventory
 *       closed). It is the number the game uses in the click, and it changes from window to window.
 *   <li><b>inventory index</b>: the fixed position in the player's inventory, the same in any
 *       window: 0-8 the hotbar, 9-35 the main bag, 36-39 the armour (36 boots ... 39 helmet).
 *       {@link #slotOf} translates one into the other.
 * </ul>
 *
 * <p><b>Every action is a real click</b> ({@code PlayerControllerMP.windowClick}): the game applies
 * it at once, the server receives the same packet as from a human click, and {@link
 * SteinMod#onWindowClick} sees each one. It works with the inventory open or closed. Nothing here
 * waits: several actions in a row go out in the same tick — the pace (and what the server accepts)
 * is the mod's decision, with {@link SteinMod#onTickEnd}. Actions return false when there is no
 * player, the slot does not exist in the current window, or there was nothing to do; in creative
 * mode the game's inventory is different, and the clicks here do not apply.
 *
 * <p>Items are ItemStack (null = empty); call from the game thread.
 */
@Experimental(since = "0.1.15")
public final class Inventory {

    // Click modes (the windowClick "mode").
    /**
     * Pick up/drop on the cursor (button 0: everything; 1: half when picking up, one when
     * dropping).
     */
    public static final int PICKUP = 0;

    /** Shift+click: sends the item to the other side of the window (armour goes to its slot). */
    public static final int QUICK_MOVE = 1;

    /** Number key over the slot: swaps with the hotbar (the button is the hotbar position, 0-8). */
    public static final int SWAP = 2;

    /** Middle button: copies the full stack (creative only). */
    public static final int CLONE = 3;

    /** Drop key over the slot (button 0: one; 1: the stack). */
    public static final int THROW = 4;

    /** Drag distributing (the three steps of the game's drag). */
    public static final int QUICK_CRAFT = 5;

    /** Double click: gathers the window's matching items onto the cursor. */
    public static final int PICKUP_ALL = 6;

    /** "Slot" outside the window: clicking there drops the cursor item on the ground. */
    public static final int OUTSIDE = -999;

    // Armour pieces (inventory index = ARMOR_START + piece).
    /** Boots: inventory index {@code ARMOR_START + BOOTS}. */
    public static final int BOOTS = 0;

    /** Leggings: inventory index {@code ARMOR_START + LEGGINGS}. */
    public static final int LEGGINGS = 1;

    /** Chestplate: inventory index {@code ARMOR_START + CHESTPLATE}. */
    public static final int CHESTPLATE = 2;

    /** Helmet: inventory index {@code ARMOR_START + HELMET}. */
    public static final int HELMET = 3;

    // Player inventory indices.
    /** First hotbar index (0-8 are the hotbar). */
    public static final int HOTBAR_START = 0;

    /** First index of the main inventory (9-35). */
    public static final int MAIN_START = 9;

    /** First armor index (36-39, boots first). */
    public static final int ARMOR_START = 36;

    /** Total number of indices (0-39). */
    public static final int SIZE = 40;

    private Inventory() {}

    // --- window ---

    /**
     * The current window: the open container's, or 0 (the player's inventory). -1 with no player.
     */
    public static int windowId() {
        return Host.INSTANCE.invWindowId();
    }

    /**
     * Is a world container open (chest, furnace, crafting table...)? The player's own inventory
     * does not count.
     */
    public static boolean isContainerOpen() {
        return Host.INSTANCE.invContainerOpen();
    }

    /** Is an inventory screen in front (the player's or a container's)? */
    public static boolean isScreenOpen() {
        return Host.INSTANCE.invScreenOpen();
    }

    /** How many slots the current window has (0 with no player). */
    public static int slotCount() {
        return Host.INSTANCE.invSlotCount();
    }

    /** The item in a slot of the current window, or null. */
    public static Object slotStack(int slot) {
        return Host.INSTANCE.invSlotStack(slot);
    }

    /**
     * Does the slot accept this item? It is the slot's own rule: armour only the right piece,
     * furnace only fuel at the bottom, result nothing. Slot outside the window: false.
     */
    @Experimental(since = "0.1.16")
    public static boolean accepts(int slot, Object stack) {
        return Host.INSTANCE.invAccepts(slot, stack);
    }

    /**
     * How many more items equal to the one there fit in the slot — the lesser of the slot's limit
     * and the stack's. Empty slot: the slot's limit (almost always 64); outside the window: 0.
     */
    @Experimental(since = "0.1.16")
    public static int room(int slot) {
        return Host.INSTANCE.invRoom(slot);
    }

    /**
     * Crafting slot (the grid or the result, in the inventory or the crafting table)? An item left
     * there falls out when the screen closes.
     */
    @Experimental(since = "0.1.16")
    public static boolean isCraftingSlot(int slot) {
        return Host.INSTANCE.invCrafting(slot);
    }

    /**
     * Do the two items stack onto each other (same item, same damage, same data)? Null with null:
     * false.
     */
    @Experimental(since = "0.1.16")
    public static boolean stacksWith(Object a, Object b) {
        return Host.INSTANCE.invStacksWith(a, b);
    }

    /**
     * How many items in the inventory (hotbar and bag, 0-35) pass the test, summing the stacks:
     * {@code Inventory.count(s -> Stacks.is(s, "arrow"))}.
     */
    @Experimental(since = "0.1.16")
    public static int count(Predicate<Object> which) {
        int n = 0;
        for (int i = 0; i < ARMOR_START; i++) {
            Object s = stack(i);
            if (s != null && which.test(s)) {
                n += Stacks.count(s);
            }
        }
        return n;
    }

    /**
     * The slot of the current window that shows this inventory index, or -1 if it does not show it
     * (the armour, in a chest).
     */
    public static int slotOf(int inventoryIndex) {
        return Host.INSTANCE.invSlotOf(inventoryIndex);
    }

    /**
     * The player-inventory index behind a slot of the current window, or -1 if the slot is not the
     * player's.
     */
    public static int inventoryIndexOf(int slot) {
        return Host.INSTANCE.invIndexOf(slot);
    }

    // --- the player's inventory ---

    /** The item at a player-inventory index (0-39), whatever the window, or null. */
    public static Object stack(int inventoryIndex) {
        return Host.INSTANCE.invStack(inventoryIndex);
    }

    /** The item stuck on the cursor (picked up and not yet dropped), or null. */
    public static Object cursor() {
        return Host.INSTANCE.invCursor();
    }

    /** The selected hotbar position (0-8). */
    public static int selectedHotbar() {
        return Host.INSTANCE.invSelected();
    }

    /** The held item, or null. */
    public static Object heldItem() {
        return stack(selectedHotbar());
    }

    /** The armour worn on a piece ({@link #BOOTS}...{@link #HELMET}), or null. */
    public static Object armor(int piece) {
        return stack(ARMOR_START + piece);
    }

    /**
     * The first inventory index, from {@code from} to {@code to} (inclusive), whose item passes the
     * test; -1 if none. Empty slots are not tested.
     */
    public static int find(Predicate<Object> test, int from, int to) {
        for (int i = Math.max(0, from); i <= Math.min(SIZE - 1, to); i++) {
            Object s = stack(i);
            if (s != null && test.test(s)) {
                return i;
            }
        }
        return -1;
    }

    /** The first empty index in the hotbar and bag (0-35), as the game searches; -1 if full. */
    public static int firstEmpty() {
        return Host.INSTANCE.invFirstEmpty();
    }

    // --- items ---

    /**
     * Which piece this item is worn on ({@link #BOOTS}...{@link #HELMET}), or -1 if it is not
     * armour.
     */
    public static int armorPiece(Object stack) {
        return Host.INSTANCE.invArmorPiece(stack);
    }

    /**
     * The item's armour points (the half-armour it gives, without enchantment), or 0 if it is not
     * armour.
     */
    public static int armorValue(Object stack) {
        return Host.INSTANCE.invArmorValue(stack);
    }

    // --- actions (each one, one or more clicks) ---

    /**
     * A raw click on the current window, as the game does it.
     *
     * @param slot the slot, or {@link #OUTSIDE}
     * @param button the button (or the hotbar position, in {@link #SWAP})
     * @param mode {@link #PICKUP}, {@link #QUICK_MOVE}...
     * @return false if there was no player or the slot does not exist in the window
     */
    public static boolean click(int slot, int button, int mode) {
        return Host.INSTANCE.invClick(slot, button, mode);
    }

    /** Picks up the slot's item (or drops the cursor's into it): a left click. */
    public static boolean pickup(int slot) {
        return click(slot, 0, PICKUP);
    }

    /** Right click: picks up half the stack, or drops one item from the cursor. */
    public static boolean pickupHalf(int slot) {
        return click(slot, 1, PICKUP);
    }

    /** Shift+click on the slot. */
    public static boolean quickMove(int slot) {
        return click(slot, 0, QUICK_MOVE);
    }

    /** Swaps the slot with a hotbar position (0-8), like the number key over it. */
    public static boolean swapHotbar(int slot, int hotbar) {
        return click(slot, hotbar, SWAP);
    }

    /** Drops one item from the slot onto the ground, or the whole stack. */
    public static boolean drop(int slot, boolean wholeStack) {
        return click(slot, wholeStack ? 1 : 0, THROW);
    }

    /** Drops what is on the cursor onto the ground. */
    public static boolean dropCursor() {
        return cursor() != null && click(OUTSIDE, 0, PICKUP);
    }

    /**
     * Moves the item from one slot to another in the current window: picks up, drops at the
     * destination and, if something is left on the cursor (the destination held another item, or
     * the stack did not fit), returns it to the origin — occupied becomes a swap. Two or three
     * clicks. It only starts with an empty cursor.
     */
    public static boolean move(int from, int to) {
        if (from == to || cursor() != null || slotStack(from) == null) {
            return false;
        }
        if (!pickup(from) || !pickup(to)) {
            return false;
        }
        return cursor() == null || pickup(from);
    }

    /**
     * Puts on the armour that is at an inventory index, in its piece's slot. With the slot empty, a
     * shift+click; occupied, it swaps (the old one goes to where the new one was). Only with the
     * armour in view in the current window — the player's inventory, open or closed; in a chest,
     * false.
     */
    public static boolean equip(int inventoryIndex) {
        int piece = armorPiece(stack(inventoryIndex));
        if (piece < 0 || inventoryIndex == ARMOR_START + piece) {
            return false;
        }
        int from = slotOf(inventoryIndex);
        int to = slotOf(ARMOR_START + piece);
        if (from < 0 || to < 0) {
            return false;
        }
        return armor(piece) == null ? quickMove(from) : move(from, to);
    }

    /**
     * Takes the armour off a piece into the bag or the hotbar (shift+click). False if there is no
     * armour or no room.
     */
    public static boolean unequip(int piece) {
        int slot = slotOf(ARMOR_START + piece);
        if (armor(piece) == null || slot < 0 || firstEmpty() < 0) {
            return false;
        }
        return quickMove(slot);
    }

    /**
     * Starts using the held item (eating, drinking, raising the sword, drawing the bow), like a
     * right click in the air — with any screen open or none. The game only keeps using it while the
     * use key is held down (with no screen open): whoever uses it holds {@code
     * Keys.game(GameKey.USE).hold(true)} until it is done, and releases it at the end.
     *
     * @return false with no player or no held item
     */
    @Experimental(since = "0.1.19")
    public static boolean useHeldItem() {
        return Host.INSTANCE.invUseHeld();
    }

    /** Is the player using an item now (eating, drinking, blocking, drawing the bow)? */
    @Experimental(since = "0.1.19")
    public static boolean isUsingItem() {
        return Host.INSTANCE.invUsing();
    }

    /**
     * Selects the hotbar position (0-8), like the mouse wheel or the number key. The server finds
     * out on the next tick, as in the game.
     */
    public static void selectHotbar(int hotbar) {
        Host.INSTANCE.invSelect(hotbar);
    }

    /**
     * The slot of the current window under the mouse (with an inventory or container screen open),
     * or -1 — what the game highlights with the light square.
     */
    @Experimental(since = "0.1.16")
    public static int hoveredSlot() {
        return Host.INSTANCE.invHovered();
    }

    /**
     * Puts a click in the Loader's queue: one goes out per tick, in order, through the same path as
     * {@link #click}. For working on many slots without sending everything in the same tick (a
     * server that counts clicks). The queue belongs to the window the click was requested in: if
     * another window opens (or the screen closes and the window changes), what is left is discarded
     * — a chest slot never lands in another window.
     */
    @Experimental(since = "0.1.16")
    public static void clickLater(int slot, int button, int mode) {
        clickLater("", slot, button, mode);
    }

    /**
     * The same, in the owner's queue (generally the mod's id): each mod counts and discards only
     * its own clicks. Owners take turns, one click per tick in total. If requested with an
     * inventory screen open, the click is also dropped if it closes.
     */
    @Experimental(since = "0.1.16")
    public static void clickLater(String owner, int slot, int button, int mode) {
        Host.INSTANCE.invClickLater(owner, slot, button, mode);
    }

    /** How many clicks are still in the queue ({@link #clickLater}). */
    @Experimental(since = "0.1.16")
    public static int pendingClicks() {
        return pendingClicks("");
    }

    /** How many of the owner's clicks are still in the queue. */
    @Experimental(since = "0.1.16")
    public static int pendingClicks(String owner) {
        return Host.INSTANCE.invPending(owner);
    }

    /** Discards the click queue. */
    @Experimental(since = "0.1.16")
    public static void clearPending() {
        clearPending("");
    }

    /** Discards the owner's clicks that are still in the queue. */
    @Experimental(since = "0.1.16")
    public static void clearPending(String owner) {
        Host.INSTANCE.invClearPending(owner);
    }

    /**
     * Hides the open container's screen <b>without closing it</b>: the server keeps the chest open
     * (clicks on it work, through the window's slots) and the player goes back to playing, without
     * seeing the screen. Call it in the same tick the container opened (the end of the tick comes
     * before drawing, so the screen never even appears). Close it later with {@link
     * #closeContainer}.
     */
    @Experimental(since = "0.1.19")
    public static void hideContainerScreen() {
        Host.INSTANCE.invHideScreen();
    }

    /**
     * The next chest the server opens (within the next {@code millis}) becomes a <b>virtual
     * container</b>: really open (clicks on it work, and {@link #isContainerOpen} says so), but
     * with no screen at all — the player sees nothing, not even one frame, and the mouse stays
     * captured by the game. Close it with {@link #closeContainer}. 0 disarms.
     */
    @Experimental(since = "0.1.19")
    public static void openVirtualNext(long millis) {
        Host.INSTANCE.invVirtualNext(millis);
    }

    /**
     * Closes the open container, with or without its screen in view (notifies the server, like
     * Esc).
     */
    @Experimental(since = "0.1.19")
    public static void closeContainer() {
        Host.INSTANCE.invCloseContainer();
    }

    /**
     * Tells the server that window {@code windowId} closed, without touching the client's screen:
     * for a hidden container ({@link #hideContainerScreen}) that lost its place to another screen
     * (the player opened the inventory on top of it).
     */
    @Experimental(since = "0.1.19")
    public static void closeWindowOnServer(int windowId) {
        Host.INSTANCE.invCloseWindow(windowId);
    }

    /**
     * Asks the server for the real inventory of the open window. For when the client may have ended
     * up different from the server — a click the server refused without returning the slot (a
     * plugin that cancels the click, an anticheat), and the client keeps showing the worn armour
     * that the server does not have.
     *
     * <p>It sends a click that moves nothing on the server (a drag that starts and ends in the same
     * packet) stating a result that does not match: the server, as with any click that does not
     * match, refuses and sends the whole window again. When it arrives, mods receive {@link
     * SteinMod#onWindowItems}. Nothing changes on the client before that, and no item moves, not
     * even the cursor's. Do not use it every tick: it is a click packet like any other.
     *
     * @return true if the request went out (false with no player or no connection)
     */
    @Experimental(since = "0.1.21")
    public static boolean resync() {
        return Host.INSTANCE.invResync();
    }

    /**
     * How many slots the open container has, not counting the player's inventory (0 with no
     * container).
     */
    @Experimental(since = "0.1.19")
    public static int containerSize() {
        return isContainerOpen() ? Math.max(0, slotCount() - ARMOR_START) : 0;
    }

    /** Opens the player's inventory screen, like the E key. */
    public static void openInventory() {
        Host.INSTANCE.invOpen();
    }

    /** Closes the open inventory or container screen, notifying the server, like Esc. */
    public static void closeScreen() {
        Host.INSTANCE.invClose();
    }
}
