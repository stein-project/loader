/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.loader;

import static dev.xavier.stein.loader.GameOps.mc;

import dev.xavier.stein.loader.api.Effects;
import dev.xavier.stein.loader.api.Entities;
import dev.xavier.stein.loader.api.Players;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiPlayerTabOverlay;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.resources.I18n;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityGolem;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.EntityAmbientCreature;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.passive.EntityWaterMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StringUtils;
import net.minecraft.world.WorldSettings;

import org.lwjgl.opengl.Display;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Items, effects, entities, the Tab list and the FPS of the API (Stacks, Effects, Entities,
 * Players, Game) against the game: what the HUD used to do directly on Minecraft. All on the game
 * thread.
 */
final class HudOps {

    private HudOps() {}

    // --- Stacks ---

    static Object create(String id, int count, int damage) {
        Item item = id == null ? null : Item.getByNameOrId(id);
        if (item == null) {
            return null;
        }
        ItemStack s = new ItemStack(item, Math.max(1, count));
        s.setItemDamage(damage);
        return s;
    }

    static String id(Object stack) {
        if (!(stack instanceof ItemStack s) || s.getItem() == null) {
            return "";
        }
        Object name = Item.itemRegistry.getNameForObject(s.getItem());
        return name == null ? "" : name.toString();
    }

    static String name(Object stack) {
        return stack instanceof ItemStack s ? s.getDisplayName() : "";
    }

    static int count(Object stack) {
        return stack instanceof ItemStack s ? s.stackSize : 0;
    }

    static int maxCount(Object stack) {
        return stack instanceof ItemStack s ? s.getMaxStackSize() : 0;
    }

    static boolean damageable(Object stack) {
        return stack instanceof ItemStack s && s.isItemStackDamageable();
    }

    static int damage(Object stack) {
        return stack instanceof ItemStack s ? s.getItemDamage() : 0;
    }

    static int maxDamage(Object stack) {
        return stack instanceof ItemStack s ? s.getMaxDamage() : 0;
    }

    static int[] potionEffects(Object stack) {
        if (!(stack instanceof ItemStack s) || !(s.getItem() instanceof ItemPotion p)) {
            return new int[0];
        }
        List<PotionEffect> list = p.getEffects(s);
        if (list == null) {
            return new int[0];
        }
        int[] out = new int[list.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = list.get(i).getPotionID();
        }
        return out;
    }

    static boolean splash(Object stack) {
        return stack instanceof ItemStack s
                && s.getItem() instanceof ItemPotion
                && ItemPotion.isSplash(s.getMetadata());
    }

    static boolean enchanted(Object stack) {
        return stack instanceof ItemStack s && s.isItemEnchanted();
    }

    static int enchantLevel(Object stack, int id) {
        return stack instanceof ItemStack s ? EnchantmentHelper.getEnchantmentLevel(id, s) : 0;
    }

    static boolean sameKind(Object a, Object b) {
        return a instanceof ItemStack x
                && b instanceof ItemStack y
                && x.getItem() == y.getItem()
                && (!x.getItem().getHasSubtypes() || x.getMetadata() == y.getMetadata());
    }

    // --- Effects ---

    static List<Effects.Effect> effects(Object entity) {
        if (!(entity instanceof EntityLivingBase e)) {
            return List.of();
        }
        List<Effects.Effect> out = new ArrayList<>();
        for (PotionEffect p : e.getActivePotionEffects()) {
            out.add(
                    new Effects.Effect(
                            p.getPotionID(),
                            p.getAmplifier(),
                            p.getDuration(),
                            p.getIsPotionDurationMax()));
        }
        return out;
    }

    /** The name with the level in Roman numerals, as in the inventory (above X, as a number). */
    static String effectName(int id, int amplifier) {
        Potion potion = potion(id);
        if (potion == null) {
            return "";
        }
        String n = I18n.format(potion.getName());
        if (amplifier > 0) {
            n +=
                    " "
                            + (amplifier < 10
                                    ? I18n.format("enchantment.level." + (amplifier + 1))
                                    : amplifier + 1);
        }
        return n;
    }

    static String duration(int ticks, boolean permanent) {
        return permanent
                ? "**:**"
                : StringUtils.ticksToElapsedTime(ticks); // Potion.getDurationString
    }

    static boolean hasIcon(int id) {
        Potion potion = potion(id);
        return potion != null && potion.hasStatusIcon();
    }

    static Potion potion(int id) {
        return id >= 0 && id < Potion.potionTypes.length ? Potion.potionTypes[id] : null;
    }

    // --- Entities ---

    static List<Object> entities() {
        return mc().theWorld == null
                ? List.of()
                : Collections.unmodifiableList(mc().theWorld.loadedEntityList);
    }

