/*
 * Copyright (c) 2026 Breno Xavier. All rights reserved.
 * Proprietary software: see the LICENSE file at the root of this repository.
 */

package dev.xavier.stein.loader;

import dev.xavier.stein.bridge.engine.EnchCache;
import dev.xavier.stein.bridge.hooks.GameHooks;
import dev.xavier.stein.loader.api.Effects;
import dev.xavier.stein.loader.api.spi.Host;

import net.minecraft.client.Minecraft;

import java.util.List;

/**
 * The inventory, item stacks and effects part of {@link LoaderHost}: the Host SPI is one flat
 * interface, so its implementation is split by area along a chain of classes (HostInventory,
 * HostWorld, HostRender, LoaderHost). Each method is a one-line delegate, mostly to InventoryOps
 * and GameOps.
 */
abstract class HostInventory implements Host {

    @Override
    public void setInventoryEffectsHidden(boolean hide) {
        GameHooks.setInventoryEffectsHidden(hide);
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
}
