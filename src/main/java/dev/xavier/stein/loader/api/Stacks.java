/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Breno Xavier
 *
 * Part of the Stein Loader API, licensed under the MIT License (see LICENSE-API).
 */

package dev.xavier.stein.loader.api;

import dev.xavier.stein.loader.api.spi.Host;

/**
 * Item stacks (the game's ItemStack, as Object): what it is, how many, how worn, and creating one
 * to display. The ids are those of the /give command, with or without the "minecraft:" ({@code
 * "diamond_sword"}, {@code "minecraft:arrow"}). A null stack is "nothing": gives 0, "" or false.
 *
 * <pre>
 * Object sword = Stacks.create("diamond_sword", 1, 900);   // a worn example, for the HUD editor
 * int left = Stacks.maxDamage(s) - Stacks.damage(s);
 * int arrows = Inventory.count(s -&gt; Stacks.is(s, "arrow"));
 * </pre>
 */
@Experimental(since = "0.1.16")
public final class Stacks {

    private Stacks() {}

    /**
     * A new stack (display only: it does not enter the inventory), or null if the id does not
     * exist.
     */
    public static Object create(String id, int count, int damage) {
        return Host.INSTANCE.stackCreate(id, count, damage);
    }

    /** The item id, with the "minecraft:" ({@code "minecraft:bow"}), or "". */
    public static String id(Object stack) {
        return Host.INSTANCE.stackId(stack);
    }

    /** Is it this item? ({@code Stacks.is(s, "bow")}) */
    public static boolean is(Object stack, String id) {
        if (stack == null || id == null) {
            return false;
        }
        String mine = id(stack);
        return mine.equals(id) || mine.equals("minecraft:" + id);
    }

    /** The name as the game shows it (the anvil-renamed one, if any), with colors. */
    public static String name(Object stack) {
        return Host.INSTANCE.stackName(stack);
    }

    /**
     * How many items are in the stack.
     *
     * @param stack an ItemStack (game type, as Object)
     * @return the count, or 0 when it is not a stack
     */
    public static int count(Object stack) {
        return Host.INSTANCE.stackCount(stack);
    }

    /** How many fit in a stack of this item (64, 16, 1). */
    public static int maxCount(Object stack) {
        return Host.INSTANCE.stackMaxCount(stack);
    }

    /** Does it wear out with use (tool, weapon, armor)? */
    public static boolean isDamageable(Object stack) {
        return Host.INSTANCE.stackDamageable(stack);
    }

    /**
     * The damage: 0 is new; {@link #maxDamage} is broken. On an item that does not wear out, it is
     * the subtype (the wool color).
     */
    public static int damage(Object stack) {
        return Host.INSTANCE.stackDamage(stack);
    }

    /**
     * The damage at which the item breaks ({@link #damage}); 0 for items that do not wear out.
     *
     * @param stack an ItemStack (game type, as Object)
     * @return the maximum damage, or 0
     */
    public static int maxDamage(Object stack) {
        return Host.INSTANCE.stackMaxDamage(stack);
    }

    /** Does it have any enchantment? */
    @Experimental(since = "0.1.19")
    public static boolean isEnchanted(Object stack) {
        return Host.INSTANCE.stackEnchanted(stack);
    }

    /**
     * The level of an enchantment on the item (0 if absent), by the game's id: 0 Protection, 1 Fire
     * Protection, 3 Blast Protection, 4 Projectile Protection, 34 Unbreaking, 16 Sharpness...
     */
    @Experimental(since = "0.1.19")
    public static int enchantLevel(Object stack, int enchantmentId) {
        return Host.INSTANCE.stackEnchantLevel(stack, enchantmentId);
    }

    /**
     * The ids of a potion's effects (1 Speed, 5 Strength, 10 Regeneration...); empty if it is not a
     * potion.
     */
    @Experimental(since = "0.1.19")
    public static int[] potionEffects(Object stack) {
        return Host.INSTANCE.stackPotionEffects(stack);
    }

    /** Is it a splash potion (thrown, not drunk)? */
    @Experimental(since = "0.1.19")
    public static boolean isSplashPotion(Object stack) {
        return Host.INSTANCE.stackSplash(stack);
    }

    /**
     * Are the two the same thing for counting (same item and, for those with subtypes, same
     * subtype)?
     */
    public static boolean sameKind(Object a, Object b) {
        return Host.INSTANCE.stackSameKind(a, b);
    }
}