    static int kind(Object entity) {
        Entities.Kind k;
        if (entity instanceof EntityPlayer) {
            k = Entities.Kind.PLAYER;
        } else if (entity instanceof IMob) {
            k = Entities.Kind.HOSTILE;
        } else if (entity instanceof EntityAnimal
                || entity instanceof EntityWaterMob
                || entity instanceof EntityAmbientCreature
                || entity instanceof EntityVillager
                || entity instanceof EntityGolem) {
            k = Entities.Kind.PASSIVE;
        } else {
            k = Entities.Kind.OTHER;
        }
        return k.ordinal();
    }

    static String type(Object entity) {
        if (!(entity instanceof Entity e) || e instanceof EntityPlayer) {
            return "";
        }
        String s = EntityList.getEntityString(e);
        return s == null ? "" : s;
    }

    static double pos(Object entity, int axis, float pt) {
        if (!(entity instanceof Entity e)) {
            return 0;
        }
        return switch (axis) {
            case 0 -> e.prevPosX + (e.posX - e.prevPosX) * pt;
            case 1 -> e.prevPosY + (e.posY - e.prevPosY) * pt;
            case 2 -> e.prevPosZ + (e.posZ - e.prevPosZ) * pt;
            default -> e.prevRotationYaw + (e.rotationYaw - e.prevRotationYaw) * pt;
        };
    }

    // --- Players ---

    static List<Players.Entry> list() {
        NetHandlerPlayClient net = mc().getNetHandler();
        if (net == null) {
            return List.of();
        }
        GuiPlayerTabOverlay tab = mc().ingameGUI == null ? null : mc().ingameGUI.getTabList();
        List<Players.Entry> out = new ArrayList<>(net.getPlayerInfoMap().size());
        for (NetworkPlayerInfo p : net.getPlayerInfoMap()) {
            String nick = PlayerInfos.nickOf(p);
            if (nick == null) {
                nick = "";
            }
            out.add(
                    new Players.Entry(
                            nick,
                            tab == null ? nick : tab.getPlayerName(p),
                            p.getPlayerTeam() == null ? "" : p.getPlayerTeam().getRegisteredName(),
                            p.getResponseTime(),
                            p.getGameType() == WorldSettings.GameType.SPECTATOR,
                            p.hasLocationSkin()));
        }
        return out;
    }

    static int count() {
        NetHandlerPlayClient net = mc().getNetHandler();
        return net == null ? 0 : net.getPlayerInfoMap().size();
    }

    /**
     * The Tab list's top and bottom texts, which the game keeps in two private fields and reads out
     * to nobody. Found by type (the only two text fields in the class), with no name at all; which
     * one is the bottom, the game itself tells: its public setFooter writes to one of them, and the
     * earlier value is restored right away.
     */
    private static Field header;

    private static Field footer;

    static String tabText(boolean top) {
        GuiPlayerTabOverlay tab = mc().ingameGUI == null ? null : mc().ingameGUI.getTabList();
        if (tab == null) {
            return null;
        }
        try {
            if (header == null && !findTabFields(tab)) {
                return null;
            }
            Object c = (top ? header : footer).get(tab);
            return c instanceof IChatComponent t ? t.getFormattedText() : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static boolean findTabFields(GuiPlayerTabOverlay tab)
            throws ReflectiveOperationException {
        List<Field> texts = new ArrayList<>();
        for (Field f : GuiPlayerTabOverlay.class.getDeclaredFields()) {
            if (f.getType() == IChatComponent.class) {
                f.setAccessible(true);
                texts.add(f);
            }
        }
        if (texts.size() != 2) {
            return false;
        }
        Field a = texts.get(0);
        Field b = texts.get(1);
        Object oldA = a.get(tab);
        Object oldB = b.get(tab);
        IChatComponent mark = new ChatComponentText("");
        tab.setFooter(mark);
        boolean aIsFooter = a.get(tab) == mark;
        a.set(tab, oldA);
        b.set(tab, oldB);
        footer = aIsFooter ? a : b;
        header = aIsFooter ? b : a;
        return true;
    }

    static String listScore(String nick) {
        if (mc().theWorld == null || nick == null) {
            return null;
        }
        Scoreboard board = mc().theWorld.getScoreboard();
        ScoreObjective objective = board.getObjectiveInDisplaySlot(0);
        return objective == null
                ? null
                : String.valueOf(board.getValueFromObjective(nick, objective).getScorePoints());
    }

    /** The game's Tab list, with the list's scoreboard (slot 0) as the game's HUD passes it. */
    static void drawGameTabList(int width) {
        Minecraft mc = mc();
        if (mc.ingameGUI == null || mc.theWorld == null) {
            return;
        }
        Scoreboard board = mc.theWorld.getScoreboard();
        mc.ingameGUI
                .getTabList()
                .renderPlayerlist(width, board, board.getObjectiveInDisplaySlot(0));
    }

    // --- Game ---

    static int fps() {
        return Minecraft.getDebugFPS();
    }

    static int fpsLimit() {
        int limit = mc().gameSettings.limitFramerate;
        return limit <= 0 || limit >= 260 ? 0 : limit;
    }

    static int refreshRate() {
        int hz = Display.getDesktopDisplayMode().getFrequency();
        return Math.max(0, hz);
    }
}
