/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.loader;

import dev.xavier.stein.bridge.hooks.GameHooks;
import dev.xavier.stein.loader.api.Players;
import dev.xavier.stein.loader.api.Targeting;
import dev.xavier.stein.loader.api.Terrain;
import dev.xavier.stein.loader.gui.Draw2D;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;

import java.util.List;

/**
 * The world part of {@link LoaderHost}: targeting, entities, players, relations and terrain. Each
 * method is a one-line delegate, mostly to TargetingOps, PlayerOps, HudOps and TerrainOps.
 */
abstract class HostWorld extends HostInventory {

    @Override
    public void watchBlockModel(Object block, boolean watched) {
        GameHooks.watchBlockModel(block, watched);
    }

    @Override
    public Object spriteAt(float u, float v) {
        return GameHooks.spriteAt(u, v);
    }

    @Override
    public Targeting.Ray raycast(double reach) {
        return TargetingOps.raycast(reach);
    }

    @Override
    public double attackReach() {
        return TargetingOps.attackReach();
    }

    @Override
    public Object pointedEntity() {
        return TargetingOps.pointedEntity();
    }

    @Override
    public boolean attack(Object entity) {
        return TargetingOps.attack(entity);
    }

    @Override
    public void swing() {
        TargetingOps.swing();
    }

    @Override
    public boolean interact(Object entity) {
        return TargetingOps.interact(entity);
    }

    @Override
    public Object self() {
        return PlayerOps.self();
    }

    @Override
    public boolean isLiving(Object entity) {
        return entity instanceof EntityLivingBase;
    }

    @Override
    public float health(Object entity) {
        return isLiving(entity) ? ((EntityLivingBase) entity).getHealth() : 0;
    }

    @Override
    public float maxHealth(Object entity) {
        return isLiving(entity) ? ((EntityLivingBase) entity).getMaxHealth() : 0;
    }

    @Override
    public float absorption(Object entity) {
        return isLiving(entity) ? ((EntityLivingBase) entity).getAbsorptionAmount() : 0;
    }

    @Override
    public int armor(Object entity) {
        return isLiving(entity) ? ((EntityLivingBase) entity).getTotalArmorValue() : 0;
    }

    @Override
    public int hurtTime(Object entity) {
        return isLiving(entity) ? ((EntityLivingBase) entity).hurtTime : 0;
    }

    @Override
    public String selfName() {
        return PlayerOps.selfName();
    }

    @Override
    public List<String> onlinePlayers() {
        return PlayerOps.online();
    }

    @Override
    public String playerTeam(String nick) {
        return PlayerOps.teamName(nick);
    }

    @Override
    public String playerPrefix(String nick) {
        return PlayerOps.prefix(nick);
    }

    @Override
    public String playerSuffix(String nick) {
        return PlayerOps.suffix(nick);
    }

    @Override
    public String playerTabName(String nick) {
        return PlayerOps.tabName(nick);
    }

    @Override
    public int playerPing(String nick) {
        return PlayerOps.ping(nick);
    }

    @Override
    public String playerNameOf(Object entity) {
        return PlayerOps.nameOf(entity);
    }

    @Override
    public void relationsChanged() {
        GameTasks.relationsChanged();
    }

    @Override
    public List<Object> worldPlayers() {
        return GameOps.players();
    }

    @Override
    public Object playerEntity(String nick) {
        return GameOps.player(nick);
    }

    @Override
    public boolean isPlayer(Object entity) {
        return GameOps.isPlayer(entity);
    }

    @Override
    public String entityName(Object entity) {
        return GameOps.name(entity);
    }

    @Override
    public boolean isInvisible(Object entity) {
        return GameOps.invisible(entity);
    }

    @Override
    public float distance(Object a, Object b) {
        return GameOps.distance(a, b);
    }

    @Override
    public String playerDisplayName(String nick) {
        return GameOps.displayName(nick);
    }

    @Override
    public String playerInfoNick(Object playerInfo) {
        return GameOps.nickOf(playerInfo);
    }

    @Override
    public Object terrainWorld() {
        return TerrainOps.current();
    }

    @Override
    public boolean terrainLoaded(int chunkX, int chunkZ) {
        return TerrainOps.loaded(chunkX, chunkZ);
    }

    @Override
    public int terrainHeight(int x, int z) {
        return TerrainOps.height(x, z);
    }

    @Override
    public boolean terrainAir(int x, int y, int z) {
        return TerrainOps.air(x, y, z);
    }

    @Override
    public boolean terrainSolid(int x, int y, int z) {
        return TerrainOps.solid(x, y, z);
    }

    @Override
    public boolean terrainWater(int x, int y, int z) {
        return TerrainOps.water(x, y, z);
    }

    @Override
    public int terrainColor(int x, int y, int z) {
        return TerrainOps.color(x, y, z);
    }

    @Override
    public int terrainMapColor(int x, int y, int z) {
        return TerrainOps.mapColor(x, y, z);
    }

    @Override
    public int terrainSkyLight(int x, int y, int z) {
        return TerrainOps.skyLight(x, y, z);
    }

    @Override
    public boolean terrainHasSky() {
        return TerrainOps.hasSky();
    }

    @Override
    public void terrainSpawners(Terrain.Visitor visitor) {
        TerrainOps.spawners(visitor);
    }

    @Override
    public List<Object> worldEntities() {
        return HudOps.entities();
    }

    @Override
    public int entityKind(Object entity) {
        return HudOps.kind(entity);
    }

    @Override
    public String entityType(Object entity) {
        return HudOps.type(entity);
    }

    @Override
    public double entityPos(Object entity, int axis, float partialTicks) {
        return HudOps.pos(entity, axis, partialTicks);
    }

    @Override
    public List<Players.Entry> playerList() {
        return HudOps.list();
    }

    @Override
    public int playerCount() {
        return HudOps.count();
    }

    @Override
    public String tabText(boolean header) {
        return HudOps.tabText(header);
    }

    @Override
    public String listScore(String nick) {
        return HudOps.listScore(nick);
    }

    @Override
    public Object playerSkin(String nick) {
        return Draw2D.playerSkin(nick);
    }

    @Override
    public Targeting.Ray raycastQuery(Targeting.Query query) {
        return TargetingOps.raycast(query);
    }

    @Override
    public boolean entityAlive(Object entity) {
        return entity instanceof EntityLivingBase l && l.isEntityAlive();
    }

    @Override
    public String entityDisplayName(Object entity) {
        return entity instanceof Entity e ? e.getDisplayName().getFormattedText() : null;
    }

    @Override
    public Object camera() {
        return Minecraft.getMinecraft().getRenderViewEntity();
    }

    @Override
    public String terrainBlockId(int x, int y, int z) {
        return TerrainOps.blockId(x, y, z);
    }
}
